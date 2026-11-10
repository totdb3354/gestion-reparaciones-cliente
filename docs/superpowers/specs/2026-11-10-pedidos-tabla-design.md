# «Pedidos» como la hoja: el ERP sustituye a la hoja de pedidos (cimientos)

Spec de diseño. Aprobada por el usuario en el brainstorming del 10/11/2026, con prototipo
(`assets/2026-11-10-pedidos-tabla-prototipo.html`, datos inventados). Servidor + web + base de datos. Sin número de versión
todavía: se le pone al cerrarla.

Sustituye a la pantalla «Pedidos» de la pieza 2 (`2026-11-04-pieza2-pedidos-design.md`), que solo leía y enseñaba lo pendiente
y **no llega a producción**. De aquella se conservan las ideas que siguen valiendo (qué SKU acepta un pedido, cuándo cuadra un
móvil, la recomendación de §7) y se cita donde hace falta.

## 1. El problema y el objetivo

Hoy los pedidos de Amazon y de las webs se apuntan a mano en una hoja de cálculo: alguien descarga lo pedido en cada canal,
apunta una fila por móvil (fecha, plataforma, SKU) y, cuando se coge el móvil, su IMEI. La hoja no comprueba nada al teclear.

El objetivo es que ese trabajo se haga en el ERP **con el mismo flujo y una pantalla lo más parecida posible a la hoja**, porque
es a lo que está acostumbrada la gente: una tabla, una fila por móvil, todo el historial a la vista. El ERP quita lo que se
copia a mano y añade ayudas; no cambia la manera de trabajar.

Esta spec cubre **los cimientos**: la tabla, de dónde salen las filas, el IMEI con sus recomendaciones y avisos, el histórico y
el corte. Lo que hoy depende de la hoja fuera del ERP (§9) se diseña después y condiciona la fecha del corte.

## 2. Decisiones del usuario (10/11)

1. El ERP **sustituye** a la hoja; no escribe en ella. En el corte se carga su histórico y la hoja deja de usarse.
2. Solo la hoja de pedidos. La hoja de ventas directas (con facturación) queda fuera: es más compleja y sensible.
3. La tabla lleva **solo Amazon (dos cuentas) y las webs**. Las ventas directas y las incidencias no se apuntan en el ERP.
4. Las filas las crea el ERP desde ShippyPro. **Lo único que se teclea es el IMEI.** Si se queda corto, se abrirá la edición.
5. El IMEI se pone a mano porque hay que buscar el móvil; al pulsar la celda salen los IMEI recomendados.
6. Un IMEI que no cuadra **se guarda y se avisa** en la celda; no se bloquea ni se pregunta.
7. **Historial entero**: las filas enviadas no desaparecen.
8. Se carga **todo** el histórico de la hoja, tal cual.
9. La usan SUPERTECNICO y ADMIN. Un rol de logística vendrá más adelante.
10. Módulo nuevo (no una evolución del de la pieza 2).

## 3. La pantalla

«Teléfonos › Pedidos». Una tabla con cabecera fija:

| Columna | De dónde sale | Se edita |
|---|---|---|
| Fecha | El día en que el ERP ve el pedido por primera vez (hora de Madrid) | no |
| Plataforma | `AMZ` y `FNSK` para las dos cuentas de Amazon; en las webs, el nombre del cliente del pedido | no |
| SKU | El del artículo del pedido, tal cual | no |
| IMEI | Lo pone la persona | **sí** |
| Pedido | El número de pedido del canal (lo crea el canal; el ERP solo lo lee) | no |
| Fecha compra | La fecha del pedido en el canal | no |

- **Una fila por móvil:** un artículo con cantidad 3 son tres filas iguales.
- **Orden:** como la hoja, lo más antiguo arriba y lo nuevo abajo; la pantalla se abre al final.
- **Filas enviadas:** en gris, sin edición. No se borran.
- **Arriba:** el título con el recuento (filas, sin enviar, sin IMEI), la píldora de las fuentes (ShippyPro y NSYS: cuándo se
  leyeron y si fallaron), un buscador (pedido, plataforma, SKU o IMEI) y el filtro «Solo sin IMEI».
- **Refresco:** la tabla se sondea como las demás vistas; una fila nueva entra sola por abajo.

### 3.1 La celda del IMEI

Al pulsarla (solo en filas sin enviar) se abre un desplegable con:

- un campo para escribir o leer con el lector un IMEI de 15 cifras (Enter lo guarda);
- **los recomendados** para ese SKU (§5), del mejor al peor, cada uno con su IMEI, su caja y por qué está en ese puesto;
- «Quitar el IMEI», si lo tiene, y «Cerrar».

Guardar es inmediato, sin confirmación. Si otra persona cambió esa fila entretanto, gana el último y la tabla lo enseña al
refrescar (el registro de §4.2 guarda los dos cambios).

### 3.2 Los avisos

La celda se pinta según lo que el ERP sabe del IMEI puesto (reglas de §6.4.3 de la spec de la pieza 2, con lo que vale en el
taller, no con lo que dice NSYS):

- **sin color:** cuadra; al lado, la caja del móvil;
- **ámbar, con el motivo:** vale con aviso (es un Ms, «B por A» en Amazon, o está en el taller con un trabajo abierto);
- **naranja, con el motivo:** no cuadra (otro modelo, capacidad, eSIM, color o grado, diciendo qué es el móvil; grado que no se
  vende; no está en NSYS; ya está en otra fila sin enviar).

Los avisos **no se guardan**: se calculan al servir la tabla, así que cambian solos cuando cambia el móvil (por ejemplo, cuando
el taller le pone el color que pide el pedido). En las filas enviadas y en las del histórico no se calculan.

## 4. Los datos

### 4.1 `Pedido_fila`

Una fila por móvil pedido. Es la fuente de verdad.

| Campo | Qué es |
|---|---|
| `ID_FILA` | Clave. El orden de la tabla es `FECHA`, `ID_FILA` |
| `FECHA` | La de la columna Fecha |
| `PLATAFORMA` | Texto de la columna |
| `SKU` | Tal cual llega |
| `IMEI` | Nullable. En el histórico, el texto de la hoja tal cual (puede no ser un IMEI) |
| `PEDIDO` | Número de pedido del canal; nullable en el histórico |
| `FECHA_COMPRA` | Nullable en el histórico |
| `ORIGEN` | `SHIPPYPRO` o `HISTORICO` |
| `CANAL` | `AMAZON`, `AMAZON_STOCKROOM`, `DISTRIBUIDORES`, `PARTICULARES` o `BACKIPHONE`; null en el histórico. Decide qué columna de SKU del maestro se usa |
| `POSICION` | Qué unidad del pedido es (1, 2, 3…); con `CANAL` y `PEDIDO` identifica la fila entre lecturas |
| `ENVIADA_EN` | Null = viva. Las del histórico nacen cerradas |

Única por (`CANAL`, `PEDIDO`, `POSICION`) en las de ShippyPro.

### 4.2 `Pedido_fila_cambio`

Quién puso, cambió o quitó cada IMEI: fila, usuario, fecha, IMEI anterior e IMEI nuevo. No se enseña en esta entrega; es el
rastro para cuando alguien pregunte.

### 4.3 `Pedido_lectura`

Como en la pieza 2 (§5.3): cada lectura de ShippyPro con su resultado, para la píldora. Se borran a los 30 días.

## 5. La recomendación

Como §7 de la spec de la pieza 2, sin cambios de fondo:

- **Entra** un móvil del mismo modelo, capacidad y eSIM que el SKU de la fila, que cuente como stock (lo que «Stock de
  teléfonos» cuenta, grados no vendibles fuera) y que **no esté puesto en otra fila sin enviar**.
- **Orden:** 1) igual que el pedido; 2) en el taller y acabará igual; 3) hay que transformarlo (otro color o grado). Dentro de
  cada grupo, el que menos trabajo necesita y, a igualdad, el más antiguo en stock.
- Se enseñan los cinco primeros. Si no hay ninguno, lo dice.

Los textos de cada puesto se afinan con el usuario al construirlo (pendiente suyo del 10/11).

## 6. Cómo se llenan las filas

Una tarea cada 5 minutos lee los pedidos pendientes de ShippyPro (la misma llamada de solo lectura de la pieza 2):

- un marketplace que no está en el ajuste de canales se ignora;
- un artículo cuyo SKU no es de teléfono (no lo conoce el maestro en la columna de su canal) no crea fila; si parece de
  teléfono, se apunta en la lectura como SKU desconocido y sale en la píldora. Al comparar, «Ultramarino» es «Azul»;
- un pedido nuevo crea sus filas; uno ya visto no se toca (el IMEI puesto se conserva);
- un pedido que deja de estar pendiente cierra sus filas (`ENVIADA_EN`). Solo se cierra con una lectura completa y correcta:
  si ShippyPro falla o responde a medias, no se cierra nada y la píldora lo avisa.

Ajustes: los de la pieza 2 que siguen haciendo falta (encender la tarea, la clave de ShippyPro y los canales). Los de las hojas
desaparecen.

## 7. La API

- `GET /api/pedidos/filas` — las filas con el aviso de cada IMEI. Paginada desde el final (las últimas N y «cargar anteriores»)
  y con búsqueda en el servidor, para no pasar del límite de 15 s con el histórico entero.
- `GET /api/pedidos/filas/{id}/recomendados` — los candidatos de una fila, al abrir su celda.
- `PUT /api/pedidos/filas/{id}/imei` — `{imei}` (15 cifras o null). `409` si la fila ya está enviada; `400` si no son 15 cifras.
  Con `Idempotency-Key`, como las demás escrituras.
- `GET /api/pedidos/fuentes` — la píldora.

Todas, SUPERTECNICO y ADMIN; el TECNICO recibe `403`.

## 8. El histórico y el corte

- **Carga:** una herramienta de un solo uso lee la hoja entera y crea una fila `HISTORICO` cerrada por cada fila con datos, con
  sus textos tal cual (también ventas directas e incidencias). Se puede repetir en preprod; antes de cargar, vacía lo cargado.
- **El día del corte:** 1) se deja de teclear en la hoja; 2) se carga el histórico; 3) se enciende la tarea: los pedidos
  pendientes en ShippyPro entran como filas vivas; 4) para esos pedidos, las filas de la hoja que ya tenían IMEI y aún no
  estaban enviadas **no se cargan como histórico**: su IMEI pasa a la fila viva del mismo pedido (o, sin número de pedido, del
  mismo canal y SKU, del más antiguo al más nuevo), para no dejar una fila vieja con IMEI y otra nueva vacía.
- **Ensayo:** el corte entero se ensaya en preprod con una copia de la hoja del día antes de hacerlo en producción.

## 9. Fuera de esta entrega

- Lo que hoy lee la hoja fuera del ERP (procesos automáticos de vendidos y de comprobación de SKU, y las pestañas que dependen
  de ella). **Sin resolverlo no se puede cerrar la hoja**: se inventaría y se diseña antes de fijar la fecha del corte.
- Ventas directas, incidencias, añadir o borrar filas a mano y editar otras columnas.
- Las demás columnas de la hoja. Ideas del usuario para después: un atajo para asignar el trabajo desde la fila, columnas de
  «quién lo tiene» y «localización», y observaciones con valores fijos (revisar, cancelado, «no se encuentra el móvil»).
- La línea de pedido bajo el IMEI en Asignaciones y Pendientes (pieza 2 §9.3): se vuelve a plantear sobre estas filas.
- El rol de logística.

## 10. Qué pasa con lo construido en la pieza 2

Las ramas `dev_v0926_pedidos` no se fusionan. El módulo nuevo se escribe aparte y toma de ellas, copiándolo, lo que sirve tal
cual: el cliente de ShippyPro, qué acepta cada SKU por canal, las reglas de si un móvil cuadra y las columnas de SKU de canal
del maestro (con su migración). No se llevan: la lectura de las hojas, el cruce por reparto, las tablas `Pedido` y
`Pedido_unidad` ni la pantalla. El cambio de nombre de «Pedidos» de piezas a «Compras» sí se mantiene.

## 11. Pruebas

- **Servidor:** la tarea (pedido nuevo, repetido, cantidad N, cierre solo con lectura buena, SKU desconocido), el IMEI
  (poner, cambiar, quitar, fila enviada, registro de cambios), los avisos uno a uno, la recomendación (quién entra, orden, no
  repetir un móvil ya puesto), la carga del histórico y el traspaso de IMEI del corte, y los roles.
- **Web:** la tabla, el desplegable, los colores y motivos, el filtro y el buscador.
- **E2E en preprod:** el técnico no la ve; el supertécnico pone, cambia y quita un IMEI en una fila viva y lo deja como estaba.
- **Ensayo del corte** con una copia real de la hoja, fuera del repositorio.
