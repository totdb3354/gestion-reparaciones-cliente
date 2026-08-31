# Entrega del teléfono al técnico de glass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El dueño de una reparación normal marca "Entregar a <técnico de glass>" en Mis pendientes; se sella hora y quién en la asignación de glass abierta del mismo IMEI, y ambos extremos (y Asignaciones) lo ven como badge "Entregado hh:mm" / "Llegó hh:mm".

**Architecture:** Dos columnas nulas en `Reparacion` (`ENTREGADO_AT`, `ENTREGADO_POR`) que solo se rellenan en filas `AG`. Un endpoint `PATCH …/entrega-glass` calcado de por-cerrar (validación de propiedad, log). Las queries de asignaciones devuelven campos aditivos: los reales en filas `AG` y, en filas `A`, derivados por subconsulta sobre las `AG` abiertas del IMEI (nunca se desincronizan). En el cliente toda la lógica de textos/visibilidad vive en la clase nueva `EntregaGlass` (pura, testeada) y los controladores solo enganchan.

**Tech Stack:** Servidor Spring Boot 3.3.4 / JdbcTemplate SQL a mano / Mockito (sin BD en tests). Cliente JavaFX 21 / Gson / JUnit 5.10. MariaDB en preprod (`api.fonestore.es`).

Spec: `docs/superpowers/specs/2026-08-28-entrega-glass-design.md`.

## Global Constraints

- **Ramas**: servidor `feature/entrega-glass` desde `main` del submódulo `gestion-reparaciones-servidor` (`6b30768`, lo desplegado). Cliente `feature/entrega-glass` desde `hotfix/0.16.1` en el repo raíz. **No tocar** `feature/auto-revision` (WIP 0.17) ni `main` del raíz.
- **Nunca `git add -A` / `git commit -a`** en el repo raíz: el submódulo aparece como `M gestion-reparaciones-servidor` y NO debe commitearse en tareas de feature (solo en el commit de release, fuera de este plan). Añadir ficheros por nombre.
- **Commits sin `Co-Authored-By`**. Mensajes en español con prefijo `feat(servidor):`, `feat(cliente):`, `test(...)`, `docs(...)`.
- **Maven en Bash** necesita el prefijo (toolchain portable): `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`. Servidor: `mvn -q -f gestion-reparaciones-servidor/pom.xml test`. Cliente: `mvn -q -f gestion-reparaciones-cliente/pom.xml test`. Silencio = BUILD SUCCESS.
- **Migración BD YA APLICADA en preprod (2026-08-28)**: no volver a ejecutarla. Solo se versiona el script.
- **Endpoint**: `PATCH /api/reparaciones/asignaciones/{idRep}/entrega-glass`, body `{"entregado": true|false}`, 204. Errores: 422 no es `A…` normal · 404 no existe/cerrada · 403 no es tuya · 422 "Sin glass abierta para este IMEI".
- **Log**: acciones `ENTREGAR_GLASS` / `DESHACER_ENTREGA_GLASS`, detalle `ID_REP: A…, IMEI: …, GLASS: AG…, TECNICO_GLASS: Jhona`.
- **Textos exactos**: menú `Entregar a <nombre>` / `Deshacer entrega`; badges `Entregado HH:mm` (fila `A`) y `Llegó HH:mm` (fila `AG`); si no es hoy `Entregado dd/MM HH:mm`; tooltips `Entregado a <glass> por <quien>, dd/MM HH:mm` y `Bajado por <quien>, dd/MM HH:mm`. Horas en zona Madrid (el servidor manda UTC; usar `FechaUtils`).
- **Colores badge**: fondo `#E8EAF6`, texto `#3949AB`, mismo `base` de pastilla que Urgente/Por cerrar.
- **JSON aditivo**: cliente 0.16.0 contra servidor nuevo debe seguir igual; cliente nuevo contra servidor viejo → campos nulos → sin acción ni badges.

---

## Servidor

### Task 1: Rama del servidor + migración SQL + `crear_bd.sql` + MER

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-entrega-glass.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (tabla `Reparacion`, líneas ~172-178)
- Modify (fuera del repo): `C:\Users\dev\Documents\Apuntes\Tabla BBDD(Corregido).drawio`

**Interfaces:**
- Produces: columnas `Reparacion.ENTREGADO_AT DATETIME NULL`, `Reparacion.ENTREGADO_POR INT NULL` (FK `Tecnico.ID_TEC`) que usan las Tasks 2-4.

- [ ] **Step 1: Poner el submódulo en `main` y crear la rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git status --short            # debe estar limpio (la auto-revision ya esta commiteada en su rama)
git checkout main
git fetch origin && git status -sb | head -1     # esperado: ## main...origin/main (sin ahead/behind)
git log --oneline -1          # esperado: 6b30768 merge: F2c ciclo completo ...
git checkout -b feature/entrega-glass
```

- [ ] **Step 2: Escribir el script de migración**

Crear `gestion-reparaciones-servidor/sql/migracion-entrega-glass.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-entrega-glass.sql — entrega del teléfono al técnico de glass
-- Sella en la asignación de glass (filas AG) cuándo y quién bajó el teléfono.
-- APLICADA en preproducción el 2026-08-28 (vista previa 0 → ALTER OK, 13.564
-- filas → comprobación 2). NO idempotente (relanzar = error de columna existente).
-- 1) Vista previa: el SELECT debe devolver 0 (las columnas no existen aún).
-- 2) Ejecutar el ALTER.
-- 3) Comprobación: el SELECT final debe devolver 2.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- 1) Vista previa (no modifica nada) ──────────────────────────────────────────
SELECT COUNT(*) AS EXISTEN_COLUMNAS
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME IN ('ENTREGADO_AT', 'ENTREGADO_POR');

-- 2) Migración ────────────────────────────────────────────────────────────────
ALTER TABLE Reparacion
    ADD COLUMN ENTREGADO_AT  DATETIME NULL AFTER POR_CERRAR,
    ADD COLUMN ENTREGADO_POR INT      NULL AFTER ENTREGADO_AT,
    ADD CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico (ID_TEC);

-- 3) Comprobación ─────────────────────────────────────────────────────────────
SELECT COUNT(*) AS EXISTEN_COLUMNAS
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME IN ('ENTREGADO_AT', 'ENTREGADO_POR');
```

- [ ] **Step 3: Sincronizar `crear_bd.sql`**

En `gestion-reparaciones-servidor/sql/crear_bd.sql`, tabla `Reparacion`, cambiar:

```sql
    POR_CERRAR           BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
```
por
```sql
    POR_CERRAR           BOOLEAN      NOT NULL DEFAULT FALSE,
    ENTREGADO_AT         DATETIME     NULL,
    ENTREGADO_POR        INT          NULL,
    UPDATED_AT           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
```
y la última constraint de la tabla:
```sql
    CONSTRAINT fk_rep_tec_asigna FOREIGN KEY (ID_TEC_ASIGNA)   REFERENCES Tecnico   (ID_TEC)
);
```
por
```sql
    CONSTRAINT fk_rep_tec_asigna FOREIGN KEY (ID_TEC_ASIGNA)   REFERENCES Tecnico   (ID_TEC),
    CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico  (ID_TEC)
);
```
(Solo dentro de `CREATE TABLE Reparacion`; `UPDATED_AT … ON UPDATE` aparece también en otras tablas — no tocarlas.)

Verificar: `grep -n 'ENTREGADO' gestion-reparaciones-servidor/sql/crear_bd.sql` → 3 líneas (dos columnas + FK).

- [ ] **Step 4: Actualizar el MER (drawio, XML plano)**

```bash
f="/c/Users/dev/Documents/Apuntes/Tabla BBDD(Corregido).drawio"
grep -c 'POR_CERRAR: BOOLEAN (not null default 0)&lt;/div&gt;' "$f"    # esperado: 1
perl -pi -e 's/(&lt;div&gt;POR_CERRAR: BOOLEAN \(not null default 0\)&lt;\/div&gt;)/$1&lt;div&gt;ENTREGADO_AT: DATETIME (nullable, solo filas AG)&lt;\/div&gt;&lt;div&gt;ENTREGADO_POR: INT (FK Tecnico, nullable)&lt;\/div&gt;/' "$f"
grep -c 'ENTREGADO_POR: INT (FK Tecnico, nullable)' "$f"                 # esperado: 1
```

- [ ] **Step 5: Commit (solo servidor; el MER vive fuera del repo)**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git add sql/migracion-entrega-glass.sql sql/crear_bd.sql
git commit -m "feat(sql): columnas ENTREGADO_AT/ENTREGADO_POR en Reparacion (entrega a glass) — migracion aplicada en preprod 2026-08-28 + crear_bd en sync"
```

---

### Task 2: Modelo + lectura (SELECTs, mapper, GROUP BY) con test de columnas

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (constantes `ASIGNACION_SELECT` ~L60-98, `GLASS_ASIGNACION_SELECT` ~L880-900, `RESUMEN_MAPPER` ~L100-142, GROUP BY de glass ~L946-975 y `getAsignacionGlassById` ~L1136)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java`

**Interfaces:**
- Produces en `ReparacionResumen` (servidor): `LocalDateTime getEntregadoAt()`, `String getEntregadoPorNombre()`, `boolean isGlassAbierta()`, `LocalDateTime getGlassEntregadoAt()`, `String getGlassEntregadoPorNombre()`, `String getGlassTecnicoNombre()` (+ setters). Nombres JSON idénticos (`entregadoAt`, `entregadoPorNombre`, `glassAbierta`, `glassEntregadoAt`, `glassEntregadoPorNombre`, `glassTecnicoNombre`) — el cliente (Task 6) los replica.

- [ ] **Step 1: Escribir el test que falla (columnas presentes en las queries)**

Crear `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReparacionDAOEntregaGlassTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void asignacionesNormalesDevuelvenDerivadosDeLaGlassAbierta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignaciones(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        String q = sql.getValue();
        assertTrue(q.contains("AS GLASS_ABIERTAS"), "GLASS_ABIERTAS");
        assertTrue(q.contains("AS GLASS_ENTREGADO_AT"), "GLASS_ENTREGADO_AT");
        assertTrue(q.contains("AS GLASS_ENTREGADO_POR_NOMBRE"), "GLASS_ENTREGADO_POR_NOMBRE");
        assertTrue(q.contains("AS GLASS_TECNICO_NOMBRE"), "GLASS_TECNICO_NOMBRE");
        // las subconsultas miran solo AG abiertas del mismo IMEI
        assertTrue(q.contains("g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL"));
    }

    @SuppressWarnings("unchecked")
    @Test void asignacionesGlassDevuelvenEntregaYAgrupanPorLasColumnasNuevas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignacionesGlass(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        String q = sql.getValue();
        assertTrue(q.contains(" r.ENTREGADO_AT,"), "columna ENTREGADO_AT");
        assertTrue(q.contains("AS ENTREGADO_POR_NOMBRE"), "ENTREGADO_POR_NOMBRE");
        assertTrue(q.contains("r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE"), "GROUP BY con columnas nuevas");
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionDAOEntregaGlassTest`
Expected: FAIL — `GLASS_ABIERTAS ==> expected: <true> but was: <false>`.

- [ ] **Step 3: Campos nuevos en `ReparacionResumen` (servidor)**

Tras `private String        cliente;` añadir:

```java
    // Entrega a glass (spec 2026-08-28): reales en filas AG, derivados en filas A.
    private LocalDateTime entregadoAt;              // AG: cuándo bajó el teléfono
    private String        entregadoPorNombre;       // AG: quién lo bajó
    private boolean       glassAbierta;             // A: hay AG abierta en el IMEI
    private LocalDateTime glassEntregadoAt;         // A: entrega sellada en esa AG
    private String        glassEntregadoPorNombre;  // A: quién la selló
    private String        glassTecnicoNombre;       // A: dueño actual de esa AG
```

Y tras `public void          setCliente(String cliente)           { this.cliente = cliente; }` añadir:

```java
    public LocalDateTime getEntregadoAt()                          { return entregadoAt; }
    public void          setEntregadoAt(LocalDateTime v)           { this.entregadoAt = v; }
    public String        getEntregadoPorNombre()                   { return entregadoPorNombre; }
    public void          setEntregadoPorNombre(String v)           { this.entregadoPorNombre = v; }
    public boolean       isGlassAbierta()                          { return glassAbierta; }
    public void          setGlassAbierta(boolean v)                { this.glassAbierta = v; }
    public LocalDateTime getGlassEntregadoAt()                     { return glassEntregadoAt; }
    public void          setGlassEntregadoAt(LocalDateTime v)      { this.glassEntregadoAt = v; }
    public String        getGlassEntregadoPorNombre()              { return glassEntregadoPorNombre; }
    public void          setGlassEntregadoPorNombre(String v)      { this.glassEntregadoPorNombre = v; }
    public String        getGlassTecnicoNombre()                   { return glassTecnicoNombre; }
    public void          setGlassTecnicoNombre(String v)           { this.glassTecnicoNombre = v; }
```

- [ ] **Step 4: Columnas derivadas en `ASIGNACION_SELECT` (filas A)**

En `ReparacionDAO.java`, dentro de la constante `ASIGNACION_SELECT` (la que termina en `WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.ID_REP NOT LIKE 'AG%' AND r.FECHA_FIN IS NULL`), cambiar las dos líneas:

```java
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
por
```java
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR," +
            // Entrega a glass, derivada de la AG abierta más antigua del mismo IMEI (spec 2026-08-28 §4)
            " (SELECT COUNT(*) FROM Reparacion g" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL) AS GLASS_ABIERTAS," +
            " (SELECT g.ENTREGADO_AT FROM Reparacion g" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL" +
            "  ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_ENTREGADO_AT," +
            " (SELECT tg.NOMBRE FROM Reparacion g LEFT JOIN Tecnico tg ON g.ENTREGADO_POR = tg.ID_TEC" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL" +
            "  ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_ENTREGADO_POR_NOMBRE," +
            " (SELECT tg.NOMBRE FROM Reparacion g JOIN Tecnico tg ON g.ID_TEC = tg.ID_TEC" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL" +
            "  ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_TECNICO_NOMBRE," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
(La misma pareja de líneas existe en `HISTORIAL_SELECT` con `TIENE_ASIGNACIONES` detrás y sin `r.POR_CERRAR` — no es esa. Solo `ASIGNACION_SELECT` lleva `r.POR_CERRAR,` seguido de `ta.NOMBRE AS NOMBRE_TEC_ASIGNA`.)

- [ ] **Step 5: Columnas reales en `GLASS_ASIGNACION_SELECT` (filas AG)**

Dentro de la constante `GLASS_ASIGNACION_SELECT` (termina en `WHERE r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL`), cambiar:

```java
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE, r.ES_CHASIS," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
por
```java
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE, r.ES_CHASIS," +
            " r.ENTREGADO_AT," +
            " (SELECT te.NOMBRE FROM Tecnico te WHERE te.ID_TEC = r.ENTREGADO_POR) AS ENTREGADO_POR_NOMBRE," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
Ojo: esa pareja de líneas puede existir también en `ASIGNACION_PULIDO_SELECT`; comprobar con `grep -n 'r.URGENTE, r.ES_CHASIS," +' ReparacionDAO.java` y editar solo la ocurrencia que está entre `GLASS_ASIGNACION_SELECT =` y `GLASS_HISTORIAL_SELECT =`.

- [ ] **Step 6: GROUP BY de las tres queries de glass**

Las cuatro consumidoras de `GLASS_ASIGNACION_SELECT` (`getAsignacionesGlass`, `getAsignacionesGlassPorImei`, `getAsignacionGlassById` y el lado glass de `getAsignacionesCompletadasHoy`) comparten el fragmento `r.ES_CHASIS, ta.NOMBRE, cli.NOMBRE`:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
grep -c 'r\.ES_CHASIS, ta\.NOMBRE, cli\.NOMBRE' src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java   # esperado: 4
sed -i 's/r\.ES_CHASIS, ta\.NOMBRE, cli\.NOMBRE/r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE, cli.NOMBRE/g' src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
grep -c 'r\.ENTREGADO_AT, r\.ENTREGADO_POR, ta\.NOMBRE' src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java   # esperado: 4
```
Si el primer `grep -c` no da 4, parar y mirar cada ocurrencia: solo deben cambiar las que acompañan a `GLASS_ASIGNACION_SELECT`. Las queries de filas `A` no necesitan cambios en el GROUP BY (las subconsultas nuevas solo correlacionan `r.IMEI`, que ya está agrupado).

- [ ] **Step 7: Mapper**

En `RESUMEN_MAPPER`, tras `try { rr.setNombreTecnicoAsigna(rs.getString("NOMBRE_TEC_ASIGNA")); } catch (Exception ignored) {}` añadir:

```java
        // Entrega a glass: AG lleva las reales, A las derivadas; el resto de queries no las traen.
        try { Timestamp e = rs.getTimestamp("ENTREGADO_AT"); rr.setEntregadoAt(e != null ? e.toLocalDateTime() : null); } catch (Exception ignored) {}
        try { rr.setEntregadoPorNombre(rs.getString("ENTREGADO_POR_NOMBRE")); } catch (Exception ignored) {}
        try { rr.setGlassAbierta(rs.getInt("GLASS_ABIERTAS") > 0); } catch (Exception ignored) {}
        try { Timestamp ge = rs.getTimestamp("GLASS_ENTREGADO_AT"); rr.setGlassEntregadoAt(ge != null ? ge.toLocalDateTime() : null); } catch (Exception ignored) {}
        try { rr.setGlassEntregadoPorNombre(rs.getString("GLASS_ENTREGADO_POR_NOMBRE")); } catch (Exception ignored) {}
        try { rr.setGlassTecnicoNombre(rs.getString("GLASS_TECNICO_NOMBRE")); } catch (Exception ignored) {}
```
(`Timestamp` ya está importado: se usa en `Timestamp fin = rs.getTimestamp("FECHA_FIN")`.)

- [ ] **Step 8: Ejecutar el test para verificar que pasa**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionDAOEntregaGlassTest` (con el prefijo de JAVA_HOME/PATH)
Expected: sin salida (PASS).

- [ ] **Step 9: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git add src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java
git commit -m "feat(servidor): entrega a glass en las queries de asignaciones — reales en AG (entregadoAt, entregadoPorNombre) y derivadas en A (glassAbierta, glassEntregadoAt, glassEntregadoPorNombre, glassTecnicoNombre)"
```

---

### Task 3: DAO de escritura — `getGlassAbiertas`, `entregarGlass`, `deshacerEntregaGlass`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (junto a `actualizarPorCerrar`, ~L636)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java` (ampliar)

**Interfaces:**
- Produces: `public record GlassAbierta(String idRep, String nombreTecnico)`; `List<GlassAbierta> getGlassAbiertas(String imei)`; `void entregarGlass(String imei, int idTecEntrega)`; `void deshacerEntregaGlass(String imei)`. Los usa la Task 4.

- [ ] **Step 1: Ampliar el test (falla)**

Añadir a `ReparacionDAOEntregaGlassTest`:

```java
    @Test void entregarSellaTodasLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).entregarGlass(IMEI, 7);
        verify(jdbc).update(
                "UPDATE Reparacion SET ENTREGADO_AT = NOW(), ENTREGADO_POR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                7, IMEI);
    }

    @Test void deshacerLimpiaTodasLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).deshacerEntregaGlass(IMEI);
        verify(jdbc).update(
                "UPDATE Reparacion SET ENTREGADO_AT = NULL, ENTREGADO_POR = NULL, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                IMEI);
    }

    @SuppressWarnings("unchecked")
    @Test void glassAbiertasConsultaSoloAgAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getGlassAbiertas(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), org.mockito.ArgumentMatchers.eq(IMEI));
        assertTrue(sql.getValue().contains("WHERE r.IMEI = ? AND r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL"));
    }
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionDAOEntregaGlassTest`
Expected: error de compilación `cannot find symbol: method entregarGlass(...)`.

- [ ] **Step 3: Implementar los tres métodos**

En `ReparacionDAO.java`, justo después de `actualizarPorCerrar(...)`:

```java
    /** Asignación de glass abierta de un IMEI (id + dueño actual), la más antigua primero. */
    public record GlassAbierta(String idRep, String nombreTecnico) {}

    /** AG abiertas del IMEI: destinatarias de la entrega (spec entrega-glass §2). */
    public List<GlassAbierta> getGlassAbiertas(String imei) {
        return jdbc.query(
                "SELECT r.ID_REP, t.NOMBRE FROM Reparacion r JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL ORDER BY r.FECHA_ASIG ASC",
                (rs, i) -> new GlassAbierta(rs.getString("ID_REP"), rs.getString("NOMBRE")),
                imei);
    }

    /** Sella la entrega (ahora, por idTecEntrega) en TODAS las AG abiertas del IMEI. Volver a entregar sobrescribe. */
    public void entregarGlass(String imei, int idTecEntrega) {
        jdbc.update(
                "UPDATE Reparacion SET ENTREGADO_AT = NOW(), ENTREGADO_POR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                idTecEntrega, imei);
    }

    /** Deshace la entrega en TODAS las AG abiertas del IMEI. */
    public void deshacerEntregaGlass(String imei) {
        jdbc.update(
                "UPDATE Reparacion SET ENTREGADO_AT = NULL, ENTREGADO_POR = NULL, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                imei);
    }
```

- [ ] **Step 4: Ejecutar el test para verificar que pasa**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionDAOEntregaGlassTest`
Expected: sin salida (PASS, 5 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java
git commit -m "feat(servidor): DAO de entrega a glass — getGlassAbiertas, entregarGlass y deshacerEntregaGlass sobre todas las AG abiertas del IMEI"
```

---

### Task 4: Endpoint `PATCH /asignaciones/{idRep}/entrega-glass` con test de controlador

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (tras `actualizarPorCerrar`, ~L275; records al final, ~L453)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java`

**Interfaces:**
- Consumes: `dao.getAsignacionAnyById(String)`, `dao.getGlassAbiertas(String)`, `dao.entregarGlass(String,int)`, `dao.deshacerEntregaGlass(String)`, `logDao.insertar(int,String,String)`.
- Produces: el endpoint que consume `ReparacionDAO.actualizarEntregaGlass` del cliente (Task 6).

- [ ] **Step 1: Escribir el test que falla**

Crear `src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReparacionControllerEntregaGlassTest {

    private static final String IMEI = "351111112222333";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class));
    private final UsuarioPrincipal manu = new UsuarioPrincipal(42, "manu", "x", "TECNICO", 7);

    private ReparacionResumen asigDe(int idTec) {
        ReparacionResumen a = mock(ReparacionResumen.class);
        when(a.getIdTec()).thenReturn(idTec);
        when(a.getImei()).thenReturn(IMEI);
        return a;
    }

    /** Invoca y devuelve el status del ResponseStatusException lanzado. */
    private int statusDe(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        return ex.getStatusCode().value();
    }

    @Test void rechazaIdsQueNoSonReparacionNormal() {
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AG20260828_1", req(true), manu)));
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AP20260828_1", req(true), manu)));
        verifyNoInteractions(logDao);
    }

    @Test void asignacionInexistenteOCerradaEs404() {
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), manu)));
    }

    // Ojo Mockito 5: el mock se crea ANTES del when(...) externo — anidar asigDe() dentro de
    // thenReturn(Optional.of(...)) lanza UnfinishedStubbingException (stubbing anidado).
    @Test void soloElDuenoPuedeEntregar() {
        ReparacionResumen ajena = asigDe(99);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(ajena));
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), manu)));
        UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), admin)));
        verify(dao, never()).entregarGlass(anyString(), anyInt());
    }

    @Test void sinGlassAbiertaEs422YNoEscribe() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(propia));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarEntregaGlass("A20260828_1", req(true), manu));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("Sin glass abierta para este IMEI", ex.getReason());
        verify(dao, never()).entregarGlass(anyString(), anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void entregarSellaYRegistraEnElLog() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(propia));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Jhona")));
        ctl.actualizarEntregaGlass("A20260828_1", req(true), manu);
        verify(dao).entregarGlass(IMEI, 7);
        verify(dao, never()).deshacerEntregaGlass(anyString());
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Jhona");
    }

    @Test void deshacerLimpiaYRegistraEnElLog() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(propia));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Jhona")));
        ctl.actualizarEntregaGlass("A20260828_1", req(false), manu);
        verify(dao).deshacerEntregaGlass(IMEI);
        verify(dao, never()).entregarGlass(anyString(), anyInt());
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Jhona");
    }

    private static ReparacionController.EntregaGlassRequest req(boolean entregado) {
        return new ReparacionController.EntregaGlassRequest(entregado);
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionControllerEntregaGlassTest`
Expected: error de compilación `cannot find symbol: class EntregaGlassRequest`.

- [ ] **Step 3: Implementar el endpoint**

En `ReparacionController.java`, justo después del método `actualizarPorCerrar` (antes de `@PreAuthorize("hasRole('SUPERTECNICO')") @PatchMapping("/asignaciones/{idRep}")`):

```java
    /**
     * Entrega del teléfono a la glass abierta del IMEI (spec entrega-glass 2026-08-28 §4).
     * Solo el dueño de la reparación normal; sella (o limpia) ENTREGADO_AT/ENTREGADO_POR en
     * TODAS las AG abiertas del IMEI. Sin lock optimista (toggle, último gana).
     */
    @PatchMapping("/asignaciones/{idRep}/entrega-glass")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarEntregaGlass(@PathVariable String idRep, @RequestBody EntregaGlassRequest req,
                                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        boolean esRepNormal = idRep != null && idRep.startsWith("A")
                && !idRep.startsWith("AG") && !idRep.startsWith("AP");
        if (!esRepNormal) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Solo aplica a asignaciones de reparación");
        }
        ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
        boolean esSuya = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
        if (!esSuya) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes entregar tus propias asignaciones");
        }
        List<ReparacionDAO.GlassAbierta> glass = dao.getGlassAbiertas(asig.getImei());
        if (glass.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Sin glass abierta para este IMEI");
        }
        if (req.entregado()) dao.entregarGlass(asig.getImei(), principal.getIdTec());
        else                 dao.deshacerEntregaGlass(asig.getImei());

        String ids  = glass.stream().map(ReparacionDAO.GlassAbierta::idRep)
                .collect(java.util.stream.Collectors.joining(","));
        String tecs = glass.stream().map(ReparacionDAO.GlassAbierta::nombreTecnico).distinct()
                .collect(java.util.stream.Collectors.joining(","));
        logDao.insertar(principal.getIdUsu(),
                req.entregado() ? "ENTREGAR_GLASS" : "DESHACER_ENTREGA_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", GLASS: " + ids + ", TECNICO_GLASS: " + tecs);
    }
```

Y entre los records del final, tras `private record PorCerrarRequest(boolean porCerrar) {}`:

```java
    record EntregaGlassRequest(boolean entregado) {}   // package-private: lo construye el test
```

- [ ] **Step 4: Ejecutar el test para verificar que pasa**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionControllerEntregaGlassTest`
Expected: sin salida (PASS, 6 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git add src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java
git commit -m "feat(servidor): PATCH /asignaciones/{idRep}/entrega-glass — solo el dueno de la reparacion normal, sella todas las AG abiertas del IMEI, log ENTREGAR_GLASS/DESHACER_ENTREGA_GLASS"
```

---

### Task 5: Suite completa del servidor + entrega para merge/despliegue

**Files:** ninguno nuevo.

- [ ] **Step 1: Suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; mvn -q -f gestion-reparaciones-servidor/pom.xml test; echo "EXIT: $?"`
Expected: `EXIT: 0` (86 tests previos + 11 nuevos).

- [ ] **Step 2: Comprobar que el repo raíz no ha cambiado nada más que el gitlink**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git status --short     # esperado: " M gestion-reparaciones-servidor" + untracked de siempre; NADA más
git -C gestion-reparaciones-servidor log --oneline main..HEAD   # esperado: los 4 commits de las Tasks 1-4
```

- [ ] **Step 3: Entregar al usuario (NO ejecutar sin su OK)**

Informar: rama `feature/entrega-glass` del servidor lista con 4 commits y suite en verde. Pasos que ejecuta el usuario (o Claude con OK explícito):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git merge --no-ff feature/entrega-glass -m "merge: entrega del telefono al tecnico de glass — columnas ENTREGADO_AT/POR en AG, endpoint entrega-glass, derivados en asignaciones normales (feature/entrega-glass)"
git push origin main
```
Despliegue en la VM (lo ejecuta el usuario por SSH; Claude no hace SSH): `cd <ruta del clon en la VM> && git pull && docker compose up -d --build && docker compose logs -f --tail=50 api` — validar que Spring arranca ("Started … in N seconds") y que `GET /api/reparaciones/asignaciones` responde. Después, poner al día la rama WIP: `git checkout feature/auto-revision && git merge main` (sin conflicto esperado).

---

## Cliente

### Task 6: Rama del cliente + modelo + DAO + mensaje real en 422

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ReparacionResumen.java` (campos ~L48, getters ~L193)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java` (tras `actualizarPorCerrar`, ~L442)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java` (`clasificar`, ~L329)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ApiClientClasificarTest.java`

**Interfaces:**
- Produces en `ReparacionResumen` (cliente): `getEntregadoAt()`, `getEntregadoPorNombre()`, `isGlassAbierta()`, `getGlassEntregadoAt()`, `getGlassEntregadoPorNombre()`, `getGlassTecnicoNombre()` + setters (mismos nombres JSON que el servidor). `ReparacionDAO.actualizarEntregaGlass(String idRep, boolean entregado) throws SQLException`.

- [ ] **Step 1: Crear la rama desde la hotfix**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git branch --show-current            # esperado: hotfix/0.16.1
git checkout -b feature/entrega-glass
```

- [ ] **Step 2: Test que falla — el 422 conserva el mensaje del servidor**

Añadir a `ApiClientClasificarTest`:

```java
    @Test
    void status_422_conserva_el_mensaje_del_servidor() {
        SQLException e = ApiClient.clasificar(422, "Sin glass abierta para este IMEI");
        assertEquals("Sin glass abierta para este IMEI", e.getMessage());
    }
```

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=ApiClientClasificarTest`
Expected: FAIL — `expected: <Sin glass abierta para este IMEI> but was: <Contraseña actual incorrecta.>`.

- [ ] **Step 3: Arreglar `clasificar`**

En `ApiClient.clasificar`, cambiar `case 422 -> new SQLException("Contraseña actual incorrecta.");` por:

```java
            // 422 = regla de negocio del servidor (por-cerrar, entrega-glass, contraseña…): su mensaje es el bueno.
            // El texto fijo antiguo ("Contraseña actual incorrecta.") ocultaba todos los demás; el único 422 sin
            // cuerpo (cambiar contraseña en servidores viejos) lo resuelve UsuarioDAO.cambiarPassword.
            case 422 -> new SQLException(msg);
```
(`msg` viene de `extractMessage`, que ya devuelve "Sin detalles." si el cuerpo está vacío; el servidor envía `message` porque tiene `server.error.include-message=always`.)

Nota (review final): `AuthController` sí devolvía un 422 sin cuerpo para la contraseña; corregido en servidor (body con `message`) y con fallback en `UsuarioDAO.cambiarPassword`.

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=ApiClientClasificarTest` → sin salida (PASS).

- [ ] **Step 4: Campos en `ReparacionResumen` (cliente)**

Tras `private boolean       porCerrar;` añadir:

```java
    // Entrega a glass (spec 2026-08-28): reales en filas AG, derivadas en filas A. Nulas con servidor viejo.
    private LocalDateTime entregadoAt;
    private String        entregadoPorNombre;
    private boolean       glassAbierta;
    private LocalDateTime glassEntregadoAt;
    private String        glassEntregadoPorNombre;
    private String        glassTecnicoNombre;
```

Tras `public void    setPorCerrar(boolean porCerrar)                   { this.porCerrar = porCerrar; }` añadir:

```java
    public LocalDateTime getEntregadoAt()                            { return entregadoAt; }
    public void          setEntregadoAt(LocalDateTime v)             { this.entregadoAt = v; }
    public String        getEntregadoPorNombre()                     { return entregadoPorNombre; }
    public void          setEntregadoPorNombre(String v)             { this.entregadoPorNombre = v; }
    public boolean       isGlassAbierta()                            { return glassAbierta; }
    public void          setGlassAbierta(boolean v)                  { this.glassAbierta = v; }
    public LocalDateTime getGlassEntregadoAt()                       { return glassEntregadoAt; }
    public void          setGlassEntregadoAt(LocalDateTime v)        { this.glassEntregadoAt = v; }
    public String        getGlassEntregadoPorNombre()                { return glassEntregadoPorNombre; }
    public void          setGlassEntregadoPorNombre(String v)        { this.glassEntregadoPorNombre = v; }
    public String        getGlassTecnicoNombre()                     { return glassTecnicoNombre; }
    public void          setGlassTecnicoNombre(String v)             { this.glassTecnicoNombre = v; }
```

- [ ] **Step 5: Método en `ReparacionDAO` (cliente)**

Tras `actualizarPorCerrar(...)`:

```java
    /**
     * Entrega (o deshace la entrega) del teléfono a la glass abierta del mismo IMEI.
     * Solo el dueño de la reparación normal; el servidor valida (403/422 con mensaje).
     */
    public void actualizarEntregaGlass(String idRep, boolean entregado) throws SQLException {
        ApiClient.patch("/api/reparaciones/asignaciones/" + idRep + "/entrega-glass",
                Map.of("entregado", entregado));
    }
```

- [ ] **Step 6: Compilar y commit**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=ApiClientClasificarTest` → sin salida.

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ReparacionResumen.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ApiClientClasificarTest.java
git commit -m "feat(cliente): campos de entrega a glass en ReparacionResumen, PATCH entrega-glass en ReparacionDAO y 422 con el mensaje real del servidor"
```

---

### Task 7: `EntregaGlass` — lógica pura de textos, tooltips, opción de menú y CSV (TDD)

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/EntregaGlass.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/EntregaGlassTest.java`

**Interfaces:**
- Consumes: `ReparacionResumen` (Task 6), `TipoTrabajo.desde`, `FechaUtils.formatear/toLocalDate`.
- Produces (todo `public static`): `LocalDate hoy()`; `String textoBadge(ReparacionResumen rep, LocalDate hoy)` (null = sin badge); `String tooltip(ReparacionResumen rep)`; `String opcionMenu(ReparacionResumen rep, boolean pestanaGlass)` (null = ocultar); `String textoCsv(ReparacionResumen rep, DateTimeFormatter fmt)` ("" si no hay); `String estiloColores()`.

- [ ] **Step 1: Escribir los tests (fallan)**

Crear `src/test/java/com/reparaciones/utils/EntregaGlassTest.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class EntregaGlassTest {

    /** 2026-08-28 08:42 UTC = 10:42 en Madrid (CEST). */
    private static final LocalDateTime UTC_0842 = LocalDateTime.of(2026, 8, 28, 8, 42);
    private static final LocalDate HOY = LocalDate.of(2026, 8, 28);
    private static final LocalDate MANANA = LocalDate.of(2026, 8, 29);

    private static ReparacionResumen normal(boolean glassAbierta, LocalDateTime entregadoAt) {
        ReparacionResumen r = new ReparacionResumen();
        r.setIdRep("A20260828_1");
        r.setGlassAbierta(glassAbierta);
        r.setGlassEntregadoAt(entregadoAt);
        r.setGlassEntregadoPorNombre(entregadoAt != null ? "Manu" : null);
        r.setGlassTecnicoNombre(glassAbierta ? "Jhona" : null);
        return r;
    }

    private static ReparacionResumen glass(LocalDateTime entregadoAt) {
        ReparacionResumen r = new ReparacionResumen();
        r.setIdRep("AG20260828_3");
        r.setEntregadoAt(entregadoAt);
        r.setEntregadoPorNombre(entregadoAt != null ? "Manu" : null);
        return r;
    }

    // ── Badge ──────────────────────────────────────────────────────────────

    @Test void badgeArribaHoySoloHora() {
        assertEquals("Entregado 10:42", EntregaGlass.textoBadge(normal(true, UTC_0842), HOY));
    }

    @Test void badgeArribaOtroDiaLlevaFecha() {
        assertEquals("Entregado 28/08 10:42", EntregaGlass.textoBadge(normal(true, UTC_0842), MANANA));
    }

    @Test void badgeGlassDiceLlego() {
        assertEquals("Llegó 10:42", EntregaGlass.textoBadge(glass(UTC_0842), HOY));
        assertEquals("Llegó 28/08 10:42", EntregaGlass.textoBadge(glass(UTC_0842), MANANA));
    }

    @Test void sinEntregaNoHayBadge() {
        assertNull(EntregaGlass.textoBadge(normal(true, null), HOY));
        assertNull(EntregaGlass.textoBadge(glass(null), HOY));
    }

    @Test void pulidoNuncaTieneBadge() {
        ReparacionResumen p = new ReparacionResumen();
        p.setIdRep("AP20260828_1");
        p.setEntregadoAt(UTC_0842);
        assertNull(EntregaGlass.textoBadge(p, HOY));
    }

    // ── Tooltip ────────────────────────────────────────────────────────────

    @Test void tooltipArribaDiceAQuienYPorQuien() {
        assertEquals("Entregado a Jhona por Manu, 28/08 10:42", EntregaGlass.tooltip(normal(true, UTC_0842)));
    }

    @Test void tooltipGlassDicePorQuien() {
        assertEquals("Bajado por Manu, 28/08 10:42", EntregaGlass.tooltip(glass(UTC_0842)));
    }

    @Test void tooltipSinEntregaEsNull() {
        assertNull(EntregaGlass.tooltip(normal(true, null)));
    }

    // ── Opción de menú ─────────────────────────────────────────────────────

    @Test void opcionEntregarConGlassAbiertaSinEntrega() {
        assertEquals("Entregar a Jhona", EntregaGlass.opcionMenu(normal(true, null), false));
    }

    @Test void opcionDeshacerConEntrega() {
        assertEquals("Deshacer entrega", EntregaGlass.opcionMenu(normal(true, UTC_0842), false));
    }

    @Test void opcionOcultaSinGlassAbierta() {
        assertNull(EntregaGlass.opcionMenu(normal(false, null), false));
    }

    @Test void opcionOcultaEnPestanaGlassYEnFilasGlass() {
        assertNull(EntregaGlass.opcionMenu(normal(true, null), true));
        assertNull(EntregaGlass.opcionMenu(glass(null), false));
    }

    @Test void opcionOcultaSinFila() {
        assertNull(EntregaGlass.opcionMenu(null, false));
    }

    // ── CSV ────────────────────────────────────────────────────────────────

    @Test void csvFechaCompletaOVacio() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        assertEquals("28/08/2026 10:42", EntregaGlass.textoCsv(normal(true, UTC_0842), fmt));
        assertEquals("28/08/2026 10:42", EntregaGlass.textoCsv(glass(UTC_0842), fmt));
        assertEquals("", EntregaGlass.textoCsv(normal(true, null), fmt));
    }

    @Test void estiloLlevaLaPaletaIndigo() {
        assertTrue(EntregaGlass.estiloColores().contains("#E8EAF6"));
        assertTrue(EntregaGlass.estiloColores().contains("#3949AB"));
    }
}
```

- [ ] **Step 2: Ejecutar para verificar que falla**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=EntregaGlassTest`
Expected: error de compilación `cannot find symbol: class EntregaGlass`.

- [ ] **Step 3: Implementar `EntregaGlass`**

Crear `src/main/java/com/reparaciones/utils/EntregaGlass.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Entrega del teléfono al técnico de glass (spec 2026-08-28): textos de badge, tooltip,
 * opción de menú y CSV. Pura y sin JavaFX para poder testearla; los controladores solo
 * enganchan. Las horas llegan en UTC del servidor y se muestran en hora de Madrid.
 *
 * <p>Fila {@code A…} (técnico de arriba): usa los campos derivados {@code glass*}.
 * Fila {@code AG…} (técnico de glass): usa {@code entregadoAt}/{@code entregadoPorNombre}.</p>
 */
public final class EntregaGlass {

    public static final String COLOR_FONDO = "#E8EAF6";
    public static final String COLOR_TEXTO = "#3949AB";

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter FMT_HORA     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private EntregaGlass() {}

    /** Hoy en Madrid (para decidir si el badge lleva fecha). */
    public static LocalDate hoy() { return LocalDate.now(MADRID); }

    /** Texto del badge o {@code null} si no hay entrega (o la fila es de pulido). */
    public static String textoBadge(ReparacionResumen rep, LocalDate hoy) {
        if (rep == null) return null;
        switch (TipoTrabajo.desde(rep.getIdRep())) {
            case GLASS:      return texto("Llegó", rep.getEntregadoAt(), hoy);
            case REPARACION: return texto("Entregado", rep.getGlassEntregadoAt(), hoy);
            default:         return null;
        }
    }

    /** Tooltip del badge o {@code null} si no hay entrega. */
    public static String tooltip(ReparacionResumen rep) {
        if (rep == null) return null;
        switch (TipoTrabajo.desde(rep.getIdRep())) {
            case GLASS:
                if (rep.getEntregadoAt() == null) return null;
                return "Bajado por " + nombre(rep.getEntregadoPorNombre()) + ", "
                        + FechaUtils.formatear(rep.getEntregadoAt(), FMT_DIA_HORA);
            case REPARACION:
                if (rep.getGlassEntregadoAt() == null) return null;
                return "Entregado a " + nombre(rep.getGlassTecnicoNombre()) + " por "
                        + nombre(rep.getGlassEntregadoPorNombre()) + ", "
                        + FechaUtils.formatear(rep.getGlassEntregadoAt(), FMT_DIA_HORA);
            default:
                return null;
        }
    }

    /**
     * Texto de la opción del menú contextual de Mis pendientes, o {@code null} para ocultarla.
     * Solo en la pestaña Reparación, en filas {@code A…} normales con glass abierta.
     */
    public static String opcionMenu(ReparacionResumen rep, boolean pestanaGlass) {
        if (rep == null || pestanaGlass) return null;
        if (TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.REPARACION) return null;
        if (!rep.isGlassAbierta()) return null;
        return rep.getGlassEntregadoAt() != null
                ? "Deshacer entrega"
                : "Entregar a " + nombre(rep.getGlassTecnicoNombre());
    }

    /** Columna "Entregado" del CSV de Asignaciones: fecha completa o vacío (pulido y fila nula ⇒ vacío). */
    public static String textoCsv(ReparacionResumen rep, DateTimeFormatter fmt) {
        if (rep == null) return "";
        TipoTrabajo tipo = TipoTrabajo.desde(rep.getIdRep());
        if (tipo == TipoTrabajo.PULIDO) return "";
        LocalDateTime at = tipo == TipoTrabajo.GLASS ? rep.getEntregadoAt() : rep.getGlassEntregadoAt();
        return FechaUtils.formatear(at, fmt);   // "" si null
    }

    /** Colores del badge, para concatenar al estilo base de pastilla. */
    public static String estiloColores() {
        return "-fx-background-color: " + COLOR_FONDO + "; -fx-text-fill: " + COLOR_TEXTO + ";";
    }

    private static String texto(String prefijo, LocalDateTime utc, LocalDate hoy) {
        if (utc == null) return null;
        boolean esHoy = hoy != null && hoy.equals(FechaUtils.toLocalDate(utc));
        return prefijo + " " + FechaUtils.formatear(utc, esHoy ? FMT_HORA : FMT_DIA_HORA);
    }

    private static String nombre(String n) {
        return (n == null || n.isBlank()) ? "glass" : n;
    }
}
```

- [ ] **Step 4: Ejecutar para verificar que pasa**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=EntregaGlassTest`
Expected: sin salida (PASS, 15 tests).

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/EntregaGlass.java gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/EntregaGlassTest.java
git commit -m "feat(cliente): EntregaGlass — textos de badge (Entregado/Llego, fecha si no es hoy), tooltips, opcion de menu y CSV, puros y testeados"
```

---

### Task 8: Mis pendientes — opción de menú y badge en `PendientesTecnicoController`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java` (import ~L10; menú ~L148-164; celda Estado ~L200-232)

**Interfaces:**
- Consumes: `EntregaGlass.*` (Task 7), `reparacionDAO.actualizarEntregaGlass` (Task 6), `glass` (campo booleano: true = pestaña Glass), `cargar()`, `mostrarError(Exception)`.

- [ ] **Step 1: Import**

Tras `import com.reparaciones.utils.ConfirmDialog;` añadir `import com.reparaciones.utils.EntregaGlass;`.

- [ ] **Step 2: Opción del menú contextual**

Justo después de `menu.getItems().add(togglePorCerrar);` añadir:

```java
                MenuItem toggleEntrega = new MenuItem("Entregar a glass");
                toggleEntrega.setOnAction(e -> {
                    ReparacionResumen rep = getItem();
                    if (rep == null) return;
                    try {
                        // true = entregar (aún sin entrega); false = deshacer (ya entregada)
                        reparacionDAO.actualizarEntregaGlass(rep.getIdRep(), rep.getGlassEntregadoAt() == null);
                        cargar();
                    } catch (SQLException ex) { mostrarError(ex); }
                });
                menu.getItems().add(toggleEntrega);
```

Y dentro de `menu.setOnShowing(ev -> { … })`, tras la línea `if (esRepNormal) togglePorCerrar.setText(...)`, añadir:

```java
                    String opcionEntrega = EntregaGlass.opcionMenu(rep, glass);
                    toggleEntrega.setVisible(opcionEntrega != null);
                    if (opcionEntrega != null) toggleEntrega.setText(opcionEntrega);
```

- [ ] **Step 3: Badge en la celda Estado**

En `cEstado.setCellFactory(...)`, cambiar la declaración de labels y el VBox:

```java
            private final Label badgeUrgente   = new Label();
            private final Label badgePorCerrar = new Label("Por cerrar");
            private final Label badge          = new Label();
            private final Label lblTipo        = new Label();
            private final javafx.scene.layout.VBox celdaBox =
                    new javafx.scene.layout.VBox(2, badgeUrgente, badgePorCerrar, badge, lblTipo);
```
por
```java
            private final Label badgeUrgente   = new Label();
            private final Label badgePorCerrar = new Label("Por cerrar");
            private final Label badgeEntrega   = new Label();     // "Entregado hh:mm" (A) / "Llegó hh:mm" (AG)
            private final Label badge          = new Label();
            private final Label lblTipo        = new Label();
            private final javafx.scene.layout.VBox celdaBox =
                    new javafx.scene.layout.VBox(2, badgeUrgente, badgePorCerrar, badgeEntrega, badge, lblTipo);
```

Y en `updateItem`, justo después del bloque `if (rep.isPorCerrar()) { … } else { … }`, añadir:

```java
                String textoEntrega = EntregaGlass.textoBadge(rep, EntregaGlass.hoy());
                if (textoEntrega != null) {
                    badgeEntrega.setText(textoEntrega);
                    badgeEntrega.setStyle(base + EntregaGlass.estiloColores());
                    badgeEntrega.setTooltip(new Tooltip(EntregaGlass.tooltip(rep)));
                    badgeEntrega.setVisible(true); badgeEntrega.setManaged(true);
                } else {
                    badgeEntrega.setTooltip(null);
                    badgeEntrega.setVisible(false); badgeEntrega.setManaged(false);
                }
```
(`Tooltip` viene de `javafx.scene.control.*`, ya importado.)

- [ ] **Step 4: Compilar y suite**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test; echo "EXIT: $?"`
Expected: `EXIT: 0`.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java
git commit -m "feat(cliente): Mis pendientes — 'Entregar a <glass>' / 'Deshacer entrega' en el menu contextual y badge Entregado/Llego en la columna Estado"
```

---

### Task 9: Asignaciones — badge, columna CSV y acciones de log

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (celda Estado ~L468-498)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` (cabeceras ~L1181-1183; `filaAsignacion` ~L1257)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java` (lista de acciones ~L58)

**Interfaces:**
- Consumes: `EntregaGlass.textoBadge/tooltip/estiloColores/textoCsv` (Task 7).

- [ ] **Step 1: Badge en Asignaciones**

En `PendientesSuperTecnicoController`, `cEstado.setCellFactory(...)`, cambiar:

```java
            private final Label badgeUrgente   = new Label();
            private final Label badgePorCerrar = new Label("Por cerrar");
            private final Label badge          = new Label();
            private final javafx.scene.layout.VBox celdaBox =
                    new javafx.scene.layout.VBox(2, badgeUrgente, badgePorCerrar, badge);
```
por
```java
            private final Label badgeUrgente   = new Label();
            private final Label badgePorCerrar = new Label("Por cerrar");
            private final Label badgeEntrega   = new Label();     // "Entregado hh:mm" (A) / "Llegó hh:mm" (AG)
            private final Label badge          = new Label();
            private final javafx.scene.layout.VBox celdaBox =
                    new javafx.scene.layout.VBox(2, badgeUrgente, badgePorCerrar, badgeEntrega, badge);
```

Y en su `updateItem`, tras el bloque `if (rep.isPorCerrar()) { … } else { … }`, añadir:

```java
                String textoEntrega = com.reparaciones.utils.EntregaGlass.textoBadge(rep, com.reparaciones.utils.EntregaGlass.hoy());
                if (textoEntrega != null) {
                    badgeEntrega.setText(textoEntrega);
                    badgeEntrega.setStyle(base + com.reparaciones.utils.EntregaGlass.estiloColores());
                    badgeEntrega.setTooltip(new Tooltip(com.reparaciones.utils.EntregaGlass.tooltip(rep)));
                    badgeEntrega.setVisible(true); badgeEntrega.setManaged(true);
                } else {
                    badgeEntrega.setTooltip(null);
                    badgeEntrega.setVisible(false); badgeEntrega.setManaged(false);
                }
```
(Si `Tooltip` no está importado en este fichero, usar `javafx.scene.control.Tooltip` cualificado.)

- [ ] **Step 2: Columna "Entregado" en el CSV espejo**

En `ReparacionControllerSuperTecnico.exportarCSV`, cabeceras de `pnlPendientes`:

```java
            List<String> cabeceras = List.of(
                    "ID", "Tipo", "Técnico", "IMEI", "Modelo", "Fecha asignación", "Comentario",
                    "Cliente", "Asignado por", "Urgente", "Chasis", "Por cerrar", "En espera de pieza");
```
por
```java
            List<String> cabeceras = List.of(
                    "ID", "Tipo", "Técnico", "IMEI", "Modelo", "Fecha asignación", "Comentario",
                    "Cliente", "Asignado por", "Urgente", "Chasis", "Por cerrar", "Entregado", "En espera de pieza");
```

Y en `filaAsignacion`, tras `fila.add(r.isPorCerrar() ? "Sí" : "No");` añadir:

```java
        fila.add(com.reparaciones.utils.EntregaGlass.textoCsv(r, fmt));   // entrega a glass (A: derivada; AG: real)
```

- [ ] **Step 3: Acciones en el filtro del Log**

En `LogController`, cambiar `"MARCAR_POR_CERRAR", "QUITAR_POR_CERRAR",` por:

```java
        "MARCAR_POR_CERRAR", "QUITAR_POR_CERRAR",
        "ENTREGAR_GLASS", "DESHACER_ENTREGA_GLASS",
```

- [ ] **Step 4: Compilar y suite**

Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test; echo "EXIT: $?"`
Expected: `EXIT: 0`.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/LogController.java
git commit -m "feat(cliente): Asignaciones — badge Entregado/Llego en Estado, columna Entregado en el CSV espejo y acciones ENTREGAR_GLASS/DESHACER_ENTREGA_GLASS en el filtro del log"
```

---

### Task 10: CHANGELOG, suite completa y entrega para smoke/merge

**Files:**
- Modify: `CHANGELOG.md` (sección `[Unreleased]`)

- [ ] **Step 1: CHANGELOG**

Sustituir en `CHANGELOG.md`:

```markdown
## [Unreleased]

_(cambios para la próxima versión)_
```
por
```markdown
## [Unreleased]

### Added
- **Entrega del teléfono al técnico de glass**: en Mis pendientes, el dueño de una reparación normal cuyo IMEI tiene glass abierta puede **"Entregar a <técnico de glass>"** (clic derecho, junto a "Marcar por cerrar"); se registra quién y a qué hora. Badge **"Entregado hh:mm"** en su fila y **"Llegó hh:mm"** en la fila de glass del otro técnico (con fecha si no es de hoy; tooltip con quién/a quién), visibles también en Asignaciones. "Deshacer entrega" por si fue un error. Columna "Entregado" en el CSV de Asignaciones. Acciones `ENTREGAR_GLASS` / `DESHACER_ENTREGA_GLASS` en el log.

### Fixed
- Los errores **422** del servidor muestran su mensaje real (antes salía siempre "Contraseña actual incorrecta.").

### Notas de despliegue
- Requiere el **servidor** con el endpoint `entrega-glass` y la migración `sql/migracion-entrega-glass.sql` (columnas `ENTREGADO_AT`/`ENTREGADO_POR` en `Reparacion`; **ya aplicada en preproducción el 2026-08-28**). Orden: **ALTER → servidor → cliente**. Retrocompatible en ambos sentidos (campos JSON aditivos).
```

- [ ] **Step 2: Suite completa del cliente**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"; mvn -q -f gestion-reparaciones-cliente/pom.xml test; echo "EXIT: $?"`
Expected: `EXIT: 0`.

- [ ] **Step 3: Commit**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add CHANGELOG.md
git commit -m "docs(changelog): entrega del telefono al tecnico de glass + 422 con mensaje real (Unreleased, camino a 0.16.1)"
git status --short    # esperado: " M gestion-reparaciones-servidor" + untracked de siempre; nada más
```

- [ ] **Step 4: Entregar al usuario — smoke y merge (NO ejecutar sin su OK)**

Precondición: servidor desplegado en la VM (Task 5). Smoke desde el IDE contra preprod, checklist de la spec §9:

1. IMEI con normal + glass: entregar → "Entregado hh:mm" arriba, "Llegó hh:mm" en la pestaña Glass del otro técnico, ambos en Asignaciones.
2. Reasignar la glass → el tooltip de arriba cambia de técnico; el nuevo glass ve "Llegó".
3. Reasignar la normal → "por Manu" no cambia; el nuevo dueño puede deshacer.
4. Deshacer → desaparecen ambos badges; volver a entregar → hora nueva.
5. Borrar la glass → arriba desaparecen acción y badge.
6. IMEI solo con normal → la opción no aparece.
7. Entrega de ayer (`UPDATE Reparacion SET ENTREGADO_AT = ENTREGADO_AT - INTERVAL 1 DAY WHERE ID_REP = 'AG…'`) → badge con fecha.
8. Log: las dos acciones con su detalle; filtro funciona.
9. CSV de Asignaciones con la columna "Entregado".
10. Cliente 0.16.0 contra el servidor nuevo: todo igual que antes.

Tras el smoke, con OK del usuario:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git checkout hotfix/0.16.1
git merge --no-ff feature/entrega-glass -m "merge: entrega del telefono al tecnico de glass — Entregado/Llego en Mis pendientes y Asignaciones, CSV y log; 422 con mensaje real (feature/entrega-glass)"
```
El bump a 0.16.1, NOVEDADES y el gitlink del servidor van en el commit de release, fuera de este plan (ver spec §8 y `Apuntes/plan-futuro.md` § Hotfix 0.16.1).

---

## Extra del smoke (2026-08-28)

### Task 11: La entrega sobrevive al completar — "Llegó" en el historial de glass

Hallazgo del smoke: al completar una glass, la fila de historial `G…` nace con `FECHA_ASIG = FECHA_FIN = NOW()` y sin enlace a su `AG…` (`ID_REP_ANTERIOR` es solo para reincidencias). Decisión del usuario: la fila `G` **hereda** `ENTREGADO_AT`/`ENTREGADO_POR` de la `AG` al completar, y en las vistas de historial (Agrupado por IMEI e Historial de los 3 roles) la columna Reparador muestra bajo el nombre la sub-etiqueta gris **"Llegó dd/MM HH:mm"** (siempre con día y hora), tooltip "Bajado por <quien>, dd/MM HH:mm". Sin migración (la entrega existe desde hoy).

**Files:**
- Modify (servidor, rama `feature/entrega-glass` del submódulo, desde `main` `f3a1054`): `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (`insertarCompleta` INSERT ~L469 y `guardarFilaIndividual` INSERT ~L553; `GLASS_HISTORIAL_SELECT` ~L968)
- Test (servidor): `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java`
- Create (cliente): `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CeldaReparador.java`
- Modify (cliente): `utils/EntregaGlass.java`, `controllers/AgrupadoController.java` (~L323), `controllers/ReparacionControllerSuperTecnico.java` (~L426), `controllers/ReparacionControllerTecnico.java` (~L411), `controllers/ReparacionControllerAdmin.java` (~L287), `CHANGELOG.md`, spec §2/§5/§9
- Test (cliente): `src/test/java/com/reparaciones/utils/EntregaGlassTest.java`

**Interfaces:**
- Servidor: filas `G…` del historial devuelven `entregadoAt`/`entregadoPorNombre` (mismos campos que las `AG`; el mapper ya los lee).
- Cliente: `EntregaGlass.subEtiquetaHistorial(ReparacionResumen rep)` → `"Llegó dd/MM HH:mm"` o `null`; `CeldaReparador.crear()` → `TableCell<Object, String>`.

- [ ] **Step 1 (servidor): test que falla — el historial de glass trae la entrega**

Añadir a `ReparacionDAOEntregaGlassTest`:

```java
    @SuppressWarnings("unchecked")
    @Test void historialGlassDevuelveLaEntregaHeredada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getHistorialGlass(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains(" r.ENTREGADO_AT,"), "columna ENTREGADO_AT en historial glass");
        assertTrue(sql.getValue().contains("AS ENTREGADO_POR_NOMBRE"), "ENTREGADO_POR_NOMBRE en historial glass");
    }
```
Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionDAOEntregaGlassTest` → FAIL en `columna ENTREGADO_AT en historial glass`.

- [ ] **Step 2 (servidor): `GLASS_HISTORIAL_SELECT` con la entrega**

Dentro de `GLASS_HISTORIAL_SELECT` (termina en `WHERE r.ID_REP LIKE 'G%'`), cambiar
```java
            " tel.UPDATED_AT AS TELEFONO_UPDATED_AT," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
por
```java
            " tel.UPDATED_AT AS TELEFONO_UPDATED_AT," +
            " r.ENTREGADO_AT," +
            " (SELECT te.NOMBRE FROM Tecnico te WHERE te.ID_TEC = r.ENTREGADO_POR) AS ENTREGADO_POR_NOMBRE," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
(Esa pareja de líneas también existe en `HISTORIAL_SELECT` de reparaciones normales: NO tocarla; solo la que está entre `GLASS_HISTORIAL_SELECT =` y `getAsignacionesGlass(`.) Las queries de historial no llevan `GROUP BY`.

- [ ] **Step 3 (servidor): heredar la entrega al completar (dos INSERT)**

Añadir en `ReparacionDAO` este helper privado (junto a `entregarGlass`):

```java
    /** Entrega sellada en una AG (o {ts, id} nulos si no es AG o no hay entrega): la hereda la G al completar. */
    private Object[] entregaHeredable(String idAsignacion) {
        if (idAsignacion == null || !idAsignacion.startsWith("AG")) return new Object[] {null, null};
        java.util.Map<String, Object> e = jdbc.queryForMap(
                "SELECT ENTREGADO_AT, ENTREGADO_POR FROM Reparacion WHERE ID_REP = ?", idAsignacion);
        return new Object[] {e.get("ENTREGADO_AT"), e.get("ENTREGADO_POR")};
    }
```
En `insertarCompleta` (la variante con `categoria`), justo antes de `for (FilaReparacion fila : filas) {` (~L464) añadir `Object[] entrega = entregaHeredable(idAsignacion);` y cambiar el INSERT de ~L469-471 a:
```java
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA, ENTREGADO_AT, ENTREGADO_POR)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?,?,?)",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna, entrega[0], entrega[1]);
```
Lo mismo en `guardarFilaIndividual`: `Object[] entrega = entregaHeredable(idAsignacion);` antes de su `for`, y su INSERT (~L553-555) con las mismas dos columnas y los mismos dos argumentos extra. Si en cualquiera de los dos métodos el INSERT ya tiene otra forma, parar y reportar.

- [ ] **Step 4 (servidor): test verde + suite + commit**

Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test` → sin salida. Commit en el submódulo (solo los 2 ficheros): `feat(servidor): la G hereda ENTREGADO_AT/POR de la AG al completar y el historial de glass devuelve la entrega (Llego en historial)`.

- [ ] **Step 5 (cliente): tests que fallan — sub-etiqueta de historial**

Añadir a `EntregaGlassTest`:
```java
    @Test void subEtiquetaHistorialSoloEnGlassConEntrega() {
        ReparacionResumen g = new ReparacionResumen();
        g.setIdRep("G20260828_64");
        g.setEntregadoAt(UTC_0842);
        g.setEntregadoPorNombre("Manu");
        assertEquals("Llegó 28/08 10:42", EntregaGlass.subEtiquetaHistorial(g));
        assertEquals("Llegó 28/08 10:42", EntregaGlass.subEtiquetaHistorial(glass(UTC_0842)));
        assertNull(EntregaGlass.subEtiquetaHistorial(glass(null)));
        assertNull(EntregaGlass.subEtiquetaHistorial(normal(true, UTC_0842)));
        assertNull(EntregaGlass.subEtiquetaHistorial(null));
    }
```
Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=EntregaGlassTest` → error de compilación (`subEtiquetaHistorial`).

- [ ] **Step 6 (cliente): `EntregaGlass.subEtiquetaHistorial` + `CeldaReparador`**

En `EntregaGlass`, tras `textoCsv`:
```java
    /**
     * Sub-etiqueta bajo el nombre del reparador en las vistas de historial (Agrupado por IMEI,
     * Historial): solo filas de glass (AG/G) con entrega, siempre con día y hora porque en el
     * historial las "Fechas" de una G son las de completar, no las de asignar. Tooltip: {@link #tooltip}.
     */
    public static String subEtiquetaHistorial(ReparacionResumen rep) {
        if (rep == null || rep.getEntregadoAt() == null) return null;
        if (TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.GLASS) return null;
        return "Llegó " + FechaUtils.formatear(rep.getEntregadoAt(), FMT_DIA_HORA);
    }
```
Crear `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CeldaReparador.java`:
```java
package com.reparaciones.utils;

import com.reparaciones.models.ReparacionResumen;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/**
 * Celda de la columna Reparador de las vistas de historial: el nombre y, en filas de glass
 * con entrega, la sub-etiqueta "Llegó dd/MM HH:mm" debajo (mismo estilo que "Chasis" bajo
 * el tipo). Compartida por Agrupado por IMEI y por el Historial de los tres roles.
 */
public final class CeldaReparador {

    private CeldaReparador() {}

    public static TableCell<Object, String> crear() {
        return new TableCell<>() {
            private final Label lblNombre = new Label();
            private final Label lblLlego  = new Label();
            private final VBox  box       = new VBox(1, lblNombre, lblLlego);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                lblLlego.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A94A6;");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null || item.isEmpty()) { setGraphic(null); return; }
                Object fila = (getIndex() >= 0 && getIndex() < getTableView().getItems().size())
                        ? getTableView().getItems().get(getIndex()) : null;
                String sub = fila instanceof ReparacionResumen rep ? EntregaGlass.subEtiquetaHistorial(rep) : null;
                lblNombre.setText(item);
                if (sub != null) {
                    lblLlego.setText(sub);
                    lblLlego.setTooltip(new Tooltip(EntregaGlass.tooltip((ReparacionResumen) fila)));
                    lblLlego.setVisible(true); lblLlego.setManaged(true);
                } else {
                    lblLlego.setTooltip(null);
                    lblLlego.setVisible(false); lblLlego.setManaged(false);
                }
                setGraphic(box);
            }
        };
    }
}
```

- [ ] **Step 7 (cliente): usar la celda en las 4 vistas**

En `AgrupadoController` (~L323), `ReparacionControllerSuperTecnico` (~L426), `ReparacionControllerTecnico` (~L411) y `ReparacionControllerAdmin` (~L287), sustituir el bloque
```java
        colReparador.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(empty || item == null || item.isEmpty() ? null : item);
            }
        });
```
por
```java
        colReparador.setCellFactory(col -> com.reparaciones.utils.CeldaReparador.crear());   // nombre + "Llegó …" en glass
```
Si en alguno de los cuatro el bloque no es exactamente ese (p. ej. `colReparador` tipado distinto de `TableColumn<Object, String>`), parar y reportar en vez de adaptar.

- [ ] **Step 8 (cliente): docs, suite, commit**

`CHANGELOG.md` `[Unreleased]` → Added: añadir al final de la línea de la entrega: ` En el historial (Agrupado por IMEI e Historial), las glass completadas muestran bajo el reparador **"Llegó dd/MM hh:mm"** (la entrega se hereda al completar).`
Spec: en §2 tabla de ciclo de vida, fila "Completan la glass" → `La fila `G` nueva **hereda** `ENTREGADO_AT`/`ENTREGADO_POR` de la `AG` (y la `AG` cerrada los conserva). En Agrupado por IMEI e Historial, bajo el reparador: sub-etiqueta "Llegó dd/MM hh:mm" (las "Fechas" de una `G` son las de completar, no las de asignar).`; en §9 añadir `14. Completar la glass entregada → en Agrupado por IMEI e Historial la fila G muestra "Llegó dd/MM hh:mm" bajo el reparador (tooltip "Bajado por …").`
Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test` → sin salida. Commit (raíz, por nombre, sin gitlink): `feat(cliente): "Llego dd/MM hh:mm" bajo el reparador en Agrupado por IMEI e Historial (celda compartida CeldaReparador; la G hereda la entrega)`.

---

### Task 12: Sin teléfono no hay glass — ocultar "Añadir glass" hasta la entrega

Decisión del usuario (smoke 2026-08-28): en Mis pendientes → Glass, el botón **"Añadir glass"** se **oculta** mientras el teléfono no haya llegado. Regla exacta (para no bloquear a nadie): se oculta **solo si** el IMEI tiene una **reparación normal abierta** (`A…` sin `FECHA_FIN`, ni `AG` ni `AP`) **y** la glass **no tiene entrega** (`entregadoAt == null`). Sin normal abierta (glass directa, o normal ya completada sin marcar) → botón visible. Con "Llegó" → visible.

**Files:**
- Modify (servidor, rama `feature/entrega-glass-gate` desde `main` `f8c09ed`): `src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`, `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (`GLASS_ASIGNACION_SELECT` y `RESUMEN_MAPPER`)
- Test (servidor): `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOEntregaGlassTest.java`
- Modify (cliente, rama `feature/entrega-glass`): `models/ReparacionResumen.java`, `utils/EntregaGlass.java`, `controllers/PendientesTecnicoController.java` (celda `cAccion`, ~L301-320), `CHANGELOG.md`, spec §2/§9
- Test (cliente): `src/test/java/com/reparaciones/utils/EntregaGlassTest.java`

**Interfaces:**
- Servidor → JSON aditivo en filas `AG`: `normalAbierta` (boolean), `normalTecnicoNombre` (String, dueño de la normal abierta más antigua; null si no hay).
- Cliente: `EntregaGlass.ocultarAnadirGlass(ReparacionResumen rep)` → boolean.

- [ ] **Step 1 (servidor): test que falla**

Añadir a `ReparacionDAOEntregaGlassTest`:
```java
    @SuppressWarnings("unchecked")
    @Test void asignacionesGlassDicenSiHayNormalAbiertaEnElImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignacionesGlass(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        String q = sql.getValue();
        assertTrue(q.contains("AS NORMAL_ABIERTAS"), "NORMAL_ABIERTAS");
        assertTrue(q.contains("AS NORMAL_TECNICO_NOMBRE"), "NORMAL_TECNICO_NOMBRE");
        assertTrue(q.contains("n.ID_REP LIKE 'A%' AND n.ID_REP NOT LIKE 'AG%' AND n.ID_REP NOT LIKE 'AP%' AND n.FECHA_FIN IS NULL"),
                "solo reparaciones normales abiertas");
    }
```
Run: `mvn -q -f gestion-reparaciones-servidor/pom.xml test -Dtest=ReparacionDAOEntregaGlassTest` → FAIL en `NORMAL_ABIERTAS`.

- [ ] **Step 2 (servidor): modelo + SELECT + mapper**

`ReparacionResumen` (servidor): tras `private String glassTecnicoNombre;` añadir
```java
    private boolean       normalAbierta;            // AG: hay reparación normal abierta en el IMEI (alguien arriba debe entregar)
    private String        normalTecnicoNombre;      // AG: dueño de esa normal (la más antigua)
```
y los accesores tras `setGlassTecnicoNombre`:
```java
    public boolean       isNormalAbierta()                         { return normalAbierta; }
    public void          setNormalAbierta(boolean v)               { this.normalAbierta = v; }
    public String        getNormalTecnicoNombre()                  { return normalTecnicoNombre; }
    public void          setNormalTecnicoNombre(String v)          { this.normalTecnicoNombre = v; }
```
`ReparacionDAO`, dentro de `GLASS_ASIGNACION_SELECT` (termina en `WHERE r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL`), justo después de la línea `" (SELECT te.NOMBRE FROM Tecnico te WHERE te.ID_TEC = r.ENTREGADO_POR) AS ENTREGADO_POR_NOMBRE," +` añadir:
```java
            // Sin teléfono no hay glass: ¿hay una reparación normal abierta arriba que deba entregarlo? (Task 12)
            " (SELECT COUNT(*) FROM Reparacion n" +
            "  WHERE n.IMEI = r.IMEI AND n.ID_REP LIKE 'A%' AND n.ID_REP NOT LIKE 'AG%' AND n.ID_REP NOT LIKE 'AP%' AND n.FECHA_FIN IS NULL) AS NORMAL_ABIERTAS," +
            " (SELECT tn.NOMBRE FROM Reparacion n JOIN Tecnico tn ON n.ID_TEC = tn.ID_TEC" +
            "  WHERE n.IMEI = r.IMEI AND n.ID_REP LIKE 'A%' AND n.ID_REP NOT LIKE 'AG%' AND n.ID_REP NOT LIKE 'AP%' AND n.FECHA_FIN IS NULL" +
            "  ORDER BY n.FECHA_ASIG ASC LIMIT 1) AS NORMAL_TECNICO_NOMBRE," +
```
(Solo correlacionan `r.IMEI`, ya agrupado: los GROUP BY no cambian.) En `RESUMEN_MAPPER`, tras la línea de `setGlassTecnicoNombre`:
```java
        try { rr.setNormalAbierta(rs.getInt("NORMAL_ABIERTAS") > 0); } catch (Exception ignored) {}
        try { rr.setNormalTecnicoNombre(rs.getString("NORMAL_TECNICO_NOMBRE")); } catch (Exception ignored) {}
```
Run el test enfocado (verde) y la suite completa del servidor. Commit (3 ficheros): `feat(servidor): filas AG con normalAbierta/normalTecnicoNombre (hay reparacion normal abierta en el IMEI) para ocultar Anadir glass hasta la entrega`.

- [ ] **Step 3 (cliente): tests que fallan**

Añadir a `EntregaGlassTest`:
```java
    @Test void anadirGlassSeOcultaSoloConNormalAbiertaYSinEntrega() {
        ReparacionResumen g = glass(null);
        g.setNormalAbierta(true);
        assertTrue(EntregaGlass.ocultarAnadirGlass(g));                 // alguien arriba aún no ha entregado
        ReparacionResumen entregada = glass(UTC_0842);
        entregada.setNormalAbierta(true);
        assertFalse(EntregaGlass.ocultarAnadirGlass(entregada));        // ya llegó
        assertFalse(EntregaGlass.ocultarAnadirGlass(glass(null)));      // glass directa, sin normal arriba
        ReparacionResumen normal = normal(true, null);
        normal.setNormalAbierta(true);
        assertFalse(EntregaGlass.ocultarAnadirGlass(normal));           // no es fila de glass
        assertFalse(EntregaGlass.ocultarAnadirGlass(null));
    }
```
Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=EntregaGlassTest` → error de compilación (`setNormalAbierta`).

- [ ] **Step 4 (cliente): modelo, lógica y botón**

`ReparacionResumen` (cliente): tras `private String glassTecnicoNombre;` añadir `private boolean normalAbierta;` y `private String normalTecnicoNombre;`, con accesores `isNormalAbierta/setNormalAbierta/getNormalTecnicoNombre/setNormalTecnicoNombre` tras `setGlassTecnicoNombre`.
`EntregaGlass`, tras `subEtiquetaHistorial`:
```java
    /**
     * Sin teléfono no hay glass (decisión 2026-08-28): el botón "Añadir glass" se oculta mientras haya
     * una reparación normal abierta en el IMEI y esta glass no tenga entrega. Sin normal abierta
     * (glass directa, o normal ya completada sin marcar) no se bloquea a nadie.
     */
    public static boolean ocultarAnadirGlass(ReparacionResumen rep) {
        if (rep == null || TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.GLASS) return false;
        return rep.isNormalAbierta() && rep.getEntregadoAt() == null;
    }
```
`PendientesTecnicoController`, celda `cAccion`, sustituir el `updateItem`:
```java
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) btn.setText(glass ? "Añadir glass" : "Añadir reparación");
                setGraphic(empty ? null : btn);
            }
```
por
```java
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                btn.setText(glass ? "Añadir glass" : "Añadir reparación");
                ReparacionResumen asig = getTableView().getItems().get(getIndex());
                // Glass: sin el teléfono (normal abierta arriba y sin entrega) no hay nada que reparar → sin botón
                setGraphic(glass && EntregaGlass.ocultarAnadirGlass(asig) ? null : btn);
            }
```

- [ ] **Step 5 (cliente): docs, suite, commit**

CHANGELOG `[Unreleased]` → Added, nueva línea: `- **Sin teléfono no hay glass**: en Mis pendientes → Glass, el botón "Añadir glass" no aparece mientras el IMEI tenga una reparación normal abierta y la glass no tenga entrega; en cuanto llega la píldora "Llegó" (o si no hay reparación normal abierta) vuelve a estar disponible.`
Spec §2, tras la lista de "Qué ve cada uno": `- **Sin teléfono no hay glass** (smoke 2026-08-28): en Mis pendientes → Glass, "Añadir glass" se oculta mientras haya una reparación normal abierta en el IMEI y la glass no tenga entrega. Sin normal abierta (glass directa o normal completada sin marcar) no se oculta: nadie queda bloqueado. Campos derivados en filas AG: `normalAbierta`, `normalTecnicoNombre`.` Spec §9: `13. Glass con normal abierta y sin entrega → sin botón "Añadir glass"; tras entregar → aparece; glass sin normal abierta → aparece siempre.`
Run suite completa del cliente → sin salida. Commit (por nombre, sin gitlink): `feat(cliente): ocultar "Anadir glass" mientras haya reparacion normal abierta sin entrega (sin telefono no hay glass)`.

---

### Task 13: Píldora "Glass: <técnico>" bajo el IMEI de la reparación normal (solo cliente)

Decisión del usuario (smoke 2026-08-31): en las filas de reparación normal cuyo IMEI tiene glass abierta **sin entrega registrada**, mostrar bajo el IMEI una **mini-píldora con la paleta del tipo Glass** con el texto **"Glass: Jhona"** (dueño actual de la glass). Texto neutro a propósito: el teléfono puede estar arriba o ya abajo (abierto y repartido allí); la píldora solo dice de quién es la glass. Al registrar la entrega desaparece (la índigo "→ Jhona" de Estado toma el relevo). En Asignaciones, además, **replantea el "N asignados"**: la píldora lo sustituye cuando aplica, y con la glass ya entregada y solo 2 asignados no se muestra nada (la píldora índigo ya lo cuenta). Sin servidor: `glassAbierta`/`glassTecnicoNombre`/`glassEntregadoAt` ya llegan.

**Files:**
- Modify (cliente, rama `feature/entrega-glass`): `utils/EntregaGlass.java`, `controllers/PendientesTecnicoController.java` (celda `cImei`, ~L73-94), `controllers/PendientesSuperTecnicoController.java` (celda `cImei`, ~L252-285), `CHANGELOG.md`, spec §2/§9
- Test: `src/test/java/com/reparaciones/utils/EntregaGlassTest.java`

**Interfaces:**
- `EntregaGlass.etiquetaGlassPendiente(ReparacionResumen rep)` → `"Glass: <nombre>"` o `null`.
- `EntregaGlass.ocultarContadorAsignados(ReparacionResumen rep, int n)` → boolean (fila `A…` con glass entregada y `n == 2`).
- `EntregaGlass.estiloPildoraGlassPendiente()` → estilo completo de la mini-píldora.

- [ ] **Step 1: tests que fallan**

Añadir a `EntregaGlassTest`:
```java
    @Test void etiquetaGlassPendienteSoloEnNormalConGlassSinEntrega() {
        assertEquals("Glass: Jhona", EntregaGlass.etiquetaGlassPendiente(normal(true, null)));
        assertNull(EntregaGlass.etiquetaGlassPendiente(normal(true, UTC_0842)));   // entregada: la cuenta la píldora →
        assertNull(EntregaGlass.etiquetaGlassPendiente(normal(false, null)));      // sin glass
        assertNull(EntregaGlass.etiquetaGlassPendiente(glass(null)));              // fila AG, no aplica
        assertNull(EntregaGlass.etiquetaGlassPendiente(null));
    }

    @Test void contadorAsignadosSeOcultaSoloConGlassEntregadaYDosAsignados() {
        assertTrue(EntregaGlass.ocultarContadorAsignados(normal(true, UTC_0842), 2));
        assertFalse(EntregaGlass.ocultarContadorAsignados(normal(true, UTC_0842), 3)); // hay mas gente: el contador aporta
        assertFalse(EntregaGlass.ocultarContadorAsignados(normal(true, null), 2));     // sin entrega: lo cubre la pildora verde
        assertFalse(EntregaGlass.ocultarContadorAsignados(glass(UTC_0842), 2));        // fila AG, no aplica
        assertFalse(EntregaGlass.ocultarContadorAsignados(null, 2));
    }

    @Test void estiloPildoraGlassPendienteUsaLaPaletaGlass() {
        assertTrue(EntregaGlass.estiloPildoraGlassPendiente().contains(TipoTrabajo.GLASS.colorFondo()));
        assertTrue(EntregaGlass.estiloPildoraGlassPendiente().contains(TipoTrabajo.GLASS.colorTexto()));
    }
```
Run: `mvn -q -f gestion-reparaciones-cliente/pom.xml test -Dtest=EntregaGlassTest` → error de compilación.

- [ ] **Step 2: lógica en `EntregaGlass`**

Tras `ocultarAnadirGlass`:
```java
    /**
     * Píldora bajo el IMEI de la reparación normal mientras la glass del IMEI no tenga entrega
     * registrada: "Glass: <dueño actual>". Texto neutro a propósito: el teléfono puede estar
     * arriba o ya abajo (abierto y repartido allí); solo dice de quién es la glass. Al entregar
     * → null (la píldora índigo "→ …" de Estado toma el relevo).
     */
    public static String etiquetaGlassPendiente(ReparacionResumen rep) {
        if (rep == null || TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.REPARACION) return null;
        if (!rep.isGlassAbierta() || rep.getGlassEntregadoAt() != null) return null;
        return "Glass: " + nombre(rep.getGlassTecnicoNombre());
    }

    /**
     * El "N asignados" de la vista Asignaciones sobra cuando la píldora índigo ya cuenta la
     * historia: fila normal con glass entregada y exactamente 2 asignados (el caso típico).
     * Con 3+ el contador sigue aportando.
     */
    public static boolean ocultarContadorAsignados(ReparacionResumen rep, int n) {
        if (rep == null || TipoTrabajo.desde(rep.getIdRep()) != TipoTrabajo.REPARACION) return false;
        return rep.isGlassAbierta() && rep.getGlassEntregadoAt() != null && n == 2;
    }

    /** Estilo completo de la mini-píldora "Glass: …" (paleta del tipo Glass, tamaño sub-etiqueta). */
    public static String estiloPildoraGlassPendiente() {
        return "-fx-background-radius: 8; -fx-padding: 1 8 1 8; -fx-font-size: 10px; -fx-font-weight: bold;"
             + "-fx-background-color: " + TipoTrabajo.GLASS.colorFondo() + "; -fx-text-fill: " + TipoTrabajo.GLASS.colorTexto() + ";";
    }
```

- [ ] **Step 3: celda IMEI de Mis pendientes (`PendientesTecnicoController` ~L73-94)**

Sustituir la celda actual de `cImei` (Label suelto) por la versión con píldora (misma gestión de selección):
```java
        cImei.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            private final Label lblGlass = new Label();
            private final javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(1, lbl, lblGlass);
            private final javafx.beans.value.ChangeListener<Boolean> selListener =
                (obs, o, sel) -> lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (sel ? "white" : "#2C3B54") + ";");
            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #2C3B54;");
                lblGlass.setStyle(EntregaGlass.estiloPildoraGlassPendiente());
                lblGlass.setVisible(false); lblGlass.setManaged(false);
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) oldRow.selectedProperty().removeListener(selListener);
                    if (newRow != null) newRow.selectedProperty().addListener(selListener);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                ReparacionResumen rep = getTableView().getItems().get(getIndex());
                lbl.setText(rep.getImei());
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (getTableRow() != null && getTableRow().isSelected() ? "white" : "#2C3B54") + ";");
                String glassPend = EntregaGlass.etiquetaGlassPendiente(rep);
                if (glassPend != null) {
                    lblGlass.setText(glassPend);
                    lblGlass.setTooltip(new Tooltip("Glass abierta de " + rep.getGlassTecnicoNombre() + " — entrega sin registrar"));
                    lblGlass.setVisible(true); lblGlass.setManaged(true);
                } else {
                    lblGlass.setText(null);
                    lblGlass.setTooltip(null);
                    lblGlass.setVisible(false); lblGlass.setManaged(false);
                }
                setGraphic(box);
            }
        });
```

- [ ] **Step 4: celda IMEI de Asignaciones (`PendientesSuperTecnicoController` ~L252-285)**

En la celda existente: añadir `private final Label lblGlass = new Label();` tras `lblAsignados`, VBox pasa a `new javafx.scene.layout.VBox(1, lbl, lblGlass, lblAsignados)`, en el bloque init `lblGlass.setStyle(com.reparaciones.utils.EntregaGlass.estiloPildoraGlassPendiente()); lblGlass.setVisible(false); lblGlass.setManaged(false);` y en `updateItem`, sustituir el bloque del contador:
```java
                int n = conteoTecnicosPorImei.getOrDefault(imei, 1);
                boolean varios = n >= 2;
                lblAsignados.setText(varios ? n + " asignados" : "");
                lblAsignados.setVisible(varios); lblAsignados.setManaged(varios);
```
por
```java
                ReparacionResumen repFila = getTableView().getItems().get(getIndex());
                String glassPend = com.reparaciones.utils.EntregaGlass.etiquetaGlassPendiente(repFila);
                int n = conteoTecnicosPorImei.getOrDefault(imei, 1);
                if (glassPend != null) {
                    lblGlass.setText(glassPend);
                    lblGlass.setTooltip(new Tooltip("Glass abierta de " + repFila.getGlassTecnicoNombre() + " — entrega sin registrar"));
                    lblGlass.setVisible(true); lblGlass.setManaged(true);
                } else {
                    lblGlass.setText(null); lblGlass.setTooltip(null);
                    lblGlass.setVisible(false); lblGlass.setManaged(false);
                }
                boolean varios = n >= 2 && glassPend == null
                        && !com.reparaciones.utils.EntregaGlass.ocultarContadorAsignados(repFila, n);
                lblAsignados.setText(varios ? n + " asignados" : "");
                lblAsignados.setVisible(varios); lblAsignados.setManaged(varios);
```
(Si `Tooltip` no resuelve sin cualificar en ese fichero, usar `javafx.scene.control.Tooltip`. Si la celda difiere del anclaje, parar y reportar.)

- [ ] **Step 5: docs, suite, commit**

CHANGELOG `[Unreleased]` → Added, nueva línea: `- **Píldora "Glass: <técnico>" bajo el IMEI** en Mis pendientes y Asignaciones (filas de reparación normal con glass abierta sin entrega): se ve de primeras quién tiene la glass del IMEI, esté el teléfono arriba o ya abajo. Al registrar la entrega la sustituye la píldora "→ <técnico>"; en Asignaciones, el "2 asignados" genérico deja de mostrarse cuando la píldora (verde o índigo) ya cuenta quién es el segundo.`
Spec §2, tras el bloque "Sin teléfono no hay glass": `- **Píldora "Glass: <técnico>"** (smoke 2026-08-31): bajo el IMEI de la reparación normal mientras la glass del IMEI no tenga entrega registrada (paleta del tipo Glass; texto neutro — el teléfono puede estar arriba o ya abajo, abierto y repartido allí). Al entregar desaparece (la "→ …" de Estado toma el relevo). En Asignaciones sustituye al "N asignados" cuando aplica, y con glass entregada y 2 asignados no se muestra contador.` Spec §9, añadir: `15. Fila normal con glass sin entrega → píldora verde "Glass: <técnico>" bajo el IMEI (Mis pendientes y Asignaciones; el "2 asignados" no aparece); al entregar → desaparece y queda "→ <técnico>"; con 3 asignados el contador vuelve.`
Run suite completa del cliente → sin salida. Commit (por nombre, sin gitlink): `feat(cliente): pildora "Glass: <tecnico>" bajo el IMEI en Mis pendientes y Asignaciones mientras la entrega no este registrada (sustituye al contador cuando aplica)`.

---

### Task 14: "Marcar que llegó" — el técnico de glass desbloquea su propia asignación

Decisión del usuario (2026-08-31, pre-reparto de la 0.16.1): el gate "sin teléfono no hay glass" no debe depender de que el dueño de la normal pulse "Entregar" (abajo también hay técnicos con normales y el teléfono ya está allí). **Válvula de escape**: en la fila de glass **bloqueada** (normal abierta y sin entrega), menú contextual → **"Marcar que llegó"**: sella la entrega ahora con `ENTREGADO_POR` = el propio técnico de glass (firmado y en el log; misma confianza que "Por cerrar"). El camino principal sigue siendo "Entregar a X".

**Files:**
- Modify (servidor, rama `feature/entrega-glass-llegada` desde `main` `9eba0ae`): `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (tras `actualizarEntregaGlass`)
- Test (servidor): `src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEntregaGlassTest.java` (ampliar)
- Modify (cliente, rama `hotfix/0.16.1`): `utils/EntregaGlass.java`, `dao/ReparacionDAO.java`, `controllers/PendientesTecnicoController.java` (menú contextual), `CHANGELOG.md` (línea 0.16.1), `NOVEDADES-v0.16.1.md`, spec §2/§4/§9
- Test (cliente): `src/test/java/com/reparaciones/utils/EntregaGlassTest.java`

**Interfaces:**
- Servidor: `PATCH /api/reparaciones/asignaciones/{idRep}/llegada` (idRep = `AG…`, sin body, 204). Validaciones: no es `AG` → 422 "Solo aplica a asignaciones de glass"; no existe/cerrada → 404; no es el dueño → 403 "Solo puedes registrar la llegada de tus propias asignaciones"; ya entregada → 422 "La entrega ya está registrada". Efecto: `dao.entregarGlass(imei, principal.getIdTec())` (sella TODAS las AG abiertas del IMEI). Log: acción `ENTREGAR_GLASS`, detalle `ID_REP: AG…, IMEI: …, LLEGADA registrada por el tecnico de glass`.
- Cliente: `EntregaGlass.mostrarMarcarLlegada(ReparacionResumen rep, boolean pestanaGlass)` → `pestanaGlass && ocultarAnadirGlass(rep)`; `ReparacionDAO.marcarLlegadaGlass(String idRep)` → `ApiClient.patch(".../llegada", null)`.

- [ ] **Step 1 (servidor): tests que fallan** — añadir a `ReparacionControllerEntregaGlassTest` (usa los helpers existentes `asigDe`, `manu`, `statusDe`; recuerda crear el mock ANTES del `when(...)` externo):

```java
    @Test void llegadaRechazaIdsQueNoSonGlass() {
        assertEquals(422, statusDe(() -> ctl.marcarLlegadaGlass("A20260828_1", manu)));
        verifyNoInteractions(logDao);
    }

    @Test void llegadaInexistenteEs404() {
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", manu)));
    }

    @Test void llegadaSoloDelDueno() {
        ReparacionResumen ajena = asigDe(99);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(ajena));
        assertEquals(403, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", manu)));
        verify(dao, never()).entregarGlass(anyString(), anyInt());
    }

    @Test void llegadaYaEntregadaEs422() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.marcarLlegadaGlass("AG20260828_3", manu));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("La entrega ya está registrada", ex.getReason());
        verify(dao, never()).entregarGlass(anyString(), anyInt());
    }

    @Test void llegadaSellaYFirmaElPropioTecnico() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ctl.marcarLlegadaGlass("AG20260828_3", manu);
        verify(dao).entregarGlass(IMEI, 7);
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA registrada por el tecnico de glass");
    }
```
Run test enfocado → error de compilación (`marcarLlegadaGlass`).

- [ ] **Step 2 (servidor): endpoint** — en `ReparacionController`, justo después de `actualizarEntregaGlass`:

```java
    /**
     * Llegada registrada por el propio técnico de glass (válvula de escape del gate "sin teléfono
     * no hay glass" — spec 2026-08-28 §2): sella la entrega en TODAS las AG abiertas del IMEI con
     * él como ENTREGADO_POR. Solo el dueño de la AG y solo si aún no hay entrega registrada.
     */
    @PatchMapping("/asignaciones/{idRep}/llegada")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLlegadaGlass(@PathVariable String idRep,
                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (idRep == null || !idRep.startsWith("AG")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Solo aplica a asignaciones de glass");
        }
        ReparacionResumen asig = dao.getAsignacionAnyById(idRep)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idRep));
        boolean esSuya = principal.getIdTec() != null && asig.getIdTec() == principal.getIdTec();
        if (!esSuya) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puedes registrar la llegada de tus propias asignaciones");
        }
        if (asig.getEntregadoAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La entrega ya está registrada");
        }
        dao.entregarGlass(asig.getImei(), principal.getIdTec());
        logDao.insertar(principal.getIdUsu(), "ENTREGAR_GLASS",
                "ID_REP: " + idRep + ", IMEI: " + asig.getImei() + ", LLEGADA registrada por el tecnico de glass");
    }
```
Test enfocado verde (11 tests) → suite completa → commit (2 ficheros): `feat(servidor): PATCH /asignaciones/{idRep}/llegada — el tecnico de glass registra su propia llegada cuando el gate le bloquea (firmado como ENTREGADO_POR)`.

- [ ] **Step 3 (cliente): tests que fallan** — añadir a `EntregaGlassTest`:

```java
    @Test void marcarLlegadaSoloEnPestanaGlassYFilaBloqueada() {
        ReparacionResumen bloqueada = glass(null);
        bloqueada.setNormalAbierta(true);
        assertTrue(EntregaGlass.mostrarMarcarLlegada(bloqueada, true));
        assertFalse(EntregaGlass.mostrarMarcarLlegada(bloqueada, false));            // pestaña Reparación
        ReparacionResumen entregada = glass(UTC_0842);
        entregada.setNormalAbierta(true);
        assertFalse(EntregaGlass.mostrarMarcarLlegada(entregada, true));             // ya llegó
        assertFalse(EntregaGlass.mostrarMarcarLlegada(glass(null), true));           // glass directa: no está bloqueada
        assertFalse(EntregaGlass.mostrarMarcarLlegada(null, true));
    }
```
Run → error de compilación.

- [ ] **Step 4 (cliente): lógica, DAO y menú** — `EntregaGlass`, tras `ocultarAnadirGlass`:

```java
    /**
     * "Marcar que llegó": válvula de escape del gate — visible solo en la pestaña Glass, en la
     * fila bloqueada (normal abierta y sin entrega). La firma el propio técnico de glass.
     */
    public static boolean mostrarMarcarLlegada(ReparacionResumen rep, boolean pestanaGlass) {
        return pestanaGlass && ocultarAnadirGlass(rep);
    }
```
`ReparacionDAO` (cliente), tras `actualizarEntregaGlass`:
```java
    /** El técnico de glass registra él mismo la llegada del teléfono (válvula del gate). */
    public void marcarLlegadaGlass(String idRep) throws SQLException {
        ApiClient.patch("/api/reparaciones/asignaciones/" + idRep + "/llegada", null);
    }
```
`PendientesTecnicoController`, tras `menu.getItems().add(toggleEntrega);`:
```java
                MenuItem marcarLlegada = new MenuItem("Marcar que llegó");
                marcarLlegada.setOnAction(e -> {
                    ReparacionResumen rep = getItem();
                    if (rep == null) return;
                    try {
                        reparacionDAO.marcarLlegadaGlass(rep.getIdRep());
                        cargar();
                    } catch (SQLException ex) { mostrarError(ex); }
                });
                menu.getItems().add(marcarLlegada);
```
y dentro de `menu.setOnShowing`, tras las líneas de `toggleEntrega`:
```java
                    marcarLlegada.setVisible(EntregaGlass.mostrarMarcarLlegada(rep, glass));
```

- [ ] **Step 5 (cliente): docs, suite, commit** — spec §2, tras el bloque "Sin teléfono no hay glass": `- **"Marcar que llegó"** (2026-08-31): en la fila de glass bloqueada, el propio técnico registra la llegada (menú contextual); queda firmado (`ENTREGADO_POR` = él, log `ENTREGAR_GLASS` con detalle de llegada propia). Cubre a los técnicos de abajo, que no dependen de que nadie "baje" nada.` Spec §4, tras el endpoint de entrega: una línea con el endpoint `/llegada` y sus validaciones. Spec §9: `16. Fila glass bloqueada → "Marcar que llegó" en su menú; al usarla el botón "Añadir glass" aparece, arriba se ve "→ <glass>" y el log registra la llegada propia; en una fila ya entregada o sin normal abierta la opción no sale.` CHANGELOG (sección 0.16.1, línea "Sin teléfono no hay glass"): añadir al final: ` Si el dueño de la reparación no registra la entrega (p. ej. el teléfono ya estaba abajo), el técnico de glass puede desbloquearse con **"Marcar que llegó"** (queda firmado en el log).` NOVEDADES-v0.16.1.md, sección "🚫 Sin teléfono no hay glass", añadir bullet: `- ¿El teléfono ya estaba abajo o nadie registró la entrega? El técnico de glass tiene **"Marcar que llegó"** (clic derecho): se desbloquea al momento y queda firmado quién lo registró.` Suite completa verde → commit (por nombre, sin gitlink): `feat(cliente): "Marcar que llego" — el tecnico de glass registra su propia llegada cuando el gate le bloquea`.
