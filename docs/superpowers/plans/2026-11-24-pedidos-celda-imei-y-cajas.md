# Pedidos: celda del IMEI con piezas y cajas «Caja · …» — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La celda del IMEI de Pedidos dice todo lo que pasa con textos cortos («eSIM · color: Rosa», «reparándose · Juan», «N/A»), con fondo solo para naranja (no cuadra) y amarillo claro (N/A); y las cajas de NSYS se llaman «Caja · …» en vez de «Taller · …» / «Listos».

**Architecture:** El servidor separa el aviso en un motivo principal (que se parte: `COLOR`, `GRADO`, `CAPACIDAD`…) y unas señales aparte (`sim`, `na`, `ms`, `dato`, `trabajos`) que viajan en un objeto nuevo `detalle` de `FilaVista` (aditivo). La web compone las piezas y el fondo con funciones puras de `filas.ts`. Las cajas cambian con una migración SQL de datos (sin código Java que dependa de los nombres) y la web que busca esos nombres sale a la vez.

**Tech Stack:** Java 17 + Spring Boot 3.3 (JUnit 5 + Mockito); MariaDB; React + TypeScript (Vitest); Playwright.

**Spec:** `docs/superpowers/specs/2026-11-24-pedidos-celda-imei-y-cajas-design.md` (prototipo `assets/2026-11-24-pedidos-celda-imei-prototipo.html`).

## Global Constraints

- Versión: **la siguiente a «IMEI duplicados en pedidos»** (otra sesión la está haciendo; hoy la web está en 0.9.44). Preguntar el número al usuario en la Task 8; no inventarlo.
- Ramas `dev_pedidos_celda_imei` en servidor y web, worktrees `C:/Users/dev/Documents/wt/servidor-pedidos-celda-imei` y `C:/Users/dev/Documents/wt/web-pedidos-celda-imei`, desde `origin/main`. **Antes de fusionar** hay que traer la rama de IMEI duplicados (merge de `origin/main` cuando ya esté dentro) y repetir las pruebas. Hay otras sesiones: `git fetch` antes de comparar, no tocar ramas ni worktrees ajenos.
- Commits sin `Co-Authored-By` (norma del usuario). **Nada de merge, push, tag ni despliegue sin OK explícito del usuario.** Claude no hace SSH a las VMs: prepara los comandos y el usuario los ejecuta; cada bloque ssh empieza con `hostname` y, en prod, `/usr/local/sbin/backup-erp.sh`.
- Maven desde Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"` (comprobar las rutas con `ls /c/Users/dev/tools`).
- Repos públicos: datos de prueba inventados (IMEI `3500000000000xx`, técnicos «Técnico 1», «Juan», «Ana»).
- Nombres de caja exactos: `Caja · Pulir`, `Caja · Glass`, `Caja · Reparación`, `Caja · Pantalla rota`, `Caja · Listos` (el punto es `·`, U+00B7, con un espacio a cada lado).
- Textos exactos de la celda: `eSIM`, `SIM física`, `N/A`, `MS`, `B por A`, `reparándose · <técnicos>`, `color: <c>`, `grado: <g>`, `otro color o grado`, `modelo: <m>`, `capacidad: <c>`, `no se vende: <g>`, `SKU desconocido`, `no está en NSYS`, `en otro pedido`. Separador entre piezas: ` · `.
- Colores: texto naranja `text-[#B45309]`, texto ámbar `text-form-agotado-text`; fondo naranja `bg-[#FFE0B2]`, fondo N/A `bg-form-agotado-bg`. No hay más fondos.
- No se pone ninguna marca automática. `noCuadra` sigue siendo `nivel === 'MAL'`.

## Cambios respecto a la spec (decididos al leer el código; se corrige la spec en la Task 0)

1. `Nsys_evento.A` también guarda la ubicación en los eventos `ENTRADA` (`Comparador.java:52`), así que la migración cambia `A` con `TIPO IN ('UBICACION','ENTRADA')` y `DE` con `TIPO = 'UBICACION'`.
2. «MS» deja de ser motivo y pasa a señal `ms` (como `na`): así sale «MS · color: Rojo». Sin otra cosa, nivel AVISO.
3. Las señales viajan en `FilaVista.detalle` (objeto `DetalleAviso`, nulo sin aviso) en vez de cuatro campos sueltos: `FilaVista` tiene tres constructores de compatibilidad y así solo crece un componente. `trabajos` es la lista `TrabajoAbierto` completa (el mismo tipo que ya usa Stock).
4. Los tests del servidor que usan «Listos»/«Taller · …» como datos de ejemplo no se tocan: ningún código Java compara esos nombres.

## Mapa de ficheros

Servidor (`gestion-reparaciones-servidor/`, worktree):
- Modify `src/main/java/com/reparaciones/servidor/pedidos/Aceptacion.java` — `aceptaColor`, `aceptaGrado`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/EstadoUnidad.java` — orden nuevo y `Resultado` con señales.
- Create `src/main/java/com/reparaciones/servidor/pedidos/DetalleAviso.java`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/AvisoImei.java` — señales, `MOTIVOS_SKU`, `detalle()`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/FilaVista.java` — componente `detalle`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/VistaFilas.java` — validada conserva señales; pasa `detalle`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/Recomendador.java` — fuera SIM distinta, n/a en nivel 3.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/FilasController.java:201` (solo el javadoc de motivos).
- Create `sql/renombrar-cajas-nsys.sql`, `sql/renombrar-cajas-nsys-atras.sql`.
- Modify `sql/crear_bd.sql:542-546`, `sql/migracion-nsys-espejo.sql:127-131,146-157`.
- Modify `docs/api_contract.md` (FilaVista).
- Test `src/test/java/com/reparaciones/servidor/pedidos/AceptacionTest.java` (crear si no existe), `EstadoUnidadTest.java`, `AvisoImeiTest.java`, `RecomendadorTest.java`, `VistaFilasTest.java`.

Web (`gestion-reparaciones-web/`, worktree):
- Modify `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados), `src/shared/api/client.ts` (tipo `DetalleAviso`), `src/modules/telefonos/test/fabrica.ts`.
- Modify `src/modules/telefonos/stock/quien.ts` (+ `quien.test.ts`) — `textoReparandose`, nombres de caja.
- Modify `src/modules/telefonos/pedidos/filas.ts` (+ `filas.test.ts`) — `partesAviso`, `lineasAviso`, `claseCelda`, `TEXTO_NIVEL`, `MOTIVOS_SKU`.
- Modify `src/modules/telefonos/pedidos/CeldaImei.tsx`, `PanelFila.tsx`, `AyudaPedidos.tsx` (+ `PedidosAyuda.test.tsx`, `PedidosPanel.test.tsx` y el test que pinte la celda).
- Modify `src/shared/lib/telefonoNsys.ts` (+ test), `src/modules/telefonos/stock/modelo.ts` (+ test), `src/modules/taller/imeis/rastro.ts`, `PanelRastro.tsx` (+ tests), fábricas `src/modules/telefonos/test/fabrica.ts`, `src/modules/taller/test/fabrica.ts`, y los tests de Stock que usan los nombres viejos.
- Modify `package.json`, `package-lock.json`, `CHANGELOG.md`.

Raíz (`ProgramaReparaciones/`):
- Modify `docs/superpowers/specs/2026-11-24-pedidos-celda-imei-y-cajas-design.md` (los 4 cambios de arriba), `docs/modulos/pedidos.md`.

---

### Task 0: Spec corregida, ramas y worktrees

**Files:** Modify `docs/superpowers/specs/2026-11-24-pedidos-celda-imei-y-cajas-design.md`.

- [x] **Step 1: Corregir la spec** (hecho al escribir el plan) con los 4 «Cambios respecto a la spec» de este plan:
  - §1 Migración: «`Nsys_evento.A` con `TIPO IN ('UBICACION','ENTRADA')` y `Nsys_evento.DE` con `TIPO = 'UBICACION'`».
  - §3: la tabla de campos pasa a «`FilaVista.detalle` (`DetalleAviso`, nulo sin aviso): `sim`, `na`, `ms`, `dato`, `trabajos` (lista `TrabajoAbierto`)»; `motivo` ya no toma `MS`.
  - §1 Web, último punto: «Tests y fábricas de la web» (los del servidor no comparan nombres).

- [ ] **Step 2: Traer origin y comparar**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q && git log --oneline -3 origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q && git log --oneline -3 origin/main
```
Expected: servidor `7d44ac8` o posterior, web `3559eb6` o posterior. Si ya está dentro la rama de IMEI duplicados, mejor (se parte de ahí). Si algo nuevo toca `EstadoUnidad`, `AvisoImei`, `VistaFilas`, `FilaVista`, `filas.ts` o `CeldaImei.tsx`, leerlo antes de seguir.

- [ ] **Step 3: Worktrees**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git worktree add -b dev_pedidos_celda_imei /c/Users/dev/Documents/wt/servidor-pedidos-celda-imei origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git worktree add -b dev_pedidos_celda_imei /c/Users/dev/Documents/wt/web-pedidos-celda-imei origin/main
cd /c/Users/dev/Documents/wt/web-pedidos-celda-imei && npm ci
```
Expected: dos worktrees nuevos; `npm ci` sin errores.

---

### Task 1: Servidor — `Aceptacion.aceptaColor` / `aceptaGrado` y `EstadoUnidad` nuevo

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/Aceptacion.java`
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/EstadoUnidad.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/EstadoUnidadTest.java`

**Interfaces:**
- Produces: `Aceptacion.aceptaColor(String color): boolean`, `Aceptacion.aceptaGrado(String grado): boolean`;
  `EstadoUnidad.Resultado(String estado, String motivo, String sim, boolean na, boolean ms, String dato)` con constructor
  corto `Resultado(String estado, String motivo)` = `(estado, motivo, null, false, false, null)`.
  Estados: `NO_EN_NSYS`, `NO_CUADRA`, `EN_TALLER`, `NECESITA_TALLER`, `CON_AVISO`, `CUADRA` (los de hoy).
  Motivos: `SKU_DESCONOCIDO`, `MODELO`, `CAPACIDAD`, `GRADO_NO_VENDIBLE`, `COLOR`, `GRADO`, `COLOR_O_GRADO`, `B_POR_A`, o null.
  `sim`: null, `"ESIM"` (el móvil es eSIM y el pedido no) o `"SIM_FISICA"` (al revés).

- [ ] **Step 1: Tests que fallan.** En `EstadoUnidadTest`, añade el helper y los casos nuevos, y cambia las expectativas viejas
  según la tabla de abajo (busca cada `COLOR_O_GRADO`, `C_SIN_REPARAR` y `"MS"` del fichero).

```java
    private static MovilNsys movil(String modeloMaestro, String capacidad, String grade, boolean esEsim) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, capacidad, "Azul", grade, esEsim,
                "Listos", "Listos", EstadoMovil.STOCK, null, null);
    }

    private static EstadoUnidad.Resultado r(String estado, String motivo, String sim, boolean na, boolean ms, String dato) {
        return new EstadoUnidad.Resultado(estado, motivo, sim, na, ms, dato);
    }

    @Test void modeloYCapacidadVanSeparadosConLoQueEsElMovil() {
        assertEquals(r("NO_CUADRA", "MODELO", null, false, false, "14"),
                de(AMAZON_A, movil("14", "128GB", "A", false), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NO_CUADRA", "CAPACIDAD", null, false, false, "256GB"),
                de(AMAZON_A, movil("13", "256GB", "A", false), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
    }

    @Test void laSimDistintaSeApuntaYSeSigueMirando() {
        assertEquals(r("CON_AVISO", null, "ESIM", false, false, null),
                de(AMAZON_A, movil("13", "128GB", "A", true), vale("Azul", "A", "i13128AzulA", null), NINGUNO, Canal.AMAZON),
                "pide SIM física y es eSIM: solo el aviso de SIM");
        assertEquals(r("NECESITA_TALLER", "COLOR", "ESIM", false, false, "Rojo"),
                de(AMAZON_A, movil("13", "128GB", "A", true), vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NO_CUADRA", "CAPACIDAD", "ESIM", false, false, "256GB"),
                de(AMAZON_A, movil("13", "256GB", "A", true), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON),
                "con otra capacidad también se apunta la SIM");
    }

    @Test void unNaSoloMiraElColor() {
        assertEquals(r("CON_AVISO", null, null, true, false, null),
                de(AMAZON_A, movil("13", "128GB", "n/a", false), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "COLOR", null, true, false, "Rosa"),
                de(AMAZON_A, movil("13", "128GB", "n/a", false), vale("Rosa", "n/a", "i13128RosaNA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("CON_AVISO", null, "ESIM", true, false, null),
                de(AMAZON_A, movil("13", "128GB", "n/a", true), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.AMAZON));
    }

    @Test void colorYGradoSeparados() {
        assertEquals(r("NECESITA_TALLER", "COLOR", null, false, false, "Rojo"),
                de(AMAZON_A, movil("13", "A"), vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "GRADO", null, false, false, "B"),
                de(WEB_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.DISTRIBUIDORES));
        assertEquals(r("NECESITA_TALLER", "GRADO", null, false, false, "C"),
                de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), NINGUNO, Canal.AMAZON),
                "un C sin reparar es «grado: C»");
        assertEquals(r("NECESITA_TALLER", "COLOR", null, false, false, "Rojo"),
                de(WEB_A, movil("13", "B"), vale("Rojo", "B", "i13128RojoB", null), NINGUNO, Canal.DISTRIBUIDORES),
                "si fallan los dos, el color");
    }

    @Test void colorYGradoValenPorSeparadoPeroLaCombinacionNoExiste() {
        AceptaSku dos = new AceptaSku(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128X1", "i13128X", null, null),
                AceptaSkuTest.producto("Rojo", "B", "i13128X2", "i13128X", null, null)));
        Aceptacion ac = dos.para(Canal.DISTRIBUIDORES, "i13128X");
        assertEquals(r("NECESITA_TALLER", "COLOR_O_GRADO", null, false, false, null),
                de(ac, movil("13", "A"), vale("Rojo", "A", null, null), NINGUNO, Canal.DISTRIBUIDORES));
    }

    @Test void unMsEsUnaSenalYPuedeIrConOtroMotivo() {
        assertEquals(r("CON_AVISO", null, null, false, true, null),
                de(AMAZON_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "COLOR", null, false, true, "Rojo"),
                de(AMAZON_A, movil("13", "MsA"), vale("Rojo", "MsA", null, null), NINGUNO, Canal.AMAZON));
    }

    @Test void enElTallerNoMiraColorNiGradoNiApuntaNaNiMs() {
        TrabajoAbierto rep = new TrabajoAbierto("REPARACION", "Juan", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        assertEquals(r("EN_TALLER", null, null, false, false, null),
                de(AMAZON_A, movil("13", "128GB", "n/a", false), vale("Rosa", "n/a", null, null), List.of(rep), Canal.AMAZON));
    }
```

  Expectativas viejas que cambian en `losCasosDeLaSpecEnSuOrden` (y en el resto del fichero, igual):

| Hoy | Pasa a |
|---|---|
| `("NO_CUADRA","MODELO")` con «13 mini» | `r("NO_CUADRA","MODELO",null,false,false,"13 mini")` |
| `("NO_CUADRA","GRADO_NO_VENDIBLE")` con Fd / ID | `r(..., "GRADO_NO_VENDIBLE", null,false,false,"Fd")` / `"ID"` |
| `("NECESITA_TALLER","COLOR_O_GRADO")` con ID en web | `r("NECESITA_TALLER","GRADO",null,false,false,"ID")` |
| `("NECESITA_TALLER","C_SIN_REPARAR")` | `r("NECESITA_TALLER","GRADO",null,false,false,"C")` |
| `("CON_AVISO","MS")` (las dos) | `r("CON_AVISO",null,null,false,true,null)` |
| `("NECESITA_TALLER","COLOR_O_GRADO")` con B en web | `r("NECESITA_TALLER","GRADO",null,false,false,"B")` |
| `("NECESITA_TALLER","COLOR_O_GRADO")` con Rojo A | `r("NECESITA_TALLER","COLOR",null,false,false,"Rojo")` |

  Las que siguen igual (NO_EN_NSYS, SKU_DESCONOCIDO, EN_TALLER, CUADRA, B_POR_A) valen con el constructor corto.

- [ ] **Step 2: Comprobar que fallan**

Run (en el worktree del servidor): `mvn -q test -Dtest=EstadoUnidadTest`
Expected: FAIL de compilación (no existe el constructor de 6 argumentos).

- [ ] **Step 3: `Aceptacion`** — añadir debajo de `aceptaColorYGrado`:

```java
    /** ¿Algún producto aceptado tiene ese color? Para decir «color: X» en vez de «otro color o grado». */
    public boolean aceptaColor(String color) {
        return productos.stream().anyMatch(p -> igual(p.color(), color));
    }

    /** ¿Algún producto aceptado tiene ese grado (Calidad Detalle)? */
    public boolean aceptaGrado(String grado) {
        return productos.stream().anyMatch(p -> igual(p.calidadDetalle(), grado));
    }
```

- [ ] **Step 4: `EstadoUnidad`** — sustituir el record y `de` por:

```java
    /** estado y motivo como antes; sim: null, ESIM (el móvil es eSIM y el pedido no) o SIM_FISICA (al revés); na: el grado que
     *  cuenta es n/a; ms: es un Ms; dato: lo que es el móvil para el motivo (modelo, capacidad, color o grado), o null. */
    public record Resultado(String estado, String motivo, String sim, boolean na, boolean ms, String dato) {
        public Resultado(String estado, String motivo) {
            this(estado, motivo, null, false, false, null);
        }
    }

    public static Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos, Canal canal) {
        if (m == null) return new Resultado("NO_EN_NSYS", null);
        if (ac == null) return new Resultado("NO_CUADRA", "SKU_DESCONOCIDO");
        Atributos pide = ac.pide();
        String sim = pide.esEsim() == m.esEsim() ? null : m.esEsim() ? "ESIM" : "SIM_FISICA";
        if (!igual(pide.modelo(), m.modeloMaestro())) return new Resultado("NO_CUADRA", "MODELO", sim, false, false, m.modeloMaestro());
        if (!igual(pide.capacidad(), m.capacidad())) return new Resultado("NO_CUADRA", "CAPACIDAD", sim, false, false, m.capacidad());
        String original = v.grade() == null ? "" : v.grade().strip();
        String grado = original.toUpperCase(Locale.ROOT);
        if (EstadoMovil.NO_VENDIBLES.contains(grado) || (canal.esAmazon() && grado.equals("ID")))
            return new Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE", sim, false, false, original);
        // Con un técnico, el color y el grado aún pueden cambiar: no se miran (ni n/a ni Ms).
        if (trabajos != null && !trabajos.isEmpty()) return new Resultado("EN_TALLER", null, sim, false, false, null);
        if (cSinReparar(v)) return new Resultado("NECESITA_TALLER", "GRADO", sim, false, false, "C");
        if (SkuNa.esNa(v.grade())) {
            // Sin grado: solo se puede comprobar el color.
            if (ac.aceptaColor(v.color())) return new Resultado("CON_AVISO", null, sim, true, false, null);
            return new Resultado("NECESITA_TALLER", "COLOR", sim, true, false, v.color());
        }
        boolean ms = grado.startsWith("MS");
        String base = ms ? original.substring(2) : original;
        // El SKU que vale o, si no está entre los del pedido, el color y el grado: desde la 0.9.29 un n/a lleva SKU (el «SKU sin
        // grado» + «NA»), que no es el SKU web de ninguna fila del maestro.
        boolean aceptado = (v.sku() != null && ac.acepta(v.sku())) || ac.aceptaColorYGrado(v.color(), v.grade())
                || (ms && ac.aceptaColorYGrado(v.color(), base));
        if (aceptado) return ms || sim != null ? new Resultado("CON_AVISO", null, sim, false, ms, null) : new Resultado("CUADRA", null);
        if (v.sku() != null && ac.aceptaBporA(v.sku())) return new Resultado("CON_AVISO", "B_POR_A", sim, false, ms, null);
        boolean colorVale = ac.aceptaColor(v.color());
        boolean gradoVale = ac.aceptaGrado(base);
        if (colorVale && gradoVale) return new Resultado("NECESITA_TALLER", "COLOR_O_GRADO", sim, false, ms, null);
        return colorVale ? new Resultado("NECESITA_TALLER", "GRADO", sim, false, ms, base)
                : new Resultado("NECESITA_TALLER", "COLOR", sim, false, ms, v.color());
    }
```
  Añadir `import com.reparaciones.servidor.nsys.SkuNa;`. Ojo: el caso viejo «Ms aceptado por SKU» (`i13128AzulMs`) daba MS; ahora da
  `CON_AVISO` con `ms = true` y motivo null.

- [ ] **Step 5: Comprobar que pasan**

Run: `mvn -q test -Dtest=EstadoUnidadTest`
Expected: PASS. Si `colorYGradoValenPorSeparadoPeroLaCombinacionNoExiste` falla porque `AceptaSku.para` no junta los dos
productos por `skuWeb`, mirar `AceptaSkuTest.producto` (qué argumento es el SKU web) y ajustar los datos del test, no la lógica.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/Aceptacion.java src/main/java/com/reparaciones/servidor/pedidos/EstadoUnidad.java src/test/java/com/reparaciones/servidor/pedidos/EstadoUnidadTest.java
git commit -m "feat(pedidos): el aviso del IMEI separa color, grado, modelo y capacidad; SIM, n/a y Ms como señales"
```

---

### Task 2: Servidor — `AvisoImei`, `DetalleAviso`, `FilaVista`, `VistaFilas` y `Recomendador`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/DetalleAviso.java`
- Modify: `AvisoImei.java`, `FilaVista.java`, `VistaFilas.java:180-202`, `Recomendador.java:38-46`, `FilasController.java:201` (javadoc), `FilaVista.java:20` (javadoc)
- Modify: `docs/api_contract.md`
- Test: `AvisoImeiTest.java`, `RecomendadorTest.java`, `VistaFilasTest.java`

**Interfaces:**
- Consumes: `EstadoUnidad.Resultado(estado, motivo, sim, na, ms, dato)` de la Task 1.
- Produces:
  - `record DetalleAviso(String sim, boolean na, boolean ms, String dato, List<TrabajoAbierto> trabajos)`.
  - `record AvisoImei(String nivel, String motivo, String skuMovil, String caja, DetalleAviso detalle)` + constructor corto de 4.
  - `FilaVista` gana el último componente `@Schema(nullable = true) DetalleAviso detalle`.
  - JSON: `fila.detalle = { sim: "ESIM"|"SIM_FISICA"|null, na: bool, ms: bool, dato: string|null, trabajos: TrabajoAbierto[] } | null`.
  - `AvisoImei.MOTIVOS_SKU = MODELO, CAPACIDAD, COLOR, GRADO, COLOR_O_GRADO, GRADO_NO_VENDIBLE, SKU_DESCONOCIDO`.

- [ ] **Step 1: Tests que fallan.**

`AvisoImeiTest` (añadir; los `esperado(...)` viejos con `COLOR_O_GRADO`/`MS` se cambian igual que en la Task 1: Rojo → `COLOR`,
B en web → `GRADO`, Ms → nivel `AVISO` con motivo `null`):

```java
    @Test void lasSenalesYElNivel() {
        AvisoImei sim = AvisoImei.de(AMAZON_A, Canal.AMAZON, movilEsim("A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, false);
        assertEquals("AVISO", sim.nivel());
        assertNull(sim.motivo());
        assertEquals("ESIM", sim.detalle().sim());

        AvisoImei na = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "n/a"), vale("Rosa", "n/a", null, null), NINGUNO, false);
        assertEquals("MAL", na.nivel());
        assertEquals("COLOR", na.motivo());
        assertTrue(na.detalle().na());
        assertEquals("Rosa", na.detalle().dato());

        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Ana", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        AvisoImei taller = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), List.of(glass), false);
        assertEquals("AVISO", taller.nivel());
        assertEquals("EN_TALLER", taller.motivo());
        assertEquals(List.of(glass), taller.detalle().trabajos());

        AvisoImei bien = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, false);
        assertEquals("OK", bien.nivel());
        assertNull(bien.detalle(), "sin nada que decir, sin detalle");
    }

    @Test void losMotivosDeSkuNuevosSeValidan() {
        for (String m : List.of("MODELO", "CAPACIDAD", "COLOR", "GRADO", "COLOR_O_GRADO", "GRADO_NO_VENDIBLE", "SKU_DESCONOCIDO"))
            assertTrue(new AvisoImei("MAL", m, null, null).esDeSku(), m);
        assertFalse(new AvisoImei("AVISO", "B_POR_A", null, null).esDeSku());
    }
```
  con el helper `movilEsim(grade)` = el `movil("13", grade)` del fichero con `esEsim = true` (copiar el constructor de `MovilNsys`
  del helper y cambiar el `false` de la posición 16 por `true`).

`RecomendadorTest` (añadir):

```java
    @Test void unaSimDistintaNoSeProponeYUnNaEsHayQueTransformarlo() {
        MovilNsys esim = new MovilNsys("350000000000041", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", D1, null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", true, "Listos", "Listos", EstadoMovil.STOCK, null, null);
        MovilNsys na = movil("350000000000042", "n/a", D1);
        List<Recomendador.Candidato> l = de(AMAZON_A, Canal.AMAZON, List.of(esim, na), SIN_TRABAJOS, NADIE);
        assertEquals(List.of("350000000000042"), imeis(l));
        assertEquals(3, l.get(0).nivel());
    }
```
  (Si `Candidato.nivel()` tiene otro nombre, usar el del record `Recomendador.Candidato`. Ojo: el bucle descarta antes los
  móviles con `m.estado().conGrado(v.grado()) != STOCK`; si un n/a no pasa ese filtro hoy, la lista sale vacía: entonces el n/a no
  se propone ni antes ni ahora, se quita esa mitad del test y se apunta en el informe de la tarea.)

`VistaFilasTest` (añadir, junto a `unaValidacionDeEsteImeiPasaElAvisoDeSkuAOkYGuardaElMotivo`):

```java
    @Test void laFilaVistaTraeElDetalleYUnaValidadaConservaLaSim() {
        MovilNsys esimRojo = new MovilNsys(IMEI2, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", true, "Listos", "Listos",
                EstadoMovil.STOCK, null, null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), conValidacion(fila(2, IMEI2, null), IMEI2)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of());
        nsysConUnMovilDeOtroColor(movil(IMEI1));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI1), esimRojo));

        List<FilaVista> f = vista.filas(null, null, null).filas();

        assertEquals("COLOR", f.get(0).motivo());
        assertEquals("Rojo", f.get(0).detalle().dato());
        assertEquals("OK", f.get(1).nivel());
        assertEquals("COLOR", f.get(1).motivoValidado());
        assertEquals("ESIM", f.get(1).detalle().sim(), "la SIM se sigue viendo aunque el SKU esté validado");
        assertNull(f.get(1).detalle().dato());
    }
```
  y en `unaValidacionDeEsteImeiPasaElAvisoDeSkuAOkYGuardaElMotivo` cambiar `"COLOR_O_GRADO"` por `"COLOR"` (dos veces).
  (`nsysConUnMovilDeOtroColor` ya existe en el fichero: el valedor da Rojo. Si el helper usa otro color, ajustar el `"Rojo"`.)

- [ ] **Step 2: Comprobar que fallan**

Run: `mvn -q test -Dtest='AvisoImeiTest,RecomendadorTest,VistaFilasTest'`
Expected: FAIL de compilación (`detalle()` no existe).

- [ ] **Step 3: `DetalleAviso.java`**

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.TrabajoAbierto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Lo que acompaña al motivo del aviso de un IMEI (spec 2026-11-24 §3). sim: ESIM (el móvil es eSIM y el pedido no), SIM_FISICA
 *  (al revés) o null. na: el grado que cuenta es n/a. ms: es un Ms. dato: lo que es el móvil para el motivo (modelo, capacidad,
 *  color o grado). trabajos: las asignaciones abiertas, solo con motivo EN_TALLER (si no, vacía). */
public record DetalleAviso(@Schema(nullable = true) String sim, boolean na, boolean ms, @Schema(nullable = true) String dato,
                           List<TrabajoAbierto> trabajos) {

    public DetalleAviso {
        trabajos = trabajos == null ? List.of() : List.copyOf(trabajos);
    }

    /** null si no hay nada que decir. */
    static DetalleAviso de(String sim, boolean na, boolean ms, String dato, List<TrabajoAbierto> trabajos) {
        boolean vacio = sim == null && !na && !ms && dato == null && (trabajos == null || trabajos.isEmpty());
        return vacio ? null : new DetalleAviso(sim, na, ms, dato, trabajos);
    }

    /** Lo que queda al validar el SKU: la SIM, el n/a y el Ms se siguen viendo; el dato del motivo no. */
    DetalleAviso validado() {
        return de(sim, na, ms, null, List.of());
    }
}
```

- [ ] **Step 4: `AvisoImei.java`** — record y `de`:

```java
public record AvisoImei(String nivel, String motivo, String skuMovil, String caja, DetalleAviso detalle) {

    public static final String OK = "OK";
    public static final String AVISO = "AVISO";
    public static final String MAL = "MAL";
    /** Los motivos de SKU, los unicos que una persona puede dar por buenos; NO_EN_NSYS y EN_OTRA_FILA se arreglan cambiando el IMEI. */
    public static final java.util.Set<String> MOTIVOS_SKU = java.util.Set.of("MODELO", "CAPACIDAD", "COLOR", "GRADO", "COLOR_O_GRADO",
            "GRADO_NO_VENDIBLE", "SKU_DESCONOCIDO");

    public AvisoImei(String nivel, String motivo, String skuMovil, String caja) {
        this(nivel, motivo, skuMovil, caja, null);
    }

    public boolean esDeSku() {
        return motivo != null && MOTIVOS_SKU.contains(motivo);
    }

    public static AvisoImei de(Aceptacion ac, Canal canal, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos,
                               boolean enOtraFilaViva) {
        if (m == null) return new AvisoImei(MAL, "NO_EN_NSYS", null, null);
        String sku = v.sku();
        String caja = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
        if (enOtraFilaViva) return new AvisoImei(MAL, "EN_OTRA_FILA", sku, caja);
        EstadoUnidad.Resultado r = EstadoUnidad.de(ac, m, v, trabajos, canal);
        boolean enTaller = r.estado().equals("EN_TALLER");
        DetalleAviso d = DetalleAviso.de(r.sim(), r.na(), r.ms(), r.dato(), enTaller ? trabajos : List.of());
        return switch (r.estado()) {
            case "NO_CUADRA", "NECESITA_TALLER" -> new AvisoImei(MAL, r.motivo(), sku, caja, d);
            case "EN_TALLER" -> new AvisoImei(AVISO, "EN_TALLER", sku, caja, d);
            case "CON_AVISO" -> new AvisoImei(AVISO, r.motivo(), sku, caja, d);
            case "CUADRA" -> new AvisoImei(OK, null, sku, caja, d);
            default -> throw new IllegalStateException("Estado de unidad desconocido: " + r.estado());
        };
    }
}
```
  (El `NECESITA_TALLER` ya no se traduce a `COLOR_O_GRADO`: el motivo viene partido de `EstadoUnidad`.)

- [ ] **Step 5: `FilaVista.java`** — añadir `@Schema(nullable = true) DetalleAviso detalle` como **último** componente (detrás de
  `salidaDesde`); añadir un constructor de compatibilidad con los 33 argumentos de hoy que llame al canónico con `detalle = null`
  (los otros tres constructores delegan en ese y no cambian). En el javadoc: «detalle: lo que acompaña al motivo (DetalleAviso);
  nulo sin aviso o sin nada que decir. Validada: conserva sim, na y ms.» y la lista de motivos validables con CAPACIDAD, COLOR y GRADO.

- [ ] **Step 6: `VistaFilas.java`** (bloque de las líneas ~185-202):

```java
            boolean validada = aviso != null && f.validadaImei() != null && f.validadaImei().equals(f.imei()) && aviso.esDeSku();
            String motivoValidado = validada ? aviso.motivo() : null;
            if (validada) aviso = new AvisoImei(AvisoImei.OK, null, aviso.skuMovil(), aviso.caja(),
                    aviso.detalle() == null ? null : aviso.detalle().validado());
```
  y en el `new FilaVista(...)` añadir al final `, aviso == null ? null : aviso.detalle()` (detrás de `salidaDesde`).

- [ ] **Step 7: `Recomendador.java`** — dentro del bucle, tras calcular `r`:

```java
            // Una SIM distinta no se propone (antes era MODELO); un n/a hay que transformarlo aunque el color cuadre.
            if (r.sim() != null) continue;
            int nivel = r.na() ? 3 : switch (r.estado()) {
                case "CUADRA", "CON_AVISO" -> 1;
                case "EN_TALLER" -> ac.acepta(v.sku()) ? 2 : 0;
                case "NECESITA_TALLER" -> 3;
                default -> 0;
            };
```

- [ ] **Step 8: Javadocs y contrato.** `FilasController.java:201`: la lista de motivos de SKU pasa a «MODELO, CAPACIDAD, COLOR, GRADO,
  COLOR_O_GRADO, GRADO_NO_VENDIBLE o SKU_DESCONOCIDO». En `docs/api_contract.md`, en `FilaVista`: el campo `detalle` (tabla de la
  Interfaz de arriba), los motivos nuevos (`CAPACIDAD`, `COLOR`, `GRADO`; `MS` ya no sale, va en `detalle.ms`) y que una validada
  conserva `sim`, `na` y `ms`.

- [ ] **Step 9: Pasar los tests y toda la batería**

Run: `mvn -q test -Dtest='EstadoUnidadTest,AvisoImeiTest,RecomendadorTest,VistaFilasTest,ValidacionControllerTest,FilasControllerTest'` → PASS.
Después `mvn -q test` (suite entera, incluye `OpenApiContractTest`, que deja `target/openapi.json`) → PASS. Si algún test viejo
espera `COLOR_O_GRADO` o `MS` como motivo, cambiarlo según la tabla de la Task 1 (no tocar la lógica).

- [ ] **Step 10: Commit**

```bash
git add -A src docs/api_contract.md
git commit -m "feat(pedidos): la fila trae el detalle del aviso (SIM, n/a, Ms, dato y técnicos); el recomendador no propone SIM distinta"
```

---

### Task 3: Servidor — migración de los nombres de caja

**Files:**
- Create: `sql/renombrar-cajas-nsys.sql`, `sql/renombrar-cajas-nsys-atras.sql`
- Modify: `sql/crear_bd.sql:542-546`, `sql/migracion-nsys-espejo.sql:127-131` y la comprobación HEX (`:146-157`)

- [ ] **Step 1: `sql/renombrar-cajas-nsys.sql`**

```sql
-- Spec 2026-11-24 §1: las cajas de NSYS se llaman «Caja · …» (antes «Taller · …» y «Listos»). Solo datos; la web nueva sale a la vez.
-- Ejecutar con cliente utf8mb4 (el «·» y la tilde). Vuelta atrás: renombrar-cajas-nsys-atras.sql.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE Nsys_ubicacion SET UBICACION = CASE UBICACION
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

UPDATE Nsys_movil SET UBICACION = CASE UBICACION
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

UPDATE Nsys_movil SET UBICACION_ULTIMA = CASE UBICACION_ULTIMA
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE UBICACION_ULTIMA IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

-- En Nsys_evento, DE y A solo son ubicaciones en UBICACION (y A también en ENTRADA); en los demás tipos son grados o productos.
UPDATE Nsys_evento SET DE = CASE DE
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE TIPO = 'UBICACION'
   AND DE IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

UPDATE Nsys_evento SET A = CASE A
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE TIPO IN ('UBICACION', 'ENTRADA')
   AND A IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

COMMIT;

-- Comprobación (no modifica nada): debe dar 7. Bytes UTF-8 de los siete nombres con tilde o «·».
SELECT COUNT(*) AS ubicaciones_utf8_ok
  FROM Nsys_ubicacion
 WHERE HEX(UBICACION) IN (
       '456E20726576697369C3B36E',                          -- En revisión
       '4465766F6C756369C3B36E',                            -- Devolución
       '43616A6120C2B72050756C6972',                        -- Caja · Pulir
       '43616A6120C2B720476C617373',                        -- Caja · Glass
       '43616A6120C2B7205265706172616369C3B36E',            -- Caja · Reparación
       '43616A6120C2B72050616E74616C6C6120726F7461',        -- Caja · Pantalla rota
       '43616A6120C2B7204C6973746F73');                     -- Caja · Listos

-- Quedan viejos (debe dar 0 en las tres).
SELECT COUNT(*) AS moviles_viejos FROM Nsys_movil
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos')
    OR UBICACION_ULTIMA IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');
SELECT COUNT(*) AS eventos_viejos FROM Nsys_evento
 WHERE TIPO IN ('UBICACION', 'ENTRADA')
   AND (DE IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos')
        OR A IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos'));
SELECT COUNT(*) AS ubicaciones_viejas FROM Nsys_ubicacion
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');
```

- [ ] **Step 2: `sql/renombrar-cajas-nsys-atras.sql`**: el mismo fichero con cada `WHEN 'Caja · X' THEN '<viejo>'` al revés
  (`'Caja · Pulir'` → `'Taller · Pulir'`, …, `'Caja · Listos'` → `'Listos'`), los `IN (...)` con los nombres nuevos, y la
  comprobación HEX con los cuatro viejos de `migracion-nsys-espejo.sql:154-157` (debe dar 6) y los tres «quedan» con los nuevos.
  Escribirlo entero (sin «igual que»): es lo que se ejecutaría deprisa en una vuelta atrás.

- [ ] **Step 3: Instalaciones nuevas.** En `sql/crear_bd.sql:542-546` y `sql/migracion-nsys-espejo.sql:127-131`, los cinco nombres
  nuevos (mantener el alineado de columnas). En `migracion-nsys-espejo.sql`, la comprobación HEX pasa a los siete de arriba y su
  comentario dice «debe dar 7».

- [ ] **Step 4: Probar la migración en local** (MariaDB local o el contenedor de pruebas que use el proyecto; si no hay ninguno a
  mano, saltar este paso y decirlo: se prueba en preprod en la Task 8). Cargar unas filas con nombres viejos, ejecutar el fichero,
  comprobar 7 / 0 / 0 / 0; ejecutar el de vuelta atrás y comprobar 6 / 0 / 0 / 0.

- [ ] **Step 5: Commit**

```bash
git add sql/renombrar-cajas-nsys.sql sql/renombrar-cajas-nsys-atras.sql sql/crear_bd.sql sql/migracion-nsys-espejo.sql
git commit -m "feat(nsys): las cajas de NSYS se llaman «Caja · …» (migración y vuelta atrás)"
```

---

### Task 4: Web — tipos regenerados y `textoReparandose`

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts`, `src/shared/api/client.ts`, `src/modules/telefonos/test/fabrica.ts`
- Modify: `src/modules/telefonos/stock/quien.ts`; Test: `src/modules/telefonos/stock/quien.test.ts`

**Interfaces:**
- Produces: `export type DetalleAviso = components['schemas']['DetalleAviso']` en `client.ts`;
  `FilaVista['detalle']: DetalleAviso | null` (la fábrica `fila()` lo pone a `null`);
  `export function textoReparandose(trabajos: TrabajoAbierto[]): string` en `quien.ts` («Juan · glass Ana», «Ana (glass)», «Luis (pulido)»; `''` sin trabajos).

- [ ] **Step 1: Regenerar los tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-pedidos-celda-imei/target/openapi.json /c/Users/dev/Documents/wt/web-pedidos-celda-imei/api/openapi.json
cd /c/Users/dev/Documents/wt/web-pedidos-celda-imei && npm run api:types:offline && git diff --stat
```
Expected: cambian `api/openapi.json` y `schema.d.ts` (aparece `DetalleAviso` y `FilaVista.detalle`). Si el diff trae más cosas que
no son de esta rama, parar y avisar (el `target/openapi.json` sale de la suite de la Task 2).

- [ ] **Step 2:** En `client.ts`, junto a `TrabajoAbierto`: `export type DetalleAviso = components['schemas']['DetalleAviso']`.
  En `fabrica.ts:159` añadir `detalle: null,` antes de `...p`.

- [ ] **Step 3: Test que falla** (en `quien.test.ts`):

```ts
describe('textoReparandose', () => {
  const t = (categoria: string, tecnico: string) => ({ categoria, tecnico, desde: '2026-11-24T09:00:00', entregado: null })
  it('una reparación', () => expect(textoReparandose([t('REPARACION', 'Juan')])).toBe('Juan'))
  it('solo glass', () => expect(textoReparandose([t('GLASS', 'Ana')])).toBe('Ana (glass)'))
  it('reparación y glass, la reparación primero', () =>
    expect(textoReparandose([t('GLASS', 'Ana'), t('REPARACION', 'Juan')])).toBe('Juan · glass Ana'))
  it('pulido', () => expect(textoReparandose([t('PULIDO', 'Luis')])).toBe('Luis (pulido)'))
  it('sin trabajos', () => expect(textoReparandose([])).toBe(''))
})
```

Run: `npx vitest run src/modules/telefonos/stock/quien.test.ts` → FAIL (`textoReparandose` no existe).

- [ ] **Step 4: Implementar** en `quien.ts`: sacar la construcción de `partes` de `quienLoTiene` a una función y exportar el texto.

```ts
/** Las partes de los trabajos, ordenados (reparación, glass, pulido). Sin `hoy`, sin la entrega de la glass. */
function partesDe(trabajos: TrabajoAbierto[], hoy: string | null): ParteQuien[] {
  const hayReparacion = trabajos.some((t) => t.categoria === 'REPARACION')
  return trabajos.map((t): ParteQuien => {
    if (t.categoria === 'GLASS') {
      return { prefijo: hayReparacion ? 'glass' : null, tecnico: t.tecnico, sufijo: hayReparacion ? null : '(glass)', entrega: hoy === null ? null : entrega(t, hoy) }
    }
    return { prefijo: null, tecnico: t.tecnico, sufijo: t.categoria === 'PULIDO' ? '(pulido)' : null, entrega: null }
  })
}

const ordenados = (trabajos: TrabajoAbierto[]) =>
  [...trabajos].sort((a, b) => (ORDEN_CATEGORIA[a.categoria] ?? 9) - (ORDEN_CATEGORIA[b.categoria] ?? 9))

/** Quién lo está reparando, como «Quién lo tiene» sin la entrega: lo usa la celda del IMEI de Pedidos («reparándose · …»). */
export function textoReparandose(trabajos: TrabajoAbierto[]): string {
  return partesDe(ordenados(trabajos), null).map((p) => [p.prefijo, p.tecnico, p.sufijo].filter(Boolean).join(' ')).join(' · ')
}
```
  y en `quienLoTiene`: `const trabajos = ordenados(m.trabajos ?? [])` y `const partes = partesDe(trabajos, hoy)` (borrar el
  `hayReparacion` y el `map` de dentro).

- [ ] **Step 5:** `npx vitest run src/modules/telefonos/stock/quien.test.ts` → PASS; `npm run typecheck` → sin errores.

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/telefonos/test/fabrica.ts src/modules/telefonos/stock/quien.ts src/modules/telefonos/stock/quien.test.ts
git commit -m "feat(pedidos): tipos con el detalle del aviso; textoReparandose compartido con Stock"
```

---

### Task 5: Web — piezas, líneas y fondo de la celda (`filas.ts`)

**Files:**
- Modify: `src/modules/telefonos/pedidos/filas.ts:11-49,197-206`
- Test: `src/modules/telefonos/pedidos/filas.test.ts`

**Interfaces:**
- Consumes: `FilaVista.detalle`, `textoReparandose` (Task 4).
- Produces:
  - `export type ParteAviso = { texto: string; tono: 'naranja' | 'ambar' }`
  - `export function partesAviso(f: FilaVista): ParteAviso[]` — orden: SIM, modelo/capacidad, N/A, MS, el resto.
  - `export function lineasAviso(f: FilaVista): string[]` — las frases largas, una por pieza (title y panel).
  - `claseCelda(f): 'vacia' | 'ok' | 'na' | 'mal'` (desaparece `'aviso'`).
  - `textoMotivo(f)` sigue (frase larga del motivo solo; la usa `textoMotivoValidado`).
  - `MOTIVOS_SKU` con `CAPACIDAD`, `COLOR`, `GRADO`. `TEXTO_NIVEL[2] = 'reparándose, acabará igual'`.

- [ ] **Step 1: Tests que fallan** (en `filas.test.ts`; importar `partesAviso`, `lineasAviso`). Sustituir el `describe('textoMotivo')`
  y el de `claseCelda` por:

```ts
const d = (p: Partial<NonNullable<FilaVista['detalle']>>) => ({ sim: null, na: false, ms: false, dato: null, trabajos: [], ...p })
const IMEI = '350000000000001'
const textos = (f: FilaVista) => partesAviso(f).map((p) => `${p.texto}|${p.tono}`)

describe('partesAviso', () => {
  it.each([
    ['NO_EN_NSYS', null, 'no está en NSYS'],
    ['EN_OTRA_FILA', null, 'en otro pedido'],
    ['SKU_DESCONOCIDO', null, 'SKU desconocido'],
    ['MODELO', '14', 'modelo: 14'],
    ['CAPACIDAD', '256GB', 'capacidad: 256GB'],
    ['COLOR', 'Rosa', 'color: Rosa'],
    ['GRADO', 'B', 'grado: B'],
    ['COLOR_O_GRADO', null, 'otro color o grado'],
    ['GRADO_NO_VENDIBLE', 'F', 'no se vende: F'],
  ])('%s en naranja', (motivo, dato, texto) =>
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo, detalle: dato ? d({ dato }) : null }))).toEqual([`${texto}|naranja`]))

  it('ámbar sin fondo: B por A, MS, reparándose', () => {
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', motivo: 'B_POR_A' }))).toEqual(['B por A|ambar'])
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', motivo: null, detalle: d({ ms: true }) }))).toEqual(['MS|ambar'])
    const trabajos = [{ categoria: 'REPARACION', tecnico: 'Juan', desde: '2026-11-24T09:00:00', entregado: null },
      { categoria: 'GLASS', tecnico: 'Ana', desde: '2026-11-24T09:00:00', entregado: null }]
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', motivo: 'EN_TALLER', detalle: d({ trabajos }) }))).toEqual(['reparándose · Juan · glass Ana|ambar'])
  })

  it('SIM en naranja y N/A en ámbar', () => {
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', detalle: d({ sim: 'ESIM' }) }))).toEqual(['eSIM|naranja'])
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', detalle: d({ sim: 'SIM_FISICA' }) }))).toEqual(['SIM física|naranja'])
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', detalle: d({ na: true }) }))).toEqual(['N/A|ambar'])
  })

  it('varias a la vez, en su orden', () => {
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo: 'COLOR', detalle: d({ sim: 'ESIM', dato: 'Rosa' }) }))).toEqual(['eSIM|naranja', 'color: Rosa|naranja'])
    expect(textos(fila({ imei: IMEI, nivel: 'AVISO', detalle: d({ sim: 'ESIM', na: true }) }))).toEqual(['eSIM|naranja', 'N/A|ambar'])
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo: 'COLOR', detalle: d({ na: true, dato: 'Rosa' }) }))).toEqual(['N/A|ambar', 'color: Rosa|naranja'])
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo: 'COLOR', detalle: d({ ms: true, dato: 'Rojo' }) }))).toEqual(['MS|ambar', 'color: Rojo|naranja'])
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo: 'CAPACIDAD', detalle: d({ sim: 'SIM_FISICA', dato: '128GB' }) }))).toEqual(['SIM física|naranja', 'capacidad: 128GB|naranja'])
  })

  it('sin dato, la palabra sola; motivo desconocido, «revisar»; sin nada, vacío', () => {
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo: 'COLOR' }))).toEqual(['otro color|naranja'])
    expect(textos(fila({ imei: IMEI, nivel: 'MAL', motivo: 'MOTIVO_NUEVO' }))).toEqual(['revisar|naranja'])
    expect(partesAviso(fila({ imei: IMEI, nivel: 'OK' }))).toEqual([])
  })

  it('validada: solo las señales', () =>
    expect(textos(fila({ imei: IMEI, nivel: 'OK', motivo: null, motivoValidado: 'COLOR', detalle: d({ sim: 'ESIM' }) }))).toEqual(['eSIM|naranja']))
})

describe('lineasAviso', () => {
  it('una frase por pieza, con el SKU del móvil', () =>
    expect(lineasAviso(fila({ imei: IMEI, nivel: 'MAL', motivo: 'COLOR', skuMovil: 'i13128RosaA', detalle: d({ sim: 'ESIM', dato: 'Rosa' }) })))
      .toEqual(['el pedido es de SIM física y el móvil es eSIM', 'otro color (Rosa): es i13128RosaA']))
})

describe('claseCelda', () => {
  it('sin IMEI, vacía', () => expect(claseCelda(fila())).toBe('vacia'))
  it('naranja con MAL, aunque sea N/A', () =>
    expect(claseCelda(fila({ imei: IMEI, nivel: 'MAL', detalle: d({ na: true }) }))).toBe('mal'))
  it('amarillo solo con N/A', () => expect(claseCelda(fila({ imei: IMEI, nivel: 'AVISO', detalle: d({ na: true }) }))).toBe('na'))
  it('el resto de avisos, sin fondo', () => {
    expect(claseCelda(fila({ imei: IMEI, nivel: 'AVISO', motivo: 'EN_TALLER' }))).toBe('ok')
    expect(claseCelda(fila({ imei: IMEI, nivel: 'AVISO', detalle: d({ sim: 'ESIM' }) }))).toBe('ok')
    expect(claseCelda(fila({ imei: IMEI, nivel: 'OK' }))).toBe('ok')
  })
})
```
  Y `expect(TEXTO_NIVEL[2]).toBe('reparándose, acabará igual')` donde se compruebe `TEXTO_NIVEL`. Importar `type FilaVista` de `@/shared/api/client`.

Run: `npx vitest run src/modules/telefonos/pedidos/filas.test.ts` → FAIL.

- [ ] **Step 2: Implementar** en `filas.ts` (sustituye `TEXTO_NIVEL`, `textoMotivo`, `MOTIVOS_SKU` y `claseCelda`; importar
  `textoReparandose` de `../stock/quien`):

```ts
export const TEXTO_NIVEL: Record<1 | 2 | 3, string> = {
  1: 'igual que el pedido',
  2: 'reparándose, acabará igual',
  3: 'hay que transformarlo',
}

export type ParteAviso = { texto: string; tono: 'naranja' | 'ambar' }

const PALABRA_SIM: Record<string, string> = { ESIM: 'eSIM', SIM_FISICA: 'SIM física' }
const FRASE_SIM: Record<string, string> = {
  ESIM: 'el pedido es de SIM física y el móvil es eSIM',
  SIM_FISICA: 'el pedido es de eSIM y el móvil es de SIM física',
}
const DE_MODELO = ['MODELO', 'CAPACIDAD']

/** El motivo en corto, como se lee en la celda. */
function corto(f: FilaVista): string {
  const dato = f.detalle?.dato
  const con = (nombre: string, otro: string) => (dato ? `${nombre}: ${dato}` : otro)
  switch (f.motivo) {
    case 'NO_EN_NSYS': return 'no está en NSYS'
    case 'EN_OTRA_FILA': return 'en otro pedido'
    case 'SKU_DESCONOCIDO': return 'SKU desconocido'
    case 'MODELO': return con('modelo', 'otro modelo')
    case 'CAPACIDAD': return con('capacidad', 'otra capacidad')
    case 'COLOR': return con('color', 'otro color')
    case 'GRADO': return con('grado', 'otro grado')
    case 'COLOR_O_GRADO': return 'otro color o grado'
    case 'GRADO_NO_VENDIBLE': return con('no se vende', 'no se vende')
    case 'EN_TALLER': {
      const quien = textoReparandose(f.detalle?.trabajos ?? [])
      return quien ? `reparándose · ${quien}` : 'reparándose'
    }
    case 'B_POR_A': return 'B por A'
    case 'MS': return 'MS' // servidor anterior
    default: return 'revisar'
  }
}

/** Las piezas de la celda del IMEI (spec 2026-11-24 §2): SIM, modelo o capacidad, N/A, MS y el resto. Naranja: la SIM y lo que no
 *  cuadra; ámbar: lo demás. */
export function partesAviso(f: FilaVista): ParteAviso[] {
  const d = f.detalle
  const p: ParteAviso[] = []
  const tonoMotivo = f.nivel === 'MAL' ? 'naranja' : 'ambar'
  if (d?.sim) p.push({ texto: PALABRA_SIM[d.sim] ?? d.sim, tono: 'naranja' })
  if (f.motivo && DE_MODELO.includes(f.motivo)) p.push({ texto: corto(f), tono: tonoMotivo })
  if (d?.na) p.push({ texto: 'N/A', tono: 'ambar' })
  if (d?.ms) p.push({ texto: 'MS', tono: 'ambar' })
  if (f.motivo && !DE_MODELO.includes(f.motivo)) p.push({ texto: corto(f), tono: tonoMotivo })
  return p
}

/** El motivo en una frase, con lo que es el móvil (`skuMovil`). Sin motivo, null. */
export function textoMotivo(f: FilaVista): string | null {
  if (!f.motivo) return null
  const es = f.skuMovil ? `: es ${f.skuMovil}` : ''
  const dato = f.detalle?.dato ? ` (${f.detalle.dato})` : ''
  switch (f.motivo) {
    case 'NO_EN_NSYS': return 'no está en NSYS'
    case 'EN_OTRA_FILA': return 'ya está en otro pedido'
    case 'SKU_DESCONOCIDO': return 'el maestro no conoce este SKU'
    case 'MODELO': return `otro modelo${dato}${es}`
    case 'CAPACIDAD': return `otra capacidad${dato}${es}`
    case 'COLOR': return `otro color${dato}${es}`
    case 'GRADO': return `otro grado${dato}${es}`
    case 'COLOR_O_GRADO': return `el color y el grado valen por separado, pero el pedido no tiene esa combinación${es}`
    case 'GRADO_NO_VENDIBLE': return `ese grado no se vende${dato}`
    case 'EN_TALLER': return `se está reparando${f.detalle?.trabajos?.length ? ` (${textoReparandose(f.detalle.trabajos)})` : ''}`
    case 'B_POR_A': return `B por A${es}`
    case 'MS': return 'es un Ms'
    default: return 'revisar'
  }
}

/** Una frase por pieza, en el orden de la celda: para el title y el panel. */
export function lineasAviso(f: FilaVista): string[] {
  const d = f.detalle
  const l: string[] = []
  if (d?.sim) l.push(FRASE_SIM[d.sim] ?? d.sim)
  const motivo = textoMotivo(f)
  if (motivo && f.motivo && DE_MODELO.includes(f.motivo)) l.push(motivo)
  if (d?.na) l.push('grado n/a: NSYS no le da grado')
  if (d?.ms) l.push('es un Ms')
  if (motivo && !(f.motivo && DE_MODELO.includes(f.motivo))) l.push(motivo)
  return l
}

export const MOTIVOS_SKU = ['MODELO', 'CAPACIDAD', 'COLOR', 'GRADO', 'COLOR_O_GRADO', 'GRADO_NO_VENDIBLE', 'SKU_DESCONOCIDO']
```
```ts
/** Cómo se pinta la celda del IMEI: vacía; naranja si no cuadra; amarillo claro si es N/A (el único amarillo); si no, sin fondo. */
export function claseCelda(f: FilaVista): 'vacia' | 'ok' | 'na' | 'mal' {
  if (!f.imei) return 'vacia'
  if (f.nivel === 'MAL') return 'mal'
  if (f.detalle?.na) return 'na'
  return 'ok'
}
```
  (El test de `lineasAviso` espera exactamente «el pedido es de SIM física y el móvil es eSIM» y «otro color (Rosa): es i13128RosaA».)

- [ ] **Step 3:** `npx vitest run src/modules/telefonos/pedidos/filas.test.ts` → PASS. `npm run typecheck` fallará en
  `CeldaImei.tsx`/`AyudaPedidos.tsx` por `'aviso'`: se arregla en la Task 6.

- [ ] **Step 4: Commit**

```bash
git add src/modules/telefonos/pedidos/filas.ts src/modules/telefonos/pedidos/filas.test.ts
git commit -m "feat(pedidos): piezas cortas del aviso del IMEI y fondo solo para naranja y N/A"
```

---

### Task 6: Web — celda, panel y ayuda

**Files:**
- Modify: `src/modules/telefonos/pedidos/CeldaImei.tsx:12-60`, `PanelFila.tsx:149`, `AyudaPedidos.tsx:43-44`
- Test: `PedidosAyuda.test.tsx`, `PedidosPanel.test.tsx` y el test que pinta la celda (buscar con
  `grep -rln "data-imei\|CeldaImei" src --include=*.test.tsx`)

- [ ] **Step 1: Test que falla** — en el test que pinta la tabla de Pedidos (el que salga del grep), añadir:

```tsx
it('la celda enseña las piezas del aviso y el fondo según el peor', async () => {
  // Montar la tabla igual que los demás tests del fichero, con estas dos filas:
  const filas = [
    fila({ idFila: 1, imei: '350000000000001', nivel: 'MAL', motivo: 'COLOR', skuMovil: 'i13128RosaA',
      detalle: { sim: 'ESIM', na: false, ms: false, dato: 'Rosa', trabajos: [] } }),
    fila({ idFila: 2, imei: '350000000000002', nivel: 'AVISO', motivo: null,
      detalle: { sim: null, na: true, ms: false, dato: null, trabajos: [] } }),
  ]
  // …render con `filas`…
  const [c1, c2] = screen.getAllByRole('cell').filter((c) => c.hasAttribute('data-imei'))
  expect(c1).toHaveTextContent('350000000000001eSIM · color: Rosa')
  expect(c1.className).toContain('bg-[#FFE0B2]')
  expect(c2).toHaveTextContent('350000000000002N/A')
  expect(c2.className).toContain('bg-form-agotado-bg')
})
```
  (Usar el mismo montaje — `render`, proveedores y mocks de la API — que ya tiene el fichero; si `getAllByRole('cell')` no
  encuentra los `td`, usar `container.querySelectorAll('td[data-imei]')`.)

  En `PedidosAyuda.test.tsx`: cambiar la expectativa del texto «IMEI en ámbar: vale, pero con un aviso.» por los textos del Step 4.

Run: `npx vitest run <ese fichero> src/modules/telefonos/pedidos/PedidosAyuda.test.tsx` → FAIL.

- [ ] **Step 2: `CeldaImei.tsx`**

```tsx
import { Fragment, useEffect, useRef, useState } from 'react'
// …
import { CLASE_TONO, pedidoVisible, TEXTO_NIVEL, claseCelda, estaValidada, lineasAviso, partesAviso, textoMotivoValidado, tonoFila, type ParteAviso } from './filas'

export const FONDO: Record<string, string> = {
  na: 'bg-form-agotado-bg',
  mal: 'bg-[#FFE0B2]',
}
export const TONO_PARTE: Record<ParteAviso['tono'], string> = {
  naranja: 'text-[#B45309]',
  ambar: 'text-form-agotado-text',
}
```
  En `CeldaImei`, sustituir `motivo` por:

```tsx
  // Validada: el motivo de SKU ya no se escribe (sale «validado» y queda en el title); la SIM, el N/A y el MS sí.
  const partes = partesAviso(fila)
  const lineas = lineasAviso(fila)
```
  el `title` del botón por
  `title={fallo ? textoFallo : [fila.imei ?? 'pulsa para poner el IMEI…', avisoValidado, ...lineas, partes.length === 0 && !avisoValidado ? fila.caja : null].filter(Boolean).join('\n')}`,
  el `span` del motivo por

```tsx
      {!fallo && partes.length > 0 && (
        <span className="min-w-0 truncate font-sans text-[11px] font-bold">
          {partes.map((p, i) => (
            <Fragment key={i}>
              {i > 0 && <span className="font-normal text-[#b9a27a]"> · </span>}
              <span className={TONO_PARTE[p.tono]}>{p.texto}</span>
            </Fragment>
          ))}
        </span>
      )}
```
  y la caja por `{!fallo && partes.length === 0 && !validada && fila.caja && …}` (mismo `span` de hoy).

- [ ] **Step 3: `PanelFila.tsx:149`** — `const motivo = lineasAviso(f).join(' · ') || null` (importar `lineasAviso` en vez de
  `textoMotivo` si ya no se usa en otro sitio del fichero).

- [ ] **Step 4: `AyudaPedidos.tsx:43-44`** — sustituir las dos líneas por:

```tsx
          <Linea muestra={celda(FONDO.mal, '350000000000002')}>IMEI en naranja: no cuadra con el pedido (cuenta en «no cuadran»). Al lado, en corto, qué no cuadra: «color: Rosa», «grado: B», «modelo: 14»…</Linea>
          <Linea muestra={celda(FONDO.na, '350000000000001')}>IMEI en amarillo claro: grado N/A (NSYS no le da grado). Vale, no cuenta en «no cuadran».</Linea>
          <Linea>Sin fondo pero con una palabra al lado: «eSIM» o «SIM física» en naranja (la SIM no es la del pedido) o, en ámbar, «MS», «B por A» o «reparándose · técnico». Ninguna cuenta en «no cuadran». Si pasan varias cosas, salen todas separadas por «·».</Linea>
```
  y donde la ayuda nombre «Listos» o «Taller», poner los nombres nuevos.

- [ ] **Step 5: Pasar todo**

Run: `npm run typecheck && npm run lint && npx vitest run src/modules/telefonos` → PASS. Si algún test viejo busca «es un Ms»,
«en el taller», «otro color o grado: es …» o el fondo amarillo de `aviso`, cambiarlo a los textos nuevos de la Task 5.

- [ ] **Step 6: Commit**

```bash
git add src/modules/telefonos/pedidos
git commit -m "feat(pedidos): la celda del IMEI enseña todas las piezas del aviso; panel y ayuda con los textos nuevos"
```

---

### Task 7: Web — cajas «Caja · …» en Stock y en el rastro

**Files:**
- Modify: `src/shared/lib/telefonoNsys.ts:39-47`, `src/modules/telefonos/stock/quien.ts:66-78`, `src/modules/telefonos/stock/modelo.ts:101-102`, `src/modules/taller/imeis/rastro.ts:44-45`, `src/modules/taller/imeis/PanelRastro.tsx:9`
- Modify (datos de tests): `src/modules/telefonos/test/fabrica.ts`, `src/modules/taller/test/fabrica.ts`, y los `*.test.ts(x)` que usan los nombres viejos.

**Interfaces:**
- Produces: `export const CAJA_LISTOS = 'Caja · Listos'` y `export const esCajaDeTaller = (u: string | null | undefined): boolean` en `telefonoNsys.ts` (empieza por `Caja · ` y no es `Caja · Listos`).

- [ ] **Step 1: Test que falla** (en `src/shared/lib/telefonoNsys.test.ts`):

```ts
describe('cajas', () => {
  it('las cajas del taller son las «Caja · …» menos Listos', () => {
    for (const u of ['Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota']) expect(esCajaDeTaller(u)).toBe(true)
    for (const u of ['Caja · Listos', 'En revisión', 'Taller · Pulir', null]) expect(esCajaDeTaller(u)).toBe(false)
  })
  it('cada caja con su color', () => {
    expect(clasesUbicacion('Caja · Listos')).toBe('bg-fila-recibido-bg text-fila-recibido-brd')
    expect(clasesUbicacion('Caja · Glass')).toBe('bg-tipo-glass-bg text-tipo-glass-text')
  })
})
```
Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts` → FAIL.

- [ ] **Step 2: Implementar.**
  - `telefonoNsys.ts`: claves de `CLASES_UBICACION` → `'Caja · Pulir'`, `'Caja · Glass'`, `'Caja · Reparación'`, `'Caja · Pantalla rota'`, `'Caja · Listos'` (mismas clases); y

```ts
export const CAJA_LISTOS = 'Caja · Listos'
/** Una caja de taller de NSYS (Pulir, Glass, Reparación, Pantalla rota): no Listos. */
export const esCajaDeTaller = (u: string | null | undefined): boolean => !!u && u.startsWith('Caja · ') && u !== CAJA_LISTOS
```
  - `quien.ts:66` → `const enCaja = m.estado !== 'DE_PEDIDO' && esCajaDeTaller(m.ubicacion) && trabajos.length === 0`;
    `:68` → `m.ubicacion === CAJA_LISTOS`; `:78` → `'NSYS dice "Caja · Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?'`.
  - `modelo.ts:101-102` → `if (u === CAJA_LISTOS) r.listos++` / `else if (esCajaDeTaller(u)) r.taller++`.
  - `rastro.ts:44-45` → `if (u === CAJA_LISTOS) …` / `if (esCajaDeTaller(u)) …`.
  - `PanelRastro.tsx:9` → leyenda `'Cajas del taller'` y `'Caja · Listos'`.

- [ ] **Step 3: Datos de tests.** Sustituir en `src` (solo web) los nombres viejos por los nuevos:

```bash
cd /c/Users/dev/Documents/wt/web-pedidos-celda-imei && grep -rln "Taller · \|'Listos'\|\"Listos\"\|ubicacion: 'Listos'\|a: 'Listos'" src
```
  Editar cada fichero con Edit (no con sed: hay `·` y tildes, ver la memoria de backslashes/Bash): `'Taller · X'` → `'Caja · X'`,
  `'Listos'` → `'Caja · Listos'` **solo** donde sea una ubicación (no en textos como «Listos para enviar» si los hubiera).

- [ ] **Step 4:** `npm run typecheck && npm run lint && npm test` → todo PASS.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat(stock): las cajas de NSYS se ven como «Caja · …»"
```

---

### Task 8: Versión, changelog, doc del módulo, preprod y cierre

**Files:** web `package.json`, `package-lock.json`, `CHANGELOG.md`; raíz `docs/modulos/pedidos.md`.

- [ ] **Step 1: Preguntar al usuario el número de versión** (la siguiente a la de IMEI duplicados) y si esa rama ya está en
  `origin/main`. Si lo está: `git fetch && git merge origin/main` en los dos worktrees, resolver conflictos y repetir
  `mvn -q test` y `npm run typecheck && npm run lint && npm test`.

- [ ] **Step 2: Versión y changelog.** `npm version <X.Y.Z> --no-git-tag-version` en la web. En `CHANGELOG.md`, arriba:

```markdown
## [X.Y.Z] - AAAA-MM-DD — Pedidos: la celda del IMEI dice todo en corto; cajas «Caja · …»

- Celda del IMEI: todas las piezas a la vez, separadas por «·»: «eSIM» / «SIM física» (naranja), «modelo: 14», «capacidad: 256GB», «color: Rosa», «grado: B», «no se vende: F», «SKU desconocido», «en otro pedido» (naranja); «N/A», «MS», «B por A», «reparándose · técnico» (ámbar).
- Fondo naranja solo si no cuadra; amarillo claro solo con N/A; el resto sin fondo. La SIM distinta, el N/A, el MS, el B por A y «reparándose» no cuentan en «no cuadran».
- Recomendados: «reparándose, acabará igual».
- Stock y rastro: las cajas de NSYS se llaman «Caja · Pulir», «Caja · Glass», «Caja · Reparación», «Caja · Pantalla rota» y «Caja · Listos». Necesita la migración `renombrar-cajas-nsys.sql`.
```
  Commit: `git commit -am "chore: versión X.Y.Z y changelog"`.

- [ ] **Step 3: Doc del módulo** (raíz) `docs/modulos/pedidos.md`: en la parte de la celda del IMEI, la tabla de textos y colores de
  la spec §2; en la de Stock, los nombres «Caja · …». Commit en la raíz: `git commit -m "docs: Pedidos, celda del IMEI en piezas y cajas «Caja · …»"`.

- [ ] **Step 4: Preprod — preparar el guion para el usuario** (Claude no hace SSH). Orden: migración (midiendo el tiempo) → servidor →
  web, todo seguido, porque la web vieja no reconoce «Caja · …» y la nueva no reconoce «Taller · …». Bloque:

```bash
ssh preprod
hostname
# Comprobar que es preprod antes de seguir.
cd <directorio del proyecto en preprod, el de Apuntes/despliegue_preprod.md>
time docker exec -i <contenedor mariadb> mariadb --default-character-set=utf8mb4 -uroot -p"$MARIADB_ROOT_PASSWORD" <base> < gestion-reparaciones-servidor/sql/renombrar-cajas-nsys.sql
```
  Los nombres reales (directorio, contenedor y base) se copian de `Documents/Apuntes/despliegue_preprod.md`, no se escriben en el repo.
  Esperado: 7 / 0 / 0 / 0 y el `real` del `time`. Avisar de que el build de después tarda y no saca salida durante un rato.
  Si `Nsys_evento` tarda más de unos 30 s, decírselo al usuario: en prod iría fuera de horario.

- [ ] **Step 5: Revisión en preprod con datos reales** (usuario o E2E con datos sintéticos por API): en Pedidos, buscar una fila
  con móvil n/a, una con eSIM, una de un móvil asignado a un técnico y una de otro color; en Stock, que «Quién lo tiene» sigue
  diciendo «⚠ En caja, sin asignar» en las cajas del taller y que el resumen cuenta Listos y Taller igual que antes de migrar.

- [ ] **Step 6: Cierre.** Usar `superpowers:finishing-a-development-branch`. **Merge, push, tag y prod solo con el OK del usuario**;
  en prod, `/usr/local/sbin/backup-erp.sh` antes de la migración. Actualizar la memoria del proyecto con el estado.
