# 0.9.5 — Previsión de pedidos, parámetros solo admin y dos arreglos de la sesión — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que Stock enseñe a supertécnicos y administrador cuánto se gasta al día de cada pieza y cuánto pedir para 15 y 30 días; que solo el administrador cambie el stock mínimo y los pesos de la previsión; y que la sesión cerrada por inactividad y el Enter rápido en las contraseñas se comporten bien.

**Architecture:** En el servidor, una clase pura `PrevisionPedido` reparte el consumo por días en tres tramos y calcula la previsión con aritmética entera; `PrevisionPedidoService` la aplica al listado de Stock con los pesos de una tabla nueva `Parametro` (que leen y escriben `ParametroDAO` y `ParametroController`). En la web, `columnas.tsx` añade tres columnas para SUPERTECNICO y ADMIN, un diálogo propio edita los pesos, y el menú de Stock cambia por rol. Los dos arreglos de la sesión son cambios pequeños en `expiracion.ts`/`main.tsx` y en `medidor.ts`.

**Tech Stack:** Servidor Spring Boot 3.3.4 + JdbcTemplate, tests JUnit 5 + Mockito + MockMvc. Web Vite + React 19 + TanStack Query + openapi-fetch, tests Vitest + msw + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-07-v095-prevision-pedidos-design.md`

## Global Constraints

- **Los tres repos son públicos.** Ni dominios, ni direcciones, ni nombres de personas o proveedores, ni el nombre de la empresa en código, tests, commits ni docs de repo. Lo operativo va a `Documents/Apuntes/`.
- **Commits en español, en minúscula tras el prefijo, sin `Co-Authored-By`.** Nunca `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Claude no hace SSH.** Los comandos de máquina se preparan y los ejecuta el usuario, en una sola sesión por máquina.
- **Producción solo fuera de horario.** Preprod se puede tocar en horario (nadie la usa).
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`
- **Versión del producto: 0.9.5.** Servidor y web etiquetados juntos al final (tras producción).
- **Ramas:** `feature/prevision-pedidos` en el servidor (la crea la Task 1) y en la web (la crea la Task 6).
- **Regla exacta** (spec §3.1): tramos `[hoy−30, hoy)`, `[hoy−60, hoy−30)`, `[hoy−90, hoy−60)`; pesos por defecto 50/30/20; consumo/día = `(p1·t1 + p2·t2 + p3·t3) / 3000`; pedir N = `máx(0, ⌈máx(mínimo·3000, ponderado·N) − (stock+enCamino)·3000⌉ / 3000)` en enteros; N ∈ {15, 30}.
- **Qué es consumo** (spec §3.2): filas de `Reparacion_componente` bajo `ID_REP` `R%` o `G%`, `ES_REUTILIZADO = 0`, componente no `otro%`, fecha `r.FECHA_FIN`, sumado al master `COALESCE(c.ID_COM_MASTER, c.ID_COM)`.
- **Textos literales:**
  - columnas `Consumo/día`, `Pedir 15 d`, `Pedir 30 d`; CSV con las mismas cabeceras;
  - botón y título `Parámetros de previsión`; campos `Días 1-30 (%)`, `Días 31-60 (%)`, `Días 61-90 (%)`;
  - ayuda `Lo reciente pesa más. Los tres tienen que sumar 100.`; suma `Suma: N %`;
  - error de pesos (servidor y web) `Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.`;
  - acción del registro `EDITAR_PARAMETROS`, detalle `PREVISION: 50/30/20 → 40/35/25`;
  - login tras inactividad `Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo.`

## Estructura de ficheros

**Servidor (`gestion-reparaciones-servidor/`) — se crean:**

| Fichero | Responsabilidad |
|---|---|
| `src/main/java/com/reparaciones/servidor/util/PrevisionPedido.java` | La regla: reparto en tramos y cálculo de consumo/día y pedidos. Pura. |
| `src/main/java/com/reparaciones/servidor/dao/ParametroDAO.java` | Leer y guardar los pesos en `Parametro`, con 50/30/20 si faltan. |
| `src/main/java/com/reparaciones/servidor/service/PrevisionPedidoService.java` | Rellena la previsión de cada componente del listado. |
| `src/main/java/com/reparaciones/servidor/controller/ParametroController.java` | `GET/PUT /api/parametros/prevision`, solo ADMIN. |
| `src/main/java/com/reparaciones/servidor/model/PesosPrevision.java` | Cuerpo y respuesta `{peso1, peso2, peso3}`. |
| `sql/migracion-parametros-prevision.sql` | La tabla `Parametro` con los tres pesos. |

**Servidor — se modifican:** `model/Componente.java`, `dao/ComponenteDAO.java`, `controller/ComponenteController.java`, `sql/crear_bd.sql`, `docs/schema.md`, `docs/autorizacion_endpoints.md`, tests de `ComponenteController*` y `OpenApiContractTest`.

**Web (`gestion-reparaciones-web/src/`) — se crean:** `modules/almacen/stock/ParametrosPrevisionDialog.tsx`, `modules/almacen/stock/prevision.ts` (formato y validación de pesos) y sus tests.
**Web — se modifican:** `api/openapi.json` y `shared/api/schema.d.ts` (regenerados), `shared/api/client.ts`, fixtures de `Componente`, `modules/almacen/stock/{columnas.tsx,api.ts,MenuComponente.tsx,StockPage.tsx}`, `modules/almacen/ui/DialogoAlmacen.tsx`, `shared/session/{expiracion.ts,VigilanciaInactividad.tsx}`, `app/session/mensajes.ts`, `main.tsx`, `modules/gestion/cuenta/medidor.ts`, `package.json`, `CHANGELOG.md`.

**Raíz:** `docs/novedades/NOVEDADES-v0.9.5.md` y gitlinks.

---

## SERVIDOR

### Task 1: La regla, `PrevisionPedido`

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/PrevisionPedido.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/PrevisionPedidoTest.java`

**Interfaces:**
- Produces:
  - `PrevisionPedido.DIAS_TRAMO = 30`, `PrevisionPedido.DIAS_VENTANA = 90`
  - `record PrevisionPedido.Pesos(int p1, int p2, int p3)` con `POR_DEFECTO` (50/30/20), `boolean validos()`, `String texto()` → `"50/30/20"`
  - `record PrevisionPedido.Tramos(int t1, int t2, int t3)` con `CERO`
  - `record PrevisionPedido.ConsumoDia(int idMaster, LocalDate dia, int unidades)`
  - `record PrevisionPedido.Resultado(double consumoDiario, int pedir15, int pedir30)`
  - `static Map<Integer, Tramos> agrupar(List<ConsumoDia> consumos, LocalDate hoy)`
  - `static Resultado calcular(Tramos t, Pesos p, int minimo, int stock, int enCamino)`

- [ ] **Step 1: Rama**

```bash
cd gestion-reparaciones-servidor && git checkout main && git pull --ff-only && git checkout -b feature/prevision-pedidos
```

- [ ] **Step 2: Test que falla**

`src/test/java/com/reparaciones/servidor/util/PrevisionPedidoTest.java`:

```java
package com.reparaciones.servidor.util;

import com.reparaciones.servidor.util.PrevisionPedido.ConsumoDia;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import com.reparaciones.servidor.util.PrevisionPedido.Resultado;
import com.reparaciones.servidor.util.PrevisionPedido.Tramos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regla de la previsión de pedidos (spec 0.9.5 §3.1): los casos de la tabla de la spec, en el mismo orden. */
class PrevisionPedidoTest {

    private static final Pesos P = Pesos.POR_DEFECTO;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);

    @Test void bateriaQueSeMueve() {
        assertEquals(new Resultado(0.31, 2, 7), PrevisionPedido.calcular(new Tramos(12, 8, 5), P, 2, 3, 0));
    }

    @Test void bienSurtidaNoPide() {
        assertEquals(new Resultado(0.31, 0, 0), PrevisionPedido.calcular(new Tramos(12, 8, 5), P, 2, 10, 0));
    }

    @Test void casiParadaPideElMinimo() {
        assertEquals(new Resultado(0.02, 2, 2), PrevisionPedido.calcular(new Tramos(1, 0, 0), P, 2, 0, 0));
    }

    @Test void loQueEstaEnCaminoCuenta() {
        assertEquals(new Resultado(0.31, 0, 2), PrevisionPedido.calcular(new Tramos(12, 8, 5), P, 2, 3, 5));
    }

    @Test void piezaNuevaSinTratoEspecial() {
        assertEquals(new Resultado(0.10, 2, 3), PrevisionPedido.calcular(new Tramos(6, 0, 0), P, 2, 0, 0));
    }

    @Test void sinConsumoPideElMinimo() {
        assertEquals(new Resultado(0.0, 2, 2), PrevisionPedido.calcular(Tramos.CERO, P, 2, 0, 0));
    }

    @Test void conMinimoCeroYSinConsumoNoPide() {
        assertEquals(new Resultado(0.0, 0, 0), PrevisionPedido.calcular(Tramos.CERO, P, 0, 0, 0));
    }

    /** 6 unidades en el tramo reciente = 0,1 al día; 0,1 × 30 en coma flotante es 3,0000000000000004 y redondeando
     *  hacia arriba daría 4. En enteros da exactamente 3. */
    @Test void elRedondeoNoSeEquivocaPorDecimales() {
        assertEquals(3, PrevisionPedido.calcular(new Tramos(6, 0, 0), P, 0, 0, 0).pedir30());
    }

    @Test void otrosPesos() {
        // 40·12 + 35·8 + 25·5 = 885 → 885/3000 = 0,295 → 0,30; 15 d: 13275 − 9000 → 2; 30 d: 26550 − 9000 → 6
        assertEquals(new Resultado(0.30, 2, 6), PrevisionPedido.calcular(new Tramos(12, 8, 5), new Pesos(40, 35, 25), 2, 3, 0));
    }

    @Test void pesosValidosSoloSiSonDe0a100YSuman100() {
        assertTrue(P.validos());
        assertTrue(new Pesos(100, 0, 0).validos());
        assertFalse(new Pesos(50, 30, 30).validos());
        assertFalse(new Pesos(-10, 60, 50).validos());
        assertFalse(new Pesos(101, -1, 0).validos());
        assertEquals("50/30/20", P.texto());
    }

    @Test void agruparRepartePorTramosYExcluyeHoyYLoDeMasDe90Dias() {
        List<ConsumoDia> consumos = List.of(
                new ConsumoDia(1, HOY, 100),                  // hoy: fuera
                new ConsumoDia(1, HOY.minusDays(1), 1),       // tramo 1
                new ConsumoDia(1, HOY.minusDays(30), 2),      // tramo 1 (límite)
                new ConsumoDia(1, HOY.minusDays(31), 4),      // tramo 2
                new ConsumoDia(1, HOY.minusDays(60), 8),      // tramo 2 (límite)
                new ConsumoDia(1, HOY.minusDays(61), 16),     // tramo 3
                new ConsumoDia(1, HOY.minusDays(90), 32),     // tramo 3 (límite)
                new ConsumoDia(1, HOY.minusDays(91), 1000),   // fuera
                new ConsumoDia(7, HOY.minusDays(5), 3));      // otro master
        Map<Integer, Tramos> t = PrevisionPedido.agrupar(consumos, HOY);
        assertEquals(new Tramos(3, 12, 48), t.get(1));
        assertEquals(new Tramos(3, 0, 0), t.get(7));
        assertEquals(2, t.size());
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

```bash
mvn -q test -Dtest=PrevisionPedidoTest 2>&1 | tail -15
```

Esperado: error de compilación, `PrevisionPedido` no existe.

- [ ] **Step 4: Implementación**

`src/main/java/com/reparaciones/servidor/util/PrevisionPedido.java`:

```java
package com.reparaciones.servidor.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Previsión de pedidos de piezas (spec 0.9.5 §3.1). El consumo diario es una media ponderada de tres tramos de 30
 * días hacia atrás (lo reciente pesa más) y el pedido cubre 15 o 30 días con el stock mínimo como suelo:
 * {@code pedir = máx(0, ⌈máx(mínimo, consumo/día × días) − (stock + en camino)⌉)}.
 *
 * <p>Todo se calcula en enteros escalados por 3000 (100 de los porcentajes × 30 días del tramo): en coma flotante,
 * 0,1 × 30 da 3,0000000000000004 y el redondeo hacia arriba pediría una pieza de más.
 */
public final class PrevisionPedido {

    public static final int DIAS_TRAMO = 30;
    public static final int DIAS_VENTANA = 3 * DIAS_TRAMO;
    private static final long ESCALA = 100L * DIAS_TRAMO;

    /** Pesos en % de los tramos 1-30, 31-60 y 61-90 días. Válidos si son enteros de 0 a 100 que suman 100. */
    public record Pesos(int p1, int p2, int p3) {
        public static final Pesos POR_DEFECTO = new Pesos(50, 30, 20);

        public boolean validos() {
            return enRango(p1) && enRango(p2) && enRango(p3) && p1 + p2 + p3 == 100;
        }

        private static boolean enRango(int p) { return p >= 0 && p <= 100; }

        /** "50/30/20", para el registro de actividad. */
        public String texto() { return p1 + "/" + p2 + "/" + p3; }
    }

    /** Unidades consumidas en cada tramo: días 1-30, 31-60 y 61-90 antes de hoy. */
    public record Tramos(int t1, int t2, int t3) {
        public static final Tramos CERO = new Tramos(0, 0, 0);
    }

    /** Unidades de un master consumidas en un día (una fila de la consulta de consumo). */
    public record ConsumoDia(int idMaster, LocalDate dia, int unidades) {}

    public record Resultado(double consumoDiario, int pedir15, int pedir30) {}

    private PrevisionPedido() {}

    /** Reparte el consumo de cada master en sus tres tramos. Hoy no cuenta (día a medias) ni lo de hace más de 90 días. */
    public static Map<Integer, Tramos> agrupar(List<ConsumoDia> consumos, LocalDate hoy) {
        Map<Integer, int[]> acumulado = new HashMap<>();
        for (ConsumoDia c : consumos) {
            long hace = ChronoUnit.DAYS.between(c.dia(), hoy);
            if (hace < 1 || hace > DIAS_VENTANA) continue;
            int tramo = (int) ((hace - 1) / DIAS_TRAMO);
            acumulado.computeIfAbsent(c.idMaster(), k -> new int[3])[tramo] += c.unidades();
        }
        Map<Integer, Tramos> resultado = new HashMap<>();
        acumulado.forEach((id, t) -> resultado.put(id, new Tramos(t[0], t[1], t[2])));
        return resultado;
    }

    public static Resultado calcular(Tramos t, Pesos p, int minimo, int stock, int enCamino) {
        long ponderado = (long) p.p1() * t.t1() + (long) p.p2() * t.t2() + (long) p.p3() * t.t3();
        double consumoDiario = BigDecimal.valueOf(ponderado)
                .divide(BigDecimal.valueOf(ESCALA), 2, RoundingMode.HALF_UP).doubleValue();
        int disponible = stock + enCamino;
        return new Resultado(consumoDiario, pedir(ponderado, 15, minimo, disponible), pedir(ponderado, 30, minimo, disponible));
    }

    private static int pedir(long ponderado, int dias, int minimo, int disponible) {
        long objetivo = Math.max((long) minimo * ESCALA, ponderado * dias);
        long falta = objetivo - (long) disponible * ESCALA;
        if (falta <= 0) return 0;
        return (int) ((falta + ESCALA - 1) / ESCALA);
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

```bash
mvn -q test -Dtest=PrevisionPedidoTest 2>&1 | tail -15
```

Esperado: `Tests run: 11, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/util/PrevisionPedido.java src/test/java/com/reparaciones/servidor/util/PrevisionPedidoTest.java
git commit -m "feat: regla de la prevision de pedidos con tramos ponderados y aritmetica entera"
```

---

### Task 2: Datos de la previsión — consulta de consumo, tabla `Parametro` y `ParametroDAO`

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-parametros-prevision.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (tras el bloque de `Dificultad_puntos`)
- Modify: `gestion-reparaciones-servidor/docs/schema.md` (lista de tablas)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ParametroDAO.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ComponenteDAOConsumoTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ParametroDAOTest.java`

**Interfaces:**
- Consumes: `PrevisionPedido.ConsumoDia`, `PrevisionPedido.Pesos` (Task 1).
- Produces:
  - `List<PrevisionPedido.ConsumoDia> ComponenteDAO.getConsumoDiario(LocalDate desde, LocalDate hasta)` (intervalo `[desde, hasta)`)
  - `PrevisionPedido.Pesos ParametroDAO.getPesosPrevision()` (50/30/20 si falta la tabla, alguna clave o los pesos no son válidos)
  - `void ParametroDAO.guardarPesosPrevision(PrevisionPedido.Pesos p)`

- [ ] **Step 1: Migración y esquema de referencia**

`sql/migracion-parametros-prevision.sql`:

```sql
-- 0.9.5: parámetros editables por el administrador. De momento, los pesos de la previsión de pedidos de Stock
-- (días 1-30, 31-60 y 61-90, en %, que suman 100). Aditivo: el servidor 0.9.4 sigue funcionando sobre este esquema,
-- y el 0.9.5 sin esta tabla usa 50/30/20 (solo fallaría el diálogo que los guarda).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.5. Antes de aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

CREATE TABLE Parametro (
    CLAVE      VARCHAR(50) NOT NULL,
    VALOR      INT         NOT NULL,
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (CLAVE)
);

INSERT INTO Parametro (CLAVE, VALOR) VALUES
    ('PREVISION_PESO_1', 50),
    ('PREVISION_PESO_2', 30),
    ('PREVISION_PESO_3', 20);
```

En `sql/crear_bd.sql`, justo después del `INSERT INTO Dificultad_puntos …;`, el mismo `CREATE TABLE Parametro` y su `INSERT` (sin el `USE` ni los comentarios de cabecera). En `docs/schema.md`, añadir `` - `Parametro` `` después de `` - `Dificultad_puntos` ``.

- [ ] **Step 2: Tests que fallan**

`src/test/java/com/reparaciones/servidor/dao/ComponenteDAOConsumoTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.util.PrevisionPedido.ConsumoDia;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Consumo por master y día para la previsión de pedidos (spec 0.9.5 §3.2). Que las solicitudes nunca caen bajo
 *  R…/G… se comprueba con datos reales en preprod; aquí, que el SQL lleva cada filtro y que las filas se leen. */
class ComponenteDAOConsumoTest {

    @SuppressWarnings("unchecked")
    @Test void consultaSoloReparacionesResultantesSinReutilizadasNiOtrosYSumaEnElMaster() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_MASTER")).thenReturn(3);
        when(rs.getDate("DIA")).thenReturn(Date.valueOf(LocalDate.of(2026, 10, 1)));
        when(rs.getInt("UNIDADES")).thenReturn(4);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sql.capture(), any(RowMapper.class), any(), any()))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        LocalDate desde = LocalDate.of(2026, 7, 9);
        LocalDate hasta = LocalDate.of(2026, 10, 7);
        List<ConsumoDia> filas = new ComponenteDAO(jdbc).getConsumoDiario(desde, hasta);

        assertEquals(List.of(new ConsumoDia(3, LocalDate.of(2026, 10, 1), 4)), filas);
        String q = sql.getValue();
        assertTrue(q.contains("r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%'"), q);
        assertTrue(q.contains("rc.ES_REUTILIZADO = 0"), q);
        assertTrue(q.contains("c.TIPO NOT LIKE 'otro%'"), q);
        assertTrue(q.contains("COALESCE(c.ID_COM_MASTER, c.ID_COM) AS ID_MASTER"), q);
        assertTrue(q.contains("r.FECHA_FIN >= ? AND r.FECHA_FIN < ?"), q);
        verify(jdbc).query(anyString(), any(RowMapper.class),
                eq(Timestamp.valueOf(desde.atStartOfDay())), eq(Timestamp.valueOf(hasta.atStartOfDay())));
    }

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
```

`src/test/java/com/reparaciones/servidor/dao/ParametroDAOTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/** Pesos de la previsión en la tabla Parametro (spec 0.9.5 §4.1). */
class ParametroDAOTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ParametroDAO dao = new ParametroDAO(jdbc);

    private static Map<String, Object> fila(String clave, int valor) { return Map.of("CLAVE", clave, "VALOR", valor); }

    @Test void leeLosTresPesos() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenReturn(List.of(
                fila("PREVISION_PESO_1", 40), fila("PREVISION_PESO_2", 35), fila("PREVISION_PESO_3", 25)));
        assertEquals(new Pesos(40, 35, 25), dao.getPesosPrevision());
    }

    @Test void siFaltaUnaClaveUsaLosDeSerie() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenReturn(List.of(
                fila("PREVISION_PESO_1", 40), fila("PREVISION_PESO_2", 35)));
        assertEquals(Pesos.POR_DEFECTO, dao.getPesosPrevision());
    }

    @Test void siNoSumanCienUsaLosDeSerie() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenReturn(List.of(
                fila("PREVISION_PESO_1", 50), fila("PREVISION_PESO_2", 50), fila("PREVISION_PESO_3", 50)));
        assertEquals(Pesos.POR_DEFECTO, dao.getPesosPrevision());
    }

    @Test void sinTablaUsaLosDeSerie() {
        when(jdbc.queryForList(contains("FROM Parametro"))).thenThrow(new DataAccessResourceFailureException("no existe"));
        assertEquals(Pesos.POR_DEFECTO, dao.getPesosPrevision());
    }

    @Test void guardaLosTresConUpsert() {
        dao.guardarPesosPrevision(new Pesos(40, 35, 25));
        String sql = "INSERT INTO Parametro (CLAVE, VALOR) VALUES (?, ?) ON DUPLICATE KEY UPDATE VALOR = VALUES(VALOR)";
        verify(jdbc).update(sql, "PREVISION_PESO_1", 40);
        verify(jdbc).update(sql, "PREVISION_PESO_2", 35);
        verify(jdbc).update(sql, "PREVISION_PESO_3", 25);
    }
}
```

- [ ] **Step 3: Ejecutar y ver que fallan**

```bash
mvn -q test -Dtest='ComponenteDAOConsumoTest,ParametroDAOTest' 2>&1 | tail -15
```

Esperado: error de compilación (`getConsumoDiario` y `ParametroDAO` no existen).

- [ ] **Step 4: Implementación**

En `ComponenteDAO.java`, añadir el import `import com.reparaciones.servidor.util.PrevisionPedido;` y este método después de `getAllGestionados()`:

```java
    /** Unidades consumidas por master y día en [desde, hasta) (spec 0.9.5 §3.2): solo las piezas de las reparaciones
     *  resultantes (R…, G…), que son las que restan stock; las solicitudes cuelgan de la asignación (A…, AG…) y una
     *  solicitud ya servida tiene las mismas marcas que una fila de consumo. Sin reutilizadas ni tipos "otro". */
    public List<PrevisionPedido.ConsumoDia> getConsumoDiario(LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT COALESCE(c.ID_COM_MASTER, c.ID_COM) AS ID_MASTER, DATE(r.FECHA_FIN) AS DIA,
                       SUM(rc.CANTIDAD) AS UNIDADES
                FROM Reparacion_componente rc
                JOIN Reparacion r ON rc.ID_REP = r.ID_REP
                JOIN Componente c ON rc.ID_COM = c.ID_COM
                WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%')
                  AND rc.ES_REUTILIZADO = 0
                  AND c.TIPO NOT LIKE 'otro%'
                  AND r.FECHA_FIN >= ? AND r.FECHA_FIN < ?
                GROUP BY ID_MASTER, DIA
                """;
        return jdbc.query(sql, (rs, row) -> new PrevisionPedido.ConsumoDia(
                        rs.getInt("ID_MASTER"), rs.getDate("DIA").toLocalDate(), rs.getInt("UNIDADES")),
                Timestamp.valueOf(desde.atStartOfDay()), Timestamp.valueOf(hasta.atStartOfDay()));
    }
```

`src/main/java/com/reparaciones/servidor/dao/ParametroDAO.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parámetros que edita el administrador (tabla Parametro, 0.9.5). Hoy, los pesos de la previsión de pedidos. */
@Repository
public class ParametroDAO {

    private static final Logger log = LoggerFactory.getLogger(ParametroDAO.class);

    static final String PESO_1 = "PREVISION_PESO_1";
    static final String PESO_2 = "PREVISION_PESO_2";
    static final String PESO_3 = "PREVISION_PESO_3";

    private final JdbcTemplate jdbc;

    public ParametroDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los pesos guardados, o 50/30/20 si no hay tabla (servidor desplegado antes que la migración), falta alguna
     *  clave o no son válidos: la previsión de Stock nunca rompe el listado. */
    public Pesos getPesosPrevision() {
        Map<String, Integer> valores = new HashMap<>();
        try {
            for (Map<String, Object> fila : jdbc.queryForList(
                    "SELECT CLAVE, VALOR FROM Parametro WHERE CLAVE LIKE 'PREVISION_PESO_%'")) {
                valores.put((String) fila.get("CLAVE"), ((Number) fila.get("VALOR")).intValue());
            }
        } catch (DataAccessException e) {
            log.warn("Sin tabla Parametro ({}): la previsión usa los pesos por defecto", e.getMessage());
            return Pesos.POR_DEFECTO;
        }
        if (!valores.keySet().containsAll(List.of(PESO_1, PESO_2, PESO_3))) {
            log.warn("Faltan pesos de la previsión en Parametro: se usan los pesos por defecto");
            return Pesos.POR_DEFECTO;
        }
        Pesos pesos = new Pesos(valores.get(PESO_1), valores.get(PESO_2), valores.get(PESO_3));
        if (!pesos.validos()) {
            log.warn("Pesos de la previsión no válidos en Parametro ({}): se usan los pesos por defecto", pesos.texto());
            return Pesos.POR_DEFECTO;
        }
        return pesos;
    }

    public void guardarPesosPrevision(Pesos p) {
        String sql = "INSERT INTO Parametro (CLAVE, VALOR) VALUES (?, ?) ON DUPLICATE KEY UPDATE VALOR = VALUES(VALOR)";
        jdbc.update(sql, PESO_1, p.p1());
        jdbc.update(sql, PESO_2, p.p2());
        jdbc.update(sql, PESO_3, p.p3());
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasan**

```bash
mvn -q test -Dtest='ComponenteDAOConsumoTest,ParametroDAOTest' 2>&1 | tail -15
```

Esperado: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add sql/migracion-parametros-prevision.sql sql/crear_bd.sql docs/schema.md \
  src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java src/main/java/com/reparaciones/servidor/dao/ParametroDAO.java \
  src/test/java/com/reparaciones/servidor/dao/ComponenteDAOConsumoTest.java src/test/java/com/reparaciones/servidor/dao/ParametroDAOTest.java
git commit -m "feat: consumo por master y dia y tabla de parametros con los pesos de la prevision"
```

---

### Task 3: La previsión en el listado de Stock, según el rol

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/Componente.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/PrevisionPedidoService.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ComponenteController.java` (constructor y `getAllGestionados`)
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java` y `ComponenteControllerEliminarTest.java` (constructor)
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/service/PrevisionPedidoServiceTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ComponenteControllerPrevisionTest.java`

**Interfaces:**
- Consumes: Task 1 y `ComponenteDAO.getConsumoDiario`, `ParametroDAO.getPesosPrevision` (Task 2).
- Produces:
  - `Componente`: `Double getConsumoDiario()`, `Integer getPedir15()`, `Integer getPedir30()`, `void setPrevision(double consumoDiario, int pedir15, int pedir30)`; en el contrato, tres propiedades nulables `consumoDiario`, `pedir15`, `pedir30`.
  - `PrevisionPedidoService.rellenar(List<Componente> componentes, LocalDate hoy)`.
  - `new ComponenteController(ComponenteDAO dao, LogDAO logDao, PrevisionPedidoService prevision)`.

- [ ] **Step 1: Tests que fallan**

`src/test/java/com/reparaciones/servidor/service/PrevisionPedidoServiceTest.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.util.PrevisionPedido.ConsumoDia;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrevisionPedidoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 10, 0);

    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ParametroDAO parametroDao = mock(ParametroDAO.class);
    private final PrevisionPedidoService servicio = new PrevisionPedidoService(componenteDao, parametroDao);

    private static Componente comp(int id, Integer master, int stock, int minimo, boolean activo, int enCamino) {
        Componente c = new Componente(id, "bat-" + id, T, stock, minimo, activo, T);
        c.setIdComMaster(master);
        c.setEnCamino(enCamino);
        return c;
    }

    @Test void rellenaLosActivosConElConsumoDeSuMasterYDejaNulosLosDesactivados() {
        when(parametroDao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        // master 1: 12 / 8 / 5 en los tres tramos
        when(componenteDao.getConsumoDiario(HOY.minusDays(90), HOY)).thenReturn(List.of(
                new ConsumoDia(1, HOY.minusDays(3), 12),
                new ConsumoDia(1, HOY.minusDays(40), 8),
                new ConsumoDia(1, HOY.minusDays(70), 5)));
        Componente master = comp(1, null, 3, 2, true, 0);
        Componente slave = comp(5, 1, 3, 2, true, 0);         // el listado ya trae stock y mínimo del master
        Componente sinConsumo = comp(2, null, 0, 2, true, 0);
        Componente desactivado = comp(4, null, 0, 2, false, 0);

        servicio.rellenar(List.of(master, slave, sinConsumo, desactivado), HOY);

        assertEquals(0.31, master.getConsumoDiario());
        assertEquals(2, master.getPedir15());
        assertEquals(7, master.getPedir30());
        assertEquals(0.31, slave.getConsumoDiario());
        assertEquals(7, slave.getPedir30());
        assertEquals(0.0, sinConsumo.getConsumoDiario());
        assertEquals(2, sinConsumo.getPedir15());
        assertNull(desactivado.getConsumoDiario());
        assertNull(desactivado.getPedir15());
        assertNull(desactivado.getPedir30());
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/ComponenteControllerPrevisionTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.PrevisionPedidoService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** La previsión de Stock solo se calcula para SUPERTECNICO y ADMIN (spec 0.9.5 §3.3). */
class ComponenteControllerPrevisionTest {

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final PrevisionPedidoService prevision = mock(PrevisionPedidoService.class);
    private final ComponenteController ctl = new ComponenteController(dao, mock(LogDAO.class), prevision);

    @Test void alTecnicoNoSeLeCalcula() {
        when(dao.getAllGestionados()).thenReturn(List.of(new Componente()));
        ctl.getAllGestionados(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        verify(prevision, never()).rellenar(anyList(), any(LocalDate.class));
    }

    @Test void alSupertecnicoYAlAdminSi() {
        List<Componente> lista = List.of(new Componente());
        when(dao.getAllGestionados()).thenReturn(lista);
        ctl.getAllGestionados(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3));
        ctl.getAllGestionados(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        verify(prevision, times(2)).rellenar(eq(lista), any(LocalDate.class));
    }

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
```

En `ComponenteControllerValidacionTest.java` y `ComponenteControllerEliminarTest.java`, cambiar la línea del controlador por:

```java
    private final ComponenteController ctl = new ComponenteController(dao, logDao, mock(com.reparaciones.servidor.service.PrevisionPedidoService.class));
```

En `OpenApiContractTest.java`, junto a `assertNullable(esquemas, "Componente", "ultimoPedido", "idComMaster");`:

```java
        assertNullable(esquemas, "Componente", "consumoDiario", "pedir15", "pedir30");
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
mvn -q test -Dtest='PrevisionPedidoServiceTest,ComponenteControllerPrevisionTest' 2>&1 | tail -15
```

Esperado: error de compilación (`PrevisionPedidoService`, `setPrevision` y el constructor de tres argumentos no existen).

- [ ] **Step 3: Implementación**

En `model/Componente.java`, tras `idComMaster`:

```java
    // Previsión de pedidos (spec 0.9.5 §3): solo para SUPERTECNICO y ADMIN y solo en componentes activos.
    @Schema(nullable = true) private Double  consumoDiario;
    @Schema(nullable = true) private Integer pedir15;
    @Schema(nullable = true) private Integer pedir30;
```

y al final de la clase:

```java
    public Double  getConsumoDiario() { return consumoDiario; }
    public Integer getPedir15()       { return pedir15; }
    public Integer getPedir30()       { return pedir30; }

    public void setPrevision(double consumoDiario, int pedir15, int pedir30) {
        this.consumoDiario = consumoDiario;
        this.pedir15       = pedir15;
        this.pedir30       = pedir30;
    }
```

`src/main/java/com/reparaciones/servidor/service/PrevisionPedidoService.java`:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.util.PrevisionPedido;
import com.reparaciones.servidor.util.PrevisionPedido.Resultado;
import com.reparaciones.servidor.util.PrevisionPedido.Tramos;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Rellena la previsión de pedidos del listado de Stock (spec 0.9.5 §3.3). Stock, mínimo y en camino ya vienen
 *  resueltos al master del grupo compartido; el consumo se busca también por master. */
@Service
public class PrevisionPedidoService {

    private final ComponenteDAO componenteDao;
    private final ParametroDAO parametroDao;

    public PrevisionPedidoService(ComponenteDAO componenteDao, ParametroDAO parametroDao) {
        this.componenteDao = componenteDao;
        this.parametroDao = parametroDao;
    }

    public void rellenar(List<Componente> componentes, LocalDate hoy) {
        PrevisionPedido.Pesos pesos = parametroDao.getPesosPrevision();
        Map<Integer, Tramos> porMaster = PrevisionPedido.agrupar(
                componenteDao.getConsumoDiario(hoy.minusDays(PrevisionPedido.DIAS_VENTANA), hoy), hoy);
        for (Componente c : componentes) {
            if (!c.isActivo()) continue;
            int master = c.getIdComMaster() != null ? c.getIdComMaster() : c.getIdCom();
            Resultado r = PrevisionPedido.calcular(porMaster.getOrDefault(master, Tramos.CERO), pesos,
                    c.getStockMinimo(), c.getStock(), c.getEnCamino());
            c.setPrevision(r.consumoDiario(), r.pedir15(), r.pedir30());
        }
    }
}
```

En `ComponenteController.java`: import `com.reparaciones.servidor.service.PrevisionPedidoService` y `java.time.ZoneId`; campo y constructor:

```java
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ComponenteDAO          dao;
    private final LogDAO                 logDao;
    private final PrevisionPedidoService prevision;

    public ComponenteController(ComponenteDAO dao, LogDAO logDao, PrevisionPedidoService prevision) {
        this.dao       = dao;
        this.logDao    = logDao;
        this.prevision = prevision;
    }
```

y `getAllGestionados`:

```java
    /** Listado de Stock. La previsión de pedidos (consumo/día, pedir 15 y 30 días) es información de compras: solo
     *  se calcula para SUPERTECNICO y ADMIN; a un TECNICO le llegan los tres campos nulos (spec 0.9.5 §3.3). */
    @GetMapping("/gestionados")
    public List<Componente> getAllGestionados(@AuthenticationPrincipal UsuarioPrincipal principal) {
        List<Componente> lista = dao.getAllGestionados();
        if (principal != null && ("SUPERTECNICO".equals(principal.getRol()) || "ADMIN".equals(principal.getRol())))
            prevision.rellenar(lista, LocalDate.now(MADRID));
        return lista;
    }
```

- [ ] **Step 4: Ejecutar y ver que pasan, con la suite completa**

```bash
mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos (incluidos `OpenApiContractTest` y los tests de roles que montan el contexto: `PrevisionPedidoService` arranca con su `ParametroDAO` real, que sin base cae a los pesos por defecto).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/Componente.java src/main/java/com/reparaciones/servidor/service/PrevisionPedidoService.java \
  src/main/java/com/reparaciones/servidor/controller/ComponenteController.java src/test/java/com/reparaciones/servidor
git commit -m "feat: el listado de stock trae la prevision de pedidos para supertecnico y admin"
```

---

### Task 4: Mínimo y parámetros solo para el administrador

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/PesosPrevision.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ParametroController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ComponenteController.java` (`actualizar`, `setStockMinimo`; `insertar` no se toca: `POST /api/componentes` es ruta retirada, 403 para todos)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java` (`actualizar`, `setStockMinimo`)
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ComponenteControllerValidacionTest.java`
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Modify: `gestion-reparaciones-servidor/docs/autorizacion_endpoints.md`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ParametroControllerTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/RolesParametrosPrevisionTest.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ComponenteDAOMinimoTest.java`

**Interfaces:**
- Consumes: `ParametroDAO` (Task 2), `PrevisionPedido.Pesos` (Task 1), constructor de `ComponenteController` (Task 3).
- Produces:
  - `record PesosPrevision(int peso1, int peso2, int peso3)` (esquema `PesosPrevision` del contrato).
  - `GET /api/parametros/prevision` → `PesosPrevision`; `PUT /api/parametros/prevision` con cuerpo `PesosPrevision` → 200, o 422 con `ParametroController.MSG_PESOS`.
  - `ComponenteDAO.actualizar(int idCom, String tipo, int stock, LocalDateTime updatedAt)` (sin mínimo).
  - `ComponenteDAO.setStockMinimo(int idCom, int stockMinimo)` guarda en el master.

- [ ] **Step 1: Tests que fallan**

`src/test/java/com/reparaciones/servidor/controller/ParametroControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.PesosPrevision;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ParametroControllerTest {

    private final ParametroDAO dao = mock(ParametroDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ParametroController ctl = new ParametroController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "", "ADMIN", null);

    @Test void devuelveLosPesosGuardados() {
        when(dao.getPesosPrevision()).thenReturn(new Pesos(40, 35, 25));
        assertEquals(new PesosPrevision(40, 35, 25), ctl.getPrevision());
    }

    @Test void guardaYAnotaElCambio() {
        when(dao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        ctl.guardarPrevision(new PesosPrevision(40, 35, 25), admin);
        verify(dao).guardarPesosPrevision(new Pesos(40, 35, 25));
        verify(logDao).insertar(1, "EDITAR_PARAMETROS", "PREVISION: 50/30/20 → 40/35/25");
    }

    @Test void sinCambiosNoAnota() {
        when(dao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        ctl.guardarPrevision(new PesosPrevision(50, 30, 20), admin);
        verify(logDao, never()).insertar(anyInt(), anyString(), anyString());
    }

    @Test void siNoSumanCienEs422YNoEscribe() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarPrevision(new PesosPrevision(50, 30, 30), admin));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.", e.getReason());
        verify(dao, never()).guardarPesosPrevision(any());
    }

    @Test void unNegativoEs422() {
        assertThrows(ResponseStatusException.class, () -> ctl.guardarPrevision(new PesosPrevision(-10, 60, 50), admin));
        verify(dao, never()).guardarPesosPrevision(any());
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/RolesParametrosPrevisionTest.java` (misma cabecera de anotaciones y propiedades que `RolesSolicitudesStockTest`):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Mínimo y pesos de la previsión: solo ADMIN (spec 0.9.5 §4.2), con la cadena de seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesParametrosPrevisionTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ParametroDAO parametroDao;
    @MockBean ComponenteDAO componenteDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private int status(MockHttpServletRequestBuilder peticion, String autorizacion) throws Exception {
        return mvc.perform(peticion.header("Authorization", autorizacion)).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test void parametrosSoloAdmin() throws Exception {
        when(parametroDao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        String cuerpo = "{\"peso1\":40,\"peso2\":35,\"peso3\":25}";
        for (String quien : new String[] { tecnico(), supertecnico() }) {
            assertEquals(403, status(get("/api/parametros/prevision"), quien));
            assertEquals(403, status(json(put("/api/parametros/prevision"), cuerpo), quien));
        }
        verify(parametroDao, never()).guardarPesosPrevision(any());
        assertEquals(200, status(get("/api/parametros/prevision"), admin()));
        assertEquals(200, status(json(put("/api/parametros/prevision"), cuerpo), admin()));
        verify(parametroDao).guardarPesosPrevision(new Pesos(40, 35, 25));
    }

    @Test void stockMinimoSoloAdmin() throws Exception {
        String cuerpo = "{\"stockMinimo\":4}";
        assertEquals(403, status(json(patch("/api/componentes/5/stock-minimo"), cuerpo), supertecnico()));
        assertEquals(403, status(json(patch("/api/componentes/5/stock-minimo"), cuerpo), tecnico()));
        verify(componenteDao, never()).setStockMinimo(anyInt(), anyInt());
        assertEquals(200, status(json(patch("/api/componentes/5/stock-minimo"), cuerpo), admin()));
        verify(componenteDao).setStockMinimo(5, 4);
    }
}
```

`src/test/java/com/reparaciones/servidor/dao/ComponenteDAOMinimoTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El mínimo es el suelo de la previsión y se guarda en el master del grupo compartido (spec 0.9.5 §4.2). */
class ComponenteDAOMinimoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComponenteDAO dao = new ComponenteDAO(jdbc);

    @Test void elMinimoDeUnSlaveSeGuardaEnSuMaster() {
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        dao.setStockMinimo(12, 4);
        verify(jdbc).update("UPDATE Componente SET STOCK_MINIMO = ? WHERE ID_COM = ?", 4, 3);
    }

    @Test void editarStockYaNoTocaElMinimo() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(5))).thenReturn(5);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        dao.actualizar(5, "lcd-x", 7, t);
        // El UPDATE ya no lleva STOCK_MINIMO: se comprueba con el SQL exacto.
        verify(jdbc).update(eq("UPDATE Componente SET TIPO = ?, STOCK = ? WHERE ID_COM = ? AND UPDATED_AT = ?"),
                eq("lcd-x"), eq(7), eq(5), any());
    }
}
```

En `ComponenteControllerValidacionTest.java`, sustituir `editarConMinimoNegativoEs422` y `editarConCeroVale`, y los dos `verify(dao, never()).actualizar(anyInt(), anyString(), anyInt(), anyInt(), any());` de los otros tests por `verify(dao, never()).actualizar(anyInt(), anyString(), anyInt(), any());`:

```java
    @Test void editarIgnoraElMinimo() {
        when(dao.getStockById(5)).thenReturn(4);
        ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", 3, -2, ahora), super7);
        verify(dao).actualizar(5, "lcd-x", 3, ahora);
    }

    @Test void editarConCeroVale() {
        when(dao.getStockById(5)).thenReturn(4);
        ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", 0, 0, ahora), super7);
        verify(dao).actualizar(5, "lcd-x", 0, ahora);
    }
```

En `OpenApiContractTest.java`, junto al resto de comprobaciones de rutas y nulabilidad:

```java
        assertTrue(paths.has("/api/parametros/prevision"), "falta /api/parametros/prevision en el contrato");
        assertNoNullable(esquemas, "PesosPrevision", "peso1", "peso2", "peso3");
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
mvn -q test -Dtest='ParametroControllerTest,RolesParametrosPrevisionTest,ComponenteDAOMinimoTest,ComponenteControllerValidacionTest' 2>&1 | tail -20
```

Esperado: error de compilación (`ParametroController`, `PesosPrevision` y el `actualizar` de cuatro argumentos no existen).

- [ ] **Step 3: Implementación**

`src/main/java/com/reparaciones/servidor/model/PesosPrevision.java`:

```java
package com.reparaciones.servidor.model;

/** Pesos en % de la previsión de pedidos: días 1-30, 31-60 y 61-90 (spec 0.9.5 §4.2). */
public record PesosPrevision(int peso1, int peso2, int peso3) {}
```

`src/main/java/com/reparaciones/servidor/controller/ParametroController.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.PesosPrevision;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Parámetros que edita el administrador (spec 0.9.5 §4.2): los pesos de la previsión de pedidos de Stock. */
@RestController
@RequestMapping("/api/parametros")
public class ParametroController {

    static final String MSG_PESOS = "Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.";

    private final ParametroDAO dao;
    private final LogDAO logDao;

    public ParametroController(ParametroDAO dao, LogDAO logDao) {
        this.dao = dao;
        this.logDao = logDao;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/prevision")
    public PesosPrevision getPrevision() {
        Pesos p = dao.getPesosPrevision();
        return new PesosPrevision(p.p1(), p.p2(), p.p3());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @PutMapping("/prevision")
    public void guardarPrevision(@RequestBody PesosPrevision req, @AuthenticationPrincipal UsuarioPrincipal principal) {
        Pesos nuevos = new Pesos(req.peso1(), req.peso2(), req.peso3());
        if (!nuevos.validos()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_PESOS);
        Pesos antes = dao.getPesosPrevision();
        dao.guardarPesosPrevision(nuevos);
        if (!antes.equals(nuevos))
            logDao.insertar(principal.getIdUsu(), "EDITAR_PARAMETROS", "PREVISION: " + antes.texto() + " → " + nuevos.texto());
    }
}
```

En `ComponenteController.java`:

- `actualizar`: quitar `noNegativo(req.stockMinimo(), MSG_MINIMO);` y llamar `dao.actualizar(idCom, req.tipo(), req.stock(), req.updatedAt());`. Javadoc encima: `/** "Editar stock" (SUPERTECNICO) no cambia el mínimo: el campo stockMinimo del cuerpo se acepta, para no romper clientes, y se ignora. El mínimo solo lo cambia el ADMIN con PATCH /stock-minimo (spec 0.9.5 §4.2). */`
- `setStockMinimo`: `@PreAuthorize("hasRole('ADMIN')")` en lugar de `hasRole('SUPERTECNICO')`.

En `ComponenteDAO.java`, sustituir `actualizar` y `setStockMinimo`:

```java
    /** Editar stock. El mínimo no se toca aquí: solo lo cambia el ADMIN con setStockMinimo (spec 0.9.5 §4.2). */
    public void actualizar(int idCom, String tipo, int stock, LocalDateTime updatedAt) {
        int masterIdCom = resolveToMasterId(idCom);
        if (masterIdCom != idCom) {
            // Slave: STOCK en el master; TIPO en el propio slave
            jdbc.update("UPDATE Componente SET STOCK = ? WHERE ID_COM = ?", stock, masterIdCom);
            jdbc.update("UPDATE Componente SET TIPO = ? WHERE ID_COM = ?", tipo, idCom);
        } else {
            int filas = jdbc.update(
                    "UPDATE Componente SET TIPO = ?, STOCK = ? WHERE ID_COM = ? AND UPDATED_AT = ?",
                    tipo, stock, idCom,
                    Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
            if (filas == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
            }
        }
    }

    /** El mínimo vive en el master del grupo compartido: es el que enseña Stock y el que usa la previsión. */
    public void setStockMinimo(int idCom, int stockMinimo) {
        jdbc.update("UPDATE Componente SET STOCK_MINIMO = ? WHERE ID_COM = ?", stockMinimo, resolveToMasterId(idCom));
    }
```

En `docs/autorizacion_endpoints.md`, sección "Criterio general":
- en **Lecturas**, añadir al final de la frase: `La previsión de pedidos de `GET /api/componentes/gestionados` (consumo/día y cuánto pedir) solo se rellena para SUPERTECNICO y ADMIN; a un TECNICO le llega nula.`
- en **Administración**: `usuarios, logs, valores de dificultad, stock mínimo de los componentes y parámetros de la previsión de pedidos: **ADMIN**.`

Y en la tabla "Dónde están los tests", una fila: `| Mínimo y parámetros solo ADMIN | `controller/RolesParametrosPrevisionTest`, `controller/ParametroControllerTest` |`.

- [ ] **Step 4: Ejecutar y ver que pasan, con la suite completa**

```bash
mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java docs/autorizacion_endpoints.md
git commit -m "feat: el stock minimo y los pesos de la prevision solo los cambia el admin"
```

---

### Task 5: Cierre del servidor

**Files:** ninguno nuevo (comprobación y contrato).

- [ ] **Step 1: Suite completa y contrato**

```bash
mvn -q test 2>&1 | tail -20
ls -la target/openapi.json
node -e "const d=require('./target/openapi.json');console.log(!!d.paths['/api/parametros/prevision'], Object.keys(d.components.schemas.Componente.properties), d.components.schemas.PesosPrevision)"
```

Esperado: 0 fallos; `true`; `Componente` con `consumoDiario`, `pedir15`, `pedir30`; `PesosPrevision` con `peso1..3`.

- [ ] **Step 2: Revisión de la rama**

`superpowers:requesting-code-review` de `feature/prevision-pedidos` del servidor, con foco en: que ninguna solicitud cuenta como consumo (filtro `R%`/`G%`), que el redondeo es entero y exacto, que a un TECNICO no le llega la previsión, que el mínimo ya solo lo cambia el ADMIN por cualquier ruta viva (`PATCH`; el `PUT` lo ignora y el `POST` está retirado) y que el servidor sin la tabla `Parametro` sigue sirviendo Stock.

El merge, el push y el tag van en las Tasks 12 y 13, cada uno con su OK.

---

## WEB

### Task 6: Tipos del contrato y fixtures

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (regenerados)
- Modify: `gestion-reparaciones-web/src/shared/api/client.ts`
- Modify: fixtures de `Componente` que señale el `typecheck`

**Interfaces:**
- Consumes: `target/openapi.json` del servidor (Task 5).
- Produces: `Componente` con `consumoDiario: number | null`, `pedir15: number | null`, `pedir30: number | null`; tipo `PesosPrevision` exportado desde `@/shared/api/client`.

- [ ] **Step 1: Rama y tipos**

```bash
cd gestion-reparaciones-web && git checkout main && git pull --ff-only && git checkout -b feature/prevision-pedidos
node -e "const fs=require('fs');const j=JSON.parse(fs.readFileSync('../gestion-reparaciones-servidor/target/openapi.json','utf8'));fs.writeFileSync('api/openapi.json',JSON.stringify(j,null,2)+'\n')"
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
grep -n "consumoDiario\|pedir15\|PesosPrevision\|parametros/prevision" src/shared/api/schema.d.ts | head
```

Esperado: cambian solo esos dos ficheros y aparecen los tres campos, el esquema y la ruta.

En `src/shared/api/client.ts`, junto a `export type Componente = …`:

```ts
export type PesosPrevision = components['schemas']['PesosPrevision']
```

- [ ] **Step 2: Fixtures**

```bash
npm run typecheck 2>&1 | grep -E "error TS" | head -30
```

En cada fichero que señale (objetos tipados `Componente` sin los campos nuevos: `modules/almacen/stock/{columnas,dialogos,api,filtros,graficos}.test.*`, `modules/taller/test/fabrica.ts`, `modules/almacen/pedidos/formulario/datosPrueba.ts`…), añadir al objeto base `consumoDiario: null, pedir15: null, pedir30: null`. En `modules/almacen/stock/StockPage.test.tsx` (sin tipo, pero lo usa la tabla), lo mismo en `base`, y en la fila de `bat-x` (id 2) `consumoDiario: 0.31, pedir15: 2, pedir30: 7`.

- [ ] **Step 3: Comprobación**

```bash
npm run typecheck 2>&1 | tail -5 && npm run test 2>&1 | tail -8
```

Esperado: sin errores de tipos y la suite en verde (todavía no se pinta nada nuevo).

- [ ] **Step 4: Commit**

```bash
git add api/openapi.json src/shared/api src/modules
git commit -m "chore: tipos del contrato con la prevision de pedidos y los pesos"
```

---

### Task 7: Columnas y CSV de la previsión

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/stock/prevision.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/columnas.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.tsx` (columnas y CSV)
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/prevision.test.ts`
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/columnas.test.tsx`, `StockPage.test.tsx`

**Interfaces:**
- Consumes: campos de la Task 6.
- Produces:
  - `formatearConsumo(v: number | null | undefined): string` → `'0,31'` o `'—'`
  - `formatearPedir(v: number | null | undefined): string` → `'7'` o `'—'`
  - `crearColumnasStock({ onEnCamino, conPrevision })` (`conPrevision?: boolean`, por defecto `false`)
  - `CABECERAS_CSV_PREVISION`, `cabecerasCsvStock(conPrevision: boolean): string[]`, `filaCsvStock(c, conPrevision = false): string[]`

- [ ] **Step 1: Tests que fallan**

`src/modules/almacen/stock/prevision.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { formatearConsumo, formatearPedir } from './prevision'

describe('formato de la previsión', () => {
  it('consumo con dos decimales y coma; sin previsión, "—"', () => {
    expect(formatearConsumo(0.31)).toBe('0,31')
    expect(formatearConsumo(0)).toBe('0,00')
    expect(formatearConsumo(1.5)).toBe('1,50')
    expect(formatearConsumo(null)).toBe('—')
    expect(formatearConsumo(undefined)).toBe('—')
  })
  it('pedir: el número; sin previsión, "—"', () => {
    expect(formatearPedir(7)).toBe('7')
    expect(formatearPedir(0)).toBe('0')
    expect(formatearPedir(null)).toBe('—')
  })
})
```

En `columnas.test.tsx`, añadir:

```tsx
  it('con previsión: tres columnas entre "Stock Mínimo" y "Último pedido"; el 0 en gris y "—" en desactivadas', () => {
    render(<DataTable columns={crearColumnasStock({ onEnCamino: vi.fn(), conPrevision: true })} data={[
      c({ consumoDiario: 0.31, pedir15: 0, pedir30: 7 }),
      c({ idCom: 2, tipo: 'bat-x', activo: false }),
    ]} vacio="Sin componentes" getRowId={(x) => String(x.idCom)} />)
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(
      ['Componente', 'En Stock', 'En Camino', 'Stock Mínimo', 'Consumo/día', 'Pedir 15 d', 'Pedir 30 d', 'Último pedido', 'Estado'])
    const [fila1, fila2] = screen.getAllByRole('row').slice(1)
    expect(within(fila1).getByText('0,31')).toBeInTheDocument()
    expect(within(fila1).getByText('0')).toHaveClass('text-texto-vacio')
    expect(within(fila1).getByText('7')).toHaveClass('font-bold')
    expect(fila2.querySelector('[data-columna="pedir30"]')).toHaveTextContent('—')
  })
  it('sin previsión (TECNICO) las columnas son las de siempre', () => {
    render(<DataTable columns={crearColumnasStock({ onEnCamino: vi.fn() })} data={[base]} vacio="Sin componentes" />)
    expect(screen.queryByRole('columnheader', { name: 'Consumo/día' })).not.toBeInTheDocument()
  })
  it('CSV: con previsión añade las tres columnas al final', () => {
    expect(cabecerasCsvStock(false)).toEqual(CABECERAS_CSV_STOCK)
    expect(cabecerasCsvStock(true)).toEqual([...CABECERAS_CSV_STOCK, 'Consumo/día', 'Pedir 15 d', 'Pedir 30 d'])
    const fila = filaCsvStock(c({ consumoDiario: 0.31, pedir15: 2, pedir30: 7 }), true)
    expect(fila.slice(-3)).toEqual(['0,31', '2', '7'])
    expect(filaCsvStock(c({ activo: false }), true).slice(-3)).toEqual(['—', '—', '—'])
    expect(filaCsvStock(base)).toHaveLength(CABECERAS_CSV_STOCK.length)
  })
```

(añadir `within` al import de `@testing-library/react` y `cabecerasCsvStock` al de `./columnas`).

En `StockPage.test.tsx`, en el test `'Descargar CSV exporta …'` (sesión SUPERTECNICO), las expectativas pasan a:

```tsx
    expect(cabeceras).toEqual(['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro', 'Consumo/día', 'Pedir 15 d', 'Pedir 30 d'])
    expect(filas).toEqual([['bat-x', '2', '3', 'Bajo', '4', '01/09/2026 12:00', '0,31', '2', '7']])
```

y un test nuevo:

```tsx
  it('las columnas de previsión las ven SUPERTECNICO y ADMIN, no el TECNICO', async () => {
    for (const [sesion, ve] of [[SESION_SUPER, true], [SESION_ADMIN, true], [SESION_TEC, false]] as const) {
      const { unmount } = montar(sesion)
      await screen.findByText('lcd-x')
      expect(screen.queryByRole('columnheader', { name: 'Pedir 30 d' }) !== null).toBe(ve)
      unmount()
    }
  })
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
npx vitest run src/modules/almacen/stock 2>&1 | tail -20
```

Esperado: fallan los tests nuevos (`./prevision` no existe; columnas y CSV sin previsión).

- [ ] **Step 3: Implementación**

`src/modules/almacen/stock/prevision.ts`:

```ts
/** Formato de la previsión de pedidos (spec 0.9.5 §3.4). Sin previsión (pieza desactivada, o TECNICO), "—". */
export function formatearConsumo(v: number | null | undefined): string {
  return typeof v === 'number' ? v.toFixed(2).replace('.', ',') : '—'
}

export function formatearPedir(v: number | null | undefined): string {
  return typeof v === 'number' ? String(v) : '—'
}
```

En `columnas.tsx`:

```tsx
import { formatearConsumo, formatearPedir } from './prevision'

/** prefWidth de StockView.fxml :46-53; las tres de la previsión son de la 0.9.5. */
export const ANCHOS_STOCK = { componente: 230, enStock: 80, enCamino: 90, stockMinimo: 100, consumoDia: 95, pedir15: 90, pedir30: 90, ultimoPedido: 120, estado: 100 } as const

/** Un 0 en gris (no hay que pedir) y el resto en negrita, para que salten las piezas que sí. */
function CeldaPedir({ valor }: { valor: number | null }) {
  if (valor === null) return <>{formatearPedir(valor)}</>
  return <span className={cn(valor === 0 ? 'text-texto-vacio' : 'font-bold', CREMA_EN_FILA_SELECCIONADA)}>{valor}</span>
}

/** Previsión de pedidos (spec 0.9.5 §3.4): solo para SUPERTECNICO y ADMIN. */
function columnasPrevision(): ColumnDef<Componente>[] {
  return [
    { id: 'consumoDia', header: 'Consumo/día', size: ANCHOS_STOCK.consumoDia, maxSize: ANCHOS_STOCK.consumoDia, accessorFn: (c) => formatearConsumo(c.consumoDiario) },
    { id: 'pedir15', header: 'Pedir 15 d', size: ANCHOS_STOCK.pedir15, maxSize: ANCHOS_STOCK.pedir15, cell: ({ row }) => <CeldaPedir valor={row.original.pedir15} /> },
    { id: 'pedir30', header: 'Pedir 30 d', size: ANCHOS_STOCK.pedir30, maxSize: ANCHOS_STOCK.pedir30, cell: ({ row }) => <CeldaPedir valor={row.original.pedir30} /> },
  ]
}
```

`crearColumnasStock` pasa a recibir `{ onEnCamino, conPrevision = false }: { onEnCamino: (c: Componente) => void; conPrevision?: boolean }` y, en el array que devuelve, justo después de la columna `stockMinimo`, `...(conPrevision ? columnasPrevision() : []),`.

CSV, al final del fichero:

```tsx
export const CABECERAS_CSV_PREVISION = ['Consumo/día', 'Pedir 15 d', 'Pedir 30 d']
export function cabecerasCsvStock(conPrevision: boolean): string[] {
  return conPrevision ? [...CABECERAS_CSV_STOCK, ...CABECERAS_CSV_PREVISION] : CABECERAS_CSV_STOCK
}
export function filaCsvStock(c: Componente, conPrevision = false): string[] {
  const fila = [c.tipo, String(c.stock), String(c.stockMinimo), estadoStock(c), String(c.enCamino), formatear(c.fechaRegistro, 'dd/MM/yyyy HH:mm')]
  return conPrevision ? [...fila, formatearConsumo(c.consumoDiario), formatearPedir(c.pedir15), formatearPedir(c.pedir30)] : fila
}
```

(la `filaCsvStock` anterior se sustituye por esta).

En `StockPage.tsx`, mover `const veEnCamino = esAdminOSuperTecnico(sesion)` por encima de `columnas` (si no lo está ya) y:

```tsx
  const columnas = useMemo(() => crearColumnasStock({ onEnCamino: irAPedidos, conPrevision: veEnCamino }), [irAPedidos, veEnCamino])

  useRegistrarExportable(() => descargarCsv('stock_actual', cabecerasCsvStock(veEnCamino), visibles.map((c) => filaCsvStock(c, veEnCamino))))
```

(import `cabecerasCsvStock` en lugar de `CABECERAS_CSV_STOCK`).

- [ ] **Step 4: Ejecutar y ver que pasan**

```bash
npx vitest run src/modules/almacen/stock 2>&1 | tail -10 && npm run typecheck 2>&1 | tail -3 && npm run lint 2>&1 | tail -3
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock
git commit -m "feat: columnas consumo dia y pedir 15 y 30 dias en stock para supertecnico y admin"
```

---

### Task 8: Diálogo "Parámetros de previsión" (solo ADMIN)

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/ui/DialogoAlmacen.tsx` (propiedad `accionDeshabilitada`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/prevision.ts` (validación de pesos)
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/api.ts` (consulta y mutación)
- Create: `gestion-reparaciones-web/src/modules/almacen/stock/ParametrosPrevisionDialog.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.tsx` (botón y diálogo)
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/prevision.test.ts`, `ParametrosPrevisionDialog.test.tsx`, `StockPage.test.tsx`

**Interfaces:**
- Consumes: `PesosPrevision` (Task 6), rutas `GET/PUT /api/parametros/prevision`.
- Produces:
  - `MSG_PESOS_NO_VALIDOS`, `AYUDA_PESOS`, `leerPesos(textos: [string, string, string]): PesosPrevision | null`, `sumaPesos(textos): number | null` en `prevision.ts`
  - `useParametrosPrevision(abierto: boolean)`, `useGuardarParametrosPrevision()` en `api.ts`
  - `<ParametrosPrevisionDialog abierto onCerrar />`
  - `DialogoAlmacen` con `accionDeshabilitada?: boolean`

- [ ] **Step 1: Tests que fallan**

En `prevision.test.ts`, añadir:

```ts
import { leerPesos, sumaPesos } from './prevision'

describe('pesos de la previsión', () => {
  it('tres enteros de 0 a 100 que suman 100', () => {
    expect(leerPesos(['50', '30', '20'])).toEqual({ peso1: 50, peso2: 30, peso3: 20 })
    expect(leerPesos([' 100 ', '0', '0'])).toEqual({ peso1: 100, peso2: 0, peso3: 0 })
    expect(leerPesos(['50', '30', '30'])).toBeNull()
    expect(leerPesos(['50', '30', ''])).toBeNull()
    expect(leerPesos(['50', '30', '2.5'])).toBeNull()
    expect(leerPesos(['150', '-30', '-20'])).toBeNull()
  })
  it('suma en vivo; null si algún campo no es un entero de 0 a 100', () => {
    expect(sumaPesos(['50', '30', '30'])).toBe(110)
    expect(sumaPesos(['50', '', '20'])).toBeNull()
  })
})
```

`src/modules/almacen/stock/ParametrosPrevisionDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_ADMIN } from '@/test/render'
import { server } from '@/test/server'
import { ParametrosPrevisionDialog } from './ParametrosPrevisionDialog'

function abrir(onCerrar = vi.fn()) {
  server.use(http.get('*/api/parametros/prevision', () => HttpResponse.json({ peso1: 50, peso2: 30, peso3: 20 })))
  renderConProviders(<ParametrosPrevisionDialog abierto onCerrar={onCerrar} />, { sesion: SESION_ADMIN })
  return onCerrar
}
const dlg = () => within(screen.getByRole('dialog', { name: 'Parámetros de previsión' }))

describe('ParametrosPrevisionDialog', () => {
  it('carga los pesos guardados, enseña la ayuda y la suma', async () => {
    abrir()
    await waitFor(() => expect(dlg().getByLabelText('Días 1-30 (%)')).toHaveValue('50'))
    expect(dlg().getByLabelText('Días 31-60 (%)')).toHaveValue('30')
    expect(dlg().getByLabelText('Días 61-90 (%)')).toHaveValue('20')
    expect(dlg().getByText('Lo reciente pesa más. Los tres tienen que sumar 100.')).toBeInTheDocument()
    expect(dlg().getByText('Suma: 100 %')).not.toHaveClass('text-texto-error')
  })
  it('si no suman 100, la suma en rojo y "Guardar" desactivado', async () => {
    abrir()
    const campo = await dlg().findByDisplayValue('20')
    await userEvent.clear(campo)
    await userEvent.type(campo, '30')
    expect(dlg().getByText('Suma: 110 %')).toHaveClass('text-texto-error')
    expect(dlg().getByRole('button', { name: 'Guardar' })).toBeDisabled()
  })
  it('guarda con PUT y cierra', async () => {
    const cuerpos: unknown[] = []
    server.use(http.put('*/api/parametros/prevision', async ({ request }) => { cuerpos.push(await request.json()); return new HttpResponse(null, { status: 200 }) }))
    const onCerrar = abrir()
    const p1 = await dlg().findByDisplayValue('50')
    await userEvent.clear(p1)
    await userEvent.type(p1, '40')
    const p2 = dlg().getByLabelText('Días 31-60 (%)')
    await userEvent.clear(p2)
    await userEvent.type(p2, '35')
    const p3 = dlg().getByLabelText('Días 61-90 (%)')
    await userEvent.clear(p3)
    await userEvent.type(p3, '25')
    await userEvent.click(dlg().getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(cuerpos).toEqual([{ peso1: 40, peso2: 35, peso3: 25 }]))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
  })
  it('un 422 del servidor se pinta dentro y el diálogo sigue abierto', async () => {
    server.use(http.put('*/api/parametros/prevision', () =>
      HttpResponse.json({ message: 'Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.' }, { status: 422 })))
    const onCerrar = abrir()
    await dlg().findByDisplayValue('50')
    await userEvent.click(dlg().getByRole('button', { name: 'Guardar' }))
    expect(await dlg().findByRole('alert')).toHaveTextContent('Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.')
    expect(onCerrar).not.toHaveBeenCalled()
  })
})
```

En `StockPage.test.tsx`, añadir:

```tsx
  it('"Parámetros de previsión" solo lo ve el ADMIN', async () => {
    for (const [sesion, ve] of [[SESION_ADMIN, true], [SESION_SUPER, false], [SESION_TEC, false]] as const) {
      const { unmount } = montar(sesion)
      await screen.findByText('lcd-x')
      expect(screen.queryByRole('button', { name: 'Parámetros de previsión' }) !== null).toBe(ve)
      unmount()
    }
  })
  it('el botón abre el diálogo con los pesos y congela el sondeo mientras está abierto', async () => {
    server.use(http.get('*/api/parametros/prevision', () => HttpResponse.json({ peso1: 50, peso2: 30, peso3: 20 })))
    montar(SESION_ADMIN)
    await screen.findByText('lcd-x')
    await userEvent.click(screen.getByRole('button', { name: 'Parámetros de previsión' }))
    expect(await screen.findByRole('dialog', { name: 'Parámetros de previsión' })).toBeInTheDocument()
    const antes = cargas.n
    await act(() => new Promise((r) => setTimeout(r, 50)))
    expect(cargas.n).toBe(antes)
  })
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
npx vitest run src/modules/almacen/stock 2>&1 | tail -20
```

Esperado: fallan los nuevos (no existen `leerPesos`, el diálogo ni el botón).

- [ ] **Step 3: Implementación**

`DialogoAlmacen.tsx`: en `Props`, `accionDeshabilitada?: boolean` con el comentario `/** Desactiva el botón de acción (p. ej. pesos que no suman 100); Enter tampoco confirma porque onConfirmar valida. */`; en la firma, `accionDeshabilitada = false`; y el botón:

```tsx
            <BotonPrimario type="submit" disabled={enviando || accionDeshabilitada}>{textoAccion}</BotonPrimario>
```

En `prevision.ts`, añadir:

```ts
import type { PesosPrevision } from '@/shared/api/client'
import { parseEnteroNoNegativo } from './dialogos'

export const AYUDA_PESOS = 'Lo reciente pesa más. Los tres tienen que sumar 100.'
export const MSG_PESOS_NO_VALIDOS = 'Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.'

type Textos = readonly [string, string, string]

function peso(texto: string): number | null {
  const n = parseEnteroNoNegativo(texto)
  return n !== null && n <= 100 ? n : null
}

/** Suma de los tres campos, o null si alguno no es un entero de 0 a 100. */
export function sumaPesos(textos: Textos): number | null {
  const ns = textos.map(peso)
  return ns.every((n) => n !== null) ? (ns as number[]).reduce((a, b) => a + b, 0) : null
}

/** Los tres pesos si son válidos (enteros de 0 a 100 que suman 100), o null. La misma regla que el servidor. */
export function leerPesos(textos: Textos): PesosPrevision | null {
  if (sumaPesos(textos) !== 100) return null
  const [peso1, peso2, peso3] = textos.map((t) => peso(t) as number)
  return { peso1, peso2, peso3 }
}
```

En `api.ts`, añadir (import `type PesosPrevision` desde `@/shared/api/client`):

```ts
export const CLAVE_PARAMETROS_PREVISION = ['parametros', 'prevision'] as const

/** GET /api/parametros/prevision (solo ADMIN): se pide al abrir el diálogo, siempre fresco. Sin refetch por foco: una
 *  recarga con el diálogo abierto pisaría lo que el administrador está tecleando. */
export function useParametrosPrevision(abierto: boolean): UseQueryResult<PesosPrevision | null> {
  return useQuery({
    queryKey: CLAVE_PARAMETROS_PREVISION,
    queryFn: async () => (await api.GET('/api/parametros/prevision')).data ?? null,
    enabled: abierto,
    staleTime: 0,
    refetchOnWindowFocus: false,
  })
}

/** Su 422 se pinta dentro del diálogo; al terminar se recarga Stock (la previsión cambia con los pesos). */
export function useGuardarParametrosPrevision(): UseMutationResult<unknown, unknown, PesosPrevision> {
  const recargar = useRecarga()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (pesos: PesosPrevision) => api.PUT('/api/parametros/prevision', { body: pesos }),
    meta: { silenciarError: true },
    onSettled: () => {
      recargar()
      void qc.invalidateQueries({ queryKey: CLAVE_PARAMETROS_PREVISION })
    },
  })
}
```

`src/modules/almacen/stock/ParametrosPrevisionDialog.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { esErrorGestionadoGlobalmente, mensajeDeError, ReglaNegocioError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/utils'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { DialogoAlmacen } from '../ui/DialogoAlmacen'
import { useGuardarParametrosPrevision, useParametrosPrevision } from './api'
import { AYUDA_PESOS, leerPesos, MSG_PESOS_NO_VALIDOS, sumaPesos } from './prevision'

type Props = { abierto: boolean; onCerrar: () => void }
type Textos = [string, string, string]

const CAMPOS = [
  { id: 'prevision-peso-1', etiqueta: 'Días 1-30 (%)' },
  { id: 'prevision-peso-2', etiqueta: 'Días 31-60 (%)' },
  { id: 'prevision-peso-3', etiqueta: 'Días 61-90 (%)' },
] as const

/**
 * Pesos de la previsión de pedidos (spec 0.9.5 §4.3), solo para el ADMIN. Tres porcentajes enteros que suman 100,
 * la suma en vivo y "Guardar" desactivado mientras no cuadren; el servidor aplica la misma regla y su 422 sale dentro.
 */
export function ParametrosPrevisionDialog({ abierto, onCerrar }: Props) {
  const { mostrarError } = useAlerta()
  const { data } = useParametrosPrevision(abierto)
  const guardar = useGuardarParametrosPrevision()
  const [textos, setTextos] = useState<Textos>(['', '', ''])
  const [error, setError] = useState<string | null>(null)

  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga los campos cuando llegan los pesos guardados
    if (abierto && data) { setTextos([String(data.peso1), String(data.peso2), String(data.peso3)]); setError(null) }
  }, [abierto, data])

  const suma = sumaPesos(textos)
  const pesos = leerPesos(textos)

  function cambiar(i: 0 | 1 | 2, valor: string) {
    const nuevos: Textos = [...textos]
    nuevos[i] = valor
    setTextos(nuevos)
    setError(null)
  }

  function confirmar() {
    if (pesos === null) { setError(MSG_PESOS_NO_VALIDOS); return }
    setError(null)
    guardar.mutate(pesos, {
      onSuccess: onCerrar,
      onError: (e) => {
        if (e instanceof ReglaNegocioError) { setError(e.message); return }
        if (!esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e))
      },
    })
  }

  return (
    <DialogoAlmacen abierto={abierto} titulo="Parámetros de previsión" error={error} textoAccion="Guardar" enviando={guardar.isPending} accionDeshabilitada={pesos === null} onConfirmar={confirmar} onCancelar={onCerrar}>
      {CAMPOS.map((campo, i) => (
        <div key={campo.id} className="flex items-center justify-between gap-3">
          <Label htmlFor={campo.id} className="text-[12px] font-bold text-azul-gris">{campo.etiqueta}</Label>
          <Input id={campo.id} value={textos[i]} inputMode="numeric" onChange={(e) => cambiar(i as 0 | 1 | 2, e.target.value)} className="w-[80px] bg-superficie text-[13px] text-azul-medio" />
        </div>
      ))}
      <p className={cn('text-[12px] font-bold', suma === 100 ? 'text-azul-gris' : 'text-texto-error')}>Suma: {suma ?? '—'} %</p>
      <p className="text-[11px] text-azul-gris">{AYUDA_PESOS}</p>
    </DialogoAlmacen>
  )
}
```

En `StockPage.tsx`:

```tsx
import { ParametrosPrevisionDialog } from './ParametrosPrevisionDialog'
…
  const [parametrosAbiertos, setParametrosAbiertos] = useState(false)
…
  // Un diálogo abierto (de fila o de parámetros) congela el sondeo (D4 del 3a).
  useEffect(() => {
    if (!dialogo && !parametrosAbiertos) return
    marcar(true)
    return () => marcar(false)
  }, [dialogo, parametrosAbiertos, marcar])
```

(sustituye al `useEffect` de `dialogo` que había). En la barra, tras "Limpiar filtros":

```tsx
        {esAdmin(sesion) && <BotonSecundario onClick={() => setParametrosAbiertos(true)}>Parámetros de previsión</BotonSecundario>}
```

y junto a los otros diálogos, al final:

```tsx
      <ParametrosPrevisionDialog abierto={parametrosAbiertos} onCerrar={() => setParametrosAbiertos(false)} />
```

- [ ] **Step 4: Ejecutar y ver que pasan**

```bash
npx vitest run src/modules/almacen 2>&1 | tail -10 && npm run typecheck 2>&1 | tail -3 && npm run lint 2>&1 | tail -3
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen
git commit -m "feat: dialogo de parametros de prevision en stock para el admin"
```

---

### Task 9: Menú de Stock por rol

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/MenuComponente.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.tsx` (`rol` y `menuFila`)
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.test.tsx`

**Interfaces:**
- Produces: `MenuComponente` con `rol: 'ADMIN' | 'SUPERTECNICO' | 'TECNICO'`.

- [ ] **Step 1: Tests que fallan**

En `StockPage.test.tsx`:
- El test `'menú del supertécnico: …'` pasa a llamarse `'menú del supertécnico: cuatro ítems sin "Ajustar mínimo", con separadores; "Activar" en una desactivada'` y sus expectativas a:

  ```tsx
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Pedir', 'Editar stock', 'Desactivar', 'Solicitar pieza'])
    expect(screen.getAllByRole('separator')).toHaveLength(2)
  ```

- El test `'ADMIN no tiene menú contextual'` se sustituye por:

  ```tsx
  it('ADMIN solo ve "Ajustar mínimo"', async () => {
    montar(SESION_ADMIN)
    await screen.findByText('lcd-x')
    await userEvent.pointer({ keys: '[MouseRight]', target: filaDe('lcd-x') })
    expect(screen.getAllByRole('menuitem').map((i) => i.textContent)).toEqual(['Ajustar mínimo'])
  })
  ```

- En `'un 422 del servidor se muestra inline … (Editar stock y Ajustar mínimo)'`: la parte de Editar stock sigue con `montar()`; antes de la parte de Ajustar mínimo, `cleanup()` (import de `@testing-library/react`) y `montar(SESION_ADMIN)` + `await screen.findByText('lcd-x')`.
- En `'"Ajustar mínimo" hace el PATCH; "Desactivar" …; "Solicitar pieza" …'`: mover el tramo de Ajustar mínimo a un test propio con `montar(SESION_ADMIN)`; el resto (Desactivar, Solicitar pieza) sigue con `montar()`.
- En `'"Ajustar mínimo": tras el PATCH se cierra el diálogo y se recarga …'`: `montar(SESION_ADMIN)`.

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
npx vitest run src/modules/almacen/stock/StockPage.test.tsx 2>&1 | tail -20
```

Esperado: fallan los del menú (el supertécnico aún tiene "Ajustar mínimo"; el ADMIN no tiene menú).

- [ ] **Step 3: Implementación**

`MenuComponente.tsx`: `rol: 'ADMIN' | 'SUPERTECNICO' | 'TECNICO'`; el comentario de la función pasa a decir "supertécnico sin «Ajustar mínimo», que desde la 0.9.5 es solo del ADMIN (su único ítem); técnico solo «Solicitar pieza»"; y el cuerpo:

```tsx
  if (rol === 'TECNICO') return <ContextMenuItem onSelect={() => onSolicitar(c)}>Solicitar pieza</ContextMenuItem>
  if (rol === 'ADMIN') return <ContextMenuItem onSelect={() => onAjustarMinimo(c)}>Ajustar mínimo</ContextMenuItem>
  return (
    <>
      <ContextMenuItem onSelect={() => onPedir(c)}>Pedir</ContextMenuItem>
      <ContextMenuItem onSelect={() => onEditarStock(c)}>Editar stock</ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem onSelect={() => onToggleActivo(c)}>{c.activo ? 'Desactivar' : 'Activar'}</ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem onSelect={() => onSolicitar(c)}>Solicitar pieza</ContextMenuItem>
    </>
  )
```

`StockPage.tsx`:

```tsx
  const rol = esSuperTecnico(sesion) ? 'SUPERTECNICO' : esAdmin(sesion) ? 'ADMIN' : 'TECNICO'
```

y `menuFila={(c) => (<MenuComponente … />)}` sin el `rol ? … : undefined`.

- [ ] **Step 4: Ejecutar y ver que pasan**

```bash
npx vitest run src/modules/almacen 2>&1 | tail -10 && npm run typecheck 2>&1 | tail -3
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/stock
git commit -m "feat: ajustar minimo pasa al menu del admin y sale del supertecnico"
```

---

### Task 10: Mensaje propio al cerrar por inactividad

**Files:**
- Modify: `gestion-reparaciones-web/src/shared/session/expiracion.ts`
- Modify: `gestion-reparaciones-web/src/shared/session/VigilanciaInactividad.tsx`
- Modify: `gestion-reparaciones-web/src/app/session/mensajes.ts`
- Modify: `gestion-reparaciones-web/src/main.tsx`
- Test: `gestion-reparaciones-web/src/shared/session/expiracion.test.ts`, `VigilanciaInactividad.test.tsx`, `src/app/session/mensajes.test.ts`

**Interfaces:**
- Produces: `type MotivoExpiracion = 'inactividad'`; `dispararSesionExpirada(motivo?: MotivoExpiracion)`; `onSesionExpirada(h: (motivo?: MotivoExpiracion) => void)`; `MSG_SESION_INACTIVIDAD_UI`; `mensajeSesionCerrada(motivo?: MotivoExpiracion): string`.

- [ ] **Step 1: Tests que fallan**

En `expiracion.test.ts`, añadir:

```ts
  it('pasa el motivo al handler; sin motivo, undefined', () => {
    const h = vi.fn()
    onSesionExpirada(h)
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't', passwordTemporal: false })
    dispararSesionExpirada('inactividad')
    expect(h).toHaveBeenCalledWith('inactividad')
    rearmarSesionExpirada()
    dispararSesionExpirada()
    expect(h).toHaveBeenLastCalledWith(undefined)
  })
```

En `VigilanciaInactividad.test.tsx`, el mock pasa a reenviar el argumento:

```tsx
vi.mock('./expiracion', () => ({
  dispararSesionExpirada: (...args: unknown[]) => expulsar(...args),
}))
```

y en `'pasado el tope expulsa por el camino de sesión caducada'`, `expect(expulsar).toHaveBeenCalledWith('inactividad')` tras el `toHaveBeenCalledTimes(1)`.

`src/app/session/mensajes.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mensajeSesionCerrada, MSG_SESION_EXPIRADA_UI, MSG_SESION_INACTIVIDAD_UI } from './mensajes'

describe('mensaje del login al cerrar la sesión', () => {
  it('por inactividad, el propio; en cualquier otro caso, el de sesión expirada', () => {
    expect(mensajeSesionCerrada('inactividad')).toBe('Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo.')
    expect(MSG_SESION_INACTIVIDAD_UI).toBe('Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo.')
    expect(mensajeSesionCerrada()).toBe(MSG_SESION_EXPIRADA_UI)
  })
})
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
npx vitest run src/shared/session src/app/session 2>&1 | tail -15
```

Esperado: fallan los nuevos.

- [ ] **Step 3: Implementación**

`expiracion.ts`:

```ts
import { leerSesion } from './storage'

/** Por qué se cierra la sesión, cuando no es un 401 cualquiera: cambia el mensaje del login (spec 0.9.5 §5). */
export type MotivoExpiracion = 'inactividad'

/** Un 401 con sesión activa expulsa al usuario una sola vez (varias peticiones pueden fallar a la vez). */
let handler: ((motivo?: MotivoExpiracion) => void) | null = null
let disparado = false

/** Registra el handler (uno solo: el último gana) y devuelve cómo retirarlo. El unsubscribe solo quita `h` si sigue siendo
 *  el actual: el de un handler ya sustituido no deja a la app sin el nuevo. */
export function onSesionExpirada(h: (motivo?: MotivoExpiracion) => void): () => void {
  handler = h
  return () => {
    if (handler === h) handler = null
  }
}
export function dispararSesionExpirada(motivo?: MotivoExpiracion) {
  if (disparado || !leerSesion() || !handler) return
  disparado = true
  handler(motivo)
}
/** Llamar tras un login correcto. */
export function rearmarSesionExpirada() {
  disparado = false
}
```

`VigilanciaInactividad.tsx`: `dispararSesionExpirada('inactividad')` y, en el comentario de la función, "Expulsa por `dispararSesionExpirada('inactividad')`, el mismo camino que un 401 pero con su propio mensaje en el login."

`app/session/mensajes.ts`:

```ts
import type { MotivoExpiracion } from '@/shared/session/expiracion'

export const MSG_SESION_EXPIRADA_UI = 'Tu sesión ha expirado. Inicia sesión de nuevo.'
export const MSG_SESION_INACTIVIDAD_UI = 'Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo.'

/** Mensaje del login cuando la aplicación cierra la sesión (spec 0.9.5 §5). */
export function mensajeSesionCerrada(motivo?: MotivoExpiracion): string {
  return motivo === 'inactividad' ? MSG_SESION_INACTIVIDAD_UI : MSG_SESION_EXPIRADA_UI
}
```

`main.tsx`: importar `mensajeSesionCerrada` en lugar de `MSG_SESION_EXPIRADA_UI` y:

```tsx
  onSesionExpirada((motivo) => {
    borrarSesion()
    sessionStorage.setItem('fsgr.mensajeLogin', mensajeSesionCerrada(motivo))
    window.location.assign('/login')
  })
```

(y en el comentario de encima, "con el mensaje" → "con el mensaje del motivo (inactividad o sesión caducada)").

- [ ] **Step 4: Ejecutar y ver que pasan**

```bash
npx vitest run src/shared/session src/app 2>&1 | tail -10 && npm run typecheck 2>&1 | tail -3 && npm run lint 2>&1 | tail -3
```

Esperado: todo en verde (si `lint` se queja de que `app/` importa de `shared/`, es la dirección permitida; si se queja de lo contrario, mover `MotivoExpiracion` no hace falta: `shared` no importa de `app`).

- [ ] **Step 5: Commit**

```bash
git add src/shared/session src/app/session src/main.tsx
git commit -m "feat: mensaje propio en el login al cerrar la sesion por inactividad"
```

---

### Task 11: Enter rápido en los formularios de contraseña

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/gestion/cuenta/medidor.ts` (`bloqueaGuardar`)
- Test: `gestion-reparaciones-web/src/modules/gestion/cuenta/MedidorPassword.test.tsx`, `CambiarPasswordDialog.test.tsx`, `src/app/cuenta/CambioObligatorioPage.test.tsx`

**Interfaces:**
- Produces: `bloqueaGuardar(e: EstadoNota): boolean` solo `true` con una nota recibida y no aceptable.

- [ ] **Step 1: Tests que fallan**

`MedidorPassword.test.tsx`:
- en `'mientras comprueba la nueva conserva la nota anterior …'`, `expect(bloqueaGuardar(result.current)).toBe(true)` pasa a `toBe(false)`;
- el test de `describe('bloqueaGuardar')` pasa a:

  ```ts
  it('solo bloquea una nota recibida y no aceptable; mientras comprueba decide el servidor (spec 0.9.5 §6)', () => {
    expect(bloqueaGuardar({ estado: 'comprobando' })).toBe(false)
    expect(bloqueaGuardar({ estado: 'comprobando', previa: { estado: 'listo', nota: 2, aceptable: false, mensaje: 'x' } })).toBe(false)
    expect(bloqueaGuardar({ estado: 'listo', nota: 2, aceptable: false, mensaje: 'x' })).toBe(true)
    expect(bloqueaGuardar({ estado: 'listo', nota: 3, aceptable: true, mensaje: null })).toBe(false)
    expect(bloqueaGuardar({ estado: 'vacio' })).toBe(false)
    expect(bloqueaGuardar({ estado: 'error' })).toBe(false)
  })
  ```

`CambiarPasswordDialog.test.tsx`, dentro del `describe` del Enter:

```tsx
  it('Enter pulsado justo después de teclear la nueva (sin esperar a la nota) guarda', async () => {
    const cuerpos = registrarCambio()
    abrir()
    await rellenar('secreta1', '', 'nueva-larga-123')
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'nueva-larga-123{Enter}')
    await waitFor(() => expect(cuerpos).toEqual([{ passwordActual: 'secreta1', passwordNueva: 'nueva-larga-123' }]))
  })
```

`CambioObligatorioPage.test.tsx`, dentro de `describe('CambioObligatorioPage')`:

```tsx
  it('Enter pulsado justo después de teclear la nueva (sin esperar a la nota) guarda', async () => {
    const cuerpos = registrarCambio()
    montar()
    await rellenar('LaTemporal9', '', 'MiClaveNueva1')
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'MiClaveNueva1{Enter}')
    expect(await screen.findByText('APLICACIÓN')).toBeInTheDocument()
    expect(cuerpos).toEqual([{ passwordActual: 'LaTemporal9', passwordNueva: 'MiClaveNueva1' }])
  })
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
npx vitest run src/modules/gestion/cuenta src/app/cuenta 2>&1 | tail -15
```

Esperado: fallan los nuevos y los cambiados (hoy `comprobando` bloquea).

- [ ] **Step 3: Implementación**

En `medidor.ts`:

```ts
/** "Guardar" solo se bloquea con una nota ya recibida que el servidor no acepta. Vacía no bloquea (al pulsar sale
 *  "Rellena todos los campos."), un fallo de la consulta tampoco, y mientras se comprueba tampoco: un Enter pulsado
 *  antes de que llegue la nota envía y decide el servidor, que aplica la misma regla (spec 0.9.5 §6). */
export function bloqueaGuardar(e: EstadoNota): boolean {
  return e.estado === 'listo' && !e.aceptable
}
```

- [ ] **Step 4: Ejecutar y ver que pasan**

```bash
npx vitest run src/modules/gestion/cuenta src/app/cuenta 2>&1 | tail -10
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/gestion/cuenta src/app/cuenta
git commit -m "fix: enter rapido en las contrasenas envia y decide el servidor"
```

---

## VERSIÓN Y DESPLIEGUE

### Task 12: Versión 0.9.5, merges y preprod (en horario)

**Files:**
- Modify: `gestion-reparaciones-web/package.json` (+ `package-lock.json`), `gestion-reparaciones-web/CHANGELOG.md`
- Create: `docs/novedades/NOVEDADES-v0.9.5.md` (raíz)
- Fuera de git: `Apuntes/despliegue_preprod.md` (registro de sesiones)

- [ ] **Step 1: Versión y documentos**

`npm version 0.9.5 --no-git-tag-version` en la web. En `CHANGELOG.md` de la web, encima de `## [0.9.4]`:

```markdown
## [0.9.5] - 2026-10-.. — Previsión de pedidos

- **Previsión de pedidos en Stock** (supertécnicos y administrador). Tres columnas nuevas: **Consumo/día** (lo que se gasta al día de cada pieza, con más peso lo de los últimos 30 días: 50 %, 30 % y 20 % para los días 1-30, 31-60 y 61-90) y **Pedir 15 d** / **Pedir 30 d** (cuánto pedir para cubrir 15 o 30 días, contando el stock y lo que ya está en camino, sin bajar nunca del stock mínimo). Un 0 sale en gris. También van en el CSV.
- **El stock mínimo es el suelo y solo lo cambia el administrador** (clic derecho → "Ajustar mínimo"). Los supertécnicos ya no tienen esa opción; "Editar stock" no toca el mínimo.
- **Parámetros de previsión** (solo administrador): botón en Stock para cambiar los tres pesos; tienen que sumar 100.
- Al cerrarse la sesión por dos horas sin uso, el login lo dice: "Se cerró la sesión tras dos horas sin uso."
- En "Cambiar contraseña" y "Cambia tu contraseña", un Enter pulsado justo después de escribir ya guarda (decide el servidor).
- Requiere el servidor 0.9.5 y la migración `migracion-parametros-prevision.sql` (tabla `Parametro`). Sin cambios en la configuración de nginx.
```

`docs/novedades/NOVEDADES-v0.9.5.md` (raíz), con el formato de `NOVEDADES-v0.9.2.md`:
- "📦 Stock: cuánto pedir" (para supertécnicos y administrador: qué son las tres columnas; ejemplo sencillo; las piezas que ya no se trabajan se desactivan);
- "🛠️ Solo administrador" (mínimo y parámetros);
- "🔐 Sesión y contraseñas" (mensaje de inactividad y Enter);
- "🚚 Notas de despliegue" (migración antes del servidor; servidor y web 0.9.5, primero el servidor).

```bash
cd gestion-reparaciones-web && git add package.json package-lock.json CHANGELOG.md && git commit -m "chore: version 0.9.5"
```

- [ ] **Step 2: Suite completa, compilación y revisión de la web**

```bash
cd gestion-reparaciones-web && npm run check 2>&1 | tail -20 && npm run build 2>&1 | tail -5
```

`superpowers:requesting-code-review` de `feature/prevision-pedidos` de la web, con foco en: que el TECNICO no ve ni descarga la previsión, que el diálogo de parámetros no deja guardar pesos que no suman 100 y congela el sondeo, que el menú por rol coincide con el servidor (mínimo solo ADMIN), y que el Enter solo envía cuando la nota no está ya rechazada.

- [ ] **Step 3: Merges y push (con OK, uno a uno)**

```bash
cd gestion-reparaciones-servidor && git checkout main && git merge --no-ff feature/prevision-pedidos -m "merge: prevision de pedidos y minimo y parametros solo para el admin (0.9.5)"
cd ../gestion-reparaciones-web && git checkout main && git merge --no-ff feature/prevision-pedidos -m "merge: prevision de pedidos en stock, parametros, inactividad y enter en contrasenas (0.9.5)"
```

Push de cada `main` con su OK.

- [ ] **Step 4: Preprod (lo ejecuta el usuario, una sola sesión `ssh preprod`, en horario)**

1. **Refrescar la base desde producción** con el procedimiento "Refrescar la base desde producción" de `Apuntes/despliegue_preprod.md` (incluye el saneado de cuentas).
2. **Comprobación de solo lectura del consumo** (spec §3.2), desde `/opt/reparaciones`:

   ```bash
   docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones' <<'SQL'
   -- 1) Ninguna solicitud bajo R…/G… (esperado: 0)
   SELECT COUNT(*) AS solicitudes_bajo_RG FROM Reparacion_componente
    WHERE (ID_REP LIKE 'R%' OR ID_REP LIKE 'G%') AND (ES_SOLICITUD = 1 OR DESCRIPCION_SOLICITUD IS NOT NULL);
   -- 2) Dónde vive cada cosa (las solicitudes, bajo A/AG)
   SELECT LEFT(ID_REP, 1) AS pref, ES_SOLICITUD, COUNT(*) FROM Reparacion_componente GROUP BY pref, ES_SOLICITUD;
   -- 3) Las tres piezas que más se gastan, con sus tramos (para comparar con la pantalla)
   SELECT COALESCE(c.ID_COM_MASTER, c.ID_COM) AS master, m.TIPO, m.STOCK, m.STOCK_MINIMO,
          SUM(CASE WHEN r.FECHA_FIN >= CURDATE() - INTERVAL 30 DAY THEN rc.CANTIDAD ELSE 0 END) AS t1,
          SUM(CASE WHEN r.FECHA_FIN <  CURDATE() - INTERVAL 30 DAY AND r.FECHA_FIN >= CURDATE() - INTERVAL 60 DAY THEN rc.CANTIDAD ELSE 0 END) AS t2,
          SUM(CASE WHEN r.FECHA_FIN <  CURDATE() - INTERVAL 60 DAY THEN rc.CANTIDAD ELSE 0 END) AS t3
     FROM Reparacion_componente rc JOIN Reparacion r ON rc.ID_REP = r.ID_REP
     JOIN Componente c ON rc.ID_COM = c.ID_COM JOIN Componente m ON m.ID_COM = COALESCE(c.ID_COM_MASTER, c.ID_COM)
    WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND rc.ES_REUTILIZADO = 0 AND c.TIPO NOT LIKE 'otro%'
      AND r.FECHA_FIN >= CURDATE() - INTERVAL 90 DAY AND r.FECHA_FIN < CURDATE()
    GROUP BY master, m.TIPO, m.STOCK, m.STOCK_MINIMO ORDER BY t1 DESC LIMIT 3;
   SQL
   ```

   Si la consulta 1 no da 0, **parar** y revisar antes de seguir.
3. **Migración, con su comprobación en el mismo bloque, y despliegue (servidor antes que web):**

   ```bash
   cd /opt/reparaciones
   git -C gestion-reparaciones-servidor pull --ff-only && git -C gestion-reparaciones-servidor log --oneline -1
   docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < gestion-reparaciones-servidor/sql/migracion-parametros-prevision.sql
   docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones -e "SELECT CLAVE, VALOR FROM Parametro ORDER BY CLAVE"'
   docker compose up -d --build backend && sleep 25 && docker compose logs backend --tail 15 | grep -E "Started|ERROR|Exception"
   git -C gestion-reparaciones-web pull --ff-only && git -C gestion-reparaciones-web log --oneline -1
   docker compose up -d --build nginx
   ```

   Esperado: los dos `merge` de la 0.9.5, las tres claves (50/30/20), `Started App` sin excepciones.

- [ ] **Step 5: Comprobación en preprod (desde el PC)**

1. `/version.json` = `{"version":"0.9.5"}` y el letrero sigue saliendo (`preprod/comprobar-letrero.cjs`).
2. Con las cuentas de pruebas de `~/.env.e2e`:
   - **SUPERTECNICO:** Stock con las tres columnas; las cifras de las tres piezas de la consulta 3 cuadran con `(0,5·t1 + 0,3·t2 + 0,2·t3)/30` y con `máx(mínimo; consumo × N) − (stock + en camino)`; menú sin "Ajustar mínimo"; sin botón de parámetros; el CSV trae las tres columnas.
   - **ADMIN:** botón "Parámetros de previsión" (50/30/20); probar 50/30/30 (suma en rojo, Guardar desactivado); guardar 40/35/25, ver que las cifras cambian, y volver a 50/30/20; en Gestión → Registro, las dos líneas `EDITAR_PARAMETROS`; clic derecho → "Ajustar mínimo" en una pieza y comprobar que cambia "Pedir".
   - **TECNICO:** Stock sin las columnas y menú solo "Solicitar pieza".
3. **Inactividad:** con sesión abierta, en la consola del navegador `localStorage.setItem('fsgr.actividad', String(Date.now() - 2*60*60*1000 - 5000))` (`CLAVE_ACTIVIDAD` de `shared/session/storage.ts`); en pocos segundos, login con "Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo.".
4. **Enter rápido:** "Cambiar contraseña" con la cuenta de pruebas, escribir la nueva y pulsar Enter enseguida → guarda (o el servidor explica por qué no). Volver a dejar la contraseña de `~/.env.e2e`.
5. **Smoke E2E:** `( set -a; . ~/.env.e2e; set +a; npx playwright test tests/e2e/gestion.spec.ts --workers=1 )` en el clon de la web.

Anotar todo en el registro de sesiones de `Apuntes/despliegue_preprod.md`.

---

### Task 13: Producción (fuera de horario), tags y gitlinks

**Files:**
- Fuera de git: `Apuntes/despliegue_vdc_produccion.md` (registro), `Apuntes/plan-futuro.md`
- Raíz: gitlinks y `docs/novedades/NOVEDADES-v0.9.5.md`

- [ ] **Step 1: Producción (lo ejecuta el usuario, una sola sesión `ssh prod`, fuera de horario)**

```bash
/usr/local/sbin/backup-erp.sh && tail -n 1 /var/log/backup-erp.log
cd /opt/reparaciones
git -C gestion-reparaciones-servidor pull --ff-only && git -C gestion-reparaciones-servidor log --oneline -1
docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < gestion-reparaciones-servidor/sql/migracion-parametros-prevision.sql
docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones -e "SELECT CLAVE, VALOR FROM Parametro ORDER BY CLAVE"'
docker compose up -d --build backend && sleep 25 && docker compose logs backend --tail 15 | grep -E "Started|ERROR|Exception"
git -C gestion-reparaciones-web pull --ff-only && git -C gestion-reparaciones-web log --oneline -1
docker compose up -d --build nginx
```

Esperado: copia hecha, los dos `merge` de la 0.9.5, las tres claves, `Started App` sin excepciones.

- [ ] **Step 2: Comprobación ligera, sin escribir datos**

`/version.json` = `0.9.5` y sin letrero; como administrador, Stock con las columnas y el botón de parámetros (abrir y **cancelar**, sin guardar); una cuenta supertécnica real, si el usuario quiere, solo para mirar. Registro en `Apuntes/despliegue_vdc_produccion.md` con la vuelta atrás (`checkout` de `ba73737` en el servidor y de `5902bb1` en la web, más `up -d --build`; la tabla `Parametro` puede quedarse: es aditiva).

- [ ] **Step 3: Tags y gitlinks (con OK, uno a uno)**

```bash
cd gestion-reparaciones-servidor && git tag -a v0.9.5 -m "0.9.5: prevision de pedidos y parametros solo admin" && git push origin v0.9.5
cd ../gestion-reparaciones-web && git tag -a v0.9.5 -m "0.9.5: prevision de pedidos y parametros solo admin" && git push origin v0.9.5
cd .. && git add gestion-reparaciones-servidor gestion-reparaciones-web docs/novedades/NOVEDADES-v0.9.5.md
git commit -m "chore: gitlinks servidor y web tras la 0.9.5 (v0.9.5)"
```

- [ ] **Step 4: Plan maestro**

En `Apuntes/plan-futuro.md`: la Fase 5 "Stock mínimo por consumo mensual" pasa a hecha como "Previsión de pedidos (0.9.5)"; el "Mensaje propio al cerrar por inactividad" y el Enter de la 0.9.2 a hechos; backlog nuevo con lo de la spec §9 (URGENTE por cobertura, plazo real por proveedor, gráfica de evolución que cuenta solicitudes, propuesta de pedido ordenada, tramos y horizontes editables).

---

## Autorrevisión del plan

- **Cobertura de la spec:** §2 decisiones → Tasks 1-4 y 7-11; §3.1 regla y ejemplos → Task 1; §3.2 consumo → Task 2 (SQL) y Task 12 (comprobación con datos reales); §3.3 servidor → Tasks 2-3; §3.4 web → Task 7; §4.1 migración → Task 2; §4.2 permisos y parámetros → Task 4; §4.3 web → Tasks 8-9; §5 inactividad → Task 10; §6 Enter → Task 11; §7 versión y despliegue → Tasks 12-13; §8 pruebas → tests de cada tarea y Tasks 12-13; §9 backlog → Task 13 Step 4.
- **Tipos:** `PrevisionPedido.{Pesos,Tramos,ConsumoDia,Resultado}`, `agrupar`, `calcular`, `ComponenteDAO.getConsumoDiario(desde, hasta)`, `ParametroDAO.getPesosPrevision/guardarPesosPrevision`, `PrevisionPedidoService.rellenar(lista, hoy)`, `PesosPrevision(peso1, peso2, peso3)`, `Componente.setPrevision(consumo, p15, p30)`, `crearColumnasStock({ onEnCamino, conPrevision })`, `filaCsvStock(c, conPrevision)`, `cabecerasCsvStock`, `leerPesos`, `sumaPesos`, `useParametrosPrevision`, `useGuardarParametrosPrevision`, `MotivoExpiracion`, `mensajeSesionCerrada`, `bloqueaGuardar`: mismos nombres en todas las tareas.
- **Cambio respecto a la spec, ya reflejado en ella:** `POST /api/componentes` no se toca (ruta retirada, 403 para todos).

---

## AMPLIACIÓN (2026-10-07): una fila por grupo compartido (spec §10)

Solo web, en una rama nueva `feature/grupos-compartidos` desde `main` (`1d261f4`). Mismas restricciones globales.
Textos literales: `stock compartido` (gris pequeño bajo el nombre), separador de nombres ` / `, y en Solicitar pieza la
etiqueta `Modelo` con el aviso de validación `Elige el modelo.`.

### Task 14: Una fila por grupo en Stock (tabla, buscador, CSV, donut, desactivados, llegada desde Pedidos)

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/stock/grupos.ts` + `grupos.test.ts`
- Modify: `stock/filtros.ts` (`nombreComponente` y `aplicarFiltrosStock`), `stock/columnas.tsx` (columna Componente y CSV), `stock/StockPage.tsx`
- Modify tests: `stock/filtros.test.ts`, `stock/columnas.test.tsx`, `stock/StockPage.test.tsx`, `stock/graficos.test.ts` si hace falta

**Interfaces:**
- Produces:
  - `type FilaStock = Componente & { miembros: Componente[] }`: `miembros[0]` es el propio componente (el master o uno suelto) y después sus slaves en el orden de la lista; un componente sin grupo tiene `miembros = [c]`.
  - `agruparCompartidos(lista: Componente[]): FilaStock[]`: conserva el orden de `lista`, quita los slaves cuyo master está en la lista y los añade a `miembros` de su master; un slave cuyo master no está en la lista queda como fila suelta (`miembros = [slave]`).
  - `nombreGrupo(f: Pick<FilaStock, 'miembros'>): string` → `miembros.map(m => m.tipo).join(' / ')`.
  - `esGrupo(f): boolean` → `miembros.length > 1`.

**Requisitos:**
1. `StockPage` agrupa una vez (`useMemo(() => agruparCompartidos(data), [data])`) y usa las filas agrupadas para la tabla, el buscador, el CSV, `conteosDonut`, el contador de desactivados y la fila seleccionada (`seleccionado`), en lugar de `data`.
2. Columna Componente: primera línea `nombreGrupo(fila)`; si `esGrupo`, segunda línea `stock compartido` en gris y más pequeño (mismo estilo que los textos de ayuda: `text-[11px] text-azul-gris`), legible también en la fila seleccionada (`CREMA_EN_FILA_SELECCIONADA`). El nombre puede saltar de línea si no cabe (la celda no debe cortar ni desbordar). Desaparece el sufijo `  (compartido)`: `nombreComponente` se sustituye o se adapta (borrar lo que quede sin uso).
3. Buscador (`aplicarFiltrosStock`): coincide si el texto está en el `tipo` de **cualquier** miembro (sin mayúsculas, "contiene", como hoy). El filtro por estado usa la fila (el master).
4. CSV: misma forma de hoy (con o sin las columnas de previsión), una fila por grupo, columna "Tipo" = `nombreGrupo`.
5. Llegada desde Pedidos (`?componente=<id>`): si el id es de un slave cuyo master está en la lista, se selecciona y se desplaza a la fila del master (la comprobación contra la lista filtrada sigue igual, ahora sobre las filas agrupadas).
6. Los handlers del menú y los diálogos siguen recibiendo el componente de la fila (el master), salvo Solicitar pieza (Task 15).

**Tests (TDD):**
- `grupos.test.ts`: master + 2 slaves → una fila con `miembros` [master, s1, s2] y `nombreGrupo` "a / b / c"; componente suelto → `miembros` [c]; slave huérfano → fila suelta; el orden de la lista se conserva; una lista sin grupos sale igual.
- `filtros.test.ts`: el buscador encuentra el grupo por el nombre del slave y por el del master; no encuentra por un texto que no está en ninguno.
- `columnas.test.tsx`: fila de grupo → nombre `bat-x / bat-y` y `stock compartido`; fila suelta → sin `stock compartido`; CSV de grupo con "Tipo" = nombre del grupo.
- `StockPage.test.tsx`: con un master y su slave en los datos, la tabla tiene una sola fila para los dos (con `stock compartido`), el buscador con el nombre del slave la muestra, el donut cuenta el grupo una vez, y `?componente=<id del slave>` selecciona la fila del grupo. Ajustar los tests existentes que esperaban `(compartido)` o una fila por slave.

**Commit:** `feat: stock muestra una fila por grupo de stock compartido con el nombre de todos`

### Task 15: "Solicitar pieza" elige el modelo en una fila de grupo

**Files:**
- Modify: `stock/SolicitarPiezaDialog.tsx`, `stock/StockPage.tsx` (cuerpo de la solicitud), `stock/dialogos.ts` si el subtítulo necesita el nombre del grupo
- Test: `stock/SolicitarPiezaDialog.test.tsx` (crear si no existe), `stock/StockPage.test.tsx`

**Interfaces:**
- Consumes: `FilaStock`, `esGrupo`, `nombreGrupo` (Task 14).
- Produces: `SolicitarPiezaDialog` con `componente: FilaStock | null` y `onConfirmar: (idCom: number, descripcion: string | null) => void`.

**Requisitos:**
1. Fila sin grupo: igual que hoy (título, subtítulo, textarea, botón "Solicitar"); `onConfirmar(c.idCom, descripcion)`.
2. Fila de grupo: subtítulo con el nombre del grupo (`Componente: cami13 / cami13pro   ·   Stock actual: 4 ud(s).`) y, antes de la descripción, un campo **`Modelo`** con una opción por miembro (su `tipo`), **sin ninguna elegida** al abrir. Mientras no se elija, "Solicitar" queda desactivado (`accionDeshabilitada` de `DialogoAlmacen`) y, si aun así se confirma (Enter), sale el error `Elige el modelo.` dentro del diálogo y no se envía. Al elegir, se envía con el `idCom` del miembro elegido. Usar un componente que ya exista en `src/shared/ui` (por ejemplo `ComboNavy` o `SelectorLista`, el que encaje con el estilo de los diálogos de almacén); no añadir dependencias.
3. Al reabrir el diálogo, la elección y la descripción empiezan vacías.
4. `StockPage` construye el cuerpo con el `idCom` que devuelve el diálogo (la clave de idempotencia sigue calculándose sobre el cuerpo).

**Tests (TDD):** fila suelta envía su idCom; fila de grupo: "Solicitar" desactivado hasta elegir, Enter sin elegir muestra `Elige el modelo.` y no envía, elegir el slave envía el idCom del slave; al reabrir no queda elegido nada. En `StockPage.test.tsx`, la solicitud desde la fila del grupo llega al servidor con el idCom elegido.

**Commit:** `feat: solicitar pieza en un grupo compartido pide elegir el modelo`

### Task 16: Nuevo pedido con una opción por grupo

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`, `pedidos/formulario/lineas.ts`
- Test: `pedidos/formulario/lineas.test.ts`, `pedidos/formulario/NuevoPedidoDialog.test.tsx`

**Interfaces:**
- Consumes: `agruparCompartidos`, `nombreGrupo` (Task 14, importados de `../../stock/grupos`).

**Requisitos:**
1. Opciones del selector de SKU: una por fila de `agruparCompartidos(activos)`, con `etiqueta = nombreGrupo(fila)` y `clave = String(fila.idCom)` (el master).
2. `precargarComponentes` y `precargarSolicitudes` llevan cada id de slave a su master (el master del slave según la lista de componentes recibida) antes de comprobar si está activo; las solicitudes de varios miembros de un mismo grupo se juntan en una sola línea con la suma, en el orden de la primera aparición. Un id de slave cuyo master no está activo cuenta como omitido, como hoy.
3. El resto del diálogo no cambia.

**Tests (TDD):** `lineas.test.ts`: slave → línea con el id del master; solicitudes de master y slave del mismo grupo → una línea con cantidad 2; un id suelto sigue igual. `NuevoPedidoDialog.test.tsx`: el selector muestra `bat-x / bat-y` una vez y no muestra `bat-y` como opción propia. (Los datos de prueba de `datosPrueba.ts` ya tienen un slave `bat-y` del master `bat-x`.)

**Commit:** `feat: nuevo pedido ofrece una opcion por grupo de stock compartido`

### Task 17: Cierre de la ampliación (versión, docs, revisión, merge, preprod)

1. En `CHANGELOG.md` de la web, en la entrada `## [0.9.5]`, una línea: "**Stock compartido en una sola fila**: los SKU que comparten stock (p. ej. `cami13 / cami13pro`) salen en una fila con «stock compartido», el buscador los encuentra por cualquier nombre, «Solicitar pieza» pide elegir el modelo y «Nuevo pedido» los ofrece una vez." Y en `docs/novedades/NOVEDADES-v0.9.5.md` (raíz, sin commit) un punto equivalente en lenguaje de usuario.
2. `npm run check` y `npm run build`; revisión de la rama `feature/grupos-compartidos` (`superpowers:requesting-code-review`).
3. Merge `--no-ff` a `main` y push, con OK uno a uno.
4. Preprod (usuario, `ssh preprod`): `git -C gestion-reparaciones-web pull --ff-only` y `docker compose up -d --build nginx`. Comprobación: `cami13 / cami13pro` y `bati12 / bati12pro` en una fila, buscador, CSV, Solicitar pieza con elección y Nuevo pedido.
