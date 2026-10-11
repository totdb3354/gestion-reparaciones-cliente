# 0.9.9 (ampliación) — Piezas que vende cada proveedor y previsión por proveedor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada proveedor tiene su lista de piezas (puesta a mano y rellenada con el historial); «Nuevo pedido» y el menú del bloque proponen la previsión **del proveedor elegido**; «Pedir 60 d» descuenta lo pendiente; Stock enseña «Pendiente»; el buscador de Pedidos encuentra un bloque por su número.

**Architecture:** Tabla `Proveedor_componente` (proveedor, pieza master) con su DAO y un `PUT` que sustituye la lista entera. El listado de Stock (`GET /api/componentes/gestionados`) trae en cada pieza `pendiente` y `proveedores` (solo SUPERTECNICO y ADMIN) y la previsión ya descuenta lo pendiente. La web filtra la previsión por el proveedor con las funciones puras de `formulario/lineas.ts`; el diálogo de Proveedores saca la lista del mismo listado de Stock.

**Tech Stack:** Servidor Spring Boot 3.3 + JdbcTemplate + MariaDB (JUnit 5 + Mockito con JdbcTemplate simulado; MockMvc para roles; springdoc para el contrato). Web React + TypeScript + TanStack Query/Table + Radix (Vitest + Testing Library + MSW; Playwright para E2E).

**Spec:** `docs/superpowers/specs/2026-10-11-v099-catalogo-proveedores-design.md` (raíz). Va **dentro de la 0.9.9**: la de bloques ya está en `main` (servidor `187145e`, web `343c0d4`) y validada en preprod, sin desplegar en producción ni etiquetar.

## Global Constraints

- **Ramas y worktrees:** rama `feature/catalogo-proveedores` creada **desde `main`** en los worktrees ya existentes `C:/Users/dev/Documents/wt/servidor-099` y `C:/Users/dev/Documents/wt/web-099` (hoy en `feature/bloques-pedidos`, ya mergeada). La web de ese worktree ya tiene `node_modules`. No tocar los clones normales (`ProgramaReparaciones/gestion-reparaciones-*`) salvo en el cierre (Task 11) y con OK.
- **Comandos (Git Bash):**
  - Servidor: `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"` y después `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro de `wt/servidor-099`.
  - Web: `npx vitest run <ruta>`, `npx tsc -b`, `npm run lint` dentro de `wt/web-099`. **Un solo proceso de vitest a la vez** (la máquina da timeouts de 5 s con carga).
- **Commits:** en español **sin tildes**, prefijos `feat:` / `fix:` / `test:` / `docs:` / `chore:`, **sin** línea `Co-Authored-By`. Nada de push, merge ni tag sin OK del usuario.
- **Repos públicos:** datos de prueba sintéticos; sin IPs, nombres reales ni secretos.
- **Contrato:** registros anidados de controller = `<Controller sin sufijo><Record>`; todas las propiedades `required`; nulabilidad campo a campo con `@Schema(nullable = true)`. Tras cambiar el contrato: `cp <servidor>/target/openapi.json api/openapi.json && npm run api:types:offline` en la web (sin editar `schema.d.ts` a mano).
- **Textos exactos (UI):** «Piezas que vende…», «Piezas que vende · <proveedor>», «Buscar pieza…», «Marcar las que se ven», «Quitar las que se ven», «Solo las marcadas», «1 pieza marcada» / «N piezas marcadas», «Guardar»; «Añadir previsión de <X> (N)», «Añadir previsión» (deshabilitado, ayuda «Elige antes el proveedor»), «1 pieza marcada no necesita pedido.» / «N piezas marcadas no necesitan pedido.» (los de hoy), «1 pieza en Auto con algo que pedir no está en la lista de ningún proveedor.» / «N piezas en Auto con algo que pedir no están en la lista de ningún proveedor.», «Nada que pedir a <X> según la previsión.»; menú del bloque «Añadir previsión…»; buscador de Pedidos (Componentes) «Buscar componente o nº de bloque…» (en Otros sigue «Buscar componente…»); columna de Stock «Pendiente».
- **Textos exactos (servidor):** 422 «Solo los proveedores de componentes tienen piezas.», 422 «Alguna pieza ya no existe.», 422 «Falta la lista de piezas.», 404 «Proveedor no encontrado.»; registro `EDITAR_PIEZAS_PROVEEDOR` con detalle `ID_PROV: <id>, PIEZAS: +<añadidas> -<quitadas>` (guion ASCII).
- **Registro de subagentes:** `.superpowers/sdd/v099b/` en la raíz (gitignored), como en la 0.9.9.

---

### Task 1: Servidor — tabla, relleno y DAO de las piezas de cada proveedor

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-catalogo-proveedores.sql`
- Create: `gestion-reparaciones-servidor/sql/rellenar-catalogo-proveedores.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (DROP y CREATE)
- Modify: `gestion-reparaciones-servidor/docs/schema.md`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ProveedorComponenteDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ProveedorComponenteDAOTest.java`

**Interfaces:**
- Produces: `ProveedorComponenteDAO` (`@Repository`, constructor `(JdbcTemplate)`) con:
  - `Map<Integer, List<Integer>> proveedoresPorPieza()` — master → ids de proveedor ordenados.
  - `Set<Integer> piezasDe(int idProv)`.
  - `Map<Integer, Integer> mastersDe(Collection<Integer> idsCom)` — id → su master; los que no existen no salen; colección vacía → mapa vacío sin consultar.
  - `void anadir(int idProv, Collection<Integer> idsCom)`, `void quitar(int idProv, Collection<Integer> idsCom)` (lotes).
  - Constantes `SQL_TODAS`, `SQL_DE_PROVEEDOR`, `SQL_INSERTAR`, `SQL_QUITAR`, `SQL_MASTERS` (package-private).

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git status --short && git switch -c feature/catalogo-proveedores main && git log --oneline -1
```
Expected: árbol limpio; `187145e merge: pedidos por bloques (0.9.9)`.

- [ ] **Step 1: Test que falla**

`src/test/java/com/reparaciones/servidor/dao/ProveedorComponenteDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Piezas que vende cada proveedor (spec 2026-10-11 §3): SQL y reparto, con el JdbcTemplate simulado. */
class ProveedorComponenteDAOTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProveedorComponenteDAO dao = new ProveedorComponenteDAO(jdbc);

    /** Da al RowCallbackHandler de la llamada las filas (ID_PROV, ID_COM) indicadas. */
    private static void filas(RowCallbackHandler h, int[][] filas) throws Exception {
        for (int[] f : filas) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_PROV")).thenReturn(f[0]);
            when(rs.getInt("ID_COM")).thenReturn(f[1]);
            when(rs.getInt("ID_MASTER")).thenReturn(f[1]);
            h.processRow(rs);
        }
    }

    @Test void proveedoresPorPiezaAgrupaPorPiezaYOrdena() {
        doAnswer(inv -> { filas(inv.getArgument(1), new int[][]{{2, 5}, {1, 5}, {1, 6}}); return null; })
                .when(jdbc).query(eq(ProveedorComponenteDAO.SQL_TODAS), any(RowCallbackHandler.class));
        Map<Integer, List<Integer>> mapa = dao.proveedoresPorPieza();
        assertEquals(Map.of(5, List.of(1, 2), 6, List.of(1)), mapa);
        assertEquals("SELECT ID_PROV, ID_COM FROM Proveedor_componente", ProveedorComponenteDAO.SQL_TODAS);
    }

    @Test void piezasDeUnProveedor() {
        when(jdbc.queryForList(ProveedorComponenteDAO.SQL_DE_PROVEEDOR, Integer.class, 7)).thenReturn(List.of(5, 6));
        assertEquals(Set.of(5, 6), dao.piezasDe(7));
        assertEquals("SELECT ID_COM FROM Proveedor_componente WHERE ID_PROV = ?", ProveedorComponenteDAO.SQL_DE_PROVEEDOR);
    }

    @Test void mastersDeConsultaConUnaMarcaPorIdYDevuelveSoloLosQueExisten() {
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_COM")).thenReturn(12);
            when(rs.getInt("ID_MASTER")).thenReturn(3);
            h.processRow(rs);
            return null;
        }).when(jdbc).query(eq("SELECT ID_COM, COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER FROM Componente WHERE ID_COM IN (?, ?)"),
                any(RowCallbackHandler.class), eq(12), eq(99));
        assertEquals(Map.of(12, 3), dao.mastersDe(List.of(12, 99)));
    }

    @Test void mastersDeConColeccionVaciaNoConsulta() {
        assertEquals(Map.of(), dao.mastersDe(List.of()));
        verifyNoInteractions(jdbc);
    }

    @Test void anadirYQuitarVanEnLote() {
        dao.anadir(7, List.of(5, 6));
        dao.quitar(7, List.of(8));
        verify(jdbc).batchUpdate(eq("INSERT INTO Proveedor_componente (ID_PROV, ID_COM) VALUES (?, ?)"),
                argThat((List<Object[]> l) -> l.size() == 2 && l.get(0)[0].equals(7) && l.get(0)[1].equals(5) && l.get(1)[1].equals(6)));
        verify(jdbc).batchUpdate(eq("DELETE FROM Proveedor_componente WHERE ID_PROV = ? AND ID_COM = ?"),
                argThat((List<Object[]> l) -> l.size() == 1 && l.get(0)[0].equals(7) && l.get(0)[1].equals(8)));
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `mvn -q test -Dtest=ProveedorComponenteDAOTest`
Expected: FALLA de compilación (`ProveedorComponenteDAO` no existe).

- [ ] **Step 3: DAO**

`src/main/java/com/reparaciones/servidor/dao/ProveedorComponenteDAO.java`:

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.*;

/** Piezas que vende cada proveedor (0.9.9, spec 2026-10-11 §3): una fila por (proveedor, pieza master). La mantiene el
 *  supertécnico desde Proveedores y la usa la previsión por proveedor de «Nuevo pedido». */
@Repository
public class ProveedorComponenteDAO {

    static final String SQL_TODAS = "SELECT ID_PROV, ID_COM FROM Proveedor_componente";
    static final String SQL_DE_PROVEEDOR = "SELECT ID_COM FROM Proveedor_componente WHERE ID_PROV = ?";
    static final String SQL_INSERTAR = "INSERT INTO Proveedor_componente (ID_PROV, ID_COM) VALUES (?, ?)";
    static final String SQL_QUITAR = "DELETE FROM Proveedor_componente WHERE ID_PROV = ? AND ID_COM = ?";
    /** `%s` = una marca `?` por id. */
    static final String SQL_MASTERS = "SELECT ID_COM, COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER FROM Componente WHERE ID_COM IN (%s)";

    private final JdbcTemplate jdbc;

    public ProveedorComponenteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pieza master → ids de los proveedores que la venden, de menor a mayor. */
    public Map<Integer, List<Integer>> proveedoresPorPieza() {
        Map<Integer, List<Integer>> mapa = new HashMap<>();
        jdbc.query(SQL_TODAS, (RowCallbackHandler) rs ->
                mapa.computeIfAbsent(rs.getInt("ID_COM"), k -> new ArrayList<>()).add(rs.getInt("ID_PROV")));
        mapa.values().forEach(Collections::sort);
        return mapa;
    }

    public Set<Integer> piezasDe(int idProv) {
        return new HashSet<>(jdbc.queryForList(SQL_DE_PROVEEDOR, Integer.class, idProv));
    }

    /** Master de cada id que existe; los que no existen no salen en el mapa. */
    public Map<Integer, Integer> mastersDe(Collection<Integer> idsCom) {
        if (idsCom.isEmpty()) return Map.of();
        String marcas = String.join(", ", Collections.nCopies(idsCom.size(), "?"));
        Map<Integer, Integer> mapa = new HashMap<>();
        jdbc.query(String.format(SQL_MASTERS, marcas),
                (RowCallbackHandler) rs -> mapa.put(rs.getInt("ID_COM"), rs.getInt("ID_MASTER")), idsCom.toArray());
        return mapa;
    }

    public void anadir(int idProv, Collection<Integer> idsCom) {
        jdbc.batchUpdate(SQL_INSERTAR, idsCom.stream().map(id -> new Object[]{idProv, id}).toList());
    }

    public void quitar(int idProv, Collection<Integer> idsCom) {
        jdbc.batchUpdate(SQL_QUITAR, idsCom.stream().map(id -> new Object[]{idProv, id}).toList());
    }
}
```

- [ ] **Step 4: Scripts SQL, `crear_bd.sql` y `schema.md`**

`sql/migracion-catalogo-proveedores.sql`:

```sql
-- 0.9.9 (ampliación): piezas que vende cada proveedor (spec docs/superpowers/specs/2026-10-11-v099-catalogo-proveedores-design.md §3).
-- Aditivo: el servidor anterior sigue funcionando sobre este esquema (no nombra la tabla).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor, y justo después rellenar-catalogo-proveedores.sql. Antes de
-- aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

CREATE TABLE Proveedor_componente (
    ID_PROV INT NOT NULL,
    ID_COM  INT NOT NULL,
    PRIMARY KEY (ID_PROV, ID_COM),
    CONSTRAINT fk_provcom_proveedor  FOREIGN KEY (ID_PROV) REFERENCES Proveedor  (ID_PROV) ON DELETE CASCADE,
    CONSTRAINT fk_provcom_componente FOREIGN KEY (ID_COM)  REFERENCES Componente (ID_COM)  ON DELETE CASCADE,
    INDEX idx_provcom_componente (ID_COM)
);

-- Verificación post: SELECT COUNT(*) FROM Proveedor_componente;  -- 0 (la rellena rellenar-catalogo-proveedores.sql)
```

`sql/rellenar-catalogo-proveedores.sql`:

```sql
-- 0.9.9 (ampliación): rellena las piezas de cada proveedor con el historial de pedidos (spec 2026-10-11 §3.2).
-- REPETIBLE: INSERT IGNORE, solo añade las parejas que falten. Se ejecuta tras migracion-catalogo-proveedores.sql y otra
-- vez después de desplegar el servidor (recoge los pedidos hechos entre medias).
USE gestion_reparaciones;

-- Cada pareja (proveedor, pieza) con alguna línea no cancelada. Los pedidos nuevos ya van al master; alguno antiguo
-- puede apuntar a un slave: por eso el COALESCE.
INSERT IGNORE INTO Proveedor_componente (ID_PROV, ID_COM)
SELECT DISTINCT cc.ID_PROV, COALESCE(c.ID_COM_MASTER, cc.ID_COM)
FROM Compra_componente cc
JOIN Componente c ON c.ID_COM = cc.ID_COM
WHERE cc.ESTADO <> 'cancelado';

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS parejas, COUNT(DISTINCT ID_COM) AS piezas, COUNT(DISTINCT ID_PROV) AS proveedores FROM Proveedor_componente;
SELECT COUNT(*) AS apuntan_a_slave FROM Proveedor_componente pc
  JOIN Componente c ON c.ID_COM = pc.ID_COM WHERE c.ID_COM_MASTER IS NOT NULL;                              -- 0
```

`sql/crear_bd.sql`:
- En la lista de `DROP`, justo **antes** de `DROP TABLE IF EXISTS Bloque_compra;`, añadir `DROP TABLE IF EXISTS Proveedor_componente;`.
- Tras el `CREATE TABLE Bloque_compra (…);` (antes de `CREATE TABLE Compra_componente`), añadir el mismo `CREATE TABLE Proveedor_componente (…)` del script de migración (sin `USE`).

`docs/schema.md`: tras la línea de `Bloque_compra`, añadir:
```
- `Proveedor_componente` (0.9.9: piezas que vende cada proveedor, guardadas por el master; la usa la previsión por proveedor de «Nuevo pedido»)
```

- [ ] **Step 5: Ver que pasa**

Run: `mvn -q test -Dtest=ProveedorComponenteDAOTest` y después `mvn -q test`
Expected: verde (800 + 5 tests).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git add sql/migracion-catalogo-proveedores.sql sql/rellenar-catalogo-proveedores.sql sql/crear_bd.sql docs/schema.md src/main/java/com/reparaciones/servidor/dao/ProveedorComponenteDAO.java src/test/java/com/reparaciones/servidor/dao/ProveedorComponenteDAOTest.java
git commit -m "feat: tabla y dao de las piezas que vende cada proveedor, con relleno desde el historial"
```

---

### Task 2: Servidor — «Pendiente» y proveedores de cada pieza en el listado de Stock

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/model/Componente.java`
- Modify: `src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java` (`getAllGestionados`)
- Modify: `src/main/java/com/reparaciones/servidor/service/PrevisionPedidoService.java`
- Modify: `src/main/java/com/reparaciones/servidor/util/PrevisionPedido.java` (solo el comentario de la fórmula)
- Modify: `src/main/java/com/reparaciones/servidor/controller/ComponenteController.java`
- Test: `src/test/java/com/reparaciones/servidor/dao/ComponenteDAOAutoPedidoTest.java`, `service/PrevisionPedidoServiceTest.java`, `controller/ComponenteControllerPrevisionTest.java`; constructor en `ComponenteControllerAutoPedidoTest`, `ComponenteControllerEliminarTest`, `ComponenteControllerValidacionTest`.

**Interfaces:**
- Consumes: `ProveedorComponenteDAO.proveedoresPorPieza()` (Task 1).
- Produces: `Componente.getPendiente()/setPendiente(Integer)` y `getProveedores()/setProveedores(List<Integer>)`, los dos nulables en el contrato; `ComponenteController(ComponenteDAO, LogDAO, PrevisionPedidoService, ProveedorComponenteDAO)`.

- [ ] **Step 1: Tests que fallan**

En `ComponenteDAOAutoPedidoTest`, nuevo test (mismo patrón de captura que el de `AUTO_PEDIDO`):

```java
    @Test void elListadoSumaLoPendienteDelMaster() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<Componente>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sql.capture(), mapper.capture())).thenReturn(List.of());
        dao.getAllGestionados();
        assertTrue(sql.getValue().contains("COALESCE(SUM(CASE WHEN cc.ESTADO = 'pendiente' THEN cc.CANTIDAD ELSE 0 END), 0) AS PENDIENTE"));

        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf(LocalDateTime.of(2026, 10, 11, 10, 0));
        when(rs.getInt("ID_COM")).thenReturn(5);
        when(rs.getString("TIPO")).thenReturn("bat-y");
        when(rs.getTimestamp("FECHA_REGISTRO")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getInt("PENDIENTE")).thenReturn(4);
        when(rs.wasNull()).thenReturn(true);
        assertEquals(4, mapper.getValue().mapRow(rs, 0).getPendiente());
    }
```

En `PrevisionPedidoServiceTest`, nuevo test:

```java
    @Test void loPendienteSeDescuentaComoLoEnCamino() {
        when(parametroDao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        when(componenteDao.getConsumoDiario(HOY.minusDays(90), HOY)).thenReturn(List.of());
        Componente c = comp(1, null, 2, 10, true, 3);   // mínimo 10, stock 2, en camino 3
        c.setPendiente(4);
        servicio.rellenar(List.of(c), HOY);
        assertEquals(1, c.getPedir60());                 // 10 - (2 + 3 + 4)
    }
```

En `ComponenteControllerPrevisionTest`, sustituir el campo `ctl` y añadir dos tests:

```java
    private final ProveedorComponenteDAO catalogo = mock(ProveedorComponenteDAO.class);
    private final ComponenteController ctl = new ComponenteController(dao, mock(LogDAO.class), prevision, catalogo);

    @Test void alTecnicoNoLeLleganNiLoPendienteNiLosProveedores() {
        Componente c = new Componente();
        c.setPendiente(3);
        when(dao.getAllGestionados()).thenReturn(List.of(c));
        ctl.getAllGestionados(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        assertNull(c.getPendiente());
        assertNull(c.getProveedores());
        verifyNoInteractions(catalogo);
    }

    @Test void alSupertecnicoCadaPiezaLlevaLosProveedoresDeSuMaster() {
        Componente master = new Componente(2, "bat-x", null, 0, 1, true, null);
        Componente slave = new Componente(5, "bat-y", null, 0, 1, true, null);
        slave.setIdComMaster(2);
        Componente sinLista = new Componente(7, "flex-x", null, 0, 1, true, null);
        when(dao.getAllGestionados()).thenReturn(List.of(master, slave, sinLista));
        when(catalogo.proveedoresPorPieza()).thenReturn(Map.of(2, List.of(1, 3)));
        ctl.getAllGestionados(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3));
        assertEquals(List.of(1, 3), master.getProveedores());
        assertEquals(List.of(1, 3), slave.getProveedores());
        assertEquals(List.of(), sinLista.getProveedores());
    }
```
(imports nuevos: `ProveedorComponenteDAO`, `java.util.Map`, `static org.junit.jupiter.api.Assertions.*`).

En `ComponenteControllerAutoPedidoTest`, `ComponenteControllerEliminarTest` y `ComponenteControllerValidacionTest`, añadir `mock(com.reparaciones.servidor.dao.ProveedorComponenteDAO.class)` como cuarto argumento del `new ComponenteController(…)`.

- [ ] **Step 2: Ver que falla**

Run: `mvn -q test -Dtest='ComponenteDAOAutoPedidoTest,PrevisionPedidoServiceTest,ComponenteController*Test'`
Expected: FALLA de compilación (`setPendiente`, `getProveedores` y el constructor de cuatro argumentos no existen).

- [ ] **Step 3: Implementación**

`Componente.java` — tras el bloque de `autoPedido`:

```java
    // Cantidad en líneas pendientes del master (0.9.9): solo para SUPERTECNICO y ADMIN, como la previsión.
    @Schema(nullable = true) private Integer pendiente;

    // Proveedores que tienen la pieza (su master) en su lista (0.9.9): solo para SUPERTECNICO y ADMIN.
    @ArraySchema(arraySchema = @Schema(nullable = true), schema = @Schema(implementation = Integer.class))
    private List<Integer> proveedores;
```
y sus accesores al final de la clase:

```java
    public Integer       getPendiente()                        { return pendiente; }
    public void          setPendiente(Integer pendiente)       { this.pendiente = pendiente; }
    public List<Integer> getProveedores()                      { return proveedores; }
    public void          setProveedores(List<Integer> proveedores) { this.proveedores = proveedores; }
```
(imports: `io.swagger.v3.oas.annotations.media.ArraySchema`, `java.util.List`).

`ComponenteDAO.getAllGestionados` — en el SQL, tras la línea `ELSE 0 END), 0) AS EN_CAMINO,`:

```sql
                       COALESCE(SUM(CASE WHEN cc.ESTADO = 'pendiente' THEN cc.CANTIDAD ELSE 0 END), 0) AS PENDIENTE,
```
y en el mapper, tras `c.setEnCamino(rs.getInt("EN_CAMINO"));`:

```java
            c.setPendiente(rs.getInt("PENDIENTE"));
```

`PrevisionPedidoService.rellenar` — la llamada a `calcular` pasa a descontar lo pendiente (el parámetro de `calcular` sigue siendo uno: lo ya pedido o apuntado):

```java
            int pendiente = c.getPendiente() != null ? c.getPendiente() : 0;
            Resultado r = PrevisionPedido.calcular(porMaster.getOrDefault(master, Tramos.CERO), pesos,
                    c.getStockMinimo(), c.getStock(), c.getEnCamino() + pendiente);
```
y el comentario de la clase: «Stock, mínimo, en camino y pendiente ya vienen resueltos al master…».

`PrevisionPedido.java` — en el comentario que documenta la fórmula (si lo hay) o sobre `calcular`, dejar escrito: `enCamino` = en camino + pendiente (0.9.9): lo ya pedido o apuntado no se vuelve a pedir.

`ComponenteController`:

```java
    private final ProveedorComponenteDAO catalogo;

    public ComponenteController(ComponenteDAO dao, LogDAO logDao, PrevisionPedidoService prevision,
                                ProveedorComponenteDAO catalogo) {
        // … asignaciones de hoy …
        this.catalogo = catalogo;
    }
```
y `getAllGestionados`:

```java
    /** Listado de Stock. La previsión de pedidos, la marca del pedido automático, lo pendiente y los proveedores de cada
     *  pieza son información de compras: solo para SUPERTECNICO y ADMIN; a un TECNICO le llegan nulos
     *  (spec 0.9.5 §3.3, spec 0.9.6 §4.2, spec 2026-10-11 §4). */
    @GetMapping("/gestionados")
    public List<Componente> getAllGestionados(@AuthenticationPrincipal UsuarioPrincipal principal) {
        List<Componente> lista = dao.getAllGestionados();
        if (principal != null && ("SUPERTECNICO".equals(principal.getRol()) || "ADMIN".equals(principal.getRol()))) {
            prevision.rellenar(lista, LocalDate.now(MADRID));
            Map<Integer, List<Integer>> porPieza = catalogo.proveedoresPorPieza();
            lista.forEach(c -> c.setProveedores(porPieza.getOrDefault(
                    c.getIdComMaster() != null ? c.getIdComMaster() : c.getIdCom(), List.of())));
        } else {
            lista.forEach(c -> { c.setAutoPedido(null); c.setPendiente(null); });
        }
        return lista;
    }
```

- [ ] **Step 4: Ver que pasa**

Run: `mvn -q test`
Expected: todo verde.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: el listado de stock trae lo pendiente y los proveedores de cada pieza y la prevision descuenta lo pendiente"
```

---

### Task 3: Servidor — `PUT /api/proveedores/{idProv}/componentes`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/service/ProveedorPiezasService.java`
- Create: `src/main/java/com/reparaciones/servidor/controller/ProveedorPiezasController.java`
- Test: `src/test/java/com/reparaciones/servidor/service/ProveedorPiezasServiceTest.java`, `controller/ProveedorPiezasControllerTest.java`, `controller/RolesPiezasProveedorTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Modify: `docs/autorizacion_endpoints.md`, `docs/api_contract.md`

**Interfaces:**
- Consumes: `ProveedorComponenteDAO` (Task 1); `ProveedorDAO.getById(int)` → `Optional<Proveedor>`, `ProveedorDAO.TIPO_COMPONENTES`, `Proveedor.getTipo()`; `LogDAO.insertar(int, String, String)`; `Componente.pendiente/proveedores` (Task 2, para el contrato).
- Produces: `ProveedorPiezasService.reemplazar(int idProv, List<Integer> idsCom)` → `Cambios(int anadidas, int quitadas)`; constantes `MSG_SOLO_COMPONENTES`, `MSG_PIEZA_NO_EXISTE`, `MSG_SIN_LISTA`, `MSG_NO_ENCONTRADO`. Esquema del contrato `ProveedorPiezasRequest { idsCom: integer[] }`.

- [ ] **Step 1: Tests que fallan**

`src/test/java/com/reparaciones/servidor/service/ProveedorPiezasServiceTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProveedorPiezasServiceTest {

    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final ProveedorComponenteDAO catalogo = mock(ProveedorComponenteDAO.class);
    private final ProveedorPiezasService servicio = new ProveedorPiezasService(proveedorDao, catalogo);

    private static Proveedor proveedor(String tipo) {
        Proveedor p = mock(Proveedor.class);
        when(p.getTipo()).thenReturn(tipo);
        return p;
    }

    private void existe(int idProv, String tipo) {
        Proveedor p = proveedor(tipo);
        when(proveedorDao.getById(idProv)).thenReturn(Optional.of(p));
    }

    private static int estado(ResponseStatusException e) { return e.getStatusCode().value(); }

    @Test void sustituyeLaListaAnadiendoYQuitandoSoloLoQueCambia() {
        existe(7, "COMPONENTES");
        when(catalogo.mastersDe(any())).thenReturn(Map.of(5, 5, 12, 3));   // 12 es un slave de 3
        when(catalogo.piezasDe(7)).thenReturn(Set.of(5, 8));
        ProveedorPiezasService.Cambios c = servicio.reemplazar(7, List.of(5, 12, 12));
        assertEquals(new ProveedorPiezasService.Cambios(1, 1), c);
        verify(catalogo).anadir(7, List.of(3));
        verify(catalogo).quitar(7, List.of(8));
        verify(catalogo).mastersDe(new LinkedHashSet<>(List.of(5, 12)));
    }

    @Test void sinCambiosNoEscribe() {
        existe(7, "COMPONENTES");
        when(catalogo.mastersDe(any())).thenReturn(Map.of(5, 5));
        when(catalogo.piezasDe(7)).thenReturn(Set.of(5));
        assertEquals(new ProveedorPiezasService.Cambios(0, 0), servicio.reemplazar(7, List.of(5)));
        verify(catalogo, never()).anadir(anyInt(), any());
        verify(catalogo, never()).quitar(anyInt(), any());
    }

    @Test void listaVaciaLasQuitaTodas() {
        existe(7, "COMPONENTES");
        when(catalogo.piezasDe(7)).thenReturn(Set.of(5, 8));
        assertEquals(new ProveedorPiezasService.Cambios(0, 2), servicio.reemplazar(7, List.of()));
        verify(catalogo).quitar(7, List.of(5, 8));
    }

    @Test void proveedorInexistenteEs404() {
        when(proveedorDao.getById(99)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(99, List.of(5)));
        assertEquals(404, estado(e));
        assertEquals(ProveedorPiezasService.MSG_NO_ENCONTRADO, e.getReason());
    }

    @Test void proveedorDeOtrosEs422() {
        existe(7, "OTROS");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, List.of(5)));
        assertEquals(422, estado(e));
        assertEquals("Solo los proveedores de componentes tienen piezas.", e.getReason());
        verifyNoInteractions(catalogo);
    }

    @Test void sinListaEs422() {
        existe(7, "COMPONENTES");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, null));
        assertEquals(422, estado(e));
        assertEquals("Falta la lista de piezas.", e.getReason());
    }

    @Test void unaPiezaQueNoExisteEs422YNoEscribe() {
        existe(7, "COMPONENTES");
        when(catalogo.mastersDe(any())).thenReturn(Map.of(5, 5));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, List.of(5, 99)));
        assertEquals(422, estado(e));
        assertEquals("Alguna pieza ya no existe.", e.getReason());
        verify(catalogo, never()).anadir(anyInt(), any());
        verify(catalogo, never()).quitar(anyInt(), any());
    }

    @Test void unNuloEnLaListaEs422() {
        existe(7, "COMPONENTES");
        List<Integer> conNulo = new ArrayList<>(Arrays.asList(5, null));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, conNulo));
        assertEquals(422, estado(e));
        assertEquals("Alguna pieza ya no existe.", e.getReason());
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/ProveedorPiezasControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProveedorPiezasControllerTest {

    private final ProveedorPiezasService servicio = mock(ProveedorPiezasService.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorPiezasController ctl = new ProveedorPiezasController(servicio, logDao);
    private final UsuarioPrincipal yo = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    @Test void registraCuantasPiezasAnadeYQuita() {
        when(servicio.reemplazar(5, List.of(1, 2))).thenReturn(new ProveedorPiezasService.Cambios(2, 1));
        ctl.reemplazar(5, new ProveedorPiezasController.Request(List.of(1, 2)), yo);
        verify(logDao).insertar(7, "EDITAR_PIEZAS_PROVEEDOR", "ID_PROV: 5, PIEZAS: +2 -1");
    }

    @Test void unFalloNoRegistraNada() {
        when(servicio.reemplazar(anyInt(), any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "x"));
        assertThrows(ResponseStatusException.class, () -> ctl.reemplazar(5, new ProveedorPiezasController.Request(List.of(1)), yo));
        verifyNoInteractions(logDao);
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/RolesPiezasProveedorTest.java` — misma cabecera de anotaciones que `RolesBloquesCompraTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(UsuariosOperativosTestConfig.class)`, el mismo `@TestPropertySource`):

```java
class RolesPiezasProveedorTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ProveedorPiezasService servicio;
    @MockBean LogDAO logDao;

    private String token(String rol, Integer idTec) {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "u", "", rol, idTec));
    }

    private ResultActions put(String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/proveedores/5/componentes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idsCom\":[1,2]}"));
    }

    @Test void tecnicoYAdminReciben403() throws Exception {
        put(token("TECNICO", 4)).andExpect(status().isForbidden());
        put(token("ADMIN", null)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test void elSupertecnicoGuardaY204() throws Exception {
        when(servicio.reemplazar(anyInt(), any())).thenReturn(new ProveedorPiezasService.Cambios(2, 0));
        put(token("SUPERTECNICO", 3)).andExpect(status().isNoContent());
        verify(servicio).reemplazar(5, List.of(1, 2));
    }
}
```
(imports como en `RolesBloquesCompraTest` más `MockMvcRequestBuilders`).

En `OpenApiContractTest`, método nuevo tras `elContratoPublicaLosBloquesDePedidos` (mismos ayudantes `refDelCuerpo`, `assertNullable`, `assertNoNullable`):

```java
    /** 0.9.9 (ampliación): piezas de cada proveedor y lo pendiente en el listado de Stock (spec 2026-10-11 §4). */
    @Test void elContratoPublicaLasPiezasDeCadaProveedor() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        assertTrue(paths.path("/api/proveedores/{idProv}/componentes").has("put"), "falta PUT /api/proveedores/{idProv}/componentes");
        assertTrue(refDelCuerpo(paths, "/api/proveedores/{idProv}/componentes", "put").endsWith("/ProveedorPiezasRequest"));
        assertNoNullable(esquemas, "ProveedorPiezasRequest", "idsCom");
        assertNullable(esquemas, "Componente", "pendiente", "proveedores");
        JsonNode items = esquemas.path("Componente").path("properties").path("proveedores").path("items");
        assertEquals("integer", items.path("type").asText());
        assertFalse(items.path("nullable").asBoolean(), "los ids de proveedor no son nulos");
    }
```

- [ ] **Step 2: Ver que falla**

Run: `mvn -q test -Dtest='ProveedorPiezas*Test,RolesPiezasProveedorTest,OpenApiContractTest'`
Expected: FALLA de compilación (`ProveedorPiezasService` y `ProveedorPiezasController` no existen).

- [ ] **Step 3: Servicio**

`src/main/java/com/reparaciones/servidor/service/ProveedorPiezasService.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Sustituye las piezas que vende un proveedor (spec 2026-10-11 §4.3): todo o nada, guardando el master de cada pieza. */
@Service
public class ProveedorPiezasService {

    public static final String MSG_NO_ENCONTRADO = "Proveedor no encontrado.";
    public static final String MSG_SOLO_COMPONENTES = "Solo los proveedores de componentes tienen piezas.";
    public static final String MSG_PIEZA_NO_EXISTE = "Alguna pieza ya no existe.";
    public static final String MSG_SIN_LISTA = "Falta la lista de piezas.";

    public record Cambios(int anadidas, int quitadas) {}

    private final ProveedorDAO proveedorDao;
    private final ProveedorComponenteDAO catalogo;

    public ProveedorPiezasService(ProveedorDAO proveedorDao, ProveedorComponenteDAO catalogo) {
        this.proveedorDao = proveedorDao;
        this.catalogo = catalogo;
    }

    @Transactional
    public Cambios reemplazar(int idProv, List<Integer> idsCom) {
        Proveedor p = proveedorDao.getById(idProv)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_ENCONTRADO));
        if (!ProveedorDAO.TIPO_COMPONENTES.equals(p.getTipo())) throw regla(MSG_SOLO_COMPONENTES);
        if (idsCom == null) throw regla(MSG_SIN_LISTA);
        // Sin contains(null): en una lista inmutable (List.of) lanza NullPointerException.
        if (idsCom.stream().anyMatch(Objects::isNull)) throw regla(MSG_PIEZA_NO_EXISTE);
        Set<Integer> distintos = new LinkedHashSet<>(idsCom);
        Map<Integer, Integer> masters = catalogo.mastersDe(distintos);
        if (masters.size() != distintos.size()) throw regla(MSG_PIEZA_NO_EXISTE);
        Set<Integer> nuevas = new TreeSet<>(masters.values());
        Set<Integer> actuales = catalogo.piezasDe(idProv);
        List<Integer> anadir = nuevas.stream().filter(id -> !actuales.contains(id)).toList();
        List<Integer> quitar = actuales.stream().filter(id -> !nuevas.contains(id)).sorted().toList();
        if (!quitar.isEmpty()) catalogo.quitar(idProv, quitar);
        if (!anadir.isEmpty()) catalogo.anadir(idProv, anadir);
        return new Cambios(anadir.size(), quitar.size());
    }

    private static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
```

- [ ] **Step 4: Controller**

`src/main/java/com/reparaciones/servidor/controller/ProveedorPiezasController.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Piezas que vende un proveedor (0.9.9, spec 2026-10-11 §4.3). El registro va tras la transacción del servicio. */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorPiezasController {

    private final ProveedorPiezasService servicio;
    private final LogDAO logDao;

    public ProveedorPiezasController(ProveedorPiezasService servicio, LogDAO logDao) {
        this.servicio = servicio;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}/componentes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reemplazar(@PathVariable int idProv, @RequestBody Request req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorPiezasService.Cambios c = servicio.reemplazar(idProv, req.idsCom());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PIEZAS_PROVEEDOR",
                "ID_PROV: " + idProv + ", PIEZAS: +" + c.anadidas() + " -" + c.quitadas());
    }

    record Request(List<Integer> idsCom) {}
}
```

- [ ] **Step 5: Documentación del servidor**

- `docs/autorizacion_endpoints.md`: en la tabla o lista de escrituras de proveedores, añadir `PUT /api/proveedores/{idProv}/componentes` — SUPERTECNICO (piezas que vende; 0.9.9). En lecturas, una nota: `GET /api/componentes/gestionados` trae `pendiente` y `proveedores` solo para SUPERTECNICO y ADMIN.
- `docs/api_contract.md`: entrada del `PUT` (cuerpo `{ "idsCom": [int] }`, sustituye la lista entera, slaves → master, 204; 404 «Proveedor no encontrado.»; 422 «Solo los proveedores de componentes tienen piezas.», «Falta la lista de piezas.», «Alguna pieza ya no existe.») y, en `Componente`, los campos `pendiente` y `proveedores` (nulos para TECNICO). Mismo estilo que las entradas de bloques.

- [ ] **Step 6: Ver que pasa y comprobar el contrato generado**

Run: `mvn -q test`
Expected: todo verde; `target/openapi.json` con `/api/proveedores/{idProv}/componentes` y `ProveedorPiezasRequest`.

- [ ] **Step 7: Commit**

```bash
git add src docs
git commit -m "feat: put de las piezas que vende cada proveedor con registro y contrato"
```

---

### Task 4: Web — contrato, tipos, datos de prueba y hook

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `src/shared/api/schema.d.ts` (generados)
- Modify: los literales de `Componente` de los tests (los que `tsc` marque)
- Modify: `src/modules/almacen/proveedores/api.ts`
- Test: `src/modules/almacen/proveedores/api.test.tsx` (nuevo si no existe; si existe, añadir el caso)

**Interfaces:**
- Consumes: contrato de las Tasks 2-3.
- Produces: `Componente.pendiente: number | null`, `Componente.proveedores: number[] | null`; `useGuardarPiezasProveedor(): UseMutationResult<unknown, unknown, { idProv: number; idsCom: number[] }>` (silencia el error global; al terminar invalida las consultas `['componentes']`).

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git status --short && git switch -c feature/catalogo-proveedores main && git log --oneline -1
```
Expected: árbol limpio; `343c0d4 test: la e2e de bloques lee el stock…`.

- [ ] **Step 1: Contrato y tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-099/target/openapi.json api/openapi.json && npm run api:types:offline
grep -n "pendiente\|proveedores\|ProveedorPiezasRequest" src/shared/api/schema.d.ts | head
```
Expected: `pendiente: number | null`, `proveedores: number[] | null` en `Componente` y el esquema `ProveedorPiezasRequest`.

- [ ] **Step 2: Datos de prueba**

Run: `npx tsc -b`
Expected: errores en los literales de `Componente` sin `pendiente`/`proveedores`. En cada uno (los objetos `base` de `pedidos/formulario/datosPrueba.ts`, `stock/*.test.ts(x)`, `taller/test/fabrica.ts`, etc.), añadir `pendiente: null, proveedores: null` junto a `autoPedido`. Repetir `npx tsc -b` hasta que no quede ninguno.

- [ ] **Step 3: Test del hook (falla)**

En `src/modules/almacen/proveedores/api.test.tsx` (crear si no existe, con el patrón de `renderHook` + `envoltorio` de otros `api.test.tsx` del módulo, p. ej. `stock/api.test.tsx`):

```tsx
it('useGuardarPiezasProveedor manda la lista entera con PUT', async () => {
  let cuerpo: unknown = null
  server.use(http.put('*/api/proveedores/:id/componentes', async ({ request, params }) => {
    expect(params.id).toBe('5')
    cuerpo = await request.json()
    return new HttpResponse(null, { status: 204 })
  }))
  const { result } = renderHook(() => useGuardarPiezasProveedor(), { wrapper: envoltorio })
  await act(() => result.current.mutateAsync({ idProv: 5, idsCom: [1, 2] }))
  expect(cuerpo).toEqual({ idsCom: [1, 2] })
})
```

Run: `npx vitest run src/modules/almacen/proveedores/api.test.tsx`
Expected: FALLA (`useGuardarPiezasProveedor` no existe).

- [ ] **Step 4: Hook**

En `src/modules/almacen/proveedores/api.ts`:

```ts
/** 0.9.9 (spec 2026-10-11 §5.1): sustituye las piezas que vende el proveedor. Su 422 lo pinta el diálogo: silencia el
 *  global. La lista llega en `proveedores` de cada pieza del listado de Stock: se recarga (`['componentes', …]`). */
export function useGuardarPiezasProveedor(): UseMutationResult<unknown, unknown, { idProv: number; idsCom: number[] }> {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ idProv, idsCom }: { idProv: number; idsCom: number[] }) =>
      api.PUT('/api/proveedores/{idProv}/componentes', { params: { path: { idProv } }, body: { idsCom } }),
    meta: { silenciarError: true },
    onSettled: () => void qc.invalidateQueries({ queryKey: ['componentes'] }),
  })
}
```

- [ ] **Step 5: Ver que pasa**

Run: `npx vitest run src/modules/almacen/proveedores` y `npx tsc -b` y `npm run lint`
Expected: verde; lint 0 errores (el aviso conocido de `BadgeEstadoPedido` sigue).

- [ ] **Step 6: Commit**

```bash
git add api src
git commit -m "chore: contrato con las piezas de cada proveedor y lo pendiente, y hook para guardarlas"
```

---

### Task 5: Web — previsión por proveedor (funciones puras)

**Files:**
- Modify: `src/modules/almacen/pedidos/formulario/lineas.ts`
- Test: `src/modules/almacen/pedidos/formulario/lineas.test.ts`

**Interfaces:**
- Consumes: `Componente.proveedores` (Task 4).
- Produces (en `lineas.ts`):
  - `cuantasPrevision(activos: Componente[], idProv: number | null): number` — sin proveedor, 0.
  - `aplicarPrevision(lineas: LineaCompra[], activos: Componente[], idProv: number): { lineas: LineaCompra[]; sinPedido: number }` — solo las marcadas de la lista de `idProv`.
  - `sinLista(activos: Componente[]): number` — marcadas con `pedir60 > 0` y `proveedores` vacío (no `null`).
  - `avisoSinLista(n: number): string | null`, `avisoNadaQuePedir(nombre: string): string`.

- [ ] **Step 1: Tests que fallan**

En `lineas.test.ts`, el bloque de la previsión (0.9.6) pasa a dar a las marcadas su lista. Sustituir los casos de `cuantasPrevision` / `aplicarPrevision` por estos (y adaptar los que quedan a la firma nueva, siempre con `idProv` 1 y `proveedores: [1]` en las marcadas):

```ts
describe('previsión por proveedor (spec 2026-10-11 §5.2)', () => {
  const c = (idCom: number, extra: Partial<Componente>): Componente => ({ ...COMPONENTES[0], idCom, tipo: `p-${idCom}`, idComMaster: null, activo: true, ...extra })
  const ACTIVOS: Componente[] = [
    c(1, { autoPedido: true, pedir60: 16, proveedores: [1] }),
    c(2, { autoPedido: true, pedir60: 0, proveedores: [1, 2] }),
    c(3, { autoPedido: true, pedir60: 4, proveedores: [2] }),
    c(4, { autoPedido: true, pedir60: 5, proveedores: [] }),
    c(5, { autoPedido: false, pedir60: 9, proveedores: [1] }),
    c(6, { autoPedido: true, pedir60: 7, proveedores: null }),
  ]

  it('N cuenta solo las marcadas de la lista del proveedor con algo que pedir; sin proveedor, 0', () => {
    expect(cuantasPrevision(ACTIVOS, 1)).toBe(1)
    expect(cuantasPrevision(ACTIVOS, 2)).toBe(1)
    expect(cuantasPrevision(ACTIVOS, null)).toBe(0)
  })

  it('aplicarPrevision solo añade las del proveedor, con él y su cantidad, y cuenta las que no necesitan pedido', () => {
    const r = aplicarPrevision([], ACTIVOS, 1)
    expect(r.lineas).toEqual([{ ...lineaCompraVacia(1, 1), idProv: 1, cantidad: '16' }])
    expect(r.sinPedido).toBe(1)
  })

  it('sinLista cuenta las marcadas con algo que pedir y la lista vacía (no las que no traen lista)', () => {
    expect(sinLista(ACTIVOS)).toBe(1)
  })

  it('avisos', () => {
    expect(avisoSinLista(0)).toBeNull()
    expect(avisoSinLista(1)).toBe('1 pieza en Auto con algo que pedir no está en la lista de ningún proveedor.')
    expect(avisoSinLista(3)).toBe('3 piezas en Auto con algo que pedir no están en la lista de ningún proveedor.')
    expect(avisoNadaQuePedir('ACME')).toBe('Nada que pedir a ACME según la previsión.')
  })
})
```
(imports nuevos: `sinLista`, `avisoSinLista`, `avisoNadaQuePedir`.)

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts`
Expected: FALLA.

- [ ] **Step 2: Implementación**

En `lineas.ts`, sustituir `marcadas`, `cuantasPrevision` y `aplicarPrevision` y añadir lo nuevo:

```ts
/** Piezas que entran en el pedido automático: activas, marcadas y master (spec 0.9.6 §4.4). */
function marcadas(activos: Componente[]): Componente[] {
  return activos.filter((c) => c.activo && c.autoPedido === true && c.idComMaster == null)
}

/** Las marcadas que vende el proveedor (spec 2026-10-11 §5.2). */
function marcadasDe(activos: Componente[], idProv: number): Componente[] {
  return marcadas(activos).filter((c) => (c.proveedores ?? []).includes(idProv))
}

/** N del botón «Añadir previsión de X (N)»: marcadas de la lista de X con algo que pedir. Sin proveedor, 0. */
export function cuantasPrevision(activos: Componente[], idProv: number | null): number {
  if (idProv === null) return 0
  return marcadasDe(activos, idProv).filter((c) => (c.pedir60 ?? 0) > 0).length
}

/** «Añadir previsión de X» (spec 0.9.6 §4.4 con el filtro de la spec 2026-10-11 §5.2). Marcada de la lista de X sin
 *  línea: línea nueva con «Pedir 60 d» y el proveedor X, o nada si no hay que pedir (cuenta en `sinPedido`). Con
 *  línea: cantidad = máx(la de la línea, la previsión, 1) y X solo si la línea no tenía proveedor. Idempotente. */
export function aplicarPrevision(lineas: LineaCompra[], activos: Componente[], idProv: number): { lineas: LineaCompra[]; sinPedido: number } {
  let resultado = lineas
  let sinPedido = 0
  for (const c of marcadasDe(activos, idProv)) {
    const pedir = c.pedir60 ?? 0
    const existente = resultado.find((l) => l.idCom === c.idCom)
    if (existente === undefined) {
      if (pedir > 0) resultado = [...resultado, { ...lineaCompraVacia(siguienteId(resultado), c.idCom), idProv, cantidad: String(pedir) }]
      else sinPedido += 1
      continue
    }
    const cantidad = Math.max(parsearEntero(existente.cantidad) ?? 0, pedir, 1)
    resultado = cambiarLinea(resultado, existente.id, { cantidad: String(cantidad), idProv: existente.idProv ?? idProv })
  }
  return { lineas: resultado, sinPedido }
}

/** Marcadas con algo que pedir que no están en la lista de ningún proveedor (spec 2026-10-11 §5.2). Una pieza sin el
 *  dato (`proveedores` nulo) no cuenta: no se sabe. */
export function sinLista(activos: Componente[]): number {
  return marcadas(activos).filter((c) => (c.pedir60 ?? 0) > 0 && c.proveedores !== null && c.proveedores.length === 0).length
}

export function avisoSinLista(n: number): string | null {
  if (n <= 0) return null
  return n === 1
    ? '1 pieza en Auto con algo que pedir no está en la lista de ningún proveedor.'
    : `${n} piezas en Auto con algo que pedir no están en la lista de ningún proveedor.`
}

export function avisoNadaQuePedir(nombre: string): string {
  return `Nada que pedir a ${nombre} según la previsión.`
}
```

- [ ] **Step 3: Ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts` y `npx tsc -b`
Expected: el fichero verde; `tsc` marca las llamadas antiguas en `NuevoPedidoDialog.tsx` (las arregla la Task 6). Si `tsc` falla solo ahí, es lo esperado: commit igualmente y anotarlo en el informe.

- [ ] **Step 4: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/lineas.ts src/modules/almacen/pedidos/formulario/lineas.test.ts
git commit -m "feat: la prevision se filtra por las piezas que vende el proveedor"
```

---

### Task 6: Web — «Nuevo pedido» con la previsión de X y «Añadir previsión…» desde el bloque

**Files:**
- Modify: `src/shared/lib/formularioPedido.ts` (`prevision?` en la precarga `bloque`)
- Modify: `src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`
- Modify: `src/modules/almacen/pedidos/bloques/reglas.ts` (`AccionMenuBloque` y `entradasMenuBloque`)
- Modify: `src/modules/almacen/pedidos/PedidosPage.tsx` (caso `'prevision'`)
- Test: `formulario/NuevoPedidoDialog.test.tsx` (bloque «pedido automático»), `bloques/reglas.test.ts`, `PedidosPage.bloques.test.tsx`; los que fijen la lista exacta del menú del bloque (`bloques/MenuBloque.test.tsx`, si la fija).

**Interfaces:**
- Consumes: Task 5 (`cuantasPrevision`, `aplicarPrevision`, `sinLista`, `avisoSinLista`, `avisoNadaQuePedir`).
- Produces: `PrecargaPedido` `{ modo: 'bloque'; idBloque: number; idProv: number; prevision?: boolean }`; acción de menú `'prevision'` («Añadir previsión…»), justo tras `'anadir'`.

- [ ] **Step 1: Tests que fallan**

En `NuevoPedidoDialog.test.tsx`, el `describe('pedido automático (spec 0.9.6 §4.4)')` pasa a:

```tsx
describe('pedido automático por proveedor (spec 0.9.6 §4.4 y 2026-10-11 §5.2)', () => {
  // lcd-x-negro (1) marcada, de ACME, pide 16; bat-x (2) marcada, de ACME, sin pedido; flex-x (6) marcada, pide 3 y no
  // está en ninguna lista.
  const CON_PREVISION: Componente[] = [
    ...COMPONENTES.map((c) =>
      c.idCom === 1 ? { ...c, autoPedido: true, pedir60: 16, proveedores: [1] }
        : c.idCom === 2 ? { ...c, autoPedido: true, pedir60: 0, proveedores: [1] } : { ...c, proveedores: [] }),
    { ...COMPONENTES[0], idCom: 6, tipo: 'flex-x', autoPedido: true, pedir60: 3, proveedores: [] },
  ]

  beforeEach(() => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json(CON_PREVISION)))
  })

  async function elegirProveedorGeneral(nombre: string) {
    await userEvent.click(screen.getByRole('combobox', { name: 'Proveedor general' }))
    await userEvent.click(await within(screen.getByRole('listbox', { name: 'Proveedor general' })).findByRole('button', { name: nombre }))
  }

  it('sin proveedor «Añadir previsión» está deshabilitado y explica por qué', async () => {
    abrir()
    const boton = await screen.findByRole('button', { name: 'Añadir previsión' })
    expect(boton).toBeDisabled()
    expect(boton).toHaveAttribute('title', 'Elige antes el proveedor')
  })

  it('«Añadir previsión de ACME (1)» mete la suya con su cantidad y ACME, y avisa de la que no necesita pedido y de la que no tiene lista', async () => {
    abrir()
    await screen.findByRole('button', { name: 'Añadir previsión' })
    await elegirProveedorGeneral('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión de ACME (1)' }))
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.getByRole('combobox', { name: 'Proveedor línea 1' })).toHaveTextContent('ACME')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('1 pieza marcada no necesita pedido.')
    expect(screen.getByRole('status')).toHaveTextContent('1 pieza en Auto con algo que pedir no está en la lista de ningún proveedor.')
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
  })

  it('con la pieza ya en el pedido sube la cantidad y no la duplica', async () => {
    abrir({ modo: 'componentes', idsCom: [1] })
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('1'))
    await elegirProveedorGeneral('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión de ACME (1)' }))
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
  })

  it('«Añadir previsión…» de un bloque abre con la previsión de su proveedor ya puesta', async () => {
    abrir({ modo: 'bloque', idBloque: 41, idProv: 1, prevision: true })
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16'))
    expect(screen.getByRole('dialog', { name: 'Añadir líneas · Bloque 41' })).toBeInTheDocument()
  })

  it('si el proveedor del bloque no tiene nada que pedir, no añade líneas y lo dice', async () => {
    abrir({ modo: 'bloque', idBloque: 41, idProv: 2, prevision: true })
    expect(await screen.findByRole('status')).toHaveTextContent('Nada que pedir a Proveedor B según la previsión.')
    expect(screen.queryByLabelText('Cantidad línea 1')).not.toBeInTheDocument()
  })
})
```
Mantener los demás casos de ese `describe` que siguen valiendo (elegir el proveedor general rellena las líneas sin proveedor; «+ Añadir línea» con proveedor general), cambiando solo el nombre del botón si lo usan.

En `bloques/reglas.test.ts`, en el test de `entradasMenuBloque` (o uno nuevo):

```ts
it('«Añadir previsión…» va justo tras «Añadir líneas…» y siempre está disponible', () => {
  const textos = entradasMenuBloque(bloque, 0).map((e) => (e === 'separador' ? '—' : e.texto))
  const i = textos.indexOf('Añadir líneas…')
  expect(textos[i + 1]).toBe('Añadir previsión…')
  const entrada = entradasMenuBloque(bloque, 0).find((e) => e !== 'separador' && e.accion === 'prevision')
  expect(entrada).toMatchObject({ deshabilitada: false, peligro: false })
})
```
(con el `bloque` de prueba que ya usen esos tests).

En `PedidosPage.bloques.test.tsx`, un caso nuevo con el mismo patrón que el de «Añadir líneas…» (abrir el ⋮ de una cabecera y elegir): elegir «Añadir previsión…» deja `formularioPedido` con `{ tipo: 'compra', precarga: { modo: 'bloque', idBloque: <n>, idProv: <p>, prevision: true } }`.

Run: `npx vitest run src/modules/almacen/pedidos`
Expected: FALLAN los casos nuevos.

- [ ] **Step 2: Precarga**

`src/shared/lib/formularioPedido.ts`:

```ts
  | { modo: 'bloque'; idBloque: number; idProv: number; prevision?: boolean }
```
y en el comentario de los modos: «'bloque': «Añadir líneas…» de un bloque (proveedor y destino fijos); con `prevision`, «Añadir previsión…»: se aplica al abrir la previsión de su proveedor (0.9.9).»

- [ ] **Step 3: Menú del bloque y página**

`bloques/reglas.ts`:

```ts
export type AccionMenuBloque = AccionBloque | 'anadir' | 'prevision' | 'separar' | 'juntar' | 'nota'
```
y en `entradasMenuBloque`, tras la entrada de `'anadir'`:

```ts
    entrada('prevision', 'Añadir previsión…', false, null),
```

`PedidosPage.tsx`, en el `switch (accion)` del menú del bloque, tras el caso `'anadir'`:

```ts
      case 'prevision':
        abrirNuevoPedido({ modo: 'bloque', idBloque: b.idBloque as number, idProv: b.idProv, prevision: true })
        return
```

- [ ] **Step 4: `NuevoPedidoDialog`**

1. Imports de `./lineas`: añadir `avisoNadaQuePedir`, `avisoSinLista`, `sinLista`.
2. Estado y cálculo (sustituye `nPrevision`):

```tsx
  const nPrevision = useMemo(() => cuantasPrevision(activos, idProvGeneral), [activos, idProvGeneral])
  const [sinListaVistas, setSinListaVistas] = useState(0)
  const [nadaQuePedir, setNadaQuePedir] = useState(false)
  // «Añadir previsión…» de un bloque: se aplica una vez, cuando el listado de Stock ha cargado (bien o mal).
  const [previsionPendiente, setPrevisionPendiente] = useState(fijo?.prevision === true)
```
3. Nombre del proveedor de la previsión (tras `nombreFijo`):

```tsx
  const nombrePrevision = fijo ? nombreFijo : proveedoresActivos.find((p) => p.idProv === idProvGeneral)?.nombre ?? ''
```
4. `anadirPrevision` pasa a:

```tsx
  function anadirPrevision() {
    if (idProvGeneral === null) return
    const r = aplicarPrevision(lineas ?? [], activos, idProvGeneral)
    setLineas(r.lineas)
    setSinPedido(r.sinPedido)
    setSinListaVistas(sinLista(activos))
    setNadaQuePedir(false)
    setError(null)
  }
```
5. Aplicación automática, tras el bloque de la precarga inicial (`if (lineas === null && …) { … }`), con el mismo patrón de «ajustar el estado durante el render»:

```tsx
  if (previsionPendiente && fijo && lineas !== null && (componentes.isSuccess || componentes.isError)) {
    setPrevisionPendiente(false)
    if (componentes.isSuccess) {
      const r = aplicarPrevision(lineas, activos, fijo.idProv)
      setLineas(r.lineas)
      setSinPedido(r.sinPedido)
      setSinListaVistas(sinLista(activos))
      setNadaQuePedir(r.lineas.length === lineas.length)
    }
  }
```
6. En `cambiar`, `quitar` y `anadir`, junto a `setSinPedido(0)`: `setSinListaVistas(0)` y `setNadaQuePedir(false)`.
7. Línea de información y botón:

```tsx
  const info = [avisoOmitidas(omitidas), avisoSinPedido(sinPedido), avisoSinLista(sinListaVistas), nadaQuePedir ? avisoNadaQuePedir(nombrePrevision) : null]
    .filter((t) => t !== null).join(' ') || null
  const botonPrevision = idProvGeneral === null
    ? <BotonSecundario type="button" disabled title="Elige antes el proveedor">Añadir previsión</BotonSecundario>
    : <BotonSecundario type="button" disabled={bloqueado || nPrevision === 0} onClick={anadirPrevision}>Añadir previsión de {nombrePrevision} ({nPrevision})</BotonSecundario>
```
(`BotonSecundario` pasa `title` al `button`; si no lo hace, comprobarlo en `shared/ui/Botones.tsx` y pasarlo.)

- [ ] **Step 5: Ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos` y `npx tsc -b` y `npm run lint`
Expected: verde.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: nuevo pedido con la prevision del proveedor elegido y anadir prevision desde el bloque"
```

---

### Task 7: Web — buscar un bloque por su número

**Files:**
- Modify: `src/modules/almacen/pedidos/filtros.ts`
- Modify: `src/modules/almacen/pedidos/PedidosPage.tsx` (texto de ayuda del buscador)
- Test: `src/modules/almacen/pedidos/filtros.test.ts`, `PedidosPage.bloques.test.tsx`

**Interfaces:**
- Consumes: `CompraComponente.idBloque` (0.9.9).
- Produces: `aplicarFiltrosPedidos` con la regla «solo cifras = número de bloque exacto» para las líneas con `idBloque`.

- [ ] **Step 1: Tests que fallan**

En `filtros.test.ts`:

```ts
describe('buscador con número de bloque (spec 2026-10-11 §5.4)', () => {
  const lista = [
    compra({ idCompra: 1, tipoComponente: 'bati13', idBloque: 13 }),
    compra({ idCompra: 2, tipoComponente: 'bati14', idBloque: 41 }),
    compra({ idCompra: 3, tipoComponente: 'lcd-x', idBloque: 4 }),
    compra({ idCompra: 4, tipoComponente: 'cami13pro', idBloque: null }),
  ]

  it('solo cifras: las líneas del bloque con ese número exacto', () => {
    expect(ids(aplicarFiltrosPedidos(lista, f({ buscador: ' 41 ' })))).toEqual([2])
    expect(ids(aplicarFiltrosPedidos(lista, f({ buscador: '4' })))).toEqual([3])
    expect(ids(aplicarFiltrosPedidos(lista, f({ buscador: '13' })))).toEqual([1])
  })

  it('con letras busca la pieza como siempre', () => {
    expect(ids(aplicarFiltrosPedidos(lista, f({ buscador: 'bati1' })))).toEqual([1, 2])
  })

  it('en «Otros» (sin bloque) las cifras siguen buscando el concepto', () => {
    const otros = [otro({ idCompraOtro: 9, concepto: 'Cable 41 cm' }), otro({ idCompraOtro: 10, concepto: 'Bolsas' })]
    expect(aplicarFiltrosPedidos(otros, f({ buscador: '41' })).map((p) => p.idCompraOtro)).toEqual([9])
  })
})
```
(`compra`, `otro`, `f` e `ids` son los ayudantes que ya tiene `filtros.test.ts`; si `compra` toma el nombre de la pieza de otro campo que `tipoComponente`, usar el que lea `nombrePedido`.)

En `PedidosPage.bloques.test.tsx`, un caso: escribir el número de un bloque de la fixture en «Buscar componente o nº de bloque…» deja una sola cabecera (`tr[data-cabecera]`), la de ese bloque.

Run: `npx vitest run src/modules/almacen/pedidos/filtros.test.ts`
Expected: FALLA.

- [ ] **Step 2: Implementación**

En `filtros.ts`, sustituir la condición del buscador por una función:

```ts
/** El buscador (:934-957 + 0.9.9): solo cifras busca el número de bloque exacto en las líneas que tienen bloque
 *  (Componentes); el resto, "contiene" sin mayúsculas sobre el componente o el concepto. */
function pasaBuscador(p: Pedido, texto: string): boolean {
  if (texto === '') return true
  if (/^\d+$/.test(texto) && 'idBloque' in p) return p.idBloque === Number(texto)
  return nombrePedido(p).toLowerCase().includes(texto)
}
```
y en `aplicarFiltrosPedidos`: `(texto === '' || nombrePedido(p).toLowerCase().includes(texto)) &&` → `pasaBuscador(p, texto) &&`. Actualizar el comentario de la función.

En `PedidosPage.tsx`, el `Input` del buscador:

```tsx
        {/* "Buscar componente…" también en Otros (calco, inventario §1); en Componentes, también el nº de bloque (0.9.9). */}
        <Input value={filtros.buscador} onChange={(e) => setFiltros((f) => ({ ...f, buscador: e.target.value }))}
          placeholder={componentes ? 'Buscar componente o nº de bloque…' : 'Buscar componente…'} className="w-[200px] bg-superficie" />
```
(`componentes`: el booleano de la pestaña que ya usa la página; si se llama distinto, usar el que haya.)

- [ ] **Step 3: Ver que pasa**

Run: `npx vitest run src/modules/almacen/pedidos` y `npx tsc -b`
Expected: verde. Si algún test de la página buscaba el texto de ayuda «Buscar componente…» en Componentes, cambiarlo al nuevo.

- [ ] **Step 4: Commit**

```bash
git add src/modules/almacen/pedidos
git commit -m "feat: el buscador de pedidos encuentra un bloque por su numero"
```

---

### Task 8: Web — columna «Pendiente» en Stock

**Files:**
- Modify: `src/modules/almacen/stock/columnas.tsx`
- Modify: `src/modules/almacen/stock/prevision.ts` (`formatearPendiente`)
- Test: `src/modules/almacen/stock/columnas.test.tsx`

**Interfaces:**
- Consumes: `Componente.pendiente` (Task 4).
- Produces: columna `pendiente` («Pendiente», ancho 90) tras «En Camino» solo con previsión; CSV con previsión: «Pendiente» tras «En camino»; `formatearPendiente(n: number | null): string` (`'—'` si es nulo o 0).

- [ ] **Step 1: Tests que fallan**

En `columnas.test.tsx`:

```tsx
describe('Pendiente (spec 2026-10-11 §5.5)', () => {
  it('va tras «En Camino» solo con previsión, con «—» si no hay y el número si hay', () => {
    const idsCon = crearColumnasStock({ onEnCamino: vi.fn(), conPrevision: true }).map((col) => col.id)
    expect(idsCon[idsCon.indexOf('enCamino') + 1]).toBe('pendiente')
    expect(crearColumnasStock({ onEnCamino: vi.fn() }).map((col) => col.id)).not.toContain('pendiente')
    render(<DataTable columns={crearColumnasStock({ onEnCamino: vi.fn(), conPrevision: true })}
      data={filas([c({ idCom: 1, pendiente: 0 }), c({ idCom: 2, tipo: 'bat-x', pendiente: 4 })])} vacio="Sin componentes" getRowId={(x) => String(x.idCom)} />)
    const cabeceras = screen.getAllByRole('columnheader').map((h) => h.textContent)
    const i = cabeceras.indexOf('Pendiente')
    const celda = (fila: number) => within(screen.getAllByRole('row')[fila]).getAllByRole('cell')[i].textContent
    expect(celda(1)).toBe('—')
    expect(celda(2)).toBe('4')
  })

  it('el CSV con previsión lleva «Pendiente» tras «En camino»', () => {
    const cab = cabecerasCsvStock(true)
    expect(cab[cab.indexOf('En camino') + 1]).toBe('Pendiente')
    expect(cabecerasCsvStock(false)).not.toContain('Pendiente')
    const fila = filaCsvStock(filas([c({ enCamino: 2, pendiente: 5 })])[0], true)
    expect(fila[cab.indexOf('Pendiente')]).toBe('5')
    expect(fila).toHaveLength(cab.length)
  })

  it('formatearPendiente', () => {
    expect(formatearPendiente(null)).toBe('—')
    expect(formatearPendiente(0)).toBe('—')
    expect(formatearPendiente(7)).toBe('7')
  })
})
```
(`base`, `c` y `filas` son los ayudantes del fichero; `formatearPendiente` se importa de `./prevision`. El orden de filas del `DataTable` sigue el de `data`.) Actualizar el test de anchos (`repartoFluido`) y cualquier otro que fije la lista completa de columnas o de cabeceras del CSV con previsión.

Run: `npx vitest run src/modules/almacen/stock/columnas.test.tsx`
Expected: FALLA.

- [ ] **Step 2: Implementación**

`prevision.ts`:

```ts
/** «Pendiente» de Stock (0.9.9): lo apuntado y aún sin pedir; «—» si no hay. */
export function formatearPendiente(n: number | null): string {
  return n === null || n <= 0 ? '—' : String(n)
}
```

`columnas.tsx`:
- `ANCHOS_STOCK`: añadir `pendiente: 90` (tras `enCamino`).
- En la lista de columnas, justo después del objeto de `enCamino`:

```tsx
    ...(conPrevision ? [{
      id: 'pendiente', header: 'Pendiente', size: ANCHOS_STOCK.pendiente, maxSize: ANCHOS_STOCK.pendiente,
      cell: ({ row }) => <span className={CREMA_EN_FILA_SELECCIONADA}>{formatearPendiente(row.original.pendiente)}</span>,
    } satisfies ColumnDef<FilaStock>] : []),
```
- CSV:

```ts
export function cabecerasCsvStock(conPrevision: boolean): string[] {
  if (!conPrevision) return CABECERAS_CSV_STOCK
  // 0.9.9: «Pendiente» tras «En camino».
  const i = CABECERAS_CSV_STOCK.indexOf('En camino') + 1
  return [...CABECERAS_CSV_STOCK.slice(0, i), 'Pendiente', ...CABECERAS_CSV_STOCK.slice(i), ...CABECERAS_CSV_PREVISION]
}
export function filaCsvStock(c: FilaStock, conPrevision = false): string[] {
  const antes = [nombreGrupo(c), String(c.stock), String(c.stockMinimo), estadoStock(c), String(c.enCamino)]
  const fecha = formatear(c.fechaRegistro, 'dd/MM/yyyy HH:mm')
  if (!conPrevision) return [...antes, fecha]
  return [...antes, String(c.pendiente ?? 0), fecha, formatearConsumo(c.consumoDiario), formatearPedir(c.pedir60), formatearModo(c.activo ? c.autoPedido : null)]
}
```
(En el CSV un 0 sale «0», no «—»: es un número para hojas de cálculo.)

- [ ] **Step 3: Ver que pasa**

Run: `npx vitest run src/modules/almacen/stock` y `npx tsc -b`
Expected: verde (si `StockPage.test.tsx` fija las cabeceras del CSV con previsión, añadir «Pendiente»).

- [ ] **Step 4: Commit**

```bash
git add src/modules/almacen/stock
git commit -m "feat: columna pendiente en stock y en su csv con prevision"
```

---

### Task 9: Web — «Piezas que vende…» en Proveedores

**Files:**
- Create: `src/modules/almacen/proveedores/PiezasProveedorDialog.tsx`
- Modify: `src/modules/almacen/proveedores/MenuProveedor.tsx` (prop `onPiezas`)
- Modify: `src/modules/almacen/proveedores/ProveedoresPage.tsx` (diálogo `piezas`)
- Test: `src/modules/almacen/proveedores/PiezasProveedorDialog.test.tsx` (nuevo), `ProveedoresPage.test.tsx`

**Interfaces:**
- Consumes: `useComponentesStock({ activo })` (`../stock/api`), `agruparCompartidos`, `nombreGrupo` (`../stock/grupos`), `useGuardarPiezasProveedor` (Task 4), `DialogoAlmacen` (`../ui/DialogoAlmacen`), `Checkbox` (`@/shared/ui/checkbox`), `Input`, `BotonSecundario`, errores (`ReglaNegocioError`, `esErrorGestionadoGlobalmente`, `mensajeDeError` de `@/shared/api/errors`), `useAlerta`.
- Produces: `<PiezasProveedorDialog proveedor onCerrar />` (la página lo monta solo mientras está abierto); `MenuProveedor` con `onPiezas: (p: Proveedor) => void` y la entrada «Piezas que vende…».

- [ ] **Step 1: Tests que fallan**

`src/modules/almacen/proveedores/PiezasProveedorDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Componente, Proveedor } from '@/shared/api/client'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { COMPONENTES } from '../pedidos/formulario/datosPrueba'
import { PiezasProveedorDialog } from './PiezasProveedorDialog'

const ACME: Proveedor = { idProv: 1, nombre: 'ACME', activo: true, divisa: 'EUR', comentario: '', tipo: 'COMPONENTES' }

// lcd-x-negro (1) y bat-x / bat-y (2, 5) los vende ACME; mc-x (4) está desactivada y también la vende ACME.
const LISTADO: Componente[] = COMPONENTES.map((c) => ({ ...c, proveedores: c.idCom === 1 || c.idCom === 2 || c.idCom === 4 || c.idCom === 5 ? [1] : [] }))
  .concat([{ ...COMPONENTES[0], idCom: 6, tipo: 'flex-x', proveedores: [] }])

let cuerpos: unknown[]
beforeEach(() => {
  cuerpos = []
  server.use(
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(LISTADO)),
    http.put('*/api/proveedores/:id/componentes', async ({ request }) => {
      cuerpos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }),
  )
})

const abrir = (onCerrar = vi.fn()) => {
  renderConProviders(<PiezasProveedorDialog proveedor={ACME} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

describe('PiezasProveedorDialog (spec 2026-10-11 §5.1)', () => {
  it('lista las activas, una fila por grupo, con las de ACME marcadas', async () => {
    abrir()
    const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
    expect(await within(dlg).findByRole('checkbox', { name: 'Vende lcd-x-negro' })).toBeChecked()
    expect(within(dlg).getByRole('checkbox', { name: 'Vende bat-x / bat-y' })).toBeChecked()
    expect(within(dlg).getByRole('checkbox', { name: 'Vende flex-x' })).not.toBeChecked()
    expect(within(dlg).queryByRole('checkbox', { name: 'Vende mc-x' })).not.toBeInTheDocument()
    expect(dlg).toHaveTextContent('3 piezas marcadas')
  })

  it('«Marcar las que se ven» y «Quitar las que se ven» actúan sobre lo filtrado; guardar conserva la desactivada', async () => {
    const onCerrar = abrir()
    const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
    await within(dlg).findByRole('checkbox', { name: 'Vende flex-x' })
    await userEvent.type(within(dlg).getByRole('textbox', { name: 'Buscar pieza' }), 'flex')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Marcar las que se ven' }))
    await userEvent.clear(within(dlg).getByRole('textbox', { name: 'Buscar pieza' }))
    await userEvent.type(within(dlg).getByRole('textbox', { name: 'Buscar pieza' }), 'lcd')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Quitar las que se ven' }))
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    const cuerpo = cuerpos[0] as { idsCom: number[] }
    expect([...cuerpo.idsCom].sort((a, b) => a - b)).toEqual([2, 4, 6])
  })

  it('«Solo las marcadas» esconde las demás', async () => {
    abrir()
    const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
    await within(dlg).findByRole('checkbox', { name: 'Vende flex-x' })
    await userEvent.click(within(dlg).getByRole('checkbox', { name: 'Solo las marcadas' }))
    expect(within(dlg).queryByRole('checkbox', { name: 'Vende flex-x' })).not.toBeInTheDocument()
    expect(within(dlg).getByRole('checkbox', { name: 'Vende lcd-x-negro' })).toBeInTheDocument()
  })

  it('un 422 sale dentro y el diálogo sigue abierto', async () => {
    server.use(http.put('*/api/proveedores/:id/componentes', () => HttpResponse.json({ message: 'Alguna pieza ya no existe.' }, { status: 422 })))
    const onCerrar = abrir()
    const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
    await within(dlg).findByRole('checkbox', { name: 'Vende flex-x' })
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    expect(await within(dlg).findByRole('alert')).toHaveTextContent('Alguna pieza ya no existe.')
    expect(onCerrar).not.toHaveBeenCalled()
  })
})
```
(Si `renderConProviders` o `SESION_SUPER` viven en otra ruta, usar la de los demás tests del módulo. Comprobar la forma de un 422 con `{message}` en otros tests, p. ej. `bloques/dialogos.test.tsx`.)

En `ProveedoresPage.test.tsx`, un caso: el menú de una fila (clic derecho, mismo patrón que «Editar») tiene «Piezas que vende…» y al elegirlo abre el diálogo «Piezas que vende · <nombre>»; el técnico no tiene menú (ya probado).

Run: `npx vitest run src/modules/almacen/proveedores`
Expected: FALLA.

- [ ] **Step 2: Diálogo**

`src/modules/almacen/proveedores/PiezasProveedorDialog.tsx`:

```tsx
import { useMemo, useState } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { BotonSecundario } from '@/shared/ui/Botones'
import { Checkbox } from '@/shared/ui/checkbox'
import { Input } from '@/shared/ui/input'
import { useComponentesStock } from '../stock/api'
import { agruparCompartidos, nombreGrupo } from '../stock/grupos'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { useGuardarPiezasProveedor } from './api'

type Props = { proveedor: Proveedor; onCerrar: () => void }

const textoMarcadas = (n: number) => (n === 1 ? '1 pieza marcada' : `${n} piezas marcadas`)

/** «Piezas que vende…» (spec 2026-10-11 §5.1): la lista del proveedor sale del listado de Stock (`proveedores` de cada
 *  pieza). Se ven las activas, una fila por grupo compartido (el master); las desactivadas que ya estaban en la lista no
 *  se ven pero se conservan al guardar. «Guardar» manda la lista entera. La página lo monta solo mientras está abierto. */
export function PiezasProveedorDialog({ proveedor, onCerrar }: Props) {
  const componentes = useComponentesStock({ activo: false })
  const guardar = useGuardarPiezasProveedor()
  const { mostrarError } = useAlerta()
  const filas = useMemo(() => agruparCompartidos((componentes.data ?? []).filter((c) => c.activo)), [componentes.data])
  const [marcadas, setMarcadas] = useState<Set<number> | null>(null)
  const [texto, setTexto] = useState('')
  const [soloMarcadas, setSoloMarcadas] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Las de la lista al abrir (masters, activas o no), una sola vez cuando carga el listado.
  if (marcadas === null && componentes.isSuccess) {
    setMarcadas(new Set((componentes.data ?? [])
      .filter((c) => c.idComMaster == null && (c.proveedores ?? []).includes(proveedor.idProv))
      .map((c) => c.idCom)))
  }

  const buscado = texto.trim().toLowerCase()
  const visibles = filas.filter((f) =>
    (buscado === '' || nombreGrupo(f).toLowerCase().includes(buscado)) && (!soloMarcadas || (marcadas?.has(f.idCom) ?? false)))

  function cambiar(ids: number[], marcar: boolean) {
    setMarcadas((m) => {
      const nuevo = new Set(m ?? [])
      for (const id of ids) {
        if (marcar) nuevo.add(id)
        else nuevo.delete(id)
      }
      return nuevo
    })
    setError(null)
  }

  async function confirmar() {
    if (marcadas === null) return
    setError(null)
    try {
      await guardar.mutateAsync({ idProv: proveedor.idProv, idsCom: [...marcadas] })
      onCerrar()
    } catch (e) {
      if (e instanceof ReglaNegocioError) { setError(e.message); return }
      if (esErrorGestionadoGlobalmente(e)) return
      mostrarError(mensajeDeError(e))
    }
  }

  return (
    <DialogoAlmacen abierto ancho={640} titulo={`Piezas que vende · ${proveedor.nombre}`} error={error} textoAccion="Guardar"
      enviando={guardar.isPending} accionDeshabilitada={marcadas === null} onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      <div className="flex flex-wrap items-center gap-2.5">
        <Input value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Buscar pieza…" aria-label="Buscar pieza" className="w-[200px] bg-superficie" />
        <BotonSecundario type="button" onClick={() => cambiar(visibles.map((f) => f.idCom), true)}>Marcar las que se ven</BotonSecundario>
        <BotonSecundario type="button" onClick={() => cambiar(visibles.map((f) => f.idCom), false)}>Quitar las que se ven</BotonSecundario>
        <label className="flex items-center gap-1.5 text-[12px] text-azul-gris">
          <Checkbox checked={soloMarcadas} onCheckedChange={(v) => setSoloMarcadas(v === true)} aria-label="Solo las marcadas" />
          Solo las marcadas
        </label>
      </div>
      <p className="text-[12px] text-azul-gris">{textoMarcadas(marcadas?.size ?? 0)}</p>
      <div className="flex max-h-[50vh] flex-col overflow-y-auto rounded border border-fila-sep">
        {visibles.map((f) => (
          <label key={f.idCom} className="flex items-center gap-2 border-b border-fila-sep px-2 py-1 text-[13px] text-azul-medio last:border-b-0">
            <Checkbox checked={marcadas?.has(f.idCom) ?? false} onCheckedChange={(v) => cambiar([f.idCom], v === true)} aria-label={`Vende ${nombreGrupo(f)}`} />
            {nombreGrupo(f)}
          </label>
        ))}
      </div>
    </DialogoAlmacen>
  )
}
```
(Comprobar los nombres reales de `esErrorGestionadoGlobalmente`, `mensajeDeError` y `ReglaNegocioError` en `@/shared/api/errors`, y que `DialogoAlmacen` acepta `ancho` y `accionDeshabilitada`; si no, el cambio mínimo.)

- [ ] **Step 3: Menú y página**

`MenuProveedor.tsx`: prop nueva `onPiezas: (p: Proveedor) => void` y, tras «Editar»:

```tsx
      <ContextMenuItem onSelect={() => onPiezas(p)}>Piezas que vende…</ContextMenuItem>
```

`ProveedoresPage.tsx`:
- `type Dialogo = … | { tipo: 'piezas'; p: Proveedor } | null` (el efecto que marca la interacción con `dialogo` ya congela el sondeo).
- En `MenuProveedor`: `onPiezas={(x) => setDialogo({ tipo: 'piezas', p: x })}`.
- Tras el `ConfirmDialog`:

```tsx
      {dialogo?.tipo === 'piezas' && <PiezasProveedorDialog proveedor={dialogo.p} onCerrar={cerrarDialogo} />}
```

- [ ] **Step 4: Ver que pasa**

Run: `npx vitest run src/modules/almacen/proveedores` y `npx tsc -b` y `npm run lint`
Expected: verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/proveedores
git commit -m "feat: piezas que vende cada proveedor desde su menu en proveedores"
```

---

### Task 10: E2E `catalogo.spec.ts` (solo preproducción)

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/catalogo.spec.ts`
- Modify: `gestion-reparaciones-web/.env.e2e.example` (un párrafo)

**Interfaces:**
- Consumes: todo lo anterior; patrones de `tests/e2e/bloques.spec.ts` (`entrar`, `llamarApi`, guarda del letrero «PREPRODUCCIÓN · datos de prueba», `E2E_SKU_PRUEBA`, `E2E_USER`/`E2E_PASS`, `ADMIN_USER`/`ADMIN_PASS`).

- [ ] **Step 1: Escribir la E2E**

`tests/e2e/catalogo.spec.ts` (copiar de `bloques.spec.ts` los ayudantes `entrar` y `llamarApi` y la guarda del letrero, que deben ser idénticos):

```ts
import { expect, test, type Page } from '@playwright/test'

// 0.9.9 (spec 2026-10-11): piezas que vende cada proveedor, previsión por proveedor, «Pendiente» y buscar un bloque por
// su número. ESCRIBE: solo contra PREPRODUCCIÓN (se niega sin el letrero). Proveedor sintético «E2E Catálogo <marca>»;
// el SKU de prueba (E2E_SKU_PRUEBA, activo y sin master) se pasa a Auto y se le sube el mínimo para que haya algo que
// pedir; todo se devuelve como estaba en afterAll y la línea creada (pendiente) se borra.

const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const marca = Date.now()
const nombreProv = `E2E Catálogo ${marca}`
const sku = process.env.E2E_SKU_PRUEBA

type Pieza = { idCom: number; tipo: string; stockMinimo: number; autoPedido: boolean | null; pedir60: number | null; pendiente: number | null; proveedores: number[] | null; stock: number }

let sup: Page | null = null
let adm: Page | null = null
let idProv: number | null = null
let antes: Pieza | null = null
const lineasCreadas: number[] = []

/* entrar(page, usuario, clave) y llamarApi(page, metodo, ruta, cuerpo?): copiados tal cual de bloques.spec.ts. */

async function pieza(page: Page): Promise<Pieza> {
  const r = await llamarApi(page, 'GET', '/api/componentes/gestionados')
  expect(r.status).toBe(200)
  const p = (r.json as Pieza[]).find((x) => x.tipo === sku)
  expect(p, `No hay componente ${sku}`).toBeTruthy()
  return p as Pieza
}

test.describe.configure({ mode: 'serial' })

test.beforeAll(async ({ browser }, testInfo) => {
  if (!process.env.E2E_USER || !process.env.E2E_PASS || !process.env.ADMIN_USER || !process.env.ADMIN_PASS || !sku) return
  const baseURL = testInfo.project.use.baseURL
  sup = await browser.newPage({ baseURL })
  await entrar(sup, process.env.E2E_USER, process.env.E2E_PASS)
  if (!(await sup.getByText(LETRERO_PRE).isVisible())) {
    await sup.close(); sup = null
    throw new Error(`catalogo.spec.ts escribe datos: se niega a correr sin el letrero «${LETRERO_PRE}»`)
  }
  adm = await browser.newPage({ baseURL })
  await entrar(adm, process.env.ADMIN_USER, process.env.ADMIN_PASS)
  antes = await pieza(sup)
  const alta = await llamarApi(sup, 'POST', '/api/proveedores', { nombre: nombreProv, divisa: 'EUR', tipo: 'COMPONENTES' })
  expect(alta.status).toBe(201)
  const lista = await llamarApi(sup, 'GET', '/api/proveedores?tipo=COMPONENTES')
  idProv = (lista.json as { idProv: number; nombre: string }[]).find((p) => p.nombre === nombreProv)?.idProv ?? null
  expect(idProv).not.toBeNull()
})

test.beforeEach(() => {
  test.skip(!sku || !process.env.ADMIN_USER, 'Faltan E2E_SKU_PRUEBA o ADMIN_USER/ADMIN_PASS')
  test.skip(sup === null || adm === null || idProv === null, 'Sin sesiones o sin proveedor de prueba (beforeAll)')
})

test.afterAll(async () => {
  if (sup === null) return
  for (const id of lineasCreadas) await llamarApi(sup, 'DELETE', `/api/compras/${id}`)
  if (antes !== null && adm !== null) {
    await llamarApi(adm, 'PATCH', `/api/componentes/${antes.idCom}/stock-minimo`, { stockMinimo: antes.stockMinimo })
    await llamarApi(sup, 'PATCH', `/api/componentes/${antes.idCom}/auto-pedido`, { autoPedido: antes.autoPedido === true })
  }
  if (idProv !== null) {
    const borrado = await llamarApi(sup, 'DELETE', `/api/proveedores/${idProv}`)
    if (borrado.status !== 204) console.warn(`catalogo.spec: no se pudo borrar el proveedor ${idProv} (${borrado.status})`)
  }
  if (antes !== null) expect((await pieza(sup)).stock, 'el stock del SKU queda igual').toBe(antes.stock)
  await sup.close()
  await adm?.close()
})

test('«Piezas que vende…» guarda la pieza en la lista del proveedor', async () => {
  const page = sup as Page
  await page.goto('/stock/proveedores')
  await page.getByRole('row').filter({ hasText: nombreProv }).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Piezas que vende…' }).click()
  const dlg = page.getByRole('dialog', { name: `Piezas que vende · ${nombreProv}` })
  await dlg.getByRole('textbox', { name: 'Buscar pieza' }).fill(sku as string)
  await dlg.getByRole('checkbox', { name: new RegExp(`^Vende ${sku}( |$)`) }).click()
  await dlg.getByRole('button', { name: 'Guardar' }).click()
  await expect(dlg).toBeHidden()
  expect((await pieza(page)).proveedores).toContain(idProv)
})

test('con algo que pedir, «Añadir previsión de X (1)» la mete con su cantidad y X', async () => {
  const page = sup as Page
  const p = antes as Pieza
  // Mínimo por encima de lo que hay para que «Pedir 60 d» > 0 y la pieza en Auto.
  const minimo = p.stock + 50
  expect((await llamarApi(adm as Page, 'PATCH', `/api/componentes/${p.idCom}/stock-minimo`, { stockMinimo: minimo })).status).toBe(200)
  expect((await llamarApi(page, 'PATCH', `/api/componentes/${p.idCom}/auto-pedido`, { autoPedido: true })).status).toBe(200)
  const conPedido = await pieza(page)
  expect(conPedido.pedir60 ?? 0).toBeGreaterThan(0)

  await page.goto('/stock/pedidos')
  await page.getByRole('button', { name: 'Nuevo pedido', exact: true }).click()
  const dlg = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
  await expect(dlg.getByRole('button', { name: 'Añadir previsión' })).toBeDisabled()
  await dlg.getByRole('combobox', { name: 'Proveedor general' }).click()
  await page.getByRole('listbox', { name: 'Proveedor general' }).getByRole('button', { name: nombreProv }).click()
  await dlg.getByRole('button', { name: `Añadir previsión de ${nombreProv} (1)` }).click()
  await expect(dlg.getByLabel('Cantidad línea 1')).toHaveValue(String(conPedido.pedir60))
  const respuesta = page.waitForResponse((r) => r.url().includes('/api/compras/lote') && r.request().method() === 'POST')
  await dlg.getByRole('button', { name: 'Confirmar pedido' }).click()
  const cuerpo = (await (await respuesta).json()) as { idsCreados: number[] }
  lineasCreadas.push(...cuerpo.idsCreados)
  await expect(dlg).toBeHidden()

  const despues = await pieza(page)
  expect(despues.pendiente).toBe((conPedido.pendiente ?? 0) + (conPedido.pedir60 ?? 0))
  expect(despues.pedir60).toBe(0)
})

test('el buscador con el número del bloque enseña solo ese bloque', async () => {
  const page = sup as Page
  const compras = await llamarApi(page, 'GET', '/api/compras')
  const idBloque = (compras.json as { idCompra: number; idBloque: number | null }[]).find((l) => l.idCompra === lineasCreadas[0])?.idBloque
  expect(idBloque).toBeTruthy()
  await page.goto('/stock/pedidos')
  await page.getByPlaceholder('Buscar componente o nº de bloque…').fill(String(idBloque))
  await expect(page.locator('tr[data-cabecera]')).toHaveCount(1)
  await expect(page.locator('tr[data-cabecera]').first()).toContainText(nombreProv)
})
```
(Ajustar a la forma real de la respuesta del lote —`idsCreados` como en `bloques.spec.ts`— y a cómo se abre el menú de fila en Proveedores en `ProveedoresPage.test.tsx`; si la cabecera del bloque no lleva el nombre del proveedor en texto, comprobar el número `Bloque <n>` como hace `bloques.spec.ts`.)

`.env.e2e.example`: párrafo como el de `bloques.spec.ts`: «catalogo.spec.ts ESCRIBE (0.9.9, solo PREPRODUCCIÓN): con E2E_USER crea un proveedor "E2E Catálogo <marca>", le pone E2E_SKU_PRUEBA en su lista y hace un pedido de la previsión; con ADMIN_USER sube temporalmente el mínimo de E2E_SKU_PRUEBA. Al final borra la línea y el proveedor y devuelve mínimo y modo.»

- [ ] **Step 2: Comprobar sin servidor**

Run: `npx tsc -b && npm run lint && E2E_BASE_URL=http://localhost:9 npx playwright test --list tests/e2e/catalogo.spec.ts`
Expected: sin errores; lista 3 tests. (Se ejecuta de verdad contra preprod en la Task 12.)

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/catalogo.spec.ts .env.e2e.example
git commit -m "test: e2e de las piezas de cada proveedor y la prevision por proveedor contra preprod"
```

---

### Task 11: Cierre — documentación, suites e integración (con OK en cada paso de integración)

**Files:**
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.9]`)
- Modify: `docs/novedades/NOVEDADES-v0.9.9.md` (raíz)
- Modify: `gestion-reparaciones-web/docs/paridad/proveedores.md`, `stock.md`, `pedidos.md`
- Modify: `C:\Users\dev\Documents\Apuntes\plan-futuro.md` (fuera de git)

- [ ] **Step 1: CHANGELOG** — en la sección `## [0.9.9] - 2026-10-XX — Pedidos por bloques` (no se cambia la versión: sigue 0.9.9), añadir tras la viñeta de «Mover a bloque»:

```markdown
- **Piezas que vende cada proveedor** (Proveedores → clic derecho → «Piezas que vende…», supertécnicos): lista de piezas con buscador, «Marcar las que se ven» y «Quitar las que se ven». Arranca rellenada con el historial de pedidos.
- **Previsión por proveedor** en «Nuevo pedido»: se elige el proveedor arriba y «Añadir previsión de X (N)» añade las piezas en Auto que vende X con su cantidad. Avisa de las piezas en Auto con algo que pedir que no están en la lista de ningún proveedor. También desde el menú del bloque: «Añadir previsión…».
- **«Pedir 60 d» descuenta lo pendiente** (lo apuntado en cualquier bloque y aún sin pedir), y Stock tiene la columna **«Pendiente»** (también en el CSV).
- **Buscar un bloque por su número**: en Pedidos (Componentes), escribir solo cifras en el buscador enseña ese bloque.
```
y en la última viñeta («Requiere el servidor 0.9.9 y las migraciones…») añadir `migracion-catalogo-proveedores.sql` y `rellenar-catalogo-proveedores.sql`.

- [ ] **Step 2: Novedades** — en `docs/novedades/NOVEDADES-v0.9.9.md`, antes del último bloque, una sección:

```markdown
---

## 🧾 Qué le pides a cada proveedor

- En **Proveedores**, clic derecho → **«Piezas que vende…»**: marcas las piezas de ese proveedor (con el buscador y
  «Marcar las que se ven» se ponen de golpe, por ejemplo todas las baterías). Ya viene rellenada con lo que le has pedido
  hasta hoy.
- En **«Nuevo pedido»**, eliges el proveedor arriba y **«Añadir previsión de X»** te mete lo que hay que pedirle (las
  piezas en Auto que vende). También desde el menú del bloque: **«Añadir previsión…»**.
- La previsión ya **no vuelve a proponer lo que está apuntado en otro bloque**: Stock enseña cuánto hay **«Pendiente»**.
- En Pedidos, escribe el **número de un bloque** en el buscador para ir directo a él.
```

- [ ] **Step 3: Fichas de paridad (web)** — una sección «0.9.9 — sin equivalente en el JavaFX» (o añadir a la que ya existe en `pedidos.md`) con: Proveedores «Piezas que vende…»; Stock columna «Pendiente» y «Pedir 60 d» que descuenta lo pendiente; Pedidos búsqueda por número de bloque y «Añadir previsión de X»/«Añadir previsión…». Mismo estilo (una viñeta por línea) y fin de línea de cada fichero.

- [ ] **Step 4: Suites completas**

Run (servidor): `mvn -q test` → verde. Run (web): `npx tsc -b && npm run lint && npx vitest run && npm run build` → verde (si algún fichero ajeno da timeout de 5 s por carga, repetirlo solo y anotarlo).

- [ ] **Step 5: Commits**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git add CHANGELOG.md docs/paridad && git commit -m "docs: changelog y paridad de las piezas de cada proveedor (0.9.9)"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.9.md && git commit -m "docs: novedades de la 0.9.9 con las piezas de cada proveedor"
```
`plan-futuro.md`: en el punto de la 0.9.9, añadir la ampliación; en su backlog, «Ya que pides a X…» (15 días), precio del último pedido, combo de la línea por la lista, proveedor preferido y «Pendiente» como enlace.

- [ ] **Step 6: Integración (OK del usuario antes de cada uno)**
1. Merge `--no-ff` de `feature/catalogo-proveedores` a `main` en servidor y web desde los clones normales (en `main` y limpios): `merge: piezas de cada proveedor y prevision por proveedor (0.9.9)`. Comprobar `git diff feature/catalogo-proveedores main` vacío.
2. Push de `main` en los dos.
3. Gitlinks en la raíz (`chore: gitlinks servidor y web tras las piezas de cada proveedor (0.9.9)`) y push de la raíz.

---

### Task 12: Preproducción (lo ejecuta el usuario; Claude no hace SSH)

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\preprod\v099b-preprod.md`
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_preprod.md` (§Registro de sesiones)

- [ ] **Step 1: Guion** (mismo esquema que `v099-preprod.md`; sin `docker exec -i` sin heredoc o `< fichero`; avisar de que `docker compose up -d --build` tarda ~1 min sin salida):
1. **Estado:** clones en `main` con `187145e` / `343c0d4`; contenedores `Up`; `SELECT COUNT(*) AS tabla_ya FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Proveedor_componente';` = **0**; `tablas` = **24**; parejas previstas (solo lectura): `SELECT COUNT(*) FROM (SELECT DISTINCT cc.ID_PROV, COALESCE(c.ID_COM_MASTER, cc.ID_COM) FROM Compra_componente cc JOIN Componente c ON c.ID_COM = cc.ID_COM WHERE cc.ESTADO <> 'cancelado') x;`.
2. **Código, migración, relleno y arranque:** `pull --ff-only` de los dos; `time` + migración; comprobación de la tabla y sus dos claves ajenas (`fk_provcom_proveedor`, `fk_provcom_componente`); relleno (`parejas` = las previstas; `apuntan_a_slave` 0); `docker compose up -d --build`; `Started App` sin `ERROR`; **relleno otra vez** (mismas cifras).
3. **Comprobaciones (Claude, desde el PC):** `/version.json` 0.9.9; `/api/componentes/gestionados` con `pendiente` y `proveedores` como supertécnico y nulos como técnico; `PUT /api/proveedores/{id}/componentes` como técnico y administrador → 403 (contra un proveedor inexistente, sin escribir nada); E2E en Chromium **de una en una con ≥ 60 s**: las de siempre + `bloques.spec.ts` + `catalogo.spec.ts`; limpieza del bloque 2c de `v099-preprod.md` si `bloques.spec.ts` deja líneas canceladas, y comprobar que `catalogo.spec.ts` no deja proveedores `E2E Catálogo %`.
4. **A mano (usuario):** «Piezas que vende…» de LEO u otro real, solo mirar (sin guardar); «Nuevo pedido» → elegir proveedor → ver «Añadir previsión de X (N)» sin confirmar; columna «Pendiente».
5. **`init.sql`:** re-volcado con **25** `CREATE TABLE`, `fk_provcom_proveedor` presente, 3 hashes, 17 anuladas.

- [ ] **Step 2: Ejecutar (usuario) y revisar (Claude)** — ante cualquier cifra distinta, PARAR.
- [ ] **Step 3: Registro** en `despliegue_preprod.md`.

---

### Task 13: Producción (lo ejecuta el usuario)

**Files:**
- Modify: `C:\Users\dev\Documents\Apuntes\prod-v099.md`
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_vdc_produccion.md`; memoria `project_produccion_vdc.md` y `project_v099_bloques_pedidos.md`

- [ ] **Step 1: Guion** — actualizar `prod-v099.md`: commits nuevos de `main` (servidor y web tras la Task 11); en el bloque 1, `tabla_ya` de `Proveedor_componente` = 0 y la consulta de parejas previstas; en el bloque 2, tras la reconstrucción de bloques, la migración de la lista y el relleno; en el 2b, el relleno otra vez; copia de después con **25** tablas; vuelta atrás: la tabla se queda (el código anterior no la nombra). La norma de despliegue en horario sigue valiendo (aditivo).
- [ ] **Step 2: Ejecutar (usuario) y revisar (Claude).**
- [ ] **Step 3: Cierre** — registro en `despliegue_vdc_produccion.md`; fecha real en `[0.9.9]` del CHANGELOG (commit aparte, push con OK); tags `v0.9.9` **solo cuando lo diga el usuario**; `plan-futuro.md` con la 0.9.9 `[x]`; memoria (producción en 0.9.9 con 25 tablas; v099 cerrada); quitar los worktrees `wt/servidor-099` y `wt/web-099` y borrar las ramas `feature/bloques-pedidos` y `feature/catalogo-proveedores` (con OK).
