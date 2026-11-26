# Reserva web: un n/a vale para un pedido A o A100 (0.9.49) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que la reserva automática de IMEI de los pedidos web (Distribuidores, Particulares, BackiPhone) coja un móvil N/A del mismo modelo, capacidad, formato y color cuando el pedido es A o A100, por delante del B o C.

**Architecture:** un solo método puro del servidor, `Reparto.puestoWeb`, decide el puesto de cada móvil para una fila web. Se añade el puesto 2 (N/A) y el B o C pasa al 3. El recomendador de la celda no cambia (ya propone los n/a). La web solo sube versión y CHANGELOG.

**Tech Stack:** Java 17 + Spring Boot + JUnit 5 (servidor, Maven); React + Vite (web, solo versión).

**Spec:** `docs/superpowers/specs/2026-11-26-pedidos-reserva-web-na-design.md`

## Global Constraints

- Versión: **0.9.49** (sube el número de la web aunque el cambio sea solo del servidor).
- Ramas por área en cada submódulo (`dev_reserva_web_na`), merge `--no-ff`; **nada de merge, push ni tags sin confirmación del usuario**.
- Commits sin línea `Co-Authored-By`.
- Maven en Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; /c/Users/dev/tools/apache-maven-3.9.16/bin/mvn …`.
- Textos de la web y de los comentarios en español, como el resto del código.
- Sale a producción **antes** de activar la regla «n/a como A» del ciclo de stock.

---

### Task 1: `Reparto.puestoWeb` con el N/A en el puesto 2 (servidor)

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/pedidos/Reparto.java:169-187`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/pedidos/RepartoTest.java`

**Interfaces:**
- Consumes: `Reparto.Grado` (`na()`, `naPorCaja()`, `bOC()`, `id()`, `ms()`), `pedidoA(String)`, `aceptaSku(Aceptacion, LoQueVale.Valor)`, `conTrabajo(...)` — todos ya existen en `Reparto.java`.
- Produces: `static Integer puestoWeb(Fila f, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo)` con la misma firma; valores 1, 2 (N/A), 3 (B o C) o null.

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-servidor && git checkout main && git checkout -b dev_reserva_web_na
```

- [ ] **Step 2: Cambiar los tests existentes de la web y añadir los nuevos**

En `RepartoTest.java`:

a) En `devolucionYSinCajaCuentanComoNa` (líneas 301-303), sustituir:

```java
        // En la web no valen como exacto.
        assertNull(puestoWeb(ac(false, "Azul", "A"), aDevolucion));
        assertNull(puestoWeb(ac(false, "Azul", "A"), aSinCaja));
```

por:

```java
        // En la web no valen como exacto, pero sí como N/A (0.9.49).
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), aDevolucion));
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), aSinCaja));
        assertNull(puestoWeb(ac(false, "Azul", "A-"), aSinCaja), "para A- no");
```

b) En `webPuesto1EsElSkuExacto` (línea 366), sustituir:

```java
        assertNull(puestoWeb(webA, movil("004", "Azul", "n/a", D1)));
```

por:

```java
        assertEquals(2, puestoWeb(webA, movil("004", "Azul", "n/a", D1)), "N/A: puesto 2, no exacto");
```

c) En `webBoCSoloConPedidoAoA100DelMismoColorYFormato`, los B/C pasan al puesto 3. Sustituir las líneas 375-377:

```java
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), b));
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), c));
        assertEquals(2, puestoWeb(ac(false, "Azul", "A100"), b));
```

por:

```java
        assertEquals(3, puestoWeb(ac(false, "Azul", "A"), b));
        assertEquals(3, puestoWeb(ac(false, "Azul", "A"), c));
        assertEquals(3, puestoWeb(ac(false, "Azul", "A100"), b));
```

y la línea 388:

```java
        assertEquals(List.of(1, 2), puestos(r));
```

por:

```java
        assertEquals(List.of(1, 3), puestos(r));
```

d) Añadir, justo después del test `webNuncaOtroColorNiOtroFormato` (antes de `// ── Prioridad entre filas ──`):

```java
    // ── Web: el N/A (0.9.49, regla «n/a como A» del ciclo de stock) ──

    @Test void webNaSoloConPedidoAoA100DelMismoColorYFormato() {
        MovilNsys na = movil("001", "Azul", "n/a", D1);
        MovilNsys naEsim = movil("002", true, "Azul", "n/a", LISTOS, D1);
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), na));
        assertEquals(2, puestoWeb(ac(false, "Azul", "A100"), na));
        assertEquals(2, puestoWeb(ac(true, "Azul", "A100"), naEsim));
        assertNull(puestoWeb(ac(false, "Azul", "A-"), na), "para A- no");
        assertNull(puestoWeb(ac(false, "Azul", "A-100"), na));
        assertNull(puestoWeb(ac(false, "Azul", "B"), na), "para B no");
        assertNull(puestoWeb(ac(false, "Rosa", "A"), na), "otro color, no");
        assertNull(puestoWeb(ac(true, "Azul", "A"), na), "otro formato, no");
        assertNull(puestoWeb(ac(false, "Azul", "A"), movil("003", false, "Azul", "ID", SIN_CAJA, D1)), "ID sin caja, no");
        assertNull(puestoWeb(ac(false, "Azul", "A"), movil("004", false, "Azul", "MsA", DEVOLUCION, D1)), "Ms, no");
    }

    @Test void webElNaVaDelanteDelBoCYDetrasDelExacto() {
        MovilNsys b = movil("001", "Azul", "B", D1);
        MovilNsys c = movil("002", false, "Azul", "C", REPARACION, D1);
        MovilNsys na = movil("003", false, "Azul", "n/a", SIN_CAJA, D3);
        MovilNsys a = movil("004", false, "Azul", "A", GLASS, D3);
        Aceptacion webA = ac(false, "Azul", "A");
        List<Reparto.Fila> filas = List.of(fila(1, Canal.DISTRIBUIDORES, "ES", HOY, webA),
                fila(2, Canal.PARTICULARES, "ES", HOY, webA), fila(3, Canal.BACKIPHONE, "ES", HOY, webA));
        List<Reparto.Resultado> r = de(filas, List.of(b, c, na, a));
        assertEquals(List.of("004", "003", "001"), imeis(r));
        assertEquals(List.of(1, 2, 3), puestos(r));
        assertEquals("Puesto 2 · En revisión · N/A", r.get(1).porque());
    }

    @Test void webConSoloNaYUnBElNaSeReservaYElBSeQueda() {
        MovilNsys b = movil("001", "Azul", "B", D1);
        MovilNsys na = movil("002", "Azul", "n/a", D2);
        List<Reparto.Resultado> r = de(List.of(fila(1, Canal.DISTRIBUIDORES, "ES", HOY, ac(false, "Azul", "A100"))), List.of(b, na));
        assertEquals(List.of("002"), imeis(r));
        assertEquals(List.of(2), puestos(r));
    }

    @Test void webAConSkuExactoPeroSinCajaEsPuesto2ComoNa() {
        MovilNsys aSinCaja = movil("001", false, "Azul", "A", SIN_CAJA, D1);
        List<Reparto.Resultado> r = de(List.of(fila(1, Canal.DISTRIBUIDORES, "ES", HOY, ac(false, "Azul", "A"))), List.of(aSinCaja));
        assertEquals(List.of(2), puestos(r));
        assertEquals("Puesto 2 · En revisión · N/A (es A)", r.get(0).porque());
    }

    @Test void webFFdYRmaSinCajaNuncaComoNa() {
        List<MovilNsys> stock = List.of(movil("001", false, "Azul", "F", SIN_CAJA, D1),
                movil("002", false, "Azul", "FD", DEVOLUCION, D1), movil("003", false, "Azul", "rma", SIN_CAJA, D1));
        Aceptacion webA = ac(false, "Azul", "A");
        List<Reparto.Fila> filas = List.of(fila(1, Canal.DISTRIBUIDORES, "ES", HOY, webA), fila(2, Canal.DISTRIBUIDORES, "ES", HOY, webA),
                fila(3, Canal.DISTRIBUIDORES, "ES", HOY, webA));
        assertEquals(Arrays.asList(null, null, null), imeis(de(filas, stock)));
    }
```

e) En `conTrabajoAbiertoSoloPuesto1YConElSkuAceptado`, después de `assertNull(Reparto.puestoWeb(web, bAzul, vale(bAzul), true));` (línea 470), añadir:

```java
        MovilNsys naAzul = movil("006", "Azul", "n/a", D1);
        assertNull(Reparto.puestoWeb(web, naAzul, vale(naAzul), true), "N/A con trabajo abierto, no");
```

- [ ] **Step 3: Ejecutar los tests y ver que fallan**

Run:
```bash
cd gestion-reparaciones-servidor && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && /c/Users/dev/tools/apache-maven-3.9.16/bin/mvn -q test -Dtest=RepartoTest
```
Expected: FAIL en `devolucionYSinCajaCuentanComoNa`, `webPuesto1EsElSkuExacto`, `webBoCSoloConPedidoAoA100DelMismoColorYFormato`, `webNaSoloConPedidoAoA100DelMismoColorYFormato`, `webElNaVaDelanteDelBoCYDetrasDelExacto`, `webConSoloNaYUnBElNaSeReservaYElBSeQueda`, `webAConSkuExactoPeroSinCajaEsPuesto2ComoNa`. (`webFFdYRmaSinCajaNuncaComoNa` y el de trabajo abierto deben pasar ya.)

- [ ] **Step 4: Implementar**

En `Reparto.java`, sustituir el Javadoc y el cuerpo privado de `puestoWeb` (líneas 169-187) por:

```java
    /** Tabla 4.2 (0.9.49): el SKU exacto del pedido (puesto 1) y, solo si el pedido es A o A100, un N/A (puesto 2: puede salir A
     *  al revisarlo y ahorrarse la reparación; es el que vende la regla «n/a como A» del ciclo de stock) o un B o C (puesto 3);
     *  siempre del mismo formato y color, nunca Ms ni ID. Un N/A por la caja (Devolución, sin caja) no vale como exacto: su SKU es
     *  el de un grado que aún no está revisado; cuenta como N/A. Con un trabajo del taller abierto, solo el puesto 1. */
    static Integer puestoWeb(Fila f, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo) {
        Aceptacion ac = f.aceptacion();
        return sirve(ac) ? puestoWeb(f, ac.pide(), m, v, conTrabajo) : null;
    }

    private static Integer puestoWeb(Fila f, Atributos pide, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo) {
        Aceptacion ac = f.aceptacion();
        if (!mismoModelo(pide, m)) return null;
        Grado g = grado(m, v);
        if (g.id() || g.ms() || m.esEsim() != pide.esEsim() || !igualDato(v.color(), pide.color())) return null;
        boolean pedidoA = pedidoA(pide.grado());
        Integer puesto;
        if (!g.naPorCaja() && aceptaSku(ac, v)) puesto = 1;
        else if (pedidoA && g.na()) puesto = 2;
        else if (pedidoA && g.bOC()) puesto = 3;
        else puesto = null;
        return conTrabajo(puesto, conTrabajo, ac, v);
    }
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run:
```bash
cd gestion-reparaciones-servidor && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && /c/Users/dev/tools/apache-maven-3.9.16/bin/mvn -q test -Dtest=RepartoTest
```
Expected: PASS (todos). `webFFdYRmaSinCajaNuncaComoNa` pasa porque `Reparto.de` ya descarta los elegibles cuyo `estado().conGrado(grado)` no es STOCK (F, FD y RMA), antes de mirar puestos (`Reparto.java:66`).

- [ ] **Step 6: La suite completa (incluye el test de arranque)**

Run:
```bash
cd gestion-reparaciones-servidor && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && /c/Users/dev/tools/apache-maven-3.9.16/bin/mvn -q test
```
Expected: BUILD SUCCESS. Si falla algún test de `ReservasTest`, `LecturaFilasTest` o del recomendador que dependía de «la web nunca reserva un N/A» o de «B/C es puesto 2», corregir el valor esperado al nuevo (N/A → 2, B/C → 3) solo si el caso es exactamente ese; cualquier otro fallo se investiga antes de tocar nada.

- [ ] **Step 7: Commit**

```bash
cd gestion-reparaciones-servidor && git add src/main/java/com/reparaciones/servidor/pedidos/Reparto.java src/test/java/com/reparaciones/servidor/pedidos/RepartoTest.java && git commit -m "feat(reserva): en la web un N/A vale para un pedido A o A100 (puesto 2, delante del B o C) (0.9.49)"
```

---

### Task 2: versión 0.9.49 de la web

**Files:**
- Modify: `gestion-reparaciones-web/package.json` (campo `version`)
- Modify: `gestion-reparaciones-web/package-lock.json` (las dos apariciones de la versión del paquete raíz)
- Modify: `gestion-reparaciones-web/CHANGELOG.md`

**Interfaces:** ninguna con el código.

- [ ] **Step 1: Rama**

```bash
cd gestion-reparaciones-web && git checkout main && git checkout -b dev_reserva_web_na
```

- [ ] **Step 2: Subir la versión**

```bash
cd gestion-reparaciones-web && npm version 0.9.49 --no-git-tag-version
```
Expected: imprime `v0.9.49`; `package.json` y `package-lock.json` cambian solo en la versión (`git diff --stat` = 2 ficheros).

- [ ] **Step 3: CHANGELOG**

En `gestion-reparaciones-web/CHANGELOG.md`, justo encima de `## [0.9.48.4] - 2026-11-26 …`, insertar:

```markdown
## [0.9.49] - 2026-11-26 — Reserva web: un móvil sin revisar (N/A) vale para un pedido A o A100 (solo servidor)

- En los pedidos de las webs (Distribuidores, Particulares, BackiPhone) de grado A o A100, la reserva coge ahora un móvil sin
  revisar (n/a, sin caja o en Devolución) del mismo modelo, capacidad, formato y color si no hay el SKU exacto. Va delante del
  B o C: al revisarlo puede salir A y ahorrarse la reparación.
- Es lo que vende la regla «n/a como A» del ciclo de stock: sin esto, esos pedidos se quedarían sin reserva.
- Para A- y A-100, nunca. Con un trabajo del taller abierto, solo el SKU exacto, como antes.
```

- [ ] **Step 4: Tests de la web**

Run:
```bash
cd gestion-reparaciones-web && npm test -- --run
```
Expected: todos pasan (el aviso de versión lee `package.json`).

- [ ] **Step 5: Commit**

```bash
cd gestion-reparaciones-web && git add package.json package-lock.json CHANGELOG.md && git commit -m "chore: version 0.9.49 (la reserva web acepta un N/A para un pedido A o A100, solo servidor)"
```

---

### Task 3: documentación del módulo y de la spec de la reserva (raíz)

**Files:**
- Modify: `docs/modulos/pedidos.md:106-107` y la tabla de versiones (línea ~125)
- Modify: `docs/superpowers/specs/2026-11-24-pedidos-reserva-imei-design.md:65-73` (§4.2)

- [ ] **Step 1: `docs/modulos/pedidos.md`**

Sustituir el párrafo que empieza en la línea 106:

```markdown
Webs (tabla 4.2): el SKU exacto del pedido (puesto 1) o, si el pedido es A o A100, un B o C (puesto 2), siempre del mismo color y
formato SIM; nunca un N/A por caja.
```

por:

```markdown
Webs (tabla 4.2): el SKU exacto del pedido (puesto 1) o, si el pedido es A o A100, un N/A (puesto 2, desde la 0.9.49: n/a, sin
caja o Devolución) o un B o C (puesto 3), siempre del mismo color y formato SIM. Un N/A por caja nunca vale como exacto.
```

(Conservar el resto de la frase que sigue en la línea 107 a partir de «Dentro de cada puesto».)

Y en la tabla de versiones, justo encima de la fila `| 0.9.48.4 | …`, añadir:

```markdown
| 0.9.49 | Reserva web: un N/A (n/a, sin caja o Devolución) del mismo modelo, formato y color vale para un pedido A o A100, delante del B o C; acompaña a la regla «n/a como A» del ciclo de stock |
```

- [ ] **Step 2: spec de la reserva, §4.2**

En `docs/superpowers/specs/2026-11-24-pedidos-reserva-imei-design.md`, sustituir la tabla de §4.2 y la frase de debajo (líneas 67-73) por:

```markdown
| Puesto | Teléfono | Formato | Color |
|---|---|---|---|
| 1 | El SKU exacto del pedido en la columna del canal (`ac.acepta(skuQueVale)`; grado y «100» incluidos) | el del pedido | el del pedido |
| 2 | N/A, **solo si el pedido es A o A100** (desde la 0.9.49; spec `2026-11-26-pedidos-reserva-web-na-design.md`) | el del pedido | el del pedido |
| 3 | B o C, **solo si el pedido es A o A100** (la batería va con la reparación); para A- no | el del pedido | el del pedido |

Nunca otro formato, otro color ni Ms en la web: eso lo negocia y lo pone una persona.
```

- [ ] **Step 3: Commit (raíz, en la rama de docs ya abierta)**

```bash
git checkout docs_na_como_a && git add docs/modulos/pedidos.md docs/superpowers/specs/2026-11-24-pedidos-reserva-imei-design.md docs/superpowers/specs/2026-11-26-pedidos-reserva-web-na-design.md docs/superpowers/plans/2026-11-26-pedidos-reserva-web-na.md && git commit -m "docs(pedidos): 0.9.49 en el modulo, la tabla 4.2 de la reserva y el plan"
```

---

### Task 4: integración y despliegue (con el usuario)

Sin código. Cada paso de merge/push/tag **se confirma antes con el usuario**.

- [ ] **Step 1:** Revisión del diff de servidor y web (`git diff main...dev_reserva_web_na` en cada submódulo).
- [ ] **Step 2:** Con confirmación: merge `--no-ff` de `dev_reserva_web_na` en `main` en servidor y web (`merge: version 0.9.49, …`), push de los dos, y en la raíz commit de gitlinks (`chore: gitlinks de la 0.9.49 …`) + merge de `docs_na_como_a`.
- [ ] **Step 3: Preprod.** Preparar el guion de comandos (el usuario los ejecuta; cada bloque empieza con `hostname`) para construir la 0.9.49 en preprod en primer plano. Después, E2E con datos sintéticos por API (cuentas de `~/.env.e2e`): una fila web A de un modelo con un n/a del mismo color en stock → queda reservado el n/a con «Puesto 2 · … · N/A»; con un B además → sigue el n/a; una fila A- → sin reserva. Limpieza de los datos sintéticos.
- [ ] **Step 4: Producción**, en horario (corte de segundos, sin migración): guion con `hostname` + `/usr/local/sbin/backup-erp.sh` al principio, build en primer plano, comprobación de la versión 0.9.49 en la web.
- [ ] **Step 5:** Avisar de que ya se puede activar la regla del ciclo de stock (plan del repo de automatización).
