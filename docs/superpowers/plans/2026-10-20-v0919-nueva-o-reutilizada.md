# 0.9.19 — Formulario de reparación: «Nueva» o «Reutilizada» en la celda del SKU · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que cada pieza del formulario de reparación empiece diciendo, en la celda del SKU, si es **nueva** (gasta del stock)
o **reutilizada** (no gasta), para que el stock deje de bajar de más y el «SKU del taller» pueda fiarse de esa elección.

**Architecture:**
- **Solo web** (módulo `taller/formulario`), sin servidor ni base: el contrato sigue siendo `cantidad` + `reutilizado`.
- `estado.ts` (reductor puro) gana un campo `eleccion` por fila, dos acciones (`ELEGIR`, `VOLVER`) y selectores para pintar
  (`botonNueva`, `marcaPieza`, `muestraEleccion`, `reutilizadaJuntoASolicitud`, `filaElegidaSinSku`, `textoFaltaSku`).
  `SUMAR` / `RESTAR` solo valen con «Nueva»; las bandas de pedir solo salen con «Nueva».
- `borrador.ts` deduce la elección al recuperar un borrador (el JSON no cambia: es el mismo que el del JavaFX).
- `FilaComponente.tsx` pinta los dos botones en la celda del SKU, la marca con su «×» en la columna «Pieza» (antes
  «¿Reutilizado?») y «Solicitar pieza» directo. `useGuardado.ts` avisa si una fila elegida no tiene SKU.

**Tech Stack:** React 19 + TypeScript, Vitest + Testing Library + user-event + MSW, Playwright (E2E en preprod).

**Spec:** `docs/superpowers/specs/2026-10-20-v0919-nueva-o-reutilizada-design.md` (raíz). Prototipo aprobado:
`docs/superpowers/specs/assets/2026-10-20-v0919-prototipo-nueva-o-reutilizada.html` (v5).

## Global Constraints

**Versión, ramas y worktree**
- Va en la **0.9.19** de la web (el `package.json` ya dice 0.9.19: **no se cambia la versión**). Esta pieza no toca el
  servidor ni la base; la subida entera de la 0.9.19 la cierra otra sesión (lleva además el «SKU del taller», con servidor).
- Rama **`dev_v0919_nueva_reutilizada`** de la web, desde su `main`, en el worktree
  **`/c/Users/dev/Documents/wt/web-0919-nr`** con `npm ci` dentro (lo crea la Task 1). La copia principal de la web la usan
  otras sesiones: **no se toca**. Las rutas `src/…` y `tests/…` del plan son de ese worktree; `docs/…` sin prefijo es la raíz.
- Registro de los subagentes: **`.superpowers/sdd/v0919-nr/`** (raíz, local).

**Commits y datos de prueba**
- Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**.
- Commits en español sin tildes, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- **Repo público:** datos de prueba sintéticos (las fábricas de `src/modules/taller/test/fabrica.ts`, IMEI `355400000000111`).
  Nada de capturas del programa real en `docs/`.

**Textos y medidas exactos** (spec §2)
- Botones de la celda del SKU: «+ Nueva» (`aria-label` «Nueva <tipo>»), «↺ Reutilizada» («Reutilizada <tipo>») y «Solicitar
  pieza» («Solicitar <tipo>»); `<tipo>` es el nombre de la fila («Batería», «Chasis»…).
- Columna «Pieza» (140 px) en lugar de «¿Reutilizado?» (110 px); «Observación» pasa de 280 a **250 px**. El total de columnas
  sigue en 870 px.
- Marcas de «Pieza» (`data-testid` `marca-<prefijo>`): «+ Nueva», «↺ Reutilizada», «Solicitada». Su «×»: `aria-label`
  «Volver atrás <tipo>», `title` «Volver atrás: la fila queda como al principio».
- Contador de una reutilizada: «—». Stock de una Nueva sin guardar: «<stock> → <stock − cantidad>» en rojo.
- Aviso de «Terminar asignación» / «Guardar cambios» con una fila elegida sin SKU: «Falta elegir el SKU de: Chasis.» (nombres
  de las filas separados por «, »), en el diálogo de error de la web.

**Verificación**
- Comandos en el worktree: `npx vitest run <ficheros>` para una tarea; `npm run check` (lint + typecheck + test) al cerrar.
- Las Tasks 2 y 3 cambian la lógica antes que la interfaz: **al terminar la Task 2, los tests de componentes del formulario
  (`FilaComponente`, `FormularioReparacion`, `SubFilaAgotado`, `rutas`, `api`) y `ImeiDetallePage` pueden fallar**: la interfaz
  aún pinta «+» y la casilla. Los arregla la Task 4. Desde la Task 4, `npm run check` en verde en cada tarea.

## Cómo se adaptan los tests de antes

Cuando una tarea dice «adaptar los tests», es esto. **Solo** se tocan los tests que fallan, y cada cambio debe caer en una de
estas dos tablas; si un fallo no encaja en ninguna, **parar y avisar** (puede ser una regresión).

**Traducción mecánica** (el comportamiento es el mismo, cambia el gesto):

| Antes | Ahora |
|---|---|
| Primer `{ tipo: 'SUMAR', prefijo: P }` sobre una fila sin elegir | `{ tipo: 'ELEGIR', prefijo: P, eleccion: 'nueva' }` (ya pone 1) |
| `SUMAR` siguientes | igual |
| `dispatch({ tipo: 'SUMAR', prefijo: P })` en un arnés que puede pulsarse varias veces | `dispatch(sumarOElegir(estado, P))` (`src/modules/taller/formulario/test/acciones.ts`, Task 2) |
| `{ tipo: 'MARCAR_REUTILIZADO', prefijo: P, valor: true }` | `{ tipo: 'ELEGIR', prefijo: P, eleccion: 'reutilizada' }` |
| `{ tipo: 'MARCAR_REUTILIZADO', prefijo: P, valor: false }` | `{ tipo: 'VOLVER', prefijo: P }` |
| Clic en «Sumar X» sobre una fila sin elegir (UI) | clic en «Nueva X» |
| Marcar la casilla «Reutilizado X» (UI) | clic en «Reutilizada X» |
| Desmarcar la casilla (UI) | clic en «Volver atrás X» |
| `getByRole('checkbox', { name: 'Reutilizado X' })` marcada | `getByTestId('marca-P')` con «↺ Reutilizada» |
| Abrir o leer el combo «SKU de X» de una fila sin elegir (UI) | antes, clic en «Nueva X» o «Reutilizada X» (sin elegir, el combo no se pinta) |
| Cabecera «¿Reutilizado?» | «Pieza» |
| Un `FilaEstado` escrito a mano en un test (p. ej. en `estado.color.test.ts` o `estado.solicitudes.test.ts`) | añadir `eleccion`: `null`, o la que encaje con su `cantidad` / `reutilizado` |

**Cambios de comportamiento a propósito** (el test pasa a esperar lo nuevo, citando la spec en el nombre o en un comentario):
1. **«−» hasta 0** deja la fila **sin elegir**: `eleccion: null`, controles `{ mas: false, menos: false, reutilizado: true,
   sku: true, observacion: true }` (antes «+» seguía encendido).
2. **Desmarcar «Reutilizado»** (ahora `VOLVER`) deja la fila sin elegir con esos mismos controles; desaparece la rareza de «"+"
   habilitado con stock 0 tras desmarcar».
3. **Cambiar de SKU con Nueva** ajusta la cantidad **sin pasarse del stock** (2 → SKU con stock 1 = 1, antes 0); a un SKU sin
   stock, 0 y la banda de pedirla.
4. **Bandas de pedir** («Sin stock…», «Stock agotado…») **solo con Nueva**: en una fila sin tocar o reutilizada, `subFila` da
   `oculta`. Para probar la banda, elegir antes «Nueva» (o usar «Solicitar pieza» directo si ningún SKU tiene stock).
5. **Fila editada al abrir:** `eleccion` según lo guardado; controles `{ mas: nueva && stock > 0, menos: nueva && cantidad > 0,
   reutilizado: true, sku: true, observacion: true }`.
6. **Chasis y tapa sin color:** controles `{ mas: false, menos: false, reutilizado: true, sku: true, observacion: false }`
   («Reutilizada» se puede elegir antes que el color).
7. **«Cancelar solicitud»** (local) deja la fila sin elegir (cantidad 0, `eleccion: null`), como la «×».
8. **Filas guardadas borradas por otro** (`DESBLOQUEAR_BORRADAS`): vuelven sin elegir.
9. **`previsionStock`** devuelve «stock → stock − cantidad» también en una Nueva sin guardar (no solo en la fila editada); en la
   interfaz la columna Stock lo enseña.
10. **`filaActiva`** exige SKU (`idCom !== null`).
11. **Zona de guardar:** también visible con una fila elegida sin SKU (para poder avisar al pulsar).

## Mapa de ficheros

| Fichero (worktree de la web) | Qué cambia | Task |
|---|---|---|
| `src/modules/taller/formulario/estado.ts` | `Eleccion`, campo `eleccion`, `ELEGIR` / `VOLVER`, reglas y selectores nuevos | 2 (y 4 quita `MARCAR_REUTILIZADO`) |
| `src/modules/taller/formulario/test/acciones.ts` | Nuevo: `sumarOElegir` para los arneses | 2 |
| `src/modules/taller/formulario/estado.eleccion.test.ts` | Nuevo: tests de la elección | 2 |
| `src/modules/taller/formulario/estado*.test.ts`, `useGuardado.test.tsx`, `useBorrador.test.tsx`, `borrador.test.ts` | Adaptar | 2 |
| `src/modules/taller/formulario/borrador.ts` (+ `borrador.test.ts`) | Deducir la elección; no capturar elegidas sin SKU | 3 |
| `src/modules/taller/formulario/FilaComponente.tsx` (+ test) | Celda del SKU, columna «Pieza», «Solicitar pieza» directo | 4 |
| `src/modules/taller/formulario/FormularioReparacion.tsx` (+ test) | Cabecera y anchos | 4 |
| `SubFilaAgotado.test.tsx`, `rutas.test.tsx`, `api.test.tsx`, `src/modules/taller/imeis/ImeiDetallePage.test.tsx` | Adaptar | 4 |
| `src/modules/taller/formulario/useGuardado.ts` (+ test) | Aviso «Falta elegir el SKU de: …» | 5 |
| `tests/e2e/formulario.spec.ts` | Nueva, Solicitar directo, Volver, editar a reutilizada | 6 |
| `docs/paridad/formulario.md`, `CHANGELOG.md` (web); `docs/novedades/NOVEDADES-v0.9.19.md` (raíz) | Documentación | 7 |

---

### Task 1: Worktree y línea base

**Files:** ninguno (solo el worktree).

**Interfaces:**
- Produces: el worktree `/c/Users/dev/Documents/wt/web-0919-nr` en la rama `dev_v0919_nueva_reutilizada`, con la suite en verde.

- [ ] **Step 1: Crear el worktree**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git worktree add -b dev_v0919_nueva_reutilizada /c/Users/dev/Documents/wt/web-0919-nr main
cd /c/Users/dev/Documents/wt/web-0919-nr && npm ci
```
Expected: `Preparing worktree (new branch 'dev_v0919_nueva_reutilizada')` y `npm ci` sin errores.

- [ ] **Step 2: Línea base**

Run (en el worktree): `npm run check`
Expected: lint, typecheck y todos los tests en verde. Si algo falla ya en `main`, anotarlo en `.superpowers/sdd/v0919-nr/progress.md`
(raíz) y avisar antes de seguir.

- [ ] **Step 3: Registro**

Crear `.superpowers/sdd/v0919-nr/progress.md` (raíz) con la rama, el commit de partida (`git log --oneline -1`) y el resultado
de la línea base. No se commitea (`.superpowers/` está en `.gitignore`).

---

### Task 2: Reductor — la elección de cada fila

**Files:**
- Modify: `src/modules/taller/formulario/estado.ts`
- Create: `src/modules/taller/formulario/test/acciones.ts`
- Create: `src/modules/taller/formulario/estado.eleccion.test.ts`
- Modify (adaptar): `src/modules/taller/formulario/estado.test.ts`, `estado.edicion.test.ts`, `estado.solicitudes.test.ts`,
  `estado.color.test.ts`, `estado.cerrar.test.ts`, `borrador.test.ts`, `useGuardado.test.tsx`, `useBorrador.test.tsx`

**Interfaces:**
- Produces (en `estado.ts`, exportado):
  - `type Eleccion = 'nueva' | 'reutilizada' | null`; `FilaEstado.eleccion: Eleccion`.
  - Acciones `{ tipo: 'ELEGIR'; prefijo: string; eleccion: 'nueva' | 'reutilizada' }` y `{ tipo: 'VOLVER'; prefijo: string }`.
    `MARCAR_REUTILIZADO` sigue de momento como alias (`true` → `ELEGIR reutilizada`, `false` → `VOLVER`); la quita la Task 4.
  - `type BotonNueva = 'nueva' | 'solicitar' | 'deshabilitada'`; `botonNueva(e: EstadoFormulario, fila: FilaEstado): BotonNueva`.
  - `type MarcaPieza = { tipo: 'nueva' | 'reutilizada' | 'solicitada'; quitar: boolean }`; `marcaPieza(fila: FilaEstado): MarcaPieza | null`.
  - `muestraEleccion(fila: FilaEstado): boolean`; `reutilizadaJuntoASolicitud(fila: FilaEstado): boolean`.
  - `filaElegidaSinSku(fila: FilaEstado): boolean`; `filasElegidasSinSku(e: EstadoFormulario): FilaEstado[]`;
    `textoFaltaSku(e: EstadoFormulario): string | null`.
- Produces (en `test/acciones.ts`): `sumarOElegir(estado: EstadoFormulario, prefijo: string): AccionFormulario`.

- [ ] **Step 1: Ayudante de tests**

Create `src/modules/taller/formulario/test/acciones.ts`:

```ts
import type { AccionFormulario, EstadoFormulario } from '../estado'

/** «+» como lo pulsaban los tests de antes de la 0.9.19: si la fila está sin elegir, primero «Nueva» (que ya pone 1); si ya es
 *  Nueva, «+». Solo para arneses que reparten varios clics sobre la misma fila. */
export function sumarOElegir(estado: EstadoFormulario, prefijo: string): AccionFormulario {
  const fila = estado.filas.find((f) => f.prefijo === prefijo)
  return fila?.eleccion === 'nueva' ? { tipo: 'SUMAR', prefijo } : { tipo: 'ELEGIR', prefijo, eleccion: 'nueva' }
}
```

- [ ] **Step 2: Tests nuevos (fallan)**

Create `src/modules/taller/formulario/estado.eleccion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, componente, detalleEdicion, solicitudAsignacion } from '../test/fabrica'
import {
  type AccionFormulario, type DatosEditar, type DatosNuevo, type EstadoFormulario, type FilaEstado,
  botonNueva, estadoInicial, filaActiva, filaEditadaInvalida, filaElegidaSinSku, marcaPieza, muestraEleccion, planGuardarCambios,
  planTerminar, previsionStock, reducir, reutilizadaJuntoASolicitud, subFila, textoFaltaSku, zonaGuardarVisible,
} from './estado'

const IMEI = '355400000000111'
const datosNuevo = (parcial: Partial<DatosNuevo> = {}): DatosNuevo => ({
  modo: 'nuevo', idAsignacion: 'A20260916_1', imei: IMEI, agrupados: agrupados(), solicitudes: [], incidencia: null,
  modeloTelefono: null, ...parcial,
})
const datosEditar = (parcial: Partial<DatosEditar> = {}): DatosEditar => ({
  modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [], accionesYaReparadas: [],
  ...parcial,
})
const conModelo = (modelo: string, parcial: Partial<DatosNuevo> = {}) =>
  reducir(estadoInicial(datosNuevo(parcial)), { tipo: 'CAMBIAR_MODELO', modelo })
const aplicar = (e: EstadoFormulario, ...acciones: AccionFormulario[]) => acciones.reduce(reducir, e)
function fila(e: EstadoFormulario, prefijo: string): FilaEstado {
  const f = e.filas.find((x) => x.prefijo === prefijo)
  if (!f) throw new Error(`no hay fila ${prefijo}`)
  return f
}
const nueva = (prefijo: string): AccionFormulario => ({ tipo: 'ELEGIR', prefijo, eleccion: 'nueva' })
const reutilizada = (prefijo: string): AccionFormulario => ({ tipo: 'ELEGIR', prefijo, eleccion: 'reutilizada' })
const volver = (prefijo: string): AccionFormulario => ({ tipo: 'VOLVER', prefijo })

describe('0.9.19 · «Nueva» o «Reutilizada» en la celda del SKU (spec §2)', () => {
  it('una fila empieza sin elegir; «Nueva» pone 1 y enciende «+» y «−»', () => {
    const e = conModelo('13') // bati13: stock 5
    expect(fila(e, 'bat')).toMatchObject({ eleccion: null, cantidad: 0, reutilizado: false, idCom: 101 })
    expect(muestraEleccion(fila(e, 'bat'))).toBe(true)
    const n = reducir(e, nueva('bat'))
    expect(fila(n, 'bat')).toMatchObject({ eleccion: 'nueva', cantidad: 1, reutilizado: false })
    expect(fila(n, 'bat').controles).toMatchObject({ mas: true, menos: true, reutilizado: false })
    expect(filaActiva(fila(n, 'bat'))).toBe(true)
    expect(muestraEleccion(fila(n, 'bat'))).toBe(false)
    expect(fila(reducir(n, { tipo: 'SUMAR', prefijo: 'bat' }), 'bat').cantidad).toBe(2)
  })

  it('sin elegir o con «Reutilizada», «+» no suma', () => {
    const e = conModelo('13')
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(e)
    const r = reducir(e, reutilizada('bat'))
    expect(reducir(r, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(r)
  })

  it('«Reutilizada» no gasta: cantidad 0, sin «+» ni «−», y la fila cuenta', () => {
    const r = fila(reducir(conModelo('13'), reutilizada('bat')), 'bat')
    expect(r).toMatchObject({ eleccion: 'reutilizada', reutilizado: true, cantidad: 0 })
    expect(r.controles).toMatchObject({ mas: false, menos: false })
    expect(filaActiva(r)).toBe(true)
  })

  it('solo se elige sobre una fila sin elegir', () => {
    const n = reducir(conModelo('13'), nueva('bat'))
    expect(reducir(n, reutilizada('bat'))).toBe(n)
    expect(reducir(n, nueva('bat'))).toBe(n)
  })

  it('«−» hasta 0 y la «×» dejan la fila sin elegir y conservan el SKU', () => {
    const cero = aplicar(conModelo('13'), nueva('bat'), { tipo: 'RESTAR', prefijo: 'bat' })
    expect(fila(cero, 'bat')).toMatchObject({ eleccion: null, cantidad: 0, idCom: 101 })
    expect(fila(cero, 'bat').controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: true, observacion: true })
    const vuelta = aplicar(conModelo('13'), reutilizada('bat'), volver('bat'))
    expect(fila(vuelta, 'bat')).toMatchObject({ eleccion: null, reutilizado: false, cantidad: 0, idCom: 101 })
    expect(reducir(vuelta, volver('bat'))).toBe(vuelta) // sin elegir, la «×» no hace nada
  })

  it('las bandas de pedir solo salen con «Nueva»', () => {
    const e = conModelo('14') // bati14: stock 0, el único del modelo
    expect(subFila(e, fila(e, 'bat')).tipo).toBe('oculta')
    const r = reducir(e, reutilizada('bat'))
    expect(subFila(r, fila(r, 'bat')).tipo).toBe('oculta')
    const n = reducir(e, nueva('bat'))
    expect(fila(n, 'bat').cantidad).toBe(0)
    expect(subFila(n, fila(n, 'bat')).tipo).toBe('sinStock')
    const lcd = reducir(conModelo('13'), nueva('lcd')) // lcdi13: stock 1 → en el límite
    expect(subFila(lcd, fila(lcd, 'lcd'))).toEqual({ tipo: 'limite', stock: 1 })
  })

  it('con «Nueva», cambiar de SKU ajusta la cantidad sin pasar del stock', () => {
    const catalogo = agrupados()
    catalogo.bat = [componente({ idCom: 105, tipo: 'bati13negra', stock: 0 }), componente({ idCom: 101, tipo: 'bati13', stock: 5 }),
      componente({ idCom: 106, tipo: 'bati13alta', stock: 1 })]
    const dos = aplicar(conModelo('13', { agrupados: catalogo }), nueva('bat'), { tipo: 'SUMAR', prefijo: 'bat' })
    expect(fila(dos, 'bat')).toMatchObject({ idCom: 101, cantidad: 2 })
    const uno = reducir(dos, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 106 })
    expect(fila(uno, 'bat')).toMatchObject({ idCom: 106, cantidad: 1 })
    expect(fila(uno, 'bat').controles).toMatchObject({ mas: false, menos: true })
    const sin = reducir(uno, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 105 })
    expect(fila(sin, 'bat')).toMatchObject({ idCom: 105, cantidad: 0, eleccion: 'nueva' })
    expect(subFila(sin, fila(sin, 'bat')).tipo).toBe('sinStock')
    expect(fila(reducir(sin, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 }), 'bat').cantidad).toBe(1)
    // con «Reutilizada», cambiar de SKU no toca nada más
    const r = aplicar(conModelo('13', { agrupados: catalogo }), reutilizada('bat'), { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 105 })
    expect(fila(r, 'bat')).toMatchObject({ idCom: 105, eleccion: 'reutilizada', reutilizado: true, cantidad: 0 })
  })

  it('chasis: elegido sin color no cuenta y no deja terminar; con el color, «Nueva» pone 1', () => {
    const e = conModelo('13') // chai13negro: stock 2, sin preseleccionar
    expect(fila(e, 'cha').controles.reutilizado).toBe(true) // «Reutilizada» se puede elegir antes que el color
    const n = aplicar(e, nueva('cha'), nueva('bat'))
    expect(fila(n, 'cha')).toMatchObject({ eleccion: 'nueva', idCom: null, cantidad: 0 })
    expect(filaActiva(fila(n, 'cha'))).toBe(false)
    expect(filaElegidaSinSku(fila(n, 'cha'))).toBe(true)
    expect(textoFaltaSku(n)).toBe('Falta elegir el SKU de: Chasis.')
    expect(reducir(n, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' })).toBe(n)
    const conColor = reducir(n, { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 131 })
    expect(fila(conColor, 'cha')).toMatchObject({ idCom: 131, cantidad: 1 })
    expect(textoFaltaSku(conColor)).toBeNull()
    expect(reducir(conColor, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' }).guardado.clics).toBe(1)
  })

  it('chasis: «Reutilizada» antes del color no se envía hasta tener SKU, pero muestra la zona para poder avisar', () => {
    const r = reducir(conModelo('13'), reutilizada('cha'))
    expect(fila(r, 'cha')).toMatchObject({ eleccion: 'reutilizada', reutilizado: true, idCom: null })
    expect(filaActiva(fila(r, 'cha'))).toBe(false)
    expect(zonaGuardarVisible(r)).toBe(true)
    expect(planTerminar(r, 4).completa?.filas).toEqual([])
    const conColor = reducir(r, { tipo: 'CAMBIAR_SKU', prefijo: 'cha', idCom: 131 })
    expect(filaActiva(fila(conColor, 'cha'))).toBe(true)
    expect(fila(conColor, 'cha').cantidad).toBe(0)
  })

  it('botón de la izquierda: «Nueva» si algún SKU tiene stock; si ninguno, «Solicitar pieza»; en edición, deshabilitado', () => {
    const e13 = conModelo('13')
    expect(botonNueva(e13, fila(e13, 'bat'))).toBe('nueva')
    const e14 = conModelo('14')
    expect(botonNueva(e14, fila(e14, 'bat'))).toBe('solicitar')
    const catalogo = agrupados()
    catalogo.bat = [componente({ idCom: 101, tipo: 'bati13', stock: 0 })]
    catalogo.cam = [componente({ idCom: 121, tipo: 'cami13', stock: 0 })]
    const ed = estadoInicial(datosEditar({ agrupados: catalogo })) // bati13 nueva ×1; ya sin stock
    expect(botonNueva(ed, fila(ed, 'cam'))).toBe('deshabilitada')
    // la fila editada cuenta la unidad que devolvería su pieza original
    expect(botonNueva(ed, fila(ed, 'bat'))).toBe('nueva')
  })

  it('«Solicitar pieza» directo: la fila sin elegir queda Nueva y pedida; la «×» la cancela', () => {
    const e = conModelo('14')
    const pedida = reducir(e, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '  urge  ' })
    expect(fila(pedida, 'bat')).toMatchObject({ eleccion: 'nueva', cantidad: 0, agotado: { descripcion: 'urge', registrado: false } })
    expect(filaActiva(fila(pedida, 'bat'))).toBe(true)
    expect(marcaPieza(fila(pedida, 'bat'))).toEqual({ tipo: 'solicitada', quitar: true })
    expect(subFila(pedida, fila(pedida, 'bat')).tipo).toBe('confirmada')
    expect(fila(reducir(pedida, volver('bat')), 'bat')).toMatchObject({ eleccion: null, agotado: null, cantidad: 0 })
    // «Cancelar solicitud» de la banda hace lo mismo
    expect(fila(reducir(pedida, { tipo: 'CANCELAR_AGOTADO', prefijo: 'bat' }), 'bat')).toMatchObject({ eleccion: null, agotado: null })
    // con stock no hay «Solicitar pieza» directo
    const con = conModelo('13')
    expect(reducir(con, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' })).toBe(con)
    // registrada en el servidor, ya no se quita
    const registrada = reducir(pedida, { tipo: 'AGOTADO_REGISTRADO', prefijo: 'bat' })
    expect(reducir(registrada, volver('bat'))).toBe(registrada)
    expect(marcaPieza(fila(registrada, 'bat'))?.quitar).toBe(false)
  })

  it('marca de la columna «Pieza»', () => {
    const e = conModelo('13')
    expect(marcaPieza(fila(e, 'bat'))).toBeNull()
    expect(marcaPieza(fila(reducir(e, nueva('bat')), 'bat'))).toEqual({ tipo: 'nueva', quitar: true })
    expect(marcaPieza(fila(reducir(e, reutilizada('bat')), 'bat'))).toEqual({ tipo: 'reutilizada', quitar: true })
    const guardada = aplicar(e, nueva('bat'), { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' })
    expect(marcaPieza(fila(guardada, 'bat'))).toEqual({ tipo: 'nueva', quitar: false })
    expect(muestraEleccion(fila(guardada, 'bat'))).toBe(false)
    expect(reducir(guardada, volver('bat'))).toBe(guardada)
  })

  it('solicitud guardada pendiente: sin marca, «↺ Reutilizada» al lado y vuelta con la «×»; en camino, nada', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion()] })) // pendiente de bati14 → modelo 14
    const bat = fila(e, 'bat')
    expect(bat.solicitud).toEqual({ estado: 'pendiente', descripcion: null })
    expect(muestraEleccion(bat)).toBe(false)
    expect(marcaPieza(bat)).toBeNull()
    expect(reutilizadaJuntoASolicitud(bat)).toBe(true)
    expect(reducir(e, nueva('bat'))).toBe(e)
    const r = reducir(e, reutilizada('bat'))
    expect(fila(r, 'bat')).toMatchObject({ eleccion: 'reutilizada', reutilizado: true })
    expect(filaActiva(fila(r, 'bat'))).toBe(true)
    expect(reutilizadaJuntoASolicitud(fila(r, 'bat'))).toBe(false)
    expect(marcaPieza(fila(r, 'bat'))).toEqual({ tipo: 'reutilizada', quitar: true })
    const vuelta = reducir(r, volver('bat'))
    expect(fila(vuelta, 'bat')).toMatchObject({ eleccion: null, reutilizado: false })
    expect(fila(vuelta, 'bat').controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: false, observacion: true })
    const enCamino = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ enCamino: true })] }))
    expect(reutilizadaJuntoASolicitud(fila(enCamino, 'bat'))).toBe(false)
    expect(reducir(enCamino, reutilizada('bat'))).toBe(enCamino)
  })

  it('«Stock» de una Nueva sin guardar enseña lo que quedará', () => {
    const n = reducir(conModelo('13'), nueva('bat'))
    expect(previsionStock(n, fila(n, 'bat'))).toEqual({ texto: '5 → 4', tendencia: 'baja' })
    const r = reducir(conModelo('13'), reutilizada('bat'))
    expect(previsionStock(r, fila(r, 'bat'))).toBeNull()
  })

  it('edición: la fila arranca elegida según lo guardado y con la «×» cambia de tipo', () => {
    const e = estadoInicial(datosEditar()) // bati13 nueva ×1, stock 5
    expect(fila(e, 'bat')).toMatchObject({ rol: 'editada', eleccion: 'nueva', cantidad: 1 })
    const sinElegir = reducir(e, volver('bat'))
    expect(filaEditadaInvalida(sinElegir)).toBe(true)
    expect(zonaGuardarVisible(sinElegir)).toBe(false)
    const r = reducir(sinElegir, reutilizada('bat'))
    expect(filaEditadaInvalida(r)).toBe(false)
    expect(zonaGuardarVisible(r)).toBe(true)
    expect(previsionStock(r, fila(r, 'bat'))).toEqual({ texto: '5 → 6', tendencia: 'sube' })
    expect(planGuardarCambios(r).editarFila).toMatchObject({ idComNuevo: 101, esReutilizadoNuevo: true, nNuevas: 0 })
    const reu = estadoInicial(datosEditar({ detalle: detalleEdicion({ esReutilizado: true, cantidad: 0 }) }))
    expect(fila(reu, 'bat')).toMatchObject({ eleccion: 'reutilizada', reutilizado: true })
  })

  it('cambiar de modelo deja las filas sin elegir', () => {
    const e = aplicar(conModelo('13'), nueva('bat'), { tipo: 'CAMBIAR_MODELO', modelo: '14' })
    expect(fila(e, 'bat')).toMatchObject({ eleccion: null, cantidad: 0 })
  })
})
```

- [ ] **Step 3: Ver que fallan**

Run: `npx vitest run src/modules/taller/formulario/estado.eleccion.test.ts`
Expected: FAIL (no existen `ELEGIR`, `eleccion`, `botonNueva`… ni el resto de exportaciones).

- [ ] **Step 4: Tipos y acciones**

En `src/modules/taller/formulario/estado.ts`:

1. Tras `export type OriginalEdicion = …` (línea 25), añadir:

```ts
/** Lo que se eligió en la celda del SKU (spec 0.9.19 §2): «Nueva» gasta del stock, «Reutilizada» no; null = sin elegir (la
 *  celda enseña los dos botones). */
export type Eleccion = 'nueva' | 'reutilizada' | null
```

2. Cambiar el comentario de `ControlesFila` (líneas 27-29) por:

```ts
/** Habilitado de los controles, mantenido por el reductor paso a paso (no se deriva). `reutilizado` = se puede elegir
 *  «Reutilizada» (desde la 0.9.19 ya no hay casilla); «+» y «−» solo cuentan con «Nueva». Una fila sin SKU para el modelo
 *  (opciones vacías) se pinta siempre deshabilitada y atenuada, diga lo que diga este campo. */
```

3. En `FilaEstado`, tras `reutilizado: boolean`, añadir `eleccion: Eleccion`.

4. En `AccionFormulario`, sustituir la línea de `MARCAR_REUTILIZADO` por:

```ts
  | { tipo: 'ELEGIR'; prefijo: string; eleccion: 'nueva' | 'reutilizada' } // celda del SKU (0.9.19)
  | { tipo: 'VOLVER'; prefijo: string } // «×» de la marca: la fila vuelve sin elegir, con su SKU (0.9.19)
  | { tipo: 'MARCAR_REUTILIZADO'; prefijo: string; valor: boolean } // alias temporal: true = ELEGIR reutilizada, false = VOLVER
```

- [ ] **Step 5: Construcción de filas**

1. Sustituir `CONTROLES_SOLO_SKU` (líneas 173-174) por:

```ts
/** Chasis o tapa con opciones pero sin SKU elegido: el combo y «Reutilizada» (se puede elegir antes que el color; spec 0.9.7
 *  §9 y 0.9.19 §2). */
const CONTROLES_SOLO_SKU: ControlesFila = { ...CONTROLES_APAGADOS, reutilizado: true, sku: true }
/** Fila con SKU y sin elegir (0.9.19): los dos botones; «+» y «−» esperan a «Nueva». */
const CONTROLES_SIN_ELEGIR: ControlesFila = { mas: false, menos: false, reutilizado: true, sku: true, observacion: true }
```

2. En `filaLimpia`, tras `reutilizado: false,` añadir `eleccion: null,`.

3. En `aplicarSolicitud`, dentro de `normal`, tras `reutilizado: false,` añadir `eleccion: null,`.

4. Sustituir `filaEditada` entera por:

```ts
/** Fila en edición: SKU, cantidad, «Reutilizado» y observación originales, y la elección que les corresponde (0.9.19): con
 *  la «×» se puede cambiar de nueva a reutilizada y al revés. «+» y «−» solo con «Nueva»; «+» apagado con stock ≤ 0. */
function filaEditada(base: BaseFila, c: Componente, modelo: string | null, detalle: DetalleEdicion): FilaEstado {
  const skus = base.skus.some((s) => s.idCom === c.idCom) ? base.skus : [...base.skus, c]
  const limpia = filaLimpia({ prefijo: base.prefijo, nombre: base.nombre, skus }, modelo)
  const opciones = limpia.opciones.some((o) => o.idCom === c.idCom) ? limpia.opciones : [...limpia.opciones, c]
  const cantidad = Math.max(0, detalle.cantidad)
  const reutilizado = detalle.esReutilizado
  const observacion = textoONull(detalle.observacion)
  const eleccion: Eleccion = reutilizado ? 'reutilizada' : cantidad > 0 ? 'nueva' : null
  return {
    ...limpia,
    skus,
    opciones,
    idCom: c.idCom,
    cantidad,
    reutilizado,
    eleccion,
    observacion,
    controles: {
      mas: eleccion === 'nueva' && c.stock > 0,
      menos: eleccion === 'nueva' && cantidad > 0,
      reutilizado: true,
      sku: true,
      observacion: true,
    },
    rol: 'editada',
    original: { idCom: c.idCom, cantidad, reutilizado, observacion },
  }
}
```

5. Sustituir `filaActiva` por:

```ts
/** Fila que se enviará: con SKU y con pieza (nueva, reutilizada o solicitud local). Una fila elegida sin SKU no cuenta. */
export function filaActiva(fila: FilaEstado): boolean {
  return fila.idCom !== null && (fila.cantidad > 0 || fila.reutilizado || (fila.agotado !== null && fila.solicitud === null))
}
```

- [ ] **Step 6: Reglas de la fila**

En el bloque «Reductor» de `estado.ts`:

1. En `sumar`, cambiar la condición de salida por:

```ts
  if (bloqueada(fila) || fila.eleccion !== 'nueva' || fila.solicitud !== null || c === null || !fila.controles.mas || fila.cantidad >= c.stock) return null
```

2. Sustituir `restar` por:

```ts
function restar(fila: FilaEstado): FilaEstado | null {
  const c = componenteDe(fila)
  if (bloqueada(fila) || fila.solicitud !== null || c === null || !fila.controles.menos || fila.cantidad <= 0) return null
  const cantidad = fila.cantidad - 1
  // Una Nueva que baja a 0 vuelve sin elegir con su SKU (spec 0.9.19 §2, «Volver atrás»).
  if (cantidad === 0 && fila.eleccion === 'nueva') return { ...fila, cantidad: 0, eleccion: null, controles: CONTROLES_SIN_ELEGIR }
  return {
    ...fila,
    cantidad,
    controles: {
      ...fila.controles,
      mas: cantidad < c.stock,
      menos: cantidad > 0,
      reutilizado: cantidad === 0 ? true : fila.controles.reutilizado,
    },
  }
}
```

3. Sustituir `cambiarSku` por (y añadir antes los dos ayudantes):

```ts
/** Stock que puede gastar una Nueva: el del SKU y, en la fila editada con su SKU original nuevo, también lo que devolvería. */
function stockParaNueva(fila: FilaEstado, c: Componente): number {
  const o = fila.original
  const devuelve = fila.rol === 'editada' && o !== null && o.idCom === c.idCom && !o.reutilizado ? o.cantidad : 0
  return c.stock + devuelve
}

/** Cantidad de una Nueva tras elegirla o cambiar de SKU: la que tenía (mínimo 1) sin pasar del stock; 0 si no hay stock. */
function cantidadNueva(fila: FilaEstado, c: Componente): number {
  const stock = stockParaNueva(fila, c)
  return stock <= 0 ? 0 : Math.min(Math.max(fila.cantidad, 1), stock)
}

function cambiarSku(fila: FilaEstado, idCom: number): FilaEstado | null {
  const c = fila.opciones.find((o) => o.idCom === idCom)
  if (bloqueada(fila) || fila.solicitud !== null || !fila.controles.sku || c === undefined || idCom === fila.idCom) return null
  if (fila.eleccion === 'nueva') {
    // Nueva (spec 0.9.19 §2): 1 si el SKU tiene stock (o la que tenía, sin pasarse); 0 sin stock, con la banda de pedirla.
    // En la fila editada, volver a su SKU original conserva la cantidad.
    const esElOriginal = fila.original !== null && fila.original.idCom === idCom
    const cantidad = esElOriginal && fila.cantidad > 0 ? fila.cantidad : cantidadNueva(fila, c)
    return { ...fila, idCom, cantidad, controles: { ...fila.controles, mas: cantidad < c.stock, menos: cantidad > 0 } }
  }
  // Sin elegir o Reutilizada: solo cambia el SKU. La primera elección de chasis o tapa enciende la observación.
  if (fila.idCom === null) return { ...fila, idCom, controles: CONTROLES_SIN_ELEGIR }
  return { ...fila, idCom }
}
```

4. Sustituir `marcarReutilizado` por `elegir` y `volver`:

```ts
/** «+ Nueva» / «↺ Reutilizada» de la celda del SKU (spec 0.9.19 §2), solo sobre una fila sin elegir. «Nueva» pone 1 si el SKU
 *  tiene stock (0 sin stock o, en chasis y tapa, hasta elegir el color). «Reutilizada» no gasta y, con una solicitud guardada,
 *  solo vale mientras no esté en camino (`controles.reutilizado`). */
function elegir(fila: FilaEstado, eleccion: 'nueva' | 'reutilizada'): FilaEstado | null {
  if (bloqueada(fila) || fila.eleccion !== null) return null
  if (eleccion === 'reutilizada') {
    if (!fila.controles.reutilizado) return null
    return { ...fila, eleccion, reutilizado: true, cantidad: 0, controles: { ...fila.controles, mas: false, menos: false } }
  }
  if (fila.solicitud !== null) return null
  const c = componenteDe(fila)
  const cantidad = c === null ? 0 : cantidadNueva({ ...fila, cantidad: 0 }, c)
  return {
    ...fila,
    eleccion,
    reutilizado: false,
    cantidad,
    controles: { ...fila.controles, mas: c !== null && cantidad < c.stock, menos: cantidad > 0, reutilizado: false },
  }
}

/** «×» de la marca (spec 0.9.19 §2): la fila vuelve sin elegir con su SKU; con una solicitud local sin registrar, la cancela.
 *  Una guardada, en vuelo, ya reparada o con la solicitud ya registrada en el servidor no vuelve. */
function volver(fila: FilaEstado): FilaEstado | null {
  if (fila.eleccion === null || fila.guardada !== null || fila.guardando || fila.rol === 'yaReparado') return null
  if (fila.agotado !== null && fila.agotado.registrado) return null
  const controles: ControlesFila =
    fila.solicitud !== null
      ? { mas: false, menos: false, reutilizado: fila.solicitud.estado !== 'enCamino', sku: false, observacion: true }
      : fila.idCom === null
        ? CONTROLES_SOLO_SKU
        : CONTROLES_SIN_ELEGIR
  return { ...fila, eleccion: null, cantidad: 0, reutilizado: false, agotado: null, controles }
}
```

- [ ] **Step 7: Solicitudes locales y bandas**

1. Sustituir `confirmarAgotado` por:

```ts
/** Confirmar es LOCAL: bloquea la fila, borra su observación y deja el contador en lo que se enviará a agotar-componente
 *  (0 en "sin stock", el stock en "límite"). Procede si la banda ofrece alguna de las dos variantes o, sobre una fila sin
 *  elegir cuyo botón es «Solicitar pieza» (spec 0.9.19 §2), como si fuera Nueva. Deja la fila en «Nueva». */
function confirmarAgotado(estado: EstadoFormulario, prefijo: string, descripcion: string): EstadoFormulario {
  return cambiarFila(estado, prefijo, (fila) => {
    const directa = fila.eleccion === null && !bloqueada(fila) && botonNueva(estado, fila) === 'solicitar'
    const base: FilaEstado = directa ? { ...fila, eleccion: 'nueva' } : fila
    const variante = subFila(estado, base)
    if (fila.guardando || (variante.tipo !== 'sinStock' && variante.tipo !== 'limite')) return null
    return {
      ...base,
      cantidad: variante.tipo === 'limite' ? variante.stock : 0,
      reutilizado: false,
      observacion: null,
      agotado: { descripcion: textoONull(descripcion), registrado: false },
      controles: CONTROLES_APAGADOS,
    }
  })
}
```

2. Sustituir `cancelarAgotado` por:

```ts
/** «Cancelar solicitud» de la banda (solicitud local sin registrar): como la «×», la fila vuelve sin elegir con su SKU. */
function cancelarAgotado(estado: EstadoFormulario, prefijo: string): EstadoFormulario {
  return cambiarFila(estado, prefijo, (fila) =>
    fila.agotado === null || fila.agotado.registrado ? null : volver({ ...fila, eleccion: fila.eleccion ?? 'nueva' }),
  )
}
```

3. En `subFila`, sustituir las tres últimas líneas (`if (stock === 0) …`, `if (!fila.reutilizado && …)`, `return { tipo: 'oculta' }`)
   por:

```ts
  // Las bandas de pedir solo salen con «Nueva» (spec 0.9.19 §2): sin elegir o Reutilizada, nunca.
  if (fila.eleccion !== 'nueva') return { tipo: 'oculta' }
  if (stock === 0) return { tipo: 'sinStock' }
  if (fila.cantidad >= stock) return { tipo: 'limite', stock }
  return { tipo: 'oculta' }
```

4. En `desbloquearBorradas`, sustituir el `return { … }` de las filas por:

```ts
    return {
      ...fila, guardada: null, cantidad: 0, reutilizado: false, eleccion: null, observacion: null, confirmandoGuardar: false,
      guardando: false, controles: fila.idCom === null ? CONTROLES_SOLO_SKU : CONTROLES_SIN_ELEGIR,
    }
```

- [ ] **Step 8: Selectores nuevos, previsión, zona y reductor**

1. Tras `chasisResaltados`, añadir:

```ts
// ───────────────────────────── Selectores: elección (0.9.19) ─────────────────────────────

export type BotonNueva = 'nueva' | 'solicitar' | 'deshabilitada'

/** Botón de la izquierda de la celda del SKU (spec 0.9.19 §2): «+ Nueva» si algún SKU de la fila tiene stock (en la fila
 *  editada cuenta lo que devolvería su pieza original); si ninguno, «Solicitar pieza» (en edición no se pide: deshabilitado). */
export function botonNueva(e: EstadoFormulario, fila: FilaEstado): BotonNueva {
  if (fila.opciones.some((c) => stockParaNueva(fila, c) > 0)) return 'nueva'
  return e.modo === 'editar' ? 'deshabilitada' : 'solicitar'
}

/** La celda del SKU enseña los dos botones: fila sin elegir, sin solicitud guardada, ni guardada ni ya reparada. */
export function muestraEleccion(fila: FilaEstado): boolean {
  return fila.eleccion === null && fila.solicitud === null && fila.guardada === null && fila.rol !== 'yaReparado'
}

export type MarcaPieza = { tipo: 'nueva' | 'reutilizada' | 'solicitada'; quitar: boolean }

/** Marca de la columna «Pieza». `quitar` = lleva «×» (no en guardadas, en vuelo, ya reparadas ni con la solicitud registrada). */
export function marcaPieza(fila: FilaEstado): MarcaPieza | null {
  const quitar = fila.guardada === null && !fila.guardando && fila.rol !== 'yaReparado' && fila.agotado?.registrado !== true
  if (fila.eleccion === 'reutilizada') return { tipo: 'reutilizada', quitar }
  if (fila.eleccion === 'nueva') return { tipo: fila.agotado !== null && fila.cantidad === 0 ? 'solicitada' : 'nueva', quitar }
  return null
}

/** «↺ Reutilizada» en «Pieza» de una solicitud guardada pendiente (sustituye a la casilla de antes; en camino, no). */
export function reutilizadaJuntoASolicitud(fila: FilaEstado): boolean {
  return fila.solicitud !== null && fila.eleccion === null && fila.controles.reutilizado && !bloqueada(fila)
}

/** Elección hecha y sin SKU (chasis o tapa sin color): no deja terminar. */
export function filaElegidaSinSku(fila: FilaEstado): boolean {
  return fila.guardada === null && fila.eleccion !== null && fila.idCom === null
}

export function filasElegidasSinSku(e: EstadoFormulario): FilaEstado[] {
  return e.filas.filter(filaElegidaSinSku)
}

/** Aviso de «Terminar asignación» / «Guardar cambios», o null si no falta ningún SKU. */
export function textoFaltaSku(e: EstadoFormulario): string | null {
  const faltan = filasElegidasSinSku(e)
  return faltan.length === 0 ? null : `Falta elegir el SKU de: ${faltan.map((f) => f.nombre).join(', ')}.`
}
```

2. Sustituir `previsionStock` (y su comentario) por:

```ts
/** "<stock> → <previsto>". Fila editada: previsto = stock + devuelto − descontado (devuelto = la cantidad original si el SKU
 *  sigue siendo el original y no era reutilizada; descontado = 0 con "Reutilizado", si no la cantidad). Nueva sin guardar
 *  (0.9.19): lo que quedará al guardar, stock − cantidad. */
export function previsionStock(e: EstadoFormulario, fila: FilaEstado): PrevisionStock | null {
  const stock = stockDe(fila)
  if (stock === null) return null
  if (e.modo === 'editar' && fila.rol === 'editada' && fila.original !== null) {
    const devuelto = fila.idCom === fila.original.idCom && !fila.original.reutilizado ? fila.original.cantidad : 0
    const descontado = fila.reutilizado ? 0 : fila.cantidad
    const previsto = stock + devuelto - descontado
    return { texto: `${stock} → ${previsto}`, tendencia: previsto < stock ? 'baja' : previsto > stock ? 'sube' : 'igual' }
  }
  if (fila.rol === 'normal' && fila.guardada === null && fila.eleccion === 'nueva' && fila.cantidad > 0) {
    return { texto: `${stock} → ${stock - fila.cantidad}`, tendencia: 'baja' }
  }
  return null
}
```

3. En `zonaGuardarVisible`, cambiar `e.filas.some((f) => f.guardada !== null || filaActiva(f))` por
   `e.filas.some((f) => f.guardada !== null || filaActiva(f) || filaElegidaSinSku(f))`.

4. En `zonaVisibleEnEdicion`, sustituir el `return ( … )` por:

```ts
  return (
    hayCambioEnFilaEditada(e) || hayCambioEnAccionEditada(e) || filasNuevasEnEdicion(e).length > 0 || accionesPendientes(e).length > 0 ||
    e.filas.some((f) => f.rol === 'normal' && filaElegidaSinSku(f))
  )
```

5. En `pedirConfirmacionGuardar`, cambiar la condición por
   `if (!zonaGuardarVisible(estado) || estado.guardado.enCurso || estado.guardado.clics === 1 || textoFaltaSku(estado) !== null) return estado`.

6. En `reducir`, sustituir el `case 'MARCAR_REUTILIZADO':` y su `return` por:

```ts
    case 'ELEGIR':
      return cambiarFila(estado, accion.prefijo, (f) => elegir(f, accion.eleccion))
    case 'VOLVER':
      return cambiarFila(estado, accion.prefijo, volver)
    case 'MARCAR_REUTILIZADO': // alias temporal (lo quita la Task 4)
      return cambiarFila(estado, accion.prefijo, (f) => (accion.valor ? elegir(f, 'reutilizada') : volver(f)))
```

- [ ] **Step 9: Ver que pasan los nuevos**

Run: `npx vitest run src/modules/taller/formulario/estado.eleccion.test.ts`
Expected: PASS (16 tests).

- [ ] **Step 10: Adaptar los tests de lógica**

Run: `npx vitest run src/modules/taller/formulario/estado src/modules/taller/formulario/borrador.test.ts src/modules/taller/formulario/useGuardado.test.tsx src/modules/taller/formulario/useBorrador.test.tsx`

Arreglar cada fallo según «Cómo se adaptan los tests de antes» (traducción mecánica o cambio a propósito 1-11). En
`useGuardado.test.tsx` y `useBorrador.test.tsx`, los botones o `dispatch` de «sumar» de los arneses pasan a
`sumarOElegir(estado, P)` (importado de `./test/acciones`; en `useBorrador.test.tsx`, con el `estado` que devuelve el arnés,
`result.current.estado`). Expected al final: PASS en esos ficheros.

- [ ] **Step 11: Typecheck y lint**

Run: `npm run typecheck && npm run lint`
Expected: sin errores (la interfaz aún usa `MARCAR_REUTILIZADO`, que sigue existiendo).

- [ ] **Step 12: Commit**

```bash
git add src/modules/taller/formulario
git commit -m "feat: eleccion nueva o reutilizada en cada fila del formulario (reductor, 0.9.19)"
```

---

### Task 3: Borrador — deducir la elección

**Files:**
- Modify: `src/modules/taller/formulario/borrador.ts`
- Test: `src/modules/taller/formulario/borrador.test.ts`

**Interfaces:**
- Consumes: `Eleccion` y `FilaEstado.eleccion` (Task 2).
- Produces: `aplicarBorrador` deja cada fila con su `eleccion`; `capturar` no escribe filas elegidas sin SKU. El JSON no cambia.

- [ ] **Step 1: Tests (fallan)**

Al final de `src/modules/taller/formulario/borrador.test.ts`, añadir:

```ts
describe('borrador: la elección se deduce al recuperarlo (0.9.19)', () => {
  it('reutilizado → Reutilizada; cantidad → Nueva; solo observación → sin elegir', () => {
    const b = contenido({
      modelo: '13',
      filas: [
        filaBorrador({ prefijo: 'bat', idCom: 101, reutilizado: true }),
        filaBorrador({ prefijo: 'lcd', idCom: 111, cantidad: 1 }),
        filaBorrador({ prefijo: 'cam', idCom: 121, observacion: 'Mancha' }),
      ],
    })
    const e = aplicarBorrador(estadoInicial(datos()), b)
    expect(fila(e, 'bat')).toMatchObject({ eleccion: 'reutilizada', reutilizado: true, cantidad: 0 })
    expect(fila(e, 'lcd')).toMatchObject({ eleccion: 'nueva', cantidad: 1 })
    expect(fila(e, 'cam')).toMatchObject({ eleccion: null, observacion: 'Mancha' })
  })

  it('agotado confirmado → Nueva pedida; guardada → su elección', () => {
    const pedida = aplicarBorrador(estadoInicial(datos()), contenido({
      modelo: '14', filas: [filaBorrador({ prefijo: 'bat', idCom: 102, agotadoConfirmado: true, descripcionAgotado: 'urge' })],
    }))
    expect(fila(pedida, 'bat')).toMatchObject({ eleccion: 'nueva', cantidad: 0, agotado: { descripcion: 'urge', registrado: false } })
    const guardadas = aplicarBorrador(estadoInicial(datos()), contenido({
      modelo: '13',
      filas: [
        filaBorrador({ prefijo: 'bat', idCom: 101, reutilizado: true, guardada: true, idRepGenerado: 'R20260916_9', fechaGuardado: '16/09 09:15' }),
        filaBorrador({ prefijo: 'lcd', idCom: 111, cantidad: 1, guardada: true, idRepGenerado: 'R20260916_8', fechaGuardado: '16/09 09:10' }),
      ],
    }))
    expect(fila(guardadas, 'bat')).toMatchObject({ eleccion: 'reutilizada', guardada: { idRep: 'R20260916_9', fecha: '16/09 09:15' } })
    expect(fila(guardadas, 'lcd')).toMatchObject({ eleccion: 'nueva', cantidad: 1 })
  })

  it('una fila elegida sin SKU no llega al borrador', () => {
    const e = aplicarAcciones(conModelo13(), { tipo: 'ELEGIR', prefijo: 'cha', eleccion: 'reutilizada' })
    expect(capturar(e).filas).toEqual([])
  })
})
```

Run: `npx vitest run src/modules/taller/formulario/borrador.test.ts`
Expected: FAIL en los tres (la elección queda en null y el chasis reutilizado sin SKU se captura con `idCom: -1`).

- [ ] **Step 2: Implementación**

En `src/modules/taller/formulario/borrador.ts`:

1. Import: añadir `type Eleccion` a la lista de `./estado`.

2. En `capturarFila`, tras el bloque de la fila guardada y antes de `const agotadoNuevo = …`, añadir:

```ts
  // Una fila elegida sin SKU (chasis o tapa sin color) no se guarda: no hay pieza a la que atarla (spec 0.9.19 §2).
  if (fila.idCom === null) return null
```

3. En `aplicarFila`:
   - rama `if (f.guardada)`: tras `reutilizado: f.reutilizado || base.reutilizado,` añadir
     `eleccion: f.reutilizado || base.reutilizado ? 'reutilizada' : 'nueva',`;
   - rama `if (f.agotadoConfirmado)`: tras `reutilizado: false,` añadir `eleccion: 'nueva',`;
   - rama normal: sustituir desde `const cantidad = Math.max(0, f.cantidad)` hasta el `return` final por:

```ts
  const cantidad = Math.max(0, f.cantidad)
  const observacion = f.observacion !== undefined && f.observacion.trim() !== '' ? f.observacion : base.observacion
  // La elección se deduce (el JSON es el del JavaFX y no la guarda): reutilizado → Reutilizada; cantidad → Nueva.
  const eleccion: Eleccion = f.reutilizado ? 'reutilizada' : cantidad > 0 ? 'nueva' : null
  return {
    ...base, reutilizado: f.reutilizado, eleccion, cantidad, observacion, controles: { ...base.controles, menos: cantidad > 0 },
    confirmandoGuardar: false, recibidoPendienteUso: base.recibidoPendienteUso && cantidad === 0 && !f.reutilizado,
  }
```

- [ ] **Step 3: Ver que pasan**

Run: `npx vitest run src/modules/taller/formulario/borrador.test.ts src/modules/taller/formulario/useBorrador.test.tsx src/modules/taller/formulario/estado`
Expected: PASS (el ida y vuelta con `BORRADOR_JAVAFX` sigue dando la misma cadena).

- [ ] **Step 4: Commit**

```bash
git add src/modules/taller/formulario/borrador.ts src/modules/taller/formulario/borrador.test.ts
git commit -m "feat: el borrador recupera la eleccion nueva o reutilizada de cada fila (0.9.19)"
```

---

### Task 4: Interfaz — celda del SKU, columna «Pieza» y «Solicitar pieza» directo

**Files:**
- Modify: `src/modules/taller/formulario/FilaComponente.tsx`
- Modify: `src/modules/taller/formulario/FormularioReparacion.tsx`
- Modify: `src/modules/taller/formulario/estado.ts` (quitar el alias `MARCAR_REUTILIZADO`)
- Test: `src/modules/taller/formulario/FilaComponente.test.tsx` (tests nuevos + adaptar)
- Modify (adaptar): `FormularioReparacion.test.tsx`, `SubFilaAgotado.test.tsx`, `rutas.test.tsx`, `api.test.tsx`,
  `src/modules/taller/imeis/ImeiDetallePage.test.tsx`, y los tests de lógica que aún usen `MARCAR_REUTILIZADO`

**Interfaces:**
- Consumes: `botonNueva`, `marcaPieza`, `MarcaPieza`, `muestraEleccion`, `reutilizadaJuntoASolicitud`, `previsionStock` y las
  acciones `ELEGIR`, `VOLVER`, `CONFIRMAR_AGOTADO` (Task 2); `DialogoSolicitarPieza` (ya existe:
  `{ abierto, tipo, variante: 'sinStock' | 'limite', stock, inicial, onConfirmar(descripcion), onCancelar }`).
- Produces: `data-testid` `marca-<prefijo>`; nombres accesibles «Nueva <tipo>», «Reutilizada <tipo>», «Solicitar <tipo>» y
  «Volver atrás <tipo>».

- [ ] **Step 1: Tests nuevos (fallan)**

Al final de `src/modules/taller/formulario/FilaComponente.test.tsx`, añadir:

```tsx
describe('0.9.19 · la elección en la celda del SKU (spec §2)', () => {
  it('sin elegir: «+ Nueva» y «↺ Reutilizada» en lugar del desplegable; contador apagado y sin marca', () => {
    montar([MODELO_13])
    const bat = within(screen.getByTestId('fila-bat'))
    expect(bat.queryByRole('combobox', { name: 'SKU de Batería' })).not.toBeInTheDocument()
    expect(bat.getByRole('button', { name: 'Nueva Batería' })).toHaveTextContent('+ Nueva')
    expect(bat.getByRole('button', { name: 'Reutilizada Batería' })).toHaveTextContent('↺ Reutilizada')
    expect(bat.getByRole('button', { name: 'Sumar Batería' })).toBeDisabled()
    expect(screen.queryByTestId('marca-bat')).not.toBeInTheDocument()
  })

  it('«Nueva»: desplegable, contador en 1, «5 → 4» y marca con «×» que vuelve atrás', async () => {
    montar([MODELO_13])
    const bat = within(screen.getByTestId('fila-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Nueva Batería' }))
    expect(bat.getByRole('combobox', { name: 'SKU de Batería' })).toHaveTextContent('bati13')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('1')
    expect(screen.getByTestId('stock-bat')).toHaveTextContent('5 → 4')
    expect(screen.getByTestId('marca-bat')).toHaveTextContent('+ Nueva')
    expect(bat.getByRole('button', { name: 'Sumar Batería' })).toBeEnabled()
    await userEvent.click(bat.getByRole('button', { name: 'Volver atrás Batería' }))
    expect(bat.getByRole('button', { name: 'Nueva Batería' })).toBeInTheDocument()
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
  })

  it('«Reutilizada»: contador «—», sin «+», stock sin flecha y marca «↺ Reutilizada»', async () => {
    montar([MODELO_13])
    const bat = within(screen.getByTestId('fila-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Reutilizada Batería' }))
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('—')
    expect(bat.getByRole('button', { name: 'Sumar Batería' })).toBeDisabled()
    expect(screen.getByTestId('stock-bat')).toHaveTextContent(/^5$/)
    expect(screen.getByTestId('marca-bat')).toHaveTextContent('↺ Reutilizada')
  })

  it('ningún SKU con stock: «Solicitar pieza» abre el diálogo y la fila queda «Solicitada»; cancelar no cambia nada', async () => {
    montar([MODELO_14]) // bati14: stock 0, el único del modelo
    const bat = within(screen.getByTestId('fila-bat'))
    expect(bat.queryByRole('button', { name: 'Nueva Batería' })).not.toBeInTheDocument()
    await userEvent.click(bat.getByRole('button', { name: 'Solicitar Batería' }))
    let dlg = within(await screen.findByRole('dialog', { name: 'Solicitar pieza — Batería' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Cancelar' }))
    expect(bat.getByRole('button', { name: 'Solicitar Batería' })).toBeInTheDocument()
    await userEvent.click(bat.getByRole('button', { name: 'Solicitar Batería' }))
    dlg = within(await screen.findByRole('dialog', { name: 'Solicitar pieza — Batería' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar: solicitar pieza' }))
    expect(screen.getByTestId('marca-bat')).toHaveTextContent('Solicitada')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
  })

  it('solicitud guardada pendiente: SKU fijo y «↺ Reutilizada» en «Pieza»; en camino, sin botón', async () => {
    montar([], [solicitudAsignacion()]) // pendiente de bati14 → modelo 14
    const bat = within(screen.getByTestId('fila-bat'))
    expect(bat.getByRole('combobox', { name: 'SKU de Batería' })).toBeDisabled()
    expect(screen.queryByTestId('marca-bat')).not.toBeInTheDocument()
    await userEvent.click(bat.getByRole('button', { name: 'Reutilizada Batería' }))
    expect(screen.getByTestId('marca-bat')).toHaveTextContent('↺ Reutilizada')
    await userEvent.click(bat.getByRole('button', { name: 'Volver atrás Batería' }))
    expect(bat.getByRole('button', { name: 'Reutilizada Batería' })).toBeInTheDocument()
  })

  it('solicitud en camino: sin «↺ Reutilizada»', () => {
    montar([], [solicitudAsignacion({ enCamino: true })])
    expect(within(screen.getByTestId('fila-bat')).queryByRole('button', { name: 'Reutilizada Batería' })).not.toBeInTheDocument()
  })

  it('chasis: «Nueva» enseña «— Elige color —»; al elegirlo, el contador pasa a 1', async () => {
    montar([MODELO_13])
    const cha = within(screen.getByTestId('fila-cha'))
    await userEvent.click(cha.getByRole('button', { name: 'Nueva Chasis' }))
    const combo = cha.getByRole('combobox', { name: 'SKU de Chasis' })
    expect(combo).toHaveTextContent('— Elige color —')
    expect(screen.getByTestId('contador-cha')).toHaveTextContent('0')
    await userEvent.click(combo)
    // chai13negro: «negro» no está en la tabla de colores, así que la opción se llama por su token (opcionesSku).
    await userEvent.click(within(await screen.findByRole('listbox', { name: 'SKU de Chasis' })).getByRole('button', { name: 'negro' }))
    expect(screen.getByTestId('contador-cha')).toHaveTextContent('1')
  })
})
```

Run: `npx vitest run src/modules/taller/formulario/FilaComponente.test.tsx`
Expected: FAIL (no hay botones «Nueva …», ni `marca-…`).

- [ ] **Step 2: `FilaComponente.tsx`**

Sustituir `src/modules/taller/formulario/FilaComponente.tsx` entero por:

```tsx
import { useState, type Dispatch, type ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { PREFIJO_CHASIS, PREFIJOS_CON_COLOR } from '../lib/colores'
import { DialogoObservacionFila } from './DialogoObservacionFila'
import { DialogoSolicitarPieza } from './DialogoSolicitarPieza'
import {
  botonDerecho, botonNueva, chasisResaltados, filaEditadaInvalida, filaSinSku, marcaPieza, muestraEleccion, previsionStock,
  reutilizadaJuntoASolicitud, stockDe,
  type AccionFormulario, type BotonDerecho, type EstadoFormulario, type FilaEstado, type MarcaPieza, type PrevisionStock,
} from './estado'
import { TEXTO_SIN_COLOR, opcionesSku } from './opcionesSku'

type Props = { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario>; onGuardarFila: (prefijo: string) => void; children?: ReactNode }

type EstadoFila = 'normal' | 'guardada' | 'editada' | 'yaReparado' | 'sinSku'

/** Fondo y borde inferior por estado (valor de data-estado). */
const CLASES_ESTADO: Record<EstadoFila, string> = {
  normal: 'bg-fondo-input border-form-fila-brd',
  sinSku: 'bg-fondo-input border-form-fila-brd opacity-40',
  guardada: 'bg-form-guardada-bg border-fila-reparado-brd',
  editada: 'bg-fila-edicion-bg border-fila-edicion-brd',
  yaReparado: 'bg-fila-reparado-bg border-fila-reparado-brd',
}

/** El orden reconcilia con `botonDerecho` (estado.ts): una fila "ya reparada" se pinta así aunque su tipo no tenga SKU
 *  para el modelo elegido (opciones vacías) — la reparación es de OTRO modelo del mismo tipo, no de este. */
function estadoDeFila(fila: FilaEstado): EstadoFila {
  if (fila.rol === 'yaReparado') return 'yaReparado'
  if (fila.guardada !== null) return 'guardada'
  if (filaSinSku(fila)) return 'sinSku'
  if (fila.rol === 'editada') return 'editada'
  return 'normal'
}

/** Etiqueta deshabilitada del botón derecho ("✓ Guardada …", "⚠ En camino", "✓ Recibido"): 11 px, radio 0, padding 4 10, con sus
 *  colores aunque esté disabled. */
const CLASE_ETIQUETA = 'h-[27px] rounded-none px-2.5 py-1 text-[11px]'

/** Previsión de stock ("5 → 4"): de la fila editada y, desde la 0.9.19, de una Nueva sin guardar. Rojo si baja, verde si
 *  sube, gris si queda igual. */
const COLOR_TENDENCIA: Record<PrevisionStock['tendencia'], string> = { baja: 'text-rojo-cancelar', sube: 'text-verde-ok', igual: 'text-azul-gris' }

function BotonDerechoFila({ boton, prefijo, onGuardarFila }: { boton: BotonDerecho; prefijo: string; onGuardarFila: (prefijo: string) => void }) {
  const testid = `boton-derecho-${prefijo}`
  switch (boton.tipo) {
    case 'guardarFila':
      return (
        <button
          type="button"
          data-testid={testid}
          disabled={boton.deshabilitado}
          onClick={() => onGuardarFila(prefijo)}
          className="h-[27px] cursor-pointer rounded bg-azul-noche px-2.5 py-1 text-[11px] font-bold text-superficie disabled:cursor-default disabled:opacity-50"
        >
          {boton.texto}
        </button>
      )
    case 'guardada':
      return (
        <button type="button" data-testid={testid} disabled className={cn(CLASE_ETIQUETA, 'bg-recibido-bg text-recibido-text')}>
          {boton.texto}
        </button>
      )
    case 'enCamino':
      return (
        <button type="button" data-testid={testid} disabled className={cn(CLASE_ETIQUETA, 'bg-tipo-reparacion-bg text-tipo-reparacion-text')}>
          ⚠ En camino
        </button>
      )
    case 'recibido':
      return (
        <button type="button" data-testid={testid} disabled className={cn(CLASE_ETIQUETA, 'bg-recibido-bg text-recibido-text')}>
          ✓ Recibido
        </button>
      )
    case 'yaReparado':
      // whitespace-pre conserva los dos espacios del literal; entre llaves para que el formateador no los toque.
      return <span data-testid={testid} className="px-2.5 text-[11px] font-bold whitespace-pre text-verde-ok">{'✓  Ya reparado'}</span>
    case 'ninguno':
      return null
  }
}

const CLASE_ELEGIR =
  'h-[27px] flex-1 cursor-pointer rounded-[3px] border bg-superficie text-[11.5px] font-semibold disabled:cursor-default disabled:opacity-40'

/** Celda del SKU sin elegir (spec 0.9.19 §2): «+ Nueva» (o «Solicitar pieza» si ningún SKU de la fila tiene stock; en edición,
 *  «+ Nueva» deshabilitado) y «↺ Reutilizada». */
function CeldaEleccion({ estado, fila, dispatch, inerte, onSolicitar }: {
  estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario>; inerte: boolean; onSolicitar: () => void
}) {
  const { prefijo, nombre: tipo } = fila
  const izquierda = botonNueva(estado, fila)
  return (
    <div className="flex w-[240px] gap-1.5">
      {izquierda === 'solicitar' ? (
        <button type="button" aria-label={`Solicitar ${tipo}`} disabled={inerte} onClick={onSolicitar}
          className={cn(CLASE_ELEGIR, 'border-ambar bg-form-agotado-bg text-form-agotado-text')}>
          Solicitar pieza
        </button>
      ) : (
        <button type="button" aria-label={`Nueva ${tipo}`} disabled={inerte || izquierda === 'deshabilitada'}
          title={izquierda === 'deshabilitada' ? 'Sin stock' : undefined}
          onClick={() => dispatch({ tipo: 'ELEGIR', prefijo, eleccion: 'nueva' })}
          className={cn(CLASE_ELEGIR, 'border-azul-noche text-azul-noche')}>
          + Nueva
        </button>
      )}
      <button type="button" aria-label={`Reutilizada ${tipo}`} disabled={inerte || !fila.controles.reutilizado}
        onClick={() => dispatch({ tipo: 'ELEGIR', prefijo, eleccion: 'reutilizada' })}
        className={cn(CLASE_ELEGIR, 'border-recibido-text text-recibido-text')}>
        ↺ Reutilizada
      </button>
    </div>
  )
}

const CLASES_MARCA: Record<MarcaPieza['tipo'], string> = {
  nueva: 'bg-tipo-reparacion-bg text-tipo-reparacion-text',
  reutilizada: 'bg-recibido-bg text-recibido-text',
  solicitada: 'bg-form-agotado-bg text-form-agotado-text',
}
const TEXTO_MARCA: Record<MarcaPieza['tipo'], string> = { nueva: '+ Nueva', reutilizada: '↺ Reutilizada', solicitada: 'Solicitada' }

/** Columna «Pieza» (0.9.19; antes la casilla «Reutilizado»): la marca de la elección con su «×», o el «↺ Reutilizada» de una
 *  solicitud guardada pendiente. */
function CeldaPieza({ fila, dispatch, inerte }: { fila: FilaEstado; dispatch: Dispatch<AccionFormulario>; inerte: boolean }) {
  const { prefijo, nombre: tipo } = fila
  const marca = marcaPieza(fila)
  return (
    <div className="flex w-[140px] shrink-0 items-center px-2.5">
      {marca !== null && (
        <span data-testid={`marca-${prefijo}`}
          className={cn('inline-flex h-6 items-center gap-0.5 rounded-xl pl-2.5 text-[11px] font-bold whitespace-nowrap', marca.quitar && !inerte ? 'pr-1' : 'pr-2.5', CLASES_MARCA[marca.tipo])}>
          {TEXTO_MARCA[marca.tipo]}
          {marca.quitar && !inerte && (
            <button type="button" aria-label={`Volver atrás ${tipo}`} title="Volver atrás: la fila queda como al principio"
              onClick={() => dispatch({ tipo: 'VOLVER', prefijo })}
              className="h-[18px] w-[18px] cursor-pointer rounded-full bg-transparent p-0 text-[13px] leading-none hover:bg-black/10">
              ×
            </button>
          )}
        </span>
      )}
      {reutilizadaJuntoASolicitud(fila) && (
        <button type="button" aria-label={`Reutilizada ${tipo}`} onClick={() => dispatch({ tipo: 'ELEGIR', prefijo, eleccion: 'reutilizada' })}
          className="h-6 cursor-pointer rounded-xl border border-recibido-text bg-superficie px-2 text-[11px] font-bold whitespace-nowrap text-recibido-text">
          ↺ Reutilizada
        </button>
      )}
    </div>
  )
}

/** Una fila de componente: contador con "+" y "-", nombre, celda del SKU (los dos botones de la elección o el combo), stock,
 *  columna «Pieza», observación y botón derecho; debajo, `children` (la sub-fila de agotado). No decide nada: el habilitado
 *  sale de `fila.eleccion` y `fila.controles` (una fila sin SKU para el modelo o ya guardada se pinta siempre deshabilitada),
 *  el botón derecho de `botonDerecho()` y cada gesto es un `dispatch`. */
export function FilaComponente({ estado, fila, dispatch, onGuardarFila, children }: Props) {
  const [dialogoObservacion, setDialogoObservacion] = useState(false)
  const [dialogoSolicitar, setDialogoSolicitar] = useState(false)
  const { prefijo, nombre: tipo } = fila
  const estadoFila = estadoDeFila(fila)
  const inerte = estadoFila === 'sinSku' || estadoFila === 'guardada'
  const stock = stockDe(fila)
  // Edición: un cambio que deja la fila editada sin elegir no se puede guardar → contador en rojo y negrita.
  const invalida = fila.rol === 'editada' && filaEditadaInvalida(estado)
  const prevision = previsionStock(estado, fila)
  const nueva = fila.eleccion === 'nueva'
  const clasesMasMenos = 'h-[18px] w-[35px] cursor-pointer rounded-none bg-gris-borde p-0 text-[14px] leading-none font-bold text-gris-disabled disabled:cursor-default disabled:opacity-40'
  // «Solicitar pieza» directo: con el SKU puesto abre el diálogo de siempre; en chasis o tapa sin color elige «Nueva» y la
  // banda de pedirla sale al elegir el color.
  const solicitar = () => (fila.idCom === null ? dispatch({ tipo: 'ELEGIR', prefijo, eleccion: 'nueva' }) : setDialogoSolicitar(true))

  return (
    <div data-testid={`fila-${prefijo}`} data-estado={estadoFila} className={cn('border-b', CLASES_ESTADO[estadoFila])}>
      <div className="flex min-h-[37px] items-center">
        <div className="flex w-[70px] shrink-0 items-center">
          <span data-testid={`contador-${prefijo}`} className={cn('w-[34px] text-center font-[family-name:Inter,system-ui,sans-serif] text-[20px] font-normal', invalida ? 'font-bold text-rojo-cancelar' : fila.cantidad > 0 ? 'text-texto-incidencia' : 'text-gris-borde')}>
            {fila.eleccion === 'reutilizada' ? '—' : fila.cantidad}
          </span>
          <div className="flex w-[35px] flex-col">
            <button type="button" aria-label={`Sumar ${tipo}`} disabled={inerte || !nueva || !fila.controles.mas} onClick={() => dispatch({ tipo: 'SUMAR', prefijo })} className={clasesMasMenos}>+</button>
            <button type="button" aria-label={`Restar ${tipo}`} disabled={inerte || !nueva || !fila.controles.menos} onClick={() => dispatch({ tipo: 'RESTAR', prefijo })} className={clasesMasMenos}>-</button>
          </div>
        </div>
        <span className="w-[100px] shrink-0 px-2.5 text-[12px]">{tipo}</span>
        <div className="w-[240px] shrink-0">
          {muestraEleccion(fila) ? (
            <CeldaEleccion estado={estado} fila={fila} dispatch={dispatch} inerte={inerte} onSolicitar={solicitar} />
          ) : (
            <ComboNavy
              aria-label={`SKU de ${tipo}`}
              valor={fila.idCom === null ? null : String(fila.idCom)}
              opciones={opcionesSku(fila, prefijo === PREFIJO_CHASIS ? chasisResaltados(estado) : new Set())}
              onChange={(valor) => dispatch({ tipo: 'CAMBIAR_SKU', prefijo, idCom: Number(valor) })}
              textoVacio={PREFIJOS_CON_COLOR.includes(prefijo) && fila.opciones.length > 0 && fila.rol === 'normal' ? TEXTO_SIN_COLOR : '—'}
              ancho={240}
              tamanoTexto={11}
              visibles={8}
              disabled={inerte || !fila.controles.sku}
            />
          )}
        </div>
        <span data-testid={`stock-${prefijo}`} className={cn('w-[70px] shrink-0 px-2.5 text-center text-[12px]', prevision !== null && ['font-bold', COLOR_TENDENCIA[prevision.tendencia]])}>
          {prevision !== null ? prevision.texto : (stock ?? '—')}
        </span>
        <CeldaPieza fila={fila} dispatch={dispatch} inerte={inerte} />
        <div className="flex w-[250px] max-w-[250px] shrink-0 items-center gap-1 px-2.5">
          {fila.observacion === null ? (
            <button
              type="button"
              disabled={inerte || !fila.controles.observacion}
              onClick={() => setDialogoObservacion(true)}
              className="flex h-[27px] cursor-pointer items-center gap-1.5 rounded-none bg-form-obs-bg px-2.5 py-1 text-[11px] text-gris-disabled disabled:cursor-default disabled:opacity-40"
            >
              <img src="/editar.png" alt="" className="h-3.5 w-3.5" />
              Añadir observación
            </button>
          ) : (
            <>
              <span title={fila.observacion} className="min-w-0 truncate text-[12px] text-texto-incidencia">{fila.observacion}</span>
              <button
                type="button"
                aria-label={`Borrar observación de ${tipo}`}
                disabled={inerte || !fila.controles.observacion}
                onClick={() => dispatch({ tipo: 'BORRAR_OBSERVACION', prefijo })}
                className="shrink-0 cursor-pointer bg-transparent px-1 py-0.5 disabled:cursor-default disabled:opacity-40"
              >
                <img src="/borrar.png" alt="" className="h-5 w-5" />
              </button>
            </>
          )}
        </div>
        <div className="ml-auto shrink-0 pr-2.5">
          <BotonDerechoFila boton={botonDerecho(estado, fila)} prefijo={prefijo} onGuardarFila={onGuardarFila} />
        </div>
      </div>
      {children}
      <DialogoObservacionFila
        abierto={dialogoObservacion}
        tipo={tipo}
        inicial={fila.observacion ?? ''}
        onGuardar={(texto) => { dispatch({ tipo: 'PONER_OBSERVACION', prefijo, texto }); setDialogoObservacion(false) }}
        onCancelar={() => setDialogoObservacion(false)}
      />
      <DialogoSolicitarPieza
        abierto={dialogoSolicitar}
        tipo={tipo}
        variante="sinStock"
        stock={0}
        inicial=""
        onConfirmar={(descripcion) => { dispatch({ tipo: 'CONFIRMAR_AGOTADO', prefijo, descripcion }); setDialogoSolicitar(false) }}
        onCancelar={() => setDialogoSolicitar(false)}
      />
    </div>
  )
}
```

- [ ] **Step 3: Cabecera y anchos de `FormularioReparacion.tsx`**

Sustituir el comentario y la constante `COLUMNAS`:

```tsx
/** Cabecera de columnas: mismos anchos que las celdas de cada fila (la observación, 250 fijo). El botón derecho no tiene columna.
 *  0.9.19: «¿Reutilizado?» (110) pasa a «Pieza» (140) y la observación de 280 a 250; el total sigue en 870. */
const COLUMNAS = [
  { texto: 'Unit (+/-)', clase: 'w-[70px]' },
  { texto: 'Componente', clase: 'w-[100px]' },
  { texto: 'SKU', clase: 'w-[240px]' },
  { texto: 'Stock', clase: 'w-[70px] text-center' },
  { texto: 'Pieza', clase: 'w-[140px]' },
  { texto: 'Observación', clase: 'w-[250px]' },
] as const
```

- [ ] **Step 4: Quitar el alias `MARCAR_REUTILIZADO`**

En `src/modules/taller/formulario/estado.ts`, borrar la línea `| { tipo: 'MARCAR_REUTILIZADO'; … }` de `AccionFormulario` y el
`case 'MARCAR_REUTILIZADO':` (con su `return`) de `reducir`.

Run: `npm run typecheck`
Expected: errores solo en tests que aún usan `MARCAR_REUTILIZADO`; se traducen con la tabla (`true` → `ELEGIR reutilizada`,
`false` → `VOLVER`) hasta que `npm run typecheck` quede limpio.

- [ ] **Step 5: Ver que pasan los nuevos y adaptar los de interfaz**

Run: `npx vitest run src/modules/taller/formulario/FilaComponente.test.tsx`
Expected: los tests nuevos en PASS.

Run: `npm run test`
Arreglar cada fallo de `FilaComponente.test.tsx`, `FormularioReparacion.test.tsx` (la cabecera: «Pieza» en lugar de
«¿Reutilizado?»), `SubFilaAgotado.test.tsx`, `rutas.test.tsx`, `api.test.tsx` e `ImeiDetallePage.test.tsx` según «Cómo se adaptan
los tests de antes». Casos que se reescriben (cambio a propósito, no traducción):
- «fila sin SKU: atenuada y todo deshabilitado»: en la celda del SKU salen los dos botones deshabilitados (no hay combo) y no
  hay casilla.
- «pinta contador, nombre, SKU, stock, Reutilizado…»: sin elegir, la celda del SKU tiene los dos botones y no el combo.
- Bandas de agotado en filas sin tocar (`SubFilaAgotado.test.tsx`, `FormularioReparacion.test.tsx`): pulsar antes «Nueva X»; en
  filas sin ningún SKU con stock, usar «Solicitar X».

Expected al final: `npm run test` en verde.

- [ ] **Step 6: Check completo**

Run: `npm run check`
Expected: lint, typecheck y tests en verde.

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "feat: nueva o reutilizada en la celda del SKU, columna Pieza y solicitar pieza directo (0.9.19)"
```

---

### Task 5: «Terminar» avisa si falta un SKU

**Files:**
- Modify: `src/modules/taller/formulario/useGuardado.ts`
- Test: `src/modules/taller/formulario/useGuardado.test.tsx`

**Interfaces:**
- Consumes: `textoFaltaSku(e: EstadoFormulario): string | null` (Task 2); `mostrarError` de `useAlerta` (ya en el hook).

- [ ] **Step 1: Test (falla)**

En `src/modules/taller/formulario/useGuardado.test.tsx`, al final del fichero:

```tsx
/** Chasis elegido como Nueva sin color y batería Nueva (modelo 13). */
function ArnesSinColor() {
  const [estado, dispatch] = useReducer(reducir, DATOS, (d) =>
    ([
      { tipo: 'ELEGIR', prefijo: 'cha', eleccion: 'nueva' },
      { tipo: 'ELEGIR', prefijo: 'bat', eleccion: 'nueva' },
    ] satisfies AccionFormulario[]).reduce(reducir, estadoInicial(d)),
  )
  const { pulsarGuardar } = useGuardado({ estado, dispatch, onGuardado: () => {} })
  return <button data-testid="guardar" onClick={pulsarGuardar}>{textoBotonGuardar(estado)}</button>
}

describe('useGuardado · fila elegida sin SKU (0.9.19)', () => {
  it('no pasa a confirmar: avisa «Falta elegir el SKU de: Chasis.» y no escribe nada', async () => {
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    renderConProviders(<ArnesSinColor />, { sesion: SESION_TEC })
    await userEvent.click(screen.getByTestId('guardar'))
    const aviso = await screen.findByRole('dialog', { name: 'Error' })
    expect(within(aviso).getByText('Falta elegir el SKU de: Chasis.')).toBeInTheDocument()
    await userEvent.click(within(aviso).getByRole('button', { name: 'Aceptar' }))
    expect(screen.getByTestId('guardar')).toHaveTextContent('Terminar asignación')
    expect(escrituras(llamadas)).toEqual([])
  })
})
```

Y añadir `type AccionFormulario` al import de `./estado` de ese fichero.

Run: `npx vitest run src/modules/taller/formulario/useGuardado.test.tsx -t "fila elegida sin SKU"`
Expected: FAIL (no sale el aviso: el primer clic no hace nada y no hay diálogo).

- [ ] **Step 2: Implementación**

En `src/modules/taller/formulario/useGuardado.ts`, añadir `textoFaltaSku` al import de `./estado` y, en `pulsarGuardar`, justo
después de `if (estado.guardado.enCurso) return`:

```ts
    // Una fila con «Nueva» o «Reutilizada» elegida y sin SKU (chasis o tapa sin color) no deja seguir (spec 0.9.19 §2).
    const falta = textoFaltaSku(estado)
    if (falta !== null) {
      mostrarError(falta)
      return
    }
```

- [ ] **Step 3: Ver que pasa**

Run: `npx vitest run src/modules/taller/formulario/useGuardado.test.tsx`
Expected: PASS (todo el fichero).

- [ ] **Step 4: Commit**

```bash
git add src/modules/taller/formulario/useGuardado.ts src/modules/taller/formulario/useGuardado.test.tsx
git commit -m "feat: terminar avisa si una pieza elegida no tiene SKU (0.9.19)"
```

---

### Task 6: E2E del formulario (preprod)

**Files:**
- Modify: `tests/e2e/formulario.spec.ts`

**Interfaces:**
- Consumes: los nombres accesibles y `data-testid` de la Task 4; las rutas de API `GET /api/componentes/agrupados`,
  `GET /api/reparaciones/imei/{imei}` y `GET /api/reparaciones/{idRep}/detalle-edicion` (ya existen).

Se ejecuta en preprod en la Task 8 (nunca en producción). Aquí solo se escribe y se comprueba que compila.

- [ ] **Step 1: Modelo de prueba sin chasis ni tapa**

Tras `const TIPOS_SIN_FILA = ['otro', 'g', 'mc']`, añadir:

```ts
/** Chasis y tapa piden color además de «Nueva»: la prueba usa otras filas para no depender del color. */
const CON_COLOR = ['cha', 'tapa']
```

y en `elegirModeloDePrueba`, cambiar `if (TIPOS_SIN_FILA.includes(prefijo)) continue` por
`if (TIPOS_SIN_FILA.includes(prefijo) || CON_COLOR.includes(prefijo)) continue`. Actualizar su comentario: «…un tipo **sin
color** con algún SKU activo con stock (para «Nueva» y guardar la fila) y otro **sin color** con todos sus SKU activos a 0 (para
«Solicitar pieza»)».

- [ ] **Step 2: Estado compartido y stock por API**

Tras `let creada: … = null`, añadir:

```ts
/** Pieza que el técnico pone como «Nueva» y guarda: el supertécnico la pasa luego a reutilizada (y el stock la recupera). */
let piezaNueva: { tipo: string; stockAntes: number } | null = null

/** Stock actual de un SKU (por su texto) según GET /api/componentes/agrupados, con la sesión de API del supertécnico. */
async function stockPorApi(tipo: string): Promise<number> {
  const r = await api!.ctx.get('/api/componentes/agrupados', { headers: api!.headers })
  expect(r.status(), 'GET /api/componentes/agrupados').toBe(200)
  const todos = Object.values((await r.json()) as Record<string, { tipo: string; stock: number }[]>).flat()
  const c = todos.find((x) => x.tipo === tipo)
  if (!c) throw new Error(`El SKU ${tipo} no está en el catálogo`)
  return c.stock
}
```

En `test.afterAll`, tras `creada = null`, añadir `piezaNueva = null`.

- [ ] **Step 3: Técnico — «Nueva» en lugar de «+»**

Sustituir el bloque `// Activar la primera fila que tenga stock` (hasta el `toHaveText('1')`) por:

```ts
  // «Nueva» en la primera fila sin color que la ofrezca (su SKU por defecto tiene stock): ya pone 1 y Stock enseña lo que quedará
  const sinColor = '[data-testid^="fila-"]:not([data-testid="fila-cha"]):not([data-testid="fila-tapa"])'
  const fila = formulario.locator(sinColor).filter({ has: page.getByRole('button', { name: /^Nueva /, disabled: false }) }).first()
  await expect(fila).toBeVisible()
  const prefijo = ((await fila.getAttribute('data-testid')) ?? '').replace('fila-', '')
  await fila.getByRole('button', { name: /^Nueva / }).click()
  await expect(formulario.getByTestId(`contador-${prefijo}`)).toHaveText('1')
  await expect(formulario.getByTestId(`marca-${prefijo}`)).toContainText('+ Nueva')
  const tipoSku = (await fila.getByRole('combobox', { name: /^SKU de / }).innerText()).trim()
  const stockAntes = await stockPorApi(tipoSku)
  await expect(formulario.getByTestId(`stock-${prefijo}`)).toHaveText(`${stockAntes} → ${stockAntes - 1}`)
```

Tras el `await expect(formulario.getByTestId(`contador-${prefijo}`)).toHaveText('1')` que sigue a la banda «✓ Borrador
recuperado», añadir:

```ts
  await expect(formulario.getByTestId(`marca-${prefijo}`)).toContainText('+ Nueva') // el borrador recupera la elección
```

Tras el `toHaveText(/^✓ Guardada …$/)` de «✓ Guardar fila», añadir:

```ts
  expect(await stockPorApi(tipoSku), 'la pieza nueva gasta una del stock').toBe(stockAntes - 1)
  piezaNueva = { tipo: tipoSku, stockAntes }
```

- [ ] **Step 4: Técnico — «Reutilizada», «Volver atrás» y «Solicitar pieza» directo**

Sustituir el bloque `// Solicitar pieza (local) en la fila sin stock…` (hasta el `toContainText('✓  Solicitud de reposición
pendiente — Smoke e2e')`) por:

```ts
  // Fila sin color y sin stock: el botón de la celda del SKU ya dice «Solicitar <tipo>». Antes, «Reutilizada» (contador «—»)
  // y «Volver atrás» (la fila vuelve a los dos botones).
  const filaSinStock = formulario.locator(sinColor).filter({ has: page.getByRole('button', { name: /^Solicitar / }) }).first()
  await expect(filaSinStock).toBeVisible()
  const prefijoSinStock = ((await filaSinStock.getAttribute('data-testid')) ?? '').replace('fila-', '')
  await filaSinStock.getByRole('button', { name: /^Reutilizada / }).click()
  await expect(formulario.getByTestId(`contador-${prefijoSinStock}`)).toHaveText('—')
  await expect(formulario.getByTestId(`marca-${prefijoSinStock}`)).toContainText('↺ Reutilizada')
  await filaSinStock.getByRole('button', { name: /^Volver atrás / }).click()
  await filaSinStock.getByRole('button', { name: /^Solicitar / }).click()
  const solicitar = page.getByRole('dialog', { name: /^Solicitar pieza — / })
  await solicitar.getByPlaceholder('Describe la pieza que necesitas (opcional)...').fill('Smoke e2e')
  await solicitar.getByRole('button', { name: 'Confirmar: solicitar pieza' }).click()
  const subfila = formulario.getByTestId(`subfila-${prefijoSinStock}`)
  await expect(subfila).toHaveAttribute('data-variante', 'confirmada')
  await expect(subfila).toContainText('✓  Solicitud de reposición pendiente — Smoke e2e')
  await expect(formulario.getByTestId(`marca-${prefijoSinStock}`)).toContainText('Solicitada')
```

Actualizar el título del test a `'técnico: borrador recuperado, nueva y guardar fila, reutilizada y volver, solicitar pieza y terminar'`
y su comentario: «…pone una pieza como «Nueva» y guarda la fila (consume una unidad de stock de prueba), prueba «Reutilizada» y
«Volver atrás», registra una solicitud con «Solicitar pieza» y termina…».

- [ ] **Step 5: Supertécnico — la pieza nueva pasa a reutilizada**

En el test del supertécnico:
- Cambiar el comentario del paso «Editar» desde el Historial: «El cambio es la observación, que nunca deja el cambio como
  inválido.» (ya no hay casilla «Reutilizado»).
- Al final del test, tras `await expect(edicion).toBeHidden()`, añadir:

```ts
  // La pieza que el técnico guardó como nueva pasa a reutilizada: el stock recupera la unidad (spec 0.9.19 §2, edición).
  expect(piezaNueva, 'pieza nueva guardada por el test del técnico').not.toBeNull()
  const reps = await api!.ctx.get(`/api/reparaciones/imei/${imeiPrueba}`, { headers: api!.headers })
  expect(reps.status(), `GET /api/reparaciones/imei/${imeiPrueba}`).toBe(200)
  const idR = ((await reps.json()) as { idRep: string }[]).map((r) => r.idRep).find((id) => id.startsWith('R'))
  expect(idR, 'la R… de la fila guardada').toBeTruthy()
  await page.goto(`/reparaciones/historial/editar/${idR}`)
  const edicionPrueba = page.getByRole('dialog', { name: /^Editar reparación — / })
  await expect(edicionPrueba).toBeVisible()
  const editadaPrueba = edicionPrueba.locator('[data-estado="editada"]')
  await editadaPrueba.getByRole('button', { name: /^Volver atrás / }).click()
  await editadaPrueba.getByRole('button', { name: /^Reutilizada / }).click()
  const guardarCambios = edicionPrueba.getByTestId('zona-guardar').getByRole('button')
  await expect(guardarCambios).toHaveText('Guardar cambios')
  await guardarCambios.click()
  await expect(guardarCambios).toHaveText('✓  Confirmar terminar')
  await guardarCambios.click()
  await expect(edicionPrueba).toBeHidden()
  expect(await stockPorApi(piezaNueva!.tipo), 'pasar a reutilizada devuelve la unidad').toBe(piezaNueva!.stockAntes)
  const detalle = await api!.ctx.get(`/api/reparaciones/${idR}/detalle-edicion`, { headers: api!.headers })
  expect(detalle.status(), `GET /api/reparaciones/${idR}/detalle-edicion`).toBe(200)
  expect(((await detalle.json()) as { esReutilizado: boolean }).esReutilizado).toBe(true)
```

- En el comentario de `test.afterAll`, punto 2: «…primero las R… que creó el test (si el test cayó antes de pasarla a
  reutilizada, la fila guardada devuelve su unidad al stock; …)».

- [ ] **Step 6: Compila y lint**

Run: `npm run typecheck && npm run lint && npx playwright test --list tests/e2e/formulario.spec.ts`
Expected: sin errores (`tsconfig.node.json` incluye `tests/e2e/**/*.ts`) y la lista con los tests del fichero.

- [ ] **Step 7: Commit**

```bash
git add tests/e2e/formulario.spec.ts
git commit -m "test: e2e del formulario con nueva, reutilizada, volver atras, solicitar directo y editar a reutilizada (0.9.19)"
```

---

### Task 7: Documentación y cierre de la rama

**Files:**
- Modify: `docs/paridad/formulario.md` (web)
- Modify: `CHANGELOG.md` (web)
- Create or Modify: `docs/novedades/NOVEDADES-v0.9.19.md` (raíz; lo sube el controlador con los gitlinks)
- Modify: `Apuntes/plan-futuro.md` (fuera de los repos)

- [ ] **Step 1: Paridad (`docs/paridad/formulario.md`)**

Cambios (cada viñeta se sustituye entera por el texto nuevo; las no citadas no se tocan):
- «Cabecera de columnas…»: «… "Stock" 70 · "Pieza" 140 (0.9.19; antes "¿Reutilizado?" 110) · "Observación" 250 (fijo; antes
  280). El botón de la derecha de cada fila no tiene columna.»
- «Fila: fondo…»: el orden pasa a «contador, nombre, celda del SKU (los dos botones de la elección o el combo), stock, "Pieza",
  observación, botón derecho».
- Las cuatro viñetas «Estado inicial de una fila con SKU…», «"+"…», «"Reutilizado" (casilla…)» y «Cambiar de SKU…» se
  sustituyen por:
  - «- [x] **Elección en la celda del SKU (0.9.19, spec `docs/superpowers/specs/2026-10-20-v0919-nueva-o-reutilizada-design.md`
    del repo raíz):** una fila sin elegir enseña, en lugar del combo, «+ Nueva» y «↺ Reutilizada» (alto 27, 11,5 px); el contador
    está apagado y no hay sub-fila de agotado. Si ningún SKU de la fila tiene stock, el primero es «Solicitar pieza» (ámbar), que
    abre el diálogo «Solicitar pieza — <tipo>» (en chasis y tapa sin color, hace de «Nueva»); en edición, «+ Nueva» sale
    deshabilitado.»
  - «- [x] «Nueva»: aparece el combo (todos los SKU, los sin stock en rojo); el contador se pone en 1 si el SKU tiene stock (0 sin
    stock, o en chasis y tapa hasta elegir el color) y "+"/"-" se encienden; Stock enseña "<stock> → <stock − cantidad>" en rojo
    negrita. "+" solo suma si cantidad < stock. Cambiar de SKU deja la cantidad que tenía (mínimo 1) sin pasar del stock, o 0 sin
    stock (con la sub-fila de pedirla).»
  - «- [x] «Reutilizada»: aparece el combo (todos los SKU), el contador enseña "—" y "+"/"-" quedan apagados; no gasta stock ni
    saca la sub-fila de agotado. Cambiar de SKU no toca nada más.»
  - «- [x] Columna "Pieza": marca «+ Nueva», «↺ Reutilizada» o «Solicitada» (11 px negrita, radio 12) con una «×» ("Volver atrás
    <tipo>") que deja la fila sin elegir conservando el SKU y cancela la solicitud local. En una Nueva, "-" hasta 0 hace lo mismo.
    Sin «×» en filas guardadas, en vuelo, ya reparadas o con la solicitud ya registrada.»
  - «- [x] Con una fila elegida sin SKU (chasis o tapa sin color), la zona de guardar se muestra y al pulsarla sale el aviso
    «Falta elegir el SKU de: <filas>.» sin pasar a "✓  Confirmar terminar".»
- «Tipo sin SKU para el modelo…» y «Chasis y tapa trasera con color…»: cambiar «"+", "-", "Reutilizado" y observación
  deshabilitados» / ««+», «-», «Reutilizado» y «Añadir observación» siguen desactivados hasta elegir» por «los dos botones de la
  elección y la observación deshabilitados» / «tras «Nueva» o «Reutilizada» el combo enseña «— Elige color —» y la observación
  sigue desactivada hasta elegirlo».
- «Cualquier cambio en la fila (contador, SKU, "Reutilizado", observación)…» → «(elección, contador, SKU, observación)».
- «"✓ Guardar fila"…»: «…cuando cantidad > 0 o la pieza es reutilizada…».
- «Fila guardada…»: «…combo SKU, "+", "-", la «×» de la marca y "Añadir observación" deshabilitados… Una guardada reutilizada
  muestra contador "—" y la marca «↺ Reutilizada» sin «×».»
- Sub-fila: «Solo existe en flujo nuevo y Glass…» añade «y solo con «Nueva» (desde la 0.9.19, una fila sin elegir o reutilizada
  no la muestra)»; la variante límite pasa a «(Nueva, stock > 0 y cantidad ≥ stock)»; quitar «Se evalúa también al pintar la
  fila por primera vez».
- «Confirmar es local…»: «se deshabilitan "+", "-", combo SKU y "Añadir observación"; la marca pasa a «Solicitada» con su «×»…».
- «"Cancelar solicitud" sobre una solicitud local…» → «…la fila vuelve sin elegir (los dos botones), con su SKU; lo mismo que la
  «×» de la marca.»
- Solicitudes guardadas: en «Pendiente…», «**"Reutilizado" habilitado**» → «en "Pieza", el botón «↺ Reutilizada» (sustituye a la
  casilla; elegido, la marca «↺ Reutilizada ×»)»; en «En camino…», «"Reutilizado" desmarcado y **deshabilitado**» → «sin
  «↺ Reutilizada»»; en «Recibido…», añadir «la fila sale sin elegir y se usa con «+ Nueva»»; en «Rechazada…», «con lo que la fila
  muestra el SKU en rojo y la sub-fila amarilla…» → «; al elegir «Nueva» sale la sub-fila amarilla "Sin stock… / Solicitar pieza"
  (o, si ningún SKU del tipo tiene stock, el botón «Solicitar pieza»)»; «Marcar "Reutilizado" en una fila con solicitud
  pendiente…» → «Elegir «↺ Reutilizada» en una fila con solicitud pendiente…».
- Zona de guardar: «(cantidad > 0, "Reutilizado", o agotado…)» → «(Nueva con cantidad > 0, Reutilizada, o agotado…), o alguna
  fila elegida sin SKU (para avisar)»; paso 2: «(incluidas las de solicitud cargada con «↺ Reutilizada»)».
- Borrador: en «Recuperación…», añadir «La elección se deduce: reutilizado → Reutilizada; cantidad > 0 o agotado → Nueva; si no,
  sin elegir (el JSON no cambia).»; en «Qué se captura…», añadir «una fila elegida sin SKU no se captura»; en «Filas guardadas
  borradas por otro…», «"Reutilizado" desmarcado» → «sin elegir».
- Modo edición: «Fila editada…» → «SKU, cantidad, "Reutilizado" y observación originales y la elección que les corresponde
  (Nueva o Reutilizada); con la «×» se cambia de una a otra. "+" y "-" solo con Nueva; "+" deshabilitado si stock ≤ 0…»;
  «Hay cambio si varía cantidad, SKU, "Reutilizado"…» y «Cambio sin uso (cantidad 0 y sin "Reutilizado")» → «Cambio sin uso
  (fila sin elegir)»; «Esas filas nuevas no tienen sub-fila de agotado ni "Solicitar pieza": con stock 0 "+" está
  deshabilitado…» → «…ni "Solicitar pieza": sin ningún SKU con stock, «+ Nueva» sale deshabilitado.»
- «Correcciones deliberadas respecto al JavaFX», nueva viñeta al final:
  «- Nueva o reutilizada (0.9.19): el JavaFX tenía un contador "+" y una casilla "Reutilizado", y por costumbre se pulsaba "+" con
  piezas reutilizadas (el stock bajaba de más). La web obliga a elegir «+ Nueva» o «↺ Reutilizada» en la celda del SKU antes de
  nada; la sub-fila de agotado solo sale con Nueva; «Solicitar pieza» sale directo si no hay stock de ningún SKU; con «Nueva» el
  combo deja la cantidad sin pasar del stock (el JavaFX la ponía a 0); y en edición se puede pasar de nueva a reutilizada con la
  «×».»
- «Comportamientos del JavaFX calcados a propósito»: borrar «Tras desmarcar "Reutilizado", "+" queda habilitado aunque el stock
  sea 0 (sin efecto).» y, en «…con solicitud guardada, "Reutilizado" y "Añadir observación" siguen activos…», «"Reutilizado"» →
  ««↺ Reutilizada»».

- [ ] **Step 2: CHANGELOG de la web**

En `CHANGELOG.md`, dentro de `## [0.9.19]`, antes de la línea «- Solo web: funciona con el servidor 0.9.18. Sin migración.»
(esa línea la ajusta la sesión que cierra la 0.9.19), añadir:

```markdown
- **Formulario de reparación: «Nueva» o «Reutilizada» en la celda del SKU.** Cada pieza empieza eligiendo, donde estaba el desplegable, si es **nueva** (gasta del stock) o **reutilizada** (no gasta); después sale el desplegable. Con «Nueva» el contador se pone en 1 y Stock enseña lo que quedará («5 → 4»); con «Reutilizada» el contador enseña «—». La columna «¿Reutilizado?» pasa a «Pieza», con la marca «+ Nueva», «↺ Reutilizada» o «Solicitada» y una «×» para volver atrás («−» hasta 0 también). Si ningún SKU de la pieza tiene stock, el botón ya dice «Solicitar pieza». La banda «Sin stock… Solicitar pieza» solo sale con «Nueva». Con una pieza elegida sin SKU (chasis o tapa sin color), «Terminar» avisa «Falta elegir el SKU de: …». Al editar una reparación, la «×» permite pasar una pieza de nueva a reutilizada y al revés (el servidor ya ajusta el stock).
```

- [ ] **Step 3: Novedades (raíz)**

Si `docs/novedades/NOVEDADES-v0.9.19.md` no existe, crearlo con la cabecera:

```markdown
# 🎉 Novedades — Versión 0.9.19 (web)

---
```

Añadir (al final, o tras la última sección `---`):

```markdown
## 🔧 Técnicos

### 🧩 Formulario de reparación: ¿pieza nueva o reutilizada?

- **Cada pieza empieza diciendo si es nueva o reutilizada**, en el sitio donde estaba el desplegable del SKU:
  - **«+ Nueva»** gasta una pieza del stock: aparece el desplegable y el contador se pone en 1. La columna Stock enseña lo que
    quedará («5 → 4»).
  - **«↺ Reutilizada»** no gasta stock: aparece el desplegable y no hay contador.
- Ya **no hay casilla «Reutilizado»**: la columna se llama «Pieza» y dice «+ Nueva», «↺ Reutilizada» o «Solicitada».
- **¿Te has equivocado?** Pulsa la **×** de esa marca (o «−» hasta 0 en una nueva): la fila vuelve al principio y el color
  elegido no se pierde.
- **Si no queda stock** de ninguna pieza de ese tipo, el botón ya dice **«Solicitar pieza»**. Si solo falta la que has elegido
  (por ejemplo, un color), al elegirla sale la banda de siempre para pedirla.
- **Chasis y tapa:** después de «Nueva» o «Reutilizada», elige el color. Sin color no deja terminar.
- **Editar una reparación:** con la × se puede pasar una pieza de nueva a reutilizada (y al revés); el stock se ajusta solo.

> ⚠️ Antes se pulsaba «+» por costumbre también con piezas reutilizadas, y el stock bajaba de más. Ahora hay que decirlo cada
> vez: cuenta para el stock y, con el SKU del taller, para la batería nueva del móvil.

---
```

- [ ] **Step 4: Check y build**

Run (en el worktree): `npm run check && npm run build`
Expected: todo en verde; `vite build` sin errores.

- [ ] **Step 5: Commit de la web**

```bash
git add docs/paridad/formulario.md CHANGELOG.md
git commit -m "docs: paridad y changelog de nueva o reutilizada en la celda del SKU (0.9.19)"
```

El fichero de novedades (raíz) y la marca del plan los sube el controlador con los gitlinks, con el OK del usuario.

- [ ] **Step 5b: Plan maestro**

En `Apuntes/plan-futuro.md` §5, en la entrada «0.9.19 Formulario de reparación…», añadir «Rama `dev_v0919_nueva_reutilizada`
lista, con los commits que da `git log --oneline main..dev_v0919_nueva_reutilizada`; falta preprod y la subida con el resto de
la 0.9.19.» (sin marcar `[x]` hasta producción).

---

### Task 8: Entrega (preprod y merge, con el resto de la 0.9.19)

**Files:** guion en `Apuntes/preprod/v0919-preprod.md` (lo comparte con las otras piezas de la 0.9.19; fuera de los repos).

**Requisitos:** las Tasks 1-7 hechas y revisadas. Merge, push y gitlinks **solo con el OK del usuario**. Claude **no** hace SSH:
prepara los comandos y el usuario los ejecuta (cada bloque empieza con `hostname` y, en prod, el seguro de `backup-erp.sh`).

- [ ] **Step 1: Avisar al usuario** de que la rama está lista y de lo que falta: merge `--no-ff` a `main` de la web (con su OK),
  despliegue en preprod junto con las otras piezas de la 0.9.19, y **avisar al taller** del cambio antes de subir a producción.
- [ ] **Step 2: Preprod** (cuando el usuario lo despliegue): `/version.json` da 0.9.19; E2E `formulario` con
  `E2E_BASE_URL` de preprod (Chromium; datos sintéticos por API, limpieza en `afterAll`; nunca en producción). Si da el 503 del
  límite de inicios de sesión, esperar un minuto y repetir solo ese fichero.
- [ ] **Step 3: Prueba a mano en preprod con el usuario:** una batería nueva, una reutilizada, un chasis con color, «Solicitar
  pieza» de una pieza sin stock, «Volver atrás», y editar una reparación de nueva a reutilizada; mirar que el stock cuadra.
  Además (revisión final): chasis «Nueva» + color + «Añadir observación» (debe dejar), y que «↺ Reutilizada ×» cabe en la
  columna «Pieza» sin cortarse.
- [ ] **Step 4: Registro:** resultado en `.superpowers/sdd/v0919-nr/progress.md` y en `Apuntes/despliegue_preprod.md`.
