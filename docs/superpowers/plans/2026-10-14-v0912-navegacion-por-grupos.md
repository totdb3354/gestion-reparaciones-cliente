# 0.9.12 — Navegación por grupos: plan de implementación

> **Sale como 0.9.14** (2026-10-14): la 0.9.13 (casillas en las acciones de bloque y «Exportar bloque en CSV») entró antes en `main`. Los nombres de fichero conservan `v0912`; en el código, la web y los documentos, la versión es 0.9.14.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** toda la navegación de la web pasa a la columna lateral. Cada sección es un grupo con una cabecera plegable. La barra navy pierde los botones de sección, y Gestión sale del menú de usuario.

**Arquitectura:**
- Una función pura, `gruposNav`, construye los grupos del rol.
- `preferencias.ts` recuerda los grupos plegados.
- Un componente nuevo, `BadgeSumaLateral`, suma los números de un grupo plegado.
- `SubNav` pinta los grupos sobre la columna plegable de la 0.9.10.
- `TopBar` y `UserMenu` adelgazan, y desaparece el store `ultimaRutaStock`, que solo usaba la barra.

**Tecnologías:** React 19, React Router 7, Tailwind 4 (tokens de `src/shared/styles/tokens.css`), lucide-react 1.46.0, Radix Tooltip (`src/shared/ui/tooltip.tsx`), TanStack Query, Vitest con Testing Library y msw, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-14-v0912-navegacion-por-grupos-design.md`.
**Prototipo aprobado:** `docs/superpowers/specs/assets/2026-10-14-v0912-prototipo-navegacion-grupos.html`, modo «Nueva».

## Restricciones globales

- **Solo web** (`gestion-reparaciones-web`): sin cambios en el servidor, la base ni nginx.
- **Dónde se trabaja:**
  - en el worktree `C:/Users/dev/Documents/wt/web-0912`, rama `feature/navegacion-por-grupos`, creada desde `main` de la web (`4b4a1bf`, que ya trae la 0.9.11);
  - **no se toca** la copia principal `C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web` ni otros worktrees.
- **Grupos, en este orden:**
  - **REPARACIONES:** las pestañas de hoy según el rol.
  - **STOCK:** Stock actual, Pedidos y Proveedores.
  - **ESTADÍSTICAS:** Rendimiento → `/estadisticas`, con el icono `ChartColumn`.
  - **CLIENTES:** Listado → `/clientes`, con el icono `Users`.
  - **GESTIÓN (solo el admin):** Técnicos → `/gestion/tecnicos` (`UserCog`) y Logs → `/gestion/logs` (`ScrollText`). Sus enlaces llevan `state: { volverA: <ruta actual> }`.
- **Títulos de los grupos:** `Reparaciones`, `Stock`, `Estadísticas`, `Clientes` y `Gestión`. Se escriben así y se pintan en mayúsculas con CSS (`uppercase`).
- **Cabecera de grupo, con la columna abierta:**
  - es un botón: 10,5 px, `font-extrabold`, `tracking-wide`, `uppercase`, `text-azul-gris`;
  - a la derecha, un `ChevronDown` de 14 px (`size-3.5`) que gira `-rotate-90` con el grupo plegado;
  - `aria-expanded` y `aria-controls` = `columna-lateral-<id>`;
  - no navega.
- **Grupo plegado:** sus pestañas no se pintan, y la cabecera lleva la suma de sus números (`BadgeSumaLateral`). Si la página actual está dentro, la cabecera va en `text-azul-noche`.
- **Columna plegada (56 px):**
  - sin cabeceras;
  - todos los iconos, también los de los grupos plegados;
  - una línea `h-px bg-fila-sep mx-1 my-1.5` (con `aria-hidden`) antes de cada grupo menos el primero;
  - el nombre del enlace (`aria-label` y tooltip) es `«<Título del grupo> · <Pestaña>»`, por ejemplo `Stock · Pedidos`.
- **Preferencias del navegador:**
  - `columnaLateral.plegada`, igual que en la 0.9.10;
  - nueva `columnaLateral.gruposPlegados`: JSON con la lista de ids plegados. Un valor que no sea lista equivale a ninguno, y los ids desconocidos se ignoran. Leer y guardar van con `try/catch`.
- **Lo que no cambia de la 0.9.10:**
  - anchos 200/56 px, el botón redondo del borde y sus textos «Ocultar menú lateral» y «Mostrar menú lateral»;
  - `disableHoverableContent` y el mismo árbol en los dos estados (plegar la columna no remonta los enlaces);
  - los números compactos con la columna plegada.
- **Barra navy:** sin los botones de sección. **Menú de usuario:** sin «Gestionar técnicos» ni «Ver logs». Se borra `ultimaRutaStock`.
- **Colores:** solo utilidades de tokens; ningún color escrito a mano.
- **Commits:** en español, con prefijos `feat:`, `test:`, `docs:`, `chore:` y `refactor:`. **Sin línea `Co-Authored-By`.**
- **Datos de los tests:** sintéticos. Los repos son públicos.
- **E2E:** solo contra **preprod**, de una en una y con al menos 60 s entre ficheros. **Nunca** contra producción.

## Ficheros

| Fichero | Qué cambia |
|---|---|
| `src/shared/lib/enlaces.ts` | `Enlace.state?`, tipos `IdGrupoNav` y `GrupoNav` |
| `src/app/shell/navegacion.ts` (nuevo) + `navegacion.test.ts` | `gruposNav(sesion, pathname)` |
| `src/app/shell/preferencias.ts` + `preferencias.test.ts` | `CLAVE_GRUPOS_PLEGADOS`, `leerGruposPlegados()`, `guardarGruposPlegados()` |
| `src/modules/taller/componentes/BadgeSumaLateral.tsx` (nuevo) + test | Suma de la cabecera de un grupo plegado |
| `src/app/shell/SubNav.tsx` + `SubNav.test.tsx` | Columna por grupos |
| `src/test/server.ts` | Contadores de Pendientes a cero por defecto |
| `src/app/shell/TopBar.tsx` + `TopBar.test.tsx`, `src/app/shell/UserMenu.tsx` | Sin botones de sección; menú sin Gestión |
| `src/modules/almacen/estado.ts` (se borra); `StockPage.tsx`, `PedidosPage.tsx`, `ProveedoresPage.tsx` y sus tests | Fuera `ultimaRutaStock` |
| `tests/e2e/clientes.spec.ts`, `pedidos.spec.ts`, `stock.spec.ts`, `gestion.spec.ts`, `cliente-historial.spec.ts`, `columna-lateral.spec.ts` | Navegar por la columna |
| `package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/*.md`; raíz `docs/novedades/NOVEDADES-v0.9.12.md` | Versión y documentación |

Todas las rutas son relativas al worktree `C:/Users/dev/Documents/wt/web-0912`, salvo las de la raíz `C:/Users/dev/Documents/ProgramaReparaciones`.

---

### Tarea 1: Worktree, grupos y preferencia de grupos plegados

**Ficheros:**
- Modificar: `src/shared/lib/enlaces.ts`, `src/app/shell/preferencias.ts`, `src/app/shell/preferencias.test.ts`.
- Crear: `src/app/shell/navegacion.ts`, `src/app/shell/navegacion.test.ts`.

**Interfaces:**
- Produce:
  - `type IdGrupoNav = 'reparaciones' | 'stock' | 'estadisticas' | 'clientes' | 'gestion'`;
  - `type GrupoNav = { id: IdGrupoNav; titulo: string; enlaces: Enlace[] }`;
  - `Enlace` con `state?: { volverA: string }`;
  - `gruposNav(sesion: Sesion | null, pathname: string): GrupoNav[]`;
  - `CLAVE_GRUPOS_PLEGADOS = 'columnaLateral.gruposPlegados'`;
  - `leerGruposPlegados(): Set<IdGrupoNav>`;
  - `guardarGruposPlegados(ids: ReadonlySet<IdGrupoNav>): void`.

- [ ] **Paso 1: worktree e instalación**

```bash
git -C "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web" worktree add "C:/Users/dev/Documents/wt/web-0912" -b feature/navegacion-por-grupos main
cd "C:/Users/dev/Documents/wt/web-0912" && npm ci --no-audit --no-fund
cd "C:/Users/dev/Documents/wt/web-0912" && npx vitest run src/app/shell
```

Esperado: rama `feature/navegacion-por-grupos` sobre `4b4a1bf`, y los tests de `src/app/shell` en verde.

- [ ] **Paso 2: tests que fallan**

Crear `src/app/shell/navegacion.test.ts`:

```ts
import { ChartColumn, ScrollText, UserCog, Users } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { gruposNav } from './navegacion'

const resumen = (grupos: ReturnType<typeof gruposNav>) => grupos.map((g) => [g.id, g.titulo, g.enlaces.map((e) => e.label)])

describe('grupos de la columna lateral (0.9.12)', () => {
  it('técnico: Reparaciones con Pendientes, Stock, Estadísticas › Rendimiento y Clientes › Listado, sin Gestión', () => {
    expect(resumen(gruposNav(SESION_TEC, '/stock'))).toEqual([
      ['reparaciones', 'Reparaciones', ['Pendientes', 'Historial', 'IMEIs']],
      ['stock', 'Stock', ['Stock actual', 'Pedidos', 'Proveedores']],
      ['estadisticas', 'Estadísticas', ['Rendimiento']],
      ['clientes', 'Clientes', ['Listado']],
    ])
  })

  it('supertécnico: Asignaciones y Pendientes en Reparaciones, sin Gestión', () => {
    const grupos = gruposNav(SESION_SUPER, '/stock')
    expect(grupos.map((g) => g.id)).toEqual(['reparaciones', 'stock', 'estadisticas', 'clientes'])
    expect(grupos[0].enlaces.map((e) => e.label)).toEqual(['Asignaciones', 'Pendientes', 'Historial', 'IMEIs'])
  })

  it('admin: Gestión al final con Técnicos y Logs, que llevan volverA = la ruta actual', () => {
    const grupos = gruposNav(SESION_ADMIN, '/stock/proveedores')
    expect(grupos[0].enlaces.map((e) => e.label)).toEqual(['Asignaciones', 'Historial', 'IMEIs'])
    const gestion = grupos[grupos.length - 1]
    expect([gestion.id, gestion.titulo]).toEqual(['gestion', 'Gestión'])
    expect(gestion.enlaces.map((e) => [e.label, e.to, e.state])).toEqual([
      ['Técnicos', '/gestion/tecnicos', { volverA: '/stock/proveedores' }],
      ['Logs', '/gestion/logs', { volverA: '/stock/proveedores' }],
    ])
  })

  it('Rendimiento, Listado, Técnicos y Logs llevan sus iconos', () => {
    const enlaces = gruposNav(SESION_ADMIN, '/').flatMap((g) => g.enlaces)
    const de = (label: string) => enlaces.find((e) => e.label === label)
    expect([de('Rendimiento')?.to, de('Rendimiento')?.icono]).toEqual(['/estadisticas', ChartColumn])
    expect([de('Listado')?.to, de('Listado')?.icono]).toEqual(['/clientes', Users])
    expect(de('Técnicos')?.icono).toBe(UserCog)
    expect(de('Logs')?.icono).toBe(ScrollText)
  })

  it('sin sesión: sin Gestión y sin números', () => {
    const grupos = gruposNav(null, '/')
    expect(grupos.map((g) => g.id)).toEqual(['reparaciones', 'stock', 'estadisticas', 'clientes'])
    expect(grupos.flatMap((g) => g.enlaces).some((e) => e.badge)).toBe(false)
  })
})
```

En `src/app/shell/preferencias.test.ts`:
- cambiar la importación de `./preferencias` por `import { CLAVE_GRUPOS_PLEGADOS, CLAVE_PLEGADA, guardarGruposPlegados, guardarPlegada, leerGruposPlegados, leerPlegada } from './preferencias'`;
- añadir al final:

```ts
describe('grupos plegados de la columna lateral (0.9.12)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sin nada guardado, ninguno plegado', () => {
    expect(leerGruposPlegados()).toEqual(new Set())
  })

  it('guarda la lista y la lee', () => {
    guardarGruposPlegados(new Set(['stock', 'gestion']))
    expect(JSON.parse(localStorage.getItem(CLAVE_GRUPOS_PLEGADOS) ?? 'null')).toEqual(['stock', 'gestion'])
    expect(leerGruposPlegados()).toEqual(new Set(['stock', 'gestion']))
  })

  it('JSON roto o que no es una lista: ninguno plegado', () => {
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '{roto')
    expect(leerGruposPlegados()).toEqual(new Set())
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '{"stock":true}')
    expect(leerGruposPlegados()).toEqual(new Set())
  })

  it('ignora los ids que no son de un grupo', () => {
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '["stock","inventado",3]')
    expect(leerGruposPlegados()).toEqual(new Set(['stock']))
  })

  it('sin almacenamiento, ni leer ni guardar rompen', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('almacenamiento bloqueado')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('almacenamiento bloqueado')
    })
    expect(leerGruposPlegados()).toEqual(new Set())
    expect(() => guardarGruposPlegados(new Set(['stock']))).not.toThrow()
  })
})
```

- [ ] **Paso 3: comprobar que fallan**

Ejecutar: `npx vitest run src/app/shell/navegacion.test.ts src/app/shell/preferencias.test.ts`

Esperado: FAIL. `navegacion.test.ts` no encuentra el módulo `./navegacion`, y los tests nuevos de `preferencias` no encuentran las exportaciones.

- [ ] **Paso 4: implementar**

`src/shared/lib/enlaces.ts`, completo:

```ts
import type { LucideIcon } from 'lucide-react'

/** Entrada de la columna lateral (SubNav). Vive en shared desde el sub-proyecto 4a porque la usan taller y almacén, y un
 *  módulo no importa de otro. `end`: NavLink solo activo en la ruta exacta. `icono` (0.9.10): va delante del nombre y es
 *  lo único que se ve con la columna plegada. `state` (0.9.12): el que lleva la navegación (el `volverA` de Gestión). */
export type Enlace = {
  to: string
  label: string
  icono: LucideIcon
  badge?: 'pendientes' | 'asignaciones'
  end?: boolean
  state?: { volverA: string }
}

/** Grupos de la columna lateral (0.9.12, spec «Navegación por grupos»): una cabecera plegable y sus pestañas. */
export type IdGrupoNav = 'reparaciones' | 'stock' | 'estadisticas' | 'clientes' | 'gestion'
export type GrupoNav = { id: IdGrupoNav; titulo: string; enlaces: Enlace[] }
```

Crear `src/app/shell/navegacion.ts`:

```ts
import { ChartColumn, ScrollText, UserCog, Users } from 'lucide-react'
import { enlacesStock } from '@/modules/almacen/rutas'
import { enlacesReparaciones } from '@/modules/taller/rutas'
import type { GrupoNav } from '@/shared/lib/enlaces'
import { esAdmin, type Sesion } from '@/shared/session/storage'

/**
 * Grupos de la columna lateral (0.9.12, spec «Navegación por grupos» §2), en este orden: Reparaciones (sus pestañas
 * dependen del rol), Stock, Estadísticas, Clientes y, solo para el admin, Gestión. Las pestañas no repiten el nombre del
 * grupo (Estadísticas › Rendimiento, Clientes › Listado). Los enlaces de Gestión llevan `volverA` = la ruta actual para el
 * «Cerrar» de Técnicos y de Logs, como hacía el menú de usuario.
 */
export function gruposNav(sesion: Sesion | null, pathname: string): GrupoNav[] {
  const grupos: GrupoNav[] = [
    { id: 'reparaciones', titulo: 'Reparaciones', enlaces: enlacesReparaciones(sesion) },
    { id: 'stock', titulo: 'Stock', enlaces: enlacesStock() },
    { id: 'estadisticas', titulo: 'Estadísticas', enlaces: [{ to: '/estadisticas', label: 'Rendimiento', icono: ChartColumn }] },
    { id: 'clientes', titulo: 'Clientes', enlaces: [{ to: '/clientes', label: 'Listado', icono: Users }] },
  ]
  if (esAdmin(sesion)) {
    const state = { volverA: pathname }
    grupos.push({
      id: 'gestion',
      titulo: 'Gestión',
      enlaces: [
        { to: '/gestion/tecnicos', label: 'Técnicos', icono: UserCog, state },
        { to: '/gestion/logs', label: 'Logs', icono: ScrollText, state },
      ],
    })
  }
  return grupos
}
```

En `src/app/shell/preferencias.ts`:
- añadir arriba `import type { IdGrupoNav } from '@/shared/lib/enlaces'`;
- añadir al final:

```ts
/** Grupos plegados de la columna (0.9.12, spec «Navegación por grupos» §3): la misma preferencia del navegador. Un valor que
 *  no sea una lista equivale a ninguno plegado; los ids que no son de un grupo conocido se ignoran. */
export const CLAVE_GRUPOS_PLEGADOS = 'columnaLateral.gruposPlegados'
const IDS_GRUPOS: readonly IdGrupoNav[] = ['reparaciones', 'stock', 'estadisticas', 'clientes', 'gestion']
const esIdGrupo = (v: unknown): v is IdGrupoNav => typeof v === 'string' && (IDS_GRUPOS as readonly string[]).includes(v)

export function leerGruposPlegados(): Set<IdGrupoNav> {
  try {
    const valor: unknown = JSON.parse(localStorage.getItem(CLAVE_GRUPOS_PLEGADOS) ?? '[]')
    return new Set(Array.isArray(valor) ? valor.filter(esIdGrupo) : [])
  } catch {
    return new Set()
  }
}

export function guardarGruposPlegados(ids: ReadonlySet<IdGrupoNav>): void {
  try {
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, JSON.stringify([...ids]))
  } catch {
    // sin almacenamiento: la elección dura lo que la página
  }
}
```

- [ ] **Paso 5: comprobar que pasan**

Ejecutar: `npx vitest run src/app/shell/navegacion.test.ts src/app/shell/preferencias.test.ts` y después `npm run typecheck && npm run lint`.

Esperado: todo en verde.

- [ ] **Paso 6: commit**

```bash
git add src/shared/lib/enlaces.ts src/app/shell/navegacion.ts src/app/shell/navegacion.test.ts src/app/shell/preferencias.ts src/app/shell/preferencias.test.ts
git commit -m "feat: grupos de la columna lateral y preferencia de grupos plegados (0.9.12)"
```

---

### Tarea 2: Suma de números en la cabecera de un grupo plegado

**Ficheros:**
- Crear: `src/modules/taller/componentes/BadgeSumaLateral.tsx`, `src/modules/taller/componentes/BadgeSumaLateral.test.tsx`.

**Interfaces:**
- Consume:
  - `useContadoresPendientes()` (`src/modules/taller/api.ts`);
  - `useTotalAsignaciones({ sondea })` (`src/modules/taller/asignaciones/api.ts`);
  - `BadgeLateral({ total, activo, compacto? })`.
- Produce: `BadgeSumaLateral({ pendientes, asignaciones, sondeaAsignaciones }: { pendientes: boolean; asignaciones: boolean; sondeaAsignaciones: boolean })`.

- [ ] **Paso 1: test que falla**

Crear `src/modules/taller/componentes/BadgeSumaLateral.test.tsx`:

```tsx
import { act, screen, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { INTERVALO_CONECTADO_MS } from '@/shared/api/refresco'
import { renderConProviders, SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { BadgeSumaLateral } from './BadgeSumaLateral'

const filas = (prefijo: string, n: number) => Array.from({ length: n }, (_, i) => ({ idRep: `${prefijo}20260901_${i + 1}` }))

/** Contadores de Pendientes y las tres listas de asignaciones; cuenta las cargas de la de reparaciones. */
function datos(contadores: { reparaciones: number; glass: number; pulidos: number }, rep: number, glass: number, pul: number) {
  const cargas = { n: 0 }
  server.use(
    http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json(contadores)),
    http.get('*/api/reparaciones/asignaciones', () => {
      cargas.n += 1
      return HttpResponse.json(filas('A', rep))
    }),
    http.get('*/api/glass/asignaciones', () => HttpResponse.json(filas('AG', glass))),
    http.get('*/api/pulidos/asignaciones', () => HttpResponse.json(filas('AP', pul))),
  )
  return cargas
}

/** Suma de la cabecera de un grupo plegado (0.9.12, spec «Navegación por grupos» §3). */
describe('suma de números de un grupo plegado', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('suma Pendientes y Asignaciones, navy como sobre un enlace inactivo', async () => {
    datos({ reparaciones: 1, glass: 1, pulidos: 1 }, 2, 1, 1)
    renderConProviders(<BadgeSumaLateral pendientes asignaciones sondeaAsignaciones />, { sesion: SESION_SUPER })
    expect(await screen.findByText('7')).toHaveClass('bg-azul-noche', 'text-superficie')
  })

  it('solo Pendientes: no pide las listas de asignaciones', async () => {
    const cargas = datos({ reparaciones: 4, glass: 1, pulidos: 0 }, 2, 1, 1)
    renderConProviders(<BadgeSumaLateral pendientes asignaciones={false} sondeaAsignaciones />, { sesion: SESION_TEC })
    expect(await screen.findByText('5')).toBeInTheDocument()
    expect(cargas.n).toBe(0)
  })

  it('con tope 99+', async () => {
    datos({ reparaciones: 90, glass: 5, pulidos: 4 }, 1, 0, 0)
    renderConProviders(<BadgeSumaLateral pendientes asignaciones sondeaAsignaciones />, { sesion: SESION_SUPER })
    expect(await screen.findByText('99+')).toBeInTheDocument()
  })

  it('a cero no se pinta', async () => {
    const cargas = datos({ reparaciones: 0, glass: 0, pulidos: 0 }, 0, 0, 0)
    renderConProviders(<BadgeSumaLateral pendientes asignaciones sondeaAsignaciones />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cargas.n).toBe(1))
    await act(async () => { await new Promise((r) => setTimeout(r, 50)) })
    expect(screen.queryByText(/\d/)).not.toBeInTheDocument()
  })

  it('un grupo sin números no pinta nada', () => {
    renderConProviders(<BadgeSumaLateral pendientes={false} asignaciones={false} sondeaAsignaciones />, { sesion: SESION_SUPER })
    expect(screen.queryByText(/\d/)).not.toBeInTheDocument()
  })

  it('fuera de Asignaciones se mantiene fresco con el sondeo general (60 s)', async () => {
    const cargas = datos({ reparaciones: 0, glass: 0, pulidos: 0 }, 2, 1, 1)
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderConProviders(<BadgeSumaLateral pendientes asignaciones sondeaAsignaciones />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cargas.n).toBe(1))
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS) })
    await waitFor(() => expect(cargas.n).toBe(2))
  })

  it('con la vista de Asignaciones abierta no sondea su total por su cuenta (lo lleva la vista, D4)', async () => {
    const cargas = datos({ reparaciones: 0, glass: 0, pulidos: 0 }, 2, 1, 1)
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderConProviders(<BadgeSumaLateral pendientes asignaciones sondeaAsignaciones={false} />, { sesion: SESION_SUPER })
    expect(await screen.findByText('4')).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(INTERVALO_CONECTADO_MS * 2) })
    expect(cargas.n).toBe(1)
  })
})
```

- [ ] **Paso 2: comprobar que falla**

Ejecutar: `npx vitest run src/modules/taller/componentes/BadgeSumaLateral.test.tsx`

Esperado: FAIL porque no encuentra el módulo `./BadgeSumaLateral`.

- [ ] **Paso 3: implementar**

Crear `src/modules/taller/componentes/BadgeSumaLateral.tsx`:

```tsx
import { useContadoresPendientes } from '../api'
import { useTotalAsignaciones } from '../asignaciones/api'
import { BadgeLateral } from './BadgeLateral'

type Props = { pendientes: boolean; asignaciones: boolean; sondeaAsignaciones: boolean }
type Contadores = { reparaciones: number; glass: number; pulidos: number }

const totalPendientes = (c: Contadores | undefined) => (c ? c.reparaciones + c.glass + c.pulidos : undefined)

/**
 * Suma de los números de un grupo plegado de la columna lateral (0.9.12, spec «Navegación por grupos» §3): Pendientes,
 * Asignaciones o los dos, según las pestañas del grupo. Va en la cabecera, navy como sobre un enlace inactivo, con el
 * tope 99+ y oculta a cero (BadgeLateral). Con el grupo plegado sus pestañas no se pintan, así que esta suma es la que
 * mantiene vivos los sondeos. `sondeaAsignaciones`: con la vista de Asignaciones abierta su total no se sondea por su
 * cuenta (lo lleva la vista y su congelado D4), igual que BadgeAsignaciones.
 */
export function BadgeSumaLateral({ pendientes, asignaciones, sondeaAsignaciones }: Props) {
  if (pendientes && asignaciones) return <SumaAmbos sondeaAsignaciones={sondeaAsignaciones} />
  if (pendientes) return <SumaPendientes />
  if (asignaciones) return <SumaAsignaciones sondeaAsignaciones={sondeaAsignaciones} />
  return null
}

function SumaPendientes() {
  const { data } = useContadoresPendientes()
  return <BadgeLateral total={totalPendientes(data)} activo={false} />
}

function SumaAsignaciones({ sondeaAsignaciones }: { sondeaAsignaciones: boolean }) {
  const { data } = useTotalAsignaciones({ sondea: sondeaAsignaciones })
  return <BadgeLateral total={data} activo={false} />
}

function SumaAmbos({ sondeaAsignaciones }: { sondeaAsignaciones: boolean }) {
  const { data: contadores } = useContadoresPendientes()
  const { data: asignaciones } = useTotalAsignaciones({ sondea: sondeaAsignaciones })
  const pendientes = totalPendientes(contadores)
  const total = pendientes === undefined && asignaciones === undefined ? undefined : (pendientes ?? 0) + (asignaciones ?? 0)
  return <BadgeLateral total={total} activo={false} />
}
```

- [ ] **Paso 4: comprobar que pasa**

Ejecutar: `npx vitest run src/modules/taller/componentes/BadgeSumaLateral.test.tsx` y después `npm run typecheck && npm run lint`.

Esperado: 7/7 en verde y la salida limpia.

- [ ] **Paso 5: commit**

```bash
git add src/modules/taller/componentes/BadgeSumaLateral.tsx src/modules/taller/componentes/BadgeSumaLateral.test.tsx
git commit -m "feat: suma de los numeros de un grupo plegado de la columna (0.9.12)"
```

---

### Tarea 3: La columna por grupos

**Ficheros:**
- Modificar: `src/app/shell/SubNav.tsx` (se reescribe), `src/test/server.ts`.
- Test: `src/app/shell/SubNav.test.tsx` (se reescribe).

**Interfaces:**
- Consume:
  - `gruposNav(sesion, pathname)`, `GrupoNav`, `IdGrupoNav` y `Enlace.state` (Tarea 1);
  - `leerGruposPlegados()`, `guardarGruposPlegados()` y `CLAVE_GRUPOS_PLEGADOS` (Tarea 1);
  - `BadgeSumaLateral` (Tarea 2);
  - `leerPlegada()`, `guardarPlegada()` y `CLAVE_PLEGADA` (0.9.10).
- Produce: `SubNav()` con la columna por grupos. Los ids de las listas son `columna-lateral-<id>`.

- [ ] **Paso 1: respuesta por defecto de los contadores en los tests**

En `src/test/server.ts`, añadir este handler justo después de la línea `http.get('*/api/pulidos/asignaciones', () => HttpResponse.json([])),`:

```ts
  // 0.9.12: con la navegación por grupos, el badge de "Pendientes" (técnico y supertécnico) va en la columna en todas las
  // vistas, así que cualquier test que monte el layout con esas sesiones pide sus contadores. A cero no se pinta.
  http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 0 })),
```

- [ ] **Paso 2: reescribir los tests (fallan)**

Sustituir `src/app/shell/SubNav.test.tsx` por completo:

```tsx
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { INTERVALO_CONECTADO_MS } from '@/shared/api/refresco'
import { server } from '@/test/server'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { AppLayout } from './AppLayout'
import { SubNav } from './SubNav'
import { CLAVE_GRUPOS_PLEGADOS, CLAVE_PLEGADA } from './preferencias'

const columna = () => within(screen.getByRole('navigation', { name: 'Sub-navegación' }))
/** Cabecera de un grupo: su nombre accesible empieza por el título (plegado, le sigue la suma de sus números). */
const cabecera = (titulo: string) => screen.getByRole('button', { name: new RegExp(`^${titulo}`) })
const CABECERAS = /^(Reparaciones|Stock|Estadísticas|Clientes|Gestión)/

/** Destino de prueba que enseña el state con el que se llegó (el `volverA` de los enlaces de Gestión). */
function Destino({ texto }: { texto: string }) {
  const { state } = useLocation()
  return <p>{`${texto} ${JSON.stringify(state)}`}</p>
}

/** Filas mínimas para el total: el badge solo cuenta, así que basta con el id. */
const filas = (prefijo: string, n: number) => Array.from({ length: n }, (_, i) => ({ idRep: `${prefijo}20260901_${i + 1}` }))

/** Las tres listas de asignaciones (sin ?tecnico=) con n filas cada una; cuenta las cargas por la de reparaciones. */
function asignaciones(rep: number, glass: number, pul: number) {
  const cargas = { n: 0 }
  server.use(
    http.get('*/api/reparaciones/asignaciones', () => {
      cargas.n += 1
      return HttpResponse.json(filas('A', rep))
    }),
    http.get('*/api/glass/asignaciones', () => HttpResponse.json(filas('AG', glass))),
    http.get('*/api/pulidos/asignaciones', () => HttpResponse.json(filas('AP', pul))),
    http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 0 })),
  )
  return cargas
}

const avanzar = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })

/** Navegación por grupos (0.9.12, spec «Navegación por grupos»): toda la navegación en la columna. */
describe('columna lateral por grupos', () => {
  it('en cualquier ruta salen todos los grupos del rol, en orden, con sus pestañas', () => {
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/estadisticas' })
    expect(columna().getAllByRole('button', { name: CABECERAS }).map((b) => b.textContent)).toEqual(['Reparaciones', 'Stock', 'Estadísticas', 'Clientes'])
    expect(columna().getAllByRole('link').map((l) => l.textContent)).toEqual(['Pendientes', 'Historial', 'IMEIs', 'Stock actual', 'Pedidos', 'Proveedores', 'Rendimiento', 'Listado'])
    expect(columna().getByRole('link', { name: 'Rendimiento' })).toHaveAttribute('aria-current', 'page')
  })

  it('el admin tiene además Gestión, y en /gestion/logs la columna sigue con Logs marcado', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_ADMIN, ruta: '/gestion/logs' })
    expect(cabecera('Gestión')).toBeInTheDocument()
    expect(columna().getByRole('link', { name: 'Logs' })).toHaveAttribute('aria-current', 'page')
    expect(columna().getByRole('link', { name: 'Técnicos' })).toHaveAttribute('href', '/gestion/tecnicos')
  })

  it.each([
    ['Técnicos', '/gestion/tecnicos', 'TECNICOS'],
    ['Logs', '/gestion/logs', 'LOGS'],
  ])('"%s" navega a %s con volverA = la ruta desde la que se abrió', async (nombre, ruta, texto) => {
    renderConProviders(<AppLayout />, { sesion: SESION_ADMIN, ruta: '/stock/proveedores', rutas: <Route path={ruta} element={<Destino texto={texto} />} /> })
    await userEvent.click(columna().getByRole('link', { name: nombre }))
    expect(await screen.findByText(`${texto} {"volverA":"/stock/proveedores"}`)).toBeInTheDocument()
  })

  it('el supertécnico no tiene Gestión', () => {
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/stock' })
    expect(screen.queryAllByRole('button', { name: /^Gestión/ })).toHaveLength(0)
    expect(screen.queryByRole('link', { name: 'Técnicos' })).not.toBeInTheDocument()
  })

  it('"Stock actual" solo está activo en /stock exacto (NavLink end), no en /stock/pedidos', () => {
    renderConProviders(<SubNav />, { sesion: SESION_ADMIN, ruta: '/stock/pedidos' })
    expect(screen.getByRole('link', { name: 'Pedidos' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Stock actual' })).not.toHaveAttribute('aria-current')
  })

  it('cada enlace lleva su icono delante del nombre, decorativo', () => {
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/stock' })
    for (const nombre of ['Stock actual', 'Pedidos', 'Proveedores', 'Rendimiento', 'Listado']) {
      const enlace = columna().getByRole('link', { name: nombre })
      const icono = enlace.querySelector('svg')
      expect(icono).not.toBeNull()
      expect(icono).toHaveAttribute('aria-hidden', 'true')
      expect(enlace.firstElementChild).toBe(icono)
      expect(enlace.textContent).toBe(nombre)
    }
  })

  it('el shell la coloca junto al contenido en todas las vistas, también en Estadísticas', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC, ruta: '/estadisticas' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toBeInTheDocument()
  })

  it('usa el blanco de superficie del sistema de tokens', () => {
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toHaveClass('bg-superficie')
  })
})

describe('grupos plegables', () => {
  it('la cabecera pliega su grupo sin navegar, lo recuerda y lo despliega', async () => {
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/clientes' })
    const stock = cabecera('Stock')
    expect(stock).toHaveAttribute('aria-expanded', 'true')
    expect(stock).toHaveAttribute('aria-controls', 'columna-lateral-stock')
    await user.click(stock)
    expect(stock).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('link', { name: 'Pedidos' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Listado' })).toHaveAttribute('aria-current', 'page')
    expect(JSON.parse(localStorage.getItem(CLAVE_GRUPOS_PLEGADOS) ?? 'null')).toEqual(['stock'])
    await user.click(stock)
    expect(screen.getByRole('link', { name: 'Pedidos' })).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(CLAVE_GRUPOS_PLEGADOS) ?? 'null')).toEqual([])
  })

  it('arranca con los grupos que el navegador recuerda plegados', () => {
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '["reparaciones"]')
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/stock' })
    expect(cabecera('Reparaciones')).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('link', { name: 'Historial' })).not.toBeInTheDocument()
  })

  it('plegado, el grupo enseña en su cabecera la suma de sus números', async () => {
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '["reparaciones"]')
    asignaciones(2, 1, 1)
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 2, glass: 1, pulidos: 0 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/stock' })
    expect(await within(cabecera('Reparaciones')).findByText('7')).toHaveClass('bg-azul-noche', 'text-superficie')
  })

  it('plegado con la página actual dentro, la cabecera va en navy', () => {
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '["stock","clientes"]')
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/stock/pedidos' })
    expect(cabecera('Stock')).toHaveClass('text-azul-noche')
    expect(cabecera('Clientes')).toHaveClass('text-azul-gris')
  })

  it('los números de Pendientes y Asignaciones se ven estando en Stock', async () => {
    asignaciones(2, 1, 1)
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 5, glass: 0, pulidos: 0 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/stock' })
    expect(await within(screen.getByRole('link', { name: /Asignaciones/ })).findByText('4')).toBeInTheDocument()
    expect(await within(screen.getByRole('link', { name: /Pendientes/ })).findByText('5')).toBeInTheDocument()
  })
})

describe('número de Pendientes en la columna', () => {
  it('en Reparaciones pinta los enlaces del rol y el badge de Pendientes con tope 99+', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 90, glass: 10, pulidos: 5 })))
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' })
    const pendientes = screen.getByRole('link', { name: /Pendientes/ })
    expect(pendientes).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByText('99+')).toHaveClass('bg-superficie', 'text-azul-noche')
    expect(screen.getByRole('link', { name: 'IMEIs' })).toHaveAttribute('href', '/reparaciones/imeis')
    expect(screen.queryByRole('link', { name: 'Asignaciones' })).not.toBeInTheDocument()
  })
  it('el badge no se pinta a cero', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 0 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    expect(screen.getByRole('link', { name: 'Asignaciones' })).toHaveAttribute('href', '/reparaciones/asignaciones')
    await screen.findByRole('link', { name: 'Pendientes' })
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })
  it('el badge va navy sobre el enlace inactivo', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 5, glass: 2, pulidos: 1 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    const pendientes = await screen.findByRole('link', { name: /Pendientes/ })
    expect(pendientes).not.toHaveAttribute('aria-current', 'page')
    expect(await screen.findByText('8')).toHaveClass('bg-azul-noche', 'text-superficie')
  })
})

/** Calco de ReparacionControllerSuperTecnico.actualizarBadges: lblBadgeAsignaciones = getTotalItems() de la tabla
 *  unificada (rep + glass + pulido, sin filtros), con el setBadge de siempre (oculto a cero, tope 99+). */
describe('badge de Asignaciones en el lateral del supertécnico', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('pinta el total de las tres categorías, navy sobre el enlace inactivo', async () => {
    asignaciones(2, 1, 1)
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    const enlace = screen.getByRole('link', { name: /Asignaciones/ })
    expect(await within(enlace).findByText('4')).toHaveClass('bg-azul-noche', 'text-superficie')
  })

  it('con tope 99+', async () => {
    asignaciones(60, 30, 15)
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    expect(await within(screen.getByRole('link', { name: /Asignaciones/ })).findByText('99+')).toBeInTheDocument()
  })

  it('a cero no se pinta', async () => {
    const cargas = asignaciones(0, 0, 0)
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    await waitFor(() => expect(cargas.n).toBe(1))
    expect(screen.getByRole('link', { name: 'Asignaciones' }).textContent).toBe('Asignaciones')
  })

  it('el ADMIN no lo ve ni pide las listas (spec §12)', async () => {
    const cargas = asignaciones(2, 1, 1)
    renderConProviders(<SubNav />, { sesion: SESION_ADMIN, ruta: '/reparaciones/historial' })
    expect(screen.getByRole('link', { name: 'Asignaciones' }).textContent).toBe('Asignaciones')
    await act(async () => { await new Promise((r) => setTimeout(r, 50)) })
    expect(cargas.n).toBe(0)
  })

  it('fuera de Asignaciones se mantiene fresco con el sondeo general (60 s)', async () => {
    const cargas = asignaciones(2, 1, 1)
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    await waitFor(() => expect(cargas.n).toBe(1))
    await avanzar(INTERVALO_CONECTADO_MS)
    await waitFor(() => expect(cargas.n).toBe(2))
  })

  it('en Asignaciones va invertido y no sondea por su cuenta: el ritmo (y el congelado D4) lo lleva la vista', async () => {
    const cargas = asignaciones(2, 1, 1)
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/asignaciones' })
    const enlace = screen.getByRole('link', { name: /Asignaciones/ })
    expect(await within(enlace).findByText('4')).toHaveClass('bg-superficie', 'text-azul-noche')
    expect(cargas.n).toBe(1)
    await avanzar(INTERVALO_CONECTADO_MS * 2)
    expect(cargas.n).toBe(1)
  })
})

/** Columna plegable (0.9.10): el botón redondo del borde la pliega a 56 px y la despliega; el navegador lo recuerda. */
describe('plegar la columna lateral', () => {
  it('abierta: 200 px, con los nombres y el botón «Ocultar menú lateral»', () => {
    renderConProviders(<SubNav />, { ruta: '/stock' })
    const nav = screen.getByRole('navigation', { name: 'Sub-navegación' })
    expect(nav).toHaveClass('w-[200px]')
    const boton = screen.getByRole('button', { name: 'Ocultar menú lateral' })
    expect(boton).toHaveAttribute('title', 'Ocultar menú lateral')
    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(boton).toHaveAttribute('aria-controls', nav.id)
    expect(within(nav).getByRole('link', { name: 'Pedidos' }).textContent).toBe('Pedidos')
  })

  it('el botón la pliega a la franja de iconos, lo guarda y la vuelve a desplegar', async () => {
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock/pedidos' })
    const nav = screen.getByRole('navigation', { name: 'Sub-navegación' })

    await user.click(screen.getByRole('button', { name: 'Ocultar menú lateral' }))
    expect(nav).toHaveClass('w-14')
    const pedidos = within(nav).getByRole('link', { name: 'Stock · Pedidos' })
    expect(pedidos.textContent).toBe('')
    expect(pedidos.querySelector('svg')).not.toBeNull()
    expect(pedidos).toHaveAttribute('aria-current', 'page')
    expect(localStorage.getItem(CLAVE_PLEGADA)).toBe('si')
    const boton = screen.getByRole('button', { name: 'Mostrar menú lateral' })
    expect(boton).toHaveAttribute('aria-expanded', 'false')

    await user.click(boton)
    expect(nav).toHaveClass('w-[200px]')
    expect(within(nav).getByRole('link', { name: 'Pedidos' }).textContent).toBe('Pedidos')
    expect(localStorage.getItem(CLAVE_PLEGADA)).toBe('no')
  })

  it('plegada: sin cabeceras, todos los iconos (también los de grupos plegados) y una línea entre grupos', () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    localStorage.setItem(CLAVE_GRUPOS_PLEGADOS, '["stock"]')
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    const nav = screen.getByRole('navigation', { name: 'Sub-navegación' })
    expect(within(nav).queryAllByRole('button', { name: CABECERAS })).toHaveLength(0)
    expect(within(nav).getAllByRole('link').map((l) => l.getAttribute('aria-label'))).toEqual([
      'Reparaciones · Historial',
      'Reparaciones · IMEIs',
      'Stock · Stock actual',
      'Stock · Pedidos',
      'Stock · Proveedores',
      'Estadísticas · Rendimiento',
      'Clientes · Listado',
    ])
    expect(nav.querySelectorAll('div[aria-hidden="true"].h-px')).toHaveLength(3)
  })

  it('arranca plegada si el navegador lo recuerda', () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toHaveClass('w-14')
    expect(screen.getByRole('link', { name: 'Clientes · Listado' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Mostrar menú lateral' })).toBeInTheDocument()
  })

  it('anima el ancho, salvo si el sistema pide reducir movimiento', () => {
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toHaveClass('transition-[width]', 'motion-reduce:transition-none')
  })

  it('plegar y desplegar no remonta los enlaces', async () => {
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock' })
    const antes = screen.getByRole('link', { name: 'Pedidos' })
    await user.click(screen.getByRole('button', { name: 'Ocultar menú lateral' }))
    expect(screen.getByRole('link', { name: 'Stock · Pedidos' })).toBe(antes)
    await user.click(screen.getByRole('button', { name: 'Mostrar menú lateral' }))
    expect(screen.getByRole('link', { name: 'Pedidos' })).toBe(antes)
  })

  it('plegar no vuelve a pedir los números', async () => {
    const user = userEvent.setup()
    const cargas = asignaciones(2, 1, 1)
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    await waitFor(() => expect(cargas.n).toBe(1))
    await user.click(screen.getByRole('button', { name: 'Ocultar menú lateral' }))
    await act(async () => { await new Promise((r) => setTimeout(r, 50)) })
    expect(cargas.n).toBe(1)
  })
})

/** Plegada: los números siguen cada uno en su icono, compactos, y el nombre «Grupo · Pestaña» sale al pasar el ratón o al
 *  llegar con el teclado. */
describe('columna lateral plegada: números y nombre de cada icono', () => {
  it('los números de Asignaciones y Pendientes siguen, compactos en la esquina del icono y con sus colores', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    asignaciones(2, 1, 1)
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 90, glass: 10, pulidos: 5 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/pendientes' })
    const asig = screen.getByRole('link', { name: 'Reparaciones · Asignaciones' })
    expect(await within(asig).findByText('4')).toHaveClass('absolute', 'bg-azul-noche', 'text-superficie')
    const pend = screen.getByRole('link', { name: 'Reparaciones · Pendientes' })
    expect(await within(pend).findByText('99+')).toHaveClass('absolute', 'bg-superficie', 'text-azul-noche')
  })

  it('abierta, el número sigue detrás del nombre, como antes', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 1, glass: 1, pulidos: 1 })))
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/reparaciones/historial' })
    const numero = await within(screen.getByRole('link', { name: /Pendientes/ })).findByText('3')
    expect(numero).toHaveClass('ml-2')
    expect(numero).not.toHaveClass('absolute')
  })

  it('al pasar el ratón por un icono sale «Grupo · Pestaña»', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock' })
    await user.hover(screen.getByRole('link', { name: 'Stock · Proveedores' }))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Stock · Proveedores')
  })

  it('también al llegar con el teclado', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    renderConProviders(<SubNav />, { ruta: '/stock' })
    act(() => screen.getByRole('link', { name: 'Stock · Pedidos' }).focus())
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Stock · Pedidos')
  })

  it('al quitar el ratón del icono el nombre se va (no tapa el botón de la columna)', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock' })
    const enlace = screen.getByRole('link', { name: 'Stock · Proveedores' })
    await user.hover(enlace)
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Stock · Proveedores')
    await user.unhover(enlace)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
  })

  it('abierta no hay tooltip: el nombre ya se ve', async () => {
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock' })
    await user.hover(screen.getByRole('link', { name: 'Proveedores' }))
    await act(async () => { await new Promise((r) => setTimeout(r, 50)) })
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })
})
```

- [ ] **Paso 3: comprobar que fallan**

Ejecutar: `npx vitest run src/app/shell/SubNav.test.tsx`

Esperado: FAIL en los tests de grupos, en los de Gestión y en los nombres «Grupo · Pestaña». La columna actual solo pinta la sección de la ruta.

- [ ] **Paso 4: implementar**

Sustituir `src/app/shell/SubNav.tsx` por completo:

```tsx
import { ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { NavLink, matchPath, useLocation } from 'react-router'
import { BadgeAsignaciones } from '@/modules/taller/asignaciones/BadgeAsignaciones'
import { BadgePendientes } from '@/modules/taller/componentes/BadgePendientes'
import { BadgeSumaLateral } from '@/modules/taller/componentes/BadgeSumaLateral'
import type { Enlace, GrupoNav, IdGrupoNav } from '@/shared/lib/enlaces'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/shared/ui/tooltip'
import { gruposNav } from './navegacion'
import { guardarGruposPlegados, guardarPlegada, leerGruposPlegados, leerPlegada } from './preferencias'

/** Id de la columna: el botón de plegar la señala con `aria-controls`. Las listas de los grupos van en `<id>-<grupo>`. */
const ID_COLUMNA = 'columna-lateral'
/** Con la vista de Asignaciones abierta, la suma de su grupo plegado no sondea por su cuenta (D4). */
const RUTA_ASIGNACIONES = '/reparaciones/asignaciones'

/**
 * Columna lateral con toda la navegación de la web (0.9.12, spec «Navegación por grupos»; adaptación web, sin calco en el
 * JavaFX). Cada sección es un grupo con su cabecera plegable, y plegado enseña la suma de sus números. La columna se pliega
 * con el botón redondo del borde (0.9.10): abierta mide 200 px, con icono y nombre; plegada mide 56 px, con todos los
 * iconos, una línea entre grupos y el nombre «Grupo · Pestaña» al pasar el ratón. El navegador recuerda la columna y los
 * grupos plegados. Las tablas de las vistas se recolocan solas al cambiar el ancho (ResizeObserver de DataTable).
 */
export function SubNav() {
  const { pathname } = useLocation()
  const { sesion } = useSession()
  const [plegada, setPlegada] = useState(leerPlegada)
  const [gruposPlegados, setGruposPlegados] = useState(leerGruposPlegados)
  const grupos = gruposNav(sesion, pathname)
  const alternar = () => {
    setPlegada(!plegada)
    guardarPlegada(!plegada)
  }
  const alternarGrupo = (id: IdGrupoNav) => {
    const nuevos = new Set(gruposPlegados)
    if (nuevos.has(id)) nuevos.delete(id)
    else nuevos.add(id)
    setGruposPlegados(nuevos)
    guardarGruposPlegados(nuevos)
  }
  const accion = plegada ? 'Mostrar menú lateral' : 'Ocultar menú lateral'
  return (
    <nav
      id={ID_COLUMNA}
      aria-label="Sub-navegación"
      className={cn(
        'relative shrink-0 bg-superficie transition-[width] duration-[180ms] ease-in-out motion-reduce:transition-none',
        plegada ? 'w-14' : 'w-[200px]',
      )}
    >
      {/* overflow-hidden: al desplegar, los nombres aparecen antes de que la columna termine de crecer. A la derecha se
          deja sitio para el botón, que monta sobre el borde. */}
      <div className={cn('flex flex-col overflow-hidden py-2', plegada ? 'pr-3.5 pl-1.5' : 'pr-[18px] pl-2')}>
        {/* disableHoverableContent: el nombre se va en cuanto el ratón sale del icono; si no, plegada, su tooltip (a la
            derecha del primer icono) taparía el botón de desplegar y se llevaría sus clics. */}
        <TooltipProvider disableHoverableContent>
          {grupos.map((g, i) => (
            <GrupoLateral
              key={g.id}
              grupo={g}
              primero={i === 0}
              plegada={plegada}
              grupoPlegado={gruposPlegados.has(g.id)}
              onAlternar={() => alternarGrupo(g.id)}
              pathname={pathname}
            />
          ))}
        </TooltipProvider>
      </div>
      <button
        type="button"
        onClick={alternar}
        aria-label={accion}
        title={accion}
        aria-expanded={!plegada}
        aria-controls={ID_COLUMNA}
        className="absolute top-3.5 -right-3 z-10 flex size-6 cursor-pointer items-center justify-center rounded-full border border-pill-borde bg-superficie text-azul-medio shadow-sm hover:bg-seleccion-suave"
      >
        {plegada ? <ChevronRight aria-hidden className="size-3.5" /> : <ChevronLeft aria-hidden className="size-3.5" />}
      </button>
    </nav>
  )
}

type PropsGrupo = { grupo: GrupoNav; primero: boolean; plegada: boolean; grupoPlegado: boolean; onAlternar: () => void; pathname: string }

/**
 * Un grupo de la columna.
 * - Columna abierta: una cabecera (un botón que pliega y despliega el grupo, sin navegar) y sus pestañas. Con el grupo
 *   plegado no se pintan las pestañas, la cabecera suma sus números y, si la página actual está dentro, va en navy.
 * - Columna plegada: sin cabecera, con una línea antes de cada grupo menos el primero, y todas sus pestañas aunque el
 *   grupo esté plegado.
 * La lista de pestañas va siempre en el mismo sitio del árbol, así que plegar la columna no remonta los enlaces.
 */
function GrupoLateral({ grupo: g, primero, plegada, grupoPlegado, onAlternar, pathname }: PropsGrupo) {
  const idLista = `${ID_COLUMNA}-${g.id}`
  const dentro = g.enlaces.some((e) => matchPath({ path: e.to, end: e.end ?? false }, pathname) !== null)
  return (
    <div>
      {plegada ? (
        !primero && <div aria-hidden className="mx-1 my-1.5 h-px bg-fila-sep" />
      ) : (
        <button
          type="button"
          onClick={onAlternar}
          aria-expanded={!grupoPlegado}
          aria-controls={idLista}
          className={cn(
            'flex w-full cursor-pointer items-center gap-1.5 rounded-lg py-1 pr-2.5 pl-4 text-[10.5px] font-extrabold tracking-wide whitespace-nowrap uppercase hover:bg-azul-medio/5',
            !primero && 'mt-2',
            grupoPlegado && dentro ? 'text-azul-noche' : 'text-azul-gris hover:text-azul-medio',
          )}
        >
          {g.titulo}
          {grupoPlegado && (
            <BadgeSumaLateral
              pendientes={g.enlaces.some((e) => e.badge === 'pendientes')}
              asignaciones={g.enlaces.some((e) => e.badge === 'asignaciones')}
              sondeaAsignaciones={matchPath(RUTA_ASIGNACIONES, pathname) === null}
            />
          )}
          <ChevronDown
            aria-hidden
            className={cn('ml-auto size-3.5 shrink-0 transition-transform motion-reduce:transition-none', grupoPlegado && '-rotate-90')}
          />
        </button>
      )}
      <div id={idLista}>
        {(plegada || !grupoPlegado) && g.enlaces.map((e) => <EnlaceLateral key={e.to} enlace={e} grupo={g.titulo} plegada={plegada} />)}
      </div>
    </div>
  )
}

/** Un enlace de la columna. Plegada, solo el icono: el nombre «Grupo · Pestaña» pasa al `aria-label`, porque deja de verse,
 *  y a un tooltip que sale al pasar el ratón o al llegar con el teclado. Los números van compactos, en la esquina del
 *  icono. El árbol es el mismo plegada y abierta (el tooltip, controlado, solo se abre plegada), así que plegar no remonta
 *  el enlace ni sus números y no se vuelven a pedir los contadores. */
function EnlaceLateral({ enlace: e, grupo, plegada }: { enlace: Enlace; grupo: string; plegada: boolean }) {
  const [abierto, setAbierto] = useState(false)
  const nombre = `${grupo} · ${e.label}`
  const enlace = (
    <NavLink
      to={e.to}
      end={e.end}
      state={e.state}
      aria-label={plegada ? nombre : undefined}
      className={({ isActive }) =>
        cn(
          'relative flex w-full items-center rounded-3xl py-2.5 text-left text-[13px] font-bold whitespace-nowrap',
          plegada ? 'justify-center' : 'px-4',
          isActive ? 'bg-azul-noche text-texto-nav-activo' : 'text-azul-medio hover:bg-azul-medio/8',
        )
      }
    >
      {({ isActive }) => (
        <>
          <e.icono aria-hidden className={cn('size-[18px] shrink-0', !plegada && 'mr-2.5')} />
          {!plegada && e.label}
          {e.badge === 'pendientes' && <BadgePendientes activo={isActive} compacto={plegada} />}
          {e.badge === 'asignaciones' && <BadgeAsignaciones activo={isActive} compacto={plegada} />}
        </>
      )}
    </NavLink>
  )
  // El disparador es un <span> que envuelve el enlace, no el propio NavLink con asChild: el Slot de Radix junta los
  // className como texto y convertiría en texto la función de className de NavLink.
  return (
    <Tooltip open={plegada && abierto} onOpenChange={(v) => setAbierto(plegada && v)}>
      <TooltipTrigger asChild>
        <span className="block">{enlace}</span>
      </TooltipTrigger>
      <TooltipContent side="right" sideOffset={8}>
        {nombre}
      </TooltipContent>
    </Tooltip>
  )
}
```

- [ ] **Paso 5: comprobar que pasan, también la batería completa**

Ejecutar:
- `npx vitest run src/app/shell src/modules/taller/componentes`;
- `npm run typecheck && npm run lint`;
- `npm run test`: la columna se monta ahora con todos los grupos en todas las vistas.

Esperado: todo en verde. Si algún test de otra vista falla porque ahora la columna tiene un enlace con el mismo nombre que uno de su página, **parar y avisar** con la salida. No cambiar los nombres de las pestañas para esquivarlo.

- [ ] **Paso 6: commit**

```bash
git add src/app/shell/SubNav.tsx src/app/shell/SubNav.test.tsx src/test/server.ts
git commit -m "feat: columna lateral por grupos plegables con toda la navegacion (0.9.12)"
```

---

### Tarea 4: Barra navy sin botones de sección, menú sin Gestión y fuera la «última pestaña de Stock»

**Ficheros:**
- Modificar: `src/app/shell/TopBar.tsx`, `src/app/shell/TopBar.test.tsx`, `src/app/shell/UserMenu.tsx`.
- Modificar: `src/modules/almacen/stock/StockPage.tsx`, `src/modules/almacen/pedidos/PedidosPage.tsx`, `src/modules/almacen/proveedores/ProveedoresPage.tsx` y sus tests (`StockPage.test.tsx`, `PedidosPage.test.tsx`, `ProveedoresPage.test.tsx`).
- Borrar: `src/modules/almacen/estado.ts`.

**Interfaces:**
- Consume: la columna por grupos (Tarea 3), que ya lleva Técnicos y Logs con `volverA`.
- Produce:
  - `TopBar` sin botones de sección;
  - `UserMenu` sin las entradas de Gestión;
  - ningún import de `ultimaRutaStock`.

- [ ] **Paso 1: cambiar los tests de la barra (fallan)**

En `src/app/shell/TopBar.test.tsx`:
1. Quitar la importación `import { ultimaRutaStock } from '@/modules/almacen/estado'`.
2. Sustituir el test `'muestra título, versión, los 4 botones y el saludo para cualquier rol'` por:

```tsx
  it('muestra título, versión y saludo, y ya no tiene botones de sección (0.9.12: la navegación va en la columna)', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    expect(screen.getByText('FSGR:')).toBeInTheDocument()
    expect(screen.getByText(/Gestión de Stock y Reparaciones V\.\d+\.\d+\.\d+/)).toBeInTheDocument()
    expect(within(screen.getByRole('banner')).getAllByRole('link').map((l) => l.getAttribute('href'))).toEqual(['/'])
    expect(screen.getByText('Hola, tecnico_n')).toBeInTheDocument()
  })
```

3. Borrar los tests `'"Stock" lleva a la última pestaña de Stock visitada (caché de vista del JavaFX) y no se marca fuera de /stock'` y `it.each(['/stock', '/stock/pedidos', '/stock/proveedores'])('"Stock" sigue activo en %s aunque apunte a otra pestaña', …)`.
4. Sustituir `'el admin ve además "Gestionar técnicos" y "Ver logs"'` por:

```tsx
  it('el menú del admin ya no tiene Gestión: Técnicos y Logs están en la columna (0.9.12)', async () => {
    renderConProviders(<AppLayout />, { sesion: SESION_ADMIN })
    await userEvent.click(screen.getByRole('button', { name: /Hola, admin/ }))
    expect(screen.queryByRole('menuitem', { name: 'Gestionar técnicos' })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Ver logs' })).not.toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Cambiar contraseña' })).toBeInTheDocument()
  })
```

5. Borrar el `it.each([['Gestionar técnicos', …], ['Ver logs', …]])('"%s" navega a %s con volverA = la ruta desde la que se abrió', …)`. Ese comportamiento lo cubre ahora `SubNav.test.tsx`.
6. Si `Destino` y `useLocation` ya no se usan en el fichero (compruébalo con `grep -n "Destino\|useLocation" src/app/shell/TopBar.test.tsx`), borrar la función `Destino` y quitar `useLocation` de la importación de `react-router`. `Route` sigue haciendo falta.

En los tests de las páginas de Stock:
- **`src/modules/almacen/stock/StockPage.test.tsx`:** quitar `import { ultimaRutaStock } from '../estado'` y borrar el test `'al entrar guarda "/stock" como última pestaña de Stock (el botón "Stock" de la barra superior vuelve aquí)'`.
- **`src/modules/almacen/proveedores/ProveedoresPage.test.tsx`:** quitar `import { ultimaRutaStock } from '../estado'` y borrar el test `'al entrar guarda "/stock/proveedores" como última pestaña de Stock (S2: la barra superior vuelve a ella)'`.
- **`src/modules/almacen/pedidos/PedidosPage.test.tsx`:**
  - quitar `import { ultimaRutaStock } from '../estado'`;
  - en el test `'título, toggle en Componentes, columnas, cancelados al final, pie "Actualizado" y última pestaña de Stock'`, quitar la línea `ultimaRutaStock.set('/stock')` y la línea `expect(ultimaRutaStock.get()).toBe('/stock/pedidos')`, y renombrarlo a `'título, toggle en Componentes, columnas, cancelados al final y pie "Actualizado"'`;
  - en `'el toggle "Otros" cambia de ruta y de tabla, conserva los filtros y fija la última pestaña'`, quitar `expect(ultimaRutaStock.get()).toBe('/stock/pedidos/otros')` y renombrarlo a `'el toggle "Otros" cambia de ruta y de tabla y conserva los filtros'`.

- [ ] **Paso 2: comprobar que fallan**

Ejecutar: `npx vitest run src/app/shell/TopBar.test.tsx`

Esperado: FAIL en los dos tests cambiados, porque la barra aún tiene los botones y el menú aún tiene Gestión.

- [ ] **Paso 3: implementar**

`src/app/shell/TopBar.tsx`, completo:

```tsx
import { NavLink } from 'react-router'
import { Campana } from '@/modules/taller/notificaciones/Campana'
import { APP_VERSION } from '@/shared/lib/version'
import { UserMenu } from './UserMenu'

/** Barra superior navy de 64 px (calco de la navbar de MainView.fxml): logo, título con la versión, campana y usuario. Desde
 *  la 0.9.12 (spec «Navegación por grupos») ya no lleva los botones de sección: toda la navegación va en la columna. */
export function TopBar() {
  return (
    <header className="flex h-navbar items-center gap-2.5 bg-azul-noche px-5">
      <NavLink to="/" className="rounded-lg px-2 py-1.5 hover:bg-white/8">
        <img src="/logoNavBar.png" alt="Inicio" className="h-[38px]" />
      </NavLink>
      <span className="text-[13px] font-bold text-crema">FSGR:</span>
      <span className="hidden text-[13px] text-crema md:inline">Gestión de Stock y Reparaciones V.{APP_VERSION}</span>
      <div className="flex-1" />
      <Campana />
      <UserMenu />
    </header>
  )
}
```

En `src/app/shell/UserMenu.tsx`:
- borrar el bloque `{esAdmin(sesion) && ( <> …Gestionar técnicos… …Ver logs… <DropdownMenuSeparator /> </> )}` entero;
- borrar la línea `const { pathname } = useLocation()`;
- quitar `useLocation` de `import { useLocation, useNavigate } from 'react-router'`, que queda `import { useNavigate } from 'react-router'`;
- quitar `import { esAdmin } from '@/shared/session/storage'`;
- encima del `DropdownMenuContent`, añadir el comentario `{/* Gestión (Técnicos y Logs) está en la columna desde la 0.9.12. */}`.

En las páginas de Stock:
- **`src/modules/almacen/stock/StockPage.tsx`:** quitar `import { ultimaRutaStock } from '../estado'` y la línea `useEffect(() => { ultimaRutaStock.set('/stock') }, [])`.
- **`src/modules/almacen/proveedores/ProveedoresPage.tsx`:** quitar `import { ultimaRutaStock } from '../estado'` y la línea `useEffect(() => { ultimaRutaStock.set('/stock/proveedores') }, [])`.
- **`src/modules/almacen/pedidos/PedidosPage.tsx`:**
  - quitar `import { ultimaRutaStock } from '../estado'`;
  - quitar las dos líneas `// Última pestaña de Stock para el botón de la barra superior (caché de vista del JavaFX, S2).` y `useEffect(() => { ultimaRutaStock.set(RUTA[tipo]) }, [tipo])`;
  - quitar la constante `const RUTA: Record<TipoPedido, string> = { componentes: '/stock/pedidos', otros: '/stock/pedidos/otros' }`, que ya no se usa.
  - Si `useEffect` o `TipoPedido` quedan sin uso en alguno de los tres ficheros, quitar también su importación (el typecheck y el lint lo dicen).

Borrar `src/modules/almacen/estado.ts`:

```bash
git rm src/modules/almacen/estado.ts
```

- [ ] **Paso 4: comprobar que pasan**

Ejecutar:
- `grep -rn "ultimaRutaStock\|almacen/estado" src` (esperado: sin resultados);
- `npx vitest run src/app/shell src/modules/almacen`;
- `npm run typecheck && npm run lint`;
- `npm run test`.

Esperado: sin resultados en el grep y todo en verde.

- [ ] **Paso 5: commit**

```bash
git add -A src/app/shell src/modules/almacen
git commit -m "refactor: barra superior sin botones de seccion, menu sin gestion y fuera la ultima pestana de stock (0.9.12)"
```

---

### Tarea 5: E2E por la columna

**Ficheros:**
- Modificar: `tests/e2e/clientes.spec.ts`, `tests/e2e/pedidos.spec.ts`, `tests/e2e/stock.spec.ts`, `tests/e2e/gestion.spec.ts`, `tests/e2e/cliente-historial.spec.ts`, `tests/e2e/columna-lateral.spec.ts`.

**Interfaces:**
- Consume:
  - la columna por grupos: las pestañas «Stock actual», «Pedidos», «Listado», «Técnicos», «Logs» y «Historial»;
  - las cabeceras de grupo, botones cuyo nombre empieza por el título;
  - con la columna plegada, los nombres «Grupo · Pestaña».

- [ ] **Paso 1: adaptar los pasos que usaban la barra o el menú**

**`tests/e2e/clientes.spec.ts`:** sustituir el comentario de 2 líneas que empieza por `// Acotado a la barra superior` y la línea `await page.getByRole('banner').getByRole('link', { name: 'Clientes' }).click()` por:

```ts
  // Clientes › Listado, en la columna lateral (0.9.12: la barra superior ya no tiene botones de sección).
  await page.getByRole('navigation', { name: 'Sub-navegación' }).getByRole('link', { name: 'Listado' }).click()
```

**`tests/e2e/pedidos.spec.ts`:** sustituir las dos líneas
`await page.getByRole('link', { name: 'Stock', exact: true }).click()` y
`await page.getByRole('link', { name: 'Pedidos', exact: true }).click()` por:

```ts
    await page.getByRole('navigation', { name: 'Sub-navegación' }).getByRole('link', { name: 'Pedidos', exact: true }).click()
```

**`tests/e2e/stock.spec.ts`:** sustituir `await page.getByRole('link', { name: 'Stock' }).click()` por:

```ts
  await page.getByRole('navigation', { name: 'Sub-navegación' }).getByRole('link', { name: 'Stock actual' }).click()
```

**`tests/e2e/gestion.spec.ts`:**
- añadir, justo después de la función `menuUsuario`:

```ts
/** Técnicos y Logs están en el grupo «Gestión» de la columna lateral (0.9.12; antes, en el menú de usuario). */
async function irAGestion(page: Page, pestana: 'Técnicos' | 'Logs') {
  await page.getByRole('navigation', { name: 'Sub-navegación' }).getByRole('link', { name: pestana, exact: true }).click()
}
```

- sustituir las dos llamadas `await menuUsuario(page, 'Gestionar técnicos')` por `await irAGestion(page, 'Técnicos')`, y la llamada `await menuUsuario(page, 'Ver logs')` por `await irAGestion(page, 'Logs')`.

**`tests/e2e/cliente-historial.spec.ts`:** en el test del admin, sustituir las dos líneas
`await page.getByRole('button', { name: /Hola,/ }).click()` y
`await page.getByRole('menuitem', { name: 'Ver logs', exact: true }).click()` por:

```ts
  await page.getByRole('navigation', { name: 'Sub-navegación' }).getByRole('link', { name: 'Logs', exact: true }).click()
```

- [ ] **Paso 2: la E2E de la columna, con grupos**

Sustituir `tests/e2e/columna-lateral.spec.ts` por completo:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// columna-lateral.spec.ts (0.9.10 y 0.9.12): navegación por grupos en la columna, plegar un grupo, plegar la columna,
// recargar, navegar por un icono y desplegar. Solo LEE: no crea ni cambia datos, y las preferencias viven en el
// localStorage del contexto del navegador, que Playwright descarta al terminar. Aun así, como todas las E2E, solo contra
// PREPRODUCCIÓN: se niega a seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
}

test('navegación por grupos y columna lateral plegable', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await entrar(page, usuario, clave)
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
  // El supertécnico entra en Asignaciones (InicioReparaciones).
  await expect(page).toHaveURL(/\/reparaciones\/asignaciones$/)
  const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
  await expect(columna).toHaveCSS('width', '200px')

  // Un clic desde cualquier sitio: de Pedidos a Historial sin pasar por la barra superior.
  await columna.getByRole('link', { name: 'Pedidos', exact: true }).click()
  await expect(page).toHaveURL(/\/stock\/pedidos$/)
  await columna.getByRole('link', { name: 'Historial', exact: true }).click()
  await expect(page).toHaveURL(/\/reparaciones\/historial$/)

  // La cabecera de Stock pliega su grupo y lo despliega.
  const stock = columna.getByRole('button', { name: /^Stock/ })
  await stock.click()
  await expect(stock).toHaveAttribute('aria-expanded', 'false')
  await expect(columna.getByRole('link', { name: 'Pedidos', exact: true })).toHaveCount(0)
  await stock.click()
  await expect(columna.getByRole('link', { name: 'Pedidos', exact: true })).toBeVisible()

  // Plegar la columna, recargar y navegar por un icono con su nombre «Grupo · Pestaña».
  await page.getByRole('button', { name: 'Ocultar menú lateral' }).click()
  await expect(columna).toHaveCSS('width', '56px')
  await expect(columna.getByText('Historial', { exact: true })).toHaveCount(0)
  await page.reload()
  await expect(columna).toHaveCSS('width', '56px')
  const asignaciones = columna.getByRole('link', { name: 'Reparaciones · Asignaciones' })
  await asignaciones.hover()
  await expect(page.getByRole('tooltip')).toHaveText('Reparaciones · Asignaciones')
  await asignaciones.click()
  await expect(page).toHaveURL(/\/reparaciones\/asignaciones$/)
  await expect(columna).toHaveCSS('width', '56px')

  await page.getByRole('button', { name: 'Mostrar menú lateral' }).click()
  await expect(columna).toHaveCSS('width', '200px')
  await expect(columna.getByRole('link', { name: 'Historial', exact: true })).toHaveText('Historial')
})
```

- [ ] **Paso 3: comprobar que compilan y que Playwright las ve**

Ejecutar:
- `npm run typecheck && npm run lint`;
- `npx playwright test --list`;
- `grep -rn "getByRole('link', { name: 'Stock'\|menuitem', { name: '\(Gestionar técnicos\|Ver logs\)'\|getByRole('banner').getByRole('link'" tests/e2e`.

Esperado: sin errores; la lista incluye `columna-lateral.spec.ts › navegación por grupos y columna lateral plegable`; y el grep sale sin resultados.

**No ejecutar las E2E:** se pasan contra preprod en el cierre.

- [ ] **Paso 4: commit**

```bash
git add tests/e2e
git commit -m "test: e2e navegando por la columna lateral por grupos (0.9.12)"
```

---

### Tarea 6: Versión 0.9.12 y documentación

**Ficheros:**
- Modificar: `package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/shell.md`, `docs/paridad/pedidos.md`, `docs/paridad/logs.md`, `docs/paridad/tecnicos.md`.
- Crear en la raíz: `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.12.md`.

- [ ] **Paso 1: versión**

Ejecutar: `npm version 0.9.12 --no-git-tag-version`

Esperado: `v0.9.12`, con `package.json` y los dos `version` de `package-lock.json` en `0.9.12`.

- [ ] **Paso 2: CHANGELOG**

Insertar justo encima de la línea `## [0.9.11] - …`, con una línea en blanco antes y otra después:

```markdown
## [0.9.12] - 2026-10-XX — Navegación por grupos

- **Toda la navegación en la columna de la izquierda.** Cada sección es un grupo con su cabecera, que se pliega y se despliega: Reparaciones, Stock, Estadísticas (Rendimiento), Clientes (Listado) y, para el administrador, Gestión (Técnicos y Logs). Cualquier pestaña está a un clic desde cualquier sitio.
- **La barra de arriba ya no tiene botones de sección**: se queda con el logo, la versión, la campana y el usuario. Gestionar técnicos y Ver logs salen del menú de usuario y pasan al grupo Gestión.
- **Los números de Pendientes y Asignaciones se ven siempre**, también desde Stock o Clientes. Con su grupo plegado, la cabecera enseña la suma.
- Con la columna plegada se ven todos los iconos, separados por grupos, y al pasar el ratón sale «Grupo · Pestaña». El navegador recuerda qué grupos se dejaron plegados.
- Solo web: sin cambios en el servidor, la base ni nginx.
```

- [ ] **Paso 3: fichas de paridad**

Añadir al **final de la línea** que se indica, sin tocar lo que ya hay:

| Fichero | Línea que empieza por | Texto a añadir |
|---|---|---|
| `docs/paridad/shell.md` | `- [x] Píldora de navegación` | ` **0.9.12 (adaptación web):** la píldora desaparece; las secciones son grupos de la columna lateral.` |
| `docs/paridad/shell.md` | `- [x] Los 4 botones visibles` | ` **0.9.12:** ya no hay botones; los grupos de la columna dependen del rol (Gestión solo para el ADMIN).` |
| `docs/paridad/shell.md` | `- [x] Menú de usuario:` | ` **0.9.12:** sin «Gestionar técnicos» ni «Ver logs» (están en el grupo Gestión de la columna).` |
| `docs/paridad/shell.md` | `- [x] (Sub-proyecto 6) "Gestionar técnicos" y "Ver logs"` | ` **0.9.12:** se abren desde las pestañas Técnicos y Logs del grupo Gestión, que también pasan \`volverA\`.` |
| `docs/paridad/pedidos.md` | `- [x] El botón "Stock" de la barra superior vuelve a la última pestaña de Stock` | ` **0.9.12:** retirado: ya no hay botón «Stock»; cada pestaña de Stock está en la columna.` |
| `docs/paridad/logs.md` | `- [x] "Ver logs" del menú (solo ADMIN)` | ` **0.9.12:** se entra por la pestaña Logs del grupo Gestión de la columna.` |
| `docs/paridad/tecnicos.md` | `- [x] "Gestionar técnicos" del menú (solo ADMIN)` | ` **0.9.12:** se entra por la pestaña Técnicos del grupo Gestión de la columna.` |

- [ ] **Paso 4: novedades en la raíz**

Crear `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.12.md`:

```markdown
# 🎉 Novedades — Versión 0.9.12 (web)

**Toda la navegación** pasa a la columna de la izquierda.

---

## 🧭 Columna por grupos

- Cada sección es un **grupo**: Reparaciones, Stock, Estadísticas (Rendimiento), Clientes (Listado) y, para el
  administrador, **Gestión** (Técnicos y Logs).
- Pulsa el **nombre del grupo** para plegarlo o desplegarlo. El navegador recuerda cómo los dejaste.
- Cualquier pestaña está **a un clic** desde cualquier sitio: de Pedidos a Historial sin pasar por arriba.
- Los números de **Pendientes** y **Asignaciones** se ven siempre. Con su grupo plegado, el grupo enseña la suma.
- Con la columna plegada (botón redondo del borde) se ven todos los iconos, separados por grupos. Al pasar el ratón sale
  «Grupo · Pestaña».

---

## 🔝 Barra de arriba

Ya no tiene los botones de sección: queda el logo, la versión, la campana y tu usuario. **Gestionar técnicos** y **Ver
logs** ya no están en el menú de usuario: están en el grupo Gestión de la columna.
```

- [ ] **Paso 5: verificación completa**

Ejecutar: `npm run check && npm run build`

Esperado: lint (solo el aviso que ya había en `BadgeEstadoPedido.tsx`), typecheck y **todos** los tests en verde, y el build sin errores. Anotar el número de tests.

- [ ] **Paso 6: commits**

En el worktree de la web:

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad
git commit -m "docs: version 0.9.12, changelog y fichas de paridad de la navegacion por grupos"
```

En la raíz, solo este fichero:

```bash
git -C "C:/Users/dev/Documents/ProgramaReparaciones" add docs/novedades/NOVEDADES-v0.9.12.md
git -C "C:/Users/dev/Documents/ProgramaReparaciones" commit -m "docs: novedades de la 0.9.12 (navegacion por grupos)"
```

---

## Cierre (fuera de las tareas de código; cada paso con el OK del usuario)

1. **Esperar a que la 0.9.11 esté en producción.** Después, traer `main` a la rama. El choque esperable es la cabecera del CHANGELOG: `[0.9.12]` va encima de `[0.9.11]` con su fecha. Volver a pasar `npm run check && npm run build`.
2. **Merge a `main`** de la web con `--no-ff`, pidiendo antes el OK, y push. Después, el gitlink de la raíz.
3. **Preprod:**
   - guion `Apuntes/preprod/v0912-preprod.md`, como el de la 0.9.10: solo la web;
   - las E2E de una en una, con `columna-lateral`, `clientes`, `pedidos`, `stock`, `gestion` y `cliente-historial` incluidas;
   - limpieza de lo que deje `bloques`;
   - revisión visual a 1366 y 1920 con los tres roles: columna abierta y plegada, algún grupo plegado, y comprobar que el admin cabe a 1366.
4. **Producción** en horario (solo web), con el guion `Apuntes/prod-v0912.md`. Avisar al taller del cambio de navegación (texto en el spec §9). Registro, fecha del CHANGELOG, y tag `v0.9.12` cuando lo diga el usuario.
5. Quitar el worktree `wt/web-0912` y la rama, con OK.

## Ajuste tras la revisión final (2026-10-14)

La revisión final encontró que pulsar la pestaña activa de Gestión (Técnicos o Logs) dejaba `volverA` apuntando a la propia página, y «Cerrar» no hacía nada. El usuario decidió **quitar «Cerrar»** de Técnicos y de Logs, porque ahora son pestañas como las demás. Con él se van `volverA`, `rutaVolverA` y el `state` de los enlaces; `gruposNav` pasa a ser `gruposNav(sesion)`.

En la misma tanda se hicieron los menores de la revisión:
- comentarios huérfanos en Stock y Proveedores;
- comentarios viejos de Gestión;
- títulos de las E2E;
- notas de paridad (`shell.md`, `stock.md`, `logs.md` y `tecnicos.md`).

Instrucciones: `.superpowers/sdd/v0912/fix-final-brief.md`. El spec (§2, §7 y §8) y las novedades están actualizados.
