# 0.9.22 pieza 4 — «Revisar a mano» rehecho: «Sin grado en NSYS», batería recordada y los que han vuelto

Diseño aprobado por el usuario el 2026-10-27, parte a parte, tras revisar con datos reales lo que salía en «Revisar a mano».
Va en la **0.9.22, antes de subirla a producción**, encima de la pieza 3 (`2026-10-27-v0922-descartar-y-avisos-design.md`). Spec
de las piezas 1 y 2: `2026-10-25-v0922-plantillas-nsys-design.md`. Sin prototipo: son retoques sobre filas y textos que ya existen
(decisión del usuario).

## 1. Por qué

Con la base de producción del 26/10, «Revisar a mano» tenía 76 avisos y casi ninguno pedía hacer algo en el ERP:

- **69 eran «Grado sin calcular» de un móvil en «n/a»**, casi todos por una batería: el ERP no puede ponerle el 100 a un grado
  sin letra. Pero lo raro no es la batería: **en el flujo normal todo móvil llega al taller ya graduado**. Lo que hay que ver es
  que un móvil sin grado ha pasado por el taller, sea la pieza que sea. Y el aviso no se iba aunque NSYS lo graduara después.
- **Un fallo del ERP**: si la batería nueva se guarda antes que el chasis en guardados distintos («✓ Guardar fila» fila a fila),
  el chasis pone la «A» pero sin el 100. El arreglo de la 0.9.21.1 (el chasis primero) solo vale dentro de un mismo guardado. Con
  la copia hay 7 móviles así, todos en «A» cuando deberían estar en «A100».
- **Más de la mitad eran de móviles ya vendidos**, que no tenemos. Pero algún vendido ha vuelto (una devolución o una garantía
  que NSYS aún no ha registrado) y el taller lo está trabajando: ese sí hay que verlo, también en Color y Grado.
- **Grados que no siguen `[Ms]<letra>[100]`**: unos se siguen usando (F, Fd/FD, RMA, ID: no vendible, desguace, bloqueo de
  operador, falla de Face ID) y no los debe tocar el taller; otros están en desuso (sufijos P, G y P+, sin ninguno recibido en
  2026); «A+» equivale a «A»; y hay una errata («MSA-» por «MsA-»).
- Dos avisos de tapa del 23/10, de antes del hotfix 0.9.21.1 (hoy la tapa no toca el grado), y la pastilla «se va con
  «Revisado» (queda quién y cuándo) o al corregirlo», que sobra.

## 2. Decisiones

| Tema | Decisión |
|---|---|
| Grados | Tres grupos: **con forma** (A+ se trata como A; «Ms» sin mirar mayúsculas), **ignorados** (F, Fd, FD, RMA, ID: nada los cambia y no avisan) y **como «n/a»** (n/a, vacío y cualquier otro código). |
| «Sin grado en NSYS» | Una fila por móvil en «Revisar a mano» si su grado es «como n/a» y el taller le ha hecho **cualquier trabajo** desde el **22/10/2026 12:49** (la 0.9.21 en producción). Se calcula al pedir las tarjetas. |
| Cierre | **Solo con grado**: se va sola cuando NSYS lo gradúa (o el taller le da letra con un chasis, o «Corregir…»). **Sin «✓ Revisado».** |
| Chasis en un «n/a» | Solo en Grado (el chasis ya le da la «A»): no sale en «Sin grado». |
| Batería recordada | Una batería puesta en un «como n/a» se recuerda (su aviso «Grado sin calcular», que deja de salir en la lista). Se aplica sola cuando el grado tiene letra **por NSYS o por un chasis**. Con **«Corregir…» manda lo que se escribe**: la ventana solo la recuerda. |
| Vendidos y bajas | Fuera de «Revisar a mano» (Color y Grado ya los quitaban), **salvo los que han vuelto**: trabajo del taller asignado un día posterior a la venta (o a la baja). Salen con una línea ámbar y no van en la plantilla de color. |
| Color sin deducir | Se queda como está (el modelo se busca por la combinación exacta «producto + grado» del maestro). |
| Restos de tapa | Un `UPDATE` en el guion de producción los marca revisados por el usuario a la hora de la subida. |
| Pastilla | Fuera. |
| Versión | 0.9.22, pieza 4, con sus ramas, encima de la pieza 3. Sin tablas nuevas ni migración. |

## 3. Grados (servidor)

`GradoTaller` clasifica el grado de NSYS (o el que vale con el taller) en tres grupos:

- **Con forma**: `(?i:Ms)?(A\+|A-|A|B|C)(100)?`. «Ms» sin mirar mayúsculas (`MSA-` es `MsA-`). Cuando el ERP escribe un grado
  nuevo, lo escribe como NSYS: `Ms` (si lo llevaba, escrito así), la letra y el `100`; **una «A+» se escribe «A»** (A+ con batería
  nueva → `A100`; con chasis → `A`; A+100 con batería reutilizada → `A`). La errata se corrige sola al escribir (`MSA-` con batería
  nueva → `MsA-100`).
- **Ignorados**: `F`, `Fd`, `FD`, `RMA`, `ID` (sin mirar mayúsculas). Ni el chasis ni la batería los cambian y no hay aviso.
- **Como «n/a»**: `n/a`, vacío y todo lo demás (los sufijos P, G y P+, códigos nuevos). El chasis lo deja en `A`; la batería no
  puede (no hay letra) y queda recordada (§5).

`EfectoTaller` deja de crear «Grado sin calcular» salvo en un caso: **una batería en un «como n/a»** (es el registro de la batería
recordada). Con un ignorado no hay aviso; la tapa ya no tocaba el grado.

## 4. «Revisar a mano» (servidor)

### 4.1 Lo que sale

`GET /api/stock-telefonos/por-pasar/tarjetas` sigue con sus listas y:

- **`revisar`** (los avisos) lleva solo **«Color sin deducir» y «No se pudo deshacer»** sin revisar y sin corrección a mano
  posterior (como hoy), **de móviles en la empresa o que han vuelto** (§6). «Grado sin calcular» deja de salir (`POR_REVISAR` =
  SIN_COLOR y SIN_DESHACER; «Revisado» sobre uno de grado da el 400 de siempre, «Ese aviso no es de «Revisar a mano»»).
- **`sinGrado`** (nueva, al final del objeto): una fila por móvil que cumple las tres cosas:
  1. NSYS lo tiene en la empresa (`EstadoMovil.enEmpresa()`) o **ha vuelto** (§6);
  2. **lo que vale** de su grado (NSYS más lo del taller vigente, `LoQueVale`) es «como n/a»;
  3. tiene **algún trabajo del taller** (cualquier fila de `Reparacion` de su IMEI, por `claveCruce`: asignación, reparación,
     glass, pulido…) con `FECHA_ASIG` desde el **22/10/2026 12:49 hora de Madrid** (`FECHA_ASIG` va en UTC, como en
     `NsysDAO.altas`).

  `FilaSinGradoNsys`: `imeiSn`, `modelo`, `modeloMaestro`, `capacidad`, `gradoNsys` (el grado de NSYS tal cual, «n/a», vacío o el
  código), `ultimoTrabajo` (el `ID_REP` del trabajo más reciente), `ultimoTrabajoEn` (su asignación, hora de Madrid), `categoria`
  (`Rastro.categoria`, como en `altas`), `bateriaIdRep`, `bateriaNueva`, `bateriaEn` (la batería recordada, §5; null si no hay),
  `vendidoEn`, `bajaEn`, `vueltoDesde` (§6; null si está en la empresa). Orden: del trabajo más antiguo al más reciente, y por IMEI.

  Con la copia del 26/10 serían unas 50 filas (menos las de vendidos que no han vuelto).

### 4.2 Lo que no cambia

«Color sin deducir» y «No se pudo deshacer» siguen con «✓ Revisado» y «Corregir…». Los avisos viejos de grado se quedan en el
rastro del IMEI. La lista de la 0.9.21 (`GET …/por-pasar`) no cambia.

## 5. La batería recordada (servidor)

### 5.1 Qué es

Un aviso «Grado sin calcular» (SIN_GRADO) **sin revisar**, cuya R… **sigue existiendo y es una batería** (por el SKU de su pieza) y
cuyo grado de partida (`DE`) es «como n/a». Nueva o reutilizada, la que diga hoy la pieza de su R… (`ES_REUTILIZADO`). Si un IMEI
tiene varias, **vale la más reciente** (por fecha y luego por id); al usarla se dan por usadas todas las suyas.

### 5.2 Cuándo se aplica

Cuando lo que vale del grado del móvil **tiene letra** (grupo «con forma»):

- **Al guardar un chasis** (en el mismo guardado o en otro): `EfectoTaller` recibe la batería recordada del IMEI. Si el chasis pasa
  el grado de «como n/a» a `A`, le aplica la batería: nueva → `A100`, reutilizada → `A`. El cambio es el del chasis (su R…). La
  batería se da por usada en la misma transacción. Arregla el caso de los 7.
- **Tras cada importación ACTUAL que acaba en OK** (junto al cierre de cambios, el repaso de «Hecho» y el de los descartes, y con
  la misma condición: solo si el cierre fue bien): para cada batería recordada de un móvil cuyo grado ya tiene letra, el ERP crea
  el cambio de grado que haga falta (la letra con `100` si es nueva, sin `100` si es reutilizada) **a nombre de la R… de la
  batería** (ORIGEN REPARACION, motivo el de la pieza), como un cambio más: sale en «Cambio de grado». Si ya lo tiene (p. ej. NSYS
  puso `A100`), no crea nada. Los 7 de arriba, ya en «A» por el chasis, pasan a `A100` en la primera importación.
- **Si el grado es un ignorado** (NSYS le puso F, Fd, RMA o ID): no cambia nada y la batería se da por usada.
- **Con «Corregir…» a mano**: si la corrección deja el grado con letra, manda lo que se escribe y la batería se da por usada
  en la misma transacción; si lo deja «como n/a» («Volver a lo de NSYS» con NSYS en «n/a») o solo corrige el color, sigue
  recordada.
- **Mientras siga «como n/a»**: sigue recordada.

Un fallo aquí no rompe la importación (se apunta en el log del servidor, como el repaso). Línea de log si usa alguna:
`NSYS: baterías recordadas usadas: N (cambios de grado: M)`.

### 5.3 Darla por usada

En el aviso: `REVISADO_EN` = la hora, `REVISADO_POR` = null y `A` = el grado que quedó (el que ya tenía si no hizo falta cambio).
En el rastro del IMEI sale como **«Batería resuelta por el ERP»** (tipo `TALLER_BATERIA_USADA`, con el grado que quedó), en lugar
de «revisado por».

### 5.4 Borrar o editar la R… de la batería

Si ya se aplicó, su cambio se deshace como cualquier otro (`PlanDeshacer`). Si estaba recordada, deja de contar (su R… ya no existe
o ya no es una batería). Si se edita a otra batería (nueva ↔ reutilizada), el guardado nuevo deja su propio aviso, que es el más
reciente.

### 5.5 «Corregir…»

`GET /api/stock-telefonos/{imei}/colores` (las opciones del diálogo) gana al final `bateriaIdRep`, `bateriaNueva` y `bateriaEn`
(la batería recordada; null si no hay).

## 6. Vendidos, bajas y los que han vuelto (servidor)

Un móvil **vendido** (`VENDIDO`) o **de baja** (`BAJA`) en NSYS **ha vuelto** si el taller tiene algún trabajo suyo (fila de
`Reparacion`) asignado **un día posterior** a:

- la venta: `soldDate` de su fila del espejo (va sin hora: el mismo día no cuenta);
- la baja: el día de `cambiado` de su fila del espejo (la última vez que NSYS la cambió).

Su `vueltoDesde` es la asignación (hora de Madrid) del **primer** trabajo posterior. En «Revisar a mano» (avisos y «Sin grado»),
**Cambio de color** y **Cambio de grado**:

- un vendido o de baja que **no** ha vuelto no sale;
- uno que ha vuelto **sale** con `vendidoEn` (la fecha de venta) o `bajaEn` (el día de la baja) y `vueltoDesde`.

`FilaRevisarNsys`, `FilaColorNsys` y `FilaGradoNsys` ganan al final esos tres campos (null en un móvil en la empresa).

**La plantilla de color** (`GET …/plantilla-color`) **no lleva** los que han vuelto: subirla pondría en stock un móvil que NSYS
da por vendido o de baja sin registrar antes la devolución. Cuentan en el número de su tarjeta (son trabajo pendiente). La
plantilla de altas no cambia.

Cuando NSYS registra la devolución, la importación lo pasa a stock y la fila sale como una más.

## 7. Web

- **«Revisar a mano»**: primero las filas «Sin grado en NSYS», después los avisos. Cada fila «Sin grado»:
  - IMEI (abre la ficha, como en las demás listas), modelo y capacidad;
  - «NSYS: n/a» (o el código; «NSYS: —» si está vacío);
  - «Último trabajo: <dd/mm/aaaa> · <Reparación | Glass | Pulido>» (con su R…);
  - si hay batería recordada: «Batería nueva del dd/mm (R…)» o «Batería reutilizada del dd/mm (R…)»;
  - botón **«Corregir…»** (el diálogo de siempre). Sin «✓ Revisado».
- El número de la tarjeta cuenta las filas «Sin grado» más los avisos.
- **Fuera la pastilla** «se va con «Revisado» (queda quién y cuándo) o al corregirlo».
- **Ayuda de la tarjeta**: «Sin grado en NSYS: móviles sin grado que han pasado por el taller desde el 22/10; se van solos cuando
  NSYS los gradúa o con «Corregir…». Los avisos de color y de deshacer se van con «Revisado» o al corregirlos.»
- **Los que han vuelto**, en «Revisar a mano», Cambio de color y Cambio de grado: una línea ámbar (11 px, el estilo del «Salió
  otra vez…» de la pieza 3) debajo del IMEI:
  - vendido: «Vendido en NSYS el dd/mm/aaaa · el taller lo tiene desde el dd/mm/aaaa: haz antes la devolución en NSYS»;
  - de baja: «De baja en NSYS desde el dd/mm/aaaa · el taller lo tiene desde el dd/mm/aaaa: míralo antes en NSYS».
- **Plantilla de color**: si quedan fuera filas que han vuelto, el aviso de la descarga añade «N han vuelto y no van: haz antes la
  devolución en NSYS».
- **«Corregir…»**: con batería recordada, debajo del grado, en gris: «Lleva batería nueva del dd/mm (R…): si quieres el 100,
  escríbelo» (o «Lleva batería reutilizada del dd/mm (R…): sin el 100»).
- **Rastro del IMEI**: `TALLER_BATERIA_USADA` → «Batería resuelta por el ERP · grado <grado>» (con la R…).

## 8. Datos y despliegue

- **Sin tablas ni migración nuevas.** La 0.9.22 sigue con las tres migraciones de las piezas 1 a 3.
- **Restos de tapa**: en el guion de producción (fuera del repositorio), tras subir el servidor, un `UPDATE` que marca revisados
  por el usuario, a la hora de la subida, los avisos `SIN_GRADO` **sin revisar** cuyo motivo empieza por «tapa »; tiene que tocar 2.
  Sustituye al paso «✓ Revisado a mano» que había.
- **Compatibilidad**: los campos nuevos van al final. La web de las piezas 1 a 3 con este servidor no ve las filas «Sin grado»
  (cuenta menos en «Revisar a mano») hasta que sube la web: se suben seguidas, como siempre. El servidor anterior funciona con los
  datos que deja este (avisos marcados y cambios normales).
- **Preprod**: piezas 3 y 4 juntas con la base de producción; comprobar «Sin grado» (~50 menos los vendidos que no han vuelto), los
  7 en `A100` en «Cambio de grado» tras la primera importación, los que han vuelto y el E2E.
- **Producción**: el guion de la 0.9.22 completa (tres migraciones, servidor, web y el `UPDATE` de la tapa). En horario.
- **Vuelta atrás**: servidor y web anteriores; los avisos de batería usados quedan revisados (no vuelven a salir en la lista vieja)
  y los cambios creados son cambios normales.

## 9. Pruebas

- **Servidor**:
  - `GradoTaller`: los tres grupos (A+, A+100, MsA, `MSA-`, F/Fd/FD/RMA/ID en mayúsculas o minúsculas, AG, A-P+, códigos raros,
    vacío); escribir sobre A+ y sobre la errata.
  - `EfectoTaller`: batería en «como n/a» con aviso y en ignorado sin aviso; chasis sobre «como n/a» con batería recordada
    (nueva → A100, reutilizada → A) en el mismo guardado y en otro; chasis en un ignorado sin cambio.
  - Las filas «Sin grado»: desde el 22/10 12:49 (justo antes no cuenta), cualquier categoría de trabajo, vendido fuera, vuelto
    dentro con sus fechas, ignorados fuera, con letra del taller fuera, con la batería recordada correcta (la más reciente).
  - `revisar`: sin «Grado sin calcular», sin vendidos que no han vuelto.
  - La batería tras la importación: letra sin 100 → cambio a nombre de la R… de la batería; ya con 100 → nada; ignorado → usada;
    sigue «como n/a» → recordada; R… borrada → no cuenta; varias → la más reciente y todas usadas; solo tras un cierre bueno; un
    fallo no rompe la importación.
  - «Corregir…» del grado da la batería por usada; las opciones del diálogo traen la batería.
  - Los que han vuelto en Color y Grado, y fuera de la plantilla de color; el mismo día de la venta no cuenta.
  - El rastro con `TALLER_BATERIA_USADA`; contrato OpenAPI.
- **Web**: las filas «Sin grado» (con y sin batería, código y vacío), el número de la tarjeta, la pastilla quitada, la ayuda, la
  línea ámbar en las tres listas, el aviso de la plantilla, el texto de «Corregir…» y el del rastro.
- **E2E en preprod** (nunca en producción): con un móvil «n/a» de la copia que NSYS tenga en la empresa, crear por la API una
  asignación y una batería nueva → sale en «Sin grado» con la batería; un chasis después → sale en «Cambio de grado» en `A100` y
  deja «Sin grado»; limpiar (borrar las reparaciones y comprobar que el móvil queda como estaba).

## 10. Fuera de alcance

- Buscar el modelo por el producto sin el grado cuando el maestro no tiene la combinación (decidido: se queda como está).
- «✓ Revisado» o «descartar» en las filas «Sin grado».
- Avisar en el formulario del taller al guardar un trabajo en un móvil sin grado.
- Tocar los avisos viejos (salvo los 2 de tapa) o la lista de la 0.9.21.
