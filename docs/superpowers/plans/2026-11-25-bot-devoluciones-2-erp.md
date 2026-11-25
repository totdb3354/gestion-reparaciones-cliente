# Bot de devoluciones — plan 2: ERP (0.9.47) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El ERP guarda lo que el bot de NSYS le informa que ha movido y le da la lista de IMEI que debe devolver de «Pedidos
realizados» a su caja anterior, con apuntes en el historial de la fila.

**Architecture:** Paquete nuevo `com.reparaciones.servidor.bot` en el servidor: una tabla solo de añadir (`Bot_movimiento`), un
DAO (`BotDAO`), una regla pura y probada condición a condición (`ReglaDevoluciones`), un servicio que junta los datos y escribe
los apuntes (`Devoluciones`) y un controlador con dos rutas de máquina bajo `/api/bot/` protegidas por una clave propia
(`ClaveBotFilter`). En la web solo cambia el texto del historial de la fila y el número de versión.

**Tech Stack:** Java 17, Spring Boot 3.3, JdbcTemplate, MariaDB 11, JUnit 5 + Mockito (JdbcTemplate simulado, como el resto de
DAO), MockMvc; web React + TypeScript + Vitest.

**Spec:** `docs/superpowers/specs/2026-11-24-bot-devoluciones-design.md` (§3, §4, §5, §6, §10). **Fuera de este plan:** §7 «no
vendible» (EN ESPERA de la sesión «IMEI en la celda») y el bot (plan 3).

## Global Constraints

- Versión **0.9.47** (orden acordado: 0.9.46 libro manda, 0.9.47 esta, 0.9.48 reserva de IMEI). Sube el número de la web
  (`package.json` y `CHANGELOG.md`), también si solo cambiara el servidor.
- Ramas `dev_bot_devoluciones` en worktrees bajo `C:\Users\dev\Documents\wt\`: servidor `wt/servidor-bot-devoluciones` y web
  `wt/web-bot-devoluciones`, **desde `origin/main`** (0.9.46.2: servidor `43e85fb`, web `61ad299`). Nada de merge, push ni tags
  sin confirmación de Imad. Commits sin `Co-Authored-By`.
- **No tocar en la web** (otra sesión, rama `dev_pedidos_imei_celda`): `pedidos/CeldaImei.tsx`, `TablaFilas.tsx`,
  `PedidosPage.tsx`, `estado.ts`, `filas.ts`, `AyudaPedidos.tsx` ni sus pruebas. `historial.ts` sí se toca (solo importa de
  `filas.ts`).
- Maven en Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.
  `mvn test` completo antes de dar el plan por hecho (incluye `OpenApiContractTest`, que pilla roturas de arranque).
- Horas: `Bot_movimiento.FECHA`, `Nsys_evento.FECHA` y `Pedido_fila_cambio.FECHA` van en hora de Madrid;
  `Reparacion.FECHA_ASIG` va en UTC y se lee con `NsysDAO.trabajosDe`, que ya la pasa a Madrid.
- Lecturas y escrituras en la misma transacción: `@Transactional(isolation = Isolation.READ_COMMITTED)` (MariaDB 11.6+, error
  1020 con REPEATABLE READ).
- Las cajas se comparan por la caja de NSYS tal cual (`A_NSYS`, `Nsys_ubicacion.LOCATION`), nunca por el nombre de ubicación del
  ERP («Caja · …» desde la 0.9.45).
- IMEI reales, IPs y claves nunca en el repo. Tests con IMEI inventados (`35000000000000x`).
- **Decisión de este plan (avisar a Imad):** la clave de `/api/bot/**` tiene **ajuste propio** `pedidos.bot.clave`
  (`PEDIDOS_BOT_CLAVE`), como sincronizados y asociaciones: las rutas que escriben no usan la clave de solo lectura de vendidos.
  En el PC del bot puede ser la misma clave puesta en los dos ajustes.
- **Simplificación (avisar a Imad):** el grado del destino es el de NSYS (`Nsys_movil.GRADE`), sin los cambios del taller.
  Cualquier reparación posterior al descuento ya bloquea la devolución automática («reparado después»).

---

## File Structure

Servidor (`C:\Users\dev\Documents\wt\servidor-bot-devoluciones`):

| Fichero | Qué hace |
|---|---|
| `sql/migracion-bot-movimiento.sql` (nuevo) | Crea `Bot_movimiento` (aditiva, idempotente) |
| `sql/crear_bd.sql` | La tabla nueva y el comentario de `Pedido_fila_cambio.TIPO` |
| `src/main/java/com/reparaciones/servidor/bot/MovimientoBot.java` (nuevo) | Un movimiento que informa el bot |
| `src/main/java/com/reparaciones/servidor/bot/MovimientosBot.java` (nuevo) | El cuerpo del `POST` |
| `src/main/java/com/reparaciones/servidor/bot/InformeMovimientos.java` (nuevo) | La respuesta del `POST` |
| `src/main/java/com/reparaciones/servidor/bot/ListaDevoluciones.java` (nuevo) | La respuesta del `GET` (grupos por destino) |
| `src/main/java/com/reparaciones/servidor/bot/BotDAO.java` (nuevo) | SQL de `Bot_movimiento` y lecturas para la lista |
| `src/main/java/com/reparaciones/servidor/bot/ReglaDevoluciones.java` (nuevo) | La regla de §5, pura |
| `src/main/java/com/reparaciones/servidor/bot/Devoluciones.java` (nuevo) | Junta datos, aplica la regla, escribe apuntes |
| `src/main/java/com/reparaciones/servidor/bot/BotController.java` (nuevo) | `GET /api/bot/devoluciones`, `POST /api/bot/movimientos` |
| `src/main/java/com/reparaciones/servidor/security/ClaveBotFilter.java` (nuevo) | Clave de máquina de `/api/bot/**` |
| `src/main/java/com/reparaciones/servidor/config/SecurityConfig.java` | Registra el filtro y la regla de la ruta |
| `src/main/java/com/reparaciones/servidor/security/JwtAuthFilter.java` | Se salta `/api/bot/**` |
| `src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java` | Tipos `DEVUELTO`, `NO_DEVUELTO`, `SIN_DESTINO` |
| `docs/api_contract.md`, `docs/autorizacion_endpoints.md`, `docs/deployment.md`, `docs/schema.md` | Documentación |
| Tests en `src/test/java/com/reparaciones/servidor/bot/` y `.../controller/RolesBot*Test.java` | |

Web (`C:\Users\dev\Documents\wt\web-bot-devoluciones`): `src/modules/telefonos/pedidos/historial.ts` (+ test), `package.json`,
`CHANGELOG.md`.

Raíz (`C:\Users\dev\Documents\wt\dev_bot_devoluciones`): `docs/modulos/pedidos.md` (historial de la fila).

---

### Task 0: Worktrees

- [ ] **Step 1: Crear los dos worktrees desde origin/main**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git worktree add -b dev_bot_devoluciones /c/Users/dev/Documents/wt/servidor-bot-devoluciones origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git worktree add -b dev_bot_devoluciones /c/Users/dev/Documents/wt/web-bot-devoluciones origin/main
```

Expected: `Preparing worktree (new branch 'dev_bot_devoluciones')` dos veces; `git -C .../servidor-bot-devoluciones log -1
--oneline` = `43e85fb`, web = `61ad299` (o posterior si main avanzó: anotar cuál).

- [ ] **Step 2: Comprobar que la suite parte en verde**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/wt/servidor-bot-devoluciones && mvn -q test 2>&1 | tail -15
cd /c/Users/dev/Documents/wt/web-bot-devoluciones && npm ci && npx vitest run 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` (sin `FAIL`) y Vitest sin fallos. Si algo falla ya en origin/main, anotarlo en el ledger y no
arreglarlo aquí (memoria: test web `PedidosFiltros «buscador <2 caracteres»` podía estar roto de antes).

---

### Task 1: Tabla `Bot_movimiento`, tipos de apunte y escritura del DAO

**Files:**
- Create: `sql/migracion-bot-movimiento.sql`
- Modify: `sql/crear_bd.sql` (tabla nueva al final de la zona de Pedidos; comentario de `Pedido_fila_cambio.TIPO`)
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/MovimientoBot.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/BotDAO.java`
- Test: `src/test/java/com/reparaciones/servidor/bot/BotDAOTest.java`

**Interfaces:**
- Produces: `record MovimientoBot(String imei, String accion, String resultado, String origen, String destino, String motivo, LocalDateTime fecha)`;
  `BotDAO(JdbcTemplate)`; `int BotDAO.guardar(String run, MovimientoBot m, LocalDateTime recibido)` (1 = guardado, 0 = ya
  estaba); `void BotDAO.apuntar(int idFila, LocalDateTime fecha, String imei, String tipo, String detalle)`;
  `CambioImei.DEVUELTO`, `CambioImei.NO_DEVUELTO`, `CambioImei.SIN_DESTINO`.

- [ ] **Step 1: Migración**

`sql/migracion-bot-movimiento.sql`:

```sql
-- Spec 2026-11-24-bot-devoluciones §4.1: lo que el bot de NSYS informa que ha movido. Solo se añade; nunca se cambia ni se borra.
-- Aditiva e idempotente: el servidor anterior no la lee. Vuelta atras: DROP TABLE Bot_movimiento; (pierde lo informado).
CREATE TABLE IF NOT EXISTS Bot_movimiento (
    ID           BIGINT      NOT NULL AUTO_INCREMENT,
    RUN_ID       VARCHAR(40) NOT NULL COMMENT 'La vuelta del bot',
    IMEI         VARCHAR(40) NOT NULL,
    ACCION       VARCHAR(20) NOT NULL COMMENT 'DESCONTADO o DEVUELTO',
    RESULTADO    VARCHAR(20) NOT NULL COMMENT 'OK, NO_APARECE o ERROR',
    ORIGEN_NSYS  VARCHAR(80) NULL COMMENT 'Location que leyo el bot antes de mover, tal cual',
    DESTINO_NSYS VARCHAR(80) NULL,
    MOTIVO       VARCHAR(30) NULL COMMENT 'En DEVUELTO: el motivo de la lista (IMEI_CORREGIDO, RESERVA_LIBERADA)',
    FECHA        DATETIME    NOT NULL COMMENT 'Hora de Madrid del bot',
    RECIBIDO     DATETIME    NOT NULL COMMENT 'Hora de Madrid en que lo guardo el ERP',
    PRIMARY KEY (ID),
    UNIQUE KEY uq_bot_movimiento (RUN_ID, IMEI, ACCION),
    KEY idx_bot_movimiento_imei (IMEI, ACCION, FECHA)
);
SELECT COUNT(*) AS COLUMNAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Bot_movimiento';
```

Expected al aplicarla: `COLUMNAS = 10`.

- [ ] **Step 2: Lo mismo en `crear_bd.sql`**

Pegar el mismo `CREATE TABLE` (con `CREATE TABLE Bot_movimiento (`, sin `IF NOT EXISTS`, como el resto del fichero) justo después
del bloque de `Pedido_fila_cambio`, con una línea de comentario encima: `-- Lo que el bot de NSYS informa que ha movido (spec
2026-11-24-bot-devoluciones §4.1). Solo se añade.` Y ampliar el `COMMENT` de `Pedido_fila_cambio.TIPO` añadiendo al final
`, DEVUELTO, NO_DEVUELTO o SIN_DESTINO (devoluciones del bot)`.

- [ ] **Step 3: Tipos nuevos en `CambioImei`**

En `CambioImei.java`, después de `VALIDACION_QUITADA`:

```java
    /** El bot de NSYS devolvio a su caja el IMEI que salio de esta fila (imeiAnterior); detalle «de Pedidos realizados a <caja>; IMEI
     *  cambiado por <quien> el <dd/MM HH:mm>». Sin usuario. */
    public static final String DEVUELTO = "DEVUELTO";
    /** El bot no pudo devolverlo; detalle «no aparece en NSYS en Pedidos realizados» o «3 errores seguidos». Sin usuario. */
    public static final String NO_DEVUELTO = "NO_DEVUELTO";
    /** No se devuelve solo (lo mueve una persona); detalle «sin caja», «<caja> no es vendible», «grado <G> no es vendible» o
     *  «reparado después (<ID_REP>, …)». Una vez por IMEI y fila. Sin usuario. */
    public static final String SIN_DESTINO = "SIN_DESTINO";
```

Y en el javadoc del record, tras `VALIDACION_QUITADA (...)`, añadir: `DEVUELTO, NO_DEVUELTO y SIN_DESTINO (devoluciones del bot
de NSYS, spec 2026-11-24-bot-devoluciones §4.2: imeiAnterior = el IMEI que salio de la fila, imeiNuevo nulo, detalle con el
destino o el motivo)`.

- [ ] **Step 4: Test que falla**

`src/test/java/com/reparaciones/servidor/bot/BotDAOTest.java`:

```java
package com.reparaciones.servidor.bot;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Escrituras de BotDAO (JdbcTemplate simulado; IMEI inventados). */
class BotDAOTest {

    private static final LocalDateTime BOT = LocalDateTime.of(2026, 11, 25, 10, 12, 3);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 25, 10, 15);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BotDAO dao = new BotDAO(jdbc);

    @Test void guardarInsertaIgnorandoRepetidosYDevuelveSiEntro() {
        when(jdbc.update(eq(BotDAO.SQL_GUARDAR), any(Object[].class))).thenReturn(1, 0);
        MovimientoBot m = new MovimientoBot("350000000000001", "DESCONTADO", "OK", "CAJA LISTOS", "Pedidos realizados", null, BOT);
        assertEquals(1, dao.guardar("run-1", m, AHORA));
        assertEquals(0, dao.guardar("run-1", m, AHORA));
        verify(jdbc, times(2)).update(BotDAO.SQL_GUARDAR, "run-1", "350000000000001", "DESCONTADO", "OK", "CAJA LISTOS",
                "Pedidos realizados", null, Timestamp.valueOf(BOT), Timestamp.valueOf(AHORA));
    }

    @Test void elSqlDeGuardarEsInsertIgnore() {
        assertEquals("INSERT IGNORE INTO Bot_movimiento (RUN_ID, IMEI, ACCION, RESULTADO, ORIGEN_NSYS, DESTINO_NSYS, MOTIVO, FECHA, RECIBIDO)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", BotDAO.SQL_GUARDAR);
    }

    @Test void apuntarEscribeSinUsuarioConElImeiQueSalio() {
        dao.apuntar(29116, AHORA, "350000000000001", "SIN_DESTINO", "sin caja");
        verify(jdbc).update("INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
                + " VALUES (?, NULL, ?, ?, NULL, ?, ?)", 29116, Timestamp.valueOf(AHORA), "350000000000001", "SIN_DESTINO", "sin caja");
    }
}
```

- [ ] **Step 5: Ver que falla**

Run: `cd /c/Users/dev/Documents/wt/servidor-bot-devoluciones && mvn -q test -Dtest=BotDAOTest 2>&1 | tail -15`
Expected: error de compilación (`cannot find symbol ... BotDAO` / `MovimientoBot`).

- [ ] **Step 6: Implementar**

`src/main/java/com/reparaciones/servidor/bot/MovimientoBot.java`:

```java
package com.reparaciones.servidor.bot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un movimiento que el bot de NSYS informa (spec 2026-11-24-bot-devoluciones §6.2). accion: DESCONTADO o DEVUELTO; resultado: OK,
 *  NO_APARECE o ERROR; origen: la Location que leyo antes de mover, tal cual; destino: a donde lo movio (o intento); motivo: en
 *  DEVUELTO, el de la lista; fecha: hora de Madrid del bot. */
public record MovimientoBot(String imei, String accion, String resultado, @Schema(nullable = true) String origen,
                            @Schema(nullable = true) String destino, @Schema(nullable = true) String motivo, LocalDateTime fecha) {}
```

`src/main/java/com/reparaciones/servidor/bot/BotDAO.java`:

```java
package com.reparaciones.servidor.bot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Bot_movimiento y las lecturas de la lista de devoluciones (spec 2026-11-24-bot-devoluciones §4 y §5). */
@Repository
public class BotDAO {

    static final String SQL_GUARDAR = "INSERT IGNORE INTO Bot_movimiento (RUN_ID, IMEI, ACCION, RESULTADO, ORIGEN_NSYS, DESTINO_NSYS, MOTIVO, FECHA, RECIBIDO)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_APUNTAR = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, NULL, ?, ?, NULL, ?, ?)";

    private final JdbcTemplate jdbc;

    public BotDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Guarda un movimiento; un repetido (misma vuelta, IMEI y accion) no entra. 1 si entro, 0 si ya estaba. */
    public int guardar(String run, MovimientoBot m, LocalDateTime recibido) {
        return jdbc.update(SQL_GUARDAR, run, m.imei(), m.accion(), m.resultado(), m.origen(), m.destino(), m.motivo(),
                Timestamp.valueOf(m.fecha()), Timestamp.valueOf(recibido));
    }

    /** Un apunte del historial de la fila sin usuario, con el IMEI que salio de ella en IMEI_ANTERIOR. */
    public void apuntar(int idFila, LocalDateTime fecha, String imei, String tipo, String detalle) {
        jdbc.update(SQL_APUNTAR, idFila, Timestamp.valueOf(fecha), imei, tipo, detalle);
    }
}
```

- [ ] **Step 7: Ver que pasa**

Run: `mvn -q test -Dtest=BotDAOTest 2>&1 | tail -15`
Expected: sin errores (`Tests run: 3, Failures: 0`).

- [ ] **Step 8: Commit**

```bash
git add sql/migracion-bot-movimiento.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java \
  src/main/java/com/reparaciones/servidor/bot src/test/java/com/reparaciones/servidor/bot
git commit -m "feat(bot): tabla Bot_movimiento, apuntes DEVUELTO/NO_DEVUELTO/SIN_DESTINO y escritura del DAO"
```

---

### Task 2: La regla de la lista (pura)

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/bot/ReglaDevoluciones.java`
- Test: `src/test/java/com/reparaciones/servidor/bot/ReglaDevolucionesTest.java`

**Interfaces:**
- Consumes: `com.reparaciones.servidor.nsys.EstadoMovil` (`de`, `NO_VENDIBLES`), `Normalizacion.gradoSinBateria`,
  `UbicacionNsys(String location, String ubicacion, boolean cuentaStock, boolean esPedido)`.
- Produces (todo `public` dentro de `ReglaDevoluciones`):
  - constantes `CAJA_PEDIDOS = "Pedidos realizados"`, `CAJAS_REVISAR` (Set), `MARGEN_ENTRADA` (30 min), `ERRORES_SEGUIDOS = 3`,
    `DESCONTADO`, `DEVUELTO`, `OK`, `NO_APARECE`, `ERROR`, `IMEI_CORREGIDO`, `ACCIONES`, `RESULTADOS`;
  - `record Paso(String accion, String resultado, String origen, LocalDateTime fecha)` — una fila de `Bot_movimiento`;
  - `record Salida(int idFila, LocalDateTime fecha, String quien)` — el último apunte en que el IMEI salió de una fila;
  - `record Trabajo(String idRep, LocalDateTime asignado)` — hora de Madrid;
  - `record Datos(String imei, List<Paso> pasos, Salida salida, List<LocalDateTime> puestos, String estadoEspejo,
    String cajaEspejo, String grado, LocalDateTime entradaPedidos, List<Trabajo> trabajos)`;
  - `enum Tipo { DEVOLVER, SIN_DESTINO, NADA }`, `record Veredicto(Tipo tipo, String destino, String motivo, Salida salida)`
    con `Veredicto.NADA`;
  - `static Veredicto evaluar(Datos d, Map<String, UbicacionNsys> ubicaciones)` (`ubicaciones` por `LOCATION`);
  - `static int erroresSeguidosAlFinal(List<Paso> devueltos)`.

Regla (spec §5, con la decisión del 25/11 de «reparado después»). Con `t` = la hora del último `DESCONTADO · OK`:

1. Sin `DESCONTADO · OK` → NADA (lo movió una persona o no se informó).
2. Sin salida de una fila, o salida no posterior a `t` → NADA (un `DESCONTADO` posterior es de otra venta, p. ej. el Panel).
3. Nunca puesto en una fila antes o en `t` → NADA (el bot lo descontó por otra cosa).
4. El espejo no lo tiene `DE_PEDIDO` en «Pedidos realizados» → NADA (alguien lo movió).
5. Su última entrada en «Pedidos realizados» no cae en `[t − 30 min, t + 30 min]` → NADA (lo sacaron y lo volvieron a meter).
6. Un `DEVUELTO` desde `t` con `OK` o `NO_APARECE`, o los 3 últimos `DEVUELTO` desde `t` con `ERROR` → NADA.
7. Con alguna reparación asignada después de `t` → SIN_DESTINO «reparado después (<ID_REP>, …)».
8. Origen vacío → SIN_DESTINO «sin caja».
9. Origen de revisar, desconocido o que no cuenta como stock → SIN_DESTINO «<caja> no es vendible».
10. Grado F, FD o RMA → SIN_DESTINO «grado <G> no es vendible».
11. Si no → DEVOLVER a la caja de origen.

La condición «hoy no está en ninguna fila» la garantiza la consulta de candidatos (Task 3); la regla no la vuelve a mirar.

- [ ] **Step 1: Tests que fallan**

`src/test/java/com/reparaciones/servidor/bot/ReglaDevolucionesTest.java`:

```java
package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.*;
import com.reparaciones.servidor.nsys.UbicacionNsys;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La regla de la lista de devoluciones (spec 2026-11-24-bot-devoluciones §5), condición a condición. Datos inventados. */
class ReglaDevolucionesTest {

    private static final LocalDateTime PUESTO = LocalDateTime.of(2026, 11, 23, 9, 10);
    private static final LocalDateTime BOT = LocalDateTime.of(2026, 11, 23, 9, 25);
    private static final LocalDateTime ENTRADA = LocalDateTime.of(2026, 11, 23, 9, 32);
    private static final LocalDateTime SALIO = LocalDateTime.of(2026, 11, 24, 18, 7);
    private static final Salida SALIDA = new Salida(29116, SALIO, "el libro de ventas");
    private static final Map<String, UbicacionNsys> UBICACIONES = Map.of(
            "CAJA LISTOS", new UbicacionNsys("CAJA LISTOS", "Caja · Listos", true, false),
            "Pedidos realizados", new UbicacionNsys("Pedidos realizados", "Pedidos realizados", true, true),
            "Desguace", new UbicacionNsys("Desguace", null, false, false),
            "CAJA NO CUENTA", new UbicacionNsys("CAJA NO CUENTA", "No cuenta", false, false));

    private static Paso descontado(String origen) { return new Paso("DESCONTADO", "OK", origen, BOT); }
    private static Paso devuelto(String resultado, LocalDateTime cuando) { return new Paso("DEVUELTO", resultado, "Pedidos realizados", cuando); }

    private static Datos datos(List<Paso> pasos) {
        return new Datos("350000000000001", pasos, SALIDA, List.of(PUESTO), "DE_PEDIDO", "Pedidos realizados", "A", ENTRADA, List.of());
    }

    private static Datos base() { return datos(List.of(descontado("CAJA LISTOS"))); }

    private static Veredicto evaluar(Datos d) { return ReglaDevoluciones.evaluar(d, UBICACIONES); }

    @Test void elCasoNormalSeDevuelveASuCajaDeAntes() {
        Veredicto v = evaluar(base());
        assertEquals(Tipo.DEVOLVER, v.tipo());
        assertEquals("CAJA LISTOS", v.destino());
        assertEquals(SALIDA, v.salida());
    }

    @Test void sinDescontadoOkDelBotNoSale() {
        assertEquals(Tipo.NADA, evaluar(datos(List.of())).tipo());
        assertEquals(Tipo.NADA, evaluar(datos(List.of(new Paso("DESCONTADO", "ERROR", "CAJA LISTOS", BOT)))).tipo());
    }

    @Test void sinSalidaOSalidaAntesDelDescuentoNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), null, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
        Salida antes = new Salida(29116, BOT.minusMinutes(1), "Lucía");
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), antes, b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void siNuncaEstuvoEnUnaFilaAntesDelDescuentoNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), List.of(BOT.plusMinutes(5)), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), List.of(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void siElEspejoYaNoLoTieneEnPedidosRealizadosNoSale() {
        Datos b = base();
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), "STOCK", "CAJA LISTOS", b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), null, null, b.grado(), b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void siLoSacaronYLoVolvieronAMeterNoSale() {
        Datos b = base();
        LocalDateTime tarde = BOT.plusMinutes(31);
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), tarde, b.trabajos())).tipo());
        assertEquals(Tipo.NADA, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), null, b.trabajos())).tipo());
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), BOT.plusMinutes(30), b.trabajos())).tipo());
    }

    @Test void yaDevueltoONoApareceNoSale() {
        assertEquals(Tipo.NADA, evaluar(datos(List.of(descontado("CAJA LISTOS"), devuelto("OK", BOT.plusDays(1))))).tipo());
        assertEquals(Tipo.NADA, evaluar(datos(List.of(descontado("CAJA LISTOS"), devuelto("NO_APARECE", BOT.plusDays(1))))).tipo());
    }

    @Test void tresErroresSeguidosLoDejanYDosNo() {
        List<Paso> dos = new ArrayList<>(List.of(descontado("CAJA LISTOS"), devuelto("ERROR", BOT.plusHours(1)), devuelto("ERROR", BOT.plusHours(2))));
        assertEquals(Tipo.DEVOLVER, evaluar(datos(dos)).tipo());
        List<Paso> tres = new ArrayList<>(dos);
        tres.add(devuelto("ERROR", BOT.plusHours(3)));
        assertEquals(Tipo.NADA, evaluar(datos(tres)).tipo());
    }

    @Test void unaDevolucionAnteriorAlDescuentoNoCuenta() {
        // Devuelto OK de una venta anterior, y despues otro descuento: vale el ultimo descuento.
        List<Paso> pasos = List.of(new Paso("DESCONTADO", "OK", "CAJA LISTOS", BOT.minusDays(5)), devuelto("OK", BOT.minusDays(4)), descontado("CAJA LISTOS"));
        assertEquals(Tipo.DEVOLVER, evaluar(datos(pasos)).tipo());
    }

    @Test void reparadoDespuesDelDescuentoNoSeDevuelveSolo() {
        Datos b = base();
        List<Trabajo> trabajos = List.of(new Trabajo("R20261101_1", BOT.minusDays(20)), new Trabajo("A20261123_41", BOT.plusHours(1)),
                new Trabajo("R20261123_47", BOT.plusHours(2)));
        Veredicto v = evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), b.grado(), b.entradaPedidos(), trabajos));
        assertEquals(Tipo.SIN_DESTINO, v.tipo());
        assertEquals("reparado después (A20261123_41, R20261123_47)", v.motivo());
        assertEquals(SALIDA, v.salida());
    }

    @Test void sinCajaDeOrigen() {
        assertEquals("sin caja", evaluar(datos(List.of(descontado(null)))).motivo());
        assertEquals("sin caja", evaluar(datos(List.of(descontado("  ")))).motivo());
        assertEquals(Tipo.SIN_DESTINO, evaluar(datos(List.of(descontado(null)))).tipo());
    }

    @Test void cajaDeOrigenQueNoEsVendible() {
        for (String caja : List.of("Pendiente revisar si hay error de inventario", "MV Pendiente revisar si hay error de inventario",
                "Pedidos realizados", "Desguace", "CAJA NO CUENTA", "CAJA QUE NO CONOCE EL ERP")) {
            Veredicto v = evaluar(datos(List.of(descontado(caja))));
            assertEquals(Tipo.SIN_DESTINO, v.tipo(), caja);
            assertEquals(caja + " no es vendible", v.motivo());
        }
    }

    @Test void gradoQueNoSeVende() {
        Datos b = base();
        for (String grado : List.of("F", "fd", "RMA")) {
            Veredicto v = evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), grado, b.entradaPedidos(), b.trabajos()));
            assertEquals(Tipo.SIN_DESTINO, v.tipo(), grado);
            assertEquals("grado " + grado.toUpperCase() + " no es vendible", v.motivo());
        }
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), "A-100", b.entradaPedidos(), b.trabajos())).tipo());
        assertEquals(Tipo.DEVOLVER, evaluar(new Datos(b.imei(), b.pasos(), b.salida(), b.puestos(), b.estadoEspejo(), b.cajaEspejo(), null, b.entradaPedidos(), b.trabajos())).tipo());
    }

    @Test void erroresSeguidosAlFinalCuentaSoloLosUltimos() {
        assertEquals(0, ReglaDevoluciones.erroresSeguidosAlFinal(List.of()));
        assertEquals(1, ReglaDevoluciones.erroresSeguidosAlFinal(List.of(devuelto("ERROR", BOT), devuelto("NO_APARECE", BOT), devuelto("ERROR", BOT))));
        assertEquals(2, ReglaDevoluciones.erroresSeguidosAlFinal(List.of(devuelto("ERROR", BOT), devuelto("ERROR", BOT))));
    }
}
```

- [ ] **Step 2: Ver que falla**

Run: `mvn -q test -Dtest=ReglaDevolucionesTest 2>&1 | tail -15`
Expected: error de compilación (`cannot find symbol ... ReglaDevoluciones`).

- [ ] **Step 3: Implementar**

`src/main/java/com/reparaciones/servidor/bot/ReglaDevoluciones.java`:

```java
package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.Normalizacion;
import com.reparaciones.servidor.nsys.UbicacionNsys;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** La regla de la lista de devoluciones del bot (spec 2026-11-24-bot-devoluciones §5, con «reparado después» del 25/11). Pura:
 *  recibe lo que se sabe de un IMEI y dice si se devuelve, a dónde, o por qué no se devuelve solo. */
public final class ReglaDevoluciones {

    public static final String CAJA_PEDIDOS = "Pedidos realizados";
    /** Las cajas de revisar del bot (LOCATIONS_NO_TOCAR de stock/nsys_bot_completo.py): nunca son destino. */
    public static final Set<String> CAJAS_REVISAR = Set.of("Pendiente revisar si hay error de inventario",
            "MV Pendiente revisar si hay error de inventario");
    /** La entrada en «Pedidos realizados» que ve el espejo cae, como mucho, a esta distancia del descuento del bot. */
    public static final Duration MARGEN_ENTRADA = Duration.ofMinutes(30);
    public static final int ERRORES_SEGUIDOS = 3;

    public static final String DESCONTADO = "DESCONTADO";
    public static final String DEVUELTO = "DEVUELTO";
    public static final String OK = "OK";
    public static final String NO_APARECE = "NO_APARECE";
    public static final String ERROR = "ERROR";
    public static final Set<String> ACCIONES = Set.of(DESCONTADO, DEVUELTO);
    public static final Set<String> RESULTADOS = Set.of(OK, NO_APARECE, ERROR);
    /** El unico motivo de hoy; la reserva de IMEI añadira RESERVA_LIBERADA. */
    public static final String IMEI_CORREGIDO = "IMEI_CORREGIDO";

    /** Una fila de Bot_movimiento (hora de Madrid). */
    public record Paso(String accion, String resultado, String origen, LocalDateTime fecha) {}
    /** El ultimo apunte en que el IMEI salio de una fila: la fila, cuando (Madrid) y quien («Lucía», «el libro de ventas»…). */
    public record Salida(int idFila, LocalDateTime fecha, String quien) {}
    /** Un trabajo del taller del IMEI, con su asignacion en hora de Madrid. */
    public record Trabajo(String idRep, LocalDateTime asignado) {}
    /** Lo que se sabe de un IMEI candidato: sus pasos del bot (por fecha), su ultima salida de una fila, cuando se puso en filas,
     *  el espejo (estado y caja del lote que esta de pedido, si lo hay; grado de NSYS), su ultima entrada en «Pedidos realizados»
     *  segun Nsys_evento y sus trabajos del taller. */
    public record Datos(String imei, List<Paso> pasos, Salida salida, List<LocalDateTime> puestos, String estadoEspejo,
                        String cajaEspejo, String grado, LocalDateTime entradaPedidos, List<Trabajo> trabajos) {}

    public enum Tipo { DEVOLVER, SIN_DESTINO, NADA }

    /** DEVOLVER con su destino; SIN_DESTINO con el motivo (va al historial de la fila de la salida); NADA. */
    public record Veredicto(Tipo tipo, String destino, String motivo, Salida salida) {
        public static final Veredicto NADA = new Veredicto(Tipo.NADA, null, null, null);
    }

    private ReglaDevoluciones() {}

    public static Veredicto evaluar(Datos d, Map<String, UbicacionNsys> ubicaciones) {
        Paso descuento = d.pasos().stream()
                .filter(p -> DESCONTADO.equals(p.accion()) && OK.equals(p.resultado()) && p.fecha() != null)
                .max(Comparator.comparing(Paso::fecha)).orElse(null);
        if (descuento == null) return Veredicto.NADA;
        LocalDateTime t = descuento.fecha();
        Salida salida = d.salida();
        if (salida == null || !salida.fecha().isAfter(t)) return Veredicto.NADA;
        if (d.puestos().stream().noneMatch(p -> !p.isAfter(t))) return Veredicto.NADA;
        if (!EstadoMovil.DE_PEDIDO.name().equals(d.estadoEspejo()) || !CAJA_PEDIDOS.equals(d.cajaEspejo())) return Veredicto.NADA;
        LocalDateTime entrada = d.entradaPedidos();
        if (entrada == null || entrada.isBefore(t.minus(MARGEN_ENTRADA)) || entrada.isAfter(t.plus(MARGEN_ENTRADA))) return Veredicto.NADA;
        List<Paso> devueltos = d.pasos().stream()
                .filter(p -> DEVUELTO.equals(p.accion()) && p.fecha() != null && !p.fecha().isBefore(t)).toList();
        if (devueltos.stream().anyMatch(p -> OK.equals(p.resultado()) || NO_APARECE.equals(p.resultado()))) return Veredicto.NADA;
        if (erroresSeguidosAlFinal(devueltos) >= ERRORES_SEGUIDOS) return Veredicto.NADA;

        List<String> reparaciones = d.trabajos().stream()
                .filter(tr -> tr.asignado() != null && tr.asignado().isAfter(t)).map(Trabajo::idRep).toList();
        if (!reparaciones.isEmpty()) return sinDestino("reparado después (" + String.join(", ", reparaciones) + ")", salida);
        String caja = descuento.origen() == null ? "" : descuento.origen().strip();
        if (caja.isEmpty()) return sinDestino("sin caja", salida);
        if (!vendible(caja, ubicaciones.get(caja))) return sinDestino(caja + " no es vendible", salida);
        String grado = Normalizacion.gradoSinBateria(d.grado()).strip().toUpperCase(Locale.ROOT);
        if (EstadoMovil.NO_VENDIBLES.contains(grado)) return sinDestino("grado " + grado + " no es vendible", salida);
        return new Veredicto(Tipo.DEVOLVER, caja, null, salida);
    }

    /** Cuantos ERROR seguidos hay al final de la lista (en su orden). */
    public static int erroresSeguidosAlFinal(List<Paso> devueltos) {
        int n = 0;
        for (int i = devueltos.size() - 1; i >= 0 && ERROR.equals(devueltos.get(i).resultado()); i--) n++;
        return n;
    }

    /** Una caja de NSYS a la que se puede devolver: conocida por el ERP, cuenta como stock, no es «Pedidos realizados» ni de revisar. */
    static boolean vendible(String caja, UbicacionNsys ubicacion) {
        return !CAJAS_REVISAR.contains(caja) && ubicacion != null && EstadoMovil.de("Available", ubicacion) == EstadoMovil.STOCK;
    }

    private static Veredicto sinDestino(String motivo, Salida salida) {
        return new Veredicto(Tipo.SIN_DESTINO, null, motivo, salida);
    }
}
```

- [ ] **Step 4: Ver que pasa**

Run: `mvn -q test -Dtest=ReglaDevolucionesTest 2>&1 | tail -15`
Expected: `Tests run: 14, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/bot/ReglaDevoluciones.java src/test/java/com/reparaciones/servidor/bot/ReglaDevolucionesTest.java
git commit -m "feat(bot): regla de la lista de devoluciones (condiciones, destino vendible, reparado despues)"
```

---

### Task 3: Lecturas del DAO y servicio `Devoluciones`

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/bot/BotDAO.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/ListaDevoluciones.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/MovimientosBot.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/InformeMovimientos.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/Devoluciones.java`
- Test: `src/test/java/com/reparaciones/servidor/bot/BotDAOTest.java` (ampliar), `src/test/java/com/reparaciones/servidor/bot/DevolucionesTest.java`

**Interfaces:**
- Consumes: Task 1 (`BotDAO.guardar`, `BotDAO.apuntar`, `MovimientoBot`, `CambioImei.*`), Task 2 (`ReglaDevoluciones.*`),
  `NsysDAO.porImeis(Collection<String>) → List<MovilNsys>` (de cada IMEI, el lote más reciente primero; `MovilNsys.imeiSn()`,
  `.estado()`, `.location()`, `.grade()`), `NsysDAO.cargarUbicaciones() → List<UbicacionNsys>`,
  `NsysDAO.trabajosDe(Collection<String>) → List<NsysDAO.TrabajoFecha>` (`imei()`, `idRep()`, `asignado()` en Madrid).
- Produces:
  - `record BotDAO.Apunte(int idFila, LocalDateTime fecha, String imeiAnterior, String imeiNuevo, String tipo, String detalle, String usuario)`
    con `String quien()`;
  - `List<String> BotDAO.candidatos(LocalDateTime desde, int tope)`;
  - `Map<String, List<ReglaDevoluciones.Paso>> BotDAO.pasos(Collection<String> imeis)`;
  - `List<BotDAO.Apunte> BotDAO.apuntes(Collection<String> imeis)` (orden FECHA, ID_CAMBIO);
  - `Map<String, LocalDateTime> BotDAO.entradasEnPedidos(Collection<String> imeis)`;
  - `Optional<BotDAO.Apunte> BotDAO.ultimaSalida(String imei)`;
  - `List<String> BotDAO.ultimosDevueltos(String imei)` (resultados, el más reciente primero, hasta 4, desde el último descuento);
  - `record ListaDevoluciones(List<Grupo> grupos)`, `record ListaDevoluciones.Grupo(String destino, List<ImeiADevolver> imeis)`,
    `record ListaDevoluciones.ImeiADevolver(String imei, String motivo)`;
  - `record MovimientosBot(String run, List<MovimientoBot> movimientos)`; `record InformeMovimientos(int guardados, int repetidos)`;
  - `@Service Devoluciones(BotDAO dao, NsysDAO nsys)`; `ListaDevoluciones lista()`; `InformeMovimientos registrar(MovimientosBot cuerpo)`.

- [ ] **Step 1: Records de la respuesta y del cuerpo**

`ListaDevoluciones.java`:

```java
package com.reparaciones.servidor.bot;

import java.util.List;

/** GET /api/bot/devoluciones (spec 2026-11-24-bot-devoluciones §6.1): los IMEI a devolver, agrupados por la caja de NSYS de destino,
 *  en orden de caja y de IMEI. motivo: IMEI_CORREGIDO (la reserva de IMEI añadira RESERVA_LIBERADA). */
public record ListaDevoluciones(List<Grupo> grupos) {
    public record Grupo(String destino, List<ImeiADevolver> imeis) {}
    public record ImeiADevolver(String imei, String motivo) {}
}
```

`MovimientosBot.java`:

```java
package com.reparaciones.servidor.bot;

import java.util.List;

/** POST /api/bot/movimientos (spec §6.2): la vuelta del bot y lo que movio en ella. */
public record MovimientosBot(String run, List<MovimientoBot> movimientos) {}
```

`InformeMovimientos.java`:

```java
package com.reparaciones.servidor.bot;

/** Respuesta del POST: cuantos movimientos se guardaron y cuantos ya estaban (reenvio). */
public record InformeMovimientos(int guardados, int repetidos) {}
```

- [ ] **Step 2: Tests del servicio que fallan**

`src/test/java/com/reparaciones/servidor/bot/DevolucionesTest.java`:

```java
package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.Paso;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.UbicacionNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El servicio de devoluciones: junta los datos, aplica la regla, agrupa y apunta (DAO simulados; IMEI inventados). */
class DevolucionesTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 25, 10, 0);
    private static final LocalDateTime PUESTO = LocalDateTime.of(2026, 11, 23, 9, 10);
    private static final LocalDateTime BOT = LocalDateTime.of(2026, 11, 23, 9, 25);
    private static final LocalDateTime SALIO = LocalDateTime.of(2026, 11, 24, 18, 7);
    private static final String A = "350000000000001", B = "350000000000002", C = "350000000000003";

    private final BotDAO dao = mock(BotDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private Devoluciones servicio;

    @BeforeEach void preparar() {
        servicio = new Devoluciones(dao, nsys, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
        when(nsys.cargarUbicaciones()).thenReturn(List.of(new UbicacionNsys("CAJA LISTOS", "Caja · Listos", true, false),
                new UbicacionNsys("CAJA GLASS", "Caja · Glass", true, false)));
    }

    private static MovilNsys movil(String imei) {
        MovilNsys m = mock(MovilNsys.class);
        when(m.imeiSn()).thenReturn(imei);
        when(m.estado()).thenReturn(EstadoMovil.DE_PEDIDO);
        when(m.location()).thenReturn("Pedidos realizados");
        when(m.grade()).thenReturn("A");
        return m;
    }

    /** Tres candidatos que cumplen; A y B van a CAJA LISTOS, C a CAJA GLASS. */
    private void tresCandidatos() {
        List<String> imeis = List.of(A, B, C);
        when(dao.candidatos(AHORA.minusDays(Devoluciones.DIAS_DESCONTADO), Devoluciones.TOPE_CANDIDATOS)).thenReturn(imeis);
        when(dao.pasos(imeis)).thenReturn(Map.of(
                A, List.of(new Paso("DESCONTADO", "OK", "CAJA LISTOS", BOT)),
                B, List.of(new Paso("DESCONTADO", "OK", "CAJA LISTOS", BOT)),
                C, List.of(new Paso("DESCONTADO", "OK", "CAJA GLASS", BOT))));
        when(dao.apuntes(imeis)).thenReturn(List.of(
                new BotDAO.Apunte(10, PUESTO, null, A, "IMEI", null, "Fati"),
                new BotDAO.Apunte(11, PUESTO, null, B, "IMEI", null, "Fati"),
                new BotDAO.Apunte(12, PUESTO, null, C, "IMEI", null, "Fati"),
                new BotDAO.Apunte(10, SALIO, A, "359999999999990", "IMEI", "LIBRO", null),
                new BotDAO.Apunte(11, SALIO, B, null, "IMEI", null, "Lucía"),
                new BotDAO.Apunte(12, SALIO, C, null, "IMEI", null, "Lucía")));
        when(dao.entradasEnPedidos(imeis)).thenReturn(Map.of(A, BOT.plusMinutes(7), B, BOT.plusMinutes(7), C, BOT.plusMinutes(7)));
        List<MovilNsys> moviles = List.of(movil(C), movil(A), movil(B));
        when(nsys.porImeis(imeis)).thenReturn(moviles);
        when(nsys.trabajosDe(imeis)).thenReturn(List.of());
    }

    @Test void sinCandidatosLaListaEstaVaciaYNoConsultaNadaMas() {
        when(dao.candidatos(any(), anyInt())).thenReturn(List.of());
        assertEquals(new ListaDevoluciones(List.of()), servicio.lista());
        verify(nsys, never()).porImeis(any());
    }

    @Test void agrupaPorDestinoEnOrdenDeCajaYDeImei() {
        tresCandidatos();
        ListaDevoluciones lista = servicio.lista();
        assertEquals(List.of(
                new ListaDevoluciones.Grupo("CAJA GLASS", List.of(new ListaDevoluciones.ImeiADevolver(C, "IMEI_CORREGIDO"))),
                new ListaDevoluciones.Grupo("CAJA LISTOS", List.of(new ListaDevoluciones.ImeiADevolver(A, "IMEI_CORREGIDO"),
                        new ListaDevoluciones.ImeiADevolver(B, "IMEI_CORREGIDO")))), lista.grupos());
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void unReparadoDespuesNoSaleYSeApuntaUnaVezEnLaFilaDeLaSalida() {
        tresCandidatos();
        when(nsys.trabajosDe(List.of(A, B, C))).thenReturn(List.of(new NsysDAO.TrabajoFecha(C, "R20261123_47", BOT.plusHours(1))));
        ListaDevoluciones lista = servicio.lista();
        assertEquals(1, lista.grupos().size());
        verify(dao).apuntar(12, AHORA, C, "SIN_DESTINO", "reparado después (R20261123_47)");
    }

    @Test void siElSinDestinoYaEstaApuntadoEnEsaFilaNoSeRepite() {
        tresCandidatos();
        when(nsys.trabajosDe(List.of(A, B, C))).thenReturn(List.of(new NsysDAO.TrabajoFecha(C, "R20261123_47", BOT.plusHours(1))));
        List<BotDAO.Apunte> conApunte = new java.util.ArrayList<>(dao.apuntes(List.of(A, B, C)));
        conApunte.add(new BotDAO.Apunte(12, AHORA.minusHours(1), C, null, "SIN_DESTINO", "reparado después (R20261123_47)", null));
        when(dao.apuntes(List.of(A, B, C))).thenReturn(conApunte);
        servicio.lista();
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void elEspejoPrefiereElLoteQueEstaDePedido() {
        tresCandidatos();
        MovilNsys otroLote = mock(MovilNsys.class);
        when(otroLote.imeiSn()).thenReturn(A);
        when(otroLote.estado()).thenReturn(EstadoMovil.BAJA);
        when(otroLote.location()).thenReturn("");
        when(nsys.porImeis(List.of(A, B, C))).thenReturn(List.of(otroLote, movil(A), movil(B), movil(C)));
        assertEquals(2, servicio.lista().grupos().get(1).imeis().size());
    }

    @Test void elTopeCortaLaListaEnOrden() {
        tresCandidatos();
        // Tope 2: el primer grupo entero (CAJA GLASS con C) y el primero del segundo (CAJA LISTOS con A).
        ListaDevoluciones lista = servicio.lista(2);
        assertEquals(2, lista.grupos().size());
        assertEquals("CAJA GLASS", lista.grupos().get(0).destino());
        assertEquals(List.of(new ListaDevoluciones.ImeiADevolver(A, "IMEI_CORREGIDO")), lista.grupos().get(1).imeis());
    }

    @Test void registrarGuardaCuentaRepetidosYApuntaLasDevoluciones() {
        MovimientoBot desc = new MovimientoBot(A, "DESCONTADO", "OK", "CAJA LISTOS", "Pedidos realizados", null, BOT);
        MovimientoBot dev = new MovimientoBot(B, "DEVUELTO", "OK", "Pedidos realizados", "CAJA LISTOS", "IMEI_CORREGIDO", AHORA);
        MovimientoBot noAp = new MovimientoBot(C, "DEVUELTO", "NO_APARECE", null, "CAJA GLASS", "IMEI_CORREGIDO", AHORA);
        when(dao.guardar(eq("run-7"), any(), eq(AHORA))).thenReturn(0, 1, 1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.of(new BotDAO.Apunte(11, SALIO, B, null, "IMEI", null, "Lucía")));
        when(dao.ultimaSalida(C)).thenReturn(Optional.of(new BotDAO.Apunte(12, SALIO, C, null, "IMEI", "LIBRO", null)));
        assertEquals(new InformeMovimientos(2, 1), servicio.registrar(new MovimientosBot("run-7", List.of(desc, dev, noAp))));
        verify(dao).apuntar(11, AHORA, B, "DEVUELTO", "de Pedidos realizados a CAJA LISTOS; IMEI cambiado por Lucía el 24/11 18:07");
        verify(dao).apuntar(12, AHORA, C, "NO_DEVUELTO", "no aparece en NSYS en Pedidos realizados");
        verify(dao, never()).apuntar(anyInt(), any(), eq(A), any(), any());
    }

    @Test void elTercerErrorSeguidoSeApuntaUnaSolaVez() {
        MovimientoBot err = new MovimientoBot(B, "DEVUELTO", "ERROR", null, "CAJA LISTOS", "IMEI_CORREGIDO", AHORA);
        when(dao.guardar(any(), any(), any())).thenReturn(1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.of(new BotDAO.Apunte(11, SALIO, B, null, "IMEI", null, "Lucía")));
        when(dao.ultimosDevueltos(B)).thenReturn(List.of("ERROR", "ERROR"), List.of("ERROR", "ERROR", "ERROR"),
                List.of("ERROR", "ERROR", "ERROR", "ERROR"));
        servicio.registrar(new MovimientosBot("r2", List.of(err)));
        servicio.registrar(new MovimientosBot("r3", List.of(err)));
        servicio.registrar(new MovimientosBot("r4", List.of(err)));
        verify(dao, times(1)).apuntar(11, AHORA, B, "NO_DEVUELTO", "3 errores seguidos");
    }

    @Test void unaDevolucionSinFilaDeSalidaSeGuardaPeroNoApunta() {
        when(dao.guardar(any(), any(), any())).thenReturn(1);
        when(dao.ultimaSalida(B)).thenReturn(Optional.empty());
        servicio.registrar(new MovimientosBot("r", List.of(new MovimientoBot(B, "DEVUELTO", "OK", null, "CAJA LISTOS", null, AHORA))));
        verify(dao, never()).apuntar(anyInt(), any(), any(), any(), any());
    }

    @Test void quienDeUnApunte() {
        assertEquals("Lucía", new BotDAO.Apunte(1, SALIO, A, null, "IMEI", null, "Lucía").quien());
        assertEquals("el libro de ventas", new BotDAO.Apunte(1, SALIO, A, null, "IMEI", "LIBRO", null).quien());
        assertEquals("un proceso", new BotDAO.Apunte(1, SALIO, A, null, "IMEI", null, null).quien());
    }
}
```

Nota para el implementador: `MovilNsys` es un record; si Mockito no puede simularlo (records son `final`), crear los móviles con
su constructor real (mirar `MovilNsys.java` y los tests de `nsys/` que ya lo construyen, p. ej. con un helper) en vez de
`mock(MovilNsys.class)`. Mockito 5 (inline) sí simula clases finales; comprobar la versión en `pom.xml` antes de cambiar nada.

- [ ] **Step 3: Tests del DAO que fallan** (añadir a `BotDAOTest`)

```java
    @Test void candidatosSaleDeLosDescontadosQueSalieronDeUnaFilaYHoyNoEstanEnNinguna() {
        LocalDateTime desde = LocalDateTime.of(2026, 8, 27, 10, 0);
        when(jdbc.queryForList(BotDAO.SQL_CANDIDATOS, String.class, Timestamp.valueOf(desde), 1000)).thenReturn(List.of("350000000000001"));
        assertEquals(List.of("350000000000001"), dao.candidatos(desde, 1000));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("ACCION = 'DESCONTADO' AND b.RESULTADO = 'OK'"));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = b.IMEI"));
        assertTrue(BotDAO.SQL_CANDIDATOS.contains("NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE f.IMEI = b.IMEI)"));
    }

    @Test void sinImeisLasLecturasNoConsultan() {
        assertEquals(Map.of(), dao.pasos(List.of()));
        assertEquals(List.of(), dao.apuntes(List.of()));
        assertEquals(Map.of(), dao.entradasEnPedidos(List.of()));
        verifyNoInteractions(jdbc);
    }

    @Test void lasLecturasConImeisUsanUnInConSusMarcas() {
        dao.pasos(List.of("350000000000001", "350000000000002"));
        verify(jdbc).query(eq(BotDAO.SQL_PASOS + "?, ?" + BotDAO.ORDEN_PASOS), any(RowMapper.class), eq("350000000000001"), eq("350000000000002"));
    }
```

(Imports a añadir: `java.util.List`, `java.util.Map`, `org.springframework.jdbc.core.RowMapper`,
`static org.junit.jupiter.api.Assertions.assertTrue`, `static org.mockito.ArgumentMatchers.eq`; el test `lasLecturas…` lleva
`@SuppressWarnings("unchecked")`.)

- [ ] **Step 4: Ver que fallan**

Run: `mvn -q test -Dtest='BotDAOTest,DevolucionesTest' 2>&1 | tail -20`
Expected: errores de compilación (`cannot find symbol ... Devoluciones`, `SQL_CANDIDATOS`, `Apunte`…).

- [ ] **Step 5: Lecturas del DAO**

Añadir a `BotDAO.java` (imports: `com.reparaciones.servidor.bot.ReglaDevoluciones.Paso`, `java.sql.ResultSet`,
`java.sql.SQLException`, `java.util.*`):

```java
    /** Un apunte de Pedido_fila_cambio de los que mira la lista: los de IMEI (sale o entra) y los SIN_DESTINO. */
    public record Apunte(int idFila, LocalDateTime fecha, String imeiAnterior, String imeiNuevo, String tipo, String detalle, String usuario) {
        /** Quien quito el IMEI, para el historial: la persona, el libro de ventas (detalle LIBRO) o un proceso. */
        public String quien() {
            if (usuario != null) return usuario;
            return "LIBRO".equals(detalle) ? "el libro de ventas" : "un proceso";
        }
    }

    /** Los IMEI con un descuento OK del bot desde {@code ?}, que salieron alguna vez de una fila y que hoy no estan en ninguna
     *  (vender gana a devolver): como mucho {@code ?}. */
    static final String SQL_CANDIDATOS = "SELECT DISTINCT b.IMEI FROM Bot_movimiento b"
            + " WHERE b.ACCION = 'DESCONTADO' AND b.RESULTADO = 'OK' AND b.FECHA >= ?"
            + " AND EXISTS (SELECT 1 FROM Pedido_fila_cambio c WHERE c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = b.IMEI)"
            + " AND NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE f.IMEI = b.IMEI)"
            + " ORDER BY b.IMEI LIMIT ?";
    static final String SQL_PASOS = "SELECT IMEI, ACCION, RESULTADO, ORIGEN_NSYS, FECHA FROM Bot_movimiento WHERE IMEI IN (";
    static final String ORDEN_PASOS = ") ORDER BY FECHA, ID";
    static final String SQL_APUNTES = "SELECT c.ID_FILA, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE, u.NOMBRE_USUARIO"
            + " FROM Pedido_fila_cambio c LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"
            + " WHERE c.TIPO IN ('IMEI', 'SIN_DESTINO') AND (c.IMEI_ANTERIOR IN (%s) OR c.IMEI_NUEVO IN (%s))"
            + " ORDER BY c.FECHA, c.ID_CAMBIO";
    static final String SQL_ENTRADAS = "SELECT IMEI_SN, MAX(FECHA) AS ENTRADA FROM Nsys_evento"
            + " WHERE TIPO = 'UBICACION' AND A_NSYS = 'Pedidos realizados' AND IMEI_SN IN (%s) GROUP BY IMEI_SN";
    static final String SQL_ULTIMA_SALIDA = "SELECT c.ID_FILA, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE, u.NOMBRE_USUARIO"
            + " FROM Pedido_fila_cambio c LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"
            + " WHERE c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = ? ORDER BY c.FECHA DESC, c.ID_CAMBIO DESC LIMIT 1";
    /** Los resultados de los DEVUELTO desde el ultimo descuento OK, el mas reciente primero (hasta 4: basta para saber si el
     *  que acaba de llegar es el tercer ERROR seguido). */
    static final String SQL_ULTIMOS_DEVUELTOS = "SELECT RESULTADO FROM Bot_movimiento WHERE IMEI = ? AND ACCION = 'DEVUELTO'"
            + " AND FECHA >= COALESCE((SELECT MAX(d.FECHA) FROM Bot_movimiento d WHERE d.IMEI = ? AND d.ACCION = 'DESCONTADO'"
            + " AND d.RESULTADO = 'OK'), '1970-01-01') ORDER BY FECHA DESC, ID DESC LIMIT 4";

    public List<String> candidatos(LocalDateTime desde, int tope) {
        return jdbc.queryForList(SQL_CANDIDATOS, String.class, Timestamp.valueOf(desde), tope);
    }

    public Map<String, List<Paso>> pasos(Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, List<Paso>> porImei = new HashMap<>();
        jdbc.query(SQL_PASOS + marcas(imeis.size()) + ORDEN_PASOS, (ResultSet rs, int n) -> {
            porImei.computeIfAbsent(rs.getString("IMEI"), k -> new ArrayList<>()).add(new Paso(rs.getString("ACCION"),
                    rs.getString("RESULTADO"), rs.getString("ORIGEN_NSYS"), rs.getTimestamp("FECHA").toLocalDateTime()));
            return null;
        }, imeis.toArray());
        return porImei;
    }

    public List<Apunte> apuntes(Collection<String> imeis) {
        if (imeis.isEmpty()) return List.of();
        String m = marcas(imeis.size());
        List<Object> args = new ArrayList<>(imeis);
        args.addAll(imeis);
        return jdbc.query(String.format(SQL_APUNTES, m, m), BotDAO::apunte, args.toArray());
    }

    public Map<String, LocalDateTime> entradasEnPedidos(Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, LocalDateTime> entradas = new HashMap<>();
        jdbc.query(String.format(SQL_ENTRADAS, marcas(imeis.size())), (ResultSet rs, int n) -> {
            entradas.put(rs.getString("IMEI_SN"), rs.getTimestamp("ENTRADA").toLocalDateTime());
            return null;
        }, imeis.toArray());
        return entradas;
    }

    public Optional<Apunte> ultimaSalida(String imei) {
        return jdbc.query(SQL_ULTIMA_SALIDA, BotDAO::apunte, imei).stream().findFirst();
    }

    public List<String> ultimosDevueltos(String imei) {
        return jdbc.queryForList(SQL_ULTIMOS_DEVUELTOS, String.class, imei, imei);
    }

    static Apunte apunte(ResultSet rs, int n) throws SQLException {
        return new Apunte(rs.getInt("ID_FILA"), rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("IMEI_ANTERIOR"),
                rs.getString("IMEI_NUEVO"), rs.getString("TIPO"), rs.getString("DETALLE"), rs.getString("NOMBRE_USUARIO"));
    }

    private static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }
```

- [ ] **Step 6: El servicio**

`src/main/java/com/reparaciones/servidor/bot/Devoluciones.java`:

```java
package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.Datos;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Salida;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Trabajo;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Veredicto;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.UbicacionNsys;
import com.reparaciones.servidor.pedidos.CambioImei;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** La lista de devoluciones del bot y lo que el bot informa (spec 2026-11-24-bot-devoluciones §5 y §6). */
@Service
public class Devoluciones {

    /** Solo se miran los descuentos de los ultimos 90 dias. */
    static final int DIAS_DESCONTADO = 90;
    static final int TOPE_CANDIDATOS = 1000;
    /** Tope defensivo de IMEI en la lista. */
    static final int TOPE_LISTA = 500;
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final BotDAO dao;
    private final NsysDAO nsys;
    private final Clock clock;

    @Autowired
    public Devoluciones(BotDAO dao, NsysDAO nsys) {
        this(dao, nsys, Clock.system(ImportadorNsys.MADRID));
    }

    Devoluciones(BotDAO dao, NsysDAO nsys, Clock clock) {
        this.dao = dao;
        this.nsys = nsys;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ListaDevoluciones lista() {
        return lista(TOPE_LISTA);
    }

    /** La lista con un tope dado; los SIN_DESTINO nuevos se apuntan en la fila de la salida (una vez por IMEI y fila). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ListaDevoluciones lista(int tope) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<String> imeis = dao.candidatos(ahora.minusDays(DIAS_DESCONTADO), TOPE_CANDIDATOS);
        if (imeis.isEmpty()) return new ListaDevoluciones(List.of());
        Map<String, List<ReglaDevoluciones.Paso>> pasos = dao.pasos(imeis);
        List<BotDAO.Apunte> apuntes = dao.apuntes(imeis);
        Map<String, LocalDateTime> entradas = dao.entradasEnPedidos(imeis);
        Map<String, MovilNsys> espejo = new HashMap<>();
        for (MovilNsys m : nsys.porImeis(imeis))
            espejo.merge(m.imeiSn(), m, (viejo, nuevo) -> viejo.estado() != EstadoMovil.DE_PEDIDO && nuevo.estado() == EstadoMovil.DE_PEDIDO ? nuevo : viejo);
        Map<String, UbicacionNsys> ubicaciones = new HashMap<>();
        for (UbicacionNsys u : nsys.cargarUbicaciones()) ubicaciones.put(u.location(), u);
        Map<String, List<Trabajo>> trabajos = new HashMap<>();
        for (NsysDAO.TrabajoFecha t : nsys.trabajosDe(imeis))
            trabajos.computeIfAbsent(t.imei(), k -> new ArrayList<>()).add(new Trabajo(t.idRep(), t.asignado()));

        Map<String, List<String>> porDestino = new TreeMap<>();
        for (String imei : imeis) {
            MovilNsys m = espejo.get(imei);
            Datos d = new Datos(imei, pasos.getOrDefault(imei, List.of()), salida(apuntes, imei), puestos(apuntes, imei),
                    m == null || m.estado() == null ? null : m.estado().name(), m == null ? null : m.location(),
                    m == null ? null : m.grade(), entradas.get(imei), trabajos.getOrDefault(imei, List.of()));
            Veredicto v = ReglaDevoluciones.evaluar(d, ubicaciones);
            if (v.tipo() == ReglaDevoluciones.Tipo.DEVOLVER) porDestino.computeIfAbsent(v.destino(), k -> new ArrayList<>()).add(imei);
            else if (v.tipo() == ReglaDevoluciones.Tipo.SIN_DESTINO && !yaApuntado(apuntes, imei, v.salida().idFila()))
                dao.apuntar(v.salida().idFila(), ahora, imei, CambioImei.SIN_DESTINO, v.motivo());
        }
        List<ListaDevoluciones.Grupo> grupos = new ArrayList<>();
        int quedan = tope;
        for (Map.Entry<String, List<String>> e : porDestino.entrySet()) {
            if (quedan <= 0) break;
            List<ListaDevoluciones.ImeiADevolver> suyos = e.getValue().stream().sorted().limit(quedan)
                    .map(i -> new ListaDevoluciones.ImeiADevolver(i, ReglaDevoluciones.IMEI_CORREGIDO)).toList();
            grupos.add(new ListaDevoluciones.Grupo(e.getKey(), suyos));
            quedan -= suyos.size();
        }
        return new ListaDevoluciones(grupos);
    }

    /** Guarda lo que informa el bot (los repetidos no entran) y apunta en la fila de la salida cada devolucion nueva. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InformeMovimientos registrar(MovimientosBot cuerpo) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        int guardados = 0, repetidos = 0;
        for (MovimientoBot m : cuerpo.movimientos()) {
            if (dao.guardar(cuerpo.run(), m, ahora) == 0) {
                repetidos++;
                continue;
            }
            guardados++;
            if (ReglaDevoluciones.DEVUELTO.equals(m.accion())) apuntarDevolucion(m, ahora);
        }
        return new InformeMovimientos(guardados, repetidos);
    }

    private void apuntarDevolucion(MovimientoBot m, LocalDateTime ahora) {
        Optional<BotDAO.Apunte> salida = dao.ultimaSalida(m.imei());
        if (salida.isEmpty()) return;
        int fila = salida.get().idFila();
        switch (m.resultado()) {
            case ReglaDevoluciones.OK -> dao.apuntar(fila, ahora, m.imei(), CambioImei.DEVUELTO, "de Pedidos realizados a " + m.destino()
                    + "; IMEI cambiado por " + salida.get().quien() + " el " + DIA_HORA.format(salida.get().fecha()));
            case ReglaDevoluciones.NO_APARECE -> dao.apuntar(fila, ahora, m.imei(), CambioImei.NO_DEVUELTO,
                    "no aparece en NSYS en Pedidos realizados");
            case ReglaDevoluciones.ERROR -> {
                if (esElTercerError(dao.ultimosDevueltos(m.imei())))
                    dao.apuntar(fila, ahora, m.imei(), CambioImei.NO_DEVUELTO, ReglaDevoluciones.ERRORES_SEGUIDOS + " errores seguidos");
            }
            default -> { }
        }
    }

    /** Los resultados, el mas reciente primero: los tres primeros son ERROR y el cuarto (si hay) no. */
    static boolean esElTercerError(List<String> ultimos) {
        int n = ReglaDevoluciones.ERRORES_SEGUIDOS;
        if (ultimos.size() < n) return false;
        for (int i = 0; i < n; i++) if (!ReglaDevoluciones.ERROR.equals(ultimos.get(i))) return false;
        return ultimos.size() == n || !ReglaDevoluciones.ERROR.equals(ultimos.get(n));
    }

    private static Salida salida(List<BotDAO.Apunte> apuntes, String imei) {
        BotDAO.Apunte ultima = null;
        for (BotDAO.Apunte a : apuntes) if (CambioImei.IMEI.equals(a.tipo()) && imei.equals(a.imeiAnterior())) ultima = a;
        return ultima == null ? null : new Salida(ultima.idFila(), ultima.fecha(), ultima.quien());
    }

    private static List<LocalDateTime> puestos(List<BotDAO.Apunte> apuntes, String imei) {
        return apuntes.stream().filter(a -> CambioImei.IMEI.equals(a.tipo()) && imei.equals(a.imeiNuevo())).map(BotDAO.Apunte::fecha).toList();
    }

    private static boolean yaApuntado(List<BotDAO.Apunte> apuntes, String imei, int idFila) {
        return apuntes.stream().anyMatch(a -> CambioImei.SIN_DESTINO.equals(a.tipo()) && a.idFila() == idFila && imei.equals(a.imeiAnterior()));
    }
}
```

Comprobar que `ImportadorNsys.MADRID` existe y es `public` (lo usa `VendidosController`); si no, `ZoneId.of("Europe/Madrid")`.

- [ ] **Step 7: Ver que pasan**

Run: `mvn -q test -Dtest='BotDAOTest,DevolucionesTest,ReglaDevolucionesTest' 2>&1 | tail -20`
Expected: todos en verde.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/bot src/test/java/com/reparaciones/servidor/bot
git commit -m "feat(bot): lista de devoluciones (candidatos, espejo, reparaciones) y registro de lo que informa el bot con apuntes"
```

---

### Task 4: Rutas de máquina `/api/bot/**`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/security/ClaveBotFilter.java`
- Modify: `src/main/java/com/reparaciones/servidor/config/SecurityConfig.java`
- Modify: `src/main/java/com/reparaciones/servidor/security/JwtAuthFilter.java`
- Create: `src/main/java/com/reparaciones/servidor/bot/BotController.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesBotTest.java`, `RolesBotApagadaTest.java`,
  `src/test/java/com/reparaciones/servidor/bot/BotControllerTest.java`, `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Modify: `docs/api_contract.md`, `docs/autorizacion_endpoints.md`, `docs/deployment.md`, `docs/schema.md`

**Interfaces:**
- Consumes: Task 3 (`Devoluciones.lista()`, `Devoluciones.registrar(MovimientosBot)`, records), `ReglaDevoluciones.ACCIONES`/`RESULTADOS`.
- Produces: `ClaveBotFilter.RUTA_BASE = "/api/bot"`, `ClaveBotFilter.ROL = "BOT_STOCK"`, `ClaveBotFilter.esDeMaquina(HttpServletRequest)`;
  ajuste `pedidos.bot.clave` (variable `PEDIDOS_BOT_CLAVE`).

- [ ] **Step 1: Tests de roles que fallan**

`src/test/java/com/reparaciones/servidor/controller/RolesBotTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.bot.Devoluciones;
import com.reparaciones.servidor.bot.InformeMovimientos;
import com.reparaciones.servidor.bot.ListaDevoluciones;
import com.reparaciones.servidor.pedidos.FilasDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/bot/**: solo con la clave del bot (su propio ajuste); la de vendidos y los tokens de usuario no valen. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-solo-lectura-inventada-000001",
        "pedidos.bot.clave=clave-del-bot-de-stock-inventada-01,corta"
})
class RolesBotTest {

    private static final String BOT = "clave-del-bot-de-stock-inventada-01";
    private static final String VENDIDOS = "clave-solo-lectura-inventada-000001";
    private static final String CUERPO = "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"DESCONTADO\","
            + "\"resultado\":\"OK\",\"origen\":\"CAJA LISTOS\",\"destino\":\"Pedidos realizados\",\"motivo\":null,\"fecha\":\"2026-11-25T10:12:03\"}]}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean Devoluciones devoluciones;
    @MockBean FilasDAO filas;

    private String admin() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void conLaClaveDelBotLeeLaListaYGuarda() throws Exception {
        when(devoluciones.lista()).thenReturn(new ListaDevoluciones(List.of(new ListaDevoluciones.Grupo("CAJA LISTOS",
                List.of(new ListaDevoluciones.ImeiADevolver("350000000000001", "IMEI_CORREGIDO"))))));
        mvc.perform(get("/api/bot/devoluciones").header("X-API-Key", BOT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].destino").value("CAJA LISTOS"))
                .andExpect(jsonPath("$.grupos[0].imeis[0].motivo").value("IMEI_CORREGIDO"));
        when(devoluciones.registrar(any())).thenReturn(new InformeMovimientos(1, 0));
        mvc.perform(post("/api/bot/movimientos").header("X-API-Key", BOT).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guardados").value(1)).andExpect(jsonPath("$.repetidos").value(0));
    }

    @Test void sinClaveConOtraOConLaDeVendidosEs401() throws Exception {
        for (String clave : new String[] {null, "otra-clave", "corta", VENDIDOS}) {
            var g = get("/api/bot/devoluciones");
            var p = post("/api/bot/movimientos").contentType(MediaType.APPLICATION_JSON).content(CUERPO);
            if (clave != null) { g.header("X-API-Key", clave); p.header("X-API-Key", clave); }
            mvc.perform(g).andExpect(status().isUnauthorized());
            mvc.perform(p).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(devoluciones);
    }

    @Test void unTokenDeUsuarioNoVale() throws Exception {
        mvc.perform(get("/api/bot/devoluciones").header("Authorization", admin())).andExpect(status().isUnauthorized());
        verifyNoInteractions(devoluciones);
    }

    @Test void laClaveDelBotNoAbreOtrasRutas() throws Exception {
        mvc.perform(get("/api/pedidos/vendidos").header("X-API-Key", BOT)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/filas").header("X-API-Key", BOT)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").header("X-API-Key", BOT)).andExpect(status().isForbidden());
    }

    @Test void unCuerpoMaloEs400() throws Exception {
        for (String cuerpo : List.of("{\"run\":\"\",\"movimientos\":[]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"VENDIDO\",\"resultado\":\"OK\",\"fecha\":\"2026-11-25T10:12:03\"}]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"DEVUELTO\",\"resultado\":\"QUIZA\",\"fecha\":\"2026-11-25T10:12:03\"}]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"\",\"accion\":\"DEVUELTO\",\"resultado\":\"OK\",\"fecha\":\"2026-11-25T10:12:03\"}]}",
                "{\"run\":\"r1\",\"movimientos\":[{\"imei\":\"350000000000001\",\"accion\":\"DEVUELTO\",\"resultado\":\"OK\"}]}"))
            mvc.perform(post("/api/bot/movimientos").header("X-API-Key", BOT).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(devoluciones);
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/RolesBotApagadaTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.bot.Devoluciones;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin pedidos.bot.clave (aunque haya clave de vendidos) /api/bot/** esta apagada: 404 para todos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "pedidos.vendidos.clave=clave-solo-lectura-inventada-000001",
        "pedidos.bot.clave="
})
class RolesBotApagadaTest {

    @Autowired MockMvc mvc;
    @MockBean Devoluciones devoluciones;

    @Test void conElAjusteVacioEsUn404ParaTodos() throws Exception {
        mvc.perform(get("/api/bot/devoluciones")).andExpect(status().isNotFound());
        mvc.perform(get("/api/bot/devoluciones").header("X-API-Key", "clave-solo-lectura-inventada-000001")).andExpect(status().isNotFound());
        verifyNoInteractions(devoluciones);
    }
}
```

En `OpenApiContractTest.elContratoPublicaLosEsquemasDeLaWeb`, añadir a la lista de rutas `"/api/bot/devoluciones",
"/api/bot/movimientos"`.

- [ ] **Step 2: Ver que fallan**

Run: `mvn -q test -Dtest='RolesBotTest,RolesBotApagadaTest,OpenApiContractTest' 2>&1 | tail -20`
Expected: compilación OK pero 404/403 donde se espera 200/401, y «falta la ruta /api/bot/devoluciones».

- [ ] **Step 3: El filtro**

`src/main/java/com/reparaciones/servidor/security/ClaveBotFilter.java`:

```java
package com.reparaciones.servidor.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La clave de maquina de /api/bot/** (bot de stock de NSYS: GET /api/bot/devoluciones y POST /api/bot/movimientos; spec
 * 2026-11-24-bot-devoluciones §6). Misma regla que {@link ClaveVendidosFilter} con su PROPIO ajuste (PEDIDOS_BOT_CLAVE): ajuste sin
 * ninguna clave valida: 404; clave ausente o mala: 401; un token de usuario no vale; esta clave no abre ninguna otra ruta.
 */
public class ClaveBotFilter extends ClaveVendidosFilter {

    public static final String RUTA_BASE = "/api/bot";
    public static final String ROL = "BOT_STOCK";

    public ClaveBotFilter(String ajuste) {
        super(ajuste, RUTA_BASE, ROL, "bot");
    }

    /** Cualquier ruta bajo /api/bot/. */
    public static boolean esDeMaquina(HttpServletRequest request) {
        return request.getRequestURI().startsWith(RUTA_BASE + "/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !esDeMaquina(request);
    }
}
```

- [ ] **Step 4: SecurityConfig y JwtAuthFilter**

En `SecurityConfig`: campo `private final ClaveBotFilter claveBotFilter;`; parámetro nuevo en el constructor
`@Value("${pedidos.bot.clave:}") String claveBot` y `this.claveBotFilter = new ClaveBotFilter(claveBot);`; en el javadoc del
constructor, una línea: `{@code pedidos.bot.clave} (PEDIDOS_BOT_CLAVE): las claves del bot de stock en /api/bot/** (lee la lista
de devoluciones y escribe lo que mueve); aparte de la de vendidos.`; en `authorizeHttpRequests`, después de la línea de
asociaciones: `.requestMatchers(ClaveBotFilter.RUTA_BASE + "/**").hasRole(ClaveBotFilter.ROL)`; en la cadena:
`.addFilterBefore(claveBotFilter, JwtAuthFilter.class)`. Import `com.reparaciones.servidor.security.ClaveBotFilter`.

En `JwtAuthFilter.shouldNotFilter`, añadir `|| ClaveBotFilter.esDeMaquina(request)` a la condición y al comentario «y /api/bot/**».

- [ ] **Step 5: El controlador**

`src/main/java/com/reparaciones/servidor/bot/BotController.java`:

```java
package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.security.ClaveBotFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Las rutas del bot de stock de NSYS (spec 2026-11-24-bot-devoluciones §6). No son de usuario: solo entra la clave de maquina que
 *  comprueba {@link ClaveBotFilter}. */
@RestController
@RequestMapping(ClaveBotFilter.RUTA_BASE)
@PreAuthorize("hasRole('" + ClaveBotFilter.ROL + "')")
public class BotController {

    static final int MAX_MOVIMIENTOS = 2000;

    private final Devoluciones devoluciones;

    public BotController(Devoluciones devoluciones) {
        this.devoluciones = devoluciones;
    }

    /** Los IMEI a devolver, por caja de destino. */
    @GetMapping("/devoluciones")
    public ListaDevoluciones devoluciones() {
        return devoluciones.lista();
    }

    /** Lo que el bot movio en una vuelta; los repetidos (misma vuelta, IMEI y accion) no se guardan dos veces. */
    @PostMapping("/movimientos")
    public InformeMovimientos movimientos(@RequestBody MovimientosBot cuerpo) {
        validar(cuerpo);
        return devoluciones.registrar(cuerpo);
    }

    static void validar(MovimientosBot c) {
        if (c == null || vacio(c.run()) || c.run().length() > 40) throw mal("run es obligatorio (40 caracteres como mucho)");
        if (c.movimientos() == null || c.movimientos().size() > MAX_MOVIMIENTOS)
            throw mal("movimientos es obligatorio (" + MAX_MOVIMIENTOS + " como mucho)");
        for (MovimientoBot m : c.movimientos()) {
            if (m == null || vacio(m.imei()) || m.imei().length() > 40) throw mal("imei es obligatorio (40 caracteres como mucho)");
            if (!ReglaDevoluciones.ACCIONES.contains(m.accion())) throw mal("accion debe ser DESCONTADO o DEVUELTO");
            if (!ReglaDevoluciones.RESULTADOS.contains(m.resultado())) throw mal("resultado debe ser OK, NO_APARECE o ERROR");
            if (m.fecha() == null) throw mal("fecha es obligatoria");
            if (largo(m.origen(), 80) || largo(m.destino(), 80) || largo(m.motivo(), 30)) throw mal("origen o destino de mas de 80 caracteres, o motivo de mas de 30");
        }
    }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static boolean largo(String s, int max) { return s != null && s.length() > max; }
    private static ResponseStatusException mal(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }
}
```

`src/test/java/com/reparaciones/servidor/bot/BotControllerTest.java`:

```java
package com.reparaciones.servidor.bot;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotControllerTest {

    private static final LocalDateTime F = LocalDateTime.of(2026, 11, 25, 10, 0);

    private static MovimientoBot mov(String origen, String motivo) {
        return new MovimientoBot("350000000000001", "DEVUELTO", "OK", origen, "CAJA LISTOS", motivo, F);
    }

    @Test void unCuerpoBuenoPasaYUnoVacioTambien() {
        assertDoesNotThrow(() -> BotController.validar(new MovimientosBot("r1", List.of(mov(null, null)))));
        assertDoesNotThrow(() -> BotController.validar(new MovimientosBot("r1", List.of())));
    }

    @Test void largosYTopesSon400() {
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r".repeat(41), List.of())));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", List.of(mov("x".repeat(81), null)))));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", List.of(mov(null, "x".repeat(31))))));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1",
                Collections.nCopies(BotController.MAX_MOVIMIENTOS + 1, mov(null, null)))));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", null)));
    }
}
```

- [ ] **Step 6: Ver que pasan**

Run: `mvn -q test -Dtest='RolesBotTest,RolesBotApagadaTest,BotControllerTest,OpenApiContractTest,RolesVendidosTest,RolesSincronizadosTest,RolesAsociacionesTest' 2>&1 | tail -20`
Expected: todo en verde (las de vendidos, sincronizados y asociaciones siguen igual).

- [ ] **Step 7: Documentación**

- `docs/autorizacion_endpoints.md`: junto a las otras rutas de máquina, un punto **«Bot de stock de NSYS»** (`GET
  /api/bot/devoluciones`, `POST /api/bot/movimientos`): no es de ningún rol de usuario; clave de máquina en `X-API-Key`
  (`ClaveBotFilter`, ajuste propio `PEDIDOS_BOT_CLAVE`: la de vendidos da `401` aquí); ajuste sin clave válida `404`, sin clave
  o con otra `401`; un token de usuario no vale; la clave no abre ninguna otra ruta. Y en la tabla de tests:
  `| Clave de máquina del bot de stock (200, 401, 400, token, otras rutas) | controller/RolesBotTest |` y
  `| Esa ruta apagada (404) | controller/RolesBotApagadaTest |`.
- `docs/deployment.md`: tras `PEDIDOS_ASOCIACIONES_CLAVE`, `PEDIDOS_BOT_CLAVE` (por defecto vacío) con el mismo estilo
  (hexadecimal de 32+, distinta en prod y preprod, nunca al repo; vacía = rutas apagadas) y: **«Antes de desplegar el servidor que
  la usa se aplica `sql/migracion-bot-movimiento.sql` (comprobación `COLUMNAS = 10`)»**.
- `docs/api_contract.md`: las dos rutas con los JSON de §6.1 y §6.2 de la spec, la validación (400) y la respuesta
  `{"guardados": n, "repetidos": m}`.
- `docs/schema.md`: la tabla `Bot_movimiento` y los tres tipos nuevos de `Pedido_fila_cambio`.

- [ ] **Step 8: Commit**

```bash
git add -A src docs
git commit -m "feat(bot): rutas de maquina /api/bot/devoluciones y /api/bot/movimientos con clave propia (PEDIDOS_BOT_CLAVE)"
```

---

### Task 5: Web — historial de la fila y versión 0.9.47

**Files:**
- Modify: `C:\Users\dev\Documents\wt\web-bot-devoluciones\src\modules\telefonos\pedidos\historial.ts`
- Test: `C:\Users\dev\Documents\wt\web-bot-devoluciones\src\modules\telefonos\pedidos\historial.test.ts`
- Modify: `package.json` (`"version": "0.9.47"`), `CHANGELOG.md`
- Modify (raíz, `C:\Users\dev\Documents\wt\dev_bot_devoluciones`): `docs/modulos/pedidos.md`

**Interfaces:**
- Consumes: apuntes con `tipo` `DEVUELTO` / `NO_DEVUELTO` / `SIN_DESTINO`, `imeiAnterior` = IMEI, `usuario` nulo, `detalle` texto.

- [ ] **Step 1: Test que falla**

Añadir a `historial.test.ts` (mirar arriba del fichero cómo se construyen `fila` y los `CambioImei`; usar el mismo helper):

```ts
  it('las devoluciones del bot salen como hitos con su detalle, a nombre del bot de NSYS', () => {
    const base = { usuario: null, imeiNuevo: null, fecha: '2026-11-25T10:00:00' }
    const h = construirHistorial(fila, [
      { ...base, tipo: 'DEVUELTO', imeiAnterior: '350000000000001', detalle: 'de Pedidos realizados a CAJA LISTOS; IMEI cambiado por Lucía el 24/11 18:07' },
      { ...base, tipo: 'NO_DEVUELTO', imeiAnterior: '350000000000002', detalle: 'no aparece en NSYS en Pedidos realizados' },
      { ...base, tipo: 'SIN_DESTINO', imeiAnterior: '350000000000003', detalle: 'reparado después (R20261123_47)' },
    ] as CambioImei[])
    const textos = h.filter((e) => e.tipo === 'marca').map((e) => (e.tipo === 'marca' ? [e.texto, e.quien] : null))
    expect(textos).toEqual([
      ['Devuelto a stock 350000000000001: de Pedidos realizados a CAJA LISTOS; IMEI cambiado por Lucía el 24/11 18:07', 'bot de NSYS'],
      ['No se pudo devolver 350000000000002: no aparece en NSYS en Pedidos realizados', 'bot de NSYS'],
      ['No se devuelve solo 350000000000003 (lo mueve una persona): reparado después (R20261123_47)', 'bot de NSYS'],
    ])
  })
```

Si el test file usa otro nombre para la fila de ejemplo, adaptar `fila` a ese nombre (no crear otro helper).

- [ ] **Step 2: Ver que falla**

Run: `cd /c/Users/dev/Documents/wt/web-bot-devoluciones && npx vitest run src/modules/telefonos/pedidos/historial.test.ts 2>&1 | tail -15`
Expected: FAIL (los tres salen como `tipo: 'imei'`).

- [ ] **Step 3: Implementar**

En `historial.ts`, dentro de `construirHistorial`, antes del `return { tipo: 'imei', … }` final:

```ts
    // Devoluciones del bot de NSYS (0.9.47): sin usuario; imeiAnterior = el IMEI que salió de la fila.
    if (cambio.tipo === 'DEVUELTO' || cambio.tipo === 'NO_DEVUELTO' || cambio.tipo === 'SIN_DESTINO') {
      const imei = cambio.imeiAnterior ?? ''
      const texto = cambio.tipo === 'DEVUELTO'
        ? `Devuelto a stock ${imei}`
        : cambio.tipo === 'NO_DEVUELTO' ? `No se pudo devolver ${imei}` : `No se devuelve solo ${imei} (lo mueve una persona)`
      return { tipo: 'marca', clave: `i${i}`, cuando, conHora: true, texto: `${texto}${cambio.detalle ? `: ${cambio.detalle}` : ''}`, quien: 'bot de NSYS' }
    }
```

- [ ] **Step 4: Ver que pasa y suite completa**

Run: `npx vitest run src/modules/telefonos/pedidos/historial.test.ts 2>&1 | tail -5; npx vitest run 2>&1 | tail -5; npx tsc --noEmit -p . 2>&1 | tail -5`
Expected: verde (salvo fallos que ya estaban en origin/main, anotados en la Task 0).

- [ ] **Step 5: Versión**

`package.json`: `"version": "0.9.47"`. `CHANGELOG.md`, encima de 0.9.46.2:

```markdown
## [0.9.47] - 2026-11-25 — El bot de NSYS devuelve a stock los móviles que salieron de un pedido

- Cuando se cambia el IMEI de un pedido después de que el bot de NSYS haya pasado el móvil a «Pedidos realizados», el ERP se lo
  dice al bot y el bot lo devuelve a la caja donde estaba (si esa caja se vende). Queda en el historial de la fila: «Devuelto a
  stock», «No se pudo devolver» o «No se devuelve solo (lo mueve una persona)» con el motivo (sin caja, caja o grado que no se
  vende, reparado después).
- La devolución automática empieza cuando el bot de producción se actualice (plan 3); hasta entonces la lista sale vacía.
```

Raíz, `docs/modulos/pedidos.md`: en la parte del historial de la fila, una línea **«Desde la 0.9.47»** con los tres apuntes del
bot (mismo texto que el CHANGELOG).

- [ ] **Step 6: Commits**

```bash
cd /c/Users/dev/Documents/wt/web-bot-devoluciones
git add src/modules/telefonos/pedidos/historial.ts src/modules/telefonos/pedidos/historial.test.ts package.json CHANGELOG.md
git commit -m "feat(pedidos): historial con las devoluciones del bot de NSYS; version 0.9.47"
cd /c/Users/dev/Documents/wt/dev_bot_devoluciones
git add docs/modulos/pedidos.md
git commit -m "docs: Pedidos, historial con las devoluciones del bot (0.9.47)"
```

---

### Task 6: Verificación final y guion de preprod

- [ ] **Step 1: Suites completas**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/wt/servidor-bot-devoluciones && mvn -q test 2>&1 | tail -20
cd /c/Users/dev/Documents/wt/web-bot-devoluciones && npx vitest run 2>&1 | tail -5 && npm run build 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`; Vitest y build en verde.

- [ ] **Step 2: Guion de preprod (en Apuntes, no en el repo)**

Escribir `C:\Users\dev\Documents\Apuntes\preprod\v0947-preprod.md` con bloques que ejecuta Imad (cada bloque empieza con
`hostname` y `ls -l /usr/local/sbin/backup-erp.sh`, que en preprod debe dar «No such file»; build en primer plano):

1. Migración `sql/migracion-bot-movimiento.sql` (`COLUMNAS = 10`).
2. `PEDIDOS_BOT_CLAVE` en el `.env` de preprod (clave nueva, `openssl rand -hex 24`), despliegue del servidor y la web.
3. E2E con datos sintéticos por API (spec §10.2), sin tocar NSYS:
   - una fila de prueba con un IMEI inventado (o de una fila ya existente de preprod que se restaura al final);
   - `POST /api/bot/movimientos` con un `DESCONTADO · OK` simulado (origen `CAJA LISTOS`);
   - cambiar el IMEI de la fila;
   - `GET /api/bot/devoluciones`: solo sale si el espejo lo tiene en «Pedidos realizados» con la entrada a ±30 min; con un IMEI
     inventado no estará en el espejo → **la lista no lo da (comprobación negativa)**. Para el caso positivo, usar un IMEI de
     preprod que esté hoy `DE_PEDIDO` y poner la `FECHA` del `DESCONTADO` a su última entrada en «Pedidos realizados» (consulta
     de solo lectura para sacarla);
   - `POST` de un `DEVUELTO · OK` → apunte «Devuelto a stock» en el historial de la fila;
   - limpieza: borrar las filas de `Bot_movimiento` con `RUN_ID LIKE 'e2e-%'`, los apuntes de la prueba y restaurar la fila.
4. Comprobar tiempo del `GET` (debe ser < 1 s).

- [ ] **Step 3: Revisión final y entrega**

Revisión de toda la rama (servidor + web) con `superpowers:requesting-code-review`. Después, presentar a Imad: commits, qué falta
(§7 en espera, plan 3 del bot) y pedir OK antes de merge/push/preprod.
