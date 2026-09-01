# Estadísticas por puntos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir el conteo de reparaciones del gráfico de técnicos por un sistema de puntos de dificultad (tabla BD editable por admin), con métricas Puntos y Puntos/día, y rehacer la usabilidad de la vista (arranque limpio, buscador, flechas, popover de desglose, tarjetas resumen).

**Architecture:** Servidor Spring Boot (submódulo, rama desde `main`): tabla `Dificultad_puntos` + cálculo puro `PuntosCalculo` + endpoint `GET /api/reparaciones/estadisticas/puntos` + `GET/PUT /api/valores-dificultad`. Cliente JavaFX (repo raíz, rama desde `hotfix/0.16.1`): helper puro `PuntosEstadistica` + rework de `EstadisticasController`/FXML. Spec: `docs/superpowers/specs/2026-09-01-estadisticas-puntos-design.md`.

**Tech Stack:** Java 17, Spring Boot + JdbcTemplate (servidor), JavaFX + Gson (cliente), JUnit 5 + Mockito, MariaDB.

## Global Constraints

- Toolchain portable (cada shell Bash nuevo): `export JAVA_HOME="C:/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/jdk-17/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`
- Commits **sin** `Co-Authored-By` (preferencia del usuario). Mensajes en español, prefijos `feat(servidor):`, `feat(cliente):`, `docs(...)`.
- **NUNCA** merge, push, tag ni release sin OK explícito del usuario. Los commits locales en la rama feature sí son parte de cada tarea.
- Repo raíz contiene el cliente; `gestion-reparaciones-servidor` es submódulo. Nunca `git add -A` en la raíz (arrastraría el gitlink del submódulo); añadir archivos por ruta.
- Decimales en UI con **coma** ("14,5"). Puntos siempre con 1 decimal.
- Formatos de periodo (idénticos en servidor y cliente): día `2026-09-01`, semana `2026-W36`, mes `2026-09`, año `2026`. Granularidades servidor: `dia|semana|mes|ano`.
- Prefijos de `ID_REP`: `R`=reparación terminada, `G`=glass terminada, `P`=pulido terminado, `A/AG/AP`=abiertas (nunca puntúan).
- Claves de dificultad (8, fijas): `bateria, camara, chasis, marco, pantalla, glass, otro, pulido`. Mapeo SKU→clave en este orden de prefijos: `bat→bateria, cha→chasis, cam→camara, lcd→pantalla, mc→marco, g→glass`; sin match → `otro`.
- El endpoint viejo `GET /api/reparaciones/estadisticas` **no se toca**.

---

### Task 1: Rama servidor + migración SQL + crear_bd

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-estadisticas-puntos.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (añadir tabla tras `CREATE TABLE TipoCambio`, línea ~220)

**Interfaces:**
- Produces: tabla `Dificultad_puntos (CLAVE VARCHAR(20) PK, PUNTOS DECIMAL(4,2), UPDATED_AT)` con 8 filas seed — la leen Tasks 3-5.

- [ ] **Step 1: Crear la rama del servidor desde main**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor"
git checkout main && git pull && git checkout -b feature/estadisticas-puntos
```
Expected: rama nueva sobre `2f48c94` (o el main actual).

- [ ] **Step 2: Escribir la migración**

`gestion-reparaciones-servidor/sql/migracion-estadisticas-puntos.sql`:

```sql
-- Migración estadísticas por puntos (spec 2026-09-01-estadisticas-puntos-design).
-- Vista previa antes de aplicar:
--   SELECT COUNT(*) FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Dificultad_puntos';  -- esperado: 0

CREATE TABLE Dificultad_puntos (
    CLAVE      VARCHAR(20)  NOT NULL,
    PUNTOS     DECIMAL(4,2) NOT NULL,
    UPDATED_AT TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (CLAVE)
);

INSERT INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('bateria',  1.00),
    ('camara',   0.70),
    ('chasis',   2.00),
    ('marco',    0.50),
    ('pantalla', 1.00),
    ('glass',    0.50),
    ('otro',     0.50),
    ('pulido',   0.25);

-- Verificación post: SELECT CLAVE, PUNTOS FROM Dificultad_puntos ORDER BY CLAVE;  -- 8 filas
```

- [ ] **Step 3: Añadir la misma tabla a `crear_bd.sql`** (mismo `CREATE TABLE` + `INSERT`, colocado tras la tabla `TipoCambio`).

- [ ] **Step 4: Commit**

```bash
git add sql/migracion-estadisticas-puntos.sql sql/crear_bd.sql
git commit -m "feat(servidor): tabla Dificultad_puntos con seed de dificultades (migracion + crear_bd)"
```

---

### Task 2: `PuntosCalculo` — lógica pura de puntuación (servidor)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java`

**Interfaces:**
- Produces (usado por Task 4):
  - `record Pieza(String tipo, int cantidad)` — `tipo` puede ser `null` (fila sin componente).
  - `record FilaPuntos(String tecnico, java.time.LocalDate fecha, String idRep, String tipoPieza, Integer cantidad)`
  - `static String claveDeTipo(String tipo)`
  - `static double puntosDeReparacion(String idRep, java.util.List<Pieza> piezas, java.util.Map<String, Double> valores)`
  - `static java.util.List<PuntoEstadisticaPuntos> agregar(java.util.List<FilaPuntos> filas, java.util.Map<String, Double> valores, java.util.function.Function<java.time.LocalDate, String> periodoDe)` — agrupa por técnico+periodo y devuelve la lista ordenada por periodo, técnico.
- Consumes: modelo `PuntoEstadisticaPuntos` — se crea en esta task (ver Step 3) para que compile.

- [ ] **Step 1: Escribir los tests que fallan**

`PuntosCalculoTest.java`:

```java
package com.reparaciones.servidor.util;

import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PuntosCalculoTest {

    private static final Map<String, Double> VALORES = Map.of(
            "bateria", 1.00, "camara", 0.70, "chasis", 2.00, "marco", 0.50,
            "pantalla", 1.00, "glass", 0.50, "otro", 0.50, "pulido", 0.25);

    // ── claveDeTipo ───────────────────────────────────────────────────────────
    @Test void mapeaPrefijosSku() {
        assertEquals("bateria",  PuntosCalculo.claveDeTipo("bat14pro"));
        assertEquals("chasis",   PuntosCalculo.claveDeTipo("cha12"));
        assertEquals("camara",   PuntosCalculo.claveDeTipo("cam11"));
        assertEquals("pantalla", PuntosCalculo.claveDeTipo("lcdx"));
        assertEquals("marco",    PuntosCalculo.claveDeTipo("mc13mini"));
        assertEquals("glass",    PuntosCalculo.claveDeTipo("g15promax"));
    }

    @Test void tipoDesconocidoNuloOtroEsOtro() {
        assertEquals("otro", PuntosCalculo.claveDeTipo("otro tornilleria"));
        assertEquals("otro", PuntosCalculo.claveDeTipo("XYZ"));
        assertEquals("otro", PuntosCalculo.claveDeTipo(null));
    }

    @Test void mayusculasNoImportan() {
        assertEquals("glass", PuntosCalculo.claveDeTipo("G15PROMAX"));
    }

    // ── puntosDeReparacion ────────────────────────────────────────────────────
    @Test void sumaPiezasPorCantidad() {
        double p = PuntosCalculo.puntosDeReparacion("R20260901_1",
                List.of(new PuntosCalculo.Pieza("lcd14", 1), new PuntosCalculo.Pieza("bat14", 2)), VALORES);
        assertEquals(3.00, p, 0.001); // pantalla 1,00 + bateria 1,00 × 2
    }

    @Test void glassConPiezasSumaIgualQueNormal() {
        double p = PuntosCalculo.puntosDeReparacion("G20260901_1",
                List.of(new PuntosCalculo.Pieza("g14", 1), new PuntosCalculo.Pieza("mc14", 1)), VALORES);
        assertEquals(1.00, p, 0.001); // glass 0,50 + marco 0,50
    }

    @Test void sinPiezasPuntuaOtroFijo() {
        assertEquals(0.50, PuntosCalculo.puntosDeReparacion("R20260901_2", List.of(), VALORES), 0.001);
    }

    @Test void piezaSinTipoPuntuaOtro() {
        double p = PuntosCalculo.puntosDeReparacion("R20260901_3",
                List.of(new PuntosCalculo.Pieza(null, 1)), VALORES);
        assertEquals(0.50, p, 0.001);
    }

    @Test void pulidoEsFijoAunqueTuvieraPiezas() {
        assertEquals(0.25, PuntosCalculo.puntosDeReparacion("P20260901_1", List.of(), VALORES), 0.001);
        assertEquals(0.25, PuntosCalculo.puntosDeReparacion("P20260901_2",
                List.of(new PuntosCalculo.Pieza("bat14", 1)), VALORES), 0.001);
    }

    // ── agregar ───────────────────────────────────────────────────────────────
    @Test void agregaPorTecnicoYPeriodoConDesglose() {
        LocalDate d = LocalDate.of(2026, 9, 1);
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Marcos", d, "R20260901_1", "lcd14", 1),
                new PuntosCalculo.FilaPuntos("Marcos", d, "R20260901_1", "bat14", 1),
                new PuntosCalculo.FilaPuntos("Marcos", d, "R20260901_2", null,    null), // sin piezas
                new PuntosCalculo.FilaPuntos("Marcos", d, "G20260901_1", "g14",   1),
                new PuntosCalculo.FilaPuntos("Marcos", d, "P20260901_1", null,    null),
                new PuntosCalculo.FilaPuntos("Zara",   d, "R20260901_3", "cha12", 1));
        List<PuntoEstadisticaPuntos> out = PuntosCalculo.agregar(filas, VALORES, f -> "2026-09");

        assertEquals(2, out.size());
        PuntoEstadisticaPuntos marcos = out.stream()
                .filter(p -> p.getNombreTecnico().equals("Marcos")).findFirst().orElseThrow();
        assertEquals("2026-09", marcos.getPeriodo());
        assertEquals(3.25, marcos.getPuntos(), 0.001);         // 2,0 + 0,5 + 0,5 + 0,25
        assertEquals(2.50, marcos.getPuntosNormales(), 0.001); // R: 2,0 + 0,5
        assertEquals(0.50, marcos.getPuntosGlass(), 0.001);
        assertEquals(0.25, marcos.getPuntosPulidos(), 0.001);
        assertEquals(2, marcos.getnNormales());
        assertEquals(1, marcos.getnGlass());
        assertEquals(1, marcos.getnPulidos());
        assertEquals(1, marcos.getnSinPiezas());
        PuntoEstadisticaPuntos zara = out.stream()
                .filter(p -> p.getNombreTecnico().equals("Zara")).findFirst().orElseThrow();
        assertEquals(2.00, zara.getPuntos(), 0.001);
    }

    @Test void unaFilaSinPiezaNoDuplicaLaReparacion() {
        // La query trae UNA fila por reparación sin piezas (LEFT JOIN con NULL):
        // no debe contarse como pieza Y como reparación aparte.
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Marcos", LocalDate.of(2026, 9, 1), "R20260901_9", null, null));
        List<PuntoEstadisticaPuntos> out = PuntosCalculo.agregar(filas, VALORES, f -> "2026-09");
        assertEquals(0.50, out.get(0).getPuntos(), 0.001);
        assertEquals(1, out.get(0).getnSinPiezas());
    }

    @Test void valoresQueFaltanCaenAlValorPorDefecto() {
        // Tabla vacía o clave borrada a mano: nunca NullPointerException.
        double p = PuntosCalculo.puntosDeReparacion("R20260901_1",
                List.of(new PuntosCalculo.Pieza("bat14", 1)), Map.of());
        assertEquals(0.0, p, 0.001);
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor"
export JAVA_HOME="C:/Users/dev/tools/jdk-17" && export PATH="/c/Users/dev/tools/jdk-17/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q test -Dtest=PuntosCalculoTest
```
Expected: FAIL (compilación: `PuntosCalculo`/`PuntoEstadisticaPuntos` no existen).

- [ ] **Step 3: Crear el modelo `PuntoEstadisticaPuntos`**

`gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/PuntoEstadisticaPuntos.java`:

```java
package com.reparaciones.servidor.model;

/** Punto del gráfico de estadísticas por puntos de dificultad (spec 2026-09-01). */
public class PuntoEstadisticaPuntos {
    private String nombreTecnico;
    private String periodo;
    private double puntos;
    private double puntosNormales;
    private double puntosGlass;
    private double puntosPulidos;
    private int    nNormales;
    private int    nGlass;
    private int    nPulidos;
    private int    nSinPiezas;

    public PuntoEstadisticaPuntos() {}

    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas) {
        this.nombreTecnico = nombreTecnico;
        this.periodo = periodo;
        this.puntos = puntos;
        this.puntosNormales = puntosNormales;
        this.puntosGlass = puntosGlass;
        this.puntosPulidos = puntosPulidos;
        this.nNormales = nNormales;
        this.nGlass = nGlass;
        this.nPulidos = nPulidos;
        this.nSinPiezas = nSinPiezas;
    }

    public String getNombreTecnico()  { return nombreTecnico; }
    public String getPeriodo()        { return periodo; }
    public double getPuntos()         { return puntos; }
    public double getPuntosNormales() { return puntosNormales; }
    public double getPuntosGlass()    { return puntosGlass; }
    public double getPuntosPulidos()  { return puntosPulidos; }
    public int    getnNormales()      { return nNormales; }
    public int    getnGlass()         { return nGlass; }
    public int    getnPulidos()       { return nPulidos; }
    public int    getnSinPiezas()     { return nSinPiezas; }
}
```

- [ ] **Step 4: Implementar `PuntosCalculo`**

```java
package com.reparaciones.servidor.util;

import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Cálculo puro de puntos de dificultad (spec 2026-09-01-estadisticas-puntos-design §2).
 * R/G = suma de piezas × cantidad; sin piezas → 'otro'; P = 'pulido' fijo.
 */
public final class PuntosCalculo {

    private PuntosCalculo() {}

    /** Orden de matching de prefijos SKU → clave de Dificultad_puntos ('g' SIEMPRE el último). */
    private static final LinkedHashMap<String, String> PREFIJO_CLAVE = new LinkedHashMap<>();
    static {
        PREFIJO_CLAVE.put("bat", "bateria");
        PREFIJO_CLAVE.put("cha", "chasis");
        PREFIJO_CLAVE.put("cam", "camara");
        PREFIJO_CLAVE.put("lcd", "pantalla");
        PREFIJO_CLAVE.put("mc",  "marco");
        PREFIJO_CLAVE.put("g",   "glass");
    }

    public record Pieza(String tipo, int cantidad) {}

    /** Fila cruda de la query (una por pieza; reparación sin piezas = una fila con tipoPieza null). */
    public record FilaPuntos(String tecnico, LocalDate fecha, String idRep,
                             String tipoPieza, Integer cantidad) {}

    public static String claveDeTipo(String tipo) {
        if (tipo == null) return "otro";
        String lower = tipo.toLowerCase();
        for (var e : PREFIJO_CLAVE.entrySet())
            if (lower.startsWith(e.getKey())) return e.getValue();
        return "otro";
    }

    private static double valor(Map<String, Double> valores, String clave) {
        return valores.getOrDefault(clave, 0.0);
    }

    public static double puntosDeReparacion(String idRep, List<Pieza> piezas, Map<String, Double> valores) {
        if (idRep.startsWith("P")) return valor(valores, "pulido");
        if (piezas.isEmpty())      return valor(valores, "otro");
        return piezas.stream()
                .mapToDouble(p -> valor(valores, claveDeTipo(p.tipo())) * p.cantidad())
                .sum();
    }

    public static List<PuntoEstadisticaPuntos> agregar(List<FilaPuntos> filas,
                                                       Map<String, Double> valores,
                                                       Function<LocalDate, String> periodoDe) {
        // 1) agrupar filas por reparación (conservando técnico y fecha)
        record Rep(String tecnico, LocalDate fecha, String idRep) {}
        Map<Rep, List<Pieza>> piezasPorRep = new LinkedHashMap<>();
        for (FilaPuntos f : filas) {
            Rep rep = new Rep(f.tecnico(), f.fecha(), f.idRep());
            List<Pieza> lista = piezasPorRep.computeIfAbsent(rep, k -> new ArrayList<>());
            if (f.tipoPieza() != null || f.cantidad() != null)
                lista.add(new Pieza(f.tipoPieza(), f.cantidad() == null ? 1 : f.cantidad()));
        }

        // 2) acumular por técnico + periodo
        record Acum(double[] puntos, int[] contadores) {}  // puntos: total,N,G,P — contadores: nN,nG,nP,nSin
        Map<String, Map<String, Acum>> mapa = new LinkedHashMap<>();
        piezasPorRep.forEach((rep, piezas) -> {
            String periodo = periodoDe.apply(rep.fecha());
            Acum a = mapa.computeIfAbsent(rep.tecnico(), k -> new LinkedHashMap<>())
                         .computeIfAbsent(periodo, k -> new Acum(new double[4], new int[4]));
            double pts = puntosDeReparacion(rep.idRep(), piezas, valores);
            a.puntos()[0] += pts;
            char pref = rep.idRep().charAt(0);
            if (pref == 'R') { a.puntos()[1] += pts; a.contadores()[0]++; }
            if (pref == 'G') { a.puntos()[2] += pts; a.contadores()[1]++; }
            if (pref == 'P') { a.puntos()[3] += pts; a.contadores()[2]++; }
            if (pref != 'P' && piezas.isEmpty()) a.contadores()[3]++;
        });

        // 3) aplanar y ordenar como el endpoint viejo (periodo, técnico)
        List<PuntoEstadisticaPuntos> out = new ArrayList<>();
        mapa.forEach((tec, periodos) -> periodos.forEach((periodo, a) ->
                out.add(new PuntoEstadisticaPuntos(tec, periodo,
                        a.puntos()[0], a.puntos()[1], a.puntos()[2], a.puntos()[3],
                        a.contadores()[0], a.contadores()[1], a.contadores()[2], a.contadores()[3]))));
        out.sort(Comparator.comparing(PuntoEstadisticaPuntos::getPeriodo)
                           .thenComparing(PuntoEstadisticaPuntos::getNombreTecnico));
        return out;
    }
}
```

- [ ] **Step 5: Ejecutar los tests y verificar que pasan**

```bash
mvn -q test -Dtest=PuntosCalculoTest
```
Expected: PASS (11 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java \
        src/main/java/com/reparaciones/servidor/model/PuntoEstadisticaPuntos.java \
        src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java
git commit -m "feat(servidor): PuntosCalculo — clave por prefijo SKU, puntos por reparacion y agregado por tecnico/periodo"
```

---

### Task 3: `ValorDificultad` + `DificultadPuntosDAO` (servidor)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ValorDificultad.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/DificultadPuntosDAO.java`

**Interfaces:**
- Produces (usado por Tasks 4-5): `ValorDificultad{String clave; double puntos}` (POJO con getters/constructor vacío); `DificultadPuntosDAO.getValores() → Map<String,Double>`, `getAll() → List<ValorDificultad>`, `actualizar(String clave, double puntos) → int` (filas afectadas).

- [ ] **Step 1: Modelo `ValorDificultad`**

```java
package com.reparaciones.servidor.model;

public class ValorDificultad {
    private String clave;
    private double puntos;

    public ValorDificultad() {}
    public ValorDificultad(String clave, double puntos) { this.clave = clave; this.puntos = puntos; }

    public String getClave()  { return clave; }
    public double getPuntos() { return puntos; }
}
```

- [ ] **Step 2: DAO** (patrón `TipoCambioDAO`: `@Repository` + JdbcTemplate; glue fino sin test unitario propio, la lógica con miga vive en `PuntosCalculo` y el controller)

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.ValorDificultad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DificultadPuntosDAO {

    private final JdbcTemplate jdbc;

    public DificultadPuntosDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Double> getValores() {
        Map<String, Double> out = new HashMap<>();
        jdbc.query("SELECT CLAVE, PUNTOS FROM Dificultad_puntos",
                rs -> { out.put(rs.getString("CLAVE"), rs.getDouble("PUNTOS")); });
        return out;
    }

    public List<ValorDificultad> getAll() {
        return jdbc.query("SELECT CLAVE, PUNTOS FROM Dificultad_puntos ORDER BY CLAVE",
                (rs, row) -> new ValorDificultad(rs.getString("CLAVE"), rs.getDouble("PUNTOS")));
    }

    /** @return filas afectadas (0 si la clave no existe — el controller lo convierte en 422). */
    public int actualizar(String clave, double puntos) {
        return jdbc.update("UPDATE Dificultad_puntos SET PUNTOS = ? WHERE CLAVE = ?", puntos, clave);
    }
}
```

- [ ] **Step 3: Compilar** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/ValorDificultad.java \
        src/main/java/com/reparaciones/servidor/dao/DificultadPuntosDAO.java
git commit -m "feat(servidor): ValorDificultad y DificultadPuntosDAO (lectura de valores y update por clave)"
```

---

### Task 4: `ReparacionDAO.getEstadisticasPuntos` (servidor)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (junto a `getEstadisticasPorTecnico`, línea ~378)

**Interfaces:**
- Consumes: `PuntosCalculo.FilaPuntos`, `PuntosCalculo.agregar`, `formatearPeriodo`/`inicioPeriodo` privados ya existentes en `ReparacionDAO` (los usa `getEstadisticasPorTecnico`).
- Produces (usado por Task 5): `getEstadisticasPuntos(String granularidad, LocalDate desde, LocalDate hasta, Map<String,Double> valores) → List<PuntoEstadisticaPuntos>`.

- [ ] **Step 1: Añadir el método** (glue de query + delegación; sin test unitario propio — la agregación está testeada en Task 2 y el contrato HTTP en Task 5):

```java
    /**
     * Estadísticas por puntos de dificultad (spec 2026-09-01). Una fila por pieza usada
     * (ES_SOLICITUD = 0; reutilizadas e incidencias SÍ puntúan — pieza puesta es trabajo
     * hecho, criterio distinto al descuento de stock que excluye reutilizadas).
     * Reparación sin piezas llega como una única fila con TIPO/CANTIDAD null.
     */
    public List<PuntoEstadisticaPuntos> getEstadisticasPuntos(
            String granularidad, LocalDate desde, LocalDate hasta, Map<String, Double> valores) {
        List<PuntosCalculo.FilaPuntos> filas = jdbc.query(
                "SELECT t.NOMBRE, DATE(r.FECHA_FIN) AS FD, r.ID_REP, c.TIPO, rc.CANTIDAD" +
                " FROM Reparacion r" +
                " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " LEFT JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0" +
                " LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM" +
                " WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%' OR r.ID_REP LIKE 'P%')" +
                " AND r.FECHA_FIN IS NOT NULL AND DATE(r.FECHA_FIN) BETWEEN ? AND ?",
                (rs, row) -> new PuntosCalculo.FilaPuntos(
                        rs.getString("NOMBRE"),
                        rs.getDate("FD").toLocalDate(),
                        rs.getString("ID_REP"),
                        rs.getString("TIPO"),
                        (Integer) rs.getObject("CANTIDAD")),
                desde, hasta);
        return PuntosCalculo.agregar(filas, valores,
                fecha -> formatearPeriodo(inicioPeriodo(fecha, granularidad), granularidad));
    }
```

Imports nuevos en `ReparacionDAO`: `com.reparaciones.servidor.model.PuntoEstadisticaPuntos`, `com.reparaciones.servidor.util.PuntosCalculo`, `java.util.Map` (si falta).

- [ ] **Step 2: Compilar** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(servidor): getEstadisticasPuntos — query de piezas por reparacion terminada y agregado con PuntosCalculo"
```

---

### Task 5: Endpoints `GET /estadisticas/puntos` y `GET/PUT /valores-dificultad` (servidor)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (junto a `@GetMapping("/estadisticas")`, línea ~160)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/DificultadController.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/DificultadControllerTest.java`

**Interfaces:**
- Consumes: `DificultadPuntosDAO` (Task 3), `ReparacionDAO.getEstadisticasPuntos` (Task 4), `LogDAO.insertar(int idUsu, String accion, String detalle)`, `UsuarioPrincipal` (getIdUsu()).
- Produces: `GET /api/reparaciones/estadisticas/puntos?granularidad=&desde=&hasta=`; `GET /api/valores-dificultad`; `PUT /api/valores-dificultad` (body `[{clave, puntos}]`, `@PreAuthorize("hasRole('ADMIN')")`, log `EDITAR_PUNTOS`).

- [ ] **Step 1: Escribir el test del controller nuevo que falla**

`DificultadControllerTest.java` (patrón `ReparacionControllerEntregaGlassTest`):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.ValorDificultad;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DificultadControllerTest {

    private final DificultadPuntosDAO dao = mock(DificultadPuntosDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final DificultadController ctl = new DificultadController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void getDevuelveLaTabla() {
        when(dao.getAll()).thenReturn(List.of(new ValorDificultad("bateria", 1.0)));
        assertEquals(1, ctl.getValores().size());
    }

    @Test void putActualizaYLogueaSoloLosCambios() {
        when(dao.getValores()).thenReturn(Map.of("bateria", 1.0, "chasis", 2.0));
        when(dao.actualizar("chasis", 3.0)).thenReturn(1);
        ctl.actualizarValores(List.of(
                new ValorDificultad("bateria", 1.0),   // sin cambio → ni update ni log
                new ValorDificultad("chasis", 3.0)), admin);
        verify(dao, never()).actualizar("bateria", 1.0);
        verify(dao).actualizar("chasis", 3.0);
        verify(logDao).insertar(eq(1), eq("EDITAR_PUNTOS"), contains("chasis: 2,0 -> 3,0"));
    }

    @Test void putRechazaNegativosYNoEscribe() {
        when(dao.getValores()).thenReturn(Map.of("bateria", 1.0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarValores(List.of(new ValorDificultad("bateria", -0.5)), admin));
        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).actualizar(anyString(), anyDouble());
        verifyNoInteractions(logDao);
    }

    @Test void putRechazaClaveDesconocida() {
        when(dao.getValores()).thenReturn(Map.of("bateria", 1.0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarValores(List.of(new ValorDificultad("revision", 0.13)), admin));
        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).actualizar(anyString(), anyDouble());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla** — `mvn -q test -Dtest=DificultadControllerTest` → FAIL (no compila).

- [ ] **Step 3: Implementar `DificultadController`**

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.ValorDificultad;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Valores de dificultad de las estadísticas por puntos (spec 2026-09-01). */
@RestController
@RequestMapping("/api/valores-dificultad")
public class DificultadController {

    private final DificultadPuntosDAO dao;
    private final LogDAO logDao;

    public DificultadController(DificultadPuntosDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @GetMapping
    public List<ValorDificultad> getValores() {
        return dao.getAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public void actualizarValores(@RequestBody List<ValorDificultad> valores,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        Map<String, Double> actuales = dao.getValores();
        // Validación completa ANTES de escribir nada
        for (ValorDificultad v : valores) {
            if (v.getClave() == null || !actuales.containsKey(v.getClave()))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Clave desconocida: " + v.getClave());
            if (v.getPuntos() < 0 || v.getPuntos() > 99.99)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Puntos fuera de rango para " + v.getClave());
        }
        StringBuilder detalle = new StringBuilder();
        for (ValorDificultad v : valores) {
            double antes = actuales.get(v.getClave());
            if (Math.abs(antes - v.getPuntos()) < 0.001) continue;
            dao.actualizar(v.getClave(), v.getPuntos());
            if (detalle.length() > 0) detalle.append(", ");
            detalle.append(v.getClave()).append(": ")
                   .append(formatear(antes)).append(" -> ").append(formatear(v.getPuntos()));
        }
        if (detalle.length() > 0)
            logDao.insertar(principal.getIdUsu(), "EDITAR_PUNTOS", detalle.toString());
    }

    private static String formatear(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}
```

- [ ] **Step 4: Añadir el endpoint de estadísticas en `ReparacionController`** (debajo del `GET /estadisticas` actual; inyectar `DificultadPuntosDAO dificultadDao` como campo + parámetro de constructor nuevo — actualizar también la construcción en `ReparacionControllerEntregaGlassTest` con `mock(DificultadPuntosDAO.class)`):

```java
    @GetMapping("/estadisticas/puntos")
    public List<PuntoEstadisticaPuntos> getEstadisticasPuntos(
            @RequestParam String granularidad,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dao.getEstadisticasPuntos(granularidad, desde, hasta, dificultadDao.getValores());
    }
```

(Copiar las anotaciones `@RequestParam`/`@DateTimeFormat` exactas del `GET /estadisticas` actual, línea ~160.)

- [ ] **Step 5: Ejecutar tests y suite completa**

```bash
mvn -q test -Dtest=DificultadControllerTest   # PASS (4 tests)
mvn -q clean test                              # PASS — suite completa verde (~95+ tests)
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/DificultadController.java \
        src/main/java/com/reparaciones/servidor/controller/ReparacionController.java \
        src/test/java/com/reparaciones/servidor/controller/DificultadControllerTest.java \
        src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java
git commit -m "feat(servidor): GET /estadisticas/puntos y GET/PUT /valores-dificultad (PUT solo ADMIN, log EDITAR_PUNTOS)"
```

---

### Task 6: Rama cliente + modelos y DAOs HTTP (cliente)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/PuntoEstadisticaPuntos.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ValorDificultad.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ValoresDificultadDAO.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java` (junto al método `getEstadisticasPorTecnico` existente)

**Interfaces:**
- Produces (usado por Tasks 7-12): cliente `PuntoEstadisticaPuntos` — **mismos nombres de campo que el servidor** (Gson mapea por nombre): `nombreTecnico, periodo, puntos, puntosNormales, puntosGlass, puntosPulidos, nNormales, nGlass, nPulidos, nSinPiezas`, con getters idénticos a los del modelo servidor (Task 2 Step 3) y un constructor completo con ese mismo orden de parámetros (para tests). `ValorDificultad{clave, puntos}` con getters y constructor `(String, double)`. `ReparacionDAO.getEstadisticasPuntos(String granularidad, LocalDate desde, LocalDate hasta) → List<PuntoEstadisticaPuntos>` (throws SQLException). `ValoresDificultadDAO.getAll() → List<ValorDificultad>`, `guardar(List<ValorDificultad>) → void`.

- [ ] **Step 1: Crear la rama raíz desde la hotfix**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones"
git checkout hotfix/0.16.1 && git checkout -b feature/estadisticas-puntos
```

- [ ] **Step 2: Modelos cliente** — copiar `PuntoEstadisticaPuntos` del servidor (Task 2 Step 3) cambiando el package a `com.reparaciones.models` y añadiendo javadoc breve; `ValorDificultad` igual que el del servidor con package `com.reparaciones.models`.

- [ ] **Step 3: Métodos DAO**

En `ReparacionDAO` cliente (junto a `getEstadisticasPorTecnico`, copiando su construcción de URL con granularidad y fechas ISO):

```java
    /** Estadísticas por puntos de dificultad (spec 2026-09-01). */
    public List<PuntoEstadisticaPuntos> getEstadisticasPuntos(
            String granularidad, java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        return ApiClient.getList("/api/reparaciones/estadisticas/puntos?granularidad=" + granularidad
                + "&desde=" + desde + "&hasta=" + hasta, PuntoEstadisticaPuntos.class);
    }
```

(Si el método viejo construye la URL distinto — p. ej. otro nombre de parámetro — copiar SU formato exacto y solo cambiar el path a `/estadisticas/puntos` y el tipo devuelto.)

`ValoresDificultadDAO.java`:

```java
package com.reparaciones.dao;

import com.reparaciones.models.ValorDificultad;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;

/** DAO de la tabla {@code Dificultad_puntos} vía API REST (spec 2026-09-01). */
public class ValoresDificultadDAO {

    public List<ValorDificultad> getAll() throws SQLException {
        return ApiClient.getList("/api/valores-dificultad", ValorDificultad.class);
    }

    /** Guarda la tabla completa; el servidor solo escribe y loguea las claves que cambian. */
    public void guardar(List<ValorDificultad> valores) throws SQLException {
        ApiClient.put("/api/valores-dificultad", valores);
    }
}
```

- [ ] **Step 4: Compilar** — `cd gestion-reparaciones-cliente && mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones"
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/PuntoEstadisticaPuntos.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ValorDificultad.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ValoresDificultadDAO.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java
git commit -m "feat(cliente): modelos PuntoEstadisticaPuntos/ValorDificultad y DAOs de puntos y valores de dificultad"
```

---

### Task 7: `PuntosEstadistica` — helper puro del cliente

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java`

**Interfaces:**
- Consumes: `com.reparaciones.models.PuntoEstadisticaPuntos` (Task 6).
- Produces (usado por Tasks 8-12), todo `static` en `com.reparaciones.utils.PuntosEstadistica`:
  - `LocalDate[] periodoAFechas(String periodo, String granularidad)` — granularidad UI: `"Día"|"Semana"|"Mes"|"Año"`.
  - `int diasLaborables(LocalDate desde, LocalDate hasta)` — L–V inclusive; rango invertido → 0.
  - `double puntosDia(double puntos, String periodo, String granularidad, LocalDate hoy)` — divisor `max(1, diasLaborables(inicio, min(fin, hoy)))`.
  - `double promedioVentana(Map<String, Map<String, Double>> valorPorTecnicoYPeriodo, List<String> periodosVisibles)` — media por técnico-periodo; solo técnicos con algún valor > 0 en la ventana; sin actividad → 0.
  - `Optional<Double> parsePuntos(String texto)` — coma o punto, `0 ≤ v ≤ 99,99`; inválido → empty.
  - `String formatearPuntos(double v)` — 1 decimal con coma ("14,5"; "0,0").
  - `String textoTooltip(String periodo, double valor, boolean porDia, int trabajos)`
  - `String textoPopover(PuntoEstadisticaPuntos p)` — desglose multilínea.
  - `record Tarjetas(String mesLabel, double puntos, double puntosDia, Double deltaPuntosPct, Double deltaPuntosDiaPct)`
  - `Tarjetas calcularTarjetas(List<PuntoEstadisticaPuntos> filasMensuales, YearMonth mesActual, LocalDate hoy, String tecnicoONull)` — filas con granularidad mes de mesAnterior+mesActual; `tecnicoONull != null` filtra por ese técnico; delta `null` si el mes anterior no tiene datos.

- [ ] **Step 1: Escribir los tests que fallan**

```java
package com.reparaciones.utils;

import com.reparaciones.models.PuntoEstadisticaPuntos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PuntosEstadisticaTest {

    // ── periodoAFechas ────────────────────────────────────────────────────────
    @Test void periodoAFechasPorGranularidad() {
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)},
                PuntosEstadistica.periodoAFechas("2026-09-01", "Día"));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)},
                PuntosEstadistica.periodoAFechas("2026-W36", "Semana"));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)},
                PuntosEstadistica.periodoAFechas("2026-09", "Mes"));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)},
                PuntosEstadistica.periodoAFechas("2026", "Año"));
    }

    // ── diasLaborables ────────────────────────────────────────────────────────
    @Test void cuentaLunesAViernes() {
        // Semana 2026-W36: lunes 31/08 → domingo 06/09 → 5 laborables
        assertEquals(5, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)));
        // Septiembre 2026 completo: 22 laborables
        assertEquals(22, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        // Sábado a domingo → 0; rango invertido → 0
        assertEquals(0, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6)));
        assertEquals(0, PuntosEstadistica.diasLaborables(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 1)));
    }

    // ── puntosDia ─────────────────────────────────────────────────────────────
    @Test void divideEntreLaborablesDelPeriodo() {
        assertEquals(9.6, PuntosEstadistica.puntosDia(48, "2026-W36", "Semana", LocalDate.of(2026, 12, 1)), 0.001);
    }

    @Test void periodoEnCursoDivideSoloPorDiasTranscurridos() {
        // hoy = martes 01/09 dentro de la semana W36 → 2 laborables transcurridos (lun+mar)
        assertEquals(5.0, PuntosEstadistica.puntosDia(10, "2026-W36", "Semana", LocalDate.of(2026, 9, 1)), 0.001);
    }

    @Test void diaNoLaborableDivisorUno() {
        // sábado con trabajo: divisor max(1, 0) = 1
        assertEquals(3.0, PuntosEstadistica.puntosDia(3, "2026-09-05", "Día", LocalDate.of(2026, 12, 1)), 0.001);
    }

    // ── promedioVentana ───────────────────────────────────────────────────────
    @Test void promedioSoloTecnicosConActividadYPeriodosVisibles() {
        Map<String, Map<String, Double>> datos = Map.of(
                "Marcos", Map.of("2026-W35", 10.0, "2026-W36", 20.0),
                "Zara",   Map.of("2026-W36", 6.0),
                "Luis",   Map.of());  // sin actividad → no cuenta
        // 2 técnicos activos × 2 periodos; huecos = 0 → (10+20+0+6)/4 = 9,0
        assertEquals(9.0, PuntosEstadistica.promedioVentana(datos, List.of("2026-W35", "2026-W36")), 0.001);
    }

    @Test void promedioSinActividadEsCero() {
        assertEquals(0.0, PuntosEstadistica.promedioVentana(Map.of(), List.of("2026-W36")), 0.001);
    }

    // ── parsePuntos / formatearPuntos ─────────────────────────────────────────
    @Test void parseAceptaComaYPunto() {
        assertEquals(1.5, PuntosEstadistica.parsePuntos("1,5").orElseThrow(), 0.001);
        assertEquals(1.5, PuntosEstadistica.parsePuntos("1.5").orElseThrow(), 0.001);
        assertEquals(0.0, PuntosEstadistica.parsePuntos("0").orElseThrow(), 0.001);
    }

    @Test void parseRechazaInvalidos() {
        assertTrue(PuntosEstadistica.parsePuntos("-1").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos("abc").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos("100").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos("").isEmpty());
        assertTrue(PuntosEstadistica.parsePuntos(null).isEmpty());
    }

    @Test void formateaConComaYUnDecimal() {
        assertEquals("14,5", PuntosEstadistica.formatearPuntos(14.5));
        assertEquals("0,0",  PuntosEstadistica.formatearPuntos(0));
        assertEquals("3,3",  PuntosEstadistica.formatearPuntos(3.25)); // HALF_UP
    }

    // ── textos ────────────────────────────────────────────────────────────────
    @Test void tooltipConPuntosYTrabajos() {
        assertEquals("2026-W36\n14,5 puntos · 18 trabajos",
                PuntosEstadistica.textoTooltip("2026-W36", 14.5, false, 18));
        assertEquals("2026-W36\n2,9 puntos/día",
                PuntosEstadistica.textoTooltip("2026-W36", 2.9, true, 18));
    }

    @Test void popoverConDesglose() {
        PuntoEstadisticaPuntos p = new PuntoEstadisticaPuntos("Marcos", "2026-W36",
                14.5, 11.0, 2.0, 1.5, 9, 3, 6, 2);
        String texto = PuntosEstadistica.textoPopover(p);
        assertEquals("14,5 puntos\n9 normales (11,0) · 3 glass (2,0) · 6 pulidos (1,5)\n2 sin piezas (0,5 c/u)",
                texto);
    }

    @Test void popoverOmiteCategoriasVacias() {
        PuntoEstadisticaPuntos p = new PuntoEstadisticaPuntos("Zara", "2026-W36",
                2.0, 2.0, 0, 0, 1, 0, 0, 0);
        assertEquals("2,0 puntos\n1 normales (2,0)", PuntosEstadistica.textoPopover(p));
    }

    // ── tarjetas ──────────────────────────────────────────────────────────────
    @Test void tarjetasDelEquipoConDelta() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-08", 100), fila("Zara", "2026-08", 100),
                fila("Marcos", "2026-09", 50),  fila("Zara", "2026-09", 60));
        // hoy = 15/09/2026 → 11 laborables transcurridos en septiembre
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null);
        assertEquals("septiembre", t.mesLabel());
        assertEquals(110.0, t.puntos(), 0.001);
        assertEquals(10.0, t.puntosDia(), 0.001);            // 110 / 11
        assertEquals(-45.0, t.deltaPuntosPct(), 0.001);      // (110-200)/200
        // agosto 2026: 21 laborables → 200/21 = 9,52; (10 − 9,52)/9,52 = +5,0%
        assertEquals(5.0, t.deltaPuntosDiaPct(), 0.1);
    }

    @Test void tarjetasDeUnTecnicoYSinMesAnterior() {
        List<PuntoEstadisticaPuntos> filas = List.of(
                fila("Marcos", "2026-09", 50), fila("Zara", "2026-09", 60));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), "Marcos");
        assertEquals(50.0, t.puntos(), 0.001);
        assertNull(t.deltaPuntosPct());
        assertNull(t.deltaPuntosDiaPct());
    }

    private static PuntoEstadisticaPuntos fila(String tec, String periodo, double puntos) {
        return new PuntoEstadisticaPuntos(tec, periodo, puntos, puntos, 0, 0, 1, 0, 0, 0);
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla** — `cd gestion-reparaciones-cliente && mvn -q test -Dtest=PuntosEstadisticaTest` → FAIL (no compila).

- [ ] **Step 3: Implementar `PuntosEstadistica`**

```java
package com.reparaciones.utils;

import com.reparaciones.models.PuntoEstadisticaPuntos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lógica pura de la vista de estadísticas por puntos (spec 2026-09-01):
 * fechas de periodo, días laborables, puntos/día, promedio de ventana,
 * validación del modal de valores y textos de tooltip/popover/tarjetas.
 */
public final class PuntosEstadistica {

    private PuntosEstadistica() {}

    /** Convierte un periodo del servidor al rango [inicio, fin] según la granularidad de la UI. */
    public static LocalDate[] periodoAFechas(String periodo, String granularidad) {
        return switch (granularidad) {
            case "Día" -> {
                LocalDate d = LocalDate.parse(periodo);
                yield new LocalDate[]{d, d};
            }
            case "Mes" -> {
                YearMonth ym = YearMonth.parse(periodo);
                yield new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
            case "Año" -> {
                int year = Integer.parseInt(periodo);
                yield new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
            }
            default -> { // Semana: "2026-W36" → lunes-domingo ISO
                LocalDate lunes = LocalDate.parse(periodo + "-1", DateTimeFormatter.ISO_WEEK_DATE);
                yield new LocalDate[]{lunes, lunes.plusDays(6)};
            }
        };
    }

    /** Días laborables (L-V) del rango inclusive; rango invertido → 0. Festivos no descontados. */
    public static int diasLaborables(LocalDate desde, LocalDate hasta) {
        int n = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1))
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) n++;
        return Math.max(n, 0);
    }

    /** Puntos ÷ laborables del periodo; el periodo en curso corta en hoy; divisor mínimo 1. */
    public static double puntosDia(double puntos, String periodo, String granularidad, LocalDate hoy) {
        LocalDate[] rango = periodoAFechas(periodo, granularidad);
        LocalDate fin = rango[1].isAfter(hoy) ? hoy : rango[1];
        return puntos / Math.max(1, diasLaborables(rango[0], fin));
    }

    /**
     * Media por técnico-periodo de la ventana: suma de valores de los técnicos con
     * actividad (huecos = 0) ÷ (técnicos con actividad × periodos visibles). Sin actividad → 0.
     */
    public static double promedioVentana(Map<String, Map<String, Double>> valorPorTecnicoYPeriodo,
                                          List<String> periodosVisibles) {
        double suma = 0;
        int tecnicosConActividad = 0;
        for (Map<String, Double> porPeriodo : valorPorTecnicoYPeriodo.values()) {
            double sumaTec = 0;
            for (String p : periodosVisibles) sumaTec += porPeriodo.getOrDefault(p, 0.0);
            if (sumaTec > 0) { suma += sumaTec; tecnicosConActividad++; }
        }
        if (tecnicosConActividad == 0 || periodosVisibles.isEmpty()) return 0;
        return suma / (tecnicosConActividad * periodosVisibles.size());
    }

    /** Valida el texto del modal de valores: coma o punto, 0 ≤ v ≤ 99,99. */
    public static Optional<Double> parsePuntos(String texto) {
        if (texto == null || texto.isBlank()) return Optional.empty();
        try {
            double v = Double.parseDouble(texto.trim().replace(',', '.'));
            return (v < 0 || v > 99.99) ? Optional.empty() : Optional.of(v);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** "14,5" — un decimal, coma, HALF_UP. */
    public static String formatearPuntos(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    public static String textoTooltip(String periodo, double valor, boolean porDia, int trabajos) {
        return porDia
                ? periodo + "\n" + formatearPuntos(valor) + " puntos/día"
                : periodo + "\n" + formatearPuntos(valor) + " puntos · " + trabajos + " trabajos";
    }

    /** Desglose del popover; omite categorías a cero. */
    public static String textoPopover(PuntoEstadisticaPuntos p) {
        StringBuilder sb = new StringBuilder(formatearPuntos(p.getPuntos())).append(" puntos\n");
        StringBuilder linea = new StringBuilder();
        if (p.getnNormales() > 0)
            linea.append(p.getnNormales()).append(" normales (")
                 .append(formatearPuntos(p.getPuntosNormales())).append(")");
        if (p.getnGlass() > 0) {
            if (linea.length() > 0) linea.append(" · ");
            linea.append(p.getnGlass()).append(" glass (")
                 .append(formatearPuntos(p.getPuntosGlass())).append(")");
        }
        if (p.getnPulidos() > 0) {
            if (linea.length() > 0) linea.append(" · ");
            linea.append(p.getnPulidos()).append(" pulidos (")
                 .append(formatearPuntos(p.getPuntosPulidos())).append(")");
        }
        sb.append(linea);
        if (p.getnSinPiezas() > 0)
            sb.append("\n").append(p.getnSinPiezas()).append(" sin piezas (0,5 c/u)");
        return sb.toString();
    }

    // ── Tarjetas resumen ──────────────────────────────────────────────────────

    public record Tarjetas(String mesLabel, double puntos, double puntosDia,
                           Double deltaPuntosPct, Double deltaPuntosDiaPct) {}

    /**
     * @param filasMensuales resultado del endpoint con granularidad mes cubriendo mes anterior y actual
     * @param tecnicoONull   null = equipo entero; nombre = solo ese técnico
     */
    public static Tarjetas calcularTarjetas(List<PuntoEstadisticaPuntos> filasMensuales,
                                            YearMonth mesActual, LocalDate hoy, String tecnicoONull) {
        YearMonth anterior = mesActual.minusMonths(1);
        String pActual = mesActual.toString();     // "2026-09"
        String pAnterior = anterior.toString();

        double puntosActual = 0, puntosAnterior = 0;
        boolean hayAnterior = false;
        for (PuntoEstadisticaPuntos f : filasMensuales) {
            if (tecnicoONull != null && !tecnicoONull.equals(f.getNombreTecnico())) continue;
            if (pActual.equals(f.getPeriodo()))   puntosActual   += f.getPuntos();
            if (pAnterior.equals(f.getPeriodo())) { puntosAnterior += f.getPuntos(); hayAnterior = true; }
        }

        double diaActual = puntosActual
                / Math.max(1, diasLaborables(mesActual.atDay(1),
                        mesActual.atEndOfMonth().isAfter(hoy) ? hoy : mesActual.atEndOfMonth()));
        Double deltaPuntos = null, deltaDia = null;
        if (hayAnterior && puntosAnterior > 0) {
            double diaAnterior = puntosAnterior
                    / Math.max(1, diasLaborables(anterior.atDay(1), anterior.atEndOfMonth()));
            deltaPuntos = (puntosActual - puntosAnterior) / puntosAnterior * 100;
            deltaDia    = (diaActual - diaAnterior) / diaAnterior * 100;
        }
        String mesLabel = mesActual.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        return new Tarjetas(mesLabel, puntosActual, diaActual, deltaPuntos, deltaDia);
    }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasan** — `mvn -q test -Dtest=PuntosEstadisticaTest` → PASS (15 tests).

- [ ] **Step 5: Commit**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones"
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java
git commit -m "feat(cliente): PuntosEstadistica — laborables, puntos/dia, promedio de ventana, parse/formato y textos, puro y testeado"
```

---

### Task 8: Vista base — FXML + render por puntos, Equipo, Promedio, flechas, métrica

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`

**Interfaces:**
- Consumes: `ReparacionDAO.getEstadisticasPuntos` (Task 6), `PuntosEstadistica.{puntosDia, promedioVentana, formatearPuntos, textoTooltip}` (Task 7).
- Produces (que Tasks 9-12 asumen): campos `List<PuntoEstadisticaPuntos> todosPuntos`, `boolean metricaPorDia()`, método `double valorDe(PuntoEstadisticaPuntos p)` (aplica métrica), `renderVentana(int offset)` mantenido, `recargarDatos()` mantenido, campo `int ventanaOffset`.

Esta task es la más grande: transforma el gráfico sin tocar aún selección de técnicos (Task 9), tarjetas (Task 10), popover (Task 11) ni modal (Task 12). Sin test unitario nuevo (la lógica está en el helper de Task 7); la verificación es compilar + suite completa + arranque visual manual al final.

- [ ] **Step 1: FXML — cabecera y controles.** En `EstadisticasView.fxml`:
  1. Sidebar: `<Button fx:id="btnTabReparaciones" text="Reparaciones"...>` → `text="Técnicos"`.
  2. Título: `<Label text="Reparaciones por técnico"...>` → `text="Puntos por técnico"`, y añadir en su mismo `HBox`:
```xml
                    <Region HBox.hgrow="ALWAYS"/>
                    <RadioButton fx:id="rbPuntos" text="Puntos" selected="true"
                                 style="-fx-font-weight: bold;"/>
                    <RadioButton fx:id="rbPuntosDia" text="Puntos/día"
                                 style="-fx-font-weight: bold;"/>
```
  (import `javafx.scene.layout.Region` ya cubierto por `javafx.scene.layout.*`.)
  3. En el `FlowPane` de filtros: quitar `chkTodos` y `chkActividad`; renombrar `chkMedia` a texto `Ver medias`; añadir `fx:id="chkEquipo"` así:
```xml
                    <CheckBox fx:id="chkEquipo" text="Equipo (suma)"
                              style="-fx-text-fill: #000000; -fx-font-weight: bold;"/>
                    <CheckBox fx:id="chkMedia" text="Ver medias"
                              style="-fx-font-weight: bold;"/>
```
  4. Sustituir el bloque `hboxSlider` completo (Slider + labels) por:
```xml
                <HBox fx:id="hboxNavVentana" visible="false" managed="false"
                      alignment="CENTER" spacing="10">
                    <Button fx:id="btnVentanaAnterior" text="◀" onAction="#ventanaAnterior"
                            styleClass="btn-secondary"/>
                    <Label fx:id="lblRangoVentana"
                           style="-fx-font-size: 12px; -fx-text-fill: #2C3B54;"/>
                    <Button fx:id="btnVentanaSiguiente" text="▶" onAction="#ventanaSiguiente"
                            styleClass="btn-secondary"/>
                </HBox>
```

- [ ] **Step 2: Controller — campos y datos.**
  - Sustituir campos `@FXML chkTodos`, `@FXML chkActividad`, `@FXML hboxSlider`, `@FXML sliderVentana`, `@FXML lblSliderDesde`, `@FXML lblSliderHasta` por: `@FXML RadioButton rbPuntos, rbPuntosDia; @FXML CheckBox chkEquipo; @FXML HBox hboxNavVentana; @FXML Button btnVentanaAnterior, btnVentanaSiguiente; @FXML Label lblRangoVentana;` y campo `private int ventanaOffset = 0;`.
  - `private List<PuntoEstadisticaPuntos> todosPuntos = List.of();` (cambia el tipo).
  - `COLOR_TODOS` renombrar a `COLOR_EQUIPO` y `"Todos"` → `"Equipo"` en `coloresPorNombre` y la serie suma.
  - Añadir helpers:
```java
    private boolean metricaPorDia() { return rbPuntosDia.isSelected(); }

    /** Valor del punto según la métrica activa. */
    private double valorDe(PuntoEstadisticaPuntos p) {
        return metricaPorDia()
                ? PuntosEstadistica.puntosDia(p.getPuntos(), p.getPeriodo(),
                        cmbGranularidad.getValue(), java.time.LocalDate.now())
                : p.getPuntos();
    }
```
  - En `initialize()`: crear `ToggleGroup` para los dos RadioButton y listener que llama `renderVentana(ventanaOffset)`; reemplazar los listeners de `chkTodos`/`chkActividad` por uno de `chkEquipo` (default `setSelected(true)`) que llama `renderVentana(ventanaOffset)`; `chkMedia.setSelected(false)`; eliminar el listener del slider.
  - En `recargarDatos()`: cambiar la llamada a `new ReparacionDAO().getEstadisticasPuntos(granularidad, desde, hasta)`; la llamada final `configurarSlider()` pasa a `configurarVentana()` (Step 3).
  - Al eliminar el campo `sliderVentana`, el compilador señalará los usos restantes (p. ej. `renderVentana((int) sliderVentana.getValue())` dentro del cell factory de `cargarTecnicos`, línea ~242): sustituirlos todos por `renderVentana(ventanaOffset)`.

- [ ] **Step 3: Controller — ventana con flechas.** Sustituir `configurarSlider()` por:

```java
    /** Configura la navegación de ventana y renderiza la más reciente. */
    private void configurarVentana() {
        int maxOffset = Math.max(0, todosPeriodos.size() - ventanaTamanio);
        boolean hayNavegacion = maxOffset > 0;
        hboxNavVentana.setVisible(hayNavegacion);
        hboxNavVentana.setManaged(hayNavegacion);
        ventanaOffset = maxOffset;                 // lo más reciente
        renderVentana(ventanaOffset);
    }

    @FXML private void ventanaAnterior()  { renderVentana(Math.max(0, ventanaOffset - 1)); }
    @FXML private void ventanaSiguiente() {
        renderVentana(Math.min(Math.max(0, todosPeriodos.size() - ventanaTamanio), ventanaOffset + 1));
    }
```

En `renderVentana(int offset)`: primera línea `ventanaOffset = offset;`; donde actualizaba `lblSliderDesde/Hasta` poner:

```java
        int maxOffset = Math.max(0, todosPeriodos.size() - ventanaTamanio);
        btnVentanaAnterior.setDisable(inicio == 0);
        btnVentanaSiguiente.setDisable(offset >= maxOffset);
        lblRangoVentana.setText(tamanio + " periodos · "
                + todosPeriodos.get(inicio) + " — " + todosPeriodos.get(fin - 1));
```

- [ ] **Step 4: Controller — series por puntos.** En `renderVentana`:
  - Construcción de series: `new XYChart.Data<>(p.getPeriodo(), valorDe(p))` (antes `getCantidad()`).
  - Serie suma: renombrar a `"Equipo"`, condición `chkEquipo.isSelected()`; suma con `Map<String, Double>` y `valorDe(p)`.
  - Eje Y: `maxVisible` como `double` con `.mapToDouble(this::valorDe)`; `ejeY.setUpperBound(Math.max(5, Math.ceil(maxVisible) + 1));` y `ejeY.setLabel(metricaPorDia() ? "Puntos/día" : "Puntos");` — quitar `autoRanging`/tick fijo del FXML si estorban (dejar `tickUnit` en 1).
  - Tooltips de vértice (en `aplicarColores`, línea ~665): sustituir el texto por `PuntosEstadistica.textoTooltip(d.getXValue(), d.getYValue().doubleValue(), metricaPorDia(), trabajosDe(serie.getName(), d.getXValue()))` con helper:
```java
    private int trabajosDe(String tecnico, String periodo) {
        return todosPuntos.stream()
                .filter(p -> p.getPeriodo().equals(periodo)
                        && ("Equipo".equals(tecnico) || p.getNombreTecnico().equals(tecnico)))
                .mapToInt(p -> p.getnNormales() + p.getnGlass() + p.getnPulidos()).sum();
    }
```

- [ ] **Step 5: Controller — Promedio horizontal.** En `dibujarLineasMedia`, la línea de referencia del equipo pasa a: valor `PuntosEstadistica.promedioVentana(datosVentana, new ArrayList<>(periodosVisibles))` donde `datosVentana` se construye de `todosPuntos` (solo técnicos, nunca "Equipo") como `Map<String, Map<String, Double>> = tecnico → periodo → valorDe(p)`; etiqueta del label `"Promedio"`; **siempre visible**: `actualizarVisibilidadReferencia()` pasa a `lineasReferencia.forEach(n -> n.setVisible(true));` (y se elimina su vinculación a `chkTodos`). Si el promedio es 0 (sin actividad), no se dibuja. Eliminar `actualizarVisibilidadActividad()` y sus llamadas.

- [ ] **Step 6: Compilar y suite** — `mvn -q clean test` en el cliente → BUILD SUCCESS, suite completa verde (los tests existentes de `EntregaGlassTest`, `CargaTecnicosTest`, etc. no tocan esta vista).

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java
git commit -m "feat(cliente): vista Tecnicos por puntos — metrica Puntos/Puntos-dia, serie Equipo, promedio horizontal fijo y flechas de ventana"
```

---

### Task 9: Selección de técnicos — arranque limpio, buscador, leyenda, roles

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/MultiSelectDropdown.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`

**Interfaces:**
- Consumes: overload `setup(combo, items, cellFactory, etiqueta)` existente (línea 89) — NO tocarlo (18 llamantes).
- Produces: overload nuevo `setup(combo, items, cellFactory, etiqueta, Function<T,String> textoFiltro)` con buscador arriba.

- [ ] **Step 1: Overload con buscador en `MultiSelectDropdown`** (debajo del overload custom, línea ~107):

```java
    // ── Overload custom + buscador (Estadísticas por puntos) ──────────────────

    /** Igual que el overload custom, con un TextField de filtro sobre la lista.
     *  {@code textoFiltro} extrae el texto buscable del item; item null (separador) siempre visible. */
    public static <T> Handle setup(
            MultiSelectComboBox<T> combo,
            List<T> items,
            Callback<ListView<T>, ListCell<T>> cellFactory,
            StringProperty etiqueta,
            Function<T, String> textoFiltro) {

        ListView<T> listView = getOrCreate(combo);
        List<T> maestros = new ArrayList<>(items);

        if (combo.getUserData() == null) {
            combo.setUserData(listView);
            listView.setCellFactory(cellFactory);

            TextField buscador = new TextField();
            buscador.setPromptText("Buscar…");
            buscador.textProperty().addListener((obs, o, texto) -> {
                String t = texto == null ? "" : texto.trim().toLowerCase();
                List<T> filtrados = t.isEmpty() ? maestros : maestros.stream()
                        .filter(it -> it == null
                                || textoFiltro.apply(it).toLowerCase().contains(t))
                        .collect(java.util.stream.Collectors.toList());
                listView.getItems().setAll(filtrados);
                listView.setMaxHeight(Math.min(filtrados.size(), MAX_VISIBLE_ROWS) * ALTURA_FILA + RELLENO_LISTA);
            });
            setupPopupAndButton(combo, listView, etiqueta, buscador);
        }

        listView.getItems().setAll(maestros);
        listView.setMaxHeight(Math.min(maestros.size(), MAX_VISIBLE_ROWS) * ALTURA_FILA + RELLENO_LISTA);
        listView.refresh();
        return new Handle(listView);
    }
```

Y generalizar el helper privado sin tocar a los llamantes existentes:

```java
    private static <T> void setupPopupAndButton(
            MultiSelectComboBox<T> combo, ListView<T> listView, StringProperty etiqueta) {
        setupPopupAndButton(combo, listView, etiqueta, null);
    }

    private static <T> void setupPopupAndButton(
            MultiSelectComboBox<T> combo, ListView<T> listView, StringProperty etiqueta,
            TextField buscador) {
        // ... (cuerpo actual, con un cambio:)
        VBox contenedor = buscador != null ? new VBox(4, buscador, listView) : new VBox(listView);
        // resto idéntico
    }
```

Imports nuevos: `javafx.scene.control.TextField`, `java.util.ArrayList`, `java.util.function.Function` (algunos ya están).

- [ ] **Step 2: Controller — arranque limpio y roles.** En `cargarTecnicos()`:
  - **No** preseleccionar activos: eliminar `nombresSeleccionadosTec.add(t.getNombre())` del bucle de activos.
  - Rol técnico: tras calcular `nombreTecnicoSesion`, si `!Sesion.esAdminOSuperTecnico()`:
```java
        if (!com.reparaciones.Sesion.esAdminOSuperTecnico()) {
            if (nombreTecnicoSesion != null) nombresSeleccionadosTec.add(nombreTecnicoSesion);
            menuTecnicos.setVisible(false);
            menuTecnicos.setManaged(false);
            return; // sin desplegable: no montar el MultiSelectDropdown
        }
```
  - Cambiar la llamada a `MultiSelectDropdown.setup(...)` por el overload nuevo añadiendo el extractor: `t -> t == null ? "" : t.getNombre()`.
  - `etiquetaTecs` inicial pasa a `"+ Técnicos"` y `actualizarTextoMenuTecnicos()` muestra `"+ Técnicos"` cuando no hay selección.

- [ ] **Step 3: Controller — quitar por leyenda.** Al final del `Runnable render` de `renderVentana` (tras `aplicarColores`):

```java
            for (javafx.scene.Node item : chartReparaciones.lookupAll(".chart-legend-item")) {
                if (!(item instanceof Label lbl)) continue;
                String nombre = lbl.getText();
                boolean esTecnico = !"Equipo".equals(nombre);
                lbl.setStyle(esTecnico ? "-fx-cursor: hand;" : "");
                lbl.setOnMouseClicked(e -> {
                    if (!esTecnico) return;
                    nombresSeleccionadosTec.remove(nombre);
                    if (filtroTecHandle != null) filtroTecHandle.refresh();
                    actualizarTextoMenuTecnicos();
                    renderVentana(ventanaOffset);
                });
            }
```

- [ ] **Step 4: Compilar y suite** — `mvn -q clean test` → verde.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/MultiSelectDropdown.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java
git commit -m "feat(cliente): arranque limpio de la vista — buscador en el desplegable de tecnicos, quitar por leyenda y tecnico raso solo se ve a si mismo"
```

---

### Task 10: Tarjetas resumen del mes

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`

**Interfaces:**
- Consumes: `PuntosEstadistica.calcularTarjetas` y `formatearPuntos` (Task 7); `ReparacionDAO.getEstadisticasPuntos` (Task 6).

- [ ] **Step 1: FXML** — entre la cabecera (HBox del título) y el `FlowPane` de filtros:

```xml
                <HBox spacing="12">
                    <VBox spacing="2" style="-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 14;">
                        <Label fx:id="lblCardPuntosTitulo" style="-fx-font-size: 11px; -fx-text-fill: #7A8A9A;"/>
                        <HBox spacing="6" alignment="BASELINE_LEFT">
                            <Label fx:id="lblCardPuntosValor" style="-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;"/>
                            <Label fx:id="lblCardPuntosDelta" style="-fx-font-size: 11px;"/>
                        </HBox>
                    </VBox>
                    <VBox spacing="2" style="-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 14;">
                        <Label fx:id="lblCardDiaTitulo" style="-fx-font-size: 11px; -fx-text-fill: #7A8A9A;"/>
                        <HBox spacing="6" alignment="BASELINE_LEFT">
                            <Label fx:id="lblCardDiaValor" style="-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;"/>
                            <Label fx:id="lblCardDiaDelta" style="-fx-font-size: 11px;"/>
                        </HBox>
                    </VBox>
                </HBox>
```

- [ ] **Step 2: Controller** — campos `@FXML` para los 6 labels; método llamado desde `initialize()` (tras `recargarDatos()`) y desde `recargar()`:

```java
    /** Tarjetas del mes en curso (equipo, o el propio técnico si el rol es TECNICO). */
    private void cargarTarjetas() {
        java.time.YearMonth mes = java.time.YearMonth.now();
        List<PuntoEstadisticaPuntos> filas;
        try {
            filas = new ReparacionDAO().getEstadisticasPuntos("mes",
                    mes.minusMonths(1).atDay(1), mes.atEndOfMonth());
        } catch (SQLException e) { mostrarError(e); return; }
        String tecnico = com.reparaciones.Sesion.esAdminOSuperTecnico() ? null : nombreTecnicoSesion;
        var t = PuntosEstadistica.calcularTarjetas(filas, mes, java.time.LocalDate.now(), tecnico);
        String quien = tecnico == null ? "equipo" : "tú";
        lblCardPuntosTitulo.setText("Puntos · " + t.mesLabel() + " · " + quien);
        lblCardPuntosValor.setText(PuntosEstadistica.formatearPuntos(t.puntos()));
        pintarDelta(lblCardPuntosDelta, t.deltaPuntosPct());
        lblCardDiaTitulo.setText("Puntos/día · " + t.mesLabel() + " · " + quien);
        lblCardDiaValor.setText(PuntosEstadistica.formatearPuntos(t.puntosDia()));
        pintarDelta(lblCardDiaDelta, t.deltaPuntosDiaPct());
    }

    private void pintarDelta(Label lbl, Double pct) {
        if (pct == null) { lbl.setText(""); return; }
        boolean sube = pct >= 0;
        lbl.setText((sube ? "▲ +" : "▼ ") + String.format("%.0f", pct) + "%");
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (sube ? "#2E7D32" : "#C62828") + ";");
    }
```

- [ ] **Step 3: Compilar y suite** — `mvn -q clean test` → verde.

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java
git commit -m "feat(cliente): tarjetas resumen del mes — puntos y puntos/dia con delta contra el mes anterior"
```

---

### Task 11: Popover de desglose + "Ver en Historial"

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`

**Interfaces:**
- Consumes: `PuntosEstadistica.{textoPopover, periodoAFechas, formatearPuntos}`; `navegacion.navegarAReparaciones(desde, hasta, tecnico)` (interfaz `Navegable` existente); regla de navegabilidad actual (`Sesion.esAdminOSuperTecnico() || serie propia`).

- [ ] **Step 1: Sustituir el click de vértice.** En `aplicarColores` (bloque `if (navegable)`, línea ~684): el `setOnMouseClicked` deja de navegar directamente y abre el popover:

```java
                if (navegable) {
                    final XYChart.Series<String, Number> serieClick = serie;
                    nodo.setOnMouseClicked(e ->
                            mostrarPopoverDesglose(nodo, serieClick.getName(), d.getXValue()));
                }
```

- [ ] **Step 2: Implementar el popover** (método nuevo del controller):

```java
    /** Popover con el desglose del punto y salto opcional al Historial (spec §4). */
    private void mostrarPopoverDesglose(javafx.scene.Node ancla, String nombreSerie, String periodo) {
        boolean esEquipo = "Equipo".equals(nombreSerie);
        PuntoEstadisticaPuntos datos = todosPuntos.stream()
                .filter(p -> p.getPeriodo().equals(periodo)
                        && (esEquipo || p.getNombreTecnico().equals(nombreSerie)))
                .reduce((a, b) -> new PuntoEstadisticaPuntos(nombreSerie, periodo,
                        a.getPuntos() + b.getPuntos(),
                        a.getPuntosNormales() + b.getPuntosNormales(),
                        a.getPuntosGlass() + b.getPuntosGlass(),
                        a.getPuntosPulidos() + b.getPuntosPulidos(),
                        a.getnNormales() + b.getnNormales(),
                        a.getnGlass() + b.getnGlass(),
                        a.getnPulidos() + b.getnPulidos(),
                        a.getnSinPiezas() + b.getnSinPiezas()))
                .orElse(null);
        if (datos == null) return;

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        Label titulo = new Label(nombreSerie + " — " + periodo);
        titulo.setStyle("-fx-font-weight: bold; -fx-text-fill: #2C3B54;");
        Label cuerpo = new Label(PuntosEstadistica.textoPopover(datos));
        cuerpo.setStyle("-fx-text-fill: #2C3B54; -fx-font-size: 12px;");
        Button verHistorial = new Button("Ver en Historial");
        verHistorial.getStyleClass().add("btn-secondary");
        verHistorial.setOnAction(ev -> {
            popup.hide();
            java.time.LocalDate[] rango =
                    PuntosEstadistica.periodoAFechas(periodo, cmbGranularidad.getValue());
            navegacion.navegarAReparaciones(rango[0], rango[1], esEquipo ? null : nombreSerie);
        });

        VBox caja = new VBox(6, titulo, cuerpo, verHistorial);
        caja.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0;"
                + " -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 12;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 10, 0, 0, 2);");
        popup.getContent().add(caja);
        var b = ancla.localToScreen(ancla.getBoundsInLocal());
        popup.show(ancla, b.getMaxX() + 6, b.getMinY() - 10);
    }
```

- [ ] **Step 3: Borrar el método privado `periodoAFechas` del controller** (línea ~1086) y usar en su único llamante restante (si queda alguno) `PuntosEstadistica.periodoAFechas(periodo, cmbGranularidad.getValue())`.

- [ ] **Step 4: Compilar y suite** — `mvn -q clean test` → verde.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java
git commit -m "feat(cliente): popover de desglose al clicar un vertice con salto 'Ver en Historial' (navegacion arreglada como accion deliberada)"
```

---

### Task 12: Modal ⚙ Valores + filtro del Log + fix etiquetas Stock + CHANGELOG

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java:59`
- Modify: `CHANGELOG.md` (raíz)

**Interfaces:**
- Consumes: `ValoresDificultadDAO` (Task 6), `PuntosEstadistica.{parsePuntos, formatearPuntos}` (Task 7), `Sesion.esAdmin()`.

- [ ] **Step 1: FXML** — en el `HBox` de cabecera (junto a los RadioButton, tras `rbPuntosDia`):

```xml
                    <Button fx:id="btnValores" text="⚙ Valores" styleClass="btn-secondary"
                            onAction="#abrirModalValores"/>
```

- [ ] **Step 2: Controller — visibilidad y modal.** En `initialize()`:

```java
        btnValores.setVisible(com.reparaciones.Sesion.esAdmin());
        btnValores.setManaged(com.reparaciones.Sesion.esAdmin());
```

Método del modal (programático, sin FXML nuevo):

```java
    /** Etiquetas legibles de las claves de Dificultad_puntos, en orden de mostrado. */
    private static final java.util.LinkedHashMap<String, String> ETIQUETA_CLAVE = new java.util.LinkedHashMap<>();
    static {
        ETIQUETA_CLAVE.put("pantalla", "Pantalla");
        ETIQUETA_CLAVE.put("bateria",  "Batería");
        ETIQUETA_CLAVE.put("chasis",   "Chasis");
        ETIQUETA_CLAVE.put("camara",   "Cámara");
        ETIQUETA_CLAVE.put("glass",    "Glass");
        ETIQUETA_CLAVE.put("marco",    "Marco");
        ETIQUETA_CLAVE.put("otro",     "Otro / sin piezas");
        ETIQUETA_CLAVE.put("pulido",   "Pulido");
    }

    @FXML
    private void abrirModalValores() {
        List<com.reparaciones.models.ValorDificultad> valores;
        try {
            valores = new com.reparaciones.dao.ValoresDificultadDAO().getAll();
        } catch (SQLException e) { mostrarError(e); return; }
        java.util.Map<String, Double> porClave = new java.util.HashMap<>();
        valores.forEach(v -> porClave.put(v.getClave(), v.getPuntos()));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Valores de dificultad");
        dialog.setHeaderText("Puntos por tipo de trabajo");
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(8);
        java.util.Map<String, TextField> campos = new java.util.LinkedHashMap<>();
        int fila = 0;
        for (var e : ETIQUETA_CLAVE.entrySet()) {
            if (!porClave.containsKey(e.getKey())) continue;
            grid.add(new Label(e.getValue()), 0, fila);
            TextField tf = new TextField(PuntosEstadistica.formatearPuntos(porClave.get(e.getKey())));
            tf.setPrefWidth(80);
            campos.put(e.getKey(), tf);
            grid.add(tf, 1, fila++);
        }
        Label aviso = new Label("Cambiar un valor re-valora también las estadísticas pasadas.");
        aviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A8A9A;");
        grid.add(aviso, 0, fila, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validar sin cerrar el diálogo si hay campos inválidos
        javafx.scene.control.Button ok =
                (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            List<com.reparaciones.models.ValorDificultad> nuevos = new java.util.ArrayList<>();
            boolean hayError = false;
            for (var e : campos.entrySet()) {
                var parsed = PuntosEstadistica.parsePuntos(e.getValue().getText());
                if (parsed.isEmpty()) {
                    e.getValue().setStyle("-fx-border-color: #C62828;");
                    hayError = true;
                } else {
                    e.getValue().setStyle("");
                    nuevos.add(new com.reparaciones.models.ValorDificultad(e.getKey(), parsed.get()));
                }
            }
            if (hayError) { ev.consume(); return; }
            try {
                new com.reparaciones.dao.ValoresDificultadDAO().guardar(nuevos);
            } catch (SQLException ex) { ev.consume(); mostrarError(ex); return; }
            recargarDatos();
            cargarTarjetas();
        });
        dialog.showAndWait();
    }
```

- [ ] **Step 3: Filtro del Log.** En `LogController.java:59`, añadir `"EDITAR_PUNTOS"` a la lista de acciones (tras `"DESHACER_ENTREGA_GLASS"`).

- [ ] **Step 4: Fix etiquetas de la pestaña Stock.** En `EstadisticasController` (`PREFIJO_TIPO`, línea ~886): `PREFIJO_TIPO.put("lcd", "LCD")` → `PREFIJO_TIPO.put("lcd", "Pantalla")` y `PREFIJO_TIPO.put("g", "Pantalla")` → `PREFIJO_TIPO.put("g", "Glass")` (fuente: `FormularioReparacionController.traducirTipo`, línea 1438).

- [ ] **Step 5: CHANGELOG.** En `CHANGELOG.md`, sección `[Unreleased]` (crearla arriba si no existe) añadir:

```markdown
### Añadido
- Estadísticas por puntos de dificultad: la pestaña "Técnicos" mide puntos (tabla editable por el admin en ⚙ Valores) con métricas Puntos y Puntos/día, tarjetas del mes, promedio del equipo, desplegable con buscador, flechas de navegación temporal y popover de desglose con salto al Historial.

### Corregido
- La pestaña Stock de Estadísticas etiquetaba los SKU `g` como "Pantalla" y `lcd` como "LCD"; ahora `g`=Glass y `lcd`=Pantalla, como el formulario de reparación.
```

- [ ] **Step 6: Compilar y suite** — `mvn -q clean test` → verde.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/EstadisticasView.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java \
        CHANGELOG.md
git commit -m "feat(cliente): modal Valores de dificultad (solo admin), accion EDITAR_PUNTOS en el filtro del log y fix etiquetas g/lcd en Stock"
```

---

### Task 13: Cierre — suites completas, consulta de calibración y estado de la spec

**Files:**
- Create: `gestion-reparaciones-servidor/sql/consulta-calibracion-otro.sql`
- Modify: `docs/superpowers/specs/2026-09-01-estadisticas-puntos-design.md` (línea de Estado)

- [ ] **Step 1: Suites completas en ambos repos**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q clean test
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q clean test
```
Expected: ambas verdes. Pegar el recuento de tests en el resumen final.

- [ ] **Step 2: Consulta de calibración de "otro"** (la ejecuta el usuario en preprod; Claude no hace SSH):

```sql
-- Calibración del peso 'otro' (spec 2026-09-01 §2): compara los puntos medios de las
-- reparaciones formalizadas con el volumen de reparaciones sin piezas, para decidir
-- si 0,50 es un peso justo. Ejecutar en preprod con: mysql -u ... reparaciones < consulta-calibracion-otro.sql

-- 1) Puntos medios por reparación formalizada (R/G con piezas), con la tabla vigente
SELECT COUNT(DISTINCT r.ID_REP)                                        AS reps_formalizadas,
       ROUND(SUM(dp.PUNTOS * rc.CANTIDAD) / COUNT(DISTINCT r.ID_REP), 2) AS puntos_medios
FROM Reparacion r
JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0
LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM
JOIN Dificultad_puntos dp ON dp.CLAVE = CASE
    WHEN c.TIPO IS NULL              THEN 'otro'
    WHEN LOWER(c.TIPO) LIKE 'bat%'   THEN 'bateria'
    WHEN LOWER(c.TIPO) LIKE 'cha%'   THEN 'chasis'
    WHEN LOWER(c.TIPO) LIKE 'cam%'   THEN 'camara'
    WHEN LOWER(c.TIPO) LIKE 'lcd%'   THEN 'pantalla'
    WHEN LOWER(c.TIPO) LIKE 'mc%'    THEN 'marco'
    WHEN LOWER(c.TIPO) LIKE 'g%'     THEN 'glass'
    ELSE 'otro' END
WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL;

-- 2) Cuántas reparaciones cerradas NO tienen ninguna pieza (hoy puntuarían 'otro' = 0,50)
SELECT COUNT(*) AS reps_sin_piezas
FROM Reparacion r
WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Reparacion_componente rc
                   WHERE rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0);

-- 3) Distribución por nº de piezas (contexto)
SELECT piezas, COUNT(*) AS reps FROM (
    SELECT r.ID_REP, COUNT(rc.ID_RC) AS piezas
    FROM Reparacion r
    LEFT JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0
    WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL
    GROUP BY r.ID_REP) t
GROUP BY piezas ORDER BY piezas;
```

- [ ] **Step 3: Actualizar el Estado de la spec** a `IMPLEMENTADA en ramas feature/estadisticas-puntos (cliente y servidor) — pendiente migración en preprod, smoke y merges (OK del usuario)`.

- [ ] **Step 4: Commits**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor"
git add sql/consulta-calibracion-otro.sql
git commit -m "docs(sql): consulta de calibracion del peso 'otro' contra reparaciones formalizadas"
cd "C:/Users/dev/Documents/ProgramaReparaciones"
git add docs/superpowers/specs/2026-09-01-estadisticas-puntos-design.md
git commit -m "docs(spec): estadisticas-puntos implementada en ramas feature (pendiente migracion, smoke y merges)"
```

- [ ] **Step 5: Resumen final para el usuario** — recordarle el orden de entrega (spec §8): migración SQL en preprod (vista previa) → OK merge servidor + deploy → smoke §9 → OK merge cliente → release 0.16.2 cuando él diga. **Nada de eso se ejecuta sin su OK.**
