# 0.9.9 (ajuste) — Un proveedor por pedido y solo se mueve lo vivo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- «Nuevo pedido» lleva un único proveedor, arriba y obligatorio.
- Solo se mueven líneas vivas (pendiente, en camino o parcial) y solo hacia bloques vivos del mismo proveedor.
- Un bloque terminado queda cerrado: no entra ni sale nada.

**Architecture:**
- **Servidor:** un método nuevo `BloqueCompraDAO.estaTerminado` y tres comprobaciones con 422:
  - un destino terminado, en `DestinoBloque.resolver`;
  - una línea recibida o cancelada en `mover`;
  - un origen terminado en `juntar` y `separar`.
- **Web:**
  - las reglas de bloques filtran por `esVivo` y quitan el tope de 8;
  - el menú de línea solo ofrece «Mover» a las vivas;
  - «Nuevo pedido» pone el proveedor de arriba en todas las líneas y enseña la columna Proveedor como texto.
- Sin cambios de esquema ni de contrato.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3 y JdbcTemplate. Pruebas con JUnit 5 y Mockito.
- **Web:** React, TypeScript y TanStack Query. Pruebas con Vitest, Testing Library, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-10-12-v099-un-proveedor-por-pedido-design.md` (raíz). Va **dentro de la 0.9.9**:
servidor `main` `0fc5169` y web `main` `ca421eb`, ya en preproducción, sin desplegar en producción ni etiquetar.

## Global Constraints

- **Ramas y worktrees:**
  - Rama `feature/un-proveedor-por-pedido` creada **desde `main`**, en los worktrees `C:/Users/dev/Documents/wt/servidor-099` y `C:/Users/dev/Documents/wt/web-099`. Hoy están en `feature/piezas-por-tipo`, ya mergeada.
  - Los clones normales no se tocan, salvo en el cierre y con OK.
- **Comandos (Git Bash):**
  - **Servidor:** `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"` y luego `mvn -q test`.
  - **Web:** `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint`.
    - **Un solo vitest a la vez, siempre en primer plano.** No dejar ningún proceso en segundo plano.
    - La suite completa tarda ~4 min: usar un timeout largo.
- **Commits:**
  - En español **sin tildes**, con los prefijos `feat:` / `fix:` / `test:` / `docs:` / `chore:`.
  - **Sin** `Co-Authored-By`.
  - Nada de push, merge ni tag sin OK.
- **Comentarios del código:** en español normal, **con** tildes.
- **Estados:**
  - **línea viva** = `pendiente`, `en_camino` o `parcial`;
  - **bloque terminado** = ninguna línea viva, es decir, todo `recibido` o `cancelado`;
  - **bloque vivo** = abierto o en curso. En la web ya existen `situacion(b)` y `esVivo(b)`.
- **Textos exactos (servidor):**
  - 422 «El bloque {n} ya está terminado.» (`"El bloque %d ya está terminado."`);
  - 422 «Una línea recibida o cancelada no se puede mover.».
- **Textos exactos (web):**
  - error «Elige el proveedor.»;
  - columna Proveedor sin elegir: «—»;
  - resumen sin líneas: «Añade líneas: aquí verás a qué bloque van.»;
  - motivos del menú del bloque: «el bloque está terminado» y «no hay otro bloque vivo de este proveedor».
- **Registro de subagentes:** `.superpowers/sdd/v099d/` en la raíz (gitignored).

---

### Task 1: Servidor — un bloque terminado está cerrado y lo recibido o cancelado no se mueve

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/BloqueCompraDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/DestinoBloque.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/BloqueCompraService.java`: `mover`, `juntar` y `separar`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md`
- Test: `src/test/java/com/reparaciones/servidor/dao/BloqueCompraDAOTest.java`, `service/DestinoBloqueTest.java` y `service/BloqueCompraServiceTest.java`

**Interfaces:**
- Produces:
  - `BloqueCompraDAO.estaTerminado(int idBloque): boolean`, con la constante package-private `SQL_VIVAS`;
  - `DestinoBloque.MSG_TERMINADO = "El bloque %d ya está terminado."` (package-private);
  - `BloqueCompraService.MSG_LINEA_CERRADA = "Una línea recibida o cancelada no se puede mover."`.

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/servidor-099 && git status --short && git switch -c feature/un-proveedor-por-pedido main && git log --oneline -1
```
Expected: árbol limpio; `0fc5169 merge: piezas de cada proveedor por tipo de pieza (0.9.9)`.

- [ ] **Step 1: Tests que fallan**

En `BloqueCompraDAOTest`, junto a `contarLineas()`:

```java
    @Test void estaTerminadoCuentaLasLineasVivas() {
        assertEquals("SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ? AND ESTADO IN ('pendiente','en_camino','parcial')",
                BloqueCompraDAO.SQL_VIVAS);
        when(jdbc.queryForObject(BloqueCompraDAO.SQL_VIVAS, Integer.class, 41)).thenReturn(0);
        when(jdbc.queryForObject(BloqueCompraDAO.SQL_VIVAS, Integer.class, 42)).thenReturn(2);
        assertTrue(dao.estaTerminado(41));
        assertFalse(dao.estaTerminado(42));
    }
```

En `DestinoBloqueTest`, tras `unBloqueDeOtroProveedorEs422`:

```java
    @Test void unBloqueTerminadoEs422() {
        when(dao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        when(dao.estaTerminado(41)).thenReturn(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> destino.resolver(2, new DestinoPedido(41), true));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El bloque 41 ya está terminado.", e.getReason());
        verify(dao, never()).crear(anyInt());
    }
```

En `BloqueCompraServiceTest`, en la sección «mover»:

```java
    @Test void moverUnaRecibidaOUnaCanceladaEs422SinTocarNada() {
        for (String estado : List.of("recibido", "cancelado")) {
            when(compraDao.getById(5)).thenReturn(Optional.of(linea(estado, 41)));
            falla(HttpStatus.UNPROCESSABLE_ENTITY, "Una línea recibida o cancelada no se puede mover.",
                    () -> servicio.mover(5, new DestinoPedido(40), AT));
        }
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
        verify(bloqueDao, never()).crear(anyInt());
    }

    @Test void moverAUnBloqueTerminadoEs422() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        when(bloqueDao.estaTerminado(40)).thenReturn(true);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 40 ya está terminado.", () -> servicio.mover(5, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }
```

En la sección «separar, juntar y nota»:

```java
    @Test void separarUnBloqueTerminadoEs422() {
        bloque41(lb(1, "recibido"), lb(2, "cancelado"));
        when(bloqueDao.estaTerminado(41)).thenReturn(true);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 41 ya está terminado.", () -> servicio.separar(41, List.of(v(1))));
        verify(bloqueDao, never()).crear(anyInt());
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }

    @Test void juntarDesdeOHaciaUnBloqueTerminadoEs422() {
        bloque41(lb(1, "recibido"), lb(2, "recibido"));
        when(bloqueDao.estaTerminado(41)).thenReturn(true);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 41 ya está terminado.", () -> servicio.juntar(41, 40, List.of(v(1), v(2))));
        when(bloqueDao.estaTerminado(41)).thenReturn(false);
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        when(bloqueDao.estaTerminado(40)).thenReturn(true);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 40 ya está terminado.", () -> servicio.juntar(41, 40, List.of(v(1), v(2))));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }
```

En la sección «editar cambiando el proveedor»:

```java
    @Test void editarHaciaUnBloqueTerminadoEs422() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(60)).thenReturn(Optional.of(bloque(60, 3)));
        when(bloqueDao.estaTerminado(60)).thenReturn(true);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 60 ya está terminado.",
                () -> servicio.editarCambiandoProveedor(5, 3, 3, false, 10.0, "EUR", 10.0, AT, new DestinoPedido(60)));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }
```

**Nota:** `editarCambiandoProveedor` llama a `compraDao.editar` antes de resolver el destino. En producción, el 422 revierte todo porque el método es `@Transactional`. En el test el DAO es un mock: solo se comprueba que no hay movimiento.

- [ ] **Step 2: Comprobar que fallan**

Run: `mvn -q test -Dtest='BloqueCompraDAOTest,DestinoBloqueTest,BloqueCompraServiceTest'`
Expected: falla la compilación, porque `estaTerminado` y `SQL_VIVAS` no existen.

- [ ] **Step 3: Implementación**

`BloqueCompraDAO`: junto a `SQL_CONTAR` y `contarLineas`, añadir:

```java
    static final String SQL_VIVAS =
            "SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ? AND ESTADO IN ('pendiente','en_camino','parcial')";

    /** Terminado = sin ninguna línea pendiente, en camino ni parcial: todo recibido o cancelado (spec 2026-10-12
     *  «un proveedor por pedido» §4). Un bloque terminado está cerrado: no entra ni sale ninguna línea. */
    public boolean estaTerminado(int idBloque) {
        Integer n = jdbc.queryForObject(SQL_VIVAS, Integer.class, idBloque);
        return n == null || n == 0;
    }
```

`DestinoBloque`:
1. Añadir la constante `static final String MSG_TERMINADO = "El bloque %d ya está terminado.";` junto a `MSG_NO_EXISTE`.
2. En `resolver`, tras el `if (b.idProv() != idProv) { … }`, añadir:

```java
        if (dao.estaTerminado(idBloque)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_TERMINADO.formatted(idBloque));
        }
```

3. Actualizar su Javadoc: «… 422 si es de otro proveedor o si está terminado (2026-10-12) …».

`BloqueCompraService`:
- Constante, junto a las demás:
  `static final String MSG_LINEA_CERRADA = "Una línea recibida o cancelada no se puede mover.";`
- En `mover`, justo después de la comprobación de versión (409):

```java
        // Lo recibido y lo cancelado es historial: no cambia de bloque (spec 2026-10-12 §2).
        if ("recibido".equals(l.getEstado()) || "cancelado".equals(l.getEstado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_LINEA_CERRADA);
        }
```

- Actualizar el Javadoc de `mover`: «… El servidor no distingue estados …» pasa a «Una línea recibida o cancelada no se mueve (422); de las demás, la confirmación de una ya pedida la pide la web».
- En `separar`, tras `Comprobadas c = comprobar(idBloque, vistas, false);`, y en `juntar`, tras `Comprobadas c = comprobar(idBloque, vistas, true);`:

```java
        if (bloqueDao.estaTerminado(idBloque)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, DestinoBloque.MSG_TERMINADO.formatted(idBloque));
        }
```

En `juntar`, el destino ya pasa por `destinoBloque.resolver`, que ahora también rechaza un destino terminado.

`docs/api_contract.md`, en la viñeta de bloques que lista los 422 de mover, juntar y separar: añadir «`422` «El bloque N ya está terminado.» si el destino (lote, mover, editar, juntar) o el origen (juntar, separar) no tiene ninguna línea pendiente, en camino ni parcial, y `422` «Una línea recibida o cancelada no se puede mover.» al mover una línea en esos estados (spec 2026-10-12)».

- [ ] **Step 4: Comprobar que pasa**

Run:
1. `mvn -q test -Dtest='BloqueCompraDAOTest,DestinoBloqueTest,BloqueCompraServiceTest'`
2. Después, la suite entera: `mvn -q test`.

Expected: todo verde. Los recuentos salen de `target/surefire-reports` si `-q` no imprime nada.

- [ ] **Step 5: Commit**

```bash
git add src/ docs/api_contract.md
git commit -m "feat: un bloque terminado no admite movimientos y una linea recibida o cancelada no cambia de bloque"
```

---

### Task 2: Web — solo se mueve lo vivo (reglas, menú de línea, «Juntar» y «Editar»)

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/bloques/reglas.ts` y `bloques/reglas.test.ts`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/PedidosPage.tsx`: `entradasDe`, `menuFila` y los candidatos de `JuntarDialog`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/EditarPedidoDialog.tsx` y `EditarPedidoDialog.bloques.test.tsx`
- Test: `gestion-reparaciones-web/src/modules/almacen/pedidos/PedidosPage.bloques.test.tsx`, solo si algún test mueve una cancelada o una recibida

**Interfaces:**
- Produces, en `bloques/reglas.ts`:
  - `vivosDelProveedor(bloques: Map<string, Bloque>, idProv: number, excluir?: string): Bloque[]`: los de `bloquesDelProveedor` que cumplen `esVivo`.
  - `sePuedeMover(p: CompraComponente): boolean`: `pendiente`, `en_camino` o `parcial`.
  - `destinosMover(p, bloques)`: vivos y sin tope.
  - `entradasMenuBloque(b, otrosVivos)`: el segundo parámetro pasa a significar «otros bloques vivos del proveedor».

- [ ] **Step 0: Rama**

```bash
cd /c/Users/dev/Documents/wt/web-099 && git status --short && git switch -c feature/un-proveedor-por-pedido main && git log --oneline -1
```
Expected: árbol limpio; `ca421eb merge: piezas de cada proveedor por tipo de pieza (0.9.9)`.

- [ ] **Step 1: Tests que fallan (reglas)**

En `bloques/reglas.test.ts`, describe de mover y destinos, que usa `todas` con 39 recibida (terminado), 40 abierto y 41 abierto/en curso:
- Añadir `sePuedeMover` y `vivosDelProveedor` al import de `./reglas`.
- Sustituir el test «destinos: los del mismo proveedor sin el propio, de mayor a menor número» por:

```ts
  it('destinos: los bloques vivos del mismo proveedor sin el propio, de mayor a menor número (el 39, terminado, no)', () => {
    expect(destinosMover(todas[0], bloques)).toEqual([
      { idBloque: 40, texto: 'Bloque 40', detalle: '08/10 · 1 línea · abierto' },
    ])
    expect(vivosDelProveedor(bloques, 1).map((b) => b.idBloque)).toEqual([41, 40])
  })
```

- Sustituir «como mucho 8 destinos» por:

```ts
  it('sin tope: todos los vivos del proveedor', () => {
    const muchas = Array.from({ length: 11 }, (_, i) => l({ idCompra: i + 1, idBloque: 100 + i, fechaBloque: `2026-10-0${(i % 9) + 1}T10:00:00` }))
    expect(destinosMover(muchas[0], agruparEnBloques(muchas))).toHaveLength(10)
  })

  it('solo se mueven las líneas vivas', () => {
    expect(['pendiente', 'en_camino', 'parcial', 'recibido', 'cancelado'].map((estado) => sePuedeMover(l({ estado }))))
      .toEqual([true, true, true, false, false])
  })
```

En el describe «textos»:
- en «menú del bloque con contadores…», el motivo de «Juntar con…» pasa a `'no hay otro bloque vivo de este proveedor'`;
- el test ««Añadir previsión…» va justo tras «Añadir líneas…» y siempre está disponible» cambia su nombre a «… y en un bloque vivo está disponible». El cuerpo no cambia, porque `b` es vivo;
- añadir:

```ts
  it('un bloque terminado está cerrado: sin añadir, previsión, separar ni juntar', () => {
    const terminado = [...agruparEnBloques([
      l({ idCompra: 1, idBloque: 60, estado: 'recibido' }), l({ idCompra: 2, idBloque: 60, estado: 'cancelado' }),
    ]).values()][0]
    const porAccion = (a: string) => entradasMenuBloque(terminado, 3).find((e) => e !== 'separador' && e.accion === a)
    for (const accion of ['anadir', 'prevision', 'separar', 'juntar']) {
      expect(porAccion(accion)).toMatchObject({ deshabilitada: true, motivo: 'el bloque está terminado' })
    }
    expect(porAccion('nota')).toMatchObject({ deshabilitada: false })
  })
```

`l` es la fábrica de líneas que ya usa el fichero. Si en ese describe se llama de otra forma, se usa la que haya.

Run: `npx vitest run src/modules/almacen/pedidos/bloques/reglas.test.ts`
Expected: fallan los tests nuevos y los cambiados.

- [ ] **Step 2: Implementación de las reglas**

En `bloques/reglas.ts`:

```ts
/** 2026-10-12: solo se mueven las líneas vivas; lo recibido y lo cancelado es historial. */
export function sePuedeMover(p: CompraComponente): boolean {
  return VIVOS.has(p.estado)
}

/** Bloques vivos (abiertos o en curso) del proveedor, sin `excluir`, de mayor a menor número: los únicos que admiten
 *  líneas (un bloque terminado está cerrado, spec 2026-10-12). */
export function vivosDelProveedor(bloques: Map<string, Bloque>, idProv: number, excluir?: string): Bloque[] {
  return bloquesDelProveedor(bloques, idProv, excluir).filter(esVivo)
}
```

Además:
- **`destinosMover`:** usa `vivosDelProveedor(bloques, p.idProv, claveBloque(p))` y quita el `.slice(0, 8)`. Su comentario pasa a «Submenú «Mover a bloque ▸» (spec §6.4 y 2026-10-12): los bloques vivos del proveedor sin el propio.».
- **`entradasMenuBloque(b: Bloque, otrosVivos: number)`:**
  - calcular `const cerrado = !esVivo(b)` y `const TERMINADO = 'el bloque está terminado'`;
  - cambiar estas entradas:

```ts
    entrada('anadir', 'Añadir líneas…', cerrado, TERMINADO),
    entrada('prevision', 'Añadir previsión…', cerrado, TERMINADO),
    entrada('separar', 'Separar líneas…', cerrado || b.lineas.length < 2, cerrado ? TERMINADO : 'solo tiene una línea'),
    entrada('juntar', 'Juntar con…', cerrado || otrosVivos === 0, cerrado ? TERMINADO : 'no hay otro bloque vivo de este proveedor'),
```

  - el comentario: «`otrosVivos`: cuántos bloques vivos más tiene su proveedor».

- [ ] **Step 3: Pantallas**

En `PedidosPage.tsx`:
- Importar `sePuedeMover` y `vivosDelProveedor` de `./bloques/reglas`.
- `entradasDe`:
  ```ts
  const entradasDe = (b: Bloque) => entradasMenuBloque(b, vivosDelProveedor(bloques, b.idProv, b.clave).length)
  ```
- En `menuFila`:
  - `const mov = esCompra(p) ? {…} : undefined` pasa a `const mov = esCompra(p) && sePuedeMover(p) ? {…} : undefined`;
  - el comentario de encima: «Un cancelado no lleva menú (y desde 2026-10-12 tampoco «Mover a bloque»: solo se mueven líneas vivas).».
- `<JuntarDialog … candidatos={vivosDelProveedor(bloques, juntando.idProv, juntando.clave)} …/>`.
- Si `bloquesDelProveedor` queda sin uso en el fichero, quitarlo del import.

En `formulario/EditarPedidoDialog.tsx`, `opcionesBloque`:
- `...bloquesDelProveedor(bloques, proveedor).slice(0, 8).map(...)` pasa a `...vivosDelProveedor(bloques, proveedor).map(...)`;
- ajustar el import: `vivosDelProveedor` en lugar de `bloquesDelProveedor`, si este ya no se usa.

- [ ] **Step 4: Tests de pantallas**

- **`EditarPedidoDialog.bloques.test.tsx`:** si algún test espera un bloque terminado entre las opciones de «Bloque», o un tope de 8, ajustarlo a la regla nueva, que es solo vivos y sin tope. Añadir este test. Usa `abrir`, `elegirProveedor`, `COMPRA` y la fábrica `l` del fichero:

```tsx
  it('el campo Bloque solo ofrece los bloques vivos del proveedor nuevo y «Bloque nuevo»', async () => {
    // Bloque 60 de «Proveedor B» abierto y bloque 59 terminado (todo recibido): el 59 no se ofrece.
    server.use(http.get('*/api/compras', () => HttpResponse.json([
      COMPRA,
      l({ idCompra: 20, idProv: 2, nombreProveedor: 'Proveedor B', idBloque: 60 }),
      l({ idCompra: 21, idProv: 2, nombreProveedor: 'Proveedor B', idBloque: 59, estado: 'recibido' }),
    ])))
    abrir(COMPRA)
    await screen.findByRole('dialog', { name: 'Editar pedido #7' })
    await elegirProveedor('Proveedor B')
    await userEvent.click(await screen.findByRole('combobox', { name: 'Bloque' }))
    const opciones = within(screen.getByRole('listbox', { name: 'Bloque' })).getAllByRole('button').map((b) => b.textContent ?? '')
    expect(opciones).toHaveLength(2)
    expect(opciones[0]).toMatch(/^Bloque 60 /)
    expect(opciones[1]).toBe('Bloque nuevo')
  })
```

- **`PedidosPage.bloques.test.tsx`:** si algún test abre «Mover a bloque» en una línea recibida o cancelada, cambiarlo a una línea viva. Añadir este test. Usa `montar`, `abrirMenu` y los datos del fichero: 41 en curso, 40 abierto, 39 terminado con `lcd-y` recibido.

```tsx
  it('una recibida no ofrece «Mover a bloque» y una viva solo ve destinos vivos (el 39, terminado, no)', async () => {
    montar()
    await screen.findByText('lcd-x-negro')
    // El 39 está terminado y sale plegado: se despliega desde su cabecera.
    await userEvent.click(screen.getByText('Bloque 39').closest('button') as HTMLElement)
    await abrirMenu('lcd-y')
    expect(await screen.findByRole('menuitem', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Mover a bloque' })).not.toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    await abrirMenu('lcd-x-negro')
    await userEvent.click(screen.getByRole('menuitem', { name: 'Mover a bloque' }))
    expect(await screen.findByRole('menuitem', { name: /Bloque 40/ })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: /Bloque 39/ })).not.toBeInTheDocument()
  })
```

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos`;
2. `npx tsc -b && npm run lint`;
3. la suite entera: `npx vitest run 2>&1 | grep -E "Test Files|Tests "`.

Expected: todo verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/pedidos
git commit -m "feat: solo se mueven lineas vivas y solo a bloques vivos del proveedor; un bloque terminado queda cerrado"
```

---

### Task 3: Web — «Nuevo pedido» con un único proveedor

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.ts`: fuera `rellenarProveedorVacio`, más la constante `MSG_SIN_PROVEEDOR`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/ResumenBloques.tsx`: el texto sin líneas
- Test: `formulario/NuevoPedidoDialog.test.tsx`, `formulario/NuevoPedidoDialog.bloques.test.tsx` y `formulario/lineas.test.ts`

**Interfaces:**
- Consumes:
  - `aplicarProveedorATodas(lineas, idProv)` (ya existe en `lineas.ts`);
  - la prop `proveedorFijo?: string` de `DialogoLineas`, que pinta una píldora en lugar del combo de cada línea.
- Produces: `MSG_SIN_PROVEEDOR = 'Elige el proveedor.'` en `lineas.ts`.

- [ ] **Step 1: Tests que fallan**

`formulario/lineas.test.ts`:
- quitar `rellenarProveedorVacio` del import;
- borrar su test (el de la línea ~215);
- el test ««Aplicar a todas» pone el proveedor en todas las líneas» pasa a llamarse «aplicarProveedorATodas pone el proveedor en todas las líneas». El cuerpo no cambia.

`formulario/NuevoPedidoDialog.test.tsx`:
1. **Ayudantes.** Sustituir `elegirProveedor(n, nombre)` por estos dos. Borrar también el `elegirProveedorGeneral` local del describe «pedido automático…» y usar `elegirProveedor` en su lugar.

```ts
/** El proveedor del pedido (0.9.9: uno por pedido, arriba): ComboNavy «Proveedor general». */
async function elegirProveedor(nombre: string) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Proveedor general' }))
  await userEvent.click(await within(screen.getByRole('listbox', { name: 'Proveedor general' })).findByRole('button', { name: nombre }))
}

/** Lo que enseña la columna Proveedor de la línea n: el nombre del proveedor del pedido o «—» (sin combo). */
const proveedorDeLinea = (n: number) => within(filaDe(n)).getAllByRole('cell')[1].textContent
```

2. **Llamadas.** Cada `await elegirProveedor(<n>, '<X>')` pasa a `await elegirProveedor('<X>')`. Si un test lo llamaba para varias líneas con el mismo proveedor, basta una vez.
3. **«vacío: título de 24 px…»:** la lista de botones pasa a ser `['Añadir previsión', '+ Añadir línea', 'Cancelar', 'Confirmar pedido', 'Close']`.
4. **«"Pedir" (un componente)…»:**
   - `expect(screen.getByRole('combobox', { name: 'Proveedor línea 1' }).textContent).toBe('')` pasa a `expect(proveedorDeLinea(1)).toBe('—')`, y lo mismo para la línea 2;
   - añadir `expect(screen.queryByRole('combobox', { name: 'Proveedor línea 1' })).not.toBeInTheDocument()`.
5. **«validación en la línea de error…»:** el recorrido nuevo es este.

```ts
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+ Añadir línea' }))
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Elige el proveedor.')
    await elegirProveedor('ACME')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: selecciona un componente.')
    await elegirComponente(1, 'bat', 'bat-x / bat-y')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await escribir('Cantidad línea 1', '0')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: la cantidad debe ser mayor que 0.')
    await escribir('Cantidad línea 1', '2')
    await escribir('Precio línea 1', '-1')
    await confirmar()
    expect(screen.getByRole('alert')).toHaveTextContent('Línea 1: el precio no puede ser negativo.')
    expect(lotes).toHaveLength(0)
```

6. **«proveedor en USD…»:** abrir el combo `'Proveedor general'` en vez de `'Proveedor línea 1'`, comprobar sus opciones `['ACME', 'Proveedor B']` y elegir `'Proveedor B'`. Las aserciones de `$` y `17,60 €` no cambian. Después, `elegirProveedor('ACME')` deja `€` y `20,00 €`.
7. **Aserciones del combo.** Cada `expect(screen.getByRole('combobox', { name: 'Proveedor línea N' })).toHaveTextContent('X')` pasa a `expect(proveedorDeLinea(N)).toBe('X')`.
8. **En «pedido automático…»:**
   - borrar ««Aplicar a todas» cambia también el proveedor que ya tenía una línea»;
   - borrar «elegir el proveedor general rellena al momento las líneas sin proveedor y no toca las que ya tienen»;
   - mantener «con proveedor general elegido, «+ Añadir línea» la añade ya con ese proveedor», con `proveedorDeLinea(1)`;
   - añadir:

```tsx
  it('elegir el proveedor lo pone en todas las líneas; cambiarlo las cambia todas (un proveedor por pedido)', async () => {
    abrir({ modo: 'componentes', idsCom: [1, 2] })
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 2')).toHaveValue('1'))
    expect([proveedorDeLinea(1), proveedorDeLinea(2)]).toEqual(['—', '—'])
    await elegirProveedor('Proveedor B')
    expect([proveedorDeLinea(1), proveedorDeLinea(2)]).toEqual(['Proveedor B', 'Proveedor B'])
    await elegirProveedor('ACME')
    expect([proveedorDeLinea(1), proveedorDeLinea(2)]).toEqual(['ACME', 'ACME'])
    expect(screen.queryByRole('button', { name: 'Aplicar a todas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Proveedor línea 1' })).not.toBeInTheDocument()
  })
```

`formulario/NuevoPedidoDialog.bloques.test.tsx`:
- **`anadirLinea(n, proveedor?)`:** quitar el parámetro `proveedor` y las dos líneas del combo por línea. Añadir el mismo ayudante `elegirProveedor(nombre)` que en el otro fichero.
- **Llamadas:** cada `await anadirLinea(n, 'X')` pasa a llamar antes, una vez, a `await elegirProveedor('X')` y después a `await anadirLinea(n)`.
- **«sin líneas, el resumen lo explica»:** el texto pasa a `'Añade líneas: aquí verás a qué bloque van.'`.

Run: `npx vitest run src/modules/almacen/pedidos/formulario`
Expected: fallan los tests nuevos y los cambiados.

- [ ] **Step 2: Implementación**

`formulario/lineas.ts`:
- borrar `rellenarProveedorVacio`, con su comentario;
- añadir, junto a `MSG_SIN_LINEAS`:

```ts
/** 0.9.9 (2026-10-12): «Nuevo pedido» lleva un único proveedor, el de arriba. */
export const MSG_SIN_PROVEEDOR = 'Elige el proveedor.'
```

`formulario/NuevoPedidoDialog.tsx`:
- **Import:** quitar `rellenarProveedorVacio` y añadir `MSG_SIN_PROVEEDOR`.
- **`elegirProveedorGeneral`:**

```ts
  /** Un proveedor por pedido (2026-10-12): elegirlo, o cambiarlo, lo pone en todas las líneas. */
  function elegirProveedorGeneral(idProv: number) {
    setIdProvGeneral(idProv)
    setLineas((ls) => (ls === null ? null : aplicarProveedorATodas(ls, idProv)))
    setError(null)
    setSinPedido(0)
    setSinListaVistas(0)
    setNadaQuePedir(false)
  }
```

- **Precarga:** dentro del `if (lineas === null && …)`, `setLineas(inicial.lineas)` pasa a `setLineas(idProvGeneral === null ? inicial.lineas : aplicarProveedorATodas(inicial.lineas, idProvGeneral))`. Así, si el proveedor se eligió mientras cargaba la lista, la precarga lo lleva.
- **Fuera** la función `aplicarATodas` y el botón `<BotonSecundario …>Aplicar a todas</BotonSecundario>` de la barra.
- **`confirmar`:** `const fallo = validarLineasCompra(lineas)` pasa a:

```ts
    const fallo = lineas.length > 0 && idProvGeneral === null ? MSG_SIN_PROVEEDOR : validarLineasCompra(lineas)
```

- **`DialogoLineas`:** `proveedorFijo={fijo ? nombreFijo : undefined}` pasa a `proveedorFijo={fijo ? nombreFijo : nombrePrevision || '—'}`. `nombrePrevision` ya es el nombre del proveedor elegido, o `''`.

`formulario/ResumenBloques.tsx`: el texto `Añade líneas: aquí verás a qué bloque va cada proveedor.` pasa a `Añade líneas: aquí verás a qué bloque van.`.

- [ ] **Step 3: Comprobar que pasa**

Run, de uno en uno y en primer plano:
1. `npx vitest run src/modules/almacen/pedidos/formulario`;
2. `npx tsc -b && npm run lint`;
3. la suite entera: `npx vitest run 2>&1 | grep -E "Test Files|Tests "`.

Expected: todo verde.

- [ ] **Step 4: Commit**

```bash
git add src/modules/almacen/pedidos/formulario
git commit -m "feat: nuevo pedido con un unico proveedor para todas las lineas"
```

---

### Task 4: Web — E2E y documentación

**Files:**
- Modify: `gestion-reparaciones-web/tests/e2e/pedidos.spec.ts`: pedido de componentes
- Modify: `gestion-reparaciones-web/tests/e2e/bloques.spec.ts`: `rellenarNuevoPedido` y su comentario
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (`[0.9.9]`) y `docs/paridad/pedidos.md`

- [ ] **Step 1: E2E**

**`pedidos.spec.ts`, paso «pedido de componentes: crear, editar y borrar».** Sustituir las dos líneas que eligen el proveedor de la línea 1, y el comentario de encima, por:

```ts
      // Un proveedor por pedido (0.9.9): se elige arriba, en «Proveedor general», y lo llevan todas las líneas.
      await dlg.getByRole('combobox', { name: 'Proveedor general' }).click()
      await page.getByRole('listbox', { name: 'Proveedor general' }).getByRole('button', { name: nombreProv }).click()
```

El paso de «Otros pedidos» **no cambia**, porque sigue con proveedor por línea.

**`bloques.spec.ts`, `rellenarNuevoPedido`:**
- elegir el proveedor una vez, antes del bucle;
- dentro del bucle, quitar las dos líneas del combo por línea;
- cambiar el comentario: «…autocompletar de componente; el proveedor, una vez arriba («Proveedor general»)».

```ts
  await page.getByRole('button', { name: 'Nuevo pedido', exact: true }).click()
  const dlg = page.getByRole('dialog', { name: 'Nuevo pedido', exact: true })
  await dlg.getByRole('combobox', { name: 'Proveedor general' }).click()
  await page.getByRole('listbox', { name: 'Proveedor general' }).getByRole('button', { name: nombreProv }).click()
  for (const [i, precio] of precios.entries()) {
    await dlg.getByRole('button', { name: '+ Añadir línea' }).click()
    await dlg.getByPlaceholder('Escribe componente...').nth(i).fill(sku!)
    // La lista del autocompletar va en un portal fuera del diálogo (114ca65): se busca en la página.
    await page.getByRole('option', { name: sku!, exact: true }).click()
    await dlg.getByLabel(`Precio línea ${i + 1}`).fill(precio)
  }
  return dlg
```

Repasar el resto de `bloques.spec.ts` en busca de cualquier `Proveedor línea`. Los tests «mover … a «Bloque nuevo» y juntar de vuelta» y «Recibir todo…» trabajan con bloques vivos y siguen valiendo. Si alguno mueve una línea recibida o cancelada, PARAR y avisar.

Run: `npx tsc -b && npm run lint`. Las E2E **no** se ejecutan aquí, sino contra preproducción en el cierre.

- [ ] **Step 2: Documentación**

`CHANGELOG.md` `[0.9.9]`: añadir, tras la viñeta de «Mover a bloque»:

```
- **Un proveedor por pedido.** «Nuevo pedido» lleva el proveedor arriba, obligatorio, y vale para todas las líneas (la columna Proveedor solo lo enseña); ya no hay «Aplicar a todas». Desde la campana, las piezas entran sin proveedor y al elegirlo lo llevan todas. Para pedir a otro proveedor, otro pedido.
- **Solo se mueve lo vivo.** «Mover a bloque» solo sale en líneas pendientes, en camino o parciales, y ofrece los bloques vivos (abiertos o en curso) del mismo proveedor, sin tope, y «Bloque nuevo». «Juntar con…» y el «Bloque» de «Editar», igual. Un bloque terminado (todo recibido o cancelado) queda cerrado: sin añadir líneas ni previsión, separar ni juntar.
```

En la viñeta de «Mover a bloque» de la 0.9.9, el texto «(también en las canceladas)» pasa a «(solo en líneas vivas)».

`docs/paridad/pedidos.md`, sección «Nuevo pedido»: añadir

```
- [x] **Un proveedor por pedido (0.9.9, decisión del usuario 2026-10-12)**: el proveedor se elige arriba («Proveedor general»), es obligatorio («Elige el proveedor.») y vale para todas las líneas; la columna Proveedor lo enseña sin combo. Desviación del JavaFX, que elegía proveedor en cada línea (`NuevoPedidoDialog.test.tsx` «elegir el proveedor lo pone en todas las líneas…»).
```

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/pedidos.spec.ts tests/e2e/bloques.spec.ts CHANGELOG.md docs/paridad/pedidos.md
git commit -m "test: las e2e eligen el proveedor una vez arriba; changelog y paridad de un proveedor por pedido"
```

---

### Task 5: Cierre (controlador)

- [ ] Revisión final de las dos ramas con el modelo más capaz. Los arreglos, con un solo subagente.
- [ ] **Con OK del usuario:**
  1. `merge --no-ff` en `main`, en los dos repos, con el mensaje `merge: un proveedor por pedido y solo se mueve lo vivo (0.9.9)`, y push.
  2. En la raíz: gitlinks y `NOVEDADES-v0.9.9.md` (dos viñetas: un proveedor por pedido y lo terminado queda cerrado). Push.
- [ ] `Apuntes/preprod/v099d-preprod.md`:
  - código de los dos repos, `compose up`, `Started`;
  - **sin** migración;
  - E2E una a una;
  - limpieza 2c de `bloques.spec.ts`.
  - No hace falta `init.sql`, porque el esquema no cambia.
- [ ] `Apuntes/prod-v099.md`: los commits nuevos en el despliegue, en el «Esperado» y en los tags.

### Task 6 (usuario): preproducción

### Task 7 (usuario): producción, con `prod-v099.md`
