# Pedidos: asociación de IMEI y pedido en Amazon (plan B, parte del ERP) — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que las filas de Amazon salgan «sin asociar» hasta que llegue la asociación IMEI → pedido de los libros de ventas, y que el
ERP la aplique (confirmar, intercambiar, poner o dejar para revisar).

**Architecture:** servicio `AsociacionAmazon` con las cuatro reglas, en una transacción por pasada; ruta de máquina
`POST /api/pedidos/asociaciones` calcada de `POST /api/pedidos/sincronizados` (filtro por clave propia); ruta de lectura para la web;
en la web, la columna «Pedido», el panel, el «1/n», un filtro y una píldora con la lista «para revisar».

**Tech Stack:** Spring Boot 3 / Java 17 / JdbcTemplate / MariaDB 11; React + TypeScript + TanStack Query + Vitest + Playwright.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-13-pedidos-amazon-asociacion-design.md` (raíz). Manda sobre este plan.
- Canales afectados: solo `AMAZON` y `AMAZON_STOCKROOM`. Las webs no cambian en nada.
- **Nunca** se cambia el IMEI de una fila con `ENVIO_FECHA` no nula (etiqueta generada) ni con `MARCA = 'CANCELADO'`. Nunca se toca
  una fila del histórico.
- El número de pedido se normaliza como en la lectura de ShippyPro (`SincroShippyPro.numeroGuardado`). IMEI: 15 cifras tras recortar
  espacios; lo que no lo sea se descarta y se cuenta (los libros traen «CANCELADO», vacíos y números).
- Las asociaciones se aplican en el orden recibido; repetir la misma pasada no cambia nada (idempotente).
- Historial con usuario nulo (lo hace una máquina). Tipos nuevos: `ASOCIADA` (detalle `LIBRO` o `HISTORICO`) e
  `IMEI_INTERCAMBIADO` (detalle `con la fila <id>`). Cada cambio de IMEI de una fila apunta además su `IMEI` como siempre.
- Poner un IMEI en una fila con `ESPERA_STOCK` la quita, igual que el PUT del IMEI.
- Migración aditiva y repetible; si hay FK, `ADD CONSTRAINT x FOREIGN KEY IF NOT EXISTS (...)` (nunca `ADD CONSTRAINT IF NOT EXISTS`).
- Repos públicos: datos sintéticos. Commits en castellano sin acentos ni eñes, sin `Co-Authored-By`. No se toca el número de versión.

---

### Task 1: Servidor

**Files (worktree `C:/Users/dev/Documents/wt/servidor-pedidos-tabla`):**
- Create: `sql/migracion-pedidos-asociacion.sql` (prod y preprod), `pedidos/AsociacionAmazon.java` (servicio), `pedidos/AsociacionesController.java` (ruta de máquina y ruta de lectura), `pedidos/PeticionAsociaciones.java`, `pedidos/RespuestaAsociaciones.java`, filtro de clave (heredando de `ClaveVendidosFilter`, como `ClaveSincronizadosFilter`)
- Modify: `sql/crear_bd.sql`, `sql/migracion-pedidos-tabla.sql` (columnas y tablas dentro de sus CREATE), `FilasDAO.java`, `FilaPedido.java`, `FilaVista.java`, `VistaFilas.java`, `CargaHistorico.java`/`Traspaso` (regla A = asociada `HISTORICO`), consulta de vendidos (`pedido` nulo en Amazon sin asociar), búsqueda (`SQL` de buscar: un número de pedido no encuentra filas de Amazon sin asociar), `SecurityConfig`, `JwtAuthFilter`, `docs/api_contract.md`, `docs/deployment.md`, `docs/autorizacion_endpoints.md`
- Test: nuevos `AsociacionAmazonTest`, `AsociacionesControllerTest`, `RolesAsociacionesTest`; ajustar `VendidosTest`, `CargaHistoricoTest`, `OpenApiContractTest`

**Interfaces — Produces:**
- Migración: `Pedido_fila.ASOCIADA_EN DATETIME NULL`, `Pedido_fila.ASOCIADA_ORIGEN VARCHAR(20) NULL`; tabla `Pedido_asociacion_revisar` (`ID` PK autoincremental, `CANAL VARCHAR(30) NOT NULL`, `PEDIDO VARCHAR(60) NOT NULL`, `IMEI VARCHAR(20) NOT NULL`, `MOTIVO VARCHAR(30) NOT NULL`, `VISTO_EN DATETIME NOT NULL`, índice por `CANAL`); tabla `Pedido_asociacion_pasada` (`CANAL VARCHAR(30)` PK, `CUANDO DATETIME NOT NULL`, `CONFIRMADAS`, `INTERCAMBIADAS`, `PUESTAS`, `YA_ESTABAN`, `PARA_REVISAR`, `DESCARTADAS` INT NOT NULL). Vista previa `YA_ESTAN` y comprobación final como `migracion-pedidos-marcas.sql`.
- `POST /api/pedidos/asociaciones` (clave `X-API-Key` de `PEDIDOS_ASOCIACIONES_CLAVE`; sin claves válidas 404; mala 401; JWT no vale; rol interno propio): cuerpo `{"canal": "AMAZON"|"AMAZON_STOCKROOM", "pares": [{"pedido": "...", "imei": "..."}]}` (tope 10.000 pares; 400 si el canal no es de Amazon o pasa el tope) → 200 `{"confirmadas", "intercambiadas", "puestas", "yaEstaban", "paraRevisar", "descartadas"}`. Cada pasada rehace `Pedido_asociacion_revisar` de ese canal y guarda su fila en `Pedido_asociacion_pasada`.
- Motivos «para revisar» (valores exactos): `SIN_HUECO` (el pedido no tiene fila libre ni sin confirmar), `YA_SALIO` (el IMEI está en una fila con etiqueta de otro pedido), `PEDIDO_AJENO` (el pedido no tiene filas en el ERP), `CANCELADO` (las únicas filas posibles están canceladas).
- `GET /api/pedidos/asociaciones` (JWT, SUPERTECNICO y ADMIN): `{"canales": [{"canal", "cuando", "confirmadas", "intercambiadas", "puestas", "yaEstaban", "paraRevisar", "descartadas", "revisar": [{"pedido", "imei", "motivo", "vistoEn"}]}]}` (un elemento por canal con pasada; lista vacía si no hubo ninguna).
- `FilaVista` gana al final `asociadaEn` (fecha-hora|null) y `asociadaOrigen` (`LIBRO`|`HISTORICO`|null).
- `GET /api/pedidos/vendidos`: `pedido` nulo en filas de Amazon sin `ASOCIADA_EN` (el bot solo lee `imei`; comprobado).

- [ ] **Step 1:** migración y `crear_bd.sql`/`migracion-pedidos-tabla.sql`.
- [ ] **Step 2:** pruebas que fallan de `AsociacionAmazon` (JdbcTemplate simulado, patrón de `MarcaDAOTest`/`FaltaEnviarDAOTest`): regla 1 (ya en su fila → confirmada, `yaEstaban` si ya lo estaba); regla 2 (en fila de otro pedido sin etiqueta → intercambio con la primera fila sin confirmar de B por posición; la otra queda sin confirmar con el IMEI que tenía la de B o vacía; si la de origen estaba confirmada para otro pedido, deja de estarlo); regla 3 (no está → a la primera fila libre de B, confirmada; quita `ESPERA_STOCK`); regla 4 (cada motivo); filas con etiqueta y canceladas intocables; idempotencia; IMEI con espacios, numérico o «CANCELADO» (descartado); normalización del número; 2 de 3 del mismo pedido; historial (`ASOCIADA`, `IMEI_INTERCAMBIADO`, `IMEI`) con usuario nulo.
- [ ] **Step 3:** implementar el servicio (`@Transactional(READ_COMMITTED)`, filas del canal afectadas `FOR UPDATE`).
- [ ] **Step 4:** ruta de máquina y su filtro/rol (calcar `SincronizadosController`, `ClaveSincronizadosFilter`, `RolesSincronizadosTest`, `RolesSincronizadosApagadaTest`), ruta de lectura con roles (`RolesPedidosTest`).
- [ ] **Step 5:** `FilaVista`, vendidos (`pedido` nulo), búsqueda, carga del histórico (regla A → `ASOCIADA_EN` = momento de la carga, `ASOCIADA_ORIGEN = 'HISTORICO'`), con sus pruebas.
- [ ] **Step 6:** contrato y docs (`api_contract.md`, `deployment.md` con `PEDIDOS_ASOCIACIONES_CLAVE`, `autorizacion_endpoints.md`), `OpenApiContractTest`.
- [ ] **Step 7:** batería completa una vez (`mvn -q test`; antes 2155) y un commit: `feat: asociacion de IMEI y pedido en Amazon (plan B): filas sin asociar, ruta de maquina para los libros de ventas y lista para revisar`.

### Task 2: Web

**Files (worktree `C:/Users/dev/Documents/wt/web-pedidos-tabla`):**
- Modify: `src/shared/api/schema.d.ts`, `api/openapi.json` (a mano), `src/modules/telefonos/api.ts` (`useAsociaciones`), `pedidos/filas.ts` (`pedidoVisible`, `numerosDePedido` solo asociadas en Amazon, filtro «Sin asociar»), `TablaFilas.tsx` (celda «Pedido»), `PanelFila.tsx`, `historial.ts`, `PedidosPage.tsx` y una píldora nueva junto a `PildoraFuentes` (o dentro, si encaja mejor), `AyudaPedidos.tsx`, `test/fabrica.ts`
- Test: nuevo `PedidosAsociacion.test.tsx`; ajustar `filas.test.ts`, `historial.test.ts`, `PedidosAyuda.test.tsx`; nuevo `tests/local/pedidos-asociacion.spec.ts`

**Interfaces — Consumes:** las de la Task 1.

- [ ] **Step 1:** tipos a mano (`asociadaEn`, `asociadaOrigen`, ruta `GET /api/pedidos/asociaciones`).
- [ ] **Step 2:** pruebas que fallan: fila de Amazon sin `asociadaEn` → la celda «Pedido» dice «sin asociar» (gris) y el número no se ve ni en la celda ni en su `title`; asociada → número; web → número siempre; panel («Pedido: sin asociar» / «Pedido N · asociado desde el libro de ventas el dd/mm hh:mm» / «… desde el histórico de la hoja»); «1/n» en Amazon solo entre asociadas; filtro «Sin asociar» (dentro del filtro de la columna IMEI, como «Con marca», con su recuento; solo filas de Amazon); píldora «Asociación Amazon: hh:mm · N para revisar» (sin pasadas: no sale) que abre la lista (pedido, IMEI, motivo en palabras: «el pedido no tiene hueco», «el móvil ya salió en otro pedido», «el pedido no está en el ERP», «solo quedan filas canceladas»); historial de `ASOCIADA` e `IMEI_INTERCAMBIADO`.
- [ ] **Step 3:** implementar; la píldora consulta `GET /api/pedidos/asociaciones` con el mismo sondeo que las fuentes.
- [ ] **Step 4:** ayuda: una línea «sin asociar» (Amazon: el pedido sale cuando lo asocia el libro de ventas).
- [ ] **Step 5:** navegador real (API falsa): fila de Amazon «sin asociar», la píldora y su lista.
- [ ] **Step 6:** `npm run check` (antes 2974) y Playwright local (antes 11) una vez; un commit: `feat: Pedidos en Amazon sale sin asociar hasta que el libro de ventas asocia el IMEI (columna, panel, 1/n, filtro y lista para revisar)`.
