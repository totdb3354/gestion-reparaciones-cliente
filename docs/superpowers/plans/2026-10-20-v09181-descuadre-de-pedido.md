# 0.9.18.1 — El descuadre no cuenta los móviles de pedido · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que un móvil de pedido («Pedidos realizados») con un trabajo abierto deje de salir como descuadre en Stock de
teléfonos y en «Pegar varios IMEIs»; el descuadre queda solo para un móvil en stock en «Listos».

**Architecture:**
- **Solo web**, una función: `quienLoTiene` en `src/modules/telefonos/stock/quien.ts`. Todo lo que enseña «Quién lo tiene»
  (celda, aviso «con descuadre», filtro «Técnico», orden de la tabla, «Pegar varios IMEIs» y su copia) sale de ella.
- Arreglo aparte: rama `hotfix/0.9.18.1` desde el tag `v0.9.18` (`a501202`), en el worktree `/c/Users/dev/Documents/wt/web-09181`.
  Tras producción y el tag, merge `--no-ff` a `main` para que la 0.9.19 lo lleve.

**Tech Stack:** React 19 + TypeScript; pruebas con Vitest (`npm run check` = lint + typecheck + test) y Playwright para la E2E.

**Spec:** `docs/superpowers/specs/2026-10-20-v09181-descuadre-de-pedido-design.md` (raíz).

## Global Constraints

- **Solo web**: sin cambios en el servidor ni en la base; funciona con el servidor 0.9.18.
- **Descuadre = trabajo abierto de hace más de 60 minutos con el móvil en stock en «Listos».** Un móvil de pedido no da
  descuadre nunca, aunque la última caja que se le conoce sea «Listos».
- **«⚠ En caja, sin asignar» sigue sin salir en los de pedido.**
- Frase del descuadre, siempre: `NSYS dice "Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?`
- Versión `0.9.18.1`. **Nunca `npm version`**: con cuatro números escribe `0.9.1-8.1`.
- Repos públicos: en código, pruebas y documentos del repo, datos inventados («Técnico 1»), sin nombres reales ni IPs.
- Commits sin `Co-Authored-By`. Merge, push, tag y despliegues **solo con el OK del usuario**.
- Otras sesiones trabajan a la vez: el clon principal de la web y los worktrees `wt/web-0919-nr` y `wt/web-0920` no se tocan
  (salvo el merge final, Task 4, con sus condiciones). Antes de merge o push, `git fetch` y comparar con `origin`.

## Ajustes a la spec (se suben a la spec en el commit del plan)

- La versión se pone con `npm pkg set version=0.9.18.1` y las dos entradas raíz de `package-lock.json` se cambian a mano.
- `src/shared/lib/version.test.ts` exige hoy tres números (`/^\d+\.\d+\.\d+$/`): pasa a aceptar un cuarto opcional.

## Mapa de ficheros

| Fichero | Qué cambia |
|---|---|
| `src/modules/telefonos/stock/quien.ts` (worktree) | La regla del descuadre y sus comentarios |
| `src/modules/telefonos/stock/quien.test.ts` (worktree) | El caso de pedido pasa a «sin descuadre»; dos casos nuevos |
| `src/shared/lib/version.test.ts` (worktree) | Acepta `x.y.z.w` |
| `package.json`, `package-lock.json`, `CHANGELOG.md` (worktree) | Versión 0.9.18.1 y su entrada |
| `docs/novedades/NOVEDADES-v0.9.18.md` (raíz) | La línea del descuadre |

---

### Task 1: La regla — un móvil de pedido no da descuadre

**Files:**
- Modify: `src/modules/telefonos/stock/quien.ts:4-6` (cabecera) y `:62-75` (`quienLoTiene`)
- Test: `src/modules/telefonos/stock/quien.test.ts:56-66`

**Interfaces:**
- Consumes: nada de otras tareas. `movil`, `trabajo` y `AHORA` de `src/modules/telefonos/test/fabrica.ts` (AHORA = 16/10/2026
  13:43 en Madrid; el `trabajo()` por defecto es una reparación de «Técnico 1» desde las 09:00 de ese día).
- Produces: `quienLoTiene(m, ahora): Quien` con la misma firma y el mismo tipo `Quien`; solo cambia cuándo `descuadre` es `true`.

- [ ] **Step 1: Worktree y dependencias**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web fetch -q origin --tags
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add -b hotfix/0.9.18.1 /c/Users/dev/Documents/wt/web-09181 v0.9.18
cd /c/Users/dev/Documents/wt/web-09181 && npm ci --no-audit --no-fund && git log --oneline -1
```

Expected: `a501202 docs: fecha de la 0.9.18 en el changelog`.

- [ ] **Step 2: Las pruebas que fallan**

En `src/modules/telefonos/stock/quien.test.ts`, sustituir el caso entero que empieza por
`it('descuadre: trabajo abierto hace más de 60 minutos con el móvil en Listos o de pedido', () => {` (líneas 56-66) por:

```ts
  it('descuadre: trabajo abierto hace más de 60 minutos con el móvil en Listos', () => {
    // AHORA es el 16/10 a las 13:43 en Madrid; 12:42 son 61 minutos.
    const listos = q({ ubicacion: 'Listos', trabajos: [trabajo({ desde: '2026-10-16T12:42:00' })] })
    expect(listos.descuadre).toBe(true)
    expect(listos.texto).toBe('⚠ Técnico 1: trabajo abierto')
    expect(listos.etiquetas).toEqual(['Técnico 1', CON_DESCUADRE])
    expect(listos.titulo).toBe('Reparación de Técnico 1 desde el 16/10 · NSYS dice "Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?')
  })

  it('un móvil de pedido con trabajo abierto no es descuadre: tiene dueño, pero se repara hasta el Released (0.9.18.1)', () => {
    // El trabajo por defecto es de las 09:00: 4 h 43 min antes de AHORA.
    const r = q({ ubicacion: 'Taller · Reparación', estado: 'DE_PEDIDO', trabajos: [trabajo()] })
    expect(r.descuadre).toBe(false)
    expect(r.texto).toBe('Técnico 1')
    expect(r.etiquetas).toEqual(['Técnico 1'])
    expect(r.titulo).toBe('Reparación de Técnico 1 desde el 16/10')
  })

  it('tampoco si la última caja que se conoce del móvil de pedido es Listos (0.9.18.1)', () => {
    const r = q({ ubicacion: 'Listos', estado: 'DE_PEDIDO', trabajos: [trabajo()] })
    expect(r.descuadre).toBe(false)
    expect(r.texto).toBe('Técnico 1')
  })
```

El caso `'un móvil de pedido no está en la caja, aunque su última caja sea de taller'` se queda como está.

- [ ] **Step 3: Comprobar que fallan**

Run: `cd /c/Users/dev/Documents/wt/web-09181 && npx vitest run src/modules/telefonos/stock/quien.test.ts`
Expected: FAIL, **2 failed**: los dos casos «(0.9.18.1)», con `expected true to be false` en `descuadre`. El de «Listos» pasa.

- [ ] **Step 4: La regla**

En `src/modules/telefonos/stock/quien.ts`, la cabecera (líneas 4-6) pasa a:

```ts
/** «Quién lo tiene» (0.9.18, spec «Columna compacta, SKU, Quién lo tiene y Revisado» §4.7): las asignaciones abiertas del
 *  ERP cruzadas con la caja de NSYS. Los trabajos son del momento; la caja, de la última exportación (hasta ~35 min de
 *  retraso), por eso el descuadre espera 60 minutos. Las horas son de Madrid, sin zona: se cortan del texto.
 *  0.9.18.1 (spec «El descuadre no cuenta los móviles de pedido»): un móvil de pedido tiene dueño, pero se repara ahí hasta
 *  que NSYS lo pone Released, así que solo da descuadre un móvil en stock en «Listos». */
```

Y en `quienLoTiene`, el bloque que va de `// Un móvil de pedido ya no está en su caja de taller…` hasta la línea del detalle
del descuadre (líneas 62-75) queda así (lo de en medio, `texto` y `titulo`, igual salvo la frase):

```ts
  // Un móvil de pedido no da «en caja»: al pasar a «Pedidos realizados» su última caja se congela, y ya reparado, esperando
  // el Released, seguiría pareciendo en el taller.
  const enCaja = m.estado !== 'DE_PEDIDO' && (m.ubicacion ?? '').startsWith('Taller') && trabajos.length === 0
  // Solo un móvil en stock: la ubicación de uno de pedido es su última caja, y puede ser «Listos».
  const enListos = m.estado !== 'DE_PEDIDO' && m.ubicacion === 'Listos'
  // El texto del descuadre nombra solo a quien lleva el trabajo más de 60 minutos; las etiquetas, a todos.
  const tarde = enListos ? [...new Set(trabajos.filter((t) => minutosDesde(t.desde, fecha) > MINUTOS_DESCUADRE).map((t) => t.tecnico))] : []
  const descuadre = tarde.length > 0
  const texto = descuadre ? `⚠ ${tarde.join(' · ')}: trabajo abierto`
    : enCaja ? '⚠ En caja, sin asignar'
      : partes.length === 0 ? '—'
        : partes.map((p) => [p.prefijo, p.tecnico, p.sufijo, p.entrega?.texto].filter(Boolean).join(' ')).join(' · ')
  const titulo = [
    ...trabajos.map((t) => detalle(t, hoy)),
    ...(descuadre ? ['NSYS dice "Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la caja?'] : []),
    ...(enCaja ? ['Está en una caja de taller y nadie lo tiene asignado'] : []),
  ].join(' · ')
```

Desaparecen `fueraDelTaller` y `caja`.

- [ ] **Step 5: Comprobar que pasan, y la suite entera**

Run: `cd /c/Users/dev/Documents/wt/web-09181 && npx vitest run src/modules/telefonos/stock/quien.test.ts`
Expected: PASS, 13 tests (eran 11: uno cambia y entran dos).

Run: `npm run check`
Expected: lint y typecheck sin errores; todas las pruebas en verde (dos más que en `v0.9.18`).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-09181
git add src/modules/telefonos/stock/quien.ts src/modules/telefonos/stock/quien.test.ts
git commit -m "fix: un movil de pedido con trabajo abierto no es descuadre; solo cuenta Listos (0.9.18.1)"
```

---

### Task 2: Cierre — versión, CHANGELOG y novedades

**Files:**
- Modify: `package.json`, `package-lock.json`, `CHANGELOG.md`, `src/shared/lib/version.test.ts` (worktree)
- Modify: `docs/novedades/NOVEDADES-v0.9.18.md` (raíz)

**Interfaces:**
- Consumes: Task 1.
- Produces: la rama `hotfix/0.9.18.1` lista para preprod.

- [ ] **Step 1: La prueba de la versión acepta cuatro números**

En `src/shared/lib/version.test.ts`, el caso pasa a:

```ts
  it('es un semver tomado de package.json (con un cuarto número en un arreglo aparte, como 0.9.18.1)', () => {
    expect(APP_VERSION).toMatch(/^\d+\.\d+\.\d+(\.\d+)?$/)
  })
```

- [ ] **Step 2: Versión**

Run: `cd /c/Users/dev/Documents/wt/web-09181 && npm pkg set version=0.9.18.1 && grep -n '"version": "0.9.18"' package-lock.json`
Expected: `package.json` con `"version": "0.9.18.1"`; el grep da las líneas 3 y 9 de `package-lock.json`.

En `package-lock.json`, cambiar a mano (Edit) esas dos líneas, `"version": "0.9.18",` → `"version": "0.9.18.1",` (la de la raíz,
línea 3, y la de `packages[""]`, línea 9). Ninguna otra.

Run: `git diff --stat`
Expected: `package.json` y `package-lock.json` con 1 línea cada uno más la de `version.test.ts`.

- [ ] **Step 3: CHANGELOG**

En `CHANGELOG.md`, tras la línea `El formato sigue [Keep a Changelog]…` y su línea en blanco, antes de `## [0.9.18]`:

```markdown
## [0.9.18.1] - 2026-10-XX — Stock de teléfonos: el descuadre no cuenta los de pedido

- **Stock de teléfonos** (supertécnico y administrador): un móvil de pedido («Pedidos realizados») con un trabajo abierto ya no sale como descuadre. Tiene dueño, pero se repara ahí hasta que NSYS lo pone Released: sale el técnico, como en cualquier otro trabajo. El descuadre queda para un trabajo abierto de hace más de una hora con el móvil en Listos. Cambia en la tabla, en el aviso «con descuadre», en el filtro «Técnico» y en «Pegar varios IMEIs».
- Solo web: funciona con el servidor 0.9.18. Sin migración.

```

La entrada `[0.9.18]` no se toca.

- [ ] **Step 4: Comprobar y commit**

Run: `npm run check`
Expected: todo en verde.

```bash
git add package.json package-lock.json CHANGELOG.md src/shared/lib/version.test.ts
git commit -m "docs: version 0.9.18.1 y changelog del descuadre sin los moviles de pedido"
```

- [ ] **Step 5: Novedades de la 0.9.18 (raíz)**

En `docs/novedades/NOVEDADES-v0.9.18.md` de la raíz, las dos líneas

```markdown
  - **descuadre:** hay un trabajo abierto **de hace más de una hora** pero NSYS dice que está en Listos o de pedido.
    Falta cerrar el trabajo o mover la caja.
```

pasan a:

```markdown
  - **descuadre:** hay un trabajo abierto **de hace más de una hora** pero NSYS dice que está en Listos. Falta cerrar el
    trabajo o mover la caja. Un móvil de pedido con trabajo abierto no es descuadre: tiene dueño, pero se repara hasta que
    NSYS lo pone Released (0.9.18.1).
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/novedades/NOVEDADES-v0.9.18.md
git commit -m "docs: novedades de la 0.9.18 con el descuadre sin los moviles de pedido (0.9.18.1)"
```

- [ ] **Step 6: Push de la rama (con el OK del usuario)**

```bash
cd /c/Users/dev/Documents/wt/web-09181 && git push -u origin hotfix/0.9.18.1
```

---

### Task 3: Preproducción

**Files:**
- Create: `Apuntes/preprod/v09181-preprod.md` (guion, fuera de los repos)
- Modify: `Apuntes/despliegue_preprod.md` (registro)

**Requisitos:** Task 2 con la rama subida. **Preguntar al usuario si preprod está libre**: otras sesiones prueban allí la 0.9.19.

- [ ] **Step 1: Guion**

Basado en `preprod/v0919-preprod.md` (solo web). **Cada bloque empieza con `hostname` y la comprobación de `backup-erp.sh`.**
- **Bloque 1 (solo lee):** commit actual de la web (`git -C gestion-reparaciones-web log --oneline -1`, **se apunta para el
  bloque 3**), contenedores y `git -C gestion-reparaciones-web fetch -q origin && git -C gestion-reparaciones-web log --oneline -1 origin/hotfix/0.9.18.1`.
- **Bloque 2 (escribe), con el OK de Claude:**
  - `git -C gestion-reparaciones-web checkout --detach origin/hotfix/0.9.18.1` (no `pull`: `main` lleva la 0.9.19);
  - `docker compose build nginx && docker compose up -d --no-deps nginx`: avisar de que tarda ~20 s sin salida;
  - nginx `Up … seconds`, y el backend y MariaDB con el mismo tiempo que antes.
- **Comprobaciones de Claude desde el PC:**
  - `/version.json` da `0.9.18.1`;
  - E2E `stock-telefonos` (solo lee) con `E2E_BASE_URL` de preprod y las cuentas de `~/.env.e2e`.
- **Bloque 3 (escribe):** preprod vuelve al commit del bloque 1 (`git -C gestion-reparaciones-web checkout <commit>` o
  `checkout main` si estaba en `main`) y `docker compose build nginx && docker compose up -d --no-deps nginx`.
- **Pruebas a mano con el usuario** (antes del bloque 3, en su navegador): Stock de teléfonos con el interruptor encendido, un
  móvil de pedido con trabajo abierto sale con el técnico en negro; uno en Listos con trabajo de hace más de una hora sigue
  en rojo (si los hay en los datos de preprod).
- **Vuelta atrás:** el bloque 3.

- [ ] **Step 2: Ejecución con el usuario y registro**

1. El usuario pega los bloques y Claude valida cada salida antes de seguir.
2. Entrada en `despliegue_preprod.md` con los commits, la E2E y lo que se vio a mano.
3. **Producción solo** cuando cuadra y el usuario da el OK.

---

### Task 4: Producción, tag y merge a `main`

**Files:**
- Create: `Apuntes/prod-v09181.md` (guion)
- Modify: `Apuntes/despliegue_vdc_produccion.md` (registro); en la raíz, el gitlink de la web y `Apuntes/plan-futuro.md`
  (fuera del repo); en la memoria, `project_produccion_vdc.md`

- [ ] **Step 1: Guion y despliegue**

Basado en el bloque de la web de `prod-v0918.md`.
- **En horario:** solo web, sin migración, corte de unos segundos de nginx y vuelta atrás simple.
- Cada bloque empieza con `hostname` y `backup-erp.sh`.
- **Bloque 1 (solo lee):** commit actual de la web (debería ser `741b633`, la de la 0.9.18; se apunta para la vuelta atrás) y
  `origin/hotfix/0.9.18.1` tras el `fetch`.
- **Bloque 2 (escribe):** `git -C gestion-reparaciones-web fetch -q origin && git -C gestion-reparaciones-web checkout --detach origin/hotfix/0.9.18.1`
  (**no `pull`**: `main` lleva la 0.9.19), y `docker compose build nginx && docker compose up -d --no-deps nginx`.
- Comprobaciones: `/version.json` da `0.9.18.1`; el IMEI de pedido con trabajo abierto que dio pie al arreglo sale con su
  técnico en negro en «Pegar varios IMEIs» (lo mira el usuario).
- **Vuelta atrás:** `git -C gestion-reparaciones-web checkout <commit del bloque 1>` y el mismo build.
- **Aviso en el guion y en el registro:** producción queda en un commit suelto. La próxima subida (0.9.19) empieza con
  `git -C gestion-reparaciones-web checkout main` antes del `pull --ff-only`.
- **Sin E2E en producción.**

- [ ] **Step 2: Fecha y tag (con el OK del usuario)**

```bash
cd /c/Users/dev/Documents/wt/web-09181
# CHANGELOG: «## [0.9.18.1] - 2026-10-XX» con la fecha de producción
git add CHANGELOG.md && git commit -m "docs: fecha de la 0.9.18.1 en el changelog"
git tag v0.9.18.1 && git push origin hotfix/0.9.18.1 v0.9.18.1
```

- [ ] **Step 3: Merge a `main` de la web (con el OK del usuario)**

Solo si en el clon principal `git status` está limpio, en `main`, y `git fetch` deja `main` igual a `origin/main`; si no,
parar y preguntar al usuario (otras sesiones trabajan en él).

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch -q origin && git status -sb
git merge --no-ff hotfix/0.9.18.1 -m "merge: el descuadre no cuenta los moviles de pedido (0.9.18.1)"
```

Conflictos esperados y cómo quedan:
- `package.json` y `package-lock.json`: `0.9.19` (lo de `main`);
- `CHANGELOG.md`: las dos entradas, en este orden: `[0.9.19]`, `[0.9.18.1]`, `[0.9.18]`. **Ojo (revisión final):** las dos
  acaban con la misma línea `- Solo web: funciona con el servidor 0.9.18. Sin migración.` y git la deja fuera del conflicto,
  detrás de `>>>>>>>`. Al resolver, la `[0.9.19]` entera **con** su línea «Solo web…», una línea en blanco y la `[0.9.18.1]`
  entera con la suya (comprobado con `git merge-tree --write-tree main hotfix/0.9.18.1`).

Run: `npm run check`
Expected: todo en verde. Después `git push origin main` (con el OK).

- [ ] **Step 4: Raíz, limpieza y registro**

1. Gitlink de la web al commit del tag **sin tocar la copia del submódulo** (la usan otras sesiones):
   `git -C /c/Users/dev/Documents/ProgramaReparaciones update-index --cacheinfo 160000,$(git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web rev-parse v0.9.18.1^{commit}),gestion-reparaciones-web`
   y commit `chore: gitlink de la web con la 0.9.18.1` junto con este plan marcado. Push de la raíz con el OK.
2. `git worktree remove /c/Users/dev/Documents/wt/web-09181` y `git branch -d hotfix/0.9.18.1` (la rama remota se queda
   con el tag).
3. Registro en `despliegue_vdc_produccion.md` y en la memoria de producción (web en `v0.9.18.1`, commit suelto).
4. `Apuntes/plan-futuro.md` §5: la 0.9.18.1 marcada `[x]`.
