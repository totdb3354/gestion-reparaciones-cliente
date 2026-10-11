# 0.9.8 (añadido) — Cliente en el Historial y «Corregir cliente»: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El Historial de reparación y glass y el detalle de un IMEI muestran (y filtran y exportan) el cliente guardado de cada trabajo, y un supertécnico o el administrador pueden corregir ese cliente en uno o varios trabajos cerrados de un IMEI desde un modal.

**Architecture:** Servidor: dos rutas en `ReparacionController` (`GET /api/reparaciones/imei/{imei}/cerrados`, `PATCH /api/reparaciones/cliente`) sobre dos métodos nuevos de `ReparacionDAO` y un modelo `TrabajoCerrado`. Web: columna «Cliente» en `columnasTrabajo` (compartida por el Historial y el detalle de IMEIs), filtro en `HistorialPage`, CSV, y un `CorregirClienteDialog` abierto desde `MenuHistorial` vía `useAccionesTrabajo`.

**Tech Stack:** Spring Boot 3.3 + JdbcTemplate + MariaDB (JUnit 5 + Mockito); React + TypeScript (Vitest + Testing Library + MSW); openapi-typescript.

**Spec:** `docs/superpowers/specs/2026-10-11-v098-cliente-en-historial-design.md`. Base: la 0.9.8 ya mergeada (servidor `main` `cbea34c`, web `main` `4fa9679`), sin tag y solo en preproducción.

## Global Constraints

- Versión **0.9.8** (la misma; sin tag ni producción todavía). Ramas **`feature/cliente-en-historial`** en servidor y web, desde `main`, **en los clones normales** (la otra sesión, la 0.9.9, trabaja en `wt/*-099`). Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**.
- El clon del servidor tiene **sin commit** en `main` el arreglo de collation del relleno (`sql/relleno-cliente-por-trabajo.sql`, `unicode_ci` → `uca1400_ai_ci`): es el **primer commit** de la rama del servidor (`fix: el relleno usa la collation uca1400 de las tablas`).
- Commits en español sin tildes, prefijo `feat:` / `test:` / `docs:` / `chore:` / `fix:`, **sin** línea `Co-Authored-By` (comprobar con `git log -1 --format=%B`).
- Repos **públicos**: datos de tests sintéticos.
- Permisos: **«Corregir cliente» y su lectura** → `@PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")` en el servidor; en la web, visible para `esAdminOSuperTecnico(sesion)`. **No hay jerarquía de roles**: `hasRole('SUPERTECNICO')` excluye al ADMIN. El resto del menú de edición del Historial sigue siendo solo del supertécnico (`puedeEditar`).
- Registro: una línea **`CORREGIR_CLIENTE_TRABAJO`** por trabajo, detalle exacto `ID_REP: <id>, IMEI: <imei>, ID_CLI: <anterior> → <nuevo>` con `—` para sin cliente.
- `UPDATED_AT` de `Reparacion` se **conserva** al corregir (`UPDATED_AT = UPDATED_AT`).
- Textos de la UI: columna y filtro **«Cliente»**; acción **«Corregir cliente»**; botón **«Corregir N»**; opción **«— Sin cliente —»**; vacío en la columna **«—»**.
- Servidor (Git Bash): `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"` y `mvn -q test` en `gestion-reparaciones-servidor`.
- Web: `npx vitest run <ruta>`, `npx tsc -b`, `npm run lint` en `gestion-reparaciones-web`. Si la suite completa da timeouts sueltos por carga de la máquina, repetir aparte los ficheros afectados (pasó en la 0.9.8).
- Producción (después de esta tarea 4): el plan `2026-10-09-v098-cliente-por-trabajo.md` Task 7 y el de la limpieza `2026-10-09-quitar-clientes-incidencias.md` Task 3, con todo junto.

---

### Task 1: Servidor — rutas para listar y corregir el cliente de trabajos cerrados

**Files:**
- Commit previo: `gestion-reparaciones-servidor/sql/relleno-cliente-por-trabajo.sql` (ya modificado en el árbol)
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/TrabajoCerrado.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (métodos nuevos al final de la sección de lectura y de escritura)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (dos rutas y un record)
- Modify: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ReparacionDAOCorregirClienteTest.java`
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ReparacionControllerCorregirClienteTest.java`

**Interfaces:**
- Produces: `GET /api/reparaciones/imei/{imei}/cerrados` → `TrabajoCerrado[]` (`idRep`, `fechaFin`, `descripcion?`, `idCli?`, `cliente?`); `PATCH /api/reparaciones/cliente` con cuerpo `{ imei: string, idReps: string[], idCli: number | null }` → 204. Esquemas `TrabajoCerrado` y `ReparacionCorregirClienteRequest` en el contrato (`target/openapi.json`).

- [ ] **Step 1: Rama y commit previo**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git status --short   # debe salir solo:  M sql/relleno-cliente-por-trabajo.sql
git checkout -b feature/cliente-en-historial
git add sql/relleno-cliente-por-trabajo.sql
git commit -m "fix: el relleno usa la collation uca1400 de las tablas"
```

- [ ] **Step 2: Tests que fallan**

`ReparacionDAOCorregirClienteTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Corregir cliente» (spec 0.9.8 añadido §3): lectura de los cerrados de un IMEI y corrección todo o nada. */
class ReparacionDAOCorregirClienteTest {

    private static final String IMEI = "355400000000111";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void losCerradosDeUnImeiSonDeCualquierTipoYSoloCerrados() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getCerradosPorImei(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI));
        String q = sql.getValue();
        assertTrue(q.contains("WHERE r.IMEI = ? AND r.FECHA_FIN IS NOT NULL"), q);
        assertTrue(q.contains("LEFT JOIN Cliente cli ON cli.ID_CLI = r.ID_CLI"), q);
        assertTrue(q.contains("ORDER BY r.FECHA_FIN DESC"), q);
        assertFalse(q.contains("LIKE 'R%' AND"), "no se limita a un tipo");
    }

    @Test void corregirCambiaElClienteConservandoUpdatedAt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM Reparacion"), eq(Integer.class), any(Object[].class))).thenReturn(2);
        dao(jdbc).corregirCliente(IMEI, List.of("R20261002_1", "R20261002_2"), 7);
        verify(jdbc).update(eq("UPDATE Reparacion SET ID_CLI = ?, UPDATED_AT = UPDATED_AT"
                + " WHERE IMEI = ? AND FECHA_FIN IS NOT NULL AND ID_REP IN (?,?)"), eq(7), eq(IMEI), eq("R20261002_1"), eq("R20261002_2"));
    }

    @Test void siAlgunoNoEstaCerradoONoEsDelImeiEs409YNoCambiaNada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM Reparacion"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> dao(jdbc).corregirCliente(IMEI, List.of("R20261002_1", "A20261002_9"), null));
        assertEquals(409, e.getStatusCode().value());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
```

`ReparacionControllerCorregirClienteTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReparacionControllerCorregirClienteTest {

    private static final String IMEI = "355400000000111";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class),
            mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class));
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    private int statusDe(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test void corrigeYDejaUnaLineaPorTrabajoConAnteriorYNuevo() {
        Map<String, Integer> antes = new HashMap<>();
        antes.put("R20261002_1", 19);
        antes.put("A20261002_9", null);
        when(dao.existeCliente(7)).thenReturn(true);
        when(dao.getClienteGuardado(List.of("R20261002_1", "A20261002_9"))).thenReturn(antes);
        ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of("R20261002_1", "A20261002_9"), 7), admin);
        verify(dao).corregirCliente(IMEI, List.of("R20261002_1", "A20261002_9"), 7);
        verify(logDao).insertar(1, "CORREGIR_CLIENTE_TRABAJO", "ID_REP: R20261002_1, IMEI: " + IMEI + ", ID_CLI: 19 → 7");
        verify(logDao).insertar(1, "CORREGIR_CLIENTE_TRABAJO", "ID_REP: A20261002_9, IMEI: " + IMEI + ", ID_CLI: — → 7");
    }

    @Test void aSinClienteSeApuntaConGuion() {
        when(dao.getClienteGuardado(List.of("R20261002_1"))).thenReturn(Map.of("R20261002_1", 19));
        ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of("R20261002_1"), null), admin);
        verify(dao).corregirCliente(IMEI, List.of("R20261002_1"), null);
        verify(logDao).insertar(1, "CORREGIR_CLIENTE_TRABAJO", "ID_REP: R20261002_1, IMEI: " + IMEI + ", ID_CLI: 19 → —");
    }

    @Test void listaVaciaOClienteInexistenteEs400SinTocarNada() {
        assertEquals(400, statusDe(() -> ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of(), 7), admin)));
        when(dao.existeCliente(99)).thenReturn(false);
        assertEquals(400, statusDe(() -> ctl.corregirCliente(new ReparacionController.CorregirClienteRequest(IMEI, List.of("R20261002_1"), 99), admin)));
        verify(dao, never()).corregirCliente(anyString(), anyList(), any());
        verifyNoInteractions(logDao);
    }

    @Test void soloSupertecnicoYAdmin() throws Exception {
        for (String metodo : List.of("getCerradosPorImei", "corregirCliente")) {
            PreAuthorize p = java.util.Arrays.stream(ReparacionController.class.getMethods())
                    .filter(m -> m.getName().equals(metodo)).findFirst().orElseThrow().getAnnotation(PreAuthorize.class);
            assertEquals("hasAnyRole('SUPERTECNICO','ADMIN')", p.value(), metodo);
        }
    }
}
```

En `OpenApiContractTest.java`, en `elContratoPublicaLosEsquemasDeLaWeb`, junto a las otras comprobaciones de rutas, añadir:

```java
        assertTrue(paths.has("/api/reparaciones/imei/{imei}/cerrados"), "falta GET /imei/{imei}/cerrados (0.9.8)");
        assertTrue(paths.path("/api/reparaciones/cliente").has("patch"), "falta PATCH /api/reparaciones/cliente (0.9.8)");
        assertTrue(esquemas.has("TrabajoCerrado"), "falta el esquema TrabajoCerrado (0.9.8)");
```

(usar los nombres de variable `paths`/`esquemas` que ya tiene ese método).

Run: `mvn -q test -Dtest='ReparacionDAOCorregirClienteTest,ReparacionControllerCorregirClienteTest'`
Expected: no compila (faltan `getCerradosPorImei`, `corregirCliente`, `existeCliente`, `getClienteGuardado`, el record y la ruta).

- [ ] **Step 3: Modelo**

`TrabajoCerrado.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Trabajo cerrado de un IMEI para el modal «Corregir cliente» (spec 0.9.8 añadido §3.1). */
public record TrabajoCerrado(
        String idRep,
        LocalDateTime fechaFin,
        @Schema(nullable = true) String descripcion,
        @Schema(nullable = true) Integer idCli,
        @Schema(nullable = true) String cliente) {}
```

- [ ] **Step 4: DAO**

En `ReparacionDAO.java`, en la sección de lectura (por ejemplo, tras `getAsignacionesPorImei`):

```java
    /** Trabajos cerrados de un IMEI, de cualquier tipo, con su cliente guardado, del más reciente al más antiguo
     *  (spec 0.9.8 añadido §3.1). La descripción: tipo de pieza en R/G, «Pulido» en P, «Asignación» en las A… */
    public List<TrabajoCerrado> getCerradosPorImei(String imei) {
        return jdbc.query(
                "SELECT r.ID_REP, r.FECHA_FIN, r.ID_CLI, cli.NOMBRE AS CLIENTE," +
                " CASE WHEN r.ID_REP LIKE 'P%' THEN 'Pulido' WHEN r.ID_REP LIKE 'A%' THEN 'Asignación' ELSE c.TIPO END AS DESCRIPCION" +
                " FROM Reparacion r" +
                " LEFT JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0" +
                " LEFT JOIN Componente c ON c.ID_COM = rc.ID_COM" +
                " LEFT JOIN Cliente cli ON cli.ID_CLI = r.ID_CLI" +
                " WHERE r.IMEI = ? AND r.FECHA_FIN IS NOT NULL" +
                " ORDER BY r.FECHA_FIN DESC, r.ID_REP DESC",
                (rs, n) -> new TrabajoCerrado(rs.getString("ID_REP"), rs.getTimestamp("FECHA_FIN").toLocalDateTime(),
                        rs.getString("DESCRIPCION"), (Integer) rs.getObject("ID_CLI"), rs.getString("CLIENTE")),
                imei);
    }

    /** Cliente guardado de cada trabajo (null = sin cliente), para el registro de «Corregir cliente». */
    public Map<String, Integer> getClienteGuardado(List<String> idReps) {
        String marcas = String.join(",", Collections.nCopies(idReps.size(), "?"));
        Map<String, Integer> out = new HashMap<>();
        jdbc.query("SELECT ID_REP, ID_CLI FROM Reparacion WHERE ID_REP IN (" + marcas + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> out.put(rs.getString("ID_REP"), (Integer) rs.getObject("ID_CLI")),
                idReps.toArray());
        return out;
    }

    public boolean existeCliente(int idCli) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM Cliente WHERE ID_CLI = ?", Integer.class, idCli);
        return n != null && n > 0;
    }
```

En la sección de escritura:

```java
    /** «Corregir cliente» (spec 0.9.8 añadido §3.2): todo o nada. Si alguno no existe, no es del IMEI o no está cerrado,
     *  409 sin cambiar nada. UPDATED_AT se conserva: es el dato derivado del cierre, no una edición. */
    @Transactional
    public void corregirCliente(String imei, List<String> idReps, Integer idCli) {
        String marcas = String.join(",", Collections.nCopies(idReps.size(), "?"));
        List<Object> filtro = new ArrayList<>();
        filtro.add(imei);
        filtro.addAll(idReps);
        Integer cerrados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND FECHA_FIN IS NOT NULL AND ID_REP IN (" + marcas + ") FOR UPDATE",
                Integer.class, filtro.toArray());
        if (cerrados == null || cerrados != idReps.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Algún trabajo ya no está cerrado o no es de este teléfono");
        }
        List<Object> params = new ArrayList<>();
        params.add(idCli);
        params.addAll(filtro);
        jdbc.update("UPDATE Reparacion SET ID_CLI = ?, UPDATED_AT = UPDATED_AT"
                + " WHERE IMEI = ? AND FECHA_FIN IS NOT NULL AND ID_REP IN (" + marcas + ")", params.toArray());
    }
```

(`java.util.*` ya está importado; añadir `import com.reparaciones.servidor.model.TrabajoCerrado;` si `model.*` no lo cubre — sí lo cubre.)

- [ ] **Step 5: Controlador**

En `ReparacionController.java` (junto a las otras rutas de historial/IMEI):

```java
    /** Trabajos cerrados de un IMEI para el modal «Corregir cliente» (spec 0.9.8 añadido §3.1). */
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/imei/{imei}/cerrados")
    public List<TrabajoCerrado> getCerradosPorImei(@PathVariable String imei) {
        return dao.getCerradosPorImei(imei);
    }

    /** Corrige el cliente guardado de unos trabajos cerrados de un IMEI (spec 0.9.8 añadido §3.2). */
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @PatchMapping("/cliente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void corregirCliente(@RequestBody CorregirClienteRequest req,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (req.idReps() == null || req.idReps().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay trabajos que corregir");
        }
        if (req.idCli() != null && !dao.existeCliente(req.idCli())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no existe");
        }
        Map<String, Integer> antes = dao.getClienteGuardado(req.idReps());
        dao.corregirCliente(req.imei(), req.idReps(), req.idCli());
        for (String idRep : req.idReps()) {
            logDao.insertar(principal.getIdUsu(), "CORREGIR_CLIENTE_TRABAJO",
                    "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", ID_CLI: " + idCliTexto(antes.get(idRep)) + " → " + idCliTexto(req.idCli()));
        }
    }

    private static String idCliTexto(Integer idCli) {
        return idCli == null ? "—" : String.valueOf(idCli);
    }

    record CorregirClienteRequest(String imei, List<String> idReps, @Schema(nullable = true) Integer idCli) {}
```

(imports que falten: `java.util.Map`, `com.reparaciones.servidor.model.TrabajoCerrado`, `io.swagger.v3.oas.annotations.media.Schema`; comprobar los que ya hay. El nombre del esquema en el contrato saldrá como `ReparacionCorregirClienteRequest` o `CorregirClienteRequest` según la configuración de springdoc del proyecto: anotar en el informe cuál.)

- [ ] **Step 6: Tests y suite**

Run: `mvn -q test -Dtest='ReparacionDAOCorregirClienteTest,ReparacionControllerCorregirClienteTest,OpenApiContractTest'`
Expected: PASS.
Run: `mvn -q test`
Expected: todo en verde (720 + los nuevos); `target/openapi.json` regenerado con las dos rutas.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/TrabajoCerrado.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOCorregirClienteTest.java src/test/java/com/reparaciones/servidor/controller/ReparacionControllerCorregirClienteTest.java
git commit -m "feat: listar y corregir el cliente guardado de los trabajos cerrados de un imei"
```

---

### Task 2: Web — columna, filtro, CSV y «Copiar celda» del cliente

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json` y `src/shared/api/schema.d.ts` (copiados/regenerados), `src/shared/api/client.ts` (export del tipo `TrabajoCerrado`)
- Modify: `src/modules/taller/historial/columnasTrabajo.tsx`, `src/modules/taller/historial/csvTrabajos.ts`, `src/modules/taller/imeis/csvImeis.ts`
- Modify: `src/modules/taller/estado.ts`, `src/modules/taller/historial/HistorialPage.tsx`
- Modify: `src/modules/taller/lib/filtros.ts` (helper `opcionesClientes`), `src/modules/taller/imeis/agrupacion.ts` (usarlo)
- Test: `src/modules/taller/historial/columnasTrabajo.test.ts`, `src/modules/taller/historial/HistorialPage.test.tsx`, `src/modules/taller/lib/filtros.test.ts`, y los tests de CSV que comprueben cabeceras

**Interfaces:**
- Consumes: `target/openapi.json` de la Task 1.
- Produces: `opcionesClientes(nombres: (string | null | undefined)[]): string[]` en `lib/filtros.ts`; `FiltrosHistorial.clientes: Set<string>`; tipo `TrabajoCerrado` exportado desde `@/shared/api/client` para la Task 3.

- [ ] **Step 1: Rama, contrato y tipos**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout -b feature/cliente-en-historial
cp ../gestion-reparaciones-servidor/target/openapi.json api/openapi.json   # tal cual: diff mínimo
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
```

En `src/shared/api/client.ts`, junto a `export type ReparacionResumen = …`, añadir:

```ts
export type TrabajoCerrado = components['schemas']['TrabajoCerrado']
```

- [ ] **Step 2: Tests que fallan**

En `lib/filtros.test.ts`:

```ts
it('opcionesClientes: alfabético, sin repetidos y "(Sin cliente)" delante solo si hay vacíos', () => {
  expect(opcionesClientes(['WEB', 'AMAZON', 'WEB'])).toEqual(['AMAZON', 'WEB'])
  expect(opcionesClientes(['WEB', null, '', 'AMAZON'])).toEqual([SIN_CLIENTE, 'AMAZON', 'WEB'])
})
```

En `historial/columnasTrabajo.test.ts` (patrón de sus tests actuales): la columna `cliente` existe con cabecera «Cliente» justo después de `modelo`, y `textoCeldaTrabajo(resumen({ cliente: 'WEB' }), 'cliente', 'dd/MM/yyyy')` es `'WEB'` (y `''` con `cliente: null`).

En `historial/HistorialPage.test.tsx` (con sus `filas`, dándoles `cliente`): el desplegable «Cliente» lista los clientes de lo cargado con «(Sin cliente)» delante; marcar uno deja solo sus filas; «Limpiar filtros» lo vacía; el CSV del Historial lleva la columna «Cliente» tras «IMEI». Ajustar las aserciones existentes de cabeceras de CSV (Historial y detalle de IMEIs) a la columna nueva, sin quitar ninguna comprobación.

Run: `npx vitest run src/modules/taller/lib/filtros.test.ts src/modules/taller/historial/columnasTrabajo.test.ts src/modules/taller/historial/HistorialPage.test.tsx`
Expected: FAIL en los casos nuevos.

- [ ] **Step 3: Implementación**

`lib/filtros.ts`, tras `pasaCliente`:

```ts
/** Opciones del filtro «Cliente»: nombres distintos en orden alfabético, con "(Sin cliente)" delante si hay vacíos. */
export function opcionesClientes(nombres: (string | null | undefined)[]): string[] {
  const unicos = new Set<string>()
  let sinCliente = false
  for (const n of nombres) {
    if (n) unicos.add(n)
    else sinCliente = true
  }
  const lista = [...unicos].sort((a, b) => a.localeCompare(b, 'es'))
  return sinCliente ? [SIN_CLIENTE, ...lista] : lista
}
```

`imeis/agrupacion.ts`: el cuerpo de `opcionesCliente(trabajos)` pasa a `return opcionesClientes(trabajos.map((t) => t.clienteTelefono))` (mismo comentario; importar `opcionesClientes` de `../lib/filtros` y quitar el import de `SIN_CLIENTE` si deja de usarse).

`historial/columnasTrabajo.tsx`, tras la columna `modelo`:

```tsx
    { id: 'cliente', header: 'Cliente', size: 110, accessorFn: (r) => r.cliente ?? '—' },
```

y en `textoCeldaTrabajo`, tras `case 'modelo'`:

```tsx
    case 'cliente': return rep.cliente ?? ''
```

`historial/csvTrabajos.ts`: cabecera `'Cliente'` justo después de `'IMEI'` y valor `r.cliente ?? ''` justo después de `textoForzado(r.imei)`.

`imeis/csvImeis.ts` (`CABECERAS_DETALLE` / `filaDetalle`): igual, `'Cliente'` tras `'IMEI'` y `r.cliente ?? ''` tras `textoForzado(r.imei)`.

`estado.ts`:

```ts
export type FiltrosHistorial = { tecnicos: Set<number>; piezas: Set<string>; clientes: Set<string>; desde: string; hasta: string; incidencias: Set<EstadoIncidencia> }
const historialVacio = (): FiltrosHistorial => ({ tecnicos: new Set(), piezas: new Set(), clientes: new Set(), desde: '', hasta: '', incidencias: new Set() })
```

`historial/HistorialPage.tsx`:
- `const clientes = useMemo(() => opcionesClientes(data.map((r) => r.cliente)), [data])` junto a `piezas`.
- En `visibles`, añadir `&& pasaCliente(r.cliente, filtros.clientes)`.
- Tras el `MultiSelect` de «Pieza»:

```tsx
        <MultiSelect opciones={clientes} clave={(c) => c} etiqueta={(c) => c} seleccion={filtros.clientes} onChange={(s) => setFiltros({ ...filtros, clientes: s })} textoVacio="Cliente" textoPlural={(n) => `${n} clientes`} className="min-w-[130px]" />
```

- En `limpiar`, añadir `clientes: new Set()`.

- [ ] **Step 4: Tests, tipos, lint**

Run: `npx vitest run src/modules/taller` y después `npx tsc -b && npm run lint`
Expected: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/taller
git commit -m "feat: columna, filtro y csv del cliente de cada trabajo en el historial y el detalle de imeis"
```

---

### Task 3: Web — «Corregir cliente»

**Files:**
- Modify: `src/modules/taller/api.ts` (dos hooks)
- Create: `src/modules/taller/componentes/CorregirClienteDialog.tsx`
- Create: `src/modules/taller/componentes/CorregirClienteDialog.test.tsx`
- Modify: `src/modules/taller/componentes/MenuHistorial.tsx`, `src/modules/taller/componentes/useAccionesTrabajo.tsx`
- Modify: `src/modules/taller/historial/HistorialPage.tsx`, `src/modules/taller/imeis/ImeiDetallePage.tsx` (pasar el permiso nuevo)
- Test: los de `MenuHistorial` (si existen) / `HistorialPage.test.tsx` / `ImeiDetallePage.test.tsx`

**Interfaces:**
- Consumes: `TrabajoCerrado` (Task 2), rutas de la Task 1.
- Produces: `AccionesHistorial.corregirCliente(rep)`; prop `puedeCorregirCliente: boolean` en `MenuHistorial`.

- [ ] **Step 1: Hooks**

En `api.ts` (importar `TrabajoCerrado` de `@/shared/api/client`):

```ts
export function useTrabajosCerrados(imei: string | null) {
  return useQuery({
    queryKey: ['trabajos-cerrados', imei],
    enabled: imei !== null,
    queryFn: async (): Promise<TrabajoCerrado[]> =>
      (await api.GET('/api/reparaciones/imei/{imei}/cerrados', { params: { path: { imei: imei ?? '' } } })).data ?? [],
  })
}
/** «Corregir cliente» (spec 0.9.8 añadido): el error lo muestra el modal (meta.silenciarError). */
export function useCorregirCliente() {
  const recargar = useRecargarSinEsperar(...HISTORIALES)
  return useMutation({
    mutationFn: ({ imei, idReps, idCli }: { imei: string; idReps: string[]; idCli: number | null }) =>
      api.PATCH('/api/reparaciones/cliente', { body: { imei, idReps, idCli } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}
```

- [ ] **Step 2: Test del modal que falla**

`CorregirClienteDialog.test.tsx` (patrón de `HistorialPage.test.tsx`: `server.use(http.get/patch…)`, `renderConProviders(…, { sesion: SESION_SUPER })`, `resumen` de `../test/fabrica`):

```tsx
const IMEI = '355400000000111'
const cerrados = [
  { idRep: 'R20261002_1', fechaFin: '2026-10-02T09:30:00', descripcion: 'lcdi16', idCli: 19, cliente: 'AMAZON' },
  { idRep: 'R20261002_2', fechaFin: '2026-10-02T09:30:00', descripcion: 'bati16', idCli: 19, cliente: 'AMAZON' },
  { idRep: 'A20261001_7', fechaFin: '2026-10-02T09:30:01', descripcion: 'Asignación', idCli: 19, cliente: 'AMAZON' },
  { idRep: 'R20260714_5', fechaFin: '2026-07-14T10:00:00', descripcion: 'chai16negro', idCli: 19, cliente: 'AMAZON' },
  { idRep: 'R20260601_1', fechaFin: '2026-06-01T10:00:00', descripcion: 'otroi16', idCli: null, cliente: null },
]
const activos = [{ idCli: 19, nombre: 'AMAZON', activo: true, updatedAt: null }, { idCli: 2, nombre: 'WEB', activo: true, updatedAt: null }]
```

Casos:
1. Con `rep = resumen({ idRep: 'R20261002_1', imei: IMEI, cliente: 'AMAZON', fechaFin: '2026-10-02T09:30:00' })`: lista **solo** los 4 con `idCli` 19 (no el de sin cliente); marcados de entrada los 3 del 02/10; el de julio sin marcar.
2. El botón «Corregir 3» está deshabilitado sin cliente elegido, y si se elige AMAZON (el mismo guardado); con «WEB» se habilita.
3. Desmarcar todos deshabilita el botón.
4. Al pulsar «Corregir 3» con WEB, el `PATCH /api/reparaciones/cliente` recibe `{ imei: IMEI, idReps: ['R20261002_1','R20261002_2','A20261001_7'], idCli: 2 }` (orden de la lista) y se llama a `onCerrar`.
5. «— Sin cliente —» envía `idCli: null`.
6. Si el PATCH responde 409 con mensaje, se muestra «No se pudo corregir: <mensaje>» y el modal sigue abierto.

Run: `npx vitest run src/modules/taller/componentes/CorregirClienteDialog.test.tsx`
Expected: FAIL (no existe el componente).

- [ ] **Step 3: El modal**

`CorregirClienteDialog.tsx`:

```tsx
import { useLayoutEffect, useMemo, useState } from 'react'
import type { ReparacionResumen } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { fechaLocal, formatear } from '@/shared/lib/fechas'
import { useCerrojoEnvio } from '@/shared/lib/useCerrojoEnvio'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { useClientesActivos, useCorregirCliente, useTrabajosCerrados } from '../api'

const SIN = 'SIN'

type Props = { rep: ReparacionResumen | null; onCerrar: () => void }

/** «Corregir cliente» (spec 0.9.8 añadido §4): cliente correcto y, con casillas, los trabajos cerrados del mismo IMEI que
 *  tienen guardado el mismo cliente que el elegido; de entrada, marcados el elegido y los del mismo día. No toca el
 *  cliente actual del teléfono. Se cierra cuando la corrección sale bien; si falla, sigue abierto con el mensaje. */
export function CorregirClienteDialog({ rep, onCerrar }: Props) {
  const { mostrarError } = useAlerta()
  const { data: cerrados } = useTrabajosCerrados(rep?.imei ?? null)
  const { data: activos = [] } = useClientesActivos()
  const corregir = useCorregirCliente()
  const enviar = useCerrojoEnvio({ abierto: rep !== null, enviando: corregir.isPending })
  const [cliente, setCliente] = useState('')
  const [marcados, setMarcados] = useState<Set<string> | null>(null)

  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el formulario al abrir con otra fila (patrón de DialogoIncidencia)
    if (rep) { setCliente(''); setMarcados(null) }
  }, [rep])

  const elegido = cerrados?.find((t) => t.idRep === rep?.idRep)
  const idCliGuardado = elegido ? (elegido.idCli ?? null) : undefined
  const candidatos = useMemo(
    () => (cerrados && idCliGuardado !== undefined ? cerrados.filter((t) => (t.idCli ?? null) === idCliGuardado) : []),
    [cerrados, idCliGuardado],
  )
  // De entrada: el elegido y los del mismo día (piezas de la misma reparación). Se fija una vez por apertura.
  const diaElegido = fechaLocal(elegido?.fechaFin)
  const seleccion = marcados ?? new Set(candidatos.filter((t) => t.idRep === rep?.idRep || fechaLocal(t.fechaFin) === diaElegido).map((t) => t.idRep))

  const idCliNuevo = cliente === SIN ? null : cliente === '' ? undefined : Number(cliente)
  const listo = idCliNuevo !== undefined && idCliNuevo !== idCliGuardado && seleccion.size > 0

  function alternar(idRep: string) {
    const s = new Set(seleccion)
    if (s.has(idRep)) s.delete(idRep)
    else s.add(idRep)
    setMarcados(s)
  }

  function confirmar() {
    if (!rep || idCliNuevo === undefined) return
    const idReps = candidatos.map((t) => t.idRep).filter((id) => seleccion.has(id))
    corregir.mutate({ imei: rep.imei, idReps, idCli: idCliNuevo }, {
      onSuccess: onCerrar,
      onError: (e) => { if (!esErrorGestionadoGlobalmente(e)) mostrarError(`No se pudo corregir: ${mensajeDeError(e)}`) },
    })
  }

  return (
    <Dialog open={rep !== null} onOpenChange={(o) => { if (!o && !corregir.isPending) onCerrar() }}>
      <DialogContent aria-describedby={undefined} className="max-w-[560px] gap-2 bg-fondo-input p-4">
        <DialogHeader><DialogTitle className="text-[14px] font-bold text-azul-medio">Corregir cliente</DialogTitle></DialogHeader>
        <p className="text-[12px] text-azul-medio">IMEI {rep?.imei} · guardado ahora: {elegido?.cliente ?? '— Sin cliente —'}</p>
        <Label htmlFor="corregir-cliente" className="text-[12px]">Cliente correcto</Label>
        <select id="corregir-cliente" value={cliente} onChange={(e) => setCliente(e.target.value)} className="h-9 w-full rounded border border-gris-borde bg-superficie px-2 text-[13px]">
          <option value="">Selecciona cliente</option>
          <option value={SIN}>— Sin cliente —</option>
          {activos.map((c) => <option key={c.idCli} value={String(c.idCli)}>{c.nombre}</option>)}
        </select>
        <p className="text-[12px] text-azul-medio">Trabajos cerrados de este IMEI con {elegido?.cliente ?? 'sin cliente'}:</p>
        <ul className="max-h-[240px] overflow-y-auto rounded border border-gris-borde bg-superficie p-2">
          {candidatos.map((t) => (
            <li key={t.idRep}>
              <label className="flex items-center gap-2 text-[12px]">
                <input type="checkbox" checked={seleccion.has(t.idRep)} onChange={() => alternar(t.idRep)} />
                <span className="font-mono">{t.idRep}</span>
                <span>{formatear(t.fechaFin, 'dd/MM/yyyy')}</span>
                <span className="text-azul-gris">{t.descripcion ?? ''}</span>
              </label>
            </li>
          ))}
        </ul>
        <Button disabled={!listo || corregir.isPending} onClick={() => enviar(confirmar)} className="h-auto w-full rounded bg-fila-reparado-ico py-2 text-[12px] text-superficie hover:bg-fila-reparado-ico/90 disabled:bg-gris-disabled disabled:text-gris-borde disabled:opacity-100">
          Corregir {seleccion.size}
        </Button>
        <DialogFooter><Button variant="outline" disabled={corregir.isPending} onClick={onCerrar}>Cerrar</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

(Si `formatear` no admite `'dd/MM/yyyy'` como `Patron`, usar el patrón que sí admita y ajustar el test; si `esErrorGestionadoGlobalmente`/`mensajeDeError` están en otra ruta, usar la de `useAccionesTrabajo.tsx`.)

- [ ] **Step 4: Menú y acciones**

`MenuHistorial.tsx`:
- `AccionesHistorial` gana `corregirCliente: (rep: ReparacionResumen) => void`.
- Prop nueva `puedeCorregirCliente: boolean`.
- Sin permiso de edición pero con `puedeCorregirCliente` y fila cerrada (`rep.fechaFin`): el menú es «Copiar celda» + separador + «Corregir cliente».
- Con permiso de edición: tras «Borrar», si `puedeCorregirCliente && rep.fechaFin`, `<ContextMenuItem onSelect={() => acciones.corregirCliente(rep)}>Corregir cliente</ContextMenuItem>`.

`useAccionesTrabajo.tsx`: estado `const [aCorregir, setACorregir] = useState<ReparacionResumen | null>(null)`; `acciones` gana `corregirCliente: setACorregir`; en `dialogos`, `<CorregirClienteDialog rep={aCorregir} onCerrar={() => setACorregir(null)} />`.

`HistorialPage.tsx`: pasar `puedeCorregirCliente={global}` a `MenuHistorial` (ya existe `const global = esAdminOSuperTecnico(sesion)`). `ImeiDetallePage.tsx`: `puedeCorregirCliente={esAdminOSuperTecnico(sesion)}` (añadir `esAdminOSuperTecnico` al import de `@/shared/session/storage`).

Tests: en `HistorialPage.test.tsx`, con `SESION_ADMIN` el menú de una fila cerrada ofrece «Corregir cliente» (y no «Editar»/«Borrar»); con `SESION_TEC`, no lo ofrece; con `SESION_SUPER`, lo ofrece junto a «Editar»/«Borrar» y abre el modal.

- [ ] **Step 5: Tests, tipos, lint, suite**

Run: `npx vitest run src/modules/taller` y después `npx tsc -b && npm run lint && npx vitest run`
Expected: todo en verde (si hay timeouts sueltos por carga, repetir aparte esos ficheros y anotarlo).

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller
git commit -m "feat: corregir el cliente guardado de los trabajos cerrados desde el historial y el detalle de imeis"
```

---

### Task 4: Cierre, entrega y preproducción

**Files:**
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.8]`)
- Modify: `docs/novedades/NOVEDADES-v0.9.8.md` (raíz)
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_preprod.md` (registro de la sesión de la 0.9.8)

- [ ] **Step 1: CHANGELOG y novedades**

En `CHANGELOG.md`, dentro de `## [0.9.8]`, tras el punto de la «Vista IMEIs», añadir:

```markdown
- **Historial con el cliente de cada trabajo:** columna **Cliente** (también en el detalle de un IMEI), filtro «Cliente» y columna en el CSV.
- **Corregir cliente** (supertécnicos y administrador): clic derecho en un trabajo cerrado → «Corregir cliente». Se elige el cliente correcto y qué trabajos cerrados del mismo IMEI con ese cliente se corrigen (de entrada, los del mismo día). El cliente actual del teléfono no cambia. Queda en el registro.
```

En `docs/novedades/NOVEDADES-v0.9.8.md`, tras la sección «🧾 Historial: el cliente de cada trabajo», añadir:

```markdown
- En el **Historial** y en el **detalle de un IMEI** hay una columna **Cliente** y un filtro «Cliente».
- ¿Se apuntó mal el cliente? Clic derecho en el trabajo → **«Corregir cliente»**: eliges el cliente correcto y qué
  trabajos de ese teléfono se corrigen (de entrada salen marcados los del mismo día). Supertécnicos y administrador.
```

- [ ] **Step 2: Suites**

Servidor `mvn -q test`; web `npx tsc -b && npm run lint && npx vitest run && npm run build`. Expected: verde.

- [ ] **Step 3: Commits**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git add CHANGELOG.md && git commit -m "docs: changelog 0.9.8 con el cliente en el historial y corregir cliente"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.8.md && git commit -m "docs: novedades 0.9.8 con el cliente en el historial y corregir cliente"
```

- [ ] **Step 4: Entrega (con OK del usuario en cada paso)**

1. Merge `--no-ff` de `feature/cliente-en-historial` a `main` en servidor y web (`merge: cliente en el historial y corregir cliente (0.9.8)`), borrar las ramas.
2. Push de `main` en los dos repos.
3. Gitlinks en la raíz (`chore: gitlinks servidor y web tras el cliente en el historial (0.9.8)`) y push de la raíz.

- [ ] **Step 5: Preproducción (usuario ejecuta, Claude revisa)**

`ssh preprod`: `git -C gestion-reparaciones-servidor pull --ff-only`, `git -C gestion-reparaciones-web pull --ff-only`, `docker compose up -d --build`, `Started App` sin errores. Sin migración.
Claude por la API: `/version.json` 0.9.8; `GET /api/reparaciones/imei/<IMEI de prueba>/cerrados` con el administrador.
A mano: columna y filtro «Cliente» en el Historial; «Corregir cliente» en un trabajo de prueba (cambiar y deshacer), y la línea `CORREGIR_CLIENTE_TRABAJO` en «Ver logs».
Después, el **bloque 6** de `Apuntes/preprod/v098-preprod.md` (volcado de `init.sql`) y la entrada del registro en `Apuntes/despliegue_preprod.md`.
