# «Fuera de su caja»: los F, FD y RMA que no están en la caja de su grado

Spec de diseño. Aprobada por el usuario en el brainstorming del 10/11/2026, con prototipo
(`assets/2026-11-10-prototipo-fuera-de-su-caja.html`). **Servidor y web, sin base de datos.** Es la mejora que la 0.9.30
dejó apuntada (§7 de `2026-11-10-v0930-grados-no-vendibles-design.md`).

Número de versión: **0.9.31** (decisión del usuario el 11/11: sale antes que los pedidos, que pasan a la 0.9.32).

## 1. El problema

Desde la 0.9.30 un móvil con grado F, FD o RMA no cuenta como stock esté en la caja que esté. Según almacén, cada uno de
esos grados tiene además su caja en NSYS:

| Grado | Caja de NSYS | Cómo la enseña el ERP |
|---|---|---|
| F | CF021 | No vendible |
| FD | Desguace | no se trae (el móvil pasa a baja) |
| RMA | Funcionalidad pendiente | Devolución |

Hoy nadie ve los que están en otra caja: un F en «CAJA REP. OFICINA» o un RMA sin caja se quedan ahí hasta que alguien
tropieza con ellos. El 10/11 había 29 móviles con esos grados en la empresa; casi todos en «Funcionalidad pendiente» y unos
pocos en «CAJA REP. OFICINA» o sin caja.

Es una ayuda para ordenar NSYS. No protege ventas: eso ya lo hace la 0.9.30.

## 2. Decisiones (brainstorming del 10/11)

| Qué | Decisión |
|---|---|
| Dónde se ve | Una **quinta tarjeta** en «Por pasar a NSYS», **«Fuera de su caja»**, después de «Revisar a mano». Es donde ya mira quien corrige NSYS. |
| Sentido | Solo el F, FD o RMA que está fuera de su caja. El contrario (un grado vendible dentro de «CF021») queda fuera. |
| Qué grado | **El que vale** (NSYS con lo del taller por encima), sin espacios a los lados y sin mirar mayúsculas, como en la 0.9.30. |
| Sin caja | También sale, con «sin caja» como caja de hoy. |
| Quién no sale | El que tiene un **trabajo abierto del taller** y el **de pedido**: están fuera de su caja por un motivo. |
| Qué ofrece | La lista agrupada por la caja que toca y, en cada grupo, **«Copiar IMEIs (n)»**. |
| «Hecho» e «ignorar» | No lleva. La fila se va sola. |
| Dónde vive la regla | En el servidor, en código, junto a `EstadoMovil.NO_VENDIBLES`. |

Descartado:

- **Marca en la fila del stock:** esos móviles solo se ven con «Ver también lo que no cuenta como stock»; casi nadie la vería.
- **Grupo en «Qué revisar»:** esa lista la usa el taller para decidir qué revisar, no almacén para ordenar cajas.
- **La pareja grado → caja en `Nsys_ubicacion`** (columna nueva): pide migración para tres parejas que casi no cambian. Si
  almacén renombra una caja ya hay que tocar la tabla de cajas, y la importación avisa de la caja desconocida.
- **Calcularlo en la web** con la lista del stock: la web no recibe la caja de NSYS, solo el nombre del ERP, y la regla de
  grados quedaría repartida entre servidor y web.
- **«Hecho»:** pide guardar marcas en el servidor para una lista corta que se vacía sola cada 30 min.
- **Excel de cajas:** copiar los IMEIs basta para buscarlos en NSYS.

## 3. La regla

Un móvil sale en la tarjeta si cumple todo esto:

1. NSYS lo tiene en la empresa y su estado guardado es `STOCK` o `NO_CUENTA` (un `DE_PEDIDO` no sale).
2. Su grado que vale es F, FD o RMA.
3. Su caja de NSYS (`Nsys_movil.LOCATION`, tal cual) no es la de su grado. Sin caja cuenta como «no es la suya».
4. No tiene ningún trabajo abierto del taller.

Un FD que ya está en «Desguace» nunca sale: esa caja no se trae y el móvil es `BAJA`, fuera de los que están en la empresa.

La fila se va sola cuando la importación ve la caja buena, cuando el grado que vale deja de ser F, FD o RMA o cuando el
taller le abre un trabajo. Si el trabajo se cierra y sigue con ese grado fuera de su caja, vuelve a salir.

**Un caso a saber.** Si el taller pone F a un móvil que NSYS sigue teniendo con B, sale en «Cambio de grado» y aquí. Las dos
son verdad y son dos tareas distintas en NSYS. La fila lo dice (§5). Con las reglas de hoy no puede pasar: «Corregir…» solo
deja poner A, A-, B o C y ninguna pieza escribe F, FD ni RMA (revisión final, 11/11). Se deja preparado por si eso cambia.

## 4. Servidor

### 4.1 La regla, en un solo sitio

En `EstadoMovil`, junto a `NO_VENDIBLES`:

- `CAJA_DE_GRADO`: el mapa F → «CF021», FD → «Desguace», RMA → «Funcionalidad pendiente». En el orden en que salen los grupos.
- `cajaQueToca(String grado, String location)`: devuelve la caja que le toca si el grado (sin espacios, en mayúsculas) está
  en el mapa y `location` (sin espacios a los lados; `null` o vacía = sin caja) no es esa caja; si no, vacío.

Es pura: no lee de la base de datos y no mira el estado ni los trabajos. `NO_VENDIBLES` pasa a ser las claves del mapa, para
que las dos listas no puedan separarse.

### 4.2 La lista

Una función pura nueva en `TarjetasNsys`, `cajas(...)`, recibe los móviles en la empresa, lo que vale cada uno y los trabajos
abiertos por IMEI, y aplica §3. Usa lo mismo que ya calcula `stock()`: `dao.enEmpresa()`, `taller.valedorDeTodos()` y
`dao.trabajosAbiertos()` (cruzados por `NsysDAO.claveCruce`).

Cada fila (`FilaCajaNsys`):

| Campo | Qué es |
|---|---|
| `imeiSn` | El IMEI o número de serie. |
| `modelo`, `modeloMaestro`, `capacidad`, `esEsim` | Como en las otras tarjetas, para escribir «iPhone 14 eSIM 128GB». |
| `color` | El que vale. |
| `grado` | El que vale (F, FD o RMA, como esté escrito). |
| `gradoNsys` | El de NSYS, solo si es distinto del que vale; si no, `null`. |
| `caja` | La de NSYS hoy; `null` = sin caja. |
| `ubicacion` | Cómo llama el ERP a esa caja («Taller · Reparación», «En revisión»…). |
| `cajaQueToca` | «CF021», «Desguace» o «Funcionalidad pendiente». |

Orden: por caja que toca (el orden del mapa), dentro por caja de hoy (sin caja al final) y por IMEI.

### 4.3 Cómo llega a la web

La respuesta de las tarjetas de «Por pasar a NSYS» (`TarjetasPorPasar`) gana la lista `cajas`. El contrato cambia de forma
aditiva: una web anterior no la mira. Se actualiza el contrato de la API y su prueba.

Permisos: los mismos que el resto de «Por pasar a NSYS».

**Rendimiento.** Las tarjetas pasan a recorrer los móviles en la empresa, como ya hace la página del stock. Se mide en preprod
con los datos de producción: la petición tiene que seguir muy por debajo de los 15 s.

## 5. Web

Prototipo: `assets/2026-11-10-prototipo-fuera-de-su-caja.html`.

**La tarjeta.** Quinta y última; la fila de tarjetas pasa de cuatro a cinco columnas. Nombre «Fuera de su caja», borde de arriba
verde azulado, el número de móviles y un botón «Ver». Texto: «F, FD o RMA en una caja que no es la de su grado: llévalo a su caja
y cámbiale la «Location» en NSYS.» No lleva «hechos». Su número suma en el de la pestaña «Por pasar a NSYS».

**La lista.** Una tabla con las columnas IMEI, Modelo, Color, Grado y «Caja de hoy en NSYS».

- Va en grupos, uno por caja que toca. La cabecera del grupo dice «Llevar a «CF021»», «grado F · 3 móviles» y lleva el botón
  «Copiar IMEIs (3)». Un grupo sin móviles no sale.
- El IMEI abre el detalle del móvil, como en las otras listas.
- La caja de hoy sale como la escribe NSYS, con el nombre del ERP debajo en gris. Sin caja: «sin caja», en ámbar.
- Si `gradoNsys` viene, debajo del grado: «en NSYS aún B: también en «Cambio de grado»».
- Vacía: «No hay móviles F, FD o RMA fuera de su caja.»

**«Copiar IMEIs».** Copia los IMEIs del grupo, uno por línea, y avisa: «Copiado: 3 IMEIs para «CF021». Búscalos en NSYS y
cámbiales la «Location»».

**La ayuda** (el párrafo sobre la lista): «Móviles con grado F, FD o RMA que NSYS tiene en la empresa y fuera de la caja de su
grado: los F van en «CF021», los FD en «Desguace» y los RMA en «Funcionalidad pendiente». Copia los IMEIs de cada caja,
búscalos en NSYS y cámbiales la «Location». Cada fila se va sola cuando la importación (cada 30 min) ve la caja nueva. No salen
los que tienen un trabajo abierto del taller ni los de pedido.»

**Con un servidor anterior** (vuelta atrás): la web trata `cajas` ausente como lista vacía, como ya hace con `sinGrado`. La
tarjeta sale con 0.

Ficheros que cambian: `porPasar.ts` (la tarjeta nueva en `Tarjeta`, `TARJETAS`, `NOMBRE`, `EXPLICACION`, `AYUDA`, `cuentas`,
y los grupos y el texto a copiar como funciones puras), `TarjetasNsys.tsx` (cinco columnas y el borde), `ListasPorPasar.tsx`
(la lista nueva), `PorPasarNsys.tsx` (el botón y la copia) y el tipo de la respuesta en el cliente de la API.

## 6. Pruebas

Servidor:

- `EstadoMovilTest`, `cajaQueToca`: cada grado en su caja → vacío; cada grado en otra caja y sin caja (`null`, vacía) → su
  caja; «Fd» y « rma » valen; A, ID, n/a, `null` y vacío → vacío. `NO_VENDIBLES` sigue siendo F, FD y RMA.
- `TarjetasNsysTest`, `cajas`: un F en «CAJA REP. OFICINA» sale; con un trabajo abierto no sale; un `DE_PEDIDO` no sale; un RMA
  en NSYS corregido a «A» en el taller no sale; un «B» en NSYS corregido a «F» sale con `gradoNsys` = B; el orden de §4.2.
- La prueba del controlador de las tarjetas: la respuesta lleva `cajas`.
- `mvn test` entero (lleva el test de arranque y el del contrato).

Web:

- `porPasar.test.ts`: `cuentas` y el número de la pestaña con la tarjeta nueva; los grupos; el texto que se copia; sin `cajas`
  en la respuesta, lista vacía.
- La prueba de la lista: cabeceras de grupo, «sin caja», la nota de «Cambio de grado» y la lista vacía.

En preprod, con los datos copiados de producción: la lista coincide con una consulta directa al espejo (móviles vivos F, FD
o RMA fuera de su caja, quitando los de trabajo abierto) y el tiempo de la petición.

## 7. Despliegue

Servidor y web, sin migración. Contrato aditivo: la web anterior sirve con el servidor nuevo y la nueva con el anterior
(tarjeta a 0). Vuelta atrás, las versiones anteriores.

Novedades para el taller: «En «Por pasar a NSYS» hay una tarjeta nueva, «Fuera de su caja», con los móviles F, FD y RMA que
no están en la caja de su grado y un botón para copiar sus IMEIs.»

## 8. Fuera de esta versión

- El contrario: un móvil de grado vendible dentro de «CF021». Hay que confirmar con almacén que ahí solo van los F.
- La marca en la fila del stock y en la ficha del IMEI.
