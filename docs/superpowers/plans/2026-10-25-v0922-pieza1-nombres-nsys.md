# 0.9.22 pieza 1 — Leer mejor el maestro y NSYS · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el ERP sepa siempre cómo escribe NSYS el color de un producto (para «Poner en NSYS» y para deducir el color del
chasis) y guarde el coste de cada móvil al importar, sin tocar el Excel del maestro ni la web.

**Architecture:** el maestro guarda también su columna «Product Name» (`Nsys_producto.NOMBRE`), que da el nombre en inglés de
cada color de cada modelo; una clase pura nueva (`GrafiasNsys`) sabe cómo escribe NSYS cada nombre de color a partir de los
Goods del espejo, y otra (`ProductoNsys`) compone el producto a poner. `ColorChasis` usa el nombre del maestro cuando un color no
tiene móviles en NSYS. El importador guarda el «Total cost» de cada lote en `Nsys_movil.COSTE` con una actualización aparte (sin
tocar `MovilNsys` ni el comparador).

**Tech Stack:** Java 17, Spring Boot (JdbcTemplate), Apache POI (lectura de xlsx), JUnit 5 + Mockito, MariaDB 11.

**Spec:** `docs/superpowers/specs/2026-10-25-v0922-plantillas-nsys-design.md` (raíz), §2 entero. La pieza 2 (§3-§4) va en otro plan.

## Global Constraints

- **El Excel del maestro nunca se edita**: el ERP solo lo lee (spec §1, «Maestro: solo se lee mejor; nunca se edita»).
- **Sin cambios en la web** en esta pieza (spec §2.4: «La web no cambia en esta pieza»).
- **Migración aditiva** (`ALTER TABLE … ADD COLUMN`), con vista previa y comprobación final, como `sql/migracion-sku-taller.sql`.
- Columnas: `Nsys_producto.NOMBRE VARCHAR(150) NULL` y `Nsys_movil.COSTE DECIMAL(10,2) NULL` (spec §2.4).
- Orden de la grafía del color (spec §2.1): 1) Goods más usado de ese color en el mismo modelo, capacidad y eSIM; 2) ese color en
  el mismo modelo con otra capacidad; 3) en cualquier modelo; la más usada si hay varias. **El paso 4 (el nombre del maestro tal
  cual) NO se usa en esta pieza**: sin grafía de NSYS, `ponerProducto` sigue null («búscalo a mano»).
- «Color en ingles» del maestro **no se usa** (es la familia, no el nombre).
- El modelo y la capacidad del producto salen del **Goods del propio móvil** en NSYS.
- El coste **no sale en ninguna pantalla ni en la API de listas** (spec §2.3).
- El límite de 15 s por petición no se sube.
- Datos de prueba inventados (repositorios públicos).
- Comentarios y nombres en castellano, con el estilo del código de alrededor. Mensajes de commit en castellano, sin tildes como
  los anteriores, y **sin línea Co-Authored-By**.

## Entorno

- Servidor: rama **`dev_v0922_nombres_nsys`** desde `main` (`b827ac0`), en el worktree
  `/c/Users/dev/Documents/wt/servidor-0922-nombres`:
  `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add -b dev_v0922_nombres_nsys /c/Users/dev/Documents/wt/servidor-0922-nombres main`
- Web (solo el CHANGELOG, Task 8): rama `dev_v0922_nombres_nsys` desde `main` en `/c/Users/dev/Documents/wt/web-0922-nombres`:
  `git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add -b dev_v0922_nombres_nsys /c/Users/dev/Documents/wt/web-0922-nombres main`
- Maven (Bash, desde el worktree del servidor):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH" && mvn -q -o test -Dtest=NombreDelTest`
  (sin `-Dtest` para la suite entera; 1401 pruebas al empezar).
- Rutas: `S = src/main/java/com/reparaciones/servidor/nsys/`, `T = src/test/java/com/reparaciones/servidor/nsys/`.

## Ficheros

| Fichero | Qué |
|---|---|
| `sql/migracion-nombres-y-coste-nsys.sql` (nuevo) | La migración aditiva de las dos columnas. |
| `sql/crear_bd.sql` | Las dos columnas en `Nsys_producto` y `Nsys_movil`. |
| `docs/schema.md` | Descripción de las dos columnas. |
| `S/AnchosNsys.java` | `NOMBRE = 150`. |
| `S/ProductoMaestro.java` | Campo `nombre` (con constructor sin él). |
| `S/LectorMaestro.java` | Lee «Product Name» (columna opcional, aviso en el log si falta). |
| `S/FilaItems.java` | Campo `coste` (con constructor sin él). |
| `S/LectorItems.java` | Lee «Total cost». |
| `S/NsysDAO.java` | `NOMBRE` en productos; `cargarCostes`, `guardarCostes`, `productosSinNombre`, `goodsContados`. |
| `S/ImportadorNsys.java` | Guarda los costes tras cada Items; recarga el maestro una vez si no tiene nombres. |
| `S/GrafiasNsys.java` (nuevo) | Cómo escribe NSYS cada color; el color de un Goods; cambiar el color de un Goods. |
| `S/Maestro.java` | `nombreColor`. |
| `S/ProductoNsys.java` (nuevo) | El producto a poner en NSYS para un color. |
| `S/TallerNsys.java` | `productoAPoner`, `porPasar` y `opciones` con `ProductoNsys`. |
| `S/ColorChasis.java` | Nombre del maestro para los colores sin móviles en NSYS. |
| `S/TallerReparaciones.java` | `colorDeChasis` le pasa los nombres del maestro. |
| Pruebas | `T/DdlNombresCosteTest.java` (nuevo), `T/GrafiasNsysTest.java` (nuevo), `T/ProductoNsysTest.java` (nuevo), y `LectorMaestroTest`, `LectorItemsTest`, `XlsxDePrueba`, `NsysDAOTest`, `ImportadorNsysTest`, `MaestroTest`, `TallerDePrueba`, `TallerNsysTest`, `ColorChasisTest`, `TallerReparacionesTest`. |

---

### Task 1: Migración, DDL y anchos

**Files:**
- Create: `sql/migracion-nombres-y-coste-nsys.sql`
- Modify: `sql/crear_bd.sql` (CREATE TABLE `Nsys_movil` ~línea 454 y `Nsys_producto` ~línea 499)
- Modify: `S/AnchosNsys.java`
- Modify: `docs/schema.md` (líneas de `Nsys_movil` y `Nsys_producto`)
- Test: `T/DdlNombresCosteTest.java` (nuevo)

**Interfaces:**
- Produces: `AnchosNsys.NOMBRE` (int, 150), que usa la Task 2.

- [ ] **Step 1: Write the failing test**

`T/DdlNombresCosteTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Las columnas de la 0.9.22 (spec «plantillas NSYS» §2.4): la migración, que es la que se aplica en la VM, y crear_bd.sql dicen
 *  lo mismo, y AnchosNsys corta el nombre del maestro a su ancho. */
class DdlNombresCosteTest {

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    /** «VARCHAR(150) NULL COMMENT '…'» de la migración: lo que va entre «ADD COLUMN <columna>» y «AFTER». */
    private static String deLaMigracion(String columna) throws Exception {
        Matcher m = Pattern.compile("ADD COLUMN " + columna + " (.+?) AFTER ").matcher(fichero("migracion-nombres-y-coste-nsys.sql"));
        assertTrue(m.find(), columna + " en la migración");
        return m.group(1).strip();
    }

    /** Lo mismo en crear_bd.sql, dentro del CREATE TABLE de {@code tabla} (hay otras columnas NOMBRE en otras tablas): la línea
     *  de la columna sin el nombre ni la coma final. */
    private static String deCrearBd(String tabla, String columna) throws Exception {
        String sql = fichero("crear_bd.sql");
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla + " en crear_bd.sql");
        String bloque = sql.substring(desde, sql.indexOf(");", desde));
        Matcher m = Pattern.compile("(?m)^\\s+" + columna + "\\s+(.+?),?$").matcher(bloque);
        assertTrue(m.find(), columna + " en " + tabla);
        return m.group(1).strip();
    }

    @Test void laMigracionYCrearBdDicenLoMismo() throws Exception {
        assertEquals(deLaMigracion("NOMBRE"), deCrearBd("Nsys_producto", "NOMBRE"));
        assertEquals(deLaMigracion("COSTE"), deCrearBd("Nsys_movil", "COSTE"));
        assertTrue(deLaMigracion("COSTE").startsWith("DECIMAL(10,2) NULL"), deLaMigracion("COSTE"));
    }

    @Test void elNombreSeCortaAlAnchoDeSuColumna() throws Exception {
        assertEquals("VARCHAR(" + AnchosNsys.NOMBRE + ") NULL", deLaMigracion("NOMBRE").substring(0, "VARCHAR(150) NULL".length()));
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero("migracion-nombres-y-coste-nsys.sql");
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -o test -Dtest=DdlNombresCosteTest`
Expected: FAIL (no existe `sql/migracion-nombres-y-coste-nsys.sql`; no compila `AnchosNsys.NOMBRE`).

- [ ] **Step 3: Write the migration, the DDL and the width**

`sql/migracion-nombres-y-coste-nsys.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-nombres-y-coste-nsys.sql — 0.9.22 pieza 1, leer mejor el maestro y NSYS
-- (spec docs/superpowers/specs/2026-10-25-v0922-plantillas-nsys-design.md §2.4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.22. Aditiva: el servidor anterior no lee estas columnas.
-- NO idempotente (relanzar = error de columna existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) Los dos ALTER TABLE.
-- 3) Comprobación final: CREADAS debe dar 2.
-- Vuelta atrás: el servidor anterior funciona con las columnas puestas; no hace falta quitarlas.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Nsys_producto' AND COLUMN_NAME = 'NOMBRE') OR (TABLE_NAME = 'Nsys_movil' AND COLUMN_NAME = 'COSTE'));

-- El «Product Name» del maestro tal cual: da el nombre en inglés de cada color de cada modelo. Se rellena al recargar el maestro.
ALTER TABLE Nsys_producto
    ADD COLUMN NOMBRE VARCHAR(150) NULL COMMENT '«Product Name» del maestro tal cual (0.9.22)' AFTER CLAVE;

-- El «Total cost» de NSYS de cada lote: solo para la plantilla de color. Se rellena con la siguiente importación de Items.
ALTER TABLE Nsys_movil
    ADD COLUMN COSTE DECIMAL(10,2) NULL COMMENT '«Total cost» de NSYS de este lote (0.9.22)' AFTER SUPPLIER;

-- Comprobación final (no modifica nada): debe dar 2; si no, NO desplegar el servidor 0.9.22 (lee las dos columnas).
SELECT COUNT(*) AS CREADAS
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Nsys_producto' AND COLUMN_NAME = 'NOMBRE') OR (TABLE_NAME = 'Nsys_movil' AND COLUMN_NAME = 'COSTE'));
```

`sql/crear_bd.sql`: en `CREATE TABLE Nsys_movil`, después de la línea `    SUPPLIER         VARCHAR(80)  NULL,` añade:

```sql
    COSTE            DECIMAL(10,2) NULL COMMENT '«Total cost» de NSYS de este lote (0.9.22)',
```

y en `CREATE TABLE Nsys_producto`, después de la línea de `CLAVE` añade:

```sql
    NOMBRE          VARCHAR(150) NULL COMMENT '«Product Name» del maestro tal cual (0.9.22)',
```

`S/AnchosNsys.java`: en el bloque de `Nsys_producto`, debajo de `static final int CLAVE = 160;`:

```java
    static final int NOMBRE = 150;
```

`docs/schema.md`: cambia las dos líneas por estas:

```markdown
- `Nsys_movil` (0.9.17: la foto actual de NSYS, una fila por IMEI/SN y lote; desde la 0.9.22, con el coste de NSYS de cada lote —`COSTE`, solo para la plantilla de color—; sin precios de venta)
- `Nsys_producto` (0.9.17: el maestro de productos, «Goods + Grade» → modelo, color y SKU; desde la 0.9.22, con el «Product Name» tal cual —`NOMBRE`— para saber cómo se llama cada color en inglés)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -o test -Dtest=DdlNombresCosteTest+AnchosNsysTest+DdlTallerTest`
Expected: PASS (las de `AnchosNsysTest` y `DdlTallerTest` siguen igual).

- [ ] **Step 5: Commit**

```bash
git add sql/migracion-nombres-y-coste-nsys.sql sql/crear_bd.sql docs/schema.md src/main/java/com/reparaciones/servidor/nsys/AnchosNsys.java src/test/java/com/reparaciones/servidor/nsys/DdlNombresCosteTest.java
git commit -m "feat: migracion de NOMBRE en Nsys_producto y COSTE en Nsys_movil (0.9.22, pieza 1)"
```

---

### Task 2: El maestro guarda su «Product Name»

**Files:**
- Modify: `S/ProductoMaestro.java`
- Modify: `S/LectorMaestro.java`
- Modify: `S/NsysDAO.java` (`SQL_PRODUCTO`, `cargarProductos`, `reemplazarProductos`)
- Test: `T/LectorMaestroTest.java`, `T/NsysDAOTest.java`

**Interfaces:**
- Consumes: `AnchosNsys.NOMBRE` (Task 1).
- Produces: `ProductoMaestro.nombre()` (String, null si el maestro no lo trae); constructor de 11 argumentos
  `(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, nombre)` y el de 10
  de siempre (nombre null); `LectorMaestro.NOMBRE` = `"Product Name"`.

- [ ] **Step 1: Write the failing tests**

En `T/LectorMaestroTest.java`:

1. Cambia el ayudante `fila` para que la columna «Product Name» lleve el nombre sin el grado (hoy pone `"x"`):

```java
    private static List<Object> fila(String nombre, String familia, Object modelo, String color, String detalle,
                                     String web, String amazon) {
        return Arrays.asList(nombre, nombre.substring(0, nombre.lastIndexOf(' ')).strip(), familia, modelo, "128GB", color,
                detalle, web, amazon, "i13128Azul");
    }
```

2. En `leeSuHojaConModelosYSku`, el producto esperado lleva el nombre:

```java
        assertEquals(new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13", "13", "128GB", "Azul",
                "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false, "Apple iPhone 13 128GB Blue"), r.productos().get(0));
```

3. En `cadaCampoSeCortaAlAnchoDeSuColumna`, el segundo valor de `larga` pasa de `"x"` a `"N".repeat(160)` y la comprobación
   incluye el nombre:

```java
        List<Object> larga = Arrays.asList("Apple iPhone 13 " + "x".repeat(200), "N".repeat(160), "iPhone", "M".repeat(45),
                "C".repeat(25), "K".repeat(45), "D".repeat(25), "W".repeat(65), "Z".repeat(65), "S".repeat(65));
        ProductoMaestro p = leer(maestro(List.of(larga))).productos().get(0);
        assertEquals(List.of(160, 150, 40, 20, 40, 20, 60, 60, 60), java.util.stream.Stream.of(p.clave(), p.nombre(),
                p.modeloMaestro(), p.capacidad(), p.color(), p.calidadDetalle(), p.skuWeb(), p.skuAmazon(), p.skuSinGrado())
                .map(String::length).toList());
```

4. Pruebas nuevas al final de la clase:

```java
    @Test void unaClaveRepetidaConservaElNombreDelPrimero() throws Exception {
        ResultadoMaestro r = leer(maestro(List.of(
                fila("Apple iPhone 13 128GB Blue A", "iPhone", "13", "Azul", "A", "i13128AzulA", "i13128AzulA"),
                fila("Apple iPhone 13 128GB  Blue A", "iPhone", "13", "Azul", "A", "i13128AzulB", "i13128AzulB"))));
        assertEquals("Apple iPhone 13 128GB Blue", r.productos().get(0).nombre());
    }

    @Test void sinLaColumnaProductNameSeCargaIgualSinNombres() throws Exception {
        List<List<Object>> hoja = new ArrayList<>();
        hoja.add(new ArrayList<Object>(LectorMaestro.COLUMNAS));
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA",
                "i13128Azul"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        ProductoMaestro p = leer(XlsxDePrueba.libro(hojas)).productos().get(0);
        assertNull(p.nombre());
        assertEquals("i13128AzulA", p.skuWeb());
    }
```

En `T/NsysDAOTest.java`, cambia `reemplazarProductosBorraYCargaTodo` y añade una prueba:

```java
    @Test void reemplazarProductosBorraYCargaTodo() {
        dao.reemplazarProductos(List.of(new ProductoMaestro("clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true,
                "Apple iPhone 13 128GB Blue")));
        verify(jdbc).update("DELETE FROM Nsys_producto");
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_PRODUCTO), lote.capture());
        assertArrayEquals(new Object[]{"clave", "13", "13", "128GB", "Azul", "A", "w", "a", "s", true,
                "Apple iPhone 13 128GB Blue"}, lote.getValue().get(0));
        assertTrue(NsysDAO.SQL_PRODUCTO.contains("ES_ESIM, NOMBRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), NsysDAO.SQL_PRODUCTO);
    }

    @SuppressWarnings("unchecked")
    @Test void cargarProductosLeeElNombre() throws SQLException {
        dao.cargarProductos();
        ArgumentCaptor<RowMapper<ProductoMaestro>> mapeo = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(contains(", NOMBRE FROM Nsys_producto"), mapeo.capture());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CLAVE")).thenReturn("clave");
        when(rs.getString("NOMBRE")).thenReturn("Apple iPhone 13 128GB Blue");
        assertEquals("Apple iPhone 13 128GB Blue", mapeo.getValue().mapRow(rs, 0).nombre());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=LectorMaestroTest+NsysDAOTest`
Expected: FAIL de compilación (no hay constructor de 11 argumentos ni `nombre()`).

- [ ] **Step 3: Implement**

`S/ProductoMaestro.java` entero:

```java
package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_producto: lo que el maestro dice de un «Goods + Grade». {@code modelo} es el código del ERP (null si
 *  no está en el catálogo); {@code modeloMaestro}, el texto de la columna «Modelo»; {@code nombre}, la columna «Product Name»
 *  tal cual (0.9.22: el nombre del producto sin el grado, del que sale el nombre en inglés de cada color; null si el maestro no
 *  la trae). */
public record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                              String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim,
                              String nombre) {

    /** Sin el nombre del producto: las pruebas que no lo necesitan. */
    public ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                           String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim) {
        this(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, null);
    }
}
```

`S/LectorMaestro.java`:
- imports: añade `import org.slf4j.Logger;` y `import org.slf4j.LoggerFactory;`.
- debajo de `public static final String HOJA = …;`:

```java
    /** La columna con el nombre del producto sin el grado (0.9.22). Opcional: un maestro sin ella se carga como antes. */
    static final String NOMBRE = "Product Name";
    private static final Logger log = LoggerFactory.getLogger(LectorMaestro.class);
```

- en `Recolector.fila`, el bloque de la cabecera pasa a ser:

```java
            if (columnas == null) {
                columnas = HojaXlsx.columnas(celdas, COLUMNAS, "el maestro");
                if (!columnas.containsKey(NOMBRE))
                    log.warn("NSYS: el maestro no trae la columna «{}»: se carga sin nombres de producto", NOMBRE);
                return;
            }
```

- el `new ProductoMaestro(…)` de la fila añade el nombre al final:

```java
            ProductoMaestro p = new ProductoMaestro(Normalizacion.clave(nombre), modelo, modeloMaestro,
                    valor(celdas, "Capacidad", AnchosNsys.CAPACIDAD), valor(celdas, "Color", AnchosNsys.COLOR),
                    valor(celdas, "Calidad Detalle", AnchosNsys.CALIDAD_DETALLE),
                    valor(celdas, "SKU (stock web)", AnchosNsys.SKU), valor(celdas, "SKU (Amazon)", AnchosNsys.SKU),
                    valor(celdas, "SKU sin grado", AnchosNsys.SKU), Normalizacion.esEsim(nombre),
                    valor(celdas, NOMBRE, AnchosNsys.NOMBRE));
```

- y el del conflicto conserva el nombre del primero:

```java
                productos.put(p.clave(), new ProductoMaestro(previo.clave(), previo.modelo(), previo.modeloMaestro(),
                        previo.capacidad(), previo.color(), previo.calidadDetalle(), null, null, null, previo.esEsim(),
                        previo.nombre()));
```

(`valor(celdas, NOMBRE, …)` con la columna ausente da null: `HojaXlsx.valor` devuelve null con índice null.)

`S/NsysDAO.java`:

```java
    static final String SQL_PRODUCTO = "INSERT INTO Nsys_producto (CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR,"
            + " CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON, SKU_SIN_GRADO, ES_ESIM, NOMBRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
```

```java
    public List<ProductoMaestro> cargarProductos() {
        return jdbc.query("SELECT CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON,"
                        + " SKU_SIN_GRADO, ES_ESIM, NOMBRE FROM Nsys_producto",
                (rs, n) -> new ProductoMaestro(rs.getString("CLAVE"), rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"),
                        rs.getString("CAPACIDAD"), rs.getString("COLOR"), rs.getString("CALIDAD_DETALLE"),
                        rs.getString("SKU_WEB"), rs.getString("SKU_AMAZON"), rs.getString("SKU_SIN_GRADO"),
                        rs.getBoolean("ES_ESIM"), rs.getString("NOMBRE")));
    }
```

y en `reemplazarProductos` el array lleva `p.nombre()` al final:

```java
            jdbc.batchUpdate(SQL_PRODUCTO, lote.stream().map(p -> new Object[]{p.clave(), p.modelo(), p.modeloMaestro(),
                    p.capacidad(), p.color(), p.calidadDetalle(), p.skuWeb(), p.skuAmazon(), p.skuSinGrado(), p.esEsim(),
                    p.nombre()}).toList());
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=LectorMaestroTest+NsysDAOTest+ImportadorNsysTest+MaestroTest+TraductorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/ProductoMaestro.java src/main/java/com/reparaciones/servidor/nsys/LectorMaestro.java src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/test/java/com/reparaciones/servidor/nsys/LectorMaestroTest.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java
git commit -m "feat: el maestro guarda su Product Name (0.9.22, pieza 1)"
```

---

### Task 3: Recargar una vez el maestro sin nombres

**Files:**
- Modify: `S/NsysDAO.java` (nuevo `productosSinNombre`)
- Modify: `S/ImportadorNsys.java` (`actualizarMaestro` y un campo nuevo)
- Test: `T/NsysDAOTest.java`, `T/ImportadorNsysTest.java`

**Interfaces:**
- Consumes: `ProductoMaestro.nombre()`, `LectorMaestro.NOMBRE` (Task 2).
- Produces: `NsysDAO.productosSinNombre()` → `boolean`; `NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE`.

- [ ] **Step 1: Write the failing tests**

`T/NsysDAOTest.java`:

```java
    @Test void productosSinNombreMiraSiNingunoLoTiene() {
        when(jdbc.queryForObject(NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE, Boolean.class)).thenReturn(true);
        assertTrue(dao.productosSinNombre());
        when(jdbc.queryForObject(NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE, Boolean.class)).thenReturn(null);
        assertFalse(dao.productosSinNombre());
        assertEquals("SELECT COUNT(*) > 0 AND COALESCE(SUM(NOMBRE IS NOT NULL), 0) = 0 FROM Nsys_producto",
                NsysDAO.SQL_PRODUCTOS_SIN_NOMBRE);
    }
```

`T/ImportadorNsysTest.java`:

```java
    @Test void sinNombresEnElMaestroGuardadoSeRecargaUnaVezAunqueNoHayaCambiado() throws Exception {
        when(dao.productosSinNombre()).thenReturn(true);
        List<List<Object>> hoja = new ArrayList<>();
        List<Object> cabecera = new ArrayList<>(LectorMaestro.COLUMNAS);
        cabecera.add(LectorMaestro.NOMBRE);
        hoja.add(cabecera);
        hoja.add(List.of("Apple iPhone 13 128GB Blue A", "iPhone", "13", "128GB", "Azul", "A", "i13128AzulA",
                "i13128AzulA", "i13128Azul", "Apple iPhone 13 128GB Blue"));
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, hoja);
        when(drive.descargar("maestro-1")).thenReturn(XlsxDePrueba.libro(hojas));
        when(drive.listarItems("carpeta-1")).thenReturn(List.of());

        importador.pasada();
        importador.pasada();

        verify(drive, times(1)).descargar("maestro-1");
        verify(dao).reemplazarProductos(List.of(new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13",
                "13", "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false, "Apple iPhone 13 128GB Blue")));
        verify(dao, times(1)).productosSinNombre();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=NsysDAOTest+ImportadorNsysTest`
Expected: FAIL de compilación (no existe `productosSinNombre`).

- [ ] **Step 3: Implement**

`S/NsysDAO.java`, junto a las demás constantes SQL:

```java
    /** true si hay productos y ninguno tiene nombre: el maestro se cargó antes de la columna NOMBRE (0.9.22). */
    static final String SQL_PRODUCTOS_SIN_NOMBRE =
            "SELECT COUNT(*) > 0 AND COALESCE(SUM(NOMBRE IS NOT NULL), 0) = 0 FROM Nsys_producto";
```

y, en la sección «foto, maestro y cajas», después de `cargarProductos`:

```java
    /** El maestro guardado no tiene ningún nombre de producto (se cargó antes de la 0.9.22). */
    public boolean productosSinNombre() {
        return Boolean.TRUE.equals(jdbc.queryForObject(SQL_PRODUCTOS_SIN_NOMBRE, Boolean.class));
    }
```

`S/ImportadorNsys.java`:
- campo nuevo, con los demás campos de instancia:

```java
    /** Si ya se ha mirado en este arranque si al maestro guardado le faltan los nombres (0.9.22). */
    private boolean nombresMirados;
```

- en `actualizarMaestro`, la comprobación de «sin cambios» pasa a ser:

```java
            Optional<Importacion> ultimo = dao.ultimaImportacion(MAESTRO, Set.of(OK, RECHAZADO));
            boolean sinNombres = faltanNombres();
            if (ultimo.isPresent() && !modificado.isAfter(ultimo.get().fechaExportacion()) && !sinNombres) return false;
```

- método nuevo, debajo de `actualizarMaestro`:

```java
    /** Al maestro guardado le faltan los nombres de producto (0.9.22: la columna es nueva): se recarga una vez aunque no haya
     *  cambiado en Drive. Se mira una sola vez por arranque, para no descargarlo en cada pasada si el Excel no trae la columna. */
    private boolean faltanNombres() {
        if (nombresMirados) return false;
        nombresMirados = true;
        try {
            return dao.productosSinNombre();
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudo mirar si al maestro le faltan los nombres: {}", e.toString());
            return false;
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=NsysDAOTest+ImportadorNsysTest`
Expected: PASS (las demás de `ImportadorNsysTest` siguen igual: `productosSinNombre()` del mock da false).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java
git commit -m "feat: el maestro sin nombres se recarga una vez al arrancar (0.9.22, pieza 1)"
```

---

### Task 4: El coste de cada móvil («Total cost»)

**Files:**
- Modify: `S/FilaItems.java`
- Modify: `S/LectorItems.java`
- Modify: `S/NsysDAO.java` (`cargarCostes`, `guardarCostes`, record `Coste`)
- Modify: `S/ImportadorNsys.java` (`procesar` y método nuevo `guardarCostes`)
- Test: `T/LectorItemsTest.java`, `T/XlsxDePrueba.java` (solo el comentario de `fila`), `T/NsysDAOTest.java`, `T/ImportadorNsysTest.java`

**Interfaces:**
- Produces: `FilaItems.coste()` (`BigDecimal`, dos decimales, null si no hay); `LectorItems.coste(String)`;
  `NsysDAO.guardarCostes(List<FilaItems>)` → `int` (filas actualizadas); `NsysDAO.SQL_COSTES`, `NsysDAO.SQL_GUARDAR_COSTE`.
  La pieza 2 leerá `Nsys_movil.COSTE` para la plantilla.

- [ ] **Step 1: Write the failing tests**

`T/LectorItemsTest.java`: añade `import java.math.BigDecimal;` y cambia la primera prueba (el coste 123.45 de `XlsxDePrueba.fila`
ahora se lee):

```java
    @Test void leeLasFilasConSusFechasAmericanasYSuCoste() throws Exception {
        byte[] xlsx = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null),
                XlsxDePrueba.fila("350000000000002", "Apple iPhone 13 128GB Blue", "A-100", "Released", "BT-9001", "Pedidos realizados", "9/2/2026", "10/15/2026")));
        assertEquals(List.of(
                new FilaItems("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                        "Proveedor A", LocalDate.of(2026, 9, 2), null, new BigDecimal("123.45")),
                new FilaItems("350000000000002", "BT-9001", "Apple iPhone 13 128GB Blue", "A-100", "Released", "Pedidos realizados",
                        "Proveedor A", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 15), new BigDecimal("123.45"))),
                leer(xlsx));
    }

    @Test void elCosteVaADosDecimalesConPuntoOComaYSinNumeroEsNull() {
        assertEquals(new BigDecimal("535.41"), LectorItems.coste("535.41"));
        assertEquals(new BigDecimal("535.41"), LectorItems.coste(" 535,41 "));
        assertEquals(new BigDecimal("669.00"), LectorItems.coste("669"));
        assertEquals(new BigDecimal("386.80"), LectorItems.coste("386.8"));
        assertNull(LectorItems.coste(null));
        assertNull(LectorItems.coste(""));
        assertNull(LectorItems.coste("n/a"));
    }
```

`T/XlsxDePrueba.java`: el comentario de `fila` pasa a «Una fila de Items en el orden de LectorItems.CABECERAS, con un coste de
123,45 (se lee desde la 0.9.22).»

`T/NsysDAOTest.java`: añade `import java.math.BigDecimal;` y:

```java
    private static FilaItems conCoste(String imei, String coste) {
        return new FilaItems(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 9, 2), null, coste == null ? null : new BigDecimal(coste));
    }

    @SuppressWarnings("unchecked")
    @Test void guardarCostesSoloActualizaLosQueCambian() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES), any(RowMapper.class))).thenReturn(List.of(
                new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("535.41")),
                new NsysDAO.Coste(new ClaveMovil("350000000000002", "BT-9001"), new BigDecimal("200.00"))));
        int n = dao.guardarCostes(List.of(conCoste("350000000000001", "535.41"), conCoste("350000000000002", "210.50"),
                conCoste("350000000000003", "99.90"), conCoste("350000000000004", null)));
        assertEquals(2, n);
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(NsysDAO.SQL_GUARDAR_COSTE), lote.capture());
        assertEquals(2, lote.getValue().size());
        assertArrayEquals(new Object[]{new BigDecimal("210.50"), "350000000000002", "BT-9001"}, lote.getValue().get(0));
        assertArrayEquals(new Object[]{new BigDecimal("99.90"), "350000000000003", "BT-9001"}, lote.getValue().get(1));
    }

    @SuppressWarnings("unchecked")
    @Test void sinCostesNuevosNoSeEscribeNada() {
        when(jdbc.query(eq(NsysDAO.SQL_COSTES), any(RowMapper.class))).thenReturn(List.of(
                new NsysDAO.Coste(new ClaveMovil("350000000000001", "BT-9001"), new BigDecimal("535.41"))));
        assertEquals(0, dao.guardarCostes(List.of(conCoste("350000000000001", "535.410"))));
        verify(jdbc, never()).batchUpdate(eq(NsysDAO.SQL_GUARDAR_COSTE), any(List.class));
        assertEquals("UPDATE Nsys_movil SET COSTE = ? WHERE IMEI_SN = ? AND BATCH = ?", NsysDAO.SQL_GUARDAR_COSTE);
        assertEquals("SELECT IMEI_SN, BATCH, COSTE FROM Nsys_movil WHERE COSTE IS NOT NULL", NsysDAO.SQL_COSTES);
    }
```

`T/ImportadorNsysTest.java`: añade `import java.math.BigDecimal;` y:

```java
    @SuppressWarnings("unchecked")
    @Test void trasGuardarLaFotoSeGuardanLosCostesDelFichero() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));

        importador.pasada();

        InOrder orden = inOrder(dao);
        orden.verify(dao).aplicar(any(), eq(1));
        ArgumentCaptor<List<FilaItems>> filas = ArgumentCaptor.forClass(List.class);
        orden.verify(dao).guardarCostes(filas.capture());
        assertEquals(2, filas.getValue().size());
        assertEquals(new BigDecimal("123.45"), filas.getValue().get(0).coste());
        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), isNull(), eq(AHORA));
    }

    @Test void siFallanLosCostesLaImportacionSigueOk() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        when(dao.guardarCostes(any())).thenThrow(new UncategorizedSQLException("costes", "UPDATE Nsys_movil",
                new java.sql.SQLException("fallo")));

        importador.pasada();

        verify(dao).terminarImportacion(eq(1), eq("OK"), eq(2), anyInt(), any(), eq(AHORA));
        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=LectorItemsTest+NsysDAOTest+ImportadorNsysTest`
Expected: FAIL de compilación (`FilaItems` sin `coste`, sin `LectorItems.coste`, sin `guardarCostes`).

- [ ] **Step 3: Implement**

`S/FilaItems.java` entero:

```java
package com.reparaciones.servidor.nsys;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del fichero Items de NSYS, solo con las columnas que usa el ERP. Desde la 0.9.22 lleva también el coste («Total
 *  cost», a dos decimales; null si no viene), que se guarda aparte (NsysDAO#guardarCostes) solo para la plantilla de NSYS. Sin
 *  precios de venta. */
public record FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location,
                        String supplier, LocalDate receivingDate, LocalDate soldDate, BigDecimal coste) {

    /** Sin coste: las filas rehechas desde el espejo (MovilNsys#comoFila) y las pruebas que no lo necesitan. */
    public FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location,
                     String supplier, LocalDate receivingDate, LocalDate soldDate) {
        this(imeiSn, batch, goods, grade, status, location, supplier, receivingDate, soldDate, null);
    }

    public ClaveMovil clave() {
        return new ClaveMovil(imeiSn, batch);
    }
}
```

`S/LectorItems.java`:
- imports: `java.math.BigDecimal` y `java.math.RoundingMode`.
- el comentario de la clase: «… valida las 19 columnas por su nombre y se queda con las que usa el ERP. Desde la 0.9.22 lee
  también el coste («Total cost»); los precios de venta no se leen.»
- método nuevo, debajo de `fecha`:

```java
    /** El coste de NSYS («Total cost»): un número con punto o coma decimal, a dos decimales; null si no viene o no es un número. */
    static BigDecimal coste(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return new BigDecimal(texto.strip().replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException noEsUnNumero) {
            return null;
        }
    }
```

- en `Recolector.fila`, el `new FilaItems(…)` añade el coste al final:

```java
            filas.add(new FilaItems(imei, lote == null ? "" : lote, valor(celdas, "Goods", AnchosNsys.GOODS),
                    valor(celdas, "Grade", AnchosNsys.GRADE), valor(celdas, "Status", AnchosNsys.STATUS),
                    valor(celdas, "Location", AnchosNsys.LOCATION), valor(celdas, "Supplier", AnchosNsys.SUPPLIER),
                    fecha(valor(celdas, "Receiving date")), fecha(valor(celdas, "Sold date")),
                    coste(valor(celdas, "Total cost"))));
```

`S/NsysDAO.java`:
- import `java.math.BigDecimal`.
- constantes:

```java
    /** El coste guardado de cada lote (0.9.22): para escribir solo los que cambian. */
    static final String SQL_COSTES = "SELECT IMEI_SN, BATCH, COSTE FROM Nsys_movil WHERE COSTE IS NOT NULL";
    static final String SQL_GUARDAR_COSTE = "UPDATE Nsys_movil SET COSTE = ? WHERE IMEI_SN = ? AND BATCH = ?";
```

- record y métodos nuevos, después de `aplicar`:

```java
    /** Una fila de {@link #SQL_COSTES}. */
    record Coste(ClaveMovil clave, BigDecimal coste) {}

    /** El coste de cada lote del espejo que lo tiene (0.9.22). */
    Map<ClaveMovil, BigDecimal> cargarCostes() {
        Map<ClaveMovil, BigDecimal> mapa = new HashMap<>();
        for (Coste c : jdbc.query(SQL_COSTES, (rs, n) -> new Coste(new ClaveMovil(rs.getString("IMEI_SN"),
                rs.getString("BATCH")), rs.getBigDecimal("COSTE")))) mapa.put(c.clave(), c.coste());
        return mapa;
    }

    /** Guarda el «Total cost» de las filas de un Items (spec 0.9.22 §2.3): solo los que cambian, por lotes de mil; una fila sin
     *  coste no borra el que había. Se llama después de {@link #aplicar}, con los móviles nuevos ya guardados. Devuelve cuántos
     *  ha escrito. */
    @Transactional
    public int guardarCostes(List<FilaItems> filas) {
        Map<ClaveMovil, BigDecimal> antes = cargarCostes();
        Map<ClaveMovil, BigDecimal> nuevos = new LinkedHashMap<>();
        for (FilaItems f : filas) {
            if (f.coste() == null) continue;
            BigDecimal previo = antes.get(f.clave());
            if (previo == null || previo.compareTo(f.coste()) != 0) nuevos.put(f.clave(), f.coste());
        }
        if (nuevos.isEmpty()) return 0;
        List<Object[]> argumentos = nuevos.entrySet().stream()
                .map(e -> new Object[]{e.getValue(), e.getKey().imeiSn(), e.getKey().batch()}).toList();
        for (List<Object[]> lote : trozos(argumentos)) jdbc.batchUpdate(SQL_GUARDAR_COSTE, lote);
        return nuevos.size();
    }
```

`S/ImportadorNsys.java`, en `procesar`, justo después de `dao.aplicar(r, id);`:

```java
            guardarCostes(filas);
```

y el método, debajo de `rellenarModelos`:

```java
    /** Tras guardar la foto, el coste de cada lote (spec 0.9.22 §2.3). Si falla, la importación sigue OK y se avisa en el registro
     *  del servidor: con el siguiente fichero se vuelven a mirar todos. */
    private void guardarCostes(List<FilaItems> filas) {
        try {
            dao.guardarCostes(filas);
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron guardar los costes", e);
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=LectorItemsTest+NsysDAOTest+ImportadorNsysTest+ComparadorTest+HistoricoNsysTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/FilaItems.java src/main/java/com/reparaciones/servidor/nsys/LectorItems.java src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java src/test/java/com/reparaciones/servidor/nsys/LectorItemsTest.java src/test/java/com/reparaciones/servidor/nsys/XlsxDePrueba.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java
git commit -m "feat: el coste de NSYS de cada lote se guarda al importar (0.9.22, pieza 1)"
```

---

### Task 5: Cómo escribe NSYS cada color (`GrafiasNsys`)

**Files:**
- Create: `S/GrafiasNsys.java`
- Modify: `S/NsysDAO.java` (`goodsContados`, `SQL_GOODS_CONTADOS`)
- Test: `T/GrafiasNsysTest.java` (nuevo), `T/NsysDAOTest.java`

**Interfaces:**
- Produces:
  - `record GrafiasNsys.GoodsContado(String modeloMaestro, String goods, int n)`;
  - `static GrafiasNsys GrafiasNsys.de(List<GoodsContado>)`;
  - `String color(String modeloMaestro, String nombre)` → la grafía de NSYS o null;
  - `static String colorDelGoods(String goods)` → lo que va detrás de la capacidad, sin «eSIM»; null si no hay;
  - `static String cambiarColor(String goods, String grafia)` → el Goods con el color cambiado; null sin capacidad;
  - `List<GrafiasNsys.GoodsContado> NsysDAO.goodsContados()`.

- [ ] **Step 1: Write the failing tests**

`T/GrafiasNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cómo escribe NSYS el nombre de cada color (spec 0.9.22 §2.1): datos inventados con las grafías raras que tiene NSYS. */
class GrafiasNsysTest {

    @Test void elColorEsLoQueVaDetrasDeLaCapacidadSinElEsim() {
        assertEquals("Midnight", GrafiasNsys.colorDelGoods("Apple iPhone 14 eSIM 256GB Midnight"));
        assertEquals("Cosmic Orange", GrafiasNsys.colorDelGoods("Apple iPhone 17 Pro Max eSIM 1TB Cosmic Orange"));
        assertEquals("Blue", GrafiasNsys.colorDelGoods("Apple iPhone 13 128GB Blue eSIM"));
        assertEquals("Blue", GrafiasNsys.colorDelGoods("Apple iPhone 13 128 GB eSIM Blue"));
        assertNull(GrafiasNsys.colorDelGoods("Apple iPhone 13 Blue"), "sin capacidad no se sabe dónde empieza");
        assertNull(GrafiasNsys.colorDelGoods("Apple iPhone 13 128GB"));
        assertNull(GrafiasNsys.colorDelGoods("Apple iPhone 13 128GB eSIM"));
        assertNull(GrafiasNsys.colorDelGoods(null));
    }

    @Test void cambiarElColorConservaElModeloLaCapacidadYElEsimDelMovil() {
        assertEquals("Apple iPhone 14 eSIM 256GB Yellow", GrafiasNsys.cambiarColor("Apple iPhone 14 eSIM 256GB Midnight", "Yellow"));
        assertEquals("Apple iphone 15 pro 128GB Blue Titanium",
                GrafiasNsys.cambiarColor("Apple iphone 15 pro 128GB black titanium", "Blue Titanium"), "el modelo, como lo tiene el móvil");
        assertEquals("Apple iPhone 13 128GB Pink eSIM", GrafiasNsys.cambiarColor("Apple iPhone 13 128GB Blue eSIM", "Pink"));
        assertEquals("Apple iPhone 13 128GB eSIM Pink", GrafiasNsys.cambiarColor("Apple iPhone 13 128GB eSIM Blue", "Pink"));
        assertNull(GrafiasNsys.cambiarColor("Apple iPhone 13 Blue", "Pink"));
        assertNull(GrafiasNsys.cambiarColor(null, "Pink"));
        assertNull(GrafiasNsys.cambiarColor("Apple iPhone 13 128GB Blue", null));
    }

    @Test void ganaLaGrafiaDelMismoModeloYDentroLaMasUsada() {
        GrafiasNsys g = GrafiasNsys.de(List.of(
                new GrafiasNsys.GoodsContado("17 PRO MAX eSIM", "Apple iPhone 17 Pro Max eSIM 256GB Deep Blue", 9),
                new GrafiasNsys.GoodsContado("17 PRO MAX eSIM", "Apple iPhone 17 Pro Max eSIM 512GB Deep blue", 2),
                new GrafiasNsys.GoodsContado("17 PRO", "Apple iPhone 17 Pro 256GB deep blue", 50),
                new GrafiasNsys.GoodsContado(null, "Apple iPhone 15 128GB Blue", 3)));
        assertEquals("Deep Blue", g.color("17 PRO MAX eSIM", "Deep blue"), "en su modelo, la más usada");
        assertEquals("deep blue", g.color("17 AIR", "DEEP BLUE"), "sin móviles en su modelo, la más usada de todos");
        assertEquals("Blue", g.color(null, "blue"));
        assertNull(g.color("15", "Ultramarine"), "NSYS nunca lo ha escrito");
        assertNull(g.color("15", null));
        assertNull(g.color("15", " "));
    }

    @Test void aIgualDeMovilesGanaLaPrimeraPorOrdenAlfabetico() {
        GrafiasNsys g = GrafiasNsys.de(List.of(new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 128GB blue", 2),
                new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Blue", 2)));
        assertEquals("Blue", g.color("13", "BLUE"));
    }
}
```

`T/NsysDAOTest.java`:

```java
    @SuppressWarnings("unchecked")
    @Test void losGoodsContadosSeLeenDeUnaVez() {
        dao.goodsContados();
        verify(jdbc).query(eq(NsysDAO.SQL_GOODS_CONTADOS), any(RowMapper.class));
        assertEquals("SELECT MODELO_MAESTRO, GOODS, COUNT(*) AS N FROM Nsys_movil WHERE GOODS IS NOT NULL"
                + " GROUP BY MODELO_MAESTRO, GOODS", NsysDAO.SQL_GOODS_CONTADOS);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=GrafiasNsysTest+NsysDAOTest`
Expected: FAIL de compilación (no existe `GrafiasNsys`).

- [ ] **Step 3: Implement**

`S/GrafiasNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cómo escribe NSYS el nombre de cada color (spec 0.9.22 §2.1, pasos 2 y 3): NSYS crea un producto nuevo si cambia una
 *  mayúscula, así que el color de un producto a poner se escribe como ya lo escribe NSYS. Se arma con los Goods del espejo
 *  (NsysDAO#goodsContados): el color es lo que va detrás de la capacidad, sin «eSIM». Para un nombre en inglés (el del maestro,
 *  comparado sin mayúsculas ni espacios), vale la grafía más usada en el mismo modelo del maestro y, si no hay, la más usada de
 *  todos; a igual número, la primera por orden alfabético. Es puro. */
public final class GrafiasNsys {

    /** Un Goods del espejo, el modelo del maestro de sus móviles (null si no lo tienen) y cuántos móviles lo llevan. */
    public record GoodsContado(String modeloMaestro, String goods, int n) {}

    static final Pattern CAPACIDAD = Pattern.compile("\\b\\d+\\s?(?:GB|TB)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESIM_DELANTE = Pattern.compile("^(?i)(esim)\\s+");
    private static final Pattern ESIM_DETRAS = Pattern.compile("\\s+(?i)(esim)$");

    /** Modelo del maestro → nombre normalizado → grafía → móviles. */
    private final Map<String, Map<String, Map<String, Integer>>> porModelo;
    /** Nombre normalizado → grafía → móviles, de todos los modelos. */
    private final Map<String, Map<String, Integer>> todos;

    private GrafiasNsys(Map<String, Map<String, Map<String, Integer>>> porModelo, Map<String, Map<String, Integer>> todos) {
        this.porModelo = porModelo;
        this.todos = todos;
    }

    public static GrafiasNsys de(List<GoodsContado> filas) {
        Map<String, Map<String, Map<String, Integer>>> porModelo = new HashMap<>();
        Map<String, Map<String, Integer>> todos = new HashMap<>();
        for (GoodsContado f : filas) {
            String color = colorDelGoods(f.goods());
            if (color == null) continue;
            String clave = ColorChasis.normalizar(color);
            todos.computeIfAbsent(clave, k -> new HashMap<>()).merge(color, f.n(), Integer::sum);
            if (f.modeloMaestro() != null)
                porModelo.computeIfAbsent(f.modeloMaestro().strip(), k -> new HashMap<>())
                        .computeIfAbsent(clave, k -> new HashMap<>()).merge(color, f.n(), Integer::sum);
        }
        return new GrafiasNsys(porModelo, todos);
    }

    /** La grafía de NSYS del color {@code nombre} (en inglés): la más usada en el modelo {@code modeloMaestro} y, si no hay, la
     *  más usada de todos; null si NSYS nunca lo ha escrito. */
    public String color(String modeloMaestro, String nombre) {
        String clave = ColorChasis.normalizar(nombre);
        if (clave.isEmpty()) return null;
        Map<String, Map<String, Integer>> delModelo = modeloMaestro == null ? Map.of()
                : porModelo.getOrDefault(modeloMaestro.strip(), Map.of());
        String grafia = masUsada(delModelo.get(clave));
        return grafia != null ? grafia : masUsada(todos.get(clave));
    }

    private static String masUsada(Map<String, Integer> grafias) {
        if (grafias == null) return null;
        return grafias.entrySet().stream()
                .min(Comparator.comparing((Map.Entry<String, Integer> e) -> -e.getValue()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).orElse(null);
    }

    /** El color de un Goods: lo que va detrás de la capacidad, sin «eSIM» delante ni detrás («Apple iPhone 14 eSIM 256GB
     *  Midnight» → «Midnight»). null si no hay capacidad o no queda nada. */
    static String colorDelGoods(String goods) {
        if (goods == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        if (!c.find()) return null;
        String resto = goods.substring(c.end()).strip();
        resto = ESIM_DETRAS.matcher(ESIM_DELANTE.matcher(resto).replaceFirst("")).replaceFirst("").strip();
        return resto.isEmpty() || resto.equalsIgnoreCase("esim") ? null : resto;
    }

    /** El Goods {@code goods} con el color cambiado por {@code grafia}: el modelo y la capacidad como los tiene el móvil, y su
     *  «eSIM» donde estaba («Apple iPhone 13 128GB Blue eSIM» + «Pink» → «Apple iPhone 13 128GB Pink eSIM»). null sin
     *  capacidad o sin grafía. */
    static String cambiarColor(String goods, String grafia) {
        if (goods == null || grafia == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        if (!c.find()) return null;
        String resto = goods.substring(c.end()).strip();
        Matcher delante = ESIM_DELANTE.matcher(resto);
        Matcher detras = ESIM_DETRAS.matcher(resto);
        return goods.substring(0, c.end()) + " " + (delante.find() ? delante.group(1) + " " : "") + grafia
                + (detras.find() ? " " + detras.group(1) : "");
    }
}
```

`S/NsysDAO.java`:

```java
    /** Los Goods del espejo con el modelo del maestro de sus móviles y cuántos son (0.9.22 §2.1): de aquí sale cómo escribe NSYS
     *  cada color. Una consulta para todos los modelos; solo se pide si un color no tiene móviles en su modelo y capacidad. */
    static final String SQL_GOODS_CONTADOS = "SELECT MODELO_MAESTRO, GOODS, COUNT(*) AS N FROM Nsys_movil WHERE GOODS IS NOT NULL"
            + " GROUP BY MODELO_MAESTRO, GOODS";
```

```java
    public List<GrafiasNsys.GoodsContado> goodsContados() {
        return jdbc.query(SQL_GOODS_CONTADOS, (rs, n) -> new GrafiasNsys.GoodsContado(rs.getString("MODELO_MAESTRO"),
                rs.getString("GOODS"), rs.getInt("N")));
    }
```

(`ColorChasis.normalizar` es `static` y de paquete: minúsculas, sin tildes, solo letras y cifras.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=GrafiasNsysTest+NsysDAOTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/GrafiasNsys.java src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/test/java/com/reparaciones/servidor/nsys/GrafiasNsysTest.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java
git commit -m "feat: como escribe NSYS cada color, a partir de los Goods del espejo (0.9.22, pieza 1)"
```

---

### Task 6: El producto a poner en NSYS con el nombre del maestro

**Files:**
- Modify: `S/Maestro.java` (`nombreColor`)
- Create: `S/ProductoNsys.java`
- Modify: `S/TallerNsys.java` (`productoAPoner`, `porPasar`, `opciones`)
- Test: `T/MaestroTest.java`, `T/ProductoNsysTest.java` (nuevo), `T/TallerDePrueba.java` (ayudante nuevo), `T/TallerNsysTest.java`

**Interfaces:**
- Consumes: `GrafiasNsys` (Task 5), `ProductoMaestro.nombre()` (Task 2), `NsysDAO.goodsContados()` (Task 5).
- Produces: `String Maestro.nombreColor(MovilNsys m, String color)`;
  `static String ProductoNsys.paraColor(MovilNsys m, String color, Map<String,String> goodsMismoModelo, Supplier<Maestro> maestro, Supplier<GrafiasNsys> grafias)`;
  `TallerDePrueba.productoConNombre(String goodsColor, String color, String grado)`.

- [ ] **Step 1: Write the failing tests**

`T/TallerDePrueba.java`, debajo de `producto`:

```java
    /** Como {@link #producto}, con el «Product Name» del maestro (0.9.22): «Apple iPhone 13 128GB <goodsColor>». */
    static ProductoMaestro productoConNombre(String goodsColor, String color, String grado) {
        ProductoMaestro p = producto(goodsColor, color, grado);
        return new ProductoMaestro(p.clave(), p.modelo(), p.modeloMaestro(), p.capacidad(), p.color(), p.calidadDetalle(),
                p.skuWeb(), p.skuAmazon(), p.skuSinGrado(), p.esEsim(), "Apple iPhone 13 128GB " + goodsColor);
    }
```

`T/MaestroTest.java`:

```java
    @Test void elNombreDeUnColorSaleDelProductNameDetrasDeLaCapacidad() {
        Maestro maestro = new Maestro(List.of(productoConNombre("(PRODUCT)RED", "Rojo", "A"),
                productoConNombre("(PRODUCT)RED", "Rojo", "B"), producto("Blue", "Azul", "B")));
        assertEquals("(PRODUCT)RED", maestro.nombreColor(AZUL, "Rojo"));
        assertEquals("(PRODUCT)RED", maestro.nombreColor(AZUL, " Rojo "));
        assertNull(maestro.nombreColor(AZUL, "Azul"), "sin «Product Name»");
        assertNull(maestro.nombreColor(AZUL, "Verde"));
        assertNull(maestro.nombreColor(sinMaestro("350000000000002", "B"), "Rojo"), "sin modelo en el maestro");
    }
```

`T/ProductoNsysTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** El producto que hay que poner en NSYS para un color (spec 0.9.22 §2.1). El móvil de prueba es un iPhone 13 128GB Blue. */
class ProductoNsysTest {

    private static final MovilNsys AZUL = movil("350000000000001", "Azul", "B");
    private static final Supplier<Maestro> MAESTRO = () -> new Maestro(List.of(productoConNombre("Blue", "Azul", "B"),
            productoConNombre("Pink", "Rosa", "B"), producto("Green", "Verde", "B")));
    private static final Supplier<GrafiasNsys> GRAFIAS = () -> GrafiasNsys.de(List.of(
            new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));

    private static <T> Supplier<T> prohibido() {
        return () -> { throw new AssertionError("no hacía falta leerlo"); };
    }

    @Test void conMovilesDeEseColorEnSuModeloYCapacidadValeEseGoods() {
        assertEquals("Apple iPhone 13 128GB Pink", ProductoNsys.paraColor(AZUL, "Rosa",
                Map.of("Rosa", "Apple iPhone 13 128GB Pink"), prohibido(), prohibido()));
    }

    @Test void sinMovilesSeComponeConElNombreDelMaestroEscritoComoNsys() {
        assertEquals("Apple iPhone 13 128GB Pink", ProductoNsys.paraColor(AZUL, "Rosa", Map.of(), MAESTRO, GRAFIAS));
    }

    @Test void sinNombreEnElMaestroONuncaEscritoEnNsysNoHayProducto() {
        assertNull(ProductoNsys.paraColor(AZUL, "Verde", Map.of(), MAESTRO, prohibido()), "el maestro no da el nombre");
        assertNull(ProductoNsys.paraColor(AZUL, "Rosa", Map.of(), MAESTRO, () -> GrafiasNsys.de(List.of())),
                "NSYS nunca ha escrito «Pink»: el paso 4 llega con la pieza 2");
        assertNull(ProductoNsys.paraColor(AZUL, null, Map.of(), prohibido(), prohibido()));
    }

    @Test void unMovilSinCapacidadEnSuGoodsNoSeCompone() {
        MovilNsys raro = new MovilNsys("350000000000003", "BT-9001", "Apple iPhone 13 Blue", "B", "Available", "CAJA LISTOS",
                "Proveedor A", AZUL.receivingDate(), null, null, "13", "13", "128GB", "Azul", "B", false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY, HOY);
        assertNull(ProductoNsys.paraColor(raro, "Rosa", Map.of(), prohibido(), prohibido()));
    }
}
```

`T/TallerNsysTest.java`, en la sección de «Por pasar a NSYS»:

```java
    @Test void sinMovilesDeEseColorElProductoSaleDelNombreDelMaestroEscritoComoNsys() {
        when(nsys.cargarProductos()).thenReturn(List.of(productoConNombre("Blue", "Azul", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rosa", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(nsys.goodsContados()).thenReturn(List.of(new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));
        assertEquals("Apple iPhone 13 128GB Pink", servicio.porPasar().get(0).ponerProducto());
    }

    @Test void sinNombreEnElMaestroNoSeLeenLasGrafias() {
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertNull(servicio.porPasar().get(0).ponerProducto());
        verify(nsys, never()).goodsContados();
    }

    @Test void lasOpcionesUsanTambienElNombreDelMaestro() {
        when(nsys.cargarProductos()).thenReturn(List.of(productoConNombre("Blue", "Azul", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        when(nsys.goodsContados()).thenReturn(List.of(new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));
        assertEquals(List.of(new OpcionColor("Azul", "Apple iPhone 13 128GB Blue"),
                new OpcionColor("Rosa", "Apple iPhone 13 128GB Pink")), servicio.opciones(IMEI).colores());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=MaestroTest+ProductoNsysTest+TallerNsysTest`
Expected: FAIL de compilación (no existen `nombreColor` ni `ProductoNsys`).

- [ ] **Step 3: Implement**

`S/Maestro.java`: imports `java.util.stream.Collectors`; método nuevo después de `colores`:

```java
    /** El nombre en inglés de un color del maestro para el modelo del móvil (spec 0.9.22 §2.1): lo que va detrás de la
     *  capacidad en el «Product Name» de sus filas (el más repetido; a igual número, el primero). null si ninguna fila de ese
     *  color trae el nombre. */
    public String nombreColor(MovilNsys m, String color) {
        Map<String, Long> nombres = delModelo(m).filter(p -> Normalizacion.mismoTexto(p.color(), color))
                .map(p -> GrafiasNsys.colorDelGoods(p.nombre())).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()));
        return nombres.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
```

`S/ProductoNsys.java`:

```java
package com.reparaciones.servidor.nsys;

import java.util.Map;
import java.util.function.Supplier;

/** El producto («Goods») que hay que poner en NSYS cuando el taller cambia el color de un móvil (spec 0.9.22 §2.1). Es puro. */
final class ProductoNsys {

    private ProductoNsys() {}

    /** Para el color {@code color} del maestro: 1) el Goods más usado de ese color entre los móviles del mismo modelo, capacidad
     *  y eSIM ({@code goodsMismoModelo}, de NsysDAO#goodsMasUsados); 2-3) si no hay, el Goods del propio móvil con el color
     *  cambiado por su nombre en inglés del maestro (Maestro#nombreColor) escrito como lo escribe NSYS en otra capacidad del
     *  mismo modelo o en otro modelo (GrafiasNsys#color). null si el maestro no da el nombre o NSYS nunca lo ha escrito: la web
     *  dice «búscalo a mano» (el nombre del maestro tal cual llega con la pieza 2). El maestro y las grafías solo se leen si
     *  hacen falta. */
    static String paraColor(MovilNsys m, String color, Map<String, String> goodsMismoModelo, Supplier<Maestro> maestro,
                            Supplier<GrafiasNsys> grafias) {
        if (color == null) return null;
        String exacto = goodsMismoModelo.get(color);
        if (exacto != null) return exacto;
        if (GrafiasNsys.colorDelGoods(m.goods()) == null) return null;
        String nombre = maestro.get().nombreColor(m, color);
        if (nombre == null) return null;
        String grafia = grafias.get().color(m.modeloMaestro(), nombre);
        return grafia == null ? null : GrafiasNsys.cambiarColor(m.goods(), grafia);
    }
}
```

`S/TallerNsys.java`:

```java
    /** El producto («Goods») que hay que poner en NSYS (spec 0.9.21 §2.4 y 0.9.22 §2.1): si el color no cambia, el de ahora; si
     *  cambia, el que da ProductoNsys (el más usado de ese color en el mismo modelo, capacidad y eSIM o, si no hay, el del móvil
     *  con el color escrito como NSYS); null si no se sabe (la web dice «búscalo a mano»). */
    public String productoAPoner(MovilNsys m, LoQueVale.Valor v) {
        if (v.colorNsys() == null) return m.goods();
        return ProductoNsys.paraColor(m, v.color(), nsysDao.goodsMasUsados(m),
                perezoso(() -> new Maestro(nsysDao.cargarProductos())), perezoso(() -> GrafiasNsys.de(nsysDao.goodsContados())));
    }
```

En `porPasar`, debajo de `Supplier<Maestro> maestro = …;`:

```java
        Supplier<GrafiasNsys> grafias = perezoso(() -> GrafiasNsys.de(nsysDao.goodsContados()));
```

y la línea del producto:

```java
            String producto = v.colorNsys() == null ? m.goods()
                    : ProductoNsys.paraColor(m, v.color(), goods.computeIfAbsent(Arrays.asList(m.modelo(), m.modeloMaestro(),
                            m.capacidad(), m.esEsim()), k -> nsysDao.goodsMasUsados(m)), maestro, grafias);
```

En `opciones`:

```java
        Map<String, String> goods = colores.isEmpty() ? Map.of() : nsysDao.goodsMasUsados(m);
        Supplier<GrafiasNsys> grafias = perezoso(() -> GrafiasNsys.de(nsysDao.goodsContados()));
        List<OpcionColor> opciones = colores.stream().map(c -> new OpcionColor(c, Normalizacion.mismoTexto(c, m.color())
                ? m.goods() : ProductoNsys.paraColor(m, c, goods, () -> maestro, grafias))).toList();
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=MaestroTest+ProductoNsysTest+TallerNsysTest+StockTelefonosControllerTest`
Expected: PASS (las pruebas de antes siguen: sus productos no tienen nombre, así que nada cambia).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/Maestro.java src/main/java/com/reparaciones/servidor/nsys/ProductoNsys.java src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java src/test/java/com/reparaciones/servidor/nsys/MaestroTest.java src/test/java/com/reparaciones/servidor/nsys/ProductoNsysTest.java src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java
git commit -m "feat: el producto a poner en NSYS con el nombre del maestro escrito como NSYS (0.9.22, pieza 1)"
```

---

### Task 7: El color del chasis con el nombre del maestro

**Files:**
- Modify: `S/ColorChasis.java` (sobrecarga de `de`)
- Modify: `S/TallerReparaciones.java` (`colorDeChasis`)
- Test: `T/ColorChasisTest.java`, `T/TallerReparacionesTest.java`

**Interfaces:**
- Consumes: `Maestro.nombreColor` (Task 6), `TallerDePrueba.productoConNombre` (Task 6).
- Produces: `static ColorChasis ColorChasis.de(List<String> colores, Map<String,String> goodsPorColor, Map<String,String> nombresMaestro, String capacidad, Map<String,String> alias)`
  (la de 4 argumentos sigue, con `nombresMaestro` vacío).

- [ ] **Step 1: Write the failing tests**

`T/ColorChasisTest.java`:

```java
    @Test void unColorSinMovilesEnNsysEncajaConElNombreDelMaestro() {
        ColorChasis c = ColorChasis.de(List.of("Azul", "Rosa"), Map.of("Azul", "Apple iPhone 13 128GB Blue"),
                Map.of("Rosa", "Pink"), "128GB", Map.of());
        assertEquals(Optional.of("Rosa"), c.deducir("chai13pink"));
        assertEquals(Optional.of("Azul"), c.deducir("chai13blue"));
    }

    @Test void conMovilesEnNsysMandaElGoodsSobreElMaestro() {
        ColorChasis c = ColorChasis.de(List.of("Azul"), Map.of("Azul", "Apple iPhone 13 128GB Blue"), Map.of("Azul", "Navy"),
                "128GB", Map.of());
        assertEquals(Optional.of("Azul"), c.deducir("chai13blue"));
        assertEquals(Optional.empty(), c.deducir("chai13navy"));
    }
```

`T/TallerReparacionesTest.java`:

```java
    @Test void unColorSinMovilesEnNsysSeDeduceConElNombreDelMaestro() {
        when(nsys.cargarProductos()).thenReturn(List.of(producto("Blue", "Azul", "B"), producto("(PRODUCT)RED", "Rojo", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        servicio.aplicar(IMEI, List.of(fila(R1, 132, false)), 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rosa", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 8, "chasis chai13pink nuevo", AHORA);
        verify(taller, never()).avisar(any(), anyInt(), any());
    }
```

(`unColorQueNoSeDeduceSeAvisaYElGradoCambia` sigue igual: los productos de la base no tienen nombre.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -o test -Dtest=ColorChasisTest+TallerReparacionesTest`
Expected: FAIL (no hay sobrecarga de 5 argumentos; el chasis rosa no se deduce).

- [ ] **Step 3: Implement**

`S/ColorChasis.java`: la `de` de siempre delega en la nueva, y la nueva usa el nombre del maestro cuando el color no tiene Goods:

```java
    /** Con los colores del maestro del modelo ({@link Maestro#colores}), el Goods más usado de cada uno
     *  ({@link NsysDAO#goodsMasUsados}), la capacidad del móvil y Color_equivalencia (texto → color oficial). */
    public static ColorChasis de(List<String> colores, Map<String, String> goodsPorColor, String capacidad,
                                 Map<String, String> alias) {
        return de(colores, goodsPorColor, Map.of(), capacidad, alias);
    }

    /** Como la otra, y para los colores sin ningún móvil en NSYS, el nombre en inglés del maestro ({@code nombresMaestro}: color →
     *  nombre, de {@link Maestro#nombreColor}; spec 0.9.22 §2.2). Si el color tiene Goods, manda el Goods. */
    public static ColorChasis de(List<String> colores, Map<String, String> goodsPorColor, Map<String, String> nombresMaestro,
                                 String capacidad, Map<String, String> alias) {
        Map<String, String> goods = new HashMap<>();
        goodsPorColor.forEach((color, g) -> {
            if (color != null) goods.put(color.strip(), g);
        });
        Map<String, String> nombres = new LinkedHashMap<>();
        for (String color : colores) {
            String nombre = nombreDelGoods(goods.get(color.strip()), capacidad);
            if (nombre == null) {
                String delMaestro = normalizar(nombresMaestro.get(color));
                if (!delMaestro.isEmpty()) nombre = delMaestro;
            }
            if (nombre != null) nombres.put(color, nombre);
        }
        Map<String, String> limpios = new HashMap<>();
        alias.forEach((texto, oficial) -> {
            String t = normalizar(texto);
            String o = normalizar(oficial);
            if (!t.isEmpty() && !o.isEmpty()) limpios.put(t, o);
        });
        return new ColorChasis(nombres, limpios);
    }
```

y el comentario de la clase añade al final: «Desde la 0.9.22, un color sin ningún móvil en NSYS usa el nombre en inglés del
«Product Name» del maestro.»

`S/TallerReparaciones.java`, `colorDeChasis`:

```java
    /** SKU → color del maestro, con el maestro (y el nombre en inglés de cada color, 0.9.22), el Goods más usado de cada color y
     *  Color_equivalencia leídos solo si hace falta (una batería no los necesita). */
    private Function<String, Optional<String>> colorDeChasis(MovilNsys m) {
        Supplier<ColorChasis> chasis = TallerNsys.perezoso(() -> {
            Maestro maestro = new Maestro(nsysDao.cargarProductos());
            List<String> colores = maestro.colores(m);
            Map<String, String> nombres = new HashMap<>();
            for (String c : colores) {
                String nombre = maestro.nombreColor(m, c);
                if (nombre != null) nombres.put(c, nombre);
            }
            return ColorChasis.de(colores, nsysDao.goodsMasUsados(m), nombres, m.capacidad(), alias());
        });
        return sku -> chasis.get().deducir(sku);
    }
```

(`HashMap`, `List` y `Map` ya están importados en `TallerReparaciones`; si falta alguno, añádelo.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -o test -Dtest=ColorChasisTest+TallerReparacionesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/ColorChasis.java src/main/java/com/reparaciones/servidor/nsys/TallerReparaciones.java src/test/java/com/reparaciones/servidor/nsys/ColorChasisTest.java src/test/java/com/reparaciones/servidor/nsys/TallerReparacionesTest.java
git commit -m "feat: el color del chasis se deduce tambien con el nombre del maestro (0.9.22, pieza 1)"
```

---

### Task 8: Suite completa y documentación

**Files:**
- Modify: `CHANGELOG.md` de la **web** (worktree `/c/Users/dev/Documents/wt/web-0922-nombres`, rama `dev_v0922_nombres_nsys`)
- Create: `docs/novedades/NOVEDADES-v0.9.22.md` (raíz)

- [ ] **Step 1: La suite entera del servidor**

Run: `mvn -o test` (en el worktree del servidor)
Expected: `BUILD SUCCESS`, 1401 + las nuevas, sin fallos. Si `OpenApiContractTest` falla, es que una ruta cambió de forma
(no debería: esta pieza no cambia la API); arréglalo antes de seguir.

- [ ] **Step 2: CHANGELOG de la web**

Encima de `## [0.9.21.1] …`:

```markdown
## [0.9.22] - (pendiente) — Pieza 1 de «Lo que no cuadra con NSYS»: el ERP lee mejor el maestro y NSYS

- **«Poner en NSYS» y la deducción del color del chasis** saben cómo se llama cada color en NSYS aunque ningún móvil de ese modelo y capacidad lo tenga: el nombre en inglés sale del «Product Name» del maestro (el Excel no se toca) y se escribe con la grafía que ya usa NSYS en otra capacidad u otro modelo. Salen menos «búscalo a mano» y menos «el color del chasis no se puede deducir».
- **Coste de NSYS:** al importar los ficheros de NSYS se guarda el «Total cost» de cada móvil, para la plantilla de la pieza 2. No sale en ninguna pantalla.
- Solo servidor, con la migración `migracion-nombres-y-coste-nsys.sql` (dos columnas nuevas). La web sigue en la 0.9.21.
```

- [ ] **Step 3: Novedades (raíz)**

`docs/novedades/NOVEDADES-v0.9.22.md`:

```markdown
# Novedades de la versión 0.9.22

---

## 📦 Stock de teléfonos (supertécnicos y administrador)

- En «Por pasar a NSYS», el producto que hay que poner en NSYS sale casi siempre, también para colores que ningún móvil de ese
  modelo y capacidad tiene todavía en NSYS: «búscalo a mano» queda para casos muy raros.

## 🔧 Formulario de reparación

- Al guardar una reparación con chasis o tapa trasera, el programa deduce el color en más casos (colores que aún no tiene ningún
  móvil en NSYS). Menos avisos de «el color del chasis no se puede deducir».

---

*Sin cambios en la forma de trabajar. Lo siguiente (pieza 2): las listas de color, grado, móviles que no están en NSYS y revisar
a mano, con las plantillas para subir a NSYS.*
```

- [ ] **Step 4: Commit (web y raíz)**

```bash
git -C /c/Users/dev/Documents/wt/web-0922-nombres add CHANGELOG.md
git -C /c/Users/dev/Documents/wt/web-0922-nombres commit -m "docs: changelog 0.9.22 pieza 1, el ERP lee mejor el maestro y NSYS"
git -C /c/Users/dev/Documents/ProgramaReparaciones add docs/novedades/NOVEDADES-v0.9.22.md
git -C /c/Users/dev/Documents/ProgramaReparaciones commit -m "docs: novedades de la 0.9.22 (pieza 1)"
```

---

## Después del plan (fuera de las tareas)

- Revisión final de la rama, merge `--no-ff` en `main` (servidor y web) y push con el OK del usuario.
- Preprod: migración (vista previa 0 → CREADAS 2), servidor, comprobar en el log que el maestro se recarga una vez con nombres y
  que la siguiente importación guarda costes; medir que `porPasar` y `opciones` siguen por debajo de los 15 s.
- Producción en horario (migración aditiva, servidor con corte ~5 s), con guion en `Apuntes/prod-v0922.md`.
