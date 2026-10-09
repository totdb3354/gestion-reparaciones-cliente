# 0.9.9 — Pedidos por bloques

Fecha: 2026-10-09. Versión del producto: **0.9.9** (servidor y web etiquetados juntos), después de la 0.9.8.
Prototipo aprobado: `assets/2026-10-09-v099-prototipo-bloques.html` (HTML suelto, datos inventados; se abre en el
navegador). Ante cualquier duda de aspecto o de textos, manda el prototipo salvo donde esta spec diga otra cosa.

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de las máquinas y
el registro de los despliegues viven fuera de git, en `Apuntes/`.

## 1. Problema

Cada fila de Pedidos es una línea de `Compra_componente` (un componente, un proveedor, una cantidad y su estado).
«Nuevo pedido» ya crea varias líneas de golpe (`POST /api/compras/lote`), pero al guardarlas se pierde que iban
juntas: después se buscan, se confirman y se reciben una a una. En el taller, en cambio, se pide **por bloques**: un
conjunto de piezas que se piden de una vez a un mismo proveedor (normalmente uno al día, pero no siempre; puede haber
dos bloques del mismo proveedor el mismo día).

Un bloque es lo que un ERP llama la **cabecera del pedido de compra** (Odoo, Dolibarr, ERPNext: un proveedor y N
líneas). Esta versión la añade sin cambiar cómo funciona cada línea.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Qué es un bloque | Tabla nueva `Bloque_compra`; cada línea de `Compra_componente` pertenece a un bloque | Modelo clásico de cabecera y líneas |
| Proveedor | **Un proveedor por bloque**; todas sus líneas son de ese proveedor | El bloque es «lo que se le pide a un proveedor» |
| Estado del bloque | **No se guarda**: se deriva de sus líneas | El estado vive en cada línea (parciales, cancelaciones); nada que desincronizar |
| Nacimiento | Al guardar «Nuevo pedido», las líneas de cada proveedor **se suman a su bloque abierto más reciente**; en el modal se puede elegir «Bloque nuevo» | Encaja con «uno al día», como Odoo y Dolibarr |
| Bloque abierto | El que tiene **todas** sus líneas pendientes | Es el que aún no se ha pedido al proveedor |
| Mover líneas | Pendiente: libre. Resto de estados: **con confirmación** y apuntado en el log | Mover no toca el stock; se permite corregir errores descubiertos tarde |
| Destino al mover | Solo bloques **del mismo proveedor** o «Bloque nuevo» | El proveedor arrastra divisa y precio |
| Cambiar de proveedor | Solo desde **«Editar»**, eligiendo bloque del proveedor nuevo | «Editar» ya revisa precio y divisa |
| Varias líneas a la vez | Acciones de bloque «Separar líneas…» y «Juntar con…»; **sin selección múltiple** en la tabla | No toca la selección de la tabla compartida por 19 pantallas |
| Acciones de estado | Por bloque (atajos) **y** por línea (como hoy) | Una pieza puede llegar antes que las demás |
| Bloques con líneas mezcladas | Las acciones se aplican **solo a las líneas que pueden recibirlas**, con contador y confirmación que lista qué cambia y qué no | Deshabilitarlas bloquearía el caso normal |
| Borrar bloque | **Todo o nada**: solo si todas sus líneas están pendientes | Un bloque con algo pedido es un pedido real |
| Cambios simultáneos | Todo o nada: si alguna línea cambió desde que se abrió el diálogo, 409 y no se aplica nada | Nunca queda un bloque a medias |
| Bloque vacío | Se borra solo, en la misma operación | Sin cabeceras vacías |
| Extras del bloque | **Nota** (200 caracteres): nº de pedido o factura, seguimiento… | Se pidió; sale en cabecera y CSV |
| Quién creó el bloque | **No se guarda en la tabla**; queda en el log (`CREAR_BLOQUE`) | Una clave a `Usuario` añadiría una décima referencia al borrado de usuarios |
| Pedidos ya existentes | **Reconstruidos**: un bloque por proveedor y día, marcados «antiguo» | Toda línea tiene bloque; el código no arrastra el caso «sin bloque» |
| Vista | **Agrupada por defecto**, con interruptor a la vista plana de hoy (el navegador lo recuerda) | La plana sigue siendo útil para buscar en todo el historial |
| Plegado | Desplegados los bloques con algo por hacer, plegados los terminados; lo manual se respeta durante la sesión | Lo vivo a la vista, lo cerrado recogido |
| Filtros | Actúan sobre las **líneas**; se ven solo las que cumplen («mostrando k de n»); resumen y total, del bloque entero | Buscar «pantalla» enseña las pantallas de cada bloque |
| Arrastrar filas | **Fuera** (backlog) | Choca con el pintado parcial y el sondeo; WCAG 2.2 §2.5.7 exige alternativa igualmente |
| Gastos de envío | **Fuera** (backlog) | Hoy no se registran; la nota sirve si hace falta |
| Pedidos «Otros» | **Fuera** (backlog): siguen planos | Otra tabla con otras columnas; casi dobla el trabajo |
| Auto con bloques | **Fuera**: versión aparte tras unas semanas de uso | El proveedor depende de la pieza y cambia; los bloques darán los datos para diseñarlo |
| Versión | **0.9.9**, después de la 0.9.8 | Sin mezclar migraciones de dos versiones |

## 3. Reglas del bloque

Estas reglas se cumplen siempre. Las comprueba el servidor; la web las usa para pintar y para habilitar acciones.

1. Todas las líneas de un bloque tienen el `ID_PROV` del bloque.
2. Un bloque sin líneas no existe: la operación que lo vacía lo borra en la misma transacción.
3. **Abierto** = todas sus líneas `pendiente`. **En curso** = alguna `pendiente`, `en_camino` o `parcial` y no está
   abierto. **Terminado** = todas `recibido` o `cancelado`. «Vivo» = abierto o en curso.
4. **Resumen** de la cabecera: número de líneas por estado, en el orden pendiente, en camino, parcial, recibido,
   cancelado (solo los estados con alguna línea).
5. **Total** = suma de `totalFila` (la columna EUR de hoy) de las líneas **no canceladas**.
6. **⚠** en la cabecera si alguna línea cumple `llevaAviso` (urgente y en camino o parcial).
7. **Orden**: bloques por `FECHA` descendente y, a igualdad, por número descendente; dentro, líneas por
   `FECHA_PEDIDO` ascendente y, a igualdad, por `ID_COMPRA` ascendente.
8. **Fecha del bloque** = cuando se creó. Las líneas conservan su propia `FECHA_PEDIDO`.

## 4. Base de datos

`gestion-reparaciones-servidor/sql/migracion-bloques-compra.sql` y el mismo esquema en `crear_bd.sql` (tabla nueva antes
de `Compra_componente`, columna dentro de su `CREATE TABLE` y su `DROP` en la lista).

```sql
CREATE TABLE Bloque_compra (
    ID_BLOQUE    INT          NOT NULL AUTO_INCREMENT,
    ID_PROV      INT          NOT NULL,
    FECHA        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         VARCHAR(200),
    RECONSTRUIDO BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_BLOQUE),
    CONSTRAINT fk_bloque_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

ALTER TABLE Compra_componente
    ADD COLUMN ID_BLOQUE INT NULL AFTER ID_PROV,
    ADD CONSTRAINT fk_compra_bloque FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    ADD INDEX idx_compra_bloque (ID_BLOQUE);
```

- `ID_BLOQUE` admite vacío **solo** para poder volver atrás (el servidor 0.9.8 no lo rellena). El servidor 0.9.9
  siempre lo rellena.
- Borrar un proveedor sigue protegido por `tienePedidos`: un bloque nunca está vacío, así que un proveedor con bloques
  siempre tiene compras.

**Reconstrucción** (en el mismo fichero, después del esquema; **se puede repetir**):

1. Un bloque `RECONSTRUIDO` por cada pareja (proveedor, día de `FECHA_PEDIDO`) que tenga líneas sin bloque y que aún
   no tenga bloque reconstruido; su `FECHA` es la `FECHA_PEDIDO` más antigua del grupo.
2. Cada línea sin bloque pasa al bloque reconstruido de su proveedor y día, **dejando `UPDATED_AT` como estaba**
   (`SET ..., UPDATED_AT = UPDATED_AT`): si no, `ON UPDATE CURRENT_TIMESTAMP` cambiaría la fecha de modificación de
   todas las líneas.
3. Comprobación: ninguna línea sin bloque y ningún bloque sin líneas; recuento de bloques reconstruidos.

El «día» es el de la zona de la base de datos; solo afectaría a pedidos hechos de madrugada.

Consulta para ver de antemano cómo agrupará (solo lectura):

```sql
SELECT p.NOMBRE, DATE(cc.FECHA_PEDIDO) AS dia, COUNT(*) AS lineas, GROUP_CONCAT(DISTINCT cc.ESTADO) AS estados
FROM Compra_componente cc JOIN Proveedor p ON p.ID_PROV = cc.ID_PROV
GROUP BY cc.ID_PROV, DATE(cc.FECHA_PEDIDO)
ORDER BY dia DESC, p.NOMBRE;
```

## 5. Servidor

### 5.1 Lectura

`GET /api/compras` (la consulta que la web sondea) sigue siendo la única. Cada línea añade los datos de su bloque:

| Campo | Tipo | Contenido |
|---|---|---|
| `idBloque` | entero, nulable | Nulo solo en el hueco de un despliegue o tras volver atrás |
| `fechaBloque` | fecha y hora, nulable | `Bloque_compra.FECHA` |
| `notaBloque` | texto, nulable | `NOTA` |
| `bloqueUpdatedAt` | fecha y hora, nulable | `Bloque_compra.UPDATED_AT` (control de cambios de la nota) |
| `bloqueAntiguo` | booleano | `RECONSTRUIDO` |

### 5.2 Escrituras que ya existen

Sin los campos nuevos funcionan como hoy (la web 0.9.8 sigue funcionando contra el servidor 0.9.9).

- **`POST /api/compras/lote`**: campo opcional `destinos: [{ idProv, idBloque }]`, uno por proveedor de las líneas;
  `idBloque` nulo = bloque nuevo. Proveedor sin entrada en `destinos` → **regla por defecto**: su bloque abierto más
  reciente (§3.3, orden §3.7) o, si no tiene, uno nuevo. Validaciones: un destino de otro proveedor → 422 «El bloque
  {n} es de otro proveedor.»; un destino que ya no existe → 409 «El bloque {n} ya no existe; elige otro destino.».
  Un destino que dejó de estar abierto se acepta (añadir líneas pendientes siempre es seguro; es lo que hace «Añadir
  líneas…»). Crear los bloques nuevos va en la misma transacción que las líneas. La clave de idempotencia cubre el
  cuerpo entero, `destinos` incluido.
- **`POST /api/compras`** (alta de una línea, sin uso desde la web pero no retirada): aplica la regla por defecto.
- **`PUT /api/compras/{id}` (Editar)**: campo opcional `idBloque` que solo cuenta si cambia el proveedor (nulo = bloque
  nuevo). Si cambia el proveedor y no llega, regla por defecto: línea pendiente → bloque abierto más reciente del
  proveedor nuevo o uno nuevo; en otro estado → bloque nuevo. Mismas validaciones de destino. Si el bloque de origen
  queda vacío, se borra. La comprobación de `updatedAt` de la línea sigue igual.
- **`DELETE /api/compras/{id}`** (borrar pendiente): si el bloque queda vacío, se borra.

### 5.3 Escrituras nuevas

Todas `hasRole('SUPERTECNICO')`, cada una en **una transacción**. Las que actúan sobre líneas reciben la lista exacta
que vio la web, `lineas: [{ idCompra, updatedAt }]`, y la comprueban entera **antes** de cambiar nada.

| Ruta | Cuerpo | Hace |
|---|---|---|
| `PATCH /api/compras/{id}/bloque` | `{ idBloque, updatedAt }` | Mueve la línea (nulo = bloque nuevo) |
| `POST /api/bloques-compra/{id}/confirmar` | `{ lineas }` | Esas líneas: `pendiente` → `en_camino` |
| `POST /api/bloques-compra/{id}/recibir` | `{ lineas }` | Esas líneas: `en_camino` → `recibido`, sumando stock con el código de «Confirmar recibido» |
| `POST /api/bloques-compra/{id}/cancelar` | `{ lineas }` | Esas líneas: `en_camino` → `cancelado` |
| `POST /api/bloques-compra/{id}/borrar` | `{ lineas }` | Borra el bloque y todas sus líneas (todas pendientes) |
| `POST /api/bloques-compra/{id}/separar` | `{ lineas }` | Crea un bloque nuevo del mismo proveedor y mueve allí esas líneas; responde `{ idBloque }` |
| `POST /api/bloques-compra/{id}/juntar` | `{ destino, lineas }` | Mueve todas las líneas al bloque `destino`, añade la nota y borra el bloque |
| `PATCH /api/bloques-compra/{id}/nota` | `{ nota, updatedAt }` | Cambia la nota (vacía = sin nota) |

Respuestas de error (textos para la web):

| Caso | Código y texto |
|---|---|
| Alguna línea de la lista no existe, no es del bloque, cambió su `updatedAt` o ya no está en el estado de la acción; el bloque no existe | 409 «Este bloque fue modificado por otro usuario.» |
| `borrar` o `juntar` con una lista que no es el bloque entero (alguien añadió o movió líneas) | 409, mismo texto |
| `borrar` con alguna línea no pendiente | 409, mismo texto |
| Lista vacía | 422 «No hay líneas a las que aplicar la acción.» |
| `separar` con todas las líneas del bloque | 422 «Deja al menos una línea en el bloque.» |
| Mover o juntar hacia un bloque de otro proveedor | 422 «El bloque {n} es de otro proveedor.» |
| Mover al bloque en el que ya está; juntar un bloque consigo mismo | 422 «La línea ya está en ese bloque.» / «Elige otro bloque.» |
| Mover a «bloque nuevo» una línea que ya está sola | 422 «La línea ya está sola en su bloque.» |
| Nota de más de 200 caracteres | 422 «La nota no puede superar los 200 caracteres.» |
| Nota con `updatedAt` del bloque distinto | 409 «Este bloque fue modificado por otro usuario.» |
| Línea de `PATCH …/bloque` inexistente | 404 |

- Un reintento de una acción ya hecha recibe 409 (las líneas ya no están en el estado o su `updatedAt` cambió), igual
  que las transiciones por línea de hoy. Solo el lote lleva clave de idempotencia.
- «Juntar»: la nota del bloque absorbido se añade a la del destino con « · », recortada a 200.
- Mover, separar y juntar cambian `UPDATED_AT` de cada línea movida (es un cambio real).

### 5.4 Log de actividad

Tras la transacción, como los logs de hoy:

| Acción | Detalle |
|---|---|
| `CREAR_BLOQUE` | `BLOQUE: n, PROVEEDOR: nombre` |
| `MOVER_LINEA_BLOQUE` | `ID_COMPRA: n, ESTADO: e, DE: bloque A: bloque` |
| `SEPARAR_BLOQUE` / `JUNTAR_BLOQUE` | Bloques de origen y destino y los `ID_COMPRA` movidos |
| `CONFIRMAR_BLOQUE` / `RECIBIR_BLOQUE` / `CANCELAR_BLOQUE` / `BORRAR_BLOQUE` | `BLOQUE: n` y los `ID_COMPRA` afectados |
| `EDITAR_NOTA_BLOQUE` | `BLOQUE: n` |

Además, cada línea que cambia de estado apunta su log de hoy (`CONFIRMAR_PEDIDO`, `RECIBIR_PEDIDO` con componente y
cantidad, `CANCELAR_PEDIDO`, `BORRAR_PEDIDO`), para rastrear cada movimiento de stock línea a línea. Un bloque que se
borra por quedarse vacío apunta `BORRAR_BLOQUE` con «(se quedó sin líneas)».

### 5.5 Documentación del servidor

`docs/autorizacion_endpoints.md` y `docs/api_contract.md` con las rutas nuevas; el `openapi.json` se regenera.

## 6. Web

### 6.1 `DataTable`: agrupación opcional

Prop nueva `grupos` (opcional). Sin ella, la tabla no cambia: las 18 pantallas restantes no la pasan y sus tests
siguen pasando sin tocarlos. Con ella:

- La página pasa las filas **ya ordenadas** por grupo y una función que da la clave de grupo de cada fila; la tabla
  intercala una **fila cabecera** a todo el ancho antes de cada grupo, pintada por la página, con su propio menú
  contextual (opcional).
- Saber si un grupo está desplegado y alternarlo lo decide la página; un grupo plegado pinta solo su cabecera.
- **Flechas**: saltan las cabeceras y las filas plegadas. Clic en una cabecera no selecciona ninguna fila.
- **Pintado parcial** (más de `umbralVirtual` filas): cuenta cabeceras y filas.
- La selección y `pedirDesplazamiento` siguen funcionando por id de fila; si la fila elegida está en un grupo plegado,
  la página despliega antes el grupo.

### 6.2 Pestaña Pedidos (Componentes)

- **Interruptor «Agrupar por bloque»** en la barra, tras «Nuevo pedido». Se guarda en el navegador (`localStorage`,
  leído con `try/catch`: si no se puede leer, encendido); por defecto, encendido.
- Con la vista agrupada, botón **«Plegar todo» / «Desplegar todo»** (uno solo: pliega si hay algún bloque visible
  desplegado; si no, despliega).
- **Plegado**: por defecto, desplegados los bloques vivos y plegados los terminados. Lo que se pliega o despliega a
  mano se guarda en memoria durante la sesión. Con algún filtro activo, todos los bloques con coincidencias salen
  desplegados y lo manual se guarda aparte, y se olvida al cambiar los filtros.
- **Filtros** (Estado, Proveedor, Buscar, Fechas): se aplican a las líneas; un bloque sin líneas que cumplan no sale.
  Si se ocultan líneas, la cabecera lleva «mostrando k de n». Resumen, total y ⚠ son del bloque entero.
- **Columnas**: vista agrupada sin «Proveedor» (está en la cabecera); vista plana igual que hoy con una columna
  **«Bloque»** (número en gris) tras «ID».
- **Llegada desde «En camino» de Stock**: además de lo de hoy, despliega el bloque de la línea seleccionada.
- Administrador y técnico ven lo mismo sin menús ni botón ⋮.
- Líneas con `idBloque` nulo (solo en el hueco de un despliegue): agrupadas bajo una cabecera «Sin bloque» sin menú.
- La pestaña «Otros» no cambia.

### 6.3 Cabecera del bloque

Una línea, 40 px, fondo `fila-maestro-bg`, en este orden: ▼/▶ · **Bloque n** · proveedor · fecha `dd/MM/yyyy` ·
«n líneas» · **total €** · ⚠ · etiqueta «antiguo» (si `bloqueAntiguo`, con ayuda «Reconstruido al migrar: mismo
proveedor y mismo día») · chips del resumen (colores de `BadgeEstadoPedido`) · «mostrando k de n» · nota en cursiva
recortada con el texto completo al pasar el ratón · a la derecha, ⋮ (solo SUPERTECNICO). Clic en la flecha o en el
título pliega y despliega; clic derecho o ⋮ abre el menú del bloque.

### 6.4 Menús

**Línea** (clic derecho, solo SUPERTECNICO): las entradas de hoy por estado y, al final, separador y **«Mover a
bloque ▸»**. Las canceladas, que hoy no tienen menú, pasan a tener solo esta entrada. El submenú lista hasta 8 bloques
del mismo proveedor (sin el propio), los más recientes primero, como «Bloque n» con «dd/MM · k líneas · abierto / en
curso / terminado» y, tras un separador, **«Bloque nuevo»** (deshabilitada con «ya está sola en su bloque» si la línea
es la única de su bloque). Pendiente → se mueve sin preguntar. Otro estado → confirmación «Mover línea ya pedida»:
«La línea {componente} (ID n) está {estado}. ¿Moverla a {el Bloque n / un bloque nuevo} igualmente?» y la nota «El
stock no cambia. El movimiento queda apuntado en el registro de actividad.»; botón «Mover».

**Bloque** (⋮ o clic derecho en la cabecera, solo SUPERTECNICO):

| Texto de la entrada | n | Deshabilitada cuando (texto a la derecha) |
|---|---|---|
| «Confirmar bloque (n)» | líneas pendientes | n = 0 («nada pendiente») |
| «Recibir todo (n)» | líneas en camino | n = 0 («nada en camino») |
| — | | |
| «Añadir líneas…» | | nunca |
| «Separar líneas…» | | una sola línea («solo tiene una línea») |
| «Juntar con…» | | sin otro bloque del proveedor («no hay otro bloque de este proveedor») |
| «Añadir nota…» (sin nota) / «Editar nota…» | | nunca |
| — | | |
| «Cancelar bloque (n)», en rojo | líneas en camino | n = 0 («nada en camino») |
| «Borrar bloque», en rojo | | alguna línea no pendiente («hay líneas ya pedidas») |

### 6.5 Diálogos nuevos

- **Acción de bloque** (Confirmar, Recibir, Cancelar, Borrar): título «{Acción} · Bloque n», debajo proveedor y fecha.
  Lista «Pasan a en camino (n)» / «Se reciben (suman al stock) (n)» / «Se cancelan (no se pueden reactivar) (n)» /
  «Se borran el bloque y todas sus líneas (n)», con ID, componente, cantidad y estado. Si hay líneas que no cambian,
  segunda lista «No cambian (m)» con el motivo de cada una:

  | Acción | Pendiente | En camino | Parcial | Recibido | Cancelado |
  |---|---|---|---|---|---|
  | Confirmar | cambia | «ya en camino» | «ya parcial» | «ya recibido» | «cancelado» |
  | Recibir | «pendiente: aún no se ha pedido» | cambia | «parcial (r/c): usa «Recibir resto» en su fila» | «ya recibido» | «cancelado» |
  | Cancelar | «pendiente: se borra, no se cancela» | cambia | «parcial: usa «Cerrar sin resto» en su fila» | «ya recibido» | «ya cancelado» |

  Botones «Cancelar» y «Confirmar (n)» / «Recibir (n)» / «Cancelar líneas (n)» (rojo) / «Borrar bloque» (rojo). Un 409
  cierra el diálogo y avisa como «Advertencia»: «Este bloque fue modificado por otro usuario. Los datos se han
  recargado.».
- **Separar líneas · Bloque n**: «Las marcadas pasan a un bloque nuevo de {proveedor} con fecha de hoy.»; casillas con
  ID, componente, cantidad y estado. Botón «Separar (k)» deshabilitado con 0 marcadas o con todas («Deja al menos una
  línea en el Bloque n.»). Si alguna marcada no está pendiente, aviso dentro del diálogo («k líneas de las marcadas ya
  se pidieron: el movimiento quedará apuntado en el registro.») y el botón pasa a «Separar igualmente»; no hay
  segunda confirmación.
- **Juntar Bloque n con…**: «Sus k líneas pasan al bloque que elijas y el Bloque n desaparece.» (y «Su nota se añade a
  la del destino.» si tiene nota). Opciones con todos los bloques del proveedor salvo él, del más reciente al más
  antiguo: «Bloque m · dd/MM/yyyy · k líneas · total € · situación». Aviso dentro del diálogo si tiene líneas ya
  pedidas. Botón «Juntar», deshabilitado hasta elegir.
- **Nota · Bloque n**: «Nº de pedido o factura del proveedor, seguimiento, forma de pago… Sale en la cabecera y en el
  CSV.»; área de texto de 200 caracteres con contador «k/200». Botón «Guardar».

### 6.6 «Nuevo pedido» y «Editar»

- **Resumen «Bloques»** al pie de «Nuevo pedido», encima de los botones, actualizado al cambiar los proveedores de las
  líneas. Por cada proveedor presente: «{proveedor} (k líneas) →» y, si tiene bloques abiertos, un desplegable con
  «Añadir a Bloque n · dd/MM · k líneas» por cada abierto (el más reciente elegido por defecto) y «Bloque nuevo»; si no
  tiene ninguno, «Bloque nuevo (no tiene ninguno abierto)». Las líneas sin proveedor: «k líneas sin proveedor
  todavía». Debajo: «Solo se ofrecen bloques abiertos (sin nada confirmado). Para sumar a uno ya confirmado: ⋮ del
  bloque → «Añadir líneas…».». Lo elegido viaja como `destinos`. Un 409 de destino se enseña como error del modal,
  que sigue abierto, y se recargan los pedidos para que el resumen ofrezca los bloques que existen ahora (con el modal
  abierto el sondeo está congelado).
- **Modo «Añadir líneas»** (precarga nueva `{ modo: 'bloque', idBloque, idProv }` de `formularioPedido`): título
  «Añadir líneas · Bloque n», debajo «{proveedor} · {situación} · las líneas nuevas entran como pendientes y el bloque
  mostrará «Confirmar bloque (n)».». Proveedor general fijo (sin desplegable ni «Aplicar a todas»), proveedor de cada
  línea fijo, «Añadir previsión» disponible. El resumen dice «{proveedor} (k líneas) → Bloque n (fijado)».
- **«Editar»**: al elegir otro proveedor aparece el campo **«Bloque»** (hasta 8 bloques recientes del proveedor nuevo
  y «Bloque nuevo»; sugerido: si la línea está pendiente, el abierto más reciente; si no, «Bloque nuevo») y la nota «Al
  cambiar el proveedor, la línea sale del Bloque n y pasa a un bloque de {proveedor}.», más «Ojo: {proveedor} trabaja
  en {divisa}; revisa el precio.» si cambia la divisa. Al guardar una línea no pendiente con cambio de proveedor,
  confirmación «Mover línea ya pedida» (botón «Guardar y mover»); «Cancelar» vuelve al diálogo de editar.

### 6.7 CSV

Siempre plano, en el orden de la vista y con los filtros aplicados. Columnas nuevas **«Bloque»** y **«Nota del
bloque»** justo después de «ID».

### 6.8 Sondeo, contrato y documentación

- Cualquier menú o diálogo de bloque abierto congela el sondeo, como los de línea (`marcar`).
- Contrato: copiado del `openapi.json` del servidor 0.9.9, como indica el README.
- `docs/paridad/pedidos.md` actualizado (diferencias nuevas respecto al JavaFX: bloques).

## 7. Despliegue

1. **Preproducción, en horario:** refrescar la base desde producción; lanzar antes la consulta de §4 para ver los
   grupos; migración con su comprobación; servidor; web; probar todo el flujo con las cuentas de prueba (las de
   `~/.env.e2e`).
2. **Producción:** según la norma de despliegue en horario (migración que solo añade, web 0.9.8 compatible con el
   servidor 0.9.9, vuelta atrás simple); se confirma ese día según cómo haya ido en preproducción. Copia a mano antes.
3. **Orden en las dos máquinas:** migración → servidor → web → **repetir la reconstrucción** (recoge las líneas creadas
   por el servidor 0.9.8 en el hueco entre la migración y el servidor nuevo).
4. **Vuelta atrás:** servidor y web 0.9.8 funcionan sobre el esquema nuevo. Al volver a desplegar la 0.9.9, repetir la
   reconstrucción.
5. Las copias nocturnas cuentan las tablas solas (pasan de 23 a 24): no hay que tocar el script.
6. Cierre: tags `v0.9.9` en servidor y web, gitlinks y `NOVEDADES-v0.9.9` en la raíz, registro en
   `Apuntes/despliegue_preprod.md` y `Apuntes/despliegue_vdc_produccion.md`, backlog en `Apuntes/plan-futuro.md`.

## 8. Pruebas

**Servidor**
- Lote: sin `destinos` suma al abierto más reciente o crea uno; con `destinos` respeta el elegido y el nuevo; destino
  de otro proveedor 422; destino inexistente 409; destino ya no abierto aceptado; bloques nuevos en la misma
  transacción (si falla una línea, no queda ningún bloque).
- Editar: cambio de proveedor con y sin `idBloque`, regla por defecto por estado, bloque origen vacío borrado.
- Mover: pendiente y no pendiente, otro proveedor 422, sola a «nuevo» 422, `updatedAt` distinto 409, origen vacío
  borrado.
- Acciones de bloque: solo las líneas de la lista; línea ajena, cambiada o en otro estado → 409 sin cambios; recibir
  suma stock (también en el master de un grupo compartido, como «Confirmar recibido»); borrar solo con todas
  pendientes y con la lista completa.
- Separar y juntar: casos de 422 y 409, nota añadida y recortada, bloque absorbido borrado.
- Nota: 200 caracteres, 422 por encima, 409 por `updatedAt`.
- Roles: TECNICO y ADMIN → 403 en todas las escrituras nuevas.
- Lectura: los campos nuevos en `GET /api/compras`.
- Migración: script probado contra una base de prueba (reconstrucción repetible, `UPDATED_AT` intacto).

**Web**
- `reglasBloque.ts` (funciones puras): situación, resumen, total, ⚠, qué cambia en cada acción y motivos, destinos de
  mover, destino por defecto.
- `DataTable` con `grupos`: cabeceras, plegado, flechas que saltan cabeceras y plegadas, pintado parcial con
  cabeceras; y la tabla sin `grupos` idéntica (los tests de hoy sin tocar).
- Diálogos: acción de bloque (listas, botones, 409), separar (límites y aviso), juntar, nota, mover con y sin
  confirmación, «Nuevo pedido» con el resumen de bloques y `destinos`, modo «Añadir líneas», «Editar» con campo Bloque
  y confirmación.
- Página: agrupada y plana, interruptor recordado, plegado por defecto y manual, filtros con «mostrando k de n»,
  llegada desde Stock, roles sin menús, CSV con las columnas nuevas.
- Smoke `pedidos.spec` en preproducción.

## 9. Fuera de alcance (al backlog de `plan-futuro`)

- **Auto con bloques**: proveedor habitual por pieza (sacado de los bloques) y «Ya que pides a X…» (política
  *can-order* del *joint replenishment problem*: al pedir a X, sugerir piezas de X que se agotarán pronto). Versión
  aparte, tras unas semanas de uso.
- **Plazo real por proveedor** (fecha del bloque → llegadas), ya en el backlog de la previsión: con los bloques pasa a
  ser directo.
- Gastos de envío por bloque.
- Bloques en los pedidos «Otros».
- Arrastrar filas entre bloques (con alternativa sin arrastrar, WCAG 2.2 §2.5.7).
- Selección múltiple en la tabla.
