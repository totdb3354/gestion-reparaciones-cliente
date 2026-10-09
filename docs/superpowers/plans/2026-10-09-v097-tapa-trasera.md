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

---

## Añadido: selector de chasis y tapa trasera (spec §9, 2026-10-09)

Tareas 6 a 10, **solo web**, en la rama **`feature/selector-color`** creada desde `main` de la web (`bcaa321`). La
versión sigue siendo **0.9.7** (ya en `package.json`). Servidor y datos no cambian.

### Global Constraints del añadido

- Filas afectadas: solo **Chasis** (`cha`) y **Tapa trasera** (`tapa`). El resto de filas se preselecciona como hoy.
- Sin SKU elegido: botón **«— Elige color —»**; «+» y «Reutilizado» **desactivados**; Stock «—»; **sin** sub-fila de
  «Sin stock». Al cambiar de modelo, chasis y tapa vuelven a sin elegir (salvo filas bloqueadas).
- Lista: **círculo** de color (`size-3`) con **borde fino semitransparente en todos** (`border-black/25`) y el **nombre
  oficial** legible; SKU completo en el `title`. Botón con SKU elegido: círculo + **SKU completo**.
- Chasis: bloques **«SIM»** y después **«eSIM»**, solo si el modelo tiene de los dos; tapa sin bloques.
- Enlace: chasis → tapa del **mismo color**; tapa → chasis del mismo color **conservando su SIM/eSIM** si el chasis ya
  estaba elegido; si no, el chasis **sigue sin elegir** y se **resaltan** las opciones del color de la tapa. El enlace
  solo cambia el SKU, nunca cantidad ni «Reutilizado», y nunca toca una fila **en edición o ya reparada**
  (`rol !== 'normal'`), bloqueada o con solicitud.
- Al abrir, si el chasis trae SKU (edición, solicitud, rechazada) y la tapa no, la tapa toma su color **sin subir
  `revision`** (abrir no reprograma el autoguardado).
- Borrador: recupera lo elegido; una fila de chasis/tapa sin SKU recuperable no recupera cantidad ni «Reutilizado».
- `ComboNavy`: campos nuevos **opcionales**; sin ellos se pinta igual que hoy.
- Commits en español sin tildes; **sin** `Co-Authored-By`. Repos públicos: datos de test sintéticos.
- Web: `npx vitest run <ruta>`, `npx tsc -b`, `npm run lint` dentro de `gestion-reparaciones-web`.

---

### Task 6: Web — tabla de colores y lectura del color del SKU

**Files:**
- Create: `gestion-reparaciones-web/src/modules/taller/lib/colores.ts`
- Test: `gestion-reparaciones-web/src/modules/taller/lib/colores.test.ts`

**Interfaces:**
- Consumes: `extraerModelo(sku, prefijo)` de `./modelos`.
- Produces: `PREFIJO_CHASIS = 'cha'`, `PREFIJO_TAPA = 'tapa'`, `PREFIJOS_CON_COLOR: readonly string[]`;
  `type LecturaSku = { modelo: string | null; esim: boolean; token: string }`;
  `type ColorSku = LecturaSku & { nombre: string; tono: string | null }`;
  `leerSku(sku, prefijo): LecturaSku`; `colorDeSku(sku, prefijo): ColorSku`; `mismoColor(a: LecturaSku, b: LecturaSku): boolean`.

- [ ] **Step 1: Rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git switch main && git pull && git switch -c feature/selector-color
```

- [ ] **Step 2: Test que falla**

Crear `src/modules/taller/lib/colores.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { PREFIJOS_CON_COLOR, PREFIJO_CHASIS, PREFIJO_TAPA, colorDeSku, leerSku, mismoColor } from './colores'

describe('leerSku (modelo, eSIM y token de color)', () => {
  it('chasis con eSIM, tapa sin ella', () => {
    expect(leerSku('chai16ultramarineesim', 'cha')).toEqual({ modelo: '16', esim: true, token: 'ultramarine' })
    expect(leerSku('tapai16ultramarine', 'tapa')).toEqual({ modelo: '16', esim: false, token: 'ultramarine' })
  })
  it('gana el modelo más largo y respeta las variantes', () => {
    expect(leerSku('chai16eblackesim', 'cha')).toEqual({ modelo: '16e', esim: true, token: 'black' })
    expect(leerSku('chai17promaxcosmicorange', 'cha')).toEqual({ modelo: '17promax', esim: false, token: 'cosmicorange' })
    expect(leerSku('chaiaircloudwhite', 'cha')).toEqual({ modelo: 'air', esim: false, token: 'cloudwhite' })
    expect(leerSku('tapai15problacktitanium', 'tapa')).toEqual({ modelo: '15pro', esim: false, token: 'blacktitanium' })
  })
})

describe('colorDeSku (nombre oficial y tono)', () => {
  it('nombre oficial legible', () => {
    expect(colorDeSku('chai16ultramarine', 'cha').nombre).toBe('Ultramarine')
    expect(colorDeSku('chai15problacktitanium', 'cha').nombre).toBe('Black Titanium')
    expect(colorDeSku('chaiaircloudwhite', 'cha').nombre).toBe('Cloud White')
    expect(colorDeSku('chai12red', 'cha').nombre).toBe('(PRODUCT)RED')
  })
  it('tono propio por modelo cuando Apple repite el nombre con otro tono; si no, el tono base', () => {
    expect(colorDeSku('chai15blue', 'cha').tono).toBe('#D3E0EA')
    expect(colorDeSku('chai12blue', 'cha').tono).toBe('#11416B')
    expect(colorDeSku('chai12problue', 'cha').tono).toBe('#2C5A84')
  })
  it('un color que no está en la tabla: el token como nombre y sin tono', () => {
    expect(colorDeSku('chai16fucsia', 'cha')).toMatchObject({ nombre: 'fucsia', tono: null, modelo: '16' })
  })
  it('conoce todos los colores de los modelos activos (series 12 a 17)', () => {
    const tokens = [
      'black', 'white', 'red', 'blue', 'green', 'purple', 'pink', 'yellow', 'midnight', 'starlight', 'gold', 'graphite',
      'silver', 'pacificblue', 'sierrablue', 'alpinegreen', 'deeppurple', 'spaceblack', 'blacktitanium', 'bluetitanium',
      'naturaltitanium', 'whitetitanium', 'deserttitanium', 'teal', 'ultramarine', 'sage', 'mistblue', 'lavender',
      'cloudwhite', 'lightgold', 'skyblue', 'deepblue', 'cosmicorange',
    ]
    for (const t of tokens) expect(colorDeSku(`chai16${t}`, 'cha').tono, t).not.toBeNull()
  })
})

describe('mismoColor y constantes', () => {
  it('mismo modelo y mismo token, sin mirar SIM/eSIM', () => {
    expect(mismoColor(leerSku('chai16tealesim', 'cha'), leerSku('tapai16teal', 'tapa'))).toBe(true)
    expect(mismoColor(leerSku('chai16teal', 'cha'), leerSku('tapai16plusteal', 'tapa'))).toBe(false)
    expect(mismoColor(leerSku('chai16teal', 'cha'), leerSku('tapai16pink', 'tapa'))).toBe(false)
  })
  it('prefijos con color', () => {
    expect(PREFIJO_CHASIS).toBe('cha')
    expect(PREFIJO_TAPA).toBe('tapa')
    expect(PREFIJOS_CON_COLOR).toEqual(['cha', 'tapa'])
  })
})
```

- [ ] **Step 3: Comprobar que falla**

Run: `npx vitest run src/modules/taller/lib/colores.test.ts`
Expected: FAIL (no existe `./colores`).

- [ ] **Step 4: Implementación**

Crear `src/modules/taller/lib/colores.ts`:

```ts
import { extraerModelo } from './modelos'

/** Tipos de pieza que van por color (spec 0.9.7 §9): sin SKU preseleccionado, con muestra de color y enlazados entre sí. */
export const PREFIJO_CHASIS = 'cha'
export const PREFIJO_TAPA = 'tapa'
export const PREFIJOS_CON_COLOR: readonly string[] = [PREFIJO_CHASIS, PREFIJO_TAPA]

export type LecturaSku = { modelo: string | null; esim: boolean; token: string }
export type ColorSku = LecturaSku & { nombre: string; tono: string | null }

type Color = { nombre: string; tono: string; porModelo?: Record<string, string> }

/** Nombre oficial de Apple y tono aproximado de cada color de SKU (spec 0.9.7 §9.2). Solo orientativo: sirve para
 *  distinguir los colores de un mismo modelo. `porModelo` corrige los nombres que Apple repite con otro tono. */
const COLORES = new Map<string, Color>(Object.entries({
  black: { nombre: 'Black', tono: '#232426', porModelo: { '15': '#3B3D3F', '15plus': '#3B3D3F', '16': '#3C3C3E', '16plus': '#3C3C3E', '16e': '#3C3C3E' } },
  white: { nombre: 'White', tono: '#F4F4F0' },
  red: { nombre: '(PRODUCT)RED', tono: '#BF0013' },
  blue: { nombre: 'Blue', tono: '#2C5A84', porModelo: { '12': '#11416B', '12mini': '#11416B', '13': '#2F6585', '13mini': '#2F6585', '14': '#A0B4C7', '14plus': '#A0B4C7', '15': '#D3E0EA', '15plus': '#D3E0EA' } },
  green: { nombre: 'Green', tono: '#4E6B4F', porModelo: { '12': '#D8EFD5', '12mini': '#D8EFD5', '13': '#394C38', '13mini': '#394C38', '15': '#D0DCC9', '15plus': '#D0DCC9' } },
  purple: { nombre: 'Purple', tono: '#B9AEDC', porModelo: { '14': '#E3DAEA', '14plus': '#E3DAEA' } },
  pink: { nombre: 'Pink', tono: '#F4C7D4', porModelo: { '13': '#F9E0DA', '13mini': '#F9E0DA', '15': '#F6D7DC', '15plus': '#F6D7DC', '16': '#F0A6CF', '16plus': '#F0A6CF' } },
  yellow: { nombre: 'Yellow', tono: '#F6E58D', porModelo: { '15': '#F2E9C4', '15plus': '#F2E9C4' } },
  midnight: { nombre: 'Midnight', tono: '#232A31' },
  starlight: { nombre: 'Starlight', tono: '#F7F1E7' },
  gold: { nombre: 'Gold', tono: '#F3E2C7' },
  graphite: { nombre: 'Graphite', tono: '#54524F' },
  silver: { nombre: 'Silver', tono: '#E3E4E3' },
  pacificblue: { nombre: 'Pacific Blue', tono: '#2E4A5C' },
  sierrablue: { nombre: 'Sierra Blue', tono: '#A7C1D9' },
  alpinegreen: { nombre: 'Alpine Green', tono: '#576856' },
  deeppurple: { nombre: 'Deep Purple', tono: '#594F63' },
  spaceblack: { nombre: 'Space Black', tono: '#3B3A39', porModelo: { air: '#1E1E20' } },
  blacktitanium: { nombre: 'Black Titanium', tono: '#3C3C3D' },
  bluetitanium: { nombre: 'Blue Titanium', tono: '#3D4555' },
  naturaltitanium: { nombre: 'Natural Titanium', tono: '#BAB4A9' },
  whitetitanium: { nombre: 'White Titanium', tono: '#F2F1ED' },
  deserttitanium: { nombre: 'Desert Titanium', tono: '#BFA48F' },
  teal: { nombre: 'Teal', tono: '#B0D4D2' },
  ultramarine: { nombre: 'Ultramarine', tono: '#9AADF6' },
  sage: { nombre: 'Sage', tono: '#A9B693' },
  mistblue: { nombre: 'Mist Blue', tono: '#9DB3D1' },
  lavender: { nombre: 'Lavender', tono: '#DCCBE8' },
  cloudwhite: { nombre: 'Cloud White', tono: '#F5F5F2' },
  lightgold: { nombre: 'Light Gold', tono: '#E6D5B5' },
  skyblue: { nombre: 'Sky Blue', tono: '#C5DCEF' },
  deepblue: { nombre: 'Deep Blue', tono: '#2F3B57' },
  cosmicorange: { nombre: 'Cosmic Orange', tono: '#F2782F' },
} satisfies Record<string, Color>))

/** Modelo, variante eSIM y token de color de un SKU de chasis o tapa: lo que queda entre el modelo y un `esim` final
 *  (`chai16ultramarineesim` → 16, eSIM, `ultramarine`). */
export function leerSku(sku: string, prefijo: string): LecturaSku {
  const modelo = extraerModelo(sku, prefijo)
  let resto = sku.toLowerCase().slice(prefijo.length)
  if (resto.startsWith('i')) resto = resto.slice(1)
  if (modelo !== null) resto = resto.slice(modelo.length)
  const esim = resto.endsWith('esim')
  return { modelo, esim, token: esim ? resto.slice(0, -'esim'.length) : resto }
}

/** Nombre oficial y tono del color de un SKU. Un color que no está en la tabla devuelve el token como nombre y tono
 *  null (el combo lo pinta con un círculo gris discontinuo). */
export function colorDeSku(sku: string, prefijo: string): ColorSku {
  const lectura = leerSku(sku, prefijo)
  const color = COLORES.get(lectura.token)
  if (color === undefined) return { ...lectura, nombre: lectura.token || sku, tono: null }
  const tono = (lectura.modelo !== null ? color.porModelo?.[lectura.modelo] : undefined) ?? color.tono
  return { ...lectura, nombre: color.nombre, tono }
}

/** Chasis y tapa «coinciden»: mismo modelo y mismo color (sin mirar SIM/eSIM). */
export function mismoColor(a: LecturaSku, b: LecturaSku): boolean {
  return a.modelo !== null && a.modelo === b.modelo && a.token === b.token
}
```

- [ ] **Step 5: Comprobar que pasa**

Run: `npx vitest run src/modules/taller/lib && npx tsc -b`
Expected: PASS y tsc sin errores.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/lib/colores.ts src/modules/taller/lib/colores.test.ts
git commit -m "feat: tabla de colores y lectura del color del sku de chasis y tapa"
```

---

### Task 7: Web — `ComboNavy` con muestra de color, bloques, resaltado y etiqueta del botón

**Files:**
- Modify: `gestion-reparaciones-web/src/shared/ui/ComboNavy.tsx`
- Test: `gestion-reparaciones-web/src/shared/ui/ComboNavy.test.tsx`

**Interfaces:**
- Produces: `OpcionCombo = { valor: string; etiqueta: string; clase?: string; color?: string | null; grupo?: string;
  resaltada?: boolean; titulo?: string; etiquetaBoton?: string }`. `color`: `string` = círculo de ese tono; `null` =
  círculo gris discontinuo (color desconocido); ausente = sin círculo. `grupo`: título que se pinta antes de la primera
  opción de cada bloque. `resaltada`: anillo verde (`ring-2 ring-verde-ok ring-inset`) y `data-resaltada="true"` en el
  `<li>`. `titulo`: atributo `title` del botón de la opción. `etiquetaBoton`: lo que muestra el botón cerrado si esa
  opción es la elegida (por defecto, `etiqueta`).

- [ ] **Step 1: Tests que fallan**

En `ComboNavy.test.tsx`, dentro de `describe('ComboNavy (combo navy de selección única)', …)`, añadir al final:

```tsx
  it('muestra de color con borde en la lista y en el botón; title y etiqueta propia del botón', async () => {
    const opciones: OpcionCombo[] = [
      { valor: '1', etiqueta: 'Ultramarine', etiquetaBoton: 'chai16ultramarine', titulo: 'chai16ultramarine', color: '#9AADF6' },
      { valor: '2', etiqueta: 'fucsia', titulo: 'chai16fucsia', color: null },
    ]
    render(<ComboNavy valor="1" opciones={opciones} onChange={() => {}} textoVacio="— Elige color —" ancho={170} aria-label="SKU de Chasis" />)
    const combo = screen.getByRole('combobox', { name: 'SKU de Chasis' })
    expect(combo).toHaveTextContent('chai16ultramarine')
    expect(within(combo).getByTestId('muestra-color')).toHaveStyle({ backgroundColor: '#9AADF6' })
    await userEvent.click(combo)
    const lista = screen.getByRole('listbox', { name: 'SKU de Chasis' })
    const ultra = within(lista).getByRole('button', { name: 'Ultramarine' })
    expect(ultra).toHaveAttribute('title', 'chai16ultramarine')
    expect(within(ultra).getByTestId('muestra-color')).toHaveClass('border-black/25')
    const desconocido = within(lista).getByRole('button', { name: 'fucsia' })
    expect(within(desconocido).getByTestId('muestra-color')).toHaveAttribute('data-desconocido', 'true')
  })
  it('bloques con título antes de la primera opción de cada grupo, y opciones resaltadas', async () => {
    const opciones: OpcionCombo[] = [
      { valor: '1', etiqueta: 'Black', grupo: 'SIM', color: '#232426' },
      { valor: '2', etiqueta: 'Teal', grupo: 'SIM', color: '#B0D4D2', resaltada: true },
      { valor: '3', etiqueta: 'Black', grupo: 'eSIM', color: '#232426' },
      { valor: '4', etiqueta: 'Teal', grupo: 'eSIM', color: '#B0D4D2', resaltada: true },
    ]
    render(<ComboNavy valor={null} opciones={opciones} onChange={() => {}} textoVacio="— Elige color —" ancho={170} aria-label="SKU de Chasis" />)
    await userEvent.click(screen.getByRole('combobox', { name: 'SKU de Chasis' }))
    const lista = screen.getByRole('listbox', { name: 'SKU de Chasis' })
    expect(Array.from(lista.querySelectorAll('li')).map((li) => li.textContent)).toEqual(['SIM', 'Black', 'Teal', 'eSIM', 'Black', 'Teal'])
    expect(within(lista).getAllByRole('option')).toHaveLength(4)
    const resaltadas = within(lista).getAllByRole('option').filter((o) => o.getAttribute('data-resaltada') === 'true')
    expect(resaltadas.map((o) => o.textContent)).toEqual(['Teal', 'Teal'])
    expect(within(resaltadas[0]).getByRole('button')).toHaveClass('ring-verde-ok')
  })
  it('sin los campos nuevos se pinta igual: sin muestras, sin títulos ni resaltado', async () => {
    render(<ComboNavy valor="101" opciones={SKUS} onChange={() => {}} textoVacio="—" ancho={170} aria-label="SKU de Batería" />)
    const combo = screen.getByRole('combobox', { name: 'SKU de Batería' })
    expect(within(combo).queryByTestId('muestra-color')).not.toBeInTheDocument()
    await userEvent.click(combo)
    const lista = screen.getByRole('listbox', { name: 'SKU de Batería' })
    expect(within(lista).queryAllByTestId('muestra-color')).toHaveLength(0)
    expect(lista.querySelectorAll('li')).toHaveLength(3)
    expect(lista.querySelector('[data-resaltada]')).toBeNull()
  })
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/shared/ui/ComboNavy.test.tsx`
Expected: FAIL en los dos primeros tests nuevos (no hay `muestra-color`, títulos de bloque ni `title`); el tercero puede
pasar ya.

- [ ] **Step 3: Implementación**

En `ComboNavy.tsx`:

1. Import de React: `import { Fragment, useEffect, useRef, useState } from 'react'`.
2. El tipo:

```ts
/** `color`: tono del círculo de muestra (null = color desconocido, círculo gris discontinuo; sin el campo, no hay
 *  círculo). `grupo`: título del bloque, que se pinta antes de su primera opción. `resaltada`: anillo verde (p. ej. los
 *  chasis del color de la tapa). `titulo`: `title` de la opción. `etiquetaBoton`: lo que enseña el botón cerrado cuando
 *  esta opción es la elegida (por defecto `etiqueta`). Todos opcionales: sin ellos el combo se pinta como siempre. */
export type OpcionCombo = {
  valor: string
  etiqueta: string
  clase?: string
  color?: string | null
  grupo?: string
  resaltada?: boolean
  titulo?: string
  etiquetaBoton?: string
}
```

3. Antes de `export function ComboNavy`:

```tsx
/** Círculo de color con borde fino en todos (para que blanco, starlight o plata se vean sobre fondo blanco); el color
 *  desconocido, gris y discontinuo. */
function MuestraColor({ color }: { color: string | null }) {
  if (color === null) {
    return <span aria-hidden="true" data-testid="muestra-color" data-desconocido="true" className="inline-block size-3 shrink-0 rounded-full border border-dashed border-gris-borde bg-superficie" />
  }
  return <span aria-hidden="true" data-testid="muestra-color" className="inline-block size-3 shrink-0 rounded-full border border-black/25" style={{ backgroundColor: color }} />
}
```

4. En el `PopoverTrigger`, sustituir `<span className="truncate">{actual ? actual.etiqueta : textoVacio}</span>` por:

```tsx
        <span className="flex min-w-0 items-center gap-1.5">
          {actual?.color !== undefined && <MuestraColor color={actual.color} />}
          <span className="truncate">{actual ? (actual.etiquetaBoton ?? actual.etiqueta) : textoVacio}</span>
        </span>
```

5. Sustituir el `opciones.map(...)` de la lista por:

```tsx
          {opciones.map((o, i) => {
            const activa = o.valor === valor
            const tituloBloque = o.grupo !== undefined && o.grupo !== opciones[i - 1]?.grupo ? o.grupo : null
            return (
              <Fragment key={o.valor}>
                {tituloBloque !== null && (
                  <li role="presentation" className="px-3 pt-1.5 pb-0.5 text-[10px] font-bold tracking-wide text-azul-gris uppercase">
                    {tituloBloque}
                  </li>
                )}
                <li role="option" aria-selected={activa} data-resaltada={o.resaltada ? 'true' : undefined}>
                  <button
                    type="button"
                    title={o.titulo}
                    onClick={() => {
                      cambiarAbierto(false)
                      if (!activa) onChange(o.valor)
                    }}
                    className={cn(
                      'mx-1 flex h-7 w-[calc(100%-8px)] items-center gap-1.5 rounded-lg px-3 text-left font-bold',
                      tamano,
                      activa ? 'bg-azul-noche text-superficie' : cn('hover:bg-seleccion-suave', o.clase || 'text-azul-noche', o.resaltada && 'ring-2 ring-verde-ok ring-inset'),
                    )}
                  >
                    {o.color !== undefined && <MuestraColor color={o.color} />}
                    <span className="truncate">{o.etiqueta}</span>
                  </button>
                </li>
              </Fragment>
            )
          })}
```

- [ ] **Step 4: Comprobar que pasa, y todos los usos de ComboNavy**

Run: `npx vitest run src/shared/ui src/modules && npx tsc -b && npm run lint`
Expected: PASS (los 11 usos actuales no pasan campos nuevos), tsc y lint limpios. Si algún test de otro módulo comprueba
la clase exacta `block`/`truncate` del botón de opción, adaptarlo a `flex …` + `<span class="truncate">` sin cambiar lo
que comprueba, y decirlo en el informe.

- [ ] **Step 5: Commit**

```bash
git add src/shared/ui/ComboNavy.tsx src/shared/ui/ComboNavy.test.tsx
git commit -m "feat: combo navy con muestra de color, bloques, resaltado y etiqueta propia del boton"
```

---

### Task 8: Web — estado del formulario: chasis y tapa sin preselección y enlazados por color

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/taller/formulario/estado.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/formulario/borrador.ts` (`aplicarFila`)
- Test: `gestion-reparaciones-web/src/modules/taller/formulario/estado.color.test.ts` (nuevo)

**Interfaces:**
- Consumes: `PREFIJO_CHASIS`, `PREFIJO_TAPA`, `PREFIJOS_CON_COLOR`, `leerSku`, `mismoColor` de `../lib/colores` (Task 6).
- Produces: `controlesIniciales(c)` pasa a **exportarse**; selector nuevo `chasisResaltados(e: EstadoFormulario): Set<number>`
  (idCom de las opciones del chasis del color de la tapa, solo mientras el chasis está sin elegir).

- [ ] **Step 1: Tests que fallan**

Crear `src/modules/taller/formulario/estado.color.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, componente, detalleEdicion, solicitudAsignacion } from '../test/fabrica'
import { aplicarBorrador, capturar } from './borrador'
import {
  type AccionFormulario, type DatosEditar, type DatosNuevo, type EstadoFormulario, type FilaEstado,
  chasisResaltados, estadoInicial, reducir, subFila,
} from './estado'

/** Catálogo del modelo 16 con chasis SIM y eSIM y tapas (spec 0.9.7 §9). */
function catalogo16() {
  const a = agrupados()
  const sku = (idCom: number, tipo: string) => componente({ idCom, tipo, stock: 9999, stockMinimo: 2 })
  a.cha = [sku(201, 'chai16black'), sku(202, 'chai16blackesim'), sku(203, 'chai16teal'), sku(204, 'chai16tealesim'), ...a.cha]
  a.bat = [...a.bat, sku(205, 'bati16')]
  return { ...a, tapa: [sku(211, 'tapai16black'), sku(212, 'tapai16teal')] }
}
function datosNuevo(parcial: Partial<DatosNuevo> = {}): DatosNuevo {
  return { modo: 'nuevo', idAsignacion: 'A20261009_1', imei: '355400000000111', agrupados: catalogo16(), solicitudes: [], incidencia: null, modeloTelefono: null, ...parcial }
}
function datosEditar(parcial: Partial<DatosEditar> = {}): DatosEditar {
  return { modo: 'editar', idRep: 'R20261009_5', detalle: detalleEdicion({ idCom: 205 }), agrupados: catalogo16(), yaReparados: [], accionesYaReparadas: [], ...parcial }
}
const aplicar = (e: EstadoFormulario, ...acciones: AccionFormulario[]) => acciones.reduce(reducir, e)
const fila = (e: EstadoFormulario, prefijo: string): FilaEstado => {
  const f = e.filas.find((x) => x.prefijo === prefijo)
  if (!f) throw new Error(`no hay fila ${prefijo}`)
  return f
}
const modelo16 = () => aplicar(estadoInicial(datosNuevo()), { tipo: 'CAMBIAR_MODELO', modelo: '16' })

describe('chasis y tapa sin SKU preseleccionado (spec 0.9.7 §9.1)', () => {
  it('al elegir el modelo, chasis y tapa quedan sin elegir; el resto se preselecciona como siempre', () => {
    const e = modelo16()
    expect(fila(e, 'cha').idCom).toBeNull()
    expect(fila(e, 'tapa').idCom).toBeNull()
    expect(fila(e, 'bat').idCom).toBe(205)
    expect(fila(e, 'cha').controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: true, observacion: false })
  })
  it('sin SKU, "+" y "Reutilizado" no hacen nada y no sale la sub-fila de sin stock', () => {
    const e = modelo16()
    expect(aplicar(e, { tipo: 'SUMAR', prefijo: 'cha' }, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cha', valor: true })).toBe(e)
    expect(subFila(e, fila(e, 'cha'))).toEqual({ tipo: 'oculta' })
  })
  it('al elegir el SKU se encienden los controles y se puede sumar', () => {
    const e = aplicar(modelo16(), { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 204 }, { tipo: 'SUMAR', prefijo: 'cha' })
    expect(fila(e, 'cha')).toMatchObject({ idCom: 204, cantidad: 1 })
  })
  it('al cambiar de modelo vuelven a sin elegir', () => {
    const e = aplicar(modelo16(), { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 201 }, { tipo: 'CAMBIAR_MODELO', modelo: '13' })
    expect(fila(e, 'cha').idCom).toBeNull()
  })
})

describe('enlace de color chasis ↔ tapa (spec 0.9.7 §9.1)', () => {
  it('elegir el chasis pone la tapa del mismo color', () => {
    const e = aplicar(modelo16(), { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 204 })
    expect(fila(e, 'tapa').idCom).toBe(212)
    expect(fila(e, 'tapa').cantidad).toBe(0)
  })
  it('elegir la tapa con chasis elegido cambia el color del chasis conservando su eSIM', () => {
    const e = aplicar(modelo16(), { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 202 }, { tipo: 'CAMBIAR_SKU', prefijo: 'tapa', idCom: 212 })
    expect(fila(e, 'cha').idCom).toBe(204)
  })
  it('elegir la tapa con el chasis sin elegir: el chasis sigue sin elegir y se resaltan las del color de la tapa', () => {
    const e = aplicar(modelo16(), { tipo: 'CAMBIAR_SKU', prefijo: 'tapa', idCom: 212 })
    expect(fila(e, 'cha').idCom).toBeNull()
    expect([...chasisResaltados(e)].sort()).toEqual([203, 204])
    expect(chasisResaltados(aplicar(e, { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 203 })).size).toBe(0)
  })
  it('el enlace no cambia la cantidad ni "Reutilizado" de la otra fila', () => {
    const e = aplicar(modelo16(),
      { tipo: 'CAMBIAR_SKU', prefijo: 'tapa', idCom: 211 }, { tipo: 'SUMAR', prefijo: 'tapa' },
      { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 203 })
    expect(fila(e, 'tapa')).toMatchObject({ idCom: 212, cantidad: 1, reutilizado: false })
  })
  it('no toca la fila en edición', () => {
    // Edición de una tapa (211, modelo 16): elegir el chasis teal no cambia la tapa editada.
    const e0 = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 211 }) }))
    expect(fila(e0, 'tapa').rol).toBe('editada')
    const e = aplicar(e0, { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 203 })
    expect(fila(e, 'tapa').idCom).toBe(211)
  })
})

describe('al abrir y con el borrador (spec 0.9.7 §9.3)', () => {
  it('editar un chasis: la tapa empieza con su color, sin subir revision', () => {
    const e = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 204 }) }))
    expect(fila(e, 'cha').idCom).toBe(204)
    expect(fila(e, 'tapa').idCom).toBe(212)
    expect(e.revision).toBe(0)
  })
  it('una solicitud de chasis sigue preseleccionando su SKU, y la tapa toma su color', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ idCom: 203 })] }))
    expect(fila(e, 'cha').idCom).toBe(203)
    expect(fila(e, 'tapa').idCom).toBe(212)
  })
  it('el borrador recupera el chasis elegido y su cantidad', () => {
    const elegido = aplicar(modelo16(), { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 202 }, { tipo: 'SUMAR', prefijo: 'cha' })
    const recuperado = aplicarBorrador(estadoInicial(datosNuevo()), capturar(elegido))
    expect(fila(recuperado, 'cha')).toMatchObject({ idCom: 202, cantidad: 1 })
    expect(fila(recuperado, 'cha').controles.menos).toBe(true)
  })
  it('una fila de chasis sin SKU en el borrador no recupera cantidad', () => {
    const recuperado = aplicarBorrador(estadoInicial(datosNuevo()), capturar(modelo16()))
    expect(fila(recuperado, 'cha')).toMatchObject({ idCom: null, cantidad: 0, reutilizado: false })
  })
})
```

(`solicitudAsignacion({ idCom: 203 })` es pendiente por defecto: fija el modelo 16 por la solicitud.)

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/formulario/estado.color.test.ts`
Expected: FAIL (`chasisResaltados` no existe; el chasis sale preseleccionado con 201).

- [ ] **Step 3: Implementación en `estado.ts`**

1. Import: `import { PREFIJO_CHASIS, PREFIJO_TAPA, PREFIJOS_CON_COLOR, leerSku, mismoColor } from '../lib/colores'`.
2. Comentario del campo `idCom` de `FilaEstado`: `// SKU elegido; null si opciones está vacío o, en chasis y tapa, hasta que se elige (spec 0.9.7 §9)`.
3. Tras `CONTROLES_APAGADOS`:

```ts
/** Chasis o tapa con opciones pero sin SKU elegido: solo el combo encendido (spec 0.9.7 §9). */
const CONTROLES_SOLO_SKU: ControlesFila = { ...CONTROLES_APAGADOS, sku: true }
```

4. `function controlesIniciales` pasa a `export function controlesIniciales` (lo usa `borrador.ts`).
5. En `filaLimpia`, el SKU por defecto:

```ts
  // Chasis y tapa no se preseleccionan (spec 0.9.7 §9): el técnico elige el color a conciencia.
  const elegido = PREFIJOS_CON_COLOR.includes(base.prefijo) ? null : (opciones.find((c) => c.stock > 0) ?? opciones[0] ?? null)
```

   y en el objeto devuelto: `controles: elegido ? controlesIniciales(elegido) : opciones.length > 0 ? CONTROLES_SOLO_SKU : CONTROLES_APAGADOS,`.

6. En `cambiarSku`, justo después de la línea del guardia (`if (bloqueada(fila) || …) return null`):

```ts
  // Primera elección de chasis o tapa: controles como una fila recién puesta sobre ese SKU.
  if (fila.idCom === null) return { ...fila, idCom, controles: controlesIniciales(c) }
```

7. En `subFila`, tras su primera línea (`if (e.modo === 'editar' || …) return { tipo: 'oculta' }`):

```ts
  if (fila.idCom === null) return { tipo: 'oculta' } // chasis o tapa sin elegir: no hay SKU del que hablar
```

8. Justo antes de `export function reducir`:

```ts
/** El enlace de color nunca cambia la fila en edición ni una ya reparada (spec 0.9.7 §9.3); las bloqueadas y las de
 *  solicitud ya las rechaza cambiarSku. */
function enlazable(fila: FilaEstado): boolean {
  return fila.rol === 'normal'
}

/** Enlace de color tras elegir a mano el SKU del chasis o de la tapa (spec 0.9.7 §9.1). Chasis → tapa del mismo color.
 *  Tapa → chasis del mismo color conservando su SIM/eSIM, solo si el chasis ya estaba elegido (si no, sigue sin elegir y
 *  chasisResaltados marca las opciones). Solo cambia el SKU (cambiarSku), nunca cantidad ni "Reutilizado". */
function enlazarColor(estado: EstadoFormulario, origen: string): EstadoFormulario {
  const chasis = estado.filas.find((f) => f.prefijo === PREFIJO_CHASIS)
  const tapa = estado.filas.find((f) => f.prefijo === PREFIJO_TAPA)
  if (chasis === undefined || tapa === undefined) return estado
  if (origen === PREFIJO_CHASIS) {
    const c = componenteDe(chasis)
    if (c === null || !enlazable(tapa)) return estado
    const color = leerSku(c.tipo, PREFIJO_CHASIS)
    const destino = tapa.opciones.find((o) => mismoColor(leerSku(o.tipo, PREFIJO_TAPA), color))
    return destino ? cambiarFila(estado, PREFIJO_TAPA, (f) => cambiarSku(f, destino.idCom)) : estado
  }
  if (origen === PREFIJO_TAPA) {
    const t = componenteDe(tapa)
    const actual = componenteDe(chasis)
    if (t === null || actual === null || !enlazable(chasis)) return estado
    const color = leerSku(t.tipo, PREFIJO_TAPA)
    const esim = leerSku(actual.tipo, PREFIJO_CHASIS).esim
    const destino = chasis.opciones.find((o) => {
      const lectura = leerSku(o.tipo, PREFIJO_CHASIS)
      return lectura.esim === esim && mismoColor(lectura, color)
    })
    return destino ? cambiarFila(estado, PREFIJO_CHASIS, (f) => cambiarSku(f, destino.idCom)) : estado
  }
  return estado
}

/** Al abrir: si el chasis ya trae SKU (edición, solicitud o rechazada) y la tapa no, la tapa toma su color. Sin subir
 *  revision: abrir no es un cambio que reprograme el autoguardado. */
function enlazarAlAbrir(estado: EstadoFormulario): EstadoFormulario {
  const tapa = estado.filas.find((f) => f.prefijo === PREFIJO_TAPA)
  if (tapa === undefined || tapa.idCom !== null) return estado
  const enlazado = enlazarColor(estado, PREFIJO_CHASIS)
  return enlazado === estado ? estado : { ...enlazado, revision: estado.revision }
}

/** Opciones del chasis del color de la tapa elegida, mientras el chasis está sin elegir (spec 0.9.7 §9.1, opción A). */
export function chasisResaltados(e: EstadoFormulario): Set<number> {
  const chasis = e.filas.find((f) => f.prefijo === PREFIJO_CHASIS)
  const tapa = e.filas.find((f) => f.prefijo === PREFIJO_TAPA)
  const t = tapa === undefined ? null : componenteDe(tapa)
  if (chasis === undefined || chasis.idCom !== null || t === null) return new Set()
  const color = leerSku(t.tipo, PREFIJO_TAPA)
  return new Set(chasis.opciones.filter((o) => mismoColor(leerSku(o.tipo, PREFIJO_CHASIS), color)).map((o) => o.idCom))
}
```

9. En `reducir`, el caso `CAMBIAR_SKU`:

```ts
    case 'CAMBIAR_SKU': {
      const tras = cambiarFila(estado, accion.prefijo, (f) => cambiarSku(f, accion.idCom))
      return tras === estado ? estado : enlazarColor(tras, accion.prefijo)
    }
```

10. `estadoInicial`: `return enlazarAlAbrir(datos.modo === 'editar' ? inicialEditar(datos) : inicialNuevo(datos))`.

- [ ] **Step 4: Implementación en `borrador.ts` (`aplicarFila`)**

1. Import: añadir `controlesIniciales` a lo que se importa de `./estado`.
2. Sustituir el comienzo de `aplicarFila`, desde su primer comentario hasta la línea de `const base` incluida, por:

```ts
  // Una solicitud ya guardada en el servidor manda sobre el borrador; sin SKU para el modelo no hay nada que restaurar.
  if (fila.solicitud !== null || fila.opciones.length === 0) return fila
  // El SKU solo se preselecciona si está entre las opciones actuales; si no, queda el SKU por defecto (en chasis y tapa,
  // ninguno: spec 0.9.7 §9). Sobre una fila sin elegir, los controles salen como recién puesta sobre ese SKU.
  const elegido = f.idCom > 0 ? fila.opciones.find((c) => c.idCom === f.idCom) : undefined
  const base: FilaEstado = elegido === undefined ? fila
    : fila.idCom === null ? { ...fila, idCom: elegido.idCom, controles: controlesIniciales(elegido) }
    : { ...fila, idCom: elegido.idCom, controles: { ...fila.controles, mas: elegido.stock > 0 } }
```

3. Justo antes del comentario `// Normal: "Reutilizado", cantidad …`:

```ts
  // Chasis o tapa sin SKU recuperable: no hay pieza a la que devolver cantidad ni "Reutilizado".
  if (base.idCom === null) return base
```

- [ ] **Step 5: Comprobar que pasa, y todo el formulario**

Run: `npx vitest run src/modules/taller/formulario && npx tsc -b && npm run lint`
Expected: PASS. Los tests existentes usan el chasis `chai13negro`; si alguno contaba con que el chasis viniera
preseleccionado (por ejemplo un `SUMAR` sobre `cha` sin `CAMBIAR_SKU` antes, o una lista de SKU elegidos por defecto),
añadirle el `CAMBIAR_SKU` previo o ajustar el valor esperado del chasis a `null`, sin cambiar lo demás que comprueba, y
listarlo en el informe.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/formulario/estado.ts src/modules/taller/formulario/borrador.ts src/modules/taller/formulario/estado.color.test.ts
git commit -m "feat: chasis y tapa sin sku preseleccionado y enlazados por color en el formulario"
```

(Si hubo que ajustar tests existentes, añadirlos al `git add`.)

---

### Task 9: Web — filas de chasis y tapa con color, bloques SIM/eSIM y resaltado

**Files:**
- Create: `gestion-reparaciones-web/src/modules/taller/formulario/opcionesSku.ts`
- Test: `gestion-reparaciones-web/src/modules/taller/formulario/opcionesSku.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/taller/formulario/FilaComponente.tsx` (combo de SKU)
- Test: `gestion-reparaciones-web/src/modules/taller/formulario/FormularioReparacion.test.tsx`
- Modify: `gestion-reparaciones-web/docs/paridad/formulario.md`

**Interfaces:**
- Consumes: `OpcionCombo` (Task 7); `PREFIJO_CHASIS`, `PREFIJOS_CON_COLOR`, `colorDeSku` (Task 6); `chasisResaltados`
  (Task 8); `claseStock` de `../lib/piezas`.
- Produces: `opcionesSku(fila: FilaEstado, resaltadas: Set<number>): OpcionCombo[]` y `TEXTO_SIN_COLOR = '— Elige color —'`.

- [ ] **Step 1: Tests que fallan (opciones)**

Crear `src/modules/taller/formulario/opcionesSku.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { componente } from '../test/fabrica'
import type { FilaEstado } from './estado'
import { TEXTO_SIN_COLOR, opcionesSku } from './opcionesSku'

const sku = (idCom: number, tipo: string, stock = 9999): Componente => componente({ idCom, tipo, stock, stockMinimo: 2 })
const filaDe = (prefijo: string, opciones: Componente[]) => ({ prefijo, opciones }) as FilaEstado

describe('opcionesSku (combo de SKU de cada fila)', () => {
  it('filas sin color: como siempre (SKU tal cual y clase de stock)', () => {
    expect(opcionesSku(filaDe('bat', [sku(1, 'bati16', 0)]), new Set())).toEqual([{ valor: '1', etiqueta: 'bati16', clase: 'text-rojo-sin-stock' }])
  })
  it('chasis: SIM y luego eSIM, cada bloque por nombre; nombre en la lista, SKU en el botón y en el title', () => {
    const fila = filaDe('cha', [sku(1, 'chai16teal'), sku(2, 'chai16tealesim'), sku(3, 'chai16black'), sku(4, 'chai16blackesim')])
    const opciones = opcionesSku(fila, new Set([1, 2]))
    expect(opciones.map((o) => [o.grupo, o.etiqueta, o.etiquetaBoton, o.resaltada])).toEqual([
      ['SIM', 'Black', 'chai16black', false], ['SIM', 'Teal', 'chai16teal', true],
      ['eSIM', 'Black', 'chai16blackesim', false], ['eSIM', 'Teal', 'chai16tealesim', true],
    ])
    expect(opciones[0]).toMatchObject({ titulo: 'chai16black', color: '#3C3C3E' })
  })
  it('chasis solo SIM: sin bloques', () => {
    const opciones = opcionesSku(filaDe('cha', [sku(1, 'chai13midnight'), sku(2, 'chai13blue')]), new Set())
    expect(opciones.map((o) => [o.grupo, o.etiqueta])).toEqual([[undefined, 'Blue'], [undefined, 'Midnight']])
  })
  it('tapa: sin bloques; color desconocido con tono null', () => {
    const opciones = opcionesSku(filaDe('tapa', [sku(1, 'tapai16teal'), sku(2, 'tapai16fucsia')]), new Set())
    expect(opciones.map((o) => [o.grupo, o.etiqueta, o.color])).toEqual([[undefined, 'fucsia', null], [undefined, 'Teal', '#B0D4D2']])
  })
  it('texto vacío de chasis y tapa', () => {
    expect(TEXTO_SIN_COLOR).toBe('— Elige color —')
  })
})
```

- [ ] **Step 2: Comprobar que falla**

Run: `npx vitest run src/modules/taller/formulario/opcionesSku.test.ts`
Expected: FAIL (no existe `./opcionesSku`).

- [ ] **Step 3: Implementación de `opcionesSku.ts`**

```ts
import type { OpcionCombo } from '@/shared/ui/ComboNavy'
import { PREFIJO_CHASIS, PREFIJOS_CON_COLOR, colorDeSku } from '../lib/colores'
import { claseStock } from '../lib/piezas'
import type { FilaEstado } from './estado'

/** Texto del combo de chasis y tapa sin SKU elegido (spec 0.9.7 §9.1). */
export const TEXTO_SIN_COLOR = '— Elige color —'

/** Opciones del combo de SKU de una fila. Chasis y tapa (spec 0.9.7 §9): círculo de color, nombre oficial en la lista,
 *  SKU completo en el botón y en el title; el chasis en bloques «SIM» y «eSIM» (solo si hay de los dos) y con las
 *  opciones del color de la tapa resaltadas. El resto de filas, como siempre. */
export function opcionesSku(fila: FilaEstado, resaltadas: Set<number>): OpcionCombo[] {
  if (!PREFIJOS_CON_COLOR.includes(fila.prefijo)) {
    return fila.opciones.map((c) => ({ valor: String(c.idCom), etiqueta: c.tipo, clase: claseStock(c) }))
  }
  const conColor = fila.opciones
    .map((c) => ({ c, color: colorDeSku(c.tipo, fila.prefijo) }))
    .sort((a, b) => Number(a.color.esim) - Number(b.color.esim) || a.color.nombre.localeCompare(b.color.nombre, 'es'))
  const conBloques = fila.prefijo === PREFIJO_CHASIS && conColor.some((x) => x.color.esim) && conColor.some((x) => !x.color.esim)
  return conColor.map(({ c, color }) => ({
    valor: String(c.idCom),
    etiqueta: color.nombre,
    etiquetaBoton: c.tipo,
    titulo: c.tipo,
    color: color.tono,
    grupo: conBloques ? (color.esim ? 'eSIM' : 'SIM') : undefined,
    resaltada: resaltadas.has(c.idCom),
    clase: claseStock(c),
  }))
}
```

- [ ] **Step 4: Usarlo en `FilaComponente.tsx`**

1. Imports: añadir `chasisResaltados` al import existente de `./estado`; `import { TEXTO_SIN_COLOR, opcionesSku } from './opcionesSku'`;
   `import { PREFIJO_CHASIS, PREFIJOS_CON_COLOR } from '../lib/colores'`. Quitar `claseStock` del import de `../lib/piezas`
   si deja de usarse en el fichero.
2. En el `ComboNavy` de SKU, las props `opciones` y `textoVacio` pasan a:

```tsx
            opciones={opcionesSku(fila, prefijo === PREFIJO_CHASIS ? chasisResaltados(estado) : new Set())}
            textoVacio={PREFIJOS_CON_COLOR.includes(prefijo) ? TEXTO_SIN_COLOR : '—'}
```

(el resto de props del combo, igual).

- [ ] **Step 5: Test de interfaz**

En `FormularioReparacion.test.tsx` (importar `componente` de `../test/fabrica` si no está), junto al test
`'con el modelo elegido no se pintan los tipos sin ningún SKU de ese modelo (spec 0.9.7 §5.2)'`:

```tsx
  it('chasis y tapa: «— Elige color —», bloques SIM/eSIM y elegir el chasis pone la tapa (spec 0.9.7 §9)', async () => {
    const a = agrupados()
    const sku = (idCom: number, tipo: string) => componente({ idCom, tipo, stock: 9999, stockMinimo: 2 })
    abrir({
      agrupados: {
        ...a,
        bat: [...a.bat, sku(205, 'bati16')],
        cha: [sku(201, 'chai16black'), sku(202, 'chai16blackesim'), sku(203, 'chai16teal'), sku(204, 'chai16tealesim'), ...a.cha],
        tapa: [sku(211, 'tapai16black'), sku(212, 'tapai16teal')],
      },
    })
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 16')
    const chasis = screen.getByRole('combobox', { name: 'SKU de Chasis' })
    expect(chasis).toHaveTextContent('— Elige color —')
    expect(screen.getByRole('combobox', { name: 'SKU de Tapa trasera' })).toHaveTextContent('— Elige color —')
    expect(screen.getByRole('button', { name: 'Sumar Chasis' })).toBeDisabled()
    await userEvent.click(chasis)
    const lista = screen.getByRole('listbox', { name: 'SKU de Chasis' })
    expect(Array.from(lista.querySelectorAll('li')).map((li) => li.textContent)).toEqual(['SIM', 'Black', 'Teal', 'eSIM', 'Black', 'Teal'])
    await userEvent.click(within(lista).getAllByRole('button', { name: 'Teal' })[1])
    expect(screen.getByRole('combobox', { name: 'SKU de Chasis' })).toHaveTextContent('chai16tealesim')
    expect(screen.getByRole('combobox', { name: 'SKU de Tapa trasera' })).toHaveTextContent('tapai16teal')
    expect(screen.getByRole('button', { name: 'Sumar Chasis' })).toBeEnabled()
  })
```

- [ ] **Step 6: Paridad**

En `docs/paridad/formulario.md`, junto a la línea de «Tipo sin SKU para el modelo», añadir una línea `- [x]` con la regla
del §9 (spec 0.9.7): chasis y tapa sin SKU preseleccionado («— Elige color —», «+» y «Reutilizado» desactivados hasta
elegir), círculo de color y nombre oficial en la lista, SKU completo en el botón, bloques SIM/eSIM en el chasis, enlace de
color chasis ↔ tapa (la tapa nunca decide SIM/eSIM: con chasis sin elegir se resaltan las del color) y filas en edición,
bloqueadas o con solicitud sin tocar. En «Correcciones deliberadas respecto al JavaFX», una línea: el JavaFX
preseleccionaba el primer chasis con stock; desde la 0.9.7 chasis y tapa se eligen a mano.

- [ ] **Step 7: Comprobar que pasa, y todo el taller**

Run: `npx vitest run src/modules/taller src/shared/ui && npx tsc -b && npm run lint`
Expected: PASS, tsc y lint limpios.

- [ ] **Step 8: Commit**

```bash
git add src/modules/taller/formulario/opcionesSku.ts src/modules/taller/formulario/opcionesSku.test.ts src/modules/taller/formulario/FilaComponente.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx docs/paridad/formulario.md
git commit -m "feat: selector de chasis y tapa con color, bloques sim y esim y resaltado"
```

---

### Task 10: Cierre del añadido — CHANGELOG, novedades, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.7]`)
- Modify: `docs/novedades/NOVEDADES-v0.9.7.md` (raíz)

- [ ] **Step 1: CHANGELOG**

En la sección `## [0.9.7] - 2026-10-09 — Tapa trasera`, añadir tras el punto «Formulario más limpio…»:

```markdown
- **Chasis y tapa trasera se eligen a mano y con color.** Ya no vienen con un SKU preseleccionado: el desplegable dice "— Elige color —" y "+" y "Reutilizado" se activan al elegirlo. En la lista, cada opción lleva un círculo con su color y el nombre oficial (Ultramarine, Black Titanium…); en el botón, el SKU completo. El chasis separa las opciones en **SIM** y **eSIM**. Chasis y tapa van enlazados: al elegir el chasis, la tapa pasa a su color; al elegir la tapa, el chasis cambia de color conservando su SIM/eSIM (si aún no está elegido, se resaltan las opciones de ese color).
```

- [ ] **Step 2: Novedades (raíz)**

En `docs/novedades/NOVEDADES-v0.9.7.md`, añadir antes de la sección `## 📦 Stock`:

```markdown
## 🎨 Chasis y tapa: se eligen a mano y con color

- El desplegable de **Chasis** y el de **Tapa trasera** ya no traen nada elegido: dicen **«— Elige color —»** y no
  puedes sumar hasta elegir. Así nadie registra un color por despiste.
- Cada opción lleva un **círculo con su color** y su nombre oficial; al elegirla, el botón enseña el SKU completo.
- En el chasis, las opciones van en dos bloques: **SIM** y **eSIM**. Fíjate en cuál es el teléfono.
- Van enlazados: al elegir el **chasis**, la tapa se pone del **mismo color**. Si eliges primero la **tapa**, el chasis
  te marca en verde las opciones de ese color para que elijas SIM o eSIM.

---

```

- [ ] **Step 3: Suites completas**

Run (web): `npx tsc -b && npx vitest run && npm run lint` → Expected: todo en verde (el `ImeisPage.test.tsx` de filtros
falló una vez con carga: si falla, relanzarlo solo y anotar los dos resultados).

- [ ] **Step 4: Commits**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git add CHANGELOG.md && git commit -m "docs: changelog 0.9.7 con el selector de chasis y tapa"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.7.md && git commit -m "docs: novedades 0.9.7 con el selector de chasis y tapa"
```

- [ ] **Step 5: Entrega (con OK del usuario en cada paso)**

1. Merge `--no-ff` de `feature/selector-color` a `main` de la web (`merge: selector de chasis y tapa con color (0.9.7)`), borrar la rama; push.
2. Gitlink de la web en la raíz.
3. Preprod: `git -C gestion-reparaciones-web pull --ff-only` y `docker compose up -d --build nginx`; `/version.json` 0.9.7;
   prueba a mano: en un 16, «— Elige color —», bloques SIM/eSIM, elegir chasis pone la tapa, elegir la tapa primero
   resalta los chasis; en un 13, solo chasis y sin bloques.
4. Producción con `Apuntes/prod-v097.md` (la web a desplegar pasa a ser el nuevo `main`); tags `v0.9.7` cuando lo diga el usuario.
