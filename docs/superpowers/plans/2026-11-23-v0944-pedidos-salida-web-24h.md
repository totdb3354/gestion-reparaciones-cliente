# Pedidos 0.9.44: los pedidos web salen en 24 h laborables — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un pedido web sale «Sale hoy» desde su primer día laborable y pasa a «Con retraso» cuando se pasa el laborable siguiente, sin mirar el servicio de envío; en fin de semana nunca «Sale hoy».

**Architecture:** El servidor cambia el límite de las webs (`FechaSalida.de`) a «laborable siguiente al primer laborable desde la entrada» y sirve un campo nuevo `salidaDesde` en `FilaVista` (calculado al servir, sin migración). La web (`salida.ts`) calcula el nivel y el texto con una «fecha de referencia»: `salidaDesde` si aún no ha llegado, hoy dentro del plazo (el lunes si hoy es fin de semana) o el límite si ya pasó. Amazon no cambia.

**Tech Stack:** Java 17 + Spring Boot 3.3 (JUnit 5 + Mockito); React + TypeScript (Vitest); Playwright (E2E en preprod).

**Spec:** `docs/superpowers/specs/2026-11-23-pedidos-salida-web-24h-design.md`

## Global Constraints

- Versión **0.9.44** (servidor + web). La web está en 0.9.43 (`package.json`); el servidor no lleva número propio (pom `1.0-SNAPSHOT`).
- Ramas `dev_pedidos_salida_web` en servidor y web, en worktrees `C:/Users/dev/Documents/wt/servidor-pedidos-salida-web` y `C:/Users/dev/Documents/wt/web-pedidos-salida-web`, desde `origin/main` (hacer `git fetch` y comparar antes: hay otras sesiones trabajando).
- Commits sin `Co-Authored-By` (norma del usuario). **Nada de merge, push, tag ni despliegue sin OK explícito del usuario.**
- Maven desde Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"` (comprobar las rutas con `ls /c/Users/dev/tools`).
- Regla (solo webs; Amazon igual que hoy): desde = entrada si es laborable, si no el primer laborable siguiente; límite = el laborable siguiente a «desde»; no se mira el servicio de envío; sin hora de corte.
- Textos exactos: «Sale hoy», «Sale mañana», «Sale lun 23/11» (día abreviado en minúscula + dd/mm), «Con retraso».
- Datos de prueba inventados (repos públicos): pedidos con prefijo `0099`, IMEI `3500000000000xx`.
- Sin migración. Despliegue en horario (servidor primero, después la web).

## Mapa de ficheros

Servidor (`gestion-reparaciones-servidor/`, en el worktree):
- Modify `src/main/java/com/reparaciones/servidor/pedidos/CalendarioLaboral.java` — `primerLaborable`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/FechaSalida.java` — regla de las webs y `desde`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/FilaVista.java` — campo `salidaDesde` + constructor de compatibilidad.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/VistaFilas.java` — calcula `salidaDesde` con `CalendarioLaboral`.
- Modify `docs/api_contract.md`.
- Test `src/test/java/com/reparaciones/servidor/pedidos/CalendarioLaboralTest.java`, `FechaSalidaTest.java`, `VistaFilasTest.java`.

Web (`gestion-reparaciones-web/`, en el worktree):
- Modify `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados), `src/modules/telefonos/test/fabrica.ts`.
- Modify `src/modules/telefonos/pedidos/salida.ts`, `salida.test.ts`, `filas.test.ts`.
- Modify `src/modules/telefonos/pedidos/AyudaPedidos.tsx`, `PedidosAyuda.test.tsx`.
- Modify `tests/e2e/pedidos-salida.spec.ts` (tests 1 y 2).
- Modify `package.json`, `package-lock.json` (0.9.44), `CHANGELOG.md`.

Raíz (`ProgramaReparaciones/`):
- Modify `docs/modulos/pedidos.md` §4.

---

### Task 0: Ramas y worktrees

**Files:** ninguno.

- [ ] **Step 1: Traer origin y comparar**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git fetch -q && git log --oneline -1 origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q && git log --oneline -1 origin/main
```
Expected: servidor `a60c592…` o posterior, web `252d710…` o posterior (0.9.43). Si hay algo más nuevo, leer su mensaje y avisar si toca `FechaSalida`, `VistaFilas`, `FilaVista` o `salida.ts`.

- [ ] **Step 2: Worktrees**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git worktree add -b dev_pedidos_salida_web /c/Users/dev/Documents/wt/servidor-pedidos-salida-web origin/main
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git worktree add -b dev_pedidos_salida_web /c/Users/dev/Documents/wt/web-pedidos-salida-web origin/main
cd /c/Users/dev/Documents/wt/web-pedidos-salida-web && npm ci
```
Expected: dos worktrees nuevos; `npm ci` sin errores.

---

### Task 1: Servidor — la regla nueva de las webs (`primerLaborable`, `FechaSalida`)

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/CalendarioLaboral.java`
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FechaSalida.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/CalendarioLaboralTest.java`, `FechaSalidaTest.java`

**Interfaces:**
- Produces: `CalendarioLaboral.primerLaborable(LocalDate d, Set<LocalDate> festivos): LocalDate`;
  `FechaSalida.desde(Canal canal, LocalDate entrada, Set<LocalDate> festivos): LocalDate` (nulo si no es web o sin entrada).

Fechas de referencia: 2026-11-16 lunes, 20 viernes, 21 sábado, 22 domingo, 23 lunes, 24 martes, 25 miércoles; 2026-12-07 lunes, 8 martes (festivo en `FESTIVOS` del test).

- [ ] **Step 1: Tests que fallan**

En `CalendarioLaboralTest.java` añadir (con `import java.util.Set;` y `java.time.LocalDate` si faltan):

```java
    @Test
    void primerLaborableEsElMismoDiaSiEsLaborableYSiNoElSiguiente() {
        Set<LocalDate> festivos = Set.of(LocalDate.of(2026, 12, 8), LocalDate.of(2026, 11, 23));
        assertEquals(LocalDate.of(2026, 11, 20), CalendarioLaboral.primerLaborable(LocalDate.of(2026, 11, 20), festivos)); // viernes
        assertEquals(LocalDate.of(2026, 11, 24), CalendarioLaboral.primerLaborable(LocalDate.of(2026, 11, 21), festivos)); // sab -> lun festivo -> mar
        assertEquals(LocalDate.of(2026, 11, 24), CalendarioLaboral.primerLaborable(LocalDate.of(2026, 11, 22), festivos)); // domingo
        assertEquals(LocalDate.of(2026, 12, 9), CalendarioLaboral.primerLaborable(LocalDate.of(2026, 12, 8), festivos));   // festivo
    }
```

(Si `CalendarioLaboralTest` usa AssertJ en vez de `assertEquals`, escribir lo mismo con `assertThat(...).isEqualTo(...)`.)

En `FechaSalidaTest.java` **sustituir** `enLasWebsEsLaEntradaMasDosOTresDiasLaborablesSegunElEnvio` y `enLasWebsSeSaltanLosFestivos` por:

```java
    @Test
    void enLasWebsEsElLaborableSiguienteAlPrimerLaborableSinMirarElEnvio() {
        LocalDateTime lunesNoche = LocalDateTime.of(2026, 11, 16, 23, 30);
        assertThat(FechaSalida.de(pedido("processing", "Rápido (Entrega en 24/48 horas laborables)", lunesNoche), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(FechaSalida.de(pedido("processing", "Envío gratis en 24/72 horas laborables", lunesNoche), Canal.PARTICULARES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(FechaSalida.de(pedido("processing", null, lunesNoche), Canal.BACKIPHONE, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void enLasWebsElViernesVenceElLunesYElFinDeSemanaElMartes() {
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 11, 20, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 11, 21, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 11, 22, 18, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void enLasWebsSeSaltanLosFestivos() {
        // lunes 7/12 (vispera de festivo): desde lunes 7, el laborable siguiente es el miercoles 9 (martes 8 festivo)
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 12, 7, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 12, 9));
        // entra el martes 8 (festivo): desde miercoles 9, vence el jueves 10
        assertThat(FechaSalida.de(pedido("processing", "24/48", LocalDateTime.of(2026, 12, 8, 10, 0)), Canal.DISTRIBUIDORES, FESTIVOS))
                .isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    void desdeEsElPrimerLaborableEnLasWebsYNuloEnAmazon() {
        assertThat(FechaSalida.desde(Canal.PARTICULARES, LocalDate.of(2026, 11, 21), FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(FechaSalida.desde(Canal.DISTRIBUIDORES, LocalDate.of(2026, 11, 20), FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(FechaSalida.desde(Canal.AMAZON, LocalDate.of(2026, 11, 21), FESTIVOS)).isNull();
        assertThat(FechaSalida.desde(null, LocalDate.of(2026, 11, 21), FESTIVOS)).isNull();
        assertThat(FechaSalida.desde(Canal.PARTICULARES, null, FESTIVOS)).isNull();
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run (en el worktree del servidor): `mvn -q test -Dtest=CalendarioLaboralTest+FechaSalidaTest`
Expected: error de compilación (`primerLaborable`, `desde` no existen).

- [ ] **Step 3: Implementación**

En `CalendarioLaboral.java`, debajo de `sumarLaborables`:

```java
    /** {@code d} si es laborable; si no, el primer laborable despues (spec 2026-11-23 «salida web 24 h»). */
    public static LocalDate primerLaborable(LocalDate d, Set<LocalDate> festivos) {
        LocalDate x = d;
        while (!esLaborable(x, festivos)) x = x.plusDays(1);
        return x;
    }
```

`FechaSalida.java` entero:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ultimo dia para enviar un pedido. Amazon: la fecha de «Latest ship» del estado de ShippyPro (spec 2026-11-18 §2). Webs (spec
 *  2026-11-23 «salida web 24 h»): el laborable siguiente al primer laborable desde la entrada, sin mirar el servicio de envio. Puro. */
public final class FechaSalida {

    private static final Pattern LATEST_SHIP = Pattern.compile("Latest ship:\\s*(\\d{4}-\\d{2}-\\d{2})");

    private FechaSalida() {}

    public static LocalDate de(PedidoShippyPro p, Canal canal, Set<LocalDate> festivos) {
        if (canal == null) return null;
        if (canal.esAmazon()) {
            if (p.estado() == null) return null;
            Matcher m = LATEST_SHIP.matcher(p.estado());
            if (!m.find()) return null;
            try {
                return LocalDate.parse(m.group(1));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        if (canal.esWeb()) return CalendarioLaboral.sumarLaborables(desde(canal, p.fecha().toLocalDate(), festivos), 1, festivos);
        return null;
    }

    /** Primer dia en que un pedido web ya «sale hoy»: la entrada si es laborable, si no el primer laborable siguiente. Nulo fuera de
     *  las webs o sin entrada. */
    public static LocalDate desde(Canal canal, LocalDate entrada, Set<LocalDate> festivos) {
        if (canal == null || !canal.esWeb() || entrada == null) return null;
        return CalendarioLaboral.primerLaborable(entrada, festivos);
    }
}
```

- [ ] **Step 4: Verde**

Run: `mvn -q test -Dtest=CalendarioLaboralTest+FechaSalidaTest+LecturaFilasTest`
Expected: PASS. Si `LecturaFilasTest` comprobaba una fecha de salida web con la regla vieja (+2/+3), actualizar la fecha esperada a la regla nueva y anotarlo en el commit.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/CalendarioLaboral.java src/main/java/com/reparaciones/servidor/pedidos/FechaSalida.java src/test/java/com/reparaciones/servidor/pedidos/
git commit -m "feat(pedidos): los pedidos web vencen el laborable siguiente a su primer laborable, sin mirar el servicio de envio"
```

---

### Task 2: Servidor — `salidaDesde` en la vista

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FilaVista.java`
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/VistaFilas.java`
- Modify: `docs/api_contract.md`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/VistaFilasTest.java`

**Interfaces:**
- Consumes: `FechaSalida.desde(Canal, LocalDate, Set<LocalDate>)` (Task 1); `CalendarioLaboral.festivos(): Set<LocalDate>`.
- Produces: `FilaVista.salidaDesde(): LocalDate` (JSON `salidaDesde`, ISO `yyyy-mm-dd`, nullable), último campo del record.

- [ ] **Step 1: Test que falla**

En `VistaFilasTest.java`: añadir `private final CalendarioLaboral calendario = mock(CalendarioLaboral.class);` junto a los otros mocks, y al final de la clase:

```java
    private static FilaPedido conSalida(int id, LocalDate entrada, Canal canal, String origen, LocalDate salida) {
        FilaPedido b = fila(id, null, null);
        return new FilaPedido(id, entrada, b.plataforma(), b.sku(), null, "0099-" + id, b.fechaCompra(), origen,
                canal, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, salida, "ES");
    }

    @Test void enLasWebsLaVistaLlevaElPrimerDiaEnQueSaleHoyYEnAmazonYElHistoricoNo() {
        when(calendario.festivos()).thenReturn(Set.of());
        VistaFilas conCalendario = new VistaFilas(dao, nsys, taller, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID), calendario);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                conSalida(1, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", LocalDate.of(2026, 11, 10)), // sabado
                conSalida(2, LocalDate.of(2026, 11, 5), Canal.PARTICULARES, "SHIPPYPRO", LocalDate.of(2026, 11, 6)),   // jueves
                conSalida(3, LocalDate.of(2026, 11, 7), Canal.AMAZON, "SHIPPYPRO", LocalDate.of(2026, 11, 10)),
                conSalida(4, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", null),
                conSalida(5, LocalDate.of(2026, 11, 7), null, "HISTORICO", null)));
        List<FilaVista> f = conCalendario.filas(null, null, null).filas();
        assertEquals(LocalDate.of(2026, 11, 9), f.get(0).salidaDesde());
        assertEquals(LocalDate.of(2026, 11, 5), f.get(1).salidaDesde());
        assertNull(f.get(2).salidaDesde());
        assertNull(f.get(3).salidaDesde(), "sin fecha de salida, sin desde");
        assertNull(f.get(4).salidaDesde());
    }

    @Test void sinCalendarioLaVistaNoFallaYCuentaSinFestivos() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                conSalida(1, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", LocalDate.of(2026, 11, 10))));
        assertEquals(LocalDate.of(2026, 11, 9), vista.filas(null, null, null).filas().get(0).salidaDesde());
    }
```

La lista de argumentos de `new FilaPedido(...)` es la misma que en `laFechaDeSalidaYElPaisDeLaFilaPasanALaVista` (mismo número de `null`); si el compilador dice que no casa, copiar exactamente la de ese test y cambiar solo id, entrada, pedido, origen, canal, salida y país. Añadir `import java.util.Set;` si falta. Si `delPeriodo` ordena o filtra (por ejemplo, el histórico va por otro método del DAO), ajustar el mock como en los tests vecinos de la misma clase que ya mezclan histórico y ShippyPro.

- [ ] **Step 2: Comprobar que falla**

Run: `mvn -q test -Dtest=VistaFilasTest`
Expected: error de compilación (`salidaDesde()` y el constructor con `CalendarioLaboral` no existen).

- [ ] **Step 3: `FilaVista`**

En el javadoc del record, sustituir la línea de `fechaSalida` por:

```
 *  fechaSalida: ultimo dia para enviar (Amazon: Latest ship de ShippyPro; webs: el laborable siguiente al primer laborable desde la
 *  entrada; historico: nulo).
 *  salidaDesde: en las webs, primer dia en que el pedido ya «sale hoy» (la entrada, o el primer laborable si entro en festivo o fin de
 *  semana); nulo en Amazon, en el historico y sin fecha de salida.
```

Añadir al final de los componentes, después de `@Schema(nullable = true) String pais`:

```java
                        @Schema(nullable = true) String pais, @Schema(nullable = true) LocalDate salidaDesde) {

    /** Una fila sin {@code salidaDesde} (Amazon, historico o tests). */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen,
                     LocalDateTime validadaEn, String validadaPor, String motivoValidado, LocalDate fechaSalida, String pais) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, validadaEn, validadaPor, motivoValidado, fechaSalida, pais, null);
    }
```

(Los constructores cortos que ya existen siguen llamando a `this(..., null, null)` con 32 argumentos: ahora resuelven al de compatibilidad. No hay que tocarlos.)

- [ ] **Step 4: `VistaFilas`**

Campo y constructores:

```java
    private final CalendarioLaboral calendario;

    @Autowired
    public VistaFilas(FilasDAO dao, NsysDAO nsysDao, TallerNsys taller, CalendarioLaboral calendario) {
        this(dao, nsysDao, taller, Clock.system(ImportadorNsys.MADRID), calendario);
    }

    VistaFilas(FilasDAO dao, NsysDAO nsysDao, TallerNsys taller, Clock clock) {
        this(dao, nsysDao, taller, clock, null);
    }

    VistaFilas(FilasDAO dao, NsysDAO nsysDao, TallerNsys taller, Clock clock, CalendarioLaboral calendario) {
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.taller = taller;
        this.clock = clock;
        this.calendario = calendario;
    }
```

En `vistas(List<FilaPedido> filas)`, justo antes de `List<FilaVista> vistas = new ArrayList<>();`:

```java
        Set<LocalDate> festivos = null; // se leen una sola vez, y solo si hay alguna fila web con fecha de salida
```

y dentro del bucle, antes de `vistas.add(...)`:

```java
            LocalDate salidaDesde = null;
            if (f.fechaSalida() != null && f.canal() != null && f.canal().esWeb() && !HISTORICO.equals(f.origen())) {
                if (festivos == null) festivos = calendario == null ? Set.of() : calendario.festivos();
                salidaDesde = FechaSalida.desde(f.canal(), f.fecha(), festivos);
            }
```

y en `vistas.add(new FilaVista(...))` cambiar el final `f.fechaSalida(), f.pais()));` por `f.fechaSalida(), f.pais(), salidaDesde));`.
Añadir `import java.time.LocalDate;` si falta.

- [ ] **Step 5: `docs/api_contract.md`**

Tras el párrafo de la línea ~87 (`FilaVista` gana al final `fechaSalida`…), añadir:

```markdown
    0.9.44: `FilaVista` gana al final `salidaDesde` (ISO, nullable): en las webs, primer día en que el pedido ya «sale hoy» (la entrada,
    o el primer laborable si entró en festivo o fin de semana); nulo en Amazon, en el histórico y sin `fechaSalida`. Desde la 0.9.44 la
    `fechaSalida` de las webs es el laborable siguiente a `salidaDesde`, sin mirar el servicio de envío (antes, entrada + 2 o 3 laborables).
```

- [ ] **Step 6: Verde y suite**

Run: `mvn -q test -Dtest=VistaFilasTest` → PASS. Después `mvn -q test` (suite entera, incluye `OpenApiContractTest`, que deja `target/openapi.json`) → PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/FilaVista.java src/main/java/com/reparaciones/servidor/pedidos/VistaFilas.java src/test/java/com/reparaciones/servidor/pedidos/VistaFilasTest.java docs/api_contract.md
git commit -m "feat(pedidos): la vista lleva salidaDesde, el primer dia en que un pedido web ya sale hoy"
```

---

### Task 3: Web — «Sale hoy» desde el primer laborable

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados), `src/modules/telefonos/test/fabrica.ts`
- Modify: `src/modules/telefonos/pedidos/salida.ts`
- Test: `src/modules/telefonos/pedidos/salida.test.ts`, `src/modules/telefonos/pedidos/filas.test.ts`

**Interfaces:**
- Consumes: `FilaVista.salidaDesde: string | null` (Task 2).
- Produces: `fechaReferencia(f: FilaVista, hoy: string): string | null` exportada de `salida.ts`; `nivelSalida` y `textoSalida` con la misma firma que hoy.

- [ ] **Step 1: Tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-pedidos-salida-web/target/openapi.json /c/Users/dev/Documents/wt/web-pedidos-salida-web/api/openapi.json
cd /c/Users/dev/Documents/wt/web-pedidos-salida-web && npm run api:types:offline
grep -n "salidaDesde" src/shared/api/schema.d.ts
```
Expected: `salidaDesde: string | null;` en `FilaVista`. En `src/modules/telefonos/test/fabrica.ts`, en la fábrica de filas (línea ~159), añadir `salidaDesde: null,` después de `pais: null,`.

- [ ] **Step 2: Tests que fallan**

En `salida.test.ts`, dentro del `describe`, añadir:

```ts
  // 0.9.44, webs: «Sale hoy» desde el primer laborable hasta el límite; nunca en fin de semana.
  // 2026-11-20 viernes, 21 sábado, 22 domingo, 23 lunes, 24 martes, 25 miércoles.
  const web = (salidaDesde: string, fechaSalida: string) => fila({ estado: 'PENDIENTE', canal: 'PARTICULARES', salidaDesde, fechaSalida })
  it('web que entra en sábado: «Sale lun» el sábado, «Sale mañana» el domingo, «Sale hoy» lunes y martes, «Con retraso» el miércoles', () => {
    const f = web('2026-11-23', '2026-11-24')
    expect(textoSalida(f, '2026-11-21')).toBe('Sale lun 23/11')
    expect(nivelSalida(f, '2026-11-21')).toBe('LUEGO')
    expect(textoSalida(f, '2026-11-22')).toBe('Sale mañana')
    expect(textoSalida(f, '2026-11-23')).toBe('Sale hoy')
    expect(textoSalida(f, '2026-11-24')).toBe('Sale hoy')
    expect(textoSalida(f, '2026-11-25')).toBe('Con retraso')
  })
  it('web del viernes: «Sale hoy» el viernes, el fin de semana dice el lunes, «Sale hoy» el lunes y el martes va con retraso', () => {
    const f = web('2026-11-20', '2026-11-23')
    expect(textoSalida(f, '2026-11-20')).toBe('Sale hoy')
    expect(textoSalida(f, '2026-11-21')).toBe('Sale lun 23/11')
    expect(nivelSalida(f, '2026-11-21')).toBe('LUEGO')
    expect(textoSalida(f, '2026-11-22')).toBe('Sale mañana')
    expect(nivelSalida(f, '2026-11-23')).toBe('HOY')
    expect(nivelSalida(f, '2026-11-24')).toBe('RETRASO')
  })
  it('sin salidaDesde (Amazon o servidor viejo) se usa solo el límite, como antes', () => {
    const f = fila({ estado: 'PENDIENTE', canal: 'AMAZON', salidaDesde: null, fechaSalida: '2026-11-24' })
    expect(textoSalida(f, '2026-11-23')).toBe('Sale mañana')
    expect(textoSalida(f, '2026-11-24')).toBe('Sale hoy')
  })
  it('fechaReferencia: desde, hoy, el lunes o el límite', () => {
    expect(fechaReferencia(web('2026-11-23', '2026-11-24'), '2026-11-21')).toBe('2026-11-23')
    expect(fechaReferencia(web('2026-11-23', '2026-11-24'), '2026-11-24')).toBe('2026-11-24')
    expect(fechaReferencia(web('2026-11-20', '2026-11-23'), '2026-11-22')).toBe('2026-11-23')
    expect(fechaReferencia(web('2026-11-20', '2026-11-23'), '2026-11-25')).toBe('2026-11-23')
    expect(fechaReferencia(fila({ estado: 'PENDIENTE', fechaSalida: null }), '2026-11-25')).toBeNull()
  })
```

y cambiar el import a `import { fechaReferencia, nivelSalida, textoSalida } from './salida'`.

En `filas.test.ts`, junto a los tests de `recuentoSalida` (buscar `recuentoSalida`; si no hay ninguno, añadir un `describe('recuentoSalida', …)` con el import de `recuentoSalida` desde `./filas`):

```ts
  it('una web cuenta en «salen hoy» su primer y su segundo día', () => {
    const f = fila({ estado: 'PENDIENTE', canal: 'PARTICULARES', salidaDesde: '2026-11-23', fechaSalida: '2026-11-24' })
    expect(recuentoSalida([f], '2026-11-23')).toEqual({ hoy: 1, retraso: 0 })
    expect(recuentoSalida([f], '2026-11-24')).toEqual({ hoy: 1, retraso: 0 })
    expect(recuentoSalida([f], '2026-11-25')).toEqual({ hoy: 0, retraso: 1 })
    expect(recuentoSalida([f], '2026-11-22')).toEqual({ hoy: 0, retraso: 0 })
  })
```

- [ ] **Step 3: Comprobar que fallan**

Run: `npm run test -- src/modules/telefonos/pedidos/salida.test.ts src/modules/telefonos/pedidos/filas.test.ts`
Expected: FAIL (`fechaReferencia` no existe; textos con la regla vieja).

- [ ] **Step 4: Implementación — `salida.ts` entero**

```ts
import type { FilaVista } from '@/shared/api/client'
import { estadoPantalla } from './filas'

export type NivelSalida = 'RETRASO' | 'HOY' | 'MANANA' | 'LUEGO'
const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb']

/** Día siguiente a una fecha ISO (yyyy-mm-dd), sin husos: se trabaja con el día de Madrid ya calculado. */
function siguiente(iso: string): string {
  const d = new Date(`${iso}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + 1)
  return d.toISOString().slice(0, 10)
}

/** Día de la semana de una fecha ISO (0 = domingo, 6 = sábado). */
function diaSemana(iso: string): number {
  return new Date(`${iso}T12:00:00Z`).getUTCDay()
}

/** El lunes siguiente a una fecha ISO. */
function lunesSiguiente(iso: string): string {
  let d = siguiente(iso)
  while (diaSemana(d) !== 1) d = siguiente(d)
  return d
}

/** La fecha con la que se calcula el nivel (spec 2026-11-23 «salida web 24 h»). Sin `salidaDesde` (Amazon), el límite. En las webs:
 *  «desde» si aún no ha llegado; hoy si está dentro del plazo (el lunes siguiente si hoy es sábado o domingo); el límite si ya pasó.
 *  La web no conoce los festivos: un festivo entre semana dentro del plazo sale «Sale hoy» (el límite, que sí los cuenta, no cambia). */
export function fechaReferencia(f: FilaVista, hoy: string): string | null {
  if (!f.fechaSalida) return null
  const desde = f.salidaDesde
  if (!desde || hoy > f.fechaSalida) return f.fechaSalida
  if (hoy < desde) return desde
  const d = diaSemana(hoy)
  return d === 0 || d === 6 ? lunesSiguiente(hoy) : hoy
}

/** Spec 2026-11-18 §3: solo las filas pendientes o con «falta por enviar» que tienen fecha de salida. */
export function nivelSalida(f: FilaVista, hoy: string): NivelSalida | null {
  const e = estadoPantalla(f)
  if ((e !== 'PENDIENTE' && e !== 'FALTA_ENVIAR') || !f.fechaSalida) return null
  const ref = fechaReferencia(f, hoy)!
  if (ref < hoy) return 'RETRASO'
  if (ref === hoy) return 'HOY'
  return ref === siguiente(hoy) ? 'MANANA' : 'LUEGO'
}

export function textoSalida(f: FilaVista, hoy: string): string | null {
  const n = nivelSalida(f, hoy)
  if (n === null) return null
  if (n === 'RETRASO') return 'Con retraso'
  if (n === 'HOY') return 'Sale hoy'
  if (n === 'MANANA') return 'Sale mañana'
  const ref = fechaReferencia(f, hoy)!
  return `Sale ${DIAS[diaSemana(ref)]} ${ref.slice(8, 10)}/${ref.slice(5, 7)}`
}
```

- [ ] **Step 5: Verde**

Run: `npm run test -- src/modules/telefonos/pedidos/` → PASS (los tests de salida que ya había siguen igual: sus filas no llevan `salidaDesde`).

- [ ] **Step 6: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/telefonos/test/fabrica.ts src/modules/telefonos/pedidos/salida.ts src/modules/telefonos/pedidos/salida.test.ts src/modules/telefonos/pedidos/filas.test.ts
git commit -m "feat(pedidos): un pedido web sale hoy desde su primer laborable hasta su limite, y nunca en fin de semana"
```

---

### Task 4: Web — ayuda, E2E, versión y changelog

**Files:**
- Modify: `src/modules/telefonos/pedidos/AyudaPedidos.tsx`, `src/modules/telefonos/pedidos/PedidosAyuda.test.tsx`
- Modify: `tests/e2e/pedidos-salida.spec.ts`
- Modify: `package.json`, `package-lock.json`, `CHANGELOG.md`

- [ ] **Step 1: Ayuda**

En `AyudaPedidos.tsx`, el texto de la `Linea` de «Sale hoy» pasa a:

```tsx
            Pendiente (o envío parcial que falta por enviar, con «· sale hoy») que tiene que salir hoy. Amazon: el último día que da Amazon. Webs: desde el día en que entra (o el primer laborable, si entra en fin de semana o festivo) hasta el laborable siguiente, festivos de Madrid incluidos.
```

En `PedidosAyuda.test.tsx` (línea ~63), cambiar `getByText(/48 o 72 h laborables/)` por `getByText(/hasta el laborable siguiente/)`.

- [ ] **Step 2: E2E — tests 1 y 2 con la regla nueva**

En `tests/e2e/pedidos-salida.spec.ts`:
- En el tipo `Fila` añadir `salidaDesde?: string | null`.
- Debajo de `cuentaEnExcel`, añadir el mismo cálculo que `fechaReferencia` de la web:

```ts
/** La fecha con la que la web calcula «Sale hoy» / «Con retraso» (0.9.44; igual que `fechaReferencia` de salida.ts). */
function referencia(f: Fila, hoy: string): string | null {
  if (!f.fechaSalida) return null
  const desde = f.salidaDesde
  if (!desde || hoy > f.fechaSalida) return f.fechaSalida
  if (hoy < desde) return desde
  const dia = new Date(`${hoy}T12:00:00Z`).getUTCDay()
  if (dia !== 0 && dia !== 6) return hoy
  const d = new Date(`${hoy}T12:00:00Z`)
  do d.setUTCDate(d.getUTCDate() + 1); while (d.getUTCDay() !== 1)
  return d.toISOString().slice(0, 10)
}
```

- Test 1: `f.fechaSalida === hoy` → `referencia(f, hoy) === hoy` en el cálculo de `deHoy`.
- Test 2: `unaDeHoy` con `referencia(f, hoy) === hoy` en vez de `f.fechaSalida === hoy`; `posterior` con `(referencia(f, hoy) ?? '') > hoy` en vez de `!!f.fechaSalida && f.fechaSalida > hoy`. En el `console.log` del final añadir `${posterior.salidaDesde ?? ''}` junto a la fecha.
- Cabecera del fichero (comentario de la línea 6): añadir «; desde la 0.9.44 las webs salen hoy desde su primer laborable (`salidaDesde`)».

Comprobar que compila: `npx playwright test --list tests/e2e/pedidos-salida.spec.ts` (solo lista).

- [ ] **Step 3: Versión y changelog**

```bash
npm version 0.9.44 --no-git-tag-version
```
Expected: `v0.9.44`.

En `CHANGELOG.md`, encima de `## [0.9.43]`:

```markdown
## [0.9.44] - 2026-11-23 — Pedidos: los pedidos web salen en 24 h laborables

- En **Pedidos**, un pedido de las webs sale **«Sale hoy»** desde el día en que entra y hasta el día laborable siguiente; después pasa a **«Con retraso»**. Antes tenía 48 o 72 h laborables según el envío y se veía «Sale mié 25/11» hasta el último día.
- Si entra en **fin de semana o festivo**, cuenta desde el primer laborable: el sábado y el domingo dice «Sale lun», y no sale «Con retraso» hasta el miércoles. En sábado o domingo nunca sale «Sale hoy».
- Ya no se mira el tipo de envío (24/48 o 24/72): todos los pedidos web igual. Amazon no cambia.
```

- [ ] **Step 4: Suite web**

Run: `npm run check` → PASS (lint, tipos y tests).

- [ ] **Step 5: Commit**

```bash
git add src/modules/telefonos/pedidos/AyudaPedidos.tsx src/modules/telefonos/pedidos/PedidosAyuda.test.tsx tests/e2e/pedidos-salida.spec.ts package.json package-lock.json CHANGELOG.md
git commit -m "chore: version 0.9.44, ayuda y E2E de la salida de los pedidos web en 24 h"
```

---

### Task 5: Doc del módulo (raíz) y cierre

**Files:**
- Modify: `docs/modulos/pedidos.md` (raíz `ProgramaReparaciones/`)

- [ ] **Step 1: Doc**

En `docs/modulos/pedidos.md` §4, sustituir la frase de las webs de «Fecha de salida» («Webs: el día en que entró el pedido más 2 días laborables si el envío es de 24/48 h, o 3 si es de 24/72 h.») por:

```markdown
Webs (desde la 0.9.44): salen **«Sale hoy»** desde el día en que entra el pedido (o el primer laborable, si entra en fin de semana o
  festivo) y hasta el laborable siguiente, que es su fecha de salida; después, «Con retraso». No se mira el tipo de envío. En sábado o
  domingo nunca sale «Sale hoy» (dice «Sale lun»).
```

(Mantener el resto de la viñeta: qué es laborable y lo de cargar los festivos de cada año.)

- [ ] **Step 2: Commit (raíz)**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/modulos/pedidos.md && git commit -m "docs: Pedidos, los pedidos web salen en 24 h laborables (0.9.44)"
```

- [ ] **Step 3: Avisar a Imad**

Resumen de las dos ramas (commits, tests: `mvn -q test` y `npm run check` en verde) y siguiente paso: despliegue en preprod (guion en `Apuntes/preprod/`), E2E `pedidos-salida.spec.ts` contra preprod, y producción en horario (servidor primero). **Nada de merge, push ni despliegue sin su OK.**
