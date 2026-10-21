# 0.9.21 (pieza «corrección en NSYS») — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que una baja y alta en otro lote de NSYS (una corrección) cuente como el mismo móvil: una línea «Corrección en
NSYS» en el rastro y, en la ficha y en la tabla del stock, los días, las entradas, la revisión y la última caja de toda la
cadena.

**Architecture:** una clase pura nueva, `Correcciones`, saca de los lotes y los eventos de un IMEI las correcciones y sus
cadenas. La usan `Rastro.unir` (evento `CORRECCION`) y `StockTelefonosController` (ficha y `GET /`). Todo se calcula al
leer: no cambia la base ni lo guardado. La web añade el texto de `CORRECCION`.

**Tech Stack:** Java 17, Spring Boot 3.3, JUnit 5 + Mockito; React + TypeScript, Vitest, openapi-typescript.

**Spec:** `docs/superpowers/specs/2026-10-21-v0921-correccion-nsys-design.md` (raíz, aprobada el 21/10).

## Global Constraints

- **Pieza de la 0.9.21**, la misma versión que el «SKU del taller» (otra sesión, `programareparaciones-f6`). Entra la
  primera en `main` de los dos repos; al mergear, se avisa a esa sesión con los dos commits.
- **Servidor + web, sin base de datos.** No cambia el esquema ni lo guardado; nada de SQL de limpieza.
- **Regla:** L1 sigue en L2 si L1 tiene un `ESTADO` → `Removed` con hora (no `SOLO_DIA`) el día D y L2 (otro lote del mismo
  IMEI, con fecha de recepción) se recibe entre **D−7 y D+1**, no antes que L1; el más cercano a D (empate: lote mayor); un
  L2 sigue a un solo L1; se encadena.
- **`EventoRastro`:** campo nuevo `loteAnterior` **el último** (la sesión del SKU del taller pone los suyos detrás). Tipo
  nuevo `CORRECCION`: `lote` = L2, `loteAnterior` = L1, `de`/`a` = «capacidad color · grado» (maestro) o «Goods · grado».
- **`Rastro.unir(lotes, nsys, trabajos)`**; la de dos parámetros sigue y llama a esta con `List.of()`.
- **Versión:** esta pieza sube la web a **0.9.21** con `npm pkg set version=0.9.21` y las dos entradas raíz de
  `package-lock.json` a mano (**nunca `npm version`**); abre `## [0.9.21] - 2026-10-XX` en el CHANGELOG de la web y crea
  `docs/novedades/NOVEDADES-v0.9.21.md` en la raíz. Fecha y tag, tras producción (con el resto de la 0.9.21).
- **Ramas** `dev_v0921_correccion_nsys`: servidor desde `main` `4c4cfe5` en `C:/Users/dev/Documents/wt/servidor-0921c`;
  web desde `main` (`68fb5e8` o el que haya) en `C:/Users/dev/Documents/wt/web-0921c`.
- **Commits** sin `Co-Authored-By`, en español sin tildes. **Merge, push y despliegues, solo con el OK del usuario**, con
  `git fetch` y comparación con `origin` antes (hay otras sesiones).
- **Claude no hace SSH**; repos públicos: ni IMEIs reales ni nombres de máquinas en el repo (los guiones van a `Apuntes`).

**Maven** (Git Bash), delante de cada `mvn`:

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17
export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
```

---

### Task 1: `Correcciones` — la regla y las cadenas

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Correcciones.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/MovilNsys.java` (`conRecepcion`)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/CorreccionesTest.java`

**Interfaces:**
- Produces: `Correcciones.de(List<MovilNsys> lotes, List<EventoNsys> eventos)`, `Correcciones.NINGUNA`,
  `record Correcciones.Correccion(String loteAnterior, String lote, LocalDateTime fecha)`, y los métodos `todas()`,
  `primerLote(String)`, `ultimoLote(String)`, `lote(String)`, `ultimaCajaAnterior(String)`, `sustituido(EventoNsys)`,
  `paraRevision(List<EventoNsys>)`, `descripcion(String)`; `MovilNsys.conRecepcion(LocalDate)`.

- [ ] **Step 1: Worktree y rama del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch -q origin && git status -sb | head -1 && git log --oneline -1 origin/main
git worktree add -b dev_v0921_correccion_nsys /c/Users/dev/Documents/wt/servidor-0921c origin/main
cd /c/Users/dev/Documents/wt/servidor-0921c && git log --oneline -1 && mvn -q test 2>&1 | tail -3
```

Expected: `4c4cfe5 merge: un movil vendido o de baja no esta en revision …` (o más reciente: apuntarlo) y `BUILD SUCCESS`
con 1129 tests (apuntar la cifra: es la base).

- [ ] **Step 2: El test que falla**

`CorreccionesTest.java`:

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Corrección en NSYS (spec 0.9.21 «corrección en NSYS» §2): baja de un lote y alta del IMEI en otro. */
class CorreccionesTest {

    private static final String IMEI = "350000000000001";

    private static LocalDate d(int dia, int mes) {
        return LocalDate.of(2026, mes, dia);
    }

    private static LocalDateTime t(int dia, int mes) {
        return LocalDateTime.of(2026, mes, dia, 10, 1);
    }

    private static MovilNsys lote(String batch, LocalDate recepcion) {
        return new MovilNsys(IMEI, batch, "Apple iPhone 15 eSIM 512GB Yellow", "A", "InProcess", "CAJA LISTOS",
                "Proveedor A", recepcion, null, null, "15", "15", "512GB", "Amarillo", "A", true, "Listos", "Listos",
                EstadoMovil.STOCK, t(1, 1), t(1, 1));
    }

    private static EventoNsys baja(String batch, LocalDateTime fecha) {
        return new EventoNsys(IMEI, batch, fecha, false, TipoEvento.ESTADO, "InProcess", "Removed", null);
    }

    @Test void unaBajaYUnAltaElMismoDiaEsUnaCorreccion() {
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(23, 9)), lote("BT-2", d(20, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals(List.of(new Correcciones.Correccion("BT-1", "BT-2", t(20, 10))), c.todas());
        assertEquals("BT-1", c.primerLote("BT-2"));
        assertEquals("BT-2", c.ultimoLote("BT-1"));
    }

    @Test void elAltaHastaSieteDiasAntesOUnDiaDespuesCuenta() {
        assertEquals(1, Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(13, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().size(), "7 días antes");
        assertEquals(1, Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(21, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().size(), "1 día después");
    }

    @Test void fueraDelMargenNoEsCorreccion() {
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(12, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "8 días antes");
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(22, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "2 días después");
    }

    @Test void unaReentradaSemanasDespuesNoEsCorreccion() {
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 8)), lote("BT-2", d(15, 10))),
                List.of(baja("BT-1", t(2, 9)))).todas().isEmpty());
    }

    @Test void tresLotesSonUnaCadena() {
        Correcciones c = Correcciones.de(List.of(lote("BT-1307", d(6, 7)), lote("BT-1309", d(7, 7)), lote("BT-1345", d(20, 10))),
                List.of(baja("BT-1307", t(7, 7)), baja("BT-1309", t(20, 10))));
        assertEquals(2, c.todas().size());
        assertEquals("BT-1307", c.primerLote("BT-1345"));
        assertEquals("BT-1345", c.ultimoLote("BT-1307"));
    }

    @Test void conDosCandidatosGanaElMasCercano() {
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(17, 10)), lote("BT-3", d(20, 10))),
                List.of(baja("BT-1", t(20, 10))));
        assertEquals("BT-3", c.todas().get(0).lote());
    }

    @Test void sinFechaDeRecepcionDelEsqueletoOAntesQueElViejoNoSigue() {
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", null)),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "sin fecha de recepción");
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(1, 9)), lote("BT-2", d(20, 10))),
                List.of(new EventoNsys(IMEI, "BT-1", t(20, 10), true, TipoEvento.ESTADO, null, "Removed", null))).todas().isEmpty(),
                "baja del esqueleto");
        assertTrue(Correcciones.de(List.of(lote("BT-1", d(19, 10)), lote("BT-2", d(15, 10))),
                List.of(baja("BT-1", t(20, 10)))).todas().isEmpty(), "recibido antes que el lote dado de baja");
    }

    @Test void unLoteYaCorregidoNoVuelveASerElNuevoDeOtro() {
        // Los dos el mismo día: BT-1 sigue en BT-2; la baja de BT-2 no puede volver a BT-1 (sería un círculo).
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(20, 10)), lote("BT-2", d(20, 10))),
                List.of(baja("BT-1", t(20, 10)), baja("BT-2", LocalDateTime.of(2026, 10, 20, 15, 1))));
        assertEquals(1, c.todas().size());
    }

    @Test void paraRevisionJuntaLaCadenaEnSuUltimoLoteYNeutralizaLaCorreccion() {
        List<EventoNsys> eventos = List.of(
                new EventoNsys(IMEI, "BT-1", t(13, 10), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                baja("BT-1", t(20, 10)),
                new EventoNsys(IMEI, "BT-2", t(20, 10), false, TipoEvento.ENTRADA, null, "En revisión", ""),
                new EventoNsys(IMEI, "BT-2", t(20, 10), false, TipoEvento.REENTRADA, null, "BT-2", null));
        Correcciones c = Correcciones.de(List.of(lote("BT-1", d(23, 9)), lote("BT-2", d(20, 10))), eventos);
        List<EventoNsys> r = c.paraRevision(eventos);

        assertEquals(4, r.size());
        assertTrue(r.stream().allMatch(e -> e.batch().equals("BT-2")));
        assertEquals(TipoEvento.UBICACION, r.get(0).tipo());
        assertEquals(List.of(TipoEvento.ESTADO, TipoEvento.ESTADO, TipoEvento.ESTADO),
                r.subList(1, 4).stream().map(EventoNsys::tipo).toList());
        assertTrue(r.subList(1, 4).stream().allMatch(e -> e.de() == null && e.a() == null));
        assertTrue(c.sustituido(eventos.get(1)) && c.sustituido(eventos.get(2)) && c.sustituido(eventos.get(3)));
        assertFalse(c.sustituido(eventos.get(0)));
    }

    @Test void laUltimaCajaAnteriorYLaDescripcion() {
        MovilNsys viejo = new MovilNsys(IMEI, "BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "Removed", "", "Proveedor A",
                d(23, 9), null, null, "15", "15", "512GB", "Amarillo", "A", true, null, "Listos", EstadoMovil.BAJA,
                t(1, 1), t(1, 1));
        MovilNsys nuevo = new MovilNsys(IMEI, "BT-2", "Apple iPhone 15 Pro eSIM 256GB", "n/a", "InProcess",
                "Pedidos realizados", "Proveedor A", d(20, 10), null, null, null, null, null, null, null, true, null, null,
                EstadoMovil.DE_PEDIDO, t(1, 1), t(1, 1));
        Correcciones c = Correcciones.de(List.of(viejo, nuevo), List.of(baja("BT-1", t(20, 10))));

        assertEquals("Listos", c.ultimaCajaAnterior("BT-2"));
        assertNull(c.ultimaCajaAnterior("BT-1"));
        assertEquals("512GB Amarillo · A", c.descripcion("BT-1"));
        assertEquals("Apple iPhone 15 Pro eSIM 256GB · n/a", c.descripcion("BT-2"), "sin maestro, el Goods");
    }

    @Test void sinCorreccionesCadaLoteEsSuPropiaCadena() {
        assertEquals("BT-9", Correcciones.NINGUNA.primerLote("BT-9"));
        assertNull(Correcciones.NINGUNA.lote("BT-9"));
        assertNull(Correcciones.NINGUNA.ultimaCajaAnterior("BT-9"));
    }
}
```

- [ ] **Step 3: Comprobar que no compila**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest=CorreccionesTest 2>&1 | grep -aE "ERROR|cannot find symbol" | head -3
```

Expected: `cannot find symbol … Correcciones`.

- [ ] **Step 4: `Correcciones.java`**

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correcciones de NSYS (spec 0.9.21 «corrección en NSYS» §2). Para corregir un móvil (color, capacidad, producto o grado;
 * también tras una transformación del taller), NSYS da de baja su lote (Removed) y da de alta el IMEI en otro lote. El lote
 * L1 sigue en L2 cuando L1 pasa a Removed con hora el día D y L2, del mismo IMEI, se recibe entre D−{@value #DIAS_ANTES} y
 * D+{@value #DIAS_DESPUES}, no antes que L1; si hay varios, el más cercano a D (empate: el lote mayor). Un L2 sigue a un
 * solo L1 y las correcciones se encadenan: la cadena es un solo móvil. Es puro: se calcula al leer, con los lotes y los
 * eventos de un IMEI.
 */
public final class Correcciones {

    static final int DIAS_ANTES = 7;
    static final int DIAS_DESPUES = 1;
    private static final String REMOVED = "Removed";

    /** Sin lotes ni eventos: cada lote es su propia cadena. */
    public static final Correcciones NINGUNA = de(List.of(), List.of());

    /** Una corrección: el lote dado de baja, el que lo sigue y la hora de la baja. */
    public record Correccion(String loteAnterior, String lote, LocalDateTime fecha) {}

    private final Map<String, MovilNsys> lotes = new HashMap<>();
    private final List<Correccion> todas = new ArrayList<>();
    private final Map<String, Correccion> porLoteNuevo = new HashMap<>();
    private final Map<String, Correccion> porLoteViejo = new HashMap<>();

    private Correcciones() {}

    /** Las correcciones de un IMEI con sus lotes (en cualquier orden) y sus eventos (basta con las bajas). */
    public static Correcciones de(List<MovilNsys> lotes, List<EventoNsys> eventos) {
        Correcciones c = new Correcciones();
        for (MovilNsys m : lotes) c.lotes.putIfAbsent(m.batch(), m);
        // La última baja con hora de cada lote: un lote puede volver y darse de baja otra vez.
        Map<String, EventoNsys> bajas = new HashMap<>();
        for (EventoNsys e : eventos) if (esBaja(e) && c.lotes.containsKey(e.batch())) bajas.put(e.batch(), e);
        List<EventoNsys> porFecha = new ArrayList<>(bajas.values());
        porFecha.sort(Comparator.comparing(EventoNsys::fecha));
        for (EventoNsys b : porFecha) {
            MovilNsys viejo = c.lotes.get(b.batch());
            LocalDate dia = b.fecha().toLocalDate();
            MovilNsys elegido = null;
            for (MovilNsys m : c.lotes.values()) {
                if (!c.puedeSeguir(m, viejo, dia)) continue;
                if (elegido == null || masCercano(m, elegido, dia)) elegido = m;
            }
            if (elegido == null) continue;
            Correccion k = new Correccion(viejo.batch(), elegido.batch(), b.fecha());
            c.todas.add(k);
            c.porLoteNuevo.put(k.lote(), k);
            c.porLoteViejo.put(k.loteAnterior(), k);
        }
        return c;
    }

    /** ¿Puede {@code m} ser el lote que sigue a {@code viejo}, dado de baja el día {@code dia}? */
    private boolean puedeSeguir(MovilNsys m, MovilNsys viejo, LocalDate dia) {
        if (m.batch().equals(viejo.batch()) || porLoteNuevo.containsKey(m.batch()) || m.receivingDate() == null) return false;
        if (m.receivingDate().isBefore(dia.minusDays(DIAS_ANTES)) || m.receivingDate().isAfter(dia.plusDays(DIAS_DESPUES)))
            return false;
        if (viejo.receivingDate() != null && m.receivingDate().isBefore(viejo.receivingDate())) return false;
        // Que no cierre un círculo: m no puede ser un lote anterior de la cadena del viejo.
        return !primerosDe(viejo.batch()).contains(m.batch());
    }

    private List<String> primerosDe(String lote) {
        List<String> anteriores = new ArrayList<>();
        for (Correccion k = porLoteNuevo.get(lote); k != null; k = porLoteNuevo.get(k.loteAnterior()))
            anteriores.add(k.loteAnterior());
        return anteriores;
    }

    private static boolean masCercano(MovilNsys m, MovilNsys otro, LocalDate dia) {
        long a = Math.abs(ChronoUnit.DAYS.between(dia, m.receivingDate()));
        long b = Math.abs(ChronoUnit.DAYS.between(dia, otro.receivingDate()));
        return a != b ? a < b : m.batch().compareTo(otro.batch()) > 0;
    }

    /** Una baja con hora: un cambio de estado a Removed que no es del esqueleto. */
    static boolean esBaja(EventoNsys e) {
        return e.tipo() == TipoEvento.ESTADO && REMOVED.equals(e.a()) && !e.soloDia();
    }

    /** Las correcciones, por la hora de la baja. */
    public List<Correccion> todas() {
        return List.copyOf(todas);
    }

    /** El primer lote de la cadena de {@code lote}: el de la entrada de verdad. */
    public String primerLote(String lote) {
        String l = lote;
        for (Correccion k = porLoteNuevo.get(l); k != null; k = porLoteNuevo.get(l)) l = k.loteAnterior();
        return l;
    }

    /** El último lote de la cadena de {@code lote}: el que la representa. */
    public String ultimoLote(String lote) {
        String l = lote;
        for (Correccion k = porLoteViejo.get(l); k != null; k = porLoteViejo.get(l)) l = k.lote();
        return l;
    }

    /** El lote con ese nombre, o null. */
    public MovilNsys lote(String batch) {
        return lotes.get(batch);
    }

    /** La última caja de los lotes anteriores de la cadena (su última ubicación o su ubicación), del más cercano al más
     *  lejano; null si no hay. */
    public String ultimaCajaAnterior(String lote) {
        for (Correccion k = porLoteNuevo.get(lote); k != null; k = porLoteNuevo.get(k.loteAnterior())) {
            MovilNsys m = lotes.get(k.loteAnterior());
            if (m == null) continue;
            if (m.ubicacionUltima() != null) return m.ubicacionUltima();
            if (m.ubicacion() != null) return m.ubicacion();
        }
        return null;
    }

    /** ¿Lo sustituye una corrección? La baja del lote viejo y la entrada y la reentrada del nuevo. */
    public boolean sustituido(EventoNsys e) {
        if (esBaja(e)) {
            Correccion k = porLoteViejo.get(e.batch());
            return k != null && k.fecha().equals(e.fecha());
        }
        return (e.tipo() == TipoEvento.ENTRADA || e.tipo() == TipoEvento.REENTRADA) && porLoteNuevo.containsKey(e.batch());
    }

    /** Los eventos para {@link Revision}, con la misma longitud y orden: los de cada cadena con el nombre de su último
     *  lote y, en lugar de los que sustituye una corrección, un ESTADO vacío que Revision no mira. */
    public List<EventoNsys> paraRevision(List<EventoNsys> eventos) {
        List<EventoNsys> r = new ArrayList<>(eventos.size());
        for (EventoNsys e : eventos) {
            String lote = ultimoLote(e.batch());
            r.add(sustituido(e)
                    ? new EventoNsys(e.imeiSn(), lote, e.fecha(), e.soloDia(), TipoEvento.ESTADO, null, null, null)
                    : new EventoNsys(e.imeiSn(), lote, e.fecha(), e.soloDia(), e.tipo(), e.de(), e.a(), e.aNsys()));
        }
        return r;
    }

    /** El móvil de un lote para el rastro: capacidad y color del maestro con el grado de NSYS («512GB Negro · A100») o, si
     *  no los tiene, el Goods de NSYS y el grado. null si no hay ese lote. */
    public String descripcion(String batch) {
        MovilNsys m = lotes.get(batch);
        if (m == null) return null;
        String grado = m.grade() == null ? "—" : m.grade();
        if (m.capacidad() != null && m.color() != null) return m.capacidad() + " " + m.color() + " · " + grado;
        return (m.goods() == null ? "—" : m.goods()) + " · " + grado;
    }
}
```

En `MovilNsys.java`, detrás de `conFechas`:

```java
    /** El mismo lote con otra fecha de recepción (0.9.21: la del primer lote de su cadena de correcciones). */
    public MovilNsys conRecepcion(LocalDate recepcion) {
        return new MovilNsys(imeiSn, batch, goods, grade, status, location, supplier, recepcion, soldDate, sku, modelo,
                modeloMaestro, capacidad, color, gradoDetalle, esEsim, ubicacion, ubicacionUltima, estado, vistoPrimero,
                cambiado);
    }
```

- [ ] **Step 5: Comprobar que pasa**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest=CorreccionesTest 2>&1 | grep -aE "Tests run|FAIL|BUILD" | tail -3
```

Expected: 11 tests en verde.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c
git add src/main/java/com/reparaciones/servidor/nsys/Correcciones.java src/main/java/com/reparaciones/servidor/nsys/MovilNsys.java src/test/java/com/reparaciones/servidor/nsys/CorreccionesTest.java
git commit -m "feat: correcciones de NSYS, la baja de un lote y el alta del IMEI en otro son el mismo movil (0.9.21)"
```

---

### Task 2: El rastro con `CORRECCION`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/EventoRastro.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/Rastro.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java` (`rastro`)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/RastroTest.java`

**Interfaces:**
- Consumes: `Correcciones` (Task 1).
- Produces: `EventoRastro(…, String revision, String loteAnterior)` (13 campos); `Rastro.unir(List<MovilNsys>, List<EventoNsys>, List<TrabajoTaller>)`.

- [ ] **Step 1: Los tests que fallan**

En `RastroTest.java`: añadir `, null` al final de los cinco `new EventoRastro(...)` de `uneNsysYTallerDelMasRecienteAlMasAntiguo`
(el campo nuevo `loteAnterior`), los imports `java.time.LocalDate` y `static org.junit.jupiter.api.Assertions.assertNull`, y
detrás del último test:

```java
    // 0.9.21 (corrección en NSYS): la baja de un lote y el alta del IMEI en otro, en una sola línea.

    private static MovilNsys lote(String batch, String goods, String grade, String capacidad, String color, LocalDate recepcion) {
        return new MovilNsys("350000000000001", batch, goods, grade, "InProcess", "", "Proveedor A", recepcion, null, null,
                "15", "15", capacidad, color, grade, true, null, null, EstadoMovil.STOCK, recepcion.atStartOfDay(),
                recepcion.atStartOfDay());
    }

    private static EventoNsys nsys(String lote, LocalDateTime fecha, boolean soloDia, TipoEvento tipo, String de, String a, String aNsys) {
        return new EventoNsys("350000000000001", lote, fecha, soloDia, tipo, de, a, aNsys);
    }

    @Test void unaCorreccionSaleComoUnSoloEventoConLosDosLotes() {
        LocalDateTime baja = LocalDateTime.of(2026, 10, 20, 10, 1);
        List<MovilNsys> lotes = List.of(
                lote("BT-1333", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 9, 23)),
                lote("BT-1345", "Apple iPhone 15 eSIM 512GB Black", "A100", "512GB", "Negro", LocalDate.of(2026, 10, 20)));
        List<EventoRastro> rastro = Rastro.unir(lotes, List.of(
                nsys("BT-1333", LocalDateTime.of(2026, 9, 23, 0, 0), true, TipoEvento.ENTRADA, null, null, null),
                nsys("BT-1333", baja, false, TipoEvento.ESTADO, "InProcess", "Removed", null),
                nsys("BT-1345", baja, false, TipoEvento.ENTRADA, null, null, "Pedidos realizados"),
                nsys("BT-1345", baja, false, TipoEvento.REENTRADA, null, "BT-1345", null),
                nsys("BT-1345", LocalDateTime.of(2026, 10, 20, 14, 1), false, TipoEvento.VENTA, "InProcess", "Released", null)),
                List.of());

        assertEquals(List.of("VENTA", "CORRECCION", "ENTRADA"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(baja, false, "NSYS", "CORRECCION", "BT-1345", "512GB Amarillo · A", "512GB Negro · A100",
                null, null, null, null, null, "BT-1333"), rastro.get(1));
    }

    @Test void sinMaestroLaCorreccionLlevaElGoods() {
        LocalDateTime baja = LocalDateTime.of(2026, 7, 7, 10, 1);
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        lote("BT-1307", "Apple iPhone 15 Pro eSIM 256GB", "n/a", null, null, LocalDate.of(2026, 7, 6)),
                        lote("BT-1309", "Apple iPhone 15 Pro eSIM 256GB Blue Titanium", "B100", null, null, LocalDate.of(2026, 7, 7))),
                List.of(nsys("BT-1307", baja, false, TipoEvento.ESTADO, "InProcess", "Removed", null)), List.of());
        EventoRastro c = rastro.get(0);
        assertEquals("CORRECCION", c.tipo());
        assertEquals("Apple iPhone 15 Pro eSIM 256GB · n/a", c.de());
        assertEquals("Apple iPhone 15 Pro eSIM 256GB Blue Titanium · B100", c.a());
    }

    @Test void unaReentradaDeVerdadSigueSaliendo() {
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        lote("BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 8, 1)),
                        lote("BT-2", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 10, 15))),
                List.of(nsys("BT-1", LocalDateTime.of(2026, 9, 2, 10, 1), false, TipoEvento.ESTADO, "InProcess", "Removed", null),
                        nsys("BT-2", LocalDateTime.of(2026, 10, 15, 10, 1), false, TipoEvento.ENTRADA, null, "Listos", "CAJA LISTOS"),
                        nsys("BT-2", LocalDateTime.of(2026, 10, 15, 10, 1), false, TipoEvento.REENTRADA, null, "BT-2", null)),
                List.of());
        assertEquals(List.of("REENTRADA", "ENTRADA", "ESTADO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertNull(rastro.get(0).loteAnterior());
    }

    @Test void trasUnaCorreccionLaRevisionNoSeRepite() {
        LocalDateTime baja = LocalDateTime.of(2026, 10, 20, 10, 1);
        List<EventoRastro> rastro = Rastro.unir(List.of(
                        lote("BT-1", "Apple iPhone 15 eSIM 512GB Yellow", "A", "512GB", "Amarillo", LocalDate.of(2026, 9, 23)),
                        lote("BT-2", "Apple iPhone 15 eSIM 512GB Black", "A", "512GB", "Negro", LocalDate.of(2026, 10, 20))),
                List.of(nsys("BT-1", LocalDateTime.of(2026, 10, 13, 8, 46), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                        nsys("BT-1", LocalDateTime.of(2026, 10, 18, 9, 1), false, TipoEvento.UBICACION, "Listos", "En revisión", ""),
                        nsys("BT-1", baja, false, TipoEvento.ESTADO, "InProcess", "Removed", null),
                        nsys("BT-2", baja, false, TipoEvento.ENTRADA, null, "En revisión", ""),
                        nsys("BT-2", LocalDateTime.of(2026, 10, 21, 9, 1), false, TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS")),
                List.of());
        assertEquals(List.of("UBICACION", "CORRECCION", "UBICACION", "UBICACION"),
                rastro.stream().map(EventoRastro::tipo).toList());
        assertNull(rastro.get(0).revision(), "ya se revisó en BT-1: no vuelve a ser la revisión de entrada");
        assertEquals("ENTRADA", rastro.get(3).revision());
    }
```

- [ ] **Step 2: Comprobar que fallan**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest=RastroTest 2>&1 | grep -aE "ERROR|cannot find symbol|Tests run" | head -5
```

Expected: no compila (`EventoRastro` con 13 argumentos y `Rastro.unir` con tres).

- [ ] **Step 3: El código**

`EventoRastro.java`: añadir el campo al final y ampliar el comentario:

```java
/** Un evento del rastro tal como lo recibe la web: de NSYS (lote, de, a, aNsys) o del taller del ERP (idRep, técnico,
 *  categoría). {@code tipo}: los de {@link TipoEvento}, TRABAJO_ASIGNADO / TRABAJO_TERMINADO o CORRECCION (0.9.21: la baja
 *  de un lote y el alta del IMEI en otro; {@code lote} el nuevo, {@code loteAnterior} el viejo y {@code de}/{@code a} el
 *  móvil antes y después). {@code revision}: ENTRADA o DEVOLUCION en el cambio de caja que es la revisión del lote (0.9.18,
 *  {@link Revision}). La web compone los textos. */
public record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo,
                           @Schema(nullable = true) String lote, @Schema(nullable = true) String de,
                           @Schema(nullable = true) String a, @Schema(nullable = true) String aNsys,
                           @Schema(nullable = true) String idRep, @Schema(nullable = true) String tecnico,
                           @Schema(nullable = true) String categoria, @Schema(nullable = true) String revision,
                           @Schema(nullable = true) String loteAnterior) {}
```

`Rastro.java`: sustituir `unir` por las dos versiones y ampliar el comentario de la clase:

```java
/** Junta los eventos de NSYS y los trabajos del taller de un IMEI en una sola línea de tiempo, del más reciente al más
 *  antiguo (spec pieza 1 §8). La venta del esqueleto (solo el día) no sale si el mismo lote tiene una con hora. Con los
 *  lotes del IMEI, cada corrección de NSYS sale como un solo evento CORRECCION (0.9.21, {@link Correcciones}). */
public final class Rastro {

    private Rastro() {}

    /** Sin los lotes: no detecta correcciones. */
    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        return unir(List.of(), nsys, trabajos);
    }

    public static List<EventoRastro> unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        Correcciones correcciones = Correcciones.de(lotes, nsys);
        Set<String> lotesConVentaConHora = nsys.stream().filter(e -> e.tipo() == TipoEvento.VENTA && !e.soloDia())
                .map(EventoNsys::batch).collect(Collectors.toSet());
        // Sobre la lista entera, antes de quitar la venta del esqueleto: así cada tipo sigue con su evento. Con la cadena de
        // cada corrección como un solo lote (0.9.21).
        List<String> revisiones = Revision.tipos(correcciones.paraRevision(nsys));
        List<EventoRastro> todos = new ArrayList<>();
        for (int i = 0; i < nsys.size(); i++) {
            EventoNsys e = nsys.get(i);
            if (e.tipo() == TipoEvento.VENTA && e.soloDia() && lotesConVentaConHora.contains(e.batch())) continue;
            if (correcciones.sustituido(e)) continue;
            todos.add(new EventoRastro(e.fecha(), e.soloDia(), "NSYS", e.tipo().name(), e.batch(), e.de(), e.a(), e.aNsys(),
                    null, null, null, revisiones.get(i), null));
        }
        for (Correcciones.Correccion k : correcciones.todas())
            todos.add(new EventoRastro(k.fecha(), false, "NSYS", "CORRECCION", k.lote(), correcciones.descripcion(k.loteAnterior()),
                    correcciones.descripcion(k.lote()), null, null, null, null, null, k.loteAnterior()));
        for (TrabajoTaller t : trabajos) {
            boolean asignacion = t.idRep().startsWith("A");
            LocalDateTime fecha = asignacion || t.terminado() == null ? t.asignado() : t.terminado();
            if (fecha == null) continue;
            todos.add(new EventoRastro(fecha, false, "ERP", asignacion ? "TRABAJO_ASIGNADO" : "TRABAJO_TERMINADO",
                    null, null, null, null, t.idRep(), t.tecnico(), categoria(t.idRep()), null, null));
        }
        // (el orden, igual que antes)
```

(El resto del método, el orden por fecha y la vuelta, no cambia.)

`StockTelefonosController.rastro`:

```java
    @GetMapping("/{imeiSn}/rastro")
    public List<EventoRastro> rastro(@PathVariable String imeiSn) {
        return Rastro.unir(dao.porImei(imeiSn), dao.eventos(imeiSn), dao.trabajos(imeiSn));
    }
```

- [ ] **Step 4: Comprobar que pasan**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest='RastroTest,StockTelefonosControllerTest,RevisionTest' 2>&1 | grep -aE "Tests run|FAIL|BUILD" | tail -4
```

Expected: todo en verde.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c
git add src/main/java/com/reparaciones/servidor/nsys/EventoRastro.java src/main/java/com/reparaciones/servidor/nsys/Rastro.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/test/java/com/reparaciones/servidor/nsys/RastroTest.java
git commit -m "feat: el rastro junta la baja y el alta de una correccion de NSYS en un evento CORRECCION (0.9.21)"
```

---

### Task 3: La ficha y la tabla del stock con la cadena

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java` (`stock`, `ficha`)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java`,
  `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java`

**Interfaces:**
- Consumes: `Correcciones`, `MovilNsys.conRecepcion` (Task 1).
- Produces: `NsysDAO.lotesDeImeisConVarios(): List<MovilNsys>`, `NsysDAO.bajasConHora(): List<EventoNsys>`.

- [ ] **Step 1: Los tests que fallan**

En `NsysDAOTest.java`:

```java
    // ---------------------------------------------------------------- 0.9.21: corrección en NSYS

    @SuppressWarnings("unchecked")
    @Test void losLotesDeLosImeisConVariosYLasBajasConHoraSeLeenDeUnaVez() {
        dao.lotesDeImeisConVarios();
        verify(jdbc).query(and(contains("FROM Nsys_movil WHERE IMEI_SN IN"), contains("GROUP BY IMEI_SN HAVING COUNT(*) > 1")),
                any(RowMapper.class));
        dao.bajasConHora();
        verify(jdbc).query(and(contains("FROM Nsys_evento"), contains("TIPO = 'ESTADO' AND A = 'Removed' AND NOT SOLO_DIA")),
                any(RowMapper.class));
    }
```

En `StockTelefonosControllerTest.java`, detrás de `laFichaDeUnaBajaGuardadaConElFalloNoEstaPendienteDeRevisar()`:

```java
    // 0.9.21 (corrección en NSYS): BT-1333 (recibido el 23/09, revisado el 13/10 y de pedido desde Listos) se da de baja el
    // 15/10 y vuelve como BT-1345, de pedido y sin última caja.

    private static MovilNsys loteNuevoCorregido() {
        return new MovilNsys("350000000000001", "BT-1345", "Apple iPhone 15 eSIM 512GB Black", "A100", "InProcess",
                "Pedidos realizados", "Proveedor A", LocalDate.of(2026, 10, 15), null, "i15eSIM512NegroA100", "15", "15",
                "512GB", "Negro", "A100", true, null, null, EstadoMovil.DE_PEDIDO, AHORA.minusDays(1), AHORA.minusDays(1));
    }

    private static MovilNsys loteViejoCorregido() {
        return new MovilNsys("350000000000001", "BT-1333", "Apple iPhone 15 eSIM 512GB Yellow", "A", "Removed", "",
                "Proveedor A", LocalDate.of(2026, 9, 23), null, "i15eSIM512AmarilloA", "15", "15", "512GB", "Amarillo", "A",
                true, null, "Listos", EstadoMovil.BAJA, AHORA.minusDays(20), AHORA.minusDays(1));
    }

    private static EventoNsys eventoCorregido(String lote, LocalDateTime fecha, TipoEvento tipo, String de, String a, String aNsys) {
        return new EventoNsys("350000000000001", lote, fecha, false, tipo, de, a, aNsys);
    }

    private static final LocalDateTime BAJA = LocalDateTime.of(2026, 10, 15, 10, 1);

    @Test void laFichaDeUnLoteCorregidoCuentaDesdeElPrimerLoteConSuRevisionYSuCaja() {
        when(dao.porImei("350000000000001")).thenReturn(List.of(loteNuevoCorregido(), loteViejoCorregido()));
        when(dao.eventos("350000000000001")).thenReturn(List.of(
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 13, 8, 46), TipoEvento.UBICACION, "En revisión", "Listos", "CAJA LISTOS"),
                eventoCorregido("BT-1333", LocalDateTime.of(2026, 10, 14, 13, 31), TipoEvento.UBICACION, "Listos", null, "Pedidos realizados"),
                eventoCorregido("BT-1333", BAJA, TipoEvento.ESTADO, "InProcess", "Removed", null),
                eventoCorregido("BT-1345", BAJA, TipoEvento.ENTRADA, null, null, "Pedidos realizados"),
                eventoCorregido("BT-1345", BAJA, TipoEvento.REENTRADA, null, "BT-1345", null)));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertEquals("BT-1345", ficha.get("lote").asText());
        assertEquals(23, ficha.get("dias").asInt(), "del 23/09 (primer lote) al 16/10");
        assertEquals(1, ficha.get("entradas").asInt());
        assertTrue(ficha.get("revisadoEl").asText().startsWith("2026-10-13T08:46"));
        assertEquals("Listos", ficha.get("ubicacion").asText(), "la caja del lote de antes");
    }

    @Test void elStockCuentaLosDiasDesdeElPrimerLoteYLaCajaDelAnterior() {
        when(dao.enEmpresa()).thenReturn(List.of(loteNuevoCorregido()));
        when(dao.lotesDeImeisConVarios()).thenReturn(List.of(loteNuevoCorregido(), loteViejoCorregido()));
        when(dao.bajasConHora()).thenReturn(List.of(eventoCorregido("BT-1333", BAJA, TipoEvento.ESTADO, "InProcess", "Removed", null)));
        JsonNode stock = json(api.stock());
        assertEquals(23, stock.get(0).get("dias").asInt());
        assertEquals("Listos", stock.get(0).get("ubicacion").asText());
    }
```

- [ ] **Step 2: Comprobar que fallan**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest='StockTelefonosControllerTest,NsysDAOTest' 2>&1 | grep -aE "ERROR|cannot find symbol|Tests run" | head -5
```

Expected: no compila (`lotesDeImeisConVarios`, `bajasConHora`).

- [ ] **Step 3: El DAO**

En `NsysDAO.java`, detrás de `porImeis`, y con el mapeo de eventos sacado a un método:

```java
    static final String COLUMNAS_EVENTO = "IMEI_SN, BATCH, FECHA, SOLO_DIA, TIPO, DE, A, A_NSYS";

    /** De los IMEI/SN con más de un lote, todos sus lotes (0.9.21, corrección en NSYS): para las cadenas del stock, de una
     *  vez y sin leer IMEI a IMEI. */
    public List<MovilNsys> lotesDeImeisConVarios() {
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN IN"
                + " (SELECT IMEI_SN FROM Nsys_movil GROUP BY IMEI_SN HAVING COUNT(*) > 1)", NsysDAO::movil);
    }

    /** Las bajas con hora (ESTADO → Removed, no del esqueleto), por orden (0.9.21, corrección en NSYS). */
    public List<EventoNsys> bajasConHora() {
        return jdbc.query("SELECT " + COLUMNAS_EVENTO + " FROM Nsys_evento"
                + " WHERE TIPO = 'ESTADO' AND A = 'Removed' AND NOT SOLO_DIA ORDER BY FECHA, ID_EVENTO", NsysDAO::evento);
    }

    public List<EventoNsys> eventos(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS_EVENTO + " FROM Nsys_evento WHERE IMEI_SN = ? ORDER BY FECHA, ID_EVENTO",
                NsysDAO::evento, imeiSn);
    }

    static EventoNsys evento(ResultSet rs, int n) throws SQLException {
        return new EventoNsys(rs.getString("IMEI_SN"), rs.getString("BATCH"), rs.getTimestamp("FECHA").toLocalDateTime(),
                rs.getBoolean("SOLO_DIA"), TipoEvento.valueOf(rs.getString("TIPO")), rs.getString("DE"), rs.getString("A"),
                rs.getString("A_NSYS"));
    }
```

(`eventos` sustituye a la versión de ahora, con la misma consulta.)

- [ ] **Step 4: El controlador**

En `StockTelefonosController.java` (añadir `import java.util.ArrayList;` si falta):

`stock`:

```java
    /** Los móviles que están en la empresa (en stock, de pedido o que no cuentan), cada uno con sus trabajos abiertos del
     *  taller (0.9.18). Los días y la caja de un móvil de pedido cuentan con su cadena de correcciones de NSYS (0.9.21). La
     *  web filtra en local y calcula «Quién lo tiene». */
    @GetMapping
    public List<MovilStock> stock() {
        LocalDate hoy = LocalDate.now(clock);
        Map<String, List<TrabajoAbierto>> abiertos = dao.trabajosAbiertos();
        Map<String, Correcciones> correcciones = correccionesPorImei();
        return dao.enEmpresa().stream().map(m -> {
            Correcciones c = correcciones.getOrDefault(m.imeiSn(), Correcciones.NINGUNA);
            MovilNsys primero = Objects.requireNonNullElse(c.lote(c.primerLote(m.batch())), m);
            return new MovilStock(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                    Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), m.esEsim(), m.sku(),
                    ubicacionVisible(m, c), m.estado().name(), dias(primero, hoy),
                    abiertos.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()));
        }).toList();
    }

    /** Las cadenas de correcciones de los IMEI con más de un lote, de una vez (0.9.21). */
    private Map<String, Correcciones> correccionesPorImei() {
        Map<String, List<MovilNsys>> lotes = new HashMap<>();
        for (MovilNsys m : dao.lotesDeImeisConVarios()) lotes.computeIfAbsent(m.imeiSn(), k -> new ArrayList<>()).add(m);
        Map<String, List<EventoNsys>> bajas = new HashMap<>();
        for (EventoNsys e : dao.bajasConHora()) bajas.computeIfAbsent(e.imeiSn(), k -> new ArrayList<>()).add(e);
        Map<String, Correcciones> porImei = new HashMap<>();
        lotes.forEach((imei, l) -> porImei.put(imei, Correcciones.de(l, bajas.getOrDefault(imei, List.of()))));
        return porImei;
    }
```

`ficha`:

```java
    @GetMapping("/{imeiSn}")
    public FichaMovil ficha(@PathVariable String imeiSn) {
        List<MovilNsys> lotes = dao.porImei(imeiSn);
        if (lotes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "NSYS no conoce el IMEI " + imeiSn);
        MovilNsys m = lotes.get(0);
        List<EventoNsys> eventos = dao.eventos(imeiSn);
        // 0.9.21 (corrección en NSYS): la cadena de correcciones del lote cuenta como un solo móvil: días desde su primer
        // lote, sus entradas de verdad y la revisión, el grado anterior y la espera con los eventos de toda la cadena.
        Correcciones correcciones = Correcciones.de(lotes, eventos);
        List<EventoNsys> deLaCadena = correcciones.paraRevision(eventos);
        MovilNsys primero = Objects.requireNonNullElse(correcciones.lote(correcciones.primerLote(m.batch())), m);
        // El último cambio de grado de verdad del lote: un cambio de batería (A → A100) no cuenta, o taparía el grado
        // anterior real (C → A → A100 da C).
        String gradoAnterior = deLaCadena.stream()
                .filter(e -> e.tipo() == TipoEvento.GRADO && Objects.equals(e.batch(), m.batch()))
                .filter(e -> !Normalizacion.gradoSinBateria(e.de()).equals(Normalizacion.gradoSinBateria(e.a())))
                .reduce((a, b) -> b).map(e -> e.de() == null ? null : Normalizacion.gradoSinBateria(e.de())).orElse(null);
        boolean enEmpresa = m.estado() == EstadoMovil.STOCK || m.estado() == EstadoMovil.DE_PEDIDO
                || m.estado() == EstadoMovil.NO_CUENTA;
        // 0.9.18 (spec §5.2): la última revisión del lote y, si está en «En revisión», desde cuándo. Un vendido o una baja
        // no espera revisión (0.9.18.2).
        Revision.Ultima revisado = Revision.ultima(deLaCadena, m.batch());
        LocalDate recepcion = primero.receivingDate() != null ? primero.receivingDate() : m.receivingDate();
        return new FichaMovil(m.imeiSn(), m.batch(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                Normalizacion.gradoSinBateria(m.grade()), Normalizacion.bateriaNueva(m.grade()), gradoAnterior, m.sku(),
                ubicacionVisible(m, correcciones), m.estado().name(), enEmpresa ? dias(primero, LocalDate.now(clock)) : null,
                lotes.size() - correcciones.todas().size(),
                m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null,
                revisado == null ? null : revisado.fecha(), revisado != null && revisado.devolucion(),
                enEmpresa ? Revision.pendienteDesde(m.conRecepcion(recepcion), deLaCadena) : null);
    }
```

Y detrás de `ubicacionVisible(MovilNsys m)` (que se queda para el buscador y «Pegar varios IMEIs»):

```java
    /** Como {@link #ubicacionVisible(MovilNsys)} y, si un móvil de pedido no tiene última caja porque entró así tras una
     *  corrección de NSYS, la del lote anterior de su cadena (0.9.21). */
    private static String ubicacionVisible(MovilNsys m, Correcciones c) {
        if (m.estado() != EstadoMovil.DE_PEDIDO) return m.ubicacion();
        return m.ubicacionUltima() != null ? m.ubicacionUltima() : c.ultimaCajaAnterior(m.batch());
    }
```

- [ ] **Step 5: Comprobar que pasan y la suite**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest='StockTelefonosControllerTest,NsysDAOTest,RolesStockTelefonosTest' 2>&1 | grep -aE "Tests run|FAIL|BUILD" | tail -4
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn test 2>&1 | grep -aE "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$|FAIL|BUILD" | tail -3
```

Expected: todo en verde; la suite, la base de la Task 1 + 18 (11 + 4 + 3).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c
git add src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java
git commit -m "feat: la ficha y el stock cuentan dias, entradas, revision y caja con la cadena de correcciones de NSYS (0.9.21)"
```

---

### Task 4: La web — `CORRECCION` en el rastro, versión y novedades

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (regenerados)
- Modify: `gestion-reparaciones-web/src/modules/taller/test/fabrica.ts` (`eventoRastro`)
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/rastro.ts`, `rastro.test.ts`
- Modify: `gestion-reparaciones-web/package.json`, `package-lock.json`, `CHANGELOG.md`
- Create (raíz): `docs/novedades/NOVEDADES-v0.9.21.md`

**Interfaces:**
- Consumes: `EventoRastro.loteAnterior` y el tipo `CORRECCION` (Task 2).

- [ ] **Step 1: Worktree y rama de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch -q origin && git log --oneline -1 origin/main
git worktree add -b dev_v0921_correccion_nsys /c/Users/dev/Documents/wt/web-0921c origin/main
cd /c/Users/dev/Documents/wt/web-0921c && npm ci --silent && npm run check 2>&1 | tail -5
```

Expected: `origin/main` en la 0.9.20 o más reciente; `npm run check` en verde (apuntar el número de tests).

- [ ] **Step 2: El contrato**

```bash
cd /c/Users/dev/Documents/wt/servidor-0921c && mvn -q test -Dtest=OpenApiContractTest 2>&1 | tail -2
cp target/openapi.json /c/Users/dev/Documents/wt/web-0921c/api/openapi.json
cd /c/Users/dev/Documents/wt/web-0921c && npm run api:types:offline && git diff --stat
git diff src/shared/api/schema.d.ts
```

Expected: en `schema.d.ts`, solo `loteAnterior: string | null;` en `EventoRastro` (y lo mismo en `openapi.json`). Si sale
más, parar: la rama del servidor lleva algo que la web no espera.

En `src/modules/taller/test/fabrica.ts`, en `eventoRastro`, añadir `loteAnterior: null,` detrás de `revision: null,`.

- [ ] **Step 3: El test que falla**

En `rastro.test.ts`, dentro de `it('cada tipo de evento', …)`, detrás de la línea de `VENTA_DESHECHA`:

```ts
    expect(verEvento(eventoRastro({ tipo: 'CORRECCION', lote: 'BT-1345', loteAnterior: 'BT-1333', de: '512GB Amarillo · A', a: '512GB Negro · A100', aNsys: null })))
      .toMatchObject({ titulo: 'Corrección en NSYS (lote BT-1333 → BT-1345)', detalle: '512GB Amarillo · A → 512GB Negro · A100', color: 'bg-ambar' })
```

```bash
cd /c/Users/dev/Documents/wt/web-0921c && npx vitest run src/modules/taller/imeis/rastro.test.ts 2>&1 | tail -6
```

Expected: falla (`titulo: 'CORRECCION'` del `default`).

- [ ] **Step 4: El texto**

En `rastro.ts`, dentro de `verEvento`, detrás del caso `VENTA_DESHECHA`:

```ts
    case 'CORRECCION':
      // 0.9.21: NSYS da de baja el lote y da de alta el IMEI en otro para corregir el móvil (spec «corrección en NSYS» §3).
      return { fecha, titulo: `Corrección en NSYS (lote ${e.loteAnterior ?? '—'} → ${e.lote ?? '—'})`, detalle: `${e.de ?? '—'} → ${e.a ?? '—'}`, color: 'bg-ambar' }
```

```bash
cd /c/Users/dev/Documents/wt/web-0921c && npx vitest run src/modules/taller/imeis/rastro.test.ts 2>&1 | tail -4
```

Expected: en verde.

- [ ] **Step 5: Versión, CHANGELOG y la suite**

```bash
cd /c/Users/dev/Documents/wt/web-0921c && npm pkg set version=0.9.21 && sed -i '3s/"version": "0.9.20"/"version": "0.9.21"/; 9s/"version": "0.9.20"/"version": "0.9.21"/' package-lock.json && grep -n '"version": "0.9.2' package.json package-lock.json | head -3
```

Expected: `package.json:4` y `package-lock.json:3` y `:9` en `0.9.21`. (Si las líneas del lock no son la 3 y la 9, cambiar
a mano las dos entradas raíz: la de arriba y la de `packages[""]`.)

En `CHANGELOG.md`, encima de `## [0.9.20]`:

```markdown
## [0.9.21] - 2026-10-XX

- **Correcciones de NSYS** (supertécnico y administrador): cuando NSYS corrige un móvil dándolo de baja y dándolo de alta en otro lote (por ejemplo, tras cambiarle el chasis de color en el taller), el ERP lo trata como el mismo móvil.
  - En el rastro, una sola línea «Corrección en NSYS (lote … → …)» con el móvil antes y después, en vez de «Estado en NSYS: Removed» y «Vuelve a entrar».
  - En la ficha del IMEI y en la tabla de Stock de teléfonos, los días cuentan desde que entró el primer lote; la ficha cuenta solo las entradas de verdad, conserva la revisión y la espera de antes de la corrección y, si es de pedido, su última caja.
- Servidor y web. Sin migración.
```

```bash
cd /c/Users/dev/Documents/wt/web-0921c && npm run check 2>&1 | tail -5
```

Expected: lint, tipos y tests en verde (la base del Step 1 + 1 test cambiado).

- [ ] **Step 6: Commits (web y raíz)**

```bash
cd /c/Users/dev/Documents/wt/web-0921c
git add api/openapi.json src/shared/api/schema.d.ts src/modules/taller/test/fabrica.ts src/modules/taller/imeis/rastro.ts src/modules/taller/imeis/rastro.test.ts package.json package-lock.json CHANGELOG.md
git commit -m "feat: correccion en NSYS en el rastro y version 0.9.21 (pieza de correcciones)"
```

Raíz, `docs/novedades/NOVEDADES-v0.9.21.md`:

```markdown
# 🎉 Novedades — Versión 0.9.21

(La sesión del SKU del taller añade su apartado.)

---

## 👀 Supertécnicos y administrador

### 🔎 IMEIs y Stock de teléfonos

- **Correcciones de NSYS:** cuando en NSYS se corrige un móvil dándolo de baja y de alta en otro lote (por ejemplo, tras
  cambiarle el chasis de color en el taller), el ERP lo trata como el mismo móvil:
  - en el rastro sale una sola línea, **«Corrección en NSYS (lote … → …)»**, con el móvil antes y después;
  - la ficha y la tabla del stock cuentan los días desde que entró el primer lote, y la ficha cuenta solo las entradas de
    verdad y conserva la revisión, la espera y la última caja de antes de la corrección.
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git fetch -q && git status -sb | head -4
git add docs/novedades/NOVEDADES-v0.9.21.md docs/superpowers/specs/2026-10-21-v0921-correccion-nsys-design.md docs/superpowers/plans/2026-10-21-v0921-correccion-nsys.md
git commit -m "docs: novedades de la 0.9.21 con las correcciones de NSYS; spec aprobada y plan"
```

---

### Task 5: Revisión final, merge y aviso

- [ ] **Step 1: Revisión final** de las dos ramas (`git diff origin/main..dev_v0921_correccion_nsys` en cada repo) frente a
  la spec, con `superpowers:requesting-code-review`. Lo importante se arregla antes de seguir; lo menor, a `plan-futuro.md`.
- [ ] **Step 2: Merge y push (con el OK del usuario)**, primero el servidor y después la web, con el clon principal limpio
  y `main` igual a `origin/main` tras el `fetch` (si no, parar y preguntar):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q origin && git status -sb | head -2
git merge --no-ff dev_v0921_correccion_nsys -m "merge: correcciones de NSYS, baja y alta en otro lote es el mismo movil (0.9.21)"
mvn -q test 2>&1 | tail -2 && git push origin main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q origin && git status -sb | head -2
git merge --no-ff dev_v0921_correccion_nsys -m "merge: correccion en NSYS en el rastro (0.9.21)"
npm run check 2>&1 | tail -3 && git push origin main
```

- [ ] **Step 3: Gitlinks y aviso**
  - Raíz: `git add gestion-reparaciones-servidor gestion-reparaciones-web` y commit `chore: gitlinks de la 0.9.21, pieza
    de correcciones de NSYS` (con `fetch` y `status` antes: otras sesiones mueven el gitlink de la web); push con el OK.
  - **Aviso a `programareparaciones-f6`** con los dos commits del merge y la forma final de `EventoRastro` y
    `Rastro.unir`.
  - Worktrees y ramas: se borran tras el merge (`git worktree remove …`, `git branch -d dev_v0921_correccion_nsys`).

---

### Task 6: Preprod

**Files:** Create `C:/Users/dev/Documents/Apuntes/preprod/v0921-correccion-preprod.md`; modify `Apuntes/despliegue_preprod.md`.

- [ ] **Step 1: Guion** (preguntar antes si preprod está libre). Preprod tiene el servidor en el commit suelto `f27b170`
  (0.9.18.2) y la web en la 0.9.20:
  - **Bloque 1 (solo lee):** commits de los dos clones, `origin/main` tras el `fetch`, contenedores.
  - **Bloque 2 (escribe):** `git -C gestion-reparaciones-servidor checkout main && git -C gestion-reparaciones-servidor
    pull --ff-only`, lo mismo en la web, `docker compose build backend nginx && docker compose up -d` (avisar: ~1 min sin
    salida); `Started App` sin errores. Sin base de datos.
  - **Comprobaciones de Claude** (cuenta de pruebas): el rastro de `353177640742481` con un `CORRECCION` BT-1333 → BT-1345
    y sin «Removed» + «Vuelve a entrar»; el de `355407368461545` con dos (BT-1307 → BT-1309 → BT-1345); sus fichas (días
    desde el primer lote, 1 entrada); `/version.json` 0.9.21; E2E `stock-telefonos`; capturas en la carpeta temporal.
  - **Vuelta atrás:** `checkout f27b170` del servidor y el commit de la web del bloque 1, y el mismo build.
- [ ] **Step 2:** el usuario pega los bloques; registro en `despliegue_preprod.md`. Preprod se queda en `main`. Producción,
  con el resto de la 0.9.21 (plan del SKU del taller).
