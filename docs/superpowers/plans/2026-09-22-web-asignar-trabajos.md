# Sub-proyecto 3b — El modal "Asignar trabajos": plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activar el botón "Asignar" de la vista de asignaciones con el modal real de tres colas (Reparación, Glass, Pulido), y subir al servidor la predicción de glass y un guardado por lotes atómico con `Idempotency-Key`.

**Architecture:** El servidor gana `PrediccionGlass` portada sin cambios, un endpoint de cálculo (`POST /api/glass/prediccion`) y un endpoint de escritura por lotes (`POST /api/asignaciones/lote`) cuya transacción vive en un servicio nuevo. La web gana `modules/taller/asignaciones/modal/`: un reductor puro partido por temas que emite **efectos** (lookups, predicción, guardado del modelo) a una cola que un hook consume, más los componentes que lo pintan.

**Tech Stack:** Servidor Spring Boot 3.3 + JdbcTemplate + JUnit 5 + Mockito + MockMvc. Web React 19 + TypeScript + TanStack Query + openapi-fetch + Vitest + Testing Library + MSW + Playwright.

**Spec:** [`docs/superpowers/specs/2026-09-22-web-asignar-trabajos-design.md`](../specs/2026-09-22-web-asignar-trabajos-design.md). Las decisiones D1-D6 de su §3 son vinculantes. **Referencia de detalle:** el inventario del código del modal, guardado fuera del repo (lo tiene el controlador de la sesión; si una regla de este plan no cuadra con el JavaFX, manda el JavaFX y se consulta).

## Global Constraints

- **Ramas:** `feature/web-asignar-trabajos` en `gestion-reparaciones-web` y en `gestion-reparaciones-servidor`, creadas desde `main`. El repo raíz se queda en `main`.
- **Nunca** `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Commits sin `Co-Authored-By`.** Mensajes en español, en minúscula tras el prefijo.
- **Los tres repos son públicos:** ningún dato real (IMEIs, clientes, nombres de técnicos, dominios, IPs) en código, tests, comentarios ni documentación. IMEIs de test sintéticos (`111111111111111`, `222222222222222`…), técnicos "Técnico A", "javi"/"jhona" solo como en los tests portados del cliente (ya públicos).
- **El cliente JavaFX NO se toca.** Se consulta en solo lectura con `git show hotfix/0.16.3:gestion-reparaciones-cliente/<ruta>` desde el raíz.
- **Todo el trabajo de servidor es aditivo:** los endpoints de alta sueltos y cualquier respuesta que el JavaFX consuma no cambian.
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`
- **Antes de cada tarea, buscar en el repo lo que va a crear** (lección del 3a): si ya existe, se reutiliza y se anota la desviación.
- **Cada petición se verifica contra el contrato OpenAPI real** (`src/shared/api/schema.d.ts`) antes de escribir el código que la usa. Si un nombre de esquema no coincide con este plan, manda el contrato.
- **Textos visibles exactos** (se copian tal cual): ver cada tarea; ninguno se reescribe "mejorado".

---

## Estructura de ficheros

**Servidor** (`gestion-reparaciones-servidor`)

| Fichero | Responsabilidad |
|---|---|
| `src/main/java/com/reparaciones/servidor/service/PrediccionGlass.java` | *Crear.* Port literal del cliente |
| `src/main/java/com/reparaciones/servidor/model/PrediccionGlassRespuesta.java` | *Crear.* `{ idTec, nombre }`, ambos anulables |
| `src/main/java/com/reparaciones/servidor/controller/GlassController.java` | *Modificar.* `POST /prediccion`; inyecta `TecnicoDAO` |
| `src/main/java/com/reparaciones/servidor/model/LoteAsignaciones.java` | *Crear.* Records de petición y respuesta del lote |
| `src/main/java/com/reparaciones/servidor/service/AsignacionLoteService.java` | *Crear.* La transacción del lote |
| `src/main/java/com/reparaciones/servidor/controller/AsignacionController.java` | *Crear.* `POST /api/asignaciones/lote`: clave, validación, lookup de pulido, logs |
| `src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java` | *Crear.* Los 16 tests portados |
| `src/test/java/com/reparaciones/servidor/controller/PrediccionGlassControllerTest.java` | *Crear.* Roles, elección, sin candidato, degradación |
| `src/test/java/com/reparaciones/servidor/service/AsignacionLoteServiceTest.java` | *Crear.* Orden, conflictos, categorías, propagación del error |
| `src/test/java/com/reparaciones/servidor/controller/AsignacionControllerTest.java` | *Crear.* Clave, reintento, validación, lookup, logs (unitario) |
| `src/test/java/com/reparaciones/servidor/controller/RolesAsignacionLoteTest.java` | *Crear.* Roles con MockMvc |
| `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` | *Modificar.* Las dos rutas nuevas |

**Web** (`gestion-reparaciones-web`)

| Fichero | Responsabilidad |
|---|---|
| `src/shared/api/schema.d.ts`, `src/shared/api/client.ts` | *Modificar.* Contrato regenerado y alias de tipos |
| `src/shared/lib/pegadoImei.ts` (+ test) | *Crear.* Port de `parsearPegadoImeis` |
| `src/shared/ui/CampoAutocompletar.tsx` (+ test) | *Crear.* Campo navy con popup (modelo y cliente) |
| `src/modules/taller/asignaciones/modal/estado/tipos.ts` | *Crear.* Tipos del estado, acciones y efectos |
| `…/modal/estado/base.ts` | *Crear.* Buscar/actualizar entradas, emitir efectos |
| `…/modal/estado/derivados.ts` (+ test) | *Crear.* Ocupados, glass en BD, verdes, contadores, barra, habilitados |
| `…/modal/estado/cliente.ts` (+ test) | *Crear.* Precedencia y propagación del cliente |
| `…/modal/estado/glass.ts` (+ test) | *Crear.* Casilla ⇔ glass, predicción |
| `…/modal/estado/colas.ts` (+ test) | *Crear.* Escanear, pegar, cargar, quitar, modelo, técnicos, asignar |
| `…/modal/estado/pulido.ts` (+ test) | *Crear.* Panel de Pulido |
| `…/modal/estado/reductor.ts` (+ test) | *Crear.* Solo compone |
| `…/modal/lote.ts` (+ test) | *Crear.* Estado → cuerpo del lote |
| `…/modal/api.ts` (+ test) | *Crear.* Mutación del lote, clientes, ejecutores de efectos |
| `…/modal/useEfectosModal.ts` (+ test) | *Crear.* Consume la cola de efectos |
| `…/modal/ListaTecnicos.tsx`, `FilaCola.tsx` (+ tests) | *Crear.* Piezas de presentación |
| `…/modal/DetalleEntrada.tsx`, `PanelRico.tsx` (+ tests) | *Crear.* Reparación y Glass |
| `…/modal/PanelPulido.tsx` (+ test) | *Crear.* Pulido |
| `…/modal/AsignarTrabajosDialog.tsx` (+ test) | *Crear.* El modal entero, guardar y descartar |
| `src/modules/taller/asignaciones/AsignacionesPage.tsx` (+ tests) | *Modificar.* Botón activo, congelación |
| `tests/e2e/asignar.spec.ts` | *Crear.* Smoke |
| `docs/paridad/asignar-trabajos.md`, `CHANGELOG.md`, `package.json` | *Crear/Modificar.* Ficha, versión 0.5.0 |

---

## Task 1: Servidor — `PrediccionGlass` portada con sus 16 tests

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/service/PrediccionGlass.java`
- Test: `src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java`

**Interfaces:**
- Consumes: `CargaTecnicos.fraccion9h(ReparacionResumen, boolean, boolean)` (package-private, mismo paquete), `CargaTecnicos.TOPE_*_9H`, `TipoTrabajo.desde`, `model.Tecnico`, `model.ReparacionResumen`.
- Produces: `PrediccionGlass.elegir(List<Tecnico>, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, List<VerdeEnModal>, String imei, boolean conCliente) → Tecnico | null`; `public record PrediccionGlass.VerdeEnModal(String imei, int idTec, TipoTrabajo tipo, boolean esChasis, boolean conCliente)`.

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git checkout -b feature/web-asignar-trabajos
```

- [ ] **Step 2: Portar el test**

Copiar `git show hotfix/0.16.3:gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/PrediccionGlassTest.java` a `src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java` con **solo** estos cambios:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static com.reparaciones.servidor.service.TipoTrabajo.GLASS;
import static com.reparaciones.servidor.service.TipoTrabajo.REPARACION;
import static com.reparaciones.servidor.service.PrediccionGlass.VerdeEnModal;
```

y el helper `asig` con el constructor de 21 argumentos del servidor:

```java
    private static ReparacionResumen asig(String idRep, String imei, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, imei, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null, null, 0, false, null, null, null);
        r.setCliente(cliente);
        return r;
    }
```

`tec(...)` no cambia: `new Tecnico(id, nombre, activo, true, esGlass)` tiene el mismo orden en el servidor. Los 16 `@Test` se copian **sin tocar una expectativa**.

- [ ] **Step 3: Ejecutarlo y ver que falla**

Run: `mvn -q test -Dtest=PrediccionGlassTest`
Expected: FAIL de compilación, `PrediccionGlass` no existe.

- [ ] **Step 4: Portar la clase**

`src/main/java/com/reparaciones/servidor/service/PrediccionGlass.java`: el fichero del cliente (`git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/PrediccionGlass.java`) con el paquete y los imports cambiados:

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
```

El resto (javadoc, `VerdeEnModal` con `fraccion9h()`, `elegir`, `carga`) se copia literal. `CargaTecnicos.fraccion9h` ya es accesible por estar en el mismo paquete.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest=PrediccionGlassTest`
Expected: PASS, 16 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/service/PrediccionGlass.java src/test/java/com/reparaciones/servidor/service/PrediccionGlassTest.java
git commit -m "feat(servidor): prediccion de glass portada del cliente con sus 16 tests"
```

---

## Task 2: Servidor — `POST /api/glass/prediccion`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/PrediccionGlassRespuesta.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/GlassController.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/PrediccionGlassControllerTest.java`

**Interfaces:**
- Consumes: Task 1; `ReparacionDAO.getAsignaciones/getAsignacionesGlass/getAsignacionesPulido(Integer)`, `ReparacionDAO.getAsignacionesCompletadasHoy(Timestamp)`, `UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(Clock)`, `TecnicoDAO.getAllActivos()`.
- Produces: `POST /api/glass/prediccion`, cuerpo `GlassPrediccionRequest { imei, conCliente, verdes: GlassVerdeRequest[] }` (`tipo` = `"REPARACION"|"GLASS"`), respuesta `PrediccionGlassRespuesta { idTec: number|null, nombre: string|null }`.

- [ ] **Step 1: Escribir el test del endpoint**

Mismo patrón que `CargaTecnicosControllerTest` (cópiese su cabecera: `@SpringBootTest`, `@AutoConfigureMockMvc`, las mismas `@TestPropertySource`, JWT real con `JwtUtil`, `@MockBean ReparacionDAO dao` y `@MockBean TecnicoDAO tecnicoDao`, y los helpers `tecnico()`, `supertecnico()`, `admin()`).

```java
class PrediccionGlassControllerTest {
    private static final String IMEI = "111111111111111";
    private static final String CUERPO = """
            {"imei":"111111111111111","conCliente":true,"verdes":[]}""";

    // cabecera, campos y tokens copiados de CargaTecnicosControllerTest

    private ReparacionResumen asig(String idRep, String imei, int idTec, String cliente) {
        ReparacionResumen r = new ReparacionResumen(idRep, imei, null, null, null, null, null,
                false, false, null, null, idTec, 0, null, null, null, 0, false, null, null, null);
        r.setCliente(cliente);
        return r;
    }

    private void dosHabilitados() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(
                new Tecnico(1, "javi", true, false, true),
                new Tecnico(2, "jhona", true, false, true)));
    }

    private ResultActions predecir(String token, String cuerpo) throws Exception {
        return mvc.perform(post("/api/glass/prediccion").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test void tecnicoYAdminReciben403() throws Exception {
        predecir(tecnico(), CUERPO).andExpect(status().isForbidden());
        predecir(admin(), CUERPO).andExpect(status().isForbidden());
    }

    @Test void eligeAlDeMenorCarga() throws Exception {
        dosHabilitados();
        when(dao.getAsignacionesGlass(null)).thenReturn(List.of(asig("AG1", "222222222222222", 1, "CLI")));
        predecir(supertecnico(), CUERPO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTec").value(2))
                .andExpect(jsonPath("$.nombre").value("jhona"));
    }

    @Test void lasVerdesDelCuerpoCuentan() throws Exception {
        dosHabilitados();
        String cuerpo = """
                {"imei":"111111111111111","conCliente":true,"verdes":[
                  {"imei":"333333333333333","idTec":1,"tipo":"GLASS","esChasis":false,"conCliente":true}]}""";
        predecir(supertecnico(), cuerpo).andExpect(jsonPath("$.idTec").value(2));
    }

    @Test void sinCandidatoDevuelveNulos() throws Exception {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(new Tecnico(3, "manu", true, false, false)));
        predecir(supertecnico(), CUERPO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTec").value(nullValue()))
                .andExpect(jsonPath("$.nombre").value(nullValue()));
    }

    @Test void siFallanLasCompletadasHoyCalculaSoloConLoAbierto() throws Exception {
        dosHabilitados();
        when(dao.getAsignacionesCompletadasHoy(any(Timestamp.class))).thenThrow(new RuntimeException("BD caída"));
        when(dao.getAsignacionesGlass(null)).thenReturn(List.of(asig("AG1", "222222222222222", 1, "CLI")));
        predecir(supertecnico(), CUERPO).andExpect(status().isOk()).andExpect(jsonPath("$.idTec").value(2));
    }
}
```

Los `getAsignaciones*` que no se stubean devuelven la lista vacía de Mockito, que es lo que se quiere.

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=PrediccionGlassControllerTest`
Expected: FAIL (404 o 405 en la ruta).

- [ ] **Step 3: La respuesta**

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Técnico elegido para la glass automática (spec 3b §4.1); los dos a null si no hay candidato. */
public record PrediccionGlassRespuesta(@Schema(nullable = true) Integer idTec,
                                       @Schema(nullable = true) String nombre) {}
```

- [ ] **Step 4: El endpoint**

En `GlassController`: añadir `TecnicoDAO tecnicoDao` al constructor y a los campos, un `private static final Logger log = LoggerFactory.getLogger(GlassController.class);` si no lo hay, y:

```java
    /** Glass automática del modal "Asignar trabajos" (spec 3b §4.1). POST porque lleva las verdes del modal,
     *  pero no escribe nada. Carga lo mismo que /reparaciones/carga-tecnicos: las tres categorías abiertas y las
     *  cerradas hoy (si esa consulta falla, degrada a solo lo abierto). */
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/prediccion")
    public PrediccionGlassRespuesta predecir(@RequestBody PrediccionRequest req) {
        List<ReparacionResumen> abiertas = new ArrayList<>(dao.getAsignaciones(null));
        abiertas.addAll(dao.getAsignacionesGlass(null));
        abiertas.addAll(dao.getAsignacionesPulido(null));
        List<ReparacionResumen> cerradasHoy;
        try {
            cerradasHoy = dao.getAsignacionesCompletadasHoy(
                    UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(Clock.system(ZoneId.of("Europe/Madrid"))));
        } catch (RuntimeException e) {
            log.warn("getAsignacionesCompletadasHoy() falló; la predicción de glass cuenta solo lo abierto", e);
            cerradasHoy = List.of();
        }
        List<PrediccionGlass.VerdeEnModal> verdes = req.verdes() == null ? List.of() : req.verdes().stream()
                .map(v -> new PrediccionGlass.VerdeEnModal(v.imei(), v.idTec(), v.tipo(), v.esChasis(), v.conCliente()))
                .toList();
        Tecnico t = PrediccionGlass.elegir(tecnicoDao.getAllActivos(), abiertas, cerradasHoy, verdes,
                req.imei(), req.conCliente());
        return t == null ? new PrediccionGlassRespuesta(null, null)
                         : new PrediccionGlassRespuesta(t.getIdTec(), t.getNombre());
    }

    private record PrediccionRequest(String imei, boolean conCliente, List<VerdeRequest> verdes) {}
    private record VerdeRequest(String imei, int idTec, TipoTrabajo tipo, boolean esChasis, boolean conCliente) {}
```

Imports: `service.PrediccionGlass`, `service.TipoTrabajo`, `model.PrediccionGlassRespuesta`, `model.Tecnico`, `model.ReparacionResumen`, `dao.TecnicoDAO`, `job.UrgenteAutomaticoJob`, `java.time.Clock`, `java.time.ZoneId`, `java.util.ArrayList`, `java.util.List`, SLF4J.

Buscar (`grep -rn "new GlassController(" src/test`) si algún test construye el controller a mano y añadirle el mock de `TecnicoDAO`.

- [ ] **Step 5: El contrato**

En `OpenApiContractTest`, añadir `"/api/glass/prediccion"` a la lista de rutas que se comprueban (la misma lista donde está `"/api/reparaciones/carga-tecnicos"`).

- [ ] **Step 6: Ejecutar la suite entera**

Run: `mvn -q test`
Expected: PASS. Los `@SpringBootTest` levantan el contexto completo, así que un error de wiring por el constructor nuevo aparece aquí.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "feat(servidor): POST /api/glass/prediccion con las verdes del modal y degradacion a solo lo abierto"
```

---

## Task 3: Servidor — records del lote y `AsignacionLoteService`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/LoteAsignaciones.java`
- Create: `src/main/java/com/reparaciones/servidor/service/AsignacionLoteService.java`
- Test: `src/test/java/com/reparaciones/servidor/service/AsignacionLoteServiceTest.java`

**Interfaces:**
- Consumes: `TelefonoDAO.insertar(String, String, Integer, boolean)`, `ReparacionDAO.existeAsignacionParaTecnico(String, int, String)`, `insertarAsignacion(String, int, String, boolean, boolean, Integer, int)`, `insertarAsignacionGlass(String, int, String, boolean, Integer, int)`, `insertarAsignacionPulido(String, int, String, Integer, int)`, `getNombreTecnicoById(int)`.
- Produces: `LoteAsignaciones.Peticion(List<TelefonoDelLote>, List<AsignacionDelLote>)`, `TelefonoDelLote(String imei, String modelo, Integer idCli, boolean clienteExplicito)`, `AsignacionDelLote(String imei, String categoria, int idTec, String comentario, boolean esChasis)`, `Respuesta(List<Creada>, List<Conflicto>)`, `Creada(String idRep, String imei, int idTec, String categoria)`, `Conflicto(String imei, int idTec, String nombreTecnico, String categoria)`; `AsignacionLoteService.guardar(Peticion, Integer idTecAsigna, int idUsu) → Respuesta`, `@Transactional`.

- [ ] **Step 1: Los records**

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Guardado por lotes del modal "Asignar trabajos" (spec 3b §4.2). Anidados: springdoc los publica como
 *  LoteAsignacionesPeticion, LoteAsignacionesTelefonoDelLote, etc. */
public final class LoteAsignaciones {
    private LoteAsignaciones() {}

    public record Peticion(List<TelefonoDelLote> telefonos, List<AsignacionDelLote> asignaciones) {}

    /** Un teléfono por IMEI. modelo vacío/null = conservar el de BD (COALESCE). */
    public record TelefonoDelLote(String imei, @Schema(nullable = true) String modelo,
                                  @Schema(nullable = true) Integer idCli, boolean clienteExplicito) {}

    /** categoria: "R" reparación, "G" glass, "P" pulido. esChasis solo cuenta en "R". */
    public record AsignacionDelLote(String imei, String categoria, int idTec,
                                    @Schema(nullable = true) String comentario, boolean esChasis) {}

    public record Respuesta(List<Creada> creadas, List<Conflicto> conflictos) {}

    public record Creada(String idRep, String imei, int idTec, String categoria) {}

    public record Conflicto(String imei, int idTec, String nombreTecnico, String categoria) {}
}
```

- [ ] **Step 2: El test del servicio**

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsignacionLoteServiceTest {
    private static final String IMEI = "111111111111111";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final TelefonoDAO telefonoDao = mock(TelefonoDAO.class);
    private final AsignacionLoteService servicio = new AsignacionLoteService(dao, telefonoDao);

    private static Peticion lote(AsignacionDelLote... a) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, "13pro", 12, false)), List.of(a));
    }

    @Test void primeroLosTelefonosDespuesLasAltas() {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt())).thenReturn("A1");
        servicio.guardar(lote(new AsignacionDelLote(IMEI, "R", 3, null, false)), 7, 70);
        InOrder orden = inOrder(telefonoDao, dao);
        orden.verify(telefonoDao).insertar(IMEI, "13pro", 12, false);
        orden.verify(dao).insertarAsignacion(IMEI, 3, null, false, false, 7, 70);
    }

    @Test void cadaCategoriaVaASuAltaConUrgenteFalse() {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt())).thenReturn("A1");
        when(dao.insertarAsignacionGlass(any(), anyInt(), any(), anyBoolean(), any(), anyInt())).thenReturn("AG1");
        when(dao.insertarAsignacionPulido(any(), anyInt(), any(), any(), anyInt())).thenReturn("AP1");
        Respuesta r = servicio.guardar(lote(
                new AsignacionDelLote(IMEI, "R", 3, "hola", true),
                new AsignacionDelLote(IMEI, "G", 4, null, true),
                new AsignacionDelLote(IMEI, "P", 5, null, false)), 7, 70);
        verify(dao).insertarAsignacion(IMEI, 3, "hola", false, true, 7, 70);
        verify(dao).insertarAsignacionGlass(IMEI, 4, null, false, 7, 70);
        verify(dao).insertarAsignacionPulido(IMEI, 5, null, 7, 70);
        assertEquals(List.of(new Creada("A1", IMEI, 3, "R"), new Creada("AG1", IMEI, 4, "G"),
                new Creada("AP1", IMEI, 5, "P")), r.creadas());
        assertTrue(r.conflictos().isEmpty());
    }

    @Test void elDuplicadoSeSaltaYSeInformaConElNombre() {
        when(dao.existeAsignacionParaTecnico(IMEI, 3, "R")).thenReturn(true);
        when(dao.getNombreTecnicoById(3)).thenReturn("Técnico A");
        Respuesta r = servicio.guardar(lote(new AsignacionDelLote(IMEI, "R", 3, null, false)), 7, 70);
        verify(dao, never()).insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt());
        assertEquals(List.of(new Conflicto(IMEI, 3, "Técnico A", "R")), r.conflictos());
        assertTrue(r.creadas().isEmpty());
    }

    @Test void comentarioEnBlancoViajaComoNull() {
        when(dao.insertarAsignacionPulido(any(), anyInt(), any(), any(), anyInt())).thenReturn("AP1");
        servicio.guardar(lote(new AsignacionDelLote(IMEI, "P", 5, "   ", false)), 7, 70);
        verify(dao).insertarAsignacionPulido(IMEI, 5, null, 7, 70);
    }

    @Test void unErrorAMitadSePropagaParaQueLaTransaccionHagaRollback() {
        when(dao.insertarAsignacion(any(), anyInt(), any(), anyBoolean(), anyBoolean(), any(), anyInt()))
                .thenReturn("A1").thenThrow(new IllegalStateException("BD caída"));
        assertThrows(IllegalStateException.class, () -> servicio.guardar(lote(
                new AsignacionDelLote(IMEI, "R", 3, null, false),
                new AsignacionDelLote(IMEI, "R", 4, null, false)), 7, 70));
    }

    @Test void guardarEsTransaccional() throws Exception {
        assertTrue(AsignacionLoteService.class
                .getMethod("guardar", Peticion.class, Integer.class, int.class)
                .isAnnotationPresent(Transactional.class));
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=AsignacionLoteServiceTest`
Expected: FAIL de compilación.

- [ ] **Step 4: El servicio**

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** La transacción del guardado por lotes (spec 3b §4.2, D1): upsert de teléfonos y altas juntos; una asignación
 *  que ya existe (técnico + IMEI + categoría) se salta y se informa; cualquier excepción deshace el lote entero.
 *  Las altas de ReparacionDAO son @Transactional REQUIRED y se unen a esta. Urgente siempre false al crear. */
@Service
public class AsignacionLoteService {

    private final ReparacionDAO dao;
    private final TelefonoDAO telefonoDao;

    public AsignacionLoteService(ReparacionDAO dao, TelefonoDAO telefonoDao) {
        this.dao = dao;
        this.telefonoDao = telefonoDao;
    }

    @Transactional
    public Respuesta guardar(Peticion p, Integer idTecAsigna, int idUsu) {
        for (TelefonoDelLote t : p.telefonos())
            telefonoDao.insertar(t.imei(), t.modelo(), t.idCli(), t.clienteExplicito());
        List<Creada> creadas = new ArrayList<>();
        List<Conflicto> conflictos = new ArrayList<>();
        for (AsignacionDelLote a : p.asignaciones()) {
            if (dao.existeAsignacionParaTecnico(a.imei(), a.idTec(), a.categoria())) {
                conflictos.add(new Conflicto(a.imei(), a.idTec(), dao.getNombreTecnicoById(a.idTec()), a.categoria()));
                continue;
            }
            String comentario = (a.comentario() == null || a.comentario().isBlank()) ? null : a.comentario();
            String idRep = switch (a.categoria()) {
                case "G" -> dao.insertarAsignacionGlass(a.imei(), a.idTec(), comentario, false, idTecAsigna, idUsu);
                case "P" -> dao.insertarAsignacionPulido(a.imei(), a.idTec(), comentario, idTecAsigna, idUsu);
                default  -> dao.insertarAsignacion(a.imei(), a.idTec(), comentario, false, a.esChasis(), idTecAsigna, idUsu);
            };
            creadas.add(new Creada(idRep, a.imei(), a.idTec(), a.categoria()));
        }
        return new Respuesta(creadas, conflictos);
    }
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest=AsignacionLoteServiceTest`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/LoteAsignaciones.java src/main/java/com/reparaciones/servidor/service/AsignacionLoteService.java src/test/java/com/reparaciones/servidor/service/AsignacionLoteServiceTest.java
git commit -m "feat(servidor): servicio transaccional del lote de asignaciones con los duplicados saltados"
```

---

## Task 4: Servidor — `POST /api/asignaciones/lote`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/controller/AsignacionController.java`
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/AsignacionControllerTest.java` (unitario, como `IdempotenciaReparacionControllerTest`)
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesAsignacionLoteTest.java` (MockMvc, como `CargaTecnicosControllerTest`)

**Interfaces:**
- Consumes: Task 3; `RegistroIdempotencia.ejecutar(int, String, String, Object, Supplier<T>, Consumer<T>)`, `TecnicoDAO.getAllActivos()`, `TelefonoDAO.getModelo(String)`, `ImeiLookupService.lookupModeloInterno(String)`, `LogDAO.insertar(int, String, String)`, `ReparacionDAO.getModeloByImei(String)`, `getNombreTecnicoById(int)`.
- Produces: `POST /api/asignaciones/lote`, cabecera `Idempotency-Key` obligatoria, cuerpo `LoteAsignacionesPeticion`, respuesta 200 `LoteAsignacionesRespuesta`. Mensajes: `MSG_SIN_CLAVE = "Falta la clave de idempotencia"`.

- [ ] **Step 1: El test unitario**

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.AsignacionLoteService;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsignacionControllerTest {
    private static final String IMEI = "111111111111111";
    private static final String CLAVE = "clave-1";
    private final AsignacionLoteService servicio = mock(AsignacionLoteService.class);
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final TelefonoDAO telefonoDao = mock(TelefonoDAO.class);
    private final TecnicoDAO tecnicoDao = mock(TecnicoDAO.class);
    private final ImeiLookupService lookup = mock(ImeiLookupService.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final AsignacionController controller = new AsignacionController(
            servicio, dao, telefonoDao, tecnicoDao, lookup, logDao, new RegistroIdempotencia());
    private final UsuarioPrincipal super_ = new UsuarioPrincipal(7, "super", "", "SUPERTECNICO", 3);

    AsignacionControllerTest() {
        when(tecnicoDao.getAllActivos()).thenReturn(List.of(new Tecnico(3, "Técnico A", true, false, false)));
        when(servicio.guardar(any(), any(), anyInt())).thenReturn(new Respuesta(List.of(), List.of()));
    }

    private static Peticion rep(String modelo) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, modelo, null, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, false)));
    }

    private static Peticion pulido(String modelo) {
        return new Peticion(List.of(new TelefonoDelLote(IMEI, modelo, null, false)),
                List.of(new AsignacionDelLote(IMEI, "P", 3, null, false)));
    }

    private static int estado(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test void sinClaveEs400() {
        assertEquals(400, estado(() -> controller.guardarLote(rep("13pro"), super_, null)));
        assertEquals(400, estado(() -> controller.guardarLote(rep("13pro"), super_, "  ")));
        verifyNoInteractions(servicio);
    }

    @Test void elReintentoConLaMismaClaveNoRepiteLaEscritura() {
        Respuesta primera = controller.guardarLote(rep("13pro"), super_, CLAVE);
        Respuesta segunda = controller.guardarLote(rep("13pro"), super_, CLAVE);
        assertSame(primera, segunda);
        verify(servicio, times(1)).guardar(any(), eq(3), eq(7));
    }

    @Test void validaciones422() {
        assertEquals(422, estado(() -> controller.guardarLote(rep(""), super_, CLAVE)));            // R sin modelo
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, "X", 3, null, false))), super_, CLAVE)));      // categoría
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote(IMEI, "13pro", null, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 99, null, false))), super_, CLAVE)));     // técnico no activo
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(new TelefonoDelLote("123", "13pro", null, false)),
                List.of(new AsignacionDelLote("123", "R", 3, null, false))), super_, CLAVE)));     // IMEI
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(
                List.of(), List.of(new AsignacionDelLote(IMEI, "R", 3, null, false))), super_, CLAVE))); // sin teléfono
        assertEquals(422, estado(() -> controller.guardarLote(new Peticion(List.of(), List.of()), super_, CLAVE))); // vacío
        verifyNoInteractions(servicio);
    }

    @Test void pulidoSinModeloEnBdHaceElLookupYLoMandaAlServicio() {
        when(telefonoDao.getModelo(IMEI)).thenReturn(null);
        when(lookup.lookupModeloInterno(IMEI)).thenReturn("12");
        controller.guardarLote(pulido(null), super_, CLAVE);
        ArgumentCaptor<Peticion> enviada = ArgumentCaptor.forClass(Peticion.class);
        verify(servicio).guardar(enviada.capture(), eq(3), eq(7));
        assertEquals("12", enviada.getValue().telefonos().get(0).modelo());
    }

    @Test void sinLookupSiLaBdYaTieneModeloOSiHayReparacion() {
        when(telefonoDao.getModelo(IMEI)).thenReturn("12");
        controller.guardarLote(pulido(null), super_, "clave-a");
        controller.guardarLote(rep("13pro"), super_, "clave-b");
        verifyNoInteractions(lookup);
    }

    @Test void logsComoLosEndpointsSueltos() {
        when(servicio.guardar(any(), any(), anyInt())).thenReturn(new Respuesta(
                List.of(new Creada("A1", IMEI, 3, "R")), List.of()));
        when(dao.getModeloByImei(IMEI)).thenReturn("13pro");
        when(dao.getNombreTecnicoById(3)).thenReturn("Técnico A");
        controller.guardarLote(new Peticion(List.of(new TelefonoDelLote(IMEI, "13pro", 12, false)),
                List.of(new AsignacionDelLote(IMEI, "R", 3, null, true))), super_, CLAVE);
        verify(logDao).insertar(7, "ASIGNAR_CLIENTE", "IMEI: " + IMEI + ", ID_CLI: 12");
        verify(logDao).insertar(7, "CREAR_ASIGNACION",
                "ID_REP: A1, IMEI: " + IMEI + ", MODELO: 13pro, TECNICO: Técnico A, CHASIS: true");
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=AsignacionControllerTest`
Expected: FAIL de compilación.

- [ ] **Step 3: El controller**

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.LoteAsignaciones.*;
import com.reparaciones.servidor.model.Tecnico;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.AsignacionLoteService;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Guardado por lotes del modal "Asignar trabajos" (spec 3b §4.2). Los endpoints de alta sueltos siguen para el JavaFX. */
@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionController {

    static final String MSG_SIN_CLAVE = "Falta la clave de idempotencia";
    private static final Set<String> CATEGORIAS = Set.of("R", "G", "P");

    private final AsignacionLoteService servicio;
    private final ReparacionDAO dao;
    private final TelefonoDAO telefonoDao;
    private final TecnicoDAO tecnicoDao;
    private final ImeiLookupService imeiLookupService;
    private final LogDAO logDao;
    private final RegistroIdempotencia idempotencia;

    public AsignacionController(AsignacionLoteService servicio, ReparacionDAO dao, TelefonoDAO telefonoDao,
                                TecnicoDAO tecnicoDao, ImeiLookupService imeiLookupService, LogDAO logDao,
                                RegistroIdempotencia idempotencia) {
        this.servicio = servicio;
        this.dao = dao;
        this.telefonoDao = telefonoDao;
        this.tecnicoDao = tecnicoDao;
        this.imeiLookupService = imeiLookupService;
        this.logDao = logDao;
        this.idempotencia = idempotencia;
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping("/lote")
    public Respuesta guardarLote(@RequestBody Peticion req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal,
                                 @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false) String claveIdempotencia) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_SIN_CLAVE);
        validar(req);
        return idempotencia.ejecutar(principal.getIdUsu(), "lote", claveIdempotencia, req,
                // El lookup externo va aquí, ANTES de entrar en la transacción del servicio.
                () -> servicio.guardar(conModeloDePulido(req), principal.getIdTec(), principal.getIdUsu()),
                resultado -> registrarLogs(req, resultado, principal.getIdUsu()));
    }

    private void validar(Peticion req) {
        if (req.asignaciones() == null || req.asignaciones().isEmpty())
            throw regla("El lote no tiene asignaciones");
        Map<String, TelefonoDelLote> telefonos = new HashMap<>();
        if (req.telefonos() != null) for (TelefonoDelLote t : req.telefonos()) telefonos.put(t.imei(), t);
        Set<Integer> activos = tecnicoDao.getAllActivos().stream().map(Tecnico::getIdTec).collect(Collectors.toSet());
        for (AsignacionDelLote a : req.asignaciones()) {
            if (!CATEGORIAS.contains(a.categoria())) throw regla("Categoría no válida: " + a.categoria());
            if (a.imei() == null || !a.imei().matches("\\d{15}")) throw regla("IMEI no válido: " + a.imei());
            if (!activos.contains(a.idTec())) throw regla("Técnico no activo: " + a.idTec());
            TelefonoDelLote t = telefonos.get(a.imei());
            if (t == null) throw regla("Falta el teléfono del IMEI " + a.imei());
            if (!"P".equals(a.categoria()) && (t.modelo() == null || t.modelo().isBlank()))
                throw regla("Falta el modelo del IMEI " + a.imei());
        }
    }

    private static ResponseStatusException regla(String msg) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }

    /** Como hoy POST /pulidos/asignaciones: un IMEI que solo va a pulido y no tiene modelo ni en el lote ni en BD
     *  lo busca en el servicio de IMEI. Lento y externo: fuera de la transacción. */
    private Peticion conModeloDePulido(Peticion req) {
        Set<String> conRepOGlass = req.asignaciones().stream().filter(a -> !"P".equals(a.categoria()))
                .map(AsignacionDelLote::imei).collect(Collectors.toSet());
        List<TelefonoDelLote> telefonos = new ArrayList<>();
        for (TelefonoDelLote t : req.telefonos()) {
            boolean sinModelo = t.modelo() == null || t.modelo().isBlank();
            if (sinModelo && !conRepOGlass.contains(t.imei())) {
                String enBd = telefonoDao.getModelo(t.imei());
                if (enBd == null || enBd.isBlank()) {
                    String encontrado = imeiLookupService.lookupModeloInterno(t.imei());
                    if (encontrado != null) t = new TelefonoDelLote(t.imei(), encontrado, t.idCli(), t.clienteExplicito());
                }
            }
            telefonos.add(t);
        }
        return new Peticion(telefonos, req.asignaciones());
    }

    /** Los mismos logs que POST /telefonos y los tres endpoints de alta sueltos. */
    private void registrarLogs(Peticion req, Respuesta r, int idUsu) {
        for (TelefonoDelLote t : req.telefonos()) {
            if (t.idCli() != null) logDao.insertar(idUsu, "ASIGNAR_CLIENTE", "IMEI: " + t.imei() + ", ID_CLI: " + t.idCli());
            else if (t.clienteExplicito()) logDao.insertar(idUsu, "QUITAR_CLIENTE", "IMEI: " + t.imei());
        }
        for (Creada c : r.creadas()) {
            switch (c.categoria()) {
                case "P" -> logDao.insertar(idUsu, "CREAR_ASIGNACION_PULIDO",
                        "ID_REP: " + c.idRep() + ", IMEI: " + c.imei() + ", ID_TEC: " + c.idTec());
                case "G" -> logDao.insertar(idUsu, "CREAR_ASIGNACION_GLASS",
                        "ID_REP: " + c.idRep() + ", IMEI: " + c.imei() + ", MODELO: " + dao.getModeloByImei(c.imei())
                        + ", TECNICO: " + dao.getNombreTecnicoById(c.idTec()));
                default -> {
                    boolean chasis = req.asignaciones().stream().anyMatch(a -> "R".equals(a.categoria())
                            && a.imei().equals(c.imei()) && a.idTec() == c.idTec() && a.esChasis());
                    String detalle = "ID_REP: " + c.idRep() + ", IMEI: " + c.imei() + ", MODELO: "
                            + dao.getModeloByImei(c.imei()) + ", TECNICO: " + dao.getNombreTecnicoById(c.idTec());
                    if (chasis) detalle += ", CHASIS: true";
                    logDao.insertar(idUsu, "CREAR_ASIGNACION", detalle);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `mvn -q test -Dtest=AsignacionControllerTest`
Expected: PASS, 6 tests.

- [ ] **Step 5: Roles con MockMvc**

`RolesAsignacionLoteTest` con la cabecera de `CargaTecnicosControllerTest`, `@MockBean AsignacionLoteService servicio` y `@MockBean TecnicoDAO tecnicoDao` (con un técnico activo id 3):

```java
    private static final String CUERPO = """
            {"telefonos":[{"imei":"111111111111111","modelo":"13pro","idCli":null,"clienteExplicito":false}],
             "asignaciones":[{"imei":"111111111111111","categoria":"R","idTec":3,"comentario":null,"esChasis":false}]}""";

    private ResultActions lote(String token, String clave) throws Exception {
        var peticion = post("/api/asignaciones/lote").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO);
        if (clave != null) peticion = peticion.header("Idempotency-Key", clave);
        return mvc.perform(peticion);
    }

    @Test void tecnicoYAdminReciben403() throws Exception {
        lote(tecnico(), "k1").andExpect(status().isForbidden());
        lote(admin(), "k2").andExpect(status().isForbidden());
    }

    @Test void supertecnicoGuarda() throws Exception {
        when(servicio.guardar(any(), any(), anyInt())).thenReturn(new LoteAsignaciones.Respuesta(
                List.of(new LoteAsignaciones.Creada("A1", "111111111111111", 3, "R")), List.of()));
        lote(supertecnico(), "k3").andExpect(status().isOk()).andExpect(jsonPath("$.creadas[0].idRep").value("A1"));
    }

    @Test void sinClaveEs400() throws Exception {
        lote(supertecnico(), null).andExpect(status().isBadRequest());
    }
```

Los logs usan `LogDAO` real contra una BD que no existe: añadir también `@MockBean LogDAO logDao` y `@MockBean ReparacionDAO dao`.

- [ ] **Step 6: Contrato y suite entera**

Añadir `"/api/asignaciones/lote"` a la lista de rutas de `OpenApiContractTest`.

Run: `mvn -q test`
Expected: PASS (unos 231 + 16 + 5 + 6 + 6 + 3 tests). Los `@SpringBootTest` validan el wiring del controller y el servicio nuevos.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "feat(servidor): POST /api/asignaciones/lote atomico con clave obligatoria, lookup de pulido fuera de la transaccion y logs de siempre"
```

---

## Task 5: Web — rama, contrato regenerado y alias de tipos

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (generados)
- Modify: `src/shared/api/client.ts`

**Interfaces:**
- Consumes: Tasks 2 y 4 (el contrato).
- Produces (desde `@/shared/api/client`): `PeticionLote`, `RespuestaLote`, `ConflictoLote`, `TelefonoDelLote`, `AsignacionDelLote`, `PeticionPrediccion`, `VerdePrediccion`, `RespuestaPrediccion`.

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-web
git checkout main && git pull --ff-only
git checkout -b feature/web-asignar-trabajos
```

- [ ] **Step 2: Regenerar el contrato sin levantar el servidor**

`OpenApiContractTest` escribe el contrato completo en `target/openapi.json` al pasar la suite del servidor (Task 4, Step 6).

```bash
cp ../gestion-reparaciones-servidor/target/openapi.json api/openapi.json
npm run api:types:offline
git diff --stat src/shared/api/schema.d.ts
```

Expected: `schema.d.ts` gana `/api/glass/prediccion` y `/api/asignaciones/lote`, y nada más cambia de forma (si cambia algo ajeno, parar y avisar: el contrato de `main` y el de la rama divergen).

- [ ] **Step 3: Alias en `client.ts`**

Junto a los alias del sub-proyecto 3a, **con los nombres de esquema reales** (buscarlos en `schema.d.ts`; springdoc antepone la clase contenedora: se esperan `LoteAsignacionesPeticion`, `GlassPrediccionRequest`, `GlassVerdeRequest`…):

```ts
/** Guardado por lotes del modal "Asignar trabajos" (sub-proyecto 3b). */
export type PeticionLote = components['schemas']['LoteAsignacionesPeticion']
export type TelefonoDelLote = components['schemas']['LoteAsignacionesTelefonoDelLote']
export type AsignacionDelLote = components['schemas']['LoteAsignacionesAsignacionDelLote']
export type RespuestaLote = components['schemas']['LoteAsignacionesRespuesta']
export type ConflictoLote = components['schemas']['LoteAsignacionesConflicto']
/** Predicción de la glass automática (sub-proyecto 3b). */
export type PeticionPrediccion = components['schemas']['GlassPrediccionRequest']
export type VerdePrediccion = components['schemas']['GlassVerdeRequest']
export type RespuestaPrediccion = components['schemas']['PrediccionGlassRespuesta']
```

Comprobar además en `schema.d.ts` que `POST /api/asignaciones/lote` declara la cabecera `Idempotency-Key` en `parameters.header` (es `required = false` en Java; en el tipo sale opcional).

- [ ] **Step 4: Comprobar y commit**

```bash
npm run check
git add api/openapi.json src/shared/api/
git commit -m "chore(web): contrato regenerado con la prediccion de glass y el lote de asignaciones"
```

---

## Task 6: Web — `pegadoImei.ts`

**Files:**
- Create: `src/shared/lib/pegadoImei.ts`
- Test: `src/shared/lib/pegadoImei.test.ts`

**Interfaces:**
- Produces: `type TipoPegado = 'INCOMPLETO' | 'UNICO' | 'LOTE' | 'CORRUPTO'`; `parsearPegadoImeis(texto: string | null | undefined): { tipo: TipoPegado; imeis: string[] }`.

- [ ] **Step 1: Test (port de `ImeiUtilsTest`)**

```ts
import { describe, expect, it } from 'vitest'
import { parsearPegadoImeis } from './pegadoImei'

describe('parsearPegadoImeis (port de ImeiUtils)', () => {
  it('incompleto si menos de 15', () => {
    expect(parsearPegadoImeis('12345')).toEqual({ tipo: 'INCOMPLETO', imeis: [] })
  })
  it('único si exactamente 15', () => {
    expect(parsearPegadoImeis('352680941087812')).toEqual({ tipo: 'UNICO', imeis: ['352680941087812'] })
  })
  it('lote si múltiplo de 15', () => {
    expect(parsearPegadoImeis('352680941087812354739185728537')).toEqual({
      tipo: 'LOTE', imeis: ['352680941087812', '354739185728537'],
    })
  })
  it('corrupto si mayor de 15 y no múltiplo', () => {
    expect(parsearPegadoImeis('3526809410878123').tipo).toBe('CORRUPTO')
    expect(parsearPegadoImeis('3'.repeat(31)).tipo).toBe('CORRUPTO')
    expect(parsearPegadoImeis('3'.repeat(16)).imeis).toEqual([])
  })
  it('quita separadores y no dígitos', () => {
    const r = parsearPegadoImeis('352680941087812\n354739185728537')
    expect(r.tipo).toBe('LOTE')
    expect(r.imeis).toHaveLength(2)
  })
  it('vacío o null es incompleto', () => {
    expect(parsearPegadoImeis('').tipo).toBe('INCOMPLETO')
    expect(parsearPegadoImeis(null).tipo).toBe('INCOMPLETO')
  })
})
```

- [ ] **Step 2: Ver que falla** — Run: `npx vitest run src/shared/lib/pegadoImei.test.ts` → FAIL (módulo no existe).

- [ ] **Step 3: Implementar**

```ts
/** Port de ImeiUtils.parsearPegadoImeis del JavaFX: quita todo lo que no sea dígito y trocea en bloques de 15. */
export type TipoPegado = 'INCOMPLETO' | 'UNICO' | 'LOTE' | 'CORRUPTO'

export function parsearPegadoImeis(texto: string | null | undefined): { tipo: TipoPegado; imeis: string[] } {
  const d = (texto ?? '').replace(/\D/g, '')
  if (d.length < 15) return { tipo: 'INCOMPLETO', imeis: [] }
  if (d.length === 15) return { tipo: 'UNICO', imeis: [d] }
  if (d.length % 15 !== 0) return { tipo: 'CORRUPTO', imeis: [] }
  const imeis: string[] = []
  for (let i = 0; i < d.length; i += 15) imeis.push(d.slice(i, i + 15))
  return { tipo: 'LOTE', imeis }
}
```

- [ ] **Step 4: Ver que pasa y commit**

```bash
npx vitest run src/shared/lib/pegadoImei.test.ts
git add src/shared/lib/pegadoImei.ts src/shared/lib/pegadoImei.test.ts
git commit -m "feat(web): troceado del pegado de IMEIs portado del cliente"
```

---

## Task 7: Web — tipos, base y derivados del estado del modal

**Files:**
- Create: `src/modules/taller/asignaciones/modal/estado/tipos.ts`
- Create: `src/modules/taller/asignaciones/modal/estado/base.ts`
- Create: `src/modules/taller/asignaciones/modal/estado/derivados.ts`
- Test: `src/modules/taller/asignaciones/modal/estado/derivados.test.ts`
- Create: `src/modules/taller/asignaciones/modal/estado/fabrica.ts` (helpers de test, sin tests propios)

**Interfaces:**
- Consumes: `tipoDe` de `@/shared/lib/tipoTrabajo`; `ReparacionResumen` de `@/shared/api/client`.
- Produces: todos los tipos de abajo; `estadoInicial(tabla)`; `buscar`, `actualizar`, `actualizarTodas`, `conEfecto`, `colaDe`, `setCola`; en derivados: `tecnicosOcupados`, `glassAbiertaBd`, `verdesParaPrediccion`, `filasCola`, `contadorPestana`, `resumenBarra`, `asignarHabilitado`, `totalEscaneados`, `entradaActual`, `promptModelo`, `imeiEnColaActiva`.

- [ ] **Step 1: `tipos.ts`**

```ts
import type { ReparacionResumen } from '@/shared/api/client'

/** Colas "ricas" (comparten esqueleto); Pulido va aparte. */
export type Cola = 'REPARACION' | 'GLASS'
export type Pestana = Cola | 'PULIDO'

/** Decisión de cliente de un IMEI: cliente real, o "— Sin cliente —" explícito (`sin`). {null,false} = sin cliente
 *  elegido pero decisión registrada (calco del `clienteManual.put(imei, null)` del JavaFX). */
export type RefCliente = { idCli: number | null; sin: boolean }

/** Una entrada de las colas Reparación/Glass (EntradaAsignacion del JavaFX). `seq` es único en todo el modal. */
export type Entrada = {
  seq: number
  imei: string
  tipo: Cola
  modelo: string | null
  tecnicos: number[]
  idCli: number | null
  sinCliente: boolean
  comentario: string
  esChasis: boolean
  asignada: boolean
  modeloBuscado: boolean
  buscando: boolean
  /** El lookup de modelo corrió y no encontró nada: prompt "No encontrado — selecciona manualmente". */
  modeloNoEncontrado: boolean
  llevaGlass: boolean
  auto: boolean
  calculando: boolean
  tokenPrediccion: number
}

export type FilaPulido = {
  seq: number
  imei: string
  idTec: number | null
  comentario: string
  idCli: number | null
  sinCliente: boolean
}

/** Lo que el formulario de detalle tiene "en el aire" y solo pasa a la entrada al pulsar Asignar/Guardar cambios. */
export type Borrador = { tecnicos: number[]; comentario: string; esChasis: boolean }

export type Mensaje = { texto: string; tono: 'error' | 'ok' }

/** Lo que el modal lee de la tabla de asignaciones (congelada mientras está abierto). */
export type FilaTabla = Pick<ReparacionResumen, 'idRep' | 'imei' | 'idTec' | 'nombreTecnico'>

export type VerdeEnModal = { imei: string; idTec: number; tipo: Cola; esChasis: boolean; conCliente: boolean }

/** Trabajo asíncrono que el reductor pide y `useEfectosModal` ejecuta (cola de salida). */
export type Efecto =
  | { id: number; tipo: 'lookup'; seq: number; imei: string; buscarModelo: boolean }
  | { id: number; tipo: 'clientePulido'; seq: number; imei: string }
  | { id: number; tipo: 'guardarModelo'; imei: string; modelo: string }
  | { id: number; tipo: 'prediccion'; seq: number; token: number; imei: string; conCliente: boolean; verdes: VerdeEnModal[] }

type SinId<T> = T extends unknown ? Omit<T, 'id'> : never
export type EfectoSinId = SinId<Efecto>

export type EstadoModal = {
  pestana: Pestana
  rep: Entrada[]
  glass: Entrada[]
  pulido: FilaPulido[]
  actual: number | null
  borrador: Borrador
  pulidoSel: number | null
  tecPulidoArriba: number | null
  defTecnicos: Record<Cola, number[]>
  seq: number
  clienteManual: Record<string, RefCliente>
  clienteDefault: RefCliente | null
  modeloPorImei: Record<string, string>
  mensajeScan: Mensaje | null
  mensajePulido: Mensaje | null
  avisoPrediccion: boolean
  tabla: FilaTabla[]
  efectos: Efecto[]
  sigEfecto: number
}

export type Accion =
  | { tipo: 'CAMBIAR_PESTANA'; pestana: Pestana }
  | { tipo: 'ESCANEAR'; imei: string }
  | { tipo: 'PEGAR'; texto: string }
  | { tipo: 'CARGAR'; seq: number }
  | { tipo: 'QUITAR'; seq: number }
  | { tipo: 'LOOKUP_RESUELTO'; seq: number; modelo: string | null; idCliBd: number | null }
  | { tipo: 'DECIDIR_MODELO'; modelo: string }
  | { tipo: 'BORRAR_MODELO' }
  | { tipo: 'MARCAR_TECNICO'; idTec: number; marcado: boolean }
  | { tipo: 'ELEGIR_CLIENTE'; ref: RefCliente }
  | { tipo: 'CAMBIAR_COMENTARIO'; texto: string }
  | { tipo: 'CAMBIAR_CHASIS'; valor: boolean }
  | { tipo: 'MARCAR_LLEVA_GLASS'; valor: boolean }
  | { tipo: 'ASIGNAR' }
  | { tipo: 'PREDICCION_RESUELTA'; seq: number; token: number; idTec: number | null }
  | { tipo: 'PREDICCION_FALLIDA'; seq: number; token: number }
  | { tipo: 'CERRAR_AVISO_PREDICCION' }
  | { tipo: 'PULIDO_TEC_ARRIBA'; idTec: number | null }
  | { tipo: 'PULIDO_ESCANEAR'; imei: string }
  | { tipo: 'PULIDO_PEGAR'; texto: string }
  | { tipo: 'PULIDO_SELECCIONAR'; seq: number | null }
  | { tipo: 'PULIDO_TECNICO'; idTec: number | null }
  | { tipo: 'PULIDO_CLIENTE'; ref: RefCliente }
  | { tipo: 'PULIDO_COMENTARIO'; texto: string }
  | { tipo: 'PULIDO_QUITAR'; seq: number }
  | { tipo: 'PULIDO_CLIENTE_BD'; seq: number; idCli: number | null }
  | { tipo: 'EFECTOS_CONSUMIDOS'; ids: number[] }

export function estadoInicial(tabla: FilaTabla[]): EstadoModal {
  return {
    pestana: 'REPARACION', rep: [], glass: [], pulido: [], actual: null,
    borrador: { tecnicos: [], comentario: '', esChasis: false },
    pulidoSel: null, tecPulidoArriba: null, defTecnicos: { REPARACION: [], GLASS: [] }, seq: 0,
    clienteManual: {}, clienteDefault: null, modeloPorImei: {}, mensajeScan: null, mensajePulido: null,
    avisoPrediccion: false, tabla, efectos: [], sigEfecto: 1,
  }
}
```

- [ ] **Step 2: `base.ts`**

```ts
import type { Cola, Efecto, EfectoSinId, Entrada, EstadoModal } from './tipos'

export const colaDe = (s: EstadoModal, cola: Cola): Entrada[] => (cola === 'GLASS' ? s.glass : s.rep)

export const setCola = (s: EstadoModal, cola: Cola, entradas: Entrada[]): EstadoModal =>
  cola === 'GLASS' ? { ...s, glass: entradas } : { ...s, rep: entradas }

export function buscar(s: EstadoModal, seq: number | null): Entrada | undefined {
  if (seq == null) return undefined
  return s.rep.find((e) => e.seq === seq) ?? s.glass.find((e) => e.seq === seq)
}

export function actualizar(s: EstadoModal, seq: number, fn: (e: Entrada) => Entrada): EstadoModal {
  const e = buscar(s, seq)
  if (!e) return s
  return setCola(s, e.tipo, colaDe(s, e.tipo).map((x) => (x.seq === seq ? fn(x) : x)))
}

/** Aplica `fn` a todas las entradas (de las dos colas) que cumplan `pred`. */
export function actualizarTodas(s: EstadoModal, pred: (e: Entrada) => boolean, fn: (e: Entrada) => Entrada): EstadoModal {
  const m = (xs: Entrada[]) => xs.map((x) => (pred(x) ? fn(x) : x))
  return { ...s, rep: m(s.rep), glass: m(s.glass) }
}

export function conEfecto(s: EstadoModal, efecto: EfectoSinId): EstadoModal {
  return { ...s, efectos: [...s.efectos, { ...efecto, id: s.sigEfecto } as Efecto], sigEfecto: s.sigEfecto + 1 }
}

export function nuevaEntrada(seq: number, imei: string, tipo: Cola): Entrada {
  return {
    seq, imei, tipo, modelo: null, tecnicos: [], idCli: null, sinCliente: false, comentario: '', esChasis: false,
    asignada: false, modeloBuscado: false, buscando: false, modeloNoEncontrado: false, llevaGlass: false,
    auto: false, calculando: false, tokenPrediccion: 0,
  }
}
```

- [ ] **Step 3: helpers de test `fabrica.ts`**

```ts
import { estadoInicial, type Entrada, type EstadoModal, type FilaTabla } from './tipos'
import { nuevaEntrada } from './base'

export const IMEI_1 = '111111111111111'
export const IMEI_2 = '222222222222222'
export const IMEI_3 = '333333333333333'

export function filaTabla(p: Partial<FilaTabla> & Pick<FilaTabla, 'idRep' | 'imei' | 'idTec'>): FilaTabla {
  return { nombreTecnico: 'Técnico A', ...p }
}

export function entrada(p: Partial<Entrada> & Pick<Entrada, 'seq' | 'imei'>): Entrada {
  return { ...nuevaEntrada(p.seq, p.imei, p.tipo ?? 'REPARACION'), ...p }
}

export function estado(p: Partial<EstadoModal> = {}): EstadoModal {
  return { ...estadoInicial([]), ...p }
}
```

- [ ] **Step 4: Test de `derivados.ts`**

```ts
import { describe, expect, it } from 'vitest'
import {
  asignarHabilitado, contadorPestana, filasCola, glassAbiertaBd, imeiEnColaActiva, promptModelo, resumenBarra,
  tecnicosOcupados, totalEscaneados, verdesParaPrediccion,
} from './derivados'
import { entrada, estado, filaTabla, IMEI_1, IMEI_2 } from './fabrica'

const tabla = [
  filaTabla({ idRep: 'A1', imei: IMEI_1, idTec: 3 }),
  filaTabla({ idRep: 'AG1', imei: IMEI_1, idTec: 4, nombreTecnico: 'Técnico G' }),
  filaTabla({ idRep: 'AP1', imei: IMEI_1, idTec: 5 }),
]

describe('derivados', () => {
  it('ocupados por categoría, sobre la tabla', () => {
    expect([...tecnicosOcupados(tabla, IMEI_1, 'REPARACION')]).toEqual([3])
    expect([...tecnicosOcupados(tabla, IMEI_1, 'GLASS')]).toEqual([4])
    expect(tecnicosOcupados(tabla, IMEI_2, 'REPARACION').size).toBe(0)
  })

  it('glass abierta en BD: nombre del técnico o null', () => {
    expect(glassAbiertaBd(tabla, IMEI_1)).toBe('Técnico G')
    expect(glassAbiertaBd(tabla, IMEI_2)).toBeNull()
  })

  it('verdes para la predicción: una por entrada verde × técnico, con conCliente', () => {
    const s = estado({
      rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true, tecnicos: [3, 4], esChasis: true, idCli: 9 }),
        entrada({ seq: 2, imei: IMEI_2, asignada: false, tecnicos: [3] })],
      glass: [entrada({ seq: 3, imei: IMEI_1, tipo: 'GLASS', asignada: true, tecnicos: [5] })],
    })
    expect(verdesParaPrediccion(s)).toEqual([
      { imei: IMEI_1, idTec: 3, tipo: 'REPARACION', esChasis: true, conCliente: true },
      { imei: IMEI_1, idTec: 4, tipo: 'REPARACION', esChasis: true, conCliente: true },
      { imei: IMEI_1, idTec: 5, tipo: 'GLASS', esChasis: false, conCliente: false },
    ])
  })

  it('filas de la cola activa: rojas y verdes de más nueva a más vieja', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 }), entrada({ seq: 4, imei: IMEI_2 }),
      entrada({ seq: 2, imei: '333333333333333', asignada: true })] })
    const { rojas, verdes } = filasCola(s, 'REPARACION')
    expect(rojas.map((e) => e.seq)).toEqual([4, 1])
    expect(verdes.map((e) => e.seq)).toEqual([2])
  })

  it('contador de pestaña: total y pendientes (pulido sin técnico cuenta como pendiente)', () => {
    const s = estado({
      glass: [entrada({ seq: 1, imei: IMEI_1, tipo: 'GLASS', asignada: true })],
      pulido: [{ seq: 2, imei: IMEI_1, idTec: null, comentario: '', idCli: null, sinCliente: false }],
    })
    expect(contadorPestana(s, 'GLASS')).toEqual({ total: 1, pendientes: 0 })
    expect(contadorPestana(s, 'PULIDO')).toEqual({ total: 1, pendientes: 1 })
    expect(contadorPestana(s, 'REPARACION')).toEqual({ total: 0, pendientes: 0 })
  })

  it('barra: textos y Guardar', () => {
    const vacio = resumenBarra(estado())
    expect(vacio).toEqual({ texto: '0 configurados · 0 pendientes', n: 0, guardarHabilitado: false })
    const s = estado({
      rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true }), entrada({ seq: 2, imei: IMEI_2 })],
      pulido: [{ seq: 3, imei: IMEI_1, idTec: 5, comentario: '', idCli: null, sinCliente: false }],
    })
    expect(resumenBarra(s)).toEqual({ texto: '1 configurados · 1 pendientes · 1 pulido · 1 sin modelo', n: 2, guardarHabilitado: false })
    const listo = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true, modelo: '12' })],
      pulido: [{ seq: 3, imei: IMEI_1, idTec: 5, comentario: '', idCli: null, sinCliente: false }] })
    expect(resumenBarra(listo)).toEqual({ texto: '1 configurados · 0 pendientes · 1 pulido', n: 2, guardarHabilitado: true })
  })

  it('Guardar se bloquea con un pulido sin técnico', () => {
    const s = estado({ pulido: [{ seq: 1, imei: IMEI_1, idTec: null, comentario: '', idCli: null, sinCliente: false }] })
    expect(resumenBarra(s).guardarHabilitado).toBe(false)
  })

  it('Asignar exige modelo y un técnico marcado que no esté ocupado', () => {
    const base = estado({ tabla, rep: [entrada({ seq: 1, imei: IMEI_1, modelo: '12' })], actual: 1 })
    expect(asignarHabilitado({ ...base, borrador: { tecnicos: [], comentario: '', esChasis: false } })).toBe(false)
    expect(asignarHabilitado({ ...base, borrador: { tecnicos: [3], comentario: '', esChasis: false } })).toBe(false)
    expect(asignarHabilitado({ ...base, borrador: { tecnicos: [7], comentario: '', esChasis: false } })).toBe(true)
    const sinModelo = { ...base, rep: [entrada({ seq: 1, imei: IMEI_1 })], borrador: { tecnicos: [7], comentario: '', esChasis: false } }
    expect(asignarHabilitado(sinModelo)).toBe(false)
  })

  it('total escaneados: rep + glass + pulido', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })],
      pulido: [{ seq: 3, imei: IMEI_2, idTec: null, comentario: '', idCli: null, sinCliente: false }] })
    expect(totalEscaneados(s)).toBe(3)
  })

  it('prompt del modelo', () => {
    expect(promptModelo(entrada({ seq: 1, imei: IMEI_1, buscando: true }))).toBe('Buscando...')
    expect(promptModelo(entrada({ seq: 1, imei: IMEI_1, modeloNoEncontrado: true }))).toBe('No encontrado — selecciona manualmente')
    expect(promptModelo(entrada({ seq: 1, imei: IMEI_1 }))).toBe('Escribe modelo...')
  })

  it('IMEI en la cola activa', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })] })
    expect(imeiEnColaActiva(s, IMEI_1)).toBe(true)
    expect(imeiEnColaActiva({ ...s, pestana: 'GLASS' }, IMEI_1)).toBe(false)
  })
})
```

- [ ] **Step 5: Ver que falla** — Run: `npx vitest run src/modules/taller/asignaciones/modal/estado` → FAIL.

- [ ] **Step 6: `derivados.ts`**

```ts
import { tipoDe } from '@/shared/lib/tipoTrabajo'
import { buscar, colaDe } from './base'
import type { Cola, Entrada, EstadoModal, FilaTabla, Pestana, VerdeEnModal } from './tipos'

/** Técnicos con una asignación abierta de ese IMEI en esa categoría (tecnicosOcupados del JavaFX). */
export function tecnicosOcupados(tabla: FilaTabla[], imei: string, tipo: Cola): Set<number> {
  const ids = new Set<number>()
  for (const r of tabla) if (r.imei === imei && tipoDe(r.idRep) === tipo) ids.add(r.idTec)
  return ids
}

/** Técnico de la primera glass abierta del IMEI en la tabla, o null (tecnicoGlassAbierta del JavaFX). */
export function glassAbiertaBd(tabla: FilaTabla[], imei: string): string | null {
  const r = tabla.find((x) => x.imei === imei && tipoDe(x.idRep) === 'GLASS')
  return r ? r.nombreTecnico : null
}

export function verdesParaPrediccion(s: EstadoModal): VerdeEnModal[] {
  const out: VerdeEnModal[] = []
  for (const e of [...s.rep, ...s.glass])
    if (e.asignada)
      for (const idTec of e.tecnicos)
        out.push({ imei: e.imei, idTec, tipo: e.tipo, esChasis: e.esChasis, conCliente: e.idCli != null })
  return out
}

const porSeqDesc = (a: Entrada, b: Entrada) => b.seq - a.seq

export function filasCola(s: EstadoModal, cola: Cola): { rojas: Entrada[]; verdes: Entrada[] } {
  const xs = colaDe(s, cola)
  return { rojas: xs.filter((e) => !e.asignada).sort(porSeqDesc), verdes: xs.filter((e) => e.asignada).sort(porSeqDesc) }
}

export function contadorPestana(s: EstadoModal, p: Pestana): { total: number; pendientes: number } {
  if (p === 'PULIDO') return { total: s.pulido.length, pendientes: s.pulido.filter((f) => f.idTec == null).length }
  const xs = colaDe(s, p)
  return { total: xs.length, pendientes: xs.filter((e) => !e.asignada).length }
}

export function resumenBarra(s: EstadoModal): { texto: string; n: number; guardarHabilitado: boolean } {
  const todas = [...s.rep, ...s.glass]
  const verdes = todas.filter((e) => e.asignada).length
  const rojas = todas.length - verdes
  const sinModelo = todas.filter((e) => !e.asignada && !e.modelo).length
  const nPul = s.pulido.length
  const pulSinTec = s.pulido.filter((f) => f.idTec == null).length
  const texto = `${verdes} configurados · ${rojas} pendientes` + (nPul > 0 ? ` · ${nPul} pulido` : '')
    + (sinModelo > 0 ? ` · ${sinModelo} sin modelo` : '')
  const n = verdes + nPul
  return { texto, n, guardarHabilitado: rojas === 0 && pulSinTec === 0 && n > 0 }
}

export const entradaActual = (s: EstadoModal): Entrada | undefined => buscar(s, s.actual)

export function asignarHabilitado(s: EstadoModal): boolean {
  const e = entradaActual(s)
  if (!e || !e.modelo) return false
  const ocup = tecnicosOcupados(s.tabla, e.imei, e.tipo)
  return s.borrador.tecnicos.some((t) => !ocup.has(t))
}

export const totalEscaneados = (s: EstadoModal): number => s.rep.length + s.glass.length + s.pulido.length

export function promptModelo(e: Entrada): string {
  if (e.buscando) return 'Buscando...'
  if (e.modeloNoEncontrado) return 'No encontrado — selecciona manualmente'
  return 'Escribe modelo...'
}

export function imeiEnColaActiva(s: EstadoModal, imei: string): boolean {
  if (s.pestana === 'PULIDO') return s.pulido.some((f) => f.imei === imei)
  return colaDe(s, s.pestana).some((e) => e.imei === imei)
}
```

- [ ] **Step 7: Ver que pasa y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal/estado
npm run check
git add src/modules/taller/asignaciones/modal
git commit -m "feat(asignar): tipos, base y derivados del estado del modal"
```

---

## Task 8: Web — `cliente.ts` y `glass.ts`

**Files:**
- Create: `src/modules/taller/asignaciones/modal/estado/cliente.ts`, `glass.ts`
- Test: `src/modules/taller/asignaciones/modal/estado/cliente.test.ts`, `glass.test.ts`

**Interfaces:**
- Consumes: Task 7.
- Produces: `sembrarCliente(s, x)`, `aplicarClienteDefault(s, x)`, `aplicarClienteBd(s, x, idCli)`, `propagarCliente(s, imei, ref)`, `elegirCliente(s, ref)`; `glassDe(s, imei)`, `vincularGlass(s, e) → { s, e }`, `predecir(s, seq)`, `crearGlassDe(s, repSeq, predecirSiVerde = true)`, `quitarGlassDe(s, imei)`, `marcarLlevaGlass(s, valor)`, `prediccionResuelta(s, seq, token, idTec)`, `prediccionFallida(s, seq, token)`. `x` es genérico `T extends { imei; idCli; sinCliente }` (vale para `Entrada` y `FilaPulido`).

- [ ] **Step 1: Test de `cliente.ts`**

```ts
import { describe, expect, it } from 'vitest'
import { aplicarClienteBd, aplicarClienteDefault, elegirCliente, propagarCliente, sembrarCliente } from './cliente'
import { entrada, estado, IMEI_1, IMEI_2 } from './fabrica'

const fila = (imei: string) => ({ seq: 9, imei, idTec: null, comentario: '', idCli: null, sinCliente: false })

describe('cliente del modal (precedencia: manual → BD → pegajoso → vacío)', () => {
  it('siembra la decisión manual del IMEI, también "sin cliente"', () => {
    const s = estado({ clienteManual: { [IMEI_1]: { idCli: 5, sin: false }, [IMEI_2]: { idCli: null, sin: true } } })
    expect(sembrarCliente(s, entrada({ seq: 1, imei: IMEI_1 }))).toMatchObject({ idCli: 5, sinCliente: false })
    expect(sembrarCliente(s, entrada({ seq: 1, imei: IMEI_2 }))).toMatchObject({ idCli: null, sinCliente: true })
  })

  it('el pegajoso solo se aplica si no hay decisión', () => {
    const s = estado({ clienteDefault: { idCli: 7, sin: false } })
    expect(aplicarClienteDefault(s, entrada({ seq: 1, imei: IMEI_1 }))).toMatchObject({ idCli: 7 })
    expect(aplicarClienteDefault(s, entrada({ seq: 1, imei: IMEI_1, idCli: 3 }))).toMatchObject({ idCli: 3 })
    expect(aplicarClienteDefault(s, entrada({ seq: 1, imei: IMEI_1, sinCliente: true }))).toMatchObject({ idCli: null, sinCliente: true })
    expect(aplicarClienteDefault(estado(), entrada({ seq: 1, imei: IMEI_1 }))).toMatchObject({ idCli: null })
  })

  it('la BD manda salvo decisión manual del IMEI', () => {
    const e = entrada({ seq: 1, imei: IMEI_1, idCli: 7 })
    expect(aplicarClienteBd(estado(), e, 4)).toMatchObject({ idCli: 4, sinCliente: false })
    expect(aplicarClienteBd(estado({ clienteManual: { [IMEI_1]: { idCli: 7, sin: false } } }), e, 4)).toMatchObject({ idCli: 7 })
    expect(aplicarClienteBd(estado(), e, null)).toMatchObject({ idCli: 7 })
  })

  it('propaga a las tres colas, solo al IMEI', () => {
    const s = estado({
      rep: [entrada({ seq: 1, imei: IMEI_1 }), entrada({ seq: 2, imei: IMEI_2 })],
      glass: [entrada({ seq: 3, imei: IMEI_1, tipo: 'GLASS' })],
      pulido: [fila(IMEI_1)],
    })
    const r = propagarCliente(s, IMEI_1, { idCli: null, sin: true })
    expect(r.rep[0]).toMatchObject({ idCli: null, sinCliente: true })
    expect(r.rep[1]).toMatchObject({ idCli: null, sinCliente: false })
    expect(r.glass[0].sinCliente).toBe(true)
    expect(r.pulido[0].sinCliente).toBe(true)
  })

  it('elegir: pegajoso siempre, decisión manual y propagación si hay entrada cargada', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })], actual: 1 })
    const r = elegirCliente(s, { idCli: 5, sin: false })
    expect(r.clienteDefault).toEqual({ idCli: 5, sin: false })
    expect(r.clienteManual[IMEI_1]).toEqual({ idCli: 5, sin: false })
    expect(r.glass[0].idCli).toBe(5)
    const sinActual = elegirCliente({ ...s, actual: null }, { idCli: 5, sin: false })
    expect(sinActual.clienteManual).toEqual({})
    expect(sinActual.clienteDefault).toEqual({ idCli: 5, sin: false })
  })
})
```

- [ ] **Step 2: `cliente.ts`**

```ts
import { actualizarTodas, buscar } from './base'
import type { EstadoModal, RefCliente } from './tipos'

type ConCliente = { imei: string; idCli: number | null; sinCliente: boolean }

const conRef = <T extends ConCliente>(x: T, ref: RefCliente): T => ({ ...x, idCli: ref.sin ? null : ref.idCli, sinCliente: ref.sin })

/** Hereda la última decisión MANUAL del modal para ese IMEI (sembrarClienteEntrada / sembrarClientePulido). */
export function sembrarCliente<T extends ConCliente>(s: EstadoModal, x: T): T {
  const m = s.clienteManual[x.imei]
  return m ? conRef(x, m) : x
}

/** Cliente "pegajoso" del modal (uno para todo el modal), solo si la entrada sigue sin decisión. */
export function aplicarClienteDefault<T extends ConCliente>(s: EstadoModal, x: T): T {
  if (x.idCli != null || x.sinCliente || !s.clienteDefault) return x
  return conRef(x, s.clienteDefault)
}

/** Cliente que el IMEI ya tenía en BD: manda siempre, salvo decisión manual para ese IMEI. */
export function aplicarClienteBd<T extends ConCliente>(s: EstadoModal, x: T, idCli: number | null): T {
  if (idCli == null || x.imei in s.clienteManual) return x
  return { ...x, idCli, sinCliente: false }
}

/** Copia el cliente (o "sin cliente") a todas las entradas y filas del IMEI en las tres colas. */
export function propagarCliente(s: EstadoModal, imei: string, ref: RefCliente): EstadoModal {
  const t = actualizarTodas(s, (e) => e.imei === imei, (e) => conRef(e, ref))
  return { ...t, pulido: t.pulido.map((f) => (f.imei === imei ? conRef(f, ref) : f)) }
}

/** Elegir cliente en el detalle de Reparación/Glass (confirmarCliente del JavaFX). */
export function elegirCliente(s: EstadoModal, ref: RefCliente): EstadoModal {
  const r = { ...s, clienteDefault: ref }
  const e = buscar(r, r.actual)
  if (!e) return r
  return propagarCliente({ ...r, clienteManual: { ...r.clienteManual, [e.imei]: ref } }, e.imei, ref)
}
```

- [ ] **Step 3: Test de `glass.ts`**

```ts
import { describe, expect, it } from 'vitest'
import { crearGlassDe, marcarLlevaGlass, predecir, prediccionFallida, prediccionResuelta, quitarGlassDe, vincularGlass } from './glass'
import { entrada, estado, filaTabla, IMEI_1, IMEI_2 } from './fabrica'

const conGlassEnBd = [filaTabla({ idRep: 'AG1', imei: IMEI_1, idTec: 4 })]

describe('glass: invariante casilla ⇔ glass en la cola', () => {
  it('al escanear una reparación de un IMEI que ya está en Glass, nace marcada', () => {
    const s = estado({ glass: [entrada({ seq: 1, imei: IMEI_1, tipo: 'GLASS' })] })
    expect(vincularGlass(s, entrada({ seq: 2, imei: IMEI_1 })).e.llevaGlass).toBe(true)
  })
  it('al escanear una glass, marca las reparaciones del IMEI', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })] })
    expect(vincularGlass(s, entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })).s.rep[0].llevaGlass).toBe(true)
  })
  it('con glass abierta en BD no se vincula nada', () => {
    const s = estado({ tabla: conGlassEnBd, glass: [entrada({ seq: 1, imei: IMEI_1, tipo: 'GLASS' })] })
    expect(vincularGlass(s, entrada({ seq: 2, imei: IMEI_1 })).e.llevaGlass).toBe(false)
  })

  it('marcar en una roja es solo intención', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], actual: 1, seq: 1 })
    const r = marcarLlevaGlass(s, true)
    expect(r.rep[0].llevaGlass).toBe(true)
    expect(r.glass).toEqual([])
  })
  it('marcar en una verde crea la glass con modelo y cliente, sin comentario, y pide la predicción', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true, tecnicos: [3], modelo: '12', idCli: 5, comentario: 'x' })], actual: 1, seq: 1 })
    const r = marcarLlevaGlass(s, true)
    expect(r.glass[0]).toMatchObject({ seq: 2, imei: IMEI_1, tipo: 'GLASS', modelo: '12', idCli: 5, comentario: '', calculando: true, tokenPrediccion: 1, modeloBuscado: true })
    expect(r.efectos).toEqual([{ id: 1, tipo: 'prediccion', seq: 2, token: 1, imei: IMEI_1, conCliente: true,
      verdes: [{ imei: IMEI_1, idTec: 3, tipo: 'REPARACION', esChasis: false, conCliente: true }] }])
  })
  it('desmarcar retira la glass del IMEI esté como esté', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, llevaGlass: true })],
      glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS', asignada: true, tecnicos: [4] })], actual: 1 })
    const r = marcarLlevaGlass(s, false)
    expect(r.glass).toEqual([])
    expect(r.rep[0].llevaGlass).toBe(false)
  })
  it('la glass creada toma el modelo vivo si la reparación no lo tiene', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], modeloPorImei: { [IMEI_1]: '13' }, seq: 1 })
    expect(crearGlassDe(s, 1).glass[0].modelo).toBe('13')
  })
  it('no crea una segunda glass del mismo IMEI', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })], seq: 2 })
    expect(crearGlassDe(s, 1).glass).toHaveLength(1)
  })
  it('quitar la glass vacía el detalle si era la cargada', () => {
    const s = estado({ glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })], actual: 2 })
    expect(quitarGlassDe(s, IMEI_1).actual).toBeNull()
  })
})

describe('glass: predicción', () => {
  const roja = (p = {}) => estado({ glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS', ...p })] })

  it('una glass asignada a mano o con técnicos no se predice', () => {
    expect(predecir(roja({ asignada: true, tecnicos: [4] }), 2).efectos).toEqual([])
    expect(predecir(roja({ tecnicos: [4] }), 2).efectos).toEqual([])
  })
  it('una auto se resetea y se vuelve a pedir', () => {
    const r = predecir(roja({ asignada: true, auto: true, tecnicos: [4], tokenPrediccion: 1 }), 2)
    expect(r.glass[0]).toMatchObject({ asignada: false, auto: false, tecnicos: [], calculando: true, tokenPrediccion: 2 })
    expect(r.efectos).toHaveLength(1)
  })
  it('la respuesta la deja verde y "auto"', () => {
    const r = prediccionResuelta(roja({ calculando: true, tokenPrediccion: 1 }), 2, 1, 4)
    expect(r.glass[0]).toMatchObject({ calculando: false, asignada: true, auto: true, tecnicos: [4] })
  })
  it('sin candidato se queda roja', () => {
    expect(prediccionResuelta(roja({ calculando: true, tokenPrediccion: 1 }), 2, 1, null).glass[0])
      .toMatchObject({ calculando: false, asignada: false, tecnicos: [] })
  })
  it('una respuesta vieja, o de una glass quitada o ya asignada a mano, se ignora', () => {
    const s = roja({ calculando: true, tokenPrediccion: 2 })
    expect(prediccionResuelta(s, 2, 1, 4)).toBe(s)
    expect(prediccionResuelta(estado(), 2, 1, 4)).toEqual(estado())
    const aMano = roja({ asignada: true, tecnicos: [5], calculando: false, tokenPrediccion: 1 })
    expect(prediccionResuelta(aMano, 2, 1, 4)).toBe(aMano)
  })
  it('si falla, roja y aviso', () => {
    const r = prediccionFallida(roja({ calculando: true, tokenPrediccion: 1 }), 2, 1)
    expect(r.glass[0].calculando).toBe(false)
    expect(r.avisoPrediccion).toBe(true)
  })
  it('las verdes que viajan excluyen a la propia glass reseteada', () => {
    const s = estado({ glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS', asignada: true, auto: true, tecnicos: [4] }),
      entrada({ seq: 3, imei: IMEI_2, tipo: 'GLASS', asignada: true, tecnicos: [5] })] })
    const ef = predecir(s, 2).efectos[0]
    expect(ef.tipo === 'prediccion' && ef.verdes.map((v) => v.idTec)).toEqual([5])
  })
})
```

- [ ] **Step 4: `glass.ts`**

```ts
import { actualizar, buscar, conEfecto, nuevaEntrada } from './base'
import { glassAbiertaBd, verdesParaPrediccion } from './derivados'
import type { Entrada, EstadoModal } from './tipos'

export const glassDe = (s: EstadoModal, imei: string): Entrada | undefined => s.glass.find((g) => g.imei === imei)

/** Al escanear (vincularGlass del JavaFX). Con glass abierta en BD la casilla está deshabilitada y no se marca nada. */
export function vincularGlass(s: EstadoModal, e: Entrada): { s: EstadoModal; e: Entrada } {
  if (glassAbiertaBd(s.tabla, e.imei) != null) return { s, e }
  if (e.tipo === 'REPARACION') return { s, e: { ...e, llevaGlass: glassDe(s, e.imei) != null } }
  return { s: { ...s, rep: s.rep.map((r) => (r.imei === e.imei ? { ...r, llevaGlass: true } : r)) }, e }
}

/** predecirGlass: una "auto" se resetea y se recalcula con la carga de ahora; asignada a mano o con técnicos no se toca.
 *  Las verdes se calculan DESPUÉS del reseteo, así que la propia glass no se cuenta. */
export function predecir(s: EstadoModal, seq: number): EstadoModal {
  let g = buscar(s, seq)
  if (!g || g.tipo !== 'GLASS') return s
  if (g.auto) {
    s = actualizar(s, seq, (x) => ({ ...x, tecnicos: [], asignada: false, auto: false }))
    g = buscar(s, seq)!
  }
  if (g.asignada || g.tecnicos.length > 0) return s
  const token = g.tokenPrediccion + 1
  s = actualizar(s, seq, (x) => ({ ...x, calculando: true, tokenPrediccion: token }))
  return conEfecto(s, { tipo: 'prediccion', seq, token, imei: g.imei, conCliente: g.idCli != null, verdes: verdesParaPrediccion(s) })
}

/** crearGlassDe: glass pendiente del IMEI de la reparación (si no la hay), con su modelo (o el vivo) y su cliente,
 *  sin comentario ni lookup. Si la reparación ya es verde y `predecirSiVerde`, la predice en el acto. */
export function crearGlassDe(s: EstadoModal, repSeq: number, predecirSiVerde = true): EstadoModal {
  const e = buscar(s, repSeq)
  if (!e || glassDe(s, e.imei)) return s
  const seq = s.seq + 1
  const g: Entrada = { ...nuevaEntrada(seq, e.imei, 'GLASS'), modelo: e.modelo ?? s.modeloPorImei[e.imei] ?? null,
    idCli: e.idCli, sinCliente: e.sinCliente, modeloBuscado: true }
  const r = { ...s, seq, glass: [...s.glass, g] }
  return e.asignada && predecirSiVerde ? predecir(r, seq) : r
}

export function quitarGlassDe(s: EstadoModal, imei: string): EstadoModal {
  const quitadas = new Set(s.glass.filter((g) => g.imei === imei).map((g) => g.seq))
  return { ...s, glass: s.glass.filter((g) => g.imei !== imei), actual: s.actual != null && quitadas.has(s.actual) ? null : s.actual }
}

/** Casilla "Lleva glass" (solo clic del usuario, solo Reparación). */
export function marcarLlevaGlass(s: EstadoModal, valor: boolean): EstadoModal {
  const e = buscar(s, s.actual)
  if (!e || e.tipo !== 'REPARACION') return s
  const r = actualizar(s, e.seq, (x) => ({ ...x, llevaGlass: valor }))
  if (!valor) return quitarGlassDe(r, e.imei)
  return e.asignada ? crearGlassDe(r, e.seq) : r
}

const vigente = (s: EstadoModal, seq: number, token: number) => {
  const g = buscar(s, seq)
  return !!g && g.calculando && g.tokenPrediccion === token
}

export function prediccionResuelta(s: EstadoModal, seq: number, token: number, idTec: number | null): EstadoModal {
  if (!vigente(s, seq, token)) return s
  return actualizar(s, seq, (x) => (idTec != null && !x.asignada && x.tecnicos.length === 0
    ? { ...x, calculando: false, tecnicos: [idTec], asignada: true, auto: true }
    : { ...x, calculando: false }))
}

export function prediccionFallida(s: EstadoModal, seq: number, token: number): EstadoModal {
  if (!vigente(s, seq, token)) return s
  return { ...actualizar(s, seq, (x) => ({ ...x, calculando: false })), avisoPrediccion: true }
}
```

- [ ] **Step 5: Ver que pasa, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal/estado
npm run check
git add src/modules/taller/asignaciones/modal/estado
git commit -m "feat(asignar): cliente con precedencia y propagacion, y glass automatica con prediccion pedida como efecto"
```

---

## Task 9: Web — `colas.ts`: escanear, pegar, cargar, quitar, modelo, técnicos y asignar

**Files:**
- Create: `src/modules/taller/asignaciones/modal/estado/colas.ts`
- Test: `src/modules/taller/asignaciones/modal/estado/colas.test.ts`

**Interfaces:**
- Consumes: Tasks 6-8.
- Produces: `cambiarPestana(s, p)`, `escanear(s, imei)`, `pegar(s, texto)`, `cargar(s, seq)`, `quitar(s, seq)`, `lookupResuelto(s, seq, modelo, idCliBd)`, `propagarModelo(imei, modelo, rep, glass) → { rep, glass, n }`, `decidirModelo(s, modelo)`, `borrarModelo(s)`, `marcarTecnico(s, idTec, marcado)`, `cambiarComentario(s, texto)`, `cambiarChasis(s, valor)`, `asignar(s)`.

- [ ] **Step 1: El test**

```ts
import { describe, expect, it } from 'vitest'
import { asignar, borrarModelo, cambiarPestana, cargar, decidirModelo, escanear, lookupResuelto, marcarTecnico,
  pegar, propagarModelo, quitar } from './colas'
import { entrada, estado, filaTabla, IMEI_1, IMEI_2, IMEI_3 } from './fabrica'

describe('escanear y pegar', () => {
  it('añade a la cola activa, la carga y pide el lookup de modelo y cliente', () => {
    const r = escanear(estado(), IMEI_1)
    expect(r.rep[0]).toMatchObject({ seq: 1, imei: IMEI_1, tipo: 'REPARACION', modeloBuscado: true, buscando: true })
    expect(r.actual).toBe(1)
    expect(r.efectos).toEqual([{ id: 1, tipo: 'lookup', seq: 1, imei: IMEI_1, buscarModelo: true }])
  })
  it('repetido en la cola activa: mensaje y no añade', () => {
    const r = escanear(escanear(estado(), IMEI_1), IMEI_1)
    expect(r.rep).toHaveLength(1)
    expect(r.mensajeScan).toEqual({ texto: 'Ese IMEI ya está en la cola (Reparación).', tono: 'error' })
  })
  it('el mismo IMEI puede estar en Reparación y en Glass', () => {
    const r = escanear(cambiarPestana(escanear(estado(), IMEI_1), 'GLASS'), IMEI_1)
    expect(r.glass).toHaveLength(1)
    expect(r.mensajeScan).toBeNull()
  })
  it('nace con el modelo vivo y sin buscarlo; el cliente se sigue buscando', () => {
    const r = escanear(estado({ modeloPorImei: { [IMEI_1]: '12' } }), IMEI_1)
    expect(r.rep[0]).toMatchObject({ modelo: '12', buscando: false })
    expect(r.efectos[0]).toMatchObject({ tipo: 'lookup', buscarModelo: false })
  })
  it('pegado: añade sin cargar, salta repetidos y avisa en verde', () => {
    const s = escanear(estado(), IMEI_1)
    const r = pegar({ ...s, actual: null }, IMEI_1 + IMEI_2 + IMEI_3)
    expect(r.rep.map((e) => e.imei)).toEqual([IMEI_1, IMEI_2, IMEI_3])
    expect(r.actual).toBeNull()
    expect(r.mensajeScan).toEqual({ texto: '2 IMEIs añadidos · 1 ya estaban en la lista.', tono: 'ok' })
    expect(pegar(estado(), IMEI_1 + IMEI_2).mensajeScan?.texto).toBe('2 IMEIs añadidos.')
  })
  it('pegado corrupto', () => {
    expect(pegar(estado(), '1'.repeat(16)).mensajeScan).toEqual({
      texto: 'Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos.', tono: 'error' })
  })
})

describe('cargar', () => {
  it('una roja nueva toma los técnicos pegajosos de SU cola, sin comentario ni chasis', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], defTecnicos: { REPARACION: [3], GLASS: [8] }, seq: 1 })
    expect(cargar(s, 1).borrador).toEqual({ tecnicos: [3], comentario: '', esChasis: false })
  })
  it('los técnicos ocupados se desmarcan', () => {
    const s = estado({ tabla: [filaTabla({ idRep: 'A1', imei: IMEI_1, idTec: 3 })], rep: [entrada({ seq: 1, imei: IMEI_1 })],
      defTecnicos: { REPARACION: [3, 4], GLASS: [] }, seq: 1 })
    expect(cargar(s, 1).borrador.tecnicos).toEqual([4])
  })
  it('una verde carga lo suyo y no relanza el lookup', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true, tecnicos: [5], comentario: 'c', esChasis: true })], seq: 1 })
    const r = cargar(s, 1)
    expect(r.borrador).toEqual({ tecnicos: [5], comentario: 'c', esChasis: true })
    expect(r.efectos).toEqual([])
  })
  it('recibe el cliente pegajoso al viajar si no tiene decisión', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], clienteDefault: { idCli: 7, sin: false }, seq: 1 })
    expect(cargar(s, 1).rep[0].idCli).toBe(7)
  })
  it('con glass abierta en BD, "Lleva glass" se desmarca', () => {
    const s = estado({ tabla: [filaTabla({ idRep: 'AG1', imei: IMEI_1, idTec: 4 })], rep: [entrada({ seq: 1, imei: IMEI_1, llevaGlass: true })], seq: 1 })
    expect(cargar(s, 1).rep[0].llevaGlass).toBe(false)
  })
})

describe('lookup', () => {
  const buscando = () => estado({ rep: [entrada({ seq: 1, imei: IMEI_1, modeloBuscado: true, buscando: true })], seq: 1 })
  it('aplica el modelo y lo recuerda sin pisar una decisión manual', () => {
    const r = lookupResuelto(buscando(), 1, '12', null)
    expect(r.rep[0]).toMatchObject({ modelo: '12', buscando: false })
    expect(r.modeloPorImei[IMEI_1]).toBe('12')
    const manual = lookupResuelto({ ...buscando(), modeloPorImei: { [IMEI_1]: '13' } }, 1, '12', null)
    expect(manual.modeloPorImei[IMEI_1]).toBe('13')
  })
  it('sin resultado: no encontrado', () => {
    expect(lookupResuelto(buscando(), 1, null, null).rep[0]).toMatchObject({ buscando: false, modeloNoEncontrado: true, modelo: null })
  })
  it('una decisión manual llegada en vuelo no se pisa', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, buscando: true, modelo: '13' })] })
    expect(lookupResuelto(s, 1, '12', null).rep[0].modelo).toBe('13')
  })
  it('el cliente de BD manda salvo decisión manual; una entrada quitada se ignora', () => {
    expect(lookupResuelto(buscando(), 1, null, 4).rep[0].idCli).toBe(4)
    expect(lookupResuelto({ ...buscando(), clienteManual: { [IMEI_1]: { idCli: null, sin: true } } }, 1, null, 4).rep[0].idCli).toBeNull()
    expect(lookupResuelto(estado(), 1, '12', 4)).toEqual(estado())
  })
})

describe('modelo', () => {
  it('propagarModelo (port de los 3 tests del JavaFX)', () => {
    const rep = [entrada({ seq: 1, imei: IMEI_1, modelo: 'viejo' }), entrada({ seq: 2, imei: IMEI_1, asignada: true }), entrada({ seq: 3, imei: IMEI_2, modelo: 'otro' })]
    const glass = [entrada({ seq: 4, imei: IMEI_1, tipo: 'GLASS' })]
    const r = propagarModelo(IMEI_1, 'nuevo', rep, glass)
    expect(r.n).toBe(3)
    expect([r.rep[0].modelo, r.rep[1].modelo, r.glass[0].modelo, r.rep[2].modelo]).toEqual(['nuevo', 'nuevo', 'nuevo', 'otro'])
    expect(propagarModelo(IMEI_1, 'nuevo', [entrada({ seq: 5, imei: IMEI_2, modelo: 'otro' })], []).n).toBe(0)
    expect(propagarModelo(null, 'nuevo', rep, glass).n).toBe(0)
  })
  it('decidir: propaga a las dos colas, lo recuerda y pide guardarlo', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })], actual: 1 })
    const r = decidirModelo(s, '14pro')
    expect([r.rep[0].modelo, r.glass[0].modelo]).toEqual(['14pro', '14pro'])
    expect(r.modeloPorImei[IMEI_1]).toBe('14pro')
    expect(r.efectos).toEqual([{ id: 1, tipo: 'guardarModelo', imei: IMEI_1, modelo: '14pro' }])
  })
  it('teclear otra cosa borra el modelo solo de la entrada cargada', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, modelo: '12' })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS', modelo: '12' })], actual: 1 })
    const r = borrarModelo(s)
    expect([r.rep[0].modelo, r.glass[0].modelo]).toEqual([null, '12'])
  })
})

describe('técnicos pegajosos', () => {
  it('marcar memoriza en la cola y en la roja; en la verde solo en el borrador', () => {
    const roja = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], actual: 1 })
    const r = marcarTecnico(roja, 3, true)
    expect(r.defTecnicos.REPARACION).toEqual([3])
    expect(r.rep[0].tecnicos).toEqual([3])
    const verde = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true, tecnicos: [5] })], actual: 1, borrador: { tecnicos: [5], comentario: '', esChasis: false } })
    const v = marcarTecnico(verde, 3, true)
    expect(v.rep[0].tecnicos).toEqual([5])
    expect(v.borrador.tecnicos).toEqual([5, 3])
  })
  it('un ocupado no se puede marcar', () => {
    const s = estado({ tabla: [filaTabla({ idRep: 'A1', imei: IMEI_1, idTec: 3 })], rep: [entrada({ seq: 1, imei: IMEI_1 })], actual: 1 })
    expect(marcarTecnico(s, 3, true).borrador.tecnicos).toEqual([])
  })
})

describe('asignar', () => {
  const lista = (p = {}) => estado({
    rep: [entrada({ seq: 1, imei: IMEI_1, modelo: '12' }), entrada({ seq: 2, imei: IMEI_2, modelo: '13' })],
    actual: 2, seq: 2, borrador: { tecnicos: [3], comentario: '  hola ', esChasis: true }, ...p })

  it('pasa a verde, recorta el comentario, fija pegajosos y carga la siguiente roja (la más nueva)', () => {
    const r = asignar(lista())
    expect(r.rep[1]).toMatchObject({ asignada: true, tecnicos: [3], comentario: 'hola', esChasis: true })
    expect(r.defTecnicos.REPARACION).toEqual([3])
    expect(r.actual).toBe(1)
    expect(r.borrador.tecnicos).toEqual([3])
    expect(r.clienteManual[IMEI_2]).toEqual({ idCli: null, sin: false })
  })
  it('sin modelo o sin técnico no hace nada', () => {
    const s = lista({ borrador: { tecnicos: [], comentario: '', esChasis: false } })
    expect(asignar(s)).toBe(s)
  })
  it('editar una verde ("Guardar cambios") vacía el detalle', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, modelo: '12', asignada: true, tecnicos: [3] })], actual: 1,
      borrador: { tecnicos: [4], comentario: '', esChasis: false } })
    const r = asignar(s)
    expect(r.rep[0].tecnicos).toEqual([4])
    expect(r.actual).toBeNull()
  })
  it('con "Lleva glass": crea la glass con el modelo de la reparación y pide UNA predicción', () => {
    const s = lista({ rep: [entrada({ seq: 2, imei: IMEI_2, modelo: '13', llevaGlass: true })], actual: 2, seq: 2 })
    const r = asignar(s)
    expect(r.glass[0]).toMatchObject({ imei: IMEI_2, modelo: '13', calculando: true })
    expect(r.efectos.filter((e) => e.tipo === 'prediccion')).toHaveLength(1)
  })
  it('reasignar una reparación re-predice su glass "auto"', () => {
    const s = estado({
      rep: [entrada({ seq: 1, imei: IMEI_1, modelo: '12', asignada: true, tecnicos: [3], llevaGlass: true })],
      glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS', asignada: true, auto: true, tecnicos: [4], tokenPrediccion: 1 })],
      actual: 1, seq: 2, borrador: { tecnicos: [5], comentario: '', esChasis: false } })
    const r = asignar(s)
    expect(r.glass[0]).toMatchObject({ asignada: false, auto: false, calculando: true, tokenPrediccion: 2 })
  })
  it('sin "Lleva glass" retira la glass del IMEI; con glass abierta en BD no toca la cola', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, modelo: '12' })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })],
      actual: 1, seq: 2, borrador: { tecnicos: [3], comentario: '', esChasis: false } })
    expect(asignar(s).glass).toEqual([])
    const bloqueada = { ...s, tabla: [filaTabla({ idRep: 'AG9', imei: IMEI_1, idTec: 9 })] }
    expect(asignar(bloqueada).glass).toHaveLength(1)
  })
  it('asignar a mano una glass le quita "auto" y "Calculando…"', () => {
    const s = estado({ pestana: 'GLASS', glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS', modelo: '12', auto: true, asignada: true, tecnicos: [4], calculando: true })],
      actual: 2, borrador: { tecnicos: [6], comentario: '', esChasis: true } })
    expect(asignar(s).glass[0]).toMatchObject({ auto: false, calculando: false, tecnicos: [6], esChasis: false })
  })
})

describe('quitar y cambiar de pestaña', () => {
  it('quitar una reparación se lleva su glass (si no hay glass en BD)', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1 })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })], actual: 1 })
    const r = quitar(s, 1)
    expect([r.rep, r.glass, r.actual]).toEqual([[], [], null])
  })
  it('quitar una glass desmarca la casilla de la reparación', () => {
    const s = estado({ rep: [entrada({ seq: 1, imei: IMEI_1, llevaGlass: true })], glass: [entrada({ seq: 2, imei: IMEI_1, tipo: 'GLASS' })] })
    expect(quitar(s, 2).rep[0].llevaGlass).toBe(false)
  })
  it('cambiar de pestaña vacía el detalle y conserva las colas', () => {
    const s = escanear(estado(), IMEI_1)
    const r = cambiarPestana(s, 'GLASS')
    expect(r.actual).toBeNull()
    expect(r.rep).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Ver que falla** — Run: `npx vitest run src/modules/taller/asignaciones/modal/estado/colas.test.ts` → FAIL.

- [ ] **Step 3: `colas.ts`**

```ts
import { parsearPegadoImeis } from '@/shared/lib/pegadoImei'
import { TIPO_TRABAJO } from '@/shared/lib/tipoTrabajo'
import { actualizar, buscar, colaDe, conEfecto, nuevaEntrada, setCola } from './base'
import { aplicarClienteBd, aplicarClienteDefault, propagarCliente, sembrarCliente } from './cliente'
import { glassAbiertaBd, tecnicosOcupados } from './derivados'
import { crearGlassDe, glassDe, predecir, quitarGlassDe, vincularGlass } from './glass'
import type { Cola, Entrada, EstadoModal, Pestana, RefCliente } from './tipos'

export const cambiarPestana = (s: EstadoModal, pestana: Pestana): EstadoModal => ({ ...s, pestana, actual: null })

/** Alta de una entrada en `cola`: decisión manual de cliente → pegajoso → modelo vivo → vínculo con la glass. */
function alta(s: EstadoModal, imei: string, cola: Cola): { s: EstadoModal; e: Entrada } {
  const seq = s.seq + 1
  let e = aplicarClienteDefault(s, sembrarCliente(s, nuevaEntrada(seq, imei, cola)))
  const vivo = s.modeloPorImei[imei]
  if (vivo) e = { ...e, modelo: vivo }
  const v = vincularGlass({ ...s, seq }, e)
  return { s: setCola(v.s, cola, [...colaDe(v.s, cola), v.e]), e: v.e }
}

export function escanear(s: EstadoModal, imei: string): EstadoModal {
  if (s.pestana === 'PULIDO') return s
  const cola = s.pestana
  if (colaDe(s, cola).some((e) => e.imei === imei))
    return { ...s, mensajeScan: { texto: `Ese IMEI ya está en la cola (${TIPO_TRABAJO[cola].etiqueta}).`, tono: 'error' } }
  const r = alta({ ...s, mensajeScan: null }, imei, cola)
  return cargar(r.s, r.e.seq)
}

/** Pegado de varios IMEIs: añade sin cargar el detalle (calco, D6). */
export function pegar(s: EstadoModal, texto: string): EstadoModal {
  if (s.pestana === 'PULIDO') return s
  const cola = s.pestana
  const p = parsearPegadoImeis(texto)
  if (p.tipo === 'CORRUPTO')
    return { ...s, mensajeScan: { texto: 'Algún IMEI del pegado está corrupto. Revisa que todos los IMEIs son válidos.', tono: 'error' } }
  let r = s
  let anadidos = 0
  let duplicados = 0
  for (const imei of p.imeis) {
    if (colaDe(r, cola).some((e) => e.imei === imei)) { duplicados++; continue }
    r = alta(r, imei, cola).s
    anadidos++
  }
  const texto2 = `${anadidos} IMEIs añadidos` + (duplicados > 0 ? ` · ${duplicados} ya estaban en la lista.` : '.')
  return { ...r, mensajeScan: { texto: texto2, tono: 'ok' } }
}

/** Una vez por entrada: modelo (si le falta) y cliente del IMEI en BD. */
function lanzarLookup(s: EstadoModal, seq: number): EstadoModal {
  const e = buscar(s, seq)
  if (!e || e.modeloBuscado) return s
  const buscarModelo = !e.modelo
  const r = actualizar(s, seq, (x) => ({ ...x, modeloBuscado: true, buscando: buscarModelo }))
  return conEfecto(r, { tipo: 'lookup', seq, imei: e.imei, buscarModelo })
}

export function cargar(s: EstadoModal, seq: number): EstadoModal {
  let e = buscar(s, seq)
  if (!e) return s
  let r = s
  if (e.idCli == null && !e.sinCliente && !(e.imei in r.clienteManual)) {
    r = actualizar(r, seq, (x) => aplicarClienteDefault(r, x))
    e = buscar(r, seq)!
  }
  if (e.tipo === 'REPARACION' && e.llevaGlass && glassAbiertaBd(r.tabla, e.imei) != null)
    r = actualizar(r, seq, (x) => ({ ...x, llevaGlass: false }))
  const base = e.asignada || e.tecnicos.length > 0 ? e.tecnicos : r.defTecnicos[e.tipo]
  const ocup = tecnicosOcupados(r.tabla, e.imei, e.tipo)
  r = { ...r, actual: seq, borrador: { tecnicos: base.filter((t) => !ocup.has(t)), comentario: e.comentario, esChasis: e.esChasis } }
  return e.asignada ? r : lanzarLookup(r, seq)
}

export function quitar(s: EstadoModal, seq: number): EstadoModal {
  const e = buscar(s, seq)
  if (!e) return s
  let r = setCola(s, e.tipo, colaDe(s, e.tipo).filter((x) => x.seq !== seq))
  if (e.tipo === 'REPARACION' && glassAbiertaBd(r.tabla, e.imei) == null) r = quitarGlassDe(r, e.imei)   // sin marca no hay glass
  if (e.tipo === 'GLASS') r = { ...r, rep: r.rep.map((x) => (x.imei === e.imei ? { ...x, llevaGlass: false } : x)) }   // sin glass no hay marca
  return r.actual === seq ? { ...r, actual: null } : r
}

export function lookupResuelto(s: EstadoModal, seq: number, modelo: string | null, idCliBd: number | null): EstadoModal {
  const e = buscar(s, seq)
  if (!e) return s
  let r = s
  if (modelo && !(e.imei in r.modeloPorImei)) r = { ...r, modeloPorImei: { ...r.modeloPorImei, [e.imei]: modelo } }
  const ctx = r
  return actualizar(r, seq, (x) => {
    let y: Entrada = { ...x, buscando: false }
    if (modelo) { if (!y.modelo) y = { ...y, modelo } }
    else if (x.buscando) y = { ...y, modeloNoEncontrado: true }
    return aplicarClienteBd(ctx, y, idCliBd)
  })
}

/** Modelo vivo: copia el modelo a TODAS las entradas del IMEI en las dos colas (port de propagarModelo). */
export function propagarModelo(imei: string | null, modelo: string, rep: Entrada[], glass: Entrada[]): { rep: Entrada[]; glass: Entrada[]; n: number } {
  if (imei == null) return { rep, glass, n: 0 }
  let n = 0
  const m = (xs: Entrada[]) => xs.map((x) => { if (x.imei !== imei) return x; n++; return { ...x, modelo } })
  return { rep: m(rep), glass: m(glass), n }
}

/** Decisión manual de modelo: propaga, recuerda para los próximos escaneos y lo guarda ya (D3). */
export function decidirModelo(s: EstadoModal, modelo: string): EstadoModal {
  const e = buscar(s, s.actual)
  if (!e || !modelo) return s
  const p = propagarModelo(e.imei, modelo, s.rep, s.glass)
  const r = { ...s, rep: p.rep, glass: p.glass, modeloPorImei: { ...s.modeloPorImei, [e.imei]: modelo } }
  return conEfecto(r, { tipo: 'guardarModelo', imei: e.imei, modelo })
}

export function borrarModelo(s: EstadoModal): EstadoModal {
  return s.actual == null ? s : actualizar(s, s.actual, (x) => ({ ...x, modelo: null }))
}

/** Clic en un técnico: memoriza los pegajosos de la cola; en una roja también sus técnicos; en una verde solo el borrador. */
export function marcarTecnico(s: EstadoModal, idTec: number, marcado: boolean): EstadoModal {
  const e = buscar(s, s.actual)
  if (!e || tecnicosOcupados(s.tabla, e.imei, e.tipo).has(idTec)) return s
  const tecnicos = marcado ? [...new Set([...s.borrador.tecnicos, idTec])] : s.borrador.tecnicos.filter((t) => t !== idTec)
  const r = { ...s, borrador: { ...s.borrador, tecnicos }, defTecnicos: { ...s.defTecnicos, [e.tipo]: tecnicos } }
  return e.asignada ? r : actualizar(r, e.seq, (x) => ({ ...x, tecnicos }))
}

export const cambiarComentario = (s: EstadoModal, texto: string): EstadoModal => ({ ...s, borrador: { ...s.borrador, comentario: texto } })
export const cambiarChasis = (s: EstadoModal, valor: boolean): EstadoModal => ({ ...s, borrador: { ...s.borrador, esChasis: valor } })

function cargarSiguienteRojo(s: EstadoModal): EstadoModal {
  if (s.pestana === 'PULIDO') return { ...s, actual: null }
  const rojas = colaDe(s, s.pestana).filter((x) => !x.asignada)
  return rojas.length === 0 ? { ...s, actual: null } : cargar(s, Math.max(...rojas.map((x) => x.seq)))
}

/** "Asignar →" / "Guardar cambios" (asignarActual del JavaFX). */
export function asignar(s: EstadoModal): EstadoModal {
  const e = buscar(s, s.actual)
  if (!e || !e.modelo) return s
  const ocup = tecnicosOcupados(s.tabla, e.imei, e.tipo)
  const sel = s.borrador.tecnicos.filter((t) => !ocup.has(t))
  if (sel.length === 0) return s
  const editandoVerde = e.asignada
  const ref: RefCliente = { idCli: e.idCli, sin: e.sinCliente }
  const borrador = s.borrador
  let r: EstadoModal = { ...s, clienteManual: { ...s.clienteManual, [e.imei]: ref }, defTecnicos: { ...s.defTecnicos, [e.tipo]: sel } }
  r = propagarCliente(r, e.imei, ref)
  r = actualizar(r, e.seq, (x) => ({
    ...x, tecnicos: sel, comentario: borrador.comentario.trim(), esChasis: x.tipo === 'REPARACION' && borrador.esChasis, asignada: true,
    ...(x.tipo === 'GLASS' ? { auto: false, calculando: false } : {}),
  }))
  if (e.tipo === 'REPARACION') {
    const bloqueada = glassAbiertaBd(r.tabla, e.imei) != null
    if (e.llevaGlass && !bloqueada) {
      r = crearGlassDe(r, e.seq, false)
      const g = glassDe(r, e.imei)
      if (g) {
        if (!g.modelo) r = actualizar(r, g.seq, (x) => ({ ...x, modelo: e.modelo }))
        r = predecir(r, g.seq)
      }
    } else if (!bloqueada) {
      r = quitarGlassDe(r, e.imei)
    }
  }
  return editandoVerde ? { ...r, actual: null } : cargarSiguienteRojo(r)
}
```

- [ ] **Step 4: Ver que pasa, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal/estado
npm run check
git add src/modules/taller/asignaciones/modal/estado
git commit -m "feat(asignar): colas de reparacion y glass (escaneo, pegado, lookup, modelo vivo, pegajosos y asignar)"
```

---

## Task 10: Web — `pulido.ts`

**Files:**
- Create: `src/modules/taller/asignaciones/modal/estado/pulido.ts`
- Test: `src/modules/taller/asignaciones/modal/estado/pulido.test.ts`

**Interfaces:**
- Consumes: Tasks 6-8.
- Produces: `pulidoTecArriba(s, idTec)`, `pulidoEscanear(s, imei)`, `pulidoPegar(s, texto)`, `pulidoSeleccionar(s, seq)`, `pulidoTecnico(s, idTec)`, `pulidoCliente(s, ref)`, `pulidoComentario(s, texto)`, `pulidoQuitar(s, seq)`, `pulidoClienteBd(s, seq, idCli)`.

- [ ] **Step 1: El test**

```ts
import { describe, expect, it } from 'vitest'
import { pulidoCliente, pulidoClienteBd, pulidoComentario, pulidoEscanear, pulidoPegar, pulidoQuitar, pulidoSeleccionar,
  pulidoTecArriba, pulidoTecnico } from './pulido'
import { entrada, estado, filaTabla, IMEI_1, IMEI_2 } from './fabrica'

const conPulido = () => pulidoEscanear(pulidoTecArriba(estado({ pestana: 'PULIDO' }), 5), IMEI_1)

describe('pulido', () => {
  it('añade con el técnico de arriba, comentario vacío, lo selecciona y pide el cliente de BD', () => {
    const r = conPulido()
    expect(r.pulido[0]).toEqual({ seq: 1, imei: IMEI_1, idTec: 5, comentario: '', idCli: null, sinCliente: false })
    expect(r.pulidoSel).toBe(1)
    expect(r.efectos).toEqual([{ id: 1, tipo: 'clientePulido', seq: 1, imei: IMEI_1 }])
  })
  it('el técnico de arriba puede faltar y cambiarlo no toca las filas ya añadidas', () => {
    const r = pulidoTecArriba(conPulido(), 6)
    expect(r.pulido[0].idTec).toBe(5)
    expect(pulidoEscanear(estado({ pestana: 'PULIDO' }), IMEI_1).pulido[0].idTec).toBeNull()
  })
  it('repetido: mensaje', () => {
    expect(pulidoEscanear(conPulido(), IMEI_1).mensajePulido).toEqual({ texto: 'Ese IMEI ya está en la lista de pulido.', tono: 'error' })
  })
  it('no bloquea al técnico que ya tiene ese IMEI en pulido (D4: lo frena el lote)', () => {
    const s = pulidoTecArriba(estado({ pestana: 'PULIDO', tabla: [filaTabla({ idRep: 'AP1', imei: IMEI_1, idTec: 5 })] }), 5)
    expect(pulidoEscanear(s, IMEI_1).pulido[0].idTec).toBe(5)
  })
  it('pegado: selecciona la última y avisa con su texto corto', () => {
    const r = pulidoPegar(conPulido(), IMEI_1 + IMEI_2)
    expect(r.pulidoSel).toBe(2)
    expect(r.mensajePulido).toEqual({ texto: '1 IMEIs añadidos · 1 ya estaban.', tono: 'ok' })
    expect(pulidoPegar(estado(), '1'.repeat(16)).mensajePulido).toEqual({ texto: 'Algún IMEI del pegado está corrupto.', tono: 'error' })
  })
  it('detalle: técnico, comentario y cliente de la fila seleccionada', () => {
    let r = pulidoTecnico(conPulido(), 7)
    r = pulidoComentario(r, 'con cuidado')
    expect(r.pulido[0]).toMatchObject({ idTec: 7, comentario: 'con cuidado' })
  })
  it('elegir cliente en pulido: decisión manual, pegajoso y propagación a las otras colas', () => {
    const s = { ...conPulido(), rep: [entrada({ seq: 9, imei: IMEI_1 })] }
    const r = pulidoCliente(s, { idCli: 4, sin: false })
    expect(r.clienteManual[IMEI_1]).toEqual({ idCli: 4, sin: false })
    expect(r.clienteDefault).toEqual({ idCli: 4, sin: false })
    expect(r.rep[0].idCli).toBe(4)
  })
  it('seleccionar aplica el pegajoso si la fila no tiene decisión', () => {
    const s = { ...conPulido(), clienteDefault: { idCli: 8, sin: false }, pulidoSel: null }
    expect(pulidoSeleccionar(s, 1).pulido[0].idCli).toBe(8)
  })
  it('cliente de BD: manda salvo decisión manual', () => {
    expect(pulidoClienteBd(conPulido(), 1, 3).pulido[0].idCli).toBe(3)
    const manual = { ...conPulido(), clienteManual: { [IMEI_1]: { idCli: null, sin: true } } }
    expect(pulidoClienteBd(manual, 1, 3).pulido[0].idCli).toBeNull()
  })
  it('quitar la seleccionada vacía el detalle', () => {
    const r = pulidoQuitar(conPulido(), 1)
    expect([r.pulido, r.pulidoSel]).toEqual([[], null])
  })
})
```

- [ ] **Step 2: Ver que falla**, luego **`pulido.ts`**

```ts
import { parsearPegadoImeis } from '@/shared/lib/pegadoImei'
import { conEfecto } from './base'
import { aplicarClienteBd, aplicarClienteDefault, propagarCliente, sembrarCliente } from './cliente'
import type { EstadoModal, FilaPulido, RefCliente } from './tipos'

/** Panel de Pulido (construirPulidoPane del JavaFX): sin modelo, sin chasis, sin "Lleva glass" y sin bloqueo del
 *  duplicado al seleccionar (D4). */
export const pulidoTecArriba = (s: EstadoModal, idTec: number | null): EstadoModal => ({ ...s, tecPulidoArriba: idTec })

function agregar(s: EstadoModal, imei: string): EstadoModal {
  const seq = s.seq + 1
  const f = aplicarClienteDefault(s, sembrarCliente(s, { seq, imei, idTec: s.tecPulidoArriba, comentario: '', idCli: null, sinCliente: false } as FilaPulido))
  return conEfecto({ ...s, seq, pulido: [...s.pulido, f], pulidoSel: seq }, { tipo: 'clientePulido', seq, imei })
}

export function pulidoEscanear(s: EstadoModal, imei: string): EstadoModal {
  if (s.pulido.some((f) => f.imei === imei)) return { ...s, mensajePulido: { texto: 'Ese IMEI ya está en la lista de pulido.', tono: 'error' } }
  return agregar({ ...s, mensajePulido: null }, imei)
}

export function pulidoPegar(s: EstadoModal, texto: string): EstadoModal {
  const p = parsearPegadoImeis(texto)
  if (p.tipo === 'CORRUPTO') return { ...s, mensajePulido: { texto: 'Algún IMEI del pegado está corrupto.', tono: 'error' } }
  let r = s
  let add = 0
  let dup = 0
  for (const imei of p.imeis) {
    if (r.pulido.some((f) => f.imei === imei)) { dup++; continue }
    r = agregar(r, imei)
    add++
  }
  return { ...r, mensajePulido: { texto: `${add} IMEIs añadidos` + (dup > 0 ? ` · ${dup} ya estaban.` : '.'), tono: 'ok' } }
}

const actualizarFila = (s: EstadoModal, seq: number | null, fn: (f: FilaPulido) => FilaPulido): EstadoModal =>
  seq == null ? s : { ...s, pulido: s.pulido.map((f) => (f.seq === seq ? fn(f) : f)) }

export function pulidoSeleccionar(s: EstadoModal, seq: number | null): EstadoModal {
  const f = s.pulido.find((x) => x.seq === seq)
  if (!f) return { ...s, pulidoSel: null }
  const r = f.idCli == null && !f.sinCliente && !(f.imei in s.clienteManual) ? actualizarFila(s, seq, (x) => aplicarClienteDefault(s, x)) : s
  return { ...r, pulidoSel: seq }
}

export const pulidoTecnico = (s: EstadoModal, idTec: number | null): EstadoModal => actualizarFila(s, s.pulidoSel, (f) => ({ ...f, idTec }))
export const pulidoComentario = (s: EstadoModal, texto: string): EstadoModal => actualizarFila(s, s.pulidoSel, (f) => ({ ...f, comentario: texto }))

export function pulidoCliente(s: EstadoModal, ref: RefCliente): EstadoModal {
  const f = s.pulido.find((x) => x.seq === s.pulidoSel)
  if (!f) return s
  return propagarCliente({ ...s, clienteManual: { ...s.clienteManual, [f.imei]: ref }, clienteDefault: ref }, f.imei, ref)
}

export function pulidoQuitar(s: EstadoModal, seq: number): EstadoModal {
  return { ...s, pulido: s.pulido.filter((f) => f.seq !== seq), pulidoSel: s.pulidoSel === seq ? null : s.pulidoSel }
}

export const pulidoClienteBd = (s: EstadoModal, seq: number, idCli: number | null): EstadoModal =>
  actualizarFila(s, seq, (f) => aplicarClienteBd(s, f, idCli))
```

- [ ] **Step 3: Ver que pasa y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal/estado
git add src/modules/taller/asignaciones/modal/estado
git commit -m "feat(asignar): panel de pulido en el estado del modal"
```

---

## Task 11: Web — `reductor.ts` y `lote.ts`

**Files:**
- Create: `src/modules/taller/asignaciones/modal/estado/reductor.ts`, `src/modules/taller/asignaciones/modal/lote.ts`
- Test: `src/modules/taller/asignaciones/modal/estado/reductor.test.ts`, `src/modules/taller/asignaciones/modal/lote.test.ts`

**Interfaces:**
- Consumes: Tasks 5, 7-10.
- Produces: `reducir(s: EstadoModal, a: Accion): EstadoModal`; `construirLote(s: EstadoModal): PeticionLote`.

- [ ] **Step 1: Tests**

`reductor.test.ts` comprueba el cableado (una acción por familia) y los efectos consumidos:

```ts
import { describe, expect, it } from 'vitest'
import { reducir } from './reductor'
import { estado, IMEI_1 } from './fabrica'

describe('reducir', () => {
  it('encadena un flujo corto', () => {
    let s = reducir(estado(), { tipo: 'ESCANEAR', imei: IMEI_1 })
    s = reducir(s, { tipo: 'LOOKUP_RESUELTO', seq: 1, modelo: '12', idCliBd: null })
    s = reducir(s, { tipo: 'MARCAR_TECNICO', idTec: 3, marcado: true })
    s = reducir(s, { tipo: 'MARCAR_LLEVA_GLASS', valor: true })
    s = reducir(s, { tipo: 'ASIGNAR' })
    expect(s.rep[0].asignada).toBe(true)
    expect(s.glass[0].calculando).toBe(true)
    const pred = s.efectos.find((e) => e.tipo === 'prediccion')!
    s = reducir(s, { tipo: 'PREDICCION_RESUELTA', seq: s.glass[0].seq, token: 1, idTec: 4 })
    expect(s.glass[0]).toMatchObject({ asignada: true, auto: true, tecnicos: [4] })
    s = reducir(s, { tipo: 'EFECTOS_CONSUMIDOS', ids: [pred.id] })
    expect(s.efectos.some((e) => e.id === pred.id)).toBe(false)
  })
  it('pestañas y pulido', () => {
    let s = reducir(estado(), { tipo: 'CAMBIAR_PESTANA', pestana: 'PULIDO' })
    s = reducir(s, { tipo: 'PULIDO_ESCANEAR', imei: IMEI_1 })
    expect(s.pulido).toHaveLength(1)
  })
  it('cerrar el aviso de predicción', () => {
    expect(reducir(estado({ avisoPrediccion: true }), { tipo: 'CERRAR_AVISO_PREDICCION' }).avisoPrediccion).toBe(false)
  })
})
```

`lote.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { construirLote } from './lote'
import { entrada, estado, IMEI_1, IMEI_2 } from './estado/fabrica'

describe('construirLote', () => {
  it('un teléfono por IMEI y una asignación por entrada verde × técnico, más el pulido', () => {
    const s = estado({
      rep: [entrada({ seq: 1, imei: IMEI_1, asignada: true, modelo: '12', tecnicos: [3, 4], comentario: ' ojo ', esChasis: true, idCli: 5 }),
        entrada({ seq: 2, imei: IMEI_2 })],   // roja: no viaja
      glass: [entrada({ seq: 3, imei: IMEI_1, tipo: 'GLASS', asignada: true, modelo: '12', tecnicos: [6], idCli: 5, esChasis: true })],
      pulido: [{ seq: 4, imei: IMEI_2, idTec: 7, comentario: '', idCli: null, sinCliente: true }],
    })
    expect(construirLote(s)).toEqual({
      telefonos: [
        { imei: IMEI_1, modelo: '12', idCli: 5, clienteExplicito: false },
        { imei: IMEI_2, modelo: null, idCli: null, clienteExplicito: true },
      ],
      asignaciones: [
        { imei: IMEI_1, categoria: 'R', idTec: 3, comentario: 'ojo', esChasis: true },
        { imei: IMEI_1, categoria: 'R', idTec: 4, comentario: 'ojo', esChasis: true },
        { imei: IMEI_1, categoria: 'G', idTec: 6, comentario: null, esChasis: false },
        { imei: IMEI_2, categoria: 'P', idTec: 7, comentario: null, esChasis: false },
      ],
    })
  })
  it('un IMEI en pulido y en reparación conserva el modelo de la reparación', () => {
    const s = estado({
      pulido: [{ seq: 1, imei: IMEI_1, idTec: 7, comentario: '', idCli: null, sinCliente: false }],
      rep: [entrada({ seq: 2, imei: IMEI_1, asignada: true, modelo: '12', tecnicos: [3] })],
    })
    expect(construirLote(s).telefonos).toEqual([{ imei: IMEI_1, modelo: '12', idCli: null, clienteExplicito: false }])
  })
})
```

- [ ] **Step 2: Ver que fallan**, luego **`reductor.ts`**

```ts
import { asignar, borrarModelo, cambiarChasis, cambiarComentario, cambiarPestana, cargar, decidirModelo, escanear,
  lookupResuelto, marcarTecnico, pegar, quitar } from './colas'
import { elegirCliente } from './cliente'
import { marcarLlevaGlass, prediccionFallida, prediccionResuelta } from './glass'
import { pulidoCliente, pulidoClienteBd, pulidoComentario, pulidoEscanear, pulidoPegar, pulidoQuitar, pulidoSeleccionar,
  pulidoTecArriba, pulidoTecnico } from './pulido'
import type { Accion, EstadoModal } from './tipos'

/** Solo compone: cada regla vive en su fichero y tiene allí su test. */
export function reducir(s: EstadoModal, a: Accion): EstadoModal {
  switch (a.tipo) {
    case 'CAMBIAR_PESTANA': return cambiarPestana(s, a.pestana)
    case 'ESCANEAR': return escanear(s, a.imei)
    case 'PEGAR': return pegar(s, a.texto)
    case 'CARGAR': return cargar(s, a.seq)
    case 'QUITAR': return quitar(s, a.seq)
    case 'LOOKUP_RESUELTO': return lookupResuelto(s, a.seq, a.modelo, a.idCliBd)
    case 'DECIDIR_MODELO': return decidirModelo(s, a.modelo)
    case 'BORRAR_MODELO': return borrarModelo(s)
    case 'MARCAR_TECNICO': return marcarTecnico(s, a.idTec, a.marcado)
    case 'ELEGIR_CLIENTE': return elegirCliente(s, a.ref)
    case 'CAMBIAR_COMENTARIO': return cambiarComentario(s, a.texto)
    case 'CAMBIAR_CHASIS': return cambiarChasis(s, a.valor)
    case 'MARCAR_LLEVA_GLASS': return marcarLlevaGlass(s, a.valor)
    case 'ASIGNAR': return asignar(s)
    case 'PREDICCION_RESUELTA': return prediccionResuelta(s, a.seq, a.token, a.idTec)
    case 'PREDICCION_FALLIDA': return prediccionFallida(s, a.seq, a.token)
    case 'CERRAR_AVISO_PREDICCION': return { ...s, avisoPrediccion: false }
    case 'PULIDO_TEC_ARRIBA': return pulidoTecArriba(s, a.idTec)
    case 'PULIDO_ESCANEAR': return pulidoEscanear(s, a.imei)
    case 'PULIDO_PEGAR': return pulidoPegar(s, a.texto)
    case 'PULIDO_SELECCIONAR': return pulidoSeleccionar(s, a.seq)
    case 'PULIDO_TECNICO': return pulidoTecnico(s, a.idTec)
    case 'PULIDO_CLIENTE': return pulidoCliente(s, a.ref)
    case 'PULIDO_COMENTARIO': return pulidoComentario(s, a.texto)
    case 'PULIDO_QUITAR': return pulidoQuitar(s, a.seq)
    case 'PULIDO_CLIENTE_BD': return pulidoClienteBd(s, a.seq, a.idCli)
    case 'EFECTOS_CONSUMIDOS': return { ...s, efectos: s.efectos.filter((e) => !a.ids.includes(e.id)) }
  }
}
```

**`lote.ts`**

```ts
import type { AsignacionDelLote, PeticionLote, TelefonoDelLote } from '@/shared/api/client'
import type { EstadoModal } from './estado/tipos'

/** Estado → cuerpo de POST /api/asignaciones/lote (spec 3b §4.2). Un teléfono por IMEI (el primer modelo no nulo
 *  gana: el cliente ya es el mismo en todas las colas porque se propaga); una asignación por verde × técnico. */
export function construirLote(s: EstadoModal): PeticionLote {
  const telefonos = new Map<string, TelefonoDelLote>()
  const anotar = (imei: string, modelo: string | null, idCli: number | null, sin: boolean) => {
    const t = telefonos.get(imei)
    if (!t) telefonos.set(imei, { imei, modelo, idCli, clienteExplicito: sin })
    else if (!t.modelo && modelo) telefonos.set(imei, { ...t, modelo })
  }
  const verdes = [...s.rep, ...s.glass].filter((e) => e.asignada)
  for (const e of verdes) anotar(e.imei, e.modelo, e.idCli, e.sinCliente)
  for (const f of s.pulido) anotar(f.imei, null, f.idCli, f.sinCliente)
  const asignaciones: AsignacionDelLote[] = [
    ...verdes.flatMap((e) => e.tecnicos.map((idTec): AsignacionDelLote => ({
      imei: e.imei, categoria: e.tipo === 'GLASS' ? 'G' : 'R', idTec,
      comentario: e.comentario.trim() || null, esChasis: e.tipo === 'REPARACION' && e.esChasis,
    }))),
    ...s.pulido.filter((f) => f.idTec != null).map((f): AsignacionDelLote => ({
      imei: f.imei, categoria: 'P', idTec: f.idTec!, comentario: f.comentario.trim() || null, esChasis: false,
    })),
  ]
  return { telefonos: [...telefonos.values()], asignaciones }
}
```

Si los tipos generados declaran `modelo`/`idCli`/`comentario` como opcionales en vez de `| null`, ajustar el literal al tipo real sin cambiar los valores.

- [ ] **Step 3: Ver que pasan, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal
npm run check
git add src/modules/taller/asignaciones/modal
git commit -m "feat(asignar): reductor que compone las reglas y cuerpo del lote"
```

---

## Task 12: Web — `api.ts` del modal y `useEfectosModal`

**Files:**
- Create: `src/modules/taller/asignaciones/modal/api.ts`, `src/modules/taller/asignaciones/modal/useEfectosModal.ts`
- Test: `src/modules/taller/asignaciones/modal/api.test.tsx`, `src/modules/taller/asignaciones/modal/useEfectosModal.test.tsx`

**Interfaces:**
- Consumes: Tasks 5, 7, 11; `CLAVE_ASIGNACIONES_TODAS`, `CLAVE_CARGA_TECNICOS` de `../api`; `CLAVE_CONTADORES` de `../../api`.
- Produces: `CLAVE_CLIENTES_TODOS = ['clientes']`, `useClientesTodos(habilitada: boolean)`, `pedirLookup(imei, buscarModelo, idsClientes) → Promise<{ modelo: string | null; idCliBd: number | null }>`, `pedirClienteBd(imei, idsClientes) → Promise<number | null>`, `guardarModelo(imei, modelo) → Promise<void>`, `pedirPrediccion(cuerpo: PeticionPrediccion) → Promise<number | null>`, `useGuardarLote()` (mutación `{ cuerpo: PeticionLote; clave: string } → RespuestaLote`); `useEfectosModal(efectos: Efecto[], dispatch: Dispatch<Accion>, idsClientes: Set<number>)`.

- [ ] **Step 1: Verificar permisos y contrato**

- `GET /api/clientes` (todos, con `activo`): comprobar en el servidor (`ClienteController`) que SUPERTECNICO puede leerlo. Si no puede, usar `GET /api/clientes/activos` y **anotar la desviación** en la ficha (el cliente inactivo de BD no se mostraría, D6 no se cumpliría: consultar al usuario antes de seguir).
- `GET /api/telefonos/{imei}/modelo` → `ValorTexto { value }`, `""` si no hay; `GET /api/telefonos/{imei}/cliente` → `{ value }` con el id como texto o `""`; `POST /api/telefonos` con `ImeiRequest { imei, modelo, idCli?, clienteExplicito? }`. Confirmar los nombres en `schema.d.ts`.

- [ ] **Step 2: Tests de `api.ts` (MSW)**

```tsx
import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderHook, waitFor } from '@testing-library/react'
import { server } from '@/test/server'
import { guardarModelo, pedirClienteBd, pedirLookup, pedirPrediccion, useGuardarLote } from './api'
// envoltorioConQc: copiar el de src/modules/taller/asignaciones/api.test.tsx (crearQueryClient({ retry: false }))

const IMEI = '111111111111111'

describe('ejecutores de efectos', () => {
  it('lookup: modelo y cliente de BD (solo si el cliente existe en la lista)', async () => {
    server.use(
      http.get('*/api/telefonos/:imei/modelo', () => HttpResponse.json({ value: '12' })),
      http.get('*/api/telefonos/:imei/cliente', () => HttpResponse.json({ value: '5' })),
    )
    expect(await pedirLookup(IMEI, true, new Set([5]))).toEqual({ modelo: '12', idCliBd: 5 })
    expect(await pedirLookup(IMEI, true, new Set([9]))).toEqual({ modelo: '12', idCliBd: null })
  })
  it('lookup: vacíos y errores se tragan; sin buscarModelo no pide el modelo', async () => {
    let pidioModelo = false
    server.use(
      http.get('*/api/telefonos/:imei/modelo', () => { pidioModelo = true; return HttpResponse.json({ value: '' }) }),
      http.get('*/api/telefonos/:imei/cliente', () => new HttpResponse(null, { status: 500 })),
    )
    expect(await pedirLookup(IMEI, false, new Set())).toEqual({ modelo: null, idCliBd: null })
    expect(pidioModelo).toBe(false)
    expect(await pedirLookup(IMEI, true, new Set())).toEqual({ modelo: null, idCliBd: null })
  })
  it('cliente de BD vacío es null', async () => {
    server.use(http.get('*/api/telefonos/:imei/cliente', () => HttpResponse.json({ value: '' })))
    expect(await pedirClienteBd(IMEI, new Set([0]))).toBeNull()
  })
  it('guardar el modelo manda imei y modelo y no lanza si falla', async () => {
    let cuerpo: unknown
    server.use(http.post('*/api/telefonos', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 500 }) }))
    await expect(guardarModelo(IMEI, '14pro')).resolves.toBeUndefined()
    expect(cuerpo).toMatchObject({ imei: IMEI, modelo: '14pro' })
  })
  it('predicción: idTec o null; los errores se propagan para marcarla fallida', async () => {
    server.use(http.post('*/api/glass/prediccion', () => HttpResponse.json({ idTec: 4, nombre: 'Técnico G' })))
    expect(await pedirPrediccion({ imei: IMEI, conCliente: true, verdes: [] })).toBe(4)
    server.use(http.post('*/api/glass/prediccion', () => new HttpResponse(null, { status: 500 })))
    await expect(pedirPrediccion({ imei: IMEI, conCliente: true, verdes: [] })).rejects.toBeTruthy()
  })
})

describe('useGuardarLote', () => {
  it('manda la clave en la cabecera e invalida asignaciones, carga y contadores', async () => {
    let clave: string | null = null
    server.use(http.post('*/api/asignaciones/lote', ({ request }) => {
      clave = request.headers.get('Idempotency-Key')
      return HttpResponse.json({ creadas: [], conflictos: [] })
    }))
    const { wrapper, queryClient } = envoltorioConQc()
    const invalidadas: unknown[] = []
    const original = queryClient.invalidateQueries.bind(queryClient)
    queryClient.invalidateQueries = ((f: { queryKey: unknown }) => { invalidadas.push(f.queryKey); return original(f) }) as typeof queryClient.invalidateQueries
    const { result } = renderHook(() => useGuardarLote(), { wrapper })
    await result.current.mutateAsync({ cuerpo: { telefonos: [], asignaciones: [] }, clave: 'k-1' })
    expect(clave).toBe('k-1')
    await waitFor(() => expect(invalidadas).toEqual(expect.arrayContaining([
      ['asignaciones', 'todas'], ['carga-tecnicos'], ['asignaciones'], ['pendientes', 'contadores']])))
  })
})
```

Si `envoltorioConQc` de `asignaciones/api.test.tsx` tiene otra firma, adaptarse a la real; lo que se comprueba es la cabecera y las cuatro claves invalidadas.

- [ ] **Step 3: `api.ts`**

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Cliente, type PeticionLote, type PeticionPrediccion, type RespuestaLote } from '@/shared/api/client'
import { CLAVE_CONTADORES } from '../../api'
import { CLAVE_ASIGNACIONES_TODAS, CLAVE_CARGA_TECNICOS } from '../api'

/** Todos los clientes con su estado: el buscador ofrece solo los activos, pero un cliente inactivo que el IMEI ya
 *  tenga en BD se muestra (spec 3b D6). Misma clave que la lista de Gestión → Clientes: comparten caché. */
export const CLAVE_CLIENTES_TODOS = ['clientes'] as const

export function useClientesTodos(habilitada: boolean) {
  return useQuery({
    queryKey: CLAVE_CLIENTES_TODOS,
    queryFn: async (): Promise<Cliente[]> => (await api.GET('/api/clientes')).data ?? [],
    enabled: habilitada,
  })
}

/** Cliente del IMEI en BD, o null (vacío, desconocido en la lista o error: calco, se traga). */
export async function pedirClienteBd(imei: string, idsClientes: Set<number>): Promise<number | null> {
  try {
    const { data } = await api.GET('/api/telefonos/{imei}/cliente', { params: { path: { imei } } })
    const v = data?.value
    if (!v) return null
    const id = Number(v)
    return idsClientes.has(id) ? id : null
  } catch {
    return null
  }
}

/** Lookup de una entrada roja: modelo (si le falta; el servidor ya consulta el servicio de IMEI) y cliente de BD. */
export async function pedirLookup(imei: string, buscarModelo: boolean, idsClientes: Set<number>): Promise<{ modelo: string | null; idCliBd: number | null }> {
  let modelo: string | null = null
  if (buscarModelo) {
    try {
      const { data } = await api.GET('/api/telefonos/{imei}/modelo', { params: { path: { imei } } })
      modelo = data?.value || null
    } catch {
      modelo = null
    }
  }
  return { modelo, idCliBd: await pedirClienteBd(imei, idsClientes) }
}

/** Guardado inmediato del modelo decidido a mano (D3). Si falla, nada: el lote lo vuelve a mandar. */
export async function guardarModelo(imei: string, modelo: string): Promise<void> {
  try {
    await api.POST('/api/telefonos', { body: { imei, modelo, idCli: null, clienteExplicito: null } })
  } catch {
    // el lote vuelve a mandar el modelo
  }
}

/** Técnico de la glass automática, o null si no hay candidato. Los errores se propagan (glass roja + aviso). */
export async function pedirPrediccion(cuerpo: PeticionPrediccion): Promise<number | null> {
  const { data } = await api.POST('/api/glass/prediccion', { body: cuerpo })
  return data?.idTec ?? null
}

export function useGuardarLote() {
  const qc = useQueryClient()
  return useMutation<RespuestaLote, unknown, { cuerpo: PeticionLote; clave: string }>({
    mutationFn: async ({ cuerpo, clave }) =>
      (await api.POST('/api/asignaciones/lote', { params: { header: { 'Idempotency-Key': clave } }, body: cuerpo })).data
        ?? { creadas: [], conflictos: [] },
    meta: { silenciarError: true },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: CLAVE_ASIGNACIONES_TODAS })
      void qc.invalidateQueries({ queryKey: CLAVE_CARGA_TECNICOS })
      void qc.invalidateQueries({ queryKey: ['asignaciones'] })   // pendientes de los técnicos
      void qc.invalidateQueries({ queryKey: CLAVE_CONTADORES })
    },
  })
}
```

- [ ] **Step 4: Test de `useEfectosModal`**

```tsx
import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { renderHook, waitFor } from '@testing-library/react'
import { server } from '@/test/server'
import { useEfectosModal } from './useEfectosModal'
import type { Efecto } from './estado/tipos'

const IMEI = '111111111111111'
const pred: Efecto = { id: 1, tipo: 'prediccion', seq: 2, token: 1, imei: IMEI, conCliente: true, verdes: [] }

describe('useEfectosModal', () => {
  it('ejecuta cada efecto una sola vez, marca consumidos y despacha el resultado', async () => {
    let llamadas = 0
    server.use(http.post('*/api/glass/prediccion', () => { llamadas++; return HttpResponse.json({ idTec: 4, nombre: 'x' }) }))
    const dispatch = vi.fn()
    const ids = new Set<number>()
    const { rerender } = renderHook(({ ef }) => useEfectosModal(ef, dispatch, ids), { initialProps: { ef: [pred] } })
    rerender({ ef: [pred] })   // el mismo efecto otra vez (StrictMode, re-render): no se repite
    expect(dispatch).toHaveBeenCalledWith({ tipo: 'EFECTOS_CONSUMIDOS', ids: [1] })
    await waitFor(() => expect(dispatch).toHaveBeenCalledWith({ tipo: 'PREDICCION_RESUELTA', seq: 2, token: 1, idTec: 4 }))
    expect(llamadas).toBe(1)
  })
  it('una predicción que falla se despacha como fallida', async () => {
    server.use(http.post('*/api/glass/prediccion', () => new HttpResponse(null, { status: 500 })))
    const dispatch = vi.fn()
    renderHook(() => useEfectosModal([pred], dispatch, new Set()))
    await waitFor(() => expect(dispatch).toHaveBeenCalledWith({ tipo: 'PREDICCION_FALLIDA', seq: 2, token: 1 }))
  })
  it('lookup y cliente de pulido', async () => {
    server.use(
      http.get('*/api/telefonos/:imei/modelo', () => HttpResponse.json({ value: '12' })),
      http.get('*/api/telefonos/:imei/cliente', () => HttpResponse.json({ value: '5' })),
    )
    const dispatch = vi.fn()
    const efectos: Efecto[] = [{ id: 1, tipo: 'lookup', seq: 1, imei: IMEI, buscarModelo: true }, { id: 2, tipo: 'clientePulido', seq: 3, imei: IMEI }]
    renderHook(() => useEfectosModal(efectos, dispatch, new Set([5])))
    await waitFor(() => {
      expect(dispatch).toHaveBeenCalledWith({ tipo: 'LOOKUP_RESUELTO', seq: 1, modelo: '12', idCliBd: 5 })
      expect(dispatch).toHaveBeenCalledWith({ tipo: 'PULIDO_CLIENTE_BD', seq: 3, idCli: 5 })
    })
  })
})
```

`ids` estable en el primer test: una `Set` nueva en cada render dispararía el efecto de nuevo, y el guard de `lanzados` debe impedir igualmente la repetición.

- [ ] **Step 5: `useEfectosModal.ts`**

```ts
import { useEffect, useRef, type Dispatch } from 'react'
import { guardarModelo, pedirClienteBd, pedirLookup, pedirPrediccion } from './api'
import type { Accion, Efecto } from './estado/tipos'

/** Consume la cola de salida del reductor: lanza cada efecto una sola vez (también bajo StrictMode) y despacha su
 *  resultado. El reductor decide si el resultado sigue valiendo (entrada existente, token vigente). */
export function useEfectosModal(efectos: Efecto[], dispatch: Dispatch<Accion>, idsClientes: Set<number>) {
  const lanzados = useRef(new Set<number>())
  const vivo = useRef(true)
  useEffect(() => {
    vivo.current = true
    return () => { vivo.current = false }
  }, [])

  useEffect(() => {
    const nuevos = efectos.filter((e) => !lanzados.current.has(e.id))
    if (nuevos.length === 0) return
    const despachar = (a: Accion) => { if (vivo.current) dispatch(a) }
    for (const e of nuevos) {
      lanzados.current.add(e.id)
      void ejecutar(e)
    }
    dispatch({ tipo: 'EFECTOS_CONSUMIDOS', ids: nuevos.map((e) => e.id) })

    async function ejecutar(e: Efecto) {
      switch (e.tipo) {
        case 'lookup': {
          const r = await pedirLookup(e.imei, e.buscarModelo, idsClientes)
          despachar({ tipo: 'LOOKUP_RESUELTO', seq: e.seq, modelo: r.modelo, idCliBd: r.idCliBd })
          return
        }
        case 'clientePulido':
          despachar({ tipo: 'PULIDO_CLIENTE_BD', seq: e.seq, idCli: await pedirClienteBd(e.imei, idsClientes) })
          return
        case 'guardarModelo':
          await guardarModelo(e.imei, e.modelo)
          return
        case 'prediccion':
          try {
            const idTec = await pedirPrediccion({ imei: e.imei, conCliente: e.conCliente, verdes: e.verdes })
            despachar({ tipo: 'PREDICCION_RESUELTA', seq: e.seq, token: e.token, idTec })
          } catch {
            despachar({ tipo: 'PREDICCION_FALLIDA', seq: e.seq, token: e.token })
          }
      }
    }
  }, [efectos, dispatch, idsClientes])
}
```

- [ ] **Step 6: Ver que pasan, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal
npm run check
git add src/modules/taller/asignaciones/modal
git commit -m "feat(asignar): api del modal (lote con clave, prediccion, lookups) y consumo de la cola de efectos"
```

---

## Task 13: Web — `CampoAutocompletar`

**Files:**
- Create: `src/shared/ui/CampoAutocompletar.tsx`
- Test: `src/shared/ui/CampoAutocompletar.test.tsx`

**Interfaces:**
- Produces: `CampoAutocompletar({ valor: string | null; opciones: { clave: string; etiqueta: string }[]; onElegir: (clave: string) => void; onTextoCambiado?: (texto: string) => void; placeholder: string; disabled?: boolean; 'aria-label': string })`.

Comportamiento (calco de los dos buscadores del modal JavaFX): campo navy redondeado; al teclear filtra por **"contiene"** sin mayúsculas y abre el popup (máximo 6 filas visibles, scroll si hay más); clic en una fila elige; **Enter** elige la primera filtrada **salvo** que el texto ya sea la etiqueta elegida o esté vacío; **al salir** decide la coincidencia exacta (sin mayúsculas) o **restaura** el texto de lo elegido (o vacío); Escape cierra el popup. `onTextoCambiado` avisa cuando el texto deja de coincidir con lo elegido (el modelo lo usa para borrarse, como el JavaFX).

- [ ] **Step 1: Test**

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { CampoAutocompletar } from './CampoAutocompletar'

const OPCIONES = [
  { clave: '14', etiqueta: 'iPhone 14' }, { clave: '14pro', etiqueta: 'iPhone 14 Pro' },
  { clave: '14promax', etiqueta: 'iPhone 14 Pro Max' }, { clave: '15', etiqueta: 'iPhone 15' },
]

function Envoltorio({ onElegir = vi.fn(), onTextoCambiado = vi.fn(), inicial = null as string | null }) {
  const [valor, setValor] = useState<string | null>(inicial)
  return <>
    <CampoAutocompletar aria-label="Modelo" placeholder="Escribe modelo..." valor={valor} opciones={OPCIONES}
      onElegir={(c) => { setValor(c); onElegir(c) }} onTextoCambiado={onTextoCambiado} />
    <button>fuera</button>
  </>
}

describe('CampoAutocompletar', () => {
  it('filtra por "contiene" y elige con clic', async () => {
    const onElegir = vi.fn()
    render(<Envoltorio onElegir={onElegir} />)
    await userEvent.type(screen.getByRole('combobox', { name: 'Modelo' }), '14 pro')
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['iPhone 14 Pro', 'iPhone 14 Pro Max'])
    await userEvent.click(screen.getByRole('option', { name: 'iPhone 14 Pro Max' }))
    expect(onElegir).toHaveBeenCalledWith('14promax')
    expect(screen.getByRole('combobox')).toHaveValue('iPhone 14 Pro Max')
    expect(screen.queryByRole('listbox')).toBeNull()
  })
  it('Enter elige la primera filtrada, pero no con el campo vacío ni con lo ya elegido', async () => {
    const onElegir = vi.fn()
    render(<Envoltorio onElegir={onElegir} inicial="15" />)
    const campo = screen.getByRole('combobox')
    await userEvent.type(campo, '{Enter}')
    expect(onElegir).not.toHaveBeenCalled()
    await userEvent.clear(campo)
    await userEvent.type(campo, '{Enter}')
    expect(onElegir).not.toHaveBeenCalled()
    await userEvent.type(campo, 'pro{Enter}')
    expect(onElegir).toHaveBeenCalledWith('14pro')
  })
  it('al salir: coincidencia exacta decide; si no, restaura', async () => {
    const onElegir = vi.fn()
    render(<Envoltorio onElegir={onElegir} inicial="15" />)
    const campo = screen.getByRole('combobox')
    await userEvent.clear(campo)
    await userEvent.type(campo, 'iphone 14')
    await userEvent.click(screen.getByRole('button', { name: 'fuera' }))
    expect(onElegir).toHaveBeenCalledWith('14')
    await userEvent.clear(campo)
    await userEvent.type(campo, 'iph')
    await userEvent.click(screen.getByRole('button', { name: 'fuera' }))
    expect(campo).toHaveValue('iPhone 14')
  })
  it('avisa cuando el texto deja de coincidir con lo elegido', async () => {
    const onTextoCambiado = vi.fn()
    render(<Envoltorio onTextoCambiado={onTextoCambiado} inicial="15" />)
    await userEvent.type(screen.getByRole('combobox'), 'x')
    expect(onTextoCambiado).toHaveBeenCalledWith('iPhone 15x')
  })
  it('muestra como mucho 6 filas a la vez (alto del popup)', async () => {
    render(<CampoAutocompletar aria-label="C" placeholder="" valor={null} onElegir={vi.fn()}
      opciones={Array.from({ length: 10 }, (_, i) => ({ clave: String(i), etiqueta: `Cliente ${i}` }))} />)
    await userEvent.type(screen.getByRole('combobox'), 'cliente')
    expect(screen.getByRole('listbox')).toHaveStyle({ maxHeight: '180px' })
  })
})
```

- [ ] **Step 2: Ver que falla**, luego **implementar**

```tsx
import { useEffect, useId, useMemo, useState, type KeyboardEvent } from 'react'
import { cn } from '@/shared/lib/utils'

type Opcion = { clave: string; etiqueta: string }
type Props = {
  valor: string | null
  opciones: Opcion[]
  onElegir: (clave: string) => void
  onTextoCambiado?: (texto: string) => void
  placeholder: string
  disabled?: boolean
  'aria-label': string
}

const ALTO_FILA = 30
const VISIBLES = 6

/** Buscador en línea del modal "Asignar trabajos" (modelo y cliente): calco de los TextField + Popup del JavaFX. */
export function CampoAutocompletar({ valor, opciones, onElegir, onTextoCambiado, placeholder, disabled, 'aria-label': ariaLabel }: Props) {
  const etiquetaDe = (clave: string | null) => opciones.find((o) => o.clave === clave)?.etiqueta ?? ''
  const elegida = etiquetaDe(valor)
  const [texto, setTexto] = useState(elegida)
  const [abierto, setAbierto] = useState(false)
  const idLista = useId()

  useEffect(() => { setTexto(elegida) }, [elegida])

  const filtradas = useMemo(() => {
    const t = texto.trim().toLowerCase()
    return t === '' ? opciones : opciones.filter((o) => o.etiqueta.toLowerCase().includes(t))
  }, [texto, opciones])

  const elegir = (o: Opcion) => {
    setTexto(o.etiqueta)
    setAbierto(false)
    onElegir(o.clave)
  }

  const onKeyDown = (ev: KeyboardEvent<HTMLInputElement>) => {
    if (ev.key === 'Escape') { setAbierto(false); return }
    if (ev.key !== 'Enter') return
    ev.preventDefault()
    const t = texto.trim()
    if (t === '' || (valor != null && t === elegida)) return
    if (filtradas.length > 0) elegir(filtradas[0])
  }

  const onBlur = () => {
    setAbierto(false)
    const t = texto.trim()
    if (valor != null && t === elegida) return
    const exacta = opciones.find((o) => o.etiqueta.toLowerCase() === t.toLowerCase())
    if (exacta && t !== '') elegir(exacta)
    else setTexto(elegida)
  }

  return (
    <div className="relative w-full">
      <input
        role="combobox"
        aria-label={ariaLabel}
        aria-expanded={abierto}
        aria-controls={idLista}
        aria-autocomplete="list"
        value={texto}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(ev) => {
          setTexto(ev.target.value)
          setAbierto(true)
          if (ev.target.value !== elegida) onTextoCambiado?.(ev.target.value)
        }}
        onKeyDown={onKeyDown}
        onBlur={onBlur}
        className="w-full rounded-full bg-azul-noche px-3 py-1 text-[12px] font-bold text-crema placeholder:text-crema/45 disabled:opacity-60"
      />
      {abierto && filtradas.length > 0 && (
        <ul
          id={idLista}
          role="listbox"
          style={{ maxHeight: `${ALTO_FILA * VISIBLES}px` }}
          className="absolute z-50 mt-px w-full overflow-y-auto rounded-lg border border-borde-input bg-white py-0.5 shadow-md"
        >
          {filtradas.map((o) => (
            <li
              key={o.clave}
              role="option"
              aria-selected={o.clave === valor}
              // mousedown en vez de click: se adelanta al blur del input, que si no cerraría la lista antes
              onMouseDown={(ev) => { ev.preventDefault(); elegir(o) }}
              className={cn('mx-1.5 cursor-pointer rounded-lg px-3 text-[12px] font-bold text-azul-noche hover:bg-azul-noche hover:text-white')}
              style={{ lineHeight: `${ALTO_FILA}px` }}
            >
              {o.etiqueta}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
```

`userEvent.click` sobre la opción dispara `mousedown`, así que el test de clic pasa con este manejador. Si `cn` no está en `@/shared/lib/utils`, usar la ruta real (la que importan los componentes shadcn del repo).

- [ ] **Step 3: Ver que pasa, check y commit**

```bash
npx vitest run src/shared/ui/CampoAutocompletar.test.tsx
npm run check
git add src/shared/ui/CampoAutocompletar.tsx src/shared/ui/CampoAutocompletar.test.tsx
git commit -m "feat(web): campo con autocompletado en linea para modelo y cliente"
```

---

## Task 14: Web — tokens, `CampoEscaneo`, `FilaCola` y `ListaTecnicos`

**Files:**
- Modify: `src/shared/styles/tokens.css`
- Create: `src/modules/taller/asignaciones/modal/CampoEscaneo.tsx`, `FilaCola.tsx`, `ListaTecnicos.tsx`
- Test: `src/modules/taller/asignaciones/modal/piezas.test.tsx`

**Interfaces:**
- Consumes: Task 7 (`Entrada`), `traducirModelo`, `carga.ts` (`pctTotal`, `nivelCarga`, `CLASE_TEXTO_NIVEL`, `formatearPct`), `TIPO_TRABAJO`, `Checkbox`, `FilaCarga`, `Tecnico`.
- Produces: `CampoEscaneo({ etiqueta: string; onImei: (imei: string) => boolean; onPegado: (texto: string) => void; autoFocus?: boolean })` (`onImei` devuelve `true` si lo aceptó: entonces se vacía el campo); `FilaCola({ e: Entrada; nombresTecnicos: string; seleccionada: boolean; onCargar: () => void; onQuitar: () => void })`; `ListaTecnicos({ tecnicos: Tecnico[]; carga: FilaCarga[]; marcados: number[]; ocupados: Set<number>; marcarGlass: boolean; onMarcar: (idTec: number, marcado: boolean) => void })`.

- [ ] **Step 1: Tokens**

Ningún color se escribe a mano en un componente. Añadir a `@theme` de `tokens.css` los que falten, **reutilizando** un token existente si ya tiene ese hex (buscarlo antes con `grep -i "<hex>" src/shared/styles/tokens.css`):

```css
  /* Modal "Asignar trabajos" (sub-proyecto 3b), calco de los estilos inline de abrirFormularioAsignacion */
  --color-modal-fondo: #DDE1E7;
  --color-cola-roja: #C0392B;          /* "Pendiente de asignar (N)", ✕ al pasar, pastilla de pendientes */
  --color-cola-roja-brd: #EFC4C0;
  --color-cola-roja-bg: #FDE2E1;       /* pastilla del toggle con pendientes */
  --color-cola-verde: #2E7D32;         /* "Asignados (N) · sin guardar", mensaje ok */
  --color-cola-verde-brd: #BFE0C2;
  --color-pill-modelo-bg: #E3F1E4;
  --color-pill-modelo-text: #1B5E20;
  --color-pill-aviso-bg: #FCE7C3;      /* "⚠ falta modelo" y "N asignados" */
  --color-pill-aviso-text: #9A6B00;
  --color-pill-buscando-bg: #EEF1F5;
  --color-cola-sel-bg: #EAF1FF;
  --color-cola-quitar: #C2B3B3;
  --color-pulido-sel-bg: #E8EEF7;
```

- [ ] **Step 2: Test de las piezas**

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CampoEscaneo } from './CampoEscaneo'
import { FilaCola } from './FilaCola'
import { ListaTecnicos } from './ListaTecnicos'
import { entrada } from './estado/fabrica'
import { filaCarga, tecnico } from '../../test/fabrica'

const IMEI = '111111111111111'

describe('CampoEscaneo', () => {
  it('solo dígitos; a los 15 se entrega sin Enter y se vacía si se acepta', async () => {
    const onImei = vi.fn(() => true)
    render(<CampoEscaneo etiqueta="Escanear IMEI → pendiente de asignar" onImei={onImei} onPegado={vi.fn()} />)
    const campo = screen.getByRole('textbox')
    await userEvent.type(campo, '11111a1111111111')
    expect(onImei).toHaveBeenCalledWith(IMEI)
    expect(campo).toHaveValue('')
  })
  it('si no se acepta (repetido) los dígitos se quedan', async () => {
    render(<CampoEscaneo etiqueta="x" onImei={() => false} onPegado={vi.fn()} />)
    await userEvent.type(screen.getByRole('textbox'), IMEI)
    expect(screen.getByRole('textbox')).toHaveValue(IMEI)
  })
  it('incompleto + Enter no hace nada y conserva los dígitos (D6)', async () => {
    const onImei = vi.fn(() => true)
    render(<CampoEscaneo etiqueta="x" onImei={onImei} onPegado={vi.fn()} />)
    await userEvent.type(screen.getByRole('textbox'), '12345{Enter}')
    expect(onImei).not.toHaveBeenCalled()
    expect(screen.getByRole('textbox')).toHaveValue('12345')
  })
  it('un pegado de más de 15 va a onPegado y vacía el campo', async () => {
    const onPegado = vi.fn()
    render(<CampoEscaneo etiqueta="x" onImei={vi.fn(() => true)} onPegado={onPegado} />)
    await userEvent.click(screen.getByRole('textbox'))
    await userEvent.paste(`${IMEI}\n222222222222222`)
    expect(onPegado).toHaveBeenCalledWith(`${IMEI}222222222222222`)
    expect(screen.getByRole('textbox')).toHaveValue('')
  })
})

describe('FilaCola', () => {
  it('roja: IMEI, badge y pastilla de modelo / buscando / falta modelo', () => {
    const { rerender } = render(<FilaCola e={entrada({ seq: 1, imei: IMEI, modelo: '14' })} nombresTecnicos="" seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} />)
    expect(screen.getByText('Rep')).toBeInTheDocument()
    expect(screen.getByText('iPhone 14')).toBeInTheDocument()
    rerender(<FilaCola e={entrada({ seq: 1, imei: IMEI, buscando: true })} nombresTecnicos="" seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} />)
    expect(screen.getByText('Buscando…')).toBeInTheDocument()
    rerender(<FilaCola e={entrada({ seq: 1, imei: IMEI })} nombresTecnicos="" seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} />)
    expect(screen.getByText('⚠ falta modelo')).toBeInTheDocument()
  })
  it('verde glass automática: "auto" y los técnicos; "Calculando…" mientras llega la predicción', () => {
    const { rerender } = render(<FilaCola e={entrada({ seq: 2, imei: IMEI, tipo: 'GLASS', asignada: true, auto: true, tecnicos: [4], modelo: '14' })}
      nombresTecnicos="Técnico G" seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} />)
    expect(screen.getByText('Glass')).toBeInTheDocument()
    expect(screen.getByText('auto')).toBeInTheDocument()
    expect(screen.getByText('Técnico G')).toBeInTheDocument()
    rerender(<FilaCola e={entrada({ seq: 2, imei: IMEI, tipo: 'GLASS', calculando: true, modelo: '14' })} nombresTecnicos="" seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} />)
    expect(screen.getByText('Calculando…')).toBeInTheDocument()
  })
  it('clic carga; la ✕ quita sin cargar', async () => {
    const onCargar = vi.fn()
    const onQuitar = vi.fn()
    render(<FilaCola e={entrada({ seq: 1, imei: IMEI })} nombresTecnicos="" seleccionada={false} onCargar={onCargar} onQuitar={onQuitar} />)
    await userEvent.click(screen.getByRole('button', { name: `Quitar ${IMEI}` }))
    expect(onQuitar).toHaveBeenCalled()
    expect(onCargar).not.toHaveBeenCalled()
    await userEvent.click(screen.getByText(IMEI))
    expect(onCargar).toHaveBeenCalled()
  })
})

describe('ListaTecnicos', () => {
  const tecnicos = [tecnico({ idTec: 3, nombre: 'Técnico A', esGlass: true }), tecnico({ idTec: 4, nombre: 'Técnico B' })]
  const carga = [filaCarga({ idTec: 3, pctPendiente: 29, pctHecho: 0 })]

  it('nombre ● % de Pedidos (0% si no está en la carga) y pastilla "glass" solo en la cola Glass', () => {
    const { rerender } = render(<ListaTecnicos tecnicos={tecnicos} carga={carga} marcados={[]} ocupados={new Set()} marcarGlass={false} onMarcar={vi.fn()} />)
    expect(screen.getByRole('checkbox', { name: /Técnico A.*29%/ })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Técnico B.*0%/ })).toBeInTheDocument()
    expect(screen.queryByText('glass')).toBeNull()
    rerender(<ListaTecnicos tecnicos={tecnicos} carga={carga} marcados={[]} ocupados={new Set()} marcarGlass onMarcar={vi.fn()} />)
    expect(screen.getByText('glass')).toBeInTheDocument()
  })
  it('ocupados deshabilitados y pastilla "N asignado(s)"', () => {
    const { rerender } = render(<ListaTecnicos tecnicos={tecnicos} carga={carga} marcados={[]} ocupados={new Set([3])} marcarGlass={false} onMarcar={vi.fn()} />)
    expect(screen.getByRole('checkbox', { name: /Técnico A/ })).toBeDisabled()
    expect(screen.getByText('1 asignado')).toBeInTheDocument()
    rerender(<ListaTecnicos tecnicos={tecnicos} carga={carga} marcados={[]} ocupados={new Set([3, 4])} marcarGlass={false} onMarcar={vi.fn()} />)
    expect(screen.getByText('2 asignados')).toBeInTheDocument()
  })
  it('marcar avisa con el id y el nuevo estado', async () => {
    const onMarcar = vi.fn()
    render(<ListaTecnicos tecnicos={tecnicos} carga={carga} marcados={[4]} ocupados={new Set()} marcarGlass={false} onMarcar={onMarcar} />)
    await userEvent.click(screen.getByRole('checkbox', { name: /Técnico A/ }))
    expect(onMarcar).toHaveBeenCalledWith(3, true)
    await userEvent.click(screen.getByRole('checkbox', { name: /Técnico B/ }))
    expect(onMarcar).toHaveBeenCalledWith(4, false)
  })
})
```

Si `tecnico(...)`/`filaCarga(...)` de `src/modules/taller/test/fabrica.ts` tienen otra firma, usar la real.

- [ ] **Step 3: `CampoEscaneo.tsx`**

```tsx
import { useState } from 'react'

type Props = { etiqueta: string; onImei: (imei: string) => boolean; onPegado: (texto: string) => void; autoFocus?: boolean }

/** Campo de escaneo del modal: solo dígitos; a los 15 se entrega solo (el lector de códigos es un teclado); más de 15
 *  es un pegado. Un IMEI incompleto + Enter no hace nada y se queda en el campo (calco del código, D6). */
export function CampoEscaneo({ etiqueta, onImei, onPegado, autoFocus }: Props) {
  const [texto, setTexto] = useState('')
  const entregar = (digitos: string) => {
    if (digitos.length > 15) { onPegado(digitos); setTexto(''); return }
    if (digitos.length === 15) { setTexto(onImei(digitos) ? '' : digitos); return }
    setTexto(digitos)
  }
  return (
    <label className="flex flex-col gap-1">
      <span className="text-[12px] font-bold text-azul-gris">{etiqueta}</span>
      <input
        value={texto}
        autoFocus={autoFocus}
        placeholder="Escanea o escribe el IMEI (15 dígitos)..."
        onChange={(ev) => entregar(ev.target.value.replace(/\D/g, ''))}
        onKeyDown={(ev) => { if (ev.key === 'Enter' && texto.length === 15) entregar(texto) }}
        className="rounded border border-borde-input bg-white p-[11px] text-[14px] text-azul-medio"
      />
    </label>
  )
}
```

- [ ] **Step 4: `FilaCola.tsx`**

```tsx
import { traducirModelo } from '../../lib/modelos'
import { TIPO_TRABAJO } from '@/shared/lib/tipoTrabajo'
import { cn } from '@/shared/lib/utils'
import type { Entrada } from './estado/tipos'

type Props = { e: Entrada; nombresTecnicos: string; seleccionada: boolean; onCargar: () => void; onQuitar: () => void }

const PILDORA = 'shrink-0 rounded-md px-2 py-0.5 text-[10.5px] font-bold'

/** Fila de las listas roja y verde (crearFilaPila del JavaFX). */
export function FilaCola({ e, nombresTecnicos, seleccionada, onCargar, onQuitar }: Props) {
  const esGlass = e.tipo === 'GLASS'
  return (
    <div
      onClick={onCargar}
      className={cn('flex cursor-pointer items-center gap-2 border-b border-pill-buscando-bg py-[7px] pr-[9px]',
        seleccionada ? 'border-l-4 border-l-azul-medio bg-cola-sel-bg pl-[5px]' : 'pl-[9px]')}
    >
      <div className="flex min-w-0 flex-1 flex-col gap-[3px] overflow-hidden">
        <div className="flex items-center gap-2">
          <span className="shrink-0 font-mono text-[12px] font-bold text-azul-medio">{e.imei}</span>
          <span className={cn('shrink-0 rounded-md px-1.5 text-[9.5px] font-bold',
            esGlass ? TIPO_TRABAJO.GLASS.clases : TIPO_TRABAJO.REPARACION.clases)}>{esGlass ? 'Glass' : 'Rep'}</span>
          {e.modelo
            ? <span className={cn(PILDORA, 'bg-pill-modelo-bg text-pill-modelo-text')}>{traducirModelo(e.modelo)}</span>
            : e.buscando
              ? <span className={cn(PILDORA, 'bg-pill-buscando-bg text-azul-gris')}>Buscando…</span>
              : <span className={cn(PILDORA, 'bg-pill-aviso-bg text-pill-aviso-text')}>⚠ falta modelo</span>}
          {e.calculando && <span className={cn(PILDORA, 'bg-pill-buscando-bg text-azul-gris')}>Calculando…</span>}
        </div>
        {e.asignada && e.tecnicos.length > 0 && (
          <div className="flex items-center gap-1.5">
            {e.auto && <span className={cn('rounded-md px-1.5 text-[9.5px] font-bold', TIPO_TRABAJO.GLASS.clases)}>auto</span>}
            <span className="text-[10.5px] text-azul-gris">{nombresTecnicos}</span>
          </div>
        )}
      </div>
      <button
        type="button"
        aria-label={`Quitar ${e.imei}`}
        onClick={(ev) => { ev.stopPropagation(); onQuitar() }}
        className="shrink-0 px-1 text-[12px] text-cola-quitar hover:text-cola-roja"
      >
        ✕
      </button>
    </div>
  )
}
```

La ruta de `traducirModelo` es `src/modules/taller/lib/modelos.ts`; ajustar el relativo si el fichero queda a otra profundidad.

- [ ] **Step 5: `ListaTecnicos.tsx`**

```tsx
import { Checkbox } from '@/shared/ui/checkbox'
import { TIPO_TRABAJO } from '@/shared/lib/tipoTrabajo'
import { cn } from '@/shared/lib/utils'
import type { FilaCarga, Tecnico } from '@/shared/api/client'
import { CLASE_TEXTO_NIVEL, formatearPct, nivelCarga, pctTotal } from '../carga'

type Props = {
  tecnicos: Tecnico[]
  carga: FilaCarga[]
  marcados: number[]
  ocupados: Set<number>
  marcarGlass: boolean
  onMarcar: (idTec: number, marcado: boolean) => void
}

/** "Técnicos a asignar": nombre ● % de Pedidos (solo Pedidos, decisión 2026-07-10), pastilla "glass" en la cola Glass,
 *  y los que ya tienen el IMEI en esa categoría deshabilitados (el bloqueo del duplicado se hace al seleccionar). */
export function ListaTecnicos({ tecnicos, carga, marcados, ocupados, marcarGlass, onMarcar }: Props) {
  const nOcupados = tecnicos.filter((t) => ocupados.has(t.idTec)).length
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <span className="text-[12px] font-bold text-azul-gris">Técnicos a asignar</span>
        {nOcupados >= 1 && (
          <span className="rounded-full bg-pill-aviso-bg px-[9px] py-0.5 text-[10.5px] font-bold text-pill-aviso-text">
            {nOcupados} {nOcupados === 1 ? 'asignado' : 'asignados'}
          </span>
        )}
      </div>
      <div className="max-h-[150px] overflow-y-auto rounded border border-borde-input bg-white p-2">
        {tecnicos.map((t) => {
          const fila = carga.find((f) => f.idTec === t.idTec)
          const total = fila ? pctTotal(fila) : 0
          const ocupado = ocupados.has(t.idTec)
          const id = `tec-${t.idTec}`
          return (
            <div key={t.idTec} className="flex items-center gap-2 py-[3px]">
              <Checkbox id={id} checked={marcados.includes(t.idTec) && !ocupado} disabled={ocupado}
                onCheckedChange={(v) => onMarcar(t.idTec, v === true)} />
              <label htmlFor={id} className={cn('flex items-center gap-1 text-[12px]', ocupado && 'opacity-50')}>
                <span className="text-azul-medio">{t.nombre}</span>
                <span className="text-[9px] text-carga-pedidos">●</span>
                <span className={cn('font-bold', CLASE_TEXTO_NIVEL[nivelCarga(total)])}>{formatearPct(total)}</span>
                {marcarGlass && t.esGlass && (
                  <span className={cn('rounded-md px-1.5 text-[9.5px] font-bold', TIPO_TRABAJO.GLASS.clases)}>glass</span>
                )}
              </label>
            </div>
          )
        })}
      </div>
      <span className="text-[10.5px] italic text-azul-gris">↳ Se mantienen del IMEI anterior; cámbialos solo si hace falta.</span>
    </div>
  )
}
```

- [ ] **Step 6: Ver que pasan, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal/piezas.test.tsx
npm run check
git add src/shared/styles/tokens.css src/modules/taller/asignaciones/modal
git commit -m "feat(asignar): campo de escaneo, fila de cola y lista de tecnicos con su carga"
```

---

## Task 15: Web — `DetalleEntrada` y `PanelRico`

**Files:**
- Modify: `src/modules/taller/asignaciones/modal/estado/tipos.ts`, `reductor.ts` (acción `LIMPIAR_MENSAJE`)
- Create: `src/modules/taller/asignaciones/modal/DetalleEntrada.tsx`, `PanelRico.tsx`, `MensajeEscaneo.tsx`
- Test: `src/modules/taller/asignaciones/modal/PanelRico.test.tsx`

**Interfaces:**
- Consumes: Tasks 7-14; `MODELOS_ORDENADOS`, `traducirModelo`; `BotonPrimario`; `Cliente`, `Tecnico`, `FilaCarga`.
- Produces: `DetalleEntrada({ estado, dispatch, tecnicos, carga, clientes })`, `PanelRico({ estado, dispatch, tecnicos, carga, clientes })`, `MensajeEscaneo({ mensaje: Mensaje | null })`; acción `{ tipo: 'LIMPIAR_MENSAJE'; panel: 'rico' | 'pulido' }`.

- [ ] **Step 1: Acción `LIMPIAR_MENSAJE`**

El JavaFX borra el mensaje bajo el campo en cuanto se teclea (`lblScanErr.setText("")`). Añadir a `Accion` en `tipos.ts`:

```ts
  | { tipo: 'LIMPIAR_MENSAJE'; panel: 'rico' | 'pulido' }
```

y a `reducir`:

```ts
    case 'LIMPIAR_MENSAJE': return a.panel === 'rico' ? { ...s, mensajeScan: null } : { ...s, mensajePulido: null }
```

con su caso en `reductor.test.ts`:

```ts
  it('limpiar el mensaje del panel', () => {
    const s = estado({ mensajeScan: { texto: 'x', tono: 'error' }, mensajePulido: { texto: 'y', tono: 'ok' } })
    expect(reducir(s, { tipo: 'LIMPIAR_MENSAJE', panel: 'rico' }).mensajeScan).toBeNull()
    expect(reducir(s, { tipo: 'LIMPIAR_MENSAJE', panel: 'pulido' }).mensajePulido).toBeNull()
  })
```

Y en `CampoEscaneo` una prop opcional `onTeclear?: () => void` que se llama en cada cambio con 15 dígitos o menos, **antes** de entregar (así un repetido vuelve a pintar su mensaje después de limpiarlo).

- [ ] **Step 2: Test del panel (con el reductor real)**

```tsx
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useReducer } from 'react'
import { PanelRico } from './PanelRico'
import { reducir } from './estado/reductor'
import { estadoInicial, type EstadoModal } from './estado/tipos'
import { filaTabla } from './estado/fabrica'
import { filaCarga, tecnico } from '../../test/fabrica'

const IMEI = '111111111111111'
const tecnicos = [tecnico({ idTec: 3, nombre: 'Técnico A' }), tecnico({ idTec: 4, nombre: 'Técnico B', esGlass: true })]
const clientes = [{ idCli: 5, nombre: 'CLIENTE UNO', activo: true, updatedAt: '' }, { idCli: 6, nombre: 'CLIENTE VIEJO', activo: false, updatedAt: '' }]

function Harness({ inicial }: { inicial: EstadoModal }) {
  const [estado, dispatch] = useReducer(reducir, inicial)
  return <PanelRico estado={estado} dispatch={dispatch} tecnicos={tecnicos} carga={[filaCarga({ idTec: 3 })]} clientes={clientes} />
}

const escanear = async (imei = IMEI) => userEvent.type(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...'), imei)

describe('PanelRico', () => {
  it('escanear añade a "Pendiente de asignar" y carga el detalle', async () => {
    render(<Harness inicial={estadoInicial([])} />)
    await escanear()
    expect(screen.getByText('Pendiente de asignar (1)')).toBeInTheDocument()
    expect(screen.getByTestId('imei-en-curso')).toHaveTextContent(IMEI)
    expect(screen.getByRole('button', { name: 'Asignar →' })).toBeDisabled()
  })
  it('repetido: mensaje y el IMEI se queda en el campo', async () => {
    render(<Harness inicial={estadoInicial([])} />)
    await escanear()
    await escanear()
    expect(screen.getByText('Ese IMEI ya está en la cola (Reparación).')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...')).toHaveValue(IMEI)
  })
  it('modelo + técnico habilitan Asignar; al asignar pasa a "Asignados (1) · sin guardar" con su técnico', async () => {
    render(<Harness inicial={estadoInicial([])} />)
    await escanear()
    await userEvent.type(screen.getByRole('combobox', { name: 'Modelo de iPhone' }), 'iPhone 14 Pro Max{Enter}')
    await userEvent.click(screen.getByRole('checkbox', { name: /Técnico A/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Asignar →' }))
    expect(screen.getByText('Asignados (1) · sin guardar')).toBeInTheDocument()
    expect(screen.getAllByText('Técnico A')).toHaveLength(2)   // lista de técnicos + segunda línea de la fila verde
    expect(screen.getByTestId('imei-en-curso')).toHaveTextContent('—')
  })
  it('una verde se reabre con "Guardar cambios"', async () => {
    render(<Harness inicial={estadoInicial([])} />)
    await escanear()
    await userEvent.type(screen.getByRole('combobox', { name: 'Modelo de iPhone' }), 'iPhone 14{Enter}')
    await userEvent.click(screen.getByRole('checkbox', { name: /Técnico A/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Asignar →' }))
    await userEvent.click(screen.getByText(IMEI))
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeEnabled()
  })
  it('bloqueo del duplicado al seleccionar y "ya tiene glass"', async () => {
    const tabla = [filaTabla({ idRep: 'A1', imei: IMEI, idTec: 3 }), filaTabla({ idRep: 'AG1', imei: IMEI, idTec: 4, nombreTecnico: 'Técnico B' })]
    render(<Harness inicial={estadoInicial(tabla)} />)
    await escanear()
    expect(screen.getByRole('checkbox', { name: /Técnico A/ })).toBeDisabled()
    expect(screen.getByText('1 asignado')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Lleva glass' })).toBeDisabled()
    expect(screen.getByText('ya tiene glass: Técnico B')).toBeInTheDocument()
  })
  it('el cliente inactivo que ya tiene el IMEI se muestra; el buscador no lo ofrece a otros', async () => {
    const s = reducir(reducir(estadoInicial([]), { tipo: 'ESCANEAR', imei: IMEI }), { tipo: 'LOOKUP_RESUELTO', seq: 1, modelo: '14', idCliBd: 6 })
    render(<Harness inicial={s} />)
    expect(screen.getByRole('combobox', { name: 'Cliente' })).toHaveValue('CLIENTE VIEJO')
  })
  it('en la cola Glass no hay chasis ni "Lleva glass"', async () => {
    render(<Harness inicial={{ ...estadoInicial([]), pestana: 'GLASS' }} />)
    await escanear()
    expect(screen.queryByRole('checkbox', { name: 'Reparación de chasis' })).toBeNull()
    expect(screen.queryByRole('checkbox', { name: 'Lleva glass' })).toBeNull()
  })
  it('pegado: añade en rojo sin cargar el detalle', async () => {
    render(<Harness inicial={estadoInicial([])} />)
    await userEvent.click(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...'))
    await userEvent.paste(`${IMEI}222222222222222`)
    expect(screen.getByText('Pendiente de asignar (2)')).toBeInTheDocument()
    expect(screen.getByText('2 IMEIs añadidos.')).toBeInTheDocument()
    expect(screen.getByTestId('imei-en-curso')).toHaveTextContent('—')
  })
})
```

- [ ] **Step 3: `MensajeEscaneo.tsx`**

```tsx
import { cn } from '@/shared/lib/utils'
import type { Mensaje } from './estado/tipos'

export function MensajeEscaneo({ mensaje }: { mensaje: Mensaje | null }) {
  return (
    <p role="status" className={cn('min-h-[15px] text-[11px]', mensaje?.tono === 'ok' ? 'text-cola-verde' : 'text-texto-error')}>
      {mensaje?.texto ?? ''}
    </p>
  )
}
```

- [ ] **Step 4: `DetalleEntrada.tsx`**

```tsx
import type { Dispatch } from 'react'
import type { Cliente, FilaCarga, Tecnico } from '@/shared/api/client'
import { BotonPrimario } from '@/shared/ui/Botones'
import { CampoAutocompletar } from '@/shared/ui/CampoAutocompletar'
import { Checkbox } from '@/shared/ui/checkbox'
import { MODELOS_ORDENADOS, traducirModelo } from '../../lib/modelos'
import { ListaTecnicos } from './ListaTecnicos'
import { asignarHabilitado, entradaActual, glassAbiertaBd, promptModelo, tecnicosOcupados } from './estado/derivados'
import type { Accion, EstadoModal } from './estado/tipos'

type Props = { estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[] }

const ETIQUETA = 'text-[12px] font-bold text-azul-gris'
const SIN = 'SIN'
const OPCIONES_MODELO = MODELOS_ORDENADOS.map((c) => ({ clave: c, etiqueta: traducirModelo(c) }))

/** Panel derecho de Reparación y Glass; deshabilitado sin entrada cargada. */
export function DetalleEntrada({ estado, dispatch, tecnicos, carga, clientes }: Props) {
  const e = entradaActual(estado)
  const esRep = (e?.tipo ?? estado.pestana) === 'REPARACION'
  const ocupados = e ? tecnicosOcupados(estado.tabla, e.imei, e.tipo) : new Set<number>()
  const glassBd = e && e.tipo === 'REPARACION' ? glassAbiertaBd(estado.tabla, e.imei) : null
  // Activos más el que ya tenga la entrada (un inactivo de BD se muestra, D6)
  const opcionesCliente = [{ clave: SIN, etiqueta: '— Sin cliente —' },
    ...clientes.filter((c) => c.activo || c.idCli === e?.idCli).map((c) => ({ clave: String(c.idCli), etiqueta: c.nombre }))]
  const valorCliente = !e ? null : e.sinCliente ? SIN : e.idCli != null ? String(e.idCli) : null

  return (
    <fieldset disabled={!e} className="flex flex-col gap-2 rounded-md border border-borde-input bg-white p-4 disabled:opacity-60">
      <div key={e?.seq ?? 'vacio'} className="contents">
        <span className="text-[11px] font-bold text-azul-gris">IMEI en curso</span>
        <span data-testid="imei-en-curso" className="font-mono text-[18px] font-bold text-azul-medio">{e?.imei ?? '—'}</span>
        <span className={ETIQUETA}>Modelo de iPhone</span>
        <CampoAutocompletar aria-label="Modelo de iPhone" placeholder={e ? promptModelo(e) : 'Escribe modelo...'}
          valor={e?.modelo ?? null} opciones={OPCIONES_MODELO}
          onElegir={(modelo) => dispatch({ tipo: 'DECIDIR_MODELO', modelo })}
          onTextoCambiado={() => { if (e?.modelo) dispatch({ tipo: 'BORRAR_MODELO' }) }} />
        <ListaTecnicos tecnicos={tecnicos} carga={carga} marcados={estado.borrador.tecnicos} ocupados={ocupados}
          marcarGlass={estado.pestana === 'GLASS'} onMarcar={(idTec, marcado) => dispatch({ tipo: 'MARCAR_TECNICO', idTec, marcado })} />
        <span className={ETIQUETA}>Cliente (opcional)</span>
        <CampoAutocompletar aria-label="Cliente" placeholder="Escribe cliente..." valor={valorCliente} opciones={opcionesCliente}
          onElegir={(clave) => dispatch({ tipo: 'ELEGIR_CLIENTE', ref: clave === SIN ? { idCli: null, sin: true } : { idCli: Number(clave), sin: false } })} />
        <span className={ETIQUETA}>Comentario (opcional)</span>
        <textarea aria-label="Comentario" rows={2} placeholder="Instrucciones para el técnico..." value={estado.borrador.comentario}
          onChange={(ev) => dispatch({ tipo: 'CAMBIAR_COMENTARIO', texto: ev.target.value })}
          className="rounded border border-borde-input bg-white p-2 text-[13px] text-azul-medio" />
        {esRep && (
          <label className="flex items-center gap-2 text-[12px] text-azul-gris">
            <Checkbox aria-label="Reparación de chasis" checked={estado.borrador.esChasis}
              onCheckedChange={(v) => dispatch({ tipo: 'CAMBIAR_CHASIS', valor: v === true })} />
            Reparación de chasis
          </label>
        )}
        {esRep && (
          <div className="flex items-center gap-2">
            <label className="flex items-center gap-2 text-[12px] text-azul-gris">
              <Checkbox aria-label="Lleva glass" checked={!!e?.llevaGlass} disabled={glassBd != null}
                onCheckedChange={(v) => dispatch({ tipo: 'MARCAR_LLEVA_GLASS', valor: v === true })} />
              Lleva glass
            </label>
            {glassBd && <span className="text-[10.5px] italic text-azul-gris">ya tiene glass: {glassBd}</span>}
          </div>
        )}
        <BotonPrimario className="w-full" disabled={!asignarHabilitado(estado)} onClick={() => dispatch({ tipo: 'ASIGNAR' })}>
          {e?.asignada ? 'Guardar cambios' : 'Asignar →'}
        </BotonPrimario>
      </div>
    </fieldset>
  )
}
```

- [ ] **Step 5: `PanelRico.tsx`**

```tsx
import type { Dispatch } from 'react'
import type { Cliente, FilaCarga, Tecnico } from '@/shared/api/client'
import { CampoEscaneo } from './CampoEscaneo'
import { DetalleEntrada } from './DetalleEntrada'
import { FilaCola } from './FilaCola'
import { MensajeEscaneo } from './MensajeEscaneo'
import { filasCola, imeiEnColaActiva } from './estado/derivados'
import type { Accion, Entrada, EstadoModal } from './estado/tipos'

type Props = { estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[] }

/** Reparación y Glass comparten esqueleto: escaneo, listas roja y verde de la cola activa, y detalle. */
export function PanelRico(props: Props) {
  const { estado, dispatch, tecnicos } = props
  if (estado.pestana === 'PULIDO') return null
  const { rojas, verdes } = filasCola(estado, estado.pestana)
  // En el orden de la lista de técnicos, como el JavaFX
  const nombres = (e: Entrada) => tecnicos.filter((t) => e.tecnicos.includes(t.idTec)).map((t) => t.nombre).join(', ')
  const fila = (e: Entrada) => (
    <FilaCola key={e.seq} e={e} nombresTecnicos={nombres(e)} seleccionada={e.seq === estado.actual}
      onCargar={() => dispatch({ tipo: 'CARGAR', seq: e.seq })} onQuitar={() => dispatch({ tipo: 'QUITAR', seq: e.seq })} />
  )
  return (
    <div className="flex flex-col gap-3">
      <CampoEscaneo etiqueta="Escanear IMEI → pendiente de asignar" autoFocus
        onTeclear={() => dispatch({ tipo: 'LIMPIAR_MENSAJE', panel: 'rico' })}
        onImei={(imei) => { const repetido = imeiEnColaActiva(estado, imei); dispatch({ tipo: 'ESCANEAR', imei }); return !repetido }}
        onPegado={(texto) => dispatch({ tipo: 'PEGAR', texto })} />
      <MensajeEscaneo mensaje={estado.mensajeScan} />
      <hr className="border-borde-input" />
      <div className="flex flex-wrap gap-[18px]">
        <div className="flex w-[300px] shrink-0 flex-col gap-1.5">
          <span className="text-[11.5px] font-bold text-cola-roja">Pendiente de asignar ({rojas.length})</span>
          <div className="max-h-[220px] min-h-[34px] overflow-y-auto rounded-md border border-cola-roja-brd bg-white">{rojas.map(fila)}</div>
          <span className="pt-2.5 text-[11.5px] font-bold text-cola-verde">Asignados ({verdes.length}) · sin guardar</span>
          <div className="max-h-[220px] min-h-[34px] overflow-y-auto rounded-md border border-cola-verde-brd bg-white">{verdes.map(fila)}</div>
        </div>
        <div className="min-w-[280px] flex-1"><DetalleEntrada {...props} /></div>
      </div>
    </div>
  )
}
```

- [ ] **Step 6: Ver que pasan, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal
npm run check
git add src/modules/taller/asignaciones/modal
git commit -m "feat(asignar): panel de reparacion y glass con listas roja y verde y detalle"
```

---

## Task 16: Web — `PanelPulido`

**Files:**
- Create: `src/modules/taller/asignaciones/modal/PanelPulido.tsx`
- Test: `src/modules/taller/asignaciones/modal/PanelPulido.test.tsx`

**Interfaces:**
- Consumes: Tasks 10, 13-15; `ComboNavy` (`{ valor: string | null; opciones: { valor; etiqueta }[]; onChange: (valor: string) => void; textoVacio: string; ancho: number; 'aria-label': string }`).
- Produces: `PanelPulido({ estado, dispatch, tecnicos, carga, clientes })`.

Diferencia de presentación aceptada (se anota en la ficha): el combo de técnico muestra en todas partes el texto `Nombre (P62%)`; el JavaFX pinta en el desplegable abierto el punto de color, pero `ComboNavy` solo admite texto.

- [ ] **Step 1: Test**

```tsx
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useReducer } from 'react'
import { PanelPulido } from './PanelPulido'
import { reducir } from './estado/reductor'
import { estadoInicial } from './estado/tipos'
import { filaCarga, tecnico } from '../../test/fabrica'

const IMEI = '111111111111111'
const tecnicos = [tecnico({ idTec: 3, nombre: 'Técnico A' }), tecnico({ idTec: 4, nombre: 'Técnico B' })]
const clientes = [{ idCli: 5, nombre: 'CLIENTE UNO', activo: true, updatedAt: '' }]

function Harness() {
  const [estado, dispatch] = useReducer(reducir, { ...estadoInicial([]), pestana: 'PULIDO' as const })
  return <PanelPulido estado={estado} dispatch={dispatch} tecnicos={tecnicos} carga={[filaCarga({ idTec: 3, pctPendiente: 62, pctHecho: 0 })]} clientes={clientes} />
}
const campo = () => screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...')

describe('PanelPulido', () => {
  it('sin nada: "Nada añadido aún" y detalle deshabilitado', () => {
    render(<Harness />)
    expect(screen.getByText('Nada añadido aún')).toBeInTheDocument()
    expect(screen.getByTestId('imei-pulido')).toHaveTextContent('—')
  })
  it('sin técnico arriba la fila sale en rojo "(sin técnico) · —"', async () => {
    render(<Harness />)
    await userEvent.type(campo(), IMEI)
    expect(screen.getByText('1 en pulido')).toBeInTheDocument()
    expect(screen.getByText('(sin técnico) · —')).toBeInTheDocument()
  })
  it('el técnico de arriba se aplica a lo que se escanea y se ve con su carga', async () => {
    render(<Harness />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Técnico para los IMEIs que escanees' }))
    await userEvent.click(screen.getByRole('option', { name: 'Técnico A (P62%)' }))
    await userEvent.type(campo(), IMEI)
    expect(screen.getByText('Técnico A · —')).toBeInTheDocument()
  })
  it('detalle: "— Sin cliente —" se resume como "sin cliente"', async () => {
    render(<Harness />)
    await userEvent.type(campo(), IMEI)
    await userEvent.type(screen.getByRole('combobox', { name: 'Cliente' }), '— Sin cliente —{Enter}')
    expect(screen.getByText('(sin técnico) · sin cliente')).toBeInTheDocument()
  })
  it('repetido: mensaje', async () => {
    render(<Harness />)
    await userEvent.type(campo(), IMEI)
    await userEvent.type(campo(), IMEI)
    expect(screen.getByText('Ese IMEI ya está en la lista de pulido.')).toBeInTheDocument()
  })
})
```

Ajustar la forma de abrir el `ComboNavy` a la que usan sus propios tests (`ComboNavy.test.tsx`), si el rol no es `combobox`.

- [ ] **Step 2: Implementar**

```tsx
import type { Dispatch } from 'react'
import type { Cliente, FilaCarga, Tecnico } from '@/shared/api/client'
import { CampoAutocompletar } from '@/shared/ui/CampoAutocompletar'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { cn } from '@/shared/lib/utils'
import { formatearPct, pctTotal } from '../carga'
import { CampoEscaneo } from './CampoEscaneo'
import { MensajeEscaneo } from './MensajeEscaneo'
import type { Accion, EstadoModal, FilaPulido } from './estado/tipos'

type Props = { estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[] }

const ETIQUETA = 'text-[12px] font-bold text-azul-gris'
const SIN = 'SIN'

/** Panel de Pulido (construirPulidoPane): técnico de arriba, escaneo, lista y detalle que se aplica al momento. */
export function PanelPulido({ estado, dispatch, tecnicos, carga, clientes }: Props) {
  const opcionesTec = tecnicos.map((t) => {
    const f = carga.find((x) => x.idTec === t.idTec)
    return { valor: String(t.idTec), etiqueta: `${t.nombre} (P${formatearPct(f ? pctTotal(f) : 0)})` }
  })
  const nombreTec = (id: number | null) => tecnicos.find((t) => t.idTec === id)?.nombre
  const nombreCli = (f: FilaPulido) => (f.sinCliente ? 'sin cliente' : clientes.find((c) => c.idCli === f.idCli)?.nombre ?? '—')
  const sel = estado.pulido.find((f) => f.seq === estado.pulidoSel)
  const opcionesCliente = [{ clave: SIN, etiqueta: '— Sin cliente —' },
    ...clientes.filter((c) => c.activo || c.idCli === sel?.idCli).map((c) => ({ clave: String(c.idCli), etiqueta: c.nombre }))]
  const n = estado.pulido.length

  return (
    <div className="flex flex-col gap-2">
      <span className={ETIQUETA}>Técnico (se aplica a los IMEIs que escanees)</span>
      <ComboNavy aria-label="Técnico para los IMEIs que escanees" valor={estado.tecPulidoArriba == null ? null : String(estado.tecPulidoArriba)}
        opciones={opcionesTec} onChange={(v) => dispatch({ tipo: 'PULIDO_TEC_ARRIBA', idTec: Number(v) })} textoVacio="" ancho={344} />
      <hr className="border-borde-input" />
      <CampoEscaneo etiqueta="Escanear IMEI → pulido"
        onTeclear={() => dispatch({ tipo: 'LIMPIAR_MENSAJE', panel: 'pulido' })}
        onImei={(imei) => { const repetido = estado.pulido.some((f) => f.imei === imei); dispatch({ tipo: 'PULIDO_ESCANEAR', imei }); return !repetido }}
        onPegado={(texto) => dispatch({ tipo: 'PULIDO_PEGAR', texto })} />
      <MensajeEscaneo mensaje={estado.mensajePulido} />
      <hr className="border-borde-input" />
      <span className={cn('text-[11.5px] font-bold', n === 0 ? 'text-azul-gris' : 'text-cola-verde')}>
        {n === 0 ? 'Nada añadido aún' : `${n} en pulido`}
      </span>
      <div className="flex flex-wrap gap-[18px]">
        <div className="max-h-[300px] w-[300px] min-w-[280px] shrink-0 overflow-y-auto rounded-md border border-cola-verde-brd bg-white">
          {estado.pulido.map((f) => (
            <div key={f.seq} onClick={() => dispatch({ tipo: 'PULIDO_SELECCIONAR', seq: f.seq })}
              className={cn('flex cursor-pointer items-center gap-2 border-b border-pill-buscando-bg p-2', f.seq === estado.pulidoSel && 'bg-pulido-sel-bg')}>
              <div className="flex flex-1 flex-col gap-px">
                <span className="font-mono text-[12px] font-bold text-azul-medio">{f.imei}</span>
                {f.idTec == null
                  ? <span className="text-[11px] font-bold text-cola-roja">(sin técnico) · {nombreCli(f)}</span>
                  : <span className="text-[11px] text-azul-gris">{nombreTec(f.idTec)} · {nombreCli(f)}</span>}
              </div>
              <button type="button" aria-label={`Quitar ${f.imei}`} className="px-1 text-[12px] text-cola-quitar hover:text-cola-roja"
                onClick={(ev) => { ev.stopPropagation(); dispatch({ tipo: 'PULIDO_QUITAR', seq: f.seq }) }}>✕</button>
            </div>
          ))}
        </div>
        <fieldset disabled={!sel} className="flex min-w-[280px] flex-1 flex-col gap-2 rounded-md border border-borde-input bg-white p-4 disabled:opacity-60">
          <div key={sel?.seq ?? 'vacio'} className="contents">
            <span className="text-[11px] font-bold text-azul-gris">IMEI en curso</span>
            <span data-testid="imei-pulido" className="font-mono text-[18px] font-bold text-azul-medio">{sel?.imei ?? '—'}</span>
            <span className={ETIQUETA}>Técnico</span>
            <ComboNavy aria-label="Técnico del pulido" valor={sel?.idTec == null ? null : String(sel.idTec)} opciones={opcionesTec}
              onChange={(v) => dispatch({ tipo: 'PULIDO_TECNICO', idTec: Number(v) })} textoVacio="" ancho={344} disabled={!sel} />
            <span className={ETIQUETA}>Cliente (opcional)</span>
            <CampoAutocompletar aria-label="Cliente" placeholder="Escribe cliente..." opciones={opcionesCliente}
              valor={!sel ? null : sel.sinCliente ? SIN : sel.idCli != null ? String(sel.idCli) : null}
              onElegir={(clave) => dispatch({ tipo: 'PULIDO_CLIENTE', ref: clave === SIN ? { idCli: null, sin: true } : { idCli: Number(clave), sin: false } })} />
            <span className={ETIQUETA}>Comentario</span>
            <textarea aria-label="Comentario del pulido" rows={2} placeholder="Instrucciones para el técnico..." value={sel?.comentario ?? ''}
              onChange={(ev) => dispatch({ tipo: 'PULIDO_COMENTARIO', texto: ev.target.value })}
              className="rounded border border-borde-input bg-white p-2 text-[13px] text-azul-medio" />
          </div>
        </fieldset>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Ver que pasa, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones/modal/PanelPulido.test.tsx
npm run check
git add src/modules/taller/asignaciones/modal
git commit -m "feat(asignar): panel de pulido"
```

---

## Task 17: Web — `AsignarTrabajosDialog` y el botón "Asignar"

**Files:**
- Create: `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.tsx`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`, `AsignacionesPage.test.tsx`
- Test: `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.test.tsx`

**Interfaces:**
- Consumes: todo lo anterior; `useTecnicos(true)` de `../../api`; `useCargaTecnicos` de `../api`; `crearClavesIdempotencia` de `../../formulario/clavesIdempotencia`; `useAlerta` (`mostrarError`, `mostrarAviso`); `esErrorGestionadoGlobalmente`, `mensajeDeError` de `@/shared/api/errors`; `Dialog`, `DialogContent`, `DialogTitle`, `DialogDescription`; `ConfirmDialog`.
- Produces: `AsignarTrabajosDialog({ tabla: ReparacionResumen[]; onCerrar: () => void; onInteraccion: (abierta: boolean) => void })`. Se monta solo mientras está abierto: cada apertura empieza con el estado vacío.

- [ ] **Step 1: Test del modal (MSW, flujo completo)**

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { AsignarTrabajosDialog } from './AsignarTrabajosDialog'
import { filaCarga, tecnico } from '../../test/fabrica'

const IMEI = '111111111111111'
let lotes: { cuerpo: unknown; clave: string | null }[]
let respuestaLote: () => Response

beforeEach(() => {
  lotes = []
  respuestaLote = () => HttpResponse.json({ creadas: [], conflictos: [] })
  server.use(
    http.get('*/api/tecnicos/activos', () => HttpResponse.json([tecnico({ idTec: 3, nombre: 'Técnico A' }), tecnico({ idTec: 4, nombre: 'Técnico G', esGlass: true })])),
    http.get('*/api/reparaciones/carga-tecnicos', () => HttpResponse.json({ pedidos: [filaCarga({ idTec: 3 })], total: [] })),
    http.get('*/api/clientes', () => HttpResponse.json([])),
    http.get('*/api/telefonos/:imei/modelo', () => HttpResponse.json({ value: '14' })),
    http.get('*/api/telefonos/:imei/cliente', () => HttpResponse.json({ value: '' })),
    http.post('*/api/glass/prediccion', () => HttpResponse.json({ idTec: 4, nombre: 'Técnico G' })),
    http.post('*/api/asignaciones/lote', async ({ request }) => {
      lotes.push({ cuerpo: await request.json(), clave: request.headers.get('Idempotency-Key') })
      return respuestaLote()
    }),
  )
})

function abrir() {
  const onCerrar = vi.fn()
  renderConProviders(<AsignarTrabajosDialog tabla={[]} onCerrar={onCerrar} onInteraccion={vi.fn()} />, { sesion: SESION_SUPER })
  return onCerrar
}

async function asignarUnaConGlass() {
  await userEvent.type(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...'), IMEI)
  await screen.findByText('iPhone 14')
  await userEvent.click(await screen.findByRole('checkbox', { name: /Técnico A/ }))
  await userEvent.click(screen.getByRole('checkbox', { name: 'Lleva glass' }))
  await userEvent.click(screen.getByRole('button', { name: 'Asignar →' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Guardar (2)' })).toBeEnabled())
}

describe('AsignarTrabajosDialog', () => {
  it('abre con su cabecera, las tres pestañas y Guardar (0) deshabilitado', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Asignar trabajos' })).toBeInTheDocument()
    expect(screen.getByText('Elige el tipo, escanea IMEIs y configúralos. Los técnicos se mantienen entre IMEIs. Se guardan todos al final.')).toBeInTheDocument()
    for (const n of ['Reparación', 'Glass', 'Pulido']) expect(screen.getByRole('button', { name: new RegExp(`^${n}`) })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar (0)' })).toBeDisabled()
    expect(screen.getByText('0 configurados · 0 pendientes')).toBeInTheDocument()
  })

  it('flujo completo: reparación + glass automática → un lote con clave → se cierra', async () => {
    const onCerrar = abrir()
    await asignarUnaConGlass()
    expect(screen.getByRole('button', { name: /^Glass/ })).toHaveTextContent('1')
    await userEvent.click(screen.getByRole('button', { name: /^Glass/ }))
    expect(screen.getByText('auto')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar (2)' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(lotes).toHaveLength(1)
    expect(lotes[0].clave).toBeTruthy()
    expect(lotes[0].cuerpo).toEqual({
      telefonos: [{ imei: IMEI, modelo: '14', idCli: null, clienteExplicito: false }],
      asignaciones: [
        { imei: IMEI, categoria: 'R', idTec: 3, comentario: null, esChasis: false },
        { imei: IMEI, categoria: 'G', idTec: 4, comentario: null, esChasis: false },
      ],
    })
  })

  it('conflictos: aviso con el texto del JavaFX', async () => {
    respuestaLote = () => HttpResponse.json({ creadas: [], conflictos: [{ imei: IMEI, idTec: 3, nombreTecnico: 'Técnico A', categoria: 'R' }] })
    abrir()
    await asignarUnaConGlass()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar (2)' }))
    expect(await screen.findByText(/Algunas asignaciones no se crearon:/)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(`• ${IMEI} → Técnico A \\(ya asignado · Reparación\\)`))).toBeInTheDocument()
  })

  it('si el guardado falla, el modal sigue abierto y el reintento usa la MISMA clave', async () => {
    respuestaLote = () => new HttpResponse(JSON.stringify({ message: 'Falta el modelo del IMEI' }), { status: 422 })
    const onCerrar = abrir()
    await asignarUnaConGlass()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar (2)' }))
    await waitFor(() => expect(lotes).toHaveLength(1))
    expect(onCerrar).not.toHaveBeenCalled()
    await userEvent.click(await screen.findByRole('button', { name: 'Aceptar' }))   // el diálogo de error
    respuestaLote = () => HttpResponse.json({ creadas: [], conflictos: [] })
    await userEvent.click(screen.getByRole('button', { name: 'Guardar (2)' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(lotes[1].clave).toBe(lotes[0].clave)
  })

  it('si la predicción falla: glass roja y aviso', async () => {
    server.use(http.post('*/api/glass/prediccion', () => new HttpResponse(null, { status: 500 })))
    abrir()
    await userEvent.type(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...'), IMEI)
    await screen.findByText('iPhone 14')
    await userEvent.click(await screen.findByRole('checkbox', { name: /Técnico A/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Lleva glass' }))
    await userEvent.click(screen.getByRole('button', { name: 'Asignar →' }))
    expect(await screen.findByText('No se pudo calcular la glass automática; asígnala a mano.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar (1)' })).toBeDisabled()
  })

  it('cerrar: sin entradas se cierra; con entradas pide "Descartar" con el total', async () => {
    const onCerrar = abrir()
    await userEvent.keyboard('{Escape}')
    expect(onCerrar).toHaveBeenCalledTimes(1)
  })

  it('cerrar con entradas pide confirmación', async () => {
    const onCerrar = abrir()
    await asignarUnaConGlass()
    await userEvent.click(screen.getByRole('button', { name: 'Close' }))   // la ✕ de DialogContent; usar el nombre real
    expect(await screen.findByText('Se descartarán los 2 IMEIs escaneados.')).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Descartar' }))
    expect(onCerrar).toHaveBeenCalled()
  })
})
```

Notas para el implementador: el nombre accesible de la ✕ de `DialogContent` (`showCloseButton`) está en `src/shared/ui/dialog.tsx` (suele ser un `sr-only` "Close"; usar el real). El cuerpo del 422 debe tener la forma que lee `extraerMensaje` en `client.ts`. El JSON del error puede diferir: se comprueba que el modal **no** se cierra y que la clave se repite.

- [ ] **Step 2: Ver que falla**, luego **`AsignarTrabajosDialog.tsx`**

```tsx
import { useEffect, useMemo, useReducer, useState } from 'react'
import type { ReparacionResumen } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/shared/ui/dialog'
import { cn } from '@/shared/lib/utils'
import { useTecnicos } from '../../api'
import { crearClavesIdempotencia } from '../../formulario/clavesIdempotencia'
import { useCargaTecnicos } from '../api'
import { useClientesTodos, useGuardarLote } from './api'
import { construirLote } from './lote'
import { PanelPulido } from './PanelPulido'
import { PanelRico } from './PanelRico'
import { useEfectosModal } from './useEfectosModal'
import { contadorPestana, resumenBarra, totalEscaneados } from './estado/derivados'
import { reducir } from './estado/reductor'
import { estadoInicial, type Pestana } from './estado/tipos'

type Props = { tabla: ReparacionResumen[]; onCerrar: () => void; onInteraccion: (abierta: boolean) => void }

const PESTANAS: { p: Pestana; etiqueta: string }[] = [
  { p: 'REPARACION', etiqueta: 'Reparación' }, { p: 'GLASS', etiqueta: 'Glass' }, { p: 'PULIDO', etiqueta: 'Pulido' }]
const CATEGORIA: Record<string, string> = { R: 'Reparación', G: 'Glass', P: 'Pulido' }

/** Modal "Asignar trabajos" (abrirFormularioAsignacion del JavaFX). Nada se escribe hasta "Guardar (N)", salvo el
 *  modelo decidido a mano (D3). La tabla de detrás queda congelada mientras está abierto. */
export function AsignarTrabajosDialog({ tabla, onCerrar, onInteraccion }: Props) {
  const [estado, dispatch] = useReducer(reducir, tabla, (t) =>
    estadoInicial(t.map((r) => ({ idRep: r.idRep, imei: r.imei, idTec: r.idTec, nombreTecnico: r.nombreTecnico }))))
  const { data: tecnicos = [] } = useTecnicos(true)
  const { data: carga } = useCargaTecnicos(true)
  const { data: clientes = [] } = useClientesTodos(true)
  const idsClientes = useMemo(() => new Set(clientes.map((c) => c.idCli)), [clientes])
  useEfectosModal(estado.efectos, dispatch, idsClientes)
  useEffect(() => { onInteraccion(true); return () => onInteraccion(false) }, [onInteraccion])

  const guardarLote = useGuardarLote()
  const [claves] = useState(() => crearClavesIdempotencia())
  const [descartar, setDescartar] = useState(false)
  const { mostrarError, mostrarAviso } = useAlerta()
  const barra = resumenBarra(estado)
  const guardando = guardarLote.isPending
  const total = totalEscaneados(estado)

  const pedirCierre = () => {
    if (guardando) return
    if (total === 0) onCerrar()
    else setDescartar(true)
  }

  const guardar = async () => {
    const cuerpo = construirLote(estado)
    const clave = claves.para('lote', cuerpo)
    try {
      const r = await guardarLote.mutateAsync({ cuerpo, clave })
      claves.hecha('lote')
      onCerrar()
      if (r.conflictos.length > 0)
        mostrarAviso('Aviso', 'Algunas asignaciones no se crearon:\n\n' + r.conflictos
          .map((c) => `• ${c.imei} → ${c.nombreTecnico} (ya asignado · ${CATEGORIA[c.categoria] ?? c.categoria})`).join('\n'))
    } catch (e) {
      if (!esErrorGestionadoGlobalmente(e)) mostrarError(mensajeDeError(e))
    }
  }

  const panel = { estado, dispatch, tecnicos, carga: carga?.pedidos ?? [], clientes }

  return (
    <>
      <Dialog open onOpenChange={(abierto) => { if (!abierto) pedirCierre() }}>
        <DialogContent className="max-h-[calc(100vh-24px)] w-[calc(100vw-32px)] max-w-[980px] overflow-y-auto bg-modal-fondo p-[26px]">
          <DialogTitle className="text-[20px] font-bold text-azul-medio">Asignar trabajos</DialogTitle>
          <DialogDescription className="text-[11px] text-azul-gris">
            Elige el tipo, escanea IMEIs y configúralos. Los técnicos se mantienen entre IMEIs. Se guardan todos al final.
          </DialogDescription>
          <div className="flex" role="group" aria-label="Tipo de trabajo">
            {PESTANAS.map(({ p, etiqueta }, i) => {
              const c = contadorPestana(estado, p)
              const activa = estado.pestana === p
              return (
                <button key={p} type="button" aria-pressed={activa}
                  onClick={() => dispatch({ tipo: 'CAMBIAR_PESTANA', pestana: p })}
                  className={cn('flex items-center gap-1.5 border border-azul-noche px-4 py-1.5 text-[13px] font-bold',
                    i === 0 && 'rounded-l-full', i === PESTANAS.length - 1 && 'rounded-r-full',
                    activa ? 'bg-azul-noche text-crema' : 'bg-white text-azul-noche')}>
                  {etiqueta}
                  {c.total > 0 && (
                    <span className={cn('rounded-[10px] px-[7px] text-[10.5px] font-bold',
                      c.pendientes > 0 ? 'bg-cola-roja-bg text-cola-roja' : 'bg-badge-neutro-bg text-azul-gris')}>{c.total}</span>
                  )}
                </button>
              )
            })}
          </div>
          {estado.avisoPrediccion && (
            <div role="status" className="flex items-center gap-2 text-[11px] text-texto-error">
              No se pudo calcular la glass automática; asígnala a mano.
              <button type="button" aria-label="Cerrar aviso" onClick={() => dispatch({ tipo: 'CERRAR_AVISO_PREDICCION' })}>✕</button>
            </div>
          )}
          {estado.pestana === 'PULIDO' ? <PanelPulido {...panel} /> : <PanelRico {...panel} />}
          <div className="flex items-center gap-3 border-t border-borde-input pt-3.5">
            <span className="text-[12px] font-bold text-azul-gris">{barra.texto}</span>
            <div className="flex-1" />
            <button type="button" disabled={!barra.guardarHabilitado || guardando} onClick={() => void guardar()}
              className="rounded-md bg-azul-medio px-[22px] py-[11px] text-[14px] font-bold text-white disabled:opacity-50">
              {guardando ? 'Guardando…' : `Guardar (${barra.n})`}
            </button>
          </div>
        </DialogContent>
      </Dialog>
      <ConfirmDialog abierto={descartar} titulo="Descartar" descripcion={`Se descartarán los ${total} IMEIs escaneados.`}
        textoAccion="Descartar" onConfirmar={onCerrar} onCancelar={() => setDescartar(false)} />
    </>
  )
}
```

Comprobar en `tokens.css` que `badge-neutro-bg` es `#E8EAF0` (la pastilla gris del JavaFX); si no, añadir `--color-cola-gris-bg: #E8EAF0` y usarlo. Las clases del toggle se copian de la que ya usa `CargaTecnicosDialog` (toggle Pedidos | Total con `aria-pressed`) si difieren de estas: manda la del repo.

- [ ] **Step 3: Botón "Asignar" en la página**

En `AsignacionesPage.tsx`: borrar `TOOLTIP_ASIGNAR` y el `<span title>`; añadir `const [asignando, setAsignando] = useState(false)` y:

```tsx
{!soloLectura && (
  <div className="mb-3 flex flex-wrap items-center gap-3">
    <BotonPrimario onClick={() => setAsignando(true)}>Asignar</BotonPrimario>
  </div>
)}
{asignando && <AsignarTrabajosDialog tabla={data} onCerrar={() => setAsignando(false)} onInteraccion={marcar} />}
```

En `AsignacionesPage.test.tsx`, sustituir la aserción del botón deshabilitado con tooltip (L70-74) por:

```tsx
  it('el botón Asignar abre el modal y congela el refresco', async () => {
    // mismos handlers de técnicos/carga/clientes que el test del modal
    renderConProviders(<AsignacionesPage />, { sesion: SESION_SUPER, ruta: '/reparaciones/asignaciones' })
    await userEvent.click(await screen.findByRole('button', { name: 'Asignar' }))
    expect(await screen.findByRole('heading', { name: 'Asignar trabajos' })).toBeInTheDocument()
  })
```

`admin.test.tsx` no cambia (el ADMIN sigue sin botón).

- [ ] **Step 4: Ver que pasa todo, check y commit**

```bash
npx vitest run src/modules/taller/asignaciones
npm run check
git add src/modules/taller/asignaciones src/shared/styles/tokens.css
git commit -m "feat(asignar): modal Asignar trabajos con guardado por lotes y descarte, y boton Asignar activo"
```

---

## Task 18: Web — smoke Playwright

**Files:**
- Create: `tests/e2e/asignar.spec.ts`

**Interfaces:**
- Consumes: `credenciales(variableUsuario, variableClave)` de `tests/e2e/credenciales.ts`; el patrón de login de `tests/e2e/taller.spec.ts`.

Variables nuevas (en `~/.env.e2e`, fuera del repo): `E2E_IMEI_PRUEBA` (un IMEI **sintético** que ya exista en la BD de pruebas con modelo) y `E2E_TEC_PRUEBA` (nombre del usuario técnico de prueba). Si faltan, el test se salta como hace `credenciales`. **Nunca** "Lleva glass": la glass automática iría a un técnico real.

- [ ] **Step 1: El test**

```ts
import { expect, test } from '@playwright/test'
import { credenciales } from './credenciales'

test('el supertécnico asigna un trabajo desde el modal y lo borra', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  const imei = process.env.E2E_IMEI_PRUEBA
  const tecnico = process.env.E2E_TEC_PRUEBA
  test.skip(!imei || !tecnico, 'Faltan E2E_IMEI_PRUEBA / E2E_TEC_PRUEBA')

  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page).toHaveURL(/\/reparaciones\/historial$/)
  await page.goto('/reparaciones/asignaciones')

  await page.getByRole('button', { name: 'Asignar' }).click()
  const modal = page.getByRole('dialog', { name: 'Asignar trabajos' })
  await modal.getByPlaceholder('Escanea o escribe el IMEI (15 dígitos)...').fill(imei!)
  await expect(modal.getByTestId('imei-en-curso')).toHaveText(imei!)
  await expect(modal.getByRole('combobox', { name: 'Modelo de iPhone' })).not.toHaveValue('')
  await modal.getByRole('checkbox', { name: new RegExp(tecnico!) }).click()
  await modal.getByRole('button', { name: 'Asignar →' }).click()
  await modal.getByRole('button', { name: 'Guardar (1)' }).click()
  await expect(modal).toBeHidden()

  // Limpieza: la fila recién creada, por IMEI y técnico
  try {
    await page.getByPlaceholder('Filtrar por IMEI').fill(imei!)
    const fila = page.getByRole('row').filter({ hasText: imei! }).filter({ hasText: tecnico! }).first()
    await expect(fila).toBeVisible()
  } finally {
    const fila = page.getByRole('row').filter({ hasText: imei! }).filter({ hasText: tecnico! }).first()
    if (await fila.isVisible()) {
      await fila.getByRole('button', { name: /Borrar/ }).click()
      await page.getByRole('button', { name: 'Borrar asignación' }).click()
      await expect(fila).toBeHidden()
    }
  }
})
```

Ajustar el nombre accesible de la papelera al real (buscar en `columnas.tsx` del 3a). `fill` sobre el campo de escaneo entrega los 15 dígitos de golpe: como `onChange` recibe los 15, se añade igual que con el lector.

- [ ] **Step 2: Ejecutar contra producción, en serie**

```bash
set -a; . ~/.env.e2e; set +a
npx playwright test
```

Expected: verde (los 5 del 3a + este). Si el IMEI de prueba ya tenía una asignación del técnico de prueba, el guardado lo informa como conflicto: limpiar a mano y repetir. **Este paso escribe en producción**: pedir OK al usuario antes de lanzarlo, y ejecutarlo después del despliegue del servidor (el endpoint del lote tiene que existir allí).

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/asignar.spec.ts
git commit -m "test(e2e): smoke del modal Asignar trabajos con limpieza"
```

---

## Task 19: Ficha de paridad, versión y verificación final

**Files:**
- Create: `docs/paridad/asignar-trabajos.md` (web)
- Modify: `CHANGELOG.md`, `package.json` (web, versión `0.5.0`)
- Modify: `docs/superpowers/plans/2026-09-22-web-asignar-trabajos.md` (raíz): sección "Ejecución y cierre"

- [ ] **Step 1: La ficha**

Mismo formato que `docs/paridad/asignaciones.md`: título, párrafos de Referencia / Capturas (**solo por nombre**: `asignaciones/{modal-inicial,modal-inicial-abajo,modal-pegado-imeis,modal-cliente-popup,modal-selector-modelo,modal-detalle-completo,modal-lleva-glass,modal-asignados,modal-cola-glass,modal-cola-pulido,modal-imei-invalido,modal-cerrar-sin-guardar,modal-duplicado}.png`, documentación privada fuera del repo) / ejemplos sintéticos; después **"## Diferencias deliberadas"** con las seis de la spec §10 más la del combo de Pulido (Task 16) y la del cliente inactivo pickable en su propia entrada (Task 15); y secciones de `- [ ]` por bloque: cabecera y pestañas, escaneo y pegado, listas roja y verde, detalle, modelo, técnicos y duplicado, cliente, glass automática, pulido, barra y Guardar, guardado y conflictos, cerrar. Una línea por comportamiento, con la captura entre paréntesis.

- [ ] **Step 2: Marcarla contra las capturas**

Recorrer las capturas una a una con la web en local contra producción y marcar. **Toda diferencia nueva se anota y se consulta con el usuario**, no se resuelve sobre la marcha.

- [ ] **Step 3: Capturas de la web lado a lado (antes del tag)**

Tomar las mismas situaciones que las capturas `modal-*` en la web, guardarlas junto a las del JavaFX **fuera del repo** (`paridad-capturas/asignaciones/web-*.png`) y compararlas con el usuario. Lección del 3a: así salen diferencias que ningún test ve.

- [ ] **Step 4: Versión y CHANGELOG**

`package.json`: `"version": "0.5.0"`. `CHANGELOG.md`, entrada nueva arriba, en el formato de la 0.4.0:

```md
## [0.5.0] - 2026-09-XX — Asignar trabajos

- El botón "Asignar" abre el modal de asignación: colas de Reparación, Glass y Pulido, escaneo y pegado de IMEIs, modelo y cliente por IMEI, técnicos que se mantienen entre IMEIs, chasis y "Lleva glass" con la glass automática.
- Todo el lote se guarda de una vez al final; si algo falla no queda nada a medias y reintentar no duplica. Las asignaciones que ya existían se saltan y se avisan.
- Compartidos: campo con autocompletado en línea y troceado del pegado de IMEIs.
- Servidor: predicción de la glass automática y guardado por lotes con clave de idempotencia.
- Diferencias aceptadas respecto al programa de escritorio: `docs/paridad/asignar-trabajos.md`.
```

- [ ] **Step 5: Verificación completa de los tres repos**

```bash
# servidor
cd gestion-reparaciones-servidor && mvn -q test
# web
cd ../gestion-reparaciones-web && npm run check && npm run build
# cliente JavaFX: no se ha tocado; sigue compilando
cd .. && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-cliente && mvn -q test
```

Expected: verde en los tres; el cliente con sus 284 tests intactos. Comprobar además que `api/openapi.json` de la web es idéntico al `target/openapi.json` del servidor de la rama.

- [ ] **Step 6: Commits de cierre**

```bash
cd gestion-reparaciones-web
git add docs/paridad/asignar-trabajos.md CHANGELOG.md package.json package-lock.json
git commit -m "docs(web): ficha de asignar trabajos marcada, CHANGELOG y version 0.5.0"
```

En el raíz, añadir al final de este plan la sección **"Ejecución y cierre"** (qué se desvió, suites, commits), como en el 3a, y commitearla en `main`.

- [ ] **Step 7: Parar y pedir OK al usuario**

**No hacer push, merge, tag ni despliegue.** Presentar: qué se ha hecho, estado de los tres repos, y la lista de pasos que requieren su OK **uno a uno**: push de las dos ramas, merges `--no-ff`, tag `v0.5.0`, gitlinks en el raíz, despliegue en la VDC con **el servidor antes que la web**, smoke de la Task 18 contra producción, y actualizar `Apuntes/plan-futuro.md` (casilla del 3) y la memoria del programa.

---

## Trazabilidad: reglas del inventario del modal → test

| Regla (inventario) | Test |
|---|---|
| §1 un IMEI por cola; varios técnicos por entrada | `colas.test` "repetido…", `lote.test` "una asignación por verde × técnico" |
| §2 cabecera, subtítulo corregido, pestañas, barra | `AsignarTrabajosDialog.test` "abre con su cabecera…" |
| §2 cambiar de pestaña vacía el detalle y conserva colas | `colas.test` "cambiar de pestaña…" |
| §3 a los 15 sin Enter; solo dígitos; incompleto + Enter nada | `piezas.test` CampoEscaneo |
| §3 repetido en la cola activa / mismo IMEI en otra cola | `colas.test`, `PanelRico.test` |
| §3 pegado: corrupto, añadidos, repetidos, sin cargar | `colas.test`, `PanelRico.test` "pegado…" |
| §4 orden por seq desc; pastillas de modelo; "auto"; ✕ | `derivados.test` filasCola, `piezas.test` FilaCola |
| §4 quitar reparación se lleva la glass; quitar glass desmarca | `colas.test` "quitar…" |
| §5 pegajosos por cola; comentario y chasis no se arrastran | `colas.test` "cargar…", "técnicos pegajosos" |
| §5 Asignar exige modelo y técnico; "Guardar cambios" en verde | `derivados.test` asignarHabilitado, `PanelRico.test` |
| §5 asignar: siguiente roja más nueva / editar verde vacía | `colas.test` "asignar…" |
| §6 autocompletado (contiene, Enter con guardas, salir) | `CampoAutocompletar.test` |
| §6 lookup: modelo vivo sin pisar manual; no encontrado; BD manda cliente | `colas.test` "lookup", `api.test` |
| §6 decisión manual: propaga, recuerda, guarda al momento (D3) | `colas.test` "decidir…", `api.test` guardarModelo |
| §7 carga Pedidos en la lista; pastilla "glass" en cola Glass | `piezas.test` ListaTecnicos |
| §7 duplicado deshabilitado al seleccionar + "N asignados" | `derivados.test`, `piezas.test`, `PanelRico.test` |
| §8 invariante casilla ⇔ glass (escaneo, marcar, desmarcar) | `glass.test` |
| §8 glass creada: modelo/cliente de la reparación, sin comentario | `glass.test` |
| §8 predicción: auto se recalcula, manual intacta, sin candidato roja | `glass.test`, `PrediccionGlassTest` (16) |
| §8 respuestas viejas o de glass quitada se ignoran | `glass.test` |
| §9 pastillas del toggle; progreso; Guardar habilitado | `derivados.test`, `AsignarTrabajosDialog.test` |
| §10 pulido: técnico de arriba, sin técnico en rojo, pegado selecciona, sin bloqueo (D4) | `pulido.test`, `PanelPulido.test` |
| §11 lote atómico, conflictos saltados y avisados, urgente false | `AsignacionLoteServiceTest`, `AsignacionControllerTest`, `AsignarTrabajosDialog.test` |
| §11 reintento con la misma clave no duplica | `AsignacionControllerTest`, `AsignarTrabajosDialog.test` |
| §11 lookup de pulido fuera de la transacción | `AsignacionControllerTest` |
| §12 precedencia manual → BD → pegajoso; propagación a 3 colas | `cliente.test` |
| §13 cerrar: directo sin entradas, "Descartar" con total | `AsignarTrabajosDialog.test` |

---

## Autorrevisión del plan

**Cobertura de la spec.** §4.1 → Tasks 1-2. §4.2 → Tasks 3-4. §4.3 → Task 12 (Step 1). §5 estructura → Tasks 5-17 (se añaden `base.ts`, `CampoEscaneo.tsx`, `MensajeEscaneo.tsx` y `useEfectosModal.ts`, que la spec no nombraba). §6 comportamiento → Tasks 7-17. §7 guardado → Tasks 11, 12, 17. §8 errores → Tasks 8, 12, 17. §9 tests → todas + Task 18. §10 diferencias → Task 19. §11 cierre → Task 19. D1 → Tasks 3-4. D2 → Tasks 2, 8, 12. D3 → Tasks 9, 12. D4 → Tasks 10, 16. D5 → Tasks 7-11. D6 → Tasks 14-17.

**Sin marcadores.** No hay "TBD" ni pasos sin contenido. Donde el plan dice "usar el nombre real" (esquemas de springdoc, nombre accesible de la ✕ del diálogo, firma de las fábricas de test, papelera de la tabla), es una comprobación con ruta concreta, no un hueco.

**Consistencia de nombres.** `reducir`, `estadoInicial`, `EstadoModal`, `Entrada`, `FilaPulido`, `RefCliente`, `Efecto`, `buscar`, `actualizar`, `conEfecto`, `tecnicosOcupados`, `glassAbiertaBd`, `verdesParaPrediccion`, `resumenBarra`, `construirLote`, `useEfectosModal`, `useGuardarLote`, `useClientesTodos`, `pedirLookup`, `pedirClienteBd`, `guardarModelo`, `pedirPrediccion` se usan igual en todas las tareas. En el servidor, `LoteAsignaciones.*`, `AsignacionLoteService.guardar(Peticion, Integer, int)` y `PrediccionGlassRespuesta` son consistentes entre las Tasks 2-4.

**Riesgo conocido.** Los nombres de esquema generados (Task 5) y algunos detalles de componentes existentes (`ComboNavy`, `dialog.tsx`, fábricas de test) se leen del código antes de escribir; si no coinciden, manda el código existente.


---

## Ejecución y cierre

**Entregado el 2026-09-24.** Servidor `main` `54c60c6` y web `main` `08dadad` (tag `v0.5.0`), desplegados en producción con el servidor antes que la web; el bundle servido es byte a byte el del build local de `main`. Suites: servidor 271, web 1017, cliente JavaFX sin cambios. Smoke e2e `asignar.spec.ts` en verde contra producción (crea una reparación del IMEI de prueba y borra solo el id que devuelve el lote). Ficha de paridad `docs/paridad/asignar-trabajos.md` con todas sus casillas marcadas tras comparar las 13 capturas de la web con las del JavaFX.

Las diecinueve tareas se ejecutaron con un implementador y una revisión por tarea, y una revisión final de cada rama (servidor y web), ambas cerradas en "Ready to merge" tras un lote de arreglos. Como en el 3a, el código existente y el contrato real mandaron sobre el código de ejemplo de este plan. Los ajustes, todos decididos con el usuario o salidos de las revisiones:

- **Servidor.** La carga de asignaciones abiertas y cerradas de hoy se extrajo a `CargaAsignacionesService`, compartida por `carga-tecnicos` y la predicción, en vez de copiarla. El test de rollback del lote va contra el proxy `@Transactional` real con un `DataSource` de mentira (no hay BD de tests): verifica `rollback()` y ningún `commit()`, y se pone en rojo si se quita la anotación. `POST /api/asignaciones/lote` responde 422 a elementos nulos, teléfonos sin IMEI válido y categorías desconocidas; `POST /api/glass/prediccion`, a un IMEI inválido. `OpenApiContractTest` incluye el lote en la lista blanca de `Idempotency-Key`.
- **Web: reutilización.** `useClientes`/`CLAVE_CLIENTES` pasaron de `gestion/clientes` a `src/shared/api/clientes.ts` (el lint prohíbe importar entre módulos y su propio mensaje manda mover lo compartido a `shared`); no existe `useClientesTodos`. Los ayudantes del selector de cliente viven en `modal/opcionesCliente.ts`, compartidos por `DetalleEntrada` y `PanelPulido`. Tres tokens del plan ya existían con otro nombre (`fondo-vista`, `recibido-text`, `seleccion-suave`).
- **Web: paridad decidida durante la ejecución.** Los técnicos se guardan en el orden de la lista del modal, como el JavaFX (`MARCAR_TECNICO` lleva `orden`). Una entrada verde sin modelo manda el modelo vivo del IMEI y "Guardar" se bloquea si no hay ninguno (el JavaFX mandaba `null`; el lote nuevo lo rechazaría entero). Las filas de pulido sin técnico no mandan teléfono, como el JavaFX. El texto guía del campo Modelo se calcula por entrada (diferencia aceptada).
- **Web: hallazgos de las revisiones.** `CampoAutocompletar` sincroniza el texto con el patrón de estado derivado en el render (sin `useEffect` + `setState`, que rompía el lint) y no borra lo tecleado cuando el padre anula el valor. Los efectos del modal esperan a que carguen los clientes, para no perder el cliente de la BD (D6). El smoke espera a que la tabla esté cargada y borra solo el `idRep` que devuelve el lote. La lista roja/verde con la clave de idempotencia reutilizada en el reintento y la congelación del refresco tienen tests que fallan si se quita la pieza.
- **Comparación de capturas.** Salieron dos diferencias nuevas, aceptadas: el campo Cliente deshabilitado se queda vacío cuando no hay entrada cargada (el JavaFX muestra el último cliente en gris) y los combos de técnico de Pulido no llenan el ancho. Las tres que traía la ficha (texto del combo de Pulido, borde `#D4D8DE`, título "Aviso") también se aceptaron.

Backlog anotado en `Apuntes/plan-futuro.md` (menores de las revisiones: accesibilidad de las filas clicables y del autocompletado, teléfonos repetidos dentro de un lote, lookups de pulido en serie, tests de borde).

Siguiente: sub-proyecto 4 (Inventario y el panel de Revisión), con la red y la seguridad (SP7) después del código del 3, según decidió el usuario.
