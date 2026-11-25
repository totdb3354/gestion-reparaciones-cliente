# Pedidos: IMEI en la celda y vista filtrada fija — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que picar IMEIs en Pedidos no se atasque: el campo va dentro de la celda (sin desplegable que tape filas), una lectura no
válida queda en rojo sin frenar, y la vista filtrada no pierde filas mientras se pica.

**Architecture:** solo web (`gestion-reparaciones-web`). `CeldaImei` deja el `Popover` de celda y pinta un `CampoImei` en línea; los
recomendados pasan a un `Popover` anclado a un ▾. Las lecturas no válidas viven en `estadoPedidos.noValidos` y se copian en
`localStorage` por usuario (módulo `noValidos.ts`). La vista fija es una función pura acumulativa en `filas.ts` que `PedidosPage`
reinicia al cambiar filtros, periodo, búsqueda, «Ver» o al pulsar un contador.

**Tech Stack:** React 19 + TypeScript, TanStack Query, Radix Popover (`@/shared/ui/popover`), Vitest + Testing Library + MSW.

**Spec:** `docs/superpowers/specs/2026-11-25-pedidos-imei-en-celda-design.md` (prototipo en `specs/assets/2026-11-25-pedidos-imei-en-celda-prototipo.html`).

## Global Constraints

- Solo web; ninguna API ni migración. El servidor no cambia.
- Textos exactos: «IMEI no válido · …1234» (4 últimas cifras) / «IMEI no válido · 16 cifras»; título largo «IMEI no válido: <imei> (la
  cifra de control no cuadra). No se ha guardado.» / «IMEI no válido: <n> cifras (<imei>). No se ha guardado.»; botón «Guardar
  igual»; ▾ con nombre accesible «Ver recomendados»; lista con `aria-label="Recomendados"`; error de pocas cifras «Un IMEI son 15 cifras».
- `localStorage` clave `pedidos.imeiNoValidos.<nombreUsuario>`, caducidad 24 h.
- El campo sigue siendo `role=textbox` con nombre «IMEI»; la celda en reposo sigue siendo un botón con su texto de hoy («pulsa para
  poner el IMEI…» o el IMEI): así la mayoría de pruebas existentes siguen valiendo.
- Commits sin línea `Co-Authored-By` (preferencia del usuario). Rama `dev_pedidos_imei_celda` en el submódulo web. Merge a main,
  push y tags solo con confirmación del usuario.
- Comandos desde `gestion-reparaciones-web/`: `npx vitest run <fichero>`, `npm run check` (lint + typecheck + test).
- Prueba conocida rota de antes (no es de este trabajo): PedidosFiltros «buscador <2 caracteres» (urls length 1 but got 2).

## Ficheros

| Fichero | Qué |
|---|---|
| `src/modules/telefonos/pedidos/noValidos.ts` (nuevo) | Lecturas no válidas: tipo, textos, leer/escribir `localStorage`, marcar/quitar/limpiar en el store |
| `src/modules/telefonos/pedidos/noValidos.test.ts` (nuevo) | Sus pruebas |
| `src/modules/telefonos/pedidos/estado.ts` | Campo `noValidos` |
| `src/modules/telefonos/pedidos/filas.ts` | `VistaFija`, `vistaVacia`, `acumularVista` |
| `src/modules/telefonos/pedidos/filas.test.ts` | Pruebas de la vista fija |
| `src/modules/telefonos/pedidos/CeldaImei.tsx` | Celda en línea: `CampoImei`, `ListaRecomendados`; fuera `Desplegable`, marca y validar |
| `src/modules/telefonos/pedidos/TablaFilas.tsx` | Pasa `noValido`; el salto evita las filas en rojo |
| `src/modules/telefonos/pedidos/PedidosPage.tsx` | Vista fija, carga y limpieza de no válidos, Escape del panel |
| `src/modules/telefonos/pedidos/TablaFilas.celda.test.tsx` (nuevo) | Pruebas de la celda nueva |
| `src/modules/telefonos/pedidos/PedidosVistaFija.test.tsx` (nuevo) | Pruebas de la vista fija en la página |
| `src/modules/telefonos/pedidos/AyudaPedidos.tsx` + `PedidosAyuda.test.tsx` | Textos de la ayuda |
| Pruebas existentes de `pedidos/` | Migrar lo que usaba el desplegable (Task 5) |

---

### Task 0: Rama

- [ ] **Step 1:** desde `gestion-reparaciones-web/`:

```bash
git fetch && git status --short && git checkout main && git pull --ff-only && git checkout -b dev_pedidos_imei_celda
```

Si `git status` enseña cambios que no son tuyos, para y pregunta (puede haber otra sesión trabajando).

---

### Task 1: Lecturas no válidas (`noValidos.ts` + store)

**Files:**
- Create: `src/modules/telefonos/pedidos/noValidos.ts`, `src/modules/telefonos/pedidos/noValidos.test.ts`
- Modify: `src/modules/telefonos/pedidos/estado.ts`

**Interfaces:**
- Produces: `type MotivoNoValido = 'luhn' | 'mas'`; `type NoValido = { imei: string; motivo: MotivoNoValido; imeiFila: string | null; en: number }`;
  `CADUCIDAD_NO_VALIDO_MS`; `leerNoValidos(ahora?): Record<number, NoValido>`; `cargarNoValidos(): void`;
  `marcarNoValido(idFila: number, nv: Omit<NoValido, 'en'>, ahora?: number): void`; `quitarNoValido(idFila: number): void`;
  `limpiarNoValidos(filas: FilaVista[]): void`; `textoNoValido(nv: NoValido): { corto: string; largo: string }`;
  `EstadoPedidos.noValidos: Record<number, NoValido>`.

- [ ] **Step 1: Pruebas que fallan** — `noValidos.test.ts`:

```ts
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '@/shared/session/storage'
import { SESION_SUPER } from '@/test/render'
import { fila } from '../test/fabrica'
import { estadoPedidos } from './estado'
import { CADUCIDAD_NO_VALIDO_MS, cargarNoValidos, leerNoValidos, limpiarNoValidos, marcarNoValido, quitarNoValido, textoNoValido } from './noValidos'

const CLAVE = `pedidos.imeiNoValidos.${SESION_SUPER.nombreUsuario}`

beforeEach(() => { localStorage.clear(); estadoPedidos.reset(); guardarSesion(SESION_SUPER) })

describe('noValidos', () => {
  it('marcar lo pone en el store y en localStorage del usuario; quitar lo borra de los dos', () => {
    marcarNoValido(7, { imei: '350000000000017', motivo: 'luhn', imeiFila: null }, 1000)
    expect(estadoPedidos.get().noValidos[7]).toEqual({ imei: '350000000000017', motivo: 'luhn', imeiFila: null, en: 1000 })
    expect(JSON.parse(localStorage.getItem(CLAVE)!)).toEqual({ 7: { imei: '350000000000017', motivo: 'luhn', imeiFila: null, en: 1000 } })
    quitarNoValido(7)
    expect(estadoPedidos.get().noValidos).toEqual({})
    expect(localStorage.getItem(CLAVE)).toBeNull()
  })

  it('al cargar se descartan las de más de 24 h, las mal formadas y las de otro usuario', () => {
    const ahora = 10 * CADUCIDAD_NO_VALIDO_MS
    localStorage.setItem(CLAVE, JSON.stringify({
      1: { imei: '3500000000000171', motivo: 'mas', imeiFila: null, en: ahora - 1000 },
      2: { imei: '350000000000017', motivo: 'luhn', imeiFila: null, en: ahora - CADUCIDAD_NO_VALIDO_MS - 1 },
      3: { imei: 5, motivo: 'luhn', en: ahora },
    }))
    localStorage.setItem('pedidos.imeiNoValidos.otro', JSON.stringify({ 4: { imei: '350000000000017', motivo: 'luhn', imeiFila: null, en: ahora } }))
    expect(Object.keys(leerNoValidos(ahora))).toEqual(['1'])
  })

  it('cargarNoValidos lleva lo guardado al store; sin sesión no lee nada', () => {
    localStorage.setItem(CLAVE, JSON.stringify({ 5: { imei: '350000000000017', motivo: 'luhn', imeiFila: null, en: Date.now() } }))
    cargarNoValidos()
    expect(Object.keys(estadoPedidos.get().noValidos)).toEqual(['5'])
    localStorage.clear()
    expect(leerNoValidos()).toEqual({})
  })

  it('limpiar quita las de filas cuyo IMEI ya no es el que tenían al leer; las que no llegan se quedan', () => {
    marcarNoValido(1, { imei: '350000000000017', motivo: 'luhn', imeiFila: null })
    marcarNoValido(2, { imei: '350000000000017', motivo: 'luhn', imeiFila: null })
    marcarNoValido(3, { imei: '350000000000017', motivo: 'luhn', imeiFila: '350000000000025' })
    limpiarNoValidos([fila({ idFila: 1, imei: '350000000000033' }), fila({ idFila: 3, imei: '350000000000025' })])
    expect(Object.keys(estadoPedidos.get().noValidos).sort()).toEqual(['2', '3'])
  })

  it('textos corto y largo', () => {
    expect(textoNoValido({ imei: '350000000006537', motivo: 'luhn', imeiFila: null, en: 0 })).toEqual({
      corto: 'IMEI no válido · …6537', largo: 'IMEI no válido: 350000000006537 (la cifra de control no cuadra). No se ha guardado.' })
    expect(textoNoValido({ imei: '3500000000065371', motivo: 'mas', imeiFila: null, en: 0 })).toEqual({
      corto: 'IMEI no válido · 16 cifras', largo: 'IMEI no válido: 16 cifras (3500000000065371). No se ha guardado.' })
  })
})
```

- [ ] **Step 2:** `npx vitest run src/modules/telefonos/pedidos/noValidos.test.ts` → FALLA (no existe el módulo).

- [ ] **Step 3: Implementación.** En `estado.ts`, añadir al tipo (tras `fallosImei`) y al valor inicial:

```ts
import type { NoValido } from './noValidos'
// … en EstadoPedidos:
  /** Lecturas que no eran un IMEI válido (cifra de control mala o más de 15 cifras), por `idFila`: no se guardaron y la celda sigue en
   *  rojo hasta poner un IMEI en esa fila. Copia en localStorage por usuario (24 h): ver `noValidos.ts`. */
  noValidos: Record<number, NoValido>
// … en crearStore: añadir `noValidos: {},` tras `fallosImei: {},`
```

`noValidos.ts`:

```ts
import type { FilaVista } from '@/shared/api/client'
import { leerSesion } from '@/shared/session/storage'
import { estadoPedidos } from './estado'

/** Por qué no es un IMEI válido: la cifra de control (Luhn) no cuadra, o hay más de 15 cifras. */
export type MotivoNoValido = 'luhn' | 'mas'
/** Una lectura que no era un IMEI válido (spec 2026-11-25 §2): no se guardó y la celda queda en rojo. `imeiFila` es el IMEI que tenía la
 *  fila al leer: en cuanto la fila tenga otro (lo pone quien sea), la marca sobra. `en`: cuándo, en ms (caduca a las 24 h). */
export type NoValido = { imei: string; motivo: MotivoNoValido; imeiFila: string | null; en: number }

export const CADUCIDAD_NO_VALIDO_MS = 24 * 60 * 60 * 1000
const PREFIJO = 'pedidos.imeiNoValidos.'

/** Por usuario: en un PC compartido no salen los rojos de otro. Sin sesión, no se lee ni escribe nada. */
function clave(): string | null {
  const usuario = leerSesion()?.nombreUsuario
  return usuario ? PREFIJO + usuario : null
}

/** Lo guardado en este navegador para el usuario de la sesión, sin lo caducado ni lo mal formado. */
export function leerNoValidos(ahora = Date.now()): Record<number, NoValido> {
  const k = clave()
  if (!k) return {}
  try {
    const crudo = JSON.parse(localStorage.getItem(k) ?? '{}') as Record<string, Partial<NoValido>>
    const vivos: Record<number, NoValido> = {}
    for (const [id, nv] of Object.entries(crudo)) {
      if (!nv || typeof nv.imei !== 'string' || (nv.motivo !== 'luhn' && nv.motivo !== 'mas') || typeof nv.en !== 'number') continue
      if (ahora - nv.en >= CADUCIDAD_NO_VALIDO_MS) continue
      vivos[Number(id)] = { imei: nv.imei, motivo: nv.motivo, imeiFila: typeof nv.imeiFila === 'string' ? nv.imeiFila : null, en: nv.en }
    }
    return vivos
  } catch {
    return {}
  }
}

function escribir(m: Record<number, NoValido>): void {
  const k = clave()
  if (!k) return
  try {
    if (Object.keys(m).length === 0) localStorage.removeItem(k)
    else localStorage.setItem(k, JSON.stringify(m))
  } catch {
    // localStorage lleno o bloqueado: la marca queda solo en memoria hasta recargar.
  }
}

/** Al entrar en Pedidos: lo guardado pasa al store. */
export function cargarNoValidos(): void {
  estadoPedidos.set((s) => ({ ...s, noValidos: leerNoValidos() }))
}

export function marcarNoValido(idFila: number, nv: Omit<NoValido, 'en'>, ahora = Date.now()): void {
  estadoPedidos.set((s) => {
    const m = { ...s.noValidos, [idFila]: { ...nv, en: ahora } }
    escribir(m)
    return { ...s, noValidos: m }
  })
}

export function quitarNoValido(idFila: number): void {
  estadoPedidos.set((s) => {
    if (!(idFila in s.noValidos)) return s
    const m = { ...s.noValidos }
    delete m[idFila]
    escribir(m)
    return { ...s, noValidos: m }
  })
}

/** Con las filas recibidas: quita las marcas de filas cuyo IMEI ya no es el que tenían al leer (alguien puso uno). Las filas que no
 *  llegan (otro periodo) conservan la marca hasta que caduque. */
export function limpiarNoValidos(filas: FilaVista[]): void {
  const actuales = estadoPedidos.get().noValidos
  if (Object.keys(actuales).length === 0) return
  const porId = new Map(filas.map((f) => [f.idFila, f]))
  const sobran = Object.keys(actuales).map(Number).filter((id) => {
    const f = porId.get(id)
    return f !== undefined && (f.imei ?? null) !== actuales[id].imeiFila
  })
  if (sobran.length === 0) return
  estadoPedidos.set((s) => {
    const m = { ...s.noValidos }
    for (const id of sobran) delete m[id]
    escribir(m)
    return { ...s, noValidos: m }
  })
}

export function textoNoValido(nv: NoValido): { corto: string; largo: string } {
  return nv.motivo === 'mas'
    ? { corto: `IMEI no válido · ${nv.imei.length} cifras`, largo: `IMEI no válido: ${nv.imei.length} cifras (${nv.imei}). No se ha guardado.` }
    : { corto: `IMEI no válido · …${nv.imei.slice(-4)}`, largo: `IMEI no válido: ${nv.imei} (la cifra de control no cuadra). No se ha guardado.` }
}
```

- [ ] **Step 4:** `npx vitest run src/modules/telefonos/pedidos/noValidos.test.ts` → PASA. `npm run typecheck` → sin errores.
- [ ] **Step 5: Commit** — `git add -A src/modules/telefonos/pedidos && git commit -m "feat(pedidos): lecturas de IMEI no válidas en el store y en localStorage por usuario"`

---

### Task 2: Vista fija (función pura + página)

**Files:**
- Modify: `src/modules/telefonos/pedidos/filas.ts`, `filas.test.ts`, `PedidosPage.tsx`
- Create: `src/modules/telefonos/pedidos/PedidosVistaFija.test.tsx`

**Interfaces:**
- Produces: `type VistaFija = { ids: Set<number>; conocidas: Set<number> }`; `vistaVacia(): VistaFija`;
  `acumularVista(v: VistaFija, filas: FilaVista[], filtros: FiltrosColumna, hoy: string): FilaVista[]` (muta `v`).

- [ ] **Step 1: Pruebas unitarias que fallan** — al final de `filas.test.ts` (añadir `acumularVista, vistaVacia, SIN_FILTROS` a su import de `./filas` si no están):

```ts
describe('acumularVista (vista fija como en Excel)', () => {
  const sin = { ...SIN_FILTROS, soloSinImei: true }
  const HOY = '2026-11-25'
  it('la que deja de cumplir se queda; la nueva que cumple entra; la nueva que no cumple no; la que deja de llegar sale', () => {
    const v = vistaVacia()
    expect(acumularVista(v, [fila({ idFila: 1 }), fila({ idFila: 2, imei: '350000000000017' })], sin, HOY).map((f) => f.idFila)).toEqual([1])
    const ahora = [fila({ idFila: 1, imei: '350000000000025' }), fila({ idFila: 2 }), fila({ idFila: 3 }), fila({ idFila: 4, imei: '350000000000033' })]
    // 1 ya tiene IMEI pero se queda; 2 ya no tiene IMEI pero era conocida: no entra; 3 es nueva y cumple; 4 es nueva y no cumple.
    expect(acumularVista(v, ahora, sin, HOY).map((f) => f.idFila)).toEqual([1, 3])
    expect(acumularVista(v, [fila({ idFila: 3 })], sin, HOY).map((f) => f.idFila)).toEqual([3])
  })
  it('sin filtros de columna es todo lo recibido', () => {
    expect(acumularVista(vistaVacia(), [fila({ idFila: 1 }), fila({ idFila: 2, imei: '350000000000017' })], SIN_FILTROS, HOY)).toHaveLength(2)
  })
  it('respeta el orden de las filas recibidas', () => {
    const v = vistaVacia()
    acumularVista(v, [fila({ idFila: 1 }), fila({ idFila: 2 })], sin, HOY)
    expect(acumularVista(v, [fila({ idFila: 2 }), fila({ idFila: 1 })], sin, HOY).map((f) => f.idFila)).toEqual([2, 1])
  })
})
```

(`fila` es `import { fila } from '../test/fabrica'`; si `filas.test.ts` usa otro nombre para la fábrica, usar ese.)

- [ ] **Step 2:** `npx vitest run src/modules/telefonos/pedidos/filas.test.ts` → FALLA.

- [ ] **Step 3: Implementación** — en `filas.ts`, justo después de `filtrarColumnas`:

```ts
/** La vista de la tabla con filtros (spec 2026-11-25 §4, como en Excel): las filas que cumplían al fijarla se quedan aunque dejen de
 *  cumplir (por un IMEI puesto aquí o por el sondeo); de las que llegan después (un `idFila` que no se conocía) entran las que cumplen.
 *  `VistaFija` es un acumulador: `PedidosPage` crea uno vacío (`vistaVacia`) al cambiar filtros, periodo, búsqueda o «Ver», o al pulsar
 *  un contador. Devuelve las filas recibidas que están en la vista, en su orden; la que deja de llegar no sale (no hay qué pintar). */
export type VistaFija = { ids: Set<number>; conocidas: Set<number> }
export const vistaVacia = (): VistaFija => ({ ids: new Set(), conocidas: new Set() })
export function acumularVista(v: VistaFija, filas: FilaVista[], filtros: FiltrosColumna, hoy: string): FilaVista[] {
  const nuevas = filas.filter((f) => !v.conocidas.has(f.idFila))
  for (const f of filtrarColumnas(nuevas, filtros, hoy)) v.ids.add(f.idFila)
  for (const f of nuevas) v.conocidas.add(f.idFila)
  return filas.filter((f) => v.ids.has(f.idFila))
}
```

- [ ] **Step 4:** `npx vitest run src/modules/telefonos/pedidos/filas.test.ts` → PASA.

- [ ] **Step 5: Prueba de página que falla** — `PedidosVistaFija.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { maquetarJsdom } from '@/test/layoutJsdom'
import { estadoNsys, fila, fuente } from '../test/fabrica'
import { imeiValido } from '../test/imeiValido'
import { estadoPedidos } from './estado'
import { PedidosPage } from './PedidosPage'
import { congelarHoy } from '../test/hoy'

congelarHoy()

const pedido = (i: number) => `402-0000000-${String(i).padStart(7, '0')}`
let filas = [fila({ idFila: 1, pedido: pedido(1) }), fila({ idFila: 2, pedido: pedido(2) }), fila({ idFila: 3, pedido: pedido(3), imei: imeiValido(3) })]

afterEach(() => { vi.restoreAllMocks() })
beforeEach(() => {
  estadoPedidos.reset()
  maquetarJsdom()
  filas = [fila({ idFila: 1, pedido: pedido(1) }), fila({ idFila: 2, pedido: pedido(2) }), fila({ idFila: 3, pedido: pedido(3), imei: imeiValido(3) })]
  server.use(
    http.get('*/api/pedidos/filas', () => HttpResponse.json({ filas, hayMas: false })),
    http.get('*/api/pedidos/fuentes', () => HttpResponse.json([fuente({ fuente: 'SHIPPYPRO' })])),
    http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys())),
    http.get('*/api/pedidos/filas/:id/recomendados', () => HttpResponse.json([])),
    http.put('*/api/pedidos/filas/:id/imei', async ({ request, params }) => {
      const { imei } = (await request.json()) as { imei: string | null }
      const nueva = fila({ idFila: Number(params.id), pedido: pedido(Number(params.id)), imei })
      filas = filas.map((f) => (f.idFila === nueva.idFila ? nueva : f))
      return HttpResponse.json(nueva)
    }),
  )
})

const abrir = () => renderConProviders(<PedidosPage />, { sesion: SESION_SUPER, ruta: '/telefonos/pedidos' })
const tabla = () => screen.getByRole('region', { name: 'Tabla de pedidos' })

describe('Pedidos: la vista filtrada no se mueve', () => {
  it('con «sin IMEI» puesto, la fila picada se queda; pulsar el contador otra vez la saca', async () => {
    const user = userEvent.setup()
    abrir()
    await user.click(await screen.findByRole('button', { name: '2 sin IMEI' }))
    expect(within(tabla()).queryByText(pedido(3))).not.toBeInTheDocument()
    await user.click(within(tabla()).getAllByRole('button', { name: /pulsa para poner el IMEI/ })[0])
    await user.keyboard(`${imeiValido(1)}{Enter}`)
    await screen.findByRole('button', { name: '1 sin IMEI' })
    expect(within(tabla()).getByText(pedido(1))).toBeInTheDocument()
    expect(within(tabla()).getByText(imeiValido(1))).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: '1 sin IMEI' }))
    await waitFor(() => expect(within(tabla()).queryByText(pedido(1))).not.toBeInTheDocument())
    expect(within(tabla()).getByText(pedido(2))).toBeInTheDocument()
  })

  it('un IMEI puesto por otro (sondeo) tampoco saca la fila; un pedido nuevo que cumple entra', async () => {
    const user = userEvent.setup()
    const { queryClient } = abrir()
    await user.click(await screen.findByRole('button', { name: '2 sin IMEI' }))
    filas = [fila({ idFila: 1, pedido: pedido(1), imei: imeiValido(9) }), filas[1], filas[2], fila({ idFila: 4, pedido: pedido(4) })]
    await queryClient.invalidateQueries()
    await screen.findByText(pedido(4))
    expect(within(tabla()).getByText(pedido(1))).toBeInTheDocument()
  })
})
```

Si `renderConProviders` no devuelve `queryClient`, crear uno (`new QueryClient(...)` como en otras pruebas de `pedidos/`) y pasarlo en las opciones (`queryClient`). Si el texto del pedido no sale tal cual en la celda «Pedido» (Amazon sin asociar), usar filas con `canal` web en la fábrica, como en `PedidosPage.test.tsx`.

- [ ] **Step 6:** `npx vitest run src/modules/telefonos/pedidos/PedidosVistaFija.test.tsx` → FALLA (la fila 1 desaparece).

- [ ] **Step 7: Implementación en `PedidosPage.tsx`.**
  1. Import: añadir `acumularVista, vistaVacia` al import de `./filas`.
  2. Junto a los `useState` de arriba: `const [pulsoVista, setPulsoVista] = useState(0)`.
  3. Sustituir la línea `const vistas = useMemo(() => ordenarPorSku(filtrarColumnas(filas, filtros, hoy), e.ordenSku), …)` por:

```ts
  // La vista fija (spec 2026-11-25 §4): se rehace al cambiar los filtros de columna, el periodo, la búsqueda o «Ver», y al pulsar un
  // contador (aunque sea el puesto: así se quitan las ya picadas). Entre medias, una fila que deja de cumplir el filtro se queda.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const vista = useMemo(() => vistaVacia(), [filtros, desde, hasta, aplicado, buscando, e.verFuera, pulsoVista])
  const vistas = useMemo(() => ordenarPorSku(acumularVista(vista, filas, filtros, hoy), e.ordenSku), [vista, filas, filtros, hoy, e.ordenSku])
```

  4. En el `onClick` de los contadores: `onClick={() => { setE((s) => ({ ...s, ...filtrosDeContador(c.id) })); setPulsoVista((n) => n + 1) }}`.
  5. Quitar `filtrarColumnas` del import si ya no se usa (lint lo dice).

  Si `npm run lint` marca la mutación de `vista` dentro de `useMemo` (reglas del compilador de React), añadir sobre esa línea
  `// eslint-disable-next-line <regla>` con el comentario «acumulador por clave: mutarlo es idempotente».

- [ ] **Step 8:** `npx vitest run src/modules/telefonos/pedidos/PedidosVistaFija.test.tsx src/modules/telefonos/pedidos/PedidosFiltros.test.tsx src/modules/telefonos/pedidos/PedidosPage.test.tsx` → PASA (salvo la rota conocida de PedidosFiltros). Las de PedidosFiltros que esperen que una fila desaparezca tras editarla con un filtro puesto se adaptan a la vista fija (la fila se queda hasta pulsar el contador o cambiar el filtro).
- [ ] **Step 9: Commit** — `git commit -am "feat(pedidos): la vista filtrada no pierde filas hasta cambiar el filtro (como Excel)"` (añadir antes con `git add` el test nuevo).

---

### Task 3: La celda se edita en su sitio

**Files:**
- Modify: `src/modules/telefonos/pedidos/CeldaImei.tsx` (reescritura de la parte editable), `TablaFilas.tsx`, `PedidosPage.tsx`
- Create: `src/modules/telefonos/pedidos/TablaFilas.celda.test.tsx`

**Interfaces:**
- Consumes: Task 1 (`NoValido`, `marcarNoValido`, `quitarNoValido`, `textoNoValido`, `cargarNoValidos`, `limpiarNoValidos`, `EstadoPedidos.noValidos`).
- Produces: `CeldaImei` con prop nueva `noValido?: NoValido`; `FilaTabla` con prop nueva `noValido?: NoValido`.

- [ ] **Step 1: Pruebas que fallan** — `TablaFilas.celda.test.tsx` (mismo arranque que `TablaFilas.salto.test.tsx`: copia sus imports, `pedido`, `f`, `campo`, `pulsar`, `puts`, `beforeEach` con los handlers de PUT y recomendados, `abrirTabla`, `vacia`, `aqui`). Añadir `imeiConLuhnMalo` al import y `localStorage` limpio en `beforeEach` (`localStorage.clear()` antes de `renderConProviders`, que guarda la sesión). Casos:

```tsx
describe('Celda del IMEI en su sitio', () => {
  it('al pulsarla el campo está dentro de la celda: no hay desplegable, ni marca, ni «Validar SKU»', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    expect(campo()).toHaveFocus()
    expect(campo()!.closest('td')).toHaveAttribute('data-imei')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Validar SKU/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver recomendados' })).toBeInTheDocument()
  })

  it('cifra de control mala: no guarda, la celda queda en rojo y el campo salta a la siguiente; el salto siguiente la evita', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1), f(2), f(3)])
    await user.click(await vacia())
    await user.keyboard(`${imeiConLuhnMalo(5)}{Enter}`)
    await waitFor(() => expect(aqui()).toHaveTextContent(pedido(2)))
    expect(puts).toEqual([])
    const roja = screen.getByRole('button', { name: new RegExp(`IMEI no válido · …${imeiConLuhnMalo(5).slice(-4)}`) })
    expect(roja.closest('td')).toHaveClass('bg-[#FDE2E2]')
    expect(estadoPedidos.get().avisoImei).toMatch(/IMEI no válido/)
    await user.keyboard(`${A}{Enter}`)
    await waitFor(() => expect(aqui()).toHaveTextContent(pedido(3)))
    expect(puts).toEqual([pulsar('2', A)])
  })

  it('más de 15 cifras: rojo «16 cifras», sin guardar, y salta', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.keyboard(`${A}7{Enter}`)
    await waitFor(() => expect(aqui()).toHaveTextContent(pedido(2)))
    expect(screen.getByRole('button', { name: /IMEI no válido · 16 cifras/ })).toBeInTheDocument()
    expect(puts).toEqual([])
  })

  it('reabrir la roja trae la lectura; «Guardar igual» (solo Luhn) la guarda sin saltar y quita el rojo', async () => {
    const user = userEvent.setup()
    const mala = imeiConLuhnMalo(5)
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.keyboard(`${mala}{Enter}`)
    await waitFor(() => expect(aqui()).toHaveTextContent(pedido(2)))
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: /IMEI no válido/ }))
    expect(campo()).toHaveValue(mala)
    await user.keyboard('{Enter}')
    expect(puts).toEqual([])
    await user.click(screen.getByRole('button', { name: 'Guardar igual' }))
    await waitFor(() => expect(puts).toEqual([pulsar('1', mala)]))
    await waitFor(() => expect(campo()).not.toBeInTheDocument())
    expect(estadoPedidos.get().noValidos).toEqual({})
  })

  it('reabrir la roja y leer otro IMEI bueno lo sustituye y lo guarda', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.keyboard(`${imeiConLuhnMalo(5)}{Enter}`)
    await waitFor(() => expect(aqui()).toHaveTextContent(pedido(2)))
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: /IMEI no válido/ }))
    await pausa()
    await user.keyboard(`${B}{Enter}`)
    await waitFor(() => expect(puts).toEqual([pulsar('1', B)]))
  })

  it('con 16 cifras reabierta no hay «Guardar igual»; Escape y clic fuera la dejan en rojo', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.keyboard(`${A}7{Enter}`)
    await waitFor(() => expect(aqui()).toHaveTextContent(pedido(2)))
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: /IMEI no válido/ }))
    expect(screen.queryByRole('button', { name: 'Guardar igual' })).not.toBeInTheDocument()
    await user.click(document.body)
    expect(campo()).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /IMEI no válido · 16 cifras/ })).toBeInTheDocument()
  })

  it('quitar: en una celda con IMEI, campo vacío y Enter manda null; sin IMEI, Enter vacío no hace nada', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1, { imei: C }), f(2)])
    await user.click(screen.getByRole('button', { name: new RegExp(C) }))
    await user.keyboard('{Enter}')
    await waitFor(() => expect(puts).toEqual([pulsar('1', null, C)]))
    await user.click(await vacia())
    await user.keyboard('{Enter}')
    await pausa(200)
    expect(puts).toHaveLength(1)
  })

  it('pocas cifras y Enter: rojo en el sitio con «Un IMEI son 15 cifras», sin saltar', async () => {
    const user = userEvent.setup()
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.keyboard('3500{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('Un IMEI son 15 cifras')
    expect(aqui()).toHaveTextContent(pedido(1))
  })

  it('el ▾ abre los recomendados sin quitar el foco del campo; elegir uno guarda y cierra; Escape cierra solo la lista', async () => {
    server.use(http.get('*/api/pedidos/filas/:id/recomendados', () => HttpResponse.json([candidato({ imei: C, caja: 'Caja · Listos', nivel: 1 })])))
    const user = userEvent.setup()
    abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.click(screen.getByRole('button', { name: 'Ver recomendados' }))
    const lista = await screen.findByRole('dialog', { name: 'Recomendados' })
    expect(campo()).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(lista).not.toBeInTheDocument())
    expect(campo()).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver recomendados' }))
    await user.click(await screen.findByRole('button', { name: new RegExp(C) }))
    await waitFor(() => expect(puts).toEqual([pulsar('1', C)]))
    await waitFor(() => expect(campo()).not.toBeInTheDocument())
  })

  it('la marca no válida sobrevive a recargar (localStorage) y no la ve otro usuario', async () => {
    const user = userEvent.setup()
    const { unmount } = abrirTabla([f(1), f(2)])
    await user.click(await vacia())
    await user.keyboard(`${imeiConLuhnMalo(5)}{Enter}`)
    await waitFor(() => expect(estadoPedidos.get().noValidos[1]).toBeDefined())
    unmount()
    estadoPedidos.reset()
    cargarNoValidos()
    abrirTabla([f(1), f(2)])
    expect(await screen.findByRole('button', { name: /IMEI no válido/ })).toBeInTheDocument()
  })
})
```

(`candidato` viene de `../test/fabrica`; `cargarNoValidos` de `./noValidos`; `B`, `C`, `A`, `pausa` como en `TablaFilas.salto.test.tsx`.)

- [ ] **Step 2:** `npx vitest run src/modules/telefonos/pedidos/TablaFilas.celda.test.tsx` → FALLA.

- [ ] **Step 3: `CeldaImei.tsx` nuevo.** Conservar tal cual `FONDO`, `TONO_PARTE`, `NIVEL` y la pintura del botón en reposo; sustituir el resto del fichero por:

```tsx
import { Fragment, useEffect, useRef, useState } from 'react'
import type { FilaVista } from '@/shared/api/client'
import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'
import { cn } from '@/shared/lib/utils'
import { StaleDataError, mensajeDeError } from '@/shared/api/errors'
import { Popover, PopoverContent, PopoverTrigger } from '@/shared/ui/popover'
import { usePonerImei, useRecomendados } from '../api'
import { estadoPedidos, type FalloImei } from './estado'
import { marcarNoValido, quitarNoValido, textoNoValido, type MotivoNoValido, type NoValido } from './noValidos'
import { CIFRAS_IMEI, QUIETO_MS, SUSTITUIR_MS, luhnCuadra } from './imei'
import { CLASE_TONO, pedidoVisible, TEXTO_NIVEL, claseCelda, estaValidada, lineasAviso, partesAviso, textoMotivoValidado, tonoFila, type ParteAviso } from './filas'

// FONDO, TONO_PARTE y NIVEL: sin cambios (copiar los de hoy).

const ROJO = 'bg-[#FDE2E2]'
const MENSAJE_CORTO = `Un IMEI son ${CIFRAS_IMEI} cifras`

type Props = {
  fila: FilaVista; abierta: boolean; marcada?: boolean
  /** El último guardado de esta celda falló: se ve en rojo hasta que se guarde bien. */
  fallo?: FalloImei
  /** La última lectura no era un IMEI válido (no se guardó): rojo hasta poner un IMEI en la fila. */
  noValido?: NoValido
  onAbrir: (idFila: number, abrir: boolean) => void
  /** Tras guardar un IMEI leído (o una lectura no válida): abre la celda vacía siguiente y dice si la hay. */
  onSaltar?: (idFila: number) => boolean
}

/** La única celda editable de la tabla (spec 2026-11-25 §1): en reposo, el IMEI con su aviso o su caja; al pulsarla (si la fila es
 *  editable), la propia celda es el campo, con el contador y el ▾ de los recomendados. Nada flota salvo la lista de recomendados. */
export function CeldaImei({ fila, abierta, marcada, fallo, noValido, onAbrir, onSaltar }: Props) {
  const clase = claseCelda(fila)
  const validada = estaValidada(fila)
  const partes = partesAviso(fila)
  const lineas = lineasAviso(fila)
  const avisoValidado = validada ? `SKU validado${fila.validadaPor ? ` por ${fila.validadaPor}` : ''}: ${textoMotivoValidado(fila) ?? 'aviso de SKU'}` : null
  const textoFallo = fallo ? (fallo.duplicado ? fallo.mensaje : fallo.imei ? `No se guardó …${fallo.imei.slice(-4)} · pulsa para reintentar` : 'No se quitó el IMEI · pulsa para reintentar') : null
  const textosNoValido = noValido ? textoNoValido(noValido) : null
  const rojo = textoFallo ?? textosNoValido?.corto ?? null
  const tituloRojo = textoFallo ?? (textosNoValido ? `${textosNoValido.largo} Pulsa para revisarlo.` : null)
  const botonRef = useRef<HTMLButtonElement>(null)
  // Al cerrar el campo, el foco vuelve a la celda si se quedó en el aire (no si ya está en el campo de otra celda, tras un salto).
  const estabaAbierta = useRef(abierta)
  useEffect(() => {
    if (estabaAbierta.current && !abierta && (!document.activeElement || document.activeElement === document.body)) botonRef.current?.focus()
    estabaAbierta.current = abierta
  }, [abierta])

  const boton = (
    <button ref={botonRef} type="button" onClick={() => onAbrir(fila.idFila, true)}
      title={tituloRojo ?? [fila.imei ?? 'pulsa para poner el IMEI…', avisoValidado, ...lineas, partes.length === 0 && !avisoValidado ? fila.caja : null].filter(Boolean).join('\n')}
      className={cn('flex min-h-8 w-full cursor-pointer items-center gap-2 px-2.5 py-1 text-left font-mono text-[12.5px] hover:outline-2 hover:-outline-offset-2 hover:outline-[#9db8e8]',
        clase === 'vacia' && !rojo && 'font-sans italic text-texto-sub')}>
      {rojo
        ? <span className="min-w-0 truncate font-sans text-[12px] font-bold text-texto-error">{rojo}</span>
        : <span className="shrink-0">{fila.imei ?? 'pulsa para poner el IMEI…'}</span>}
      {!rojo && partes.length > 0 && (
        <span className="min-w-0 truncate font-sans text-[11px] font-bold">
          {partes.map((p: ParteAviso, i) => (
            <Fragment key={i}>
              {i > 0 && <span className="font-normal text-[#b9a27a]"> · </span>}
              <span className={TONO_PARTE[p.tono]}>{p.texto}</span>
            </Fragment>
          ))}
        </span>
      )}
      {!rojo && validada && (
        <span data-validado className="shrink-0 rounded-lg bg-fila-reparado-bg px-1.5 py-px font-sans text-[10.5px] font-bold text-[#2f7d3b]">validado</span>
      )}
      {!rojo && partes.length === 0 && !validada && fila.caja && <span className="min-w-0 truncate font-sans text-[11px] text-azul-gris">{fila.caja}</span>}
    </button>
  )
  const tono = tonoFila(fila)
  return (
    <td data-imei data-editando={abierta || undefined} className={cn('relative overflow-hidden border-r border-b border-form-fila-brd p-0 last:border-r-0',
      rojo ? ROJO : FONDO[clase] ?? cn(tono && CLASE_TONO[tono], marcada && 'bg-seleccion-suave'),
      abierta && cn(rojo ? ROJO : 'bg-superficie', 'outline-2 -outline-offset-2 outline-[#2f6fe0]'))}>
      {marcada && <span className="sr-only">Fila en la que estás</span>}
      {!fila.editable ? (
        fila.imei && <span className="flex min-h-8 items-center px-2.5 py-1 font-mono text-[12.5px]">{fila.imei}</span>
      ) : abierta ? (
        <CampoImei fila={fila} fallo={fallo} noValido={noValido} onCerrar={() => onAbrir(fila.idFila, false)}
          onSaltar={onSaltar ? () => onSaltar(fila.idFila) : undefined} />
      ) : boton}
    </td>
  )
}

/** Con lo que empieza el texto del 409 del servidor cuando el IMEI ya está en otro pedido. */
const PREFIJO_DUPLICADO = 'Este IMEI ya está'

function CampoImei({ fila, fallo, noValido, onCerrar, onSaltar }: { fila: FilaVista; fallo?: FalloImei; noValido?: NoValido; onCerrar: () => void; onSaltar?: () => boolean }) {
  // Reabrir una celda en rojo trae lo que falló: la lectura no válida (para sustituirla o «Guardar igual») o el IMEI cuyo guardado
  // falló (Enter reintenta con la misma clave).
  const [texto, setTexto] = useState(noValido?.imei ?? (fallo && !fallo.duplicado ? fallo.imei ?? '' : ''))
  const [corto, setCorto] = useState(false)
  const [ver, setVer] = useState(false)
  const recomendados = useRecomendados(fila.idFila, ver)
  const poner = usePonerImei()
  const [claves] = useState(() => crearClavesIdempotencia())
  const [anterior] = useState(() => fila.imei ?? '')
  // Abierta sobre una lectura no válida: es revisión; otra lectura no válida aquí no salta.
  const [revision] = useState(() => !!noValido)
  // Guardado en vuelo (referencia y no `poner.isPending`: el Enter del lector llega antes de que React pinte «pendiente»).
  const enCurso = useRef(false)
  const ultimaEntrada = useRef(0)
  const quieto = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const caja = useRef<HTMLDivElement>(null)
  useEffect(() => () => clearTimeout(quieto.current), [])
  // Pulsar fuera de la celda la cierra; no la lista de recomendados ni un diálogo. Perder el foco sin pulsar (otra ventana) no.
  useEffect(() => {
    const alPulsar = (ev: PointerEvent) => {
      const t = ev.target as Element | null
      if (!t || caja.current?.closest('td')?.contains(t)) return
      if (t.closest?.('[data-slot="popover-content"],[role="dialog"],[role="alertdialog"]')) return
      onCerrar()
    }
    document.addEventListener('pointerdown', alPulsar, true)
    return () => document.removeEventListener('pointerdown', alPulsar, true)
  }, [onCerrar])

  // Igual que el `guardar` de hoy (copiar el cuerpo de `Desplegable.guardar`), con un cambio: al guardar bien, además de quitar el fallo,
  // se quita la marca de lectura no válida de la fila (`quitarNoValido(idFila)` justo tras `claves.hecha(operacion)`).
  const guardar = (imei: string | null, salto = false) => {
    if (enCurso.current || poner.isPending) return
    enCurso.current = true
    const idFila = fila.idFila
    const operacion = `imei:${idFila}`
    const clave = fallo && !fallo.duplicado && fallo.imei === imei ? fallo.clave : claves.para(operacion, { imei })
    const salta = salto && !!onSaltar && onSaltar()
    poner.mutateAsync({ idFila, imei, imeiAnterior: anterior, clave }).then(
      () => {
        claves.hecha(operacion); enCurso.current = false
        quitarNoValido(idFila)
        estadoPedidos.set((s) => {
          if (!(idFila in s.fallosImei)) return s
          const resto = { ...s.fallosImei }
          delete resto[idFila]
          return { ...s, fallosImei: resto }
        })
        if (!salta) onCerrar()
      },
      (e: unknown) => {
        enCurso.current = false
        if (e instanceof StaleDataError && e.message.startsWith(PREFIJO_DUPLICADO)) {
          const mensaje = e.message
          estadoPedidos.set((s) => ({ ...s, fallosImei: { ...s.fallosImei, [idFila]: { imei, clave, mensaje, duplicado: true } }, avisoImei: `Pedido ${pedidoVisible(fila) ?? idFila}: ${mensaje}` }))
          return
        }
        if (e instanceof StaleDataError) {
          const aviso = `Pedido ${pedidoVisible(fila) ?? idFila}: ${mensajeDeError(e)}`
          estadoPedidos.set((s) => {
            const resto = { ...s.fallosImei }
            delete resto[idFila]
            return { ...s, fallosImei: resto, avisoImei: aviso }
          })
          if (!salta) onCerrar()
          return
        }
        const mensaje = `No se guardó el IMEI del pedido ${pedidoVisible(fila) ?? idFila}. Vuelve a abrir su celda.`
        estadoPedidos.set((s) => ({ ...s, fallosImei: { ...s.fallosImei, [idFila]: { imei, clave, mensaje } }, avisoImei: mensaje }))
      },
    )
  }
  // Una lectura que no es un IMEI válido no se guarda ni frena: rojo, aviso arriba y a la siguiente (spec §2). En revisión, se cierra.
  const noValida = (cifras: string, motivo: MotivoNoValido) => {
    const nv = { imei: cifras, motivo, imeiFila: fila.imei ?? null }
    marcarNoValido(fila.idFila, nv)
    const { largo } = textoNoValido({ ...nv, en: 0 })
    estadoPedidos.set((s) => ({ ...s, avisoImei: `Pedido ${pedidoVisible(fila) ?? fila.idFila}: ${largo}` }))
    const salta = !revision && !!onSaltar && onSaltar()
    if (!salta) onCerrar()
  }
  const comprobar = (cifras: string) => {
    if (enCurso.current) return
    // La lectura no válida que se está revisando: se decide con «Guardar igual» o leyendo otra.
    if (noValido && cifras === noValido.imei) return
    if (cifras.length > CIFRAS_IMEI) { noValida(cifras, 'mas'); return }
    // Un IMEI cuyo guardado falló ya pasó la comprobación (o se guardó igual): Enter lo reintenta sin volver a mirarla.
    const reintento = !!fallo && !fallo.duplicado && fallo.imei === cifras
    if (!reintento && !luhnCuadra(cifras)) { noValida(cifras, 'luhn'); return }
    guardar(cifras, true)
  }
  const enviar = () => {
    if (enCurso.current) return
    clearTimeout(quieto.current)
    // Vacío: quita el IMEI si lo hay; sin IMEI es el Enter del lector en la celda recién abierta y no hace nada.
    if (texto.length === 0) { if (fila.imei) guardar(null); return }
    if (texto.length < CIFRAS_IMEI) { setCorto(true); return }
    comprobar(texto)
  }
  // Solo cifras (también al pegar). Con 15 o más se espera un momento quieto (QUIETO_MS) y se comprueba, para que toda la ráfaga del
  // lector (y su Enter) caiga aquí. Con la lectura no válida en el campo, lo que llega tras una pausa (SUSTITUIR_MS) es otra lectura.
  const cambiar = (valor: string) => {
    if (enCurso.current) return
    const ahora = Date.now()
    const pausa = ahora - ultimaEntrada.current
    ultimaEntrada.current = ahora
    clearTimeout(quieto.current)
    let cifras = valor.replace(/\D/g, '')
    if (noValido && texto === noValido.imei && pausa >= SUSTITUIR_MS && valor.startsWith(texto)) {
      const nuevas = valor.slice(texto.length).replace(/\D/g, '')
      if (nuevas !== '') cifras = nuevas
    }
    setTexto(cifras)
    setCorto(false)
    if (cifras.length >= CIFRAS_IMEI) quieto.current = setTimeout(() => comprobar(cifras), QUIETO_MS)
  }
  const lecturaMala = !!noValido && texto === noValido.imei
  const enRojo = corto || lecturaMala || texto.length > CIFRAS_IMEI

  return (
    <div ref={caja} className={cn('flex h-8 items-center gap-1.5 pr-1 pl-2.5', enRojo && ROJO)}
      title={corto ? MENSAJE_CORTO : lecturaMala && noValido ? textoNoValido(noValido).largo : undefined}>
      <input autoFocus aria-label="IMEI" inputMode="numeric" pattern="[0-9]*" autoComplete="off" value={texto} readOnly={poner.isPending}
        placeholder={fila.imei ? 'Lee otro, o vacía y Enter para quitar' : 'Lee o escribe el IMEI'}
        onChange={(e) => cambiar(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') { e.preventDefault(); enviar() }
          // Con la lista abierta, Escape cierra solo la lista (Radix); si no, cierra la celda.
          else if (e.key === 'Escape') { e.preventDefault(); if (ver) setVer(false); else onCerrar() }
        }}
        className="h-7 min-w-0 flex-1 bg-transparent font-mono text-[13px] outline-none placeholder:font-sans placeholder:text-[12px] placeholder:text-texto-sub placeholder:italic" />
      {corto && <span role="alert" className="sr-only">{MENSAJE_CORTO}</span>}
      {poner.isPending && <span className="shrink-0 text-[11px] text-azul-gris italic">guardando…</span>}
      {lecturaMala && noValido?.motivo === 'luhn' && (
        <button type="button" disabled={poner.isPending} onClick={() => guardar(texto)} title="Guardar este IMEI aunque la cifra de control no cuadre"
          className="h-6 shrink-0 cursor-pointer rounded-md border border-[#e0a0a0] bg-superficie px-1.5 text-[11px] font-bold text-texto-error">Guardar igual</button>
      )}
      {/* El contador es una ayuda a la vista: oculto al lector de pantalla para que no lea cada cifra. */}
      <span aria-hidden="true" data-contador className={cn('shrink-0 font-mono text-[11px]', enRojo ? 'font-bold text-texto-error' : 'text-azul-gris')}>{texto.length}/{CIFRAS_IMEI}</span>
      <Popover open={ver} onOpenChange={setVer}>
        <PopoverTrigger asChild>
          {/* Sin robar el foco al campo: el lector puede seguir leyendo con la lista abierta. */}
          <button type="button" aria-label="Ver recomendados" title="Ver recomendados" onMouseDown={(e) => e.preventDefault()}
            className="flex size-6 shrink-0 cursor-pointer items-center justify-center rounded-md border border-borde-input bg-superficie text-[11px] text-azul-medio aria-expanded:bg-azul-medio aria-expanded:text-superficie">▾</button>
        </PopoverTrigger>
        <PopoverContent align="end" sideOffset={2} collisionPadding={8} aria-label="Recomendados"
          onOpenAutoFocus={(e) => e.preventDefault()} onCloseAutoFocus={(e) => e.preventDefault()}
          className="w-[680px] max-w-[calc(100vw-16px)] rounded-lg border-azul-medio bg-superficie p-2 whitespace-normal shadow-[0_8px_24px_rgba(0,18,50,.25)]">
          <ListaRecomendados fila={fila} recomendados={recomendados} ocupado={poner.isPending} onElegir={(imei) => guardar(imei)} />
        </PopoverContent>
      </Popover>
    </div>
  )
}

/** Los recomendados de la fila (los de hoy, sin cambios): la caja delante, el IMEI, «es …» en nivel 3 y el nivel. Elegir uno lo guarda. */
function ListaRecomendados({ fila, recomendados, ocupado, onElegir }: {
  fila: FilaVista; recomendados: ReturnType<typeof useRecomendados>; ocupado: boolean; onElegir: (imei: string) => void
}) {
  return (
    <>
      <div className="mb-1 px-0.5 text-[11px] font-bold tracking-wide text-azul-gris uppercase">Recomendados{fila.sku && <> para <span className="font-mono normal-case">{fila.sku}</span></>} · dónde buscarlo</div>
      {recomendados.isPending && <p className="px-2 py-1.5 text-[12px] text-azul-gris">Cargando…</p>}
      {recomendados.isError && <p className="px-2 py-1.5 text-[12px] text-azul-gris">No se pudieron cargar los recomendados.</p>}
      {recomendados.data?.length === 0 && <p className="px-2 py-1.5 text-[12px] text-azul-gris">No hay ningún móvil de ese modelo y capacidad libre en stock.</p>}
      {recomendados.data?.map((c) => (
        <button key={c.imei} type="button" disabled={ocupado} onClick={() => onElegir(c.imei)}
          className="grid w-full cursor-pointer grid-cols-[150px_118px_minmax(0,1fr)_auto] items-start gap-2.5 rounded-md px-2 py-1.5 text-left text-[12px] hover:bg-seleccion-suave">
          <span className="justify-self-start rounded-md bg-[#eef0f3] px-2 py-px font-bold [overflow-wrap:anywhere]">{c.caja}</span>
          <span className="font-mono text-[12.5px] font-bold">{c.imei}</span>
          <span className="font-mono text-[11.5px] [overflow-wrap:anywhere] text-azul-gris">{c.nivel === 3 && (c.sku ? <>es {c.sku}</> : <span className="font-sans italic">SKU sin identificar</span>)}</span>
          <span className={cn('rounded-lg px-2 py-px text-[11px] font-bold whitespace-nowrap', NIVEL[c.nivel as 1 | 2 | 3])}>{TEXTO_NIVEL[c.nivel as 1 | 2 | 3]}</span>
        </button>
      ))}
    </>
  )
}
```

Desaparecen de este fichero: `Desplegable`, `MENSAJE_ERROR`/`ErrorImei`, y los imports de `EditorMarca` y `BotonValidarSku`. `Fragment` y `ParteAviso` siguen haciendo falta en los tres bloques del botón.

- [ ] **Step 4: `TablaFilas.tsx`.**
  1. `import type { NoValido } from './noValidos'`; en `PropsFila` añadir `/** La última lectura de esta fila no era un IMEI válido. */ noValido?: NoValido`.
  2. `FilaTabla`: recibir `noValido` y pasarlo: `<CeldaImei … fallo={fallo} noValido={noValido} …/>`.
  3. `const [{ filaAqui, filaElegida, fallosImei, noValidos }] = useStore(estadoPedidos)`; en las dos llamadas a `<FilaTabla …>` añadir `noValido={noValidos[f.idFila]}`.
  4. En `saltar`: `const j = lista.findIndex((f, k) => k > i && saltaElLector(f) && !(f.idFila in estadoPedidos.get().noValidos))` y en su comentario «… sin IMEI y sin rojo de lectura no válida …».
  5. Actualizar el comentario de cabecera de `FilaTabla`/`TablaFilas` que habla del «desplegable del IMEI» y su portal: ahora el campo está dentro de la celda.

- [ ] **Step 5: `PedidosPage.tsx`.**
  1. `import { cargarNoValidos, limpiarNoValidos } from './noValidos'`.
  2. Tras `recibidas`:

```ts
  // Lecturas no válidas (spec 2026-11-25 §2): las de este navegador al entrar; y se quitan las de filas que ya tienen otro IMEI.
  useEffect(() => { cargarNoValidos() }, [])
  useEffect(() => { limpiarNoValidos(recibidas) }, [recibidas])
```

  3. En el listener de Escape del panel, no cerrar el panel si el foco está en la celda del IMEI (el Escape es de la celda):

```ts
      if (ev.key === 'Escape' && !document.querySelector('[role="dialog"],[role="alertdialog"],[data-slot="popover-content"]')
        && !(document.activeElement as Element | null)?.closest?.('td[data-imei]')) cerrarPanel()
```

- [ ] **Step 6:** `npx vitest run src/modules/telefonos/pedidos/TablaFilas.celda.test.tsx` → PASA. Luego `npm run typecheck && npm run lint` → limpio.
- [ ] **Step 7: Commit** — `git add -A src/modules/telefonos/pedidos && git commit -m "feat(pedidos): el IMEI se pica en la propia celda; lectura no válida en rojo sin frenar; recomendados en el ▾"`

---

### Task 4: Ayuda

**Files:** Modify `src/modules/telefonos/pedidos/AyudaPedidos.tsx`, `PedidosAyuda.test.tsx`

- [ ] **Step 1: Prueba que falla** — en el primer `it` de `PedidosAyuda.test.tsx`, añadir:

```ts
    expect(within(ayuda).getByText(/El IMEI se lee o se escribe en la propia celda/)).toBeInTheDocument()
    expect(within(ayuda).getByText(/IMEI no válido/)).toBeInTheDocument()
    expect(within(ayuda).getByText(/Con un filtro puesto, las filas que vas picando no se van/)).toBeInTheDocument()
```

- [ ] **Step 2:** `npx vitest run src/modules/telefonos/pedidos/PedidosAyuda.test.tsx` → FALLA.
- [ ] **Step 3: Textos en `AyudaPedidos.tsx`.**
  - Tras `<Linea>Las filas las crea el ERP desde ShippyPro. Solo se teclea el IMEI.</Linea>`, añadir:

```tsx
          <Linea>El IMEI se lee o se escribe en la propia celda: púlsala y lee. Al guardarlo, pasa sola a la siguiente fila sin IMEI. Para quitarlo, deja el campo vacío y pulsa Enter. El ▾ enseña los recomendados.</Linea>
          <Linea muestra={celda(undefined, 'IMEI no válido · …6537')}>
            IMEI no válido (cifra de control mala o más de 15 cifras): no se guarda y sigue a la siguiente. Al revisar, pulsa la celda roja:
            lee otro o, si estás seguro, «Guardar igual». Se queda en rojo en este navegador hasta poner un IMEI en esa fila (24 h como mucho).
          </Linea>
          <Linea>Con un filtro puesto, las filas que vas picando no se van: la vista se queda como al poner el filtro hasta que lo cambias o vuelves a pulsar el contador.</Linea>
```

  - En la línea de «validado»: «(«Validar SKU», en el desplegable del IMEI o en el panel)» → «(«Validar SKU», en el panel de la fila)».
  - En la de marcas: «Las otras marcas se eligen en la celda del IMEI (debajo del campo) o en el panel de la fila» → «Las otras marcas se eligen en el panel de la fila».
  - Si `celda(undefined, …)` no pinta rojo, usar como muestra `<span className="rounded-sm bg-[#FDE2E2] px-1.5 py-px text-[11px] font-bold text-texto-error">IMEI no válido · …6537</span>`.

- [ ] **Step 4:** `npx vitest run src/modules/telefonos/pedidos/PedidosAyuda.test.tsx` → PASA.
- [ ] **Step 5: Commit** — `git commit -am "docs(pedidos): la ayuda explica la celda, el IMEI no válido y la vista fija"`

---

### Task 5: Migrar las pruebas que usaban el desplegable

**Files:** las pruebas de `src/modules/telefonos/pedidos/` que fallen (según el recuento: `PedidosPage`, `PedidosPanel`, `PedidosMarcas`,
`PedidosValidar`, `PedidosV2`, `PedidosVentana`, `PedidosParcial`, `PedidosCancelar`, `TablaFilas.salto`, `TablaFilas.abierta`, `TablaFilas`).

- [ ] **Step 1:** `npx vitest run src/modules/telefonos/pedidos` y apuntar las que fallan.
- [ ] **Step 2: Reglas de migración** (aplicar fichero a fichero; cada cambio respeta lo que la prueba quería comprobar):
  - `findByRole('dialog', { name: 'Poner el IMEI' })` / `desplegable()` → el campo: `await screen.findByRole('textbox', { name: 'IMEI' })`. «El desplegable está cerrado» → `expect(screen.queryByRole('textbox', { name: 'IMEI' })).not.toBeInTheDocument()`.
  - Botón «Cerrar» del desplegable → `await user.keyboard('{Escape}')`.
  - Botón «Quitar el IMEI» → con el campo abierto (vacío), `await user.keyboard('{Enter}')`.
  - «Ver recomendados» sigue existiendo (es el ▾); «Ocultar recomendados» → `await user.keyboard('{Escape}')`; la lista ahora es `dialog` «Recomendados».
  - Marca y nota, «Validar SKU» y «Para cancelar el móvil, abre el panel» **desde la celda** → hacerlo desde el panel: pulsar la celda del pedido (botón «Ver el detalle del pedido» de la fila) y usar los mismos controles dentro de `screen.getByRole('region'…)`/`section` «Detalle del pedido». Las pruebas que solo comprobaban que estaban en la celda se borran, y se añade (si no existe ya en `PedidosPanel`) una comprobación de que están en el panel.
  - Cifra de control mala (`PedidosPage.test.tsx` ~l. 328 y `TablaFilas.salto.test.tsx` ~l. 306): sustituir por el comportamiento nuevo (rojo, sin PUT, salta; «Guardar igual» solo al reabrir) — ya cubierto en `TablaFilas.celda.test.tsx`, así que esas dos se borran o se reescriben para el caso de su fichero (en `PedidosPage`, que el aviso de arriba dice «IMEI no válido»).
  - «Más de 15 cifras: error y no guarda» → rojo «16 cifras», sin PUT y salta.
  - Una prueba que esperaba que una fila desapareciera del filtro tras ponerle IMEI → ahora se queda (vista fija); si lo que probaba era el filtro, pulsar el contador después.
- [ ] **Step 3:** `npm run check` → todo en verde salvo la rota conocida (PedidosFiltros «buscador <2 caracteres»).
- [ ] **Step 4: Commit** — `git commit -am "test(pedidos): pruebas al día con la celda en línea (marca y validar desde el panel)"`

---

### Task 6: Comprobación en el navegador

- [ ] **Step 1:** `npm run dev` y, con Playwright headless (`node_modules` de `gestion-reparaciones-web`, `NODE_PATH`), contra el servidor local o preprod según haya datos: abrir Pedidos, poner «sin IMEI», picar 3 seguidos (uno con Luhn mala), comprobar que ninguna fila se mueve, que la roja queda y que el ▾ abre la lista; captura en el scratchpad y revisarla.
- [ ] **Step 2:** Si algo no cuadra con el prototipo (`docs/superpowers/specs/assets/2026-11-25-pedidos-imei-en-celda-prototipo.html`), arreglar y volver a `npm run check`.
- [ ] **Step 3:** Parar aquí y avisar al usuario: rama lista para revisar. Versión, changelog, merge, preprod (E2E con datos sintéticos por API y limpieza) y producción van aparte y con su confirmación.
