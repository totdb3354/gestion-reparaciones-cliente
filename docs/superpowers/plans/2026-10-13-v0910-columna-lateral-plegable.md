# 0.9.10 — Columna lateral plegable con iconos: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** la columna lateral de la web lleva un icono en cada enlace, se pliega a una franja de iconos con el botón redondo de su borde y desaparece en las secciones sin enlaces.

**Arquitectura:** todo el cambio vive en `SubNav`, el componente de la columna, más tres piezas pequeñas:
- `Enlace` gana el campo `icono`;
- un módulo `preferencias.ts` recuerda en `localStorage` si la columna está plegada;
- `BadgeLateral` tiene una variante compacta para el número sobre el icono.

Las vistas no se tocan: sus tablas (`DataTable`) ya se recolocan solas al cambiar el ancho.

**Tecnologías:** React 19, React Router 7, Tailwind 4 (tokens de `src/shared/styles/tokens.css`), lucide-react 1.46.0, Radix Tooltip (`src/shared/ui/tooltip.tsx`), Vitest con Testing Library y msw, Playwright para las E2E.

**Spec:** `docs/superpowers/specs/2026-10-13-v0910-columna-lateral-plegable-design.md`.
**Prototipo aprobado:** `docs/superpowers/specs/assets/2026-10-13-v0910-prototipo-columna-lateral.html`, variante «A + E».

## Restricciones globales

- **Solo web** (`gestion-reparaciones-web`): sin cambios en el servidor, la base ni nginx.
- **Dónde se trabaja:**
  - en el worktree `C:/Users/dev/Documents/wt/web-0910`, rama `feature/columna-lateral-plegable`, creada desde `main` de la web (`4f0a863`);
  - **no se toca** ni el worktree `C:/Users/dev/Documents/wt/web-099` (la 0.9.9 en curso) ni la copia principal `C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web`.
- **Iconos** (lucide-react, ya instalado): Asignaciones `ClipboardList`, Pendientes `Wrench`, Historial `History`, IMEIs `Smartphone`, Stock actual `Package`, Pedidos `ShoppingCart`, Proveedores `Building2`, Clientes `Users`. El botón usa `ChevronLeft` con la columna abierta y `ChevronRight` con la columna plegada.
- **Medidas:**
  - columna abierta: 200 px (`w-[200px]`), con relleno de 8 px a la izquierda y 18 px a la derecha;
  - columna plegada: 56 px (`w-14`), con relleno de 6 px a la izquierda y 14 px a la derecha;
  - botón: 24 px (`size-6`), a 14 px de arriba (`top-3.5`) y con la mitad fuera de la columna (`-right-3`);
  - icono: 18 px, separado 10 px del nombre (`mr-2.5`);
  - transición del ancho: 180 ms, sin animación con «reducir movimiento» (`motion-reduce:transition-none`).
- **Textos exactos del botón:** «Ocultar menú lateral» con la columna abierta, «Mostrar menú lateral» con la columna plegada. Van en `aria-label` y en `title`.
- **Preferencia:**
  - clave de `localStorage` `columnaLateral.plegada`, con los valores `si` o `no`;
  - es del navegador, no del usuario, y `reiniciarStores` no la toca;
  - si no se puede leer, la columna sale abierta; si no se puede guardar, la elección dura lo que la página.
- **Sin enlaces:** `SubNav` no pinta nada, ni columna ni botón. Pasa en Estadísticas, `/gestion/tecnicos` y `/gestion/logs`.
- **Colores:** ningún color escrito a mano, solo utilidades de tokens (`bg-azul-noche`, `bg-superficie`, `border-pill-borde`, `bg-seleccion-suave`, `text-azul-medio`, `text-texto-nav-activo`…).
- **Commits:** en español, con prefijos `feat:`, `test:`, `docs:` y `chore:`, como el historial del repo. **Sin línea `Co-Authored-By`.**
- **Datos de los tests:** sintéticos. Los repos son públicos: nada de nombres, IMEIs ni datos reales.
- **E2E:** solo contra **preprod**, de una en una y con al menos 60 s entre ficheros. **Nunca** contra producción.

## Ficheros

| Fichero | Qué cambia |
|---|---|
| `src/shared/lib/enlaces.ts` | `Enlace` gana `icono: LucideIcon` |
| `src/modules/taller/rutas.tsx` | Iconos de Asignaciones, Pendientes, Historial e IMEIs |
| `src/modules/almacen/rutas.ts` | Iconos de Stock actual, Pedidos y Proveedores |
| `src/app/shell/SubNav.tsx` | Icono de Clientes, sin columna si no hay enlaces, estado plegada, botón y tooltip |
| `src/app/shell/preferencias.ts` (nuevo) | `CLAVE_PLEGADA`, `leerPlegada()`, `guardarPlegada()` |
| `src/modules/taller/componentes/BadgeLateral.tsx` | Prop `compacto` |
| `src/modules/taller/componentes/BadgePendientes.tsx` | Recibe `compacto` y lo pasa |
| `src/modules/taller/asignaciones/BadgeAsignaciones.tsx` | Recibe `compacto` y lo pasa |
| Tests: `src/app/shell/SubNav.test.tsx`, `src/app/shell/preferencias.test.ts` (nuevo), `src/modules/taller/rutas.test.tsx`, `src/modules/almacen/rutas.test.ts` (nuevo) | — |
| `tests/e2e/columna-lateral.spec.ts` (nuevo) | E2E contra preprod |
| `package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/shell.md` | Versión 0.9.10 y documentación |
| Raíz: `docs/novedades/NOVEDADES-v0.9.10.md` (nuevo) | Novedades para el taller |

Todas las rutas son relativas al worktree `C:/Users/dev/Documents/wt/web-0910`, salvo las de la raíz `C:/Users/dev/Documents/ProgramaReparaciones`.

---

### Tarea 1: Worktree e iconos en los enlaces (columna abierta)

**Ficheros:**
- Modificar: `src/shared/lib/enlaces.ts`, `src/modules/taller/rutas.tsx:14-22`, `src/modules/almacen/rutas.ts`, `src/app/shell/SubNav.tsx`.
- Tests: `src/modules/taller/rutas.test.tsx`, `src/modules/almacen/rutas.test.ts` (nuevo), `src/app/shell/SubNav.test.tsx`.

**Interfaces:**
- Produce: el tipo `Enlace = { to: string; label: string; icono: LucideIcon; badge?: 'pendientes' | 'asignaciones'; end?: boolean }`. `enlacesReparaciones(sesion)` y `enlacesStock()` devuelven enlaces con `icono`.

- [ ] **Paso 1: crear el worktree e instalar**

```bash
git -C "C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web" worktree add "C:/Users/dev/Documents/wt/web-0910" -b feature/columna-lateral-plegable main
cd "C:/Users/dev/Documents/wt/web-0910" && npm ci
cd "C:/Users/dev/Documents/wt/web-0910" && npx vitest run src/app/shell src/modules/taller/rutas.test.tsx
```

Esperado: el worktree en `feature/columna-lateral-plegable` sobre `4f0a863`, y los tests de `src/app/shell` y de `rutas` en verde.

- [ ] **Paso 2: escribir los tests que fallan**

En `src/modules/taller/rutas.test.tsx`, añadir la importación detrás de la línea `import { describe, expect, it } from 'vitest'`:

```tsx
import { ClipboardList, History, Smartphone, Wrench } from 'lucide-react'
```

Y, justo después del test `'enlaces de la columna por rol, en el orden del JavaFX'`:

```tsx
  it('cada enlace de la columna lleva su icono (0.9.10)', () => {
    expect(enlacesReparaciones(SESION_SUPER).map((e) => e.icono)).toEqual([ClipboardList, Wrench, History, Smartphone])
    expect(enlacesReparaciones(SESION_TEC).map((e) => e.icono)).toEqual([Wrench, History, Smartphone])
    expect(enlacesReparaciones(SESION_ADMIN)[0].icono).toBe(ClipboardList)
  })
```

Crear `src/modules/almacen/rutas.test.ts`:

```ts
import { Building2, Package, ShoppingCart } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { enlacesStock } from './rutas'

describe('enlaces de la columna de Stock', () => {
  it('cada enlace lleva su icono (0.9.10)', () => {
    expect(enlacesStock().map((e) => [e.label, e.icono])).toEqual([
      ['Stock actual', Package],
      ['Pedidos', ShoppingCart],
      ['Proveedores', Building2],
    ])
  })
})
```

En `src/app/shell/SubNav.test.tsx`, dentro de `describe('columna lateral de sub-navegación', …)`, después del test `'"Stock actual" solo está activo en /stock exacto (NavLink end), no en /stock/pedidos'`:

```tsx
  it.each([
    ['/stock', ['Stock actual', 'Pedidos', 'Proveedores']],
    ['/clientes', ['Clientes']],
  ])('en %s cada enlace lleva su icono delante del nombre, decorativo (0.9.10)', (ruta, nombres) => {
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta })
    const columna = within(screen.getByRole('navigation', { name: 'Sub-navegación' }))
    for (const nombre of nombres) {
      const enlace = columna.getByRole('link', { name: nombre })
      const icono = enlace.querySelector('svg')
      expect(icono).not.toBeNull()
      expect(icono).toHaveAttribute('aria-hidden', 'true')
      expect(enlace.firstElementChild).toBe(icono)
      expect(enlace.textContent).toBe(nombre)
    }
  })
```

- [ ] **Paso 3: comprobar que fallan**

Ejecutar: `npx vitest run src/modules/taller/rutas.test.tsx src/modules/almacen/rutas.test.ts src/app/shell/SubNav.test.tsx`

Esperado: FAIL. Los tests nuevos dan `undefined` en lugar de los iconos, y el de SubNav da `expected null not to be null`.

- [ ] **Paso 4: implementar**

`src/shared/lib/enlaces.ts`, completo:

```ts
import type { LucideIcon } from 'lucide-react'

/** Entrada de la columna lateral (SubNav). Vive en shared desde el sub-proyecto 4a porque la usan taller y almacén, y un
 *  módulo no importa de otro. `end`: NavLink solo activo en la ruta exacta. `icono` (0.9.10): va delante del nombre y es
 *  lo único que se ve con la columna plegada. */
export type Enlace = { to: string; label: string; icono: LucideIcon; badge?: 'pendientes' | 'asignaciones'; end?: boolean }
```

En `src/modules/taller/rutas.tsx`, añadir la importación detrás de `import { useEffect } from 'react'`:

```tsx
import { ClipboardList, History, Smartphone, Wrench } from 'lucide-react'
```

y dejar el cuerpo de `enlacesReparaciones` así (el comentario de encima no cambia):

```tsx
export function enlacesReparaciones(sesion: Sesion | null): EnlaceTaller[] {
  const enlaces: EnlaceTaller[] = []
  // El badge de Asignaciones (total de la tabla unificada) es solo del supertécnico: para el ADMIN desaparece (spec 3a §12).
  if (esSuperTecnico(sesion)) enlaces.push({ to: '/reparaciones/asignaciones', label: 'Asignaciones', icono: ClipboardList, badge: 'asignaciones' })
  else if (esAdmin(sesion)) enlaces.push({ to: '/reparaciones/asignaciones', label: 'Asignaciones', icono: ClipboardList })
  if (!esAdmin(sesion) && sesion?.idTec != null) enlaces.push({ to: '/reparaciones/pendientes', label: 'Pendientes', icono: Wrench, badge: 'pendientes' })
  enlaces.push({ to: '/reparaciones/historial', label: 'Historial', icono: History }, { to: '/reparaciones/imeis', label: 'IMEIs', icono: Smartphone })
  return enlaces
}
```

`src/modules/almacen/rutas.ts`, completo:

```ts
import { Building2, Package, ShoppingCart } from 'lucide-react'
import type { Enlace } from '@/shared/lib/enlaces'

/** Sidebar de StockView.fxml (`stock-sidebar-btn`): "Stock actual" · "Pedidos" · "Proveedores", en ese orden, sin badges y
 *  para los tres roles (el botón "Stock" de la barra superior tampoco depende del rol). `end` en el primero: sin él,
 *  NavLink lo marcaría activo también en /stock/pedidos. Iconos desde la 0.9.10. */
export function enlacesStock(): Enlace[] {
  return [
    { to: '/stock', label: 'Stock actual', icono: Package, end: true },
    { to: '/stock/pedidos', label: 'Pedidos', icono: ShoppingCart },
    { to: '/stock/proveedores', label: 'Proveedores', icono: Building2 },
  ]
}
```

En `src/app/shell/SubNav.tsx`:
- añadir `import { Users } from 'lucide-react'` como primera importación;
- cambiar la entrada de Clientes del mapa por `clientes: () => [{ to: '/clientes', label: 'Clientes', icono: Users }],`;
- en el render del `NavLink`, poner el icono delante del nombre:

```tsx
          {({ isActive }) => (
            <>
              <e.icono aria-hidden className="mr-2.5 size-[18px] shrink-0" />
              {e.label}
              {e.badge === 'pendientes' && <BadgePendientes activo={isActive} />}
              {e.badge === 'asignaciones' && <BadgeAsignaciones activo={isActive} />}
            </>
          )}
```

- [ ] **Paso 5: comprobar que pasan**

Ejecutar: `npx vitest run src/modules/taller/rutas.test.tsx src/modules/almacen/rutas.test.ts src/app/shell` y después `npm run typecheck && npm run lint`.

Esperado: todo en verde. `typecheck` no da errores: no hay más sitios que construyan un `Enlace`, solo los tres de arriba.

- [ ] **Paso 6: commit**

```bash
git add src/shared/lib/enlaces.ts src/modules/taller/rutas.tsx src/modules/taller/rutas.test.tsx src/modules/almacen/rutas.ts src/modules/almacen/rutas.test.ts src/app/shell/SubNav.tsx src/app/shell/SubNav.test.tsx
git commit -m "feat: iconos en los enlaces de la columna lateral (0.9.10)"
```

---

### Tarea 2: Sin columna en las secciones sin enlaces

**Ficheros:**
- Modificar: `src/app/shell/SubNav.tsx`.
- Test: `src/app/shell/SubNav.test.tsx`.

**Interfaces:**
- Produce: `SubNav` devuelve `null` cuando la sección no tiene enlaces.

- [ ] **Paso 1: cambiar los tests**

En `src/app/shell/SubNav.test.tsx`:

1. Sustituir el test `'en una sección todavía sin enlaces mantiene la columna vacía'` por:

```tsx
  it('en una sección sin enlaces no pinta la columna: el contenido usa todo el ancho (0.9.10)', () => {
    renderConProviders(<SubNav />, { ruta: '/estadisticas' })
    expect(screen.queryByRole('navigation', { name: 'Sub-navegación' })).not.toBeInTheDocument()
  })
```

2. Sustituir el `it.each(['/constructor', '/__proto__', '/toString'])` (el comentario de encima se queda) por:

```tsx
  it.each(['/constructor', '/__proto__', '/toString'])(
    'una sección que se llama como un miembro heredado de Object (%s) no pinta la columna y no rompe',
    (ruta) => {
      renderConProviders(<SubNav />, { ruta })
      expect(screen.queryByRole('navigation', { name: 'Sub-navegación' })).not.toBeInTheDocument()
    },
  )
```

3. Sustituir el test `'el shell la coloca junto al contenido en todas las vistas'` por:

```tsx
  it('el shell la coloca junto al contenido', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC, ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toBeInTheDocument()
  })

  it('en Gestión el shell no pinta la columna (0.9.10)', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_ADMIN, ruta: '/gestion/logs' })
    expect(screen.queryByRole('navigation', { name: 'Sub-navegación' })).not.toBeInTheDocument()
  })
```

- [ ] **Paso 2: comprobar que fallan**

Ejecutar: `npx vitest run src/app/shell/SubNav.test.tsx`

Esperado: FAIL en los cinco tests de sección sin enlaces (el nuevo de Estadísticas, los tres del `it.each` y el de Gestión), porque hoy la columna se pinta vacía.

- [ ] **Paso 3: implementar**

En `src/app/shell/SubNav.tsx`, justo después de la línea `const enlaces = Object.hasOwn(SUBNAV, seccion) ? SUBNAV[seccion](sesion) : []`:

```tsx
  // Sin enlaces (Estadísticas, Gestión) no hay columna: el contenido se queda con todo el ancho (0.9.10).
  if (enlaces.length === 0) return null
```

- [ ] **Paso 4: comprobar que pasan**

Ejecutar: `npx vitest run src/app/shell`

Esperado: todo en verde.

- [ ] **Paso 5: commit**

```bash
git add src/app/shell/SubNav.tsx src/app/shell/SubNav.test.tsx
git commit -m "feat: sin columna lateral en las secciones sin enlaces (0.9.10)"
```

---

### Tarea 3: Plegar y desplegar con el botón del borde, y recordarlo

**Ficheros:**
- Crear: `src/app/shell/preferencias.ts`, `src/app/shell/preferencias.test.ts`.
- Modificar: `src/app/shell/SubNav.tsx`.
- Test: `src/app/shell/SubNav.test.tsx`.

**Interfaces:**
- Consume: `Enlace.icono` (Tarea 1).
- Produce:
  - `CLAVE_PLEGADA = 'columnaLateral.plegada'`;
  - `leerPlegada(): boolean`;
  - `guardarPlegada(plegada: boolean): void`;
  - dentro de `SubNav.tsx`, la función interna `EnlaceLateral({ enlace, plegada }: { enlace: Enlace; plegada: boolean })`. La Tarea 4 la amplía.

- [ ] **Paso 1: escribir los tests que fallan**

Crear `src/app/shell/preferencias.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CLAVE_PLEGADA, guardarPlegada, leerPlegada } from './preferencias'

describe('preferencia de la columna lateral plegada (0.9.10)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sin nada guardado, abierta', () => {
    expect(leerPlegada()).toBe(false)
  })

  it('guarda «si» y «no» y los lee', () => {
    guardarPlegada(true)
    expect(localStorage.getItem(CLAVE_PLEGADA)).toBe('si')
    expect(leerPlegada()).toBe(true)
    guardarPlegada(false)
    expect(localStorage.getItem(CLAVE_PLEGADA)).toBe('no')
    expect(leerPlegada()).toBe(false)
  })

  it('si no se puede leer (modo privado, almacenamiento bloqueado), abierta', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('almacenamiento bloqueado')
    })
    expect(leerPlegada()).toBe(false)
  })

  it('si no se puede guardar, no rompe', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('almacenamiento bloqueado')
    })
    expect(() => guardarPlegada(true)).not.toThrow()
  })
})
```

En `src/app/shell/SubNav.test.tsx`:
- añadir `import userEvent from '@testing-library/user-event'` detrás de la importación de `@testing-library/react`;
- añadir `import { CLAVE_PLEGADA } from './preferencias'` detrás de `import { SubNav } from './SubNav'`;
- añadir este bloque al final del fichero:

```tsx
/** Columna plegable (0.9.10, spec «Columna lateral plegable» §2): el botón redondo del borde la pliega a una franja de
 *  iconos de 56 px y la despliega; el navegador recuerda cómo se dejó. */
describe('plegar la columna lateral', () => {
  it('abierta: 200 px, con los nombres y el botón «Ocultar menú lateral»', () => {
    renderConProviders(<SubNav />, { ruta: '/stock' })
    const columna = screen.getByRole('navigation', { name: 'Sub-navegación' })
    expect(columna).toHaveClass('w-[200px]')
    const boton = screen.getByRole('button', { name: 'Ocultar menú lateral' })
    expect(boton).toHaveAttribute('title', 'Ocultar menú lateral')
    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(boton).toHaveAttribute('aria-controls', columna.id)
    expect(within(columna).getByRole('link', { name: 'Pedidos' }).textContent).toBe('Pedidos')
  })

  it('el botón la pliega a la franja de iconos, lo guarda y la vuelve a desplegar', async () => {
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock/pedidos' })
    const columna = screen.getByRole('navigation', { name: 'Sub-navegación' })

    await user.click(screen.getByRole('button', { name: 'Ocultar menú lateral' }))
    expect(columna).toHaveClass('w-14')
    const pedidos = within(columna).getByRole('link', { name: 'Pedidos' })
    expect(pedidos.textContent).toBe('')
    expect(pedidos.querySelector('svg')).not.toBeNull()
    expect(pedidos).toHaveAttribute('aria-current', 'page')
    expect(localStorage.getItem(CLAVE_PLEGADA)).toBe('si')
    const boton = screen.getByRole('button', { name: 'Mostrar menú lateral' })
    expect(boton).toHaveAttribute('aria-expanded', 'false')

    await user.click(boton)
    expect(columna).toHaveClass('w-[200px]')
    expect(within(columna).getByRole('link', { name: 'Pedidos' }).textContent).toBe('Pedidos')
    expect(localStorage.getItem(CLAVE_PLEGADA)).toBe('no')
  })

  it('arranca plegada si el navegador lo recuerda', () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toHaveClass('w-14')
    expect(screen.getByRole('link', { name: 'Clientes' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Mostrar menú lateral' })).toBeInTheDocument()
  })

  it('anima el ancho, salvo si el sistema pide reducir movimiento', () => {
    renderConProviders(<SubNav />, { ruta: '/clientes' })
    expect(screen.getByRole('navigation', { name: 'Sub-navegación' })).toHaveClass('transition-[width]', 'motion-reduce:transition-none')
  })

  it('en una sección sin enlaces tampoco hay botón', () => {
    renderConProviders(<SubNav />, { ruta: '/estadisticas' })
    expect(screen.queryByRole('button', { name: /menú lateral/ })).not.toBeInTheDocument()
  })
})
```

- [ ] **Paso 2: comprobar que fallan**

Ejecutar: `npx vitest run src/app/shell/preferencias.test.ts src/app/shell/SubNav.test.tsx`

Esperado: FAIL. `preferencias.test.ts` no encuentra el módulo `./preferencias`. Los tests de SubNav no encuentran el botón ni la clase `w-[200px]`.

- [ ] **Paso 3: implementar `preferencias.ts`**

Crear `src/app/shell/preferencias.ts`:

```ts
/** Columna lateral plegada o abierta (0.9.10, spec «Columna lateral plegable» §2): preferencia de este navegador, no del
 *  usuario (en el taller se comparten PCs), y no es un filtro: `reiniciarStores` no la toca al cerrar sesión. Si no se
 *  puede leer (modo privado, almacenamiento bloqueado), abierta; si no se puede guardar, dura lo que la página. */
export const CLAVE_PLEGADA = 'columnaLateral.plegada'

export function leerPlegada(): boolean {
  try {
    return localStorage.getItem(CLAVE_PLEGADA) === 'si'
  } catch {
    return false
  }
}

export function guardarPlegada(plegada: boolean): void {
  try {
    localStorage.setItem(CLAVE_PLEGADA, plegada ? 'si' : 'no')
  } catch {
    // sin almacenamiento: la elección dura lo que la página
  }
}
```

- [ ] **Paso 4: implementar el plegado en `SubNav.tsx`**

`src/app/shell/SubNav.tsx`, completo:

```tsx
import { ChevronLeft, ChevronRight, Users } from 'lucide-react'
import { useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { enlacesStock } from '@/modules/almacen/rutas'
import { enlacesReparaciones } from '@/modules/taller/rutas'
import { BadgeAsignaciones } from '@/modules/taller/asignaciones/BadgeAsignaciones'
import { BadgePendientes } from '@/modules/taller/componentes/BadgePendientes'
import type { Enlace } from '@/shared/lib/enlaces'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import type { Sesion } from '@/shared/session/storage'
import { guardarPlegada, leerPlegada } from './preferencias'

/** Enlaces de la columna por sección (primer segmento de la ruta) y rol. Cada sub-proyecto añade los suyos. */
const SUBNAV: Record<string, (sesion: Sesion | null) => Enlace[]> = {
  clientes: () => [{ to: '/clientes', label: 'Clientes', icono: Users }],
  reparaciones: enlacesReparaciones,
  stock: enlacesStock,
}

/** Id de la columna: el botón de plegar la señala con `aria-controls`. */
const ID_COLUMNA = 'columna-lateral'

/**
 * Columna lateral de sub-navegación. Plegable desde la 0.9.10 (spec «Columna lateral plegable»; adaptación web, sin calco
 * en el JavaFX): abierta mide 200 px con icono y nombre; plegada, 56 px con solo los iconos. El botón redondo del borde la
 * pliega y la despliega, y el navegador recuerda cómo se dejó. Las tablas de las vistas se recolocan solas al cambiar el
 * ancho (ResizeObserver de DataTable).
 */
export function SubNav() {
  const { pathname } = useLocation()
  const { sesion } = useSession()
  const [plegada, setPlegada] = useState(leerPlegada)
  const seccion = pathname.split('/')[1]
  // Object.hasOwn: la sección viene de la URL y un /constructor resolvería a un miembro heredado de Object.prototype
  const enlaces = Object.hasOwn(SUBNAV, seccion) ? SUBNAV[seccion](sesion) : []
  // Sin enlaces (Estadísticas, Gestión) no hay columna: el contenido se queda con todo el ancho (0.9.10).
  if (enlaces.length === 0) return null
  const alternar = () => {
    setPlegada(!plegada)
    guardarPlegada(!plegada)
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
        {enlaces.map((e) => (
          <EnlaceLateral key={e.to} enlace={e} plegada={plegada} />
        ))}
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

/** Un enlace de la columna. Plegada, solo el icono: el nombre pasa al `aria-label`, porque deja de verse. */
function EnlaceLateral({ enlace: e, plegada }: { enlace: Enlace; plegada: boolean }) {
  return (
    <NavLink
      to={e.to}
      end={e.end}
      aria-label={plegada ? e.label : undefined}
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
          {e.badge === 'pendientes' && <BadgePendientes activo={isActive} />}
          {e.badge === 'asignaciones' && <BadgeAsignaciones activo={isActive} />}
        </>
      )}
    </NavLink>
  )
}
```

- [ ] **Paso 5: comprobar que pasan**

Ejecutar: `npx vitest run src/app/shell` y después `npm run typecheck && npm run lint`.

Esperado: todo en verde, también los tests de badges que ya había, porque abierta la columna se pinta igual que antes.

- [ ] **Paso 6: commit**

```bash
git add src/app/shell/preferencias.ts src/app/shell/preferencias.test.ts src/app/shell/SubNav.tsx src/app/shell/SubNav.test.tsx
git commit -m "feat: columna lateral plegable con el boton del borde y recordada en el navegador (0.9.10)"
```

---

### Tarea 4: Plegada, números compactos y nombre al pasar el ratón

**Ficheros:**
- Modificar: `src/modules/taller/componentes/BadgeLateral.tsx`, `src/modules/taller/componentes/BadgePendientes.tsx`, `src/modules/taller/asignaciones/BadgeAsignaciones.tsx`, `src/app/shell/SubNav.tsx`.
- Test: `src/app/shell/SubNav.test.tsx`.

**Interfaces:**
- Consume: `EnlaceLateral` y `CLAVE_PLEGADA` (Tarea 3), y el helper `asignaciones(rep, glass, pul)`, que ya existe en `SubNav.test.tsx`.
- Produce:
  - `BadgeLateral({ total, activo, compacto = false }: { total: number | undefined; activo: boolean; compacto?: boolean })`;
  - `BadgePendientes({ activo, compacto = false })`;
  - `BadgeAsignaciones({ activo, compacto = false })`.

- [ ] **Paso 1: escribir los tests que fallan**

Añadir al final de `src/app/shell/SubNav.test.tsx`:

```tsx
/** Plegada (0.9.10, spec §2): los números siguen cada uno en su icono, compactos, y el nombre sale al pasar el ratón o al
 *  llegar con el teclado. */
describe('columna lateral plegada: números y nombre de cada icono', () => {
  it('los números de Asignaciones y Pendientes siguen, compactos en la esquina del icono y con sus colores', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    asignaciones(2, 1, 1)
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 90, glass: 10, pulidos: 5 })))
    renderConProviders(<SubNav />, { sesion: SESION_SUPER, ruta: '/reparaciones/pendientes' })
    const asig = screen.getByRole('link', { name: 'Asignaciones' })
    expect(await within(asig).findByText('4')).toHaveClass('absolute', 'bg-azul-noche', 'text-superficie')
    const pend = screen.getByRole('link', { name: 'Pendientes' })
    expect(await within(pend).findByText('99+')).toHaveClass('absolute', 'bg-superficie', 'text-azul-noche')
  })

  it('abierta, el número sigue detrás del nombre, como antes', async () => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 1, glass: 1, pulidos: 1 })))
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/reparaciones/historial' })
    const numero = await within(screen.getByRole('link', { name: /Pendientes/ })).findByText('3')
    expect(numero).toHaveClass('ml-2')
    expect(numero).not.toHaveClass('absolute')
  })

  it('al pasar el ratón por un icono sale su nombre', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    const user = userEvent.setup()
    renderConProviders(<SubNav />, { ruta: '/stock' })
    await user.hover(screen.getByRole('link', { name: 'Proveedores' }))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Proveedores')
  })

  it('también al llegar con el teclado', async () => {
    localStorage.setItem(CLAVE_PLEGADA, 'si')
    renderConProviders(<SubNav />, { ruta: '/stock' })
    act(() => screen.getByRole('link', { name: 'Pedidos' }).focus())
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Pedidos')
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

- [ ] **Paso 2: comprobar que fallan**

Ejecutar: `npx vitest run src/app/shell/SubNav.test.tsx`

Esperado: FAIL en el de los números compactos (no tienen la clase `absolute`) y en los dos de tooltip, que no aparece. El de «abierta» y el de «abierta no hay tooltip» ya pasan: son de protección.

- [ ] **Paso 3: implementar los números compactos**

`src/modules/taller/componentes/BadgeLateral.tsx`, completo:

```tsx
import { cn } from '@/shared/lib/utils'
import { textoBadgeLateral } from '../lib/filtros'

/** Calco de .sidebar-badge y del setBadge del JavaFX: oculto a cero (o sin dato), tope 99+; blanco sobre el enlace
 *  activo, navy en los demás. Lo comparten los badges de Pendientes y de Asignaciones; cada uno pone su total.
 *  `compacto` (0.9.10, columna plegada): más pequeño y en la esquina superior derecha del icono, con los mismos colores. */
export function BadgeLateral({ total, activo, compacto = false }: { total: number | undefined; activo: boolean; compacto?: boolean }) {
  const texto = total === undefined ? null : textoBadgeLateral(total)
  if (!texto) return null
  return (
    <span
      className={cn(
        'inline-block rounded-lg text-center font-bold',
        compacto ? 'absolute top-px right-[3px] min-w-[13px] px-1 text-[8.5px]' : 'ml-2 min-w-[14px] px-[5px] py-px text-[9px]',
        activo ? 'bg-superficie text-azul-noche' : 'bg-azul-noche text-superficie',
      )}
    >
      {texto}
    </span>
  )
}
```

`src/modules/taller/componentes/BadgePendientes.tsx`, completo:

```tsx
import { useContadoresPendientes } from '../api'
import { BadgeLateral } from './BadgeLateral'

/** Badge de "Pendientes": suma rep + glass + pulidos del técnico en sesión. `compacto`: columna plegada (BadgeLateral). */
export function BadgePendientes({ activo, compacto = false }: { activo: boolean; compacto?: boolean }) {
  const { data } = useContadoresPendientes()
  return <BadgeLateral total={data ? data.reparaciones + data.glass + data.pulidos : undefined} activo={activo} compacto={compacto} />
}
```

En `src/modules/taller/asignaciones/BadgeAsignaciones.tsx`:
- añadir al final del comentario JSDoc la línea ` * `compacto`: columna plegada (BadgeLateral, 0.9.10).`, antes del ` */`;
- dejar la función así:

```tsx
export function BadgeAsignaciones({ activo, compacto = false }: { activo: boolean; compacto?: boolean }) {
  const { data } = useTotalAsignaciones({ sondea: !activo })
  return <BadgeLateral total={data} activo={activo} compacto={compacto} />
}
```

- [ ] **Paso 4: implementar el tooltip y pasar `compacto` en `SubNav.tsx`**

En `src/app/shell/SubNav.tsx`:
- añadir `import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/shared/ui/tooltip'` detrás de la importación de `@/shared/session/storage`;
- envolver la lista de enlaces en el proveedor:

```tsx
      <div className={cn('flex flex-col overflow-hidden py-2', plegada ? 'pr-3.5 pl-1.5' : 'pr-[18px] pl-2')}>
        <TooltipProvider>
          {enlaces.map((e) => (
            <EnlaceLateral key={e.to} enlace={e} plegada={plegada} />
          ))}
        </TooltipProvider>
      </div>
```

- sustituir `EnlaceLateral` por:

```tsx
/** Un enlace de la columna. Plegada, solo el icono: el nombre pasa al `aria-label`, porque deja de verse, y a un tooltip
 *  que sale al pasar el ratón o al llegar con el teclado. Los números van compactos, en la esquina del icono. */
function EnlaceLateral({ enlace: e, plegada }: { enlace: Enlace; plegada: boolean }) {
  const enlace = (
    <NavLink
      to={e.to}
      end={e.end}
      aria-label={plegada ? e.label : undefined}
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
  if (!plegada) return enlace
  // El disparador es un <span> que envuelve el enlace, no el propio NavLink con asChild: el Slot de Radix junta los
  // className como texto y convertiría en texto la función de className de NavLink.
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="block">{enlace}</span>
      </TooltipTrigger>
      <TooltipContent side="right" sideOffset={8}>
        {e.label}
      </TooltipContent>
    </Tooltip>
  )
}
```

- [ ] **Paso 5: comprobar que pasan**

Ejecutar: `npx vitest run src/app/shell src/modules/taller` y después `npm run typecheck && npm run lint`.

Esperado: todo en verde.

- [ ] **Paso 6: commit**

```bash
git add src/modules/taller/componentes/BadgeLateral.tsx src/modules/taller/componentes/BadgePendientes.tsx src/modules/taller/asignaciones/BadgeAsignaciones.tsx src/app/shell/SubNav.tsx src/app/shell/SubNav.test.tsx
git commit -m "feat: columna plegada con los numeros en cada icono y su nombre al pasar el raton (0.9.10)"
```

---

### Tarea 5: E2E de la columna lateral

**Ficheros:**
- Crear: `tests/e2e/columna-lateral.spec.ts`.

**Interfaces:**
- Consume:
  - el botón «Ocultar menú lateral» / «Mostrar menú lateral» y los anchos de 200 y 56 px (Tarea 3);
  - el tooltip con el nombre (Tarea 4);
  - las variables de entorno `E2E_BASE_URL`, `E2E_USER` y `E2E_PASS`, con un usuario supertécnico.

- [ ] **Paso 1: escribir la E2E**

Crear `tests/e2e/columna-lateral.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// columna-lateral.spec.ts (0.9.10, spec «Columna lateral plegable»): plegar, recargar, navegar por los iconos y desplegar.
// Solo LEE: no crea ni cambia datos, y la preferencia vive en el localStorage del contexto del navegador, que Playwright
// descarta al terminar. Aun así, como todas las E2E, solo contra PREPRODUCCIÓN: se niega a seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
}

test('la columna lateral se pliega, se recuerda al recargar y se despliega', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await entrar(page, usuario, clave)
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
  // El supertécnico entra en Asignaciones (InicioReparaciones).
  await expect(page).toHaveURL(/\/reparaciones\/asignaciones$/)
  const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
  await expect(columna).toHaveCSS('width', '200px')

  await page.getByRole('button', { name: 'Ocultar menú lateral' }).click()
  await expect(columna).toHaveCSS('width', '56px')
  await expect(columna.getByText('Historial', { exact: true })).toHaveCount(0)

  await page.reload()
  await expect(columna).toHaveCSS('width', '56px')
  const historial = columna.getByRole('link', { name: 'Historial' })
  await historial.hover()
  await expect(page.getByRole('tooltip')).toHaveText('Historial')
  await historial.click()
  await expect(page).toHaveURL(/\/reparaciones\/historial$/)
  await expect(columna).toHaveCSS('width', '56px')

  await page.getByRole('button', { name: 'Mostrar menú lateral' }).click()
  await expect(columna).toHaveCSS('width', '200px')
  await expect(columna.getByRole('link', { name: 'Historial' })).toHaveText('Historial')
})
```

- [ ] **Paso 2: comprobar que compila y que Playwright la ve**

Ejecutar: `npm run typecheck && npm run lint && npx playwright test --list tests/e2e/columna-lateral.spec.ts`

Esperado: sin errores, y en la lista sale `columna-lateral.spec.ts › la columna lateral se pliega, se recuerda al recargar y se despliega`.

**No ejecutarla todavía:** se ejecuta contra preprod en el cierre, cuando esta web esté desplegada allí.

- [ ] **Paso 3: commit**

```bash
git add tests/e2e/columna-lateral.spec.ts
git commit -m "test: e2e de la columna lateral plegable (0.9.10)"
```

---

### Tarea 6: Versión 0.9.10 y documentación

**Ficheros:**
- Modificar: `package.json`, `package-lock.json`, `CHANGELOG.md`, `docs/paridad/shell.md:20`.
- Crear en la raíz: `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.10.md`.

- [ ] **Paso 1: subir la versión**

Ejecutar: `npm version 0.9.10 --no-git-tag-version`

Esperado: `v0.9.10`. `package.json` y `package-lock.json` (sus dos campos `version`) pasan a `0.9.10`.

- [ ] **Paso 2: CHANGELOG**

En `CHANGELOG.md`, insertar justo encima de la línea `## [0.9.9] - …` (deja una línea en blanco antes y otra después):

```markdown
## [0.9.10] - 2026-10-XX — Columna lateral plegable

- **Iconos en la columna lateral.** Cada pestaña de la columna de la izquierda lleva su icono delante del nombre: Asignaciones, Pendientes, Historial, IMEIs, Stock actual, Pedidos, Proveedores y Clientes.
- **La columna se pliega.** El botón redondo de su borde la deja en una franja de 56 px con solo los iconos, y el contenido gana 144 px; el mismo botón la despliega. Plegada, al pasar el ratón por un icono sale su nombre, y los números de Pendientes y Asignaciones siguen cada uno en su icono. El navegador recuerda cómo se dejó (es del ordenador, no del usuario).
- **Sin columna vacía.** En Estadísticas, Gestionar técnicos y Ver logs ya no sale la columna en blanco: el contenido usa todo el ancho.
- Solo web: sin cambios en el servidor, la base ni nginx.
```

- [ ] **Paso 3: ficha de paridad**

En `docs/paridad/shell.md`, sustituir la línea 20 por:

```markdown
- [x] Columna lateral de sub-navegación (200 px, blanca, píldora navy del apartado activo; en Clientes una sola píldora "Clientes"; el resto de secciones la rellenan los sub-proyectos 1-6). (verificado el 2026-09-16 con el build de la rama contra la API de producción) **0.9.10, adaptación web sin calco:** icono delante de cada enlace; el botón redondo del borde la pliega a una franja de 56 px con solo los iconos (nombre en tooltip y badges compactos en la esquina), preferencia del navegador `columnaLateral.plegada`; sin columna en las secciones sin enlaces (Estadísticas, Gestión).
```

- [ ] **Paso 4: novedades en la raíz**

Crear `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.10.md`:

```markdown
# 🎉 Novedades — Versión 0.9.10 (web)

La columna de la izquierda **se pliega** y lleva **iconos**.

---

## 🧭 Columna lateral

- Cada pestaña de la columna lleva su **icono** delante del nombre.
- El **botón redondo** del borde de la columna la pliega: queda una franja estrecha con solo los iconos y las tablas
  ganan espacio. El mismo botón la vuelve a abrir.
- Plegada, al **pasar el ratón** por un icono sale el nombre de la pestaña.
- Los números de **Pendientes** y **Asignaciones** siguen a la vista, cada uno en su icono.
- El navegador **recuerda** si la dejaste plegada o abierta. Es del ordenador, no de tu usuario: quien use el mismo PC
  la verá como la dejaste.

---

## 📐 Más espacio

En **Estadísticas**, **Gestionar técnicos** y **Ver logs** ya no sale la columna en blanco: el contenido usa todo el ancho.
```

- [ ] **Paso 5: verificación completa**

Ejecutar: `npm run check && npm run build`

Esperado: lint, typecheck y **todos** los tests en verde, y el build sin errores. Anotar el número de tests: tiene que ser el de `main` más los nuevos de las Tareas 1 a 4.

- [ ] **Paso 6: commits**

En el worktree de la web:

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/shell.md
git commit -m "docs: version 0.9.10, changelog y ficha de paridad de la columna lateral"
```

En la raíz, solo este fichero, porque el `main` de la raíz lo usa también la sesión de la 0.9.9:

```bash
git -C "C:/Users/dev/Documents/ProgramaReparaciones" add docs/novedades/NOVEDADES-v0.9.10.md
git -C "C:/Users/dev/Documents/ProgramaReparaciones" commit -m "docs: novedades de la 0.9.10 (columna lateral plegable)"
```

---

## Cierre (fuera de las tareas de código; cada paso con el OK del usuario)

1. **Esperar a que la 0.9.9 esté en producción.** Después, traer `main` a la rama: `git -C C:/Users/dev/Documents/wt/web-0910 merge main`. El único choque esperable es la cabecera del CHANGELOG: `[0.9.10]` va encima de `[0.9.9]`. Volver a pasar `npm run check && npm run build`.
2. **Merge a `main`** de la web con `--no-ff`, **pidiendo antes el OK**. Después, el commit del gitlink de la web en la raíz.
3. **Preprod:**
   - desplegar la web siguiendo `Apuntes/despliegue_preprod.md` (Claude prepara los comandos y el usuario los ejecuta por SSH);
   - pasar las E2E de siempre, de una en una, más `columna-lateral.spec.ts`;
   - capturas a 1366 y a 1920 px con los tres roles, abierta y plegada.
4. **Producción** en horario (corte despreciable: solo web, sin migración). Registro en `Apuntes/despliegue_vdc_produccion.md` y fecha del CHANGELOG.
5. **Tag `v0.9.10`** de la web solo cuando lo diga el usuario. Después, borrar el worktree `wt/web-0910` y la rama, con OK.
