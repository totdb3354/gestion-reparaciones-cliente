# 0.9.9 (ajuste) — Un proveedor por pedido y solo se mueve lo vivo

Fecha: 2026-10-12. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va **dentro de la 0.9.9**, que está en `main` (servidor `0fc5169`, web `ca421eb`) y validada en preproducción, sin
desplegar en producción ni etiquetar. Ajusta la spec de bloques `2026-10-09-v099-bloques-pedidos-design.md`: §2 filas
«Nacimiento» y «Mover líneas», §5.2-§5.3, §6.4 y §6.6. Sin cambios en la base de datos.

## 0. Nota sobre este documento

Repo público: sin datos reales, IPs ni nombres.

## 1. Objetivo

Con los bloques, un pedido es un pedido a **un** proveedor. Hoy, en cambio:
- **«Nuevo pedido»** deja mezclar proveedores: hay un combo de proveedor en cada línea y un botón «Aplicar a todas», y
  al guardar se reparte en un bloque por proveedor. La previsión ya es por proveedor, así que la mezcla sobra.
- **«Mover a bloque»** deja mover cualquier línea, también las recibidas y las canceladas, a cualquiera de los 8 bloques
  de número más alto del proveedor, incluidos los terminados. Con un proveedor con muchos bloques, la lista es larga y
  casi siempre sin sentido.
- **«Juntar con…»** enseña todos los bloques del proveedor.

Referencia: en Odoo, ERPNext y Dolibarr cada pedido de compra lleva **un único proveedor**, en la cabecera. En Odoo
solo se juntan pedidos en borrador, y lo recibido no se toca.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Proveedor de «Nuevo pedido» | **Uno**, arriba, **obligatorio**; vale para todas las líneas | Un bloque = un proveedor; como Odoo |
| Combo por línea y «Aplicar a todas» | **Fuera** | Ya no hay nada que repartir |
| Campana («Pedir todas las piezas», solicitudes) | Las piezas entran sin proveedor; **al elegirlo, van todas a él**; las que no, se quitan y van en otro pedido | Sencillo; repartir solo queda para otra versión |
| Qué líneas se mueven | Solo **pendientes, en camino o parciales** | Lo recibido y lo cancelado es historial |
| Bloque terminado (todo recibido o cancelado) | **Cerrado**: no entra ni sale ninguna línea (mover, juntar, separar, añadir líneas o previsión) | Igual que un pedido hecho en Odoo; listas cortas |
| Destinos de «Mover», «Juntar» y del «Bloque» de «Editar» | Bloques **vivos** (abiertos o en curso) **del mismo proveedor**, sin tope, y «Bloque nuevo» donde ya estaba | Son pocos; un bloque nunca mezcla proveedores |
| Cambiar el proveedor de una línea | Sigue en «Editar»: la lleva a un bloque del proveedor nuevo | Es la corrección de un error, no un movimiento |
| Dónde se comprueba | **En la web y en el servidor** | Que la API no deje saltárselo |
| «Otros pedidos» | **Sin cambios** (no van por bloques) | — |

## 3. Web

### 3.1 «Nuevo pedido» (`NuevoPedidoDialog`)

- **«Proveedor:»** arriba: el combo de hoy, con su nombre accesible «Proveedor general».
  - Elegirlo, o cambiarlo, pone ese proveedor en **todas** las líneas (`aplicarProveedorATodas`, que ya existe).
  - Las líneas que se añaden después lo llevan, como hoy.
- **Fuera:**
  - el botón «Aplicar a todas»;
  - la función `rellenarProveedorVacio`, que solo se usaba aquí.
- **Columna «Proveedor» de las líneas:** sin desplegable. Enseña el nombre del proveedor en la píldora de «Añadir
  líneas» (prop `proveedorFijo` de `DialogoLineas`), o «—» si aún no se ha elegido.
- **Confirmar sin proveedor:** error «Elige el proveedor.» en el sitio de los errores del diálogo. Sale antes que los
  errores de cada línea, y no se manda nada.
- **Campana:**
  - `precargaInicial` deja las piezas sin proveedor, como hoy;
  - al elegir el proveedor, van todas a él;
  - las que sobran se quitan con la papelera.
- **Resumen «Bloques» (`ResumenBloques`):** el código no cambia. Con un solo proveedor saca una fila,
  «X (n líneas) → Añadir a Bloque n / Bloque nuevo». Solo cambia el texto sin líneas, de «Añade líneas: aquí verás a
  qué bloque va cada proveedor.» a «Añade líneas: aquí verás a qué bloque van.».
- **Sin cambios:**
  - «Añadir previsión de X (N)» y sus avisos;
  - el modo «Añadir líneas» de un bloque, con el proveedor fijo.

### 3.2 Reglas de los bloques (`bloques/reglas.ts`)

- **`destinosMover(p, bloques)`:** los bloques **vivos** (`esVivo`) del proveedor de la línea, sin el propio y sin el
  tope de 8, de mayor a menor número. El detalle de cada destino no cambia.
- **Nueva `sePuedeMover(p)`:** `true` si la línea está pendiente, en camino o parcial.
- **`entradasMenuBloque(b, otrosVivos)`:** el segundo parámetro pasa a ser cuántos **otros bloques vivos** tiene el
  proveedor. Con el bloque terminado (`!esVivo(b)`), quedan deshabilitadas con el motivo «el bloque está terminado»:
  - «Añadir líneas…»;
  - «Añadir previsión…»;
  - «Separar líneas…»;
  - «Juntar con…».

  «Juntar con…» también queda deshabilitada si no hay otro bloque vivo, con el motivo «no hay otro bloque vivo de
  este proveedor». Las demás entradas no cambian.

### 3.3 Pantallas

- **Menú de una línea (`PedidosPage` / `MenuPedido`):**
  - «Mover a bloque ▸» solo sale si `sePuedeMover(p)`, así que desaparece de las recibidas y las canceladas;
  - una cancelada se queda sin menú, como antes de la 0.9.9.
- **«Juntar con…» (`JuntarDialog`):** los candidatos son los otros bloques **vivos** del proveedor.
- **«Editar» con otro proveedor (`EditarPedidoDialog`):**
  - el desplegable «Bloque» ofrece los bloques **vivos** del proveedor nuevo, sin el tope de 8, y «Bloque nuevo»;
  - la sugerencia no cambia: pendiente → el abierto más reciente; si no, nuevo.

## 4. Servidor

Sin cambios de esquema ni de contrato. Solo hay dos textos 422 nuevos.

- **`BloqueCompraDAO.estaTerminado(int idBloque)`:**
  `SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ? AND ESTADO IN ('pendiente','en_camino','parcial')` = 0.
- **`DestinoBloque.resolver`:** con un bloque elegido, tras comprobar que existe (409) y que es del mismo proveedor
  (422), si `estaTerminado` → **422 «El bloque {n} ya está terminado.»**. Vale para:
  - el lote de «Nuevo pedido» y «Añadir líneas»;
  - mover;
  - editar con destino;
  - el destino de juntar.

  La regla por defecto (sin bloque elegido) no cambia.
- **`BloqueCompraService.mover`:** si la línea está `recibido` o `cancelado` → **422 «Una línea recibida o cancelada no
  se puede mover.»**, antes de mirar el destino.
- **`juntar` y `separar`:** si el bloque de **origen** está terminado → 422 «El bloque {n} ya está terminado.».
- **Sin cambios:**
  - `editarCambiandoProveedor` sin destino, es decir, la regla por defecto: pendiente → abierto más reciente o nuevo;
    ya pedida → nuevo;
  - las acciones de estado (confirmar, recibir, cancelar y borrar).
- `docs/api_contract.md`: los dos 422 nuevos, en las viñetas de mover, juntar, separar, el lote y editar.

## 5. Pruebas

**Servidor:**
- `estaTerminado` (SQL);
- `resolver` con un destino terminado → 422 y sin escribir;
- `mover` de una recibida y de una cancelada → 422;
- `juntar` y `separar` desde un bloque terminado → 422;
- la regla por defecto, sin cambios.

**Web:**
- `reglas`: `destinosMover` solo con vivos y sin tope; `sePuedeMover`; las entradas del menú de un bloque terminado y
  sin otro vivo.
- Menú de línea: sin «Mover a bloque» en recibidas y canceladas.
- `JuntarDialog`: solo vivos.
- `EditarPedidoDialog`: «Bloque» solo con vivos.
- `NuevoPedidoDialog`:
  - sin combos por línea ni «Aplicar a todas»;
  - el proveedor de arriba pone el suyo en todas las líneas;
  - cambiarlo las cambia todas;
  - confirmar sin proveedor da «Elige el proveedor.»;
  - precarga de la campana sin proveedor, que al elegirlo entra en todas.

**E2E:**
- `pedidos.spec.ts` y `bloques.spec.ts` eligen el proveedor una vez, arriba, en vez de en cada línea.
- Se comprueba que en `bloques.spec.ts` siguen valiendo:
  - mover a «Bloque nuevo» y juntar de vuelta, porque los dos bloques están vivos;
  - recibir todo y revertir.

**Preproducción:**
1. Servidor y web nuevos, sin migración.
2. Todas las E2E.
3. Limpieza de `bloques.spec.ts`.

**Producción:** el guion `prod-v099.md` cambia solo los commits.

**Documentación:**
- CHANGELOG `[0.9.9]`: proveedor único en «Nuevo pedido» y «solo se mueve lo vivo». La viñeta de la 0.9.6 sobre
  «Aplicar a todas» se queda como historia.
- `NOVEDADES-v0.9.9.md`.
- Paridad de Pedidos: la desviación del JavaFX, que tenía proveedor por línea.

## 6. Fuera de alcance

- Repartir solo las piezas de la campana entre proveedores según «Piezas que vende…», como el reaprovisionamiento de
  Odoo.
- Los CSV de Pedidos, que se revisan después con el usuario.
