# Módulo «Pedidos» (Teléfonos › Pedidos)

Desde la versión 0.9.37 la tabla «Pedidos» del ERP sustituye a la hoja de Google «PEDIDOS 2026»: una fila por cada móvil
que hay que enviar, donde logística pone el IMEI del móvil que sale en cada pedido. Este documento cuenta el módulo entero.
El detalle de cada pieza está en sus specs (al final).

Los datos de este documento son de ejemplo. La operación real (máquinas, claves, rutas) no va en el repositorio.

## 1. Qué hace y quién lo usa

- **Logística** ve los móviles que hay que preparar, descarga el **Listado** de lo que hay que buscar en el almacén, pone el
  IMEI de cada móvil (a mano o con la pistola) y marca las incidencias (falta stock, cancelado…).
- **Atención al cliente** sincroniza con la web los IMEI de los pedidos de las webs (plugin de WordPress), que van a la factura.
- **El bot de vendidos** (automatización de stock, fuera del ERP) mueve en NSYS a «Pedidos realizados» los móviles que tienen
  IMEI en un pedido, para que dejen de contar como stock.
- Pantalla para SUPERTECNICO y ADMIN. El técnico no la ve.

## 2. De dónde salen los datos

```
ShippyPro ──(cada 5 min, solo lectura)──► ERP: crea las filas, las cierra, pone envío y etiqueta
Libros de ventas de Amazon (Google Drive) ──(cada 10 min, solo lectura)──► ERP: asocia cada móvil a su pedido de Amazon
Hoja PEDIDOS 2026 ──(una vez, a mano)──► ERP: carga del histórico y traspaso de los IMEI ya tecleados
NSYS (espejo del stock) ──► ERP: avisos de la celda del IMEI (qué es el móvil, dónde está)

ERP ──GET /api/pedidos/vendidos (clave)──► bot de vendidos ──► NSYS («Pedidos realizados»)
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

### 2.3 La carga del histórico (una vez, en el corte)

`POST /api/pedidos/historico` (ADMIN) lee la hoja entera, borra el histórico anterior y, en una transacción: traspasa a las filas
pendientes del ERP los IMEI ya tecleados en la hoja (por número de pedido de Amazon; sin número, o en las webs, por SKU entre las
filas recientes) y carga el resto como **histórico** (filas grises, no editables). Repetirla da el mismo resultado.

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

**Celda del IMEI:** vacía, verde (cuadra), ámbar (aviso) o naranja (no cuadra: no está en NSYS, ya está en otro pedido, otro
modelo/capacidad/eSIM, otro color o grado, grado no vendible, SKU desconocido, en el taller…). Un aviso de SKU se puede dar por
bueno con **«Validar SKU»**. La pistola guarda y salta a la siguiente fila vacía.

**Marcas de la fila** (una como mucho, con nota): En espera de stock · En revisión · Pendiente de color, en el desplegable de la celda del IMEI y del panel. **Cancelado** va aparte: botón «Cancelar móvil» del panel, con confirmación y nota opcional; se deshace con «Quitar cancelación» (también con confirmación). Una fila cancelada no lleva otra marca ni «Falta por enviar».

**Amazon «sin asociar»:** la fila de Amazon no enseña su número de pedido hasta que el libro la asocia (§2.2); el IMEI se puede
poner igual en cualquier fila de ese SKU.

**Panel de la fila:** datos del pedido, envío, historial de la fila (quién puso o cambió el IMEI, marcas, etiqueta, sincronización),
previsualizar la etiqueta, marcar «Falta por enviar», «Cancelar móvil / Quitar cancelación».
Desde la 0.9.47, el historial también recoge los tres apuntes del bot de NSYS al devolver a stock un móvil que salió del pedido: «Devuelto a stock», «No se pudo devolver» y «No se devuelve solo (lo mueve una persona)», cada uno con su motivo (sin caja, caja o grado que no se vende, reparado después).

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
  una fila web así **cuenta si la web la ha sincronizado** (pedidos juntados en una sola caja: el móvil sí salió).
- **Envío parcial (webs).** Con la etiqueta generada, el móvil que no entra en la caja se marca «Falta por enviar»; la marca se quita
  al sincronizar la web o a mano con «Ya ha salido». Cancelar la fila quita «Falta por enviar» (apunte «al cancelar» en el historial) y no se puede marcar en una cancelada (409). En el despliegue de la 0.9.42 la migración sql/migracion-cancelado-sin-falta.sql limpió las filas viejas que tenían las dos (sin apunte).

## 5. El Listado

Botón «Listado»: un Excel «Listado dd-MM HHhmm.xlsx» con una pestaña por plataforma, cada una para imprimir en A4:
- **AMZ** y **FNSK**: dos tablas SKU × país con el total, «Para hoy» (salen hoy o ya van tarde) y «Pueden esperar».
- **Webs**: un bloque por web (Fonestockroom, Fonestore, BackiPhone), una fila por cliente, SKU y día de entrada, con las unidades.

Cuenta las filas **sin IMEI** y sin «Cancelado» que siguen pendientes o con «Falta por enviar».

**CSV de lo que se ve (0.9.40).** «Descargar CSV», en el menú del usuario, baja las filas que enseña la tabla: el periodo (o «Ver»
los pendientes de fuera, o la búsqueda), con los filtros de columna y el orden puestos. Columnas: Fecha, Plataforma, SKU, IMEI,
Pedido, Enviado, Fecha compra, Marca y Nota marca, con los textos de la pantalla. Sin filas a la vista, la opción sale deshabilitada.

## 6. Ajustes del servidor

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

La cuenta de servicio de Google (la misma de NSYS) necesita lectura en los libros y en la hoja. Sin clave, las rutas de máquina
responden 404 (apagadas) o 401.

## 7. Cuando algo falla

| Síntoma | Qué mirar |
|---|---|
| Píldora de ShippyPro en ámbar o rojo | Última lectura (tabla `Pedido_lectura`); clave o red de ShippyPro. Nada se cierra mientras falle |
| No entran pedidos nuevos | `PEDIDOS_LECTURA_ACTIVA`, la píldora, el log del backend |
| Píldora de los libros en ámbar | Drive no responde o el libro no se comparte; se reintenta cada 10 min |
| Muchas filas «para revisar» | Pedidos de Amazon con etiqueta sin IMEI en el ERP; IMEI cambiados en el libro y no en el ERP |
| Fila «Ya no está en ShippyPro» que sí salió | Juntada con otro pedido o etiqueta hecha a mano; el stock lo cubre la sincronización de la web |
| El bot no mueve un móvil | Que la fila tenga IMEI y no esté cancelada; que el bot tenga la clave; el log del bot (`[AVISO] ERP`) |
| La web no recibe un IMEI | La fila debe tener IMEI y ser de esa web; la clave del plugin; «Revisar y sincronizar» |

Cada pieza externa tiene su interruptor (vuelta atrás sin tocar el ERP): la lectura y los libros con su variable; el bot y el
plugin, quitando su clave.

## 8. Specs y planes

Por orden: `2026-11-04-pieza2-pedidos`, `2026-11-10-pedidos-tabla`, `2026-11-11-pedidos-pantalla-v2`,
`2026-11-11-pedidos-vendidos-para-el-bot`, `2026-11-13-pedidos-envios-parciales`, `2026-11-13-pedidos-marcas`,
`2026-11-13-pedidos-amazon-asociacion`, `2026-11-16-pedidos-validar-sku`, `2026-11-18-pedidos-buscar-y-salida` (con el §8 de los
pedidos juntados) y, para después, `2026-11-20-pedidos-juntar` (en `docs/superpowers/specs/`, con su plan en
`docs/superpowers/plans/`). Rutas y DTO: `gestion-reparaciones-servidor/docs/api_contract.md`. Tablas:
`gestion-reparaciones-servidor/docs/schema.md`.
