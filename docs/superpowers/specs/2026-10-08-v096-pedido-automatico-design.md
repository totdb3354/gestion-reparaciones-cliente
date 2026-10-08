# 0.9.6 — Pedir para 60 días y pedido automático desde la previsión

Fecha: 2026-10-08. Versión del producto: **0.9.6** (servidor y web etiquetados juntos).
Antecedentes: spec de la 0.9.5 (`2026-10-07-v095-prevision-pedidos-design.md`, §3: previsión de pedidos).

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de las máquinas y
el registro de los despliegues viven fuera de git, en `Apuntes/`.

## 1. Objetivo

Pulir la previsión de pedidos de la 0.9.5. Dos piezas, una sola versión y un solo despliegue:

1. **Una sola columna «Pedir 60 d»** en lugar de «Pedir 15 d» y «Pedir 30 d». **Ya hecho** y en `main` de servidor
   y web (sin tag); se documenta aquí para que la versión tenga una sola spec.
2. **Pedido automático:** en Stock se marcan las piezas que entran en el pedido automático; en «Nuevo pedido» un botón
   añade las marcadas con la cantidad de la previsión, de modo que solo queda elegir el proveedor (el precio es
   opcional y se puede poner después con «Editar pedido»).

Fuera de esta versión (plan maestro): que el ADMIN también haga pedidos. Hoy «Nuevo pedido» es solo del
SUPERTECNICO; cuando se amplíe, el botón de esta spec le funcionará igual sin cambios.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Horizonte del pedido | **Una columna, 60 días**, fijo en el código (`DIAS_PEDIDO`) | Petición del usuario; un parámetro editable no hace falta todavía |
| Control en Stock | **Casilla** (`Checkbox` existente) en la columna «Auto» | Ya existe en `shared/ui`; sin dependencias nuevas |
| Dónde se guarda la marca | Columna nueva `Componente.AUTO_PEDIDO` (booleana, `FALSE` por defecto) | Una sola lista compartida por supertécnicos y admin, en cualquier PC |
| Grupos compartidos | La marca vive en el **master**, como el stock mínimo | En Stock el grupo es una fila y el pedido siempre va al master |
| Quién ve y cambia la marca | **SUPERTECNICO y ADMIN**. Al TECNICO el servidor no le envía el dato | Es información de compras, como la previsión |
| Cantidad que pone el botón | **«Pedir 60 d»**; las marcadas con 0 **no se añaden** y se avisa cuántas son | Una línea con cantidad 0 no se puede confirmar; si no hace falta pedir, no se pide |
| Líneas que ya estaban | No se duplican. Si la pieza está marcada: cantidad = **máx(la de la línea, la previsión)** | Nunca baja lo que alguien puso a mano (p. ej. una solicitud urgente) |
| Proveedor | Un **proveedor general** en el modal que **solo rellena** las líneas sin proveedor | No pisa lo elegido a mano |
| Sobrescribir proveedor | Botón explícito **«Aplicar a todas»** | Para corregir de golpe un general mal elegido |
| Deseleccionar proveedor | **No** | El proveedor es obligatorio para confirmar; se cambia por otro |
| `UPDATED_AT` al marcar | **No cambia** | «Editar stock» lo usa para detectar ediciones simultáneas; marcar no es editar el stock |

## 3. Pedir 60 d (hecho)

La regla de la 0.9.5 §3.1 con un único horizonte de 60 días:
`Pedir 60 d = máx(0, ⌈ máx(mínimo; consumo/día · 60) − (stock + en camino) ⌉)`, en aritmética entera.

- Servidor: `PrevisionPedido.Resultado(consumoDiario, pedir60)`; campo `pedir60` (nulo para TECNICO y desactivadas)
  en lugar de `pedir15` y `pedir30`.
- Web: columna «Pedir 60 d» tras «Consumo/día» y en el CSV.

## 4. Pedido automático

### 4.1 Base de datos

Migración `sql/migracion-auto-pedido.sql` (aditiva; el servidor 0.9.5 sigue funcionando sobre el esquema nuevo):

```sql
ALTER TABLE Componente ADD COLUMN IF NOT EXISTS AUTO_PEDIDO BOOLEAN NOT NULL DEFAULT FALSE;
```

Idempotente (`IF NOT EXISTS` de MariaDB). La misma columna se añade a la definición de `Componente` en `crear_bd.sql`.
Ninguna pieza queda marcada tras la migración.

### 4.2 Servidor

- **Listado de Stock** (`GET /api/componentes/gestionados`): campo nuevo `autoPedido` (`Boolean`, nulable). Se rellena
  con el valor del **master** (`COALESCE(master.AUTO_PEDIDO, c.AUTO_PEDIDO)`, como `STOCK_MINIMO`) solo para
  SUPERTECNICO y ADMIN, en el mismo sitio donde se rellena la previsión. Para TECNICO llega `null`. Para las piezas
  desactivadas llega su valor (la web no deja cambiarlo, ver §4.3).
- **Endpoint nuevo** `PATCH /api/componentes/{idCom}/auto-pedido`, cuerpo `{ "autoPedido": true|false }`,
  `hasAnyRole('SUPERTECNICO', 'ADMIN')`:
  - Resuelve el master: si `idCom` es un slave, guarda en su master.
  - `UPDATE Componente SET AUTO_PEDIDO = ?, UPDATED_AT = UPDATED_AT WHERE ID_COM = ?` (no toca `UPDATED_AT`).
  - `idCom` inexistente → 404.
  - Registro de actividad: acción `EDITAR_COMPONENTE`, detalle `ID_COM: <master>, AUTO_PEDIDO: SI|NO`.
- Contrato OpenAPI: `autoPedido` nulable en `Componente`; esquema de la petición del PATCH.

### 4.3 Web — Stock

- Columna **«Auto»** con una casilla (el `Checkbox` de `shared/ui`; no se añade un componente nuevo), justo después de «Pedir 60 d». Solo para SUPERTECNICO y ADMIN (las
  mismas condiciones que las columnas de previsión).
- Cada clic guarda al momento (`PATCH …/auto-pedido`). Mientras se guarda, la casilla muestra el valor nuevo; si
  falla, vuelve al anterior y sale el error por el camino habitual. Al terminar se refresca el listado.
- En una fila desactivada la casilla se ve deshabilitada.
- El clic en la casilla no selecciona la fila ni abre el menú.
- CSV de Stock con previsión: columna «Auto» con `Sí` / `No` (tras «Pedir 60 d»).

### 4.4 Web — «Nuevo pedido»

Barra encima de las líneas:

```
Proveedor: [ ▼ ]   [Aplicar a todas]   [Añadir previsión (N)]
```

- **Proveedor:** combo con los proveedores activos (los mismos del combo de cada línea). Empieza vacío.
- **N** = número de piezas activas, marcadas y con `pedir60 > 0`, contadas una vez por grupo (master).
- **«Añadir previsión (N)»**, deshabilitado sin proveedor general o con N = 0. Al pulsarlo, para cada pieza marcada:

  | Situación | Resultado |
  |---|---|
  | Sin línea y `pedir60 > 0` | Línea nueva al final: cantidad = `pedir60`, proveedor general, precio 0,00, sin urgente |
  | Sin línea y `pedir60 = 0` | No se añade; cuenta para el aviso |
  | Con línea | Cantidad = máx(cantidad de la línea, `pedir60`), y nunca menor que 1; si la cantidad de la línea no es un número válido, se pone `pedir60` (o 1 si es 0). Proveedor: el general **solo si la línea no tenía** |

  Las líneas de piezas no marcadas no se tocan. Una línea "con pieza" es la que tiene ese `idCom` (el del master,
  que es el que ponen todas las precargas). Pulsar dos veces seguidas deja el formulario igual.
- **Aviso** tras pulsar, en la línea de información del formulario: «N pieza(s) marcada(s) no necesitan pedido.»
  (solo si hay alguna). Convive con el aviso de solicitudes omitidas.
- **«Aplicar a todas»**, deshabilitado sin proveedor general o sin líneas: pone el proveedor general en **todas** las
  líneas, también las que ya tenían uno.
- La regla del botón es una **función pura** en `formulario/lineas.ts` (entra: líneas, componentes activos, proveedor;
  sale: líneas nuevas y el número de omitidas), probada aparte del diálogo.
- «Nuevo otro pedido» no cambia.

### 4.5 Errores y casos límite

- Listado de componentes sin cargar o con error: el botón queda deshabilitado (N = 0).
- Una pieza marcada que se desactiva: no cuenta (solo activas). Si se reactiva, conserva la marca.
- Un componente borrado: su marca desaparece con él.
- Proveedor general desactivado mientras el modal está abierto: el combo solo ofrece activos al abrir; el servidor
  valida el proveedor al confirmar, como hoy.

## 5. Pruebas

- **Servidor:** DAO (marca en el master, `UPDATED_AT` intacto, 404); controller (permisos: TECNICO 403, SUPERTECNICO y
  ADMIN 200; registro de actividad); listado (`autoPedido` del master para SUPER/ADMIN, `null` para TECNICO);
  `OpenApiContractTest` con `autoPedido` nulable.
- **Web:** función pura del botón (añadir, saltar ceros, subir cantidad sin bajarla, no duplicar, proveedor solo en
  vacías, idempotente, grupos); columna «Auto» (visible por rol, deshabilitada en desactivadas, guarda y revierte si
  falla, no selecciona la fila); CSV; diálogo (botones deshabilitados sin proveedor, aviso de omitidas, «Aplicar a
  todas»).

## 6. Despliegue

Orden (preprod primero, refrescada desde producción, y después producción fuera de horario):

1. Copia de la base.
2. `migracion-auto-pedido.sql`. Verificación: `SELECT COUNT(*) FROM Componente WHERE AUTO_PEDIDO;` → 0.
3. P8 (`pull` de servidor y web, `docker compose up -d --build`).
4. Comprobar `/version.json` = 0.9.6, la columna «Pedir 60 d», la casilla «Auto» y el botón en «Nuevo pedido».

La web 0.9.6 necesita el servidor 0.9.6 (`pedir60`, `autoPedido`, el PATCH). Sin cambios en nginx.
