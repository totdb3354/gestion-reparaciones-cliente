# Sub-proyecto 4 — Almacén, Inventario y Revisión: partición y decisiones comunes

**Fecha:** 2026-09-24
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario. **Actualización 2026-09-26:** 4a y 4b están cerrados (web v0.6.0 y v0.7.0). Por decisión del usuario, la web migra solo lo que la tienda usa hoy, la línea `hotfix/0.16.3` del cliente: **4c, 4d y 4e quedan fuera de la migración por ahora** (Inventario, lotes, envíos, suppliers, importador y Revisión solo existen en `main` del cliente y la tienda no los usa). El siguiente sub-proyecto es el 6 (Gestión). El merge de `hotfix/0.16.3` en `main` del cliente previsto en §3 sí se hizo ese día.
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra; sus decisiones generales no se repiten aquí). Antecesor: [Asignar trabajos (3b)](2026-09-22-web-asignar-trabajos-design.md).
**Ámbito:** este documento no se planifica directamente. Fija la partición del sub-proyecto 4 en cinco partes, el orden, las decisiones que las cruzan y lo que sube al servidor en cada una. Cada parte tiene su propia spec y su propio plan: la primera es [4a Stock actual y Proveedores](2026-09-24-web-almacen-stock-design.md).

## 1. Objetivo

Migrar a la web todo lo que el JavaFX tiene bajo las pestañas **Stock** (stock actual, pedidos de componentes y "otros", proveedores) e **Inventario** (inventario de teléfonos con lotes, alta manual, importador xlsx, envíos, devoluciones, suppliers y el panel de Revisión con su ficha), subiendo al servidor las reglas de negocio que hoy viven solo en el cliente.

La referencia de detalle son cuatro inventarios del código del JavaFX, guardados fuera del repo (llevan datos reales): `inventario-stock.md`, `inventario-pedidos.md`, `inventario-inventario-lotes.md` e `inventario-revision.md`. Suman unas 8.900 líneas de controladores y diálogos frente a las 985 del modal del 3b.

## 2. Referencias del cliente JavaFX

- `main` del cliente no tiene mergeado ningún hotfix 0.16.x (tags existentes: v0.16.0, v0.16.1, v0.16.2).
- **Stock, Pedidos, Proveedores y los cuatro formularios de pedido:** referencia `hotfix/0.16.3`, la línea que usa la tienda. `main` solo difiere en `?tipo=COMPONENTES` en las llamadas a proveedores y en extraer "Editar proveedor" a un diálogo propio; no hay commits de almacén en el hotfix que falten en `main`. La web adopta el `?tipo=COMPONENTES` desde el principio porque el servidor de `main` ya distingue proveedores de componentes y de teléfonos.
- **Inventario, lotes, importador, envíos, suppliers y Revisión:** solo existen en `main` del cliente.
- En `main` la vista Agrupado/IMEIs se movió de Reparaciones a Inventario y cambió de fuente de datos (la agregación por IMEI la hace el servidor en `GET /api/telefonos/inventario`). La web migró en el sub-proyecto 1 la versión de `hotfix/0.16.3`.

## 3. Partición y orden (D1, D2, D16)

| Parte | Contenido | Referencia | Tag web |
|---|---|---|---|
| **4a** | Stock actual + Proveedores: tabla, semáforo, filtros, gráficos, menú y diálogos, proveedores, salidas de la campana hacia Stock | hotfix | v0.6.0 |
| **4b** | Pedidos (Componentes y Otros) + los cuatro formularios + los botones de pedir de la campana | hotfix | v0.7.0 |
| **4c** | Inventario: rebase de la vista IMEIs sobre `main`, vista Inventario, alta manual de lote, envío, devolución, Suppliers | main | v0.8.0 |
| **4d** | Importador xlsx con el parser en el servidor | main | v0.9.0 |
| **4e** | Panel de Revisión, "A revisar (masivo)" y Ficha de revisión | main | v0.10.0 |

Orden 4a → 4b → 4c → 4d → 4e: primero lo que la tienda usa a diario. Cada parte lleva spec, plan, ramas `feature/web-<parte>` en la web y en el servidor creadas desde `main`, smoke Playwright, ficha de paridad con capturas comparadas y tag, todo con el OK del usuario paso a paso.

**Antes de 4c** se mergea `hotfix/0.16.3` en `main` del cliente, vigilando tres artefactos que el inventario detectó: `main` perdió el `isActivo()` en la regla de alerta de stock (hay que conservar la versión del hotfix, que es la que copia la web), y no tiene `CeldaReparador` ni `setFiltroInicial` de la vista Agrupado. A partir de 4c la referencia de la web es `main`, que es lo que será v0.17.0.

**2026-09-26:** el merge se hizo sin conflictos textuales y con los tres artefactos conservados (suite del cliente 287 tests), pero 4c, 4d y 4e no se ejecutan: la web calca únicamente `hotfix/0.16.3` y las vistas que solo existen en `main` no se migran por ahora (ver Estado). Las decisiones D4, D5, D11, D12, D14 y D15 quedan como referencia si esas partes se retoman después del corte.

**De esta sesión salen** este documento, la spec y el plan de 4a. Las partes 4b a 4e se brainstormean corto y se especifican en su propia sesión, con este documento y sus inventarios como contexto.

## 4. Decisiones comunes

| # | Decisión | Por qué |
|---|---|---|
| D3 | **Pedidos (4b): todo sube al servidor, aditivo.** Guards de estado y de rango en cada transición (409/422), `POST /api/compras/lote` y `/compras-otros/lote` atómicos con `Idempotency-Key`, `precioEur` calculado en el servidor a partir de precio y divisa, y corrección del sentido de la tasa | Hoy la máquina de estados solo la aplica el menú del cliente, la recepción parcial acepta negativos, el alta son N POST sin transacción y el cliente multiplica por una tasa que Frankfurter devuelve como "divisa por 1 EUR" (debería dividir). El JavaFX ya cumple esas reglas, así que los guards no lo rompen |
| D4 | **Importador (4d): el parser vive en el servidor.** `POST /api/lotes/previsualizar` multipart con POI, mapeo de modelo y color, catálogo de atributos, clasificación de duplicados, agrupación por batch y `precioCompraEur`; `/importar` sigue recibiendo el JSON limpio. Los tests del parser y del clasificador se portan a JUnit | Es lo que dice la spec maestra §4.3 y deja la lógica en un solo sitio. Ni el servidor tiene POI ni la web SheetJS: hay que añadir una de las dos, y el servidor es la fuente de verdad |
| D5 | **Veredicto de revisión (4e): lo devuelve el servidor** en `GET /{imei}/revision` y en los dos PATCH; `VeredictoRevision` se porta a Java con sus tests; la web solo pinta | El umbral 85 está duplicado en cliente y servidor. Aditivo: el JavaFX ignora el campo nuevo |
| D6 | **Guards de escritura en el 4, lecturas sin rol en SP7.** Cada parte cierra en el servidor los guards de sus escrituras (409 al borrar proveedor o supplier con pedidos, 422 sin motivo, rangos). Los GET sin `@PreAuthorize` (`/inventario`, `/lotes`, `/movimientos`, `/tipo-cambio`, `/gestionados`) van a la lista del SP7 en `plan-futuro` | Cerrar `/inventario` a TECNICO afecta a la vista IMEIs de `main`, que lo usa; es una decisión de rol, no de calco |
| D7 | **Recharts** entra en 4a para el donut y las barras de Stock y queda para Estadísticas | Dependencia prevista en la spec maestra; evita un SVG a mano que luego se tiraría |
| D8 | **SKU compartidos en Stock:** el servidor resuelve `cantidad-en-camino` al master; las acciones sobre filas compartidas se calcan tal cual. La unificación del mínimo master/slave y el bloqueo optimista de slaves siguen en F5 | El primero es un bug claro y aditivo; lo demás ya estaba en el backlog |
| D9 | **Rutas calcadas:** `/stock`, `/stock/pedidos`, `/stock/pedidos/otros`, `/stock/proveedores`; quinta entrada "Inventario" en la barra superior solo ADMIN/SUPERTECNICO con `/inventario`, `/inventario/:imei`, `/inventario/revision`, `/inventario/suppliers`. Filtros en el estado de cada página, conservados al cambiar de sección dentro de Stock. Guards de rol en el router | La barra superior del JavaFX tiene Stock e Inventario como pestañas distintas |
| D10 | **Campana (4b):** se activan los cinco botones hacia Stock y Pedidos. El lote de compras acepta los `idSol` que originan cada línea y los marca `GESTIONADA` en la misma transacción; un componente inactivo se avisa y su solicitud no se marca. "Ver Stock Completo" e "→ Ir a pedidos" se activan ya en 4a | Hoy el marcado son N PATCH sueltos y el inactivo se marca sin pedirse |
| D11 | **Ficha de revisión (4e): modal** con el refresco de la cola congelado mientras está abierta; "Asignar trabajos…" navega a `/reparaciones/asignaciones?imei=&tipo=` y abre el modal del 3b precargado | Calco del Stage modal; mismo patrón que Asignar trabajos |
| D12 | **Reglas de revisión: se calca el código.** "Marcar OK" se permite con trabajos pendientes si batería ≥ 85 y sin bloqueo; la escala de grado queda C/B/A-/A (sin A+). Se anotan como diferencias aceptadas respecto a la spec F2 | Sin ALTER de enums ni cambio de comportamiento |
| D13 | **Manda el código del JavaFX; solo se corrigen los textos demostrablemente falsos**, listados uno a uno en cada spec (como el subtítulo del modal en el 3b). Diálogos nativos de JavaFX pasan a diálogos propios con el estilo de "Editar stock". Ordenación por cabecera bloqueada como en Asignaciones. Toda diferencia nueva se consulta | Es lo que la tienda ve hoy; las specs de junio no siempre coinciden con el código |
| D14 | **Fuera de alcance del 4:** alta y borrado de componente, override manual de ubicación, panel de lotes con porcentajes, unificación del mínimo master/slave, lock de slaves, cruce de modelo sin mapear con el lookup | Paridad estricta: el 4 migra lo que el JavaFX muestra. Todo queda en `plan-futuro` |
| D15 | **Idempotencia:** clave en envío, devolución y a-revisar (además del lote de compras); cambio de estado y alta manual se quedan como están (ya protegidos por `WHERE ESTADO` e idempotentes por batch). La cola de Revisión se calca sobre `/inventario` filtrado en la web, compartiendo la consulta con la pestaña Inventario | Registro de idempotencia ya en el servidor desde el sub-proyecto 2 |

## 5. Servidor por parte (todo aditivo; el JavaFX 0.16.x sigue funcionando)

| Parte | Cambios |
|---|---|
| 4a | `GET /api/compras/cantidad-en-camino/{idCom}` resuelto al master; `DELETE /api/proveedores/{idProv}` responde 409 con mensaje si tiene pedidos; 422 en `stock < 0`, `stockMinimo < 0`, nombre de proveedor vacío y divisa fuera de {EUR, USD} |
| 4b | Guards de estado y rango en `confirmar-recibido`, `confirmar-parcial`, `recibir-resto`, `confirmar-alterado`, `cancelar`, `desrecibir` y `PUT`; `POST /api/compras/lote` y `/compras-otros/lote` con `Idempotency-Key`, `precioEur` calculado en servidor y marcado de solicitudes; tasa en el sentido correcto; nullables del contrato (`cantidadRecibida`, `fechaLlegada`) |
| 4c | `Idempotency-Key` en `POST /api/envios` y `/telefonos/devoluciones`; 409 al borrar supplier con lotes; motivo obligatorio en devolución y bloqueo (422) |
| 4d | `POST /api/lotes/previsualizar` (multipart, POI); parser, mapeos, clasificador y catálogo portados con sus tests |
| 4e | Veredicto en las respuestas de revisión; `Idempotency-Key` en `POST /telefonos/a-revisar`; `@Schema(nullable = true)` en `Revision`, `TelefonoRevisionResponse`, requests de estética y funcional y `TelefonoInventario`; validación de batería y de longitudes (422) |

## 6. Paridad y verificación (común a las cinco partes)

Igual que en el 3b: ficha `docs/paridad/<vista>.md` en el repo web marcada contra las capturas del JavaFX (citadas solo por nombre, guardadas fuera del repo en `paridad-capturas/almacen/`), tabla de trazabilidad regla del inventario → test en cada plan, smoke Playwright contra producción con la web de la rama en local y solo tras desplegar el servidor, borrando únicamente lo que crea e identificado por lo que devuelve el servidor, y capturas de la web comparadas lado a lado con las del JavaFX antes del tag. Las suites de los tres repos en verde; el cliente JavaFX no se toca.

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| Cinco partes alargan el programa | Cada parte es un bloque cerrado con tag; el programa puede pausarse entre bloques. El orden pone primero lo que la tienda usa a diario |
| Dos referencias del cliente (hotfix y main) | 4a y 4b calcan hotfix, que en esta parte es equivalente a main; el merge hotfix → main se hace antes de 4c con los tres artefactos vigilados |
| Datos históricos de pedidos en USD con `precioEur` mal calculado | Antes de 4b se comprueba en la BD si existe algún proveedor o pedido en USD; si existe, se corrige con una consulta aparte y con el OK del usuario |
| Los guards de estado en el servidor rompen algún flujo del JavaFX | El inventario documenta las transiciones que el menú del cliente permite; los guards se escriben para esas mismas transiciones y se prueban con la matriz completa |
| POI en el servidor y un endpoint multipart son terreno nuevo en este proyecto | 4d es una parte pequeña y aislada; el parser se porta con sus tests del cliente |
