# Web Taller técnico (sub-proyecto 1) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrar a la web Pendientes (reparaciones, glass, pulidos), Historial (reparaciones, glass, pulidos) e IMEIs (maestro y detalle) con paridad contra las fichas `docs/paridad/{pendientes,historial,imeis}.md`, verificar `?tecnico=` contra el token en el servidor y pagar la deuda de Cimientos.

**Architecture:** Servidor: regla única `FiltroTecnico` en los seis endpoints de lectura, endpoint de contadores y contrato con `required`/`nullable` explícitos. Web: módulo `src/modules/taller/` con una vista por apartado y rutas propias (`/reparaciones/pendientes[/glass|/pulidos]`, `/reparaciones/historial[/glass|/pulidos]`, `/reparaciones/imeis[/:imei]`), lógica pura portada del JavaFX con sus tests, componentes compartidos nuevos en `src/shared/`, `DataTable` con `colgroup`, selección y virtualización; IMEIs se agrupa en la web sobre las tres consultas de historial ya cacheadas.

**Tech Stack:** Spring Boot 3.3.4 + springdoc 2.6.0 + JUnit 5/Mockito (servidor); React 19.3 + TypeScript 5.9 + Vite 8 + React Router 7.18 + TanStack Query 5.102 + TanStack Table 8.21 + `@tanstack/react-virtual` 3 (nueva) + Tailwind 4 + shadcn/ui + Vitest 5 + Testing Library + MSW 2 + Playwright (web).

**Spec:** `docs/superpowers/specs/2026-09-16-web-taller-design.md` (repo raíz). **Fichas (criterio de aceptación):** `gestion-reparaciones-web/docs/paridad/{pendientes,historial,imeis}.md` (cerradas con el usuario el 2026-09-16). **Capturas JavaFX:** `Apuntes/paridad-capturas/taller/` (fuera de los repos).

## Global Constraints

- Ramas: servidor `feature/web-taller` (se crea en la Task 1 desde `main` 74f663e); web `feature/web-taller` (ya existe, base 482847c con las fichas). El repo raíz se queda en `main` y solo recibe este plan y, al cerrar, los gitlinks.
- Commits **sin** `Co-Authored-By`. **Nunca** `git push`, merge ni tag: los hace el usuario o se piden con su OK explícito, uno a uno.
- Maven en Bash necesita: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`. Servidor: `mvn -q test` en verde antes de cada commit (los 140 tests actuales + los nuevos). Web: `npm run check` (lint + typecheck + tests) en verde antes de cada commit.
- Textos idénticos al JavaFX, tildes incluidas (los tests los comprueban literalmente). Colores solo por tokens de `src/shared/styles/tokens.css` (Tailwind `bg-<token>` / `text-<token>` / `border-<token>`); ningún color escrito a mano en un componente.
- Capas: `shared` no importa de `app` ni de `modules`; un módulo no importa de otro; desde la Task 5, `modules` tampoco importa de `app`.
- Nada sensible (IPs, secretos, nombres de personas, capturas reales) en los repos: todo eso va a `Apuntes/`.
- Versiones pinneadas en `package.json` (instalar con `--save-exact`). Única dependencia nueva: `@tanstack/react-virtual@3`.
- Rutas HTTP y cuerpos exactamente los del contrato `api/openapi.json` (regenerado en la Task 4); la web nunca envía `?tecnico=` siendo TECNICO.
- Tests: Vitest al lado de cada fichero (`X.test.ts(x)`), Testing Library + MSW (`server.use(http.get('*/api/...'))`) para las vistas, `renderConProviders` de `src/test/render.tsx` con `SESION_TEC` / `SESION_SUPER` / `SESION_ADMIN`.
- Cada tarea termina con el deliverable testado y committeado; un revisor debe poder aprobarla o rechazarla por separado.

---

## Mapa de ficheros

**Servidor (`gestion-reparaciones-servidor`)**

| Fichero | Responsabilidad |
|---|---|
| `src/main/java/com/reparaciones/servidor/security/FiltroTecnico.java` (nuevo) | Regla del `?tecnico=`: TECNICO forzado a lo suyo, 403 si pide otro |
| `controller/{ReparacionController,GlassController,PulidoController}.java` | Aplican `FiltroTecnico` en historial y asignaciones; `ReparacionController` añade `/pendientes/contadores` y tipa `referenciadora` |
| `model/ContadoresPendientes.java`, `model/ValorTexto.java` (nuevos) | Respuestas tipadas para el contrato |
| `dao/ReparacionDAO.java` | `contarPendientes(Integer idTec)` |
| `config/OpenApiConfig.java` | `OpenApiCustomizer` que marca todas las propiedades como `required` |
| `model/LoginResponse.java`, `model/ReparacionResumen.java`, records de petición | `@Schema(nullable = true)` donde puede venir `null` |
| `src/test/.../security/FiltroTecnicoTest.java`, `controller/FiltroTecnicoControllersTest.java`, `controller/ReparacionControllerContadoresTest.java`, `OpenApiContractTest.java` | Tests |

**Web (`gestion-reparaciones-web`)**

| Fichero | Responsabilidad |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` | Contrato regenerado (required/nullable) |
| `src/shared/api/client.ts` | Tipos `Cliente`, `LoginResponse`, `ReparacionResumen`, `Tecnico`, `ContadoresPendientes` sin `Required<>` |
| `src/shared/api/refresco.ts` | `useIntervaloRefresco` (60 s / 5 s) + contrato `errorUpdateCount` documentado |
| `src/shared/session/SessionProvider.tsx` (movido desde `app/session`), `src/shared/ui/exportable.tsx` (movido desde `app/shell`) | Sesión (`useSession`) y "Descargar CSV" delegado a la vista (`useRegistrarExportable`) |
| `src/shared/lib/{store,fechas,csv,filtroImei,tipoTrabajo}.ts` | Store externo, fechas Madrid, CSV, filtro IMEI, tipo de trabajo |
| `src/shared/ui/DataTable.tsx` | Tabla con `colgroup`, ajuste fijo/estirar, selección, teclado, ordenación opcional, virtualización, celda pulsada |
| `src/shared/ui/{TogglePill,FiltroImei,RangoFechas,BadgeTipo,CeldaFechas,TextoExpandible,SelectorLista,EtiquetaActualizado,MenuCopiarCelda}.tsx`, `MultiSelect.tsx` (+`textoTodas`), `ConfirmDialog.tsx` (+motivo) | Compartidos |
| `src/shared/styles/tokens.css` | Tokens nuevos (superficie, tipos de trabajo, entrega, badges, fila maestro, textos secundarios) |
| `src/app/shell/{ConnectionBanner,SubNav}.tsx`, `src/app/router.tsx` | Región viva fija; columna lateral por rol con badge; rutas del taller |
| `src/modules/taller/api.ts` | Hooks de lectura y mutaciones del módulo |
| `src/modules/taller/estado.ts` | Stores de filtros compartidos entre rutas (IMEI, técnico, fechas, incidencias, último IMEI visto) |
| `src/modules/taller/rutas.tsx` | `InicioReparaciones` (redirección por rol) y `RequiereTecnico` |
| `src/modules/taller/lib/{piezas,modelos,entregaGlass,grupoImei,estadoPendiente,filtros}.ts` | Lógica pura con tests portados |
| `src/modules/taller/componentes/{BadgePendientes,TogglesPendientes,CeldaImeiPendiente,BadgesEstadoPendiente,BotonPapelera,CeldaReparador,CeldaIncidencia,CeldaEstadoTrabajo,DialogoIncidencia,DialogoObservacion,MenuHistorial,useAccionesTrabajo}.tsx` | Piezas propias del módulo |
| `src/modules/taller/pendientes/{MenuPendiente,PendientesPage,PulidosPendientesPage}.tsx`, `textoCelda.ts` (+ tests) | Pendientes |
| `src/modules/taller/historial/{columnasTrabajo,HistorialPage,HistorialPulidosPage}.tsx`, `csvTrabajos.ts` (+ tests) | Historial |
| `src/modules/taller/imeis/{agrupacion,csvImeis,useTrabajos}.ts`, `{BarraFiltrosImeis,ImeisPage,ImeiDetallePage}.tsx` (+ tests) | IMEIs (maestro y detalle) |
| `src/shared/ui/{Botones,PildoraContador}.tsx`, `src/modules/taller/test/fabrica.ts` | Botones del JavaFX, píldora contador, fábrica de filas para tests |
| `tests/e2e/taller.spec.ts`, `.env.e2e.example`, `CHANGELOG.md`, `README.md`, `package.json` (0.2.0), `docs/paridad/*.md` | Cierre |

---

### Task 1: Servidor — `FiltroTecnico` y `?tecnico=` verificado en los seis endpoints

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/security/FiltroTecnico.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (métodos `getHistorial` y `getAsignaciones`), `GlassController.java` (`getAsignaciones`, `getHistorial`), `PulidoController.java` (`getAsignaciones`, `getHistorial`)
- Test: `src/test/java/com/reparaciones/servidor/security/FiltroTecnicoTest.java`, `src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java`

**Interfaces:**
- Produces: `FiltroTecnico.efectivo(UsuarioPrincipal principal, Integer tecnico): Integer` (null = sin filtro; lanza `ResponseStatusException(403, "Solo puedes consultar tus propios trabajos")`); constante `FiltroTecnico.MSG_SOLO_PROPIOS`.
- Consumes: `UsuarioPrincipal.getRol()`, `getIdTec()` (existentes).

- [ ] **Step 1: Crear la rama del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git rev-parse --short HEAD   # 74f663e
git checkout -b feature/web-taller
```

- [ ] **Step 2: Escribir el test unitario de la regla**

`src/test/java/com/reparaciones/servidor/security/FiltroTecnicoTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Regla del ?tecnico= (spec web-taller 2026-09-16 §5.1). */
class FiltroTecnicoTest {

    private static final UsuarioPrincipal TECNICO = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private static final UsuarioPrincipal SUPER   = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private static final UsuarioPrincipal ADMIN   = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void tecnicoSinParametroRecibeLoSuyo() {
        assertEquals(4, FiltroTecnico.efectivo(TECNICO, null));
    }

    @Test void tecnicoPidiendoseASiMismoRecibeLoSuyo() {
        assertEquals(4, FiltroTecnico.efectivo(TECNICO, 4));
    }

    @Test void tecnicoPidiendoAOtroEs403ConMensaje() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> FiltroTecnico.efectivo(TECNICO, 9));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(FiltroTecnico.MSG_SOLO_PROPIOS, ex.getReason());
    }

    @Test void tecnicoSinIdTecNoRecibeNada() {
        UsuarioPrincipal raro = new UsuarioPrincipal(9, "x", "x", "TECNICO", null);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> FiltroTecnico.efectivo(raro, null)).getStatusCode().value());
    }

    @Test void supertecnicoYAdminMantienenElFiltroLibre() {
        assertNull(FiltroTecnico.efectivo(SUPER, null));
        assertEquals(9, FiltroTecnico.efectivo(SUPER, 9));
        assertNull(FiltroTecnico.efectivo(ADMIN, null));
        assertEquals(9, FiltroTecnico.efectivo(ADMIN, 9));
    }
}
```

- [ ] **Step 3: Ejecutarlo y ver que falla por compilación**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q test -Dtest=FiltroTecnicoTest
```
Expected: `COMPILATION ERROR ... cannot find symbol: class FiltroTecnico`.

- [ ] **Step 4: Implementar `FiltroTecnico`**

`src/main/java/com/reparaciones/servidor/security/FiltroTecnico.java`:

```java
package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regla única del parámetro {@code ?tecnico=} de las listas del taller (spec web-taller 2026-09-16 §5.1):
 * un TECNICO solo consulta lo suyo (sin parámetro o con su propio id); pedir otro técnico es 403.
 * SUPERTECNICO y ADMIN mantienen el filtro libre (sin parámetro = todo). Cualquier rol que no sea
 * SUPERTECNICO ni ADMIN se trata como TECNICO, por prudencia.
 */
public final class FiltroTecnico {

    public static final String MSG_SOLO_PROPIOS = "Solo puedes consultar tus propios trabajos";

    private FiltroTecnico() {}

    /**
     * @param principal usuario del token (las rutas exigen sesión, nunca es nulo)
     * @param tecnico   valor de {@code ?tecnico=}, o {@code null} si no vino
     * @return el filtro que debe aplicar el DAO ({@code null} = sin filtro)
     * @throws ResponseStatusException 403 si un técnico pide un técnico distinto del suyo (o no tiene técnico)
     */
    public static Integer efectivo(UsuarioPrincipal principal, Integer tecnico) {
        String rol = principal.getRol();
        if ("SUPERTECNICO".equals(rol) || "ADMIN".equals(rol)) return tecnico;
        Integer propio = principal.getIdTec();
        if (propio == null || (tecnico != null && !tecnico.equals(propio))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SOLO_PROPIOS);
        }
        return propio;
    }
}
```

- [ ] **Step 5: Ejecutar el test y ver que pasa**

```bash
mvn -q test -Dtest=FiltroTecnicoTest
```
Expected: `Tests run: 5, Failures: 0`.

- [ ] **Step 6: Escribir el test de los controllers (falla porque las firmas aún no reciben el principal)**

`src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Los seis endpoints de lectura del taller pasan el ?tecnico= por FiltroTecnico (spec web-taller §5.1). */
class FiltroTecnicoControllersTest {

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final ReparacionController rep = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), mock(LogDAO.class), mock(BorradorDAO.class),
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class));
    private final GlassController glass = new GlassController(dao, mock(LogDAO.class), mock(TelefonoDAO.class), mock(ImeiLookupService.class));
    private final PulidoController pulido = new PulidoController(dao, mock(LogDAO.class), mock(TelefonoDAO.class), mock(ImeiLookupService.class));

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

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
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoYAdminConservanElFiltroLibre() {
        rep.getHistorial(null, supertecnico);   verify(dao).getHistorial(null);
        rep.getHistorial(9, supertecnico);      verify(dao).getHistorial(9);
        pulido.getAsignaciones(null, admin);    verify(dao).getAsignacionesPulido(null);
        glass.getAsignaciones(9, admin);        verify(dao).getAsignacionesGlass(9);
    }

    private static int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }
}
```

- [ ] **Step 7: Aplicar la regla en los controllers**

En `ReparacionController.java` sustituir los dos métodos:

```java
    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getHistorial(FiltroTecnico.efectivo(principal, tecnico));
    }
```
y
```java
    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getAsignaciones(FiltroTecnico.efectivo(principal, tecnico));
    }
```
con `import com.reparaciones.servidor.security.FiltroTecnico;` (ya importa `UsuarioPrincipal` y `AuthenticationPrincipal`).

En `GlassController.java`:

```java
    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(@RequestParam(required = false) Integer tecnico,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getAsignacionesGlass(FiltroTecnico.efectivo(principal, tecnico));
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(@RequestParam(required = false) Integer tecnico,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getHistorialGlass(FiltroTecnico.efectivo(principal, tecnico));
    }
```

En `PulidoController.java`:

```java
    @GetMapping("/asignaciones")
    public List<ReparacionResumen> getAsignaciones(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getAsignacionesPulido(FiltroTecnico.efectivo(principal, tecnico));
    }

    @GetMapping("/historial")
    public List<ReparacionResumen> getHistorial(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return dao.getHistorialPulido(FiltroTecnico.efectivo(principal, tecnico));
    }
```
Añadir `import com.reparaciones.servidor.security.FiltroTecnico;` en los tres.

- [ ] **Step 8: Toda la suite en verde**

```bash
mvn -q test
```
Expected: sin fallos (140 anteriores + 8 nuevos). `OpenApiContractTest` sigue pasando: el parámetro `@AuthenticationPrincipal` no entra en el contrato (springdoc lo ignora, como en `por-cerrar`).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/security/FiltroTecnico.java src/main/java/com/reparaciones/servidor/controller src/test/java/com/reparaciones/servidor/security/FiltroTecnicoTest.java src/test/java/com/reparaciones/servidor/controller/FiltroTecnicoControllersTest.java
git commit -m "feat(servidor): ?tecnico= verificado contra el token en historial y asignaciones (TECNICO solo lo suyo, 403 si pide otro)"
```

---

### Task 2: Servidor — contadores de pendientes

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/ContadoresPendientes.java`
- Modify: `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (nuevo método), `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (nuevo endpoint), `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (ruta y esquema nuevos)
- Test: `src/test/java/com/reparaciones/servidor/controller/ReparacionControllerContadoresTest.java`

**Interfaces:**
- Produces: `GET /api/reparaciones/pendientes/contadores?tecnico=` → `ContadoresPendientes(int reparaciones, int glass, int pulidos)`; `ReparacionDAO.contarPendientes(Integer idTec): ContadoresPendientes`.
- Consumes: `FiltroTecnico.efectivo` (Task 1).

- [ ] **Step 1: Test del controller (falla por compilación)**

`src/test/java/com/reparaciones/servidor/controller/ReparacionControllerContadoresTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.model.ContadoresPendientes;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** GET /api/reparaciones/pendientes/contadores (spec web-taller §5.2): el badge de Pendientes de la web. */
class ReparacionControllerContadoresTest {

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), mock(LogDAO.class), mock(BorradorDAO.class),
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class));
    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void tecnicoRecibeSusContadores() {
        when(dao.contarPendientes(4)).thenReturn(new ContadoresPendientes(10, 0, 2));
        assertEquals(new ContadoresPendientes(10, 0, 2), ctl.getContadoresPendientes(null, tecnico));
    }

    @Test void tecnicoPidiendoAOtroEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.getContadoresPendientes(9, tecnico));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoSinParametroRecibeLosSuyosYConParametroLosDelOtro() {
        when(dao.contarPendientes(3)).thenReturn(new ContadoresPendientes(0, 0, 0));
        when(dao.contarPendientes(9)).thenReturn(new ContadoresPendientes(1, 2, 3));
        assertEquals(new ContadoresPendientes(0, 0, 0), ctl.getContadoresPendientes(null, supertecnico));
        assertEquals(new ContadoresPendientes(1, 2, 3), ctl.getContadoresPendientes(9, supertecnico));
    }

    @Test void adminSinTecnicoRecibeCerosSinConsultar() {
        assertEquals(new ContadoresPendientes(0, 0, 0), ctl.getContadoresPendientes(null, admin));
        verifyNoInteractions(dao);
        when(dao.contarPendientes(9)).thenReturn(new ContadoresPendientes(5, 0, 0));
        assertEquals(new ContadoresPendientes(5, 0, 0), ctl.getContadoresPendientes(9, admin));
    }
}
```

```bash
mvn -q test -Dtest=ReparacionControllerContadoresTest
```
Expected: `COMPILATION ERROR ... ContadoresPendientes`.

- [ ] **Step 2: Modelo, DAO y endpoint**

`src/main/java/com/reparaciones/servidor/model/ContadoresPendientes.java`:

```java
package com.reparaciones.servidor.model;

/** Asignaciones abiertas por tipo de trabajo (badge y sufijos de Pendientes de la web). */
public record ContadoresPendientes(int reparaciones, int glass, int pulidos) {}
```

En `ReparacionDAO.java`, junto a `getAsignacionesPulido`:

```java
    /**
     * Asignaciones abiertas por tipo (A%, AG%, AP% con FECHA_FIN nula), de un técnico o de todos
     * (idTec nulo). Un solo SELECT: lo consume el badge de Pendientes de la web cada 60 s.
     */
    public ContadoresPendientes contarPendientes(Integer idTec) {
        String sql = "SELECT" +
                " COALESCE(SUM(CASE WHEN ID_REP LIKE 'AG%' THEN 1 ELSE 0 END), 0) AS GLASS," +
                " COALESCE(SUM(CASE WHEN ID_REP LIKE 'AP%' THEN 1 ELSE 0 END), 0) AS PUL," +
                " COALESCE(SUM(CASE WHEN ID_REP NOT LIKE 'AG%' AND ID_REP NOT LIKE 'AP%' THEN 1 ELSE 0 END), 0) AS REP" +
                " FROM Reparacion WHERE ID_REP LIKE 'A%' AND FECHA_FIN IS NULL" +
                (idTec != null ? " AND ID_TEC = ?" : "");
        RowMapper<ContadoresPendientes> mapper = (rs, i) ->
                new ContadoresPendientes(rs.getInt("REP"), rs.getInt("GLASS"), rs.getInt("PUL"));
        return idTec != null ? jdbc.queryForObject(sql, mapper, idTec) : jdbc.queryForObject(sql, mapper);
    }
```

En `ReparacionController.java`, tras `getAsignaciones`:

```java
    /**
     * Badge y sufijos de Pendientes de la web (spec web-taller §5.2). Sin parámetro cuenta las del técnico
     * del token (el supertécnico también es técnico); ADMIN sin técnico recibe ceros. Con parámetro, la
     * regla de FiltroTecnico (un técnico solo puede pedirse a sí mismo).
     */
    @GetMapping("/pendientes/contadores")
    public ContadoresPendientes getContadoresPendientes(
            @RequestParam(required = false) Integer tecnico,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        Integer pedido = tecnico != null ? tecnico : principal.getIdTec();
        Integer efectivo = FiltroTecnico.efectivo(principal, pedido);
        if (efectivo == null) return new ContadoresPendientes(0, 0, 0);
        return dao.contarPendientes(efectivo);
    }
```
(`ContadoresPendientes` entra por el `import com.reparaciones.servidor.model.*;` existente.)

- [ ] **Step 3: Test verde**

```bash
mvn -q test -Dtest=ReparacionControllerContadoresTest
```
Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 4: Contrato: ruta y esquema nuevos en `OpenApiContractTest`**

En `elContratoPublicaLosEsquemasDeLaWeb`, añadir `"/api/reparaciones/pendientes/contadores"` a la lista de rutas y `"ContadoresPendientes"` a la de esquemas, y después del bloque de `ValorBooleano`:

```java
        JsonNode contadores = esquemas.path("ContadoresPendientes").path("properties");
        for (String campo : List.of("reparaciones", "glass", "pulidos")) {
            assertEquals("integer", contadores.path(campo).path("type").asText(),
                    () -> "ContadoresPendientes." + campo + " debe ser integer");
        }
```

```bash
mvn -q test
```
Expected: todo en verde y `target/openapi.json` regenerado con la ruta nueva.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat(servidor): GET /api/reparaciones/pendientes/contadores (badge de Pendientes de la web)"
```

---

### Task 3: Servidor — contrato con `required` y `nullable` explícitos; `referenciadora` tipada

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/config/OpenApiConfig.java`, `model/LoginResponse.java`, `model/ReparacionResumen.java`, `controller/ReparacionController.java` (`MotivoRequest`, `getReferenciadora`), `controller/PulidoController.java` (`MotivoRequest`), `controller/TelefonoController.java` (`ImeiRequest`, `ClienteRequest`), `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Create: `src/main/java/com/reparaciones/servidor/model/ValorTexto.java`

**Interfaces:**
- Produces: contrato en el que cada esquema lista todas sus propiedades en `required` y las que pueden ser `null` llevan `nullable: true`; `GET /api/reparaciones/{idRep}/referenciadora` → `ValorTexto { value: string | null }` (mismo JSON que hoy).
- Consumes: springdoc `OpenApiCustomizer`, `io.swagger.v3.oas.annotations.media.Schema`.

- [ ] **Step 1: Ampliar `OpenApiContractTest` (falla)**

Añadir `"ValorTexto"` a la lista de esquemas y, al final de `elContratoPublicaLosEsquemasDeLaWeb`, antes de escribir `target/openapi.json`:

```java
        // Nullabilidad (spec web-taller §5.3): todo required, nullable explícito
        JsonNode resumen = esquemas.path("ReparacionResumen");
        List<String> requeridos = new ArrayList<>();
        resumen.path("required").forEach(n -> requeridos.add(n.asText()));
        List<String> propiedades = nombres(resumen.path("properties"));
        assertEquals(propiedades.size(), requeridos.size(), "ReparacionResumen: todas las propiedades deben ser required");
        assertTrue(requeridos.containsAll(propiedades));
        assertTrue(resumen.path("properties").path("fechaFin").path("nullable").asBoolean(false), "fechaFin nullable");
        assertTrue(resumen.path("properties").path("glassEntregadoPor").path("nullable").asBoolean(false), "glassEntregadoPor nullable");
        assertFalse(resumen.path("properties").path("idRep").path("nullable").asBoolean(false), "idRep no nullable");
        assertTrue(esquemas.path("LoginResponse").path("properties").path("idTec").path("nullable").asBoolean(false), "idTec nullable");
        assertTrue(esquemas.path("LoginResponse").path("required").toString().contains("\"token\""));
        assertTrue(esquemas.path("ReparacionMotivoRequest").path("properties").path("motivo").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("TelefonoClienteRequest").path("properties").path("idCli").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("TelefonoImeiRequest").path("properties").path("clienteExplicito").path("nullable").asBoolean(false));
        assertTrue(esquemas.path("ValorTexto").path("properties").path("value").path("nullable").asBoolean(false));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/referenciadora", "get", "200").endsWith("/ValorTexto"));
```

```bash
mvn -q test -Dtest=OpenApiContractTest
```
Expected: FAIL en "todas las propiedades deben ser required".

- [ ] **Step 2: Customizer "todo required"**

En `OpenApiConfig.java` añadir los imports `org.springdoc.core.customizers.OpenApiCustomizer`, `io.swagger.v3.oas.models.media.Schema`, `java.util.ArrayList`, `java.util.Map` y el bean:

```java
    /**
     * Jackson serializa todas las claves (inclusión por defecto del proyecto), así que para la web cada
     * propiedad está siempre presente: se marcan todas como {@code required} y la nulabilidad se declara
     * campo a campo con {@code @Schema(nullable = true)}. openapi-typescript genera entonces {@code T} o
     * {@code T | null} en vez de {@code T | undefined}, y client.ts deja de necesitar {@code Required<>}.
     */
    @Bean
    OpenApiCustomizer todasLasPropiedadesRequeridas() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            for (Schema<?> esquema : openApi.getComponents().getSchemas().values()) {
                Map<String, Schema> propiedades = esquema.getProperties();
                if (propiedades == null || propiedades.isEmpty()) continue;
                esquema.setRequired(new ArrayList<>(propiedades.keySet()));
            }
        };
    }
```

- [ ] **Step 3: Anotar la nulabilidad**

`LoginResponse.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta del login. Mismo JSON que el Map anterior; tipada para el contrato OpenAPI. */
public record LoginResponse(int idUsu, String nombreUsuario, String rol,
                            @Schema(nullable = true) Integer idTec, String token) {}
```

`ValorTexto.java` (nuevo):

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Envoltorio {"value": texto|null} de los endpoints que devuelven un único texto opcional. */
public record ValorTexto(@Schema(nullable = true) String value) {}
```

`ReparacionResumen.java`: añadir `import io.swagger.v3.oas.annotations.media.Schema;` y anteponer `@Schema(nullable = true)` a estas declaraciones de campo (una por línea, el resto sin tocar): `fechaFin`, `tipoComponente`, `observaciones`, `incidencia`, `idRepAnterior`, `descripcionSolicitud`, `estadoSolicitud`, `tipoSolicitud`, `tiposSolicitud`, `updatedAt`, `modelo`, `comentarioAsignacion`, `observacionTelefono`, `nombreTecnicoAsigna`, `telefonoUpdatedAt`, `cliente`, `entregadoAt`, `entregadoPorNombre`, `entregadoPor`, `glassEntregadoAt`, `glassEntregadoPorNombre`, `glassEntregadoPor`, `glassTecnicoNombre`, `normalTecnicoNombre`. Ejemplo:

```java
    @Schema(nullable = true) private LocalDateTime fechaFin;
```

`ReparacionController.java`: `private record MotivoRequest(@Schema(nullable = true) String motivo) {}` (import `io.swagger.v3.oas.annotations.media.Schema`) y `getReferenciadora` pasa a:

```java
    @GetMapping("/{idRep}/referenciadora")
    public ValorTexto getReferenciadora(@PathVariable String idRep) {
        return new ValorTexto(dao.getReferenciadora(idRep));
    }
```

`PulidoController.java`: `private record MotivoRequest(@Schema(nullable = true) String motivo) {}` (+ import).

`TelefonoController.java`: en `ImeiRequest` anotar `idCli` y `clienteExplicito`, y en `ClienteRequest` anotar `idCli`:

```java
    private record ImeiRequest(String imei, String modelo, @Schema(nullable = true) Integer idCli,
                               @Schema(nullable = true) Boolean clienteExplicito) {}
    private record ClienteRequest(@Schema(nullable = true) Integer idCli, java.time.LocalDateTime updatedAt) {}
```
(comprobar los nombres reales de los records en el fichero antes de editar; conservar el resto de campos tal cual).

- [ ] **Step 4: Suite en verde y snapshot**

```bash
mvn -q test
ls -la target/openapi.json
```
Expected: todo en verde; `target/openapi.json` con `"required"` en cada esquema y `"nullable": true` en los campos anotados.

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat(servidor): contrato con todas las propiedades required y nullable explicito; referenciadora tipada (ValorTexto)"
```

---

### Task 4: Web — contrato regenerado y tipos sin `Required<>`

**Files:**
- Modify: `api/openapi.json` (copia de `../gestion-reparaciones-servidor/target/openapi.json`), `src/shared/api/schema.d.ts` (generado), `src/shared/api/client.ts`, `src/modules/gestion/clientes/api.ts`
- Test: `src/shared/api/client.test.ts` (tipos)

**Interfaces:**
- Produces: `Cliente`, `LoginResponse`, `ReparacionResumen`, `Tecnico`, `ContadoresPendientes` exportados de `@/shared/api/client` como los tipos generados (campos requeridos, `T | null` donde el servidor los marcó nullable).

- [ ] **Step 1: Copiar el contrato y regenerar los tipos**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout feature/web-taller
cp ../gestion-reparaciones-servidor/target/openapi.json api/openapi.json
npm run api:types:offline
grep -n "idTec: number | null" src/shared/api/schema.d.ts | head -3
```
Expected: `schema.d.ts` regenerado; `LoginResponse` con `idTec: number | null` y `ReparacionResumen` con `fechaFin: string | null`.

- [ ] **Step 2: Test de tipos (falla en typecheck)**

Añadir a `src/shared/api/client.test.ts`:

```ts
import { expectTypeOf } from 'vitest'
import type { Cliente, ContadoresPendientes, LoginResponse, ReparacionResumen, Tecnico } from './client'

describe('tipos del contrato (required + nullable, spec web-taller §5.3)', () => {
  it('los campos siempre presentes son obligatorios y los nulos van como T | null', () => {
    expectTypeOf<Cliente['nombre']>().toEqualTypeOf<string>()
    expectTypeOf<LoginResponse['idTec']>().toEqualTypeOf<number | null>()
    expectTypeOf<ReparacionResumen['idRep']>().toEqualTypeOf<string>()
    expectTypeOf<ReparacionResumen['fechaFin']>().toEqualTypeOf<string | null>()
    expectTypeOf<ReparacionResumen['glassEntregadoPor']>().toEqualTypeOf<number | null>()
    expectTypeOf<Tecnico['nombre']>().toEqualTypeOf<string>()
    expectTypeOf<ContadoresPendientes>().toEqualTypeOf<{ reparaciones: number; glass: number; pulidos: number }>()
  })
})
```
(añadir `describe`/`it` al import de vitest si el fichero no los trae ya).

```bash
npm run typecheck
```
Expected: error en `client.test.ts` (`ContadoresPendientes` no exportado / tipos no coinciden).

- [ ] **Step 3: `client.ts` sin `Required<>`**

Sustituir el bloque de tipos por:

```ts
/** Tipos del contrato tal cual los genera openapi-typescript: el servidor marca todas las propiedades como
 *  required y anota `nullable` en las que pueden venir a null (OpenApiConfig.todasLasPropiedadesRequeridas), así
 *  que aquí ya no hace falta `Required<>` ni corregir `idTec` a mano. */
export type Cliente = components['schemas']['Cliente']
export type LoginResponse = components['schemas']['LoginResponse']
export type ReparacionResumen = components['schemas']['ReparacionResumen']
export type Tecnico = components['schemas']['Tecnico']
export type ContadoresPendientes = components['schemas']['ContadoresPendientes']
```

En `src/modules/gestion/clientes/api.ts` quitar el cast y el comentario:

```ts
    queryFn: async () => (await api.GET('/api/clientes')).data ?? [],
```
(y el import de `Cliente` si deja de usarse en ese fichero; conservarlo si lo usan las mutaciones).

- [ ] **Step 4: `npm run check` en verde**

```bash
npm run check
```
Expected: lint, typecheck y los 100 tests + el nuevo en verde. Si `SessionProvider.esLoginResponse` o `ClientesPage` marcan errores de tipo por `null`, ajustar solo lo que `tsc` señale (p. ej. `idTec: data.idTec` ya es `number | null`).

- [ ] **Step 5: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/api/client.test.ts src/modules/gestion/clientes/api.ts
git commit -m "chore(web): contrato regenerado con required/nullable; tipos del cliente sin Required<>"
```

---

### Task 5: Web — `SessionProvider` a `shared/session` y frontera de lint

**Files:**
- Move: `src/app/session/SessionProvider.tsx` → `src/shared/session/SessionProvider.tsx`; `src/app/shell/exportable.tsx` (+ `exportable.test.tsx`) → `src/shared/ui/exportable.tsx` (+ test)
- Modify: `src/main.tsx`, `src/app/login/LoginPage.tsx`, `src/app/login/LoginPage.test.tsx`, `src/app/session/RequireSesion.tsx`, `src/app/session/RequireSesion.test.tsx`, `src/app/shell/UserMenu.tsx`, `src/app/shell/AppLayout.tsx`, `src/modules/gestion/clientes/ClientesPage.tsx`, `src/test/render.tsx`, `eslint.config.js`

**Interfaces:**
- Produces: `import { SessionProvider, useSession, MSG_CREDENCIALES } from '@/shared/session/SessionProvider'`; `import { useRegistrarExportable, useExportable, ExportableProvider } from '@/shared/ui/exportable'` (las vistas del taller registran su "Descargar CSV" desde `shared`, no desde `app`).

- [ ] **Step 1: Mover los ficheros y actualizar los imports**

```bash
git mv src/app/session/SessionProvider.tsx src/shared/session/SessionProvider.tsx
git mv src/app/shell/exportable.tsx src/shared/ui/exportable.tsx
git mv src/app/shell/exportable.test.tsx src/shared/ui/exportable.test.tsx
grep -rl "app/session/SessionProvider\|from './SessionProvider'" src | xargs sed -i "s#@/app/session/SessionProvider#@/shared/session/SessionProvider#g; s#from './SessionProvider'#from '@/shared/session/SessionProvider'#g"
grep -rl "from './exportable'" src/app/shell src/shared/ui | xargs sed -i "s#from './exportable'#from '@/shared/ui/exportable'#g"
grep -rn "SessionProvider'\|exportable'" src | grep import
```
Expected: todos los imports apuntan a `@/shared/session/SessionProvider` (main.tsx, LoginPage y su test, RequireSesion y su test, UserMenu, ClientesPage, render.tsx) y a `@/shared/ui/exportable` (AppLayout, UserMenu, exportable.test.tsx). `exportable.tsx` no importa nada de `app`, así que vive en `shared/ui` sin cambios.

- [ ] **Step 2: Bloquear `@/app/**` en la zona de módulos**

En `eslint.config.js`, en el bloque `files: ['src/modules/**/*.{ts,tsx}']`, añadir un patrón:

```js
            {
              group: ['@/app/*', '@/app/**'],
              message: 'Un módulo no puede importar de app: la sesión vive en @/shared/session y el shell compone los módulos, no al revés.',
            },
```

- [ ] **Step 3: Comprobar que la frontera funciona (rojo, luego verde)**

Añadir temporalmente en `src/modules/gestion/clientes/ClientesPage.tsx` la línea `import '@/app/router'`, ejecutar `npm run lint` y comprobar que falla con el mensaje nuevo; quitar la línea.

```bash
npm run check
```
Expected: todo en verde.

- [ ] **Step 4: Commit**

```bash
git add -A src eslint.config.js
git commit -m "refactor(web): SessionProvider a shared/session y exportable a shared/ui; los modulos no pueden importar de app"
```

---

### Task 6: Web — región viva del banner montada siempre y blanco único

**Files:**
- Modify: `src/app/shell/ConnectionBanner.tsx`, `src/app/shell/ConnectionBanner.test.tsx`, `src/shared/styles/tokens.css`, `src/app/shell/SubNav.tsx`, `src/shared/ui/DataTable.tsx`, `src/shared/ui/MultiSelect.tsx`, `src/app/shell/SubNav.test.tsx`

**Interfaces:**
- Produces: token `--color-superficie: #FFFFFF` (`bg-superficie`); `<div role="status" aria-live="polite">` siempre en el DOM.

- [ ] **Step 1: Tests (fallan)**

En `ConnectionBanner.test.tsx` añadir:

```ts
  it('la región viva existe también conectado (vacía y solo para lectores de pantalla)', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    const region = screen.getByRole('status')
    expect(region).toBeEmptyDOMElement()
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(region).toHaveClass('sr-only')
    act(() => reportarFallo())
    expect(region).not.toHaveClass('sr-only')
    expect(region).toHaveTextContent('⚠ Sin conexión con el servidor. Reintentando…')
  })
```

En `SubNav.test.tsx` añadir:

```ts
  it('usa el blanco de superficie del sistema de tokens', () => {
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toHaveClass('bg-superficie')
  })
```

```bash
npm test -- ConnectionBanner SubNav
```
Expected: 2 fallos.

- [ ] **Step 2: Implementar**

`ConnectionBanner.tsx`:

```tsx
import { useConexion } from '@/shared/api/conexion'
import { cn } from '@/shared/lib/utils'

/** Calco de `.banner-conexion`. La región viva está montada siempre (vacía y solo para lectores de pantalla
 *  mientras hay conexión) para que el aviso se anuncie al aparecer: una región que nace con el texto no se lee. */
export function ConnectionBanner() {
  const conectado = useConexion()
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(conectado ? 'sr-only' : 'min-h-6 bg-banner-bg px-3 py-1 text-center text-[12px] font-bold text-banner-text')}
    >
      {conectado ? null : '⚠ Sin conexión con el servidor. Reintentando…'}
    </div>
  )
}
```

`tokens.css`: añadir dentro de `@theme`, tras `--color-crema`:

```css
  --color-superficie: #FFFFFF;
```

Sustituir `bg-white` por `bg-superficie` en `SubNav.tsx` (el `<nav>`) y en `MultiSelect.tsx` (el `PopoverContent`), y `bg-card` por `bg-superficie` en `DataTable.tsx` (contenedor). No tocar `hover:bg-white/8` de la barra superior (es un velo sobre navy, no una superficie).

```bash
grep -rn "bg-white\b\|bg-card\b" src --include=*.tsx
```
Expected: solo `hover:bg-white/8` en `TopBar.tsx` y `UserMenu.tsx`.

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add -A src
git commit -m "fix(web): region viva del banner montada siempre; blanco unico --color-superficie"
```

---

### Task 7: Web — `shared/lib`: store externo, fechas Madrid, CSV, filtro IMEI y tipo de trabajo

**Files:**
- Create: `src/shared/lib/store.ts`, `store.test.ts`, `fechas.ts`, `fechas.test.ts`, `csv.ts`, `csv.test.ts`, `filtroImei.ts`, `filtroImei.test.ts`, `tipoTrabajo.ts`, `tipoTrabajo.test.ts`
- Modify: `src/shared/styles/tokens.css` (tokens de tipo de trabajo)

**Interfaces (produces):**
- `store.ts`: `crearStore<T>(inicial: T): Store<T>` con `get()`, `set(v | (prev) => v)`, `subscribe(cb)`, `reset()`; `useStore<T>(store): [T, Store<T>['set']]`.
- `fechas.ts`: `type Patron = 'yyyy/MM/dd HH:mm' | 'yyyy/MM/dd' | 'dd/MM HH:mm' | 'dd/MM' | 'HH:mm' | 'dd/MM/yyyy' | 'dd/MM/yyyy HH:mm'`; `parsearUtc(iso): Date | null`; `formatear(iso, patron): string` ('' si nulo); `fechaLocal(iso): string | null` ('yyyy-MM-dd' en Madrid); `hoyMadrid(ahora?): string`; `horaLocal(ahora?): string` ('HH:mm' del PC); `marcaFichero(ahora?): string` ('yyyy-MM-dd_HH-mm' del PC).
- `csv.ts`: `escaparCsv(v)`, `textoForzado(v)`, `generarCsv(cabeceras, filas): string`, `descargarCsv(nombreBase, cabeceras, filas, ahora?)`.
- `filtroImei.ts`: `canonicalizarImei(texto): string`, `imeisValidos(texto): Set<string>`, `estadoFiltroImei(texto): 'vacio' | 'incompleto' | 'valido'`.
- `tipoTrabajo.ts`: `type TipoTrabajo = 'REPARACION' | 'GLASS' | 'PULIDO'`; `tipoDe(idRep): TipoTrabajo`; `TIPO_TRABAJO: Record<TipoTrabajo, { etiqueta: string; clases: string }>`.

- [ ] **Step 1: Tests (fallan por módulos inexistentes)**

`src/shared/lib/store.test.ts`:

```ts
import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { crearStore, useStore } from './store'

describe('crearStore', () => {
  it('guarda, notifica y reinicia', () => {
    const s = crearStore('')
    let avisos = 0
    const off = s.subscribe(() => avisos++)
    s.set('35')
    expect(s.get()).toBe('35')
    s.set((prev) => prev + '2')
    expect(s.get()).toBe('352')
    expect(avisos).toBe(2)
    s.reset()
    expect(s.get()).toBe('')
    off()
    s.set('x')
    expect(avisos).toBe(3)
  })
  it('useStore sigue el valor y expone el setter', () => {
    const s = crearStore(new Set<string>())
    const { result } = renderHook(() => useStore(s))
    expect(result.current[0].size).toBe(0)
    act(() => result.current[1](new Set(['a'])))
    expect(result.current[0].has('a')).toBe(true)
  })
})
```

`src/shared/lib/fechas.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { fechaLocal, formatear, horaLocal, hoyMadrid, marcaFichero, parsearUtc } from './fechas'

describe('fechas (UTC del servidor → Madrid, calco de FechaUtils)', () => {
  it('formatea en hora de Madrid con los patrones del JavaFX', () => {
    expect(formatear('2026-08-28T08:42:00', 'dd/MM HH:mm')).toBe('28/08 10:42')          // CEST
    expect(formatear('2026-01-15T10:00:00', 'yyyy/MM/dd HH:mm')).toBe('2026/01/15 11:00') // CET
    expect(formatear('2026-08-28T08:42:00', 'yyyy/MM/dd')).toBe('2026/08/28')
    expect(formatear('2026-08-28T08:42:00', 'dd/MM')).toBe('28/08')
    expect(formatear('2026-08-28T08:42:00', 'HH:mm')).toBe('10:42')
    expect(formatear('2026-08-28T08:42:00', 'dd/MM/yyyy HH:mm')).toBe('28/08/2026 10:42')
    expect(formatear('2026-08-28T08:42:00', 'dd/MM/yyyy')).toBe('28/08/2026')
  })
  it('acepta ISO con zona y devuelve vacío con nulos o basura', () => {
    expect(formatear('2026-08-28T08:42:00Z', 'HH:mm')).toBe('10:42')
    expect(formatear(null, 'HH:mm')).toBe('')
    expect(formatear(undefined, 'HH:mm')).toBe('')
    expect(formatear('no-es-fecha', 'HH:mm')).toBe('')
    expect(parsearUtc('2026-08-28T22:30:00')?.toISOString()).toBe('2026-08-28T22:30:00.000Z')
  })
  it('fechaLocal cambia de día con la zona (22:30 UTC = 00:30 del día siguiente en Madrid)', () => {
    expect(fechaLocal('2026-08-28T22:30:00')).toBe('2026-08-29')
    expect(fechaLocal(null)).toBeNull()
  })
  it('hoyMadrid, horaLocal y marcaFichero', () => {
    expect(hoyMadrid(new Date(Date.UTC(2026, 7, 28, 22, 30)))).toBe('2026-08-29')
    const local = new Date(2026, 8, 16, 9, 5)
    expect(horaLocal(local)).toBe('09:05')
    expect(marcaFichero(local)).toBe('2026-09-16_09-05')
  })
})
```

`src/shared/lib/csv.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { descargarCsv, escaparCsv, generarCsv, textoForzado } from './csv'

describe('csv (calco de CsvExporter)', () => {
  it('textoForzado envuelve en ="…" y escapa comillas; vacío o nulo → vacío', () => {
    expect(textoForzado('123456789012345')).toBe('="123456789012345"')
    expect(textoForzado('valor con "comillas"')).toBe('="valor con ""comillas"""')
    expect(textoForzado('42')).toBe('="42"')
    expect(textoForzado('')).toBe('')
    expect(textoForzado(null)).toBe('')
  })
  it('escapa ; comillas y saltos de línea, y solo entonces', () => {
    expect(escaparCsv('hola')).toBe('hola')
    expect(escaparCsv('a;b')).toBe('"a;b"')
    expect(escaparCsv('di "x"')).toBe('"di ""x"""')
    expect(escaparCsv('l1\nl2')).toBe('"l1\nl2"')
    expect(escaparCsv(null)).toBe('')
  })
  it('genera BOM, cabecera sin escapar, filas con ; y CRLF', () => {
    const csv = generarCsv(['ID', 'IMEI'], [['R1', textoForzado('351')], ['R2', 'a;b']])
    expect(csv).toBe('\uFEFFID;IMEI\r\nR1;"=""351"""\r\nR2;"a;b"\r\n')
  })
  it('descargarCsv crea un enlace con el nombre <base>_yyyy-MM-dd_HH-mm.csv y lo pulsa', () => {
    const crear = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x')
    const revocar = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    descargarCsv('mis_pendientes', ['ID'], [['R1']], new Date(2026, 8, 16, 9, 5))
    expect(crear).toHaveBeenCalledTimes(1)
    const blob = crear.mock.calls[0][0] as Blob
    expect(blob.type).toBe('text/csv;charset=utf-8')
    expect(clic).toHaveBeenCalledTimes(1)
    const enlace = clic.mock.instances[0] as HTMLAnchorElement
    expect(enlace.download).toBe('mis_pendientes_2026-09-16_09-05.csv')
    expect(revocar).toHaveBeenCalledWith('blob:x')
    vi.restoreAllMocks()
  })
})
```

`src/shared/lib/filtroImei.test.ts` (port de `FiltroImeiTest`):

```ts
import { describe, expect, it } from 'vitest'
import { canonicalizarImei, estadoFiltroImei, imeisValidos } from './filtroImei'

describe('filtroImei (calco de FiltroImei)', () => {
  it('parte un blob concatenado cada 15', () => {
    expect(canonicalizarImei('352600000000071354700000000091')).toBe('352600000000071, 354700000000091, ')
  })
  it('un IMEI completo añade separador', () => {
    expect(canonicalizarImei('352600000000071')).toBe('352600000000071, ')
  })
  it('un blob con resto deja el resto como token', () => {
    expect(canonicalizarImei('35260000000007135470000000009112')).toBe('352600000000071, 354700000000091, 12')
  })
  it('quita no dígitos y normaliza separadores', () => {
    expect(canonicalizarImei('352-600-000-000-071')).toBe('352600000000071, ')
  })
  it('es idempotente', () => {
    for (const e of ['', '3526', '352600000000071', '352600000000071354700000000091', '35260000000007135470000000009112']) {
      const una = canonicalizarImei(e)
      expect(canonicalizarImei(una)).toBe(una)
    }
  })
  it('vacío o nulo', () => {
    expect(canonicalizarImei('')).toBe('')
    expect(canonicalizarImei(null)).toBe('')
  })
  it('imeisValidos solo los de 15', () => {
    expect(imeisValidos('352600000000071, 354700000000091, 12')).toEqual(new Set(['352600000000071', '354700000000091']))
    expect(imeisValidos('').size).toBe(0)
    expect(imeisValidos('12, 34').size).toBe(0)
  })
  it('estado clasifica', () => {
    expect(estadoFiltroImei('')).toBe('vacio')
    expect(estadoFiltroImei('   ')).toBe('vacio')
    expect(estadoFiltroImei('3526')).toBe('incompleto')
    expect(estadoFiltroImei('352600000000071, 12')).toBe('incompleto')
    expect(estadoFiltroImei('352600000000071, ')).toBe('valido')
    expect(estadoFiltroImei('352600000000071, 354700000000091, ')).toBe('valido')
  })
})
```

`src/shared/lib/tipoTrabajo.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { TIPO_TRABAJO, tipoDe } from './tipoTrabajo'

describe('tipoTrabajo (calco de TipoTrabajo.desde)', () => {
  it('deriva el tipo del prefijo, asignaciones e historial', () => {
    expect(tipoDe('A20260828_1')).toBe('REPARACION')
    expect(tipoDe('R20260828_1')).toBe('REPARACION')
    expect(tipoDe('AG20260828_1')).toBe('GLASS')
    expect(tipoDe('G20260828_1')).toBe('GLASS')
    expect(tipoDe('AP20260828_1')).toBe('PULIDO')
    expect(tipoDe('P20260828_1')).toBe('PULIDO')
    expect(tipoDe(null)).toBe('REPARACION')
  })
  it('etiquetas y paletas por tipo', () => {
    expect(TIPO_TRABAJO.REPARACION.etiqueta).toBe('Reparación')
    expect(TIPO_TRABAJO.GLASS.etiqueta).toBe('Glass')
    expect(TIPO_TRABAJO.PULIDO.etiqueta).toBe('Pulido')
    expect(TIPO_TRABAJO.GLASS.clases).toContain('bg-tipo-glass-bg')
  })
})
```

```bash
npm test -- src/shared/lib
```
Expected: 5 suites fallan por módulos inexistentes.

- [ ] **Step 2: Implementar**

`src/shared/lib/store.ts`:

```ts
import { useSyncExternalStore } from 'react'

/** Estado que sobrevive al cambio de ruta sin vivir en el árbol de React (calco de los campos de un controller
 *  del JavaFX que persisten al cambiar de panel: el texto del filtro IMEI, los técnicos marcados...). */
export type Store<T> = {
  get: () => T
  set: (valor: T | ((prev: T) => T)) => void
  subscribe: (cb: () => void) => () => void
  reset: () => void
}

export function crearStore<T>(inicial: T): Store<T> {
  let valor = inicial
  const listeners = new Set<() => void>()
  const avisar = () => listeners.forEach((l) => l())
  return {
    get: () => valor,
    set: (v) => {
      valor = typeof v === 'function' ? (v as (prev: T) => T)(valor) : v
      avisar()
    },
    subscribe: (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    reset: () => {
      valor = inicial
      avisar()
    },
  }
}

export function useStore<T>(store: Store<T>): [T, Store<T>['set']] {
  const valor = useSyncExternalStore(store.subscribe, store.get)
  return [valor, store.set]
}
```

`src/shared/lib/fechas.ts`:

```ts
/** Calco de FechaUtils: el servidor manda LocalDateTime ISO sin zona (UTC); se muestra en Europe/Madrid. */
const ZONA = 'Europe/Madrid'

export type Patron = 'yyyy/MM/dd HH:mm' | 'yyyy/MM/dd' | 'dd/MM HH:mm' | 'dd/MM' | 'HH:mm' | 'dd/MM/yyyy' | 'dd/MM/yyyy HH:mm'

const FMT = new Intl.DateTimeFormat('es-ES', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

type Partes = Record<'yyyy' | 'MM' | 'dd' | 'HH' | 'mm', string>

function partesMadrid(d: Date): Partes {
  const p: Record<string, string> = {}
  for (const parte of FMT.formatToParts(d)) p[parte.type] = parte.value
  // Algunos motores devuelven "24" a medianoche con hour12: false
  const HH = p.hour === '24' ? '00' : p.hour
  return { yyyy: p.year, MM: p.month, dd: p.day, HH, mm: p.minute }
}

/** ISO sin zona = UTC (el JavaFX hace `atZone(UTC)`); con zona se respeta. Nulo o inválido → null. */
export function parsearUtc(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const conZona = /(Z|[+-]\d\d:\d\d)$/.test(iso)
  const d = new Date(conZona ? iso : `${iso}Z`)
  return Number.isNaN(d.getTime()) ? null : d
}

export function formatear(iso: string | null | undefined, patron: Patron): string {
  const d = parsearUtc(iso)
  if (!d) return ''
  const p = partesMadrid(d)
  return patron.replace(/yyyy|MM|dd|HH|mm/g, (t) => p[t as keyof Partes])
}

/** Fecha civil en Madrid ('yyyy-MM-dd'), comparable con los <input type="date"> de los filtros. */
export function fechaLocal(iso: string | null | undefined): string | null {
  const d = parsearUtc(iso)
  if (!d) return null
  const p = partesMadrid(d)
  return `${p.yyyy}-${p.MM}-${p.dd}`
}

export function hoyMadrid(ahora: Date = new Date()): string {
  const p = partesMadrid(ahora)
  return `${p.yyyy}-${p.MM}-${p.dd}`
}

const dos = (n: number) => String(n).padStart(2, '0')

/** "HH:mm" del reloj del PC (LocalTime.now() del JavaFX), para "Actualizado HH:mm". */
export function horaLocal(ahora: Date = new Date()): string {
  return `${dos(ahora.getHours())}:${dos(ahora.getMinutes())}`
}

/** Marca del nombre de fichero CSV (LocalDateTime.now() del JavaFX): 'yyyy-MM-dd_HH-mm' local. */
export function marcaFichero(ahora: Date = new Date()): string {
  return `${ahora.getFullYear()}-${dos(ahora.getMonth() + 1)}-${dos(ahora.getDate())}_${dos(ahora.getHours())}-${dos(ahora.getMinutes())}`
}
```

`src/shared/lib/csv.ts`:

```ts
import { marcaFichero } from './fechas'

/** Calco de CsvExporter: separador `;`, BOM para Excel, escapado de `;`, comillas y saltos, `="…"` para los IMEIs. */
export function escaparCsv(valor: string | null | undefined): string {
  if (valor == null) return ''
  return /[;"\n]/.test(valor) ? `"${valor.replace(/"/g, '""')}"` : valor
}

/** Fuerza que Excel trate el valor como texto (IMEIs): `="valor"`. */
export function textoForzado(valor: string | null | undefined): string {
  if (!valor) return ''
  return `="${valor.replace(/"/g, '""')}"`
}

export function generarCsv(cabeceras: string[], filas: string[][]): string {
  const lineas = [cabeceras.join(';'), ...filas.map((f) => f.map(escaparCsv).join(';'))]
  return `\uFEFF${lineas.join('\r\n')}\r\n`
}

/** Descarga del navegador (diferencia aceptada respecto al FileChooser del JavaFX). */
export function descargarCsv(nombreBase: string, cabeceras: string[], filas: string[][], ahora: Date = new Date()): void {
  const blob = new Blob([generarCsv(cabeceras, filas)], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${nombreBase}_${marcaFichero(ahora)}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
```

`src/shared/lib/filtroImei.ts`:

```ts
/** Calco de FiltroImei: campo multi-IMEI con pegado en lote (spec filtro-imei-pegado 2026-06-29). */
export type EstadoFiltroImei = 'vacio' | 'incompleto' | 'valido'

/** Solo dígitos y comas, separador ", ", troceo de tokens largos cada 15 y ", " tras un IMEI completo. Idempotente. */
export function canonicalizarImei(texto: string | null | undefined): string {
  if (!texto) return ''
  const limpio = texto.replace(/, /g, ',').replace(/[^\d,]/g, '').replace(/,+/g, ',').replace(/^,/, '')
  const partes: string[] = []
  for (const token of limpio.split(',')) {
    if (token.length > 15) {
      for (let i = 0; i < token.length; i += 15) partes.push(token.slice(i, i + 15))
    } else {
      partes.push(token)
    }
  }
  const unido = partes.join(',')
  let visible = unido.replace(/,/g, ', ')
  const ultimo = unido.split(',').at(-1) ?? ''
  if (ultimo.length === 15 && !visible.endsWith(', ')) visible += ', '
  return visible
}

export function imeisValidos(texto: string | null | undefined): Set<string> {
  if (!texto || texto.trim() === '') return new Set()
  return new Set(texto.split(',').map((t) => t.trim()).filter((t) => t.length === 15))
}

export function estadoFiltroImei(texto: string | null | undefined): EstadoFiltroImei {
  if (!texto || texto.trim() === '') return 'vacio'
  const tokens = texto.split(',').map((t) => t.trim()).filter((t) => t !== '')
  if (tokens.some((t) => t.length < 15)) return 'incompleto'
  return imeisValidos(texto).size === 0 ? 'vacio' : 'valido'
}
```

`src/shared/lib/tipoTrabajo.ts`:

```ts
/** Calco de TipoTrabajo: los tres tipos comparten la tabla Reparacion y se distinguen por el prefijo del ID
 *  (asignaciones A/AG/AP, historial R/G/P). */
export type TipoTrabajo = 'REPARACION' | 'GLASS' | 'PULIDO'

export const TIPO_TRABAJO: Record<TipoTrabajo, { etiqueta: string; clases: string }> = {
  REPARACION: { etiqueta: 'Reparación', clases: 'bg-tipo-reparacion-bg text-tipo-reparacion-text' },
  GLASS: { etiqueta: 'Glass', clases: 'bg-tipo-glass-bg text-tipo-glass-text' },
  PULIDO: { etiqueta: 'Pulido', clases: 'bg-tipo-pulido-bg text-tipo-pulido-text' },
}

export function tipoDe(idRep: string | null | undefined): TipoTrabajo {
  if (!idRep) return 'REPARACION'
  if (idRep.startsWith('AG') || idRep.startsWith('G')) return 'GLASS'
  if (idRep.startsWith('AP') || idRep.startsWith('P')) return 'PULIDO'
  return 'REPARACION'
}
```

`tokens.css`: añadir dentro de `@theme` (valores de `TipoTrabajo`, `EntregaGlass`, `PendientesTecnicoController` y `AgrupadoController`):

```css
  --color-tipo-reparacion-bg: #E3F2FD;
  --color-tipo-reparacion-text: #1565C0;
  --color-tipo-glass-bg: #E0F2F1;
  --color-tipo-glass-text: #00796B;
  --color-tipo-pulido-bg: #EDE7F6;
  --color-tipo-pulido-text: #5E35B1;
  --color-entrega-bg: #E8EAF6;
  --color-entrega-text: #3949AB;
  --color-urgente-bg: #FDDEDE;
  --color-recibido-bg: #E8F5E9;
  --color-recibido-text: #2E7D32;
  --color-badge-neutro-bg: #E8EAF0;
  --color-fila-maestro-bg: #EEF0F5;
  --color-texto-sub: #8A94A6;
  --color-texto-fecha-inicio: #9AA0AA;
  --color-texto-vacio: #A0A0A0;
  --color-seleccion-suave: #EAF1FF;
```

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/shared/lib src/shared/styles/tokens.css
git commit -m "feat(web): shared/lib fechas (Madrid), csv (descarga), filtroImei, tipoTrabajo y store externo; tokens del taller"
```

---

### Task 8: Web — `useIntervaloRefresco` y `EtiquetaActualizado`

**Files:**
- Create: `src/shared/api/refresco.ts`, `src/shared/api/refresco.test.tsx`, `src/shared/ui/EtiquetaActualizado.tsx`, `src/shared/ui/EtiquetaActualizado.test.tsx`
- Modify: `src/shared/api/conexion.ts` (reutiliza `intervaloRefresco()`), `src/shared/api/queryClient.ts` (comentario apunta al contrato)

**Interfaces (produces):**
- `useIntervaloRefresco(activo = true): number | false` (60000 / 5000 / false).
- `<EtiquetaActualizado actualizadoEn={number} onRecargar={() => Promise<unknown>} />` (`actualizadoEn` = `dataUpdatedAt` de la query, 0 = nunca).

- [ ] **Step 1: Tests (fallan)**

`src/shared/api/refresco.test.tsx`:

```tsx
import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { reportarExito, reportarFallo } from './conexion'
import { INTERVALO_CONECTADO_MS, INTERVALO_DESCONECTADO_MS, useIntervaloRefresco } from './refresco'

describe('useIntervaloRefresco (calco de Poller.java)', () => {
  it('60 s conectado, 5 s con el banner activo, false si la vista no sondea', () => {
    reportarExito()
    const { result } = renderHook(() => useIntervaloRefresco())
    expect(result.current).toBe(INTERVALO_CONECTADO_MS)
    expect(INTERVALO_CONECTADO_MS).toBe(60_000)
    act(() => reportarFallo())
    expect(result.current).toBe(INTERVALO_DESCONECTADO_MS)
    expect(INTERVALO_DESCONECTADO_MS).toBe(5_000)
    act(() => reportarExito())
    const sinSondeo = renderHook(() => useIntervaloRefresco(false))
    expect(sinSondeo.result.current).toBe(false)
  })
})
```

`src/shared/ui/EtiquetaActualizado.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConexionError } from '@/shared/api/errors'
import { AlertaProvider } from './AlertaProvider'
import { EtiquetaActualizado } from './EtiquetaActualizado'

describe('EtiquetaActualizado (calco de lblUltimaActualizacion)', () => {
  it('muestra "Actualizado HH:mm" con la hora local y recarga al pulsar', async () => {
    const recargar = vi.fn().mockResolvedValue(undefined)
    const cuando = new Date(2026, 8, 16, 9, 21).getTime()
    render(<AlertaProvider><EtiquetaActualizado actualizadoEn={cuando} onRecargar={recargar} /></AlertaProvider>)
    const boton = screen.getByRole('button', { name: 'Actualizado 09:21' })
    expect(boton).toHaveClass('text-[10px]', 'text-texto-vacio')
    await userEvent.click(boton)
    expect(recargar).toHaveBeenCalledTimes(1)
  })
  it('sin fecha no muestra texto', () => {
    render(<AlertaProvider><EtiquetaActualizado actualizadoEn={0} onRecargar={() => Promise.resolve()} /></AlertaProvider>)
    expect(screen.getByRole('button')).toHaveTextContent('')
  })
  it('si la recarga manual falla por conexión abre el diálogo con el detalle (la pidió el usuario)', async () => {
    const recargar = vi.fn().mockRejectedValue(new ConexionError(0, 'Sin conexión con el servidor.', 'Failed to fetch'))
    render(<AlertaProvider><EtiquetaActualizado actualizadoEn={Date.now()} onRecargar={recargar} /></AlertaProvider>)
    await userEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(screen.getByRole('dialog')).toHaveTextContent('Sin conexión con el servidor: Failed to fetch'))
  })
})
```

```bash
npm test -- refresco EtiquetaActualizado
```
Expected: fallan por módulos inexistentes.

- [ ] **Step 2: Implementar**

`src/shared/api/refresco.ts`:

```ts
import { intervaloRefresco, useConexion } from './conexion'

export const INTERVALO_CONECTADO_MS = 60_000
export const INTERVALO_DESCONECTADO_MS = 5_000

/**
 * Calco de Poller.java: las vistas que sondean recargan cada 60 s, y cada 5 s mientras el banner de conexión
 * está activo. `activo = false` para las vistas que no sondean (ADMIN en el taller, Clientes, Estadísticas).
 * Uso: `useQuery({ ..., refetchInterval: useIntervaloRefresco(sondea) })`.
 *
 * CONTRATO DE ERRORES de las consultas (crearQueryClient, QueryCache.onError):
 * - Un fallo de conexión (red, timeout, 5xx) abre el diálogo "Sin conexión con el servidor: <detalle>" SOLO en el
 *   primer fallo de una consulta que nunca tuvo datos (`query.state.data === undefined && errorUpdateCount === 1`):
 *   es la carga inicial que pidió el usuario al navegar. Los refetch por intervalo, por foco o por invalidación
 *   dejan solo el banner (`errorUpdateCount` sigue subiendo mientras el servidor esté caído, así que el diálogo
 *   no se repite; dentro de los 5 min de gcTime de una consulta fallida tampoco se repite al volver a la vista).
 * - Cualquier otro error (403, 404, 409, 422...) abre el diálogo con su mensaje; las mutaciones avisan siempre.
 * - Una recarga MANUAL (EtiquetaActualizado) con datos en pantalla solo enciende el banner por esta vía; por eso
 *   la etiqueta muestra ella misma el diálogo cuando su `refetch({ throwOnError: true })` falla por conexión.
 */
export function useIntervaloRefresco(activo = true): number | false {
  const conectado = useConexion()
  if (!activo) return false
  return conectado ? INTERVALO_CONECTADO_MS : INTERVALO_DESCONECTADO_MS
}

export { intervaloRefresco }
```

En `conexion.ts`, dejar `intervaloRefresco()` como está (`return conectado ? 60_000 : 5_000`) y en su comentario añadir "ver refresco.ts". En `queryClient.ts`, en el comentario de `QueryCache.onError`, añadir al final: `// Contrato completo documentado en shared/api/refresco.ts.`

`src/shared/ui/EtiquetaActualizado.tsx`:

```tsx
import { ConexionError, esErrorGestionadoGlobalmente, mensajeDeError, mensajeSinConexion } from '@/shared/api/errors'
import { horaLocal } from '@/shared/lib/fechas'
import { useAlerta } from './AlertaProvider'

type Props = {
  /** `dataUpdatedAt` de la consulta (ms); 0 = todavía sin datos. */
  actualizadoEn: number
  /** Recarga manual; debe rechazar si falla (`() => refetch({ throwOnError: true })`). */
  onRecargar: () => Promise<unknown>
}

/** Calco de lblUltimaActualizacion: "Actualizado HH:mm" (10 px, gris, hora local del PC) abajo a la derecha, con
 *  subrayado al pasar y recarga al pulsar. La recarga la pide el usuario, así que un fallo de conexión abre el
 *  diálogo (en el JavaFX el catch de cargar() muestra el Alert porque no es un refresco de fondo). */
export function EtiquetaActualizado({ actualizadoEn, onRecargar }: Props) {
  const { mostrarError } = useAlerta()
  async function recargar() {
    try {
      await onRecargar()
    } catch (e) {
      if (e instanceof ConexionError) mostrarError(mensajeSinConexion(e))
      else if (!esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e))
    }
  }
  return (
    <button type="button" onClick={recargar} className="mt-1 block w-full cursor-pointer text-right text-[10px] text-texto-vacio hover:underline">
      {actualizadoEn > 0 ? `Actualizado ${horaLocal(new Date(actualizadoEn))}` : ''}
    </button>
  )
}
```

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/shared/api src/shared/ui/EtiquetaActualizado.tsx src/shared/ui/EtiquetaActualizado.test.tsx
git commit -m "feat(web): useIntervaloRefresco (60 s / 5 s) con el contrato de errores documentado y EtiquetaActualizado"
```

---

### Task 9: Web — `DataTable` con `colgroup`, ajuste, selección, teclado, ordenación y virtualización

**Files:**
- Modify: `src/shared/ui/DataTable.tsx`, `src/shared/ui/DataTable.test.tsx`, `src/modules/gestion/clientes/ClientesPage.test.tsx` (tests de anchos), `package.json` (+ `@tanstack/react-virtual`)

**Interfaces (produces):**
```ts
export type CeldaPulsada = { columnaId: string; resaltar: () => void }
type Props<T> = {
  columns: ColumnDef<T, any>[]; data: T[]; vacio: string
  filaClase?: (row: T) => string
  menuFila?: (row: T, celda: CeldaPulsada) => ReactNode
  getRowId?: (row: T) => string
  ajuste?: 'fijo' | 'estirar'            // por defecto 'fijo'
  seleccionada?: string | null; onSeleccionar?: (id: string | null) => void   // si `seleccionada` cambia desde fuera y la fila no está a la vista, la tabla se desplaza hasta ella
  onAbrir?: (row: T) => void             // doble clic o Enter
  ordenacion?: boolean                   // por defecto false
  alturaMax?: string                     // por defecto 'calc(100dvh - 330px)'
  umbralVirtual?: number                 // por defecto 200
}
```
Semántica: `size` de cada columna = px en 'fijo' (la tabla mide la suma y el resto del contenedor queda en blanco) o peso en 'estirar' (porcentaje sobre la suma, `min-width` = suma → scroll horizontal por debajo). Sin columna de relleno: un `<colgroup>` fija los anchos. Fila seleccionada: `data-state="selected"`, fondo azul medio, texto crema. Teclado (contenedor con `tabIndex=0` cuando hay `onSeleccionar`): ↑/↓ mueven la selección, Enter abre. Clic derecho selecciona la fila y registra la columna pulsada para `menuFila`.

- [ ] **Step 1: Instalar la dependencia**

```bash
npm install --save-exact @tanstack/react-virtual@3
grep '"@tanstack/react-virtual"' package.json
```
Expected: entrada pinneada (3.x).

- [ ] **Step 2: Reescribir los tests de `DataTable` (fallan)**

`src/shared/ui/DataTable.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ColumnDef } from '@tanstack/react-table'
import { ContextMenuItem } from './context-menu'
import { DataTable } from './DataTable'

type Fila = { id: string; nombre: string; nota: string }

const DATOS: Fila[] = [
  { id: '1', nombre: 'WEB', nota: 'a' },
  { id: '2', nombre: 'OTRO', nota: 'b' },
  { id: '3', nombre: 'AMAZON', nota: 'c' },
]
const COLUMNAS: ColumnDef<Fila, string>[] = [
  { accessorKey: 'nombre', header: 'Nombre', size: 340 },
  { accessorKey: 'nota', header: 'Nota', size: 130 },
]

describe('DataTable', () => {
  it('fija los anchos con un colgroup y sin columna de relleno (ajuste fijo)', () => {
    const { container } = render(<DataTable columns={COLUMNAS} data={DATOS} vacio="Sin filas" />)
    const cols = container.querySelectorAll('col')
    expect(cols).toHaveLength(2)
    expect(cols[0]).toHaveStyle({ width: '340px' })
    expect(cols[1]).toHaveStyle({ width: '130px' })
    expect(container.querySelector('table')).toHaveStyle({ width: '470px' })
    expect(screen.getAllByRole('columnheader')).toHaveLength(2)
    expect(within(screen.getByRole('row', { name: /^WEB a$/ })).getAllByRole('cell')).toHaveLength(2)
  })

  it('en ajuste estirar reparte porcentajes sobre la suma y fija el mínimo', () => {
    const { container } = render(<DataTable columns={COLUMNAS} data={DATOS} vacio="Sin filas" ajuste="estirar" />)
    const cols = container.querySelectorAll('col')
    expect(cols[0].style.width).toMatch(/^72\.34/)
    expect(cols[1].style.width).toMatch(/^27\.65/)
    expect(container.querySelector('table')).toHaveStyle({ minWidth: '470px' })
    expect(container.querySelector('table')).toHaveClass('w-full')
  })

  it('el mensaje de vacío ocupa todas las columnas que se pintan', () => {
    render(<DataTable columns={COLUMNAS} data={[]} vacio="Sin filas" />)
    expect(screen.getByRole('cell', { name: 'Sin filas' })).toHaveAttribute('colspan', '2')
  })

  it('selecciona con clic, mueve con las flechas y abre con Enter o doble clic', async () => {
    const onSeleccionar = vi.fn()
    const onAbrir = vi.fn()
    const { rerender } = render(<DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id} seleccionada={null} onSeleccionar={onSeleccionar} onAbrir={onAbrir} />)
    await userEvent.click(screen.getByText('OTRO'))
    expect(onSeleccionar).toHaveBeenLastCalledWith('2')
    rerender(<DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id} seleccionada="2" onSeleccionar={onSeleccionar} onAbrir={onAbrir} />)
    const fila = screen.getByRole('row', { name: /^OTRO b$/ })
    expect(fila).toHaveAttribute('data-state', 'selected')
    expect(fila).toHaveAttribute('aria-selected', 'true')
    expect(fila).toHaveClass('data-[state=selected]:bg-azul-medio')
    const contenedor = screen.getByRole('table').parentElement!
    contenedor.focus()
    await userEvent.keyboard('{ArrowDown}')
    expect(onSeleccionar).toHaveBeenLastCalledWith('3')
    await userEvent.keyboard('{ArrowUp}')
    expect(onSeleccionar).toHaveBeenLastCalledWith('1')
    await userEvent.keyboard('{Enter}')
    expect(onAbrir).toHaveBeenLastCalledWith(DATOS[1])
    await userEvent.dblClick(screen.getByText('AMAZON'))
    expect(onAbrir).toHaveBeenLastCalledWith(DATOS[2])
  })

  it('el menú contextual recibe la columna pulsada y puede resaltar la celda', async () => {
    const { container } = render(
      <DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id}
        menuFila={(f, celda) => <ContextMenuItem onSelect={celda.resaltar}>{`copiar ${f.nombre} ${celda.columnaId}`}</ContextMenuItem>} />,
    )
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('b') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'copiar OTRO nota' }))
    const celda = within(screen.getByRole('row', { name: /^OTRO b$/ })).getAllByRole('cell')[1]
    expect(celda).toHaveClass('bg-fila-modificada-bg')
    expect(container.querySelector('tbody')).not.toBeNull()
  })

  it('ordena por clic en la cabecera solo si se pide', async () => {
    render(<DataTable columns={COLUMNAS} data={DATOS} vacio="" ordenacion />)
    await userEvent.click(screen.getByRole('button', { name: 'Nombre' }))
    const nombres = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent)
    expect(nombres).toEqual(['AMAZON', 'OTRO', 'WEB'])
    expect(screen.getByRole('columnheader', { name: 'Nombre' })).toHaveAttribute('aria-sort', 'ascending')
  })

  it('sin ordenación las cabeceras no son botones', () => {
    render(<DataTable columns={COLUMNAS} data={DATOS} vacio="" />)
    expect(screen.queryByRole('button', { name: 'Nombre' })).not.toBeInTheDocument()
  })

  it('si la selección cambia desde fuera, desplaza la tabla hasta la fila (restauración al volver de un detalle)', () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    const { rerender } = render(<DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id} seleccionada={null} onSeleccionar={() => {}} />)
    expect(scrollIntoView).not.toHaveBeenCalled()
    rerender(<DataTable columns={COLUMNAS} data={DATOS} vacio="" getRowId={(f) => f.id} seleccionada="3" onSeleccionar={() => {}} />)
    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    expect(scrollIntoView.mock.instances[0]).toBe(screen.getByRole('row', { name: /^AMAZON c$/ }))
  })

  it('por encima del umbral solo pinta las filas visibles (virtualización)', () => {
    const muchas: Fila[] = Array.from({ length: 500 }, (_, i) => ({ id: String(i), nombre: `F${i}`, nota: 'x' }))
    render(<DataTable columns={COLUMNAS} data={muchas} vacio="" getRowId={(f) => f.id} umbralVirtual={100} />)
    const filas = screen.getAllByRole('row').length - 1
    expect(filas).toBeGreaterThan(5)
    expect(filas).toBeLessThan(100)
    expect(screen.getByText('F0')).toBeInTheDocument()
    expect(screen.queryByText('F499')).not.toBeInTheDocument()
  })
})
```

Y en `ClientesPage.test.tsx` sustituir el test `'da a Nombre y Estado el ancho del TableView y rellena el resto sin ensuciar las filas'` por:

```tsx
  it('da a Nombre y Estado el ancho del TableView con un colgroup, sin columna de relleno', async () => {
    const { container } = renderConProviders(<ClientesPage />, { sesion: SESION_TEC })
    await screen.findByText('WEB')
    const cols = container.querySelectorAll('col')
    expect(cols).toHaveLength(2)
    expect(cols[0]).toHaveStyle({ width: '340px' })
    expect(cols[1]).toHaveStyle({ width: '130px' })
    expect(screen.getAllByRole('columnheader')).toHaveLength(2)
    const fila = screen.getByRole('row', { name: /^WEB Activo$/ })
    expect(within(fila).getAllByRole('cell')).toHaveLength(2)
    // lo que sobra es el blanco del contenedor, no una celda
    expect(container.querySelector('table')).toHaveStyle({ width: '470px' })
  })
```

```bash
npm test -- DataTable ClientesPage
```
Expected: fallan (colgroup inexistente, relleno presente, sin selección).

- [ ] **Step 3: Reescribir `DataTable.tsx`**

```tsx
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type Row,
  type SortingState,
} from '@tanstack/react-table'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { ContextMenu, ContextMenuContent, ContextMenuTrigger } from './context-menu'
import { TableBody, TableCell, TableHead, TableHeader, TableRow } from './table'
import { cn } from '@/shared/lib/utils'

/** Qué columna se pulsó con el botón derecho y cómo resaltarla ("📋 Copiar celda"). */
export type CeldaPulsada = { columnaId: string; resaltar: () => void }

type Props<T> = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: ColumnDef<T, any>[]
  data: T[]
  vacio: string
  filaClase?: (row: T) => string
  /** Menú contextual de la fila (equivale al ContextMenu del TableView). */
  menuFila?: (row: T, celda: CeldaPulsada) => ReactNode
  getRowId?: (row: T) => string
  /** 'fijo' (por defecto): cada columna mide su `size` en px y lo que sobra queda en blanco, como el TableView con
   *  columna de relleno. 'estirar': las columnas se reparten el ancho proporcionalmente a `size` (política de
   *  anchos del Historial, `prefWidth = max(min, min·u)`), con scroll horizontal por debajo de la suma. */
  ajuste?: 'fijo' | 'estirar'
  seleccionada?: string | null
  onSeleccionar?: (id: string | null) => void
  /** Doble clic o Enter sobre la fila seleccionada. */
  onAbrir?: (row: T) => void
  /** Ordenación por clic en la cabecera; apagada por defecto porque el JavaFX no ordena por clic. */
  ordenacion?: boolean
  /** Altura máxima del contenedor con scroll. */
  alturaMax?: string
  /** A partir de cuántas filas se pintan solo las visibles (el TableView virtualiza siempre). */
  umbralVirtual?: number
}

/** Borra el `size: 150` que ColumnSizing inyecta por defecto: así `columnDef.size` refleja lo que declaró el consumidor. */
const COLUMNA_POR_DEFECTO = { size: undefined } as const
const ANCHO_SIN_SIZE = 150
const ALTO_FILA_ESTIMADO = 44
const MS_RESALTADO = 600

export function DataTable<T>({
  columns,
  data,
  vacio,
  filaClase,
  menuFila,
  getRowId,
  ajuste = 'fijo',
  seleccionada = null,
  onSeleccionar,
  onAbrir,
  ordenacion = false,
  alturaMax = 'calc(100dvh - 330px)',
  umbralVirtual = 200,
}: Props<T>) {
  const [orden, setOrden] = useState<SortingState>([])
  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table 8 devuelve funciones no memoizables; aviso conocido del React Compiler
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    enableSorting: ordenacion,
    state: { sorting: ordenacion ? orden : [] },
    onSortingChange: setOrden,
    getRowId,
    defaultColumn: COLUMNA_POR_DEFECTO,
  })
  const hojas = table.getVisibleLeafColumns()
  const anchos = hojas.map((c) => c.columnDef.size ?? ANCHO_SIN_SIZE)
  const suma = anchos.reduce((a, b) => a + b, 0)
  const filas = table.getRowModel().rows

  const contenedorRef = useRef<HTMLDivElement>(null)
  const filaRefs = useRef(new Map<string, HTMLTableRowElement>())
  const virtual = filas.length > umbralVirtual
  const virtualizador = useVirtualizer({
    count: filas.length,
    getScrollElement: () => contenedorRef.current,
    estimateSize: () => ALTO_FILA_ESTIMADO,
    overscan: 8,
    enabled: virtual,
    // jsdom no mide: con un rectángulo inicial la virtualización pinta un puñado de filas también en los tests
    initialRect: { width: 1000, height: 600 },
  })

  const [columnaPulsada, setColumnaPulsada] = useState<string>('')
  const [resaltada, setResaltada] = useState<{ fila: string; columna: string } | null>(null)
  useEffect(() => {
    if (!resaltada) return
    const t = setTimeout(() => setResaltada(null), MS_RESALTADO)
    return () => clearTimeout(t)
  }, [resaltada])

  function desplazarA(indice: number) {
    if (virtual) virtualizador.scrollToIndex(indice)
    else filaRefs.current.get(filas[indice].id)?.scrollIntoView({ block: 'nearest' })
  }

  // Selección impuesta desde fuera (p. ej. el maestro de IMEIs reseleccionando el IMEI al volver del detalle, o el
  // enlace "Id Rep. Anterior"): si la fila no está a la vista, desplazar hasta ella con tres filas de contexto por
  // encima (calco de tabla.scrollTo(idx - 3)). Con una selección por clic la fila ya está a la vista y no pasa nada.
  useEffect(() => {
    if (seleccionada === null) return
    const idx = filas.findIndex((r) => r.id === seleccionada)
    if (idx < 0) return
    if (virtual) {
      if (!virtualizador.getVirtualItems().some((v) => v.index === idx)) virtualizador.scrollToIndex(Math.max(0, idx - 3), { align: 'start' })
    } else {
      filaRefs.current.get(seleccionada)?.scrollIntoView({ block: 'nearest' })
    }
  }, [seleccionada, filas, virtual, virtualizador])

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (!onSeleccionar || filas.length === 0) return
    const idx = seleccionada === null ? -1 : filas.findIndex((r) => r.id === seleccionada)
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const nuevo = e.key === 'ArrowDown' ? Math.min(idx + 1, filas.length - 1) : Math.max(idx - 1, 0)
      onSeleccionar(filas[nuevo].id)
      desplazarA(nuevo)
    } else if (e.key === 'Enter' && idx >= 0 && onAbrir) {
      e.preventDefault()
      onAbrir(filas[idx].original)
    }
  }

  function pintarFila(row: Row<T>, indice: number) {
    const id = row.id
    const sel = seleccionada === id
    const tr = (
      <TableRow
        key={id}
        data-index={indice}
        ref={(el: HTMLTableRowElement | null) => {
          if (el) {
            filaRefs.current.set(id, el)
            if (virtual) virtualizador.measureElement(el)
          } else {
            filaRefs.current.delete(id)
          }
        }}
        data-state={sel ? 'selected' : undefined}
        aria-selected={onSeleccionar ? sel : undefined}
        className={cn(
          'border-b border-fila-sep',
          filaClase?.(row.original),
          onSeleccionar && 'cursor-default data-[state=selected]:bg-azul-medio data-[state=selected]:text-crema',
        )}
        onClick={() => onSeleccionar?.(id)}
        onDoubleClick={() => onAbrir?.(row.original)}
        onContextMenu={(e) => {
          setColumnaPulsada((e.target as HTMLElement).closest('td')?.dataset.columna ?? '')
          onSeleccionar?.(id)
        }}
      >
        {row.getVisibleCells().map((c) => (
          <TableCell
            key={c.id}
            data-columna={c.column.id}
            className={cn('text-[12px]', resaltada?.fila === id && resaltada.columna === c.column.id && 'bg-fila-modificada-bg')}
          >
            {flexRender(c.column.columnDef.cell, c.getContext())}
          </TableCell>
        ))}
      </TableRow>
    )
    if (!menuFila) return tr
    const celda: CeldaPulsada = { columnaId: columnaPulsada, resaltar: () => setResaltada({ fila: id, columna: columnaPulsada }) }
    return (
      <ContextMenu key={id}>
        <ContextMenuTrigger asChild>{tr}</ContextMenuTrigger>
        <ContextMenuContent>{menuFila(row.original, celda)}</ContextMenuContent>
      </ContextMenu>
    )
  }

  const items = virtual ? virtualizador.getVirtualItems() : null
  const total = virtual ? virtualizador.getTotalSize() : 0
  const arriba = items && items.length > 0 ? items[0].start : 0
  const abajo = items && items.length > 0 ? total - items[items.length - 1].end : 0

  return (
    <div
      ref={contenedorRef}
      tabIndex={onSeleccionar ? 0 : undefined}
      onKeyDown={onKeyDown}
      className="overflow-auto rounded-md bg-superficie outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
      style={{ maxHeight: alturaMax }}
    >
      <table
        className={cn('table-fixed caption-bottom text-sm', ajuste === 'estirar' && 'w-full')}
        style={ajuste === 'estirar' ? { minWidth: suma } : { width: suma }}
      >
        <colgroup>
          {hojas.map((c, i) => (
            <col key={c.id} style={{ width: ajuste === 'estirar' ? `${(anchos[i] / suma) * 100}%` : anchos[i] }} />
          ))}
        </colgroup>
        <TableHeader className="sticky top-0 z-10 bg-crema">
          {table.getHeaderGroups().map((hg) => (
            <TableRow key={hg.id}>
              {hg.headers.map((h) => {
                const dir = h.column.getIsSorted()
                const contenido = h.isPlaceholder ? null : flexRender(h.column.columnDef.header, h.getContext())
                return (
                  <TableHead
                    key={h.id}
                    colSpan={h.colSpan}
                    aria-sort={dir === 'asc' ? 'ascending' : dir === 'desc' ? 'descending' : undefined}
                    className="text-[12px] font-bold text-azul-medio"
                  >
                    {ordenacion && h.column.getCanSort() ? (
                      <button type="button" className="cursor-pointer" onClick={h.column.getToggleSortingHandler()}>
                        {contenido}
                      </button>
                    ) : (
                      contenido
                    )}
                  </TableHead>
                )
              })}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {filas.length === 0 && (
            <TableRow>
              <TableCell colSpan={hojas.length} className="py-8 text-center text-azul-gris">{vacio}</TableCell>
            </TableRow>
          )}
          {!virtual && filas.map((r, i) => pintarFila(r, i))}
          {virtual && items && (
            <>
              {arriba > 0 && <tr aria-hidden style={{ height: arriba }}><td colSpan={hojas.length} /></tr>}
              {items.map((vi) => pintarFila(filas[vi.index], vi.index))}
              {abajo > 0 && <tr aria-hidden style={{ height: abajo }}><td colSpan={hojas.length} /></tr>}
            </>
          )}
        </TableBody>
      </table>
    </div>
  )
}
```

Notas para el implementador: React 19 pasa `ref` como prop normal a `TableRow` (función que la reparte al `<tr>`), no hace falta `forwardRef`. Si `useVirtualizer` no acepta `enabled` en la versión instalada, mantener `count: virtual ? filas.length : 0`.

- [ ] **Step 4: Verde y commit**

```bash
npm run check
git add package.json package-lock.json src/shared/ui/DataTable.tsx src/shared/ui/DataTable.test.tsx src/modules/gestion/clientes/ClientesPage.test.tsx
git commit -m "feat(web): DataTable con colgroup, ajuste fijo/estirar, seleccion y teclado, ordenacion opcional y virtualizacion"
```

---

### Task 10: Web — componentes compartidos del taller

**Files:**
- Create: `src/shared/ui/pildora.ts`, `TogglePill.tsx` (+test), `FiltroImei.tsx` (+test), `RangoFechas.tsx` (+test), `BadgeTipo.tsx` (+test), `CeldaFechas.tsx` (+test), `copiar.ts`, `MenuCopiarCelda.tsx` (+test), `TextoExpandible.tsx` (+test), `SelectorLista.tsx` (+test)
- Modify: `src/shared/ui/MultiSelect.tsx` (+`textoTodas`, etiqueta de la selección única), `MultiSelect.test.tsx`, `src/shared/ui/ConfirmDialog.tsx` (+motivo), `ConfirmDialog.test.tsx`

**Interfaces (produces):**
- `pildora.ts`: `CLASES_PILDORA = 'inline-block rounded-[10px] px-2.5 py-0.5 text-[11px] font-bold'`, `CLASES_MINI_PILDORA = 'inline-block rounded-lg px-2 py-px text-[10px] font-bold'`.
- `<TogglePill opciones={[{ to, etiqueta }]} />` (enlaces `NavLink end`).
- `<FiltroImei valor onChange className? />` (input "Filtrar por IMEI", canonicaliza, borde por estado).
- `<RangoFechas desde hasta onChange(desde, hasta) />` (valores `'yyyy-MM-dd'` o `''`).
- `MultiSelect`: nueva prop opcional `textoTodas?: string`; la etiqueta de una única selección es la `etiqueta` de la opción (no la clave).
- `<BadgeTipo idRep esChasis? />`.
- `<CeldaFechas inicio fin patron />`.
- `copiarAlPortapapeles(texto): Promise<void>`; `<MenuCopiarCelda texto celda />` (`TEXTO_COPIAR_CELDA = '📋  Copiar celda'`).
- `<TextoExpandible titulo texto className? />`.
- `<SelectorLista abierto titulo etiquetaLista? placeholderBuscar opciones claveActual? textoNada? textoSeleccionar onSeleccionar onCancelar />` con `OpcionLista = { clave: string; etiqueta: string }`.
- `ConfirmDialog`: nueva prop `conMotivo?: boolean`; `onConfirmar: (motivo?: string) => void` (motivo recortado, solo con `conMotivo`).

- [ ] **Step 1: Tests (fallan)**

`src/shared/ui/TogglePill.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderConProviders } from '@/test/render'
import { TogglePill } from './TogglePill'

const OPCIONES = [
  { to: '/reparaciones/pendientes', etiqueta: 'Reparaciones (10)' },
  { to: '/reparaciones/pendientes/glass', etiqueta: 'Glass (0)' },
  { to: '/reparaciones/pendientes/pulidos', etiqueta: 'Pulidos (0)' },
]

describe('TogglePill (calco de toggle-pill-left/mid/right)', () => {
  it('marca activa solo la ruta exacta y redondea los extremos', () => {
    renderConProviders(<TogglePill opciones={OPCIONES} />, { ruta: '/reparaciones/pendientes' })
    const rep = screen.getByRole('link', { name: 'Reparaciones (10)' })
    const glass = screen.getByRole('link', { name: 'Glass (0)' })
    const pul = screen.getByRole('link', { name: 'Pulidos (0)' })
    expect(rep).toHaveAttribute('aria-current', 'page')
    expect(glass).not.toHaveAttribute('aria-current')
    expect(rep).toHaveClass('rounded-l-3xl', 'bg-azul-noche', 'text-superficie')
    expect(glass).toHaveClass('bg-pill-bg', 'text-azul-gris')
    expect(pul).toHaveClass('rounded-r-3xl')
  })
})
```

`src/shared/ui/FiltroImei.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { FiltroImei } from './FiltroImei'

function Prueba() {
  const [v, setV] = useState('')
  return <FiltroImei valor={v} onChange={setV} />
}

describe('FiltroImei (calco del TextField "Filtrar por IMEI")', () => {
  it('canonicaliza al teclear y pinta el borde por estado', async () => {
    render(<Prueba />)
    const campo = screen.getByPlaceholderText('Filtrar por IMEI')
    expect(campo).toHaveClass('border-azul-gris')
    await userEvent.type(campo, '3554')
    expect(campo).toHaveValue('3554')
    expect(campo).toHaveClass('border-fila-incidencia-brd')
    await userEvent.type(campo, '00000000111')
    expect(campo).toHaveValue('355400000000111, ')
    expect(campo).toHaveClass('border-fila-reparado-ico')
    await userEvent.type(campo, 'x1')
    expect(campo).toHaveValue('355400000000111, 1')
  })
})
```

`src/shared/ui/RangoFechas.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RangoFechas } from './RangoFechas'

describe('RangoFechas (calco de Desde:/Hasta: + DatePicker)', () => {
  it('etiqueta los dos campos y avisa con el par completo', async () => {
    const onChange = vi.fn()
    render(<RangoFechas desde="" hasta="2026-09-16" onChange={onChange} />)
    const desde = screen.getByLabelText('Desde:')
    expect(desde).toHaveAttribute('type', 'date')
    expect(screen.getByLabelText('Hasta:')).toHaveValue('2026-09-16')
    await userEvent.type(desde, '2026-09-01')
    expect(onChange).toHaveBeenLastCalledWith('2026-09-01', '2026-09-16')
  })
})
```

En `MultiSelect.test.tsx` añadir:

```tsx
  it('con textoTodas y todas marcadas muestra "Todas"; con una marcada muestra su etiqueta, no su clave', async () => {
    const opciones = [{ id: 1, nombre: 'Técnico A' }, { id: 2, nombre: 'Técnico F' }]
    const { rerender } = render(<MultiSelect opciones={opciones} clave={(o) => String(o.id)} etiqueta={(o) => o.nombre} seleccion={new Set(['2'])} onChange={() => {}} textoVacio="Técnico" textoPlural={(n) => `${n} técnicos`} textoTodas="Todas" />)
    expect(screen.getByRole('button', { name: 'Técnico F' })).toBeInTheDocument()
    rerender(<MultiSelect opciones={opciones} clave={(o) => String(o.id)} etiqueta={(o) => o.nombre} seleccion={new Set(['1', '2'])} onChange={() => {}} textoVacio="Técnico" textoPlural={(n) => `${n} técnicos`} textoTodas="Todas" />)
    expect(screen.getByRole('button', { name: 'Todas' })).toBeInTheDocument()
    rerender(<MultiSelect opciones={opciones} clave={(o) => String(o.id)} etiqueta={(o) => o.nombre} seleccion={new Set(['1', '2'])} onChange={() => {}} textoVacio="Técnico" textoPlural={(n) => `${n} técnicos`} />)
    expect(screen.getByRole('button', { name: '2 técnicos' })).toBeInTheDocument()
  })
```
(añadir `render` y `screen` a los imports del test si faltan).

`src/shared/ui/BadgeTipo.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { BadgeTipo } from './BadgeTipo'

describe('BadgeTipo (calco de celdaTipoConChasis)', () => {
  it('píldora del tipo por prefijo y "Chasis" solo en reparaciones con chasis', () => {
    const { rerender } = render(<BadgeTipo idRep="A20260916_12" esChasis />)
    expect(screen.getByText('Reparación')).toHaveClass('bg-tipo-reparacion-bg', 'text-tipo-reparacion-text', 'rounded-[10px]', 'text-[11px]', 'font-bold')
    expect(screen.getByText('Chasis')).toHaveClass('text-[10px]', 'text-texto-sub')
    rerender(<BadgeTipo idRep="AG20260916_1" esChasis />)
    expect(screen.getByText('Glass')).toHaveClass('bg-tipo-glass-bg')
    expect(screen.queryByText('Chasis')).not.toBeInTheDocument()
    rerender(<BadgeTipo idRep="P20260916_1" />)
    expect(screen.getByText('Pulido')).toHaveClass('bg-tipo-pulido-bg')
  })
})
```

`src/shared/ui/CeldaFechas.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CeldaFechas } from './CeldaFechas'

describe('CeldaFechas (calco de la columna Fechas)', () => {
  it('dos líneas: inicio en gris pequeño y "→ fin" en azul medio; "—" si falta', () => {
    render(<CeldaFechas inicio="2026-09-11T07:00:00" fin={null} patron="yyyy/MM/dd" />)
    expect(screen.getByText('2026/09/11')).toHaveClass('text-[10px]', 'text-texto-fecha-inicio')
    expect(screen.getByText('→ —')).toHaveClass('text-[11px]', 'text-azul-medio')
  })
})
```

`src/shared/ui/MenuCopiarCelda.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ContextMenu, ContextMenuContent, ContextMenuTrigger } from './context-menu'
import { MenuCopiarCelda } from './MenuCopiarCelda'

describe('MenuCopiarCelda', () => {
  it('copia el texto y resalta la celda; con texto vacío no hace nada', async () => {
    const escribir = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText: escribir } })
    const resaltar = vi.fn()
    const { rerender } = render(
      <ContextMenu><ContextMenuTrigger>fila</ContextMenuTrigger><ContextMenuContent><MenuCopiarCelda texto="355400000000111" celda={{ columnaId: 'imei', resaltar }} /></ContextMenuContent></ContextMenu>,
    )
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('fila') })
    await userEvent.click(await screen.findByRole('menuitem', { name: /Copiar celda/ }))
    expect(escribir).toHaveBeenCalledWith('355400000000111')
    expect(resaltar).toHaveBeenCalledTimes(1)
    rerender(
      <ContextMenu><ContextMenuTrigger>fila</ContextMenuTrigger><ContextMenuContent><MenuCopiarCelda texto={null} celda={{ columnaId: 'x', resaltar }} /></ContextMenuContent></ContextMenu>,
    )
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('fila') })
    await userEvent.click(await screen.findByRole('menuitem', { name: /Copiar celda/ }))
    expect(escribir).toHaveBeenCalledTimes(1)
    expect(resaltar).toHaveBeenCalledTimes(1)
  })
})
```

`src/shared/ui/TextoExpandible.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TextoExpandible } from './TextoExpandible'

describe('TextoExpandible (calco de labelExpandible + ConfirmDialog.mostrarTexto)', () => {
  it('con texto abre el popup con el texto completo y "Copiar" copia y cierra', async () => {
    const escribir = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText: escribir } })
    render(<TextoExpandible titulo="Observaciones" texto="maquina laser" />)
    await userEvent.click(screen.getByRole('button', { name: 'maquina laser' }))
    const dlg = screen.getByRole('dialog', { name: 'Observaciones' })
    expect(dlg.querySelector('textarea')).toHaveValue('maquina laser')
    await userEvent.click(screen.getByRole('button', { name: 'Copiar' }))
    expect(escribir).toHaveBeenCalledWith('maquina laser')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('sin texto no pinta nada', () => {
    const { container } = render(<TextoExpandible titulo="Observaciones" texto={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})
```

`src/shared/ui/SelectorLista.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SelectorLista } from './SelectorLista'

const OPCIONES = [{ clave: 'sin', etiqueta: '— Sin cliente —' }, { clave: '1', etiqueta: 'AMAZON' }, { clave: '2', etiqueta: 'CLIENTE A' }]

describe('SelectorLista (calco de SelectorClienteDialog)', () => {
  it('filtra, resalta el actual, muestra "Nada seleccionado" y deshabilita Seleccionar hasta elegir', async () => {
    const onSeleccionar = vi.fn()
    render(<SelectorLista abierto titulo="Seleccionar cliente" placeholderBuscar="Buscar cliente..." opciones={OPCIONES} claveActual="1" textoNada="Nada seleccionado" textoSeleccionar="Seleccionar" onSeleccionar={onSeleccionar} onCancelar={() => {}} />)
    const dlg = screen.getByRole('dialog', { name: 'Seleccionar cliente' })
    expect(within(dlg).getByText('Nada seleccionado')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Seleccionar' })).toBeDisabled()
    expect(within(dlg).getByRole('button', { name: 'AMAZON' })).toHaveClass('bg-seleccion-suave')
    await userEvent.type(within(dlg).getByPlaceholderText('Buscar cliente...'), 'cliente a')
    expect(within(dlg).queryByRole('button', { name: 'AMAZON' })).not.toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'CLIENTE A' }))
    expect(within(dlg).getByText('CLIENTE A', { selector: 'p' })).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Seleccionar' }))
    expect(onSeleccionar).toHaveBeenCalledWith('2')
  })
  it('Cancelar y la ✕ avisan', async () => {
    const onCancelar = vi.fn()
    render(<SelectorLista abierto titulo="Editar modelo" etiquetaLista="Selecciona el modelo:" placeholderBuscar="Filtrar modelo…" opciones={OPCIONES} textoSeleccionar="Guardar" onSeleccionar={() => {}} onCancelar={onCancelar} />)
    expect(screen.getByText('Selecciona el modelo:')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancelar).toHaveBeenCalledTimes(1)
  })
})
```

En `ConfirmDialog.test.tsx` añadir:

```tsx
  it('con motivo: el botón rojo se habilita al escribir y confirma con el motivo recortado', async () => {
    const ok = vi.fn()
    render(<ConfirmDialog abierto conMotivo titulo="Borrar reparación" descripcion="Se borrará R1. Escribe el motivo." textoAccion="Borrar reparación" onConfirmar={ok} onCancelar={vi.fn()} />)
    const accion = screen.getByRole('button', { name: 'Borrar reparación' })
    expect(accion).toBeDisabled()
    const motivo = screen.getByPlaceholderText('Escribe el motivo del borrado...')
    await waitFor(() => expect(motivo).toHaveFocus())
    await userEvent.type(motivo, '  error al crear  ')
    expect(accion).toBeEnabled()
    await userEvent.click(accion)
    expect(ok).toHaveBeenCalledWith('error al crear')
  })
```

```bash
npm test -- src/shared/ui
```
Expected: fallan los nuevos.

- [ ] **Step 2: Implementar**

`src/shared/ui/pildora.ts`:

```ts
/** Estilos de las píldoras del JavaFX: badge (radio 10, 11 px negrita, padding 2 10) y mini-píldora bajo el IMEI (radio 8, 10 px). */
export const CLASES_PILDORA = 'inline-block rounded-[10px] px-2.5 py-0.5 text-[11px] font-bold'
export const CLASES_MINI_PILDORA = 'inline-block rounded-lg px-2 py-px text-[10px] font-bold'
```

`src/shared/ui/TogglePill.tsx`:

```tsx
import { NavLink } from 'react-router'
import { cn } from '@/shared/lib/utils'

export type OpcionToggle = { to: string; etiqueta: string }

/** Calco de toggle-pill-left/mid/right: píldora segmentada (extremos redondeados, centro recto, activo navy con texto
 *  blanco). Cada opción es un enlace porque los toggles del taller son rutas. */
export function TogglePill({ opciones, className }: { opciones: OpcionToggle[]; className?: string }) {
  return (
    <div role="group" className={cn('inline-flex', className)}>
      {opciones.map((o, i) => (
        <NavLink
          key={o.to}
          to={o.to}
          end
          className={({ isActive }) =>
            cn(
              'border border-pill-borde px-3.5 py-[5px] text-[12px] font-bold',
              i === 0 && 'rounded-l-3xl',
              i === opciones.length - 1 && 'rounded-r-3xl',
              i > 0 && '-ml-px',
              isActive ? 'border-azul-noche bg-azul-noche text-superficie' : 'bg-pill-bg text-azul-gris hover:bg-azul-medio/8',
            )
          }
        >
          {o.etiqueta}
        </NavLink>
      ))}
    </div>
  )
}
```

`src/shared/ui/FiltroImei.tsx`:

```tsx
import { useLayoutEffect, useRef } from 'react'
import { canonicalizarImei, estadoFiltroImei } from '@/shared/lib/filtroImei'
import { cn } from '@/shared/lib/utils'

const BORDE = {
  vacio: 'border-azul-gris bg-superficie',
  incompleto: 'border-fila-incidencia-brd bg-fondo-input',
  valido: 'border-fila-reparado-ico bg-fondo-input',
} as const

type Props = { valor: string; onChange: (valor: string) => void; className?: string }

/** Calco del TextField `.buscador` "Filtrar por IMEI" con FiltroImei: canonicaliza al teclear (el caret va al final,
 *  como positionCaret) y pinta el borde rojo con tokens incompletos o verde con IMEIs válidos. */
export function FiltroImei({ valor, onChange, className }: Props) {
  const ref = useRef<HTMLInputElement>(null)
  const moverCaret = useRef(false)
  useLayoutEffect(() => {
    if (!moverCaret.current || !ref.current) return
    moverCaret.current = false
    ref.current.setSelectionRange(valor.length, valor.length)
  }, [valor])
  return (
    <input
      ref={ref}
      type="text"
      placeholder="Filtrar por IMEI"
      aria-label="Filtrar por IMEI"
      value={valor}
      onChange={(e) => {
        const canonico = canonicalizarImei(e.target.value)
        if (canonico !== e.target.value) moverCaret.current = true
        onChange(canonico)
      }}
      className={cn('h-10 w-[160px] rounded border px-2.5 text-[12px] text-azul-medio outline-none placeholder:text-texto-suave', BORDE[estadoFiltroImei(valor)], className)}
    />
  )
}
```

`src/shared/ui/RangoFechas.tsx`:

```tsx
import { useId } from 'react'

type Props = { desde: string; hasta: string; onChange: (desde: string, hasta: string) => void }

const CLASE = 'h-10 w-[130px] rounded-3xl border border-azul-noche bg-superficie px-3 text-[12px] text-azul-medio'

/** Calco de "Desde:" / "Hasta:" con DatePicker: valores 'yyyy-MM-dd' o '' (sin filtro). */
export function RangoFechas({ desde, hasta, onChange }: Props) {
  const id = useId()
  return (
    <>
      <label htmlFor={`${id}-desde`} className="text-[12px] text-azul-medio">Desde:</label>
      <input id={`${id}-desde`} type="date" value={desde} onChange={(e) => onChange(e.target.value, hasta)} className={CLASE} />
      <label htmlFor={`${id}-hasta`} className="text-[12px] text-azul-medio">Hasta:</label>
      <input id={`${id}-hasta`} type="date" value={hasta} onChange={(e) => onChange(desde, e.target.value)} className={CLASE} />
    </>
  )
}
```

`MultiSelect.tsx`: cambiar `textoMultiSelect` y el cálculo del texto:

```ts
export function textoMultiSelect(seleccion: string[], textoVacio: string, textoPlural: (n: number) => string, total?: number, textoTodas?: string) {
  if (seleccion.length === 0) return textoVacio
  if (textoTodas !== undefined && total !== undefined && total > 1 && seleccion.length === total) return textoTodas
  if (seleccion.length === 1) return seleccion[0]
  return textoPlural(seleccion.length)
}
```
En `Props<T>` añadir `textoTodas?: string`, y en el componente:

```ts
  // La etiqueta de una única selección es el nombre de la opción, no su clave (los técnicos van por id)
  const nombres = [...seleccion].map((k) => { const o = opciones.find((x) => clave(x) === k); return o ? etiqueta(o) : k })
  const texto = textoMultiSelect(nombres, textoVacio, textoPlural, opciones.length, textoTodas)
```

`src/shared/ui/BadgeTipo.tsx`:

```tsx
import { TIPO_TRABAJO, tipoDe } from '@/shared/lib/tipoTrabajo'
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from './pildora'

/** Calco de TipoTrabajo.celdaTipoConChasis: píldora del tipo y "Chasis" debajo solo en reparaciones con chasis. */
export function BadgeTipo({ idRep, esChasis = false }: { idRep: string; esChasis?: boolean }) {
  const tipo = tipoDe(idRep)
  return (
    <div className="flex flex-col items-start gap-px">
      <span className={cn(CLASES_PILDORA, TIPO_TRABAJO[tipo].clases)}>{TIPO_TRABAJO[tipo].etiqueta}</span>
      {esChasis && tipo === 'REPARACION' && <span className="text-[10px] text-texto-sub">Chasis</span>}
    </div>
  )
}
```

`src/shared/ui/CeldaFechas.tsx`:

```tsx
import { formatear, type Patron } from '@/shared/lib/fechas'

/** Calco de la columna "Fechas" (asignación en gris pequeño, "→ fin" debajo; "—" si falta). */
export function CeldaFechas({ inicio, fin, patron }: { inicio: string | null; fin: string | null; patron: Patron }) {
  return (
    <div className="flex flex-col leading-tight">
      <span className="text-[10px] text-texto-fecha-inicio">{formatear(inicio, patron) || '—'}</span>
      <span className="text-[11px] text-azul-medio">→ {formatear(fin, patron) || '—'}</span>
    </div>
  )
}
```

`src/shared/ui/copiar.ts`:

```ts
/** Portapapeles con fallback para contextos sin `navigator.clipboard` (http en local). */
export async function copiarAlPortapapeles(texto: string): Promise<void> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(texto)
      return
    }
  } catch {
    /* cae al fallback */
  }
  const area = document.createElement('textarea')
  area.value = texto
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  try {
    document.execCommand('copy')
  } finally {
    area.remove()
  }
}
```

`src/shared/ui/MenuCopiarCelda.tsx`:

```tsx
import { ContextMenuItem } from './context-menu'
import type { CeldaPulsada } from './DataTable'
import { copiarAlPortapapeles } from './copiar'

export const TEXTO_COPIAR_CELDA = '📋  Copiar celda'

/** "📋  Copiar celda" del JavaFX: copia el texto de la columna pulsada y resalta la celda; con texto nulo o vacío
 *  (columna no copiable) no hace nada. */
export function MenuCopiarCelda({ texto, celda }: { texto: string | null | undefined; celda: CeldaPulsada }) {
  return (
    <ContextMenuItem
      onSelect={() => {
        if (!texto) return
        void copiarAlPortapapeles(texto)
        celda.resaltar()
      }}
    >
      {TEXTO_COPIAR_CELDA}
    </ContextMenuItem>
  )
}
```

`src/shared/ui/TextoExpandible.tsx`:

```tsx
import { useState } from 'react'
import { cn } from '@/shared/lib/utils'
import { Button } from './button'
import { copiarAlPortapapeles } from './copiar'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from './dialog'

type Props = { titulo: string; texto: string | null | undefined; className?: string }

/** Calco de labelExpandible + ConfirmDialog.mostrarTexto: texto con elipsis y cursor de mano; el clic abre el popup
 *  con el título, el texto completo de solo lectura y "Copiar" (copia y cierra). Sin texto no pinta nada. */
export function TextoExpandible({ titulo, texto, className }: Props) {
  const [abierto, setAbierto] = useState(false)
  if (!texto) return null
  return (
    <>
      <button type="button" onClick={() => setAbierto(true)} className={cn('block w-full cursor-pointer truncate text-left', className)}>
        {texto}
      </button>
      <Dialog open={abierto} onOpenChange={setAbierto}>
        <DialogContent aria-describedby={undefined} className="max-w-[420px] gap-2.5 bg-crema p-5">
          <DialogHeader>
            <DialogTitle className="text-[14px] font-bold text-azul-medio">{titulo}</DialogTitle>
          </DialogHeader>
          <textarea readOnly value={texto} rows={6} className="w-full resize-none rounded border border-fila-sep bg-superficie p-2 text-[13px] text-azul-medio" />
          <Button
            className="h-auto w-full rounded bg-azul-medio py-2 text-[12px] text-crema hover:bg-azul-medio/90"
            onClick={() => {
              void copiarAlPortapapeles(texto)
              setAbierto(false)
            }}
          >
            Copiar
          </Button>
        </DialogContent>
      </Dialog>
    </>
  )
}
```

`src/shared/ui/SelectorLista.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { cn } from '@/shared/lib/utils'
import { Button } from './button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from './dialog'
import { Input } from './input'

export type OpcionLista = { clave: string; etiqueta: string }

type Props = {
  abierto: boolean
  titulo: string
  etiquetaLista?: string
  placeholderBuscar: string
  opciones: OpcionLista[]
  claveActual?: string | null
  /** Si se da, debajo de la lista se muestra este texto o la etiqueta elegida (SelectorClienteDialog). */
  textoNada?: string
  textoSeleccionar: string
  onSeleccionar: (clave: string) => void
  onCancelar: () => void
}

/** Calco de SelectorClienteDialog y del selector "Editar modelo": buscador que filtra por etiqueta, lista con la opción
 *  actual resaltada y la elegida en navy, botón principal deshabilitado hasta elegir; doble clic elige directamente. */
export function SelectorLista({ abierto, titulo, etiquetaLista, placeholderBuscar, opciones, claveActual = null, textoNada, textoSeleccionar, onSeleccionar, onCancelar }: Props) {
  const [busqueda, setBusqueda] = useState('')
  const [seleccion, setSeleccion] = useState<string | null>(null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el buscador y la selección al reabrir (patrón "Adjusting state", como ClienteDialog)
    if (abierto) { setBusqueda(''); setSeleccion(null) }
  }, [abierto])
  const filtro = busqueda.trim().toLowerCase()
  const visibles = filtro === '' ? opciones : opciones.filter((o) => o.etiqueta.toLowerCase().includes(filtro))
  const elegida = opciones.find((o) => o.clave === seleccion)
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent aria-describedby={undefined} className="max-w-[440px] gap-3 bg-crema p-6">
        <DialogHeader>
          <DialogTitle className="text-[16px] font-bold text-azul-medio">{titulo}</DialogTitle>
        </DialogHeader>
        {etiquetaLista && <p className="text-[12px] text-azul-medio">{etiquetaLista}</p>}
        <Input value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder={placeholderBuscar} aria-label={placeholderBuscar} className="bg-superficie text-[13px]" />
        <ul role="listbox" aria-label={titulo} className="max-h-[300px] overflow-auto rounded border border-fila-sep bg-superficie py-1">
          {visibles.map((o) => (
            <li key={o.clave} role="option" aria-selected={seleccion === o.clave}>
              <button
                type="button"
                onClick={() => setSeleccion(o.clave)}
                onDoubleClick={() => onSeleccionar(o.clave)}
                className={cn(
                  'mx-1 my-0.5 block w-[calc(100%-8px)] rounded px-3 py-1 text-left text-[12px] text-azul-noche hover:bg-seleccion-suave',
                  seleccion === o.clave ? 'bg-azul-noche font-bold text-superficie hover:bg-azul-noche' : claveActual === o.clave && 'bg-seleccion-suave font-bold',
                )}
              >
                {o.etiqueta}
              </button>
            </li>
          ))}
        </ul>
        {textoNada !== undefined && <p className="text-[12px] text-azul-gris">{elegida ? elegida.etiqueta : textoNada}</p>}
        <Button disabled={!seleccion} onClick={() => seleccion && onSeleccionar(seleccion)} className="h-auto w-full rounded bg-azul-medio py-2.5 text-[12px] text-crema hover:bg-azul-medio/90">
          {textoSeleccionar}
        </Button>
        <Button variant="outline" onClick={onCancelar} className="h-auto w-full rounded border-azul-gris bg-crema py-2.5 text-[12px] text-azul-gris shadow-none hover:bg-crema hover:text-azul-gris">
          Cancelar
        </Button>
      </DialogContent>
    </Dialog>
  )
}
```

`ConfirmDialog.tsx`: props `conMotivo?: boolean` y `onConfirmar: (motivo?: string) => void`; dentro:

```tsx
  const [motivo, setMotivo] = useState('')
  const motivoRef = useRef<HTMLTextAreaElement>(null)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el motivo al reabrir
    if (abierto) setMotivo('')
  }, [abierto])
  const listo = !conMotivo || motivo.trim() !== ''
```
En `onOpenAutoFocus`: `(conMotivo ? motivoRef.current : cancelarRef.current)?.focus()`. Entre `DialogHeader` y `DialogFooter`, cuando `conMotivo`:

```tsx
        {conMotivo && (
          <textarea
            ref={motivoRef}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Escribe el motivo del borrado..."
            rows={3}
            className="w-full resize-none rounded border border-fila-sep bg-superficie p-2 text-[13px] text-azul-medio"
          />
        )}
```
Y el botón de acción: `disabled={!listo}` con `onClick={() => onConfirmar(conMotivo ? motivo.trim() : undefined)}` (añadir `disabled:opacity-60` a sus clases). Importar `useLayoutEffect`, `useState`.

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/shared/ui
git commit -m "feat(web): componentes compartidos del taller (TogglePill, FiltroImei, RangoFechas, BadgeTipo, CeldaFechas, TextoExpandible, SelectorLista, copiar celda, ConfirmDialog con motivo, MultiSelect con Todas)"
```

---

### Task 11: Web — lógica pura del taller (1): piezas, modelos, entregaGlass y fábrica de tests

**Files:**
- Create: `src/modules/taller/test/fabrica.ts`, `src/modules/taller/lib/piezas.ts` (+test), `modelos.ts` (+test), `entregaGlass.ts` (+test)

**Interfaces (produces):**
- `resumen(parcial?: Partial<ReparacionResumen>): ReparacionResumen` (fábrica de tests con todos los campos).
- `categoriaPieza(sku): string`.
- `MODELOS_ORDENADOS: readonly string[]`, `traducirModelo(codigo): string` ('' si nulo/vacío).
- `entregaGlass.ts` (todas reciben `ReparacionResumen | null`): `textoBadgeEntrega(rep, hoy: string | null)`, `tooltipEntrega(rep)`, `opcionMenuEntrega(rep, pestanaGlass, idTecSesion)`, `opcionDeshacerLlegada(rep, pestanaGlass, idTecSesion)`, `mostrarMarcarLlegada(rep, pestanaGlass)`, `ocultarAnadirGlass(rep)`, `subEtiquetaHistorial(rep)`, `etiquetaGlassPendiente(rep)`, `tooltipGlassPendiente(rep)`, `etiquetaRepAbierta(rep)`, `tooltipRepAbierta(rep)`, `textoCsvEntrega(rep)`; `hoy` es 'yyyy-MM-dd' de Madrid (`hoyMadrid()`).

- [ ] **Step 1: Fábrica y tests (fallan)**

`src/modules/taller/test/fabrica.ts`:

```ts
import type { ReparacionResumen, Tecnico } from '@/shared/api/client'

/** Fila completa con valores por defecto; cada test sobrescribe lo que le importa. */
export function resumen(parcial: Partial<ReparacionResumen> = {}): ReparacionResumen {
  return {
    idRep: 'A20260916_1', imei: '355400000000111', nombreTecnico: 'Técnico A', fechaAsig: '2026-09-16T07:02:00', fechaFin: null,
    tipoComponente: null, observaciones: null, esIncidencia: false, esResuelto: false, esReutilizado: false, incidencia: null,
    idRepAnterior: null, idTec: 4, esSolicitud: 0, descripcionSolicitud: null, estadoSolicitud: null, tipoSolicitud: null,
    stockSolicitud: 0, enCamino: false, tiposSolicitud: null, updatedAt: '2026-09-16T07:02:00', modelo: '14',
    comentarioAsignacion: null, observacionTelefono: null, urgente: false, esChasis: false, porCerrar: false,
    tieneAsignaciones: false, nombreTecnicoAsigna: 'Técnico F', telefonoUpdatedAt: '2026-09-16T07:02:00', cliente: 'AMAZON',
    entregadoAt: null, entregadoPorNombre: null, entregadoPor: null, glassAbierta: false, glassEntregadoAt: null,
    glassEntregadoPorNombre: null, glassEntregadoPor: null, glassTecnicoNombre: null, normalAbierta: false, normalTecnicoNombre: null,
    ...parcial,
  }
}

export function tecnico(parcial: Partial<Tecnico> = {}): Tecnico {
  return { idTec: 4, nombre: 'Técnico A', activo: true, esEstadistica: true, esGlass: false, ...parcial }
}

/** Fila de reparación normal (A…) con la glass del IMEI descrita por sus derivados (calco de EntregaGlassTest.normal). */
export function normal(glassAbierta: boolean, entregadoAt: string | null): ReparacionResumen {
  return resumen({
    idRep: 'A20260828_1', glassAbierta, glassEntregadoAt: entregadoAt,
    glassEntregadoPorNombre: entregadoAt ? 'Técnico J' : null, glassEntregadoPor: entregadoAt ? 7 : null,
    glassTecnicoNombre: glassAbierta ? 'Técnico H' : null,
  })
}

/** Fila de glass (AG…) con su propia entrega (calco de EntregaGlassTest.glass). */
export function glass(entregadoAt: string | null): ReparacionResumen {
  return resumen({ idRep: 'AG20260828_3', entregadoAt, entregadoPorNombre: entregadoAt ? 'Técnico J' : null, entregadoPor: entregadoAt ? 7 : null })
}
```

`src/modules/taller/lib/piezas.test.ts` (port de `PiezasTest`):

```ts
import { describe, expect, it } from 'vitest'
import { categoriaPieza } from './piezas'

describe('categoriaPieza (calco de Piezas.categoria)', () => {
  it('deriva la categoría del prefijo del SKU', () => {
    expect(categoriaPieza('gi12negra')).toBe('Glass')
    expect(categoriaPieza('lcdi12negraic')).toBe('Pantalla')
    expect(categoriaPieza('mci12negra')).toBe('Marco')
    expect(categoriaPieza('bati12')).toBe('Batería')
    expect(categoriaPieza('cami12')).toBe('Cámara')
    expect(categoriaPieza('chai12negro')).toBe('Chasis')
    expect(categoriaPieza('otroi8')).toBe('Otros')
  })
  it('vacía si nulo o desconocido', () => {
    expect(categoriaPieza(null)).toBe('')
    expect(categoriaPieza('xyz123')).toBe('')
  })
})
```

`src/modules/taller/lib/modelos.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { MODELOS_ORDENADOS, traducirModelo } from './modelos'

describe('modelos (calco de MODELOS_ORDENADOS y traducirModelo)', () => {
  it('traduce numéricos con variante, especiales y aire', () => {
    expect(traducirModelo('12promax')).toBe('iPhone 12 Pro Max')
    expect(traducirModelo('14plus')).toBe('iPhone 14 Plus')
    expect(traducirModelo('13mini')).toBe('iPhone 13 Mini')
    expect(traducirModelo('16e')).toBe('iPhone 16e')
    expect(traducirModelo('15')).toBe('iPhone 15')
    expect(traducirModelo('se2020')).toBe('iPhone SE 2020')
    expect(traducirModelo('xsmax')).toBe('iPhone XS Max')
    expect(traducirModelo('6splus')).toBe('iPhone 6S Plus')
    expect(traducirModelo('air')).toBe('iPhone Air')
    expect(traducirModelo('')).toBe('')
    expect(traducirModelo(null)).toBe('')
  })
  it('el catálogo va en orden de tienda y empieza y acaba como el JavaFX', () => {
    expect(MODELOS_ORDENADOS[0]).toBe('6s')
    expect(MODELOS_ORDENADOS.at(-1)).toBe('17promax')
    expect(MODELOS_ORDENADOS).toHaveLength(39)
    expect(new Set(MODELOS_ORDENADOS).size).toBe(MODELOS_ORDENADOS.length)
  })
})
```

`src/modules/taller/lib/entregaGlass.test.ts` (port de `EntregaGlassTest`; `UTC_0842` = 10:42 en Madrid):

```ts
import { describe, expect, it } from 'vitest'
import { glass, normal, resumen } from '../test/fabrica'
import {
  etiquetaGlassPendiente, etiquetaRepAbierta, mostrarMarcarLlegada, ocultarAnadirGlass, opcionDeshacerLlegada, opcionMenuEntrega,
  subEtiquetaHistorial, textoBadgeEntrega, textoCsvEntrega, tooltipEntrega, tooltipGlassPendiente, tooltipRepAbierta,
} from './entregaGlass'

const UTC_0842 = '2026-08-28T08:42:00'
const HOY = '2026-08-28'
const MANANA = '2026-08-29'

describe('entregaGlass (calco de EntregaGlass, spec 2026-08-28)', () => {
  it('badge arriba dice a quién, sin hora y sin depender del día', () => {
    expect(textoBadgeEntrega(normal(true, UTC_0842), HOY)).toBe('→ Técnico H')
    expect(textoBadgeEntrega(normal(true, UTC_0842), MANANA)).toBe('→ Técnico H')
    expect(textoBadgeEntrega(normal(true, UTC_0842), null)).toBe('→ Técnico H')
  })
  it('badge de glass dice Llegó con hora hoy y con fecha otro día', () => {
    expect(textoBadgeEntrega(glass(UTC_0842), HOY)).toBe('Llegó 10:42')
    expect(textoBadgeEntrega(glass(UTC_0842), MANANA)).toBe('Llegó 28/08')
    expect(textoBadgeEntrega(glass(UTC_0842), null)).toBe('Llegó 28/08')
  })
  it('sin entrega, en pulidos o sin fila no hay badge ni tooltip', () => {
    expect(textoBadgeEntrega(normal(true, null), HOY)).toBeNull()
    expect(textoBadgeEntrega(glass(null), HOY)).toBeNull()
    expect(textoBadgeEntrega(resumen({ idRep: 'AP20260828_1', entregadoAt: UTC_0842 }), HOY)).toBeNull()
    expect(textoBadgeEntrega(null, HOY)).toBeNull()
    expect(tooltipEntrega(normal(true, null))).toBeNull()
    expect(tooltipEntrega(null)).toBeNull()
  })
  it('tooltips', () => {
    expect(tooltipEntrega(normal(true, UTC_0842))).toBe('Entregado a Técnico H por Técnico J, 28/08 10:42')
    expect(tooltipEntrega(glass(UTC_0842))).toBe('Bajado por Técnico J, 28/08 10:42')
  })
  it('opción de menú de entrega', () => {
    expect(opcionMenuEntrega(normal(true, null), false, 7)).toBe('Entregar a Técnico H')
    expect(opcionMenuEntrega(normal(true, UTC_0842), false, 7)).toBe('Deshacer entrega')
    expect(opcionMenuEntrega(normal(true, UTC_0842), false, 9)).toBeNull()      // la firmó otro
    expect(opcionMenuEntrega(normal(true, UTC_0842), false, null)).toBeNull()
    expect(opcionMenuEntrega(normal(true, null), false, 9)).toBe('Entregar a Técnico H')  // entregar no exige firma
    expect(opcionMenuEntrega(normal(false, null), false, 7)).toBeNull()
    expect(opcionMenuEntrega(normal(true, null), true, 7)).toBeNull()
    expect(opcionMenuEntrega(glass(null), false, 7)).toBeNull()
    expect(opcionMenuEntrega(null, false, 7)).toBeNull()
  })
  it('nombre vacío cae en "glass"', () => {
    const sinTecnico = { ...normal(true, null), glassTecnicoNombre: null }
    expect(opcionMenuEntrega(sinTecnico, false, 7)).toBe('Entregar a glass')
    const conEntrega = { ...normal(true, UTC_0842), glassTecnicoNombre: '', glassEntregadoPorNombre: null }
    expect(tooltipEntrega(conEntrega)).toBe('Entregado a glass por glass, 28/08 10:42')
    expect(textoBadgeEntrega(conEntrega, HOY)).toBe('→ glass')
  })
  it('CSV: fecha completa o vacío', () => {
    expect(textoCsvEntrega(normal(true, UTC_0842))).toBe('28/08/2026 10:42')
    expect(textoCsvEntrega(glass(UTC_0842))).toBe('28/08/2026 10:42')
    expect(textoCsvEntrega(normal(true, null))).toBe('')
    expect(textoCsvEntrega(null)).toBe('')
    expect(textoCsvEntrega(resumen({ idRep: 'AP20260828_1', entregadoAt: UTC_0842, glassEntregadoAt: UTC_0842 }))).toBe('')
  })
  it('"Añadir glass" se oculta solo con normal abierta y sin entrega; "Marcar que llegó" solo en la pestaña Glass y bloqueada', () => {
    const bloqueada = { ...glass(null), normalAbierta: true }
    expect(ocultarAnadirGlass(bloqueada)).toBe(true)
    expect(ocultarAnadirGlass({ ...glass(UTC_0842), normalAbierta: true })).toBe(false)
    expect(ocultarAnadirGlass(glass(null))).toBe(false)
    expect(ocultarAnadirGlass({ ...normal(true, null), normalAbierta: true })).toBe(false)
    expect(ocultarAnadirGlass(null)).toBe(false)
    expect(mostrarMarcarLlegada(bloqueada, true)).toBe(true)
    expect(mostrarMarcarLlegada(bloqueada, false)).toBe(false)
    expect(mostrarMarcarLlegada({ ...glass(UTC_0842), normalAbierta: true }, true)).toBe(false)
    expect(mostrarMarcarLlegada(glass(null), true)).toBe(false)
    expect(mostrarMarcarLlegada(null, true)).toBe(false)
  })
  it('sub-etiqueta del historial solo en glass con entrega', () => {
    expect(subEtiquetaHistorial(resumen({ idRep: 'G20260828_64', entregadoAt: UTC_0842, entregadoPorNombre: 'Técnico J' }))).toBe('Llegó 28/08 10:42')
    expect(subEtiquetaHistorial(glass(UTC_0842))).toBe('Llegó 28/08 10:42')
    expect(subEtiquetaHistorial(glass(null))).toBeNull()
    expect(subEtiquetaHistorial(normal(true, UTC_0842))).toBeNull()
    expect(subEtiquetaHistorial(null)).toBeNull()
  })
  it('píldoras "Glass: X" y "Rep: X" con sus tooltips', () => {
    expect(etiquetaGlassPendiente(normal(true, null))).toBe('Glass: Técnico H')
    expect(etiquetaGlassPendiente(normal(true, UTC_0842))).toBeNull()
    expect(etiquetaGlassPendiente(normal(false, null))).toBeNull()
    expect(etiquetaGlassPendiente(glass(null))).toBeNull()
    expect(etiquetaGlassPendiente(null)).toBeNull()
    expect(tooltipGlassPendiente(normal(true, null))).toBe('Glass abierta de Técnico H — entrega sin registrar')
    expect(tooltipGlassPendiente({ ...normal(true, null), glassTecnicoNombre: null })).toBe('Glass abierta de glass — entrega sin registrar')
    expect(tooltipGlassPendiente(normal(true, UTC_0842))).toBeNull()
    const ag = { ...glass(null), normalAbierta: true, normalTecnicoNombre: 'Técnico J' }
    expect(etiquetaRepAbierta(ag)).toBe('Rep: Técnico J')
    expect(tooltipRepAbierta(ag)).toBe('Reparación abierta de Técnico J')
    expect(etiquetaRepAbierta({ ...glass(UTC_0842), normalAbierta: true, normalTecnicoNombre: 'Técnico J' })).toBe('Rep: Técnico J')
    expect(etiquetaRepAbierta({ ...glass(null), normalAbierta: true, normalTecnicoNombre: null })).toBe('Rep: técnico')
    expect(etiquetaRepAbierta(glass(null))).toBeNull()
    expect(etiquetaRepAbierta(normal(true, null))).toBeNull()
    expect(tooltipRepAbierta(glass(null))).toBeNull()
  })
  it('deshacer llegada solo para el firmante en la pestaña Glass', () => {
    const ag = glass(UTC_0842)
    expect(opcionDeshacerLlegada(ag, true, 7)).toBe('Deshacer llegada')
    expect(opcionDeshacerLlegada(ag, true, 9)).toBeNull()
    expect(opcionDeshacerLlegada(ag, false, 7)).toBeNull()
    expect(opcionDeshacerLlegada(glass(null), true, 7)).toBeNull()
    expect(opcionDeshacerLlegada(normal(true, UTC_0842), true, 7)).toBeNull()
    expect(opcionDeshacerLlegada(null, true, 7)).toBeNull()
  })
})
```

```bash
npm test -- src/modules/taller
```
Expected: fallan por módulos inexistentes.

- [ ] **Step 2: Implementar**

`src/modules/taller/lib/piezas.ts`:

```ts
/** Calco de Piezas: categoría legible a partir del prefijo del SKU (prefijos largos antes para no confundir cha/cam con g). */
const PREFIJOS = ['otro', 'cha', 'cam', 'bat', 'lcd', 'mc', 'g'] as const
const ETIQUETAS: Record<(typeof PREFIJOS)[number], string> = {
  bat: 'Batería', cha: 'Chasis', g: 'Glass', cam: 'Cámara', lcd: 'Pantalla', mc: 'Marco', otro: 'Otros',
}

export function categoriaPieza(sku: string | null | undefined): string {
  if (!sku) return ''
  const s = sku.toLowerCase()
  for (const p of PREFIJOS) if (s.startsWith(p)) return ETIQUETAS[p]
  return ''
}
```

`src/modules/taller/lib/modelos.ts`:

```ts
/** Catálogo de modelos en orden de tienda Apple (calco de FormularioReparacionController.MODELOS_ORDENADOS) y su
 *  traducción código → nombre (traducirModelo). Duplicado en TypeScript hasta que suba al servidor (sub-proyecto 3). */
export const MODELOS_ORDENADOS: readonly string[] = [
  '6s', '6splus', '7', '7plus', '8', '8plus', 'se2020',
  'x', 'xr', 'xs', 'xsmax',
  '11', '11pro', '11promax',
  '12', '12mini', '12pro', '12promax',
  '13', '13mini', '13pro', '13promax',
  '14', '14plus', '14pro', '14promax',
  '15', '15plus', '15pro', '15promax',
  '16', '16e', '16plus', '16pro', '16promax',
  '17', 'air', '17pro', '17promax',
]

const ESPECIALES: Record<string, string> = {
  se2020: 'iPhone SE 2020', x: 'iPhone X', xr: 'iPhone XR', xs: 'iPhone XS', xsmax: 'iPhone XS Max',
  '6s': 'iPhone 6S', '6splus': 'iPhone 6S Plus', air: 'iPhone Air',
}
const SUFIJOS: Record<string, string> = { plus: ' Plus', mini: ' Mini', pro: ' Pro', promax: ' Pro Max', e: 'e' }

export function traducirModelo(codigo: string | null | undefined): string {
  if (!codigo) return ''
  const especial = ESPECIALES[codigo]
  if (especial) return especial
  const num = codigo.replace(/[^0-9]/g, '')
  const variante = codigo.replace(/[0-9]/g, '')
  return `iPhone ${num}${SUFIJOS[variante] ?? ''}`
}
```

`src/modules/taller/lib/entregaGlass.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { fechaLocal, formatear } from '@/shared/lib/fechas'
import { tipoDe } from '@/shared/lib/tipoTrabajo'

/** Calco de EntregaGlass (spec entrega-glass 2026-08-28): textos de badge, tooltip, menú, píldoras y CSV de la entrega
 *  del teléfono al técnico de glass. Fila A… (arriba): usa los derivados glass*; fila AG… (glass): entregadoAt/Por. */

type Rep = ReparacionResumen | null | undefined

const nombre = (n: string | null | undefined) => (n && n.trim() !== '' ? n : 'glass')
const nombreRep = (n: string | null | undefined) => (n && n.trim() !== '' ? n : 'técnico')

/** Badge: fila A → "→ <técnico de glass>" (sin hora: no cabía en 100 px); fila AG → "Llegó HH:mm" hoy o "Llegó dd/MM" otro día. */
export function textoBadgeEntrega(rep: Rep, hoy: string | null): string | null {
  if (!rep) return null
  switch (tipoDe(rep.idRep)) {
    case 'GLASS': {
      if (!rep.entregadoAt) return null
      const esHoy = hoy !== null && hoy === fechaLocal(rep.entregadoAt)
      return `Llegó ${formatear(rep.entregadoAt, esHoy ? 'HH:mm' : 'dd/MM')}`
    }
    case 'REPARACION':
      return rep.glassEntregadoAt ? `→ ${nombre(rep.glassTecnicoNombre)}` : null
    default:
      return null
  }
}

export function tooltipEntrega(rep: Rep): string | null {
  if (!rep) return null
  switch (tipoDe(rep.idRep)) {
    case 'GLASS':
      return rep.entregadoAt ? `Bajado por ${nombre(rep.entregadoPorNombre)}, ${formatear(rep.entregadoAt, 'dd/MM HH:mm')}` : null
    case 'REPARACION':
      return rep.glassEntregadoAt
        ? `Entregado a ${nombre(rep.glassTecnicoNombre)} por ${nombre(rep.glassEntregadoPorNombre)}, ${formatear(rep.glassEntregadoAt, 'dd/MM HH:mm')}`
        : null
    default:
      return null
  }
}

/** Pestaña Reparaciones, fila A con glass abierta: "Entregar a X" o "Deshacer entrega" (solo el firmante). */
export function opcionMenuEntrega(rep: Rep, pestanaGlass: boolean, idTecSesion: number | null): string | null {
  if (!rep || pestanaGlass || tipoDe(rep.idRep) !== 'REPARACION' || !rep.glassAbierta) return null
  if (!rep.glassEntregadoAt) return `Entregar a ${nombre(rep.glassTecnicoNombre)}`
  return idTecSesion !== null && idTecSesion === rep.glassEntregadoPor ? 'Deshacer entrega' : null
}

/** Pestaña Glass, fila AG con entrega firmada por el propio técnico. */
export function opcionDeshacerLlegada(rep: Rep, pestanaGlass: boolean, idTecSesion: number | null): string | null {
  if (!rep || !pestanaGlass || tipoDe(rep.idRep) !== 'GLASS' || !rep.entregadoAt) return null
  return idTecSesion !== null && idTecSesion === rep.entregadoPor ? 'Deshacer llegada' : null
}

/** Sin teléfono no hay glass: se oculta "Añadir glass" con reparación normal abierta y sin entrega. */
export function ocultarAnadirGlass(rep: Rep): boolean {
  if (!rep || tipoDe(rep.idRep) !== 'GLASS') return false
  return rep.normalAbierta && !rep.entregadoAt
}

export function mostrarMarcarLlegada(rep: Rep, pestanaGlass: boolean): boolean {
  return pestanaGlass && ocultarAnadirGlass(rep)
}

/** Columna "Entregado" del CSV (A: derivada; AG: real; pulido: vacío). */
export function textoCsvEntrega(rep: Rep): string {
  if (!rep) return ''
  const tipo = tipoDe(rep.idRep)
  if (tipo === 'PULIDO') return ''
  return formatear(tipo === 'GLASS' ? rep.entregadoAt : rep.glassEntregadoAt, 'dd/MM/yyyy HH:mm')
}

/** Bajo el reparador en Historial/IMEIs: solo glass con entrega, siempre con día y hora. */
export function subEtiquetaHistorial(rep: Rep): string | null {
  if (!rep || !rep.entregadoAt || tipoDe(rep.idRep) !== 'GLASS') return null
  return `Llegó ${formatear(rep.entregadoAt, 'dd/MM HH:mm')}`
}

/** Píldora bajo el IMEI de la reparación mientras su glass no tenga entrega. */
export function etiquetaGlassPendiente(rep: Rep): string | null {
  if (!rep || tipoDe(rep.idRep) !== 'REPARACION' || !rep.glassAbierta || rep.glassEntregadoAt) return null
  return `Glass: ${nombre(rep.glassTecnicoNombre)}`
}

export function tooltipGlassPendiente(rep: Rep): string | null {
  return etiquetaGlassPendiente(rep) === null ? null : `Glass abierta de ${nombre(rep!.glassTecnicoNombre)} — entrega sin registrar`
}

/** Píldora bajo el IMEI de la glass mientras la reparación normal siga abierta (se mantiene tras "Llegó"). */
export function etiquetaRepAbierta(rep: Rep): string | null {
  if (!rep || tipoDe(rep.idRep) !== 'GLASS' || !rep.normalAbierta) return null
  return `Rep: ${nombreRep(rep.normalTecnicoNombre)}`
}

export function tooltipRepAbierta(rep: Rep): string | null {
  return etiquetaRepAbierta(rep) === null ? null : `Reparación abierta de ${nombreRep(rep!.normalTecnicoNombre)}`
}
```

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/modules/taller
git commit -m "feat(web): logica pura del taller: piezas, catalogo de modelos y entregaGlass (tests portados de JUnit)"
```

---

### Task 12: Web — lógica pura del taller (2): agrupación por IMEI, estado de pendientes y filtros

**Files:**
- Create: `src/modules/taller/lib/grupoImei.ts` (+test), `estadoPendiente.ts` (+test), `filtros.ts` (+test)

**Interfaces (produces):**
- `grupoImei.ts`: `type GrupoImei = { imei; modelo: string; observacion: string | null; cliente: string | null; fechaMasAntigua: string | null; fechaMasReciente: string | null; trabajos: ReparacionResumen[]; incAbiertas: number; countRep; countGlass; countPul; telefonoUpdatedAt: string | null }`; `agruparPorImei(trabajos): GrupoImei[]` (orden de primera aparición); `resumenTipos(g): string`; `ordenarPorActividad(grupos): GrupoImei[]`.
- `estadoPendiente.ts`: `type BadgeEstado = { texto: string; clases: string; tooltip?: string; sub?: string }`; `badgesEstado(rep, hoy): BadgeEstado[]`.
- `filtros.ts`: `SIN_CLIENTE = '(Sin cliente)'`; `type TipoPendiente = 'solicitud' | 'incidencia' | 'asignacion'`; `pasaTipo(rep, marcados)` (las tres casillas se evalúan por separado, como en el JavaFX: una fila que es incidencia **y** solicitud aparece bajo ambas); `type EstadoIncidencia = 'abiertas' | 'cerradas' | 'sin'`; `estadoIncidencia(rep)`; `pasaIncidencias(rep, marcados)`; `pasaFechas(rep, desde, hasta)`; `pasaImeis(imei, imeis)`; `pasaCliente(cliente, marcados)`; `pasaPieza(tipoComponente, marcadas)`; `pasaTecnico(idTec, marcados)`; `ordenarPendientes(lista)`; `etiquetaContador(n, singular, plural, tope?)`; `sufijoToggle(n)`; `textoBadgeLateral(total)`; `etiquetaMultiseleccion` no (la pone MultiSelect).

- [ ] **Step 1: Tests (fallan)**

`src/modules/taller/lib/grupoImei.test.ts` (port de `GrupoImeiTest` + orden y agregados):

```ts
import { describe, expect, it } from 'vitest'
import { resumen } from '../test/fabrica'
import { agruparPorImei, ordenarPorActividad, resumenTipos } from './grupoImei'

const rr = (idRep: string, extra = {}) => resumen({ idRep, imei: '111111111111111', modelo: null, cliente: null, ...extra })

describe('grupoImei (calco de GrupoImei)', () => {
  it('cuenta por tipo según el prefijo', () => {
    const [g] = agruparPorImei([rr('R20260630_1'), rr('G20260630_1'), rr('G20260630_2'), rr('P20260630_1')])
    expect(g.countRep).toBe(1)
    expect(g.countGlass).toBe(2)
    expect(g.countPul).toBe(1)
  })
  it('resumen omite los tipos a cero y va en orden Rep · Glass · Pul', () => {
    expect(resumenTipos(agruparPorImei([rr('R20260630_1'), rr('R20260630_2')])[0])).toBe('2 Rep')
    expect(resumenTipos(agruparPorImei([rr('P20260630_1'), rr('R20260630_1'), rr('G20260630_1')])[0])).toBe('1 Rep · 1 Glass · 1 Pul')
    expect(resumenTipos(agruparPorImei([rr('G20260630_1')])[0])).toBe('1 Glass')
  })
  it('modelo, observación y cliente son el primer valor no vacío; fechas mínima de asignación y máxima de fin; incidencias abiertas', () => {
    const [g] = agruparPorImei([
      rr('R1', { modelo: '', fechaAsig: '2026-09-11T07:00:00', fechaFin: '2026-09-11T08:00:00', esIncidencia: true, esResuelto: false, telefonoUpdatedAt: '2026-09-01T00:00:00' }),
      rr('R2', { modelo: '13mini', observacionTelefono: 'rayado', cliente: 'WEB', fechaAsig: '2026-09-10T07:00:00', fechaFin: '2026-09-12T08:00:00', esIncidencia: true, esResuelto: true }),
      rr('P1', { fechaAsig: '2026-09-12T07:00:00', fechaFin: null }),
    ])
    expect(g.imei).toBe('111111111111111')
    expect(g.modelo).toBe('13mini')
    expect(g.observacion).toBe('rayado')
    expect(g.cliente).toBe('WEB')
    expect(g.fechaMasAntigua).toBe('2026-09-10T07:00:00')
    expect(g.fechaMasReciente).toBe('2026-09-12T08:00:00')
    expect(g.incAbiertas).toBe(1)
    expect(g.telefonoUpdatedAt).toBe('2026-09-01T00:00:00')
    expect(g.trabajos).toHaveLength(3)
  })
  it('un grupo por IMEI en orden de aparición, y ordenarPorActividad pone la más reciente arriba y las sin fecha al final', () => {
    const grupos = agruparPorImei([
      resumen({ idRep: 'R1', imei: '111111111111111', fechaFin: '2026-09-01T00:00:00' }),
      resumen({ idRep: 'R2', imei: '222222222222222', fechaFin: '2026-09-05T00:00:00' }),
      resumen({ idRep: 'R3', imei: '111111111111111', fechaFin: '2026-09-03T00:00:00' }),
      resumen({ idRep: 'P9', imei: '333333333333333', fechaFin: null }),
    ])
    expect(grupos.map((g) => g.imei)).toEqual(['111111111111111', '222222222222222', '333333333333333'])
    expect(ordenarPorActividad(grupos).map((g) => g.imei)).toEqual(['222222222222222', '111111111111111', '333333333333333'])
  })
})
```

`src/modules/taller/lib/estadoPendiente.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { glass, normal, resumen } from '../test/fabrica'
import { badgesEstado } from './estadoPendiente'

const HOY = '2026-08-28'
const textos = (r: Parameters<typeof badgesEstado>[0]) => badgesEstado(r, HOY).map((b) => b.texto)

describe('badgesEstado (calco de la celda Estado de PendientesTecnicoController)', () => {
  it('Normal sola por defecto; Urgente sin Normal; Por cerrar encima', () => {
    expect(textos(resumen())).toEqual(['Normal'])
    expect(textos(resumen({ urgente: true }))).toEqual(['Urgente'])
    expect(textos(resumen({ porCerrar: true }))).toEqual(['Por cerrar', 'Normal'])
    expect(textos(resumen({ urgente: true, porCerrar: true }))).toEqual(['Urgente', 'Por cerrar'])
  })
  it('entrega: "→ X" en reparaciones entregadas y "Llegó" en glass, entre Por cerrar y el estado', () => {
    expect(textos({ ...normal(true, '2026-08-28T08:42:00'), porCerrar: true })).toEqual(['Por cerrar', '→ Técnico H', 'Normal'])
    const b = badgesEstado(glass('2026-08-28T08:42:00'), HOY)
    expect(b.map((x) => x.texto)).toEqual(['Llegó 10:42', 'Normal'])
    expect(b[0].tooltip).toBe('Bajado por Técnico J, 28/08 10:42')
    expect(b[0].clases).toContain('bg-entrega-bg')
  })
  it('Incidencia manda sobre solicitud; Recibido / En camino / Solicitud con sub-etiqueta y tooltip', () => {
    expect(textos(resumen({ esIncidencia: true, esSolicitud: 1 }))).toEqual(['Incidencia'])
    const recibido = badgesEstado(resumen({ esSolicitud: 1, estadoSolicitud: 'GESTIONADA', stockSolicitud: 2, tiposSolicitud: 'Batería' }), HOY)
    expect(recibido.map((x) => x.texto)).toEqual(['Recibido'])
    expect(recibido[0].sub).toBe('Batería')
    expect(recibido[0].tooltip).toBe('Batería')
    expect(textos(resumen({ esSolicitud: 1, enCamino: true }))).toEqual(['En camino'])
    const varias = badgesEstado(resumen({ esSolicitud: 2, tiposSolicitud: 'Batería, Pantalla' }), HOY)
    expect(varias.map((x) => x.texto)).toEqual(['Solicitud'])
    expect(varias[0].sub).toBe('2 piezas')
    expect(varias[0].tooltip).toBe('Batería, Pantalla')
    expect(varias[0].clases).toContain('bg-fila-solicitud-bg')
  })
})
```

`src/modules/taller/lib/filtros.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { resumen } from '../test/fabrica'
import {
  SIN_CLIENTE, estadoIncidencia, etiquetaContador, ordenarPendientes, pasaCliente, pasaFechas, pasaImeis, pasaIncidencias,
  pasaPieza, pasaTecnico, pasaTipo, sufijoToggle, textoBadgeLateral,
} from './filtros'

describe('filtros del taller', () => {
  it('filtro Tipo: las tres casillas se evalúan por separado (calco de las checkboxes del JavaFX)', () => {
    expect(pasaTipo(resumen(), new Set())).toBe(true)
    expect(pasaTipo(resumen(), new Set(['asignacion']))).toBe(true)
    expect(pasaTipo(resumen(), new Set(['solicitud']))).toBe(false)
    expect(pasaTipo(resumen({ esSolicitud: 1 }), new Set(['solicitud', 'asignacion']))).toBe(true)
    expect(pasaTipo(resumen({ esIncidencia: true }), new Set(['incidencia']))).toBe(true)
    // una solicitud con incidencia sale bajo cualquiera de las dos, y nunca bajo "Asignaciones"
    const ambas = resumen({ esSolicitud: 1, esIncidencia: true })
    expect(pasaTipo(ambas, new Set(['solicitud']))).toBe(true)
    expect(pasaTipo(ambas, new Set(['incidencia']))).toBe(true)
    expect(pasaTipo(ambas, new Set(['asignacion']))).toBe(false)
  })
  it('estado de incidencia y filtro Incidencias', () => {
    expect(estadoIncidencia(resumen({ esIncidencia: true }))).toBe('abiertas')
    expect(estadoIncidencia(resumen({ esIncidencia: true, esResuelto: true }))).toBe('cerradas')
    expect(estadoIncidencia(resumen())).toBe('sin')
    expect(pasaIncidencias(resumen(), new Set())).toBe(true)
    expect(pasaIncidencias(resumen(), new Set(['abiertas']))).toBe(false)
    expect(pasaIncidencias(resumen({ esIncidencia: true }), new Set(['abiertas', 'sin']))).toBe(true)
  })
  it('fechas por fecha de fin en Madrid, extremos incluidos; sin fin y con rango → fuera', () => {
    const r = resumen({ fechaFin: '2026-08-28T22:30:00' })   // 29/08 00:30 en Madrid
    expect(pasaFechas(r, '', '')).toBe(true)
    expect(pasaFechas(r, '2026-08-29', '')).toBe(true)
    expect(pasaFechas(r, '2026-08-30', '')).toBe(false)
    expect(pasaFechas(r, '', '2026-08-28')).toBe(false)
    expect(pasaFechas(r, '2026-08-29', '2026-08-29')).toBe(true)
    expect(pasaFechas(resumen({ fechaFin: null }), '2026-08-01', '')).toBe(false)
    expect(pasaFechas(resumen({ fechaFin: null }), '', '')).toBe(true)
  })
  it('IMEIs, cliente con "(Sin cliente)", pieza por categoría y técnico', () => {
    expect(pasaImeis('355400000000111', new Set())).toBe(true)
    expect(pasaImeis('355400000000111', new Set(['1']))).toBe(false)
    expect(pasaCliente(null, new Set([SIN_CLIENTE]))).toBe(true)
    expect(pasaCliente('WEB', new Set([SIN_CLIENTE]))).toBe(false)
    expect(pasaCliente('WEB', new Set(['WEB']))).toBe(true)
    expect(pasaPieza('bati13', new Set(['Batería']))).toBe(true)
    expect(pasaPieza('lcdi13', new Set(['Batería']))).toBe(false)
    expect(pasaPieza(null, new Set())).toBe(true)
    expect(pasaTecnico(4, new Set([4, 5]))).toBe(true)
    expect(pasaTecnico(9, new Set([4]))).toBe(false)
  })
  it('orden de pendientes: urgentes, con cliente, resto; estable', () => {
    const lista = [
      resumen({ idRep: 'A1', cliente: null }), resumen({ idRep: 'A2', urgente: true, cliente: null }),
      resumen({ idRep: 'A3', cliente: 'WEB' }), resumen({ idRep: 'A4', cliente: null }), resumen({ idRep: 'A5', urgente: true, cliente: 'WEB' }),
    ]
    expect(ordenarPendientes(lista).map((r) => r.idRep)).toEqual(['A2', 'A5', 'A3', 'A1', 'A4'])
  })
  it('contadores y sufijos', () => {
    expect(etiquetaContador(1, 'pendiente', 'pendientes', 999)).toBe('1 pendiente')
    expect(etiquetaContador(10, 'pendiente', 'pendientes', 999)).toBe('10 pendientes')
    expect(etiquetaContador(1200, 'pendiente', 'pendientes', 999)).toBe('999+ pendientes')
    expect(etiquetaContador(1314, 'reparación', 'reparaciones')).toBe('1314 reparaciones')
    expect(sufijoToggle(0)).toBe('(0)')
    expect(sufijoToggle(150)).toBe('(99+)')
    expect(textoBadgeLateral(0)).toBeNull()
    expect(textoBadgeLateral(10)).toBe('10')
    expect(textoBadgeLateral(100)).toBe('99+')
  })
})
```

```bash
npm test -- src/modules/taller/lib
```
Expected: fallan los tres nuevos.

- [ ] **Step 2: Implementar**

`src/modules/taller/lib/grupoImei.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { tipoDe } from '@/shared/lib/tipoTrabajo'

/** Calco de GrupoImei: un grupo por IMEI con los trabajos de los tres tipos (R/G/P). */
export type GrupoImei = {
  imei: string
  modelo: string
  observacion: string | null
  cliente: string | null
  fechaMasAntigua: string | null
  fechaMasReciente: string | null
  trabajos: ReparacionResumen[]
  incAbiertas: number
  countRep: number
  countGlass: number
  countPul: number
  telefonoUpdatedAt: string | null
}

const primero = (valores: (string | null | undefined)[]) => valores.find((v) => v && v !== '') ?? null

function construir(imei: string, trabajos: ReparacionResumen[]): GrupoImei {
  let countRep = 0, countGlass = 0, countPul = 0
  for (const t of trabajos) {
    const tipo = tipoDe(t.idRep)
    if (tipo === 'GLASS') countGlass++
    else if (tipo === 'PULIDO') countPul++
    else countRep++
  }
  const asignaciones = trabajos.map((t) => t.fechaAsig).filter((f): f is string => !!f)
  const fines = trabajos.map((t) => t.fechaFin).filter((f): f is string => !!f)
  return {
    imei,
    modelo: primero(trabajos.map((t) => t.modelo)) ?? '',
    observacion: primero(trabajos.map((t) => t.observacionTelefono)),
    cliente: primero(trabajos.map((t) => t.cliente)),
    fechaMasAntigua: asignaciones.length ? asignaciones.reduce((a, b) => (a < b ? a : b)) : null,
    fechaMasReciente: fines.length ? fines.reduce((a, b) => (a > b ? a : b)) : null,
    trabajos,
    incAbiertas: trabajos.filter((t) => t.esIncidencia && !t.esResuelto).length,
    countRep,
    countGlass,
    countPul,
    telefonoUpdatedAt: trabajos[0]?.telefonoUpdatedAt ?? null,
  }
}

/** Agrupa en orden de primera aparición (LinkedHashMap del JavaFX); ordenar después con ordenarPorActividad. */
export function agruparPorImei(trabajos: ReparacionResumen[]): GrupoImei[] {
  const porImei = new Map<string, ReparacionResumen[]>()
  for (const t of trabajos) {
    const lista = porImei.get(t.imei)
    if (lista) lista.push(t)
    else porImei.set(t.imei, [t])
  }
  return [...porImei].map(([imei, lista]) => construir(imei, lista))
}

/** "2 Rep · 1 Glass · 1 Pul", omitiendo los tipos a cero. */
export function resumenTipos(g: GrupoImei): string {
  const partes: string[] = []
  if (g.countRep > 0) partes.push(`${g.countRep} Rep`)
  if (g.countGlass > 0) partes.push(`${g.countGlass} Glass`)
  if (g.countPul > 0) partes.push(`${g.countPul} Pul`)
  return partes.length ? partes.join(' · ') : '0'
}

/** Actividad más reciente arriba, cuente el tipo que cuente; sin fecha al final. */
export function ordenarPorActividad(grupos: GrupoImei[]): GrupoImei[] {
  return [...grupos].sort((a, b) => {
    if (a.fechaMasReciente === b.fechaMasReciente) return 0
    if (a.fechaMasReciente === null) return 1
    if (b.fechaMasReciente === null) return -1
    return a.fechaMasReciente > b.fechaMasReciente ? -1 : 1
  })
}
```

`src/modules/taller/lib/estadoPendiente.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { textoBadgeEntrega, tooltipEntrega } from './entregaGlass'

export type BadgeEstado = { texto: string; clases: string; tooltip?: string; sub?: string }

const URGENTE = 'bg-urgente-bg text-fila-urgente-brd'
const POR_CERRAR = 'bg-tipo-glass-bg text-tipo-glass-text'
const ENTREGA = 'bg-entrega-bg text-entrega-text'
const INCIDENCIA = 'bg-fila-incidencia-bg text-fila-incidencia-brd'
const RECIBIDO = 'bg-recibido-bg text-recibido-text'
const EN_CAMINO = 'bg-tipo-reparacion-bg text-tipo-reparacion-text'
const SOLICITUD = 'bg-fila-solicitud-bg text-fila-solicitud-brd'
const NORMAL = 'bg-badge-neutro-bg text-azul-gris'

/** Calco de la celda Estado de PendientesTecnicoController: badges apilados de arriba abajo. */
export function badgesEstado(rep: ReparacionResumen, hoy: string | null): BadgeEstado[] {
  const badges: BadgeEstado[] = []
  if (rep.urgente) badges.push({ texto: 'Urgente', clases: URGENTE })
  if (rep.porCerrar) badges.push({ texto: 'Por cerrar', clases: POR_CERRAR })
  const entrega = textoBadgeEntrega(rep, hoy)
  if (entrega) badges.push({ texto: entrega, clases: ENTREGA, tooltip: tooltipEntrega(rep) ?? undefined })
  if (rep.esIncidencia) {
    badges.push({ texto: 'Incidencia', clases: INCIDENCIA })
  } else if (rep.esSolicitud > 0) {
    const recibido = rep.estadoSolicitud === 'GESTIONADA' && rep.stockSolicitud > 0
    const badge: BadgeEstado = recibido
      ? { texto: 'Recibido', clases: RECIBIDO }
      : rep.enCamino
        ? { texto: 'En camino', clases: EN_CAMINO }
        : { texto: 'Solicitud', clases: SOLICITUD }
    if (rep.tiposSolicitud) {
      badge.sub = rep.esSolicitud > 1 ? `${rep.esSolicitud} piezas` : rep.tiposSolicitud
      badge.tooltip = rep.tiposSolicitud
    }
    badges.push(badge)
  } else if (!rep.urgente) {
    badges.push({ texto: 'Normal', clases: NORMAL })
  }
  return badges
}
```

`src/modules/taller/lib/filtros.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { fechaLocal } from '@/shared/lib/fechas'
import { categoriaPieza } from './piezas'

export const SIN_CLIENTE = '(Sin cliente)'

export type TipoPendiente = 'solicitud' | 'incidencia' | 'asignacion'
/** Calco de las tres casillas de PendientesTecnicoController: no son excluyentes. Una fila puede ser solicitud e
 *  incidencia a la vez y entonces la muestran las dos casillas; "Asignaciones" es la única exclusiva (ni una ni otra). */
export function pasaTipo(rep: ReparacionResumen, marcados: Set<TipoPendiente>): boolean {
  if (marcados.size === 0) return true
  if (marcados.has('solicitud') && rep.esSolicitud > 0) return true
  if (marcados.has('incidencia') && rep.esIncidencia) return true
  return marcados.has('asignacion') && rep.esSolicitud === 0 && !rep.esIncidencia
}

export type EstadoIncidencia = 'abiertas' | 'cerradas' | 'sin'
export function estadoIncidencia(rep: ReparacionResumen): EstadoIncidencia {
  if (!rep.esIncidencia) return 'sin'
  return rep.esResuelto ? 'cerradas' : 'abiertas'
}
export function pasaIncidencias(rep: ReparacionResumen, marcados: Set<EstadoIncidencia>): boolean {
  return marcados.size === 0 || marcados.has(estadoIncidencia(rep))
}

/** Por fecha de fin en Madrid, extremos incluidos ('' = sin límite); sin fecha de fin y con rango → fuera. */
export function pasaFechas(rep: ReparacionResumen, desde: string, hasta: string): boolean {
  if (desde === '' && hasta === '') return true
  const fin = fechaLocal(rep.fechaFin)
  if (fin === null) return false
  if (desde !== '' && fin < desde) return false
  if (hasta !== '' && fin > hasta) return false
  return true
}

export function pasaImeis(imei: string, imeis: Set<string>): boolean {
  return imeis.size === 0 || imeis.has(imei)
}

export function pasaCliente(cliente: string | null | undefined, marcados: Set<string>): boolean {
  if (marcados.size === 0) return true
  return !cliente || cliente === '' ? marcados.has(SIN_CLIENTE) : marcados.has(cliente)
}

export function pasaPieza(tipoComponente: string | null | undefined, marcadas: Set<string>): boolean {
  return marcadas.size === 0 || marcadas.has(categoriaPieza(tipoComponente))
}

export function pasaTecnico(idTec: number, marcados: Set<number>): boolean {
  return marcados.size === 0 || marcados.has(idTec)
}

/** Urgentes primero, después con cliente, después el resto; estable (orden del servidor dentro de cada grupo). */
export function ordenarPendientes(lista: ReparacionResumen[]): ReparacionResumen[] {
  const rango = (r: ReparacionResumen) => (r.urgente ? 0 : r.cliente ? 1 : 2)
  return [...lista].sort((a, b) => rango(a) - rango(b))
}

/** "N pendientes" / "1 pendiente" / "999+ pendientes" (tope opcional). */
export function etiquetaContador(n: number, singular: string, plural: string, tope?: number): string {
  const numero = tope !== undefined && n > tope ? `${tope}+` : String(n)
  return `${numero} ${n === 1 ? singular : plural}`
}

/** Sufijo de los toggles de Pendientes, siempre presente y con tope 99+. */
export function sufijoToggle(n: number): string {
  return `(${n > 99 ? '99+' : n})`
}

/** Badge de la columna lateral: oculto a cero, tope 99+. */
export function textoBadgeLateral(total: number): string | null {
  if (total <= 0) return null
  return total > 99 ? '99+' : String(total)
}
```

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/modules/taller/lib
git commit -m "feat(web): agrupacion por IMEI, badges de estado de pendientes y predicados de filtros del taller (con tests)"
```

---

### Task 13: Web — rutas del taller, columna lateral por rol con badge, estado compartido y `api.ts`

**Files:**
- Create: `src/modules/taller/api.ts` (+`api.test.tsx`), `src/modules/taller/estado.ts`, `src/modules/taller/rutas.tsx` (+`rutas.test.tsx`), `src/modules/taller/componentes/BadgePendientes.tsx`
- Modify: `src/app/router.tsx`, `src/app/shell/SubNav.tsx`, `src/app/shell/SubNav.test.tsx`, `src/app/shell/PendienteDeMigrar.tsx` (sin cambios funcionales)

**Interfaces (produces):**
- `api.ts`: `type TipoLista = 'REPARACION' | 'GLASS' | 'PULIDO'`; claves `claveHistorial(tipo)`, `claveAsignaciones(tipo)`, `CLAVE_CONTADORES`, `CLAVE_TECNICOS`, `CLAVE_TECNICOS_ACTIVOS`, `CLAVE_CLIENTES_ACTIVOS`; hooks `useHistorial(tipo)`, `useAsignaciones(tipo)`, `useContadoresPendientes()`, `useTecnicos(soloActivos?)`, `useClientesActivos()`; mutaciones `usePorCerrar()`, `useEntregaGlass()`, `useMarcarLlegada()`, `useDeshacerLlegada()`, `useBorrarAsignacion()`, `useBorrarIncidenciaActiva()`, `useCompletarPulidos()`, `useBorrarAsignacionPulido()`, `useBorrarReparacion()`, `useBorrarPulido()`, `useAnadirIncidencia()`, `useCancelarIncidencia()`, `useEditarObservacionTelefono()`, `useEditarClienteTelefono()`, `useEditarModeloTelefono()`; función `referenciadora(idRep): Promise<string | null>`.
- `estado.ts`: stores `filtroImeiPendientes`, `tipoPendientes` (por pestaña), `filtroImeiHistorial`, `filtrosHistorial` (por toggle: técnicos, piezas, desde, hasta, incidencias), `filtrosImeis` (imei, técnicos, clientes, desde, hasta, incidencias; `FILTROS_IMEIS_VACIOS`, tipo `FiltrosImeis`), `ultimoImeiVisto`; `reiniciarEstadoTaller()`.
- `rutas.tsx`: `<InicioReparaciones />`, `<RequiereTecnico />` (layout route), `enlacesReparaciones(sesion)`.
- `SubNav`: mapa de secciones por rol con `badge` opcional; `<BadgePendientes activo />`.

- [ ] **Step 1: Tests (fallan)**

`src/modules/taller/rutas.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { Route } from 'react-router'
import { describe, expect, it } from 'vitest'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { enlacesReparaciones, InicioReparaciones, RequiereTecnico } from './rutas'

const destinos = (
  <>
    <Route path="/reparaciones/pendientes" element={<p>PENDIENTES</p>} />
    <Route path="/reparaciones/historial" element={<p>HISTORIAL</p>} />
  </>
)

describe('rutas del taller', () => {
  it('/reparaciones entra en Pendientes para el técnico y en Historial para supertécnico y admin', () => {
    renderConProviders(<InicioReparaciones />, { sesion: SESION_TEC, ruta: '/reparaciones', rutas: destinos })
    expect(screen.getByText('PENDIENTES')).toBeInTheDocument()
    renderConProviders(<InicioReparaciones />, { sesion: SESION_SUPER, ruta: '/reparaciones', rutas: destinos })
    expect(screen.getByText('HISTORIAL')).toBeInTheDocument()
    renderConProviders(<InicioReparaciones />, { sesion: SESION_ADMIN, ruta: '/reparaciones', rutas: destinos })
    expect(screen.getAllByText('HISTORIAL')).toHaveLength(2)
  })
  it('RequiereTecnico deja pasar a quien tiene técnico y manda al admin a Historial', () => {
    renderConProviders(<RequiereTecnico />, {
      sesion: SESION_ADMIN,
      ruta: '/reparaciones/pendientes',
      rutas: <><Route path="/reparaciones/pendientes" element={<p>PROTEGIDO</p>} /><Route path="/reparaciones/historial" element={<p>HISTORIAL</p>} /></>,
    })
    expect(screen.getByText('HISTORIAL')).toBeInTheDocument()
  })
  it('enlaces de la columna por rol, en el orden del JavaFX', () => {
    expect(enlacesReparaciones(SESION_TEC).map((e) => e.label)).toEqual(['Pendientes', 'Historial', 'IMEIs'])
    expect(enlacesReparaciones(SESION_SUPER).map((e) => e.label)).toEqual(['Asignaciones', 'Pendientes', 'Historial', 'IMEIs'])
    expect(enlacesReparaciones(SESION_ADMIN).map((e) => e.label)).toEqual(['Asignaciones', 'Historial', 'IMEIs'])
    expect(enlacesReparaciones(SESION_TEC)[0].badge).toBe('pendientes')
  })
})
```

En `SubNav.test.tsx` añadir:

```tsx
  it('en Reparaciones pinta los enlaces del rol y el badge de Pendientes con tope 99+', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 90, glass: 10, pulidos: 5 })))
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' })
    const pendientes = screen.getByRole('link', { name: /Pendientes/ })
    expect(pendientes).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByText('99+')).toHaveClass('bg-superficie', 'text-azul-noche')
    expect(screen.getByRole('link', { name: 'IMEIs' })).toHaveAttribute('href', '/reparaciones/imeis')
    expect(screen.queryByRole('link', { name: 'Asignaciones' })).not.toBeInTheDocument()
  })
  it('el badge no se pinta a cero y va navy sobre el enlace inactivo', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 0 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    expect(screen.getByRole('link', { name: 'Asignaciones' })).toHaveAttribute('href', '/reparaciones/asignaciones')
    await screen.findByRole('link', { name: 'Pendientes' })
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })
```
(imports: `http`, `HttpResponse` de `msw`, `server` de `@/test/server`, `SESION_SUPER`).

`src/modules/taller/api.test.tsx`:

```tsx
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { crearQueryClient } from '@/shared/api/queryClient'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { guardarSesion } from '@/shared/session/storage'
import { server } from '@/test/server'
import { SESION_SUPER, SESION_TEC } from '@/test/render'
import { useAsignaciones, useContadoresPendientes, usePorCerrar } from './api'

function envoltorio(sesion: typeof SESION_TEC) {
  guardarSesion(sesion)
  const qc = crearQueryClient({ retry: false })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}><SessionProvider>{children}</SessionProvider></QueryClientProvider>
  )
}

describe('api del taller', () => {
  it('el técnico pide sus asignaciones sin ?tecnico= y el supertécnico con su idTec', async () => {
    const urls: string[] = []
    server.use(http.get('*/api/glass/asignaciones', ({ request }) => { urls.push(new URL(request.url).search); return HttpResponse.json([]) }))
    const tec = renderHook(() => useAsignaciones('GLASS'), { wrapper: envoltorio(SESION_TEC) })
    await waitFor(() => expect(tec.result.current.isSuccess).toBe(true))
    const sup = renderHook(() => useAsignaciones('GLASS'), { wrapper: envoltorio(SESION_SUPER) })
    await waitFor(() => expect(sup.result.current.isSuccess).toBe(true))
    expect(urls).toEqual(['', '?tecnico=3'])
  })
  it('una acción de Pendientes invalida la lista y los contadores', async () => {
    let contadores = 0
    server.use(
      http.get('*/api/reparaciones/asignaciones', () => HttpResponse.json([])),
      http.get('*/api/reparaciones/pendientes/contadores', () => { contadores++; return HttpResponse.json({ reparaciones: 1, glass: 0, pulidos: 0 }) }),
      http.patch('*/api/reparaciones/asignaciones/A1/por-cerrar', () => new HttpResponse(null, { status: 204 })),
    )
    const wrapper = envoltorio(SESION_TEC)
    const c = renderHook(() => useContadoresPendientes(), { wrapper })
    await waitFor(() => expect(c.result.current.isSuccess).toBe(true))
    const m = renderHook(() => usePorCerrar(), { wrapper })
    await m.result.current.mutateAsync({ idRep: 'A1', porCerrar: true })
    await waitFor(() => expect(contadores).toBe(2))
  })
})
```

```bash
npm test -- rutas SubNav api
```
Expected: fallan.

- [ ] **Step 2: Implementar `estado.ts`, `api.ts`, `rutas.tsx`, `BadgePendientes`**

`src/modules/taller/estado.ts`:

```ts
import { crearStore } from '@/shared/lib/store'
import type { EstadoIncidencia, TipoPendiente } from './lib/filtros'

/** Filtros que sobreviven al cambio de ruta dentro del taller (calco de los campos de los controllers del JavaFX). */
export const filtroImeiPendientes = crearStore('')
export const tipoPendientes = {
  REPARACION: crearStore(new Set<TipoPendiente>()),
  GLASS: crearStore(new Set<TipoPendiente>()),
}

export type FiltrosHistorial = { tecnicos: Set<number>; piezas: Set<string>; desde: string; hasta: string; incidencias: Set<EstadoIncidencia> }
const historialVacio = (): FiltrosHistorial => ({ tecnicos: new Set(), piezas: new Set(), desde: '', hasta: '', incidencias: new Set() })
export const filtroImeiHistorial = crearStore('')
export const filtrosHistorial = {
  REPARACION: crearStore<FiltrosHistorial>(historialVacio()),
  GLASS: crearStore<FiltrosHistorial>(historialVacio()),
  PULIDO: crearStore<FiltrosHistorial>(historialVacio()),
}

export type FiltrosImeis = { imei: string; tecnicos: Set<number>; clientes: Set<string>; desde: string; hasta: string; incidencias: Set<EstadoIncidencia> }
export const FILTROS_IMEIS_VACIOS: FiltrosImeis = { imei: '', tecnicos: new Set(), clientes: new Set(), desde: '', hasta: '', incidencias: new Set() }
export const filtrosImeis = crearStore<FiltrosImeis>(FILTROS_IMEIS_VACIOS)
/** IMEI del detalle del que se vuelve: el maestro lo reselecciona y desplaza hasta él. */
export const ultimoImeiVisto = crearStore<string | null>(null)

export function reiniciarEstadoTaller() {
  filtroImeiPendientes.reset()
  tipoPendientes.REPARACION.reset()
  tipoPendientes.GLASS.reset()
  filtroImeiHistorial.reset()
  filtrosHistorial.REPARACION.reset()
  filtrosHistorial.GLASS.reset()
  filtrosHistorial.PULIDO.reset()
  filtrosImeis.reset()
  ultimoImeiVisto.reset()
}
```

`src/modules/taller/api.ts`:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Cliente, type ContadoresPendientes, type ReparacionResumen, type Tecnico } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin, esSuperTecnico } from '@/shared/session/storage'

export type TipoLista = 'REPARACION' | 'GLASS' | 'PULIDO'

export const claveHistorial = (tipo: TipoLista) => ['historial', tipo] as const
export const claveAsignaciones = (tipo: TipoLista) => ['asignaciones', tipo] as const
export const CLAVE_CONTADORES = ['pendientes', 'contadores'] as const
export const CLAVE_TECNICOS = ['tecnicos'] as const
export const CLAVE_TECNICOS_ACTIVOS = ['tecnicos', 'activos'] as const
export const CLAVE_CLIENTES_ACTIVOS = ['clientes', 'activos'] as const

async function pedirHistorial(tipo: TipoLista): Promise<ReparacionResumen[]> {
  switch (tipo) {
    case 'REPARACION': return (await api.GET('/api/reparaciones/historial')).data ?? []
    case 'GLASS': return (await api.GET('/api/glass/historial')).data ?? []
    case 'PULIDO': return (await api.GET('/api/pulidos/historial')).data ?? []
  }
}

async function pedirAsignaciones(tipo: TipoLista, tecnico: number | undefined): Promise<ReparacionResumen[]> {
  const params = { query: { tecnico } }
  switch (tipo) {
    case 'REPARACION': return (await api.GET('/api/reparaciones/asignaciones', { params })).data ?? []
    case 'GLASS': return (await api.GET('/api/glass/asignaciones', { params })).data ?? []
    case 'PULIDO': return (await api.GET('/api/pulidos/asignaciones', { params })).data ?? []
  }
}

/** El supertécnico pide sus propios pendientes con ?tecnico=; al técnico el servidor ya se lo fuerza (FiltroTecnico). */
function useTecnicoPropio(): number | undefined {
  const { sesion } = useSession()
  return esSuperTecnico(sesion) && sesion?.idTec != null ? sesion.idTec : undefined
}

/** Historial (R/G/P). El ADMIN no sondea, como en el JavaFX. */
export function useHistorial(tipo: TipoLista) {
  const { sesion } = useSession()
  const intervalo = useIntervaloRefresco(!esAdmin(sesion))
  return useQuery({ queryKey: claveHistorial(tipo), queryFn: () => pedirHistorial(tipo), refetchInterval: intervalo })
}

/** Pendientes (A/AG/AP) del técnico de la sesión. */
export function useAsignaciones(tipo: TipoLista) {
  const tecnico = useTecnicoPropio()
  const intervalo = useIntervaloRefresco()
  return useQuery({ queryKey: [...claveAsignaciones(tipo), tecnico ?? 'propio'], queryFn: () => pedirAsignaciones(tipo, tecnico), refetchInterval: intervalo })
}

export function useContadoresPendientes() {
  const tecnico = useTecnicoPropio()
  const intervalo = useIntervaloRefresco()
  return useQuery({
    queryKey: [...CLAVE_CONTADORES, tecnico ?? 'propio'],
    queryFn: async (): Promise<ContadoresPendientes> =>
      (await api.GET('/api/reparaciones/pendientes/contadores', { params: { query: { tecnico } } })).data ?? { reparaciones: 0, glass: 0, pulidos: 0 },
    refetchInterval: intervalo,
  })
}

export function useTecnicos(soloActivos = false) {
  return useQuery({
    queryKey: soloActivos ? CLAVE_TECNICOS_ACTIVOS : CLAVE_TECNICOS,
    queryFn: async (): Promise<Tecnico[]> => (soloActivos ? (await api.GET('/api/tecnicos/activos')).data : (await api.GET('/api/tecnicos')).data) ?? [],
  })
}

export function useClientesActivos() {
  return useQuery({ queryKey: CLAVE_CLIENTES_ACTIVOS, queryFn: async (): Promise<Cliente[]> => (await api.GET('/api/clientes/activos')).data ?? [] })
}

export async function referenciadora(idRep: string): Promise<string | null> {
  const { data } = await api.GET('/api/reparaciones/{idRep}/referenciadora', { params: { path: { idRep } } })
  return data?.value ?? null
}

function useInvalidar(...claves: readonly (readonly unknown[])[]) {
  const qc = useQueryClient()
  return () => Promise.all(claves.map((queryKey) => qc.invalidateQueries({ queryKey })))
}

const PENDIENTES_REP = [claveAsignaciones('REPARACION'), CLAVE_CONTADORES] as const
const PENDIENTES_GLASS = [claveAsignaciones('GLASS'), CLAVE_CONTADORES] as const
const PENDIENTES_AMBAS = [claveAsignaciones('REPARACION'), claveAsignaciones('GLASS'), CLAVE_CONTADORES] as const
const PENDIENTES_PUL = [claveAsignaciones('PULIDO'), CLAVE_CONTADORES] as const
const HISTORIALES = [claveHistorial('REPARACION'), claveHistorial('GLASS'), claveHistorial('PULIDO')] as const

export function usePorCerrar() {
  const recargar = useInvalidar(...PENDIENTES_REP)
  return useMutation({
    mutationFn: ({ idRep, porCerrar }: { idRep: string; porCerrar: boolean }) =>
      api.PATCH('/api/reparaciones/asignaciones/{idRep}/por-cerrar', { params: { path: { idRep } }, body: { porCerrar } }),
    onSettled: recargar,
  })
}
export function useEntregaGlass() {
  const recargar = useInvalidar(...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: ({ idRep, entregado }: { idRep: string; entregado: boolean }) =>
      api.PATCH('/api/reparaciones/asignaciones/{idRep}/entrega-glass', { params: { path: { idRep } }, body: { entregado } }),
    onSettled: recargar,
  })
}
export function useMarcarLlegada() {
  const recargar = useInvalidar(...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: (idRep: string) => api.PATCH('/api/reparaciones/asignaciones/{idRep}/llegada', { params: { path: { idRep } } }),
    onSettled: recargar,
  })
}
export function useDeshacerLlegada() {
  const recargar = useInvalidar(...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: (idRep: string) => api.DELETE('/api/reparaciones/asignaciones/{idRep}/llegada', { params: { path: { idRep } } }),
    onSettled: recargar,
  })
}
export function useBorrarAsignacion() {
  const recargar = useInvalidar(...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: (idAsig: string) => api.DELETE('/api/reparaciones/asignaciones/{idAsig}', { params: { path: { idAsig } } }),
    onSettled: recargar,
  })
}
export function useBorrarIncidenciaActiva() {
  const recargar = useInvalidar(...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: ({ imei, tipo }: { imei: string; tipo: 'R' | 'G' }) =>
      api.DELETE('/api/reparaciones/imei/{imei}/incidencia-activa', { params: { path: { imei }, query: { tipo } } }),
    onSettled: recargar,
  })
}
export function useCompletarPulidos() {
  const recargar = useInvalidar(...PENDIENTES_PUL, claveHistorial('PULIDO'))
  return useMutation({
    mutationFn: (ids: string[]) => api.POST('/api/pulidos/asignaciones/completar-lote', { body: { ids } }),
    onSettled: recargar,
  })
}
export function useBorrarAsignacionPulido() {
  const recargar = useInvalidar(...PENDIENTES_PUL)
  return useMutation({
    mutationFn: (idAP: string) => api.DELETE('/api/pulidos/asignaciones/{idAP}', { params: { path: { idAP } } }),
    onSettled: recargar,
  })
}
export function useBorrarReparacion() {
  const recargar = useInvalidar(...HISTORIALES)
  return useMutation({
    mutationFn: ({ idRep, motivo }: { idRep: string; motivo: string }) =>
      api.DELETE('/api/reparaciones/{idRep}', { params: { path: { idRep } }, body: { motivo } }),
    onSettled: recargar,
  })
}
export function useBorrarPulido() {
  const recargar = useInvalidar(claveHistorial('PULIDO'))
  return useMutation({
    mutationFn: ({ idP, motivo }: { idP: string; motivo: string }) =>
      api.DELETE('/api/pulidos/historial/{idP}', { params: { path: { idP } }, body: { motivo } }),
    onSettled: recargar,
  })
}
/** La vista muestra "No se pudo guardar: <mensaje>" (calco del JavaFX), de ahí meta.silenciarError. */
export function useAnadirIncidencia() {
  const recargar = useInvalidar(...HISTORIALES, ...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: ({ idRep, comentario, imei, idTec }: { idRep: string; comentario: string; imei: string; idTec: number }) =>
      api.POST('/api/reparaciones/{idRep}/incidencia', { params: { path: { idRep } }, body: { comentario, imei, idTec } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}
export function useCancelarIncidencia() {
  const recargar = useInvalidar(...HISTORIALES, ...PENDIENTES_AMBAS)
  return useMutation({
    mutationFn: (idRep: string) => api.DELETE('/api/reparacion-componentes/{idRep}/incidencia', { params: { path: { idRep } } }),
    onSettled: recargar,
  })
}
/** Observación y cliente del teléfono llevan bloqueo optimista: su 409 lo traduce la vista (meta.silenciarError).
 *  `updatedAt` es el `telefonoUpdatedAt` de la fila (no nullable en el contrato: el servidor exige la fila Telefono). */
export function useEditarObservacionTelefono() {
  const recargar = useInvalidar(...HISTORIALES)
  return useMutation({
    mutationFn: ({ imei, observacion, updatedAt }: { imei: string; observacion: string; updatedAt: string }) =>
      api.PATCH('/api/telefonos/{imei}/observacion', { params: { path: { imei } }, body: { observacion, updatedAt } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}
export function useEditarClienteTelefono() {
  const recargar = useInvalidar(...HISTORIALES)
  return useMutation({
    mutationFn: ({ imei, idCli, updatedAt }: { imei: string; idCli: number | null; updatedAt: string }) =>
      api.PATCH('/api/telefonos/{imei}/cliente', { params: { path: { imei } }, body: { idCli, updatedAt } }),
    meta: { silenciarError: true },
    onSettled: recargar,
  })
}
export function useEditarModeloTelefono() {
  const recargar = useInvalidar(...HISTORIALES)
  return useMutation({
    mutationFn: ({ imei, modelo }: { imei: string; modelo: string }) =>
      api.POST('/api/telefonos', { body: { imei, modelo, idCli: null, clienteExplicito: null } }),
    onSettled: recargar,
  })
}
```
Si `tsc` rechaza `clienteExplicito: null`, usar `false`. (Task 3 dejó `updatedAt` no nullable en los cuerpos de petición a propósito: el DAO lo desreferencia y la columna es NOT NULL; por eso las dos mutaciones lo tipan `string` y la vista solo ofrece "Editar observación" / "Editar cliente" cuando el grupo tiene `telefonoUpdatedAt`.)

`src/modules/taller/componentes/BadgePendientes.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { useContadoresPendientes } from '../api'
import { textoBadgeLateral } from '../lib/filtros'

/** Calco de .sidebar-badge: suma rep + glass + pulidos, tope 99+, oculto a cero; blanco sobre el enlace activo, navy en los demás. */
export function BadgePendientes({ activo }: { activo: boolean }) {
  const { data } = useContadoresPendientes()
  const texto = data ? textoBadgeLateral(data.reparaciones + data.glass + data.pulidos) : null
  if (!texto) return null
  return (
    <span className={cn('ml-2 inline-block min-w-[14px] rounded-lg px-[5px] py-px text-center text-[9px] font-bold', activo ? 'bg-superficie text-azul-noche' : 'bg-azul-noche text-superficie')}>
      {texto}
    </span>
  )
}
```

`src/modules/taller/rutas.tsx`:

```tsx
import { Navigate, Outlet } from 'react-router'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin, esAdminOSuperTecnico, type Sesion } from '@/shared/session/storage'

export type EnlaceTaller = { to: string; label: string; badge?: 'pendientes' }

/** Columna lateral de Reparaciones en el orden del JavaFX: TECNICO Pendientes·Historial·IMEIs; SUPERTECNICO
 *  Asignaciones·Pendientes·Historial·IMEIs; ADMIN Asignaciones·Historial·IMEIs. */
export function enlacesReparaciones(sesion: Sesion | null): EnlaceTaller[] {
  const enlaces: EnlaceTaller[] = []
  if (esAdminOSuperTecnico(sesion)) enlaces.push({ to: '/reparaciones/asignaciones', label: 'Asignaciones' })
  if (!esAdmin(sesion) && sesion?.idTec != null) enlaces.push({ to: '/reparaciones/pendientes', label: 'Pendientes', badge: 'pendientes' })
  enlaces.push({ to: '/reparaciones/historial', label: 'Historial' }, { to: '/reparaciones/imeis', label: 'IMEIs' })
  return enlaces
}

/** Entrada por rol (spec web-taller §4.1): TECNICO en Pendientes; ADMIN y, hasta el sub-proyecto 3, SUPERTECNICO en Historial. */
export function InicioReparaciones() {
  const { sesion } = useSession()
  const aPendientes = !esAdminOSuperTecnico(sesion) && sesion?.idTec != null
  return <Navigate to={aPendientes ? '/reparaciones/pendientes' : '/reparaciones/historial'} replace />
}

/** Pendientes exige técnico en sesión: el ADMIN (sin idTec) va a Historial. */
export function RequiereTecnico() {
  const { sesion } = useSession()
  if (esAdmin(sesion) || sesion?.idTec == null) return <Navigate to="/reparaciones/historial" replace />
  return <Outlet />
}
```

`SubNav.tsx`: sustituir el mapa y el render por:

```tsx
import { NavLink, useLocation } from 'react-router'
import { enlacesReparaciones, type EnlaceTaller } from '@/modules/taller/rutas'
import { BadgePendientes } from '@/modules/taller/componentes/BadgePendientes'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import type { Sesion } from '@/shared/session/storage'

/** Enlaces de la columna por sección (primer segmento de la ruta) y rol. Cada sub-proyecto añade los suyos. */
const SUBNAV: Record<string, (sesion: Sesion | null) => EnlaceTaller[]> = {
  clientes: () => [{ to: '/clientes', label: 'Clientes' }],
  reparaciones: enlacesReparaciones,
}

export function SubNav() {
  const { pathname } = useLocation()
  const { sesion } = useSession()
  const seccion = pathname.split('/')[1]
  // Object.hasOwn: la sección viene de la URL y un /constructor resolvería a un miembro heredado de Object.prototype
  const enlaces = Object.hasOwn(SUBNAV, seccion) ? SUBNAV[seccion](sesion) : []
  return (
    <nav aria-label="Sub-navegación" className="w-[200px] shrink-0 bg-superficie p-2">
      {enlaces.map((e) => (
        <NavLink
          key={e.to}
          to={e.to}
          className={({ isActive }) =>
            cn(
              'flex w-full items-center rounded-3xl px-4 py-2.5 text-left text-[13px] font-bold',
              isActive ? 'bg-azul-noche text-texto-nav-activo' : 'text-azul-medio hover:bg-azul-medio/8',
            )
          }
        >
          {({ isActive }) => (
            <>
              {e.label}
              {e.badge === 'pendientes' && <BadgePendientes activo={isActive} />}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
```

`router.tsx`: sustituir la entrada `/reparaciones/*` por (importando `InicioReparaciones` y `RequiereTecnico` de `@/modules/taller/rutas`):

```tsx
          { path: '/reparaciones', element: <InicioReparaciones /> },
          { path: '/reparaciones/asignaciones', element: <PendienteDeMigrar nombre="Asignaciones" /> },
          {
            element: <RequiereTecnico />,
            children: [
              { path: '/reparaciones/pendientes', element: <PendienteDeMigrar nombre="Pendientes" /> },
              { path: '/reparaciones/pendientes/glass', element: <PendienteDeMigrar nombre="Pendientes (glass)" /> },
              { path: '/reparaciones/pendientes/pulidos', element: <PendienteDeMigrar nombre="Pendientes (pulidos)" /> },
            ],
          },
          { path: '/reparaciones/historial', element: <PendienteDeMigrar nombre="Historial" /> },
          { path: '/reparaciones/historial/glass', element: <PendienteDeMigrar nombre="Historial (glass)" /> },
          { path: '/reparaciones/historial/pulidos', element: <PendienteDeMigrar nombre="Historial (pulidos)" /> },
          { path: '/reparaciones/imeis', element: <PendienteDeMigrar nombre="IMEIs" /> },
          { path: '/reparaciones/imeis/:imei', element: <PendienteDeMigrar nombre="IMEIs (detalle)" /> },
```
(las Tasks 14-18 sustituyen cada placeholder por su página).

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/app src/modules/taller
git commit -m "feat(web): rutas del taller con entrada por rol, columna lateral por rol con badge de pendientes, estado compartido y api del modulo"
```

---

### Task 14: Web — Pendientes: Reparaciones y Glass

**Files:**
- Create: `src/shared/ui/Botones.tsx`, `src/shared/ui/PildoraContador.tsx`, `src/modules/taller/lib/textos.ts`, `src/modules/taller/componentes/TogglesPendientes.tsx`, `CeldaImeiPendiente.tsx`, `BadgesEstadoPendiente.tsx`, `BotonPapelera.tsx`, `src/modules/taller/pendientes/textoCelda.ts`, `MenuPendiente.tsx`, `PendientesPage.tsx`, `PendientesPage.test.tsx`
- Modify: `src/app/router.tsx` (placeholders de `/reparaciones/pendientes` y `/glass` → `PendientesPage`)

**Interfaces:**
- Consumes: `useAsignaciones`, `useContadoresPendientes`, mutaciones de Pendientes (Task 13); stores `filtroImeiPendientes`, `tipoPendientes` (Task 13); `badgesEstado`, `ordenarPendientes`, `pasaImeis`, `pasaTipo`, `etiquetaContador`, `sufijoToggle` (Task 12); `entregaGlass` (Task 11); `DataTable`, `FiltroImei`, `MultiSelect`, `TogglePill`, `BadgeTipo`, `EtiquetaActualizado`, `ConfirmDialog`, `MenuCopiarCelda` (Tasks 8-10); `useRegistrarExportable` de `@/shared/ui/exportable` (Task 5).
- Produces: `<BotonPrimario />`, `<BotonSecundario />` (calco de `btn-primary` / `btn-secondary`), `<PildoraContador texto />`, `<TogglesPendientes />`, `<BotonPapelera onClick />`, `TOOLTIP_FORMULARIO` (`lib/textos.ts`), `<PendientesPage tipo="REPARACION" | "GLASS" />`.

- [ ] **Step 1: Tests de la página (fallan)**

`src/modules/taller/pendientes/PendientesPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER, SESION_TEC } from '@/test/render'
import * as csv from '@/shared/lib/csv'
import { reiniciarEstadoTaller } from '../estado'
import { glass, normal, resumen } from '../test/fabrica'
import { PendientesPage } from './PendientesPage'

const filas = [
  resumen({ idRep: 'A20260915_29', imei: '355400000000111', modelo: '14plus', cliente: 'CLIENTE F', nombreTecnicoAsigna: 'Técnico E', urgente: true, fechaAsig: '2026-09-15T08:53:00' }),
  { ...normal(true, null), idRep: 'A20260916_12', imei: '355100000000101', modelo: '14', esChasis: true, cliente: 'AMAZON', fechaAsig: '2026-09-16T07:02:00' },
  resumen({ idRep: 'A20260916_1', imei: '353400000000081', modelo: '15pro', cliente: null, esSolicitud: 2, tiposSolicitud: 'Batería, Pantalla', fechaAsig: '2026-09-16T06:50:00' }),
  resumen({ idRep: 'A20260916_2', imei: '350200000000011', esIncidencia: true, porCerrar: true, cliente: null }),
]

beforeEach(() => {
  reiniciarEstadoTaller()
  server.use(
    http.get('*/api/reparaciones/asignaciones', () => HttpResponse.json(filas)),
    http.get('*/api/glass/asignaciones', () => HttpResponse.json([])),
    http.get('*/api/pulidos/asignaciones', () => HttpResponse.json([])),
    http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 4, glass: 0, pulidos: 120 })),
  )
})

const abrir = (sesion = SESION_TEC, ruta = '/reparaciones/pendientes') => renderConProviders(<PendientesPage tipo="REPARACION" />, { sesion, ruta })

describe('PendientesPage (ficha docs/paridad/pendientes.md)', () => {
  it('título, contador, toggles con sufijo y columnas del TableView', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Mis asignaciones pendientes' })).toBeInTheDocument()
    expect(screen.getByText('4 pendientes')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Reparaciones (4)' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Glass (0)' })).toHaveAttribute('href', '/reparaciones/pendientes/glass')
    expect(screen.getByRole('link', { name: 'Pulidos (99+)' })).toBeInTheDocument()
    const cabeceras = screen.getAllByRole('columnheader').map((c) => c.textContent)
    expect(cabeceras).toEqual(['Id Asignación', 'Tipo', 'IMEI', 'Modelo', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por', 'Estado', ''])
    expect(screen.getByText('iPhone 14 Plus')).toBeInTheDocument()
    expect(screen.getByText('2026/09/15 10:53')).toBeInTheDocument()
  })
  it('orden urgente → con cliente → resto; borde por solicitud e incidencia; badges de estado', async () => {
    abrir()
    await screen.findByText('A20260915_29')
    const ids = screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0].textContent)
    expect(ids).toEqual(['A20260915_29', 'A20260916_12', 'A20260916_1', 'A20260916_2'])
    expect(screen.getByRole('row', { name: /A20260916_1 / })).toHaveClass('border-l-fila-solicitud-brd')
    expect(screen.getByRole('row', { name: /A20260916_2 / })).toHaveClass('border-l-fila-incidencia-brd')
    expect(screen.getByText('Urgente')).toHaveClass('bg-urgente-bg')
    expect(screen.getByText('Solicitud')).toBeInTheDocument()
    expect(screen.getByText('2 piezas')).toBeInTheDocument()
    expect(screen.getByText('Por cerrar')).toBeInTheDocument()
    expect(screen.getByText('Incidencia')).toBeInTheDocument()
    expect(screen.getByText('Chasis')).toBeInTheDocument()
    expect(screen.getByText('Glass: Técnico H')).toHaveClass('bg-tipo-glass-bg')
  })
  it('el botón "Añadir reparación" está deshabilitado con el tooltip del formulario', async () => {
    abrir()
    const botones = await screen.findAllByRole('button', { name: 'Añadir reparación' })
    expect(botones).toHaveLength(4)
    expect(botones[0]).toBeDisabled()
    expect(botones[0].parentElement).toHaveAttribute('title', 'Disponible con el formulario de reparación (siguiente entrega)')
  })
  it('filtro Tipo: casillas, etiqueta "Todas"/"N filtros" y filtrado', async () => {
    abrir()
    await screen.findByText('A20260915_29')
    await userEvent.click(screen.getByRole('button', { name: 'Tipo' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Solicitudes pieza' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Solicitudes pieza' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByText('1 pendiente')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Solicitudes pieza' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Incidencias' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Asignaciones' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Todas' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getByRole('button', { name: 'Tipo' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(5)
  })
  it('filtro IMEI: solo los de 15 dígitos filtran', async () => {
    abrir()
    await screen.findByText('A20260915_29')
    const campo = screen.getByPlaceholderText('Filtrar por IMEI')
    await userEvent.type(campo, '3554')
    expect(screen.getAllByRole('row')).toHaveLength(5)
    await userEvent.type(campo, '00000000111')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(campo).toHaveValue('355400000000111, ')
  })
  it('menú contextual: copiar, por cerrar y "Entregar a" según la fila; PATCH por-cerrar', async () => {
    let body: unknown = null
    server.use(http.patch('*/api/reparaciones/asignaciones/A20260916_2/por-cerrar', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 204 }) }))
    abrir()
    await screen.findByText('A20260915_29')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('A20260916_12') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda', 'Marcar por cerrar', 'Entregar a Técnico H'])
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('A20260916_2') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Quitar por cerrar' }))
    await waitFor(() => expect(body).toEqual({ porCerrar: false }))
  })
  it('pestaña Glass: "Marcar que llegó" en la fila bloqueada, botón oculto y placeholder', async () => {
    const bloqueada = { ...glass(null), idRep: 'AG20260916_1', imei: '351111111111111', normalAbierta: true, normalTecnicoNombre: 'Técnico J' }
    server.use(http.get('*/api/glass/asignaciones', () => HttpResponse.json([bloqueada])), http.patch('*/api/reparaciones/asignaciones/AG20260916_1/llegada', () => new HttpResponse(null, { status: 204 })))
    renderConProviders(<PendientesPage tipo="GLASS" />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' })
    await screen.findByText('AG20260916_1')
    expect(screen.getByText('Rep: Técnico J')).toHaveClass('bg-tipo-reparacion-bg')
    expect(screen.queryByRole('button', { name: 'Añadir glass' })).not.toBeInTheDocument()
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('AG20260916_1') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda', 'Marcar que llegó'])
    await userEvent.keyboard('{Escape}')
    server.use(http.get('*/api/glass/asignaciones', () => HttpResponse.json([])))
    renderConProviders(<PendientesPage tipo="GLASS" />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' })
    expect(await screen.findByText('No tienes asignaciones pendientes')).toBeInTheDocument()
  })
  it('la papelera solo la ve el supertécnico y borra la asignación o la incidencia activa', async () => {
    let borrada: string | null = null
    server.use(
      http.get('*/api/reparaciones/asignaciones', ({ request }) => { expect(new URL(request.url).searchParams.get('tecnico')).toBe('3'); return HttpResponse.json(filas) }),
      http.delete('*/api/reparaciones/asignaciones/A20260915_29', () => { borrada = 'asig'; return new HttpResponse(null, { status: 204 }) }),
      http.delete('*/api/reparaciones/imei/350200000000011/incidencia-activa', ({ request }) => { borrada = `inc:${new URL(request.url).searchParams.get('tipo')}`; return new HttpResponse(null, { status: 204 }) }),
    )
    abrir(SESION_SUPER)
    await screen.findByText('A20260915_29')
    expect(screen.getAllByRole('columnheader')).toHaveLength(11)
    await userEvent.click(screen.getAllByRole('button', { name: 'Borrar asignación' })[0])
    const dlg = screen.getByRole('dialog', { name: 'Borrar asignación A20260915_29' })
    expect(within(dlg).getByText('El técnico dejará de verla en su lista de pendientes.')).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Borrar asignación' }))
    await waitFor(() => expect(borrada).toBe('asig'))
    await userEvent.click(screen.getAllByRole('button', { name: 'Borrar asignación' })[3])
    expect(screen.getByText('El técnico dejará de verla en su lista de pendientes y la incidencia se marcará como no activa en la tabla principal.')).toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Borrar asignación' }))
    await waitFor(() => expect(borrada).toBe('inc:R'))
  })
  it('un técnico no ve la papelera', async () => {
    abrir()
    await screen.findByText('A20260915_29')
    expect(screen.queryByRole('button', { name: 'Borrar asignación' })).not.toBeInTheDocument()
  })
  it('"Descargar CSV" exporta las filas visibles con las cabeceras del técnico', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    abrir()
    await screen.findByText('A20260915_29')
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    expect(descargar).toHaveBeenCalledTimes(1)
    const [base, cabeceras, filasCsv] = descargar.mock.calls[0]
    expect(base).toBe('mis_pendientes')
    expect(cabeceras).toEqual(['ID Reparación', 'IMEI', 'Fecha asig.', 'Fecha fin', 'Componente', 'Observaciones', 'Incidencia', 'Resuelto', 'ID Rep. anterior'])
    expect(filasCsv[0]).toEqual(['A20260915_29', '="355400000000111"', '15/09/2026 10:53', '', '', '', 'No', 'No', ''])
    descargar.mockRestore()
  })
})
```
Nota: el test del CSV necesita el menú de usuario, así que este `renderConProviders` se envuelve en `AppLayout` (`rutas` con `<Route element={<AppLayout />}>`): si `renderConProviders` no lo permite, montar `<AppLayout />` con `ruta` y una `<Route path="/reparaciones/pendientes" element={<PendientesPage tipo="REPARACION" />} />` dentro de `rutas`; ajustar `abrir()` en consecuencia para ese test.

```bash
npm test -- PendientesPage
```
Expected: falla (módulo inexistente).

- [ ] **Step 2: Componentes compartidos pequeños**

`src/shared/ui/Botones.tsx`:

```tsx
import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/utils'
import { Button } from './button'

/** Calco de .btn-primary: navy, radio 24, 12 px negrita, padding 8 16. */
export function BotonPrimario({ className, ...props }: ComponentProps<typeof Button>) {
  return <Button className={cn('h-auto rounded-3xl bg-azul-noche px-4 py-2 text-[12px] font-bold text-texto-nav-activo hover:bg-azul-noche-hover disabled:opacity-50', className)} {...props} />
}

/** Calco de .btn-secondary: crema, borde navy, radio 24, 12 px negrita, hover #E8EAF0. */
export function BotonSecundario({ className, ...props }: ComponentProps<typeof Button>) {
  return <Button variant="outline" className={cn('h-auto rounded-3xl border-azul-noche bg-crema px-4 py-2 text-[12px] font-bold text-azul-noche shadow-none hover:bg-badge-neutro-bg hover:text-azul-noche', className)} {...props} />
}
```

`src/shared/ui/PildoraContador.tsx`:

```tsx
/** Calco de lblContador: píldora gris (#E8EAF0 / #586376, 12 px negrita, radio 12, padding 3 10). */
export function PildoraContador({ texto }: { texto: string }) {
  return <span className="inline-block rounded-xl bg-badge-neutro-bg px-2.5 py-[3px] text-[12px] font-bold text-azul-gris">{texto}</span>
}
```

- [ ] **Step 3: Componentes del módulo**

`src/modules/taller/componentes/TogglesPendientes.tsx`:

```tsx
import { TogglePill } from '@/shared/ui/TogglePill'
import { useContadoresPendientes } from '../api'
import { sufijoToggle } from '../lib/filtros'

/** Toggles de Pendientes con el sufijo "(n)" siempre presente (tope 99+), alimentado por los contadores del servidor. */
export function TogglesPendientes() {
  const { data } = useContadoresPendientes()
  const c = data ?? { reparaciones: 0, glass: 0, pulidos: 0 }
  return (
    <TogglePill
      className="mb-2"
      opciones={[
        { to: '/reparaciones/pendientes', etiqueta: `Reparaciones ${sufijoToggle(c.reparaciones)}` },
        { to: '/reparaciones/pendientes/glass', etiqueta: `Glass ${sufijoToggle(c.glass)}` },
        { to: '/reparaciones/pendientes/pulidos', etiqueta: `Pulidos ${sufijoToggle(c.pulidos)}` },
      ]}
    />
  )
}
```

`src/modules/taller/componentes/CeldaImeiPendiente.tsx`:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { TIPO_TRABAJO } from '@/shared/lib/tipoTrabajo'
import { cn } from '@/shared/lib/utils'
import { CLASES_MINI_PILDORA } from '@/shared/ui/pildora'
import { etiquetaGlassPendiente, etiquetaRepAbierta, tooltipGlassPendiente, tooltipRepAbierta } from '../lib/entregaGlass'

/** IMEI con la mini-píldora "Glass: X" (fila de reparación con glass sin entregar) o "Rep: X" (fila de glass con reparación abierta). */
export function CeldaImeiPendiente({ rep }: { rep: ReparacionResumen }) {
  const glassPendiente = etiquetaGlassPendiente(rep)
  const repAbierta = etiquetaRepAbierta(rep)
  return (
    <div className="flex flex-col items-start gap-px">
      <span className="text-[12px]">{rep.imei}</span>
      {glassPendiente && <span title={tooltipGlassPendiente(rep) ?? undefined} className={cn(CLASES_MINI_PILDORA, TIPO_TRABAJO.GLASS.clases)}>{glassPendiente}</span>}
      {!glassPendiente && repAbierta && <span title={tooltipRepAbierta(rep) ?? undefined} className={cn(CLASES_MINI_PILDORA, TIPO_TRABAJO.REPARACION.clases)}>{repAbierta}</span>}
    </div>
  )
}
```

`src/modules/taller/componentes/BadgesEstadoPendiente.tsx`:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from '@/shared/ui/pildora'
import { badgesEstado } from '../lib/estadoPendiente'

export function BadgesEstadoPendiente({ rep, hoy }: { rep: ReparacionResumen; hoy: string }) {
  return (
    <div className="flex flex-col items-start gap-0.5">
      {badgesEstado(rep, hoy).map((b) => (
        <span key={b.texto} title={b.tooltip} className="flex flex-col items-start">
          <span className={cn(CLASES_PILDORA, b.clases)}>{b.texto}</span>
          {b.sub && <span className="text-[10px] text-azul-gris">{b.sub}</span>}
        </span>
      ))}
    </div>
  )
}
```

`src/modules/taller/componentes/BotonPapelera.tsx`:

```tsx
/** Calco del ImageView borrar.png (25 px, cursor de mano) de las papeleras del supertécnico. */
export function BotonPapelera({ onClick, etiqueta = 'Borrar asignación' }: { onClick: () => void; etiqueta?: string }) {
  return (
    <button type="button" onClick={onClick} aria-label={etiqueta} className="cursor-pointer">
      <img src="/borrar.png" alt="" className="h-[25px] w-[25px]" />
    </button>
  )
}
```
Copiar `gestion-reparaciones-cliente/src/main/resources/images/borrar.png` a `public/borrar.png` (y `editar.png`, `Historial.png` para las tareas 17 y 18) — son imágenes del producto, sin datos.

`src/modules/taller/pendientes/textoCelda.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { formatear } from '@/shared/lib/fechas'
import { traducirModelo } from '../lib/modelos'

export const FMT_PENDIENTES = 'yyyy/MM/dd HH:mm' as const

/** Texto de "Copiar celda" por columna (calco de textoDeCelda); null = columna no copiable. */
export function textoCeldaPendiente(rep: ReparacionResumen, columna: string): string | null {
  switch (columna) {
    case 'id': return rep.idRep
    case 'imei': return rep.imei
    case 'modelo': return traducirModelo(rep.modelo)
    case 'fecha': return formatear(rep.fechaAsig, FMT_PENDIENTES)
    case 'comentario': return rep.comentarioAsignacion ?? ''
    default: return null
  }
}
```

`src/modules/taller/pendientes/MenuPendiente.tsx`:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { tipoDe } from '@/shared/lib/tipoTrabajo'
import { ContextMenuItem } from '@/shared/ui/context-menu'
import type { CeldaPulsada } from '@/shared/ui/DataTable'
import { MenuCopiarCelda } from '@/shared/ui/MenuCopiarCelda'
import { mostrarMarcarLlegada, opcionDeshacerLlegada, opcionMenuEntrega } from '../lib/entregaGlass'
import { textoCeldaPendiente } from './textoCelda'

export type AccionesPendiente = {
  porCerrar: (rep: ReparacionResumen) => void
  entrega: (rep: ReparacionResumen) => void
  llegada: (rep: ReparacionResumen) => void
  deshacerLlegada: (rep: ReparacionResumen) => void
}

/** Menú contextual de Mis pendientes (calco del setRowFactory de PendientesTecnicoController). */
export function MenuPendiente({ rep, celda, glass, idTec, acciones }: { rep: ReparacionResumen; celda: CeldaPulsada; glass: boolean; idTec: number | null; acciones: AccionesPendiente }) {
  const esRepNormal = !glass && tipoDe(rep.idRep) === 'REPARACION'
  const opEntrega = opcionMenuEntrega(rep, glass, idTec)
  const opDeshacer = opcionDeshacerLlegada(rep, glass, idTec)
  return (
    <>
      <MenuCopiarCelda texto={textoCeldaPendiente(rep, celda.columnaId)} celda={celda} />
      {esRepNormal && <ContextMenuItem onSelect={() => acciones.porCerrar(rep)}>{rep.porCerrar ? 'Quitar por cerrar' : 'Marcar por cerrar'}</ContextMenuItem>}
      {opEntrega && <ContextMenuItem onSelect={() => acciones.entrega(rep)}>{opEntrega}</ContextMenuItem>}
      {mostrarMarcarLlegada(rep, glass) && <ContextMenuItem onSelect={() => acciones.llegada(rep)}>Marcar que llegó</ContextMenuItem>}
      {opDeshacer && <ContextMenuItem onSelect={() => acciones.deshacerLlegada(rep)}>{opDeshacer}</ContextMenuItem>}
    </>
  )
}
```

- [ ] **Step 4: La página**

`src/modules/taller/lib/textos.ts` (lo comparte el menú del Historial, Task 16):

```ts
/** Acciones que dependen del formulario de reparación (sub-proyecto 2): visibles pero deshabilitadas, con este tooltip. */
export const TOOLTIP_FORMULARIO = 'Disponible con el formulario de reparación (siguiente entrega)'
```

`src/modules/taller/pendientes/PendientesPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import type { ReparacionResumen } from '@/shared/api/client'
import { descargarCsv, textoForzado } from '@/shared/lib/csv'
import { formatear, hoyMadrid } from '@/shared/lib/fechas'
import { imeisValidos } from '@/shared/lib/filtroImei'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { BadgeTipo } from '@/shared/ui/BadgeTipo'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { FiltroImei } from '@/shared/ui/FiltroImei'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { useAsignaciones, useBorrarAsignacion, useBorrarIncidenciaActiva, useDeshacerLlegada, useEntregaGlass, useMarcarLlegada, usePorCerrar } from '../api'
import { BadgesEstadoPendiente } from '../componentes/BadgesEstadoPendiente'
import { BotonPapelera } from '../componentes/BotonPapelera'
import { CeldaImeiPendiente } from '../componentes/CeldaImeiPendiente'
import { TogglesPendientes } from '../componentes/TogglesPendientes'
import { filtroImeiPendientes, tipoPendientes } from '../estado'
import { ocultarAnadirGlass } from '../lib/entregaGlass'
import { etiquetaContador, ordenarPendientes, pasaImeis, pasaTipo, type TipoPendiente } from '../lib/filtros'
import { traducirModelo } from '../lib/modelos'
import { TOOLTIP_FORMULARIO } from '../lib/textos'
import { MenuPendiente } from './MenuPendiente'
import { FMT_PENDIENTES } from './textoCelda'

const FMT_CSV = 'dd/MM/yyyy HH:mm'
const OPCIONES_TIPO: { clave: TipoPendiente; etiqueta: string }[] = [
  { clave: 'solicitud', etiqueta: 'Solicitudes pieza' },
  { clave: 'incidencia', etiqueta: 'Incidencias' },
  { clave: 'asignacion', etiqueta: 'Asignaciones' },
]

/** "Añadir reparación" / "Añadir glass" deshabilitado hasta el sub-proyecto 2; en glass bloqueada no se pinta. */
function BotonAnadir({ rep, glass }: { rep: ReparacionResumen; glass: boolean }) {
  if (glass && ocultarAnadirGlass(rep)) return null
  return (
    <span title={TOOLTIP_FORMULARIO} className="inline-block">
      <BotonPrimario disabled className="pointer-events-none">{glass ? 'Añadir glass' : 'Añadir reparación'}</BotonPrimario>
    </span>
  )
}

export function PendientesPage({ tipo }: { tipo: 'REPARACION' | 'GLASS' }) {
  const glass = tipo === 'GLASS'
  const { sesion } = useSession()
  const esSuper = esSuperTecnico(sesion)
  const idTec = sesion?.idTec ?? null
  const { data = [], dataUpdatedAt, refetch } = useAsignaciones(tipo)
  const [filtroImei, setFiltroImei] = useStore(filtroImeiPendientes)
  const [tipos, setTipos] = useStore(tipoPendientes[tipo])
  const [seleccionada, setSeleccionada] = useState<string | null>(null)
  const [aBorrar, setABorrar] = useState<ReparacionResumen | null>(null)
  const hoy = hoyMadrid()
  const porCerrar = usePorCerrar()
  const entrega = useEntregaGlass()
  const llegada = useMarcarLlegada()
  const deshacerLlegada = useDeshacerLlegada()
  const borrarAsignacion = useBorrarAsignacion()
  const borrarIncidencia = useBorrarIncidenciaActiva()

  const visibles = useMemo(() => {
    const imeis = imeisValidos(filtroImei)
    return ordenarPendientes(data.filter((r) => pasaImeis(r.imei, imeis) && pasaTipo(r, tipos)))
  }, [data, filtroImei, tipos])

  const columnas = useMemo<ColumnDef<ReparacionResumen>[]>(() => {
    const base: ColumnDef<ReparacionResumen>[] = [
      { id: 'id', accessorKey: 'idRep', header: 'Id Asignación', size: 90 },
      { id: 'tipo', header: 'Tipo', size: 90, cell: ({ row }) => <BadgeTipo idRep={row.original.idRep} esChasis={row.original.esChasis} /> },
      { id: 'imei', header: 'IMEI', size: 130, cell: ({ row }) => <CeldaImeiPendiente rep={row.original} /> },
      { id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => traducirModelo(r.modelo) },
      { id: 'fecha', header: 'Fecha asignación', size: 130, accessorFn: (r) => formatear(r.fechaAsig, FMT_PENDIENTES) },
      { id: 'comentario', header: 'Comentario', size: 160, accessorFn: (r) => r.comentarioAsignacion ?? '' },
      { id: 'cliente', header: 'Cliente', size: 110, accessorFn: (r) => r.cliente ?? '' },
      { id: 'asignadoPor', header: 'Asignado por', size: 120, accessorFn: (r) => r.nombreTecnicoAsigna ?? '—' },
      { id: 'estado', header: 'Estado', size: 100, cell: ({ row }) => <BadgesEstadoPendiente rep={row.original} hoy={hoy} /> },
      { id: 'accion', header: '', size: 150, cell: ({ row }) => <BotonAnadir rep={row.original} glass={glass} /> },
    ]
    if (esSuper) base.push({ id: 'borrar', header: '', size: 45, cell: ({ row }) => <BotonPapelera onClick={() => setABorrar(row.original)} /> })
    return base
  }, [esSuper, glass, hoy])

  useRegistrarExportable(() => {
    const cabeceras = ['ID Reparación', 'IMEI', ...(esSuper ? ['Técnico'] : []), 'Fecha asig.', 'Fecha fin', 'Componente', 'Observaciones', 'Incidencia', 'Resuelto', 'ID Rep. anterior']
    const filas = visibles.map((r) => [
      r.idRep, textoForzado(r.imei), ...(esSuper ? [r.nombreTecnico ?? ''] : []),
      formatear(r.fechaAsig, FMT_CSV), formatear(r.fechaFin, FMT_CSV), r.tipoComponente ?? '', r.observaciones ?? '',
      r.esIncidencia ? (r.incidencia ?? 'Sí') : 'No', r.esResuelto ? 'Sí' : 'No', r.idRepAnterior ?? '',
    ])
    descargarCsv('mis_pendientes', cabeceras, filas)
  })

  const acciones = {
    porCerrar: (r: ReparacionResumen) => porCerrar.mutate({ idRep: r.idRep, porCerrar: !r.porCerrar }),
    entrega: (r: ReparacionResumen) => entrega.mutate({ idRep: r.idRep, entregado: r.glassEntregadoAt === null }),
    llegada: (r: ReparacionResumen) => llegada.mutate(r.idRep),
    deshacerLlegada: (r: ReparacionResumen) => deshacerLlegada.mutate(r.idRep),
  }

  function confirmarBorrado() {
    if (!aBorrar) return
    const rep = aBorrar
    setABorrar(null)
    if (rep.esIncidencia) borrarIncidencia.mutate({ imei: rep.imei, tipo: glass ? 'G' : 'R' })
    else borrarAsignacion.mutate(rep.idRep)
  }

  return (
    <div className="p-10">
      <TogglesPendientes />
      <div className="mb-3 flex items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Mis asignaciones pendientes</h1>
        <PildoraContador texto={etiquetaContador(visibles.length, 'pendiente', 'pendientes', 999)} />
      </div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <FiltroImei valor={filtroImei} onChange={setFiltroImei} />
        <MultiSelect
          opciones={OPCIONES_TIPO}
          clave={(o) => o.clave}
          etiqueta={(o) => o.etiqueta}
          seleccion={tipos as Set<string>}
          onChange={(s) => setTipos(s as Set<TipoPendiente>)}
          textoVacio="Tipo"
          textoPlural={(n) => `${n} filtros`}
          textoTodas="Todas"
          className="min-w-[130px]"
        />
        <BotonSecundario onClick={() => { setFiltroImei(''); setTipos(new Set()) }}>Limpiar filtros</BotonSecundario>
      </div>
      <DataTable
        columns={columnas}
        data={visibles}
        vacio="No tienes asignaciones pendientes"
        getRowId={(r) => r.idRep}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        filaClase={(r) => cn('border-l-8', r.esSolicitud > 0 ? 'border-l-fila-solicitud-brd' : r.esIncidencia ? 'border-l-fila-incidencia-brd' : 'border-l-transparent')}
        menuFila={(r, celda) => <MenuPendiente rep={r} celda={celda} glass={glass} idTec={idTec} acciones={acciones} />}
      />
      <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} />
      <ConfirmDialog
        abierto={aBorrar !== null}
        titulo={`Borrar asignación ${aBorrar?.idRep ?? ''}`}
        descripcion={aBorrar?.esIncidencia ? 'El técnico dejará de verla en su lista de pendientes y la incidencia se marcará como no activa en la tabla principal.' : 'El técnico dejará de verla en su lista de pendientes.'}
        textoAccion="Borrar asignación"
        onCancelar={() => setABorrar(null)}
        onConfirmar={confirmarBorrado}
      />
    </div>
  )
}
```

`router.tsx`: sustituir los dos placeholders por `<PendientesPage tipo="REPARACION" />` y `<PendientesPage tipo="GLASS" />` (import de `@/modules/taller/pendientes/PendientesPage`).

- [ ] **Step 5: Verde y commit**

```bash
npm run check
git add public/borrar.png public/editar.png public/Historial.png src/shared/ui/Botones.tsx src/shared/ui/PildoraContador.tsx src/modules/taller src/app/router.tsx
git commit -m "feat(web): Pendientes (reparaciones y glass): filtros, badges de estado, entrega a glass, papelera y CSV"
```

---

### Task 15: Web — Pendientes: Pulidos

**Files:**
- Create: `src/modules/taller/pendientes/PulidosPendientesPage.tsx`, `PulidosPendientesPage.test.tsx`
- Modify: `src/app/router.tsx` (placeholder de `/reparaciones/pendientes/pulidos`)

**Interfaces:**
- Consumes: `useAsignaciones('PULIDO')`, `useCompletarPulidos`, `useBorrarAsignacionPulido`, `TogglesPendientes`, `BotonPapelera`, `filtroImeiPendientes`.

- [ ] **Step 1: Tests (fallan)**

`src/modules/taller/pendientes/PulidosPendientesPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER, SESION_TEC } from '@/test/render'
import { reiniciarEstadoTaller } from '../estado'
import { resumen } from '../test/fabrica'
import { PulidosPendientesPage } from './PulidosPendientesPage'

const filas = [
  resumen({ idRep: 'AP20260916_1', imei: '351200000000021', modelo: '13', comentarioAsignacion: 'rayado', nombreTecnicoAsigna: 'Técnico F' }),
  resumen({ idRep: 'AP20260916_2', imei: '358300000000121', modelo: null, cliente: null, nombreTecnicoAsigna: null }),
]

beforeEach(() => {
  reiniciarEstadoTaller()
  server.use(
    http.get('*/api/pulidos/asignaciones', () => HttpResponse.json(filas)),
    http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 2 })),
  )
})
const abrir = (sesion = SESION_TEC) => renderConProviders(<PulidosPendientesPage />, { sesion, ruta: '/reparaciones/pendientes/pulidos' })

describe('PulidosPendientesPage (ficha docs/paridad/pendientes.md, pestaña Pulidos)', () => {
  it('título, columnas y "—" sin asignador', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Mis pulidos pendientes' })).toBeInTheDocument()
    expect(screen.getByText('2 pendientes')).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['', 'Id Asignación', 'IMEI', 'Modelo', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por'])
    expect(within(screen.getByRole('row', { name: /AP20260916_2/ })).getByText('—')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Completar seleccionados' })).toBeDisabled()
  })
  it('seleccionar todo, completar en lote y limpiar la selección', async () => {
    let ids: unknown = null
    server.use(http.post('*/api/pulidos/asignaciones/completar-lote', async ({ request }) => { ids = await request.json(); return new HttpResponse(null, { status: 204 }) }))
    abrir()
    await screen.findByText('AP20260916_1')
    await userEvent.click(screen.getByRole('button', { name: 'Seleccionar todo' }))
    expect(screen.getAllByRole('checkbox').every((c) => (c as HTMLInputElement).checked || c.getAttribute('aria-checked') === 'true')).toBe(true)
    const completar = screen.getByRole('button', { name: 'Completar seleccionados' })
    expect(completar).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Seleccionar todo' }))
    expect(completar).toBeDisabled()
    await userEvent.click(screen.getAllByRole('checkbox')[1])
    await userEvent.click(completar)
    await waitFor(() => expect(ids).toEqual({ ids: ['AP20260916_2'] }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Completar seleccionados' })).toBeDisabled())
  })
  it('la papelera del supertécnico borra la asignación de pulido con su texto', async () => {
    let borrado = false
    server.use(http.delete('*/api/pulidos/asignaciones/AP20260916_1', () => { borrado = true; return new HttpResponse(null, { status: 204 }) }))
    abrir(SESION_SUPER)
    await screen.findByText('AP20260916_1')
    await userEvent.click(screen.getAllByRole('button', { name: 'Borrar asignación' })[0])
    const dlg = screen.getByRole('dialog', { name: 'Borrar asignación AP20260916_1' })
    expect(within(dlg).getByText('El pulido dejará de estar asignado y desaparecerá de tus pendientes.')).toBeInTheDocument()
    await userEvent.click(within(dlg).getByRole('button', { name: 'Borrar asignación' }))
    await waitFor(() => expect(borrado).toBe(true))
  })
  it('menú solo con Copiar celda; placeholder vacío', async () => {
    abrir()
    await screen.findByText('AP20260916_1')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('AP20260916_1') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda'])
    await userEvent.keyboard('{Escape}')
    server.use(http.get('*/api/pulidos/asignaciones', () => HttpResponse.json([])))
    renderConProviders(<PulidosPendientesPage />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/pulidos' })
    expect(await screen.findByText('No tienes pulidos pendientes')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Implementar**

`src/modules/taller/pendientes/PulidosPendientesPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import type { ReparacionResumen } from '@/shared/api/client'
import { descargarCsv, textoForzado } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'
import { imeisValidos } from '@/shared/lib/filtroImei'
import { useStore } from '@/shared/lib/store'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { Checkbox } from '@/shared/ui/checkbox'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { FiltroImei } from '@/shared/ui/FiltroImei'
import { MenuCopiarCelda } from '@/shared/ui/MenuCopiarCelda'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { useAsignaciones, useBorrarAsignacionPulido, useCompletarPulidos } from '../api'
import { BotonPapelera } from '../componentes/BotonPapelera'
import { TogglesPendientes } from '../componentes/TogglesPendientes'
import { filtroImeiPendientes } from '../estado'
import { etiquetaContador, pasaImeis } from '../lib/filtros'
import { traducirModelo } from '../lib/modelos'
import { FMT_PENDIENTES } from './textoCelda'

function textoCelda(rep: ReparacionResumen, columna: string): string | null {
  switch (columna) {
    case 'id': return rep.idRep
    case 'imei': return rep.imei
    case 'modelo': return traducirModelo(rep.modelo)
    case 'fecha': return formatear(rep.fechaAsig, FMT_PENDIENTES)
    case 'comentario': return rep.comentarioAsignacion ?? ''
    case 'cliente': return rep.cliente ?? ''
    case 'asignadoPor': return rep.nombreTecnicoAsigna ?? ''
    default: return null
  }
}

export function PulidosPendientesPage() {
  const { sesion } = useSession()
  const esSuper = esSuperTecnico(sesion)
  const { data = [], dataUpdatedAt, refetch } = useAsignaciones('PULIDO')
  const [filtroImei, setFiltroImei] = useStore(filtroImeiPendientes)
  const [seleccionados, setSeleccionados] = useState<Set<string>>(new Set())
  const [seleccionada, setSeleccionada] = useState<string | null>(null)
  const [aBorrar, setABorrar] = useState<ReparacionResumen | null>(null)
  const completar = useCompletarPulidos()
  const borrar = useBorrarAsignacionPulido()

  const visibles = useMemo(() => { const imeis = imeisValidos(filtroImei); return data.filter((r) => pasaImeis(r.imei, imeis)) }, [data, filtroImei])

  function marcar(id: string, marcado: boolean) {
    setSeleccionados((prev) => { const s = new Set(prev); if (marcado) s.add(id); else s.delete(id); return s })
  }
  function seleccionarTodo() {
    setSeleccionados((prev) => (data.length > 0 && prev.size === data.length ? new Set() : new Set(data.map((r) => r.idRep))))
  }
  function completarSeleccionados() {
    if (seleccionados.size === 0) return
    const ids = [...seleccionados]
    setSeleccionados(new Set())
    completar.mutate(ids)
  }

  const columnas = useMemo<ColumnDef<ReparacionResumen>[]>(() => {
    const base: ColumnDef<ReparacionResumen>[] = [
      { id: 'check', header: '', size: 40, cell: ({ row }) => <Checkbox aria-label={`Seleccionar ${row.original.idRep}`} checked={seleccionados.has(row.original.idRep)} onCheckedChange={(v) => marcar(row.original.idRep, v === true)} /> },
      { id: 'id', accessorKey: 'idRep', header: 'Id Asignación', size: 90 },
      { id: 'imei', accessorKey: 'imei', header: 'IMEI', size: 130 },
      { id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => traducirModelo(r.modelo) },
      { id: 'fecha', header: 'Fecha asignación', size: 130, accessorFn: (r) => formatear(r.fechaAsig, FMT_PENDIENTES) },
      { id: 'comentario', header: 'Comentario', size: 160, accessorFn: (r) => r.comentarioAsignacion ?? '' },
      { id: 'cliente', header: 'Cliente', size: 110, accessorFn: (r) => r.cliente ?? '' },
      { id: 'asignadoPor', header: 'Asignado por', size: 120, accessorFn: (r) => r.nombreTecnicoAsigna ?? '—' },
    ]
    if (esSuper) base.push({ id: 'borrar', header: '', size: 50, cell: ({ row }) => <BotonPapelera onClick={() => setABorrar(row.original)} /> })
    return base
  }, [esSuper, seleccionados])

  useRegistrarExportable(() => {
    descargarCsv('pulidos_pendientes', ['ID', 'IMEI', 'Modelo', 'Fecha asig.', 'Comentario'],
      visibles.map((r) => [r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'), r.comentarioAsignacion ?? '']))
  })

  return (
    <div className="p-10">
      <TogglesPendientes />
      <div className="mb-3 flex items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Mis pulidos pendientes</h1>
        <PildoraContador texto={etiquetaContador(visibles.length, 'pendiente', 'pendientes', 999)} />
      </div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <FiltroImei valor={filtroImei} onChange={setFiltroImei} />
        <BotonSecundario onClick={() => setFiltroImei('')}>Limpiar filtros</BotonSecundario>
        <BotonSecundario className="ml-6" onClick={seleccionarTodo}>Seleccionar todo</BotonSecundario>
        <BotonPrimario disabled={seleccionados.size === 0} onClick={completarSeleccionados}>Completar seleccionados</BotonPrimario>
      </div>
      <DataTable
        columns={columnas}
        data={visibles}
        vacio="No tienes pulidos pendientes"
        getRowId={(r) => r.idRep}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        menuFila={(r, celda) => <MenuCopiarCelda texto={textoCelda(r, celda.columnaId)} celda={celda} />}
      />
      <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} />
      <ConfirmDialog
        abierto={aBorrar !== null}
        titulo={`Borrar asignación ${aBorrar?.idRep ?? ''}`}
        descripcion="El pulido dejará de estar asignado y desaparecerá de tus pendientes."
        textoAccion="Borrar asignación"
        onCancelar={() => setABorrar(null)}
        onConfirmar={() => { if (aBorrar) borrar.mutate(aBorrar.idRep); setABorrar(null) }}
      />
    </div>
  )
}
```
`router.tsx`: `/reparaciones/pendientes/pulidos` → `<PulidosPendientesPage />`. La selección se vacía al recargar los datos: añadir en la página `useEffect(() => setSeleccionados(new Set()), [dataUpdatedAt])` con el comentario `// calco de cargar(): la selección no sobrevive a una recarga` (y el `eslint-disable-next-line react-hooks/set-state-in-effect` correspondiente).

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/modules/taller src/app/router.tsx
git commit -m "feat(web): Pendientes: pulidos (seleccion, completar en lote, papelera y CSV)"
```

---

### Task 16: Web — Historial: Reparaciones y Glass

**Files:**
- Create: `src/modules/taller/componentes/CeldaReparador.tsx`, `CeldaIncidencia.tsx`, `CeldaEstadoTrabajo.tsx`, `DialogoIncidencia.tsx`, `MenuHistorial.tsx`, `useAccionesTrabajo.tsx`, `src/modules/taller/historial/columnasTrabajo.tsx`, `csvTrabajos.ts`, `HistorialPage.tsx`, `HistorialPage.test.tsx`
- Modify: `src/shared/ui/AlertaProvider.tsx` (+`mostrarAviso(titulo, mensaje)`), `AlertaProvider.test.tsx`, `src/app/router.tsx`

**Interfaces:**
- Consumes: `useHistorial`, `useTecnicos`, `useBorrarReparacion`, `useAnadirIncidencia`, `useCancelarIncidencia`, `referenciadora` (Task 13); stores `filtroImeiHistorial`, `filtrosHistorial[tipo]` (Task 13); `pasaFechas`, `pasaIncidencias`, `pasaPieza`, `pasaTecnico`, `pasaImeis`, `estadoIncidencia` (Task 12); `categoriaPieza`, `subEtiquetaHistorial`, `tooltipEntrega` (Task 11); `CeldaFechas`, `TextoExpandible`, `RangoFechas`, `MultiSelect`, `ConfirmDialog` con motivo (Task 10).
- Produces (reutilizados por IMEIs, Task 18): `columnasTrabajo(opciones): ColumnDef<ReparacionResumen>[]`, `textoCeldaTrabajo(rep, columna, patronFechas)`, `cabecerasHistorial(conTecnico)`, `filaHistorial(rep, conTecnico)`, `<MenuHistorial />` con `AccionesHistorial`, `useAccionesTrabajo({ tituloBorrar, avisoReferencia }): { acciones, dialogos }`, `TITULOS_BORRAR`, `descripcionBorrado(idRep)`, `OPCIONES_INCIDENCIAS`, `<DialogoIncidencia />`, `<CeldaReparador />`, `<CeldaIncidencia />`, `<CeldaEstadoTrabajo />`, `useAlerta().mostrarAviso(titulo, mensaje)`.

- [ ] **Step 1: `mostrarAviso` en `AlertaProvider` (test primero)**

En `AlertaProvider.test.tsx` añadir:

```tsx
  it('mostrarAviso abre el diálogo con un título propio', async () => {
    function Vista() { const { mostrarAviso } = useAlerta(); return <button onClick={() => mostrarAviso('No se puede borrar', 'La reparación R1 apunta a esta. Bórrala primero.')}>ir</button> }
    render(<AlertaProvider><Vista /></AlertaProvider>)
    await userEvent.click(screen.getByRole('button', { name: 'ir' }))
    expect(screen.getByRole('dialog', { name: 'No se puede borrar' })).toHaveTextContent('La reparación R1 apunta a esta. Bórrala primero.')
  })
```
Implementación: el estado pasa a `{ titulo: string; msg: string } | null`; `mostrarError(m)` = `setAviso({ titulo: 'Error', msg: m })`; nuevo `mostrarAviso(titulo, msg)`; el `DialogTitle` pinta `aviso.titulo`. Exponer ambos en el contexto (`useAlerta(): { mostrarError; mostrarAviso }`).

- [ ] **Step 2: Tests de la página (fallan)**

`src/modules/taller/historial/HistorialPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import * as csv from '@/shared/lib/csv'
import { reiniciarEstadoTaller } from '../estado'
import { resumen, tecnico } from '../test/fabrica'
import { HistorialPage } from './HistorialPage'

const filas = [
  resumen({ idRep: 'R20260916_6', imei: '358800000000131', modelo: '14', nombreTecnico: 'tecnico_i', idTec: 5, nombreTecnicoAsigna: 'Técnico M', fechaAsig: '2026-09-16T07:00:00', fechaFin: '2026-09-16T07:00:00', tipoComponente: 'otroi14', observaciones: 'cerrar' }),
  resumen({ idRep: 'R20260915_133', imei: '351900000000041', modelo: '16', nombreTecnico: 'tecnico_b', idTec: 6, fechaAsig: '2026-09-15T15:00:00', fechaFin: '2026-09-15T15:00:00', tipoComponente: 'bati16', esReutilizado: true, esIncidencia: true, incidencia: 'no enciende' }),
  resumen({ idRep: 'R20260910_1', imei: '351900000000041', modelo: '16', nombreTecnico: 'tecnico_i', idTec: 5, fechaAsig: '2026-09-10T10:00:00', fechaFin: '2026-09-11T10:00:00', tipoComponente: 'lcdi16negra', esIncidencia: true, esResuelto: true, incidencia: 'pantalla', idRepAnterior: 'R20260916_6' }),
]
const tecnicos = [tecnico({ idTec: 5, nombre: 'tecnico_i' }), tecnico({ idTec: 6, nombre: 'tecnico_b' }), tecnico({ idTec: 7, nombre: 'tecnico_n', activo: false })]

beforeEach(() => {
  reiniciarEstadoTaller()
  server.use(
    http.get('*/api/reparaciones/historial', () => HttpResponse.json(filas)),
    http.get('*/api/glass/historial', () => HttpResponse.json([])),
    http.get('*/api/tecnicos', () => HttpResponse.json(tecnicos)),
    http.get('*/api/tecnicos/activos', () => HttpResponse.json(tecnicos.filter((t) => t.activo))),
  )
})
const abrir = (sesion = SESION_SUPER) => renderConProviders(<HistorialPage tipo="REPARACION" />, { sesion, ruta: '/reparaciones/historial' })

describe('HistorialPage (ficha docs/paridad/historial.md)', () => {
  it('título por rol, contador, toggles y columnas estiradas', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Historial de reparaciones' })).toBeInTheDocument()
    expect(screen.getByText('3 reparaciones')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reparaciones' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Pulidos' })).toHaveAttribute('href', '/reparaciones/historial/pulidos')
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Id Reparación', 'IMEI teléfono', 'Modelo', 'Reparador', 'Asignado por', 'Fechas', 'Componente', 'Observaciones', 'Estado', 'Incidencia', 'Id Rep. Anterior'])
    expect(screen.getByRole('table')).toHaveClass('w-full')
    expect(screen.getByText('Reutilizado')).toHaveClass('italic')
    expect(screen.getByText('2026/09/16')).toBeInTheDocument()
    expect(screen.getAllByText('Sin incidencia')).toHaveLength(1)
    expect(screen.getByText('Resuelta')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /R20260915_133/ })).toHaveClass('border-l-fila-incidencia-brd')
    expect(screen.getByRole('row', { name: /R20260910_1/ })).toHaveClass('border-l-fila-reparado-brd')
  })
  it('el técnico ve "Mis reparaciones" y no tiene filtro de técnico', async () => {
    abrir(SESION_TEC)
    expect(await screen.findByRole('heading', { name: 'Mis reparaciones' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Técnico' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pieza' })).toBeInTheDocument()
  })
  it('filtros Técnico (todos, incluidos inactivos), Pieza (categorías presentes), fechas e incidencias', async () => {
    abrir()
    await screen.findByText('R20260916_6')
    await userEvent.click(screen.getByRole('button', { name: 'Técnico' }))
    expect(screen.getByRole('checkbox', { name: 'tecnico_n' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'tecnico_i' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'tecnico_i' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(3)
    await userEvent.click(screen.getByRole('button', { name: 'Pieza' }))
    expect(screen.getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'))).toEqual(['Batería', 'Otros', 'Pantalla'])
    await userEvent.click(screen.getByRole('checkbox', { name: 'Pantalla' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByText('1 reparación')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getAllByRole('row')).toHaveLength(4)
    await userEvent.type(screen.getByLabelText('Desde:'), '2026-09-16')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await userEvent.click(screen.getByRole('button', { name: 'Incidencias' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Cerradas' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Cerradas' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(2)
  })
  it('el enlace Id Rep. Anterior selecciona esa fila', async () => {
    abrir()
    await screen.findByText('R20260916_6')
    await userEvent.click(screen.getByRole('button', { name: 'R20260916_6' }))
    expect(screen.getByRole('row', { name: /^R20260916_6 / })).toHaveAttribute('aria-selected', 'true')
  })
  it('menú del supertécnico y diálogo "Borrar reparación" con motivo; referenciada avisa y no abre', async () => {
    let borrado: unknown = null
    server.use(
      http.get('*/api/reparaciones/R20260915_133/referenciadora', () => HttpResponse.json({ value: null })),
      http.get('*/api/reparaciones/R20260916_6/referenciadora', () => HttpResponse.json({ value: 'R20260910_1' })),
      http.delete('*/api/reparaciones/R20260915_133', async ({ request }) => { borrado = await request.json(); return new HttpResponse(null, { status: 204 }) }),
    )
    abrir()
    await screen.findByText('R20260916_6')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('R20260915_133') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['Editar', 'Borrar', '📋  Copiar celda', 'Cancelar incidencia'])
    expect(screen.getByRole('menuitem', { name: 'Editar' })).toHaveAttribute('aria-disabled', 'true')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Borrar' }))
    const dlg = await screen.findByRole('dialog', { name: 'Borrar reparación' })
    expect(within(dlg).getByText('Se borrará R20260915_133. Los componentes usados volverán a stock y, si resolvía una incidencia, esta quedará activa de nuevo. Escribe el motivo.')).toBeInTheDocument()
    await userEvent.type(within(dlg).getByPlaceholderText('Escribe el motivo del borrado...'), 'duplicada')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Borrar reparación' }))
    await waitFor(() => expect(borrado).toEqual({ motivo: 'duplicada' }))
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('R20260916_6') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Borrar' }))
    expect(await screen.findByRole('dialog', { name: 'No se puede borrar' })).toHaveTextContent('Esta reparación está siendo referenciada. La reparación R20260910_1 apunta a esta. Bórrala primero.')
  })
  it('"Añadir incidencia": técnicos activos, reparador preseleccionado, botón habilitado con comentario y POST', async () => {
    let body: unknown = null
    server.use(http.post('*/api/reparaciones/R20260916_6/incidencia', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 201 }) }))
    abrir()
    await screen.findByText('R20260916_6')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('R20260916_6') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Añadir incidencia' }))
    const dlg = await screen.findByRole('dialog', { name: 'Añadir incidencia' })
    const boton = within(dlg).getByRole('button', { name: 'Añadir incidencia y asignar' })
    expect(boton).toBeDisabled()
    expect(within(dlg).getByLabelText('Técnico asignado')).toHaveValue('5')
    expect(within(dlg).queryByRole('option', { name: 'tecnico_n' })).not.toBeInTheDocument()
    await userEvent.type(within(dlg).getByLabelText('Comentario de incidencia'), 'sigue sin cargar')
    expect(boton).toBeEnabled()
    await userEvent.click(boton)
    await waitFor(() => expect(body).toEqual({ comentario: 'sigue sin cargar', imei: '358800000000131', idTec: 5 }))
  })
  it('el admin y el técnico solo tienen "Copiar celda"', async () => {
    abrir(SESION_ADMIN)
    await screen.findByText('R20260916_6')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('R20260916_6') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda'])
  })
  it('CSV del supertécnico con columna Técnico y nombre historial_reparaciones', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    abrir()
    await screen.findByText('R20260916_6')
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    const [base, cabeceras, filasCsv] = descargar.mock.calls[0]
    expect(base).toBe('historial_reparaciones')
    expect(cabeceras).toEqual(['ID Reparación', 'IMEI', 'Técnico', 'Fecha asig.', 'Fecha fin', 'Componente', 'Reutilizado', 'Observaciones', 'Incidencia', 'Resuelto', 'ID Rep. anterior'])
    expect(filasCsv[1]).toEqual(['R20260915_133', '="351900000000041"', 'tecnico_b', '15/09/2026 17:00', '15/09/2026 17:00', 'bati16', 'Sí', '', 'no enciende', 'No', ''])
    descargar.mockRestore()
  })
})
```
(Como en la Task 14, el test del CSV monta `<AppLayout />` con la ruta de la página para tener el menú de usuario.)

- [ ] **Step 3: Componentes de trabajo (compartidos con IMEIs)**

`src/modules/taller/componentes/CeldaReparador.tsx`:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { subEtiquetaHistorial, tooltipEntrega } from '../lib/entregaGlass'

/** Calco de CeldaReparador: nombre y, en glass con entrega, "Llegó dd/MM HH:mm" debajo. */
export function CeldaReparador({ rep }: { rep: ReparacionResumen }) {
  const sub = subEtiquetaHistorial(rep)
  return (
    <div className="flex flex-col leading-tight">
      <span>{rep.nombreTecnico ?? ''}</span>
      {sub && <span title={tooltipEntrega(rep) ?? undefined} className="text-[10px] text-texto-sub">{sub}</span>}
    </div>
  )
}
```

`src/modules/taller/componentes/CeldaEstadoTrabajo.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { CLASES_PILDORA } from '@/shared/ui/pildora'

/** Estado de un trabajo del historial: Incidencia (abierta) / Resuelta / Normal. */
export function CeldaEstadoTrabajo({ esIncidencia, esResuelto }: { esIncidencia: boolean; esResuelto: boolean }) {
  if (esIncidencia && !esResuelto) return <span className={cn(CLASES_PILDORA, 'bg-fila-incidencia-bg text-fila-incidencia-brd')}>Incidencia</span>
  if (esIncidencia) return <span className={cn(CLASES_PILDORA, 'bg-fila-reparado-bg text-fila-reparado-ico')}>Resuelta</span>
  return <span className={cn(CLASES_PILDORA, 'bg-badge-neutro-bg text-azul-gris')}>Normal</span>
}
```

`src/modules/taller/componentes/CeldaIncidencia.tsx`:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { TextoExpandible } from '@/shared/ui/TextoExpandible'

/** "Sin incidencia" en cursiva gris; el texto en negro si está abierta o en gris sobre verde claro si está resuelta (clic → popup). */
export function CeldaIncidencia({ rep }: { rep: ReparacionResumen }) {
  if (!rep.esIncidencia) return <span className="text-[12px] italic text-texto-vacio">Sin incidencia</span>
  return (
    <div className={rep.esResuelto ? '-m-2 bg-fila-reparado-bg p-2' : undefined}>
      <TextoExpandible titulo="Incidencia" texto={rep.incidencia} className={rep.esResuelto ? 'text-gris-borde' : 'text-black'} />
    </div>
  )
}
```

`src/modules/taller/componentes/DialogoIncidencia.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import type { ReparacionResumen } from '@/shared/api/client'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { useTecnicos } from '../api'

type Props = { rep: ReparacionResumen | null; onGuardar: (comentario: string, idTec: number) => void; onCerrar: () => void }

/** Calco de abrirDialogoIncidencia: comentario, técnico asignado (activos, preseleccionado el reparador) y botón que
 *  solo se habilita con ambos. */
export function DialogoIncidencia({ rep, onGuardar, onCerrar }: Props) {
  const { data: tecnicos = [] } = useTecnicos(true)
  const [comentario, setComentario] = useState('')
  const [idTec, setIdTec] = useState<string>('')
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reinicia el formulario al abrir con otra fila
    if (rep) { setComentario(rep.incidencia ?? ''); setIdTec(String(rep.idTec)) }
  }, [rep])
  const listo = comentario.trim() !== '' && idTec !== ''
  return (
    <Dialog open={rep !== null} onOpenChange={(o) => !o && onCerrar()}>
      <DialogContent aria-describedby={undefined} className="max-w-[520px] gap-2 bg-fondo-input p-4">
        <DialogHeader><DialogTitle className="text-[14px] font-bold text-azul-medio">Añadir incidencia</DialogTitle></DialogHeader>
        <Label htmlFor="incidencia-comentario" className="text-[12px]">Comentario de incidencia</Label>
        <textarea id="incidencia-comentario" value={comentario} onChange={(e) => setComentario(e.target.value)} placeholder="Describe la incidencia..." rows={4} className="w-full rounded border border-gris-borde bg-superficie p-2 text-[13px]" />
        <Label htmlFor="incidencia-tecnico" className="text-[12px]">Técnico asignado</Label>
        <select id="incidencia-tecnico" value={idTec} onChange={(e) => setIdTec(e.target.value)} className="h-9 w-full rounded border border-gris-borde bg-superficie px-2 text-[13px]">
          <option value="">Selecciona técnico</option>
          {tecnicos.map((t) => <option key={t.idTec} value={String(t.idTec)}>{t.nombre}</option>)}
        </select>
        <Button disabled={!listo} onClick={() => onGuardar(comentario.trim(), Number(idTec))} className="h-auto w-full rounded bg-fila-reparado-ico py-2 text-[12px] text-superficie hover:bg-fila-reparado-ico/90 disabled:bg-gris-disabled disabled:text-gris-borde disabled:opacity-100">
          Añadir incidencia y asignar
        </Button>
        <DialogFooter><Button variant="outline" onClick={onCerrar}>Cerrar</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

`src/modules/taller/componentes/MenuHistorial.tsx`:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { tipoDe } from '@/shared/lib/tipoTrabajo'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'
import type { CeldaPulsada } from '@/shared/ui/DataTable'
import { MenuCopiarCelda } from '@/shared/ui/MenuCopiarCelda'
import { TOOLTIP_FORMULARIO } from '../lib/textos'

export type AccionesHistorial = {
  borrar: (rep: ReparacionResumen) => void
  anadirIncidencia: (rep: ReparacionResumen) => void
  cancelarIncidencia: (rep: ReparacionResumen) => void
}

/** Menú contextual del Historial y del detalle de IMEIs: Editar (R/G, deshabilitado hasta el SP2), Borrar, Copiar celda,
 *  Añadir incidencia (si no tiene), Cancelar incidencia (si está abierta). Sin permisos de edición: solo Copiar celda. */
export function MenuHistorial({ rep, celda, texto, puedeEditar, acciones }: { rep: ReparacionResumen; celda: CeldaPulsada; texto: string | null; puedeEditar: boolean; acciones: AccionesHistorial }) {
  if (!puedeEditar) return <MenuCopiarCelda texto={texto} celda={celda} />
  const tipo = tipoDe(rep.idRep)
  const editable = tipo === 'REPARACION' || tipo === 'GLASS'
  const abierta = rep.esIncidencia && !rep.esResuelto
  return (
    <>
      {editable && <ContextMenuItem disabled title={TOOLTIP_FORMULARIO}>Editar</ContextMenuItem>}
      <ContextMenuItem onSelect={() => acciones.borrar(rep)}>Borrar</ContextMenuItem>
      <ContextMenuSeparator />
      <MenuCopiarCelda texto={texto} celda={celda} />
      {(!rep.esIncidencia || abierta) && <ContextMenuSeparator />}
      {!rep.esIncidencia && <ContextMenuItem onSelect={() => acciones.anadirIncidencia(rep)}>Añadir incidencia</ContextMenuItem>}
      {abierta && <ContextMenuItem onSelect={() => acciones.cancelarIncidencia(rep)}>Cancelar incidencia</ContextMenuItem>}
    </>
  )
}
```

`src/modules/taller/componentes/useAccionesTrabajo.tsx`:

```tsx
import { useState, type ReactNode } from 'react'
import type { ReparacionResumen } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { referenciadora, useAnadirIncidencia, useBorrarReparacion, useCancelarIncidencia } from '../api'
import { DialogoIncidencia } from './DialogoIncidencia'
import type { AccionesHistorial } from './MenuHistorial'

export const TITULOS_BORRAR = { historial: 'Borrar reparación', trabajo: 'Borrar trabajo' } as const

export function descripcionBorrado(idRep: string): string {
  return `Se borrará ${idRep}. Los componentes usados volverán a stock y, si resolvía una incidencia, esta quedará activa de nuevo. Escribe el motivo.`
}

type Opciones = {
  /** Título y texto del botón del diálogo de borrado: "Borrar reparación" (Historial) o "Borrar trabajo" (detalle de IMEIs) */
  tituloBorrar: string
  /** Primera frase del aviso de referencia: "Esta reparación está siendo referenciada" o "Este trabajo está siendo referenciado" */
  avisoReferencia: string
}

/** Acciones del menú de un trabajo del historial y sus diálogos, compartidas por HistorialPage e ImeiDetallePage:
 *  borrar (comprobación de referencia → aviso "No se puede borrar", o ConfirmDialog con motivo → DELETE),
 *  añadir incidencia (DialogoIncidencia → POST; "No se pudo guardar: <msg>" si falla) y cancelar incidencia (ConfirmDialog → DELETE). */
export function useAccionesTrabajo({ tituloBorrar, avisoReferencia }: Opciones): { acciones: AccionesHistorial; dialogos: ReactNode } {
  const { mostrarError, mostrarAviso } = useAlerta()
  const [aBorrar, setABorrar] = useState<ReparacionResumen | null>(null)
  const [aCancelar, setACancelar] = useState<ReparacionResumen | null>(null)
  const [conIncidencia, setConIncidencia] = useState<ReparacionResumen | null>(null)
  const borrar = useBorrarReparacion()
  const anadir = useAnadirIncidencia()
  const cancelar = useCancelarIncidencia()

  async function pedirBorrado(rep: ReparacionResumen) {
    try {
      const ref = await referenciadora(rep.idRep)
      if (ref) {
        mostrarAviso('No se puede borrar', `${avisoReferencia}. La reparación ${ref} apunta a esta. Bórrala primero.`)
        return
      }
      setABorrar(rep)
    } catch (e) {
      if (!esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e))
    }
  }

  const acciones: AccionesHistorial = { borrar: (r) => void pedirBorrado(r), anadirIncidencia: setConIncidencia, cancelarIncidencia: setACancelar }
  const dialogos = (
    <>
      <ConfirmDialog abierto={aBorrar !== null} conMotivo titulo={tituloBorrar} descripcion={aBorrar ? descripcionBorrado(aBorrar.idRep) : ''} textoAccion={tituloBorrar}
        onCancelar={() => setABorrar(null)} onConfirmar={(motivo) => { if (aBorrar && motivo) borrar.mutate({ idRep: aBorrar.idRep, motivo }); setABorrar(null) }} />
      <ConfirmDialog abierto={aCancelar !== null} titulo="Borrar incidencia" descripcion="Esta acción solo es válida si fue un error al añadirla." textoAccion="Borrar incidencia"
        onCancelar={() => setACancelar(null)} onConfirmar={() => { if (aCancelar) cancelar.mutate(aCancelar.idRep); setACancelar(null) }} />
      <DialogoIncidencia rep={conIncidencia} onCerrar={() => setConIncidencia(null)}
        onGuardar={(comentario, idTec) => {
          if (!conIncidencia) return
          const rep = conIncidencia
          setConIncidencia(null)
          anadir.mutate({ idRep: rep.idRep, comentario, imei: rep.imei, idTec }, { onError: (e) => { if (!esErrorGestionadoGlobalmente(e)) mostrarError(`No se pudo guardar: ${mensajeDeError(e)}`) } })
        }} />
    </>
  )
  return { acciones, dialogos }
}
```
El motivo llega ya recortado desde `ConfirmDialog` (Task 10), que no habilita el botón con motivo vacío.

- [ ] **Step 4: Columnas, CSV y página**

`src/modules/taller/historial/columnasTrabajo.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { ReparacionResumen } from '@/shared/api/client'
import { formatear, type Patron } from '@/shared/lib/fechas'
import { BadgeTipo } from '@/shared/ui/BadgeTipo'
import { CeldaFechas } from '@/shared/ui/CeldaFechas'
import { TextoExpandible } from '@/shared/ui/TextoExpandible'
import { tipoDe } from '@/shared/lib/tipoTrabajo'
import { CeldaEstadoTrabajo } from '../componentes/CeldaEstadoTrabajo'
import { CeldaIncidencia } from '../componentes/CeldaIncidencia'
import { CeldaReparador } from '../componentes/CeldaReparador'
import { traducirModelo } from '../lib/modelos'

type Opciones = {
  /** Columna "Tipo" delante (detalle de IMEIs) */
  conTipo?: boolean
  /** Patrón de la columna Fechas: 'yyyy/MM/dd' en el Historial, 'yyyy/MM/dd HH:mm' en el detalle de IMEIs */
  patronFechas: Patron
  /** Título de la columna del ID: "Id Reparación" en el Historial, "Id" en el detalle */
  tituloId: string
  /** Enlace "Id Rep. Anterior": selecciona esa fila */
  onIrA: (idRep: string) => void
}

/** Columnas de una tabla de trabajos (Historial rep/glass y detalle de IMEIs), con los mínimos del FXML como pesos. */
export function columnasTrabajo({ conTipo = false, patronFechas, tituloId, onIrA }: Opciones): ColumnDef<ReparacionResumen>[] {
  const base: ColumnDef<ReparacionResumen>[] = [
    { id: 'id', accessorKey: 'idRep', header: tituloId, size: 110 },
    { id: 'imei', accessorKey: 'imei', header: 'IMEI teléfono', size: 130 },
    { id: 'modelo', header: 'Modelo', size: 100, accessorFn: (r) => traducirModelo(r.modelo) },
    { id: 'reparador', header: 'Reparador', size: 100, cell: ({ row }) => <CeldaReparador rep={row.original} /> },
    { id: 'asignadoPor', header: 'Asignado por', size: 100, accessorFn: (r) => r.nombreTecnicoAsigna ?? '—' },
    { id: 'fechas', header: 'Fechas', size: 110, cell: ({ row }) => <CeldaFechas inicio={row.original.fechaAsig} fin={row.original.fechaFin} patron={patronFechas} /> },
    {
      id: 'componente', header: 'Componente', size: 150,
      cell: ({ row }) => (
        <div className="flex flex-col leading-tight">
          <span>{row.original.tipoComponente ?? ''}</span>
          {row.original.esReutilizado && <span className="text-[10px] italic text-texto-fecha-inicio">Reutilizado</span>}
        </div>
      ),
    },
    { id: 'observaciones', header: 'Observaciones', size: 200, cell: ({ row }) => <TextoExpandible titulo="Observaciones" texto={row.original.observaciones} /> },
    { id: 'estado', header: 'Estado', size: 120, cell: ({ row }) => <CeldaEstadoTrabajo esIncidencia={row.original.esIncidencia} esResuelto={row.original.esResuelto} /> },
    { id: 'incidencia', header: 'Incidencia', size: 200, cell: ({ row }) => <CeldaIncidencia rep={row.original} /> },
    {
      id: 'anterior', header: 'Id Rep. Anterior', size: 150,
      cell: ({ row }) => {
        const r = row.original
        // el pulido guarda en ID_REP_ANTERIOR el enlace interno a su asignación, no una reincidencia
        if (!r.idRepAnterior || tipoDe(r.idRep) === 'PULIDO') return null
        return <button type="button" onClick={() => onIrA(r.idRepAnterior!)} className="cursor-pointer truncate text-texto-accion hover:underline">{r.idRepAnterior}</button>
      },
    },
  ]
  if (conTipo) base.unshift({ id: 'tipo', header: 'Tipo', size: 100, cell: ({ row }) => <BadgeTipo idRep={row.original.idRep} /> })
  return base
}

/** Texto de "Copiar celda" (calco de textoDeCelda del Historial y del Agrupado en detalle). */
export function textoCeldaTrabajo(rep: ReparacionResumen, columna: string, patronFechas: Patron): string | null {
  switch (columna) {
    case 'tipo': return tipoDe(rep.idRep) === 'GLASS' ? 'Glass' : tipoDe(rep.idRep) === 'PULIDO' ? 'Pulido' : 'Reparación'
    case 'id': return rep.idRep
    case 'imei': return rep.imei
    case 'modelo': return traducirModelo(rep.modelo)
    case 'reparador': return rep.nombreTecnico ?? ''
    case 'asignadoPor': return rep.nombreTecnicoAsigna ?? ''
    case 'fechas': return formatear(rep.fechaFin, patronFechas)
    case 'componente': return rep.tipoComponente ?? ''
    case 'observaciones': return rep.observaciones ?? ''
    case 'incidencia': return rep.incidencia ?? ''
    case 'anterior': return rep.idRepAnterior ?? ''
    default: return null
  }
}
```

`src/modules/taller/historial/csvTrabajos.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { textoForzado } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'

const FMT = 'dd/MM/yyyy HH:mm'

/** Cabeceras del CSV del Historial: el técnico exporta sin "Técnico"; supertécnico y admin con ella. */
export function cabecerasHistorial(conTecnico: boolean): string[] {
  return ['ID Reparación', 'IMEI', ...(conTecnico ? ['Técnico'] : []), 'Fecha asig.', 'Fecha fin', 'Componente', 'Reutilizado', 'Observaciones', 'Incidencia', 'Resuelto', 'ID Rep. anterior']
}

export function filaHistorial(r: ReparacionResumen, conTecnico: boolean): string[] {
  return [
    r.idRep, textoForzado(r.imei), ...(conTecnico ? [r.nombreTecnico ?? ''] : []),
    formatear(r.fechaAsig, FMT), formatear(r.fechaFin, FMT), r.tipoComponente ?? '', r.esReutilizado ? 'Sí' : 'No',
    r.observaciones ?? '', r.esIncidencia ? (r.incidencia ?? 'Sí') : 'No', r.esResuelto ? 'Sí' : 'No', r.idRepAnterior ?? '',
  ]
}
```

`src/modules/taller/historial/HistorialPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import { descargarCsv } from '@/shared/lib/csv'
import { imeisValidos } from '@/shared/lib/filtroImei'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdminOSuperTecnico, esSuperTecnico } from '@/shared/session/storage'
import { BotonSecundario } from '@/shared/ui/Botones'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { FiltroImei } from '@/shared/ui/FiltroImei'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { RangoFechas } from '@/shared/ui/RangoFechas'
import { TogglePill } from '@/shared/ui/TogglePill'
import { useHistorial, useTecnicos } from '../api'
import { MenuHistorial } from '../componentes/MenuHistorial'
import { TITULOS_BORRAR, useAccionesTrabajo } from '../componentes/useAccionesTrabajo'
import { filtroImeiHistorial, filtrosHistorial } from '../estado'
import { estadoIncidencia, etiquetaContador, pasaFechas, pasaImeis, pasaIncidencias, pasaPieza, pasaTecnico, type EstadoIncidencia } from '../lib/filtros'
import { categoriaPieza } from '../lib/piezas'
import { columnasTrabajo, textoCeldaTrabajo } from './columnasTrabajo'
import { cabecerasHistorial, filaHistorial } from './csvTrabajos'

export const TOGGLES_HISTORIAL = [
  { to: '/reparaciones/historial', etiqueta: 'Reparaciones' },
  { to: '/reparaciones/historial/glass', etiqueta: 'Glass' },
  { to: '/reparaciones/historial/pulidos', etiqueta: 'Pulidos' },
]
/** Las tres casillas del Historial y del detalle de IMEIs (el maestro de IMEIs usa otras dos, Task 18). */
export const OPCIONES_INCIDENCIAS: { clave: EstadoIncidencia; etiqueta: string }[] = [
  { clave: 'abiertas', etiqueta: 'Abiertas' },
  { clave: 'cerradas', etiqueta: 'Cerradas' },
  { clave: 'sin', etiqueta: 'Sin incidencia' },
]

export function HistorialPage({ tipo }: { tipo: 'REPARACION' | 'GLASS' }) {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const global = esAdminOSuperTecnico(sesion)
  const { data = [], dataUpdatedAt, refetch } = useHistorial(tipo)
  const { data: tecnicos = [] } = useTecnicos()
  const [filtroImei, setFiltroImei] = useStore(filtroImeiHistorial)
  const [filtros, setFiltros] = useStore(filtrosHistorial[tipo])
  const [seleccionada, setSeleccionada] = useState<string | null>(null)
  const { acciones, dialogos } = useAccionesTrabajo({ tituloBorrar: TITULOS_BORRAR.historial, avisoReferencia: 'Esta reparación está siendo referenciada' })

  const piezas = useMemo(() => [...new Set(data.map((r) => categoriaPieza(r.tipoComponente)).filter((c) => c !== ''))].sort((a, b) => a.localeCompare(b, 'es')), [data])
  const visibles = useMemo(() => {
    const imeis = imeisValidos(filtroImei)
    return data.filter((r) => pasaImeis(r.imei, imeis) && pasaTecnico(r.idTec, filtros.tecnicos) && pasaPieza(r.tipoComponente, filtros.piezas) && pasaFechas(r, filtros.desde, filtros.hasta) && pasaIncidencias(r, filtros.incidencias))
  }, [data, filtroImei, filtros])
  const columnas = useMemo(() => columnasTrabajo({ patronFechas: 'yyyy/MM/dd', tituloId: 'Id Reparación', onIrA: setSeleccionada }), [])

  useRegistrarExportable(() => {
    const base = global ? (tipo === 'GLASS' ? 'historial_glass' : 'historial_reparaciones') : tipo === 'GLASS' ? 'mis_glass' : 'mis_reparaciones'
    descargarCsv(base, cabecerasHistorial(global), visibles.map((r) => filaHistorial(r, global)))
  })

  const limpiar = () => { setFiltroImei(''); setFiltros({ tecnicos: new Set(), piezas: new Set(), desde: '', hasta: '', incidencias: new Set() }) }

  return (
    <div className="p-10">
      <TogglePill className="mb-2" opciones={TOGGLES_HISTORIAL} />
      <div className="mb-2 flex items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">{global ? 'Historial de reparaciones' : 'Mis reparaciones'}</h1>
        <PildoraContador texto={etiquetaContador(visibles.length, 'reparación', 'reparaciones')} />
      </div>
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <FiltroImei valor={filtroImei} onChange={setFiltroImei} />
        {global && (
          <MultiSelect opciones={tecnicos} clave={(t) => String(t.idTec)} etiqueta={(t) => t.nombre} seleccion={new Set([...filtros.tecnicos].map(String))}
            onChange={(s) => setFiltros({ ...filtros, tecnicos: new Set([...s].map(Number)) })} textoVacio="Técnico" textoPlural={(n) => `${n} técnicos`} className="min-w-[130px]" />
        )}
        <MultiSelect opciones={piezas} clave={(p) => p} etiqueta={(p) => p} seleccion={filtros.piezas} onChange={(s) => setFiltros({ ...filtros, piezas: s })} textoVacio="Pieza" textoPlural={(n) => `${n} piezas`} className="min-w-[140px]" />
        <RangoFechas desde={filtros.desde} hasta={filtros.hasta} onChange={(desde, hasta) => setFiltros({ ...filtros, desde, hasta })} />
        <MultiSelect opciones={OPCIONES_INCIDENCIAS} clave={(o) => o.clave} etiqueta={(o) => o.etiqueta} seleccion={filtros.incidencias as Set<string>}
          onChange={(s) => setFiltros({ ...filtros, incidencias: s as Set<EstadoIncidencia> })} textoVacio="Incidencias" textoPlural={(n) => `${n} filtros`} textoTodas="Todas" className="min-w-[130px]" />
        <BotonSecundario onClick={limpiar}>Limpiar filtros</BotonSecundario>
      </div>
      <DataTable
        columns={columnas}
        data={visibles}
        vacio=""
        ajuste="estirar"
        getRowId={(r) => r.idRep}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        filaClase={(r) => cn('border-l-8', estadoIncidencia(r) === 'abiertas' ? 'border-l-fila-incidencia-brd' : estadoIncidencia(r) === 'cerradas' ? 'border-l-fila-reparado-brd' : 'border-l-transparent')}
        menuFila={(r, celda) => <MenuHistorial rep={r} celda={celda} texto={textoCeldaTrabajo(r, celda.columnaId, 'yyyy/MM/dd')} puedeEditar={puedeEditar} acciones={acciones} />}
      />
      <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} />
      {dialogos}
    </div>
  )
}
```
`router.tsx`: `/reparaciones/historial` → `<HistorialPage tipo="REPARACION" />`, `/reparaciones/historial/glass` → `<HistorialPage tipo="GLASS" />`.

Nota: el JavaFX no pinta placeholder en esta tabla (`vacio=""`). El menú "Editar" deshabilitado: Radix marca `aria-disabled="true"` y `data-disabled`; el `title` se pasa al elemento.

- [ ] **Step 5: Verde y commit**

```bash
npm run check
git add src/shared/ui/AlertaProvider.tsx src/shared/ui/AlertaProvider.test.tsx src/modules/taller src/app/router.tsx
git commit -m "feat(web): Historial de reparaciones y glass: filtros por rol, columnas estiradas, menu del supertecnico con borrado con motivo e incidencias, CSV"
```

---

### Task 17: Web — Historial: Pulidos

**Files:**
- Create: `src/modules/taller/historial/HistorialPulidosPage.tsx`, `HistorialPulidosPage.test.tsx`
- Modify: `src/app/router.tsx`

**Interfaces:**
- Consumes: `useHistorial('PULIDO')`, `useTecnicos(true)`, `useBorrarPulido`, `useEditarModeloTelefono`, `SelectorLista`, `MODELOS_ORDENADOS`, `traducirModelo`, `filtrosHistorial.PULIDO`, `filtroImeiHistorial`.

- [ ] **Step 1: Tests (fallan)**

`src/modules/taller/historial/HistorialPulidosPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER, SESION_TEC } from '@/test/render'
import { reiniciarEstadoTaller } from '../estado'
import { resumen, tecnico } from '../test/fabrica'
import { HistorialPulidosPage } from './HistorialPulidosPage'

const filas = [
  resumen({ idRep: 'P20260915_10', imei: '351200000000021', modelo: '13', nombreTecnico: 'tecnico_k', idTec: 5, fechaAsig: '2026-09-15T09:44:00', fechaFin: '2026-09-15T09:44:00', nombreTecnicoAsigna: 'Técnico F', cliente: null }),
  resumen({ idRep: 'P20260905_1', imei: '351800000000031', modelo: null, nombreTecnico: 'tecnico_c', idTec: 6, fechaAsig: '2026-09-05T14:32:00', fechaFin: '2026-09-05T14:32:00', nombreTecnicoAsigna: 'tecnico_c', cliente: 'CLIENTE F' }),
]

beforeEach(() => {
  reiniciarEstadoTaller()
  server.use(
    http.get('*/api/pulidos/historial', () => HttpResponse.json(filas)),
    http.get('*/api/tecnicos/activos', () => HttpResponse.json([tecnico({ idTec: 5, nombre: 'tecnico_k' }), tecnico({ idTec: 6, nombre: 'tecnico_c' })])),
  )
})
const abrir = (sesion = SESION_SUPER) => renderConProviders(<HistorialPulidosPage />, { sesion, ruta: '/reparaciones/historial/pulidos' })

describe('HistorialPulidosPage (ficha docs/paridad/historial.md, toggle Pulidos)', () => {
  it('título, contador, filtros y columnas', async () => {
    abrir(SESION_TEC)
    expect(await screen.findByRole('heading', { name: 'Historial de pulidos' })).toBeInTheDocument()
    expect(screen.getByText('2 pulidos')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Técnico' })).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Id Pulido', 'IMEI', 'Modelo', 'Técnico', 'Fecha asignación', 'Fecha fin', 'Comentario', 'Cliente', 'Asignado por'])
    expect(screen.getAllByText('2026/09/15 11:44')).toHaveLength(2)
    await userEvent.click(screen.getByRole('button', { name: 'Técnico' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'tecnico_c' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByText('1 pulido')).toBeInTheDocument()
  })
  it('"Editar modelo" abre el selector con el modelo actual y guarda con POST /api/telefonos', async () => {
    let body: unknown = null
    server.use(http.post('*/api/telefonos', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 201 }) }))
    abrir()
    await screen.findByText('P20260915_10')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('P20260915_10') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['Editar modelo', 'Borrar', '📋  Copiar celda'])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar modelo' }))
    const dlg = await screen.findByRole('dialog', { name: 'Editar modelo' })
    expect(within(dlg).getByText('Selecciona el modelo:')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'iPhone 13' })).toHaveClass('bg-seleccion-suave')
    await userEvent.type(within(dlg).getByPlaceholderText('Filtrar modelo…'), '13 pro max')
    await userEvent.click(within(dlg).getByRole('button', { name: 'iPhone 13 Pro Max' }))
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(body).toEqual({ imei: '351200000000021', modelo: '13promax', idCli: null, clienteExplicito: null }))
  })
  it('"Borrar" pide motivo con su título y llama a DELETE', async () => {
    let body: unknown = null
    server.use(http.delete('*/api/pulidos/historial/P20260905_1', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 204 }) }))
    abrir()
    await screen.findByText('P20260905_1')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('P20260905_1') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Borrar' }))
    const dlg = await screen.findByRole('dialog', { name: 'Borrar pulido P20260905_1' })
    expect(within(dlg).getByText('Se borrará P20260905_1 del historial de pulido. Escribe el motivo.')).toBeInTheDocument()
    await userEvent.type(within(dlg).getByPlaceholderText('Escribe el motivo del borrado...'), 'duplicado')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(body).toEqual({ motivo: 'duplicado' }))
  })
  it('el técnico solo tiene Copiar celda; placeholder', async () => {
    abrir(SESION_TEC)
    await screen.findByText('P20260915_10')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('P20260915_10') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda'])
    await userEvent.keyboard('{Escape}')
    server.use(http.get('*/api/pulidos/historial', () => HttpResponse.json([])))
    renderConProviders(<HistorialPulidosPage />, { sesion: SESION_TEC, ruta: '/reparaciones/historial/pulidos' })
    expect(await screen.findByText('No hay pulidos completados')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Implementar**

`src/modules/taller/historial/HistorialPulidosPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import type { ReparacionResumen } from '@/shared/api/client'
import { descargarCsv, textoForzado } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'
import { imeisValidos } from '@/shared/lib/filtroImei'
import { useStore } from '@/shared/lib/store'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdminOSuperTecnico, esSuperTecnico } from '@/shared/session/storage'
import { BotonSecundario } from '@/shared/ui/Botones'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'
import { DataTable } from '@/shared/ui/DataTable'
import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { FiltroImei } from '@/shared/ui/FiltroImei'
import { MenuCopiarCelda } from '@/shared/ui/MenuCopiarCelda'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { RangoFechas } from '@/shared/ui/RangoFechas'
import { SelectorLista } from '@/shared/ui/SelectorLista'
import { TogglePill } from '@/shared/ui/TogglePill'
import { useBorrarPulido, useEditarModeloTelefono, useHistorial, useTecnicos } from '../api'
import { filtroImeiHistorial, filtrosHistorial } from '../estado'
import { etiquetaContador, pasaFechas, pasaImeis, pasaTecnico } from '../lib/filtros'
import { MODELOS_ORDENADOS, traducirModelo } from '../lib/modelos'
import { TOGGLES_HISTORIAL } from './HistorialPage'

const FMT = 'yyyy/MM/dd HH:mm' as const
const OPCIONES_MODELO = MODELOS_ORDENADOS.map((m) => ({ clave: m, etiqueta: traducirModelo(m) }))

function textoCelda(rep: ReparacionResumen, columna: string): string | null {
  switch (columna) {
    case 'id': return rep.idRep
    case 'imei': return rep.imei
    case 'modelo': return traducirModelo(rep.modelo)
    case 'tecnico': return rep.nombreTecnico ?? ''
    case 'fechaIni': return formatear(rep.fechaAsig, FMT)
    case 'fechaFin': return formatear(rep.fechaFin, FMT)
    case 'comentario': return rep.comentarioAsignacion ?? ''
    case 'cliente': return rep.cliente ?? ''
    case 'asignadoPor': return rep.nombreTecnicoAsigna ?? ''
    default: return null
  }
}

const COLUMNAS: ColumnDef<ReparacionResumen>[] = [
  { id: 'id', accessorKey: 'idRep', header: 'Id Pulido', size: 110 },
  { id: 'imei', accessorKey: 'imei', header: 'IMEI', size: 130 },
  { id: 'modelo', header: 'Modelo', size: 120, accessorFn: (r) => traducirModelo(r.modelo) },
  { id: 'tecnico', header: 'Técnico', size: 110, accessorFn: (r) => r.nombreTecnico ?? '' },
  { id: 'fechaIni', header: 'Fecha asignación', size: 130, accessorFn: (r) => formatear(r.fechaAsig, FMT) },
  { id: 'fechaFin', header: 'Fecha fin', size: 130, accessorFn: (r) => formatear(r.fechaFin, FMT) },
  { id: 'comentario', header: 'Comentario', size: 160, accessorFn: (r) => r.comentarioAsignacion ?? '' },
  { id: 'cliente', header: 'Cliente', size: 110, accessorFn: (r) => r.cliente ?? '' },
  { id: 'asignadoPor', header: 'Asignado por', size: 120, accessorFn: (r) => r.nombreTecnicoAsigna ?? '—' },
]

export function HistorialPulidosPage() {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const global = esAdminOSuperTecnico(sesion)
  const { data = [], dataUpdatedAt, refetch } = useHistorial('PULIDO')
  const { data: tecnicos = [] } = useTecnicos(true)
  const [filtroImei, setFiltroImei] = useStore(filtroImeiHistorial)
  const [filtros, setFiltros] = useStore(filtrosHistorial.PULIDO)
  const [seleccionada, setSeleccionada] = useState<string | null>(null)
  const [aBorrar, setABorrar] = useState<ReparacionResumen | null>(null)
  const [aEditar, setAEditar] = useState<ReparacionResumen | null>(null)
  const borrar = useBorrarPulido()
  const editarModelo = useEditarModeloTelefono()

  const visibles = useMemo(() => {
    const imeis = imeisValidos(filtroImei)
    return data.filter((r) => pasaImeis(r.imei, imeis) && pasaTecnico(r.idTec, filtros.tecnicos) && pasaFechas(r, filtros.desde, filtros.hasta))
  }, [data, filtroImei, filtros])

  useRegistrarExportable(() => {
    const cabeceras = ['ID', 'IMEI', 'Modelo', ...(global ? ['Técnico'] : []), 'Fecha inicio', 'Fecha fin', 'Comentario']
    descargarCsv('historial_pulidos', cabeceras, visibles.map((r) => [
      r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), ...(global ? [r.nombreTecnico ?? ''] : []),
      formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'), formatear(r.fechaFin, 'dd/MM/yyyy HH:mm'), r.comentarioAsignacion ?? '',
    ]))
  })

  return (
    <div className="p-10">
      <TogglePill className="mb-2" opciones={TOGGLES_HISTORIAL} />
      <div className="mb-3 flex items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Historial de pulidos</h1>
        <PildoraContador texto={etiquetaContador(visibles.length, 'pulido', 'pulidos')} />
      </div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <FiltroImei valor={filtroImei} onChange={setFiltroImei} />
        <MultiSelect opciones={tecnicos} clave={(t) => String(t.idTec)} etiqueta={(t) => t.nombre} seleccion={new Set([...filtros.tecnicos].map(String))}
          onChange={(s) => setFiltros({ ...filtros, tecnicos: new Set([...s].map(Number)) })} textoVacio="Técnico" textoPlural={(n) => `${n} técnicos`} className="min-w-[130px]" />
        <RangoFechas desde={filtros.desde} hasta={filtros.hasta} onChange={(desde, hasta) => setFiltros({ ...filtros, desde, hasta })} />
        <BotonSecundario onClick={() => { setFiltroImei(''); setFiltros({ ...filtros, tecnicos: new Set(), desde: '', hasta: '' }) }}>Limpiar filtros</BotonSecundario>
      </div>
      <DataTable
        columns={COLUMNAS}
        data={visibles}
        vacio="No hay pulidos completados"
        getRowId={(r) => r.idRep}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        menuFila={(r, celda) =>
          puedeEditar ? (
            <>
              <ContextMenuItem onSelect={() => setAEditar(r)}><img src="/editar.png" alt="" className="mr-2 h-3.5 w-3.5" />Editar modelo</ContextMenuItem>
              <ContextMenuItem onSelect={() => setABorrar(r)}>Borrar</ContextMenuItem>
              <ContextMenuSeparator />
              <MenuCopiarCelda texto={textoCelda(r, celda.columnaId)} celda={celda} />
            </>
          ) : (
            <MenuCopiarCelda texto={textoCelda(r, celda.columnaId)} celda={celda} />
          )
        }
      />
      <EtiquetaActualizado actualizadoEn={dataUpdatedAt} onRecargar={() => refetch({ throwOnError: true })} />
      <ConfirmDialog abierto={aBorrar !== null} conMotivo titulo={`Borrar pulido ${aBorrar?.idRep ?? ''}`} descripcion={aBorrar ? `Se borrará ${aBorrar.idRep} del historial de pulido. Escribe el motivo.` : ''} textoAccion="Borrar"
        onCancelar={() => setABorrar(null)} onConfirmar={(motivo) => { if (aBorrar && motivo) borrar.mutate({ idP: aBorrar.idRep, motivo }); setABorrar(null) }} />
      <SelectorLista abierto={aEditar !== null} titulo="Editar modelo" etiquetaLista="Selecciona el modelo:" placeholderBuscar="Filtrar modelo…" opciones={OPCIONES_MODELO}
        claveActual={aEditar?.modelo ?? null} textoSeleccionar="Guardar" onCancelar={() => setAEditar(null)}
        onSeleccionar={(modelo) => { if (aEditar) editarModelo.mutate({ imei: aEditar.imei, modelo }); setAEditar(null) }} />
    </div>
  )
}
```
`router.tsx`: `/reparaciones/historial/pulidos` → `<HistorialPulidosPage />`.

- [ ] **Step 3: Verde y commit**

```bash
npm run check
git add src/modules/taller src/app/router.tsx
git commit -m "feat(web): Historial de pulidos con Editar modelo (catalogo), Borrar con motivo y CSV"
```

---

### Task 18: Web — IMEIs: maestro agrupado y detalle por IMEI

**Files:**
- Create: `src/modules/taller/imeis/agrupacion.ts` (+ `agrupacion.test.ts`), `useTrabajos.ts`, `BarraFiltrosImeis.tsx`, `csvImeis.ts`, `ImeisPage.tsx` (+ `ImeisPage.test.tsx`), `ImeiDetallePage.tsx` (+ `ImeiDetallePage.test.tsx`), `src/modules/taller/componentes/DialogoObservacion.tsx`
- Modify: `src/app/router.tsx` (placeholders de `/reparaciones/imeis` y `/reparaciones/imeis/:imei`), `src/test/render.tsx` (+ opción `patron`)

**Interfaces:**
- Consumes: `agruparPorImei`, `ordenarPorActividad`, `resumenTipos`, `GrupoImei` (Task 12); `pasaImeis`, `pasaFechas`, `pasaCliente`, `pasaTecnico`, `pasaIncidencias`, `estadoIncidencia`, `SIN_CLIENTE`, `etiquetaContador`, `EstadoIncidencia` (Task 12); `filtrosImeis`, `FILTROS_IMEIS_VACIOS`, `FiltrosImeis`, `ultimoImeiVisto` (Task 13); `useHistorial`, `useTecnicos`, `useClientesActivos`, `useEditarObservacionTelefono`, `useEditarClienteTelefono` (Task 13); `columnasTrabajo`, `textoCeldaTrabajo`, `MenuHistorial`, `useAccionesTrabajo({ tituloBorrar, avisoReferencia })`, `TITULOS_BORRAR`, `OPCIONES_INCIDENCIAS`, `CeldaEstadoTrabajo` (Task 16); `SelectorLista`, `CeldaFechas`, `TextoExpandible`, `MultiSelect` (+`textoTodas`), `RangoFechas`, `FiltroImei`, `MenuCopiarCelda` (Task 10); `DataTable` con `seleccionada` restaurable y `onAbrir` (Task 9); `StaleDataError`, `esErrorGestionadoGlobalmente`, `mensajeDeError` (existentes).
- Produces: `agruparVisibles(trabajos, filtros): GrupoImei[]`, `pasaIncidenciasGrupo(g, marcados)`, `opcionesCliente(trabajos): string[]`, `filasDetalle(trabajos, imei, filtros): FilasDetalle`, `esAjeno(rep, filtros)`, `textoTrabajos(detalle, conFiltroTecnico)`; `useTrabajos(): ReparacionResumen[]`; `<BarraFiltrosImeis modo="maestro" | "detalle" opcionesCliente? />`; `<DialogoObservacion grupo onGuardar onCerrar />`; `MSG_TELEFONO_MODIFICADO`.

- [ ] **Step 1: Tests de la lógica pura (fallan)**

`src/modules/taller/imeis/agrupacion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { FILTROS_IMEIS_VACIOS, type FiltrosImeis } from '../estado'
import { SIN_CLIENTE, type EstadoIncidencia } from '../lib/filtros'
import { resumen } from '../test/fabrica'
import { agruparVisibles, esAjeno, filasDetalle, opcionesCliente, textoTrabajos } from './agrupacion'

const inc = (...v: EstadoIncidencia[]) => new Set<EstadoIncidencia>(v)

const A = '111111111111111', B = '222222222222222', C = '333333333333333'
const trabajos = [
  resumen({ idRep: 'R20260901_1', imei: A, idTec: 5, cliente: 'WEB', fechaAsig: '2026-09-01T08:00:00', fechaFin: '2026-09-01T09:00:00' }),
  resumen({ idRep: 'G20260910_1', imei: A, idTec: 6, cliente: 'WEB', fechaAsig: '2026-09-10T08:00:00', fechaFin: '2026-09-10T09:00:00', esIncidencia: true }),
  resumen({ idRep: 'P20260905_1', imei: B, idTec: 5, cliente: null, fechaAsig: '2026-09-05T08:00:00', fechaFin: '2026-09-05T09:00:00' }),
  resumen({ idRep: 'R20260812_1', imei: C, idTec: 6, cliente: 'AMAZON', fechaAsig: '2026-08-12T08:00:00', fechaFin: '2026-08-12T09:00:00', esIncidencia: true, esResuelto: true }),
]
const f = (extra: Partial<FiltrosImeis>): FiltrosImeis => ({ ...FILTROS_IMEIS_VACIOS, ...extra })

describe('agrupación del maestro (calco de AgrupadoController.cargar)', () => {
  it('sin filtros: un grupo por IMEI ordenado por actividad más reciente', () => {
    expect(agruparVisibles(trabajos, FILTROS_IMEIS_VACIOS).map((g) => g.imei)).toEqual([A, B, C])
  })
  it('el filtro de técnico deja los grupos en los que ALGUNO de los marcados trabajó, con todos sus trabajos', () => {
    const grupos = agruparVisibles(trabajos, f({ tecnicos: new Set([6]) }))
    expect(grupos.map((g) => g.imei)).toEqual([A, C])
    expect(grupos[0].trabajos).toHaveLength(2)
  })
  it('IMEI, fechas y cliente filtran los trabajos antes de agrupar', () => {
    expect(agruparVisibles(trabajos, f({ imei: B })).map((g) => g.imei)).toEqual([B])
    expect(agruparVisibles(trabajos, f({ desde: '2026-09-02', hasta: '2026-09-30' })).map((g) => g.imei)).toEqual([A, B])
    expect(agruparVisibles(trabajos, f({ desde: '2026-09-02', hasta: '2026-09-30' }))[0].trabajos).toHaveLength(1)
    expect(agruparVisibles(trabajos, f({ clientes: new Set([SIN_CLIENTE]) })).map((g) => g.imei)).toEqual([B])
    expect(agruparVisibles(trabajos, f({ clientes: new Set(['AMAZON', 'WEB']) })).map((g) => g.imei)).toEqual([A, C])
  })
  it('Incidencia = alguna abierta, Normal = ninguna; "cerradas" no cuenta en el maestro', () => {
    expect(agruparVisibles(trabajos, f({ incidencias: inc('abiertas') })).map((g) => g.imei)).toEqual([A])
    expect(agruparVisibles(trabajos, f({ incidencias: inc('sin') })).map((g) => g.imei)).toEqual([B, C])
    expect(agruparVisibles(trabajos, f({ incidencias: inc('cerradas') })).map((g) => g.imei)).toEqual([A, B, C])
  })
  it('opcionesCliente: alfabético con "(Sin cliente)" delante solo si hay trabajos sin cliente', () => {
    expect(opcionesCliente(trabajos)).toEqual([SIN_CLIENTE, 'AMAZON', 'WEB'])
    expect(opcionesCliente(trabajos.filter((t) => t.cliente))).toEqual(['AMAZON', 'WEB'])
  })
})

describe('detalle de un IMEI', () => {
  const del = [
    resumen({ idRep: 'R20260901_1', imei: A, idTec: 5, fechaAsig: '2026-09-01T08:00:00', fechaFin: '2026-09-01T09:00:00' }),
    resumen({ idRep: 'G20260910_1', imei: A, idTec: 6, fechaAsig: '2026-09-10T08:00:00', fechaFin: '2026-09-10T09:00:00', esIncidencia: true }),
    resumen({ idRep: 'R20260815_1', imei: A, idTec: 6, fechaAsig: '2026-08-15T08:00:00', fechaFin: '2026-08-15T09:00:00' }),
    resumen({ idRep: 'P20260905_1', imei: B, idTec: 5 }),
  ]
  it('solo los trabajos del IMEI, por fecha de asignación ascendente', () => {
    const d = filasDetalle(del, A, FILTROS_IMEIS_VACIOS)
    expect(d.filas.map((t) => t.idRep)).toEqual(['R20260815_1', 'R20260901_1', 'G20260910_1'])
    expect(d).toMatchObject({ deFiltrados: 3, deOtros: 0 })
    expect(textoTrabajos(d, false)).toBe('• 3 trabajos')
    expect(textoTrabajos(filasDetalle(del, B, FILTROS_IMEIS_VACIOS), false)).toBe('• 1 trabajo')
  })
  it('con filtro de técnico: primero los suyos, después los ajenos (atenuados), y el texto "X de filtrados + Y de otros"', () => {
    const filtros = f({ tecnicos: new Set([6]) })
    const d = filasDetalle(del, A, filtros)
    expect(d.filas.map((t) => t.idRep)).toEqual(['R20260815_1', 'G20260910_1', 'R20260901_1'])
    expect(esAjeno(d.filas[2], filtros)).toBe(true)
    expect(esAjeno(d.filas[0], filtros)).toBe(false)
    expect(textoTrabajos(d, true)).toBe('• 2 de filtrados + 1 de otros')
    expect(textoTrabajos(filasDetalle(del, A, f({ tecnicos: new Set([5, 6]) })), true)).toBe('• 3 de filtrados')
  })
  it('fechas e incidencias filtran las filas del detalle', () => {
    expect(filasDetalle(del, A, f({ incidencias: inc('abiertas') })).filas.map((t) => t.idRep)).toEqual(['G20260910_1'])
    expect(filasDetalle(del, A, f({ desde: '2026-09-05', hasta: '' })).filas.map((t) => t.idRep)).toEqual(['G20260910_1'])
  })
})
```

```bash
npm test -- agrupacion
```
Expected: falla (módulo inexistente).

- [ ] **Step 2: Lógica pura, hook de datos, filtros y diálogo de observación**

`src/modules/taller/imeis/agrupacion.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { imeisValidos } from '@/shared/lib/filtroImei'
import type { FiltrosImeis } from '../estado'
import { SIN_CLIENTE, pasaCliente, pasaFechas, pasaImeis, pasaIncidencias, pasaTecnico, type EstadoIncidencia } from '../lib/filtros'
import { agruparPorImei, ordenarPorActividad, type GrupoImei } from '../lib/grupoImei'

/** Maestro: "Incidencia" = alguna abierta, "Normal" = ninguna. "cerradas" solo existe en el detalle y aquí no cuenta. */
export function pasaIncidenciasGrupo(g: GrupoImei, marcados: Set<EstadoIncidencia>): boolean {
  const incidencia = marcados.has('abiertas')
  const normal = marcados.has('sin')
  if (!incidencia && !normal) return true
  return (incidencia && g.incAbiertas > 0) || (normal && g.incAbiertas === 0)
}

/** Calco de AgrupadoController.cargar(): IMEI, fechas y cliente filtran los trabajos antes de agrupar; un grupo se
 *  muestra si ALGUNO de sus trabajos pasa el filtro de técnico (y conserva todos sus trabajos); orden por actividad. */
export function agruparVisibles(trabajos: ReparacionResumen[], f: FiltrosImeis): GrupoImei[] {
  const imeis = imeisValidos(f.imei)
  const previos = trabajos.filter((t) => pasaImeis(t.imei, imeis) && pasaFechas(t, f.desde, f.hasta) && pasaCliente(t.cliente, f.clientes))
  const grupos = agruparPorImei(previos).filter((g) => g.trabajos.some((t) => pasaTecnico(t.idTec, f.tecnicos)) && pasaIncidenciasGrupo(g, f.incidencias))
  return ordenarPorActividad(grupos)
}

/** Clientes presentes en los trabajos cargados, alfabéticos, con "(Sin cliente)" delante si hay trabajos sin cliente. */
export function opcionesCliente(trabajos: ReparacionResumen[]): string[] {
  const nombres = new Set<string>()
  let sinCliente = false
  for (const t of trabajos) {
    if (t.cliente) nombres.add(t.cliente)
    else sinCliente = true
  }
  const lista = [...nombres].sort((a, b) => a.localeCompare(b, 'es'))
  return sinCliente ? [SIN_CLIENTE, ...lista] : lista
}

export type FilasDetalle = { filas: ReparacionResumen[]; deFiltrados: number; deOtros: number }

/** ISO del servidor: el orden lexicográfico es el cronológico. */
function porAsignacion(a: ReparacionResumen, b: ReparacionResumen): number {
  return a.fechaAsig === b.fechaAsig ? 0 : a.fechaAsig < b.fechaAsig ? -1 : 1
}

/** Ajeno al filtro de técnico (la vista lo atenúa); sin filtro nadie es ajeno. */
export function esAjeno(t: ReparacionResumen, f: FiltrosImeis): boolean {
  return f.tecnicos.size > 0 && !pasaTecnico(t.idTec, f.tecnicos)
}

/** Detalle: trabajos del IMEI que pasan fechas e incidencias, por fecha de asignación ascendente;
 *  con filtro de técnico, primero los de los marcados y después los ajenos. */
export function filasDetalle(trabajos: ReparacionResumen[], imei: string, f: FiltrosImeis): FilasDetalle {
  const ordenados = trabajos.filter((t) => t.imei === imei && pasaFechas(t, f.desde, f.hasta) && pasaIncidencias(t, f.incidencias)).sort(porAsignacion)
  const propios = ordenados.filter((t) => !esAjeno(t, f))
  const ajenos = ordenados.filter((t) => esAjeno(t, f))
  return { filas: [...propios, ...ajenos], deFiltrados: propios.length, deOtros: ajenos.length }
}

/** "• N trabajos" / "• 1 trabajo"; con filtro de técnico "• X de filtrados + Y de otros" (o solo "• X de filtrados"). */
export function textoTrabajos(d: FilasDetalle, conFiltroTecnico: boolean): string {
  if (!conFiltroTecnico) return `• ${d.filas.length} ${d.filas.length === 1 ? 'trabajo' : 'trabajos'}`
  return d.deOtros === 0 ? `• ${d.deFiltrados} de filtrados` : `• ${d.deFiltrados} de filtrados + ${d.deOtros} de otros`
}
```

`src/modules/taller/imeis/useTrabajos.ts`:

```ts
import { useMemo } from 'react'
import type { ReparacionResumen } from '@/shared/api/client'
import { useHistorial } from '../api'

/** La unión de los tres historiales (R + G + P), las mismas consultas cacheadas que usa el Historial. */
export function useTrabajos(): ReparacionResumen[] {
  const rep = useHistorial('REPARACION')
  const glass = useHistorial('GLASS')
  const pul = useHistorial('PULIDO')
  return useMemo(() => [...(rep.data ?? []), ...(glass.data ?? []), ...(pul.data ?? [])], [rep.data, glass.data, pul.data])
}
```

`src/modules/taller/imeis/BarraFiltrosImeis.tsx` (el tipo `FiltrosImeis` es el del store, de ahí el nombre distinto):

```tsx
import { useStore } from '@/shared/lib/store'
import { BotonSecundario } from '@/shared/ui/Botones'
import { FiltroImei } from '@/shared/ui/FiltroImei'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { RangoFechas } from '@/shared/ui/RangoFechas'
import { useTecnicos } from '../api'
import { FILTROS_IMEIS_VACIOS, filtrosImeis } from '../estado'
import { OPCIONES_INCIDENCIAS } from '../historial/HistorialPage'
import type { EstadoIncidencia } from '../lib/filtros'

/** En el maestro solo hay dos casillas: "Incidencia" (alguna abierta) y "Normal"; la de "Cerradas" se oculta. */
export const OPCIONES_INCIDENCIAS_MAESTRO: { clave: EstadoIncidencia; etiqueta: string }[] = [
  { clave: 'abiertas', etiqueta: 'Incidencia' },
  { clave: 'sin', etiqueta: 'Normal' },
]

type Props = { modo: 'maestro' | 'detalle'; opcionesCliente?: string[] }

/** Los mismos controles en maestro y detalle, sobre el mismo store (los filtros sobreviven al ir y volver del detalle).
 *  Maestro: IMEI · Técnico · Cliente · Desde · Hasta · Incidencias(2) · Limpiar. Detalle: Técnico · Desde · Hasta · Incidencias(3) · Limpiar. */
export function BarraFiltrosImeis({ modo, opcionesCliente = [] }: Props) {
  const [f, setF] = useStore(filtrosImeis)
  const { data: tecnicos = [] } = useTecnicos()
  const maestro = modo === 'maestro'
  const opcionesInc = maestro ? OPCIONES_INCIDENCIAS_MAESTRO : OPCIONES_INCIDENCIAS
  const incidencias = maestro ? new Set([...f.incidencias].filter((k) => k !== 'cerradas')) : f.incidencias
  return (
    <div className="mb-2 flex flex-wrap items-center gap-3">
      {maestro && <FiltroImei valor={f.imei} onChange={(imei) => setF({ ...f, imei })} />}
      <MultiSelect opciones={tecnicos} clave={(t) => String(t.idTec)} etiqueta={(t) => t.nombre} seleccion={new Set([...f.tecnicos].map(String))}
        onChange={(s) => setF({ ...f, tecnicos: new Set([...s].map(Number)) })} textoVacio="Técnico" textoPlural={(n) => `${n} técnicos`} className="min-w-[130px]" />
      {maestro && (
        <MultiSelect opciones={opcionesCliente} clave={(c) => c} etiqueta={(c) => c} seleccion={f.clientes} onChange={(clientes) => setF({ ...f, clientes })}
          textoVacio="Cliente" textoPlural={(n) => `${n} clientes`} className="min-w-[150px]" />
      )}
      <RangoFechas desde={f.desde} hasta={f.hasta} onChange={(desde, hasta) => setF({ ...f, desde, hasta })} />
      <MultiSelect opciones={opcionesInc} clave={(o) => o.clave} etiqueta={(o) => o.etiqueta} seleccion={incidencias as Set<string>}
        onChange={(s) => setF({ ...f, incidencias: s as Set<EstadoIncidencia> })} textoVacio="Incidencias" textoPlural={(n) => `${n} filtros`} textoTodas="Todas" className="min-w-[130px]" />
      <BotonSecundario onClick={() => setF(FILTROS_IMEIS_VACIOS)}>Limpiar filtros</BotonSecundario>
    </div>
  )
}
```

`src/modules/taller/componentes/DialogoObservacion.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import type { GrupoImei } from '../lib/grupoImei'

type Props = { grupo: GrupoImei | null; onGuardar: (observacion: string) => void; onCerrar: () => void }

/** Calco de abrirDialogoObservacionTelefono: "Observación — IMEI <imei>", área de 4 líneas precargada, "Guardar" verde y "Cerrar". */
export function DialogoObservacion({ grupo, onGuardar, onCerrar }: Props) {
  const [texto, setTexto] = useState('')
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga la observación del grupo al abrir
    if (grupo) setTexto(grupo.observacion ?? '')
  }, [grupo])
  return (
    <Dialog open={grupo !== null} onOpenChange={(o) => !o && onCerrar()}>
      <DialogContent aria-describedby={undefined} className="max-w-[520px] gap-2 bg-fondo-input p-4">
        <DialogHeader><DialogTitle className="text-[14px] font-bold text-azul-medio">Observación del teléfono</DialogTitle></DialogHeader>
        <Label htmlFor="observacion-telefono" className="text-[12px]">Observación — IMEI {grupo?.imei ?? ''}</Label>
        <textarea id="observacion-telefono" value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Observación del teléfono..." rows={4}
          className="w-full rounded border border-gris-borde bg-superficie p-2 text-[13px]" />
        <Button onClick={() => onGuardar(texto.trim())} className="h-auto w-full rounded bg-fila-reparado-ico py-2 text-[12px] text-superficie hover:bg-fila-reparado-ico/90">Guardar</Button>
        <DialogFooter><Button variant="outline" onClick={onCerrar}>Cerrar</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

`src/modules/taller/imeis/csvImeis.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { textoForzado } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'
import { tipoDe } from '@/shared/lib/tipoTrabajo'
import type { GrupoImei } from '../lib/grupoImei'
import { traducirModelo } from '../lib/modelos'

/** CSV del maestro (agrupado_resumen), sin "Revisión logística" (diferencia aceptada). */
export const CABECERAS_RESUMEN = ['IMEI', 'Modelo', 'Primera', 'Última', 'Reparaciones', 'Glass', 'Pulidos', 'Inc. abiertas', 'Observación', 'Cliente']
export function filaResumen(g: GrupoImei): string[] {
  return [
    textoForzado(g.imei), g.modelo ? traducirModelo(g.modelo) : '', formatear(g.fechaMasAntigua, 'dd/MM/yyyy'), formatear(g.fechaMasReciente, 'dd/MM/yyyy'),
    String(g.countRep), String(g.countGlass), String(g.countPul), String(g.incAbiertas), g.observacion ?? '', g.cliente ?? '',
  ]
}

/** CSV del detalle (agrupado_<imei>): recorrido cronológico del IMEI con columna Tipo; "ID Rep. anterior" vacío en pulidos. */
export const CABECERAS_DETALLE = ['Tipo', 'ID', 'IMEI', 'Técnico', 'Fecha asig.', 'Fecha fin', 'Componente', 'Reutilizado', 'Observaciones', 'Incidencia', 'Resuelto', 'ID Rep. anterior']
export function filaDetalle(r: ReparacionResumen): string[] {
  const tipo = tipoDe(r.idRep)
  const etiqueta = tipo === 'GLASS' ? 'Glass' : tipo === 'PULIDO' ? 'Pulido' : 'Reparación'
  return [
    etiqueta, r.idRep, textoForzado(r.imei), r.nombreTecnico ?? '', formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'), formatear(r.fechaFin, 'dd/MM/yyyy HH:mm'),
    r.tipoComponente ?? '', r.esReutilizado ? 'Sí' : 'No', r.observaciones ?? '', r.esIncidencia ? (r.incidencia ?? 'Sí') : 'No', r.esResuelto ? 'Sí' : 'No',
    tipo === 'PULIDO' ? '' : (r.idRepAnterior ?? ''),
  ]
}
```

```bash
npm test -- agrupacion
```
Expected: verde.

- [ ] **Step 3: Tests de las páginas (fallan)**

`src/modules/taller/imeis/ImeisPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import * as csv from '@/shared/lib/csv'
import { reiniciarEstadoTaller, ultimoImeiVisto } from '../estado'
import { resumen, tecnico } from '../test/fabrica'
import { ImeiDetallePage } from './ImeiDetallePage'
import { ImeisPage } from './ImeisPage'

const A = '351900000000041', B = '358800000000131'
const reps = [
  resumen({ idRep: 'R20260916_6', imei: B, modelo: '14', idTec: 5, nombreTecnico: 'tecnico_i', fechaAsig: '2026-09-16T07:00:00', fechaFin: '2026-09-16T07:30:00', observacionTelefono: 'tapa rayada', cliente: 'WEB', telefonoUpdatedAt: '2026-09-01T00:00:00' }),
  resumen({ idRep: 'R20260910_1', imei: A, modelo: '16', idTec: 6, nombreTecnico: 'tecnico_b', fechaAsig: '2026-09-10T10:00:00', fechaFin: '2026-09-11T10:00:00', esIncidencia: true, incidencia: 'no enciende', cliente: null }),
]
const glass = [resumen({ idRep: 'G20260912_1', imei: A, modelo: '16', idTec: 5, nombreTecnico: 'tecnico_i', fechaAsig: '2026-09-12T10:00:00', fechaFin: '2026-09-12T11:00:00', cliente: null })]
const pulidos = [resumen({ idRep: 'P20260913_1', imei: A, modelo: '16', idTec: 5, nombreTecnico: 'tecnico_i', fechaAsig: '2026-09-13T10:00:00', fechaFin: '2026-09-13T11:00:00', cliente: null })]

beforeEach(() => {
  reiniciarEstadoTaller()
  server.use(
    http.get('*/api/reparaciones/historial', () => HttpResponse.json(reps)),
    http.get('*/api/glass/historial', () => HttpResponse.json(glass)),
    http.get('*/api/pulidos/historial', () => HttpResponse.json(pulidos)),
    http.get('*/api/tecnicos', () => HttpResponse.json([tecnico({ idTec: 5, nombre: 'tecnico_i' }), tecnico({ idTec: 6, nombre: 'tecnico_b' })])),
    http.get('*/api/clientes/activos', () => HttpResponse.json([{ idCli: 1, nombre: 'AMAZON', activo: true, updatedAt: null }, { idCli: 2, nombre: 'WEB', activo: true, updatedAt: null }])),
  )
})
const abrir = (sesion = SESION_SUPER) =>
  renderConProviders(<ImeisPage />, { sesion, ruta: '/reparaciones/imeis', rutas: <Route path="/reparaciones/imeis/:imei" element={<ImeiDetallePage />} /> })

describe('ImeisPage — maestro (ficha docs/paridad/imeis.md)', () => {
  it('título, contador, columnas, agrupación y orden por actividad', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Agrupado por IMEI' })).toBeInTheDocument()
    await screen.findByText(A)
    expect(screen.getByText('2 IMEIs')).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['IMEI teléfono', 'Modelo', 'Fechas', 'Trabajos', 'Estado', 'Observación', 'Cliente'])
    const filas = screen.getAllByRole('row').slice(1)
    expect(filas[0]).toHaveTextContent(B)
    expect(filas[1]).toHaveTextContent(A)
    expect(filas[1]).toHaveTextContent('1 Rep · 1 Glass · 1 Pul')
    expect(within(filas[1]).getByText('Incidencia')).toBeInTheDocument()
    expect(filas[1]).toHaveClass('border-l-fila-incidencia-brd')
    expect(within(filas[0]).getByText('Normal')).toBeInTheDocument()
    expect(filas[0]).toHaveClass('border-l-azul-medio')
    expect(within(filas[0]).getByText('2026/09/16 09:00')).toBeInTheDocument()
    expect(within(filas[0]).getByText('→ 2026/09/16 09:30')).toBeInTheDocument()
    expect(within(filas[0]).getByText('tapa rayada')).toBeInTheDocument()
    expect(screen.queryByText(/Actualizado/)).not.toBeInTheDocument()
  })
  it('filtros: IMEI, Técnico, Cliente con "(Sin cliente)", Incidencias con dos casillas, Limpiar', async () => {
    abrir()
    await screen.findByText(A)
    await userEvent.type(screen.getByPlaceholderText('Filtrar por IMEI'), B)
    expect(screen.getByText('1 IMEI')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await userEvent.click(screen.getByRole('button', { name: 'Técnico' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'tecnico_b' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByRole('row', { name: new RegExp(A) })).toHaveTextContent('1 Rep · 1 Glass · 1 Pul')
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cliente' }))
    expect(screen.getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'))).toEqual(['(Sin cliente)', 'WEB'])
    await userEvent.click(screen.getByRole('checkbox', { name: '(Sin cliente)' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByRole('row', { name: new RegExp(A) })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await userEvent.click(screen.getByRole('button', { name: 'Incidencias' }))
    expect(screen.getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'))).toEqual(['Incidencia', 'Normal'])
    await userEvent.click(screen.getByRole('checkbox', { name: 'Normal' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Normal' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByRole('row', { name: new RegExp(B) })).toBeInTheDocument()
  })
  it('el icono y el doble clic abren el detalle; al volver, el IMEI queda seleccionado', async () => {
    abrir()
    await screen.findByText(A)
    await userEvent.click(screen.getByRole('button', { name: `Ver trabajos de ${A}` }))
    expect(await screen.findByText(`IMEI: ${A}`)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '← Volver' }))
    await screen.findByRole('heading', { name: 'Agrupado por IMEI' })
    expect(await screen.findByRole('row', { name: new RegExp(A) })).toHaveAttribute('aria-selected', 'true')
    expect(ultimoImeiVisto.get()).toBeNull()
    await userEvent.dblClick(screen.getByText(B))
    expect(await screen.findByText(`IMEI: ${B}`)).toBeInTheDocument()
  })
  it('menú del supertécnico: Copiar celda, Editar observación (PATCH con updatedAt) y 409 con su aviso', async () => {
    let body: unknown = null
    server.use(http.patch(`*/api/telefonos/${B}/observacion`, async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 204 }) }))
    abrir()
    await screen.findByText(A)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText(B) })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda', 'Editar observación', 'Editar cliente'])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Editar observación' }))
    const dlg = await screen.findByRole('dialog', { name: 'Observación del teléfono' })
    const area = within(dlg).getByLabelText(`Observación — IMEI ${B}`)
    expect(area).toHaveValue('tapa rayada')
    await userEvent.clear(area)
    await userEvent.type(area, '  tapa nueva ')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(body).toEqual({ observacion: 'tapa nueva', updatedAt: '2026-09-01T00:00:00' }))
    server.use(http.patch(`*/api/telefonos/${B}/observacion`, () => HttpResponse.json({ message: 'stale' }, { status: 409 })))
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText(B) })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar observación' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Observación del teléfono' })).getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('El teléfono fue modificado por otro usuario. Se recargan los datos.')
  })
  it('"Editar cliente": "— Sin cliente —" primero, actual resaltado, Seleccionar deshabilitado hasta elegir, PATCH', async () => {
    let body: unknown = null
    server.use(http.patch(`*/api/telefonos/${B}/cliente`, async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 204 }) }))
    abrir()
    await screen.findByText(A)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText(B) })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar cliente' }))
    const dlg = await screen.findByRole('dialog', { name: 'Seleccionar cliente' })
    expect(within(dlg).getAllByRole('option').map((o) => o.textContent)).toEqual(['— Sin cliente —', 'AMAZON', 'WEB'])
    expect(within(dlg).getByRole('button', { name: 'WEB' })).toHaveClass('bg-seleccion-suave')
    expect(within(dlg).getByText('Nada seleccionado')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Seleccionar' })).toBeDisabled()
    await userEvent.click(within(dlg).getByRole('button', { name: '— Sin cliente —' }))
    await userEvent.click(within(dlg).getByRole('button', { name: 'Seleccionar' }))
    await waitFor(() => expect(body).toEqual({ idCli: null, updatedAt: '2026-09-01T00:00:00' }))
  })
  it('el técnico solo tiene Copiar celda', async () => {
    abrir(SESION_TEC)
    await screen.findByText(A)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText(B) })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda'])
  })
  it('el admin solo tiene Copiar celda', async () => {
    abrir(SESION_ADMIN)
    await screen.findByText(A)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText(B) })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda'])
  })
  it('CSV agrupado_resumen con los grupos visibles', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    abrir()
    await screen.findByText(A)
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    const [base, cabeceras, filasCsv] = descargar.mock.calls[0]
    expect(base).toBe('agrupado_resumen')
    expect(cabeceras).toEqual(['IMEI', 'Modelo', 'Primera', 'Última', 'Reparaciones', 'Glass', 'Pulidos', 'Inc. abiertas', 'Observación', 'Cliente'])
    expect(filasCsv[1]).toEqual([`="${A}"`, 'iPhone 16', '10/09/2026', '13/09/2026', '1', '1', '1', '1', '', ''])
    descargar.mockRestore()
  })
})
```
(Como en las Tasks 14 y 16, el test del CSV monta `<AppLayout />` con la ruta de la página para tener el menú de usuario.)

`src/modules/taller/imeis/ImeiDetallePage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER, SESION_TEC } from '@/test/render'
import * as csv from '@/shared/lib/csv'
import { filtrosImeis, reiniciarEstadoTaller, ultimoImeiVisto } from '../estado'
import { resumen, tecnico } from '../test/fabrica'
import { ImeiDetallePage } from './ImeiDetallePage'

const A = '351900000000041'
const reps = [
  resumen({ idRep: 'R20260910_1', imei: A, modelo: '16', idTec: 6, nombreTecnico: 'tecnico_b', nombreTecnicoAsigna: 'Técnico M', fechaAsig: '2026-09-10T10:00:00', fechaFin: '2026-09-11T10:00:00', tipoComponente: 'lcdi16negra', esIncidencia: true, incidencia: 'no enciende' }),
  resumen({ idRep: 'R20260916_6', imei: '358800000000131', modelo: '14', idTec: 5, nombreTecnico: 'tecnico_i' }),
]
const glass = [resumen({ idRep: 'G20260912_1', imei: A, modelo: '16', idTec: 5, nombreTecnico: 'tecnico_i', fechaAsig: '2026-09-12T10:00:00', fechaFin: '2026-09-12T11:00:00', tipoComponente: 'glassi16', esReutilizado: true })]
const pulidos = [resumen({ idRep: 'P20260913_1', imei: A, modelo: '16', idTec: 5, nombreTecnico: 'tecnico_i', fechaAsig: '2026-09-13T10:00:00', fechaFin: '2026-09-13T11:00:00', idRepAnterior: 'AP20260913_1' })]

beforeEach(() => {
  reiniciarEstadoTaller()
  server.use(
    http.get('*/api/reparaciones/historial', () => HttpResponse.json(reps)),
    http.get('*/api/glass/historial', () => HttpResponse.json(glass)),
    http.get('*/api/pulidos/historial', () => HttpResponse.json(pulidos)),
    http.get('*/api/tecnicos', () => HttpResponse.json([tecnico({ idTec: 5, nombre: 'tecnico_i' }), tecnico({ idTec: 6, nombre: 'tecnico_b' })])),
    http.get('*/api/tecnicos/activos', () => HttpResponse.json([tecnico({ idTec: 5, nombre: 'tecnico_i' }), tecnico({ idTec: 6, nombre: 'tecnico_b' })])),
  )
})
const abrir = (sesion = SESION_SUPER) => renderConProviders(<ImeiDetallePage />, { sesion, ruta: `/reparaciones/imeis/${A}`, patron: '/reparaciones/imeis/:imei' })

describe('ImeiDetallePage (ficha docs/paridad/imeis.md, detalle)', () => {
  it('barra, filtros, columnas, orden cronológico, Tipo, enlace anterior oculto en pulidos', async () => {
    abrir()
    expect(await screen.findByText(`IMEI: ${A}`)).toBeInTheDocument()
    expect(screen.getByText('• iPhone 16')).toBeInTheDocument()
    expect(screen.getByText('• 3 trabajos')).toBeInTheDocument()
    expect(screen.queryByPlaceholderText('Filtrar por IMEI')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cliente' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Tipo', 'Id', 'IMEI teléfono', 'Modelo', 'Reparador', 'Asignado por', 'Fechas', 'Componente', 'Observaciones', 'Estado', 'Incidencia', 'Id Rep. Anterior'])
    const filas = screen.getAllByRole('row').slice(1)
    expect(filas.map((f) => within(f).getAllByRole('cell')[1].textContent)).toEqual(['R20260910_1', 'G20260912_1', 'P20260913_1'])
    expect(filas.map((f) => within(f).getAllByRole('cell')[0].textContent)).toEqual(['Reparación', 'Glass', 'Pulido'])
    expect(within(filas[0]).getByText('2026/09/10 12:00')).toBeInTheDocument()
    expect(within(filas[1]).getByText('Reutilizado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'AP20260913_1' })).not.toBeInTheDocument()
    expect(filas[0]).toHaveClass('border-l-fila-incidencia-brd')
    expect(ultimoImeiVisto.get()).toBe(A)
    await userEvent.click(screen.getByRole('button', { name: 'Incidencias' }))
    expect(screen.getAllByRole('checkbox').map((c) => c.getAttribute('aria-label'))).toEqual(['Abiertas', 'Cerradas', 'Sin incidencia'])
  })
  it('filtro de técnico: los suyos primero, los ajenos atenuados y el texto "X de filtrados + Y de otros"', async () => {
    filtrosImeis.set({ ...filtrosImeis.get(), tecnicos: new Set([6]) })
    abrir()
    await screen.findByText(`IMEI: ${A}`)
    expect(screen.getByText('• 1 de filtrados + 2 de otros')).toBeInTheDocument()
    const filas = screen.getAllByRole('row').slice(1)
    expect(within(filas[0]).getAllByRole('cell')[1]).toHaveTextContent('R20260910_1')
    expect(filas[0]).not.toHaveClass('opacity-45')
    expect(filas[1]).toHaveClass('opacity-45')
  })
  it('"Borrar" del supertécnico usa el título "Borrar trabajo" y el aviso "Este trabajo está siendo referenciado"', async () => {
    let body: unknown = null
    server.use(
      http.get('*/api/reparaciones/G20260912_1/referenciadora', () => HttpResponse.json({ value: null })),
      http.get('*/api/reparaciones/R20260910_1/referenciadora', () => HttpResponse.json({ value: 'R20260916_6' })),
      http.delete('*/api/reparaciones/G20260912_1', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 204 }) }),
    )
    abrir()
    await screen.findByText(`IMEI: ${A}`)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('G20260912_1') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['Editar', 'Borrar', '📋  Copiar celda', 'Añadir incidencia'])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Borrar' }))
    const dlg = await screen.findByRole('dialog', { name: 'Borrar trabajo' })
    await userEvent.type(within(dlg).getByPlaceholderText('Escribe el motivo del borrado...'), 'error')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Borrar trabajo' }))
    await waitFor(() => expect(body).toEqual({ motivo: 'error' }))
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('R20260910_1') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Borrar' }))
    expect(await screen.findByRole('dialog', { name: 'No se puede borrar' })).toHaveTextContent('Este trabajo está siendo referenciado. La reparación R20260916_6 apunta a esta. Bórrala primero.')
  })
  it('el técnico solo tiene Copiar celda y "← Volver" navega al maestro', async () => {
    abrir(SESION_TEC)
    await screen.findByText(`IMEI: ${A}`)
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('G20260912_1') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).toEqual(['📋  Copiar celda'])
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: '← Volver' })).toBeInTheDocument()
  })
  it('CSV agrupado_<imei> con Tipo y sin ID anterior en pulidos', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    abrir()
    await screen.findByText(`IMEI: ${A}`)
    await userEvent.click(screen.getByRole('button', { name: /Hola,/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    const [base, cabeceras, filasCsv] = descargar.mock.calls[0]
    expect(base).toBe(`agrupado_${A}`)
    expect(cabeceras).toEqual(['Tipo', 'ID', 'IMEI', 'Técnico', 'Fecha asig.', 'Fecha fin', 'Componente', 'Reutilizado', 'Observaciones', 'Incidencia', 'Resuelto', 'ID Rep. anterior'])
    expect(filasCsv[0]).toEqual(['Reparación', 'R20260910_1', `="${A}"`, 'tecnico_b', '10/09/2026 12:00', '11/09/2026 12:00', 'lcdi16negra', 'No', '', 'no enciende', 'No', ''])
    expect(filasCsv[2]).toEqual(['Pulido', 'P20260913_1', `="${A}"`, 'tecnico_i', '13/09/2026 12:00', '13/09/2026 13:00', '', 'No', '', 'No', 'No', ''])
    descargar.mockRestore()
  })
})
```
`renderConProviders` necesita una opción nueva `patron` (la `path` de la `<Route>` cuando la ruta lleva parámetros): en `src/test/render.tsx` añadir `patron?: string` a `Opciones` y usar `<Route path={patron ?? ruta} element={ui} />`.

```bash
npm test -- Imei
```
Expected: fallan (páginas inexistentes).

- [ ] **Step 4: Implementar las páginas**

`src/modules/taller/imeis/ImeisPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { esErrorGestionadoGlobalmente, mensajeDeError, StaleDataError } from '@/shared/api/errors'
import { descargarCsv } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { CeldaFechas } from '@/shared/ui/CeldaFechas'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'
import { DataTable } from '@/shared/ui/DataTable'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { MenuCopiarCelda } from '@/shared/ui/MenuCopiarCelda'
import { PildoraContador } from '@/shared/ui/PildoraContador'
import { SelectorLista } from '@/shared/ui/SelectorLista'
import { TextoExpandible } from '@/shared/ui/TextoExpandible'
import { useClientesActivos, useEditarClienteTelefono, useEditarObservacionTelefono } from '../api'
import { CeldaEstadoTrabajo } from '../componentes/CeldaEstadoTrabajo'
import { DialogoObservacion } from '../componentes/DialogoObservacion'
import { filtrosImeis, ultimoImeiVisto } from '../estado'
import { etiquetaContador } from '../lib/filtros'
import { resumenTipos, type GrupoImei } from '../lib/grupoImei'
import { traducirModelo } from '../lib/modelos'
import { agruparVisibles, opcionesCliente } from './agrupacion'
import { CABECERAS_RESUMEN, filaResumen } from './csvImeis'
import { BarraFiltrosImeis } from './BarraFiltrosImeis'
import { useTrabajos } from './useTrabajos'

export const MSG_TELEFONO_MODIFICADO = 'El teléfono fue modificado por otro usuario. Se recargan los datos.'
const FMT = 'yyyy/MM/dd HH:mm' as const
const SIN_CLIENTE_CLAVE = ''

function textoCeldaGrupo(g: GrupoImei, columna: string): string | null {
  switch (columna) {
    case 'imei': return g.imei
    case 'modelo': return traducirModelo(g.modelo)
    case 'fechas': return `${formatear(g.fechaMasAntigua, FMT) || '—'} → ${formatear(g.fechaMasReciente, FMT) || '—'}`
    case 'trabajos': return resumenTipos(g)
    case 'observacion': return g.observacion ?? ''
    case 'cliente': return g.cliente ?? ''
    default: return null
  }
}

/** Maestro "Agrupado por IMEI": calco de AgrupadoController en modo MAESTRO (sin columna Revisión ni etiqueta "Actualizado"). */
export function ImeisPage() {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const navigate = useNavigate()
  const { mostrarError } = useAlerta()
  const trabajos = useTrabajos()
  const [filtros] = useStore(filtrosImeis)
  const grupos = useMemo(() => agruparVisibles(trabajos, filtros), [trabajos, filtros])
  const clientes = useMemo(() => opcionesCliente(trabajos), [trabajos])
  // Al volver del detalle, el maestro reselecciona ese IMEI (y DataTable desplaza hasta él); el aviso se consume una vez.
  const [seleccionada, setSeleccionada] = useState<string | null>(() => ultimoImeiVisto.get())
  useEffect(() => { ultimoImeiVisto.reset() }, [])
  const [conObservacion, setConObservacion] = useState<GrupoImei | null>(null)
  const [conCliente, setConCliente] = useState<GrupoImei | null>(null)
  const { data: clientesActivos = [] } = useClientesActivos()
  const editarObservacion = useEditarObservacionTelefono()
  const editarCliente = useEditarClienteTelefono()

  const abrir = (imei: string) => navigate(`/reparaciones/imeis/${imei}`)
  const alFallar = (e: unknown) => {
    if (esErrorGestionadoGlobalmente(e)) return
    mostrarError(e instanceof StaleDataError ? MSG_TELEFONO_MODIFICADO : `No se pudo guardar: ${mensajeDeError(e)}`)
  }

  const columnas = useMemo<ColumnDef<GrupoImei>[]>(() => [
    {
      id: 'imei', header: 'IMEI teléfono', size: 180,
      cell: ({ row }) => (
        <div className="flex items-center justify-between gap-2">
          <span className="text-[12px] font-bold text-azul-medio">{row.original.imei}</span>
          <button type="button" aria-label={`Ver trabajos de ${row.original.imei}`} onClick={(e) => { e.stopPropagation(); navigate(`/reparaciones/imeis/${row.original.imei}`) }} className="shrink-0 cursor-pointer">
            <img src="/Historial.png" alt="" className="h-[25px] w-[25px]" />
          </button>
        </div>
      ),
    },
    { id: 'modelo', header: 'Modelo', size: 150, accessorFn: (g) => traducirModelo(g.modelo) },
    { id: 'fechas', header: 'Fechas', size: 130, cell: ({ row }) => <CeldaFechas inicio={row.original.fechaMasAntigua} fin={row.original.fechaMasReciente} patron={FMT} /> },
    { id: 'trabajos', header: 'Trabajos', size: 160, accessorFn: resumenTipos },
    { id: 'estado', header: 'Estado', size: 130, cell: ({ row }) => <CeldaEstadoTrabajo esIncidencia={row.original.incAbiertas > 0} esResuelto={false} /> },
    { id: 'observacion', header: 'Observación', size: 200, cell: ({ row }) => <TextoExpandible titulo="Observación" texto={row.original.observacion} /> },
    { id: 'cliente', header: 'Cliente', size: 200, cell: ({ row }) => <TextoExpandible titulo="Cliente" texto={row.original.cliente} /> },
  ], [navigate])

  useRegistrarExportable(() => descargarCsv('agrupado_resumen', CABECERAS_RESUMEN, grupos.map(filaResumen)))

  const opcionesClienteActivo = [{ clave: SIN_CLIENTE_CLAVE, etiqueta: '— Sin cliente —' }, ...clientesActivos.map((c) => ({ clave: String(c.idCli), etiqueta: c.nombre }))]
  const claveClienteActual = conCliente?.cliente ? (clientesActivos.find((c) => c.nombre === conCliente.cliente)?.idCli.toString() ?? null) : null

  return (
    <div className="p-10">
      <div className="mb-2 flex items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Agrupado por IMEI</h1>
        <PildoraContador texto={etiquetaContador(grupos.length, 'IMEI', 'IMEIs')} />
      </div>
      <BarraFiltrosImeis modo="maestro" opcionesCliente={clientes} />
      <DataTable
        columns={columnas}
        data={grupos}
        vacio=""
        ajuste="estirar"
        getRowId={(g) => g.imei}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        onAbrir={(g) => abrir(g.imei)}
        filaClase={(g) => cn('cursor-pointer border-l-4 bg-fila-maestro-bg', g.incAbiertas > 0 ? 'border-l-fila-incidencia-brd' : 'border-l-azul-medio')}
        menuFila={(g, celda) => (
          <>
            <MenuCopiarCelda texto={textoCeldaGrupo(g, celda.columnaId)} celda={celda} />
            {puedeEditar && g.telefonoUpdatedAt !== null && (
              <>
                <ContextMenuSeparator />
                <ContextMenuItem onSelect={() => setConObservacion(g)}>Editar observación</ContextMenuItem>
                <ContextMenuSeparator />
                <ContextMenuItem onSelect={() => setConCliente(g)}>Editar cliente</ContextMenuItem>
              </>
            )}
          </>
        )}
      />
      <DialogoObservacion grupo={conObservacion} onCerrar={() => setConObservacion(null)}
        onGuardar={(observacion) => {
          if (!conObservacion?.telefonoUpdatedAt) return
          const g = conObservacion
          setConObservacion(null)
          editarObservacion.mutate({ imei: g.imei, observacion, updatedAt: g.telefonoUpdatedAt }, { onError: alFallar })
        }} />
      <SelectorLista abierto={conCliente !== null} titulo="Seleccionar cliente" placeholderBuscar="Buscar cliente..." opciones={opcionesClienteActivo} claveActual={claveClienteActual}
        textoNada="Nada seleccionado" textoSeleccionar="Seleccionar" onCancelar={() => setConCliente(null)}
        onSeleccionar={(clave) => {
          if (!conCliente?.telefonoUpdatedAt) return
          const g = conCliente
          setConCliente(null)
          editarCliente.mutate({ imei: g.imei, idCli: clave === SIN_CLIENTE_CLAVE ? null : Number(clave), updatedAt: g.telefonoUpdatedAt }, { onError: alFallar })
        }} />
    </div>
  )
}
```
Nota: el DataTable pinta el borde izquierdo de la fila seleccionada con su propio `data-[state=selected]:bg-azul-medio` (el color azul medio de la ficha); `filaClase` solo aporta el fondo `fila-maestro-bg` y el borde de 4 px. El icono para el botón lleva `stopPropagation` para no disparar además la selección/doble clic de la fila. "Editar observación" / "Editar cliente" solo se ofrecen cuando el grupo tiene `telefonoUpdatedAt` (hay fila Telefono): el contrato exige `updatedAt` no nulo (Task 3) y el JavaFX en ese caso fallaba con "No se pudo guardar"; anotarlo en la ficha de IMEIs como diferencia aceptada.

`src/modules/taller/imeis/ImeiDetallePage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { descargarCsv } from '@/shared/lib/csv'
import { useStore } from '@/shared/lib/store'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { BotonSecundario } from '@/shared/ui/Botones'
import { DataTable } from '@/shared/ui/DataTable'
import { useRegistrarExportable } from '@/shared/ui/exportable'
import { MenuHistorial } from '../componentes/MenuHistorial'
import { TITULOS_BORRAR, useAccionesTrabajo } from '../componentes/useAccionesTrabajo'
import { filtrosImeis, ultimoImeiVisto } from '../estado'
import { columnasTrabajo, textoCeldaTrabajo } from '../historial/columnasTrabajo'
import { estadoIncidencia } from '../lib/filtros'
import { traducirModelo } from '../lib/modelos'
import { esAjeno, filasDetalle, textoTrabajos } from './agrupacion'
import { CABECERAS_DETALLE, filaDetalle } from './csvImeis'
import { BarraFiltrosImeis } from './BarraFiltrosImeis'
import { useTrabajos } from './useTrabajos'

const FMT = 'yyyy/MM/dd HH:mm' as const

/** Detalle de un IMEI: calco de AgrupadoController en modo DETALLE (barra "← Volver · IMEI · modelo · N trabajos" y tabla de trabajos con Tipo). */
export function ImeiDetallePage() {
  const { imei = '' } = useParams()
  const navigate = useNavigate()
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const trabajos = useTrabajos()
  const [filtros] = useStore(filtrosImeis)
  // El maestro lo reselecciona al volver (también con el botón atrás del navegador).
  useEffect(() => { ultimoImeiVisto.set(imei) }, [imei])
  const detalle = useMemo(() => filasDetalle(trabajos, imei, filtros), [trabajos, imei, filtros])
  const modelo = traducirModelo(trabajos.find((t) => t.imei === imei && t.modelo)?.modelo)
  const [seleccionada, setSeleccionada] = useState<string | null>(null)
  const { acciones, dialogos } = useAccionesTrabajo({ tituloBorrar: TITULOS_BORRAR.trabajo, avisoReferencia: 'Este trabajo está siendo referenciado' })
  const columnas = useMemo(() => columnasTrabajo({ conTipo: true, patronFechas: FMT, tituloId: 'Id', onIrA: setSeleccionada }), [])
  const conFiltroTecnico = filtros.tecnicos.size > 0

  useRegistrarExportable(() => descargarCsv(`agrupado_${imei}`, CABECERAS_DETALLE, detalle.filas.map(filaDetalle)))

  return (
    <div className="p-10">
      <div className="mb-2 flex items-center gap-3">
        <BotonSecundario onClick={() => navigate('/reparaciones/imeis')}>← Volver</BotonSecundario>
        <span aria-hidden="true" className="h-6 w-px bg-fila-sep" />
        <span className="text-[13px] font-bold text-azul-medio">IMEI: {imei}</span>
        {modelo && <span className="text-[12px] text-azul-gris">• {modelo}</span>}
        <span className="text-[12px] text-azul-gris">{textoTrabajos(detalle, conFiltroTecnico)}</span>
      </div>
      <BarraFiltrosImeis modo="detalle" />
      <DataTable
        columns={columnas}
        data={detalle.filas}
        vacio=""
        ajuste="estirar"
        getRowId={(r) => r.idRep}
        seleccionada={seleccionada}
        onSeleccionar={setSeleccionada}
        filaClase={(r) => cn('border-l-8', estadoIncidencia(r) === 'abiertas' ? 'border-l-fila-incidencia-brd' : estadoIncidencia(r) === 'cerradas' ? 'border-l-fila-reparado-brd' : 'border-l-transparent', esAjeno(r, filtros) && 'opacity-45')}
        menuFila={(r, celda) => <MenuHistorial rep={r} celda={celda} texto={textoCeldaTrabajo(r, celda.columnaId, FMT)} puedeEditar={puedeEditar} acciones={acciones} />}
      />
      {dialogos}
    </div>
  )
}
```
`traducirModelo` devuelve `''` con `undefined`/`null` (Task 11), de ahí el `{modelo && ...}`.

`router.tsx`: `/reparaciones/imeis` → `<ImeisPage />`, `/reparaciones/imeis/:imei` → `<ImeiDetallePage />` (con esto desaparece el último `PendienteDeMigrar` de `/reparaciones/*`; `Asignaciones` sigue siendo placeholder hasta el sub-proyecto 3).

- [ ] **Step 5: Verde y commit**

```bash
npm run check
git add src/modules/taller src/app/router.tsx src/test/render.tsx
git commit -m "feat(web): IMEIs: maestro agrupado (filtros, observacion y cliente del telefono, CSV) y detalle por IMEI (Tipo, filtro de tecnico atenuado, borrado e incidencias, CSV)"
```

---

### Task 19: Cierre — smoke e2e, capturas web, documentación, versión 0.2.0 y verificación final

**Files:**
- Create: `tests/e2e/taller.spec.ts`
- Modify (web): `.env.e2e.example` (+`TEC_USER`, `TEC_PASS`), `README.md`, `CHANGELOG.md`, `package.json` + `package-lock.json` (0.2.0), `docs/paridad/{pendientes,historial,imeis}.md` (casillas)
- Modify (Apuntes, fuera de los repos): `Apuntes/herramientas/paridad-capturas/capturas-web.mjs` (vistas del taller), `Apuntes/web-esqueleto-directorios.md` (módulo `taller`), `Apuntes/plan-futuro.md` (§9)
- Modify (raíz, al final y solo tras los merges): gitlinks + este plan con las casillas marcadas

- [ ] **Step 1: Smoke Playwright del taller**

`.env.e2e.example`:

```
E2E_BASE_URL=https://erp.fonestore.es
E2E_USER=<supertecnico>
E2E_PASS=<contraseña>
TEC_USER=<tecnico con pendientes>
TEC_PASS=<contraseña>
```

`tests/e2e/taller.spec.ts`:

```ts
import { expect, test } from '@playwright/test'

/** Recorrido del técnico por el taller: Pendientes con filas → Historial → IMEIs → detalle → volver. Solo lectura. */
test('técnico: pendientes, historial, IMEIs y detalle', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(process.env.TEC_USER!)
  await page.getByPlaceholder('Contraseña').fill(process.env.TEC_PASS!)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()

  // Entrada del técnico: Pendientes, con el toggle Reparaciones activo y al menos una fila
  await expect(page).toHaveURL(/\/reparaciones\/pendientes$/)
  await expect(page.getByRole('heading', { name: 'Mis asignaciones pendientes' })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Reparaciones \(/ })).toHaveAttribute('aria-current', 'page')
  await expect(page.getByRole('table').getByRole('row').nth(1)).toBeVisible()

  await page.getByRole('link', { name: 'Historial' }).click()
  await expect(page.getByRole('heading', { name: 'Mis reparaciones' })).toBeVisible()
  await expect(page.getByText(/^Actualizado [0-9][0-9]:[0-9][0-9]$/)).toBeVisible()

  await page.getByRole('link', { name: 'IMEIs' }).click()
  await expect(page.getByRole('heading', { name: 'Agrupado por IMEI' })).toBeVisible()
  const primera = page.getByRole('table').getByRole('row').nth(1)
  await expect(primera).toBeVisible()
  const imei = (await primera.getByRole('cell').first().innerText()).trim().split(/\s/)[0]
  await primera.getByRole('button', { name: `Ver trabajos de ${imei}` }).click()
  await expect(page).toHaveURL(new RegExp(`/reparaciones/imeis/${imei}$`))
  await expect(page.getByText(`IMEI: ${imei}`)).toBeVisible()

  await page.getByRole('button', { name: '← Volver' }).click()
  await expect(page.getByRole('heading', { name: 'Agrupado por IMEI' })).toBeVisible()
  await expect(page.getByRole('row', { name: new RegExp(imei) })).toHaveAttribute('aria-selected', 'true')
})

/** El supertécnico entra por Historial y ve el filtro de técnico. */
test('supertécnico: entra en Historial de reparaciones con filtro de técnico', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(process.env.E2E_USER!)
  await page.getByPlaceholder('Contraseña').fill(process.env.E2E_PASS!)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page).toHaveURL(/\/reparaciones\/historial$/)
  await expect(page.getByRole('heading', { name: 'Historial de reparaciones' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Técnico' })).toBeVisible()
})
```
Se ejecuta en el Step 6 contra producción, no ahora (necesita el servidor con `?tecnico=` y los contadores desplegado).

- [ ] **Step 2: Documentación del repo web y versión**

`CHANGELOG.md`, encima de `## [0.1.0]`:

```markdown
## [0.2.0] - <fecha del tag> — Taller técnico

- Pendientes del técnico: reparaciones, glass y pulidos (filtros, badges de estado, entrega a glass y llegada, papelera del supertécnico, completar pulidos en lote, CSV).
- Historial: reparaciones, glass y pulidos (filtros por rol, borrado con motivo, incidencias, editar modelo, CSV).
- IMEIs: maestro agrupado por IMEI (observación y cliente del teléfono) y detalle por IMEI.
- Columna lateral de Reparaciones por rol con contador de pendientes; entrada por rol.
- Refresco periódico (60 s, 5 s sin conexión) con etiqueta "Actualizado HH:mm".
- `DataTable` con anchos fijos (`colgroup`), selección, teclado y virtualización.
- Contrato OpenAPI con `required`/`nullable` explícitos (sin `Required<>` en el cliente).
- Servidor: `?tecnico=` verificado contra el token y `GET /api/reparaciones/pendientes/contadores`.
- Diferencias aceptadas: sin columna "Revisión" en IMEIs; "Editar" del historial deshabilitado hasta el formulario de reparación (sub-proyecto 2).
```

`README.md`: en "Smoke e2e" añadir que `taller.spec.ts` necesita además `TEC_USER`/`TEC_PASS` (un técnico con pendientes) y es de solo lectura; en "Documentación" añadir la spec del taller (`docs/superpowers/specs/2026-09-16-web-taller-design.md`, repo raíz) y las fichas `docs/paridad/{pendientes,historial,imeis}.md`.

```bash
npm version 0.2.0 --no-git-tag-version
git diff --stat package.json package-lock.json
```
Expected: ambos ficheros con `"version": "0.2.0"`.

- [ ] **Step 3: Fichas de paridad**

Recorrer `docs/paridad/pendientes.md`, `historial.md` e `imeis.md` marcando `[x]` cada casilla cubierta por un test de vista o por la implementación; lo que no se cumpla queda `[ ]` con una nota en "Diferencias aceptadas" o se corrige antes del commit. La revisión final (Step 7) usa estas fichas como lista de comprobación.

```bash
npm run check
git add .env.e2e.example tests/e2e/taller.spec.ts README.md CHANGELOG.md package.json package-lock.json docs/paridad
git commit -m "chore(web): 0.2.0: smoke e2e del taller, changelog, README y fichas de paridad marcadas"
```

- [ ] **Step 4: Apuntes (fuera de los repos)**

`Apuntes/herramientas/paridad-capturas/capturas-web.mjs`: tras el bloque de Clientes del SUPERTECNICO y antes de "cerrar sesion", añadir los pasos del taller; y en el bloque del TECNICO, tras "lista-tecnico + clic derecho". Cada paso sigue el patrón `paso(nombre, fn)` + `shot(page, nombre)`; `OUT` pasa a leerse solo de `OUT_DIR` (sin ruta por defecto al scratchpad antiguo).

```js
// ---------- SUPERTECNICO: taller ----------
await paso('historial-supertecnico', async () => {
  await page.getByRole('link', { name: 'Reparaciones' }).click()
  await page.getByRole('heading', { name: 'Historial de reparaciones' }).waitFor()
  await page.getByRole('table').getByRole('row').nth(1).waitFor()
  await page.waitForTimeout(500)
  await shot(page, 'historial-supertecnico')
})
await paso('historial-menu-contextual-supertecnico', async () => {
  await page.getByRole('table').getByRole('row').nth(1).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Borrar' }).waitFor()
  console.log('     items:', JSON.stringify(await page.getByRole('menuitem').allTextContents()))
  await shot(page, 'historial-menu-contextual-supertecnico')
  await page.keyboard.press('Escape')
})
await paso('historial-filtro-tecnico', async () => {
  await page.getByRole('button', { name: 'Técnico' }).click()
  await page.getByRole('checkbox').first().waitFor()
  await shot(page, 'historial-filtro-tecnico')
  await page.keyboard.press('Escape')
})
await paso('historial-glass-supertecnico', async () => {
  await page.getByRole('link', { name: 'Glass' }).click()
  await page.waitForTimeout(800)
  await shot(page, 'historial-glass-supertecnico')
})
await paso('historial-pulidos-supertecnico', async () => {
  await page.getByRole('link', { name: 'Pulidos' }).click()
  await page.getByRole('heading', { name: 'Historial de pulidos' }).waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'historial-pulidos-supertecnico')
})
await paso('imeis-maestro-supertecnico', async () => {
  await page.getByRole('link', { name: 'IMEIs' }).click()
  await page.getByRole('heading', { name: 'Agrupado por IMEI' }).waitFor()
  await page.getByRole('table').getByRole('row').nth(1).waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'imeis-maestro-supertecnico')
})
await paso('imeis-maestro-menu-contextual-supertecnico', async () => {
  await page.getByRole('table').getByRole('row').nth(1).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Editar cliente' }).waitFor()
  await shot(page, 'imeis-maestro-menu-contextual-supertecnico')
  await page.keyboard.press('Escape')
})
await paso('imeis-detalle-supertecnico', async () => {
  await page.getByRole('table').getByRole('row').nth(1).dblclick()
  await page.getByRole('button', { name: '← Volver' }).waitFor()
  await page.waitForTimeout(500)
  await shot(page, 'imeis-detalle-supertecnico')
  await page.getByRole('table').getByRole('row').nth(1).click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Borrar' }).waitFor()
  await shot(page, 'imeis-detalle-menu-contextual-supertecnico')
  await page.keyboard.press('Escape')
})
await paso('imeis-maestro-tras-volver', async () => {
  await page.getByRole('button', { name: '← Volver' }).click()
  await page.getByRole('heading', { name: 'Agrupado por IMEI' }).waitFor()
  await page.waitForTimeout(500)
  await shot(page, 'imeis-maestro-tras-volver')
})
```

```js
// ---------- TECNICO: taller ----------
await paso('pendientes-tecnico', async () => {
  await page.getByRole('link', { name: 'Reparaciones' }).click()
  await page.getByRole('heading', { name: 'Mis asignaciones pendientes' }).waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'pendientes-tecnico')
})
await paso('pendientes-glass-tecnico', async () => {
  await page.getByRole('link', { name: /^Glass \(/ }).click()
  await page.waitForTimeout(800)
  await shot(page, 'pendientes-glass-tecnico')
})
await paso('pendientes-pulidos-tecnico', async () => {
  await page.getByRole('link', { name: /^Pulidos \(/ }).click()
  await page.getByRole('heading', { name: 'Mis pulidos pendientes' }).waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'pendientes-pulidos-tecnico')
})
await paso('historial-tecnico', async () => {
  await page.getByRole('link', { name: 'Historial' }).click()
  await page.getByRole('heading', { name: 'Mis reparaciones' }).waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'historial-tecnico')
})
await paso('imeis-maestro-tecnico', async () => {
  await page.getByRole('link', { name: 'IMEIs' }).click()
  await page.getByRole('heading', { name: 'Agrupado por IMEI' }).waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'imeis-maestro-tecnico')
})
await paso('imeis-detalle-tecnico', async () => {
  await page.getByRole('table').getByRole('row').nth(1).dblclick()
  await page.getByRole('button', { name: '← Volver' }).waitFor()
  await page.waitForTimeout(500)
  await shot(page, 'imeis-detalle-tecnico')
})
```
Los textos de los toggles del TECNICO llevan el sufijo `(N)`, de ahí las expresiones regulares. Solo lectura: ningún paso pulsa Borrar, Completar ni Guardar.

`Apuntes/web-esqueleto-directorios.md`: cambiar "Estado a 2026-09-15 (sub-proyecto 0, Cimientos)" por la fecha del cierre y "sub-proyecto 1, Taller"; sustituir la línea `taller/ (vacío hasta el sub-proyecto 1...)` por el árbol real (`api.ts`, `estado.ts`, `rutas.tsx`, `lib/`, `componentes/`, `pendientes/`, `historial/`, `imeis/`) con una frase por carpeta, y en la tabla de equivalencias añadir `PendientesTecnicoController` → `pendientes/PendientesPage.tsx`, `HistorialController` → `historial/HistorialPage.tsx`, `AgrupadoController` → `imeis/{ImeisPage,ImeiDetallePage}.tsx`, `EntregaGlass`/`GrupoImei`/`Piezas` → `lib/`. Añadir también `src/shared/session/` como casa de `SessionProvider` (ya no está en `app/session/`) y `src/shared/ui/exportable.tsx`.

`Apuntes/plan-futuro.md` §9: marcar `[x] 1 Taller técnico ...` con la fecha y los commits de merge/tag cuando existan (Step 8).

- [ ] **Step 5: Capturas de la web contra el build de la rama con proxy a producción**

En una terminal (el login lo hacen los scripts con las variables de `~/.env.e2e`, que el usuario carga; nunca por chat):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
set -a; . ~/.env.e2e; set +a
VITE_API_PROXY_TARGET=https://erp.fonestore.es npm run build && npx vite preview --port 4173 &
E2E_BASE_URL=http://localhost:4173 OUT_DIR=/c/Users/dev/Documents/Apuntes/paridad-capturas/taller-web node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/capturas-web.mjs
```
Expected: `ok` en cada paso y PNGs en `Apuntes/paridad-capturas/taller-web/`. Comparar lado a lado con `Apuntes/paridad-capturas/taller/` (mismo nombre base) y anotar en las fichas lo que difiera; el usuario revisa las capturas y comprueba ADMIN en el navegador. **Ojo:** el build de la rama apunta a producción, donde el servidor aún no tiene `?tecnico=` verificado ni contadores: hasta el despliegue (Step 8) el badge de pendientes y el SUPERTECNICO en Pendientes verán el 404 del endpoint de contadores como banner. Por eso las capturas de Pendientes se hacen con el TECNICO (no usa `?tecnico=`) y las del contador se repiten tras el despliegue.

- [ ] **Step 6: Revisión final de la rama (multi-lente) y correcciones**

Revisión del diff completo `main...feature/web-taller` de web y servidor con tres lentes, cada una un subagente revisor con esfuerzo alto: (1) paridad contra las fichas y las capturas; (2) capas, tipos y tests (fronteras de lint, `client.ts` sin `Required<>`, contrato idéntico `target/openapi.json` ↔ `api/openapi.json`, textos literales); (3) seguridad y errores (`?tecnico=` en los seis endpoints, 403 al pedir otro técnico, ningún `?tecnico=` desde TECNICO, 409/422 con sus mensajes, nada sensible en docs). Cada hallazgo se corrige en un commit propio (`fix(web): ...` / `fix(servidor): ...`) con su test.

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test && cmp target/openapi.json ../gestion-reparaciones-web/api/openapi.json && echo CONTRATO_IDENTICO
cd ../gestion-reparaciones-web && npm run check && grep -c "Required<[A-Za-z]" src/shared/api/client.ts
```
Expected: tests en verde, `CONTRATO_IDENTICO`, `0` (el patrón con letra ignora la mención `Required<>` del JSDoc de `client.ts`; `grep -c` sale con 1 cuando no hay coincidencias, es lo esperado).

- [ ] **Step 7: Merges, tag y despliegue (solo con el OK del usuario, uno a uno)**

Orden, pidiendo confirmación antes de cada línea y sin ejecutar nada de esto por cuenta propia:

```bash
# servidor
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git checkout main && git merge --no-ff feature/web-taller -m "Merge branch 'feature/web-taller' (?tecnico= verificado, contadores de pendientes, contrato required/nullable)"
git push origin main
# web
cd ../gestion-reparaciones-web && git checkout main && git merge --no-ff feature/web-taller -m "Merge branch 'feature/web-taller' (taller: pendientes, historial, IMEIs)"
git tag -a v0.2.0 -m "Taller técnico: pendientes, historial e IMEIs"
git push origin main --tags
```
Después el usuario reconstruye backend y web en la VM con el runbook (`Apuntes/despliegue_vdc_produccion.md`; Claude no hace SSH), y se ejecuta el smoke contra producción:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && set -a; . ~/.env.e2e; set +a; npm run e2e
```
Expected: `clientes.spec.ts` y `taller.spec.ts` en verde. Si algo falla, se corrige en una rama `fix/...` y se repite este paso; no se tapa con un reintento.

- [ ] **Step 8: Raíz, memoria y ledger**

En el repo raíz (`main`): `git add gestion-reparaciones-web gestion-reparaciones-servidor docs/superpowers/plans/2026-09-16-web-taller.md` (gitlinks a los merges y este plan con las casillas marcadas) y commit `chore: gitlinks servidor y web tras el sub-proyecto 1 (web v0.2.0) y plan cerrado`. Sin push salvo OK. Marcar `Apuntes/plan-futuro.md` §9 y actualizar la memoria del proyecto (sub-proyecto 1 CERRADO, fecha, commits, deuda que queda) y `.superpowers/sdd/progress.md`.

---

## Cobertura de la spec

| Spec | Tasks |
|---|---|
| §4 rutas, entrada por rol, refresco 60 s / 5 s, ADMIN sin sondeo | 8, 13, 16, 18 |
| §5.1 `?tecnico=` verificado en los seis endpoints (403) | 1 |
| §5.2 contadores | 2, 13 (badge), 14 (toggles) |
| §5.3 nullabilidad del contrato, `referenciadora` tipada | 3, 4 |
| §5.4 tests del servidor | 1, 2, 3 |
| §6 módulo taller (estructura, lógica pura, compartidos, deuda de Cimientos) | 5–13 |
| §7.1 Pendientes rep/glass · §7.2 pulidos | 14, 15 |
| §7.3 Historial rep/glass · §7.4 pulidos | 16, 17 |
| §7.5 IMEIs maestro · §7.6 detalle | 18 |
| §8 errores (403/409/422, "No se pudo guardar", aviso de referencia) | 13 (meta), 16, 18 |
| §9 diferencias aceptadas (sin Revisión, Editar deshabilitado, CSV descarga, modales) | 14, 16, 18, fichas en 19 |
| §10 fichas, tests, capturas web, smoke | 19 |
| §11 criterios de cierre (contrato idéntico, `Required<>` = 0, merges/tag con OK) | 19 |
