# 0.9.21 — Descripción completa donde pone «Modelo» · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que las listas del taller (Asignaciones, Pendientes, Pulidos pendientes, Historial de trabajos y de pulidos, IMEIs y
la barra del detalle del IMEI) enseñen «iPhone 13 128GB Azul» donde hoy ponen «iPhone 13», con la capacidad y el color que
vale de NSYS, y que los CSV con «Modelo» ganen «Capacidad» y «Color».

**Architecture:**
- **Servidor** (sin base de datos):
  - `ReparacionResumen` gana `capacidad` y `color` (nullables, al final);
  - `TallerNsys.colorDeTodos()`: el color que vale de cada móvil con los vigentes del taller en una consulta, sin el maestro;
  - servicio nuevo `nsys.DescripcionMovil.rellenar(filas)`: junta los IMEIs sin repetir, lee el espejo por tandas
    (`NsysDAO.porImeis`, el lote más reciente por `claveCruce`), pone capacidad y color en cada fila y, si algo falla, deja la
    lista como hoy y lo apunta en el log;
  - las seis rutas de asignaciones e historial de `ReparacionController`, `GlassController` y `PulidoController` lo llaman.
- **Web:**
  - contrato y tipos regenerados; `descripcionMovil(nombre, capacidad, color)` en `src/shared/lib/telefonoNsys.ts` sustituye
    las cuatro copias de «modelo capacidad»;
  - `descripcionTrabajo(r)` del taller (`modules/taller/lib/modelos.ts`) en las columnas «Modelo», «Copiar celda», la
    confirmación de «Completar seleccionados» y la barra del detalle; `movilDe` (el modelo, la capacidad y el color de un IMEI)
    en IMEIs;
  - «Capacidad» y «Color» detrás de «Modelo» en `reparaciones_pendientes`, `pulidos_pendientes`, `historial_pulidos` y
    `agrupado_resumen`.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB. Pruebas con JUnit 5, Mockito y MockMvc (sin base de datos).
- **Web:** React 19 + TypeScript, TanStack Query/Table, openapi-fetch. Pruebas con Vitest, Testing Library, user-event y MSW.

**Spec:** `docs/superpowers/specs/2026-10-22-v0921-descripcion-completa-design.md` (raíz). Exploración del código (de dónde sale
hoy el modelo en cada pantalla, con fichero:línea): `.superpowers/sdd/v0921-desc/exploracion.md` (raíz).

## Global Constraints

**Versión, código de partida y base de datos**
- Va en la **0.9.21**, que aún no está en producción, junto con la «Corrección en NSYS», el panel de carga más grande y el SKU
  del taller (piezas 1 y 2), ya mergeados en los `main` (servidor `bdcec23` y web `16c3413` el 22/10). Toca **servidor y web**;
  **sin base de datos** (ni migración ni cambios en `crear_bd.sql`).
- La web ya está en `0.9.21` (`package.json`): **no** se sube la versión. La entrada `## [0.9.21]` del CHANGELOG de la web y
  `docs/novedades/NOVEDADES-v0.9.21.md` (raíz) ya existen: esta pieza añade lo suyo.
- Compatibilidad: la web 0.9.20 de producción ignora los dos campos nuevos; la web 0.9.21 con un servidor sin ellos ve
  `undefined` y enseña lo de hoy (`descripcionMovil` quita las partes vacías).

**Ramas, worktrees y registro**
- Rama **`dev_v0921_descripcion`** en los dos repos, desde `origin/main` **en el momento de crearla** (`git fetch` antes y mirar
  el commit; no dar por hecho ningún hash), en worktrees:
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0921-desc` (lo crea la Task 1).
  - Web: `/c/Users/dev/Documents/wt/web-0921-desc`, con `npm ci` dentro (lo crea la Task 3).
  - **Los clones normales no se tocan** (`gestion-reparaciones-servidor` y `gestion-reparaciones-web` de la raíz; los usan otras
    sesiones). Las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` del plan son las de los worktrees;
    `docs/…` sin prefijo es la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0921-desc/progress.md`** (raíz), junto a `exploracion.md`.

**Commits, merge y datos de prueba**
- Commits en español **sin tildes**, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- Merge `--no-ff`, push, tags, gitlinks, preproducción y producción: **fuera de este plan** (los hace el controlador, con el OK
  del usuario; ver «Después de este plan»).
- **Repos públicos:** datos de prueba sintéticos: «Técnico 2», IMEI `35000000000xxxx`, el iPhone 13 128GB «Apple iPhone 13
  128GB Blue» de `TallerDePrueba`, colores Azul, Rojo, Negro y Verde. Nunca nombres, IMEIs ni datos reales de NSYS.

**Reglas** (spec §1-§3)
- **Texto:** `<nombre del modelo> <capacidad> <color>`, un espacio entre partes y sin las que falten: «iPhone 13 128GB Azul»,
  «iPhone 13 128GB», «iPhone 13». **Sin nombre del modelo** (`Telefono.MODELO` vacío), vacío como hoy, aunque NSYS sepa la
  capacidad y el color. **Sin grado** y **sin la marca «taller»** en estas listas.
- **Nombre del modelo:** el de hoy en cada lista (`traducirModelo(r.modelo)`, el código de `Telefono.MODELO`). **Capacidad:**
  `Nsys_movil.CAPACIDAD`. **Color:** el que vale (lo del taller vigente o, si no hay, `Nsys_movil.COLOR`). Los dos de la **fila
  más reciente** del IMEI: `NsysDAO.porImeis` trae de cada IMEI el lote más reciente primero y se queda el primero por
  `NsysDAO.claveCruce`. Un IMEI fuera del espejo, sin los dos. En el historial, el color **de hoy**.
- **Quién:** todos los roles, también el TECNICO. Sin rutas nuevas ni permisos nuevos: las seis rutas conservan su
  `FiltroTecnico`.
- **Servidor:** una lectura del espejo por IMEI distinto, en tandas de 500 (`NsysDAO.TANDA_IN`), y **una** de lo del taller
  (todos los vigentes); nunca una consulta por fila y nunca un JOIN en SQL con `Nsys_movil` (intercalaciones distintas). Si la
  lectura falla, la lista sale **como hoy** (sin los dos campos) y un `log.warn` de una línea.
- **Solo las seis rutas** rellenan: `GET /api/reparaciones/asignaciones`, `/api/reparaciones/historial`, `/api/glass/asignaciones`,
  `/api/glass/historial`, `/api/pulidos/asignaciones` y `/api/pulidos/historial`. Las demás que devuelven `ReparacionResumen`
  (`/historial/imei/{imei}`, `completadas-hoy`, carga de técnicos, predicción de glass…) los dejan a `null`.
- **CSV** (se generan en la web): `reparaciones_pendientes`, `pulidos_pendientes`, `historial_pulidos` y `agrupado_resumen` ganan
  «Capacidad» y «Color» **detrás de «Modelo»**, con el valor tal cual (vacío sin NSYS); «Modelo» sigue siendo el nombre solo. Los
  demás (`mis_pendientes`, `historial_*`/`mis_*` de trabajos, `agrupado_<imei>`) no cambian.
- **Las cuatro copias de «modelo capacidad»** (ficha del IMEI, buscador de IMEI, «Por pasar a NSYS» y «Pegar varios IMEIs») pasan
  por `descripcionMovil` **sin cambiar lo que enseñan** (su color sigue aparte, con la marca o en su columna).
- **Anchos de columna:** no cambian (ver «Preguntas para el usuario»).
- **MSW y E2E:** los manejadores por defecto de `src/test/server.ts` devuelven listas vacías: no cambian. Las E2E tampoco: sus
  IMEIs sintéticos no están en el espejo, y la de pulidos compara con `toContainText`.

**Comandos**
- **Servidor** (Git Bash, en `/c/Users/dev/Documents/wt/servidor-0921-desc`):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest='Clase1,Clase2'`). Con `-q`, Maven no escribe nada si todo pasa: el código de
  salida es 0 y no hay `FAIL` ni `ERROR`. `OpenApiContractTest` levanta el contexto entero (pilla un cableado roto) y deja
  `target/openapi.json` para los tipos de la web.
- **Web** (en `/c/Users/dev/Documents/wt/web-0921-desc`): `npx vitest run <rutas>`, `npx tsc -b`, `npm run lint` y, al cerrar,
  `npm run check`. Lint con las reglas del compilador de `eslint-plugin-react-hooks` 7 (nada de `new Date()` ni `Date.now()` en el
  cuerpo de un componente o un hook). **Un módulo no importa de otro**: lo común va a `src/shared`.
- **Inestabilidad conocida de la web:** con la suite entera, algún test puede pasar de los 5 s («orden de llamadas…» de
  `FormularioReparacion`, `NuevoPedidoDialog`, `StockPage`, `HistorialPage`); solos pasan. Si pasa, se repite ese fichero solo y
  se apunta en el registro; no se tocan.

## Ajustes a la spec (se suben a la spec, como §7, en el commit del plan)

1. **`ReparacionResumen` es una clase con setters, no un record:** `capacidad` y `color` van al final con su getter y su setter
   y nacen a `null`. El constructor no cambia, así que los demás `new ReparacionResumen(…)` del repo (`RESUMEN_MAPPER`,
   `CargaTecnicos`, `PrediccionGlass` y sus tests) no se tocan y salen con `null` sin escribirlo.
2. **El color sale de un método nuevo, `TallerNsys.colorDeTodos()`:** la misma consulta única de los vigentes que
   `valedorDeTodos()` y el mismo color, pero **sin cargar nunca el maestro**, que solo hace falta para el SKU (estas listas no lo
   llevan).
3. **Todo o nada:** si falla la lectura del espejo o la del taller, ninguna fila lleva capacidad ni color (no se enseña el color de
   NSYS sin saber si el taller lo cambió). Si ningún IMEI de la lista está en el espejo, no se lee el taller.
4. **El móvil de un IMEI en la vista IMEIs** (modelo del grupo y barra del detalle) es el primer modelo, la primera capacidad y el
   primer color no vacíos de sus trabajos (`movilDe`), como ya se hacía con el modelo: si una de las tres listas no pudo leer
   NSYS, vale lo de otra.
5. **Los CSV de «Pulidos pendientes» e «Historial de pulidos» salen de las páginas a funciones puras** (`pendientes/csvPulidos.ts`
   y `historial/csvTrabajos.ts`) para probarlos sin montar el `AppLayout`; mismo fichero y mismas columnas, más las dos nuevas.
6. **Anchos:** las columnas «Modelo» conservan su ancho; un texto largo se corta con «…» como cualquier celda y «Copiar celda»
   copia el texto entero.

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`; clases en `src/main/java/com/reparaciones/servidor/` y pruebas en
`src/test/java/com/reparaciones/servidor/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `model/ReparacionResumen.java` | `capacidad` y `color` al final | 1 |
| `nsys/TallerNsys.java` + `nsys/TallerNsysTest.java` | `colorDeTodos()` | 1 |
| `nsys/DescripcionMovil.java` (nuevo) + `nsys/DescripcionMovilTest.java` (nuevo) | El relleno de capacidad y color | 1 |
| `controller/ReparacionController.java`, `controller/GlassController.java`, `controller/PulidoController.java` | Las seis rutas | 2 |
| `controller/FiltroTecnicoControllersTest.java` y los 6 tests con `new ReparacionController(…)` | Constructores y rutas | 2 |
| `nsys/DescripcionMovilRutasTest.java` (nuevo), `OpenApiContractTest.java` | Rutas para el técnico y contrato | 2 |
| `docs/api_contract.md` | Convención del contrato | 6 |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/modules/taller/test/fabrica.ts` | Contrato, tipos y fábrica | 3 |
| `src/shared/lib/telefonoNsys.ts` (+ test), `src/modules/taller/lib/modelos.ts` (+ test) | `descripcionMovil` y `descripcionTrabajo` | 3 |
| `src/modules/taller/imeis/FichaMovil.tsx`, `src/modules/telefonos/stock/{BuscadorImei.tsx,porPasar.ts,varios.ts}` | Las cuatro copias | 3 |
| `src/modules/taller/asignaciones/columnas.tsx`, `src/modules/taller/pendientes/{textoCelda.ts,PendientesPage.tsx,PulidosPendientesPage.tsx}` (+ tests; `textoCelda.test.ts` nuevo) | Asignaciones y Pendientes | 4 |
| `src/modules/taller/historial/{columnasTrabajo.tsx,HistorialPulidosPage.tsx}` (+ tests) | Historial | 4 |
| `src/modules/taller/lib/grupoImei.ts`, `src/modules/taller/imeis/{ImeisPage.tsx,ImeiDetallePage.tsx}` (+ tests) | IMEIs y la barra del detalle | 4 |
| `src/modules/taller/asignaciones/csv.ts`, `pendientes/csvPulidos.ts` (nuevo), `historial/csvTrabajos.ts`, `imeis/csvImeis.ts`, las dos páginas de pulidos (+ `csv.test.ts`, `AsignacionesPage.test.tsx`, `csvPulidos.test.ts` y `csvTrabajos.test.ts` nuevos, `ImeisPage.test.tsx`) | CSV | 5 |
| `CHANGELOG.md`, `docs/paridad/{asignaciones,pendientes,historial,imeis}.md` | Documentación | 6 |

**Raíz y apuntes** (fuera de los worktrees): `docs/novedades/NOVEDADES-v0.9.21.md` y `Apuntes/plan-futuro.md` (Task 6).

---

### Task 1: Servidor — capacidad y color en `ReparacionResumen` y el servicio `DescripcionMovil`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/DescripcionMovil.java`
- Create (test): `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilTest.java`
- Modify (test): `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java`

**Interfaces:**
- Consumes (ya en `main`): `NsysDAO.porImeis(Collection<String>)` (tandas de `TANDA_IN` = 500, de cada IMEI el lote más
  reciente primero), `NsysDAO.claveCruce(String)`, `MovilNsys.capacidad()`, `TallerDAO.vigentes()`,
  `TallerNsys.porImei(List<CambioTaller>)` (de paquete), `LoQueVale.valor(List<CambioTaller>, DatoTaller, MovilNsys)` (de paquete)
  y, en los tests, `TallerDePrueba.movil(imei, color, grade)` (un iPhone 13 128GB) y `TallerDePrueba.vigente(id, imei, dato,
  valor, valorNsys)`.
- Produces:
  - `ReparacionResumen`: `String getCapacidad()`, `void setCapacidad(String)`, `String getColor()`, `void setColor(String)`;
    en el contrato, `capacidad` y `color` nullables y al final.
  - `TallerNsys`: `public Function<MovilNsys, String> colorDeTodos()`.
  - `DescripcionMovil` (`@Service`, paquete `com.reparaciones.servidor.nsys`): `public DescripcionMovil(NsysDAO, TallerNsys)` y
    `public List<ReparacionResumen> rellenar(List<ReparacionResumen> filas)`, que cambia las filas en el sitio y devuelve la
    **misma** lista (también si falla o si viene vacía).

- [ ] **Step 1: Worktree del servidor y registro**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git log --oneline -1 origin/main
git worktree add -b dev_v0921_descripcion /c/Users/dev/Documents/wt/servidor-0921-desc origin/main
cd /c/Users/dev/Documents/wt/servidor-0921-desc && git log --oneline -1
```

Expected: `Preparing worktree (new branch 'dev_v0921_descripcion')` y el mismo commit que `origin/main` (el 22/10, `bdcec23`, con
toda la 0.9.21 dentro). Crea `.superpowers/sdd/v0921-desc/progress.md` (raíz) con la rama y ese commit. Línea base:
`mvn -q test` en el worktree, sin fallos (si algo falla ya en `main`, apuntarlo y avisar).

- [ ] **Step 2: Tests (fallan)**

En `src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java`, justo después del test
`unNumeroDeSerieEnMinusculasCruza`, añade:

```java
    @Test void elColorQueValeDeTodosEsElDelValedorYNoLeeElMaestro() {
        String otro = "350000000000002";
        when(taller.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A100", "B"),
                vigente(3, "f2lxabc123", COLOR, "Verde", "Azul")));
        Function<MovilNsys, String> color = servicio.colorDeTodos();
        assertEquals("Rojo", color.apply(movil(IMEI, "Azul", "B")));
        assertEquals("Azul", color.apply(movil(otro, "Azul", "B")), "sin nada del taller, el de NSYS");
        assertEquals("Verde", color.apply(movil("F2LXABC123", "Azul", "B")), "el número de serie cruza sin mirar mayúsculas");
        verify(nsys, never()).cargarProductos();
        verify(taller, times(1)).vigentes();
        assertEquals(servicio.valedorDeTodos().apply(movil(IMEI, "Azul", "B")).color(), color.apply(movil(IMEI, "Azul", "B")));
    }
```

Crea `src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilTest.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** La capacidad y el color de NSYS en las listas del taller (spec 0.9.21 «Descripción completa donde pone «Modelo»» §2.3), con
 *  los DAO mockeados y sin base de datos. Datos inventados: el iPhone 13 128GB de TallerDePrueba. */
class DescripcionMovilTest {

    private static final String IMEI = "350000000000001";
    private static final String OTRO = "350000000000002";
    private static final String DE_CLIENTE = "350000000000003";
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final DescripcionMovil descripcion = new DescripcionMovil(nsys, new TallerNsys(nsys, tallerDao, mock(LogDAO.class)));

    /** Una fila de una lista del taller (asignación o trabajo) de este IMEI, con el modelo del taller «13». */
    private static ReparacionResumen fila(String idRep, String imei) {
        return new ReparacionResumen(idRep, imei, "Técnico 2", null, null, null, null, false, false, null, null, 4, 0, null,
                null, null, 0, false, null, null, "13");
    }

    @Test void poneLaCapacidadYElColorDeNsysYDejaElModeloDelTaller() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        assertSame(filas, descripcion.rellenar(filas));
        assertEquals("128GB", filas.get(0).getCapacidad());
        assertEquals("Azul", filas.get(0).getColor());
        assertEquals("13", filas.get(0).getModelo());
    }

    @SuppressWarnings("unchecked")
    @Test void losImeisRepetidosSeLeenUnaSolaVez() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B"), movil(OTRO, "Negro", "A")));
        // El historial trae una fila por componente: el mismo IMEI sale varias veces.
        List<ReparacionResumen> filas = List.of(fila("R20261022_1", IMEI), fila("R20261022_1", IMEI), fila("R20261022_2", OTRO),
                fila("R20261021_9", IMEI));
        descripcion.rellenar(filas);
        ArgumentCaptor<Collection<String>> imeis = ArgumentCaptor.forClass(Collection.class);
        verify(nsys, times(1)).porImeis(imeis.capture());
        assertEquals(List.of(IMEI, OTRO), List.copyOf(imeis.getValue()));
        verify(tallerDao, times(1)).vigentes();
        assertEquals(List.of("Azul", "Azul", "Negro", "Azul"), filas.stream().map(ReparacionResumen::getColor).toList());
    }

    @Test void masDeQuinientosImeisVanEnTandasYSinNadaEnElEspejoNoSeLeeElTaller() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NsysDAO dao = new NsysDAO(jdbc);
        DescripcionMovil conTandas = new DescripcionMovil(dao, new TallerNsys(dao, tallerDao, mock(LogDAO.class)));
        List<ReparacionResumen> filas = new ArrayList<>();
        for (int n = 0; n < 501; n++) {
            String imei = String.format("3500000000%05d", n);
            filas.add(fila("R20261022_" + n, imei));
            filas.add(fila("R20261022_" + n, imei));
        }
        conTandas.rellenar(filas);
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query"))
                .map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "501 IMEIs distintos en 1002 filas: 500 + 1, nunca una consulta por fila");
        assertTrue(sql.stream().allMatch(s -> s.contains("FROM Nsys_movil WHERE IMEI_SN IN (")), sql.toString());
        verifyNoInteractions(tallerDao);
        assertNull(filas.get(0).getCapacidad());
    }

    @Test void unImeiFueraDelEspejoSeQuedaSinCapacidadNiColor() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI), fila("A20261022_2", DE_CLIENTE));
        descripcion.rellenar(filas);
        assertEquals("128GB", filas.get(0).getCapacidad());
        assertNull(filas.get(1).getCapacidad());
        assertNull(filas.get(1).getColor());
        assertEquals("13", filas.get(1).getModelo());
    }

    @Test void elColorDelTallerMandaSobreElDeNsysSinLeerElMaestro() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(tallerDao.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        descripcion.rellenar(filas);
        assertEquals("Rojo", filas.get(0).getColor());
        assertEquals("128GB", filas.get(0).getCapacidad());
        verify(nsys, never()).cargarProductos();
    }

    @Test void valeElLoteMasRecienteDelImei() {
        // porImeis trae de cada IMEI el lote más reciente primero.
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Verde", "B"), movil(IMEI, "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        descripcion.rellenar(filas);
        assertEquals("Verde", filas.get(0).getColor());
    }

    @Test void unNumeroDeSerieEnMinusculasCruza() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil("F2LXABC123", "Azul", "B")));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", "f2lxabc123"));
        descripcion.rellenar(filas);
        assertEquals("Azul", filas.get(0).getColor());
    }

    @Test void siFallaElEspejoLaListaSaleComoHoy() {
        when(nsys.porImeis(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        assertSame(filas, descripcion.rellenar(filas));
        assertNull(filas.get(0).getCapacidad());
        assertNull(filas.get(0).getColor());
        assertEquals("13", filas.get(0).getModelo());
    }

    @Test void siFallaLoDelTallerNoSeRellenaNada() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(tallerDao.vigentes()).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        List<ReparacionResumen> filas = List.of(fila("A20261022_1", IMEI));
        descripcion.rellenar(filas);
        assertNull(filas.get(0).getCapacidad(), "todo o nada: sin saber lo del taller no se enseña lo de NSYS");
        assertNull(filas.get(0).getColor());
    }

    @Test void unaListaVaciaNoConsultaNada() {
        List<ReparacionResumen> vacia = new ArrayList<>();
        assertSame(vacia, descripcion.rellenar(vacia));
        verifyNoInteractions(nsys, tallerDao);
    }
}
```

- [ ] **Step 3: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='DescripcionMovilTest,TallerNsysTest'`
Expected: error de compilación: no existen `DescripcionMovil`, `ReparacionResumen.getCapacidad()`/`getColor()` ni
`TallerNsys.colorDeTodos()`.

- [ ] **Step 4: `ReparacionResumen`**

En `src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`, justo después de la línea

```java
    @Schema(nullable = true) private String        normalTecnicoNombre;      // AG: dueño de esa normal (la más antigua)
```

añade:

```java

    // 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2.3): la capacidad del espejo de NSYS y el color que vale (lo
    // del taller vigente o el de NSYS), de la fila más reciente del IMEI. Solo los rellenan las seis listas del taller
    // (nsys.DescripcionMovil); null fuera del espejo, si la lectura falla y en las demás rutas. Van al final del contrato.
    @Schema(nullable = true) private String        capacidad;
    @Schema(nullable = true) private String        color;
```

y, después del último setter (`public void setNormalTecnicoNombre(String v) { … }`), antes de la llave final de la clase:

```java
    public String        getCapacidad()                            { return capacidad; }
    public void          setCapacidad(String v)                    { this.capacidad = v; }
    public String        getColor()                                { return color; }
    public void          setColor(String v)                        { this.color = v; }
```

El constructor no cambia.

- [ ] **Step 5: `TallerNsys.colorDeTodos()`**

En `src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java`, justo después del método `valedorDeTodos()`, añade:

```java
    /** Solo el color que vale de cada móvil, con todos los cambios vigentes del taller (una consulta) y sin el maestro, que solo
     *  hace falta para el SKU: las listas del taller (spec 0.9.21 «Descripción completa donde pone «Modelo»» §2.3). Da lo mismo
     *  que {@code valedorDeTodos().apply(m).color()}. */
    public Function<MovilNsys, String> colorDeTodos() {
        Map<String, List<CambioTaller>> porImei = porImei(tallerDao.vigentes());
        return m -> LoQueVale.valor(porImei.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), List.of()), DatoTaller.COLOR, m);
    }
```

- [ ] **Step 6: `DescripcionMovil`**

Crea `src/main/java/com/reparaciones/servidor/nsys/DescripcionMovil.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** La descripción completa del móvil en las listas del taller (spec 0.9.21 «Descripción completa donde pone «Modelo»» §2.3):
 *  a cada fila le pone la capacidad del espejo de NSYS y el color que vale (lo del taller vigente o, si no hay, el de NSYS), de
 *  la fila más reciente de su IMEI. El nombre del modelo sigue saliendo de Telefono.MODELO. Un IMEI que no está en el espejo
 *  (un cliente externo) se queda sin los dos. El cruce va en Java por {@link NsysDAO#claveCruce}, nunca con un JOIN (las tablas
 *  tienen intercalaciones distintas). Nunca rompe una lista: si el espejo o lo del taller fallan, las filas salen como antes y
 *  se apunta en el log. */
@Service
public class DescripcionMovil {

    private static final Logger log = LoggerFactory.getLogger(DescripcionMovil.class);

    private final NsysDAO nsysDao;
    private final TallerNsys taller;

    public DescripcionMovil(NsysDAO nsysDao, TallerNsys taller) {
        this.nsysDao = nsysDao;
        this.taller = taller;
    }

    /** Lo de NSYS que lleva una fila. */
    private record DelEspejo(String capacidad, String color) {}

    /** Rellena la capacidad y el color de estas filas (las cambia en el sitio) y devuelve la misma lista. Una lectura del espejo
     *  por IMEI distinto, en tandas de {@value NsysDAO#TANDA_IN} ({@link NsysDAO#porImeis}), y una de lo del taller (todos los
     *  vigentes); nunca una consulta por fila. Todo o nada: si falla una lectura, ninguna fila cambia. */
    public List<ReparacionResumen> rellenar(List<ReparacionResumen> filas) {
        if (filas == null || filas.isEmpty()) return filas;
        Map<String, DelEspejo> moviles;
        try {
            moviles = leer(filas);
        } catch (RuntimeException e) {
            log.warn("Listas del taller sin capacidad ni color ({} filas): no se pudo leer el espejo de NSYS o lo del taller: {}",
                    filas.size(), e.toString());
            return filas;
        }
        for (ReparacionResumen r : filas) {
            DelEspejo m = moviles.get(NsysDAO.claveCruce(r.getImei()));
            if (m == null) continue;
            r.setCapacidad(m.capacidad());
            r.setColor(m.color());
        }
        return filas;
    }

    private Map<String, DelEspejo> leer(List<ReparacionResumen> filas) {
        Set<String> imeis = new LinkedHashSet<>();
        for (ReparacionResumen r : filas) if (r.getImei() != null && !r.getImei().isBlank()) imeis.add(r.getImei());
        if (imeis.isEmpty()) return Map.of();
        // porImeis trae de cada IMEI el lote más reciente primero: se queda ese.
        Map<String, MovilNsys> masReciente = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(imeis)) masReciente.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        if (masReciente.isEmpty()) return Map.of();
        Function<MovilNsys, String> color = taller.colorDeTodos();
        Map<String, DelEspejo> moviles = new HashMap<>();
        masReciente.forEach((clave, m) -> moviles.put(clave, new DelEspejo(m.capacidad(), color.apply(m))));
        return moviles;
    }
}
```

- [ ] **Step 7: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='DescripcionMovilTest,TallerNsysTest'`
Expected: sin fallos (código de salida 0).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java \
        src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java \
        src/main/java/com/reparaciones/servidor/nsys/DescripcionMovil.java \
        src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilTest.java \
        src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java
git commit -m "feat: capacidad y color de NSYS para las listas del taller (DescripcionMovil)"
```

---

### Task 2: Servidor — las seis rutas del taller rellenan capacidad y color

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/GlassController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/PulidoController.java`
- Modify (test): `src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java` (entero) y, solo el
  constructor, `IdempotenciaReparacionControllerTest`, `PropiedadAsignacionControllersTest`, `ReparacionControllerContadoresTest`,
  `ReparacionControllerCorregirClienteTest`, `ReparacionControllerEliminarTest` y `ReparacionControllerEntregaGlassTest`
- Create (test): `src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilRutasTest.java`
- Modify (test): `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `DescripcionMovil.rellenar(List<ReparacionResumen>)` de la Task 1 (devuelve la misma lista).
- Produces:
  - `ReparacionController(…, GuardadoReparacion guardado, DescripcionMovil descripcion)` (un parámetro más, al final);
  - `GlassController(ReparacionDAO, LogDAO, TelefonoDAO, ModeloNsys, TecnicoDAO, CargaAsignacionesService, DescripcionMovil)`;
  - `PulidoController(ReparacionDAO, LogDAO, TelefonoDAO, ModeloNsys, DescripcionMovil)`;
  - `target/openapi.json` con `ReparacionResumen.capacidad` y `ReparacionResumen.color` (nullables, required, al final), para la
    Task 3.

- [ ] **Step 1: Tests (fallan)**

Sustituye **entero** `src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java` por:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import com.reparaciones.servidor.service.EntregaGlassService;
import com.reparaciones.servidor.service.GuardadoReparacion;
import com.reparaciones.servidor.nsys.DescripcionMovil;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import com.reparaciones.servidor.nsys.ModeloNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Los seis endpoints de lectura del taller pasan el ?tecnico= por FiltroTecnico (spec web-taller §5.1) y, desde la 0.9.21,
 *  devuelven su lista con la capacidad y el color de NSYS (DescripcionMovil; spec «Descripción completa donde pone «Modelo»»). */
class FiltroTecnicoControllersTest {

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final DescripcionMovil descripcion = mock(DescripcionMovil.class);
    private final ReparacionController rep = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), mock(LogDAO.class), mock(BorradorDAO.class),
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class), mock(EntregaGlassService.class), new GuardadoReparacion(dao, mock(TallerReparaciones.class)),
            descripcion);
    private final GlassController glass = new GlassController(dao, mock(LogDAO.class), mock(TelefonoDAO.class),
            mock(ModeloNsys.class), mock(TecnicoDAO.class), mock(CargaAsignacionesService.class), descripcion);
    private final PulidoController pulido = new PulidoController(dao, mock(LogDAO.class), mock(TelefonoDAO.class), mock(ModeloNsys.class),
            descripcion);

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    /** Como el servicio de verdad: rellena en el sitio y devuelve la misma lista. */
    @BeforeEach void laDescripcionDevuelveLaMismaLista() {
        when(descripcion.rellenar(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void tecnicoSinParametroSoloRecibeLoSuyoEnLasSeisRutas() {
        rep.getHistorial(null, tecnico);        verify(dao).getHistorial(4);
        rep.getAsignaciones(null, tecnico);     verify(dao).getAsignaciones(4);
        glass.getHistorial(null, tecnico);      verify(dao).getHistorialGlass(4);
        glass.getAsignaciones(null, tecnico);   verify(dao).getAsignacionesGlass(4);
        pulido.getHistorial(null, tecnico);     verify(dao).getHistorialPulido(4);
        pulido.getAsignaciones(null, tecnico);  verify(dao).getAsignacionesPulido(4);
    }

    @Test void tecnicoPidiendoAOtroEs403SinTocarElDao() {
        assertEquals(403, status(() -> rep.getHistorial(9, tecnico)));
        assertEquals(403, status(() -> rep.getAsignaciones(9, tecnico)));
        assertEquals(403, status(() -> glass.getHistorial(9, tecnico)));
        assertEquals(403, status(() -> glass.getAsignaciones(9, tecnico)));
        assertEquals(403, status(() -> pulido.getHistorial(9, tecnico)));
        assertEquals(403, status(() -> pulido.getAsignaciones(9, tecnico)));
        verifyNoInteractions(dao, descripcion);
    }

    @Test void supertecnicoYAdminConservanElFiltroLibre() {
        rep.getHistorial(null, supertecnico);   verify(dao).getHistorial(null);
        rep.getHistorial(9, supertecnico);      verify(dao).getHistorial(9);
        pulido.getAsignaciones(null, admin);    verify(dao).getAsignacionesPulido(null);
        glass.getAsignaciones(9, admin);        verify(dao).getAsignacionesGlass(9);
    }

    @Test void lasSeisRutasDevuelvenSuListaPasadaPorLaDescripcionDelMovil() {
        List<ReparacionResumen> lista = List.of(new ReparacionResumen());
        when(dao.getHistorial(4)).thenReturn(lista);
        when(dao.getAsignaciones(4)).thenReturn(lista);
        when(dao.getHistorialGlass(4)).thenReturn(lista);
        when(dao.getAsignacionesGlass(4)).thenReturn(lista);
        when(dao.getHistorialPulido(4)).thenReturn(lista);
        when(dao.getAsignacionesPulido(4)).thenReturn(lista);
        assertSame(lista, rep.getHistorial(null, tecnico));
        assertSame(lista, rep.getAsignaciones(null, tecnico));
        assertSame(lista, glass.getHistorial(null, tecnico));
        assertSame(lista, glass.getAsignaciones(null, tecnico));
        assertSame(lista, pulido.getHistorial(null, tecnico));
        assertSame(lista, pulido.getAsignaciones(null, tecnico));
        verify(descripcion, times(6)).rellenar(lista);
    }

    private static int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }
}
```

Crea `src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilRutasTest.java`:

```java
package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.TallerDePrueba.movil;
import static com.reparaciones.servidor.nsys.TallerDePrueba.vigente;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las seis listas del taller llevan la capacidad y el color de NSYS (spec 0.9.21 «Descripción completa donde pone «Modelo»»
 *  §2.3), también para el técnico: cadena de seguridad real, DescripcionMovil y TallerNsys de verdad y los DAO mockeados (sin
 *  base de datos). */
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
class DescripcionMovilRutasTest {

    private static final String IMEI = "350000000000001";
    private static final String DE_CLIENTE = "350000000000003";
    private static final List<String> RUTAS = List.of("/api/reparaciones/asignaciones", "/api/reparaciones/historial",
            "/api/glass/asignaciones", "/api/glass/historial", "/api/pulidos/asignaciones", "/api/pulidos/historial");

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean NsysDAO nsys;
    @MockBean TallerDAO tallerDao;

    private String tecnico() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "usuario_prueba", "", "TECNICO", 4));
    }

    /** Dos filas del técnico 4 («Técnico 2»): una de un móvil de NSYS y otra de un cliente externo. Nuevas en cada llamada. */
    private static List<ReparacionResumen> lista() {
        return List.of(fila(IMEI), fila(DE_CLIENTE));
    }

    private static ReparacionResumen fila(String imei) {
        return new ReparacionResumen("A20261022_1", imei, "Técnico 2", null, null, null, null, false, false, null, null, 4, 0,
                null, null, null, 0, false, null, null, "13");
    }

    @BeforeEach void espejoYTaller() {
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(tallerDao.vigentes()).thenReturn(List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")));
        when(dao.getAsignaciones(4)).thenAnswer(i -> lista());
        when(dao.getHistorial(4)).thenAnswer(i -> lista());
        when(dao.getAsignacionesGlass(4)).thenAnswer(i -> lista());
        when(dao.getHistorialGlass(4)).thenAnswer(i -> lista());
        when(dao.getAsignacionesPulido(4)).thenAnswer(i -> lista());
        when(dao.getHistorialPulido(4)).thenAnswer(i -> lista());
    }

    @Test void elTecnicoRecibeLaCapacidadYElColorQueValeEnLasSeisListas() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).header("Authorization", tecnico()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].modelo").value("13"))
                    .andExpect(jsonPath("$[0].capacidad").value("128GB"))
                    .andExpect(jsonPath("$[0].color").value("Rojo"))
                    .andExpect(jsonPath("$[1].capacidad").value(nullValue()))
                    .andExpect(jsonPath("$[1].color").value(nullValue()));
        }
        verify(nsys, times(6)).porImeis(any());
        verify(tallerDao, times(6)).vigentes();
    }

    @Test void siElEspejoFallaLaListaSaleIgualSinCapacidadNiColor() throws Exception {
        when(nsys.porImeis(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idRep").value("A20261022_1"))
                .andExpect(jsonPath("$[0].modelo").value("13"))
                .andExpect(jsonPath("$[0].capacidad").value(nullValue()))
                .andExpect(jsonPath("$[0].color").value(nullValue()));
    }
}
```

En `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`, justo después de la línea

```java
        assertFalse(resumen.path("properties").path("idRep").path("nullable").asBoolean(false), "idRep no nullable");
```

añade:

```java
        // 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2.3): capacidad y color de NSYS, nullables y al final.
        assertNullable(esquemas, "ReparacionResumen", "capacidad", "color");
        assertEquals(List.of("capacidad", "color"), propiedades.subList(propiedades.size() - 2, propiedades.size()),
                "capacidad y color van al final de ReparacionResumen");
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `mvn -q test -Dtest='FiltroTecnicoControllersTest,DescripcionMovilRutasTest,OpenApiContractTest'`
Expected: error de compilación en `FiltroTecnicoControllersTest`: los constructores de `ReparacionController`,
`GlassController` y `PulidoController` no admiten un `DescripcionMovil`.

- [ ] **Step 3: `ReparacionController`**

En `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`:

1. Añade `import com.reparaciones.servidor.nsys.DescripcionMovil;` tras `import com.reparaciones.servidor.model.*;`.
2. Sustituye

```java
    private final GuardadoReparacion guardado;

    public ReparacionController(ReparacionDAO dao, ReparacionComponenteDAO rcDao, LogDAO logDao,
                                com.reparaciones.servidor.dao.BorradorDAO borradorDao, ComponenteDAO componenteDao,
                                DificultadPuntosDAO dificultadDao, TecnicoDAO tecnicoDao, RegistroIdempotencia idempotencia,
                                CargaAsignacionesService cargaAsignaciones, EntregaGlassService entregaGlass, GuardadoReparacion guardado) {
```

por

```java
    private final GuardadoReparacion guardado;
    /** 0.9.21: la capacidad y el color de NSYS en el historial y las asignaciones. */
    private final DescripcionMovil descripcion;

    public ReparacionController(ReparacionDAO dao, ReparacionComponenteDAO rcDao, LogDAO logDao,
                                com.reparaciones.servidor.dao.BorradorDAO borradorDao, ComponenteDAO componenteDao,
                                DificultadPuntosDAO dificultadDao, TecnicoDAO tecnicoDao, RegistroIdempotencia idempotencia,
                                CargaAsignacionesService cargaAsignaciones, EntregaGlassService entregaGlass, GuardadoReparacion guardado,
                                DescripcionMovil descripcion) {
```

3. Tras `        this.guardado = guardado;` (última línea del constructor), añade `        this.descripcion = descripcion;`.
4. Sustituye el cuerpo de `getHistorial(Integer tecnico, UsuarioPrincipal principal)` (`GET /historial`):

```java
        return dao.getHistorial(FiltroTecnico.efectivo(principal, tecnico));
```

por

```java
        return descripcion.rellenar(dao.getHistorial(FiltroTecnico.efectivo(principal, tecnico)));
```

5. Sustituye el cuerpo de `getAsignaciones(Integer tecnico, UsuarioPrincipal principal)` (`GET /asignaciones`):

```java
        return dao.getAsignaciones(FiltroTecnico.efectivo(principal, tecnico));
```

por

```java
        return descripcion.rellenar(dao.getAsignaciones(FiltroTecnico.efectivo(principal, tecnico)));
```

`getHistorialPorImei`, `completadas-hoy` y `carga-tecnicos` no se tocan.

- [ ] **Step 4: `GlassController` y `PulidoController`**

En `src/main/java/com/reparaciones/servidor/controller/GlassController.java`, añade
`import com.reparaciones.servidor.nsys.DescripcionMovil;` tras `import com.reparaciones.servidor.nsys.ModeloNsys;` y sustituye
desde `    private final CargaAsignacionesService cargaAsignaciones;` hasta el final de `getHistorial` por:

```java
    private final CargaAsignacionesService cargaAsignaciones;
    /** 0.9.21: la capacidad y el color de NSYS en el historial y las asignaciones de glass. */
    private final DescripcionMovil descripcion;

    public GlassController(ReparacionDAO dao, LogDAO logDao, TelefonoDAO telefonoDAO, ModeloNsys modeloNsys,
                           TecnicoDAO tecnicoDao, CargaAsignacionesService cargaAsignaciones, DescripcionMovil descripcion) {
        this.dao = dao;
        this.logDao = logDao;
        this.telefonoDAO = telefonoDAO;
        this.modeloNsys = modeloNsys;
        this.tecnicoDao = tecnicoDao;
        this.cargaAsignaciones = cargaAsignaciones;
        this.descripcion = descripcion;
    }

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(@RequestParam(required = false) Integer tecnico,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descripcion.rellenar(dao.getAsignacionesGlass(FiltroTecnico.efectivo(principal, tecnico)));
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(@RequestParam(required = false) Integer tecnico,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descripcion.rellenar(dao.getHistorialGlass(FiltroTecnico.efectivo(principal, tecnico)));
    }
```

En `src/main/java/com/reparaciones/servidor/controller/PulidoController.java`, añade
`import com.reparaciones.servidor.nsys.DescripcionMovil;` tras `import com.reparaciones.servidor.nsys.ModeloNsys;` y sustituye
desde `    private final ModeloNsys modeloNsys;` hasta el final de `getHistorial` por:

```java
    private final ModeloNsys modeloNsys;
    /** 0.9.21: la capacidad y el color de NSYS en el historial y las asignaciones de pulido. */
    private final DescripcionMovil descripcion;

    public PulidoController(ReparacionDAO dao, LogDAO logDao, TelefonoDAO telefonoDAO, ModeloNsys modeloNsys,
                            DescripcionMovil descripcion) {
        this.dao    = dao;
        this.logDao = logDao;
        this.telefonoDAO = telefonoDAO;
        this.modeloNsys = modeloNsys;
        this.descripcion = descripcion;
    }

    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descripcion.rellenar(dao.getAsignacionesPulido(FiltroTecnico.efectivo(principal, tecnico)));
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descripcion.rellenar(dao.getHistorialPulido(FiltroTecnico.efectivo(principal, tecnico)));
    }
```

- [ ] **Step 5: El constructor en los otros seis tests**

Los seis construyen el controlador con la misma última línea; se les añade un `DescripcionMovil` mockeado (no llaman a las seis
rutas):

```bash
cd /c/Users/dev/Documents/wt/servidor-0921-desc/src/test/java/com/reparaciones/servidor/controller
for f in IdempotenciaReparacionControllerTest PropiedadAsignacionControllersTest ReparacionControllerContadoresTest \
         ReparacionControllerCorregirClienteTest ReparacionControllerEliminarTest ReparacionControllerEntregaGlassTest; do
  sed -i 's/new GuardadoReparacion(dao, mock(TallerReparaciones.class)));/new GuardadoReparacion(dao, mock(TallerReparaciones.class)), mock(DescripcionMovil.class));/' "$f.java"
  sed -i '/^import com.reparaciones.servidor.nsys.TallerReparaciones;/i import com.reparaciones.servidor.nsys.DescripcionMovil;' "$f.java"
done
grep -l "mock(DescripcionMovil.class)" *.java | wc -l
grep -c "^import com.reparaciones.servidor.nsys.DescripcionMovil;" IdempotenciaReparacionControllerTest.java
cd /c/Users/dev/Documents/wt/servidor-0921-desc
```

Expected: `7` (los seis más `FiltroTecnicoControllersTest`) y `1`.

- [ ] **Step 6: Ejecutar los tests y ver que pasan**

Run: `mvn -q test -Dtest='FiltroTecnicoControllersTest,DescripcionMovilRutasTest,OpenApiContractTest,IdempotenciaReparacionControllerTest,PropiedadAsignacionControllersTest,ReparacionControllerContadoresTest,ReparacionControllerCorregirClienteTest,ReparacionControllerEliminarTest,ReparacionControllerEntregaGlassTest'`
Expected: sin fallos.

- [ ] **Step 7: Suite completa del servidor y contrato**

Run: `mvn -q test`
Expected: sin fallos (el contexto arranca con el bean nuevo). Después:
`grep -c '"capacidad"' target/openapi.json` da al menos 1. Anota el total de tests en el registro.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ReparacionController.java \
        src/main/java/com/reparaciones/servidor/controller/GlassController.java \
        src/main/java/com/reparaciones/servidor/controller/PulidoController.java \
        src/test/java/com/reparaciones/servidor/controller \
        src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilRutasTest.java \
        src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: las seis listas del taller llevan la capacidad y el color de NSYS"
```

---

### Task 3: Web — contrato, tipos, `descripcionMovil` y las cuatro copias

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `src/shared/api/schema.d.ts` (generados)
- Modify: `gestion-reparaciones-web/src/modules/taller/test/fabrica.ts`
- Modify: `gestion-reparaciones-web/src/shared/lib/telefonoNsys.ts` (+ `telefonoNsys.test.ts`)
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/modelos.ts` (+ `modelos.test.ts`)
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/FichaMovil.tsx`,
  `src/modules/telefonos/stock/BuscadorImei.tsx`, `src/modules/telefonos/stock/porPasar.ts`, `src/modules/telefonos/stock/varios.ts`

**Interfaces:**
- Consumes: `/c/Users/dev/Documents/wt/servidor-0921-desc/target/openapi.json` de la Task 2.
- Produces:
  - `ReparacionResumen` (tipo) con `capacidad: string | null` y `color: string | null`; la fábrica `resumen()` los trae a `null`.
  - `descripcionMovil(nombre: string | null | undefined, capacidad?: string | null, color?: string | null): string` en
    `src/shared/lib/telefonoNsys.ts`.
  - `descripcionTrabajo(t: { modelo?: string | null; capacidad?: string | null; color?: string | null }): string` en
    `src/modules/taller/lib/modelos.ts` (el modelo del taller traducido con la capacidad y el color de NSYS).

- [ ] **Step 1: Worktree de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git log --oneline -1 origin/main
git worktree add -b dev_v0921_descripcion /c/Users/dev/Documents/wt/web-0921-desc origin/main
cd /c/Users/dev/Documents/wt/web-0921-desc && npm ci
```

Expected: el mismo commit que `origin/main` (el 22/10, `16c3413`) y `npm ci` sin errores. Apunta el commit en el registro.
Línea base: `npm run check` en verde (si algo falla ya en `main`, apuntarlo y avisar).

- [ ] **Step 2: Contrato y tipos**

Run (en `/c/Users/dev/Documents/wt/web-0921-desc`):
`cp /c/Users/dev/Documents/wt/servidor-0921-desc/target/openapi.json api/openapi.json && npm run api:types:offline`
Expected: `git diff --stat` enseña solo `api/openapi.json` (unas 10 inserciones y 2 borrados, todo dentro de
`ReparacionResumen`: la lista `required` y los bloques `capacidad` y `color`) y `src/shared/api/schema.d.ts` (2 inserciones:
`capacidad: string | null;` y `color: string | null;` al final de `ReparacionResumen`). **Si el diff toca algo más, parar y
avisar:** el `main` del servidor y la copia del contrato de la web no van a la par.

- [ ] **Step 3: La fábrica (el tipo la rompe)**

Run: `npx tsc -b`
Expected: error en `src/modules/taller/test/fabrica.ts`: a la fila de `resumen()` le faltan `capacidad` y `color`.

En `src/modules/taller/test/fabrica.ts`, dentro de `resumen()`, sustituye

```ts
    glassEntregadoPorNombre: null, glassEntregadoPor: null, glassTecnicoNombre: null, normalAbierta: false, normalTecnicoNombre: null,
    ...parcial,
```

por

```ts
    glassEntregadoPorNombre: null, glassEntregadoPor: null, glassTecnicoNombre: null, normalAbierta: false, normalTecnicoNombre: null,
    // 0.9.21: sin NSYS por defecto (la lista dice el modelo solo, como antes).
    capacidad: null, color: null,
    ...parcial,
```

Run: `npx tsc -b`
Expected: limpio. Los manejadores por defecto de MSW (`src/test/server.ts`) devuelven listas vacías: no cambian.

- [ ] **Step 4: Commit del contrato**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/taller/test/fabrica.ts
git commit -m "chore: contrato y tipos con capacidad y color en ReparacionResumen (0.9.21)"
```

- [ ] **Step 5: Tests (fallan)**

En `src/shared/lib/telefonoNsys.test.ts`, cambia el import por

```ts
import { clasesUbicacion, descripcionMovil, diaNsys, diasDesdeNsys, textoGrado, nombreModeloNsys, textoEstadoMovil, textoPoner } from './telefonoNsys'
```

y añade al final del fichero:

```ts
describe('descripcionMovil (0.9.21, spec «Descripción completa donde pone «Modelo»» §2.1)', () => {
  it('nombre, capacidad y color con un espacio, sin los que falten', () => {
    expect(descripcionMovil('iPhone 13', '128GB', 'Azul')).toBe('iPhone 13 128GB Azul')
    expect(descripcionMovil('iPhone 13', '128GB', null)).toBe('iPhone 13 128GB')
    expect(descripcionMovil('iPhone 13', null, 'Azul')).toBe('iPhone 13 Azul')
    expect(descripcionMovil('iPhone 13', '128GB')).toBe('iPhone 13 128GB')
    expect(descripcionMovil('iPhone 13', '', '')).toBe('iPhone 13')
    expect(descripcionMovil('iPhone 13')).toBe('iPhone 13')
  })

  it('sin nombre, nada: una lista del taller sin modelo dice lo de hoy aunque NSYS sepa la capacidad', () => {
    expect(descripcionMovil('', '128GB', 'Azul')).toBe('')
    expect(descripcionMovil(null, '128GB', 'Azul')).toBe('')
    expect(descripcionMovil(undefined)).toBe('')
  })

  it('las copias de «modelo capacidad» de NSYS enseñan lo mismo que antes', () => {
    expect(descripcionMovil(nombreModeloNsys('13pro', '13 PRO'), '256GB')).toBe('iPhone 13 Pro 256GB')
    expect(descripcionMovil(nombreModeloNsys(null, 'SE2022'), null)).toBe('iPhone SE2022')
    expect(descripcionMovil(nombreModeloNsys(null, null), null)).toBe('Sin modelo en el maestro')
  })
})
```

En `src/modules/taller/lib/modelos.test.ts`, cambia el import por

```ts
import { MODELOS_ORDENADOS, descripcionTrabajo, extraerModelo, modelosDisponibles, traducirModelo } from './modelos'
```

y añade al final del fichero:

```ts
describe('descripcionTrabajo (0.9.21): lo que enseñan las listas del taller donde pone «Modelo»', () => {
  it('el modelo del taller traducido con la capacidad y el color de NSYS; fuera de NSYS, el modelo solo; sin modelo, vacío', () => {
    expect(descripcionTrabajo({ modelo: '13', capacidad: '128GB', color: 'Azul' })).toBe('iPhone 13 128GB Azul')
    expect(descripcionTrabajo({ modelo: '12promax', capacidad: '256GB', color: null })).toBe('iPhone 12 Pro Max 256GB')
    expect(descripcionTrabajo({ modelo: '14', capacidad: null, color: null })).toBe('iPhone 14')
    expect(descripcionTrabajo({ modelo: null, capacidad: '128GB', color: 'Azul' })).toBe('')
    // Un servidor sin los campos nuevos: lo de hoy.
    expect(descripcionTrabajo({ modelo: '15pro' })).toBe('iPhone 15 Pro')
    expect(descripcionTrabajo({})).toBe('')
  })
})
```

- [ ] **Step 6: Ejecutar los tests y ver que fallan**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/lib/modelos.test.ts`
Expected: FAIL: `descripcionMovil` y `descripcionTrabajo` no existen (`is not a function`).

- [ ] **Step 7: `descripcionMovil` y `descripcionTrabajo`**

En `src/shared/lib/telefonoNsys.ts`, justo después de `nombreModeloNsys`, añade:

```ts
/** 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2.1): «iPhone 13 128GB Azul», el nombre del modelo con la capacidad
 *  y el color detrás, separados por un espacio y sin los que falten. Sin nombre, nada: una lista del taller sin modelo dice lo de
 *  hoy (vacío) aunque NSYS sepa la capacidad. La usan las listas del taller y las copias de «modelo capacidad» de NSYS (ficha
 *  del IMEI, buscador, «Por pasar a NSYS» y «Pegar varios IMEIs», que pintan el color aparte). */
export function descripcionMovil(nombre: string | null | undefined, capacidad?: string | null, color?: string | null): string {
  if (!nombre) return ''
  return [nombre, capacidad, color].filter(Boolean).join(' ')
}
```

En `src/modules/taller/lib/modelos.ts`, sustituye

```ts
// traducirModelo y MODELOS_ORDENADOS viven en shared (los usan también almacén y telefonos): se reexportan para taller.
import { MODELOS_ORDENADOS } from '@/shared/lib/modelos'
export { MODELOS_ORDENADOS, traducirModelo } from '@/shared/lib/modelos'
```

por

```ts
// traducirModelo y MODELOS_ORDENADOS viven en shared (los usan también almacén y telefonos): se reexportan para taller.
import { MODELOS_ORDENADOS, traducirModelo } from '@/shared/lib/modelos'
import { descripcionMovil } from '@/shared/lib/telefonoNsys'
export { MODELOS_ORDENADOS, traducirModelo } from '@/shared/lib/modelos'

/** 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2): lo que enseñan las listas del taller donde pone «Modelo»: el
 *  modelo del taller (`Telefono.MODELO`, traducido) con la capacidad y el color de NSYS que manda el servidor («iPhone 13 128GB
 *  Azul»); fuera de NSYS, el modelo solo; sin modelo, vacío. Vale para un trabajo (`ReparacionResumen`) y para un grupo de IMEIs. */
export function descripcionTrabajo(t: { modelo?: string | null; capacidad?: string | null; color?: string | null }): string {
  return descripcionMovil(traducirModelo(t.modelo), t.capacidad, t.color)
}
```

- [ ] **Step 8: Las cuatro copias de «modelo capacidad»**

Sin cambiar lo que enseñan (el color sigue aparte):

1. `src/modules/taller/imeis/FichaMovil.tsx`: añade `descripcionMovil` al import de `@/shared/lib/telefonoNsys`
   (`import { clasesUbicacion, descripcionMovil, diaNsys, nombreModeloNsys, SIN_SKU_MAESTRO, textoEstadoMovil, textoPoner } from '@/shared/lib/telefonoNsys'`)
   y sustituye `{[nombreModeloNsys(ficha.modelo, ficha.modeloMaestro), ficha.capacidad].filter(Boolean).join(' ')}` por
   `{descripcionMovil(nombreModeloNsys(ficha.modelo, ficha.modeloMaestro), ficha.capacidad)}`.
2. `src/modules/telefonos/stock/BuscadorImei.tsx`: el import pasa a
   `import { descripcionMovil, nombreModeloNsys } from '@/shared/lib/telefonoNsys'` y
   `` {`${m.imeiSn} · ${[nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad].filter(Boolean).join(' ')}`} `` pasa a
   `` {`${m.imeiSn} · ${descripcionMovil(nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad)}`} ``.
3. `src/modules/telefonos/stock/porPasar.ts`: el import pasa a
   `import { descripcionMovil, diaNsys, nombreModeloNsys, SIN_SKU_MAESTRO, textoPoner } from '@/shared/lib/telefonoNsys'` y
   `modeloDeFila` a:

```ts
/** «iPhone 13 128GB». */
export const modeloDeFila = (f: FilaPorPasar): string => descripcionMovil(nombreModeloNsys(f.modelo, f.modeloMaestro), f.capacidad)
```

4. `src/modules/telefonos/stock/varios.ts`: el import pasa a
   `import { descripcionMovil, diaNsys, nombreModeloNsys, textoEstadoMovil } from '@/shared/lib/telefonoNsys'` y
   `modeloYCapacidad` a:

```ts
/** «iPhone 13 128GB». */
export const modeloYCapacidad = (m: MovilComprobado): string => descripcionMovil(nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad)
```

- [ ] **Step 9: Ejecutar los tests y ver que pasan**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/lib/modelos.test.ts src/modules/taller/imeis/FichaMovil.test.tsx src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/varios.test.ts src/modules/telefonos/stock/StockTelefonosPage.test.tsx`
Expected: PASS. Los de las cuatro copias ya existían y siguen igual: la ficha «iPhone 16 128GB Rosa taller NSYS: Negro», el
buscador «353918700112233 · iPhone 11 64GB Negro · …», «Por pasar a NSYS» «iPhone 13 128GB» y el Excel de «Pegar varios IMEIs»
`1\t350000000000001\tiPhone 13 128GB\tAzul\t…`.

Run: `npx tsc -b && npm run lint`
Expected: limpios.

- [ ] **Step 10: Commit**

```bash
git add src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/modules/taller/lib/modelos.ts \
        src/modules/taller/lib/modelos.test.ts src/modules/taller/imeis/FichaMovil.tsx \
        src/modules/telefonos/stock/BuscadorImei.tsx src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/varios.ts
git commit -m "feat: descripcionMovil comun para el texto del movil y descripcionTrabajo del taller"
```

---

### Task 4: Web — la descripción completa en las pantallas del taller

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/asignaciones/columnas.tsx` (+ `columnas.test.tsx`)
- Modify: `gestion-reparaciones-web/src/modules/taller/pendientes/textoCelda.ts` (+ `textoCelda.test.ts`, nuevo),
  `PendientesPage.tsx` (+ test), `PulidosPendientesPage.tsx` (+ test)
- Modify: `gestion-reparaciones-web/src/modules/taller/historial/columnasTrabajo.tsx` (+ test), `HistorialPulidosPage.tsx` (+ test)
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/grupoImei.ts` (+ test), `src/modules/taller/imeis/ImeisPage.tsx`
  (+ test), `src/modules/taller/imeis/ImeiDetallePage.tsx` (+ test)

**Interfaces:**
- Consumes: `descripcionTrabajo` (Task 3) y la fábrica `resumen()` con `capacidad` y `color`.
- Produces:
  - `movilDe(trabajos: ReparacionResumen[]): { modelo: string; capacidad: string | null; color: string | null }` en
    `modules/taller/lib/grupoImei.ts` (el primer valor no vacío de cada uno);
  - `GrupoImei` con `capacidad: string | null` y `color: string | null` (los usa el CSV de la Task 5).

- [ ] **Step 1: Tests (fallan)**

Crea `src/modules/taller/pendientes/textoCelda.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { resumen } from '../test/fabrica'
import { textoCeldaPendiente } from './textoCelda'

describe('textoCeldaPendiente: «Copiar celda» de Asignaciones, Pendientes y Pulidos pendientes', () => {
  it('0.9.21: el modelo copia la descripción completa; sin NSYS, el modelo solo; sin modelo, vacío', () => {
    expect(textoCeldaPendiente(resumen({ modelo: '13', capacidad: '128GB', color: 'Azul' }), 'modelo')).toBe('iPhone 13 128GB Azul')
    expect(textoCeldaPendiente(resumen({ modelo: '13', capacidad: '128GB', color: null }), 'modelo')).toBe('iPhone 13 128GB')
    expect(textoCeldaPendiente(resumen({ modelo: '13' }), 'modelo')).toBe('iPhone 13')
    expect(textoCeldaPendiente(resumen({ modelo: null, capacidad: '128GB', color: 'Azul' }), 'modelo')).toBe('')
  })
})
```

Al final de `src/modules/taller/asignaciones/columnas.test.tsx`, añade:

```tsx
describe('crearColumnas: Modelo con la descripción completa (0.9.21)', () => {
  it('el modelo del taller con la capacidad y el color de NSYS; sin NSYS, el modelo solo', () => {
    pintar([
      resumen({ idRep: 'A20261022_1', imei: '350000000000001', modelo: '13', capacidad: '128GB', color: 'Azul' }),
      resumen({ idRep: 'A20261022_2', imei: '350000000000002', modelo: '14', capacidad: null, color: null }),
    ], opciones({ soloLectura: true }))
    expect(screen.getByText('iPhone 13 128GB Azul')).toBeInTheDocument()
    expect(screen.getByText('iPhone 14')).toBeInTheDocument()
  })
})
```

Al final de `src/modules/taller/pendientes/PendientesPage.test.tsx`, añade:

```tsx
describe('PendientesPage: Modelo con la descripción completa (0.9.21)', () => {
  it('la columna Modelo lleva la capacidad y el color de NSYS', async () => {
    server.use(http.get('*/api/reparaciones/asignaciones', () => HttpResponse.json([
      resumen({ idRep: 'A20261022_1', imei: '350000000000001', modelo: '13', capacidad: '128GB', color: 'Azul', nombreTecnico: 'Técnico 2' }),
    ])))
    abrir()
    expect(await screen.findByText('iPhone 13 128GB Azul')).toBeInTheDocument()
  })
})
```

Al final de `src/modules/taller/pendientes/PulidosPendientesPage.test.tsx`, añade:

```tsx
describe('PulidosPendientesPage: Modelo con la descripción completa (0.9.21)', () => {
  it('en la columna y en la confirmación «IMEI · modelo»', async () => {
    server.use(http.get('*/api/pulidos/asignaciones', () => HttpResponse.json([{ ...filas[0], capacidad: '128GB', color: 'Azul' }, filas[1]])))
    abrir()
    expect(await screen.findByText('iPhone 13 128GB Azul')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Seleccionar todo' }))
    await userEvent.click(screen.getByRole('button', { name: 'Completar seleccionados' }))
    const dlg = screen.getByRole('dialog', { name: 'Completar 2 pulidos' })
    expect(within(dlg).getByText(/Has seleccionado/).textContent)
      .toBe('Has seleccionado 2 pulidos:\n351200000000021 · iPhone 13 128GB Azul\n358300000000121')
  })
})
```

Al final de `src/modules/taller/historial/columnasTrabajo.test.ts`, añade:

```ts
describe('Modelo con la descripción completa (0.9.21)', () => {
  const modelo = columnasTrabajo({ patronFechas: 'dd/MM/yyyy', tituloId: 'Id', onIrA: () => {} }).find((c) => c.id === 'modelo') as unknown as
    { accessorFn: (r: ReturnType<typeof resumen>, i: number) => unknown }

  it('la celda y «Copiar celda» llevan la capacidad y el color de NSYS; sin NSYS, el modelo solo; sin modelo, vacío', () => {
    const conNsys = resumen({ modelo: '13', capacidad: '128GB', color: 'Azul' })
    expect(modelo.accessorFn(conNsys, 0)).toBe('iPhone 13 128GB Azul')
    expect(textoCeldaTrabajo(conNsys, 'modelo', 'dd/MM/yyyy')).toBe('iPhone 13 128GB Azul')
    expect(modelo.accessorFn(resumen({ modelo: '14' }), 0)).toBe('iPhone 14')
    expect(textoCeldaTrabajo(resumen({ modelo: null, capacidad: '128GB' }), 'modelo', 'dd/MM/yyyy')).toBe('')
  })
})
```

En `src/modules/taller/historial/HistorialPulidosPage.test.tsx`, cambia `import { beforeEach, describe, expect, it } from 'vitest'`
por `import { beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'` y añade al final del fichero:

```tsx
describe('HistorialPulidosPage: Modelo con la descripción completa (0.9.21)', () => {
  it('en la columna y en «Copiar celda»', async () => {
    // navigator.clipboard no existe en jsdom: se define y se restaura al acabar (patrón de MenuCopiarCelda.test.tsx).
    const descriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
    onTestFinished(() => { if (descriptor) Object.defineProperty(navigator, 'clipboard', descriptor); else Reflect.deleteProperty(navigator, 'clipboard') })
    const escribir = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: escribir }, configurable: true })
    server.use(http.get('*/api/pulidos/historial', () => HttpResponse.json([{ ...filas[0], capacidad: '128GB', color: 'Azul' }])))
    abrir(SESION_TEC)
    const celda = await screen.findByText('iPhone 13 128GB Azul')
    await userEvent.pointer({ keys: '[MouseRight]', target: celda })
    await userEvent.click(await screen.findByRole('menuitem', { name: /Copiar celda/ }))
    expect(escribir).toHaveBeenCalledWith('iPhone 13 128GB Azul')
  })
})
```

En `src/modules/taller/lib/grupoImei.test.ts`, cambia el import por
`import { agruparPorImei, movilDe, ordenarPorActividad, resumenTipos } from './grupoImei'` y añade al final del fichero:

```ts
describe('el móvil del grupo (0.9.21)', () => {
  it('modelo, capacidad y color: el primero no vacío de cada uno, aunque vengan de trabajos distintos', () => {
    const trabajos = [
      rr('R1', { modelo: '13', capacidad: null, color: null }),
      rr('G1', { modelo: '13', capacidad: '128GB', color: 'Azul' }),
      rr('P1', { modelo: null, capacidad: '128GB', color: 'Rojo' }),
    ]
    expect(movilDe(trabajos)).toEqual({ modelo: '13', capacidad: '128GB', color: 'Azul' })
    const [g] = agruparPorImei(trabajos)
    expect([g.modelo, g.capacidad, g.color]).toEqual(['13', '128GB', 'Azul'])
    expect(movilDe([rr('R1')])).toEqual({ modelo: '', capacidad: null, color: null })
  })
})
```

Al final de `src/modules/taller/imeis/ImeisPage.test.tsx`, añade:

```tsx
describe('ImeisPage: Modelo con la descripción completa (0.9.21)', () => {
  it('el modelo del grupo lleva la capacidad y el color de NSYS, también al copiar la celda', async () => {
    const descriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
    onTestFinished(() => { if (descriptor) Object.defineProperty(navigator, 'clipboard', descriptor); else Reflect.deleteProperty(navigator, 'clipboard') })
    const escribir = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: escribir }, configurable: true })
    server.use(http.get('*/api/reparaciones/historial', () =>
      HttpResponse.json(reps.map((r) => (r.imei === B ? { ...r, capacidad: '128GB', color: 'Azul' } : r)))))
    abrir()
    const celda = await screen.findByText('iPhone 14 128GB Azul')
    expect(screen.getByText('iPhone 16')).toBeInTheDocument()
    await userEvent.pointer({ keys: '[MouseRight]', target: celda })
    await userEvent.click(await screen.findByRole('menuitem', { name: /Copiar celda/ }))
    expect(escribir).toHaveBeenCalledWith('iPhone 14 128GB Azul')
  })
})
```

Al final de `src/modules/taller/imeis/ImeiDetallePage.test.tsx`, añade:

```tsx
describe('ImeiDetallePage: la barra con la descripción completa (0.9.21)', () => {
  it('«• iPhone 16 128GB Negro» con la capacidad y el color de NSYS', async () => {
    server.use(http.get('*/api/reparaciones/historial', () =>
      HttpResponse.json(reps.map((r) => (r.imei === A ? { ...r, capacidad: '128GB', color: 'Negro' } : r)))))
    abrir()
    expect(await screen.findByText('• iPhone 16 128GB Negro')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `npx vitest run src/modules/taller/asignaciones/columnas.test.tsx src/modules/taller/pendientes src/modules/taller/historial/columnasTrabajo.test.ts src/modules/taller/historial/HistorialPulidosPage.test.tsx src/modules/taller/lib/grupoImei.test.ts src/modules/taller/imeis/ImeisPage.test.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx`
Expected: FAIL en los tests nuevos: «Unable to find an element with the text: iPhone 13 128GB Azul» (y los demás textos con
capacidad y color), `expected 'iPhone 13' to be 'iPhone 13 128GB Azul'` y `movilDe is not a function`. Los demás, en verde.

- [ ] **Step 3: Asignaciones y Pendientes**

1. `src/modules/taller/asignaciones/columnas.tsx`: `import { traducirModelo } from '../lib/modelos'` pasa a
   `import { descripcionTrabajo } from '../lib/modelos'` y la columna «Modelo» a:

```tsx
    // 0.9.21: con la capacidad y el color de NSYS («iPhone 13 128GB Azul»); sin NSYS, el modelo solo.
    { id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => descripcionTrabajo(r) },
```

2. `src/modules/taller/pendientes/textoCelda.ts` (lo usan también Asignaciones y Pulidos pendientes): el import pasa a
   `import { descripcionTrabajo } from '../lib/modelos'` y `case 'modelo': return traducirModelo(rep.modelo)` a
   `case 'modelo': return descripcionTrabajo(rep)`.
3. `src/modules/taller/pendientes/PendientesPage.tsx`: el import pasa a `import { descripcionTrabajo } from '../lib/modelos'` y
   la columna «Modelo» a `{ id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => descripcionTrabajo(r) },`.
4. `src/modules/taller/pendientes/PulidosPendientesPage.tsx`: el import pasa a
   `import { descripcionTrabajo, traducirModelo } from '../lib/modelos'` (el CSV aún usa `traducirModelo` hasta la Task 5), la
   columna «Modelo» a `{ id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => descripcionTrabajo(r) },` y, en
   `textoConfirmarCompletar`, la línea de las líneas a:

```ts
  const lineas = reps.slice(0, MAX_LINEAS_CONFIRMAR).map((r) => { const modelo = descripcionTrabajo(r); return modelo ? `${r.imei} · ${modelo}` : r.imei })
```

   y su comentario a `/** Cuántos y cuáles va a completar: una línea por IMEI (con el modelo, la capacidad y el color si los tiene), las cinco primeras y cuántas más. */`.

- [ ] **Step 4: Historial**

1. `src/modules/taller/historial/columnasTrabajo.tsx` (Historial de reparaciones y glass, y la tabla del detalle del IMEI): el
   import pasa a `import { descripcionTrabajo } from '../lib/modelos'`, la columna a
   `{ id: 'modelo', header: 'Modelo', size: 100, accessorFn: (r) => descripcionTrabajo(r) },` y, en `textoCeldaTrabajo`,
   `case 'modelo': return traducirModelo(rep.modelo)` a `case 'modelo': return descripcionTrabajo(rep)`.
2. `src/modules/taller/historial/HistorialPulidosPage.tsx`: el import pasa a
   `import { descripcionTrabajo, MODELOS_ORDENADOS, traducirModelo } from '../lib/modelos'` (`OPCIONES_MODELO` y el CSV siguen con
   `traducirModelo`); en `textoCelda`, `case 'modelo': return descripcionTrabajo(rep)`; en `COLUMNAS`,
   `{ id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => descripcionTrabajo(r) },`. «Editar modelo» no cambia
   (`claveActual={aEditar?.modelo ?? null}`: el código del modelo).

- [ ] **Step 5: IMEIs**

En `src/modules/taller/lib/grupoImei.ts`:
1. En el tipo `GrupoImei`, tras `  modelo: string`, añade:

```ts
  /** 0.9.21: la capacidad y el color de NSYS del IMEI (null fuera de NSYS). */
  capacidad: string | null
  color: string | null
```

2. Tras `const primero = …`, añade:

```ts
/** 0.9.21: el móvil de los trabajos de un IMEI: el primer modelo, la primera capacidad y el primer color no vacíos (las tres
 *  listas traen lo mismo del IMEI; si una no pudo leer NSYS, vale lo de otra). */
export function movilDe(trabajos: ReparacionResumen[]): { modelo: string; capacidad: string | null; color: string | null } {
  return {
    modelo: primero(trabajos.map((t) => t.modelo)) ?? '',
    capacidad: primero(trabajos.map((t) => t.capacidad)),
    color: primero(trabajos.map((t) => t.color)),
  }
}
```

3. En `construir`, sustituye `    modelo: primero(trabajos.map((t) => t.modelo)) ?? '',` por `    ...movilDe(trabajos),`.

En `src/modules/taller/imeis/ImeisPage.tsx`: el import pasa a `import { descripcionTrabajo } from '../lib/modelos'`; en
`textoCeldaGrupo`, `case 'modelo': return descripcionTrabajo(g)`; la columna, a
`{ id: 'modelo', header: 'Modelo', size: 150, accessorFn: (g) => descripcionTrabajo(g) },`. El aviso «También hay N móviles sin
trabajos…» no cambia.

En `src/modules/taller/imeis/ImeiDetallePage.tsx`: tras `import { estadoIncidencia } from '../lib/filtros'`, añade
`import { movilDe } from '../lib/grupoImei'`; `import { traducirModelo } from '../lib/modelos'` pasa a
`import { descripcionTrabajo } from '../lib/modelos'`; y sustituye

```tsx
  const modelo = traducirModelo(trabajos.find((t) => t.imei === imei && t.modelo)?.modelo)
```

por

```tsx
  // 0.9.21: «iPhone 16 128GB Negro», con la capacidad y el color de NSYS de los trabajos del IMEI.
  const modelo = descripcionTrabajo(movilDe(trabajos.filter((t) => t.imei === imei)))
```

- [ ] **Step 6: Ejecutar los tests y ver que pasan**

Run: `npx vitest run src/modules/taller/asignaciones/columnas.test.tsx src/modules/taller/pendientes src/modules/taller/historial/columnasTrabajo.test.ts src/modules/taller/historial/HistorialPulidosPage.test.tsx src/modules/taller/lib/grupoImei.test.ts src/modules/taller/imeis/ImeisPage.test.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx src/modules/taller/historial/HistorialPage.test.tsx src/modules/taller/asignaciones/AsignacionesPage.test.tsx`
Expected: PASS (los tests de antes siguen igual: sus filas no traen NSYS).

Run: `npx tsc -b && npm run lint`
Expected: limpios (sin imports sin usar).

- [ ] **Step 7: Commit**

```bash
git add src/modules/taller/asignaciones/columnas.tsx src/modules/taller/asignaciones/columnas.test.tsx \
        src/modules/taller/pendientes src/modules/taller/historial src/modules/taller/lib/grupoImei.ts \
        src/modules/taller/lib/grupoImei.test.ts src/modules/taller/imeis/ImeisPage.tsx src/modules/taller/imeis/ImeisPage.test.tsx \
        src/modules/taller/imeis/ImeiDetallePage.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx
git commit -m "feat: descripcion completa del movil en las listas del taller (0.9.21)"
```

---

### Task 5: Web — «Capacidad» y «Color» en los CSV con «Modelo»

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/asignaciones/csv.ts` (+ `csv.test.ts`, `AsignacionesPage.test.tsx`)
- Create: `gestion-reparaciones-web/src/modules/taller/pendientes/csvPulidos.ts` (+ `csvPulidos.test.ts`)
- Modify: `gestion-reparaciones-web/src/modules/taller/pendientes/PulidosPendientesPage.tsx`
- Modify: `gestion-reparaciones-web/src/modules/taller/historial/csvTrabajos.ts` (+ `csvTrabajos.test.ts`, nuevo),
  `HistorialPulidosPage.tsx`
- Modify: `gestion-reparaciones-web/src/modules/taller/imeis/csvImeis.ts` (+ `ImeisPage.test.tsx`)

**Interfaces:**
- Consumes: `ReparacionResumen.capacidad/color` y `GrupoImei.capacidad/color` (Tasks 3 y 4); `traducirModelo` de
  `modules/taller/lib/modelos.ts`.
- Produces:
  - `CABECERAS_ASIGNACIONES` con 16 columnas (`…, 'Modelo', 'Capacidad', 'Color', 'Fecha asignación', …`);
  - `NOMBRE_CSV_PULIDOS_PENDIENTES = 'pulidos_pendientes'`, `CABECERAS_PULIDOS_PENDIENTES` y
    `filaPulidoPendiente(r: ReparacionResumen): string[]` en `modules/taller/pendientes/csvPulidos.ts`;
  - `cabecerasHistorialPulidos(conTecnico: boolean): string[]` y `filaHistorialPulido(r: ReparacionResumen, conTecnico: boolean): string[]`
    en `modules/taller/historial/csvTrabajos.ts`;
  - `CABECERAS_RESUMEN` con 12 columnas (`'IMEI', 'Modelo', 'Capacidad', 'Color', 'Primera', …`).

- [ ] **Step 1: Tests (fallan)**

Sustituye **entero** `src/modules/taller/asignaciones/csv.test.ts` por:

```ts
import { describe, expect, it } from 'vitest'
import { glass, normal, resumen } from '../test/fabrica'
import { CABECERAS_ASIGNACIONES, filaAsignacionCsv, NOMBRE_CSV_ASIGNACIONES } from './csv'

// Índices de las columnas, en el orden de CABECERAS_ASIGNACIONES.
const TIPO = 1
const TECNICO = 2
const MODELO = 4
const CAPACIDAD = 5
const COLOR = 6
const FECHA = 7
const COMENTARIO = 8
const CLIENTE = 9
const ASIGNADO_POR = 10
const URGENTE = 11
const CHASIS = 12
const POR_CERRAR = 13
const ENTREGADO = 14
const EN_ESPERA = 15

describe('CSV de Asignaciones (calco de filaAsignacion del hotfix 0.16.3, con capacidad y color de NSYS desde la 0.9.21)', () => {
  it('nombre del fichero y las 16 cabeceras exactas en orden', () => {
    expect(NOMBRE_CSV_ASIGNACIONES).toBe('reparaciones_pendientes')
    expect(CABECERAS_ASIGNACIONES).toEqual([
      'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por',
      'Urgente', 'Chasis', 'Por cerrar', 'Entregado', 'En espera de pieza',
    ])
  })

  it('fila completa de una reparación: todos los campos, «Modelo» solo el modelo, fecha en hora de Madrid y "Sí" en las marcas', () => {
    const r = resumen({
      idRep: 'A20260916_1', imei: '000000000000000', nombreTecnico: 'tecnico-a', modelo: '14', capacidad: '128GB', color: 'Azul',
      fechaAsig: '2026-09-16T07:02:00', comentarioAsignacion: 'Pantalla rota', cliente: 'Cliente A', nombreTecnicoAsigna: 'tecnico-b',
      urgente: true, esChasis: true, porCerrar: true, glassAbierta: true, glassEntregadoAt: '2026-09-16T08:42:00',
      glassTecnicoNombre: 'tecnico-c', esSolicitud: 1, estadoSolicitud: 'PENDIENTE', stockSolicitud: 0,
    })
    expect(filaAsignacionCsv(r)).toEqual([
      'A20260916_1', 'Reparación', 'tecnico-a', '="000000000000000"', 'iPhone 14', '128GB', 'Azul', '16/09/2026 09:02',
      'Pantalla rota', 'Cliente A', 'tecnico-b', 'Sí', 'Sí', 'Sí', '16/09/2026 10:42', 'Sí',
    ])
  })

  it('fecha de asignación en invierno: UTC+1', () => {
    expect(filaAsignacionCsv(resumen({ fechaAsig: '2026-01-15T07:02:00' }))[FECHA]).toBe('15/01/2026 08:02')
  })

  it('tipo por prefijo: A → Reparación, AG → Glass, AP → Pulido', () => {
    expect(filaAsignacionCsv(resumen({ idRep: 'A20260916_1' }))[TIPO]).toBe('Reparación')
    expect(filaAsignacionCsv(resumen({ idRep: 'AG20260916_2' }))[TIPO]).toBe('Glass')
    expect(filaAsignacionCsv(resumen({ idRep: 'AP20260916_3' }))[TIPO]).toBe('Pulido')
  })

  it('nulos: técnico, modelo, capacidad, color, comentario y cliente vacíos; "Asignado por" nulo es "—"', () => {
    // El contrato declara nombreTecnico no nulo; el JavaFX lo protege igual (`!= null ? … : ""`) y aquí también.
    const fila = filaAsignacionCsv(resumen({
      nombreTecnico: null as unknown as string, modelo: null, capacidad: null, color: null, comentarioAsignacion: null, cliente: null,
      nombreTecnicoAsigna: null,
    }))
    expect(fila[TECNICO]).toBe('')
    expect(fila[MODELO]).toBe('')
    expect(fila[CAPACIDAD]).toBe('')
    expect(fila[COLOR]).toBe('')
    expect(fila[COMENTARIO]).toBe('')
    expect(fila[CLIENTE]).toBe('')
    expect(fila[ASIGNADO_POR]).toBe('—')
    expect(filaAsignacionCsv(resumen({ modelo: '' }))[MODELO]).toBe('')
  })

  it('marcas a false son "No"', () => {
    const fila = filaAsignacionCsv(resumen({ urgente: false, esChasis: false, porCerrar: false }))
    expect([fila[URGENTE], fila[CHASIS], fila[POR_CERRAR]]).toEqual(['No', 'No', 'No'])
  })

  it('Entregado: fecha de la entrega en A y AG, vacío sin entrega y en pulidos', () => {
    expect(filaAsignacionCsv(normal(true, '2026-08-28T08:42:00'))[ENTREGADO]).toBe('28/08/2026 10:42')
    expect(filaAsignacionCsv(glass('2026-08-28T08:42:00'))[ENTREGADO]).toBe('28/08/2026 10:42')
    expect(filaAsignacionCsv(normal(true, null))[ENTREGADO]).toBe('')
    expect(filaAsignacionCsv(resumen({ idRep: 'AP20260828_1', entregadoAt: '2026-08-28T08:42:00' }))[ENTREGADO]).toBe('')
  })

  it('en espera de pieza: sin solicitud No; solicitud sin gestionar Sí; gestionada sin stock Sí; gestionada con stock No', () => {
    const espera = (p: Parameters<typeof resumen>[0]) => filaAsignacionCsv(resumen(p))[EN_ESPERA]
    expect(espera({ esSolicitud: 0, estadoSolicitud: null, stockSolicitud: 0 })).toBe('No')
    expect(espera({ esSolicitud: 2, estadoSolicitud: 'PENDIENTE', stockSolicitud: 5 })).toBe('Sí')
    expect(espera({ esSolicitud: 1, estadoSolicitud: 'GESTIONADA', stockSolicitud: 0 })).toBe('Sí')
    expect(espera({ esSolicitud: 1, estadoSolicitud: 'GESTIONADA', stockSolicitud: 3 })).toBe('No')
  })
})
```

En `src/modules/taller/asignaciones/AsignacionesPage.test.tsx`, en el test de «Descargar CSV»:
1. `it('"Descargar CSV" exporta reparaciones_pendientes con las 14 columnas y solo las filas visibles', …` pasa a
   `it('"Descargar CSV" exporta reparaciones_pendientes con las 16 columnas y solo las filas visibles', …`.
2. Sustituye

```tsx
    expect(cabeceras).toEqual([
      'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por', 'Urgente',
      'Chasis', 'Por cerrar', 'Entregado', 'En espera de pieza',
    ])
```

por

```tsx
    expect(cabeceras).toEqual([
      'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por',
      'Urgente', 'Chasis', 'Por cerrar', 'Entregado', 'En espera de pieza',
    ])
```

3. Sustituye
   `['AP20260916_3', 'Pulido', 'Técnico A', '="000000000000003"', 'iPhone 14', '16/09/2026 09:02', '', 'CLIENTE A', 'Técnico F', 'No', 'No', 'No', '', 'No'],`
   por
   `['AP20260916_3', 'Pulido', 'Técnico A', '="000000000000003"', 'iPhone 14', '', '', '16/09/2026 09:02', '', 'CLIENTE A', 'Técnico F', 'No', 'No', 'No', '', 'No'],`.

Crea `src/modules/taller/pendientes/csvPulidos.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { resumen } from '../test/fabrica'
import { CABECERAS_PULIDOS_PENDIENTES, filaPulidoPendiente, NOMBRE_CSV_PULIDOS_PENDIENTES } from './csvPulidos'

describe('CSV pulidos_pendientes (0.9.21: «Capacidad» y «Color» detrás de «Modelo»)', () => {
  it('nombre y cabeceras', () => {
    expect(NOMBRE_CSV_PULIDOS_PENDIENTES).toBe('pulidos_pendientes')
    expect(CABECERAS_PULIDOS_PENDIENTES).toEqual(['ID', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Fecha asig.', 'Comentario'])
  })

  it('la fila, con «Modelo» solo el modelo; sin NSYS ni modelo, vacías', () => {
    const r = resumen({ idRep: 'AP20261022_1', imei: '350000000000001', modelo: '13', capacidad: '128GB', color: 'Azul',
      fechaAsig: '2026-10-22T08:00:00', comentarioAsignacion: 'rayado' })
    expect(filaPulidoPendiente(r)).toEqual(['AP20261022_1', '="350000000000001"', 'iPhone 13', '128GB', 'Azul', '22/10/2026 10:00', 'rayado'])
    expect(filaPulidoPendiente({ ...r, modelo: null, capacidad: null, color: null, comentarioAsignacion: null }))
      .toEqual(['AP20261022_1', '="350000000000001"', '', '', '', '22/10/2026 10:00', ''])
  })
})
```

Crea `src/modules/taller/historial/csvTrabajos.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { resumen } from '../test/fabrica'
import { cabecerasHistorialPulidos, filaHistorialPulido } from './csvTrabajos'

describe('CSV historial_pulidos (0.9.21: «Capacidad» y «Color» detrás de «Modelo»)', () => {
  const pulido = resumen({ idRep: 'P20261022_1', imei: '350000000000001', modelo: '13', capacidad: '128GB', color: 'Azul',
    nombreTecnico: 'Técnico 2', fechaAsig: '2026-10-22T08:00:00', fechaFin: '2026-10-22T08:30:00', comentarioAsignacion: 'rayado' })

  it('cabeceras con «Técnico» (supertécnico y admin) y sin él (técnico)', () => {
    expect(cabecerasHistorialPulidos(true)).toEqual(['ID', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Técnico', 'Fecha inicio', 'Fecha fin', 'Comentario'])
    expect(cabecerasHistorialPulidos(false)).toEqual(['ID', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Fecha inicio', 'Fecha fin', 'Comentario'])
  })

  it('la fila: el modelo solo en «Modelo» y la capacidad y el color en las suyas; sin NSYS, vacías', () => {
    expect(filaHistorialPulido(pulido, true))
      .toEqual(['P20261022_1', '="350000000000001"', 'iPhone 13', '128GB', 'Azul', 'Técnico 2', '22/10/2026 10:00', '22/10/2026 10:30', 'rayado'])
    expect(filaHistorialPulido({ ...pulido, capacidad: null, color: null }, false))
      .toEqual(['P20261022_1', '="350000000000001"', 'iPhone 13', '', '', '22/10/2026 10:00', '22/10/2026 10:30', 'rayado'])
  })
})
```

En `src/modules/taller/imeis/ImeisPage.test.tsx`, en el test `'CSV agrupado_resumen con los grupos visibles'`:
1. Tras `    const descargar = vi.spyOn(csv, 'descargarCsv').mockResolvedValue(undefined)`, añade:

```tsx
    // 0.9.21: B está en NSYS (128GB Azul); A no.
    server.use(http.get('*/api/reparaciones/historial', () =>
      HttpResponse.json(reps.map((r) => (r.imei === B ? { ...r, capacidad: '128GB', color: 'Azul' } : r)))))
```

2. Sustituye

```tsx
    expect(cabeceras).toEqual(['IMEI', 'Modelo', 'Primera', 'Última', 'Reparaciones', 'Glass', 'Pulidos', 'Inc. abiertas', 'Observación', 'Cliente'])
    // Fila B: la única con Observación y Cliente no vacíos (la de A los tiene vacíos, ver abajo).
    expect(filasCsv[0]).toEqual([`="${B}"`, 'iPhone 14', '16/09/2026', '16/09/2026', '1', '0', '0', '0', 'tapa rayada', 'WEB'])
    expect(filasCsv[1]).toEqual([`="${A}"`, 'iPhone 16', '10/09/2026', '13/09/2026', '1', '1', '1', '1', '', ''])
```

por

```tsx
    expect(cabeceras).toEqual(['IMEI', 'Modelo', 'Capacidad', 'Color', 'Primera', 'Última', 'Reparaciones', 'Glass', 'Pulidos', 'Inc. abiertas', 'Observación', 'Cliente'])
    // Fila B: la única con Observación y Cliente no vacíos (la de A los tiene vacíos, ver abajo) y la única en NSYS.
    expect(filasCsv[0]).toEqual([`="${B}"`, 'iPhone 14', '128GB', 'Azul', '16/09/2026', '16/09/2026', '1', '0', '0', '0', 'tapa rayada', 'WEB'])
    expect(filasCsv[1]).toEqual([`="${A}"`, 'iPhone 16', '', '', '10/09/2026', '13/09/2026', '1', '1', '1', '1', '', ''])
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `npx vitest run src/modules/taller/asignaciones/csv.test.ts src/modules/taller/asignaciones/AsignacionesPage.test.tsx src/modules/taller/pendientes/csvPulidos.test.ts src/modules/taller/historial/csvTrabajos.test.ts src/modules/taller/imeis/ImeisPage.test.tsx`
Expected: FAIL: cabeceras y filas sin «Capacidad» ni «Color», y no existen `csvPulidos.ts`, `cabecerasHistorialPulidos` ni
`filaHistorialPulido`.

- [ ] **Step 3: CSV de Asignaciones**

En `src/modules/taller/asignaciones/csv.ts`, sustituye el comentario y las cabeceras por:

```ts
/** Calco de ReparacionControllerSuperTecnico.exportarCSV con la tabla de asignaciones visible (hotfix 0.16.3, :1170-1186):
 *  fichero `reparaciones_pendientes`, las filas visibles tras los filtros y estas 16 columnas: las 14 del JavaFX (spec SP6 §6.5,
 *  G7) y, desde la 0.9.21, «Capacidad» y «Color» de NSYS detrás de «Modelo» (spec «Descripción completa donde pone «Modelo»»). */
export const NOMBRE_CSV_ASIGNACIONES = 'reparaciones_pendientes'

export const CABECERAS_ASIGNACIONES = [
  'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por',
  'Urgente', 'Chasis', 'Por cerrar', 'Entregado', 'En espera de pieza',
]
```

y, en `filaAsignacionCsv`, tras `    traducirModelo(r.modelo),`, añade:

```ts
    r.capacidad ?? '',
    r.color ?? '',
```

- [ ] **Step 4: CSV de Pulidos pendientes**

Crea `src/modules/taller/pendientes/csvPulidos.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { textoForzado } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'
import { traducirModelo } from '../lib/modelos'

/** CSV de «Mis pulidos pendientes» (ficha docs/paridad/pendientes.md): las filas visibles. Desde la 0.9.21, con «Capacidad» y
 *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo. */
export const NOMBRE_CSV_PULIDOS_PENDIENTES = 'pulidos_pendientes'
export const CABECERAS_PULIDOS_PENDIENTES = ['ID', 'IMEI', 'Modelo', 'Capacidad', 'Color', 'Fecha asig.', 'Comentario']

export function filaPulidoPendiente(r: ReparacionResumen): string[] {
  return [
    r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), r.capacidad ?? '', r.color ?? '',
    formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'), r.comentarioAsignacion ?? '',
  ]
}
```

En `src/modules/taller/pendientes/PulidosPendientesPage.tsx`:
1. `import { descargarCsv, textoForzado } from '@/shared/lib/csv'` pasa a `import { descargarCsv } from '@/shared/lib/csv'`.
2. `import { descripcionTrabajo, traducirModelo } from '../lib/modelos'` pasa a `import { descripcionTrabajo } from '../lib/modelos'`.
3. Tras `import { FMT_PENDIENTES, textoCeldaPendiente } from './textoCelda'`, añade
   `import { CABECERAS_PULIDOS_PENDIENTES, filaPulidoPendiente, NOMBRE_CSV_PULIDOS_PENDIENTES } from './csvPulidos'`.
4. Sustituye

```tsx
  useRegistrarExportable(() => {
    descargarCsv('pulidos_pendientes', ['ID', 'IMEI', 'Modelo', 'Fecha asig.', 'Comentario'],
      visibles.map((r) => [r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'), r.comentarioAsignacion ?? '']))
  })
```

por

```tsx
  useRegistrarExportable(() => descargarCsv(NOMBRE_CSV_PULIDOS_PENDIENTES, CABECERAS_PULIDOS_PENDIENTES, visibles.map(filaPulidoPendiente)))
```

- [ ] **Step 5: CSV del Historial de pulidos**

En `src/modules/taller/historial/csvTrabajos.ts`, tras `import { formatear } from '@/shared/lib/fechas'`, añade
`import { traducirModelo } from '../lib/modelos'` y, al final del fichero:

```ts
/** CSV `historial_pulidos` con las filas visibles: «Técnico» solo para supertécnico y admin. Desde la 0.9.21, con «Capacidad» y
 *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo. */
export function cabecerasHistorialPulidos(conTecnico: boolean): string[] {
  return ['ID', 'IMEI', 'Modelo', 'Capacidad', 'Color', ...(conTecnico ? ['Técnico'] : []), 'Fecha inicio', 'Fecha fin', 'Comentario']
}

export function filaHistorialPulido(r: ReparacionResumen, conTecnico: boolean): string[] {
  return [
    r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), r.capacidad ?? '', r.color ?? '', ...(conTecnico ? [r.nombreTecnico ?? ''] : []),
    formatear(r.fechaAsig, FMT), formatear(r.fechaFin, FMT), r.comentarioAsignacion ?? '',
  ]
}
```

En `src/modules/taller/historial/HistorialPulidosPage.tsx`:
1. `import { descargarCsv, textoForzado } from '@/shared/lib/csv'` pasa a `import { descargarCsv } from '@/shared/lib/csv'`.
2. Tras `import { TOGGLES_HISTORIAL } from './constantes'`, añade
   `import { cabecerasHistorialPulidos, filaHistorialPulido } from './csvTrabajos'`.
3. Sustituye

```tsx
  useRegistrarExportable(() => {
    const cabeceras = ['ID', 'IMEI', 'Modelo', ...(global ? ['Técnico'] : []), 'Fecha inicio', 'Fecha fin', 'Comentario']
    descargarCsv('historial_pulidos', cabeceras, visibles.map((r) => [
      r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), ...(global ? [r.nombreTecnico ?? ''] : []),
      formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'), formatear(r.fechaFin, 'dd/MM/yyyy HH:mm'), r.comentarioAsignacion ?? '',
    ]))
  })
```

por

```tsx
  useRegistrarExportable(() => descargarCsv('historial_pulidos', cabecerasHistorialPulidos(global), visibles.map((r) => filaHistorialPulido(r, global))))
```

(`traducirModelo` se queda en el import: lo usa `OPCIONES_MODELO`.)

- [ ] **Step 6: CSV del resumen de IMEIs**

En `src/modules/taller/imeis/csvImeis.ts`, sustituye

```ts
/** CSV del maestro (agrupado_resumen), sin "Revisión logística" (diferencia aceptada). */
export const CABECERAS_RESUMEN = ['IMEI', 'Modelo', 'Primera', 'Última', 'Reparaciones', 'Glass', 'Pulidos', 'Inc. abiertas', 'Observación', 'Cliente']
export function filaResumen(g: GrupoImei): string[] {
  return [
    textoForzado(g.imei), traducirModelo(g.modelo), formatear(g.fechaMasAntigua, 'dd/MM/yyyy'), formatear(g.fechaMasReciente, 'dd/MM/yyyy'),
```

por

```ts
/** CSV del maestro (agrupado_resumen), sin "Revisión logística" (diferencia aceptada). Desde la 0.9.21, con «Capacidad» y
 *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo. */
export const CABECERAS_RESUMEN = ['IMEI', 'Modelo', 'Capacidad', 'Color', 'Primera', 'Última', 'Reparaciones', 'Glass', 'Pulidos', 'Inc. abiertas', 'Observación', 'Cliente']
export function filaResumen(g: GrupoImei): string[] {
  return [
    textoForzado(g.imei), traducirModelo(g.modelo), g.capacidad ?? '', g.color ?? '',
    formatear(g.fechaMasAntigua, 'dd/MM/yyyy'), formatear(g.fechaMasReciente, 'dd/MM/yyyy'),
```

(`CABECERAS_DETALLE`/`filaDetalle`, el CSV `agrupado_<imei>`, no cambian.)

- [ ] **Step 7: Ejecutar los tests y ver que pasan**

Run: `npx vitest run src/modules/taller/asignaciones/csv.test.ts src/modules/taller/asignaciones/AsignacionesPage.test.tsx src/modules/taller/pendientes src/modules/taller/historial src/modules/taller/imeis`
Expected: PASS.

Run: `npx tsc -b && npm run lint`
Expected: limpios (sin imports sin usar en las dos páginas de pulidos).

- [ ] **Step 8: Commit**

```bash
git add src/modules/taller/asignaciones/csv.ts src/modules/taller/asignaciones/csv.test.ts \
        src/modules/taller/asignaciones/AsignacionesPage.test.tsx src/modules/taller/pendientes/csvPulidos.ts \
        src/modules/taller/pendientes/csvPulidos.test.ts src/modules/taller/pendientes/PulidosPendientesPage.tsx \
        src/modules/taller/historial/csvTrabajos.ts src/modules/taller/historial/csvTrabajos.test.ts \
        src/modules/taller/historial/HistorialPulidosPage.tsx src/modules/taller/imeis/csvImeis.ts \
        src/modules/taller/imeis/ImeisPage.test.tsx
git commit -m "feat: capacidad y color en los csv que llevan modelo (0.9.21)"
```

---

### Task 6: Cierre — documentación y suites completas

**Files:**
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (entrada `[0.9.21]`), `docs/paridad/asignaciones.md`, `docs/paridad/pendientes.md`,
  `docs/paridad/historial.md`, `docs/paridad/imeis.md`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md`
- Modify: `docs/novedades/NOVEDADES-v0.9.21.md` (raíz; lo sube el controlador con los gitlinks)
- Modify: `Apuntes/plan-futuro.md` (`C:/Users/dev/Documents/Apuntes/plan-futuro.md`, fuera de los repos)

**Interfaces:**
- Consumes: todo lo de las Tasks 1-5.
- Produces: las ramas `dev_v0921_descripcion` (servidor y web) listas para la revisión y el merge.

- [ ] **Step 1: CHANGELOG de la web**

En `CHANGELOG.md`, dentro de `## [0.9.21] - 2026-10-XX — …`, justo **antes** de la línea
«- Requiere el servidor de la 0.9.21 y la migración `migracion-sku-taller.sql` …» (el título y la fecha no se tocan), añade:

```markdown
- **Listas del taller: el móvil entero donde ponía el modelo** (todos los roles, también el técnico): Asignaciones, Pendientes, Pulidos pendientes (también su confirmación), el Historial de trabajos y de pulidos, IMEIs y la barra del detalle del IMEI pasan de «iPhone 13» a **«iPhone 13 128GB Azul»**: la capacidad y el color de NSYS (el color que vale, con lo del taller encima), también en «Copiar celda». Sin grado ni marca «taller». Un móvil que no está en NSYS sigue con el modelo solo; en el historial sale el color de hoy. Los CSV `reparaciones_pendientes`, `pulidos_pendientes`, `historial_pulidos` y `agrupado_resumen` ganan «Capacidad» y «Color» detrás de «Modelo». Servidor y web, sin migración.
```

- [ ] **Step 2: Paridad**

En cada uno de los cuatro ficheros, justo después de la primera viñeta de «## Diferencias deliberadas respecto al JavaFX» (la
de las fechas, «- **Diferencia deliberada:** fechas en …»), añade la viñeta indicada, y cambia las líneas que se dicen:

**`docs/paridad/asignaciones.md`**
- Viñeta:

```markdown
- **Diferencia deliberada (0.9.21):** la columna «Modelo» y su «Copiar celda» llevan la descripción completa del móvil, «iPhone 13 128GB Azul»: el modelo del taller con la capacidad y el color de NSYS (el que vale, con lo del taller encima; spec raíz `docs/superpowers/specs/2026-10-22-v0921-descripcion-completa-design.md`). Sin NSYS, el modelo solo. El JavaFX solo pone el modelo. El CSV gana «Capacidad» y «Color» detrás de «Modelo». Pruebas: `columnas.test.tsx`, `pendientes/textoCelda.test.ts` y `csv.test.ts`.
```

- En «## Las once columnas», la línea

```text
- [x] Modelo: nombre traducido del código del modelo (vacío si no hay).
```

  pasa a

```text
- [x] Modelo: nombre traducido del código del modelo (vacío si no hay) y, desde la 0.9.21, la capacidad y el color de NSYS detrás (diferencia deliberada, arriba).
```

- En la primera viñeta de «## CSV», tres reemplazos dentro de la misma línea (el resto, igual):

```text
y las 14 columnas `ID;Tipo;Técnico;IMEI;Modelo;Fecha asignación;
→ y las 16 columnas `ID;Tipo;Técnico;IMEI;Modelo;Capacidad;Color;Fecha asignación;

modelo traducido o vacío;
→ modelo traducido o vacío; «Capacidad» y «Color» de NSYS o vacíos (0.9.21, diferencia deliberada);

las 14 columnas las cubre el test
→ las 16 columnas las cubre el test
```

**`docs/paridad/pendientes.md`**
- Viñeta:

```markdown
- **Diferencia deliberada (0.9.21):** la columna «Modelo» de las tres pestañas, su «Copiar celda» y la confirmación de «Completar seleccionados» («IMEI · modelo») llevan la descripción completa del móvil, «iPhone 13 128GB Azul»: la capacidad y el color de NSYS (el que vale; spec raíz `docs/superpowers/specs/2026-10-22-v0921-descripcion-completa-design.md`). Sin NSYS, el modelo solo. El CSV `pulidos_pendientes` gana «Capacidad» y «Color» detrás de «Modelo»; `mis_pendientes` no cambia.
```

- Dos reemplazos (el resto de cada línea, igual):

```text
- [x] Modelo traducido (`traducirModelo`: "12promax" → "iPhone 12 Pro Max"; vacío si no hay).
→ - [x] Modelo traducido (`traducirModelo`: "12promax" → "iPhone 12 Pro Max"; vacío si no hay) y, desde la 0.9.21, con la capacidad y el color de NSYS detrás (diferencia deliberada, arriba).

- [x] CSV `pulidos_pendientes`: "ID", "IMEI" (`="…"`), "Modelo", "Fecha asig."
→ - [x] CSV `pulidos_pendientes`: "ID", "IMEI" (`="…"`), "Modelo", "Capacidad", "Color" (0.9.21), "Fecha asig."
```

**`docs/paridad/historial.md`**
- Viñeta:

```markdown
- **Diferencia deliberada (0.9.21):** la columna «Modelo» (Reparaciones, Glass y Pulidos) y su «Copiar celda» llevan la descripción completa del móvil, «iPhone 13 128GB Azul», con la capacidad y el color **de hoy** de NSYS (el que vale), no los de cuando se hizo el trabajo (spec raíz `docs/superpowers/specs/2026-10-22-v0921-descripcion-completa-design.md`). Sin NSYS, el modelo solo. El CSV `historial_pulidos` gana «Capacidad» y «Color» detrás de «Modelo»; los `historial_*`/`mis_*` de trabajos no cambian.
```

- En la viñeta del CSV `historial_pulidos` (el resto de la línea, igual):

```text
"Modelo", ["Técnico" solo SUPERTECNICO y ADMIN]
→ "Modelo", "Capacidad", "Color" (0.9.21), ["Técnico" solo SUPERTECNICO y ADMIN]
```

**`docs/paridad/imeis.md`**
- Viñeta:

```markdown
- **Diferencia deliberada (0.9.21):** el «Modelo» del maestro (el del grupo), la columna «Modelo» del detalle, sus «Copiar celda» y la barra «IMEI · modelo» del detalle llevan la descripción completa del móvil, «iPhone 13 128GB Azul»: la capacidad y el color de hoy de NSYS (el que vale; spec raíz `docs/superpowers/specs/2026-10-22-v0921-descripcion-completa-design.md`), para todos los roles. Sin NSYS, el modelo solo. El CSV `agrupado_resumen` gana «Capacidad» y «Color» detrás de «Modelo»; `agrupado_<imei>` no cambia.
```

- En la viñeta del CSV `agrupado_resumen` (el resto de la línea, igual):

```text
"IMEI" (`="…"`), "Modelo", "Primera"
→ "IMEI" (`="…"`), "Modelo", "Capacidad", "Color" (0.9.21), "Primera"
```

- [ ] **Step 3: Commit de la web**

```bash
git add CHANGELOG.md docs/paridad/asignaciones.md docs/paridad/pendientes.md docs/paridad/historial.md docs/paridad/imeis.md
git commit -m "docs: changelog y paridad de la descripcion completa del movil (0.9.21)"
```

- [ ] **Step 4: Contrato del servidor**

En `docs/api_contract.md` del worktree del servidor, justo antes de la viñeta
«- `GET /api/reparaciones/pendientes/contadores` → `ContadoresPendientes` …», añade:

```markdown
- Las seis listas del taller (`GET /api/reparaciones/historial`, `/api/reparaciones/asignaciones`, `/api/glass/historial`,
  `/api/glass/asignaciones`, `/api/pulidos/historial`, `/api/pulidos/asignaciones`) traen en `ReparacionResumen` la `capacidad`
  (de `Nsys_movil`) y el `color` que vale (lo del taller vigente o el de NSYS) de la fila más reciente del IMEI en el espejo de
  NSYS (0.9.21). Son `null` si el IMEI no está en el espejo, si la lectura falla (la lista sale igual y se apunta en el log) y en
  las demás rutas que devuelven `ReparacionResumen`. `modelo` sigue siendo el código de `Telefono.MODELO`.
```

```bash
git add docs/api_contract.md
git commit -m "docs: capacidad y color de las listas del taller en el contrato (0.9.21)"
```

- [ ] **Step 5: Novedades (raíz, sin commit)**

En `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.21.md`, tras la viñeta
«- Editar o borrar una reparación deshace lo que había cambiado (si nadie lo ha tocado después).» (la última de
«🔧 Formulario de reparación») y antes del `---` que la sigue, añade (con una línea en blanco antes):

```markdown
---

## 📋 Listas del taller

- Donde ponía solo el modelo, ahora pone el **móvil entero**: **«iPhone 13 128GB Azul»** (la capacidad y el color de NSYS, con
  el color que haya puesto el taller). En Asignaciones, Pendientes, Pulidos pendientes, el Historial, IMEIs y el detalle del
  IMEI, y también al copiar la celda.
- Los CSV que llevan «Modelo» traen al lado **«Capacidad»** y **«Color»**.
- Un móvil que no está en NSYS (de un cliente) sigue con el modelo solo. En el historial sale el color de hoy.
```

- [ ] **Step 6: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0921-desc`
Expected: sin fallos. Anota el total en el registro.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0921-desc`
Expected: lint sin errores, `tsc -b` limpio y Vitest en verde (los lentos conocidos, de uno en uno si hace falta). Anota el total.

Run (web): `npm run build`
Expected: build sin errores.

Run (web): `npx playwright test --list`
Expected: la lista de siempre, sin errores de compilación (esta pieza no añade E2E).

- [ ] **Step 7: Plan maestro y registro**

En `C:/Users/dev/Documents/Apuntes/plan-futuro.md` §5, en la entrada «**Descripción completa donde pone «Modelo»**», tras
«…donde se enchufa el color del taller.» (final de la entrada), añade: «**Va en la 0.9.21** (decisión del 22/10): spec y plan
`2026-10-22-v0921-descripcion-completa*` (raíz), ramas `dev_v0921_descripcion` en servidor y web; falta preprod (con la medida
del historial entero) y producción.» Sin marcar `[x]` hasta producción.

En `.superpowers/sdd/v0921-desc/progress.md`, los commits de las dos ramas (`git log --oneline main..dev_v0921_descripcion` en cada
worktree) y los totales de las suites.

---

## Después de este plan (controlador, con el OK del usuario)

No son tareas de este plan:
1. Revisión final de las dos ramas.
2. Merge `--no-ff` de `dev_v0921_descripcion` en `main` del servidor y de la web y push; en la raíz, gitlinks,
   `NOVEDADES-v0.9.21.md`, este plan marcado y el §7 de la spec.
3. Preproducción con la subida de la 0.9.21: un vistazo con datos reales a las pantallas y a los cuatro CSV, y la **medida del
   historial entero del supertécnico** (`GET /api/reparaciones/historial` sin `?tecnico=`) antes y después, contra el límite de
   15 s, que no se sube.
4. Producción con el resto de la 0.9.21 y el tag `v0.9.21`; los worktrees y las ramas se borran después.

## Preguntas para el usuario

1. **¿Se quedan las columnas «Modelo» con el ancho de hoy?** Con nombres largos («iPhone 13 Pro Max 256GB Azul Alpino») la
   celda puede cortarse con «…», sobre todo en el Historial con la ventana estrecha; «Copiar celda» copia el texto entero.
   Recomendación: dejarlas como están y mirarlo en preprod con datos reales; si molesta, se ensanchan en la misma 0.9.21.
