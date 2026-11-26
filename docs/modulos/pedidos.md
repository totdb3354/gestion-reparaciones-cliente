# Módulo «Pedidos» (Teléfonos › Pedidos)

Desde la versión 0.9.37 la tabla «Pedidos» del ERP sustituye a la hoja de Google «PEDIDOS 2026»: una fila por cada móvil
que hay que enviar, donde logística pone el IMEI del móvil que sale en cada pedido. Este documento cuenta el módulo entero.
El detalle de cada pieza está en sus specs (al final).

Los datos de este documento son de ejemplo. La operación real (máquinas, claves, rutas) no va en el repositorio.

## 1. Qué hace y quién lo usa

- **Logística** ve los móviles que hay que preparar, descarga el **Listado** de lo que hay que buscar en el almacén, pone el
  IMEI de cada móvil (a mano o con la pistola) y marca las incidencias (falta stock, cancelado…).
- **Atención al cliente** sincroniza con la web los IMEI de los pedidos de las webs (plugin de WordPress), que van a la factura.
- **El bot de NSYS** (automatización de stock, fuera del ERP) mueve en NSYS a «Pedidos realizados» los móviles que tienen
  IMEI en un pedido (y, con el ajuste encendido, los **reservados**), para que dejen de contar como stock; le dice al ERP qué
  ha movido y **devuelve a su caja** los que el ERP le pide (IMEI corregidos y reservas soltadas). Ver §5.
- **La reserva de IMEI** (desde la 0.9.48): el ERP aparta un móvil concreto para cada fila que espera, y logística lo ve como
  sugerencia al picar. Ver §2.4.
- Pantalla para SUPERTECNICO y ADMIN. El técnico no la ve.

## 2. De dónde salen los datos

```
ShippyPro ──(cada 5 min, solo lectura)──► ERP: crea las filas, las cierra, pone envío y etiqueta
Libros de ventas de Amazon (Google Drive) ──(cada 10 min, solo lectura)──► ERP: asocia cada móvil a su pedido de Amazon
Hoja PEDIDOS 2026 ──(una vez, a mano)──► ERP: carga del histórico y traspaso de los IMEI ya tecleados
NSYS (espejo del stock) ──► ERP: avisos de la celda del IMEI (qué es el móvil, dónde está)

ERP ──GET /api/pedidos/vendidos (clave)──► bot de NSYS ──► NSYS («Pedidos realizados»)
bot de NSYS ──POST /api/bot/movimientos (clave propia)──► ERP: lo que ha descontado y devuelto (tabla Bot_movimiento)
ERP ──GET /api/bot/devoluciones (clave propia)──► bot de NSYS ──► NSYS (de «Pedidos realizados» a su caja)
ERP ──GET (clave)──► plugin de la web ──► factura del pedido de WooCommerce
plugin de la web ──POST /api/pedidos/sincronizados (clave)──► ERP: «ya sincronizado» y quita «Falta por enviar»
```

El ERP **nunca escribe** en ShippyPro, en los libros ni en la hoja. **Las etiquetas se siguen generando e imprimiendo en
ShippyPro**, como antes del corte; el ERP solo lee que existen y si se han impreso (y la previsualización del panel solo descarga
el PDF). Generarlas desde el ERP es de la fase 2.

### 2.1 La lectura de ShippyPro (`LecturaFilas`, cada 5 minutos)

- Lee los pedidos pendientes (`GetPendingOrders`) de las cinco tiendas configuradas (Amazon, Amazon Stockroom y las tres webs) y
  crea **una fila por unidad** de cada línea de pedido nueva.
- Lee los envíos de los últimos 10 días (`GetShippedOrders`): pone a cada fila su fecha, transportista y seguimiento
  («Etiqueta generada») y, cuando la etiqueta se imprime, «Enviado».
- Una fila cuyo pedido **falta en dos lecturas buenas seguidas** sin haberse visto su envío pasa a **«Ya no está en ShippyPro»**
  (cancelado, juntado con otro en la misma caja o enviado con una etiqueta hecha a mano). Si el pedido vuelve, se reabre.
- Al final de cada lectura calcula la **fecha de salida** y el **país** de las filas vivas (§4).
- Si la lectura falla o trae pedidos sin fecha válida, no cierra nada; lo dice la píldora de fuentes.

### 2.2 El lector de los libros de Amazon (`LecturaLibros`, cada 10 minutos)

En Amazon, qué móvil va en qué pedido lo decide una persona en los libros de ventas (pestaña «Paso 1. Meter IMEI»). El ERP los lee
desde Drive cuando cambian (pedidos de los últimos 30 días) y:
- **asocia** cada IMEI a la fila de su pedido (la fila deja de estar «sin asociar» y enseña su número de pedido);
- si el IMEI estaba puesto en otra fila del mismo SKU, los **intercambia**;
- lo que no puede resolver va a la lista **«para revisar»** (el pedido no tiene hueco, el móvil ya salió en otro pedido, el pedido
  no está en el ERP, solo quedan filas canceladas).
- **El libro manda también en pedidos con etiqueta** (desde la 0.9.46). Si el pedido no tiene ninguna fila pendiente (sin etiqueta, sin confirmar y sin cancelar) y sí filas con la
  etiqueta hecha (impresa o no), o el pedido ya estaba asociado con otro IMEI, el ERP pone el IMEI del libro en la fila cuyo IMEI el
  libro no reconoce y la confirma. **Desde la 0.9.46.2 el libro manda siempre**: aunque el móvil no cuadre con el pedido (la celda
  avisa en rojo o naranja) y aunque en el libro corrijan un IMEI ya asociado. Con unidades de distinto SKU elige la fila con la que
  cuadra. Si ese IMEI estaba en otro pedido:
  - con etiqueta y también en el libro: ese otro pedido se queda sin IMEI y sin asociar hasta que llegue su línea del libro;
  - asociado, cancelado, o con la etiqueta **impresa** y fuera del libro: el otro pedido lo conserva y las dos celdas avisan «en otro
    pedido»;
  - con la etiqueta **sin imprimir** (aún no empaquetado) y fuera del libro, o pendiente: se intercambian (el otro pedido recibe el
    IMEI que tenía este); si tenía etiqueta, su historial dice «por el libro: comprobar el móvil».
  El IMEI quitado queda en el historial de la fila («libro»), para la futura devolución automática a stock.
- Un IMEI que el libro pone en varios pedidos (de los que tiene el ERP) se pone en todos; ninguno se lo quita a otro.
- Solo van a «para revisar» los imposibles: el pedido solo tiene filas canceladas, o no tiene fila libre (más IMEI que unidades).

### 2.3 La carga del histórico (una vez, en el corte)

`POST /api/pedidos/historico` (ADMIN) lee la hoja entera, borra el histórico anterior y, en una transacción: traspasa a las filas
pendientes del ERP los IMEI ya tecleados en la hoja (por número de pedido de Amazon; sin número, o en las webs, por SKU entre las
filas recientes) y carga el resto como **histórico** (filas grises, no editables). Repetirla da el mismo resultado.

### 2.4 La reserva de IMEI (`Reservas` + `Reparto`, desde la 0.9.48)

Al final de cada lectura de ShippyPro (cada 5 minutos), en tres pasos:

1. **Resuelve** las reservas de las filas que ya tienen IMEI: si se picó el reservado, «Confirmado por …»; si se picó otro,
   «Picó otro» y «Vuelve a stock» (la reserva queda **sustituida**).
2. **Suelta** las reservas que ya no valen, con su motivo en el historial: cancelada, marcada (en espera de stock, en revisión,
   pendiente de color), ya no está en ShippyPro, con la etiqueta impresa, enviada sin IMEI, el móvil no se encuentra (cajas de
   «Pendiente revisar…»), no disponible (vendido, de baja, caja que no cuenta), puesto en otra fila, o ya en «Pedidos realizados»
   sin el ajuste del bot encendido.
3. **Reparte** un móvil a cada fila viva sin IMEI ni reserva, y apunta «sin stock» a las que se quedan sin. Nunca reserva un Ms,
   un móvil puesto o reservado en otra fila, ni lo que una reserva soltada «seguramente salió» (fuera 30 días). Con un trabajo del
   taller abierto, el móvil solo vale si es exactamente el SKU del pedido.

**Orden del reparto** (el «Puesto N» del historial). Amazon (tabla 4.1 del spec):

| Puesto | Móvil |
|---|---|
| 1 | Mismo formato SIM, mismo color, grado del pedido o N/A |
| 2 | Mismo formato SIM, B o C (cualquier color: el chasis se cambia al subirlo a A) |
| 3 | El otro formato (eSIM ↔ física), mismo color, grado del pedido o N/A |
| 4 | El otro formato, B o C |
| 5 | Mismo formato, otro color, N/A |
| 6 | El otro formato, otro color, N/A |
| 7 | Mismo formato, otro color, A/A- (hay que transformarlo) |
| 8 | El otro formato, otro color, A/A- |

Webs (tabla 4.2): el SKU exacto del pedido (puesto 1) o, si el pedido es A o A100, un N/A (puesto 2, desde la 0.9.49: n/a, sin
caja o Devolución) o un B o C (puesto 3), siempre del mismo color y formato SIM. Un N/A por caja nunca vale como exacto. Dentro de cada puesto: la caja (Listos → Reparación / Pantalla rota → Pulir → Glass →
Devolución y «En revisión»), el grado, la recepción más antigua y el IMEI. Un móvil en «Funcionalidad pendiente» o sin caja
(«En revisión») cuenta como N/A. **Una reserva no se cambia por otra mejor** si aparece después: se queda hasta que se suelta.

En la tabla: el IMEI reservado sale tenue en la celda, los contadores «reservadas» y «sin stock» se pueden pulsar, y el desplegable
de recomendados trae primero el reservado de la fila («reservado para este pedido») y nunca los reservados de otras filas.

**Reservas y NSYS (`PEDIDOS_RESERVA_AL_BOT`).** Apagado, la reserva vive solo en el ERP (el móvil sigue a la venta). Encendido
(en producción desde el 26/11/2026), `/api/pedidos/vendidos` trae también los reservados de **todas** las filas vivas, sin la
ventana de días (desde la 0.9.48.1), y el bot los pasa a «Pedidos realizados» en su vuelta siguiente: dejan de venderse en el
Panel y las webs desde que se reservan. Si la reserva se suelta o se pica otro, el ERP pide devolverlo (§5).

| Versión | Cambio |
|---|---|
| 0.9.48 | La reserva, con el ajuste apagado |
| 0.9.48.1 | Los reservados llegan al bot sin la ventana de días de vendidos |
| 0.9.48.2 | Solo los reservados con caja en NSYS (los sin caja no se podían devolver) |
| 0.9.48.3 | Todos otra vez: lo que vino sin caja se devuelve a la localización `--------------` (§5) |
| 0.9.49 | Reserva web: un N/A (n/a, sin caja o Devolución) del mismo modelo, formato y color vale para un pedido A o A100, delante del B o C; acompaña a la regla «n/a como A» del ciclo de stock |
| 0.9.48.4 | (26/11 15:45, en producción) Los puestos 3, 4, 6 y 8 empiezan a funcionar: el reparto no reconocía un eSIM como el mismo modelo que el físico («15 PRO eSIM» frente a «15 PRO» en el maestro) |

## 3. La tabla

Columnas: Fecha · Plataforma (cliente) · SKU · IMEI · Pedido · Enviado · Fecha compra. Lo nuevo abajo; la tabla abre al final.

**Periodo** (por defecto, el último mes): la tabla enseña solo las filas con fecha dentro, como un filtro del Excel (desde la 0.9.38).
El servidor manda además las filas abiertas de otras fechas; con las pendientes (no las canceladas) la web monta un aviso amarillo
(«Hay 4 pendientes de antes del 20/11 (3 con retraso)») y «Ver» las enseña solas. Los contadores cuentan lo que se ve. El buscador no
mira el periodo: busca en todo el historial.

**Columna «Enviado»** (estado de la fila):

| Estado | Qué es |
|---|---|
| Sale hoy · Sale mañana · Sale lun 23/11 · Con retraso | Pendiente, con su fecha de salida (§4) |
| Falta por enviar | Envío parcial de una web: la etiqueta salió y este móvil no entró en la caja |
| Etiqueta generada | ShippyPro tiene la etiqueta; el móvil sigue en el almacén |
| Enviado | La etiqueta se imprimió |
| Ya no está en ShippyPro | El pedido desapareció de ShippyPro sin que se viera su envío |
| Cancelado | Puesto a mano con «Cancelar móvil» del panel: ese móvil no sale |
| (gris) | Histórico de la hoja |

**Celda del IMEI (desde la 0.9.45):** el IMEI y, al lado, en corto, todo lo que pasa con él, separado por «·» (orden: SIM,
modelo o capacidad, N/A, MS y el resto). Al pasar el ratón, una frase larga por pieza con el SKU del móvil.

| Pieza | Color del texto | Fondo de la celda | Cuenta en «no cuadran» |
|---|---|---|---|
| `modelo: 14`, `capacidad: 256GB`, `no se vende: F`, `SKU desconocido`, `no está en NSYS`, `en otro pedido` (otro móvil o no se vende; desde la 0.9.46.2) | rojo | rojo | sí |
| `color: Rosa`, `grado: B`, `grado: C`, `otro color o grado` (lo arregla el taller) | naranja | naranja | sí |
| `eSIM` (pide SIM física) / `SIM física` (pide eSIM) | naranja | ninguno | no |
| `N/A` (grado n/a; si el pedido acepta n/a, cuadra) | ámbar | amarillo claro | no |
| `MS`, `B por A`, `reparándose · Juan · glass Ana` | ámbar | ninguno | no |

Ejemplo: «eSIM · color: Rosa» (fondo naranja). Si un técnico lo tiene, se mira todo igual y además sale quién lo tiene («color: Rojo · reparándose · Juan»; desde la 0.9.46.1). En un pedido ya impreso el
aviso solo informa de qué móvil salió: no cuenta en «no cuadran» ni se valida (desde la 0.9.46.2). Un aviso de SKU (los rojos y
naranjas salvo «no está en NSYS» y «en otro pedido») se puede dar por bueno con **«Validar SKU»**; la fila validada conserva
«eSIM», «N/A» y «MS». La pistola guarda y salta a la siguiente fila vacía.

**Cajas de NSYS (desde la 0.9.45):** «Caja · Pulir», «Caja · Glass», «Caja · Reparación», «Caja · Pantalla rota» y «Caja · Listos»
(antes «Taller · …» y «Listos»), en Pedidos, Stock y el rastro del IMEI. Migración `sql/renombrar-cajas-nsys.sql` (vuelta atrás
`renombrar-cajas-nsys-atras.sql`).

**Marcas de la fila** (una como mucho, con nota): En espera de stock · En revisión · Pendiente de color, en el desplegable de la celda del IMEI y del panel. **Cancelado** va aparte: botón «Cancelar móvil» del panel, con confirmación y nota opcional; se deshace con «Quitar cancelación» (también con confirmación). Una fila cancelada no lleva otra marca ni «Falta por enviar».

**Amazon «sin asociar»:** la fila de Amazon no enseña su número de pedido hasta que el libro la asocia (§2.2); el IMEI se puede
poner igual en cualquier fila de ese SKU.

**Panel de la fila:** datos del pedido, envío, historial de la fila (quién puso o cambió el IMEI, marcas, etiqueta, sincronización),
previsualizar la etiqueta, marcar «Falta por enviar», «Cancelar móvil / Quitar cancelación».
Desde la 0.9.47, el historial también recoge los tres apuntes del bot de NSYS al devolver a stock un móvil que salió del pedido: «Devuelto a stock», «No se pudo devolver» y «No se devuelve solo (lo mueve una persona)», cada uno con su motivo (§5). Desde la 0.9.48, los de la reserva: «Reservado» (con su puesto), «Confirmado por …», «Picó otro» + «Vuelve a stock», «Reserva quitada» (con su motivo) y «Sin stock».

**Botones flotantes:** «↓ Al final» y «Fila seleccionada».

## 4. Reglas

- **Fecha de salida.** Amazon: la fecha «Latest ship» que da ShippyPro. Webs (desde la 0.9.44): salen **«Sale hoy»** desde el día en
  que entra el pedido (o el primer laborable, si entra en fin de semana o festivo) y hasta el laborable siguiente, que es su fecha de
  salida; después, «Con retraso». No se mira el tipo de envío. En sábado o domingo nunca sale «Sale hoy» (dice «Sale lun»). Laborable = de lunes a viernes y que no esté en la tabla `Calendario_festivo`
  (festivos de la Comunidad de Madrid; **hay que cargar los de cada año nuevo**, se publican en septiembre).
- **IMEI duplicado.** No se puede poner un IMEI que ya está en otra fila viva o con «Falta por enviar» (409 «Este IMEI ya está en el
  pedido X»). Sí si la otra fila está cancelada o ya se envió (un móvil devuelto puede volver a salir). Quitar la cancelación pasa la misma comprobación: si el IMEI ya está en otra fila que cuenta, 409 y la fila sigue cancelada.
- **Lo que cuenta como vendido** (lista del bot, `GET /api/pedidos/vendidos`): las filas con IMEI puesto en los últimos días, salvo
  las canceladas y las que ya no están en ShippyPro sin envío (pedido anulado con el IMEI ya puesto: ese móvil no salió). Excepción:
  una fila web así **cuenta si la web la ha sincronizado** (pedidos juntados en una sola caja: el móvil sí salió). Con
  `PEDIDOS_RESERVA_AL_BOT` encendido, también los reservados de las filas vivas (§2.4).
- **Envío parcial (webs).** Con la etiqueta generada, el móvil que no entra en la caja se marca «Falta por enviar»; la marca se quita
  al sincronizar la web o a mano con «Ya ha salido». Cancelar la fila quita «Falta por enviar» (apunte «al cancelar» en el historial) y no se puede marcar en una cancelada (409). En el despliegue de la 0.9.42 la migración sql/migracion-cancelado-sin-falta.sql limpió las filas viejas que tenían las dos (sin apunte).

## 5. El bot de NSYS: descontar, informar y devolver (desde la 0.9.47)

El bot vive en el repo de automatización (`stock/nsys_bot_completo.py`, PC de stock, cada 10 minutos). Lo que hace con el ERP:

- **Descontar**: pide `/api/pedidos/vendidos` (y las ventas del Panel), los pega en el filtro de IMEI de NSYS **en tandas de 900
  como mucho** (con 1.000 o más NSYS devuelve la tabla vacía sin error; arreglado el 26/11/2026) y los mueve a «Pedidos
  realizados». Nunca saca un móvil de las cajas de «Pendiente revisar…».
- **Informar**: manda al ERP (`POST /api/bot/movimientos`) cada `DESCONTADO` con su caja de origen y cada `DEVUELTO` con su
  resultado (OK, NO_APARECE, ERROR, VENDIDO_PANEL). Lo que no puede enviar lo guarda y lo reenvía en la vuelta siguiente.
- **Devolver** (interruptor `"devolver"` del bot, encendido desde el 26/11/2026): pide `/api/bot/devoluciones` y mueve cada móvil
  de «Pedidos realizados» al destino que le da el ERP.

**Qué pide devolver el ERP** (`ReglaDevoluciones`): un móvil que el bot descontó (DESCONTADO OK), que después salió de su fila
(otro IMEI encima, «Cancelar móvil» sin etiqueta impresa, reserva soltada o sustituida) y que el espejo de NSYS sigue viendo en
«Pedidos realizados» desde ese descuento, salvo que otra fila lo esté vendiendo o ya haya salido (vender gana a devolver). Va a
**la caja de la que lo sacó el bot**, solo si cuenta como stock (Listos, Reparación, Pantalla rota, Pulir, Glass, Funcionalidad
pendiente). Desde la 0.9.48.3, lo que salió **sin caja** («En revisión») vuelve a la localización `--------------` (NSYS no deja
mover a «sin localización»; el ERP la trata igual que sin caja), solo si el espejo confirma que entró desde «En revisión».

**No se devuelve solo** (apunte «No se devuelve solo», lo mueve una persona): sin caja de origen sin confirmar, caja que no cuenta
como stock (CF021, AFaltaRepuestos…), grado F/FD/RMA, reparado después del descuento, «Ya no está en ShippyPro» (mezcla
cancelados y enviados: comprobar si salió), reserva soltada con la etiqueta impresa o enviada sin IMEI. Tras tres ERROR seguidos
deja de pedirlo. Si está vendido en el Panel, el bot no lo devuelve (`VENDIDO_PANEL`).

**Vuelta atrás**: en el bot, `"devolver": false`; en el ERP, quitar `PEDIDOS_RESERVA_AL_BOT` del compose (los reservados ya
descontados se sueltan como «ya en Pedidos realizados» y se devuelven a mano).

## 6. El Listado

Botón «Listado»: un Excel «Listado dd-MM HHhmm.xlsx» con una pestaña por plataforma, cada una para imprimir en A4:
- **AMZ** y **FNSK**: dos tablas SKU × país con el total, «Para hoy» (salen hoy o ya van tarde) y «Pueden esperar».
- **Webs**: un bloque por web (Fonestockroom, Fonestore, BackiPhone), una fila por cliente, SKU y día de entrada, con las unidades.

Cuenta las filas **sin IMEI** y sin «Cancelado» que siguen pendientes o con «Falta por enviar».

**CSV de lo que se ve (0.9.40).** «Descargar CSV», en el menú del usuario, baja las filas que enseña la tabla: el periodo (o «Ver»
los pendientes de fuera, o la búsqueda), con los filtros de columna y el orden puestos. Columnas: Fecha, Plataforma, SKU, IMEI,
Pedido, Enviado, Fecha compra, Marca y Nota marca, con los textos de la pantalla. Sin filas a la vista, la opción sale deshabilitada.

## 7. Ajustes del servidor

Variables de entorno (valores fuera del repositorio):

| Variable | Para qué |
|---|---|
| `PEDIDOS_LECTURA_ACTIVA` | Enciende la lectura de ShippyPro (`true`/`false`) |
| `PEDIDOS_SHIPPYPRO_CLAVE` | Clave de la API de ShippyPro (solo lectura) |
| `PEDIDOS_SHIPPYPRO_CANALES` | `id_tienda:CANAL,…` de las cinco tiendas |
| `PEDIDOS_LIBROS_ACTIVA` | Enciende el lector de los libros de Amazon |
| `PEDIDOS_LIBRO_AMAZON`, `PEDIDOS_LIBRO_STOCKROOM` | Id de Drive de los dos libros |
| `PEDIDOS_HISTORICO_HOJA` | Id de la hoja para la carga del histórico |
| `PEDIDOS_VENDIDOS_CLAVE` | Clave de máquina del bot (`X-API-Key`) |
| `PEDIDOS_SINCRONIZADOS_CLAVE` | Clave de máquina del plugin de la web |
| `PEDIDOS_BOT_CLAVE` | Clave propia del bot para `/api/bot/movimientos` y `/api/bot/devoluciones` (la de vendidos no vale ahí) |
| `PEDIDOS_RESERVA_AL_BOT` | `true`: el bot descuenta también los reservados (§2.4). Por defecto apagado |

La cuenta de servicio de Google (la misma de NSYS) necesita lectura en los libros y en la hoja. Sin clave, las rutas de máquina
responden 404 (apagadas) o 401.

## 8. Cuando algo falla

| Síntoma | Qué mirar |
|---|---|
| Píldora de ShippyPro en ámbar o rojo | Última lectura (tabla `Pedido_lectura`); clave o red de ShippyPro. Nada se cierra mientras falle |
| No entran pedidos nuevos | `PEDIDOS_LECTURA_ACTIVA`, la píldora, el log del backend |
| Píldora de los libros en ámbar | Drive no responde o el libro no se comparte; se reintenta cada 10 min |
| Muchas filas «para revisar» | Pedidos de Amazon con etiqueta sin IMEI en el ERP; IMEI cambiados en el libro y no en el ERP |
| Fila «Ya no está en ShippyPro» que sí salió | Juntada con otro pedido o etiqueta hecha a mano; el stock lo cubre la sincronización de la web |
| El bot no mueve un móvil | Que la fila tenga IMEI y no esté cancelada; que el bot tenga la clave; el log del bot (`[AVISO] ERP`); que pegue en tandas («IMEIs en N tandas») |
| El bot no mueve nada en horas («Tabla vacia» con la lista entera) | La versión del bot: sin las tandas de 900, con 1.000 IMEI o más NSYS devuelve la tabla vacía |
| Un móvil no vuelve a su caja | El historial de la fila («No se devuelve solo» y su motivo); `Bot_movimiento` (DEVUELTO y su resultado); la vuelta del bot (`DEVUELTOS: N`) |
| Una reserva rara (otro color, N/A) | El «Puesto N» del historial y el orden de §2.4; la reserva no se mejora sola si aparece después un móvil mejor |
| La web no recibe un IMEI | La fila debe tener IMEI y ser de esa web; la clave del plugin; «Revisar y sincronizar» |

Cada pieza externa tiene su interruptor (vuelta atrás sin tocar el ERP): la lectura y los libros con su variable; el bot y el
plugin, quitando su clave; la devolución, con `"devolver": false` en el bot; la reserva al bot, quitando `PEDIDOS_RESERVA_AL_BOT`.

## 9. Specs y planes

Por orden: `2026-11-04-pieza2-pedidos`, `2026-11-10-pedidos-tabla`, `2026-11-11-pedidos-pantalla-v2`,
`2026-11-11-pedidos-vendidos-para-el-bot`, `2026-11-13-pedidos-envios-parciales`, `2026-11-13-pedidos-marcas`,
`2026-11-13-pedidos-amazon-asociacion`, `2026-11-16-pedidos-validar-sku`, `2026-11-18-pedidos-buscar-y-salida` (con el §8 de los
pedidos juntados), `2026-11-24-pedidos-celda-imei-y-cajas` (0.9.45), `2026-11-24-bot-devoluciones-design` (0.9.47, con sus planes 1-3),
`2026-11-24-pedidos-reserva-imei-design` (0.9.48) y, para después, `2026-11-20-pedidos-juntar` (en `docs/superpowers/specs/`, con su plan en
`docs/superpowers/plans/`). Rutas y DTO: `gestion-reparaciones-servidor/docs/api_contract.md`. Tablas:
`gestion-reparaciones-servidor/docs/schema.md`.
