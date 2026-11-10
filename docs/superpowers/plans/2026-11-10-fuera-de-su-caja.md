# «Fuera de su caja» (los F, FD y RMA que no están en la caja de su grado) · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** una quinta tarjeta en «Por pasar a NSYS», «Fuera de su caja», con los móviles de grado F, FD o RMA que NSYS tiene en una
caja que no es la de su grado, agrupados por la caja que toca y con «Copiar IMEIs» en cada grupo.

**Architecture:**
- **Servidor:** la pareja grado → caja vive en `EstadoMovil` (`CAJA_DE_GRADO`, `cajaQueToca`), junto a `NO_VENDIBLES`, que pasa a
  salir de ese mapa. `TarjetasNsys.cajas(...)` (pura) aplica la regla a los móviles en la empresa con el grado que vale y los
  trabajos abiertos. `PorPasarNsys.tarjetas()` la llama y `TarjetasPorPasar` gana la lista `cajas`.
- **Web:** textos, grupos y lo que se copia en `porPasar.ts` (puro); `TarjetasNsys.tsx` pasa a cinco columnas; `ListasPorPasar.tsx`
  gana `ListaCajas`; `PorPasarNsys.tsx` la pinta y copia.
- Sin base de datos, sin «Hecho», sin rutas nuevas.

**Tech Stack:** Spring Boot 3 + JUnit 5 + Mockito (servidor); React 19 + TypeScript, Vitest + Testing Library (web).

**Spec:** `docs/superpowers/specs/2026-11-10-fuera-de-su-caja-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-11-10-prototipo-fuera-de-su-caja.html`.

## Global Constraints

- **Servidor + web, sin migración.** Contrato aditivo: `TarjetasPorPasar.cajas`. La web anterior sirve con el servidor nuevo; la
  nueva, con el servidor anterior, trata `cajas` ausente como lista vacía (`?? []`).
- **La pareja grado → caja**, en este orden: `F` → `CF021`, `FD` → `Desguace`, `RMA` → `Funcionalidad pendiente`. El grado se
  compara sin espacios a los lados y sin mirar mayúsculas; la caja, con `Nsys_movil.LOCATION` sin espacios a los lados y **tal
  cual** (mayúsculas incluidas).
- **Regla (spec §3):** sale un móvil con estado guardado `STOCK` o `NO_CUENTA` (un `DE_PEDIDO` no), cuyo grado que vale (NSYS con
  lo del taller vigente, sin el «100» de la batería) es F, FD o RMA, cuya caja no es la de su grado (sin caja también sale) y que
  no tiene ningún trabajo abierto del taller.
- **Orden de la lista:** por caja que toca (el orden del mapa), dentro por caja de hoy (sin caja al final) y por IMEI.
- **Textos exactos (web):**
  - tarjeta: nombre `Fuera de su caja`; explicación `F, FD o RMA en una caja que no es la de su grado: llévalo a su caja y cámbiale la «Location» en NSYS.`; botón `Ver`;
  - ayuda: `Móviles con grado F, FD o RMA que NSYS tiene en la empresa y fuera de la caja de su grado: los F van en «CF021», los FD en «Desguace» y los RMA en «Funcionalidad pendiente». Copia los IMEIs de cada caja, búscalos en NSYS y cámbiales la «Location». Cada fila se va sola cuando la importación (cada 30 min) ve la caja nueva. No salen los que tienen un trabajo abierto del taller ni los de pedido.`;
  - lista vacía: `No hay móviles F, FD o RMA fuera de su caja.`;
  - tabla `aria-label` `Fuera de su caja`, columnas `IMEI`, `Modelo`, `Color`, `Grado`, `Caja de hoy en NSYS`;
  - cabecera de grupo: `Llevar a «CF021»`, `grado F · 3 móviles` (`1 móvil`), botón `Copiar IMEIs (3)` con `aria-label`
    `Copiar IMEIs para CF021`;
  - sin caja: `sin caja` (la constante `SIN_CAJA`), en ámbar; debajo de la caja, en gris, el nombre del ERP (`—` si no hay);
  - nota del grado: `en NSYS aún B: también en «Cambio de grado»`;
  - aviso al copiar: `Copiado: 3 IMEIs para «CF021». Búscalos en NSYS y cámbiales la «Location»` (`1 IMEI`).
- **Permisos:** los de `PorPasarController` (no se toca).
- **Fronteras de la web:** un módulo no importa de otro (`no-restricted-imports`): todo va en `src/modules/telefonos/` o en
  `src/shared`.
- **Repos públicos:** datos inventados (IMEIs `3500000000…`, «Técnico 2»); ni IMEIs reales, ni URLs ni IPs.
- **Commits** en castellano **sin tildes** y **sin** línea `Co-Authored-By`. **Nunca** push, merge ni tags sin el OK del usuario.
- **Otras sesiones trabajan a la vez:** no se tocan los clones principales ni otros worktrees `wt/…`. En la raíz hay ficheros sin
  seguimiento que no son de nadie (`.codegraph/`, `node_modules/`, PDF): `git add` solo los de la tarea.
- **Número de versión:** lo decide el usuario al sacarla (hoy sería la 0.9.31). Hasta la Task 6 no se toca ningún número.
- **Toolchain:** en cada Bash del servidor, antes de `mvn`:
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.

**Dónde se trabaja** (Task 0):
- servidor: worktree `/c/Users/dev/Documents/wt/servidor-fuera-caja`, rama `dev_fuera_de_su_caja`, desde `origin/main` del
  servidor (10/11: `ac56071`, 0.9.30);
- web: worktree `/c/Users/dev/Documents/wt/web-fuera-caja`, rama `dev_fuera_de_su_caja`, desde `origin/main` de la web (10/11:
  `1793373`, 0.9.30);
- raíz (spec, plan, novedades): `/c/Users/dev/Documents/ProgramaReparaciones`, en `main`, sin push.

En el servidor, `S=/c/Users/dev/Documents/wt/servidor-fuera-caja`, paquete `$S/src/main/java/com/reparaciones/servidor/nsys/`
(tests en `$S/src/test/java/com/reparaciones/servidor/nsys/`). En la web, `W=/c/Users/dev/Documents/wt/web-fuera-caja` y
`$W/src/modules/telefonos/stock/`.

## Ficheros

| Fichero | Qué hace aquí |
|---|---|
| `$S/…/nsys/EstadoMovil.java` | `CAJA_DE_GRADO`, `cajaQueToca`; `NO_VENDIBLES` sale del mapa. |
| `$S/…/nsys/FilaCajaNsys.java` (nuevo) | Una fila de «Fuera de su caja». |
| `$S/…/nsys/TarjetasNsys.java` | `cajas(...)`, la regla de la lista (pura). |
| `$S/…/nsys/TarjetasPorPasar.java` | Gana `cajas`. |
| `$S/…/nsys/PorPasarNsys.java` | Lee y llama a `TarjetasNsys.cajas`. |
| `$S/src/test/…/OpenApiContractTest.java` | El esquema `FilaCajaNsys` en el contrato. |
| `$W/api/openapi.json`, `$W/src/shared/api/schema.d.ts` | Contrato copiado y tipos generados. |
| `$W/src/shared/api/client.ts` | El tipo `FilaCajaNsys`. |
| `$W/src/shared/styles/tokens.css` | El color del borde de la tarjeta. |
| `$W/…/stock/porPasar.ts` | Tarjeta nueva, grupos, lo que se copia y sus textos. |
| `$W/…/stock/TarjetasNsys.tsx` | Cinco columnas y el borde. |
| `$W/…/stock/ListasPorPasar.tsx` | `ListaCajas`. |
| `$W/…/stock/PorPasarNsys.tsx` | Botón «Ver», la lista y la copia. |
| `$W/src/modules/telefonos/test/fabrica.ts` | `filaCaja` y `cajas: []` en `tarjetas()`. |

---

### Task 0: Worktrees

**Files:** ninguno.

- [ ] **Step 1: Crear los dos worktrees desde `origin/main`**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q && git worktree add -b dev_fuera_de_su_caja /c/Users/dev/Documents/wt/servidor-fuera-caja origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q && git worktree add -b dev_fuera_de_su_caja /c/Users/dev/Documents/wt/web-fuera-caja origin/main
cd /c/Users/dev/Documents/wt/web-fuera-caja && npm ci 2>&1 | tail -3
```

Expected: los dos worktrees creados; `git log --oneline -1` da el merge de la 0.9.30 en cada uno; `npm ci` sin errores.

- [ ] **Step 2: Punto de partida en verde**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/wt/servidor-fuera-caja && mvn -q test -Dtest='EstadoMovilTest,TarjetasNsysTest,PorPasarNsysTest' 2>&1 | tail -8
cd /c/Users/dev/Documents/wt/web-fuera-caja && npx vitest run src/modules/telefonos 2>&1 | tail -6
```

Expected: sin fallos en los dos.

---

### Task 1: La regla grado → caja en `EstadoMovil`

**Files:**
- Modify: `$S/src/main/java/com/reparaciones/servidor/nsys/EstadoMovil.java`
- Test: `$S/src/test/java/com/reparaciones/servidor/nsys/EstadoMovilTest.java`

**Interfaces:**
- Consumes: `Normalizacion.caja(String location)` (ya existe: `null` → `""`, sin espacios a los lados).
- Produces: `EstadoMovil.CAJA_DE_GRADO` (`Map<String, String>`, en orden F, FD, RMA) y
  `static Optional<String> EstadoMovil.cajaQueToca(String grado, String location)`. `EstadoMovil.NO_VENDIBLES` sigue siendo un
  `Set<String>` con F, FD y RMA.

- [ ] **Step 1: Escribir las pruebas que fallan**

Al final de `EstadoMovilTest` (antes de la llave que cierra la clase), y con estos imports arriba: `java.util.List`,
`java.util.Optional`, `java.util.Set`.

```java
    // ---------------------------------------------------------------- «Fuera de su caja»: cada grado no vendible tiene su caja

    @Test void cadaGradoNoVendibleEnSuCajaNoTocaNada() {
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca("F", "CF021"));
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca("FD", "Desguace"));
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca("RMA", "Funcionalidad pendiente"));
        assertEquals(Optional.empty(), EstadoMovil.cajaQueToca(" rma ", " Funcionalidad pendiente "), "sin espacios a los lados");
    }

    @Test void fueraDeSuCajaOSinCajaLeTocaLaSuya() {
        assertEquals(Optional.of("CF021"), EstadoMovil.cajaQueToca("F", "CAJA REP. OFICINA"));
        assertEquals(Optional.of("CF021"), EstadoMovil.cajaQueToca("f", null));
        assertEquals(Optional.of("Desguace"), EstadoMovil.cajaQueToca("Fd", "Funcionalidad pendiente"));
        assertEquals(Optional.of("Funcionalidad pendiente"), EstadoMovil.cajaQueToca("RMA", ""));
        assertEquals(Optional.of("Funcionalidad pendiente"), EstadoMovil.cajaQueToca("RMA", "CF021"));
        assertEquals(Optional.of("CF021"), EstadoMovil.cajaQueToca("F", "cf021"), "la caja se compara tal cual");
    }

    @Test void unGradoVendibleNoTieneCaja() {
        for (String grado : new String[]{"A", "A-", "B", "ID", "n/a", "Ms", "", null})
            assertEquals(Optional.empty(), EstadoMovil.cajaQueToca(grado, "CAJA LISTOS"), String.valueOf(grado));
    }

    @Test void losNoVendiblesSonLosGradosConCajaYEnEseOrden() {
        assertEquals(Set.of("F", "FD", "RMA"), EstadoMovil.NO_VENDIBLES);
        assertEquals(List.of("CF021", "Desguace", "Funcionalidad pendiente"), List.copyOf(EstadoMovil.CAJA_DE_GRADO.values()));
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $S && mvn -q test -Dtest=EstadoMovilTest 2>&1 | tail -15`
Expected: error de compilación, `cannot find symbol … cajaQueToca` y `CAJA_DE_GRADO`.

- [ ] **Step 3: Implementar**

En `EstadoMovil.java`, imports: añadir `java.util.Collections`, `java.util.LinkedHashMap`, `java.util.Map` y `java.util.Optional`.
Sustituir el bloque de `NO_VENDIBLES` (el comentario y la constante) por:

```java
    /** La caja de NSYS de cada grado que no se vende («Fuera de su caja»): no vendible, desguace y devolución al proveedor, en el
     *  orden en que salen sus grupos. Los grados, en mayúsculas; las cajas, como las escribe NSYS. */
    public static final Map<String, String> CAJA_DE_GRADO = cajaDeGrado();

    private static Map<String, String> cajaDeGrado() {
        Map<String, String> cajas = new LinkedHashMap<>();
        cajas.put("F", "CF021");
        cajas.put("FD", "Desguace");
        cajas.put("RMA", "Funcionalidad pendiente");
        return Collections.unmodifiableMap(cajas);
    }

    /** Los grados que no se venden estén en la caja que estén (0.9.30): los que tienen caja propia. En mayúsculas; ID y n/a sí
     *  cuentan. */
    public static final Set<String> NO_VENDIBLES = CAJA_DE_GRADO.keySet();

    /** La caja que le toca a un móvil con este grado si no está en ella («Fuera de su caja»): el grado, sin espacios a los lados
     *  y sin mirar mayúsculas; {@code location}, la caja de NSYS sin espacios a los lados y tal cual (null o vacía: sin caja).
     *  Vacío si el grado no tiene caja propia o ya está en la suya. No mira el estado ni los trabajos del taller. */
    public static Optional<String> cajaQueToca(String grado, String location) {
        if (grado == null) return Optional.empty();
        String suya = CAJA_DE_GRADO.get(grado.strip().toUpperCase(Locale.ROOT));
        if (suya == null || suya.equals(Normalizacion.caja(location))) return Optional.empty();
        return Optional.of(suya);
    }
```

`conGrado` no cambia (sigue usando `NO_VENDIBLES`).

- [ ] **Step 4: Comprobar que pasan**

Run: `cd $S && mvn -q test -Dtest=EstadoMovilTest 2>&1 | tail -15`
Expected: sin fallos (las tres pruebas de la 0.9.30 siguen en verde).

- [ ] **Step 5: Commit**

```bash
cd $S && git add src/main/java/com/reparaciones/servidor/nsys/EstadoMovil.java src/test/java/com/reparaciones/servidor/nsys/EstadoMovilTest.java
git commit -m "feat: la caja de cada grado no vendible (F en CF021, FD en Desguace, RMA en Funcionalidad pendiente)"
```

---

### Task 2: La lista «Fuera de su caja» (`TarjetasNsys.cajas`)

**Files:**
- Create: `$S/src/main/java/com/reparaciones/servidor/nsys/FilaCajaNsys.java`
- Modify: `$S/src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java`
- Test: `$S/src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java`

**Interfaces:**
- Consumes: `EstadoMovil.cajaQueToca` y `EstadoMovil.CAJA_DE_GRADO` (Task 1); ya existen `LoQueVale.valor(List<CambioTaller>,
  DatoTaller, MovilNsys)`, `LoQueVale.siCambia(String, String)`, `Normalizacion.gradoSinBateria(String)`,
  `NsysDAO.claveCruce(String)`, `TarjetasNsys.caja(MovilNsys)` y `TallerNsys.porImei(List<CambioTaller>)`.
- Produces: el record `FilaCajaNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, boolean esEsim, String
  color, String grado, String gradoNsys, String caja, String ubicacion, String cajaQueToca)` y
  `static List<FilaCajaNsys> TarjetasNsys.cajas(List<MovilNsys> enEmpresa, Map<String, List<CambioTaller>> vigentes, Map<String,
  List<TrabajoAbierto>> abiertos)`; los dos mapas, por `NsysDAO.claveCruce`.

- [ ] **Step 1: Escribir las pruebas que fallan**

Al final de `TarjetasNsysTest` (antes de la llave que cierra la clase):

```java
    // ---------------------------------------------------------------- «Fuera de su caja»

    /** Un iPhone 13 128GB Azul con este grado de NSYS, en esta caja de NSYS (null: sin caja) y con este estado. */
    private static MovilNsys enCaja(String imei, String grade, String location, String ubicacion, EstadoMovil estado) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", location, "Proveedor A",
                LocalDate.of(2026, 10, 1), null, null, "13", "13", "128GB", "Azul", grade, false, ubicacion, ubicacion, estado,
                HOY.minusDays(20), HOY.minusDays(1));
    }

    private static final Map<String, List<TrabajoAbierto>> SIN_TRABAJOS = Map.of();

    @Test void unNoVendibleFueraDeSuCajaSaleConLaQueLeToca() {
        MovilNsys f = enCaja(IMEI, "F", "CAJA REP. OFICINA", "Taller · Reparación", EstadoMovil.STOCK);
        FilaCajaNsys fila = TarjetasNsys.cajas(List.of(f), Map.of(), SIN_TRABAJOS).get(0);
        assertEquals(IMEI, fila.imeiSn());
        assertEquals("13", fila.modelo());
        assertEquals("128GB", fila.capacidad());
        assertEquals("Azul", fila.color());
        assertEquals("F", fila.grado());
        assertNull(fila.gradoNsys(), "el grado es el de NSYS");
        assertEquals("CAJA REP. OFICINA", fila.caja());
        assertEquals("Taller · Reparación", fila.ubicacion());
        assertEquals("CF021", fila.cajaQueToca());
    }

    @Test void enSuCajaConGradoVendibleODePedidoNoSale() {
        List<MovilNsys> moviles = List.of(
                enCaja(IMEI, "RMA", "Funcionalidad pendiente", "Devolución", EstadoMovil.STOCK),
                enCaja(OTRO, "F", "CF021", "No vendible", EstadoMovil.NO_CUENTA),
                enCaja("350000000000003", "B", "CAJA LISTOS", "Listos", EstadoMovil.STOCK),
                enCaja("350000000000004", "RMA", "Pedidos realizados", null, EstadoMovil.DE_PEDIDO));
        assertTrue(TarjetasNsys.cajas(moviles, Map.of(), SIN_TRABAJOS).isEmpty());
    }

    @Test void sinCajaTambienSaleYUnNoCuentaTambien() {
        List<FilaCajaNsys> filas = TarjetasNsys.cajas(List.of(
                enCaja(IMEI, "RMA", null, "En revisión", EstadoMovil.STOCK),
                enCaja(OTRO, "Fd", "AFaltaRepuestos", "Esperando pieza", EstadoMovil.NO_CUENTA)), Map.of(), SIN_TRABAJOS);
        assertEquals(List.of("Desguace", "Funcionalidad pendiente"), filas.stream().map(FilaCajaNsys::cajaQueToca).toList());
        assertNull(filas.get(1).caja(), "sin caja");
        assertEquals("Fd", filas.get(0).grado(), "como esté escrito");
    }

    @Test void conUnTrabajoAbiertoDelTallerNoSale() {
        MovilNsys f = enCaja("f2lxabc123", "F", "CAJA REP. OFICINA", "Taller · Reparación", EstadoMovil.STOCK);
        Map<String, List<TrabajoAbierto>> abiertos = Map.of("F2LXABC123",
                List.of(new TrabajoAbierto("REPARACION", "Técnico 2", AYER, null)));
        assertTrue(TarjetasNsys.cajas(List.of(f), Map.of(), abiertos).isEmpty(), "cruza por la clave, sin mirar mayúsculas");
        assertEquals(1, TarjetasNsys.cajas(List.of(f), Map.of(), Map.of("F2LXABC123", List.of())).size(), "sin trabajos, sale");
    }

    @Test void mandaElGradoQueValeElDelTallerPorEncima() {
        MovilNsys rmaEnNsys = enCaja(IMEI, "RMA", "CAJA LISTOS", "Listos", EstadoMovil.STOCK);
        MovilNsys bEnNsys = enCaja(OTRO, "B", "CAJA REP. OFICINA", "Taller · Reparación", EstadoMovil.STOCK);
        Map<String, List<CambioTaller>> vigentes = TallerNsys.porImei(List.of(
                vigente(1, IMEI, GRADO, "A", "RMA"), vigente(2, OTRO, GRADO, "F", "B")));
        List<FilaCajaNsys> filas = TarjetasNsys.cajas(List.of(rmaEnNsys, bEnNsys), vigentes, SIN_TRABAJOS);
        assertEquals(List.of(OTRO), filas.stream().map(FilaCajaNsys::imeiSn).toList(), "el RMA corregido a A no sale; el B puesto a F, sí");
        assertEquals("F", filas.get(0).grado());
        assertEquals("B", filas.get(0).gradoNsys(), "NSYS aún no lo sabe: también está en «Cambio de grado»");
    }

    @Test void vanPorLaCajaQueTocaLuegoPorLaCajaDeHoyConSinCajaAlFinalYPorImei() {
        List<FilaCajaNsys> filas = TarjetasNsys.cajas(List.of(
                enCaja("350000000000009", "RMA", "CAJA REP. OFICINA", "Taller · Reparación", EstadoMovil.STOCK),
                enCaja("350000000000008", "F", null, "En revisión", EstadoMovil.STOCK),
                enCaja("350000000000007", "F", "CAJA REP. OFICINA", "Taller · Reparación", EstadoMovil.STOCK),
                enCaja("350000000000006", "FD", "CAJA LISTOS", "Listos", EstadoMovil.STOCK),
                enCaja("350000000000005", "F", "CAJA REP. OFICINA", "Taller · Reparación", EstadoMovil.STOCK),
                enCaja("350000000000004", "F", "CAJA LISTOS", "Listos", EstadoMovil.STOCK)), Map.of(), SIN_TRABAJOS);
        assertEquals(List.of("350000000000004", "350000000000005", "350000000000007", "350000000000008", "350000000000006",
                "350000000000009"), filas.stream().map(FilaCajaNsys::imeiSn).toList());
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $S && mvn -q test -Dtest=TarjetasNsysTest 2>&1 | tail -15`
Expected: error de compilación, `cannot find symbol … FilaCajaNsys` y `cajas`.

- [ ] **Step 3: Crear `FilaCajaNsys.java`**

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una fila de «Fuera de su caja» (spec «Fuera de su caja» §4.2): un móvil en la empresa con grado F, FD o RMA que NSYS tiene en
 *  una caja que no es la de su grado. {@code color} y {@code grado}: los que valen (el grado, sin el «100» de la batería y como
 *  esté escrito). {@code gradoNsys}: el de NSYS si el taller lo ha cambiado («—» si NSYS no lo sabe); null si es el mismo.
 *  {@code caja}: la de NSYS hoy (null: sin caja) y {@code ubicacion}, cómo la llama el ERP. {@code cajaQueToca}: «CF021»,
 *  «Desguace» o «Funcionalidad pendiente». */
public record FilaCajaNsys(String imeiSn, @Schema(nullable = true) String modelo, @Schema(nullable = true) String modeloMaestro,
                           @Schema(nullable = true) String capacidad, boolean esEsim, @Schema(nullable = true) String color,
                           String grado, @Schema(nullable = true) String gradoNsys, @Schema(nullable = true) String caja,
                           @Schema(nullable = true) String ubicacion, String cajaQueToca) {}
```

- [ ] **Step 4: Implementar `TarjetasNsys.cajas`**

En `TarjetasNsys.java`, justo después del método `static String caja(MovilNsys m)`. Si faltan, añadir los imports
`java.util.ArrayList`, `java.util.Comparator`, `java.util.List`, `java.util.Map` y `java.util.Optional`.

```java
    /** «Fuera de su caja» (spec §3): de los móviles en la empresa, los de estado STOCK o NO_CUENTA (un de pedido, no) cuyo grado
     *  que vale (NSYS con lo del taller vigente, sin el «100») es F, FD o RMA, que no están en la caja de su grado y que no tienen
     *  ningún trabajo abierto del taller. {@code vigentes} y {@code abiertos}, por {@link NsysDAO#claveCruce}. Por la caja que
     *  toca (el orden de {@link EstadoMovil#CAJA_DE_GRADO}), dentro por la caja de hoy (sin caja al final) y por IMEI. */
    static List<FilaCajaNsys> cajas(List<MovilNsys> enEmpresa, Map<String, List<CambioTaller>> vigentes,
                                    Map<String, List<TrabajoAbierto>> abiertos) {
        List<String> orden = List.copyOf(EstadoMovil.CAJA_DE_GRADO.values());
        List<FilaCajaNsys> filas = new ArrayList<>();
        for (MovilNsys m : enEmpresa) {
            if (m.estado() != EstadoMovil.STOCK && m.estado() != EstadoMovil.NO_CUENTA) continue;
            String clave = NsysDAO.claveCruce(m.imeiSn());
            List<CambioTaller> suyos = vigentes.getOrDefault(clave, List.of());
            String grade = LoQueVale.valor(suyos, DatoTaller.GRADO, m);
            String grado = Normalizacion.gradoSinBateria(grade).strip();
            Optional<String> toca = EstadoMovil.cajaQueToca(grado, m.location());
            if (toca.isEmpty() || !abiertos.getOrDefault(clave, List.of()).isEmpty()) continue;
            filas.add(new FilaCajaNsys(m.imeiSn(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.esEsim(),
                    LoQueVale.valor(suyos, DatoTaller.COLOR, m), grado, LoQueVale.siCambia(grade, m.grade()), caja(m),
                    m.ubicacion(), toca.get()));
        }
        filas.sort(Comparator.comparingInt((FilaCajaNsys f) -> orden.indexOf(f.cajaQueToca()))
                .thenComparing(f -> f.caja() == null)
                .thenComparing(f -> f.caja() == null ? "" : f.caja())
                .thenComparing(FilaCajaNsys::imeiSn));
        return filas;
    }
```

- [ ] **Step 5: Comprobar que pasan**

Run: `cd $S && mvn -q test -Dtest=TarjetasNsysTest 2>&1 | tail -15`
Expected: sin fallos.

- [ ] **Step 6: Commit**

```bash
cd $S && git add src/main/java/com/reparaciones/servidor/nsys/FilaCajaNsys.java src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java
git commit -m "feat: la lista Fuera de su caja (F, FD y RMA que no estan en la caja de su grado)"
```

---

### Task 3: `cajas` en la respuesta de las tarjetas y en el contrato

**Files:**
- Modify: `$S/src/main/java/com/reparaciones/servidor/nsys/TarjetasPorPasar.java`
- Modify: `$S/src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java` (método `tarjetas()`)
- Modify: `$S/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Test: `$S/src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java`

**Interfaces:**
- Consumes: `TarjetasNsys.cajas` y `FilaCajaNsys` (Task 2); ya existen `NsysDAO.enEmpresa()`, `NsysDAO.trabajosAbiertos()`
  (por `claveCruce`), `TallerDAO.vigentes()` y `TallerNsys.porImei(...)`.
- Produces: `TarjetasPorPasar.cajas()` (`List<FilaCajaNsys>`), octavo componente del record; el esquema `FilaCajaNsys` y el
  campo `cajas` de `TarjetasPorPasar` en `target/openapi.json`, para la Task 4.

La spec pide «la prueba del controlador de las tarjetas»; el controlador solo delega (`porPasar.tarjetas()`), así que la
prueba va en el servicio (`PorPasarNsysTest`) y en el contrato.

- [ ] **Step 1: Escribir la prueba que falla**

En `PorPasarNsysTest`, después de `sinImeisFueraDeNsysNiAvisosNoSeLeeNadaMas`:

```java
    @Test void lasTarjetasLlevanLosQueEstanFueraDeSuCaja() {
        MovilNsys rma = new MovilNsys(IMEI, "BT-9001", "Apple iPhone 13 128GB Blue", "RMA", "Available", "CAJA REP. OFICINA",
                "Proveedor A", LocalDate.of(2026, 10, 1), null, null, "13", "13", "128GB", "Azul", "RMA", false,
                "Taller · Reparación", "Taller · Reparación", EstadoMovil.STOCK, AHORA.minusDays(20), AHORA.minusDays(1));
        MovilNsys conTrabajo = new MovilNsys("350000000000002", "BT-9001", "Apple iPhone 13 128GB Blue", "F", "Available",
                "CAJA REP. OFICINA", "Proveedor A", LocalDate.of(2026, 10, 1), null, null, "13", "13", "128GB", "Azul", "F", false,
                "Taller · Reparación", "Taller · Reparación", EstadoMovil.STOCK, AHORA.minusDays(20), AHORA.minusDays(1));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI, "350000000000002"));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI, "350000000000002"));
        when(nsys.enEmpresa()).thenReturn(List.of(rma, conTrabajo));
        when(nsys.trabajosAbiertos()).thenReturn(Map.of("350000000000002",
                List.of(new TrabajoAbierto("REPARACION", "Técnico 2", AHORA.minusHours(2), null))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI), t.cajas().stream().map(FilaCajaNsys::imeiSn).toList());
        assertEquals("Funcionalidad pendiente", t.cajas().get(0).cajaQueToca());
    }
```

- [ ] **Step 2: Comprobar que falla**

Run: `cd $S && mvn -q test -Dtest=PorPasarNsysTest 2>&1 | tail -15`
Expected: error de compilación, `cannot find symbol … cajas()`.

- [ ] **Step 3: `TarjetasPorPasar` gana `cajas`**

Sustituir el fichero entero por:

```java
package com.reparaciones.servidor.nsys;

import java.util.List;

/** Las tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente, aparte los
 *  descartados de «No está en NSYS» (pieza 3 §4.2), del descarte más reciente al más antiguo, las filas «Sin grado en NSYS» de
 *  «Revisar a mano» (pieza 4 §4.1), del trabajo más antiguo al más reciente, los ignorados de Color, Grado y Revisar (0.9.27
 *  §4.2), del ignorado más reciente al más antiguo, y la quinta tarjeta, «Fuera de su caja» (spec «Fuera de su caja» §4.2), por
 *  la caja que toca. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas,
                               List<FilaSinGradoNsys> sinGrado, List<FilaIgnoradaNsys> ignorados, List<FilaCajaNsys> cajas) {

    /** Sin «Fuera de su caja». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas, List<FilaSinGradoNsys> sinGrado,
                            List<FilaIgnoradaNsys> ignorados) {
        this(color, grado, altas, revisar, descartadas, sinGrado, ignorados, List.of());
    }

    /** Sin ignorados ni «Fuera de su caja». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas, List<FilaSinGradoNsys> sinGrado) {
        this(color, grado, altas, revisar, descartadas, sinGrado, List.of(), List.of());
    }

    /** Sin filas «Sin grado en NSYS», ignorados ni «Fuera de su caja». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas) {
        this(color, grado, altas, revisar, descartadas, List.of(), List.of(), List.of());
    }
}
```

- [ ] **Step 4: `PorPasarNsys.tarjetas()` la rellena**

En `PorPasarNsys.java`, en `tarjetas()`: el comentario de la clase y el del método dicen «cuatro tarjetas»; en el del método,
añadir al final «y «Fuera de su caja» (con los móviles en la empresa, lo del taller vigente y los trabajos abiertos)».
Sustituir el `return` del método por:

```java
        List<FilaCajaNsys> cajas = TarjetasNsys.cajas(nsysDao.enEmpresa(), TallerNsys.porImei(tallerDao.vigentes()),
                nsysDao.trabajosAbiertos());
        return new TarjetasPorPasar(TarjetasNsys.color(salen.moviles(), marcas, salen.vueltas()),
                TarjetasNsys.grado(salen.moviles(), marcas, salen.vueltas()), TarjetasNsys.altas(altas, marcas, descartes),
                revisar.salen(), TarjetasNsys.descartadas(altas, descartes), sinGrado.salen(), ignorados.filas(), cajas);
```

- [ ] **Step 5: Comprobar que pasa, con todo `PorPasarNsysTest`**

Run: `cd $S && mvn -q test -Dtest='PorPasarNsysTest,TarjetasNsysTest' 2>&1 | tail -15`
Expected: sin fallos. Las pruebas anteriores no cambian: con los DAO simulados, `enEmpresa()` da una lista vacía.

- [ ] **Step 6: El contrato**

En `OpenApiContractTest.java`, en el método que comprueba los esquemas de «Por pasar a NSYS» (el que tiene la lista
`"TarjetasPorPasar", "FilaColorNsys", …`), justo después del `for` de esos esquemas:

```java
        // «Fuera de su caja»: la quinta tarjeta de «Por pasar a NSYS».
        assertTrue(esquemas.has("FilaCajaNsys"), () -> "falta el esquema FilaCajaNsys; publicados: " + nombres(esquemas));
        assertTrue(esquemas.path("TarjetasPorPasar").path("properties").has("cajas"), "TarjetasPorPasar no lleva «cajas»");
        assertNullable(esquemas, "FilaCajaNsys", "modelo", "modeloMaestro", "capacidad", "color", "gradoNsys", "caja", "ubicacion");
        assertNoNullable(esquemas, "FilaCajaNsys", "imeiSn", "esEsim", "grado", "cajaQueToca");
```

- [ ] **Step 7: Todo el servidor**

Run: `cd $S && mvn test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD SUCCESS|BUILD FAILURE|ERROR\]" | tail -8`
Expected: `BUILD SUCCESS`, 0 fallos (incluye `OpenApiContractTest`, que levanta el contexto y escribe `target/openapi.json`).

Run: `cd $S && grep -o '"FilaCajaNsys"\|"cajaQueToca"' target/openapi.json | sort | uniq -c`
Expected: las dos cadenas aparecen.

- [ ] **Step 8: Commit**

```bash
cd $S && git add src/main/java/com/reparaciones/servidor/nsys/TarjetasPorPasar.java src/main/java/com/reparaciones/servidor/nsys/PorPasarNsys.java src/test/java/com/reparaciones/servidor/nsys/PorPasarNsysTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: las tarjetas de Por pasar a NSYS llevan los moviles fuera de su caja"
```

---

### Task 4: Web — el tipo y las reglas puras

**Files:**
- Modify: `$W/api/openapi.json` (copia), `$W/src/shared/api/schema.d.ts` (generado), `$W/src/shared/api/client.ts`
- Modify: `$W/src/modules/telefonos/test/fabrica.ts`
- Modify: `$W/src/modules/telefonos/stock/porPasar.ts`
- Modify: `$W/src/modules/telefonos/stock/PorPasarNsys.tsx` (solo `VACIAS`)
- Test: `$W/src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Consumes: `target/openapi.json` de la Task 3.
- Produces (de `porPasar.ts`): `type GrupoCaja = { cajaQueToca: string; grado: string; filas: FilaCajaNsys[] }`,
  `gruposCajas(filas: readonly FilaCajaNsys[]): GrupoCaja[]`, `textoGrupoCaja(g: GrupoCaja): string`,
  `imeisDeCaja(filas: readonly FilaCajaNsys[]): string`, `avisoImeisCopiados(n: number, caja: string): string`,
  `notaGradoNsys(f: FilaCajaNsys): string | null`. De `fabrica.ts`: `filaCaja(p?)`. De `client.ts`: el tipo `FilaCajaNsys`.

- [ ] **Step 1: Contrato y tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-fuera-caja/target/openapi.json /c/Users/dev/Documents/wt/web-fuera-caja/api/openapi.json
cd /c/Users/dev/Documents/wt/web-fuera-caja && npm run api:types:offline && git diff --stat
```

Expected: cambian `api/openapi.json` y `src/shared/api/schema.d.ts` (con `FilaCajaNsys` y `cajas` en `TarjetasPorPasar`).

En `src/shared/api/client.ts`, después de la línea de `PeticionIgnorado`:

```ts

/** «Fuera de su caja»: la quinta tarjeta de «Por pasar a NSYS». Un móvil en la empresa con grado F, FD o RMA (`grado`, el que vale)
 *  en una caja que no es la de su grado: `caja`, la de NSYS hoy (null: sin caja); `ubicacion`, cómo la llama el ERP;
 *  `cajaQueToca`, «CF021», «Desguace» o «Funcionalidad pendiente»; `gradoNsys`, el de NSYS si el taller lo ha cambiado. */
export type FilaCajaNsys = components['schemas']['FilaCajaNsys']
```

- [ ] **Step 2: La fábrica de pruebas y `VACIAS`**

En `src/modules/telefonos/test/fabrica.ts`: añadir `FilaCajaNsys` al `import type` de `@/shared/api/client`; en `tarjetas()`,
el objeto pasa a `{ color: [], grado: [], altas: [], revisar: [], descartadas: [], sinGrado: [], ignorados: [], cajas: [], ...p }`;
y, después de `filaIgnorada`:

```ts

/** «Fuera de su caja»: un iPhone 12 64GB de grado F que NSYS tiene en «CAJA REP. OFICINA»; le toca «CF021». */
export function filaCaja(p: Partial<FilaCajaNsys> = {}): FilaCajaNsys {
  return {
    imeiSn: '350000000000214', modelo: '12', modeloMaestro: '12', capacidad: '64GB', esEsim: false, color: 'Negro', grado: 'F',
    gradoNsys: null, caja: 'CAJA REP. OFICINA', ubicacion: 'Taller · Reparación', cajaQueToca: 'CF021', ...p,
  }
}
```

En `src/modules/telefonos/stock/PorPasarNsys.tsx`, la constante `VACIAS` pasa a:

```ts
const VACIAS: TarjetasPorPasar = { color: [], grado: [], altas: [], revisar: [], descartadas: [], sinGrado: [], ignorados: [], cajas: [] }
```

- [ ] **Step 3: Escribir las pruebas que fallan**

En `porPasar.test.ts`: añadir `filaCaja` al import de `../test/fabrica` y `avisoImeisCopiados, gruposCajas, imeisDeCaja,
notaGradoNsys, textoGrupoCaja` al import de `./porPasar`. Al final del fichero:

```ts

describe('«Fuera de su caja»: grupos por la caja que toca y lo que se copia', () => {
  const filas = [
    filaCaja(),
    filaCaja({ imeiSn: '350000000000222', grado: 'F', gradoNsys: 'B' }),
    filaCaja({ imeiSn: '350000000000248', grado: 'Fd', caja: 'Funcionalidad pendiente', ubicacion: 'Devolución', cajaQueToca: 'Desguace' }),
    filaCaja({ imeiSn: '350000000000263', grado: 'RMA', caja: null, ubicacion: 'En revisión', cajaQueToca: 'Funcionalidad pendiente' }),
  ]

  it('un grupo por caja que toca, en el orden en que llegan, con su grado en mayúsculas', () => {
    const grupos = gruposCajas(filas)
    expect(grupos.map((g) => g.cajaQueToca)).toEqual(['CF021', 'Desguace', 'Funcionalidad pendiente'])
    expect(grupos.map((g) => g.grado)).toEqual(['F', 'FD', 'RMA'])
    expect(grupos[0].filas.map((f) => f.imeiSn)).toEqual(['350000000000214', '350000000000222'])
    expect(gruposCajas([])).toEqual([])
  })

  it('la cabecera del grupo dice el grado y cuántos', () => {
    const [cf021, desguace] = gruposCajas(filas)
    expect(textoGrupoCaja(cf021)).toBe('grado F · 2 móviles')
    expect(textoGrupoCaja(desguace)).toBe('grado FD · 1 móvil')
  })

  it('se copian los IMEIs del grupo, uno por línea, y el aviso dice para qué caja', () => {
    expect(imeisDeCaja(gruposCajas(filas)[0].filas)).toBe('350000000000214\n350000000000222')
    expect(avisoImeisCopiados(3, 'CF021')).toBe('Copiado: 3 IMEIs para «CF021». Búscalos en NSYS y cámbiales la «Location»')
    expect(avisoImeisCopiados(1, 'Desguace')).toBe('Copiado: 1 IMEI para «Desguace». Búscalos en NSYS y cámbiales la «Location»')
  })

  it('la nota del grado solo sale si NSYS aún tiene otro', () => {
    expect(notaGradoNsys(filas[0])).toBeNull()
    expect(notaGradoNsys(filas[1])).toBe('en NSYS aún B: también en «Cambio de grado»')
  })
})
```

- [ ] **Step 4: Comprobar que fallan**

Run: `cd $W && npx vitest run src/modules/telefonos/stock/porPasar.test.ts 2>&1 | tail -12`
Expected: FAIL, `gruposCajas` (y las demás) no existen en `./porPasar`.

- [ ] **Step 5: Implementar en `porPasar.ts`**

Añadir `FilaCajaNsys` al `import type` de `@/shared/api/client` que ya hay arriba. Al final del fichero:

```ts

// ---------------------------------------------------------------- «Fuera de su caja»: los F, FD y RMA que no están en la caja de su grado

/** Los móviles que van a la misma caja; `grado`, el del grupo en mayúsculas («Fd» es FD). */
export type GrupoCaja = { cajaQueToca: string; grado: string; filas: FilaCajaNsys[] }

/** Las filas por la caja que toca, en el orden en que llegan (el servidor ya las manda por caja que toca, caja de hoy e IMEI). */
export function gruposCajas(filas: readonly FilaCajaNsys[]): GrupoCaja[] {
  const grupos: GrupoCaja[] = []
  for (const f of filas) {
    let grupo = grupos.find((g) => g.cajaQueToca === f.cajaQueToca)
    if (!grupo) {
      grupo = { cajaQueToca: f.cajaQueToca, grado: f.grado.trim().toUpperCase(), filas: [] }
      grupos.push(grupo)
    }
    grupo.filas.push(f)
  }
  return grupos
}

/** «grado F · 3 móviles», al lado de «Llevar a «CF021»». */
export const textoGrupoCaja = (g: GrupoCaja): string => `grado ${g.grado} · ${g.filas.length} ${palabraMoviles(g.filas.length)}`

/** Lo que copia «Copiar IMEIs»: los IMEIs del grupo, uno por línea, para buscarlos en NSYS. */
export const imeisDeCaja = (filas: readonly FilaCajaNsys[]): string => filas.map((f) => f.imeiSn).join('\n')

export const avisoImeisCopiados = (n: number, caja: string): string =>
  `Copiado: ${n} ${n === 1 ? 'IMEI' : 'IMEIs'} para «${caja}». Búscalos en NSYS y cámbiales la «Location»`

/** «en NSYS aún B: también en «Cambio de grado»»: el taller ha puesto el grado y NSYS todavía tiene otro; si no, nada. */
export const notaGradoNsys = (f: FilaCajaNsys): string | null =>
  f.gradoNsys ? `en NSYS aún ${f.gradoNsys}: también en «Cambio de grado»` : null
```

- [ ] **Step 6: Comprobar que pasan, con tipos**

Run: `cd $W && npx vitest run src/modules/telefonos/stock/porPasar.test.ts 2>&1 | tail -8 && npm run typecheck 2>&1 | tail -15`
Expected: todas las pruebas en verde y `typecheck` sin errores.

- [ ] **Step 7: Commit**

```bash
cd $W && git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/modules/telefonos/test/fabrica.ts src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/PorPasarNsys.tsx
git commit -m "feat: tipos y reglas de Fuera de su caja (grupos por caja y copiar IMEIs)"
```

---

### Task 5: Web — la quinta tarjeta y su lista

**Files:**
- Modify: `$W/src/shared/styles/tokens.css`
- Modify: `$W/src/modules/telefonos/stock/porPasar.ts`
- Modify: `$W/src/modules/telefonos/stock/TarjetasNsys.tsx`
- Modify: `$W/src/modules/telefonos/stock/ListasPorPasar.tsx`
- Modify: `$W/src/modules/telefonos/stock/PorPasarNsys.tsx`
- Test: `$W/src/modules/telefonos/stock/porPasar.test.ts`, `$W/src/modules/telefonos/stock/PorPasarNsys.test.tsx`

**Interfaces:**
- Consumes (Task 4): `FilaCajaNsys`, `GrupoCaja`, `gruposCajas`, `textoGrupoCaja`, `imeisDeCaja`, `avisoImeisCopiados`,
  `notaGradoNsys`, `filaCaja`. Ya existen `SIN_CAJA`, `modeloDeFila`, `PildoraGrado` (`./PildoraGrado`),
  `copiarAlPortapapeles` y `avisar`.
- Produces: `Tarjeta` incluye `'cajas'`; `ListaCajas` en `ListasPorPasar.tsx` con props
  `{ filas: FilaCajaNsys[]; vacio: string; onAbrirImei: (imeiSn: string) => void; onCopiar: (grupo: GrupoCaja) => void }`.

- [ ] **Step 1: Escribir las pruebas que fallan**

En `porPasar.test.ts`, dentro del `describe` de «Fuera de su caja» de la Task 4 (antes de su `})` final), y añadiendo
`cuentas`, `NOMBRE`, `EXPLICACION`, `AYUDA`, `VACIA` y `totalPendientes` al import de `./porPasar` si alguno falta:

```ts

  it('la tarjeta cuenta sus filas, sin «hechos», y suma en el número de la pestaña', () => {
    const t = tarjetas({ grado: [filaGrado()], cajas: filas })
    expect(cuentas(t, 'cajas')).toEqual({ total: 4, hechos: 0, pendientes: 4 })
    expect(totalPendientes(t)).toBe(5)
  })

  it('con un servidor anterior, que no manda «cajas», la tarjeta está a cero', () => {
    const anterior = { ...tarjetas({ grado: [filaGrado()] }), cajas: undefined } as unknown as TarjetasPorPasar
    expect(cuentas(anterior, 'cajas')).toEqual({ total: 0, hechos: 0, pendientes: 0 })
    expect(totalPendientes(anterior)).toBe(1)
  })

  it('sus textos', () => {
    expect(NOMBRE.cajas).toBe('Fuera de su caja')
    expect(EXPLICACION.cajas).toBe('F, FD o RMA en una caja que no es la de su grado: llévalo a su caja y cámbiale la «Location» en NSYS.')
    expect(AYUDA.cajas).toContain('los F van en «CF021», los FD en «Desguace» y los RMA en «Funcionalidad pendiente»')
    expect(AYUDA.cajas).toContain('No salen los que tienen un trabajo abierto del taller ni los de pedido.')
    expect(VACIA.cajas).toBe('No hay móviles F, FD o RMA fuera de su caja.')
  })
```

En `PorPasarNsys.test.tsx`: añadir `filaCaja` al import de `../test/fabrica`. Al final del fichero:

```tsx

describe('«Fuera de su caja»: la quinta tarjeta', () => {
  const CAJAS = [
    filaCaja(),
    filaCaja({ imeiSn: '350000000000222', gradoNsys: 'B' }),
    filaCaja({ imeiSn: '350000000000230', caja: null, ubicacion: 'En revisión' }),
    filaCaja({ imeiSn: '350000000000263', grado: 'RMA', caja: null, ubicacion: 'En revisión', cajaQueToca: 'Funcionalidad pendiente' }),
  ]

  it('dice cuántos hay y «Ver» abre su lista con la ayuda', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ cajas: CAJAS }))
    const tarjeta = screen.getByRole('article', { name: 'Fuera de su caja' })
    expect(tarjeta).toHaveTextContent('4 móviles')
    expect(tarjeta).toHaveTextContent('F, FD o RMA en una caja que no es la de su grado')
    await user.click(within(tarjeta).getByRole('button', { name: 'Ver' }))
    expect(screen.getByRole('button', { name: 'Fuera de su caja' })).toHaveAttribute('aria-pressed', 'true')
    expect(cabeceras(screen.getByRole('table', { name: 'Fuera de su caja' }))).toEqual(['IMEI', 'Modelo', 'Color', 'Grado', 'Caja de hoy en NSYS'])
    expect(screen.getByText(/los F van en «CF021»/)).toBeInTheDocument()
  })

  it('sin móviles, «Ver» no se puede pulsar y la lista lo dice', async () => {
    const user = userEvent.setup()
    pintar(tarjetas())
    const tarjeta = screen.getByRole('article', { name: 'Fuera de su caja' })
    expect(tarjeta).toHaveTextContent('0 móviles')
    expect(within(tarjeta).getByRole('button', { name: 'Ver' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Fuera de su caja' }))
    expect(screen.getByRole('table', { name: 'Fuera de su caja' })).toHaveTextContent('No hay móviles F, FD o RMA fuera de su caja.')
  })

  it('la lista va por la caja que toca, con «sin caja», el nombre del ERP y la nota de «Cambio de grado»', async () => {
    const user = userEvent.setup()
    const abrir = pintar(tarjetas({ cajas: CAJAS }))
    await user.click(screen.getByRole('button', { name: 'Fuera de su caja' }))
    const [grupoF, uno, dos, tres, grupoRma, cuatro] = filas(screen.getByRole('table', { name: 'Fuera de su caja' }))
    expect(grupoF).toHaveTextContent('Llevar a «CF021»')
    expect(grupoF).toHaveTextContent('grado F · 3 móviles')
    expect(uno).toHaveTextContent('iPhone 12 64GB')
    expect(uno).toHaveTextContent('Negro')
    expect(uno).toHaveTextContent('CAJA REP. OFICINA')
    expect(uno).toHaveTextContent('Taller · Reparación')
    expect(dos).toHaveTextContent('en NSYS aún B: también en «Cambio de grado»')
    expect(tres).toHaveTextContent('sin caja')
    expect(tres).toHaveTextContent('En revisión')
    expect(grupoRma).toHaveTextContent('Llevar a «Funcionalidad pendiente»')
    expect(grupoRma).toHaveTextContent('grado RMA · 1 móvil')
    expect(cuatro).toHaveTextContent('350000000000263')
    await user.click(within(uno).getByRole('button', { name: 'Abrir 350000000000214' }))
    expect(abrir).toHaveBeenCalledWith('350000000000214')
  })

  it('«Copiar IMEIs» copia los del grupo, uno por línea, y avisa', async () => {
    const user = userEvent.setup()
    pintar(tarjetas({ cajas: CAJAS }))
    await user.click(screen.getByRole('button', { name: 'Fuera de su caja' }))
    const boton = screen.getByRole('button', { name: 'Copiar IMEIs para CF021' })
    expect(boton).toHaveTextContent('Copiar IMEIs (3)')
    await user.click(boton)
    await waitFor(async () => expect(await navigator.clipboard.readText()).toBe('350000000000214\n350000000000222\n350000000000230'))
    expect(await screen.findByTestId('aviso-breve'))
      .toHaveTextContent('Copiado: 3 IMEIs para «CF021». Búscalos en NSYS y cámbiales la «Location»')
  })
})
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd $W && npx vitest run src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/PorPasarNsys.test.tsx 2>&1 | tail -20`
Expected: FAIL; no existe la tarjeta «Fuera de su caja» ni `NOMBRE.cajas`.

- [ ] **Step 3: El color del borde**

En `src/shared/styles/tokens.css`, dentro del bloque `@theme`, justo después de la línea `--color-taller-text: #6D28D9;`:

```css
  /* «Fuera de su caja» (prototipo): el borde de arriba de la quinta tarjeta de «Por pasar a NSYS». */
  --color-caja: #0D9488;
```

- [ ] **Step 4: La tarjeta en `porPasar.ts`**

Sustituir las dos líneas de `Tarjeta` y `TARJETAS` por:

```ts
export type Tarjeta = 'color' | 'grado' | 'altas' | 'revisar' | 'cajas'
export const TARJETAS: readonly Tarjeta[] = ['color', 'grado', 'altas', 'revisar', 'cajas']
```

En `NOMBRE`, añadir al final del objeto `cajas: 'Fuera de su caja',`. En `EXPLICACION`, añadir al final:

```ts
  cajas: 'F, FD o RMA en una caja que no es la de su grado: llévalo a su caja y cámbiale la «Location» en NSYS.',
```

En `AYUDA`, añadir al final:

```ts
  cajas: 'Móviles con grado F, FD o RMA que NSYS tiene en la empresa y fuera de la caja de su grado: los F van en «CF021», los FD en «Desguace» y los RMA en «Funcionalidad pendiente». Copia los IMEIs de cada caja, búscalos en NSYS y cámbiales la «Location». Cada fila se va sola cuando la importación (cada 30 min) ve la caja nueva. No salen los que tienen un trabajo abierto del taller ni los de pedido.',
```

En `VACIA`, añadir al final `cajas: 'No hay móviles F, FD o RMA fuera de su caja.',`.

En `cuentas`, justo antes de la línea `const filas: readonly ConHecho[] = …`:

```ts
  if (cual === 'cajas') {
    // `?? []`: un servidor anterior a «Fuera de su caja» (vuelta atrás) no manda `cajas`. No lleva «Hecho».
    const n = (t.cajas ?? []).length
    return { total: n, hechos: 0, pendientes: n }
  }
```

Y poner al día los comentarios que dicen «cuatro tarjetas» en `cuentas` y `totalPendientes` («las tarjetas»).

- [ ] **Step 5: Cinco columnas en `TarjetasNsys.tsx`**

`BORDE` gana la tarjeta nueva:

```tsx
const BORDE: Record<Tarjeta, string> = {
  color: 'border-t-taller', grado: 'border-t-tipo-reparacion-text', altas: 'border-t-fila-urgente-brd', revisar: 'border-t-ambar',
  cajas: 'border-t-caja',
}
```

En el `div` que envuelve las tarjetas, `grid-cols-4` pasa a `grid-cols-5`. El comentario del componente pasa a «Las cinco
tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3.1 y «Fuera de su caja» §5)».

- [ ] **Step 6: `ListaCajas` en `ListasPorPasar.tsx`**

Imports: `import { Fragment, useState, type ReactNode } from 'react'`; añadir `FilaCajaNsys` al `import type` de
`@/shared/api/client`; añadir `gruposCajas, notaGradoNsys, textoGrupoCaja, type GrupoCaja` al import de `./porPasar` (que ya
trae `modeloDeFila` y `SIN_CAJA`); y `import { PildoraGrado } from './PildoraGrado'`. Al final del fichero:

```tsx

type CajasProps = { filas: FilaCajaNsys[]; vacio: string; onAbrirImei: (imeiSn: string) => void; onCopiar: (grupo: GrupoCaja) => void }

/** «Fuera de su caja» (spec «Fuera de su caja» §5): los F, FD y RMA que no están en la caja de su grado, en grupos por la caja que
 *  toca, cada uno con «Copiar IMEIs». Sin «Hecho»: la fila se va sola cuando la importación ve la caja nueva. */
export function ListaCajas({ filas, vacio, onAbrirImei, onCopiar }: CajasProps) {
  const columnas = ['IMEI', 'Modelo', 'Color', 'Grado', 'Caja de hoy en NSYS']
  return (
    <div className="min-h-0 flex-1 overflow-auto rounded-lg border border-fila-sep">
      <table aria-label="Fuera de su caja" className="w-full border-collapse text-[12.5px] text-azul-medio">
        <thead className="sticky top-0 bg-crema">
          <tr>{columnas.map((c) => <th key={c} scope="col" className="px-2 py-1.5 text-left font-bold whitespace-nowrap">{c}</th>)}</tr>
        </thead>
        <tbody>
          {filas.length === 0 && (
            <tr><td colSpan={columnas.length} className="px-2 py-3 text-[12px] text-azul-gris">{vacio}</td></tr>
          )}
          {gruposCajas(filas).map((g) => (
            <Fragment key={g.cajaQueToca}>
              <tr className="border-t border-fila-sep bg-seleccion-suave">
                <td colSpan={columnas.length} className="px-2 py-1.5">
                  <span className="flex items-center gap-2.5">
                    <span className="text-[13px] font-bold">{`Llevar a «${g.cajaQueToca}»`}</span>
                    <span className="text-[12px] text-azul-gris">{textoGrupoCaja(g)}</span>
                    <button type="button" aria-label={`Copiar IMEIs para ${g.cajaQueToca}`} onClick={() => onCopiar(g)}
                      className={cn(PEQUENO, 'ml-auto')}>{`Copiar IMEIs (${g.filas.length})`}</button>
                  </span>
                </td>
              </tr>
              {g.filas.map((f) => {
                const nota = notaGradoNsys(f)
                return (
                  <tr key={f.imeiSn} className="border-t border-fila-sep">
                    <td className={cn(CELDA, 'whitespace-nowrap')}><Imei imeiSn={f.imeiSn} onAbrir={onAbrirImei} /></td>
                    <td className={cn(CELDA, 'whitespace-nowrap')}>{modeloDeFila(f)}</td>
                    <td className={cn(CELDA, 'whitespace-nowrap')}>{f.color ?? '—'}</td>
                    <td className={cn(CELDA, 'whitespace-nowrap')}>
                      <PildoraGrado grado={f.grado} />
                      {nota && <span className={GRIS}>{nota}</span>}
                    </td>
                    <td className={cn(CELDA, 'whitespace-nowrap')}>
                      {f.caja ? f.caja : <span className="font-semibold text-ambar-grafico">{SIN_CAJA}</span>}
                      <span className={GRIS}>{f.ubicacion ?? '—'}</span>
                    </td>
                  </tr>
                )
              })}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 7: `PorPasarNsys.tsx` la pinta y copia**

Imports: `ListaCajas` se añade al import de `./ListasPorPasar`; `avisoImeisCopiados` e `imeisDeCaja`, al de `./porPasar`.

En `boton(cual)`, el último tramo (el de «Revisar a mano») pasa a servir a las dos tarjetas sin botón propio:

```tsx
    const n = cuentas(t, cual).total
    return (
      <button type="button" disabled={n === 0} title={n === 0 ? SIN_NADA : undefined}
        onClick={(e) => { e.stopPropagation(); setElegida(cual) }} className={SECUNDARIO}>Ver</button>
    )
```

En el JSX, después del bloque `{elegida === 'revisar' && ( … )}`:

```tsx
      {/* `?? []` en `cajas`: un servidor anterior a «Fuera de su caja» (vuelta atrás) no lo manda. */}
      {elegida === 'cajas' && (
        <ListaCajas filas={t.cajas ?? []} vacio={vacio} onAbrirImei={onAbrirImei}
          onCopiar={(g) => {
            void copiarAlPortapapeles(imeisDeCaja(g.filas))
            avisar({ texto: avisoImeisCopiados(g.filas.length, g.cajaQueToca), tipo: 'ok' })
          }} />
      )}
```

El comentario del componente pasa de «las cuatro tarjetas» a «las cinco tarjetas».

- [ ] **Step 8: Comprobar que pasan**

Run: `cd $W && npx vitest run src/modules/telefonos 2>&1 | tail -10`
Expected: todo en verde. Si una prueba anterior de `PorPasarNsys.test.tsx` o de `StockTelefonosPage.test.tsx` falla por dar
con dos botones «Ver» (el de «Revisar a mano» y el nuevo), acotarla a su tarjeta:
`within(screen.getByRole('article', { name: 'Revisar a mano' })).getByRole('button', { name: 'Ver' })`.

Run: `cd $W && npm run lint 2>&1 | tail -8 && npm run typecheck 2>&1 | tail -8`
Expected: sin errores.

- [ ] **Step 9: Verlo en el navegador**

Arrancar la web contra preprod como en las versiones anteriores (`npm run dev` con el proxy que ya tiene el proyecto), entrar
con una cuenta de supertécnico de `~/.env.e2e`, abrir «Stock de teléfonos» → «Por pasar a NSYS» y comprobar con el prototipo
al lado (`docs/superpowers/specs/assets/2026-11-10-prototipo-fuera-de-su-caja.html`): cinco tarjetas en una fila sin que se
corten los textos, la quinta con el borde verde azulado y, con el servidor de preprod aún sin esta versión, a 0 y con la
lista vacía (sin errores en la consola). Si las cinco tarjetas no caben a 1366 px de ancho, decirlo al usuario antes de
seguir.

- [ ] **Step 10: Commit**

```bash
cd $W && git add src/shared/styles/tokens.css src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/stock/TarjetasNsys.tsx src/modules/telefonos/stock/ListasPorPasar.tsx src/modules/telefonos/stock/PorPasarNsys.tsx src/modules/telefonos/stock/PorPasarNsys.test.tsx
git commit -m "feat: quinta tarjeta Fuera de su caja en Por pasar a NSYS, con Copiar IMEIs por caja"
```

(Si en el Step 8 hubo que acotar una prueba de `StockTelefonosPage.test.tsx`, añadir ese fichero al `git add`.)

---

### Task 6: Versión, changelog, paridad y novedades

**Antes de empezar:** preguntar al usuario el número de versión (hoy sería la 0.9.31). En lo que sigue, `X` es ese número.

**Files:**
- Modify: `$W/package.json`, `$W/package-lock.json`, `$W/CHANGELOG.md`, `$W/docs/paridad/imeis.md`
- Raíz: Create `docs/novedades/NOVEDADES-vX.md`

- [ ] **Step 1: Versión y changelog de la web**

`cd $W && npm version X --no-git-tag-version`. En `CHANGELOG.md`, encima de `## [0.9.30]`:

```markdown
## [X] - 2026-11-?? — «Fuera de su caja»: los F, FD y RMA que no están en la caja de su grado

- En «Por pasar a NSYS» hay una **quinta tarjeta, «Fuera de su caja»**: los móviles con grado **F**, **FD** o **RMA** que NSYS tiene en la empresa y en una caja que no es la de su grado (los F van en «CF021», los FD en «Desguace» y los RMA en «Funcionalidad pendiente»). Un móvil sin caja también sale.
- La lista va **agrupada por la caja que toca** («Llevar a «CF021»») y cada grupo tiene **«Copiar IMEIs (n)»**, que los copia uno por línea para buscarlos en NSYS y cambiarles la «Location».
- Cada fila enseña la caja de hoy como la escribe NSYS y cómo la llama el ERP. Si el taller ha puesto el grado y NSYS aún tiene otro, lo dice: «también en «Cambio de grado»».
- No lleva «Hecho»: la fila se va sola cuando la importación ve la caja nueva. No salen los móviles con un trabajo abierto del taller ni los de pedido.
- Servidor y web, sin base de datos: `cajas` en las tarjetas de «Por pasar a NSYS». Con el servidor anterior la tarjeta sale a 0.
```

(la fecha se pone el día que se sube a producción). En `docs/paridad/imeis.md`, al final de la sección de «Por pasar a NSYS»
(tras la línea de «ignorar» de la 0.9.27):

```markdown
- **«Fuera de su caja» (X):** quinta tarjeta con los móviles F, FD y RMA en la empresa que no están en la caja de su grado
  (F → «CF021», FD → «Desguace», RMA → «Funcionalidad pendiente»; sin caja también), sin los de trabajo abierto ni los de
  pedido; en grupos por la caja que toca, con «Copiar IMEIs». Sin «Hecho». Campo `cajas` de `GET
  /api/stock-telefonos/por-pasar/tarjetas`.
```

- [ ] **Step 2: Comprobación completa de la web**

Run: `cd $W && npm run check 2>&1 | tail -12`
Expected: lint, typecheck y todos los tests en verde.

- [ ] **Step 3: Commit de la web**

```bash
cd $W && git add package.json package-lock.json CHANGELOG.md docs/paridad/imeis.md
git commit -m "chore: version X, changelog y paridad de Fuera de su caja"
```

- [ ] **Step 4: Novedades en la raíz**

Si ya existe `docs/novedades/NOVEDADES-vX.md` (las de los pedidos), **parar y preguntar al usuario** qué número pasan a
llevar. Si no, crear `docs/novedades/NOVEDADES-vX.md`:

```markdown
# Novedades de la versión X

## «Fuera de su caja»

En **Stock de teléfonos → Por pasar a NSYS** hay una tarjeta nueva, **«Fuera de su caja»**.

Enseña los móviles con grado **F**, **FD** o **RMA** que no están en la caja de su grado:

- los **F** van en «CF021»;
- los **FD** van en «Desguace»;
- los **RMA** van en «Funcionalidad pendiente».

La lista va por la caja a la que hay que llevarlos. En cada grupo, **«Copiar IMEIs»** copia sus IMEIs para buscarlos en NSYS
y cambiarles la «Location» de una vez.

No hay que marcar nada: cuando NSYS tiene el móvil en su caja, la fila se va sola (la lista se pone al día cada 30 minutos).
Un móvil que el taller tiene entre manos no sale hasta que el trabajo se cierra.
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-vX.md
git commit -m "docs: novedades de la version X (Fuera de su caja)"
```

---

### Task 7: Revisión final y preprod

**Files:** ninguno del código (solo si la revisión encuentra algo).

- [ ] **Step 1: Revisión final** de los dos worktrees contra la spec (requesting-code-review o revisión propia):
`cd $S && git diff origin/main --stat` y `cd $W && git diff origin/main --stat`; cada punto de la spec §3, §4 y §5 tiene su
código y su prueba; los textos son los de «Global Constraints».

- [ ] **Step 2: Todo en verde por última vez**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd /c/Users/dev/Documents/wt/servidor-fuera-caja && mvn test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD SUCCESS|BUILD FAILURE" | tail -3
cd /c/Users/dev/Documents/wt/web-fuera-caja && npm run check 2>&1 | tail -6
```

Expected: `BUILD SUCCESS` y `check` en verde.

- [ ] **Step 3: Parar y pedir el OK del usuario** para subir las ramas y ponerlas en preprod. Claude no hace SSH: prepara el
guion (con `hostname` al principio de cada bloque) y el usuario lo ejecuta, como en `Apuntes/preprod/v0931-preprod.md`.

- [ ] **Step 4: En preprod, con los datos copiados de producción** (spec §6):

  1. La lista de la tarjeta frente a esta consulta al espejo (usa el grado de NSYS; las diferencias tienen que explicarse por
     un cambio de grado del taller o por un trabajo abierto):

     ```sql
     SELECT IMEI_SN, GRADE, LOCATION, ESTADO
     FROM Nsys_movil
     WHERE ESTADO IN ('STOCK', 'NO_CUENTA')
       AND UPPER(TRIM(GRADE)) IN ('F', 'FD', 'RMA')
       AND NOT (UPPER(TRIM(GRADE)) = 'F' AND TRIM(IFNULL(LOCATION, '')) = 'CF021')
       AND NOT (UPPER(TRIM(GRADE)) = 'FD' AND TRIM(IFNULL(LOCATION, '')) = 'Desguace')
       AND NOT (UPPER(TRIM(GRADE)) = 'RMA' AND TRIM(IFNULL(LOCATION, '')) = 'Funcionalidad pendiente')
     ORDER BY GRADE, LOCATION, IMEI_SN;
     ```

  2. El tiempo de `GET /api/stock-telefonos/por-pasar/tarjetas` (pestaña Red del navegador): muy por debajo de los 15 s. Si
     pasa de 2-3 s, decirlo al usuario antes de producción.
  3. «Copiar IMEIs» de un grupo y pegarlo en un bloc de notas: un IMEI por línea.

- [ ] **Step 5: Producción, merge y tags**, solo con el OK del usuario y con su guía (`Apuntes/despliegue_vdc_produccion.md`):
servidor primero y web después. Poner la fecha en el `CHANGELOG.md` y marcar la casilla en `Apuntes/plan-futuro.md`.
