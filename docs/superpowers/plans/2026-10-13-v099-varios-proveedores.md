# 0.9.9 (ajuste) — Varios proveedores en un mismo «Nuevo pedido» — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** «Nuevo pedido» admite varios proveedores a la vez: un recuadro por proveedor (cada recuadro es un bloque), reparto automático de lo precargado con «⚠ Por repartir», cantidad = lo que falta, y un único guardado.

**Architecture:**
- **Solo web.** El servidor ya guarda un lote con líneas de varios proveedores y un destino por proveedor.
- **Modelo:** el diálogo sigue con una lista plana de `LineaCompra` donde cada línea tiene su `idProv`. `null` significa «Por repartir».
  - Los recuadros salen de esa lista, más los proveedores elegidos a mano todavía vacíos.
  - Las reglas puras van en `formulario/recuadros.ts`, que es nuevo.
  - Las reglas que ya existen en `lineas.ts` pasan a trabajar por recuadro.
- **UI:**
  - El armazón del diálogo se separa en `DialogoPedidoBase` y la tabla en `TablaLineas`. Los dos se reutilizan.
  - `RecuadroProveedor.tsx` pinta los recuadros.
  - `NuevoPedidoDialog` se rehace con todo eso.
  - Desaparecen `ResumenBloques`, el «Proveedor general», `aplicarProveedorATodas` y `MSG_SIN_PROVEEDOR`.

**Tech Stack:**
- React 18, TypeScript, TanStack Query, Radix y lucide-react.
- Pruebas con Vitest, Testing Library y MSW, y E2E con Playwright.

**Spec:** `docs/superpowers/specs/2026-10-13-v099-varios-proveedores-design.md` (raíz). El prototipo está en `docs/superpowers/specs/assets/2026-10-13-v099-prototipo-varios-proveedores.html`. Se parte de la web `main` `343217b`.

## Global Constraints

- **Rama y worktree:**
  - Rama `feature/varios-proveedores` creada **desde `main`** en `C:/Users/dev/Documents/wt/web-099`. Hoy ese worktree está en `feature/cantidad-prevista-cabecera`, ya mergeada.
  - Solo web: el servidor no se toca.
  - No tocar `C:/Users/dev/Documents/wt/web-0910`, que es de otra sesión.
- **Comandos (Git Bash), en `wt/web-099`:**
  - `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint`.
  - **Un solo vitest a la vez, siempre en primer plano.** Si una orden larga pasa a segundo plano por el timeout, esperar a que acabe antes de lanzar otra.
  - La suite completa tarda unos 4 minutos: `npx vitest run 2>&1 | grep -E "Test Files|Tests "`, con un timeout de 600000 ms.
  - `FormularioReparacion.test.tsx` y `ClientesPage.test.tsx` a veces fallan solo bajo carga. Si fallan, repetirlos aislados y dar los dos resultados.
  - Lint tiene un aviso que ya existía en `BadgeEstadoPedido.tsx`.
- **Commits:**
  - En español **sin tildes**, con prefijo `feat:` / `fix:` / `test:` / `refactor:` / `docs:`.
  - **Sin** `Co-Authored-By`: el usuario lo prohíbe y esto manda sobre cualquier otra instrucción de atribución.
  - Nada de push ni merge.
- **Comentarios y docs:** en español **con** tildes.
- **Textos exactos:**
  - Al abrir vacío: «¿A qué proveedor pides?», el desplegable `aria-label` «Proveedor» con `textoVacio` «Elige el proveedor…», y debajo «Luego puedes pedir a otros con «+ Otro proveedor»: cada uno va en su bloque.».
  - Opciones de «Proveedor» y «Otro proveedor»: «{nombre} · Bloque {N} abierto» o «{nombre} · sin bloque abierto». Para «+ Otro proveedor», el `aria-label` es «Otro proveedor» y el `textoVacio` «+ Otro proveedor».
  - Recuadro de proveedor:
    - `<section aria-label="Pedido a {nombre}">`;
    - cabecera con «{nombre}», «· {n línea(s)} · {divisa}», «→», el destino, «Añadir previsión ({n})» y el botón × `aria-label` «Quitar {nombre}»;
    - el plegado con `aria-label` «Plegar {nombre}» / «Desplegar {nombre}» y `aria-expanded`;
    - tabla vacía: «Añade líneas o «Añadir previsión».».
  - Destino: el combo `aria-label` «Bloque de {nombre}» con las opciones de hoy (`opcionesDestino`). Si no hay abiertos, «**Bloque nuevo** (no tiene ninguno abierto)». En «Añadir líneas», «**Bloque {N}** (fijado)». Mientras cargan los pedidos, «Cargando bloques…».
  - «Por repartir»:
    - `<section aria-label="Por repartir">`;
    - cabecera «⚠ Por repartir», «· {n línea(s)} · las venden varios proveedores o ninguno: elige a quién se las pides»;
    - en la columna Proveedor, el combo `aria-label` «Pasar a línea {n}» con `textoVacio` «Pasar a…» y los grupos «Lo vende» / «Otros proveedores».
  - Avisos:
    - «{pieza} también va a {proveedor} ({cant}).»;
    - «{pieza} ya está en el pedido a {proveedor} (línea {n}).»;
    - en «Por repartir», «{pieza} ya está en «Por repartir» (línea {n}).»;
    - «{pieza} ya estaba en {proveedor}: se queda la línea que había.».
  - Confirmar:
    - botón «Confirmar pedido», o «Confirmar pedido ({n} bloques)» con más de uno;
    - «Queda 1 línea por repartir: elige a quién se la pides.» / «Quedan {n} líneas por repartir: elige a quién se las pides.»;
    - las validaciones de línea llevan delante «{proveedor} · »;
    - quitar un recuadro con líneas: ConfirmDialog «Quitar proveedor», «¿Quitar {p} y su línea?» / «¿Quitar {p} y sus {n} líneas?», acción «Quitar».
- **Reglas:**
  - Lo que falta = «Pedir 60 d» del master − lo que va a **otros** proveedores en el pedido, ≥ 0. «Por repartir» no cuenta.
  - Recuadros por nombre (`localeCompare(…, 'es')`).
  - Reparto automático al **único** proveedor **activo** que vende la pieza.
  - Repetidas solo dentro de un recuadro.
  - «Cancelar» cierra sin preguntar, como hoy. «Atrás» del navegador pide «Descartar».
  - «Añadir líneas…» de un bloque tiene un solo recuadro fijo, sin × ni «+ Otro proveedor».
- **Registro de subagentes:** `.superpowers/sdd/v099f/` en la raíz (gitignored).

---

### Task 1: Reglas puras — lo que falta, recuadros y reparto

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.ts` y `formulario/lineas.test.ts`
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/recuadros.ts` y `formulario/recuadros.test.ts`

**Interfaces:**
- Produces en `lineas.ts`:
  - `loQueFalta(lineas: LineaCompra[], idCom: number, idProv: number | null, activos: Componente[]): number`;
  - `cuantasPrevision(activos, idProv, lineas: LineaCompra[] = [])`;
  - `elegirPieza` y `aplicarPrevision` por recuadro, con las mismas firmas.
- Produces en `recuadros.ts`:
  - `POR_REPARTIR = 'repartir'` y `type ClaveRecuadro = number | typeof POR_REPARTIR`;
  - `repartirLineas(lineas, activos, idsActivos: ReadonlySet<number>): LineaCompra[]`;
  - `recuadrosDelPedido(lineas, anadidos: number[], nombre: (idProv: number) => string): number[]`;
  - `lineasDe(lineas, clave: ClaveRecuadro): LineaCompra[]`;
  - `pasarA(lineas, id, idProv): { lineas; repetida: boolean }`;
  - `opcionesPasarA(idCom: number | null, activos, proveedores: Proveedor[]): OpcionCombo[]`;
  - `opcionesOtroProveedor(proveedores, recuadros: number[], bloques: Map<string, Bloque>): OpcionCombo[]`;
  - `tambienVa(lineas, idProv, nombrePieza, nombreProveedor): string[]`;
  - `textoConfirmar(nBloques)`, `avisoYaEstaba(pieza, proveedor)` y `textoQuitarRecuadro(proveedor, n)`;
  - `validarPedido(lineas, recuadros, nombre): { error: string; recuadro: ClaveRecuadro | null } | null`.
- **Restricción:** el diálogo de hoy tiene que seguir funcionando, porque esta tarea no lo toca. Las reglas nuevas, con todas las líneas del mismo proveedor, dan lo mismo que antes.

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git status --short && git switch -c feature/varios-proveedores main && git log --oneline -1
```
Expected: árbol limpio y `343217b merge: cantidad prevista, sin repetidas y cabecera del bloque (0.9.9)`.

- [ ] **Step 1: Tests que fallan (`lineas.test.ts`)**

1. Añadir `loQueFalta` al import de `./lineas`.
2. Cambiar el test «en una línea que ya estaba sube la cantidad sin bajarla y solo rellena el proveedor vacío» del describe «pedido automático (spec 0.9.6 §4.4)». Las líneas no cambian; cambian el título, el comentario y `sinPedido`:

```ts
  it('en una línea suya o de «Por repartir» sube la cantidad sin bajarla, y la de «Por repartir» pasa a este proveedor; la de otro proveedor no se toca (2026-10-13)', () => {
    const lineas: LineaCompra[] = [
      { id: 1, idCom: 1, idProv: null, cantidad: '3', precio: '0,00', urgente: true },
      { id: 2, idCom: 2, idProv: 9, cantidad: '4', precio: '1,50', urgente: false },
      { id: 3, idCom: 3, idProv: null, cantidad: '1', precio: '0,00', urgente: false },
    ]
    const r = aplicarPrevision(lineas, ACTIVOS, 7)
    expect(r.lineas).toEqual([
      { id: 1, idCom: 1, idProv: 7, cantidad: '16', precio: '0,00', urgente: true },
      { id: 2, idCom: 2, idProv: 9, cantidad: '4', precio: '1,50', urgente: false },
      { id: 3, idCom: 3, idProv: null, cantidad: '1', precio: '0,00', urgente: false },
    ])
    // bat (2) ya va a otro proveedor (9) y su previsión es 0: a este no le falta nada.
    expect(r.sinPedido).toBe(1)
  })
```

3. Añadir al final del fichero:

```ts
describe('lo que falta y recuadros (varios proveedores, spec 2026-10-13 §4-§5)', () => {
  // lcd-x-negro (1) pide 7, marcada en Auto; la venden ACME (1) y Proveedor B (2).
  const ACTIVOS: Componente[] = COMPONENTES.map((c) => (c.idCom === 1 ? { ...c, pedir60: 7, autoPedido: true, proveedores: [1, 2] } : c))
  const L = (id: number, idCom: number | null, idProv: number | null, cantidad = '1'): LineaCompra => ({ ...lineaCompraVacia(id, idCom), idProv, cantidad })

  it('loQueFalta: «Pedir 60 d» menos lo que va a otros proveedores; «Por repartir» no cuenta; nunca negativo', () => {
    const ls = [L(1, 1, 1, '3'), L(2, 1, null, '5')]
    expect(loQueFalta(ls, 1, 2, ACTIVOS)).toBe(4)
    expect(loQueFalta(ls, 1, 1, ACTIVOS)).toBe(7)
    expect(loQueFalta([L(1, 1, 1, '9')], 1, 2, ACTIVOS)).toBe(0)
  })

  it('elegir la pieza en el segundo proveedor pone lo que falta (7 − 3 = 4); si no falta nada, 1', () => {
    expect(elegirPieza([L(1, 1, 1, '3'), L(2, null, 2)], 2, 1, ACTIVOS).lineas[1].cantidad).toBe('4')
    expect(elegirPieza([L(1, 1, 1, '7'), L(2, null, 2)], 2, 1, ACTIVOS).lineas[1].cantidad).toBe('1')
  })

  it('una pieza solo se junta dentro de su recuadro; en otro proveedor sí puede ir; el número es el del recuadro', () => {
    const otroProveedor = elegirPieza([L(1, 1, 1, '3'), L(2, null, 2)], 2, 1, ACTIVOS)
    expect(otroProveedor.repetida).toBeNull()
    expect(otroProveedor.lineas.map((l) => [l.idCom, l.idProv])).toEqual([[1, 1], [1, 2]])
    const mismo = elegirPieza([L(1, 2, 2), L(2, 1, 1, '3'), L(3, 1, 2, '4'), L(4, null, 2)], 4, 1, ACTIVOS)
    expect(mismo.repetida).toEqual({ id: 3, n: 2 })
    expect(mismo.lineas.map((l) => l.id)).toEqual([1, 2, 3])
  })

  it('«Añadir previsión» de un recuadro: lo que falta; coge la de «Por repartir»; no toca la de otro proveedor', () => {
    expect(cuantasPrevision(ACTIVOS, 2, [L(1, 1, 1, '3')])).toBe(1)
    expect(cuantasPrevision(ACTIVOS, 2, [L(1, 1, 1, '7')])).toBe(0)
    expect(aplicarPrevision([L(1, 1, 1, '3')], ACTIVOS, 2).lineas).toEqual([L(1, 1, 1, '3'), L(2, 1, 2, '4')])
    expect(aplicarPrevision([L(1, 1, null, '2')], ACTIVOS, 2).lineas).toEqual([L(1, 1, 2, '7')])
    expect(aplicarPrevision([L(1, 1, 1, '7')], ACTIVOS, 2)).toEqual({ lineas: [L(1, 1, 1, '7')], sinPedido: 1 })
  })
})
```

- [ ] **Step 2: Tests que fallan (`recuadros.test.ts`, nuevo)**

```ts
import { describe, expect, it } from 'vitest'
import type { Componente } from '@/shared/api/client'
import { lineaDePrueba } from '../bloques/datosPrueba'
import { agruparEnBloques } from '../bloques/reglas'
import { COMPONENTES, PROVEEDORES } from './datosPrueba'
import { lineaCompraVacia, type LineaCompra } from './lineas'
import {
  avisoYaEstaba, lineasDe, opcionesOtroProveedor, opcionesPasarA, pasarA, POR_REPARTIR, recuadrosDelPedido, repartirLineas, tambienVa,
  textoConfirmar, textoQuitarRecuadro, validarPedido,
} from './recuadros'

const L = (id: number, idCom: number | null, idProv: number | null, cantidad = '1'): LineaCompra => ({ ...lineaCompraVacia(id, idCom), idProv, cantidad })
// lcd-x-negro (1) la venden ACME (1) y Proveedor B (2); bat-x (2) y su slave bat-y (5), ACME y Proveedor A (3), que está
// desactivado; mc-x (4) no trae lista.
const ACTIVOS: Componente[] = COMPONENTES.map((c) => (c.idCom === 1 ? { ...c, proveedores: [1, 2] } : c.idCom === 2 || c.idCom === 5 ? { ...c, proveedores: [1, 3] } : c))
const ACTIVOS_IDS = new Set([1, 2])
const PROVEEDORES_ACTIVOS = PROVEEDORES.filter((p) => p.activo)
const nombre = (id: number) => PROVEEDORES.find((p) => p.idProv === id)?.nombre ?? `Proveedor ${id}`

describe('reparto automático (spec 2026-10-13 §3)', () => {
  it('al único proveedor activo que la vende; varios, ninguno, sin dato o sin pieza → «Por repartir»', () => {
    const r = repartirLineas([L(1, 2, null), L(2, 1, null), L(3, null, null), L(4, 4, null)], ACTIVOS, ACTIVOS_IDS)
    expect(r.map((l) => l.idProv)).toEqual([1, null, null, null])
  })
})

describe('recuadros', () => {
  it('los proveedores con líneas y los añadidos vacíos, sin repetir, por nombre', () => {
    expect(recuadrosDelPedido([L(1, 1, 2), L(2, 2, 1), L(3, 2, null)], [2, 3], nombre)).toEqual([1, 3, 2]) // ACME, Proveedor A, Proveedor B
  })
  it('lineasDe: las de un proveedor o las de «Por repartir», en su orden', () => {
    const ls = [L(1, 1, 2), L(2, 2, null), L(3, 2, 2)]
    expect(lineasDe(ls, 2).map((l) => l.id)).toEqual([1, 3])
    expect(lineasDe(ls, POR_REPARTIR).map((l) => l.id)).toEqual([2])
  })
})

describe('Pasar a… (§3)', () => {
  it('la línea va al final del recuadro del proveedor', () => {
    expect(pasarA([L(1, 1, null), L(2, 2, 1)], 1, 1)).toEqual({ lineas: [L(2, 2, 1), L(1, 1, 1)], repetida: false })
  })
  it('si ese recuadro ya tenía la pieza, se queda la que había', () => {
    expect(pasarA([L(1, 2, null, '5'), L(2, 2, 1, '3')], 1, 1)).toEqual({ lineas: [L(2, 2, 1, '3')], repetida: true })
    expect(avisoYaEstaba('bat-x / bat-y', 'ACME')).toBe('bat-x / bat-y ya estaba en ACME: se queda la línea que había.')
  })
  it('opciones: primero «Lo vende» y luego «Otros proveedores»', () => {
    expect(opcionesPasarA(2, ACTIVOS, PROVEEDORES_ACTIVOS)).toEqual([
      { valor: '1', etiqueta: 'ACME', grupo: 'Lo vende' },
      { valor: '2', etiqueta: 'Proveedor B', grupo: 'Otros proveedores' },
    ])
    expect(opcionesPasarA(null, ACTIVOS, PROVEEDORES_ACTIVOS).map((o) => o.grupo)).toEqual(['Otros proveedores', 'Otros proveedores'])
  })
})

describe('otro proveedor', () => {
  it('los que no tienen recuadro, con su bloque abierto o «sin bloque abierto»', () => {
    const bloques = agruparEnBloques([lineaDePrueba({ idCompra: 9, idProv: 1, nombreProveedor: 'ACME', idBloque: 41 })])
    expect(opcionesOtroProveedor(PROVEEDORES_ACTIVOS, [], bloques)).toEqual([
      { valor: '1', etiqueta: 'ACME · Bloque 41 abierto' },
      { valor: '2', etiqueta: 'Proveedor B · sin bloque abierto' },
    ])
    expect(opcionesOtroProveedor(PROVEEDORES_ACTIVOS, [1], bloques).map((o) => o.valor)).toEqual(['2'])
  })
})

describe('también va a (§5)', () => {
  it('las piezas del recuadro que van también a otros proveedores, con su cantidad allí; «Por repartir» no cuenta', () => {
    const ls = [L(1, 1, 1, '3'), L(2, 1, 2, '4'), L(3, 2, 1), L(4, 1, null, '9')]
    expect(tambienVa(ls, 1, () => 'lcd-x-negro', nombre)).toEqual(['lcd-x-negro también va a Proveedor B (4).'])
    expect(tambienVa(ls, 2, () => 'lcd-x-negro', nombre)).toEqual(['lcd-x-negro también va a ACME (3).'])
  })
})

describe('Confirmar (§6)', () => {
  it('primero lo que queda por repartir', () => {
    expect(validarPedido([L(1, 1, null)], [], nombre)).toEqual({ error: 'Queda 1 línea por repartir: elige a quién se la pides.', recuadro: POR_REPARTIR })
    expect(validarPedido([L(1, 1, null), L(2, 2, null)], [], nombre)?.error).toBe('Quedan 2 líneas por repartir: elige a quién se las pides.')
  })
  it('sin líneas: «Añade al menos una línea.»; los recuadros vacíos no cuentan', () => {
    expect(validarPedido([], [1], nombre)).toEqual({ error: 'Añade al menos una línea.', recuadro: null })
    expect(validarPedido([L(1, 1, 2)], [1, 2], nombre)).toBeNull()
  })
  it('las líneas de cada recuadro en el orden en que se ven, con el proveedor delante', () => {
    expect(validarPedido([L(1, null, 2), L(2, 1, 1, '0')], [1, 2], nombre)).toEqual({ error: 'ACME · Línea 1: la cantidad debe ser mayor que 0.', recuadro: 1 })
  })
  it('textos del botón y de quitar un recuadro', () => {
    expect(textoConfirmar(0)).toBe('Confirmar pedido')
    expect(textoConfirmar(1)).toBe('Confirmar pedido')
    expect(textoConfirmar(3)).toBe('Confirmar pedido (3 bloques)')
    expect(textoQuitarRecuadro('ACME', 1)).toBe('¿Quitar ACME y su línea?')
    expect(textoQuitarRecuadro('ACME', 4)).toBe('¿Quitar ACME y sus 4 líneas?')
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts src/modules/almacen/pedidos/formulario/recuadros.test.ts`
Expected: fallan. `loQueFalta` y `recuadros.ts` no existen, y el test cambiado sigue dando `sinPedido` 0.

- [ ] **Step 3: Implementación (`lineas.ts`)**

Tras `cantidadPrevista`, añadir:

```ts
/** Lo que falta de una pieza en el recuadro del proveedor `idProv` (0.9.9, varios proveedores en un pedido, spec
 *  2026-10-13 §4): su «Pedir 60 d» menos lo que ya va a OTROS proveedores en este pedido, nunca menos de 0. Las líneas de
 *  «Por repartir» (sin proveedor) no cuentan: aún no van a nadie. Con un solo proveedor es su «Pedir 60 d». */
export function loQueFalta(lineas: LineaCompra[], idCom: number, idProv: number | null, activos: Componente[]): number {
  const pedir = activos.find((c) => c.idCom === idCom)?.pedir60 ?? 0
  const enOtros = lineas
    .filter((l) => l.idCom === idCom && l.idProv !== null && l.idProv !== idProv)
    .reduce((suma, l) => suma + (parsearEntero(l.cantidad) ?? 0), 0)
  return Math.max(pedir - enOtros, 0)
}
```

Sustituir `cuantasPrevision` por:

```ts
/** N del botón «Añadir previsión (N)» de un recuadro: marcadas de la lista del proveedor a las que aún les falta algo en
 *  este pedido (`loQueFalta`). Sin proveedor, 0. */
export function cuantasPrevision(activos: Componente[], idProv: number | null, lineas: LineaCompra[] = []): number {
  if (idProv === null) return 0
  return marcadasDe(activos, idProv).filter((c) => loQueFalta(lineas, c.idCom, idProv, activos) > 0).length
}
```

Sustituir `aplicarPrevision`, con su comentario, por:

```ts
/** «Añadir previsión» del recuadro del proveedor X (spec 0.9.6 §4.4, filtro de la spec 2026-10-11 §5.2 y varios
 *  proveedores 2026-10-13 §4). Por cada marcada de la lista de X, con lo que falta (`loQueFalta`):
 *  - si el recuadro de X ya la tiene, su cantidad = máx(la de la línea, lo que falta, 1);
 *  - si no, y está en «Por repartir», esa línea pasa a X con esa misma cantidad;
 *  - si no está, línea nueva de X con lo que falta, o nada si no falta nada (cuenta en `sinPedido`).
 *  Las líneas de otros proveedores no se tocan. Idempotente. */
export function aplicarPrevision(lineas: LineaCompra[], activos: Componente[], idProv: number): { lineas: LineaCompra[]; sinPedido: number } {
  let resultado = lineas
  let sinPedido = 0
  for (const c of marcadasDe(activos, idProv)) {
    const falta = loQueFalta(resultado, c.idCom, idProv, activos)
    const existente = resultado.find((l) => l.idCom === c.idCom && l.idProv === idProv)
      ?? resultado.find((l) => l.idCom === c.idCom && l.idProv === null)
    if (existente === undefined) {
      if (falta > 0) resultado = [...resultado, { ...lineaCompraVacia(siguienteId(resultado), c.idCom), idProv, cantidad: String(falta) }]
      else sinPedido += 1
      continue
    }
    const cantidad = Math.max(parsearEntero(existente.cantidad) ?? 0, falta, 1)
    resultado = cambiarLinea(resultado, existente.id, { cantidad: String(cantidad), idProv })
  }
  return { lineas: resultado, sinPedido }
}
```

Sustituir `elegirPieza`, con su comentario, y `cantidadAutomatica` por:

```ts
/** Elegir la pieza `idCom` en la línea `id` (regla «Recalcular» del 2026-10-13, con lo que falta de la spec de varios
 *  proveedores §4). Todo es dentro del recuadro de la línea, es decir, su proveedor o «Por repartir»:
 *  - si la línea ya tenía esa pieza, no cambia nada (p. ej. una línea de solicitud con 1 sigue con 1);
 *  - si otra línea del recuadro ya la tiene, la línea `id` se quita y la otra no cambia; `n` es su número en el recuadro;
 *  - si no, la línea toma la pieza. Si su cantidad seguía automática (el 1 de entrada, o lo que faltaba de la pieza
 *    anterior), pasa a lo que falta de la nueva o, si no falta nada, a 1. Si se cambió a mano, no se toca. */
export function elegirPieza(lineas: LineaCompra[], id: number, idCom: number, activos: Componente[]): Eleccion {
  const linea = lineas.find((l) => l.id === id)
  if (linea === undefined || linea.idCom === idCom) return { lineas, repetida: null }
  const otra = lineas.find((l) => l.id !== id && l.idCom === idCom && l.idProv === linea.idProv)
  if (otra !== undefined) {
    const restantes = quitarLinea(lineas, id)
    const delRecuadro = restantes.filter((l) => l.idProv === linea.idProv)
    return { lineas: restantes, repetida: { id: otra.id, n: delRecuadro.findIndex((l) => l.id === otra.id) + 1 } }
  }
  const cambio: Partial<LineaCompra> = { idCom }
  if (cantidadAutomatica(lineas, linea, activos)) {
    const falta = loQueFalta(lineas, idCom, linea.idProv, activos)
    cambio.cantidad = String(falta > 0 ? falta : 1)
  }
  return { lineas: cambiarLinea(lineas, id, cambio), repetida: null }
}

/** La cantidad de la línea es la que puso el formulario y no la del usuario: el 1 de entrada o lo que faltaba de su pieza. */
function cantidadAutomatica(lineas: LineaCompra[], linea: LineaCompra, activos: Componente[]): boolean {
  if (linea.cantidad === '1') return true
  if (linea.idCom === null) return false
  const anterior = loQueFalta(lineas, linea.idCom, linea.idProv, activos)
  return anterior > 0 && linea.cantidad === String(anterior)
}
```

Ojo con el orden: `quitarLinea` y `cambiarLinea` ya están definidas más abajo en el fichero. Al ser `function`, eso no importa.

- [ ] **Step 4: Implementación (`recuadros.ts`, nuevo)**

```ts
import type { Componente, Proveedor } from '@/shared/api/client'
import type { OpcionCombo } from '@/shared/ui/ComboNavy'
import { abiertosDe, type Bloque } from '../bloques/reglas'
import { MSG_SIN_LINEAS, validarLineasCompra, type LineaCompra } from './lineas'

/** Clave del recuadro «Por repartir» de «Nuevo pedido» (0.9.9, spec 2026-10-13 varios proveedores §3): las líneas aún sin
 *  proveedor. Los demás recuadros se identifican por el id de su proveedor. */
export const POR_REPARTIR = 'repartir'
export type ClaveRecuadro = number | typeof POR_REPARTIR

/** Reparto automático de las líneas precargadas (§3): cada una al único proveedor activo que vende su pieza (`proveedores`
 *  de «Piezas que vende»; un slave trae la lista de su master). Si la venden varios o ninguno, si no se sabe (`proveedores`
 *  nulo) o la línea no tiene pieza, queda sin proveedor: «Por repartir». */
export function repartirLineas(lineas: LineaCompra[], activos: Componente[], idsActivos: ReadonlySet<number>): LineaCompra[] {
  return lineas.map((l) => {
    const venden = l.idCom === null ? [] : (activos.find((c) => c.idCom === l.idCom)?.proveedores ?? []).filter((id) => idsActivos.has(id))
    return { ...l, idProv: venden.length === 1 ? venden[0] : null }
  })
}

/** Recuadros de proveedor del pedido: los que tienen líneas y los elegidos a mano aún vacíos, por nombre. */
export function recuadrosDelPedido(lineas: LineaCompra[], anadidos: number[], nombre: (idProv: number) => string): number[] {
  const ids = new Set(anadidos)
  for (const l of lineas) if (l.idProv !== null) ids.add(l.idProv)
  return [...ids].sort((a, b) => nombre(a).localeCompare(nombre(b), 'es'))
}

/** Líneas de un recuadro en su orden: la n de «línea n» es su posición aquí. */
export function lineasDe(lineas: LineaCompra[], clave: ClaveRecuadro): LineaCompra[] {
  return lineas.filter((l) => (clave === POR_REPARTIR ? l.idProv === null : l.idProv === clave))
}

/** «Pasar a…» de una línea de «Por repartir»: va al final del recuadro de `idProv`. Si ese recuadro ya tenía la pieza, se
 *  queda la que había, la de «Por repartir» se quita y `repetida` es true. */
export function pasarA(lineas: LineaCompra[], id: number, idProv: number): { lineas: LineaCompra[]; repetida: boolean } {
  const linea = lineas.find((l) => l.id === id)
  if (linea === undefined) return { lineas, repetida: false }
  const resto = lineas.filter((l) => l.id !== id)
  if (linea.idCom !== null && resto.some((l) => l.idProv === idProv && l.idCom === linea.idCom)) return { lineas: resto, repetida: true }
  return { lineas: [...resto, { ...linea, idProv }], repetida: false }
}

/** Opciones de «Pasar a…»: primero los proveedores que venden la pieza («Lo vende») y después el resto («Otros
 *  proveedores»), cada grupo en el orden de la lista de proveedores. */
export function opcionesPasarA(idCom: number | null, activos: Componente[], proveedores: Proveedor[]): OpcionCombo[] {
  const venden = new Set(idCom === null ? [] : activos.find((c) => c.idCom === idCom)?.proveedores ?? [])
  const opcion = (p: Proveedor, grupo: string): OpcionCombo => ({ valor: String(p.idProv), etiqueta: p.nombre, grupo })
  return [
    ...proveedores.filter((p) => venden.has(p.idProv)).map((p) => opcion(p, 'Lo vende')),
    ...proveedores.filter((p) => !venden.has(p.idProv)).map((p) => opcion(p, 'Otros proveedores')),
  ]
}

/** Opciones de «¿A qué proveedor pides?» y de «+ Otro proveedor»: los proveedores sin recuadro, con su bloque abierto más
 *  reciente o «sin bloque abierto». */
export function opcionesOtroProveedor(proveedores: Proveedor[], recuadros: number[], bloques: Map<string, Bloque>): OpcionCombo[] {
  return proveedores.filter((p) => !recuadros.includes(p.idProv)).map((p) => {
    const abierto = abiertosDe(bloques, p.idProv)[0]
    return { valor: String(p.idProv), etiqueta: `${p.nombre} · ${abierto === undefined ? 'sin bloque abierto' : `Bloque ${abierto.idBloque} abierto`}` }
  })
}

/** Avisos «también va a» del recuadro de `idProv` (§5): sus piezas que van también a otros proveedores, con la cantidad
 *  que llevan allí. Las de «Por repartir» no cuentan. */
export function tambienVa(lineas: LineaCompra[], idProv: number, nombrePieza: (idCom: number) => string, nombreProveedor: (idProv: number) => string): string[] {
  return lineasDe(lineas, idProv).flatMap((l) => {
    const idCom = l.idCom
    if (idCom === null) return []
    return lineas.flatMap((o) => (o.idCom === idCom && o.idProv !== null && o.idProv !== idProv
      ? [`${nombrePieza(idCom)} también va a ${nombreProveedor(o.idProv)} (${o.cantidad}).`]
      : []))
  })
}

export function textoConfirmar(nBloques: number): string {
  return nBloques > 1 ? `Confirmar pedido (${nBloques} bloques)` : 'Confirmar pedido'
}

export function avisoYaEstaba(pieza: string, proveedor: string): string {
  return `${pieza} ya estaba en ${proveedor}: se queda la línea que había.`
}

export function textoQuitarRecuadro(proveedor: string, n: number): string {
  return n === 1 ? `¿Quitar ${proveedor} y su línea?` : `¿Quitar ${proveedor} y sus ${n} líneas?`
}

/** «Confirmar» (§6): primero lo que queda por repartir, luego que haya alguna línea y después las líneas de cada recuadro
 *  en el orden en que se ven, con el proveedor delante. Devuelve el primer fallo con el recuadro al que llevar al usuario. */
export function validarPedido(lineas: LineaCompra[], recuadros: number[], nombre: (idProv: number) => string): { error: string; recuadro: ClaveRecuadro | null } | null {
  const sinRepartir = lineasDe(lineas, POR_REPARTIR).length
  if (sinRepartir === 1) return { error: 'Queda 1 línea por repartir: elige a quién se la pides.', recuadro: POR_REPARTIR }
  if (sinRepartir > 1) return { error: `Quedan ${sinRepartir} líneas por repartir: elige a quién se las pides.`, recuadro: POR_REPARTIR }
  if (lineas.length === 0) return { error: MSG_SIN_LINEAS, recuadro: null }
  for (const idProv of recuadros) {
    const suyas = lineasDe(lineas, idProv)
    if (suyas.length === 0) continue
    const error = validarLineasCompra(suyas)
    if (error !== null) return { error: `${nombre(idProv)} · ${error}`, recuadro: idProv }
  }
  return null
}
```

- [ ] **Step 5: Comprobar que pasa**

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos/formulario`. Pasan también los tests de hoy de `NuevoPedidoDialog`: con un proveedor, todo da lo mismo.
2. `npx tsc -b && npm run lint`.

Expected: verde. La suite completa se pasa en la Tarea 4.

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/lineas.ts src/modules/almacen/pedidos/formulario/lineas.test.ts src/modules/almacen/pedidos/formulario/recuadros.ts src/modules/almacen/pedidos/formulario/recuadros.test.ts
git commit -m "feat: reglas de varios proveedores en un pedido: lo que falta, recuadros y reparto"
```

---

### Task 2: Separar el armazón del diálogo y la tabla de líneas (sin cambiar el comportamiento)

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/DialogoPedidoBase.tsx`
- Create: `formulario/TablaLineas.tsx` y `formulario/TablaLineas.test.tsx`
- Modify: `formulario/DialogoLineas.tsx`, que pasa a componer los dos de arriba

**Interfaces:**
- Produces:
  - `DialogoPedidoBase({ titulo, nLineas, info, pie?, error, bloqueado, enviando, textoConfirmar?, onConfirmar, onCerrar, children })`;
  - `TablaLineas<L extends LineaBase>({ primera, proveedor, lineas, proveedores, vacio, seleccionada, desplazarA, onSeleccionar, onCambiar, onQuitar })`. `proveedor` es `((linea: L, n: number) => ReactNode) | null`; con `null` no hay columna Proveedor;
  - `type PrimeraColumna<L>`.
- **Restricción:** es un refactor puro. Los tests de hoy de `NuevoPedidoDialog*`, `NuevoOtroPedidoDialog` y `FormulariosPedido` tienen que pasar **sin tocarlos**.

- [ ] **Step 1: Test que falla (`TablaLineas.test.tsx`)**

```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { PROVEEDORES } from './datosPrueba'
import { lineaCompraVacia, type LineaCompra } from './lineas'
import { TablaLineas } from './TablaLineas'

const base = {
  primera: { cabecera: 'Componente', ancho: 175, celda: (l: LineaCompra) => <span>{l.idCom ?? '—'}</span> },
  lineas: [{ ...lineaCompraVacia(1, 2), idProv: 1 }],
  proveedores: PROVEEDORES,
  vacio: 'Vacía',
  seleccionada: null,
  desplazarA: null,
  onSeleccionar: vi.fn(),
  onCambiar: vi.fn(),
  onQuitar: vi.fn(),
}

describe('TablaLineas', () => {
  it('sin columna Proveedor (recuadros de «Nuevo pedido», 0.9.9): seis columnas', () => {
    renderConProviders(<TablaLineas<LineaCompra> {...base} proveedor={null} />, { sesion: SESION_SUPER })
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Componente', 'Cant.', 'P.Unit.', 'Urg.', 'Total EUR', ''])
  })
  it('con columna Proveedor, su celda va la segunda', () => {
    renderConProviders(<TablaLineas<LineaCompra> {...base} proveedor={() => <span>ACME fijo</span>} />, { sesion: SESION_SUPER })
    expect(screen.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Componente', 'Proveedor', 'Cant.', 'P.Unit.', 'Urg.', 'Total EUR', ''])
    expect(screen.getAllByRole('cell')[1]).toHaveTextContent('ACME fijo')
  })
  it('sin líneas, una fila con el texto de vacío', () => {
    renderConProviders(<TablaLineas<LineaCompra> {...base} lineas={[]} proveedor={null} />, { sesion: SESION_SUPER })
    expect(screen.getByText('Vacía')).toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/TablaLineas.test.tsx`
Expected: falla, porque `./TablaLineas` no existe.

- [ ] **Step 2: `DialogoPedidoBase.tsx` (nuevo)**

```tsx
import { useState, type ReactNode } from 'react'
import type { Blocker } from 'react-router'
import { useAvisoAlSalir } from '@/shared/lib/useAvisoAlSalir'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { GuardiaAtras } from '@/shared/ui/GuardiaAtras'
import { textoDescartar } from './lineas'

type Props = {
  titulo: string
  /** Líneas del pedido. Con alguna, F5 o cerrar la pestaña avisan y Atrás pide «Descartar». */
  nLineas: number
  info: string | null
  /** Entre la línea de información y la de error. */
  pie?: ReactNode
  error: string | null
  bloqueado: boolean
  enviando: boolean
  /** Texto del botón de confirmar (0.9.9: «Confirmar pedido (n bloques)» con varios proveedores). */
  textoConfirmar?: string
  onConfirmar: () => void
  onCerrar: () => void
  /** Lo de en medio: la barra, las líneas y «+ Añadir línea». */
  children: ReactNode
}

/** Armazón de «Nuevo pedido» y «Nuevo otro pedido» (FormularioCompraView.fxml / FormularioOtroPedidoView.fxml): modal de
 *  880 px (700 en el JavaFX; más grande desde la 0.9.6), título de 24 px, lo de en medio, línea de información, línea de
 *  error (P6) y «Cancelar» / «Confirmar pedido». Sin atajos de teclado (el JavaFX no tenía). */
export function DialogoPedidoBase({ titulo, nLineas, info, pie, error, bloqueado, enviando, textoConfirmar = 'Confirmar pedido', onConfirmar, onCerrar, children }: Props) {
  // C25: si el diálogo ya nace con líneas (precarga desde Stock/campana con la caché caliente), Radix enfoca Y
  // SELECCIONA el primer campo tabulable al abrirse (@radix-ui/react-focus-scope, focusFirst({ select: true })):
  // el Componente de la línea 1 queda con el texto en azul, y teclear lo pisa. El JavaFX no autoenfoca ese campo.
  // Con el diálogo vacío (alta manual u "Otros") no hay ninguna línea todavía en el primer render, así que el
  // foco automático de Radix no cae sobre un campo con texto y se deja como estaba (calco). El valor se congela
  // en el primer render: es justo el que ve el efecto de montaje de FocusScope, que solo corre una vez.
  const [huboPrecarga] = useState(() => nLineas > 0)
  // F5, cerrar la pestaña o salir de la web con líneas en el pedido: el navegador pregunta (el pedido aún no se ha guardado).
  useAvisoAlSalir(nLineas > 0)
  // Atrás (y Adelante) del navegador con líneas en el pedido: se para la navegación y se pide confirmación "Descartar",
  // como en "Asignar trabajos". Diferencia deliberada con el JavaFX (decisión del usuario 2026-09-27, web 0.8.5): la ✕ y
  // "Cancelar" siguen cerrando sin preguntar, como su cancelar() (FormularioCompraController.java:558).
  const [bloqueo, setBloqueo] = useState<Blocker | null>(null)
  const atrasBloqueado = bloqueo?.state === 'blocked'

  return (
    <>
    <Dialog open onOpenChange={(abierto) => { if (!abierto && !enviando) onCerrar() }}>
      <DialogContent
        aria-describedby={undefined}
        // Sin seleccionar texto en una precarga (C25): igual de accesible que el comportamiento por defecto de Radix
        // (el propio diálogo recibe el foco, sin tocar ningún campo), pero sin pisar lo precargado al teclear.
        onOpenAutoFocus={huboPrecarga ? (e) => { e.preventDefault(); (e.target as HTMLElement).focus() } : undefined}
        className="max-h-[calc(100vh-24px)] w-[880px] max-w-[min(880px,calc(100%-2rem))] gap-4 overflow-y-auto bg-fondo-vista p-7"
      >
        <DialogTitle className="text-2xl font-bold text-azul-medio">{titulo}</DialogTitle>
        {children}
        {info !== null && <p role="status" className="text-[11px] text-azul-gris">{info}</p>}
        {pie}
        {error !== null && <p role="alert" className="text-[11px] text-texto-error">{error}</p>}
        <div className="flex justify-end gap-2.5">
          <BotonSecundario type="button" disabled={enviando} onClick={onCerrar}>Cancelar</BotonSecundario>
          <BotonPrimario type="button" disabled={bloqueado} onClick={onConfirmar}>{textoConfirmar}</BotonPrimario>
        </div>
      </DialogContent>
    </Dialog>
    {nLineas > 0 && <GuardiaAtras activa={!enviando} onBloqueo={setBloqueo} />}
    <ConfirmDialog abierto={atrasBloqueado} titulo="Descartar" descripcion={textoDescartar(nLineas)} textoAccion="Descartar"
      onConfirmar={() => {
        // Como en "Asignar trabajos": se cierra el diálogo (y con él sus líneas) antes de dejar seguir la navegación.
        onCerrar()
        if (bloqueo?.state === 'blocked') bloqueo.proceed()
      }}
      onCancelar={() => { if (bloqueo?.state === 'blocked') bloqueo.reset() }} />
    </>
  )
}
```

- [ ] **Step 3: `TablaLineas.tsx` (nuevo)**

```tsx
import type { ReactNode } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { formatearImporte, parsearDecimal, parsearEntero, simboloFormulario } from '@/shared/lib/importes'
import { Checkbox } from '@/shared/ui/checkbox'
import { TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { useTasas } from '../tasa'
import { totalLinea } from './conversion'
import type { LineaBase } from './lineas'

/** Columnas tras la primera y la de Proveedor (FormularioCompraView.fxml:19-25): Cant. 55 · P.Unit. 80 · Urg. 45 ·
 *  Total EUR 90 · papelera 40 sin cabecera. */
const COLUMNAS = [
  { cabecera: 'Cant.', ancho: 55 },
  { cabecera: 'P.Unit.', ancho: 80 },
  { cabecera: 'Urg.', ancho: 45 },
  { cabecera: 'Total EUR', ancho: 90 },
  { cabecera: '', ancho: 40 },
]
/** Columna Proveedor: 155 px en el JavaFX. */
const ANCHO_PROVEEDOR = 155
/** ESTILO_EDITABLE del JavaFX (FC :321-379): caja blanca, borde #C2C8D0, radio 3, padding 5 8. */
const CLASE_EDITABLE = 'h-[26px] w-full min-w-0 rounded-[3px] border border-fila-sep bg-superficie px-2 text-[12px] text-azul-medio outline-none focus-visible:ring-2 focus-visible:ring-ring/50'

export type PrimeraColumna<L> = { cabecera: string; ancho: number; celda: (linea: L, n: number) => ReactNode }

type Props<L extends LineaBase> = {
  primera: PrimeraColumna<L>
  /** Celda de la columna Proveedor: el combo de la línea, el nombre fijo o «Pasar a…». Con null no hay columna (los
   *  recuadros de «Nuevo pedido», 0.9.9, dicen su proveedor en la cabecera). */
  proveedor: ((linea: L, n: number) => ReactNode) | null
  lineas: L[]
  /** Para la divisa de cada línea: la de su proveedor; sin proveedor, EUR. */
  proveedores: Proveedor[]
  /** Texto de la fila de la tabla vacía. */
  vacio: string
  seleccionada: number | null
  /** Línea hasta la que se desplaza la caja con scroll: la de «+ Añadir línea» o la que queda al juntar una repetida. */
  desplazarA: number | null
  onSeleccionar: (id: number) => void
  onCambiar: (id: number, cambio: Partial<LineaBase>) => void
  onQuitar: (id: number) => void
}

/** Tabla de líneas de los formularios de pedido: celdas siempre editables (P8), la fila seleccionada en navy y el Total
 *  EUR con la tasa de la divisa de cada línea. Sin proveedor, la línea se calcula como EUR (tasa 1.0 por defecto de
 *  LineaCompra). */
export function TablaLineas<L extends LineaBase>({ primera, proveedor, lineas, proveedores, vacio, seleccionada, desplazarA, onSeleccionar, onCambiar, onQuitar }: Props<L>) {
  const porId = new Map(proveedores.map((p) => [p.idProv, p]))
  const divisaDe = (l: LineaBase): string => (l.idProv === null ? undefined : porId.get(l.idProv)?.divisa) ?? 'EUR'
  const tasas = useTasas(Array.from(new Set(lineas.map(divisaDe))))
  const columnas = [primera, ...(proveedor === null ? [] : [{ cabecera: 'Proveedor', ancho: ANCHO_PROVEEDOR }]), ...COLUMNAS]
  const suma = columnas.reduce((a, c) => a + c.ancho, 0)

  function totalDe(l: L): string {
    const total = totalLinea(parsearDecimal(l.precio), tasas[divisaDe(l)]?.tasa ?? null, parsearEntero(l.cantidad))
    return total === null ? '—' : formatearImporte(total, '€')
  }

  return (
    <table className="w-full table-fixed text-sm">
      <colgroup>
        {columnas.map((c, i) => <col key={i} style={{ width: `${(c.ancho / suma) * 100}%` }} />)}
      </colgroup>
      <TableHeader className="bg-crema">
        <TableRow className="hover:bg-transparent">
          {columnas.map((c, i) => (
            <TableHead key={i} className="h-8 px-1 text-[12px] font-bold text-azul-medio">{c.cabecera}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {lineas.length === 0 && (
          <TableRow className="hover:bg-transparent">
            <TableCell colSpan={columnas.length} className="py-8 text-center text-azul-gris">{vacio}</TableCell>
          </TableRow>
        )}
        {lineas.map((l, i) => {
          const n = i + 1
          return (
            <TableRow
              key={l.id}
              ref={l.id === desplazarA ? (el: HTMLTableRowElement | null) => { el?.scrollIntoView({ block: 'nearest' }) } : undefined}
              data-state={l.id === seleccionada ? 'selected' : undefined}
              // Enfocar cualquier campo de la fila la selecciona (FC :203-205); el foco burbujea en React.
              onFocus={() => onSeleccionar(l.id)}
              onClick={() => onSeleccionar(l.id)}
              className="group border-b border-fila-sep hover:bg-transparent data-[state=selected]:border-b-fila-selected-brd data-[state=selected]:bg-azul-medio data-[state=selected]:text-crema"
            >
              <TableCell className="px-1 py-1">{primera.celda(l, n)}</TableCell>
              {proveedor !== null && <TableCell className="px-1 py-1">{proveedor(l, n)}</TableCell>}
              <TableCell className="px-1 py-1">
                <input aria-label={`Cantidad línea ${n}`} value={l.cantidad} onChange={(e) => onCambiar(l.id, { cantidad: e.target.value })} className={CLASE_EDITABLE} />
              </TableCell>
              <TableCell className="px-1 py-1">
                <div className="flex items-center gap-1">
                  <input aria-label={`Precio línea ${n}`} value={l.precio} onChange={(e) => onCambiar(l.id, { precio: e.target.value })} className={CLASE_EDITABLE} />
                  <span className="shrink-0 text-[12px]">{simboloFormulario(divisaDe(l))}</span>
                </div>
              </TableCell>
              <TableCell className="px-1 py-1 text-center">
                <Checkbox aria-label={`Urgente línea ${n}`} checked={l.urgente} onCheckedChange={(v) => onCambiar(l.id, { urgente: v === true })} className="bg-superficie" />
              </TableCell>
              <TableCell className="px-1 py-1 text-[12px]">{totalDe(l)}</TableCell>
              <TableCell className="px-1 py-1 text-center">
                <button type="button" aria-label={`Quitar línea ${n}`} onClick={(e) => { e.stopPropagation(); onQuitar(l.id) }} className="cursor-pointer bg-transparent">
                  <img src="/borrar.png" alt="" className="h-[25px] w-[25px]" />
                </button>
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </table>
  )
}
```

- [ ] **Step 4: `DialogoLineas.tsx` (reescrito con los dos)**

Mismas `Props<L>` y el mismo comportamiento. Sustituir el fichero entero por:

```tsx
import { useState, type ReactNode } from 'react'
import type { Proveedor } from '@/shared/api/client'
import { BotonSecundario } from '@/shared/ui/Botones'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { DialogoPedidoBase } from './DialogoPedidoBase'
import type { LineaBase } from './lineas'
import { TablaLineas, type PrimeraColumna } from './TablaLineas'

/** Ancho del combo de proveedor dentro de su celda (155 px menos el padding; cabe también con el reparto proporcional de
 *  "Nuevo otro pedido"). */
const ANCHO_COMBO_PROVEEDOR = 140

type Props<L extends LineaBase> = {
  titulo: string
  primera: PrimeraColumna<L>
  barra?: ReactNode
  /** 0.9.9: resumen «Bloques» de «Nuevo pedido», encima de los botones. */
  pie?: ReactNode
  /** 0.9.9: proveedor fijo de todas las líneas, sin desplegable. Lo usan «Añadir líneas» (el proveedor del bloque) y
   *  «Nuevo pedido» (el único proveedor del pedido, «—» hasta que se elige). */
  proveedorFijo?: string
  lineas: L[]
  proveedores: Proveedor[]
  info: string | null
  /** Petición de selección desde fuera (0.9.9, 2026-10-13: al juntar una pieza repetida queda seleccionada la línea que
   *  ya la tenía). Cada `marca` nueva selecciona la línea `id`. */
  seleccionar?: { id: number; marca: number } | null
  error: string | null
  bloqueado: boolean
  enviando: boolean
  onCambiar: (id: number, cambio: Partial<LineaBase>) => void
  onQuitar: (id: number) => void
  onAnadir: () => number
  onConfirmar: () => void
  onCerrar: () => void
}

/** "Nuevo pedido" y "Nuevo otro pedido" con una sola tabla: el armazón (DialogoPedidoBase), la barra opcional, la tabla
 *  de líneas (TablaLineas) en una caja de alto máximo con scroll propio y "+ Añadir línea". La selección de línea es suya. */
export function DialogoLineas<L extends LineaBase>({ titulo, primera, barra, pie, proveedorFijo, lineas, proveedores, info, seleccionar: peticion, error, bloqueado, enviando, onCambiar, onQuitar, onAnadir, onConfirmar, onCerrar }: Props<L>) {
  const [seleccionada, setSeleccionada] = useState<number | null>(null)
  // Línea hasta la que se desplaza la caja con scroll: la de «+ Añadir línea» o la que queda al juntar una repetida.
  const [anadida, setAnadida] = useState<number | null>(null)
  // Ajuste durante el render (sin useEffect): una marca nueva de `seleccionar` selecciona esa línea una vez y desplaza
  // la caja hasta ella (con muchas líneas puede estar fuera de la vista).
  const [marcaVista, setMarcaVista] = useState<number | null>(null)
  if (peticion && peticion.marca !== marcaVista) {
    setMarcaVista(peticion.marca)
    setSeleccionada(peticion.id)
    setAnadida(peticion.id)
  }
  const opciones = proveedores.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre }))

  function seleccionar(id: number) {
    setSeleccionada(id)
    setAnadida(null)
  }
  /** "+ Añadir línea" (añadirFila :496-513): añade, selecciona y desplaza hasta la línea nueva. */
  function anadir() {
    const id = onAnadir()
    setSeleccionada(id)
    setAnadida(id)
  }

  return (
    <DialogoPedidoBase titulo={titulo} nLineas={lineas.length} info={info} pie={pie} error={error} bloqueado={bloqueado} enviando={enviando}
      onConfirmar={onConfirmar} onCerrar={onCerrar}>
      {barra}
      {/* Alto máximo con scroll propio (ListView a prefHeight 220 de FormularioCompraView.fxml, C24; 300-420 desde la 0.9.6): con
          muchas líneas la tabla desplaza y "Cancelar"/"Confirmar pedido" quedan siempre a la vista. El popup del
          autocompletar de Componente va en un portal (CampoAutocompletar), así que el recorte de esta caja no lo
          afecta: no hace falta que el scroll lo traiga a la vista. */}
      <div className="min-h-[300px] max-h-[420px] overflow-y-auto rounded-md bg-superficie">
        <TablaLineas<L>
          primera={primera}
          proveedor={(l, n) => (proveedorFijo !== undefined ? (
            <span className="inline-block rounded-full bg-nav-switch px-2.5 py-1 text-[12px] font-bold text-azul-medio">{proveedorFijo}</span>
          ) : (
            <ComboNavy valor={l.idProv === null ? null : String(l.idProv)} opciones={opciones} onChange={(v) => onCambiar(l.id, { idProv: Number(v) })} textoVacio="" ancho={ANCHO_COMBO_PROVEEDOR} visibles={8} aria-label={`Proveedor línea ${n}`} />
          ))}
          lineas={lineas}
          proveedores={proveedores}
          vacio="Añade al menos una línea"
          seleccionada={seleccionada}
          desplazarA={anadida}
          onSeleccionar={seleccionar}
          onCambiar={onCambiar}
          onQuitar={onQuitar}
        />
      </div>
      <div>
        <BotonSecundario type="button" disabled={bloqueado} onClick={anadir}>+ Añadir línea</BotonSecundario>
      </div>
    </DialogoPedidoBase>
  )
}
```

- [ ] **Step 5: Comprobar que pasa**

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos/formulario`, con todos los tests de hoy intactos;
2. `npx tsc -b && npm run lint`.

Expected: verde, sin haber cambiado ningún test que ya existía.

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/DialogoPedidoBase.tsx src/modules/almacen/pedidos/formulario/TablaLineas.tsx src/modules/almacen/pedidos/formulario/TablaLineas.test.tsx src/modules/almacen/pedidos/formulario/DialogoLineas.tsx
git commit -m "refactor: armazon del dialogo de pedido y tabla de lineas aparte"
```

---

### Task 3: Recuadros de «Nuevo pedido»: de proveedor, «Por repartir» y destino

**Files:**
- Create: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/RecuadroProveedor.tsx` y `formulario/RecuadroProveedor.test.tsx`

**Interfaces:**
- Consumes: `VALOR_NUEVO` y `type OpcionDestino` de `./destinos`, y `textoLineas` de `../bloques/reglas`.
- Produces:
  - `RecuadroProveedor({ nombre, divisa, nLineas, destino: ReactNode, nPrevision, previsionDeshabilitada, onPrevision, onQuitar?, bloqueado, onAnadir, avisos: string[], plegado, onPlegar, marcado, aLaVista, children })`;
  - `RecuadroRepartir({ nLineas, plegado, onPlegar, marcado, aLaVista, children })`;
  - `DestinoRecuadro({ nombre, fijado?, opciones?, valor?, cargando?, onElegir? })`.

- [ ] **Step 1: Tests que fallan (`RecuadroProveedor.test.tsx`)**

```tsx
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { describe, expect, it, onTestFinished, vi } from 'vitest'
import { DestinoRecuadro, RecuadroProveedor, RecuadroRepartir } from './RecuadroProveedor'

function recuadro(extra: Partial<ComponentProps<typeof RecuadroProveedor>> = {}) {
  const props: ComponentProps<typeof RecuadroProveedor> = {
    nombre: 'ACME', divisa: 'EUR', nLineas: 2, destino: <span>destino</span>, nPrevision: 3, previsionDeshabilitada: false,
    onPrevision: vi.fn(), onQuitar: vi.fn(), bloqueado: false, onAnadir: vi.fn(), avisos: [], plegado: false, onPlegar: vi.fn(),
    marcado: false, aLaVista: false, children: <p>tabla</p>, ...extra,
  }
  render(<RecuadroProveedor {...props} />)
  return props
}

describe('RecuadroProveedor (0.9.9, varios proveedores)', () => {
  it('cabecera con el proveedor, sus líneas, divisa, destino, «Añadir previsión (n)» y la ×; el cuerpo con la tabla y «+ Añadir línea»', async () => {
    const p = recuadro()
    const r = screen.getByRole('region', { name: 'Pedido a ACME' })
    expect(r).toHaveTextContent('ACME')
    expect(r).toHaveTextContent('· 2 líneas · EUR')
    expect(r).toHaveTextContent('destino')
    expect(within(r).getByText('tabla')).toBeInTheDocument()
    await userEvent.click(within(r).getByRole('button', { name: 'Añadir previsión (3)' }))
    expect(p.onPrevision).toHaveBeenCalled()
    await userEvent.click(within(r).getByRole('button', { name: 'Quitar ACME' }))
    expect(p.onQuitar).toHaveBeenCalled()
    await userEvent.click(within(r).getByRole('button', { name: '+ Añadir línea' }))
    expect(p.onAnadir).toHaveBeenCalled()
    const plegar = within(r).getByRole('button', { name: 'Plegar ACME' })
    expect(plegar).toHaveAttribute('aria-expanded', 'true')
    await userEvent.click(plegar)
    expect(p.onPlegar).toHaveBeenCalled()
  })

  it('plegado: sin cuerpo y el botón dice «Desplegar ACME»', () => {
    recuadro({ plegado: true })
    expect(screen.queryByText('tabla')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+ Añadir línea' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Desplegar ACME' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('sin onQuitar no hay ×; con la previsión a 0, deshabilitada; avisos «también va a»; una línea en singular', () => {
    recuadro({ onQuitar: undefined, nPrevision: 0, avisos: ['lcd-x-negro también va a Proveedor B (4).'], nLineas: 1 })
    expect(screen.queryByRole('button', { name: 'Quitar ACME' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Añadir previsión (0)' })).toBeDisabled()
    expect(screen.getByText('lcd-x-negro también va a Proveedor B (4).')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Pedido a ACME' })).toHaveTextContent('· 1 línea · EUR')
  })

  it('marcado: data-marcado y borde de error; aLaVista: se desplaza hasta él', () => {
    const desplazar = vi.spyOn(Element.prototype, 'scrollIntoView')
    onTestFinished(() => desplazar.mockRestore())
    recuadro({ marcado: true, aLaVista: true })
    const r = screen.getByRole('region', { name: 'Pedido a ACME' })
    expect(r).toHaveAttribute('data-marcado', 'true')
    expect(r).toHaveClass('border-texto-error')
    expect(desplazar.mock.contexts).toContain(r)
  })
})

describe('RecuadroRepartir', () => {
  it('cabecera ámbar con las líneas y la explicación; plegado sin cuerpo', async () => {
    const onPlegar = vi.fn()
    const { rerender } = render(<RecuadroRepartir nLineas={2} plegado={false} onPlegar={onPlegar} marcado={false} aLaVista={false}><p>filas</p></RecuadroRepartir>)
    const r = screen.getByRole('region', { name: 'Por repartir' })
    expect(r).toHaveTextContent('⚠ Por repartir')
    expect(r).toHaveTextContent('· 2 líneas · las venden varios proveedores o ninguno: elige a quién se las pides')
    expect(within(r).getByText('filas')).toBeInTheDocument()
    await userEvent.click(within(r).getByRole('button', { name: 'Plegar Por repartir' }))
    expect(onPlegar).toHaveBeenCalled()
    rerender(<RecuadroRepartir nLineas={2} plegado onPlegar={onPlegar} marcado={false} aLaVista={false}><p>filas</p></RecuadroRepartir>)
    expect(screen.queryByText('filas')).not.toBeInTheDocument()
  })
})

describe('DestinoRecuadro', () => {
  it('fijado, cargando y sin abiertos', () => {
    const { rerender } = render(<DestinoRecuadro nombre="ACME" fijado={41} />)
    expect(screen.getByText(/Bloque 41/).parentElement).toHaveTextContent('Bloque 41 (fijado)')
    rerender(<DestinoRecuadro nombre="ACME" cargando />)
    expect(screen.getByText('Cargando bloques…')).toBeInTheDocument()
    rerender(<DestinoRecuadro nombre="ACME" opciones={[]} />)
    expect(screen.getByText(/Bloque nuevo/).parentElement).toHaveTextContent('Bloque nuevo (no tiene ninguno abierto)')
  })

  it('con abiertos, el combo «Bloque de ACME»: «Bloque nuevo» → null; un bloque → su id', async () => {
    const onElegir = vi.fn()
    const opciones = [{ valor: '41', etiqueta: 'Añadir a Bloque 41 · 09/10 · 1 línea' }, { valor: 'nuevo', etiqueta: 'Bloque nuevo' }]
    render(<DestinoRecuadro nombre="ACME" opciones={opciones} valor={41} onElegir={onElegir} />)
    expect(screen.getByRole('combobox', { name: 'Bloque de ACME' })).toHaveTextContent('Añadir a Bloque 41')
    await userEvent.click(screen.getByRole('combobox', { name: 'Bloque de ACME' }))
    await userEvent.click(within(screen.getByRole('listbox', { name: 'Bloque de ACME' })).getByRole('button', { name: 'Bloque nuevo' }))
    expect(onElegir).toHaveBeenLastCalledWith(null)
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/RecuadroProveedor.test.tsx`
Expected: falla, porque el componente no existe.

- [ ] **Step 2: Implementación (`RecuadroProveedor.tsx`)**

```tsx
import type { ReactNode } from 'react'
import { ChevronDown, ChevronRight, X } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { BotonSecundario } from '@/shared/ui/Botones'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { textoLineas } from '../bloques/reglas'
import { VALOR_NUEVO, type OpcionDestino } from './destinos'

/** Desplaza la caja hasta el recuadro mientras `activo` (al añadirlo, o con el error de «Confirmar»). `block: 'nearest'`
 *  no mueve nada si ya se ve. */
const traerALaVista = (activo: boolean) => (activo ? (el: HTMLElement | null) => { el?.scrollIntoView({ block: 'nearest' }) } : undefined)

function BotonPlegar({ nombre, plegado, onPlegar }: { nombre: string; plegado: boolean; onPlegar: () => void }) {
  return (
    <button type="button" aria-expanded={!plegado} aria-label={`${plegado ? 'Desplegar' : 'Plegar'} ${nombre}`} onClick={onPlegar}
      className="flex size-6 shrink-0 cursor-pointer items-center justify-center rounded text-current hover:bg-nav-switch">
      {plegado ? <ChevronRight aria-hidden="true" className="size-4" /> : <ChevronDown aria-hidden="true" className="size-4" />}
    </button>
  )
}

type Comunes = {
  plegado: boolean
  onPlegar: () => void
  /** Recuadro del error de «Confirmar»: borde rojo. */
  marcado: boolean
  aLaVista: boolean
  children: ReactNode
}

type Props = Comunes & {
  nombre: string
  divisa: string
  nLineas: number
  /** A qué bloque va: DestinoRecuadro. */
  destino: ReactNode
  nPrevision: number
  previsionDeshabilitada: boolean
  onPrevision: () => void
  /** La ×. Sin ella no hay botón («Añadir líneas» de un bloque: el recuadro es fijo). */
  onQuitar?: () => void
  bloqueado: boolean
  onAnadir: () => void
  /** «… también va a X (n).» */
  avisos: string[]
}

/** Recuadro de un proveedor en «Nuevo pedido» (0.9.9, spec 2026-10-13 varios proveedores §2): es un bloque. Cabecera con
 *  plegar, el proveedor, sus líneas y divisa, el destino, «Añadir previsión (n)» y la ×; cuerpo con la tabla (children),
 *  «+ Añadir línea» y los avisos «también va a». */
export function RecuadroProveedor({ nombre, divisa, nLineas, destino, nPrevision, previsionDeshabilitada, onPrevision, onQuitar, bloqueado, onAnadir, avisos, plegado, onPlegar, marcado, aLaVista, children }: Props) {
  return (
    <section aria-label={`Pedido a ${nombre}`} ref={traerALaVista(aLaVista)} data-marcado={marcado ? 'true' : undefined}
      className={cn('shrink-0 overflow-hidden rounded-[10px] border bg-superficie', marcado ? 'border-texto-error ring-1 ring-texto-error' : 'border-fila-sep')}>
      <div className={cn('flex flex-wrap items-center gap-2 bg-fila-maestro-bg px-2.5 py-1.5 text-[12px] text-azul-medio', !plegado && 'border-b border-fila-sep')}>
        <BotonPlegar nombre={nombre} plegado={plegado} onPlegar={onPlegar} />
        <span className="text-[13px] font-bold">{nombre}</span>
        <span className="text-azul-gris">· {textoLineas(nLineas)} · {divisa}</span>
        <span aria-hidden="true" className="text-texto-sub">→</span>
        {destino}
        <span className="flex-1" />
        <BotonSecundario type="button" disabled={previsionDeshabilitada || nPrevision === 0} onClick={onPrevision}>Añadir previsión ({nPrevision})</BotonSecundario>
        {onQuitar && (
          <button type="button" aria-label={`Quitar ${nombre}`} title={`Quitar ${nombre}`} onClick={onQuitar}
            className="flex size-6 shrink-0 cursor-pointer items-center justify-center rounded-full text-texto-sub hover:bg-nav-switch hover:text-texto-error">
            <X aria-hidden="true" className="size-4" />
          </button>
        )}
      </div>
      {!plegado && (
        <div className="flex flex-col gap-2 p-2.5">
          {children}
          <div className="flex flex-wrap items-center gap-2.5">
            <BotonSecundario type="button" disabled={bloqueado} onClick={onAnadir}>+ Añadir línea</BotonSecundario>
            {avisos.length > 0 && <p className="text-[11px] text-azul-gris">{avisos.join(' ')}</p>}
          </div>
        </div>
      )}
    </section>
  )
}

/** Recuadro «⚠ Por repartir» (§3): las líneas precargadas que venden varios proveedores o ninguno. Va el primero, en ámbar. */
export function RecuadroRepartir({ nLineas, plegado, onPlegar, marcado, aLaVista, children }: Comunes & { nLineas: number }) {
  return (
    <section aria-label="Por repartir" ref={traerALaVista(aLaVista)} data-marcado={marcado ? 'true' : undefined}
      className={cn('shrink-0 overflow-hidden rounded-[10px] border bg-superficie', marcado ? 'border-texto-error ring-1 ring-texto-error' : 'border-fila-pendiente-brd')}>
      <div className={cn('flex flex-wrap items-center gap-2 bg-badge-pendiente-bg px-2.5 py-1.5 text-[12px] text-badge-pendiente-text', !plegado && 'border-b border-fila-pendiente-brd')}>
        <BotonPlegar nombre="Por repartir" plegado={plegado} onPlegar={onPlegar} />
        <span className="text-[13px] font-bold">⚠ Por repartir</span>
        <span>· {textoLineas(nLineas)} · las venden varios proveedores o ninguno: elige a quién se las pides</span>
      </div>
      {!plegado && <div className="p-2.5">{children}</div>}
    </section>
  )
}

type DestinoProps = {
  nombre: string
  /** «Añadir líneas» de un bloque: destino fijo. */
  fijado?: number
  opciones?: OpcionDestino[]
  valor?: number | null
  /** Los pedidos aún no han llegado (no se sabe qué bloques hay abiertos). */
  cargando?: boolean
  onElegir?: (idBloque: number | null) => void
}

/** A qué bloque va un recuadro (spec §6.6 de bloques, ahora en la cabecera): fijado, cargando, el desplegable de sus
 *  bloques abiertos y «Bloque nuevo», o «Bloque nuevo (no tiene ninguno abierto)». */
export function DestinoRecuadro({ nombre, fijado, opciones = [], valor = null, cargando = false, onElegir }: DestinoProps) {
  if (fijado !== undefined) return <span><b>Bloque {fijado}</b> <span className="text-azul-gris">(fijado)</span></span>
  if (cargando) return <span className="text-azul-gris">Cargando bloques…</span>
  if (opciones.length === 0) return <span><b>Bloque nuevo</b> <span className="text-azul-gris">(no tiene ninguno abierto)</span></span>
  return (
    <ComboNavy valor={valor === null ? VALOR_NUEVO : String(valor)} opciones={opciones} onChange={(v) => onElegir?.(v === VALOR_NUEVO ? null : Number(v))}
      textoVacio="" ancho={300} visibles={8} aria-label={`Bloque de ${nombre}`} />
  )
}
```

- [ ] **Step 3: Comprobar que pasa**

Run:
1. `npx vitest run src/modules/almacen/pedidos/formulario/RecuadroProveedor.test.tsx`;
2. `npx tsc -b && npm run lint`.

Expected: verde. Si una clase de color no existe, búscala en `src/shared/styles/tokens.css`:
- `fila-maestro-bg`, `fila-pendiente-brd`, `badge-pendiente-bg`, `badge-pendiente-text`, `texto-error`, `texto-sub` y `fila-sep`;
- se usan con el prefijo `--color-`.

- [ ] **Step 4: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/RecuadroProveedor.tsx src/modules/almacen/pedidos/formulario/RecuadroProveedor.test.tsx
git commit -m "feat: recuadros de proveedor y por repartir para nuevo pedido"
```

---

### Task 4: «Nuevo pedido» con un recuadro por proveedor; tests y documentación

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`, reescrito.
- Modify: `formulario/NuevoPedidoDialog.test.tsx` y `formulario/NuevoPedidoDialog.bloques.test.tsx`, reescritos.
- Modify en `formulario/lineas.ts`:
  - quitar `aplicarProveedorATodas` y `MSG_SIN_PROVEEDOR`;
  - `avisoRepetida` con proveedor;
  - y su test en `formulario/lineas.test.ts`.
- Modify: `formulario/DialogoLineas.tsx`: fuera las props `proveedorFijo`, `pie` y `seleccionar`, que ya nadie usa.
- Delete: `formulario/ResumenBloques.tsx`.
- Modify: `CHANGELOG.md` (`[0.9.9]`) y `docs/paridad/pedidos.md`.

**Interfaces:**
- Consumes:
  - Tarea 1: `loQueFalta`, `cuantasPrevision(activos, idProv, lineas)`, `elegirPieza`, `aplicarPrevision` y todo `recuadros.ts`;
  - Tarea 2: `DialogoPedidoBase` y `TablaLineas`;
  - Tarea 3: `RecuadroProveedor`, `RecuadroRepartir` y `DestinoRecuadro`.
- Produces: `avisoRepetida(nombre: string, proveedor: string | null, n: number): string`.

- [ ] **Step 1: `lineas.ts` y su test**

En `lineas.ts`:
- borrar `MSG_SIN_PROVEEDOR`, con su comentario, y la función `aplicarProveedorATodas`, con su comentario;
- sustituir `avisoRepetida` por:

```ts
/** Aviso de pieza repetida dentro de un recuadro (2026-10-13; con el proveedor desde varios proveedores en un pedido). */
export function avisoRepetida(nombre: string, proveedor: string | null, n: number): string {
  return proveedor === null ? `${nombre} ya está en «Por repartir» (línea ${n}).` : `${nombre} ya está en el pedido a ${proveedor} (línea ${n}).`
}
```

En `lineas.test.ts`:
- quitar `aplicarProveedorATodas` del import;
- borrar el test «aplicarProveedorATodas pone el proveedor en todas las líneas»;
- el test «aviso de repetida» pasa a:

```ts
  it('aviso de repetida, con el proveedor del recuadro o «Por repartir»', () => {
    expect(avisoRepetida('lcd-x-negro', 'ACME', 3)).toBe('lcd-x-negro ya está en el pedido a ACME (línea 3).')
    expect(avisoRepetida('lcd-x-negro', null, 1)).toBe('lcd-x-negro ya está en «Por repartir» (línea 1).')
  })
```

- [ ] **Step 2: `DialogoLineas.tsx` sin lo que ya no se usa**

Solo lo usa «Nuevo otro pedido», que no pasa `proveedorFijo`, `pie` ni `seleccionar`. Compruébalo con `grep -n "DialogoLineas" -r src`. Hay que:
- quitar esas tres props del tipo, de la desestructuración y de su JSDoc;
- quitar `marcaVista` y el bloque `if (peticion …)`;
- dejar en la celda de proveedor solo el `ComboNavy`;
- pasar a `DialogoPedidoBase` sin `pie`.

El comentario del componente pasa a:

```tsx
/** "Nuevo otro pedido": el armazón (DialogoPedidoBase), la barra opcional, la tabla de líneas (TablaLineas) con el
 *  combo de proveedor en cada línea, en una caja de alto máximo con scroll propio, y "+ Añadir línea". La selección de
 *  línea es suya. («Nuevo pedido», desde la 0.9.9, va por recuadros de proveedor: NuevoPedidoDialog.) */
```

Después, borrar `formulario/ResumenBloques.tsx` con `git rm`.

- [ ] **Step 3: `NuevoPedidoDialog.tsx` (reescrito)**

```tsx
import { useMemo, useState } from 'react'
import { StaleDataError } from '@/shared/api/errors'
import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { CampoAutocompletar } from '@/shared/ui/CampoAutocompletar'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { useProveedoresComponentes } from '../../proveedores/api'
import { useComponentesStock } from '../../stock/api'
import { agruparCompartidos, nombreGrupo } from '../../stock/grupos'
import { useCompras, useGuardarLoteCompras } from '../api'
import { agruparEnBloques, situacion } from '../bloques/reglas'
import { destinoElegido, destinosDelCuerpo, opcionesDestino } from './destinos'
import { DialogoPedidoBase } from './DialogoPedidoBase'
import { mensajeErrorGuardado } from './errores'
import {
  aplicarPrevision, avisoNadaQuePedir, avisoOmitidas, avisoRepetida, avisoSinLista, avisoSinPedido, cambiarLinea, cuantasPrevision, cuerpoLoteCompras,
  elegirPieza, lineaCompraVacia, ponerCantidadesPrevistas, precargaInicial, quitarLinea, siguienteId, sinLista, type LineaBase, type LineaCompra,
} from './lineas'
import { DestinoRecuadro, RecuadroProveedor, RecuadroRepartir } from './RecuadroProveedor'
import {
  avisoYaEstaba, lineasDe, opcionesOtroProveedor, opcionesPasarA, pasarA, POR_REPARTIR, recuadrosDelPedido, repartirLineas, tambienVa,
  textoConfirmar, textoQuitarRecuadro, validarPedido, type ClaveRecuadro,
} from './recuadros'
import { TablaLineas } from './TablaLineas'

const OPERACION = 'compras:lote'

type Props = { precarga: PrecargaPedido; onCerrar: () => void }

/** El conjunto sin `clave`: desplegar un recuadro. */
function sinClave(plegados: ReadonlySet<ClaveRecuadro>, clave: ClaveRecuadro): ReadonlySet<ClaveRecuadro> {
  const resto = new Set(plegados)
  resto.delete(clave)
  return resto
}

/** "Nuevo pedido" (FormularioCompraController, spec §6): modal en el sitio (P1) con la precarga del store. Un único
 *  POST /api/compras/lote con Idempotency-Key (P5): misma clave mientras el cuerpo no cambie, nueva tras un éxito.
 *  0.9.9 (spec 2026-10-13 varios proveedores): un recuadro por proveedor, que es su bloque, con su destino en la
 *  cabecera; lo precargado se reparte solo al proveedor que vende cada pieza y lo demás espera en «Por repartir». Modo
 *  «Añadir líneas» de un bloque (precarga `bloque`): un único recuadro con proveedor y destino fijos. */
export function NuevoPedidoDialog({ precarga, onCerrar }: Props) {
  const componentes = useComponentesStock({ activo: false })
  const proveedores = useProveedoresComponentes({ activo: false })
  // Los bloques salen de los pedidos (sin sondeo: con el formulario abierto las vistas no refrescan).
  const compras = useCompras({ activo: false })
  const bloques = useMemo(() => agruparEnBloques(compras.data ?? []), [compras.data])
  const fijo = precarga.modo === 'bloque' ? precarga : null
  const activos = useMemo(() => (componentes.data ?? []).filter((c) => c.activo), [componentes.data])
  const proveedoresActivos = useMemo(() => (proveedores.data ?? []).filter((p) => p.activo), [proveedores.data])
  const idsActivos = useMemo(() => new Set(proveedoresActivos.map((p) => p.idProv)), [proveedoresActivos])
  const opciones = useMemo(() => agruparCompartidos(activos).map((f) => ({ clave: String(f.idCom), etiqueta: nombreGrupo(f) })), [activos])
  const [lineas, setLineas] = useState<LineaCompra[] | null>(precarga.modo === 'vacio' || fijo ? [] : null)
  // Proveedores elegidos a mano, aunque aún no tengan líneas. En «Añadir líneas», el del bloque.
  const [anadidos, setAnadidos] = useState<number[]>(fijo ? [fijo.idProv] : [])
  const [plegados, setPlegados] = useState<ReadonlySet<ClaveRecuadro>>(() => new Set())
  // Recuadro con el error de «Confirmar» (borde rojo) y recuadro que se trae a la vista.
  const [marcado, setMarcado] = useState<ClaveRecuadro | null>(null)
  const [aLaVista, setALaVista] = useState<ClaveRecuadro | null>(null)
  // Recuadro con líneas que se va a quitar: espera la confirmación.
  const [quitando, setQuitando] = useState<number | null>(null)
  // Una línea seleccionada para todo el diálogo. La de juntar una repetida va por petición (marca), porque el clic en la
  // opción del autocompletar burbujea hasta la fila que desaparece y la seleccionaría después.
  const [seleccionada, setSeleccionada] = useState<number | null>(null)
  const [desplazarA, setDesplazarA] = useState<number | null>(null)
  const [peticion, setPeticion] = useState<{ id: number; marca: number } | null>(null)
  const [marcaVista, setMarcaVista] = useState<number | null>(null)
  if (peticion && peticion.marca !== marcaVista) {
    setMarcaVista(peticion.marca)
    setSeleccionada(peticion.id)
    setDesplazarA(peticion.id)
  }
  const [omitidas, setOmitidas] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [sinPedido, setSinPedido] = useState(0)
  // Destino elegido a mano por proveedor (idBloque o null = bloque nuevo).
  const [elegidos, setElegidos] = useState<Record<number, number | null>>({})
  const [sinListaVistas, setSinListaVistas] = useState(0)
  const [nadaQuePedir, setNadaQuePedir] = useState(false)
  const [repetida, setRepetida] = useState<string | null>(null)
  // «Añadir previsión…» de un bloque: se aplica una vez, cuando el listado de Stock ha cargado (bien o mal).
  const [previsionPendiente, setPrevisionPendiente] = useState(fijo?.prevision === true)
  // «Pedir» y «Pedir todas las piezas» (2026-10-13): su «Pedir 60 d» se pone una vez, con la lista fresca.
  const [cantidadesPendientes, setCantidadesPendientes] = useState(precarga.modo === 'componentes')
  // Una instancia por apertura: el host remonta el diálogo en cada apertura (key), así que no sobrevive a un éxito.
  const [claves] = useState(() => crearClavesIdempotencia())
  const guardar = useGuardarLoteCompras()
  const bloqueFijo = fijo ? bloques.get(String(fijo.idBloque)) : undefined
  const nombreFijo = fijo ? proveedoresActivos.find((p) => p.idProv === fijo.idProv)?.nombre ?? bloqueFijo?.nombreProveedor ?? '' : ''
  const nombreProv = (idProv: number) => (fijo !== null && idProv === fijo.idProv ? nombreFijo : proveedoresActivos.find((p) => p.idProv === idProv)?.nombre ?? `Proveedor ${idProv}`)
  const nombrePieza = (idCom: number) => opciones.find((o) => o.clave === String(idCom))?.etiqueta ?? ''

  // Precarga una sola vez, cuando la lista de componentes termina de cargar (bien o mal): patrón "ajustar el estado
  // durante el render" (CampoAutocompletar, useErrorServidor), sin useEffect + setState. Espera también a los
  // proveedores: el reparto automático necesita saber cuáles están activos.
  // Si la carga falla, `activos` queda vacío y toda solicitud parecería "omitida" (falso: no se ha podido leer la
  // lista, no es que estén desactivados). El diálogo global de error ya avisa del fallo, así que aquí no se cuenta
  // ninguna omitida y no sale la línea de información.
  if (lineas === null && (componentes.isSuccess || componentes.isError) && (proveedores.isSuccess || proveedores.isError)) {
    const inicial = precargaInicial(precarga, activos)
    setLineas(repartirLineas(inicial.lineas, activos, idsActivos))
    setOmitidas(componentes.isError ? 0 : inicial.omitidas)
  }
  // «Pedir» y «Pedir todas las piezas» (2026-10-13): las líneas salen ya (C25); su «Pedir 60 d», con la lista fresca.
  if (cantidadesPendientes && lineas !== null && ((componentes.isSuccess && !componentes.isFetching) || componentes.isError)) {
    setCantidadesPendientes(false)
    if (componentes.isSuccess) setLineas(ponerCantidadesPrevistas(lineas, activos))
  }
  // Con la caché de Stock ya cargada, `isSuccess` es true con datos viejos: se espera a que acabe la recarga.
  if (previsionPendiente && fijo && lineas !== null && ((componentes.isSuccess && !componentes.isFetching) || componentes.isError)) {
    setPrevisionPendiente(false)
    if (componentes.isSuccess) {
      const r = aplicarPrevision(lineas, activos, fijo.idProv)
      setLineas(r.lineas)
      setSinPedido(r.sinPedido)
      setSinListaVistas(sinLista(activos))
      setNadaQuePedir(r.lineas.length === lineas.length)
    }
  }

  const todas = lineas ?? []
  const recuadros = fijo ? [fijo.idProv] : recuadrosDelPedido(todas, anadidos, nombreProv)
  const porRepartir = lineasDe(todas, POR_REPARTIR)
  const nBloques = recuadros.filter((idProv) => lineasDe(todas, idProv).length > 0).length
  const opcionesOtro = fijo ? [] : opcionesOtroProveedor(proveedoresActivos, recuadros, bloques)

  /** Tocar las líneas borra los avisos, el error y la marca del recuadro. */
  function limpiarAvisos() {
    setError(null)
    setSinPedido(0)
    setSinListaVistas(0)
    setNadaQuePedir(false)
    setRepetida(null)
    setMarcado(null)
    setALaVista(null)
  }
  function seleccionar(id: number) {
    setSeleccionada(id)
    setDesplazarA(null)
  }
  function cambiar(id: number, cambio: Partial<LineaBase>) {
    setLineas((ls) => cambiarLinea(ls ?? [], id, cambio))
    limpiarAvisos()
  }
  /** Elegir la pieza de una línea: lo que falta y, si la pieza ya estaba en su recuadro, se junta en esa línea. */
  function elegir(id: number, idCom: number) {
    const linea = todas.find((l) => l.id === id)
    const r = elegirPieza(todas, id, idCom, activos)
    setLineas(r.lineas)
    limpiarAvisos()
    if (r.repetida === null || linea === undefined) return
    const { id: queda, n } = r.repetida
    setRepetida(avisoRepetida(nombrePieza(idCom), linea.idProv === null ? null : nombreProv(linea.idProv), n))
    setPeticion((p) => ({ id: queda, marca: (p?.marca ?? 0) + 1 }))
  }
  function quitar(id: number) {
    setLineas((ls) => quitarLinea(ls ?? [], id))
    limpiarAvisos()
  }
  /** «+ Añadir línea» de un recuadro: vacía, de ese proveedor; queda seleccionada y a la vista. */
  function anadir(idProv: number) {
    const id = siguienteId(todas)
    setLineas((ls) => [...(ls ?? []), { ...lineaCompraVacia(id), idProv }])
    limpiarAvisos()
    setSeleccionada(id)
    setDesplazarA(id)
  }
  function anadirPrevision(idProv: number) {
    const r = aplicarPrevision(todas, activos, idProv)
    setLineas(r.lineas)
    limpiarAvisos()
    setSinPedido(r.sinPedido)
    setSinListaVistas(sinLista(activos))
  }
  function pasar(id: number, idProv: number) {
    const linea = todas.find((l) => l.id === id)
    const r = pasarA(todas, id, idProv)
    setLineas(r.lineas)
    limpiarAvisos()
    setPlegados((p) => sinClave(p, idProv))
    if (r.repetida && linea !== undefined && linea.idCom !== null) setRepetida(avisoYaEstaba(nombrePieza(linea.idCom), nombreProv(idProv)))
  }
  /** «¿A qué proveedor pides?» y «+ Otro proveedor»: su recuadro, desplegado y a la vista. */
  function anadirProveedor(idProv: number) {
    setAnadidos((a) => (a.includes(idProv) ? a : [...a, idProv]))
    setPlegados((p) => sinClave(p, idProv))
    limpiarAvisos()
    setALaVista(idProv)
  }
  function quitarRecuadro(idProv: number) {
    setLineas((ls) => (ls ?? []).filter((l) => l.idProv !== idProv))
    setAnadidos((a) => a.filter((x) => x !== idProv))
    setQuitando(null)
    limpiarAvisos()
  }
  function pedirQuitar(idProv: number) {
    if (lineasDe(todas, idProv).length > 0) setQuitando(idProv)
    else quitarRecuadro(idProv)
  }
  function plegar(clave: ClaveRecuadro) {
    setPlegados((p) => (p.has(clave) ? sinClave(p, clave) : new Set([...p, clave])))
  }

  async function confirmar() {
    if (lineas === null) return
    const fallo = validarPedido(lineas, recuadros, nombreProv)
    if (fallo !== null) {
      setError(fallo.error)
      setMarcado(fallo.recuadro)
      const clave = fallo.recuadro
      if (clave !== null) {
        setPlegados((p) => sinClave(p, clave))
        setALaVista(clave)
      }
      return
    }
    const origen = precarga.modo === 'solicitudes' ? { urgentes: precarga.urgentes, preventivas: precarga.preventivas } : null
    // Sin la lista de pedidos no se sabe qué bloques hay abiertos: null y que el servidor aplique su regla por defecto.
    const destinos = fijo ? [{ idProv: fijo.idProv, idBloque: fijo.idBloque }] : compras.isSuccess ? destinosDelCuerpo(lineas, bloques, elegidos) : null
    const cuerpo = cuerpoLoteCompras(lineas, origen, activos, destinos)
    setError(null)
    try {
      await guardar.mutateAsync({ cuerpo, clave: claves.para(OPERACION, cuerpo) })
      claves.hecha(OPERACION)
      onCerrar()
    } catch (e) {
      // Un 409 de destino (el bloque ya no existe): los recuadros tienen que ofrecer los bloques de ahora.
      if (e instanceof StaleDataError) void compras.refetch()
      setError(mensajeErrorGuardado(e))
    }
  }

  const bloqueado = guardar.isPending || lineas === null
  const info = [avisoOmitidas(omitidas), avisoSinPedido(sinPedido), avisoSinLista(sinListaVistas), nadaQuePedir ? avisoNadaQuePedir(nombreFijo) : null, repetida].filter((t) => t !== null).join(' ') || null
  const primera = {
    cabecera: 'Componente',
    ancho: 175,
    celda: (l: LineaCompra, n: number) => (
      // Fila seleccionada: el campo navy lleva el borde rgba(255,255,255,0.35) del JavaFX (FC :459-473).
      <div className="rounded-full group-data-[state=selected]:ring-1 group-data-[state=selected]:ring-white/35">
        <CampoAutocompletar valor={l.idCom === null ? null : String(l.idCom)} opciones={opciones} onElegir={(c) => elegir(l.id, Number(c))} placeholder="Escribe componente..." aria-label={`Componente línea ${n}`} />
      </div>
    ),
  }
  /** Tabla de un recuadro; en «Por repartir», con «Pasar a…» en la columna Proveedor. */
  function tabla(suyas: LineaCompra[], conPasarA: boolean) {
    return (
      <TablaLineas<LineaCompra>
        primera={primera}
        proveedor={conPasarA ? (l, n) => (
          <ComboNavy valor={null} opciones={opcionesPasarA(l.idCom, activos, proveedoresActivos)} onChange={(v) => pasar(l.id, Number(v))}
            textoVacio="Pasar a…" ancho={140} visibles={8} aria-label={`Pasar a línea ${n}`} />
        ) : null}
        lineas={suyas}
        proveedores={proveedoresActivos}
        vacio="Añade líneas o «Añadir previsión»."
        seleccionada={seleccionada}
        desplazarA={desplazarA}
        onSeleccionar={seleccionar}
        onCambiar={cambiar}
        onQuitar={quitar}
      />
    )
  }
  function destinoDe(idProv: number) {
    if (fijo) return <DestinoRecuadro nombre={nombreFijo} fijado={fijo.idBloque} />
    return (
      <DestinoRecuadro nombre={nombreProv(idProv)} opciones={opcionesDestino(bloques, idProv)} valor={destinoElegido(bloques, idProv, elegidos)} cargando={compras.isPending}
        onElegir={(idBloque) => { setElegidos((e) => ({ ...e, [idProv]: idBloque })); setError(null) }} />
    )
  }
  const vacio = lineas !== null && !fijo && recuadros.length === 0 && porRepartir.length === 0

  return (
    <DialogoPedidoBase titulo={fijo ? `Añadir líneas · Bloque ${fijo.idBloque}` : 'Nuevo pedido'} nLineas={todas.length} info={info} error={error}
      bloqueado={bloqueado} enviando={guardar.isPending} textoConfirmar={textoConfirmar(nBloques)} onConfirmar={() => void confirmar()} onCerrar={onCerrar}>
      {fijo && (
        <p className="text-[12px] text-azul-gris">
          {nombreFijo}{bloqueFijo ? ` · ${situacion(bloqueFijo)}` : ''} · las líneas nuevas entran como pendientes y el bloque mostrará «Confirmar bloque (n)».
        </p>
      )}
      {/* Alto máximo con scroll propio (C24, 520 px desde los recuadros): con muchas líneas la caja desplaza y "Cancelar"/
          "Confirmar pedido" quedan siempre a la vista. Los popups (autocompletar, combos) van en un portal. */}
      <div className="flex max-h-[520px] min-h-[300px] flex-col gap-2.5 overflow-y-auto">
        {vacio ? (
          <div className="flex flex-col items-center gap-2.5 rounded-[10px] bg-superficie px-4 py-9 text-center">
            <p className="text-[13px] font-bold text-azul-medio">¿A qué proveedor pides?</p>
            <ComboNavy valor={null} opciones={opcionesOtro} onChange={(v) => anadirProveedor(Number(v))} textoVacio="Elige el proveedor…" ancho={280} visibles={8} aria-label="Proveedor" />
            <p className="text-[12px] text-azul-gris">Luego puedes pedir a otros con «+ Otro proveedor»: cada uno va en su bloque.</p>
          </div>
        ) : (
          <>
            {porRepartir.length > 0 && (
              <RecuadroRepartir nLineas={porRepartir.length} plegado={plegados.has(POR_REPARTIR)} onPlegar={() => plegar(POR_REPARTIR)}
                marcado={marcado === POR_REPARTIR} aLaVista={aLaVista === POR_REPARTIR}>
                {tabla(porRepartir, true)}
              </RecuadroRepartir>
            )}
            {recuadros.map((idProv) => {
              const suyas = lineasDe(todas, idProv)
              return (
                <RecuadroProveedor key={idProv} nombre={nombreProv(idProv)} divisa={proveedoresActivos.find((p) => p.idProv === idProv)?.divisa ?? 'EUR'}
                  nLineas={suyas.length} destino={destinoDe(idProv)} nPrevision={cuantasPrevision(activos, idProv, todas)}
                  previsionDeshabilitada={bloqueado || componentes.isFetching} onPrevision={() => anadirPrevision(idProv)}
                  onQuitar={fijo ? undefined : () => pedirQuitar(idProv)} bloqueado={bloqueado} onAnadir={() => anadir(idProv)}
                  avisos={tambienVa(todas, idProv, nombrePieza, nombreProv)} plegado={plegados.has(idProv)} onPlegar={() => plegar(idProv)}
                  marcado={marcado === idProv} aLaVista={aLaVista === idProv}>
                  {tabla(suyas, false)}
                </RecuadroProveedor>
              )
            })}
            {opcionesOtro.length > 0 && lineas !== null && (
              <div>
                <ComboNavy valor={null} opciones={opcionesOtro} onChange={(v) => anadirProveedor(Number(v))} textoVacio="+ Otro proveedor" ancho={260} visibles={8} aria-label="Otro proveedor" />
              </div>
            )}
          </>
        )}
      </div>
      <ConfirmDialog abierto={quitando !== null} titulo="Quitar proveedor"
        descripcion={quitando === null ? '' : textoQuitarRecuadro(nombreProv(quitando), lineasDe(todas, quitando).length)}
        textoAccion="Quitar" onConfirmar={() => { if (quitando !== null) quitarRecuadro(quitando) }} onCancelar={() => setQuitando(null)} />
    </DialogoPedidoBase>
  )
}
```

- [ ] **Step 4: Tests del diálogo (`NuevoPedidoDialog.test.tsx`, reescrito entero)**

```tsx
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'
import type { Componente } from '@/shared/api/client'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { crearQueryClient } from '@/shared/api/queryClient'
import { avisaAlSalir } from '@/test/avisoAlSalir'
import { renderConRouter, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { CLAVE_PROVEEDORES } from '../../proveedores/api'
import { CLAVE_COMPONENTES_GESTIONADOS } from '../../stock/api'
import { COMPONENTES, PROVEEDORES, preventiva, urgente } from './datosPrueba'
import { NuevoPedidoDialog } from './NuevoPedidoDialog'

/** 0.9.9 (varios proveedores): lcd-x-negro, bat-x y su slave bat-y solo los vende ACME, así que lo precargado va solo a su
 *  recuadro. «Por repartir» tiene sus tests al final. */
const DE_ACME: Componente[] = COMPONENTES.map((c) => (c.idCom === 4 ? c : { ...c, proveedores: [1] }))

let lotes: { cuerpo: unknown; clave: string | null }[]
let respuestaLote: () => Response
let tasasPedidas: string[]

beforeEach(() => {
  lotes = []
  tasasPedidas = []
  respuestaLote = () => HttpResponse.json({ idsCreados: [101] })
  server.use(
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(DE_ACME)),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', ({ params }) => { tasasPedidas.push(String(params.divisa)); return HttpResponse.json({ value: 1.1367 }) }),
    http.post('*/api/compras/lote', async ({ request }) => {
      lotes.push({ cuerpo: await request.json(), clave: request.headers.get('Idempotency-Key') })
      return respuestaLote()
    }),
  )
})

function abrir(precarga: PrecargaPedido = { modo: 'vacio' }) {
  const onCerrar = vi.fn()
  renderConRouter([{ path: '/', element: <NuevoPedidoDialog precarga={precarga} onCerrar={onCerrar} /> }], { sesion: SESION_SUPER })
  return onCerrar
}

/** Por etiqueta y no por rol: si un aviso global abre su diálogo encima, el formulario queda aria-hidden y los *ByRole
 *  dejarían de verlo. Con un solo recuadro, la etiqueta es única. */
const filaDe = (n: number) => screen.getByLabelText(`Cantidad línea ${n}`).closest('tr') as HTMLElement
const confirmar = () => userEvent.click(screen.getByRole('button', { name: /^Confirmar pedido/ }))
/** Un recuadro del pedido (0.9.9): el de un proveedor o «Por repartir». */
const recuadro = (nombre: string) => screen.getByRole('region', { name: nombre === 'Por repartir' ? nombre : `Pedido a ${nombre}` })

async function elegirComponente(n: number, texto: string, opcion: string, donde?: HTMLElement) {
  await userEvent.type(within(donde ?? document.body).getByRole('combobox', { name: `Componente línea ${n}` }), texto)
  await userEvent.click(await screen.findByRole('option', { name: opcion }))
}

/** ComboNavy: disparador role="combobox" con aria-label; lista role="listbox"; cada opción, un <button> dentro de un
 *  <li role="option">. «Proveedor» (al abrir vacío) y «Otro proveedor» enseñan «{nombre} · {bloque abierto}». */
async function elegirEnCombo(combo: string, opcion: string | RegExp) {
  await userEvent.click(screen.getByRole('combobox', { name: combo }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: combo })).findByRole('button', { name: opcion }))
}
const elegirProveedor = (nombre: string) => elegirEnCombo('Proveedor', new RegExp(`^${nombre} · `))
const otroProveedor = (nombre: string) => elegirEnCombo('Otro proveedor', new RegExp(`^${nombre} · `))

async function escribir(etiqueta: string, valor: string, donde?: HTMLElement) {
  const campo = within(donde ?? document.body).getByRole('textbox', { name: etiqueta })
  await userEvent.clear(campo)
  await userEvent.type(campo, valor)
}

describe('NuevoPedidoDialog', () => {
  it('Atrás con líneas pide "Descartar": Cancelar se queda con las líneas intactas y Descartar cierra y sale', async () => {
    const onCerrar = vi.fn()
    const { router } = renderConRouter(
      [{ path: '/lista', element: <p>lista</p> }, { path: '/pedido', element: <NuevoPedidoDialog precarga={{ modo: 'vacio' }} onCerrar={onCerrar} /> }],
      { sesion: SESION_SUPER, ruta: '/lista' },
    )
    await act(async () => { await router.navigate('/pedido') })
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await act(async () => { await router.navigate(-1) })
    const confirmacion = await screen.findByRole('dialog', { name: 'Descartar' })
    expect(confirmacion).toHaveTextContent('Se descartará la línea del pedido.')
    // Mismo orden y aspecto que "Descartar" de "Asignar trabajos" (ConfirmDialog): la acción encima de "Cancelar".
    expect(within(confirmacion).getAllByRole('button').map((b) => b.textContent)).toEqual(['Descartar', 'Cancelar', 'Close'])
    expect(router.state.location.pathname).toBe('/pedido')
    await userEvent.click(within(confirmacion).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Descartar' })).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe('/pedido')
    expect(screen.getByLabelText('Cantidad línea 1')).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await act(async () => { await router.navigate(-1) })
    const otra = await screen.findByRole('dialog', { name: 'Descartar' })
    expect(otra).toHaveTextContent('Se descartarán las 2 líneas del pedido.')
    await userEvent.click(within(otra).getByRole('button', { name: 'Descartar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(router.state.location.pathname).toBe('/lista'))
  })
  it('Atrás sin líneas sale sin preguntar', async () => {
    const { router } = renderConRouter(
      [{ path: '/lista', element: <p>lista</p> }, { path: '/pedido', element: <NuevoPedidoDialog precarga={{ modo: 'vacio' }} onCerrar={vi.fn()} /> }],
      { sesion: SESION_SUPER, ruta: '/lista' },
    )
    await act(async () => { await router.navigate('/pedido') })
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await act(async () => { await router.navigate(-1) })
    expect(router.state.location.pathname).toBe('/lista')
    expect(screen.queryByRole('dialog', { name: 'Descartar' })).not.toBeInTheDocument()
  })

  it('aviso al salir (F5, cerrar la pestaña): sin líneas no pregunta; con una línea, sí; al quitarla, ya no', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    expect(avisaAlSalir()).toBe(false)
    await elegirProveedor('ACME')
    expect(avisaAlSalir()).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    expect(avisaAlSalir()).toBe(true)
    await userEvent.click(screen.getByRole('button', { name: 'Quitar línea 1' }))
    expect(avisaAlSalir()).toBe(false)
  })

  it('vacío: título de 24 px y «¿A qué proveedor pides?» con los proveedores activos; al elegir uno, su recuadro con seis columnas', async () => {
    abrir()
    const dlg = within(await screen.findByRole('dialog', { name: 'Nuevo pedido' }))
    expect(dlg.getByRole('heading', { name: 'Nuevo pedido' })).toHaveClass('text-2xl', 'font-bold', 'text-azul-medio')
    expect(screen.getByRole('dialog')).toHaveClass('w-[880px]', 'bg-fondo-vista', 'p-7')
    expect(dlg.getByText('¿A qué proveedor pides?')).toBeInTheDocument()
    expect(dlg.getByText('Luego puedes pedir a otros con «+ Otro proveedor»: cada uno va en su bloque.')).toBeInTheDocument()
    // La ✕ de DialogContent ("Close", sr-only) va la última.
    expect(dlg.getAllByRole('button').map((b) => b.textContent)).toEqual(['Cancelar', 'Confirmar pedido', 'Close'])
    await userEvent.click(dlg.getByRole('combobox', { name: 'Proveedor' }))
    const lista = screen.getByRole('listbox', { name: 'Proveedor' })
    await within(lista).findByRole('button', { name: /^ACME/ })
    expect(within(lista).getAllByRole('button').map((b) => b.textContent)).toEqual(['ACME · sin bloque abierto', 'Proveedor B · sin bloque abierto'])
    await userEvent.click(within(lista).getByRole('button', { name: /^ACME/ }))
    const acme = within(recuadro('ACME'))
    expect(acme.getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Componente', 'Cant.', 'P.Unit.', 'Urg.', 'Total EUR', ''])
    expect(acme.getByText('Añade líneas o «Añadir previsión».')).toBeInTheDocument()
    expect(screen.queryByText('¿A qué proveedor pides?')).not.toBeInTheDocument()
  })

  it('sin líneas: "Añade al menos una línea." y no envía nada', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar pedido' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Añade al menos una línea.')
    expect(screen.getByRole('alert')).toHaveClass('text-[11px]', 'text-texto-error')
    expect(lotes).toHaveLength(0)
  })

  it('"Pedir" (un componente): va al recuadro de ACME, el único que la vende, con cantidad 1, precio 0,00 y sin urgente; "+ Añadir línea" añade una vacía en ese recuadro y la selecciona', async () => {
    abrir({ modo: 'componentes', idsCom: [2] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x / bat-y')
    expect(within(recuadro('ACME')).getByRole('combobox', { name: 'Componente línea 1' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Por repartir' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Proveedor línea 1' })).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 1' })).toHaveValue('1')
    expect(screen.getByRole('textbox', { name: 'Precio línea 1' })).toHaveValue('0,00')
    expect(screen.getByRole('checkbox', { name: 'Urgente línea 1' })).not.toBeChecked()
    expect(within(filaDe(1)).getByText('0,00 €')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    expect(screen.getByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 2' })).toHaveValue('1')
    expect(screen.getByRole('textbox', { name: 'Precio línea 2' })).toHaveValue('0,00')
    expect(screen.getByRole('checkbox', { name: 'Urgente línea 2' })).not.toBeChecked()
    expect(screen.getByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x / bat-y')
    expect(filaDe(2)).toHaveAttribute('data-state', 'selected')
    expect(filaDe(1)).not.toHaveAttribute('data-state')
  })

  it('"Pedir" con un componente precargado: no autoenfoca el campo Componente (C25, se pisaría la precarga al teclear)', async () => {
    // Cachés ya calientes (como al venir de Stock): la línea precargada existe desde el primer render del diálogo, que es
    // cuando Radix decide a qué enfocar al abrirse.
    const qc = crearQueryClient({ retry: false })
    qc.setQueryData(CLAVE_COMPONENTES_GESTIONADOS, DE_ACME)
    qc.setQueryData(CLAVE_PROVEEDORES, PROVEEDORES)
    const onCerrar = vi.fn()
    renderConRouter([{ path: '/', element: <NuevoPedidoDialog precarga={{ modo: 'componentes', idsCom: [2] }} onCerrar={onCerrar} /> }], { sesion: SESION_SUPER, queryClient: qc })
    const campo = await screen.findByRole('combobox', { name: 'Componente línea 1' })
    expect(campo).toHaveValue('bat-x / bat-y')
    expect(document.activeElement).not.toBe(campo)
  })

  it('componente desactivado o desconocido: la línea va vacía (calco), con su placeholder, a «Por repartir»', async () => {
    abrir({ modo: 'componentes', idsCom: [4, 99] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('')
    const primera = screen.getByRole('combobox', { name: 'Componente línea 1' })
    expect(primera).toHaveValue('')
    expect(primera).toHaveAttribute('placeholder', 'Escribe componente...')
    expect(within(recuadro('Por repartir')).getByRole('combobox', { name: 'Componente línea 1' })).toBe(primera)
  })

  it('"Pedir todas las piezas" (N componentes): una línea por componente en orden; "+ Añadir línea" añade una vacía', async () => {
    abrir({ modo: 'componentes', idsCom: [1, 2] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('lcd-x-negro')
    expect(screen.getByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('bat-x / bat-y')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 2' })).toHaveValue('1')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    expect(screen.getByRole('combobox', { name: 'Componente línea 3' })).toHaveValue('')
  })

  it('"Pedir piezas": agrupa por componente con urgentes primero, cantidad = nº de solicitudes, y avisa de las omitidas', async () => {
    abrir({ modo: 'solicitudes', urgentes: [urgente(10, 2), urgente(11, 4), urgente(12, 1)], preventivas: [preventiva(20, 2), preventiva(21, 4)] })
    expect(await screen.findByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x / bat-y')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 1' })).toHaveValue('2')
    expect(screen.getByRole('combobox', { name: 'Componente línea 2' })).toHaveValue('lcd-x-negro')
    expect(screen.getByRole('textbox', { name: 'Cantidad línea 2' })).toHaveValue('1')
    expect(screen.queryByRole('combobox', { name: 'Componente línea 3' })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('2 solicitud(es) de componentes desactivados no se han añadido y siguen pendientes.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('si falla la carga de componentes (500), no hay aviso de omitidas y "Cancelar" cierra el diálogo', async () => {
    server.use(http.get('*/api/componentes/gestionados', () => new HttpResponse(null, { status: 500 })))
    const onCerrar = abrir({ modo: 'solicitudes', urgentes: [urgente(10, 2), urgente(11, 4)], preventivas: [] })
    // El error de la consulta abre el diálogo global (queryClient.test.tsx): se cierra antes de seguir con el formulario.
    await userEvent.click(await screen.findByRole('button', { name: 'Aceptar' }))
    expect(screen.queryByText(/solicitud\(es\) de componentes desactivados/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
  })

  it('autocompletar: solo activos (una opción por grupo de stock compartido), filtro "contiene" y Enter elige el primero', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    const campo = screen.getByRole('combobox', { name: 'Componente línea 1' })
    await userEvent.type(campo, 'x')
    await waitFor(() => expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['lcd-x-negro', 'bat-x / bat-y']))
    await userEvent.clear(campo)
    await userEvent.type(campo, 'bat')
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['bat-x / bat-y'])
    expect(screen.queryByRole('option', { name: 'bat-y' })).not.toBeInTheDocument()
    await userEvent.type(campo, '{Enter}')
    expect(campo).toHaveValue('bat-x / bat-y')
  })

  it('con muchas líneas: la caja de los recuadros tiene alto máximo con scroll propio y los botones siguen visibles (C24)', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    const boton = screen.getByRole('button', { name: '+ Añadir línea' })
    for (let i = 0; i < 8; i += 1) await userEvent.click(boton)
    expect(recuadro('ACME').parentElement).toHaveClass('max-h-[520px]', 'overflow-y-auto')
    expect(screen.getByRole('button', { name: 'Confirmar pedido' })).toBeInTheDocument()
  })

  it('validación con el proveedor delante, en orden y parando en el primer fallo; marca el recuadro y cambiar una línea lo borra', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('ACME · Línea 1: selecciona un componente.')
    expect(recuadro('ACME')).toHaveAttribute('data-marcado', 'true')
    await elegirComponente(1, 'bat', 'bat-x / bat-y')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(recuadro('ACME')).not.toHaveAttribute('data-marcado')
    await escribir('Cantidad línea 1', '0')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('ACME · Línea 1: la cantidad debe ser mayor que 0.')
    await escribir('Cantidad línea 1', '2')
    await escribir('Precio línea 1', '-1')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('ACME · Línea 1: el precio no puede ser negativo.')
    expect(lotes).toHaveLength(0)
  })

  it('proveedor en USD: símbolo $ y Total EUR = precio / tasa × cantidad; en el recuadro en EUR, € sin pedir tasa', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('Proveedor B')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'bat', 'bat-x / bat-y')
    await escribir('Precio línea 1', '10')
    await escribir('Cantidad línea 1', '2')
    expect(within(filaDe(1)).getByText('$')).toBeInTheDocument()
    expect(await within(filaDe(1)).findByText('17,60 €')).toBeInTheDocument()
    await otroProveedor('ACME')
    const acme = recuadro('ACME')
    await userEvent.click(within(acme).getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'lcd', 'lcd-x-negro', acme)
    await escribir('Precio línea 1', '10', acme)
    await escribir('Cantidad línea 1', '2', acme)
    const filaAcme = within(acme).getByLabelText('Cantidad línea 1').closest('tr') as HTMLElement
    expect(within(filaAcme).getByText('€')).toBeInTheDocument()
    expect(within(filaAcme).getByText('20,00 €')).toBeInTheDocument()
    expect(tasasPedidas).toEqual(['USD'])
  })

  it('mientras llega la tasa el Total EUR es "—" (P7)', async () => {
    server.use(http.get('*/api/tipo-cambio/:divisa', async () => { await delay('infinite'); return HttpResponse.json({ value: 1.1367 }) }))
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('Proveedor B')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'bat', 'bat-x / bat-y')
    expect(within(filaDe(1)).getByText('—')).toBeInTheDocument()
  })

  it('si la tasa falla, el Total EUR se queda en "—" (nunca calcula como si fuera EUR)', async () => {
    let pedidas = 0
    server.use(http.get('*/api/tipo-cambio/:divisa', () => {
      pedidas += 1
      return HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 })
    }))
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('Proveedor B')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'bat', 'bat-x / bat-y')
    await waitFor(() => expect(pedidas).toBe(1))
    expect(within(filaDe(1)).getByText('—')).toBeInTheDocument()
    expect(within(filaDe(1)).queryByText('0,00 €')).not.toBeInTheDocument()
  })

  it('"Pedir piezas" → un lote con clave y el cuerpo exacto (solo las solicitudes que siguen teniendo línea); cierra', async () => {
    const onCerrar = abrir({ modo: 'solicitudes', urgentes: [urgente(10, 2), urgente(11, 1)], preventivas: [preventiva(20, 2)] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('button', { name: 'Quitar línea 2' }))
    expect(screen.queryByRole('combobox', { name: 'Componente línea 2' })).not.toBeInTheDocument()
    await escribir('Precio línea 1', '12,5')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Urgente línea 1' }))
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes).toHaveLength(1)
    expect(lotes[0].clave).toMatch(/^[0-9a-f-]{36}$/)
    expect(lotes[0].cuerpo).toEqual({
      lineas: [{ idCom: 2, idProv: 1, cantidad: 2, esUrgente: true, precioUnidad: 12.5 }],
      solicitudes: { urgentes: [10], preventivas: [20] },
      destinos: null,
    })
  })

  it('"Pedir piezas" con solicitudes de un slave (bat-y, id 5): su id viaja en el lote junto a la línea de su master', async () => {
    abrir({ modo: 'solicitudes', urgentes: [urgente(10, 5), urgente(11, 1)], preventivas: [preventiva(20, 5)] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('button', { name: 'Quitar línea 2' }))
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
    expect(lotes[0].cuerpo).toMatchObject({
      lineas: [{ idCom: 2, cantidad: 2 }],
      solicitudes: { urgentes: [10], preventivas: [20] },
    })
  })

  it('422: su mensaje en la línea de error con el formulario abierto; el reintento usa la MISMA clave', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'Línea 1: el proveedor está desactivado.' }, { status: 422 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('Línea 1: el proveedor está desactivado.')
    expect(screen.getByRole('dialog', { name: 'Nuevo pedido' })).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
    respuestaLote = () => HttpResponse.json({ idsCreados: [101] })
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes).toHaveLength(2)
    expect(lotes[1].clave).toBeTruthy()
    expect(lotes[1].clave).toBe(lotes[0].clave)
  })

  it('otro error (403): "Error al guardar: …" en la línea de error, formulario abierto', async () => {
    respuestaLote = () => new HttpResponse(null, { status: 403 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('Error al guardar: No tienes permisos para realizar esta acción.')
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('503 del tipo de cambio al guardar: el mensaje del servidor inline, sin aviso global y con el formulario abierto', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.' }, { status: 503 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await confirmar()
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo.')
    expect(screen.queryByText('Sin conexión con el servidor: HTTP 503')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad línea 1')).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('500 sin mensaje al guardar: aviso global de conexión, sin línea de error y con el formulario abierto', async () => {
    respuestaLote = () => new HttpResponse(null, { status: 500 })
    const onCerrar = abrir({ modo: 'componentes', idsCom: [2] })
    await screen.findByRole('combobox', { name: 'Componente línea 1' })
    await confirmar()
    expect(await screen.findByText('Sin conexión con el servidor: HTTP 500')).toBeInTheDocument()
    expect(screen.queryByText(/^Error al guardar/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad línea 1')).toBeInTheDocument()
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('"Cancelar" cierra sin preguntar aunque haya líneas (calco) y no envía nada', async () => {
    const onCerrar = abrir({ modo: 'componentes', idsCom: [1, 2] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(lotes).toHaveLength(0)
  })

  it('enfocar cualquier campo selecciona su fila (navy); la papelera quita la línea sin confirmar', async () => {
    abrir({ modo: 'componentes', idsCom: [1, 2] })
    await screen.findByRole('combobox', { name: 'Componente línea 2' })
    await userEvent.click(screen.getByRole('textbox', { name: 'Cantidad línea 2' }))
    expect(filaDe(2)).toHaveAttribute('data-state', 'selected')
    expect(filaDe(2)).toHaveClass('data-[state=selected]:bg-azul-medio', 'data-[state=selected]:text-crema')
    expect(filaDe(1)).not.toHaveAttribute('data-state')
    await userEvent.click(screen.getByRole('combobox', { name: 'Componente línea 1' }))
    expect(filaDe(1)).toHaveAttribute('data-state', 'selected')
    const papelera = screen.getByRole('button', { name: 'Quitar línea 1' })
    expect(papelera.querySelector('img')).toHaveAttribute('src', '/borrar.png')
    await userEvent.click(papelera)
    expect(screen.getByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('bat-x / bat-y')
    expect(screen.queryByRole('combobox', { name: 'Componente línea 2' })).not.toBeInTheDocument()
  })
})

describe('pedido automático por proveedor (spec 0.9.6 §4.4 y 2026-10-11 §5.2)', () => {
  // lcd-x-negro (1) marcada, de ACME, pide 16; bat-x (2) marcada, de ACME, sin pedido; flex-x (6) marcada, pide 3 y no
  // está en ninguna lista.
  const CON_PREVISION: Componente[] = [
    ...COMPONENTES.map((c) =>
      c.idCom === 1 ? { ...c, autoPedido: true, pedir60: 16, proveedores: [1] }
        : c.idCom === 2 ? { ...c, autoPedido: true, pedir60: 0, proveedores: [1] } : { ...c, proveedores: [] }),
    { ...COMPONENTES[0], idCom: 6, tipo: 'flex-x', autoPedido: true, pedir60: 3, proveedores: [] },
  ]

  beforeEach(() => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json(CON_PREVISION)))
  })

  it('cada recuadro lleva su «Añadir previsión (n)»; sin nada que pedir, deshabilitado', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('Proveedor B')
    expect(await within(recuadro('Proveedor B')).findByRole('button', { name: 'Añadir previsión (0)' })).toBeDisabled()
    await otroProveedor('ACME')
    expect(await within(recuadro('ACME')).findByRole('button', { name: 'Añadir previsión (1)' })).toBeEnabled()
  })

  it('«Añadir previsión (1)» de ACME mete la suya con su cantidad, y avisa de la que no necesita pedido y de la que no tiene lista', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(await within(recuadro('ACME')).findByRole('button', { name: 'Añadir previsión (1)' }))
    expect(within(recuadro('ACME')).getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('1 pieza marcada no necesita pedido.')
    expect(screen.getByRole('status')).toHaveTextContent('1 pieza en Auto con algo que pedir no está en la lista de ningún proveedor.')
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
  })

  it('con la pieza ya en el pedido sube la cantidad y no la duplica', async () => {
    abrir({ modo: 'componentes', idsCom: [1] })
    // 2026-10-13: «Pedir» ya entra con su «Pedir 60 d»; se baja a mano para ver que la previsión la vuelve a subir.
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16'))
    await escribir('Cantidad línea 1', '2')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión (1)' }))
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
  })

  it('elegir una pieza con previsión en una línea pone su «Pedir 60 d» (2026-10-13)', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'lcd', 'lcd-x-negro')
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
  })

  it('una pieza que ya está en el recuadro no se repite: queda seleccionada, y a la vista, la que estaba y sale el aviso', async () => {
    const desplazar = vi.spyOn(Element.prototype, 'scrollIntoView')
    onTestFinished(() => desplazar.mockRestore())
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(await screen.findByRole('button', { name: 'Añadir previsión (1)' }))
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(2, 'lcd', 'lcd-x-negro')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(filaDe(1)).toHaveAttribute('data-state', 'selected')
    expect(desplazar.mock.contexts).toContain(filaDe(1))
    expect(screen.getByRole('status')).toHaveTextContent('lcd-x-negro ya está en el pedido a ACME (línea 1).')
  })

  it('el aviso de pieza repetida se borra con «Añadir previsión» y al añadir otro proveedor', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(await screen.findByRole('button', { name: 'Añadir previsión (1)' }))
    async function repetirLaPieza() {
      await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
      await elegirComponente(2, 'lcd', 'lcd-x-negro')
      expect(screen.getByRole('status')).toHaveTextContent('lcd-x-negro ya está en el pedido a ACME (línea 1).')
    }
    await repetirLaPieza()
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión (1)' }))
    expect(screen.queryByText(/ya está en el pedido/)).not.toBeInTheDocument()
    await repetirLaPieza()
    await otroProveedor('Proveedor B')
    expect(screen.queryByText(/ya está en el pedido/)).not.toBeInTheDocument()
  })

  it('«Añadir previsión…» de un bloque abre con la previsión de su proveedor ya puesta', async () => {
    abrir({ modo: 'bloque', idBloque: 41, idProv: 1, prevision: true })
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16'))
    expect(screen.getByRole('dialog', { name: 'Añadir líneas · Bloque 41' })).toBeInTheDocument()
  })

  it('si el proveedor del bloque no tiene nada que pedir, no añade líneas y lo dice', async () => {
    abrir({ modo: 'bloque', idBloque: 41, idProv: 2, prevision: true })
    expect(await screen.findByRole('status')).toHaveTextContent('Nada que pedir a Proveedor B según la previsión.')
    expect(screen.queryByLabelText('Cantidad línea 1')).not.toBeInTheDocument()
  })

  it('«Añadir previsión…» de un bloque con la caché de Stock vieja espera a la lista fresca y no aplica números viejos', async () => {
    const qc = crearQueryClient({ retry: false })
    qc.setQueryData(CLAVE_COMPONENTES_GESTIONADOS, CON_PREVISION)
    server.use(http.get('*/api/componentes/gestionados', async () => {
      await delay(50)
      return HttpResponse.json(CON_PREVISION.map((c) => (c.idCom === 1 ? { ...c, pedir60: 0 } : c)))
    }))
    renderConRouter([{ path: '/', element: <NuevoPedidoDialog precarga={{ modo: 'bloque', idBloque: 41, idProv: 1, prevision: true }} onCerrar={vi.fn()} /> }], { sesion: SESION_SUPER, queryClient: qc })
    expect(await screen.findByRole('status')).toHaveTextContent('Nada que pedir a ACME según la previsión.')
    expect(screen.queryByLabelText('Cantidad línea 1')).not.toBeInTheDocument()
  })

  it('«Pedir» con la caché de Stock vieja entra con 1 y pone el «Pedir 60 d» de la lista fresca, nunca el viejo (2026-10-13)', async () => {
    const qc = crearQueryClient({ retry: false })
    qc.setQueryData(CLAVE_COMPONENTES_GESTIONADOS, CON_PREVISION.map((c) => (c.idCom === 1 ? { ...c, pedir60: 5 } : c)))
    server.use(http.get('*/api/componentes/gestionados', async () => {
      await delay(50)
      return HttpResponse.json(CON_PREVISION)
    }))
    renderConRouter([{ path: '/', element: <NuevoPedidoDialog precarga={{ modo: 'componentes', idsCom: [1] }} onCerrar={vi.fn()} /> }], { sesion: SESION_SUPER, queryClient: qc })
    // C25: con la caché caliente la línea sale ya, pero sin la previsión de la caché.
    expect(await screen.findByLabelText('Cantidad línea 1')).not.toHaveValue('5')
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16'))
  })

  it('«Añadir previsión…» de un bloque con la lista en 500 no añade líneas ni avisos de previsión', async () => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json({ message: 'boom' }, { status: 500 })))
    abrir({ modo: 'bloque', idBloque: 41, idProv: 1, prevision: true })
    await screen.findByRole('dialog', { name: 'Añadir líneas · Bloque 41' })
    await new Promise((r) => setTimeout(r, 200))
    expect(screen.queryByLabelText('Cantidad línea 1')).not.toBeInTheDocument()
    expect(screen.queryByText(/según la previsión|no necesita pedido|no está en la lista/)).not.toBeInTheDocument()
  })

  it('el aviso de piezas sin pedido se borra al editar las líneas', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(await screen.findByRole('button', { name: 'Añadir previsión (1)' }))
    expect(screen.getByRole('status')).toHaveTextContent('1 pieza marcada no necesita pedido.')
    await escribir('Cantidad línea 1', '5')
    expect(screen.queryByText('1 pieza marcada no necesita pedido.')).not.toBeInTheDocument()
  })
})

describe('varios proveedores en un mismo pedido (0.9.9, spec 2026-10-13)', () => {
  it('un recuadro por proveedor, por nombre: «Confirmar pedido (2 bloques)» manda las líneas de los dos', async () => {
    const onCerrar = abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('Proveedor B')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'bat', 'bat-x / bat-y')
    await otroProveedor('ACME')
    expect(screen.getAllByRole('region').map((r) => r.getAttribute('aria-label'))).toEqual(['Pedido a ACME', 'Pedido a Proveedor B'])
    const acme = recuadro('ACME')
    await userEvent.click(within(acme).getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'lcd', 'lcd-x-negro', acme)
    expect(screen.getByRole('button', { name: 'Confirmar pedido (2 bloques)' })).toBeInTheDocument()
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(lotes[0].cuerpo).toEqual({
      lineas: [
        { idCom: 2, idProv: 2, cantidad: 1, esUrgente: false, precioUnidad: 0 },
        { idCom: 1, idProv: 1, cantidad: 1, esUrgente: false, precioUnidad: 0 },
      ],
      solicitudes: { urgentes: [], preventivas: [] },
      destinos: null,
    })
  })

  it('lo que falta: con 3 en ACME, en Proveedor B la misma pieza sale con 7 − 3 = 4, y cada recuadro avisa del otro', async () => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json(DE_ACME.map((c) => (c.idCom === 1 ? { ...c, pedir60: 7, proveedores: [1, 2] } : c)))))
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'lcd', 'lcd-x-negro')
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('7')
    await escribir('Cantidad línea 1', '3')
    await otroProveedor('Proveedor B')
    const b = recuadro('Proveedor B')
    await userEvent.click(within(b).getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'lcd', 'lcd-x-negro', b)
    expect(within(b).getByLabelText('Cantidad línea 1')).toHaveValue('4')
    expect(recuadro('ACME')).toHaveTextContent('lcd-x-negro también va a Proveedor B (4).')
    expect(b).toHaveTextContent('lcd-x-negro también va a ACME (3).')
  })

  it('«Por repartir»: lo precargado que venden varios (o ninguno) espera ahí; «Pasar a…» con «Lo vende» primero', async () => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json(DE_ACME.map((c) => (c.idCom === 1 ? { ...c, proveedores: [1, 2] } : c.idCom === 2 || c.idCom === 5 ? { ...c, proveedores: [] } : c)))))
    abrir({ modo: 'componentes', idsCom: [1, 2] })
    const repartir = await screen.findByRole('region', { name: 'Por repartir' })
    expect(repartir).toHaveTextContent('⚠ Por repartir')
    expect(repartir).toHaveTextContent('2 líneas')
    await userEvent.click(within(repartir).getByRole('combobox', { name: 'Pasar a línea 1' }))
    const lista = screen.getByRole('listbox', { name: 'Pasar a línea 1' })
    expect(lista).toHaveTextContent(/Lo vende.*ACME.*Proveedor B/)
    expect(within(lista).queryByText('Otros proveedores')).not.toBeInTheDocument()
    await userEvent.click(within(lista).getByRole('button', { name: 'Proveedor B' }))
    expect(within(recuadro('Proveedor B')).getByRole('combobox', { name: 'Componente línea 1' })).toHaveValue('lcd-x-negro')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Queda 1 línea por repartir: elige a quién se la pides.')
    expect(recuadro('Por repartir')).toHaveAttribute('data-marcado', 'true')
    await userEvent.click(within(recuadro('Por repartir')).getByRole('combobox', { name: 'Pasar a línea 1' }))
    const otra = screen.getByRole('listbox', { name: 'Pasar a línea 1' })
    expect(within(otra).getByText('Otros proveedores')).toBeInTheDocument()
    expect(within(otra).queryByText('Lo vende')).not.toBeInTheDocument()
    await userEvent.click(within(otra).getByRole('button', { name: 'ACME' }))
    expect(screen.queryByRole('region', { name: 'Por repartir' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar pedido (2 bloques)' })).toBeInTheDocument()
  })

  it('la × quita el recuadro: sin líneas, sin preguntar; con líneas, pregunta antes', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar ACME' }))
    expect(screen.queryByRole('region', { name: 'Pedido a ACME' })).not.toBeInTheDocument()
    expect(screen.getByText('¿A qué proveedor pides?')).toBeInTheDocument()
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await userEvent.click(screen.getByRole('button', { name: 'Quitar ACME' }))
    const pregunta = await screen.findByRole('dialog', { name: 'Quitar proveedor' })
    expect(pregunta).toHaveTextContent('¿Quitar ACME y su línea?')
    await userEvent.click(within(pregunta).getByRole('button', { name: 'Quitar' }))
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Pedido a ACME' })).not.toBeInTheDocument())
  })

  it('plegar esconde las líneas; el error de «Confirmar» despliega el recuadro, lo marca y lo trae a la vista', async () => {
    const desplazar = vi.spyOn(Element.prototype, 'scrollIntoView')
    onTestFinished(() => desplazar.mockRestore())
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await userEvent.click(screen.getByRole('button', { name: 'Plegar ACME' }))
    expect(screen.queryByLabelText('Cantidad línea 1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Desplegar ACME' })).toHaveAttribute('aria-expanded', 'false')
    desplazar.mockClear()
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('ACME · Línea 1: selecciona un componente.')
    expect(screen.getByLabelText('Cantidad línea 1')).toBeInTheDocument()
    expect(recuadro('ACME')).toHaveAttribute('data-marcado', 'true')
    expect(desplazar.mock.contexts).toContain(recuadro('ACME'))
  })
})
```

- [ ] **Step 5: `NuevoPedidoDialog.bloques.test.tsx` (reescrito entero)**

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompraComponente } from '@/shared/api/client'
import type { PrecargaPedido } from '@/shared/lib/formularioPedido'
import { renderConRouter, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { lineaDePrueba as l } from '../bloques/datosPrueba'
import { COMPONENTES, PROVEEDORES } from './datosPrueba'
import { NuevoPedidoDialog } from './NuevoPedidoDialog'

/** Bloque 41 de ACME (proveedor 1), abierto. */
const ABIERTO_ACME: CompraComponente[] = [l({ idCompra: 9, idProv: 1, nombreProveedor: 'ACME', idBloque: 41 })]
let lotes: unknown[]
let compras: CompraComponente[]
let lecturas: number
let respuestaLote: () => Response

beforeEach(() => {
  lotes = []
  compras = ABIERTO_ACME
  lecturas = 0
  respuestaLote = () => HttpResponse.json({ idsCreados: [101] })
  server.use(
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(COMPONENTES)),
    http.get('*/api/proveedores', () => HttpResponse.json(PROVEEDORES)),
    http.get('*/api/tipo-cambio/:divisa', () => HttpResponse.json({ value: 1.1367 })),
    http.get('*/api/compras', () => { lecturas += 1; return HttpResponse.json(compras) }),
    http.post('*/api/compras/lote', async ({ request }) => { lotes.push(await request.json()); return respuestaLote() }),
  )
})

function abrir(precarga: PrecargaPedido = { modo: 'vacio' }) {
  const onCerrar = vi.fn()
  renderConRouter([{ path: '/', element: <NuevoPedidoDialog precarga={precarga} onCerrar={onCerrar} /> }], { sesion: SESION_SUPER })
  return onCerrar
}

/** «¿A qué proveedor pides?» (0.9.9, varios proveedores): ComboNavy «Proveedor»; cada opción, «{nombre} · {bloque abierto}». */
async function elegirProveedor(nombre: string) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Proveedor' }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: 'Proveedor' })).findByRole('button', { name: new RegExp(`^${nombre} · `) }))
}

async function anadirLinea(n: number) {
  await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
  await userEvent.type(screen.getByRole('combobox', { name: `Componente línea ${n}` }), 'lcd')
  await userEvent.click(await screen.findByRole('option', { name: 'lcd-x-negro' }))
}
const recuadro = (nombre: string) => screen.getByRole('region', { name: `Pedido a ${nombre}` })
const confirmar = () => userEvent.click(screen.getByRole('button', { name: /^Confirmar pedido/ }))

describe('«Nuevo pedido» con bloques (0.9.9)', () => {
  it('al elegir el proveedor se ve si tiene un bloque abierto, y su recuadro ya dice a cuál irá', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await userEvent.click(screen.getByRole('combobox', { name: 'Proveedor' }))
    const opcion = await within(screen.getByRole('listbox', { name: 'Proveedor' })).findByRole('button', { name: 'ACME · Bloque 41 abierto' })
    await userEvent.click(opcion)
    expect(await within(recuadro('ACME')).findByRole('combobox', { name: 'Bloque de ACME' })).toHaveTextContent('Añadir a Bloque 41')
  })

  it('un proveedor con bloque abierto se suma a él por defecto y viaja en destinos', async () => {
    const onCerrar = abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await anadirLinea(1)
    expect(await within(recuadro('ACME')).findByRole('combobox', { name: 'Bloque de ACME' })).toHaveTextContent('Añadir a Bloque 41')
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(lotes[0]).toMatchObject({ destinos: [{ idProv: 1, idBloque: 41 }] })
  })

  it('elegir «Bloque nuevo» lo manda con idBloque null', async () => {
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await anadirLinea(1)
    await userEvent.click(await within(recuadro('ACME')).findByRole('combobox', { name: 'Bloque de ACME' }))
    await userEvent.click(await within(screen.getByRole('listbox', { name: 'Bloque de ACME' })).findByRole('button', { name: 'Bloque nuevo' }))
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
    expect(lotes[0]).toMatchObject({ destinos: [{ idProv: 1, idBloque: null }] })
  })

  it('un proveedor sin bloques abiertos va a uno nuevo sin preguntar y no viaja en destinos', async () => {
    compras = []
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('Proveedor B')
    await anadirLinea(1)
    expect(recuadro('Proveedor B')).toHaveTextContent(/1 línea.*Bloque nuevo.*no tiene ninguno abierto/)
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
    expect(lotes[0]).toMatchObject({ destinos: null })
  })

  it('«Añadir líneas» de un bloque: título, un solo recuadro fijo (sin × ni «+ Otro proveedor») y destino fijado', async () => {
    const onCerrar = abrir({ modo: 'bloque', idBloque: 41, idProv: 1 })
    await screen.findByRole('dialog', { name: 'Añadir líneas · Bloque 41' })
    await anadirLinea(1)
    expect(recuadro('ACME')).toHaveTextContent(/Bloque 41.*\(fijado\)/)
    expect(screen.queryByRole('button', { name: 'Quitar ACME' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Otro proveedor' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Proveedor línea 1' })).not.toBeInTheDocument()
    await confirmar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
    expect(lotes[0]).toMatchObject({ lineas: [{ idProv: 1 }], destinos: [{ idProv: 1, idBloque: 41 }] })
  })

  it('un 409 de destino deja el formulario abierto con el mensaje y recarga los pedidos', async () => {
    respuestaLote = () => HttpResponse.json({ message: 'El bloque 41 ya no existe; elige otro destino.' }, { status: 409 })
    abrir()
    await screen.findByRole('dialog', { name: 'Nuevo pedido' })
    await elegirProveedor('ACME')
    await anadirLinea(1)
    await within(recuadro('ACME')).findByRole('combobox', { name: 'Bloque de ACME' })
    const antes = lecturas
    await confirmar()
    expect(await screen.findByText('El bloque 41 ya no existe; elige otro destino.')).toBeInTheDocument()
    await waitFor(() => expect(lecturas).toBeGreaterThan(antes))
  })
})
```

- [ ] **Step 6: Documentación**

En `CHANGELOG.md` `[0.9.9]`:
- **Sustituir** la viñeta que empieza por «- **Un proveedor por pedido.**» por:

```
- **Varios proveedores en un mismo «Nuevo pedido».** Cada proveedor va en su recuadro, que es su bloque: en la cabecera se ve a qué bloque va («Añadir a Bloque N» o «Bloque nuevo»), su «Añadir previsión (n)», ▼ para plegarlo y × para quitarlo. «+ Otro proveedor» añade otro y «Confirmar pedido (n bloques)» lo guarda todo de una vez. Lo que llega de la campana, de Stock o de las solicitudes va solo al proveedor que vende cada pieza; si la venden varios o ninguno, espera en «⚠ Por repartir» con «Pasar a…». Al elegir una pieza sale lo que falta: su «Pedir 60 d» menos lo que ya va a otros proveedores en el pedido. Ya no hay «Proveedor general» ni el recuadro «Bloques» del pie.
```

- En la viñeta «**Cantidad prevista en «Nuevo pedido».**»:
  - «Una pieza nunca va en dos líneas:» pasa a «Una pieza no se repite dentro de un mismo proveedor (en dos proveedores sí, con el aviso «… también va a X (n).»):»;
  - ««… ya está en el pedido (línea n).»» pasa a ««… ya está en el pedido a X (línea n).»».

En `docs/paridad/pedidos.md`:
- **Línea «- [x] Línea: Componente …»:** «Proveedor (desde la 0.9.9, el del pedido sin combo; ver «Un proveedor por pedido»)» pasa a «Proveedor (desde la 0.9.9 no hay columna: la línea va en el recuadro de su proveedor; en «Por repartir», «Pasar a…»)».
- **Viñeta «- [x] **Un proveedor por pedido (0.9.9, …)**»:** se sustituye por:

```
- [x] **Varios proveedores en un mismo pedido (0.9.9, decisión del usuario 2026-10-13; sustituye a «un proveedor por pedido» del 2026-10-12)**: un recuadro por proveedor, ordenado por nombre, que es su bloque; en la cabecera, el destino, «Añadir previsión (n)», plegar y ×; «+ Otro proveedor»; lo precargado va solo al único proveedor activo que vende la pieza y lo demás a «⚠ Por repartir» («Pasar a…» con «Lo vende» / «Otros proveedores»); la cantidad es lo que falta (su «Pedir 60 d» menos lo que va a otros proveedores en el pedido); «Confirmar pedido (n bloques)» comprueba primero lo que queda por repartir y marca, despliega y trae a la vista el recuadro con el error. Desviación del JavaFX, que elegía proveedor en cada línea (`recuadros.test.ts`, `NuevoPedidoDialog.test.tsx` «varios proveedores en un mismo pedido»).
```

- **Viñeta «**Cantidad prevista y sin repetidas …**»:** «Una pieza ya presente no se repite:» pasa a «Una pieza ya presente en el mismo recuadro no se repite (en otro proveedor sí, con «… también va a X (n).»):», y ««… ya está en el pedido (línea n).»» pasa a ««… ya está en el pedido a X (línea n).»».
- **Línea 254, «- «Nuevo pedido» con el resumen «Bloques» al pie y modo …»:** pasa a «- «Nuevo pedido» con un recuadro por proveedor (destino en su cabecera) y modo «Añadir líneas» de un bloque; «Editar pedido» con el campo «Bloque» al cambiar el proveedor.».

- [ ] **Step 7: Comprobar que pasa**

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos`;
2. `npx tsc -b && npm run lint`;
3. `grep -rn "ResumenBloques\|aplicarProveedorATodas\|Proveedor general" src`, que no debe sacar nada;
4. la suite completa: `npx vitest run 2>&1 | grep -E "Test Files|Tests "`.

Expected: todo verde.

Si un test falla por cómo se busca algo y no por lo que hace el código, por ejemplo una etiqueta repetida entre dos recuadros, se acota con `within(recuadro(…))`. Ningún test se cambia para tapar un comportamiento distinto del de esta tarea: si el código no hace lo que dice el test, lo que se arregla es el código.

- [ ] **Step 8: Commit**

```bash
git add -A src/modules/almacen/pedidos/formulario CHANGELOG.md docs/paridad/pedidos.md
git commit -m "feat: nuevo pedido con varios proveedores, un recuadro por bloque y por repartir"
```

---

### Task 5: E2E — el proveedor se elige en «¿A qué proveedor pides?» y un pedido a dos proveedores

**Files:**
- Modify: `gestion-reparaciones-web/tests/e2e/pedidos.spec.ts` y `tests/e2e/bloques.spec.ts`

**Restricción:** **no ejecutar Playwright contra ningún entorno.** Las E2E solo corren contra preproducción y las lanza el controlador tras desplegar. Aquí se comprueba con `npx tsc -b` (si los e2e entran en el proyecto), `npm run lint` y `npx playwright test --list`.

- [ ] **Step 1: `pedidos.spec.ts`**

1. Tras `llamarApi`, añadir este ayudante:

```ts
/** «Nuevo pedido» (0.9.9, varios proveedores): al abrir vacío, «¿A qué proveedor pides?» con el ComboNavy «Proveedor»; con
 *  un recuadro ya puesto, «+ Otro proveedor». Cada opción es «{nombre} · {bloque abierto o «sin bloque abierto»}». */
async function elegirProveedorDelPedido(page: Page, combo: 'Proveedor' | 'Otro proveedor', nombre: string) {
  const dlg = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
  await dlg.getByRole('combobox', { name: combo, exact: true }).click()
  const escapado = nombre.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  await page.getByRole('listbox', { name: combo, exact: true }).getByRole('button', { name: new RegExp(`^${escapado} · `) }).click()
}
```

2. En el paso «pedido de componentes: crear, editar y borrar»:
   - `await expect(dlg.getByText('Añade al menos una línea')).toBeVisible()` pasa a `await expect(dlg.getByText('¿A qué proveedor pides?')).toBeVisible()`;
   - justo después va `await elegirProveedorDelPedido(page, 'Proveedor', nombreProv)`, antes de «+ Añadir línea»;
   - se borran las tres líneas del «Proveedor general»: el comentario «Un proveedor por pedido (0.9.9)…» y los dos `click` del combo y de la lista.
   El resto no cambia: el cuerpo esperado sigue siendo una línea con `idProv` y `destinos: null`.

3. **Proveedor de prueba B.** Junto a `nombreProv`, añadir `const nombreProvB = `${nombreProv}-b``. Junto a `const idProv = await crearProveedorDePrueba(page, nombreProv)`, añadir `const idProvB = await crearProveedorDePrueba(page, nombreProvB)`. En el `finally`, tras borrar `idProv`, borrar también el B:

```ts
    try {
      const r = await llamarApi(page, 'DELETE', `/api/proveedores/${idProvB}`)
      expect.soft(r.status, `limpieza DELETE /api/proveedores/${idProvB}`).toBe(204)
    } catch (e) {
      console.warn(`limpieza DELETE /api/proveedores/${idProvB} lanzó`, e)
    }
```

4. **Paso nuevo**, entre «pedido de componentes…» y «otro pedido…» (los dos proveedores son nuevos y sin bloques):

```ts
    await test.step('pedido a dos proveedores: dos recuadros, dos bloques; se borran', async () => {
      await page.getByRole('button', { name: 'Nuevo pedido', exact: true }).click()
      const dlg = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
      await elegirProveedorDelPedido(page, 'Proveedor', nombreProv)
      const recuadroA = dlg.getByRole('region', { name: `Pedido a ${nombreProv}`, exact: true })
      await recuadroA.getByRole('button', { name: '+ Añadir línea' }).click()
      await recuadroA.getByPlaceholder('Escribe componente...').fill(sku!)
      await page.getByRole('option', { name: sku!, exact: true }).click()
      await recuadroA.getByLabel('Cantidad línea 1').fill('1')
      await elegirProveedorDelPedido(page, 'Otro proveedor', nombreProvB)
      const recuadroB = dlg.getByRole('region', { name: `Pedido a ${nombreProvB}`, exact: true })
      await recuadroB.getByRole('button', { name: '+ Añadir línea' }).click()
      await recuadroB.getByPlaceholder('Escribe componente...').fill(sku!)
      await page.getByRole('option', { name: sku!, exact: true }).click()
      // La misma pieza a dos proveedores se puede (0.9.9): cada recuadro avisa del otro. La cantidad, 1 a mano.
      await recuadroB.getByLabel('Cantidad línea 1').fill('1')
      await expect(recuadroA).toContainText(`también va a ${nombreProvB} (1).`)

      const respuestaLote = page.waitForResponse(esRespuesta('/api/compras/lote', 'POST'))
      await dlg.getByRole('button', { name: 'Confirmar pedido (2 bloques)' }).click()
      const respuesta = await respuestaLote
      if (respuesta.ok()) {
        const { idsCreados } = (await respuesta.json()) as RespuestaLote
        for (const idCreado of idsCreados) porBorrar.add(`/api/compras/${idCreado}`)
      }
      expect(respuesta.ok(), `POST /api/compras/lote respondió ${respuesta.status()}`).toBe(true)
      expect(respuesta.request().postDataJSON()).toEqual({
        lineas: [
          { idCom: expect.any(Number), idProv, cantidad: 1, esUrgente: false, precioUnidad: 0 },
          { idCom: expect.any(Number), idProv: idProvB, cantidad: 1, esUrgente: false, precioUnidad: 0 },
        ],
        solicitudes: { urgentes: [], preventivas: [] },
        destinos: null,
      })
      await expect(dlg).toBeHidden()

      // Vista agrupada: un bloque nuevo por proveedor.
      await page.getByRole('switch', { name: 'Agrupar por bloque' }).click()
      await expect(page.locator('tr[data-cabecera]').filter({ hasText: nombreProvB })).toHaveCount(1)
      await expect(page.locator('tr[data-cabecera]').filter({ hasText: new RegExp(`${nombreProv.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!-b)`) })).toHaveCount(1)
      // Se borran por API, por el id que devolvió el lote (solo lo creado aquí).
      for (const ruta of [...porBorrar].filter((r) => r.startsWith('/api/compras/'))) {
        const r = await llamarApi(page, 'DELETE', ruta)
        expect(r.status, `DELETE ${ruta}`).toBe(200)
        porBorrar.delete(ruta)
      }
    })
```

Ojo con el interruptor: el paso anterior deja la vista **plana**, porque pulsó «Agrupar por bloque». Este paso lo vuelve a pulsar para verla agrupada. Comprueba en el fichero en qué estado lo deja cada paso y ajústalo para que el siguiente, «otro pedido…», empiece igual que hoy: si hace falta, vuelve a pulsarlo al final de este paso. El buscador sigue con el SKU del paso anterior y las dos cabeceras lo cumplen.

- [ ] **Step 2: `bloques.spec.ts`**

1. En `rellenarNuevoPedido`, las dos líneas del «Proveedor general» pasan a:

```ts
  // «¿A qué proveedor pides?» (0.9.9, varios proveedores): ComboNavy «Proveedor», opciones «{nombre} · {bloque abierto}».
  await dlg.getByRole('combobox', { name: 'Proveedor', exact: true }).click()
  await page.getByRole('listbox', { name: 'Proveedor', exact: true }).getByRole('button', { name: new RegExp(`^${escaparRegex(nombreProv)} · `) }).click()
```

   `escaparRegex` ya existe más abajo en el fichero; si se usa antes de definirla, muévela arriba. Actualiza el comentario de la función: «el proveedor, en «¿A qué proveedor pides?»».

2. **Recuadro «Bloques» del pie.** Busca `getByRole('region', { name: 'Bloques' })` (las variables `resumen`). Cámbialo por el recuadro del proveedor, `dlg.getByRole('region', { name: `Pedido a ${nombreProv}`, exact: true })`:
   - «2 líneas» o «1 línea» siguen saliendo en la cabecera del recuadro como «· 1 línea · EUR»;
   - «Bloque nuevo (no tiene ninguno abierto)» sale igual;
   - el combo `Bloque de ${nombreProv}` está en el recuadro;
   - en el test 1, el resumen esperaba «1 línea»: el recuadro lo enseña igual.

3. No hay más cambios. `anadirLineaAlBloque` («Añadir líneas…») tiene un solo recuadro y su «+ Añadir línea» es único.

- [ ] **Step 3: Comprobar**

Run:
1. `npx tsc -b`;
2. `npm run lint`;
3. `npx playwright test --list 2>&1 | tail -5`, que solo lista y no ejecuta. Si se queja por variables de entorno, dilo en el informe.

Expected: sin errores; la lista sigue con los mismos ficheros.

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/pedidos.spec.ts tests/e2e/bloques.spec.ts
git commit -m "test: e2e de nuevo pedido con el proveedor en su recuadro y un pedido a dos proveedores"
```

---

### Task 6: Cierre (controlador)

- [ ] Revisión final de la rama, con el modelo más capaz.
- [ ] Con OK:
  1. `merge --no-ff` en `main`, con mensaje `merge: varios proveedores en un mismo nuevo pedido (0.9.9)`, y push.
  2. En la raíz: gitlink y la viñeta de `NOVEDADES-v0.9.9.md`. La del «📦 Qué es un bloque» que dice «Cada «Nuevo pedido» es para un solo proveedor…» se reescribe. Push.
- [ ] Preprod: solo la web (`build nginx` + `up -d --no-deps nginx`), las E2E de siempre y la limpieza 2c. Los proveedores `e2e-proveedor-…` y `…-b` los borra el propio `pedidos.spec`.
- [ ] `Apuntes/prod-v099.md`: el commit nuevo de la web.
