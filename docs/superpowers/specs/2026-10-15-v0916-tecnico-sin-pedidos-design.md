# 0.9.16 — El técnico sin Pedidos ni Proveedores

Fecha: 2026-10-15. Estado: diseño aprobado, pendiente del plan.

Toca **servidor** y **web**; no toca la base de datos. Parte de servidor `main` `c65ce4f` y web `main` `ead5a0f` (0.9.15).

## 1. Problema y decisión

**Hoy:**
- Desde el 2026-05-18 (servidor `99e1055`, «permitir TECNICO leer pedidos y proveedores») el TECNICO lee `GET /api/compras`, `GET /api/compras/en-camino` y `GET /api/proveedores`. `GET /api/compras-otros` nació con el mismo criterio (servidor `bc336b2`, 2026-06-26).
- La web le enseña Pedidos y Proveedores en el grupo Stock, en solo lectura: sin «Nuevo pedido», sin «Nuevo proveedor» y sin menús de fila (`puedeEditar = esSuperTecnico(sesion)`).
- En Pedidos ve el proveedor de cada pieza y las columnas **P.Unit.** y **EUR**: los precios de compra.
- En Stock actual, la barra «Pedido» del gráfico por pieza le marca siempre 0, aunque la columna «En Camino» diga otra cosa: el TECNICO no pide `GET /api/compras/cantidad-en-camino/{idCom}` (`StockPage.tsx`, `veEnCamino`).

**Decisiones del usuario (2026-10-15):**
- El técnico **no ve Pedidos ni Proveedores**. Con Stock actual tiene lo que necesita: En Stock, En Camino, Stock Mínimo, Último pedido y Estado.
- Se cierra también la **lectura en el servidor**: con su sesión, un técnico podría pedir los precios a la API.
- El **ADMIN no cambia**: sigue viendo Pedidos y Proveedores en solo lectura.
- En el mismo cambio, la barra «Pedido» del gráfico del técnico enseña lo que está en camino.

## 2. Qué ve cada rol

| | TECNICO | SUPERTECNICO | ADMIN |
|---|:---:|:---:|:---:|
| Stock actual | sí (como hoy, más el gráfico con «Pedido») | sí, sin cambios | sí, sin cambios |
| Pedidos | **no** (antes: solo lectura) | sí, sin cambios | solo lectura, sin cambios |
| Proveedores | **no** (antes: solo lectura) | sí, sin cambios | solo lectura, sin cambios |

Cualquier sesión que no sea SUPERTECNICO ni ADMIN (también sin sesión) se trata como TECNICO, igual que en el servidor.

## 3. Web

### 3.1 Columna lateral

- `enlacesStock(sesion)` (`modules/almacen/rutas`) pasa a depender del rol: «Stock actual» siempre; «Pedidos» y «Proveedores» solo con `esAdminOSuperTecnico(sesion)`.
- `gruposNav` (`app/shell/navegacion.ts`) le pasa la sesión. El grupo Stock del técnico queda con una sola pestaña; no se quita el grupo.

### 3.2 Rutas

- Nueva guarda **`RequiereAccesoPedidos`** en `modules/almacen/rutas`, que pasa de `.ts` a `.tsx`. Es el patrón de `RequiereSupertecnicoOAdmin` (taller) y `RequiereAdmin` (gestión), copiado porque un módulo no importa de otro, y con otro nombre porque `router.tsx` importa los tres:
  - sin permiso, aviso genérico `MSG_SIN_PERMISOS` («No tienes permisos para realizar esta acción.») y `<Navigate to="/stock" replace />`;
  - con permiso, `<Outlet />`.
- En `app/router.tsx`, la guarda envuelve las tres rutas: `/stock/pedidos`, `/stock/pedidos/otros` (la redirección de la 0.9.15) y `/stock/proveedores`.

### 3.3 Stock actual

- **«En Camino» sin enlace para el técnico.** Hoy el número es un botón que abre Pedidos filtrado por esa pieza. `crearColumnasStock` recibe `onEnCamino` opcional: sin él, la celda enseña el número como texto, sin botón ni subrayado. `StockPage` solo lo pasa con `veEnCamino` (SUPERTECNICO y ADMIN). El «—» de cero no cambia.
- **Gráfico por pieza.** Para el técnico, la barra «Pedido» usa el `enCamino` de la fila seleccionada, el mismo número que la columna. Es el mismo valor que daría `cantidad-en-camino/{idCom}`: los pedidos se guardan en el componente master del SKU compartido, y la fila agrupada es la del master. El SUPERTECNICO y el ADMIN siguen pidiendo `cantidad-en-camino` como hoy.
- El CSV de Stock no cambia: el técnico ya exporta «En camino».

### 3.4 Pedidos y Proveedores

- Las páginas no cambian: `puedeEditar` sigue dejando al ADMIN en solo lectura.
- Se corrigen los comentarios que dicen «los tres roles» (`pedidos/api.ts` en `useCompras` y `useComprasOtros`, `almacen/rutas`): ahora son SUPERTECNICO y ADMIN.

## 4. Servidor

- Se quita `TECNICO` del `@PreAuthorize` de cuatro lecturas, que quedan en `hasAnyRole('SUPERTECNICO', 'ADMIN')`:
  - `CompraController`: `GET /api/compras` y `GET /api/compras/en-camino`. Esta última ya está retirada (`RutasRetiradas`, 403 para todos); se cambia para que la anotación diga lo mismo que las demás.
  - `CompraOtroController`: `GET /api/compras-otros`.
  - `ProveedorController`: `GET /api/proveedores`.
- Un técnico recibe el `403` genérico de `@PreAuthorize`, que la web enseña como `MSG_SIN_PERMISOS`.
- No rompe nada del técnico. En la web, esas lecturas solo las usan las páginas de Pedidos y Proveedores y sus diálogos (todos del SUPERTECNICO). El cliente JavaFX está retirado (SP7).
- `docs/autorizacion_endpoints.md`, en **Lecturas**: pedidos (`GET /api/compras`, `/api/compras-otros`) y proveedores (`GET /api/proveedores`): SUPERTECNICO y ADMIN, desde la 0.9.16; antes también TECNICO.

## 5. Compatibilidad, despliegue y vuelta atrás

- **Sin base de datos.** Cumple el criterio de despliegue en horario: corte de unos 30 s, web vieja compatible, vuelta atrás simple.
- **Web vieja con servidor nuevo:** un técnico que tenga Pedidos o Proveedores abierto recibe «No tienes permisos…» al refrescar los datos, hasta que recarga (la web avisa de la versión nueva con `AvisoVersion`). Sin más efecto.
- **Web nueva con servidor viejo:** el técnico ya no ve las pestañas; el servidor aún le dejaría leer. Sin efecto visible.
- **Orden:** servidor y web en el mismo despliegue; primero preprod y luego producción.
- **Vuelta atrás:** redesplegar la 0.9.15 de los dos, en cualquier orden.

## 6. Pruebas

**Web (Vitest):**
- `rutas.test.ts`: enlaces por rol (técnico solo «Stock actual»; SUPERTECNICO y ADMIN los tres, con sus iconos) y la guarda: el técnico en cada una de las tres rutas ve el aviso y acaba en `/stock`; SUPERTECNICO y ADMIN entran.
- `navegacion.test.ts`: el grupo Stock del técnico tiene solo «Stock actual».
- `SubNav.test.tsx`: los casos que usan al técnico con Pedidos (grupos plegables, cabecera en navy en `/stock/pedidos`) pasan a otro enlace del técnico o al SUPERTECNICO; uno nuevo comprueba que el técnico no tiene «Pedidos» ni «Proveedores».
- `PedidosPage.test.tsx` y `ProveedoresPage.test.tsx`: la solo lectura queda para el ADMIN; los casos del técnico se quitan o pasan al ADMIN.
- `TopBar.test.tsx`: el caso del técnico en `/stock/proveedores` usa una ruta que el técnico ve.
- `columnas.test.tsx` / `StockPage.test.tsx`: sin `onEnCamino`, «En Camino» es texto y no un botón; el técnico ve en el gráfico la barra «Pedido» con el `enCamino` de la fila, sin pedir `cantidad-en-camino`.

**Servidor (JUnit + MockMvc):**
- Test de roles nuevo, al estilo de `RolesBloquesCompraTest`: TECNICO recibe `403` en `GET /api/compras`, `/api/compras-otros` y `/api/proveedores`; SUPERTECNICO y ADMIN, `200`.
- Ningún test del servidor pide hoy esas lecturas con el TECNICO (comprobado), así que no hay que cambiar ninguno.

**E2E:** `gestion.spec.ts` y `columna-lateral.spec.ts` (esta con el supertécnico) no usan Pedidos con el técnico; no cambian. Una E2E nueva de solo lectura, `roles-stock.spec.ts`, lo comprueba en preprod con las tres cuentas de `~/.env.e2e`:
- el técnico no tiene las pestañas, por URL vuelve a Stock y la API le da 403;
- SUPERTECNICO y ADMIN siguen como antes, con 200.

## 7. Fuera de alcance

- El rol LOGISTICA (F3 del roadmap).
- Qué ve el técnico dentro de Stock actual, aparte del gráfico: columnas, filtros y menú de fila no cambian.
- Cerrar al técnico otras lecturas sin rol de almacén (`/inventario`, `/lotes`…): siguen en la lista del SP7 en plan-futuro.
