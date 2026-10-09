# 0.9.7 — Tapa trasera como pieza propia: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La tapa trasera pasa a ser una pieza con SKU por modelo y color, 1 punto de dificultad y su fila «Tapa trasera» tras Chasis en el formulario de reparación; y el formulario deja de pintar las filas de tipos que no existen para el modelo elegido.

**Architecture:** Servidor: `tapa` → clave de puntos `tapa` en `PuntosCalculo` y `tapa` tras `cha` en el orden de `getAgrupadosPorTipo`; los datos (puntos y 65 SKU) van en un script SQL idempotente que aplica el usuario tras desplegar. Web: etiqueta «Tapa trasera» en `lib/piezas.ts`; en `formulario/estado.ts`, el estado guarda por tipo los modelos con algún SKU (activo o no) y un selector puro `filaVisible` decide qué filas pinta `FormularioReparacion`.

**Tech Stack:** Spring Boot 3.3 + JdbcTemplate + MariaDB (servidor, JUnit 5 + Mockito); React + TypeScript (web, Vitest + Testing Library + MSW).

**Spec:** `docs/superpowers/specs/2026-10-09-v097-tapa-trasera-design.md` (raíz).

## Global Constraints

- Versión **0.9.7**: servidor (`main` `0d6f400`) y web (`main` `d144d63`, `package.json` en `0.9.6`) se etiquetan juntos al final, **solo con OK del usuario**.
- Ramas **`feature/tapa-trasera`** en servidor y web, desde su `main`. Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**.
- Commits en español sin tildes, prefijo `feat:` / `test:` / `docs:` / `chore:`. **Sin** línea `Co-Authored-By`.
- Repos **públicos**: nada de IPs, nombres reales ni datos del taller en código, tests ni docs. Datos de tests sintéticos.
- Prefijo del SKU: **`tapa`** (sin `i`: el servidor corta el prefijo en la primera `i`). Clave de puntos: **`tapa`**, valor **1,00**.
- Texto de la UI: **«Tapa trasera»** (nombre de la fila, categoría del historial y su filtro «Pieza»).
- Orden de grupos del servidor: **`bat, cha, tapa, g, mc, lcd`** y después el resto en orden alfabético (`cam`, `otro`).
- Modelos con tapa: **14, 14 Plus, 15, 15 Plus, 15 Pro, 15 Pro Max, 16, 16e, 16 Plus, 16 Pro, 16 Pro Max, 17, Air, 17 Pro, 17 Pro Max** → **65** tapas, stock **0**, mínimo **2**, activas, sin grupo compartido.
- Fila de un tipo **sin ningún SKU** (ni activo ni desactivado) del modelo elegido: **no se pinta**. Con SKU del modelo pero todos desactivados: se pinta atenuada (como hoy). Sin modelo: todas. Una fila con estado propio (editada, ya reparada, guardada, solicitud, agotado, «✓ Recibido») **nunca** se oculta.
- Servidor, comandos Maven (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y luego `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro de `gestion-reparaciones-servidor`.
- Web: `npx vitest run <ruta>` y `npx tsc -b` dentro de `gestion-reparaciones-web`.

---

### Task 1: Servidor — puntos de la tapa y script de datos

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java:18-27` (`PREFIJO_CLAVE`)
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql:238-246` (`INSERT INTO Dificultad_puntos`)
- Create: `gestion-reparaciones-servidor/sql/datos-tapa-trasera.sql`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java`

**Interfaces:**
- Produces: `PuntosCalculo.claveDeTipo("tapa…")` = `"tapa"`; una pieza `tapa…` puntúa `valores.get("tapa")`.

- [ ] **Step 1: Rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git switch main && git pull && git switch -c feature/tapa-trasera
```

- [ ] **Step 2: Tests que fallan**

En `PuntosCalculoTest.java`, justo después del test `mayusculasNoImportan()`, añadir:

```java
    @Test void tapaTraseraTieneSuClave() {
        assertEquals("tapa", PuntosCalculo.claveDeTipo("tapai15black"));
        assertEquals("tapa", PuntosCalculo.claveDeTipo("tapai16promaxdeserttitanium"));
    }

    @Test void unaTapaPuntuaSuValorNoElDeOtro() {
        Map<String, Double> valores = new java.util.HashMap<>(VALORES);
        valores.put("tapa", 1.00);
        double p = PuntosCalculo.puntosDeReparacion("R20261009_1",
                List.of(new PuntosCalculo.Pieza("tapai15black", 1)), valores);
        assertEquals(1.00, p, 0.001); // con 'otro' serían 0,50
    }
```

- [ ] **Step 3: Comprobar que fallan**

Run: `mvn -q test -Dtest=PuntosCalculoTest`
Expected: FAIL en los dos tests nuevos (`expected: <tapa> but was: <otro>` y `expected: <1.0> but was: <0.5>`).

- [ ] **Step 4: Implementación**

En `PuntosCalculo.java`, el bloque `static` queda:

```java
    static {
        PREFIJO_CLAVE.put("bat", "bateria");
        PREFIJO_CLAVE.put("cha", "chasis");
        PREFIJO_CLAVE.put("tapa", "tapa");
        PREFIJO_CLAVE.put("cam", "camara");
        PREFIJO_CLAVE.put("lcd", "pantalla");
        PREFIJO_CLAVE.put("mc",  "marco");
        PREFIJO_CLAVE.put("g",   "glass");
    }
```

En `crear_bd.sql`, el `INSERT` de `Dificultad_puntos` queda:

```sql
INSERT INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('bateria',  1.00),
    ('camara',   0.70),
    ('chasis',   2.00),
    ('marco',    0.50),
    ('pantalla', 1.00),
    ('tapa',     1.00),
    ('glass',    0.50),
    ('otro',     0.50),
    ('pulido',   0.25);
```

Crear `sql/datos-tapa-trasera.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- datos-tapa-trasera.sql — tapa trasera como pieza propia (spec 2026-10-09 v0.9.7 §6)
-- La aplica el usuario a mano DESPUÉS de desplegar servidor y web 0.9.7 (con el código
-- viejo la fila saldría como "tapa", al final y puntuando como "otro"). Idempotente.
-- Las tapas se generan desde los chasis activos sin eSIM de la base donde se ejecuta:
-- mismo nombre con 'tapa' en vez de 'cha' (chai16black → tapai16black).
-- Modelos: 14, 14 Plus, series 15, 16 (con 16e) y 17 (con Air). Sin 14 Pro / 14 Pro Max.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Vista previa (no modifica nada): chasis de los que saldrá una tapa. Con el catálogo del 2026-10-09: 65.
SELECT COUNT(*) AS chasis_origen FROM Componente
 WHERE TIPO LIKE 'chai%' AND TIPO NOT LIKE '%esim' AND ACTIVO = 1
   AND TIPO REGEXP '^chai(14|15|16|17|air)' AND TIPO NOT REGEXP '^chai14pro';

-- Puntos de la tapa (antes que las tapas: sin esta fila puntuarían 0)
INSERT IGNORE INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES ('tapa', 1.00);

-- Una tapa por chasis: stock 0, mínimo 2, activa, sin grupo compartido
INSERT INTO Componente (TIPO, STOCK, STOCK_MINIMO, ACTIVO)
SELECT CONCAT('tapa', SUBSTRING(c.TIPO, 4)), 0, 2, 1
  FROM Componente c
 WHERE c.TIPO LIKE 'chai%' AND c.TIPO NOT LIKE '%esim' AND c.ACTIVO = 1
   AND c.TIPO REGEXP '^chai(14|15|16|17|air)' AND c.TIPO NOT REGEXP '^chai14pro'
   AND NOT EXISTS (SELECT 1 FROM Componente t WHERE t.TIPO = CONCAT('tapa', SUBSTRING(c.TIPO, 4)));

-- Comprobación: puntos_tapa 1.00; tapas = chasis_origen, stock 0, mínimos 2 y 2, todas activas
SELECT PUNTOS AS puntos_tapa FROM Dificultad_puntos WHERE CLAVE = 'tapa';
SELECT COUNT(*) AS tapas, SUM(STOCK) AS stock, MIN(STOCK_MINIMO) AS min_minimo, MAX(STOCK_MINIMO) AS max_minimo,
       SUM(ACTIVO) AS activas
  FROM Componente WHERE TIPO LIKE 'tapai%';
```

- [ ] **Step 5: Comprobar que pasa**

Run: `mvn -q test -Dtest=PuntosCalculoTest`
Expected: PASS (todos, también los anteriores).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/util/PuntosCalculo.java src/test/java/com/reparaciones/servidor/util/PuntosCalculoTest.java sql/crear_bd.sql sql/datos-tapa-trasera.sql
git commit -m "feat: tapa trasera con su clave de puntos y script de datos con las tapas"
```

---

### Task 2: Servidor — la tapa justo después del chasis en los grupos

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java:144` (lista `orden` de `getAgrupadosPorTipo`)
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ComponenteDAOAgrupadosTest.java`

**Interfaces:**
- Produces: `GET /api/componentes/agrupados` con claves en el orden `bat, cha, tapa, g, mc, lcd, …resto alfabético`. La web pinta las filas en ese orden (`prefijosDeFila`).

- [ ] **Step 1: Test que falla**

Crear `ComponenteDAOAgrupadosTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Componente;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orden de los grupos del formulario (spec 0.9.7 §4.2): la tapa trasera justo después del chasis. */
class ComponenteDAOAgrupadosTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComponenteDAO dao = new ComponenteDAO(jdbc);

    private static Componente c(int id, String tipo) {
        LocalDateTime t = LocalDateTime.of(2026, 10, 9, 10, 0);
        return new Componente(id, tipo, t, 0, 2, true, t);
    }

    @Test @SuppressWarnings("unchecked")
    void laTapaVaJustoDespuesDelChasis() {
        // La base los devuelve en ORDER BY TIPO.
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                c(1, "bati15"), c(2, "cami15"), c(3, "chai15black"), c(4, "gi15negra"), c(5, "lcdi15negraic"),
                c(6, "mci15negra"), c(7, "otroi15"), c(8, "tapai15black")));
        assertEquals(List.of("bat", "cha", "tapa", "g", "mc", "lcd", "cam", "otro"),
                List.copyOf(dao.getAgrupadosPorTipo().keySet()));
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `mvn -q test -Dtest=ComponenteDAOAgrupadosTest`
Expected: FAIL — `expected: <[bat, cha, tapa, g, mc, lcd, cam, otro]> but was: <[bat, cha, g, mc, lcd, cam, otro, tapa]>`.

- [ ] **Step 3: Implementación**

En `ComponenteDAO.getAgrupadosPorTipo()`:

```java
        List<String> orden = List.of("bat", "cha", "tapa", "g", "mc", "lcd");
```

- [ ] **Step 4: Comprobar que pasa, y la suite entera**

Run: `mvn -q test -Dtest=ComponenteDAOAgrupadosTest` → Expected: PASS.
Run: `mvn -q test` → Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java src/test/java/com/reparaciones/servidor/dao/ComponenteDAOAgrupadosTest.java
git commit -m "feat: la tapa trasera va justo despues del chasis en los grupos del formulario"
```

---

### Task 3: Web — tipo «Tapa trasera»

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/lib/piezas.ts:4-8` (`PREFIJOS`, `ETIQUETAS`) y `:24` (`NOMBRES_TIPO`)
- Test: `gestion-reparaciones-web/src/modules/taller/lib/piezas.test.ts`

**Interfaces:**
- Produces: `categoriaPieza('tapai…')` = `'Tapa trasera'`; `nombreTipo('tapa')` = `'Tapa trasera'`. `prefijosDeFila` sin cambios (la tapa cae en Reparación y nunca en Glass).

- [ ] **Step 1: Rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git switch main && git pull && git switch -c feature/tapa-trasera
```

- [ ] **Step 2: Tests que fallan**

En `piezas.test.ts`:

1. En `describe('categoriaPieza …')`, dentro de `it('deriva la categoría del prefijo del SKU', …)`, añadir al final:

```ts
    expect(categoriaPieza('tapai15black')).toBe('Tapa trasera')
```

2. Sustituir el test `it('traduce los seis tipos conocidos', …)` por:

```ts
  it('traduce los siete tipos conocidos', () => {
    expect(nombreTipo('bat')).toBe('Batería')
    expect(nombreTipo('cha')).toBe('Chasis')
    expect(nombreTipo('tapa')).toBe('Tapa trasera')
    expect(nombreTipo('g')).toBe('Glass')
    expect(nombreTipo('cam')).toBe('Cámara')
    expect(nombreTipo('lcd')).toBe('Pantalla')
    expect(nombreTipo('mc')).toBe('Marco')
  })
```

3. En `describe('prefijosDeFila …')`, añadir:

```ts
  it('la tapa trasera es fila de reparación, en el orden del servidor, y nunca de glass', () => {
    const base = agrupados()
    const a = {
      bat: base.bat, cha: base.cha, tapa: [componente({ idCom: 181, tipo: 'tapai13black' })],
      g: base.g, mc: base.mc, lcd: base.lcd, cam: base.cam, otro: base.otro,
    }
    expect(prefijosDeFila(a, false)).toEqual(['bat', 'cha', 'tapa', 'lcd', 'cam'])
    expect(prefijosDeFila(a, true)).toEqual(['g', 'mc'])
  })
```

- [ ] **Step 3: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/lib/piezas.test.ts`
Expected: FAIL en `deriva la categoría…` (`expected '' to be 'Tapa trasera'`) y en `traduce los siete…` (`expected 'tapa' to be 'Tapa trasera'`). El de `prefijosDeFila` ya pasa (no hay que tocar esa función).

- [ ] **Step 4: Implementación**

En `piezas.ts`:

```ts
/** Calco de Piezas: categoría legible a partir del prefijo del SKU (prefijos largos antes para no confundir cha/cam con g). */
const PREFIJOS = ['otro', 'tapa', 'cha', 'cam', 'bat', 'lcd', 'mc', 'g'] as const
const ETIQUETAS: Record<(typeof PREFIJOS)[number], string> = {
  bat: 'Batería', cha: 'Chasis', tapa: 'Tapa trasera', g: 'Glass', cam: 'Cámara', lcd: 'Pantalla', mc: 'Marco', otro: 'Otros',
}
```

y

```ts
const NOMBRES_TIPO: Record<string, string> = {
  bat: 'Batería', cha: 'Chasis', tapa: 'Tapa trasera', g: 'Glass', cam: 'Cámara', lcd: 'Pantalla', mc: 'Marco',
}
```

- [ ] **Step 5: Comprobar que pasa**

Run: `npx vitest run src/modules/taller/lib && npx tsc -b`
Expected: PASS y tsc sin errores.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/lib/piezas.ts src/modules/taller/lib/piezas.test.ts
git commit -m "feat: tipo tapa trasera en el formulario y en la categoria del historial"
```

---

### Task 4: Web — no pintar las filas de tipos que no existen para el modelo

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/formulario/estado.ts` (tipo `EstadoFormulario`, `estadoBase`, selector nuevo `filaVisible` tras `filaSinSku`)
- Modify: `gestion-reparaciones-web/src/modules/taller/formulario/FormularioReparacion.tsx:13` (import) y `:169` (render de filas)
- Test: `gestion-reparaciones-web/src/modules/taller/formulario/estado.edicion.test.ts`
- Test: `gestion-reparaciones-web/src/modules/taller/formulario/FormularioReparacion.test.tsx`

**Interfaces:**
- Consumes: `extraerModelo(sku, prefijo)` de `../lib/modelos` (ya importado en `estado.ts`).
- Produces: `EstadoFormulario.modelosPorTipo: Record<string, string[]>`; `filaVisible(e: EstadoFormulario, fila: FilaEstado): boolean`.

- [ ] **Step 1: Tests que fallan (estado)**

En `estado.edicion.test.ts`, añadir `filaSinSku` y `filaVisible` a la lista de imports de `'./estado'` y, al final del fichero:

```ts
describe('filas de tipos que no existen para el modelo (spec 0.9.7 §5.2)', () => {
  const visibles = (e: EstadoFormulario) => e.filas.filter((f) => filaVisible(e, f)).map((f) => f.prefijo)

  it('sin modelo se pintan todas', () => {
    expect(visibles(estadoInicial(datosNuevo()))).toEqual(['bat', 'cha', 'lcd', 'cam'])
  })

  it('con modelo, un tipo sin ningún SKU de ese modelo no se pinta', () => {
    // agrupados(): no hay chasis ni cámara del 14
    expect(visibles(conModelo('14'))).toEqual(['bat', 'lcd'])
    expect(visibles(conModelo('13'))).toEqual(['bat', 'cha', 'lcd', 'cam'])
  })

  it('un tipo con SKU del modelo pero todos desactivados se pinta, atenuado', () => {
    const a = agrupados()
    a.cam = [componente({ idCom: 121, tipo: 'cami13', activo: false })]
    const e = conModelo('13', { agrupados: a })
    expect(visibles(e)).toEqual(['bat', 'cha', 'lcd', 'cam'])
    expect(filaSinSku(fila(e, 'cam'))).toBe(true)
  })

  it('una fila con estado propio no se oculta aunque su tipo no exista para el modelo', () => {
    // Edición de lcdi14 (modelo 14) en un IMEI con la cámara cami13 ya reparada: no hay cámara del 14, pero es "ya reparada".
    const e = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 112 }), yaReparados: [121] }))
    expect(e.modelo).toBe('14')
    expect(fila(e, 'cam').rol).toBe('yaReparado')
    expect(visibles(e)).toEqual(['bat', 'lcd', 'cam'])
  })
})
```

- [ ] **Step 2: Test que falla (interfaz)**

En `FormularioReparacion.test.tsx`, dentro de `describe('FormularioReparacion · cabecera, avisos y cierre …')`, tras el test `'elegir modelo muestra las filas (data-testid fila-*) en el orden del servidor, sin glass, marco ni otro'`, añadir:

```tsx
  it('con el modelo elegido no se pintan los tipos sin ningún SKU de ese modelo (spec 0.9.7 §5.2)', async () => {
    abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 14')
    // agrupados(): no hay chasis ni cámara del 14
    expect(screen.getAllByTestId(/^fila-/).map((f) => f.getAttribute('data-testid'))).toEqual(['fila-bat', 'fila-lcd'])
  })
```

- [ ] **Step 3: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/formulario/estado.edicion.test.ts src/modules/taller/formulario/FormularioReparacion.test.tsx`
Expected: FAIL — `filaVisible` no existe (error de import en `estado.edicion.test.ts`) y el test de interfaz recibe `['fila-bat', 'fila-cha', 'fila-lcd', 'fila-cam']`.

- [ ] **Step 4: Implementación (estado)**

En `estado.ts`:

1. En el tipo `EstadoFormulario`, justo después de `filas: FilaEstado[]`:

```ts
  /** Por tipo de fila, los modelos con algún SKU del tipo, ACTIVO O NO (spec 0.9.7 §5.2): un tipo sin ningún SKU del modelo
   *  elegido no se pinta (filaVisible). */
  modelosPorTipo: Record<string, string[]>
```

2. Justo antes de `function estadoBase`:

```ts
/** Modelos con algún SKU (activo o no) de cada tipo de fila, sin repetir. */
function modelosDeCadaTipo(agrupados: ComponentesAgrupados, prefijos: string[]): Record<string, string[]> {
  const mapa: Record<string, string[]> = {}
  for (const prefijo of prefijos) {
    const modelos = (agrupados[prefijo] ?? []).map((c) => extraerModelo(c.tipo, prefijo)).filter((m): m is string => m !== null)
    mapa[prefijo] = [...new Set(modelos)]
  }
  return mapa
}
```

3. En el objeto que devuelve `estadoBase`, justo después de `filas: bases.map((b) => filaLimpia(b, null)),`:

```ts
    modelosPorTipo: modelosDeCadaTipo(d.agrupados, bases.map((b) => b.prefijo)),
```

4. Justo después de la función `filaSinSku`:

```ts
/** La fila se pinta (spec 0.9.7 §5.2). Sin modelo, todas. Con modelo: si tiene SKU activos del modelo; si trae estado propio
 *  (editada, ya reparada, guardada, solicitud, agotado o "✓ Recibido"), siempre; si no, solo si el tipo tiene algún SKU
 *  DESACTIVADO del modelo (sale atenuada, como filaSinSku). Un tipo que no existe para el modelo no se pinta. Solo es
 *  presentación: la fila sigue en el estado, así que borrador, guardado y edición no cambian. */
export function filaVisible(e: EstadoFormulario, fila: FilaEstado): boolean {
  if (e.modelo === null || fila.opciones.length > 0) return true
  if (fila.rol !== 'normal' || fila.guardada !== null || fila.solicitud !== null || fila.agotado !== null || fila.recibidoPendienteUso) return true
  return (e.modelosPorTipo[fila.prefijo] ?? []).includes(e.modelo)
}
```

- [ ] **Step 5: Implementación (interfaz)**

En `FormularioReparacion.tsx`:

1. El import de `./estado` queda:

```ts
import { estadoInicial, filaVisible, filasVisibles, hayCambiosSinGuardar, reducir, textoConflicto, tituloPestana, type DatosEditar, type DatosNuevo } from './estado'
```

2. En el cuerpo, `estado.filas.map((fila) => (` pasa a:

```tsx
              estado.filas.filter((fila) => filaVisible(estado, fila)).map((fila) => (
```

- [ ] **Step 6: Comprobar que pasa, y todo el formulario**

Run: `npx vitest run src/modules/taller/formulario && npx tsc -b`
Expected: PASS (también los tests anteriores: usan el modelo 13, que tiene todos los tipos) y tsc sin errores.

- [ ] **Step 7: Commit**

```bash
git add src/modules/taller/formulario/estado.ts src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/estado.edicion.test.ts src/modules/taller/formulario/FormularioReparacion.test.tsx
git commit -m "feat: el formulario no pinta las filas de tipos que no existen para el modelo"
```

---

### Task 5: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json` y `gestion-reparaciones-web/package-lock.json` (versión)
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.7]`)
- Create: `docs/novedades/NOVEDADES-v0.9.7.md` (raíz)

- [ ] **Step 1: Versión 0.9.7 de la web**

En `package.json`, `"version": "0.9.6"` → `"version": "0.9.7"`. En `package-lock.json`, las dos primeras apariciones de `"version": "0.9.6"` (raíz y `packages[""]`) → `"0.9.7"`.

Run: `grep -n '"version": "0.9.7"' package.json package-lock.json`
Expected: 3 líneas (una en `package.json`, dos en `package-lock.json`).

- [ ] **Step 2: CHANGELOG de la web**

Insertar tras la línea `El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).` y su línea en blanco:

```markdown
## [0.9.7] - 2026-10-09 — Tapa trasera

- **Tapa trasera como pieza propia.** Nueva fila **"Tapa trasera"** en el formulario de reparación, justo después de Chasis, con un SKU por modelo y color (los mismos colores que el chasis) para el 14, el 14 Plus, toda la serie 15, toda la serie 16, el 17, el Air, el 17 Pro y el 17 Pro Max. Descuenta stock y sale en Stock, en la previsión, en los pedidos y en el filtro "Pieza" del historial como el resto de piezas. Puntúa 1 punto en las estadísticas (hasta ahora se apuntaba como "otro" y valía 0,5).
- **Formulario más limpio:** con el modelo elegido, la fila de un tipo de pieza que no existe para ese modelo ya no se pinta (por ejemplo, "Tapa trasera" en un iPhone 13). Si el tipo existe pero todos sus SKU de ese modelo están desactivados, la fila sigue saliendo en gris.
- Requiere el servidor 0.9.7 y el script `datos-tapa-trasera.sql` (puntos de la tapa y las tapas), que se aplica después de desplegar. Sin cambios en el esquema de la base ni en nginx.

```

- [ ] **Step 3: Novedades para el taller (raíz)**

Crear `docs/novedades/NOVEDADES-v0.9.7.md`:

```markdown
# 🎉 Novedades — Versión 0.9.7 (web)

La **tapa trasera** deja de apuntarse como «otro»: ahora es una pieza más, con su stock y su fila en el formulario.

---

## 🔧 Formulario de reparación: fila «Tapa trasera»

- Sale **justo debajo de Chasis**. Elige la tapa del **mismo color que el chasis** del teléfono, súmala con «+» y
  guarda como cualquier otra pieza: el stock se descuenta solo.
- Existe para el **14, 14 Plus, toda la serie 15, toda la serie 16, el 17, el Air, el 17 Pro y el 17 Pro Max**.
  En el resto (series 12 y 13, 14 Pro y 14 Pro Max) la tapa va pegada al chasis y se cambia el chasis.
- Cuenta **1 punto** en las estadísticas (como «otro» contaba 0,5).

---

## 🧹 Filas que no aplican, fuera

Al elegir el modelo, el formulario **ya no enseña las filas de piezas que ese modelo no tiene** (por ejemplo,
«Tapa trasera» en un iPhone 13). Una fila **en gris** significa que la pieza existe para ese modelo pero está
desactivada.

---

## 📦 Stock

Las tapas aparecen en **Stock** con stock 0 y mínimo 2 hasta que se cuenten: salen como «Sin stock» y entran en la
previsión de pedidos como el resto de piezas.
```

- [ ] **Step 4: Suites completas**

Run (servidor): `mvn -q test` → Expected: exit 0.
Run (web): `npx tsc -b && npx vitest run` → Expected: exit 0, todos los ficheros en verde.
Run (web): `npm run lint` → Expected: sin errores.

- [ ] **Step 5: Commits de versión y documentación**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git add package.json package-lock.json CHANGELOG.md && git commit -m "chore: version 0.9.7 con la tapa trasera"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.7.md && git commit -m "docs: novedades de la 0.9.7"
```

- [ ] **Step 6: Entrega (con OK del usuario en cada paso)**

Pedir OK antes de cada uno:
1. Merge `--no-ff` de `feature/tapa-trasera` a `main` en servidor y web (`merge: tapa trasera como pieza propia (0.9.7)` y `merge: tapa trasera y filas de tipos inexistentes ocultas (0.9.7)`), borrar las ramas.
2. Push de `main` en los dos repos.
3. Commit de gitlinks en la raíz (`chore: gitlinks servidor y web tras la 0.9.7 (tapa trasera)`).
4. Preprod (la base ya está refrescada el 2026-10-09 con el catálogo saneado): guion `Apuntes/preprod/v097-preprod.md` con el mismo esquema que `v096-preprod.md` — bloque 1 de estado (clones en `main`, contenedores `Up`, `SELECT COUNT(*) FROM Componente WHERE TIPO LIKE 'tapai%'` = **0**); bloque 2: `git pull --ff-only` de servidor y web, `docker compose up -d --build`, `Started App` sin errores, y **después** `datos-tapa-trasera.sql` (`chasis_origen` **65**, `puntos_tapa` **1.00**, `tapas` **65**, stock **0**, mínimos **2/2**, activas **65**) y `init.sql` re-volcado (3 hashes, 17 anuladas).
5. Comprobaciones en preprod: Claude por la API (`/version.json` = 0.9.7; `/api/componentes/agrupados` con claves `bat, cha, tapa, g, mc, lcd, cam, otro` y 65 `tapai…`); el usuario a mano: un 15 Pro muestra «Tapa trasera» tras Chasis con sus 4 colores; un 13 no muestra la fila; Stock lista las tapas en «Sin stock» con mínimo 2; guardar una reparación de prueba con tapa descuenta stock (y deshacerlo).
6. Producción en horario (corte despreciable, sin cambio de esquema; ver `feedback_despliegue_en_horario`): copia a mano antes (`backup-erp.sh`), mismo bloque 2 y el SQL de datos; tags `v0.9.7` en servidor y web **solo cuando el usuario lo diga**; registro en `Apuntes/despliegue_vdc_produccion.md`.
