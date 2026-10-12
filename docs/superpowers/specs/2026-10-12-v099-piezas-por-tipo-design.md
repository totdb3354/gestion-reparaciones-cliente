# 0.9.9 (ajuste) — Piezas que vende cada proveedor, por tipo de pieza

Fecha: 2026-10-12. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va **dentro de la 0.9.9**, que está en `main` y en preproducción, sin desplegar en producción ni etiquetar.
**Sustituye** la lista por referencias de la ampliación del 2026-10-11 (`2026-10-11-v099-catalogo-proveedores-design.md`,
§2 fila «Qué vende cada proveedor», §3, §4.2, §4.3 y §5.1). El resto de aquella spec sigue igual:
- la previsión descuenta lo pendiente;
- «Añadir previsión de X (N)» y «Añadir previsión…» funcionan igual;
- las columnas «Pendiente» en Stock no cambian;
- el buscador de bloques por número no cambia.

Prototipo: `assets/2026-10-12-v099-prototipo-piezas-por-tipo.html` (datos inventados).

## 0. Nota sobre este documento

Repo público: sin datos reales, IPs ni nombres. Las cifras salen de preproducción (copia de producción del 2026-10-09) y
solo orientan.

## 1. Objetivo

Hoy la lista de un proveedor es pieza a pieza. Uno de los proveedores tiene 59 piezas marcadas y el chasis tiene 184
referencias activas; casi todo es ruido de colores y variantes. Además, una pieza nueva no entra en la lista de nadie
hasta que alguien la marca.

Se pasa a **tipos de pieza**: un proveedor vende «Batería», y de entrada de **todos los modelos**, incluidos los que
salgan más adelante. Si de un tipo no vende algún modelo, se **desmarca** ese modelo. Lo normal será quitar uno o dos.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Unidad | **Tipo de pieza** (prefijo de la referencia: `bat`, `cha`, `tapa`, `g`, `mc`, `lcd`, `cam`) | El color y la calidad no deciden a quién se pide |
| Concretar | Por **modelo**, como **excepciones**: el proveedor vende el tipo **menos** los modelos desmarcados | Lo normal es quitar uno o dos; los modelos nuevos entran solos |
| Por defecto | Al marcar un tipo, **todos los modelos** | Decisión del usuario |
| Tipos de un proveedor nuevo | **Ninguno**: se marcan a mano | Con todos marcados, la previsión de un proveedor de cámaras traería baterías |
| Arranque | Cada **tipo pedido alguna vez** al proveedor (sin cancelados), **todos los modelos** | Decisión del usuario; luego se recorta a mano |
| Dónde se aplica | **En el servidor**: traduce las reglas a la lista de proveedores de cada pieza | Una sola lógica; Stock y la previsión reciben lo mismo que hoy |
| Grupo compartido | El grupo es de X si X vende **cualquiera** de sus piezas | El grupo es una sola pieza física |
| Versión | **Dentro de la 0.9.9** | La tabla por referencias no llegó a producción: no hay datos que convertir |

## 3. Datos

### 3.1 Tablas

`sql/migracion-catalogo-proveedores.sql` se reescribe: ya no crea `Proveedor_componente`, sino estas dos. Lo mismo en
`crear_bd.sql`: las dos tablas tras `Proveedor` y `Componente`, y sus `DROP` en la lista.

```sql
CREATE TABLE Proveedor_tipo (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO),
    CONSTRAINT fk_provtipo_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV) ON DELETE CASCADE
);

CREATE TABLE Proveedor_tipo_excluido (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    MODELO  VARCHAR(20) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO, MODELO),
    CONSTRAINT fk_provexcl_tipo FOREIGN KEY (ID_PROV, PREFIJO) REFERENCES Proveedor_tipo (ID_PROV, PREFIJO) ON DELETE CASCADE
);
```

- Una fila de `Proveedor_tipo`: el proveedor vende ese tipo. Sus filas de `Proveedor_tipo_excluido` son los modelos de
  ese tipo que **no** vende. Sin excluidos = todos los modelos.
- El esquema es aditivo y el servidor 0.9.8 sigue funcionando. Se aplica una vez por base: el `CREATE TABLE` falla si ya
  existe.
- Con la 0.9.9 la base de producción pasa de 23 a **26 tablas**: `Bloque_compra` y estas dos.
- `ON DELETE CASCADE`: borrar un proveedor se lleva sus tipos y, con ellos, sus excepciones. Un proveedor solo se puede
  borrar si no tiene pedidos.
- **Preproducción** tiene la tabla vieja `Proveedor_componente`. Se borra a mano antes de la migración nueva
  (`DROP TABLE Proveedor_componente;`, en el guion de preprod). En producción nunca existió.

### 3.2 Relleno inicial

`sql/rellenar-catalogo-proveedores.sql` se reescribe. Es **repetible**:

```sql
INSERT IGNORE INTO Proveedor_tipo (ID_PROV, PREFIJO)
SELECT DISTINCT cc.ID_PROV, SUBSTRING(c.TIPO, 1, LOCATE('i', c.TIPO) - 1)
FROM Compra_componente cc
JOIN Componente c ON c.ID_COM = cc.ID_COM
JOIN Proveedor p ON p.ID_PROV = cc.ID_PROV AND p.TIPO = 'COMPONENTES'
WHERE cc.ESTADO <> 'cancelado' AND c.TIPO NOT LIKE 'otro%' AND LOCATE('i', c.TIPO) > 1;
```

- El prefijo se saca igual que en el servidor (`ComponenteDAO.extraerPrefijo`): todo lo que va antes de la primera `i`.
- No crea excepciones.
- Comprobaciones al final:
  - `tipos` (filas) y `proveedores`;
  - `prefijos_raros` **0**: filas cuyo prefijo no es el de ninguna pieza que no sea `otro`.
- En la copia de preproducción salen **14 filas de 6 proveedores**: uno con 6 tipos, otro con 4 y cuatro con 1.

## 4. Servidor

### 4.1 Tipo y modelo de una referencia

Clase nueva `CatalogoPiezas` (utilidad sin estado):

- **`prefijo(String tipo)`** = la lógica actual de `ComponenteDAO.extraerPrefijo`, que pasa a usar esta.
- **`modelo(String tipo, String prefijo)`** = calco de `extraerModelo` de la web (`taller/lib/modelos.ts`):
  1. quita el prefijo y, si lo siguiente es una `i`, también la `i`;
  2. de los códigos de `MODELOS_ORDENADOS` que son prefijo de lo que queda, devuelve el **más largo**;
  3. si ninguno lo es, devuelve `null`.
- **`MODELOS_ORDENADOS`**: los mismos 39 códigos y en el mismo orden que en la web (de `6s` a `17promax`, con `16e` y
  `air`).
  - Un comentario en cada uno de los dos sitios avisa de que hay que tenerlos iguales.
  - Un modelo nuevo, como el 17e, se añade en los dos.
- **`ORDEN_TIPOS`**: `bat`, `cha`, `tapa`, `g`, `mc`, `lcd`, el orden del formulario. Los demás van detrás, por orden
  alfabético; hoy solo `cam`.

### 4.2 Qué piezas vende un proveedor

Una pieza `c` (activa o no, nunca `otro`) la vende X cuando se cumplen las dos condiciones:
- X tiene `prefijo(c)` en `Proveedor_tipo`;
- `modelo(c)` es `null` o no está entre los excluidos de X para ese tipo.

En un grupo compartido, el master y sus slaves llevan la misma lista. Esa lista es la unión de los proveedores que
venden cualquiera de las piezas del grupo.

`ComponenteController.getAllGestionados` rellena `proveedores` igual que hoy, pero con esta regla:
- se cargan las reglas con dos consultas, una por tabla;
- se cruzan en memoria;
- solo SUPERTECNICO y ADMIN reciben la lista; el TECNICO recibe `null`.

La forma de `proveedores` no cambia, así que la web de la previsión tampoco.

### 4.3 Endpoints

Se quita `PUT /api/proveedores/{idProv}/componentes`, que no llegó a producción.

**`GET /api/proveedores/{idProv}/piezas`** (`hasAnyRole('SUPERTECNICO','ADMIN')`).

Devuelve una lista con **todos los tipos que existen**, en `ORDEN_TIPOS`. Los tipos que existen son los prefijos de las
piezas que no son `otro`, activas o no. Es la misma definición que usa el `PUT` para validar. Cada elemento:

```json
{ "prefijo": "g", "vende": true, "modelos": ["12", "12mini", "…"], "excluidos": ["12mini", "13mini"] }
```

- `modelos`: los modelos de las piezas activas de ese tipo, en el orden de `MODELOS_ORDENADOS`, más los excluidos que
  ya no tengan pieza activa (para poder verlos y quitarlos).
- `excluidos`: vacío si no vende el tipo o si lo vende de todos los modelos.
- Errores: 404 si el proveedor no existe; 422 «Solo los proveedores de componentes tienen piezas.» si no es de
  componentes.

**`PUT /api/proveedores/{idProv}/piezas`** (`hasRole('SUPERTECNICO')`). Cuerpo:

```json
{ "tipos": [ { "prefijo": "bat", "excluidos": [] }, { "prefijo": "g", "excluidos": ["12mini", "13mini"] } ] }
```

- Sustituye todo lo del proveedor en una transacción: primero `SELECT … FOR UPDATE` sobre su fila de `Proveedor`, luego
  `DELETE` de sus tipos (las excepciones caen en cascada) e inserciones. Si dos personas guardan a la vez, gana la
  última y nadie ve un 500.
- Los excluidos repetidos se ignoran. Lista de tipos vacía = no vende nada.
- Errores, todos sin guardar nada:
  - 404 si el proveedor no existe.
  - 422 «Solo los proveedores de componentes tienen piezas.».
  - 422 «Falta la lista de tipos.» si `tipos` es nulo o un elemento es nulo.
  - 422 «Falta la lista de modelos excluidos.» si `excluidos` es nulo.
  - 422 «Algún tipo de pieza no existe.» si un prefijo no está entre los que existen.
  - 422 «Un tipo de pieza está repetido.».
  - 422 «Algún modelo no existe.» si un código no está en `MODELOS_ORDENADOS`.
  - 422 «Si no vende ningún modelo de un tipo, desmarca el tipo.» si los excluidos **no están vacíos** y cubren todos
    los modelos con pieza activa de ese tipo, y el tipo **tiene** alguna pieza activa (§6).
- Responde **204**.
- Registro de actividad: acción **`EDITAR_PIEZAS_PROVEEDOR`**, con detalle
  `ID_PROV: <id>, PIEZAS: bat (todos), g (sin 12mini, 13mini)`. Los tipos van en `ORDEN_TIPOS` y los modelos en el de
  `MODELOS_ORDENADOS`. Sin tipos: `PIEZAS: ninguna`.
- Contrato OpenAPI:
  - records anidados `ProveedorPiezasTipo` (respuesta), `ProveedorPiezasRequest` y `ProveedorPiezasTipoRequest`;
  - todas las propiedades obligatorias;
  - `docs/autorizacion_endpoints.md` y `docs/api_contract.md` al día.

## 5. Web

### 5.1 Proveedores: «Piezas que vende…»

Funciona como en el prototipo. La entrada del menú sigue igual: solo SUPERTECNICO y solo proveedores de componentes. El
diálogo «Piezas que vende · <proveedor>» (ancho 640):

- **Texto de ayuda:** «Marca los tipos de pieza que vende. De entrada, de todos los modelos (también los que salgan más
  adelante); si alguno no lo vende, desmárcalo en el desplegable.»
- **Recuento:** «Vende N de M tipos».
- **Una fila por tipo**, en el orden del servidor:
  - casilla con el nombre del tipo, tomado de `categoriaPieza` (`taller/lib/piezas.ts`): Batería, Chasis, Tapa trasera,
    Glass, Marco, Pantalla, Cámara;
  - a la derecha, el desplegable de modelos.
- **Desplegable de modelos:**
  - Sin la casilla marcada, deshabilitado y con el texto «No lo vende».
  - Con la casilla marcada:
    - dice «**Todos los modelos**» o «**Seleccionados K de M**»;
    - al lado, en gris, los que faltan: «Sin iPhone 12 Mini · iPhone 13 Mini», con los nombres de `traducirModelo`.
  - Por dentro:
    - arriba, la opción «Todos los modelos», que vuelve a marcarlos todos;
    - una línea;
    - los modelos con su casilla, **marcados** salvo los excluidos.
  - Si se desmarcan todos los modelos, se desmarca el tipo.
  - Al marcar un tipo, empieza con todos los modelos.
  - Se reutiliza `MultiSelect`, con una opción nueva para la entrada «Todos los modelos».
- **Fuera:** el buscador, «Marcar/Quitar las que se ven», «Solo las marcadas» y el recuento de piezas.
- **Carga:**
  - mientras carga, «Cargando piezas…»;
  - si falla, «No se ha podido cargar la lista de piezas.», y Guardar queda deshabilitado;
  - el diálogo se rellena solo con datos frescos, como ahora.
- **Guardar** manda la lista entera. Un 422 sale dentro del diálogo. Al guardar se recarga el listado de Stock: las
  claves `['componentes', …]`, que llevan `proveedores`.
- Abierto, congela el sondeo, como los demás diálogos.

### 5.2 Lo demás

Sin cambios: «Nuevo pedido», «Añadir previsión de X (N)», sus avisos, «Añadir previsión…» del bloque, Stock y Pedidos.
Todos usan `proveedores` del listado de Stock.

## 6. Casos límite

- **Pieza de modelo desconocido** (`modelo` = `null`): la vende quien tenga su tipo; las excepciones no le afectan.
- **Tipo con todas sus piezas desactivadas:** sigue existiendo, así que sale en el diálogo. Su desplegable solo enseña
  los modelos que tenga excluidos, si tiene alguno. Se puede marcar o desmarcar como cualquier otro.
- **Proveedor desactivado:** conserva sus tipos. El combo de «Nuevo pedido» solo ofrece los activos.
- **Proveedor «Otros»:** no tiene «Piezas que vende…» y el servidor responde 422.

## 7. Pruebas y despliegue

**Servidor:**
- `CatalogoPiezas`:
  - prefijos;
  - modelo más largo (`bati13promax` → `13promax`);
  - `air` y `16e`;
  - sin modelo → `null`;
  - los mismos casos que los tests de la web.
- Regla de venta:
  - todos los modelos;
  - con excepciones;
  - modelo `null`;
  - grupo compartido (unión);
  - pieza desactivada.
- DAO de las dos tablas.
- `GET`: orden, `modelos` (incluido un excluido sin pieza activa), 404, 422, roles.
- `PUT`:
  - sustituir y vaciar;
  - excluidos repetidos;
  - cada 422;
  - texto del registro;
  - bloqueo del proveedor;
  - roles: TECNICO y ADMIN → 403.
- Contrato.

**Web:**
- Diálogo:
  - casilla del tipo;
  - desplegable con todos marcados y «Todos los modelos»;
  - «Seleccionados K de M» y la línea «Sin …»;
  - desmarcar todos → tipo desmarcado;
  - carga y error.
- La previsión, sin cambios: sus tests siguen pasando.

**E2E** `tests/e2e/catalogo.spec.ts`, rehecha. Solo contra preproducción (letrero), con datos sintéticos y todo
restaurado:
1. Proveedor de prueba. En «Piezas que vende…», el tipo de `E2E_SKU_PRUEBA`, con todos los modelos menos el de la pieza
   de prueba.
2. Que la pieza tenga algo que pedir: como ADMIN, sube su mínimo; como SUPERTECNICO, la pasa a Auto. Se guardan los
   valores de antes.
3. «Nuevo pedido» → proveedor de prueba: «Añadir previsión de X (N)» **no** trae la pieza de prueba, porque su modelo
   está excluido.
4. En «Piezas que vende…», «Todos los modelos». Ahora sí la trae. N se calcula con la API: piezas del tipo en Auto con
   «Pedir 60 d» > 0.
5. Guardar el pedido. Stock: «Pendiente» sube y «Pedir 60 d» baja. Pedidos: buscar el número del bloque enseña solo ese
   bloque.
6. Limpieza:
   - borrar las líneas;
   - devolver el mínimo y el modo;
   - borrar el proveedor (sus tipos se van en cascada);
   - comprobar el stock;
   - si no queda todo como estaba, la prueba falla.

**Preproducción:**
1. Código.
2. `DROP TABLE Proveedor_componente;`.
3. Migración nueva.
4. Relleno dos veces, para comprobar que es repetible.
5. Arranque.
6. Todas las E2E, una a una.
7. Limpieza.
8. `init.sql` con **26** tablas.

**Producción:** `Apuntes/prod-v099.md` cambia:
- `tablas_ya` mira `Bloque_compra`, `Proveedor_tipo` y `Proveedor_tipo_excluido`;
- `parejas_previstas` pasa a `tipos_previstos`;
- comprobaciones del relleno nuevas;
- copia con **26** tablas;
- vuelta atrás: `DROP` de las dos tablas.

**Documentación:**
- CHANGELOG `[0.9.9]` y `NOVEDADES-v0.9.9.md`: se reescribe la viñeta de «Piezas que vende cada proveedor».
- Fichas de paridad de Proveedores.
- Nota en la spec del 2026-10-11 que remite a esta.

## 8. Fuera de alcance

Todo lo del §8 de la spec del 2026-10-11 («Ya que pides a X…», último precio, combo ordenado, proveedor preferido,
plazos, «Pendiente» como enlace).

Además, la limpieza de proveedores sin uso, como los dos «de incidencias». La hace el usuario desde Proveedores (Borrar)
tras comprobar en producción que no tienen pedidos ni lotes. No es código.
