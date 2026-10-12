# 0.9.9 (ajuste) — Piezas que vende cada proveedor, por tipo de pieza — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada proveedor vende **tipos de pieza** (Batería, Chasis…), de entrada de todos los modelos y con los modelos que no vende como excepciones. Sustituye a la lista pieza a pieza de la ampliación del 2026-10-11.

**Architecture:**
- **Servidor:**
  - Dos tablas: `Proveedor_tipo` (proveedor, prefijo) y `Proveedor_tipo_excluido` (proveedor, prefijo, modelo).
  - Una utilidad `CatalogoPiezas` lee el tipo y el modelo de cada referencia, con el mismo criterio que la web.
  - `ProveedorPiezasService` hace tres cosas: traduce las reglas a la lista `proveedores` de cada pieza del listado de Stock, sirve `GET /api/proveedores/{id}/piezas` y guarda con `PUT` en la misma ruta.
- **Web:** solo cambia el diálogo «Piezas que vende…». Usa `MultiSelect` con texto fijo, la opción «Todos los modelos» y el modo deshabilitado. La previsión no cambia, porque sigue usando `proveedores`.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate y MariaDB. Pruebas con JUnit 5 y Mockito (JdbcTemplate simulado), MockMvc para los roles y springdoc para el contrato.
- **Web:** React, TypeScript, TanStack Query y Radix. Pruebas con Vitest, Testing Library, MSW y Playwright para las E2E.

**Spec:** `docs/superpowers/specs/2026-10-12-v099-piezas-por-tipo-design.md` (raíz). El prototipo está en `docs/superpowers/specs/assets/2026-10-12-v099-prototipo-piezas-por-tipo.html`. Va **dentro de la 0.9.9**: servidor `main` `6a02be8` y web `main` `4830969`, ya en preproducción, sin desplegar en producción ni etiquetar.

## Global Constraints

- **Ramas y worktrees:**
  - Rama `feature/piezas-por-tipo` creada **desde `main`**, en los worktrees que ya existen: `C:/Users/dev/Documents/wt/servidor-099` y `C:/Users/dev/Documents/wt/web-099`. Hoy están en `fix/orden-bloques-numero`, ya mergeada.
  - La web de ese worktree ya tiene `node_modules`.
  - Los clones normales (`ProgramaReparaciones/gestion-reparaciones-*`) no se tocan, salvo en el cierre (Task 9) y con OK.
- **Comandos (Git Bash):**
  - **Servidor:** `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`. Después, dentro de `wt/servidor-099`, `mvn -q test`, o `mvn -q test -Dtest=Clase` para una sola clase.
  - **Web:** `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint`, dentro de `wt/web-099`.
    - **Un solo proceso de vitest a la vez:** con carga, la máquina da timeouts de 5 s.
- **Commits:**
  - En español **sin tildes**, con los prefijos `feat:` / `fix:` / `test:` / `docs:` / `chore:`.
  - **Sin** línea `Co-Authored-By`.
  - Nada de push, merge ni tag sin OK del usuario.
- **Comentarios del código:** en español normal, **con** tildes.
- **Repos públicos:** datos de prueba sintéticos; sin IPs, nombres reales ni secretos.
- **Contrato:**
  - Los records anidados de un controller se llaman `<Controller sin sufijo><Record>`.
  - Todas las propiedades son `required`.
  - Tras cambiar el contrato, en la web: `cp /c/Users/dev/Documents/wt/servidor-099/target/openapi.json api/openapi.json && npm run api:types:offline`. `schema.d.ts` no se edita a mano.
- **Fronteras de la web** (eslint):
  - Un módulo no importa de otro (`@/modules/...`). Lo común sube a `src/shared`.
  - `src/shared` no importa de `modules` ni de `app`.
- **Tipos de pieza:** prefijo de la referencia = todo lo que va antes de la primera `i`; `otro…` = `otro`. Nombres: `bat` Batería, `cha` Chasis, `tapa` Tapa trasera, `g` Glass, `mc` Marco, `lcd` Pantalla, `cam` Cámara. Orden: `bat`, `cha`, `tapa`, `g`, `mc`, `lcd` y el resto por orden alfabético (hoy `cam`).
- **Modelos:** los 39 códigos de `MODELOS_ORDENADOS` de la web (`src/modules/taller/lib/modelos.ts`), en el mismo orden. El modelo de una referencia es el código **más largo** que sea prefijo de lo que queda tras quitar el prefijo y la `i`.
- **Textos exactos (servidor):**
  - 404 «Proveedor no encontrado.»
  - 422:
    - «Solo los proveedores de componentes tienen piezas.»
    - «Falta la lista de tipos.»
    - «Falta la lista de modelos excluidos.»
    - «Algún tipo de pieza no existe.»
    - «Un tipo de pieza está repetido.»
    - «Algún modelo no existe.»
    - «Si no vende ningún modelo de un tipo, desmarca el tipo.»
  - Registro `EDITAR_PIEZAS_PROVEEDOR` con detalle `ID_PROV: <id>, PIEZAS: <resumen>`. El resumen es `bat (todos), g (sin 12mini, 13mini)`, con los tipos en el orden de tipos y los modelos en el de `MODELOS_ORDENADOS`. Sin tipos: `ninguna`.
- **Textos exactos (web):**
  - Título «Piezas que vende · <proveedor>».
  - Ayuda: «Marca los tipos de pieza que vende. De entrada, de todos los modelos (también los que salgan más adelante); si alguno no lo vende, desmárcalo en el desplegable.»
  - Recuento «Vende N de M tipos».
  - Casilla «Vende <tipo>».
  - Botón del desplegable:
    - texto «No lo vende» (deshabilitado), «Todos los modelos» o «Seleccionados K de M»;
    - nombre accesible «Modelos de <tipo>».
  - Primera opción del desplegable: «Todos los modelos».
  - Al lado del desplegable: «Sin iPhone 12 Mini · iPhone 13 Mini».
  - Botón «Guardar».
  - «Cargando piezas…» y «No se ha podido cargar la lista de piezas.»
- **Registro de subagentes:** `.superpowers/sdd/v099c/` en la raíz (gitignored).

---

### Task 1: Servidor — `CatalogoPiezas`: tipo y modelo de una referencia

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/CatalogoPiezas.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java`: `getAgrupadosPorTipo` y el `extraerPrefijo` privado
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/CatalogoPiezasTest.java`

**Interfaces:**
- Produces: `CatalogoPiezas` (`final`, todo estático):
  - `List<String> MODELOS_ORDENADOS` (39 códigos) y `List<String> ORDEN_TIPOS` (`bat, cha, tapa, g, mc, lcd`).
  - `String prefijo(String tipo)`.
  - `String modelo(String tipo, String prefijo)`: puede devolver `null`.
  - `boolean esModelo(String codigo)`.
  - `List<String> ordenarTipos(Collection<String>)` y `List<String> ordenarModelos(Collection<String>)`, sin repetidos.
  - `boolean vende(Map<String, Set<String>> tipos, String tipo)`: `tipos` es prefijo → modelos excluidos.

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git status --short && git switch -c feature/piezas-por-tipo main && git log --oneline -1
```
Expected: árbol limpio; `6a02be8 merge: los bloques se ordenan por numero (0.9.9)`.

- [ ] **Step 1: Test que falla**

`src/test/java/com/reparaciones/servidor/util/CatalogoPiezasTest.java`:

```java
package com.reparaciones.servidor.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tipo y modelo de una referencia (spec 2026-10-12 §4.1): los mismos casos que los tests de la web (taller/lib/modelos.test.ts). */
class CatalogoPiezasTest {

    @Test void elCatalogoDeModelosEsElDeLaWeb() {
        assertEquals(39, CatalogoPiezas.MODELOS_ORDENADOS.size());
        assertEquals("6s", CatalogoPiezas.MODELOS_ORDENADOS.get(0));
        assertEquals("17promax", CatalogoPiezas.MODELOS_ORDENADOS.get(38));
        assertEquals(39, Set.copyOf(CatalogoPiezas.MODELOS_ORDENADOS).size());
        assertTrue(CatalogoPiezas.MODELOS_ORDENADOS.indexOf("16e") < CatalogoPiezas.MODELOS_ORDENADOS.indexOf("16plus"));
        assertTrue(CatalogoPiezas.MODELOS_ORDENADOS.indexOf("17") < CatalogoPiezas.MODELOS_ORDENADOS.indexOf("air"));
    }

    @Test void prefijoEsLoQueVaAntesDeLaPrimeraI() {
        assertEquals("bat", CatalogoPiezas.prefijo("bati13promax"));
        assertEquals("cha", CatalogoPiezas.prefijo("chai14midnight"));
        assertEquals("tapa", CatalogoPiezas.prefijo("tapai15black"));
        assertEquals("g", CatalogoPiezas.prefijo("gi15negra"));
        assertEquals("mc", CatalogoPiezas.prefijo("mci13mininegra"));
        assertEquals("lcd", CatalogoPiezas.prefijo("lcdi14negraic"));
        assertEquals("cam", CatalogoPiezas.prefijo("cami13"));
        assertEquals("otro", CatalogoPiezas.prefijo("otroi13"));
        assertEquals("flexboton", CatalogoPiezas.prefijo("flexboton"));
    }

    @Test void ganaElCodigoMasLargoQueSeaPrefijoDelResto() {
        assertEquals("13promax", CatalogoPiezas.modelo("bati13promaxneg", "bat"));
        assertEquals("13pro", CatalogoPiezas.modelo("bati13pro", "bat"));
        assertEquals("13", CatalogoPiezas.modelo("bati13", "bat"));
        assertEquals("xsmax", CatalogoPiezas.modelo("lcdixsmaxnegra", "lcd"));
        assertEquals("xs", CatalogoPiezas.modelo("lcdixsnegra", "lcd"));
        assertEquals("6splus", CatalogoPiezas.modelo("lcdi6splus", "lcd"));
        assertEquals("16e", CatalogoPiezas.modelo("bati16e", "bat"));
    }

    @Test void quitaElPrefijoYLaIYNoDistingueMayusculas() {
        assertEquals("14plus", CatalogoPiezas.modelo("BATI14PLUS", "bat"));
        assertEquals("13", CatalogoPiezas.modelo("chai13negro", "cha"));
        assertEquals("13", CatalogoPiezas.modelo("cami13", "cam"));
        assertEquals("se2020", CatalogoPiezas.modelo("batise2020", "bat"));
        assertEquals("air", CatalogoPiezas.modelo("batair", "bat"));
        assertEquals("13", CatalogoPiezas.modelo("gi13", "g"));
        assertEquals("14promax", CatalogoPiezas.modelo("gi14promaxnegra", "g"));
        assertEquals("13", CatalogoPiezas.modelo("mci13", "mc"));
    }

    @Test void sinCoincidenciaDevuelveNull() {
        assertNull(CatalogoPiezas.modelo("batuniversal", "bat"));
        assertNull(CatalogoPiezas.modelo("bat", "bat"));
        assertNull(CatalogoPiezas.modelo("", "bat"));
    }

    @Test void ordenaTiposYModelosSinRepetidos() {
        assertEquals(List.of("bat", "cha", "tapa", "g", "mc", "lcd", "cam", "zzz"),
                CatalogoPiezas.ordenarTipos(List.of("zzz", "lcd", "cam", "g", "bat", "tapa", "mc", "cha", "bat")));
        assertEquals(List.of("12", "12mini", "13", "16e", "air"), CatalogoPiezas.ordenarModelos(Set.of("air", "13", "12mini", "16e", "12")));
        assertTrue(CatalogoPiezas.esModelo("16e"));
        assertFalse(CatalogoPiezas.esModelo("13x"));
    }

    @Test void vendeConTodosLosModelosConExcepcionesYSinModelo() {
        Map<String, Set<String>> tipos = Map.of("bat", Set.of(), "g", Set.of("12mini", "13mini"));
        assertTrue(CatalogoPiezas.vende(tipos, "bati17e"));            // modelo nuevo: entra solo
        assertTrue(CatalogoPiezas.vende(tipos, "gi14negra"));
        assertFalse(CatalogoPiezas.vende(tipos, "gi13mininegra"));     // excluido
        assertTrue(CatalogoPiezas.vende(Map.of("g", Set.of("13")), "gi13mininegra"));   // 13 no excluye 13mini
        assertFalse(CatalogoPiezas.vende(tipos, "lcdi14negraic"));     // tipo que no vende
        assertTrue(CatalogoPiezas.vende(Map.of("bat", Set.of("13")), "batiuniversal"));   // sin modelo: las excepciones no le afectan
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `mvn -q test -Dtest=CatalogoPiezasTest`
Expected: falla la compilación, porque `CatalogoPiezas` no existe.

- [ ] **Step 3: Implementación**

`src/main/java/com/reparaciones/servidor/util/CatalogoPiezas.java`:

```java
package com.reparaciones.servidor.util;

import java.util.*;

/**
 * Tipo de pieza y modelo de una referencia (0.9.9, spec 2026-10-12 §4.1). El tipo es el prefijo de la referencia (lo
 * que va antes de la primera «i»: {@code bati13} → {@code bat}) y el modelo, el código de {@link #MODELOS_ORDENADOS}
 * más largo que sea prefijo de lo que queda ({@code bati13promax} → {@code 13promax}, no {@code 13}).
 */
public final class CatalogoPiezas {

    /** Calco de MODELOS_ORDENADOS de la web (src/modules/taller/lib/modelos.ts): mismos códigos y mismo orden (el de la
     *  tienda de Apple). Un modelo nuevo (p. ej. el 17e) se añade en los DOS sitios. */
    public static final List<String> MODELOS_ORDENADOS = List.of(
            "6s", "6splus", "7", "7plus", "8", "8plus", "se2020",
            "x", "xr", "xs", "xsmax",
            "11", "11pro", "11promax",
            "12", "12mini", "12pro", "12promax",
            "13", "13mini", "13pro", "13promax",
            "14", "14plus", "14pro", "14promax",
            "15", "15plus", "15pro", "15promax",
            "16", "16e", "16plus", "16pro", "16promax",
            "17", "air", "17pro", "17promax");

    /** Orden de los tipos del formulario de reparación; los demás van detrás, por orden alfabético. */
    public static final List<String> ORDEN_TIPOS = List.of("bat", "cha", "tapa", "g", "mc", "lcd");

    private CatalogoPiezas() {}

    /** Prefijo del tipo de pieza: lo que va antes de la primera «i»; «otro…» es «otro». Sin «i», la referencia entera. */
    public static String prefijo(String tipo) {
        if (tipo.startsWith("otro")) return "otro";
        int idx = tipo.indexOf('i');
        return idx > 0 ? tipo.substring(0, idx) : tipo;
    }

    /** Código de modelo (calco de extraerModelo de la web): sin el prefijo y sin la «i» que lo sigue, gana el código más
     *  largo que sea prefijo de lo que queda. {@code null} si ninguno lo es. */
    public static String modelo(String tipo, String prefijo) {
        String minusculas = tipo.toLowerCase(Locale.ROOT);
        String resto = minusculas.length() > prefijo.length() ? minusculas.substring(prefijo.length()) : "";
        if (resto.startsWith("i")) resto = resto.substring(1);
        String mejor = null;
        for (String m : MODELOS_ORDENADOS) {
            if (resto.startsWith(m) && (mejor == null || m.length() > mejor.length())) mejor = m;
        }
        return mejor;
    }

    public static boolean esModelo(String codigo) {
        return MODELOS_ORDENADOS.contains(codigo);
    }

    /** Los tipos en el orden del formulario y, detrás, el resto por orden alfabético; sin repetidos. */
    public static List<String> ordenarTipos(Collection<String> tipos) {
        List<String> lista = new ArrayList<>(new TreeSet<>(tipos));
        lista.sort(Comparator.comparingInt(p -> posicion(ORDEN_TIPOS, p)));
        return lista;
    }

    /** Los modelos en el orden de la tienda; sin repetidos. */
    public static List<String> ordenarModelos(Collection<String> modelos) {
        List<String> lista = new ArrayList<>(new TreeSet<>(modelos));
        lista.sort(Comparator.comparingInt(m -> posicion(MODELOS_ORDENADOS, m)));
        return lista;
    }

    /** Si un proveedor con estos tipos (prefijo → modelos excluidos) vende la pieza {@code tipo} (spec §4.2): tiene su
     *  tipo y su modelo no está excluido. Una pieza sin modelo reconocible la vende quien tenga su tipo. */
    public static boolean vende(Map<String, Set<String>> tipos, String tipo) {
        String p = prefijo(tipo);
        Set<String> excluidos = tipos.get(p);
        if (excluidos == null) return false;
        String m = modelo(tipo, p);
        return m == null || !excluidos.contains(m);
    }

    /** Posición en la lista; lo que no está, al final (la ordenación es estable: conserva el orden alfabético). */
    private static int posicion(List<String> orden, String valor) {
        int i = orden.indexOf(valor);
        return i >= 0 ? i : orden.size();
    }
}
```

En `ComponenteDAO.getAgrupadosPorTipo`:
- Usar `CatalogoPiezas.prefijo(c.getTipo())` en lugar de `extraerPrefijo(c.getTipo())`.
- Usar `CatalogoPiezas.ORDEN_TIPOS` en lugar de `List.of("bat", "cha", "tapa", "g", "mc", "lcd")`.
- Borrar el método privado `extraerPrefijo`.
- Añadir `import com.reparaciones.servidor.util.CatalogoPiezas;`.

- [ ] **Step 4: Comprobar que pasa**

Run: `mvn -q test -Dtest='CatalogoPiezasTest,ComponenteDAO*Test'`
Expected: todo verde. Las pruebas de `getAgrupadosPorTipo`, si las hay, siguen pasando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/util/CatalogoPiezas.java src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java src/test/java/com/reparaciones/servidor/util/CatalogoPiezasTest.java
git commit -m "feat: catalogo de piezas con el tipo y el modelo de cada referencia"
```

---

### Task 2: Servidor — tablas, relleno y DAO de los tipos de cada proveedor

**Files:**
- Modify (reescribir): `gestion-reparaciones-servidor/sql/migracion-catalogo-proveedores.sql`
- Modify (reescribir): `gestion-reparaciones-servidor/sql/rellenar-catalogo-proveedores.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql`: el `DROP` y el `CREATE` de `Proveedor_componente`
- Modify: `gestion-reparaciones-servidor/docs/schema.md`: la línea de `Proveedor_componente`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ProveedorTipoDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ProveedorTipoDAOTest.java`

**Interfaces:**
- Produces: `ProveedorTipoDAO` (`@Repository`, constructor `(JdbcTemplate)`):
  - `record Pieza(String tipo, boolean activo)`.
  - `Map<Integer, Map<String, Set<String>>> todos()`: proveedor → (prefijo → excluidos), con mapas y conjuntos ordenados.
  - `Map<String, Set<String>> deProveedor(int idProv)`.
  - `void reemplazar(int idProv, Map<String, Set<String>> tipos)`: borra sus tipos (las excepciones caen en cascada) e inserta.
  - `List<Pieza> piezas()`: las referencias que no son `otro`, activas o no.
  - Constantes SQL package-private: `SQL_TIPOS`, `SQL_EXCLUIDOS`, `SQL_TIPOS_DE`, `SQL_EXCLUIDOS_DE`, `SQL_BORRAR`, `SQL_INSERTAR_TIPO`, `SQL_INSERTAR_EXCLUIDO`, `SQL_PIEZAS`.
- `ProveedorComponenteDAO` **se queda** hasta la Task 4, porque `ComponenteController` aún lo usa.

- [ ] **Step 1: Test que falla**

`src/test/java/com/reparaciones/servidor/dao/ProveedorTipoDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tipos que vende cada proveedor (spec 2026-10-12 §3): SQL y reparto, con el JdbcTemplate simulado. */
class ProveedorTipoDAOTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProveedorTipoDAO dao = new ProveedorTipoDAO(jdbc);

    /** Una fila con ID_PROV, PREFIJO y MODELO (el que no se use, da igual). */
    private static ResultSet fila(int idProv, String prefijo, String modelo) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_PROV")).thenReturn(idProv);
        when(rs.getString("PREFIJO")).thenReturn(prefijo);
        when(rs.getString("MODELO")).thenReturn(modelo);
        return rs;
    }

    @Test void lasConsultasSonLasDeLaSpec() {
        assertEquals("SELECT ID_PROV, PREFIJO FROM Proveedor_tipo", ProveedorTipoDAO.SQL_TIPOS);
        assertEquals("SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido", ProveedorTipoDAO.SQL_EXCLUIDOS);
        assertEquals("SELECT ID_PROV, PREFIJO FROM Proveedor_tipo WHERE ID_PROV = ?", ProveedorTipoDAO.SQL_TIPOS_DE);
        assertEquals("SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido WHERE ID_PROV = ?", ProveedorTipoDAO.SQL_EXCLUIDOS_DE);
        assertEquals("DELETE FROM Proveedor_tipo WHERE ID_PROV = ?", ProveedorTipoDAO.SQL_BORRAR);
        assertEquals("INSERT INTO Proveedor_tipo (ID_PROV, PREFIJO) VALUES (?, ?)", ProveedorTipoDAO.SQL_INSERTAR_TIPO);
        assertEquals("INSERT INTO Proveedor_tipo_excluido (ID_PROV, PREFIJO, MODELO) VALUES (?, ?, ?)", ProveedorTipoDAO.SQL_INSERTAR_EXCLUIDO);
        assertEquals("SELECT TIPO, ACTIVO FROM Componente WHERE TIPO NOT LIKE 'otro%'", ProveedorTipoDAO.SQL_PIEZAS);
    }

    @Test void todosJuntaTiposYExcepcionesPorProveedor() {
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(fila(2, "bat", null));
            h.processRow(fila(1, "g", null));
            h.processRow(fila(1, "bat", null));
            return null;
        }).when(jdbc).query(eq(ProveedorTipoDAO.SQL_TIPOS), any(RowCallbackHandler.class));
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(fila(1, "g", "13mini"));
            h.processRow(fila(1, "g", "12mini"));
            return null;
        }).when(jdbc).query(eq(ProveedorTipoDAO.SQL_EXCLUIDOS), any(RowCallbackHandler.class));
        Map<Integer, Map<String, Set<String>>> todos = dao.todos();
        assertEquals(Map.of(1, Map.of("bat", Set.of(), "g", Set.of("12mini", "13mini")), 2, Map.of("bat", Set.of())), todos);
    }

    @Test void deProveedorConsultaSoloLosSuyos() {
        doAnswer(inv -> { ((RowCallbackHandler) inv.getArgument(1)).processRow(fila(7, "lcd", null)); return null; })
                .when(jdbc).query(eq(ProveedorTipoDAO.SQL_TIPOS_DE), any(RowCallbackHandler.class), eq(7));
        doAnswer(inv -> { ((RowCallbackHandler) inv.getArgument(1)).processRow(fila(7, "lcd", "16e")); return null; })
                .when(jdbc).query(eq(ProveedorTipoDAO.SQL_EXCLUIDOS_DE), any(RowCallbackHandler.class), eq(7));
        assertEquals(Map.of("lcd", Set.of("16e")), dao.deProveedor(7));
    }

    @SuppressWarnings("unchecked")
    @Test void reemplazarBorraEInsertaTiposYExcepciones() {
        Map<String, Set<String>> tipos = new TreeMap<>(Map.of("bat", Set.of(), "g", new TreeSet<>(Set.of("12mini", "13mini"))));
        dao.reemplazar(7, tipos);
        verify(jdbc).update(ProveedorTipoDAO.SQL_BORRAR, 7);
        ArgumentCaptor<List<Object[]>> deTipos = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_TIPO), deTipos.capture());
        assertEquals(List.of(List.of(7, "bat"), List.of(7, "g")), deTipos.getValue().stream().map(Arrays::asList).toList());
        ArgumentCaptor<List<Object[]>> deExcluidos = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_EXCLUIDO), deExcluidos.capture());
        assertEquals(List.of(List.of(7, "g", "12mini"), List.of(7, "g", "13mini")), deExcluidos.getValue().stream().map(Arrays::asList).toList());
    }

    @Test void reemplazarSinTiposSoloBorra() {
        dao.reemplazar(7, Map.of());
        verify(jdbc).update(ProveedorTipoDAO.SQL_BORRAR, 7);
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test void reemplazarSinExcepcionesNoLasInserta() {
        dao.reemplazar(7, Map.of("bat", Set.of()));
        verify(jdbc).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_TIPO), anyList());
        verify(jdbc, never()).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_EXCLUIDO), anyList());
    }

    @SuppressWarnings("unchecked")
    @Test void piezasLeeTipoYActivo() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("TIPO")).thenReturn("bati13");
        when(rs.getBoolean("ACTIVO")).thenReturn(false);
        when(jdbc.query(eq(ProveedorTipoDAO.SQL_PIEZAS), any(RowMapper.class))).thenAnswer(inv ->
                List.of(((RowMapper<ProveedorTipoDAO.Pieza>) inv.getArgument(1)).mapRow(rs, 0)));
        assertEquals(List.of(new ProveedorTipoDAO.Pieza("bati13", false)), dao.piezas());
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `mvn -q test -Dtest=ProveedorTipoDAOTest`
Expected: falla la compilación, porque `ProveedorTipoDAO` no existe.

- [ ] **Step 3: Implementación del DAO**

`src/main/java/com/reparaciones/servidor/dao/ProveedorTipoDAO.java`:

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.*;

/** Tipos de pieza que vende cada proveedor y los modelos que no vende de cada uno (0.9.9, spec 2026-10-12 §3). Una fila
 *  de Proveedor_tipo: vende ese tipo; sus filas de Proveedor_tipo_excluido: los modelos de ese tipo que no vende. */
@Repository
public class ProveedorTipoDAO {

    static final String SQL_TIPOS = "SELECT ID_PROV, PREFIJO FROM Proveedor_tipo";
    static final String SQL_EXCLUIDOS = "SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido";
    static final String SQL_TIPOS_DE = "SELECT ID_PROV, PREFIJO FROM Proveedor_tipo WHERE ID_PROV = ?";
    static final String SQL_EXCLUIDOS_DE = "SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido WHERE ID_PROV = ?";
    static final String SQL_BORRAR = "DELETE FROM Proveedor_tipo WHERE ID_PROV = ?";
    static final String SQL_INSERTAR_TIPO = "INSERT INTO Proveedor_tipo (ID_PROV, PREFIJO) VALUES (?, ?)";
    static final String SQL_INSERTAR_EXCLUIDO = "INSERT INTO Proveedor_tipo_excluido (ID_PROV, PREFIJO, MODELO) VALUES (?, ?, ?)";
    static final String SQL_PIEZAS = "SELECT TIPO, ACTIVO FROM Componente WHERE TIPO NOT LIKE 'otro%'";

    /** Una referencia del catálogo (no «otro»), activa o no: de ella salen los tipos que existen y sus modelos. */
    public record Pieza(String tipo, boolean activo) {}

    private final JdbcTemplate jdbc;

    public ProveedorTipoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Proveedor → (prefijo → modelos excluidos) de todos los proveedores con algún tipo. */
    public Map<Integer, Map<String, Set<String>>> todos() {
        Map<Integer, Map<String, Set<String>>> mapa = new TreeMap<>();
        jdbc.query(SQL_TIPOS, (RowCallbackHandler) rs -> mapa.computeIfAbsent(rs.getInt("ID_PROV"), k -> new TreeMap<>())
                .putIfAbsent(rs.getString("PREFIJO"), new TreeSet<>()));
        jdbc.query(SQL_EXCLUIDOS, (RowCallbackHandler) rs -> mapa.computeIfAbsent(rs.getInt("ID_PROV"), k -> new TreeMap<>())
                .computeIfAbsent(rs.getString("PREFIJO"), k -> new TreeSet<>()).add(rs.getString("MODELO")));
        return mapa;
    }

    /** Prefijo → modelos excluidos de un proveedor; vacío si no vende nada. */
    public Map<String, Set<String>> deProveedor(int idProv) {
        Map<String, Set<String>> mapa = new TreeMap<>();
        jdbc.query(SQL_TIPOS_DE, (RowCallbackHandler) rs -> mapa.putIfAbsent(rs.getString("PREFIJO"), new TreeSet<>()), idProv);
        jdbc.query(SQL_EXCLUIDOS_DE, (RowCallbackHandler) rs ->
                mapa.computeIfAbsent(rs.getString("PREFIJO"), k -> new TreeSet<>()).add(rs.getString("MODELO")), idProv);
        return mapa;
    }

    /** Sustituye todo lo del proveedor: borra sus tipos (sus excepciones caen en cascada) e inserta los nuevos. Lo llama
     *  el servicio dentro de su transacción, con el proveedor ya bloqueado. */
    public void reemplazar(int idProv, Map<String, Set<String>> tipos) {
        jdbc.update(SQL_BORRAR, idProv);
        if (tipos.isEmpty()) return;
        jdbc.batchUpdate(SQL_INSERTAR_TIPO, tipos.keySet().stream().map(p -> new Object[]{idProv, p}).toList());
        List<Object[]> excluidos = new ArrayList<>();
        tipos.forEach((p, modelos) -> modelos.forEach(m -> excluidos.add(new Object[]{idProv, p, m})));
        if (!excluidos.isEmpty()) jdbc.batchUpdate(SQL_INSERTAR_EXCLUIDO, excluidos);
    }

    public List<Pieza> piezas() {
        return jdbc.query(SQL_PIEZAS, (rs, n) -> new Pieza(rs.getString("TIPO"), rs.getBoolean("ACTIVO")));
    }
}
```

- [ ] **Step 4: SQL**

`sql/migracion-catalogo-proveedores.sql` (sustituye todo el fichero):

```sql
-- 0.9.9 (ajuste): tipos de pieza que vende cada proveedor (spec docs/superpowers/specs/2026-10-12-v099-piezas-por-tipo-design.md §3).
-- Sustituye la tabla Proveedor_componente de la ampliación del 2026-10-11, que nunca llegó a producción (en
-- preproducción se borra a mano antes: DROP TABLE Proveedor_componente;).
-- Aditivo: el servidor anterior sigue funcionando sobre este esquema (no nombra las tablas).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor, y justo después rellenar-catalogo-proveedores.sql. Antes de
-- aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

-- Una fila: el proveedor vende ese tipo de pieza (prefijo de la referencia: bat, cha, tapa, g, mc, lcd, cam…).
CREATE TABLE Proveedor_tipo (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO),
    CONSTRAINT fk_provtipo_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV) ON DELETE CASCADE
);

-- Modelos de ese tipo que el proveedor NO vende (sin filas = todos los modelos, también los que salgan más adelante).
CREATE TABLE Proveedor_tipo_excluido (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    MODELO  VARCHAR(20) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO, MODELO),
    CONSTRAINT fk_provexcl_tipo FOREIGN KEY (ID_PROV, PREFIJO) REFERENCES Proveedor_tipo (ID_PROV, PREFIJO) ON DELETE CASCADE
);

-- Verificación post: SELECT COUNT(*) FROM Proveedor_tipo;  -- 0 (la rellena rellenar-catalogo-proveedores.sql)
```

`sql/rellenar-catalogo-proveedores.sql` (sustituye todo el fichero):

```sql
-- 0.9.9 (ajuste): rellena los tipos de pieza de cada proveedor con el historial de pedidos (spec 2026-10-12 §3.2).
-- REPETIBLE: INSERT IGNORE, solo añade los tipos que falten. Se ejecuta tras migracion-catalogo-proveedores.sql y otra
-- vez después de desplegar el servidor (recoge los pedidos hechos entre medias).
-- Solo en el despliegue (antes y justo después de arrancar el servidor): más tarde volvería a añadir los tipos
-- quitados a mano en Proveedores. No crea excepciones: cada tipo entra con todos los modelos.
USE gestion_reparaciones;

-- Cada tipo pedido alguna vez (sin cancelados) a un proveedor de componentes. El prefijo se saca como en el servidor
-- (CatalogoPiezas.prefijo): lo que va antes de la primera «i».
INSERT IGNORE INTO Proveedor_tipo (ID_PROV, PREFIJO)
SELECT DISTINCT cc.ID_PROV, SUBSTRING(c.TIPO, 1, LOCATE('i', c.TIPO) - 1)
FROM Compra_componente cc
JOIN Componente c ON c.ID_COM = cc.ID_COM
JOIN Proveedor p ON p.ID_PROV = cc.ID_PROV AND p.TIPO = 'COMPONENTES'
WHERE cc.ESTADO <> 'cancelado' AND c.TIPO NOT LIKE 'otro%' AND LOCATE('i', c.TIPO) > 1;

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS tipos, COUNT(DISTINCT ID_PROV) AS proveedores FROM Proveedor_tipo;
SELECT COUNT(*) AS prefijos_raros FROM Proveedor_tipo pt
 WHERE NOT EXISTS (SELECT 1 FROM Componente c WHERE c.TIPO NOT LIKE 'otro%' AND LOCATE('i', c.TIPO) > 1
                     AND SUBSTRING(c.TIPO, 1, LOCATE('i', c.TIPO) - 1) = pt.PREFIJO);                          -- 0
SELECT COUNT(*) AS excepciones FROM Proveedor_tipo_excluido;                                                  -- 0 en el despliegue
```

En `sql/crear_bd.sql`:
- En la lista de `DROP`, sustituir `DROP TABLE IF EXISTS Proveedor_componente;` por estas dos líneas, en este orden:
  ```sql
  DROP TABLE IF EXISTS Proveedor_tipo_excluido;
  DROP TABLE IF EXISTS Proveedor_tipo;
  ```
- Sustituir el bloque `CREATE TABLE Proveedor_componente (...)` por los dos `CREATE TABLE` de la migración, sin los comentarios de cabecera y en el mismo sitio, tras `Bloque_compra`.

En `docs/schema.md`, sustituir la línea de `Proveedor_componente` por estas dos:

```
- `Proveedor_tipo` (0.9.9: tipos de pieza que vende cada proveedor, por prefijo de la referencia; la usa la previsión por proveedor de «Nuevo pedido»)
- `Proveedor_tipo_excluido` (0.9.9: modelos de cada tipo que el proveedor no vende; sin filas = todos los modelos)
```

- [ ] **Step 5: Comprobar que pasa**

Run: `mvn -q test -Dtest=ProveedorTipoDAOTest && grep -c "Proveedor_componente" sql/crear_bd.sql docs/schema.md sql/migracion-catalogo-proveedores.sql sql/rellenar-catalogo-proveedores.sql`
Expected: el test sale en verde. En el `grep`, `crear_bd.sql` y `docs/schema.md` deben dar **0**. La migración da 2 a propósito: las dos menciones de su comentario de cabecera. El relleno, 0.

- [ ] **Step 6: Commit**

```bash
git add sql/ docs/schema.md src/main/java/com/reparaciones/servidor/dao/ProveedorTipoDAO.java src/test/java/com/reparaciones/servidor/dao/ProveedorTipoDAOTest.java
git commit -m "feat: tablas y dao de los tipos de pieza que vende cada proveedor, con relleno desde el historial"
```

---

### Task 3: Servidor — servicio y endpoints `GET` / `PUT /api/proveedores/{idProv}/piezas`

**Files:**
- Modify (reescribir): `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/ProveedorPiezasService.java`
- Modify (reescribir): `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ProveedorPiezasController.java`
- Modify (reescribir): `src/test/java/com/reparaciones/servidor/service/ProveedorPiezasServiceTest.java`, `src/test/java/com/reparaciones/servidor/controller/ProveedorPiezasControllerTest.java` y `src/test/java/com/reparaciones/servidor/controller/RolesPiezasProveedorTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`: test `elContratoPublicaLasPiezasDeCadaProveedor`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md` y `docs/autorizacion_endpoints.md`

**Interfaces:**
- Consumes:
  - `CatalogoPiezas` (Task 1);
  - `ProveedorTipoDAO` (Task 2);
  - `ProveedorDAO.getById(int)` y `ProveedorDAO.getByIdParaEditar(int)` (`Optional<Proveedor>`; el segundo con `FOR UPDATE`);
  - `ProveedorDAO.TIPO_COMPONENTES`;
  - `LogDAO.insertar(int, String, String)`.
- Produces:
  - `ProveedorPiezasService(ProveedorDAO, ProveedorTipoDAO)`, con:
    - `record Entrada(String prefijo, List<String> excluidos)`;
    - `record TipoProveedor(String prefijo, boolean vende, List<String> modelos, List<String> excluidos)`;
    - `List<TipoProveedor> leer(int idProv)`;
    - `@Transactional String reemplazar(int idProv, List<Entrada> tipos)`: devuelve el resumen del registro;
    - `Map<Integer, List<Integer>> proveedoresPorMaster(List<Componente> piezas)`: la usa la Task 4.
  - `ProveedorPiezasController`:
    - `GET /{idProv}/piezas` → `List<Tipo>`;
    - `PUT /{idProv}/piezas` (204);
    - records `Tipo(String prefijo, boolean vende, List<String> modelos, List<String> excluidos)`, `Request(List<TipoRequest> tipos)` y `TipoRequest(String prefijo, List<String> excluidos)`;
    - en el contrato: `ProveedorPiezasTipo`, `ProveedorPiezasRequest` y `ProveedorPiezasTipoRequest`.
  - Desaparece `PUT /{idProv}/componentes`.

- [ ] **Step 1: Tests que fallan**

`src/test/java/com/reparaciones/servidor/service/ProveedorPiezasServiceTest.java` (sustituye todo el fichero):

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ProveedorTipoDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.service.ProveedorPiezasService.Entrada;
import com.reparaciones.servidor.service.ProveedorPiezasService.TipoProveedor;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tipos de pieza de cada proveedor (spec 2026-10-12 §4.2-§4.3). */
class ProveedorPiezasServiceTest {

    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final ProveedorTipoDAO tiposDao = mock(ProveedorTipoDAO.class);
    private final ProveedorPiezasService servicio = new ProveedorPiezasService(proveedorDao, tiposDao);

    private void existe(int idProv, String tipo) {
        Proveedor p = mock(Proveedor.class);
        when(p.getTipo()).thenReturn(tipo);
        when(proveedorDao.getById(idProv)).thenReturn(Optional.of(p));
        when(proveedorDao.getByIdParaEditar(idProv)).thenReturn(Optional.of(p));
    }

    /** Baterías del 12 y el 13 (activas) y del 11 (desactivada); glass del 14; cámara del 13; una pantalla desactivada. */
    private void catalogo() {
        when(tiposDao.piezas()).thenReturn(List.of(
                new ProveedorTipoDAO.Pieza("bati12", true), new ProveedorTipoDAO.Pieza("bati13", true),
                new ProveedorTipoDAO.Pieza("bati11", false), new ProveedorTipoDAO.Pieza("gi14negra", true),
                new ProveedorTipoDAO.Pieza("cami13", true), new ProveedorTipoDAO.Pieza("lcdi15negraic", false)));
    }

    private static Entrada e(String prefijo, String... excluidos) { return new Entrada(prefijo, List.of(excluidos)); }

    private ResponseStatusException falla422(List<Entrada> tipos, String mensaje) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, tipos));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals(mensaje, ex.getReason());
        verify(tiposDao, never()).reemplazar(anyInt(), any());
        return ex;
    }

    // ── leer ─────────────────────────────────────────────────────────────────────

    @Test void leerDevuelveTodosLosTiposEnElOrdenDelFormulario() {
        existe(7, "COMPONENTES");
        catalogo();
        when(tiposDao.deProveedor(7)).thenReturn(Map.of("bat", Set.of("13"), "lcd", Set.of()));
        List<TipoProveedor> r = servicio.leer(7);
        assertEquals(List.of(
                new TipoProveedor("bat", true, List.of("12", "13"), List.of("13")),
                new TipoProveedor("g", false, List.of("14"), List.of()),
                new TipoProveedor("lcd", true, List.of(), List.of()),          // sin piezas activas: sigue saliendo
                new TipoProveedor("cam", false, List.of("13"), List.of())), r);
        verify(proveedorDao, never()).getByIdParaEditar(anyInt());              // leer no bloquea
    }

    @Test void leerEnsenaLosExcluidosQueYaNoTienenPiezaActiva() {
        existe(7, "COMPONENTES");
        catalogo();
        when(tiposDao.deProveedor(7)).thenReturn(Map.of("bat", Set.of("11")));
        assertEquals(List.of("11", "12", "13"), servicio.leer(7).get(0).modelos());
    }

    @Test void leerProveedorInexistenteEs404YDeOtrosEs422() {
        when(proveedorDao.getById(99)).thenReturn(Optional.empty());
        ResponseStatusException e404 = assertThrows(ResponseStatusException.class, () -> servicio.leer(99));
        assertEquals(404, e404.getStatusCode().value());
        assertEquals("Proveedor no encontrado.", e404.getReason());
        existe(8, "OTROS");
        ResponseStatusException e422 = assertThrows(ResponseStatusException.class, () -> servicio.leer(8));
        assertEquals("Solo los proveedores de componentes tienen piezas.", e422.getReason());
    }

    // ── reemplazar ───────────────────────────────────────────────────────────────

    @Test void reemplazarGuardaConBloqueoYDevuelveElResumenOrdenado() {
        existe(7, "COMPONENTES");
        catalogo();
        String resumen = servicio.reemplazar(7, List.of(e("g"), e("bat", "13", "12mini", "13")));
        assertEquals("bat (sin 12mini, 13), g (todos)", resumen);
        verify(tiposDao).reemplazar(7, Map.of("bat", Set.of("12mini", "13"), "g", Set.of()));
        verify(proveedorDao).getByIdParaEditar(7);
        verify(proveedorDao, never()).getById(anyInt());
    }

    @Test void listaVaciaNoVendeNada() {
        existe(7, "COMPONENTES");
        catalogo();
        assertEquals("ninguna", servicio.reemplazar(7, List.of()));
        verify(tiposDao).reemplazar(7, Map.of());
    }

    @Test void unTipoSinPiezasActivasSePuedeMarcarSinExcepciones() {
        existe(7, "COMPONENTES");
        catalogo();
        assertEquals("lcd (todos)", servicio.reemplazar(7, List.of(e("lcd"))));
    }

    @Test void erroresDeLaPeticion() {
        existe(7, "COMPONENTES");
        catalogo();
        falla422(null, "Falta la lista de tipos.");
        List<Entrada> conNulo = new ArrayList<>();
        conNulo.add(null);
        falla422(conNulo, "Falta la lista de tipos.");
        falla422(List.of(new Entrada("bat", null)), "Falta la lista de modelos excluidos.");
        falla422(List.of(e("pan")), "Algún tipo de pieza no existe.");
        falla422(List.of(new Entrada(null, List.of())), "Algún tipo de pieza no existe.");
        falla422(List.of(e("bat"), e("bat", "12")), "Un tipo de pieza está repetido.");
        falla422(List.of(e("bat", "13x")), "Algún modelo no existe.");
        List<String> modeloNulo = new ArrayList<>();
        modeloNulo.add(null);
        falla422(List.of(new Entrada("bat", modeloNulo)), "Algún modelo no existe.");
        falla422(List.of(e("bat", "12", "13")), "Si no vende ningún modelo de un tipo, desmarca el tipo.");
        falla422(List.of(e("lcd", "15")), "Si no vende ningún modelo de un tipo, desmarca el tipo.");
    }

    @Test void reemplazarProveedorInexistenteEs404YDeOtrosEs422() {
        when(proveedorDao.getByIdParaEditar(99)).thenReturn(Optional.empty());
        ResponseStatusException e404 = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(99, List.of()));
        assertEquals(404, e404.getStatusCode().value());
        existe(8, "OTROS");
        ResponseStatusException e422 = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(8, List.of()));
        assertEquals(422, e422.getStatusCode().value());
        assertEquals("Solo los proveedores de componentes tienen piezas.", e422.getReason());
        verify(tiposDao, never()).reemplazar(anyInt(), any());
    }

    // ── proveedores de cada pieza del listado de Stock ───────────────────────────

    @Test void proveedoresPorMasterUneLosDeLasPiezasDelGrupo() {
        when(tiposDao.todos()).thenReturn(Map.of(
                1, Map.of("cam", Set.of()),
                2, Map.of("cam", Set.of("13")),                     // no vende la del 13, sí la del 13 Pro
                3, Map.of("bat", Set.of())));
        Componente master = new Componente(10, "cami13", null, 0, 0, true, null);
        Componente slave = new Componente(11, "cami13pro", null, 0, 0, true, null);
        slave.setIdComMaster(10);
        Componente desactivada = new Componente(12, "bati12", null, 0, 0, false, null);
        Componente sinTipo = new Componente(13, "flexboton", null, 0, 0, true, null);
        Map<Integer, List<Integer>> r = servicio.proveedoresPorMaster(List.of(master, slave, desactivada, sinTipo));
        assertEquals(List.of(1, 2), r.get(10));
        assertEquals(List.of(3), r.get(12));
        assertEquals(List.of(), r.get(13));
        assertFalse(r.containsKey(11));                                 // el slave va por su master
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/ProveedorPiezasControllerTest.java` (sustituye todo el fichero):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import com.reparaciones.servidor.service.ProveedorPiezasService.Entrada;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProveedorPiezasControllerTest {

    private final ProveedorPiezasService servicio = mock(ProveedorPiezasService.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorPiezasController ctl = new ProveedorPiezasController(servicio, logDao);
    private final UsuarioPrincipal yo = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    @Test void leerDevuelveLosTiposDelServicio() {
        when(servicio.leer(5)).thenReturn(List.of(new ProveedorPiezasService.TipoProveedor("bat", true, List.of("12"), List.of())));
        assertEquals(List.of(new ProveedorPiezasController.Tipo("bat", true, List.of("12"), List.of())), ctl.leer(5));
    }

    @Test void guardarPasaLosTiposYRegistraElResumen() {
        when(servicio.reemplazar(eq(5), any())).thenReturn("bat (todos), g (sin 12mini)");
        ctl.reemplazar(5, new ProveedorPiezasController.Request(List.of(
                new ProveedorPiezasController.TipoRequest("bat", List.of()),
                new ProveedorPiezasController.TipoRequest("g", List.of("12mini")))), yo);
        verify(servicio).reemplazar(5, List.of(new Entrada("bat", List.of()), new Entrada("g", List.of("12mini"))));
        verify(logDao).insertar(7, "EDITAR_PIEZAS_PROVEEDOR", "ID_PROV: 5, PIEZAS: bat (todos), g (sin 12mini)");
    }

    @Test void losNulosLleganTalCualAlServicio() {
        when(servicio.reemplazar(anyInt(), any())).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "x"));
        assertThrows(ResponseStatusException.class, () -> ctl.reemplazar(5, new ProveedorPiezasController.Request(null), yo));
        verify(servicio).reemplazar(5, null);
        List<ProveedorPiezasController.TipoRequest> conNulo = new ArrayList<>();
        conNulo.add(null);
        assertThrows(ResponseStatusException.class, () -> ctl.reemplazar(5, new ProveedorPiezasController.Request(conNulo), yo));
        verify(servicio).reemplazar(eq(5), argThat(l -> l != null && l.size() == 1 && l.get(0) == null));
        verifyNoInteractions(logDao);
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/RolesPiezasProveedorTest.java`: se mantienen la cabecera, las anotaciones, `@MockBean` y `token(...)`, y se sustituyen `put(...)` y los dos tests:

```java
    private ResultActions get(String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/proveedores/5/piezas").header("Authorization", token));
    }

    private ResultActions put(String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/proveedores/5/piezas").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipos\":[{\"prefijo\":\"bat\",\"excluidos\":[\"12mini\"]}]}"));
    }

    @Test void leenElSupertecnicoYElAdminPeroNoElTecnico() throws Exception {
        when(servicio.leer(anyInt())).thenReturn(List.of());
        get(token("TECNICO", 4)).andExpect(status().isForbidden());
        get(token("SUPERTECNICO", 3)).andExpect(status().isOk());
        get(token("ADMIN", null)).andExpect(status().isOk());
        verify(servicio, times(2)).leer(5);
    }

    @Test void tecnicoYAdminReciben403AlGuardar() throws Exception {
        put(token("TECNICO", 4)).andExpect(status().isForbidden());
        put(token("ADMIN", null)).andExpect(status().isForbidden());
        verify(servicio, never()).reemplazar(anyInt(), any());
    }

    @Test void elSupertecnicoGuardaY204() throws Exception {
        when(servicio.reemplazar(anyInt(), any())).thenReturn("bat (sin 12mini)");
        put(token("SUPERTECNICO", 3)).andExpect(status().isNoContent());
        verify(servicio).reemplazar(5, List.of(new ProveedorPiezasService.Entrada("bat", List.of("12mini"))));
    }
```

En `OpenApiContractTest.elContratoPublicaLasPiezasDeCadaProveedor`:
- Sustituir las tres líneas que nombran `/api/proveedores/{idProv}/componentes` e `idsCom` por este bloque.
- Las aserciones de `Componente` (`pendiente`, `proveedores`) se quedan como están.
- Actualizar el comentario del test a «spec 2026-10-11 §4 y 2026-10-12 §4.3».

```java
        JsonNode piezas = paths.path("/api/proveedores/{idProv}/piezas");
        assertTrue(piezas.has("get"), "falta GET /api/proveedores/{idProv}/piezas");
        assertTrue(piezas.has("put"), "falta PUT /api/proveedores/{idProv}/piezas");
        assertFalse(paths.has("/api/proveedores/{idProv}/componentes"), "el PUT por referencias ya no existe");
        assertTrue(refDelCuerpo(paths, "/api/proveedores/{idProv}/piezas", "put").endsWith("/ProveedorPiezasRequest"));
        assertNoNullable(esquemas, "ProveedorPiezasRequest", "tipos");
        assertNoNullable(esquemas, "ProveedorPiezasTipoRequest", "prefijo", "excluidos");
        assertNoNullable(esquemas, "ProveedorPiezasTipo", "prefijo", "vende", "modelos", "excluidos");
        assertTrue(piezas.path("get").path("responses").path("200").toString().contains("/ProveedorPiezasTipo\""),
                "el GET devuelve una lista de ProveedorPiezasTipo");
```

- [ ] **Step 2: Comprobar que fallan**

Run: `mvn -q test -Dtest='ProveedorPiezasServiceTest,ProveedorPiezasControllerTest'`
Expected: falla la compilación, porque `Entrada`, `TipoProveedor`, `leer`, `Tipo` y `TipoRequest` no existen.

- [ ] **Step 3: Implementación**

`src/main/java/com/reparaciones/servidor/service/ProveedorPiezasService.java` (sustituye todo el fichero):

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ProveedorTipoDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.util.CatalogoPiezas;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Tipos de pieza que vende cada proveedor (0.9.9, spec 2026-10-12 §4): leerlos con los modelos de cada tipo,
 *  sustituirlos (todo o nada) y traducirlos a la lista de proveedores de cada pieza del listado de Stock. */
@Service
public class ProveedorPiezasService {

    public static final String MSG_NO_ENCONTRADO = "Proveedor no encontrado.";
    public static final String MSG_SOLO_COMPONENTES = "Solo los proveedores de componentes tienen piezas.";
    public static final String MSG_SIN_TIPOS = "Falta la lista de tipos.";
    public static final String MSG_SIN_EXCLUIDOS = "Falta la lista de modelos excluidos.";
    public static final String MSG_TIPO_NO_EXISTE = "Algún tipo de pieza no existe.";
    public static final String MSG_TIPO_REPETIDO = "Un tipo de pieza está repetido.";
    public static final String MSG_MODELO_NO_EXISTE = "Algún modelo no existe.";
    public static final String MSG_NINGUN_MODELO = "Si no vende ningún modelo de un tipo, desmarca el tipo.";

    /** Un tipo que vende el proveedor, con los modelos que no vende. */
    public record Entrada(String prefijo, List<String> excluidos) {}

    /** Un tipo que existe, con sus modelos (los de sus piezas activas y los excluidos) y si el proveedor lo vende. */
    public record TipoProveedor(String prefijo, boolean vende, List<String> modelos, List<String> excluidos) {}

    /** Tipos que existen (en su orden) y modelos de las piezas activas de cada uno. */
    private record Catalogo(List<String> tipos, Map<String, Set<String>> modelosActivos) {}

    private final ProveedorDAO proveedorDao;
    private final ProveedorTipoDAO tiposDao;

    public ProveedorPiezasService(ProveedorDAO proveedorDao, ProveedorTipoDAO tiposDao) {
        this.proveedorDao = proveedorDao;
        this.tiposDao = tiposDao;
    }

    public List<TipoProveedor> leer(int idProv) {
        comprobar(proveedorDao.getById(idProv));
        Catalogo cat = catalogo();
        Map<String, Set<String>> suyos = tiposDao.deProveedor(idProv);
        return cat.tipos().stream().map(p -> {
            Set<String> excluidos = suyos.get(p);
            Set<String> modelos = new HashSet<>(cat.modelosActivos().getOrDefault(p, Set.of()));
            if (excluidos != null) modelos.addAll(excluidos);
            return new TipoProveedor(p, excluidos != null, CatalogoPiezas.ordenarModelos(modelos),
                    excluidos == null ? List.of() : CatalogoPiezas.ordenarModelos(excluidos));
        }).toList();
    }

    /** Sustituye los tipos del proveedor con su fila bloqueada (FOR UPDATE): si dos guardan a la vez, gana el último.
     *  Devuelve el resumen para el registro: «bat (todos), g (sin 12mini, 13mini)» o «ninguna». */
    @Transactional
    public String reemplazar(int idProv, List<Entrada> tipos) {
        comprobar(proveedorDao.getByIdParaEditar(idProv));
        // Sin contains(null): en una lista inmutable (List.of) lanza NullPointerException.
        if (tipos == null || tipos.stream().anyMatch(Objects::isNull)) throw regla(MSG_SIN_TIPOS);
        Catalogo cat = catalogo();
        Map<String, Set<String>> nuevos = new TreeMap<>();
        for (Entrada e : tipos) {
            if (e.excluidos() == null) throw regla(MSG_SIN_EXCLUIDOS);
            if (e.prefijo() == null || !cat.tipos().contains(e.prefijo())) throw regla(MSG_TIPO_NO_EXISTE);
            if (nuevos.containsKey(e.prefijo())) throw regla(MSG_TIPO_REPETIDO);
            if (e.excluidos().stream().anyMatch(m -> m == null || !CatalogoPiezas.esModelo(m))) throw regla(MSG_MODELO_NO_EXISTE);
            Set<String> excluidos = new TreeSet<>(e.excluidos());
            Set<String> activos = cat.modelosActivos().getOrDefault(e.prefijo(), Set.of());
            if (!excluidos.isEmpty() && excluidos.containsAll(activos)) throw regla(MSG_NINGUN_MODELO);
            nuevos.put(e.prefijo(), excluidos);
        }
        tiposDao.reemplazar(idProv, nuevos);
        return resumen(nuevos);
    }

    /** Pieza master → proveedores que venden alguna pieza de su grupo (spec §4.2), de menor a mayor. Las piezas de un
     *  grupo comparten lista; un slave no tiene entrada propia. */
    public Map<Integer, List<Integer>> proveedoresPorMaster(List<Componente> piezas) {
        Map<Integer, Map<String, Set<String>>> reglas = tiposDao.todos();
        Map<Integer, Set<Integer>> porMaster = new HashMap<>();
        for (Componente c : piezas) {
            int master = c.getIdComMaster() != null ? c.getIdComMaster() : c.getIdCom();
            Set<Integer> provs = porMaster.computeIfAbsent(master, k -> new TreeSet<>());
            reglas.forEach((idProv, suyos) -> {
                if (CatalogoPiezas.vende(suyos, c.getTipo())) provs.add(idProv);
            });
        }
        Map<Integer, List<Integer>> r = new HashMap<>();
        porMaster.forEach((master, provs) -> r.put(master, List.copyOf(provs)));
        return r;
    }

    private Catalogo catalogo() {
        Set<String> tipos = new HashSet<>();
        Map<String, Set<String>> modelos = new HashMap<>();
        for (ProveedorTipoDAO.Pieza pieza : tiposDao.piezas()) {
            String p = CatalogoPiezas.prefijo(pieza.tipo());
            tipos.add(p);
            if (!pieza.activo()) continue;
            String m = CatalogoPiezas.modelo(pieza.tipo(), p);
            if (m != null) modelos.computeIfAbsent(p, k -> new HashSet<>()).add(m);
        }
        return new Catalogo(CatalogoPiezas.ordenarTipos(tipos), modelos);
    }

    private static String resumen(Map<String, Set<String>> tipos) {
        if (tipos.isEmpty()) return "ninguna";
        return CatalogoPiezas.ordenarTipos(tipos.keySet()).stream().map(p -> {
            Set<String> excluidos = tipos.get(p);
            return p + " (" + (excluidos.isEmpty() ? "todos" : "sin " + String.join(", ", CatalogoPiezas.ordenarModelos(excluidos))) + ")";
        }).collect(Collectors.joining(", "));
    }

    private static void comprobar(Optional<Proveedor> proveedor) {
        Proveedor p = proveedor.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MSG_NO_ENCONTRADO));
        if (!ProveedorDAO.TIPO_COMPONENTES.equals(p.getTipo())) throw regla(MSG_SOLO_COMPONENTES);
    }

    private static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
```

`src/main/java/com/reparaciones/servidor/controller/ProveedorPiezasController.java` (sustituye todo el fichero):

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

/** Tipos de pieza que vende un proveedor (0.9.9, spec 2026-10-12 §4.3). El registro va tras la transacción del
 *  servicio. */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorPiezasController {

    private final ProveedorPiezasService servicio;
    private final LogDAO logDao;

    public ProveedorPiezasController(ProveedorPiezasService servicio, LogDAO logDao) {
        this.servicio = servicio;
        this.logDao = logDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/{idProv}/piezas")
    public List<Tipo> leer(@PathVariable int idProv) {
        return servicio.leer(idProv).stream().map(t -> new Tipo(t.prefijo(), t.vende(), t.modelos(), t.excluidos())).toList();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idProv}/piezas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reemplazar(@PathVariable int idProv, @RequestBody Request req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        // Los nulos llegan tal cual al servicio, que responde 422 (Stream.toList admite nulos).
        List<ProveedorPiezasService.Entrada> tipos = req.tipos() == null ? null : req.tipos().stream()
                .map(t -> t == null ? null : new ProveedorPiezasService.Entrada(t.prefijo(), t.excluidos())).toList();
        String resumen = servicio.reemplazar(idProv, tipos);
        logDao.insertar(principal.getIdUsu(), "EDITAR_PIEZAS_PROVEEDOR", "ID_PROV: " + idProv + ", PIEZAS: " + resumen);
    }

    /** Un tipo que existe: sus modelos (en el orden de la tienda), si el proveedor lo vende y qué modelos excluye. */
    record Tipo(String prefijo, boolean vende, List<String> modelos, List<String> excluidos) {}

    record Request(List<TipoRequest> tipos) {}

    record TipoRequest(String prefijo, List<String> excluidos) {}
}
```

`docs/api_contract.md`: sustituir la viñeta «Piezas de cada proveedor (0.9.9): `PUT /api/proveedores/{idProv}/componentes` …» entera (hasta «…son nulos para TECNICO.») por:

```
- Tipos de pieza de cada proveedor (0.9.9, spec 2026-10-12): `GET /api/proveedores/{idProv}/piezas` (SUPERTECNICO y
  ADMIN) devuelve todos los tipos que existen (prefijo de las referencias no «otro», activas o no; orden bat, cha,
  tapa, g, mc, lcd y el resto alfabético) como `[{prefijo, vende, modelos, excluidos}]`: `modelos` = los de sus piezas
  activas más los excluidos, en el orden de la tienda. `PUT /api/proveedores/{idProv}/piezas` (solo SUPERTECNICO) con
  `{ "tipos": [{ "prefijo": "bat", "excluidos": ["12mini"] }] }` sustituye todo (sin excepciones = todos los modelos,
  también los futuros; lista vacía = no vende nada) y responde `204`. `404` «Proveedor no encontrado.»; `422` «Solo los
  proveedores de componentes tienen piezas.», «Falta la lista de tipos.», «Falta la lista de modelos excluidos.»,
  «Algún tipo de pieza no existe.», «Un tipo de pieza está repetido.», «Algún modelo no existe.» o «Si no vende ningún
  modelo de un tipo, desmarca el tipo.» (sin cambiar nada). Registra `EDITAR_PIEZAS_PROVEEDOR` con
  `ID_PROV: <id>, PIEZAS: bat (todos), g (sin 12mini, 13mini)` («ninguna» sin tipos). En `Componente`
  (`GET /api/componentes/gestionados`), `pendiente` (unidades en pedidos pendientes) y `proveedores` (ids de los
  proveedores que venden alguna pieza de su grupo) son nulos para TECNICO.
```

`docs/autorizacion_endpoints.md`:
- Línea 37: sustituir `piezas que vende cada proveedor (`PUT /api/proveedores/{idProv}/componentes`, 0.9.9)` por `tipos de pieza que vende cada proveedor (`PUT /api/proveedores/{idProv}/piezas`, 0.9.9; el `GET` también lo lee ADMIN)`.
- Línea 67: el texto pasa a «Roles del GET y el PUT de las piezas de un proveedor (cadena de seguridad real, MockMvc)».

- [ ] **Step 4: Comprobar que pasan**

Run: `mvn -q test -Dtest='ProveedorPiezasServiceTest,ProveedorPiezasControllerTest,RolesPiezasProveedorTest,OpenApiContractTest'`
Expected: todo verde.

- [ ] **Step 5: Commit**

```bash
git add -A src/ docs/
git commit -m "feat: get y put de los tipos de pieza que vende cada proveedor con registro y contrato"
```

---

### Task 4: Servidor — Stock con las reglas nuevas; fuera la tabla por referencias

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ComponenteController.java`
- Delete: `src/main/java/com/reparaciones/servidor/dao/ProveedorComponenteDAO.java` y `src/test/java/com/reparaciones/servidor/dao/ProveedorComponenteDAOTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/ComponenteControllerPrevisionTest.java`, `ComponenteControllerAutoPedidoTest.java`, `ComponenteControllerEliminarTest.java` y `ComponenteControllerValidacionTest.java`: el cuarto argumento del constructor

**Interfaces:**
- Consumes: `ProveedorPiezasService.proveedoresPorMaster(List<Componente>)` (Task 3).
- Produces: `ComponenteController(ComponenteDAO, LogDAO, PrevisionPedidoService, ProveedorPiezasService)`. `getAllGestionados` rellena `proveedores` con las reglas. La forma no cambia.

- [ ] **Step 1: Test que falla**

En `ComponenteControllerPrevisionTest`:
- Cambiar el import `ProveedorComponenteDAO` por `com.reparaciones.servidor.service.ProveedorPiezasService`.
- Sustituir `private final ProveedorComponenteDAO catalogo = mock(ProveedorComponenteDAO.class);` por `private final ProveedorPiezasService catalogo = mock(ProveedorPiezasService.class);`. El nombre del campo se queda.
- En `alSupertecnicoCadaPiezaLlevaLosProveedoresDeSuMaster`, cambiar la línea del `when`:

```java
        when(catalogo.proveedoresPorMaster(List.of(master, slave, sinLista))).thenReturn(Map.of(2, List.of(1, 3)));
```

- En los otros tres ficheros, cambiar el cuarto argumento a `mock(com.reparaciones.servidor.service.ProveedorPiezasService.class)`:

```java
mock(com.reparaciones.servidor.dao.ProveedorComponenteDAO.class)
```

- [ ] **Step 2: Comprobar que falla**

Run: `mvn -q test -Dtest='ComponenteController*Test'`
Expected: falla la compilación, porque el constructor aún pide `ProveedorComponenteDAO`.

- [ ] **Step 3: Implementación**

En `ComponenteController`:
- Cambiar el import `com.reparaciones.servidor.dao.ProveedorComponenteDAO` por `com.reparaciones.servidor.service.ProveedorPiezasService`.
- Campo y constructor quedan así:

```java
    private final ComponenteDAO          dao;
    private final LogDAO                 logDao;
    private final PrevisionPedidoService prevision;
    private final ProveedorPiezasService catalogo;

    public ComponenteController(ComponenteDAO dao, LogDAO logDao, PrevisionPedidoService prevision,
                                ProveedorPiezasService catalogo) {
        this.dao       = dao;
        this.logDao    = logDao;
        this.prevision = prevision;
        this.catalogo  = catalogo;
    }
```

En `getAllGestionados`, cambiar la llamada:

```java
            Map<Integer, List<Integer>> porMaster = catalogo.proveedoresPorMaster(lista);
            lista.forEach(c -> c.setProveedores(porMaster.getOrDefault(
                    c.getIdComMaster() != null ? c.getIdComMaster() : c.getIdCom(), List.of())));
```

En su Javadoc, añadir al final de la lista de specs: «, spec 2026-10-12 §4.2».

Borrar los dos ficheros:

```bash
git rm src/main/java/com/reparaciones/servidor/dao/ProveedorComponenteDAO.java src/test/java/com/reparaciones/servidor/dao/ProveedorComponenteDAOTest.java
```

- [ ] **Step 4: Suite entera y contrato**

Run: `mvn -q test 2>&1 | tail -20; grep -rn "ProveedorComponenteDAO\|Proveedor_componente\|/componentes\"" src/ | head; grep -c "ProveedorPiezasTipo" target/openapi.json`
Expected:
- todo verde (en el resumen de surefire, `Tests run: …, Failures: 0, Errors: 0`);
- el `grep` de `src/` no saca nada;
- `target/openapi.json` nombra `ProveedorPiezasTipo` (≥ 1).

- [ ] **Step 5: Commit**

```bash
git add -A src/
git commit -m "feat: el listado de stock saca los proveedores de cada pieza de los tipos y fuera la lista por referencias"
```

---

### Task 5: Web — nombres compartidos y funciones puras del diálogo

**Files:**
- Create: `gestion-reparaciones-web/src/shared/lib/modelos.ts`, donde va `traducirModelo` desde `taller/lib/modelos.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/modelos.ts`, que lo reexporta
- Create: `gestion-reparaciones-web/src/shared/lib/tiposPieza.ts` y `src/shared/lib/tiposPieza.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/piezas.ts`: `ETIQUETAS` sale de `NOMBRES_TIPO_PIEZA`
- Create: `gestion-reparaciones-web/src/modules/almacen/proveedores/piezasPorTipo.ts` y `piezasPorTipo.test.ts`

**Interfaces:**
- Produces:
  - **`@/shared/lib/modelos`:** `traducirModelo(codigo: string | null | undefined): string`. El código no cambia. `taller/lib/modelos.ts` lo reexporta, así que los imports de taller siguen igual.
  - **`@/shared/lib/tiposPieza`:** `NOMBRES_TIPO_PIEZA` (objeto `as const`) y `nombreTipoPieza(prefijo: string): string`. Un prefijo desconocido devuelve el propio prefijo.
  - **`proveedores/piezasPorTipo.ts`:**
    - tipos:
      - `TipoPiezas = { prefijo: string; vende: boolean; modelos: string[]; excluidos: string[] }`, con la misma forma que el `ProveedorPiezasTipo` del contrato;
      - `EstadoTipos = ReadonlyMap<string, ReadonlySet<string>>` (prefijo → excluidos; solo los tipos que vende);
      - `CuerpoPiezas = { tipos: { prefijo: string; excluidos: string[] }[] }`;
    - textos: `AYUDA_PIEZAS`;
    - funciones:
      - `estadoInicial(tipos)`, `marcarTipo(estado, prefijo, vende)`, `fijarVendidos(estado, tipo, vendidos)` y `vendidos(tipo, excluidos)`;
      - `textoBoton(tipo, excluidos)`, `textoSinModelos(tipo, excluidos)` y `textoRecuento(estado, total)`;
      - `cuerpoGuardar(tipos, estado)`.

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git status --short && git switch -c feature/piezas-por-tipo main && git log --oneline -1
```
Expected: árbol limpio; `4830969 merge: en camino en azul (0.9.9)`.

- [ ] **Step 1: Tests que fallan**

`src/shared/lib/tiposPieza.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { nombreTipoPieza } from './tiposPieza'

describe('nombreTipoPieza (spec 2026-10-12 §5.1)', () => {
  it('nombra los siete tipos y deja el prefijo desconocido tal cual', () => {
    expect(['bat', 'cha', 'tapa', 'g', 'mc', 'lcd', 'cam'].map(nombreTipoPieza))
      .toEqual(['Batería', 'Chasis', 'Tapa trasera', 'Glass', 'Marco', 'Pantalla', 'Cámara'])
    expect(nombreTipoPieza('flex')).toBe('flex')
  })
})
```

`src/modules/almacen/proveedores/piezasPorTipo.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { cuerpoGuardar, estadoInicial, fijarVendidos, marcarTipo, textoBoton, textoRecuento, textoSinModelos, vendidos, type TipoPiezas } from './piezasPorTipo'

const BAT: TipoPiezas = { prefijo: 'bat', vende: true, modelos: ['12', '13', '14'], excluidos: [] }
const G: TipoPiezas = { prefijo: 'g', vende: true, modelos: ['12', '12mini', '13', '13mini'], excluidos: ['12mini', '13mini'] }
const LCD: TipoPiezas = { prefijo: 'lcd', vende: false, modelos: ['13', '14'], excluidos: [] }
const TIPOS = [BAT, G, LCD]

describe('piezasPorTipo (spec 2026-10-12 §5.1)', () => {
  it('el estado inicial tiene solo los tipos que vende, con sus excluidos', () => {
    const e = estadoInicial(TIPOS)
    expect([...e.keys()]).toEqual(['bat', 'g'])
    expect([...(e.get('g') ?? [])]).toEqual(['12mini', '13mini'])
    expect(textoRecuento(e, TIPOS.length)).toBe('Vende 2 de 3 tipos')
  })

  it('marcar un tipo empieza con todos los modelos; desmarcarlo lo quita', () => {
    const e = marcarTipo(estadoInicial(TIPOS), 'lcd', true)
    expect(textoBoton(LCD, e.get('lcd'))).toBe('Todos los modelos')
    expect(marcarTipo(e, 'lcd', false).has('lcd')).toBe(false)
  })

  it('textos del botón y de los que faltan', () => {
    const e = estadoInicial(TIPOS)
    expect(textoBoton(BAT, e.get('bat'))).toBe('Todos los modelos')
    expect(textoBoton(G, e.get('g'))).toBe('Seleccionados 2 de 4')
    expect(textoBoton(LCD, e.get('lcd'))).toBe('No lo vende')
    expect(textoSinModelos(G, e.get('g'))).toBe('Sin iPhone 12 Mini · iPhone 13 Mini')
    expect(textoSinModelos(BAT, e.get('bat'))).toBe('')
    expect(textoSinModelos(LCD, undefined)).toBe('')
  })

  it('los marcados del desplegable son los no excluidos; sin el tipo, ninguno', () => {
    const e = estadoInicial(TIPOS)
    expect([...vendidos(G, e.get('g'))]).toEqual(['12', '13'])
    expect([...vendidos(LCD, undefined)]).toEqual([])
  })

  it('fijar los vendidos excluye el resto; todos marcados = sin excepciones; ninguno = el tipo fuera', () => {
    const e = estadoInicial(TIPOS)
    const uno = fijarVendidos(e, BAT, new Set(['12', '14']))
    expect([...(uno.get('bat') ?? [])]).toEqual(['13'])
    expect(fijarVendidos(uno, BAT, new Set(BAT.modelos)).get('bat')?.size).toBe(0)
    expect(fijarVendidos(uno, BAT, new Set()).has('bat')).toBe(false)
  })

  it('el cuerpo va en el orden del servidor, con los excluidos en el orden de los modelos', () => {
    const e = fijarVendidos(marcarTipo(estadoInicial(TIPOS), 'lcd', true), G, new Set(['12']))
    expect(cuerpoGuardar(TIPOS, e)).toEqual({ tipos: [
      { prefijo: 'bat', excluidos: [] },
      { prefijo: 'g', excluidos: ['12mini', '13', '13mini'] },
      { prefijo: 'lcd', excluidos: [] },
    ] })
  })
})
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/shared/lib/tiposPieza.test.ts src/modules/almacen/proveedores/piezasPorTipo.test.ts`
Expected: fallan porque los módulos no existen.

- [ ] **Step 3: Implementación**

`src/shared/lib/modelos.ts`:
1. Mover tal cual, desde `src/modules/taller/lib/modelos.ts`, las constantes `ESPECIALES` y `SUFIJOS` y la función `traducirModelo`, con su comentario.
2. Añadir arriba: `/** Nombre de un modelo para pantalla (traducirModelo del JavaFX): compartido por taller y almacén. */`.

En `src/modules/taller/lib/modelos.ts`, borrar esas tres piezas y añadir, tras los imports:

```ts
// traducirModelo vive en shared (lo usa también el diálogo de Proveedores de almacén): se reexporta para taller.
export { traducirModelo } from '@/shared/lib/modelos'
```

En el mismo fichero, sustituir el comentario de `MODELOS_ORDENADOS` por este (spec §4.1: aviso en los dos sitios):

```ts
/** Catálogo de modelos en orden de tienda Apple (calco de FormularioReparacionController.MODELOS_ORDENADOS). Está
 *  también en el servidor (CatalogoPiezas.MODELOS_ORDENADOS, 0.9.9): un modelo nuevo se añade en los DOS sitios. */
```

`src/shared/lib/tiposPieza.ts`:

```ts
/** Nombre de cada tipo de pieza por el prefijo de su referencia (spec 2026-10-12 §5.1; mismo criterio que
 *  categoriaPieza de taller). */
export const NOMBRES_TIPO_PIEZA = {
  bat: 'Batería', cha: 'Chasis', tapa: 'Tapa trasera', g: 'Glass', mc: 'Marco', lcd: 'Pantalla', cam: 'Cámara',
} as const

/** El nombre del tipo; un prefijo desconocido se enseña tal cual. */
export function nombreTipoPieza(prefijo: string): string {
  return (NOMBRES_TIPO_PIEZA as Record<string, string>)[prefijo] ?? prefijo
}
```

En `src/modules/taller/lib/piezas.ts`:
- Añadir `import { NOMBRES_TIPO_PIEZA } from '@/shared/lib/tiposPieza'`.
- Sustituir el literal de `ETIQUETAS` por `{ ...NOMBRES_TIPO_PIEZA, otro: 'Otros' }`. El tipo `Record<(typeof PREFIJOS)[number], string>` se queda.

`src/modules/almacen/proveedores/piezasPorTipo.ts`:

```ts
import { traducirModelo } from '@/shared/lib/modelos'

/** Un tipo de pieza tal como lo devuelve GET /api/proveedores/{id}/piezas (ProveedorPiezasTipo del contrato). */
export type TipoPiezas = { prefijo: string; vende: boolean; modelos: string[]; excluidos: string[] }
/** Lo que el diálogo lleva marcado: prefijo → modelos excluidos (vacío = todos). Los tipos que no vende no están. */
export type EstadoTipos = ReadonlyMap<string, ReadonlySet<string>>
/** Cuerpo del PUT /api/proveedores/{id}/piezas. */
export type CuerpoPiezas = { tipos: { prefijo: string; excluidos: string[] }[] }

export const AYUDA_PIEZAS =
  'Marca los tipos de pieza que vende. De entrada, de todos los modelos (también los que salgan más adelante); si alguno no lo vende, desmárcalo en el desplegable.'

export function estadoInicial(tipos: TipoPiezas[]): EstadoTipos {
  return new Map(tipos.filter((t) => t.vende).map((t) => [t.prefijo, new Set(t.excluidos)]))
}

/** Marcar un tipo empieza con todos los modelos; desmarcarlo lo quita. */
export function marcarTipo(estado: EstadoTipos, prefijo: string, vende: boolean): EstadoTipos {
  const nuevo = new Map(estado)
  if (vende) nuevo.set(prefijo, new Set())
  else nuevo.delete(prefijo)
  return nuevo
}

/** Los modelos marcados en el desplegable: el resto queda excluido. Sin ninguno marcado, el tipo deja de venderse. */
export function fijarVendidos(estado: EstadoTipos, tipo: TipoPiezas, marcados: ReadonlySet<string>): EstadoTipos {
  const nuevo = new Map(estado)
  const excluidos = new Set(tipo.modelos.filter((m) => !marcados.has(m)))
  if (tipo.modelos.length > 0 && excluidos.size === tipo.modelos.length) nuevo.delete(tipo.prefijo)
  else nuevo.set(tipo.prefijo, excluidos)
  return nuevo
}

/** Los modelos que salen marcados en el desplegable (los no excluidos); ninguno si no vende el tipo. */
export function vendidos(tipo: TipoPiezas, excluidos: ReadonlySet<string> | undefined): Set<string> {
  return new Set(excluidos === undefined ? [] : tipo.modelos.filter((m) => !excluidos.has(m)))
}

export function textoBoton(tipo: TipoPiezas, excluidos: ReadonlySet<string> | undefined): string {
  if (excluidos === undefined) return 'No lo vende'
  const fuera = tipo.modelos.filter((m) => excluidos.has(m)).length
  if (fuera === 0) return 'Todos los modelos'
  return `Seleccionados ${tipo.modelos.length - fuera} de ${tipo.modelos.length}`
}

/** «Sin iPhone 12 Mini · iPhone 13 Mini»: los que faltan, en el orden de los modelos; vacío si no falta ninguno. */
export function textoSinModelos(tipo: TipoPiezas, excluidos: ReadonlySet<string> | undefined): string {
  if (excluidos === undefined) return ''
  const fuera = tipo.modelos.filter((m) => excluidos.has(m))
  return fuera.length === 0 ? '' : `Sin ${fuera.map(traducirModelo).join(' · ')}`
}

export function textoRecuento(estado: EstadoTipos, total: number): string {
  return `Vende ${estado.size} de ${total} tipos`
}

/** El PUT entero: los tipos que vende, en el orden del servidor, con sus excluidos en el orden de los modelos. */
export function cuerpoGuardar(tipos: TipoPiezas[], estado: EstadoTipos): CuerpoPiezas {
  return {
    tipos: tipos.flatMap((t) => {
      const excluidos = estado.get(t.prefijo)
      return excluidos === undefined ? [] : [{ prefijo: t.prefijo, excluidos: t.modelos.filter((m) => excluidos.has(m)) }]
    }),
  }
}
```

- [ ] **Step 4: Comprobar que pasan**

Run, de uno en uno:
```bash
npx vitest run src/shared/lib/tiposPieza.test.ts src/modules/almacen/proveedores/piezasPorTipo.test.ts
npx vitest run src/modules/taller/lib
npx tsc -b && npm run lint
```
Expected: todo verde. Los tests de `taller/lib` (modelos y piezas) siguen pasando sin tocarlos.

- [ ] **Step 5: Commit**

```bash
git add src/shared/lib/modelos.ts src/shared/lib/tiposPieza.ts src/shared/lib/tiposPieza.test.ts src/modules/taller/lib/modelos.ts src/modules/taller/lib/piezas.ts src/modules/almacen/proveedores/piezasPorTipo.ts src/modules/almacen/proveedores/piezasPorTipo.test.ts
git commit -m "feat: nombres compartidos de tipos y modelos y funciones del dialogo de piezas por tipo"
```

---

### Task 6: Web — `MultiSelect` con texto fijo, «Todos» y deshabilitado

**Files:**
- Modify: `gestion-reparaciones-web/src/shared/ui/MultiSelect.tsx`
- Test: `gestion-reparaciones-web/src/shared/ui/MultiSelect.test.tsx`

**Interfaces:**
- Produces: cuatro props nuevas de `MultiSelect`, todas opcionales. Sin ellas se comporta igual que hoy.
  - `texto?: string`: texto fijo del botón, en vez del resumen de la selección.
  - `opcionTodas?: string`: opción de arriba, separada por una línea, que marca todas las opciones. Sale marcada cuando todas lo están.
  - `deshabilitado?: boolean`: botón deshabilitado, con el gris de lo inactivo.
  - `etiquetaAccesible?: string`: `aria-label` del botón.

- [ ] **Step 1: Tests que fallan**

Añadir al `describe('MultiSelect', …)` de `src/shared/ui/MultiSelect.test.tsx`:

```tsx
  it('texto fijo, etiqueta accesible y «Todos» arriba que marca todas', async () => {
    const onChange = vi.fn()
    render(<MultiSelect opciones={['12', '13', '14']} clave={(o) => o} etiqueta={(o) => `iPhone ${o}`} seleccion={new Set(['12'])} onChange={onChange}
      textoVacio="No lo vende" textoPlural={(n) => `${n} modelos`} texto="Seleccionados 1 de 3" opcionTodas="Todos los modelos" etiquetaAccesible="Modelos de Batería" />)
    const boton = screen.getByRole('button', { name: 'Modelos de Batería' })
    expect(boton).toHaveTextContent('Seleccionados 1 de 3')
    await userEvent.click(boton)
    const todos = screen.getByRole('checkbox', { name: 'Todos los modelos' })
    expect(todos).not.toBeChecked()
    expect(screen.getByRole('separator').previousElementSibling).toHaveTextContent('Todos los modelos')
    await userEvent.click(todos)
    expect(onChange).toHaveBeenCalledWith(new Set(['12', '13', '14']))
  })

  it('«Todos» sale marcado con todas marcadas', async () => {
    render(<MultiSelect opciones={['12', '13']} clave={(o) => o} etiqueta={(o) => o} seleccion={new Set(['12', '13'])} onChange={vi.fn()}
      textoVacio="x" textoPlural={(n) => `${n}`} opcionTodas="Todos los modelos" />)
    await userEvent.click(screen.getByRole('button'))
    expect(screen.getByRole('checkbox', { name: 'Todos los modelos' })).toBeChecked()
  })

  it('deshabilitado no abre y lleva el gris de lo inactivo', async () => {
    render(<MultiSelect opciones={['12']} clave={(o) => o} etiqueta={(o) => o} seleccion={new Set()} onChange={vi.fn()}
      textoVacio="No lo vende" textoPlural={(n) => `${n}`} deshabilitado />)
    const boton = screen.getByRole('button', { name: 'No lo vende' })
    expect(boton).toBeDisabled()
    expect(boton).toHaveClass('disabled:bg-pill-bg', 'disabled:text-texto-sub')
    await userEvent.click(boton)
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/shared/ui/MultiSelect.test.tsx`
Expected: fallan los tres tests nuevos. TypeScript también se queja de las props, que aún no existen.

- [ ] **Step 3: Implementación**

En `src/shared/ui/MultiSelect.tsx`, añadir a `Props<T>`:

```ts
  /** Texto fijo del botón en lugar del resumen de la selección (0.9.9: «Seleccionados K de M»). */
  texto?: string
  /** Opción de arriba, separada por una línea, que marca todas las opciones; sale marcada si ya lo están todas
   *  (0.9.9: «Todos los modelos»). */
  opcionTodas?: string
  /** Botón deshabilitado, con el gris de lo inactivo. */
  deshabilitado?: boolean
  /** Nombre accesible del botón cuando su texto no lo distingue de otros desplegables iguales. */
  etiquetaAccesible?: string
```

En el componente:
- añadirlas a la desestructuración;
- calcular `const textoBoton = texto ?? textoMultiSelect(nombres, textoVacio, textoPlural, opciones.length, textoTodas)` y `const todas = opciones.length > 0 && opciones.every((o) => seleccion.has(clave(o)))`;
- cambiar el trigger y el principio del contenido:

```tsx
      <PopoverTrigger
        disabled={deshabilitado}
        aria-label={etiquetaAccesible}
        className={cn(
          'flex h-10 min-w-[200px] items-center justify-between rounded-3xl bg-azul-noche px-4 text-[12px] font-bold text-texto-nav-activo hover:bg-azul-noche-hover',
          'disabled:cursor-default disabled:bg-pill-bg disabled:text-texto-sub disabled:hover:bg-pill-bg',
          className,
        )}
      >
        {textoBoton}
        <ChevronDown aria-hidden="true" className="size-4" />
      </PopoverTrigger>
      <PopoverContent align="start" className="max-h-72 w-56 overflow-auto rounded-lg border border-fila-sep bg-superficie p-2 shadow-md">
        {opcionTodas !== undefined && (
          <>
            <label htmlFor={`${idBase}-todas`} className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-[12px] font-bold hover:bg-pill-bg">
              <Checkbox id={`${idBase}-todas`} checked={todas} onCheckedChange={() => onChange(new Set(opciones.map(clave)))} aria-label={opcionTodas} />
              {opcionTodas}
            </label>
            <div role="separator" className="my-1 border-t border-fila-sep" />
          </>
        )}
        {opciones.map((o, i) => {
```

El resto de `opciones.map(...)` no cambia. La variable `texto` de antes pasa a llamarse `textoBoton`.

- [ ] **Step 4: Comprobar que pasa**

Run: `npx vitest run src/shared/ui/MultiSelect.test.tsx && npx tsc -b && npm run lint`
Expected: todo verde, también los tests de siempre del componente.

- [ ] **Step 5: Commit**

```bash
git add src/shared/ui/MultiSelect.tsx src/shared/ui/MultiSelect.test.tsx
git commit -m "feat: multiselect con texto fijo, opcion para marcar todas y modo deshabilitado"
```

---

### Task 7: Web — contrato, hooks y diálogo «Piezas que vende…» por tipos

**Files:**
- Modify (generados): `gestion-reparaciones-web/api/openapi.json` y `src/shared/api/schema.d.ts`
- Modify: `gestion-reparaciones-web/src/shared/api/client.ts`: tipo `TipoPiezasProveedor`
- Modify: `gestion-reparaciones-web/src/modules/almacen/proveedores/api.ts` y `api.test.tsx`
- Modify (reescribir): `gestion-reparaciones-web/src/modules/almacen/proveedores/PiezasProveedorDialog.tsx` y `PiezasProveedorDialog.test.tsx`
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (`[0.9.9]`) y `docs/paridad/stock.md` (línea de «Piezas que vende…»)

**Interfaces:**
- Consumes:
  - Task 4: el servidor de `wt/servidor-099` compilado, con `target/openapi.json`;
  - Task 5: `piezasPorTipo.ts`, `traducirModelo` y `nombreTipoPieza`;
  - Task 6: las props nuevas de `MultiSelect`.
- Produces:
  - `usePiezasProveedor(idProv: number): UseQueryResult<TipoPiezasProveedor[]>`, con clave `['proveedores', idProv, 'piezas']`;
  - `useGuardarPiezasProveedor(): UseMutationResult<unknown, unknown, { idProv: number; cuerpo: CuerpoPiezas }>`;
  - el diálogo con las mismas props de hoy (`{ proveedor, onCerrar }`): `ProveedoresPage` no cambia.

- [ ] **Step 1: Contrato**

```bash
cp /c/Users/dev/Documents/wt/servidor-099/target/openapi.json api/openapi.json && npm run api:types:offline
grep -n "ProveedorPiezasTipo\|/api/proveedores/{idProv}/piezas\|/api/proveedores/{idProv}/componentes" src/shared/api/schema.d.ts | head
```
Expected: salen `ProveedorPiezasTipo` y la ruta `/piezas`; `/componentes` ya no.

En `src/shared/api/client.ts`, junto a `export type Proveedor = …`, añadir:

```ts
export type TipoPiezasProveedor = components['schemas']['ProveedorPiezasTipo']
```

- [ ] **Step 2: Tests que fallan**

`src/modules/almacen/proveedores/api.test.tsx`: sustituir el test de `useGuardarPiezasProveedor` por estos dos y añadir `usePiezasProveedor` al import de `./api`:

```tsx
  it('usePiezasProveedor lee los tipos del proveedor', async () => {
    guardarSesion(SESION_SUPER)
    server.use(http.get('*/api/proveedores/:id/piezas', ({ params }) => {
      expect(params.id).toBe('5')
      return HttpResponse.json([{ prefijo: 'bat', vende: true, modelos: ['12'], excluidos: [] }])
    }))
    const { result } = renderHook(() => usePiezasProveedor(5), { wrapper: envoltorio })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([{ prefijo: 'bat', vende: true, modelos: ['12'], excluidos: [] }])
  })

  it('useGuardarPiezasProveedor manda los tipos con PUT', async () => {
    guardarSesion(SESION_SUPER)
    let cuerpo: unknown = null
    server.use(http.put('*/api/proveedores/:id/piezas', async ({ request, params }) => {
      expect(params.id).toBe('5')
      cuerpo = await request.json()
      return new HttpResponse(null, { status: 204 })
    }))
    const { result } = renderHook(() => useGuardarPiezasProveedor(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ idProv: 5, cuerpo: { tipos: [{ prefijo: 'g', excluidos: ['12mini'] }] } }))
    expect(cuerpo).toEqual({ tipos: [{ prefijo: 'g', excluidos: ['12mini'] }] })
  })
```

Añadir `waitFor` al import de `@testing-library/react`.

`src/modules/almacen/proveedores/PiezasProveedorDialog.test.tsx` (sustituye todo el fichero):

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Proveedor, TipoPiezasProveedor } from '@/shared/api/client'
import { crearQueryClient } from '@/shared/api/queryClient'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { PiezasProveedorDialog } from './PiezasProveedorDialog'

const ACME: Proveedor = { idProv: 1, nombre: 'ACME', activo: true, divisa: 'EUR', comentario: '', tipo: 'COMPONENTES' }

const TIPOS: TipoPiezasProveedor[] = [
  { prefijo: 'bat', vende: true, modelos: ['12', '13', '14'], excluidos: [] },
  { prefijo: 'g', vende: true, modelos: ['12', '12mini', '13', '13mini'], excluidos: ['12mini', '13mini'] },
  { prefijo: 'lcd', vende: false, modelos: ['13', '14'], excluidos: [] },
]

let cuerpos: unknown[]
beforeEach(() => {
  cuerpos = []
  server.use(
    http.get('*/api/proveedores/:id/piezas', () => HttpResponse.json(TIPOS)),
    http.put('*/api/proveedores/:id/piezas', async ({ request }) => {
      cuerpos.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }),
  )
})

const abrir = (onCerrar = vi.fn()) => {
  renderConProviders(<PiezasProveedorDialog proveedor={ACME} onCerrar={onCerrar} />, { sesion: SESION_SUPER })
  return onCerrar
}

async function dialogo() {
  const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
  await within(dlg).findByRole('checkbox', { name: 'Vende Batería' })
  return dlg
}

describe('PiezasProveedorDialog por tipos (spec 2026-10-12 §5.1)', () => {
  it('un renglón por tipo con su casilla, el desplegable y lo que falta', async () => {
    abrir()
    const dlg = await dialogo()
    expect(dlg).toHaveTextContent('Marca los tipos de pieza que vende.')
    expect(dlg).toHaveTextContent('Vende 2 de 3 tipos')
    expect(within(dlg).getByRole('checkbox', { name: 'Vende Batería' })).toBeChecked()
    expect(within(dlg).getByRole('checkbox', { name: 'Vende Pantalla' })).not.toBeChecked()
    expect(within(dlg).getByRole('button', { name: 'Modelos de Batería' })).toHaveTextContent('Todos los modelos')
    expect(within(dlg).getByRole('button', { name: 'Modelos de Glass' })).toHaveTextContent('Seleccionados 2 de 4')
    const pantalla = within(dlg).getByRole('button', { name: 'Modelos de Pantalla' })
    expect(pantalla).toHaveTextContent('No lo vende')
    expect(pantalla).toBeDisabled()
    expect(dlg).toHaveTextContent('Sin iPhone 12 Mini · iPhone 13 Mini')
  })

  it('marcar un tipo, quitar un modelo, volver a «Todos los modelos» y guardar', async () => {
    const onCerrar = abrir()
    const dlg = await dialogo()
    await userEvent.click(within(dlg).getByRole('checkbox', { name: 'Vende Pantalla' }))
    const pantalla = within(dlg).getByRole('button', { name: 'Modelos de Pantalla' })
    expect(pantalla).toHaveTextContent('Todos los modelos')
    await userEvent.click(pantalla)
    expect(screen.getByRole('checkbox', { name: 'iPhone 13' })).toBeChecked()
    await userEvent.click(screen.getByRole('checkbox', { name: 'iPhone 13' }))
    expect(pantalla).toHaveTextContent('Seleccionados 1 de 2')
    expect(dlg).toHaveTextContent('Sin iPhone 13')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Todos los modelos' }))
    expect(pantalla).toHaveTextContent('Todos los modelos')
    await userEvent.keyboard('{Escape}')
    await userEvent.click(within(dlg).getByRole('checkbox', { name: 'Vende Batería' }))
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(cuerpos[0]).toEqual({ tipos: [{ prefijo: 'g', excluidos: ['12mini', '13mini'] }, { prefijo: 'lcd', excluidos: [] }] })
  })

  it('desmarcar todos los modelos desmarca el tipo', async () => {
    abrir()
    const dlg = await dialogo()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Modelos de Glass' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'iPhone 12' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'iPhone 13' }))
    await waitFor(() => expect(within(dlg).getByRole('checkbox', { name: 'Vende Glass' })).not.toBeChecked())
    expect(dlg).toHaveTextContent('Vende 1 de 3 tipos')
  })

  it('un 422 sale dentro y el diálogo sigue abierto', async () => {
    server.use(http.put('*/api/proveedores/:id/piezas', () => HttpResponse.json({ message: 'Algún modelo no existe.' }, { status: 422 })))
    const onCerrar = abrir()
    const dlg = await dialogo()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    expect(await within(dlg).findByRole('alert')).toHaveTextContent('Algún modelo no existe.')
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('con la caché vieja espera a los datos frescos', async () => {
    const qc = crearQueryClient({ retry: false })
    qc.setQueryData(['proveedores', 1, 'piezas'], TIPOS.map((t) => ({ ...t, vende: false, excluidos: [] })))
    renderConProviders(<PiezasProveedorDialog proveedor={ACME} onCerrar={vi.fn()} />, { sesion: SESION_SUPER, queryClient: qc })
    const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
    await waitFor(() => expect(within(dlg).getByRole('checkbox', { name: 'Vende Batería' })).toBeChecked())
    expect(dlg).toHaveTextContent('Vende 2 de 3 tipos')
  })

  it('mientras carga avisa, y si no carga lo dice y Guardar queda deshabilitado', async () => {
    server.use(http.get('*/api/proveedores/:id/piezas', () => HttpResponse.json({ message: 'boom' }, { status: 500 })))
    abrir()
    const dlg = await screen.findByRole('dialog', { name: 'Piezas que vende · ACME' })
    expect(await within(dlg).findByText('No se ha podido cargar la lista de piezas.')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Guardar', hidden: true })).toBeDisabled()
  })
})
```

Run: `npx vitest run src/modules/almacen/proveedores`
Expected: los tests nuevos fallan.

- [ ] **Step 3: Implementación**

En `src/modules/almacen/proveedores/api.ts`:
- Añadir `useQuery` y `type UseQueryResult` al import de `@tanstack/react-query`, si no están.
- Añadir `TipoPiezasProveedor` al import de `@/shared/api/client`.
- Añadir `import type { CuerpoPiezas } from './piezasPorTipo'`.
- Sustituir `useGuardarPiezasProveedor` por:

```ts
/** 0.9.9 (spec 2026-10-12 §4.3): los tipos que existen y lo que vende el proveedor de cada uno. El diálogo lo monta
 *  solo mientras está abierto. */
export function usePiezasProveedor(idProv: number): UseQueryResult<TipoPiezasProveedor[]> {
  return useQuery({
    queryKey: ['proveedores', idProv, 'piezas'],
    queryFn: async () => (await api.GET('/api/proveedores/{idProv}/piezas', { params: { path: { idProv } } })).data ?? [],
  })
}

/** 0.9.9 (spec 2026-10-12 §4.3): sustituye los tipos que vende el proveedor. Su 422 lo pinta el diálogo: silencia el
 *  global. Lo que vende cada pieza llega en `proveedores` del listado de Stock: se recarga (`['componentes', …]`). */
export function useGuardarPiezasProveedor(): UseMutationResult<unknown, unknown, { idProv: number; cuerpo: CuerpoPiezas }> {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ idProv, cuerpo }: { idProv: number; cuerpo: CuerpoPiezas }) =>
      api.PUT('/api/proveedores/{idProv}/piezas', { params: { path: { idProv } }, body: cuerpo }),
    meta: { silenciarError: true },
    onSettled: (_datos, _error, { idProv }) => {
      void qc.invalidateQueries({ queryKey: ['componentes'] })
      void qc.invalidateQueries({ queryKey: ['proveedores', idProv, 'piezas'] })
    },
  })
}
```

`src/modules/almacen/proveedores/PiezasProveedorDialog.tsx` (sustituye todo el fichero):

```tsx
import { useState } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError } from '@/shared/api/errors'
import { traducirModelo } from '@/shared/lib/modelos'
import { nombreTipoPieza } from '@/shared/lib/tiposPieza'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Checkbox } from '@/shared/ui/checkbox'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { useGuardarPiezasProveedor, usePiezasProveedor } from './api'
import {
  AYUDA_PIEZAS, cuerpoGuardar, estadoInicial, fijarVendidos, marcarTipo, textoBoton, textoRecuento, textoSinModelos, vendidos,
  type EstadoTipos,
} from './piezasPorTipo'

type Props = { proveedor: Proveedor; onCerrar: () => void }

/** «Piezas que vende…» por tipo de pieza (spec 2026-10-12 §5.1): un renglón por tipo con su casilla y el desplegable de
 *  modelos (todos marcados de entrada; los desmarcados quedan excluidos). «Guardar» manda todo. La página lo monta solo
 *  mientras está abierto. */
export function PiezasProveedorDialog({ proveedor, onCerrar }: Props) {
  const piezas = usePiezasProveedor(proveedor.idProv)
  const guardar = useGuardarPiezasProveedor()
  const { mostrarError } = useAlerta()
  const [estado, setEstado] = useState<EstadoTipos | null>(null)
  const [error, setError] = useState<string | null>(null)
  const tipos = piezas.data ?? []

  // Una sola vez, con datos frescos: con la caché ya cargada, `isSuccess` es true al montar con datos viejos.
  if (estado === null && piezas.isSuccess && !piezas.isFetching) setEstado(estadoInicial(piezas.data))

  function cambiar(nuevo: EstadoTipos) {
    setEstado(nuevo)
    setError(null)
  }

  async function confirmar() {
    if (estado === null) return
    setError(null)
    try {
      await guardar.mutateAsync({ idProv: proveedor.idProv, cuerpo: cuerpoGuardar(tipos, estado) })
      onCerrar()
    } catch (e) {
      if (e instanceof ReglaNegocioError) { setError(e.message); return }
      if (esErrorGestionadoGlobalmente(e)) return
      mostrarError(mensajeDeError(e))
    }
  }

  return (
    <DialogoAlmacen abierto ancho={640} titulo={`Piezas que vende · ${proveedor.nombre}`} subtitulo={AYUDA_PIEZAS} error={error}
      textoAccion="Guardar" enviando={guardar.isPending} accionDeshabilitada={estado === null}
      onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      {estado === null ? (
        <p role="status" className="text-[12px] text-azul-gris">
          {piezas.isError ? 'No se ha podido cargar la lista de piezas.' : 'Cargando piezas…'}
        </p>
      ) : (
        <>
          <p className="text-[12px] text-azul-gris">{textoRecuento(estado, tipos.length)}</p>
          <div className="flex flex-col rounded border border-fila-sep bg-superficie">
            {tipos.map((t) => {
              const excluidos = estado.get(t.prefijo)
              const nombre = nombreTipoPieza(t.prefijo)
              return (
                <div key={t.prefijo} data-prefijo={t.prefijo}
                  className="flex min-h-12 items-center gap-2.5 border-b border-fila-sep px-2.5 py-2 last:border-b-0">
                  <label className="flex w-[150px] shrink-0 items-center gap-2 text-[13px] text-azul-medio">
                    <Checkbox checked={excluidos !== undefined} onCheckedChange={(v) => cambiar(marcarTipo(estado, t.prefijo, v === true))}
                      aria-label={`Vende ${nombre}`} />
                    {nombre}
                  </label>
                  <span className="min-w-0 flex-1 truncate text-[11.5px] text-texto-sub">{textoSinModelos(t, excluidos)}</span>
                  <MultiSelect opciones={t.modelos} clave={(m) => m} etiqueta={(m) => traducirModelo(m)} seleccion={vendidos(t, excluidos)}
                    onChange={(s) => cambiar(fijarVendidos(estado, t, s))} textoVacio="No lo vende" textoPlural={(n) => `${n} modelos`}
                    texto={textoBoton(t, excluidos)} opcionTodas="Todos los modelos" deshabilitado={excluidos === undefined}
                    etiquetaAccesible={`Modelos de ${nombre}`} className="h-8 min-w-[170px]" />
                </div>
              )
            })}
          </div>
        </>
      )}
    </DialogoAlmacen>
  )
}
```

`CHANGELOG.md` `[0.9.9]`: sustituir la viñeta que empieza por «- **Piezas que vende cada proveedor**» por:

```
- **Piezas que vende cada proveedor** (Proveedores → clic derecho → «Piezas que vende…», supertécnicos): un renglón por **tipo de pieza** (Batería, Chasis, Tapa trasera, Glass, Marco, Pantalla, Cámara) con su casilla. Un tipo marcado se vende de **todos los modelos**, también los que salgan más adelante; en su desplegable se desmarcan los modelos que no vende («Seleccionados K de M», con los que faltan al lado). Arranca rellenada con los tipos que se le han pedido a cada proveedor.
```

`docs/paridad/stock.md` (línea 216): sustituir el texto de la viñeta de Proveedores por:

```
- Proveedores: clic derecho → «Piezas que vende…» (supertécnicos) abre «Piezas que vende · <proveedor>»: un renglón por tipo de pieza con su casilla y un desplegable de modelos («Todos los modelos» / «Seleccionados K de M»; los desmarcados quedan excluidos, los modelos nuevos entran solos); arranca rellenada con los tipos pedidos (spec 2026-10-12).
```

- [ ] **Step 4: Comprobar que pasa**

Run, de uno en uno:
```bash
npx vitest run src/modules/almacen/proveedores
npx tsc -b && npm run lint
npx vitest run 2>&1 | grep -E "Test Files|Tests "
```
Expected: todo verde. En la suite entera, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/almacen/proveedores/ CHANGELOG.md docs/paridad/stock.md
git commit -m "feat: dialogo de piezas que vende cada proveedor por tipo de pieza con modelos excluidos"
```

---

### Task 8: Web — E2E `catalogo.spec.ts` rehecha para los tipos

**Files:**
- Modify: `gestion-reparaciones-web/tests/e2e/catalogo.spec.ts`

**Interfaces:**
- Consumes:
  - `GET` y `PUT /api/proveedores/{id}/piezas` (Task 3);
  - el diálogo: renglón `[data-prefijo]`, botón «Modelos de <tipo>», opción «Todos los modelos» (Task 7);
  - `proveedores` de cada pieza en `GET /api/componentes/gestionados`.
- La cabecera (letrero de preproducción, sesiones, `llamarApi`, `pieza`, `afterAll` con limpieza guardada) **se queda como está**. Solo cambian el comentario de arriba, el tipo `Pieza` y los tres tests.

- [ ] **Step 1: Reescribir los tests**

Comentario de cabecera: cambiar la primera frase por «0.9.9 (spec 2026-10-12): tipos de pieza que vende cada proveedor con modelos excluidos, previsión por proveedor, «Pendiente» / «Pedir 60 d» y buscar un bloque por su número.». El resto se queda igual.

El tipo `Pieza` añade `idComMaster: number | null; activo: boolean`.

Tras `pieza(...)`, añadir estos ayudantes:

```ts
type TipoPiezas = { prefijo: string; vende: boolean; modelos: string[]; excluidos: string[] }

/** Prefijo y modelo del SKU de prueba con el criterio del servidor: prefijo = lo que va antes de la primera «i»; modelo
 *  = el código más largo de los modelos de su tipo que sea prefijo de lo que queda. */
function prefijoYModelo(tipos: TipoPiezas[]): { prefijo: string; modelo: string } {
  const s = (sku as string).toLowerCase()
  const prefijo = s.slice(0, s.indexOf('i'))
  const tipo = tipos.find((t) => t.prefijo === prefijo)
  expect(tipo, `El tipo ${prefijo} de ${sku} no sale en GET /piezas`).toBeTruthy()
  const resto = s.slice(prefijo.length + 1)
  const modelo = (tipo as TipoPiezas).modelos.filter((m) => resto.startsWith(m)).sort((a, b) => b.length - a.length)[0]
  expect(modelo, `Sin modelo para ${sku}`).toBeTruthy()
  return { prefijo, modelo }
}

/** Las piezas que «Añadir previsión de X (N)» cuenta: masters activas en Auto con algo que pedir que vende X. */
async function previstas(page: Page): Promise<Pieza[]> {
  const r = await llamarApi(page, 'GET', '/api/componentes/gestionados')
  return (r.json as Pieza[]).filter((p) => p.idComMaster == null && p.activo && p.autoPedido === true && (p.pedir60 ?? 0) > 0
    && (p.proveedores ?? []).includes(idProv as number))
}

let tipoPrueba: { prefijo: string; modelo: string } | null = null
```

Sustituir los tres tests por:

```ts
test('con el modelo de la pieza excluido, la previsión de X no la trae', async () => {
  const page = sup as Page
  const p = antes as Pieza
  // Mínimo por encima de lo que hay (solo ADMIN) y pieza en Auto, para que «Pedir 60 d» sea > 0.
  expect((await llamarApi(adm as Page, 'PATCH', `/api/componentes/${p.idCom}/stock-minimo`, { stockMinimo: p.stock + p.enCamino + (p.pendiente ?? 0) + 50 })).status).toBe(200)
  expect((await llamarApi(page, 'PATCH', `/api/componentes/${p.idCom}/auto-pedido`, { autoPedido: true })).status).toBe(200)
  const lectura = await llamarApi(page, 'GET', `/api/proveedores/${idProv}/piezas`)
  expect(lectura.status).toBe(200)
  tipoPrueba = prefijoYModelo(lectura.json as TipoPiezas[])
  // El tipo de la pieza, con todos los modelos menos el suyo (por la API: el desplegable lo cubren los tests unitarios).
  const guardado = await llamarApi(page, 'PUT', `/api/proveedores/${idProv}/piezas`, { tipos: [{ prefijo: tipoPrueba.prefijo, excluidos: [tipoPrueba.modelo] }] })
  expect(guardado.status).toBe(204)
  expect((await pieza(page)).proveedores ?? []).not.toContain(idProv)
  const n = (await previstas(page)).length

  await page.goto('/stock/pedidos')
  await page.getByRole('button', { name: 'Nuevo pedido', exact: true }).click()
  const dlg = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
  await dlg.getByRole('combobox', { name: 'Proveedor general' }).click()
  await page.getByRole('listbox', { name: 'Proveedor general' }).getByRole('button', { name: nombreProv }).click()
  await expect(dlg.getByRole('button', { name: `Añadir previsión de ${nombreProv} (${n})` })).toBeVisible()
  await dlg.getByRole('button', { name: 'Cancelar' }).click()
})

test('«Piezas que vende…»: «Todos los modelos» y la previsión ya la trae', async () => {
  const page = sup as Page
  const tp = tipoPrueba as { prefijo: string; modelo: string }
  await page.goto('/stock/proveedores')
  await page.getByRole('row').filter({ hasText: nombreProv }).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Piezas que vende…' }).click()
  const dlg = page.getByRole('dialog', { name: `Piezas que vende · ${nombreProv}` })
  const renglon = dlg.locator(`[data-prefijo="${tp.prefijo}"]`)
  await expect(renglon.getByRole('checkbox')).toBeChecked()
  const desplegable = renglon.getByRole('button')
  await expect(desplegable).toContainText('Seleccionados')
  await desplegable.click()
  await page.getByRole('checkbox', { name: 'Todos los modelos' }).click()
  await expect(desplegable).toHaveText(/Todos los modelos/)
  await page.keyboard.press('Escape')
  await dlg.getByRole('button', { name: 'Guardar' }).click()
  await expect(dlg).toBeHidden()
  await expect.poll(async () => (await pieza(page)).proveedores).toContain(idProv)

  const conPedido = await pieza(page)
  const n = (await previstas(page)).length
  expect(n).toBeGreaterThan(0)
  await page.goto('/stock/pedidos')
  await page.getByRole('button', { name: 'Nuevo pedido', exact: true }).click()
  const pedido = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
  await pedido.getByRole('combobox', { name: 'Proveedor general' }).click()
  await page.getByRole('listbox', { name: 'Proveedor general' }).getByRole('button', { name: nombreProv }).click()
  await pedido.getByRole('button', { name: `Añadir previsión de ${nombreProv} (${n})` }).click()
  const respuesta = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/compras/lote' && r.request().method() === 'POST')
  await pedido.getByRole('button', { name: 'Confirmar pedido' }).click()
  const lote = await respuesta
  expect(lote.ok(), `POST /api/compras/lote respondió ${lote.status()}`).toBe(true)
  lineasCreadas.push(...((await lote.json()) as RespuestaLote).idsCreados)
  expect(lineasCreadas).toHaveLength(n)
  await expect(pedido).toBeHidden()

  const despues = await pieza(page)
  expect(despues.pendiente).toBe((conPedido.pendiente ?? 0) + (conPedido.pedir60 ?? 0))
  expect(despues.pedir60).toBe(0)
})

test('el buscador con el número del bloque enseña solo ese bloque', async () => {
  const page = sup as Page
  const compras = await llamarApi(page, 'GET', '/api/compras')
  const idBloque = (compras.json as { idCompra: number; idBloque: number | null }[]).find((l) => l.idCompra === lineasCreadas[0])?.idBloque
  expect(idBloque, 'la línea creada debe tener bloque').toBeTruthy()
  await page.goto('/stock/pedidos')
  await page.getByPlaceholder('Buscar componente o nº de bloque…').fill(String(idBloque))
  // Una búsqueda solo de dígitos devuelve las líneas cuyo idBloque es ese número: queda una única cabecera.
  await expect(page.locator('tr[data-cabecera]')).toHaveCount(1)
  // «Bloque n» sin casar con «Bloque n0» (como bloques.spec.ts).
  const nuestra = page.locator('tr[data-cabecera]').filter({ hasText: new RegExp(`Bloque ${idBloque}(?!\\d)`) })
  await expect(nuestra).toHaveCount(1)
  await expect(nuestra).toContainText(nombreProv)
})
```

`llamarApi` admite `'PUT'`: hay que añadirlo a su unión de métodos (`'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'`). `escaparRegex` deja de usarse y se borra.

- [ ] **Step 2: Tipado y lint**

Run: `npx tsc -b && npm run lint`
Expected: limpio. La E2E **no** se ejecuta aquí: corre contra preproducción en la Task 10, ya con el servidor y la web nuevos desplegados.

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/catalogo.spec.ts
git commit -m "test: la e2e del catalogo usa tipos de pieza con un modelo excluido y todos los modelos"
```

---

### Task 9: Cierre (controlador) — revisión final, integración y guiones

No es de subagente: la hace el controlador.

- [ ] Revisión final de las dos ramas con el modelo más capaz y su lista de menores. Se arreglan los Critical e Important con **un** subagente.
- [ ] **Con OK del usuario:**
  1. `merge --no-ff` de `feature/piezas-por-tipo` en `main`, en los dos repos, con el mensaje `merge: piezas de cada proveedor por tipo de pieza (0.9.9)`, y push.
  2. En la raíz: gitlinks; la viñeta «Qué le pides a cada proveedor» de `docs/novedades/NOVEDADES-v0.9.9.md` reescrita para los tipos; push.
- [ ] `Apuntes/preprod/v099c-preprod.md`:
  - **Bloque 1:** estado. Clones en `6a02be8` / `4830969`; `Proveedor_componente` existe y `Proveedor_tipo` no; 25 tablas; `tipos_previstos`.
  - **Bloque 2:** `git pull`, luego `DROP TABLE Proveedor_componente;`, la migración, comprobaciones de las tablas (`uca1400` y las dos claves en `CASCADE`), el relleno, `compose up` (aviso: ~1 min sin salida), `Started`.
  - **Bloque 2b:** el relleno otra vez, que debe dar las mismas cifras.
  - **Comprobaciones por la API:**
    - `GET /piezas` de un proveedor real;
    - `proveedores` en el listado de Stock;
    - técnico → nulos;
    - `PUT` → 403 para técnico y administrador, sin escribir.
  - **E2E**, de una en una y con ≥ 60 s entre ficheros.
  - **Bloque 2c:** limpieza de lo que dejan las E2E.
  - **Bloque 3:** `init.sql` con **26** tablas.
- [ ] `Apuntes/prod-v099.md`:
  - versiones nuevas;
  - `tablas_ya` sobre `Bloque_compra`, `Proveedor_tipo` y `Proveedor_tipo_excluido`;
  - `tipos_previstos`;
  - comprobaciones del relleno nuevo (`tipos`, `proveedores`, `prefijos_raros` 0, `excepciones` 0);
  - copia con **26** tablas;
  - vuelta atrás con `DROP TABLE Proveedor_tipo_excluido; DROP TABLE Proveedor_tipo;`.

### Task 10 (usuario): preproducción

El usuario pega los bloques de `v099c-preprod.md` en `ssh preprod`. Claude revisa cada salida, hace las comprobaciones por la API y lanza las E2E. Después se apunta en `despliegue_preprod.md`.

### Task 11 (usuario): producción

Con `prod-v099.md`, en `ssh prod`, cuando el usuario decida. Después:
- registro en `despliegue_vdc_produccion.md`;
- fecha del CHANGELOG;
- tags solo cuando lo diga el usuario;
- `plan-futuro`;
- memoria.
