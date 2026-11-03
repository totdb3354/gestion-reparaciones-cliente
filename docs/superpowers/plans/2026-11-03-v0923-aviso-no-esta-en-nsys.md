# 0.9.23 — El modal de asignación avisa si el IMEI no está en NSYS · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** al picar o pegar un IMEI en «Asignar trabajos» (Reparación, Glass y Pulido), si NSYS no lo conoce, la fila dice
«no está en NSYS» en rojo y el detalle «No está en NSYS · último Items: hoy 13:40», para darlo de alta en NSYS con el móvil en
la mano.

**Architecture:**
- **Solo web.** El modal ya tiene un reductor puro (`estado/*.ts`) que pide trabajo asíncrono con «efectos» y un hook
  (`useEfectosModal`) que los ejecuta y despacha el resultado. Se añade un efecto `comprobarNsys` que llama a
  `POST /api/stock-telefonos/comprobar` (ya en producción desde la 0.9.18) y una acción `NSYS_COMPROBADO` que guarda la
  respuesta por IMEI en `EstadoModal.enNsys`.
- La hora del último Items sale de `useEstadoNsys` (ya en producción, cacheada por el indicador de la barra superior). Como un
  módulo no puede importar de otro (regla de ESLint), `useEstadoNsys`, `CLAVE_ESTADO_NSYS` y `MAX_IMEIS` suben de
  `src/modules/telefonos/api.ts` a `src/shared/api/nsys.ts` y el módulo de teléfonos los reexporta.
- El pintado son dos componentes pequeños (`MarcaNoNsys` en la fila, `LineaNoNsys` en el detalle) con sus textos en un `.ts`.

**Tech Stack:** React 19 + TypeScript, TanStack Query, openapi-fetch (`api`), Vitest + Testing Library + MSW, Playwright (E2E).

**Spec:** `docs/superpowers/specs/2026-11-03-v0923-aviso-no-esta-en-nsys-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-11-03-v0923-prototipo-aviso-no-nsys.html` (variante A, en rojo).

## Global Constraints

- **Solo web**: sin cambios en el servidor ni en la base; funciona con el servidor 0.9.22 de producción. Sin migración.
- **Qué avisa:** solo `encontrado: false` de `POST /api/stock-telefonos/comprobar` (NSYS no tiene el IMEI en ningún lote). Un
  vendido o de baja no avisa.
- **Textos exactos:**
  - fila: `no está en NSYS`;
  - detalle: `No está en NSYS · último Items: hoy 13:40`, `No está en NSYS · último Items: del 02/11 22:10` o, sin hora,
    `No está en NSYS`;
  - ayuda al pasar el ratón: `NSYS no tiene este IMEI en ningún lote del último Items (hoy 13:40). Al guardar saldrá en «No está en NSYS».`
    y, sin hora, `NSYS no tiene este IMEI en ningún lote del último Items. Al guardar saldrá en «No está en NSYS».`.
- **Aspecto:** rojo `text-texto-error` (#B03040, ya existe). Fila: `text-[10.5px] font-semibold`, en la **segunda línea** de la
  fila (junto a los técnicos: «Técnico A · no está en NSYS»). Detalle: `text-[11px]`, justo bajo «IMEI en curso».
- **Solo informa:** no bloquea «Asignar →» ni «Guardar (N)» y no cambia el lote. Si la consulta falla, no se enseña nada.
- **Una pregunta por IMEI** mientras el modal está abierto (picar, pegar, mismo IMEI en otra cola, quitar y volver a picar).
- **Fronteras:** un módulo no importa de otro (`no-restricted-imports` de `eslint.config.js`); lo común va en `src/shared`.
- **Repos públicos:** en código, pruebas y documentos del repo, datos inventados («Técnico A», IMEIs `111…`); ni URLs ni IPs
  de preprod o producción.
- **Commits** en castellano **sin tildes** y **sin** línea `Co-Authored-By`. **Nunca** push, merge ni tags sin el OK del usuario.
- **Otras sesiones trabajan a la vez:** no se tocan el clon principal de la web ni los otros worktrees `wt/…`. En la raíz hay
  ficheros sin seguimiento que no son de nadie (`.codegraph/`, `node_modules/`, PDF): `git add` solo los de la tarea.
- **Versión 0.9.23.** La entrada `[0.9.22.1]` del CHANGELOG no se toca: su fecha la pone quien cierre la 0.9.22.1.

**Dónde se trabaja:** worktree de la web `/c/Users/dev/Documents/wt/web-0923-aviso`, rama `dev_v0923_aviso_nsys`, desde
`main` de la web (`206fa5b`, 0.9.22.1). Todas las rutas `src/…` y `tests/…` de abajo son de ese worktree.

**Comandos:** `cd /c/Users/dev/Documents/wt/web-0923-aviso && npx vitest run <ficheros>`, `npx tsc -b`, `npm run lint`,
`npm run check` (lint + typecheck + test). Con otras sesiones usando la máquina, un timeout de 5 s en un test que no es de esta
tarea se repite solo ese fichero.

---

### Task 1: El estado de NSYS, el tope de IMEIs y la hora del Items, en `shared`

Prepara lo común: el modal del taller necesita `useEstadoNsys` y `MAX_IMEIS` (hoy en el módulo de teléfonos) y un texto «hoy
13:40» / «del 02/11 22:10». Además, MSW contesta por defecto a `/comprobar` para que los tests del modal que no van de esto no
cambien.

**Files:**
- Modify: `src/shared/api/nsys.ts`
- Modify: `src/modules/telefonos/api.ts:1-39`
- Modify: `src/shared/lib/telefonoNsys.ts`
- Modify: `src/shared/lib/telefonoNsys.test.ts:1-2`
- Modify: `src/test/server.ts`

**Interfaces:**
- Produces:
  - `src/shared/api/nsys.ts`: `export const MAX_IMEIS = 500`, `export const CLAVE_ESTADO_NSYS = ['telefonos', 'estado'] as const`,
    `export function useEstadoNsys(activo = true): UseQueryResult<EstadoNsys | null>` (misma conducta que hoy);
  - `src/shared/lib/telefonoNsys.ts`: `export function cuandoItems(iso: string, ahora: Date = new Date()): string` → `'hoy 13:40'` o
    `'del 02/11 22:10'` (iso = hora de Madrid sin zona, `'2026-11-03T13:40:00'`);
  - MSW por defecto: `POST */api/stock-telefonos/comprobar` contesta `[{ imei, encontrado: true }]` por cada IMEI pedido.

- [ ] **Step 0: Crear el worktree**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git fetch -q origin && git status -sb | head -1 && git worktree add -b dev_v0923_aviso_nsys /c/Users/dev/Documents/wt/web-0923-aviso main && git -C /c/Users/dev/Documents/wt/web-0923-aviso log --oneline -1
cd /c/Users/dev/Documents/wt/web-0923-aviso && npm ci
```

Expected: `## main...origin/main` sin «ahead/behind», y el log en `206fa5b merge: las plantillas de NSYS se descargan con Guardar como en el clic (0.9.22.1)`.
Si `main` de la web no está en `206fa5b`, **parar y avisar** (otra sesión ha subido algo).

- [ ] **Step 1: Test que falla de `cuandoItems`**

En `src/shared/lib/telefonoNsys.test.ts`, añade `cuandoItems` al import de la línea 2 (al principio de la lista):

```ts
import { cuandoItems, clasesUbicacion, descripcionMovil, diaNsys, diasDesdeNsys, textoGrado, nombreModeloNsys, textoEstadoMovil, textoPoner, COMPRUEBALO, ETIQUETA_AVISO, queHacerAviso, textoAvisoTaller, textoBateriaEnCorregir, textoBateriaRecordada } from './telefonoNsys'
```

y, al final del `describe('piezas compartidas del espejo de NSYS', …)` (antes de su `})` de cierre), este caso:

```ts
  it('0.9.23: cuándo es un Items: «hoy 13:40» o «del 02/11 22:10», con el día de Madrid', () => {
    const ahora = new Date('2026-11-03T12:00:00Z') // las 13:00 en Madrid
    expect(cuandoItems('2026-11-03T13:40:00', ahora)).toBe('hoy 13:40')
    expect(cuandoItems('2026-11-02T22:10:00', ahora)).toBe('del 02/11 22:10')
    // Las 23:30 UTC del 2 son ya el 3 en Madrid: un Items de las 00:10 del 3 es de hoy.
    expect(cuandoItems('2026-11-03T00:10:00', new Date('2026-11-02T23:30:00Z'))).toBe('hoy 00:10')
  })
```

- [ ] **Step 2: Comprobar que falla**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts`
Expected: FAIL (`cuandoItems` no existe: «is not a function» o error de import).

- [ ] **Step 3: `cuandoItems`**

En `src/shared/lib/telefonoNsys.ts`, cambia la primera línea:

```ts
import { hoyMadrid } from './fechas'
import { traducirModelo } from './modelos'
```

y añade, justo después de la función `diaNsys`:

```ts
/** 0.9.23: cuándo es un Items de NSYS (hora de Madrid, sin zona) para el aviso del modal de asignación: «hoy 13:40» o
 *  «del 02/11 22:10». */
export function cuandoItems(iso: string, ahora: Date = new Date()): string {
  const hora = iso.slice(11, 16)
  return iso.slice(0, 10) === hoyMadrid(ahora) ? `hoy ${hora}` : `del ${iso.slice(8, 10)}/${iso.slice(5, 7)} ${hora}`
}
```

- [ ] **Step 4: Comprobar que pasa**

Run: `npx vitest run src/shared/lib/telefonoNsys.test.ts`
Expected: PASS.

- [ ] **Step 5: Subir `useEstadoNsys`, `CLAVE_ESTADO_NSYS` y `MAX_IMEIS` a `src/shared/api/nsys.ts`**

En `src/shared/api/nsys.ts`, cambia las dos primeras líneas por:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type EstadoNsys, type MovilBuscado } from './client'
import { useIntervaloRefresco } from './refresco'
```

y añade, después de `export const CLAVE_RASTRO_MOVIL = ['imeis', 'rastro'] as const`:

```ts
/** Como mucho los IMEIs que se comprueban de una vez (spec 0.9.18 §4.9; el servidor da 400 por encima). Desde la 0.9.23 vive
 *  aquí: también comprueba IMEIs el modal de asignación del taller. */
export const MAX_IMEIS = 500
export const CLAVE_ESTADO_NSYS = ['telefonos', 'estado'] as const

/** GET /api/stock-telefonos/estado: hora de la última exportación de NSYS y nivel del aviso. Sin capa de carga ni diálogo
 *  de error: lo enseñan la píldora de la cabecera («no se pudo comprobar») y, desde la 0.9.18, el indicador de la barra
 *  superior en todas las vistas. `activo`: el indicador solo pregunta para SUPERTECNICO y ADMIN (al TECNICO le daría 403).
 *  Desde la 0.9.23 vive aquí: el modal de asignación del taller enseña la hora del último Items. */
export function useEstadoNsys(activo = true): UseQueryResult<EstadoNsys | null> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({
    queryKey: CLAVE_ESTADO_NSYS,
    enabled: activo,
    queryFn: async () => (await api.GET('/api/stock-telefonos/estado')).data ?? null,
    refetchInterval: intervalo,
    meta: { sinCapa: true, silenciarError: true },
  })
}
```

En `src/modules/telefonos/api.ts`:
- en el import de `@/shared/api/client` (líneas 2-5) quita `type EstadoNsys, ` (ya no se usa aquí);
- la línea `import { CLAVE_RASTRO_MOVIL, CLAVE_STOCK_TELEFONOS, CLAVE_TARJETAS_NSYS } from '@/shared/api/nsys'` pasa a:

```ts
import { CLAVE_RASTRO_MOVIL, CLAVE_STOCK_TELEFONOS, CLAVE_TARJETAS_NSYS, MAX_IMEIS } from '@/shared/api/nsys'
```

- borra la línea `export const CLAVE_ESTADO_NSYS = ['telefonos', 'estado'] as const`;
- borra estas dos líneas:

```ts
/** Como mucho los IMEIs que se comprueban de una vez (spec 0.9.18 §4.9; el servidor da 400 por encima). */
export const MAX_IMEIS = 500
```

- borra la función `useEstadoNsys` entera con su comentario (desde `/** GET /api/stock-telefonos/estado: hora de la última
  exportación…` hasta su `}` de cierre);
- justo después de `export const CLAVE_IMPORTACIONES_NSYS = ['telefonos', 'importaciones'] as const`, añade:

```ts
/** 0.9.23: el estado de NSYS y el tope de IMEIs viven en `shared` (también los usa el modal de asignación del taller); se
 *  reexportan para el resto del módulo. */
export { CLAVE_ESTADO_NSYS, MAX_IMEIS, useEstadoNsys } from '@/shared/api/nsys'
```

`IndicadorNsys.tsx`, `PildoraNsys.tsx`, `ComprobarImeis.tsx` y `api.test.tsx` siguen importando de `./api` / `../api` y no cambian.

- [ ] **Step 6: Respuesta por defecto de `/comprobar` en MSW**

En `src/test/server.ts`, al final del comentario de cabecera (antes de ` */`), añade la línea:

```ts
 * Y desde la 0.9.23, «Comprobar una lista de IMEIs»: todos en NSYS (el modal de asignación pregunta por cada IMEI que se pica).
```

y, como último handler de `setupServer(…)` (después del de `'*/api/stock-telefonos/:imeiSn'`):

```ts
  // 0.9.23: el modal de asignación pregunta a NSYS por cada IMEI que se pica o se pega. Por defecto NSYS los conoce todos (no
  // sale «no está en NSYS»), así los tests del modal que no van de esto no cambian.
  http.post('*/api/stock-telefonos/comprobar', async ({ request }) => {
    const { imeis } = (await request.json()) as { imeis: string[] }
    return HttpResponse.json(imeis.map((imei) => ({ imei, encontrado: true })))
  }),
```

- [ ] **Step 7: Comprobar que nada se ha roto**

Run: `npx vitest run src/modules/telefonos src/shared src/app && npx tsc -b && npm run lint`
Expected: todo PASS, sin errores de tipos ni de lint.

- [ ] **Step 8: Commit**

```bash
git add src/shared/api/nsys.ts src/modules/telefonos/api.ts src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/test/server.ts
git commit -m "refactor: el estado de NSYS y el tope de IMEIs suben a shared para el modal de asignacion; cuandoItems y comprobar por defecto en MSW (0.9.23)"
```

---

### Task 2: El reductor pregunta a NSYS al picar y al pegar, y guarda la respuesta

**Files:**
- Modify: `src/modules/taller/asignaciones/modal/estado/tipos.ts`
- Create: `src/modules/taller/asignaciones/modal/estado/nsys.ts`
- Create: `src/modules/taller/asignaciones/modal/estado/nsys.test.ts`
- Modify: `src/modules/taller/asignaciones/modal/estado/derivados.ts` (al final)
- Modify: `src/modules/taller/asignaciones/modal/estado/reductor.ts`
- Modify: `src/modules/taller/asignaciones/modal/estado/colas.ts` (`escanear`, `pegar`)
- Modify: `src/modules/taller/asignaciones/modal/estado/pulido.ts` (`pulidoEscanear`, `pulidoPegar`)
- Test: `src/modules/taller/asignaciones/modal/estado/colas.test.ts`, `src/modules/taller/asignaciones/modal/estado/pulido.test.ts`

**Interfaces:**
- Consumes: `conEfecto(s, efecto)` de `estado/base.ts` (ya existe: añade el efecto con el siguiente id).
- Produces:
  - `EstadoModal.enNsys: Record<string, boolean | null>` (`true` lo tiene, `false` no, `null` preguntado sin respuesta; sin
    clave, no preguntado); `estadoInicial` lo deja en `{}`;
  - efecto `{ id: number; tipo: 'comprobarNsys'; imeis: string[] }`;
  - acción `{ tipo: 'NSYS_COMPROBADO'; resultado: Record<string, boolean> }`;
  - `estado/nsys.ts`: `preguntarNsys(s: EstadoModal, imeis: string[]): EstadoModal` y
    `nsysComprobado(s: EstadoModal, resultado: Record<string, boolean>): EstadoModal`;
  - `estado/derivados.ts`: `noEstaEnNsys(s: EstadoModal, imei: string): boolean` (`s.enNsys[imei] === false`).

- [ ] **Step 1: Tests que fallan de `estado/nsys.ts`**

Crea `src/modules/taller/asignaciones/modal/estado/nsys.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { noEstaEnNsys } from './derivados'
import { estado, IMEI_1, IMEI_2 } from './fabrica'
import { nsysComprobado, preguntarNsys } from './nsys'
import { reducir } from './reductor'

describe('0.9.23: pregunta a NSYS', () => {
  it('pide los IMEIs nuevos una sola vez y los apunta sin respuesta', () => {
    const r = preguntarNsys(estado(), [IMEI_1, IMEI_2, IMEI_1])
    expect(r.efectos).toEqual([{ id: 1, tipo: 'comprobarNsys', imeis: [IMEI_1, IMEI_2] }])
    expect(r.enNsys).toEqual({ [IMEI_1]: null, [IMEI_2]: null })
  })

  it('lo ya preguntado, con respuesta o sin ella, no se vuelve a pedir; sin nada nuevo no hay efecto', () => {
    const s = estado({ enNsys: { [IMEI_1]: null, [IMEI_2]: false } })
    expect(preguntarNsys(s, [IMEI_1, IMEI_2])).toBe(s)
    const vacio = estado()
    expect(preguntarNsys(vacio, [])).toBe(vacio)
  })

  it('la respuesta se fusiona (también por el reductor) y solo «false» es «no está»', () => {
    const s = nsysComprobado(estado({ enNsys: { [IMEI_1]: null, [IMEI_2]: null } }), { [IMEI_1]: false, [IMEI_2]: true })
    expect(s.enNsys).toEqual({ [IMEI_1]: false, [IMEI_2]: true })
    expect(reducir(estado(), { tipo: 'NSYS_COMPROBADO', resultado: { [IMEI_1]: false } }).enNsys).toEqual({ [IMEI_1]: false })
    expect(noEstaEnNsys(s, IMEI_1)).toBe(true)
    expect(noEstaEnNsys(s, IMEI_2)).toBe(false)
    expect(noEstaEnNsys(estado({ enNsys: { [IMEI_1]: null } }), IMEI_1)).toBe(false)
    expect(noEstaEnNsys(estado(), IMEI_1)).toBe(false)
  })
})
```

- [ ] **Step 2: Comprobar que falla**

Run: `npx vitest run src/modules/taller/asignaciones/modal/estado/nsys.test.ts`
Expected: FAIL (no existe `./nsys`).

- [ ] **Step 3: Tipos**

En `src/modules/taller/asignaciones/modal/estado/tipos.ts`:

1. En el tipo `Efecto`, añade al final de la unión (después de la línea de `'prediccion'`):

```ts
  | { id: number; tipo: 'comprobarNsys'; imeis: string[] }
```

2. En `EstadoModal`, después de `modeloPorImei: Record<string, string>`:

```ts
  /** 0.9.23: lo que contestó NSYS por IMEI mientras el modal está abierto: `true` lo tiene, `false` no, `null` preguntado sin
   *  respuesta (en camino o falló). Un IMEI sin clave no se ha preguntado. */
  enNsys: Record<string, boolean | null>
```

3. En `Accion`, después de `| { tipo: 'PULIDO_CLIENTE_BD'; seq: number; idCli: number | null }`:

```ts
  | { tipo: 'NSYS_COMPROBADO'; resultado: Record<string, boolean> }
```

4. En `estadoInicial`, la línea `clienteManual: {}, clienteDefault: null, modeloPorImei: {}, mensajeScan: null, mensajePulido: null,`
   pasa a:

```ts
    clienteManual: {}, clienteDefault: null, modeloPorImei: {}, enNsys: {}, mensajeScan: null, mensajePulido: null,
```

- [ ] **Step 4: `estado/nsys.ts`, `noEstaEnNsys` y el reductor**

Crea `src/modules/taller/asignaciones/modal/estado/nsys.ts`:

```ts
import { conEfecto } from './base'
import type { EstadoModal } from './tipos'

/** 0.9.23 (spec 2026-11-03 §2.1): pide a NSYS los IMEIs que aún no se han preguntado mientras el modal está abierto y los
 *  apunta con `null`, así una respuesta en camino no se pide dos veces. Sin IMEIs nuevos, no pide nada. */
export function preguntarNsys(s: EstadoModal, imeis: string[]): EstadoModal {
  const nuevos = [...new Set(imeis)].filter((imei) => !(imei in s.enNsys))
  if (nuevos.length === 0) return s
  const enNsys = { ...s.enNsys, ...Object.fromEntries(nuevos.map((imei) => [imei, null])) }
  return conEfecto({ ...s, enNsys }, { tipo: 'comprobarNsys', imeis: nuevos })
}

/** La respuesta de NSYS: `true` lo tiene, `false` no. Un IMEI que no viene se queda como estaba. */
export const nsysComprobado = (s: EstadoModal, resultado: Record<string, boolean>): EstadoModal =>
  ({ ...s, enNsys: { ...s.enNsys, ...resultado } })
```

Al final de `src/modules/taller/asignaciones/modal/estado/derivados.ts`:

```ts
/** 0.9.23: NSYS ha contestado que no tiene el IMEI. Sin respuesta (en camino o fallida), no. */
export const noEstaEnNsys = (s: EstadoModal, imei: string): boolean => s.enNsys[imei] === false
```

En `src/modules/taller/asignaciones/modal/estado/reductor.ts`, añade el import (después del de `./glass`):

```ts
import { nsysComprobado } from './nsys'
```

y el caso, justo antes de `case 'EFECTOS_CONSUMIDOS'`:

```ts
    case 'NSYS_COMPROBADO': return nsysComprobado(s, a.resultado)
```

- [ ] **Step 5: Comprobar que pasa**

Run: `npx vitest run src/modules/taller/asignaciones/modal/estado/nsys.test.ts`
Expected: PASS.

- [ ] **Step 6: Tests que fallan de picar y pegar (Reparación, Glass)**

En `src/modules/taller/asignaciones/modal/estado/colas.test.ts`:

1. Añade después de la línea `import { entrada, estado, filaTabla, IMEI_1, IMEI_2, IMEI_3 } from './fabrica'`:

```ts
import type { EstadoModal } from './tipos'
```

2. Cambia el primer test de `describe('escanear y pegar', …)` por:

```ts
  it('añade a la cola activa, la carga y pide el lookup de modelo y cliente y si NSYS lo conoce', () => {
    const r = escanear(estado(), IMEI_1)
    expect(r.rep[0]).toMatchObject({ seq: 1, imei: IMEI_1, tipo: 'REPARACION', modeloBuscado: true, buscando: true })
    expect(r.actual).toBe(1)
    expect(r.efectos).toEqual([
      { id: 1, tipo: 'lookup', seq: 1, imei: IMEI_1, buscarModelo: true },
      { id: 2, tipo: 'comprobarNsys', imeis: [IMEI_1] },
    ])
  })
```

3. Al final del fichero:

```ts
describe('0.9.23: pregunta a NSYS al picar y al pegar', () => {
  const pedidas = (s: EstadoModal) => s.efectos.flatMap((e) => (e.tipo === 'comprobarNsys' ? [e.imeis] : []))

  it('un repetido no vuelve a preguntar', () => {
    expect(pedidas(escanear(escanear(estado(), IMEI_1), IMEI_1))).toEqual([[IMEI_1]])
  })

  it('el mismo IMEI en Glass, o quitado y vuelto a picar, no vuelve a preguntar', () => {
    const s = escanear(estado(), IMEI_1)
    expect(pedidas(escanear(cambiarPestana(s, 'GLASS'), IMEI_1))).toEqual([[IMEI_1]])
    expect(pedidas(escanear(quitar(s, 1), IMEI_1))).toEqual([[IMEI_1]])
  })

  it('pegar pregunta una vez por todos los añadidos, sin los que ya estaban', () => {
    const s = escanear(estado(), IMEI_1)
    expect(pedidas(pegar({ ...s, actual: null }, IMEI_1 + IMEI_2 + IMEI_3))).toEqual([[IMEI_1], [IMEI_2, IMEI_3]])
  })

  it('un pegado corrupto no pregunta', () => {
    expect(pedidas(pegar(estado(), '1'.repeat(16)))).toEqual([])
  })
})
```

- [ ] **Step 7: Tests que fallan de Pulido**

En `src/modules/taller/asignaciones/modal/estado/pulido.test.ts`:

1. Añade después de la línea `import { entrada, estado, filaTabla, IMEI_1, IMEI_2 } from './fabrica'`:

```ts
import type { EstadoModal } from './tipos'
```

2. En el primer test (`'añade con el técnico de arriba, comentario vacío, lo selecciona y pide el cliente de BD'`), la línea
   `expect(r.efectos).toEqual([{ id: 1, tipo: 'clientePulido', seq: 1, imei: IMEI_1 }])` pasa a:

```ts
    expect(r.efectos).toEqual([{ id: 1, tipo: 'clientePulido', seq: 1, imei: IMEI_1 }, { id: 2, tipo: 'comprobarNsys', imeis: [IMEI_1] }])
```

3. Al final del `describe('pulido', …)` (antes de su `})` de cierre):

```ts
  it('0.9.23: pregunta a NSYS al picar y al pegar, una vez por IMEI (tampoco si ya se preguntó en otra cola)', () => {
    const pedidas = (s: EstadoModal) => s.efectos.flatMap((e) => (e.tipo === 'comprobarNsys' ? [e.imeis] : []))
    expect(pedidas(pulidoEscanear(conPulido(), IMEI_1))).toEqual([[IMEI_1]])
    expect(pedidas(pulidoPegar(conPulido(), IMEI_1 + IMEI_2))).toEqual([[IMEI_1], [IMEI_2]])
    expect(pedidas(pulidoEscanear(estado({ pestana: 'PULIDO', enNsys: { [IMEI_1]: false } }), IMEI_1))).toEqual([])
  })
```

- [ ] **Step 8: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/asignaciones/modal/estado/colas.test.ts src/modules/taller/asignaciones/modal/estado/pulido.test.ts`
Expected: FAIL en el primer test de cada fichero y en los nuevos de la 0.9.23 (falta el efecto `comprobarNsys`). El caso «un
repetido no vuelve a preguntar» también falla (`[]` en vez de `[[IMEI_1]]`).

- [ ] **Step 9: Picar y pegar preguntan a NSYS**

En `src/modules/taller/asignaciones/modal/estado/colas.ts`:

1. Añade el import después del de `./glass`:

```ts
import { preguntarNsys } from './nsys'
```

2. En `escanear`, la última línea `return cargar(r.s, r.e.seq)` pasa a:

```ts
  return preguntarNsys(cargar(r.s, r.e.seq), [imei])
```

3. En `pegar`, cambia desde `let r = s` hasta el `return` por:

```ts
  let r = s
  const nuevos: string[] = []
  let duplicados = 0
  for (const imei of p.imeis) {
    if (colaDe(r, cola).some((e) => e.imei === imei)) { duplicados++; continue }
    r = alta(r, imei, cola).s
    nuevos.push(imei)
  }
  const texto2 = `${nuevos.length} IMEIs añadidos` + (duplicados > 0 ? ` · ${duplicados} ya estaban en la lista.` : '.')
  return preguntarNsys({ ...r, mensajeScan: { texto: texto2, tono: 'ok' } }, nuevos)
```

En `src/modules/taller/asignaciones/modal/estado/pulido.ts`:

1. Añade el import después del de `./cliente`:

```ts
import { preguntarNsys } from './nsys'
```

2. En `pulidoEscanear`, la línea `return agregar({ ...s, mensajePulido: null }, imei)` pasa a:

```ts
  return preguntarNsys(agregar({ ...s, mensajePulido: null }, imei), [imei])
```

3. En `pulidoPegar`, cambia desde `let r = s` hasta el `return` por:

```ts
  let r = s
  const nuevos: string[] = []
  let dup = 0
  for (const imei of p.imeis) {
    if (r.pulido.some((f) => f.imei === imei)) { dup++; continue }
    r = agregar(r, imei)
    nuevos.push(imei)
  }
  return preguntarNsys({ ...r, mensajePulido: { texto: `${nuevos.length} IMEIs añadidos` + (dup > 0 ? ` · ${dup} ya estaban.` : '.'), tono: 'ok' } }, nuevos)
```

- [ ] **Step 10: Comprobar que pasa todo el estado del modal**

Run: `npx vitest run src/modules/taller/asignaciones/modal/estado && npx tsc -b`
Expected: PASS, sin errores de tipos.

- [ ] **Step 11: Commit**

```bash
git add src/modules/taller/asignaciones/modal/estado
git commit -m "feat: el modal de asignacion pregunta a NSYS al picar y al pegar, una vez por IMEI, y guarda la respuesta (0.9.23)"
```

---

### Task 3: El efecto llama a «Comprobar una lista de IMEIs»

**Files:**
- Modify: `src/modules/taller/asignaciones/modal/api.ts`
- Modify: `src/modules/taller/asignaciones/modal/useEfectosModal.ts`
- Test: `src/modules/taller/asignaciones/modal/api.test.tsx`, `src/modules/taller/asignaciones/modal/useEfectosModal.test.tsx`

**Interfaces:**
- Consumes: `MAX_IMEIS` de `@/shared/api/nsys` (Task 1); efecto `comprobarNsys` y acción `NSYS_COMPROBADO` (Task 2).
- Produces: `comprobarEnNsys(imeis: string[]): Promise<Record<string, boolean>>` en `modal/api.ts` (lanza si falla).

- [ ] **Step 1: Tests que fallan**

En `src/modules/taller/asignaciones/modal/api.test.tsx`, el import `import { guardarModelo, pedirClienteBd, pedirLookup, pedirPrediccion, useGuardarLote } from './api'`
pasa a:

```ts
import { comprobarEnNsys, guardarModelo, pedirClienteBd, pedirLookup, pedirPrediccion, useGuardarLote } from './api'
```

y al final del `describe('ejecutores de efectos', …)` (antes de su `})` de cierre):

```ts
  it('0.9.23: comprobarEnNsys traduce «encontrado» por IMEI y pregunta en tandas de 500', async () => {
    const tandas: string[][] = []
    server.use(http.post('*/api/stock-telefonos/comprobar', async ({ request }) => {
      const { imeis } = (await request.json()) as { imeis: string[] }
      tandas.push(imeis)
      return HttpResponse.json(imeis.map((imei) => ({ imei, encontrado: imei !== IMEI })))
    }))
    const otros = Array.from({ length: 501 }, (_, i) => String(200000000000000 + i))
    const r = await comprobarEnNsys([IMEI, ...otros])
    expect(tandas.map((t) => t.length)).toEqual([500, 2])
    expect(r[IMEI]).toBe(false)
    expect(r[otros[0]]).toBe(true)
    expect(Object.keys(r)).toHaveLength(502)
  })

  it('0.9.23: comprobarEnNsys deja pasar el error (el efecto lo calla)', async () => {
    server.use(http.post('*/api/stock-telefonos/comprobar', () => new HttpResponse(null, { status: 500 })))
    await expect(comprobarEnNsys([IMEI])).rejects.toBeTruthy()
  })
```

En `src/modules/taller/asignaciones/modal/useEfectosModal.test.tsx`, al final del `describe('useEfectosModal', …)`:

```ts
  it('0.9.23: comprobarNsys despacha lo que dice NSYS', async () => {
    server.use(http.post('*/api/stock-telefonos/comprobar', () => HttpResponse.json([{ imei: IMEI, encontrado: false }])))
    const dispatch = vi.fn()
    renderHook(() => useEfectosModal([{ id: 1, tipo: 'comprobarNsys', imeis: [IMEI] }], dispatch, new Set()))
    await waitFor(() => expect(dispatch).toHaveBeenCalledWith({ tipo: 'NSYS_COMPROBADO', resultado: { [IMEI]: false } }))
  })

  it('0.9.23: si la pregunta a NSYS falla no se despacha nada más que los consumidos', async () => {
    let pedida = false
    server.use(http.post('*/api/stock-telefonos/comprobar', () => {
      pedida = true
      return new HttpResponse(null, { status: 500 })
    }))
    const dispatch = vi.fn()
    renderHook(() => useEfectosModal([{ id: 1, tipo: 'comprobarNsys', imeis: [IMEI] }], dispatch, new Set()))
    await waitFor(() => expect(pedida).toBe(true))
    await new Promise((r) => setTimeout(r, 20))
    expect(dispatch.mock.calls.map(([a]) => a.tipo)).toEqual(['EFECTOS_CONSUMIDOS'])
  })
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/asignaciones/modal/api.test.tsx src/modules/taller/asignaciones/modal/useEfectosModal.test.tsx`
Expected: FAIL (`comprobarEnNsys` no existe; el efecto no despacha `NSYS_COMPROBADO`).

- [ ] **Step 3: `comprobarEnNsys` y el efecto**

En `src/modules/taller/asignaciones/modal/api.ts`, añade el import después del de `@/shared/api/client`:

```ts
import { MAX_IMEIS } from '@/shared/api/nsys'
```

y, después de `guardarModelo`:

```ts
/** 0.9.23 (spec 2026-11-03 §3): si NSYS conoce cada IMEI, en cualquier lote y estado («Comprobar una lista de IMEIs»), en
 *  tandas de {@link MAX_IMEIS}. Los errores se propagan: el efecto los calla. */
export async function comprobarEnNsys(imeis: string[]): Promise<Record<string, boolean>> {
  const resultado: Record<string, boolean> = {}
  for (let i = 0; i < imeis.length; i += MAX_IMEIS) {
    const { data } = await api.POST('/api/stock-telefonos/comprobar', { body: { imeis: imeis.slice(i, i + MAX_IMEIS) } })
    for (const m of data ?? []) resultado[m.imei] = m.encontrado
  }
  return resultado
}
```

En `src/modules/taller/asignaciones/modal/useEfectosModal.ts`:

1. El import `import { guardarModelo, pedirClienteBd, pedirLookup, pedirPrediccion } from './api'` pasa a:

```ts
import { comprobarEnNsys, guardarModelo, pedirClienteBd, pedirLookup, pedirPrediccion } from './api'
```

2. En el `switch (e.tipo)` de `ejecutar`, después del `case 'guardarModelo':` (sus dos líneas) y antes de `case 'prediccion':`:

```ts
        case 'comprobarNsys':
          try {
            despachar({ tipo: 'NSYS_COMPROBADO', resultado: await comprobarEnNsys(e.imeis) })
          } catch {
            // Sin respuesta no se enseña nada (spec 2026-11-03 §2.3): los IMEIs se quedan preguntados y sin marca.
          }
          return
```

- [ ] **Step 4: Comprobar que pasa**

Run: `npx vitest run src/modules/taller/asignaciones/modal/api.test.tsx src/modules/taller/asignaciones/modal/useEfectosModal.test.tsx && npx tsc -b && npm run lint`
Expected: PASS, sin errores.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/modal/api.ts src/modules/taller/asignaciones/modal/api.test.tsx src/modules/taller/asignaciones/modal/useEfectosModal.ts src/modules/taller/asignaciones/modal/useEfectosModal.test.tsx
git commit -m "feat: el efecto comprobarNsys pregunta a Comprobar una lista de IMEIs en tandas de 500 y calla los errores (0.9.23)"
```

---

### Task 4: «no está en NSYS» en la fila y en el detalle

**Files:**
- Create: `src/modules/taller/asignaciones/modal/textoNsys.ts`
- Create: `src/modules/taller/asignaciones/modal/textoNsys.test.ts`
- Create: `src/modules/taller/asignaciones/modal/MarcaNoNsys.tsx`
- Modify: `src/modules/taller/asignaciones/modal/FilaCola.tsx`
- Modify: `src/modules/taller/asignaciones/modal/PanelRico.tsx`
- Modify: `src/modules/taller/asignaciones/modal/DetalleEntrada.tsx`
- Modify: `src/modules/taller/asignaciones/modal/PanelPulido.tsx`
- Modify: `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.tsx`
- Test: `src/modules/taller/asignaciones/modal/piezas.test.tsx`, `src/modules/taller/asignaciones/modal/DetalleEntrada.test.tsx`,
  `src/modules/taller/asignaciones/modal/PanelPulido.test.tsx`, `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.test.tsx`

**Interfaces:**
- Consumes: `cuandoItems` de `@/shared/lib/telefonoNsys` y `useEstadoNsys` de `@/shared/api/nsys` (Task 1); `noEstaEnNsys` de
  `estado/derivados` y `EstadoModal.enNsys` (Task 2); el efecto real (Task 3) para el test del diálogo.
- Produces:
  - `textoNsys.ts`: `textoLineaNoNsys(ultimoItems: string | null | undefined, ahora?: Date): string` y
    `textoAyudaNoNsys(ultimoItems: string | null | undefined, ahora?: Date): string`;
  - `MarcaNoNsys.tsx`: `MarcaNoNsys({ ultimoItems })` (fila) y `LineaNoNsys({ ultimoItems })` (detalle);
  - prop opcional `ultimoItems?: string | null` en `PanelRico`, `DetalleEntrada` y `PanelPulido`; props opcionales
    `noEstaEnNsys?: boolean` y `ultimoItems?: string | null` en `FilaCola`.

- [ ] **Step 1: Tests que fallan de los textos**

Crea `src/modules/taller/asignaciones/modal/textoNsys.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { textoAyudaNoNsys, textoLineaNoNsys } from './textoNsys'

const AHORA = new Date('2026-11-03T12:00:00Z') // las 13:00 en Madrid

describe('0.9.23: textos de «no está en NSYS»', () => {
  it('la línea del detalle, con la hora del último Items o sin ella', () => {
    expect(textoLineaNoNsys('2026-11-03T13:40:00', AHORA)).toBe('No está en NSYS · último Items: hoy 13:40')
    expect(textoLineaNoNsys('2026-11-02T22:10:00', AHORA)).toBe('No está en NSYS · último Items: del 02/11 22:10')
    expect(textoLineaNoNsys(null, AHORA)).toBe('No está en NSYS')
  })

  it('la ayuda al pasar el ratón', () => {
    expect(textoAyudaNoNsys('2026-11-03T13:40:00', AHORA))
      .toBe('NSYS no tiene este IMEI en ningún lote del último Items (hoy 13:40). Al guardar saldrá en «No está en NSYS».')
    expect(textoAyudaNoNsys(undefined, AHORA))
      .toBe('NSYS no tiene este IMEI en ningún lote del último Items. Al guardar saldrá en «No está en NSYS».')
  })
})
```

- [ ] **Step 2: Tests que fallan del pintado**

En `src/modules/taller/asignaciones/modal/piezas.test.tsx`, al final del `describe('FilaCola', …)` (antes de su `})` de cierre):

```ts
  it('0.9.23: «no está en NSYS» en rojo en la segunda línea; sola en la roja y detrás de los técnicos en la verde', () => {
    const { rerender } = render(<FilaCola e={entrada({ seq: 1, imei: IMEI })} nombresTecnicos="" seleccionada={false}
      onCargar={vi.fn()} onQuitar={vi.fn()} noEstaEnNsys ultimoItems="2026-10-16T13:31:00" />)
    expect(screen.getByText('⚠ falta modelo')).toBeInTheDocument()
    const marca = screen.getByText('no está en NSYS')
    expect(marca).toHaveClass('text-texto-error')
    expect(marca.getAttribute('title')).toMatch(/^NSYS no tiene este IMEI en ningún lote del último Items \(.*13:31\)\. Al guardar saldrá en «No está en NSYS»\.$/)
    rerender(<FilaCola e={entrada({ seq: 1, imei: IMEI, asignada: true, tecnicos: [3], modelo: '14' })} nombresTecnicos="Técnico A"
      seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} noEstaEnNsys />)
    expect(screen.getByText('no está en NSYS').parentElement).toHaveTextContent('Técnico A·no está en NSYS')
    rerender(<FilaCola e={entrada({ seq: 1, imei: IMEI, modelo: '14' })} nombresTecnicos="" seleccionada={false} onCargar={vi.fn()} onQuitar={vi.fn()} />)
    expect(screen.queryByText('no está en NSYS')).not.toBeInTheDocument()
  })
```

En `src/modules/taller/asignaciones/modal/DetalleEntrada.test.tsx`, al final del `describe('DetalleEntrada', …)`:

```ts
  it('0.9.23: «No está en NSYS» con la hora del último Items bajo el IMEI; sin respuesta o si NSYS lo tiene, nada', () => {
    const con = (enNsys: boolean | null) => estado({ rep: [entrada({ seq: 1, imei: IMEI, modelo: '14' })], actual: 1, enNsys: { [IMEI]: enNsys } })
    const { rerender } = render(<DetalleEntrada estado={con(false)} dispatch={vi.fn()} tecnicos={[]} carga={[]} clientes={[]} ultimoItems="2026-10-16T13:31:00" />)
    expect(screen.getByText(/^No está en NSYS · último Items: .*13:31$/)).toHaveClass('text-texto-error')
    rerender(<DetalleEntrada estado={con(false)} dispatch={vi.fn()} tecnicos={[]} carga={[]} clientes={[]} />)
    expect(screen.getByText('No está en NSYS')).toBeInTheDocument()
    rerender(<DetalleEntrada estado={con(true)} dispatch={vi.fn()} tecnicos={[]} carga={[]} clientes={[]} />)
    expect(screen.queryByText(/No está en NSYS/)).not.toBeInTheDocument()
    rerender(<DetalleEntrada estado={con(null)} dispatch={vi.fn()} tecnicos={[]} carga={[]} clientes={[]} />)
    expect(screen.queryByText(/No está en NSYS/)).not.toBeInTheDocument()
  })
```

En `src/modules/taller/asignaciones/modal/PanelPulido.test.tsx`, el import `import { describe, expect, it } from 'vitest'` pasa a
`import { describe, expect, it, vi } from 'vitest'`, y al final del `describe('PanelPulido', …)`:

```ts
  it('0.9.23: «no está en NSYS» detrás del técnico en la fila y bajo el IMEI en el detalle', () => {
    const s = { ...estadoInicial([]), pestana: 'PULIDO' as const, seq: 1, pulidoSel: 1, enNsys: { [IMEI]: false },
      pulido: [{ seq: 1, imei: IMEI, idTec: 3, comentario: '', idCli: null, sinCliente: false }] }
    render(<PanelPulido estado={s} dispatch={vi.fn()} tecnicos={tecnicos} carga={[]} clientes={clientes} ultimoItems="2026-10-16T13:31:00" />)
    expect(screen.getByText('no está en NSYS')).toHaveClass('text-texto-error')
    expect(screen.getByText(/^No está en NSYS · último Items: .*13:31$/)).toBeInTheDocument()
  })
```

En `src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.test.tsx`, al final del `describe('AsignarTrabajosDialog', …)`:

```ts
  it('0.9.23: un IMEI que NSYS no conoce lleva «no está en NSYS» en la fila y en el detalle, y se pregunta una sola vez', async () => {
    const preguntas: string[][] = []
    server.use(http.post('*/api/stock-telefonos/comprobar', async ({ request }) => {
      const { imeis } = (await request.json()) as { imeis: string[] }
      preguntas.push(imeis)
      return HttpResponse.json(imeis.map((imei) => ({ imei, encontrado: false })))
    }))
    abrir()
    await userEvent.type(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...'), IMEI)
    expect(await screen.findByText('no está en NSYS')).toBeInTheDocument()
    // La hora sale del estado de NSYS por defecto de MSW (2026-10-16 13:31).
    expect(await screen.findByText(/^No está en NSYS · último Items: .*13:31$/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^Glass/ }))
    await userEvent.type(screen.getByPlaceholderText('Escanea o escribe el IMEI (15 dígitos)...'), IMEI)
    expect(await screen.findByText('no está en NSYS')).toBeInTheDocument()
    expect(preguntas).toEqual([[IMEI]])
  })
```

- [ ] **Step 3: Comprobar que fallan**

Run: `npx vitest run src/modules/taller/asignaciones/modal/textoNsys.test.ts src/modules/taller/asignaciones/modal/piezas.test.tsx src/modules/taller/asignaciones/modal/DetalleEntrada.test.tsx src/modules/taller/asignaciones/modal/PanelPulido.test.tsx src/modules/taller/asignaciones/modal/AsignarTrabajosDialog.test.tsx`
Expected: FAIL (no existe `./textoNsys`; no se pinta «no está en NSYS»). `npx tsc -b` también se queja de las props nuevas:
es lo esperado hasta el Step 5.

- [ ] **Step 4: Textos y componentes**

Crea `src/modules/taller/asignaciones/modal/textoNsys.ts`:

```ts
import { cuandoItems } from '@/shared/lib/telefonoNsys'

/** Textos del aviso «no está en NSYS» del modal (0.9.23, spec 2026-11-03 §2.2). `ultimoItems`: la hora (de Madrid, sin zona)
 *  de la última exportación de NSYS; sin ella, los textos no la dicen. */
export function textoLineaNoNsys(ultimoItems: string | null | undefined, ahora: Date = new Date()): string {
  return ultimoItems ? `No está en NSYS · último Items: ${cuandoItems(ultimoItems, ahora)}` : 'No está en NSYS'
}

export function textoAyudaNoNsys(ultimoItems: string | null | undefined, ahora: Date = new Date()): string {
  const items = ultimoItems ? `último Items (${cuandoItems(ultimoItems, ahora)})` : 'último Items'
  return `NSYS no tiene este IMEI en ningún lote del ${items}. Al guardar saldrá en «No está en NSYS».`
}
```

Crea `src/modules/taller/asignaciones/modal/MarcaNoNsys.tsx`:

```tsx
import { textoAyudaNoNsys, textoLineaNoNsys } from './textoNsys'

type Props = { ultimoItems?: string | null }

/** 0.9.23 (spec 2026-11-03 §2.2): el IMEI no está en NSYS, en la segunda línea de la fila de la cola. Solo informa. */
export function MarcaNoNsys({ ultimoItems }: Props) {
  return (
    <span title={textoAyudaNoNsys(ultimoItems)} className="shrink-0 whitespace-nowrap text-[10.5px] font-semibold text-texto-error">
      no está en NSYS
    </span>
  )
}

/** 0.9.23: la misma indicación en el detalle, justo bajo «IMEI en curso», con la hora del último Items. */
export function LineaNoNsys({ ultimoItems }: Props) {
  return <span title={textoAyudaNoNsys(ultimoItems)} className="-mt-1 text-[11px] text-texto-error">{textoLineaNoNsys(ultimoItems)}</span>
}
```

- [ ] **Step 5: Pintarlos en la fila, los paneles y el diálogo**

**`FilaCola.tsx`:**

1. Añade el import después de `import { cn } from '@/shared/lib/utils'`:

```ts
import { MarcaNoNsys } from './MarcaNoNsys'
```

2. La línea `type Props = { e: Entrada; nombresTecnicos: string; seleccionada: boolean; onCargar: () => void; onQuitar: () => void }`
   pasa a:

```ts
type Props = {
  e: Entrada; nombresTecnicos: string; seleccionada: boolean; onCargar: () => void; onQuitar: () => void
  /** 0.9.23: NSYS ha contestado que no tiene el IMEI; `ultimoItems`, la hora del último Items para la ayuda. */
  noEstaEnNsys?: boolean; ultimoItems?: string | null
}
```

3. La firma `export function FilaCola({ e, nombresTecnicos, seleccionada, onCargar, onQuitar }: Props) {` y la línea siguiente
   `const esGlass = e.tipo === 'GLASS'` pasan a:

```tsx
export function FilaCola({ e, nombresTecnicos, seleccionada, onCargar, onQuitar, noEstaEnNsys = false, ultimoItems }: Props) {
  const esGlass = e.tipo === 'GLASS'
  const conTecnicos = e.asignada && e.tecnicos.length > 0
```

4. El bloque de la segunda línea:

```tsx
        {e.asignada && e.tecnicos.length > 0 && (
          <div className="flex items-center gap-1.5">
            {e.auto && <span className={cn('rounded-md px-1.5 text-[9.5px] font-bold', TIPO_TRABAJO.GLASS.clases)}>auto</span>}
            <span className="text-[10.5px] text-azul-gris">{nombresTecnicos}</span>
          </div>
        )}
```

pasa a:

```tsx
        {(conTecnicos || noEstaEnNsys) && (
          <div className="flex items-center gap-1.5">
            {conTecnicos && e.auto && <span className={cn('rounded-md px-1.5 text-[9.5px] font-bold', TIPO_TRABAJO.GLASS.clases)}>auto</span>}
            {conTecnicos && <span className="text-[10.5px] text-azul-gris">{nombresTecnicos}</span>}
            {conTecnicos && noEstaEnNsys && <span className="text-[10.5px] text-azul-gris">·</span>}
            {/* 0.9.23: en la segunda línea porque la cola mide 300 px y junto a «⚠ falta modelo» no cabe en la primera. */}
            {noEstaEnNsys && <MarcaNoNsys ultimoItems={ultimoItems} />}
          </div>
        )}
```

**`PanelRico.tsx`:**

1. `import { filasCola, imeiEnColaActiva } from './estado/derivados'` pasa a
   `import { filasCola, imeiEnColaActiva, noEstaEnNsys } from './estado/derivados'`.
2. La línea `type Props = { estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[] }`
   pasa a:

```ts
type Props = {
  estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[]
  /** 0.9.23: hora del último Items de NSYS, para «no está en NSYS». */
  ultimoItems?: string | null
}
```

3. En `const fila = (e: Entrada) => (…)`, el `<FilaCola …>` pasa a:

```tsx
    <FilaCola key={e.seq} e={e} nombresTecnicos={nombres(e)} seleccionada={e.seq === estado.actual}
      noEstaEnNsys={noEstaEnNsys(estado, e.imei)} ultimoItems={props.ultimoItems}
      onCargar={() => dispatch({ tipo: 'CARGAR', seq: e.seq })} onQuitar={() => dispatch({ tipo: 'QUITAR', seq: e.seq })} />
```

(`<DetalleEntrada {...props} />` ya le pasa `ultimoItems`.)

**`DetalleEntrada.tsx`:**

1. Añade el import después de `import { ListaTecnicos } from './ListaTecnicos'`:

```ts
import { LineaNoNsys } from './MarcaNoNsys'
```

2. `import { asignarHabilitado, entradaActual, glassAbiertaBd, promptModelo, tecnicosOcupados } from './estado/derivados'` pasa a
   `import { asignarHabilitado, entradaActual, glassAbiertaBd, noEstaEnNsys, promptModelo, tecnicosOcupados } from './estado/derivados'`.
3. La línea `type Props = { estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[] }`
   pasa a:

```ts
type Props = {
  estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[]
  /** 0.9.23: hora del último Items de NSYS, para «No está en NSYS». */
  ultimoItems?: string | null
}
```

4. `export function DetalleEntrada({ estado, dispatch, tecnicos, carga, clientes }: Props) {` pasa a
   `export function DetalleEntrada({ estado, dispatch, tecnicos, carga, clientes, ultimoItems }: Props) {`.
5. Justo después de `<span data-testid="imei-en-curso" className="font-mono text-[18px] font-bold text-azul-medio">{e?.imei ?? '—'}</span>`:

```tsx
        {e && noEstaEnNsys(estado, e.imei) && <LineaNoNsys ultimoItems={ultimoItems} />}
```

**`PanelPulido.tsx`:**

1. Añade los imports después de `import { MensajeEscaneo } from './MensajeEscaneo'`:

```ts
import { LineaNoNsys, MarcaNoNsys } from './MarcaNoNsys'
import { noEstaEnNsys } from './estado/derivados'
```

2. La línea `type Props = { estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[] }`
   pasa a:

```ts
type Props = {
  estado: EstadoModal; dispatch: Dispatch<Accion>; tecnicos: Tecnico[]; carga: FilaCarga[]; clientes: Cliente[]
  /** 0.9.23: hora del último Items de NSYS, para «no está en NSYS». */
  ultimoItems?: string | null
}
```

3. `export function PanelPulido({ estado, dispatch, tecnicos, carga, clientes }: Props) {` pasa a
   `export function PanelPulido({ estado, dispatch, tecnicos, carga, clientes, ultimoItems }: Props) {`.
4. En la fila de la lista, el bloque:

```tsx
                {f.idTec == null
                  ? <span className="text-[11px] font-bold text-cola-roja">(sin técnico) · {nombreCli(f)}</span>
                  : <span className="text-[11px] text-azul-gris">{nombreTec(f.idTec)} · {nombreCli(f)}</span>}
```

pasa a:

```tsx
                <div className="flex items-center gap-1.5">
                  {f.idTec == null
                    ? <span className="text-[11px] font-bold text-cola-roja">(sin técnico) · {nombreCli(f)}</span>
                    : <span className="text-[11px] text-azul-gris">{nombreTec(f.idTec)} · {nombreCli(f)}</span>}
                  {noEstaEnNsys(estado, f.imei) && (
                    <><span className="text-[11px] text-azul-gris">·</span><MarcaNoNsys ultimoItems={ultimoItems} /></>
                  )}
                </div>
```

5. Justo después de `<span data-testid="imei-pulido" className="font-mono text-[18px] font-bold text-azul-medio">{sel?.imei ?? '—'}</span>`:

```tsx
            {sel && noEstaEnNsys(estado, sel.imei) && <LineaNoNsys ultimoItems={ultimoItems} />}
```

**`AsignarTrabajosDialog.tsx`:**

1. Añade el import después de `import { useClientes } from '@/shared/api/clientes'`:

```ts
import { useEstadoNsys } from '@/shared/api/nsys'
```

2. Después de `const { data: clientes = [], isPending: cargandoClientes } = useClientes()`:

```ts
  // 0.9.23: la hora del último Items para «no está en NSYS» (la misma consulta que el indicador de la barra superior).
  const { data: estadoNsys } = useEstadoNsys()
```

3. La línea `const panel = { estado, dispatch, tecnicos, carga: carga?.pedidos ?? [], clientes }` pasa a:

```ts
  const panel = { estado, dispatch, tecnicos, carga: carga?.pedidos ?? [], clientes, ultimoItems: estadoNsys?.ultimaExportacion ?? null }
```

- [ ] **Step 6: Comprobar que pasa todo el modal**

Run: `npx vitest run src/modules/taller/asignaciones && npx tsc -b && npm run lint`
Expected: PASS, sin errores de tipos ni de lint.

- [ ] **Step 7: Commit**

```bash
git add src/modules/taller/asignaciones/modal
git commit -m "feat: no esta en NSYS en rojo en la fila y en el detalle del modal de asignacion, con la hora del ultimo Items (0.9.23)"
```

---

### Task 5: E2E de solo lectura en preprod

Pica en el modal un IMEI inventado (NSYS no lo conoce) y uno real del espejo, mira las marcas y cierra con «Descartar». No
guarda nada, así que no hay que limpiar.

**Files:**
- Create: `tests/e2e/aviso-no-nsys.spec.ts`

**Interfaces:**
- Consumes: la app desplegada con las Tasks 1-4; `credenciales` de `tests/e2e/credenciales.ts`; cuenta `E2E_USER`/`E2E_PASS`
  (supertécnico) de `~/.env.e2e`.

- [ ] **Step 1: La E2E**

Crea `tests/e2e/aviso-no-nsys.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// aviso-no-nsys.spec.ts (0.9.23, spec 2026-11-03-v0923-aviso-no-esta-en-nsys-design.md): al picar un IMEI en «Asignar
// trabajos», el modal dice «no está en NSYS» si NSYS no lo conoce, y nada si lo conoce. Solo LEE: pica un IMEI inventado y uno
// real del espejo (el primero del stock), mira las marcas y cierra el modal con «Descartar»; no se guarda nada. Como todas las
// E2E, solo contra PREPRODUCCIÓN: se niega a seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

/** Petición con el token de la sesión de la página (patrón de stock-telefonos.spec.ts y revisar-a-mano.spec.ts). */
async function pedir<T>(page: Page, metodo: 'GET' | 'POST', ruta: string, cuerpo?: unknown): Promise<T> {
  return page.evaluate(async ({ metodo, ruta, cuerpo }) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const res = await fetch(ruta, {
      method: metodo,
      headers: { Authorization: `Bearer ${sesion.token}`, 'Content-Type': 'application/json' },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    if (!res.ok) throw new Error(`${metodo} ${ruta} respondió ${res.status}`)
    return res.json()
  }, { metodo, ruta, cuerpo }) as Promise<T>
}

test('el modal de asignación avisa de un IMEI que NSYS no conoce y no de uno que sí', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page).toHaveURL(/\/reparaciones\/asignaciones$/)
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)

  // Uno real: el primero del stock con un IMEI de 15 cifras (el campo de escaneo solo admite cifras).
  const stock = await pedir<{ imeiSn: string }[]>(page, 'GET', '/api/stock-telefonos')
  const real = stock.map((m) => m.imeiSn).find((i) => /^\d{15}$/.test(i))
  test.skip(!real, 'El stock de preprod no tiene ningún IMEI de 15 cifras')
  // Uno inventado, distinto en cada pasada; se comprueba antes que NSYS no lo conoce.
  const inventado = '99' + String(Date.now()).slice(-13)
  const [comprobado] = await pedir<{ imei: string; encontrado: boolean }[]>(page, 'POST', '/api/stock-telefonos/comprobar', { imeis: [inventado] })
  expect(comprobado.encontrado, `el IMEI inventado ${inventado} no debería estar en NSYS`).toBe(false)

  await page.getByRole('button', { name: 'Asignar' }).click()
  const modal = page.getByRole('dialog', { name: 'Asignar trabajos' })
  const campo = modal.getByPlaceholder('Escanea o escribe el IMEI (15 dígitos)...')

  await campo.fill(inventado)
  await expect(modal.getByTestId('imei-en-curso')).toHaveText(inventado)
  await expect(modal.getByText(/^No está en NSYS/)).toBeVisible()
  await expect(modal.getByText('no está en NSYS', { exact: true })).toHaveCount(1)

  // La respuesta de NSYS del real se espera antes de mirar que no hay marca (registrada ANTES de picarlo).
  const respuestaReal = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/stock-telefonos/comprobar'
    && (r.request().postData() ?? '').includes(real!))
  await campo.fill(real!)
  await expect(modal.getByTestId('imei-en-curso')).toHaveText(real!)
  expect((await respuestaReal).ok()).toBe(true)
  await expect(modal.getByText(/^No está en NSYS/)).toHaveCount(0)
  await expect(modal.getByText('no está en NSYS', { exact: true })).toHaveCount(1) // la del inventado, que sigue en la cola

  // Cerrar sin guardar: Escape pide confirmación y «Descartar» cierra el modal.
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Descartar' }).click()
  await expect(modal).toBeHidden()
})
```

- [ ] **Step 2: Comprobar que se lista y compila**

Run: `npx tsc -b && npm run lint && npx playwright test --list tests/e2e/aviso-no-nsys.spec.ts`
Expected: sin errores; la lista enseña `aviso-no-nsys.spec.ts › el modal de asignación avisa de un IMEI que NSYS no conoce y no de uno que sí`.
(Se ejecuta de verdad en la Task 7, contra preprod.)

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/aviso-no-nsys.spec.ts
git commit -m "test: E2E de solo lectura del aviso no esta en NSYS del modal de asignacion (0.9.23)"
```

---

### Task 6: Versión 0.9.23, CHANGELOG y novedades

**Files:**
- Modify: `package.json`, `package-lock.json` (versión)
- Modify: `CHANGELOG.md` (entrada nueva arriba)
- Create (raíz): `/c/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.23.md`

- [ ] **Step 1: Versión**

```bash
cd /c/Users/dev/Documents/wt/web-0923-aviso && npm version 0.9.23 --no-git-tag-version && grep -n '"version"' package.json && grep -n -m2 '"version": "0.9.23"' package-lock.json
```

Expected: `"version": "0.9.23"` en `package.json` y en las dos entradas raíz de `package-lock.json`.

- [ ] **Step 2: CHANGELOG**

En `CHANGELOG.md`, justo antes de `## [0.9.22.1] - (pendiente) — Las plantillas de NSYS se descargan`:

```markdown
## [0.9.23] - (pendiente) — El modal de asignación avisa si el IMEI no está en NSYS

- **«Asignar trabajos»** (supertécnicos y administrador): al picar o pegar un IMEI en Reparación, Glass o Pulido, si NSYS no lo tiene en ningún lote, la fila dice **«no está en NSYS»** en rojo y el detalle, bajo el IMEI, **«No está en NSYS · último Items: hoy 13:40»**. Así se puede dar de alta en NSYS en el momento, con el móvil en la mano. Solo informa: no bloquea ni cambia lo que se guarda, y al guardar el móvil sale en la tarjeta «No está en NSYS» como hasta ahora. Un vendido o de baja no avisa (NSYS lo conoce). Si la consulta falla, no se enseña nada.
- Solo web: el servidor sigue en la 0.9.22. Sin migración.

```

- [ ] **Step 3: Comprobación completa y build**

Run: `npm run check && npm run build`
Expected: lint, tipos y todos los tests en verde; el build termina sin errores.

- [ ] **Step 4: Commit de la web**

```bash
git add package.json package-lock.json CHANGELOG.md
git commit -m "docs: version 0.9.23 y changelog del aviso no esta en NSYS del modal de asignacion"
```

- [ ] **Step 5: Novedades para el taller (raíz)**

Crea `/c/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.23.md`:

```markdown
# Novedades de la versión 0.9.23

---

## 🔧 Asignar trabajos (supertécnicos y administrador)

- Al picar un IMEI, si **NSYS no lo tiene**, la fila dice **«no está en NSYS»** en rojo y, al abrirlo, debajo del IMEI sale
  **«No está en NSYS · último Items: hoy 13:40»**. Es el momento de darlo de alta en NSYS: tienes el móvil en la mano y sabes
  exactamente cómo es.
- Es solo un aviso: se puede asignar y guardar igual. Después sale en «Stock de teléfonos → Por pasar a NSYS → No está en NSYS»
  hasta que NSYS lo tenga.
- Si alguien acaba de darlo de alta en NSYS, el aviso puede seguir saliendo hasta que llegue el siguiente Items (mira la hora).
- Un móvil vendido o de baja en NSYS no avisa: NSYS ya lo conoce.
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.23.md && git commit -m "docs: novedades de la 0.9.23 (aviso no esta en NSYS al picar en el modal de asignacion)"
```

---

### Task 7: Entrega (preprod, producción y merge)

**Files:** guion en `Apuntes/preprod/v0923-preprod.md` y registro en `Apuntes/despliegue_preprod.md` y
`Apuntes/despliegue_vdc_produccion.md` (fuera de los repos).

**Requisitos:** las Tasks 1-6 hechas y revisadas. Merge, push, tags y gitlinks **solo con el OK del usuario**. Claude **no**
hace SSH: prepara los comandos y el usuario los ejecuta (cada bloque empieza con `hostname` y, en producción, el seguro de
`backup-erp.sh`).

- [ ] **Step 1: Avisar al usuario** de que la rama `dev_v0923_aviso_nsys` está lista (commits y `npm run check` en verde) y de
  lo que falta: push de la rama, despliegue en preprod, E2E, prueba a mano, producción en horario, merge y gitlink.
- [ ] **Step 2: Preprod** (con su OK, el usuario despliega la rama siguiendo `Apuntes/despliegue_preprod.md`): `/version.json`
  da `0.9.23`. Desde el PC, con las cuentas de `~/.env.e2e` y su `E2E_BASE_URL` de preprod:

```bash
cd /c/Users/dev/Documents/wt/web-0923-aviso && set -a && . ~/.env.e2e && set +a && npx playwright test tests/e2e/aviso-no-nsys.spec.ts tests/e2e/asignar.spec.ts
```

  Expected: 2 passed. Si da el 503 del límite de inicios de sesión, esperar un minuto y repetir solo ese fichero.
- [ ] **Step 3: Prueba a mano en preprod con el usuario:** picar un IMEI inventado en Reparación (sale la marca y la línea
  con la hora), uno real (nada), pegar varios, la pestaña Pulido, asignar uno que no está (la marca sigue en la verde) y pasar el
  ratón por la marca.
- [ ] **Step 4: Producción en horario** (solo web, sin migración, con su OK): guion con `hostname` + `backup-erp.sh`
  según `Apuntes/despliegue_vdc_produccion.md`; después, `/version.json` da `0.9.23`.
- [ ] **Step 5: Merge y gitlink** (con su OK): `git fetch` y comparar `main` con `origin/main` en la web y en la raíz; merge
  `--no-ff` de `dev_v0923_aviso_nsys` a `main` de la web; fecha de la `[0.9.23]` en el CHANGELOG; gitlink de la web en la raíz.
  El tag `v0.9.23`, cuando el usuario lo diga.
- [ ] **Step 6: Registro:** resultado en `Apuntes/despliegue_preprod.md` y `Apuntes/despliegue_vdc_produccion.md`, y la memoria
  `project_v0923_aviso_no_nsys` al día.
