# 0.9.9 (ampliación) — Piezas que vende cada proveedor y previsión por proveedor

Fecha: 2026-10-11. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va **dentro de la 0.9.9** (pedidos por bloques), que está en `main` y validada en preproducción pero sin desplegar en
producción ni etiquetar. Spec de los bloques: `2026-10-09-v099-bloques-pedidos-design.md`.

## 0. Nota sobre este documento

Repo público: sin datos reales, IPs ni nombres. Las cifras de historial citadas salen de preproducción (copia de
producción del 2026-10-09) y solo orientan.

## 1. Objetivo

Hoy «Añadir previsión (N)» (0.9.6) mete en «Nuevo pedido» todas las piezas en modo Auto que hay que pedir, como mucho
con un proveedor general para todas, y el proveedor se elige línea a línea. Con los bloques (un proveedor por bloque) el
flujo natural es al revés: **se elige el proveedor del bloque y se le propone lo que hay que pedirle**, limitado a las
piezas que ese proveedor vende. La lista de piezas de cada proveedor se mantiene a mano.

Además, la previsión debe **descontar lo pendiente**: si una batería ya está apuntada en el bloque de un proveedor (aún
sin pedir), no debe volver a proponerse para otro.

En el mismo trabajo se añade buscar un bloque por su número desde Pedidos.

Referencias: la tarifa de proveedor de Odoo (producto ↔ proveedor, el orden es la prioridad; el reaprovisionamiento
agrupa en la petición en borrador del proveedor, como nuestro «sumar al bloque abierto») y el proveedor por defecto de
ERPNext. El *can-order* del *joint replenishment problem* («Ya que pides a X…») queda fuera (§8).

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Qué vende cada proveedor | Lista **puesta a mano**: tabla `Proveedor_componente` (proveedor, pieza) | El proveedor depende de la pieza y no siempre es el mismo: en el historial, 28 de 67 piezas se pidieron a dos proveedores |
| Dónde se mantiene | En **Proveedores**: menú del proveedor → «Piezas que vende…» | El flujo empieza por el proveedor; dar de alta un proveedor con muchas piezas es rápido con «marcar las que se ven» |
| Grupos compartidos | La lista guarda el **master**, como el mínimo y el modo Auto | En Stock el grupo es una fila y el pedido va al master |
| Arranque | **Rellenada con el historial**: cada pareja proveedor–pieza con alguna línea no cancelada | Ahorra teclear; luego se corrige a mano |
| Qué entra en la previsión de X | Piezas **de la lista de X**, en **modo Auto**, activas y con «Pedir 60 d» > 0 | La marca Auto decide qué se pide por previsión; la lista, a quién |
| Lo pendiente | «Pedir 60 d» descuenta también las líneas **pendientes** (de cualquier bloque) | Si no, se pediría dos veces lo apuntado en otro bloque |
| Dónde se pide | En **«Nuevo pedido»** (el proveedor de arriba) **y** en el menú del bloque («Añadir previsión…») | Las dos entradas naturales |
| Sin proveedor elegido | «Añadir previsión» **deshabilitado** hasta elegirlo | Un bloque es de un proveedor |
| Precio de las líneas de la previsión | **0,00**, como hoy | Decisión del usuario; se pone con «Editar» |
| Combo de proveedor de cada línea | **Sin cambios** (todos los proveedores) | La lista solo sirve para la previsión; puede estar incompleta |
| Buscar un bloque | En el buscador de Pedidos: **solo cifras = número de bloque exacto** | Decisión del usuario: más directo que un filtro aparte |
| Versión | **Dentro de la 0.9.9** | Aún sin desplegar ni etiquetar; un solo CHANGELOG y unas solas novedades |

## 3. Datos

### 3.1 Tabla

`sql/migracion-catalogo-proveedores.sql` (aditiva; el servidor anterior sigue funcionando) y lo mismo en `crear_bd.sql`
(tabla tras `Proveedor` y `Componente`; su `DROP` en la lista):

```sql
CREATE TABLE Proveedor_componente (
    ID_PROV INT NOT NULL,
    ID_COM  INT NOT NULL,
    PRIMARY KEY (ID_PROV, ID_COM),
    CONSTRAINT fk_provcom_proveedor  FOREIGN KEY (ID_PROV) REFERENCES Proveedor  (ID_PROV) ON DELETE CASCADE,
    CONSTRAINT fk_provcom_componente FOREIGN KEY (ID_COM)  REFERENCES Componente (ID_COM)  ON DELETE CASCADE,
    INDEX idx_provcom_componente (ID_COM)
);
```

- Una vez por base (el `CREATE TABLE` falla si ya existe). La base pasa de 24 a **25 tablas**.
- `ON DELETE CASCADE`: borrar un proveedor (solo posible sin pedidos) o un componente se lleva su lista. No añade una
  referencia que bloquee el borrado de proveedores (`tienePedidos` sigue mirando solo pedidos y lotes).

### 3.2 Relleno inicial

`sql/rellenar-catalogo-proveedores.sql`, **repetible**:

```sql
INSERT IGNORE INTO Proveedor_componente (ID_PROV, ID_COM)
SELECT DISTINCT cc.ID_PROV, COALESCE(c.ID_COM_MASTER, cc.ID_COM)
FROM Compra_componente cc JOIN Componente c ON c.ID_COM = cc.ID_COM
WHERE cc.ESTADO <> 'cancelado';
```

Los pedidos nuevos ya van al master, pero alguno antiguo puede apuntar a un slave (backlog de la 0.9.5): por eso el
`COALESCE`. Comprobación al final: número de parejas, piezas y proveedores con lista. En la copia de preproducción salen
unas 100 parejas de 67 piezas.

## 4. Servidor

### 4.1 Previsión con lo pendiente

`Pedir 60 d = máx(0, ⌈ máx(mínimo; consumo/día · 60) − (stock + en camino + pendiente) ⌉)`.

- `ComponenteDAO.getAllGestionados`: columna nueva `PENDIENTE` =
  `SUM(CASE WHEN cc.ESTADO = 'pendiente' THEN cc.CANTIDAD ELSE 0 END)` (sobre el master, como `EN_CAMINO`).
- `PrevisionPedido.calcular(…, int enCamino, int pendiente)` y su servicio pasan `pendiente`.
- `Componente.pendiente` (`Integer`, nulable): se rellena **solo para SUPERTECNICO y ADMIN**, como la previsión; al
  TECNICO le llega `null`.

### 4.2 Proveedores de cada pieza en el listado de Stock

`Componente.proveedores` (`List<Integer>`, nulable): ids de los proveedores con la pieza (su master) en la lista.
Solo SUPERTECNICO y ADMIN; `null` para TECNICO. Se carga con una consulta aparte
(`SELECT ID_PROV, ID_COM FROM Proveedor_componente`) y se reparte por master en el servicio (sin `GROUP_CONCAT`).

### 4.3 Endpoint nuevo

`PUT /api/proveedores/{idProv}/componentes`, cuerpo `{ "idsCom": [int, …] }`, `hasRole('SUPERTECNICO')`:

- Sustituye la lista entera del proveedor en una transacción (`DELETE` de las suyas + inserciones).
- Cada `idCom` de un slave se guarda como su master; los repetidos se ignoran; lista vacía = vaciar.
- Proveedor inexistente → **404**. Proveedor de tipo distinto de `COMPONENTES` → **422** «Solo los proveedores de
  componentes tienen piezas.». Algún `idCom` inexistente → **422** «Alguna pieza ya no existe.» (sin guardar nada).
- Responde **204**.
- Registro de actividad: acción **`EDITAR_PIEZAS_PROVEEDOR`**, detalle `ID_PROV: <id>, PIEZAS: +<añadidas> −<quitadas>`.
- Sin control de versión: si dos personas editan la lista del mismo proveedor a la vez, gana la última.
- Contrato OpenAPI: el esquema de la petición (record anidado `ProveedorPiezasRequest`), `pendiente` y `proveedores`
  nulables en `Componente`. `docs/autorizacion_endpoints.md` y `docs/api_contract.md` al día.

## 5. Web

### 5.1 Proveedores: «Piezas que vende…»

- Entrada nueva en el menú de cada proveedor de **componentes**, solo para el SUPERTECNICO.
- Diálogo «Piezas que vende · <proveedor>» (ancho ~640):
  - Buscador; lista de las piezas **activas**, una fila por grupo compartido (nombre como en Stock), con su casilla.
    Las de la lista del proveedor salen marcadas (de `componentes/gestionados`, campo `proveedores`).
  - **«Marcar las que se ven»** y **«Quitar las que se ven»** actúan sobre las filas que deja el buscador.
  - Interruptor «Solo las marcadas». Contador «N piezas marcadas».
  - «Guardar» manda la lista entera (las desactivadas que ya estaban se conservan aunque no se vean); «Cancelar» no
    guarda. Un 422 sale dentro del diálogo. Abierto congela el sondeo como los demás diálogos.
- Al guardar se recarga el listado de Stock (las claves `['componentes', …]`).

### 5.2 «Nuevo pedido»

- El combo **«Proveedor»** de arriba sigue haciendo lo de hoy (rellena las líneas sin proveedor; «Aplicar a todas»).
- El botón pasa a ser **«Añadir previsión de X (N)»**, con X el proveedor elegido y N las piezas (master, activas) de la
  lista de X en modo Auto con `pedir60 > 0`. Sin proveedor: **«Añadir previsión»** deshabilitado con la ayuda «Elige
  antes el proveedor».
- Al pulsarlo hace lo de hoy (`aplicarPrevision`, spec 0.9.6 §4.4) **solo con las piezas de X**: líneas nuevas con
  proveedor X, cantidad `pedir60` y precio 0,00; las líneas que ya estaban, cantidad = máx(la suya, la previsión).
- Línea de información:
  - «N pieza(s) en Auto no necesitan pedido.» (las de X en Auto con `pedir60 = 0`).
  - Nuevo: «K pieza(s) en Auto con algo que pedir no están en la lista de ningún proveedor.» (K > 0).
- En el modo «Añadir líneas» (bloque fijo), X es el proveedor del bloque.
- Funciones puras en `formulario/lineas.ts`: `cuantasPrevision(activos, idProv)`, `aplicarPrevision(lineas, activos,
  idProv)` con el filtro por lista y `sinLista(activos)`.

### 5.3 Menú del bloque: «Añadir previsión…»

- Entrada nueva tras «Añadir líneas…», con su misma disponibilidad (solo SUPERTECNICO).
- Abre «Añadir líneas · Bloque N» (precarga `{ modo: 'bloque', idBloque, idProv, prevision: true }`) y aplica la
  previsión del proveedor del bloque en cuanto carga el listado de Stock. Si no hay nada que pedir, no añade líneas y la
  línea de información lo dice.

### 5.4 Buscador de Pedidos (Componentes)

- Si el texto (sin espacios a los lados) son **solo cifras**, enseña las líneas del bloque con **ese número exacto**,
  en la vista agrupada y en la plana. Si no, busca la pieza como hoy.
- El texto de ayuda de la caja pasa a «Buscar pieza o nº de bloque».
- La pestaña «Otros» no cambia. La llegada desde Stock (`?buscar=<tipo>`) nunca son solo cifras.

### 5.5 Stock

- «Pedir 60 d» ya llega descontando lo pendiente.
- Columna nueva **«Pendiente»** tras «En Camino», solo para SUPERTECNICO y ADMIN (como las de previsión): el número o
  «—» si es 0. Sin enlace. En el CSV con previsión, columna «Pendiente» tras «En camino».

## 6. Errores y casos límite

- Listado de Stock sin cargar o con error: N = 0 y el botón queda deshabilitado (como hoy).
- Una pieza de la lista que se desactiva: no cuenta; si se reactiva, sigue en la lista.
- Un proveedor desactivado conserva su lista; el combo solo ofrece activos.
- Proveedor «Otros»: no tiene «Piezas que vende…» (y el servidor lo rechaza con 422).

## 7. Pruebas y despliegue

**Servidor:** previsión con pendiente; SQL del listado (`PENDIENTE`, proveedores por master); `PUT` (sustituir, vaciar,
slave → master, repetidos, 404, 422 de tipo y de pieza, texto del registro); roles (TECNICO y ADMIN → 403); contrato.

**Web:** funciones puras (N por proveedor, filtro, `sinLista`); botón deshabilitado sin proveedor y con el texto de X;
«Añadir previsión…» desde el bloque; diálogo de Proveedores (marcar y quitar lo visible, «Solo las marcadas», las
desactivadas se conservan al guardar); buscador con cifras; columna y CSV de «Pendiente».

**E2E** `tests/e2e/catalogo.spec.ts`, solo contra preproducción (letrero), datos sintéticos y todo restaurado:
1. Proveedor de prueba; «Piezas que vende…» con `E2E_SKU_PRUEBA`.
2. Que la pieza tenga algo que pedir: como ADMIN, sube su mínimo; como SUPERTECNICO, la pasa a Auto (se guardan los
   valores de antes).
3. «Nuevo pedido» → proveedor de prueba → «Añadir previsión de X (1)» con la cantidad de la previsión → guardar.
4. Stock: «Pendiente» sube y «Pedir 60 d» baja; Pedidos: buscar el número del bloque enseña solo ese bloque.
5. Limpieza: borrar la línea (pendiente), devolver mínimo y modo, borrar el proveedor (su lista se va en cascada) y
   comprobar el stock.

**Preproducción** (ya tiene la 0.9.9): código, migración de la lista, relleno (dos veces: repetible), arranque, todas las
E2E más la nueva, `init.sql` con **25** tablas.

**Producción:** el guion `Apuntes/prod-v099.md` añade la migración y el relleno tras la reconstrucción de bloques; la
copia de después, 25 tablas. Sigue la norma de despliegue en horario (aditivo; la web 0.9.8 ignora los campos nuevos).

**Documentación:** CHANGELOG y `NOVEDADES-v0.9.9.md` ampliados; fichas de paridad de Proveedores, Stock y Pedidos.

## 8. Fuera de alcance (backlog de `plan-futuro`)

- «Ya que pides a X…» (*can-order*): piezas de X en Auto que tocarían pedido en los próximos 15 días, con la cantidad
  para cubrir 75. Tras usar la previsión por proveedor.
- Precio de las líneas de la previsión = el último pagado a ese proveedor.
- Combo de proveedor de la línea ordenado por la lista.
- Proveedor preferido o prioridad entre varios.
- Plazo real por proveedor.
- «Pendiente» de Stock como enlace a Pedidos.
