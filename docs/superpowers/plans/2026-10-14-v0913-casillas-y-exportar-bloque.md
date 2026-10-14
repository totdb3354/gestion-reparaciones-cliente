# 0.9.13 — Casillas en las acciones de bloque y «Exportar bloque en CSV» · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En Pedidos, desmarcar líneas en «Confirmar bloque», «Recibir todo» y «Cancelar bloque», y descargar un bloque entero con «Exportar bloque en CSV» desde su menú.

**Architecture:** Solo web; el servidor ya aplica las acciones de bloque a la lista de líneas que manda la web. Las reglas puras van en `bloques/reglas.ts` (entrada `exportar`, textos de las desmarcadas). Las casillas, en `ListaLineas` (prop opcional `casillas`) y `AccionBloqueDialog` (estado `marcadas`). La descarga, en `PedidosPage` (`alElegirBloque` → `descargarCsv`).

**Tech Stack:** React 18 + TypeScript, Radix (`@/shared/ui/checkbox`, menús), TanStack Query, Vitest + Testing Library + MSW, Playwright (E2E contra preproducción).

Spec: `docs/superpowers/specs/2026-10-14-v0913-casillas-y-exportar-bloque-design.md` (raíz). Prototipo: `docs/superpowers/specs/assets/2026-10-14-v0913-prototipo-casillas-bloque.html`.

**Dónde se trabaja:**
- Web en el worktree `C:/Users/dev/Documents/wt/web-0913`, rama `feature/casillas-exportar-bloque`, creada desde la web `main` `072fb27`.
- El controlador la crea antes de la tarea 1:
  - `git -C C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add C:/Users/dev/Documents/wt/web-0913 -b feature/casillas-exportar-bloque main`
  - luego `npm ci` en el worktree.
- Todas las rutas `src/…` y `tests/…` del plan son relativas a ese worktree.
- Los comandos se lanzan desde la raíz del worktree.

## Global Constraints

- Solo web: no se toca el servidor, la base ni nginx.
- Casillas solo en `confirmar`, `recibir` y `cancelar`. «Borrar bloque» sin casillas.
- Las líneas con casilla son las de `lineasQueCambian` (pendientes al confirmar, en camino al recibir o cancelar). Las parciales siguen en «No cambian» con su motivo de hoy.
- Al abrir, todas marcadas.
- Casilla de cada línea: `aria-label` «{tipoComponente} (ID {idCompra})».
- Casilla de cabecera: `aria-label` «Marcar todas». Marcada con todas, `indeterminate` (`aria-checked="mixed"`) con algunas, desmarcada con ninguna. Al pulsarla, si estaba marcada desmarca todas; si no, marca todas.
- Título: «{cambian} ({n})» con todas; «{cambian} ({marcadas} de {total})» si no. Ejemplo: «Se reciben (suman al stock) (36 de 39)».
- Línea desmarcada: en gris, nombre tachado (`line-through`), y en lugar del estado «se queda en camino» (recibir y cancelar) o «se queda pendiente» (confirmar).
- Aviso bajo la lista, solo con desmarcadas:
  - «Se queda en camino 1 línea: la desmarcada no cambia.» / «Se quedan en camino {n} líneas: las desmarcadas no cambian.»;
  - al confirmar: «Se queda pendiente 1 línea: la desmarcada no cambia.» / «Se quedan pendientes {n} líneas: las desmarcadas no cambian.».
- Botón con el texto de hoy contando las marcadas («Confirmar ({n})», «Recibir ({n})», «Cancelar líneas ({n})»), deshabilitado con 0. El cuerpo del POST lleva solo las marcadas, en el orden del bloque.
- Los textos del menú no cambian: «Recibir todo ({n})» sigue contando todas las que pueden cambiar.
- Menú del bloque: entrada «Exportar bloque en CSV», acción `exportar`.
  - Va justo tras «Añadir nota…» / «Editar nota…» y antes del separador de «Cancelar bloque».
  - Nunca deshabilitada, tampoco en bloques terminados o antiguos.
- El menú del bloque (⋮ y clic derecho en la cabecera) lo ven todos los roles: sin `puedeEditar` (admin y técnico), solo con «Exportar bloque en CSV». El grupo «Sin bloque», sin menú.
- La exportación descarga todas las líneas del bloque (`Bloque.lineas`) aunque haya filtros, con `CABECERAS_CSV_PEDIDOS` y `filaCsvPedido`, llamando a `descargarCsv('pedidos_bloque_{idBloque}', …)` en el mismo clic, sin nada asíncrono antes.
- El CSV general («Descargar CSV») no cambia.
- Commits sin la línea `Co-Authored-By`.

---

### Task 1: Reglas del menú y textos de las desmarcadas

**Files:**
- Modify: `src/modules/almacen/pedidos/bloques/reglas.ts` (tipo `AccionMenuBloque` :131, `entradasMenuBloque` :136-157; funciones nuevas tras `motivoNoCambia` :129)
- Test: `src/modules/almacen/pedidos/bloques/reglas.test.ts` (describe «acciones de bloque», :64-122)

**Interfaces:**
- Consumes: `AccionBloque` de `../api` (`'confirmar' | 'recibir' | 'cancelar' | 'borrar'`), `Bloque` y `EntradaMenuBloque` de `reglas.ts`.
- Produces:
  - `type AccionMenuBloque = AccionBloque | 'anadir' | 'prevision' | 'separar' | 'juntar' | 'nota' | 'exportar'`
  - `entradasMenuBloque(b: Bloque, otrosVivos: number, puedeEditar = true): EntradaMenuBloque[]`
  - `textoQueda(accion: AccionBloque): string`
  - `textoDesmarcadas(accion: AccionBloque, n: number): string`

- [ ] **Step 1: Write the failing tests**

En `reglas.test.ts`, añade `textoDesmarcadas` y `textoQueda` al import de `./reglas` (orden alfabético dentro de la lista, como el resto).

En el test `'menú del bloque con contadores, motivos y peligro'`, la lista esperada pasa a llevar «Exportar bloque en CSV» tras «Añadir nota…»:

```ts
    expect(entradas.map((e) => (e === 'separador' ? '—' : e.texto))).toEqual([
      'Confirmar bloque (1)', 'Recibir todo (1)', '—', 'Añadir líneas…', 'Añadir previsión…', 'Separar líneas…', 'Juntar con…', 'Añadir nota…',
      'Exportar bloque en CSV', '—', 'Cancelar bloque (1)', 'Borrar bloque',
    ])
```

Y, al final del `describe('acciones de bloque', …)`, tras el test `'menú de un bloque de una sola línea pendiente y con nota'`:

```ts
  it('«Exportar bloque en CSV» sale también en un bloque terminado (0.9.13)', () => {
    const terminado = [...agruparEnBloques([l({ idCompra: 1, idBloque: 60, estado: 'recibido' })]).values()][0]
    expect(entradasMenuBloque(terminado, 0).find((e) => e !== 'separador' && e.accion === 'exportar'))
      .toMatchObject({ texto: 'Exportar bloque en CSV', deshabilitada: false, motivo: null, peligro: false })
  })
  it('sin permiso de edición el menú solo lleva «Exportar bloque en CSV» (0.9.13)', () => {
    expect(entradasMenuBloque(b, 0, false)).toEqual([
      { accion: 'exportar', texto: 'Exportar bloque en CSV', deshabilitada: false, motivo: null, peligro: false },
    ])
  })
  it('textos de las líneas desmarcadas (0.9.13)', () => {
    expect(textoQueda('confirmar')).toBe('se queda pendiente')
    expect(textoQueda('recibir')).toBe('se queda en camino')
    expect(textoQueda('cancelar')).toBe('se queda en camino')
    expect(textoDesmarcadas('recibir', 1)).toBe('Se queda en camino 1 línea: la desmarcada no cambia.')
    expect(textoDesmarcadas('cancelar', 3)).toBe('Se quedan en camino 3 líneas: las desmarcadas no cambian.')
    expect(textoDesmarcadas('confirmar', 1)).toBe('Se queda pendiente 1 línea: la desmarcada no cambia.')
    expect(textoDesmarcadas('confirmar', 2)).toBe('Se quedan pendientes 2 líneas: las desmarcadas no cambian.')
  })
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npx vitest run src/modules/almacen/pedidos/bloques/reglas.test.ts`
Expected: FAIL. La lista del menú no lleva «Exportar bloque en CSV» y no existen `textoQueda` ni `textoDesmarcadas` (el import falla).

- [ ] **Step 3: Implement**

En `reglas.ts`, sustituye el tipo y la función del menú (:131-157):

```ts
export type AccionMenuBloque = AccionBloque | 'anadir' | 'prevision' | 'separar' | 'juntar' | 'nota' | 'exportar'
export type EntradaMenuBloque =
  | { accion: AccionMenuBloque; texto: string; deshabilitada: boolean; motivo: string | null; peligro: boolean }
  | 'separador'

/** Menú del bloque (spec §6.4). `otrosVivos`: cuántos bloques vivos más tiene su proveedor. 0.9.13: «Exportar bloque en
 *  CSV» siempre disponible; sin `puedeEditar` (admin y técnico) es la única entrada. */
export function entradasMenuBloque(b: Bloque, otrosVivos: number, puedeEditar = true): EntradaMenuBloque[] {
  const entrada = (accion: AccionMenuBloque, texto: string, deshabilitada: boolean, motivo: string | null, peligro = false): EntradaMenuBloque =>
    ({ accion, texto, deshabilitada, motivo: deshabilitada ? motivo : null, peligro })
  const exportar = entrada('exportar', 'Exportar bloque en CSV', false, null)
  if (!puedeEditar) return [exportar]
  const cerrado = !esVivo(b)
  const TERMINADO = 'el bloque está terminado'
  const pendientes = lineasQueCambian(b, 'confirmar').length
  const enCamino = lineasQueCambian(b, 'recibir').length
  return [
    entrada('confirmar', `Confirmar bloque (${pendientes})`, pendientes === 0, 'nada pendiente'),
    entrada('recibir', `Recibir todo (${enCamino})`, enCamino === 0, 'nada en camino'),
    'separador',
    entrada('anadir', 'Añadir líneas…', cerrado, TERMINADO),
    entrada('prevision', 'Añadir previsión…', cerrado, TERMINADO),
    entrada('separar', 'Separar líneas…', cerrado || b.lineas.length < 2, cerrado ? TERMINADO : 'solo tiene una línea'),
    entrada('juntar', 'Juntar con…', cerrado || otrosVivos === 0, cerrado ? TERMINADO : 'no hay otro bloque vivo de este proveedor'),
    entrada('nota', b.nota ? 'Editar nota…' : 'Añadir nota…', false, null),
    exportar,
    'separador',
    entrada('cancelar', `Cancelar bloque (${enCamino})`, enCamino === 0, 'nada en camino', true),
    entrada('borrar', 'Borrar bloque', pendientes !== b.lineas.length, 'hay líneas ya pedidas', true),
  ]
}
```

Y justo después de `motivoNoCambia` (tras su llave de cierre, :129):

```ts
/** 0.9.13: lo que pasa con una línea desmarcada en «Confirmar bloque», «Recibir todo» o «Cancelar bloque». */
export function textoQueda(accion: AccionBloque): string {
  return accion === 'confirmar' ? 'se queda pendiente' : 'se queda en camino'
}

/** 0.9.13: aviso bajo la lista de un diálogo de bloque con líneas desmarcadas. */
export function textoDesmarcadas(accion: AccionBloque, n: number): string {
  const pendiente = accion === 'confirmar'
  if (n === 1) return `${pendiente ? 'Se queda pendiente' : 'Se queda en camino'} 1 línea: la desmarcada no cambia.`
  return `${pendiente ? 'Se quedan pendientes' : 'Se quedan en camino'} ${n} líneas: las desmarcadas no cambian.`
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npx vitest run src/modules/almacen/pedidos/bloques/`
Expected: PASS, incluido `MenuBloque.test.tsx`, que sigue usando `entradasMenuBloque(b, 0)`.

Run: `npm run typecheck`
Expected: sin errores. `PedidosPage.alElegirBloque` no tiene `case 'exportar'` todavía; no es un error de tipos, porque el `switch` no exige exhaustividad. Lo cablea la tarea 3.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/pedidos/bloques/reglas.ts src/modules/almacen/pedidos/bloques/reglas.test.ts
git commit -m "feat: entrada exportar en el menu del bloque y textos de las lineas desmarcadas"
```

---

### Task 2: Casillas en el diálogo de las acciones de bloque

**Files:**
- Modify: `src/shared/ui/checkbox.tsx` (icono del estado `indeterminate`)
- Modify: `src/modules/almacen/pedidos/bloques/ListaLineas.tsx` (prop opcional `casillas`)
- Modify: `src/modules/almacen/pedidos/bloques/AccionBloqueDialog.tsx` (estado `marcadas`)
- Test: `src/modules/almacen/pedidos/bloques/dialogos.test.tsx` (describe nuevo tras «AccionBloqueDialog (spec §6.5)», :34-88)

**Interfaces:**
- Consumes (tarea 1): `textoQueda(accion: AccionBloque): string`, `textoDesmarcadas(accion: AccionBloque, n: number): string` de `./reglas`.
- Produces:
  - `export type CasillasLista = { marcadas: Set<number>; onAlternar: (idCompra: number, marcada: boolean) => void; onAlternarTodas: (marcar: boolean) => void; textoQueda: string }`, exportado desde `ListaLineas.tsx`;
  - prop opcional `casillas?: CasillasLista` de `ListaLineas`.

- [ ] **Step 1: Write the failing tests**

En `dialogos.test.tsx`, tras el `describe('AccionBloqueDialog (spec §6.5)', …)` (que acaba en :88) y antes de `describe('SepararDialog', …)`:

```tsx
/** Bloque 41: pendiente (1) y tres en camino (2 bat-x, 3 lcd-y, 4 mc-x). */
const tresEnCamino = () => agruparEnBloques([
  l({ idCompra: 1 }),
  l({ idCompra: 2, estado: 'en_camino', tipoComponente: 'bat-x' }),
  l({ idCompra: 3, estado: 'en_camino', tipoComponente: 'lcd-y' }),
  l({ idCompra: 4, estado: 'en_camino', tipoComponente: 'mc-x' }),
]).get('41')!

describe('AccionBloqueDialog con casillas (0.9.13)', () => {
  it('abre con todas marcadas; desmarcar una cambia título, botón, aviso y su texto, y manda solo las marcadas', async () => {
    const onCerrar = vi.fn()
    renderConProviders(<AccionBloqueDialog bloque={tresEnCamino()} accion="recibir" onCerrar={onCerrar} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Recibir todo · Bloque 41' })
    const casilla = within(dlg).getByRole('checkbox', { name: 'lcd-y (ID 3)' })
    expect(casilla).toBeChecked()
    expect(within(dlg).getByRole('checkbox', { name: 'Marcar todas' })).toBeChecked()
    expect(within(dlg).getByRole('region', { name: 'Se reciben (suman al stock) (3)' })).toBeInTheDocument()
    expect(within(dlg).queryByText(/desmarcadas? no cambia/)).not.toBeInTheDocument()

    await userEvent.click(casilla)
    expect(casilla).not.toBeChecked()
    const lista = within(dlg).getByRole('region', { name: 'Se reciben (suman al stock) (2 de 3)' })
    expect(within(lista).getByText('lcd-y')).toHaveClass('line-through')
    expect(lista).toHaveTextContent('se queda en camino')
    expect(within(dlg).getByRole('checkbox', { name: 'Marcar todas' })).toHaveAttribute('aria-checked', 'mixed')
    expect(within(dlg).getByText('Se queda en camino 1 línea: la desmarcada no cambia.')).toBeInTheDocument()

    await userEvent.click(within(dlg).getByRole('button', { name: 'Recibir (2)' }))
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(peticiones).toEqual([{
      ruta: '/api/bloques-compra/41/recibir',
      cuerpo: { lineas: [{ idCompra: 2, updatedAt: U }, { idCompra: 4, updatedAt: U }] },
    }])
  })

  it('«Marcar todas» desmarca todas (botón deshabilitado) y, a medias, las vuelve a marcar todas', async () => {
    renderConProviders(<AccionBloqueDialog bloque={tresEnCamino()} accion="cancelar" onCerrar={vi.fn()} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Cancelar bloque · Bloque 41' })
    const todas = within(dlg).getByRole('checkbox', { name: 'Marcar todas' })
    await userEvent.click(todas)
    expect(todas).not.toBeChecked()
    expect(within(dlg).getByRole('button', { name: 'Cancelar líneas (0)' })).toBeDisabled()
    expect(within(dlg).getByText('Se quedan en camino 3 líneas: las desmarcadas no cambian.')).toBeInTheDocument()

    await userEvent.click(within(dlg).getByRole('checkbox', { name: 'bat-x (ID 2)' }))
    expect(todas).toHaveAttribute('aria-checked', 'mixed')
    await userEvent.click(todas)
    expect(todas).toBeChecked()
    expect(within(dlg).getByRole('button', { name: 'Cancelar líneas (3)' })).toBeEnabled()
    expect(within(dlg).getByRole('region', { name: 'Se cancelan (no se pueden reactivar) (3)' })).toBeInTheDocument()
    expect(peticiones).toEqual([])
  })

  it('«Confirmar bloque» con una desmarcada: «se queda pendiente»', async () => {
    const b = agruparEnBloques([l({ idCompra: 1 }), l({ idCompra: 2, tipoComponente: 'bat-x' })]).get('41')!
    renderConProviders(<AccionBloqueDialog bloque={b} accion="confirmar" onCerrar={vi.fn()} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Confirmar bloque · Bloque 41' })
    await userEvent.click(within(dlg).getByRole('checkbox', { name: 'bat-x (ID 2)' }))
    expect(within(dlg).getByRole('region', { name: 'Pasan a en camino (1 de 2)' })).toHaveTextContent('se queda pendiente')
    expect(within(dlg).getByText('Se queda pendiente 1 línea: la desmarcada no cambia.')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Confirmar (1)' })).toBeEnabled()
  })

  it('«No cambian» y «Borrar bloque» no llevan casillas', async () => {
    const { unmount } = renderConProviders(<AccionBloqueDialog bloque={mezclado()} accion="recibir" onCerrar={vi.fn()} />, { sesion: SESION_SUPER })
    const dlg = await screen.findByRole('dialog', { name: 'Recibir todo · Bloque 41' })
    expect(within(within(dlg).getByRole('region', { name: 'No cambian (2)' })).queryAllByRole('checkbox')).toHaveLength(0)
    unmount()

    const b = agruparEnBloques([l({ idCompra: 1 }), l({ idCompra: 2 })]).get('41')!
    renderConProviders(<AccionBloqueDialog bloque={b} accion="borrar" onCerrar={vi.fn()} />, { sesion: SESION_SUPER })
    const borrar = await screen.findByRole('dialog', { name: 'Borrar bloque · Bloque 41' })
    expect(within(borrar).queryAllByRole('checkbox')).toHaveLength(0)
    expect(within(borrar).getByRole('region', { name: 'Se borran el bloque y todas sus líneas (2)' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npx vitest run src/modules/almacen/pedidos/bloques/dialogos.test.tsx`
Expected: FAIL en el describe nuevo, porque no hay casillas (`Unable to find role="checkbox"`). Los tests de antes siguen pasando.

- [ ] **Step 3: Implement — icono del estado indeterminado en `Checkbox`**

En `src/shared/ui/checkbox.tsx`:
- importa `MinusIcon` junto a `CheckIcon` (`import { CheckIcon, MinusIcon } from "lucide-react"`);
- en `className` del `Root`, añade `group` al principio y el mismo relleno que el estado marcado: `data-[state=indeterminate]:border-primary data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground`;
- deja el `Indicator` así:

```tsx
      <CheckboxPrimitive.Indicator
        data-slot="checkbox-indicator"
        className="grid place-content-center text-current transition-none"
      >
        <CheckIcon className="size-3.5 group-data-[state=indeterminate]:hidden" />
        <MinusIcon className="hidden size-3.5 group-data-[state=indeterminate]:block" />
      </CheckboxPrimitive.Indicator>
```

Así, «a medias» se ve como un guion y no como una marca. Ningún otro uso pasa hoy `checked="indeterminate"`, así que el resto no cambia.

- [ ] **Step 4: Implement — `ListaLineas` con casillas**

Sustituye `src/modules/almacen/pedidos/bloques/ListaLineas.tsx` entero:

```tsx
import type { CompraComponente } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { Checkbox } from '@/shared/ui/checkbox'
import { BadgeEstadoPedido } from '../BadgeEstadoPedido'

/** Casillas de la lista (0.9.13): las líneas marcadas, qué hacer al cambiar una o todas, y el texto de una desmarcada. */
export type CasillasLista = {
  marcadas: Set<number>
  onAlternar: (idCompra: number, marcada: boolean) => void
  onAlternarTodas: (marcar: boolean) => void
  textoQueda: string
}

type Props = { titulo: string; lineas: CompraComponente[]; motivo?: (p: CompraComponente) => string; apagada?: boolean; casillas?: CasillasLista }

function Datos({ p, tachada = false }: { p: CompraComponente; tachada?: boolean }) {
  return (
    <>
      <span className="text-azul-gris">{p.idCompra}</span>
      <b className={cn(tachada && 'line-through')}>{p.tipoComponente}</b>
      <span>× {p.cantidad}</span>
    </>
  )
}

/** Lista de líneas de los diálogos de bloque (prototipo): ID, componente, cantidad y su estado o, en «No cambian», el
 *  motivo. Con `casillas` (0.9.13) cada línea se puede desmarcar (gris, tachada y con `textoQueda`) y la cabecera lleva
 *  «Marcar todas»: marcada con todas, a medias con algunas; al pulsarla, desmarca si estaban todas y si no, marca todas. */
export function ListaLineas({ titulo, lineas, motivo, apagada = false, casillas }: Props) {
  const nMarcadas = casillas ? lineas.filter((p) => casillas.marcadas.has(p.idCompra)).length : 0
  const todas = nMarcadas === lineas.length
  return (
    <section aria-label={titulo} className="rounded-lg border border-fila-sep bg-superficie">
      <h4 className={cn('flex items-center gap-2 rounded-t-lg border-b border-fila-sep bg-crema px-2.5 py-1.5 text-[12px] font-bold', apagada ? 'text-texto-sub' : 'text-azul-medio')}>
        {casillas && lineas.length > 0 && (
          <Checkbox aria-label="Marcar todas" checked={todas ? true : nMarcadas === 0 ? false : 'indeterminate'}
            onCheckedChange={() => casillas.onAlternarTodas(!todas)} className="bg-superficie" />
        )}
        {titulo}
      </h4>
      <ul>
        {lineas.map((p) => {
          if (!casillas) {
            return (
              <li key={p.idCompra} className="flex items-center gap-2.5 border-b border-crema px-2.5 py-1.5 text-[12px] text-azul-medio last:border-b-0">
                <Datos p={p} />
                <span className="ml-auto">{motivo ? <span className="text-[11px] text-texto-sub">{motivo(p)}</span> : <BadgeEstadoPedido pedido={p} />}</span>
              </li>
            )
          }
          const marcada = casillas.marcadas.has(p.idCompra)
          return (
            <li key={p.idCompra} className="border-b border-crema last:border-b-0">
              <label className={cn('flex cursor-pointer items-center gap-2.5 px-2.5 py-1.5 text-[12px]', marcada ? 'text-azul-medio' : 'text-texto-sub')}>
                <Checkbox checked={marcada} onCheckedChange={(v) => casillas.onAlternar(p.idCompra, v === true)}
                  aria-label={`${p.tipoComponente} (ID ${p.idCompra})`} className="bg-superficie" />
                <Datos p={p} tachada={!marcada} />
                <span className="ml-auto">{marcada ? <BadgeEstadoPedido pedido={p} /> : <span className="text-[11px] italic">{casillas.textoQueda}</span>}</span>
              </label>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
```

- [ ] **Step 5: Implement — `AccionBloqueDialog` con `marcadas`**

En `src/modules/almacen/pedidos/bloques/AccionBloqueDialog.tsx`:
- el import de `./reglas` pasa a `import { lineasQueCambian, motivoNoCambia, textoDesmarcadas, textoQueda, type Bloque } from './reglas'`;
- `TEXTOS` y `Props` no cambian;
- sustituye el comentario y la función (:19-52):

```tsx
/** Confirmación de una acción de bloque (spec §6.5): qué cambia y qué no, con su motivo. 0.9.13: al confirmar, recibir
 *  o cancelar, cada línea que cambia lleva su casilla, todas marcadas al abrir, y solo se mandan las marcadas. Borrar
 *  sigue sin casillas: el servidor exige el bloque entero. El servidor comprueba la lista entera (todo o nada). */
export function AccionBloqueDialog({ bloque, accion, onCerrar }: Props) {
  const aplicar = useAccionBloque()
  const { mostrarAviso } = useAlerta()
  const [error, setError] = useState<string | null>(null)
  const t = TEXTOS[accion]
  const cambian = lineasQueCambian(bloque, accion)
  const quedan = bloque.lineas.filter((p) => !cambian.includes(p))
  const conCasillas = accion !== 'borrar'
  const [marcadas, setMarcadas] = useState<Set<number>>(() => new Set(cambian.map((p) => p.idCompra)))
  const elegidas = conCasillas ? cambian.filter((p) => marcadas.has(p.idCompra)) : cambian
  const desmarcadas = cambian.length - elegidas.length

  function alternar(idCompra: number, marcada: boolean) {
    setMarcadas((m) => {
      const n = new Set(m)
      if (marcada) n.add(idCompra)
      else n.delete(idCompra)
      return n
    })
    setError(null)
  }
  function alternarTodas(marcar: boolean) {
    setMarcadas(marcar ? new Set(cambian.map((p) => p.idCompra)) : new Set())
    setError(null)
  }

  async function confirmar() {
    setError(null)
    try {
      await aplicar.mutateAsync({ idBloque: bloque.idBloque as number, accion, lineas: elegidas.map(vistaDe) })
      onCerrar()
    } catch (e) {
      tratarFalloBloque(e, onCerrar, mostrarAviso, setError)
    }
  }

  const cuenta = elegidas.length === cambian.length ? String(cambian.length) : `${elegidas.length} de ${cambian.length}`
  return (
    <DialogoAlmacen abierto ancho={520} titulo={`${t.titulo} · Bloque ${bloque.idBloque}`}
      subtitulo={`${bloque.nombreProveedor} · ${formatear(bloque.fecha, 'dd/MM/yyyy')}`} error={error}
      textoAccion={t.boton(elegidas.length)} accionDeshabilitada={accion !== 'borrar' && elegidas.length === 0} peligro={t.peligro} enviando={aplicar.isPending}
      onConfirmar={() => void confirmar()} onCancelar={onCerrar}>
      <div className="flex max-h-[50vh] flex-col gap-2.5 overflow-y-auto">
        <ListaLineas titulo={`${t.cambian} (${cuenta})`} lineas={cambian}
          casillas={conCasillas ? { marcadas, onAlternar: alternar, onAlternarTodas: alternarTodas, textoQueda: textoQueda(accion) } : undefined} />
        {desmarcadas > 0 && <p role="status" className="text-[12px] text-azul-gris">{textoDesmarcadas(accion, desmarcadas)}</p>}
        {quedan.length > 0 && (
          <ListaLineas titulo={`No cambian (${quedan.length})`} lineas={quedan} motivo={(p) => motivoNoCambia(p, accion)} apagada />
        )}
      </div>
    </DialogoAlmacen>
  )
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `npx vitest run src/modules/almacen/pedidos/`
Expected: PASS. Incluye los tests de antes de `dialogos.test.tsx` («Recibir (1)», «Confirmar (0)» deshabilitado, 409, 422) y `PedidosPage.bloques.test.tsx` «⋮ del bloque → «Recibir todo» confirma y manda las en camino».

Run: `npx vitest run src/shared/ui/`
Expected: PASS. Ningún test de `Checkbox` se rompe con el icono nuevo.

Run: `npm run typecheck && npx eslint src/modules/almacen/pedidos/bloques src/shared/ui/checkbox.tsx`
Expected: sin errores.

- [ ] **Step 7: Commit**

```bash
git add src/shared/ui/checkbox.tsx src/modules/almacen/pedidos/bloques/ListaLineas.tsx src/modules/almacen/pedidos/bloques/AccionBloqueDialog.tsx src/modules/almacen/pedidos/bloques/dialogos.test.tsx
git commit -m "feat: casillas en confirmar, recibir y cancelar bloque para desmarcar lineas"
```

---

### Task 3: «Exportar bloque en CSV» y menú del bloque para todos los roles

**Files:**
- Modify: `src/modules/almacen/pedidos/PedidosPage.tsx` (comentario :72-74, `alElegirBloque` :221-245, `entradasDe` y `conMenuBloque` :249-250)
- Test: `src/modules/almacen/pedidos/PedidosPage.bloques.test.tsx` (test «el administrador ve los bloques sin ⋮» :161-166 y uno nuevo)

**Interfaces:**
- Consumes:
  - de la tarea 1, `entradasMenuBloque(b, otrosVivos, puedeEditar)` y la acción `'exportar'` de `AccionMenuBloque`;
  - lo que ya existe: `descargarCsv(nombreBase, cabeceras, filas)` de `@/shared/lib/csv`, y `CABECERAS_CSV_PEDIDOS` y `filaCsvPedido` de `./columnas`, que ya se importan en `PedidosPage.tsx`.
- Produces: nada que usen otras tareas.

- [ ] **Step 1: Write the failing tests**

En `PedidosPage.bloques.test.tsx`, añade a los imports:

```ts
import * as csv from '@/shared/lib/csv'
import { CABECERAS_CSV_PEDIDOS } from './columnas'
```

Sustituye el test `'el administrador ve los bloques sin ⋮'` por:

```tsx
  it('el administrador ve el ⋮ del bloque solo con «Exportar bloque en CSV» (0.9.13)', async () => {
    montar(SESION_ADMIN)
    await screen.findByText('lcd-x-negro')
    expect(screen.getByText('Bloque 41')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Acciones del bloque 41' }))
    expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual(['Exportar bloque en CSV'])
  })
```

Y tras el test `'⋮ del bloque → «Recibir todo» confirma y manda las en camino'`:

```tsx
  it('⋮ del bloque → «Exportar bloque en CSV» descarga todas sus líneas aunque un filtro oculte alguna (0.9.13)', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockResolvedValue(undefined)
    montar()
    await screen.findByText('lcd-x-negro')
    await userEvent.type(screen.getByPlaceholderText('Buscar componente o nº de bloque…'), 'bat')
    await waitFor(() => expect(screen.queryByText('lcd-x-negro')).not.toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Acciones del bloque 41' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Exportar bloque en CSV' }))
    expect(descargar).toHaveBeenCalledTimes(1)
    const [nombre, cabeceras, filasCsv] = descargar.mock.calls[0]
    expect(nombre).toBe('pedidos_bloque_41')
    expect(cabeceras).toEqual(CABECERAS_CSV_PEDIDOS)
    expect(filasCsv.map((f) => f[0])).toEqual(['1', '2'])
    expect(escrituras).toEqual([])
    descargar.mockRestore()
  })
```

Busca otros tests que den por hecho que el admin o el técnico no tienen ⋮: `grep -rn "Acciones del bloque" src --include=*.test.tsx`. Si alguno espera que no esté, cámbialo a la regla nueva (⋮ con solo «Exportar bloque en CSV»).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npx vitest run src/modules/almacen/pedidos/PedidosPage.bloques.test.tsx`
Expected: FAIL. El admin no tiene botón «Acciones del bloque 41», y «Exportar bloque en CSV» no llama a `descargarCsv`.

- [ ] **Step 3: Implement**

En `PedidosPage.tsx`:

1. En el comentario de `PedidosPage` (:72-74), tras «por bloques (spec «Pedidos por bloques» §6), con interruptor a la vista plana.» añade: «0.9.13: el menú del bloque lo ven todos; sin edición, solo con «Exportar bloque en CSV».».

2. En `alElegirBloque`, añade tras `case 'nota': … return`:

```ts
      case 'exportar':
        // En el mismo clic, sin nada asíncrono antes: la ventana «Guardar como» exige un gesto reciente (doc de descargarCsv).
        // Todas las líneas del bloque, aunque los filtros oculten alguna.
        void descargarCsv(`pedidos_bloque_${b.idBloque}`, CABECERAS_CSV_PEDIDOS, b.lineas.map(filaCsvPedido))
        return
```

3. En el `useMemo` de `grupos`:

```ts
    const entradasDe = (b: Bloque) => entradasMenuBloque(b, vivosDelProveedor(bloques, b.idProv, b.clave).length, puedeEditar)
    const conMenuBloque = (b: Bloque | undefined): b is Bloque => b !== undefined && b.clave !== SIN_BLOQUE
```

Las dependencias del `useMemo` ya incluyen `puedeEditar`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npx vitest run src/modules/almacen/pedidos/`
Expected: PASS.

Run: `npm run typecheck && npx eslint src/modules/almacen/pedidos`
Expected: sin errores.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/pedidos/PedidosPage.tsx src/modules/almacen/pedidos/PedidosPage.bloques.test.tsx
git commit -m "feat: exportar bloque en CSV desde su menu, tambien para admin y tecnico"
```

---

### Task 4: E2E de las casillas y de «Exportar bloque en CSV»

**Files:**
- Modify: `tests/e2e/bloques.spec.ts` (test «Recibir todo suma stock y «Revertir a En camino» lo devuelve» :338-362; test nuevo tras «CSV con «Bloque» y «Nota del bloque» tras «ID»» :425-438)

**Interfaces:**
- Consumes:
  - el comportamiento de las tareas 2 y 3: casilla `«{tipo} (ID {id})»`, títulos «(2 de 3)», el aviso, la entrada «Exportar bloque en CSV» y el fichero `pedidos_bloque_{n}_yyyy-MM-dd_HH-mm.csv`;
  - los helpers del fichero: `elegirEnMenuBloque`, `lineas`, `linea`, `stockDelSku`, `cabecera`, `celdaId`, `readFileSync`, y las variables `bloque1` y `stockInicial`.
- Produces: nada.

Las E2E corren contra preproducción, cuando la web está desplegada allí. En esta tarea basta con que compilen y Playwright las liste.

- [ ] **Step 1: Cambia el test de «Recibir todo»**

Sustituye el test entero (:338-362) por:

```ts
  test('Recibir todo con una línea desmarcada recibe solo las marcadas y «Revertir a En camino» lo devuelve', async () => {
    const page = sup!
    const del1 = (await lineas()).filter((l) => l.idBloque === bloque1)
    const [fuera, ...marcadas] = del1
    const unidades = marcadas.reduce((s, l) => s + l.cantidad, 0)

    await elegirEnMenuBloque(page, bloque1, /^Recibir todo \(3\)/)
    const dlg = page.getByRole('dialog', { name: `Recibir todo · Bloque ${bloque1}`, exact: true })
    await expect(dlg).toContainText('Se reciben (suman al stock) (3)')
    // 0.9.13: la línea que no ha llegado se desmarca y se queda en camino.
    await dlg.getByRole('checkbox', { name: new RegExp(`\\(ID ${fuera.idCompra}\\)$`) }).click()
    await expect(dlg).toContainText('Se reciben (suman al stock) (2 de 3)')
    await expect(dlg).toContainText('Se queda en camino 1 línea: la desmarcada no cambia.')
    await dlg.getByRole('button', { name: 'Recibir (2)' }).click()
    await expect(dlg).toBeHidden()

    await expect.poll(async () => (await lineas()).filter((l) => l.idBloque === bloque1 && l.estado === 'recibido').length).toBe(2)
    expect((await linea(fuera.idCompra))?.estado).toBe('en_camino')
    await expect.poll(() => stockDelSku(page)).toBe(stockInicial! + unidades)
    await expect(cabecera(page, bloque1)).toContainText('2 recibido')

    for (const l of marcadas) {
      await celdaId(page, l.idCompra).click({ button: 'right' })
      await page.getByRole('menuitem', { name: 'Revertir a En camino' }).click()
      const confirmacion = page.getByRole('dialog', { name: 'Revertir a En camino', exact: true })
      await confirmacion.getByRole('button', { name: 'Revertir a En camino', exact: true }).click()
      await expect(confirmacion).toBeHidden()
      await expect.poll(async () => (await linea(l.idCompra))?.estado).toBe('en_camino')
    }
    await expect.poll(() => stockDelSku(page)).toBe(stockInicial!)
  })
```

Los tests de después cuentan con que las tres líneas del bloque 1 acaben en camino, y así siguen. Por ejemplo, «Cancelar bloque…» comprueba que `l4` está en camino.

- [ ] **Step 2: Añade el test de «Exportar bloque en CSV»**

Tras el test `'CSV con «Bloque» y «Nota del bloque» tras «ID»'`, como último test del `describe`:

```ts
  test('«Exportar bloque en CSV» descarga solo las líneas de ese bloque', async () => {
    const page = sup!
    // Sin el selector de ficheros del navegador: descarga directa por enlace (src/shared/lib/csv.ts).
    await page.evaluate(() => { Reflect.set(globalThis, 'showSaveFilePicker', undefined) })
    const descarga = page.waitForEvent('download')
    await elegirEnMenuBloque(page, bloque1, /^Exportar bloque en CSV/)
    const fichero = await descarga
    expect(fichero.suggestedFilename()).toMatch(new RegExp(`^pedidos_bloque_${bloque1}_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.csv$`))
    const texto = readFileSync(await fichero.path(), 'utf8')
    const filasCsv = (texto.charCodeAt(0) === 0xfeff ? texto.slice(1) : texto).split(String.fromCharCode(10)).map((l) => l.trim()).filter((l) => l !== '')
    expect(filasCsv[0].startsWith('ID;Bloque;Nota del bloque;')).toBe(true)
    const esperadas = (await lineas()).filter((l) => l.idBloque === bloque1).map((l) => String(l.idCompra)).sort()
    expect(filasCsv.slice(1).map((l) => l.split(';')[0]).sort()).toEqual(esperadas)
  })
```

- [ ] **Step 3: Comprueba que compila y que Playwright lo lista**

Run: `npm run typecheck`. Expected: sin errores; `tsconfig.node.json` incluye `tests/e2e/**/*.ts`.
Run: `npx eslint tests/e2e/bloques.spec.ts`. Expected: sin errores.
Run: `npx playwright test tests/e2e/bloques.spec.ts --list`. Expected: lista los tests del fichero, incluidos «Recibir todo con una línea desmarcada…» y ««Exportar bloque en CSV» descarga solo las líneas de ese bloque». No los ejecuta.

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/bloques.spec.ts
git commit -m "test: e2e de recibir todo con una linea desmarcada y de exportar bloque en CSV"
```

---

### Task 5: Versión 0.9.13 y documentación

**Files:**
- Modify: `package.json`, `package-lock.json` (versión)
- Modify: `CHANGELOG.md` (entrada `[0.9.13]` arriba)
- Modify: `docs/paridad/pedidos.md` (sección nueva al final)
- Create: `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.13.md`. Va en la raíz, no en el worktree, y **no se hace commit** de ella en esta tarea: la sube el controlador en la raíz junto con el gitlink de la web.

**Interfaces:**
- Consumes: el comportamiento de las tareas 1 a 4.
- Produces: nada.

- [ ] **Step 1: Versión**

Run: `npm version 0.9.13 --no-git-tag-version`
Expected: `v0.9.13`. `package.json` y `package-lock.json`, las dos apariciones de la raíz del lock, pasan a `0.9.13`.

- [ ] **Step 2: CHANGELOG**

En `CHANGELOG.md`, tras la línea `El formato sigue [Keep a Changelog]…` y su línea en blanco, antes de `## [0.9.11] …`:

```markdown
## [0.9.13] - 2026-10-XX — Casillas en las acciones de bloque y «Exportar bloque en CSV»

- **Casillas en «Confirmar bloque», «Recibir todo» y «Cancelar bloque».** Al abrir, todas las líneas que cambian salen marcadas; se desmarcan las que no deben cambiar (por ejemplo, las que no han llegado) y «Marcar todas» marca o desmarca todas. El título y el botón cuentan las marcadas («Se reciben (suman al stock) (36 de 39)», «Recibir (36)»); una desmarcada sale en gris y tachada con «se queda en camino» («se queda pendiente» al confirmar), y debajo se avisa de cuántas no cambian. Las parciales siguen fuera, en «No cambian», y «Borrar bloque» sigue sin casillas.
- **«Exportar bloque en CSV»** en el menú del bloque (⋮ o clic derecho en su cabecera): descarga todas las líneas de ese bloque, aunque haya filtros, con las columnas del CSV de Pedidos, en `pedidos_bloque_<n>_<fecha>.csv`. El administrador y el técnico también ven el ⋮, solo con esta entrada.
- Solo web: sin cambios en el servidor, la base ni nginx.
```

La fecha `2026-10-XX` se cambia por la real al desplegar en producción, como en la 0.9.11. Si la 0.9.12 (otra sesión) llega antes a `main`, su entrada queda debajo de esta al hacer el merge.

- [ ] **Step 3: Ficha de paridad**

Añade al final de `docs/paridad/pedidos.md`, con una línea en blanco antes:

```markdown
## 0.9.13 — Casillas en las acciones de bloque y «Exportar bloque en CSV» (sin equivalente en el JavaFX)

Diferencias deliberadas, decididas en la spec `2026-10-14-v0913-casillas-y-exportar-bloque-design.md` (raíz):

- «Confirmar bloque», «Recibir todo» y «Cancelar bloque» con una casilla por cada línea que cambia, todas marcadas al abrir, y «Marcar todas»; solo se mandan las marcadas. «Borrar bloque» sin casillas; las parciales siguen en «No cambian» (`dialogos.test.tsx` «AccionBloqueDialog con casillas (0.9.13)», `bloques.spec.ts` «Recibir todo con una línea desmarcada…»).
- «Exportar bloque en CSV» en el menú del bloque: todas las líneas del bloque aunque haya filtros, en `pedidos_bloque_<n>_<fecha>.csv` (`PedidosPage.bloques.test.tsx`, `bloques.spec.ts`).
- El menú del bloque también lo ven el administrador y el técnico, solo con «Exportar bloque en CSV» (`reglas.test.ts`, `PedidosPage.bloques.test.tsx`).
```

- [ ] **Step 4: Novedades (raíz, sin commit)**

Crea `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.13.md`:

```markdown
# 🎉 Novedades — Versión 0.9.13 (web)

Dos mejoras en los bloques de **Pedidos**.

---

## ☑️ Elegir qué líneas cambian

En **«Confirmar bloque»**, **«Recibir todo»** y **«Cancelar bloque»** cada línea lleva una **casilla**:
- Al abrir, todas salen marcadas.
- **Desmarca** las que no deben cambiar. Por ejemplo: de un bloque de 39 líneas han llegado 36; desmarcas las 3 que
  faltan y pulsas «Recibir (36)». Las 3 se quedan en camino.
- La casilla de arriba marca o desmarca **todas** a la vez.
- Las **parciales** no entran: se siguen recibiendo o cerrando desde su fila («Recibir resto» o «Cerrar sin resto»).

---

## 📄 Exportar un bloque

En el menú del bloque (**⋮** o clic derecho en su cabecera) está **«Exportar bloque en CSV»**:
- Descarga **todas las líneas de ese bloque**, aunque tengas filtros puestos.
- Lleva las mismas columnas que el CSV de Pedidos.
- También lo pueden usar el administrador y los técnicos.
```

- [ ] **Step 5: Comprobación completa**

Run: `npm run check`
Expected: lint, typecheck y todos los tests de Vitest en verde.

- [ ] **Step 6: Commit (solo el worktree de la web)**

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/pedidos.md
git commit -m "docs: version 0.9.13, changelog y paridad de las casillas y exportar bloque"
```
