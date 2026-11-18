# Pedidos: «Descargar para buscar», fecha de salida e IMEI duplicado — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que Pedidos dé a logística la lista diaria de móviles a buscar (Excel por plataforma), enseñe hasta cuándo se puede enviar cada fila
(«Sale hoy», «Con retraso»…) y no deje poner un IMEI que ya está en otro pedido pendiente.

**Architecture:** Servidor: tabla `Calendario_festivo` y servicio `CalendarioLaboral`; la lectura de ShippyPro calcula `FECHA_SALIDA` y
`PAIS` de cada pedido pendiente (`FechaSalida`, puro) y los escribe en sus filas vivas; `FilaVista` los sirve; nueva ruta
`GET /api/pedidos/buscar` que genera el xlsx (`ExcelBuscar`, agregación pura + escritura con POI); `FilasDAO.ponerImei` rechaza el
duplicado dentro de su transacción. Web: la columna «Enviado» y sus filtros y contadores leen `fechaSalida`; botón «Descargar para
buscar»; scroll en la ayuda.

**Tech Stack:** Spring Boot 3 + JdbcTemplate + MariaDB, Apache POI (ya en el proyecto: `nsys/HojaImprimible`), JUnit 5 + Mockito;
React 19 + TypeScript + TanStack Query + openapi-fetch, Vitest + Testing Library + MSW.

**Spec:** `docs/superpowers/specs/2026-11-18-pedidos-buscar-y-salida-design.md` (repo raíz). Léela entera antes de empezar.

## Global Constraints

- Worktrees: servidor `C:/Users/dev/Documents/wt/servidor-pedidos-tabla`, web `C:/Users/dev/Documents/wt/web-pedidos-tabla`, rama
  `dev_pedidos_tabla` en los dos (servidor en `750c181`, web en `b777f42` al empezar). Nada en `main`.
- Commits en español **sin tildes ni eñes**, prefijos `feat:`/`fix:`/`test:`/`docs:`/`chore:`, **sin `Co-Authored-By`**. **Sin push, sin
  merge, sin tags**: eso lo autoriza Imad aparte.
- Toolchain servidor (Git Bash): `export JAVA_HOME=/c/Users/dev/tools/jdk-17* ; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-*/bin:$PATH"`
  (comprueba las rutas exactas con `ls /c/Users/dev/tools`). Tests: `mvn -q test -Dtest=Clase` y al final `mvn -q test` (la suite
  entera, ~2.280 tests, incluye `OpenApiContractTest`, que levanta el contexto y deja `target/openapi.json`).
- Web: `npm run test -- <fichero>`, al final `npm run check` (lint + typecheck + tests).
- Tipos de la API en la web: copiar `servidor/target/openapi.json` (tras `mvn test`) a `web/api/openapi.json` y `npm run api:types:offline`.
- Repos públicos: datos de prueba inventados (pedidos y IMEI con prefijo `0099`), nada real.
- Hora de la empresa: Madrid (`ImportadorNsys.MADRID` en el servidor; `hoyMadrid()` de `@/shared/lib/fechas` en la web).
- Ninguna petición puede pasar de 15 s.
- Textos de pantalla exactos: «Sale hoy», «Sale mañana», «Sale vie 20/11» (día abreviado en minúscula + dd/mm), «Con retraso»,
  «Envío parcial · falta por enviar · con retraso», «Descargar para buscar», «Para hoy», «Pueden esperar», «No hay móviles que buscar»,
  «Total general», mensaje del duplicado «Este IMEI ya está en el pedido X (fila del dd/mm)» y, en Amazon sin asociar, «Este IMEI ya
  está en una fila de AMZ del dd/mm sin asociar» (FNSK en Stockroom).

---

## Mapa de ficheros

Servidor (`src/main/java/com/reparaciones/servidor/pedidos/` salvo indicación):

| Fichero | Qué |
|---|---|
| `sql/migracion-pedidos-salida.sql` (nuevo) | `Calendario_festivo` + datos; `Pedido_fila.FECHA_SALIDA`, `Pedido_fila.PAIS` |
| `sql/crear_bd.sql`, `sql/migracion-pedidos-tabla.sql` | al día con lo anterior |
| `CalendarioLaboral.java` (nuevo) | festivos de la tabla (con caché) y cuentas de días laborables |
| `FechaSalida.java` (nuevo) | puro: fecha de salida de un pedido de ShippyPro |
| `PedidoShippyPro.java`, `ClienteShippyPro.java` | el pedido gana `estado` (`status`) y `servicio` (`shippingservice`) |
| `FilasDAO.java` | `ponerSalidas`, columnas nuevas en el SELECT y en `fila()`, consulta del Excel, duplicado en `ponerImei` |
| `FilaPedido.java`, `FilaVista.java`, `VistaFilas.java` | `fechaSalida` y `pais` |
| `LecturaFilas.java` | escribe las salidas en cada vuelta |
| `ImeiDuplicadoException.java` (nuevo) | lo lanza el DAO con los datos de la otra fila |
| `FilasController.java` | 409 del duplicado |
| `ExcelBuscar.java` (nuevo) | agregación pura + xlsx |
| `BuscarController.java` (nuevo) | `GET /api/pedidos/buscar` |
| `docs/api_contract.md` | rutas y campos nuevos |

Web (`src/modules/telefonos/` salvo indicación): `pedidos/salida.ts` (nuevo, puro), `pedidos/filas.ts`, `pedidos/TablaFilas.tsx`,
`pedidos/PedidosPage.tsx`, `pedidos/AyudaPedidos.tsx`, `pedidos/DescargarBuscar.tsx` (nuevo), `api.ts`, `api/openapi.json`,
`src/shared/api/schema.d.ts`, `test/fabrica.ts` (si hace falta el campo nuevo), `CHANGELOG.md` (entrada «Sin publicar» si existe ese
apartado; si no, nada: la versión la sube Imad al fusionar).

---

### Task 1: Calendario laboral (migración + servicio)

**Files:**
- Create: `sql/migracion-pedidos-salida.sql`
- Modify: `sql/crear_bd.sql`, `sql/migracion-pedidos-tabla.sql`
- Create: `src/main/java/com/reparaciones/servidor/pedidos/CalendarioLaboral.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/CalendarioLaboralTest.java`

**Interfaces:**
- Produces: `CalendarioLaboral` (`@Component`): `Set<LocalDate> festivos()`; `static boolean esLaborable(LocalDate d, Set<LocalDate> festivos)`;
  `static LocalDate sumarLaborables(LocalDate desde, int dias, Set<LocalDate> festivos)`.

- [ ] **Step 1: la migración**

```sql
-- Pedidos: calendario laboral (festivos de la Comunidad de Madrid), fecha de salida y pais de cada fila.
-- Aditiva y repetible. Spec 2026-11-18-pedidos-buscar-y-salida-design.md.
CREATE TABLE IF NOT EXISTS Calendario_festivo (
    FECHA  DATE         NOT NULL,
    NOMBRE VARCHAR(100) NOT NULL,
    AMBITO VARCHAR(20)  NOT NULL COMMENT 'COMUNIDAD (festivos de la Comunidad de Madrid); los locales no se cargan',
    PRIMARY KEY (FECHA)
);
INSERT IGNORE INTO Calendario_festivo (FECHA, NOMBRE, AMBITO) VALUES
 ('2026-12-07', 'Traslado del Dia de la Constitucion', 'COMUNIDAD'),
 ('2026-12-08', 'Inmaculada Concepcion', 'COMUNIDAD'),
 ('2026-12-25', 'Natividad del Senor', 'COMUNIDAD'),
 ('2027-01-01', 'Ano Nuevo', 'COMUNIDAD'),
 ('2027-01-06', 'Epifania del Senor', 'COMUNIDAD'),
 ('2027-03-19', 'San Jose', 'COMUNIDAD'),
 ('2027-03-25', 'Jueves Santo', 'COMUNIDAD'),
 ('2027-03-26', 'Viernes Santo', 'COMUNIDAD'),
 ('2027-05-01', 'Fiesta del Trabajo', 'COMUNIDAD'),
 ('2027-08-16', 'Traslado de la Asuncion de la Virgen', 'COMUNIDAD'),
 ('2027-10-12', 'Fiesta Nacional de Espana', 'COMUNIDAD'),
 ('2027-11-01', 'Todos los Santos', 'COMUNIDAD'),
 ('2027-12-06', 'Dia de la Constitucion Espanola', 'COMUNIDAD'),
 ('2027-12-08', 'Inmaculada Concepcion', 'COMUNIDAD'),
 ('2027-12-25', 'Natividad del Senor', 'COMUNIDAD');
ALTER TABLE Pedido_fila ADD COLUMN IF NOT EXISTS FECHA_SALIDA DATE NULL
    COMMENT 'Ultimo dia para enviar el pedido (Amazon: Latest ship de ShippyPro; webs: entrada + 2 o 3 dias laborables)';
ALTER TABLE Pedido_fila ADD COLUMN IF NOT EXISTS PAIS VARCHAR(2) NULL COMMENT 'Pais de destino (ISO de ShippyPro)';
SELECT COUNT(*) AS FESTIVOS FROM Calendario_festivo;
SELECT COUNT(*) AS COLUMNAS_NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('FECHA_SALIDA', 'PAIS');
```

En `crear_bd.sql` y `migracion-pedidos-tabla.sql`: la tabla `Calendario_festivo` (con el mismo `INSERT IGNORE`) y las dos columnas
dentro del `CREATE TABLE Pedido_fila` (detrás de `VALIDADA_USU`), siguiendo el estilo de las columnas que ya hay. Si existe un test
que compara anchos (`AnchosPedidosTest`) y lee el DDL, añade `PAIS = 2` en `AnchosPedidos` y comprueba que pasa.

- [ ] **Step 2: el test que falla**

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class CalendarioLaboralTest {
    private static final Set<LocalDate> FESTIVOS = Set.of(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));

    @Test
    void finesDeSemanaYFestivosNoSonLaborables() {
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 11, 18), FESTIVOS)).isTrue();  // miercoles
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 11, 21), FESTIVOS)).isFalse(); // sabado
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 11, 22), FESTIVOS)).isFalse(); // domingo
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 12, 8), FESTIVOS)).isFalse();  // festivo
    }

    @Test
    void sumarLaborablesNoCuentaElDiaDePartidaNiLosFinesDeSemana() {
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 16), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 18)); // lunes -> miercoles
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 20), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 24)); // viernes -> martes
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 21), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 24)); // sabado -> martes
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 20), 3, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 25)); // viernes -> miercoles
    }

    @Test
    void sumarLaborablesSaltaLosFestivos() {
        // viernes 4/12 + 2: lunes 7 y martes 8 son festivos -> miercoles 9 y jueves 10
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 12, 4), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 12, 10));
    }
}
```

- [ ] **Step 3: correrlo y verlo fallar** — `mvn -q test -Dtest=CalendarioLaboralTest` → no compila (falta la clase).

- [ ] **Step 4: la clase**

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Calendario laboral de la empresa (spec 2026-11-18 §1): de lunes a viernes y no en {@code Calendario_festivo} (festivos de la Comunidad
 *  de Madrid). Los festivos se leen de la base como mucho una vez por hora; si la lectura falla se siguen usando los ultimos leidos. */
@Component
public class CalendarioLaboral {

    private static final Logger log = LoggerFactory.getLogger(CalendarioLaboral.class);
    static final String SQL_FESTIVOS = "SELECT FECHA FROM Calendario_festivo";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private Set<LocalDate> festivos = Set.of();
    private LocalDateTime leidosEn;

    @Autowired
    public CalendarioLaboral(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ImportadorNsys.MADRID));
    }

    CalendarioLaboral(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public synchronized Set<LocalDate> festivos() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (leidosEn == null || ahora.isAfter(leidosEn.plusHours(1))) {
            try {
                festivos = Set.copyOf(new HashSet<>(jdbc.query(SQL_FESTIVOS, (rs, n) -> rs.getDate("FECHA").toLocalDate())));
                leidosEn = ahora;
            } catch (RuntimeException e) {
                log.warn("Calendario laboral: no se pudieron leer los festivos: {}", e.getClass().getSimpleName());
            }
        }
        return festivos;
    }

    public static boolean esLaborable(LocalDate d, Set<LocalDate> festivos) {
        DayOfWeek dia = d.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !festivos.contains(d);
    }

    /** El dia laborable numero {@code dias} despues de {@code desde} (sin contar {@code desde}). */
    public static LocalDate sumarLaborables(LocalDate desde, int dias, Set<LocalDate> festivos) {
        LocalDate d = desde;
        int faltan = dias;
        while (faltan > 0) {
            d = d.plusDays(1);
            if (esLaborable(d, festivos)) faltan--;
        }
        return d;
    }
}
```

Añade al test un caso de `festivos()` con un `JdbcTemplate` de Mockito: devuelve lo leído, no vuelve a consultar antes de una hora
(`Clock` fijo y luego avanzado 61 min → segunda consulta) y, si la consulta lanza, devuelve los anteriores.

- [ ] **Step 5: verde** — `mvn -q test -Dtest=CalendarioLaboralTest` → PASS.
- [ ] **Step 6: commit** — `git add sql/ src/ && git commit -m "feat: calendario laboral con los festivos de la Comunidad de Madrid (tabla Calendario_festivo)"`

---

### Task 2: Fecha de salida de un pedido de ShippyPro

**Files:**
- Modify: `PedidoShippyPro.java`, `ClienteShippyPro.java` (método `pedido(JsonNode)`)
- Create: `FechaSalida.java`
- Test: `FechaSalidaTest.java`; añadir un caso en `ClienteShippyProTest`

**Interfaces:**
- Consumes: `CalendarioLaboral.sumarLaborables` (Task 1), `Canal.esAmazon()/esWeb()`.
- Produces: `PedidoShippyPro(String numero, String marketplace, LocalDateTime fecha, String cliente, String pais, List<Articulo> articulos,
  String estado, String servicio)` con un constructor de 6 argumentos que pone `estado` y `servicio` a null (para no tocar los tests
  existentes); `static LocalDate FechaSalida.de(PedidoShippyPro p, Canal canal, Set<LocalDate> festivos)` (null si no se sabe).

- [ ] **Step 1: test que falla**

```java
class FechaSalidaTest {
    private static final Set<LocalDate> FESTIVOS = Set.of(LocalDate.of(2026, 12, 8));
    private static PedidoShippyPro pedido(String estado, String servicio, LocalDateTime fecha) {
        return new PedidoShippyPro("0099-1", "1", fecha, null, "ES", List.of(), estado, servicio);
    }

    @Test
    void enAmazonEsLaFechaDeLatestShip() {
        var p = pedido("Unshipped Latest ship: 2026-11-19", "Standard", LocalDateTime.of(2026, 11, 18, 9, 0));
        assertThat(FechaSalida.de(p, Canal.AMAZON, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(FechaSalida.de(p, Canal.AMAZON_STOCKROOM, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void enAmazonSinLatestShipONoValidaEsNula() {
        assertThat(FechaSalida.de(pedido("processing", null, LocalDateTime.of(2026, 11, 18, 9, 0)), Canal.AMAZON, FESTIVOS)).isNull();
        assertThat(FechaSalida.de(pedido(null, null, LocalDateTime.of(2026, 11, 18, 9, 0)), Canal.AMAZON, FESTIVOS)).isNull();
        assertThat(FechaSalida.de(pedido("Unshipped Latest ship: 2026-13-40", null, LocalDateTime.of(2026, 11, 18, 9, 0)), Canal.AMAZON, FESTIVOS)).isNull();
    }

    @Test
    void enLasWebsEsLaEntradaMasDosOTresDiasLaborablesSegunElEnvio() {
        LocalDateTime lunes = LocalDateTime.of(2026, 11, 16, 23, 30);
        assertThat(FechaSalida.de(pedido("processing", "Rápido (Entrega en 24/48 horas laborables)", lunes), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(FechaSalida.de(pedido("processing", "Envío gratis en 24/72 horas laborables", lunes), Canal.PARTICULARES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(FechaSalida.de(pedido("processing", "Recogida en tienda", lunes), Canal.BACKIPHONE, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 19)); // otro texto: 3 dias
        assertThat(FechaSalida.de(pedido("processing", null, lunes), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void enLasWebsSeSaltanLosFestivos() {
        // lunes 7/12 + 2 laborables: martes 8 festivo -> miercoles 9 y jueves 10
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 12, 7, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 12, 10));
    }
}
```

En `ClienteShippyProTest`: un pedido con `"status": "Unshipped Latest ship: 2026-11-19"` y `"shippingservice": "Standard"` sale con
esos dos valores en `estado()` y `servicio()`; sin ellos, null.

- [ ] **Step 2: verlo fallar** — `mvn -q test -Dtest=FechaSalidaTest` → no compila.

- [ ] **Step 3: implementar**

`PedidoShippyPro`: añade `String estado, String servicio` al final del record y un constructor con los 6 argumentos de antes que pasa
`null, null`. En `ClienteShippyPro.pedido(JsonNode)`, construye con `vacioANulo(o.path("status").asText(""))` y
`vacioANulo(o.path("shippingservice").asText(""))`. Actualiza el javadoc del record («sin dirección…»: el estado y el servicio no son
datos personales).

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ultimo dia para enviar un pedido (spec 2026-11-18 §2). Amazon: la fecha de «Latest ship» del estado de ShippyPro. Webs: el dia de
 *  entrada mas 2 dias laborables si el envio es de 24/48 h, o 3 en cualquier otro caso. Puro. */
public final class FechaSalida {

    private static final Pattern LATEST_SHIP = Pattern.compile("Latest ship:\\s*(\\d{4}-\\d{2}-\\d{2})");
    static final int DIAS_48 = 2;
    static final int DIAS_72 = 3;

    private FechaSalida() {}

    public static LocalDate de(PedidoShippyPro p, Canal canal, Set<LocalDate> festivos) {
        if (canal == null) return null;
        if (canal.esAmazon()) {
            if (p.estado() == null) return null;
            Matcher m = LATEST_SHIP.matcher(p.estado());
            if (!m.find()) return null;
            try {
                return LocalDate.parse(m.group(1));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        if (canal.esWeb()) {
            int dias = p.servicio() != null && p.servicio().contains("24/48") ? DIAS_48 : DIAS_72;
            return CalendarioLaboral.sumarLaborables(p.fecha().toLocalDate(), dias, festivos);
        }
        return null;
    }
}
```

- [ ] **Step 4: verde** — `mvn -q test -Dtest='FechaSalidaTest,ClienteShippyProTest'` → PASS.
- [ ] **Step 5: commit** — `git commit -m "feat: fecha de salida de un pedido de ShippyPro (Latest ship en Amazon, 48 o 72 h laborables en las webs)"`

---

### Task 3: La lectura guarda fecha de salida y país; la vista los sirve

**Files:**
- Modify: `FilasDAO.java`, `LecturaFilas.java`, `FilaPedido.java`, `FilaVista.java`, `VistaFilas.java`, `docs/api_contract.md`
- Test: `FilasDAOTest.java` (base real, como los demás de ese fichero), `LecturaFilasTest.java`, `VistaFilasTest.java`

**Interfaces:**
- Consumes: `FechaSalida.de` (Task 2), `CalendarioLaboral.festivos()` (Task 1), `SincroShippyPro.numeroGuardado(String)`.
- Produces:
  - `record FilasDAO.Salida(Canal canal, String pedido, LocalDate fechaSalida, String pais)`;
    `void FilasDAO.ponerSalidas(List<Salida> salidas)`.
  - `FilaPedido` gana al final `LocalDate fechaSalida, String pais` (el constructor canónico pasa de 23 a 25 argumentos; los
    constructores secundarios ponen null). `FilaVista` gana al final `@Schema(nullable = true) LocalDate fechaSalida` y
    `@Schema(nullable = true) String pais`.

- [ ] **Step 1: tests que fallan**
  - `FilasDAOTest.ponerSalidasSoloTocaLasVivasDelPedido`: tres filas del mismo canal y pedido (dos vivas, una cerrada) y una viva de
    otro pedido; `ponerSalidas(List.of(new Salida(Canal.AMAZON, "0099-1", 2026-11-19, "DE")))` → las dos vivas con esa fecha y `DE`,
    la cerrada y la del otro pedido sin tocar. Una segunda llamada con otra fecha la cambia (se recalcula en cada vuelta).
  - `FilasDAOTest.laFilaLeidaTraeFechaSalidaYPais` (por `porId`).
  - `LecturaFilasTest.cadaVueltaPoneLaFechaDeSalidaYElPaisDeLosPedidosPendientes`: con el `FilasDAO` de Mockito que ya usa ese test,
    un pedido de Amazon con `estado` «Unshipped Latest ship: 2026-11-19» y país «de» → `ponerSalidas` recibe
    `Salida(AMAZON, numeroGuardado, 2026-11-19, "DE")` (país en mayúsculas, cortado a 2). Un pedido de un marketplace sin canal no
    entra. Un fallo de `ponerSalidas` hace que la vuelta termine en ERROR como cualquier otro fallo de guardado (ya lo hace el
    `catch` de `vuelta`).
  - `VistaFilasTest`: `fechaSalida` y `pais` de la fila pasan a la `FilaVista`.
- [ ] **Step 2: verlos fallar.**
- [ ] **Step 3: implementar**
  - `FilasDAO`:
    ```java
    public record Salida(Canal canal, String pedido, LocalDate fechaSalida, String pais) {}
    static final String SQL_PONER_SALIDA = "UPDATE Pedido_fila SET FECHA_SALIDA = ?, PAIS = ? WHERE ORIGEN = 'SHIPPYPRO' AND CANAL = ?"
            + " AND PEDIDO = ? AND CERRADA_EN IS NULL";
    /** Pone a las filas vivas de cada pedido su fecha de salida y su pais (se recalcula en cada vuelta). */
    public void ponerSalidas(List<Salida> salidas) {
        if (salidas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (Salida s : salidas)
            lote.add(new Object[]{s.fechaSalida() == null ? null : java.sql.Date.valueOf(s.fechaSalida()), s.pais(), s.canal().name(), s.pedido()});
        jdbc.batchUpdate(SQL_PONER_SALIDA, lote);
    }
    ```
    Añade `FECHA_SALIDA, PAIS` a la lista de columnas de `SQL_FILAS` y léelas en `fila(ResultSet, int)` con el `dia(rs, "FECHA_SALIDA")`
    que ya existe y `rs.getString("PAIS")`.
  - `FilaPedido`: dos componentes al final; actualiza el javadoc. Busca con `grep -rn "new FilaPedido(" src` las llamadas con 23
    argumentos (el `fila()` del DAO y quizá tests) y añádeles `null, null` o los valores leídos.
  - `LecturaFilas`: inyecta `CalendarioLaboral` (constructor público y el de tests; ajusta los tests que construyen `LecturaFilas` con un
    `CalendarioLaboral` de Mockito que devuelva `Set.of()`). En `vuelta()`, después de `dao.reabrir(...)` y antes de `dao.marcarEnviadas(...)`:
    ```java
    Set<LocalDate> festivos = calendario.festivos();
    List<FilasDAO.Salida> salidas = new ArrayList<>();
    for (PedidoShippyPro p : leidos.pedidos()) {
        Canal canal = canales.get(p.marketplace());
        if (canal == null || p.numero() == null || p.numero().isBlank()) continue;
        String pais = p.pais() == null ? null : AnchosNsys.corta(p.pais().strip().toUpperCase(Locale.ROOT), 2);
        salidas.add(new FilasDAO.Salida(canal, SincroShippyPro.numeroGuardado(p.numero()), FechaSalida.de(p, canal, festivos), pais));
    }
    dao.ponerSalidas(salidas);
    ```
    (`numeroGuardado` es package-private en el mismo paquete: vale.)
  - `FilaVista`: dos componentes al final, nullable; los dos constructores secundarios pasan null. `VistaFilas.vistas(...)`: pasa
    `f.fechaSalida()` y `f.pais()`.
  - `docs/api_contract.md`, en el apartado de `GET /api/pedidos/filas`: «`FilaVista` gana al final `fechaSalida` (ISO, nullable: último
    día para enviar; Amazon, Latest ship de ShippyPro; webs, entrada + 2 o 3 días laborables; histórico, null) y `pais` (nullable)».
- [ ] **Step 4: verde** — los tres ficheros de test, y después `mvn -q test` entera (incluye `OpenApiContractTest`).
- [ ] **Step 5: commit** — `git commit -m "feat: la lectura de ShippyPro guarda la fecha de salida y el pais de cada fila viva y la vista los sirve"`

---

### Task 4: No se puede poner un IMEI que ya está en otro pedido pendiente

**Files:**
- Create: `ImeiDuplicadoException.java`
- Modify: `FilasDAO.java` (`ponerImei`), `FilasController.java` (`ponerImei`), `docs/api_contract.md`
- Test: `FilasDAOTest.java`, `FilasControllerTest.java`

**Interfaces:**
- Produces: `public class ImeiDuplicadoException extends RuntimeException` con `String pedido()`, `LocalDate fecha()`, `Canal canal()`,
  `boolean sinAsociar()` y `String mensaje()` (el texto para el 409).

- [ ] **Step 1: tests que fallan**
  - `FilasDAOTest`:
    - `ponerUnImeiQueEstaEnOtraFilaVivaLanzaDuplicadoYNoEscribeNada`: fila A viva con IMEI `009900000000001`; poner ese IMEI en la
      fila B viva → `ImeiDuplicadoException` con el pedido y la fecha de A; B sigue sin IMEI y sin apunte en el historial.
    - `…QueEstaEnUnaFilaConFaltaPorEnviarTambien`: A enviada (`ENVIO_FECHA` puesta, cerrada) con `FALTA_ENVIAR_EN` → duplicado.
    - `…QueEstaEnUnaEnviadaOEnElHistoricoSiSePone`: A enviada sin falta, y otra fila `HISTORICO` con el IMEI → se pone en B (PUESTO).
    - `volverAPonerElMismoImeiEnLaMismaFilaNoEsDuplicado` (la propia fila no cuenta).
    - En Amazon sin asociar (`ASOCIADA_EN` nulo y canal AMAZON), `sinAsociar()` es true.
  - `FilasControllerTest`: el DAO lanza la excepción → `409` y el mensaje exacto:
    «Este IMEI ya está en el pedido 0099-1110004-0000004 (fila del 17/11)»; sin asociar en AMAZON_STOCKROOM:
    «Este IMEI ya está en una fila de FNSK del 17/11 sin asociar».
- [ ] **Step 2: verlos fallar.**
- [ ] **Step 3: implementar**
  ```java
  package com.reparaciones.servidor.pedidos;

  import java.time.LocalDate;
  import java.time.format.DateTimeFormatter;

  /** El IMEI ya esta en otra fila de ShippyPro pendiente o con «falta por enviar» (spec 2026-11-18 §5). La lanza el DAO dentro de su
   *  transaccion, antes de escribir nada. */
  public class ImeiDuplicadoException extends RuntimeException {
      private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");
      private final String pedido;
      private final LocalDate fecha;
      private final Canal canal;
      private final boolean sinAsociar;

      public ImeiDuplicadoException(String pedido, LocalDate fecha, Canal canal, boolean sinAsociar) {
          super("IMEI duplicado");
          this.pedido = pedido; this.fecha = fecha; this.canal = canal; this.sinAsociar = sinAsociar;
      }
      public String pedido() { return pedido; }
      public LocalDate fecha() { return fecha; }
      public Canal canal() { return canal; }
      public boolean sinAsociar() { return sinAsociar; }

      public String mensaje() {
          String dia = fecha.format(DD_MM);
          if (sinAsociar) return "Este IMEI ya está en una fila de " + (canal == Canal.AMAZON_STOCKROOM ? "FNSK" : "AMZ") + " del " + dia + " sin asociar";
          return "Este IMEI ya está en el pedido " + pedido + " (fila del " + dia + ")";
      }
  }
  ```
  En `FilasDAO.ponerImei`, después de comprobar `CAMBIADA` y antes de `SQL_PONER_IMEI`, si `nuevo != null`:
  ```java
  static final String SQL_DUPLICADO = "SELECT PEDIDO, FECHA, CANAL, ASOCIADA_EN FROM Pedido_fila WHERE IMEI = ? AND ID_FILA <> ?"
          + " AND ORIGEN = 'SHIPPYPRO' AND (CERRADA_EN IS NULL OR (ENVIO_FECHA IS NOT NULL AND FALTA_ENVIAR_EN IS NOT NULL))"
          + " ORDER BY FECHA, ID_FILA LIMIT 1";
  ```
  Si devuelve fila, `throw new ImeiDuplicadoException(pedido, fecha, canal, canal != null && canal.esAmazon() && asociadaEn == null)`.
  Como el método es `@Transactional`, no queda nada escrito. En `FilasController.ponerImei`, alrededor de la llamada al DAO dentro de
  `idempotencia.ejecutar(...)`: `catch (ImeiDuplicadoException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.mensaje()); }`.
  Comprueba en el test que un reintento con la misma clave de idempotencia tras el 409 vuelve a dar 409 (no queda un resultado
  guardado como bueno).
  `docs/api_contract.md`, en el `PUT …/imei`: el nuevo 409 con sus dos textos.
- [ ] **Step 4: verde** — los dos ficheros de test.
- [ ] **Step 5: commit** — `git commit -m "feat: no se puede poner en una fila un IMEI que ya esta en otro pedido pendiente o con falta por enviar"`

---

### Task 5: «Descargar para buscar» (servidor)

**Files:**
- Create: `ExcelBuscar.java`, `BuscarController.java`
- Modify: `FilasDAO.java` (consulta), `docs/api_contract.md`, `RolesPedidosTest.java`
- Test: `ExcelBuscarTest.java`, `BuscarControllerTest.java`, `FilasDAOTest.java`

**Interfaces:**
- Produces:
  - `record FilasDAO.PorBuscar(String sku, String pais, LocalDate fechaSalida)`; `List<PorBuscar> FilasDAO.porBuscar(Collection<Canal> canales)`.
  - `ExcelBuscar.Tabla(List<String> paises, List<FilaTabla> filas)`, `record FilaTabla(String sku, Map<String,Integer> porPais, int total)`;
    `static Tabla ExcelBuscar.tabla(List<PorBuscar> filas)`; `static byte[] ExcelBuscar.escribir(String nombreHoja, Tabla paraHoy, Tabla puedenEsperar)`.
  - `GET /api/pedidos/buscar?plataforma=AMZ|FNSK|WEBS` → xlsx con `Content-Disposition: attachment; filename="Buscar AMZ 18-11.xlsx"`
    (codificado como hagan las plantillas de «Por pasar a NSYS»: mira `PorPasarNsys`/su controlador y copia cómo ponen el nombre y la
    cabecera `X-Filas`; aquí `X-Filas` = móviles contados en las dos tablas).

- [ ] **Step 1: tests que fallan**
  - `FilasDAOTest.porBuscarCuentaSoloPendientesYFaltaPorEnviarSinImeiNiCancelado`: con filas de AMAZON: viva sin IMEI (entra), viva
    con IMEI (no), viva sin IMEI con marca CANCELADO (no), viva sin IMEI con otra marca (entra), enviada sin IMEI sin falta (no),
    enviada sin IMEI con `FALTA_ENVIAR_EN` (entra), cerrada sin envío (no), HISTORICO (no), de AMAZON_STOCKROOM (no si solo se pide
    AMAZON). IMEI vacío `''` cuenta como sin IMEI.
  - `ExcelBuscarTest`:
    ```java
    @Test void agrupaPorSkuSinDistinguirMayusculasConLaFormaMasRepetidaYCuentaPorPais() {
        var t = ExcelBuscar.tabla(List.of(
            new FilasDAO.PorBuscar("i13128AzulA", "FR", null), new FilasDAO.PorBuscar("i13128AzuLA", "FR", null),
            new FilasDAO.PorBuscar("i13128AzulA", "DE", null), new FilasDAO.PorBuscar("i12128AzulA", null, null)));
        assertThat(t.paises()).containsExactly("DE", "FR", "—");
        assertThat(t.filas()).extracting(ExcelBuscar.FilaTabla::sku).containsExactly("i12128AzulA", "i13128AzulA");
        assertThat(t.filas().get(1).porPais()).containsEntry("FR", 2).containsEntry("DE", 1);
        assertThat(t.filas().get(1).total()).isEqualTo(3);
    }
    ```
    y otro que escribe el xlsx con `escribir("AMZ", paraHoy, puedenEsperar)`, lo vuelve a abrir con `XSSFWorkbook` y comprueba: una sola
    hoja; A1 «Para hoy»; la cabecera `SKU | DE | FR | — | Total general` en la fila 2; los números como celdas numéricas; vacío donde es 0;
    la fila «Total general» con las sumas; una fila en blanco y después «Pueden esperar» con su tabla; con una tabla sin filas, su título y
    «No hay móviles que buscar». Impresión: A4, `fitWidth` 1, `fitHeight` 0 (como `HojaImprimible`).
  - `BuscarControllerTest` (con el DAO de Mockito y un `Clock` fijo el 18/11/2026 en Madrid): `plataforma=AMZ` pide `[AMAZON]`; `FNSK`,
    `[AMAZON_STOCKROOM]`; `WEBS`, `[DISTRIBUIDORES, PARTICULARES, BACKIPHONE]`; otra → 400. Reparto: `fechaSalida` null, 17/11 o 18/11 →
    «Para hoy»; 19/11 → «Pueden esperar». Nombre «Buscar FNSK 18-11.xlsx».
  - `RolesPedidosTest`: SUPERTECNICO y ADMIN, 200; TECNICO, 403.
- [ ] **Step 2: verlos fallar.**
- [ ] **Step 3: implementar**
  - DAO:
    ```java
    public record PorBuscar(String sku, String pais, LocalDate fechaSalida) {}
    static final String SQL_POR_BUSCAR = "SELECT SKU, PAIS, FECHA_SALIDA FROM Pedido_fila WHERE ORIGEN = 'SHIPPYPRO' AND CANAL IN (%s)"
            + " AND (IMEI IS NULL OR IMEI = '') AND (MARCA IS NULL OR MARCA <> 'CANCELADO')"
            + " AND (CERRADA_EN IS NULL OR (ENVIO_FECHA IS NOT NULL AND FALTA_ENVIAR_EN IS NOT NULL))";
    ```
    (`%s` = tantos `?` como canales; los canales salen del enum, nunca del usuario.)
  - `ExcelBuscar.tabla`: agrupa por `sku.toLowerCase(Locale.ROOT)` (SKU nulo → «(sin SKU)»); el texto de la fila es la forma más repetida
    del grupo (empate: la primera en orden alfabético); orden de filas alfabético sin distinguir mayúsculas; países presentes en orden
    alfabético, `null` → «—» al final.
  - `ExcelBuscar.escribir`: con POI (`XSSFWorkbook`), estilos como `HojaImprimible` (cabecera en negrita, bordes finos). Título de cada
    tabla en negrita en su propia fila. Anchos: SKU al texto más largo (tope 40), países 6, total 13. `setFitToPage(true)`, A4, `fitWidth`
    1, `fitHeight` 0, vertical. Repetir filas de cabecera no aplica (hay dos tablas): no lo pongas.
  - `BuscarController` (`@RestController @RequestMapping("/api/pedidos") @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")`), con
    `Clock` inyectable como `FilasController` (hoy = `LocalDate.now(clock)`). `@ApiResponses` con el 200 binario (como
    `EtiquetaFilaController`) y el 400.
  - `docs/api_contract.md`: la ruta, sus tres valores, qué filas cuenta, las dos tablas y el nombre del fichero.
- [ ] **Step 4: verde** — los tests nuevos y `mvn -q test` entera. Deja `target/openapi.json` para la web.
- [ ] **Step 5: commit** — `git commit -m "feat: descargar para buscar, Excel por plataforma con lo que hay que buscar hoy y lo que puede esperar"`

---

### Task 6: Web — tipos, «Enviado» con la fecha de salida, filtros y contadores

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados), `src/modules/telefonos/pedidos/filas.ts`,
  `pedidos/TablaFilas.tsx`, `pedidos/PedidosPage.tsx`, `pedidos/AyudaPedidos.tsx`, `test/fabrica.ts` (si la fábrica de filas necesita
  `fechaSalida: null, pais: null` por defecto)
- Create: `pedidos/salida.ts`, `pedidos/salida.test.ts`
- Test: `pedidos/filas.test.ts`, `pedidos/PedidosFiltros.test.tsx` (o el fichero de tests de la tabla que ya pruebe «Enviado»)

**Interfaces:**
- Consumes: `FilaVista.fechaSalida: string | null` (ISO `yyyy-mm-dd`).
- Produces: `type NivelSalida = 'RETRASO' | 'HOY' | 'MANANA' | 'LUEGO'`; `nivelSalida(f: FilaVista, hoy: string): NivelSalida | null`
  (null si la fila no está pendiente ni en «falta por enviar», o si no tiene fecha); `textoSalida(f, hoy): string | null`.

- [ ] **Step 1: regenerar tipos** — copia `../servidor-pedidos-tabla/target/openapi.json` a `api/openapi.json`, `npm run api:types:offline`.
- [ ] **Step 2: test que falla (`salida.test.ts`)**
  ```ts
  import { describe, expect, it } from 'vitest'
  import { fila } from '../test/fabrica'
  import { nivelSalida, textoSalida } from './salida'

  const HOY = '2026-11-18' // miércoles
  describe('fecha de salida', () => {
    it('pendiente: retraso, hoy, mañana o más tarde', () => {
      expect(nivelSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-17' }), HOY)).toBe('RETRASO')
      expect(nivelSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-18' }), HOY)).toBe('HOY')
      expect(nivelSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-19' }), HOY)).toBe('MANANA')
      expect(nivelSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-20' }), HOY)).toBe('LUEGO')
      expect(textoSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-17' }), HOY)).toBe('Con retraso')
      expect(textoSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-18' }), HOY)).toBe('Sale hoy')
      expect(textoSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-19' }), HOY)).toBe('Sale mañana')
      expect(textoSalida(fila({ estado: 'PENDIENTE', fechaSalida: '2026-11-20' }), HOY)).toBe('Sale vie 20/11')
    })
    it('sin fecha, enviada, cancelada o histórico: nada', () => {
      expect(nivelSalida(fila({ estado: 'PENDIENTE', fechaSalida: null }), HOY)).toBeNull()
      expect(nivelSalida(fila({ estado: 'ENVIADA', impresaEn: '2026-11-17T10:00:00', fechaSalida: '2026-11-17' }), HOY)).toBeNull()
      expect(nivelSalida(fila({ estado: 'PENDIENTE', marca: 'CANCELADO', fechaSalida: '2026-11-17' }), HOY)).toBeNull()
      expect(nivelSalida(fila({ estado: 'HISTORICO', editable: false, fechaSalida: '2026-11-17' }), HOY)).toBeNull()
    })
    it('falta por enviar conserva el plazo', () => {
      const f = fila({ estado: 'ENVIADA', faltaEnviarEn: '2026-11-16T09:00:00', fechaSalida: '2026-11-17' })
      expect(nivelSalida(f, HOY)).toBe('RETRASO')
    })
  })
  ```
  (Ajusta los nombres de la fábrica a los que tenga `test/fabrica.ts`; el 20/11/2026 es viernes: «Sale vie 20/11».)
- [ ] **Step 3: implementar `salida.ts`**
  ```ts
  import type { FilaVista } from '@/shared/api/client'
  import { estadoPantalla } from './filas'

  export type NivelSalida = 'RETRASO' | 'HOY' | 'MANANA' | 'LUEGO'
  const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb']

  /** Día siguiente a una fecha ISO (yyyy-mm-dd), sin husos: se trabaja con el día de Madrid ya calculado. */
  function siguiente(iso: string): string {
    const d = new Date(`${iso}T12:00:00Z`)
    d.setUTCDate(d.getUTCDate() + 1)
    return d.toISOString().slice(0, 10)
  }

  /** Spec 2026-11-18 §3: solo las filas pendientes o con «falta por enviar» que tienen fecha de salida. */
  export function nivelSalida(f: FilaVista, hoy: string): NivelSalida | null {
    const e = estadoPantalla(f)
    if ((e !== 'PENDIENTE' && e !== 'FALTA_ENVIAR') || !f.fechaSalida) return null
    if (f.fechaSalida < hoy) return 'RETRASO'
    if (f.fechaSalida === hoy) return 'HOY'
    return f.fechaSalida === siguiente(hoy) ? 'MANANA' : 'LUEGO'
  }

  export function textoSalida(f: FilaVista, hoy: string): string | null {
    const n = nivelSalida(f, hoy)
    if (n === null) return null
    if (n === 'RETRASO') return 'Con retraso'
    if (n === 'HOY') return 'Sale hoy'
    if (n === 'MANANA') return 'Sale mañana'
    const d = new Date(`${f.fechaSalida}T12:00:00Z`)
    return `Sale ${DIAS[d.getUTCDay()]} ${f.fechaSalida!.slice(8, 10)}/${f.fechaSalida!.slice(5, 7)}`
  }
  ```
- [ ] **Step 4: «Enviado» en la tabla** (`TablaFilas.tsx`, `EtiquetaEstado`): recibe `hoy` (de `hoyMadrid()`, calculado una vez en
  `PedidosPage` y pasado por props como las demás cosas de la fila; no lo calcules por fila). Si `nivelSalida` no es null:
  - en `PENDIENTE`, el texto es `textoSalida(...)` en vez de «Pendiente»; colores: `RETRASO` `bg-[#b42318] text-white`, `HOY`
    `bg-[#fde2e1] text-[#b42318]`, `MANANA`/`LUEGO` el de `PENDIENTE`;
  - en `FALTA_ENVIAR`, solo cambia si es `RETRASO`: «Envío parcial · falta por enviar · con retraso» con el color de `RETRASO`.
  Ojo al `memo` de `FilaTabla`: `hoy` entra en sus props (cambia una vez al día).
- [ ] **Step 5: filtros y contadores** (`filas.ts`, `PedidosPage.tsx`):
  - `OPCIONES_ENVIADO` gana `{ valor: 'SALE_HOY', texto: 'Sale hoy' }` y `{ valor: 'CON_RETRASO', texto: 'Con retraso' }` (amplía el
    tipo del valor a `EstadoPantalla | 'SALE_HOY' | 'CON_RETRASO'` donde haga falta). `pasaEnviado` recibe `hoy` y deja pasar
    `SALE_HOY` si `nivelSalida === 'HOY'` y `CON_RETRASO` si `=== 'RETRASO'` (además de lo que ya hace). `filtrarColumnas` y su llamada
    en `PedidosPage` pasan `hoy`.
  - `recuentoSalida(filas, hoy): { hoy: number; retraso: number }` en una pasada. En `PedidosPage`, junto a «N sin IMEI»: «N salen hoy»
    y, solo si M > 0, «M con retraso» (mismo estilo de píldora que los otros contadores; si los contadores son botones que filtran,
    estos filtran por `SALE_HOY` y `CON_RETRASO`).
  - Tests: `filas.test.ts` (pasaEnviado con las dos opciones nuevas, recuentoSalida) y uno de pantalla: una fila pendiente con
    `fechaSalida` = hoy enseña «Sale hoy» y el contador «1 salen hoy».
- [ ] **Step 6: la ayuda** (`AyudaPedidos.tsx`): líneas con muestra para «Sale hoy», «Sale mañana / Sale vie 20/11» y «Con retraso»
  («Amazon: el último día que da Amazon; webs: 48 o 72 h laborables según el envío, festivos de Madrid incluidos»). Y el scroll:
  en `PopoverContent`, añade `max-h-[calc(100vh-80px)] overflow-y-auto`. Test: el contenido lleva esas clases.
- [ ] **Step 7: verde** — `npm run test -- src/modules/telefonos/pedidos` y `npm run check`.
- [ ] **Step 8: commit** — `git commit -m "feat: la columna Enviado de Pedidos dice si sale hoy, manana o con retraso, con su filtro y contadores"`

---

### Task 7: Web — botón «Descargar para buscar» y el mensaje del duplicado

**Files:**
- Create: `pedidos/DescargarBuscar.tsx`, `pedidos/DescargarBuscar.test.tsx`
- Modify: `api.ts` (hook), `pedidos/PedidosPage.tsx` (cabecera), y lo que haga falta en la celda del IMEI para el 409

**Interfaces:**
- Consumes: `GET /api/pedidos/buscar?plataforma=…` (Task 5).
- Produces: `useDescargarBuscar()` en `api.ts`: `mutateAsync({ plataforma: 'AMZ' | 'FNSK' | 'WEBS' })` → nombre guardado.

- [ ] **Step 1: test que falla** — con MSW: pulsar «Descargar para buscar» abre un menú con «AMZ», «FNSK» y «Webs»; elegir «FNSK» hace
  `GET /api/pedidos/buscar?plataforma=FNSK` y guarda el blob con el nombre del `Content-Disposition` («Buscar FNSK 18-11.xlsx»); un
  error sale por el diálogo de errores de siempre.
- [ ] **Step 2: implementar** — el hook, copiando el patrón de la descarga de plantillas de `api.ts` (`api.GET(..., { parseAs: 'blob' })`,
  `nombreDeDescarga(response.headers.get('Content-Disposition'))`, `guardarEn`/`descargarBlob`); el botón con un menú (usa el
  componente de menú/popover que ya use la cabecera de Pedidos o `@/shared/ui/popover`), deshabilitado mientras descarga.
- [ ] **Step 3: el 409 del duplicado** — al poner un IMEI que el servidor rechaza con 409 y «Este IMEI ya está…», la celda debe enseñar ese
  mensaje tal cual (hoy un 409 de la celda se trata como «otra persona ha cambiado el IMEI»: mira `estado.ts`/`CeldaImei.tsx` y
  `MENSAJE_ERROR`). Distingue por el texto del servidor (empieza por «Este IMEI ya está») y no reintentes. Test: el 409 con ese texto
  deja la celda en rojo con el mensaje; el 409 de siempre sigue igual.
- [ ] **Step 4: verde** — `npm run check`.
- [ ] **Step 5: commit** — `git commit -m "feat: descargar para buscar en Pedidos (AMZ, FNSK y webs) y el aviso del IMEI que ya esta en otro pedido"`

---

### Task 8: Cierre

- [ ] `mvn -q test` (servidor) y `npm run check` (web) en verde; anota los recuentos.
- [ ] Comprueba que `docs/api_contract.md` del servidor recoge: `fechaSalida`/`pais` en `FilaVista`, el 409 del duplicado y
  `GET /api/pedidos/buscar`.
- [ ] Revisión final de la rama (los commits de las Tasks 1-7) contra la spec: cada apartado (§1-§7 y «Migración») tiene su código y su
  test. Lista lo que no.
- [ ] **No** hagas push, merge, tags ni despliegues; **no** toques preprod ni producción. Informa al supervisor con los commits
  (servidor y web), los recuentos de tests y lo pendiente. El despliegue en preprod (migración `sql/migracion-pedidos-salida.sql`,
  build en primer plano, prueba en Chromium con datos 0099) lo prepara el supervisor para que lo lance Imad.
