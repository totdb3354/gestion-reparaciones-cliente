# Pieza 2 del módulo de teléfonos — Pedidos (1ª entrega: leer, avisar y sugerir)

Fecha: 2026-11-04. Estado: **aprobada** (por partes en el brainstorming del 03/11 y el 04/11, y la spec escrita el 04/11).
Plan de la 0.9.26: `docs/superpowers/plans/2026-11-04-v0926-pedidos-lectura.md`; sus ajustes están en el §15. La 0.9.27 tendrá su
propio plan.
Prototipo: `assets/2026-11-04-pieza2-prototipo-pedidos.html` (datos inventados; disposición A, la elegida; enseña también lo de la
0.9.27).
**Dos versiones**, con números provisionales porque otra sesión lleva la 0.9.25 («Qué revisar»); quien mergee segundo cambia el
número:
- **0.9.26** — servidor + base + web: lectura de las tres fuentes, pantalla «Pedidos» con estados y avisos, línea de pedido en
  Asignaciones y «Compras».
- **0.9.27** — servidor + web, sin base: sugerencia de IMEI, «¿Prefieres otro?», «Copiar IMEI» y «Asignar trabajo».

Las notas del negocio (datos medidos, nombres de canales y cuentas, decisiones con su fecha) están en
`Apuntes/mapa-flujo-telefono.md`, §11 «Pieza 2». Esta spec no lleva datos reales: el repo es público.

## 1. Problema y objetivo

Hoy los pedidos de teléfonos están repartidos:
- **Amazon y las webs** entran en ShippyPro con su número de pedido, pero sin IMEI. El IMEI que se elige para cada venta se
  teclea en **PEDIDOS 2026**, una hoja de Google. En Amazon, las filas son genéricas («Amazon» + SKU, sin número de pedido), y el
  dueño concreto se decide al final, en el libro de ventas de Amazon.
- **Las ventas en directo** van al **Simulador de movimientos**, otra hoja de Google: cada línea ya es pedido + IMEI. El Simulador
  solo pasa a PEDIDOS 2026 las ventas que llevan reparación.
- Para elegir el móvil, quien asigna busca en NSYS y en las cajas. El verificador de SKU de Automatización avisa cada 30 min si
  el IMEI tecleado no corresponde al SKU, pero solo mira NSYS: no sabe lo que ha hecho el taller.

**Objetivo de la 1ª entrega de la pieza 2:** una pantalla «Pedidos» en el ERP que **solo lee**:
- junta los pedidos pendientes de todos los canales, cada uno con su número, los más antiguos arriba;
- dice de cada unidad si ya tiene móvil (tecleado en PEDIDOS 2026 o en el Simulador) y si cuadra, usando lo que ha hecho el
  taller (el «SKU del taller» de la 0.9.21);
- a las que no tienen móvil, les sugiere el más parecido (0.9.27) y deja lanzar el trabajo de taller desde ahí.

**No sustituye nada.** El IMEI se sigue tecleando donde hoy; el ERP lo lee a los pocos minutos. PEDIDOS 2026, el bot, el plugin
de la web y el verificador siguen igual. Asociar el IMEI en el ERP es la 2ª entrega; que los demás lean del ERP, la 3ª.

## 2. Decisiones del usuario

**Del 16/10 y el 19/10 (detalle en las notas):** se parte del pedido (los más antiguos primero) y el ERP sugiere el IMEI «más
parecido» por niveles; decide la persona; lo hace quien asigna el taller, en la misma pantalla en la que asigna la reparación;
número de pedido como identificador en todos los canales y el cliente aparte; «Pedidos» para los pedidos de clientes y
«Compras» para los de piezas.

**Del 03/11:** la pieza 2 empieza solo leyendo; el Simulador entra ya como canal (leerlo; sustituirlo es la pieza 4).

**Del 04/11:**
1. Fuentes de la 1ª entrega: **ShippyPro + Simulador + PEDIDOS 2026**. Amazon y webs necesitan dos (el pedido en ShippyPro, el
   IMEI en PEDIDOS 2026); el Simulador es las dos cosas en una.
2. La 1ª entrega lleva el botón **«Asignar trabajo»** (abre el modal de Asignaciones de hoy, relleno).
3. El aviso «cuadra / no cuadra» usa el **SKU del taller**: lo que el taller ya montó cuenta aunque NSYS no esté al día.
   **Vender un móvil que se está reparando es lo normal** (los de Listos son la excepción): «en el taller» no es una alarma.
4. Se comprueba también el **grado**, con lo que acepta cada canal.
5. **Amazon:** los IMEIs tecleados de un SKU cubren los pedidos pendientes más antiguos de ese SKU (el móvil más avanzado al más
   antiguo), marcados «sin pedido concreto».
6. **Decide el humano:** puede elegir otro de la lista o **cualquier IMEI** («¿Prefieres otro?»); el ERP solo comprueba.
7. Disposición **A**: lista de pedidos a la izquierda y la unidad elegida a la derecha.
8. Cliente del modal: **el de verdad** (web y Simulador) y **«Amazon»** en Amazon.
9. **Asignaciones** enseña el nº de pedido y el SKU de venta ya en la 1ª entrega.
10. **Simulador:** un pedido sale mientras alguno de sus móviles no esté en Released y tenga **14 días o menos**; los más viejos,
    tras un interruptor.
11. **Amazon Stockroom** (la segunda cuenta de Amazon) va como canal aparte, con las reglas de Amazon.
12. Enfoque: **tablas propias**, lectura cada 5 min, **dos versiones** (0.9.26 y 0.9.27, arriba).
13. El **técnico no ve «Pedidos»**; sí la línea nueva de Asignaciones.
14. El nombre de los clientes particulares **se guarda siempre** (se propuso borrarlo a los 90 días; descartado).

## 3. Qué ve cada rol

| | «Pedidos» | Línea de pedido en Asignaciones | «Compras» (antes «Pedidos» de Stock) |
|---|---|---|---|
| TECNICO | no (sin enlace; la ruta responde 403) | sí | como hoy (no entra) |
| SUPERTECNICO | sí | sí | como hoy |
| ADMIN | sí | sí | como hoy |

Las rutas `/api/pedidos/**` llevan `hasAnyRole('SUPERTECNICO','ADMIN')`, como `/api/stock-telefonos/**`.

## 4. Fuentes y lectura (0.9.26)

Paquete nuevo `com.reparaciones.servidor.pedidos`. Una tarea programada lee las tres fuentes **por separado** cada 5 min; si una
falla, las otras siguen y de la que falla se quedan los últimos datos buenos.

### 4.1 ShippyPro

- **Llamada:** `GetPendingOrders` (los pedidos recibidos y no enviados), una por vuelta. ShippyPro deja 20 llamadas cada 10 s.
  Formato de la API como lo usa `devoluciones` de Automatización (POST JSON con `Method`/`Params`); el plan lo comprueba con una
  llamada de solo lectura antes de programar.
- **Clave:** fichero de secretos montado en el contenedor, como la de Drive (`/opt/reparaciones/secretos/…`). Si ShippyPro deja
  crear una clave propia para el ERP, esa; si no, la de `devoluciones` (pendiente, §13).
- **Qué se guarda:** número de pedido (`transaction_id`), canal, fecha, nombre y país del cliente, y de cada artículo el SKU y la
  cantidad. **No** se guardan dirección, email, teléfono ni precios. Los nombres exactos de los campos (sobre todo el del cliente y
  si el número de Amazon es el de Amazon) se comprueban en el plan con esa llamada.
- **Canal:** por el identificador de marketplace de ShippyPro, con una tabla de traducción en un ajuste (no en el código):
  Amazon, Amazon Stockroom, Distribuidores, Particulares y BackiPhone. Los demás marketplaces (BackMarket, envíos «Excel»,
  devoluciones) se ignoran.
- **Pedidos fusionados** (`merged`): se tratan como uno; si da problemas, pendiente (§13).

### 4.2 PEDIDOS 2026

- Hoja de Google, leída con la **API de Google Sheets** (`spreadsheets.values.get`, solo lectura) por la cuenta de servicio de la
  pieza 1 (`erp-nsys-lector`), con el mismo fichero de credenciales. Al alcance de Drive se le suma
  `https://www.googleapis.com/auth/spreadsheets.readonly`.
- **Columnas**, por el nombre de la cabecera: Fecha, Plataforma, SKU, IMEI (y el número de fila, para el detalle). Si falta una,
  la lectura se **rechaza** y se avisa con el nombre de la columna.
- **Solo lo reciente:** se leen las últimas N filas (ajuste; las filas se añaden al final). El plan comprueba que la hoja va en
  orden y cuántas filas hacen falta.
- No se lee nada más (en desuso, observaciones…).

### 4.3 Simulador

- Hoja de Google, pestaña «Registro actividad», por la misma API y la misma cuenta.
- **Columnas**, por cabecera: Order id, Línea facturación, Fecha, Cliente, SKU, Cantidad, IMEI. **No** se leen precios, IVA,
  forma de pago ni nada de facturación.
- **Solo lo reciente:** las últimas N filas (ajuste), comprobando que la más antigua leída tiene más días que la ventana del
  interruptor (§6.3); si no, se leen más.

### 4.4 Ciclo, fallos y frenos

- **Tarea:** `@Scheduled` cada 5 min, solo con `pedidos.lectura.activa=true` (apagada en los tests; encendida en preprod y
  producción). Preprod lee las mismas fuentes reales: no escribe en ellas.
- **Plazos de red:** los de `ClienteDrive` (1 min hasta las cabeceras, 2 min la petición entera). Si una fuente no contesta, se
  reintenta en la vuelta siguiente.
- **Freno de las hojas:** si una hoja trae menos del 90 % de las filas de la lectura anterior, se trata como incompleta y no se
  aplica (como el Items).
- **ShippyPro devuelve 0 pendientes de golpe:** se cierran los pedidos, pero un pedido cerrado que vuelve a salir pendiente **se
  reabre**. No se pierde nada.
- **Clave rechazada** o error de la API: la lectura queda en error con el mensaje.
- **Transacción:** cada vuelta escribe en una transacción, con `READ COMMITTED` (MariaDB 11.6+: leer y luego actualizar en la
  misma transacción con dos peticiones a la vez da el error 1020; ver la 0.9.18 y «Revisado»).
- **Aviso de datos antiguos:** por fuente, ámbar desde 1 h sin una lectura buena y rojo desde 3 h; no salta de 23:00 a 7:00
  (como NSYS).

### 4.5 Ajustes (`@Value`, como el resto)

`pedidos.lectura.activa`, `pedidos.shippypro.clave` (ruta del fichero), `pedidos.shippypro.canales` (marketplace → canal),
`pedidos.pedidos2026.hoja` y `.pestana`, `pedidos.simulador.hoja` y `.pestana`, `pedidos.filas` (cuántas filas leer de cada
hoja), `pedidos.simulador.dias` (14). Los identificadores de las hojas y los marketplaces van en el compose de cada VM, no en el
repo.

## 5. Datos (0.9.26, migración que solo añade)

Tablas nuevas, aparte (como las `Nsys_*`). Lo que ya existe no se toca. `Envio` y `Envio_Telefono` (modelo antiguo de envíos, con
clave ajena a `Telefono`) no sirven aquí y se dejan para la pieza 3.

### 5.1 `Pedido` — uno por pedido

`ID_PEDIDO` (autonumérico), `CANAL`, `NUMERO` (único por canal), `CLIENTE`, `PAIS`, `FECHA`, `FUENTE` (SHIPPYPRO / SIMULADOR),
`VISTO_PRIMERO`, `VISTO_ULTIMO`, `CERRADO_EN` (NULL = abierto) y `MOTIVO_CIERRE` (YA_NO_PENDIENTE, TODO_VENDIDO, ABONADO,
FUERA_DE_VENTANA). **Los pedidos cerrados no se borran** (para la 2ª entrega y, más adelante, el rastro). El nombre del cliente
se guarda siempre.

### 5.2 `Pedido_unidad` — una por unidad

`ID_UNIDAD`, `ID_PEDIDO`, `POSICION`, `SKU` (tal como lo da la fuente), `IMEI` (NULL si no hay), `IMEI_FUENTE` (PEDIDOS_2026 /
SIMULADOR), `IMEI_REF` (fila de la hoja), `IMEI_TECLEADO_EN` (la fecha de esa fila), `SIN_PEDIDO_CONCRETO` (Amazon, §6.2),
`OTRO_ORDER_ID` (Simulador: el Order id de otra línea del mismo IMEI, para el buscador), `CERRADA_EN` y `MOTIVO_CIERRE`
(RELEASED, ABONO).

### 5.3 `Pedido_lectura` — una por lectura de cada fuente

`ID_LECTURA`, `FUENTE` (SHIPPYPRO / PEDIDOS_2026 / SIMULADOR), `INICIO`, `FIN`, `RESULTADO` (OK / ERROR / RECHAZADA /
INCOMPLETA), `FILAS`, `SKU_DESCONOCIDOS` (cuántos y cuáles, texto corto), `MENSAJE`.

### 5.4 Maestro

`Nsys_producto` añade las columnas del maestro que traducen los SKU de los canales que faltan: `SKU (Amazon) con eSIM`,
`SKU (stock fonestore.es) Conjunto` (Particulares) y `SKU BackiPhone`. El plan comprueba en el maestro el texto exacto de cada
cabecera. `LectorMaestro` las lee; si faltan en el maestro, quedan vacías (no se rechaza el maestro por ellas).

### 5.5 Lo que no se guarda

El estado de cada unidad (§6.4) y la sugerencia (§7) se **calculan al pedirlos**, con lo último del espejo de NSYS y del taller:
nada se queda viejo.

## 6. Reglas (0.9.26)

### 6.1 Unidades

- **ShippyPro:** un artículo con cantidad N da N unidades. Solo cuentan los SKU de teléfono, es decir, los que el maestro conoce
  en la columna de SKU de su canal (§6.4.1). Los demás (portes, accesorios) no son unidades, pero se cuentan en
  `SKU_DESCONOCIDOS` de la lectura, para que un SKU de teléfono mal escrito no desaparezca sin verlo.
- **Simulador:** cada línea es una unidad, solo si su SKU es de teléfono (fuera portes como `GLS24`, cajas, regalos, piezas).
  - **Cada IMEI cuenta una sola vez:** si sale en varias líneas (una X que reaparece como E con otro Order id), el pedido es el
    de la primera línea; el otro Order id se guarda en `OTRO_ORDER_ID`.
  - Una línea **BB** (abono) cierra la unidad de ese IMEI (`ABONO`).
  - Las líneas **NO, R1, A1 y M** se tratan como ventas hasta que el usuario diga qué son (§13).
  - Un pedido es un Order id (el de la primera línea de cada IMEI); las líneas sin IMEI todavía son unidades sin móvil.

### 6.2 El IMEI de cada unidad

- **Simulador:** el de su línea.
- **Webs (Distribuidores, Particulares, BackiPhone):** la fila de PEDIDOS 2026 cuya Plataforma es el nombre del cliente
  (comparado sin mayúsculas, tildes ni espacios de más) y cuyo SKU es el de la unidad. Si el cliente tiene varios pedidos abiertos
  con ese SKU, las filas van a los más antiguos.
- **Amazon y Amazon Stockroom:** las filas de PEDIDOS 2026 de su Plataforma, agrupadas por SKU: «AMZ» (y «Amazon») para Amazon,
  «FNSK» o «FSNK» para Stockroom; cada canal solo con las suyas (ajuste 19). Una fila con «Pedido Amazon» es de su pedido o de
  ninguno (ajuste 21). Una fila cubre una unidad
  si su SKU es el de la unidad o uno de los nuestros que encajan en él (el abanico del maestro, §6.4.1). Los IMEIs de un SKU cubren
  los **pedidos pendientes más antiguos** de ese SKU, y el **móvil más avanzado va al pedido más antiguo** (primero Listos, luego
  taller, luego sin revisar; en empate, el que lleva más tiempo en stock). Esas unidades llevan `SIN_PEDIDO_CONCRETO`.
- **No cuentan:**
  - una fila de PEDIDOS 2026 cuyo IMEI ya está en Released en NSYS (ese móvil ya salió);
  - un IMEI que ya está en una unidad del Simulador (las ventas con reparación también están en PEDIDOS 2026: manda el Simulador).
- **IMEI repetido:** si el mismo IMEI queda en dos unidades abiertas, las dos llevan el aviso «IMEI repetido en dos pedidos».

### 6.3 Cierre

- **ShippyPro:** el pedido se cierra cuando ya no sale en los pendientes (`YA_NO_PENDIENTE`: enviado o cancelado) y se reabre si
  vuelve a salir.
- **Unidad del Simulador:** se cierra cuando su IMEI pasa a Released en NSYS (`RELEASED`) o tiene abono (`ABONO`). En ShippyPro
  manda el pedido: el móvil sale después de que se cree la etiqueta (y con ella el pedido deja de estar pendiente), y una fila de
  PEDIDOS 2026 con el IMEI ya en Released no cuenta (§6.2).
- **Simulador:** el pedido se cierra cuando todas sus unidades están cerradas (`TODO_VENDIDO` / `ABONADO`) o cuando sale de las
  filas leídas (`FUERA_DE_VENTANA`). **En la lista** sale si tiene alguna unidad abierta y **14 días o menos**; los de más de
  14 días con unidades abiertas, solo con el interruptor «Ver también los del Simulador de más de 14 días».

### 6.4 Estado de cada unidad

#### 6.4.1 Qué SKU acepta el pedido

El SKU del pedido viene en el formato de su canal. Los SKU **nuestros** que acepta son los `SKU (stock web)` de las filas del
maestro cuyo SKU de ese canal es el del pedido, sin distinguir mayúsculas:
- Distribuidores y Simulador: `SKU (stock web)` (el nuestro tal cual);
- Particulares: `SKU (stock fonestore.es) Conjunto`;
- BackiPhone: `SKU BackiPhone`;
- Amazon y Amazon Stockroom: `SKU (Amazon)` y `SKU (Amazon) con eSIM`. En Amazon un SKU acepta varios nuestros (A-, A, A+,
  A100, Ms…): es el abanico.
- **«B por A» (solo Amazon):** si el pedido es de grado A, también se aceptan los que encajan en el mismo SKU con grado B, con
  aviso.

#### 6.4.2 El SKU que vale del móvil

El de la 0.9.21: el de NSYS traducido por el maestro, con lo que el taller ya terminó por encima (chasis → su color y A; batería
nueva → 100). Hay trabajo abierto si el IMEI tiene una asignación A/AG/AP sin terminar o la entrega de la glass (el mismo cruce
que «Quién lo tiene»).

#### 6.4.3 Estados, en este orden

1. Sin IMEI → **Sin móvil**.
2. El IMEI no está en NSYS → **No cuadra: no está en NSYS**.
3. Modelo, capacidad o eSIM distintos de los del pedido → **No cuadra** (hay que cambiar el IMEI: el taller no cambia eso).
4. Grado ID en Amazon, o F, FD o RMA en cualquier canal → **No cuadra** (no se venden así, tenga o no trabajo abierto).
5. Con trabajo abierto → **En el taller con {técnico} · tiene que acabar en {SKU del pedido}**. No es una alarma.
6. Grado C o C100 sin reparar (en NSYS y sin cambio del taller) y sin trabajo abierto → **No cuadra, necesita taller** (un C no se
   vende sin reparar, aunque el maestro lo cuente como A).
7. El SKU que vale está entre los que acepta el pedido → **Cuadra**; si es Ms o «B por A» → **Con aviso**.
8. Si no (color o grado distintos) → **No cuadra, necesita taller**.

Además, sin cambiar el estado: «IMEI repetido en dos pedidos» (§6.2) y «sin pedido concreto».

**Estados en la pantalla** (filtros): Sin móvil · No cuadra (2, 3, 4, 6 y 8) · Con aviso · En el taller · Cuadra.

**Tabla de diferencias** (en «No cuadra»): Modelo, Capacidad, eSIM, Color y Grado del pedido frente a los del móvil, y si el taller
lo puede cambiar (modelo, capacidad y eSIM: nunca; color: chasis; grado: sí).

## 7. Sugerencia (0.9.27)

### 7.1 Quién entra

Mismo modelo, capacidad y eSIM; en stock (lo que «Stock de teléfonos» cuenta como stock); sin pedido (ni «de pedido» en NSYS ni
en una unidad abierta); y, si tiene un trabajo abierto, solo si su SKU que vale ya es uno de los que acepta el pedido y el
trabajo no lleva cambio de chasis (decisión del 19/10: no se le cambia el trabajo al técnico). Grados ID en Amazon y F/FD/RMA,
nunca.

### 7.2 Niveles

1. **Igual que el pedido:** su SKU que vale está entre los que acepta el pedido, no está en una caja de taller (normalmente,
   Listos) y no es un C o C100 sin reparar.
2. **En el taller, acabará igual:** su SKU que vale está entre los que acepta el pedido y está en una caja de taller (Glass,
   Pulir, Reparación, Pantalla rota) o con su trabajo abierto. El maestro ya cuenta el C como A: un C en Glass acabará en A.
3. **Hay que transformarlo:** revisado (con caja), pero de otro color o grado, o un C sin reparar fuera del taller.
4. **Sin revisar:** sin caja (`n/a`) o en Devolución.

### 7.3 Orden dentro de un nivel

1. **Menos trabajo:** número de cambios que le faltan (1 antes que 2).
2. **La pieza en el almacén:** primero los que tienen en stock las piezas que les faltan.
3. **Más tiempo en stock** (fecha de entrada en NSYS).

En Amazon, en el mismo nivel, los que se aceptan sin aviso van antes que los «B por A». La batería no ordena (su % no se
apunta), salvo el «100» del grado.

### 7.4 Qué le falta

Comparando el SKU que vale con el del pedido: otro color → **chasis** de ese color; grado por debajo → **chasis** (solo el chasis
pone la A, 0.9.21.1); «100» que falta → **batería nueva**; C en una caja de taller → que acabe su trabajo; C sin reparar fuera
del taller → **reparación**. El pulido y la glass no cambian el SKU en el ERP: solo cuentan como «que acabe su trabajo». De cada
pieza, si hay en el almacén y cuántas (por el SKU de la pieza en `Componente`, con el catálogo de piezas).

### 7.5 Reparto

El servidor recorre las unidades sin móvil de la **más antigua a la más nueva** y a cada una le da el mejor que queda: un IMEI
no se sugiere a dos unidades. A cada unidad le devuelve su sugerido, la lista de posibles (hasta 10, ordenados) y un «Por qué»
corto. Elegir otro en la pantalla **no guarda nada** ni recalcula las demás: manda lo que se teclee en PEDIDOS 2026 o en el
Simulador.

### 7.6 Comprobar otro IMEI

`POST` con un IMEI cualquiera para una unidad. Responde una de estas:
- **sirve**, con su nivel, qué le falta, dónde está y desde cuándo, y el aviso si ya va en otro pedido (de pedido en NSYS o en
  otra unidad abierta);
- **no sirve**: modelo, capacidad o eSIM distintos (dice cuál);
- **no está en NSYS**.

## 8. API

Todas con `hasAnyRole('SUPERTECNICO','ADMIN')`, salvo el cambio de Asignaciones.

**0.9.26:**
- `GET /api/pedidos` — los pedidos abiertos con sus unidades y el estado de cada una (§6.4), el móvil (IMEI, fuente, fecha, qué
  es, dónde está, quién lo tiene, diferencias) y el resumen de las fuentes. Parámetro `viejos=true` para los del Simulador de más
  de 14 días.
- `GET /api/pedidos/fuentes` — por fuente: última lectura, última buena, resultado, filas, SKU desconocidos y el aviso de datos
  antiguos (para la píldora).
- **Asignaciones:** las filas que ya devuelve el servidor para la tabla de Asignaciones añaden, si el IMEI está en una unidad
  abierta, `pedidoNumero`, `pedidoCanal`, `pedidoSku` y `pedidoRepetido`. Son campos nuevos y opcionales: la web anterior los
  ignora. Los ve también el técnico.

**0.9.27:**
- `GET /api/pedidos` añade a cada unidad sin móvil `sugerido` y `posibles` (§7.5), y a cada unidad el `idCliSugerido` del modal
  (§9.5).
- `POST /api/pedidos/unidades/{idUnidad}/comprobar` con `{ imei }` (§7.6).

Las rutas nuevas entran en el contrato OpenAPI (`OpenApiContractTest`).

## 9. Web

### 9.1 Navegación

- Grupo **Teléfonos**: «Stock de teléfonos» y **«Pedidos»** (`/telefonos/pedidos`), solo SUPERTECNICO y ADMIN.
- Grupo **Stock**: «Pedidos» pasa a llamarse **«Compras»** en el menú y en el título de la página. La dirección no cambia (los
  marcadores siguen valiendo).

### 9.2 «Pedidos» (0.9.26)

Como el prototipo, disposición A, sin lo de la 0.9.27:
- **Cabecera:** «N unidades en M pedidos · los más antiguos arriba» y la **píldora de las fuentes** (verde / ámbar / rojo, §4.4),
  con un desplegable: para Amazon y webs, ShippyPro (pedidos sin IMEI) y PEDIDOS 2026 (los IMEIs tecleados); para las ventas en
  directo, el Simulador (pedido e IMEI juntos); y NSYS (dónde está cada móvil). De cada una, la hora de la última lectura buena,
  si la última falló y por qué, y los SKU que no están en el maestro.
- **Filtros:** buscador (nº de pedido, también el otro Order id del Simulador; cliente; SKU; IMEI), chips de canal y de estado
  con su recuento, e interruptor «Ver también los del Simulador de más de 14 días».
- **Izquierda:** los pedidos de más antiguo a más nuevo. De cada uno: canal, número, cliente y país, fecha y «hace N días», y
  cuántas unidades; de cada unidad, SKU, estado y un resumen del móvil. Si un pedido tiene 3 o más unidades que cuadran, se
  pliegan en una línea «✔ N unidades más cuadran · ver».
- **Derecha:** la unidad elegida. «Pide» (SKU y descripción). Caja «Móvil»: IMEI, «Tecleado en {fuente} el {fecha}», «sin pedido
  concreto» con su explicación, qué es, el estado y, si no cuadra, la tabla de diferencias y qué hacer. «Ver rastro» abre el panel
  de la pieza 1.
- **Sin móvil (0.9.26):** «Sin móvil · tecléalo en PEDIDOS 2026» (o «en el Simulador»).
- **Refresco** cada minuto, parado mientras hay un menú o un modal abierto (como el resto de pantallas).
- Sin scroll de página a 1366×768 ni a 1920×1080.

### 9.3 Asignaciones (0.9.26)

Debajo del IMEI, en gris pequeño: «Pedido {número} · acabar en {SKU}». Si `pedidoRepetido`, el aviso «IMEI en dos pedidos». La
pantalla no cambia nada más.

### 9.4 «Pedidos» (0.9.27)

- **Caja «Sugerido»:** IMEI, nivel («Nivel 2 · En el taller, acabará igual»), qué es, dónde está (con «en manos de …» si tiene
  trabajo), qué le falta (con «hay N en el almacén de piezas»), desde cuándo está en stock, «Por qué» y avisos (B por A, Ms).
- **Lista «Para este pedido: N móviles podrían servir»**, con el filtro y el orden explicados; elegir uno lo pone en la caja
  («Elegido por ti»).
- **«¿Prefieres otro?»:** campo para escanear o escribir un IMEI y «Comprobar» (§7.6); si sirve, queda «Elegido por ti (no
  estaba en la lista)»; «Volver a la sugerencia».
- **«Copiar IMEI»** (para pegarlo en PEDIDOS 2026 o en el Simulador) y **«Ver rastro»**.
- **«Asignar trabajo»**, apagado con su motivo si es del nivel 1 («ya es igual que el pedido») o ya está con un técnico.
- Nota fija: «Decides tú: el que teclees en {PEDIDOS 2026 / el Simulador} es el que vale. Elegir aquí no guarda nada.»
- En la lista de la izquierda, «sugerido …» o «elegido …».

### 9.5 Modal «Asignar trabajos» abierto desde «Pedidos» (0.9.27)

El modal de hoy (`AsignarTrabajosDialog`) acepta un arranque con una entrada ya puesta:
- el IMEI, en la cola que toca por la caja en la que está: **Glass** si está en Taller · Glass, **Pulido** si está en
  Taller · Pulir y **Reparación** en el resto (chasis, batería, reparación de un C, sin revisar);
- el **cliente**: en la web y el Simulador, el cliente del ERP con el mismo nombre (comparado como en §6.2); en Amazon y Amazon
  Stockroom, «Amazon»; si no hay ninguno con ese nombre, vacío y con el aviso «no está en la lista de clientes: elígelo»;
- el **comentario**: «Pedido {número} ({canal}) · tiene que acabar en {SKU} · {lo que falta}»;
- **«Cambio de chasis»** marcado si lo que falta es un chasis.

Desde ahí funciona como siempre (se pueden escanear más IMEIs; nada se escribe hasta «Guardar»).

## 10. Pruebas

- **Servidor:**
  - cada lector con respuestas inventadas (ShippyPro, Sheets), cabeceras que faltan, plazos, freno del 90 % y reapertura;
  - el cruce: Amazon por SKU y abanico (los más antiguos, el más avanzado primero), webs por cliente + SKU, X/E/BB del Simulador,
    Released, IMEI repetido, IMEI del Simulador también en PEDIDOS 2026;
  - los estados de §6.4.3 uno a uno;
  - 0.9.27: niveles, orden, qué le falta, reparto sin repetir y la comprobación de otro IMEI;
  - el contrato OpenAPI y el arranque del contexto.
- **Ensayo en el PC con copias reales** de PEDIDOS 2026 y del Simulador, como el de la pieza 1. Las copias y el resultado van a
  `Apuntes/`, nunca al repo.
- **Web:** pruebas de la pantalla, filtros, pliegue, detalle y píldora; en la 0.9.27, sugerencia, lista, «¿Prefieres otro?» y el
  arranque del modal.
- **E2E en preprod (Chromium):** 0.9.26, «Pedidos» abre, filtra y enseña el detalle, y el técnico no entra; 0.9.27, «Asignar
  trabajo» abre el modal relleno y la prueba sale con «Cancelar» (no escribe nada).
- **Aceptación de la 0.9.26 en preprod:** una mañana de trabajo normal comparando «Pedidos» con ShippyPro y PEDIDOS 2026
  (pendientes por canal, cuántos con IMEI) y el usuario revisando 10-20 pedidos a mano. Con su OK, producción y se empieza la
  0.9.27.

## 11. Compatibilidad, despliegue y vuelta atrás

- **0.9.26:** migración que solo añade (tres tablas y columnas del maestro); servidor y web. Primero preprod y después producción
  **en horario** (corte de segundos, migración aditiva, la web anterior sigue funcionando).
- **Antes de preprod, el usuario:** comparte PEDIDOS 2026 y el Simulador con `erp-nsys-lector` como Lector, activa la API de
  Google Sheets en el proyecto de Google, y decide la clave de ShippyPro. Después, la clave va a los secretos de la VM y los ajustes
  `pedidos.*` al compose.
- Guiones de preprod y producción para que los ejecute el usuario, cada bloque empezando con `hostname` y la comprobación de
  `backup-erp.sh`.
- **0.9.27:** servidor y web, sin base.
- **Vuelta atrás:** `pedidos.lectura.activa=false` apaga la lectura; la web anterior no usa las tablas nuevas ni los campos nuevos
  de Asignaciones.

## 12. Riesgos

- **El cruce de la web por nombre** puede fallar si el plugin y ShippyPro escriben el nombre distinto: el ensayo con datos reales
  lo dice antes de preprod; si hace falta, se afina la comparación.
- **Amazon «sin pedido concreto»** es una aproximación (la de hoy): entre que se crea la etiqueta y el Released, un IMEI tecleado
  puede cubrir un pedido que ya no está pendiente; se corrige solo en la lectura siguiente.
- **Las hojas son de otros:** si alguien cambia una columna, la lectura se rechaza y la píldora lo dice; nada se rompe.

## 13. Pendiente (no bloquea el plan)

- Qué son las líneas **NO, R1, A1 y M** del Simulador (y confirmar X y E).
- Si los **particulares** de la web (Particulares, BackiPhone) existen como clientes en el ERP.
- **Clave de ShippyPro:** una propia del ERP o la de `devoluciones`.
- Los nombres exactos de los campos de `GetPendingOrders` y los pedidos fusionados (`merged`).
- Si el SKU de las filas de Amazon en PEDIDOS 2026 es el de Amazon o el nuestro (§6.2 acepta los dos).

## 14. Fuera de alcance

- **PC Componentes** (no está en ShippyPro).
- **Asociar el IMEI en el ERP** y escribir en PEDIDOS 2026: 2ª entrega. Que los demás lean del ERP: 3ª.
- Etiquetas, OK final, Released: pieza 3. Sustituir el Simulador y su facturación: pieza 4.
- Limpiar en NSYS los «Pedidos realizados» que no se liberan.
- Los pedidos en el rastro del teléfono (los datos quedan guardados para hacerlo después).
- Ordenar por batería (su % no se apunta).

## 15. Ajustes del plan de la 0.9.26 (04/11)

1. **Numeración:** la 0.9.26 es esta primera versión y la 0.9.27, la de la sugerencia. Rama `dev_v0926_pedidos`.
2. **ShippyPro, comprobado con una llamada de solo lectura:** la respuesta es `{Result, Total, Orders}`. De cada pedido se usan
   `transaction_id`, `date` (Unix en segundos, como texto), `marketplace_id` (texto), `name`, `country` e
   `items[{sku, quantity (texto)}]`. Sin un User-Agent propio, ShippyPro responde 403.
3. **Hojas de Google:** cada lectura son tres llamadas `values.get`: la cabecera, la columna clave entera y las últimas
   `pedidos.filas` filas. Se piden sin formato y con las fechas como número de serie. La columna clave es «SKU» en PEDIDOS 2026 y
   «Order id» en el Simulador. La cabecera del Simulador se llama «Linea facturación».
4. **PEDIDOS 2026:** una fila es venta con la regla de `Deduccion_IMEIs.gs` y si tiene un IMEI de 15 cifras. **Una fila solo
   cubre un pedido del mismo día o de antes de la fila**: así no cuentan las filas viejas de pedidos enviados que NSYS no ha
   liberado.
5. **SKU desconocidos:** solo se apuntan los que parecen de teléfono (los que empiezan por «i» y una cifra).
6. **Una vuelta solo se aplica** si las tres fuentes tienen datos, de esa vuelta o de una anterior desde el arranque.
7. **Unidades estables:** una unidad es la pareja pedido + posición, así que su id no cambia entre lecturas.
8. **La línea de pedido** sale en las filas de trabajos abiertos de las seis listas del taller (`DescripcionMovil`), también en
   Pendientes. Con la lectura apagada no sale.
9. **Maestro:** tras el despliegue se recarga una vez si ningún producto tiene los SKU de los canales nuevos.
10. **«Ver rastro»** abre el detalle del IMEI, como el stock.
11. **`Pedido_lectura`** se poda a 30 días.
12. **En la API, `pide` y `movil` nunca son nulos.** Sin SKU conocido o sin móvil, van vacíos, y `movil` lleva `enNsys = false`.
13. **Un pedido de ShippyPro sin fecha válida se salta** (decisión del usuario, 04/11, tras la revisión de la Task 3): los demás se
    leen y su número queda en el mensaje de la lectura y en el log.
14. **Amazon: se cubren todos los pedidos posibles** (decisión del usuario, 04/11, tras la revisión de la Task 5): primero, qué
    pedidos quedan cubiertos (los más antiguos que se pueda); después, el móvil más avanzado al más antiguo entre esos repartos.
15. **Simulador: tras un abono, venta nueva** (decisión del usuario, 04/11): el abono cierra la venta de antes y la siguiente línea
    del IMEI es una unidad nueva; solo un IMEI en una unidad abierta del Simulador deja de usarse en PEDIDOS 2026.
16. **Posiciones con huecos:** cuentan todas las unidades de un artículo de ShippyPro y todas las líneas no BB de un pedido del
    Simulador, para que el id de cada unidad no cambie.
17. **Un fallo al cruzar o guardar se ve** (decisión del usuario, 04/11, tras la revisión de la Task 6): las lecturas de esa vuelta
    pasan a ERROR con el motivo, así que la píldora se pone ámbar y luego roja como con una fuente caída. El registro de lecturas
    se poda en todas las vueltas. Sin el ajuste de los marketplaces, ShippyPro se marca RECHAZADA (no se cierran sus pedidos).
18. **«C sin reparar» mira el grado que vale** (tras la revisión de la Task 7, 04/11, con la regla del usuario «solo el chasis pone
    la A»): un C al que el taller solo le cambió la batería (C → C100) sigue sin reparar.
19. **Plataformas de Amazon en PEDIDOS 2026** (ensayo con datos reales y decisión del usuario, 04/11): Amazon se escribe «AMZ»
    (y «Amazon» en unas pocas filas de junio); Amazon Stockroom, «FNSK» o «FSNK». «AMZ»/«Amazon» cubren solo los pedidos de
    Amazon y «FNSK»/«FSNK» solo los de Stockroom; ninguna de ellas la coge un cliente de web.
20. **La hoja se lee hasta la última fila que cuenta** (ensayo del 04/11): debajo de las ventas de PEDIDOS 2026 hay miles de filas
    vacías y un bloque de «INCIDENCIA …» con SKU, así que la última fila no es la última con SKU. La columna clave de PEDIDOS 2026
    es «Plataforma» y la última fila es la última venta; en el Simulador, la última con «Order id». Las 4000 filas acaban ahí.
21. **Una fila con «Pedido Amazon» es de su pedido o de ninguno** (ensayo y decisión del usuario, 04/11): PEDIDOS 2026 tiene una
    columna «Pedido Amazon» que se rellena al enviar. Si una fila de Amazon o Stockroom la trae y ese número es un pedido pendiente
    de su canal, la fila lo cubre a él como pedido concreto (sin la regla de la fecha); si no, no cubre ninguno (es de un pedido ya
    enviado cuyo móvil aún no está en Released). Las filas sin número siguen el reparto «sin pedido concreto». Con los datos del
    04/11, ~4 de las 9 unidades de Amazon cubiertas lo estaban con móviles de pedidos ya enviados.
22. **Amazon solo con «SKU (Amazon) con eSIM»** (revisión final y decisión del usuario, 04/11: «ahora Amazon diferencia qué es
    eSIM y qué es físico»): en el maestro, la fila de un eSIM lleva en «SKU (Amazon)» el SKU normal, así que con las dos columnas
    458 SKU de Amazon mezclaban eSIM y no eSIM (8 de los 18 pendientes del 04/11) y la eSIM del pedido salía al azar. El SKU de
    canal de Amazon y Stockroom es solo «SKU (Amazon) con eSIM» (rellena en todas las filas con SKU de Amazon).
23. **Revisión final del servidor (04/11):** el freno también mira la densidad de la cola (menos de la mitad de filas válidas →
    INCOMPLETA); el Simulador da INCOMPLETA si las filas leídas no llegan a la ventana (§4.3); los textos de las fuentes se cortan al
    ancho de su columna; una respuesta de ShippyPro sin lista de pedidos o con `Total` distinto falla (no cuenta como «0
    pendientes»).
