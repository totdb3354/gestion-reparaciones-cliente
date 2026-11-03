# 0.9.24 — La descripción del móvil dice si es eSIM · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que la descripción del móvil diga «iPhone 14 eSIM 256GB Azul» cuando NSYS dice que es eSIM, en las listas del taller,
en las de NSYS (ficha, buscador, «Por pasar a NSYS», «Pegar varios IMEIs») y en la columna «Modelo» de los CSV del taller.

**Architecture:**
- **Servidor** (sin base de datos): las respuestas que ya llevan la capacidad del espejo ganan `esEsim` (booleano, al final), que
  sale del mismo `MovilNsys` (`esEsim()`):
  - `ReparacionResumen` (las seis listas del taller), que rellena `nsys.DescripcionMovil` con la lectura que ya hace;
  - `FichaMovil`, `MovilBuscado` y `MovilComprobado` (`nsys.StockTelefonosController`);
  - `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys` y `FilaSinGradoNsys` (`nsys.TarjetasNsys`).
- **Web:**
  - en `src/shared/lib/telefonoNsys.ts`, `conEsim(nombre, esEsim)` pone «eSIM» detrás del nombre del modelo y
    `nombreMovilNsys(m)` lo hace con el nombre de un móvil del espejo; las cinco copias de NSYS lo usan;
  - en el taller, `nombreTrabajo(t)` (modelo del taller + eSIM) sostiene `descripcionTrabajo(t)` y la columna «Modelo» de los
    cuatro CSV; `movilDe` dice si el IMEI es eSIM;
  - E2E de solo lectura en preprod.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB. Pruebas con JUnit 5, Mockito y MockMvc (sin base de datos).
- **Web:** React 19 + TypeScript, TanStack Query/Table, openapi-fetch. Pruebas con Vitest, Testing Library, user-event, MSW y
  Playwright (E2E).

**Spec:** `docs/superpowers/specs/2026-11-03-v0924-esim-en-la-descripcion-design.md` (raíz). Se apoya en la 0.9.21
(`2026-10-22-v0921-descripcion-completa-design.md` y su plan).

## Global Constraints

**Versión, código de partida y base de datos**
- **0.9.24, servidor + web, sin base de datos** (ni migración ni cambios en `crear_bd.sql`).
- El servidor no tiene CHANGELOG ni versión en el `pom.xml` (`1.0-SNAPSHOT`): su 0.9.24 es el tag `v0.9.24` y la entrada del
  CHANGELOG de la web, que dice «servidor y web». La web sube `package.json` y `package-lock.json` a `0.9.24` (Task 6).
- **Compatibilidad en los dos sentidos:** la web vieja ignora `esEsim`; la web nueva con un servidor viejo lo ve `undefined`, lo
  lee como «no es eSIM» y enseña lo de hoy, sin error.

**Ramas, worktrees y registro**
- Rama **`dev_v0924_esim`** en los dos repos, desde `origin/main` **en el momento de crearla** (`git fetch` antes y mirar el
  commit; no dar por hecho ningún hash), en worktrees:
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0924-esim` (lo crea la Task 1). El 03/11 `origin/main` era `71f1ee1`: la
    0.9.22.2 y el borrado de las 24 rutas retiradas (sp7b tarea 27), de otras sesiones.
  - Web: `/c/Users/dev/Documents/wt/web-0924-esim`, con `npm ci` dentro (lo crea la Task 3). El 03/11 `origin/main` era
    `206fa5b` (0.9.22.1); la 0.9.23 (`dev_v0923_aviso_nsys`, otra sesión) puede entrar antes en `main`.
  - **Los clones normales y los worktrees de otras ramas no se tocan** (los usan otras sesiones). Las rutas
    `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` del plan son las de los worktrees; `docs/…` sin prefijo es
    la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0924-esim/progress.md`** (raíz; `.superpowers/` está en el
  `.gitignore`).

**Commits, merge y datos de prueba**
- Commits en español **sin tildes**, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- Merge `--no-ff`, push, tags, gitlinks, preproducción y producción: **fuera de este plan** (los hace el controlador, con el OK
  del usuario; ver «Después de este plan»).
- **Repos públicos:** datos de prueba sintéticos («Técnico 2», IMEI `35000000000xxxx`, el iPhone 13 128GB de `TallerDePrueba`).
  La E2E no apunta ningún IMEI ni la dirección de preprod en el código.

**Reglas** (spec §2-§3)
- **Texto:** `<nombre del modelo> eSIM <capacidad> <color>` si el móvil es eSIM; si no, lo de la 0.9.21. Sin nombre (vacío) o con
  «Sin modelo en el maestro», **sin** «eSIM».
- **El dato:** `Nsys_movil.ES_ESIM` de la fila más reciente del IMEI (la misma de la capacidad). Fuera del espejo, si la lectura
  falla y en las rutas que no lo rellenan: `false`. El taller no lo cambia; no se mira `Telefono.ES_ESIM`.
- **IMEIs:** el móvil de un IMEI (`movilDe`) es eSIM si lo es en alguno de sus trabajos.
- **CSV** (`reparaciones_pendientes`, `pulidos_pendientes`, `historial_pulidos`, `agrupado_resumen`): la columna «Modelo» dice
  «iPhone 14 eSIM»; las columnas no cambian.
- **No cambian:** la tabla del stock (ya lleva su marca «eSIM»), la lista de modelos del stock y su buscador, «No está en NSYS»,
  el producto que hay que poner en NSYS, las plantillas Excel, `FilaPorPasar` (ruta de la 0.9.21 que la web ya no usa) y
  `MovilStock` (ya lo lleva).
- **Quién:** todos los roles, como la descripción de la 0.9.21. Sin rutas ni permisos nuevos.

**Comandos**
- **Servidor** (Git Bash, en `/c/Users/dev/Documents/wt/servidor-0924-esim`):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest='Clase1,Clase2'`). Con `-q`, Maven no escribe nada si todo pasa: código de
  salida 0 y sin `FAIL` ni `ERROR`. `OpenApiContractTest` levanta el contexto entero y deja `target/openapi.json` para la web.
- **Web** (en `/c/Users/dev/Documents/wt/web-0924-esim`): `npx vitest run <rutas>`, `npx tsc -b`, `npm run lint` y, al cerrar,
  `npm run check`. **Un módulo no importa de otro**: lo común va a `src/shared`.
- **Inestabilidad conocida de la web:** con la suite entera, algún test puede pasar de los 5 s («orden de llamadas…» de
  `FormularioReparacion`, `NuevoPedidoDialog`, `StockPage`, `HistorialPage`); solos pasan. Si pasa, se repite ese fichero solo y
  se apunta en el registro; no se tocan.

## Ajustes a la spec (se suben a la spec, como §7, en el commit del plan)

1. **El servidor no tiene CHANGELOG ni versión en el `pom.xml`:** su 0.9.24 es el tag y la entrada del CHANGELOG de la web; el
   contrato se apunta en `docs/api_contract.md` del servidor (spec §5).
2. **`main` del servidor ya no es `d714d3f`:** el 03/11 es `71f1ee1`, que suma a la 0.9.22.2 el borrado de las 24 rutas retiradas
   (sp7b tarea 27, otra sesión). La rama sale de `origin/main` al crearla y la subida del servidor lleva las tres cosas. El
   contrato de la web, regenerado aquí, pierde además esas rutas si nadie lo ha refrescado antes.
3. **`esEsim` va al final** de cada respuesta, como la capacidad y el color de la 0.9.21. Los constructores cortos de
   `FilaColorNsys`, `FilaGradoNsys` y `FilaRevisarNsys` («un móvil en la empresa», sin uso hoy) pasan `false`.
4. **Nombres en la web:** `conEsim(nombre, esEsim)`, `nombreMovilNsys(m)` y la constante `SIN_MODELO_MAESTRO` en
   `src/shared/lib/telefonoNsys.ts`; `nombreTrabajo(t)` en `src/modules/taller/lib/modelos.ts`.
5. **Fábricas de prueba:** `esEsim: false` por defecto, salvo `filaGrado()` y `filaRevisar()`, que ya describen un móvil eSIM
   («Apple iPhone 14 eSIM 256GB Midnight», chasis `chai14yellowesim`): `true`.
6. **E2E** `tests/e2e/esim-descripcion.spec.ts`: se salta con el motivo si el historial de preprod no tiene un trabajo de un
   móvil eSIM y otro sin eSIM, los dos con modelo y capacidad de NSYS.

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`; clases en `src/main/java/com/reparaciones/servidor/` y pruebas en
`src/test/java/com/reparaciones/servidor/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `model/ReparacionResumen.java` | `esEsim` al final | 1 |
| `nsys/DescripcionMovil.java` | Rellena `esEsim` con la capacidad y el color | 1 |
| `nsys/TallerDePrueba.java` (prueba) | `esim(imei, color, grade)`: el iPhone 13 128GB solo eSIM | 1 |
| `nsys/DescripcionMovilTest.java`, `nsys/DescripcionMovilRutasTest.java`, `OpenApiContractTest.java` | Pruebas | 1, 2 |
| `nsys/StockTelefonosController.java` + `nsys/StockTelefonosControllerTest.java` | Ficha, buscador y «Comprobar» | 2 |
| `nsys/{FilaColorNsys,FilaGradoNsys,FilaRevisarNsys,FilaSinGradoNsys}.java`, `nsys/TarjetasNsys.java` + `nsys/TarjetasNsysTest.java` | Las cuatro tarjetas | 2 |
| `docs/api_contract.md` | Contrato | 2 |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/modules/taller/test/fabrica.ts`, `src/modules/telefonos/test/fabrica.ts` | Contrato, tipos y fábricas | 3 |
| `src/shared/lib/telefonoNsys.ts` (+ test) | `SIN_MODELO_MAESTRO`, `conEsim`, `nombreMovilNsys` | 3 |
| `src/modules/taller/imeis/{FichaMovil.tsx,ImeisPage.tsx}`, `src/modules/telefonos/stock/{BuscadorImei.tsx,porPasar.ts,varios.ts}` (+ tests) | Las cinco copias de NSYS | 3 |
| `src/modules/taller/lib/{modelos.ts,grupoImei.ts}` (+ tests) | `nombreTrabajo`, `descripcionTrabajo`, `movilDe` | 4 |
| `src/modules/taller/asignaciones/csv.ts`, `pendientes/csvPulidos.ts`, `historial/csvTrabajos.ts`, `imeis/csvImeis.ts` (+ tests; `csvImeis.test.ts` nuevo) | «Modelo» de los CSV | 4 |
| Tests de pantallas: `asignaciones/columnas.test.tsx`, `pendientes/textoCelda.test.ts`, `historial/columnasTrabajo.test.ts`, `imeis/ImeiDetallePage.test.tsx` | Las listas del taller | 4 |
| `tests/e2e/esim-descripcion.spec.ts` (nuevo) | E2E de solo lectura | 5 |
| `package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/{asignaciones,pendientes,historial,imeis}.md` | Versión y documentación | 6 |

**Raíz y apuntes** (fuera de los worktrees): `docs/novedades/NOVEDADES-v0.9.24.md` (nuevo) y
`C:/Users/dev/Documents/Apuntes/plan-futuro.md` (Task 6).

---

### Task 1: Servidor — `esEsim` en `ReparacionResumen` y `DescripcionMovil`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/DescripcionMovil.java`
- Modify (prueba): `src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java`
- Test: `src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilTest.java`,
  `src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilRutasTest.java`,
  `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `MovilNsys.esEsim()` (ya existe).
- Produces:
  - `ReparacionResumen.isEsEsim()` / `setEsEsim(boolean)`; en JSON y en el contrato, `esEsim` (no nulo, required), el último
    campo, detrás de `capacidad` y `color`.
  - `TallerDePrueba.esim(String imei, String color, String grade): MovilNsys` (prueba): como `movil(…)` pero con
    `esEsim = true` y el producto «Apple iPhone 13 eSIM 128GB Blue». La usa también la Task 2.

- [x] **Step 1: Worktree del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git log --oneline -3 origin/main
git worktree add -b dev_v0924_esim /c/Users/dev/Documents/wt/servidor-0924-esim origin/main
```

Expected: el worktree creado sobre `origin/main` (el 03/11, `71f1ee1`). Apunta el commit en el registro
(`.superpowers/sdd/v0924-esim/progress.md`, raíz; créalo si no existe). Línea base: `mvn -q test` en el worktree, en verde (si
algo falla ya en `main`, apuntarlo y avisar).

- [x] **Step 2: El móvil eSIM de prueba**

En `src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java`, justo después del método `movil(String imei, String
color, String grade)` (antes del comentario «/** Un móvil cuyo producto no está en el maestro…»), añade:

```java
    /** Como {@link #movil}, solo eSIM (0.9.24): NSYS lo escribe en el producto, «Apple iPhone 13 eSIM 128GB Blue». */
    static MovilNsys esim(String imei, String color, String grade) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 eSIM 128GB Blue", grade, "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13esim128" + color + grade, "13", "13", "128GB", color, grade, true, "Listos",
                "Listos", EstadoMovil.STOCK, HOY.minusDays(20), HOY.minusDays(1));
    }
```

- [x] **Step 3: Las pruebas que fallan**

En `DescripcionMovilTest.java`, al final de la clase (antes de la última `}`), añade:

```java
    /** 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.3): la eSIM sale de la misma fila que la capacidad. */
    @Test void unMovilEsimLlevaLaEsimYLosDemasNo() {
        when(nsys.porImeis(any())).thenReturn(List.of(esim(IMEI, "Azul", "B"), movil(OTRO, "Negro", "A")));
        List<ReparacionResumen> filas = List.of(fila("A20261103_1", IMEI), fila("A20261103_2", OTRO),
                fila("A20261103_3", DE_CLIENTE));
        descripcion.rellenar(filas);
        assertTrue(filas.get(0).isEsEsim(), "NSYS escribe eSIM en el producto");
        assertEquals("128GB", filas.get(0).getCapacidad());
        assertFalse(filas.get(1).isEsEsim(), "SIM física");
        assertFalse(filas.get(2).isEsEsim(), "fuera del espejo, como la capacidad");
    }

    @Test void siFallaElEspejoTampocoLlevaLaEsim() {
        when(nsys.porImeis(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        List<ReparacionResumen> filas = List.of(fila("A20261103_1", IMEI));
        descripcion.rellenar(filas);
        assertFalse(filas.get(0).isEsEsim());
    }
```

(`esim` y `movil` llegan por el `import static com.reparaciones.servidor.nsys.TallerDePrueba.*` que ya tiene el fichero.)

En `DescripcionMovilRutasTest.java`:
- debajo de `import static com.reparaciones.servidor.nsys.TallerDePrueba.movil;` añade
  `import static com.reparaciones.servidor.nsys.TallerDePrueba.esim;`;
- después del test `elTecnicoRecibeLaCapacidadYElColorQueValeEnLasSeisListas`, añade:

```java
    /** 0.9.24: las seis listas dicen si el móvil es eSIM, también para el técnico; un cliente externo, no. */
    @Test void elTecnicoRecibeLaEsimEnLasSeisListas() throws Exception {
        when(nsys.porImeis(any())).thenReturn(List.of(esim(IMEI, "Azul", "B")));
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).header("Authorization", tecnico()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].esEsim").value(true))
                    .andExpect(jsonPath("$[0].capacidad").value("128GB"))
                    .andExpect(jsonPath("$[1].esEsim").value(false));
        }
    }
```

En `OpenApiContractTest.java`, sustituye

```java
        // 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2.3): capacidad y color de NSYS, nullables y al final.
        assertNullable(esquemas, "ReparacionResumen", "capacidad", "color");
        assertEquals(List.of("capacidad", "color"), propiedades.subList(propiedades.size() - 2, propiedades.size()),
                "capacidad y color van al final de ReparacionResumen");
```

por

```java
        // 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2.3): capacidad y color de NSYS, nullables y al final.
        assertNullable(esquemas, "ReparacionResumen", "capacidad", "color");
        // 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.3): detrás, esEsim, no nullable.
        assertNoNullable(esquemas, "ReparacionResumen", "esEsim");
        assertEquals(List.of("capacidad", "color", "esEsim"), propiedades.subList(propiedades.size() - 3, propiedades.size()),
                "capacidad, color y esEsim van al final de ReparacionResumen");
```

- [x] **Step 4: Comprobar que fallan**

Run: `mvn -q test -Dtest='DescripcionMovilTest,DescripcionMovilRutasTest,OpenApiContractTest'`
Expected: FAIL al compilar las pruebas: `cannot find symbol … isEsEsim()` en `DescripcionMovilTest`.

- [x] **Step 5: El campo y el relleno**

En `model/ReparacionResumen.java`, sustituye

```java
    @Schema(nullable = true) private String        capacidad;
    @Schema(nullable = true) private String        color;

    public ReparacionResumen() {}
```

por

```java
    @Schema(nullable = true) private String        capacidad;
    @Schema(nullable = true) private String        color;
    // 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.3): si NSYS escribe «eSIM» en el producto (Nsys_movil.ES_ESIM),
    // de la misma fila que la capacidad. false fuera del espejo, si la lectura falla y en las demás rutas. Al final del contrato.
    private boolean       esEsim;

    public ReparacionResumen() {}
```

y, al final de la clase, sustituye

```java
    public String        getColor()                                { return color; }
    public void          setColor(String v)                        { this.color = v; }
}
```

por

```java
    public String        getColor()                                { return color; }
    public void          setColor(String v)                        { this.color = v; }
    public boolean       isEsEsim()                                { return esEsim; }
    public void          setEsEsim(boolean v)                      { this.esEsim = v; }
}
```

En `nsys/DescripcionMovil.java`:
- sustituye

```java
    /** Lo de NSYS que lleva una fila. */
    private record DelEspejo(String capacidad, String color) {}
```

  por

```java
    /** Lo de NSYS que lleva una fila (la eSIM, desde la 0.9.24). */
    private record DelEspejo(String capacidad, String color, boolean esEsim) {}
```

- sustituye

```java
            r.setCapacidad(m.capacidad());
            r.setColor(m.color());
```

  por

```java
            r.setCapacidad(m.capacidad());
            r.setColor(m.color());
            r.setEsEsim(m.esEsim());
```

- sustituye

```java
        masReciente.forEach((clave, m) -> moviles.put(clave, new DelEspejo(m.capacidad(), color.apply(m))));
```

  por

```java
        masReciente.forEach((clave, m) -> moviles.put(clave, new DelEspejo(m.capacidad(), color.apply(m), m.esEsim())));
```

- [x] **Step 6: Comprobar que pasan**

Run: `mvn -q test -Dtest='DescripcionMovilTest,DescripcionMovilRutasTest,OpenApiContractTest'`
Expected: PASS (código de salida 0, sin `FAIL` ni `ERROR`).

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java \
        src/main/java/com/reparaciones/servidor/nsys/DescripcionMovil.java \
        src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java \
        src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilTest.java \
        src/test/java/com/reparaciones/servidor/nsys/DescripcionMovilRutasTest.java \
        src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: las listas del taller dicen si el movil es eSIM, de la misma fila del espejo que la capacidad (0.9.24)"
```

---

### Task 2: Servidor — `esEsim` en la ficha, el buscador, «Comprobar» y las cuatro tarjetas

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java`
- Modify: `…/nsys/FilaColorNsys.java`, `…/nsys/FilaGradoNsys.java`, `…/nsys/FilaRevisarNsys.java`, `…/nsys/FilaSinGradoNsys.java`,
  `…/nsys/TarjetasNsys.java`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md`
- Test: `src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java`,
  `src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java`, `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Consumes: `TallerDePrueba.esim(…)` de la Task 1; `MovilNsys.esEsim()`.
- Produces: `esEsim` (booleano, no nulo, required, el último campo) en los esquemas `StockTelefonosFichaMovil`,
  `StockTelefonosMovilBuscado`, `StockTelefonosMovilComprobado`, `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys` y
  `FilaSinGradoNsys`; en Java, el accesor `esEsim()` de los cuatro records `Fila…Nsys`. `target/openapi.json` con todo, para la
  Task 3.

- [x] **Step 1: Las pruebas que fallan**

En `StockTelefonosControllerTest.java`, al final de la clase (antes de la última `}`), añade:

```java
    /** 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.3): la ficha, el buscador y «Pegar varios IMEIs» dicen si NSYS
     *  escribe «eSIM» en el producto; un IMEI que no está, no. */
    @Test void laFichaElBuscadorYComprobarDicenSiEsEsim() {
        String otro = "350000000000002", fuera = "350000000000009";
        when(dao.porImei(IMEI)).thenReturn(List.of(TallerDePrueba.esim(IMEI, "Azul", "A")));
        assertTrue(json(api.ficha(IMEI)).get("esEsim").asBoolean());
        when(dao.buscar("3500", 10)).thenReturn(List.of(TallerDePrueba.esim(IMEI, "Azul", "A"), TallerDePrueba.movil(otro, "Azul", "A")));
        JsonNode buscados = json(api.buscar("3500"));
        assertTrue(buscados.get(0).get("esEsim").asBoolean());
        assertFalse(buscados.get(1).get("esEsim").asBoolean());
        when(dao.porImeis(List.of(IMEI, otro, fuera)))
                .thenReturn(List.of(TallerDePrueba.esim(IMEI, "Azul", "A"), TallerDePrueba.movil(otro, "Azul", "A")));
        JsonNode comprobados = json(api.comprobar(pedir(IMEI, otro, fuera)));
        assertTrue(comprobados.get(0).get("esEsim").asBoolean());
        assertFalse(comprobados.get(1).get("esEsim").asBoolean());
        assertFalse(comprobados.get(2).get("encontrado").asBoolean());
        assertFalse(comprobados.get(2).get("esEsim").asBoolean(), "fuera del espejo, no");
    }
```

(`IMEI` es la constante `"350000000000001"` declarada a mitad del fichero; `pedir(…)` y `json(…)` ya existen. Los
`tallerDao.vigentes(…)`, `dao.eventos(…)` y `dao.trabajosAbiertos(…)` sin preparar devuelven colecciones vacías: sin nada del
taller.)

En `TarjetasNsysTest.java`, al final de la clase (antes de la última `}`), añade:

```java
    /** 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.3): las cuatro tarjetas dicen si NSYS escribe «eSIM» en el
     *  producto; un aviso sin móvil en el espejo, no. */
    @Test void lasCuatroTarjetasDicenSiEsEsim() {
        TallerNsys.MovilPorPasar p = porPasar(esim(IMEI, "Azul", "B"), vale("Rojo", "A", "Azul", "B"),
                new ProductoNsys.Poner("Apple iPhone 13 eSIM 128GB (PRODUCT)RED", false),
                vigente(1, IMEI, COLOR, "Rojo", "Azul"), vigente(2, IMEI, GRADO, "A", "B"));
        assertTrue(TarjetasNsys.color(List.of(p), Map.of()).get(0).esEsim());
        assertTrue(TarjetasNsys.grado(List.of(p), Map.of()).get(0).esEsim());
        TallerNsys.MovilPorPasar sim = porPasar(movil(OTRO, "Azul", "B"), vale("Rojo", "B", "Azul", null),
                new ProductoNsys.Poner("Apple iPhone 13 128GB (PRODUCT)RED", false), vigente(3, OTRO, COLOR, "Rojo", "Azul"));
        assertFalse(TarjetasNsys.color(List.of(sim), Map.of()).get(0).esEsim());
        AvisoTaller enEspejo = new AvisoTaller(1, IMEI, "R20261103_1", AvisoTaller.Tipo.SIN_COLOR, null, COLOR,
                "chai13yellowesim", null, "chasis chai13yellowesim nuevo", 8, "Técnico 2", AYER);
        AvisoTaller fuera = new AvisoTaller(2, "350000000000009", "R20261103_2", AvisoTaller.Tipo.SIN_COLOR, null, COLOR,
                "chai13blue", null, "chasis chai13blue nuevo", 8, "Técnico 2", AYER);
        List<FilaRevisarNsys> revisar = TarjetasNsys.revisar(List.of(enEspejo, fuera), Map.of(IMEI, esim(IMEI, "Azul", "B")));
        assertEquals(List.of(true, false), revisar.stream().map(FilaRevisarNsys::esEsim).toList());
        List<FilaSinGradoNsys> sinGrado = TarjetasNsys.sinGrado(
                Map.of(IMEI, List.of(trabajo(IMEI, "R20261103_3", LocalDateTime.of(2026, 10, 27, 9, 0)))),
                Map.of(IMEI, esim(IMEI, "Azul", "n/a")), Map.of(), Map.of(IMEI, VueltaNsys.EN_EMPRESA), Map.of());
        assertTrue(sinGrado.get(0).esEsim());
    }
```

(`porPasar`, `vale` y `trabajo` son los ayudantes privados del fichero; `esim`, `movil` y `vigente` llegan por
`import static …TallerDePrueba.*`.)

En `OpenApiContractTest.java`, al final del test que termina con la línea
`assertTrue(esquemas.path("StockTelefonosFichaMovil").path("properties").has("ponerCompruebalo"));` (justo después de esa
línea, antes de la `}` que cierra el test), añade:

```java
        // 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.3): esEsim, no nullable, en lo que lleva la descripción.
        for (String esquema : List.of("StockTelefonosFichaMovil", "StockTelefonosMovilBuscado", "StockTelefonosMovilComprobado",
                "FilaColorNsys", "FilaGradoNsys", "FilaRevisarNsys", "FilaSinGradoNsys"))
            assertNoNullable(esquemas, esquema, "esEsim");
```

- [x] **Step 2: Comprobar que fallan**

Run: `mvn -q test -Dtest='StockTelefonosControllerTest,TarjetasNsysTest,OpenApiContractTest'`
Expected: FAIL al compilar las pruebas: `cannot find symbol … esEsim()` en `TarjetasNsysTest` (los records aún no lo tienen).

- [x] **Step 3: Los records de `StockTelefonosController`**

En `nsys/StockTelefonosController.java`:
- sustituye

```java
    private record MovilBuscado(String imeiSn, @Schema(nullable = true) String modelo,
                                @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                @Schema(nullable = true) String color, String estado,
                                @Schema(nullable = true) String ubicacion, @Schema(nullable = true) LocalDate fechaVenta,
                                @Schema(nullable = true) String colorNsys) {}
```

  por

```java
    /** {@code esEsim} (0.9.24): NSYS escribe «eSIM» en el producto, como en el stock, la ficha y «Pegar varios IMEIs». */
    private record MovilBuscado(String imeiSn, @Schema(nullable = true) String modelo,
                                @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                                @Schema(nullable = true) String color, String estado,
                                @Schema(nullable = true) String ubicacion, @Schema(nullable = true) LocalDate fechaVenta,
                                @Schema(nullable = true) String colorNsys, boolean esEsim) {}
```

- sustituye

```java
     *  {@code ponerCompruebalo} (0.9.22): el producto sale del nombre del maestro tal cual. */
```

  por

```java
     *  {@code ponerCompruebalo} (0.9.22): el producto sale del nombre del maestro tal cual. {@code esEsim} (0.9.24): NSYS
     *  escribe «eSIM» en el producto. */
```

- sustituye

```java
                              @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado,
                              boolean ponerCompruebalo) {}
```

  por

```java
                              @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado,
                              boolean ponerCompruebalo, boolean esEsim) {}
```

- sustituye

```java
                                   List<TrabajoAbierto> trabajos, @Schema(nullable = true) String colorNsys,
                                   @Schema(nullable = true) String gradoNsys) {}
```

  por

```java
                                   List<TrabajoAbierto> trabajos, @Schema(nullable = true) String colorNsys,
                                   @Schema(nullable = true) String gradoNsys, boolean esEsim) {}
```

Y donde se construyen:
- en `buscar`, sustituye `ubicacionVisible(m), m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null, v.colorNsys());` por
  `ubicacionVisible(m), m.estado() == EstadoMovil.VENDIDO ? m.soldDate() : null, v.colorNsys(), m.esEsim());`
- en `ficha`, sustituye `v.porPasar() ? v.grade() : null, poner != null && poner.compruebalo());` por
  `v.porPasar() ? v.grade() : null, poner != null && poner.compruebalo(), m.esEsim());`
- en `comprobar`, sustituye

```java
                return new MovilComprobado(imei, false, null, null, null, null, null, false, null, null, null, List.of(), null, null);
```

  por

```java
                return new MovilComprobado(imei, false, null, null, null, null, null, false, null, null, null, List.of(), null, null,
                        false);
```

  y `abiertos.getOrDefault(NsysDAO.claveCruce(imei), List.of()), v.colorNsys(), v.gradoNsys());` por
  `abiertos.getOrDefault(NsysDAO.claveCruce(imei), List.of()), v.colorNsys(), v.gradoNsys(), m.esEsim());`

- [x] **Step 4: Los cuatro records de las tarjetas**

En `nsys/FilaColorNsys.java`:
- en el comentario del record, sustituye
  ` *  taller (null en la empresa). Horas de Madrid. */` por
  ` *  taller (null en la empresa). Horas de Madrid. {@code esEsim} (0.9.24): NSYS escribe «eSIM» en el producto. */`
- sustituye

```java
                            @Schema(nullable = true) LocalDateTime vueltoDesde) {

    /** Un móvil en la empresa (sin vuelta). */
```

  por

```java
                            @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim) {

    /** Un móvil en la empresa (sin vuelta), sin eSIM. */
```

- sustituye `tambienGrado, porQue, desde, hechoPor, hechoEn, hechoVolvio, null, null, null);` por
  `tambienGrado, porQue, desde, hechoPor, hechoEn, hechoVolvio, null, null, null, false);`

En `nsys/FilaGradoNsys.java`:
- en el comentario del record, sustituye

```java
 *  día de la venta o de la baja y desde cuándo lo tiene el taller (null en la empresa). */
```

  por

```java
 *  día de la venta o de la baja y desde cuándo lo tiene el taller (null en la empresa). {@code esEsim} (0.9.24): como en
 *  FilaColorNsys. */
```

- sustituye

```java
                            @Schema(nullable = true) LocalDateTime vueltoDesde) {

    /** Un móvil en la empresa (sin vuelta). */
```

  por

```java
                            @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim) {

    /** Un móvil en la empresa (sin vuelta), sin eSIM. */
```

- sustituye `                hechoVolvio, null, null, null);` por `                hechoVolvio, null, null, null, false);`

En `nsys/FilaRevisarNsys.java`:
- en el comentario del record, sustituye ` *  taller (null en la empresa). */` por
  ` *  taller (null en la empresa). {@code esEsim} (0.9.24): del móvil del espejo (false si el IMEI ya no está). */`
- sustituye

```java
                              @Schema(nullable = true) LocalDateTime vueltoDesde) {

    /** Un móvil en la empresa (sin vuelta). */
```

  por

```java
                              @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim) {

    /** Un móvil en la empresa (sin vuelta), sin eSIM. */
```

- sustituye `                null, null);` (la última línea del constructor corto, tras `…usuario, fecha, null,`) por
  `                null, null, false);`

En `nsys/FilaSinGradoNsys.java`:
- en el comentario del record, sustituye

```java
 *  tiene el taller (null en la empresa). Sale hasta que tenga grado (sin «Revisado»). */
```

  por

```java
 *  tiene el taller (null en la empresa). Sale hasta que tenga grado (sin «Revisado»). {@code esEsim} (0.9.24): NSYS escribe
 *  «eSIM» en el producto. */
```

- sustituye `                               @Schema(nullable = true) LocalDateTime vueltoDesde) {}` por
  `                               @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim) {}`

En `nsys/TarjetasNsys.java`:
- en `color(…)`, sustituye

```java
                    cambio(p, DatoTaller.GRADO).isPresent(), porQue(p, DatoTaller.COLOR), desde, h.por(), h.en(), h.volvio(),
                    v.vendidoEn(), v.bajaEn(), v.desde()));
```

  por

```java
                    cambio(p, DatoTaller.GRADO).isPresent(), porQue(p, DatoTaller.COLOR), desde, h.por(), h.en(), h.volvio(),
                    v.vendidoEn(), v.bajaEn(), v.desde(), m.esEsim()));
```

- en `grado(…)`, sustituye

```java
                    m.goods(), cambio(p, DatoTaller.COLOR).isPresent(), porQue(p, DatoTaller.GRADO), desde, h.por(), h.en(),
                    h.volvio(), v.vendidoEn(), v.bajaEn(), v.desde()));
```

  por

```java
                    m.goods(), cambio(p, DatoTaller.COLOR).isPresent(), porQue(p, DatoTaller.GRADO), desde, h.por(), h.en(),
                    h.volvio(), v.vendidoEn(), v.bajaEn(), v.desde(), m.esEsim()));
```

- en `revisar(…)`, sustituye

```java
                    a.dato().name(), a.de(), a.a(), a.motivo(), a.idRep(), a.usuario(), a.fecha(), v.vendidoEn(), v.bajaEn(),
                    v.desde());
```

  por

```java
                    a.dato().name(), a.de(), a.a(), a.motivo(), a.idRep(), a.usuario(), a.fecha(), v.vendidoEn(), v.bajaEn(),
                    v.desde(), m != null && m.esEsim());
```

- en `sinGrado(…)`, sustituye

```java
                    b.map(BateriaRecordada::nueva).orElse(null), b.map(BateriaRecordada::fecha).orElse(null), v.vendidoEn(),
                    v.bajaEn(), v.desde()));
```

  por

```java
                    b.map(BateriaRecordada::nueva).orElse(null), b.map(BateriaRecordada::fecha).orElse(null), v.vendidoEn(),
                    v.bajaEn(), v.desde(), m.esEsim()));
```

Si al compilar sale otro `new FilaColorNsys(…)`, `new FilaGradoNsys(…)`, `new FilaRevisarNsys(…)` o `new FilaSinGradoNsys(…)` que
no está aquí (el 03/11 solo había los de `TarjetasNsys` y los constructores cortos), se le añade `false` (o `m.esEsim()` si tiene
el `MovilNsys` a mano) y se apunta en el registro.

- [x] **Step 5: Comprobar que pasan**

Run: `mvn -q test -Dtest='StockTelefonosControllerTest,TarjetasNsysTest,OpenApiContractTest'`
Expected: PASS.

- [x] **Step 6: El contrato**

En `docs/api_contract.md`, justo después de la viñeta de la 0.9.21 que termina en
«…`modelo` sigue siendo el código de `Telefono.MODELO`.», añade:

```markdown
- Desde la 0.9.24, esas seis listas traen también `esEsim` en `ReparacionResumen` (booleano, no nulo, el último campo): si NSYS
  escribe «eSIM» en el producto (`Nsys_movil.ES_ESIM`), de la misma fila que la `capacidad`; `false` en los mismos casos en
  que la capacidad es `null`. Lo traen igual la ficha (`GET /api/stock-telefonos/{imeiSn}`), el buscador
  (`GET /api/stock-telefonos/buscar`), «Comprobar» (`POST /api/stock-telefonos/comprobar`; un IMEI no encontrado, `false`) y
  las filas de «Cambio de color», «Cambio de grado», «Revisar a mano» y «Sin grado en NSYS» de las tarjetas de «Por pasar a
  NSYS» (un aviso sin móvil en el espejo, `false`). El stock (`GET /api/stock-telefonos`) ya lo traía.
```

- [x] **Step 7: Suite completa del servidor**

Run: `mvn -q test`
Expected: sin fallos. Después, `grep -c '"esEsim"' target/openapi.json` da **al menos 9** (los 8 esquemas de esta pieza y
`StockTelefonosMovilStock`). Anota el total de tests en el registro.

- [x] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java \
        src/main/java/com/reparaciones/servidor/nsys/FilaColorNsys.java \
        src/main/java/com/reparaciones/servidor/nsys/FilaGradoNsys.java \
        src/main/java/com/reparaciones/servidor/nsys/FilaRevisarNsys.java \
        src/main/java/com/reparaciones/servidor/nsys/FilaSinGradoNsys.java \
        src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java \
        src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java \
        src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java \
        src/test/java/com/reparaciones/servidor/OpenApiContractTest.java \
        docs/api_contract.md
git commit -m "feat: la ficha, el buscador, comprobar y las cuatro tarjetas de NSYS dicen si el movil es eSIM (0.9.24)"
```

---

### Task 3: Web — contrato, fábricas, `conEsim` y las cinco copias de NSYS

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `src/shared/api/schema.d.ts` (generados)
- Modify: `src/modules/taller/test/fabrica.ts`, `src/modules/telefonos/test/fabrica.ts`
- Modify: `src/shared/lib/telefonoNsys.ts` (+ `telefonoNsys.test.ts`)
- Modify: `src/modules/taller/imeis/FichaMovil.tsx` (+ `FichaMovil.test.tsx`), `src/modules/taller/imeis/ImeisPage.tsx`
  (+ `ImeisPage.test.tsx`), `src/modules/telefonos/stock/BuscadorImei.tsx` (+ `StockTelefonosPage.test.tsx`),
  `src/modules/telefonos/stock/porPasar.ts` (+ `porPasar.test.ts`), `src/modules/telefonos/stock/varios.ts` (+ `varios.test.ts`)

**Interfaces:**
- Consumes: `/c/Users/dev/Documents/wt/servidor-0924-esim/target/openapi.json` de la Task 2.
- Produces (en `src/shared/lib/telefonoNsys.ts`):
  - `SIN_MODELO_MAESTRO: string` (`'Sin modelo en el maestro'`);
  - `conEsim(nombre: string, esEsim: boolean | undefined): string`: «iPhone 14» + `true` → «iPhone 14 eSIM»; vacío o
    `SIN_MODELO_MAESTRO`, tal cual;
  - `nombreMovilNsys(m: { modelo: string | null; modeloMaestro: string | null; esEsim?: boolean }): string`.
- Produces (tipos): `esEsim: boolean` en `ReparacionResumen`, `FichaMovil`, `MovilBuscado`, `MovilComprobado`,
  `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys` y `FilaSinGradoNsys`; las fábricas lo traen (`false`, salvo
  `filaGrado()` y `filaRevisar()`, `true`).

- [x] **Step 1: Worktree de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git log --oneline -3 origin/main
git worktree add -b dev_v0924_esim /c/Users/dev/Documents/wt/web-0924-esim origin/main
cd /c/Users/dev/Documents/wt/web-0924-esim && npm ci
```

Expected: el worktree sobre `origin/main` (el 03/11, `206fa5b`, 0.9.22.1; si la 0.9.23 ya ha entrado, su merge) y `npm ci` sin
errores. Apunta en el registro el commit y si lleva o no la 0.9.23. Línea base: `npm run check` en verde.

- [x] **Step 2: Contrato y tipos**

Run (en `/c/Users/dev/Documents/wt/web-0924-esim`):
`cp /c/Users/dev/Documents/wt/servidor-0924-esim/target/openapi.json api/openapi.json && npm run api:types:offline`

Expected: `git diff --stat` enseña solo `api/openapi.json` y `src/shared/api/schema.d.ts`, con:
- en los 8 esquemas (`ReparacionResumen`, `StockTelefonosFichaMovil`, `StockTelefonosMovilBuscado`,
  `StockTelefonosMovilComprobado`, `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys`, `FilaSinGradoNsys`), el bloque
  `esEsim` (`"type": "boolean"`) y su entrada en `required`; en `schema.d.ts`, 8 líneas `esEsim: boolean;`;
- y, si el contrato de la web no se ha refrescado desde el borrado de las 24 rutas retiradas del servidor (sp7b tarea 27,
  `0fee2ad`), esas rutas desaparecen de los dos ficheros (y los esquemas que solo usaban ellas).

**Si el diff toca algo más, parar y avisar** (el `main` del servidor y el contrato de la web no van a la par). Apunta en el
registro si se fueron las rutas retiradas.

- [x] **Step 3: Las fábricas (el tipo las rompe)**

Run: `npx tsc -b`
Expected: errores por `esEsim` que falta en las fábricas `resumen()` y `fichaMovil()` (`src/modules/taller/test/fabrica.ts`) y
`movilBuscado()`, `movilComprobado()`, `filaColor()`, `filaGrado()`, `filaRevisar()` y `filaSinGrado()`
(`src/modules/telefonos/test/fabrica.ts`). Si sale algún error que no es `esEsim` (p. ej. un tipo de una ruta retirada que la
web aún usara), parar y avisar.

En `src/modules/taller/test/fabrica.ts`:
- en `resumen()`, sustituye

```ts
    // 0.9.21: sin NSYS por defecto (la lista dice el modelo solo, como antes).
    capacidad: null, color: null,
```

  por

```ts
    // 0.9.21: sin NSYS por defecto (la lista dice el modelo solo, como antes). 0.9.24: sin eSIM.
    capacidad: null, color: null, esEsim: false,
```

- en `fichaMovil()`, sustituye `ponerProducto: null, ponerGrado: null, ponerCompruebalo: false, ...p,` por
  `ponerProducto: null, ponerGrado: null, ponerCompruebalo: false, esEsim: false, ...p,`

En `src/modules/telefonos/test/fabrica.ts`:
- en `movilBuscado()`, sustituye `ubicacion: 'Listos', fechaVenta: null, colorNsys: null, ...p,` por
  `ubicacion: 'Listos', fechaVenta: null, colorNsys: null, esEsim: false, ...p,`
- en `movilComprobado()`, sustituye `fechaVenta: null, trabajos: [], colorNsys: null, gradoNsys: null, ...p,` por
  `fechaVenta: null, trabajos: [], colorNsys: null, gradoNsys: null, esEsim: false, ...p,`
- en `filaColor()`, sustituye
  `desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, ...p,`
  por
  `desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, esEsim: false, ...p,`
- en `filaGrado()` (su producto ya es «Apple iPhone 14 eSIM 256GB Midnight»), sustituye
  `desde: '2026-10-24T12:40:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, ...p,`
  por
  `desde: '2026-10-24T12:40:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, esEsim: true, ...p,`
- en `filaRevisar()` (su chasis ya es `chai14yellowesim`), sustituye
  `usuario: 'Técnico 3', fecha: '2026-10-22T11:14:00', vendidoEn: null, bajaEn: null, vueltoDesde: null, ...p,` por
  `usuario: 'Técnico 3', fecha: '2026-10-22T11:14:00', vendidoEn: null, bajaEn: null, vueltoDesde: null, esEsim: true, ...p,`
- en `filaSinGrado()`, sustituye

```ts
    bateriaIdRep: 'R20261026_205', bateriaNueva: true, bateriaEn: '2026-10-26T17:28:49', vendidoEn: null, bajaEn: null,
    vueltoDesde: null, ...p,
```

  por

```ts
    bateriaIdRep: 'R20261026_205', bateriaNueva: true, bateriaEn: '2026-10-26T17:28:49', vendidoEn: null, bajaEn: null,
    vueltoDesde: null, esEsim: false, ...p,
```

Run: `npx tsc -b`
Expected: limpio. Run: `npx vitest run src/modules/telefonos src/modules/taller`
Expected: PASS (las pantallas aún no miran `esEsim`, así que nada cambia).

- [x] **Step 4: Commit del contrato**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/taller/test/fabrica.ts src/modules/telefonos/test/fabrica.ts
git commit -m "chore: contrato y tipos con esEsim en las listas del taller y en las de NSYS (0.9.24)"
```

- [x] **Step 5: Las pruebas que fallan**

En `src/shared/lib/telefonoNsys.test.ts`, añade `conEsim, nombreMovilNsys, SIN_MODELO_MAESTRO` a la lista del `import … from
'./telefonoNsys'` (línea 2) y, al final del fichero, añade:

```ts
describe('la eSIM detrás del modelo (0.9.24, spec «La descripción del móvil dice si es eSIM» §2.1)', () => {
  it('conEsim: «eSIM» detrás del nombre si lo es; sin nombre o «Sin modelo en el maestro», tal cual', () => {
    expect(conEsim('iPhone 14', true)).toBe('iPhone 14 eSIM')
    expect(conEsim('iPhone 14', false)).toBe('iPhone 14')
    expect(conEsim('iPhone 14', undefined)).toBe('iPhone 14')
    expect(conEsim('', true)).toBe('')
    expect(conEsim(SIN_MODELO_MAESTRO, true)).toBe('Sin modelo en el maestro')
  })

  it('nombreMovilNsys: el nombre del espejo con su eSIM; con la capacidad detrás en descripcionMovil', () => {
    expect(nombreMovilNsys({ modelo: '14', modeloMaestro: '14', esEsim: true })).toBe('iPhone 14 eSIM')
    expect(nombreMovilNsys({ modelo: null, modeloMaestro: '17e', esEsim: true })).toBe('iPhone 17e eSIM')
    expect(nombreMovilNsys({ modelo: null, modeloMaestro: null, esEsim: true })).toBe('Sin modelo en el maestro')
    expect(nombreMovilNsys({ modelo: '13pro', modeloMaestro: '13 PRO', esEsim: false })).toBe('iPhone 13 Pro')
    expect(nombreMovilNsys({ modelo: '13pro', modeloMaestro: '13 PRO' })).toBe('iPhone 13 Pro')
    expect(descripcionMovil(nombreMovilNsys({ modelo: '14', modeloMaestro: '14', esEsim: true }), '256GB')).toBe('iPhone 14 eSIM 256GB')
  })
})
```

En `src/modules/taller/imeis/FichaMovil.test.tsx`, al final del fichero, añade:

```tsx
describe('ficha de NSYS: la eSIM (0.9.24)', () => {
  it('«iPhone 14 eSIM 256GB Negro» si NSYS dice que es eSIM; si no, sin ella', () => {
    const { unmount } = ficha({ modelo: '14', modeloMaestro: '14', capacidad: '256GB', color: 'Negro', esEsim: true })
    expect(screen.getByRole('region', { name: 'Ficha de NSYS' })).toHaveTextContent('iPhone 14 eSIM 256GB Negro')
    unmount()
    ficha({ modelo: '14', modeloMaestro: '14', capacidad: '256GB', color: 'Negro' })
    expect(screen.getByRole('region', { name: 'Ficha de NSYS' })).toHaveTextContent('iPhone 14 256GB Negro')
  })
})
```

En `src/modules/telefonos/stock/StockTelefonosPage.test.tsx`, al final del fichero, añade:

```tsx
describe('el buscador de IMEI con la eSIM (0.9.24)', () => {
  it('«iPhone 14 eSIM 256GB» si NSYS dice que es eSIM', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      movilBuscado({ imeiSn: '350000000000071', modelo: '14', modeloMaestro: '14', capacidad: '256GB', color: 'Negro', esEsim: true }),
    ])))
    abrir()
    await user.type(await screen.findByRole('searchbox', { name: 'Buscar IMEI en todos los móviles' }), '3500')
    expect(await screen.findByRole('button', { name: /^350000000000071 · iPhone 14 eSIM 256GB Negro · Listos$/ })).toBeInTheDocument()
  })
})
```

En `src/modules/telefonos/stock/porPasar.test.ts`, en el test «el último trabajo, el modelo del taller y el de NSYS», sustituye

```ts
    expect(modeloDeFila(filaGrado())).toBe('iPhone 14 256GB')
    expect(modeloDeFila(filaRevisar())).toBe('iPhone 14 512GB')
```

por

```ts
    // 0.9.24: las dos fábricas son de un móvil eSIM; una fila sin eSIM, como antes.
    expect(modeloDeFila(filaGrado())).toBe('iPhone 14 eSIM 256GB')
    expect(modeloDeFila(filaRevisar())).toBe('iPhone 14 eSIM 512GB')
    expect(modeloDeFila(filaGrado({ esEsim: false }))).toBe('iPhone 14 256GB')
    expect(modeloDeFila(filaColor({ esEsim: true }))).toBe('iPhone 13 eSIM 128GB')
```

(`filaColor` ya está importada: la usa el test «el modelo y desde cuándo».)

En `src/modules/telefonos/stock/varios.test.ts`, añade `modeloYCapacidad` a la lista del `import … from './varios'` y, dentro del
`describe` del test «la tabla para Excel: …», detrás de ese test, añade:

```ts
  it('0.9.24: el modelo lleva «eSIM» si NSYS lo dice, en la tabla y en la copia para Excel', () => {
    const m = movilComprobado({ imei: '350000000000001', modelo: '14', modeloMaestro: '14', capacidad: '256GB', esEsim: true })
    expect(modeloYCapacidad(m)).toBe('iPhone 14 eSIM 256GB')
    const { lineas } = analizar('350000000000001')
    expect(paraExcel(cruzar(lineas, [m]), AHORA).split('\n')[1].split('\t')[2]).toBe('iPhone 14 eSIM 256GB')
  })
```

En `src/modules/taller/imeis/ImeisPage.test.tsx`, dentro del `describe('ImeisPage: móviles sin trabajos del taller (0.9.17)', …)`,
detrás de su primer test, añade:

```tsx
  it('0.9.24: el aviso dice la eSIM del móvil', async () => {
    const user = userEvent.setup()
    server.use(http.get('*/api/stock-telefonos/buscar', () => HttpResponse.json([
      { imeiSn: '353918700112233', modelo: '14', modeloMaestro: '14', capacidad: '256GB', color: 'Negro', estado: 'STOCK',
        ubicacion: 'Listos', fechaVenta: null, colorNsys: null, esEsim: true },
    ])))
    renderConProviders(<ImeisPage />, { sesion: SESION_SUPER, ruta: '/reparaciones/imeis' })
    await user.type(await screen.findByPlaceholderText('Filtrar por IMEI'), '353918700112233')
    const aviso = await screen.findByText(/También hay 1 móvil sin trabajos del taller con ese IMEI/)
    expect(aviso).toHaveTextContent('(iPhone 14 eSIM)')
  })
```

- [x] **Step 6: Comprobar que fallan**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis/FichaMovil.test.tsx src/modules/telefonos/stock/StockTelefonosPage.test.tsx src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/varios.test.ts src/modules/taller/imeis/ImeisPage.test.tsx`
Expected: FAIL: `telefonoNsys.test.ts` porque `conEsim`, `nombreMovilNsys` y `SIN_MODELO_MAESTRO` no existen; en los demás, los
textos con «eSIM» no salen («iPhone 14 256GB Negro» en vez de «iPhone 14 eSIM 256GB Negro», etc.).

- [x] **Step 7: `conEsim` y `nombreMovilNsys`**

En `src/shared/lib/telefonoNsys.ts`, sustituye

```ts
/** Nombre de un modelo de NSYS: el código del ERP traducido o, si no está en el catálogo, el texto del maestro. */
export function nombreModeloNsys(modelo: string | null, modeloMaestro: string | null): string {
  if (modelo) return traducirModelo(modelo)
  if (modeloMaestro) return `iPhone ${modeloMaestro}`
  return 'Sin modelo en el maestro'
}
```

por

```ts
/** Lo que se enseña como nombre de un móvil de NSYS cuyo producto no está en el maestro. */
export const SIN_MODELO_MAESTRO = 'Sin modelo en el maestro'

/** Nombre de un modelo de NSYS: el código del ERP traducido o, si no está en el catálogo, el texto del maestro. */
export function nombreModeloNsys(modelo: string | null, modeloMaestro: string | null): string {
  if (modelo) return traducirModelo(modelo)
  if (modeloMaestro) return `iPhone ${modeloMaestro}`
  return SIN_MODELO_MAESTRO
}

/** 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.1): el nombre del modelo con «eSIM» detrás si el móvil es solo
 *  eSIM («iPhone 14 eSIM»), como lo escribe NSYS en el producto. Sin nombre o con «Sin modelo en el maestro», tal cual: no es un
 *  modelo. Lo usan las listas del taller y sus CSV (con el modelo del taller) y las copias de NSYS (`nombreMovilNsys`). */
export function conEsim(nombre: string, esEsim: boolean | undefined): string {
  return esEsim && nombre && nombre !== SIN_MODELO_MAESTRO ? `${nombre} eSIM` : nombre
}

/** 0.9.24: el nombre de un móvil del espejo de NSYS con su eSIM: la ficha del IMEI, el buscador, «Por pasar a NSYS», «Pegar
 *  varios IMEIs» y el aviso de IMEIs. `esEsim` opcional: un servidor anterior a la 0.9.24 no lo manda. */
export const nombreMovilNsys = (m: { modelo: string | null; modeloMaestro: string | null; esEsim?: boolean }): string =>
  conEsim(nombreModeloNsys(m.modelo, m.modeloMaestro), m.esEsim)
```

- [x] **Step 8: Las cinco copias de NSYS**

`src/modules/taller/imeis/FichaMovil.tsx`:
- en el import de `@/shared/lib/telefonoNsys`, cambia `nombreModeloNsys` por `nombreMovilNsys`;
- sustituye `{descripcionMovil(nombreModeloNsys(ficha.modelo, ficha.modeloMaestro), ficha.capacidad)}` por
  `{descripcionMovil(nombreMovilNsys(ficha), ficha.capacidad)}`.

`src/modules/telefonos/stock/BuscadorImei.tsx`:
- sustituye `import { descripcionMovil, nombreModeloNsys } from '@/shared/lib/telefonoNsys'` por
  `import { descripcionMovil, nombreMovilNsys } from '@/shared/lib/telefonoNsys'`;
- sustituye

```tsx
                {`${m.imeiSn} · ${descripcionMovil(nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad)}`}
```

  por

```tsx
                {`${m.imeiSn} · ${descripcionMovil(nombreMovilNsys(m), m.capacidad)}`}
```


`src/modules/telefonos/stock/porPasar.ts`:
- sustituye `import { descripcionMovil, diaNsys, nombreModeloNsys, textoBateriaRecordada } from '@/shared/lib/telefonoNsys'` por
  `import { descripcionMovil, diaNsys, nombreModeloNsys, nombreMovilNsys, textoBateriaRecordada } from '@/shared/lib/telefonoNsys'`
  (`nombreModeloNsys` lo sigue usando `modeloDelTaller`);
- sustituye

```ts
/** «iPhone 13 128GB» de una fila con el modelo del espejo de NSYS. */
export const modeloDeFila = (f: { modelo: string | null; modeloMaestro: string | null; capacidad: string | null }): string =>
  descripcionMovil(nombreModeloNsys(f.modelo, f.modeloMaestro), f.capacidad)
```

  por

```ts
type FilaConModelo = { modelo: string | null; modeloMaestro: string | null; capacidad: string | null; esEsim?: boolean }

/** «iPhone 13 128GB» de una fila con el modelo del espejo de NSYS; desde la 0.9.24, «iPhone 14 eSIM 256GB» si es eSIM. */
export const modeloDeFila = (f: FilaConModelo): string => descripcionMovil(nombreMovilNsys(f), f.capacidad)
```

`src/modules/telefonos/stock/varios.ts`:
- sustituye `import { descripcionMovil, diaNsys, nombreModeloNsys, textoEstadoMovil } from '@/shared/lib/telefonoNsys'` por
  `import { descripcionMovil, diaNsys, nombreMovilNsys, textoEstadoMovil } from '@/shared/lib/telefonoNsys'`;
- sustituye

```ts
/** «iPhone 13 128GB». */
export const modeloYCapacidad = (m: MovilComprobado): string => descripcionMovil(nombreModeloNsys(m.modelo, m.modeloMaestro), m.capacidad)
```

  por

```ts
/** «iPhone 13 128GB»; desde la 0.9.24, «iPhone 14 eSIM 256GB» si es eSIM. */
export const modeloYCapacidad = (m: MovilComprobado): string => descripcionMovil(nombreMovilNsys(m), m.capacidad)
```

`src/modules/taller/imeis/ImeisPage.tsx`:
- sustituye `import { diaNsys, nombreModeloNsys } from '@/shared/lib/telefonoNsys'` por
  `import { diaNsys, nombreMovilNsys } from '@/shared/lib/telefonoNsys'`;
- sustituye

```tsx
              {` (${nombreModeloNsys(m.modelo, m.modeloMaestro)}${m.fechaVenta ? `, vendido el ${diaNsys(m.fechaVenta)}` : ''})`}
```

  por

```tsx
              {` (${nombreMovilNsys(m)}${m.fechaVenta ? `, vendido el ${diaNsys(m.fechaVenta)}` : ''})`}
```


Run: `grep -rn "nombreModeloNsys" src --include=*.ts --include=*.tsx | grep -v test`
Expected: solo `src/shared/lib/telefonoNsys.ts` (la definición y `nombreMovilNsys`), `src/modules/telefonos/stock/porPasar.ts`
(`modeloDelTaller`) y `src/modules/telefonos/stock/modelo.ts` (`modeloDe`, la lista de modelos del stock, que no cambia).

- [x] **Step 9: Comprobar que pasan**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis src/modules/telefonos`
Expected: PASS. Run: `npx tsc -b` y `npm run lint`
Expected: limpios.

- [x] **Step 10: Commit**

```bash
git add src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts \
        src/modules/taller/imeis/FichaMovil.tsx src/modules/taller/imeis/FichaMovil.test.tsx \
        src/modules/taller/imeis/ImeisPage.tsx src/modules/taller/imeis/ImeisPage.test.tsx \
        src/modules/telefonos/stock/BuscadorImei.tsx src/modules/telefonos/stock/StockTelefonosPage.test.tsx \
        src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts \
        src/modules/telefonos/stock/varios.ts src/modules/telefonos/stock/varios.test.ts
git commit -m "feat: la ficha, el buscador, por pasar a NSYS, pegar varios y el aviso de IMEIs dicen eSIM detras del modelo (0.9.24)"
```

---

### Task 4: Web — la eSIM en las listas del taller y en la columna «Modelo» de los CSV

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/modelos.ts` (+ `modelos.test.ts`)
- Modify: `src/modules/taller/lib/grupoImei.ts` (+ `grupoImei.test.ts`)
- Modify: `src/modules/taller/asignaciones/csv.ts` (+ `csv.test.ts`), `src/modules/taller/pendientes/csvPulidos.ts`
  (+ `csvPulidos.test.ts`), `src/modules/taller/historial/csvTrabajos.ts` (+ `csvTrabajos.test.ts`),
  `src/modules/taller/imeis/csvImeis.ts`
- Create: `src/modules/taller/imeis/csvImeis.test.ts`
- Test: `src/modules/taller/asignaciones/columnas.test.tsx`, `src/modules/taller/pendientes/textoCelda.test.ts`,
  `src/modules/taller/historial/columnasTrabajo.test.ts`, `src/modules/taller/imeis/ImeiDetallePage.test.tsx`

**Interfaces:**
- Consumes: `conEsim(nombre, esEsim)` y `descripcionMovil(…)` de `@/shared/lib/telefonoNsys` (Task 3); `ReparacionResumen.esEsim`.
- Produces (en `src/modules/taller/lib/modelos.ts`), con el tipo local
  `MovilDelTaller = { modelo?: string | null; capacidad?: string | null; color?: string | null; esEsim?: boolean }`:
  - `nombreTrabajo(t: MovilDelTaller): string`: el modelo del taller traducido con «eSIM» detrás si lo es; es la columna
    «Modelo» de los CSV;
  - `descripcionTrabajo(t: MovilDelTaller): string` (la de la 0.9.21, ahora con la eSIM).
- Produces (en `src/modules/taller/lib/grupoImei.ts`): `GrupoImei.esEsim: boolean` y
  `movilDe(trabajos): Pick<GrupoImei, 'modelo' | 'capacidad' | 'color' | 'esEsim'>`.

Las pantallas no cambian de código: todas pintan `descripcionTrabajo` (columnas «Modelo», «Copiar celda», la confirmación de
«Completar seleccionados» y la barra del detalle). Sus pruebas sí ganan un caso con eSIM.

- [x] **Step 1: Las pruebas que fallan**

En `src/modules/taller/lib/modelos.test.ts`, añade `nombreTrabajo` a la lista del `import … from './modelos'` y, al final del
fichero, añade:

```ts
describe('la eSIM en las listas del taller (0.9.24)', () => {
  it('nombreTrabajo: el modelo del taller con «eSIM» detrás si NSYS lo dice; sin modelo, vacío', () => {
    expect(nombreTrabajo({ modelo: '14', esEsim: true })).toBe('iPhone 14 eSIM')
    expect(nombreTrabajo({ modelo: '14', esEsim: false })).toBe('iPhone 14')
    expect(nombreTrabajo({ modelo: '14' })).toBe('iPhone 14')
    expect(nombreTrabajo({ modelo: null, esEsim: true })).toBe('')
  })

  it('descripcionTrabajo: «iPhone 14 eSIM 256GB Negro»; sin eSIM, lo de la 0.9.21', () => {
    expect(descripcionTrabajo({ modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: true })).toBe('iPhone 14 eSIM 256GB Negro')
    expect(descripcionTrabajo({ modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: false })).toBe('iPhone 14 256GB Negro')
    expect(descripcionTrabajo({ modelo: null, capacidad: '256GB', esEsim: true })).toBe('')
  })
})
```

En `src/modules/taller/lib/grupoImei.test.ts`, en el test «modelo, capacidad y color: el primero no vacío de cada uno…»,
sustituye

```ts
    expect(movilDe(trabajos)).toEqual({ modelo: '13', capacidad: '128GB', color: 'Azul' })
```

por

```ts
    expect(movilDe(trabajos)).toEqual({ modelo: '13', capacidad: '128GB', color: 'Azul', esEsim: false })
```

y

```ts
    expect(movilDe([rr('R1')])).toEqual({ modelo: '', capacidad: null, color: null })
```

por

```ts
    expect(movilDe([rr('R1')])).toEqual({ modelo: '', capacidad: null, color: null, esEsim: false })
```

y, detrás de ese test (dentro del mismo `describe('el móvil del grupo (0.9.21)', …)`), añade:

```ts
  it('0.9.24: es eSIM si lo es en alguno de sus trabajos (una lista que no pudo leer NSYS trae false)', () => {
    const trabajos = [rr('R1', { modelo: '14' }), rr('G1', { modelo: '14', capacidad: '256GB', esEsim: true })]
    expect(movilDe(trabajos).esEsim).toBe(true)
    expect(agruparPorImei(trabajos)[0].esEsim).toBe(true)
    expect(movilDe([rr('R1', { modelo: '14' })]).esEsim).toBe(false)
  })
```

En `src/modules/taller/pendientes/textoCelda.test.ts`, dentro del `describe`, detrás del test de la 0.9.21, añade:

```ts
  it('0.9.24: con «eSIM» detrás del modelo si NSYS lo dice', () => {
    expect(textoCeldaPendiente(resumen({ modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: true }), 'modelo'))
      .toBe('iPhone 14 eSIM 256GB Negro')
  })
```

En `src/modules/taller/historial/columnasTrabajo.test.ts`, dentro de `describe('Modelo con la descripción completa (0.9.21)', …)`,
detrás de su test, añade:

```ts
  it('0.9.24: con «eSIM» detrás del modelo si NSYS lo dice', () => {
    const esim = resumen({ modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: true })
    expect(modelo.accessorFn(esim, 0)).toBe('iPhone 14 eSIM 256GB Negro')
    expect(textoCeldaTrabajo(esim, 'modelo', 'dd/MM/yyyy')).toBe('iPhone 14 eSIM 256GB Negro')
  })
```

En `src/modules/taller/asignaciones/columnas.test.tsx`, dentro de `describe('crearColumnas: Modelo con la descripción completa
(0.9.21)', …)`, detrás de su test, añade:

```tsx
  it('0.9.24: con «eSIM» detrás del modelo si NSYS lo dice', () => {
    pintar([resumen({ idRep: 'A20261103_1', imei: '350000000000001', modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: true })],
      opciones({ soloLectura: true }))
    expect(screen.getByText('iPhone 14 eSIM 256GB Negro')).toBeInTheDocument()
  })
```

En `src/modules/taller/imeis/ImeiDetallePage.test.tsx`, dentro de `describe('ImeiDetallePage: la barra con la descripción completa
(0.9.21)', …)`, detrás de su test, añade:

```tsx
  it('0.9.24: «• iPhone 16 eSIM 128GB Negro» si NSYS dice que es eSIM', async () => {
    server.use(http.get('*/api/reparaciones/historial', () =>
      HttpResponse.json(reps.map((r) => (r.imei === A ? { ...r, capacidad: '128GB', color: 'Negro', esEsim: true } : r)))))
    abrir()
    expect(await screen.findByText('• iPhone 16 eSIM 128GB Negro')).toBeInTheDocument()
  })
```

En `src/modules/taller/asignaciones/csv.test.ts`, dentro del mismo `describe` que el test «nulos: técnico, modelo, capacidad,
color, comentario y cliente vacíos; …», detrás de ese test, añade:

```ts
  it('0.9.24: «Modelo» con «eSIM» detrás si NSYS lo dice; sin modelo, vacío', () => {
    const fila = filaAsignacionCsv(resumen({ modelo: '14', capacidad: '256GB', esEsim: true }))
    expect(fila[MODELO]).toBe('iPhone 14 eSIM')
    expect(fila[CAPACIDAD]).toBe('256GB')
    expect(filaAsignacionCsv(resumen({ modelo: null, esEsim: true }))[MODELO]).toBe('')
  })
```

En `src/modules/taller/pendientes/csvPulidos.test.ts`, dentro del `describe`, detrás del último test, añade:

```ts
  it('0.9.24: «Modelo» con «eSIM» detrás si NSYS lo dice', () => {
    const r = resumen({ idRep: 'AP20261103_1', imei: '350000000000001', modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: true })
    expect(filaPulidoPendiente(r).slice(2, 5)).toEqual(['iPhone 14 eSIM', '256GB', 'Negro'])
  })
```

En `src/modules/taller/historial/csvTrabajos.test.ts`, dentro del `describe('CSV historial_pulidos (0.9.21: …)', …)`, detrás del
último test, añade:

```ts
  it('0.9.24: «Modelo» con «eSIM» detrás si NSYS lo dice', () => {
    expect(filaHistorialPulido({ ...pulido, modelo: '14', esEsim: true }, false).slice(2, 5)).toEqual(['iPhone 14 eSIM', '128GB', 'Azul'])
  })
```

Crea `src/modules/taller/imeis/csvImeis.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agruparPorImei } from '../lib/grupoImei'
import { resumen } from '../test/fabrica'
import { filaResumen } from './csvImeis'

describe('CSV agrupado_resumen: «Modelo» (0.9.24)', () => {
  it('lleva «eSIM» detrás del modelo si NSYS dice que es eSIM; si no, el modelo solo', () => {
    const [esim] = agruparPorImei([resumen({ imei: '350000000000001', modelo: '14', capacidad: '256GB', color: 'Negro', esEsim: true })])
    expect(filaResumen(esim).slice(0, 4)).toEqual(['="350000000000001"', 'iPhone 14 eSIM', '256GB', 'Negro'])
    const [sim] = agruparPorImei([resumen({ imei: '350000000000002', modelo: '14', capacidad: '256GB', color: 'Negro' })])
    expect(filaResumen(sim)[1]).toBe('iPhone 14')
  })
})
```

- [x] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/lib src/modules/taller/pendientes/textoCelda.test.ts src/modules/taller/historial/columnasTrabajo.test.ts src/modules/taller/asignaciones/columnas.test.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx src/modules/taller/asignaciones/csv.test.ts src/modules/taller/pendientes/csvPulidos.test.ts src/modules/taller/historial/csvTrabajos.test.ts src/modules/taller/imeis/csvImeis.test.ts`
Expected: FAIL: `nombreTrabajo` no existe; `movilDe` no trae `esEsim`; los textos dicen «iPhone 14 256GB Negro» y los CSV
«iPhone 14», sin «eSIM».

- [x] **Step 3: `nombreTrabajo`, `descripcionTrabajo` y `movilDe`**

En `src/modules/taller/lib/modelos.ts`, sustituye

```ts
import { descripcionMovil } from '@/shared/lib/telefonoNsys'
```

por

```ts
import { conEsim, descripcionMovil } from '@/shared/lib/telefonoNsys'
```

y sustituye

```ts
/** 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2): lo que enseñan las listas del taller donde pone «Modelo»: el
 *  modelo del taller (`Telefono.MODELO`, traducido) con la capacidad y el color de NSYS que manda el servidor («iPhone 13 128GB
 *  Azul»); fuera de NSYS, el modelo solo; sin modelo, vacío. Vale para un trabajo (`ReparacionResumen`) y para un grupo de IMEIs. */
export function descripcionTrabajo(t: { modelo?: string | null; capacidad?: string | null; color?: string | null }): string {
  return descripcionMovil(traducirModelo(t.modelo), t.capacidad, t.color)
}
```

por

```ts
/** Lo que hace falta de un trabajo (`ReparacionResumen`) o de un grupo de IMEIs para nombrar su móvil. `esEsim` opcional: un
 *  servidor anterior a la 0.9.24 no lo manda. */
type MovilDelTaller = { modelo?: string | null; capacidad?: string | null; color?: string | null; esEsim?: boolean }

/** 0.9.21 (spec «Descripción completa donde pone «Modelo»» §2): lo que enseñan las listas del taller donde pone «Modelo»: el
 *  modelo del taller (`Telefono.MODELO`, traducido) con la capacidad y el color de NSYS que manda el servidor («iPhone 13 128GB
 *  Azul»); fuera de NSYS, el modelo solo; sin modelo, vacío. Vale para un trabajo (`ReparacionResumen`) y para un grupo de IMEIs.
 *  Desde la 0.9.24, con «eSIM» detrás del modelo si lo es («iPhone 14 eSIM 256GB Azul»). */
export function descripcionTrabajo(t: MovilDelTaller): string {
  return descripcionMovil(nombreTrabajo(t), t.capacidad, t.color)
}

/** 0.9.24 (spec «La descripción del móvil dice si es eSIM» §2.1): el modelo del taller traducido con «eSIM» detrás si NSYS dice
 *  que el móvil es eSIM («iPhone 14 eSIM»); sin modelo, vacío. Es el nombre de `descripcionTrabajo` y la columna «Modelo» de los
 *  CSV del taller. */
export function nombreTrabajo(t: MovilDelTaller): string {
  return conEsim(traducirModelo(t.modelo), t.esEsim)
}
```

En `src/modules/taller/lib/grupoImei.ts`, sustituye

```ts
  /** 0.9.21: la capacidad y el color de NSYS del IMEI (null fuera de NSYS). */
  capacidad: string | null
  color: string | null
```

por

```ts
  /** 0.9.21: la capacidad y el color de NSYS del IMEI (null fuera de NSYS). */
  capacidad: string | null
  color: string | null
  /** 0.9.24: si NSYS dice que es eSIM (false fuera de NSYS). */
  esEsim: boolean
```

y sustituye

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

por

```ts
/** 0.9.21: el móvil de los trabajos de un IMEI: el primer modelo, la primera capacidad y el primer color no vacíos (las tres
 *  listas traen lo mismo del IMEI; si una no pudo leer NSYS, vale lo de otra). 0.9.24: es eSIM si lo es en alguno (una lista
 *  que no pudo leer NSYS trae false). */
export function movilDe(trabajos: ReparacionResumen[]): Pick<GrupoImei, 'modelo' | 'capacidad' | 'color' | 'esEsim'> {
  return {
    modelo: primero(trabajos.map((t) => t.modelo)) ?? '',
    capacidad: primero(trabajos.map((t) => t.capacidad)),
    color: primero(trabajos.map((t) => t.color)),
    esEsim: trabajos.some((t) => t.esEsim),
  }
}
```

(`construir` ya hace `...movilDe(trabajos)`: el grupo gana `esEsim` sin tocarlo. El `Pick` dice lo mismo que el tipo de antes,
más `esEsim`.)

- [x] **Step 4: «Modelo» de los cuatro CSV**

`src/modules/taller/asignaciones/csv.ts`:
- sustituye `import { traducirModelo } from '../lib/modelos'` por `import { nombreTrabajo } from '../lib/modelos'`;
- en `filaAsignacionCsv`, sustituye `    traducirModelo(r.modelo),` por `    nombreTrabajo(r),`;
- en el comentario de arriba, sustituye
  ` *  G7) y, desde la 0.9.21, «Capacidad» y «Color» de NSYS detrás de «Modelo» (spec «Descripción completa donde pone «Modelo»»). */`
  por
  ` *  G7) y, desde la 0.9.21, «Capacidad» y «Color» de NSYS detrás de «Modelo» (spec «Descripción completa donde pone «Modelo»»).
 *  Desde la 0.9.24, «Modelo» lleva «eSIM» detrás si NSYS lo dice («iPhone 14 eSIM»). */`

`src/modules/taller/pendientes/csvPulidos.ts`:
- sustituye `import { traducirModelo } from '../lib/modelos'` por `import { nombreTrabajo } from '../lib/modelos'`;
- sustituye `r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), r.capacidad ?? '', r.color ?? '',` por
  `r.idRep, textoForzado(r.imei), nombreTrabajo(r), r.capacidad ?? '', r.color ?? '',`;
- en el comentario, sustituye ` *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo. */` por
  ` *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo (desde la 0.9.24, con «eSIM» detrás si lo es). */`

`src/modules/taller/historial/csvTrabajos.ts`:
- sustituye `import { traducirModelo } from '../lib/modelos'` por `import { nombreTrabajo } from '../lib/modelos'`;
- en `filaHistorialPulido`, sustituye `r.idRep, textoForzado(r.imei), traducirModelo(r.modelo), r.capacidad ?? '',` por
  `r.idRep, textoForzado(r.imei), nombreTrabajo(r), r.capacidad ?? '',` (el resto de la línea, igual);
- en el comentario de `cabecerasHistorialPulidos`, sustituye ` *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo. */`
  por ` *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo (desde la 0.9.24, con «eSIM» detrás si lo es). */`

`src/modules/taller/imeis/csvImeis.ts`:
- sustituye `import { traducirModelo } from '../lib/modelos'` por `import { nombreTrabajo } from '../lib/modelos'`;
- sustituye `textoForzado(g.imei), traducirModelo(g.modelo), g.capacidad ?? '', g.color ?? '',` por
  `textoForzado(g.imei), nombreTrabajo(g), g.capacidad ?? '', g.color ?? '',`;
- en el comentario, sustituye ` *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo. */` por
  ` *  «Color» de NSYS detrás de «Modelo», que sigue siendo el modelo solo (desde la 0.9.24, con «eSIM» detrás si lo es). */`

Run: `grep -n "traducirModelo" src/modules/taller/asignaciones/csv.ts src/modules/taller/pendientes/csvPulidos.ts src/modules/taller/historial/csvTrabajos.ts src/modules/taller/imeis/csvImeis.ts`
Expected: nada.

- [x] **Step 5: Comprobar que pasan**

Run: `npx vitest run src/modules/taller`
Expected: PASS (también el CSV de `ImeisPage.test.tsx`, que sigue con «iPhone 14» porque su fila no es eSIM). Run: `npx tsc -b` y
`npm run lint`
Expected: limpios.

- [x] **Step 6: Commit**

```bash
git add src/modules/taller/lib/modelos.ts src/modules/taller/lib/modelos.test.ts \
        src/modules/taller/lib/grupoImei.ts src/modules/taller/lib/grupoImei.test.ts \
        src/modules/taller/asignaciones/csv.ts src/modules/taller/asignaciones/csv.test.ts \
        src/modules/taller/asignaciones/columnas.test.tsx \
        src/modules/taller/pendientes/csvPulidos.ts src/modules/taller/pendientes/csvPulidos.test.ts \
        src/modules/taller/pendientes/textoCelda.test.ts \
        src/modules/taller/historial/csvTrabajos.ts src/modules/taller/historial/csvTrabajos.test.ts \
        src/modules/taller/historial/columnasTrabajo.test.ts \
        src/modules/taller/imeis/csvImeis.ts src/modules/taller/imeis/csvImeis.test.ts \
        src/modules/taller/imeis/ImeiDetallePage.test.tsx
git commit -m "feat: las listas del taller y la columna Modelo de sus csv dicen eSIM detras del modelo (0.9.24)"
```

---

### Task 5: Web — E2E de solo lectura en preprod

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/esim-descripcion.spec.ts`

**Interfaces:**
- Consumes: `credenciales('E2E_USER', 'E2E_PASS')` de `tests/e2e/credenciales.ts`; `GET /api/reparaciones/historial` con
  `esEsim` (Tasks 1-2); las pantallas de las Tasks 3-4.
- Produces: la E2E que el controlador lanza en preprod (fuera de este plan).

- [x] **Step 1: La prueba**

Crea `tests/e2e/esim-descripcion.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// esim-descripcion.spec.ts (0.9.24, spec 2026-11-03-v0924-esim-en-la-descripcion-design.md): la descripción del móvil dice
// «eSIM» detrás del modelo cuando NSYS lo dice, en IMEIs, en la barra del detalle del IMEI y en la ficha de NSYS, y no lo dice
// en un móvil con SIM física. Solo LEE: toma de la API un trabajo de un móvil eSIM y otro de uno sin eSIM (no se apunta ningún
// IMEI en el código) y mira las pantallas; no guarda nada ni limpia. Como todas las E2E, solo contra PREPRODUCCIÓN: se niega a
// seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

type Trabajo = { imei: string; modelo: string | null; capacidad: string | null; esEsim: boolean }

/** GET con el token de la sesión de la página (patrón de stock-telefonos.spec.ts). */
async function leer<T>(page: Page, ruta: string): Promise<T> {
  return page.evaluate(async (r) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(r, { headers: { Authorization: `Bearer ${sesion.token}` } })
    if (!res.ok) throw new Error(`GET ${r} respondió ${res.status}`)
    return res.json()
  }, ruta) as Promise<T>
}

/** Un texto tal cual dentro de una expresión regular. */
const literal = (t: string) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

test('la descripción del móvil dice eSIM en IMEIs, en el detalle y en la ficha, y no en uno con SIM física', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)

  // Del historial del taller (todos los técnicos), un trabajo de un móvil eSIM y otro de uno sin eSIM, los dos con modelo del
  // taller, capacidad de NSYS y un IMEI de 15 cifras.
  const historial = await leer<Trabajo[]>(page, '/api/reparaciones/historial')
  const sirve = (t: Trabajo) => /^\d{15}$/.test(t.imei) && !!t.modelo && !!t.capacidad
  const esim = historial.find((t) => sirve(t) && t.esEsim)
  const sim = historial.find((t) => sirve(t) && !t.esEsim)
  test.skip(!esim || !sim, 'El historial de preprod no tiene un trabajo de un móvil eSIM y otro sin eSIM, con NSYS')

  for (const [t, esEsim] of [[esim!, true], [sim!, false]] as const) {
    const conEsim = new RegExp(`eSIM ${literal(t.capacidad!)}`)

    // IMEIs: la fila del grupo.
    await page.goto('/reparaciones/imeis')
    await page.getByPlaceholder('Filtrar por IMEI').fill(t.imei)
    const fila = page.getByRole('row').filter({ hasText: t.imei })
    await expect(fila).toHaveCount(1)
    await expect(fila).toContainText(t.capacidad!)
    if (esEsim) await expect(fila).toContainText(conEsim)
    else await expect(fila).not.toContainText(conEsim)

    // Detalle del IMEI: la barra «• descripción» y la ficha de NSYS.
    await page.goto(`/reparaciones/imeis/${encodeURIComponent(t.imei)}`)
    const barra = page.getByText(/^• iPhone /)
    await expect(barra).toContainText(t.capacidad!)
    const ficha = page.getByRole('region', { name: 'Ficha de NSYS' })
    await expect(ficha).toContainText(t.imei)
    if (esEsim) {
      await expect(barra).toContainText(conEsim)
      await expect(ficha).toContainText(conEsim)
    } else {
      await expect(barra).not.toContainText('eSIM')
      await expect(ficha).not.toContainText(conEsim)
    }
  }
})
```

- [x] **Step 2: Comprobar que compila y se lista**

Run: `npx playwright test --list`
Expected: la lista de siempre más `esim-descripcion.spec.ts › la descripción del móvil dice eSIM en IMEIs, en el detalle y en la
ficha, y no en uno con SIM física`, sin errores de compilación. Run: `npm run lint`
Expected: limpio. (No se lanza contra preprod aquí: lo hace el controlador tras subir las dos ramas.)

- [x] **Step 3: Commit**

```bash
git add tests/e2e/esim-descripcion.spec.ts
git commit -m "test: E2E de solo lectura de la eSIM en la descripcion del movil (0.9.24)"
```

---

### Task 6: Cierre — versión, documentación y suites completas

**Files:**
- Modify: `gestion-reparaciones-web/package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/asignaciones.md`,
  `docs/paridad/pendientes.md`, `docs/paridad/historial.md`, `docs/paridad/imeis.md`
- Create: `docs/novedades/NOVEDADES-v0.9.24.md` (raíz; lo sube el controlador con los gitlinks)
- Modify: `C:/Users/dev/Documents/Apuntes/plan-futuro.md` (fuera de los repos)

**Interfaces:**
- Consumes: todo lo de las Tasks 1-5.
- Produces: las ramas `dev_v0924_esim` (servidor y web) listas para la revisión y el merge.

- [x] **Step 1: Versión de la web**

Run (en `/c/Users/dev/Documents/wt/web-0924-esim`):

```bash
V=$(node -p "require('./package.json').version")
echo "$V"
sed -i "s/\"version\": \"$V\"/\"version\": \"0.9.24\"/" package.json package-lock.json
git diff --stat
```

Expected: `$V` es la versión de partida (`0.9.22.1`, o `0.9.23` si ya entró) y el diff toca 1 línea de `package.json` y 2 de
`package-lock.json` (la raíz y `packages[""]`). Si toca más líneas de `package-lock.json`, deshacer con
`git checkout package-lock.json` y cambiar esas dos a mano.

- [x] **Step 2: CHANGELOG de la web**

En `CHANGELOG.md`, justo **encima** de la primera entrada `## [` (la más reciente), añade:

```markdown
## [0.9.24] - (pendiente) — La descripción del móvil dice si es eSIM

- **«iPhone 14 eSIM 256GB Azul»**: si NSYS dice que el móvil es eSIM (lo escribe en el producto), la descripción lleva «eSIM» detrás del modelo. En las listas del taller (Asignaciones, Pendientes, Pulidos pendientes con su confirmación, el Historial de trabajos y de pulidos, IMEIs, la barra del detalle del IMEI y el aviso de los móviles sin trabajos) y en las de NSYS (la ficha del IMEI, el buscador de IMEI, las tarjetas de «Por pasar a NSYS» y «Pegar varios IMEIs» con su copia para Excel), también al copiar la celda. Un móvil con SIM física, o que no está en NSYS, sigue como antes. La tabla del stock ya tenía su marca «eSIM» y no cambia.
- Los CSV `reparaciones_pendientes`, `pulidos_pendientes`, `historial_pulidos` y `agrupado_resumen` dicen «iPhone 14 eSIM» en «Modelo»; las columnas no cambian.
- Requiere el servidor de la 0.9.24 (con uno anterior, la descripción sale como antes, sin la eSIM). Servidor y web, sin migración.

```

- [x] **Step 3: Paridad**

En cada uno de los cuatro ficheros, justo **después** de la viñeta que empieza por «- **Diferencia deliberada (0.9.21):**» (hay
una en cada fichero), añade la viñeta indicada:

`docs/paridad/asignaciones.md`

```markdown
- **Diferencia deliberada (0.9.24):** la descripción de la columna «Modelo» y su «Copiar celda» llevan «eSIM» detrás del modelo si NSYS dice que el móvil es eSIM, «iPhone 14 eSIM 256GB Azul» (spec raíz `docs/superpowers/specs/2026-11-03-v0924-esim-en-la-descripcion-design.md`); el CSV dice «iPhone 14 eSIM» en «Modelo». Pruebas: `columnas.test.tsx`, `pendientes/textoCelda.test.ts` y `csv.test.ts`.
```

`docs/paridad/pendientes.md`

```markdown
- **Diferencia deliberada (0.9.24):** la columna «Modelo» de las tres pestañas, su «Copiar celda» y la confirmación de «Completar seleccionados» llevan «eSIM» detrás del modelo si NSYS dice que el móvil es eSIM, «iPhone 14 eSIM 256GB Azul» (spec raíz `docs/superpowers/specs/2026-11-03-v0924-esim-en-la-descripcion-design.md`); el CSV `pulidos_pendientes` dice «iPhone 14 eSIM» en «Modelo». Pruebas: `textoCelda.test.ts` y `csvPulidos.test.ts`.
```

`docs/paridad/historial.md`

```markdown
- **Diferencia deliberada (0.9.24):** la columna «Modelo» (Reparaciones, Glass y Pulidos) y su «Copiar celda» llevan «eSIM» detrás del modelo si NSYS dice hoy que el móvil es eSIM, «iPhone 14 eSIM 256GB Azul» (spec raíz `docs/superpowers/specs/2026-11-03-v0924-esim-en-la-descripcion-design.md`); el CSV `historial_pulidos` dice «iPhone 14 eSIM» en «Modelo». Pruebas: `columnasTrabajo.test.ts` y `csvTrabajos.test.ts`.
```

`docs/paridad/imeis.md`

```markdown
- **Diferencia deliberada (0.9.24):** el «Modelo» del maestro y del detalle, sus «Copiar celda», la barra «IMEI · modelo» del detalle y el aviso de los móviles sin trabajos («(iPhone 14 eSIM, vendido el …)») llevan «eSIM» detrás del modelo si NSYS dice que el móvil es eSIM; un IMEI es eSIM si lo es en alguno de sus trabajos (spec raíz `docs/superpowers/specs/2026-11-03-v0924-esim-en-la-descripcion-design.md`). El CSV `agrupado_resumen` dice «iPhone 14 eSIM» en «Modelo». Pruebas: `grupoImei.test.ts`, `csvImeis.test.ts`, `ImeiDetallePage.test.tsx` e `ImeisPage.test.tsx`.
```

- [x] **Step 4: Commit de la web**

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/asignaciones.md docs/paridad/pendientes.md \
        docs/paridad/historial.md docs/paridad/imeis.md
git commit -m "docs: version 0.9.24, changelog y paridad de la eSIM en la descripcion del movil"
```

- [x] **Step 5: Novedades (raíz, sin commit)**

Crea `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.24.md`:

```markdown
# Novedades de la versión 0.9.24

---

## 📋 Listas del taller y teléfonos

- Si el móvil es **eSIM**, la descripción lo dice detrás del modelo: **«iPhone 14 eSIM 256GB Azul»**. Sale en Asignaciones,
  Pendientes, Pulidos pendientes, el Historial, IMEIs y el detalle del IMEI, y también en la ficha del IMEI, el buscador de
  IMEI, «Por pasar a NSYS» y «Pegar varios IMEIs».
- En los CSV, la columna «Modelo» dice «iPhone 14 eSIM».
- Un móvil con SIM física sigue como antes. Un móvil que no está en NSYS (de un cliente) no dice nada de la eSIM.
- La eSIM sale de NSYS: si NSYS la tiene mal, se corrige en NSYS.
```

- [x] **Step 6: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0924-esim`
Expected: sin fallos. Anota el total en el registro.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0924-esim`
Expected: lint sin errores, `tsc -b` limpio y Vitest en verde (los lentos conocidos, de uno en uno si hace falta). Anota el total.

Run (web): `npm run build`
Expected: build sin errores.

Run (web): `npx playwright test --list`
Expected: la lista de siempre más `esim-descripcion.spec.ts`, sin errores de compilación.

- [x] **Step 7: Plan maestro y registro**

En `C:/Users/dev/Documents/Apuntes/plan-futuro.md`, justo **después** de la entrada que empieza por
«- [ ] **Descripción completa donde pone «Modelo»**» (antes de «- [ ] **Backlog de la 0.9.16**»), añade:

```markdown
- [ ] **eSIM en la descripción del móvil (0.9.24)** (pedido del 03/11): «iPhone 14 eSIM 256GB Azul» en las listas del taller, la
  ficha del IMEI, el buscador, «Por pasar a NSYS», «Pegar varios IMEIs» y la columna «Modelo» de los CSV, con `Nsys_movil.ES_ESIM`.
  Spec y plan `2026-11-03-v0924-esim-en-la-descripcion*` (raíz), ramas `dev_v0924_esim` en servidor y web; falta preprod (E2E
  `esim-descripcion.spec.ts`) y producción.
```

Sin marcar `[x]` hasta producción.

En `.superpowers/sdd/v0924-esim/progress.md` (raíz), los commits de las dos ramas (`git log --oneline origin/main..dev_v0924_esim`
en cada worktree) y los totales de las suites.

---

## Después de este plan (controlador, con el OK del usuario)

No son tareas de este plan:
1. Revisión final de las dos ramas.
2. Si la 0.9.23 ha entrado en `main` de la web después de crear la rama: `git merge origin/main` en `dev_v0924_esim`. Los
   conflictos esperados son `package.json`/`package-lock.json` (se queda `0.9.24`) y `CHANGELOG.md` (las dos entradas, la
   0.9.24 encima); `telefonoNsys.ts` y su test tocan sitios distintos. Después, `npm run check`.
3. Merge `--no-ff` de `dev_v0924_esim` en `main` del servidor y de la web y push (antes, `git fetch` y comparar con `origin`:
   hay otras sesiones); en la raíz, gitlinks, `NOVEDADES-v0.9.24.md`, este plan marcado y el §7 de la spec.
4. Preproducción: primero el servidor (lleva también la 0.9.22.2 y el borrado de las rutas retiradas), después la web; la E2E
   `tests/e2e/esim-descripcion.spec.ts` (con las de siempre que toque) y un vistazo con datos reales a la ficha, IMEIs, un CSV y
   «Por pasar a NSYS».
5. Producción, en horario si se cumplen los criterios (corte despreciable, sin migración, web vieja compatible, vuelta atrás
   simple); fecha en el CHANGELOG, tags `v0.9.24` en los dos repos; los worktrees y las ramas se borran después.
