# 0.9.26 — «Descargar cajas» en «Cambio de color» · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** en la tarjeta «Cambio de color» de «Por pasar a NSYS», una columna «Caja» y un botón «⬇ Descargar cajas» que baja un
Excel aparte con el IMEI, la caja de NSYS, el modelo y el color nuevo de cada móvil, para devolverlos a su caja en NSYS después de
subir la plantilla (que no lleva la caja).

**Architecture:**
- **Servidor:** cada fila de «Cambio de color» (`FilaColorNsys`) lleva un campo nuevo, `caja`: el `LOCATION` del lote del móvil
  en el espejo (`Nsys_movil`), o null si está vacío. Se rellena en `TarjetasNsys.color`. Ni ruta nueva ni migración.
- **Web:** las reglas van en `porPasar.ts`, que es puro: qué móviles van (los de la plantilla y también los marcados «Hecho»), la
  hoja del Excel con su orden, el nombre del fichero y el aviso. `ListasPorPasar.tsx` pinta la columna y `PorPasarNsys.tsx`
  pone el segundo botón en la tarjeta. El xlsx lo escribe la ruta genérica de la 0.9.25
  (`POST /api/stock-telefonos/que-revisar/excel`, `useDescargarExcel`), con «Guardar como» en el mismo clic, como la plantilla.

**Tech Stack:** Spring Boot 3 + JUnit 5 (servidor); React 19 + TypeScript, TanStack Query, Vitest + Testing Library + MSW,
Playwright (web).

**Spec:** `docs/superpowers/specs/2026-11-06-v0926-cajas-cambio-color-design.md` (raíz).

**Antes y aparte de este plan:** los móviles que ya han perdido la caja se arreglan a mano con la consulta de solo lectura de
`/c/Users/dev/Documents/Apuntes/diagnostico-cajas-perdidas-color.md`, que el usuario pega en producción.

## Global Constraints

- **Servidor + web, sin migración.** No cambia ninguna ruta. La plantilla de color (`GET …/por-pasar/plantilla-color`) no se toca.
- **Permisos:** SUPERTECNICO y ADMIN, como el resto de Stock de teléfonos.
- **Qué móviles van en las cajas:** los de la plantilla de color (`ponerProducto !== null` y sin `vueltoDesde`) **sin mirar la
  marca «Hecho»**. Nada sale del histórico.
- **La caja:** `Nsys_movil.LOCATION` del lote de la fila, tal cual (ya se guarda sin espacios a los lados); vacía → `null`.
- **Textos exactos:**
  - columna: `Caja`, entre `En NSYS hoy` y `Poner en NSYS`; sin caja, `sin caja` en gris (`text-azul-gris`); con un servidor
    anterior (sin el campo), `—`;
  - botón (secundario, detrás de `⬇ Descargar plantilla (N)` en la tarjeta «Cambio de color»): `⬇ Descargar cajas (N)`; con 0,
    `⬇ Descargar cajas`, desactivado y con el título `No hay móviles que corregir`; con alguno, el título `N móviles`
    (`1 móvil`);
  - fichero: `Cajas_color_AAAA-MM-DD.xlsx` (día de Madrid); hoja `Cajas cambio de color`; columnas `IMEI`, `Caja`, `Modelo`,
    `Color nuevo`;
  - aviso: `Descargado «<nombre guardado>» · N móviles en M cajas` (`1 móvil en 1 caja`); `sin caja` cuenta como una caja;
  - ayuda de «Cambio de color» (`AYUDA.color`), exacta:
    `Móviles a los que el taller ha cambiado el color. Antes de tocarlos en NSYS, descarga las cajas: la plantilla no lleva la caja y NSYS los deja sin ella. En NSYS: bórralos (se pueden borrar todos de una vez), súbelos otra vez con la plantilla, que lleva el grado que tienen hoy en NSYS (el grado nuevo, si lo hay, va por «Cambio de grado», después), y devuélvelos a su caja con «Location». Cada fila se va sola cuando la importación (cada 30 min) ve el color nuevo en NSYS.`
- **Orden del Excel:** por caja, de la A a la Z sin distinguir mayúsculas (`localeCompare(…, 'es', { sensitivity: 'base' })`),
  `sin caja` al final; dentro de cada caja, por IMEI.
- **Fronteras:** un módulo no importa de otro (`no-restricted-imports` de `eslint.config.js`): todo va en
  `src/modules/telefonos/` o en `src/shared`.
- **Repos públicos:** en código, pruebas y documentos del repo, datos inventados («CAJA LISTOS», IMEIs `3500000000…`); ni URLs ni
  IPs de preprod o producción.
- **Commits** en castellano **sin tildes** y **sin** línea `Co-Authored-By`. **Nunca** push, merge ni tags sin el OK del usuario.
- **Otras sesiones trabajan a la vez:** no se tocan los clones principales ni los otros worktrees `wt/…` (en especial
  `wt/servidor-0926-pedidos` y `wt/web-0926-pedidos`, que son los pedidos, ahora 0.9.27). En la raíz hay ficheros sin seguimiento
  que no son de nadie (`.codegraph/`, `node_modules/`, PDF): `git add` solo los de la tarea.
- **Versión 0.9.26** (decisión del usuario, 06/11). Los pedidos pasan a ser la 0.9.27; se renumeran al traer `main` a sus ramas,
  fuera de este plan (salvo su fichero de novedades de la raíz, que choca de nombre: Task 4).

**Dónde se trabaja:**
- servidor: worktree `/c/Users/dev/Documents/wt/servidor-0926-cajas`, rama `dev_v0926_cajas_color`, desde `origin/main` del
  servidor (el 06/11, `6ea45b7`, 0.9.25);
- web: worktree `/c/Users/dev/Documents/wt/web-0926-cajas`, rama `dev_v0926_cajas_color`, desde `origin/main` de la web (el
  06/11, `fbbfc97`, 0.9.25).

Las rutas `src/…` de la Task 1 son del worktree del servidor; las de las Tasks 2-4, del de la web.

**Comandos:**
- servidor: `cd /c/Users/dev/Documents/wt/servidor-0926-cajas && export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`
  y después `mvn -q test` (o `-Dtest='…'`);
- web: `cd /c/Users/dev/Documents/wt/web-0926-cajas && npx vitest run <ficheros>`, `npx tsc -b`, `npm run lint`, `npm run check`
  (lint + typecheck + test). Con otras sesiones usando la máquina, un timeout de 5 s en un test que no es de esta tarea se repite
  solo ese fichero.

---

### Task 1: Servidor — la caja en cada fila de «Cambio de color» y el contrato

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/nsys/FilaColorNsys.java` (entero)
- Modify: `src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java` (`caja(MovilNsys)` nuevo y `color(…)`)
- Modify: `docs/api_contract.md`
- Test: `src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java`,
  `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`

**Interfaces:**
- Produces:
  - `FilaColorNsys.caja(): String` (nullable), el último componente del record;
  - `TarjetasNsys.caja(MovilNsys m): String`: `m.location()` o null si es null o está en blanco;
  - en el OpenAPI, `FilaColorNsys.caja` `{ type: string, nullable: true }` (lo copia la Task 2 desde `target/openapi.json`).

- [ ] **Step 1: El worktree del servidor y la línea base**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git log --oneline -3 origin/main
git worktree list
git worktree add -b dev_v0926_cajas_color /c/Users/dev/Documents/wt/servidor-0926-cajas origin/main
cd /c/Users/dev/Documents/wt/servidor-0926-cajas
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q test; echo "salida $?"
```

Expected: el worktree sobre `origin/main` (el 06/11, `6ea45b7 merge: Que revisar en Stock de telefonos, …(0.9.25)`; si ha entrado
otra versión, avisar al usuario por el número) y `salida 0`. Si algo falla ya en `main`, parar y avisar.

- [ ] **Step 2: Las pruebas que fallan**

En `TarjetasNsysTest.java`, al final de la clase (antes de la última `}`), añade:

```java
    /** 0.9.26 (spec «cajas del cambio de color» §5.1): la fila lleva la caja de NSYS de su lote, tal cual; un lote sin caja (el
     *  que deja la plantilla al subirla), null. */
    @Test void laFilaDeColorLlevaLaCajaDeNsysDeSuLoteYSinCajaNull() {
        TallerNsys.MovilPorPasar enCaja = porPasar(movil(IMEI, "Azul", "B"), vale("Rojo", "B", "Azul", null),
                new ProductoNsys.Poner("Apple iPhone 13 128GB (PRODUCT)RED", false), vigente(1, IMEI, COLOR, "Rojo", "Azul"));
        MovilNsys loteSinCaja = new MovilNsys(OTRO, "COLOR", "Apple iPhone 13 128GB Blue", "B", "InProcess", "", "Proveedor A",
                LocalDate.of(2026, 10, 1), null, "i13128AzulB", "13", "13", "128GB", "Azul", "B", false, "En revisión", "Listos",
                EstadoMovil.STOCK, HOY.minusDays(1), HOY.minusDays(1));
        TallerNsys.MovilPorPasar sinCaja = porPasar(loteSinCaja, vale("Rojo", "B", "Azul", null),
                new ProductoNsys.Poner("Apple iPhone 13 128GB (PRODUCT)RED", false), vigente(2, OTRO, COLOR, "Rojo", "Azul"));
        List<FilaColorNsys> filas = TarjetasNsys.color(List.of(enCaja, sinCaja), Map.of());
        assertEquals(List.of(IMEI, OTRO), filas.stream().map(FilaColorNsys::imeiSn).toList());
        assertEquals("CAJA LISTOS", filas.get(0).caja());
        assertNull(filas.get(1).caja());
        assertNull(TarjetasNsys.caja(new MovilNsys(OTRO, "COLOR", null, null, "InProcess", null, null, null, null, null, null, null,
                null, null, null, false, null, null, EstadoMovil.STOCK, HOY, HOY)), "sin LOCATION, null");
    }
```

(`movil`, `vigente` y `HOY` son de `TallerDePrueba`, importado con `*`; `movil` va en «CAJA LISTOS». Los dos cambios son de
ayer, así que la lista sale por IMEI.)

En `OpenApiContractTest.java`, en `elContratoPublicaElStockDeTelefonos`, justo después de la última línea del método,
`.has("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "el Excel responde un xlsx");`, añade:

```java
        // 0.9.26 (spec «cajas del cambio de color» §5.1): la caja de NSYS de cada fila de «Cambio de color».
        assertNullable(esquemas, "FilaColorNsys", "caja");
```

- [ ] **Step 3: Comprobar que fallan**

Run: `mvn -q test -Dtest='TarjetasNsysTest,OpenApiContractTest'`
Expected: FAIL de compilación: `cannot find symbol` `caja()` en `FilaColorNsys` y `caja(com.reparaciones.servidor.nsys.MovilNsys)`
en `TarjetasNsys`.

- [ ] **Step 4: El campo y dónde se rellena**

Sustituye `FilaColorNsys.java` entero por:

```java
package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Cambio de color» (spec 0.9.22 §3.2): el color de NSYS hoy y el nuevo como los escribe NSYS ({@code colorDe} →
 *  {@code colorA}; sin producto, el color nuevo es el del maestro), el producto de hoy y su grado (el que lleva la plantilla), el
 *  producto a poner ({@code ponerProducto}: null, «búscalo a mano»; {@code compruebalo}: sale del nombre del maestro tal cual), si
 *  el móvil está también en «Cambio de grado», por qué y desde cuándo (su cambio de color vigente) y la marca «Hecho» que vale
 *  ({@code hechoPor}, {@code hechoEn}; {@code hechoVolvio}: la importación vio NSYS igual y la fila cuenta otra vez). Desde la
 *  0.9.22 pieza 4 (§6), si es un vendido o de baja que ha vuelto: el día de la venta o de la baja y desde cuándo lo tiene el
 *  taller (null en la empresa). Horas de Madrid. {@code esEsim} (0.9.24): NSYS escribe «eSIM» en el producto. {@code caja}
 *  (0.9.26, spec «cajas del cambio de color» §5.1): la caja de NSYS del lote, tal cual; null si no tiene (la plantilla no la
 *  lleva y NSYS deja sin caja lo que se sube). */
public record FilaColorNsys(String imeiSn, @Schema(nullable = true) String modelo,
                            @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                            @Schema(nullable = true) String colorDe, @Schema(nullable = true) String colorA,
                            @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                            @Schema(nullable = true) String ponerProducto, boolean compruebalo, boolean tambienGrado,
                            List<PorQueTaller> porQue, LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                            @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio,
                            @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn,
                            @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim,
                            @Schema(nullable = true) String caja) {

    /** Un móvil en la empresa (sin vuelta), sin eSIM y sin caja. */
    public FilaColorNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String colorDe, String colorA,
                         String productoNsys, String gradoNsys, String ponerProducto, boolean compruebalo, boolean tambienGrado,
                         List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor, LocalDateTime hechoEn,
                         LocalDateTime hechoVolvio) {
        this(imeiSn, modelo, modeloMaestro, capacidad, colorDe, colorA, productoNsys, gradoNsys, ponerProducto, compruebalo,
                tambienGrado, porQue, desde, hechoPor, hechoEn, hechoVolvio, null, null, null, false, null);
    }
}
```

En `TarjetasNsys.java`:

1. Justo antes de `static List<FilaColorNsys> color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas) {`,
   añade:

```java
    /** La caja de NSYS de un lote (0.9.26, spec «cajas del cambio de color» §5.1): tal cual la guarda el espejo (ya sin espacios a
     *  los lados); null si no tiene. */
    static String caja(MovilNsys m) {
        return m.location() == null || m.location().isBlank() ? null : m.location();
    }

```

2. En `color(List<TallerNsys.MovilPorPasar> moviles, Map<String, HechoTaller> marcas, Map<String, VueltaNsys> vueltas)`, la
   última línea del `new FilaColorNsys(…)`, `v.vendidoEn(), v.bajaEn(), v.desde(), m.esEsim()));`, pasa a:

```java
                    v.vendidoEn(), v.bajaEn(), v.desde(), m.esEsim(), caja(m)));
```

- [ ] **Step 5: Comprobar que pasan**

Run: `mvn -q test -Dtest='TarjetasNsysTest,OpenApiContractTest'`
Expected: código de salida 0, sin `FAIL` ni `ERROR`.

- [ ] **Step 6: El contrato a mano**

En `docs/api_contract.md`, justo después de la viñeta de la 0.9.25, que termina en «…o si un texto pasa de 200 caracteres.»,
añade:

```markdown
- Desde la 0.9.26, cada fila de «Cambio de color» (`FilaColorNsys` en `GET /api/stock-telefonos/por-pasar/tarjetas`) trae
  `caja` (nullable, el último campo): la caja de NSYS del lote del móvil tal cual (`Nsys_movil.LOCATION`); `null` si no tiene.
  La web la enseña en la columna «Caja» y la escribe en «⬇ Descargar cajas», cuyo Excel hace `POST
  /api/stock-telefonos/que-revisar/excel`. La plantilla de color no cambia.
```

- [ ] **Step 7: Suite completa del servidor**

Run: `mvn -q test; echo "salida $?"`
Expected: `salida 0`, sin `FAIL` ni `ERROR`. Después,
`node -e "console.log(JSON.stringify(require('./target/openapi.json').components.schemas.FilaColorNsys.properties.caja))"`
da `{"type":"string","nullable":true}`. Ese `target/openapi.json` es el que copia la Task 2.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/nsys/FilaColorNsys.java \
        src/main/java/com/reparaciones/servidor/nsys/TarjetasNsys.java \
        src/test/java/com/reparaciones/servidor/nsys/TarjetasNsysTest.java \
        src/test/java/com/reparaciones/servidor/OpenApiContractTest.java \
        docs/api_contract.md
git commit -m "feat: la fila de Cambio de color lleva la caja de NSYS de su lote (0.9.26)"
```

---

### Task 2: Web — contrato, fábrica y las reglas de las cajas

**Files:**
- Modify (generados): `api/openapi.json`, `src/shared/api/schema.d.ts`
- Modify: `src/modules/telefonos/test/fabrica.ts` (`filaColor`)
- Modify: `src/modules/telefonos/stock/porPasar.ts` (imports, `AYUDA.color` y sección nueva al final)
- Test: `src/modules/telefonos/stock/porPasar.test.ts`

**Interfaces:**
- Consumes: `/c/Users/dev/Documents/wt/servidor-0926-cajas/target/openapi.json` (Task 1); `PeticionExcel` de
  `@/shared/api/client` (0.9.25); `hoyMadrid(ahora?: Date): string` de `@/shared/lib/fechas`.
- Produces (en `./porPasar`):
  - `SIN_CAJA = 'sin caja'`;
  - `textoCaja(caja: string | null | undefined): string`: la caja; `sin caja` si es null o está en blanco; `—` si es
    `undefined` (servidor anterior);
  - `paraCajasColor(filas: readonly FilaColorNsys[]): FilaColorNsys[]`;
  - `HOJA_CAJAS = 'Cajas cambio de color'`;
  - `excelCajasColor(filas: readonly FilaColorNsys[]): PeticionExcel`;
  - `nombreCajas(ahora?: Date): string` → `Cajas_color_AAAA-MM-DD.xlsx`;
  - `avisoCajas(nombre: string, hoja: PeticionExcel): string`;
  - `filaColor()` de la fábrica lleva `caja: 'CAJA LISTOS'` por defecto.

- [ ] **Step 1: El worktree de la web, el contrato y los tipos**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q origin && git worktree list && git worktree add -b dev_v0926_cajas_color /c/Users/dev/Documents/wt/web-0926-cajas origin/main && git -C /c/Users/dev/Documents/wt/web-0926-cajas log --oneline -1
cd /c/Users/dev/Documents/wt/web-0926-cajas && npm ci
cp /c/Users/dev/Documents/wt/servidor-0926-cajas/target/openapi.json api/openapi.json && npm run api:types:offline
git diff --stat
git diff src/shared/api/schema.d.ts
```

Expected: el worktree sobre `origin/main` (el 06/11, `fbbfc97 docs: fecha de la 0.9.25 en el changelog…`; si ha entrado otra
versión, avisar al usuario por el número), `npm ci` sin errores y `git diff --stat` solo con `api/openapi.json` y
`src/shared/api/schema.d.ts`. En `schema.d.ts`, el esquema `FilaColorNsys` gana, detrás de `esEsim: boolean;`, la línea
`caja: string | null;`. **Si el diff toca algo más, parar y avisar** (el `main` del servidor y el contrato de la web no van a la
par).

- [ ] **Step 2: Las pruebas que fallan**

En `src/modules/telefonos/stock/porPasar.test.ts`:

1. En el `import { … } from './porPasar'`, añade a la lista
   `avisoCajas, excelCajasColor, HOJA_CAJAS, nombreCajas, paraCajasColor, SIN_CAJA, textoCaja,`.
2. En el test `'los textos de cada tarjeta'`, la línea
   `expect(AYUDA.color).toMatch(/^Móviles a los que el taller ha cambiado el color\. En NSYS: bórralos y súbelos otra vez/)` pasa a:

```ts
    expect(AYUDA.color).toBe('Móviles a los que el taller ha cambiado el color. Antes de tocarlos en NSYS, descarga las cajas: la plantilla no lleva la caja y NSYS los deja sin ella. En NSYS: bórralos (se pueden borrar todos de una vez), súbelos otra vez con la plantilla, que lleva el grado que tienen hoy en NSYS (el grado nuevo, si lo hay, va por «Cambio de grado», después), y devuélvelos a su caja con «Location». Cada fila se va sola cuando la importación (cada 30 min) ve el color nuevo en NSYS.')
```

3. Al final del fichero, añade:

```ts
describe('0.9.26: las cajas del cambio de color (spec «cajas del cambio de color» §3 y §4)', () => {
  it('la caja tal cual; sin caja, «sin caja»; con un servidor anterior (sin el campo), «—»', () => {
    expect(textoCaja('CAJA LISTOS')).toBe('CAJA LISTOS')
    expect(textoCaja(null)).toBe(SIN_CAJA)
    expect(textoCaja('  ')).toBe('sin caja')
    expect(textoCaja(undefined)).toBe('—')
  })

  it('llevan lo de la plantilla y también lo marcado «Hecho»; ni sin producto ni los que han vuelto', () => {
    const filas = [
      filaColor(),
      filaColor({ imeiSn: '350000000000012', ...HECHO }),
      filaColor({ imeiSn: '350000000000013', ponerProducto: null }),
      filaColor({ imeiSn: '350000000000014', vendidoEn: '2026-09-14', vueltoDesde: '2026-10-22T14:06:00' }),
    ]
    expect(paraCajasColor(filas).map((f) => f.imeiSn)).toEqual(['350000000000011', '350000000000012'])
    expect(paraPlantillaColor(filas).map((f) => f.imeiSn), 'la plantilla sigue sin los marcados').toEqual(['350000000000011'])
  })

  it('el Excel: IMEI, caja, modelo y color nuevo, por caja sin mirar mayúsculas («sin caja» al final) y por IMEI', () => {
    const hoja = excelCajasColor([
      filaColor({ imeiSn: '350000000000015', caja: null }),
      filaColor({ imeiSn: '350000000000014', caja: 'CAJA PULIR', esEsim: true }),
      filaColor({ imeiSn: '350000000000013', caja: 'caja glass', colorA: null }),
      filaColor({ imeiSn: '350000000000012', caja: 'CAJA LISTOS' }),
      filaColor({ imeiSn: '350000000000011', caja: 'CAJA LISTOS' }),
      filaColor({ imeiSn: '350000000000016', ponerProducto: null }),
    ])
    expect(hoja.hoja).toBe(HOJA_CAJAS)
    expect(HOJA_CAJAS).toBe('Cajas cambio de color')
    expect(hoja.columnas).toEqual(['IMEI', 'Caja', 'Modelo', 'Color nuevo'])
    expect(hoja.filas).toEqual([
      ['350000000000013', 'caja glass', 'iPhone 13 128GB', ''],
      ['350000000000011', 'CAJA LISTOS', 'iPhone 13 128GB', '(PRODUCT)RED'],
      ['350000000000012', 'CAJA LISTOS', 'iPhone 13 128GB', '(PRODUCT)RED'],
      ['350000000000014', 'CAJA PULIR', 'iPhone 13 eSIM 128GB', '(PRODUCT)RED'],
      ['350000000000015', 'sin caja', 'iPhone 13 128GB', '(PRODUCT)RED'],
    ])
  })

  it('el nombre del fichero es del día de Madrid y el aviso cuenta móviles y cajas', () => {
    expect(nombreCajas(new Date('2026-11-06T10:00:00Z'))).toBe('Cajas_color_2026-11-06.xlsx')
    expect(nombreCajas(new Date('2026-11-06T23:30:00Z')), 'en Madrid ya es el 7').toBe('Cajas_color_2026-11-07.xlsx')
    const dos = excelCajasColor([filaColor(), filaColor({ imeiSn: '350000000000012', caja: null })])
    expect(avisoCajas('Cajas_color_2026-11-06.xlsx', dos)).toBe('Descargado «Cajas_color_2026-11-06.xlsx» · 2 móviles en 2 cajas')
    expect(avisoCajas('mio.xlsx', excelCajasColor([filaColor()]))).toBe('Descargado «mio.xlsx» · 1 móvil en 1 caja')
  })
})
```

(`HECHO` ya está definido arriba en el fichero. La fábrica `filaColor` es un iPhone 13 128GB que pasa a `(PRODUCT)RED`.)

- [ ] **Step 3: Comprobar que fallan**

Run: `npx vitest run src/modules/telefonos/stock/porPasar.test.ts`
Expected: FAIL: `textoCaja`, `paraCajasColor`, `excelCajasColor`, `nombreCajas` y `avisoCajas` no existen
(`is not a function`) y `AYUDA.color` no es el texto nuevo.

- [ ] **Step 4: La fábrica**

En `src/modules/telefonos/test/fabrica.ts`, en `filaColor`, la línea
`desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, esEsim: false, ...p,`
pasa a:

```ts
    desde: '2026-10-24T10:12:00', hechoPor: null, hechoEn: null, hechoVolvio: null, vendidoEn: null, bajaEn: null, vueltoDesde: null, esEsim: false,
    caja: 'CAJA LISTOS', ...p,
```

- [ ] **Step 5: Las reglas**

En `src/modules/telefonos/stock/porPasar.ts`:

1. Las dos primeras líneas (los imports) pasan a:

```ts
import type {
  FilaAltaNsys, FilaColorNsys, FilaDescartadaNsys, FilaGradoNsys, FilaSinGradoNsys, PeticionExcel, PorQueTaller, TarjetasPorPasar,
} from '@/shared/api/client'
import { hoyMadrid } from '@/shared/lib/fechas'
import { descripcionMovil, diaNsys, nombreModeloNsys, nombreMovilNsys, textoBateriaRecordada } from '@/shared/lib/telefonoNsys'
```

2. En `AYUDA`, la línea `color: 'Móviles a los que el taller ha cambiado el color. En NSYS: bórralos y súbelos otra vez con la
   plantilla, …',` pasa a:

```ts
  color: 'Móviles a los que el taller ha cambiado el color. Antes de tocarlos en NSYS, descarga las cajas: la plantilla no lleva la caja y NSYS los deja sin ella. En NSYS: bórralos (se pueden borrar todos de una vez), súbelos otra vez con la plantilla, que lleva el grado que tienen hoy en NSYS (el grado nuevo, si lo hay, va por «Cambio de grado», después), y devuélvelos a su caja con «Location». Cada fila se va sola cuando la importación (cada 30 min) ve el color nuevo en NSYS.',
```

3. Al final del fichero, añade:

```ts

// ---------------------------------------------------------------- 0.9.26: las cajas del cambio de color

/** Lo que dice la columna «Caja» y el Excel de un móvil que NSYS tiene sin caja. */
export const SIN_CAJA = 'sin caja'

/** La caja de NSYS de una fila de «Cambio de color» (spec «cajas del cambio de color» §3.1): tal cual; sin caja, «sin caja»; con
 *  un servidor anterior a la 0.9.26, que no manda el campo, «—». */
export function textoCaja(caja: string | null | undefined): string {
  if (caja === undefined) return '—'
  return caja !== null && caja.trim() !== '' ? caja : SIN_CAJA
}

/** Los móviles de «⬇ Descargar cajas» (spec §4): los de la plantilla de color (con producto, sin los que han vuelto) también si
 *  están marcados «Hecho»: se marcan al subir la plantilla, que es cuando hacen falta las cajas. */
export const paraCajasColor = (filas: readonly FilaColorNsys[]): FilaColorNsys[] =>
  filas.filter((f) => f.ponerProducto !== null && !f.vueltoDesde)

export const HOJA_CAJAS = 'Cajas cambio de color'

/** Por caja, de la A a la Z sin mirar mayúsculas, y «sin caja» al final. */
function compararCajas(a: string, b: string): number {
  if (a === b) return 0
  if (a === SIN_CAJA) return 1
  if (b === SIN_CAJA) return -1
  return a.localeCompare(b, 'es', { sensitivity: 'base' }) || a.localeCompare(b, 'es')
}

/** La hoja del Excel de las cajas (spec §3.3): IMEI, caja, modelo («iPhone 14 eSIM 256GB») y color nuevo como lo escribe NSYS,
 *  por caja y, dentro, por IMEI; así cada caja es un bloque seguido, como se seleccionan en NSYS. */
export function excelCajasColor(filas: readonly FilaColorNsys[]): PeticionExcel {
  const lineas = paraCajasColor(filas).map((f) => ({ f, caja: textoCaja(f.caja) }))
  lineas.sort((x, y) => compararCajas(x.caja, y.caja) || x.f.imeiSn.localeCompare(y.f.imeiSn))
  return {
    hoja: HOJA_CAJAS,
    columnas: ['IMEI', 'Caja', 'Modelo', 'Color nuevo'],
    filas: lineas.map(({ f, caja }) => [f.imeiSn, caja, modeloDeFila(f), f.colorA ?? '']),
  }
}

/** El nombre que propone «Guardar como»: `Cajas_color_2026-11-06.xlsx`, con el día de Madrid. */
export const nombreCajas = (ahora: Date = new Date()): string => `Cajas_color_${hoyMadrid(ahora)}.xlsx`

/** «Descargado «Cajas_color_2026-11-06.xlsx» · 12 móviles en 3 cajas», de la hoja que se bajó («sin caja» cuenta como una). */
export function avisoCajas(nombre: string, hoja: PeticionExcel): string {
  const n = hoja.filas.length
  const cajas = new Set(hoja.filas.map((fila) => fila[1])).size
  return `Descargado «${nombre}» · ${n} ${palabraMoviles(n)} en ${cajas} ${cajas === 1 ? 'caja' : 'cajas'}`
}
```

- [ ] **Step 6: Comprobar que pasan**

Run: `npx vitest run src/modules/telefonos/stock/porPasar.test.ts && npx tsc -b && npx eslint src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts src/modules/telefonos/test/fabrica.ts`
Expected: PASS (con los 4 nuevos y el de los textos cambiado); sin errores de tipos ni de lint. Si `tsc` se queja de otro objeto `FilaColorNsys` escrito a mano
sin `caja` (fuera de la fábrica), añádele `caja: null`.

- [ ] **Step 7: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/modules/telefonos/test/fabrica.ts \
        src/modules/telefonos/stock/porPasar.ts src/modules/telefonos/stock/porPasar.test.ts
git commit -m "feat: reglas de las cajas del cambio de color, el Excel por caja y la ayuda nueva (0.9.26)"
```

---

### Task 3: Web — la columna «Caja» y el botón «⬇ Descargar cajas»

**Files:**
- Modify: `src/modules/telefonos/stock/ListasPorPasar.tsx` (import de `./porPasar`, componente `Caja`, `ListaColor`)
- Modify: `src/modules/telefonos/stock/PorPasarNsys.tsx` (imports, `bajarCajas`, `boton`)
- Test: `src/modules/telefonos/stock/PorPasarNsys.test.tsx`

**Interfaces:**
- Consumes (Task 2): `SIN_CAJA`, `textoCaja`, `paraCajasColor`, `excelCajasColor`, `nombreCajas`, `avisoCajas` de `./porPasar`;
  `useDescargarExcel()` y `TIPO_XLSX` de `../api` (0.9.25: mutación `({ hoja, nombre, destino }) => Promise<string>`, el nombre
  con que se guardó); `elegirDestino(sugerido, tipo): Promise<FileSystemFileHandle | null | 'cancelado'>` de
  `@/shared/lib/descargas`.

- [ ] **Step 1: Las pruebas que fallan**

En `src/modules/telefonos/stock/PorPasarNsys.test.tsx`:

1. La línea `import type { TarjetasPorPasar } from '@/shared/api/client'` pasa a
   `import type { FilaColorNsys, TarjetasPorPasar } from '@/shared/api/client'`.
2. En el test `'cada tarjeta dice cuántos hay y cuántos hechos; sin pendientes, su botón no se puede pulsar'`, justo después de
   `expect(within(color).getByRole('button', { name: '⬇ Descargar plantilla (1)' })).toBeEnabled()`, añade:

```tsx
    // 0.9.26: las cajas llevan también la marcada «Hecho».
    expect(within(color).getByRole('button', { name: '⬇ Descargar cajas (2)' })).toHaveAttribute('title', '2 móviles')
```

3. En el test `'Color destaca lo que cambia, dice si también está en Grado y lo que no va en la plantilla'`, la línea
   `expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Color', 'En NSYS hoy', 'Poner en NSYS', 'Por qué', 'Desde', '✓ Marcar todo (3)'])`
   pasa a:

```tsx
    expect(cabeceras(tabla)).toEqual(['IMEI', 'Modelo', 'Color', 'En NSYS hoy', 'Caja', 'Poner en NSYS', 'Por qué', 'Desde', '✓ Marcar todo (3)'])
```

   y, justo después de `expect(uno).toHaveTextContent('iPhone 13 128GB')`, añade `expect(uno).toHaveTextContent('CAJA LISTOS')`.
4. Al final del fichero, añade:

```tsx
describe('«⬇ Descargar cajas» en «Cambio de color» (0.9.26)', () => {
  afterEach(() => {
    delete window.showSaveFilePicker
    vi.restoreAllMocks()
  })

  it('la columna «Caja»: tal cual, «sin caja» en gris y, con un servidor anterior, «—»', () => {
    const vieja: Partial<FilaColorNsys> = filaColor({ imeiSn: '350000000000013' })
    delete vieja.caja
    pintar(tarjetas({ color: [filaColor(), filaColor({ imeiSn: '350000000000012', caja: null }), vieja as FilaColorNsys] }))
    const [uno, dos, tres] = filas(screen.getByRole('table', { name: 'Cambio de color' }))
    const caja = (fila: HTMLElement) => within(fila).getAllByRole('cell')[4]
    expect(caja(uno)).toHaveTextContent(/^CAJA LISTOS$/)
    expect(caja(dos)).toHaveTextContent(/^sin caja$/)
    expect(within(caja(dos)).getByText('sin caja')).toHaveClass('text-azul-gris')
    expect(caja(tres)).toHaveTextContent(/^—$/)
  })

  it('abre «Guardar como» con su nombre antes de pedir el Excel, manda la hoja por caja con lo marcado y avisa', async () => {
    const user = userEvent.setup()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const orden: string[] = []
    const cuerpos: unknown[] = []
    const escrito: Blob[] = []
    const ventana = vi.fn(async () => {
      orden.push('ventana')
      return { name: 'mio.xlsx', createWritable: async () => ({ write: async (b: Blob) => { escrito.push(b) }, close: async () => {} }) }
    })
    window.showSaveFilePicker = ventana as unknown as typeof window.showSaveFilePicker
    server.use(http.post('*/api/stock-telefonos/que-revisar/excel', async ({ request }) => {
      orden.push('servidor')
      cuerpos.push(await request.json())
      return new HttpResponse('PK', { headers: { 'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' } })
    }))
    pintar(tarjetas({ color: [
      filaColor({ imeiSn: '350000000000012', caja: 'CAJA PULIR', ...HECHO }),
      filaColor(),
      filaColor({ imeiSn: '350000000000013', ponerProducto: null }),
    ] }))
    await user.click(within(screen.getByRole('article', { name: 'Cambio de color' })).getByRole('button', { name: '⬇ Descargar cajas (2)' }))
    expect(await screen.findByTestId('aviso-breve')).toHaveTextContent('Descargado «mio.xlsx» · 2 móviles en 2 cajas')
    expect(ventana).toHaveBeenCalledWith(expect.objectContaining({ suggestedName: `Cajas_color_${hoyMadrid()}.xlsx` }))
    expect(orden).toEqual(['ventana', 'servidor'])
    expect(cuerpos).toEqual([{
      hoja: 'Cajas cambio de color',
      columnas: ['IMEI', 'Caja', 'Modelo', 'Color nuevo'],
      filas: [
        ['350000000000011', 'CAJA LISTOS', 'iPhone 13 128GB', '(PRODUCT)RED'],
        ['350000000000012', 'CAJA PULIR', 'iPhone 13 128GB', '(PRODUCT)RED'],
      ],
    }])
    expect(await escrito[0].text()).toBe('PK')
  })

  it('sin nada que bajar está desactivado y lo dice al pasar el ratón', () => {
    pintar(tarjetas({ color: [filaColor({ ponerProducto: null })] }))
    const apagado = within(screen.getByRole('article', { name: 'Cambio de color' })).getByRole('button', { name: '⬇ Descargar cajas' })
    expect(apagado).toBeDisabled()
    expect(apagado).toHaveAttribute('title', 'No hay móviles que corregir')
  })

  it('si se cancela «Guardar como», no pide nada al servidor ni avisa', async () => {
    const user = userEvent.setup()
    let pedida = false
    window.showSaveFilePicker = vi.fn(async () => { throw new DOMException('cancelado', 'AbortError') })
    server.use(http.post('*/api/stock-telefonos/que-revisar/excel', () => {
      pedida = true
      return new HttpResponse('PK')
    }))
    pintar(tarjetas({ color: [filaColor()] }))
    await user.click(screen.getByRole('button', { name: '⬇ Descargar cajas (1)' }))
    await waitFor(() => expect(window.showSaveFilePicker).toHaveBeenCalledTimes(1))
    expect(pedida).toBe(false)
    expect(screen.queryByTestId('aviso-breve')).toBeNull()
  })
})
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/telefonos/stock/PorPasarNsys.test.tsx`
Expected: FAIL: no hay botón `⬇ Descargar cajas (2)` ni columna `Caja`.

- [ ] **Step 3: La columna**

En `src/modules/telefonos/stock/ListasPorPasar.tsx`:

1. En el `import { … } from './porPasar'`, añade `SIN_CAJA, textoCaja,` a la lista.
2. Justo antes del comentario `/** «Cambio de color» (§3.2). */` (encima de `export function ListaColor`), añade:

```tsx
/** La caja de NSYS de la fila (0.9.26, spec «cajas del cambio de color» §3.1): tal cual; «sin caja» en gris; «—» con un servidor
 *  anterior. */
function Caja({ caja }: { caja: string | null | undefined }) {
  const texto = textoCaja(caja)
  return texto === SIN_CAJA ? <span className="text-azul-gris">{texto}</span> : <>{texto}</>
}

```

3. En `ListaColor`, `columnas={['IMEI', 'Modelo', 'Color', 'En NSYS hoy', 'Poner en NSYS', 'Por qué', 'Desde']}` pasa a
   `columnas={['IMEI', 'Modelo', 'Color', 'En NSYS hoy', 'Caja', 'Poner en NSYS', 'Por qué', 'Desde']}`, y justo después de
   `<td className={CELDA}>{f.productoNsys ?? '—'}</td>` añade:

```tsx
          <td className={cn(CELDA, 'whitespace-nowrap')}><Caja caja={f.caja} /></td>
```

- [ ] **Step 4: El botón**

En `src/modules/telefonos/stock/PorPasarNsys.tsx`:

1. `import { useState } from 'react'` pasa a `import { useRef, useState } from 'react'`.
2. El import de `../api` pasa a:

```tsx
import {
  nombrePlantilla, TIPO_XLSX, useDescargarExcel, useDescargarPlantilla, useDescartarAlta, useDeshacerDescarte, useDeshacerHecho, useMarcarHecho,
  useRevisarAviso, type Plantilla,
} from '../api'
```

3. El import de `./porPasar` pasa a:

```tsx
import {
  avisoCajas, avisoCopiado, avisoDescartado, avisoPlantillaAltas, avisoPlantillaColor, AYUDA, avisoVuelve, cuentas, excelCajasColor, listaGrado,
  nombreCajas, paraCajasColor, paraPlantillaColor, pendientes, TARJETA_HECHO, VACIA, vueltosFueraDePlantilla, type Tarjeta, type TarjetaConHecho,
} from './porPasar'
```

4. Justo después de `const descargar = useDescargarPlantilla()`, añade:

```tsx
  const descargarExcel = useDescargarExcel()
  const cajasAbierto = useRef(false)
```

5. Justo después de la función `bajar` (tras su `}` de cierre, antes de `function boton(cual: Tarjeta) {`), añade:

```tsx

  // 0.9.26 (spec «cajas del cambio de color» §3.2): como la plantilla, «Guardar como» en el mismo clic y después el Excel, que
  // escribe la ruta genérica de «Qué revisar». Mientras la ventana está abierta, otro clic no abre otra.
  const bajarCajas = async () => {
    if (cajasAbierto.current) return
    cajasAbierto.current = true
    try {
      const hoja = excelCajasColor(t.color)
      const nombre = nombreCajas()
      const destino = await elegirDestino(nombre, TIPO_XLSX)
      if (destino === 'cancelado') return
      descargarExcel.mutate({ hoja, nombre, destino }, { onSuccess: (guardado) => avisar({ texto: avisoCajas(guardado, hoja), tipo: 'ok' }) })
    } finally {
      cajasAbierto.current = false
    }
  }
```

6. En `boton`, el bloque

```tsx
    if (cual === 'color' || cual === 'altas') {
      const n = cual === 'color' ? paraPlantillaColor(t.color).length : pendientes(t.altas).length
      return (
        <button type="button" disabled={n === 0 || descargar.isPending} title={n === 0 ? SIN_NADA : `${n} ${n === 1 ? 'móvil' : 'móviles'}`}
          onClick={(e) => { e.stopPropagation(); void bajar(cual) }} className={PRIMARIO}>
          {`⬇ Descargar plantilla${n > 0 ? ` (${n})` : ''}`}
        </button>
      )
    }
```

   pasa a:

```tsx
    if (cual === 'color' || cual === 'altas') {
      const n = cual === 'color' ? paraPlantillaColor(t.color).length : pendientes(t.altas).length
      const plantilla = (
        <button type="button" disabled={n === 0 || descargar.isPending} title={n === 0 ? SIN_NADA : `${n} ${n === 1 ? 'móvil' : 'móviles'}`}
          onClick={(e) => { e.stopPropagation(); void bajar(cual) }} className={PRIMARIO}>
          {`⬇ Descargar plantilla${n > 0 ? ` (${n})` : ''}`}
        </button>
      )
      if (cual === 'altas') return plantilla
      const c = paraCajasColor(t.color).length
      return (
        <>
          {plantilla}
          <button type="button" disabled={c === 0 || descargarExcel.isPending} title={c === 0 ? SIN_NADA : `${c} ${c === 1 ? 'móvil' : 'móviles'}`}
            onClick={(e) => { e.stopPropagation(); void bajarCajas() }} className={SECUNDARIO}>
            {`⬇ Descargar cajas${c > 0 ? ` (${c})` : ''}`}
          </button>
        </>
      )
    }
```

- [ ] **Step 5: Comprobar que pasan**

Run: `npx vitest run src/modules/telefonos/stock/PorPasarNsys.test.tsx src/modules/telefonos/stock/porPasar.test.ts && npm run check`
Expected: PASS; `npm run check` (lint + tipos + todos los tests) en verde.

- [ ] **Step 6: Commit**

```bash
git add src/modules/telefonos/stock/ListasPorPasar.tsx src/modules/telefonos/stock/PorPasarNsys.tsx \
        src/modules/telefonos/stock/PorPasarNsys.test.tsx
git commit -m "feat: columna Caja y Descargar cajas en Cambio de color (0.9.26)"
```

---

### Task 4: E2E, versión 0.9.26, CHANGELOG y novedades

**Files:**
- Modify: `tests/e2e/plantillas-nsys.spec.ts` (comentario de arriba, tipo `FilaColor` y un test nuevo al final)
- Modify: `package.json`, `package-lock.json` (versión), `CHANGELOG.md`
- Raíz (`/c/Users/dev/Documents/ProgramaReparaciones`): `docs/novedades/NOVEDADES-v0.9.26.md` (los pedidos) pasa a
  `NOVEDADES-v0.9.27.md`, y se crea `docs/novedades/NOVEDADES-v0.9.26.md` nuevo

**Interfaces:**
- Consumes: la app desplegada con las Tasks 1-3 (la E2E corre en la Task 5, contra preprod); `leerXlsx` de `tests/e2e/xlsx.ts`;
  la «Guardar como» falsa (`guardarComoFalso`), el tipo `Guardado` y `tarjetasApi` de `plantillas-nsys.spec.ts`.

Sobre la E2E: va en `plantillas-nsys.spec.ts` porque ese fichero ya pone un cambio de color sintético en el móvil de prueba de
preprod y lo limpia al acabar (`limpiar`: marcas fuera y «Volver a lo de NSYS»). El test nuevo va el último: marca el móvil
«Hecho», baja las cajas, mira su fila y deshace la marca. El Excel no escribe nada en la base.

- [ ] **Step 1: La E2E**

En `tests/e2e/plantillas-nsys.spec.ts`:

1. Justo después de la línea del comentario de arriba
   `// color y de altas (cabecera de la vacía de NSYS, columnas, rellenos), «Copiar lista» y los números de las tarjetas y de la pestaña.`,
   añade:

```ts
// 0.9.26: «⬇ Descargar cajas» lleva el móvil de prueba con la caja que da la API, también marcado «Hecho» (spec «cajas del cambio
// de color» §3.3 y §4).
```

2. La línea `type FilaColor = FilaConHecho & { ponerProducto: string | null; compruebalo: boolean }` pasa a:

```ts
type FilaColor = FilaConHecho & { ponerProducto: string | null; compruebalo: boolean; colorA: string | null; caja?: string | null }
```

3. Dentro del `test.describe`, después del test `'«Hecho» marca la fila y «deshacer» la devuelve'` (antes del `})` que cierra el
   `describe`), añade:

```ts

  test('«Descargar cajas» lleva el móvil con su caja de NSYS, también marcado «Hecho»', async () => {
    test.skip(!colorNuevo, 'sin cambio de color (ver la prueba del cambio de color y de grado)')
    await pagina.goto('/telefonos/stock')
    await pagina.getByRole('button', { name: /^Por pasar a NSYS/ }).click()
    await pagina.getByRole('button', { name: `Hecho ${IMEI}` }).click()
    await expect(pagina.getByRole('button', { name: `Deshacer Hecho de ${IMEI}` })).toBeVisible()
    const fila = (await tarjetasApi(pagina)).color.find((f) => f.imeiSn === IMEI)
    expect(fila?.hechoEn, 'la marca está en el servidor').toBeTruthy()
    expect(fila && 'caja' in fila, 'el servidor manda la caja (0.9.26)').toBe(true)
    const caja = fila!.caja && fila!.caja.trim() !== '' ? fila!.caja : 'sin caja'

    const respuesta = pagina.waitForResponse((r) => new URL(r.url()).pathname === '/api/stock-telefonos/que-revisar/excel')
    await pagina.getByRole('article', { name: 'Cambio de color' }).getByRole('button', { name: /^⬇ Descargar cajas/ }).click()
    expect((await respuesta).status(), 'el Excel de las cajas').toBe(200)
    await expect(pagina.getByTestId('aviso-breve')).toContainText(/Descargado «Cajas_color_\d{4}-\d{2}-\d{2}\.xlsx» · \d+ móvil/)
    const guardado = await pagina.evaluate(() => (Reflect.get(globalThis, 'guardadosE2E') as Guardado[]).at(-1))
    expect(guardado?.nombre).toMatch(/^Cajas_color_\d{4}-\d{2}-\d{2}\.xlsx$/)
    const xlsx = leerXlsx(Buffer.from(guardado!.bytes))
    expect(['A1', 'B1', 'C1', 'D1'].map((c) => xlsx.celda(c).valor)).toEqual(['IMEI', 'Caja', 'Modelo', 'Color nuevo'])
    const n = xlsx.filasConDatos(2, 'A').find((f) => xlsx.celda(`A${f}`).valor === IMEI)
    expect(n, 'el móvil de prueba, marcado «Hecho», está en el Excel').toBeDefined()
    expect(xlsx.celda(`B${n}`).valor, 'su caja').toBe(caja)
    expect(String(xlsx.celda(`C${n}`).valor), 'el modelo').toMatch(/^iPhone /)
    expect(xlsx.celda(`D${n}`).valor, 'el color nuevo').toBe(fila!.colorA ? fila!.colorA : null)

    await pagina.getByRole('button', { name: `Deshacer Hecho de ${IMEI}` }).click()
    await expect(pagina.getByRole('button', { name: `Hecho ${IMEI}` })).toBeVisible()
  })
```

- [ ] **Step 2: Que compila y pasa el lint**

Run: `npx eslint tests/e2e/plantillas-nsys.spec.ts && npx playwright test tests/e2e/plantillas-nsys.spec.ts --list`
Expected: sin errores de lint, y la lista incluye `«Descargar cajas» lleva el móvil con su caja de NSYS, también marcado «Hecho»`.
(`--list` no abre el navegador ni necesita `~/.env.e2e`: sin `E2E_BASE_URL` la configuración usa `localhost`. La prueba solo corre
contra preprod, en la Task 5.)

- [ ] **Step 3: Versión y CHANGELOG**

```bash
npm version 0.9.26 --no-git-tag-version
grep -n '"version"' package.json package-lock.json | head -3
```

Expected: `0.9.26` en `package.json` y en las dos primeras de `package-lock.json`.

En `CHANGELOG.md`, justo antes de la línea `## [0.9.25] - 2026-11-05 — «Qué revisar» en Stock de teléfonos`, añade:

```markdown
## [0.9.26] - (pendiente) — Las cajas del cambio de color

- **«⬇ Descargar cajas (N)»** en la tarjeta «Cambio de color» de «Por pasar a NSYS», detrás de «⬇ Descargar plantilla»: un Excel aparte (la plantilla no cambia) con el IMEI, la caja de NSYS («CAJA LISTOS»), el modelo («iPhone 14 eSIM 256GB») y el color nuevo de cada móvil, ordenado por caja (sin mirar mayúsculas, «sin caja» al final) y luego por IMEI, para devolverlos a su caja en NSYS por bloques. La plantilla de NSYS no lleva la caja: al subirla, el móvil entra sin caja. Lleva los mismos móviles que la plantilla y también los marcados «Hecho». Abre «Guardar como» con el nombre ya puesto (`Cajas_color_2026-11-06.xlsx`) y avisa de cuántos móviles y cajas lleva.
- Columna **«Caja»** en la lista de «Cambio de color», entre «En NSYS hoy» y «Poner en NSYS»: la caja tal cual la escribe NSYS, o «sin caja» en gris.
- La ayuda de «Cambio de color» dice el orden: descargar las cajas antes de tocar NSYS, borrar (se pueden borrar todos de una vez), subir la plantilla y devolverlos a su caja con «Location».
- Servidor y web, sin migración: el servidor manda la `caja` en cada fila de «Cambio de color» y el Excel lo escribe la ruta de «Qué revisar» (`POST /api/stock-telefonos/que-revisar/excel`). Con el servidor de la 0.9.25, la columna y el Excel dicen «—».

```

- [ ] **Step 4: Commit de la web**

```bash
git add tests/e2e/plantillas-nsys.spec.ts package.json package-lock.json CHANGELOG.md
git commit -m "chore: version 0.9.26, changelog y E2E de Descargar cajas"
```

- [ ] **Step 5: Novedades en la raíz**

Las novedades de los pedidos están ya como `NOVEDADES-v0.9.26.md` y ahora son la 0.9.27:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git status --short docs/novedades/
git mv docs/novedades/NOVEDADES-v0.9.26.md docs/novedades/NOVEDADES-v0.9.27.md
sed -i '1s/^# Novedades de la versión 0.9.26$/# Novedades de la versión 0.9.27/' docs/novedades/NOVEDADES-v0.9.27.md
head -1 docs/novedades/NOVEDADES-v0.9.27.md
```

Expected: `# Novedades de la versión 0.9.27`.

Crea `docs/novedades/NOVEDADES-v0.9.26.md` con:

```markdown
# Novedades de la versión 0.9.26

---

## 📱 Stock de teléfonos (supertécnicos y administrador)

- En «Por pasar a NSYS» → «Cambio de color», botón nuevo **«⬇ Descargar cajas»**, al lado de la plantilla. Baja un Excel con el
  IMEI, la caja de NSYS, el modelo y el color nuevo de cada móvil, ordenado por caja. La plantilla de NSYS no lleva la caja y,
  al subirla, los móviles se quedan sin ella: con este Excel los devuelves a su caja en NSYS por bloques (marcas todos los de
  una caja y les cambias la caja con «Location»).
- **Descárgalo antes de tocar NSYS.** Lleva los mismos móviles que la plantilla y también los que ya has marcado «Hecho», pero
  cada móvil se va de la lista cuando NSYS ya tiene el color nuevo (la importación es cada 30 minutos).
- La lista de «Cambio de color» tiene una columna **«Caja»** con la caja de NSYS de cada móvil («sin caja» si no tiene).
- La ayuda de la tarjeta dice el orden de los pasos y que en NSYS se pueden borrar todos de una vez.
```

```bash
git add docs/novedades/NOVEDADES-v0.9.26.md docs/novedades/NOVEDADES-v0.9.27.md
git commit -m "docs: novedades de la 0.9.26 (cajas del cambio de color); las de pedidos pasan a la 0.9.27"
```

---

### Task 5: Entrega (preprod, prueba a mano, producción en horario y merges)

**Files:** guion en `Apuntes/preprod/v0926-preprod.md` y registro en `Apuntes/despliegue_preprod.md` y
`Apuntes/despliegue_vdc_produccion.md` (fuera de los repos).

**Requisitos:** las Tasks 1-4 hechas y revisadas. **Push, merge, tags, gitlinks y cada despliegue, solo con el OK del usuario.**
Claude **no** hace SSH a las VMs: prepara los bloques de comandos y el usuario los ejecuta. Cada bloque empieza con `hostname` y la
comprobación de `backup-erp.sh` (en preprod, «ok: no es producción»; en producción, el seguro `if [ -x … ]`). Se avisa de que
`docker compose build` tarda alrededor de un minuto sin escribir nada.

- [ ] **Step 1: Avisar al usuario** de que las dos ramas `dev_v0926_cajas_color` están listas (servidor: `mvn -q test` en verde;
  web: `npm run check` en verde) y de lo que falta: push de las dos ramas, preprod (servidor y web), E2E, prueba a mano,
  producción en horario, merges y gitlinks.
- [ ] **Step 2: Preprod** (con su OK): push de las dos ramas y el guion `Apuntes/preprod/v0926-preprod.md` para que el usuario
  despliegue primero el servidor y después la web, siguiendo `Apuntes/despliegue_preprod.md`. Después, `/version.json` da
  `0.9.26`. Desde el PC, con las cuentas de `~/.env.e2e` y su `E2E_BASE_URL` de preprod:

```bash
cd /c/Users/dev/Documents/wt/web-0926-cajas && set -a && . ~/.env.e2e && set +a && npx playwright test tests/e2e/plantillas-nsys.spec.ts tests/e2e/que-revisar.spec.ts
```

  Expected: todo passed (el test nuevo, «Descargar cajas», incluido). Si da el 503 del límite de inicios de sesión, esperar un
  minuto y repetir solo ese fichero.
- [ ] **Step 3: Prueba a mano en preprod con el usuario:** la columna «Caja» en «Cambio de color» (cajas reales y «sin caja»);
  «⬇ Descargar cajas»: «Guardar como» con el nombre del día, abrir el Excel, ver las cuatro columnas y que cada caja va seguida;
  marcar una fila «Hecho» y comprobar que el número del botón no baja y que el móvil sigue en el Excel; la ayuda nueva.
- [ ] **Step 4: Producción en horario** (servidor + web, sin migración, con su OK): el corte es el reinicio del servidor; la web
  0.9.25 sigue funcionando con el servidor nuevo (ignora `caja`) y la vuelta atrás es desplegar el commit anterior. Guion con
  `hostname` + `backup-erp.sh` según `Apuntes/despliegue_vdc_produccion.md`: **primero el servidor, después la web** (o los dos
  en el mismo bloque). Después, `/version.json` da `0.9.26` y la tarjeta «Cambio de color» tiene «⬇ Descargar cajas».
- [ ] **Step 5: Merges y gitlinks** (con su OK): `git fetch` y comparar `main` con `origin/main` en el servidor, la web y la raíz
  (otras sesiones). Merge `--no-ff` de `dev_v0926_cajas_color` a `main` en los dos repos; fecha de la `[0.9.26]` en el
  CHANGELOG; gitlinks del servidor y de la web en la raíz. Los tags `v0.9.26` (servidor y web), cuando el usuario lo diga. Los
  worktrees y las ramas se borran después, también con su OK.
- [ ] **Step 6: Avisar de los pedidos:** ya pueden traer `main` a sus ramas (`dev_v0926_pedidos`, ahora 0.9.27) y renumerarse
  (CHANGELOG, `package.json`, spec, plan y memoria). En el servidor chocarán `FilaColorNsys`/`TarjetasNsys` solo si los pedidos
  los tocan; en la web, `CHANGELOG.md`, `package.json`/lock y quizá `porPasar.ts`.
- [ ] **Step 7: Registro:** el resultado en `Apuntes/despliegue_preprod.md` y `Apuntes/despliegue_vdc_produccion.md`; en
  `Apuntes/plan-futuro.md`, la entrada de la frase de la ayuda de «Cambio de color» (pendiente de la 0.9.22) como hecha; y la
  memoria del proyecto al día.
