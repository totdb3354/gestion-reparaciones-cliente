# 0.9.16 — El técnico sin Pedidos ni Proveedores · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El TECNICO deja de ver Pedidos y Proveedores (columna, URL y API). Con Stock actual le basta: «En Camino» pasa a ser texto sin enlace y la barra «Pedido» del gráfico enseña lo que está en camino.

**Architecture:**
- **Servidor:** las cuatro lecturas de pedidos y proveedores quedan en `hasAnyRole('SUPERTECNICO', 'ADMIN')`. Un test de roles con la cadena de seguridad real lo fija.
- **Web:**
  - `enlacesStock(sesion)` depende del rol;
  - una guarda `RequiereAccesoPedidos` envuelve las tres rutas y manda al técnico a `/stock` con el aviso genérico;
  - en Stock actual, `onEnCamino` pasa a ser opcional y el gráfico del técnico usa el `enCamino` de la fila.
- **E2E de solo lectura** con las tres cuentas de preprod.

**Tech Stack:** Spring Boot 3.3 con Spring Security `@PreAuthorize` (JUnit 5, Mockito y MockMvc). En la web, React 19 + TypeScript, React Router (data router) y TanStack Query/Table, con Vitest, Testing Library, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-10-15-v0916-tecnico-sin-pedidos-design.md` (raíz).

## Global Constraints

**Versión y código de partida**
- Versión **0.9.16**. Parte de servidor `main` `c65ce4f` y web `main` `ead5a0f` (0.9.15).
- **No toca la base de datos.**

**Ramas, worktrees y registro**
- Todo el desarrollo va en la rama **`dev_v0916_tecnico_sin_pedidos`**, en worktrees. Los clones normales no se tocan.
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0916`.
  - Web: `/c/Users/dev/Documents/wt/web-0916`, con `npm ci` dentro.
  - Las rutas `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` de este plan se refieren a esos worktrees; `docs/…` sin prefijo es la raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0916/`** (raíz).

**Commits, merge y datos de prueba**
- Merge `--no-ff`, push, gitlinks y tags **solo con OK del usuario**.
- Commits en español sin tildes, con prefijo `feat:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- Repos **públicos**: datos de tests sintéticos («Proveedor A», `lcd-x`, `bat-x`…).

**Reglas de rol** (spec §2)
- Pedidos y Proveedores: **SUPERTECNICO** (con edición) y **ADMIN** (solo lectura, como hoy).
- El **TECNICO** no los ve. Cualquier sesión que no sea SUPERTECNICO ni ADMIN, o sin sesión, cuenta como TECNICO.
- Stock actual: los tres roles, como hoy.

**Mensajes exactos**
- Aviso de la guarda: `MSG_SIN_PERMISOS` = `No tienes permisos para realizar esta acción.` (ya existe en `shared/api/errors.ts`).
- La guarda redirige a **`/stock`**.

**Comandos**
- Servidor (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro de `/c/Users/dev/Documents/wt/servidor-0916`.
- Web: `npx vitest run <ruta>`, `npx tsc -b` y `npm run lint` dentro de `/c/Users/dev/Documents/wt/web-0916`.

**Preproducción y producción**
- **Claude no hace SSH.** Prepara los guiones en `Apuntes/` y el usuario los ejecuta y pega la salida.
- Cada bloque de guion empieza comprobando la máquina: `hostname` + `backup-erp.sh`, que solo existe en producción.

---

### Task 1: Servidor — pedidos y proveedores solo para SUPERTECNICO y ADMIN

**Files:**
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/RolesLecturasPedidosTest.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraController.java:49,55`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java:44`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ProveedorController.java:44`
- Modify: `gestion-reparaciones-servidor/docs/autorizacion_endpoints.md:35`

**Interfaces:**
- Consumes: nada nuevo (parte de `main` `c65ce4f`).
- Produces: `GET /api/compras`, `GET /api/compras/en-camino`, `GET /api/compras-otros` y `GET /api/proveedores` responden `403` al TECNICO y `200` a SUPERTECNICO y ADMIN. La web (Tasks 2 y 3) deja de pedirlas con el técnico.

- [ ] **Step 1: Worktree**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor worktree add /c/Users/dev/Documents/wt/servidor-0916 -b dev_v0916_tecnico_sin_pedidos main
git -C /c/Users/dev/Documents/wt/servidor-0916 log --oneline -1
```

Expected: `c65ce4f merge: los otros pedidos dentro del bloque de su proveedor (0.9.15)`. Todos los comandos de esta tarea se lanzan en Git Bash dentro de `/c/Users/dev/Documents/wt/servidor-0916`, después del `export` de **Comandos**.

- [ ] **Step 2: Test que falla**

Crea `src/test/java/com/reparaciones/servidor/controller/RolesLecturasPedidosTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lecturas de pedidos y proveedores (0.9.16, spec «El técnico sin Pedidos ni Proveedores» §4): SUPERTECNICO y ADMIN; el
 *  TECNICO recibe 403 sin llegar al DAO. Cadena de seguridad real y DAOs mockeados (sin base de datos). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesLecturasPedidosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean CompraComponenteDAO compraDao;
    @MockBean CompraOtroDAO otroDao;
    @MockBean ProveedorDAO proveedorDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    /** `GET /api/compras/en-camino` no va aquí: es una ruta retirada y `RutasRetiradas` la corta con 403 para todos
     *  (`RutasRetiradasInterceptorTest`); su anotación se cambia solo para que diga lo mismo que las demás. */
    private static final List<String> LECTURAS = List.of("/api/compras", "/api/compras-otros", "/api/proveedores?tipo=COMPONENTES");

    @Test void elTecnicoRecibe403SinLlegarAlDao() throws Exception {
        for (String ruta : LECTURAS) {
            mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(compraDao, otroDao, proveedorDao);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
        for (String ruta : LECTURAS) {
            mvc.perform(get(ruta).header("Authorization", supertecnico())).andExpect(status().isOk());
            mvc.perform(get(ruta).header("Authorization", admin())).andExpect(status().isOk());
        }
    }
}
```

Los DAOs mockeados devuelven listas vacías por defecto (Mockito), así que las lecturas permitidas responden `200` con `[]`.

- [ ] **Step 3: Comprobar que falla**

Run: `mvn -q test -Dtest=RolesLecturasPedidosTest`
Expected: FAIL en `elTecnicoRecibe403SinLlegarAlDao` (`Status expected:<403> but was:<200>`). `supertecnicoYAdminLasLeen` pasa.

- [ ] **Step 4: Quitar TECNICO de las cuatro lecturas**

En `CompraController.java`, las dos primeras anotaciones (líneas 49 y 55):

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping
    public List<CompraComponente> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping("/en-camino")
    public List<CompraComponente> getEnCamino() {
        return dao.getEnCamino();
    }
```

En `CompraOtroController.java` (línea 44):

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping
    public List<CompraOtro> getAll() {
        return dao.getAll();
    }
```

En `ProveedorController.java` (línea 44):

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @GetMapping
    public List<Proveedor> getAll(@RequestParam(required = false) String tipo) {
        return dao.getAll(tipo);
    }
```

Comprueba que no queda ninguna lectura de pedidos ni proveedores con TECNICO:

Run: `grep -n "TECNICO')" src/main/java/com/reparaciones/servidor/controller/CompraController.java src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java src/main/java/com/reparaciones/servidor/controller/ProveedorController.java`
Expected: sin salida.

- [ ] **Step 5: Comprobar que pasa**

Run: `mvn -q test -Dtest=RolesLecturasPedidosTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Documentación de autorización**

En `docs/autorizacion_endpoints.md`, en el punto **Lecturas (GET)** (línea 35), tras `…y el detalle de edición de una reparación (SUPERTECNICO).`, añade:

```markdown
 Los pedidos (`GET /api/compras` y `GET /api/compras-otros`) y los proveedores (`GET /api/proveedores`) los leen SUPERTECNICO y ADMIN desde la 0.9.16; entre el 2026-05-18 y la 0.9.15 también los leía TECNICO.
```

Es una frase más del mismo párrafo: va en la misma línea, separada por un espacio.

- [ ] **Step 7: Suite completa**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, sin fallos. Ningún test de hoy pide esas lecturas con el TECNICO (comprobado al escribir la spec).

- [ ] **Step 8: Commit**

```bash
git add src/test/java/com/reparaciones/servidor/controller/RolesLecturasPedidosTest.java \
        src/main/java/com/reparaciones/servidor/controller/CompraController.java \
        src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java \
        src/main/java/com/reparaciones/servidor/controller/ProveedorController.java \
        docs/autorizacion_endpoints.md
git commit -m "feat: pedidos y proveedores solo para supertecnico y admin (0.9.16)"
```

---

### Task 2: Web — columna lateral y rutas sin Pedidos ni Proveedores para el técnico

**Files:**
- Rename: `gestion-reparaciones-web/src/modules/almacen/rutas.ts` → `rutas.tsx` (y entero)
- Rename: `gestion-reparaciones-web/src/modules/almacen/rutas.test.ts` → `rutas.test.tsx` (y entero)
- Modify: `gestion-reparaciones-web/src/app/shell/navegacion.ts:7-17`
- Modify: `gestion-reparaciones-web/src/app/shell/navegacion.test.ts:9-22`
- Modify: `gestion-reparaciones-web/src/app/router.tsx:1-4,76-80`
- Modify: `gestion-reparaciones-web/src/app/shell/SubNav.test.tsx` (varios casos)
- Modify: `gestion-reparaciones-web/src/app/shell/TopBar.test.tsx:36`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/PedidosPage.test.tsx:12,151,197,416`
- Modify: `gestion-reparaciones-web/src/modules/almacen/proveedores/ProveedoresPage.test.tsx:5,61-70`
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/api.ts:15,28` (comentarios)

**Interfaces:**
- Consumes: el servidor de la Task 1 (en producción irán juntos; los tests usan MSW).
- Produces:
  - `enlacesStock(sesion: Sesion | null): Enlace[]` en `@/modules/almacen/rutas`. Con `esAdminOSuperTecnico(sesion)` devuelve «Stock actual», «Pedidos» y «Proveedores»; si no, solo «Stock actual».
  - `RequiereAccesoPedidos()` en `@/modules/almacen/rutas`: guarda de layout (`<Outlet />`). Sin permiso, `mostrarError(MSG_SIN_PERMISOS)` y `<Navigate to="/stock" replace />`.

- [ ] **Step 1: Worktree de la web**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web worktree add /c/Users/dev/Documents/wt/web-0916 -b dev_v0916_tecnico_sin_pedidos main
cd /c/Users/dev/Documents/wt/web-0916 && git log --oneline -1 && npm ci
```

Expected: `ead5a0f docs: fecha de la 0.9.15 en el changelog` y `npm ci` sin errores. Todos los comandos de las Tasks 2 a 5 se lanzan dentro de `/c/Users/dev/Documents/wt/web-0916`.

- [ ] **Step 2: Renombrar los dos ficheros**

```bash
git mv src/modules/almacen/rutas.ts src/modules/almacen/rutas.tsx
git mv src/modules/almacen/rutas.test.ts src/modules/almacen/rutas.test.tsx
```

- [ ] **Step 3: Tests que fallan (enlaces y guarda)**

Sustituye `src/modules/almacen/rutas.test.tsx` entero por:

```tsx
import { screen } from '@testing-library/react'
import { Building2, Package, ShoppingCart } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { renderConRouter, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { enlacesStock, RequiereAccesoPedidos } from './rutas'

describe('enlaces de la columna de Stock', () => {
  it('SUPERTECNICO y ADMIN: los tres, cada uno con su icono (0.9.10)', () => {
    for (const sesion of [SESION_SUPER, SESION_ADMIN]) {
      expect(enlacesStock(sesion).map((e) => [e.label, e.to, e.icono])).toEqual([
        ['Stock actual', '/stock', Package],
        ['Pedidos', '/stock/pedidos', ShoppingCart],
        ['Proveedores', '/stock/proveedores', Building2],
      ])
    }
  })
  it('TECNICO y sin sesión: solo «Stock actual» (0.9.16)', () => {
    for (const sesion of [SESION_TEC, null]) {
      expect(enlacesStock(sesion).map((e) => e.label)).toEqual(['Stock actual'])
    }
  })
})

const rutas = [
  {
    element: <RequiereAccesoPedidos />,
    children: [
      { path: '/stock/pedidos', element: <p>PEDIDOS</p> },
      { path: '/stock/pedidos/otros', element: <p>OTROS</p> },
      { path: '/stock/proveedores', element: <p>PROVEEDORES</p> },
    ],
  },
  { path: '/stock', element: <p>STOCK ACTUAL</p> },
]

/** Pedidos y Proveedores (0.9.16, spec «El técnico sin Pedidos ni Proveedores» §3.2): SUPERTECNICO y ADMIN; el servidor
 *  responde 403 al resto. */
describe('RequiereAccesoPedidos', () => {
  it.each([
    ['SUPERTECNICO', SESION_SUPER, '/stock/pedidos', 'PEDIDOS'],
    ['ADMIN', SESION_ADMIN, '/stock/proveedores', 'PROVEEDORES'],
  ] as const)('%s entra sin aviso', async (_rol, sesion, ruta, texto) => {
    renderConRouter(rutas, { sesion, ruta })
    expect(await screen.findByText(texto)).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Error' })).not.toBeInTheDocument()
  })
  it.each(['/stock/pedidos', '/stock/pedidos/otros', '/stock/proveedores'])('el TECNICO en %s recibe el aviso genérico y sale a /stock', async (ruta) => {
    const { router } = renderConRouter(rutas, { sesion: SESION_TEC, ruta })
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
    expect(router.state.location.pathname).toBe('/stock')
    expect(screen.getByText('STOCK ACTUAL')).toBeInTheDocument()
    for (const texto of ['PEDIDOS', 'OTROS', 'PROVEEDORES']) expect(screen.queryByText(texto)).not.toBeInTheDocument()
  })
})
```

En `src/app/shell/navegacion.test.ts`, sustituye los dos primeros `it` (líneas 9-22) por:

```ts
  it('técnico: Reparaciones con Pendientes, Stock solo con «Stock actual» (0.9.16), Estadísticas › Rendimiento y Clientes › Listado, sin Gestión', () => {
    expect(resumen(gruposNav(SESION_TEC))).toEqual([
      ['reparaciones', 'Reparaciones', ['Pendientes', 'Historial', 'IMEIs']],
      ['stock', 'Stock', ['Stock actual']],
      ['estadisticas', 'Estadísticas', ['Rendimiento']],
      ['clientes', 'Clientes', ['Listado']],
    ])
  })

  it('supertécnico: Asignaciones y Pendientes en Reparaciones, Stock con Pedidos y Proveedores, sin Gestión', () => {
    const grupos = gruposNav(SESION_SUPER)
    expect(grupos.map((g) => g.id)).toEqual(['reparaciones', 'stock', 'estadisticas', 'clientes'])
    expect(grupos[0].enlaces.map((e) => e.label)).toEqual(['Asignaciones', 'Pendientes', 'Historial', 'IMEIs'])
    expect(grupos[1].enlaces.map((e) => e.label)).toEqual(['Stock actual', 'Pedidos', 'Proveedores'])
  })
```

- [ ] **Step 4: Comprobar que fallan**

Run: `npx vitest run src/modules/almacen/rutas.test.tsx src/app/shell/navegacion.test.ts`
Expected: FAIL. `RequiereAccesoPedidos` no existe y el técnico aún tiene «Pedidos» y «Proveedores».

- [ ] **Step 5: `enlacesStock(sesion)` y la guarda**

Sustituye `src/modules/almacen/rutas.tsx` entero por:

```tsx
import { useEffect } from 'react'
import { Building2, Package, ShoppingCart } from 'lucide-react'
import { Navigate, Outlet } from 'react-router'
import { MSG_SIN_PERMISOS } from '@/shared/api/errors'
import type { Enlace } from '@/shared/lib/enlaces'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdminOSuperTecnico, type Sesion } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'

/** Sidebar de StockView.fxml (`stock-sidebar-btn`): "Stock actual" · "Pedidos" · "Proveedores", en ese orden y sin badges.
 *  Desde la 0.9.16 (spec «El técnico sin Pedidos ni Proveedores») Pedidos y Proveedores son de SUPERTECNICO y ADMIN: el
 *  TECNICO (y cualquier otro rol, o sin sesión) se queda con "Stock actual". `end` en el primero: sin él, NavLink lo marcaría
 *  activo también en /stock/pedidos. Iconos desde la 0.9.10. */
// eslint-disable-next-line react-refresh/only-export-components -- la función vive con las rutas que la usan, patrón del proyecto
export function enlacesStock(sesion: Sesion | null): Enlace[] {
  const enlaces: Enlace[] = [{ to: '/stock', label: 'Stock actual', icono: Package, end: true }]
  if (esAdminOSuperTecnico(sesion)) {
    enlaces.push(
      { to: '/stock/pedidos', label: 'Pedidos', icono: ShoppingCart },
      { to: '/stock/proveedores', label: 'Proveedores', icono: Building2 },
    )
  }
  return enlaces
}

/** Pedidos y Proveedores (0.9.16): SUPERTECNICO y ADMIN, este en solo lectura. El TECNICO no tiene las pestañas, así que solo
 *  llega por URL: recibe el aviso genérico y sale a /stock. Mismo patrón que RequiereSupertecnicoOAdmin (modules/taller/rutas.tsx),
 *  copiado porque un módulo no importa de otro, y con otro nombre porque router.tsx importa los dos. */
export function RequiereAccesoPedidos() {
  const { sesion } = useSession()
  const { mostrarError } = useAlerta()
  const permitido = esAdminOSuperTecnico(sesion)
  useEffect(() => {
    if (!permitido) mostrarError(MSG_SIN_PERMISOS)
  }, [permitido, mostrarError])
  if (!permitido) return <Navigate to="/stock" replace />
  return <Outlet />
}
```

En `src/app/shell/navegacion.ts`, el comentario y la línea del grupo Stock:

```ts
/**
 * Grupos de la columna lateral (0.9.14, spec «Navegación por grupos» §2), en este orden: Reparaciones (sus pestañas
 * dependen del rol), Stock (desde la 0.9.16 también: el técnico solo tiene «Stock actual»), Estadísticas, Clientes y, solo
 * para el admin, Gestión. Las pestañas no repiten el nombre del grupo (Estadísticas › Rendimiento, Clientes › Listado).
 */
export function gruposNav(sesion: Sesion | null): GrupoNav[] {
  const grupos: GrupoNav[] = [
    { id: 'reparaciones', titulo: 'Reparaciones', enlaces: enlacesReparaciones(sesion) },
    { id: 'stock', titulo: 'Stock', enlaces: enlacesStock(sesion) },
```

El resto de la función no cambia.

- [ ] **Step 6: Comprobar que pasan**

Run: `npx vitest run src/modules/almacen/rutas.test.tsx src/app/shell/navegacion.test.ts`
Expected: PASS.

- [ ] **Step 7: La guarda en el router**

En `src/app/router.tsx`, añade el import tras el de `ProveedoresPage` (línea 3):

```tsx
import { RequiereAccesoPedidos } from '@/modules/almacen/rutas'
```

Y sustituye las líneas 77-80 (`/stock/pedidos`, su comentario, `/stock/pedidos/otros` y `/stock/proveedores`) por:

```tsx
          {
            // 0.9.16: Pedidos y Proveedores solo para SUPERTECNICO y ADMIN; el TECNICO por URL recibe el aviso y vuelve a /stock.
            element: <RequiereAccesoPedidos />,
            children: [
              { path: '/stock/pedidos', element: <PedidosPage /> },
              // 0.9.15: la página «Otros» ya no existe; su dirección abre Pedidos (enlaces y marcadores viejos).
              { path: '/stock/pedidos/otros', element: <RedirigirPedidosOtros /> },
              { path: '/stock/proveedores', element: <ProveedoresPage /> },
            ],
          },
```

`{ path: '/stock', element: <StockPage /> }` (línea 76) se queda encima, fuera de la guarda.

- [ ] **Step 8: SubNav con el técnico sin Pedidos**

En `src/app/shell/SubNav.test.tsx`:

1. Primer caso de `columna lateral por grupos` (líneas 39-44). Los enlaces del técnico quedan sin «Pedidos» ni «Proveedores»; justo debajo va un caso nuevo:

```tsx
  it('en cualquier ruta salen todos los grupos del rol, en orden, con sus pestañas', () => {
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/estadisticas' })
    expect(columna().getAllByRole('button', { name: CABECERAS }).map((b) => b.textContent)).toEqual(['Reparaciones', 'Stock', 'Estadísticas', 'Clientes'])
    expect(columna().getAllByRole('link').map((l) => l.textContent)).toEqual(['Pendientes', 'Historial', 'IMEIs', 'Stock actual', 'Rendimiento', 'Listado'])
    expect(columna().getByRole('link', { name: 'Rendimiento' })).toHaveAttribute('aria-current', 'page')
  })

  it('el técnico no tiene Pedidos ni Proveedores: Stock solo con «Stock actual» (0.9.16)', () => {
    renderConProviders(<SubNav />, { sesion: SESION_TEC, ruta: '/stock' })
    expect(columna().getByRole('link', { name: 'Stock actual' })).toHaveAttribute('aria-current', 'page')
    expect(columna().queryByRole('link', { name: 'Pedidos' })).not.toBeInTheDocument()
    expect(columna().queryByRole('link', { name: 'Proveedores' })).not.toBeInTheDocument()
  })
```

2. `cada enlace lleva su icono delante del nombre, decorativo` (línea 66): `sesion: SESION_TEC` → `sesion: SESION_ADMIN`. El admin tiene los cinco enlaces que recorre el test.

3. `la cabecera pliega su grupo sin navegar, lo recuerda y lo despliega` (líneas 97 y 101). El técnico sigue, pero el enlace que desaparece y vuelve es «Stock actual»:

```tsx
    expect(screen.queryByRole('link', { name: 'Stock actual' })).not.toBeInTheDocument()
```
```tsx
    expect(screen.getByRole('link', { name: 'Stock actual' })).toBeInTheDocument()
```

4. `plegado con la página actual dentro, la cabecera va en navy` (línea 122): `ruta: '/stock/pedidos'` → `ruta: '/stock'`.

5. Los casos sin sesión que usan «Pedidos» o «Proveedores» pasan a `sesion: SESION_ADMIN`. Es el rol que los tiene y no pide números al servidor (spec 3a §12). Solo se añade la sesión en las opciones de `renderConProviders`:
   - `abierta: 200 px, con los nombres y el botón «Ocultar menú lateral»` (línea 221): `{ ruta: '/stock' }` → `{ sesion: SESION_ADMIN, ruta: '/stock' }`.
   - `el botón la pliega a la franja de iconos, lo guarda y la vuelve a desplegar` (línea 233): `{ ruta: '/stock/pedidos' }` → `{ sesion: SESION_ADMIN, ruta: '/stock/pedidos' }`.
   - `plegar y desplegar no remonta los enlaces` (línea 285): `{ ruta: '/stock' }` → `{ sesion: SESION_ADMIN, ruta: '/stock' }`.
   - `al pasar el ratón por un icono sale «Grupo · Pestaña»` (línea 329), `también al llegar con el teclado` (línea 336), `al quitar el ratón del icono el nombre se va (no tapa el botón de la columna)` (línea 344) y `abierta no hay tooltip: el nombre ya se ve` (línea 354): `{ ruta: '/stock' }` → `{ sesion: SESION_ADMIN, ruta: '/stock' }`.

6. `plegada: sin cabeceras, todos los iconos (también los de grupos plegados) y una línea entre grupos` (líneas 258-266) sigue sin sesión, que cuenta como técnico. La lista pierde los dos enlaces:

```tsx
    expect(within(nav).getAllByRole('link').map((l) => l.getAttribute('aria-label'))).toEqual([
      'Reparaciones · Historial',
      'Reparaciones · IMEIs',
      'Stock · Stock actual',
      'Estadísticas · Rendimiento',
      'Clientes · Listado',
    ])
```

Las tres líneas entre grupos (`toHaveLength(3)`) no cambian.

- [ ] **Step 9: TopBar, Pedidos y Proveedores sin el técnico**

En `src/app/shell/TopBar.test.tsx`, línea 36: `ruta: '/stock/proveedores'` → `ruta: '/stock'`. El test va de «Cambiar contraseña» y vale cualquier ruta que el técnico vea.

En `src/modules/almacen/pedidos/PedidosPage.test.tsx`:
- Línea 151: el `it.each` de ADMIN y TECNICO queda solo para el admin:

```tsx
  it('ADMIN ve la tabla sin "Nuevo pedido" ni menú contextual (desde la 0.9.16 el TECNICO no entra en Pedidos)', async () => {
    montar(SESION_ADMIN)
```

  (el cuerpo sigue igual desde `await screen.findByText('lcd-x-negro')`).
- Línea 197 (`llegada desde "En Camino" de Stock…`): `montar(SESION_TEC, '/stock/pedidos?…')` → `montar(SESION_ADMIN, '/stock/pedidos?estados=pendiente%2Cen+camino%2Cparcial&buscar=bat-x')`. Desde la 0.9.16 el enlace «En Camino» lo tienen SUPERTECNICO y ADMIN.
- Línea 416 (`"Actualizado HH:mm" recarga las dos listas…`): `montar(SESION_TEC)` → `montar(SESION_ADMIN)`.
- Línea 12: quita `SESION_TEC` del import, que ya no se usa:

```tsx
import { renderConProviders, renderConRouter, SESION_ADMIN, SESION_SUPER } from '@/test/render'
```

En `src/modules/almacen/proveedores/ProveedoresPage.test.tsx`:
- Sustituye el caso `TECNICO y ADMIN: sin "Nuevo proveedor" ni menú` (líneas 61-70) por:

```tsx
  it('ADMIN: sin "Nuevo proveedor" ni menú (desde la 0.9.16 el TECNICO no entra en Proveedores)', async () => {
    montar(SESION_ADMIN)
    await screen.findByText('ACME')
    expect(screen.queryByRole('button', { name: 'Nuevo proveedor' })).not.toBeInTheDocument()
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('ACME') })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
```

- Línea 5: quita `SESION_TEC` del import:

```tsx
import { renderConProviders, SESION_ADMIN, SESION_SUPER } from '@/test/render'
```

En `src/modules/almacen/pedidos/api.ts`, los dos comentarios:
- Línea 15: `/** GET /api/compras (los tres roles). …` → `/** GET /api/compras (SUPERTECNICO y ADMIN desde la 0.9.16). …`. El resto del comentario no cambia.
- Línea 28: `/** GET /api/compras-otros (los tres roles), con las mismas reglas que useCompras. */` → `/** GET /api/compras-otros (SUPERTECNICO y ADMIN desde la 0.9.16), con las mismas reglas que useCompras. */`.

- [ ] **Step 10: Tests de la tarea, tipos y lint**

Run: `npx vitest run src/modules/almacen/rutas.test.tsx src/app/shell src/modules/almacen/pedidos/PedidosPage.test.tsx src/modules/almacen/proveedores/ProveedoresPage.test.tsx`
Expected: PASS.

Run: `npx tsc -b && npm run lint`
Expected: sin errores. El aviso de `BadgeEstadoPedido.tsx` ya existía.

- [ ] **Step 11: Commit**

```bash
git add src/modules/almacen/rutas.tsx src/modules/almacen/rutas.test.tsx \
        src/app/shell/navegacion.ts src/app/shell/navegacion.test.ts src/app/router.tsx \
        src/app/shell/SubNav.test.tsx src/app/shell/TopBar.test.tsx \
        src/modules/almacen/pedidos/PedidosPage.test.tsx src/modules/almacen/proveedores/ProveedoresPage.test.tsx \
        src/modules/almacen/pedidos/api.ts
git commit -m "feat: el tecnico sin pedidos ni proveedores en la columna y por url (0.9.16)"
```

---

### Task 3: Web — Stock actual: «En Camino» sin enlace y gráfico con lo que está en camino

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/columnas.tsx:70,85-95`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.tsx:128-135`
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/columnas.test.tsx` (caso nuevo tras el de la línea 38)
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.test.tsx:157-164` y caso nuevo

**Interfaces:**
- Consumes: nada de la Task 2 (son ficheros distintos); el técnico ya no tiene Pedidos.
- Produces: `crearColumnasStock({ onEnCamino?, conPrevision?, onAutoPedido? })`. Sin `onEnCamino`, la celda «En Camino» enseña el número como texto. `StockPage` solo lo pasa con `veEnCamino` (SUPERTECNICO y ADMIN).

- [ ] **Step 1: Tests que fallan**

En `src/modules/almacen/stock/columnas.test.tsx`, tras el caso `"En Camino" a 0 es texto "—"; > 0 es un enlace azul que avisa con el componente` (líneas 38-46), añade:

```tsx
  it('sin onEnCamino (TECNICO, 0.9.16) "En Camino" es el número como texto, sin botón', () => {
    render(<DataTable columns={crearColumnasStock({})} data={filas([c({ enCamino: 3 })])} vacio="Sin componentes" getRowId={(x) => String(x.idCom)} />)
    expect(document.querySelector('[data-columna="enCamino"]')).toHaveTextContent('3')
    expect(screen.queryByRole('button', { name: '3' })).not.toBeInTheDocument()
  })
```

En `src/modules/almacen/stock/StockPage.test.tsx`, sustituye el caso `el TECNICO no pide la cantidad en camino: la barra Pedido va a 0` (líneas 157-164) por estos dos:

```tsx
  it('el TECNICO no pide la cantidad en camino: la barra Pedido usa el «En Camino» de la fila (0.9.16)', async () => {
    let pedida = false
    // Si se pidiera, el gráfico diría 9: así se ve que el 4 es el de la fila (bat-x, enCamino 4).
    server.use(http.get('*/api/compras/cantidad-en-camino/:id', () => { pedida = true; return HttpResponse.json({ value: 9 }) }))
    montar(SESION_TEC)
    await screen.findByText('bat-x')
    await userEvent.click(screen.getByText('bat-x'))
    expect(await screen.findByRole('img', { name: 'Stock 2, Pedido 4' })).toBeInTheDocument()
    expect(pedida).toBe(false)
  })
  it('el TECNICO ve «En Camino» como número, sin enlace a Pedidos (0.9.16)', async () => {
    montar(SESION_TEC)
    await screen.findByText('bat-x')
    expect(within(filaDe('bat-x')).getByText('4')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '4' })).not.toBeInTheDocument()
  })
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/almacen/stock/columnas.test.tsx src/modules/almacen/stock/StockPage.test.tsx`
Expected: FAIL en los tres casos nuevos o cambiados. `crearColumnasStock({})` no compila con `onEnCamino` obligatorio (Vitest no comprueba tipos, así que en ejecución sale el botón y falla la aserción). El gráfico del técnico dice `Pedido 0` y su «En Camino» es un botón.

- [ ] **Step 3: `onEnCamino` opcional**

En `src/modules/almacen/stock/columnas.tsx`, la firma (línea 70):

```tsx
export function crearColumnasStock({ onEnCamino, conPrevision = false, onAutoPedido }: { onEnCamino?: (c: Componente) => void; conPrevision?: boolean; onAutoPedido?: (c: Componente, valor: boolean) => void }): ColumnDef<FilaStock>[] {
```

Y la celda de «En Camino» (líneas 85-95):

```tsx
    {
      id: 'enCamino', header: 'En Camino', size: ANCHOS_STOCK.enCamino, maxSize: ANCHOS_STOCK.enCamino,
      cell: ({ row }) => {
        const c = row.original
        if (c.enCamino <= 0) return '—'
        // Sin onEnCamino (TECNICO, 0.9.16: no tiene Pedidos) el número va como texto, igual que "En Stock".
        if (!onEnCamino) return String(c.enCamino)
        // Calco del Label con TEXTO_ACCION, cursor mano y subrayado al pasar (:305-324).
        return (
          <button type="button" onClick={(e) => { e.stopPropagation(); onEnCamino(c) }} className="cursor-pointer text-texto-accion hover:underline">
            {c.enCamino}
          </button>
        )
      },
    },
```

- [ ] **Step 4: StockPage: enlace y gráfico por rol**

En `src/modules/almacen/stock/StockPage.tsx`, sustituye las líneas 128-135 (el comentario del gráfico, `filaGrafico`, `graficoSku`, `irAPedidos` y `columnas`) por:

```tsx
  // Sin selección, "Selecciona un componente". El TECNICO no pide la cantidad: su barra "Pedido" usa el «En Camino» de la fila
  // (0.9.16), que es el mismo número (los pedidos se guardan en el master y la fila agrupada es la del master).
  const filaGrafico = veEnCamino ? (grafico ? filas.find((f) => f.idCom === grafico.idCom) ?? null : null) : seleccionado
  const graficoSku = !seleccionado || !filaGrafico ? null : { componente: filaGrafico, enCamino: veEnCamino ? grafico?.enCamino ?? 0 : filaGrafico.enCamino }

  const irAPedidos = useCallback((c: Componente) => navigate(`/stock/pedidos?${parametrosPedidos(c)}`), [navigate])
  const columnas = useMemo(
    // 0.9.16: «En Camino» solo abre Pedidos para quien tiene Pedidos (SUPERTECNICO y ADMIN, los mismos que ven la previsión).
    () => crearColumnasStock({ onEnCamino: veEnCamino ? irAPedidos : undefined, conPrevision: veEnCamino, onAutoPedido: (c, autoPedido) => marcarAutoPedido({ idCom: c.idCom, autoPedido }) }),
    [irAPedidos, veEnCamino, marcarAutoPedido])
```

- [ ] **Step 5: Comprobar que pasan**

Run: `npx vitest run src/modules/almacen/stock`
Expected: PASS, también `"En Camino" > 0 navega a Pedidos con los tres estados y el buscador` (SUPERTECNICO) y los del gráfico del supertécnico (`Stock 2, Pedido 4`).

Run: `npx tsc -b && npm run lint`
Expected: sin errores.

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/stock/columnas.tsx src/modules/almacen/stock/columnas.test.tsx \
        src/modules/almacen/stock/StockPage.tsx src/modules/almacen/stock/StockPage.test.tsx
git commit -m "feat: en camino sin enlace y grafico con lo pedido para el tecnico (0.9.16)"
```

---

### Task 4: Web — E2E de roles de Pedidos y Proveedores (solo lectura, preproducción)

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/roles-stock.spec.ts`
- Modify: `gestion-reparaciones-web/.env.e2e.example` (una línea de comentario)

**Interfaces:**
- Consumes: el comportamiento de las Tasks 1-3, desplegado en preprod (Task 6). Usa las variables `TEC_USER`/`TEC_PASS`, `E2E_USER`/`E2E_PASS` y `ADMIN_USER`/`ADMIN_PASS` de `~/.env.e2e`.
- Produces: `roles-stock.spec.ts`, que la Task 6 lanza contra preprod.

- [ ] **Step 1: La E2E**

Crea `tests/e2e/roles-stock.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// roles-stock.spec.ts (0.9.16, spec «El técnico sin Pedidos ni Proveedores»): el técnico no tiene Pedidos ni Proveedores
// (ni en la columna, ni por URL, ni por la API) y el supertécnico y el admin sí. Solo LEE: no crea ni cambia datos. Aun así,
// como todas las E2E, solo contra PREPRODUCCIÓN: se niega a seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'
const LECTURAS = ['/api/compras', '/api/compras-otros', '/api/proveedores?tipo=COMPONENTES']

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

/** Estado HTTP de cada lectura con el token de la sesión de la página (misma origin que la app; patrón de gestion.spec.ts). */
async function estados(page: Page): Promise<number[]> {
  return page.evaluate(async (rutas) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    return Promise.all(rutas.map(async (r) => (await fetch(r, { headers: { Authorization: `Bearer ${sesion.token}` } })).status))
  }, LECTURAS)
}

test('el técnico no tiene Pedidos ni Proveedores: ni en la columna, ni por URL, ni por la API', async ({ page }) => {
  const { usuario, clave } = credenciales('TEC_USER', 'TEC_PASS')
  await entrar(page, usuario, clave)
  const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
  await expect(columna.getByRole('link', { name: 'Stock actual', exact: true })).toBeVisible()
  await expect(columna.getByRole('link', { name: 'Pedidos', exact: true })).toHaveCount(0)
  await expect(columna.getByRole('link', { name: 'Proveedores', exact: true })).toHaveCount(0)
  for (const ruta of ['/stock/pedidos', '/stock/proveedores']) {
    await page.goto(ruta)
    await expect(page.getByRole('dialog', { name: 'Error' })).toContainText('No tienes permisos para realizar esta acción.')
    await expect(page).toHaveURL(/\/stock$/)
  }
  expect(await estados(page)).toEqual([403, 403, 403])
})

for (const [rol, varUsuario, varClave] of [['supertécnico', 'E2E_USER', 'E2E_PASS'], ['admin', 'ADMIN_USER', 'ADMIN_PASS']] as const) {
  test(`el ${rol} sigue con Pedidos y Proveedores`, async ({ page }) => {
    const { usuario, clave } = credenciales(varUsuario, varClave)
    await entrar(page, usuario, clave)
    const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
    await columna.getByRole('link', { name: 'Pedidos', exact: true }).click()
    await expect(page).toHaveURL(/\/stock\/pedidos$/)
    await expect(page.getByRole('heading', { name: 'Pedidos', exact: true })).toBeVisible()
    await columna.getByRole('link', { name: 'Proveedores', exact: true }).click()
    await expect(page).toHaveURL(/\/stock\/proveedores$/)
    await expect(page.getByRole('heading', { name: 'Proveedores', exact: true })).toBeVisible()
    expect(await estados(page)).toEqual([200, 200, 200])
  })
}
```

- [ ] **Step 2: Línea en `.env.e2e.example`**

Al final de `.env.e2e.example`, con una línea en blanco antes:

```
# roles-stock.spec.ts SOLO LEE (0.9.16, solo PREPRODUCCION: se niega a correr sin el letrero PREPRODUCCION): con TEC_USER
# comprueba que no hay Pedidos ni Proveedores (columna, URL y API 403); con E2E_USER y ADMIN_USER, que si (200).
```

- [ ] **Step 3: Que Playwright la encuentre y compile**

Run: `npx playwright test --list tests/e2e/roles-stock.spec.ts`
Expected: 3 tests listados (`el técnico no tiene…`, `el supertécnico sigue…`, `el admin sigue…`). No se lanza aquí: necesita preprod con la 0.9.16 (Task 6).

Run: `npx tsc -b && npm run lint`
Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/roles-stock.spec.ts .env.e2e.example
git commit -m "test: e2e de solo lectura de pedidos y proveedores por rol (0.9.16)"
```

---

### Task 5: Cierre — versión, documentación, suites y entrega

**Files:**
- Modify: `gestion-reparaciones-web/package.json` y `gestion-reparaciones-web/package-lock.json` (versión).
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (entrada `[0.9.16]`).
- Modify: `gestion-reparaciones-web/docs/paridad/pedidos.md` y `gestion-reparaciones-web/docs/paridad/stock.md` (sección 0.9.16).
- Create: `docs/novedades/NOVEDADES-v0.9.16.md` (raíz; lo sube el controlador con los gitlinks).

**Interfaces:**
- Consumes: todo lo de las Tasks 1 a 4.
- Produces: las ramas `dev_v0916_tecnico_sin_pedidos` (servidor y web) listas para el merge.

- [ ] **Step 1: Versión de la web**

Run (en `/c/Users/dev/Documents/wt/web-0916`): `npm version 0.9.16 --no-git-tag-version`
Expected: `v0.9.16`; `package.json` y las dos entradas de la raíz de `package-lock.json` en `0.9.16`.

- [ ] **Step 2: CHANGELOG de la web**

En `CHANGELOG.md`, tras la línea `El formato sigue [Keep a Changelog]…` y su línea en blanco, antes de `## [0.9.15]`:

```markdown
## [0.9.16] - 2026-10-XX — El técnico sin Pedidos ni Proveedores

- **Pedidos y Proveedores son del supertécnico y del administrador.** El técnico ya no los tiene en la columna: el grupo Stock le queda con «Stock actual». Si entra por la dirección, ve «No tienes permisos para realizar esta acción.» y vuelve a Stock actual. El administrador sigue viéndolos en solo lectura.
- **Stock actual del técnico:** «En Camino» es el número, sin enlace a Pedidos, y la barra «Pedido» del gráfico de la pieza enseña lo que está en camino (antes marcaba 0).
- Requiere el servidor 0.9.16: las lecturas de pedidos y proveedores (`GET /api/compras`, `/api/compras-otros` y `/api/proveedores`) dan 403 al técnico.
```

- [ ] **Step 3: Fichas de paridad**

Al final de `docs/paridad/pedidos.md`, con una línea en blanco antes:

```markdown
## 0.9.16 — El técnico sin Pedidos (diferencia con el JavaFX)

Diferencia deliberada, decidida en la spec `2026-10-15-v0916-tecnico-sin-pedidos-design.md` (raíz): el TECNICO ya no ve Pedidos, que en el JavaFX y hasta la 0.9.15 veía en solo lectura. Lo de arriba sobre «ADMIN y TECNICO sin "Nuevo…" ni menú» vale ahora solo para el ADMIN. Por URL, el técnico recibe el aviso de permisos y vuelve a Stock actual (`rutas.test.tsx`, `roles-stock.spec.ts`); el servidor le responde 403 (`RolesLecturasPedidosTest`).
```

Al final de `docs/paridad/stock.md`, con una línea en blanco antes:

```markdown
## 0.9.16 — El técnico sin Pedidos ni Proveedores (diferencia con el JavaFX)

Diferencias deliberadas, decididas en la spec `2026-10-15-v0916-tecnico-sin-pedidos-design.md` (raíz):

- La columna del técnico solo tiene «Stock actual»; Pedidos y Proveedores son de SUPERTECNICO y ADMIN (`rutas.test.tsx`, `navegacion.test.ts`, `SubNav.test.tsx`).
- Para el técnico, «En Camino» es el número sin enlace a Pedidos (`columnas.test.tsx`, `StockPage.test.tsx`).
- La barra «Pedido» del gráfico por SKU del técnico usa el «En Camino» de la fila; en el JavaFX y hasta la 0.9.15 iba a 0 (`StockPage.test.tsx`).
```

- [ ] **Step 4: Novedades (raíz, sin commit)**

Crea `C:/Users/dev/Documents/ProgramaReparaciones/docs/novedades/NOVEDADES-v0.9.16.md`:

```markdown
# 🎉 Novedades — Versión 0.9.16 (web)

**Pedidos** y **Proveedores** pasan a ser solo del supertécnico y del administrador.

---

## 🧰 Técnicos

- En la columna, el grupo **Stock** queda con **Stock actual**: ahí se ve cuántas piezas hay, cuántas están **en camino**,
  el stock mínimo y la fecha del último pedido.
- En el gráfico de una pieza, la barra **«Pedido»** ya enseña lo que está en camino (antes salía a 0).
- El número de «En Camino» ya no abre Pedidos.

---

## 👀 Supertécnicos y administrador

Nada cambia: Pedidos y Proveedores siguen igual (el administrador, en solo lectura).
```

- [ ] **Step 5: Suites completas**

Run (servidor): `mvn -q test` en `/c/Users/dev/Documents/wt/servidor-0916`
Expected: `BUILD SUCCESS`, sin fallos. Anota el total de tests en el informe.

Run (web): `npm run check` en `/c/Users/dev/Documents/wt/web-0916`
Expected: lint con 0 errores (el aviso de `BadgeEstadoPedido.tsx` ya existía), `tsc -b` limpio y todos los tests de Vitest en verde. Anota el total.

Run (web): `npm run build`
Expected: build sin errores.

- [ ] **Step 6: Commit de la web**

```bash
git add package.json package-lock.json CHANGELOG.md docs/paridad/pedidos.md docs/paridad/stock.md
git commit -m "docs: version 0.9.16, changelog y paridad del tecnico sin pedidos"
```

- [ ] **Step 7: Entrega (controlador, con OK del usuario)**

1. Revisión final de las dos ramas.
2. Con el OK del usuario:
   - merge `--no-ff` de `dev_v0916_tecnico_sin_pedidos` en `main` del servidor y de la web, con push de los dos;
   - en la raíz, gitlinks + `NOVEDADES-v0.9.16.md` + el plan marcado.
3. Worktrees y ramas se borran después de producción.

---

### Task 6: Preproducción

**Files:**
- Create: `Apuntes/preprod/v0916-preprod.md` (guion; fuera de los repos).
- Modify: `Apuntes/despliegue_preprod.md` (registro).

- [ ] **Step 1: Guion**

Basado en `preprod/v0915-preprod.md`, **sin ningún paso de base de datos**: no hay migración ni reconstrucción. **Cada bloque empieza con `hostname` y el seguro de `backup-erp.sh`.**

- **Bloque 1 (solo lee):** commits y contenedores.
- **Bloque 2 (escribe), con el OK de Claude:**
  - `pull` de servidor y web;
  - `docker compose up -d --build`. Avisar de que tarda ~1 min sin salida;
  - `Started App` sin `ERROR`.
- **Comprobaciones de Claude desde el PC:**
  - `/version.json` da 0.9.16;
  - E2E de una en una, con ≥ 60 s entre ficheros: `roles-stock` (nueva), `stock`, `pedidos` y `columna-lateral`.
- **Vuelta atrás:** código anterior (servidor `c65ce4f`, web `ead5a0f`) y `up -d --build`.

- [ ] **Step 2: Ejecución con el usuario y registro**

1. El usuario pega los bloques y Claude valida cada salida antes de seguir.
2. Al final, entrada en `despliegue_preprod.md` con los commits y el resultado de las E2E.
3. Si una E2E falla por la prueba y no por el programa, se arregla en la rama, se vuelve a pasar y se apunta.

---

### Task 7: Producción

**Files:**
- Create: `Apuntes/prod-v0916.md` (guion).
- Modify: `Apuntes/despliegue_vdc_produccion.md` (registro).

- [ ] **Step 1: Guion**

Basado en `prod-v0915.md`, sin los pasos de base de datos. **Cada bloque empieza con `hostname` y la comprobación `PRODUCCIÓN (ok)`.**

- **Bloque 1:** copia `backup-erp.sh`, commits y contenedores.
- **Bloque 2:** `pull` de servidor y web, y `up -d --build`.
- **Comprobación ligera desde el PC, sin escribir datos:**
  - versión 0.9.16 en `/version.json`;
  - sin letrero de preproducción (`preprod/comprobar-letrero.cjs` con `URL=https://erp.fonestore.es ESPERA=no`);
  - API sin sesión → 403.
- **A mano (usuario, sin guardar nada):** con una cuenta de técnico, el grupo Stock solo con «Stock actual»; con la suya de supertécnico, Pedidos y Proveedores como siempre.
- **Momento:** en horario (sin base de datos, corte de unos 30 s, web vieja compatible).
- **Aviso al taller:** «Hemos actualizado el programa. Pedidos y Proveedores ya solo los ven los supertécnicos; lo que está en camino se sigue viendo en Stock actual. Recargad la página. Novedades en `NOVEDADES-v0.9.16`.»
- **Vuelta atrás:** servidor `c65ce4f` y web `ead5a0f` + `up -d --build`, en cualquier orden.

- [ ] **Step 2: Ejecución con el usuario y cierre**

1. Registro en `despliegue_vdc_produccion.md`.
2. Fecha real en `[0.9.16]` del CHANGELOG de la web: commit, push y gitlink.
3. `plan-futuro.md`: la 0.9.16 añadida y marcada `[x]`.
4. Tags `v0.9.16` cuando lo diga el usuario.
5. Worktrees y ramas borrados.
