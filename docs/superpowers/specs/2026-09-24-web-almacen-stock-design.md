# Sub-proyecto 4a — Stock actual y Proveedores

**Fecha:** 2026-09-24
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra). Partición y decisiones comunes del sub-proyecto 4: [Almacén, Inventario y Revisión](2026-09-24-web-almacen-programa-design.md) (D1-D16, vinculantes aquí). Antecesor directo: [Asignar trabajos (3b)](2026-09-22-web-asignar-trabajos-design.md).
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-stock`, creadas desde `main`), repo raíz (gitlinks, plan y specs; se queda en `main`).

## 1. Objetivo

Sustituir el "Pendiente de migrar" de `/stock` por la vista **Stock** del JavaFX con su columna lateral de tres entradas, **Stock actual · Pedidos · Proveedores**, y construir las dos primeras que no dependen de los pedidos: la tabla de componentes con su semáforo, filtros, gráficos, menú contextual y diálogos, y la pestaña de proveedores con sus altas, ediciones, activación y borrado. "Pedidos" queda como pendiente hasta 4b.

En el servidor suben tres cosas pequeñas y aditivas: la cantidad en camino por SKU resuelta al master, el 409 al borrar un proveedor con pedidos y las validaciones de rango y de nombre que hoy solo hace el cliente.

Con esto, cualquier rol consulta el stock desde la web y el supertécnico gestiona stock y proveedores sin el JavaFX. Entrega: web **v0.6.0**.

Por fuera queda como el JavaFX; las diferencias son las de la §10. Referencia: `hotfix/0.16.3` (solo lectura), con paridad verificada contra las capturas de la vista y el inventario de su código, guardados fuera del repo porque llevan datos reales del taller.

## 2. Fuera de alcance

- La pestaña "Pedidos", los cuatro formularios de pedido y los tres botones de pedir de la campana ("Pedir", "Pedir piezas", "Pedir todas las piezas"): 4b. El ítem "Pedir" del menú contextual de un componente y la navegación desde "En Camino" llevan a `/stock/pedidos`, que en 4a sigue siendo "Pendiente de migrar".
- Alta y borrado de componente (D14): el servidor los tiene, el JavaFX no los muestra.
- Unificación del mínimo master/slave, bloqueo optimista de slaves y aviso al desactivar un grupo compartido (D8): backlog F5.
- Cerrar por rol `GET /api/componentes/gestionados` (D6): SP7.
- Suppliers de teléfonos: 4c.
- `/evolucion-stock` y `/stock-bajo`: sin consumidor en el JavaFX; no se usan.

## 3. Decisiones de este sub-proyecto

| # | Decisión | Por qué |
|---|---|---|
| S1 | **Sidebar de Stock = `SubNav`** con una sección `stock` de tres enlaces (Stock actual, Pedidos, Proveedores), sin badges, visible para los tres roles | Es el patrón del proyecto para las columnas laterales; el JavaFX enseña Stock a todos |
| S2 | **Los filtros y la selección de cada pestaña sobreviven al cambio de sección** dentro de Stock y a la vuelta desde Reparaciones, con un store en memoria (`shared/lib/store.ts`) por pestaña; se reinician al cerrar sesión | Calco de la caché de vista del JavaFX (`vistaCache`) |
| S3 | **Semáforo de cuatro estados en la web**, función pura con tests (hoy no tiene ninguno), sin subirlo al servidor; `nivelStock` de `taller/lib/piezas.ts` se rehace sobre ella para que haya una sola definición | La regla es de presentación y ya vive a medias en la web; `/stock-bajo` no la refleja y no lo usa nadie |
| S4 | **La selección de fila (y el gráfico por SKU) se mantiene a través del refresco** de 60 s gracias a `getRowId` | El JavaFX la pierde en cada tick; mantenerla es una diferencia a favor de la web, anotada en la ficha |
| S5 | **Diálogos nativos → diálogos propios.** "Ajustar mínimo" y "Nuevo proveedor" pasan al mismo estilo que "Editar stock" (título, subtítulo, campo, error inline, Cancelar/Confirmar). "Nuevo proveedor" sigue pidiendo solo el nombre; con el nombre en blanco muestra "El nombre no puede estar vacío." en vez de cerrarse en silencio | D13; la web no tiene "diálogo nativo" |
| S6 | **Borrar proveedor:** "Borrar" aparece en el menú solo si `tiene-pedidos` es falso (consulta al abrir el menú, no en cada clic de fila); el servidor además responde 409 "El proveedor tiene pedidos y no se puede borrar." y la web lo enseña como error de negocio | El servidor borra a ciegas y el 500 por clave foránea se disfraza hoy de "servidor no disponible" |
| S7 | **Gráficos con Recharts** (D7): donut de 120 px con el total en el centro y leyenda propia; barras "Stock" y "Pedido" con tooltip. Se calcan los dos hex distintos del ámbar (badge `#C07800`, gráficos `#c77a00`) con un token nuevo cada uno | Calco fiel; unificar el ámbar es una decisión visual que no toca al 4a |
| S8 | **"En Camino" → `/stock/pedidos?estados=pendiente,en camino,parcial&buscar=<tipo>`**: 4a solo navega y deja los parámetros en la URL; 4b los consume | Así el enlace existe desde 4a sin construir Pedidos |
| S9 | **Ordenación por cabecera bloqueada** en las dos tablas (`ordenacion={false}`), como en Asignaciones | D13; el JavaFX la permite por omisión y rompe el orden activos → desactivados |

## 4. Servidor (rama `feature/web-stock`)

Todo es **aditivo**.

### 4.1 `GET /api/compras/cantidad-en-camino/{idCom}` por master

`CompraComponenteDAO.getCantidadEnCaminoPorComponente` pasa a resolver `idCom` al master con `resolveToMasterId`, como ya hace `insertar`. Roles sin cambio (SUPERTECNICO y ADMIN). Test: un slave con compras en camino en su master devuelve la suma del master.

### 4.2 `DELETE /api/proveedores/{idProv}` con guard

Antes de borrar, `tienePedidos(idProv)` (compras de componentes, otros y lotes); si es verdadero, **409** con el mensaje `"El proveedor tiene pedidos y no se puede borrar."`. Se añade el log `BORRAR_PROVEEDOR` que hoy falta. Tests: con pedidos → 409 y ninguna fila borrada; sin pedidos → 204.

### 4.3 Validaciones (422)

- `PUT /api/componentes/{idCom}` con `stock < 0` o `stockMinimo < 0`.
- `PATCH /api/componentes/{idCom}/stock-minimo` con `stockMinimo < 0`.
- `POST` y `PUT /api/proveedores` con nombre en blanco o de más de 100 caracteres, o divisa fuera de `EUR`/`USD`.

Los mensajes son los del cliente: `"Cantidad no válida (debe ser ≥ 0)."`, `"Valor no válido (debe ser ≥ 0)."`, `"El nombre no puede estar vacío."`; para la divisa, que el cliente no valida porque su combo solo ofrece dos valores, `"Divisa no válida (EUR o USD)."`.

### 4.4 Lo que ya existe y se reutiliza

`GET /api/componentes/gestionados` (stock y mínimo del master en las filas compartidas, "en camino" agregado por master con `en_camino` + `parcial`, último pedido de cualquier estado), `PUT /api/componentes/{idCom}` con bloqueo optimista (solo en masters), `PATCH …/stock-minimo`, `PATCH …/activo` (activa o desactiva todo el grupo compartido), `POST /api/solicitudes-stock`, `GET /api/proveedores?tipo=COMPONENTES`, `POST`/`PUT`/`PATCH activo`/`DELETE /api/proveedores`, `GET /api/proveedores/{idProv}/tiene-pedidos`. Cada uno se verifica contra el contrato OpenAPI antes de planificarlo.

## 5. Web

```
app/shell/SubNav.tsx                 sección `stock`: Stock actual · Pedidos · Proveedores
app/router.tsx                       /stock, /stock/pedidos (PendienteDeMigrar), /stock/proveedores
modules/almacen/
├── stock/
│   ├── StockPage.tsx                título, filtros, tabla + pie, tarjeta de gráficos
│   ├── api.ts                       consulta de componentes, cantidad en camino, mutaciones
│   ├── semaforo.ts (+ test)         estadoComponente: Desactivado / Sin stock / Bajo / OK
│   ├── filtros.ts (+ test)          estado (OR), buscador "contiene", limpiar, texto del botón
│   ├── columnas.tsx                 las seis columnas, enlace "En Camino", badge Estado
│   ├── estado.ts                    store: filtros, buscador y fila seleccionada
│   ├── GraficoEstado.tsx            donut + total + leyenda (Recharts)
│   ├── GraficoSku.tsx               barras Stock / Pedido + placeholder (Recharts)
│   ├── MenuComponente.tsx           menú contextual por rol
│   ├── EditarStockDialog.tsx
│   ├── AjustarMinimoDialog.tsx
│   └── SolicitarPiezaDialog.tsx
└── proveedores/
    ├── ProveedoresPage.tsx          título, filtro, botón, tabla, pie
    ├── api.ts
    ├── columnas.tsx
    ├── estado.ts                    store: filtro y selección
    ├── MenuProveedor.tsx
    ├── NuevoProveedorDialog.tsx
    └── EditarProveedorDialog.tsx
```

Se reutilizan `DataTable` (menú por fila, `filaClase`, `getRowId`, `seleccionada`, `onSeleccionar`, `ordenacion`), `MultiSelect` y `textoMultiSelect`, `StatusBadge` (mismos colores que el badge de proveedor del JavaFX), `EtiquetaActualizado`, `PildoraContador`, `ConfirmDialog`, `BotonPrimario`/`BotonSecundario`, `dialog`, `input`, `checkbox`, `dropdown-menu`, `tooltip`, `useRegistrarExportable` + `descargarCsv`, `useIntervaloRefresco`, `useInteraccionesAbiertas` (se mueve a `shared/api` si otro módulo lo importa), `AlertaProvider` y `mensajeDeError`, `fechas.ts`, `crearStore`/`useStore`. `Componente` ya se exporta de `shared/api/client.ts`; se añade el alias `Proveedor`. Dependencia nueva: `recharts`. Tokens nuevos en `tokens.css`: `ambar-grafico #c77a00` y `badge-sin-stock-bg #F9E0E3`. Antes de cada tarea se busca en el repo lo que va a crear.

**Datos al abrir:** una sola consulta `['componentes','gestionados']` para la tabla, el donut y el pie; `['proveedores','COMPONENTES']` para Proveedores. La barra "Pedido" del gráfico se pide al seleccionar una fila, solo para ADMIN y SUPERTECNICO; el TECNICO la ve a 0 sin llamar.

## 6. Comportamiento

El inventario del código es la referencia de detalle. Aquí va lo que define la vista.

**Columna lateral.** "Stock actual" activo al entrar; cambiar de entrada cambia de ruta y refresca la consulta de esa pestaña. Los tres roles la ven.

**Tabla de stock.** Columnas **Componente** (tipo, con el sufijo `"  (compartido)"` de dos espacios si tiene master), **En Stock**, **En Camino** (0 → "—"; > 0 → enlace azul subrayado al pasar que lleva a Pedidos con la preselección de S8), **Stock Mínimo**, **Último pedido** (`dd/MM/yyyy`, nulo → "—") y **Estado** (badge). Orden: activos primero, desactivados al final, y dentro de cada grupo el del servidor. Placeholder **"Sin componentes"**. Fila desactivada con opacidad 0.45, que prevalece sobre la selección; fila seleccionada navy con textos crema; borde izquierdo de 8 px ámbar en "Bajo" y rojo en "Sin stock". Sin ordenación por cabecera (S9).

**Semáforo.** Desactivado si no está activo; si no, "Sin stock" con stock 0, "Bajo" con `0 < stock ≤ mínimo` (también con stock negativo, calco), "OK" el resto. Badge: OK gris, Bajo ámbar `#C07800` sobre `#FDEBC8`, Sin stock rojo `#B03040` sobre `#F9E0E3`, Desactivado gris sobre `#E0E0E0`.

**Filtros.** Menú **"Estado"** con los checks "OK", "Bajo", "Sin stock" y "Desactivado" (este último solo si hay desactivados): ninguno marcado = todos; varios = OR; el botón dice "Estado", el nombre del único marcado o **"N estados"**. Buscador con placeholder **"Buscar componente…"**, "contiene" sin mayúsculas sobre el tipo sin sufijo. **"Limpiar filtros"** desmarca los checks y vacía el buscador sin tocar la selección. Los filtros no afectan al donut y sí al CSV.

**Pie.** A la izquierda **"N desactivado"** / **"N desactivados"** solo si hay alguno; a la derecha **"Actualizado HH:mm"** clicable para recargar.

**Tarjeta de gráficos** (240 px). Arriba **"Estado del stock"**: donut de tres sectores sobre la lista completa excluyendo desactivados y negativos, verde `#3a7d44` OK, ámbar `#c77a00` Bajo, rojo `#B03040` Sin stock; en el centro el total y la palabra **"total"**; debajo la leyenda con nombre y número por sector. Abajo el título **"Selecciona un componente"** y **"↑ Haz clic en una fila"** hasta que hay selección; con ella, el tipo como título y dos barras, **"Stock"** con el color del semáforo y **"Pedido"** azul `#4A6FA5`, eje "Unidades" de 0 al máximo, tooltip con el valor. Los compartidos cuentan cada uno como una fila en el donut (calco).

**Menú contextual de componente.** SUPERTECNICO: **"Pedir"**, **"Editar stock"**, separador, **"Ajustar mínimo"**, separador, **"Desactivar"** o **"Activar"**, separador, **"Solicitar pieza"**. TECNICO: solo "Solicitar pieza". ADMIN: sin menú. "Pedir" navega a `/stock/pedidos?componente=<idCom>` (4b abrirá el formulario con él precargado).

**Diálogos de componente.** Todos con título de 20 px, subtítulo **`"Componente: <tipo>   ·   Stock actual: <stock> ud(s)."`**, error inline rojo y botones **"Cancelar"** / **"Confirmar"**:
- **"Editar stock"**: etiqueta **"Nueva cantidad"**, campo precargado con el stock, Enter confirma; no entero o negativo → `"Cantidad no válida (debe ser ≥ 0)."`.
- **"Ajustar mínimo"** (S5): etiqueta **"Nuevo stock mínimo:"**, campo precargado con el mínimo; inválido → `"Valor no válido (debe ser ≥ 0)."`.
- **"Solicitar pieza"**: etiqueta **"Descripción (opcional)"**, área de texto de 3 filas con placeholder **"Motivo o contexto de la solicitud..."**, botón **"Solicitar"**; sin validación, sin mensaje de éxito, sin recarga (calco).
- **"Desactivar" / "Activar"**: sin confirmación; recarga.

**Proveedores.** Título **"Proveedores"**; filtro multiselección con solo los activos (botón "Proveedor", el nombre si hay uno, **"N proveedores"** si varios; sin "Limpiar"); botón **"Nuevo proveedor"** solo para SUPERTECNICO. Columnas **Nombre**, **Divisa**, **Estado** (badge "Activo"/"Inactivo"), **Comentario**; placeholder **"Sin proveedores"**; fila activa con borde izquierdo verde suave, inactiva sin opacidad; orden del servidor por nombre; pie "Actualizado HH:mm". Menú solo para SUPERTECNICO: **"Desactivar"**/**"Activar"**, **"Editar"** y **"Borrar"** solo si no tiene pedidos (S6). **"Nuevo proveedor"** (S5): etiqueta **"Nombre del proveedor:"**, alta con `{nombre, tipo: "COMPONENTES"}`. **"Editar proveedor"**: título `"Editar proveedor — <nombre>"`, campos **"Nombre"**, **"Divisa"** (combo navy con EUR y USD) y **"Comentario"** (3 filas); nombre vacío → `"El nombre no puede estar vacío."`. **"Borrar"**: `ConfirmDialog` **"Borrar proveedor"** / **`"¿Eliminar el proveedor \"<nombre>\"?"`** / botón **"Borrar"**.

**Campana.** "Ver Stock Completo" cierra el panel y va a `/stock`; "→ Ir a pedidos" va a `/stock/pedidos`. Ninguna aplica filtros (calco). Los otros tres botones siguen deshabilitados con su tooltip hasta 4b.

**Roles.** La vista, las columnas, el donut y el enlace "En Camino" son de los tres roles; la barra "Pedido", el menú completo, "Nuevo proveedor" y el menú de proveedores como arriba. Sin guard de ruta: las tres rutas son públicas para cualquier sesión, como en el JavaFX.

## 7. Guardado

Cada acción es una mutación de TanStack Query con el endpoint de §4.4 y, al terminar, invalida la consulta de su pestaña. Editar stock manda `{tipo, stock, stockMinimo, updatedAt}` tal como los recibió. No hay lotes ni claves de idempotencia en 4a: todas las escrituras son de una fila y el servidor ya las protege o son idempotentes por naturaleza (activar, mínimo). Las mutaciones con diálogo abierto congelan el refresco automático (`useInteraccionesAbiertas`).

## 8. Errores y refresco

- **409 al editar stock:** se cierra el diálogo, aviso **"El componente fue modificado mientras editabas. Recarga los datos."** y recarga (calco).
- **409 al borrar proveedor** (§4.2): el mensaje del servidor como error de negocio; recarga.
- **422** de §4.3: el mensaje del servidor en el error inline del diálogo, que sigue abierto.
- **Fallo de `cantidad-en-camino`:** el gráfico por SKU conserva lo anterior y el error pasa por el mapeo común (calco).
- **Solicitar pieza:** error por el mapeo común con el diálogo abierto.
- **Refresco:** 60 s conectado, 5 s con banner; recarga al volver a la pestaña; recarga manual desde "Actualizado"; congelado con un diálogo o un menú abierto. La selección se mantiene (S4).

## 9. Tests y verificación

- **Servidor:** `cantidad-en-camino` por master; borrado con y sin pedidos; las validaciones de §4.3; `OpenApiContractTest` sin rutas nuevas (no hay). La suite no tiene test de contexto de Spring: el arranque se comprueba a mano.
- **Web:** `semaforo.test` (los cuatro estados, negativo, mínimo 0), `filtros.test` (OR, ninguno = todos, buscador, texto del botón, limpiar), `GraficoEstado.test` (conteos y exclusiones), `columnas.test` (sufijo compartido, "—", enlace y sus parámetros, fecha), `StockPage.test` (roles y menú, diálogos con sus textos y errores, 409, refresco congelado, selección mantenida), `ProveedoresPage.test` (filtro solo activos, menú por rol, "Borrar" condicionado, diálogos, 409), `SubNav.test` (sección `stock`), CSV de las dos pestañas con sus cabeceras exactas, y la navegación de la campana.
- **Trazabilidad:** el plan enlaza cada regla del inventario de Stock con el test que la cubre.
- **Smoke** Playwright contra producción, en serie, solo con OK del usuario y tras desplegar el servidor: entrar en Stock, editar el stock de un componente de prueba y devolverlo a su valor, y crear un proveedor de prueba con nombre sintético y borrarlo por el `idProv` que devuelve el alta.
- **Paridad antes del tag:** ficha `docs/paridad/stock.md` marcada contra las capturas `stock-*`, `proveedores-*` y `campana-*` referenciadas solo por nombre, y **capturas de la web comparadas lado a lado con las del JavaFX antes de `v0.6.0`**.

## 10. Diferencias y calcos

| Asunto | Decisión |
|---|---|
| "Ajustar mínimo" y "Nuevo proveedor" | **Diferencia**: diálogos propios en vez de nativos (S5) |
| "Nuevo proveedor" con nombre en blanco | **Diferencia**: error inline en vez de cerrarse en silencio (S5) |
| Ordenación por cabecera | **Diferencia**: bloqueada (S9) |
| Selección tras el refresco | **Diferencia**: se mantiene (S4) |
| Borrar proveedor con pedidos en carrera | **Diferencia**: 409 con mensaje en vez de "servidor no disponible" (S6) |
| `tiene-pedidos` | **Diferencia**: se consulta al abrir el menú, no en cada clic de fila (S6) |
| Gráficos | **Diferencia** de implementación: Recharts; colores, tamaños y textos calcados (S7) |
| Dos ámbares distintos | **Calco** (S7) |
| Donut sin filtros y con compartidos como filas | **Calco** |
| Stock negativo como "Bajo" y fuera del donut | **Calco** |
| "Solicitar pieza" sin feedback ni recarga | **Calco** |
| Activar/desactivar un compartido afecta a todo el grupo sin aviso | **Calco** (D8) |
| Filtro de proveedores solo con activos y sin "Limpiar" | **Calco** |
| CSV con sus cabeceras y omisiones | **Calco** |
| "Ver Stock Completo" e "Ir a pedidos" sin filtros | **Calco** |
| Vista visible para los tres roles y `/gestionados` sin rol | **Calco** (D6: SP7) |

Cualquier diferencia nueva que aparezca al comparar capturas se decide con el usuario, no sobre la marcha.

## 11. Criterios de cierre

1. `/stock` y `/stock/proveedores` muestran las dos pestañas como la ficha; `/stock/pedidos` sigue como pendiente con el enlace y los parámetros preparados.
2. El semáforo, los filtros, los gráficos, el menú por rol y los diálogos se comportan como el inventario, con las diferencias de la §10 y ninguna más.
3. El servidor resuelve "en camino" al master, rechaza el borrado con pedidos con 409 y valida rangos y nombre con 422.
4. Los dos botones de navegación de la campana funcionan; los tres de pedir siguen deshabilitados.
5. Suites en verde en los tres repos (el cliente JavaFX sin tocar) y smoke en verde.
6. Ficha marcada y capturas comparadas lado a lado.
7. Desplegado en la VDC, servidor antes que web, con el contrato publicado idéntico al que consume la web. Push, merge, tag y despliegue, cada uno con el OK del usuario.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| Recharts no calca el donut con el total en el centro y la leyenda del JavaFX | El donut es un `PieChart` con `innerRadius`; el total y la leyenda son HTML propio encima y debajo; se compara en captura antes del tag |
| El `SubNav` compartido no admite una sección sin badges o con enlaces a rutas pendientes | Se revisa `SubNav.tsx` antes de la tarea; la sección `stock` es una función más del registro |
| Los parámetros de URL de S8 no encajan con lo que 4b decida | Se documentan en la ficha de 4a y 4b los lee de ahí; son solo nombres de query |
| Un 422 nuevo del servidor rompe al JavaFX en producción | Las validaciones coinciden exactamente con las que el cliente ya aplica antes de llamar |
| Las capturas revelan que la vista cacheada del JavaFX conserva más estado del previsto | S2 se ajusta a lo que muestre la captura `stock-cache-vuelta`; se consulta |
