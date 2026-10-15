# 0.9.15 — «Añadir otro»: los otros pedidos dentro del bloque de su proveedor

Fecha: 2026-10-14. Estado: diseño aprobado con el prototipo, pendiente del plan.

Toca **base de datos** (migración aditiva), **servidor** y **web**. Parte de servidor `main` `e169c96` y web `main` `c6fec50` (0.9.14).

Prototipo y maquetas (datos inventados, probados con Playwright), en `assets/`:
- `2026-10-14-v0915-prototipo-anadir-otro.html`: Pedidos con bloques mixtos, filtro «Tipo», el ⋮ con «Añadir otro…» y «Nuevo pedido» con «+ Añadir otro»;
- `2026-10-14-v0915-maqueta-modal-otro.html`: el modal real de «Nuevo pedido» con la línea de otro;
- `2026-10-14-v0915-pildoras-pieza-otro.html`: las píldoras «pieza» y «otro» (se eligió la C2).

Antecedente: los otros pedidos nacieron en `2026-06-26-pedidos-otros-design.md` (tabla `Compra_otro`, aislada del stock).

## 1. Problema y decisión

**Hoy:**
- Los «otros pedidos» (conceptos libres: consumibles y piezas que no están en el catálogo) van en su propia tabla, sin bloque.
- Tienen su propia página (toggle «Componentes | Otros») y su propio diálogo («Nuevo otro pedido»).
- Los bloques de la 0.9.9 solo reúnen piezas.

**Datos de preprod**, copia de producción, 2026-10-14:
- 44 otros: 40 recibidos, 1 en camino, 1 parcial y 2 cancelados.
- La mitad se pidió el mismo día y al mismo proveedor que piezas del catálogo.
- Muchos son piezas fuera del catálogo («tapa trasera iphone 17», «botonera iphone 15»…).

Es decir: lo que se le pide a un proveedor de una vez lleva piezas y otros juntos.

**Decisiones del usuario:**
- Un otro **forma parte del bloque de su proveedor**, igual que una pieza.
- **Una sola lista** en Pedidos: desaparecen el toggle «Otros», su página y «Nuevo otro pedido». Hay un filtro «Tipo».
- En «Nuevo pedido», **«+ Añadir otro»** junto a «+ Añadir línea». En el ⋮ del bloque, **«Añadir otro…»**.
- Las líneas de otro van **aisladas de la previsión**.
- Los **otros que ya existen** entran en bloques antiguos (§3.3).
- Cada línea lleva una **píldora «pieza» u «otro» delante del nombre**, del mismo ancho, para que los nombres queden alineados (C2).
- El **CSV** gana la columna «Tipo».

## 2. Conceptos

- **Línea de un bloque:** una pieza (`Compra_componente`) o un otro (`Compra_otro`).
- **Identidad de una línea:** las dos tablas tienen numeración propia, así que una línea se identifica por **tipo + id**.
  - En pantalla, el ID de un otro se ve como **«O-{id}»** (por ejemplo «O-88»). El de una pieza no cambia.
- **Situación del bloque** (spec 0.9.9, regla 3), ahora con las líneas de los dos tipos:
  - **abierto:** todas sus líneas pendientes;
  - **terminado:** ninguna pendiente, en camino ni parcial;
  - **en curso:** el resto.
- **Bloque vacío:** sin piezas ni otros. Quien lo vacía lo borra en la misma transacción, como hoy.
- **Un otro nunca toca el stock**, ni al recibirse ni al revertirse. No cuenta en la previsión, en «lo que falta», en «Pedir 60 d» ni en la columna «Pendiente» de Stock.

## 3. Base de datos

### 3.1 Migración aditiva (`sql/migracion-otros-en-bloques.sql`)

```sql
ALTER TABLE Compra_otro
    ADD COLUMN ID_BLOQUE INT NULL AFTER ID_PROV,
    ADD CONSTRAINT fk_compra_otro_bloque FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    ADD INDEX idx_compra_otro_bloque (ID_BLOQUE);
```

- Lleva vista previa y comprobación (`EXISTE_COLUMNA` 0 → 1), como `migracion-abrir-glass.sql`.
- La columna admite nulo, igual que en `Compra_componente`. En la práctica solo queda nula durante el hueco del despliegue.

### 3.2 Recuento antes de migrar (solo lee)

En preprod y en producción, antes de la migración, un bloque que solo lee y cuenta:
- cuántos otros hay;
- cuántos entrarían en un bloque antiguo existente (mismo proveedor y día);
- cuántos bloques antiguos nuevos se crearían.

### 3.3 Reparto de los otros existentes (`sql/reconstruir-bloques-otros.sql`)

Mismo patrón que `reconstruir-bloques-compra.sql` de la 0.9.9:
1. **Bloques antiguos nuevos:** por cada proveedor y día de los otros sin bloque que no tengan ya un bloque `RECONSTRUIDO` de ese proveedor y día, se crea uno, con `FECHA` = el primer `FECHA_PEDIDO` del grupo y `RECONSTRUIDO = TRUE`.
2. **Asignación:** cada otro sin bloque va al bloque `RECONSTRUIDO` del mismo proveedor y del día de su `FECHA_PEDIDO`.
   - **Solo a bloques antiguos.** Un otro recibido metido en un bloque actual abierto lo dejaría «en curso», y «Nuevo pedido» dejaría de sumarle líneas.
   - Se conserva el `UPDATED_AT` del otro (`UPDATED_AT = UPDATED_AT`).
3. **Comprobaciones finales:**
   - `otros_sin_bloque` 0;
   - `bloques_vacios` 0, contando piezas y otros;
   - `otros_de_otro_proveedor` 0;
   - total de bloques reconstruidos;
   - el `MAX(UPDATED_AT)` de `Compra_otro` igual que antes.

`reconstruir-bloques-compra.sql` (0.9.9) **se actualiza**: su paso «borrar bloques vacíos» tiene que contar también los otros. Si no, un bloque que solo lleva otros se borraría si alguien lo vuelve a ejecutar.

## 4. Servidor

### 4.1 Bloques (`BloqueCompraDAO`)

Todas las consultas de bloque cuentan las dos tablas:
- **Bloque abierto más reciente** (`SQL_ABIERTO`): tiene alguna línea y ninguna, ni pieza ni otro, distinta de pendiente.
  - **Un bloque antiguo (`RECONSTRUIDO`) nunca se prefiere a uno actual** (decisión del usuario, 2026-10-15). Se ordena primero por `RECONSTRUIDO` y luego por número descendente, así que uno antiguo solo se elige si no hay otro abierto.
  - Motivo: la reconstrucción puede crear un bloque antiguo con solo otros pendientes. Sería «abierto» y tendría el número más alto, y se llevaría lo que se pida por defecto a ese proveedor.
  - La web hace lo mismo al proponer el destino.
- **Líneas del bloque** (`lineas`): unión de las dos tablas.
  - Cada `LineaBloque` lleva `tipo` (`PIEZA` u `OTRO`), el id, el nombre (el `TIPO` del componente o el `CONCEPTO`), la cantidad, el estado y el `updatedAt`.
  - Orden: `FECHA_PEDIDO` y luego tipo e id.
- **Contar, vivas y borrar si vacío** (`contarLineas`, `estaTerminado`, `borrarSiVacio`): cuentan las dos tablas. Un bloque se borra solo si no le queda ni pieza ni otro.

### 4.2 Altas

**`POST /api/compras/lote`** (el que usa «Nuevo pedido») acepta además `otros`: una lista de `{idProv, concepto, cantidad, esUrgente, precioUnidad}`. Es el mismo registro del lote de otros de hoy, sin divisa: la divisa es la del proveedor.
- **Destino:** las piezas y los otros de un mismo proveedor van al **mismo bloque**: el de `destinos`, o si no viene, el abierto más reciente o uno nuevo. Se resuelve una vez por proveedor (`bloquePorProveedor`), igual que hoy.
- **Una sola transacción** y la misma clave de idempotencia de hoy.
- **Validación de los otros:** las reglas del lote de otros de hoy (`ValidacionPedidos`):
  - concepto informado;
  - cantidad y precio válidos;
  - divisa del proveedor;
  - `precioEur` calculado en el servidor;
  - **nuevo:** el concepto recortado no pasa de 255 caracteres, el tamaño de la columna (hoy daría un 500).

  Un 422 de un otro dice qué línea falla: «Otro n: …», por ejemplo «Otro 2: el concepto no puede estar vacío.».
- **Respuesta:** `idsCreados` (piezas, como hoy) y `idsOtrosCreados`.
- **Registro:** `CREAR_BLOQUE` si se crea un bloque, y `CREAR_PEDIDO_OTRO` por cada otro, con el detalle de siempre más `BLOQUE: n`.

**`POST /api/compras-otros/lote`** y **`POST /api/compras-otros`** (la web vieja, durante el despliegue) siguen funcionando. Ahora también meten el otro en un bloque, con la regla por defecto: el abierto más reciente del proveedor, o uno nuevo.

### 4.3 Acciones de bloque (`BloqueCompraService`)

**Las líneas que manda la web** (`LineaVista`) ganan el campo `tipo`, que puede ser `PIEZA` u `OTRO`. Si no viene, se toma `PIEZA`: es lo que manda la web 0.9.14, que solo conoce piezas. La comprobación es la de hoy, todo o nada (409): que la línea exista en el bloque, con la misma versión y en el estado de la acción. Ahora se compara por tipo + id.

| Acción | Pieza | Otro |
|---|---|---|
| Confirmar | `compraDao.confirmar` | `compraOtroDao.confirmar` |
| Recibir | `confirmarRecibido` (suma stock) | `confirmarRecibido` (sin stock) |
| Cancelar | `cancelar` | `cancelar` |
| Borrar (bloque entero) | `borrarPendiente` | `borrarPendiente` |

- **Separar y juntar:** mueven líneas de los dos tipos con las reglas de hoy. Separar exige que al menos una línea se quede; solo se mueven líneas vivas; juntar exige el bloque entero.
- **Mover a bloque:** ruta nueva `PATCH /api/compras-otros/{id}/bloque`, con las mismas reglas que la de piezas:
  - una línea recibida o cancelada no se mueve (422);
  - con «Bloque nuevo» siendo la única línea, 422;
  - si el origen se queda vacío, se borra.
- **Editar un otro cambiando el proveedor** (`PUT /api/compras-otros/{id}` con `destino`): lo lleva a un bloque del proveedor nuevo y borra el de origen si se queda vacío, como `editarCambiandoProveedor`.
- **Borrar un otro pendiente** (`DELETE /api/compras-otros/{id}`): borra también su bloque si se queda vacío.

**Registro:**
- Cada línea apunta la acción de siempre de su tipo: `CONFIRMAR_PEDIDO` o `CONFIRMAR_PEDIDO_OTRO`, `RECIBIR_PEDIDO` o `RECIBIR_PEDIDO_OTRO`, etc.
- El apunte del bloque lista los ids por tipo: `BLOQUE: n, ID_COMPRA: 12, 13, ID_COMPRA_OTRO: 88`.
- `MOVER_LINEA_BLOQUE` de un otro dice `ID_COMPRA_OTRO: n`.

### 4.4 Lecturas

`GET /api/compras-otros` devuelve, como ya hace `GET /api/compras`:
- `idBloque`;
- `fechaBloque`;
- `notaBloque`;
- `bloqueUpdatedAt`;
- `bloqueAntiguo`.

El contrato OpenAPI se actualiza (`OpenApiContractTest`).

## 5. Web

### 5.1 Pedidos: una sola lista

- **Lo que desaparece:**
  - el toggle «Componentes | Otros», la ruta propia de Otros y el botón «Nuevo otro pedido»;
  - `NuevoOtroPedidoDialog`, `abrirNuevoOtroPedido` y su caso en `FormulariosPedido`.
- **La dirección antigua** `/stock/pedidos/otros` redirige a `/stock/pedidos` con el filtro «Tipo: Otros».
- **Datos:** la página junta `GET /api/compras` y `GET /api/compras-otros` en una lista de líneas, cada una con su tipo.
  - La clave de fila y de selección es `p-{id}` u `o-{id}`.
  - `agruparEnBloques`, `ordenarPorBloques`, `situacion`, `resumenEstados`, `totalBloque`, `lineasQueCambian` y el resto de las reglas de bloque trabajan con las dos.
- **Columna «Componente / concepto»:**
  - Delante del nombre, una píldora de ancho fijo: **«pieza»** (azul suave) u **«otro»** (lila). Así los nombres quedan alineados.
  - La pieza mantiene su enlace a Stock; el otro no lleva enlace.
- **Columna ID:** «O-{id}» para un otro.
- **Filtro «Tipo»**, en la barra de filtros: segmentado «Todos | Piezas | Otros», que por defecto está en «Todos».
  - Cuenta como filtro: con un tipo elegido, los bloques salen desplegados y con «mostrando k de n».
  - «Limpiar filtros» lo devuelve a «Todos».
  - Se recuerda igual que los demás filtros de Pedidos.
  - Los filtros Estado, Proveedor, Desde/Hasta y el buscador valen para las dos. El buscador busca también en el concepto.
- **Cabecera del bloque:** «{n líneas} ({m} otro/otros)» cuando hay otros. El total y el resumen de estados cuentan los dos; «! N sin precio» también cuenta los otros recibidos a 0.
- **Vista plana** («Agrupar por bloque» desactivado): piezas y otros juntos, con la columna «Bloque» y la columna «Proveedor».
- **Orden de los bloques** (cambia la regla 7 de la 0.9.9): primero por **fecha del bloque**, de más reciente a más antigua, y a igual fecha por número descendente. «Sin bloque» sigue al final.
  - Motivo: la reconstrucción (§3.3) crea bloques antiguos con números más altos que los actuales, pero con fechas de junio a septiembre. Ordenando solo por número saldrían arriba del todo.
  - En los bloques actuales la fecha y el número van en el mismo orden, así que no cambia nada de lo que se ve hoy.
- **Menú de una fila de otro:**
  - las transiciones que tenía en la página Otros: confirmar, recibido, parcial, resto, cerrar sin resto, cancelar, borrar, revertir y editar, con sus endpoints de `compras-otros` y sus diálogos de hoy;
  - más **«Mover a bloque»**, con las reglas de las piezas.
- **«Editar» de un otro** (`EditarOtroPedidoDialog`): con otro proveedor, gana el campo «Bloque», igual que el editor de piezas.
- **Diálogos del bloque** (casillas, separar y juntar): las líneas de otro salen con su píldora, su «O-{id}» y su concepto.
  - En las casillas, la etiqueta accesible de un otro es «{concepto} (ID O-{id})».
- **Menú del bloque:**
  - **«Añadir otro…»** va justo debajo de «Añadir líneas…», deshabilitado si el bloque está terminado, con el motivo «el bloque está terminado».
  - Al elegirlo, abre «Nuevo pedido» en modo bloque, con una línea de otro y el cursor en su concepto.
  - Solo lo ve el supertécnico, como el resto de entradas que escriben.

### 5.2 «Nuevo pedido» (y «Añadir líneas» de un bloque)

- **Botones:** en cada recuadro de proveedor, **«+ Añadir otro»** junto a «+ Añadir línea», con el borde y el texto en lila.
- **La línea de otro lleva:**
  - la píldora «otro»;
  - un **campo de texto libre**, blanco con borde lila y el texto «Escribe el concepto…», hasta 255 caracteres;
  - cantidad, precio, urgente, total y papelera, como una pieza.
- **Las líneas de pieza** llevan delante la píldora «pieza». La cabecera de la columna pasa a ser «Componente / concepto».
- **Aislada de la previsión:**
  - no cuenta en «lo que falta» ni en «Añadir previsión»;
  - no se comprueba si está repetida, así que el mismo concepto puede ir dos veces;
  - no entra en el reparto «Por repartir».
- **Validación:** se comprueba con las mismas reglas y en el mismo orden que hoy; el número de línea es su posición en el recuadro.
  - Concepto vacío: «{Proveedor} · Línea {n}: escribe el concepto.».
  - Cantidad o precio inválidos: los textos de hoy.
- **Guardar:** un solo `POST /api/compras/lote` con las piezas, los otros y los destinos por proveedor. Un recuadro con solo otros también crea su bloque o se suma al abierto.
- **El botón** «Confirmar pedido ({n} bloques)» cuenta los recuadros con líneas de cualquier tipo.

### 5.3 CSV

Afecta al general («Descargar CSV») y a «Exportar bloque en CSV».

- **Columnas:** `ID;Tipo;Bloque;Nota del bloque;Fecha pedido;Componente;Cantidad;Urgente;Proveedor;Precio unidad;Divisa;Total EUR;Estado`.
- **Tipo:** `pieza` u `otro`.
- **ID:** el de un otro es `O-{id}`.
- **Componente:** para un otro, su concepto.
- Las filas son las de la tabla visible, como hoy. «Exportar bloque en CSV» exporta todas las del bloque.

## 6. Lo que no cambia

- Las transiciones y los estados de un otro, y su editor (salvo el campo «Bloque» al cambiar de proveedor).
- Las piezas: su previsión, el stock, la campana, las solicitudes y «Por repartir».
- Las reglas de bloque de la 0.9.9 y la 0.9.13: casillas, parciales fuera de las acciones de bloque, un bloque terminado cerrado.

## 7. Pruebas

### 7.1 Servidor

- **`BloqueCompraDAOTest`** (SQL con `JdbcTemplate` simulado, como hoy):
  - las consultas de abierto, líneas, contar, vivas y borrar si vacío cuentan las dos tablas;
  - el SQL real se valida en preprod (migración, recuento y E2E).
- **`CompraLoteServiceTest` y `CompraLoteControllerTest`:**
  - lote con piezas y otros del mismo proveedor: un solo bloque;
  - lote con solo otros;
  - destino explícito;
  - 422 «Otro n: …»;
  - idempotencia;
  - todo o nada.
- **`BloqueCompraServiceTest` y `BloqueCompraControllerTest`:**
  - confirmar, recibir, cancelar y borrar con líneas mixtas;
  - recibir un otro no toca el stock;
  - una línea sin `tipo` se trata como pieza;
  - un otro con el mismo id que una pieza no se confunde;
  - separar y juntar mixtos;
  - los logs por tipo.
- **`CompraOtroControllerTest`:**
  - mover a bloque;
  - editar con otro proveedor;
  - borrar el último otro borra el bloque;
  - el lote antiguo de otros asigna bloque.
- **Contrato OpenAPI:** `OpenApiContractTest`.
- **Migración:** script de prueba contra una base con datos sintéticos:
  - otros de días con bloque antiguo y sin él;
  - un bloque actual abierto del mismo día, que no se toca;
  - las comprobaciones de §3.3.

### 7.2 Web (Vitest + MSW)

- **Reglas:**
  - agrupar líneas mixtas;
  - situación, total y resumen;
  - `lineasQueCambian` con otros;
  - filtro «Tipo»;
  - buscador por concepto.
- **Pedidos:**
  - lista mezclada con píldoras y «O-{id}»;
  - filtro «Tipo» y «mostrando k de n»;
  - menú de fila de un otro, incluido «Mover a bloque»;
  - ⋮ «Añadir otro…»;
  - la ruta antigua `/stock/pedidos/otros`.
- **Diálogos:**
  - casillas con otros: el cuerpo lleva `tipo`;
  - separar y juntar con otros.
- **Nuevo pedido:**
  - «+ Añadir otro»;
  - concepto vacío;
  - aislado de la previsión y de las repetidas;
  - el cuerpo del lote con `otros`;
  - recuadro con solo otros.
- **CSV:** «Tipo» e «O-{id}».

### 7.3 E2E en preprod (Chromium, datos sintéticos y limpieza)

- **`bloques.spec`** (o un fichero nuevo `otros-en-bloques.spec`):
  1. «Nuevo pedido» con una pieza y un otro al proveedor sintético crea **un** bloque con las dos;
  2. «Añadir otro…» desde el ⋮;
  3. «Confirmar bloque» y «Recibir todo»: el stock solo sube por la pieza;
  4. limpieza.
- **`pedidos.spec`:** hoy crea, edita y borra un otro en la página «Otros». Se adapta a la lista única y a «+ Añadir otro».
- **La limpieza 2c** de la guía de preprod borra también los otros del proveedor sintético.

## 8. Despliegue y documentación

**Preprod:**
1. Bloque que solo lee y el recuento de §3.2.
2. `migracion-otros-en-bloques.sql`.
3. `reconstruir-bloques-otros.sql`.
4. Servidor.
5. Web.
6. E2E y limpieza.

**Producción:**
1. Copia de la base.
2. Recuento.
3. Migración y reconstrucción.
4. Servidor.
5. Web.

**Candidata a hacerse en horario:**
- la migración solo añade una columna y rellena 40 y pico filas;
- la web 0.9.14 sigue funcionando con el servidor nuevo: las líneas sin `tipo` son piezas, y el lote antiguo de otros sigue y asigna bloque;
- la vuelta atrás es solo de código, y la columna se queda.

La guía dirá si se hace en horario según el recuento.

**Ventana del despliegue:**
- Entre la reconstrucción y el arranque del servidor nuevo, el servidor 0.9.14 no puede vaciar un bloque antiguo que ya tiene otros: juntar, borrar el bloque entero o mover su última pieza fallan por la clave ajena, sin cambiar nada. Por eso el servidor nuevo arranca justo después de la reconstrucción, en el mismo bloque del guion.
- Hasta que el taller recarga la página, la web 0.9.14 no conoce los otros de un bloque mixto. Borrar o juntar ese bloque desde ella da 409 («modificado por otro usuario») y no cambia nada.

**Documentación:**
- CHANGELOG `[0.9.15]` de la web. El servidor no tiene CHANGELOG: su contrato va en `docs/api_contract.md`;
- `docs/novedades/NOVEDADES-v0.9.15.md`;
- la ficha de paridad de Pedidos;
- las guías `Apuntes/preprod/v0915-preprod.md` y `Apuntes/prod-v0915.md`.

## 9. Fuera de alcance

- Convertir un otro en pieza del catálogo.
- Informes de gasto que separen piezas y otros.
- Precio del último pedido o sugerencias para los otros.
