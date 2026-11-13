# Pedidos: marcas de la fila con nota — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que cada fila de ShippyPro de «Pedidos» pueda llevar una marca fija (En espera de stock, En revisión, Pendiente de
color, Cancelado) con nota opcional, con sus efectos en contadores, lector, celda de envío y lista de vendidos.

**Architecture:** columna `MARCA` (+ nota, quién y cuándo) en `Pedido_fila`, ruta `PUT /api/pedidos/filas/{id}/marca` calcada de
`PUT /api/pedidos/filas/{id}/falta-enviar` (servidor `49b78bb`), y en la web el mismo patrón que la marca «Falta por enviar»
(web `c73d796`): hook con idempotencia, estado de pantalla derivado, historial por tipo.

**Tech Stack:** servidor Spring Boot 3 / Java 17 / JdbcTemplate / MariaDB 11; web React + TypeScript + TanStack Query + Vitest +
Playwright + Tailwind.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-13-pedidos-marcas-design.md` (raíz). Manda sobre este plan si chocan.
- Marcas (valores exactos): `ESPERA_STOCK`, `EN_REVISION`, `PENDIENTE_COLOR`, `CANCELADO`. Textos: «En espera de stock», «En
  revisión», «Pendiente de color», «Cancelado». Como mucho una por fila. Nota opcional ≤ 200 caracteres (recortada de espacios;
  vacía = null).
- Solo filas `ORIGEN='SHIPPYPRO'`; histórico → 409 con el mensaje de histórico existente.
- **Ninguna fila con marca se sirve en `GET /api/pedidos/vendidos`**, tenga o no IMEI. La fecha que cuenta para la ventana de días
  (y `fechaImei` de la respuesta) es la más reciente entre el último apunte de tipo `IMEI` y el último apunte `MARCA_QUITADA`.
- Poner un IMEI (no nulo) en una fila con `ESPERA_STOCK` quita la marca en la misma transacción y apunta `MARCA_QUITADA` con detalle
  «al poner el IMEI». Las otras marcas no se tocan al poner IMEI.
- Efectos en pantalla: `ESPERA_STOCK` y `EN_REVISION` cuentan como pendientes pero el lector NO salta a ellas; `PENDIENTE_COLOR`
  como sin marca (solo etiqueta y nota); `CANCELADO` no cuenta como pendiente, el lector no salta, y la celda de envío dice
  «Cancelado» en gris en lugar de Pendiente / Etiqueta generada / Enviado / Falta por enviar.
- «Falta por enviar» (columnas `FALTA_ENVIAR_*`) es independiente y no cambia.
- Migraciones: aditivas y repetibles; FK con `ADD CONSTRAINT <nombre> FOREIGN KEY IF NOT EXISTS (...)` (MariaDB NO acepta
  `ADD CONSTRAINT IF NOT EXISTS ... FOREIGN KEY`: da 1064).
- Repos públicos: datos de prueba sintéticos; nada real. Commits en castellano sin acentos ni eñes, sin `Co-Authored-By`.
- No se cambia el número de versión (lo hace el controlador al publicar).

---

### Task 1: Servidor — marca de la fila

**Files (worktree `C:/Users/dev/Documents/wt/servidor-pedidos-tabla`):**
- Create: `sql/migracion-pedidos-marcas.sql` (producción y preprod: el mismo fichero), `src/main/java/com/reparaciones/servidor/pedidos/PeticionMarca.java`, `src/main/java/com/reparaciones/servidor/pedidos/Marca.java` (enum con los cuatro valores)
- Modify: `sql/crear_bd.sql` (columnas dentro de los CREATE TABLE), `sql/migracion-pedidos-tabla.sql` (idem, para que una base nueva salga completa), `FilasDAO.java` (`ponerMarca`, `ponerImei` quita `ESPERA_STOCK`, SQL de vendidos), `FilasController.java` (ruta), `FilaPedido.java`, `FilaVista.java`, `VistaFilas.java`, `CambioImei.java` (`detalle`), `docs/api_contract.md`
- Test: `FilasDAOTest`/nuevo `MarcaDAOTest`, nuevo `MarcaControllerTest`, `VendidosTest`, `RolesPedidosTest`, `OpenApiContractTest`

**Interfaces — Produces (lo que usa la web):**
- `PUT /api/pedidos/filas/{id}/marca`, cabecera `Idempotency-Key` opcional, cuerpo `{"marca": "ESPERA_STOCK"|"EN_REVISION"|"PENDIENTE_COLOR"|"CANCELADO"|null, "nota": string|null, "marcaAnterior": string|null}` → 200 `FilaVista`; 400 marca desconocida o nota > 200; 404; 409 histórico; 409 «Otra persona ha cambiado la marca de esta fila mientras tanto. Recarga y vuelve a intentarlo.» si `marcaAnterior` viene (no nulo; `""` = sin marca) y no coincide.
- Poner la misma marca con otra nota = cambio (apunte `MARCA` nuevo). Misma marca y misma nota = 200 sin apunte. `marca: null` sin marca = 200 sin apunte.
- `FilaVista` gana al final: `marca` (string|null), `marcaNota` (string|null), `marcaEn` (fecha-hora|null), `marcaPor` (string|null, nombre de usuario).
- `CambioImei` gana `detalle` (string|null). Tipos nuevos: `MARCA` (detalle = `"<MARCA>"` o `"<MARCA> — <nota>"`), `MARCA_QUITADA` (detalle = marca que tenía; con «al poner el IMEI» si la quitó el ERP: `"ESPERA_STOCK — al poner el IMEI"`).

- [ ] **Step 1: Migración.** `sql/migracion-pedidos-marcas.sql`, mismo estilo que `sql/preprod-pedidos-falta-enviar.sql` (vista previa `YA_ESTAN`, `lock_wait_timeout = 10`, comprobación final):

```sql
USE gestion_reparaciones;
SET NAMES utf8mb4;
SELECT COUNT(*) AS YA_ESTAN FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('MARCA', 'MARCA_NOTA', 'MARCA_EN', 'MARCA_USU'))
     OR (TABLE_NAME = 'Pedido_fila_cambio' AND COLUMN_NAME = 'DETALLE'));
SET SESSION lock_wait_timeout = 10;
ALTER TABLE Pedido_fila
    ADD COLUMN IF NOT EXISTS MARCA      VARCHAR(20)  NULL COMMENT 'ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR o CANCELADO; NULL = sin marca',
    ADD COLUMN IF NOT EXISTS MARCA_NOTA VARCHAR(200) NULL COMMENT 'Nota libre de la marca',
    ADD COLUMN IF NOT EXISTS MARCA_EN   DATETIME     NULL COMMENT 'Cuando se puso la marca',
    ADD COLUMN IF NOT EXISTS MARCA_USU  INT          NULL COMMENT 'Quien puso la marca';
ALTER TABLE Pedido_fila
    ADD CONSTRAINT fk_fila_marca_usuario FOREIGN KEY IF NOT EXISTS (MARCA_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;
ALTER TABLE Pedido_fila_cambio
    ADD COLUMN IF NOT EXISTS DETALLE VARCHAR(250) NULL COMMENT 'Marca y nota en los apuntes MARCA y MARCA_QUITADA';
SELECT COUNT(*) AS NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('MARCA', 'MARCA_NOTA', 'MARCA_EN', 'MARCA_USU'))
     OR (TABLE_NAME = 'Pedido_fila_cambio' AND COLUMN_NAME = 'DETALLE'));
SELECT COUNT(*) AS LA_FK FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND CONSTRAINT_NAME = 'fk_fila_marca_usuario';
```

  Esperado al aplicar: `YA_ESTAN` 0 (1.ª vez) o 5; `NUEVAS` 5; `LA_FK` 1. Mismas columnas dentro de los `CREATE TABLE` de `crear_bd.sql` y `migracion-pedidos-tabla.sql`.

- [ ] **Step 2: Pruebas que fallan (DAO).** Calcar `FaltaEnviarDAOTest` (JdbcTemplate simulado, patrón del repo): poner cada marca; cambiar de marca; cambiar solo la nota (apunte nuevo); misma marca y nota (sin apunte); quitar (apunte `MARCA_QUITADA` con la marca que tenía); `marcaAnterior` que no coincide → resultado `CAMBIADA` sin escribir ni apuntar; histórico → `HISTORICO`; no existe → `NO_EXISTE`; `ponerImei` con IMEI en fila `ESPERA_STOCK` → quita la marca y apunta `MARCA_QUITADA` «ESPERA_STOCK — al poner el IMEI»; `ponerImei` en fila `EN_REVISION` → no toca la marca; quitar el IMEI (null) en fila `ESPERA_STOCK` → no toca la marca.
- [ ] **Step 3: Ejecutar y ver que fallan:** `mvn -q test -Dtest=MarcaDAOTest` → FAIL.
- [ ] **Step 4: Implementar** `FilasDAO.ponerMarca(int idFila, String marca, String nota, String marcaAnterior, Integer idUsu, LocalDateTime ahora)` en `@Transactional(isolation = READ_COMMITTED)` con la fila `FOR UPDATE` (como `ponerFaltaEnviar`), y el cambio en `ponerImei`. Columnas nuevas en `SQL_FILAS`/el mapeo de `FilaPedido` y `FilaVista` (al final, conservando constructores existentes si hay pruebas que los usan).
- [ ] **Step 5: Pruebas del controlador y roles** (calcar `FaltaEnviarControllerTest`): 200 con cada marca; 400 marca desconocida y nota de 201; 404; 409 histórico; 409 `marcaAnterior`; idempotencia (misma clave y cuerpo → una sola escritura; el registro de idempotencia incluye marca, nota y marcaAnterior); técnico 403, supertécnico y admin 200.
- [ ] **Step 6: Vendidos.** Pruebas en `VendidosTest`: fila con IMEI y cualquier marca → fuera; fila marcada hace 10 días con IMEI puesto hace 10 días y marca quitada hoy → dentro con `dias=1` y `fechaImei` = hoy; filas sin marca → como hoy. Implementar en el SQL de vendidos: `AND f.MARCA IS NULL` y la fecha = `GREATEST` del último apunte `TIPO='IMEI'` y del último `TIPO='MARCA_QUITADA'` (hoy la subconsulta filtra `TIPO='IMEI'`: ampliarla con cuidado).
- [ ] **Step 7: Contrato.** `docs/api_contract.md` (ruta, campos, tipos de historial, efecto en vendidos) y `OpenApiContractTest` (esquema `PeticionMarca`, nullabilidad, códigos 200/400/404/409).
- [ ] **Step 8: Batería completa una vez:** `mvn -q test` → antes 2127 pruebas, 0 fallos; ahora más, 0 fallos.
- [ ] **Step 9: Commit:** `feat: marcas de la fila en pedidos (espera de stock, en revision, pendiente de color y cancelado, con nota); las filas marcadas no van a vendidos`.

### Task 2: Web — marcas en la celda del IMEI, el panel y la tabla

**Files (worktree `C:/Users/dev/Documents/wt/web-pedidos-tabla`):**
- Modify: `src/shared/api/schema.d.ts`, `api/openapi.json` (a mano, como `faltaEnviarEn`), `src/modules/telefonos/api.ts` (`usePonerMarca`), `src/modules/telefonos/pedidos/filas.ts` (estado de pantalla `CANCELADO`, `esPendiente`, filtro «Con marca»), `estado.ts`, `CeldaImei.tsx` (selector de marca y nota en el desplegable; marca visible en la celda), `TablaFilas.tsx` (celda de envío «Cancelado»; `saltar` no va a filas con `ESPERA_STOCK`, `EN_REVISION` o `CANCELADO`), `PanelFila.tsx` (marca, nota, quién y cuándo, cambiar y quitar), `historial.ts` (tipos `MARCA` y `MARCA_QUITADA` con `detalle`), `AyudaPedidos.tsx` (las cuatro marcas), `src/modules/telefonos/test/fabrica.ts`
- Test: nuevo `PedidosMarcas.test.tsx`, `filas.test.ts`, `historial.test.ts`, `TablaFilas.salto.test.tsx`, `PedidosAyuda.test.tsx`, nuevo `tests/local/pedidos-marca.spec.ts`

**Interfaces — Consumes:** las de la Task 1 (ruta, `FilaVista.marca/marcaNota/marcaEn/marcaPor`, `CambioImei.tipo/detalle`).

- [ ] **Step 1: Tipos** a mano en `schema.d.ts` y `api/openapi.json` (ruta `PUT /api/pedidos/filas/{id}/marca` con `PeticionMarca`, campos nuevos de `FilaVista` y `detalle` de `CambioImei`).
- [ ] **Step 2: Pruebas que fallan** (`PedidosMarcas.test.tsx`, datos de `fabrica.ts`, IMEI con Luhn de `test/imeiValido.ts`): desde el desplegable de la celda del IMEI se elige marca y nota y se guarda (cuerpo con `marcaAnterior`, clave de idempotencia); la celda enseña la etiqueta de la marca (y la nota en el `title`), y sin IMEI ocupa el sitio de «pulsa para poner el IMEI…»; escribir un IMEI con el lector en el mismo desplegable sigue funcionando igual; panel: ver, cambiar y quitar; «Cancelado» en la celda de envío y fuera de «N pendientes»; `ESPERA_STOCK`/`EN_REVISION` dentro de «N pendientes»; el salto del lector se salta las filas con `ESPERA_STOCK`, `EN_REVISION` y `CANCELADO` (y no las de `PENDIENTE_COLOR`); filtro «Con marca» con su recuento; historial de `MARCA` (con nota) y `MARCA_QUITADA` (con «al poner el IMEI»); 409 por `marcaAnterior` → recarga y aviso como el 409 del IMEI.
- [ ] **Step 3: Ejecutar y ver que fallan:** `npx vitest run src/modules/telefonos/pedidos/PedidosMarcas.test.tsx` → FAIL.
- [ ] **Step 4: Implementar** siguiendo el patrón de «Falta por enviar» (`useFaltaEnviar`, `estadoPantalla`, `esPendiente`, `MarcaFalta` en `PanelFila`): hook `usePonerMarca` (clave de idempotencia, escribir la fila en las cachés, apunte adelantado en el historial, una sola recarga diferida); estado de pantalla `CANCELADO` por encima de los demás en la celda de envío; colores: `ESPERA_STOCK` y `PENDIENTE_COLOR` ámbar suave, `EN_REVISION` azul suave, `CANCELADO` gris.
- [ ] **Step 5: Ayuda:** cuatro líneas nuevas en «Cómo se lee esta tabla» con la etiqueta de muestra y una frase cada una (de la tabla de la spec); ajustar `PedidosAyuda.test.tsx`.
- [ ] **Step 6: Navegador real:** `tests/local/pedidos-marca.spec.ts` (API falsa de `playwright.local.config.ts`): abrir la celda del IMEI, poner «En espera de stock» con nota, ver la etiqueta, poner el IMEI con el teclado (15 cifras válidas) y ver que la marca se va.
- [ ] **Step 7: Batería completa una vez:** `npm run check` (antes 2949 + lo del arreglo del filtro) y `npx playwright test -c playwright.local.config.ts`.
- [ ] **Step 8: Commit:** `feat: marcas de la fila en Pedidos (espera de stock, en revision, pendiente de color y cancelado, con nota) desde la celda del IMEI y el panel`.
