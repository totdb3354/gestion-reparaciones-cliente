# 0.9.6 — Pedido automático desde la previsión: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Casilla «Auto» por componente en Stock (SUPERTECNICO y ADMIN) y, en «Nuevo pedido», proveedor general + «Aplicar a todas» + «Añadir previsión (N)», que mete las piezas marcadas con su «Pedir 60 d».

**Architecture:** Columna `Componente.AUTO_PEDIDO` (migración aditiva) leída del master en el listado de Stock (`autoPedido`, nulo para TECNICO) y escrita con `PATCH /api/componentes/{idCom}/auto-pedido`. En la web, la casilla guarda al momento con actualización optimista; el modal reutiliza el mismo listado (ya trae `pedir60` y `autoPedido`) y la regla del botón es una función pura en `formulario/lineas.ts`.

**Tech Stack:** Spring Boot 3.3 + JdbcTemplate + MariaDB (servidor, JUnit 5 + Mockito + MockMvc); React + TanStack Query/Table + Radix + openapi-typescript (web, Vitest + Testing Library + MSW).

**Spec:** `docs/superpowers/specs/2026-10-08-v096-pedido-automatico-design.md` (raíz).

## Global Constraints

- Versión **0.9.6**: la web ya está en `0.9.6` en `main` (`81de9fe`); el servidor `main` es `4673711`. Se etiquetan juntos al final, **solo con OK del usuario**.
- Ramas `feature/pedido-automatico` en **servidor** y **web**, creadas desde su `main`. Merge `--no-ff`, push y tag **solo con OK del usuario**.
- Commits en español sin tildes, prefijo `feat:` / `test:` / `docs:` / `chore:`. **Sin** línea `Co-Authored-By`.
- Repos **públicos**: nada de IPs, nombres reales ni datos del taller en código, tests ni docs. Datos de tests sintéticos.
- Quién ve y cambia la marca: **SUPERTECNICO y ADMIN**. Al TECNICO el listado le manda `autoPedido: null`.
- La marca vive en el **master** del grupo compartido. Marcar **no cambia `UPDATED_AT`**.
- Textos exactos de la UI: columna `Auto`; CSV `Sí` / `No`; botones `Aplicar a todas` y `Añadir previsión (N)`; combo `Proveedor:` con `aria-label="Proveedor general"`; aviso `1 pieza marcada no necesita pedido.` / `N piezas marcadas no necesitan pedido.`
- Servidor, comandos Maven (Git Bash):
  `export JAVA_HOME=$(ls -d /c/Users/dev/tools/jdk* | head -1); export PATH="$JAVA_HOME/bin:$(ls -d /c/Users/dev/tools/*maven*/bin | head -1):$PATH"`
  y luego `mvn -q test` (o `mvn -q test -Dtest=Clase`) dentro de `gestion-reparaciones-servidor`.
- Web: `npx vitest run <ruta>` y `npx tsc -b` dentro de `gestion-reparaciones-web`.

---

### Task 1: Servidor — columna AUTO_PEDIDO, modelo y DAO

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-auto-pedido.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql:40-51` (tabla `Componente`)
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/Componente.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java` (`getAllGestionados`, método nuevo `setAutoPedido`)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/dao/ComponenteDAOAutoPedidoTest.java`

**Interfaces:**
- Produces: `Componente.getAutoPedido(): Boolean`, `Componente.setAutoPedido(Boolean)`; `ComponenteDAO.setAutoPedido(int idCom, boolean autoPedido): int` (devuelve el id del master; id inexistente → `EmptyResultDataAccessException`); `getAllGestionados()` rellena `autoPedido` con el valor del master.

- [ ] **Step 1: Rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git switch main && git pull && git switch -c feature/pedido-automatico
```

- [ ] **Step 2: Test que falla**

Crear `src/test/java/com/reparaciones/servidor/dao/ComponenteDAOAutoPedidoTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Componente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Marca del pedido automático (spec 0.9.6 §4.2): en el master del grupo y sin tocar UPDATED_AT. */
class ComponenteDAOAutoPedidoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComponenteDAO dao = new ComponenteDAO(jdbc);

    @Test void laMarcaDeUnSlaveSeGuardaEnSuMasterSinTocarUpdatedAt() {
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        assertEquals(3, dao.setAutoPedido(12, true));
        verify(jdbc).update("UPDATE Componente SET AUTO_PEDIDO = ?, UPDATED_AT = UPDATED_AT WHERE ID_COM = ?", true, 3);
    }

    @Test void unIdInexistenteNoActualizaNada() {
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(99)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThrows(EmptyResultDataAccessException.class, () -> dao.setAutoPedido(99, true));
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    @Test @SuppressWarnings("unchecked")
    void elListadoDeStockTraeLaMarcaDelMaster() throws Exception {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<Componente>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(sql.capture(), mapper.capture())).thenReturn(List.of());
        dao.getAllGestionados();
        assertTrue(sql.getValue().contains("COALESCE(master.AUTO_PEDIDO, c.AUTO_PEDIDO) AS AUTO_PEDIDO"));

        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf(LocalDateTime.of(2026, 10, 8, 10, 0));
        when(rs.getInt("ID_COM")).thenReturn(5);
        when(rs.getString("TIPO")).thenReturn("bat-y");
        when(rs.getTimestamp("FECHA_REGISTRO")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getBoolean("ACTIVO")).thenReturn(true);
        when(rs.getBoolean("AUTO_PEDIDO")).thenReturn(true);
        when(rs.wasNull()).thenReturn(true);
        assertEquals(Boolean.TRUE, mapper.getValue().mapRow(rs, 0).getAutoPedido());
    }
}
```

- [ ] **Step 3: Comprobar que falla**

Run: `mvn -q test -Dtest=ComponenteDAOAutoPedidoTest`
Expected: error de compilación (`setAutoPedido` / `getAutoPedido` no existen).

- [ ] **Step 4: Modelo**

En `Componente.java`, tras el campo `pedir60`:

```java
    // Pedido automático (spec 0.9.6 §4): la marca del master; solo para SUPERTECNICO y ADMIN.
    @Schema(nullable = true) private Boolean autoPedido;
```

y tras `setPrevision(...)`:

```java
    public Boolean getAutoPedido()                { return autoPedido; }
    public void    setAutoPedido(Boolean autoPedido) { this.autoPedido = autoPedido; }
```

- [ ] **Step 5: DAO**

En `getAllGestionados()`, añadir la línea tras `COALESCE(master.STOCK_MINIMO, c.STOCK_MINIMO) AS STOCK_MINIMO,`:

```sql
                       COALESCE(master.AUTO_PEDIDO, c.AUTO_PEDIDO) AS AUTO_PEDIDO,
```

y en su mapper, tras `c.setEnCamino(rs.getInt("EN_CAMINO"));`:

```java
            c.setAutoPedido(rs.getBoolean("AUTO_PEDIDO"));
```

Método nuevo, justo después de `setStockMinimo`:

```java
    /** Marca del pedido automático (spec 0.9.6 §4.2), en el master del grupo. No toca UPDATED_AT: "Editar stock" lo usa
     *  para detectar ediciones simultáneas y marcar no es editar. Devuelve el id del master; un id inexistente lanza
     *  EmptyResultDataAccessException (resolveToMasterId). */
    public int setAutoPedido(int idCom, boolean autoPedido) {
        int master = resolveToMasterId(idCom);
        jdbc.update("UPDATE Componente SET AUTO_PEDIDO = ?, UPDATED_AT = UPDATED_AT WHERE ID_COM = ?", autoPedido, master);
        return master;
    }
```

- [ ] **Step 6: Migración y crear_bd**

Crear `sql/migracion-auto-pedido.sql`:

```sql
-- 0.9.6: marca del pedido automático por componente (spec 2026-10-08 §4.1). En un grupo compartido vale la del master.
-- Aditivo: el servidor 0.9.5 sigue funcionando sobre este esquema. Tras aplicarla no queda ninguna pieza marcada.
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.6. Antes de aplicar, hacer una copia de la base.
-- REEJECUCIÓN: idempotente (ADD COLUMN IF NOT EXISTS de MariaDB).
USE gestion_reparaciones;

ALTER TABLE Componente ADD COLUMN IF NOT EXISTS AUTO_PEDIDO BOOLEAN NOT NULL DEFAULT FALSE;

-- Verificación post: SELECT COUNT(*) FROM Componente WHERE AUTO_PEDIDO;  -- 0
```

En `sql/crear_bd.sql`, dentro de `CREATE TABLE Componente`, tras la línea `ID_COM_MASTER  INT          NULL,`:

```sql
    AUTO_PEDIDO    BOOLEAN      NOT NULL DEFAULT FALSE,
```

- [ ] **Step 7: Comprobar que pasa**

Run: `mvn -q test -Dtest=ComponenteDAOAutoPedidoTest`
Expected: PASS (sin salida de error, exit 0).

- [ ] **Step 8: Commit**

```bash
git add sql/migracion-auto-pedido.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/model/Componente.java src/main/java/com/reparaciones/servidor/dao/ComponenteDAO.java src/test/java/com/reparaciones/servidor/dao/ComponenteDAOAutoPedidoTest.java
git commit -m "feat: marca de pedido automatico en el master del componente sin tocar updated_at"
```

---

### Task 2: Servidor — endpoint PATCH, marca oculta al TECNICO y contrato

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ComponenteController.java` (`getAllGestionados`, endpoint nuevo, record nuevo)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/ComponenteControllerAutoPedidoTest.java` (nuevo)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/controller/RolesParametrosPrevisionTest.java` (método nuevo)
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/OpenApiContractTest.java:208`

**Interfaces:**
- Consumes: `ComponenteDAO.setAutoPedido(int, boolean): int`, `Componente.setAutoPedido(Boolean)` (Task 1).
- Produces: `PATCH /api/componentes/{idCom}/auto-pedido` con cuerpo `{"autoPedido": boolean}` (esquema OpenAPI `ComponenteAutoPedidoRequest`, operationId `setAutoPedido`), 200 sin cuerpo, 404 si no existe, 403 para TECNICO; campo `autoPedido` (boolean nulable) en `Componente`.

- [ ] **Step 1: Tests que fallan**

Crear `src/test/java/com/reparaciones/servidor/controller/ComponenteControllerAutoPedidoTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.PrevisionPedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pedido automático (spec 0.9.6 §4.2): la marca se guarda en el master, se apunta y solo la ven SUPER y ADMIN. */
class ComponenteControllerAutoPedidoTest {

    private static final UsuarioPrincipal SUPER = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteController ctl = new ComponenteController(dao, logDao, mock(PrevisionPedidoService.class));

    @Test void marcarGuardaEnElMasterYLoApunta() {
        when(dao.setAutoPedido(12, true)).thenReturn(3);
        ctl.setAutoPedido(12, new ComponenteController.AutoPedidoRequest(true), SUPER);
        verify(logDao).insertar(7, "EDITAR_COMPONENTE", "ID_COM: 3, AUTO_PEDIDO: SI");
    }

    @Test void desmarcarApuntaNo() {
        when(dao.setAutoPedido(3, false)).thenReturn(3);
        ctl.setAutoPedido(3, new ComponenteController.AutoPedidoRequest(false), SUPER);
        verify(logDao).insertar(7, "EDITAR_COMPONENTE", "ID_COM: 3, AUTO_PEDIDO: NO");
    }

    @Test void unIdInexistenteDa404SinApuntar() {
        when(dao.setAutoPedido(99, true)).thenThrow(new EmptyResultDataAccessException(1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.setAutoPedido(99, new ComponenteController.AutoPedidoRequest(true), SUPER));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verifyNoInteractions(logDao);
    }

    @Test void alTecnicoLeLlegaLaMarcaNula() {
        Componente c = new Componente();
        c.setAutoPedido(true);
        when(dao.getAllGestionados()).thenReturn(List.of(c));
        ctl.getAllGestionados(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        assertNull(c.getAutoPedido());
    }

    @Test void alSupertecnicoYAlAdminSeLaDeja() {
        Componente c = new Componente();
        c.setAutoPedido(true);
        when(dao.getAllGestionados()).thenReturn(List.of(c));
        ctl.getAllGestionados(SUPER);
        ctl.getAllGestionados(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        assertEquals(Boolean.TRUE, c.getAutoPedido());
    }
}
```

En `RolesParametrosPrevisionTest.java`, añadir tras `stockMinimoSoloAdmin()`:

```java
    @Test void autoPedidoSupertecnicoYAdmin() throws Exception {
        String cuerpo = "{\"autoPedido\":true}";
        assertEquals(403, status(json(patch("/api/componentes/5/auto-pedido"), cuerpo), tecnico()));
        verify(componenteDao, never()).setAutoPedido(anyInt(), anyBoolean());
        assertEquals(200, status(json(patch("/api/componentes/5/auto-pedido"), cuerpo), supertecnico()));
        assertEquals(200, status(json(patch("/api/componentes/5/auto-pedido"), cuerpo), admin()));
        verify(componenteDao, times(2)).setAutoPedido(5, true);
    }
```

En `OpenApiContractTest.java`, sustituir la línea
`assertNullable(esquemas, "Componente", "consumoDiario", "pedir60");`
por:

```java
        assertNullable(esquemas, "Componente", "consumoDiario", "pedir60", "autoPedido");
        assertTrue(paths.has("/api/componentes/{idCom}/auto-pedido"), "falta /api/componentes/{idCom}/auto-pedido en el contrato");
```

- [ ] **Step 2: Comprobar que fallan**

Run: `mvn -q test -Dtest='ComponenteControllerAutoPedidoTest,RolesParametrosPrevisionTest,OpenApiContractTest'`
Expected: error de compilación (`setAutoPedido` y `AutoPedidoRequest` no existen en el controller).

- [ ] **Step 3: Implementación**

En `ComponenteController.java`, sustituir el javadoc y el cuerpo de `getAllGestionados`:

```java
    /** Listado de Stock. La previsión de pedidos (consumo/día y pedir para 60 días) y la marca del pedido automático son
     *  información de compras: solo para SUPERTECNICO y ADMIN; a un TECNICO le llegan los tres campos nulos
     *  (spec 0.9.5 §3.3, spec 0.9.6 §4.2). */
    @GetMapping("/gestionados")
    public List<Componente> getAllGestionados(@AuthenticationPrincipal UsuarioPrincipal principal) {
        List<Componente> lista = dao.getAllGestionados();
        if (principal != null && ("SUPERTECNICO".equals(principal.getRol()) || "ADMIN".equals(principal.getRol())))
            prevision.rellenar(lista, LocalDate.now(MADRID));
        else
            lista.forEach(c -> c.setAutoPedido(null));
        return lista;
    }
```

Endpoint nuevo, justo después de `setStockMinimo(...)`:

```java
    /** Marca del pedido automático (spec 0.9.6 §4.2): se guarda en el master del grupo y se apunta con su id. */
    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
    @PatchMapping("/{idCom}/auto-pedido")
    public void setAutoPedido(@PathVariable int idCom, @RequestBody AutoPedidoRequest req,
                              @AuthenticationPrincipal UsuarioPrincipal principal) {
        int master;
        try {
            master = dao.setAutoPedido(idCom, req.autoPedido());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + idCom);
        }
        logDao.insertar(principal.getIdUsu(), "EDITAR_COMPONENTE",
                "ID_COM: " + master + ", AUTO_PEDIDO: " + (req.autoPedido() ? "SI" : "NO"));
    }
```

Junto a los demás records del final (tras `record StockMinimoRequest(int stockMinimo) {}`):

```java
    record AutoPedidoRequest(boolean autoPedido) {}
```

- [ ] **Step 4: Comprobar que pasan, y la suite entera**

Run: `mvn -q test -Dtest='ComponenteControllerAutoPedidoTest,RolesParametrosPrevisionTest,OpenApiContractTest'`
Expected: PASS.
Run: `mvn -q test`
Expected: exit 0 (los WARN de log de los tests son normales).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ComponenteController.java src/test/java/com/reparaciones/servidor/controller/ComponenteControllerAutoPedidoTest.java src/test/java/com/reparaciones/servidor/controller/RolesParametrosPrevisionTest.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat: endpoint de la marca de pedido automatico para supertecnico y admin, oculta al tecnico"
```

---

### Task 3: Web — contrato, tipos y hook `useMarcarAutoPedido`

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json` (esquema `Componente`, ruta nueva, esquema nuevo)
- Regenerate: `gestion-reparaciones-web/src/shared/api/schema.d.ts` (con `npm run api:types:offline`)
- Modify (fixtures, `autoPedido: null`): los 10 ficheros de `src/` que contienen `pedir60: null`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/api.ts`
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/api.test.tsx`

**Interfaces:**
- Consumes: el contrato de Task 2.
- Produces: `Componente.autoPedido: boolean | null` (tipo generado); `useMarcarAutoPedido(): UseMutationResult<unknown, unknown, { idCom: number; autoPedido: boolean }, { antes: Componente[] | undefined }>` en `modules/almacen/stock/api.ts`.

- [ ] **Step 1: Rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git switch main && git pull && git switch -c feature/pedido-automatico
```

- [ ] **Step 2: Contrato a mano en `api/openapi.json`**

(El fichero usa el formato `"clave" : valor` de Jackson; editar con Edit, no reescribirlo con JSON.stringify.)

a) En el esquema `"Componente"`, la lista `required` pasa de
`[ "activo", "consumoDiario", ...` a `[ "activo", "autoPedido", "consumoDiario", ...` (el resto igual).

b) En sus `properties`, sustituir el final

```json
          "pedir60" : {
            "type" : "integer",
            "format" : "int32",
            "nullable" : true
          }
        }
      },
```

por

```json
          "pedir60" : {
            "type" : "integer",
            "format" : "int32",
            "nullable" : true
          },
          "autoPedido" : {
            "type" : "boolean",
            "nullable" : true
          }
        }
      },
```

c) Tras el bloque de la ruta `"/api/componentes/{idCom}/stock-minimo" : { ... },` (termina antes de `"/api/componentes/{idCom}/activo"`), insertar:

```json
    "/api/componentes/{idCom}/auto-pedido" : {
      "patch" : {
        "tags" : [ "componente-controller" ],
        "operationId" : "setAutoPedido",
        "parameters" : [ {
          "name" : "idCom",
          "in" : "path",
          "required" : true,
          "schema" : {
            "type" : "integer",
            "format" : "int32"
          }
        } ],
        "requestBody" : {
          "content" : {
            "application/json" : {
              "schema" : {
                "$ref" : "#/components/schemas/ComponenteAutoPedidoRequest"
              }
            }
          },
          "required" : true
        },
        "responses" : {
          "200" : {
            "description" : "OK"
          }
        }
      }
    },
```

d) Tras el esquema `"ComponenteStockMinimoRequest" : { ... },`, insertar:

```json
      "ComponenteAutoPedidoRequest" : {
        "required" : [ "autoPedido" ],
        "type" : "object",
        "properties" : {
          "autoPedido" : {
            "type" : "boolean"
          }
        }
      },
```

- [ ] **Step 3: Regenerar tipos y comprobar el diff**

Run: `npm run api:types:offline && git diff --stat src/shared/api/schema.d.ts`
Expected: solo cambia `schema.d.ts` con la ruta `/api/componentes/{idCom}/auto-pedido`, la operación `setAutoPedido`, el esquema `ComponenteAutoPedidoRequest` y `autoPedido: boolean | null` en `Componente`. Si el diff toca otras cosas, parar y revisar el JSON.

- [ ] **Step 4: Fixtures**

Run: `grep -rl "pedir60: null" src | xargs sed -i 's/pedir60: null/pedir60: null, autoPedido: null/'`
Run: `npx tsc -b`
Expected: sin errores. Si queda algún objeto `Componente` sin `autoPedido` (tsc lo dice), añadirle `autoPedido: null`.

- [ ] **Step 5: Test que falla**

En `src/modules/almacen/stock/api.test.tsx`, ampliar el import a
`import { pedirCantidadEnCamino, useComponentesStock, useEditarStock, useMarcarAutoPedido } from './api'`
y añadir dentro del `describe`:

```tsx
  it('useMarcarAutoPedido pinta la marca al momento en el master y sus slaves, manda el PATCH e invalida', async () => {
    guardarSesion(SESION_SUPER)
    let cuerpo: unknown = null
    server.use(http.patch('*/api/componentes/1/auto-pedido', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 200 }) }))
    const { qc, wrapper } = envoltorio()
    qc.setQueryData(['componentes', 'gestionados'], [
      { ...base, idCom: 1, tipo: 'lcd-x', stock: 5, activo: true, autoPedido: false },
      { ...base, idCom: 2, tipo: 'lcd-y', stock: 5, activo: true, idComMaster: 1, autoPedido: false },
      { ...base, idCom: 3, tipo: 'bat-x', stock: 5, activo: true, autoPedido: false },
    ])
    const { result } = renderHook(() => useMarcarAutoPedido(), { wrapper })
    const promesa = result.current.mutateAsync({ idCom: 1, autoPedido: true })
    await waitFor(() => expect(qc.getQueryData<{ idCom: number; autoPedido: boolean }[]>(['componentes', 'gestionados'])?.map((c) => c.autoPedido)).toEqual([true, true, false]))
    await promesa
    expect(cuerpo).toEqual({ autoPedido: true })
    expect(qc.getQueryState(['componentes', 'gestionados'])?.isInvalidated).toBe(true)
  })
  it('useMarcarAutoPedido vuelve atrás si el servidor falla', async () => {
    guardarSesion(SESION_SUPER)
    server.use(http.patch('*/api/componentes/3/auto-pedido', () => HttpResponse.json({ message: 'fallo' }, { status: 500 })))
    const { qc, wrapper } = envoltorio()
    const antes = [{ ...base, idCom: 3, tipo: 'bat-x', stock: 5, activo: true, autoPedido: false }]
    qc.setQueryData(['componentes', 'gestionados'], antes)
    const { result } = renderHook(() => useMarcarAutoPedido(), { wrapper })
    await expect(result.current.mutateAsync({ idCom: 3, autoPedido: true })).rejects.toBeDefined()
    expect(qc.getQueryData<{ autoPedido: boolean }[]>(['componentes', 'gestionados'])?.[0].autoPedido).toBe(false)
  })
```

(`base` de este fichero ya llevará `autoPedido: null` tras el Step 4; cada objeto lo sobrescribe.)

- [ ] **Step 6: Comprobar que falla**

Run: `npx vitest run src/modules/almacen/stock/api.test.tsx`
Expected: FAIL (`useMarcarAutoPedido` no se exporta).

- [ ] **Step 7: Implementación**

En `src/modules/almacen/stock/api.ts`, tras `useAjustarMinimo`:

```ts
type MarcaAutoPedido = { idCom: number; autoPedido: boolean }

/** Casilla «Auto» de Stock (spec 0.9.6 §4.3): se ve marcada al momento (en la fila del master y en sus slaves, que
 *  comparten la marca); si el PATCH falla vuelve a como estaba y el error lo enseña el diálogo global. */
export function useMarcarAutoPedido(): UseMutationResult<unknown, unknown, MarcaAutoPedido, { antes: Componente[] | undefined }> {
  const qc = useQueryClient()
  const recargar = useRecarga()
  return useMutation({
    mutationFn: ({ idCom, autoPedido }: MarcaAutoPedido) =>
      api.PATCH('/api/componentes/{idCom}/auto-pedido', { params: { path: { idCom } }, body: { autoPedido } }),
    onMutate: async ({ idCom, autoPedido }: MarcaAutoPedido) => {
      await qc.cancelQueries({ queryKey: CLAVE_COMPONENTES_GESTIONADOS })
      const antes = qc.getQueryData<Componente[]>(CLAVE_COMPONENTES_GESTIONADOS)
      qc.setQueryData<Componente[]>(CLAVE_COMPONENTES_GESTIONADOS, (ls) =>
        ls?.map((c) => (c.idCom === idCom || c.idComMaster === idCom ? { ...c, autoPedido } : c)))
      return { antes }
    },
    onError: (_e, _v, contexto) => {
      if (contexto?.antes !== undefined) qc.setQueryData(CLAVE_COMPONENTES_GESTIONADOS, contexto.antes)
    },
    onSettled: recargar,
  })
}
```

- [ ] **Step 8: Comprobar que pasa**

Run: `npx vitest run src/modules/almacen/stock/api.test.tsx && npx tsc -b`
Expected: PASS y tsc sin errores.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: contrato y hook de la marca de pedido automatico con actualizacion optimista"
```

---

### Task 4: Web — columna «Auto» en Stock y en el CSV

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/prevision.ts` (`formatearAuto`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/columnas.tsx`
- Modify: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.tsx:135`
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/columnas.test.tsx`
- Test: `gestion-reparaciones-web/src/modules/almacen/stock/StockPage.test.tsx`

**Interfaces:**
- Consumes: `useMarcarAutoPedido()` (Task 3), `Componente.autoPedido`.
- Produces: `crearColumnasStock({ onEnCamino, conPrevision?, onAutoPedido? })` con `onAutoPedido?: (c: Componente, valor: boolean) => void`; `formatearAuto(v: boolean | null | undefined): string`; `CABECERAS_CSV_PREVISION = ['Consumo/día', 'Pedir 60 d', 'Auto']`.

- [ ] **Step 1: Tests que fallan (columnas y CSV)**

En `columnas.test.tsx`:

a) En el test `'con previsión: dos columnas entre "Stock Mínimo" y "Último pedido"; ...'`, cambiar el título a `'con previsión: tres columnas entre "Stock Mínimo" y "Último pedido"; el 0 en gris y "—" en desactivadas'` y la lista de cabeceras esperada a
`['Componente', 'En Stock', 'En Camino', 'Stock Mínimo', 'Consumo/día', 'Pedir 60 d', 'Auto', 'Último pedido', 'Estado']`.

b) Sustituir el test `'CSV: con previsión añade las dos columnas al final'` por:

```tsx
  it('CSV: con previsión añade las tres columnas al final', () => {
    expect(cabecerasCsvStock(false)).toEqual(CABECERAS_CSV_STOCK)
    expect(cabecerasCsvStock(true)).toEqual([...CABECERAS_CSV_STOCK, 'Consumo/día', 'Pedir 60 d', 'Auto'])
    const fila = filaCsvStock(filas([c({ consumoDiario: 0.31, pedir60: 16, autoPedido: true })])[0], true)
    expect(fila.slice(-3)).toEqual(['0,31', '16', 'Sí'])
    expect(filaCsvStock(filas([c({ autoPedido: false })])[0], true).slice(-1)).toEqual(['No'])
    expect(filaCsvStock(filas([c({ activo: false })])[0], true).slice(-3)).toEqual(['—', '—', '—'])
    expect(filaCsvStock(filas([base])[0])).toHaveLength(CABECERAS_CSV_STOCK.length)
  })
```

c) Añadir:

```tsx
  it('casilla «Auto»: marcada según la marca, deshabilitada en desactivadas, avisa al cambiar y no selecciona la fila', async () => {
    const onAutoPedido = vi.fn()
    render(<DataTable columns={crearColumnasStock({ onEnCamino: vi.fn(), conPrevision: true, onAutoPedido })} data={filas([
      c({ autoPedido: true }),
      c({ idCom: 2, tipo: 'bat-x', activo: false, autoPedido: false }),
    ])} vacio="Sin componentes" getRowId={(x) => String(x.idCom)} />)
    const lcd = screen.getByRole('checkbox', { name: 'Pedido automático lcd-x' })
    expect(lcd).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Pedido automático bat-x' })).toBeDisabled()
    await userEvent.click(lcd)
    expect(onAutoPedido).toHaveBeenCalledWith(expect.objectContaining({ idCom: 1 }), false)
    expect(lcd.closest('tr')).not.toHaveAttribute('data-state', 'selected')
  })
```

(Si el fichero aún no importa `userEvent`, añadir `import userEvent from '@testing-library/user-event'`.)

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/almacen/stock/columnas.test.tsx`
Expected: FAIL (no existe la columna «Auto» ni la cabecera CSV).

- [ ] **Step 3: Implementación**

`prevision.ts`, tras `formatearPedir`:

```ts
/** Marca del pedido automático en el CSV (spec 0.9.6 §4.3). Sin dato (TECNICO), "—". */
export function formatearAuto(v: boolean | null | undefined): string {
  return v === true ? 'Sí' : v === false ? 'No' : '—'
}
```

`columnas.tsx`:

- Import: `import { Checkbox } from '@/shared/ui/checkbox'` y `import { formatearAuto, formatearConsumo, formatearPedir } from './prevision'`.
- `ANCHOS_STOCK`: añadir `auto: 60` tras `pedir60: 90`.
- Comentario de `ANCHOS_STOCK`: «las tres de la previsión son de la 0.9.5 y 0.9.6».
- Sustituir `columnasPrevision`:

```tsx
/** Previsión de pedidos (spec 0.9.5 §3.4) y marca del pedido automático (spec 0.9.6 §4.3): solo para SUPERTECNICO y
 *  ADMIN. Sin `onAutoPedido` la casilla es de solo lectura. */
function columnasPrevision(onAutoPedido?: (c: Componente, valor: boolean) => void): ColumnDef<FilaStock>[] {
  return [
    { id: 'consumoDia', header: 'Consumo/día', size: ANCHOS_STOCK.consumoDia, maxSize: ANCHOS_STOCK.consumoDia, accessorFn: (c) => formatearConsumo(c.consumoDiario) },
    { id: 'pedir60', header: 'Pedir 60 d', size: ANCHOS_STOCK.pedir60, maxSize: ANCHOS_STOCK.pedir60, cell: ({ row }) => celdaPedir(row.original.pedir60) },
    {
      id: 'auto', header: 'Auto', size: ANCHOS_STOCK.auto, maxSize: ANCHOS_STOCK.auto,
      cell: ({ row }) => {
        const c = row.original
        // El clic no llega a la fila: marcar no la selecciona (DataTable selecciona en el onClick de la fila).
        return (
          <div className="flex justify-center" onClick={(e) => e.stopPropagation()}>
            <Checkbox aria-label={`Pedido automático ${nombreGrupo(c)}`} checked={c.autoPedido === true}
              disabled={!c.activo || onAutoPedido === undefined}
              onCheckedChange={(v) => onAutoPedido?.(c, v === true)} className="bg-superficie" />
          </div>
        )
      },
    },
  ]
}
```

- `crearColumnasStock`: firma
  `export function crearColumnasStock({ onEnCamino, conPrevision = false, onAutoPedido }: { onEnCamino: (c: Componente) => void; conPrevision?: boolean; onAutoPedido?: (c: Componente, valor: boolean) => void }): ColumnDef<FilaStock>[]`
  y `...(conPrevision ? columnasPrevision(onAutoPedido) : []),`.
- CSV: `export const CABECERAS_CSV_PREVISION = ['Consumo/día', 'Pedir 60 d', 'Auto']` y en `filaCsvStock` (una
  desactivada sale con «—», como las otras dos columnas de la previsión):
  `return conPrevision ? [...fila, formatearConsumo(c.consumoDiario), formatearPedir(c.pedir60), formatearAuto(c.activo ? c.autoPedido : null)] : fila`

`StockPage.tsx`:

- Import: añadir `useMarcarAutoPedido` al import de `./api`.
- Junto a `const ajustarMinimo = useAjustarMinimo()`: `const { mutate: marcarAutoPedido } = useMarcarAutoPedido()`.
- Línea 135:

```tsx
  const columnas = useMemo(
    () => crearColumnasStock({ onEnCamino: irAPedidos, conPrevision: veEnCamino, onAutoPedido: (c, autoPedido) => marcarAutoPedido({ idCom: c.idCom, autoPedido }) }),
    [irAPedidos, veEnCamino, marcarAutoPedido])
```

- [ ] **Step 4: Comprobar columnas**

Run: `npx vitest run src/modules/almacen/stock/columnas.test.tsx`
Expected: PASS.

- [ ] **Step 5: Tests de la página (fallan antes de ajustar sus expectativas)**

En `StockPage.test.tsx`:

a) En el test del CSV del supertécnico, cabeceras esperadas
`['Tipo', 'Stock', 'Stock mínimo', 'Estado', 'En camino', 'Fecha registro', 'Consumo/día', 'Pedir 60 d', 'Auto']`
y filas `[['bat-x', '2', '3', 'Bajo', '4', '01/09/2026 12:00', '0,31', '16', '—']]` (el fixture tiene `autoPedido: null`).

b) Añadir:

```tsx
  // La vuelta atrás si el PATCH falla la cubre api.test.tsx (Task 3): aquí el diálogo global de error dejaría la
  // tabla aria-hidden y el test dependería del orden de las recargas.
  it('la casilla «Auto» manda el PATCH con la marca nueva', async () => {
    const cuerpos: unknown[] = []
    server.use(http.patch('*/api/componentes/2/auto-pedido', async ({ request }) => {
      cuerpos.push(await request.json())
      return new HttpResponse(null, { status: 200 })
    }))
    montar()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Pedido automático bat-x' }))
    await waitFor(() => expect(cuerpos).toEqual([{ autoPedido: true }]))
  })
  it('el TECNICO no ve la columna «Auto»', async () => {
    montar(SESION_TEC)
    await screen.findByText('lcd-x / lcd-y')
    expect(screen.queryByRole('columnheader', { name: 'Auto' })).not.toBeInTheDocument()
  })
```

(Si `SESION_TEC`, `waitFor` o `userEvent` no están importados en el fichero, añadirlos de donde los importan los demás tests del mismo fichero.)

- [ ] **Step 6: Comprobar Stock completo**

Run: `npx vitest run src/modules/almacen/stock && npx tsc -b`
Expected: PASS y tsc sin errores. Si algún otro test de Stock compara listas de cabeceras con previsión, añadir `'Auto'` tras `'Pedir 60 d'`.

- [ ] **Step 7: Commit**

```bash
git add src/modules/almacen/stock
git commit -m "feat: casilla Auto en stock para marcar el pedido automatico y columna en el csv"
```

---

### Task 5: Web — regla pura de «Añadir previsión» y «Aplicar a todas»

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.ts`
- Test: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/lineas.test.ts`

**Interfaces:**
- Consumes: `Componente.autoPedido`, `Componente.pedir60`; `lineaCompraVacia`, `cambiarLinea`, `siguienteId`, `parsearEntero` (ya en el fichero).
- Produces:
  - `cuantasPrevision(activos: Componente[]): number`
  - `aplicarPrevision(lineas: LineaCompra[], activos: Componente[], idProv: number): { lineas: LineaCompra[]; sinPedido: number }`
  - `aplicarProveedorATodas<T extends { idProv: number | null }>(lineas: T[], idProv: number): T[]`
  - `avisoSinPedido(n: number): string | null`

- [ ] **Step 1: Tests que fallan**

En `lineas.test.ts`, ampliar el import de `./lineas` con `aplicarPrevision, aplicarProveedorATodas, avisoSinPedido, cuantasPrevision` y añadir:

```ts
describe('pedido automático (spec 0.9.6 §4.4)', () => {
  const comp = (o: Partial<Componente>): Componente => ({
    idCom: 1, tipo: 'x', fechaRegistro: '2026-09-01T10:00:00', updatedAt: '2026-09-01T10:00:00', stock: 0, stockMinimo: 1,
    activo: true, enCamino: 0, ultimoPedido: null, idComMaster: null, consumoDiario: 0.3, pedir60: 0, autoPedido: false, ...o,
  })
  // lcd (1) marcada y pide 16; bat (2) marcada sin pedido; cam (3) sin marcar; bat-y (5) slave de bat, hereda la marca.
  const ACTIVOS = [
    comp({ idCom: 1, tipo: 'lcd', autoPedido: true, pedir60: 16 }),
    comp({ idCom: 2, tipo: 'bat', autoPedido: true, pedir60: 0 }),
    comp({ idCom: 3, tipo: 'cam', autoPedido: false, pedir60: 9 }),
    comp({ idCom: 5, tipo: 'bat-y', autoPedido: true, pedir60: 0, idComMaster: 2 }),
  ]

  it('cuenta las marcadas que necesitan pedido, una vez por grupo', () => {
    expect(cuantasPrevision(ACTIVOS)).toBe(1)
    expect(cuantasPrevision([...ACTIVOS, comp({ idCom: 6, tipo: 'mc', autoPedido: true, pedir60: 2 })])).toBe(2)
  })

  it('añade las marcadas sin línea con su cantidad y el proveedor general, y salta las que no necesitan pedido', () => {
    const r = aplicarPrevision([], ACTIVOS, 7)
    expect(r.lineas).toEqual([{ id: 1, idCom: 1, idProv: 7, cantidad: '16', precio: '0,00', urgente: false }])
    expect(r.sinPedido).toBe(1)
  })

  it('en una línea que ya estaba sube la cantidad sin bajarla y solo rellena el proveedor vacío', () => {
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
    expect(r.sinPedido).toBe(0)
  })

  it('una cantidad no válida en una línea marcada pasa a la previsión, o a 1 si la previsión es 0', () => {
    const r = aplicarPrevision([
      { id: 1, idCom: 1, idProv: 7, cantidad: 'abc', precio: '0,00', urgente: false },
      { id: 2, idCom: 2, idProv: 7, cantidad: '', precio: '0,00', urgente: false },
    ], ACTIVOS, 7)
    expect(r.lineas.map((l) => l.cantidad)).toEqual(['16', '1'])
  })

  it('pulsar dos veces deja lo mismo', () => {
    const una = aplicarPrevision([], ACTIVOS, 7)
    expect(aplicarPrevision(una.lineas, ACTIVOS, 7)).toEqual(una)
  })

  it('las desactivadas no entran (la lista de activos ya viene filtrada) y las no marcadas no se tocan', () => {
    expect(aplicarPrevision([], [comp({ idCom: 3, autoPedido: false, pedir60: 9 })], 7)).toEqual({ lineas: [], sinPedido: 0 })
  })

  it('«Aplicar a todas» pone el proveedor en todas las líneas', () => {
    const lineas: LineaCompra[] = [
      { id: 1, idCom: 1, idProv: null, cantidad: '1', precio: '0,00', urgente: false },
      { id: 2, idCom: 2, idProv: 9, cantidad: '1', precio: '0,00', urgente: false },
    ]
    expect(aplicarProveedorATodas(lineas, 7).map((l) => l.idProv)).toEqual([7, 7])
  })

  it('aviso de las marcadas sin pedido', () => {
    expect(avisoSinPedido(0)).toBeNull()
    expect(avisoSinPedido(1)).toBe('1 pieza marcada no necesita pedido.')
    expect(avisoSinPedido(3)).toBe('3 piezas marcadas no necesitan pedido.')
  })
})
```

(Si el fichero no importa `Componente`, añadir `import type { Componente } from '@/shared/api/client'`; `LineaCompra` viene de `./lineas`.)

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts`
Expected: FAIL (funciones no exportadas).

- [ ] **Step 3: Implementación**

En `lineas.ts`, tras `avisoOmitidas`:

```ts
/** Piezas del pedido automático (spec 0.9.6 §4.4): marcadas y que son master o sueltas. Los slaves traen la marca y
 *  la previsión de su master (el listado las copia), así que contarlos duplicaría el grupo; el pedido va al master. */
function marcadas(activos: Componente[]): Componente[] {
  return activos.filter((c) => c.autoPedido === true && c.idComMaster == null)
}

/** N del botón «Añadir previsión (N)»: marcadas con algo que pedir. */
export function cuantasPrevision(activos: Componente[]): number {
  return marcadas(activos).filter((c) => (c.pedir60 ?? 0) > 0).length
}

/** «Añadir previsión» (spec 0.9.6 §4.4). Marcada sin línea: línea nueva con «Pedir 60 d» y el proveedor general, o
 *  nada si no hay que pedir (cuenta en `sinPedido`). Marcada con línea: cantidad = máx(la de la línea, la previsión, 1)
 *  y el proveedor general solo si la línea no tenía. Las líneas de piezas no marcadas no se tocan. Idempotente. */
export function aplicarPrevision(lineas: LineaCompra[], activos: Componente[], idProv: number): { lineas: LineaCompra[]; sinPedido: number } {
  let resultado = lineas
  let sinPedido = 0
  for (const c of marcadas(activos)) {
    const pedir = c.pedir60 ?? 0
    const existente = resultado.find((l) => l.idCom === c.idCom)
    if (existente === undefined) {
      if (pedir > 0) resultado = [...resultado, { ...lineaCompraVacia(siguienteId(resultado), c.idCom), idProv, cantidad: String(pedir) }]
      else sinPedido += 1
      continue
    }
    const cantidad = Math.max(parsearEntero(existente.cantidad) ?? 0, pedir, 1)
    resultado = cambiarLinea(resultado, existente.id, { cantidad: String(cantidad), idProv: existente.idProv ?? idProv })
  }
  return { lineas: resultado, sinPedido }
}

/** «Aplicar a todas»: el proveedor general en todas las líneas, también en las que ya tenían uno. */
export function aplicarProveedorATodas<T extends { idProv: number | null }>(lineas: T[], idProv: number): T[] {
  return lineas.map((l) => ({ ...l, idProv }))
}

export function avisoSinPedido(n: number): string | null {
  if (n <= 0) return null
  return n === 1 ? '1 pieza marcada no necesita pedido.' : `${n} piezas marcadas no necesitan pedido.`
}
```

(`cambiarLinea` y `siguienteId` están más abajo en el mismo fichero: son declaraciones de función, se pueden usar antes.)

- [ ] **Step 4: Comprobar que pasa**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/lineas.test.ts && npx tsc -b`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/modules/almacen/pedidos/formulario/lineas.ts src/modules/almacen/pedidos/formulario/lineas.test.ts
git commit -m "feat: regla de anadir prevision y aplicar proveedor a todas las lineas del pedido"
```

---

### Task 6: Web — barra del proveedor general en «Nuevo pedido»

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/DialogoLineas.tsx` (prop `barra`)
- Modify: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.tsx`
- Test: `gestion-reparaciones-web/src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx`

**Interfaces:**
- Consumes: `cuantasPrevision`, `aplicarPrevision`, `aplicarProveedorATodas`, `avisoSinPedido` (Task 5).
- Produces: `DialogoLineas` acepta `barra?: ReactNode`, pintada entre el título y la tabla.

- [ ] **Step 1: Tests que fallan**

En `NuevoPedidoDialog.test.tsx`, añadir al import de tipos `import type { Componente } from '@/shared/api/client'` y al final:

```tsx
describe('pedido automático (spec 0.9.6 §4.4)', () => {
  // lcd-x-negro (1) marcada y pide 16; bat-x (2) marcada sin pedido; el resto sin marcar.
  const CON_PREVISION: Componente[] = COMPONENTES.map((c) =>
    c.idCom === 1 ? { ...c, autoPedido: true, pedir60: 16 } : c.idCom === 2 ? { ...c, autoPedido: true, pedir60: 0 } : c)

  beforeEach(() => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json(CON_PREVISION)))
  })

  async function elegirProveedorGeneral(nombre: string) {
    await userEvent.click(screen.getByRole('combobox', { name: 'Proveedor general' }))
    await userEvent.click(await within(screen.getByRole('listbox', { name: 'Proveedor general' })).findByRole('button', { name: nombre }))
  }

  it('sin proveedor general los dos botones están deshabilitados', async () => {
    abrir()
    expect(await screen.findByRole('button', { name: 'Añadir previsión (1)' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Aplicar a todas' })).toBeDisabled()
  })

  it('«Añadir previsión» mete la marcada con su cantidad y el proveedor general, avisa de la que no necesita pedido y se confirma', async () => {
    abrir()
    await screen.findByRole('button', { name: 'Añadir previsión (1)' })
    await elegirProveedorGeneral('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión (1)' }))
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.getByRole('combobox', { name: 'Proveedor línea 1' })).toHaveTextContent('ACME')
    expect(screen.getByRole('status')).toHaveTextContent('1 pieza marcada no necesita pedido.')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión (1)' }))
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
    await confirmar()
    await waitFor(() => expect(lotes).toHaveLength(1))
  })

  it('con la pieza ya en el pedido sube la cantidad y no la duplica', async () => {
    abrir({ modo: 'componentes', idsCom: [1] })
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('1'))
    await elegirProveedorGeneral('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Añadir previsión (1)' }))
    expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('16')
    expect(screen.getByRole('combobox', { name: 'Proveedor línea 1' })).toHaveTextContent('ACME')
    expect(screen.queryByLabelText('Cantidad línea 2')).not.toBeInTheDocument()
  })

  it('«Aplicar a todas» cambia también el proveedor que ya tenía una línea', async () => {
    abrir({ modo: 'componentes', idsCom: [1] })
    await waitFor(() => expect(screen.getByLabelText('Cantidad línea 1')).toHaveValue('1'))
    await elegirProveedor(1, 'Proveedor B')
    await elegirProveedorGeneral('ACME')
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar a todas' }))
    expect(screen.getByRole('combobox', { name: 'Proveedor línea 1' })).toHaveTextContent('ACME')
  })
})
```

- [ ] **Step 2: Comprobar que fallan**

Run: `npx vitest run src/modules/almacen/pedidos/formulario/NuevoPedidoDialog.test.tsx`
Expected: FAIL (no existe «Proveedor general» ni los botones).

- [ ] **Step 3: `DialogoLineas` con barra**

En `DialogoLineas.tsx`:

- En `Props<L>`, tras `primera`: `barra?: ReactNode`
- En la desestructuración de la función, añadir `barra` tras `primera`.
- Justo después de `<DialogTitle ...>{titulo}</DialogTitle>`: `{barra}`
- Actualizar el comentario de la función: «… título de 24 px, barra opcional (proveedor general de "Nuevo pedido"), tabla de líneas …».

- [ ] **Step 4: `NuevoPedidoDialog` con la barra**

Imports nuevos:

```tsx
import { BotonSecundario } from '@/shared/ui/Botones'
import { ComboNavy } from '@/shared/ui/ComboNavy'
```

y ampliar el import de `./lineas` con `aplicarPrevision, aplicarProveedorATodas, avisoSinPedido, cuantasPrevision`.

Tras `const [error, setError] = useState<string | null>(null)`:

```tsx
  // Pedido automático (spec 0.9.6 §4.4): proveedor general y cuántas marcadas hay que pedir.
  const [idProvGeneral, setIdProvGeneral] = useState<number | null>(null)
  const [sinPedido, setSinPedido] = useState(0)
  const nPrevision = useMemo(() => cuantasPrevision(activos), [activos])
  const opcionesProveedor = useMemo(() => proveedoresActivos.map((p) => ({ valor: String(p.idProv), etiqueta: p.nombre })), [proveedoresActivos])
```

Tras la función `anadir()`:

```tsx
  function anadirPrevision() {
    if (idProvGeneral === null) return
    const r = aplicarPrevision(lineas ?? [], activos, idProvGeneral)
    setLineas(r.lineas)
    setSinPedido(r.sinPedido)
    setError(null)
  }
  function aplicarATodas() {
    if (idProvGeneral === null) return
    setLineas((ls) => aplicarProveedorATodas(ls ?? [], idProvGeneral))
    setError(null)
  }
```

Antes del `return`:

```tsx
  const bloqueado = guardar.isPending || lineas === null
  const info = [avisoOmitidas(omitidas), avisoSinPedido(sinPedido)].filter((t) => t !== null).join(' ') || null
```

En el JSX de `<DialogoLineas>`:

- tras `primera={{ ... }}` añadir:

```tsx
      barra={
        <div className="flex flex-wrap items-center gap-2.5">
          <span className="text-[12px] font-bold text-azul-medio">Proveedor:</span>
          <ComboNavy valor={idProvGeneral === null ? null : String(idProvGeneral)} opciones={opcionesProveedor} onChange={(v) => setIdProvGeneral(Number(v))} textoVacio="" ancho={160} visibles={8} aria-label="Proveedor general" />
          <BotonSecundario type="button" disabled={bloqueado || idProvGeneral === null || (lineas ?? []).length === 0} onClick={aplicarATodas}>Aplicar a todas</BotonSecundario>
          <BotonSecundario type="button" disabled={bloqueado || idProvGeneral === null || nPrevision === 0} onClick={anadirPrevision}>Añadir previsión ({nPrevision})</BotonSecundario>
        </div>
      }
```

- `info={avisoOmitidas(omitidas)}` → `info={info}`
- `bloqueado={guardar.isPending || lineas === null}` → `bloqueado={bloqueado}`

- [ ] **Step 5: Comprobar que pasa, y todo Pedidos**

Run: `npx vitest run src/modules/almacen/pedidos && npx tsc -b`
Expected: PASS y tsc sin errores.

- [ ] **Step 6: Commit**

```bash
git add src/modules/almacen/pedidos/formulario
git commit -m "feat: proveedor general, aplicar a todas y anadir prevision en nuevo pedido"
```

---

### Task 7: Cierre — documentación, suites completas y entrega

**Files:**
- Modify: `gestion-reparaciones-web/CHANGELOG.md` (sección `[0.9.6]`)
- Create: `docs/novedades/NOVEDADES-v0.9.6.md` (raíz)

- [ ] **Step 1: CHANGELOG de la web**

Sustituir la sección `## [0.9.6] - 2026-10-08 — Pedir para 60 días` entera por:

```markdown
## [0.9.6] - 2026-10-08 — Pedir para 60 días y pedido automático

- **Previsión de pedidos: una sola columna "Pedir 60 d"** en lugar de "Pedir 15 d" y "Pedir 30 d": cuánto pedir para cubrir 60 días, con la misma regla (contando el stock y lo que ya está en camino, sin bajar nunca del stock mínimo). También en el CSV.
- **Pedido automático** (supertécnicos y administrador). En Stock, columna **Auto** con una casilla por pieza (en un grupo de stock compartido, una para todo el grupo); se guarda al momento y la ven igual todos los supertécnicos y el administrador. También va en el CSV (Sí / No).
- En **"Nuevo pedido"**, arriba: **Proveedor** general, **"Aplicar a todas"** (pone ese proveedor en todas las líneas) y **"Añadir previsión (N)"**, que añade las piezas marcadas con la cantidad de "Pedir 60 d" y el proveedor general. Las marcadas que no necesitan pedido no se añaden y se avisa cuántas son. Si una pieza ya estaba en el pedido no se repite: se sube su cantidad a la de la previsión si es mayor, y el proveedor solo se pone si no tenía. El precio se puede dejar en 0 y ponerlo después con "Editar pedido".
- Requiere el servidor 0.9.6 y la migración `migracion-auto-pedido.sql` (columna `AUTO_PEDIDO` en `Componente`). Sin cambios en la configuración de nginx.
```

- [ ] **Step 2: Novedades para el taller (raíz)**

Crear `docs/novedades/NOVEDADES-v0.9.6.md`:

```markdown
# 🎉 Novedades — Versión 0.9.6 (web)

Stock te dice cuánto pedir para **dos meses**, y el pedido de esas piezas se prepara **con un botón**.

---

## 📦 Stock: «Pedir 60 d»

Para **supertécnicos y administrador**. Las columnas «Pedir 15 d» y «Pedir 30 d» se sustituyen por una sola,
**«Pedir 60 d»**: cuántas unidades conviene pedir para cubrir **60 días** de trabajo. La regla es la misma de antes:
cuenta lo que tienes y lo que ya está en camino, y nunca te deja por debajo del mínimo.

---

## ✅ Pedido automático

1. En **Stock**, marca en la columna **«Auto»** las piezas que quieras pedir siempre por previsión. La marca se guarda
   al momento y la ven igual el resto de supertécnicos y el administrador.
2. En **«Nuevo pedido»**, elige arriba el **Proveedor** y pulsa **«Añadir previsión (N)»**: se añaden las piezas
   marcadas con la cantidad de «Pedir 60 d». **N** es cuántas necesitan pedido; las que no, no se añaden y se avisa.
3. Si alguna pieza va a otro proveedor, cámbialo en su línea. **«Aplicar a todas»** pone el proveedor de arriba en
   todas las líneas.
4. El **precio** puede quedarse en 0 y ponerse después con **«Editar pedido»**.

Si una pieza marcada ya estaba en el pedido, no se repite: se sube su cantidad a la de la previsión si es mayor.
```

- [ ] **Step 3: Suites completas**

Run (servidor): `mvn -q test` → Expected: exit 0.
Run (web): `npx tsc -b && npx vitest run` → Expected: exit 0, todos los ficheros en verde.
Run (web): `npm run lint` → Expected: sin errores.

- [ ] **Step 4: Commits de documentación**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git add CHANGELOG.md && git commit -m "docs: changelog de la 0.9.6 con el pedido automatico"
cd /c/Users/dev/Documents/ProgramaReparaciones && git add docs/novedades/NOVEDADES-v0.9.6.md && git commit -m "docs: novedades de la 0.9.6"
```

- [ ] **Step 5: Entrega (con OK del usuario en cada paso)**

Pedir OK antes de cada uno:
1. Merge `--no-ff` de `feature/pedido-automatico` a `main` en servidor y web, borrar las ramas.
2. Push de `main` en los dos repos.
3. Commit de gitlinks en la raíz (`chore: gitlinks servidor y web tras la 0.9.6 (pedido automatico)`).
4. Preprod: copia de la base, `migracion-auto-pedido.sql` (verificación `SELECT COUNT(*) FROM Componente WHERE AUTO_PEDIDO;` → 0), P8, comprobar `/version.json` = 0.9.6, la casilla «Auto», el botón en «Nuevo pedido» y el smoke E2E.
5. Tags `v0.9.6` y producción fuera de horario: solo cuando el usuario lo diga.
