# 0.9.9 (ajuste) — Cantidad prevista, piezas repetidas, aviso de precio e icono de nota — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En «Nuevo pedido», la cantidad de una línea sale de «Pedir 60 d» y una pieza nunca va en dos líneas. En la cabecera del bloque, un aviso «! N sin precio» y un icono de nota del estilo del ERP.

**Architecture:**
- Solo web.
- Funciones puras nuevas en `formulario/lineas.ts`: `cantidadPrevista`, `elegirPieza` y `avisoRepetida`. La precarga `componentes` usa la cantidad prevista.
- `NuevoPedidoDialog` usa `elegirPieza` al elegir la pieza de una línea.
- `DialogoLineas` acepta una petición de selección.
- `bloques/reglas.ts` cuenta las líneas sin precio y `CabeceraBloque` pinta el chip y el icono `MessageSquareText` de lucide-react.

**Tech Stack:** React, TypeScript y lucide-react. Pruebas con Vitest, Testing Library y MSW.

**Spec:** `docs/superpowers/specs/2026-10-13-v099-cantidad-prevista-y-cabecera-design.md` (raíz). Prototipo en `docs/superpowers/specs/assets/2026-10-13-v099-prototipo-cabecera-bloque.html`. Web `main` `4f0a863`.

## Global Constraints

- **Rama y worktree:** rama `feature/cantidad-prevista-cabecera` creada **desde `main`** en `C:/Users/dev/Documents/wt/web-099`. Hoy está en `fix/dialogo-piezas-ancho`, ya mergeada. Solo web: el servidor no se toca.
- **Comandos (Git Bash), en `wt/web-099`:** `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint`.
  - **Un solo vitest a la vez, siempre en primer plano.** No dejar procesos en segundo plano.
  - La suite completa tarda ~4 min: usar un timeout de 600000 ms.
- **Commits:**
  - En español **sin tildes**, con los prefijos `feat:` / `fix:` / `test:` / `docs:`.
  - **Sin** `Co-Authored-By`.
  - Nada de push ni merge sin OK.
- **Comentarios:** en español, **con** tildes.
- **Textos exactos:**
  - aviso de pieza repetida: «{pieza} ya está en el pedido (línea {n}).»;
  - chip: «! {n} sin precio»;
  - tooltip del chip: «{n} líneas recibidas sin precio: ponlo con «Editar».», y con n = 1, «1 línea recibida sin precio: ponlo con «Editar».».
- **Reglas:**
  - **Cantidad prevista:** `pedir60` del master si es > 0; si no, nada. Al elegir la pieza solo se aplica si la cantidad de la línea es exactamente `'1'`.
  - **Solicitudes:** la precarga `solicitudes` no cambia.
  - **Pieza repetida:** si la pieza ya está en **otra** línea, se quita la línea que se estaba rellenando y queda seleccionada la otra, sin tocar su cantidad.
  - **Sin precio:** cuentan las líneas con `marcaPrecioCero(p) || marcaTotalCero(p)`, que son las recibidas a 0.
- **Registro de subagentes:** `.superpowers/sdd/v099e/` en la raíz (gitignored).

---

### Task 1: «Nuevo pedido» — cantidad prevista y piezas repetidas

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.ts` y `formulario/lineas.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/DialogoLineas.tsx`: prop `seleccionar`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx` y `formulario/NuevoPedidoDialog.test.tsx`

**Interfaces:**
- Produces, en `formulario/lineas.ts`:
  - `cantidadPrevista(idCom: number, activos: Componente[]): number | null`;
  - `type Eleccion = { lineas: LineaCompra[]; repetida: { id: number; n: number } | null }`;
  - `elegirPieza(lineas: LineaCompra[], id: number, idCom: number, activos: Componente[]): Eleccion`;
  - `avisoRepetida(nombre: string, n: number): string`.
- Produces, en `DialogoLineas`: prop opcional `seleccionar?: { id: number; marca: number } | null`. Cada `marca` nueva selecciona la línea `id`.

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git status --short && git switch -c feature/cantidad-prevista-cabecera main && git log --oneline -1
```
Expected: árbol limpio; `4f0a863 merge: el dialogo de piezas que vende no se sale de la caja (0.9.9)`.

- [ ] **Step 1: Tests que fallan (funciones puras)**

En `formulario/lineas.test.ts`, añadir `cantidadPrevista`, `elegirPieza` y `avisoRepetida` al import de `./lineas` y este bloque al final:

```ts
describe('cantidad prevista y piezas repetidas (0.9.9, 2026-10-13)', () => {
  // lcd-x-negro (1) pide 16; bat-x (2) no pide nada; bat-y (5) es slave de bat-x.
  const ACTIVOS: Componente[] = COMPONENTES.map((c) => (c.idCom === 1 ? { ...c, pedir60: 16 } : { ...c, pedir60: 0 }))

  it('cantidadPrevista: «Pedir 60 d» si es > 0; si no, null', () => {
    expect(cantidadPrevista(1, ACTIVOS)).toBe(16)
    expect(cantidadPrevista(2, ACTIVOS)).toBeNull()
    expect(cantidadPrevista(99, ACTIVOS)).toBeNull()
    expect(cantidadPrevista(1, COMPONENTES)).toBeNull()            // sin previsión calculada (pedir60 ausente)
  })

  it('elegir una pieza con previsión pone su cantidad si la línea seguía con el 1 de entrada', () => {
    const r = elegirPieza([lineaCompraVacia(1)], 1, 1, ACTIVOS)
    expect(r.repetida).toBeNull()
    expect(r.lineas).toEqual([{ ...lineaCompraVacia(1), idCom: 1, cantidad: '16' }])
  })

  it('con la cantidad ya cambiada, o sin previsión, solo cambia la pieza', () => {
    expect(elegirPieza([{ ...lineaCompraVacia(1), cantidad: '3' }], 1, 1, ACTIVOS).lineas[0]).toMatchObject({ idCom: 1, cantidad: '3' })
    expect(elegirPieza([lineaCompraVacia(1)], 1, 2, ACTIVOS).lineas[0]).toMatchObject({ idCom: 2, cantidad: '1' })
  })

  it('una pieza que ya está en otra línea: se quita la línea nueva y se devuelve la que queda con su número', () => {
    const lineas = [{ ...lineaCompraVacia(1, 1), cantidad: '16' }, lineaCompraVacia(2, 2), lineaCompraVacia(3)]
    const r = elegirPieza(lineas, 3, 1, ACTIVOS)
    expect(r.lineas.map((l) => l.id)).toEqual([1, 2])
    expect(r.lineas[0].cantidad).toBe('16')
    expect(r.repetida).toEqual({ id: 1, n: 1 })
    // Cambiar la pieza de una línea a otra que ya estaba, igual; el número es el de la línea que queda tras quitar.
    const r2 = elegirPieza(lineas, 1, 2, ACTIVOS)
    expect(r2.lineas.map((l) => l.id)).toEqual([2, 3])
    expect(r2.repetida).toEqual({ id: 2, n: 1 })
  })

  it('elegir en una línea la pieza que ya tenía no es repetida', () => {
    const r = elegirPieza([{ ...lineaCompraVacia(1, 1), cantidad: '4' }], 1, 1, ACTIVOS)
    expect(r.repetida).toBeNull()
    expect(r.lineas[0].cantidad).toBe('4')
  })

  it('aviso de repetida', () => {
    expect(avisoRepetida('lcd-x-negro', 3)).toBe('lcd-x-negro ya está en el pedido (línea 3).')
  })

  it('«Pedir» y «Pedir todas las piezas» entran con la cantidad prevista (las solicitudes, no)', () => {
    expect(precargarComponentes([1, 2], ACTIVOS).map((l) => [l.idCom, l.cantidad])).toEqual([[1, '16'], [2, '1']])
    // Un slave se pide a su master, con la previsión del master.
    expect(precargarComponentes([5], ACTIVOS).map((l) => [l.idCom, l.cantidad])).toEqual([[2, '1']])
  })
})
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts`
Expected: falla, porque las funciones no existen.

- [ ] **Step 2: Implementación (funciones puras)**

En `formulario/lineas.ts`, tras `idPedible`:

```ts
/** «Pedir 60 d» de la pieza (su master; el listado de Stock lo trae calculado para SUPERTECNICO y ADMIN) si es > 0;
 *  si no hay previsión o no hay nada que pedir, null (0.9.9, 2026-10-13). */
export function cantidadPrevista(idCom: number, activos: Componente[]): number | null {
  const pedir = activos.find((c) => c.idCom === idCom)?.pedir60 ?? 0
  return pedir > 0 ? pedir : null
}
```

`precargarComponentes` pasa a:

```ts
/** "Pedir" (1 id) y "Pedir todas las piezas" (N ids, orden de la campana): una línea por id. El id de un slave se
 *  sustituye por el de su master (el pedido va al master); la cantidad es su «Pedir 60 d» si es > 0 y, si no, 1
 *  (2026-10-13). Un id que no está entre los activos, o cuyo master no lo está, deja la línea vacía, como el JavaFX. */
export function precargarComponentes(idsCom: number[], activos: Componente[]): LineaCompra[] {
  return idsCom.map((idCom, i) => {
    const id = idPedible(idCom, activos)
    const linea = lineaCompraVacia(i + 1, id)
    const prevista = id === null ? null : cantidadPrevista(id, activos)
    return prevista === null ? linea : { ...linea, cantidad: String(prevista) }
  })
}
```

Junto a `cambiarLinea`:

```ts
/** Lo que deja elegir una pieza en una línea (0.9.9, 2026-10-13): las líneas y, si la pieza ya estaba en otra línea, cuál
 *  queda (su id y su número tras quitar la que se estaba rellenando). */
export type Eleccion = { lineas: LineaCompra[]; repetida: { id: number; n: number } | null }

/** Elegir la pieza `idCom` en la línea `id`. Si otra línea ya la tiene, la línea `id` se quita (una pieza nunca va en
 *  dos líneas) y la otra no cambia. Si no, la línea toma la pieza y, si seguía con el «1» de entrada, su «Pedir 60 d». */
export function elegirPieza(lineas: LineaCompra[], id: number, idCom: number, activos: Componente[]): Eleccion {
  const otra = lineas.find((l) => l.id !== id && l.idCom === idCom)
  if (otra !== undefined) {
    const restantes = quitarLinea(lineas, id)
    return { lineas: restantes, repetida: { id: otra.id, n: restantes.findIndex((l) => l.id === otra.id) + 1 } }
  }
  const linea = lineas.find((l) => l.id === id)
  const prevista = cantidadPrevista(idCom, activos)
  const cambio: Partial<LineaCompra> = { idCom }
  if (linea !== undefined && linea.cantidad === '1' && prevista !== null) cambio.cantidad = String(prevista)
  return { lineas: cambiarLinea(lineas, id, cambio), repetida: null }
}

export function avisoRepetida(nombre: string, n: number): string {
  return `${nombre} ya está en el pedido (línea ${n}).`
}
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts`
Expected: verde.

- [ ] **Step 3: Tests que fallan (diálogo)**

En `formulario/NuevoPedidoDialog.test.tsx`, dentro del describe «pedido automático por proveedor…» (fixture `CON_PREVISION`: lcd-x-negro pide 16, de ACME), añadir:

```tsx
  it('elegir una pieza con previsión en una línea pone su «Pedir 60 d» (2026-10-13)', async () => {
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(1, 'lcd', 'lcd-x-negro')
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
  })

  it('una pieza que ya está en el pedido no se repite: queda seleccionada la que estaba y sale el aviso', async () => {
    abrir()
    await screen.findByRole('button', { name: 'Añadir previsión' })
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión de ACME (1)' }))
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir línea' }))
    await elegirComponente(2, 'lcd', 'lcd-x-negro')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(filaDe(1)).toHaveAttribute('data-state', 'selected')
    expect(screen.getByRole('status')).toHaveTextContent('lcd-x-negro ya está en el pedido (línea 1).')
  })
```

`elegirProveedor(nombre)`, `elegirComponente(n, texto, opcion)`, `filaDe(n)` y `escribir(etiqueta, valor)` ya existen arriba en el fichero.

En ese mismo describe, el test existente «con la pieza ya en el pedido sube la cantidad y no la duplica» abre `{ modo: 'componentes', idsCom: [1] }` y esperaba '1'. Ahora «Pedir» entra con 16, así que pasa a:

```tsx
  it('con la pieza ya en el pedido sube la cantidad y no la duplica', async () => {
    abrir({ modo: 'componentes', idsCom: [1] })
    // 2026-10-13: «Pedir» ya entra con su «Pedir 60 d»; se baja a mano para ver que la previsión la vuelve a subir.
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16'))
    await escribir('Cantidad línea 1', '2')
    await elegirProveedor('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión de ACME (1)' }))
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
  })
```

Run: `npx vitest run src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx`
Expected: fallan los dos tests nuevos y el modificado (sigue entrando con '1').

- [ ] **Step 4: Implementación (diálogo)**

`formulario/DialogoLineas.tsx`:
- Añadir a `Props<L>`:

```ts
  /** Petición de selección desde fuera (0.9.9, 2026-10-13: al juntar una pieza repetida queda seleccionada la línea que
   *  ya la tenía). Cada `marca` nueva selecciona la línea `id`. */
  seleccionar?: { id: number; marca: number } | null
```

- Añadirlo a la desestructuración del componente y, tras `const [seleccionada, setSeleccionada] = useState<number | null>(null)`, poner:

```ts
  // Ajuste durante el render (sin useEffect): una marca nueva de `seleccionar` selecciona esa línea una vez.
  const [marcaVista, setMarcaVista] = useState<number | null>(null)
  if (seleccionar && seleccionar.marca !== marcaVista) {
    setMarcaVista(seleccionar.marca)
    setSeleccionada(seleccionar.id)
  }
```

`formulario/NuevoPedidoDialog.tsx`:
- **Import de `./lineas`:** añadir `avisoRepetida` y `elegirPieza`.
- **Estado nuevo**, junto a los demás:

```ts
  // 2026-10-13: aviso de pieza repetida y petición de seleccionar la línea que ya la tenía.
  const [repetida, setRepetida] = useState<string | null>(null)
  const [seleccion, setSeleccion] = useState<{ id: number; marca: number } | null>(null)
```

- **En `cambiar`, `quitar` y `anadir`:** añadir `setRepetida(null)` junto a los otros `set…(…)` de limpieza.
- **Función nueva, tras `cambiar`:**

```ts
  /** Elegir la pieza de una línea (2026-10-13): cantidad prevista y, si la pieza ya estaba, se junta en esa línea. */
  function elegir(id: number, idCom: number) {
    const r = elegirPieza(lineas ?? [], id, idCom, activos)
    setLineas(r.lineas)
    setError(null)
    setSinPedido(0)
    setSinListaVistas(0)
    setNadaQuePedir(false)
    if (r.repetida === null) {
      setRepetida(null)
      return
    }
    const { id: queda, n } = r.repetida
    setRepetida(avisoRepetida(opciones.find((o) => o.clave === String(idCom))?.etiqueta ?? '', n))
    setSeleccion((s) => ({ id: queda, marca: (s?.marca ?? 0) + 1 }))
  }
```

- **`CampoAutocompletar` de la primera columna:** `onElegir={(c) => cambiar(l.id, { idCom: Number(c) })}` pasa a `onElegir={(c) => elegir(l.id, Number(c))}`.
- **`info`:** añadir `repetida` al array, al final: `[…, nadaQuePedir ? … : null, repetida]`.
- **`<DialogoLineas …>`:** añadir `seleccionar={seleccion}`.

- [ ] **Step 5: Comprobar que pasa**

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos/formulario`;
2. `npx tsc -b && npm run lint`;
3. la suite entera: `npx vitest run 2>&1 | grep -E "Test Files|Tests "`.

Expected: todo verde. Los tests de siempre de la precarga «Pedir» siguen con cantidad 1, porque su fixture no trae `pedir60`.

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/pedidos/formulario
git commit -m "feat: nuevo pedido con la cantidad prevista al elegir la pieza y sin piezas repetidas"
```

---

### Task 2: Cabecera del bloque — aviso «! N sin precio» e icono de nota; documentación

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/reglas.ts` y `bloques/reglas.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/CabeceraBloque.tsx` y `bloques/CabeceraBloque.test.tsx`
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (`[0.9.9]`) y `docs/paridad/pedidos.md`

**Interfaces:**
- Consumes: `marcaPrecioCero(p)` y `marcaTotalCero(p)` de `../reglas` (`src/modules/almacen/pedidos/reglas.ts`).
- Produces, en `bloques/reglas.ts`: `sinPrecio(b: Bloque): number` y `textoSinPrecio(n: number): string`.

- [ ] **Step 1: Tests que fallan**

En `bloques/reglas.test.ts`, añadir `sinPrecio` y `textoSinPrecio` al import y:

```ts
describe('líneas recibidas sin precio (2026-10-13)', () => {
  it('cuenta las recibidas con P.Unit. o EUR a 0; las pendientes o en camino a 0 no', () => {
    const b = [...agruparEnBloques([
      l({ idCompra: 1, idBloque: 70, estado: 'recibido', precioUnidadPedido: 0, precioEur: 0 }),
      l({ idCompra: 2, idBloque: 70, estado: 'recibido' }),
      l({ idCompra: 3, idBloque: 70, estado: 'pendiente', precioUnidadPedido: 0, precioEur: 0 }),
      l({ idCompra: 4, idBloque: 70, estado: 'en_camino', precioUnidadPedido: 0, precioEur: 0 }),
    ]).values()][0]
    expect(sinPrecio(b)).toBe(1)
  })
  it('texto del tooltip', () => {
    expect(textoSinPrecio(1)).toBe('1 línea recibida sin precio: ponlo con «Editar».')
    expect(textoSinPrecio(3)).toBe('3 líneas recibidas sin precio: ponlo con «Editar».')
  })
})
```

`l` es `lineaDePrueba` de `./datosPrueba`: por defecto trae `precioUnidadPedido: 10` y `precioEur: 10`, así que la línea 2 no cuenta.

En `bloques/CabeceraBloque.test.tsx`:
- En el test «número, proveedor, fecha, líneas, total, ⚠, chips del resumen y nota», la aserción `expect(screen.getByTitle('Ped. 123')).toHaveTextContent('📝 Ped. 123')` pasa a:

```tsx
    const nota = screen.getByTitle('Ped. 123')
    expect(nota).toHaveTextContent('Ped. 123')
    expect(nota).not.toHaveTextContent('📝')
    expect(nota.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.queryByText(/sin precio/)).not.toBeInTheDocument()
```

- Añadir el test:

```tsx
  it('chip «! N sin precio» con líneas recibidas a 0 (2026-10-13)', () => {
    const b = bloque(l({ idCompra: 1, estado: 'recibido', precioUnidadPedido: 0, precioEur: 0 }), l({ idCompra: 2, estado: 'recibido', precioUnidadPedido: 0, precioEur: 0 }))
    render(<CabeceraBloque bloque={b} visibles={2} desplegado={false} onAlternar={vi.fn()} />)
    const chip = screen.getByText('! 2 sin precio')
    expect(chip).toHaveAttribute('title', '2 líneas recibidas sin precio: ponlo con «Editar».')
    expect(chip).toHaveClass('bg-fila-solicitud-bg', 'text-fila-solicitud-brd')
  })
```

Run: `npx vitest run src/modules/almacen/pedidos/bloques`
Expected: fallan los tests nuevos y el cambiado.

- [ ] **Step 2: Implementación**

En `bloques/reglas.ts`:
- Importar `marcaPrecioCero` y `marcaTotalCero` de `../reglas`. El fichero ya importa de ahí otras cosas: añadirlas a ese import.
- Junto a `avisoBloque`:

```ts
/** 2026-10-13: líneas recibidas sin precio (las del «!» ámbar de su fila); el precio se pone con «Editar». */
export function sinPrecio(b: Bloque): number {
  return b.lineas.filter((p) => marcaPrecioCero(p) || marcaTotalCero(p)).length
}

export function textoSinPrecio(n: number): string {
  return `${n === 1 ? '1 línea recibida' : `${n} líneas recibidas`} sin precio: ponlo con «Editar».`
}
```

En `bloques/CabeceraBloque.tsx`:
- `import { MessageSquareText } from 'lucide-react'`;
- añadir `sinPrecio` y `textoSinPrecio` al import de `./reglas`;
- tras el `<span className="flex min-w-0 gap-1 overflow-hidden">…chips…</span>`, añadir:

```tsx
      {sinPrecio(bloque) > 0 && (
        <span title={textoSinPrecio(sinPrecio(bloque))}
          className="shrink-0 rounded-[12px] bg-fila-solicitud-bg px-2 py-0.5 text-[11px] font-bold text-fila-solicitud-brd">
          ! {sinPrecio(bloque)} sin precio
        </span>
      )}
```

- la nota:
  `{bloque.nota && <span title={bloque.nota} className="min-w-0 truncate text-azul-gris italic">📝 {bloque.nota}</span>}`
  pasa a

```tsx
      {bloque.nota && (
        <span title={bloque.nota} className="flex min-w-0 items-center gap-1 text-azul-gris italic">
          <MessageSquareText aria-hidden="true" className="size-3.5 shrink-0" />
          <span className="truncate">{bloque.nota}</span>
        </span>
      )}
```

`CHANGELOG.md` `[0.9.9]`: añadir, tras la viñeta de «**Un proveedor por pedido.**»:

```
- **Cantidad prevista en «Nuevo pedido».** Al elegir la pieza de una línea, la cantidad pasa a su «Pedir 60 d» (si la línea seguía con 1); «Pedir» desde Stock y «Pedir todas las piezas» desde la campana entran ya con esa cantidad. Una pieza nunca va en dos líneas: si se elige una que ya estaba, se queda la que ya la tenía, seleccionada, con el aviso «… ya está en el pedido (línea n).».
- **Cabecera del bloque:** chip ámbar «! N sin precio» si hay líneas recibidas sin precio (las del «!» de su fila), y la nota con un icono de bocadillo en lugar del emoji.
```

`docs/paridad/pedidos.md`, sección «Nuevo pedido»: añadir

```
- [x] **Cantidad prevista y sin repetidas (0.9.9, decisión del usuario 2026-10-13)**: al elegir la pieza, la cantidad es su «Pedir 60 d» si la línea seguía con 1 (también en «Pedir» y «Pedir todas las piezas»; las solicitudes, como siempre); una pieza ya presente no se repite: la línea nueva desaparece, queda seleccionada la otra y sale «… ya está en el pedido (línea n).». El JavaFX dejaba 1 y permitía repetidas (`lineas.test.ts` «cantidad prevista y piezas repetidas», `NuevoPedidoDialog.test.tsx`).
```

En la sección de la vista por bloques de ese mismo fichero (búscala por «Bloques» o por «cabecera»), añadir:

```
- [x] **Cabecera: «! N sin precio» e icono de nota (0.9.9, 2026-10-13)**: chip ámbar con las líneas recibidas a 0 (tooltip «… ponlo con «Editar».»); la nota lleva el icono `MessageSquareText` en vez del emoji (`CabeceraBloque.test.tsx`).
```

- [ ] **Step 3: Comprobar que pasa**

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos`;
2. `npx tsc -b && npm run lint`;
3. la suite entera: `npx vitest run 2>&1 | grep -E "Test Files|Tests "`.

Expected: todo verde.

- [ ] **Step 4: Commit**

```bash
git add src/modules/almacen/pedidos/bloques CHANGELOG.md docs/paridad/pedidos.md
git commit -m "feat: cabecera del bloque con aviso de lineas sin precio e icono de nota"
```

---

### Task 3: Cierre (controlador)

- [ ] Revisión final de la rama.
- [ ] Con OK:
  1. `merge --no-ff` en `main` con el mensaje `merge: cantidad prevista, sin repetidas y cabecera del bloque (0.9.9)`, y push.
  2. En la raíz: gitlink y `NOVEDADES-v0.9.9.md`, con una viñeta para cada mejora. Push.
- [ ] Preprod: solo la web (`docker compose build nginx && docker compose up -d --no-deps nginx`) y las E2E de siempre, más la limpieza 2c.
- [ ] `Apuntes/prod-v099.md`: el commit nuevo de la web.
