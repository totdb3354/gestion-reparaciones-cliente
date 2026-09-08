# Estadísticas: lo de fuera de horario suma, no promedia — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar la spec `docs/superpowers/specs/2026-09-08-estadisticas-horario-no-promedia-design.md`: los trabajos cerrados fuera del horario del taller (o en fin de semana) siguen sumando en totales, gráfico y tarjetas, pero dejan de entrar en todas las medias de Estadísticas → Técnicos (Promedio, x̄ por técnico, Por encima/Por debajo, IMEIs típicos, referencia de las tarjetas), en las cuatro granularidades; visible solo en tooltips.

**Architecture:** El servidor lee la hora de cierre completa (`FECHA_FIN`, UTC en BD), la pasa a `Europe/Madrid`, decide con `util/Jornada` si el cierre cayó en la franja con margen de 15 min y devuelve dos campos aditivos por (técnico, periodo): `puntosJornada` y `nImeisJornada`. El cliente sigue usando `puntos`/`nImeis` para todo lo que suma y pasa a usar los de jornada en todas las medias; con un servidor antiguo (campos `null`) se comporta exactamente como la 0.16.2. De paso, la fecha del punto pasa a ser la de Madrid (antes `DATE(FECHA_FIN)` en UTC).

**Tech Stack:** Java 17, Spring Boot + JdbcTemplate (servidor, submódulo git `gestion-reparaciones-servidor`), JavaFX + Gson (cliente `gestion-reparaciones-cliente`), JUnit 5, Maven.

## Global Constraints

- **Horario** (spec §2, dato del usuario 2026-09-08): entrada **8:30** todos los laborables; salida **18:00** L-M, **17:00** X-J, **14:30** V; sábado y domingo sin jornada. **Margen asimétrico**: 30 min antes de la entrada y 15 después de la salida → franja efectiva **8:00–18:15 / 8:00–17:15 / 8:00–14:45**, extremos incluidos (ajuste del usuario tras la revisión final; el código de la Task 1 muestra el margen simétrico original). Solo cuentan entrada y salida (sin pausa de comida). Todo en hora de **Madrid**.
- **Regla única en las cuatro granularidades** (spec §3): totales suman todo; medias solo con puntos de jornada; un (técnico, periodo) es "trabajado" solo si sus puntos de jornada > 0.
- **Solo tooltips** (spec §4): tercera línea "`X` puntos fuera de horario" en el tooltip del punto (extra siempre en puntos, también con la métrica Puntos/día); coletilla "**, en horario**" en `ambitoReferencia()` en todas las granularidades cuando el servidor envía los campos; con servidor antiguo, todo como en 0.16.2 (coletilla ", L–V" solo en Día, sin línea de extra).
- **Campos aditivos**: servidor `double puntosJornada`, `int nImeisJornada`; cliente `Double puntosJornada`, `Integer nImeisJornada` (Gson rellena por reflexión; `null` = servidor viejo). El constructor de 6 argumentos de `FilaPuntos` se conserva (por defecto `enJornada = true`) para no romper los tests existentes.
- `esLaborable` / `soloLaborables` del cliente **no se tocan** (redundantes con servidor nuevo, sostienen el fallback).
- **Sin migración de BD, sin beans nuevos** en el servidor (el wiring no cambia; aun así, compilar y pasar la suite).
- **Git**: raíz en rama `feature/estadisticas-horario` (ya creada desde `hotfix/0.16.2`, spec en `6c31ea2`). Servidor: crear rama `feature/estadisticas-horario` desde `main` del submódulo (`1559a72`). **No** tocar el gitlink del submódulo ni hacer merge/push/tags: eso lo decide el usuario al cerrar (memoria `feedback_merge_confirmacion`). Commits **sin** `Co-Authored-By` (memoria `feedback_commits`). Mensajes de commit en español, sin acentos en el asunto (estilo del repo).
- **Toolchain** (memoria `project_toolchain_local`): en cada llamada Bash que use Maven, anteponer
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.
- **Documentación de procedencia**: cada constante del horario lleva comentario de dónde sale (petición histórica del usuario, igual que `CargaTecnicos.JORNADA_HORAS`).

---

## Mapa de ficheros

| Fichero | Responsabilidad | Tarea |
|---|---|---|
| `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/Jornada.java` (nuevo) | Horario del taller, margen, conversión UTC→Madrid, `enJornada` | 1 |
| `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/JornadaTest.java` (nuevo) | Bordes de la franja, finde, conversión de zona | 1 |
| `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java` | `FilaPuntos.enJornada`, acumulación de `puntosJornada` / `imeisJornada` | 2 |
| `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/PuntoEstadisticaPuntos.java` | Campos aditivos | 2 |
| `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java` | Tests de la agregación con horario | 2 |
| `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` | Query con `FECHA_FIN` completo, rango por instante Madrid, mapper | 3 |
| `gestion-reparaciones-servidor/docs/api_contract.md` | Respuesta del endpoint | 3 |
| `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/PuntoEstadisticaPuntos.java` | Campos aditivos + constructor de test | 4 |
| `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java` | `puntosJornada`, `puntosExtra`, `imeisJornada`, `textoTooltip` con extra, tarjetas | 4, 5 |
| `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java` | Tests de helpers, tooltip y tarjetas | 4, 5 |
| `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java` | Medias con `valorJornadaDe`, `extraDe`, `ambitoReferencia` | 6 |
| `gestion-reparaciones-cliente/docs/metricas-estadisticas.md`, `CHANGELOG.md` (raíz) | Diccionario y changelog | 7 |

---

### Task 1: Servidor — `Jornada` (horario, margen, hora de Madrid)

> Ajuste posterior (2026-09-08, tras la revisión final): margen de entrada 30 min (franja desde las 8:00) y de salida 15 min — constantes `MARGEN_ENTRADA`/`MARGEN_SALIDA`. El código de abajo es el ejecutado originalmente.

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/Jornada.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/JornadaTest.java`

**Interfaces:**
- Produces: `Jornada.MADRID : ZoneId`; `Jornada.aMadrid(java.sql.Timestamp utc) : ZonedDateTime`; `Jornada.enJornada(ZonedDateTime cierreMadrid) : boolean`.

- [ ] **Step 1: Crear la rama del servidor**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && git status --short && git checkout -b feature/estadisticas-horario main && git log --oneline -1
```
Esperado: árbol limpio, `Switched to a new branch 'feature/estadisticas-horario'`, último commit `1559a72`.

- [ ] **Step 2: Escribir el test que falla**

Crear `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/JornadaTest.java`:

```java
package com.reparaciones.servidor.util;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/** Horario del taller con margen de 15 min (spec 2026-09-08 §2). Fechas: 2026-08-31 lunes,
 *  2026-09-02 miércoles, 2026-08-28 viernes, 2026-09-05 sábado, 2026-01-12 lunes. */
class JornadaTest {

    private static ZonedDateTime madrid(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(Jornada.MADRID);
    }

    @Test void lunesEntraALasOchoYMediaYSaleALasSeisConMargen() {
        assertFalse(Jornada.enJornada(madrid("2026-08-31T08:14:59")));
        assertTrue (Jornada.enJornada(madrid("2026-08-31T08:15:00")));
        assertTrue (Jornada.enJornada(madrid("2026-08-31T13:00:00")));   // la comida no descuenta
        assertTrue (Jornada.enJornada(madrid("2026-08-31T18:15:00")));
        assertFalse(Jornada.enJornada(madrid("2026-08-31T18:15:01")));
    }

    @Test void miercolesSaleALasCinco() {
        assertTrue (Jornada.enJornada(madrid("2026-09-02T17:15:00")));
        assertFalse(Jornada.enJornada(madrid("2026-09-02T17:16:00")));
    }

    @Test void viernesSaleADosYMedia() {
        assertTrue (Jornada.enJornada(madrid("2026-08-28T08:30:00")));
        assertTrue (Jornada.enJornada(madrid("2026-08-28T14:45:00")));
        assertFalse(Jornada.enJornada(madrid("2026-08-28T14:46:00")));
        assertFalse(Jornada.enJornada(madrid("2026-08-28T22:30:00")));
    }

    @Test void finDeSemanaSiempreFuera() {
        assertFalse(Jornada.enJornada(madrid("2026-09-05T10:00:00")));   // sábado
        assertFalse(Jornada.enJornada(madrid("2026-09-06T12:00:00")));   // domingo
    }

    @Test void aMadridConvierteElTimestampUtcDeLaBd() {
        // viernes 28/08 20:30Z = 22:30 Madrid (verano): mismo día, fuera de horario
        ZonedDateTime z = Jornada.aMadrid(Timestamp.from(Instant.parse("2026-08-28T20:30:00Z")));
        assertEquals(LocalDate.of(2026, 8, 28), z.toLocalDate());
        assertEquals(LocalTime.of(22, 30), z.toLocalTime());
        assertFalse(Jornada.enJornada(z));
        // 28/08 22:30Z = sábado 29/08 00:30 Madrid: la fecha del cierre es la de Madrid
        ZonedDateTime s = Jornada.aMadrid(Timestamp.from(Instant.parse("2026-08-28T22:30:00Z")));
        assertEquals(LocalDate.of(2026, 8, 29), s.toLocalDate());
        assertFalse(Jornada.enJornada(s));
    }

    @Test void enInviernoTambienSeComparaEnHoraLocal() {
        // lunes 12/01 17:00Z = 18:00 Madrid (invierno, UTC+1): en jornada
        assertTrue(Jornada.enJornada(Jornada.aMadrid(Timestamp.from(Instant.parse("2026-01-12T17:00:00Z")))));
    }
}
```

- [ ] **Step 3: Ejecutar el test y ver que falla**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q test -Dtest=JornadaTest 2>&1 | tail -15
```
Esperado: error de compilación `cannot find symbol: class Jornada`.

- [ ] **Step 4: Implementar `Jornada`**

Crear `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/Jornada.java`:

```java
package com.reparaciones.servidor.util;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Horario del taller (spec 2026-09-08-estadisticas-horario-no-promedia §2): decide si un cierre
 * cayó "en jornada" (cuenta en las medias de Estadísticas) o "fuera de horario" (suma en los
 * totales, no promedia). Solo importan entrada y salida: la media hora de comida de L–J no tiene
 * hora fija y no se descuenta. El fin de semana es el caso extremo: jornada de 0 h.
 * <p>Futuro apuntado (como los topes de la carga de capacidad): mover a tabla configurable.</p>
 */
public final class Jornada {

    private Jornada() {}

    /** La BD y el contenedor van en UTC; la comparación se hace siempre en hora de Madrid. */
    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Entrada de todos los días laborables (dato del usuario, 2026-09-08). */
    public static final LocalTime ENTRADA = LocalTime.of(8, 30);

    /**
     * Salida por día de semana (dato del usuario, 2026-09-08). Sábado y domingo ausentes = sin
     * jornada. Las horas resultantes (9 / 9 / 8 / 8 / 6, con media hora de comida de L a J) son
     * las mismas {@code JORNADA_HORAS} de la carga de capacidad del cliente ({@code CargaTecnicos}).
     */
    public static final Map<DayOfWeek, LocalTime> SALIDA = Map.of(
            DayOfWeek.MONDAY,    LocalTime.of(18, 0),
            DayOfWeek.TUESDAY,   LocalTime.of(18, 0),
            DayOfWeek.WEDNESDAY, LocalTime.of(17, 0),
            DayOfWeek.THURSDAY,  LocalTime.of(17, 0),
            DayOfWeek.FRIDAY,    LocalTime.of(14, 30));

    /** Margen a cada lado de la franja (decisión del usuario 2026-09-08): cerrar el último
     *  móvil a las 18:03 no es hora extra. Franja efectiva 8:15–18:15 / 17:15 / 14:45. */
    public static final Duration MARGEN = Duration.ofMinutes(15);

    /** Cierre en hora de Madrid a partir del Timestamp UTC de la BD (la JVM del contenedor va en UTC). */
    public static ZonedDateTime aMadrid(Timestamp utc) {
        return utc.toInstant().atZone(MADRID);
    }

    /** true si el cierre cae en [ENTRADA − MARGEN, SALIDA + MARGEN] (extremos incluidos) de un día con jornada. */
    public static boolean enJornada(ZonedDateTime cierreMadrid) {
        LocalTime salida = SALIDA.get(cierreMadrid.getDayOfWeek());
        if (salida == null) return false;
        LocalTime hora = cierreMadrid.toLocalTime();
        return !hora.isBefore(ENTRADA.minus(MARGEN)) && !hora.isAfter(salida.plus(MARGEN));
    }
}
```

- [ ] **Step 5: Ejecutar el test y ver que pasa**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q test -Dtest=JornadaTest 2>&1 | tail -15
```
Esperado: `Tests run: 6, Failures: 0, Errors: 0` (o salida vacía con `-q`).

- [ ] **Step 6: Commit (submódulo)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && git add src/main/java/com/reparaciones/servidor/util/Jornada.java src/test/java/com/reparaciones/servidor/util/JornadaTest.java && git commit -q -m "feat(estadisticas): horario del taller (8:30-18/17/14:30, margen 15 min, hora de Madrid) para clasificar cierres en jornada" && git log --oneline -1
```

---

### Task 2: Servidor — `PuntosCalculo` acumula puntos e IMEIs de jornada

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java:32-33` (record `FilaPuntos`) y `:59-99` (`agregar`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/PuntoEstadisticaPuntos.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java`

**Interfaces:**
- Consumes: nada de la Task 1 (la clasificación llega ya hecha en la fila).
- Produces: `PuntosCalculo.FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei, String tipoPieza, Integer cantidad, boolean enJornada)` (+ constructor de 6 args con `enJornada = true`); `PuntoEstadisticaPuntos.getPuntosJornada() : double`, `getnImeisJornada() : int`; constructor del modelo de 13 args `(…, int nSinPiezas, int nImeis, double puntosJornada, int nImeisJornada)`.

- [ ] **Step 1: Escribir los tests que fallan**

Añadir al final de `PuntosCalculoTest` (antes de la llave de cierre de la clase):

```java
    // ── horario: lo de fuera suma, no promedia (spec 2026-09-08) ─────────────
    @Test void puntosJornadaSumanSoloLosCierresEnHorario() {
        LocalDate d = LocalDate.of(2026, 8, 28);
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Alex", d, "R20260828_1", "111", "lcd14", 1,    true),  // 2 piezas en jornada
                new PuntosCalculo.FilaPuntos("Alex", d, "R20260828_1", "111", "bat14", 1,    true),
                new PuntosCalculo.FilaPuntos("Alex", d, "R20260828_2", "222", "cha12", 1,    false), // extra
                new PuntosCalculo.FilaPuntos("Alex", d, "P20260828_1", "333", null,    null, false)); // pulido extra
        PuntoEstadisticaPuntos p = PuntosCalculo.agregar(filas, VALORES, LocalDate::toString).get(0);
        assertEquals(4.25, p.getPuntos(), 0.001);          // 2,0 + 2,0 + 0,25: el total suma todo
        assertEquals(2.00, p.getPuntosJornada(), 0.001);   // solo la reparación en horario
        assertEquals(3, p.getnImeis());
        assertEquals(1, p.getnImeisJornada());
        assertEquals(1, p.getnPulidos());                  // los contadores no cambian
    }

    @Test void imeiConUnCierreEnHorarioYOtroFueraCuentaUnaVezEnJornada() {
        LocalDate d = LocalDate.of(2026, 8, 28);
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Alex", d, "G20260828_1", "111", "g14",  1, true),
                new PuntosCalculo.FilaPuntos("Alex", d, "G20260828_2", "111", "mc14", 1, false),
                new PuntosCalculo.FilaPuntos("Alex", d, "G20260828_3", "222", "g14",  1, false)); // solo extra
        PuntoEstadisticaPuntos p = PuntosCalculo.agregar(filas, VALORES, LocalDate::toString).get(0);
        assertEquals(2, p.getnImeis());
        assertEquals(1, p.getnImeisJornada());
        assertEquals(0.50, p.getPuntosJornada(), 0.001);
    }

    @Test void filasSinHorarioCuentanComoJornada() {
        // Constructor de 6 argumentos (tests previos y usos sin horario): todo en jornada
        List<PuntosCalculo.FilaPuntos> filas = List.of(
                new PuntosCalculo.FilaPuntos("Alex", LocalDate.of(2026, 8, 28), "R20260828_1", "111", "lcd14", 1));
        PuntoEstadisticaPuntos p = PuntosCalculo.agregar(filas, VALORES, LocalDate::toString).get(0);
        assertEquals(p.getPuntos(), p.getPuntosJornada(), 0.001);
        assertEquals(p.getnImeis(), p.getnImeisJornada());
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q test -Dtest=PuntosCalculoTest 2>&1 | tail -15
```
Esperado: error de compilación (constructor de 7 args de `FilaPuntos` y `getPuntosJornada` no existen).

- [ ] **Step 3: Modelo del servidor**

Reemplazar el contenido de `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/PuntoEstadisticaPuntos.java`:

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
    private int    nImeis;
    private double puntosJornada;   // aditivo 2026-09-08: cerrados en horario (lo de fuera suma, no promedia)
    private int    nImeisJornada;   // aditivo 2026-09-08: IMEIs con algún cierre en horario en el periodo

    public PuntoEstadisticaPuntos() {}

    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas,
                                  int nImeis, double puntosJornada, int nImeisJornada) {
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
        this.nImeis = nImeis;
        this.puntosJornada = puntosJornada;
        this.nImeisJornada = nImeisJornada;
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
    public int    getnImeis()         { return nImeis; }
    public double getPuntosJornada()  { return puntosJornada; }
    public int    getnImeisJornada()  { return nImeisJornada; }
}
```

- [ ] **Step 4: `FilaPuntos` con `enJornada`**

En `PuntosCalculo.java`, sustituir el record `FilaPuntos` (líneas 31-33) por:

```java
    /** Fila cruda de la query (una por pieza; reparación sin piezas = una fila con tipoPieza null).
     *  {@code fecha} es la del cierre en Madrid; {@code enJornada} = cierre dentro del horario
     *  ({@link Jornada}), la misma para todas las filas de una reparación. */
    public record FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei,
                             String tipoPieza, Integer cantidad, boolean enJornada) {
        /** Fila en jornada (compatibilidad: tests previos y usos sin horario). */
        public FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei,
                          String tipoPieza, Integer cantidad) {
            this(tecnico, fecha, idRep, imei, tipoPieza, cantidad, true);
        }
    }
```

- [ ] **Step 5: `agregar` acumula jornada**

Sustituir el cuerpo de `agregar` (desde `// 1) agrupar filas` hasta el `return out;`) por:

```java
        // 1) agrupar filas por reparación (conservando técnico, fecha, IMEI y si cerró en jornada)
        record Rep(String tecnico, LocalDate fecha, String idRep, String imei, boolean enJornada) {}
        Map<Rep, List<Pieza>> piezasPorRep = new LinkedHashMap<>();
        for (FilaPuntos f : filas) {
            Rep rep = new Rep(f.tecnico(), f.fecha(), f.idRep(), f.imei(), f.enJornada());
            List<Pieza> lista = piezasPorRep.computeIfAbsent(rep, k -> new ArrayList<>());
            if (f.tipoPieza() != null || f.cantidad() != null)
                lista.add(new Pieza(f.tipoPieza(), f.cantidad() == null ? 1 : f.cantidad()));
        }

        // 2) acumular por técnico + periodo. Lo de fuera de horario suma en el total pero no
        //    promedia (spec 2026-09-08): puntos[4] e imeisJornada solo con cierres en jornada.
        record Acum(double[] puntos, int[] contadores, Set<String> imeis, Set<String> imeisJornada) {}  // puntos: total,N,G,P,jornada — contadores: nN,nG,nP,nSin
        Map<String, Map<String, Acum>> mapa = new LinkedHashMap<>();
        piezasPorRep.forEach((rep, piezas) -> {
            String periodo = periodoDe.apply(rep.fecha());
            Acum a = mapa.computeIfAbsent(rep.tecnico(), k -> new LinkedHashMap<>())
                         .computeIfAbsent(periodo, k -> new Acum(new double[5], new int[4], new HashSet<>(), new HashSet<>()));
            double pts = puntosDeReparacion(rep.idRep(), piezas, valores);
            a.puntos()[0] += pts;
            char pref = rep.idRep().charAt(0);
            if (pref == 'R') { a.puntos()[1] += pts; a.contadores()[0]++; }
            if (pref == 'G') { a.puntos()[2] += pts; a.contadores()[1]++; }
            if (pref == 'P') { a.puntos()[3] += pts; a.contadores()[2]++; }
            if (pref != 'P' && piezas.isEmpty()) a.contadores()[3]++;
            if (rep.imei() != null) a.imeis().add(rep.imei()); // IMEIs distintos del periodo
            if (rep.enJornada()) {
                a.puntos()[4] += pts;
                if (rep.imei() != null) a.imeisJornada().add(rep.imei());
            }
        });

        // 3) aplanar y ordenar como el endpoint viejo (periodo, técnico)
        List<PuntoEstadisticaPuntos> out = new ArrayList<>();
        mapa.forEach((tec, periodos) -> periodos.forEach((periodo, a) ->
                out.add(new PuntoEstadisticaPuntos(tec, periodo,
                        a.puntos()[0], a.puntos()[1], a.puntos()[2], a.puntos()[3],
                        a.contadores()[0], a.contadores()[1], a.contadores()[2], a.contadores()[3],
                        a.imeis().size(), a.puntos()[4], a.imeisJornada().size()))));
        out.sort(Comparator.comparing(PuntoEstadisticaPuntos::getPeriodo)
                           .thenComparing(PuntoEstadisticaPuntos::getNombreTecnico));
        return out;
```

- [ ] **Step 6: Ejecutar toda la suite del servidor**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q test 2>&1 | tail -20
```
Esperado: sin fallos (los tests previos de `PuntosCalculoTest` usan el constructor de 6 args y siguen verdes; los 3 nuevos pasan).

- [ ] **Step 7: Commit (submódulo)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && git add src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java src/main/java/com/reparaciones/servidor/model/PuntoEstadisticaPuntos.java src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java && git commit -q -m "feat(estadisticas): puntosJornada y nImeisJornada aditivos en la agregacion por tecnico y periodo" && git log --oneline -1
```

---

### Task 3: Servidor — query con hora de cierre completa en Madrid + contrato

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java:411-431` (`getEstadisticasPuntos`) e imports (líneas 3-20)
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md:312-330`

**Interfaces:**
- Consumes: `Jornada.MADRID`, `Jornada.aMadrid`, `Jornada.enJornada` (Task 1); `FilaPuntos` de 7 args (Task 2).
- Produces: el endpoint `GET /api/reparaciones/estadisticas/puntos` devuelve `puntosJornada` y `nImeisJornada`, y `periodo` calculado con la fecha de Madrid.

No hay test de DAO (requiere BD): la lógica de fecha/horario está cubierta por `JornadaTest`; aquí se verifica compilación, suite y arranque.

- [ ] **Step 1: Import**

En `ReparacionDAO.java`, junto a `import com.reparaciones.servidor.util.PuntosCalculo;` (línea 4) añadir:

```java
import com.reparaciones.servidor.util.Jornada;
```
y junto a `import java.time.ZoneId;` (línea 16) añadir:

```java
import java.time.ZonedDateTime;
```

- [ ] **Step 2: Reescribir `getEstadisticasPuntos`**

Sustituir el método completo (líneas 411-431; conservar el javadoc que tiene encima) por:

```java
    public List<PuntoEstadisticaPuntos> getEstadisticasPuntos(
            String granularidad, LocalDate desde, LocalDate hasta, Map<String, Double> valores) {
        // La BD guarda UTC: el rango se pide por instante (días de Madrid completos) y la fecha
        // del punto y el "en jornada" salen de la hora de cierre en Madrid (spec 2026-09-08 §5).
        // Antes DATE(FECHA_FIN) usaba la fecha UTC: un cierre entre las 00:00 y las 02:00 de
        // verano caía en el día anterior.
        Timestamp desdeUtc = Timestamp.from(desde.atStartOfDay(Jornada.MADRID).toInstant());
        Timestamp hastaUtc = Timestamp.from(hasta.plusDays(1).atStartOfDay(Jornada.MADRID).toInstant());
        List<PuntosCalculo.FilaPuntos> filas = jdbc.query(
                "SELECT t.NOMBRE, r.FECHA_FIN, r.ID_REP, r.IMEI, c.TIPO, rc.CANTIDAD" +
                " FROM Reparacion r" +
                " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " LEFT JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0" +
                " LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM" +
                " WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%' OR r.ID_REP LIKE 'P%')" +
                " AND r.FECHA_FIN IS NOT NULL AND r.FECHA_FIN >= ? AND r.FECHA_FIN < ?",
                (rs, row) -> {
                    ZonedDateTime cierre = Jornada.aMadrid(rs.getTimestamp("FECHA_FIN"));
                    return new PuntosCalculo.FilaPuntos(
                            rs.getString("NOMBRE"),
                            cierre.toLocalDate(),
                            rs.getString("ID_REP"),
                            rs.getString("IMEI"),
                            rs.getString("TIPO"),
                            (Integer) rs.getObject("CANTIDAD"),
                            Jornada.enJornada(cierre));
                },
                desdeUtc, hastaUtc);
        return PuntosCalculo.agregar(filas, valores,
                fecha -> formatearPeriodo(inicioPeriodo(fecha, granularidad), granularidad));
    }
```

- [ ] **Step 3: Contrato de la API**

En `gestion-reparaciones-servidor/docs/api_contract.md`, sustituir el bloque de la sección `### GET /api/reparaciones/estadisticas/puntos…` (líneas 313-330) por:

```markdown
Estadísticas por puntos de dificultad por técnico en el rango (spec 2026-09-01). El `periodo` se calcula con la fecha de cierre **en hora de Madrid**; el rango `desde`/`hasta` son días de Madrid completos. `puntosJornada` y `nImeisJornada` (spec 2026-09-08) son la parte cerrada **dentro del horario del taller** (8:30–18:00 L-M, 17:00 X-J, 14:30 V, con 15 min de margen; fin de semana = fuera): el cliente los usa en las medias, y `puntos`/`nImeis` en los totales.  
**Response:**
```json
[
  {
    "nombreTecnico": "Juan",
    "periodo": "2025-01",
    "puntos": 12.5,
    "puntosNormales": 9.0,
    "puntosGlass": 2.5,
    "puntosPulidos": 1.0,
    "nNormales": 8,
    "nGlass": 5,
    "nPulidos": 4,
    "nSinPiezas": 2,
    "nImeis": 6,
    "puntosJornada": 10.5,
    "nImeisJornada": 5
  }
]
```
```
(El bloque ```json va dentro de la sección, como en el resto del documento.)

- [ ] **Step 4: Compilar, suite completa y arranque de contexto**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q clean test 2>&1 | tail -20
```
Esperado: sin errores ni fallos. No se han añadido beans ni constructores a componentes Spring (el DAO conserva su constructor), así que el wiring no cambia; si `mvn -q package -DskipTests` compila, el arranque queda cubierto por el smoke en preproducción de la Task 8.

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q package -DskipTests 2>&1 | tail -5 && ls target/*.jar
```
Esperado: un jar en `target/`.

- [ ] **Step 5: Commit (submódulo)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && git add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java docs/api_contract.md && git commit -q -m "feat(estadisticas): hora de cierre completa en Madrid para fecha del periodo y clasificacion en jornada; contrato con puntosJornada/nImeisJornada" && git log --oneline -3
```

---

### Task 4: Cliente — modelo y helpers de jornada + tooltip con extra

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/PuntoEstadisticaPuntos.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java:136-140` (`textoTooltip`) y añadir helpers
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java`

**Interfaces:**
- Produces: `PuntoEstadisticaPuntos.getPuntosJornada() : Double`, `getnImeisJornada() : Integer`, constructor de 13 args `(String nombreTecnico, String periodo, double puntos, double puntosNormales, double puntosGlass, double puntosPulidos, int nNormales, int nGlass, int nPulidos, int nSinPiezas, Integer nImeis, Double puntosJornada, Integer nImeisJornada)`; `PuntosEstadistica.puntosJornada(PuntoEstadisticaPuntos) : double`, `puntosExtra(PuntoEstadisticaPuntos) : double`, `imeisJornada(PuntoEstadisticaPuntos) : int`, `textoTooltip(String periodo, double valor, boolean porDia, int trabajos, double extra) : String` (el de 4 args se conserva).

- [ ] **Step 1: Tests que fallan**

En `PuntosEstadisticaTest`, sustituir el helper `fila` del final por estos dos helpers y añadir los tests nuevos encima de ellos:

```java
    // ── horario: lo de fuera suma, no promedia (spec 2026-09-08) ─────────────
    @Test void puntosJornadaYExtraConServidorNuevoYViejo() {
        PuntoEstadisticaPuntos nuevo = filaHorario("Alex", "2026-08-28", 38.5, 26.5, 14, 9);
        assertEquals(26.5, PuntosEstadistica.puntosJornada(nuevo), 0.001);
        assertEquals(12.0, PuntosEstadistica.puntosExtra(nuevo), 0.001);
        assertEquals(9, PuntosEstadistica.imeisJornada(nuevo));

        PuntoEstadisticaPuntos viejo = fila("Alex", "2026-08-28", 38.5);   // sin campos: como 0.16.2
        assertEquals(38.5, PuntosEstadistica.puntosJornada(viejo), 0.001);
        assertEquals(0.0, PuntosEstadistica.puntosExtra(viejo), 0.001);
        assertEquals(0, PuntosEstadistica.imeisJornada(viejo));
    }

    @Test void imeisJornadaCaeANImeisSiFaltaSoloElCampoNuevo() {
        PuntoEstadisticaPuntos p = new PuntoEstadisticaPuntos("Alex", "2026-08-28",
                10, 10, 0, 0, 1, 0, 0, 0, 7, null, null);
        assertEquals(7, PuntosEstadistica.imeisJornada(p));
    }

    @Test void tooltipConExtraAnadeTerceraLineaSiempreEnPuntos() {
        assertEquals("2026-08-28\n38,5 puntos · 14 trabajos\n12,0 puntos fuera de horario",
                PuntosEstadistica.textoTooltip("2026-08-28", 38.5, false, 14, 12.0));
        assertEquals("2026-08-28\n38,5 puntos/día\n12,0 puntos fuera de horario",
                PuntosEstadistica.textoTooltip("2026-08-28", 38.5, true, 14, 12.0));
        assertEquals("2026-08-28\n38,5 puntos · 14 trabajos",
                PuntosEstadistica.textoTooltip("2026-08-28", 38.5, false, 14, 0));
    }

    private static PuntoEstadisticaPuntos fila(String tec, String periodo, double puntos) {
        return new PuntoEstadisticaPuntos(tec, periodo, puntos, puntos, 0, 0, 1, 0, 0, 0);
    }

    /** Fila de un servidor con horario: total, parte en jornada, IMEIs e IMEIs en jornada. */
    private static PuntoEstadisticaPuntos filaHorario(String tec, String periodo, double puntos,
                                                      double jornada, int nImeis, int nImeisJornada) {
        return new PuntoEstadisticaPuntos(tec, periodo, puntos, puntos, 0, 0, 1, 0, 0, 0,
                nImeis, jornada, nImeisJornada);
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test -Dtest=PuntosEstadisticaTest 2>&1 | tail -15
```
Esperado: error de compilación (constructor de 13 args y helpers inexistentes).

- [ ] **Step 3: Modelo del cliente**

Reemplazar el contenido de `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/PuntoEstadisticaPuntos.java`:

```java
package com.reparaciones.models;

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
    private Integer nImeis;        // aditivo 2026-09-04; null si el servidor aún no lo envía
    private Double  puntosJornada; // aditivo 2026-09-08: cerrados en horario; null con servidor antiguo
    private Integer nImeisJornada; // aditivo 2026-09-08: IMEIs con algún cierre en horario; null con servidor antiguo

    public PuntoEstadisticaPuntos() {}

    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas) {
        this(nombreTecnico, periodo, puntos, puntosNormales, puntosGlass, puntosPulidos,
             nNormales, nGlass, nPulidos, nSinPiezas, null, null, null);
    }

    /** Constructor completo (tests): los tres últimos pueden ser null como con un servidor antiguo. */
    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas,
                                  Integer nImeis, Double puntosJornada, Integer nImeisJornada) {
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
        this.nImeis = nImeis;
        this.puntosJornada = puntosJornada;
        this.nImeisJornada = nImeisJornada;
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

    /** IMEIs distintos del técnico en el periodo; {@code null} contra un servidor sin el campo. */
    public Integer getnImeis()        { return nImeis; }

    /** Puntos cerrados en horario (spec 2026-09-08); {@code null} contra un servidor sin el campo. */
    public Double  getPuntosJornada() { return puntosJornada; }

    /** IMEIs con algún cierre en horario (spec 2026-09-08); {@code null} contra un servidor sin el campo. */
    public Integer getnImeisJornada() { return nImeisJornada; }
}
```

- [ ] **Step 4: Helpers y tooltip en `PuntosEstadistica`**

Sustituir el método `textoTooltip` (líneas 136-140) por este bloque:

```java
    public static String textoTooltip(String periodo, double valor, boolean porDia, int trabajos) {
        return porDia
                ? periodo + "\n" + formatearPuntos(valor) + " puntos/día"
                : periodo + "\n" + formatearPuntos(valor) + " puntos · " + trabajos + " trabajos";
    }

    /** Como el anterior, con tercera línea "X puntos fuera de horario" si hay extra (siempre en
     *  puntos, también con la métrica Puntos/día — spec 2026-09-08 §4). */
    public static String textoTooltip(String periodo, double valor, boolean porDia, int trabajos, double extra) {
        String base = textoTooltip(periodo, valor, porDia, trabajos);
        return extra > 0 ? base + "\n" + formatearPuntos(extra) + " puntos fuera de horario" : base;
    }

    // ── Horario: lo de fuera suma, no promedia (spec 2026-09-08) ─────────────

    /** Puntos cerrados en horario, la base de TODAS las medias. Con un servidor sin el campo,
     *  todos los puntos (comportamiento 0.16.2). */
    public static double puntosJornada(PuntoEstadisticaPuntos p) {
        return p.getPuntosJornada() != null ? p.getPuntosJornada() : p.getPuntos();
    }

    /** Puntos fuera de horario del punto (0 con servidor antiguo). */
    public static double puntosExtra(PuntoEstadisticaPuntos p) {
        return Math.max(0, p.getPuntos() - puntosJornada(p));
    }

    /** IMEIs con algún cierre en horario; sin el campo, los IMEIs del periodo (0 si tampoco). */
    public static int imeisJornada(PuntoEstadisticaPuntos p) {
        if (p.getnImeisJornada() != null) return p.getnImeisJornada();
        return p.getnImeis() != null ? p.getnImeis() : 0;
    }
```

- [ ] **Step 5: Ejecutar y ver que pasa**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test -Dtest=PuntosEstadisticaTest 2>&1 | tail -15
```
Esperado: sin fallos.

- [ ] **Step 6: Commit (raíz)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones" && git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/PuntoEstadisticaPuntos.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java && git commit -q -m "feat(estadisticas): puntosJornada/nImeisJornada en el modelo, helpers de jornada y extra, tooltip con puntos fuera de horario" && git log --oneline -1
```

---

### Task 5: Cliente — tarjetas: referencias solo con puntos de jornada

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java` (`calcularTarjetas`, actualmente líneas 201-265, y su javadoc)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java`

**Interfaces:**
- Consumes: `puntosJornada(p)` y el helper de test `filaHorario` (Task 4).
- Produces: misma firma `calcularTarjetas(List<PuntoEstadisticaPuntos>, YearMonth, LocalDate, String, Set<String>) : Tarjetas`; `puntos`, `puntosHoy`, `puntosAnterior` siguen siendo totales; `objetivoHoy` y la media global de respaldo salen solo de puntos de jornada.

- [ ] **Step 1: Tests que fallan**

Añadir en `PuntosEstadisticaTest`, justo antes del helper `fila`:

```java
    @Test void referenciasDeLasTarjetasUsanSoloPuntosDeJornada() {
        // agosto: lunes 3 → 100 (80 en jornada), sábado 8 → 20 (0 en jornada); hoy lunes 7/09
        // → objetivo = media de los lunes EN JORNADA = 80; el total de agosto sigue siendo 120
        List<PuntoEstadisticaPuntos> filas = List.of(
                filaHorario("Marcos", "2026-08-03", 100, 80, 5, 4),
                filaHorario("Marcos", "2026-08-08", 20, 0, 1, 0),
                filaHorario("Marcos", "2026-09-07", 40, 40, 3, 3));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 7), null, Set.of());
        assertEquals(120.0, t.puntosAnterior(), 0.001);
        assertEquals(40.0, t.puntosHoy(), 0.001);
        assertEquals(80.0, t.objetivoHoy(), 0.001);
        assertEquals(50, t.pctHoy());
    }

    @Test void mediaGlobalDeRespaldoIgnoraLosDiasSoloConExtra() {
        // agosto: lunes 3 → 100 (80 en jornada), martes 4 → 30 (todo extra); hoy miércoles 2/09
        // sin muestras de miércoles → media global por día trabajado = 80 / 1 (el martes no es día trabajado)
        List<PuntoEstadisticaPuntos> filas = List.of(
                filaHorario("Marcos", "2026-08-03", 100, 80, 5, 4),
                filaHorario("Marcos", "2026-08-04", 30, 0, 2, 0),
                filaHorario("Marcos", "2026-09-02", 50, 50, 3, 3));
        PuntosEstadistica.Tarjetas t = PuntosEstadistica.calcularTarjetas(
                filas, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 2), null, Set.of());
        assertEquals(130.0, t.puntosAnterior(), 0.001);
        assertEquals(80.0, t.objetivoHoy(), 0.001);
        assertEquals(62, t.pctHoy());   // 50/80 = 62,5% truncado
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test -Dtest=PuntosEstadisticaTest 2>&1 | grep -E "Tests run|FAIL|expected" | head -10
```
Esperado: los dos tests nuevos fallan (`objetivoHoy` sale 100 en vez de 80 en el primero; en el segundo sale 65 en vez de 80).

- [ ] **Step 3: Reescribir el cuerpo de `calcularTarjetas`**

Sustituir el cuerpo del método (desde `List<PuntoEstadisticaPuntos> filas = tecnicoONull == null` hasta el `return new Tarjetas(...)` inclusive) por:

```java
        List<PuntoEstadisticaPuntos> filas = tecnicoONull == null
                ? sinExcluidos(filasDiarias, excluidos) : filasDiarias;
        YearMonth anterior = mesActual.minusMonths(1);

        // Por día de calendario: el total (suma todo) y la parte en jornada (base de las medias).
        // Lo de fuera de horario suma, no promedia (spec 2026-09-08); el finde es su caso extremo.
        Map<LocalDate, Double> porDia = new java.util.HashMap<>();
        Map<LocalDate, Double> porDiaJornada = new java.util.HashMap<>();
        for (PuntoEstadisticaPuntos f : filas) {
            if (tecnicoONull != null && !tecnicoONull.equals(f.getNombreTecnico())) continue;
            LocalDate dia = LocalDate.parse(f.getPeriodo());
            porDia.merge(dia, f.getPuntos(), Double::sum);
            porDiaJornada.merge(dia, puntosJornada(f), Double::sum);
        }

        double puntosActual = 0, puntosAnterior = 0;
        for (var e : porDia.entrySet()) {
            YearMonth ym = YearMonth.from(e.getKey());
            if (ym.equals(mesActual))     puntosActual   += e.getValue();
            else if (ym.equals(anterior)) puntosAnterior += e.getValue();
        }

        // Referencias del mes anterior, solo con puntos de jornada: un día cuenta como trabajado
        // si tiene jornada > 0. La media global de respaldo además solo mira L–V (con servidor
        // antiguo, jornada = total y el filtro L–V sostiene la regla del finde de 0.16.2).
        double puntosJornadaAnterior = 0;
        int diasTrabajadosAnterior = 0;
        Map<DayOfWeek, double[]> porDiaSemana = new java.util.EnumMap<>(DayOfWeek.class); // [suma, n]
        for (var e : porDiaJornada.entrySet()) {
            if (!YearMonth.from(e.getKey()).equals(anterior) || e.getValue() <= 0) continue;
            DayOfWeek dow = e.getKey().getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                puntosJornadaAnterior += e.getValue();
                diasTrabajadosAnterior++;
            }
            double[] acc = porDiaSemana.computeIfAbsent(dow, k -> new double[2]);
            acc[0] += e.getValue();
            acc[1]++;
        }

        double puntosHoy = porDia.getOrDefault(hoy, 0.0);

        Integer pctPuntos = null, pctHoy = null;
        Double totalAnterior = null, objetivoHoy = null;
        if (puntosAnterior > 0) {
            pctPuntos = (int) Math.floor(puntosActual / puntosAnterior * 100 + 1e-9);
            totalAnterior = puntosAnterior;

            DayOfWeek diaHoy = hoy.getDayOfWeek();
            double[] acc = porDiaSemana.get(diaHoy);
            boolean finde = diaHoy == DayOfWeek.SATURDAY || diaHoy == DayOfWeek.SUNDAY;
            if (acc != null && acc[1] > 0) {
                objetivoHoy = acc[0] / acc[1];
            } else if (!finde && diasTrabajadosAnterior > 0) {
                objetivoHoy = puntosJornadaAnterior / diasTrabajadosAnterior; // media global por día trabajado
            }
            if (objetivoHoy != null && objetivoHoy > 0)
                pctHoy = (int) Math.floor(puntosHoy / objetivoHoy * 100 + 1e-9);
            else
                objetivoHoy = null;
        }
        Locale es = new Locale("es", "ES");
        return new Tarjetas(
                mesActual.getMonth().getDisplayName(TextStyle.FULL, es),
                anterior.getMonth().getDisplayName(TextStyle.FULL, es),
                hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, es),
                puntosActual, puntosHoy, pctPuntos, totalAnterior, pctHoy, objetivoHoy);
```

En el javadoc del método, sustituir la frase `global por día trabajado (solo L–V en el divisor: el finde suma, no promedia);` por `global por día trabajado. Las dos referencias se calculan solo con puntos de jornada (lo de fuera de horario suma, no promedia — spec 2026-09-08; el finde es su caso extremo);`.

- [ ] **Step 4: Ejecutar toda la suite del cliente**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test 2>&1 | tail -20
```
Esperado: sin fallos. En particular siguen verdes `mediaGlobalPorDiaTrabajadoDeLasTarjetasIgnoraElFinde` (servidor viejo: sábado con jornada = total, excluido por el filtro L–V → objetivo 100) y `tarjetasConMesAnteriorACeroNoDanLinea` (día a 0 ya no cuenta como trabajado; sin línea igualmente).

- [ ] **Step 5: Commit (raíz)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones" && git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PuntosEstadistica.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PuntosEstadisticaTest.java && git commit -q -m "feat(estadisticas): las referencias de las tarjetas (dia de semana y media global) solo con puntos de jornada" && git log --oneline -1
```

---

### Task 6: Cliente — el controlador usa jornada en todas las medias y extra en tooltips

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`: `valorDe` (:459-465), IMEIs típicos en `renderVentana` (:642-647), `ambitoReferencia` (:715-722), `promedioVentanaActual` (:729-738), `dibujarLineasMedia` (:756-775 y el bloque Por encima/Por debajo :862-880), `trabajosDe` (:937-946), tooltip en `aplicarColores` (:972-973).

**Interfaces:**
- Consumes: `PuntosEstadistica.puntosJornada`, `puntosExtra`, `imeisJornada`, `textoTooltip` de 5 args (Task 4).
- Produces: nada para otras tareas (UI). Sin test JUnit (JavaFX); verificación = compilación + suite + smoke (Task 8).

Antes de editar, ejecutar `codegraph explore "valorDe promedioVentanaActual dibujarLineasMedia ambitoReferencia trabajosDe aplicarColores"` para trabajar sobre los números de línea actuales.

- [ ] **Step 1: `valorJornadaDe` y `servidorConJornada` junto a `valorDe`**

Justo después del método `valorDe` (línea 465) añadir:

```java
    /** Como {@link #valorDe}, pero solo con los puntos cerrados en horario: alimenta TODAS las
     *  medias (Promedio, x̄, Por encima/Por debajo, referencia de IMEIs). Lo de fuera de horario
     *  suma en el punto del gráfico pero no promedia (spec 2026-09-08). */
    private double valorJornadaDe(PuntoEstadisticaPuntos p) {
        double jornada = PuntosEstadistica.puntosJornada(p);
        return metricaPorDia()
                ? PuntosEstadistica.puntosDia(jornada, p.getPeriodo(),
                        cmbGranularidad.getValue(), java.time.LocalDate.now())
                : jornada;
    }

    /** true si el servidor envía puntosJornada; con uno antiguo la vista se comporta como la 0.16.2. */
    private boolean servidorConJornada() {
        return todosPuntos.stream().anyMatch(p -> p.getPuntosJornada() != null);
    }
```

- [ ] **Step 2: Promedio del equipo**

En `promedioVentanaActual`, cambiar `.put(p.getPeriodo(), valorDe(p));` por `.put(p.getPeriodo(), valorJornadaDe(p));` y el comentario final `// el finde suma, no promedia` por `// lo de fuera de horario (y el finde) suma, no promedia`.

- [ ] **Step 3: Líneas x̄ (Equipo y por técnico)**

En `dibujarLineasMedia`:
- En el comentario del bloque "Precomputar suma total por periodo", sustituir `Sin fin de semana en Día: el finde suma en los totales pero no promedia (spec 2026-09-07).` por `Solo puntos de jornada: lo de fuera de horario (y el finde) suma en los totales pero no promedia (spec 2026-09-08); un periodo sin jornada no cuenta como trabajado.`
- `sumaPorPeriodo.merge(p.getPeriodo(), valorDe(p), Double::sum);` → `sumaPorPeriodo.merge(p.getPeriodo(), valorJornadaDe(p), Double::sum);`
- Media de "Equipo": `media = sumaPorPeriodo.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);` → `media = sumaPorPeriodo.values().stream().mapToDouble(Double::doubleValue).filter(v -> v > 0).average().orElse(0);`
- Media por técnico: sustituir

```java
                media = todosPuntos.stream()
                        .filter(p -> p.getNombreTecnico().equals(serie.getName())
                                  && refLaborable.contains(p.getPeriodo()))
                        .mapToDouble(this::valorDe)
                        .average().orElse(0);
```
por
```java
                media = todosPuntos.stream()
                        .filter(p -> p.getNombreTecnico().equals(serie.getName())
                                  && refLaborable.contains(p.getPeriodo()))
                        .mapToDouble(this::valorJornadaDe)
                        .filter(v -> v > 0)   // periodo trabajado = con puntos de jornada
                        .average().orElse(0);
```

- [ ] **Step 4: Por encima / Por debajo**

En el mismo método, en los dos lambdas de `porEncima` y `porDebajo`, sustituir

```java
                        double media = todosPuntos.stream()
                                .filter(p -> p.getNombreTecnico().equals(nombre)
                                          && periodosReferencia.contains(p.getPeriodo()))
                                .mapToDouble(this::valorDe)
                                .average().orElse(0);
```
por (en ambos)
```java
                        double media = todosPuntos.stream()
                                .filter(p -> p.getNombreTecnico().equals(nombre)
                                          && refLaborable.contains(p.getPeriodo()))
                                .mapToDouble(this::valorJornadaDe)
                                .filter(v -> v > 0)
                                .average().orElse(0);
```
(`refLaborable` ya está declarada al principio de `dibujarLineasMedia`; así el "Por encima/Por debajo" usa exactamente la misma media que la línea x̄ de cada serie.)

- [ ] **Step 5: IMEIs típicos con IMEIs de jornada**

En `renderVentana`, en el bloque `if (servidorConImeis) {`, sustituir

```java
                if (p.getnImeis() != null)
                    datosImeis.computeIfAbsent(p.getNombreTecnico(), k -> new java.util.HashMap<>())
                              .put(p.getPeriodo(), p.getnImeis().doubleValue());
            mediaImeisTmp = PuntosEstadistica.promedioVentana(datosImeis, referenciaLaborable());   // sin finde (spec 2026-09-07)
```
por
```java
                if (p.getnImeis() != null)
                    datosImeis.computeIfAbsent(p.getNombreTecnico(), k -> new java.util.HashMap<>())
                              .put(p.getPeriodo(), (double) PuntosEstadistica.imeisJornada(p));
            mediaImeisTmp = PuntosEstadistica.promedioVentana(datosImeis, referenciaLaborable());   // solo IMEIs en horario (spec 2026-09-08)
```
(Los chips "N IMEIs" del último periodo siguen con `getnImeis()`: son un total.)

- [ ] **Step 6: `extraDe` y el tooltip del punto**

Justo después de `trabajosDe` añadir:

```java
    /** Puntos fuera de horario de un técnico (o "Equipo": técnicos que cuentan) en un periodo;
     *  0 con un servidor antiguo (spec 2026-09-08 §4). */
    private double extraDe(String tecnico, String periodo) {
        boolean esEquipo = "Equipo".equals(tecnico);
        List<PuntoEstadisticaPuntos> base = esEquipo
                ? PuntosEstadistica.sinExcluidos(todosPuntos, nombresExcluidos)
                : todosPuntos;
        return base.stream()
                .filter(p -> p.getPeriodo().equals(periodo)
                        && (esEquipo || p.getNombreTecnico().equals(tecnico)))
                .mapToDouble(PuntosEstadistica::puntosExtra).sum();
    }
```

En `aplicarColores`, sustituir

```java
                Tooltip tip = new Tooltip(PuntosEstadistica.textoTooltip(d.getXValue(), d.getYValue().doubleValue(),
                        metricaPorDia(), trabajosDe(serie.getName(), d.getXValue())));
```
por
```java
                Tooltip tip = new Tooltip(PuntosEstadistica.textoTooltip(d.getXValue(), d.getYValue().doubleValue(),
                        metricaPorDia(), trabajosDe(serie.getName(), d.getXValue()),
                        extraDe(serie.getName(), d.getXValue())));
```

- [ ] **Step 7: `ambitoReferencia` con "en horario"**

Sustituir el método (con su javadoc) por:

```java
    /** Ámbito de las varas (Promedio, x̄ y Por encima/Por debajo): filtro de fechas o última ventana.
     *  Coletilla "en horario" en todas las granularidades cuando el servidor envía puntosJornada
     *  (lo de fuera de horario suma, no promedia — spec 2026-09-08); con servidor antiguo, "L–V"
     *  solo en Día (regla del finde de 0.16.2, spec 2026-09-07). */
    private String ambitoReferencia() {
        String base = (dpDesde.getValue() != null || dpHasta.getValue() != null)
                ? "rango filtrado"
                : PuntosEstadistica.etiquetaVentana(periodosReferencia.size(), cmbGranularidad.getValue());
        if (servidorConJornada()) return base + ", en horario";
        return "Día".equals(cmbGranularidad.getValue()) ? base + ", L–V" : base;
    }
```

- [ ] **Step 8: Comprobar que no queda ninguna media sobre `valorDe`**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones" && grep -n "valorDe\b\|this::valorDe" gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java
```
Esperado: `valorDe` solo en su definición, en la construcción de series (`valorPorTecnico`), en la serie Equipo (`sumaPorPeriodo` de `renderVentana`) y en el cálculo de `maxVisible` del eje Y. Ninguna aparición en `promedioVentanaActual` ni en `dibujarLineasMedia`.

- [ ] **Step 9: Compilar y suite completa**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; cd "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test 2>&1 | tail -20
```
Esperado: sin errores de compilación ni fallos.

- [ ] **Step 10: Commit (raíz)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones" && git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java && git commit -q -m "feat(estadisticas): Promedio, medias por tecnico, Por encima/Por debajo e IMEIs tipicos solo con jornada; tooltip con puntos fuera de horario y ambito 'en horario'" && git log --oneline -1
```

---

### Task 7: Documentación — diccionario de métricas y CHANGELOG

**Files:**
- Modify: `gestion-reparaciones-cliente/docs/metricas-estadisticas.md`
- Modify: `CHANGELOG.md` (raíz, sección `[Unreleased]`)

- [ ] **Step 1: Sección nueva 2b en el diccionario**

Insertar, entre el final de la sección `## 2. Puntos de un trabajo` y `## 3. Puntos/día (la métrica normalizada)`:

```markdown
## 2b. Horario: puntos de jornada y puntos extra

**Lo de fuera de horario suma, no promedia** (decisión 2026-09-08; generaliza la regla del fin de semana del 2026-09-07). Cada trabajo terminado se clasifica por su hora de cierre **en hora de Madrid**:

| Día | Franja de jornada | Con margen de 15 min |
|---|---|---|
| Lunes, Martes | 8:30–18:00 | 8:15–18:15 |
| Miércoles, Jueves | 8:30–17:00 | 8:15–17:15 |
| Viernes | 8:30–14:30 | 8:15–14:45 |
| Sábado, Domingo | sin jornada | todo es extra |

- **Puntos de jornada** = cerrados dentro de la franja con margen (extremos incluidos). **Puntos extra** = el resto. Solo cuentan entrada y salida: la media hora de comida de L–J no tiene hora fija y no se descuenta. El margen absorbe el "cerré el último antes de irme" (18:03 no es hora extra).
- **Qué suma todo** (jornada + extra): el punto del gráfico (series por técnico y Equipo), tarjetas "Puntos · mes" y "Puntos · hoy", total del mes anterior de la tarjeta del mes, numerador de Puntos/día, chips "N IMEIs", popover de desglose.
- **Qué usa solo jornada**: todas las medias — Promedio (§6), x̄ por serie y Por encima/Por debajo (§7), IMEIs típicos (§5, con IMEIs que tienen algún cierre en horario) y las dos referencias de las tarjetas (§4). Un (técnico, periodo) cuenta como **trabajado** solo si tiene puntos de jornada > 0; un día en el que alguien solo cerró cosas fuera de horario no le cuenta como día trabajado (pero sigue en el eje X como "día con actividad").
- **En todas las granularidades**: en Semana, Mes y Año la media tampoco incluye el sábado ni las horas extra (hasta 0.16.2 en Semana el sábado sí entraba). Consecuencia buscada: quien hace muchas horas extra queda por encima de su propia x̄; el tooltip del punto lo explica ("`X` puntos fuera de horario") y el de las varas dice "en horario".
- **Dónde se decide**: en el servidor (`util/Jornada.java`, constantes con procedencia; `puntosJornada` y `nImeisJornada` en cada fila del endpoint). La fecha del punto también es la de Madrid (antes UTC). Con un servidor antiguo el cliente se comporta como la 0.16.2.
- Caso de calibración: Alex, 28 de agosto de 2026 (38,5 puntos con horas extra): el punto se queda en 38,5, su x̄ y el Promedio bajan.
```

- [ ] **Step 2: Retoques en las secciones existentes**

Aplicar estas sustituciones exactas en `metricas-estadisticas.md`:

1. En §3, el bullet que empieza por `- **Fin de semana: suma, no promedia** (2026-09-07).` — sustituir el bullet completo por:
   `- **Fin de semana y horas extra: suman, no promedian** (2026-09-07 → generalizado 2026-09-08, §2b). Sus puntos entran en todos los totales (tarjetas, semana, mes, año, Equipo, numerador de Puntos/día) pero en ninguna media, en ninguna granularidad. El punto del sábado sigue visible en el gráfico y cuenta como uno de los "días con actividad" de la ventana.`
2. En §4, el bullet `- Día de semana sin muestras el mes anterior → media global por día trabajado del mes anterior, **calculada solo con los días L–V (puntos L–V ÷ días L–V trabajados)**; el total del mes anterior de la tarjeta del mes sí incluye el finde (el finde suma, no promedia — §3).` → `- Las dos referencias (media por día de semana y media global por día trabajado) se calculan **solo con puntos de jornada** (§2b): un día cuenta como trabajado si tiene jornada > 0, y la media global además solo mira L–V. El total del mes anterior de la tarjeta del mes sí incluye finde y extra.`
3. En §5, en el bullet de IMEIs, sustituir `(misma fórmula que el Promedio de puntos, sin excluidos y sin fin de semana en Día — §3;` por `(misma fórmula que el Promedio de puntos, sin excluidos y solo con IMEIs que tienen algún cierre en horario — §2b;` y `"12,4 · por día trabajado · 30 días con actividad"` por `"12,4 · por día trabajado · 30 días con actividad, en horario"`.
4. En §6, sustituir el bullet `- El tooltip dice el ámbito: "Promedio del equipo (30 días con actividad, L–V): 12,5 puntos" / "(rango filtrado, L–V)". En Día los **sábados y domingos no cuentan como técnico-periodo trabajado** (el finde suma, no promedia — §3); la media por técnico (§7) sigue la misma regla.` por `- El tooltip dice el ámbito: "Promedio del equipo (30 días con actividad, en horario): 12,5 puntos" / "(rango filtrado, en horario)". La media se calcula **solo con puntos de jornada** y un técnico-periodo cuenta como trabajado solo si tiene jornada > 0 (§2b); la media por técnico (§7) sigue la misma regla.`
5. En §7, al final del primer párrafo (`… comparada contra ella. Estable al navegar (habla del rango de referencia, no del tramo en pantalla).`) añadir la frase ` Solo puntos de jornada (§2b); un técnico con muchas horas extra queda por encima de su propia x̄, y su punto del gráfico (que suma todo) por encima de la línea.`
6. En §9, añadir al final de la lista: `- Horario fijo en constante del servidor (`Jornada`): sin festivos, sin jornadas reducidas puntuales ni horario por técnico. Tabla configurable → candidata a la analítica web (F4).`
7. En §10, añadir dos filas a la tabla: `| Horario del taller, margen, cierre en Madrid | servidor `util/Jornada.java` (+ `JornadaTest`) |` y `| Puntos/IMEIs de jornada y extra, tooltip con extra | cliente `utils/PuntosEstadistica.java` (`puntosJornada`, `puntosExtra`, `imeisJornada`) |`. Y en la fila del endpoint, sustituir `(devuelve a todos; el filtrado de exclusión es del cliente)` por `(devuelve a todos con `puntosJornada`/`nImeisJornada`; el filtrado de exclusión es del cliente)`.

- [ ] **Step 3: CHANGELOG**

En `CHANGELOG.md` (raíz), bajo `## [Unreleased]` añadir:

```markdown
### Changed
- **Estadísticas: lo de fuera de horario suma, no promedia.** Generaliza la regla del fin de semana a las horas extra de cualquier día, en las cuatro granularidades: los puntos cerrados fuera del horario del taller (8:30–18:00 L-M, 17:00 X-J, 14:30 V, con 15 min de margen) siguen sumando en gráfico, tarjetas y totales, pero el Promedio, la media de cada técnico, Por encima/Por debajo, IMEIs típicos y la referencia de las tarjetas se calculan solo con lo cerrado en horario. El tooltip del punto añade "X puntos fuera de horario" y las varas dicen "en horario". Requiere servidor con `puntosJornada`; con uno antiguo la vista se comporta como la 0.16.2.

### Fixed
- Estadísticas por puntos: la fecha de cada cierre se toma en hora de Madrid (antes en UTC, con lo que un cierre entre las 00:00 y las 02:00 de verano caía en el día anterior).
```

- [ ] **Step 4: Revisión visual rápida y commit (raíz)**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones" && grep -n "2b\|en horario\|fuera de horario" gestion-reparaciones-cliente/docs/metricas-estadisticas.md | head -20 && git add gestion-reparaciones-cliente/docs/metricas-estadisticas.md CHANGELOG.md && git commit -q -m "docs(estadisticas): diccionario con horario y puntos de jornada (seccion 2b) y CHANGELOG" && git log --oneline -1
```
Esperado: al menos una coincidencia por cada sección tocada (2b, 3, 4, 5, 6, 7, 9, 10).

---

### Task 8: Smoke en preproducción y cierre (usuario)

**Files:** ninguno (verificación manual).

Esta tarea la ejecuta el usuario; el agente prepara los comandos y **no** hace push, merge, tags ni toca el gitlink (memoria `feedback_merge_confirmacion`, `feedback_ssh_vm`).

- [ ] **Step 1: Estado final de las dos ramas**

```bash
cd "C:/Users/dev/Documents/ProgramaReparaciones" && git log --oneline hotfix/0.16.2..feature/estadisticas-horario && git -C gestion-reparaciones-servidor log --oneline main..feature/estadisticas-horario && git status --short
```
Esperado: 5 commits en la raíz (spec + Tasks 4-7), 3 en el servidor (Tasks 1-3), árbol limpio.

- [ ] **Step 2: Pedir al usuario el despliegue del servidor en preproducción**

El usuario pushea la rama del servidor y reconstruye el contenedor en la VM de preproducción (procedimiento habitual; el agente no hace SSH). Hasta entonces, el cliente contra el servidor viejo debe verse **idéntico a la 0.16.2** (comprobación de fallback: tooltips sin línea de extra, coletilla "L–V" solo en Día).

- [ ] **Step 3: Checklist de smoke con el servidor nuevo (admin)**

1. **Alex, 28 de agosto** (Día, filtro 2026-08-01 → 2026-08-31): el punto sigue en **38,5**; el tooltip tiene la tercera línea "`X` puntos fuera de horario" con X > 0; la x̄ de Alex y el Promedio son **menores** que en 0.16.2 y cuadran con recalcularlos a mano quitando los puntos extra de los días del rango.
2. Tooltips de Promedio, x̄ y tarjeta IMEIs dicen "**, en horario**" en Día, Semana, Mes y Año.
3. **Semana**: la semana del 28 suma todo (punto intacto), la x̄ de Alex baja respecto a 0.16.2.
4. **Tarjetas**: "Puntos · mes" y "Puntos · hoy" con las mismas cifras que antes; la línea de objetivo cambia (referencia recalculada solo con jornada).
5. Un cierre de prueba fuera de horario (o uno real de hoy después de las 18:15) aparece en el tooltip de hoy como extra y **no** mueve la x̄.
6. Un sábado con actividad sigue en el eje X y en "N días con actividad".
7. Rol TECNICO: ve solo lo suyo, con los mismos textos.
8. **Zona horaria** (premisa UTC): en la BD de preproducción ejecutar `SELECT ID_REP, FECHA_FIN, NOW(), @@session.time_zone FROM Reparacion WHERE FECHA_FIN IS NOT NULL ORDER BY FECHA_FIN DESC LIMIT 1;` y confirmar que `NOW()` va en UTC (dos horas menos que el reloj en verano) y que ese cierre aparece en el tooltip del día correcto y en el lado jornada/extra que le corresponde por su hora de Madrid. Si `NOW()` no fuera UTC, parar: la clasificación estaría desplazada dos horas.
9. **Por encima / Por debajo con servidor viejo**: en Día puede diferir de 0.16.2 (las listas usan ahora el mismo conjunto de periodos que las x̄, sin finde); es esperado, no un defecto.

- [ ] **Step 4: Cierre**

Con el smoke en verde, invocar `superpowers:finishing-a-development-branch` y presentar al usuario las opciones de integración (merge a `hotfix/0.16.2` → 0.16.3, o a `main` → 0.17.0; actualización del gitlink del submódulo al commit final del servidor; tag). **Esperar su decisión** antes de cualquier merge, push o tag.
