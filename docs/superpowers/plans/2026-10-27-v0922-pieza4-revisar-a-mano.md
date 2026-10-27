# 0.9.22 pieza 4 — «Revisar a mano» rehecho: «Sin grado en NSYS», batería recordada y los que han vuelto · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En «Revisar a mano», una fila «Sin grado en NSYS» por cada móvil sin grado que ha pasado por el taller desde el 22/10; la batería puesta en un móvil sin grado se recuerda y se aplica sola cuando tiene letra (por NSYS o por un chasis); fuera los vendidos y las bajas salvo los que han vuelto (también en Color y Grado); los grados F, Fd, RMA e ID se ignoran, A+ cuenta como A y lo que no se usa cuenta como «n/a».

**Architecture:** Servidor: `GradoTaller` clasifica el grado en tres grupos; el aviso «Grado sin calcular» deja de salir en la lista y queda como registro de la batería recordada (`BateriaRecordada`, leída por `TallerDAO`); `EfectoTaller` la aplica cuando un chasis da letra; `PorPasarNsys` calcula al pedir las tarjetas las filas «Sin grado» (`TarjetasNsys.sinGrado`, trabajos desde el 22/10 12:49) y la vuelta de cada vendido o de baja (`VueltaNsys`), y tras cada importación buena usa las baterías recordadas de los móviles que ya tienen letra. Web: el contrato regenerado, la fila «Sin grado», la línea ámbar de los que han vuelto, la pastilla fuera, el aviso de la plantilla, el texto de «Corregir…» y el del rastro. Sin tablas ni migración.

**Tech Stack:** Java 17, Spring Boot 3.3, JdbcTemplate, MariaDB 11, JUnit 5 + Mockito; React 19 + TypeScript, TanStack Query 5, openapi-fetch, Vitest + Testing Library + MSW, Playwright (E2E contra preprod).

**Spec:** `docs/superpowers/specs/2026-10-27-v0922-revisar-a-mano-design.md` (raíz). Sin prototipo (decisión del usuario).

## Global Constraints

- **Base: la pieza 3 cerrada.** Esta pieza va **encima** de la pieza 3 (`dev_v0922_descartar_nsys`, la implementa otra sesión). No se empieza hasta que su registro `/c/Users/dev/Documents/ProgramaReparaciones/.superpowers/sdd/v0922-pieza3/progress.md` diga que las **10 tareas y la revisión final** están hechas (o esa sesión lo confirme). Mientras, esperar sin tocar nada.
- **Ramas y worktrees:** servidor `/c/Users/dev/Documents/wt/servidor-0922-revisar` y web `/c/Users/dev/Documents/wt/web-0922-revisar`, rama `dev_v0922_revisar_nsys` creada desde la punta **local** de `dev_v0922_descartar_nsys` de cada repositorio. **No tocar** los worktrees ni las ramas de la pieza 3 (`wt/*-0922-descartar`, `dev_v0922_descartar_nsys`), ni los clones normales (`ProgramaReparaciones/gestion-reparaciones-*`), ni otros worktrees. Si la pieza 3 recibe commits después de crear las ramas, **no** fusionarlos: se avisa en el informe final y lo decide el usuario.
- **Commits** en castellano **sin tildes**, sin línea Co-Authored-By. **Nunca** push, merge ni tags (los hace el usuario o el controlador con su OK). En la raíz hay ficheros sin seguimiento que no son de nadie (`.codegraph/`, `node_modules/`, PDF): `git add` solo los de la tarea.
- **Maven** por Bash (no PowerShell): `cd /c/Users/dev/Documents/wt/servidor-0922-revisar && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH" && mvn -q -o test -Dtest=ClaseA,ClaseB` — **comas** en `-Dtest`. Sin `-Dtest`: la suite entera (anotar el número de pruebas al empezar).
- **Web:** `cd /c/Users/dev/Documents/wt/web-0922-revisar && npx vitest run <ficheros>`, `npx tsc -b`, `npm run lint`, `npm run check`. `npm ci` una vez al crear el worktree. Con otras sesiones usando la máquina, un timeout de 5 s fuera de `telefonos` no es de esta pieza: repetir ese fichero solo.
- Java/TS con `\\` (regex) **solo con Write/Edit**, nunca con heredoc o sed de Bash (Bash en Windows junta las barras dobles).
- **Datos de prueba inventados** (repos públicos): IMEIs `3500000000000xx`, «Técnico 2», «Supertécnico 1», R… inventadas.
- **Fecha de corte:** `TarjetasNsys.DESDE_SIN_GRADO = LocalDateTime.of(2026, 10, 22, 12, 49)` (hora de Madrid; `Reparacion.FECHA_ASIG` va en UTC y se convierte como en `NsysDAO.utcAMadrid`).
- **Contrato:** los campos nuevos van **al final** de sus registros, con constructor de compatibilidad sin ellos; las rutas no cambian. Esquema nuevo `FilaSinGradoNsys`; `TarjetasPorPasar.sinGrado`; `vendidoEn`, `bajaEn`, `vueltoDesde` en `FilaColorNsys`, `FilaGradoNsys` y `FilaRevisarNsys`; `bateriaIdRep`, `bateriaNueva`, `bateriaEn` en `OpcionesTaller`.
- **Textos exactos** (spec §5.2, §5.3 y §7):
  - log del servidor: `NSYS: baterías recordadas usadas: N (cambios de grado: M)`; si falla: `NSYS: no se pudieron usar las baterías recordadas tras la importación`;
  - rastro: tipo `TALLER_BATERIA_USADA`; en la web «Batería resuelta por el ERP · grado <grado> (reparación <R…>)»;
  - etiqueta de la fila: **«Sin grado en NSYS»**; «NSYS: n/a» (el grado de NSYS tal cual; «NSYS: —» si está vacío);
  - «Último trabajo: dd/mm/aaaa · Reparación (R…)» (o «Glass», «Pulido»);
  - «Batería nueva del dd/mm (R…)» / «Batería reutilizada del dd/mm (R…)»;
  - «Qué hacer» de la fila: «Ponle grado en NSYS: la fila se va sola al importarlo (o «Corregir…» si ya lo sabes)»;
  - vuelta: «Vendido en NSYS el dd/mm/aaaa · el taller lo tiene desde el dd/mm/aaaa: haz antes la devolución en NSYS» y «De baja en NSYS desde el dd/mm/aaaa · el taller lo tiene desde el dd/mm/aaaa: míralo antes en NSYS»;
  - aviso de la plantilla de color: «N han vuelto y no van: haz antes la devolución en NSYS» (con 1: «1 ha vuelto y no va: haz antes la devolución en NSYS»);
  - «Corregir…»: «Lleva batería nueva del dd/mm (R…): si quieres el 100, escríbelo» / «Lleva batería reutilizada del dd/mm (R…): sin el 100»;
  - ayuda de «Revisar a mano»: «Sin grado en NSYS: móviles sin grado que han pasado por el taller desde el 22/10; se van solos cuando NSYS los gradúa o con «Corregir…». Los avisos de color y de deshacer se van con «Revisado» o al corregirlos.»;
  - explicación de la tarjeta «Revisar a mano»: «Sin grado en NSYS o lo que el ERP no supo calcular: corrígelo en su ficha.»;
  - fuera la pastilla «se va con «Revisado» (queda quién y cuándo) o al corregirlo».
- El límite de 15 s por petición no se sube. Las E2E, **solo contra preprod** (se niegan sin el letrero) y con un móvil **sintético** del guion de preprod, nunca uno real.

---

## Mapa de ficheros

**Servidor** (`S/` = `src/main/java/com/reparaciones/servidor/nsys/`, `T/` = `src/test/java/com/reparaciones/servidor/nsys/`):

| Fichero | Qué |
|---|---|
| `S/GradoTaller.java` + `T/GradoTallerTest.java` | los tres grupos; A+ como A; «Ms» sin mirar mayúsculas |
| `S/AvisoTaller.java`, `S/BateriaRecordada.java` (nuevo), `S/TallerDAO.java` + `T/TallerDAOTest.java`, `T/BateriaRecordadaTest.java` (nuevo) | «Revisar a mano» sin «Grado sin calcular»; leer y usar las baterías recordadas |
| `S/EfectoTaller.java`, `S/TallerReparaciones.java` + sus pruebas | el chasis aplica la batería recordada |
| `S/TallerNsys.java`, `S/OpcionesTaller.java` + `T/TallerNsysTest.java` | «Corregir…» la da por usada; el diálogo la trae |
| `S/NsysDAO.java` + `T/NsysDAOTest.java`, `S/VueltaNsys.java` (nuevo) + `T/VueltaNsysTest.java`, `T/TallerDePrueba.java` | trabajos con su fecha; los que han vuelto |
| `S/FilaColorNsys.java`, `S/FilaGradoNsys.java`, `S/FilaRevisarNsys.java`, `S/FilaSinGradoNsys.java` (nuevo), `S/TarjetasPorPasar.java` | contrato |
| `S/TarjetasNsys.java` + `T/TarjetasNsysTest.java`, `S/PorPasarNsys.java` + `T/PorPasarNsysTest.java` | vueltas, «Sin grado», plantilla, baterías tras importar |
| `S/ImportadorNsys.java` + `T/ImportadorNsysTest.java` | el enganche tras la importación |
| `S/Rastro.java` + `T/RastroTest.java`, `…/OpenApiContractTest.java` | `TALLER_BATERIA_USADA`; contrato |

**Web** (raíz del worktree web):

| Fichero | Qué |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts`, `src/shared/api/client.ts` | contrato regenerado y alias |
| `src/modules/telefonos/test/fabrica.ts`, `src/modules/taller/test/fabrica.ts`, `src/test/server.ts` | datos de prueba |
| `src/shared/lib/telefonoNsys.ts` + test | textos de la batería recordada |
| `src/modules/telefonos/stock/porPasar.ts` + `porPasar.test.ts` | cuentas, textos, plantilla |
| `src/modules/telefonos/stock/ListasPorPasar.tsx`, `PorPasarNsys.tsx` + `PorPasarNsys.test.tsx` | filas «Sin grado», línea ámbar, pastilla |
| `src/modules/taller/imeis/CorregirTallerDialog.tsx`, `rastro.ts` + sus pruebas | el texto de la batería y el rastro |
| `tests/e2e/revisar-a-mano.spec.ts` (nuevo) | E2E en preprod |
| `CHANGELOG.md` (web), `docs/novedades/NOVEDADES-v0.9.22.md` (raíz) | documentación |

---

### Task 1: Los grados en tres grupos (`GradoTaller`)

**Files:**
- Modify: `S/GradoTaller.java` (entero)
- Test: `T/GradoTallerTest.java`, `T/EfectoTallerTest.java` (líneas ~92-101 y ~190-207), `T/TallerReparacionesTest.java` (líneas ~93-99)

**Interfaces:**
- Produces: `GradoTaller.Grupo { CON_FORMA, IGNORADO, COMO_NA }`; `public static GradoTaller.Grupo grupo(String grado)`; `public static Optional<String> conPieza(String grado, PiezaTaller.Tipo tipo, boolean nueva)` (misma firma; vacío **solo** si el grado es «como n/a» y la pieza no es un chasis).

- [ ] **Step 0: Esperar a la pieza 3 y crear los worktrees**

Comprobar el registro de la pieza 3:

```bash
tail -5 /c/Users/dev/Documents/ProgramaReparaciones/.superpowers/sdd/v0922-pieza3/progress.md
```
Tiene que decir que las tareas 1-10 están completas y que la revisión final está hecha. Si no, esperar (y volver a mirar cada 20-30 min) sin tocar nada.

Con la pieza 3 cerrada:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git worktree add -b dev_v0922_revisar_nsys /c/Users/dev/Documents/wt/servidor-0922-revisar dev_v0922_descartar_nsys && git -C /c/Users/dev/Documents/wt/servidor-0922-revisar log --oneline -1
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git worktree add -b dev_v0922_revisar_nsys /c/Users/dev/Documents/wt/web-0922-revisar dev_v0922_descartar_nsys && git -C /c/Users/dev/Documents/wt/web-0922-revisar log --oneline -1
cd /c/Users/dev/Documents/wt/web-0922-revisar && npm ci
```
Expected: cada `log` enseña la última de la pieza 3 (la misma que `git -C /c/Users/dev/Documents/wt/servidor-0922-descartar log --oneline -1` y la del worktree web de la pieza 3). Anotar las dos en el registro de esta pieza (`/c/Users/dev/Documents/ProgramaReparaciones/.superpowers/sdd/v0922-pieza4/progress.md`). Correr la suite del servidor una vez y anotar cuántas pruebas pasan.

- [ ] **Step 1: Las pruebas de los grupos (fallan: no existe `grupo`)**

En `T/GradoTallerTest.java`, añadir al `@CsvSource` de `conLaFormaDeNsys` (antes de `// sin espacios a los lados`):

```java
            // 0.9.22 pieza 4: A+ cuenta como A (y se escribe A); «Ms» sin mirar mayúsculas (MSA- es MsA-)
            "A+,      CHASIS,  true,  A",
            "A+,      BATERIA, true,  A100",
            "A+100,   BATERIA, false, A",
            "MsA+,    BATERIA, true,  MsA100",
            "MSA-,    BATERIA, true,  MsA-100",
            "msB,     CHASIS,  false, MsA",
            "A+,      TAPA,    true,  A+",
```

Sustituir el test `sinLaFormaNoCambiaConNingunaPieza` entero por estos dos:

```java
    @ParameterizedTest(name = "«{0}» se ignora: ninguna pieza lo cambia")
    @CsvSource({"ID", "F", "Fd", "FD", "RMA", "rma", "' id '"})
    void losIgnoradosNoCambianConNingunaPieza(String grado) {
        for (PiezaTaller.Tipo tipo : PiezaTaller.Tipo.values()) {
            assertEquals(Optional.of(grado.strip()), GradoTaller.conPieza(grado, tipo, true), grado + " " + tipo);
            assertEquals(Optional.of(grado.strip()), GradoTaller.conPieza(grado, tipo, false), grado + " " + tipo);
        }
    }

    @ParameterizedTest(name = "«{0}» cuenta como n/a: el chasis pone A; la tapa y la batería no pueden")
    @CsvSource({"G", "P", "P+", "AG", "A-P+", "CG", "100", "a", "MsMsA", "—"})
    void loQueNoSeUsaCuentaComoNa(String grado) {
        assertEquals(Optional.of("A"), GradoTaller.conPieza(grado, PiezaTaller.Tipo.CHASIS, true));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, true));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, false));
        assertEquals(Optional.empty(), GradoTaller.conPieza(grado, PiezaTaller.Tipo.TAPA, true));
    }

    @ParameterizedTest(name = "grupo de «{0}»: {1}")
    @CsvSource({
            "A,      CON_FORMA", "MsC100, CON_FORMA", "A+100,  CON_FORMA", "MSA-,   CON_FORMA", "' B ',  CON_FORMA",
            "ID,     IGNORADO",  "Fd,     IGNORADO",  "rma,    IGNORADO",
            "n/a,    COMO_NA",   "N/A,    COMO_NA",   "'',     COMO_NA",   "AG,     COMO_NA", "A-P+,   COMO_NA", "—, COMO_NA"
    })
    void elGrupoDeCadaGrado(String grado, GradoTaller.Grupo grupo) {
        assertEquals(grupo, GradoTaller.grupo(grado));
    }

    @Test void sinGradoNingunoEsComoNa() {
        assertEquals(GradoTaller.Grupo.COMO_NA, GradoTaller.grupo(null));
    }
```

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=GradoTallerTest` (con el `cd` y el `export` de Global Constraints)
Expected: no compila (`grupo` y `Grupo` no existen).

- [ ] **Step 3: `GradoTaller` con los tres grupos**

`S/GradoTaller.java` entero (con Write):

```java
package com.reparaciones.servidor.nsys;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** El grado de un móvil con una pieza (spec 0.9.21 «SKU del taller», pieza 2, §2.1, y 0.9.22 pieza 4 §3). El grado se escribe
 *  como en NSYS, [Ms]<letra>[100], y cada pieza toca su parte: chasis, la letra pasa a A (Ms y 100 se quedan); batería nueva,
 *  con 100; reutilizada, sin 100. La tapa trasera no lo toca (0.9.21.1). «Ms» nunca cambia solo. Desde la pieza 4, tres grupos:
 *  con forma (A+ cuenta como A y, al escribir, se escribe A; «Ms» sin mirar mayúsculas y escrito «Ms»), ignorados (F, Fd, FD,
 *  RMA, ID: ninguna pieza los cambia) y como «n/a» (n/a, vacío y cualquier otro código: el chasis lo deja en A; la batería no
 *  puede). Es puro. */
public final class GradoTaller {

    /** Los tres grupos de grado (spec pieza 4 §3). */
    public enum Grupo { CON_FORMA, IGNORADO, COMO_NA }

    /** La forma del grado de NSYS: «A+» y «A-» antes que «A» para leerlo entero; «Ms» sin mirar mayúsculas. */
    static final Pattern FORMA = Pattern.compile("((?i:ms))?(A\\+|A-|A|B|C)(100)?");
    /** Los grados que se siguen usando y el taller no toca (sin mirar mayúsculas): no vendible, desguace, bloqueo de operador y
     *  falla de Face ID. */
    static final Set<String> IGNORADOS = Set.of("F", "FD", "RMA", "ID");

    private GradoTaller() {}

    /** El grupo de un grado de NSYS (o del que vale con el taller); null cuenta como «n/a». */
    public static Grupo grupo(String grado) {
        if (grado == null) return Grupo.COMO_NA;
        String g = grado.strip();
        if (FORMA.matcher(g).matches()) return Grupo.CON_FORMA;
        if (IGNORADOS.contains(g.toUpperCase(Locale.ROOT))) return Grupo.IGNORADO;
        return Grupo.COMO_NA;
    }

    /** El grado tras la pieza: con forma, la cuenta de siempre (escrito como NSYS: «Ms», la letra con A+ como A y el «100»); un
     *  ignorado o una tapa, el mismo (sin espacios a los lados); como «n/a», «A» con un chasis y vacío con lo demás (no hay letra
     *  a la que poner o quitar el 100). */
    public static Optional<String> conPieza(String grado, PiezaTaller.Tipo tipo, boolean nueva) {
        Grupo g = grupo(grado);
        if (g == Grupo.COMO_NA) return tipo == PiezaTaller.Tipo.CHASIS ? Optional.of("A") : Optional.empty();
        if (g == Grupo.IGNORADO || tipo == PiezaTaller.Tipo.TAPA) return Optional.of(grado.strip());
        Matcher f = FORMA.matcher(grado.strip());
        if (!f.matches()) throw new IllegalStateException("Grado con forma que no casa: " + grado);
        String ms = f.group(1) == null ? "" : "Ms";
        String letra = tipo == PiezaTaller.Tipo.CHASIS || "A+".equals(f.group(2)) ? "A" : f.group(2);
        boolean cien = tipo == PiezaTaller.Tipo.BATERIA ? nueva : f.group(3) != null;
        return Optional.of(ms + letra + (cien ? "100" : ""));
    }

    /** NSYS aún no le ha puesto grado: «n/a» o vacío. */
    static boolean sinGrado(String grado) {
        return grado == null || grado.isBlank() || grado.strip().equalsIgnoreCase("n/a");
    }
}
```

- [ ] **Step 4: Las pruebas del efecto que cambian con los grupos**

En `T/EfectoTallerTest.java`, sustituir `unGradoSinLaFormaDeNsysNoCambiaConNingunaPiezaYSeAvisa` entero por:

```java
    @Test void unGradoIgnoradoNoCambiaConNingunaPiezaYNoAvisa() {
        MovilNsys id = movil(IMEI, "Azul", "ID");
        EfectoTaller.Resultado r = EfectoTaller.calcular(id, "Azul", "ID",
                List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
        assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), r.pasos().get(0).nuevos(), "el color sí");
        assertNull(r.pasos().get(0).gradoSinCalcular());
        assertTrue(r.pasos().get(1).nuevos().isEmpty());
        assertNull(r.pasos().get(1).gradoSinCalcular());
        assertEquals("ID", r.gradoDespues());
        assertFalse(r.gradoSinCalcular());
    }
```

Sustituir `unChasisSobreIdGOPCambiaElColorPeroNoElGradoYAvisa` y `laBateriaReutilizadaSobreGradoIdAvisaDelGrado` enteros por:

```java
    @Test void unChasisSobreUnIgnoradoCambiaElColorPeroNoElGradoYNoAvisa() {
        for (String grado : List.of("ID", "F", "Fd", "RMA")) {
            MovilNsys m = movil(IMEI, "Azul", grado);
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, "Azul", grado,
                    List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
            assertEquals(List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE)), paso(r, "R1").nuevos(), grado);
            assertEquals(grado, r.gradoDespues());
            assertNull(paso(r, "R1").gradoSinCalcular(), grado);
            assertNull(paso(r, "R2").gradoSinCalcular(), grado);
        }
    }

    @Test void unChasisSobreLoQueNoSeUsaOSobreAMasPoneLaAYLaBateriaEl100() {
        for (String grado : List.of("G", "P", "AG", "A+")) {
            MovilNsys m = movil(IMEI, "Azul", grado);
            EfectoTaller.Resultado r = EfectoTaller.calcular(m, "Azul", grado,
                    List.of(pieza("R1", "chai13red", CHASIS, true), pieza("R2", "bati13", BATERIA, true)), COLORES);
            assertEquals("A100", r.gradoDespues(), grado);
            assertFalse(r.gradoSinCalcular(), grado);
        }
    }

    @Test void laBateriaSobreUnIgnoradoNoAvisa() {
        MovilNsys id = movil(IMEI, "Azul", "ID");
        EfectoTaller.Resultado r = EfectoTaller.calcular(id, "Azul", "ID", List.of(pieza("R1", "bati13", BATERIA, false)), COLORES);
        assertTrue(r.pasos().get(0).nuevos().isEmpty());
        assertNull(r.pasos().get(0).gradoSinCalcular());
    }
```

En `T/TallerReparacionesTest.java`, sustituir `unGradoQueNoSePuedeCalcularSeAvisaYNoCambia` entero por:

```java
    @Test void unaBateriaEnUnComoNaSeAvisaYNoCambia() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false)), 8);
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_GRADO, null, GRADO, "n/a", null,
                "batería bati13 nueva"), 8, AHORA);
    }

    @Test void unaBateriaEnUnGradoIgnoradoNiCambiaNiAvisa() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "ID")));
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false)), 8);
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller, never()).avisar(any(), anyInt(), any());
    }
```

- [ ] **Step 5: Correr las pruebas del grado y del taller**

Run: `mvn -q -o test -Dtest=GradoTallerTest,EfectoTallerTest,TallerReparacionesTest,SkuTallerControllerTest,LoQueValeTest`
Expected: todo en verde. Si otra prueba de esas clases esperaba aviso con «ID», «F», «A+», «G» o «P», adaptarla a la regla nueva (ignorado: ni cambio ni aviso; A+ y lo que no se usa: como A o como «n/a») y apuntarlo en el informe.

- [ ] **Step 6: La suite entera**

Run: `mvn -q -o test`
Expected: verde (el número anotado en el Step 0 más las pruebas nuevas). Una prueba roja fuera de estas clases que dependa del grado «ID»/«A+»: adaptarla igual y apuntarlo.

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/GradoTaller.java src/test/java/com/reparaciones/servidor/nsys/GradoTallerTest.java src/test/java/com/reparaciones/servidor/nsys/EfectoTallerTest.java src/test/java/com/reparaciones/servidor/nsys/TallerReparacionesTest.java && git commit -m "feat: grados en tres grupos, A+ como A, F/Fd/RMA/ID ignorados y lo que no se usa como n/a (0.9.22, pieza 4)"
```

---

### Task 2: «Revisar a mano» sin «Grado sin calcular» y las baterías recordadas (`TallerDAO`)

**Files:**
- Create: `S/BateriaRecordada.java`, `T/BateriaRecordadaTest.java`
- Modify: `S/AvisoTaller.java` (`POR_REVISAR`, líneas ~26-27), `S/TallerDAO.java` (`SQL_POR_REVISAR`, `SQL_REVISAR`, `guardar` de 6 argumentos y métodos nuevos al final)
- Test: `T/TallerDAOTest.java`

**Interfaces:**
- Consumes: `GradoTaller.grupo(String)`, `PiezaTaller.Tipo.deSku(String)` (Task 1).
- Produces:
  - `public record BateriaRecordada(int idAviso, String imeiSn, String idRep, boolean nueva, String motivo, Integer idUsu, LocalDateTime fecha)` con `public static Optional<BateriaRecordada> ultima(Collection<BateriaRecordada> deUnImei)`;
  - `TallerDAO`: `public List<BateriaRecordada> bateriasRecordadas()`, `public List<BateriaRecordada> bateriasRecordadas(Collection<String> imeis)`, `public int usarBaterias(Collection<Integer> idAvisos, String gradoQueQueda, LocalDateTime ahora)`;
  - `TallerDAO.guardar(String imeiSn, List<Nuevo> nuevos, String idRep, Integer idUsu, String motivo, LocalDateTime ahora)` (idUsu pasa a `Integer`, puede ser null);
  - `AvisoTaller.POR_REVISAR` = SIN_COLOR y SIN_DESHACER.

- [ ] **Step 1: Las pruebas (fallan: no existen)**

`T/BateriaRecordadaTest.java` (con Write):

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La batería recordada que vale de un IMEI (spec 0.9.22 pieza 4 §5.1): la más reciente, por fecha y luego por id. */
class BateriaRecordadaTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 26, 17, 28);

    private static BateriaRecordada b(int id, LocalDateTime fecha, boolean nueva) {
        return new BateriaRecordada(id, "350000000000001", "R20261026_" + id, nueva, "batería bati13 nueva", 8, fecha);
    }

    @Test void vaLaMasRecienteYAIgualHoraLaDeMayorId() {
        assertEquals(Optional.of(b(3, HORA, false)), BateriaRecordada.ultima(List.of(b(1, HORA.minusDays(1), true), b(3, HORA, false),
                b(2, HORA, true))));
        assertEquals(Optional.empty(), BateriaRecordada.ultima(List.of()));
    }
}
```

En `T/TallerDAOTest.java`, añadir al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: baterías recordadas (spec §5)

    @Test void revisarAManoYaNoLlevaGradoSinCalcular() {
        assertTrue(TallerDAO.SQL_POR_REVISAR.contains("v.TIPO IN ('SIN_COLOR', 'SIN_DESHACER')"), TallerDAO.SQL_POR_REVISAR);
        assertTrue(TallerDAO.SQL_REVISAR.contains("TIPO IN ('SIN_COLOR', 'SIN_DESHACER')"), TallerDAO.SQL_REVISAR);
        assertEquals(Set.of(AvisoTaller.Tipo.SIN_COLOR, AvisoTaller.Tipo.SIN_DESHACER), AvisoTaller.POR_REVISAR);
    }

    @Test void lasCandidatasSonLosAvisosDeGradoSinRevisarYLaPiezaSeLeeAparte() {
        assertEquals("SELECT v.ID_AVISO, v.IMEI_SN, v.ID_REP, v.DE, v.MOTIVO, v.ID_USU, v.FECHA FROM Taller_aviso v"
                + " WHERE v.TIPO = 'SIN_GRADO' AND v.REVISADO_EN IS NULL", TallerDAO.SQL_BATERIAS);
        assertTrue(TallerDAO.SQL_PIEZAS.contains("rc.ES_SOLICITUD = 0 AND rc.ID_REP IN ("), TallerDAO.SQL_PIEZAS);
        assertTrue(TallerDAO.SQL_USAR.startsWith("UPDATE Taller_aviso SET REVISADO_EN = ?, A = ? WHERE TIPO = 'SIN_GRADO'"
                + " AND REVISADO_EN IS NULL AND ID_AVISO IN ("), TallerDAO.SQL_USAR);
    }

    @Test void unaBateriaRecordadaEsUnaBateriaQueSigueExistiendoEnUnComoNa() {
        LocalDateTime hora = HOY.minusDays(1);
        List<TallerDAO.Candidata> candidatas = List.of(
                new TallerDAO.Candidata(1, IMEI, "R1", "n/a", "batería bati13 nueva", 8, hora),
                new TallerDAO.Candidata(2, IMEI, "R2", "—", "batería bati13 reutilizada", 8, hora),
                new TallerDAO.Candidata(3, IMEI, "R3", "ID", "batería bati13 nueva", 8, hora),        // ignorado: no
                new TallerDAO.Candidata(4, IMEI, "R4", "n/a", "tapa tapai13red nueva", 8, hora),       // tapa: no
                new TallerDAO.Candidata(5, IMEI, "R5", "n/a", "batería bati13 nueva", null, hora));    // R… borrada: no
        Map<String, TallerDAO.PiezaLeida> piezas = Map.of("R1", new TallerDAO.PiezaLeida("bati13", false),
                "R2", new TallerDAO.PiezaLeida("bati13", true), "R3", new TallerDAO.PiezaLeida("bati13", false),
                "R4", new TallerDAO.PiezaLeida("tapai13red", false));
        assertEquals(List.of(new BateriaRecordada(1, IMEI, "R1", true, "batería bati13 nueva", 8, hora),
                new BateriaRecordada(2, IMEI, "R2", false, "batería bati13 reutilizada", 8, hora)), TallerDAO.baterias(candidatas, piezas));
    }

    @Test void lasBateriasDeUnosImeisVanEnTandasYSinImeisNoConsulta() {
        assertTrue(dao.bateriasRecordadas(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
        assertTrue(dao.bateriasRecordadas(List.of(IMEI)).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query")).map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(List.of(TallerDAO.SQL_BATERIAS + " AND v.IMEI_SN IN (?)" + TallerDAO.ORDEN_AVISOS), sql,
                "sin candidatas no lee las piezas");
    }

    @Test void usarLasBateriasLasRevisaSinQuienConElGradoQueQueda() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(2);
        assertEquals(2, dao.usarBaterias(List.of(40, 41, 40), "A100", HOY));
        verify(jdbc).update(TallerDAO.SQL_USAR + "?, ?)", Timestamp.valueOf(HOY), "A100", 40, 41);
        clearInvocations(jdbc);
        assertEquals(0, dao.usarBaterias(List.of(), "A", HOY));
        verifyNoInteractions(jdbc);
    }
```
(Añadir `import java.util.Map;` si no está.)

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=BateriaRecordadaTest,TallerDAOTest`
Expected: no compila (`BateriaRecordada`, `SQL_BATERIAS`, `Candidata`… no existen).

- [ ] **Step 3: `BateriaRecordada`**

`S/BateriaRecordada.java` (con Write):

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Una batería puesta en un móvil «como n/a» (spec 0.9.22 pieza 4 §5.1): su aviso «Grado sin calcular» sin revisar, su R…, si es
 *  nueva (la pieza de hoy de su R…), el motivo («batería bati13 nueva»), quién la guardó (null si ya no existe) y cuándo (hora de
 *  Madrid). Se aplica cuando el grado del móvil tiene letra. */
public record BateriaRecordada(int idAviso, String imeiSn, String idRep, boolean nueva, String motivo, Integer idUsu,
                               LocalDateTime fecha) {

    private static final Comparator<BateriaRecordada> ORDEN = Comparator.comparing(BateriaRecordada::fecha)
            .thenComparingInt(BateriaRecordada::idAviso);

    /** La que vale de las de un IMEI: la más reciente (por fecha y luego por id); vacío sin ninguna. */
    public static Optional<BateriaRecordada> ultima(Collection<BateriaRecordada> deUnImei) {
        return deUnImei.stream().max(ORDEN);
    }
}
```

- [ ] **Step 4: `AvisoTaller.POR_REVISAR` y `TallerDAO`**

En `S/AvisoTaller.java`, sustituir:

```java
    /** Los que van a «Revisar a mano» (spec 0.9.22 §3.5); DESHECHO solo informa y se queda en el rastro. */
    public static final Set<Tipo> POR_REVISAR = Set.of(Tipo.SIN_COLOR, Tipo.SIN_GRADO, Tipo.SIN_DESHACER);
```
por:
```java
    /** Los que van a «Revisar a mano» (spec 0.9.22 §3.5); DESHECHO solo informa y se queda en el rastro. Desde la pieza 4,
     *  SIN_GRADO tampoco: es el registro de la batería recordada (BateriaRecordada) y el móvil sale como «Sin grado en NSYS». */
    public static final Set<Tipo> POR_REVISAR = Set.of(Tipo.SIN_COLOR, Tipo.SIN_DESHACER);
```

En `S/TallerDAO.java`:

1. En `SQL_POR_REVISAR`, cambiar `" WHERE v.TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER') AND v.REVISADO_EN IS NULL"` por `" WHERE v.TIPO IN ('SIN_COLOR', 'SIN_DESHACER') AND v.REVISADO_EN IS NULL"`, y su javadoc «los SIN_COLOR, SIN_GRADO y SIN_DESHACER» por «los SIN_COLOR y SIN_DESHACER (pieza 4: SIN_GRADO ya no)».
2. En `SQL_REVISAR`, cambiar `" AND REVISADO_EN IS NULL AND TIPO IN ('SIN_COLOR', 'SIN_GRADO', 'SIN_DESHACER')"` por `" AND REVISADO_EN IS NULL AND TIPO IN ('SIN_COLOR', 'SIN_DESHACER')"`.
3. En el `guardar` de 6 argumentos, cambiar `int idUsu` por `Integer idUsu` (la firma queda `public void guardar(String imeiSn, List<Nuevo> nuevos, String idRep, Integer idUsu, String motivo, LocalDateTime ahora)`; el de 5 argumentos sigue con `int` y lo llama igual). Añadir al javadoc: «{@code idUsu}: null si quien guardó ya no existe (pieza 4: la batería recordada)».
4. Debajo de `SQL_PIEZA`, las constantes:

```java
    /** 0.9.22 pieza 4 (§5.1): los avisos «Grado sin calcular» sin revisar, candidatos a batería recordada. */
    static final String SQL_BATERIAS = "SELECT v.ID_AVISO, v.IMEI_SN, v.ID_REP, v.DE, v.MOTIVO, v.ID_USU, v.FECHA FROM Taller_aviso v"
            + " WHERE v.TIPO = 'SIN_GRADO' AND v.REVISADO_EN IS NULL";
    static final String ORDEN_AVISOS = " ORDER BY v.FECHA, v.ID_AVISO";
    /** La pieza con uso real de unas reparaciones (Reparacion_componente y Componente: tablas viejas, la misma intercalación;
     *  Taller_aviso no se junta con ellas). Se completa con las marcas del IN. */
    static final String SQL_PIEZAS = "SELECT rc.ID_REP, rc.ES_REUTILIZADO, c.TIPO FROM Reparacion_componente rc"
            + " JOIN Componente c ON c.ID_COM = rc.ID_COM WHERE rc.ES_SOLICITUD = 0 AND rc.ID_REP IN (";
    /** Dar por usadas unas baterías recordadas (§5.3): revisadas por el ERP (sin quién) con el grado que quedó. Se completa con
     *  las marcas del IN. */
    static final String SQL_USAR = "UPDATE Taller_aviso SET REVISADO_EN = ?, A = ? WHERE TIPO = 'SIN_GRADO' AND REVISADO_EN IS NULL"
            + " AND ID_AVISO IN (";
```

5. Antes de `static AvisoTaller aviso(ResultSet rs, int n)`, los métodos:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: baterías recordadas (spec §5)

    /** Un aviso «Grado sin calcular» sin revisar, antes de mirar su pieza. */
    record Candidata(int idAviso, String imeiSn, String idRep, String de, String motivo, Integer idUsu, LocalDateTime fecha) {}

    /** La pieza con uso real de una reparación: su SKU (Componente.TIPO) y si es reutilizada. */
    record PiezaLeida(String sku, boolean reutilizada) {}

    /** Las baterías recordadas de todo el taller (tras cada importación), de la más antigua a la más reciente. */
    public List<BateriaRecordada> bateriasRecordadas() {
        return baterias(jdbc.query(SQL_BATERIAS + ORDEN_AVISOS, TallerDAO::candidata));
    }

    /** Las de estos IMEI (como Taller_aviso.IMEI_SN, que es el del espejo), en tandas de {@value NsysDAO#TANDA_IN}. */
    public List<BateriaRecordada> bateriasRecordadas(Collection<String> imeis) {
        List<String> lista = List.copyOf(new LinkedHashSet<>(imeis));
        if (lista.isEmpty()) return List.of();
        List<Candidata> candidatas = new ArrayList<>();
        for (List<String> tanda : NsysDAO.trozos(lista, NsysDAO.TANDA_IN))
            candidatas.addAll(jdbc.query(SQL_BATERIAS + " AND v.IMEI_SN IN (" + NsysDAO.marcas(tanda.size()) + ")" + ORDEN_AVISOS,
                    TallerDAO::candidata, tanda.toArray()));
        return baterias(candidatas);
    }

    /** Lee las piezas de las candidatas «como n/a» (solo si hay alguna) y se queda con las baterías. */
    private List<BateriaRecordada> baterias(List<Candidata> candidatas) {
        List<String> idReps = candidatas.stream().filter(c -> GradoTaller.grupo(c.de()) == GradoTaller.Grupo.COMO_NA)
                .map(Candidata::idRep).distinct().toList();
        if (idReps.isEmpty()) return List.of();
        Map<String, PiezaLeida> piezas = new HashMap<>();
        for (List<String> tanda : NsysDAO.trozos(idReps, NsysDAO.TANDA_IN))
            for (Map.Entry<String, PiezaLeida> e : jdbc.query(SQL_PIEZAS + NsysDAO.marcas(tanda.size()) + ")",
                    (rs, n) -> Map.entry(rs.getString("ID_REP"), new PiezaLeida(rs.getString("TIPO"), rs.getBoolean("ES_REUTILIZADO"))),
                    tanda.toArray()))
                piezas.put(e.getKey(), e.getValue());
        return baterias(candidatas, piezas);
    }

    /** De las candidatas, las que son una batería que sigue existiendo (su R… tiene pieza) puesta en un grado «como n/a», en su
     *  orden. Nueva = la pieza de hoy no es reutilizada. Es puro. */
    static List<BateriaRecordada> baterias(List<Candidata> candidatas, Map<String, PiezaLeida> piezas) {
        List<BateriaRecordada> baterias = new ArrayList<>();
        for (Candidata c : candidatas) {
            PiezaLeida p = piezas.get(c.idRep());
            if (p == null || GradoTaller.grupo(c.de()) != GradoTaller.Grupo.COMO_NA) continue;
            if (PiezaTaller.Tipo.deSku(p.sku()).orElse(null) != PiezaTaller.Tipo.BATERIA) continue;
            baterias.add(new BateriaRecordada(c.idAviso(), c.imeiSn(), c.idRep(), !p.reutilizada(), c.motivo(), c.idUsu(), c.fecha()));
        }
        return baterias;
    }

    /** Da por usadas estas baterías recordadas con el grado que quedó (§5.3). 0 si ya lo estaban o no hay ninguna. */
    public int usarBaterias(Collection<Integer> idAvisos, String gradoQueQueda, LocalDateTime ahora) {
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(idAvisos));
        if (ids.isEmpty()) return 0;
        Object[] args = new Object[ids.size() + 2];
        args[0] = Timestamp.valueOf(ahora);
        args[1] = gradoQueQueda;
        for (int i = 0; i < ids.size(); i++) args[i + 2] = ids.get(i);
        return jdbc.update(SQL_USAR + NsysDAO.marcas(ids.size()) + ")", args);
    }

    static Candidata candidata(ResultSet rs, int n) throws SQLException {
        return new Candidata(rs.getInt("ID_AVISO"), rs.getString("IMEI_SN"), rs.getString("ID_REP"), rs.getString("DE"),
                rs.getString("MOTIVO"), entero(rs, "ID_USU"), rs.getTimestamp("FECHA").toLocalDateTime());
    }
```

En el test `unaBateriaRecordadaEsUnaBateriaQueSigueExistiendoEnUnComoNa`, la candidata 5 tiene R… sin pieza (borrada): por eso no sale.

- [ ] **Step 5: Correr y la suite**

Run: `mvn -q -o test -Dtest=BateriaRecordadaTest,TallerDAOTest,PorPasarNsysTest,TallerReparacionesTest,TallerNsysTest`
Expected: verde salvo, quizá, `PorPasarNsysTest`: las pruebas que usan el aviso `sinGrado()` como fila de «Revisar a mano». Arreglarlas así: en `T/PorPasarNsysTest.java`, sustituir el helper `sinGrado()` por

```java
    private static AvisoTaller sinColor() {
        return new AvisoTaller(5, IMEI, "R20261024_19", AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow", null,
                "chasis chai13yellow nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }
```
cambiar todas sus llamadas `sinGrado()` por `sinColor()` y, en `revisadoApuntaQuienYCuandoYLoDejaEnElRegistro`, el detalle esperado por `"IMEI: 350000000000001, SIN_COLOR de R20261024_19"`. Añadir también:

```java
    @Test void revisadoDeUnGradoSinCalcularDa400PorqueYaNoEsDeRevisarAMano() {
        when(tallerDao.avisoPorId(6)).thenReturn(Optional.of(new AvisoTaller(6, IMEI, "R20261024_20", AvisoTaller.Tipo.SIN_GRADO,
                null, GRADO, "n/a", null, "batería bati13 nueva", 8, "Técnico 2", AHORA.minusDays(1))));
        when(tallerDao.revisar(6, 7, AHORA)).thenReturn(0);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.revisado(6, 7)));
        verifyNoInteractions(registro);
    }
```
Después: `mvn -q -o test` (suite entera, verde).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/BateriaRecordada.java src/main/java/com/reparaciones/servidor/nsys/AvisoTaller.java src/main/java/com/reparaciones/servidor/nsys/TallerDAO.java src/test/java/com/reparaciones/servidor/nsys/BateriaRecordadaTest.java src/test/java/com/reparaciones/servidor/nsys/TallerDAOTest.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java && git commit -m "feat: Revisar a mano sin Grado sin calcular y baterias recordadas en un movil sin grado (0.9.22, pieza 4)"
```

---

### Task 3: El chasis aplica la batería recordada (`EfectoTaller`, `TallerReparaciones`)

**Files:**
- Modify: `S/EfectoTaller.java` (`Resultado` y `calcular`), `S/TallerReparaciones.java` (`aplicar` y `previsualizar`)
- Test: `T/EfectoTallerTest.java`, `T/TallerReparacionesTest.java`

**Interfaces:**
- Consumes: `BateriaRecordada`, `TallerDAO.bateriasRecordadas(Collection<String>)`, `TallerDAO.usarBaterias(...)` (Task 2); `GradoTaller.grupo` (Task 1).
- Produces: `EfectoTaller.Resultado` gana al final `boolean recordadaUsada`; `EfectoTaller.calcular(MovilNsys, String, String, List<PiezaTaller>, Function<String, Optional<String>>, BateriaRecordada recordada)` (la de 5 argumentos sigue y pasa null).

- [ ] **Step 1: Las pruebas (fallan)**

En `T/EfectoTallerTest.java`, añadir al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: la batería recordada (spec §5.2)

    private static BateriaRecordada recordada(boolean nueva) {
        return new BateriaRecordada(40, IMEI, "R0", nueva, nueva ? "batería bati13 nueva" : "batería bati13 reutilizada", 8, null);
    }

    @Test void unChasisSobreUnComoNaAplicaLaBateriaRecordada() {
        MovilNsys na = movil(IMEI, "Azul", "n/a");
        EfectoTaller.Resultado nueva = EfectoTaller.calcular(na, "Azul", "n/a", List.of(pieza("R1", "chai13blue", CHASIS, false)),
                COLORES, recordada(true));
        assertEquals(List.of(new TallerDAO.Nuevo(GRADO, "A100", "n/a", VIGENTE)), nueva.pasos().get(0).nuevos());
        assertTrue(nueva.recordadaUsada());
        EfectoTaller.Resultado reutilizada = EfectoTaller.calcular(na, "Azul", "n/a",
                List.of(pieza("R1", "chai13blue", CHASIS, false)), COLORES, recordada(false));
        assertEquals("A", reutilizada.gradoDespues());
        assertTrue(reutilizada.recordadaUsada());
    }

    @Test void conLetraOIgnoradoLaBateriaRecordadaNoSeUsa() {
        for (String grado : List.of("B", "ID")) {
            EfectoTaller.Resultado r = EfectoTaller.calcular(movil(IMEI, "Azul", grado), "Azul", grado,
                    List.of(pieza("R1", "chai13blue", CHASIS, false)), COLORES, recordada(true));
            assertFalse(r.recordadaUsada(), grado);
        }
        EfectoTaller.Resultado b = EfectoTaller.calcular(movil(IMEI, "Azul", "B"), "Azul", "B",
                List.of(pieza("R1", "chai13blue", CHASIS, false)), COLORES, recordada(true));
        assertEquals("A", b.gradoDespues(), "con letra, el chasis hace lo de siempre");
    }

    @Test void unaBateriaDelMismoGuardadoVaDespuesDeLaRecordada() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(movil(IMEI, "Azul", "n/a"), "Azul", "n/a",
                List.of(pieza("R2", "bati13", BATERIA, false), pieza("R1", "chai13blue", CHASIS, false)), COLORES, recordada(true));
        assertEquals("A", r.gradoDespues(), "el chasis pone A100 con la recordada y la batería reutilizada de este guardado le quita el 100");
        assertTrue(r.recordadaUsada());
        assertFalse(r.gradoSinCalcular());
    }

    @Test void sinBateriaRecordadaComoAntes() {
        EfectoTaller.Resultado r = EfectoTaller.calcular(movil(IMEI, "Azul", "n/a"), "Azul", "n/a",
                List.of(pieza("R1", "chai13blue", CHASIS, false)), COLORES);
        assertEquals("A", r.gradoDespues());
        assertFalse(r.recordadaUsada());
    }
```

En `T/TallerReparacionesTest.java`, añadir:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: la batería recordada (spec §5.2)

    @Test void unChasisDespuesDeUnaBateriaNuevaRecordadaQuedaEnA100YLaDaPorUsada() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(
                new BateriaRecordada(40, IMEI, R1, true, "batería bati13 nueva", 8, AHORA.minusSeconds(4))));
        servicio.aplicar(IMEI, List.of(fila(R2, 133, true)), 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "n/a", VIGENTE)), R2, 8,
                "chasis chai13blue reutilizado", AHORA);
        verify(taller).usarBaterias(List.of(40), "A100", AHORA);
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void sinChasisLaBateriaRecordadaSigue() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(
                new BateriaRecordada(40, IMEI, R1, true, "batería bati13 nueva", 8, AHORA.minusSeconds(4))));
        servicio.aplicar(IMEI, List.of(fila(R2, 101, false)), 8);
        verify(taller, never()).usarBaterias(any(), any(), any());
    }
```

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=EfectoTallerTest,TallerReparacionesTest`
Expected: no compila (`recordadaUsada`, `calcular` de 6 argumentos).

- [ ] **Step 3: `EfectoTaller`**

En `S/EfectoTaller.java`:

1. `Resultado` gana el campo al final:

```java
    public record Resultado(String colorAntes, String gradoAntes, String colorDespues, String gradoDespues, List<Paso> pasos,
                            boolean recordadaUsada) {
```
(los métodos `colorSinDeducir()` y `gradoSinCalcular()` no cambian).

2. Sustituir el método `calcular` entero por los dos:

```java
    /** {@code colorVale} y {@code gradoVale}: lo que vale ahora (LoQueVale). {@code colorDeChasis}: SKU → color del maestro
     *  (ColorChasis). Sin batería recordada. */
    public static Resultado calcular(MovilNsys m, String colorVale, String gradoVale, List<PiezaTaller> guardado,
                                     Function<String, Optional<String>> colorDeChasis) {
        return calcular(m, colorVale, gradoVale, guardado, colorDeChasis, null);
    }

    /** Como el de arriba, con la batería recordada del móvil (0.9.22 pieza 4 §5.2; null si no hay): si un chasis da letra a un
     *  grado «como n/a», se le aplica (nueva → con 100, reutilizada → sin 100) y el resultado lo dice ({@code recordadaUsada}). */
    public static Resultado calcular(MovilNsys m, String colorVale, String gradoVale, List<PiezaTaller> guardado,
                                     Function<String, Optional<String>> colorDeChasis, BateriaRecordada recordada) {
        boolean chasisConColor = guardado.stream()
                .anyMatch(p -> p.tipo() == PiezaTaller.Tipo.CHASIS && colorDeChasis.apply(p.sku()).isPresent());
        String color = colorVale;
        String grado = gradoVale;
        boolean usada = false;
        List<Paso> pasos = new ArrayList<>();
        // Primero el chasis (estable: el resto conserva su orden). Los pasos salen en este orden, cada uno con su pieza.
        List<PiezaTaller> enOrden = guardado.stream()
                .sorted(Comparator.comparing((PiezaTaller p) -> p.tipo() != PiezaTaller.Tipo.CHASIS)).toList();
        for (PiezaTaller p : enOrden) {
            List<TallerDAO.Nuevo> nuevos = new ArrayList<>();
            boolean sinColor = false;
            if (p.tipo().conColor() && !(p.tipo() == PiezaTaller.Tipo.TAPA && chasisConColor)) {
                Optional<String> deducido = colorDeChasis.apply(p.sku());
                if (deducido.isEmpty()) sinColor = true;
                else {
                    Optional<TallerDAO.Nuevo> n = cambio(DatoTaller.COLOR, deducido.get(), color, m);
                    if (n.isPresent()) {
                        nuevos.add(n.get());
                        color = n.get().valor();
                    }
                }
            }
            String gradoMal = null;
            if (p.tipo().conGrado()) {
                boolean sinLetra = GradoTaller.grupo(grado) == GradoTaller.Grupo.COMO_NA;
                Optional<String> nuevoGrado = GradoTaller.conPieza(grado, p.tipo(), p.nueva());
                if (nuevoGrado.isPresent() && sinLetra && recordada != null && !usada && p.tipo() == PiezaTaller.Tipo.CHASIS) {
                    nuevoGrado = GradoTaller.conPieza(nuevoGrado.get(), PiezaTaller.Tipo.BATERIA, recordada.nueva());
                    usada = true;
                }
                if (nuevoGrado.isEmpty()) gradoMal = grado == null || grado.isBlank() ? LoQueVale.SIN_DATO : grado;
                else {
                    Optional<TallerDAO.Nuevo> n = cambio(DatoTaller.GRADO, nuevoGrado.get(), grado, m);
                    if (n.isPresent()) {
                        nuevos.add(n.get());
                        grado = n.get().valor();
                    }
                }
            }
            pasos.add(new Paso(p, List.copyOf(nuevos), sinColor, gradoMal));
        }
        return new Resultado(colorVale, gradoVale, color, grado, List.copyOf(pasos), usada);
    }
```

3. En el javadoc de la clase, añadir al final: «Desde la 0.9.22 pieza 4, un chasis que da letra a un grado «como n/a» aplica la batería recordada del móvil.»

- [ ] **Step 4: `TallerReparaciones`**

En `S/TallerReparaciones.java`, en `aplicar`, sustituir:

```java
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m), piezas, colorDeChasis(m));
        LocalDateTime ahora = ahora();
```
por:
```java
        List<CambioTaller> vigentes = tallerDao.vigentes(List.of(m.imeiSn()));
        List<BateriaRecordada> recordadas = tallerDao.bateriasRecordadas(List.of(m.imeiSn()));
        EfectoTaller.Resultado r = EfectoTaller.calcular(m, LoQueVale.valor(vigentes, DatoTaller.COLOR, m),
                LoQueVale.valor(vigentes, DatoTaller.GRADO, m), piezas, colorDeChasis(m),
                BateriaRecordada.ultima(recordadas).orElse(null));
        LocalDateTime ahora = ahora();
```
y, después del bucle `for (EfectoTaller.Paso p : r.pasos()) { … }`, añadir:

```java
        // 0.9.22 pieza 4 (§5.2): si un chasis aplicó la batería recordada, esa y las anteriores del móvil quedan usadas.
        if (r.recordadaUsada())
            tallerDao.usarBaterias(recordadas.stream().map(BateriaRecordada::idAviso).toList(), r.gradoDespues(), ahora);
```
Añadir al javadoc de `aplicar`: «Un chasis que da letra a un móvil sin grado aplica su batería recordada (0.9.22 pieza 4) y la da por usada.»

En `previsualizar`, leer la recordada una vez y pasarla a los dos `calcular` (la segunda solo si la primera no la usó):

```java
        BateriaRecordada recordada = BateriaRecordada.ultima(tallerDao.bateriasRecordadas(List.of(m.imeiSn()))).orElse(null);
```
(justo después de `Function<String, Optional<String>> colorDe = colorDeChasis(m);`); el primer `EfectoTaller.calcular(...)` gana el argumento `recordada` al final y, tras él, `if (r.recordadaUsada()) recordada = null;`; el segundo `EfectoTaller.calcular(m, color, grado, nuevas, colorDe)` pasa a `EfectoTaller.calcular(m, color, grado, nuevas, colorDe, recordada)`. (La variable `recordada` deja de ser efectivamente final: no se usa en lambdas.)

- [ ] **Step 5: Correr y la suite**

Run: `mvn -q -o test -Dtest=EfectoTallerTest,TallerReparacionesTest,SkuTallerControllerTest,GuardadoReparacionTest`
Expected: verde. Después `mvn -q -o test` (verde).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/EfectoTaller.java src/main/java/com/reparaciones/servidor/nsys/TallerReparaciones.java src/test/java/com/reparaciones/servidor/nsys/EfectoTallerTest.java src/test/java/com/reparaciones/servidor/nsys/TallerReparacionesTest.java && git commit -m "feat: el chasis aplica la bateria recordada de un movil sin grado y la da por usada (0.9.22, pieza 4)"
```

---

### Task 4: «Corregir…» da la batería por usada y el diálogo la trae (`TallerNsys`, `OpcionesTaller`)

**Files:**
- Modify: `S/TallerNsys.java` (`guardar` privado y `opciones`), `S/OpcionesTaller.java`
- Test: `T/TallerNsysTest.java`

**Interfaces:**
- Consumes: `BateriaRecordada`, `TallerDAO.bateriasRecordadas`, `TallerDAO.usarBaterias` (Task 2); `GradoTaller.grupo` (Task 1).
- Produces: `OpcionesTaller(String productoActual, List<OpcionColor> colores, List<SkuMaestro> skus, String bateriaIdRep, Boolean bateriaNueva, LocalDateTime bateriaEn)` con constructor de compatibilidad de 3 argumentos.

- [ ] **Step 1: Las pruebas (fallan)**

En `T/TallerNsysTest.java`, añadir:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: la batería recordada (spec §5.2 y §5.5)

    private static BateriaRecordada recordada() {
        return new BateriaRecordada(40, IMEI, "R20261021_3", true, "batería bati13 nueva", 8, AHORA.minusDays(1));
    }

    @Test void corregirElGradoConLetraDaPorUsadaLaBateriaRecordadaYMandaLoEscrito() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(recordada()));
        servicio.corregir(IMEI, null, "A", "visto en mano", 7);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "n/a", VIGENTE)), 7, "visto en mano", AHORA);
        verify(taller).usarBaterias(List.of(40), "A", AHORA);
    }

    @Test void corregirSoloElColorNoTocaLaBateriaRecordada() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(recordada()));
        servicio.corregir(IMEI, "Rojo", null, "chasis rojo", 7);
        verify(taller, never()).usarBaterias(any(), any(), any());
    }

    @Test void volverALoDeNsysEnNaDejaLaBateriaRecordada() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(1, IMEI, GRADO, "A", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(recordada()));
        servicio.volver(IMEI, null, 7);
        verify(taller, never()).usarBaterias(any(), any(), any());
    }

    @Test void lasOpcionesTraenLaBateriaRecordada() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(recordada()));
        OpcionesTaller o = servicio.opciones(IMEI);
        assertEquals("R20261021_3", o.bateriaIdRep());
        assertEquals(Boolean.TRUE, o.bateriaNueva());
        assertEquals(AHORA.minusDays(1), o.bateriaEn());
    }

    @Test void sinBateriaRecordadaLasOpcionesLaTraenANull() {
        OpcionesTaller o = servicio.opciones(IMEI);
        assertNull(o.bateriaIdRep());
        assertNull(o.bateriaNueva());
        assertNull(o.bateriaEn());
    }
```

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=TallerNsysTest`
Expected: no compila (`bateriaIdRep()` no existe).

- [ ] **Step 3: `OpcionesTaller`**

`S/OpcionesTaller.java` entero (con Write):

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Lo que necesita el diálogo «Corregir color, grado o batería…» (spec 0.9.21 §2.3): el producto actual del móvil, los
 *  colores del maestro para su modelo, capacidad y eSIM (vacío sin modelo en el maestro) y los SKU por color y grado. Desde la
 *  0.9.22 pieza 4 (§5.5), la batería recordada del móvil: su R…, si es nueva y cuándo se guardó (hora de Madrid; null sin
 *  ninguna). */
public record OpcionesTaller(@Schema(nullable = true) String productoActual, List<OpcionColor> colores,
                             List<SkuMaestro> skus, @Schema(nullable = true) String bateriaIdRep,
                             @Schema(nullable = true) Boolean bateriaNueva, @Schema(nullable = true) LocalDateTime bateriaEn) {

    /** Sin batería recordada. */
    public OpcionesTaller(String productoActual, List<OpcionColor> colores, List<SkuMaestro> skus) {
        this(productoActual, colores, skus, null, null, null);
    }
}
```

- [ ] **Step 4: `TallerNsys`**

En `S/TallerNsys.java`:

1. Sustituir el método privado `guardar(MovilNsys m, List<Paso> pasos, String motivo, int idUsu)` entero por:

```java
    /** Guarda los pasos y los apunta. 0.9.22 pieza 4 (§5.2): si la corrección deja el grado con letra (o en un grado ignorado),
     *  manda lo escrito y las baterías recordadas del móvil quedan usadas con ese grado; si lo deja «como n/a», siguen. */
    private void guardar(MovilNsys m, List<Paso> pasos, String motivo, int idUsu) {
        LocalDateTime ahora = ahora();
        tallerDao.guardar(m.imeiSn(), pasos.stream().map(Paso::nuevo).toList(), idUsu, motivo, ahora);
        for (Paso p : pasos) logDao.insertar(idUsu, ACCION, detalle(m.imeiSn(), p), motivo);
        Optional<String> grado = pasos.stream().map(Paso::nuevo).filter(n -> n.dato() == DatoTaller.GRADO)
                .map(TallerDAO.Nuevo::valor).findFirst();
        if (grado.isEmpty() || GradoTaller.grupo(grado.get()) == GradoTaller.Grupo.COMO_NA) return;
        List<BateriaRecordada> recordadas = tallerDao.bateriasRecordadas(List.of(m.imeiSn()));
        if (!recordadas.isEmpty())
            tallerDao.usarBaterias(recordadas.stream().map(BateriaRecordada::idAviso).toList(), grado.get(), ahora);
    }
```
(`Optional` ya se importa; si no, añadir `import java.util.Optional;`.)

2. En `opciones`, sustituir `return new OpcionesTaller(m.goods(), opciones, maestro.skus(m));` por:

```java
        Optional<BateriaRecordada> b = BateriaRecordada.ultima(tallerDao.bateriasRecordadas(List.of(m.imeiSn())));
        return new OpcionesTaller(m.goods(), opciones, maestro.skus(m), b.map(BateriaRecordada::idRep).orElse(null),
                b.map(BateriaRecordada::nueva).orElse(null), b.map(BateriaRecordada::fecha).orElse(null));
```
y añadir a su javadoc: «Y la batería recordada del móvil (0.9.22 pieza 4 §5.5).»

- [ ] **Step 5: Correr y la suite**

Run: `mvn -q -o test -Dtest=TallerNsysTest,StockTelefonosControllerTest,DescripcionMovilRutasTest`
Expected: verde. Después `mvn -q -o test` (verde).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/TallerNsys.java src/main/java/com/reparaciones/servidor/nsys/OpcionesTaller.java src/test/java/com/reparaciones/servidor/nsys/TallerNsysTest.java && git commit -m "feat: Corregir con letra da por usada la bateria recordada y el dialogo la trae (0.9.22, pieza 4)"
```

---

### Task 5: Trabajos con su fecha y los que han vuelto (`NsysDAO`, `VueltaNsys`)

**Files:**
- Create: `S/VueltaNsys.java`, `T/VueltaNsysTest.java`
- Modify: `S/NsysDAO.java` (constantes junto a `SQL_TRABAJOS_DE_IMEIS`, métodos junto a `altas`, `madridAUtc` junto a `utcAMadrid`), `T/TallerDePrueba.java` (dos helpers)
- Test: `T/NsysDAOTest.java`

**Interfaces:**
- Produces:
  - `NsysDAO.TrabajoFecha(String imei, String idRep, LocalDateTime asignado)` (record público);
  - `public List<NsysDAO.TrabajoFecha> trabajosDesde(LocalDateTime desdeMadrid)`, `public List<NsysDAO.TrabajoFecha> trabajosDe(Collection<String> imeis)`, `static Timestamp madridAUtc(LocalDateTime)`;
  - `public record VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde)` con `EN_EMPRESA`, `public static Optional<VueltaNsys> de(MovilNsys m, Collection<LocalDateTime> asignaciones)` y `public static Map<String, List<LocalDateTime>> asignacionesPorImei(Collection<NsysDAO.TrabajoFecha> trabajos)`;
  - `TallerDePrueba.vendido(String imei, String color, String grade, LocalDate venta)` y `TallerDePrueba.deBaja(String imei, String color, String grade, LocalDateTime cambiado)`.

- [ ] **Step 1: Los helpers de prueba**

En `T/TallerDePrueba.java`, debajo de `sinMaestro`:

```java
    /** Como {@link #movil}, vendido en NSYS (Released) el día {@code venta} (null: sin fecha de venta). */
    static MovilNsys vendido(String imei, String color, String grade, LocalDate venta) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Released", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), venta, "i13128" + color + grade, "13", "13", "128GB", color, grade, false, "Listos",
                "Listos", EstadoMovil.VENDIDO, HOY.minusDays(20), HOY.minusDays(1));
    }

    /** Como {@link #movil}, de baja en NSYS (Removed); {@code cambiado}: la última vez que NSYS cambió su fila. */
    static MovilNsys deBaja(String imei, String color, String grade, LocalDateTime cambiado) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Removed", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13128" + color + grade, "13", "13", "128GB", color, grade, false, "Listos",
                "Listos", EstadoMovil.BAJA, HOY.minusDays(20), cambiado);
    }
```

- [ ] **Step 2: Las pruebas (fallan)**

`T/VueltaNsysTest.java` (con Write):

```java
package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Si un móvil sale en «Revisar a mano», Color y Grado (spec 0.9.22 pieza 4 §6): en la empresa, sí; vendido o de baja, solo si
 *  el taller lo ha trabajado un día posterior a la venta o a la baja. */
class VueltaNsysTest {

    private static final String IMEI = "350000000000001";
    private static final LocalDateTime OCT22 = LocalDateTime.of(2026, 10, 22, 16, 47);

    @Test void unoEnLaEmpresaSaleTalCual() {
        assertEquals(Optional.of(VueltaNsys.EN_EMPRESA), VueltaNsys.de(movil(IMEI, "Azul", "B"), List.of()));
    }

    @Test void unVendidoQueElTallerTrabajoDespuesHaVueltoDesdeSuPrimerTrabajoPosterior() {
        MovilNsys m = vendido(IMEI, "Azul", "n/a", LocalDate.of(2026, 9, 18));
        assertEquals(Optional.of(new VueltaNsys(LocalDate.of(2026, 9, 18), null, OCT22)),
                VueltaNsys.de(m, List.of(OCT22.plusDays(2), LocalDateTime.of(2026, 9, 1, 10, 0), OCT22)));
    }

    @Test void elMismoDiaDeLaVentaNoCuentaNiLoDeAntes() {
        MovilNsys m = vendido(IMEI, "Azul", "n/a", LocalDate.of(2026, 10, 26));
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(LocalDateTime.of(2026, 10, 26, 11, 15),
                LocalDateTime.of(2026, 10, 23, 9, 0))));
    }

    @Test void unaBajaCuentaDesdeElDiaEnQueNsysCambioSuFila() {
        MovilNsys m = deBaja(IMEI, "Azul", "B", LocalDateTime.of(2026, 10, 20, 9, 31));
        assertEquals(Optional.of(new VueltaNsys(null, LocalDate.of(2026, 10, 20), OCT22)), VueltaNsys.de(m, List.of(OCT22)));
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(LocalDateTime.of(2026, 10, 20, 18, 0))));
    }

    @Test void unVendidoSinFechaDeVentaNoSale() {
        assertEquals(Optional.empty(), VueltaNsys.de(vendido(IMEI, "Azul", "B", null), List.of(OCT22)));
    }

    @Test void lasAsignacionesSeJuntanPorClaveDeCruce() {
        Map<String, List<LocalDateTime>> mapa = VueltaNsys.asignacionesPorImei(List.of(
                new NsysDAO.TrabajoFecha(IMEI, "A20261022_1", OCT22), new NsysDAO.TrabajoFecha(" f2lxabc123 ", "R20261022_2", OCT22),
                new NsysDAO.TrabajoFecha(IMEI, "R20261022_3", OCT22.plusHours(1))));
        assertEquals(List.of(OCT22, OCT22.plusHours(1)), mapa.get(IMEI));
        assertEquals(List.of(OCT22), mapa.get("F2LXABC123"));
    }
}
```

En `T/NsysDAOTest.java`, añadir al final de la clase:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: trabajos con su fecha (spec §4.1 y §6)

    @Test void lasHorasDeMadridPasanAUtcParaPreguntarALaBd() {
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 10, 49)), NsysDAO.madridAUtc(LocalDateTime.of(2026, 10, 22, 12, 49)),
                "verano, +2");
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 1, 12, 11, 1)), NsysDAO.madridAUtc(LocalDateTime.of(2026, 1, 12, 12, 1)),
                "invierno, +1");
    }

    @SuppressWarnings("unchecked")
    @Test void losTrabajosDesdeUnaHoraVanEnUnaConsultaConLaHoraEnUtc() {
        assertEquals("SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG FROM Reparacion r WHERE r.FECHA_ASIG >= ?", NsysDAO.SQL_TRABAJOS_DESDE);
        assertTrue(dao.trabajosDesde(LocalDateTime.of(2026, 10, 22, 12, 49)).isEmpty());
        verify(jdbc).query(eq(NsysDAO.SQL_TRABAJOS_DESDE + NsysDAO.ORDEN_TRABAJOS), any(RowMapper.class),
                eq(Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 10, 49))));
    }

    @Test void losTrabajosConFechaDeUnosImeisVanEnTandasYSinImeisNoConsulta() {
        assertTrue(dao.trabajosDe(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
        List<String> imeis = new ArrayList<>();
        for (int n = 0; n < 501; n++) imeis.add(String.format("3500000000%05d", n));
        assertTrue(dao.trabajosDe(imeis).isEmpty());
        List<String> sql = mockingDetails(jdbc).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("query")).map(inv -> inv.getArgument(0).toString()).toList();
        assertEquals(2, sql.size(), "500 + 1");
        assertEquals(NsysDAO.SQL_FECHAS_DE_IMEIS + "?)" + NsysDAO.ORDEN_TRABAJOS, sql.get(1));
    }

    @Test void unTrabajoConSuFechaEnMadrid() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI")).thenReturn("350000000000001");
        when(rs.getString("ID_REP")).thenReturn("R20261026_201");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 26, 16, 28)));
        assertEquals(new NsysDAO.TrabajoFecha("350000000000001", "R20261026_201", LocalDateTime.of(2026, 10, 26, 17, 28)),
                NsysDAO.trabajoFecha(rs, 0), "UTC → Madrid (invierno, +1)");
    }
```

- [ ] **Step 3: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=VueltaNsysTest,NsysDAOTest`
Expected: no compila (`VueltaNsys`, `TrabajoFecha`, `madridAUtc`… no existen).

- [ ] **Step 4: `NsysDAO`**

En `S/NsysDAO.java`:

1. Debajo de `ORDEN_TRABAJOS`:

```java
    /** 0.9.22 pieza 4 (§4.1): los trabajos del taller desde una hora (UTC, como guarda la BD), para «Sin grado en NSYS». */
    static final String SQL_TRABAJOS_DESDE = "SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG FROM Reparacion r WHERE r.FECHA_ASIG >= ?";
    /** 0.9.22 pieza 4 (§6): los trabajos de unos IMEI con su fecha, para saber si un vendido o de baja ha vuelto. Se completa con
     *  las marcas del IN y ORDEN_TRABAJOS. */
    static final String SQL_FECHAS_DE_IMEIS = "SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG FROM Reparacion r WHERE r.IMEI IN (";
```

2. Debajo de `trabajoDeAlta(...)`:

```java
    /** Una fila de Reparacion para «Sin grado en NSYS» y los que han vuelto (spec 0.9.22 pieza 4 §4.1 y §6): el IMEI del taller, su
     *  trabajo y su asignación, en hora de Madrid. */
    public record TrabajoFecha(String imei, String idRep, LocalDateTime asignado) {}

    /** Los trabajos del taller asignados desde {@code desdeMadrid} (hora de Madrid), de una consulta. */
    public List<TrabajoFecha> trabajosDesde(LocalDateTime desdeMadrid) {
        return jdbc.query(SQL_TRABAJOS_DESDE + ORDEN_TRABAJOS, NsysDAO::trabajoFecha, madridAUtc(desdeMadrid));
    }

    /** Los trabajos de estos IMEI, en tandas de {@value #TANDA_IN}; sin IMEI no consulta. */
    public List<TrabajoFecha> trabajosDe(Collection<String> imeis) {
        List<String> lista = List.copyOf(new LinkedHashSet<>(imeis));
        List<TrabajoFecha> trabajos = new ArrayList<>();
        for (List<String> tanda : trozos(lista, TANDA_IN))
            trabajos.addAll(jdbc.query(SQL_FECHAS_DE_IMEIS + marcas(tanda.size()) + ")" + ORDEN_TRABAJOS, NsysDAO::trabajoFecha,
                    tanda.toArray()));
        return trabajos;
    }

    static TrabajoFecha trabajoFecha(ResultSet rs, int n) throws SQLException {
        return new TrabajoFecha(rs.getString("IMEI"), rs.getString("ID_REP"), utcAMadrid(rs.getTimestamp("FECHA_ASIG")));
    }
```

3. Debajo de `utcAMadrid(...)`:

```java
    /** Una hora de Madrid como la guarda la BD del taller (UTC sin zona): lo contrario de {@link #utcAMadrid}. */
    static Timestamp madridAUtc(LocalDateTime madrid) {
        return Timestamp.valueOf(madrid.atZone(MADRID).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }
```
(Añadir `import java.util.LinkedHashSet;` si falta.)

- [ ] **Step 5: `VueltaNsys`**

`S/VueltaNsys.java` (con Write):

```java
package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Si un móvil sale en «Revisar a mano», Cambio de color y Cambio de grado (spec 0.9.22 pieza 4 §6): uno que NSYS tiene en la
 *  empresa, tal cual; uno vendido o de baja, solo si ha vuelto: el taller tiene algún trabajo suyo asignado un día posterior a la
 *  venta ({@code soldDate}, que va sin hora: el mismo día no cuenta) o a la baja (el día de {@code cambiado}, la última vez que
 *  NSYS cambió su fila). {@code vendidoEn} o {@code bajaEn}: ese día; {@code desde}: la asignación del primer trabajo posterior
 *  (hora de Madrid). Los tres null: en la empresa. Es puro. */
public record VueltaNsys(LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime desde) {

    /** Un móvil en la empresa: sale tal cual. */
    public static final VueltaNsys EN_EMPRESA = new VueltaNsys(null, null, null);

    /** La vuelta de un móvil con las asignaciones de sus trabajos (hora de Madrid, en cualquier orden); vacío si no sale. */
    public static Optional<VueltaNsys> de(MovilNsys m, Collection<LocalDateTime> asignaciones) {
        if (m.estado().enEmpresa()) return Optional.of(EN_EMPRESA);
        boolean vendido = m.estado() == EstadoMovil.VENDIDO;
        LocalDate dia = vendido ? m.soldDate() : m.cambiado() == null ? null : m.cambiado().toLocalDate();
        if (dia == null) return Optional.empty();
        return asignaciones.stream().filter(Objects::nonNull).filter(a -> a.toLocalDate().isAfter(dia))
                .min(Comparator.naturalOrder())
                .map(desde -> new VueltaNsys(vendido ? dia : null, vendido ? null : dia, desde));
    }

    /** Las asignaciones de unos trabajos, por {@link NsysDAO#claveCruce} del IMEI, en el orden en que llegan. */
    public static Map<String, List<LocalDateTime>> asignacionesPorImei(Collection<NsysDAO.TrabajoFecha> trabajos) {
        Map<String, List<LocalDateTime>> mapa = new LinkedHashMap<>();
        for (NsysDAO.TrabajoFecha t : trabajos)
            mapa.computeIfAbsent(NsysDAO.claveCruce(t.imei()), k -> new ArrayList<>()).add(t.asignado());
        return mapa;
    }
}
```

- [ ] **Step 6: Correr y la suite**

Run: `mvn -q -o test -Dtest=VueltaNsysTest,NsysDAOTest`
Expected: verde. Después `mvn -q -o test` (verde).

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/NsysDAO.java src/main/java/com/reparaciones/servidor/nsys/VueltaNsys.java src/test/java/com/reparaciones/servidor/nsys/NsysDAOTest.java src/test/java/com/reparaciones/servidor/nsys/VueltaNsysTest.java src/test/java/com/reparaciones/servidor/nsys/TallerDePrueba.java && git commit -m "feat: trabajos del taller con su fecha y si un vendido o de baja ha vuelto (0.9.22, pieza 4)"
```

---

### Task 6: Los que han vuelto en Color, Grado y Revisar, y fuera de la plantilla de color

**Files:**
- Modify: `S/FilaColorNsys.java`, `S/FilaGradoNsys.java`, `S/FilaRevisarNsys.java` (campos al final + constructor de compatibilidad), `S/TarjetasNsys.java` (`color`, `grado`, `revisar`), `S/PorPasarNsys.java` (`tarjetas`, `movilesEnEmpresa` → `movilesQueSalen`, `repasarHechos`, `plantillaColor`, métodos nuevos `vueltas` y `revisar`)
- Test: `T/TarjetasNsysTest.java`, `T/PorPasarNsysTest.java`

**Interfaces:**
- Consumes: `VueltaNsys`, `NsysDAO.trabajosDe`, `NsysDAO.TrabajoFecha` (Task 5).
- Produces:
  - `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys` ganan al final `LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime vueltoDesde` (nullable), con constructor sin ellos;
  - `TarjetasNsys.color(moviles, marcas, Map<String, VueltaNsys> vueltas)`, `TarjetasNsys.grado(moviles, marcas, vueltas)`, `TarjetasNsys.revisar(avisos, espejo, vueltas)` (las de antes siguen y pasan `Map.of()`);
  - `PorPasarNsys.vueltas(Collection<MovilNsys>)` (paquete): la vuelta de cada móvil que sale, por clave de cruce.

- [ ] **Step 1: Las pruebas (fallan)**

En `T/TarjetasNsysTest.java`, añadir (con `import java.time.LocalDate;` si falta):

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: los que han vuelto (spec §6)

    @Test void revisarMarcaElQueHaVuelto() {
        AvisoTaller a = new AvisoTaller(1, IMEI, "R20261022_2", AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow", null,
                "chasis chai13yellow nuevo", 8, "Técnico 2", AYER);
        VueltaNsys v = new VueltaNsys(LocalDate.of(2026, 9, 14), null, HOY);
        FilaRevisarNsys f = TarjetasNsys.revisar(List.of(a), Map.of(IMEI, movil(IMEI, "Azul", "B")), Map.of(IMEI, v)).get(0);
        assertEquals(LocalDate.of(2026, 9, 14), f.vendidoEn());
        assertNull(f.bajaEn());
        assertEquals(HOY, f.vueltoDesde());
        assertNull(TarjetasNsys.revisar(List.of(a), Map.of(IMEI, movil(IMEI, "Azul", "B"))).get(0).vueltoDesde(), "sin vuelta: en la empresa");
    }
```

En `T/PorPasarNsysTest.java`, añadir (con `import java.time.LocalDate;`):

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: los que han vuelto (spec §6)

    private static TallerNsys.MovilPorPasar vendidoConAmbosCambios(String imei, LocalDate venta) {
        return new TallerNsys.MovilPorPasar(vendido(imei, "Azul", "B", venta),
                new LoQueVale.Valor("Rosa", "A100", null, "Azul", "B", null, true),
                List.of(vigente(1, imei, COLOR, "Rosa", "Azul", AHORA.minusDays(1)), vigente(2, imei, GRADO, "A100", "B", AHORA.minusDays(1))),
                new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false));
    }

    private static AvisoTaller sinColorDe(int id, String imei) {
        return new AvisoTaller(id, imei, "R20261024_" + id, AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow", null,
                "chasis chai13yellow nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }

    @Test void unVendidoQueHaVueltoSaleMarcadoEnColorYGradoYUnoQueNoNo() {
        String vuelto = "350000000000002";
        when(taller.movilesPorPasar()).thenReturn(List.of(vendidoConAmbosCambios(vuelto, LocalDate.of(2026, 9, 14)),
                vendidoConAmbosCambios("350000000000003", LocalDate.of(2026, 10, 24))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(
                new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", LocalDateTime.of(2026, 10, 22, 14, 6)),
                new NsysDAO.TrabajoFecha("350000000000003", "R20261023_7", LocalDateTime.of(2026, 10, 23, 9, 17))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(vuelto), t.color().stream().map(FilaColorNsys::imeiSn).toList());
        assertEquals(LocalDate.of(2026, 9, 14), t.color().get(0).vendidoEn());
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 6), t.color().get(0).vueltoDesde());
        assertEquals(List.of(vuelto), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
        assertEquals(LocalDate.of(2026, 9, 14), t.grado().get(0).vendidoEn());
    }

    @Test void laPlantillaDeColorNoLlevaLosQueHanVuelto() {
        String vuelto = "350000000000002";
        when(taller.movilesPorPasar()).thenReturn(List.of(vendidoConAmbosCambios(vuelto, LocalDate.of(2026, 9, 14))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(
                new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", LocalDateTime.of(2026, 10, 22, 14, 6))));
        assertEquals(0, servicio.plantillaColor().filas());
        verify(nsys, never()).costes(any());
    }

    @Test void revisarSinLosAvisosDeVendidosQueNoHanVueltoYConLosQueSi() {
        String vuelto = "350000000000002";
        String vendidoSinMas = "350000000000003";
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinColorDe(1, IMEI), sinColorDe(2, vuelto), sinColorDe(3, vendidoSinMas)));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B"), vendido(vuelto, "Azul", "B", LocalDate.of(2026, 9, 14)),
                vendido(vendidoSinMas, "Azul", "B", LocalDate.of(2026, 10, 24))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(
                new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", LocalDateTime.of(2026, 10, 22, 14, 6))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI, vuelto), t.revisar().stream().map(FilaRevisarNsys::imeiSn).toList());
        assertNull(t.revisar().get(0).vueltoDesde());
        assertEquals(LocalDate.of(2026, 9, 14), t.revisar().get(1).vendidoEn());
    }

    @Test void conTodoEnLaEmpresaNoSeLeenTrabajos() {
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.tarjetas();
        verify(nsys, never()).trabajosDe(any());
    }
```

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=TarjetasNsysTest,PorPasarNsysTest`
Expected: no compila (`vendidoEn()`, `revisar` de 3 argumentos…).

- [ ] **Step 3: Los registros**

`S/FilaColorNsys.java`: añadir al final de los componentes `, @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn, @Schema(nullable = true) LocalDateTime vueltoDesde` (tras `hechoVolvio`), `import java.time.LocalDate;`, al javadoc «Desde la 0.9.22 pieza 4 (§6), si es un vendido o de baja que ha vuelto: el día de la venta o de la baja y desde cuándo lo tiene el taller (null en la empresa).», y el cuerpo:

```java
    /** Un móvil en la empresa (sin vuelta). */
    public FilaColorNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String colorDe, String colorA,
                         String productoNsys, String gradoNsys, String ponerProducto, boolean compruebalo, boolean tambienGrado,
                         List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor, LocalDateTime hechoEn,
                         LocalDateTime hechoVolvio) {
        this(imeiSn, modelo, modeloMaestro, capacidad, colorDe, colorA, productoNsys, gradoNsys, ponerProducto, compruebalo,
                tambienGrado, porQue, desde, hechoPor, hechoEn, hechoVolvio, null, null, null);
    }
```

`S/FilaGradoNsys.java`: lo mismo (campos al final tras `hechoVolvio`, import, javadoc) y el cuerpo:

```java
    /** Un móvil en la empresa (sin vuelta). */
    public FilaGradoNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String gradoDe, String gradoA,
                         String productoNsys, boolean conColor, List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor,
                         LocalDateTime hechoEn, LocalDateTime hechoVolvio) {
        this(imeiSn, modelo, modeloMaestro, capacidad, gradoDe, gradoA, productoNsys, conColor, porQue, desde, hechoPor, hechoEn,
                hechoVolvio, null, null, null);
    }
```

`S/FilaRevisarNsys.java`: campos al final tras `fecha`, import `java.time.LocalDate`, el javadoc («{@code tipo} SIN_COLOR o SIN_DESHACER» —SIN_GRADO ya no sale— y la frase de la vuelta) y el cuerpo:

```java
    /** Un móvil en la empresa (sin vuelta). */
    public FilaRevisarNsys(int idAviso, String imeiSn, String modelo, String modeloMaestro, String capacidad, String tipo,
                           String causa, String dato, String de, String a, String motivo, String idRep, String usuario,
                           LocalDateTime fecha) {
        this(idAviso, imeiSn, modelo, modeloMaestro, capacidad, tipo, causa, dato, de, a, motivo, idRep, usuario, fecha, null,
                null, null);
    }
```

- [ ] **Step 4: `TarjetasNsys`**

En `S/TarjetasNsys.java`:

1. `color`: renombrar la de ahora a la de 3 argumentos y añadir la de 2:

```java
    static List<FilaColorNsys> color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {
        return color(moviles, marcas, Map.of());
    }

    /** «Cambio de color» (§3.2): los móviles con un cambio de color vigente. Si el color que vale es el de NSYS (aún sin cerrar),
     *  el producto es el de ahora. {@code vueltas}: la vuelta de cada móvil por clave de cruce (pieza 4 §6); sin ella, en la
     *  empresa. */
    static List<FilaColorNsys> color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas,
                                     Map<String, VueltaNsys> vueltas) {
```
y, dentro, sustituir el `filas.add(new FilaColorNsys(...));` por:

```java
            VueltaNsys v = vueltas.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            filas.add(new FilaColorNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), deNsys != null ? deNsys : m.color(),
                    colorA, m.goods(), m.grade(), poner == null ? null : poner.producto(), poner != null && poner.compruebalo(),
                    cambio(p, DatoTaller.GRADO).isPresent(), porQue(p, DatoTaller.COLOR), desde, h.por(), h.en(), h.volvio(),
                    v.vendidoEn(), v.bajaEn(), v.desde()));
```

2. `grado`: igual:

```java
    static List<FilaGradoNsys> grado(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {
        return grado(moviles, marcas, Map.of());
    }

    /** «Cambio de grado» (§3.3): los móviles con un cambio de grado vigente, también los que cambian de color; con su vuelta
     *  (pieza 4 §6). */
    static List<FilaGradoNsys> grado(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas,
                                     Map<String, VueltaNsys> vueltas) {
```
y su `filas.add(...)` por:

```java
            VueltaNsys v = vueltas.getOrDefault(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            filas.add(new FilaGradoNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.grade(), p.valor().grade(),
                    m.goods(), cambio(p, DatoTaller.COLOR).isPresent(), porQue(p, DatoTaller.GRADO), desde, h.por(), h.en(),
                    h.volvio(), v.vendidoEn(), v.bajaEn(), v.desde()));
```

3. `revisar`: sustituir el método entero por:

```java
    /** «Revisar a mano» (§3.5), en el orden de los avisos, con el modelo de la fila más reciente del espejo de su IMEI
     *  ({@code espejo}, por {@link NsysDAO#claveCruce}) y, desde la pieza 4, su vuelta ({@code vueltas}; sin ella, en la empresa).
     *  Qué avisos salen lo decide quien llama (PorPasarNsys). */
    static List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos, Map<String, MovilNsys> espejo, Map<String, VueltaNsys> vueltas) {
        return avisos.stream().filter(a -> AvisoTaller.POR_REVISAR.contains(a.tipo())).map(a -> {
            String clave = NsysDAO.claveCruce(a.imeiSn());
            MovilNsys m = espejo.get(clave);
            VueltaNsys v = vueltas.getOrDefault(clave, VueltaNsys.EN_EMPRESA);
            return new FilaRevisarNsys(a.id(), a.imeiSn(), m == null ? null : m.modelo(), m == null ? null : m.modeloMaestro(),
                    m == null ? null : m.capacidad(), a.tipo().name(), a.causa() == null ? null : a.causa().name(),
                    a.dato().name(), a.de(), a.a(), a.motivo(), a.idRep(), a.usuario(), a.fecha(), v.vendidoEn(), v.bajaEn(),
                    v.desde());
        }).toList();
    }

    static List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos, Map<String, MovilNsys> espejo) {
        return revisar(avisos, espejo, Map.of());
    }
```

- [ ] **Step 5: `PorPasarNsys`**

En `S/PorPasarNsys.java` (imports: `java.util.Collection`, `java.util.LinkedHashMap` si faltan):

1. Sustituir `movilesEnEmpresa()` (método y javadoc) por:

```java
    /** Los móviles de «Cambio de color» y «Cambio de grado» con su vuelta (pieza 4 §6). */
    private record Salen(List<TallerNsys.MovilPorPasar> moviles, Map<String, VueltaNsys> vueltas) {}

    /** Los de «Por pasar a NSYS» que NSYS tiene en la empresa y los vendidos o de baja que han vuelto (pieza 4 §6). Un vendido o
     *  de baja que no ha vuelto no se vuelve a subir (sería stock que no hay) ni se le cambia el grado; su cambio sigue vigente y,
     *  si vuelve, sale otra vez solo. */
    private Salen movilesQueSalen() {
        List<TallerNsys.MovilPorPasar> todos = taller.movilesPorPasar();
        Map<String, VueltaNsys> vueltas = vueltas(todos.stream().map(TallerNsys.MovilPorPasar::movil).toList());
        return new Salen(todos.stream().filter(p -> vueltas.containsKey(NsysDAO.claveCruce(p.movil().imeiSn()))).toList(), vueltas);
    }

    /** La vuelta de cada móvil que sale, por clave de cruce (pieza 4 §6): los de la empresa sin leer nada; los vendidos o de baja,
     *  con sus trabajos (una consulta, solo si hay alguno). Un vendido o de baja que no ha vuelto no está. */
    Map<String, VueltaNsys> vueltas(Collection<MovilNsys> moviles) {
        Map<String, VueltaNsys> vueltas = new HashMap<>();
        List<MovilNsys> fuera = new ArrayList<>();
        for (MovilNsys m : moviles) {
            if (m.estado().enEmpresa()) vueltas.put(NsysDAO.claveCruce(m.imeiSn()), VueltaNsys.EN_EMPRESA);
            else fuera.add(m);
        }
        if (fuera.isEmpty()) return vueltas;
        Map<String, List<LocalDateTime>> asignaciones = VueltaNsys.asignacionesPorImei(
                nsysDao.trabajosDe(fuera.stream().map(MovilNsys::imeiSn).toList()));
        for (MovilNsys m : fuera) {
            String clave = NsysDAO.claveCruce(m.imeiSn());
            VueltaNsys.de(m, asignaciones.getOrDefault(clave, List.of())).ifPresent(v -> vueltas.put(clave, v));
        }
        return vueltas;
    }

    /** «Revisar a mano»: los avisos de móviles que salen (en la empresa o que han vuelto; uno que ya no está en el espejo, como
     *  antes), con su vuelta. */
    private List<FilaRevisarNsys> revisar(List<AvisoTaller> avisos) {
        Map<String, MovilNsys> espejo = espejo(avisos);
        Map<String, VueltaNsys> vueltas = vueltas(espejo.values());
        List<AvisoTaller> salen = avisos.stream().filter(a -> {
            String clave = NsysDAO.claveCruce(a.imeiSn());
            return !espejo.containsKey(clave) || vueltas.containsKey(clave);
        }).toList();
        return TarjetasNsys.revisar(salen, espejo, vueltas);
    }
```

2. En `tarjetas()`, sustituir el cuerpo por:

```java
        Salen salen = movilesQueSalen();
        Map<String, HechoTaller> marcas = TarjetasNsys.porClave(hechoDao.todas());
        List<AvisoTaller> avisos = tallerDao.avisosPorRevisar();
        List<AltaTaller> altas = altas();
        Map<String, DescarteTaller> descartes = descartes(altas);
        return new TarjetasPorPasar(TarjetasNsys.color(salen.moviles(), marcas, salen.vueltas()),
                TarjetasNsys.grado(salen.moviles(), marcas, salen.vueltas()), TarjetasNsys.altas(altas, marcas, descartes),
                revisar(avisos), TarjetasNsys.descartadas(altas, descartes));
```

3. En `repasarHechos`, `TarjetasNsys.filas(movilesEnEmpresa(), altas())` → `TarjetasNsys.filas(movilesQueSalen().moviles(), altas())`.

4. En `plantillaColor()`, sustituir sus dos primeras líneas por:

```java
        Salen salen = movilesQueSalen();
        List<TallerNsys.MovilPorPasar> moviles = salen.moviles();
        List<FilaColorNsys> van = TarjetasNsys.color(moviles, TarjetasNsys.porClave(hechoDao.todas()), salen.vueltas()).stream()
                .filter(f -> !TarjetasNsys.marcada(f.hechoEn(), f.hechoVolvio()) && f.ponerProducto() != null
                        && f.vueltoDesde() == null).toList();
```
y añadir a su javadoc: «Sin los que han vuelto (pieza 4 §6): antes, la devolución en NSYS.»

- [ ] **Step 6: Correr y la suite**

Run: `mvn -q -o test -Dtest=TarjetasNsysTest,PorPasarNsysTest`
Expected: verde (las pruebas de antes de vendidos y bajas siguen: sin fecha de venta o sin trabajo posterior no han vuelto). Después `mvn -q -o test` (verde).

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/FilaColorNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaGradoNsys.java src/main/java/com/reparaciones/servidor/nsys/FilaRevisarNsys.java src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java && git commit -m "feat: los vendidos o de baja que han vuelto salen marcados en Color, Grado y Revisar y no van en la plantilla (0.9.22, pieza 4)"
```

---

### Task 7: «Sin grado en NSYS» en «Revisar a mano»

**Files:**
- Create: `S/FilaSinGradoNsys.java`
- Modify: `S/TarjetasPorPasar.java`, `S/TarjetasNsys.java` (constante y método nuevos), `S/PorPasarNsys.java` (`tarjetas` y método nuevo `sinGrado`)
- Test: `T/TarjetasNsysTest.java`, `T/PorPasarNsysTest.java`

**Interfaces:**
- Consumes: `GradoTaller.grupo` (Task 1), `BateriaRecordada`, `TallerDAO.bateriasRecordadas(Collection<String>)` (Task 2), `NsysDAO.trabajosDesde`, `NsysDAO.TrabajoFecha` (Task 5), `PorPasarNsys.vueltas` (Task 6).
- Produces: `FilaSinGradoNsys` (14 campos, abajo); `TarjetasPorPasar` gana al final `List<FilaSinGradoNsys> sinGrado` (constructor de 5 argumentos con `List.of()`); `TarjetasNsys.DESDE_SIN_GRADO`; `TarjetasNsys.sinGrado(...)`.

- [ ] **Step 1: Las pruebas (fallan)**

En `T/TarjetasNsysTest.java`, añadir (imports `java.util.LinkedHashMap`, `java.time.LocalDate` si faltan):

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: «Sin grado en NSYS» (spec §4.1)

    private static NsysDAO.TrabajoFecha trabajo(String imei, String idRep, LocalDateTime asignado) {
        return new NsysDAO.TrabajoFecha(imei, idRep, asignado);
    }

    @Test void sinGradoLlevaLosComoNaQueSalenConSuUltimoTrabajoYSuBateria() {
        String conLetra = "350000000000003", ignorado = "350000000000004", desuso = "350000000000005", vendido = "350000000000006";
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 23, 9, 0), t2 = LocalDateTime.of(2026, 10, 26, 17, 28);
        Map<String, List<NsysDAO.TrabajoFecha>> trabajos = new LinkedHashMap<>();
        trabajos.put(IMEI, List.of(trabajo(IMEI, "A20261026_71", t1), trabajo(IMEI, "R20261026_205", t2)));
        trabajos.put(conLetra, List.of(trabajo(conLetra, "R20261023_1", t1)));
        trabajos.put(ignorado, List.of(trabajo(ignorado, "R20261023_2", t1)));
        trabajos.put(desuso, List.of(trabajo(desuso, "G20261023_40", t1)));
        trabajos.put(vendido, List.of(trabajo(vendido, "R20261023_3", t1)));
        Map<String, MovilNsys> espejo = Map.of(IMEI, movil(IMEI, "Azul", "n/a"), conLetra, movil(conLetra, "Azul", "n/a"),
                ignorado, movil(ignorado, "Azul", "ID"), desuso, movil(desuso, "Azul", "AG"), vendido, movil(vendido, "Azul", "n/a"));
        Map<String, List<CambioTaller>> vigentes = Map.of(conLetra, List.of(vigente(9, conLetra, GRADO, "A", "n/a")));
        Map<String, VueltaNsys> vueltas = Map.of(IMEI, VueltaNsys.EN_EMPRESA, conLetra, VueltaNsys.EN_EMPRESA,
                ignorado, VueltaNsys.EN_EMPRESA, desuso, VueltaNsys.EN_EMPRESA);   // el vendido no ha vuelto: no está
        BateriaRecordada b = new BateriaRecordada(40, IMEI, "R20261026_205", true, "batería bati13 nueva", 8, t2);
        List<FilaSinGradoNsys> filas = TarjetasNsys.sinGrado(trabajos, espejo, vigentes, vueltas, Map.of(IMEI, List.of(b)));
        assertEquals(List.of(desuso, IMEI), filas.stream().map(FilaSinGradoNsys::imeiSn).toList(), "del trabajo más antiguo al más reciente");
        FilaSinGradoNsys f = filas.get(1);
        assertEquals("n/a", f.gradoNsys());
        assertEquals("13", f.modelo());
        assertEquals("R20261026_205", f.ultimoTrabajo());
        assertEquals(t2, f.ultimoTrabajoEn());
        assertEquals("REPARACION", f.categoria());
        assertEquals("R20261026_205", f.bateriaIdRep());
        assertEquals(Boolean.TRUE, f.bateriaNueva());
        assertEquals(t2, f.bateriaEn());
        assertNull(f.vueltoDesde());
        assertEquals("GLASS", filas.get(0).categoria());
        assertEquals("AG", filas.get(0).gradoNsys());
        assertNull(filas.get(0).bateriaIdRep());
        assertNull(filas.get(0).bateriaNueva());
    }

    @Test void sinGradoMarcaElQueHaVuelto() {
        Map<String, List<NsysDAO.TrabajoFecha>> trabajos = Map.of(IMEI, List.of(trabajo(IMEI, "R20261022_136", HOY)));
        VueltaNsys v = new VueltaNsys(LocalDate.of(2026, 9, 18), null, HOY);
        FilaSinGradoNsys f = TarjetasNsys.sinGrado(trabajos, Map.of(IMEI, movil(IMEI, "Azul", "n/a")), Map.of(), Map.of(IMEI, v),
                Map.of()).get(0);
        assertEquals(LocalDate.of(2026, 9, 18), f.vendidoEn());
        assertEquals(HOY, f.vueltoDesde());
    }

    @Test void laFechaDeCorteEsLaSubidaDeLa0921() {
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 49), TarjetasNsys.DESDE_SIN_GRADO);
    }
```

En `T/PorPasarNsysTest.java`, añadir:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: «Sin grado en NSYS» (spec §4.1)

    @Test void sinGradoLeeLosTrabajosDesdeEl22YSoloSiHayAlgunoLoDemas() {
        assertTrue(servicio.tarjetas().sinGrado().isEmpty());
        verify(nsys).trabajosDesde(LocalDateTime.of(2026, 10, 22, 12, 49));
        verify(nsys, never()).porImeis(any());
        verify(tallerDao, never()).bateriasRecordadas(any());
    }

    @Test void sinGradoConUnTrabajoDeUnMovilNaYSuBateriaRecordada() {
        when(nsys.trabajosDesde(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(IMEI, "A20261025_9", AHORA.minusHours(2))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(tallerDao.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(new BateriaRecordada(40, IMEI, "R20261025_8", false,
                "batería bati13 reutilizada", 8, AHORA.minusHours(3))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI), t.sinGrado().stream().map(FilaSinGradoNsys::imeiSn).toList());
        assertEquals("A20261025_9", t.sinGrado().get(0).ultimoTrabajo());
        assertEquals(Boolean.FALSE, t.sinGrado().get(0).bateriaNueva());
        verify(tallerDao).vigentes(List.of(IMEI));
    }

    @Test void unMovilConGradoNoSaleEnSinGrado() {
        when(nsys.trabajosDesde(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(IMEI, "A20261025_9", AHORA.minusHours(2))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertTrue(servicio.tarjetas().sinGrado().isEmpty());
    }
```

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=TarjetasNsysTest,PorPasarNsysTest`
Expected: no compila (`FilaSinGradoNsys`, `sinGrado()`…).

- [ ] **Step 3: `FilaSinGradoNsys` y `TarjetasPorPasar`**

`S/FilaSinGradoNsys.java` (con Write):

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila «Sin grado en NSYS» de «Revisar a mano» (spec 0.9.22 pieza 4 §4.1): un móvil sin grado (lo que vale, «como n/a») que
 *  ha pasado por el taller desde el 22/10/2026 12:49. El grado de NSYS tal cual («n/a», vacío o el código), el último trabajo (su
 *  ID, su asignación en hora de Madrid y su {@code categoria}: REPARACION, GLASS o PULIDO), la batería recordada (su R…, si es
 *  nueva y cuándo; null sin ninguna) y, si es un vendido o de baja que ha vuelto, el día de la venta o de la baja y desde cuándo lo
 *  tiene el taller (null en la empresa). Sale hasta que tenga grado (sin «Revisado»). */
public record FilaSinGradoNsys(String imeiSn, @Schema(nullable = true) String modelo,
                               @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                               @Schema(nullable = true) String gradoNsys, String ultimoTrabajo, LocalDateTime ultimoTrabajoEn,
                               String categoria, @Schema(nullable = true) String bateriaIdRep,
                               @Schema(nullable = true) Boolean bateriaNueva, @Schema(nullable = true) LocalDateTime bateriaEn,
                               @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn,
                               @Schema(nullable = true) LocalDateTime vueltoDesde) {}
```

`S/TarjetasPorPasar.java` entero (con Write):

```java
package com.reparaciones.servidor.nsys;

import java.util.List;

/** Las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente, aparte los
 *  descartados de «No está en NSYS» (pieza 3 §4.2), del descarte más reciente al más antiguo, y las filas «Sin grado en NSYS» de
 *  «Revisar a mano» (pieza 4 §4.1), del trabajo más antiguo al más reciente. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas,
                               List<FilaSinGradoNsys> sinGrado) {

    /** Sin filas «Sin grado en NSYS». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas) {
        this(color, grado, altas, revisar, descartadas, List.of());
    }
}
```

- [ ] **Step 4: `TarjetasNsys.sinGrado`**

En `S/TarjetasNsys.java` (imports `java.util.Optional`, `java.util.Comparator` si faltan), después de `revisar`:

```java
    /** El primer momento que cuenta para «Sin grado en NSYS» (pieza 4 §4.1): la 0.9.21 en producción, hora de Madrid. */
    static final LocalDateTime DESDE_SIN_GRADO = LocalDateTime.of(2026, 10, 22, 12, 49);

    private static final Comparator<NsysDAO.TrabajoFecha> POR_ASIGNACION = Comparator.comparing(NsysDAO.TrabajoFecha::asignado,
            Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(NsysDAO.TrabajoFecha::idRep);

    /** «Sin grado en NSYS» (pieza 4 §4.1): por cada IMEI con trabajos desde {@link #DESDE_SIN_GRADO} ({@code trabajos}, por clave de
     *  cruce), su fila más reciente del espejo ({@code espejo}), lo que vale de su grado ({@code vigentes}), si sale ({@code
     *  vueltas}: en la empresa o que ha vuelto; si no está, no sale) y su batería recordada ({@code baterias}), todo por clave de
     *  cruce. Sale si lo que vale de su grado es «como n/a». Del trabajo más antiguo al más reciente, y por IMEI. */
    static List<FilaSinGradoNsys> sinGrado(Map<String, List<NsysDAO.TrabajoFecha>> trabajos, Map<String, MovilNsys> espejo,
                                           Map<String, List<CambioTaller>> vigentes, Map<String, VueltaNsys> vueltas,
                                           Map<String, List<BateriaRecordada>> baterias) {
        List<FilaSinGradoNsys> filas = new ArrayList<>();
        trabajos.forEach((clave, delImei) -> {
            MovilNsys m = espejo.get(clave);
            VueltaNsys v = vueltas.get(clave);
            if (m == null || v == null || delImei.isEmpty()) return;
            String grado = LoQueVale.valor(vigentes.getOrDefault(clave, List.of()), DatoTaller.GRADO, m);
            if (GradoTaller.grupo(grado) != GradoTaller.Grupo.COMO_NA) return;
            NsysDAO.TrabajoFecha ultimo = delImei.stream().max(POR_ASIGNACION).orElseThrow();
            Optional<BateriaRecordada> b = BateriaRecordada.ultima(baterias.getOrDefault(clave, List.of()));
            filas.add(new FilaSinGradoNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.grade(), ultimo.idRep(),
                    ultimo.asignado(), Rastro.categoria(ultimo.idRep()), b.map(BateriaRecordada::idRep).orElse(null),
                    b.map(BateriaRecordada::nueva).orElse(null), b.map(BateriaRecordada::fecha).orElse(null), v.vendidoEn(),
                    v.bajaEn(), v.desde()));
        });
        filas.sort(Comparator.comparing(FilaSinGradoNsys::ultimoTrabajoEn, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FilaSinGradoNsys::imeiSn));
        return filas;
    }
```

- [ ] **Step 5: `PorPasarNsys.sinGrado` y las tarjetas**

En `S/PorPasarNsys.java`, después de `revisar(List<AvisoTaller>)`:

```java
    /** «Sin grado en NSYS» (pieza 4 §4.1): los trabajos desde el 22/10 12:49 (una consulta) y, solo si hay, la fila más reciente
     *  del espejo de sus IMEI, sus cambios vigentes, sus vueltas y sus baterías recordadas. */
    List<FilaSinGradoNsys> sinGrado() {
        List<NsysDAO.TrabajoFecha> trabajos = nsysDao.trabajosDesde(TarjetasNsys.DESDE_SIN_GRADO);
        if (trabajos.isEmpty()) return List.of();
        Map<String, List<NsysDAO.TrabajoFecha>> porImei = new LinkedHashMap<>();
        for (NsysDAO.TrabajoFecha t : trabajos) porImei.computeIfAbsent(NsysDAO.claveCruce(t.imei()), k -> new ArrayList<>()).add(t);
        Map<String, MovilNsys> espejo = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(trabajos.stream().map(NsysDAO.TrabajoFecha::imei).distinct().toList()))
            espejo.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        if (espejo.isEmpty()) return List.of();
        List<String> imeis = espejo.values().stream().map(MovilNsys::imeiSn).toList();
        Map<String, List<CambioTaller>> vigentes = TallerNsys.porImei(tallerDao.vigentes(imeis));
        Map<String, List<BateriaRecordada>> baterias = new HashMap<>();
        for (BateriaRecordada b : tallerDao.bateriasRecordadas(imeis))
            baterias.computeIfAbsent(NsysDAO.claveCruce(b.imeiSn()), k -> new ArrayList<>()).add(b);
        return TarjetasNsys.sinGrado(porImei, espejo, vigentes, vueltas(espejo.values()), baterias);
    }
```

En `tarjetas()`, el `return` pasa a llevar `sinGrado()` al final:

```java
        return new TarjetasPorPasar(TarjetasNsys.color(salen.moviles(), marcas, salen.vueltas()),
                TarjetasNsys.grado(salen.moviles(), marcas, salen.vueltas()), TarjetasNsys.altas(altas, marcas, descartes),
                revisar(avisos), TarjetasNsys.descartadas(altas, descartes), sinGrado());
```
y su javadoc: «Las cuatro tarjetas (§3), los descartados de «No está en NSYS» (pieza 3) y las filas «Sin grado en NSYS» (pieza 4), con una lectura de cada cosa.»

- [ ] **Step 6: Correr y la suite**

Run: `mvn -q -o test -Dtest=TarjetasNsysTest,PorPasarNsysTest,RolesStockTelefonosTest`
Expected: verde. Después `mvn -q -o test` (verde).

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/FilaSinGradoNsys.java src/main/java/com/reparaciones/servidor/nsys/TarjetasPorPasar.java src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java && git commit -m "feat: Sin grado en NSYS en Revisar a mano, los moviles sin grado que pasan por el taller desde el 22/10 (0.9.22, pieza 4)"
```

---

### Task 8: Las baterías recordadas tras cada importación

**Files:**
- Modify: `S/PorPasarNsys.java` (método nuevo `usarBateriasRecordadas`), `S/ImportadorNsys.java` (llamada y método privado `usarBaterias`)
- Test: `T/PorPasarNsysTest.java`, `T/ImportadorNsysTest.java`

**Interfaces:**
- Consumes: `TallerDAO.bateriasRecordadas()`, `TallerDAO.usarBaterias`, `TallerDAO.guardar(…, Integer idUsu, …)` (Task 2); `GradoTaller` (Task 1); `EfectoTaller.cambio` (paquete).
- Produces: `public void PorPasarNsys.usarBateriasRecordadas()`.

- [ ] **Step 1: Las pruebas (fallan)**

En `T/PorPasarNsysTest.java`, añadir:

```java
    // ---------------------------------------------------------------- 0.9.22 pieza 4: baterías tras la importación (spec §5.2)

    private static BateriaRecordada bateria(int id, boolean nueva, LocalDateTime fecha) {
        return new BateriaRecordada(id, IMEI, "R20261024_" + id, nueva, nueva ? "batería bati13 nueva" : "batería bati13 reutilizada",
                8, fecha);
    }

    @Test void conLetraDeNsysLaBateriaNuevaRecordadaPoneEl100ANombreDeSuReparacion() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "A")));
        when(tallerDao.usarBaterias(any(), any(), any())).thenReturn(1);
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "A", CambioTaller.Estado.VIGENTE)), "R20261024_40",
                8, "batería bati13 nueva", AHORA);
        verify(tallerDao).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void conLetraDelTallerTambienSeAplica() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(9, IMEI, GRADO, "A", "n/a")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "n/a", CambioTaller.Estado.VIGENTE)),
                "R20261024_40", 8, "batería bati13 nueva", AHORA);
        verify(tallerDao).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void siYaLlevaEl100NoCambiaNadaYLaDaPorUsada() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "A100")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(tallerDao).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void conUnGradoIgnoradoSeDaPorUsadaSinCambiar() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "F")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(tallerDao).usarBaterias(List.of(40), "F", AHORA);
    }

    @Test void sinGradoTodaviaSigueRecordada() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao, never()).usarBaterias(any(), any(), any());
        verify(tallerDao, never()).guardar(any(), any(), any(), any(), any(), any());
    }

    @Test void conVariasValeLaMasRecienteYSeUsanTodas() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(2)), bateria(41, false, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "A100")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "A100", CambioTaller.Estado.VIGENTE)), "R20261024_41",
                8, "batería bati13 reutilizada", AHORA);
        verify(tallerDao).usarBaterias(List.of(40, 41), "A", AHORA);
    }

    @Test void sinBateriasRecordadasNoLeeNadaMas() {
        servicio.usarBateriasRecordadas();
        verify(nsys, never()).porImeis(any());
        verify(tallerDao, never()).vigentes(any());
    }
```

En `T/ImportadorNsysTest.java`:
- en `siElCierreFallaNoSeRepasanLasMarcasYLaImportacionSigueOk` y en `unaImportacionRechazadaNoRepasaLasMarcas`, añadir `verify(porPasar, never()).usarBateriasRecordadas();`;
- en `trasCerrarLoDelTallerSeRepasanLasMarcasConLaHoraDelFichero`, añadir al final `orden.verify(porPasar).usarBateriasRecordadas();`;
- añadir:

```java
    @Test void siFallaUsarLasBateriasLaPasadaSigue() throws Exception {
        ultimaActual(LocalDateTime.of(2026, 10, 16, 8, 31), 2);
        when(drive.listarItems("carpeta-1")).thenReturn(List.of(enDrive("Items_2026.10.16_09.01.xlsx"),
                enDrive("Items_2026.10.16_09.31.xlsx")));
        when(drive.descargar(anyString())).thenReturn(items(2));
        doThrow(new IllegalStateException("fallo")).when(porPasar).usarBateriasRecordadas();

        importador.pasada();

        verify(dao, never()).terminarImportacion(anyInt(), eq("ERROR"), anyInt(), anyInt(), any(), any());
        verify(dao, times(2)).aplicar(any(), anyInt());
    }
```

- [ ] **Step 2: Correrlas (fallan)**

Run: `mvn -q -o test -Dtest=PorPasarNsysTest,ImportadorNsysTest`
Expected: no compila (`usarBateriasRecordadas` no existe).

- [ ] **Step 3: `PorPasarNsys.usarBateriasRecordadas`**

En `S/PorPasarNsys.java` (import `java.util.Optional` si falta), después de `repasarDescartes()`:

```java
    /** Tras cada importación ACTUAL que acaba en OK (pieza 4 §5.2): las baterías recordadas de los móviles cuyo grado (lo que vale)
     *  ya tiene letra se aplican —un cambio de grado a nombre de la R… de la batería, si hace falta— y se dan por usadas; con un
     *  grado ignorado (F, Fd, RMA, ID) se dan por usadas sin cambiar nada; las de un «como n/a» siguen. Sin baterías no lee nada
     *  más. */
    public void usarBateriasRecordadas() {
        List<BateriaRecordada> todas = tallerDao.bateriasRecordadas();
        if (todas.isEmpty()) return;
        Map<String, List<BateriaRecordada>> porImei = new LinkedHashMap<>();
        for (BateriaRecordada b : todas) porImei.computeIfAbsent(NsysDAO.claveCruce(b.imeiSn()), k -> new ArrayList<>()).add(b);
        Map<String, MovilNsys> espejo = new HashMap<>();
        for (MovilNsys m : nsysDao.porImeis(todas.stream().map(BateriaRecordada::imeiSn).distinct().toList()))
            espejo.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
        if (espejo.isEmpty()) return;
        Map<String, List<CambioTaller>> vigentes = TallerNsys.porImei(
                tallerDao.vigentes(espejo.values().stream().map(MovilNsys::imeiSn).toList()));
        LocalDateTime ahora = ahora();
        int usadas = 0;
        int cambios = 0;
        for (Map.Entry<String, List<BateriaRecordada>> e : porImei.entrySet()) {
            MovilNsys m = espejo.get(e.getKey());
            if (m == null) continue;
            String grado = LoQueVale.valor(vigentes.getOrDefault(e.getKey(), List.of()), DatoTaller.GRADO, m);
            GradoTaller.Grupo grupo = GradoTaller.grupo(grado);
            if (grupo == GradoTaller.Grupo.COMO_NA) continue;
            String queda = grado;
            if (grupo == GradoTaller.Grupo.CON_FORMA) {
                BateriaRecordada b = BateriaRecordada.ultima(e.getValue()).orElseThrow();
                Optional<TallerDAO.Nuevo> n = GradoTaller.conPieza(grado, PiezaTaller.Tipo.BATERIA, b.nueva())
                        .flatMap(nuevo -> EfectoTaller.cambio(DatoTaller.GRADO, nuevo, grado, m));
                if (n.isPresent()) {
                    tallerDao.guardar(m.imeiSn(), List.of(n.get()), b.idRep(), b.idUsu(), b.motivo(), ahora);
                    queda = n.get().valor();
                    cambios++;
                }
            }
            usadas += tallerDao.usarBaterias(e.getValue().stream().map(BateriaRecordada::idAviso).toList(), queda, ahora);
        }
        if (usadas > 0) log.info("NSYS: baterías recordadas usadas: {} (cambios de grado: {})", usadas, cambios);
    }
```
(En el test `conLetraDeNsys…`, `usarBaterias` devuelve 1; en los demás, 0 por defecto: el log solo sale con alguna.)

- [ ] **Step 4: `ImportadorNsys`**

En `S/ImportadorNsys.java`:

```java
            if (cerrarTaller()) {
                repasarHechos(f.fecha());
                repasarDescartes();
                usarBaterias();
            }
```
y, después de `repasarDescartes()` (el método privado):

```java
    /** Tras el repaso de los descartes, las baterías recordadas (spec 0.9.22 pieza 4 §5.2): las de los móviles que ya tienen letra
     *  se aplican y se dan por usadas. Con la misma condición (cierre bueno); si falla, se avisa en el registro del servidor y se
     *  repite con el siguiente fichero. */
    private void usarBaterias() {
        try {
            porPasar.usarBateriasRecordadas();
        } catch (RuntimeException e) {
            log.warn("NSYS: no se pudieron usar las baterías recordadas tras la importación", e);
        }
    }
```

- [ ] **Step 5: Correr y la suite**

Run: `mvn -q -o test -Dtest=PorPasarNsysTest,ImportadorNsysTest`
Expected: verde. Después `mvn -q -o test` (verde).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/main/java/com/reparaciones/servidor/nsys/ImportadorNsys.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java src/test/java/com/reparaciones/servidor/nsys/ImportadorNsysTest.java && git commit -m "feat: tras cada importacion buena, la bateria recordada de un movil que ya tiene letra se aplica y se da por usada (0.9.22, pieza 4)"
```

---

### Task 9: El rastro de la batería usada y el contrato

**Files:**
- Modify: `S/Rastro.java` (`deRevisado`), `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (en `elContratoPublicaElStockDeTelefonos`, tras `assertNoNullable(esquemas, "TarjetasPorPasar", "descartadas");`)
- Test: `T/RastroTest.java`

**Interfaces:**
- Consumes: `AvisoTaller.revisadoPor()` null = usada por el ERP (Task 2, §5.3).
- Produces: evento de rastro `TALLER_BATERIA_USADA` (`de` = grado de partida, `a` = grado que quedó, `origen` REPARACION); `target/openapi.json` con los esquemas nuevos (lo usa la Task 10).

- [ ] **Step 1: Las pruebas (fallan)**

En `T/RastroTest.java`, añadir:

```java
    @Test void unaBateriaUsadaPorElErpSaleComoTallerBateriaUsadaConElGradoQueQuedo() {
        LocalDateTime hora = LocalDateTime.of(2026, 10, 26, 17, 28);
        AvisoTaller a = new AvisoTaller(40, "350000000000001", "R20261026_205", AvisoTaller.Tipo.SIN_GRADO, null, DatoTaller.GRADO,
                "n/a", "A100", "batería bati13 nueva", 8, "Técnico 2", hora, null, hora.plusSeconds(4));
        List<EventoRastro> rastro = Rastro.unir(List.of(), List.of(), List.of(), List.of(), List.of(a));
        assertEquals(List.of("TALLER_BATERIA_USADA", "TALLER_SIN_GRADO"), rastro.stream().map(EventoRastro::tipo).toList());
        assertEquals(new EventoRastro(hora.plusSeconds(4), false, "TALLER", "TALLER_BATERIA_USADA", null, "n/a", "A100", null,
                "R20261026_205", null, null, null, null, "GRADO", "REPARACION", "batería bati13 nueva"), rastro.get(0));
    }
```

En `OpenApiContractTest.java`, tras `assertNoNullable(esquemas, "TarjetasPorPasar", "descartadas");`:

```java
        // 0.9.22 pieza 4 (spec §4-§6): «Sin grado en NSYS», los que han vuelto y la batería recordada en el diálogo.
        assertTrue(esquemas.has("FilaSinGradoNsys"), () -> "falta el esquema FilaSinGradoNsys; publicados: " + nombres(esquemas));
        assertNoNullable(esquemas, "TarjetasPorPasar", "sinGrado");
        assertNullable(esquemas, "FilaSinGradoNsys", "modelo", "modeloMaestro", "capacidad", "gradoNsys", "bateriaIdRep",
                "bateriaNueva", "bateriaEn", "vendidoEn", "bajaEn", "vueltoDesde");
        assertNoNullable(esquemas, "FilaSinGradoNsys", "imeiSn", "ultimoTrabajo", "ultimoTrabajoEn", "categoria");
        for (String fila : List.of("FilaColorNsys", "FilaGradoNsys", "FilaRevisarNsys"))
            assertNullable(esquemas, fila, "vendidoEn", "bajaEn", "vueltoDesde");
        assertNullable(esquemas, "OpcionesTaller", "bateriaIdRep", "bateriaNueva", "bateriaEn");
```

- [ ] **Step 2: Correrlas**

Run: `mvn -q -o test -Dtest=RastroTest,OpenApiContractTest`
Expected: `RastroTest` falla (sale `TALLER_REVISADO_SIN_GRADO`); `OpenApiContractTest` ya pasa (los registros son de las tareas anteriores).

- [ ] **Step 3: `Rastro.deRevisado`**

En `S/Rastro.java`, sustituir `deRevisado` entero por:

```java
    /** «TALLER_REVISADO_» + el tipo del aviso (0.9.22): alguien lo dio por bueno desde «Revisar a mano», a la hora de la
     *  revisión. {@code tecnico}: quién lo revisó; el resto, como el aviso. Una batería recordada que usó el ERP (pieza 4 §5.3: un
     *  aviso de grado revisado sin quién) es «TALLER_BATERIA_USADA», con el grado que quedó en {@code a}. */
    private static EventoRastro deRevisado(AvisoTaller a) {
        boolean bateriaUsada = a.tipo() == AvisoTaller.Tipo.SIN_GRADO && a.revisadoPor() == null;
        return new EventoRastro(a.revisadoEn(), false, "TALLER", bateriaUsada ? "TALLER_BATERIA_USADA" : "TALLER_REVISADO_" + a.tipo().name(),
                null, a.de(), a.a(), null, a.idRep(), a.revisadoPor(), null, null, null, a.dato().name(),
                bateriaUsada ? CambioTaller.REPARACION : CambioTaller.MANO, a.motivo());
    }
```

- [ ] **Step 4: La suite entera y el contrato**

Run: `mvn -q -o test`
Expected: verde, y `target/openapi.json` reescrito por `OpenApiContractTest` (comprobar que lleva `FilaSinGradoNsys`: `grep -c FilaSinGradoNsys target/openapi.json` > 0). Anotar en el registro el número de pruebas.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-0922-revisar && git add src/main/java/com/reparaciones/servidor/nsys/Rastro.java src/test/java/com/reparaciones/servidor/nsys/RastroTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java && git commit -m "feat: rastro de la bateria usada por el ERP y contrato de Sin grado, vueltas y bateria (0.9.22, pieza 4)"
```

---

### Task 10: Web — contrato, datos de prueba y textos

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados), `src/shared/api/client.ts`, `src/modules/telefonos/test/fabrica.ts`, `src/modules/taller/test/fabrica.ts` (`opcionesTaller`), `src/test/server.ts` (línea ~41), `src/modules/telefonos/stock/PorPasarNsys.tsx` (`VACIAS`), `src/shared/lib/telefonoNsys.ts`, `src/modules/telefonos/stock/porPasar.ts`
- Test: `src/shared/lib/telefonoNsys.test.ts`, `src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Consumes: `target/openapi.json` del servidor (Task 9).
- Produces:
  - `type FilaSinGradoNsys` (client.ts); `TarjetasPorPasar.sinGrado`; `vendidoEn`/`bajaEn`/`vueltoDesde` en las filas de Color, Grado y Revisar; `bateriaIdRep`/`bateriaNueva`/`bateriaEn` en `OpcionesTaller`;
  - fábrica: `filaSinGrado(p?)`;
  - `telefonoNsys.ts`: `textoBateriaRecordada(nueva: boolean, en: string, idRep: string): string`, `textoBateriaEnCorregir(nueva: boolean, en: string, idRep: string): string`;
  - `porPasar.ts`: `ETIQUETA_SIN_GRADO`, `QUE_HACER_SIN_GRADO`, `textoGradoNsys(f)`, `textoUltimoTrabajoSinGrado(f)`, `textoBateriaDeFila(f)`, `textoVuelta(f)`, `vueltosFueraDePlantilla(filas)`, y `avisoPlantillaColor(nombre, n, sinProducto, compruebalo = 0, vueltos = 0)`.

- [ ] **Step 1: El contrato**

```bash
cp /c/Users/dev/Documents/wt/servidor-0922-revisar/target/openapi.json /c/Users/dev/Documents/wt/web-0922-revisar/api/openapi.json && cd /c/Users/dev/Documents/wt/web-0922-revisar && npm run api:types:offline && grep -c "FilaSinGradoNsys" src/shared/api/schema.d.ts
```
Expected: un número mayor que 0. (Si `target/openapi.json` no lleva `FilaSinGradoNsys`, correr antes en el servidor `mvn -q -o test -Dtest=OpenApiContractTest`.)

En `src/shared/api/client.ts`, tras la línea `export type PeticionDeshacerDescarte = …`:

```ts
/** 0.9.22 pieza 4: «Sin grado en NSYS» en «Revisar a mano» (un móvil sin grado que ha pasado por el taller desde el 22/10, con su
 *  último trabajo y su batería recordada). Color, Grado y Revisar llevan `vendidoEn`/`bajaEn`/`vueltoDesde` si es un vendido o de
 *  baja que ha vuelto; `OpcionesTaller` trae la batería recordada (`bateriaIdRep`, `bateriaNueva`, `bateriaEn`). */
export type FilaSinGradoNsys = components['schemas']['FilaSinGradoNsys']
```

- [ ] **Step 2: Datos de prueba**

En `src/modules/telefonos/test/fabrica.ts`:
- añadir `FilaSinGradoNsys` al `import type { … } from '@/shared/api/client'`;
- en `filaColor`, cambiar `desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, ...p,` por `desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, ...p,`;
- en `filaGrado`, cambiar `desde: '2026-10-24T12:40:00', hechoPor: null, hechoEn: null, hechoVolvio: null, ...p,` por `desde: '2026-10-24T12:40:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, ...p,`;
- en `filaRevisar`, cambiar `usuario: 'Técnico 3', fecha: '2026-10-22T11:14:00', ...p,` por `usuario: 'Técnico 3', fecha: '2026-10-22T11:14:00', vendidoEn: null, bajaEn: null, vueltoDesde: null, ...p,`;
- tras `filaRevisar`:

```ts
/** 0.9.22 pieza 4: una fila «Sin grado en NSYS»: un iPhone 13 128GB en «n/a» con una reparación del 26/10 17:28 y la batería
 *  nueva recordada de esa misma R…. */
export function filaSinGrado(p: Partial<FilaSinGradoNsys> = {}): FilaSinGradoNsys {
  return {
    imeiSn: '350000000000201', modelo: '13', modeloMaestro: '13', capacidad: '128GB', gradoNsys: 'n/a',
    ultimoTrabajo: 'R20261026_205', ultimoTrabajoEn: '2026-10-26T17:28:49', categoria: 'REPARACION',
    bateriaIdRep: 'R20261026_205', bateriaNueva: true, bateriaEn: '2026-10-26T17:28:49', vendidoEn: null, bajaEn: null,
    vueltoDesde: null, ...p,
  }
}
```
- en `tarjetas`, `{ color: [], grado: [], altas: [], revisar: [], descartadas: [], ...p }` → `{ color: [], grado: [], altas: [], revisar: [], descartadas: [], sinGrado: [], ...p }`.

En `src/modules/taller/test/fabrica.ts`, en `opcionesTaller`, añadir `bateriaIdRep: null, bateriaNueva: null, bateriaEn: null,` justo antes de `...p`.

En `src/test/server.ts` y en `const VACIAS` de `src/modules/telefonos/stock/PorPasarNsys.tsx`: `{ color: [], grado: [], altas: [], revisar: [], descartadas: [] }` → `{ color: [], grado: [], altas: [], revisar: [], descartadas: [], sinGrado: [] }`.

Run: `npx tsc -b`
Expected: sin errores. Si algún otro sitio construye esas filas o `TarjetasPorPasar` a mano (pruebas de páginas), añadirle los campos nuevos a null (o `sinGrado: []`).

- [ ] **Step 3: Las pruebas de los textos (fallan)**

En `src/shared/lib/telefonoNsys.test.ts`, añadir `textoBateriaEnCorregir, textoBateriaRecordada` a su import de `./telefonoNsys` y:

```ts
describe('batería recordada (0.9.22 pieza 4)', () => {
  it('en la fila «Sin grado en NSYS» y en «Corregir…»', () => {
    expect(textoBateriaRecordada(true, '2026-10-26T17:28:49', 'R20261026_205')).toBe('Batería nueva del 26/10 (R20261026_205)')
    expect(textoBateriaRecordada(false, '2026-10-26T17:28:49', 'R20261026_205')).toBe('Batería reutilizada del 26/10 (R20261026_205)')
    expect(textoBateriaEnCorregir(true, '2026-10-26T17:28:49', 'R20261026_205'))
      .toBe('Lleva batería nueva del 26/10 (R20261026_205): si quieres el 100, escríbelo')
    expect(textoBateriaEnCorregir(false, '2026-10-26T17:28:49', 'R20261026_205'))
      .toBe('Lleva batería reutilizada del 26/10 (R20261026_205): sin el 100')
  })
})
```

En `src/modules/telefonos/stock/porPasar.test.ts`, añadir a los imports `cuentas`, `paraPlantillaColor`, `avisoPlantillaColor`, `textoGradoNsys`, `textoUltimoTrabajoSinGrado`, `textoBateriaDeFila`, `textoVuelta`, `vueltosFueraDePlantilla` (de `./porPasar`) y `filaColor`, `filaRevisar`, `filaSinGrado`, `tarjetas` (de `../test/fabrica`) los que falten, y:

```ts
describe('0.9.22 pieza 4: «Sin grado en NSYS» y los que han vuelto', () => {
  it('«Revisar a mano» cuenta los avisos y las filas «Sin grado»', () => {
    expect(cuentas(tarjetas({ revisar: [filaRevisar()], sinGrado: [filaSinGrado(), filaSinGrado({ imeiSn: '350000000000202' })] }), 'revisar'))
      .toEqual({ total: 3, hechos: 0, pendientes: 3 })
  })

  it('los textos de la fila «Sin grado»', () => {
    expect(textoGradoNsys(filaSinGrado())).toBe('NSYS: n/a')
    expect(textoGradoNsys(filaSinGrado({ gradoNsys: '' }))).toBe('NSYS: —')
    expect(textoGradoNsys(filaSinGrado({ gradoNsys: null }))).toBe('NSYS: —')
    expect(textoGradoNsys(filaSinGrado({ gradoNsys: 'AG' }))).toBe('NSYS: AG')
    expect(textoUltimoTrabajoSinGrado(filaSinGrado())).toBe('Último trabajo: 26/10/2026 · Reparación (R20261026_205)')
    expect(textoUltimoTrabajoSinGrado(filaSinGrado({ ultimoTrabajo: 'G20261026_40', categoria: 'GLASS' })))
      .toBe('Último trabajo: 26/10/2026 · Glass (G20261026_40)')
    expect(textoBateriaDeFila(filaSinGrado())).toBe('Batería nueva del 26/10 (R20261026_205)')
    expect(textoBateriaDeFila(filaSinGrado({ bateriaIdRep: null, bateriaNueva: null, bateriaEn: null }))).toBeNull()
  })

  it('la línea de los que han vuelto', () => {
    expect(textoVuelta({ vendidoEn: null, bajaEn: null, vueltoDesde: null })).toBeNull()
    expect(textoVuelta({ vendidoEn: '2026-09-14', bajaEn: null, vueltoDesde: '2026-10-22T14:06:00' }))
      .toBe('Vendido en NSYS el 14/09/2026 · el taller lo tiene desde el 22/10/2026: haz antes la devolución en NSYS')
    expect(textoVuelta({ vendidoEn: null, bajaEn: '2026-10-20', vueltoDesde: '2026-10-22T14:06:00' }))
      .toBe('De baja en NSYS desde el 20/10/2026 · el taller lo tiene desde el 22/10/2026: míralo antes en NSYS')
  })

  it('la plantilla de color no lleva los que han vuelto y el aviso lo dice', () => {
    const vuelto = filaColor({ imeiSn: '350000000000012', vendidoEn: '2026-09-14', vueltoDesde: '2026-10-22T14:06:00' })
    expect(paraPlantillaColor([filaColor(), vuelto]).map((f) => f.imeiSn)).toEqual(['350000000000011'])
    expect(vueltosFueraDePlantilla([filaColor(), vuelto])).toBe(1)
    expect(avisoPlantillaColor('NSYS_color.xlsx', 1, 0, 0, 1))
      .toBe('Descargado «NSYS_color.xlsx» · 1 móvil (los marcados «Hecho» no van). 1 ha vuelto y no va: haz antes la devolución en NSYS')
    expect(avisoPlantillaColor('NSYS_color.xlsx', 1, 0, 0, 2)).toContain('2 han vuelto y no van: haz antes la devolución en NSYS')
  })
})
```

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/telefonos/stock/porPasar.test.ts`
Expected: FAIL (las funciones no existen).

- [ ] **Step 4: Los textos**

En `src/shared/lib/telefonoNsys.ts`, tras `diaNsys`:

```ts
/** «del 26/10 (R20261026_205)»: cuándo (hora de Madrid, sin zona) y en qué reparación se puso la batería recordada (0.9.22
 *  pieza 4). */
const deLaBateria = (en: string, idRep: string): string => `del ${en.slice(8, 10)}/${en.slice(5, 7)} (${idRep})`

/** «Batería nueva del 26/10 (R…)» / «Batería reutilizada del 26/10 (R…)» de la fila «Sin grado en NSYS». */
export const textoBateriaRecordada = (nueva: boolean, en: string, idRep: string): string =>
  `Batería ${nueva ? 'nueva' : 'reutilizada'} ${deLaBateria(en, idRep)}`

/** El recordatorio de «Corregir…» (0.9.22 pieza 4 §7): lo escrito manda, así que con la batería nueva que escriba él el 100. */
export const textoBateriaEnCorregir = (nueva: boolean, en: string, idRep: string): string =>
  nueva ? `Lleva batería nueva ${deLaBateria(en, idRep)}: si quieres el 100, escríbelo`
    : `Lleva batería reutilizada ${deLaBateria(en, idRep)}: sin el 100`
```

En `src/modules/telefonos/stock/porPasar.ts`:
1. Añadir `FilaSinGradoNsys` al `import type { … } from '@/shared/api/client'` y `textoBateriaRecordada` al import de `@/shared/lib/telefonoNsys`.
2. En `EXPLICACION`, `revisar: 'El ERP no supo calcularlo: mira el móvil y corrígelo en su ficha.',` → `revisar: 'Sin grado en NSYS o lo que el ERP no supo calcular: corrígelo en su ficha.',`.
3. En `AYUDA`, el texto de `revisar` → `'Sin grado en NSYS: móviles sin grado que han pasado por el taller desde el 22/10; se van solos cuando NSYS los gradúa o con «Corregir…». Los avisos de color y de deshacer se van con «Revisado» o al corregirlos.'`.
4. `cuentas`: la línea de `revisar` pasa a

```ts
  if (cual === 'revisar') {
    const n = t.revisar.length + t.sinGrado.length
    return { total: n, hechos: 0, pendientes: n }
  }
```
y su javadoc dice «(«Revisar a mano» no lleva «Hecho» y cuenta los avisos y las filas «Sin grado en NSYS»)».
5. `paraPlantillaColor` pasa a

```ts
/** Lo que lleva la plantilla de color (spec §4): lo pendiente con producto, sin los que han vuelto (pieza 4 §6: antes, la
 *  devolución en NSYS). */
export const paraPlantillaColor = (filas: readonly FilaColorNsys[]): FilaColorNsys[] =>
  pendientes(filas).filter((f) => f.ponerProducto !== null && f.vueltoDesde === null)

/** Los que irían en la plantilla de color pero han vuelto (pieza 4 §6). */
export const vueltosFueraDePlantilla = (filas: readonly FilaColorNsys[]): number =>
  pendientes(filas).filter((f) => f.ponerProducto !== null && f.vueltoDesde !== null).length
```
6. `avisoPlantillaColor` pasa a

```ts
/** El aviso de la plantilla de color (spec §4: «el número de móviles y lo que haya que revisar»): los que no van por no tener
 *  producto, los que llevan «compruébalo» (en amarillo en el Excel) y, desde la pieza 4, los que han vuelto y no van. */
export function avisoPlantillaColor(nombre: string, n: number, sinProducto: number, compruebalo = 0, vueltos = 0): string {
  const partes = [`Descargado «${nombre}» · ${n} ${palabraMoviles(n)} (los marcados «Hecho» no van)`]
  if (sinProducto > 0) partes.push(`${sinProducto} sin producto en NSYS no ${sinProducto === 1 ? 'va' : 'van'}: búscalo a mano`)
  if (compruebalo > 0) partes.push(`${compruebalo} con «compruébalo» (en amarillo): míralo antes de subirlo`)
  if (vueltos > 0) partes.push(`${vueltos} ${vueltos === 1 ? 'ha vuelto y no va' : 'han vuelto y no van'}: haz antes la devolución en NSYS`)
  return partes.join('. ')
}
```
7. Al final del fichero:

```ts
// ---------------------------------------------------------------- 0.9.22 pieza 4: «Sin grado en NSYS» y los que han vuelto

export const ETIQUETA_SIN_GRADO = 'Sin grado en NSYS'
export const QUE_HACER_SIN_GRADO = 'Ponle grado en NSYS: la fila se va sola al importarlo (o «Corregir…» si ya lo sabes)'

/** «NSYS: n/a» (el grado de NSYS tal cual; «NSYS: —» si está vacío). */
export const textoGradoNsys = (f: Pick<FilaSinGradoNsys, 'gradoNsys'>): string =>
  `NSYS: ${f.gradoNsys && f.gradoNsys.trim() !== '' ? f.gradoNsys : '—'}`

/** «Último trabajo: 26/10/2026 · Reparación (R20261026_205)». */
export const textoUltimoTrabajoSinGrado = (f: Pick<FilaSinGradoNsys, 'ultimoTrabajo' | 'ultimoTrabajoEn' | 'categoria'>): string =>
  `Último trabajo: ${diaNsys(f.ultimoTrabajoEn)} · ${CATEGORIA[f.categoria] ?? f.categoria} (${f.ultimoTrabajo})`

/** La batería recordada de la fila («Batería nueva del 26/10 (R…)»); null sin ninguna. */
export const textoBateriaDeFila = (f: Pick<FilaSinGradoNsys, 'bateriaIdRep' | 'bateriaNueva' | 'bateriaEn'>): string | null =>
  f.bateriaIdRep && f.bateriaEn ? textoBateriaRecordada(f.bateriaNueva === true, f.bateriaEn, f.bateriaIdRep) : null

/** La línea ámbar de un vendido o de baja que ha vuelto (pieza 4 §7); null en un móvil en la empresa. */
export function textoVuelta(f: { vendidoEn: string | null; bajaEn: string | null; vueltoDesde: string | null }): string | null {
  if (!f.vueltoDesde) return null
  const desde = diaNsys(f.vueltoDesde)
  if (f.vendidoEn) return `Vendido en NSYS el ${diaNsys(f.vendidoEn)} · el taller lo tiene desde el ${desde}: haz antes la devolución en NSYS`
  return `De baja en NSYS desde el ${diaNsys(f.bajaEn ?? f.vueltoDesde)} · el taller lo tiene desde el ${desde}: míralo antes en NSYS`
}
```

- [ ] **Step 5: Correr**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/telefonos/stock/porPasar.test.ts && npx tsc -b`
Expected: verde y sin errores de tipos. Si una prueba vieja de `porPasar.test.ts` comprobaba el texto anterior de la ayuda o de la explicación de «Revisar a mano», cambiarla al texto nuevo.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-0922-revisar && git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/telefonos/test/fabrica.ts src/modules/taller/test/fabrica.ts src/test/server.ts src/modules/telefonos/stock/PorPasarNsys.tsx src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts && git commit -m "feat: contrato y textos de Sin grado en NSYS, la bateria recordada y los que han vuelto (0.9.22, pieza 4)"
```
(Si el Step 2 tocó más ficheros por `tsc`, añadirlos al `git add`.)

---

### Task 11: Web — las filas «Sin grado», la línea de los que han vuelto y fuera la pastilla

**Files:**
- Modify: `src/modules/telefonos/stock/ListasPorPasar.tsx` (imports, `Vuelta`, celdas IMEI de `ListaColor` y `ListaGrado`, `ListaRevisar` entero), `src/modules/telefonos/stock/PorPasarNsys.tsx` (pastilla, botón de «Revisar a mano», aviso de la plantilla, props de `ListaRevisar`)
- Test: `src/modules/telefonos/stock/PorPasarNsys.test.tsx`

**Interfaces:**
- Consumes: todo lo de la Task 10.
- Produces: `ListaRevisar` con la prop nueva `sinGrado: FilaSinGradoNsys[]`.

- [ ] **Step 1: Las pruebas (fallan)**

En `src/modules/telefonos/stock/PorPasarNsys.test.tsx`, añadir `filaSinGrado` al import de `../test/fabrica` y, dentro del `describe`:

```tsx
  it('Revisar a mano: primero «Sin grado en NSYS» (sin «Revisado») y después los avisos; sin la pastilla', async () => {
    const user = userEvent.setup()
    const abrir = pintar(tarjetas({
      sinGrado: [filaSinGrado(), filaSinGrado({ imeiSn: '350000000000202', gradoNsys: '', bateriaIdRep: null, bateriaNueva: null,
        bateriaEn: null, categoria: 'GLASS', ultimoTrabajo: 'G20261026_40' })],
      revisar: [filaRevisar()],
    }))
    expect(screen.getByRole('article', { name: 'Revisar a mano' })).toHaveTextContent('3 móviles')
    await user.click(screen.getByRole('button', { name: 'Revisar a mano' }))
    expect(screen.queryByText('se va con «Revisado» (queda quién y cuándo) o al corregirlo')).not.toBeInTheDocument()
    expect(screen.getByText(/^Sin grado en NSYS: móviles sin grado que han pasado por el taller desde el 22\/10/)).toBeInTheDocument()
    const [uno, dos, tres] = filas(screen.getByRole('table', { name: 'Revisar a mano' }))
    expect(uno).toHaveTextContent('Sin grado en NSYS')
    expect(uno).toHaveTextContent('NSYS: n/a')
    expect(uno).toHaveTextContent('Batería nueva del 26/10 (R20261026_205)')
    expect(uno).toHaveTextContent('Último trabajo: 26/10/2026 · Reparación (R20261026_205)')
    expect(uno).toHaveTextContent('Ponle grado en NSYS: la fila se va sola al importarlo (o «Corregir…» si ya lo sabes)')
    expect(within(uno).queryByRole('button', { name: /^Revisado/ })).not.toBeInTheDocument()
    await user.click(within(uno).getByRole('button', { name: 'Corregir 350000000000201' }))
    expect(abrir).toHaveBeenCalledWith('350000000000201')
    expect(dos).toHaveTextContent('NSYS: —')
    expect(dos).toHaveTextContent('Último trabajo: 26/10/2026 · Glass (G20261026_40)')
    expect(dos).not.toHaveTextContent('Batería')
    expect(tres).toHaveTextContent('Color sin deducir')
    expect(within(tres).getByRole('button', { name: /^Revisado/ })).toBeInTheDocument()
  })

  it('sin avisos ni filas «Sin grado», Revisar a mano dice que no hay nada', async () => {
    const user = userEvent.setup()
    pintar(tarjetas())
    await user.click(screen.getByRole('button', { name: 'Revisar a mano' }))
    expect(screen.getByRole('table', { name: 'Revisar a mano' })).toHaveTextContent('Nada que revisar')
  })

  it('los que han vuelto llevan la línea ámbar en Color, Grado y Revisar', async () => {
    const user = userEvent.setup()
    const vuelto = { vendidoEn: '2026-09-14', bajaEn: null, vueltoDesde: '2026-10-22T14:06:00' }
    pintar(tarjetas({
      color: [filaColor(vuelto)],
      grado: [filaGrado({ vendidoEn: null, bajaEn: '2026-10-20', vueltoDesde: '2026-10-22T14:06:00' })],
      revisar: [filaRevisar(vuelto)],
    }))
    const texto = 'Vendido en NSYS el 14/09/2026 · el taller lo tiene desde el 22/10/2026: haz antes la devolución en NSYS'
    expect(screen.getByRole('table', { name: 'Cambio de color' })).toHaveTextContent(texto)
    await user.click(screen.getByRole('button', { name: 'Cambio de grado' }))
    expect(screen.getByRole('table', { name: 'Cambio de grado' }))
      .toHaveTextContent('De baja en NSYS desde el 20/10/2026 · el taller lo tiene desde el 22/10/2026: míralo antes en NSYS')
    await user.click(screen.getByRole('button', { name: 'Revisar a mano' }))
    expect(screen.getByRole('table', { name: 'Revisar a mano' })).toHaveTextContent(texto)
  })

  it('la plantilla de color no cuenta los que han vuelto y el aviso lo dice', async () => {
    const user = userEvent.setup()
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    server.use(http.get('*/api/stock-telefonos/por-pasar/plantilla-color', () => new HttpResponse(new Blob(['PK']), {
      headers: { 'Content-Disposition': 'attachment; filename="NSYS_color_2026-10-27.xlsx"', 'X-Filas': '1' },
    })))
    pintar(tarjetas({ color: [filaColor(), filaColor({ imeiSn: '350000000000012', vendidoEn: '2026-09-14', vueltoDesde: '2026-10-22T14:06:00' })] }))
    await user.click(screen.getByRole('button', { name: '⬇ Descargar plantilla (1)' }))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('1 ha vuelto y no va: haz antes la devolución en NSYS')
  })
```
(Si el `data-testid` del aviso breve cambió con la pieza 3, usar el que use la prueba de la plantilla de color que ya hay en este fichero.)

Run: `npx vitest run src/modules/telefonos/stock/PorPasarNsys.test.tsx`
Expected: FAIL (no hay filas «Sin grado», la pastilla sigue, no hay línea ámbar).

- [ ] **Step 2: `ListasPorPasar.tsx`**

1. Imports: `FilaSinGradoNsys` en el `import type` de `@/shared/api/client`; de `./porPasar` añadir `ETIQUETA_SIN_GRADO`, `QUE_HACER_SIN_GRADO`, `textoBateriaDeFila`, `textoGradoNsys`, `textoUltimoTrabajoSinGrado`, `textoVuelta`.
2. Tras la función `Imei`:

```tsx
const AMBAR = 'mt-0.5 block max-w-[260px] text-[11px] whitespace-normal text-ambar-grafico'

/** La línea ámbar de un vendido o de baja que ha vuelto (0.9.22 pieza 4 §7); nada en un móvil en la empresa. */
function Vuelta({ f }: { f: { vendidoEn: string | null; bajaEn: string | null; vueltoDesde: string | null } }) {
  const texto = textoVuelta(f)
  return texto ? <span className={AMBAR}>{texto}</span> : null
}
```
3. En `ListaColor` y en `ListaGrado`, la celda del IMEI `<td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /></td>` pasa a `<td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /><Vuelta f={f} /></td>`.
4. En `ListaAltas`, la línea de «descartado antes» usa la constante (es la misma clase): `<span className="mt-0.5 block max-w-[260px] text-[11px] whitespace-normal text-ambar-grafico">{textoDescartadoAntes(f)}</span>` → `<span className={AMBAR}>{textoDescartadoAntes(f)}</span>`.
5. Sustituir `RevisarProps` y `ListaRevisar` enteros por:

```tsx
type RevisarProps = {
  filas: FilaRevisarNsys[]; sinGrado: FilaSinGradoNsys[]; vacio: string; ocupado: boolean
  onAbrirImei: (imeiSn: string) => void; onRevisado: (idAviso: number) => void
}

/** «Revisar a mano» (§3.5): primero las filas «Sin grado en NSYS» (pieza 4: sin «Revisado»; se van solas al tener grado) y
 *  después los avisos, con «✓ Revisado» si ya estaba bien. «Corregir…» abre el móvil. */
export function ListaRevisar({ filas, sinGrado, vacio, ocupado, onAbrirImei, onRevisado }: RevisarProps) {
  const columnas = ['IMEI', 'Modelo', 'Qué pasa', 'Qué hacer', 'Reparación', '']
  return (
    <div className="min-h-0 flex-1 overflow-auto rounded-lg border border-fila-sep">
      <table aria-label="Revisar a mano" className="w-full border-collapse text-[12.5px] text-azul-medio">
        <thead className="sticky top-0 bg-crema">
          <tr>{columnas.map((c, i) => <th key={i} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
        </thead>
        <tbody>
          {filas.length === 0 && sinGrado.length === 0 && (
            <tr><td colSpan={columnas.length} className="px-2 py-3 text-[12px] text-azul-gris">{vacio}</td></tr>
          )}
          {sinGrado.map((f) => {
            const bateria = textoBateriaDeFila(f)
            return (
              <tr key={`sin-grado-${f.imeiSn}`} className="border-t border-fila-sep">
                <td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /><Vuelta f={f} /></td>
                <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
                <td className={cn(CELDA, 'min-w-[300px]')}>
                  <span className={cn('mb-0.5 inline-block rounded px-1.5 text-[10.5px] font-bold', ETIQUETA.SIN_GRADO)}>{ETIQUETA_SIN_GRADO}</span>
                  <span className="block">{textoGradoNsys(f)}</span>
                  {bateria && <span className={GRIS}>{bateria}</span>}
                </td>
                <td className={cn(CELDA, 'min-w-[220px] text-[12px]')}>{QUE_HACER_SIN_GRADO}</td>
                <td className={cn(CELDA, 'whitespace-nowrap')}>{textoUltimoTrabajoSinGrado(f)}</td>
                <td className={cn(CELDA, 'whitespace-nowrap')}>
                  <button type="button" aria-label={`Corregir ${f.imeiSn}`} onClick={() => onAbrirImei(f.imeiSn)} className={PEQUENO}>Corregir…</button>
                </td>
              </tr>
            )
          })}
          {filas.map((f) => (
            <tr key={f.idAviso} className="border-t border-fila-sep">
              <td className={CELDA}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /><Vuelta f={f} /></td>
              <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
              <td className={cn(CELDA, 'min-w-[300px]')}>
                <span className={cn('mb-0.5 inline-block rounded px-1.5 text-[10.5px] font-bold', ETIQUETA[f.tipo])}>{ETIQUETA_AVISO[f.tipo] ?? f.tipo}</span>
                <span className="block">{textoAvisoTaller({ tipo: f.tipo, causa: f.causa, dato: f.dato, de: f.de, idRep: f.idRep, usuario: f.usuario, motivo: f.motivo })}</span>
              </td>
              <td className={cn(CELDA, 'min-w-[220px] text-[12px]')}>{queHacerAviso(f.tipo, f.dato)}</td>
              <td className={cn(CELDA, 'whitespace-nowrap')}>{f.idRep}<span className={GRIS}>{`${f.usuario ?? '—'} · ${textoDesde(f.fecha)}`}</span></td>
              <td className={cn(CELDA, 'whitespace-nowrap')}>
                <span className="flex gap-1.5">
                  <button type="button" disabled={ocupado} aria-label={`Revisado ${f.idRep} de ${f.imeiSn}`} onClick={() => onRevisado(f.idAviso)} className={PEQUENO}>✓ Revisado</button>
                  <button type="button" aria-label={`Corregir ${f.imeiSn}`} onClick={() => onAbrirImei(f.imeiSn)} className={PEQUENO}>Corregir…</button>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 3: `PorPasarNsys.tsx`**

1. Añadir `vueltosFueraDePlantilla` al import de `./porPasar`.
2. Debajo de `const compruebalo = …`: `const vueltos = vueltosFueraDePlantilla(t.color)`, y en `bajar` la llamada `avisoPlantillaColor(nombre, filas, sinProducto, compruebalo)` pasa a `avisoPlantillaColor(nombre, filas, sinProducto, compruebalo, vueltos)`.
3. En `boton`, la última rama: `const n = t.revisar.length` → `const n = t.revisar.length + t.sinGrado.length`.
4. Borrar la línea `{elegida === 'revisar' && <span className={PASTILLA}>se va con «Revisado» (queda quién y cuándo) o al corregirlo</span>}`.
5. `<ListaRevisar filas={t.revisar} vacio={vacio} …` → `<ListaRevisar filas={t.revisar} sinGrado={t.sinGrado} vacio={vacio} …` (el resto igual).

- [ ] **Step 4: Correr**

Run: `npx vitest run src/modules/telefonos && npx tsc -b && npm run lint`
Expected: verde. Si una prueba vieja buscaba la pastilla de «Revisar a mano», quitar esa comprobación.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-0922-revisar && git add src/modules/telefonos/stock/ListasPorPasar.tsx src/modules/telefonos/stock/PorPasarNsys.tsx src/modules/telefonos/stock/PorPasarNsys.test.tsx && git commit -m "feat: filas Sin grado en NSYS en Revisar a mano, linea de los que han vuelto y fuera la pastilla (0.9.22, pieza 4)"
```

---

### Task 12: Web — «Corregir…» recuerda la batería, el rastro y la documentación

**Files:**
- Modify: `src/modules/taller/imeis/CorregirTallerDialog.tsx`, `src/modules/taller/imeis/rastro.ts`, `CHANGELOG.md` (web), `docs/novedades/NOVEDADES-v0.9.22.md` (raíz)
- Test: `src/modules/taller/imeis/CorregirTallerDialog.test.tsx`, `src/modules/taller/imeis/rastro.test.ts`

**Interfaces:**
- Consumes: `textoBateriaEnCorregir` (Task 10); `OpcionesTaller.bateriaIdRep/bateriaNueva/bateriaEn`; evento `TALLER_BATERIA_USADA` (Task 9).

- [ ] **Step 1: Las pruebas (fallan)**

En `CorregirTallerDialog.test.tsx`, dentro del `describe`:

```tsx
  it('con una batería recordada, la recuerda debajo de las casillas', async () => {
    server.use(http.get('*/api/stock-telefonos/:imeiSn/colores', () => HttpResponse.json(opcionesTaller({
      bateriaIdRep: 'R20261026_205', bateriaNueva: true, bateriaEn: '2026-10-26T17:28:49',
    }))))
    abrirDialogo({ grado: 'n/a', bateriaNueva: false })
    const d = await dialogo()
    expect(await within(d).findByText('Lleva batería nueva del 26/10 (R20261026_205): si quieres el 100, escríbelo')).toBeInTheDocument()
  })

  it('sin batería recordada no dice nada de la batería', async () => {
    abrirDialogo()
    const d = await dialogo()
    await waitFor(() => expect(within(d).getByRole('combobox', { name: 'Color' })).toBeEnabled())
    expect(within(d).queryByText(/^Lleva batería/)).not.toBeInTheDocument()
  })
```

En `rastro.test.ts`, en el `it` que comprueba los `TALLER_REVISADO_*` (usa `rep(...)` y `verEvento`), añadir:

```ts
    expect(verEvento(rep({ tipo: 'TALLER_BATERIA_USADA', dato: 'GRADO', de: 'n/a', a: 'A100', tecnico: null })))
      .toMatchObject({ titulo: 'Batería resuelta por el ERP · grado A100 (reparación R20261021_3)', color: 'bg-verde-ok' })
```

Run: `npx vitest run src/modules/taller/imeis/CorregirTallerDialog.test.tsx src/modules/taller/imeis/rastro.test.ts`
Expected: FAIL.

- [ ] **Step 2: El diálogo y el rastro**

En `CorregirTallerDialog.tsx`, añadir `textoBateriaEnCorregir` al import de `@/shared/lib/telefonoNsys`, y en `Contenido`, justo después de la etiqueta «Mensaje de pieza (Ms)» (la línea `<label className="flex items-center gap-2"><input type="checkbox" checked={e.grado.ms} …/>Mensaje de pieza (Ms)</label>`):

```tsx
        {opciones.data?.bateriaIdRep && opciones.data.bateriaEn && (<>
          <span />
          <p className="text-[11.5px] text-azul-gris">
            {textoBateriaEnCorregir(opciones.data.bateriaNueva === true, opciones.data.bateriaEn, opciones.data.bateriaIdRep)}
          </p>
        </>)}
```

En `rastro.ts`, antes de `default:`:

```ts
    case 'TALLER_BATERIA_USADA':
      // 0.9.22 pieza 4 (§5.3): el ERP aplicó la batería recordada (o la dio por usada) cuando el móvil tuvo letra.
      return { fecha, titulo: `Batería resuelta por el ERP · grado ${e.a ?? '—'} (reparación ${e.idRep ?? '—'})`, detalle: null, color: 'bg-verde-ok' }
```

- [ ] **Step 3: La documentación**

En `CHANGELOG.md` (web), dentro de `## [0.9.22] - (pendiente)`, tras la viñeta de **Rastro**:

```markdown
- **«Revisar a mano», rehecho** (pieza 4): cada móvil sin grado («n/a») que ha pasado por el taller desde el 22/10 sale una vez como **«Sin grado en NSYS»**, con su último trabajo y la batería que se le puso; se va solo cuando NSYS lo gradúa (o con «Corregir…»), sin «Revisado». Ya no salen los avisos «Grado sin calcular».
- **Batería recordada:** una batería puesta en un móvil sin grado se recuerda; cuando el móvil tiene letra (NSYS lo gradúa o se le pone un chasis), el ERP le pone el 100 —o se lo quita, si era reutilizada— y sale en «Cambio de grado». Arregla el chasis guardado después de la batería, que dejaba «A» en vez de «A100». Con «Corregir…» manda lo que se escribe: la ventana recuerda la batería.
- **Vendidos y bajas:** fuera de «Revisar a mano»; uno que el taller trabaja después de la venta (ha vuelto) sale con una línea ámbar en «Revisar a mano», Color y Grado y no va en la plantilla de color.
- **Grados:** F, Fd, RMA e ID no cambian con ninguna pieza ni avisan; A+ cuenta como A; los grados que ya no se usan (P, G, P+…) cuentan como «n/a». Rastro: «Batería resuelta por el ERP».
```

En `docs/novedades/NOVEDADES-v0.9.22.md` (raíz), cambiar la viñeta de **Revisar a mano** por:

```markdown
  - **Revisar a mano**: los móviles **sin grado** que han pasado por el taller («Sin grado en NSYS»; se van solos cuando NSYS los
    gradúa) y lo que el programa no supo calcular al guardar una reparación. «Corregir…» abre el móvil; «✓ Revisado» si ya estaba
    bien.
```
y añadir tras la viñeta de los vendidos o de baja:

```markdown
- Si se cambia la batería a un móvil sin grado, el programa se acuerda: cuando el móvil tenga grado, le pone (o le quita) el 100 y
  sale en «Cambio de grado».
- Un móvil vendido que vuelve al taller (devolución o garantía) sale con un aviso en ámbar: haz antes la devolución en NSYS.
```
(Si la pieza 3 cambió esas viñetas, poner lo mismo en las equivalentes.)

- [ ] **Step 4: Correr todo**

Run: `npx vitest run && npx tsc -b && npm run lint && npm run check`
Expected: verde (un timeout de 5 s fuera de `telefonos` y `taller/imeis` con la máquina cargada: repetir ese fichero solo).

- [ ] **Step 5: Commits**

```bash
cd /c/Users/dev/Documents/wt/web-0922-revisar && git add src/modules/taller/imeis/CorregirTallerDialog.tsx src/modules/taller/imeis/CorregirTallerDialog.test.tsx src/modules/taller/imeis/rastro.ts src/modules/taller/imeis/rastro.test.ts CHANGELOG.md && git commit -m "feat: Corregir recuerda la bateria, rastro de la bateria usada y changelog (0.9.22, pieza 4)"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.22.md && git commit -m "docs: novedades 0.9.22 pieza 4, Sin grado en NSYS, bateria recordada y los que han vuelto"
```

---

### Task 13: E2E en preprod

**Files:**
- Create: `tests/e2e/revisar-a-mano.spec.ts`
- Modify (fuera del repositorio): el guion de preprod de la 0.9.22, `/c/Users/dev/Documents/Apuntes/preprod/v0922-p2-preprod.md` (una sección «Pieza 4»)

**Interfaces:**
- Consumes: todo lo anterior desplegado en preprod (la E2E se corre **después** de subir las piezas 3 y 4 a preprod, con el usuario; aquí solo se escribe y se comprueba que compila).

- [ ] **Step 1: La E2E**

`tests/e2e/revisar-a-mano.spec.ts` (con Write):

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// revisar-a-mano.spec.ts (0.9.22 pieza 4): un móvil sin grado («n/a») con trabajo del taller sale en «Revisar a mano» como «Sin
// grado en NSYS»; una batería nueva se recuerda (sale en la fila); un chasis después le pone la A con el 100: sale en «Cambio de
// grado», deja «Sin grado» y el rastro dice «Batería resuelta por el ERP». Solo contra PREPRODUCCIÓN (se niega sin el letrero) y
// con el móvil SINTÉTICO sin grado del guion de preprod (por defecto 350000000009924, copia de un iPhone 13 128GB con GRADE
// «n/a»; otro con E2E_IMEI_SIN_GRADO): nunca uno real. Las asignaciones son del propio supertécnico (E2E_USER) y, al acabar, se
// borra todo lo del taller de ese IMEI (los avisos y el registro de actividad los limpia el guion).
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const IMEI = process.env.E2E_IMEI_SIN_GRADO ?? '350000000009924'
const SIN_NADA = 'Este móvil no tiene nada del taller'
const MOTIVO = 'E2E 0.9.22 pieza 4: Revisar a mano'

type Respuesta = { estado: number; cuerpo: unknown }
type FilaSinGrado = { imeiSn: string; ultimoTrabajo: string; bateriaIdRep: string | null; bateriaNueva: boolean | null }
type Tarjetas = { sinGrado: FilaSinGrado[]; grado: { imeiSn: string; gradoA: string | null }[] }
type Previa = { enNsys: boolean; despues: { color: string | null; grado: string | null }; colorSinDeducir: boolean }

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** Una petición a la API con el token de la sesión de la página (patrón de sku-taller.spec.ts). */
async function pedir(page: Page, metodo: string, ruta: string, cuerpo?: unknown, cabeceras: Record<string, string> = {}): Promise<Respuesta> {
  return page.evaluate(async ({ metodo, ruta, cuerpo, cabeceras }) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(ruta, {
      method: metodo,
      headers: { Authorization: `Bearer ${sesion.token}`, 'Content-Type': 'application/json', ...cabeceras },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    const texto = await res.text()
    return { estado: res.status, cuerpo: texto ? (JSON.parse(texto) as unknown) : null }
  }, { metodo, ruta, cuerpo, cabeceras })
}

/** Lo del taller de ese IMEI fuera: las reparaciones (las R… antes que las asignaciones; borrar una R… deshace lo suyo), el
 *  teléfono del lote y, por si quedó algo a mano, «Volver a lo de NSYS». */
async function limpiar(page: Page) {
  const r = await pedir(page, 'GET', `/api/reparaciones/imei/${IMEI}`)
  expect(r.estado, `GET /api/reparaciones/imei/${IMEI}`).toBe(200)
  const ids = (r.cuerpo as { idRep: string }[]).map((x) => x.idRep)
  for (const idRep of [...ids.filter((id) => id.startsWith('R')), ...ids.filter((id) => !id.startsWith('R'))]) {
    const b = await pedir(page, 'DELETE', `/api/reparaciones/${idRep}`, { motivo: 'E2E: limpieza' })
    expect.soft(b.estado, `limpieza DELETE /api/reparaciones/${idRep}`).toBe(204)
  }
  const t = await pedir(page, 'DELETE', `/api/telefonos/${IMEI}`)
  expect.soft([204, 404], `limpieza DELETE /api/telefonos/${IMEI}`).toContain(t.estado)
  const v = await pedir(page, 'DELETE', `/api/stock-telefonos/${IMEI}/taller`, { motivo: 'E2E: limpieza' })
  const detalle = `limpieza del móvil de prueba: ${v.estado} ${JSON.stringify(v.cuerpo)}`
  expect.soft([204, 400], detalle).toContain(v.estado)
  if (v.estado === 400) expect.soft(JSON.stringify(v.cuerpo), `${detalle} (se esperaba «${SIN_NADA}»)`).toContain(SIN_NADA)
}

async function tarjetas(page: Page): Promise<Tarjetas> {
  const r = await pedir(page, 'GET', '/api/stock-telefonos/por-pasar/tarjetas')
  expect(r.estado, 'GET /api/stock-telefonos/por-pasar/tarjetas').toBe(200)
  return r.cuerpo as Tarjetas
}

/** Una asignación de reparación del propio supertécnico para el móvil de prueba; devuelve su A…. */
async function asignar(page: Page, idTec: number, clave: string): Promise<string> {
  const lote = await pedir(page, 'POST', '/api/asignaciones/lote', {
    telefonos: [{ imei: IMEI, modelo: '13', idCli: null, clienteExplicito: false }],
    asignaciones: [{ imei: IMEI, categoria: 'R', idTec, comentario: MOTIVO, esChasis: false }],
  }, { 'Idempotency-Key': `e2e-revisar-${clave}-${Date.now()}` })
  expect(lote.estado, 'POST /api/asignaciones/lote').toBe(200)
  const creadas = (lote.cuerpo as { creadas: { idRep: string }[] }).creadas
  expect(creadas, 'una asignación de prueba').toHaveLength(1)
  return creadas[0].idRep
}

/** «Terminar asignación» desde el formulario con una sola pieza: la fila ({@code fila}, p. ej. «fila-bat»), su botón («Nueva
 *  Batería», «Reutilizada Chasis») y, si va por color, su SKU. */
async function terminar(page: Page, idAsig: string, fila: string, boton: string, sku?: { combo: string; tipo: string }) {
  await page.goto(`/reparaciones/pendientes/reparar/${idAsig}`)
  const formulario = page.getByRole('dialog', { name: /^Nueva reparación — IMEI / })
  await expect(formulario).toBeVisible({ timeout: 30_000 })
  const modelo = formulario.getByRole('combobox', { name: 'Filtrar por modelo' })
  if (await modelo.isEnabled()) {
    await modelo.click()
    await page.getByRole('listbox').getByRole('button', { name: 'iPhone 13', exact: true }).click()
  }
  const f = formulario.getByTestId(fila)
  const elegir = f.getByRole('button', { name: boton }).first()
  await expect(elegir, `«${boton}» deshabilitado: ¿sin stock de esa pieza en preprod?`).toBeEnabled()
  await elegir.click()
  if (sku) {
    await f.getByRole('combobox', { name: sku.combo }).click()
    await page.getByRole('listbox', { name: sku.combo }).getByTitle(sku.tipo, { exact: true }).click()
  }
  const guardar = formulario.getByTestId('zona-guardar').getByRole('button')
  await guardar.click()
  await expect(guardar).toHaveText('✓  Confirmar terminar')
  await guardar.click()
  await expect(formulario).toHaveCount(0, { timeout: 30_000 })
}

/** Un chasis reutilizado del iPhone 13 (ni Pro ni mini) cuyo color se deduce y que, con la batería nueva recordada, deja el móvil
 *  en A100 según la previa del servidor (la misma cuenta que al guardar). */
async function elegirChasis(page: Page): Promise<string> {
  const r = await pedir(page, 'GET', '/api/componentes/agrupados')
  expect(r.estado, 'GET /api/componentes/agrupados').toBe(200)
  const chasis = ((r.cuerpo as Record<string, { idCom: number; tipo: string; activo: boolean }[]>).cha ?? [])
    .filter((c) => c.activo && /^chai13(?!pro|mini)[a-z]+$/.test(c.tipo))
  for (const c of chasis) {
    const p = await pedir(page, 'POST', '/api/reparaciones/sku-taller/previsualizar', { imei: IMEI, filas: [{ idRep: null, idCom: c.idCom, reutilizada: true }] })
    expect(p.estado, 'POST /api/reparaciones/sku-taller/previsualizar').toBe(200)
    const previa = p.cuerpo as Previa
    if (previa.enNsys && !previa.colorSinDeducir && previa.despues.grado === 'A100') return c.tipo
  }
  throw new Error(`Ningún chasis del iPhone 13 deja ${IMEI} en A100 con la batería recordada: revisa el catálogo de preprod o la batería`)
}

async function abrirRevisarAMano(page: Page) {
  await page.goto('/telefonos/stock')
  await page.getByRole('button', { name: /^Por pasar a NSYS/ }).click()
  await page.getByRole('button', { name: 'Revisar a mano' }).click()
  const tabla = page.getByRole('table', { name: 'Revisar a mano' })
  await expect(tabla).toBeVisible()
  await expect(tabla.getByText('Cargando…')).toHaveCount(0)
  return tabla
}

test.describe('0.9.22 pieza 4: Revisar a mano con «Sin grado en NSYS» y la batería recordada', () => {
  test.describe.configure({ mode: 'serial' })
  let pagina: Page
  // Solo true tras comprobar el letrero de preprod: sin eso la limpieza (DELETE) no corre.
  let enPreprod = false
  let idTec: number
  let idAsig: string
  let bateria: string | null = null

  test.beforeAll(async ({ browser }, testInfo) => {
    const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
    pagina = await browser.newPage({ baseURL: testInfo.project.use.baseURL })
    await entrar(pagina, usuario, clave)
    enPreprod = true
    expect((await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).estado,
      `falta el móvil sintético ${IMEI} en el espejo de preprod (lo mete el guion)`).toBe(200)
    await limpiar(pagina)
    const ficha = (await pedir(pagina, 'GET', `/api/stock-telefonos/${IMEI}`)).cuerpo as { grado: string; porPasar: boolean }
    expect(ficha, 'el móvil de prueba empieza en «n/a» y sin nada del taller').toMatchObject({ grado: 'n/a', porPasar: false })
    idTec = await pagina.evaluate(() => (JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { idTec?: number }).idTec) as number
    expect(typeof idTec, 'idTec del supertécnico en la sesión').toBe('number')
  })

  test.afterAll(async () => {
    try {
      if (pagina && enPreprod) await limpiar(pagina)
    } finally {
      await pagina?.close()
    }
  })

  test('con una asignación sale en «Sin grado en NSYS», sin «Revisado»', async () => {
    idAsig = await asignar(pagina, idTec, 'bateria')
    expect((await tarjetas(pagina)).sinGrado.find((f) => f.imeiSn === IMEI)).toMatchObject({ ultimoTrabajo: idAsig, bateriaIdRep: null })
    const fila = (await abrirRevisarAMano(pagina)).getByRole('row', { name: new RegExp(IMEI) })
    await expect(fila).toContainText('Sin grado en NSYS')
    await expect(fila).toContainText('NSYS: n/a')
    await expect(fila.getByRole('button', { name: /^Revisado/ })).toHaveCount(0)
  })

  test('una batería nueva se recuerda en la fila', async () => {
    await terminar(pagina, idAsig, 'fila-bat', 'Nueva Batería')
    const f = (await tarjetas(pagina)).sinGrado.find((x) => x.imeiSn === IMEI)
    expect(f, 'sigue sin grado tras la batería').toBeDefined()
    expect(f).toMatchObject({ bateriaNueva: true })
    bateria = f?.bateriaIdRep ?? null
    expect(bateria).toMatch(/^R/)
    const fila = (await abrirRevisarAMano(pagina)).getByRole('row', { name: new RegExp(IMEI) })
    await expect(fila).toContainText(`Batería nueva del`)
    await expect(fila).toContainText(`(${bateria})`)
  })

  test('un chasis después le pone A100: sale en «Cambio de grado», deja «Sin grado» y el rastro lo dice', async () => {
    expect(bateria, 'el test anterior dejó la batería').not.toBeNull()
    const tipo = await elegirChasis(pagina)
    const idAsig2 = await asignar(pagina, idTec, 'chasis')
    await terminar(pagina, idAsig2, 'fila-cha', 'Reutilizada Chasis', { combo: 'SKU de Chasis', tipo })
    const t = await tarjetas(pagina)
    expect(t.sinGrado.some((x) => x.imeiSn === IMEI), 'ya tiene letra: fuera de «Sin grado»').toBe(false)
    expect(t.grado.find((x) => x.imeiSn === IMEI)).toMatchObject({ gradoA: 'A100' })
    await pagina.goto(`/reparaciones/imeis/${IMEI}`)
    const ficha = pagina.getByRole('region', { name: 'Ficha de NSYS' })
    await expect(ficha).toBeVisible({ timeout: 30_000 })
    await ficha.getByRole('button', { name: /^Ver rastro/ }).click()
    await expect(pagina.getByRole('dialog', { name: 'Rastro' })).toContainText(`Batería resuelta por el ERP · grado A100 (reparación ${bateria})`)
    await pagina.keyboard.press('Escape')
  })
})
```

Comprobar los nombres del formulario antes de dar la E2E por buena (no se puede correr sin preprod):

```bash
cd /c/Users/dev/Documents/wt/web-0922-revisar && grep -n "aria-label={\`Nueva \${tipo}\`}\|data-testid={\`fila-\${prefijo}\`}" src/modules/taller/formulario/FilaComponente.tsx && grep -rn "'bat'" src/modules/taller/lib/*.ts | head -3
```
Expected: el botón es «Nueva <nombre de la fila>» y el `data-testid` es `fila-<prefijo>`; el prefijo de la batería es `bat` (de ahí `fila-bat` y «Nueva Batería»; si el nombre de la fila no es «Batería», cambiarlo en la E2E).

Run: `npx tsc -b && npx eslint tests/e2e/revisar-a-mano.spec.ts`
Expected: sin errores.

- [ ] **Step 2: El guion de preprod (fuera del repositorio)**

En `/c/Users/dev/Documents/Apuntes/preprod/v0922-p2-preprod.md`, añadir una sección «Pieza 4 — Revisar a mano» con:
- el móvil sintético sin grado, en el bloque que ya mete el `350000000009921` (misma comprobación `hostname` + «ESTO PARECE PRODUCCIÓN» y misma función `m()`):

```sql
SET NAMES utf8mb4;
INSERT INTO Nsys_movil (IMEI_SN, BATCH, GOODS, GRADE, STATUS, LOCATION, SUPPLIER, RECEIVING_DATE, SOLD_DATE, SKU, MODELO,
  MODELO_MAESTRO, CAPACIDAD, COLOR, GRADO_DETALLE, ES_ESIM, UBICACION, UBICACION_ULTIMA, ESTADO, VISTO_PRIMERO, CAMBIADO)
SELECT '350000000009924', 'BT-E2E', GOODS, 'n/a', 'Available', 'CAJA LISTOS', 'Proveedor E2E', '2026-10-01', NULL, NULL,
  MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, 'n/a', ES_ESIM, 'Listos', 'Listos', 'STOCK', '2026-10-27 10:00:00', '2026-10-27 10:00:00'
FROM Nsys_movil WHERE MODELO = '13' AND CAPACIDAD = '128GB' AND ES_ESIM = FALSE AND COLOR IS NOT NULL
ORDER BY VISTO_PRIMERO DESC LIMIT 1;
SELECT IMEI_SN, GOODS, GRADE, COLOR FROM Nsys_movil WHERE IMEI_SN = '350000000009924';
```
- la orden de la E2E (la corre Claude desde el PC, en PowerShell, como las otras del guion):
  `$env:WEB = "/c/Users/dev/Documents/wt/web-0922-revisar"; bash -l -c "/c/Users/dev/Documents/Apuntes/preprod/e2e-0921.sh descartar-nsys revisar-a-mano plantillas-nsys sku-taller sku-taller-reparacion"`
  (con `WEB` en el worktree de esta pieza, que lleva también las E2E de la pieza 3);
- la limpieza tras la E2E: `DELETE FROM Taller_aviso WHERE IMEI_SN = '350000000009924';`, `DELETE FROM Taller_cambio WHERE IMEI_SN = '350000000009924';` y las líneas de `Log_Actividad` de esas pruebas (como en la pieza 3);
- lo que hay que mirar con la base de producción (spec §8): «Sin grado» en torno a 50 menos los vendidos que no han vuelto; tras la primera importación, los 7 móviles de batería seguida de chasis en `A100` en «Cambio de grado»; los que han vuelto (4 en «Revisar a mano», 2 en Color/Grado con la copia del 26/10).

- [ ] **Step 3: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-0922-revisar && git add tests/e2e/revisar-a-mano.spec.ts && git commit -m "test: E2E de Revisar a mano con Sin grado en NSYS y la bateria recordada, solo en preprod (0.9.22, pieza 4)"
```

---

## Al terminar

- Revisión final de toda la rama (servidor y web, desde las puntas de la pieza 3) con el revisor más capaz; los Minor del registro, a esa revisión.
- **No** fusionar, **no** subir, **no** etiquetar. Dejar en el registro (`.superpowers/sdd/v0922-pieza4/progress.md`) y en el informe final: las puntas de las dos ramas, el número de pruebas del servidor y de la web, si la pieza 3 recibió commits después de crear las ramas, y lo pendiente con el usuario: fusionar pieza 3 y pieza 4 (en ese orden), preprod (guion con la pieza 4 y la E2E), y el guion de producción `Apuntes/prod-v0922-p2.md` (tres migraciones, servidor, web y el `UPDATE` de los 2 restos de tapa: `UPDATE Taller_aviso SET REVISADO_POR = <id del usuario>, REVISADO_EN = <hora> WHERE TIPO = 'SIN_GRADO' AND REVISADO_EN IS NULL AND MOTIVO LIKE 'tapa %'`, que tiene que tocar 2, en lugar del paso «✓ Revisado a mano»).

