# Asociación de Amazon: el libro manda también en pedidos con etiqueta — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la pasada del libro de ventas de Amazon ponga el IMEI del libro también en pedidos con etiqueta (generada o impresa), salvo que el móvil no cuadre con el pedido o el libro lo ponga en dos pedidos, y que diga en «para revisar» por qué.

**Architecture:** `AsociacionAmazon` gana un tercer camino para pedidos sin fila pendiente libre pero con filas con etiqueta sin confirmar. Antes de repartir, un componente nuevo `ComprobadorLibro` mira, con «lo que vale» (NSYS + taller) y `AvisoImei`, qué IMEIs del libro no cuadran con el SKU de su pedido (nivel `MAL` = cuenta en «no cuadran»). La pasada detecta antes los IMEIs que el libro pone en dos pedidos. `Pedido_asociacion_revisar` gana `DETALLE` (qué es el móvil) y la web enseña los motivos nuevos.

**Tech Stack:** Java 17 + Spring Boot 3.3 (JUnit 5 + Mockito, JdbcTemplate simulado en `AsociacionAmazonTest`); React + TypeScript (Vitest); MariaDB.

**Spec:** `docs/superpowers/specs/2026-11-24-asociacion-libro-manda-design.md`

## Global Constraints

- **Depende de la versión «textos y colores de la celda del IMEI»** (`docs/superpowers/specs/2026-11-24-pedidos-celda-imei-y-cajas-design.md`, otra sesión). Esta versión se empieza **cuando aquella esté en `origin/main`** del servidor: con ella, eSIM / SIM física deja de ser `MAL`. Si no lo está, parar y avisar.
- Versión visible: la siguiente libre tras la de la celda (prevista **0.9.45**); se confirma con el usuario al empezar. La web lleva el número en `package.json`; el servidor no lleva número propio.
- Ramas `dev_asociacion_libro` en servidor y web, worktrees `C:/Users/dev/Documents/wt/servidor-asociacion-libro` y `C:/Users/dev/Documents/wt/web-asociacion-libro`, desde `origin/main` tras `git fetch` (hay otras sesiones trabajando: no tocar sus ramas ni worktrees).
- Commits **sin** `Co-Authored-By` (norma del usuario). **Nada de merge, push, tag ni despliegue sin OK explícito del usuario.** Claude no hace SSH: prepara guiones y el usuario los ejecuta.
- Maven desde Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"` (comprobar las rutas con `ls /c/Users/dev/tools`).
- Motivos nuevos de «para revisar» (texto exacto en la base): `REPETIDO_EN_LIBRO`, `NO_EN_NSYS`, `OTRO_MODELO`. Detalle de `OTRO_MODELO`: `el pedido es <SKU pedido> y el móvil es <SKU móvil>` (`—` si no hay SKU del móvil).
- Apunte de un IMEI cambiado por el libro en una fila **con etiqueta**: `Pedido_fila_cambio` con `TIPO = 'IMEI'`, `DETALLE = 'LIBRO'`, sin usuario.
- Datos de prueba inventados (repos públicos): pedidos `402-0000000-000000x`, IMEI `3500000000000xx`.
- Migración **aditiva** (columna nueva con `IF NOT EXISTS`): despliegue en horario; primero la migración, luego servidor, luego web.

## Mapa de ficheros

Servidor (`gestion-reparaciones-servidor/`, en el worktree):
- Create `src/main/java/com/reparaciones/servidor/pedidos/ComprobadorLibro.java` — qué IMEIs del libro no cuadran con su pedido.
- Create `src/test/java/com/reparaciones/servidor/pedidos/ComprobadorLibroTest.java`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java` — `DETALLE`, repetidos, SKU en `Hueco`, camino con etiqueta.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/EstadoAsociaciones.java` — `Revisar.detalle`.
- Modify `src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java`.
- Create `sql/migracion-asociacion-detalle.sql`; Modify `sql/crear_bd.sql` (DDL de `Pedido_asociacion_revisar`).
- Modify `docs/api_contract.md` (motivos y `detalle`).

Web (`gestion-reparaciones-web/`, en el worktree):
- Modify `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados).
- Modify `src/modules/telefonos/pedidos/filas.ts` (`textoRevisar`), `filas.test.ts`, `PildoraAsociacion.tsx`.
- Modify `package.json`, `package-lock.json` (versión), `CHANGELOG.md`.

Raíz (`ProgramaReparaciones/`):
- Modify `docs/modulos/pedidos.md` (sección de la asociación de Amazon).

---

### Task 0: Comprobar la dependencia, ramas y worktrees

**Files:** ninguno.

- [ ] **Step 1: Traer origin y comprobar que la versión de la celda está**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q && git log --oneline -5 origin/main
git show origin/main:src/main/java/com/reparaciones/servidor/pedidos/AvisoImei.java | grep -n "ESIM\|SIM_FISICA\|DetalleAviso"
```
Expected: `AvisoImei` en `origin/main` ya conoce la eSIM (sale `ESIM`/`SIM_FISICA` o `DetalleAviso`). Si no sale nada: **parar** y decir al usuario que falta la versión de la celda.

- [ ] **Step 2: Comprobar que eSIM no es MAL**

```bash
git show origin/main:src/test/java/com/reparaciones/servidor/pedidos/AvisoImeiTest.java | grep -n -i "esim" | head
```
Expected: un test en el que un móvil eSIM en un pedido sin eSIM **no** da `MAL`. Apuntar la firma actual de `AvisoImei.de(...)` (puede haber cambiado; las Tasks 1 y 4 la usan).

- [ ] **Step 3: Worktrees**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git worktree add -b dev_asociacion_libro /c/Users/dev/Documents/wt/servidor-asociacion-libro origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q && git worktree add -b dev_asociacion_libro /c/Users/dev/Documents/wt/web-asociacion-libro origin/main
cd /c/Users/dev/Documents/wt/web-asociacion-libro && npm ci
```
Expected: dos worktrees nuevos; `npm ci` sin errores.

- [ ] **Step 4: Suite de partida en verde**

```bash
cd /c/Users/dev/Documents/wt/servidor-asociacion-libro && mvn -q test
```
Expected: BUILD SUCCESS. Si algo falla ya en `origin/main`, avisar antes de seguir.

---

### Task 1: `ComprobadorLibro` — qué IMEIs del libro no cuadran con su pedido

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/ComprobadorLibro.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/ComprobadorLibroTest.java`

**Interfaces:**
- Consumes: `NsysDAO.porImeis(Collection<String>)`, `NsysDAO.trabajosAbiertos(Collection<String>)`, `NsysDAO.productosPorSkuDeCanal(Collection<String>)`, `NsysDAO.claveCruce(String)`, `TallerNsys.valedor(Collection<String>)`, `AceptaSku`, `AvisoImei.de(...)` (firma de `origin/main`, ver Task 0 Step 2).
- Produces: `ComprobadorLibro.rechazos(Canal canal, Map<String, String> skuPorImei) → Map<String, ComprobadorLibro.Rechazo>` (solo los que no valen; clave = IMEI tal cual llega); `record Rechazo(String motivo, String detalle)`; constantes `NO_EN_NSYS = "NO_EN_NSYS"`, `OTRO_MODELO = "OTRO_MODELO"`.

- [ ] **Step 1: Write the failing test**

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Qué IMEIs del libro no cuadran con el SKU de su pedido (spec «el libro manda» §2.3). Datos inventados. */
class ComprobadorLibroTest {

    private static final String X = "350000000000001";
    private static final String Y = "350000000000002";
    private final NsysDAO nsysDao = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final ComprobadorLibro comprobador = new ComprobadorLibro(nsysDao, taller);

    private static MovilNsys movil(String imei, String modeloMaestro) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, "128GB", "Azul", "A", false,
                "Listos", "Pedidos", EstadoMovil.STOCK, null, null);
    }

    @BeforeEach void preparar() {
        when(nsysDao.productosPorSkuDeCanal(anyCollection())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsysDao.trabajosAbiertos(anyCollection())).thenReturn(Map.of());
        when(taller.valedor(anyCollection())).thenReturn(m -> new LoQueVale.Valor("Azul", "A", m.sku(), null, null, null, false));
    }

    @Test void sinNadaQueComprobarNoLeeNada() {
        assertTrue(comprobador.rechazos(Canal.AMAZON, Map.of()).isEmpty());
        verifyNoInteractions(nsysDao, taller);
    }

    @Test void unMovilQueCuadraNoSeRechaza() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13")));
        assertTrue(comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")).isEmpty());
    }

    @Test void unImeiQueNoEstaEnNsysEsNoEnNsysSinDetalle() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of());
        assertEquals(Map.of(X, new ComprobadorLibro.Rechazo("NO_EN_NSYS", null)),
                comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")));
    }

    @Test void otroModeloEsOtroModeloYDiceQueEsElMovil() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13 mini")));
        assertEquals(Map.of(X, new ComprobadorLibro.Rechazo("OTRO_MODELO", "el pedido es i13128AzulA y el móvil es i13128AzulA")),
                comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")));
    }

    @Test void soloDevuelveLosQueNoValenYLeeUnaVez() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13"), movil(Y, "13 mini")));
        Map<String, String> pedidos = new LinkedHashMap<>();
        pedidos.put(X, "i13128AzulA");
        pedidos.put(Y, "i13128AzulA");
        assertEquals(List.of(Y), List.copyOf(comprobador.rechazos(Canal.AMAZON, pedidos).keySet()));
        verify(nsysDao, times(1)).porImeis(anyCollection());
        verify(taller, times(1)).valedor(anyCollection());
    }
}
```

Si `AvisoImei` en `origin/main` da otro motivo para «13 mini» que no sea `MAL`, cambiar el modelo del móvil del test a uno que el `AvisoImeiTest` de `origin/main` dé como `MAL` por modelo, y mantener lo que se comprueba (rechazo `OTRO_MODELO` con detalle).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /c/Users/dev/Documents/wt/servidor-asociacion-libro && mvn -q test -Dtest=ComprobadorLibroTest`
Expected: FAIL de compilación («cannot find symbol: class ComprobadorLibro»).

- [ ] **Step 3: Write minimal implementation**

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Si el móvil que el libro de ventas pone en un pedido con etiqueta puede ser el de ese pedido (spec «el libro manda» §2.3): con
 *  «lo que vale» (NSYS + taller) y la misma comparación que el aviso de la celda del IMEI. No vale lo que en la celda cuenta en
 *  «no cuadran» (nivel MAL); eSIM / SIM física, n/a, Ms, B por A o un técnico no lo paran. Solo lee. */
@Component
public class ComprobadorLibro {

    public static final String NO_EN_NSYS = "NO_EN_NSYS";
    public static final String OTRO_MODELO = "OTRO_MODELO";

    /** Por qué no vale: {@code motivo} NO_EN_NSYS u OTRO_MODELO; {@code detalle}, qué es el móvil (null en NO_EN_NSYS). */
    public record Rechazo(String motivo, String detalle) {}

    private final NsysDAO nsysDao;
    private final TallerNsys taller;

    public ComprobadorLibro(NsysDAO nsysDao, TallerNsys taller) {
        this.nsysDao = nsysDao;
        this.taller = taller;
    }

    /** {@code skuPorImei}: IMEI del libro → SKU del pedido en el que lo pone. Devuelve solo los que no valen, por el mismo IMEI.
     *  Una lectura de cada cosa para todos. */
    public Map<String, Rechazo> rechazos(Canal canal, Map<String, String> skuPorImei) {
        if (skuPorImei.isEmpty()) return Map.of();
        Map<String, MovilNsys> moviles = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(skuPorImei.keySet())) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        Map<String, List<TrabajoAbierto>> trabajos = nsysDao.trabajosAbiertos(skuPorImei.keySet());
        Function<MovilNsys, LoQueVale.Valor> vale = taller.valedor(skuPorImei.keySet());
        AceptaSku acepta = new AceptaSku(nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(skuPorImei.values())));
        Map<String, Rechazo> rechazos = new LinkedHashMap<>();
        skuPorImei.forEach((imei, sku) -> {
            String clave = NsysDAO.claveCruce(imei);
            MovilNsys m = moviles.get(clave);
            AvisoImei aviso = AvisoImei.de(acepta.para(canal, sku), canal, m, m == null ? null : vale.apply(m),
                    trabajos.getOrDefault(clave, List.of()), false);
            if (!AvisoImei.MAL.equals(aviso.nivel())) return;
            if (NO_EN_NSYS.equals(aviso.motivo())) rechazos.put(imei, new Rechazo(NO_EN_NSYS, null));
            else rechazos.put(imei, new Rechazo(OTRO_MODELO,
                    "el pedido es " + sku + " y el móvil es " + (aviso.skuMovil() == null ? "—" : aviso.skuMovil())));
        });
        return rechazos;
    }
}
```

Si la firma de `AvisoImei.de` cambió en `origin/main` (Task 0 Step 2), usar la nueva con los mismos datos; `enOtraFila` va siempre a `false` (que esté en otro pedido es justo lo que corrige el libro).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=ComprobadorLibroTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/ComprobadorLibro.java src/test/java/com/reparaciones/servidor/pedidos/ComprobadorLibroTest.java
git commit -m "feat(pedidos): ComprobadorLibro, si el movil del libro cuadra con su pedido"
```

---

### Task 2: «Para revisar» con detalle (migración + servidor)

**Files:**
- Create: `sql/migracion-asociacion-detalle.sql`
- Modify: `sql/crear_bd.sql` (DDL de `Pedido_asociacion_revisar`, ~línea 729)
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/EstadoAsociaciones.java:22`
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java` (`SQL_INSERTAR_REVISAR`, `SQL_REVISAR`, `estado()`, `paraRevisar`)
- Test: `src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java`

**Interfaces:**
- Produces: `EstadoAsociaciones.Revisar(String pedido, String imei, String motivo, LocalDateTime vistoEn, String detalle)` + constructor de 4 argumentos (detalle null). Filas de `reparto.revisar`: `{canal, pedido, imei, motivo, detalle, visto}`. `Reparto.paraRevisar(String pedido, String imei, String motivo, String detalle)`.

- [ ] **Step 1: Migración**

`sql/migracion-asociacion-detalle.sql`:
```sql
-- 0.9.45 (spec 2026-11-24-asociacion-libro-manda-design.md): «para revisar» dice que es el movil cuando no cuadra con el pedido.
-- Aditiva e idempotente: la web y el servidor anteriores no leen la columna. Vuelta atras: ALTER TABLE Pedido_asociacion_revisar DROP COLUMN DETALLE;
ALTER TABLE Pedido_asociacion_revisar ADD COLUMN IF NOT EXISTS DETALLE VARCHAR(250) NULL
    COMMENT 'OTRO_MODELO: «el pedido es X y el movil es Y»; nulo en los demas' AFTER MOTIVO;
SELECT COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Pedido_asociacion_revisar' AND COLUMN_NAME = 'DETALLE';
```

En `sql/crear_bd.sql`, en `CREATE TABLE Pedido_asociacion_revisar`, cambiar el comentario de `MOTIVO` y añadir la columna:
```sql
    MOTIVO   VARCHAR(30) NOT NULL COMMENT 'SIN_HUECO, YA_SALIO, PEDIDO_AJENO, CANCELADO, REPETIDO_EN_LIBRO, NO_EN_NSYS u OTRO_MODELO',
    DETALLE  VARCHAR(250) NULL COMMENT 'OTRO_MODELO: «el pedido es X y el movil es Y»; nulo en los demas',
```

- [ ] **Step 2: Write the failing test**

En `AsociacionAmazonTest`, el ayudante `paraRevisar` pasa a llevar el detalle (null) en su sitio, y uno nuevo con detalle:
```java
    private static String paraRevisar(String pedido, String imei, String motivo) {
        return paraRevisar(pedido, imei, motivo, null);
    }

    private static String paraRevisar(String pedido, String imei, String motivo, String detalle) {
        return Arrays.toString(new Object[]{"AMAZON", pedido, imei, motivo, detalle, T});
    }
```
Y en `preparar()`, el `revisar.removeIf` sigue igual (`r[0]` es el canal). Test nuevo del estado:
```java
    @Test void elEstadoLeeElDetalleDeParaRevisar() {
        when(jdbc.query(eq(AsociacionAmazon.SQL_REVISAR), any(RowMapper.class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("CANAL")).thenReturn("AMAZON");
            when(rs.getString("PEDIDO")).thenReturn(PB);
            when(rs.getString("IMEI")).thenReturn(X);
            when(rs.getString("MOTIVO")).thenReturn("OTRO_MODELO");
            when(rs.getString("DETALLE")).thenReturn("el pedido es a y el móvil es b");
            when(rs.getTimestamp("VISTO_EN")).thenReturn(T);
            return List.of(((RowMapper<Object>) inv.getArgument(1)).mapRow(rs, 0));
        });
        when(jdbc.query(eq(AsociacionAmazon.SQL_PASADAS), any(RowMapper.class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("CANAL")).thenReturn("AMAZON");
            when(rs.getTimestamp("CUANDO")).thenReturn(T);
            return List.of(((RowMapper<Object>) inv.getArgument(1)).mapRow(rs, 0));
        });
        EstadoAsociaciones e = servicio.estado();
        assertEquals(new EstadoAsociaciones.Revisar(PB, X, "OTRO_MODELO", AHORA, "el pedido es a y el móvil es b"),
                e.pasadas().get(0).revisar().get(0));
    }
```
(Si `EstadoAsociaciones.Pasada` no expone `revisar()` con ese nombre, usar el accesor real del record; mirar `EstadoAsociaciones.java`.)

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=AsociacionAmazonTest`
Expected: FAIL (compilación del constructor de 5 argumentos de `Revisar`, y las listas de `revisar()` con 6 elementos frente a 5).

- [ ] **Step 4: Write minimal implementation**

`EstadoAsociaciones.java`, el record `Revisar` (línea 22) y su javadoc (añadir los motivos nuevos y `detalle`):
```java
    public record Revisar(String pedido, String imei, String motivo, LocalDateTime vistoEn,
                          @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String detalle) {
        /** Sin detalle (los motivos de antes de la 0.9.45). */
        public Revisar(String pedido, String imei, String motivo, LocalDateTime vistoEn) {
            this(pedido, imei, motivo, vistoEn, null);
        }
    }
```
`AsociacionAmazon.java`:
```java
    static final String SQL_INSERTAR_REVISAR = "INSERT INTO Pedido_asociacion_revisar (CANAL, PEDIDO, IMEI, MOTIVO, DETALLE, VISTO_EN)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    static final String SQL_REVISAR = "SELECT CANAL, PEDIDO, IMEI, MOTIVO, DETALLE, VISTO_EN FROM Pedido_asociacion_revisar ORDER BY CANAL, ID";
```
En `estado()`:
```java
        for (ConCanal c : jdbc.query(SQL_REVISAR, (rs, n) -> new ConCanal(rs.getString("CANAL"), new EstadoAsociaciones.Revisar(
                rs.getString("PEDIDO"), rs.getString("IMEI"), rs.getString("MOTIVO"), hora(rs, "VISTO_EN"), rs.getString("DETALLE")))))
```
En `Reparto`:
```java
        private void paraRevisar(String pedido, String imei, String motivo) {
            paraRevisar(pedido, imei, motivo, null);
        }

        private void paraRevisar(String pedido, String imei, String motivo, String detalle) {
            revisar.add(new Object[]{canal.name(), pedido, imei, motivo, detalle, hora});
        }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=AsociacionAmazonTest`
Expected: PASS (todos los de antes y el nuevo).

- [ ] **Step 6: Commit**

```bash
git add sql/migracion-asociacion-detalle.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/pedidos/EstadoAsociaciones.java src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java
git commit -m "feat(pedidos): para revisar de la asociacion guarda un detalle"
```

---

### Task 3: Un IMEI que el libro pone en dos pedidos va a revisión en los dos

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java` (`pasada`, javadoc de la clase, constante)
- Test: `src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java` (sustituye `unImeiRepetidoEnLaPasadaSoloValeLaUltimaVezYLasAnterioresSeDescartan`)

**Interfaces:**
- Produces: `AsociacionAmazon.REPETIDO_EN_LIBRO = "REPETIDO_EN_LIBRO"`; el reparto ya no recibe IMEIs repetidos entre pedidos.

- [ ] **Step 1: Write the failing test**

Borrar `unImeiRepetidoEnLaPasadaSoloValeLaUltimaVezYLasAnterioresSeDescartan` y poner:
```java
    @Test void unImeiEnDosPedidosDelLibroVaARevisarEnLosDosYNoSeTocaNinguno() {
        fila(10, PA, 1, null);
        fila(20, PB, 1, null);
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 2, 0), aplicar(PA, X, PB, X));
        assertEquals(antes, estado());
        assertTrue(apuntes.isEmpty());
        assertEquals(List.of(paraRevisar(PA, X, "REPETIDO_EN_LIBRO"), paraRevisar(PB, X, "REPETIDO_EN_LIBRO")), revisar());
    }

    @Test void elMismoParRepetidoNoEsRepetidoYLasRepeticionesSeDescartan() {
        Fila b = fila(20, PB, 1, null);
        assertEquals(r(0, 0, 1, 0, 0, 1), aplicar(PB, X, PB, X));
        assertEquals(X, b.imei);
        assertTrue(revisar().isEmpty());
    }

    @Test void unImeiRepetidoEnDosPedidosNoImpideQueLosDemasParesSeApliquen() {
        fila(10, PA, 1, null);
        Fila c = fila(30, PC, 1, null);
        assertEquals(r(0, 0, 1, 0, 2, 0), aplicar(PA, X, PB, X, PC, Y));
        assertEquals(Y, c.imei);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AsociacionAmazonTest`
Expected: FAIL en los tres (hoy vale la última línea y no hay `REPETIDO_EN_LIBRO`).

- [ ] **Step 3: Write minimal implementation**

Constante junto a las demás:
```java
    /** El libro pone el mismo IMEI en dos pedidos distintos: no se aplica ninguno (spec «el libro manda» §1). */
    public static final String REPETIDO_EN_LIBRO = "REPETIDO_EN_LIBRO";
```
En `pasada(...)`, sustituir el bloque que va desde `Map<String, Integer> ultima = new HashMap<>();` hasta el final del bucle de `aplicables` por:
```java
        // Un IMEI en dos pedidos distintos: todas sus lineas a revisar y no se aplica ninguna. El mismo par repetido cuenta una vez.
        Map<String, Set<String>> pedidosPorImei = new HashMap<>();
        for (String[] v : validos) pedidosPorImei.computeIfAbsent(v[1], k -> new LinkedHashSet<>()).add(v[0]);
        List<Object[]> repetidos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        List<String[]> aplicables = new ArrayList<>();
        for (String[] v : validos) {
            if (!vistos.add(v[0] + "|" + v[1])) {
                descartadas++;
                continue;
            }
            if (pedidosPorImei.get(v[1]).size() > 1) repetidos.add(new Object[]{canal.name(), v[0], v[1], REPETIDO_EN_LIBRO, null, hora});
            else aplicables.add(v);
        }
```
(añadir `import java.util.HashSet;`). Justo después de crear el `Reparto` (`Reparto reparto = new Reparto(...)`), antes de aplicar los pares:
```java
        reparto.revisar.addAll(repetidos);
```
Actualizar el javadoc de la clase: la frase «Si un IMEI sale varias veces en la pasada solo vale la ultima (las anteriores se descartan)» pasa a «Si el libro pone un IMEI en dos pedidos distintos, sus lineas van a «para revisar» (REPETIDO_EN_LIBRO) y no se aplica ninguna; el mismo par repetido cuenta una vez».

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AsociacionAmazonTest`
Expected: PASS. Si falla `unParConPedidoAjenoNoSeReintentaYRepetirLaPasadaConReintentoNoCambiaNada` u otro que repite un IMEI entre pedidos a propósito, leerlo: si su idea era «vale la última», cambiar su expectativa a `REPETIDO_EN_LIBRO`; si no, revisar el cambio.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java
git commit -m "feat(pedidos): un IMEI que el libro pone en dos pedidos va a revisar en los dos"
```

---

### Task 4: El libro manda en pedidos con etiqueta

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java` (constructor, `HUECOS`, `Hueco`, `hueco()`, `pasada`, `Reparto`)
- Test: `src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java`

**Interfaces:**
- Consumes: `ComprobadorLibro.rechazos(Canal, Map<String,String>)`, `ComprobadorLibro.Rechazo` (Task 1); `Reparto.paraRevisar(pedido, imei, motivo, detalle)` y `reparto.revisar` con 6 columnas (Task 2); `aplicables` sin repetidos (Task 3).
- Produces: `public AsociacionAmazon(JdbcTemplate jdbc, ComprobadorLibro comprobador)`.

- [ ] **Step 1: Preparar el test para el comprobador y el SKU**

En `AsociacionAmazonTest`:
```java
    /** IMEI → rechazo que da el comprobador falso (por defecto, todo vale). */
    private final Map<String, ComprobadorLibro.Rechazo> rechazos = new HashMap<>();
    /** Lo que se le pidio comprobar en cada pasada (IMEI → SKU del pedido). */
    private final List<Map<String, String>> comprobados = new ArrayList<>();
    private final ComprobadorLibro comprobador = mock(ComprobadorLibro.class);
    private final AsociacionAmazon servicio = new AsociacionAmazon(jdbc, comprobador);
```
(sustituye la línea `private final AsociacionAmazon servicio = new AsociacionAmazon(jdbc);`; añadir `import java.util.HashMap; import java.util.Map;`). En `preparar()`:
```java
        when(comprobador.rechazos(any(Canal.class), anyMap())).thenAnswer(inv -> {
            Map<String, String> pedidos = inv.getArgument(1);
            comprobados.add(Map.copyOf(pedidos));
            Map<String, ComprobadorLibro.Rechazo> r = new HashMap<>();
            for (String imei : pedidos.keySet()) if (rechazos.containsKey(imei)) r.put(imei, rechazos.get(imei));
            return r;
        });
```
En la clase `Fila`, un campo `String sku = "i14128AzulA";` y en `rs(f)`: `when(rs.getString("SKU")).thenReturn(f.sku);`.

- [ ] **Step 2: Write the failing tests (reglas nuevas)**

```java
    // ------------------------------------------------------------------ el libro manda en pedidos con etiqueta (spec §2.3)

    private static String libro(int id, String anterior, String nuevo) {
        return apunte(id, anterior, nuevo, "IMEI", "LIBRO");
    }

    @Test void conEtiquetaSinImprimirPoneElImeiDelLibroYLoConfirma() {
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertEquals("LIBRO", b.asociadaOrigen);
        assertEquals(List.of(libro(20, Y, X), asociada(20)), apuntes());
        assertEquals(List.of(Map.of(X, "i14128AzulA")), comprobados, "se comprueba con el SKU del pedido");
    }

    @Test void siElMovilNoCuadraVaARevisarConSuDetalleYNoSeToca() {
        Fila b = fila(20, PB, 1, Y).etiqueta();
        rechazos.put(X, new ComprobadorLibro.Rechazo("OTRO_MODELO", "el pedido es i14128AzulA y el móvil es i14256AzulA"));
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(Y, b.imei);
        assertEquals(List.of(paraRevisar(PB, X, "OTRO_MODELO", "el pedido es i14128AzulA y el móvil es i14256AzulA")), revisar());
    }

    @Test void siElImeiDelLibroNoEstaEnNsysVaARevisar() {
        fila(20, PB, 1, Y).etiqueta();
        rechazos.put(X, new ComprobadorLibro.Rechazo("NO_EN_NSYS", null));
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(List.of(paraRevisar(PB, X, "NO_EN_NSYS")), revisar());
    }

    @Test void elImeiDeOtroPedidoConEtiquetaSeLeQuitaYEseSeQuedaSinImeiNiAsociar() {
        Fila o = fila(10, PA, 1, X).etiqueta();
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertEquals(AHORA, b.asociadaEn);
        assertNull(o.imei, "el ERP no inventa que fue en PA: lo dira su linea del libro");
        assertNull(o.asociadaEn);
        assertEquals(List.of(libro(20, Y, X), libro(10, X, null), asociada(20)), apuntes());
    }

    @Test void unCruceDeDosPedidosConEtiquetaSeResuelveEnLaMismaPasada() {
        Fila a = fila(10, PA, 1, Y).etiqueta();
        Fila b = fila(20, PB, 1, X).etiqueta();
        aplicar(PA, X, PB, Y);
        assertEquals(X, a.imei);
        assertEquals(Y, b.imei);
        assertEquals(AHORA, a.asociadaEn);
        assertEquals(AHORA, b.asociadaEn);
        assertTrue(revisar().isEmpty());
    }

    @Test void elImeiDeUnPedidoPendienteSeIntercambiaComoHoy() {
        Fila o = fila(10, PA, 1, X);
        Fila b = fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b.imei);
        assertEquals(Y, o.imei, "el pendiente recibe el que tenia la fila de B, como en la regla 2");
        assertNull(o.asociadaEn);
    }

    @Test void elImeiConfirmadoEnOtroPedidoEsYaSalioYNoSeToca() {
        fila(10, PA, 1, X).etiqueta().asociada("LIBRO");
        fila(20, PB, 1, Y).etiqueta();
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PB, X, "YA_SALIO")), revisar());
    }

    @Test void elImeiEnUnaFilaCanceladaDeOtroPedidoEsCancelado() {
        fila(10, PA, 1, X).marca("CANCELADO");
        fila(20, PB, 1, Y).etiqueta();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(List.of(paraRevisar(PB, X, "CANCELADO")), revisar());
    }

    @Test void conVariasUnidadesVaALaFilaCuyoImeiNoEstaEnElLibro() {
        Fila b1 = fila(20, PB, 1, Y).etiqueta();
        Fila b2 = fila(21, PB, 2, Z).etiqueta();
        aplicar(PB, Y, PB, X);
        assertEquals(Y, b1.imei, "Y la pone el libro en PB: se confirma");
        assertEquals(AHORA, b1.asociadaEn);
        assertEquals(X, b2.imei, "X va a la fila de Z, que el libro no pone en PB");
        assertEquals(AHORA, b2.asociadaEn);
    }

    @Test void unaFilaConEtiquetaCanceladaNoRecibeNada() {
        Fila b = fila(20, PB, 1, Y).etiqueta().marca("CANCELADO");
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PB, X));
        assertEquals(Y, b.imei);
        assertEquals(List.of(paraRevisar(PB, X, "SIN_HUECO")), revisar(), "sin fila corregible siguen las reglas de hoy");
        assertTrue(comprobados.isEmpty() || comprobados.get(0).isEmpty());
    }

    @Test void conUnaFilaPendienteLibreEnElPedidoSiguenLasReglasDeHoyYNoSeComprueba() {
        Fila b1 = fila(20, PB, 1, null).etiqueta();
        Fila b2 = fila(21, PB, 2, null);
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertNull(b1.imei);
        assertEquals(X, b2.imei);
        assertTrue(comprobados.isEmpty() || comprobados.get(0).isEmpty());
    }
```

Y adaptar los tres tests de hoy cuya expectativa era «con etiqueta no se toca»:
```java
    @Test void regla2SinFilaPendienteYConUnaConEtiquetaElLibroLaUsa() {   // antes: regla2SinFilaSinConfirmarNiSinEtiquetaEnElPedidoQuedaParaRevisarSinHueco
        Fila a = fila(10, PA, 1, X);
        fila(20, PB, 1, Y).asociada("LIBRO");
        Fila b2 = fila(21, PB, 2, null).etiqueta();
        assertEquals(r(0, 1, 0, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b2.imei);
        assertNull(a.imei, "pendiente: intercambio con el vacio de la fila de B");
        assertTrue(revisar().isEmpty());
    }

    @Test void regla3ConEtiquetaElLibroPoneElImeiYSiSoloQuedanCanceladasEsCancelado() {   // antes: regla3SinHuecoQuedaParaRevisarYSiSoloQuedanCanceladasEsCancelado
        fila(20, PB, 1, Y).asociada("LIBRO");
        Fila b2 = fila(21, PB, 2, Z).etiqueta();
        assertEquals(r(0, 0, 1, 0, 0, 0), aplicar(PB, X));
        assertEquals(X, b2.imei);

        fila(30, PC, 1, null).marca("CANCELADO");
        List<String> antes = estado();
        assertEquals(r(0, 0, 0, 0, 1, 0), aplicar(PC, W));
        assertEquals(antes, estado());
        assertEquals(List.of(paraRevisar(PC, W, "CANCELADO")), revisar(), "la lista se rehace: solo la ultima pasada");
    }

    @Test void unParQueSigueSinHuecoTrasLasVueltasSeGuardaComoSinHueco() {
        fila(20, PB, 1, Y);
        fila(30, PC, 1, Z).etiqueta();
        assertEquals(r(0, 1, 0, 0, 1, 0), aplicar(PB, W, PC, Y));
        assertEquals(List.of(paraRevisar(PB, W, "SIN_HUECO")), revisar());
        assertEquals(Y, porId(30).imei, "el libro manda en PC, que tiene etiqueta");
        assertEquals(Z, porId(20).imei, "PB pendiente recibe por intercambio el que tenia PC; W sigue sin hueco");
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=AsociacionAmazonTest`
Expected: FAIL de compilación (constructor de dos argumentos) y, tras él, los tests nuevos y los tres adaptados.

- [ ] **Step 4: Write the implementation**

Constructor y campo:
```java
    private final JdbcTemplate jdbc;
    private final ComprobadorLibro comprobador;

    public AsociacionAmazon(JdbcTemplate jdbc, ComprobadorLibro comprobador) {
        this.jdbc = jdbc;
        this.comprobador = comprobador;
    }
```
`HUECOS` y `hueco()` con el SKU:
```java
    private static final String HUECOS = "SELECT ID_FILA, PEDIDO, POSICION, SKU, IMEI, ENVIO_FECHA, MARCA, ASOCIADA_EN, ASOCIADA_ORIGEN"
            + " FROM Pedido_fila WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ? AND ";

    private static Hueco hueco(ResultSet rs, int n) throws SQLException {
        return new Hueco(rs.getInt("ID_FILA"), rs.getString("PEDIDO"), rs.getObject("POSICION", Integer.class), rs.getString("SKU"),
                rs.getString("IMEI"), rs.getTimestamp("ENVIO_FECHA") != null, rs.getString("MARCA"), hora(rs, "ASOCIADA_EN"),
                rs.getString("ASOCIADA_ORIGEN"));
    }
```
En `Hueco`: campo `final String sku;` y el constructor `Hueco(int id, String pedido, Integer posicion, String sku, String imei, boolean conEtiqueta, String marca, LocalDateTime asociadaEn, String asociadaOrigen)` que lo guarda. Y un método:
```java
        /** Una fila con etiqueta en la que el libro puede corregir el IMEI: sin confirmar y sin cancelar. */
        boolean corregible() {
            return conEtiqueta && !confirmada() && !cancelada();
        }
```
En `pasada(...)`, entre `Reparto reparto = new Reparto(...)` (y el `addAll(repetidos)`) y el bucle que aplica los pares:
```java
        // Los IMEI que el libro pone en cada pedido, y la comprobacion de los que pueden ir a una fila con etiqueta (una lectura).
        Map<String, Set<String>> delLibro = new HashMap<>();
        for (String[] p : aplicables) delLibro.computeIfAbsent(p[0], k -> new HashSet<>()).add(p[1]);
        Map<String, String> aComprobar = new LinkedHashMap<>();
        for (String[] p : aplicables) {
            Hueco destino = reparto.destinoConEtiqueta(p[0], p[1], delLibro);
            if (destino != null) aComprobar.put(p[1], destino.sku);
        }
        reparto.preparar(delLibro, comprobador.rechazos(canal, aComprobar));
```
En `Reparto`, campos y métodos nuevos:
```java
        Map<String, Set<String>> delLibro = Map.of();
        Map<String, ComprobadorLibro.Rechazo> rechazos = Map.of();

        void preparar(Map<String, Set<String>> delLibro, Map<String, ComprobadorLibro.Rechazo> rechazos) {
            this.delLibro = delLibro;
            this.rechazos = rechazos;
        }

        /** Si el par va por el camino «con etiqueta» (spec §2.3): el pedido existe, el IMEI no esta ya en el, no tiene ninguna fila
         *  pendiente sin confirmar ni cancelar, y si una fila con etiqueta corregible cuyo IMEI (o ninguno) no lo pone el libro en el
         *  pedido. Devuelve esa fila (la primera por posicion) o null. */
        Hueco destinoConEtiqueta(String pedido, String imei, Map<String, Set<String>> delLibro) {
            List<Hueco> deB = porPedido.getOrDefault(pedido, List.of());
            if (deB.isEmpty() || deB.stream().anyMatch(h -> imei.equals(h.imei))) return null;
            if (deB.stream().anyMatch(h -> h.abierto() && !h.cancelada())) return null;
            Set<String> delPedido = delLibro.getOrDefault(pedido, Set.of());
            return deB.stream().filter(h -> h.corregible() && (h.imei == null || !delPedido.contains(h.imei))).findFirst().orElse(null);
        }
```
En `intentar(...)`, justo después del bloque de la regla 1 (antes de `// 2. Esta en una fila de otro pedido.`):
```java
            // El libro manda en un pedido sin fila pendiente y con filas con etiqueta (spec «el libro manda» §2.3).
            if (deB.stream().noneMatch(h -> h.abierto() && !h.cancelada()) && deB.stream().anyMatch(Hueco::corregible))
                return conEtiqueta(pedido, imei);
```
Y el método:
```java
        /** La regla con etiqueta: comprobacion del movil, la fila de B y lo que pasa con la fila de otro pedido que tenia X. */
        private String conEtiqueta(String pedido, String imei) {
            ComprobadorLibro.Rechazo rechazo = rechazos.get(imei);
            if (rechazo != null) {
                paraRevisar(pedido, imei, rechazo.motivo(), rechazo.detalle());
                return null;
            }
            Set<Hueco> fuera = porImei.getOrDefault(imei, Set.of());
            if (fuera.stream().anyMatch(Hueco::confirmada)) return YA_SALIO;
            if (fuera.stream().anyMatch(Hueco::cancelada)) return CANCELADO;
            Hueco destino = destinoConEtiqueta(pedido, imei, delLibro);
            if (destino == null) return SIN_HUECO;
            String delDestino = destino.imei;
            List<Hueco> otros = List.copyOf(fuera);                       // copia: ponerImei cambia porImei
            ponerImei(destino, imei);
            Hueco pendiente = null;
            for (Hueco o : otros) {
                if (o.conEtiqueta) ponerImei(o, null);                     // el ERP no inventa que fue en O
                else if (pendiente == null) pendiente = o;
                else ponerImei(o, null);
            }
            if (pendiente != null) {
                ponerImei(pendiente, delDestino);
                apunte(destino.id, null, null, CambioImei.IMEI_INTERCAMBIADO, "con la fila " + pendiente.id);
                apunte(pendiente.id, null, null, CambioImei.IMEI_INTERCAMBIADO, "con la fila " + destino.id);
            }
            confirmar(destino);
            if (otros.isEmpty()) puestas++;
            else intercambiadas++;
            return null;
        }
```
`ponerImei` deja el apunte con `DETALLE = LIBRO` en las filas con etiqueta (cambiar solo la línea del apunte):
```java
            apunte(h.id, anterior, nuevo, CambioImei.IMEI, h.conEtiqueta ? LIBRO : null);
```
**Orden de los apuntes en `conEtiqueta`:** primero la fila de B, después la de O y al final la confirmación (el test `elImeiDeOtroPedidoConEtiquetaSeLeQuitaYEseSeQuedaSinImeiNiAsociar` espera `libro(20, Y, X), libro(10, X, null), asociada(20)`).

Actualizar el javadoc de la clase (`<ol>`): añadir el punto «Si B no tiene ninguna fila sin etiqueta ni confirmar y si filas con etiqueta: el libro manda (spec «el libro manda» §2.3)…» con lo de `ComprobadorLibro`, la fila destino, O con etiqueta sin IMEI, O pendiente con intercambio, O confirmada `YA_SALIO`, O cancelada `CANCELADO`; y quitar «Nunca cambia el IMEI de una fila con la etiqueta generada (ENVIO_FECHA)» → «Solo cambia el IMEI de una fila con etiqueta por esa regla, y lo apunta con DETALLE = LIBRO».

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=AsociacionAmazonTest+ComprobadorLibroTest`
Expected: PASS. Después `mvn -q test` (suite entera, con `OpenApiContractTest`, que deja `target/openapi.json`): PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/AsociacionAmazon.java src/test/java/com/reparaciones/servidor/pedidos/AsociacionAmazonTest.java
git commit -m "feat(pedidos): el libro de ventas manda tambien en pedidos de Amazon con etiqueta"
```

---

### Task 5: Contrato y documentación del servidor

**Files:**
- Modify: `docs/api_contract.md` (servidor; sección de `/api/pedidos/asociaciones`)

- [ ] **Step 1: Contrato**

En la descripción del `GET /api/pedidos/asociaciones`, cada elemento de `revisar` pasa a `{pedido, imei, motivo, vistoEn, detalle}` con `detalle` nulo salvo en `OTRO_MODELO`, y los motivos: `SIN_HUECO`, `YA_SALIO`, `CANCELADO`, `REPETIDO_EN_LIBRO` (el libro pone el IMEI en dos pedidos), `NO_EN_NSYS` (el IMEI del libro no está en NSYS), `OTRO_MODELO` (el móvil no cuadra con el pedido: modelo, capacidad, color, grado o SKU). Añadir una línea: «Desde la 0.9.45 el libro también corrige el IMEI de pedidos con etiqueta (spec 2026-11-24-asociacion-libro-manda-design.md)».

- [ ] **Step 2: Commit**

```bash
git add docs/api_contract.md
git commit -m "docs: contrato de asociaciones, motivos nuevos y detalle"
```

---

### Task 6: Web — textos de los motivos nuevos, detalle y versión

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados)
- Modify: `src/modules/telefonos/pedidos/filas.ts:143-152`, `filas.test.ts`
- Modify: `src/modules/telefonos/pedidos/PildoraAsociacion.tsx:49`
- Modify: `package.json`, `package-lock.json`, `CHANGELOG.md`

**Interfaces:**
- Consumes: `revisar[].detalle?: string | null` del contrato (Task 2).

- [ ] **Step 1: Regenerar los tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-asociacion-libro/target/openapi.json /c/Users/dev/Documents/wt/web-asociacion-libro/api/openapi.json
cd /c/Users/dev/Documents/wt/web-asociacion-libro && npm run api:types:offline && git diff --stat
```
Expected: cambian `api/openapi.json` y `src/shared/api/schema.d.ts` (aparece `detalle` en el esquema de `Revisar`).

- [ ] **Step 2: Write the failing test**

En `filas.test.ts`, junto a los de `textoRevisar` (añadirlos al `it.each` que ya haya o en un `describe` nuevo):
```ts
describe('textoRevisar, motivos del libro que manda', () => {
  it.each([
    ['REPETIDO_EN_LIBRO', 'el libro pone este IMEI en dos pedidos'],
    ['NO_EN_NSYS', 'el IMEI del libro no está en NSYS'],
    ['OTRO_MODELO', 'el móvil del libro no cuadra con el pedido'],
  ])('%s', (motivo, texto) => {
    expect(textoRevisar(motivo)).toBe(texto)
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `npx vitest run src/modules/telefonos/pedidos/filas.test.ts`
Expected: FAIL (devuelve el motivo tal cual).

- [ ] **Step 4: Implementation**

`filas.ts`, en `textoRevisar`:
```ts
    case 'REPETIDO_EN_LIBRO': return 'el libro pone este IMEI en dos pedidos'
    case 'NO_EN_NSYS': return 'el IMEI del libro no está en NSYS'
    case 'OTRO_MODELO': return 'el móvil del libro no cuadra con el pedido'
```
`PildoraAsociacion.tsx`, la línea del motivo (49) pasa a:
```tsx
                      <div className="text-[11px] text-azul-gris">{textoRevisar(r.motivo)}{r.detalle ? `: ${r.detalle}` : ''}</div>
```
Versión: `npm version 0.9.45 --no-git-tag-version` (o el número confirmado en Task 0). `CHANGELOG.md`, entrada nueva arriba:
```markdown
## 0.9.45 — 2026-11-xx

- Pedidos, Amazon: el libro de ventas corrige también el IMEI de los pedidos con la etiqueta hecha (impresa o no), si el móvil
  cuadra con el pedido. En «para revisar» salen tres motivos nuevos: el libro pone el IMEI en dos pedidos, el IMEI no está en NSYS
  o el móvil no cuadra con el pedido (y qué móvil es).
```

- [ ] **Step 5: Run tests and build**

Run: `npx vitest run && npm run build`
Expected: PASS y build sin errores de tipos.

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/telefonos/pedidos/filas.ts src/modules/telefonos/pedidos/filas.test.ts src/modules/telefonos/pedidos/PildoraAsociacion.tsx package.json package-lock.json CHANGELOG.md
git commit -m "feat(pedidos): textos de los motivos nuevos de la asociacion y 0.9.45"
```

---

### Task 7: Documentación del módulo (raíz)

**Files:**
- Modify: `docs/modulos/pedidos.md` (sección de la asociación de Amazon)

- [ ] **Step 1: Texto**

En la sección de la asociación con los libros de ventas, sustituir la frase que dice que nunca se toca una fila con etiqueta por:
```markdown
- **El libro manda también en pedidos con etiqueta** (0.9.45). Si el pedido no tiene ninguna fila pendiente libre y sí filas con la
  etiqueta hecha (impresa o no), el ERP pone el IMEI del libro en la fila cuyo IMEI el libro no reconoce y la confirma, siempre que el
  móvil cuadre con el pedido (lo mismo que cuenta en «no cuadran» de la celda; la eSIM no cuenta). Si ese IMEI estaba en otro pedido
  con etiqueta, ese otro pedido se queda sin IMEI y sin asociar hasta que llegue su línea del libro; si estaba en uno pendiente, se
  intercambian como siempre. El IMEI quitado queda en el historial de la fila («libro»), para la futura devolución automática a stock.
- Van a «para revisar»: el libro pone el IMEI en dos pedidos; el IMEI no está en NSYS; el móvil no cuadra (con qué móvil es).
```

- [ ] **Step 2: Commit (en la raíz, rama main local; sin push)**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/modulos/pedidos.md && git commit -m "docs: Pedidos, el libro de Amazon manda tambien con etiqueta (0.9.45)"
```

---

### Task 8: Preprod (con el usuario)

**Files:** guion en `C:/Users/dev/Documents/Apuntes/preprod/v0945-preprod.md` (fuera del repo: puede llevar datos reales).

- [ ] **Step 0: Traer la versión de la celda** (cuando esté en `origin/main`): `git fetch` y rebase (o merge) de `dev_asociacion_libro` sobre `origin/main` en servidor y web; resolver conflictos de `AvisoImei` en `ComprobadorLibro` (firma y motivos nuevos; la regla sigue siendo nivel `MAL`); añadir a `ComprobadorLibroTest` que un eSIM en un pedido sin eSIM (y al revés) **no** se rechaza; suite completa. Renumerar la versión (web `package.json`, `CHANGELOG.md`, raíz `docs/modulos/pedidos.md`, que hoy dice «versión prevista 0.9.45, aún no en producción»).
- [ ] **Step 1: Pedir OK para subir las ramas** (push de `dev_asociacion_libro` en servidor y web). Sin OK, parar.
- [ ] **Step 2: Escribir el guion de preprod** con el formato de siempre (cada bloque empieza con `hostname` y `ls /usr/local/sbin/backup-erp.sh`, que en preprod debe NO existir; los bloques que escriben llevan el seguro `if [ ! -x /usr/local/sbin/backup-erp.sh ]`): 1) solo lectura: lista «para revisar» actual (la consulta de simulación del 24/11); 2) migración `migracion-asociacion-detalle.sql` con `docker exec -i … < fichero`; 3) build y arranque del servidor con la rama (build en primer plano, avisando de que no saca salida un rato); 4) `UPDATE Pedido_asociacion_pasada SET LIBRO_MODIFICADO = NULL;` y esperar a la siguiente vuelta del lector; 5) lectura: la lista nueva, recuentos de la pasada y los apuntes `DETALLE = 'LIBRO'` de hoy; 6) web con la rama.
- [ ] **Step 3: El usuario lo ejecuta y pega las salidas.** Comparar con la simulación del 24/11 (spec §4): casi todos corregidos; en revisión solo los que no cuadran, con su detalle.
- [ ] **Step 4: Probar en la web de preprod** la píldora «Asociación Amazon» (motivos nuevos con detalle) y buscar por número de pedido uno corregido.

### Task 9: Producción (solo con OK explícito)

- [ ] **Step 1: OK del usuario** para merge `--no-ff` a `main` en servidor y web, push y tag `v0.9.45` en los dos.
- [ ] **Step 2: Guion de producción** en `C:/Users/dev/Documents/Apuntes/prod-v0945.md` (copia de seguridad con `/usr/local/sbin/backup-erp.sh` primero; seguro `if [ -x … ]`; migración → servidor → web; forzar la pasada; lectura de la lista y de los recuentos; vuelta atrás: imágenes anteriores y la migración se puede dejar, es aditiva).
- [ ] **Step 3: Tras la pasada**, comprobar con el usuario los pedidos del 23/11 (la consulta del 24/11 de «enviados sin asociar» por día) y apuntar en la memoria del proyecto el resultado.
- [ ] **Step 4: Gitlinks** en la raíz (`chore: gitlinks de la 0.9.45 en produccion (servidor …, web …)`), con OK para el push.
