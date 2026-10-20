# 0.9.18.2 — Un móvil vendido o dado de baja no está «En revisión» · Plan de implementación

> **DESCARTADO (20/10, decisión del usuario):** se arregla solo en el servidor
> (`plans/2026-10-20-v09182-vendido-no-en-revision.md`). La Task 1 llegó a hacerse (web `bf54cfd`) y se borró con su rama
> local, que nunca se subió. No ejecutar.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que un móvil vendido o dado de baja no salga «En revisión» ni «Pendiente de revisar» en la ficha del IMEI, ni con
ubicación en «Pegar varios IMEIs», y que el rastro lea «Sale de …» el vaciado de caja con que NSYS lo saca.

**Architecture:**
- **Solo web.** El servidor sigue mandando la caja tal cual; la web decide qué enseña.
- `shared/lib/telefonoNsys.ts`: `fueraDeLaEmpresa(estado)`. Lo usan la ficha (`FichaMovil.tsx`) y «Pegar varios IMEIs»
  (`varios.ts` → `ubicacionDe`, que usan `ComprobarImeis.tsx` y `paraExcel`).
- `modules/taller/imeis/rastro.ts`: `vaciaLaCajaAlSalir(e, eventos)` y `verEvento(e, alSalir)`; `PanelRastro.tsx` le pasa la
  marca.
- Rama `hotfix/0.9.18.2` desde el tag `v0.9.18.1` (`27637b5`), ya creada en el worktree `/c/Users/dev/Documents/wt/web-09181`.

**Tech Stack:** React 19 + TypeScript; Vitest + Testing Library + MSW (`npm run check` = lint + typecheck + test).

**Spec:** `docs/superpowers/specs/2026-10-20-v09182-vendidos-sin-caja-design.md` (raíz).

## Global Constraints

- **Solo web**: sin cambios en el servidor ni en la base; funciona con el servidor 0.9.18.
- **Fuera de la empresa** = estado `VENDIDO` o `BAJA`.
- Ficha: fuera de la empresa, sin píldora de caja ni «Pendiente de revisar…»; «Revisado el …» sí, si lo hay.
- «Pegar varios IMEIs»: fuera de la empresa, «Ubicación» vacía en la tabla y en la copia para Excel.
- Rastro: un `UBICACION` con `a === 'En revisión'` del mismo `lote` y la misma `fecha` que un `VENTA` o que un `ESTADO` cuyo
  `a` no es `Available`, `InProcess` ni `Repair` → título **`Sale de ${e.de ?? 'su caja'}`**, color `bg-gris-borde`,
  `detalle: null`. Los demás eventos, como hoy.
- Versión `0.9.18.2`. **Nunca `npm version`**: con cuatro números escribe basura. `npm pkg set` y el lock a mano.
- Repos públicos: datos inventados en código y pruebas. Commits sin `Co-Authored-By`.
- Merge, push, tag y despliegues **solo con el OK del usuario**. Otras sesiones: no tocar el clon principal de la web ni los
  worktrees `wt/web-0919-nr` y `wt/web-0920`; preprod sin `pull` y de vuelta a como estaba.

## Mapa de ficheros (todos en el worktree)

| Fichero | Qué cambia |
|---|---|
| `src/shared/lib/telefonoNsys.ts` (+ `.test.ts`) | `fueraDeLaEmpresa` |
| `src/modules/taller/imeis/FichaMovil.tsx` (+ `.test.tsx`) | Sin caja ni «pendiente» fuera de la empresa |
| `src/modules/telefonos/stock/varios.ts` (+ `.test.ts`) | `ubicacionDe`; `paraExcel` la usa |
| `src/modules/telefonos/stock/ComprobarImeis.tsx` (+ `.test.tsx`) | La celda «Ubicación» usa `ubicacionDe` |
| `src/modules/taller/imeis/rastro.ts` (+ `.test.ts`) | `vaciaLaCajaAlSalir`; `verEvento(e, alSalir)` |
| `src/modules/taller/imeis/PanelRastro.tsx` (+ `.test.tsx`) | Le pasa la marca a `verEvento` |
| `package.json`, `package-lock.json`, `CHANGELOG.md` | 0.9.18.2 |

---

### Task 1: Ficha y «Pegar varios IMEIs» sin caja fuera de la empresa

**Files:**
- Modify: `src/shared/lib/telefonoNsys.ts`, `src/modules/taller/imeis/FichaMovil.tsx`, `src/modules/telefonos/stock/varios.ts`,
  `src/modules/telefonos/stock/ComprobarImeis.tsx`
- Test: `src/shared/lib/telefonoNsys.test.ts`, `src/modules/taller/imeis/FichaMovil.test.tsx`,
  `src/modules/telefonos/stock/varios.test.ts`, `src/modules/telefonos/stock/ComprobarImeis.test.tsx`

**Interfaces:**
- Consumes: `fichaMovil` (`src/modules/taller/test/fabrica.ts`; por defecto `estado: 'DE_PEDIDO'`, `ubicacion: 'Listos'`),
  `movilComprobado`, `AHORA` (`src/modules/telefonos/test/fabrica.ts`; por defecto `estado: 'STOCK'`, `ubicacion: 'Listos'`,
  modelo «iPhone 13 128GB», `color: 'Azul'`, `grado: 'A'`, `trabajos: []`).
- Produces: `fueraDeLaEmpresa(estado: string | null | undefined): boolean` y `ubicacionDe(m: MovilComprobado): string | null`.

- [ ] **Step 1: Las pruebas que fallan**

`src/shared/lib/telefonoNsys.test.ts`: añadir `fueraDeLaEmpresa` al import de `./telefonoNsys` y, tras el caso
`'texto del estado del móvil'`:

```ts
  it('fuera de la empresa: vendido o dado de baja (0.9.18.2)', () => {
    expect(['STOCK', 'DE_PEDIDO', 'NO_CUENTA', 'VENDIDO', 'BAJA', null].map(fueraDeLaEmpresa))
      .toEqual([false, false, false, true, true, false])
  })
```

`src/modules/taller/imeis/FichaMovil.test.tsx`, al final del fichero:

```tsx
describe('ficha de NSYS: vendido o dado de baja (0.9.18.2)', () => {
  it('un vendido no enseña caja ni «pendiente de revisar»: NSYS le vació la caja al sacarlo', () => {
    ficha({ estado: 'VENDIDO', fechaVenta: '2026-10-20', ubicacion: 'En revisión', pendienteRevisionDesde: '2026-10-20' })
    const region = screen.getByRole('region', { name: 'Ficha de NSYS' })
    expect(region).toHaveTextContent('Vendido el 20/10/2026')
    expect(region).not.toHaveTextContent('En revisión')
    expect(region).not.toHaveTextContent('Pendiente de revisar')
  })

  it('una baja, igual', () => {
    ficha({ estado: 'BAJA', ubicacion: 'En revisión', pendienteRevisionDesde: '2026-10-20' })
    const region = screen.getByRole('region', { name: 'Ficha de NSYS' })
    expect(region).toHaveTextContent('Baja en NSYS')
    expect(region).not.toHaveTextContent('En revisión')
    expect(region).not.toHaveTextContent('Pendiente de revisar')
  })

  it('si el lote se revisó, el vendido lo sigue diciendo', () => {
    ficha({ estado: 'VENDIDO', fechaVenta: '2026-10-20', ubicacion: 'En revisión', revisadoEl: '2026-10-13T08:46:00' })
    expect(screen.getByRole('region', { name: 'Ficha de NSYS' })).toHaveTextContent('Revisado el 13/10/2026, hacia las 08:46')
  })
})
```

`src/modules/telefonos/stock/varios.test.ts`: añadir `ubicacionDe` al import de `./varios` y, tras el caso
`'la tabla para Excel: separada por tabuladores, con cabecera y «Quién lo tiene»'`:

```ts
  it('la tabla para Excel: un vendido va sin ubicación (0.9.18.2)', () => {
    const { lineas } = analizar('350000000000001')
    const filas = cruzar(lineas, [movilComprobado({ imei: '350000000000001', estado: 'VENDIDO', fechaVenta: '2026-10-20', ubicacion: 'En revisión' })])
    expect(paraExcel(filas, AHORA).split('\n')[1]).toBe('1\t350000000000001\tiPhone 13 128GB\tAzul\tA\t\t—\tVendido 20/10/2026')
  })

  it('ubicacionDe: la de NSYS, salvo si el móvil ya no está en la empresa (0.9.18.2)', () => {
    expect(ubicacionDe(movilComprobado({ ubicacion: 'Listos' }))).toBe('Listos')
    expect(ubicacionDe(movilComprobado({ estado: 'VENDIDO', ubicacion: 'En revisión' }))).toBeNull()
    expect(ubicacionDe(movilComprobado({ estado: 'BAJA', ubicacion: 'En revisión' }))).toBeNull()
  })
```

`src/modules/telefonos/stock/ComprobarImeis.test.tsx`, dentro del `describe`, al final:

```tsx
  it('un vendido no tiene ubicación: NSYS le vació la caja al venderlo (0.9.18.2)', async () => {
    server.use(http.post('*/api/stock-telefonos/comprobar', () =>
      HttpResponse.json([movilComprobado({ imei: '350000000000001', estado: 'VENDIDO', fechaVenta: '2026-10-20', ubicacion: 'En revisión' })])))
    abrirDialogo('350000000000001')
    const tabla = await within(await dialogo()).findByRole('table', { name: 'Resultado' })
    const celdas = within(within(tabla).getAllByRole('row')[1]).getAllByRole('cell')
    expect(celdas[5]).toBeEmptyDOMElement()
    expect(celdas.at(-1)).toHaveTextContent('Vendido 20/10/2026')
  })
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd /c/Users/dev/Documents/wt/web-09181 && npx vitest run src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis/FichaMovil.test.tsx src/modules/telefonos/stock/varios.test.ts src/modules/telefonos/stock/ComprobarImeis.test.tsx`
Expected: FAIL. `telefonoNsys` y `varios` no compilan el import (`fueraDeLaEmpresa` / `ubicacionDe` no existen); en la ficha,
los dos primeros casos nuevos fallan por «En revisión»/«Pendiente de revisar»; en el diálogo, `celdas[5]` no está vacía.

- [ ] **Step 3: El código**

`src/shared/lib/telefonoNsys.ts`, tras `textoEstadoMovil`:

```ts
/** Vendido (Released) o dado de baja en NSYS: ya no está en la empresa. NSYS le vacía la caja al sacarlo, y ese «sin caja»
 *  no es «En revisión» (0.9.18.2). */
export function fueraDeLaEmpresa(estado: string | null | undefined): boolean {
  return estado === 'VENDIDO' || estado === 'BAJA'
}
```

`src/modules/taller/imeis/FichaMovil.tsx`:
- el import de `@/shared/lib/telefonoNsys` pasa a `{ clasesUbicacion, diaNsys, fueraDeLaEmpresa, nombreModeloNsys, textoEstadoMovil }`;
- al final del comentario de la función, una línea más: `*  Desde la 0.9.18.2, un móvil vendido o dado de baja no enseña caja ni «pendiente de revisar».`
  (el `*/` pasa a esa línea);
- `const revision = textoRevision(ficha)` pasa a:

```tsx
  // 0.9.18.2: fuera de la empresa, el «sin caja» es que NSYS lo sacó: ni caja ni «pendiente de revisar».
  const fuera = fueraDeLaEmpresa(ficha.estado)
  const revision = textoRevision(fuera ? { ...ficha, pendienteRevisionDesde: null } : ficha)
```

- la píldora de la caja, `{ficha.ubicacion && <span …>{ficha.ubicacion}</span>}`, pasa a empezar por
  `{ficha.ubicacion && !fuera && …` (el resto igual).

`src/modules/telefonos/stock/varios.ts`:
- el import de `@/shared/lib/telefonoNsys` pasa a `{ diaNsys, fueraDeLaEmpresa, nombreModeloNsys, textoEstadoMovil }`;
- justo antes de `const CABECERA_EXCEL`:

```ts
/** La ubicación que se enseña: ninguna si el móvil ya no está en la empresa (0.9.18.2: su «sin caja» es que NSYS lo sacó). */
export const ubicacionDe = (m: MovilComprobado): string | null => (fueraDeLaEmpresa(m.estado) ? null : m.ubicacion)
```

- en `paraExcel`, `m?.ubicacion ?? ''` pasa a `m ? ubicacionDe(m) ?? '' : ''`.

`src/modules/telefonos/stock/ComprobarImeis.tsx`:
- el import de `./varios` añade `ubicacionDe`: `{ analizar, cruzar, modeloYCapacidad, paraExcel, resumen, textoEstado, ubicacionDe, type Cifra }`;
- en el `map` de las filas, tras `const falta = …`: `const ubicacion = m ? ubicacionDe(m) : null`;
- la celda de la ubicación pasa a:

```tsx
                      <td className="px-2 py-1">{ubicacion && <span className={cn(CLASES_PILDORA, clasesUbicacion(ubicacion))}>{ubicacion}</span>}</td>
```

(La celda de «Quién lo tiene» sigue pasando `m.ubicacion` a `quienLoTiene`: no cambia.)

- [ ] **Step 4: Comprobar que pasan, y la suite entera**

Run: el mismo `npx vitest run …` del Step 2.
Expected: PASS.

Run: `npm run lint && npm run typecheck && npx vitest run` (en primer plano, timeout largo; la suite tarda ~5 min).
Expected: lint sin errores (1 aviso previo en `BadgeEstadoPedido.tsx`), typecheck limpio, todo en verde.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-09181
git add src/shared/lib/telefonoNsys.ts src/shared/lib/telefonoNsys.test.ts src/modules/taller/imeis/FichaMovil.tsx src/modules/taller/imeis/FichaMovil.test.tsx src/modules/telefonos/stock/varios.ts src/modules/telefonos/stock/varios.test.ts src/modules/telefonos/stock/ComprobarImeis.tsx src/modules/telefonos/stock/ComprobarImeis.test.tsx
git commit -m "fix: un movil vendido o dado de baja no ensena caja ni pendiente de revisar en la ficha ni en pegar varios IMEIs (0.9.18.2)"
```

---

### Task 2: El rastro — «Sale de …» en vez de «Pasa a En revisión»

**Files:**
- Modify: `src/modules/taller/imeis/rastro.ts`, `src/modules/taller/imeis/PanelRastro.tsx`
- Test: `src/modules/taller/imeis/rastro.test.ts`, `src/modules/taller/imeis/PanelRastro.test.tsx`

**Interfaces:**
- Consumes: `eventoRastro` (`src/modules/taller/test/fabrica.ts`; por defecto `tipo: 'UBICACION'`, `lote: 'BT-9001'`,
  `fecha: '2026-09-12T13:01:00'`, `de: 'Taller · Glass'`, `a: 'Listos'`, `aNsys: 'CAJA LISTOS'`).
- Produces: `vaciaLaCajaAlSalir(e: EventoRastro, eventos: EventoRastro[]): boolean`;
  `verEvento(e: EventoRastro, alSalir = false): EventoVisible` (las llamadas de un solo argumento siguen igual).

- [ ] **Step 1: Las pruebas que fallan**

`src/modules/taller/imeis/rastro.test.ts`: el import de `./rastro` pasa a
`{ fechaRastro, hayAntesDelHistorico, textoRevision, vaciaLaCajaAlSalir, verEvento }` y, al final del fichero:

```ts
describe('el «sin caja» con que NSYS saca un móvil (0.9.18.2)', () => {
  const HORA = '2026-10-20T14:01:00'
  const sinCaja = eventoRastro({ fecha: HORA, lote: 'BT-9002', de: 'Pedidos realizados', a: 'En revisión', aNsys: null })
  const venta = eventoRastro({ fecha: HORA, lote: 'BT-9002', tipo: 'VENTA', de: null, a: null, aNsys: null })
  const baja = eventoRastro({ fecha: HORA, lote: 'BT-9002', tipo: 'ESTADO', de: 'InProcess', a: 'Removed', aNsys: null })

  it('con la venta o con un estado de baja, en la misma exportación y del mismo lote, es la salida', () => {
    expect(vaciaLaCajaAlSalir(sinCaja, [venta, sinCaja])).toBe(true)
    expect(vaciaLaCajaAlSalir(sinCaja, [baja, sinCaja])).toBe(true)
  })

  it('no lo es con un estado de stock, otro lote, otra hora o un destino que no es «En revisión»', () => {
    expect(vaciaLaCajaAlSalir(sinCaja, [{ ...baja, a: 'Available' }, sinCaja])).toBe(false)
    expect(vaciaLaCajaAlSalir(sinCaja, [{ ...venta, lote: 'BT-9001' }, sinCaja])).toBe(false)
    expect(vaciaLaCajaAlSalir(sinCaja, [{ ...venta, fecha: '2026-10-20T13:31:00' }, sinCaja])).toBe(false)
    const aListos = { ...sinCaja, a: 'Listos' }
    expect(vaciaLaCajaAlSalir(aListos, [venta, aListos])).toBe(false)
    expect(vaciaLaCajaAlSalir(venta, [venta, sinCaja])).toBe(false)
  })

  it('se lee «Sale de …», en gris y sin «Desde»; sin la marca, como siempre', () => {
    expect(verEvento(sinCaja, true)).toEqual({ fecha: '20/10/2026 14:01', titulo: 'Sale de Pedidos realizados', detalle: null, color: 'bg-gris-borde' })
    expect(verEvento(sinCaja)).toMatchObject({ titulo: 'Pasa a En revisión', detalle: 'Desde: Pedidos realizados', color: 'bg-ambar' })
  })
})
```

`src/modules/taller/imeis/PanelRastro.test.tsx`, al final del fichero:

```tsx
describe('PanelRastro: la salida de NSYS (0.9.18.2)', () => {
  it('el «sin caja» de la venta se lee «Sale de …», no «Pasa a En revisión»', () => {
    const hora = '2026-10-20T14:01:00'
    render(<PanelRastro imei="351900000000041" onCerrar={() => {}} onIrATrabajo={() => {}} eventos={[
      eventoRastro({ fecha: hora, tipo: 'VENTA', de: null, a: null, aNsys: null }),
      eventoRastro({ fecha: hora, de: 'Pedidos realizados', a: 'En revisión', aNsys: null }),
    ]} />)
    expect(screen.getByText('Sale de Pedidos realizados')).toBeInTheDocument()
    expect(screen.queryByText('Pasa a En revisión')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Comprobar que fallan**

Run: `cd /c/Users/dev/Documents/wt/web-09181 && npx vitest run src/modules/taller/imeis/rastro.test.ts src/modules/taller/imeis/PanelRastro.test.tsx`
Expected: FAIL: `vaciaLaCajaAlSalir` no existe (import) y el panel enseña «Pasa a En revisión».

- [ ] **Step 3: El código**

`src/modules/taller/imeis/rastro.ts`:
- justo antes de `export function verEvento`:

```ts
/** Los Status de NSYS de un móvil en la empresa (los demás lo sacan: Released, Removed…), como `EstadoMovil.VIVOS` del servidor. */
const STATUS_VIVOS = new Set(['Available', 'InProcess', 'Repair'])

/** NSYS vacía la caja del móvil que saca (lo vende o lo borra): ese «sin caja» llega en la misma exportación y del mismo lote
 *  que la venta o el estado de baja, y no es «En revisión» (0.9.18.2). */
export function vaciaLaCajaAlSalir(e: EventoRastro, eventos: EventoRastro[]): boolean {
  if (e.tipo !== 'UBICACION' || e.a !== 'En revisión') return false
  return eventos.some((o) => o.lote === e.lote && o.fecha === e.fecha
    && (o.tipo === 'VENTA' || (o.tipo === 'ESTADO' && o.a != null && !STATUS_VIVOS.has(o.a))))
}
```

- `export function verEvento(e: EventoRastro): EventoVisible {` pasa a
  `export function verEvento(e: EventoRastro, alSalir = false): EventoVisible {`;
- en `case 'UBICACION':`, antes del comentario de la 0.9.18:

```ts
      // 0.9.18.2: el «sin caja» con que NSYS saca el móvil (venta o baja) no es «En revisión».
      if (alSalir) return { fecha, titulo: `Sale de ${e.de ?? 'su caja'}`, detalle: null, color: 'bg-gris-borde' }
```

`src/modules/taller/imeis/PanelRastro.tsx`:
- el import de `./rastro` pasa a `{ hayAntesDelHistorico, NOTA_ANTES_DEL_HISTORICO, vaciaLaCajaAlSalir, verEvento }`;
- `const v = verEvento(e)` pasa a `const v = verEvento(e, vaciaLaCajaAlSalir(e, eventos))`.

- [ ] **Step 4: Comprobar que pasan, y la suite entera**

Run: el mismo `npx vitest run …` del Step 2.
Expected: PASS.

Run: `npm run lint && npm run typecheck && npx vitest run` (en primer plano, timeout largo).
Expected: todo en verde.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/web-09181
git add src/modules/taller/imeis/rastro.ts src/modules/taller/imeis/rastro.test.ts src/modules/taller/imeis/PanelRastro.tsx src/modules/taller/imeis/PanelRastro.test.tsx
git commit -m "fix: el rastro lee Sale de la caja el sin caja con que NSYS vende o borra un movil (0.9.18.2)"
```

---

### Task 3: Cierre — versión, CHANGELOG y novedades

**Files:**
- Modify: `package.json`, `package-lock.json`, `CHANGELOG.md` (worktree); `docs/novedades/NOVEDADES-v0.9.18.md` (raíz)

- [ ] **Step 1: Versión**

Run: `cd /c/Users/dev/Documents/wt/web-09181 && npm pkg set version=0.9.18.2 && grep -n '"version": "0.9.18.1"' package-lock.json`
Expected: el grep da las líneas 3 y 9. Cambiarlas a mano (Edit) a `"version": "0.9.18.2",`. Ninguna otra.

- [ ] **Step 2: CHANGELOG**

Antes de `## [0.9.18.1]`:

```markdown
## [0.9.18.2] - 2026-10-XX — IMEIs: un móvil vendido o dado de baja no está «En revisión»

- **IMEIs y Stock de teléfonos** (supertécnico y administrador): cuando NSYS saca un móvil (lo vende o lo borra) le vacía la caja, y el ERP lo enseñaba «En revisión». Ahora, en un móvil vendido o dado de baja, la ficha no enseña caja ni «Pendiente de revisar» (sí «Revisado el …», si lo hubo), «Pegar varios IMEIs» deja la ubicación vacía (también al copiar para Excel) y el rastro dice «Sale de Pedidos realizados» (o de la caja que fuera) en lugar de «Pasa a En revisión».
- Solo web: funciona con el servidor 0.9.18. Sin migración.

```

- [ ] **Step 3: Comprobar y commit**

Run: `npm run lint && npm run typecheck && npx vitest run`
Expected: todo en verde.

```bash
git add package.json package-lock.json CHANGELOG.md
git commit -m "docs: version 0.9.18.2 y changelog de los moviles vendidos sin caja"
```

- [ ] **Step 4: Novedades de la 0.9.18 (raíz)**

En `docs/novedades/NOVEDADES-v0.9.18.md` de la raíz, en `### 🔎 IMEIs`, tras la línea
`- En el rastro, la revisión sale como **«Revisado → caja»**.`:

```markdown
- Un móvil **vendido o dado de baja** no sale «En revisión» ni «Pendiente de revisar»: al sacarlo, NSYS le vacía la caja.
  En el rastro, ese paso se lee **«Sale de …»** (0.9.18.2).
```

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/novedades/NOVEDADES-v0.9.18.md
git commit -m "docs: novedades de la 0.9.18 con los moviles vendidos sin caja (0.9.18.2)"
```

- [ ] **Step 5: Push de la rama (con el OK del usuario)**

`cd /c/Users/dev/Documents/wt/web-09181 && git push -u origin hotfix/0.9.18.2`

---

### Task 4: Preprod, producción, tag y merge a `main`

Igual que la 0.9.18.1 (`Apuntes/preprod/v09181-preprod.md` y `Apuntes/prod-v09181.md`), con la rama `hotfix/0.9.18.2`:

- [ ] **Step 1: Preprod** — guion `Apuntes/preprod/v09182-preprod.md` (copia del de la 0.9.18.1 con `hotfix/0.9.18.2`, su
  commit y, en el bloque 1, en lugar de las consultas del descuadre, una que da 3 IMEIs vendidos el último día:
  `SELECT IMEI_SN, BATCH, UBICACION FROM Nsys_movil WHERE ESTADO = 'VENDIDO' AND CAMBIADO > NOW() - INTERVAL 1 DAY LIMIT 3;`).
  Desde el PC: `/version.json` 0.9.18.2, E2E `stock-telefonos`, y la ficha y el rastro de uno de esos IMEIs con Playwright
  (solo lee): sin «En revisión» ni «Pendiente de revisar» en la ficha; «Sale de …» en el rastro. Preprod vuelve a la rama y
  el commit del bloque 1.
- [ ] **Step 2: Producción** — guion `Apuntes/prod-v09182.md` (copia del de la 0.9.18.1: estado antes = web `8a5b942` en
  commit suelto; `checkout --detach origin/hotfix/0.9.18.2`; vuelta atrás = `checkout --detach 8a5b942`). Comprobación del
  usuario: la ficha y el rastro del IMEI vendido de su captura.
- [ ] **Step 3: Fecha, tag y merge (con el OK del usuario)** — fecha en `[0.9.18.2]`, tag `v0.9.18.2` y push. Merge
  `--no-ff` de `hotfix/0.9.18.2` (lleva la 0.9.18.1) a `main` de la web en el clon principal, solo si está limpio y en `main`
  = `origin/main`. Conflictos: `package.json`/`package-lock.json` → los de `main`; `CHANGELOG.md` → `[0.9.19]` entera **con**
  su línea «Solo web…», línea en blanco, `[0.9.18.2]` y `[0.9.18.1]` enteras con las suyas (ver la nota de la Task 4 Step 3
  del plan de la 0.9.18.1). `npm run check` y push.
- [ ] **Step 4: Raíz y limpieza** — gitlink de la web a `v0.9.18.2` con `update-index --cacheinfo` (sin tocar la copia del
  submódulo), registros, memoria (`project_produccion_vdc`: commit suelto `v0.9.18.2`), `plan-futuro.md`; worktree
  `wt/web-09181` y ramas locales `hotfix/0.9.18.1` y `hotfix/0.9.18.2` fuera.
