# Atributos SKU-ready de teléfonos — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capturar TODOS los atributos que componen el SKU de teléfono (eSIM, color oficial, capacidad válida, grado C/B/A-/A) para que ningún lote importado pierda información — según la spec `docs/superpowers/specs/2026-07-14-atributos-sku-telefonos-design.md` (secciones 1-3). La derivación del SKU queda FUERA (post-F2b).

**Architecture:** `Telefono.ES_ESIM` nuevo + `Color_equivalencia` (espejo de `Modelo_equivalencia`). En el cliente, una matriz estática modelo→{colores oficiales, capacidades} (`CatalogoAtributos`) alimenta selectores filtrados por modelo (alta manual, editar atributos) y la validación del importador: color no reconocido = fila bloqueada con flujo "Colores sin mapear" (idéntico al de modelos); capacidad fuera de paleta = aviso no bloqueante; texto de modelo con "esim" = flag automático. Los TIPO de chasis del catálogo de componentes se renombran al mismo vocabulario de color.

**Tech Stack:** Java 17 · JavaFX 21 · Gson 2.10.1 · JUnit 5 (cliente) · Spring Boot 3.3.4 + JdbcTemplate (servidor) · MariaDB.

## Global Constraints

- **Dos repos**: raíz `c:\Users\info\Documents\ProgramaReparaciones` (cliente + docs) y `gestion-reparaciones-servidor` (repo anidado propio). Cada commit va en el repo cuyo código toca.
- **Rama**: `feature/atributos-sku` en AMBOS repos, partiendo de `main` (raíz `8a8e9d3+`, servidor `110e0ef`). La rama raíz YA existe (plan commiteado en ella).
- **Commits SIN `Co-Authored-By`**; mensajes `feat/fix(ámbito): descripción en español`.
- **Merge, push y tags: SOLO con OK explícito del usuario.**
- **Migraciones SQL: NUNCA ejecutarlas** — se escriben en `gestion-reparaciones-servidor/sql/` y las aplica el usuario. Orden de despliegue OBLIGATORIO: ALTER → servidor → cliente.
- **Comandos por Bash**, Maven incluido: cliente `cd gestion-reparaciones-cliente && mvn -q test`; servidor `cd gestion-reparaciones-servidor && mvn -q test`.
- JSON cliente↔servidor por NOMBRE exacto de campo (Gson por field, Jackson por getter/record): el campo nuevo se llama **`esEsim`** en ambos lados. No renombrar nada existente.
- El servidor NO tiene test de contexto Spring: los cambios de wiring se validan arrancándolo (tarea de cierre).
- Escala de grado propio definitiva: **C / B / A- / A** (A+ eliminado por decisión 2026-07-14; el combo del cliente ya está sin A+ desde `3339dcf`).
- Color canónico = nombre OFICIAL de Apple en inglés. `Color_equivalencia.TEXTO_EXTERNO` se guarda NORMALIZADO: minúsculas, solo `[a-z0-9]` (p. ej. `(PRODUCT)RED` → `productred`).
- Regla eSIM: `ModeloMapper.normalizar(textoModelo).contains("esim")` ⇒ `esEsim = true`. Automático, sin UI, independiente del mapeo de modelo.
- Reutilizar patrones existentes de F2a: `ModeloMapper`/`Modelo_equivalencia`/bloque "Modelos sin mapear" son el espejo exacto de lo que se construye aquí para colores.

## Contexto imprescindible (leído del código real)

- `ModeloMapper` (cliente, package `controllers`): `static String normalizar(String)` (minúsculas, `[a-z0-9]`, sin prefijo "iphone"), `static Map<String,String> mapear(Collection<String>, Map<String,String>)` → claves = textos ORIGINALES.
- `Importacion.TelefonoImport` (cliente) y `ImportacionRequest.TelefonoImport` (servidor) son records ESPEJO: `(String imei, String modelo, Integer storageGb, String color, String gradoProveedor, BigDecimal precioCompra, String divisa, BigDecimal precioCompraEur)`. Se les añade `boolean esEsim` AL FINAL en ambos.
- `TelefonoController.AtributosRequest` (servidor, record privado): `(String modelo, Integer storageGb, String color, String gradoProveedor, String gradoPropio, LocalDateTime updatedAt)` → se añade `Boolean esEsim`.
- `ClasificadorImportacion.clasificar(List<Fila>, Map<String,String> mapeoModelos, Map<String,VerificacionImei> existentes)` → `Plan(lotes, excluidas)`; `Destino ∈ {NUEVO, REENTRADA, CONFLICTO, STATUS_DISTINTO, INVALIDO, DUPLICADO_FICHERO, MODELO_SIN_MAPEAR}`; `FilaClasificada(Fila, Destino, String modeloInterno, String detalle)`.
- `EquivalenciaModeloDAO` cliente (`getAll()` → Map, `guardar(textoExterno, modeloInterno)`) y servidor (`EquivalenciaModeloController`: GET/PUT `/api/modelos/equivalencias`, PUT con `@PreAuthorize("hasRole('SUPERTECNICO')")`) son las plantillas exactas para el lado de colores.
- `ImportadorLoteDialog`: bloque ámbar "Modelos sin mapear" con label `"texto" (N filas)` + botón `Elegir modelo…` + `reclasificar()`. El de colores se construye idéntico debajo.
- `AgrupadoController.abrirDialogoAtributos(TelefonoInventario)` (~línea 1440): TextFields libres de storage/color, combo grado `"—","C","B","A-","A"`.
- `AltaManualLoteDialog`: atributos comunes opcionales (Modelo… vía `SelectorModeloDialog`, Storage GB TextField numérico, Color TextField, Grado prov, Precio).
- Catálogo de modelos (39 códigos internos): `6s,6splus,7,7plus,8,8plus,se2020,x,xr,xs,xsmax,11,11pro,11promax,12,12mini,12pro,12promax,13,13mini,13pro,13promax,14,14plus,14pro,14promax,15,15plus,15pro,15promax,16,16e,16plus,16pro,16promax,17,air,17pro,17promax`.
- Chasis: `Componente.TIPO` (VARCHAR(100) UNIQUE) con el color embebido en el string (en castellano). Las referencias van por `ID_COM` — renombrar TIPO no rompe FKs.

## Estructura de ficheros

**Servidor:** Create `sql/migracion-atributos-sku.sql`, `sql/renombrar-chasis-colores.sql`, `dao/ColorEquivalenciaDAO.java`, `controller/ColorEquivalenciaController.java` · Modify `sql/crear_bd.sql`, `model/TelefonoInventario.java`, `model/ImportacionRequest.java`, `dao/TelefonoDAO.java`, `controller/TelefonoController.java`.

**Cliente:** Create `controllers/CatalogoAtributos.java` (+test), `utils/ColorMapper.java` (+test), `dao/ColorEquivalenciaDAO.java` · Modify `controllers/ModeloMapper.java` (+test), `utils/ClasificadorImportacion.java` (+test), `models/TelefonoInventario.java`, `models/Importacion.java`, `dao/TelefonoDAO.java`, `controllers/ImportadorLoteDialog.java`, `controllers/AltaManualLoteDialog.java`, `controllers/AgrupadoController.java`.

---

### Tarea 1: Rama servidor + migración SQL + crear_bd.sql

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-atributos-sku.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql`

**Interfaces:**
- Produces: columnas `Telefono.ES_ESIM`, enum `GRADO_PROPIO` sin A+, tabla `Color_equivalencia` — las consumen T2, T3.

- [ ] **Paso 1: Rama.** En `gestion-reparaciones-servidor`: `git checkout main && git checkout -b feature/atributos-sku`. Verificar `git branch --show-current`.

- [ ] **Paso 2: Escribir `sql/migracion-atributos-sku.sql`** (NO ejecutarla — la aplica el usuario):

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-atributos-sku.sql — mini-fase atributos SKU-ready (spec 2026-07-14)
-- La aplica el usuario a mano en gestion_reparaciones. NO idempotente.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Flag de variante eSIM (el importador lo marca solo si el texto trae "esim")
ALTER TABLE Telefono
    ADD COLUMN ES_ESIM BOOLEAN NOT NULL DEFAULT FALSE AFTER GRADO_PROPIO;

-- Escala de grado definitiva: A+ eliminado (decisión 2026-07-14, A = máximo)
UPDATE Telefono SET GRADO_PROPIO = 'A' WHERE GRADO_PROPIO = 'A+';
ALTER TABLE Telefono
    MODIFY COLUMN GRADO_PROPIO ENUM('C','B','A-','A') NULL;

-- Equivalencias de color recordadas por el importador ("blanco roto" → "White").
-- TEXTO_EXTERNO NORMALIZADO: minúsculas, solo [a-z0-9].
CREATE TABLE Color_equivalencia (
    TEXTO_EXTERNO  VARCHAR(100) NOT NULL,
    COLOR_OFICIAL  VARCHAR(50)  NOT NULL,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (TEXTO_EXTERNO)
);
```

- [ ] **Paso 3: Sincronizar `sql/crear_bd.sql`**: (a) en `CREATE TABLE Telefono`, añadir `ES_ESIM BOOLEAN NOT NULL DEFAULT FALSE,` justo tras `GRADO_PROPIO` y cambiar esa línea a `GRADO_PROPIO ENUM('C','B','A-','A') NULL,`; (b) añadir el `CREATE TABLE Color_equivalencia` (idéntico al de la migración) junto a `Modelo_equivalencia`.

- [ ] **Paso 4: Commit (repo SERVIDOR)**

```bash
git add sql/ && git commit -m "feat(sql): esquema atributos SKU — ES_ESIM, grado sin A+ y Color_equivalencia"
```

---

### Tarea 2: Servidor — `esEsim` de punta a punta

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/model/TelefonoInventario.java`, `model/ImportacionRequest.java`, `dao/TelefonoDAO.java`, `controller/TelefonoController.java`

**Interfaces:**
- Consumes: columna `ES_ESIM` (T1).
- Produces: campo JSON `esEsim` en el inventario, en `TelefonoImport` y en el PATCH de atributos — lo consumen T5-T9 del cliente. Nombres EXACTOS: `esEsim` / `isEsEsim()` / `setEsEsim(boolean)`.

- [ ] **Paso 1: `model/TelefonoInventario.java`** — añadir tras `gradoPropio`:

```java
    private boolean esEsim;
```
y sus accessors junto a los demás:
```java
    public boolean isEsEsim() { return esEsim; }
    public void setEsEsim(boolean esEsim) { this.esEsim = esEsim; }
```

- [ ] **Paso 2: `model/ImportacionRequest.java`** — añadir `boolean esEsim` como ÚLTIMO componente de `TelefonoImport`:

```java
    public record TelefonoImport(String imei, String modelo, Integer storageGb, String color,
                                 String gradoProveedor, java.math.BigDecimal precioCompra, String divisa,
                                 java.math.BigDecimal precioCompraEur, boolean esEsim) {}
```
(Jackson pone `false` si el JSON no trae el campo — retrocompatible con clientes viejos.)

- [ ] **Paso 3: `dao/TelefonoDAO.java`** — tres retoques:
  1. En el SELECT del inventario (query de `getInventario`), añadir `t.ES_ESIM` a la lista de columnas y `inv.setEsEsim(rs.getBoolean("ES_ESIM"));` en el RowMapper, junto a los demás setters.
  2. En `upsertImportacion`, añadir la columna: `INSERT INTO Telefono (..., GRADO_PROVEEDOR, ES_ESIM, PRECIO_COMPRA, ...) VALUES (?, ..., ?, ...)` y en el `ON DUPLICATE KEY UPDATE` añadir `ES_ESIM = ?`. Firma nueva: `upsertImportacion(String imei, String modelo, Integer idLote, Integer storageGb, String color, String gradoProveedor, boolean esEsim, BigDecimal precioCompra, String divisa, BigDecimal precioCompraEur)` (parámetro tras `gradoProveedor`). Actualizar el call-site en `LoteImportService.importar`: `telefonoDao.upsertImportacion(t.imei(), t.modelo(), idLote, t.storageGb(), t.color(), t.gradoProveedor(), t.esEsim(), t.precioCompra(), t.divisa(), t.precioCompraEur());`
  3. En `actualizarAtributos`, añadir `ES_ESIM = ?` al SET y `Boolean esEsim` a la firma (tras `gradoPropio`; si llega `null`, mantener valor: usar `ES_ESIM = COALESCE(?, ES_ESIM)`).

- [ ] **Paso 4: `controller/TelefonoController.java`** — `AtributosRequest` pasa a `(String modelo, Integer storageGb, String color, String gradoProveedor, String gradoPropio, Boolean esEsim, LocalDateTime updatedAt)` y el handler propaga `req.esEsim()` al DAO.

- [ ] **Paso 5: Compilar + suite + commit (repo SERVIDOR)**

Run: `mvn -q test` → BUILD SUCCESS (los tests existentes de UbicacionDerivador no tocan esto).

```bash
git add src && git commit -m "feat(servidor): esEsim en inventario, importación y edición de atributos"
```

---

### Tarea 3: Servidor — equivalencias de color (DAO + endpoints)

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/dao/ColorEquivalenciaDAO.java`, `controller/ColorEquivalenciaController.java`

**Interfaces:**
- Produces: `GET /api/colores/equivalencias` → `[{textoExterno, colorOficial}]` (autenticado-cualquiera) y `PUT /api/colores/equivalencias` body `{textoExterno, colorOficial}` (SUPERTECNICO). Los consume T5 (cliente). Nombres JSON EXACTOS: `textoExterno`, `colorOficial`.

- [ ] **Paso 1: DAO** — calco de `EquivalenciaModeloDAO` (leerlo primero y copiar su estructura exacta, cambiando tabla/columnas):

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Equivalencias de color recordadas por el importador (TEXTO_EXTERNO normalizado). */
@Repository
public class ColorEquivalenciaDAO {

    private final JdbcTemplate jdbc;

    public ColorEquivalenciaDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> getAll() {
        return jdbc.query("SELECT TEXTO_EXTERNO, COLOR_OFICIAL FROM Color_equivalencia",
                (rs, i) -> Map.of("textoExterno", rs.getString("TEXTO_EXTERNO"),
                                  "colorOficial", rs.getString("COLOR_OFICIAL")));
    }

    public void upsert(String textoExterno, String colorOficial) {
        jdbc.update("INSERT INTO Color_equivalencia (TEXTO_EXTERNO, COLOR_OFICIAL) VALUES (?, ?)" +
                    " ON DUPLICATE KEY UPDATE COLOR_OFICIAL = ?",
                textoExterno, colorOficial, colorOficial);
    }
}
```

- [ ] **Paso 2: Controller** — calco de `EquivalenciaModeloController` (leerlo y copiar anotaciones/estructura exactas, incluido el `@PreAuthorize` del PUT y el log de actividad si aquel lo hace — replicar comportamiento 1:1 con acción `EQUIVALENCIA_COLOR`):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/colores/equivalencias")
public class ColorEquivalenciaController {

    private final ColorEquivalenciaDAO dao;

    public ColorEquivalenciaController(ColorEquivalenciaDAO dao) { this.dao = dao; }

    @GetMapping
    public List<Map<String, Object>> getAll() { return dao.getAll(); }

    public record EquivalenciaRequest(String textoExterno, String colorOficial) {}

    @PutMapping
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardar(@RequestBody EquivalenciaRequest req) {
        dao.upsert(req.textoExterno(), req.colorOficial());
    }
}
```
(Si `EquivalenciaModeloController` difiere en algo — status codes, log —, imitar a AQUEL, no a este snippet, y anotarlo en el informe.)

- [ ] **Paso 3: Compilar + suite + commit (repo SERVIDOR)**

```bash
git add src && git commit -m "feat(servidor): equivalencias de color del importador (GET/PUT /api/colores/equivalencias)"
```

---

### Tarea 4: Cliente — `CatalogoAtributos` (matriz modelo→colores/capacidades, TDD)

**Files:**
- Create: `src/main/java/com/reparaciones/controllers/CatalogoAtributos.java`
- Test: `src/test/java/com/reparaciones/controllers/CatalogoAtributosTest.java`

**Interfaces:**
- Produces: `CatalogoAtributos.coloresDe(String modeloInterno)` → `List<String>` (vacía si modelo null/desconocido), `capacidadesDe(String modeloInterno)` → `List<Integer>` (ídem), `COLORES_TODOS` → `List<String>` (unión ordenada sin duplicados), `CAPACIDADES_TODAS` → `List<Integer>`, `esColorOficial(String)` → boolean. Consumido por T5, T6, T8, T9.

- [ ] **Paso 1: Test que falla**

```java
package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoAtributosTest {

    @Test void paletaDelModeloConColoresOficiales() {
        assertTrue(CatalogoAtributos.coloresDe("15promax").contains("Natural Titanium"));
        assertTrue(CatalogoAtributos.coloresDe("12").contains("(PRODUCT)RED"));
        assertFalse(CatalogoAtributos.coloresDe("12").contains("Natural Titanium"));
    }

    @Test void capacidadesDelModelo() {
        assertEquals(java.util.List.of(256, 512, 1024), CatalogoAtributos.capacidadesDe("15promax"));
        assertTrue(CatalogoAtributos.capacidadesDe("12").contains(64));
        assertFalse(CatalogoAtributos.capacidadesDe("12").contains(1024));
    }

    @Test void modeloNuloODesconocidoDevuelveVacio() {
        assertTrue(CatalogoAtributos.coloresDe(null).isEmpty());
        assertTrue(CatalogoAtributos.capacidadesDe("nokia3310").isEmpty());
    }

    @Test void todosLosModelosDelCatalogoTienenPaleta() {
        for (String m : FormularioReparacionController.MODELOS_ORDENADOS) {
            assertFalse(CatalogoAtributos.coloresDe(m).isEmpty(), "sin colores: " + m);
            assertFalse(CatalogoAtributos.capacidadesDe(m).isEmpty(), "sin capacidades: " + m);
        }
    }

    @Test void unionesYPertenencia() {
        assertTrue(CatalogoAtributos.COLORES_TODOS.contains("Midnight"));
        assertTrue(CatalogoAtributos.esColorOficial("Sierra Blue"));
        assertFalse(CatalogoAtributos.esColorOficial("Blanco"));
    }
}
```
(Si `MODELOS_ORDENADOS` no es accesible por visibilidad desde el test, usar su misma visibilidad package-private — el test está en el mismo package.)

- [ ] **Paso 2: Ejecutar y ver que falla** — `mvn -q test -Dtest=CatalogoAtributosTest` → no compila.

- [ ] **Paso 3: Implementación.** Clase final con dos mapas estáticos inmutables. DATOS COMPLETOS (paleta oficial de Apple por modelo — **el usuario los revisará en el smoke**, especialmente serie 17):

```java
package com.reparaciones.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matriz modelo→{colores oficiales de Apple (inglés), capacidades GB} (spec atributos SKU §2.3).
 * Fuente de verdad de los selectores filtrados y de la validación del importador.
 * Se amplía junto con MODELOS_ORDENADOS cuando salen series nuevas.
 */
public final class CatalogoAtributos {

    private CatalogoAtributos() {}

    private static final Map<String, List<String>> COLORES = new LinkedHashMap<>();
    private static final Map<String, List<Integer>> CAPACIDADES = new LinkedHashMap<>();

    private static void def(String modelo, List<Integer> caps, List<String> colores) {
        COLORES.put(modelo, colores);
        CAPACIDADES.put(modelo, caps);
    }

    static {
        List<String> c6s   = List.of("Silver", "Gold", "Space Gray", "Rose Gold");
        def("6s",       List.of(16, 32, 64, 128), c6s);
        def("6splus",   List.of(16, 32, 64, 128), c6s);
        List<String> c7    = List.of("Jet Black", "Black", "Silver", "Gold", "Rose Gold", "(PRODUCT)RED");
        def("7",        List.of(32, 128, 256), c7);
        def("7plus",    List.of(32, 128, 256), c7);
        List<String> c8    = List.of("Silver", "Gold", "Space Gray", "(PRODUCT)RED");
        def("8",        List.of(64, 128, 256), c8);
        def("8plus",    List.of(64, 128, 256), c8);
        def("se2020",   List.of(64, 128, 256), List.of("Black", "White", "(PRODUCT)RED"));
        def("x",        List.of(64, 256), List.of("Silver", "Space Gray"));
        def("xr",       List.of(64, 128, 256), List.of("Black", "White", "Blue", "Yellow", "Coral", "(PRODUCT)RED"));
        List<String> cxs   = List.of("Silver", "Gold", "Space Gray");
        def("xs",       List.of(64, 256, 512), cxs);
        def("xsmax",    List.of(64, 256, 512), cxs);
        def("11",       List.of(64, 128, 256), List.of("Black", "White", "Green", "Yellow", "Purple", "(PRODUCT)RED"));
        List<String> c11p  = List.of("Silver", "Gold", "Space Gray", "Midnight Green");
        def("11pro",    List.of(64, 256, 512), c11p);
        def("11promax", List.of(64, 256, 512), c11p);
        List<String> c12   = List.of("Black", "White", "Blue", "Green", "Purple", "(PRODUCT)RED");
        def("12",       List.of(64, 128, 256), c12);
        def("12mini",   List.of(64, 128, 256), c12);
        List<String> c12p  = List.of("Silver", "Gold", "Graphite", "Pacific Blue");
        def("12pro",    List.of(128, 256, 512), c12p);
        def("12promax", List.of(128, 256, 512), c12p);
        List<String> c13   = List.of("Starlight", "Midnight", "Blue", "Pink", "Green", "(PRODUCT)RED");
        def("13",       List.of(128, 256, 512), c13);
        def("13mini",   List.of(128, 256, 512), c13);
        List<String> c13p  = List.of("Silver", "Gold", "Graphite", "Sierra Blue", "Alpine Green");
        def("13pro",    List.of(128, 256, 512, 1024), c13p);
        def("13promax", List.of(128, 256, 512, 1024), c13p);
        List<String> c14   = List.of("Midnight", "Starlight", "Blue", "Purple", "Yellow", "(PRODUCT)RED");
        def("14",       List.of(128, 256, 512), c14);
        def("14plus",   List.of(128, 256, 512), c14);
        List<String> c14p  = List.of("Silver", "Gold", "Space Black", "Deep Purple");
        def("14pro",    List.of(128, 256, 512, 1024), c14p);
        def("14promax", List.of(128, 256, 512, 1024), c14p);
        List<String> c15   = List.of("Black", "Blue", "Green", "Yellow", "Pink");
        def("15",       List.of(128, 256, 512), c15);
        def("15plus",   List.of(128, 256, 512), c15);
        List<String> c15p  = List.of("Natural Titanium", "Blue Titanium", "White Titanium", "Black Titanium");
        def("15pro",    List.of(128, 256, 512, 1024), c15p);
        def("15promax", List.of(256, 512, 1024), c15p);
        List<String> c16   = List.of("Black", "White", "Pink", "Teal", "Ultramarine");
        def("16",       List.of(128, 256, 512), c16);
        def("16plus",   List.of(128, 256, 512), c16);
        def("16e",      List.of(128, 256, 512), List.of("Black", "White"));
        List<String> c16p  = List.of("Black Titanium", "White Titanium", "Natural Titanium", "Desert Titanium");
        def("16pro",    List.of(128, 256, 512, 1024), c16p);
        def("16promax", List.of(256, 512, 1024), c16p);
        def("17",       List.of(256, 512), List.of("Black", "White", "Sage", "Mist Blue", "Lavender"));
        def("air",      List.of(256, 512, 1024), List.of("Space Black", "Cloud White", "Light Gold", "Sky Blue"));
        List<String> c17p  = List.of("Silver", "Cosmic Orange", "Deep Blue");
        def("17pro",    List.of(256, 512, 1024), c17p);
        def("17promax", List.of(256, 512, 1024, 2048), c17p);
    }

    public static final List<String> COLORES_TODOS = COLORES.values().stream()
            .flatMap(List::stream).distinct().sorted().toList();

    public static final List<Integer> CAPACIDADES_TODAS = CAPACIDADES.values().stream()
            .flatMap(List::stream).distinct().sorted().toList();

    public static List<String> coloresDe(String modeloInterno) {
        return modeloInterno == null ? List.of() : COLORES.getOrDefault(modeloInterno, List.of());
    }

    public static List<Integer> capacidadesDe(String modeloInterno) {
        return modeloInterno == null ? List.of() : CAPACIDADES.getOrDefault(modeloInterno, List.of());
    }

    public static boolean esColorOficial(String color) {
        return color != null && COLORES_TODOS.contains(color);
    }
}
```

- [ ] **Paso 4: Verde + suite completa + commit (repo RAÍZ)**

Run: `mvn -q test` → verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(catalogo): matriz modelo→colores oficiales y capacidades (CatalogoAtributos)"
```

---

### Tarea 5: Cliente — `ColorMapper` + helper eSIM + modelos/DAOs (TDD)

**Files:**
- Create: `src/main/java/com/reparaciones/utils/ColorMapper.java` + `src/test/java/com/reparaciones/utils/ColorMapperTest.java`
- Modify: `controllers/ModeloMapper.java` + `ModeloMapperTest.java`, `models/TelefonoInventario.java`, `models/Importacion.java`, `dao/TelefonoDAO.java`
- Create: `dao/ColorEquivalenciaDAO.java`

**Interfaces:**
- Consumes: `CatalogoAtributos` (T4), endpoints de T3.
- Produces (para T6-T9):
  - `ColorMapper.normalizar(String)` → minúsculas `[a-z0-9]` (`"(PRODUCT)RED"` → `"productred"`).
  - `ColorMapper.resolver(String textoColor, String modeloInterno, Map<String,String> equivalencias)` → color oficial de la paleta del modelo o `null` (equivalencias con clave normalizada). Lógica: (1) match directo normalizado contra la paleta del modelo; (2) equivalencia → válida solo si el resultado está en la paleta; (3) `null` = sin mapear. Si `modeloInterno` es null/desconocido (paleta vacía): (1) match contra `COLORES_TODOS`, (2) equivalencia sin filtro de paleta.
  - `ModeloMapper.esEsim(String textoModelo)` → `normalizar(texto).contains("esim")`.
  - `models.TelefonoInventario`: campo `esEsim` + `isEsEsim()/setEsEsim`.
  - `models.Importacion.TelefonoImport`: componente final `boolean esEsim` (espejo del servidor T2).
  - `dao.ColorEquivalenciaDAO.getAll()` → `Map<String,String>` (normalizado→oficial) y `guardar(textoExterno, colorOficial)` (calco de `EquivalenciaModeloDAO`, endpoints `/api/colores/equivalencias`).
  - `dao.TelefonoDAO.actualizarAtributos(...)`: parámetro `Boolean esEsim` tras `gradoPropio` (entra en el body como `esEsim`).

- [ ] **Paso 1: Tests que fallan**

`ColorMapperTest.java`:
```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColorMapperTest {

    @Test void normaliza() {
        assertEquals("productred", ColorMapper.normalizar("(PRODUCT)RED"));
        assertEquals("naturaltitanium", ColorMapper.normalizar("Natural Titanium"));
    }

    @Test void matchDirectoContraPaletaDelModelo() {
        assertEquals("White", ColorMapper.resolver("white", "12", Map.of()));
        assertEquals("(PRODUCT)RED", ColorMapper.resolver("Product Red", "12", Map.of("productred", "(PRODUCT)RED")));
    }

    @Test void colorRealPeroFueraDePaletaNoResuelve() {
        // "Blue" es oficial en el 12, pero el 15 Pro no lo tiene: debe quedar sin mapear
        assertNull(ColorMapper.resolver("Blue", "15pro", Map.of()));
    }

    @Test void equivalenciaSoloValeSiCaeEnLaPaleta() {
        Map<String, String> eq = Map.of("azul", "Blue Titanium");
        assertEquals("Blue Titanium", ColorMapper.resolver("azul", "15pro", eq));
        assertNull(ColorMapper.resolver("azul", "12", eq)); // Blue Titanium no existe en el 12
    }

    @Test void sinModeloUsaLaUnionCompleta() {
        assertEquals("Sierra Blue", ColorMapper.resolver("sierra blue", null, Map.of()));
    }

    @Test void nuloYVacioDevuelvenNull() {
        assertNull(ColorMapper.resolver(null, "12", Map.of()));
        assertNull(ColorMapper.resolver("  ", "12", Map.of()));
    }
}
```

En `ModeloMapperTest.java` añadir:
```java
    @Test void detectaEsimEnElTextoDelProveedor() {
        assertTrue(ModeloMapper.esEsim("iPhone 15 eSIM"));
        assertTrue(ModeloMapper.esEsim("iphone 16 Pro ESIM"));
        assertFalse(ModeloMapper.esEsim("iPhone 15"));
        assertFalse(ModeloMapper.esEsim(null));
    }
```

- [ ] **Paso 2: Ver que fallan** — `mvn -q test -Dtest='ColorMapperTest,ModeloMapperTest'` → no compila.

- [ ] **Paso 3: Implementar.**

`utils/ColorMapper.java`:
```java
package com.reparaciones.utils;

import com.reparaciones.controllers.CatalogoAtributos;

import java.util.List;
import java.util.Map;

/** Resolución de textos de color de proveedor al color oficial (spec atributos SKU). */
public final class ColorMapper {

    private ColorMapper() {}

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return texto.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Color oficial para el texto del proveedor, restringido a la paleta del modelo
     * (o a la unión completa si el modelo es null/desconocido). null = sin mapear.
     */
    public static String resolver(String textoColor, String modeloInterno, Map<String, String> equivalencias) {
        String norm = normalizar(textoColor);
        if (norm.isEmpty()) return null;
        List<String> paleta = CatalogoAtributos.coloresDe(modeloInterno);
        if (paleta.isEmpty()) paleta = CatalogoAtributos.COLORES_TODOS;
        for (String oficial : paleta) {
            if (normalizar(oficial).equals(norm)) return oficial;
        }
        String porEquivalencia = equivalencias.get(norm);
        return porEquivalencia != null && paleta.contains(porEquivalencia) ? porEquivalencia : null;
    }
}
```

En `ModeloMapper.java` añadir:
```java
    /** true si el texto de modelo del proveedor indica variante eSIM (regla spec atributos SKU). */
    public static boolean esEsim(String textoModelo) {
        return textoModelo != null && normalizar(textoModelo).contains("esim");
    }
```

`models/TelefonoInventario.java` (cliente): campo `private boolean esEsim;` tras `gradoPropio` + `isEsEsim()/setEsEsim(boolean)` junto a los demás accessors.

`models/Importacion.java`: `TelefonoImport` pasa a `(String imei, String modelo, Integer storageGb, String color, String gradoProveedor, BigDecimal precioCompra, String divisa, BigDecimal precioCompraEur, boolean esEsim)`. Arreglar los DOS call-sites (`ImportadorLoteDialog`, `AltaManualLoteDialog`) añadiendo `false` como último argumento POR AHORA (T7/T8 ponen el valor real) — así esta tarea compila sola.

`dao/ColorEquivalenciaDAO.java`: calco exacto de `EquivalenciaModeloDAO` (leerlo primero) con endpoints `/api/colores/equivalencias` y claves JSON `textoExterno`/`colorOficial`.

`dao/TelefonoDAO.java`: `actualizarAtributos` añade `Boolean esEsim` tras `gradoPropio` y `body.put("esEsim", esEsim);`. Arreglar el call-site de `AgrupadoController.abrirDialogoAtributos` pasando `null` POR AHORA (T9 pone el valor real).

- [ ] **Paso 4: Verde + suite completa + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(cliente): ColorMapper con paleta por modelo, flag eSIM y DAOs/modelos de atributos"
```

---

### Tarea 6: Cliente — `ClasificadorImportacion` v2 (color sin mapear + aviso de capacidad, TDD)

**Files:**
- Modify: `src/main/java/com/reparaciones/utils/ClasificadorImportacion.java` + `ClasificadorImportacionTest.java`

**Interfaces:**
- Consumes: `ColorMapper.resolver` (T5), `CatalogoAtributos.capacidadesDe` (T4).
- Produces (para T7): firma nueva `clasificar(List<Fila> filas, Map<String,String> mapeoModelos, Map<String,String> equivalenciasColor, Map<String,VerificacionImei> existentes)`; `Destino` gana `COLOR_SIN_MAPEAR`; `FilaClasificada` gana el componente `String colorOficial` (tras `modeloInterno`): `FilaClasificada(Fila fila, Destino destino, String modeloInterno, String colorOficial, String detalle)`. Reglas: color no resuelto ⇒ excluida `COLOR_SIN_MAPEAR` (tras el chequeo de modelo); storage fuera de `capacidadesDe(modelo)` ⇒ importable con `detalle = "Storage <n> GB no es oficial del modelo"` (concatenado al detalle de re-entrada si lo hay, separado por " · "). Fila sin color (columna vacía) NO se bloquea: `colorOficial = null`.

- [ ] **Paso 1: Tests nuevos que fallan** (añadir; adaptar el helper `fila(...)` si hace falta un color por parámetro: `fila(imei, modelo, color, batch, status)` — actualizar los tests existentes al nuevo orden y a la firma nueva con `Map.of()` de equivalencias de color):

```java
    @Test void colorReconocidoSeResuelveAlOficial() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "white", "B1", 0)),
                MAPEO, Map.of(), Map.of());
        assertEquals("White", plan.lotes().get(0).filas().get(0).colorOficial());
    }

    @Test void colorDesconocidoBloqueaLaFila() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "Blanco Roto", "B1", 0)),
                MAPEO, Map.of(), Map.of());
        assertTrue(plan.lotes().isEmpty());
        assertEquals(ClasificadorImportacion.Destino.COLOR_SIN_MAPEAR, plan.excluidas().get(0).destino());
    }

    @Test void colorVacioNoBloquea() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", null, "B1", 0)),
                MAPEO, Map.of(), Map.of());
        assertEquals(1, plan.lotes().get(0).filas().size());
        assertNull(plan.lotes().get(0).filas().get(0).colorOficial());
    }

    @Test void capacidadFueraDePaletaAvisaPeroEntra() {
        // helper con storage configurable: fila de 276 GB en un iPhone 12
        var f = new LoteXlsxParser.Fila(7, "352513424271910", "Apple", "iPhone 12", 276, "White", null,
                new java.math.BigDecimal("100.00"), "Hy5", "B1", 0);
        var plan = ClasificadorImportacion.clasificar(List.of(f), MAPEO, Map.of(), Map.of());
        var fc = plan.lotes().get(0).filas().get(0);
        assertEquals(ClasificadorImportacion.Destino.NUEVO, fc.destino());
        assertTrue(fc.detalle().contains("276 GB no es oficial"));
    }
```

- [ ] **Paso 2: Ver que fallan** — `mvn -q test -Dtest=ClasificadorImportacionTest`.

- [ ] **Paso 3: Implementar** en `clasificar`, tras el bloque de `MODELO_SIN_MAPEAR` y ANTES del chequeo de conflicto:

```java
            String colorOficial = null;
            if (f.color() != null && !f.color().isBlank()) {
                colorOficial = ColorMapper.resolver(f.color(), interno, equivalenciasColor);
                if (colorOficial == null) {
                    excluidas.add(new FilaClasificada(f, Destino.COLOR_SIN_MAPEAR, interno, null,
                            "Color \"" + f.color() + "\" sin correspondencia en la paleta del modelo"));
                    continue;
                }
            }
            String avisoStorage = null;
            if (f.storageGb() != null && !CatalogoAtributos.capacidadesDe(interno).contains(f.storageGb())) {
                avisoStorage = "Storage " + f.storageGb() + " GB no es oficial del modelo";
            }
```
y al construir la fila importable, componer `detalle`: re-entrada y/o aviso de storage unidos con `" · "` (null si no hay nada). Propagar `colorOficial` en TODOS los `new FilaClasificada(...)` (en las exclusiones previas al cálculo va `null`).

- [ ] **Paso 4: Suite completa verde + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): clasificador con colores oficiales por paleta y aviso de capacidad no oficial"
```

---

### Tarea 7: Cliente — `ImportadorLoteDialog`: bloque "Colores sin mapear" + eSIM + color/aviso en tablas

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/ImportadorLoteDialog.java`

**Interfaces:**
- Consumes: T5 (`ColorEquivalenciaDAO`, `ColorMapper`, `ModeloMapper.esEsim`), T6 (firma nueva del clasificador).

- [ ] **Paso 1: Implementar** (leer primero el bloque "Modelos sin mapear" existente y calcarlo):
  1. En la carga inicial (hilo `importador-lote-carga`), cargar también `new ColorEquivalenciaDAO().getAll()` y pasarlo a la clasificación.
  2. Llamar a `clasificar(filas, mapeoModelos, equivalenciasColor, existentes)` (firma T6) en TODOS los call-sites (carga y `reclasificar()`).
  3. **Bloque "Colores sin mapear"**, idéntico al de modelos, debajo de él: visible solo si hay excluidas `COLOR_SIN_MAPEAR`; una fila por TEXTO de color distinto con label `"<texto>" (N filas)` y botón `Elegir color…` que abre un `ChoiceDialog<String>` con `CatalogoAtributos.coloresDe(modeloInterno)` de la fila (o `COLORES_TODOS` si null); al elegir → `ColorEquivalenciaDAO.guardar(ColorMapper.normalizar(texto), oficial)` en hilo (`guardar-equivalencia-color`) → recargar equivalencias → `reclasificar()`. Guard de texto vacío: no ofrecer resolver (lección F2a).
  4. La columna **Color** de la tabla del lote muestra `colorOficial` (ya no el texto crudo); la columna **Detalle** ya muestra el aviso de storage vía `detalle` (nada que hacer).
  5. Al construir `TelefonoImport`: `color = fc.colorOficial()`, y el último argumento pasa de `false` a `ModeloMapper.esEsim(fc.fila().modeloTexto())`.
  6. La tabla "No entran" no cambia (el `Destino.COLOR_SIN_MAPEAR` sale por el `toString`/texto que use el patrón existente de destinos — seguir el mismo mecanismo que `MODELO_SIN_MAPEAR`; si hay un switch de etiquetas, añadir "Color sin mapear").

- [ ] **Paso 2: Compilar + suite + commit (repo RAÍZ)** — sin test propio (pegamento UI, precedente F2a).

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): resolución de colores con equivalencias recordadas y flag eSIM automático"
```

---

### Tarea 8: Cliente — `AltaManualLoteDialog`: selectores por modelo + check eSIM

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/AltaManualLoteDialog.java`

**Interfaces:**
- Consumes: T4 (paletas), T5 (`TelefonoImport.esEsim`).

- [ ] **Paso 1: Implementar** (mantener el patrón visual actual del diálogo):
  1. **Storage**: el `TextField` numérico pasa a `ComboBox<Integer>` editable=false con primera opción vacía (`null` = sin dato). Items: si hay modelo común elegido → `CatalogoAtributos.capacidadesDe(modelo)`; si no → `CAPACIDADES_TODAS`. Al cambiar el modelo (botón `Modelo…`), repoblar el combo conservando la selección si sigue siendo válida.
  2. **Color**: el `TextField` pasa a `ComboBox<String>` con primera opción vacía; items `coloresDe(modelo)` o `COLORES_TODOS` sin modelo; repoblar al cambiar modelo igual que storage.
  3. **Check `CheckBox "eSIM"`** como atributo común opcional (default sin marcar), junto al grado proveedor.
  4. Al construir `TelefonoImport`: `storageGb`/`color` desde los combos (null si vacíos), último argumento = `cbEsim.isSelected()`.

- [ ] **Paso 2: Compilar + suite + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): alta manual con selectores de color/capacidad por modelo y check eSIM"
```

---

### Tarea 9: Cliente — `abrirDialogoAtributos`: selectores filtrados + check eSIM

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/AgrupadoController.java` (método `abrirDialogoAtributos`, ~línea 1440)

**Interfaces:**
- Consumes: T4 (paletas), T5 (`TelefonoDAO.actualizarAtributos(..., Boolean esEsim, ...)`), `TelefonoInventario.isEsEsim()`.

- [ ] **Paso 1: Implementar**:
  1. `tfStorage` (TextField) → `ComboBox<Integer>` con opción vacía + `capacidadesDe(modeloSel[0])`, preseleccionado al valor actual **aunque no esté en la paleta** (añadirlo como item extra si hace falta — un dato legacy no se pierde al abrir el diálogo). Al cambiar de modelo con `Cambiar…`, repoblar conservando si es válido.
  2. `tfColor` (TextField) → `ComboBox<String>` con opción vacía + `coloresDe(modeloSel[0])`, mismo tratamiento del valor actual fuera de paleta y repoblado al cambiar modelo.
  3. `CheckBox "eSIM"` inicializado con `t.isEsEsim()`, en su propia fila del form (tras el grado propio).
  4. En Guardar: `telefonoDAO.actualizarAtributos(t.getImei(), modeloSel[0], storage, color, gradoProv, gradoPropio, cbEsim.isSelected(), t.getTelefonoUpdatedAt())` (el `null` provisional de T5 desaparece). El try/catch existente (NFE incluida) no se toca.

- [ ] **Paso 2: Compilar + suite + commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(inventario): edición de atributos con paletas por modelo y flag eSIM"
```

---

### Tarea 10: Chasis — verificación + script de renombrado al color oficial

**Files:**
- Create: `gestion-reparaciones-servidor/sql/renombrar-chasis-colores.sql`

**Interfaces:**
- Consumes: vocabulario de `CatalogoAtributos` (T4). Lo aplica EL USUARIO.

- [ ] **Paso 1: Verificar que ningún código busca chasis por texto**: `grep -rn "chasis" gestion-reparaciones-cliente/src/main/java gestion-reparaciones-servidor/src/main/java --include=*.java -i` y revisar cada hit: solo debe haber usos de UI/flag `ES_CHASIS`, nunca matching contra `Componente.TIPO`. Anotar el resultado en el informe (si apareciera matching por texto → PARAR y reportar BLOCKED).

- [ ] **Paso 2: Escribir `sql/renombrar-chasis-colores.sql`** — REPLACEs de palabras de color castellano→oficial SOLO en filas de chasis, precedidos de un SELECT de vista previa que el usuario revisa ANTES de los UPDATEs:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- renombrar-chasis-colores.sql — TIPOs de chasis al vocabulario oficial (spec SKU)
-- 1) Ejecutar el SELECT y revisar el antes/después propuesto.
-- 2) Si todo cuadra, ejecutar los UPDATEs. Los añadidos que falten se hacen a mano.
-- Las referencias van por ID_COM: renombrar TIPO no rompe nada.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Vista previa (no modifica nada)
SELECT ID_COM, TIPO,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
           TIPO, 'negro', 'Black'), 'blanco', 'White'), 'rojo', '(PRODUCT)RED'),
           'azul', 'Blue'), 'verde', 'Green'), 'amarillo', 'Yellow'),
           'rosa', 'Pink'), 'morado', 'Purple') AS PROPUESTO
  FROM Componente
 WHERE TIPO LIKE '%chasis%';

-- UPDATEs (mismos reemplazos; ejecutar tras validar la vista previa)
UPDATE Componente SET TIPO = REPLACE(TIPO, 'negro',    'Black')        WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%negro%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'blanco',   'White')        WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%blanco%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'rojo',     '(PRODUCT)RED') WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%rojo%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'azul',     'Blue')         WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%azul%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'verde',    'Green')        WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%verde%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'amarillo', 'Yellow')       WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%amarillo%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'rosa',     'Pink')         WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%rosa%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'morado',   'Purple')       WHERE TIPO LIKE '%chasis%' AND TIPO LIKE '%morado%';

-- Verificación: no debe quedar ningún chasis con color en castellano
SELECT ID_COM, TIPO FROM Componente
 WHERE TIPO LIKE '%chasis%'
   AND (TIPO LIKE '%negro%' OR TIPO LIKE '%blanco%' OR TIPO LIKE '%rojo%' OR TIPO LIKE '%azul%'
        OR TIPO LIKE '%verde%' OR TIPO LIKE '%amarillo%' OR TIPO LIKE '%rosa%' OR TIPO LIKE '%morado%');
```
NOTA en el propio fichero: los colores multi-palabra oficiales (Sierra Blue, Titanium…) probablemente no existan en castellano en los TIPO actuales; los casos que la vista previa no cubra se corrigen a mano en la UI de SKUs. El usuario decide en la vista previa.

- [ ] **Paso 3: Commit (repo SERVIDOR)**

```bash
git add sql/renombrar-chasis-colores.sql && git commit -m "feat(sql): script de renombrado de chasis al vocabulario oficial de colores (vista previa + updates)"
```

---

### Tarea 11: Cierre — suites, arranque, smoke y merge (con el usuario)

- [ ] Suites finales verdes en ambos repos (`mvn -q test`).
- [ ] Review final de rama (ambos repos) con paquete `review-package` desde los merge-base.
- [ ] El usuario aplica `sql/migracion-atributos-sku.sql` (y cuando quiera, `renombrar-chasis-colores.sql` con su vista previa).
- [ ] Validar arranque del servidor (`mvn -q -DskipTests package` + `java -jar`, contexto limpio) — no hay test de contexto.
- [ ] Servidor: merge a main + push CON OK del usuario → build VM.
- [ ] Smoke corto con el usuario (cliente en rama): re-importar el xlsx de smoke de Hy5 (los eSIM deben marcar flag; "white"→White resuelto solo; storage 276 si se fuerza → aviso), alta manual con selectores, editar atributos con paletas, y REVISAR LA MATRIZ de colores/capacidades (especialmente serie 17) — es dato de negocio del usuario.
- [ ] Limpieza del lote de smoke (script existente `Apuntes/limpieza-smoke-f2a.sql` adaptando el batch).
- [ ] Cliente: merge a main + push SOLO con OK. Actualizar MER (`GRADO_PROPIO` sin A+ ya anotado; añadir `ES_ESIM` y `Color_equivalencia`). Marcar checkboxes en `Apuntes/plan-futuro.md`.

---

## Self-review (hecho al escribir el plan)

- **Cobertura de spec §3 mini-fase**: ES_ESIM (T1,T2,T5,T7,T8,T9) ✓ · matriz colores+capacidades (T4) ✓ · Color_equivalencia + endpoints (T1,T3,T5) ✓ · regla esim automática (T5,T7) ✓ · bloque "Colores sin mapear" (T7) ✓ · aviso storage no bloqueante (T6) ✓ · alta manual selectores + eSIM (T8) ✓ · editar atributos selectores + eSIM (T9) ✓ · renombrado chasis con verificación previa (T10) ✓ · ALTER grado sin A+ + UPDATE (T1) ✓ · derivación SKU EXCLUIDA ✓.
- **Contratos**: `esEsim` idéntico en TelefonoImport cliente/servidor (T2 ↔ T5), AtributosRequest (T2 ↔ T5/T9), equivalencias `textoExterno/colorOficial` (T3 ↔ T5). `FilaClasificada` nueva de T6 consumida con esa forma en T7.
- **Compilabilidad por tarea**: T5 arregla call-sites con `false`/`null` provisionales que T7/T9 sustituyen — cada tarea deja la suite verde.
- **Riesgo señalado**: los DATOS de la matriz (T4) y el mapa castellano→oficial del script de chasis (T10) son dato de negocio — validación explícita del usuario en T10 (vista previa SQL) y T11 (smoke).
