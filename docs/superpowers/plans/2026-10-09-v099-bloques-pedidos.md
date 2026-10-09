# 0.9.9 — Pedidos por bloques: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada línea de pedido de componentes pertenece a un bloque (la cabecera del pedido a un proveedor); la pestaña Pedidos los agrupa en filas plegables con su menú (confirmar, recibir, cancelar, borrar, añadir líneas, separar, juntar, nota), las líneas se mueven entre bloques del mismo proveedor y «Nuevo pedido» decide a qué bloque va cada proveedor.

**Architecture:** Servidor: tabla nueva `Bloque_compra` y columna `Compra_componente.ID_BLOQUE` (migración aditiva con reconstrucción proveedor + día); `GET /api/compras` trae los datos del bloque en cada línea; toda alta cae en un bloque (`DestinoBloque` con la regla «abierto más reciente o nuevo»); `BloqueCompraService` hace cada operación en una transacción, comprobando la lista exacta de líneas que vio la web (todo o nada, 409); `BloqueCompraController` publica las rutas nuevas y `RegistroBloques` escribe los logs. Web: funciones puras en `pedidos/bloques/reglas.ts`; la tabla compartida gana una prop opcional `grupos` (cabeceras intercaladas, plegado, flechas que las saltan); la página monta cabeceras, menús y diálogos; «Nuevo pedido» y «Editar» eligen el bloque destino.

**Tech Stack:** Spring Boot 3.3 + JdbcTemplate + MariaDB 11 (servidor, JUnit 5 + Mockito 5 + MockMvc); React + TypeScript + TanStack Query/Table/Virtual + Radix (web, Vitest + Testing Library + MSW; Playwright para el smoke); openapi-typescript.

**Spec:** `docs/superpowers/specs/2026-10-09-v099-bloques-pedidos-design.md` (raíz). **Prototipo aprobado:** `docs/superpowers/specs/assets/2026-10-09-v099-prototipo-bloques.html` (se abre en el navegador; manda en aspecto y textos salvo donde la spec diga otra cosa).

## Global Constraints

- Versión **0.9.9**. Se empieza cuando la **0.9.8** esté mergeada en `main` de servidor y web. Servidor y web se etiquetan juntos al final, **solo con OK del usuario**.
- Se trabaja en **worktrees** (los clones normales pueden tener otra sesión en curso):
  `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add /c/Users/dev/Documents/wt/servidor-099 -b feature/bloques-pedidos main`
  `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add /c/Users/dev/Documents/wt/web-099 -b feature/bloques-pedidos main`
  y `npm ci` dentro de `wt/web-099`. Todas las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` de este plan se refieren a esos worktrees; `docs/…` sin prefijo es la raíz.
- Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**. Commits en español sin tildes, prefijo `feat:` / `test:` / `docs:` / `chore:`, **sin** línea `Co-Authored-By`.
- Repos **públicos**: nada de IPs, nombres reales ni datos del taller en código, tests ni docs. Datos de tests sintéticos (proveedores «Proveedor A/B», SKUs tipo `lcd-x-negro`).
- **Reglas del bloque** (spec §3, el servidor las garantiza): un proveedor por bloque; un bloque sin líneas se borra en la misma transacción; **abierto** = todas sus líneas `pendiente`; el estado del bloque no se guarda; total = suma de `totalFila` de las no canceladas; orden: bloques por `FECHA` y número descendentes, líneas por `FECHA_PEDIDO` e `ID_COMPRA` ascendentes.
- **Mensajes del servidor (exactos):**
  `Este bloque fue modificado por otro usuario.` (409 de las acciones de bloque y de la nota) ·
  `No hay líneas a las que aplicar la acción.` (422) · `Deja al menos una línea en el bloque.` (422) ·
  `El bloque {n} es de otro proveedor.` (422) · `El bloque {n} ya no existe; elige otro destino.` (409) ·
  `La línea ya está en ese bloque.` (422) · `Elige otro bloque.` (422) · `La línea ya está sola en su bloque.` (422) ·
  `La nota no puede superar los 200 caracteres.` (422) · `El pedido ya no existe` (409 al mover una línea borrada) ·
  `El pedido no existe` (404 de `PATCH /api/compras/{id}/bloque`).
- **Textos de la web (exactos, spec §6 y prototipo):** interruptor `Agrupar por bloque`; botón `Plegar todo` / `Desplegar todo`; cabecera `Bloque {n}`, `{k} línea(s)`, `mostrando {k} de {n}`, etiqueta `antiguo`, cabecera `Sin bloque`; menú de línea `Mover a bloque` con `Bloque {n}` y `Bloque nuevo` (`ya está sola en su bloque`); menú del bloque `Confirmar bloque (n)`, `Recibir todo (n)`, `Añadir líneas…`, `Separar líneas…`, `Juntar con…`, `Añadir nota…` / `Editar nota…`, `Cancelar bloque (n)`, `Borrar bloque` (motivos `nada pendiente`, `nada en camino`, `solo tiene una línea`, `no hay otro bloque de este proveedor`, `hay líneas ya pedidas`); confirmación `Mover línea ya pedida`; aviso de 409 `Este bloque fue modificado por otro usuario. Los datos se han recargado.`; CSV `Bloque` y `Nota del bloque`.
- Servidor, comandos Maven (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y luego `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro de `/c/Users/dev/Documents/wt/servidor-099`.
- Web: `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint` dentro de `/c/Users/dev/Documents/wt/web-099`.
- **Claude no hace SSH**: en preprod y producción prepara los guiones en `Apuntes/` y el usuario ejecuta y pega la salida.

---

### Task 1: Servidor — esquema, lectura con el bloque y `BloqueCompraDAO`

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-bloques-compra.sql` (esquema, una vez por base)
- Create: `gestion-reparaciones-servidor/sql/reconstruir-bloques-compra.sql` (reconstrucción y comprobación, repetible)
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (lista de `DROP`, tabla nueva antes de `Compra_componente`, columna y clave en `Compra_componente`)
- Modify: `gestion-reparaciones-servidor/docs/schema.md` (lista de tablas)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/CompraComponente.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java` (`SELECT_BASE`, `MAPPER`, `moverABloque`)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/BloqueCompraDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/BloqueCompraDAOTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOBloqueTest.java`

**Interfaces:**
- Produces: `CompraComponente.conBloque(Integer idBloque, LocalDateTime fechaBloque, String notaBloque, LocalDateTime bloqueUpdatedAt, boolean bloqueAntiguo): CompraComponente` y sus getters `getIdBloque()`, `getFechaBloque()`, `getNotaBloque()`, `getBloqueUpdatedAt()`, `isBloqueAntiguo()` (JSON `idBloque`, `fechaBloque`, `notaBloque`, `bloqueUpdatedAt`, `bloqueAntiguo`); `CompraComponenteDAO.moverABloque(int idCompra, int idBloque)` (409 `El pedido ya no existe` si no toca fila); `BloqueCompraDAO` con `record Bloque(int idBloque, int idProv, LocalDateTime fecha, String nota, boolean reconstruido, LocalDateTime updatedAt)`, `record LineaBloque(int idCompra, String tipo, int cantidad, String estado, LocalDateTime updatedAt)` y `crear(int idProv): int`, `getById(int): Optional<Bloque>`, `abiertoMasReciente(int idProv): Optional<Integer>`, `lineas(int idBloque): List<LineaBloque>`, `contarLineas(int): int`, `borrarSiVacio(int): boolean`, `cambiarNota(int, String)`. `updatedAt` siempre truncado a segundos.

- [ ] **Step 1: Worktrees**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor switch main && git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor pull --ff-only
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add /c/Users/dev/Documents/wt/servidor-099 -b feature/bloques-pedidos main
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web switch main && git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web pull --ff-only
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add /c/Users/dev/Documents/wt/web-099 -b feature/bloques-pedidos main
cd /c/Users/dev/Documents/wt/web-099 && npm ci
```

Si un clon normal no está en `main` (otra sesión trabajando), **no** se hace `switch`: se crea el worktree desde `main` igualmente (`worktree add … main` no toca el clon).

- [ ] **Step 2: Tests que fallan**

Crear `src/test/java/com/reparaciones/servidor/dao/BloqueCompraDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cabeceras de pedido (0.9.9, spec «Pedidos por bloques» §3-§4) con el JdbcTemplate mockeado. */
@SuppressWarnings({"unchecked", "rawtypes"})
class BloqueCompraDAOTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BloqueCompraDAO dao = new BloqueCompraDAO(jdbc);

    @Test void crearInsertaElProveedorConLaFechaDeAhoraYDevuelveElId() throws Exception {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 90));
            return 1;
        });

        assertEquals(90, dao.crear(2));

        ArgumentCaptor<PreparedStatementCreator> sentencia = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(sentencia.capture(), any(KeyHolder.class));
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(eq("INSERT INTO Bloque_compra (ID_PROV, FECHA) VALUES (?, NOW())"),
                eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
        sentencia.getValue().createPreparedStatement(con);
        verify(ps).setInt(1, 2);
    }

    @Test void abiertoMasRecienteEsElPrimeroDeLaConsultaOVacio() {
        when(jdbc.query(eq(BloqueCompraDAO.SQL_ABIERTO), any(RowMapper.class), eq(2))).thenReturn(List.of(41));
        assertEquals(Optional.of(41), dao.abiertoMasReciente(2));
        assertEquals(Optional.empty(), dao.abiertoMasReciente(3));
        assertTrue(BloqueCompraDAO.SQL_ABIERTO.contains("cc.ESTADO <> 'pendiente'"));
        assertTrue(BloqueCompraDAO.SQL_ABIERTO.contains("EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)"));
        assertTrue(BloqueCompraDAO.SQL_ABIERTO.endsWith("ORDER BY b.FECHA DESC, b.ID_BLOQUE DESC LIMIT 1"));
    }

    @Test void getByIdLeeLaCabeceraConElUpdatedAtEnSegundos() throws Exception {
        dao.getById(41);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(contains("FROM Bloque_compra WHERE ID_BLOQUE = ?"), mapper.capture(), eq(41));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_BLOQUE")).thenReturn(41);
        when(rs.getInt("ID_PROV")).thenReturn(2);
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("NOTA")).thenReturn("Ped. 123");
        when(rs.getBoolean("RECONSTRUIDO")).thenReturn(true);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT.withNano(500_000_000)));
        assertEquals(new BloqueCompraDAO.Bloque(41, 2, AT, "Ped. 123", true, AT), mapper.getValue().mapRow(rs, 0));
    }

    @Test void lineasLeeIdTipoCantidadEstadoYUpdatedAtEnSegundos() throws Exception {
        dao.lineas(41);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(BloqueCompraDAO.SQL_LINEAS), mapper.capture(), eq(41));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_COMPRA")).thenReturn(5);
        when(rs.getString("TIPO")).thenReturn("lcd-x-negro");
        when(rs.getInt("CANTIDAD")).thenReturn(3);
        when(rs.getString("ESTADO")).thenReturn("en_camino");
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT.withNano(900_000_000)));
        assertEquals(new BloqueCompraDAO.LineaBloque(5, "lcd-x-negro", 3, "en_camino", AT), mapper.getValue().mapRow(rs, 0));
        assertTrue(BloqueCompraDAO.SQL_LINEAS.endsWith("ORDER BY cc.FECHA_PEDIDO, cc.ID_COMPRA"));
    }

    @Test void contarLineas() {
        when(jdbc.queryForObject(BloqueCompraDAO.SQL_CONTAR, Integer.class, 41)).thenReturn(3);
        assertEquals(3, dao.contarLineas(41));
    }

    @Test void borrarSiVacioSoloBorraUnBloqueSinLineas() {
        when(jdbc.update(BloqueCompraDAO.SQL_BORRAR_SI_VACIO, 41, 41)).thenReturn(1);
        assertTrue(dao.borrarSiVacio(41));
        assertFalse(dao.borrarSiVacio(42));
        assertTrue(BloqueCompraDAO.SQL_BORRAR_SI_VACIO.contains("NOT EXISTS (SELECT 1 FROM Compra_componente WHERE ID_BLOQUE = ?)"));
    }

    @Test void cambiarNotaGuardaElTextoONulo() {
        dao.cambiarNota(41, "Ped. 123");
        dao.cambiarNota(41, null);
        verify(jdbc).update(BloqueCompraDAO.SQL_CAMBIAR_NOTA, "Ped. 123", 41);
        verify(jdbc).update(BloqueCompraDAO.SQL_CAMBIAR_NOTA, null, 41);
    }
}
```

Crear `src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOBloqueTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraComponente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** GET /api/compras con los datos del bloque de cada línea y el cambio de bloque (0.9.9, spec §5.1). */
@SuppressWarnings({"unchecked", "rawtypes"})
class CompraComponenteDAOBloqueTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    private static final String MOVER = "UPDATE Compra_componente SET ID_BLOQUE=? WHERE ID_COMPRA=?";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraComponenteDAO dao = new CompraComponenteDAO(jdbc);

    private RowMapper mapperDeGetAll() {
        dao.getAll();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), mapper.capture());
        assertTrue(sql.getValue().contains("LEFT JOIN Bloque_compra b ON b.ID_BLOQUE = cc.ID_BLOQUE"));
        assertTrue(sql.getValue().contains("cc.ID_BLOQUE, b.FECHA AS FECHA_BLOQUE, b.NOTA AS NOTA_BLOQUE, b.UPDATED_AT AS UPDATED_AT_BLOQUE, b.RECONSTRUIDO"));
        return mapper.getValue();
    }

    private static ResultSet filaBase() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getTimestamp("FECHA_PEDIDO")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("ESTADO")).thenReturn("pendiente");
        return rs;
    }

    @Test void cadaLineaTraeLosDatosDeSuBloque() throws Exception {
        RowMapper mapper = mapperDeGetAll();
        ResultSet rs = filaBase();
        when(rs.getObject("ID_BLOQUE", Integer.class)).thenReturn(41);
        when(rs.getTimestamp("FECHA_BLOQUE")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("NOTA_BLOQUE")).thenReturn("Ped. 123");
        when(rs.getTimestamp("UPDATED_AT_BLOQUE")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getBoolean("RECONSTRUIDO")).thenReturn(true);

        CompraComponente c = (CompraComponente) mapper.mapRow(rs, 0);

        assertEquals(41, c.getIdBloque());
        assertEquals(AT, c.getFechaBloque());
        assertEquals("Ped. 123", c.getNotaBloque());
        assertEquals(AT, c.getBloqueUpdatedAt());
        assertTrue(c.isBloqueAntiguo());
    }

    @Test void unaLineaSinBloqueLlegaConLosCamposDelBloqueVacios() throws Exception {
        CompraComponente c = (CompraComponente) mapperDeGetAll().mapRow(filaBase(), 0);
        assertNull(c.getIdBloque());
        assertNull(c.getFechaBloque());
        assertNull(c.getNotaBloque());
        assertNull(c.getBloqueUpdatedAt());
        assertFalse(c.isBloqueAntiguo());
    }

    @Test void moverABloqueCambiaElBloqueDeLaLinea() {
        when(jdbc.update(MOVER, 41, 5)).thenReturn(1);
        dao.moverABloque(5, 41);
        verify(jdbc).update(MOVER, 41, 5);
    }

    @Test void moverUnaLineaQueYaNoExisteEs409() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.moverABloque(5, 41));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El pedido ya no existe", e.getReason());
    }
}
```

- [ ] **Step 3: Ejecutar y ver que fallan**

Run: `mvn -q test -Dtest='BloqueCompraDAOTest,CompraComponenteDAOBloqueTest'`
Expected: FAIL de compilación (`BloqueCompraDAO`, `getIdBloque`, `moverABloque` no existen).

- [ ] **Step 4: Migración y reconstrucción**

Crear `sql/migracion-bloques-compra.sql`:

```sql
-- 0.9.9: pedidos por bloques (spec docs/superpowers/specs/2026-10-09-v099-bloques-pedidos-design.md §4).
-- Un bloque es la cabecera de un pedido a un proveedor; cada línea de Compra_componente pertenece a uno.
-- Aditivo: el servidor 0.9.8 sigue funcionando sobre este esquema (no nombra ni la tabla ni la columna).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.9, y justo después reconstruir-bloques-compra.sql. Antes de
-- aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

CREATE TABLE Bloque_compra (
    ID_BLOQUE    INT          NOT NULL AUTO_INCREMENT,
    ID_PROV      INT          NOT NULL,
    FECHA        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         VARCHAR(200),
    RECONSTRUIDO BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_BLOQUE),
    CONSTRAINT fk_bloque_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

ALTER TABLE Compra_componente
    ADD COLUMN ID_BLOQUE INT NULL AFTER ID_PROV,
    ADD CONSTRAINT fk_compra_bloque FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    ADD INDEX idx_compra_bloque (ID_BLOQUE);

-- Verificación post: SELECT COUNT(*) FROM Bloque_compra;  -- 0 (los bloques los crea reconstruir-bloques-compra.sql)
```

Crear `sql/reconstruir-bloques-compra.sql`:

```sql
-- 0.9.9: reconstrucción de los bloques de las líneas que no tienen (spec «Pedidos por bloques» §4).
-- REPETIBLE: solo toca líneas sin bloque. Se ejecuta tras migracion-bloques-compra.sql, otra vez después de desplegar
-- el servidor 0.9.9 (recoge las líneas que el 0.9.8 creó en el hueco) y después de cualquier vuelta atrás al 0.9.8.
USE gestion_reparaciones;

-- ── Reconstrucción ────────────────────────────────────────────────────────────
-- Un bloque RECONSTRUIDO por cada (proveedor, día de FECHA_PEDIDO) con líneas sin bloque que aún no tenga uno; su
-- FECHA es la FECHA_PEDIDO más antigua del grupo.
INSERT INTO Bloque_compra (ID_PROV, FECHA, RECONSTRUIDO)
SELECT cc.ID_PROV, MIN(cc.FECHA_PEDIDO), TRUE
FROM Compra_componente cc
WHERE cc.ID_BLOQUE IS NULL
  AND NOT EXISTS (SELECT 1 FROM Bloque_compra b
                  WHERE b.RECONSTRUIDO AND b.ID_PROV = cc.ID_PROV AND DATE(b.FECHA) = DATE(cc.FECHA_PEDIDO))
GROUP BY cc.ID_PROV, DATE(cc.FECHA_PEDIDO);

-- Cada línea sin bloque, al reconstruido de su proveedor y día. UPDATED_AT = UPDATED_AT: sin él, el
-- ON UPDATE CURRENT_TIMESTAMP cambiaría la fecha de modificación de todas las líneas.
UPDATE Compra_componente cc
JOIN Bloque_compra b ON b.RECONSTRUIDO AND b.ID_PROV = cc.ID_PROV AND DATE(b.FECHA) = DATE(cc.FECHA_PEDIDO)
SET cc.ID_BLOQUE = b.ID_BLOQUE, cc.UPDATED_AT = cc.UPDATED_AT
WHERE cc.ID_BLOQUE IS NULL;

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS lineas_sin_bloque FROM Compra_componente WHERE ID_BLOQUE IS NULL;                         -- 0
SELECT COUNT(*) AS bloques_vacios FROM Bloque_compra b
 WHERE NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE);                  -- 0
SELECT COUNT(*) AS lineas_de_otro_proveedor FROM Compra_componente cc
  JOIN Bloque_compra b ON b.ID_BLOQUE = cc.ID_BLOQUE WHERE b.ID_PROV <> cc.ID_PROV;                       -- 0
SELECT COUNT(*) AS reconstruidos FROM Bloque_compra WHERE RECONSTRUIDO;
SELECT MAX(UPDATED_AT) AS ultima_modificacion FROM Compra_componente;  -- igual que antes de la migración
```

- [ ] **Step 5: `crear_bd.sql` y `docs/schema.md`**

En `sql/crear_bd.sql`:
1. Justo después de `DROP TABLE IF EXISTS Compra_componente;` añadir la línea `DROP TABLE IF EXISTS Bloque_compra;`.
2. Justo antes de `CREATE TABLE Compra_componente (` añadir:

```sql
CREATE TABLE Bloque_compra (
    ID_BLOQUE    INT          NOT NULL AUTO_INCREMENT,
    ID_PROV      INT          NOT NULL,
    FECHA        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         VARCHAR(200),
    RECONSTRUIDO BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_BLOQUE),
    CONSTRAINT fk_bloque_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

```

3. En `CREATE TABLE Compra_componente`, tras `    ID_PROV              INT           NOT NULL,` añadir `    ID_BLOQUE            INT,` y sustituir

```sql
    CONSTRAINT fk_compra_componente FOREIGN KEY (ID_COM)  REFERENCES Componente (ID_COM),
    CONSTRAINT fk_compra_proveedor  FOREIGN KEY (ID_PROV) REFERENCES Proveedor  (ID_PROV)
);
```

por

```sql
    CONSTRAINT fk_compra_componente FOREIGN KEY (ID_COM)    REFERENCES Componente    (ID_COM),
    CONSTRAINT fk_compra_proveedor  FOREIGN KEY (ID_PROV)   REFERENCES Proveedor     (ID_PROV),
    CONSTRAINT fk_compra_bloque     FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    INDEX idx_compra_bloque (ID_BLOQUE)
);
```

En `docs/schema.md`, en la lista de tablas, justo antes de la línea `` - `Compra_componente` `` añadir `` - `Bloque_compra` (0.9.9: cabecera de pedido a un proveedor; `Compra_componente.ID_BLOQUE` apunta a ella) ``.

- [ ] **Step 6: Modelo `CompraComponente`**

En `model/CompraComponente.java`, tras el campo `updatedAt` añadir:

```java
    // 0.9.9: datos del bloque de la línea (spec «Pedidos por bloques» §5.1). Nulos solo en el hueco de un despliegue.
    @Schema(nullable = true) private Integer       idBloque;
    @Schema(nullable = true) private LocalDateTime fechaBloque;
    @Schema(nullable = true) private String        notaBloque;
    @Schema(nullable = true) private LocalDateTime bloqueUpdatedAt;
    private boolean       bloqueAntiguo;
```

y, después de `getUpdatedAt()`, antes de la llave de cierre de la clase:

```java

    /** Rellena los datos del bloque (los pone el MAPPER del DAO). Los tests que no los necesitan usan solo el
     *  constructor, que los deja vacíos. */
    public CompraComponente conBloque(Integer idBloque, LocalDateTime fechaBloque, String notaBloque,
                                      LocalDateTime bloqueUpdatedAt, boolean bloqueAntiguo) {
        this.idBloque        = idBloque;
        this.fechaBloque     = fechaBloque;
        this.notaBloque      = notaBloque;
        this.bloqueUpdatedAt = bloqueUpdatedAt;
        this.bloqueAntiguo   = bloqueAntiguo;
        return this;
    }

    public Integer       getIdBloque()           { return idBloque; }
    public LocalDateTime getFechaBloque()        { return fechaBloque; }
    public String        getNotaBloque()         { return notaBloque; }
    public LocalDateTime getBloqueUpdatedAt()    { return bloqueUpdatedAt; }
    public boolean       isBloqueAntiguo()       { return bloqueAntiguo; }
```

- [ ] **Step 7: `CompraComponenteDAO`**

Sustituir `SELECT_BASE` y `MAPPER` por:

```java
    private static final String SELECT_BASE =
            "SELECT cc.ID_COMPRA, cc.ID_COM, c.TIPO, cc.ID_PROV, p.NOMBRE AS NOMBRE_PROV," +
            " cc.CANTIDAD, cc.CANTIDAD_RECIBIDA, cc.ES_URGENTE," +
            " cc.FECHA_PEDIDO, cc.FECHA_LLEGADA," +
            " cc.PRECIO_UNIDAD_PEDIDO, cc.DIVISA, cc.PRECIO_EUR," +
            " cc.ESTADO, cc.UPDATED_AT," +
            " cc.ID_BLOQUE, b.FECHA AS FECHA_BLOQUE, b.NOTA AS NOTA_BLOQUE, b.UPDATED_AT AS UPDATED_AT_BLOQUE, b.RECONSTRUIDO" +
            " FROM Compra_componente cc" +
            " JOIN Componente c ON cc.ID_COM = c.ID_COM" +
            " JOIN Proveedor p ON cc.ID_PROV = p.ID_PROV" +
            " LEFT JOIN Bloque_compra b ON b.ID_BLOQUE = cc.ID_BLOQUE";

    private static final RowMapper<CompraComponente> MAPPER = (rs, row) -> {
        Timestamp tsLlegada = rs.getTimestamp("FECHA_LLEGADA");
        Timestamp tsBloque = rs.getTimestamp("FECHA_BLOQUE");
        Timestamp tsBloqueAt = rs.getTimestamp("UPDATED_AT_BLOQUE");
        return new CompraComponente(
                rs.getInt("ID_COMPRA"),
                rs.getInt("ID_COM"),
                rs.getString("TIPO"),
                rs.getInt("ID_PROV"),
                rs.getString("NOMBRE_PROV"),
                rs.getInt("CANTIDAD"),
                rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                rs.getBoolean("ES_URGENTE"),
                rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime(),
                tsLlegada != null ? tsLlegada.toLocalDateTime() : null,
                rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
                rs.getString("DIVISA"),
                rs.getDouble("PRECIO_EUR"),
                rs.getString("ESTADO"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()
        ).conBloque(
                rs.getObject("ID_BLOQUE", Integer.class),
                tsBloque != null ? tsBloque.toLocalDateTime() : null,
                rs.getString("NOTA_BLOQUE"),
                tsBloqueAt != null ? tsBloqueAt.toLocalDateTime() : null,
                rs.getBoolean("RECONSTRUIDO"));
    };
```

Junto a las demás constantes `MSG_*` añadir:

```java
    static final String MSG_LINEA_NO_EXISTE = "El pedido ya no existe";
```

y, tras `confirmar(...)`:

```java
    /** Cambia el bloque de una línea (0.9.9). UPDATED_AT cambia: es un cambio real de la línea. 409 si ya no existe. */
    public void moverABloque(int idCompra, int idBloque) {
        int n = jdbc.update("UPDATE Compra_componente SET ID_BLOQUE=? WHERE ID_COMPRA=?", idBloque, idCompra);
        exigir(n, MSG_LINEA_NO_EXISTE);
    }
```

- [ ] **Step 8: `BloqueCompraDAO`**

Crear `dao/BloqueCompraDAO.java`:

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Cabeceras de pedido de compra (0.9.9, spec «Pedidos por bloques» §3-§4). Un bloque es de un proveedor y nunca está
 *  vacío: quien lo vacía lo borra en la misma transacción ({@link #borrarSiVacio}). Su estado no se guarda: se deriva
 *  de sus líneas. */
@Repository
public class BloqueCompraDAO {

    public record Bloque(int idBloque, int idProv, LocalDateTime fecha, String nota, boolean reconstruido,
                         LocalDateTime updatedAt) {}

    /** Lo que las acciones de bloque comprueban y registran de cada línea. `updatedAt` truncado a segundos, como
     *  CompraComponenteDAO.getCompraRow. */
    public record LineaBloque(int idCompra, String tipo, int cantidad, String estado, LocalDateTime updatedAt) {}

    static final String SQL_ABIERTO =
            "SELECT b.ID_BLOQUE FROM Bloque_compra b" +
            " WHERE b.ID_PROV = ?" +
            " AND EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE AND cc.ESTADO <> 'pendiente')" +
            " ORDER BY b.FECHA DESC, b.ID_BLOQUE DESC LIMIT 1";
    static final String SQL_LINEAS =
            "SELECT cc.ID_COMPRA, c.TIPO, cc.CANTIDAD, cc.ESTADO, cc.UPDATED_AT FROM Compra_componente cc" +
            " JOIN Componente c ON c.ID_COM = cc.ID_COM WHERE cc.ID_BLOQUE = ? ORDER BY cc.FECHA_PEDIDO, cc.ID_COMPRA";
    static final String SQL_CONTAR = "SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ?";
    static final String SQL_BORRAR_SI_VACIO =
            "DELETE FROM Bloque_compra WHERE ID_BLOQUE = ?" +
            " AND NOT EXISTS (SELECT 1 FROM Compra_componente WHERE ID_BLOQUE = ?)";
    static final String SQL_CAMBIAR_NOTA = "UPDATE Bloque_compra SET NOTA = ? WHERE ID_BLOQUE = ?";

    private final JdbcTemplate jdbc;

    public BloqueCompraDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int crear(int idProv) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Bloque_compra (ID_PROV, FECHA) VALUES (?, NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idProv);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_BLOQUE del bloque creado");
        return id.intValue();
    }

    public Optional<Bloque> getById(int idBloque) {
        return jdbc.query(
                "SELECT ID_BLOQUE, ID_PROV, FECHA, NOTA, RECONSTRUIDO, UPDATED_AT FROM Bloque_compra WHERE ID_BLOQUE = ?",
                (rs, row) -> new Bloque(rs.getInt("ID_BLOQUE"), rs.getInt("ID_PROV"),
                        rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("NOTA"), rs.getBoolean("RECONSTRUIDO"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                idBloque).stream().findFirst();
    }

    /** El bloque abierto (todas sus líneas pendientes) más reciente del proveedor, si lo hay (spec §3.3 y §3.7). */
    public Optional<Integer> abiertoMasReciente(int idProv) {
        return jdbc.query(SQL_ABIERTO, (rs, row) -> rs.getInt("ID_BLOQUE"), idProv).stream().findFirst();
    }

    public List<LineaBloque> lineas(int idBloque) {
        return jdbc.query(SQL_LINEAS,
                (rs, row) -> new LineaBloque(rs.getInt("ID_COMPRA"), rs.getString("TIPO"), rs.getInt("CANTIDAD"),
                        rs.getString("ESTADO"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                idBloque);
    }

    public int contarLineas(int idBloque) {
        Integer n = jdbc.queryForObject(SQL_CONTAR, Integer.class, idBloque);
        return n == null ? 0 : n;
    }

    /** Borra el bloque si se ha quedado sin líneas (regla §3.2). Devuelve si lo borró. */
    public boolean borrarSiVacio(int idBloque) {
        return jdbc.update(SQL_BORRAR_SI_VACIO, idBloque, idBloque) > 0;
    }

    /** Nota nula = sin nota. Cambia UPDATED_AT del bloque (control de cambios de la nota). */
    public void cambiarNota(int idBloque, String nota) {
        jdbc.update(SQL_CAMBIAR_NOTA, nota, idBloque);
    }
}
```

- [ ] **Step 9: Ejecutar los tests**

Run: `mvn -q test -Dtest='BloqueCompraDAOTest,CompraComponenteDAOBloqueTest,CompraComponenteDAO*'`
Expected: PASS.
Run: `mvn -q test`
Expected: todo en verde (nadie llama todavía a lo nuevo).

- [ ] **Step 10: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git add sql/migracion-bloques-compra.sql sql/reconstruir-bloques-compra.sql sql/crear_bd.sql docs/schema.md src/main/java/com/reparaciones/servidor/model/CompraComponente.java src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java src/main/java/com/reparaciones/servidor/dao/BloqueCompraDAO.java src/test/java/com/reparaciones/servidor/dao/BloqueCompraDAOTest.java src/test/java/com/reparaciones/servidor/dao/CompraComponenteDAOBloqueTest.java
git commit -m "feat: tabla Bloque_compra, bloque en cada linea de pedido y su DAO"
```

---

### Task 2: Servidor — toda alta de línea cae en un bloque

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/DestinoPedido.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/LoteCompras.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/DestinoBloque.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/BloqueCompraService.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/CompraComponenteDAO.java` (`insertar` con `idBloque`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/CompraLoteService.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/RegistroBloques.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraLoteController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java` (constructor e `insertar`)
- Test: `…/service/DestinoBloqueTest.java` (nuevo), `…/service/BloqueCompraServiceTest.java` (nuevo), `…/service/CompraLoteServiceTest.java`, `…/service/CompraLoteServiceTransaccionTest.java`, `…/controller/CompraLoteControllerTest.java`, `…/controller/RolesCompraLoteTest.java`, `…/controller/CompraControllerTest.java`, `…/controller/CompraControllerEnCaminoTest.java`, `…/dao/CompraComponenteDAOInsertarTest.java`

**Interfaces:**
- Consumes: `BloqueCompraDAO.crear/getById/abiertoMasReciente` (Task 1).
- Produces: `record DestinoPedido(Integer idBloque)` (nulo = bloque nuevo; publicado como esquema `DestinoPedido`); `LoteCompras.Peticion(List<Linea> lineas, Solicitudes solicitudes, List<Destino> destinos)` y `LoteCompras.Destino(int idProv, Integer idBloque)`; `DestinoBloque.resolver(int idProv, DestinoPedido pedido, boolean preferirAbierto): DestinoBloque.Destino` con `record Destino(int idBloque, boolean nuevo)` (pedido nulo = regla por defecto); `CompraComponenteDAO.insertar(int idCom, int idProv, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur, int idBloque): int`; `CompraLoteService.guardarCompras(List<LineaCompra>, List<Integer> urgentes, List<Integer> preventivas, Map<Integer, DestinoPedido> destinos): ResultadoLote` con `record ResultadoLote(LoteCompras.Respuesta respuesta, List<BloqueCreado> bloquesCreados)` y `record BloqueCreado(int idBloque, int idProv)`; `BloqueCompraService.altaSuelta(int idCom, int idProv, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur): DestinoBloque.Destino`; `record BloqueCompraService.Movimiento(int idCompra, String estado, Integer de, int a, boolean bloqueNuevo, int idProv, boolean origenBorrado)`; `RegistroBloques.creado(int idUsu, int idBloque, int idProv)`, `vaciado(int idUsu, int idBloque)`, `movimiento(int idUsu, Movimiento m)`.

- [ ] **Step 1: Test de `DestinoBloque` que falla**

Crear `src/test/java/com/reparaciones/servidor/service/DestinoBloqueTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Regla de destino de una línea nueva o que cambia de proveedor (0.9.9, spec §5.2). */
class DestinoBloqueTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0);
    private final BloqueCompraDAO dao = mock(BloqueCompraDAO.class);
    private final DestinoBloque destino = new DestinoBloque(dao);

    private static BloqueCompraDAO.Bloque bloque(int id, int idProv) {
        return new BloqueCompraDAO.Bloque(id, idProv, AT, null, false, AT);
    }

    @Test void sinPedidoVaAlAbiertoMasRecienteDelProveedor() {
        when(dao.abiertoMasReciente(2)).thenReturn(Optional.of(41));
        assertEquals(new DestinoBloque.Destino(41, false), destino.resolver(2, null, true));
        verify(dao, never()).crear(anyInt());
    }

    @Test void sinPedidoNiAbiertoCreaUnoNuevo() {
        when(dao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), destino.resolver(2, null, true));
    }

    @Test void sinPreferirAbiertoCreaSiempreUnoNuevo() {
        when(dao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), destino.resolver(2, null, false));
        verify(dao, never()).abiertoMasReciente(anyInt());
    }

    @Test void pedidoConBloqueNuloCreaUnoNuevo() {
        when(dao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), destino.resolver(2, new DestinoPedido(null), true));
        verify(dao, never()).abiertoMasReciente(anyInt());
    }

    @Test void pedidoConUnBloqueDelProveedorVaAEse() {
        when(dao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        assertEquals(new DestinoBloque.Destino(41, false), destino.resolver(2, new DestinoPedido(41), true));
    }

    @Test void unBloqueDeOtroProveedorEs422() {
        when(dao.getById(41)).thenReturn(Optional.of(bloque(41, 3)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> destino.resolver(2, new DestinoPedido(41), true));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El bloque 41 es de otro proveedor.", e.getReason());
    }

    @Test void unBloqueQueYaNoExisteEs409() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> destino.resolver(2, new DestinoPedido(99), true));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El bloque 99 ya no existe; elige otro destino.", e.getReason());
        verify(dao, never()).crear(anyInt());
    }
}
```

Run: `mvn -q test -Dtest=DestinoBloqueTest`
Expected: FAIL de compilación (`DestinoBloque`, `DestinoPedido` no existen).

- [ ] **Step 2: `DestinoPedido`, `LoteCompras` y `DestinoBloque`**

Crear `model/DestinoPedido.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Bloque elegido por la web para una línea (0.9.9, spec «Pedidos por bloques» §5.2): `idBloque` nulo = bloque nuevo.
 *  Donde el destino es opcional, no mandarlo (null) = regla por defecto. */
public record DestinoPedido(@Schema(nullable = true, description = "Nulo = bloque nuevo") Integer idBloque) {}
```

En `model/LoteCompras.java`, sustituir

```java
    public record Peticion(List<Linea> lineas, Solicitudes solicitudes) {}
```

por

```java
    public record Peticion(List<Linea> lineas, Solicitudes solicitudes,
                           @Schema(nullable = true) List<Destino> destinos) {}

    /** 0.9.9 (spec «Pedidos por bloques» §5.2): bloque al que van las líneas de un proveedor; `idBloque` nulo = bloque
     *  nuevo. Un proveedor sin entrada sigue la regla por defecto (su abierto más reciente o uno nuevo). */
    public record Destino(int idProv, @Schema(nullable = true) Integer idBloque) {}
```

Crear `service/DestinoBloque.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** A qué bloque va una línea nueva o que cambia de proveedor (0.9.9, spec «Pedidos por bloques» §5.2). No abre
 *  transacción: la pone quien lo llama (el lote, el alta suelta, editar y mover). */
@Component
public class DestinoBloque {

    static final String MSG_OTRO_PROVEEDOR = "El bloque %d es de otro proveedor.";
    static final String MSG_NO_EXISTE = "El bloque %d ya no existe; elige otro destino.";

    public record Destino(int idBloque, boolean nuevo) {}

    private final BloqueCompraDAO dao;

    public DestinoBloque(BloqueCompraDAO dao) {
        this.dao = dao;
    }

    /** `pedido` nulo = regla por defecto: con `preferirAbierto`, el abierto más reciente del proveedor o uno nuevo; sin
     *  él, siempre uno nuevo. `pedido` con `idBloque` nulo = bloque nuevo; con número, ese bloque (409 si ya no existe,
     *  422 si es de otro proveedor). Un destino que dejó de estar abierto se acepta: añadir líneas pendientes siempre es
     *  seguro (es lo que hace «Añadir líneas…»). */
    public Destino resolver(int idProv, DestinoPedido pedido, boolean preferirAbierto) {
        if (pedido == null) {
            if (preferirAbierto) {
                Optional<Integer> abierto = dao.abiertoMasReciente(idProv);
                if (abierto.isPresent()) return new Destino(abierto.get(), false);
            }
            return new Destino(dao.crear(idProv), true);
        }
        if (pedido.idBloque() == null) return new Destino(dao.crear(idProv), true);
        int idBloque = pedido.idBloque();
        BloqueCompraDAO.Bloque b = dao.getById(idBloque).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, MSG_NO_EXISTE.formatted(idBloque)));
        if (b.idProv() != idProv) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_OTRO_PROVEEDOR.formatted(idBloque));
        }
        return new Destino(idBloque, false);
    }
}
```

No se ejecuta todavía: Maven compila todos los tests antes de correr ninguno y, hasta el Step 5, varios construyen
`LoteCompras.Peticion` con dos argumentos (y desde el Step 3, `insertar` cambia de firma). Todo se ejecuta en el Step 7.

- [ ] **Step 3: `insertar` con el bloque**

En `dao/CompraComponenteDAO.java` sustituir el método `insertar` entero por:

```java
    /** Devuelve el ID_COMPRA generado (el lote lo devuelve a la web, sub-proyecto 4b; el POST suelto lo ignora).
     *  El pedido se guarda siempre en el master del SKU compartido y en el bloque `idBloque` (0.9.9). */
    public int insertar(int idCom, int idProv, int cantidad, boolean esUrgente,
                        double precioUnidad, String divisa, double precioEur, int idBloque) {
        int idMaster = resolveToMasterId(idCom);
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Compra_componente" +
                    " (ID_COM, ID_PROV, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO, ID_BLOQUE)" +
                    " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idMaster);
            ps.setInt(2, idProv);
            ps.setInt(3, cantidad);
            ps.setBoolean(4, esUrgente);
            ps.setDouble(5, precioUnidad);
            ps.setString(6, divisa);
            ps.setDouble(7, precioEur);
            ps.setInt(8, idBloque);
            return ps;
        }, claves);
        Number id = claves.getKey();
        if (id == null) throw new IllegalStateException("La BD no devolvió el ID_COMPRA del pedido insertado");
        return id.intValue();
    }
```

En `src/test/.../dao/CompraComponenteDAOInsertarTest.java`: la llamada pasa a
`new CompraComponenteDAO(jdbc).insertar(12, 2, 3, true, 10.0, "USD", 8.8, 90);`, el nombre del test a
`insertaEnElMasterYEnSuBloqueYDevuelveElIdGenerado` y, tras `verify(ps).setDouble(7, 8.8);`, se añade
`verify(ps).setInt(8, 90);`.

- [ ] **Step 4: `BloqueCompraService` (alta suelta) y `RegistroBloques`**

Crear `service/BloqueCompraService.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operaciones sobre bloques de pedido (0.9.9, spec «Pedidos por bloques» §5). Cada método es una transacción: un
 *  fallo a mitad no deja nada a medias (ni un bloque vacío, ni una línea fuera de su bloque). */
@Service
public class BloqueCompraService {

    /** Una línea que cambió de bloque: de dónde a dónde, si el destino es nuevo y si el origen se quedó vacío (y se
     *  borró). `de` es nulo para una línea que no tenía bloque (hueco de un despliegue). */
    public record Movimiento(int idCompra, String estado, Integer de, int a, boolean bloqueNuevo, int idProv,
                             boolean origenBorrado) {}

    private final BloqueCompraDAO bloqueDao;
    private final CompraComponenteDAO compraDao;
    private final DestinoBloque destinoBloque;

    public BloqueCompraService(BloqueCompraDAO bloqueDao, CompraComponenteDAO compraDao, DestinoBloque destinoBloque) {
        this.bloqueDao = bloqueDao;
        this.compraDao = compraDao;
        this.destinoBloque = destinoBloque;
    }

    /** POST /api/compras (alta suelta; la web usa el lote): regla por defecto y alta en la misma transacción. */
    @Transactional
    public DestinoBloque.Destino altaSuelta(int idCom, int idProv, int cantidad, boolean esUrgente,
                                            double precioUnidad, String divisa, double precioEur) {
        DestinoBloque.Destino d = destinoBloque.resolver(idProv, null, true);
        compraDao.insertar(idCom, idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, d.idBloque());
        return d;
    }
}
```

(`bloqueDao` lo usan las tareas siguientes; se inyecta ya para no cambiar el constructor otra vez.)

Crear `controller/RegistroBloques.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.service.BloqueCompraService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

/** Logs de los bloques (0.9.9, spec «Pedidos por bloques» §5.4) que comparten los controllers de pedidos. Se llaman
 *  después de la transacción, como el resto de logs. */
@Component
public class RegistroBloques {

    private final LogDAO logDao;
    private final ProveedorDAO proveedorDao;

    public RegistroBloques(LogDAO logDao, ProveedorDAO proveedorDao) {
        this.logDao = logDao;
        this.proveedorDao = proveedorDao;
    }

    public void creado(int idUsu, int idBloque, int idProv) {
        logDao.insertar(idUsu, "CREAR_BLOQUE", "BLOQUE: " + idBloque + ", PROVEEDOR: " + nombre(idProv));
    }

    public void vaciado(int idUsu, int idBloque) {
        logDao.insertar(idUsu, "BORRAR_BLOQUE", "BLOQUE: " + idBloque + " (se quedó sin líneas)");
    }

    /** CREAR_BLOQUE si el destino es nuevo, MOVER_LINEA_BLOQUE y BORRAR_BLOQUE si el origen se quedó vacío. */
    public void movimiento(int idUsu, BloqueCompraService.Movimiento m) {
        if (m.bloqueNuevo()) creado(idUsu, m.a(), m.idProv());
        logDao.insertar(idUsu, "MOVER_LINEA_BLOQUE", "ID_COMPRA: " + m.idCompra() + ", ESTADO: " + m.estado()
                + ", DE: " + (m.de() == null ? "-" : m.de()) + " A: " + m.a());
        if (m.origenBorrado() && m.de() != null) vaciado(idUsu, m.de());
    }

    /** getNombreById lanza con un id inexistente: un log no debe tumbar la petición. */
    private String nombre(int idProv) {
        try {
            return proveedorDao.getNombreById(idProv);
        } catch (EmptyResultDataAccessException e) {
            return "ID_PROV " + idProv;
        }
    }
}
```

- [ ] **Step 5: Tests del lote y del alta suelta (se adaptan y se amplían)**

Sustituir `src/test/.../service/CompraLoteServiceTest.java` entero por:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LoteCompras;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas del guardado por lotes de pedidos con las DAO mockeadas (sin transacción real; la frontera transaccional
 *  la prueba CompraLoteServiceTransaccionTest). */
class CompraLoteServiceTest {

    private final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    private final CompraOtroDAO compraOtroDao = mock(CompraOtroDAO.class);
    private final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);
    private final DestinoBloque destinoBloque = mock(DestinoBloque.class);
    private final CompraLoteService servicio =
            new CompraLoteService(compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao, destinoBloque);

    CompraLoteServiceTest() {
        when(destinoBloque.resolver(eq(2), isNull(), eq(true))).thenReturn(new DestinoBloque.Destino(90, false));
    }

    private static CompraLoteService.LineaCompra linea(int idCom, int cantidad) {
        return new CompraLoteService.LineaCompra(idCom, 2, cantidad, false, 10.0, "USD", 8.8);
    }

    private static CompraLoteService.LineaCompra lineaDe(int idProv, int idCom) {
        return new CompraLoteService.LineaCompra(idCom, idProv, 1, false, 10.0, "EUR", 10.0);
    }

    @Test void insertaCadaLineaEnOrdenEnSuBloqueYDevuelveSusIds() {
        when(compraDao.insertar(1, 2, 3, false, 10.0, "USD", 8.8, 90)).thenReturn(41);
        when(compraDao.insertar(7, 2, 1, false, 10.0, "USD", 8.8, 90)).thenReturn(42);

        CompraLoteService.ResultadoLote r = servicio.guardarCompras(List.of(linea(1, 3), linea(7, 1)), List.of(), List.of(), Map.of());

        assertEquals(List.of(41, 42), r.respuesta().idsCreados());
        assertEquals(List.of(), r.bloquesCreados());
        InOrder orden = inOrder(compraDao);
        orden.verify(compraDao).insertar(1, 2, 3, false, 10.0, "USD", 8.8, 90);
        orden.verify(compraDao).insertar(7, 2, 1, false, 10.0, "USD", 8.8, 90);
        verifyNoInteractions(reparacionComponenteDao, solicitudStockDao, compraOtroDao);
    }

    /** El destino se resuelve una vez por proveedor, con lo que eligió la web o con la regla por defecto. */
    @Test void cadaProveedorVaASuDestinoYLosBloquesNuevosSeDevuelven() {
        when(destinoBloque.resolver(eq(3), eq(new DestinoPedido(null)), eq(true))).thenReturn(new DestinoBloque.Destino(91, true));

        CompraLoteService.ResultadoLote r = servicio.guardarCompras(
                List.of(lineaDe(2, 1), lineaDe(3, 4), lineaDe(2, 7)), List.of(), List.of(), Map.of(3, new DestinoPedido(null)));

        verify(compraDao).insertar(1, 2, 1, false, 10.0, "EUR", 10.0, 90);
        verify(compraDao).insertar(4, 3, 1, false, 10.0, "EUR", 10.0, 91);
        verify(compraDao).insertar(7, 2, 1, false, 10.0, "EUR", 10.0, 90);
        verify(destinoBloque, times(1)).resolver(eq(2), isNull(), eq(true));
        verify(destinoBloque, times(1)).resolver(eq(3), eq(new DestinoPedido(null)), eq(true));
        assertEquals(List.of(new CompraLoteService.BloqueCreado(91, 3)), r.bloquesCreados());
    }

    @Test void marcaLasSolicitudesComoGestionadasDespuesDeInsertar() {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(41);

        servicio.guardarCompras(List.of(linea(1, 2)), List.of(11, 12), List.of(21), Map.of());

        InOrder orden = inOrder(compraDao, reparacionComponenteDao, solicitudStockDao);
        orden.verify(compraDao).insertar(1, 2, 2, false, 10.0, "USD", 8.8, 90);
        orden.verify(reparacionComponenteDao).actualizarEstadoSolicitud(11, "GESTIONADA");
        orden.verify(reparacionComponenteDao).actualizarEstadoSolicitud(12, "GESTIONADA");
        orden.verify(solicitudStockDao).actualizarEstado(21, "GESTIONADA");
    }

    @Test void unFalloEnLaSegundaLineaSePropagaSinMarcarSolicitudes() {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), anyInt()))
                .thenReturn(41)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarCompras(List.of(linea(1, 1), linea(7, 1)), List.of(11), List.of(21), Map.of()));

        verifyNoInteractions(reparacionComponenteDao, solicitudStockDao);
    }

    // ── Task 5 (4b): otros pedidos ──
    private static CompraLoteService.LineaOtro otro(String concepto, int cantidad) {
        return new CompraLoteService.LineaOtro(2, concepto, cantidad, false, 1.5, "EUR", 1.5);
    }

    @Test void guardarOtrosInsertaCadaLineaEnOrdenYDevuelveSusIds() {
        when(compraOtroDao.insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5)).thenReturn(51);
        when(compraOtroDao.insertar(2, "Bolsas", 1, false, 1.5, "EUR", 1.5)).thenReturn(52);

        LoteCompras.Respuesta r = servicio.guardarOtros(List.of(otro("Cinta de embalar", 3), otro("Bolsas", 1)));

        assertEquals(List.of(51, 52), r.idsCreados());
        InOrder orden = inOrder(compraOtroDao);
        orden.verify(compraOtroDao).insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5);
        orden.verify(compraOtroDao).insertar(2, "Bolsas", 1, false, 1.5, "EUR", 1.5);
        verifyNoInteractions(compraDao, reparacionComponenteDao, solicitudStockDao, destinoBloque);
    }

    @Test void guardarOtrosPropagaUnFalloEnLaSegundaLinea() {
        when(compraOtroDao.insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(51)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarOtros(List.of(otro("Cinta de embalar", 1), otro("Bolsas", 1))));
    }
}
```

En `src/test/.../service/CompraLoteServiceTransaccionTest.java`:
1. Junto a los otros mocks estáticos, `private static final DestinoBloque destinoBloque = mock(DestinoBloque.class);`.
2. En `Config.compraLoteService()`: `return new CompraLoteService(compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao, destinoBloque);`.
3. En `resetMocks()`, el `reset(...)` incluye `destinoBloque` y, después, `when(destinoBloque.resolver(anyInt(), any(), anyBoolean())).thenReturn(new DestinoBloque.Destino(90, false));`.
4. Los dos `when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))` ganan un `anyInt()` final.
5. Las dos llamadas `servicio.guardarCompras(…, List.of(11), List.of())` y `servicio.guardarCompras(…, List.of(11), List.of(21))` ganan un último argumento `Map.of()` (import `java.util.Map`).

En `src/test/.../controller/CompraLoteControllerTest.java`:
1. Imports `com.reparaciones.servidor.model.DestinoPedido` y `java.util.Map`.
2. El constructor del controller gana un último argumento: `new CompraLoteController(servicio, componenteDao, proveedorDao, reparacionComponenteDao, solicitudStockDao, new ConversionEur(tipoCambio), logDao, new RegistroIdempotencia(), new RegistroBloques(logDao, proveedorDao));`.
3. Helper nuevo junto a `linea(...)`:

```java
    private static CompraLoteService.ResultadoLote resultado(Integer... ids) {
        return new CompraLoteService.ResultadoLote(new LoteCompras.Respuesta(List.of(ids)), List.of());
    }
```

4. Sustituciones en todo el fichero: `guardarCompras(anyList(), anyList(), anyList())` → `guardarCompras(anyList(), anyList(), anyList(), anyMap())`; `.thenReturn(new LoteCompras.Respuesta(List.of(41)))` (el del constructor) → `.thenReturn(resultado(41))`; `.thenReturn(new LoteCompras.Respuesta(List.of(41, 42)))` → `.thenReturn(resultado(41, 42))`; `guardarCompras(anyList(), eq(List.of(11)), eq(List.of(21)))` → `guardarCompras(anyList(), eq(List.of(11)), eq(List.of(21)), anyMap())`; en `laDivisaEsLaDelProveedorYElEurSeCalculaAntesDelServicio`, `eq(List.of()), eq(List.of()));` → `eq(List.of()), eq(List.of()), eq(Map.of()));`; `new LoteCompras.Peticion(Arrays.asList(lineas), null)` → `new LoteCompras.Peticion(Arrays.asList(lineas), null, null)`; `new LoteCompras.Peticion(Arrays.asList(lineas), new LoteCompras.Solicitudes(urgentes, preventivas))` → `new LoteCompras.Peticion(Arrays.asList(lineas), new LoteCompras.Solicitudes(urgentes, preventivas), null)`; `new LoteCompras.Peticion(null, null)` → `new LoteCompras.Peticion(null, null, null)`.
5. Tests nuevos al final de la sección de compras (antes de `// ── Task 5`):

```java
    @Test void losDestinosElegidosLleganAlServicioPorProveedor() {
        LoteCompras.Peticion peticion = new LoteCompras.Peticion(List.of(linea(1, 2, 1, 0.0), linea(4, 3, 1, 0.0)), null,
                List.of(new LoteCompras.Destino(2, 41), new LoteCompras.Destino(3, null)));
        ctl.guardarLoteCompras(peticion, super7, CLAVE);
        verify(servicio).guardarCompras(anyList(), eq(List.of()), eq(List.of()),
                eq(Map.of(2, new DestinoPedido(41), 3, new DestinoPedido(null))));
    }

    @Test void cadaBloqueNuevoSeRegistraConSuProveedor() {
        when(servicio.guardarCompras(anyList(), anyList(), anyList(), anyMap())).thenReturn(new CompraLoteService.ResultadoLote(
                new LoteCompras.Respuesta(List.of(41)), List.of(new CompraLoteService.BloqueCreado(90, 2))));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
        ctl.guardarLoteCompras(lote(linea(1, 2, 1, 0.0)), super7, CLAVE);
        verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 90, PROVEEDOR: Proveedor A");
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-x-negro, PROVEEDOR: Proveedor A, CANT: 1");
    }
```

En `src/test/.../controller/RolesCompraLoteTest.java`: `when(servicio.guardarCompras(anyList(), anyList(), anyList())).thenReturn(new LoteCompras.Respuesta(List.of(41)));` → `when(servicio.guardarCompras(anyList(), anyList(), anyList(), anyMap())).thenReturn(new CompraLoteService.ResultadoLote(new LoteCompras.Respuesta(List.of(41)), List.of()));` (import estático `org.mockito.ArgumentMatchers.anyMap`).

En `src/test/.../controller/CompraControllerTest.java`:
1. Imports `com.reparaciones.servidor.service.BloqueCompraService` y `com.reparaciones.servidor.service.DestinoBloque`.
2. Mock nuevo `private final BloqueCompraService bloques = mock(BloqueCompraService.class);` declarado **antes** de `ctl`, y `ctl` pasa a `new CompraController(dao, logDao, componenteDao, proveedorDao, new ConversionEur(tipoCambio), bloques, new RegistroBloques(logDao, proveedorDao));`.
3. En el constructor del test, `when(bloques.altaSuelta(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble())).thenReturn(new DestinoBloque.Destino(90, false));`.
4. En `nadaEscritoNiRegistrado()`, `verify(dao, never()).insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble());` → `verify(bloques, never()).altaSuelta(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble());`.
5. `verify(dao).insertar(1, 2, 3, false, 0.0, "EUR", 0.0);` → `verify(bloques).altaSuelta(1, 2, 3, false, 0.0, "EUR", 0.0);` y `verify(dao).insertar(1, 2, 3, false, 10.0, "USD", 8.8);` → `verify(bloques).altaSuelta(1, 2, 3, false, 10.0, "USD", 8.8);`.
6. Test nuevo:

```java
    @Test void altaQueCreaBloqueLoRegistra() {
        when(bloques.altaSuelta(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(new DestinoBloque.Destino(91, true));
        ctl.insertar(alta(1, 2, 3, 0.0, "EUR"), super7);
        verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 91, PROVEEDOR: Proveedor A");
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-x-negro, PROVEEDOR: Proveedor A, CANT: 3");
    }
```

En `src/test/.../controller/CompraControllerEnCaminoTest.java`: `mock(ProveedorDAO.class), mock(ConversionEur.class));` → `mock(ProveedorDAO.class), mock(ConversionEur.class), mock(BloqueCompraService.class), mock(RegistroBloques.class));` (import `com.reparaciones.servidor.service.BloqueCompraService`).

Crear `src/test/.../service/BloqueCompraServiceTest.java` (las tareas 3-5 le añaden tests):

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.model.CompraComponente;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BloqueCompraService con las DAO mockeadas y el DestinoBloque real (0.9.9, spec «Pedidos por bloques» §5). */
class BloqueCompraServiceTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    final BloqueCompraDAO bloqueDao = mock(BloqueCompraDAO.class);
    final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    final BloqueCompraService servicio = new BloqueCompraService(bloqueDao, compraDao, new DestinoBloque(bloqueDao));

    static BloqueCompraDAO.Bloque bloque(int id, int idProv) {
        return new BloqueCompraDAO.Bloque(id, idProv, AT, null, false, AT);
    }

    /** Línea 5 del proveedor 2 en el bloque `idBloque`, con UPDATED_AT = AT. */
    static CompraComponente linea(String estado, Integer idBloque) {
        return new CompraComponente(5, 1, "lcd-x-negro", 2, "Proveedor A", 3, null, false, AT, null, 10.0, "EUR", 10.0,
                estado, AT).conBloque(idBloque, AT, null, AT, false);
    }

    static void falla(HttpStatus estado, String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(estado, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    // ── alta suelta ──
    @Test void altaSueltaVaAlAbiertoDelProveedor() {
        when(bloqueDao.abiertoMasReciente(2)).thenReturn(Optional.of(41));
        assertEquals(new DestinoBloque.Destino(41, false), servicio.altaSuelta(1, 2, 3, false, 10.0, "EUR", 10.0));
        verify(compraDao).insertar(1, 2, 3, false, 10.0, "EUR", 10.0, 41);
    }

    @Test void altaSueltaSinAbiertoCreaUnBloque() {
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), servicio.altaSuelta(1, 2, 3, false, 10.0, "EUR", 10.0));
        verify(compraDao).insertar(1, 2, 3, false, 10.0, "EUR", 10.0, 90);
    }
}
```

(Las tareas siguientes usan `falla`, `linea` y `bloque`; si el compilador avisa de que `falla` y `linea` aún no se usan, es normal.)

- [ ] **Step 6: `CompraLoteService`, `CompraLoteController` y `CompraController`**

En `service/CompraLoteService.java`:
1. Imports `com.reparaciones.servidor.model.DestinoPedido`, `java.util.HashMap`, `java.util.Map`.
2. Tras los records `LineaCompra` y `LineaOtro`:

```java
    public record BloqueCreado(int idBloque, int idProv) {}

    /** Respuesta de la API y bloques nuevos del lote (0.9.9): sus logs van tras la transacción, en el controlador. */
    public record ResultadoLote(LoteCompras.Respuesta respuesta, List<BloqueCreado> bloquesCreados) {}
```

3. Campo `private final DestinoBloque destinoBloque;`, último parámetro del constructor (`DestinoBloque destinoBloque`) y su asignación.
4. Sustituir `guardarCompras` entero por:

```java
    /** Un alta por línea (insertar resuelve al master) en el bloque de su proveedor y después cada solicitud a
     *  GESTIONADA con los mismos métodos que los PATCH de la campana. El destino de cada proveedor se resuelve una vez
     *  (0.9.9): el de `destinos` si viene; si no, su abierto más reciente o uno nuevo. */
    @Transactional
    public ResultadoLote guardarCompras(List<LineaCompra> lineas, List<Integer> urgentes, List<Integer> preventivas,
                                       Map<Integer, DestinoPedido> destinos) {
        Map<Integer, Integer> bloquePorProveedor = new HashMap<>();
        List<BloqueCreado> creados = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (LineaCompra l : lineas) {
            Integer idBloque = bloquePorProveedor.get(l.idProv());
            if (idBloque == null) {
                DestinoBloque.Destino d = destinoBloque.resolver(l.idProv(), destinos.get(l.idProv()), true);
                idBloque = d.idBloque();
                bloquePorProveedor.put(l.idProv(), idBloque);
                if (d.nuevo()) creados.add(new BloqueCreado(idBloque, l.idProv()));
            }
            ids.add(compraDao.insertar(l.idCom(), l.idProv(), l.cantidad(), l.esUrgente(),
                    l.precioUnidad(), l.divisa(), l.precioEur(), idBloque));
        }
        for (Integer idRc : urgentes) reparacionComponenteDao.actualizarEstadoSolicitud(idRc, GESTIONADA);
        for (Integer idSol : preventivas) solicitudStockDao.actualizarEstado(idSol, GESTIONADA);
        return new ResultadoLote(new LoteCompras.Respuesta(ids), creados);
    }
```

En `controller/CompraLoteController.java`:
1. Imports `com.reparaciones.servidor.model.DestinoPedido`, `java.util.HashMap`, `java.util.Map`.
2. Campo `private final RegistroBloques registroBloques;`, último parámetro del constructor y su asignación.
3. En `guardarLoteCompras`, sustituir desde `int idUsu = principal.getIdUsu();` hasta el `);` del `return` por:

```java
        Map<Integer, DestinoPedido> destinos = destinos(req.destinos());
        int idUsu = principal.getIdUsu();
        return idempotencia.ejecutar(idUsu, OP_COMPRAS, claveIdempotencia, req,
                // La tasa (Frankfurter: lenta y externa) se resuelve aquí, ANTES de entrar en la transacción del servicio.
                () -> servicio.guardarCompras(resolverCompras(lineas), urgentes, preventivas, destinos),
                r -> {
                    for (CompraLoteService.BloqueCreado b : r.bloquesCreados()) registroBloques.creado(idUsu, b.idBloque(), b.idProv());
                    registrarLogsCompras(lineas, urgentes, preventivas, idUsu);
                }).respuesta();
```

4. Junto a `lista(...)`:

```java
    /** Destino por proveedor (0.9.9): estar en la lista = elegido por la web; idBloque nulo = bloque nuevo. Con un
     *  proveedor repetido vale el último. */
    static Map<Integer, DestinoPedido> destinos(List<LoteCompras.Destino> lista) {
        Map<Integer, DestinoPedido> m = new HashMap<>();
        if (lista != null) {
            for (LoteCompras.Destino d : lista) {
                if (d != null) m.put(d.idProv(), new DestinoPedido(d.idBloque()));
            }
        }
        return m;
    }
```

En `controller/CompraController.java`:
1. Imports `com.reparaciones.servidor.service.BloqueCompraService` y `com.reparaciones.servidor.service.DestinoBloque`.
2. Campos `private final BloqueCompraService bloques;` y `private final RegistroBloques registroBloques;`; el constructor pasa a
   `public CompraController(CompraComponenteDAO dao, LogDAO logDao, ComponenteDAO componenteDao, ProveedorDAO proveedorDao, ConversionEur conversion, BloqueCompraService bloques, RegistroBloques registroBloques)` con sus dos asignaciones nuevas.
3. En `insertar`, sustituir

```java
        dao.insertar(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur);
        String tipo = componenteDao.getTipoById(req.idCom());
        String proveedor = proveedorDao.getNombreById(req.idProv());
```

por

```java
        // 0.9.9: el alta cae en el bloque abierto del proveedor o en uno nuevo (regla por defecto).
        DestinoBloque.Destino destino = bloques.altaSuelta(req.idCom(), req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur);
        String tipo = componenteDao.getTipoById(req.idCom());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        if (destino.nuevo()) registroBloques.creado(principal.getIdUsu(), destino.idBloque(), req.idProv());
```

- [ ] **Step 7: Ejecutar las suites**

Run: `mvn -q test -Dtest='DestinoBloqueTest,BloqueCompraServiceTest,CompraLoteServiceTest,CompraLoteServiceTransaccionTest,CompraLoteControllerTest,RolesCompraLoteTest,CompraControllerTest,CompraControllerEnCaminoTest,CompraComponenteDAOInsertarTest'`
Expected: PASS.
Run: `mvn -q test`
Expected: todo en verde.

- [ ] **Step 8: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git add -A src/main/java src/test/java
git commit -m "feat: toda alta de pedido cae en un bloque (destinos del lote y regla por defecto)"
```

---

### Task 3: Servidor — mover una línea, editar cambiando el proveedor y borrar la última línea

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/BloqueCompraService.java` (`mover`, `editarCambiandoProveedor`, `borrarLineaPendiente`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java` (`editar`, `borrar`, `EditarRequest.destino`)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/BloqueCompraController.java` (solo `PATCH /api/compras/{idCompra}/bloque`)
- Test: `…/service/BloqueCompraServiceTest.java`, `…/controller/CompraControllerTest.java`, `…/controller/BloqueCompraControllerTest.java` (nuevo)

**Interfaces:**
- Consumes: `DestinoBloque.resolver`, `BloqueCompraDAO.contarLineas/borrarSiVacio/crear`, `CompraComponenteDAO.getById/editar/moverABloque/borrarPendiente`, `RegistroBloques` (Tasks 1-2).
- Produces: `BloqueCompraService.mover(int idCompra, DestinoPedido destino, LocalDateTime updatedAt): Movimiento`; `editarCambiandoProveedor(int idCompra, int idProv, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt, DestinoPedido destino): Movimiento`; `borrarLineaPendiente(int idCompra): Optional<Integer>` (el bloque borrado por quedarse vacío); `static boolean mismaVersion(LocalDateTime bd, LocalDateTime cliente)`; `CompraController.EditarRequest(..., LocalDateTime updatedAt, DestinoPedido destino)` (esquema `CompraEditarRequest.destino`, nullable); `BloqueCompraController(BloqueCompraService servicio, RegistroBloques registro, LogDAO logDao)` con `record MoverRequest(Integer idBloque, LocalDateTime updatedAt)` (esquema `BloqueCompraMoverRequest`).

- [ ] **Step 1: Tests del servicio que fallan**

En `src/test/.../service/BloqueCompraServiceTest.java`, añadir los imports `com.reparaciones.servidor.model.DestinoPedido` y `org.mockito.InOrder`, y al final de la clase:

```java
    // ── mover ──
    @Test void moverUnaPendienteAOtroBloqueDelProveedor() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        BloqueCompraService.Movimiento m = servicio.mover(5, new DestinoPedido(40), AT);
        verify(compraDao).moverABloque(5, 40);
        verify(bloqueDao).borrarSiVacio(41);
        assertEquals(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false), m);
    }

    @Test void moverABloqueNuevoCreaUnoDelMismoProveedor() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.contarLineas(41)).thenReturn(2);
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new BloqueCompraService.Movimiento(5, "en_camino", 41, 90, true, 2, false),
                servicio.mover(5, new DestinoPedido(null), AT));
        verify(compraDao).moverABloque(5, 90);
    }

    @Test void elOrigenQueSeQuedaVacioSeBorra() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        assertTrue(servicio.mover(5, new DestinoPedido(40), AT).origenBorrado());
    }

    @Test void moverAlMismoBloqueOSolaABloqueNuevoEs422SinTocarNada() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.contarLineas(41)).thenReturn(1);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "La línea ya está en ese bloque.", () -> servicio.mover(5, new DestinoPedido(41), AT));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "La línea ya está sola en su bloque.", () -> servicio.mover(5, new DestinoPedido(null), AT));
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
        verify(bloqueDao, never()).crear(anyInt());
    }

    @Test void moverAUnBloqueDeOtroProveedorEs422() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 3)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 40 es de otro proveedor.", () -> servicio.mover(5, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    @Test void moverConOtraVersionEs409YUnaLineaInexistenteEs404() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        falla(HttpStatus.CONFLICT, "Dato modificado por otro usuario", () -> servicio.mover(5, new DestinoPedido(40), AT.plusSeconds(1)));
        falla(HttpStatus.NOT_FOUND, "El pedido no existe", () -> servicio.mover(6, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    // ── editar cambiando el proveedor ──
    @Test void editarUnaPendienteAOtroProveedorVaASuAbierto() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.abiertoMasReciente(3)).thenReturn(Optional.of(60));
        BloqueCompraService.Movimiento m = servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null);
        InOrder orden = inOrder(compraDao);
        orden.verify(compraDao).editar(5, 3, 4, false, 12.0, "EUR", 12.0, AT);
        orden.verify(compraDao).moverABloque(5, 60);
        assertEquals(new BloqueCompraService.Movimiento(5, "pendiente", 41, 60, false, 3, false), m);
    }

    @Test void editarUnaYaPedidaSinDestinoCreaUnBloqueNuevo() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.crear(3)).thenReturn(91);
        assertEquals(91, servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null).a());
        verify(bloqueDao, never()).abiertoMasReciente(anyInt());
    }

    @Test void editarConDestinoElegidoUsaEseBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.getById(60)).thenReturn(Optional.of(bloque(60, 3)));
        assertEquals(60, servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, new DestinoPedido(60)).a());
    }

    @Test void siEditarFallaNoSeTocaNingunBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario"))
                .when(compraDao).editar(5, 3, 4, false, 12.0, "EUR", 12.0, AT);
        assertThrows(ResponseStatusException.class,
                () -> servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null));
        verify(bloqueDao, never()).crear(anyInt());
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    // ── borrar una línea pendiente ──
    @Test void borrarLaUltimaLineaBorraSuBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        assertEquals(Optional.of(41), servicio.borrarLineaPendiente(5));
        verify(compraDao).borrarPendiente(5);
    }

    @Test void borrarUnaLineaDeUnBloqueConMasNoLoBorra() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        assertEquals(Optional.empty(), servicio.borrarLineaPendiente(5));
        verify(compraDao).borrarPendiente(5);
    }
```

Run: `mvn -q test -Dtest=BloqueCompraServiceTest`
Expected: FAIL de compilación (`mover`, `editarCambiandoProveedor`, `borrarLineaPendiente` no existen).

- [ ] **Step 2: Implementar en `BloqueCompraService`**

Imports nuevos: `com.reparaciones.servidor.model.CompraComponente`, `com.reparaciones.servidor.model.DestinoPedido`, `org.springframework.http.HttpStatus`, `org.springframework.web.server.ResponseStatusException`, `java.time.LocalDateTime`, `java.time.temporal.ChronoUnit`, `java.util.Optional`. Constantes al principio de la clase:

```java
    static final String MSG_NO_EXISTE_LINEA = "El pedido no existe";
    static final String MSG_LINEA_MODIFICADA = "Dato modificado por otro usuario";
    static final String MSG_YA_EN_ESE = "La línea ya está en ese bloque.";
    static final String MSG_YA_SOLA = "La línea ya está sola en su bloque.";
```

Métodos, después de `altaSuelta`:

```java
    /** PATCH /api/compras/{id}/bloque (spec §5.3). `destino.idBloque()` nulo = bloque nuevo. El servidor no distingue
     *  estados: mover no toca el stock (spec §2); la confirmación de una línea ya pedida la pide la web. */
    @Transactional
    public Movimiento mover(int idCompra, DestinoPedido destino, LocalDateTime updatedAt) {
        CompraComponente l = compraDao.getById(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_EXISTE_LINEA));
        if (!mismaVersion(l.getUpdatedAt(), updatedAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_LINEA_MODIFICADA);
        }
        Integer origen = l.getIdBloque();
        if (destino.idBloque() == null && origen != null && bloqueDao.contarLineas(origen) <= 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_YA_SOLA);
        }
        if (destino.idBloque() != null && destino.idBloque().equals(origen)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_YA_EN_ESE);
        }
        DestinoBloque.Destino d = destinoBloque.resolver(l.getIdProv(), destino, false);
        compraDao.moverABloque(idCompra, d.idBloque());
        boolean borrado = origen != null && bloqueDao.borrarSiVacio(origen);
        return new Movimiento(idCompra, l.getEstado(), origen, d.idBloque(), d.nuevo(), l.getIdProv(), borrado);
    }

    /** PUT /api/compras/{id} con otro proveedor (spec §5.2): edita, lleva la línea a un bloque del proveedor nuevo y
     *  borra el de origen si se queda vacío, todo junto. Sin `destino`, la regla por defecto: pendiente → su abierto más
     *  reciente o uno nuevo; ya pedida → uno nuevo. */
    @Transactional
    public Movimiento editarCambiandoProveedor(int idCompra, int idProv, int cantidad, boolean esUrgente,
                                               double precioUnidad, String divisa, double precioEur,
                                               LocalDateTime updatedAt, DestinoPedido destino) {
        CompraComponente l = compraDao.getById(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_EXISTE_LINEA));
        compraDao.editar(idCompra, idProv, cantidad, esUrgente, precioUnidad, divisa, precioEur, updatedAt);
        DestinoBloque.Destino d = destinoBloque.resolver(idProv, destino, "pendiente".equals(l.getEstado()));
        compraDao.moverABloque(idCompra, d.idBloque());
        Integer origen = l.getIdBloque();
        boolean borrado = origen != null && bloqueDao.borrarSiVacio(origen);
        return new Movimiento(idCompra, l.getEstado(), origen, d.idBloque(), d.nuevo(), idProv, borrado);
    }

    /** DELETE /api/compras/{id}: borra la línea pendiente y, si era la última, su bloque. Devuelve ese bloque. */
    @Transactional
    public Optional<Integer> borrarLineaPendiente(int idCompra) {
        Integer origen = compraDao.getById(idCompra).map(CompraComponente::getIdBloque).orElse(null);
        compraDao.borrarPendiente(idCompra);
        return origen != null && bloqueDao.borrarSiVacio(origen) ? Optional.of(origen) : Optional.empty();
    }

    /** Bloqueo optimista a segundos, como CompraComponenteDAO.checkUpdatedAt. */
    static boolean mismaVersion(LocalDateTime bd, LocalDateTime cliente) {
        return bd != null && cliente != null
                && bd.truncatedTo(ChronoUnit.SECONDS).equals(cliente.truncatedTo(ChronoUnit.SECONDS));
    }
```

Run: `mvn -q test -Dtest=BloqueCompraServiceTest`
Expected: PASS.

- [ ] **Step 3: Tests de los controllers que fallan**

En `src/test/.../controller/CompraControllerTest.java`:
1. El helper `edicion(...)` pasa a `return new CompraController.EditarRequest(idProv, cantidad, false, precio, divisa, precio, AT, null);` y las dos construcciones directas `new CompraController.EditarRequest(2, 4, false, 10.0, "usd", 999.0, AT)` y `new CompraController.EditarRequest(2, 4, false, 10.0, "USD", 999.0, AT)` ganan `, null` al final.
2. Tests nuevos al final:

```java
    // ── 0.9.9: editar cambiando el proveedor y borrar ──
    @Test void editarCambiandoElProveedorMueveLaLineaYLoRegistra() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 4, null)));   // proveedor 2
        when(proveedorDao.getById(3)).thenReturn(Optional.of(new Proveedor(3, "Proveedor B", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getNombreById(3)).thenReturn("Proveedor B");
        when(bloques.editarCambiandoProveedor(5, 3, 4, false, 12.5, "EUR", 12.5, AT, null))
                .thenReturn(new BloqueCompraService.Movimiento(5, "en_camino", 41, 91, true, 3, true));

        ctl.editar(5, edicion(3, 4, 12.5, "EUR"), super7);

        verify(dao, never()).editar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), any());
        verify(logDao).insertar(7, "EDITAR_PEDIDO", "ID_COMPRA: 5");
        verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 91, PROVEEDOR: Proveedor B");
        verify(logDao).insertar(7, "MOVER_LINEA_BLOQUE", "ID_COMPRA: 5, ESTADO: en_camino, DE: 41 A: 91");
        verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41 (se quedó sin líneas)");
    }

    @Test void editarSinCambiarElProveedorNoTocaLosBloques() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 4, null)));
        ctl.editar(5, edicion(2, 4, 12.5, "EUR"), super7);
        verify(dao).editar(5, 2, 4, false, 12.5, "EUR", 12.5, AT);
        verify(bloques, never()).editarCambiandoProveedor(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), any(), any());
    }

    @Test void borrarLaUltimaLineaDeUnBloqueRegistraElBloqueBorrado() {
        when(bloques.borrarLineaPendiente(5)).thenReturn(Optional.of(41));
        ctl.borrar(5, super7);
        verify(logDao).insertar(7, "BORRAR_PEDIDO", "ID_COMPRA: 5");
        verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41 (se quedó sin líneas)");
    }

    @Test void borrarUnaLineaDeUnBloqueConMasSoloRegistraElPedido() {
        ctl.borrar(5, super7);
        verify(bloques).borrarLineaPendiente(5);
        verify(logDao).insertar(7, "BORRAR_PEDIDO", "ID_COMPRA: 5");
        verifyNoMoreInteractions(logDao);
    }
```

Crear `src/test/.../controller/BloqueCompraControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Logs de BloqueCompraController (0.9.9, spec «Pedidos por bloques» §5.4) con el servicio mockeado. Los roles los
 *  prueba RolesBloquesCompraTest con la cadena de seguridad real. */
class BloqueCompraControllerTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    final BloqueCompraService servicio = mock(BloqueCompraService.class);
    final LogDAO logDao = mock(LogDAO.class);
    final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    final BloqueCompraController ctl = new BloqueCompraController(servicio, new RegistroBloques(logDao, proveedorDao), logDao);
    final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    BloqueCompraControllerTest() {
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
    }

    @Test void moverRegistraElMovimiento() {
        when(servicio.mover(5, new DestinoPedido(40), AT))
                .thenReturn(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false));
        ctl.mover(5, new BloqueCompraController.MoverRequest(40, AT), super7);
        verify(logDao).insertar(7, "MOVER_LINEA_BLOQUE", "ID_COMPRA: 5, ESTADO: pendiente, DE: 41 A: 40");
        verifyNoMoreInteractions(logDao);
    }

    @Test void moverABloqueNuevoQueVaciaElOrigenRegistraLosTresEnOrden() {
        when(servicio.mover(5, new DestinoPedido(null), AT))
                .thenReturn(new BloqueCompraService.Movimiento(5, "en_camino", 41, 90, true, 2, true));
        ctl.mover(5, new BloqueCompraController.MoverRequest(null, AT), super7);
        InOrder orden = inOrder(logDao);
        orden.verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 90, PROVEEDOR: Proveedor A");
        orden.verify(logDao).insertar(7, "MOVER_LINEA_BLOQUE", "ID_COMPRA: 5, ESTADO: en_camino, DE: 41 A: 90");
        orden.verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41 (se quedó sin líneas)");
    }

    @Test void unFalloDelServicioNoRegistraNada() {
        when(servicio.mover(5, new DestinoPedido(40), AT))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario"));
        assertThrows(ResponseStatusException.class,
                () -> ctl.mover(5, new BloqueCompraController.MoverRequest(40, AT), super7));
        verifyNoInteractions(logDao);
    }
}
```

Run: `mvn -q test -Dtest='CompraControllerTest,BloqueCompraControllerTest'`
Expected: FAIL de compilación (`EditarRequest` con 7 campos, `BloqueCompraController` no existe).

- [ ] **Step 4: `CompraController` y `BloqueCompraController`**

En `controller/CompraController.java`:
1. Imports `com.reparaciones.servidor.model.DestinoPedido` y `java.util.Optional`.
2. Sustituir el cuerpo de `editar` (desde `ValidacionPedidos.cantidadPositiva` hasta el log) por:

```java
        ValidacionPedidos.cantidadPositiva(req.cantidad());
        ValidacionPedidos.precioNoNegativo(req.precioUnidad());
        String divisa = ValidacionPedidos.divisaValida(req.divisa());
        ValidacionPedidos.proveedorActivo(proveedorDao, req.idProv());
        Optional<CompraComponente> actual = dao.getById(idCompra);
        actual.ifPresent(c -> ValidacionPedidos.cantidadEditable(c.getEstado(), c.getCantidad(), req.cantidad()));
        double precioEur = conversion.aEuros(req.precioUnidad(), divisa);
        // 0.9.9: otro proveedor saca la línea de su bloque (regla 1: un proveedor por bloque), en la misma transacción.
        if (actual.isPresent() && actual.get().getIdProv() != req.idProv()) {
            BloqueCompraService.Movimiento m = bloques.editarCambiandoProveedor(idCompra, req.idProv(), req.cantidad(),
                    req.esUrgente(), req.precioUnidad(), divisa, precioEur, req.updatedAt(), req.destino());
            logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
            registroBloques.movimiento(principal.getIdUsu(), m);
            return;
        }
        dao.editar(idCompra, req.idProv(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), divisa, precioEur, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO", "ID_COMPRA: " + idCompra);
```

3. Sustituir el cuerpo de `borrar` por:

```java
        Optional<Integer> vaciado = bloques.borrarLineaPendiente(idCompra);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PEDIDO", "ID_COMPRA: " + idCompra);
        vaciado.ifPresent(idBloque -> registroBloques.vaciado(principal.getIdUsu(), idBloque));
```

4. `EditarRequest` pasa a:

```java
    record EditarRequest(int idProv, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa,
                         @Schema(nullable = true, description = "Ignorado: el servidor calcula el importe en euros")
                         Double precioEur,
                         LocalDateTime updatedAt,
                         @Schema(nullable = true, description = "0.9.9: bloque destino si cambia el proveedor; nulo = regla por defecto")
                         DestinoPedido destino) {}
```

Crear `controller/BloqueCompraController.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** Bloques de pedido (0.9.9, spec «Pedidos por bloques» §5.3): mover líneas y acciones sobre un bloque. Solo
 *  SUPERTECNICO, como el resto de escrituras de compras. Los logs van después de la transacción del servicio. */
@RestController
@RequestMapping("/api")
public class BloqueCompraController {

    private final BloqueCompraService servicio;
    private final RegistroBloques registro;
    private final LogDAO logDao;

    public BloqueCompraController(BloqueCompraService servicio, RegistroBloques registro, LogDAO logDao) {
        this.servicio = servicio;
        this.registro = registro;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/compras/{idCompra}/bloque")
    public void mover(@PathVariable int idCompra, @RequestBody MoverRequest req,
                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Movimiento m = servicio.mover(idCompra, new DestinoPedido(req.idBloque()), req.updatedAt());
        registro.movimiento(principal.getIdUsu(), m);
    }

    record MoverRequest(@Schema(nullable = true, description = "Nulo = bloque nuevo") Integer idBloque,
                        LocalDateTime updatedAt) {}
}
```

(`logDao` lo usan las tareas 4 y 5.)

- [ ] **Step 5: Ejecutar**

Run: `mvn -q test -Dtest='BloqueCompraServiceTest,CompraControllerTest,BloqueCompraControllerTest'`
Expected: PASS.
Run: `mvn -q test`
Expected: todo en verde.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git add -A src/main/java src/test/java
git commit -m "feat: mover lineas entre bloques y editar el proveedor llevando la linea a su bloque"
```

---

### Task 4: Servidor — confirmar, recibir, cancelar y borrar un bloque

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/LineaVista.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/BloqueCompraService.java` (`Accion`, `Aplicada`, `comprobar`, `aplicar`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/BloqueCompraController.java` (cuatro rutas y sus logs)
- Test: `…/service/BloqueCompraServiceTest.java`, `…/controller/BloqueCompraControllerTest.java`

**Interfaces:**
- Consumes: `BloqueCompraDAO.getById/lineas/borrarSiVacio`, `CompraComponenteDAO.confirmar/confirmarRecibido/cancelar/borrarPendiente`.
- Produces: `record LineaVista(int idCompra, LocalDateTime updatedAt)` (esquema `LineaVista`); `BloqueCompraService.MSG_MODIFICADO = "Este bloque fue modificado por otro usuario."` (public); `enum BloqueCompraService.Accion { CONFIRMAR, RECIBIR, CANCELAR, BORRAR }` con `log()`; `record Aplicada(int idBloque, List<BloqueCompraDAO.LineaBloque> lineas)`; `aplicar(int idBloque, Accion accion, List<LineaVista> vistas): Aplicada`; privado `comprobar(int idBloque, List<LineaVista> vistas, boolean exigirTodas)` que las tareas siguientes reutilizan; `BloqueCompraController.LineasRequest(List<LineaVista> lineas)` (esquema `BloqueCompraLineasRequest`); rutas `POST /api/bloques-compra/{idBloque}/confirmar|recibir|cancelar|borrar`.

- [ ] **Step 1: Tests del servicio que fallan**

En `BloqueCompraServiceTest`, imports `com.reparaciones.servidor.model.LineaVista` y `java.util.List`, y al final:

```java
    // ── acciones de bloque ──
    static final String MODIFICADO = "Este bloque fue modificado por otro usuario.";

    static BloqueCompraDAO.LineaBloque lb(int id, String estado) {
        return new BloqueCompraDAO.LineaBloque(id, "lcd-" + id, 2, estado, AT);
    }

    static LineaVista v(int id) {
        return new LineaVista(id, AT);
    }

    void bloque41(BloqueCompraDAO.LineaBloque... lineas) {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        when(bloqueDao.lineas(41)).thenReturn(List.of(lineas));
    }

    @Test void recibirSoloLasLineasDeLaListaConLaRecepcionDeSiempre() {
        bloque41(lb(1, "en_camino"), lb(2, "en_camino"), lb(3, "recibido"));
        BloqueCompraService.Aplicada a = servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(v(1), v(2)));
        verify(compraDao).confirmarRecibido(1, AT);
        verify(compraDao).confirmarRecibido(2, AT);
        verify(compraDao, never()).confirmarRecibido(eq(3), any());
        assertEquals(new BloqueCompraService.Aplicada(41, List.of(lb(1, "en_camino"), lb(2, "en_camino"))), a);
        verify(bloqueDao, never()).borrarSiVacio(anyInt());
    }

    @Test void confirmarYCancelarUsanSuTransicionDeLinea() {
        bloque41(lb(1, "pendiente"), lb(2, "en_camino"));
        servicio.aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of(v(1)));
        servicio.aplicar(41, BloqueCompraService.Accion.CANCELAR, List.of(v(2)));
        verify(compraDao).confirmar(1, AT);
        verify(compraDao).cancelar(2, AT);
    }

    @Test void otraVersionOtroEstadoOUnaLineaAjenaEs409SinTocarNada() {
        bloque41(lb(1, "en_camino"), lb(2, "recibido"));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(new LineaVista(1, AT.plusSeconds(1)))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(v(1), v(2))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(v(9))));
        verify(compraDao, never()).confirmarRecibido(anyInt(), any());
    }

    @Test void unBloqueQueYaNoExisteEs409YUnaListaVaciaEs422() {
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of(v(1))));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "No hay líneas a las que aplicar la acción.",
                () -> servicio.aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of()));
    }

    @Test void borrarExigeElBloqueEnteroPendienteYLoBorra() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        servicio.aplicar(41, BloqueCompraService.Accion.BORRAR, List.of(v(1), v(2)));
        verify(compraDao).borrarPendiente(1);
        verify(compraDao).borrarPendiente(2);
        verify(bloqueDao).borrarSiVacio(41);
    }

    @Test void borrarConUnaListaIncompletaEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.BORRAR, List.of(v(1))));
        verify(compraDao, never()).borrarPendiente(anyInt());
    }
```

Run: `mvn -q test -Dtest=BloqueCompraServiceTest`
Expected: FAIL de compilación (`LineaVista`, `Accion`, `aplicar` no existen).

- [ ] **Step 2: `LineaVista` y `aplicar`**

Crear `model/LineaVista.java`:

```java
package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

/** Una línea tal como la vio la web (0.9.9, spec «Pedidos por bloques» §5.3): las acciones de bloque la comprueban
 *  contra la base antes de cambiar nada. */
public record LineaVista(int idCompra, LocalDateTime updatedAt) {}
```

En `BloqueCompraService`, imports `com.reparaciones.servidor.model.LineaVista`, `java.util.ArrayList`, `java.util.LinkedHashMap`, `java.util.List`, `java.util.Map`. Constantes:

```java
    public static final String MSG_MODIFICADO = "Este bloque fue modificado por otro usuario.";
    static final String MSG_SIN_LINEAS = "No hay líneas a las que aplicar la acción.";
```

Tipos, tras el record `Movimiento`:

```java
    /** Acción de estado de un bloque: estado que tienen que tener las líneas y nombre del log del bloque. */
    public enum Accion {
        CONFIRMAR("pendiente", "CONFIRMAR_BLOQUE"),
        RECIBIR("en_camino", "RECIBIR_BLOQUE"),
        CANCELAR("en_camino", "CANCELAR_BLOQUE"),
        BORRAR("pendiente", "BORRAR_BLOQUE");

        private final String estado;
        private final String log;

        Accion(String estado, String log) {
            this.estado = estado;
            this.log = log;
        }

        public String log() {
            return log;
        }
    }

    /** Las líneas a las que se aplicó una acción, para sus logs. */
    public record Aplicada(int idBloque, List<BloqueCompraDAO.LineaBloque> lineas) {}

    private record Comprobadas(BloqueCompraDAO.Bloque bloque, List<BloqueCompraDAO.LineaBloque> elegidas, int total) {}
```

Métodos, al final de la clase (antes de `mismaVersion`):

```java
    /** Confirmar, recibir, cancelar o borrar (spec §5.3): solo las líneas de la lista, todas en el estado de la acción;
     *  si no, 409 sin cambiar nada. Cada línea pasa por su transición de siempre (recibir suma stock como «Confirmar
     *  recibido»). Borrar exige el bloque entero y lo borra. */
    @Transactional
    public Aplicada aplicar(int idBloque, Accion accion, List<LineaVista> vistas) {
        Comprobadas c = comprobar(idBloque, vistas, accion == Accion.BORRAR);
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            if (!accion.estado.equals(l.estado())) throw modificado();
        }
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            switch (accion) {
                case CONFIRMAR -> compraDao.confirmar(l.idCompra(), l.updatedAt());
                case RECIBIR -> compraDao.confirmarRecibido(l.idCompra(), l.updatedAt());
                case CANCELAR -> compraDao.cancelar(l.idCompra(), l.updatedAt());
                case BORRAR -> compraDao.borrarPendiente(l.idCompra());
            }
        }
        if (accion == Accion.BORRAR) bloqueDao.borrarSiVacio(idBloque);
        return new Aplicada(idBloque, c.elegidas());
    }

    /** La lista de la web contra la base (spec §5.3): lista vacía → 422; bloque inexistente, línea que no es del
     *  bloque o con otra versión → 409. `exigirTodas` (borrar y juntar): la lista tiene que ser el bloque entero. */
    private Comprobadas comprobar(int idBloque, List<LineaVista> vistas, boolean exigirTodas) {
        if (vistas == null || vistas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_SIN_LINEAS);
        }
        BloqueCompraDAO.Bloque bloque = bloqueDao.getById(idBloque).orElseThrow(BloqueCompraService::modificado);
        Map<Integer, BloqueCompraDAO.LineaBloque> actuales = new LinkedHashMap<>();
        for (BloqueCompraDAO.LineaBloque l : bloqueDao.lineas(idBloque)) actuales.put(l.idCompra(), l);
        Map<Integer, BloqueCompraDAO.LineaBloque> elegidas = new LinkedHashMap<>();
        for (LineaVista v : vistas) {
            BloqueCompraDAO.LineaBloque l = actuales.get(v.idCompra());
            if (l == null || !mismaVersion(l.updatedAt(), v.updatedAt())) throw modificado();
            elegidas.put(l.idCompra(), l);
        }
        if (exigirTodas && elegidas.size() != actuales.size()) throw modificado();
        return new Comprobadas(bloque, new ArrayList<>(elegidas.values()), actuales.size());
    }

    private static ResponseStatusException modificado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, MSG_MODIFICADO);
    }
```

Run: `mvn -q test -Dtest=BloqueCompraServiceTest`
Expected: PASS.

- [ ] **Step 3: Tests del controller que fallan**

En `BloqueCompraControllerTest`, imports `com.reparaciones.servidor.dao.BloqueCompraDAO`, `com.reparaciones.servidor.model.LineaVista`, `java.util.List`, y al final:

```java
    static BloqueCompraDAO.LineaBloque lb(int id, String tipo, int cantidad) {
        return new BloqueCompraDAO.LineaBloque(id, tipo, cantidad, "en_camino", AT);
    }

    @Test void recibirRegistraCadaLineaConSuStockYElBloque() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT), new LineaVista(2, AT));
        when(servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, vistas))
                .thenReturn(new BloqueCompraService.Aplicada(41, List.of(lb(1, "lcd-x-negro", 3), lb(2, "bat-x", 1))));
        ctl.recibir(41, new BloqueCompraController.LineasRequest(vistas), super7);
        InOrder orden = inOrder(logDao);
        orden.verify(logDao).insertar(7, "RECIBIR_PEDIDO", "ID_COMPRA: 1, COMPONENTE: lcd-x-negro, CANT: 3");
        orden.verify(logDao).insertar(7, "RECIBIR_PEDIDO", "ID_COMPRA: 2, COMPONENTE: bat-x, CANT: 1");
        orden.verify(logDao).insertar(7, "RECIBIR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1, 2");
    }

    @Test void confirmarCancelarYBorrarRegistranSusLogs() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT));
        BloqueCompraService.Aplicada una = new BloqueCompraService.Aplicada(41, List.of(lb(1, "lcd-x-negro", 3)));
        when(servicio.aplicar(eq(41), any(), eq(vistas))).thenReturn(una);
        ctl.confirmar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.cancelar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.borrar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        verify(logDao).insertar(7, "CONFIRMAR_PEDIDO", "ID_COMPRA: 1");
        verify(logDao).insertar(7, "CONFIRMAR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1");
        verify(logDao).insertar(7, "CANCELAR_PEDIDO", "ID_COMPRA: 1");
        verify(logDao).insertar(7, "CANCELAR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1");
        verify(logDao).insertar(7, "BORRAR_PEDIDO", "ID_COMPRA: 1");
        verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1");
    }
```

(imports estáticos `org.mockito.ArgumentMatchers.any` y `eq` si no están: con `org.mockito.Mockito.*` ya vienen.)

Run: `mvn -q test -Dtest=BloqueCompraControllerTest`
Expected: FAIL de compilación (`recibir`, `LineasRequest` no existen).

- [ ] **Step 4: Rutas en `BloqueCompraController`**

Imports `com.reparaciones.servidor.dao.BloqueCompraDAO`, `com.reparaciones.servidor.model.LineaVista`, `java.util.List`, `java.util.stream.Collectors`. Métodos, tras `mover`:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/confirmar")
    public void confirmar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.CONFIRMAR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/recibir")
    public void recibir(@PathVariable int idBloque, @RequestBody LineasRequest req,
                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.RECIBIR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/cancelar")
    public void cancelar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.CANCELAR, req, principal);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/borrar")
    public void borrar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        aplicar(idBloque, BloqueCompraService.Accion.BORRAR, req, principal);
    }

    /** Un log por línea (el de su transición de siempre, para rastrear el stock línea a línea) y otro del bloque. */
    private void aplicar(int idBloque, BloqueCompraService.Accion accion, LineasRequest req, UsuarioPrincipal principal) {
        BloqueCompraService.Aplicada a = servicio.aplicar(idBloque, accion, req.lineas());
        int idUsu = principal.getIdUsu();
        for (BloqueCompraDAO.LineaBloque l : a.lineas()) logDao.insertar(idUsu, logDeLinea(accion), detalleDeLinea(accion, l));
        logDao.insertar(idUsu, accion.log(), "BLOQUE: " + a.idBloque() + ", ID_COMPRA: "
                + unir(a.lineas().stream().map(BloqueCompraDAO.LineaBloque::idCompra).toList()));
    }

    static String logDeLinea(BloqueCompraService.Accion accion) {
        return switch (accion) {
            case CONFIRMAR -> "CONFIRMAR_PEDIDO";
            case RECIBIR -> "RECIBIR_PEDIDO";
            case CANCELAR -> "CANCELAR_PEDIDO";
            case BORRAR -> "BORRAR_PEDIDO";
        };
    }

    /** Los mismos detalles que las transiciones por línea de CompraController (RECIBIR_PEDIDO lleva componente y cantidad). */
    static String detalleDeLinea(BloqueCompraService.Accion accion, BloqueCompraDAO.LineaBloque l) {
        return accion == BloqueCompraService.Accion.RECIBIR
                ? "ID_COMPRA: " + l.idCompra() + ", COMPONENTE: " + l.tipo() + ", CANT: " + l.cantidad()
                : "ID_COMPRA: " + l.idCompra();
    }

    static String unir(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    record LineasRequest(List<LineaVista> lineas) {}
```

- [ ] **Step 5: Ejecutar**

Run: `mvn -q test -Dtest='BloqueCompraServiceTest,BloqueCompraControllerTest'`
Expected: PASS.
Run: `mvn -q test`
Expected: todo en verde.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git add -A src/main/java src/test/java
git commit -m "feat: confirmar, recibir, cancelar y borrar un bloque con la lista exacta de lineas"
```

---

### Task 5: Servidor — separar, juntar, nota, roles, contrato y documentación

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/BloqueCompraService.java` (`separar`, `juntar`, `unirNotas`, `cambiarNota`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/BloqueCompraController.java` (tres rutas)
- Test: `…/service/BloqueCompraServiceTest.java`, `…/controller/BloqueCompraControllerTest.java`, `…/controller/RolesBloquesCompraTest.java` (nuevo), `…/OpenApiContractTest.java`
- Modify: `gestion-reparaciones-servidor/docs/autorizacion_endpoints.md`, `gestion-reparaciones-servidor/docs/api_contract.md`

**Interfaces:**
- Produces: `record Separacion(int origen, int nuevo, int idProv, List<Integer> ids)`, `record Union(int origen, int destino, List<Integer> ids)`; `separar(int idBloque, List<LineaVista> vistas): Separacion`; `juntar(int idBloque, int destino, List<LineaVista> vistas): Union`; `cambiarNota(int idBloque, String nota, LocalDateTime updatedAt)`; `static String unirNotas(String destino, String origen)`; rutas `POST /api/bloques-compra/{idBloque}/separar` (responde `BloqueCompraSepararRespuesta {idBloque}`), `POST …/juntar` (`BloqueCompraJuntarRequest {destino, lineas}`), `PATCH …/nota` (`BloqueCompraNotaRequest {nota, updatedAt}`). El contrato `target/openapi.json` queda listo para la web (Task 6).

- [ ] **Step 1: Tests del servicio que fallan**

En `BloqueCompraServiceTest`, al final:

```java
    // ── separar, juntar y nota ──
    @Test void separarCreaUnBloqueDelProveedorYMueveLasMarcadas() {
        bloque41(lb(1, "pendiente"), lb(2, "en_camino"), lb(3, "pendiente"));
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new BloqueCompraService.Separacion(41, 90, 2, List.of(1, 2)), servicio.separar(41, List.of(v(1), v(2))));
        verify(compraDao).moverABloque(1, 90);
        verify(compraDao).moverABloque(2, 90);
        verify(compraDao, never()).moverABloque(eq(3), anyInt());
    }

    @Test void separarTodasEs422YUnaCambiadaEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "Deja al menos una línea en el bloque.", () -> servicio.separar(41, List.of(v(1), v(2))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.separar(41, List.of(new LineaVista(1, AT.plusSeconds(1)))));
        verify(bloqueDao, never()).crear(anyInt());
    }

    @Test void juntarMueveTodoAlDestinoUneLasNotasYBorraElBloque() {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(new BloqueCompraDAO.Bloque(41, 2, AT, "Ped. 2", false, AT)));
        when(bloqueDao.lineas(41)).thenReturn(List.of(lb(1, "pendiente"), lb(2, "en_camino")));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(new BloqueCompraDAO.Bloque(40, 2, AT, "Ped. 1", false, AT)));
        assertEquals(new BloqueCompraService.Union(41, 40, List.of(1, 2)), servicio.juntar(41, 40, List.of(v(1), v(2))));
        verify(compraDao).moverABloque(1, 40);
        verify(compraDao).moverABloque(2, 40);
        verify(bloqueDao).cambiarNota(40, "Ped. 1 · Ped. 2");
        verify(bloqueDao).borrarSiVacio(41);
    }

    @Test void juntarConsigoMismoConOtroProveedorOIncompletoNoTocaNada() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        when(bloqueDao.getById(50)).thenReturn(Optional.of(bloque(50, 3)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "Elige otro bloque.", () -> servicio.juntar(41, 41, List.of(v(1), v(2))));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 50 es de otro proveedor.", () -> servicio.juntar(41, 50, List.of(v(1), v(2))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.juntar(41, 40, List.of(v(1))));
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    @Test void unirNotas() {
        assertEquals("A · B", BloqueCompraService.unirNotas("A", "B"));
        assertEquals("B", BloqueCompraService.unirNotas(null, "B"));
        assertEquals("A", BloqueCompraService.unirNotas("A", " "));
        assertNull(BloqueCompraService.unirNotas(null, null));
        assertEquals(200, BloqueCompraService.unirNotas("x".repeat(150), "y".repeat(150)).length());
    }

    @Test void cambiarNotaRecortaGuardaYVaciaANulo() {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        servicio.cambiarNota(41, "  Ped. 123  ", AT);
        servicio.cambiarNota(41, "   ", AT);
        verify(bloqueDao).cambiarNota(41, "Ped. 123");
        verify(bloqueDao).cambiarNota(41, null);
    }

    @Test void notaLargaEs422YConOtraVersionEs409() {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "La nota no puede superar los 200 caracteres.", () -> servicio.cambiarNota(41, "x".repeat(201), AT));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.cambiarNota(41, "Ped.", AT.plusSeconds(1)));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.cambiarNota(42, "Ped.", AT));
        verify(bloqueDao, never()).cambiarNota(anyInt(), any());
    }
```

Run: `mvn -q test -Dtest=BloqueCompraServiceTest`
Expected: FAIL de compilación.

- [ ] **Step 2: Implementar en el servicio**

Import `java.util.Objects`. Constantes:

```java
    static final String MSG_DEJA_UNA = "Deja al menos una línea en el bloque.";
    static final String MSG_OTRO_BLOQUE = "Elige otro bloque.";
    static final String MSG_NOTA_LARGA = "La nota no puede superar los 200 caracteres.";
    static final int NOTA_MAX = 200; // VARCHAR(200) de Bloque_compra.NOTA
```

Records, tras `Aplicada`:

```java
    public record Separacion(int origen, int nuevo, int idProv, List<Integer> ids) {}

    public record Union(int origen, int destino, List<Integer> ids) {}
```

Métodos, tras `aplicar`:

```java
    /** «Separar líneas…» (spec §5.3): las de la lista pasan a un bloque nuevo del mismo proveedor; al menos una se
     *  queda. Los estados no cuentan (la web ya pidió confirmación por las ya pedidas). */
    @Transactional
    public Separacion separar(int idBloque, List<LineaVista> vistas) {
        Comprobadas c = comprobar(idBloque, vistas, false);
        if (c.elegidas().size() >= c.total()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_DEJA_UNA);
        }
        int nuevo = bloqueDao.crear(c.bloque().idProv());
        List<Integer> ids = new ArrayList<>();
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            compraDao.moverABloque(l.idCompra(), nuevo);
            ids.add(l.idCompra());
        }
        return new Separacion(idBloque, nuevo, c.bloque().idProv(), ids);
    }

    /** «Juntar con…» (spec §5.3): todas las líneas (la lista tiene que ser el bloque entero) pasan a `destino`, del mismo
     *  proveedor; la nota se añade a la del destino y el bloque desaparece. */
    @Transactional
    public Union juntar(int idBloque, int destino, List<LineaVista> vistas) {
        if (destino == idBloque) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_OTRO_BLOQUE);
        Comprobadas c = comprobar(idBloque, vistas, true);
        destinoBloque.resolver(c.bloque().idProv(), new DestinoPedido(destino), false); // 409 si no existe, 422 si es de otro proveedor
        BloqueCompraDAO.Bloque d = bloqueDao.getById(destino).orElseThrow(BloqueCompraService::modificado);
        List<Integer> ids = new ArrayList<>();
        for (BloqueCompraDAO.LineaBloque l : c.elegidas()) {
            compraDao.moverABloque(l.idCompra(), destino);
            ids.add(l.idCompra());
        }
        String nota = unirNotas(d.nota(), c.bloque().nota());
        if (!Objects.equals(nota, d.nota())) bloqueDao.cambiarNota(destino, nota);
        bloqueDao.borrarSiVacio(idBloque);
        return new Union(idBloque, destino, ids);
    }

    /** Nota del destino con la del absorbido añadida tras « · », recortada a 200 (spec §5.3). */
    static String unirNotas(String destino, String origen) {
        boolean sinOrigen = origen == null || origen.isBlank();
        if (sinOrigen) return destino;
        boolean sinDestino = destino == null || destino.isBlank();
        String unida = sinDestino ? origen : destino + " · " + origen;
        return unida.length() > NOTA_MAX ? unida.substring(0, NOTA_MAX) : unida;
    }

    /** PATCH …/nota: recortada; vacía = sin nota; con la versión del bloque que vio la web. */
    @Transactional
    public void cambiarNota(int idBloque, String nota, LocalDateTime updatedAt) {
        String limpia = nota == null ? "" : nota.trim();
        if (limpia.length() > NOTA_MAX) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NOTA_LARGA);
        BloqueCompraDAO.Bloque b = bloqueDao.getById(idBloque).orElseThrow(BloqueCompraService::modificado);
        if (!mismaVersion(b.updatedAt(), updatedAt)) throw modificado();
        bloqueDao.cambiarNota(idBloque, limpia.isEmpty() ? null : limpia);
    }
```

Run: `mvn -q test -Dtest=BloqueCompraServiceTest`
Expected: PASS.

- [ ] **Step 3: Rutas, logs y sus tests**

En `BloqueCompraControllerTest`, al final:

```java
    @Test void separarRegistraElBloqueNuevoYLaSeparacion() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT), new LineaVista(2, AT));
        when(servicio.separar(41, vistas)).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(1, 2)));
        assertEquals(90, ctl.separar(41, new BloqueCompraController.LineasRequest(vistas), super7).idBloque());
        verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 90, PROVEEDOR: Proveedor A");
        verify(logDao).insertar(7, "SEPARAR_BLOQUE", "DE: 41 A: 90, ID_COMPRA: 1, 2");
    }

    @Test void juntarYNotaRegistranSusLogs() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT));
        when(servicio.juntar(41, 40, vistas)).thenReturn(new BloqueCompraService.Union(41, 40, List.of(1)));
        ctl.juntar(41, new BloqueCompraController.JuntarRequest(40, vistas), super7);
        ctl.nota(41, new BloqueCompraController.NotaRequest("Ped. 123", AT), super7);
        verify(servicio).cambiarNota(41, "Ped. 123", AT);
        verify(logDao).insertar(7, "JUNTAR_BLOQUE", "DE: 41 A: 40, ID_COMPRA: 1");
        verify(logDao).insertar(7, "EDITAR_NOTA_BLOQUE", "BLOQUE: 41");
    }
```

(import estático `org.junit.jupiter.api.Assertions.assertEquals`.)

En `BloqueCompraController`, tras `borrar`:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/separar")
    public SepararRespuesta separar(@PathVariable int idBloque, @RequestBody LineasRequest req,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Separacion s = servicio.separar(idBloque, req.lineas());
        int idUsu = principal.getIdUsu();
        registro.creado(idUsu, s.nuevo(), s.idProv());
        logDao.insertar(idUsu, "SEPARAR_BLOQUE", "DE: " + s.origen() + " A: " + s.nuevo() + ", ID_COMPRA: " + unir(s.ids()));
        return new SepararRespuesta(s.nuevo());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/bloques-compra/{idBloque}/juntar")
    public void juntar(@PathVariable int idBloque, @RequestBody JuntarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        BloqueCompraService.Union u = servicio.juntar(idBloque, req.destino(), req.lineas());
        logDao.insertar(principal.getIdUsu(), "JUNTAR_BLOQUE",
                "DE: " + u.origen() + " A: " + u.destino() + ", ID_COMPRA: " + unir(u.ids()));
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/bloques-compra/{idBloque}/nota")
    public void nota(@PathVariable int idBloque, @RequestBody NotaRequest req,
                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        servicio.cambiarNota(idBloque, req.nota(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_NOTA_BLOQUE", "BLOQUE: " + idBloque);
    }

    record JuntarRequest(int destino, List<LineaVista> lineas) {}

    record NotaRequest(@Schema(nullable = true, description = "Vacía o nula = sin nota") String nota,
                       LocalDateTime updatedAt) {}

    record SepararRespuesta(int idBloque) {}
```

- [ ] **Step 4: Roles con la cadena de seguridad real**

Crear `src/test/.../controller/RolesBloquesCompraTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.LineaVista;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las ocho escrituras de bloques (0.9.9): solo SUPERTECNICO, con la cadena de seguridad real y el servicio mockeado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesBloquesCompraTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean BloqueCompraService servicio;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final String AT = "\"updatedAt\":\"2026-10-09T10:00:00\"";
    private static final String LINEAS = "{\"lineas\":[{\"idCompra\":5," + AT + "}]}";

    private record Op(String metodo, String ruta, String cuerpo) {}

    private static final List<Op> ESCRITURAS = List.of(
            new Op("PATCH", "/api/compras/5/bloque", "{\"idBloque\":40," + AT + "}"),
            new Op("POST", "/api/bloques-compra/41/confirmar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/recibir", LINEAS),
            new Op("POST", "/api/bloques-compra/41/cancelar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/borrar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/separar", LINEAS),
            new Op("POST", "/api/bloques-compra/41/juntar", "{\"destino\":40,\"lineas\":[{\"idCompra\":5," + AT + "}]}"),
            new Op("PATCH", "/api/bloques-compra/41/nota", "{\"nota\":\"Ped. 1\"," + AT + "}"));

    private ResultActions llamar(Op op, String token) throws Exception {
        return mvc.perform(request(HttpMethod.valueOf(op.metodo()), op.ruta()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(op.cuerpo()));
    }

    @Test void tecnicoYAdminReciben403EnTodas() throws Exception {
        for (Op op : ESCRITURAS) {
            llamar(op, tecnico()).andExpect(status().isForbidden());
            llamar(op, admin()).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test void elSupertecnicoLlegaAlServicioEnTodas() throws Exception {
        when(servicio.mover(anyInt(), any(), any())).thenReturn(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false));
        when(servicio.aplicar(anyInt(), any(), anyList())).thenReturn(new BloqueCompraService.Aplicada(41, List.of()));
        when(servicio.separar(anyInt(), anyList())).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(5)));
        when(servicio.juntar(anyInt(), anyInt(), anyList())).thenReturn(new BloqueCompraService.Union(41, 40, List.of(5)));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
        for (Op op : ESCRITURAS) llamar(op, supertecnico()).andExpect(status().isOk());
        verify(servicio).separar(41, List.of(new LineaVista(5, LocalDateTime.of(2026, 10, 9, 10, 0))));
    }

    @Test void separarDevuelveElNumeroDelBloqueNuevo() throws Exception {
        when(servicio.separar(anyInt(), anyList())).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(5)));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
        llamar(ESCRITURAS.get(5), supertecnico()).andExpect(status().isOk()).andExpect(jsonPath("$.idBloque").value(90));
    }
}
```

- [ ] **Step 5: Contrato**

En `src/test/.../OpenApiContractTest.java`, añadir un test (mismo patrón que el de los lotes: token de ADMIN, `GET /v3/api-docs`, `paths` y `esquemas`):

```java
    /** 0.9.9 (spec «Pedidos por bloques» §5): rutas y esquemas de los bloques y los campos nuevos de compras. */
    @Test void elContratoPublicaLosBloquesDePedidos() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        assertTrue(paths.path("/api/compras/{idCompra}/bloque").has("patch"), "falta PATCH /api/compras/{idCompra}/bloque");
        for (String accion : List.of("confirmar", "recibir", "cancelar", "borrar", "separar", "juntar")) {
            assertTrue(paths.path("/api/bloques-compra/{idBloque}/" + accion).has("post"), () -> "falta POST …/" + accion);
        }
        assertTrue(paths.path("/api/bloques-compra/{idBloque}/nota").has("patch"), "falta PATCH …/nota");
        assertTrue(refDelCuerpo(paths, "/api/compras/{idCompra}/bloque", "patch").endsWith("/BloqueCompraMoverRequest"));
        assertTrue(refDelCuerpo(paths, "/api/bloques-compra/{idBloque}/recibir", "post").endsWith("/BloqueCompraLineasRequest"));
        assertTrue(refDelCuerpo(paths, "/api/bloques-compra/{idBloque}/juntar", "post").endsWith("/BloqueCompraJuntarRequest"));
        assertTrue(refDelCuerpo(paths, "/api/bloques-compra/{idBloque}/nota", "patch").endsWith("/BloqueCompraNotaRequest"));
        assertTrue(refDeLaRespuesta(paths, "/api/bloques-compra/{idBloque}/separar", "post", "200").endsWith("/BloqueCompraSepararRespuesta"));

        assertNullable(esquemas, "CompraComponente", "idBloque", "fechaBloque", "notaBloque", "bloqueUpdatedAt");
        assertNoNullable(esquemas, "CompraComponente", "bloqueAntiguo");
        assertNullable(esquemas, "LoteComprasPeticion", "destinos");
        assertNullable(esquemas, "LoteComprasDestino", "idBloque");
        assertNoNullable(esquemas, "LoteComprasDestino", "idProv");
        assertNullable(esquemas, "CompraEditarRequest", "destino");
        assertNullable(esquemas, "DestinoPedido", "idBloque");
        assertNoNullable(esquemas, "LineaVista", "idCompra", "updatedAt");
        assertNullable(esquemas, "BloqueCompraMoverRequest", "idBloque");
        assertNoNullable(esquemas, "BloqueCompraJuntarRequest", "destino", "lineas");
        assertNullable(esquemas, "BloqueCompraNotaRequest", "nota");
        assertNoNullable(esquemas, "BloqueCompraSepararRespuesta", "idBloque");
    }
```

Ninguna ruta nueva declara `Idempotency-Key` (el test de idempotencia ya comprueba que nadie fuera de su lista la declara).

- [ ] **Step 6: Documentación**

En `docs/autorizacion_endpoints.md`, tabla «Dónde están los tests», nueva fila tras la de «Roles de solicitudes de stock…»:

```markdown
| Roles de los bloques de pedidos (cadena de seguridad real, MockMvc) | `controller/RolesBloquesCompraTest` |
```

En `docs/api_contract.md`, sección «Convenciones que el OpenAPI no expresa», nuevo punto antes de «Sin sesión: …»:

```markdown
- Bloques de pedidos (0.9.9): `GET /api/compras` trae en cada línea su bloque (`idBloque`, `fechaBloque`, `notaBloque`,
  `bloqueUpdatedAt`, `bloqueAntiguo`). `POST /api/bloques-compra/{idBloque}/confirmar|recibir|cancelar|borrar|separar` y
  `…/juntar` reciben la lista exacta de líneas que vio el cliente (`lineas: [{idCompra, updatedAt}]`) y son todo o nada:
  si una no existe, no es del bloque, cambió o no está en el estado de la acción, `409` "Este bloque fue modificado por
  otro usuario." sin cambiar nada; `borrar` y `juntar` exigen el bloque entero. Un bloque que se queda sin líneas
  desaparece en la misma operación. `POST /api/compras/lote` acepta `destinos: [{idProv, idBloque}]` (`idBloque` nulo =
  bloque nuevo; un proveedor sin entrada va a su bloque abierto más reciente o a uno nuevo) y `PUT /api/compras/{id}`
  acepta `destino: {idBloque}` cuando cambia el proveedor. Solo SUPERTECNICO.
```

- [ ] **Step 7: Suite completa y contrato**

Run: `mvn -q test`
Expected: todo en verde y `target/openapi.json` regenerado (lo deja `OpenApiContractTest`).
Run: `grep -c "bloques-compra" target/openapi.json`
Expected: un número mayor que 0.

- [ ] **Step 8: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git add -A src/main/java src/test/java docs/autorizacion_endpoints.md docs/api_contract.md
git commit -m "feat: separar, juntar y nota de bloques; roles, contrato y documentacion"
```

---

### Task 6: Web — contrato, tipos, datos de prueba y API de bloques

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (generado)
- Modify: `gestion-reparaciones-web/src/test/server.ts` (handler por defecto de `GET /api/compras`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/datosPrueba.ts` (`BLOQUE_PRUEBA`)
- Modify: los literales `CompraComponente` de `pedidos/api.test.tsx`, `CantidadDialog.test.tsx`, `columnas.test.tsx`, `confirmaciones.test.ts`, `filtros.test.ts`, `MenuPedido.test.tsx`, `PedidosPage.test.tsx`, `reglas.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/api.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.ts` (`cuerpoLoteCompras` con `destinos: null`) y `EditarPedidoDialog.tsx` (`destino: null`)
- Test: `gestion-reparaciones-web/src/modules/almacen/pedidos/api.test.tsx`, `formulario/lineas.test.ts`, `formulario/NuevoPedidoDialog.test.tsx`, `formulario/EditarPedidoDialog.test.tsx` (cuerpos esperados)

**Interfaces:**
- Consumes: `target/openapi.json` del servidor (Task 5).
- Produces: `CompraComponente` con `idBloque: number | null`, `fechaBloque: string | null`, `notaBloque: string | null`, `bloqueUpdatedAt: string | null`, `bloqueAntiguo: boolean`; `BLOQUE_PRUEBA` (los cinco campos, bloque 1); en `pedidos/api.ts`: `CuerpoLoteCompras.destinos: { idProv: number; idBloque: number | null }[] | null`, `CuerpoEditarCompra.destino: { idBloque: number | null } | null`, `type LineaVista = { idCompra: number; updatedAt: string }`, `vistaDe(p: CompraComponente): LineaVista`, `type AccionBloque = 'confirmar' | 'recibir' | 'cancelar' | 'borrar'`, `useMoverLinea()` (variables `{ pedido: CompraComponente; idBloque: number | null }`), `useAccionBloque()` (`{ idBloque: number; accion: AccionBloque; lineas: LineaVista[] }`), `useSepararBloque()` (`{ idBloque; lineas }` → `{ idBloque: number } | undefined`), `useJuntarBloque()` (`{ idBloque; destino: number; lineas }`), `useNotaBloque()` (`{ idBloque; nota: string | null; updatedAt: string }`). Todas silencian el diálogo global y recargan Pedidos, Stock y la campana.

- [ ] **Step 1: Contrato y tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-099/target/openapi.json /c/Users/dev/Documents/wt/web-099/api/openapi.json
cd /c/Users/dev/Documents/wt/web-099 && npm run api:types:offline
grep -n "idBloque" src/shared/api/schema.d.ts | head
```

Expected: `idBloque` aparece en `CompraComponente`, `LoteComprasDestino`, `DestinoPedido` y `BloqueCompraMoverRequest`.

Run: `npx tsc -b`
Expected: errores en los literales de `CompraComponente` de los tests, en el cuerpo del lote y en el `PUT` de editar (faltan los campos nuevos). Se arreglan en los pasos 2 y 3.

- [ ] **Step 2: Datos de prueba**

En `formulario/datosPrueba.ts`, después de los imports:

```ts
/** Campos del bloque (0.9.9) para los literales de CompraComponente de los tests: todas las líneas en el bloque 1. */
export const BLOQUE_PRUEBA = {
  idBloque: 1, fechaBloque: '2026-09-20T10:00:00', notaBloque: null, bloqueUpdatedAt: '2026-09-20T10:00:00', bloqueAntiguo: false,
} as const
```

y en `COMPRA`, añadir `...BLOQUE_PRUEBA,` como primera propiedad.

En cada uno de `pedidos/api.test.tsx`, `CantidadDialog.test.tsx`, `columnas.test.tsx`, `confirmaciones.test.ts`, `filtros.test.ts`, `MenuPedido.test.tsx`, `PedidosPage.test.tsx` y `reglas.test.ts`: importar `BLOQUE_PRUEBA` (`from './formulario/datosPrueba'`) y añadir `...BLOQUE_PRUEBA,` como primera propiedad de cada literal base de `CompraComponente` (los `base`, `compra(...)`, `pedido(...)`, etc.; los de `CompraOtro` no cambian).

En `src/test/server.ts`, dentro de `setupServer(`, tras la línea de `pulidos/asignaciones`:

```ts
  // 0.9.9: «Nuevo pedido» y «Editar pedido» leen los pedidos para ofrecer sus bloques; los abren tests de Stock, de la
  // campana y del layout que no van de pedidos. Una lista vacía = ningún bloque abierto (regla por defecto).
  http.get('*/api/compras', () => HttpResponse.json([])),
```

y en el comentario de arriba, «las tres listas de asignaciones vacías» → «las tres listas de asignaciones y la de pedidos vacías».

- [ ] **Step 3: API de pedidos**

En `pedidos/api.ts` sustituir `CuerpoEditarCompra`, `CuerpoEditarOtro` y `CuerpoLoteCompras` por:

```ts
type CamposEditar = { idProv: number; cantidad: number; esUrgente: boolean; precioUnidad: number; divisa: string; updatedAt: string }
/** `destino` (0.9.9): solo cuando cambia el proveedor (`idBloque` null = bloque nuevo); null = regla por defecto del servidor. */
export type CuerpoEditarCompra = CamposEditar & { destino: { idBloque: number | null } | null }
export type CuerpoEditarOtro = CamposEditar & { concepto: string }
```

```ts
export type CuerpoLoteCompras = {
  lineas: { idCom: number; idProv: number; cantidad: number; esUrgente: boolean; precioUnidad: number }[]
  solicitudes: { urgentes: number[]; preventivas: number[] }
  /** 0.9.9 (spec «Pedidos por bloques» §5.2): bloque de cada proveedor (`idBloque` null = bloque nuevo); null = el
   *  servidor aplica su regla por defecto (abierto más reciente del proveedor o uno nuevo). */
  destinos: { idProv: number; idBloque: number | null }[] | null
}
```

Al final del fichero:

```ts
// ── 0.9.9: bloques de pedido (spec «Pedidos por bloques» §5.3) ─────────────────────────────────────────────

/** Una línea tal como la ve la tabla: el servidor la comprueba antes de aplicar una acción de bloque (todo o nada). */
export type LineaVista = { idCompra: number; updatedAt: string }
export function vistaDe(p: CompraComponente): LineaVista {
  return { idCompra: p.idCompra, updatedAt: p.updatedAt }
}
export type AccionBloque = 'confirmar' | 'recibir' | 'cancelar' | 'borrar'

/** «Mover a bloque ▸»: `idBloque` null = bloque nuevo. La página traduce el 409 y el 422; recarga siempre. */
export function useMoverLinea(): UseMutationResult<unknown, unknown, { pedido: CompraComponente; idBloque: number | null }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ pedido, idBloque }: { pedido: CompraComponente; idBloque: number | null }) =>
      api.PATCH('/api/compras/{idCompra}/bloque', { params: { path: { idCompra: pedido.idCompra } }, body: { idBloque, updatedAt: pedido.updatedAt } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

/** Una llamada por ruta literal para que openapi-fetch tipe cada una. */
async function accionBloque(idBloque: number, accion: AccionBloque, lineas: LineaVista[]) {
  const params = { path: { idBloque } }
  const body = { lineas }
  switch (accion) {
    case 'confirmar':
      return api.POST('/api/bloques-compra/{idBloque}/confirmar', { params, body })
    case 'recibir':
      return api.POST('/api/bloques-compra/{idBloque}/recibir', { params, body })
    case 'cancelar':
      return api.POST('/api/bloques-compra/{idBloque}/cancelar', { params, body })
    case 'borrar':
      return api.POST('/api/bloques-compra/{idBloque}/borrar', { params, body })
  }
}

export function useAccionBloque(): UseMutationResult<unknown, unknown, { idBloque: number; accion: AccionBloque; lineas: LineaVista[] }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ idBloque, accion, lineas }: { idBloque: number; accion: AccionBloque; lineas: LineaVista[] }) => accionBloque(idBloque, accion, lineas),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export function useSepararBloque(): UseMutationResult<{ idBloque: number } | undefined, unknown, { idBloque: number; lineas: LineaVista[] }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: async ({ idBloque, lineas }: { idBloque: number; lineas: LineaVista[] }) =>
      (await api.POST('/api/bloques-compra/{idBloque}/separar', { params: { path: { idBloque } }, body: { lineas } })).data,
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export function useJuntarBloque(): UseMutationResult<unknown, unknown, { idBloque: number; destino: number; lineas: LineaVista[] }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ idBloque, destino, lineas }: { idBloque: number; destino: number; lineas: LineaVista[] }) =>
      api.POST('/api/bloques-compra/{idBloque}/juntar', { params: { path: { idBloque } }, body: { destino, lineas } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}

export function useNotaBloque(): UseMutationResult<unknown, unknown, { idBloque: number; nota: string | null; updatedAt: string }> {
  const recargar = useRecargaPedidos()
  return useMutation({
    mutationFn: ({ idBloque, nota, updatedAt }: { idBloque: number; nota: string | null; updatedAt: string }) =>
      api.PATCH('/api/bloques-compra/{idBloque}/nota', { params: { path: { idBloque } }, body: { nota, updatedAt } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}
```

En `formulario/lineas.ts`, en el objeto que devuelve `cuerpoLoteCompras`, tras `solicitudes: …`, añadir `destinos: null,` (la Task 11 lo sustituye por los destinos elegidos). En `formulario/EditarPedidoDialog.tsx`, en el `cuerpo` del `mutateAsync`, añadir `destino: null` tras `updatedAt: pedido.updatedAt` (la Task 11 lo rellena cuando cambia el proveedor).

En `formulario/lineas.test.ts` y `formulario/NuevoPedidoDialog.test.tsx`, cada cuerpo esperado del lote (`{ lineas: …, solicitudes: … }`) gana `destinos: null`; en `formulario/EditarPedidoDialog.test.tsx`, cada cuerpo esperado del `PUT` gana `destino: null`.

- [ ] **Step 4: Tests de la API nueva**

En `pedidos/api.test.tsx`, ampliar el import de `./api` con `useAccionBloque, useJuntarBloque, useMoverLinea, useNotaBloque, useSepararBloque, vistaDe` y añadir al final:

```tsx
describe('bloques (0.9.9)', () => {
  it('useMoverLinea: PATCH /api/compras/{id}/bloque con el destino y el updatedAt de la línea', async () => {
    const peticiones = registrar('*/api/compras/:id/bloque')
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useMoverLinea(), { wrapper })
    await result.current.mutateAsync({ pedido: compra({ idCompra: 2 }), idBloque: null })
    expect(peticiones).toEqual([{ metodo: 'PATCH', ruta: '/api/compras/2/bloque', cuerpo: { idBloque: null, updatedAt: U }, clave: null }])
  })

  it.each([
    ['confirmar', '/api/bloques-compra/41/confirmar'],
    ['recibir', '/api/bloques-compra/41/recibir'],
    ['cancelar', '/api/bloques-compra/41/cancelar'],
    ['borrar', '/api/bloques-compra/41/borrar'],
  ] as const)('useAccionBloque %s: POST %s con las líneas vistas', async (accion, ruta) => {
    const peticiones = registrar('*/api/bloques-compra/:id/:accion')
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useAccionBloque(), { wrapper })
    await result.current.mutateAsync({ idBloque: 41, accion, lineas: [vistaDe(compra({ idCompra: 2 }))] })
    expect(peticiones).toEqual([{ metodo: 'POST', ruta, cuerpo: { lineas: [{ idCompra: 2, updatedAt: U }] }, clave: null }])
  })

  it('useSepararBloque devuelve el número del bloque nuevo', async () => {
    registrar('*/api/bloques-compra/:id/separar', () => HttpResponse.json({ idBloque: 90 }))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useSepararBloque(), { wrapper })
    expect(await result.current.mutateAsync({ idBloque: 41, lineas: [{ idCompra: 2, updatedAt: U }] })).toEqual({ idBloque: 90 })
  })

  it('useJuntarBloque y useNotaBloque mandan su cuerpo', async () => {
    const peticiones = registrar('*/api/bloques-compra/:id/:accion')
    const { wrapper } = envoltorio()
    const juntar = renderHook(() => useJuntarBloque(), { wrapper }).result
    const nota = renderHook(() => useNotaBloque(), { wrapper }).result
    await juntar.current.mutateAsync({ idBloque: 41, destino: 40, lineas: [{ idCompra: 2, updatedAt: U }] })
    await nota.current.mutateAsync({ idBloque: 41, nota: 'Ped. 123', updatedAt: U })
    expect(peticiones).toEqual([
      { metodo: 'POST', ruta: '/api/bloques-compra/41/juntar', cuerpo: { destino: 40, lineas: [{ idCompra: 2, updatedAt: U }] }, clave: null },
      { metodo: 'PATCH', ruta: '/api/bloques-compra/41/nota', cuerpo: { nota: 'Ped. 123', updatedAt: U }, clave: null },
    ])
  })

  it('un 409 de una acción de bloque llega como StaleDataError', async () => {
    registrar('*/api/bloques-compra/:id/:accion', () => HttpResponse.json({ message: 'Este bloque fue modificado por otro usuario.' }, { status: 409 }))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useAccionBloque(), { wrapper })
    await expect(result.current.mutateAsync({ idBloque: 41, accion: 'recibir', lineas: [] })).rejects.toBeInstanceOf(StaleDataError)
  })
})
```

- [ ] **Step 5: Ejecutar**

Run: `npx tsc -b`
Expected: sin errores.
Run: `npx vitest run src/modules/almacen/pedidos`
Expected: todo en verde.
Run: `npx vitest run && npm run lint`
Expected: todo en verde.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add -A api/openapi.json src
git commit -m "feat: contrato 0.9.9 con los bloques de pedido y su API"
```

---

### Task 7: Web — reglas de los bloques (funciones puras)

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/reglas.ts`
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/datosPrueba.ts`
- Test: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/reglas.test.ts`

**Interfaces:**
- Consumes: `totalFila`, `llevaAviso`, `chipDeEstado`, `ESTADOS_PEDIDO`, `EstadoPedido` (`../reglas`); `AccionBloque` (`../api`); `formatear` (`@/shared/lib/fechas`).
- Produces (todo exportado desde `bloques/reglas.ts`): `SIN_BLOQUE = 'sin-bloque'`; `claveBloque(p): string`; `type Bloque = { clave; idBloque: number | null; idProv; nombreProveedor; fecha: string | null; nota: string | null; updatedAt: string | null; antiguo: boolean; lineas: CompraComponente[] }`; `agruparEnBloques(todas): Map<string, Bloque>`; `compararBloques(a, b): number`; `ordenarPorBloques(visibles, bloques): CompraComponente[]`; `type Situacion = 'abierto' | 'en curso' | 'terminado'`; `situacion(b)`; `esVivo(b)`; `estaDesplegado(b, conFiltros: boolean, mapa: Record<string, boolean>): boolean`; `resumenEstados(b): { estado: EstadoPedido; n: number }[]`; `totalBloque(b)`; `avisoBloque(b)`; `lineasQueCambian(b, accion)`; `motivoNoCambia(p, accion)`; `type AccionMenuBloque = AccionBloque | 'anadir' | 'separar' | 'juntar' | 'nota'`; `type EntradaMenuBloque = { accion; texto; deshabilitada; motivo: string | null; peligro: boolean } | 'separador'`; `entradasMenuBloque(b, otrosDelProveedor: number)`; `bloquesDelProveedor(bloques, idProv, excluir?: string): Bloque[]`; `abiertosDe(bloques, idProv): Bloque[]`; `type DestinoMover = { idBloque: number; texto: string; detalle: string }`; `destinosMover(p, bloques): DestinoMover[]`; `estaSola(p, bloques): boolean`; `textoLineas(n)`; `fechaCorta(fecha)`; `destinoSugeridoEdicion(p, idProvNuevo, bloques): number | null`; `textoMoverYaPedida(p, idBloque: number | null): string`; `textoYaPedidasSeparar(n)`; `textoYaPedidasJuntar(idBloque, n)`. En `bloques/datosPrueba.ts`: `lineaDePrueba(o?: Partial<CompraComponente>): CompraComponente`.

- [ ] **Step 1: Datos de prueba y tests que fallan**

Crear `bloques/datosPrueba.ts`:

```ts
import type { CompraComponente } from '@/shared/api/client'

/** Datos SINTÉTICOS de los tests de bloques (0.9.9). Por defecto: línea 1, pendiente, del Proveedor A, en el bloque 41. */
export function lineaDePrueba(o: Partial<CompraComponente> = {}): CompraComponente {
  return {
    idCompra: 1, idCom: 11, tipoComponente: 'lcd-x-negro', idProv: 1, nombreProveedor: 'Proveedor A', cantidad: 2, cantidadRecibida: null,
    esUrgente: false, fechaPedido: '2026-10-09T10:00:00', fechaLlegada: null, precioUnidadPedido: 10, divisa: 'EUR', precioEur: 10,
    estado: 'pendiente', updatedAt: '2026-10-09T10:00:00',
    idBloque: 41, fechaBloque: '2026-10-09T10:00:00', notaBloque: null, bloqueUpdatedAt: '2026-10-09T10:00:00', bloqueAntiguo: false,
    ...o,
  }
}
```

Crear `bloques/reglas.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { lineaDePrueba as l } from './datosPrueba'
import {
  abiertosDe, agruparEnBloques, avisoBloque, claveBloque, destinoSugeridoEdicion, destinosMover, entradasMenuBloque, estaDesplegado,
  estaSola, lineasQueCambian, motivoNoCambia, ordenarPorBloques, resumenEstados, SIN_BLOQUE, situacion, textoLineas,
  textoMoverYaPedida, textoYaPedidasJuntar, textoYaPedidasSeparar, totalBloque,
} from './reglas'

const B41 = { idBloque: 41, fechaBloque: '2026-10-09T10:00:00' }
const B40 = { idBloque: 40, fechaBloque: '2026-10-08T10:00:00' }
const B39 = { idBloque: 39, fechaBloque: '2026-10-08T10:00:00' }

describe('agrupar y ordenar', () => {
  it('agrupa por bloque con los datos de la cabecera y las líneas por fecha de pedido e id', () => {
    const bloques = agruparEnBloques([
      l({ idCompra: 3, ...B41, fechaPedido: '2026-10-09T12:00:00', notaBloque: 'Ped. 1' }),
      l({ idCompra: 2, ...B41, fechaPedido: '2026-10-09T10:00:00', notaBloque: 'Ped. 1' }),
      l({ idCompra: 1, ...B41, fechaPedido: '2026-10-09T10:00:00', notaBloque: 'Ped. 1' }),
    ])
    const b = bloques.get('41')!
    expect(b).toMatchObject({ clave: '41', idBloque: 41, idProv: 1, nombreProveedor: 'Proveedor A', fecha: '2026-10-09T10:00:00', nota: 'Ped. 1', antiguo: false })
    expect(b.lineas.map((x) => x.idCompra)).toEqual([1, 2, 3])
  })

  it('una línea sin bloque va al grupo «Sin bloque»', () => {
    expect(claveBloque(l({ idBloque: null }))).toBe(SIN_BLOQUE)
  })

  it('ordena bloques por fecha y número descendentes, «Sin bloque» al final; dentro, líneas ascendentes', () => {
    const todas = [
      l({ idCompra: 1, ...B39 }), l({ idCompra: 2, ...B40 }), l({ idCompra: 3, idBloque: null, fechaBloque: null }),
      l({ idCompra: 4, ...B41, fechaPedido: '2026-10-09T12:00:00' }), l({ idCompra: 5, ...B41, fechaPedido: '2026-10-09T11:00:00' }),
    ]
    expect(ordenarPorBloques(todas, agruparEnBloques(todas)).map((x) => x.idCompra)).toEqual([5, 4, 2, 1, 3])
  })
})

describe('situación, resumen, total y aviso', () => {
  const bloque = (...lineas: ReturnType<typeof l>[]) => agruparEnBloques(lineas).get('41')!
  it('abierto, en curso y terminado', () => {
    expect(situacion(bloque(l({ idCompra: 1 }), l({ idCompra: 2 })))).toBe('abierto')
    expect(situacion(bloque(l({ idCompra: 1 }), l({ idCompra: 2, estado: 'recibido' })))).toBe('en curso')
    expect(situacion(bloque(l({ idCompra: 1, estado: 'recibido' }), l({ idCompra: 2, estado: 'cancelado' })))).toBe('terminado')
  })
  it('desplegado: lo manual manda; si no, con filtros siempre y sin filtros solo los vivos', () => {
    const terminado = bloque(l({ estado: 'recibido' }))
    expect(estaDesplegado(terminado, false, {})).toBe(false)
    expect(estaDesplegado(terminado, true, {})).toBe(true)
    expect(estaDesplegado(terminado, false, { 41: true })).toBe(true)
    expect(estaDesplegado(bloque(l()), false, { 41: false })).toBe(false)
  })
  it('resumen en el orden de los estados, solo los que hay', () => {
    const b = bloque(l({ idCompra: 1, estado: 'recibido' }), l({ idCompra: 2 }), l({ idCompra: 3, estado: 'en_camino' }), l({ idCompra: 4, estado: 'en_camino' }))
    expect(resumenEstados(b)).toEqual([{ estado: 'pendiente', n: 1 }, { estado: 'en_camino', n: 2 }, { estado: 'recibido', n: 1 }])
  })
  it('total sin las canceladas y ⚠ con una urgente en camino', () => {
    const b = bloque(l({ idCompra: 1, cantidad: 2, precioEur: 10 }), l({ idCompra: 2, cantidad: 5, precioEur: 1, estado: 'cancelado' }))
    expect(totalBloque(b)).toBe(20)
    expect(avisoBloque(b)).toBe(false)
    expect(avisoBloque(bloque(l({ esUrgente: true, estado: 'en_camino' })))).toBe(true)
  })
})

describe('acciones de bloque', () => {
  const b = agruparEnBloques([
    l({ idCompra: 1 }), l({ idCompra: 2, estado: 'en_camino' }), l({ idCompra: 3, estado: 'parcial', cantidad: 2, cantidadRecibida: 1 }),
    l({ idCompra: 4, estado: 'recibido' }), l({ idCompra: 5, estado: 'cancelado' }),
  ]).get('41')!
  it('qué líneas cambia cada acción', () => {
    expect(lineasQueCambian(b, 'confirmar').map((x) => x.idCompra)).toEqual([1])
    expect(lineasQueCambian(b, 'recibir').map((x) => x.idCompra)).toEqual([2])
    expect(lineasQueCambian(b, 'cancelar').map((x) => x.idCompra)).toEqual([2])
    expect(lineasQueCambian(b, 'borrar').map((x) => x.idCompra)).toEqual([1])
  })
  it('motivos de las que no cambian (spec §6.5)', () => {
    const [p, e, pa, r, c] = b.lineas
    expect([e, pa, r, c].map((x) => motivoNoCambia(x, 'confirmar'))).toEqual(['ya en camino', 'ya parcial', 'ya recibido', 'cancelado'])
    expect([p, pa, r, c].map((x) => motivoNoCambia(x, 'recibir'))).toEqual([
      'pendiente: aún no se ha pedido', 'parcial (1/2): usa «Recibir resto» en su fila', 'ya recibido', 'cancelado',
    ])
    expect([p, pa, r, c].map((x) => motivoNoCambia(x, 'cancelar'))).toEqual([
      'pendiente: se borra, no se cancela', 'parcial: usa «Cerrar sin resto» en su fila', 'ya recibido', 'ya cancelado',
    ])
  })
  it('menú del bloque con contadores, motivos y peligro', () => {
    const entradas = entradasMenuBloque(b, 0)
    expect(entradas.map((e) => (e === 'separador' ? '—' : e.texto))).toEqual([
      'Confirmar bloque (1)', 'Recibir todo (1)', '—', 'Añadir líneas…', 'Separar líneas…', 'Juntar con…', 'Añadir nota…', '—',
      'Cancelar bloque (1)', 'Borrar bloque',
    ])
    const porTexto = (t: string) => entradas.find((e) => e !== 'separador' && e.texto === t)
    expect(porTexto('Juntar con…')).toMatchObject({ deshabilitada: true, motivo: 'no hay otro bloque de este proveedor' })
    expect(porTexto('Borrar bloque')).toMatchObject({ deshabilitada: true, motivo: 'hay líneas ya pedidas', peligro: true })
    expect(porTexto('Separar líneas…')).toMatchObject({ deshabilitada: false, motivo: null })
  })
  it('menú de un bloque de una sola línea pendiente y con nota', () => {
    const solo = agruparEnBloques([l({ notaBloque: 'Ped. 1' })]).get('41')!
    const entradas = entradasMenuBloque(solo, 2).filter((e) => e !== 'separador')
    expect(entradas.find((e) => e.accion === 'recibir')).toMatchObject({ texto: 'Recibir todo (0)', deshabilitada: true, motivo: 'nada en camino' })
    expect(entradas.find((e) => e.accion === 'separar')).toMatchObject({ deshabilitada: true, motivo: 'solo tiene una línea' })
    expect(entradas.find((e) => e.accion === 'nota')).toMatchObject({ texto: 'Editar nota…' })
    expect(entradas.find((e) => e.accion === 'borrar')).toMatchObject({ deshabilitada: false })
  })
})

describe('mover y destinos', () => {
  const todas = [
    l({ idCompra: 1, ...B41 }), l({ idCompra: 2, ...B41, estado: 'en_camino' }),
    l({ idCompra: 3, ...B40 }), l({ idCompra: 4, ...B39, estado: 'recibido' }),
    l({ idCompra: 5, idBloque: 50, fechaBloque: '2026-10-09T09:00:00', idProv: 2, nombreProveedor: 'Proveedor B' }),
  ]
  const bloques = agruparEnBloques(todas)
  it('destinos: los del mismo proveedor sin el propio, del más reciente al más antiguo', () => {
    expect(destinosMover(todas[0], bloques)).toEqual([
      { idBloque: 40, texto: 'Bloque 40', detalle: '08/10 · 1 línea · abierto' },
      { idBloque: 39, texto: 'Bloque 39', detalle: '08/10 · 1 línea · terminado' },
    ])
  })
  it('como mucho 8 destinos', () => {
    const muchas = Array.from({ length: 11 }, (_, i) => l({ idCompra: i + 1, idBloque: 100 + i, fechaBloque: `2026-10-0${(i % 9) + 1}T10:00:00` }))
    expect(destinosMover(muchas[0], agruparEnBloques(muchas))).toHaveLength(8)
  })
  it('sola en su bloque, abiertos y destino sugerido al editar', () => {
    expect(estaSola(todas[2], bloques)).toBe(true)
    expect(estaSola(todas[0], bloques)).toBe(false)
    expect(abiertosDe(bloques, 1).map((b) => b.idBloque)).toEqual([40])
    expect(destinoSugeridoEdicion(todas[2], 2, bloques)).toBe(50)
    expect(destinoSugeridoEdicion(todas[1], 2, bloques)).toBeNull()
  })
})

describe('textos', () => {
  it('líneas, mover una ya pedida y avisos de separar y juntar', () => {
    expect(textoLineas(1)).toBe('1 línea')
    expect(textoLineas(3)).toBe('3 líneas')
    expect(textoMoverYaPedida(l({ idCompra: 7, estado: 'en_camino' }), 40)).toBe(
      'La línea lcd-x-negro (ID 7) está en camino. ¿Moverla al Bloque 40 igualmente?\nEl stock no cambia. El movimiento queda apuntado en el registro de actividad.',
    )
    expect(textoMoverYaPedida(l({ idCompra: 7, estado: 'recibido' }), null)).toContain('¿Moverla a un bloque nuevo igualmente?')
    expect(textoYaPedidasSeparar(1)).toBe('1 línea de las marcadas ya se pidió: el movimiento quedará apuntado en el registro.')
    expect(textoYaPedidasSeparar(2)).toBe('2 líneas de las marcadas ya se pidieron: el movimiento quedará apuntado en el registro.')
    expect(textoYaPedidasJuntar(41, 1)).toBe('El Bloque 41 tiene 1 línea ya pedida: el movimiento quedará apuntado en el registro.')
    expect(textoYaPedidasJuntar(41, 2)).toBe('El Bloque 41 tiene 2 líneas ya pedidas: el movimiento quedará apuntado en el registro.')
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/bloques`
Expected: FAIL (`./reglas` no existe).

- [ ] **Step 2: Implementación**

Crear `bloques/reglas.ts`:

```ts
import type { CompraComponente } from '@/shared/api/client'
import { formatear } from '@/shared/lib/fechas'
import type { AccionBloque } from '../api'
import { chipDeEstado, ESTADOS_PEDIDO, llevaAviso, totalFila, type EstadoPedido } from '../reglas'

/** Reglas de los bloques de pedido (0.9.9, spec «Pedidos por bloques» §3 y §6): funciones puras sobre las líneas de
 *  GET /api/compras, que traen los datos de su bloque. */

/** Clave de grupo de la tabla y de los mapas de plegado. Una línea sin bloque (solo en el hueco de un despliegue)
 *  va al grupo «Sin bloque», sin menú. */
export const SIN_BLOQUE = 'sin-bloque'
export function claveBloque(p: CompraComponente): string {
  return p.idBloque == null ? SIN_BLOQUE : String(p.idBloque)
}

/** Un bloque visto desde sus líneas: la cabecera y TODAS sus líneas (sin filtrar), en el orden de la regla 7. */
export type Bloque = {
  clave: string
  idBloque: number | null
  idProv: number
  nombreProveedor: string
  fecha: string | null
  nota: string | null
  updatedAt: string | null
  antiguo: boolean
  lineas: CompraComponente[]
}

function porFechaPedido(a: CompraComponente, b: CompraComponente): number {
  return a.fechaPedido.localeCompare(b.fechaPedido) || a.idCompra - b.idCompra
}

/** Agrupa TODAS las líneas por bloque (sin filtrar: el resumen y el total son siempre del bloque entero). */
export function agruparEnBloques(todas: CompraComponente[]): Map<string, Bloque> {
  const mapa = new Map<string, Bloque>()
  for (const p of todas) {
    const clave = claveBloque(p)
    let b = mapa.get(clave)
    if (b === undefined) {
      b = {
        clave, idBloque: p.idBloque ?? null, idProv: p.idProv, nombreProveedor: p.nombreProveedor, fecha: p.fechaBloque ?? null,
        nota: p.notaBloque ?? null, updatedAt: p.bloqueUpdatedAt ?? null, antiguo: p.bloqueAntiguo, lineas: [],
      }
      mapa.set(clave, b)
    }
    b.lineas.push(p)
  }
  for (const b of mapa.values()) b.lineas.sort(porFechaPedido)
  return mapa
}

/** Regla 7: bloques de más reciente a más antiguo (fecha y número descendentes); «Sin bloque», al final. */
export function compararBloques(a: Bloque, b: Bloque): number {
  if (a.idBloque === null) return b.idBloque === null ? 0 : 1
  if (b.idBloque === null) return -1
  return (b.fecha ?? '').localeCompare(a.fecha ?? '') || b.idBloque - a.idBloque
}

/** Las líneas visibles (ya filtradas) en el orden de la vista agrupada: cada bloque seguido, en el orden de la regla 7. */
export function ordenarPorBloques(visibles: CompraComponente[], bloques: Map<string, Bloque>): CompraComponente[] {
  const posicion = new Map([...bloques.values()].sort(compararBloques).map((b, i) => [b.clave, i]))
  return [...visibles].sort((a, b) => (posicion.get(claveBloque(a)) ?? 0) - (posicion.get(claveBloque(b)) ?? 0) || porFechaPedido(a, b))
}

export type Situacion = 'abierto' | 'en curso' | 'terminado'
const VIVOS = new Set(['pendiente', 'en_camino', 'parcial'])

/** Regla 3: abierto = todas pendientes; en curso = algo por hacer; terminado = todo recibido o cancelado. */
export function situacion(b: Bloque): Situacion {
  if (b.lineas.every((p) => p.estado === 'pendiente')) return 'abierto'
  return b.lineas.some((p) => VIVOS.has(p.estado)) ? 'en curso' : 'terminado'
}
export function esVivo(b: Bloque): boolean {
  return situacion(b) !== 'terminado'
}

/** Spec §6.2: lo que el usuario plegó o desplegó a mano manda; si no, con filtros todos desplegados y sin filtros solo
 *  los vivos. */
export function estaDesplegado(b: Bloque, conFiltros: boolean, mapa: Record<string, boolean>): boolean {
  return mapa[b.clave] ?? (conFiltros || esVivo(b))
}

/** Regla 4. */
export function resumenEstados(b: Bloque): { estado: EstadoPedido; n: number }[] {
  return ESTADOS_PEDIDO.map((estado) => ({ estado, n: b.lineas.filter((p) => p.estado === estado).length })).filter((r) => r.n > 0)
}

/** Regla 5: la columna EUR de las líneas no canceladas. */
export function totalBloque(b: Bloque): number {
  return b.lineas.filter((p) => p.estado !== 'cancelado').reduce((s, p) => s + totalFila(p), 0)
}

/** Regla 6. */
export function avisoBloque(b: Bloque): boolean {
  return b.lineas.some(llevaAviso)
}

const ESTADO_DE: Record<AccionBloque, EstadoPedido> = { confirmar: 'pendiente', recibir: 'en_camino', cancelar: 'en_camino', borrar: 'pendiente' }

/** Las líneas a las que se aplica cada acción (spec §6.5). */
export function lineasQueCambian(b: Bloque, accion: AccionBloque): CompraComponente[] {
  return b.lineas.filter((p) => p.estado === ESTADO_DE[accion])
}

/** Motivo de cada línea que no cambia (tabla de la spec §6.5). */
export function motivoNoCambia(p: CompraComponente, accion: AccionBloque): string {
  const e = p.estado
  if (accion === 'confirmar') return e === 'cancelado' ? 'cancelado' : `ya ${chipDeEstado(e as EstadoPedido)}`
  if (accion === 'recibir') {
    if (e === 'pendiente') return 'pendiente: aún no se ha pedido'
    if (e === 'parcial') return `parcial (${p.cantidadRecibida ?? 0}/${p.cantidad}): usa «Recibir resto» en su fila`
    return e === 'recibido' ? 'ya recibido' : 'cancelado'
  }
  if (accion === 'cancelar') {
    if (e === 'pendiente') return 'pendiente: se borra, no se cancela'
    if (e === 'parcial') return 'parcial: usa «Cerrar sin resto» en su fila'
    return e === 'recibido' ? 'ya recibido' : 'ya cancelado'
  }
  return chipDeEstado(e as EstadoPedido)
}

export type AccionMenuBloque = AccionBloque | 'anadir' | 'separar' | 'juntar' | 'nota'
export type EntradaMenuBloque =
  | { accion: AccionMenuBloque; texto: string; deshabilitada: boolean; motivo: string | null; peligro: boolean }
  | 'separador'

/** Menú del bloque (spec §6.4). `otrosDelProveedor`: cuántos bloques más tiene su proveedor. */
export function entradasMenuBloque(b: Bloque, otrosDelProveedor: number): EntradaMenuBloque[] {
  const pendientes = lineasQueCambian(b, 'confirmar').length
  const enCamino = lineasQueCambian(b, 'recibir').length
  const entrada = (accion: AccionMenuBloque, texto: string, deshabilitada: boolean, motivo: string | null, peligro = false): EntradaMenuBloque =>
    ({ accion, texto, deshabilitada, motivo: deshabilitada ? motivo : null, peligro })
  return [
    entrada('confirmar', `Confirmar bloque (${pendientes})`, pendientes === 0, 'nada pendiente'),
    entrada('recibir', `Recibir todo (${enCamino})`, enCamino === 0, 'nada en camino'),
    'separador',
    entrada('anadir', 'Añadir líneas…', false, null),
    entrada('separar', 'Separar líneas…', b.lineas.length < 2, 'solo tiene una línea'),
    entrada('juntar', 'Juntar con…', otrosDelProveedor === 0, 'no hay otro bloque de este proveedor'),
    entrada('nota', b.nota ? 'Editar nota…' : 'Añadir nota…', false, null),
    'separador',
    entrada('cancelar', `Cancelar bloque (${enCamino})`, enCamino === 0, 'nada en camino', true),
    entrada('borrar', 'Borrar bloque', pendientes !== b.lineas.length, 'hay líneas ya pedidas', true),
  ]
}

/** Bloques del proveedor (sin `excluir`), del más reciente al más antiguo. «Sin bloque» nunca cuenta. */
export function bloquesDelProveedor(bloques: Map<string, Bloque>, idProv: number, excluir?: string): Bloque[] {
  return [...bloques.values()].filter((b) => b.idBloque !== null && b.idProv === idProv && b.clave !== excluir).sort(compararBloques)
}

export function abiertosDe(bloques: Map<string, Bloque>, idProv: number): Bloque[] {
  return bloquesDelProveedor(bloques, idProv).filter((b) => situacion(b) === 'abierto')
}

export function textoLineas(n: number): string {
  return `${n} ${n === 1 ? 'línea' : 'líneas'}`
}

export function fechaCorta(fecha: string | null): string {
  return formatear(fecha, 'dd/MM')
}

export type DestinoMover = { idBloque: number; texto: string; detalle: string }

/** Submenú «Mover a bloque ▸» (spec §6.4): hasta 8 bloques del proveedor sin el propio. */
export function destinosMover(p: CompraComponente, bloques: Map<string, Bloque>): DestinoMover[] {
  return bloquesDelProveedor(bloques, p.idProv, claveBloque(p)).slice(0, 8).map((b) => ({
    idBloque: b.idBloque as number,
    texto: `Bloque ${b.idBloque}`,
    detalle: `${fechaCorta(b.fecha)} · ${textoLineas(b.lineas.length)} · ${situacion(b)}`,
  }))
}

/** «Bloque nuevo» no tiene sentido para una línea que ya está sola en su bloque. */
export function estaSola(p: CompraComponente, bloques: Map<string, Bloque>): boolean {
  return (bloques.get(claveBloque(p))?.lineas.length ?? 0) <= 1
}

/** Bloque sugerido al cambiar el proveedor en «Editar» (spec §6.6): pendiente → el abierto más reciente del proveedor
 *  nuevo; si no hay, o la línea ya se pidió, null (= bloque nuevo). */
export function destinoSugeridoEdicion(p: CompraComponente, idProvNuevo: number, bloques: Map<string, Bloque>): number | null {
  if (p.estado !== 'pendiente') return null
  return abiertosDe(bloques, idProvNuevo)[0]?.idBloque ?? null
}

/** Confirmación «Mover línea ya pedida» (spec §6.4). */
export function textoMoverYaPedida(p: CompraComponente, idBloque: number | null): string {
  const destino = idBloque === null ? 'a un bloque nuevo' : `al Bloque ${idBloque}`
  return `La línea ${p.tipoComponente} (ID ${p.idCompra}) está ${chipDeEstado(p.estado as EstadoPedido)}. ¿Moverla ${destino} igualmente?\n`
    + 'El stock no cambia. El movimiento queda apuntado en el registro de actividad.'
}

export function textoYaPedidasSeparar(n: number): string {
  return `${textoLineas(n)} de las marcadas ya ${n === 1 ? 'se pidió' : 'se pidieron'}: el movimiento quedará apuntado en el registro.`
}

export function textoYaPedidasJuntar(idBloque: number, n: number): string {
  return `El Bloque ${idBloque} tiene ${textoLineas(n)} ya ${n === 1 ? 'pedida' : 'pedidas'}: el movimiento quedará apuntado en el registro.`
}
```

- [ ] **Step 3: Ejecutar**

Run: `npx vitest run src/modules/almacen/pedidos/bloques && npx tsc -b && npm run lint`
Expected: PASS y sin errores. (Si `formatear(…, 'dd/MM')` convierte a la hora de Madrid, las fechas de prueba a las 10:00 siguen cayendo el mismo día.)

- [ ] **Step 4: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add src/modules/almacen/pedidos/bloques
git commit -m "feat: reglas puras de los bloques de pedido"
```

---

### Task 8: Web — agrupación opcional en la tabla compartida

**Files:**
- Modify: `gestion-reparaciones-web/src/shared/ui/DataTable.tsx`
- Test: `gestion-reparaciones-web/src/shared/ui/DataTable.grupos.test.tsx` (nuevo; `DataTable.test.tsx` no se toca)

**Interfaces:**
- Produces: `export type GruposTabla<T> = { clave: (fila: T) => string; desplegado: (clave: string) => boolean; cabecera: (clave: string, filas: T[]) => ReactNode; menuCabecera?: (clave: string, filas: T[]) => ReactNode | null }` y la prop opcional `grupos?: GruposTabla<T>` de `DataTable`. Las filas cabecera llevan `data-cabecera` y una sola celda con `colSpan` = columnas pintadas.

- [ ] **Step 1: Tests que fallan**

Crear `src/shared/ui/DataTable.grupos.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { ContextMenuItem } from './context-menu'
import { DataTable, type GruposTabla } from './DataTable'

type Fila = { id: string; grupo: string; nombre: string }
const DATOS: Fila[] = [
  { id: '1', grupo: 'A', nombre: 'a1' },
  { id: '2', grupo: 'A', nombre: 'a2' },
  { id: '3', grupo: 'B', nombre: 'b1' },
  { id: '4', grupo: 'C', nombre: 'c1' },
]
const COLUMNAS: ColumnDef<Fila, string>[] = [{ accessorKey: 'nombre', header: 'Nombre', size: 200 }]

function grupos(plegados: string[] = [], conMenu = false): GruposTabla<Fila> {
  return {
    clave: (f) => f.grupo,
    desplegado: (c) => !plegados.includes(c),
    cabecera: (c, filas) => <span>Grupo {c} ({filas.length})</span>,
    menuCabecera: conMenu ? (c) => <ContextMenuItem>Acción de {c}</ContextMenuItem> : undefined,
  }
}

function Tabla({ g, onSeleccionar = vi.fn(), umbralVirtual }: { g?: GruposTabla<Fila>; onSeleccionar?: (id: string | null) => void; umbralVirtual?: number }) {
  const [sel, setSel] = useState<string | null>(null)
  return (
    <DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id} grupos={g} umbralVirtual={umbralVirtual}
      seleccionada={sel} onSeleccionar={(id) => { setSel(id); onSeleccionar(id) }} />
  )
}
const textos = () => screen.getAllByRole('row').slice(1).map((r) => r.textContent)

describe('DataTable con grupos (0.9.9)', () => {
  it('sin grupos pinta las filas como siempre', () => {
    render(<Tabla />)
    expect(textos()).toEqual(['a1', 'a2', 'b1', 'c1'])
  })

  it('intercala una cabecera a todo el ancho antes de cada grupo', () => {
    render(<Tabla g={grupos()} />)
    expect(textos()).toEqual(['Grupo A (2)', 'a1', 'a2', 'Grupo B (1)', 'b1', 'Grupo C (1)', 'c1'])
    const celda = screen.getByText('Grupo A (2)').closest('td') as HTMLElement
    expect(celda).toHaveAttribute('colspan', '2') // la columna y la de relleno del ajuste fijo
    expect(celda.closest('tr')).toHaveAttribute('data-cabecera')
  })

  it('un grupo plegado pinta solo su cabecera (con todas sus filas en `filas`)', () => {
    render(<Tabla g={grupos(['A'])} />)
    expect(textos()).toEqual(['Grupo A (2)', 'Grupo B (1)', 'b1', 'Grupo C (1)', 'c1'])
  })

  it('las flechas saltan las cabeceras y las filas plegadas', () => {
    const onSeleccionar = vi.fn()
    render(<Tabla g={grupos(['B'])} onSeleccionar={onSeleccionar} />)
    fireEvent.click(screen.getByText('a2'))
    const contenedor = screen.getByRole('table').parentElement as HTMLElement
    fireEvent.keyDown(contenedor, { key: 'ArrowDown' })
    expect(onSeleccionar).toHaveBeenLastCalledWith('4')
    fireEvent.keyDown(contenedor, { key: 'ArrowUp' })
    expect(onSeleccionar).toHaveBeenLastCalledWith('2')
  })

  it('un clic en una cabecera no selecciona ninguna fila', () => {
    const onSeleccionar = vi.fn()
    render(<Tabla g={grupos()} onSeleccionar={onSeleccionar} />)
    fireEvent.click(screen.getByText('Grupo B (1)'))
    expect(onSeleccionar).not.toHaveBeenCalled()
  })

  it('la cabecera abre su menú contextual', async () => {
    render(<Tabla g={grupos([], true)} />)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('Grupo B (1)') })
    expect(screen.getByRole('menuitem', { name: 'Acción de B' })).toBeInTheDocument()
  })

  it('con pintado parcial también intercala las cabeceras', () => {
    render(<Tabla g={grupos()} umbralVirtual={2} />)
    expect(textos()).toEqual(['Grupo A (2)', 'a1', 'a2', 'Grupo B (1)', 'b1', 'Grupo C (1)', 'c1'])
  })
})
```

Run: `npx vitest run src/shared/ui/DataTable.grupos.test.tsx`
Expected: FAIL (`GruposTabla` no existe; con `grupos` ignorado no hay cabeceras).

- [ ] **Step 2: Implementación en `DataTable.tsx`**

1. En el import de React añadir `useMemo`.
2. Tras el tipo `CeldaPulsada`:

```ts
/** Agrupación opcional (0.9.9, Pedidos por bloques). `data` llega ordenada: las filas de cada grupo, seguidas. */
export type GruposTabla<T> = {
  clave: (fila: T) => string
  desplegado: (clave: string) => boolean
  /** Contenido de la fila cabecera (una celda a todo el ancho); `filas` = las de ese grupo que hay en `data`. */
  cabecera: (clave: string, filas: T[]) => ReactNode
  /** Menú contextual de la cabecera; null = sin menú. */
  menuCabecera?: (clave: string, filas: T[]) => ReactNode | null
}

/** Lo que pinta la tabla: filas de datos y, con `grupos`, cabeceras. */
type ElementoTabla<T> = { tipo: 'fila'; row: Row<T> } | { tipo: 'cabecera'; clave: string; filas: T[] }

/** Sin `grupos`, exactamente las filas (mismos índices que antes). Con `grupos`, una cabecera por grupo y, si está
 *  desplegado, sus filas. */
function construirElementos<T>(filas: Row<T>[], grupos: GruposTabla<T> | undefined): ElementoTabla<T>[] {
  if (!grupos) return filas.map((row) => ({ tipo: 'fila' as const, row }))
  const elementos: ElementoTabla<T>[] = []
  let i = 0
  while (i < filas.length) {
    const clave = grupos.clave(filas[i].original)
    const delGrupo: Row<T>[] = []
    while (i < filas.length && grupos.clave(filas[i].original) === clave) {
      delGrupo.push(filas[i])
      i += 1
    }
    elementos.push({ tipo: 'cabecera', clave, filas: delGrupo.map((r) => r.original) })
    if (grupos.desplegado(clave)) for (const row of delGrupo) elementos.push({ tipo: 'fila', row })
  }
  return elementos
}

function idElemento<T>(e: ElementoTabla<T>): string {
  return e.tipo === 'fila' ? e.row.id : `cabecera:${e.clave}`
}
```

3. En `Props<T>`, tras `altoFila?: number`:

```ts
  /** 0.9.9 (Pedidos por bloques): cabeceras de grupo intercaladas y plegables. Sin ella la tabla no cambia. */
  grupos?: GruposTabla<T>
```

y en la desestructuración de `DataTable`, tras `altoFila,`, añadir `grupos,`.

4. Justo después de `const filas = table.getRowModel().rows`:

```ts
  // 0.9.9: lo que se pinta (cabeceras y filas de los grupos desplegados) y las filas que recorren las flechas. Sin
  // `grupos`, `elementos` son las mismas filas con los mismos índices: el resto de la tabla no cambia.
  const elementos = useMemo(() => construirElementos(filas, grupos), [filas, grupos])
  const navegables = useMemo(() => elementos.flatMap((e) => (e.tipo === 'fila' ? [e.row] : [])), [elementos])
```

5. Sustituciones:
   - en el efecto de `desbordeFilas`: `querySelector('tbody tr[data-index]')` → `querySelector('tbody tr[data-index]:not([data-cabecera])')`;
   - `const virtual = filas.length > umbralVirtual` → `const virtual = elementos.length > umbralVirtual`;
   - en `useVirtualizer`, `count: filas.length,` → `count: elementos.length,`;
   - en `desplazarA`, `const fila = filaRefs.current.get(filas[indice].id)` → `const fila = filaRefs.current.get(idElemento(elementos[indice]))`;
   - en el efecto de desplazamiento por selección: `const idx = filas.findIndex((r) => r.id === seleccionada)` → `const idx = elementos.findIndex((e) => e.tipo === 'fila' && e.row.id === seleccionada)`, `filaRefs.current.get(filas[arriba].id)` → `filaRefs.current.get(idElemento(elementos[arriba]))` y, en su lista de dependencias, `filas` → `elementos`.

6. Sustituir el cuerpo de `onKeyDown` por:

```ts
    // Ni las teclas que ya ha gestionado otro (un ítem de menú, una casilla) ni las de un portal (el menú contextual).
    if (!onSeleccionar || navegables.length === 0 || e.defaultPrevented || !nacioDentro(e)) return
    const idx = seleccionada === null ? -1 : navegables.findIndex((r) => r.id === seleccionada)
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const nuevo = e.key === 'ArrowDown' ? Math.min(idx + 1, navegables.length - 1) : Math.max(idx - 1, 0)
      const id = navegables[nuevo].id
      seleccionarDesdeTabla(id)
      desplazarA(elementos.findIndex((el) => el.tipo === 'fila' && el.row.id === id))
    } else if (e.key === 'Enter' && e.target === e.currentTarget && idx >= 0 && onAbrir) {
      // Solo con el foco en la propia tabla: con el foco en un botón de una celda, Enter es de ese botón.
      e.preventDefault()
      onAbrir(navegables[idx].original)
    }
```

7. Tras la función `pintarFila`, añadir:

```tsx
  /** Fila cabecera de un grupo: una celda a todo el ancho con lo que pinte la página. No selecciona nada al pulsarla. */
  function pintarCabecera(e: Extract<ElementoTabla<T>, { tipo: 'cabecera' }>, indice: number) {
    const id = idElemento(e)
    const tr = (
      <TableRow
        key={id}
        data-index={indice}
        data-cabecera=""
        ref={(el: HTMLTableRowElement | null) => {
          if (el) {
            filaRefs.current.set(id, el)
            if (virtual) virtualizador.measureElement(el)
          } else {
            filaRefs.current.delete(id)
          }
        }}
        className="border-b border-fila-sep bg-fila-maestro-bg hover:bg-fila-maestro-bg"
      >
        <TableCell colSpan={columnasPintadas} className="p-0">{grupos?.cabecera(e.clave, e.filas)}</TableCell>
      </TableRow>
    )
    const contenido = grupos?.menuCabecera?.(e.clave, e.filas) ?? null
    if (contenido === null) return tr
    return (
      <ContextMenu key={id}>
        <ContextMenuTrigger asChild>{tr}</ContextMenuTrigger>
        <ContextMenuContent>{contenido}</ContextMenuContent>
      </ContextMenu>
    )
  }

  function pintarElemento(e: ElementoTabla<T>, indice: number) {
    return e.tipo === 'fila' ? pintarFila(e.row, indice) : pintarCabecera(e, indice)
  }
```

8. En el `<TableBody>`: `{!virtual && filas.map((r, i) => pintarFila(r, i))}` → `{!virtual && elementos.map((e, i) => pintarElemento(e, i))}` y `{items.map((vi) => pintarFila(filas[vi.index], vi.index))}` → `{items.map((vi) => pintarElemento(elementos[vi.index], vi.index))}`. (`items` sigue siendo la lista del virtualizador; no confundir con `elementos`.)

- [ ] **Step 3: Ejecutar**

Run: `npx vitest run src/shared/ui/DataTable.grupos.test.tsx src/shared/ui/DataTable.test.tsx`
Expected: PASS (los tests de siempre sin tocar).
Run: `npx vitest run && npx tsc -b && npm run lint`
Expected: todo en verde.

- [ ] **Step 4: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add src/shared/ui/DataTable.tsx src/shared/ui/DataTable.grupos.test.tsx
git commit -m "feat: agrupacion opcional con cabeceras plegables en la tabla compartida"
```

---

### Task 9: Web — cabecera del bloque y menús

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/BadgeEstadoPedido.tsx` (`clasesEstado`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/MenuPedido.tsx` (`mover`)
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/CabeceraBloque.tsx`
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/MenuBloque.tsx`
- Test: `bloques/CabeceraBloque.test.tsx`, `bloques/MenuBloque.test.tsx` (nuevos), `pedidos/MenuPedido.test.tsx` (ampliado)

**Interfaces:**
- Consumes: `Bloque`, `resumenEstados`, `totalBloque`, `avisoBloque`, `textoLineas`, `SIN_BLOQUE`, `EntradaMenuBloque`, `AccionMenuBloque`, `DestinoMover` (Task 7).
- Produces: `clasesEstado(estado: string): string`; `<CabeceraBloque bloque visibles desplegado onAlternar menu? />` (botón de la flecha con `aria-label` «Plegar bloque n» / «Desplegar bloque n»); `<MenuBloqueContextual entradas onAccion onInteraccion? />` (ítems de `ContextMenu`) y `<BotonMenuBloque idBloque entradas onAccion onInteraccion? />` (botón «Acciones del bloque n» con `DropdownMenu`); `MenuPedido` con prop opcional `mover?: { destinos: DestinoMover[]; sola: boolean; onMover: (idBloque: number | null) => void }`.

- [ ] **Step 1: Tests que fallan**

Crear `bloques/CabeceraBloque.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { formatearImporte } from '@/shared/lib/importes'
import { CabeceraBloque } from './CabeceraBloque'
import { lineaDePrueba as l } from './datosPrueba'
import { agruparEnBloques } from './reglas'

const NOTA = { notaBloque: 'Ped. 123' }
const bloque = (...lineas: ReturnType<typeof l>[]) => [...agruparEnBloques(lineas).values()][0]

describe('CabeceraBloque (spec §6.3)', () => {
  it('número, proveedor, fecha, líneas, total, ⚠, chips del resumen y nota', () => {
    const b = bloque(l({ idCompra: 1, ...NOTA }), l({ idCompra: 2, estado: 'en_camino', esUrgente: true, ...NOTA }), l({ idCompra: 3, estado: 'en_camino', ...NOTA }))
    render(<CabeceraBloque bloque={b} visibles={3} desplegado onAlternar={vi.fn()} />)
    const titulo = screen.getByText('Bloque 41').closest('button') as HTMLElement
    expect(titulo).toHaveTextContent('Bloque 41·Proveedor A·09/10/2026·3 líneas·')
    expect(screen.getByText(formatearImporte(60, '€'))).toBeInTheDocument()
    expect(screen.getByText('⚠')).toBeInTheDocument()
    expect(screen.getByText('1 pendiente')).toBeInTheDocument()
    expect(screen.getByText('2 en camino')).toBeInTheDocument()
    expect(screen.getByTitle('Ped. 123')).toHaveTextContent('📝 Ped. 123')
    expect(screen.queryByText(/mostrando/)).not.toBeInTheDocument()
    expect(screen.queryByText('antiguo')).not.toBeInTheDocument()
  })

  it('«mostrando k de n» con filtros y etiqueta «antiguo» en un bloque reconstruido', () => {
    const b = bloque(l({ idCompra: 1, bloqueAntiguo: true }), l({ idCompra: 2, bloqueAntiguo: true }))
    render(<CabeceraBloque bloque={b} visibles={1} desplegado onAlternar={vi.fn()} />)
    expect(screen.getByText('mostrando 1 de 2')).toBeInTheDocument()
    expect(screen.getByText('antiguo')).toHaveAttribute('title', 'Reconstruido al migrar: mismo proveedor y mismo día')
  })

  it('la flecha y el título pliegan; el menú va a la derecha', async () => {
    const onAlternar = vi.fn()
    render(<CabeceraBloque bloque={bloque(l())} visibles={1} desplegado={false} onAlternar={onAlternar} menu={<button type="button">⋮</button>} />)
    const flecha = screen.getByRole('button', { name: 'Desplegar bloque 41' })
    expect(flecha).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(flecha)
    await userEvent.click(screen.getByText('Bloque 41'))
    expect(onAlternar).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('button', { name: '⋮' })).toBeInTheDocument()
  })

  it('las líneas sin bloque se agrupan bajo «Sin bloque»', () => {
    render(<CabeceraBloque bloque={bloque(l({ idBloque: null, fechaBloque: null }))} visibles={1} desplegado onAlternar={vi.fn()} />)
    expect(screen.getByText('Sin bloque')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Plegar líneas sin bloque' })).toBeInTheDocument()
  })
})
```

Crear `bloques/MenuBloque.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { lineaDePrueba as l } from './datosPrueba'
import { BotonMenuBloque } from './MenuBloque'
import { agruparEnBloques, entradasMenuBloque } from './reglas'

describe('BotonMenuBloque (spec §6.4)', () => {
  it('⋮ abre el menú; las deshabilitadas llevan su motivo; elegir una avisa con su acción', async () => {
    const onAccion = vi.fn()
    const onInteraccion = vi.fn()
    const b = agruparEnBloques([l({ idCompra: 1 }), l({ idCompra: 2, estado: 'en_camino' })]).get('41')!
    render(<BotonMenuBloque idBloque={41} entradas={entradasMenuBloque(b, 0)} onAccion={onAccion} onInteraccion={onInteraccion} />)
    await userEvent.click(screen.getByRole('button', { name: 'Acciones del bloque 41' }))
    expect(onInteraccion).toHaveBeenLastCalledWith(true)
    const juntar = screen.getByRole('menuitem', { name: /Juntar con…/ })
    expect(juntar).toHaveAttribute('aria-disabled', 'true')
    expect(juntar).toHaveTextContent('no hay otro bloque de este proveedor')
    expect(screen.getByRole('menuitem', { name: /Borrar bloque/ })).toHaveClass('text-rojo-accion')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recibir todo (1)' }))
    expect(onAccion).toHaveBeenCalledWith('recibir')
  })
})
```

En `pedidos/MenuPedido.test.tsx`, al final del `describe`:

```tsx
  it('0.9.9: «Mover a bloque» abre los destinos y elegir uno avisa con su número', async () => {
    const onMover = vi.fn()
    render(<DataTable columns={COLUMNAS} data={[pedido()]} vacio="" getRowId={(x) => String(idPedido(x))}
      menuFila={(x) => <MenuPedido pedido={x} onAccion={vi.fn()} mover={{ destinos: [{ idBloque: 40, texto: 'Bloque 40', detalle: '08/10 · 1 línea · abierto' }], sola: false, onMover }} />} />)
    await abrir()
    await userEvent.click(screen.getByRole('menuitem', { name: 'Mover a bloque' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: /Bloque 40/ }))
    expect(onMover).toHaveBeenCalledWith(40)
  })

  it('0.9.9: un cancelado con «mover» tiene solo esa entrada; si está sola, «Bloque nuevo» va deshabilitado', async () => {
    render(<DataTable columns={COLUMNAS} data={[pedido({ estado: 'cancelado' })]} vacio="" getRowId={(x) => String(idPedido(x))}
      menuFila={(x) => <MenuPedido pedido={x} onAccion={vi.fn()} mover={{ destinos: [], sola: true, onMover: vi.fn() }} />} />)
    await abrir()
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Mover a bloque'])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Mover a bloque' }))
    const nuevo = await screen.findByRole('menuitem', { name: /Bloque nuevo/ })
    expect(nuevo).toHaveAttribute('aria-disabled', 'true')
    expect(nuevo).toHaveTextContent('ya está sola en su bloque')
  })
```

(Si el clic no abre el submenú en jsdom, abrirlo con el teclado: foco en «Mover a bloque» y `await userEvent.keyboard('{ArrowRight}')`.)

Run: `npx vitest run src/modules/almacen/pedidos/bloques src/modules/almacen/pedidos/MenuPedido.test.tsx`
Expected: FAIL (componentes y prop `mover` no existen).

- [ ] **Step 2: `clasesEstado` y `MenuPedido`**

En `BadgeEstadoPedido.tsx`, tras `clasesBadge`:

```ts
/** Chip de un estado en la cabecera de un bloque (0.9.9): los colores del badge, con «en camino» siempre neutro (el
 *  aviso de urgente va aparte, en el ⚠ de la cabecera). */
export function clasesEstado(estado: string): string {
  return estado === 'en_camino' ? NEUTRO : CLASES[estado] ?? NEUTRO
}
```

Sustituir `MenuPedido.tsx` entero por:

```tsx
import { useEffect } from 'react'
import { ContextMenuItem, ContextMenuSeparator, ContextMenuSub, ContextMenuSubContent, ContextMenuSubTrigger } from '@/shared/ui/context-menu'
import type { DestinoMover } from './bloques/reglas'
import { entradasMenu, type AccionMenu, type Pedido } from './reglas'

type Props = {
  pedido: Pedido
  onAccion: (accion: AccionMenu, pedido: Pedido) => void
  /** Aviso de menú abierto/cerrado para congelar el sondeo (D4 del 3a), como MenuComponente. Estable entre renders. */
  onInteraccion?: (abierto: boolean) => void
  /** 0.9.9: «Mover a bloque ▸» (solo componentes). `sola`: la línea es la única de su bloque (sin «Bloque nuevo»). */
  mover?: { destinos: DestinoMover[]; sola: boolean; onMover: (idBloque: number | null) => void }
}

/** Contenido del menú contextual de una fila de Pedidos (solo SUPERTECNICO; la página no pasa menuFila a ADMIN ni TECNICO).
 *  Entradas y separadores por estado de `entradasMenu`; cancelado no tiene ninguna salvo «Mover a bloque» (0.9.9). */
export function MenuPedido({ pedido, onAccion, onInteraccion, mover }: Props) {
  // Radix monta el contenido solo mientras el menú está abierto: montar/desmontar = abrir/cerrar.
  useEffect(() => {
    if (!onInteraccion) return
    onInteraccion(true)
    return () => onInteraccion(false)
  }, [onInteraccion])
  const entradas = entradasMenu(pedido.estado)
  if (entradas.length === 0 && !mover) return null
  return (
    <>
      {entradas.map((e, i) =>
        e === 'separador' ? (
          <ContextMenuSeparator key={`sep-${i}`} />
        ) : (
          <ContextMenuItem key={e.accion} onSelect={() => onAccion(e.accion, pedido)}>{e.texto}</ContextMenuItem>
        ),
      )}
      {mover && (
        <>
          {entradas.length > 0 && <ContextMenuSeparator />}
          <ContextMenuSub>
            <ContextMenuSubTrigger>Mover a bloque</ContextMenuSubTrigger>
            <ContextMenuSubContent className="min-w-[280px]">
              {mover.destinos.map((d) => (
                <ContextMenuItem key={d.idBloque} onSelect={() => mover.onMover(d.idBloque)} className="flex w-full gap-4">
                  <span>{d.texto}</span>
                  <span className="ml-auto text-[11px] text-texto-sub">{d.detalle}</span>
                </ContextMenuItem>
              ))}
              {mover.destinos.length > 0 && <ContextMenuSeparator />}
              <ContextMenuItem disabled={mover.sola} onSelect={() => mover.onMover(null)} className="flex w-full gap-4">
                <span>Bloque nuevo</span>
                {mover.sola && <span className="ml-auto text-[11px] text-texto-sub">ya está sola en su bloque</span>}
              </ContextMenuItem>
            </ContextMenuSubContent>
          </ContextMenuSub>
        </>
      )}
    </>
  )
}
```

- [ ] **Step 3: `CabeceraBloque` y `MenuBloque`**

Crear `bloques/CabeceraBloque.tsx`:

```tsx
import type { ReactNode } from 'react'
import { formatear } from '@/shared/lib/fechas'
import { formatearImporte } from '@/shared/lib/importes'
import { cn } from '@/shared/lib/utils'
import { clasesEstado } from '../BadgeEstadoPedido'
import { chipDeEstado } from '../reglas'
import { avisoBloque, resumenEstados, SIN_BLOQUE, textoLineas, totalBloque, type Bloque } from './reglas'

type Props = {
  bloque: Bloque
  /** Líneas del bloque que se ven con los filtros de ahora (para «mostrando k de n»). */
  visibles: number
  desplegado: boolean
  onAlternar: () => void
  /** Botón ⋮ (solo SUPERTECNICO). */
  menu?: ReactNode
}

function Punto() {
  return <span aria-hidden className="mx-1.5 text-texto-sub">·</span>
}

/** Cabecera de un bloque en la tabla de Pedidos (spec §6.3 y prototipo): una línea de 40 px. La flecha y el título
 *  pliegan y despliegan; resumen, total y ⚠ son del bloque entero aunque haya filtros. */
export function CabeceraBloque({ bloque, visibles, desplegado, onAlternar, menu }: Props) {
  const total = bloque.lineas.length
  const sinBloque = bloque.clave === SIN_BLOQUE
  return (
    <div className="flex h-10 items-center gap-2 overflow-hidden whitespace-nowrap pr-1.5 pl-1 text-[12px] text-azul-medio">
      <button type="button" onClick={onAlternar} aria-expanded={desplegado}
        aria-label={`${desplegado ? 'Plegar' : 'Desplegar'} ${sinBloque ? 'líneas sin bloque' : `bloque ${bloque.idBloque}`}`}
        className="w-5 shrink-0 cursor-pointer text-[11px]">
        {desplegado ? '▼' : '▶'}
      </button>
      <button type="button" onClick={onAlternar} tabIndex={-1} className="shrink-0 cursor-pointer text-left">
        {sinBloque ? (
          <b>Sin bloque</b>
        ) : (
          <>
            <b>Bloque {bloque.idBloque}</b>
            <Punto />{bloque.nombreProveedor}
            <Punto />{formatear(bloque.fecha, 'dd/MM/yyyy')}
            <Punto />{textoLineas(total)}
            <Punto /><b>{formatearImporte(totalBloque(bloque), '€')}</b>
          </>
        )}
      </button>
      {avisoBloque(bloque) && <span title="Hay líneas urgentes en camino o parciales" className="shrink-0 text-[13px] text-fila-solicitud-brd">⚠</span>}
      {bloque.antiguo && (
        <span title="Reconstruido al migrar: mismo proveedor y mismo día"
          className="shrink-0 rounded-lg border border-dashed border-texto-sub px-1.5 text-[10.5px] text-texto-sub">antiguo</span>
      )}
      <span className="flex shrink-0 gap-1">
        {resumenEstados(bloque).map(({ estado, n }) => (
          <span key={estado} className={cn('rounded-[12px] px-2 py-0.5 text-[11px] font-bold', clasesEstado(estado))}>{n} {chipDeEstado(estado)}</span>
        ))}
      </span>
      {visibles < total && (
        <span className="shrink-0 rounded-[10px] bg-texto-accion px-2 py-0.5 text-[11px] text-superficie">mostrando {visibles} de {total}</span>
      )}
      {bloque.nota && <span title={bloque.nota} className="min-w-0 truncate text-azul-gris italic">📝 {bloque.nota}</span>}
      <span className="flex-1" />
      {menu}
    </div>
  )
}
```

Crear `bloques/MenuBloque.tsx`:

```tsx
import { useEffect } from 'react'
import { cn } from '@/shared/lib/utils'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import type { AccionMenuBloque, EntradaMenuBloque } from './reglas'

type Props = {
  entradas: EntradaMenuBloque[]
  onAccion: (accion: AccionMenuBloque) => void
  /** Aviso de menú abierto/cerrado para congelar el sondeo, como MenuPedido. */
  onInteraccion?: (abierto: boolean) => void
}
type Entrada = Exclude<EntradaMenuBloque, 'separador'>

function Texto({ entrada }: { entrada: Entrada }) {
  return (
    <>
      <span>{entrada.texto}</span>
      {entrada.motivo !== null && <span className="ml-auto pl-4 text-[11px] text-texto-sub">{entrada.motivo}</span>}
    </>
  )
}

const claseEntrada = (e: Entrada) => cn('flex w-full gap-2', e.peligro && 'text-rojo-accion')

/** Menú del bloque con clic derecho en su cabecera (spec §6.4). Radix monta el contenido solo mientras el menú está
 *  abierto: montar/desmontar = abrir/cerrar. */
export function MenuBloqueContextual({ entradas, onAccion, onInteraccion }: Props) {
  useEffect(() => {
    if (!onInteraccion) return
    onInteraccion(true)
    return () => onInteraccion(false)
  }, [onInteraccion])
  return (
    <>
      {entradas.map((e, i) =>
        e === 'separador' ? (
          <ContextMenuSeparator key={`sep-${i}`} />
        ) : (
          <ContextMenuItem key={e.accion} disabled={e.deshabilitada} onSelect={() => onAccion(e.accion)} className={claseEntrada(e)}>
            <Texto entrada={e} />
          </ContextMenuItem>
        ),
      )}
    </>
  )
}

/** Botón ⋮ de la cabecera con el mismo menú (spec §6.3). */
export function BotonMenuBloque({ idBloque, entradas, onAccion, onInteraccion }: Props & { idBloque: number }) {
  return (
    <DropdownMenu onOpenChange={onInteraccion}>
      <DropdownMenuTrigger asChild>
        <button type="button" aria-label={`Acciones del bloque ${idBloque}`}
          className="h-[30px] w-[30px] shrink-0 cursor-pointer rounded-full text-[18px] leading-none hover:bg-fondo-vista">⋮</button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-[230px]">
        {entradas.map((e, i) =>
          e === 'separador' ? (
            <DropdownMenuSeparator key={`sep-${i}`} />
          ) : (
            <DropdownMenuItem key={e.accion} disabled={e.deshabilitada} onSelect={() => onAccion(e.accion)} className={claseEntrada(e)}>
              <Texto entrada={e} />
            </DropdownMenuItem>
          ),
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
```

- [ ] **Step 4: Ejecutar**

Run: `npx vitest run src/modules/almacen/pedidos && npx tsc -b && npm run lint`
Expected: todo en verde.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add src/modules/almacen/pedidos
git commit -m "feat: cabecera de bloque, menu del bloque y mover a bloque en el menu de la linea"
```

---

### Task 10: Web — diálogos de bloque

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/ui/DialogoAlmacen.tsx` (`peligro`)
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/fallos.ts`
- Create: `bloques/ListaLineas.tsx`, `bloques/AccionBloqueDialog.tsx`, `bloques/SepararDialog.tsx`, `bloques/JuntarDialog.tsx`, `bloques/NotaBloqueDialog.tsx`
- Test: `bloques/dialogos.test.tsx` (nuevo)

**Interfaces:**
- Consumes: `useAccionBloque`, `useSepararBloque`, `useJuntarBloque`, `useNotaBloque`, `vistaDe`, `AccionBloque` (Task 6); reglas (Task 7); `mensajeErrorGuardado` (`formulario/errores`); `useAlerta().mostrarAviso`.
- Produces: `MSG_BLOQUE_MODIFICADO` y `tratarFalloBloque(e, cerrar, avisar, setError)` en `bloques/fallos.ts`; `<AccionBloqueDialog bloque accion onCerrar />`, `<SepararDialog bloque onCerrar />`, `<JuntarDialog bloque candidatos onCerrar />`, `<NotaBloqueDialog bloque onCerrar />` (la página los monta solo mientras están abiertos); `DialogoAlmacen` con prop `peligro?: boolean` (botón de acción en rojo).

- [ ] **Step 1: Tests que fallan**

Crear `bloques/dialogos.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { AccionBloqueDialog } from './AccionBloqueDialog'
import { lineaDePrueba as l } from './datosPrueba'
import { JuntarDialog } from './JuntarDialog'
import { NotaBloqueDialog } from './NotaBloqueDialog'
import { agruparEnBloques } from './reglas'
import { SepararDialog } from './SepararDialog'

const U = '2026-10-09T10:00:00'
let peticiones: { ruta: string; cuerpo: unknown }[]
let respuesta: () => Response

beforeEach(() => {
  peticiones = []
  respuesta = () => new HttpResponse(null, { status: 200 })
  server.use(http.all('*/api/bloques-compra/:id/:accion', async ({ request }) => {
    peticiones.push({ ruta: new URL(request.url).pathname, cuerpo: await request.json() })
    return respuesta()
  }))
})

/** Bloque 41: pendiente (1), en camino (2, bat-x) y parcial 1/2 (3). */
const mezclado = () => agruparEnBloques([
  l({ idCompra: 1 }),
  l({ idCompra: 2, estado: 'en_camino', tipoComponente: 'bat-x' }),
  l({ idCompra: 3, estado: 'parcial', cantidad: 2, cantidadRecibida: 1 }),
]).get('41')!

describe('AccionBloqueDialog (spec §6.5)', () => {
  it('«Recibir todo»: lista lo que cambia y lo que no, y manda solo las en camino', async () => {
    const onCerrar = vi.fn()
    renderConProviders(<AccionBloqueDialog bloque={mezclado()} accion="recibir" onCerrar={onCerrar} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Recibir todo · Bloque 41' })
    expect(dlg).toHaveTextContent('Proveedor A · 09/10/2026')
    expect(within(dlg).getByRole('region', { name: 'Se reciben (suman al stock) (1)' })).toHaveTextContent('bat-x')
    const quedan = within(dlg).getByRole('region', { name: 'No cambian (2)' })
    expect(quedan).toHaveTextContent('pendiente: aún no se ha pedido')
    expect(quedan).toHaveTextContent('parcial (1/2): usa «Recibir resto» en su fila')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Recibir (1)' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(peticiones).toEqual([{ ruta: '/api/bloques-compra/41/recibir', cuerpo: { lineas: [{ idCompra: 2, updatedAt: U }] } }])
  })

  it('un 409 cierra y avisa como «Advertencia»', async () => {
    respuesta = () => HttpResponse.json({ message: 'Este bloque fue modificado por otro usuario.' }, { status: 409 })
    const onCerrar = vi.fn()
    renderConProviders(<AccionBloqueDialog bloque={mezclado()} accion="confirmar" onCerrar={onCerrar} />, { sesion: SESION_SUPER })
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar (1)' }))
    expect(await screen.findByText('Este bloque fue modificado por otro usuario. Los datos se han recargado.')).toBeInTheDocument()
    expect(onCerrar).toHaveBeenCalled()
  })

  it('«Cancelar bloque» y «Borrar bloque» llevan el botón en rojo', async () => {
    renderConProviders(<AccionBloqueDialog bloque={mezclado()} accion="cancelar" onCerrar={vi.fn()} />, { sesion: SESION_SUPER })
    expect(await screen.findByRole('button', { name: 'Cancelar líneas (1)' })).toHaveClass('bg-rojo-accion')
  })
})

describe('SepararDialog', () => {
  it('sin marcar no deja; con todas avisa; con ya pedidas cambia el botón; manda las marcadas', async () => {
    const onCerrar = vi.fn()
    renderConProviders(<SepararDialog bloque={mezclado()} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Separar líneas · Bloque 41' })
    expect(dlg).toHaveTextContent('Las marcadas pasan a un bloque nuevo de Proveedor A con fecha de hoy.')
    expect(within(dlg).getByRole('button', { name: 'Separar' })).toBeDisabled()
    for (const n of [1, 2, 3]) await userEvent.click(within(dlg).getByRole('checkbox', { name: new RegExp(`\\(ID ${n}\\)`) }))
    expect(within(dlg).getByText('Deja al menos una línea en el Bloque 41.')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Separar igualmente (3)' })).toBeDisabled()
    await userEvent.click(within(dlg).getByRole('checkbox', { name: /\(ID 1\)/ }))
    expect(within(dlg).getByText('2 líneas de las marcadas ya se pidieron: el movimiento quedará apuntado en el registro.')).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Separar igualmente (2)' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(peticiones).toEqual([{ ruta: '/api/bloques-compra/41/separar', cuerpo: { lineas: [{ idCompra: 2, updatedAt: U }, { idCompra: 3, updatedAt: U }] } }])
  })
})

describe('JuntarDialog', () => {
  it('elegir destino, avisar de las ya pedidas y mandar el bloque entero', async () => {
    const onCerrar = vi.fn()
    const bloques = agruparEnBloques([
      l({ idCompra: 1, estado: 'en_camino', notaBloque: 'Ped. 2' }),
      l({ idCompra: 2, idBloque: 40, fechaBloque: '2026-10-08T10:00:00' }),
    ])
    renderConProviders(<JuntarDialog bloque={bloques.get('41')!} candidatos={[bloques.get('40')!]} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Juntar Bloque 41 con…' })
    expect(dlg).toHaveTextContent('Sus 1 línea pasan al bloque que elijas y el Bloque 41 desaparece. Su nota se añade a la del destino.')
    expect(within(dlg).getByText('El Bloque 41 tiene 1 línea ya pedida: el movimiento quedará apuntado en el registro.')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Juntar' })).toBeDisabled()
    await userEvent.click(within(dlg).getByRole('radio', { name: /Bloque 40/ }))
    await userEvent.click(within(dlg).getByRole('button', { name: 'Juntar' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(peticiones).toEqual([{ ruta: '/api/bloques-compra/41/juntar', cuerpo: { destino: 40, lineas: [{ idCompra: 1, updatedAt: U }] } }])
  })
})

describe('NotaBloqueDialog', () => {
  it('cuenta los caracteres, recorta y manda null si queda vacía', async () => {
    const onCerrar = vi.fn()
    const b = agruparEnBloques([l({ notaBloque: 'Ped. 1' })]).get('41')!
    renderConProviders(<NotaBloqueDialog bloque={b} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Nota · Bloque 41' })
    const campo = within(dlg).getByRole('textbox', { name: 'Nota del bloque' })
    expect(campo).toHaveValue('Ped. 1')
    expect(dlg).toHaveTextContent('6/200')
    await userEvent.clear(campo)
    await userEvent.type(campo, '  Ped. 9  ')
    expect(dlg).toHaveTextContent('10/200')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    await userEvent.clear(campo)
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(peticiones).toHaveLength(2))
    expect(peticiones.map((p) => p.cuerpo)).toEqual([{ nota: 'Ped. 9', updatedAt: U }, { nota: null, updatedAt: U }])
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/bloques/dialogos.test.tsx`
Expected: FAIL (los diálogos no existen).

- [ ] **Step 2: `DialogoAlmacen` con `peligro` y `fallos.ts`**

En `modules/almacen/ui/DialogoAlmacen.tsx`: en `Props`, tras `accionDeshabilitada?: boolean`,

```ts
  /** 0.9.9: acción destructiva (cancelar o borrar un bloque): botón en rojo. */
  peligro?: boolean
```

en la desestructuración `peligro = false,` y el botón de acción pasa a
`<BotonPrimario type="submit" disabled={enviando || accionDeshabilitada} className={peligro ? 'bg-rojo-accion hover:bg-rojo-accion/90' : undefined}>{textoAccion}</BotonPrimario>`.

Crear `bloques/fallos.ts`:

```ts
import { StaleDataError } from '@/shared/api/errors'
import { mensajeErrorGuardado } from '../formulario/errores'

export const MSG_BLOQUE_MODIFICADO = 'Este bloque fue modificado por otro usuario. Los datos se han recargado.'

/** Fallo de un diálogo de bloque (spec §6.5): un 409 (alguien cambió el bloque; no se aplicó nada) cierra y avisa como
 *  «Advertencia»; un 422 u otro error se queda en la línea de error del diálogo; lo que gestiona el mecanismo global
 *  (sin conexión, sesión) no se repite. */
export function tratarFalloBloque(e: unknown, cerrar: () => void, avisar: (titulo: string, msg: string) => void,
                                  setError: (msg: string | null) => void): void {
  if (e instanceof StaleDataError) {
    cerrar()
    avisar('Advertencia', MSG_BLOQUE_MODIFICADO)
    return
  }
  setError(mensajeErrorGuardado(e))
}
```

- [ ] **Step 3: Diálogos**

Crear `bloques/ListaLineas.tsx`:

```tsx
import type { CompraComponente } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { BadgeEstadoPedido } from '../BadgeEstadoPedido'

type Props = { titulo: string; lineas: CompraComponente[]; motivo?: (p: CompraComponente) => string; apagada?: boolean }

/** Lista de líneas de los diálogos de bloque (prototipo): ID, componente, cantidad y su estado o, en «No cambian», el
 *  motivo. */
export function ListaLineas({ titulo, lineas, motivo, apagada = false }: Props) {
  return (
    <section aria-label={titulo} className="rounded-lg border border-fila-sep bg-superficie">
      <h4 className={cn('rounded-t-lg border-b border-fila-sep bg-crema px-2.5 py-1.5 text-[12px] font-bold', apagada ? 'text-texto-sub' : 'text-azul-medio')}>{titulo}</h4>
      <ul>
        {lineas.map((p) => (
          <li key={p.idCompra} className="flex items-center gap-2.5 border-b border-crema px-2.5 py-1.5 text-[12px] text-azul-medio last:border-b-0">
            <span className="text-azul-gris">{p.idCompra}</span>
            <b>{p.tipoComponente}</b>
            <span>× {p.cantidad}</span>
            <span className="ml-auto">{motivo ? <span className="text-[11px] text-texto-sub">{motivo(p)}</span> : <BadgeEstadoPedido pedido={p} />}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
```

Crear `bloques/AccionBloqueDialog.tsx`:

```tsx
import { useState } from 'react'
import { formatear } from '@/shared/lib/fechas'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useAccionBloque, vistaDe, type AccionBloque } from '../api'
import { tratarFalloBloque } from './fallos'
import { ListaLineas } from './ListaLineas'
import { lineasQueCambian, motivoNoCambia, type Bloque } from './reglas'

const TEXTOS: Record<AccionBloque, { titulo: string; cambian: string; boton: (n: number) => string; peligro: boolean }> = {
  confirmar: { titulo: 'Confirmar bloque', cambian: 'Pasan a en camino', boton: (n) => `Confirmar (${n})`, peligro: false },
  recibir: { titulo: 'Recibir todo', cambian: 'Se reciben (suman al stock)', boton: (n) => `Recibir (${n})`, peligro: false },
  cancelar: { titulo: 'Cancelar bloque', cambian: 'Se cancelan (no se pueden reactivar)', boton: (n) => `Cancelar líneas (${n})`, peligro: true },
  borrar: { titulo: 'Borrar bloque', cambian: 'Se borran el bloque y todas sus líneas', boton: () => 'Borrar bloque', peligro: true },
}

type Props = { bloque: Bloque; accion: AccionBloque; onCerrar: () => void }

/** Confirmación de una acción de bloque (spec §6.5): qué cambia y qué no, con su motivo. Manda la lista exacta de líneas
 *  que cambian; el servidor la comprueba entera (todo o nada). */
export function AccionBloqueDialog({ bloque, accion, onCerrar }: Props) {
  const aplicar = useAccionBloque()
  const { mostrarAviso } = useAlerta()
  const [error, setError] = useState<string | null>(null)
  const t = TEXTOS[accion]
  const cambian = lineasQueCambian(bloque, accion)
  const quedan = bloque.lineas.filter((p) => !cambian.includes(p))

  async function confirmar() {
    setError(null)
    try {
      await aplicar.mutateAsync({ idBloque: bloque.idBloque as number, accion, lineas: cambian.map(vistaDe) })
      onCerrar()
    } catch (e) {
      tratarFalloBloque(e, onCerrar, mostrarAviso, setError)
    }
  }

  return (
    <DialogoAlmacen abierto ancho={520} titulo={`${t.titulo} · Bloque ${bloque.idBloque}`}
      subtitulo={`${bloque.nombreProveedor} · ${formatear(bloque.fecha, 'dd/MM/yyyy')}`} error={error}
      textoAccion={t.boton(cambian.length)} peligro={t.peligro} enviando={aplicar.isPending}
      onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      <div className="flex max-h-[50vh] flex-col gap-2.5 overflow-y-auto">
        <ListaLineas titulo={`${t.cambian} (${cambian.length})`} lineas={cambian} />
        {quedan.length > 0 && (
          <ListaLineas titulo={`No cambian (${quedan.length})`} lineas={quedan} motivo={(p) => motivoNoCambia(p, accion)} apagada />
        )}
      </div>
    </DialogoAlmacen>
  )
}
```

Crear `bloques/SepararDialog.tsx`:

```tsx
import { useState } from 'react'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Checkbox } from '@/shared/ui/checkbox'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useSepararBloque, vistaDe } from '../api'
import { BadgeEstadoPedido } from '../BadgeEstadoPedido'
import { tratarFalloBloque } from './fallos'
import { textoYaPedidasSeparar, type Bloque } from './reglas'

type Props = { bloque: Bloque; onCerrar: () => void }

/** «Separar líneas…» (spec §6.5): las marcadas pasan a un bloque nuevo del mismo proveedor; al menos una se queda. Las
 *  ya pedidas no piden otra confirmación: el aviso va dentro y el botón dice «Separar igualmente». */
export function SepararDialog({ bloque, onCerrar }: Props) {
  const separar = useSepararBloque()
  const { mostrarAviso } = useAlerta()
  const [marcadas, setMarcadas] = useState<Set<number>>(() => new Set())
  const [error, setError] = useState<string | null>(null)
  const elegidas = bloque.lineas.filter((p) => marcadas.has(p.idCompra))
  const todas = elegidas.length === bloque.lineas.length
  const yaPedidas = elegidas.filter((p) => p.estado !== 'pendiente').length
  const deshabilitado = elegidas.length === 0 || todas

  function alternar(idCompra: number, marcada: boolean) {
    setMarcadas((m) => {
      const n = new Set(m)
      if (marcada) n.add(idCompra)
      else n.delete(idCompra)
      return n
    })
    setError(null)
  }

  async function confirmar() {
    if (deshabilitado) return
    try {
      await separar.mutateAsync({ idBloque: bloque.idBloque as number, lineas: elegidas.map(vistaDe) })
      onCerrar()
    } catch (e) {
      tratarFalloBloque(e, onCerrar, mostrarAviso, setError)
    }
  }

  const textoAccion = `${yaPedidas > 0 ? 'Separar igualmente' : 'Separar'}${elegidas.length > 0 ? ` (${elegidas.length})` : ''}`
  return (
    <DialogoAlmacen abierto ancho={520} titulo={`Separar líneas · Bloque ${bloque.idBloque}`}
      subtitulo={`Las marcadas pasan a un bloque nuevo de ${bloque.nombreProveedor} con fecha de hoy.`} error={error}
      textoAccion={textoAccion} accionDeshabilitada={deshabilitado} enviando={separar.isPending}
      onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      <ul className="max-h-[50vh] overflow-y-auto rounded-lg border border-fila-sep bg-superficie">
        {bloque.lineas.map((p) => (
          <li key={p.idCompra} className="border-b border-crema last:border-b-0">
            <label className="flex cursor-pointer items-center gap-2.5 px-2.5 py-1.5 text-[12px] text-azul-medio">
              <Checkbox checked={marcadas.has(p.idCompra)} onCheckedChange={(v) => alternar(p.idCompra, v === true)}
                aria-label={`Separar ${p.tipoComponente} (ID ${p.idCompra})`} className="bg-superficie" />
              <span className="text-azul-gris">{p.idCompra}</span>
              <b>{p.tipoComponente}</b>
              <span>× {p.cantidad}</span>
              <span className="ml-auto"><BadgeEstadoPedido pedido={p} /></span>
            </label>
          </li>
        ))}
      </ul>
      {todas && <p role="status" className="text-[12px] text-azul-gris">Deja al menos una línea en el Bloque {bloque.idBloque}.</p>}
      {yaPedidas > 0 && (
        <p role="status" className="rounded-lg bg-aviso-conflicto-bg px-2.5 py-2 text-[12px] text-aviso-conflicto-text">{textoYaPedidasSeparar(yaPedidas)}</p>
      )}
    </DialogoAlmacen>
  )
}
```

Crear `bloques/JuntarDialog.tsx`:

```tsx
import { useState } from 'react'
import { formatear } from '@/shared/lib/fechas'
import { formatearImporte } from '@/shared/lib/importes'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useJuntarBloque, vistaDe } from '../api'
import { tratarFalloBloque } from './fallos'
import { situacion, textoLineas, textoYaPedidasJuntar, totalBloque, type Bloque } from './reglas'

type Props = { bloque: Bloque; candidatos: Bloque[]; onCerrar: () => void }

/** «Juntar con…» (spec §6.5): todas las líneas pasan al bloque elegido (del mismo proveedor) y este desaparece. */
export function JuntarDialog({ bloque, candidatos, onCerrar }: Props) {
  const juntar = useJuntarBloque()
  const { mostrarAviso } = useAlerta()
  const [destino, setDestino] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const yaPedidas = bloque.lineas.filter((p) => p.estado !== 'pendiente').length

  async function confirmar() {
    if (destino === null) return
    try {
      await juntar.mutateAsync({ idBloque: bloque.idBloque as number, destino, lineas: bloque.lineas.map(vistaDe) })
      onCerrar()
    } catch (e) {
      tratarFalloBloque(e, onCerrar, mostrarAviso, setError)
    }
  }

  const subtitulo = `Sus ${textoLineas(bloque.lineas.length)} pasan al bloque que elijas y el Bloque ${bloque.idBloque} desaparece.`
    + (bloque.nota ? ' Su nota se añade a la del destino.' : '')
  return (
    <DialogoAlmacen abierto ancho={520} titulo={`Juntar Bloque ${bloque.idBloque} con…`} subtitulo={subtitulo} error={error}
      textoAccion="Juntar" accionDeshabilitada={destino === null} enviando={juntar.isPending}
      onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      <div role="radiogroup" aria-label="Bloque destino" className="max-h-[50vh] overflow-y-auto rounded-lg border border-fila-sep bg-superficie">
        {candidatos.map((b) => (
          <label key={b.clave} className="flex cursor-pointer items-center gap-2.5 border-b border-crema px-2.5 py-2 text-[12px] text-azul-medio last:border-b-0">
            <input type="radio" name="juntar-destino" checked={destino === b.idBloque} onChange={() => { setDestino(b.idBloque); setError(null) }} />
            <b>Bloque {b.idBloque}</b>
            <span className="text-azul-gris">
              {formatear(b.fecha, 'dd/MM/yyyy')} · {textoLineas(b.lineas.length)} · {formatearImporte(totalBloque(b), '€')} · {situacion(b)}
            </span>
          </label>
        ))}
      </div>
      {yaPedidas > 0 && (
        <p role="status" className="rounded-lg bg-aviso-conflicto-bg px-2.5 py-2 text-[12px] text-aviso-conflicto-text">
          {textoYaPedidasJuntar(bloque.idBloque as number, yaPedidas)}
        </p>
      )}
    </DialogoAlmacen>
  )
}
```

Crear `bloques/NotaBloqueDialog.tsx`:

```tsx
import { useState } from 'react'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useNotaBloque } from '../api'
import { tratarFalloBloque } from './fallos'
import type { Bloque } from './reglas'

const MAX_NOTA = 200 // VARCHAR(200) de Bloque_compra.NOTA

type Props = { bloque: Bloque; onCerrar: () => void }

/** «Añadir nota…» / «Editar nota…» (spec §6.5): recortada; vacía = sin nota. */
export function NotaBloqueDialog({ bloque, onCerrar }: Props) {
  const guardar = useNotaBloque()
  const { mostrarAviso } = useAlerta()
  const [texto, setTexto] = useState(bloque.nota ?? '')
  const [error, setError] = useState<string | null>(null)

  async function confirmar() {
    const limpia = texto.trim()
    try {
      await guardar.mutateAsync({ idBloque: bloque.idBloque as number, nota: limpia === '' ? null : limpia, updatedAt: bloque.updatedAt as string })
      onCerrar()
    } catch (e) {
      tratarFalloBloque(e, onCerrar, mostrarAviso, setError)
    }
  }

  return (
    <DialogoAlmacen abierto ancho={520} titulo={`Nota · Bloque ${bloque.idBloque}`}
      subtitulo="Nº de pedido o factura del proveedor, seguimiento, forma de pago… Sale en la cabecera y en el CSV."
      error={error} textoAccion="Guardar" enviando={guardar.isPending} onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      <textarea aria-label="Nota del bloque" value={texto} maxLength={MAX_NOTA} rows={3}
        onChange={(e) => { setTexto(e.target.value); setError(null) }}
        className="w-full resize-none rounded border border-fila-sep bg-superficie p-2 text-[13px] text-azul-medio" />
      <p className="text-right text-[11px] text-texto-sub">{texto.length}/{MAX_NOTA}</p>
    </DialogoAlmacen>
  )
}
```

- [ ] **Step 4: Ejecutar**

Run: `npx vitest run src/modules/almacen && npx tsc -b && npm run lint`
Expected: todo en verde (los diálogos de Stock que usan `DialogoAlmacen` no cambian: `peligro` vale `false` por defecto).

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add src/modules/almacen
git commit -m "feat: dialogos de bloque (acciones, separar, juntar y nota)"
```

---

### Task 11: Web — la pestaña Pedidos agrupada por bloques

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/preferencias.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/estado.ts` (`plegadoBloques`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/filtros.ts` (`hayFiltros`, `firmaFiltros`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/columnas.tsx` (columna «Bloque», `vista`, CSV)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/PedidosPage.tsx` (entero)
- Modify: `gestion-reparaciones-web/tests/e2e/pedidos.spec.ts`
- Test: `pedidos/PedidosPage.bloques.test.tsx` (nuevo), `pedidos/PedidosPage.test.tsx`, `pedidos/columnas.test.tsx`, `pedidos/filtros.test.ts`

**Interfaces:**
- Consumes: todo lo anterior de la web (Tasks 6-10).
- Produces: `leerAgrupar(): boolean`, `guardarAgrupar(agrupar: boolean)` (clave `pedidos.agruparPorBloque`, valores `si`/`no`); `plegadoBloques: Store<PlegadoBloques>` con `type PlegadoBloques = { normal: Record<string, boolean>; filtrado: { firma: string; mapa: Record<string, boolean> } }`; `hayFiltros(f): boolean`, `firmaFiltros(f): string`; `crearColumnasPedidos({ onComponente, vista?: 'agrupada' | 'plana' })` (por defecto `plana`); `ANCHOS_PEDIDOS.bloque = 64`; CSV con `Bloque` y `Nota del bloque` tras `ID`.

- [ ] **Step 1: Tests de la página que fallan**

Crear `pedidos/PedidosPage.bloques.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { CompraComponente, Proveedor } from '@/shared/api/client'
import type { Sesion } from '@/shared/session/storage'
import { renderConRouter, SESION_ADMIN, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { lineaDePrueba as l } from './bloques/datosPrueba'
import { PedidosPage } from './PedidosPage'

const U = '2026-10-09T10:00:00'
const B41 = { idBloque: 41, fechaBloque: '2026-10-09T10:00:00' }
const B40 = { idBloque: 40, fechaBloque: '2026-10-08T10:00:00' }
const B39 = { idBloque: 39, fechaBloque: '2026-10-07T10:00:00' }
/** 41 en curso (pendiente + en camino), 40 abierto, 39 terminado. */
const compras: CompraComponente[] = [
  l({ idCompra: 1, ...B41, tipoComponente: 'lcd-x-negro' }),
  l({ idCompra: 2, ...B41, tipoComponente: 'bat-x', estado: 'en_camino' }),
  l({ idCompra: 3, ...B40, tipoComponente: 'mc-x' }),
  l({ idCompra: 4, ...B39, tipoComponente: 'lcd-y', estado: 'recibido', cantidadRecibida: 2 }),
]
const proveedores: Proveedor[] = [{ idProv: 1, nombre: 'Proveedor A', activo: true, divisa: 'EUR', comentario: '', tipo: 'COMPONENTES' }]
let escrituras: string[]

beforeEach(() => {
  escrituras = []
  localStorage.removeItem('pedidos.agruparPorBloque')
  server.use(
    http.get('*/api/compras', () => HttpResponse.json(compras)),
    http.get('*/api/proveedores', () => HttpResponse.json(proveedores)),
    http.patch('*/api/compras/:id/bloque', async ({ params, request }) => {
      escrituras.push(`PATCH ${String(params.id)} ${JSON.stringify(await request.json())}`)
      return new HttpResponse(null, { status: 200 })
    }),
    http.post('*/api/bloques-compra/:id/:accion', async ({ params, request }) => {
      escrituras.push(`POST ${String(params.id)} ${String(params.accion)} ${JSON.stringify(await request.json())}`)
      return new HttpResponse(null, { status: 200 })
    }),
  )
})
afterEach(() => localStorage.removeItem('pedidos.agruparPorBloque'))

function montar(sesion: Sesion = SESION_SUPER) {
  return renderConRouter([{ path: '/stock/pedidos', element: <PedidosPage tipo="componentes" /> }], { sesion, ruta: '/stock/pedidos' })
}
const filas = () => screen.getAllByRole('row').slice(1).map((r) => r.textContent ?? '')
const abrirMenu = (texto: string) => userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText(texto).closest('tr') as HTMLElement })

describe('Pedidos agrupados por bloques (0.9.9)', () => {
  it('por defecto agrupa: del más reciente al más antiguo, vivos desplegados y terminados plegados, sin columna Proveedor', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    const t = filas()
    expect(t).toHaveLength(6)
    expect(t[0]).toContain('Bloque 41')
    expect(t[1]).toContain('lcd-x-negro')
    expect(t[2]).toContain('bat-x')
    expect(t[3]).toContain('Bloque 40')
    expect(t[4]).toContain('mc-x')
    expect(t[5]).toContain('Bloque 39')
    expect(screen.queryByRole('columnheader', { name: 'Proveedor' })).not.toBeInTheDocument()
  })

  it('el interruptor vuelve a la vista plana con la columna Bloque y el navegador lo recuerda', async () => {
    const { unmount } = montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.click(screen.getByRole('switch', { name: 'Agrupar por bloque' }))
    expect(screen.getByRole('columnheader', { name: 'Bloque' })).toBeInTheDocument()
    expect(screen.getByText('lcd-y')).toBeInTheDocument()
    expect(localStorage.getItem('pedidos.agruparPorBloque')).toBe('no')
    unmount()
    montar()
    await screen.findByText('lcd-x-negro')
    expect(screen.getByRole('switch', { name: 'Agrupar por bloque' })).toHaveAttribute('aria-checked', 'false')
  })

  it('plegar un bloque, «Plegar todo» y «Desplegar todo»', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.click(screen.getByRole('button', { name: 'Plegar bloque 41' }))
    expect(screen.queryByText('lcd-x-negro')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Plegar todo' }))
    expect(screen.queryByText('mc-x')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Desplegar todo' }))
    expect(screen.getByText('lcd-y')).toBeInTheDocument()
  })

  it('con el buscador: solo las líneas que cumplen, desplegadas y con «mostrando k de n»', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente…'), 'lcd')
    expect(screen.getByText('mostrando 1 de 2')).toBeInTheDocument()
    expect(screen.getByText('lcd-y')).toBeInTheDocument()
    expect(screen.queryByText('bat-x')).not.toBeInTheDocument()
    expect(screen.queryByText('mc-x')).not.toBeInTheDocument()
  })

  it('mover una pendiente va sin preguntar; una en camino pide confirmación', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await abrirMenu('lcd-x-negro')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Mover a bloque' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: /Bloque 40/ }))
    await waitFor(() => expect(escrituras).toEqual([`PATCH 1 {"idBloque":40,"updatedAt":"${U}"}`]))
    await abrirMenu('bat-x')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Mover a bloque' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: /Bloque nuevo/ }))
    const dlg = await screen.findByRole('dialog', { name: 'Mover línea ya pedida' })
    expect(dlg).toHaveTextContent('La línea bat-x (ID 2) está en camino. ¿Moverla a un bloque nuevo igualmente?')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Mover' }))
    await waitFor(() => expect(escrituras[1]).toBe(`PATCH 2 {"idBloque":null,"updatedAt":"${U}"}`))
  })

  it('⋮ del bloque → «Recibir todo» confirma y manda las en camino', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.click(screen.getByRole('button', { name: 'Acciones del bloque 41' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recibir todo (1)' }))
    const dlg = await screen.findByRole('dialog', { name: 'Recibir todo · Bloque 41' })
    await userEvent.click(within(dlg).getByRole('button', { name: 'Recibir (1)' }))
    await waitFor(() => expect(escrituras).toEqual([`POST 41 recibir {"lineas":[{"idCompra":2,"updatedAt":"${U}"}]}`]))
  })

  it('el administrador ve los bloques sin ⋮', async () => {
    montar(SESION_ADMIN)
    await screen.findByText('lcd-x-negro')
    expect(screen.getByText('Bloque 41')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Acciones del bloque/ })).not.toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/PedidosPage.bloques.test.tsx`
Expected: FAIL (sin vista agrupada).

- [ ] **Step 2: Preferencia, plegado y filtros**

Crear `pedidos/preferencias.ts`:

```ts
/** «Agrupar por bloque» (0.9.9, spec §6.2): preferencia de este navegador. Si no se puede leer (modo privado, almacenamiento
 *  bloqueado), agrupada; si no se puede guardar, dura lo que la página. */
const CLAVE_AGRUPAR = 'pedidos.agruparPorBloque'

export function leerAgrupar(): boolean {
  try {
    return localStorage.getItem(CLAVE_AGRUPAR) !== 'no'
  } catch {
    return true
  }
}

export function guardarAgrupar(agrupar: boolean): void {
  try {
    localStorage.setItem(CLAVE_AGRUPAR, agrupar ? 'si' : 'no')
  } catch {
    // sin almacenamiento: la elección dura lo que la página
  }
}
```

En `pedidos/estado.ts`, al final:

```ts
/** 0.9.9 (spec §6.2): bloques plegados o desplegados a mano durante la sesión. Con filtros va aparte (`filtrado`) y solo
 *  vale para la firma de filtros con la que se guardó: cambiar los filtros lo olvida. Se reinicia al cerrar sesión. */
export type PlegadoBloques = { normal: Record<string, boolean>; filtrado: { firma: string; mapa: Record<string, boolean> } }
export const plegadoBloques = crearStore<PlegadoBloques>({ normal: {}, filtrado: { firma: '', mapa: {} } })
```

En `pedidos/filtros.ts`, al final:

```ts
/** Algún filtro activo (0.9.9): con filtros, los bloques con coincidencias salen desplegados. */
export function hayFiltros(f: FiltrosPedidos): boolean {
  return f.estados.size > 0 || f.proveedores.size > 0 || f.buscador.trim() !== '' || f.desde !== '' || f.hasta !== ''
}

/** Identifica unos filtros para olvidar el plegado a mano al cambiarlos. */
export function firmaFiltros(f: FiltrosPedidos): string {
  return JSON.stringify([[...f.estados].sort(), [...f.proveedores].sort(), f.buscador.trim().toLowerCase(), f.desde, f.hasta])
}
```

En `pedidos/filtros.test.ts`, al final:

```ts
describe('hayFiltros y firmaFiltros (0.9.9)', () => {
  it('cualquier filtro cuenta y la firma no depende del orden ni de mayúsculas o espacios del buscador', () => {
    expect(hayFiltros(FILTROS_PEDIDOS_VACIOS)).toBe(false)
    expect(hayFiltros({ ...FILTROS_PEDIDOS_VACIOS, buscador: '  ' })).toBe(false)
    expect(hayFiltros({ ...FILTROS_PEDIDOS_VACIOS, desde: '2026-10-01' })).toBe(true)
    const a = firmaFiltros({ ...FILTROS_PEDIDOS_VACIOS, estados: new Set(['pendiente', 'en_camino']), buscador: 'LCD ' })
    const b = firmaFiltros({ ...FILTROS_PEDIDOS_VACIOS, estados: new Set(['en_camino', 'pendiente']), buscador: 'lcd' })
    expect(a).toBe(b)
  })
})
```

(con `hayFiltros, firmaFiltros` añadidos al import de `./filtros`.)

- [ ] **Step 3: Columnas y CSV**

En `pedidos/columnas.tsx`:
1. `ANCHOS_PEDIDOS` gana `bloque: 64` tras `id: 55`.
2. Sustituir `crearColumnasPedidos` entero por:

```tsx
/** `vista` (0.9.9, spec §6.2): en la agrupada no va «Proveedor» (está en la cabecera del bloque); en la plana va
 *  «Bloque» tras «ID». */
export function crearColumnasPedidos({ onComponente, vista = 'plana' }: { onComponente: (p: CompraComponente) => void; vista?: 'agrupada' | 'plana' }): ColumnDef<CompraComponente>[] {
  const id: ColumnDef<CompraComponente> = { id: 'id', header: 'ID', size: ANCHOS_PEDIDOS.id, maxSize: ANCHOS_PEDIDOS.id, cell: ({ row }) => celdaId(row.original.idCompra) }
  const bloque: ColumnDef<CompraComponente> = {
    id: 'bloque', header: 'Bloque', size: ANCHOS_PEDIDOS.bloque, maxSize: ANCHOS_PEDIDOS.bloque,
    cell: ({ row }) => <span className={cn('text-azul-gris', CREMA_EN_FILA_SELECCIONADA)}>{row.original.idBloque ?? ''}</span>,
  }
  const resto: ColumnDef<CompraComponente>[] = [
    { id: 'fecha', header: 'Pedido', size: ANCHOS_PEDIDOS.fecha, maxSize: ANCHOS_PEDIDOS.fecha, accessorFn: fecha },
    {
      id: 'componente', header: 'Componente', size: ANCHOS_PEDIDOS.componente,
      // Calco del Label con TEXTO_ACCION, cursor mano y subrayado al pasar (:762-780), con la clase del enlace "En Camino"
      // de Stock (stock/columnas.tsx:50): lleva a Stock actual con la fila del componente seleccionada. Sin stopPropagation:
      // el clic también selecciona la fila antes de navegar, como en el JavaFX (StockController :762-772).
      cell: ({ row }) => (
        <button type="button" onClick={() => onComponente(row.original)} className="cursor-pointer text-texto-accion hover:underline">
          {row.original.tipoComponente}
        </button>
      ),
    },
    { id: 'proveedor', header: 'Proveedor', size: ANCHOS_PEDIDOS.proveedor, accessorFn: (p) => p.nombreProveedor },
    { id: 'cantidad', header: 'Cant.', size: ANCHOS_PEDIDOS.cantidad, maxSize: ANCHOS_PEDIDOS.cantidad, accessorFn: textoCantidad },
    { id: 'precio', header: 'P.Unit.', size: ANCHOS_PEDIDOS.precio, maxSize: ANCHOS_PEDIDOS.precio, cell: ({ row }) => precio(row.original) },
    { id: 'eur', header: 'EUR', size: ANCHOS_PEDIDOS.eur, maxSize: ANCHOS_PEDIDOS.eur, cell: ({ row }) => eur(row.original) },
    { id: 'estado', header: 'Estado', size: ANCHOS_PEDIDOS.estado, maxSize: ANCHOS_PEDIDOS.estado, cell: ({ row }) => <BadgeEstadoPedido pedido={row.original} /> },
  ]
  return vista === 'agrupada' ? [id, ...resto.filter((c) => c.id !== 'proveedor')] : [id, bloque, ...resto]
}
```

3. CSV:

```ts
export const CABECERAS_CSV_PEDIDOS = ['ID', 'Bloque', 'Nota del bloque', 'Fecha pedido', 'Componente', 'Cantidad', 'Urgente', 'Proveedor', 'Precio unidad', 'Divisa', 'Total EUR', 'Estado']
export function filaCsvPedido(p: CompraComponente): string[] {
  return [
    String(p.idCompra), p.idBloque == null ? '' : String(p.idBloque), p.notaBloque ?? '',
    formatear(p.fechaPedido, 'dd/MM/yyyy HH:mm'), p.tipoComponente, String(p.cantidad), p.esUrgente ? 'Sí' : 'No', p.nombreProveedor,
    formatearNumero(p.precioUnidadPedido), p.divisa, formatearNumero(totalFila(p)), p.estado,
  ]
}
```

En `pedidos/columnas.test.tsx`: las expectativas de cabeceras/ids de `crearColumnasPedidos({ onComponente })` ganan `Bloque` tras `ID` (vista plana por defecto); las de `CABECERAS_CSV_PEDIDOS` y `filaCsvPedido` ganan `Bloque` (`'1'`, de `BLOQUE_PRUEBA`) y `Nota del bloque` (`''`) tras `ID`; y un test nuevo:

```tsx
  it('0.9.9: la vista agrupada no lleva Proveedor ni Bloque', () => {
    const cabeceras = crearColumnasPedidos({ onComponente: () => {}, vista: 'agrupada' }).map((c) => c.header)
    expect(cabeceras).toEqual(['ID', 'Pedido', 'Componente', 'Cant.', 'P.Unit.', 'EUR', 'Estado'])
  })
```

- [ ] **Step 4: `PedidosPage.tsx`**

Sustituir el fichero entero por:

```tsx
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { CompraComponente, CompraOtro } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError, StaleDataError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
import { abrirNuevoOtroPedido, abrirNuevoPedido, formularioPedido } from '@/shared/lib/formularioPedido'
import { useStore } from '@/shared/lib/store'
import { useInteraccionesAbiertas } from '@/shared/lib/useInteraccionesAbiertas'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable, type GruposTabla } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { Input } from '@/shared/ui/input'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { RangoFechas } from '@/shared/ui/RangoFechas'
import { TogglePill } from '@/shared/ui/TogglePill'
import { ultimaRutaStock } from '../estado'
import { useProveedoresComponentes } from '../proveedores/api'
import { useCompras, useComprasOtros, useMoverLinea, useTransicionPedido, type AccionBloque, type AccionTransicion } from './api'
import { AccionBloqueDialog } from './bloques/AccionBloqueDialog'
import { CabeceraBloque } from './bloques/CabeceraBloque'
import { JuntarDialog } from './bloques/JuntarDialog'
import { BotonMenuBloque, MenuBloqueContextual } from './bloques/MenuBloque'
import { NotaBloqueDialog } from './bloques/NotaBloqueDialog'
import {
  agruparEnBloques, bloquesDelProveedor, claveBloque, destinosMover, entradasMenuBloque, estaDesplegado, estaSola, ordenarPorBloques,
  SIN_BLOQUE, textoMoverYaPedida, type AccionMenuBloque, type Bloque,
} from './bloques/reglas'
import { SepararDialog } from './bloques/SepararDialog'
import { CantidadDialog } from './CantidadDialog'
import { CABECERAS_CSV_OTROS, CABECERAS_CSV_PEDIDOS, claseFilaPedido, crearColumnasOtros, crearColumnasPedidos, filaCsvOtro, filaCsvPedido } from './columnas'
import { confirmacionDe } from './confirmaciones'
import { filtrosPedidos, plegadoBloques, seleccionPedidos } from './estado'
import { aplicarFiltrosPedidos, FILTROS_PEDIDOS_VACIOS, filtrosDesdeStock, firmaFiltros, hayFiltros } from './filtros'
import { EditarOtroPedidoDialog } from './formulario/EditarOtroPedidoDialog'
import { EditarPedidoDialog } from './formulario/EditarPedidoDialog'
import { MenuPedido } from './MenuPedido'
import { guardarAgrupar, leerAgrupar } from './preferencias'
import { chipDeEstado, entradasMenu, esCompra, ESTADOS_PEDIDO, idPedido, type AccionMenu, type EstadoPedido, type Pedido, type TipoPedido } from './reglas'

/** mostrarConflicto() de StockController :1929-1933. */
const MSG_MODIFICADO = 'Este pedido fue modificado por otro usuario. Los datos se han recargado.'
const OPCIONES_TOGGLE = [
  { to: '/stock/pedidos', etiqueta: 'Componentes' },
  { to: '/stock/pedidos/otros', etiqueta: 'Otros' },
]
const RUTA: Record<TipoPedido, string> = { componentes: '/stock/pedidos', otros: '/stock/pedidos/otros' }
/** Mapa vacío estable (0.9.9): con filtros nuevos, nada plegado a mano. */
const SIN_PLEGADOS: Record<string, boolean> = {}

type AccionDirecta = 'confirmar' | 'recibido' | 'cerrarSinResto'
type AccionConfirmada = 'cancelar' | 'borrar' | 'revertir'
/** Entrada del menú → endpoint (inventario §7.1). */
const TRANSICION: Record<AccionDirecta | AccionConfirmada, AccionTransicion> = {
  confirmar: 'confirmar',
  recibido: 'confirmar-recibido',
  cerrarSinResto: 'confirmar-alterado',
  cancelar: 'cancelar',
  borrar: 'borrar',
  revertir: 'desrecibir',
}

type DialogoCantidad = { modo: 'parcial' | 'resto'; pedido: Pedido } | null
type Confirmar = { accion: AccionConfirmada; pedido: Pedido } | null

/** Pestaña "Pedidos" de StockView.fxml (spec 4b §6): toggle Componentes | Otros por rutas (P4), cuatro filtros compartidos,
 *  la tabla del toggle visible, menú de transiciones (SUPERTECNICO), CSV y "Actualizado". 0.9.9: Componentes agrupada
 *  por bloques (spec «Pedidos por bloques» §6), con interruptor a la vista plana. */
export function PedidosPage({ tipo }: { tipo: TipoPedido }) {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const navigate = useNavigate()
  const location = useLocation()
  const { mostrarError, mostrarAviso } = useAlerta()
  const { hayAlguna, marcar } = useInteraccionesAbiertas()
  const [formulario] = useStore(formularioPedido)
  const [filtros, setFiltros] = useStore(filtrosPedidos)
  const [seleccionada, setSeleccionada] = useStore(seleccionPedidos[tipo])
  const [dialogoCantidad, setDialogoCantidad] = useState<DialogoCantidad>(null)
  const [confirmar, setConfirmar] = useState<Confirmar>(null)
  // T17: editores (EditarPedidoDialog / EditarOtroPedidoDialog se pintan con este estado; ya congela el sondeo)
  const [editando, setEditando] = useState<Pedido | null>(null)
  // Texto de un 422 del servidor para el diálogo de cantidad abierto (spec §8): se pinta dentro, que sigue abierto.
  const [errorServidor, setErrorServidor] = useState<string | null>(null)
  const [peticionDesplazamiento, setPeticionDesplazamiento] = useState(0)
  // 0.9.9: bloques (spec «Pedidos por bloques» §6).
  const componentes = tipo === 'componentes'
  const [agrupar, setAgrupar] = useState(leerAgrupar)
  const vistaAgrupada = componentes && agrupar
  const [plegado, setPlegado] = useStore(plegadoBloques)
  const [accionBloque, setAccionBloque] = useState<{ bloque: Bloque; accion: AccionBloque } | null>(null)
  const [separando, setSeparando] = useState<Bloque | null>(null)
  const [juntando, setJuntando] = useState<Bloque | null>(null)
  const [notaDe, setNotaDe] = useState<Bloque | null>(null)
  const [moverConfirmar, setMoverConfirmar] = useState<{ pedido: CompraComponente; idBloque: number | null } | null>(null)

  // Sondeo congelado con un menú, un desplegable, un diálogo, un editor o el formulario de alta abiertos (spec §7-§8, D4).
  const activo = !hayAlguna && formulario === null
  const compras = useCompras({ activo: activo && componentes, habilitada: componentes })
  const otros = useComprasOtros({ activo: activo && tipo === 'otros', habilitada: tipo === 'otros' })
  // Proveedores del filtro: la misma consulta que la pestaña Proveedores; aquí no sondea.
  const proveedores = useProveedoresComponentes({ activo: false })
  const transicion = useTransicionPedido(tipo)
  const mover = useMoverLinea()

  // Última pestaña de Stock para el botón de la barra superior (caché de vista del JavaFX, S2).
  useEffect(() => { ultimaRutaStock.set(RUTA[tipo]) }, [tipo])
  const hayModal = dialogoCantidad !== null || confirmar !== null || editando !== null || accionBloque !== null || separando !== null
    || juntando !== null || notaDe !== null || moverConfirmar !== null
  useEffect(() => {
    if (!hayModal) return
    marcar(true)
    return () => marcar(false)
  }, [hayModal, marcar])

  // Llegada desde "En Camino" de Stock (S8, navegarAPedidosDeComponente :217-231): los parámetros se leen una vez al montar,
  // se aplican al store (proveedor y fechas intactos), se limpia la URL con replace y, en cuanto hay datos, se selecciona la
  // primera fila filtrada y se desplaza a ella. Los refs evitan repetirlo si el efecto vuelve a correr.
  const [llegada] = useState(() => filtrosDesdeStock(new URLSearchParams(location.search)))
  const llegadaAplicada = useRef(false)
  const primeraPendiente = useRef(llegada !== null)
  useEffect(() => {
    if (llegada === null || llegadaAplicada.current) return
    llegadaAplicada.current = true
    filtrosPedidos.set((f) => ({ ...f, ...llegada }))
    navigate(location.pathname, { replace: true })
  }, [llegada, navigate, location.pathname])
  const datos: Pedido[] | undefined = componentes ? compras.data : otros.data
  useEffect(() => {
    if (!primeraPendiente.current || datos === undefined) return
    primeraPendiente.current = false
    // Se filtra con el store (no con `filtros` del render): si los datos ya estaban en caché, este efecto corre en el mismo
    // commit que el de arriba, antes de que el render vea los filtros nuevos.
    const actuales = filtrosPedidos.get()
    const primera = aplicarFiltrosPedidos(datos, actuales)[0]
    if (!primera) return
    // 0.9.9: el bloque de la línea elegida, desplegado.
    if (esCompra(primera)) {
      const firmaLlegada = firmaFiltros(actuales)
      plegadoBloques.set((p) => ({
        ...p,
        filtrado: { firma: firmaLlegada, mapa: { ...(p.filtrado.firma === firmaLlegada ? p.filtrado.mapa : {}), [claveBloque(primera)]: true } },
      }))
    }
    setSeleccionada(String(idPedido(primera)))
    // eslint-disable-next-line react-hooks/set-state-in-effect -- pide a la tabla desplazarse a la fila recién elegida (select + scrollTo del JavaFX)
    setPeticionDesplazamiento((n) => n + 1)
  }, [datos, setSeleccionada])

  const irAStock = useCallback((p: CompraComponente) => navigate(`/stock?componente=${p.idCom}`), [navigate])
  const columnasPedidos = useMemo(() => crearColumnasPedidos({ onComponente: irAStock, vista: vistaAgrupada ? 'agrupada' : 'plana' }), [irAStock, vistaAgrupada])
  const columnasOtros = useMemo(() => crearColumnasOtros(), [])
  const bloques = useMemo(() => agruparEnBloques(compras.data ?? []), [compras.data])
  const visiblesCompras = useMemo(() => {
    const filtradas = aplicarFiltrosPedidos(compras.data ?? [], filtros)
    return vistaAgrupada ? ordenarPorBloques(filtradas, bloques) : filtradas
  }, [compras.data, filtros, vistaAgrupada, bloques])
  const visiblesOtros = useMemo(() => aplicarFiltrosPedidos(otros.data ?? [], filtros), [otros.data, filtros])
  // Calco de :1729-1751: el filtro Proveedor solo ofrece los activos.
  const activos = useMemo(() => (proveedores.data ?? []).filter((p) => p.activo), [proveedores.data])

  // Calco de exportarPedidos/exportarOtros (:1961-2006): la tabla visible, filtrada y en el orden mostrado.
  useRegistrarExportable(() => {
    if (componentes) descargarCsv('pedidos', CABECERAS_CSV_PEDIDOS, visiblesCompras.map(filaCsvPedido))
    else descargarCsv('pedidos_otros', CABECERAS_CSV_OTROS, visiblesOtros.map(filaCsvOtro))
  })

  // ── 0.9.9: plegado ─────────────────────────────────────────────────────────────────────────────────────────────
  const conFiltros = hayFiltros(filtros)
  const firma = firmaFiltros(filtros)
  const mapaPlegado = conFiltros ? (plegado.filtrado.firma === firma ? plegado.filtrado.mapa : SIN_PLEGADOS) : plegado.normal
  const desplegado = (clave: string) => {
    const b = bloques.get(clave)
    return b === undefined || estaDesplegado(b, conFiltros, mapaPlegado)
  }
  function fijarPlegado(cambios: Record<string, boolean>) {
    setPlegado((p) => (conFiltros
      ? { ...p, filtrado: { firma, mapa: { ...(p.filtrado.firma === firma ? p.filtrado.mapa : {}), ...cambios } } }
      : { ...p, normal: { ...p.normal, ...cambios } }))
  }
  const clavesVisibles = [...new Set(visiblesCompras.map(claveBloque))]
  const hayDesplegado = clavesVisibles.some(desplegado)
  function alternarTodos() {
    fijarPlegado(Object.fromEntries(clavesVisibles.map((c) => [c, !hayDesplegado])))
  }
  function alternarAgrupar() {
    setAgrupar(!agrupar)
    guardarAgrupar(!agrupar)
  }

  /** 409 → aviso genérico, salvo desrecibir, que enseña el mensaje del servidor (stock insuficiente o estado, :1635). Lo que
   *  gestiona el mecanismo global (401, sin conexión) no se repite. La recarga la hace el onSettled de la mutación.
   *  Un 409 es un conflicto de concurrencia, no un fallo: se avisa como "Advertencia" (Alert WARNING de
   *  StockController.mostrarConflicto :1851), calco del JavaFX. El resto de errores sigue como "Error". */
  function avisarFallo(accion: AccionTransicion | 'mover', e: unknown) {
    if (esErrorGestionadoGlobalmente(e)) return
    const mensaje = accion === 'desrecibir' ? mensajeDeError(e) : mensajeDeError(e, { staleData: MSG_MODIFICADO })
    if (e instanceof StaleDataError) mostrarAviso('Advertencia', mensaje)
    else mostrarError(mensaje)
  }

  function transicionar(accion: AccionTransicion, pedido: Pedido) {
    transicion.mutate({ accion, pedido }, { onError: (e) => avisarFallo(accion, e) })
  }

  // ── 0.9.9: mover y acciones de bloque ──────────────────────────────────────────────────────────────────────────
  function moverAhora(pedido: CompraComponente, idBloque: number | null) {
    mover.mutate({ pedido, idBloque }, { onError: (e) => avisarFallo('mover', e) })
  }
  /** Pendiente: sin preguntar. Ya pedida: «Mover línea ya pedida» (spec §6.4). */
  function pedirMover(pedido: CompraComponente, idBloque: number | null) {
    if (pedido.estado === 'pendiente') moverAhora(pedido, idBloque)
    else setMoverConfirmar({ pedido, idBloque })
  }
  function alElegirBloque(accion: AccionMenuBloque, b: Bloque) {
    switch (accion) {
      case 'confirmar':
      case 'recibir':
      case 'cancelar':
      case 'borrar':
        setAccionBloque({ bloque: b, accion })
        return
      case 'anadir':
        abrirNuevoPedido({ modo: 'bloque', idBloque: b.idBloque as number, idProv: b.idProv })
        return
      case 'separar':
        setSeparando(b)
        return
      case 'juntar':
        setJuntando(b)
        return
      case 'nota':
        setNotaDe(b)
        return
    }
  }
  const entradasDe = (b: Bloque) => entradasMenuBloque(b, bloquesDelProveedor(bloques, b.idProv, b.clave).length)
  const conMenuBloque = (b: Bloque | undefined): b is Bloque => puedeEditar && b !== undefined && b.clave !== SIN_BLOQUE
  const grupos: GruposTabla<CompraComponente> | undefined = vistaAgrupada
    ? {
        clave: claveBloque,
        desplegado,
        cabecera: (clave, filas) => {
          const b = bloques.get(clave)
          if (b === undefined) return null
          return (
            <CabeceraBloque bloque={b} visibles={filas.length} desplegado={desplegado(clave)} onAlternar={() => fijarPlegado({ [clave]: !desplegado(clave) })}
              menu={conMenuBloque(b) ? <BotonMenuBloque idBloque={b.idBloque as number} entradas={entradasDe(b)} onAccion={(a) => alElegirBloque(a, b)} onInteraccion={marcar} /> : undefined} />
          )
        },
        menuCabecera: (clave) => {
          const b = bloques.get(clave)
          return conMenuBloque(b) ? <MenuBloqueContextual entradas={entradasDe(b)} onAccion={(a) => alElegirBloque(a, b)} onInteraccion={marcar} /> : null
        },
      }
    : undefined

  function alElegir(accion: AccionMenu, pedido: Pedido) {
    switch (accion) {
      case 'confirmar':
      case 'recibido':
      case 'cerrarSinResto':
        transicionar(TRANSICION[accion], pedido)
        return
      case 'parcial':
      case 'resto':
        setErrorServidor(null)
        setDialogoCantidad({ modo: accion, pedido })
        return
      case 'cancelar':
      case 'borrar':
      case 'revertir':
        setConfirmar({ accion, pedido })
        return
      case 'editar':
        setEditando(pedido)
        return
    }
  }

  function cerrarCantidad() {
    setDialogoCantidad(null)
    setErrorServidor(null)
  }

  /** 422 → inline con el diálogo abierto; 409 → se cierra y avisa; otro error → diálogo abierto y aviso (patrón de Stock). */
  function confirmarCantidad(valor: number) {
    if (!dialogoCantidad) return
    const { modo, pedido } = dialogoCantidad
    const accion: AccionTransicion = modo === 'parcial' ? 'confirmar-parcial' : 'recibir-resto'
    setErrorServidor(null)
    transicion.mutate({ accion, pedido, cantidad: valor }, {
      onSuccess: cerrarCantidad,
      onError: (e) => {
        if (e instanceof ReglaNegocioError) { setErrorServidor(e.message); return }
        if (e instanceof StaleDataError) cerrarCantidad()
        avisarFallo(accion, e)
      },
    })
  }

  function confirmarAccion() {
    if (!confirmar) return
    const { accion, pedido } = confirmar
    setConfirmar(null)
    transicionar(TRANSICION[accion], pedido)
  }

  // Menú solo para el SUPERTECNICO (:961-966). Un cancelado no lleva menú salvo «Mover a bloque» (0.9.9, componentes).
  const menuFila = puedeEditar
    ? (p: Pedido) => {
        const mov = esCompra(p)
          ? { destinos: destinosMover(p, bloques), sola: estaSola(p, bloques), onMover: (idBloque: number | null) => pedirMover(p, idBloque) }
          : undefined
        return entradasMenu(p.estado).length > 0 || mov ? <MenuPedido pedido={p} onAccion={alElegir} onInteraccion={marcar} mover={mov} /> : null
      }
    : undefined
  const propsTabla = {
    ajuste: 'fluido' as const,
    getRowId: (p: Pedido) => String(idPedido(p)),
    filaClase: claseFilaPedido,
    seleccionada,
    onSeleccionar: setSeleccionada,
    pedirDesplazamiento: peticionDesplazamiento,
    menuFila,
  }
  const textosConfirmar = confirmar ? confirmacionDe(confirmar.accion, confirmar.pedido) : null

  return (
    <div className="p-5">
      <div className="mb-3 flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-azul-medio">Pedidos</h1>
        <TogglePill opciones={OPCIONES_TOGGLE} />
      </div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {/* Calco del MenuButton "Estado" con CustomMenuItem (no cierra al marcar) y del MultiSelectComboBox de proveedores
            (140 px, StockView.fxml:122-126): abrirlos congela el refresco (onOpenChange → marcar). */}
        <MultiSelect
          opciones={ESTADOS_PEDIDO}
          clave={(e) => e}
          etiqueta={chipDeEstado}
          seleccion={filtros.estados}
          onChange={(s) => setFiltros((f) => ({ ...f, estados: s as Set<EstadoPedido> }))}
          textoVacio="Estado"
          textoPlural={(n) => `${n} estados`}
          onOpenChange={marcar}
          className="min-w-[140px]"
        />
        <MultiSelect
          opciones={activos}
          clave={(p) => p.nombre}
          etiqueta={(p) => p.nombre}
          seleccion={filtros.proveedores}
          onChange={(s) => setFiltros((f) => ({ ...f, proveedores: s }))}
          textoVacio="Proveedor"
          textoPlural={(n) => `${n} proveedores`}
          onOpenChange={marcar}
          className="min-w-[140px]"
        />
        {/* "Buscar componente…" también en Otros (calco, inventario §1). */}
        <Input value={filtros.buscador} onChange={(e) => setFiltros((f) => ({ ...f, buscador: e.target.value }))} placeholder="Buscar componente…" className="w-[180px] bg-superficie" />
        <RangoFechas desde={filtros.desde} hasta={filtros.hasta} onChange={(desde, hasta) => setFiltros((f) => ({ ...f, desde, hasta }))} />
        {/* No toca el toggle ni la selección (:270-278). */}
        <BotonSecundario onClick={() => setFiltros({ ...FILTROS_PEDIDOS_VACIOS, estados: new Set(), proveedores: new Set() })}>Limpiar filtros</BotonSecundario>
        {puedeEditar &&
          (componentes ? (
            <BotonPrimario className="ml-6" onClick={() => abrirNuevoPedido({ modo: 'vacio' })}>Nuevo pedido</BotonPrimario>
          ) : (
            <BotonPrimario className="ml-6" onClick={() => abrirNuevoOtroPedido()}>Nuevo otro pedido</BotonPrimario>
          ))}
        {componentes && (
          <>
            <span aria-hidden className="mx-1 h-[22px] w-px bg-fila-sep" />
            <button type="button" role="switch" aria-checked={agrupar} onClick={alternarAgrupar}
              className="flex cursor-pointer items-center gap-2 text-[12px] font-bold text-azul-medio">
              <span className={cn('relative h-[18px] w-[34px] rounded-full transition-colors', agrupar ? 'bg-verde-ok' : 'bg-pill-borde')}>
                <span className={cn('absolute top-[2px] h-[14px] w-[14px] rounded-full bg-superficie transition-[left]', agrupar ? 'left-[18px]' : 'left-[2px]')} />
              </span>
              Agrupar por bloque
            </button>
            {agrupar && (
              <button type="button" onClick={alternarTodos} className="cursor-pointer text-[12px] font-bold text-texto-accion hover:underline">
                {hayDesplegado ? 'Plegar todo' : 'Desplegar todo'}
              </button>
            )}
          </>
        )}
      </div>
      {componentes ? (
        <DataTable<CompraComponente> columns={columnasPedidos} data={visiblesCompras} vacio="Sin pedidos" grupos={grupos} {...propsTabla} />
      ) : (
        <DataTable<CompraOtro> columns={columnasOtros} data={visiblesOtros} vacio="Sin otros pedidos" {...propsTabla} />
      )}
      {/* P7: recarga la tabla visible (el JavaFX recarga siempre la de componentes). */}
      <EtiquetaActualizado
        actualizadoEn={componentes ? compras.dataUpdatedAt : otros.dataUpdatedAt}
        onRecargar={() => (componentes ? compras.refetch({ throwOnError: true }) : otros.refetch({ throwOnError: true }))}
      />

      <CantidadDialog
        modo="parcial"
        pedido={dialogoCantidad?.modo === 'parcial' ? dialogoCantidad.pedido : null}
        errorServidor={errorServidor}
        enviando={transicion.isPending}
        onConfirmar={confirmarCantidad}
        onCancelar={cerrarCantidad}
      />
      <CantidadDialog
        modo="resto"
        pedido={dialogoCantidad?.modo === 'resto' ? dialogoCantidad.pedido : null}
        errorServidor={errorServidor}
        enviando={transicion.isPending}
        onConfirmar={confirmarCantidad}
        onCancelar={cerrarCantidad}
      />
      <ConfirmDialog
        abierto={confirmar !== null}
        titulo={textosConfirmar?.titulo ?? ''}
        descripcion={textosConfirmar?.descripcion ?? ''}
        textoAccion={textosConfirmar?.textoAccion ?? ''}
        onConfirmar={confirmarAccion}
        onCancelar={() => setConfirmar(null)}
      />
      <EditarPedidoDialog pedido={componentes && editando !== null && esCompra(editando) ? editando : null} onCerrar={() => setEditando(null)} />
      <EditarOtroPedidoDialog pedido={tipo === 'otros' && editando !== null && !esCompra(editando) ? editando : null} onCerrar={() => setEditando(null)} />

      {/* 0.9.9: bloques. Cada diálogo se monta solo mientras está abierto (empieza de cero en cada apertura). */}
      <ConfirmDialog
        abierto={moverConfirmar !== null}
        titulo="Mover línea ya pedida"
        descripcion={moverConfirmar ? textoMoverYaPedida(moverConfirmar.pedido, moverConfirmar.idBloque) : ''}
        textoAccion="Mover"
        onConfirmar={() => {
          if (moverConfirmar) moverAhora(moverConfirmar.pedido, moverConfirmar.idBloque)
          setMoverConfirmar(null)
        }}
        onCancelar={() => setMoverConfirmar(null)}
      />
      {accionBloque && <AccionBloqueDialog bloque={accionBloque.bloque} accion={accionBloque.accion} onCerrar={() => setAccionBloque(null)} />}
      {separando && <SepararDialog bloque={separando} onCerrar={() => setSeparando(null)} />}
      {juntando && (
        <JuntarDialog bloque={juntando} candidatos={bloquesDelProveedor(bloques, juntando.idProv, juntando.clave)} onCerrar={() => setJuntando(null)} />
      )}
      {notaDe && <NotaBloqueDialog bloque={notaDe} onCerrar={() => setNotaDe(null)} />}
    </div>
  )
}
```

(`abrirNuevoPedido({ modo: 'bloque', … })` no compila hasta la Task 12, que añade ese modo a `PrecargaPedido`: en esta tarea, añadir ya a `shared/lib/formularioPedido.ts` la variante `| { modo: 'bloque'; idBloque: number; idProv: number }` y, en `formulario/lineas.ts`, el caso `case 'bloque': return { lineas: [], omitidas: 0 }` en `precargaInicial`.)

- [ ] **Step 5: Tests de siempre de la página y smoke E2E**

En `pedidos/PedidosPage.test.tsx` (son de la vista plana de antes):
1. En el `beforeEach`, `localStorage.setItem('pedidos.agruparPorBloque', 'no')`, y en el `afterEach`, `localStorage.removeItem('pedidos.agruparPorBloque')`.
2. `nombresEnTabla` pasa a buscar la columna por su cabecera:

```tsx
const nombresEnTabla = () => {
  const cabeceras = screen.getAllByRole('columnheader').map((c) => c.textContent)
  const i = cabeceras.findIndex((t) => t === 'Componente' || t === 'Concepto')
  return screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[i].textContent)
}
```

3. Las demás lecturas de celdas por posición en la tabla de Componentes suben una posición (la columna «Bloque» va tras «ID»); las de Otros no cambian. Las expectativas del CSV de Componentes ganan `Bloque` y `Nota del bloque` tras `ID` (valores `'1'` y `''`).

En `tests/e2e/pedidos.spec.ts`, en el test de componentes:
1. El cuerpo esperado del lote gana `destinos: null` (sin bloques abiertos del proveedor nuevo no viaja ninguno).
2. Justo antes de `const fila = page.getByRole('row').filter({ hasText: nombreProv })`:

```ts
      // 0.9.9: por defecto agrupada; su bloque nuevo sale con el proveedor en la cabecera. El resto del test, en la plana.
      await expect(page.locator('tr[data-cabecera]').filter({ hasText: nombreProv })).toHaveCount(1)
      await page.getByRole('switch', { name: 'Agrupar por bloque' }).click()
```

3. Las dos `fila.getByRole('cell').nth(4)` del test de componentes → `nth(5)` (la columna «Bloque»). El test de Otros no cambia.

- [ ] **Step 6: Ejecutar**

Run: `npx vitest run src/modules/almacen/pedidos && npx tsc -b && npm run lint`
Expected: todo en verde.
Run: `npx vitest run`
Expected: todo en verde.

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add -A src tests/e2e/pedidos.spec.ts
git commit -m "feat: pestana Pedidos agrupada por bloques con interruptor a la vista plana"
```

---

### Task 12: Web — «Nuevo pedido» con resumen de bloques, «Añadir líneas» y «Editar» con bloque

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/destinos.ts`
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/ResumenBloques.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.ts` (`cuerpoLoteCompras` con `destinos`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/DialogoLineas.tsx` (`pie`, `proveedorFijo`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/EditarPedidoDialog.tsx`
- Test: `formulario/destinos.test.ts`, `formulario/NuevoPedidoDialog.bloques.test.tsx`, `formulario/EditarPedidoDialog.bloques.test.tsx` (nuevos)

**Interfaces:**
- Consumes: `abiertosDe`, `bloquesDelProveedor`, `destinoSugeridoEdicion`, `fechaCorta`, `situacion`, `textoLineas`, `textoMoverYaPedida`, `agruparEnBloques` (Task 7); `useCompras`; precarga `{ modo: 'bloque'; idBloque; idProv }` (Task 11).
- Produces: `VALOR_NUEVO = 'nuevo'`; `proveedoresDeLineas(lineas): { idProv: number; n: number }[]`; `opcionesDestino(bloques, idProv): { valor: string; etiqueta: string }[]`; `destinoElegido(bloques, idProv, elegidos: Record<number, number | null>): number | null`; `destinosDelCuerpo(lineas, bloques, elegidos): { idProv: number; idBloque: number | null }[] | null` (solo los proveedores con bloques abiertos; ninguno → null); `cuerpoLoteCompras(lineas, origen, activos, destinos = null)`; `<ResumenBloques lineas proveedores bloques elegidos onElegir fijo cargando />`; `DialogoLineas` con `pie?: ReactNode` y `proveedorFijo?: string`.

- [ ] **Step 1: Tests que fallan**

Crear `formulario/destinos.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { lineaDePrueba as l } from '../bloques/datosPrueba'
import { agruparEnBloques } from '../bloques/reglas'
import { destinoElegido, destinosDelCuerpo, opcionesDestino, proveedoresDeLineas, VALOR_NUEVO } from './destinos'
import { lineaCompraVacia } from './lineas'

const bloques = agruparEnBloques([
  l({ idCompra: 1, idProv: 1, idBloque: 41, fechaBloque: '2026-10-09T10:00:00' }),
  l({ idCompra: 2, idProv: 1, idBloque: 40, fechaBloque: '2026-10-08T10:00:00' }),
  l({ idCompra: 3, idProv: 1, idBloque: 39, fechaBloque: '2026-10-07T10:00:00', estado: 'en_camino' }),
])
const linea = (id: number, idProv: number | null) => ({ ...lineaCompraVacia(id, 11), idProv })

describe('destinos de «Nuevo pedido» (spec §6.6)', () => {
  it('proveedores en orden de aparición con sus líneas', () => {
    expect(proveedoresDeLineas([linea(1, 2), linea(2, 1), linea(3, 2), linea(4, null)])).toEqual([{ idProv: 2, n: 2 }, { idProv: 1, n: 1 }])
  })
  it('opciones: los abiertos del más reciente al más antiguo y «Bloque nuevo»; sin abiertos, ninguna', () => {
    expect(opcionesDestino(bloques, 1)).toEqual([
      { valor: '41', etiqueta: 'Añadir a Bloque 41 · 09/10 · 1 línea' },
      { valor: '40', etiqueta: 'Añadir a Bloque 40 · 08/10 · 1 línea' },
      { valor: VALOR_NUEVO, etiqueta: 'Bloque nuevo' },
    ])
    expect(opcionesDestino(bloques, 2)).toEqual([])
  })
  it('elegido: lo de a mano si sigue siendo opción; si no, el abierto más reciente; sin abiertos, nuevo', () => {
    expect(destinoElegido(bloques, 1, {})).toBe(41)
    expect(destinoElegido(bloques, 1, { 1: 40 })).toBe(40)
    expect(destinoElegido(bloques, 1, { 1: null })).toBeNull()
    expect(destinoElegido(bloques, 1, { 1: 39 })).toBe(41)
    expect(destinoElegido(bloques, 2, {})).toBeNull()
  })
  it('cuerpo: solo los proveedores con desplegable; ninguno → null', () => {
    expect(destinosDelCuerpo([linea(1, 1), linea(2, 2)], bloques, { 1: null })).toEqual([{ idProv: 1, idBloque: null }])
    expect(destinosDelCuerpo([linea(1, 2)], bloques, {})).toBeNull()
  })
})
```

Crear `formulario/NuevoPedidoDialog.bloques.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompraComponente } from '@/shared/api/client'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { renderConRouter, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { lineaDePrueba as l } from '../bloques/datosPrueba'
import { COMPONENTES, PROVEEDORES } from './datosPrueba'
import { NuevoPedidoDialog } from './NuevoPedidoDialog'

/** Bloque 41 de ACME (proveedor 1), abierto. */
const ABIERTO_ACME: CompraComponente[] = [l({ idCompra: 9, idProv: 1, nombreProveedor: 'ACME', idBloque: 41 })]
let lotes: unknown[]
let compras: CompraComponente[]
let lecturas: number
let respuestaLote: () => Response

beforeEach(() => {
  lotes = []
  compras = ABIERTO_ACME
  lecturas = 0
  respuestaLote = () => HttpResponse.json({ idsCreados: [101] })
  server.use(
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(COMPONENTES)),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ value: 1.1367 })),
    http.get('*/api/compras', () => { lecturas += 1; return HttpResponse.json(compras) }),
    http.post('*/api/compras/lote', async ({ request }) => { lotes.push(await request.json()); return respuestaLote() }),
  )
})

function abrir(precarga: PrecargaPedido = { modo: 'vacio' }) {
  const onCerrar = vi.fn()
  renderConRouter([{ path: '/', element: <NuevoPedidoDialog precarga={precarga} onCerrar={onCerrar} /> }], { sesion: SESION_SUPER })
  return onCerrar
}

async function anadirLinea(n: number, proveedor?: string) {
  await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
  await userEvent.type(screen.getByRole('combobox', { name: `Componente línea ${n}` }), 'lcd')
  await userEvent.click(await screen.findByRole('option', { name: 'lcd-x-negro' }))
  if (proveedor === undefined) return
  await userEvent.click(screen.getByRole('combobox', { name: `Proveedor línea ${n}` }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: `Proveedor línea ${n}` })).findByRole('button', { name: proveedor }))
}
const resumen = () => screen.getByRole('region', { name: 'Bloques' })
const confirmar = () => userEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }))

describe('«Nuevo pedido» con bloques (0.9.9)', () => {
  it('sin líneas, el resumen lo explica', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    expect(resumen()).toHaveTextContent('Añade líneas: aquí verás a qué bloque va cada proveedor.')
  })

  it('un proveedor con bloque abierto se suma a él por defecto y viaja en destinos', async () => {
    const onCerrar = abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await anadirLinea(1, 'ACME')
    expect(await within(resumen()).findByRole('combobox', { name: 'Bloque de ACME' })).toHaveTextContent('Añadir a Bloque 41')
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(lotes[0]).toMatchObject({ destinos: [{ idProv: 1, idBloque: 41 }] })
  })

  it('elegir «Bloque nuevo» lo manda con idBloque null', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await anadirLinea(1, 'ACME')
    await userEvent.click(await within(resumen()).findByRole('combobox', { name: 'Bloque de ACME' }))
    await userEvent.click(await within(screen.getByRole('listbox', { name: 'Bloque de ACME' })).findByRole('button', { name: 'Bloque nuevo' }))
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
    expect(lotes[0]).toMatchObject({ destinos: [{ idProv: 1, idBloque: null }] })
  })

  it('un proveedor sin bloques abiertos va a uno nuevo sin preguntar y no viaja en destinos', async () => {
    compras = []
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await anadirLinea(1, 'Proveedor B')
    expect(resumen()).toHaveTextContent(/Proveedor B.*1 línea.*Bloque nuevo.*no tiene ninguno abierto/)
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
    expect(lotes[0]).toMatchObject({ destinos: null })
  })

  it('«Añadir líneas» de un bloque: título, proveedor fijo y destino fijado', async () => {
    const onCerrar = abrir({ modo: 'bloque', idBloque: 41, idProv: 1 })
    await screen.findByRole('dialog', { name: 'Añadir líneas · Bloque 41' })
    await anadirLinea(1)
    expect(screen.queryByRole('combobox', { name: 'Proveedor línea 1' })).not.toBeInTheDocument()
    expect(resumen()).toHaveTextContent(/ACME.*Bloque 41.*\(fijado\)/)
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(lotes[0]).toMatchObject({ lineas: [{ idProv: 1 }], destinos: [{ idProv: 1, idBloque: 41 }] })
  })

  it('un 409 de destino deja el formulario abierto con el mensaje y recarga los pedidos', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'El bloque 41 ya no existe; elige otro destino.' }, { status: 409 })
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await anadirLinea(1, 'ACME')
    await within(resumen()).findByRole('combobox', { name: 'Bloque de ACME' })
    const antes = lecturas
    await confirmar()
    expect(await screen.findByText('El bloque 41 ya no existe; elige otro destino.')).toBeInTheDocument()
    await waitFor(() => expect(lecturas).toBeGreaterThan(antes))
  })
})
```

Crear `formulario/EditarPedidoDialog.bloques.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompraComponente } from '@/shared/api/client'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { lineaDePrueba as l } from '../bloques/datosPrueba'
import { COMPRA, PROVEEDORES } from './datosPrueba'
import { EditarPedidoDialog } from './EditarPedidoDialog'

let puts: unknown[]

beforeEach(() => {
  puts = []
  server.use(
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ value: 1.1367 })),
    // Bloque 60 de «Proveedor B» (proveedor 2), abierto; COMPRA (#7, ACME) está en el bloque 1.
    http.get('*/api/compras', () => HttpResponse.json([COMPRA, l({ idCompra: 20, idProv: 2, nombreProveedor: 'Proveedor B', idBloque: 60 })])),
    http.put('*/api/compras/:id', async ({ request }) => { puts.push(await request.json()); return new HttpResponse(null, { status: 200 }) }),
  )
})

function abrir(pedido: CompraComponente) {
  const onCerrar = vi.fn()
  renderConProviders(<EditarPedidoDialog pedido={pedido} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

async function elegirProveedor(nombre: string) {
  await userEvent.click(await screen.findByRole('combobox', { name: 'Proveedor' }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: 'Proveedor' })).findByRole('button', { name: nombre }))
}

describe('«Editar pedido» cambiando el proveedor (0.9.9, spec §6.6)', () => {
  it('sin cambiar el proveedor no hay campo Bloque', async () => {
    abrir(COMPRA)
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    expect(screen.queryByRole('combobox', { name: 'Bloque' })).not.toBeInTheDocument()
  })

  it('una pendiente sugiere el bloque abierto del proveedor nuevo y lo manda', async () => {
    const onCerrar = abrir(COMPRA)
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await elegirProveedor('Proveedor B')
    expect(await screen.findByRole('combobox', { name: 'Bloque' })).toHaveTextContent('Bloque 60')
    expect(screen.getByText(/la línea sale del Bloque 1 y pasa a un bloque de Proveedor B/)).toHaveTextContent('Ojo: Proveedor B trabaja en USD; revisa el precio.')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(puts[0]).toMatchObject({ idProv: 2, destino: { idBloque: 60 } })
  })

  it('una ya pedida sugiere «Bloque nuevo» y pide confirmación; «Cancelar» vuelve al editor', async () => {
    const onCerrar = abrir({ ...COMPRA, estado: 'en_camino' })
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await elegirProveedor('Proveedor B')
    expect(await screen.findByRole('combobox', { name: 'Bloque' })).toHaveTextContent('Bloque nuevo')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    const confirmacion = await screen.findByRole('dialog', { name: 'Mover línea ya pedida' })
    await userEvent.click(within(confirmacion).getByRole('button', { name: 'Cancelar' }))
    expect(puts).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Mover línea ya pedida' })).getByRole('button', { name: 'Guardar y mover' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(puts[0]).toMatchObject({ idProv: 2, destino: { idBloque: null } })
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario`
Expected: FAIL (`./destinos` no existe; sin resumen ni campo Bloque).

- [ ] **Step 2: `destinos.ts`, `ResumenBloques.tsx` y `cuerpoLoteCompras`**

Crear `formulario/destinos.ts`:

```ts
import { abiertosDe, fechaCorta, textoLineas, type Bloque } from '../bloques/reglas'
import type { LineaCompra } from './lineas'

/** Valor de «Bloque nuevo» en los desplegables de bloque (0.9.9). */
export const VALOR_NUEVO = 'nuevo'
export type OpcionDestino = { valor: string; etiqueta: string }

/** Proveedores de las líneas, en el orden en que aparecen, con cuántas líneas lleva cada uno. */
export function proveedoresDeLineas(lineas: LineaCompra[]): { idProv: number; n: number }[] {
  const cuenta = new Map<number, number>()
  for (const l of lineas) if (l.idProv !== null) cuenta.set(l.idProv, (cuenta.get(l.idProv) ?? 0) + 1)
  return [...cuenta].map(([idProv, n]) => ({ idProv, n }))
}

/** Desplegable de un proveedor (spec §6.6): sus bloques abiertos, el más reciente primero, y «Bloque nuevo». Vacío si
 *  no tiene abiertos: entonces va a uno nuevo sin preguntar. */
export function opcionesDestino(bloques: Map<string, Bloque>, idProv: number): OpcionDestino[] {
  const abiertos = abiertosDe(bloques, idProv)
  if (abiertos.length === 0) return []
  return [
    ...abiertos.map((b) => ({ valor: String(b.idBloque), etiqueta: `Añadir a Bloque ${b.idBloque} · ${fechaCorta(b.fecha)} · ${textoLineas(b.lineas.length)}` })),
    { valor: VALOR_NUEVO, etiqueta: 'Bloque nuevo' },
  ]
}

/** Destino de un proveedor: lo elegido a mano mientras siga siendo una opción; si no, su abierto más reciente; sin
 *  abiertos, bloque nuevo (null). */
export function destinoElegido(bloques: Map<string, Bloque>, idProv: number, elegidos: Record<number, number | null>): number | null {
  const abiertos = abiertosDe(bloques, idProv)
  if (idProv in elegidos) {
    const e = elegidos[idProv]
    if (e === null || abiertos.some((b) => b.idBloque === e)) return e
  }
  return abiertos[0]?.idBloque ?? null
}

/** `destinos` del lote: los proveedores con desplegable, con lo que enseña el resumen. Los demás no viajan: el servidor
 *  les crea un bloque nuevo (o los suma a uno que se haya abierto entretanto, su regla por defecto). Ninguno → null. */
export function destinosDelCuerpo(lineas: LineaCompra[], bloques: Map<string, Bloque>,
                                  elegidos: Record<number, number | null>): { idProv: number; idBloque: number | null }[] | null {
  const destinos = proveedoresDeLineas(lineas)
    .filter(({ idProv }) => abiertosDe(bloques, idProv).length > 0)
    .map(({ idProv }) => ({ idProv, idBloque: destinoElegido(bloques, idProv, elegidos) }))
  return destinos.length > 0 ? destinos : null
}
```

Crear `formulario/ResumenBloques.tsx`:

```tsx
import type { Proveedor } from '@/shared/api/client'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { textoLineas, type Bloque } from '../bloques/reglas'
import { destinoElegido, opcionesDestino, proveedoresDeLineas, VALOR_NUEVO } from './destinos'
import type { LineaCompra } from './lineas'

type Props = {
  lineas: LineaCompra[]
  proveedores: Proveedor[]
  bloques: Map<string, Bloque>
  elegidos: Record<number, number | null>
  onElegir: (idProv: number, idBloque: number | null) => void
  /** «Añadir líneas» de un bloque: destino fijo. */
  fijo: { idBloque: number; nombre: string } | null
  /** Los pedidos aún no han llegado (no se sabe qué bloques hay abiertos). */
  cargando: boolean
}

function Flecha() {
  return <span aria-hidden className="text-texto-sub">→</span>
}

/** Resumen «Bloques» al pie de «Nuevo pedido» (spec §6.6, prototipo): a qué bloque va cada proveedor de las líneas. */
export function ResumenBloques({ lineas, proveedores, bloques, elegidos, onElegir, fijo, cargando }: Props) {
  const nombre = (idProv: number) => proveedores.find((p) => p.idProv === idProv)?.nombre ?? `Proveedor ${idProv}`
  const sinProveedor = lineas.filter((l) => l.idProv === null).length
  let contenido
  if (fijo) {
    contenido = lineas.length > 0
      ? <p className="flex flex-wrap items-center gap-2"><b>{fijo.nombre}</b> ({textoLineas(lineas.length)}) <Flecha /> <b>Bloque {fijo.idBloque}</b> <span className="text-azul-gris">(fijado)</span></p>
      : <p className="text-azul-gris">Añade líneas para ver su destino.</p>
  } else if (lineas.length === 0) {
    contenido = <p className="text-azul-gris">Añade líneas: aquí verás a qué bloque va cada proveedor.</p>
  } else {
    contenido = (
      <>
        {proveedoresDeLineas(lineas).map(({ idProv, n }) => {
          const opciones = opcionesDestino(bloques, idProv)
          const valor = destinoElegido(bloques, idProv, elegidos)
          return (
            <div key={idProv} className="flex flex-wrap items-center gap-2 py-1">
              <b>{nombre(idProv)}</b> ({textoLineas(n)}) <Flecha />
              {cargando ? (
                <span className="text-azul-gris">Cargando bloques…</span>
              ) : opciones.length > 0 ? (
                <ComboNavy valor={valor === null ? VALOR_NUEVO : String(valor)} opciones={opciones}
                  onChange={(v) => onElegir(idProv, v === VALOR_NUEVO ? null : Number(v))} textoVacio="" ancho={300} visibles={8}
                  aria-label={`Bloque de ${nombre(idProv)}`} />
              ) : (
                <span><b>Bloque nuevo</b> <span className="text-azul-gris">(no tiene ninguno abierto)</span></span>
              )}
            </div>
          )
        })}
        {sinProveedor > 0 && <p className="text-azul-gris">{textoLineas(sinProveedor)} sin proveedor todavía</p>}
        <p className="mt-1.5 text-[11px] text-azul-gris">
          Solo se ofrecen bloques abiertos (sin nada confirmado). Para sumar a uno ya confirmado: ⋮ del bloque → «Añadir líneas…».
        </p>
      </>
    )
  }
  return (
    <section aria-label="Bloques" className="rounded-[10px] border border-borde-input bg-superficie px-3 py-2.5 text-[12px] text-azul-medio">
      <h3 className="mb-1.5 font-bold">Bloques</h3>
      {contenido}
    </section>
  )
}
```

En `formulario/lineas.ts`, `cuerpoLoteCompras` gana un último parámetro y lo usa en vez del `destinos: null` de la Task 6:

```ts
export function cuerpoLoteCompras(lineas: LineaCompra[], origen: { urgentes: SolicitudResumen[]; preventivas: SolicitudStock[] } | null, activos: Componente[],
                                  destinos: CuerpoLoteCompras['destinos'] = null): CuerpoLoteCompras {
```

y en el objeto devuelto, `destinos: null,` → `destinos,`.

- [ ] **Step 3: `DialogoLineas` con `pie` y `proveedorFijo`**

En `formulario/DialogoLineas.tsx`:
1. En `Props<L>`, tras `barra?: ReactNode`:

```ts
  /** 0.9.9: resumen «Bloques» de «Nuevo pedido», encima de los botones. */
  pie?: ReactNode
  /** 0.9.9 («Añadir líneas» de un bloque): proveedor fijo de todas las líneas, sin desplegable. */
  proveedorFijo?: string
```

y ambos en la desestructuración de `DialogoLineas`.
2. La celda del proveedor de cada línea pasa a:

```tsx
                    <TableCell className="px-1 py-1">
                      {proveedorFijo !== undefined ? (
                        <span className="inline-block rounded-full bg-nav-switch px-2.5 py-1 text-[12px] font-bold text-azul-medio">{proveedorFijo}</span>
                      ) : (
                        <ComboNavy valor={l.idProv === null ? null : String(l.idProv)} opciones={opciones} onChange={(v) => onCambiar(l.id, { idProv: Number(v) })} textoVacio="" ancho={ANCHO_COMBO_PROVEEDOR} visibles={8} aria-label={`Proveedor línea ${n}`} />
                      )}
                    </TableCell>
```

3. Entre `{info !== null && …}` y `{error !== null && …}`, añadir `{pie}`.

- [ ] **Step 4: `NuevoPedidoDialog`**

Sustituir `formulario/NuevoPedidoDialog.tsx` entero por:

```tsx
import { useMemo, useState } from 'react'
import { StaleDataError } from '@/shared/api/errors'
import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { BotonSecundario } from '@/shared/ui/Botones'
import { CampoAutocompletar } from '@/shared/ui/CampoAutocompletar'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { useProveedoresComponentes } from '../../proveedores/api'
import { useComponentesStock } from '../../stock/api'
import { agruparCompartidos, nombreGrupo } from '../../stock/grupos'
import { useCompras, useGuardarLoteCompras } from '../api'
import { agruparEnBloques, situacion } from '../bloques/reglas'
import { destinosDelCuerpo } from './destinos'
import { DialogoLineas } from './DialogoLineas'
import { mensajeErrorGuardado } from './errores'
import {
  aplicarPrevision, aplicarProveedorATodas, rellenarProveedorVacio, avisoOmitidas, avisoSinPedido, cambiarLinea, cuantasPrevision, cuerpoLoteCompras, lineaCompraVacia, precargaInicial, quitarLinea, siguienteId,
  validarLineasCompra, type LineaCompra,
} from './lineas'
import { ResumenBloques } from './ResumenBloques'

const OPERACION = 'compras:lote'

type Props = { precarga: PrecargaPedido; onCerrar: () => void }

/** "Nuevo pedido" (FormularioCompraController, spec §6): modal en el sitio (P1) con la precarga del store. Un único
 *  POST /api/compras/lote con Idempotency-Key (P5): misma clave mientras el cuerpo no cambie, nueva tras un éxito.
 *  0.9.9: resumen «Bloques» al pie (a qué bloque va cada proveedor) y modo «Añadir líneas» de un bloque (precarga
 *  `bloque`: proveedor y destino fijos). */
export function NuevoPedidoDialog({ precarga, onCerrar }: Props) {
  const componentes = useComponentesStock({ activo: false })
  const { data: proveedores } = useProveedoresComponentes({ activo: false })
  // 0.9.9: los bloques salen de los pedidos (sin sondeo: con el formulario abierto las vistas no refrescan).
  const compras = useCompras({ activo: false })
  const bloques = useMemo(() => agruparEnBloques(compras.data ?? []), [compras.data])
  const fijo = precarga.modo === 'bloque' ? precarga : null
  const activos = useMemo(() => (componentes.data ?? []).filter((c) => c.activo), [componentes.data])
  const proveedoresActivos = useMemo(() => (proveedores ?? []).filter((p) => p.activo), [proveedores])
  const opciones = useMemo(() => agruparCompartidos(activos).map((f) => ({ clave: String(f.idCom), etiqueta: nombreGrupo(f) })), [activos])
  const [lineas, setLineas] = useState<LineaCompra[] | null>(precarga.modo === 'vacio' || fijo ? [] : null)
  const [omitidas, setOmitidas] = useState(0)
  const [error, setError] = useState<string | null>(null)
  // Pedido automático (spec 0.9.6 §4.4): proveedor general y cuántas marcadas hay que pedir. En «Añadir líneas», el del bloque.
  const [idProvGeneral, setIdProvGeneral] = useState<number | null>(fijo ? fijo.idProv : null)
  const [sinPedido, setSinPedido] = useState(0)
  // 0.9.9: destino elegido a mano por proveedor (idBloque o null = bloque nuevo).
  const [elegidos, setElegidos] = useState<Record<number, number | null>>({})
  const nPrevision = useMemo(() => cuantasPrevision(activos), [activos])
  const opcionesProveedor = useMemo(() => proveedoresActivos.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre })), [proveedoresActivos])
  // Una instancia por apertura: el host remonta el diálogo en cada apertura (key), así que no sobrevive a un éxito.
  const [claves] = useState(() => crearClavesIdempotencia())
  const guardar = useGuardarLoteCompras()
  const bloqueFijo = fijo ? bloques.get(String(fijo.idBloque)) : undefined
  const nombreFijo = fijo ? proveedoresActivos.find((p) => p.idProv === fijo.idProv)?.nombre ?? bloqueFijo?.nombreProveedor ?? '' : ''

  // Precarga una sola vez, cuando la lista de componentes termina de cargar (bien o mal): patrón "ajustar el estado
  // durante el render" (CampoAutocompletar, useErrorServidor), sin useEffect + setState.
  // Si la carga falla, `activos` queda vacío y toda solicitud parecería "omitida" (falso: no se ha podido leer la
  // lista, no es que estén desactivados). El diálogo global de error ya avisa del fallo, así que aquí no se cuenta
  // ninguna omitida y no sale la línea de información.
  if (lineas === null && (componentes.isSuccess || componentes.isError)) {
    const inicial = precargaInicial(precarga, activos)
    setLineas(inicial.lineas)
    setOmitidas(componentes.isError ? 0 : inicial.omitidas)
  }

  function cambiar(id: number, cambio: Partial<LineaCompra>) {
    setLineas((ls) => cambiarLinea(ls ?? [], id, cambio))
    setError(null)
    setSinPedido(0)
  }
  function quitar(id: number) {
    setLineas((ls) => quitarLinea(ls ?? [], id))
    setError(null)
    setSinPedido(0)
  }
  function anadir(): number {
    const id = siguienteId(lineas ?? [])
    // Vacía, también si se abrió con "Pedir": calco de anadirLinea() { añadirFila(null); } (FormularioCompraController :496);
    // con el proveedor general si hay uno elegido (0.9.6), que en «Añadir líneas» es el del bloque.
    setLineas((ls) => [...(ls ?? []), { ...lineaCompraVacia(id), idProv: idProvGeneral }])
    setError(null)
    setSinPedido(0)
    return id
  }
  function anadirPrevision() {
    const r = aplicarPrevision(lineas ?? [], activos, idProvGeneral)
    setLineas(r.lineas)
    setSinPedido(r.sinPedido)
    setError(null)
  }
  /** Elegirlo rellena al momento las líneas sin proveedor; las que ya tienen uno solo cambian con «Aplicar a todas». */
  function elegirProveedorGeneral(idProv: number) {
    setIdProvGeneral(idProv)
    setLineas((ls) => rellenarProveedorVacio(ls ?? [], idProv))
    setError(null)
  }
  function aplicarATodas() {
    if (idProvGeneral === null) return
    setLineas((ls) => aplicarProveedorATodas(ls ?? [], idProvGeneral))
    setError(null)
  }

  async function confirmar() {
    if (lineas === null) return
    const fallo = validarLineasCompra(lineas)
    if (fallo !== null) {
      setError(fallo)
      return
    }
    const origen = precarga.modo === 'solicitudes' ? { urgentes: precarga.urgentes, preventivas: precarga.preventivas } : null
    // Sin la lista de pedidos no se sabe qué bloques hay abiertos: null y que el servidor aplique su regla por defecto.
    const destinos = fijo ? [{ idProv: fijo.idProv, idBloque: fijo.idBloque }] : compras.isSuccess ? destinosDelCuerpo(lineas, bloques, elegidos) : null
    const cuerpo = cuerpoLoteCompras(lineas, origen, activos, destinos)
    setError(null)
    try {
      await guardar.mutateAsync({ cuerpo, clave: claves.para(OPERACION, cuerpo) })
      claves.hecha(OPERACION)
      onCerrar()
    } catch (e) {
      // Un 409 de destino (el bloque ya no existe): el resumen tiene que ofrecer los bloques de ahora.
      if (e instanceof StaleDataError) void compras.refetch()
      setError(mensajeErrorGuardado(e))
    }
  }

  const bloqueado = guardar.isPending || lineas === null
  const info = [avisoOmitidas(omitidas), avisoSinPedido(sinPedido)].filter((t) => t !== null).join(' ') || null
  const botonPrevision = <BotonSecundario type="button" disabled={bloqueado || nPrevision === 0} onClick={anadirPrevision}>Añadir previsión ({nPrevision})</BotonSecundario>

  return (
    <DialogoLineas<LineaCompra>
      titulo={fijo ? `Añadir líneas · Bloque ${fijo.idBloque}` : 'Nuevo pedido'}
      primera={{
        cabecera: 'Componente',
        ancho: 175,
        celda: (l, n) => (
          // Fila seleccionada: el campo navy lleva el borde rgba(255,255,255,0.35) del JavaFX (FC :459-473).
          <div className="rounded-full group-data-[state=selected]:ring-1 group-data-[state=selected]:ring-white/35">
            <CampoAutocompletar valor={l.idCom === null ? null : String(l.idCom)} opciones={opciones} onElegir={(c) => cambiar(l.id, { idCom: Number(c) })} placeholder="Escribe componente..." aria-label={`Componente línea ${n}`} />
          </div>
        ),
      }}
      barra={
        fijo ? (
          <div className="flex flex-col gap-1.5">
            <p className="text-[12px] text-azul-gris">
              {nombreFijo}{bloqueFijo ? ` · ${situacion(bloqueFijo)}` : ''} · las líneas nuevas entran como pendientes y el bloque mostrará «Confirmar bloque (n)».
            </p>
            <div className="flex flex-wrap items-center gap-2.5">
              <span className="text-[12px] font-bold text-azul-medio">Proveedor:</span>
              <span className="rounded-full bg-nav-switch px-2.5 py-1 text-[12px] font-bold text-azul-medio">{nombreFijo}</span>
              {botonPrevision}
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2.5">
            <span className="text-[12px] font-bold text-azul-medio">Proveedor:</span>
            <ComboNavy valor={idProvGeneral === null ? null : String(idProvGeneral)} opciones={opcionesProveedor} onChange={(v) => elegirProveedorGeneral(Number(v))} textoVacio="" ancho={160} visibles={8} aria-label="Proveedor general" />
            <BotonSecundario type="button" disabled={bloqueado || idProvGeneral === null || (lineas ?? []).length === 0} onClick={aplicarATodas}>Aplicar a todas</BotonSecundario>
            {botonPrevision}
          </div>
        )
      }
      lineas={lineas ?? []}
      proveedores={proveedoresActivos}
      proveedorFijo={fijo ? nombreFijo : undefined}
      pie={
        <ResumenBloques lineas={lineas ?? []} proveedores={proveedoresActivos} bloques={bloques} elegidos={elegidos}
          onElegir={(idProv, idBloque) => { setElegidos((e) => ({ ...e, [idProv]: idBloque })); setError(null) }}
          fijo={fijo ? { idBloque: fijo.idBloque, nombre: nombreFijo } : null} cargando={compras.isPending} />
      }
      info={info}
      error={error}
      bloqueado={bloqueado}
      enviando={guardar.isPending}
      onCambiar={cambiar}
      onQuitar={quitar}
      onAnadir={anadir}
      onConfirmar={() => void confirmar()}
      onCerrar={onCerrar}
    />
  )
}
```

- [ ] **Step 5: `EditarPedidoDialog`**

Sustituir `formulario/EditarPedidoDialog.tsx` entero por:

```tsx
import { useLayoutEffect, useMemo, useState } from 'react'
import type { CompraComponente } from '@/shared/api/client'
import { formatearNumero } from '@/shared/lib/importes'
import { cn } from '@/shared/lib/utils'
import { Checkbox } from '@/shared/ui/checkbox'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { useProveedoresComponentes } from '../../proveedores/api'
import { DialogoAlmacen } from '../../ui/DialogoAlmacen'
import { useCompras, useEditarCompra } from '../api'
import { agruparEnBloques, bloquesDelProveedor, destinoSugeridoEdicion, fechaCorta, situacion, textoLineas, textoMoverYaPedida } from '../bloques/reglas'
import { useTasa } from '../tasa'
import { VALOR_NUEVO } from './destinos'
import { CLASE_CAMPO, CLASE_ETIQUETA, DIVISAS_EDICION, MSG_PEDIDO_MODIFICADO, textoTotalEdicion, validarEdicion } from './edicion'
import { mensajeErrorGuardado } from './errores'

type Props = { pedido: CompraComponente | null; onCerrar: () => void }

/** "Editar pedido #{id}" de componentes (FormularioCompraEditarController, inventario §12, spec §6). `pedido === null` =
 *  cerrado. La cantidad se precarga con la PEDIDA (P2); el servidor rechaza cambiarla en un recibido (422).
 *  0.9.9: al cambiar el proveedor aparece «Bloque» (la línea pasa a un bloque del proveedor nuevo) y, si la línea ya se
 *  pidió, guardar pide la confirmación «Mover línea ya pedida». */
export function EditarPedidoDialog({ pedido, onCerrar }: Props) {
  const { data: proveedores } = useProveedoresComponentes({ activo: false })
  const activos = useMemo(() => (proveedores ?? []).filter((p) => p.activo), [proveedores])
  const opciones = useMemo(() => activos.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre })), [activos])
  const compras = useCompras({ activo: false })
  const bloques = useMemo(() => agruparEnBloques(compras.data ?? []), [compras.data])
  const [idProv, setIdProv] = useState<number | null>(null)
  const [cantidad, setCantidad] = useState('')
  const [urgente, setUrgente] = useState(false)
  const [precio, setPrecio] = useState('')
  const [divisa, setDivisa] = useState('EUR')
  const [error, setError] = useState<string | null>(null)
  const [destino, setDestino] = useState<number | null>(null)
  const [confirmarMover, setConfirmarMover] = useState(false)
  const editar = useEditarCompra()
  const tasa = useTasa(pedido ? divisa : null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga al abrir con otro pedido (patrón de EditarStockDialog)
    if (pedido) { setIdProv(pedido.idProv); setCantidad(String(pedido.cantidad)); setUrgente(pedido.esUrgente); setPrecio(formatearNumero(pedido.precioUnidadPedido)); setDivisa(pedido.divisa); setError(null); setDestino(null); setConfirmarMover(false) }
  }, [pedido])
  // Proveedor inactivo (o lista aún sin llegar) → combo vacío y "Selecciona un proveedor." al guardar (calco, FCE :70-72).
  const proveedor = idProv !== null && activos.some((p) => p.idProv === idProv) ? idProv : null
  const cambiaProveedor = pedido !== null && proveedor !== null && proveedor !== pedido.idProv
  const nuevo = cambiaProveedor ? activos.find((p) => p.idProv === proveedor) : undefined
  const opcionesBloque = cambiaProveedor && proveedor !== null
    ? [
        ...bloquesDelProveedor(bloques, proveedor).slice(0, 8).map((b) => ({
          valor: String(b.idBloque), etiqueta: `Bloque ${b.idBloque} · ${fechaCorta(b.fecha)} · ${textoLineas(b.lineas.length)} · ${situacion(b)}`,
        })),
        { valor: VALOR_NUEVO, etiqueta: 'Bloque nuevo' },
      ]
    : []

  function elegirProveedor(v: string) {
    const id = Number(v)
    setIdProv(id)
    if (pedido && id !== pedido.idProv) setDestino(destinoSugeridoEdicion(pedido, id, bloques))
    setError(null)
  }

  async function guardar(confirmado = false) {
    if (!pedido) return
    const v = validarEdicion({ idProv: proveedor, cantidad, precio })
    if (!v.ok) {
      setError(v.error)
      return
    }
    if (cambiaProveedor && pedido.estado !== 'pendiente' && !confirmado) {
      setConfirmarMover(true)
      return
    }
    setError(null)
    try {
      await editar.mutateAsync({
        idCompra: pedido.idCompra,
        cuerpo: {
          idProv: v.valor.idProv, cantidad: v.valor.cantidad, esUrgente: urgente, precioUnidad: v.valor.precioUnidad, divisa, updatedAt: pedido.updatedAt,
          destino: cambiaProveedor ? { idBloque: destino } : null,
        },
      })
      onCerrar()
    } catch (e) {
      setError(mensajeErrorGuardado(e, { staleData: MSG_PEDIDO_MODIFICADO }))
    }
  }

  return (
    <>
      <DialogoAlmacen abierto={pedido !== null} ancho={520} titulo={pedido ? `Editar pedido #${pedido.idCompra}` : ''} error={error} textoAccion="Guardar" enviando={editar.isPending} onConfirmar={() => void guardar()} onCancelar={onCerrar}>
        <div className="grid grid-cols-[130px_1fr] items-center gap-x-3.5 gap-y-3">
          <span className={CLASE_ETIQUETA}>Componente:</span>
          <span className="text-[12px] font-bold text-azul-medio">{pedido?.tipoComponente}</span>
          <span className={CLASE_ETIQUETA}>Proveedor:</span>
          <ComboNavy valor={proveedor === null ? null : String(proveedor)} opciones={opciones} onChange={elegirProveedor} textoVacio="" ancho={320} visibles={8} aria-label="Proveedor" />
          {cambiaProveedor && (
            <>
              <span className={CLASE_ETIQUETA}>Bloque:</span>
              <ComboNavy valor={destino === null ? VALOR_NUEVO : String(destino)} opciones={opcionesBloque} onChange={(v) => setDestino(v === VALOR_NUEVO ? null : Number(v))} textoVacio="" ancho={320} visibles={8} aria-label="Bloque" />
              <p className="col-span-2 rounded-lg bg-seleccion-suave px-2.5 py-2 text-[12px] text-azul-medio">
                Al cambiar el proveedor, la línea sale del Bloque {pedido?.idBloque ?? '—'} y pasa a un bloque de {nuevo?.nombre}.
                {nuevo && nuevo.divisa !== divisa ? ` Ojo: ${nuevo.nombre} trabaja en ${nuevo.divisa}; revisa el precio.` : ''}
              </p>
            </>
          )}
          <Label htmlFor="editar-pedido-cantidad" className={CLASE_ETIQUETA}>Cantidad:</Label>
          <Input id="editar-pedido-cantidad" value={cantidad} placeholder="Ej. 10" onChange={(e) => { setCantidad(e.target.value); setError(null) }} className={CLASE_CAMPO} />
          <Label htmlFor="editar-pedido-urgente" className={CLASE_ETIQUETA}>Urgente:</Label>
          <Checkbox id="editar-pedido-urgente" checked={urgente} onCheckedChange={(v) => { setUrgente(v === true); setError(null) }} className="justify-self-start bg-superficie" />
          <Label htmlFor="editar-pedido-precio" className={CLASE_ETIQUETA}>Precio unidad:</Label>
          <div className="flex items-center gap-2">
            <Input id="editar-pedido-precio" value={precio} placeholder="0.00" onChange={(e) => { setPrecio(e.target.value); setError(null) }} className={cn(CLASE_CAMPO, 'flex-1')} />
            <ComboNavy valor={divisa} opciones={DIVISAS_EDICION} onChange={(d) => { setDivisa(d); setError(null) }} textoVacio={divisa} ancho={84} aria-label="Divisa" />
          </div>
          <span className={CLASE_ETIQUETA}>Total EUR:</span>
          <span data-testid="total-eur" className="whitespace-pre text-[13px] font-bold text-azul-medio">{textoTotalEdicion(precio, cantidad, divisa, tasa)}</span>
        </div>
      </DialogoAlmacen>
      <ConfirmDialog abierto={confirmarMover} titulo="Mover línea ya pedida" descripcion={pedido ? textoMoverYaPedida(pedido, destino) : ''}
        textoAccion="Guardar y mover" onConfirmar={() => { setConfirmarMover(false); void guardar(true) }} onCancelar={() => setConfirmarMover(false)} />
    </>
  )
}
```

(Si el test «precarga: … etiquetas en orden» de `EditarPedidoDialog.test.tsx` cuenta las etiquetas con `:`, no cambia: «Bloque:» solo aparece al cambiar el proveedor.)

- [ ] **Step 6: Ejecutar**

Run: `npx vitest run src/modules/almacen/pedidos && npx tsc -b && npm run lint`
Expected: todo en verde.
Run: `npx vitest run`
Expected: todo en verde (los tests de Stock, campana y layout que abren estos diálogos usan el `GET /api/compras` vacío por defecto de la Task 6).

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add -A src
git commit -m "feat: nuevo pedido con resumen de bloques, anadir lineas a un bloque y editar con bloque destino"
```

---

### Task 13: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json` y `gestion-reparaciones-web/package-lock.json` (versión)
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.9]`)
- Modify: `gestion-reparaciones-web/docs/paridad/pedidos.md`
- Create: `docs/novedades/NOVEDADES-v0.9.9.md` (raíz, clon normal)
- Modify: `C:\Users\dev\Documents\Apuntes\plan-futuro.md` (fuera de git)

- [ ] **Step 1: Versión**

En `package.json`, `"version": "0.9.8"` → `"version": "0.9.9"`. En `package-lock.json`, las dos primeras apariciones de `"version": "0.9.8"` (raíz y `packages[""]`) → `"0.9.9"`.

Run: `grep -n '"version": "0.9.9"' package.json package-lock.json`
Expected: 3 líneas.

- [ ] **Step 2: CHANGELOG**

En `CHANGELOG.md`, antes de `## [0.9.8]`:

```markdown
## [0.9.9] - 2026-10-XX — Pedidos por bloques

- **Bloques de pedido.** Cada pedido de componentes pertenece a un **bloque**: lo que se le pide de una vez a un proveedor. Al guardar «Nuevo pedido», las líneas de cada proveedor se suman a su bloque abierto (sin nada confirmado) o van a uno nuevo; el resumen «Bloques» al pie del formulario enseña y deja elegir el destino.
- **Pedidos agrupada por bloques** (Componentes): cabeceras plegables con número, proveedor, fecha, líneas, total, ⚠, resumen de estados y nota. Los bloques con algo por hacer salen desplegados; los terminados, plegados; con filtros, solo las líneas que cumplen («mostrando k de n»). El interruptor **«Agrupar por bloque»** vuelve a la vista plana de siempre (con columna «Bloque»), y el navegador lo recuerda.
- **Menú del bloque** (⋮ o clic derecho en la cabecera, supertécnicos): Confirmar bloque, Recibir todo, Añadir líneas…, Separar líneas…, Juntar con…, Nota, Cancelar bloque y Borrar bloque. Cada acción se aplica a las líneas que pueden recibirla y la confirmación lista lo que cambia y lo que no. Si otra persona cambia el bloque a la vez, no se aplica nada y se avisa.
- **Mover a bloque** en el menú de cada línea (también en las canceladas): una pendiente se mueve sin preguntar; una ya pedida pide confirmación y queda en el registro. «Editar» con otro proveedor lleva la línea a un bloque de ese proveedor.
- CSV de pedidos con las columnas «Bloque» y «Nota del bloque». Los pedidos anteriores se agrupan en bloques «antiguos» (mismo proveedor y día).
- Requiere el servidor 0.9.9 y las migraciones `migracion-bloques-compra.sql` y `reconstruir-bloques-compra.sql`. Sin cambios en nginx.
```

(`2026-10-XX` se sustituye por la fecha real del despliegue en la Task 15.)

- [ ] **Step 3: Ficha de paridad y novedades**

En `docs/paridad/pedidos.md`, al final:

```markdown
## 0.9.9 — Bloques (sin equivalente en el JavaFX)

Diferencias deliberadas, decididas en la spec `2026-10-09-v099-bloques-pedidos-design.md` (raíz):

- Componentes agrupada por bloques por defecto, con cabeceras plegables e interruptor «Agrupar por bloque» a la vista
  plana (que gana la columna «Bloque» tras «ID»; en la agrupada, «Proveedor» va en la cabecera).
- Menú del bloque (⋮ y clic derecho en la cabecera) y entrada «Mover a bloque ▸» en el menú de cada línea; los
  cancelados, que no tenían menú, tienen solo esa entrada.
- «Nuevo pedido» con el resumen «Bloques» al pie y modo «Añadir líneas» de un bloque; «Editar pedido» con el campo
  «Bloque» al cambiar el proveedor.
- CSV de componentes con «Bloque» y «Nota del bloque» tras «ID».
```

Crear `docs/novedades/NOVEDADES-v0.9.9.md` (en la raíz):

```markdown
# 🎉 Novedades — Versión 0.9.9 (web)

Los pedidos se agrupan en **bloques**: lo que le pides de una vez a un proveedor.

---

## 📦 Qué es un bloque

Un bloque reúne las piezas que se piden **juntas a un mismo proveedor** (normalmente, el pedido del día). Al guardar
«Nuevo pedido», las líneas de cada proveedor se **suman a su bloque abierto** (el que aún no has confirmado) o van a uno
**nuevo**. Al pie del formulario, el recuadro **«Bloques»** te dice a dónde va cada proveedor y te deja elegir.

Los pedidos anteriores a esta versión se han agrupado solos por proveedor y día (llevan la etiqueta «antiguo»).

---

## 🗂️ La pestaña Pedidos

- Cada bloque es una **cabecera plegable** con su número, proveedor, fecha, líneas, total y el resumen de estados.
- Los bloques con algo pendiente salen **desplegados**; los terminados, **plegados**.
- Al filtrar o buscar, solo ves las líneas que cumplen («mostrando 2 de 4»).
- **«Agrupar por bloque»** te devuelve a la lista de siempre cuando quieras.

---

## ⚙️ Qué puedes hacer con un bloque

Con **⋮** o con clic derecho en su cabecera: **Confirmar bloque**, **Recibir todo**, **Añadir líneas…**, **Separar
líneas…**, **Juntar con…**, **Nota** (nº de pedido, seguimiento…), **Cancelar bloque** y **Borrar bloque**. Antes de
aplicar, te enseña qué líneas cambian y cuáles no, y por qué.

Para pasar una línea a otro bloque: clic derecho → **«Mover a bloque»**. Si ya se había pedido, te lo pregunta.
```

En `Apuntes/plan-futuro.md` (fuera de git), sección de pedidos (§5): marcar la 0.9.9 con `[x]` cuando esté en producción
(Task 15) y añadir como `[ ]` el backlog de la spec §9: Auto con bloques (proveedor habitual por pieza y «Ya que pides a
X…» como versión aparte tras unas semanas de uso), plazo real por proveedor (fecha del bloque → llegadas), gastos de
envío por bloque, bloques en «Otros», arrastrar filas entre bloques (con alternativa sin arrastrar) y selección múltiple
en la tabla.

- [ ] **Step 4: Suites completas**

Run (worktree del servidor): `mvn -q test`
Expected: todo en verde.
Run (worktree de la web): `npx tsc -b && npm run lint && npx vitest run && npm run build`
Expected: todo en verde y `dist/` generado.

- [ ] **Step 5: Commits**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add package.json package-lock.json CHANGELOG.md docs/paridad/pedidos.md && git commit -m "chore: version 0.9.9 con los pedidos por bloques"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.9.md && git commit -m "docs: novedades de la 0.9.9"
```

- [ ] **Step 6: Entrega (con OK del usuario en cada paso)**

Pedir OK antes de cada uno:
1. Merge `--no-ff` de `feature/bloques-pedidos` a `main` en servidor y web desde los clones normales
   (`merge: pedidos por bloques (0.9.9)`), solo con cada clon normal en `main` y limpio. Después `git worktree remove`
   de los dos worktrees y borrar las ramas.
2. Push de `main` en los dos repos.
3. Commit de gitlinks en la raíz (`chore: gitlinks servidor y web tras la 0.9.9 (pedidos por bloques)`).

---

### Task 14: Preproducción

Requisito: la 0.9.8 completa ya desplegada en preproducción.

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\preprod\v099-preprod.md` (guion, mismo esquema que `v098-preprod.md`)
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_preprod.md` (§Registro de sesiones)

- [ ] **Step 1: Escribir el guion `v099-preprod.md`**

Con estos bloques (una sola sesión `ssh preprod`; ante cualquier `ERROR` o cifra distinta, PARAR y pegar la salida):
1. **Estado:** clones en `main` y su último commit, contenedores `Up`,
   `SELECT COUNT(*) AS tabla_ya FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Bloque_compra';`
   = **0**, `SELECT MAX(UPDATED_AT) AS ultima_modificacion FROM Compra_componente;` (se apunta) y la consulta de la spec
   §4 (grupos proveedor + día) para ver de antemano los bloques que saldrán.
2. **Código, migración, reconstrucción y arranque:** `git -C … pull --ff-only` de servidor y web;
   `docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < gestion-reparaciones-servidor/sql/migracion-bloques-compra.sql`;
   la misma línea con `reconstruir-bloques-compra.sql` (su comprobación: `lineas_sin_bloque` 0, `bloques_vacios` 0,
   `lineas_de_otro_proveedor` 0, `reconstruidos` = nº de grupos de la consulta del bloque 1, `ultima_modificacion` igual
   que la apuntada); `docker compose up -d --build`; `Started App` sin `ERROR`/`Exception`; y **otra vez**
   `reconstruir-bloques-compra.sql` (en preprod, `lineas_sin_bloque` 0 y nada nuevo).
3. **Copias:** `/usr/local/sbin/backup-erp.sh` y `tail -n 1 /var/log/backup-erp.log` con `OK` y **24** tablas.

- [ ] **Step 2: Ejecutar (usuario) y revisar (Claude)**

Expected: las cifras del Step 1. Si `reconstruidos` no cuadra con la consulta previa, PARAR (la reconstrucción se
repite sin riesgo, pero hay que entender por qué).

- [ ] **Step 3: Pruebas a mano en la web de preprod (usuario, con Claude)**

Con las cuentas de prueba de `~/.env.e2e`:
- `/version.json` = 0.9.9.
- Pedidos agrupada: bloques «antiguos» con su proveedor y día; los terminados plegados; «Agrupar por bloque» a la plana
  y vuelta.
- «Nuevo pedido» de prueba con dos proveedores: el resumen «Bloques» (sumar al abierto / bloque nuevo); guardar y ver los
  bloques.
- Mover una línea pendiente y otra ya pedida (confirmación); «Recibir todo» en un bloque de prueba (stock sube) y
  «Revertir a En camino» en su fila para dejarlo como estaba; «Separar», «Juntar» y la nota; «Borrar bloque» del bloque
  de prueba (todo pendiente).
- CSV con «Bloque» y «Nota del bloque».
- Smoke: `E2E_BASE_URL=<url de preprod> npx playwright test tests/e2e/pedidos.spec.ts` (de uno en uno; ≥60 s entre
  ficheros por el límite de inicios de sesión).
- Limpieza: borrar los pedidos de prueba que queden (un bloque vacío desaparece solo).

- [ ] **Step 4: Registrar**

Entrada nueva en `Apuntes/despliegue_preprod.md` §Registro de sesiones: commits desplegados, cifras de la reconstrucción
(antes/después), pruebas y limpieza.

---

### Task 15: Producción

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\prod-v099.md` (guion)
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_vdc_produccion.md` (registro de sesiones)
- Modify: memoria `project_produccion_vdc.md` y `project_v099_bloques_pedidos.md` (y sus líneas de `MEMORY.md`)

- [ ] **Step 1: Escribir el guion `prod-v099.md`**

Los mismos bloques que en preprod (Task 14, Step 1) con `ssh prod`, más, al principio, la copia a mano
(`/usr/local/sbin/backup-erp.sh` y `tail -n 1 /var/log/backup-erp.log` con `OK`). **Momento:** según la norma de
despliegue en horario (migración que solo añade, web 0.9.8 compatible con el servidor 0.9.9, vuelta atrás simple); se
decide ese día con lo visto en preprod. **Vuelta atrás:** servidor y web a los commits de la 0.9.8
(`git -C … checkout <commit>` + `docker compose up -d --build`); la tabla y la columna se quedan (el 0.9.8 no las
nombra); al volver a desplegar la 0.9.9, repetir `reconstruir-bloques-compra.sql`.

- [ ] **Step 2: Ejecutar (usuario) y revisar (Claude)**

Expected: las mismas cifras-regla que en preprod; `reconstruidos` del orden de la consulta previa de producción.

- [ ] **Step 3: Comprobación ligera sin escribir datos**

`/version.json` = 0.9.9; Pedidos agrupada con los bloques «antiguos»; menú ⋮ de un bloque (sin elegir nada); vista
plana con la columna «Bloque»; CSV.

- [ ] **Step 4: Registrar, etiquetar y memoria**

Entrada en `Apuntes/despliegue_vdc_produccion.md` (copia, commits desplegados, cifras de la reconstrucción, vuelta
atrás). Fecha real en la sección `[0.9.9]` del CHANGELOG de la web (commit aparte). Tags `v0.9.9` en servidor y web
**solo cuando el usuario lo diga**. `Apuntes/plan-futuro.md`: la 0.9.9 marcada `[x]`. Memoria: versión en producción y
tablas (24) en `project_produccion_vdc.md`; `project_v099_bloques_pedidos.md` como cerrada.
