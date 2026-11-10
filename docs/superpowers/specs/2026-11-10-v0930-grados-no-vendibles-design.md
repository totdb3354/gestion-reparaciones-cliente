# 0.9.30 — los grados F, FD y RMA no cuentan como stock

Spec de diseño. Aprobada por el usuario en el brainstorming del 10/11/2026. Sin prototipo (no hay nada nuevo que ver: los
móviles pasan a pintarse como lo que ya «no cuenta como stock»). **Solo servidor**: sin web ni base de datos.

## 1. El problema

En «Stock de teléfonos» un móvil cuenta como stock vendible si NSYS lo tiene vivo (Available, InProcess o Repair) y en una
caja que cuenta (`Nsys_ubicacion.CUENTA_STOCK`). El grado no se mira. Por eso un móvil con grado RMA en la caja
«Funcionalidad pendiente» (que el ERP enseña como «Devolución») suma en el total de su modelo y sale en la tabla color × grado
como uno más.

La norma de la empresa es otra: lo que cuenta como stock depende de la caja **y del grado**. Hay grados que no se venden
estén donde estén.

## 2. Qué grados (comprobado el 10/11 con lo que se publica)

Se miró lo que el libro de stock publica en cada canal:

| Canal | F, FD y RMA | ID | n/a |
|---|---|---|---|
| Amazon | no existen como SKU | no existe | no existe |
| Web, caja de listos | a 0 | a 0 | no existe |
| Lista para profesionales («Stock simplificado») | no aparecen | aparece | aparece |

**F, FD y RMA no se ofrecen en ningún canal.** ID y n/a sí cuentan (la lista para profesionales los lleva). Es la misma lista
que la pieza de pedidos trata como no vendible al asignar (`GRADO_NO_VENDIBLE`).

El mismo día había en el espejo 29 móviles con esos grados en cajas que cuentan (15 F, 1 FD y 13 RMA): casi todos en
«Funcionalidad pendiente», unos pocos en «CAJA REP. OFICINA» y sin caja, ninguno en «CAJA LISTOS».

## 3. Decisiones (brainstorming del 10/11)

| Qué | Decisión |
|---|---|
| La regla | Un móvil que por su caja sería `STOCK` y cuyo grado es **F, FD o RMA** sale como **`NO_CUENTA`**. |
| Qué grado | **El que vale**: el de NSYS con lo del taller por encima (`LoQueVale.Valor.grade()`), el mismo que se ve en la tabla y el que usan los pedidos. Sin espacios a los lados y sin mirar mayúsculas («Fd» es FD). |
| Dónde | En el servidor, **al servir** el estado. `Nsys_movil.ESTADO` no cambia: sigue diciendo lo que da la caja. |
| A quién no toca | A los `DE_PEDIDO`, `NO_CUENTA`, `VENDIDO` y `BAJA`: se quedan como están. Solo cambia el que hoy sale `STOCK`. |
| ID y n/a | Siguen contando. |
| Cómo se ve | Como cualquier otro que no cuenta: fuera de los totales, de la tabla color × grado y de «Qué revisar»; solo con «Ver también lo que no cuenta como stock», en gris y con la marca «NO CUENTA». La píldora del grado, al lado, dice por qué. |
| Avisos | **Fuera de esta versión** (§7). |
| Número | **0.9.30** (decisión del usuario): sale antes que los pedidos, que esperaban como 0.9.30 y pasan a la **0.9.31**. Sus novedades pasan a `NOVEDADES-v0.9.31.md`. |

Descartado:

- **Mirar el grado al importar** (`EstadoMovil.de` con el grado, guardado en `Nsys_movil`): usaría el grado de NSYS y no el
  que vale, pide corregir las filas que ya hay y un cambio de grado en el taller no se vería hasta la siguiente exportación.
- **Hacerlo solo en la web** (`cuentaComoStock` mirando el grado): la ficha del IMEI, «Pegar varios IMEIs» y los pedidos
  seguirían diciendo otra cosa.
- **Cambiar el texto de la ubicación** para decir el motivo («Devolución · RMA, no cuenta como stock»): la fila ya lleva la
  píldora del grado junto a la marca «NO CUENTA». Así la versión no toca la web.

## 4. La regla, en un solo sitio

En `EstadoMovil` (paquete `nsys` del servidor):

- `NO_VENDIBLES`: el conjunto `F`, `FD`, `RMA`, público, para que los pedidos usen el mismo (§6).
- `conGrado(String grade)`: devuelve `NO_CUENTA` si el estado es `STOCK` y el grado, sin espacios a los lados y en
  mayúsculas, está en `NO_VENDIBLES`; si no, el mismo estado. Un grado `null` o vacío no cambia nada.

Es puro: no lee de la base de datos.

`GradoTaller.IGNORADOS` (F, FD, RMA e ID: los grados que ninguna pieza cambia) es otra lista para otra pregunta y no se toca.

## 5. Dónde se aplica

`StockTelefonosController` manda el estado en cuatro respuestas, y en las cuatro ya tiene calculado lo que vale el móvil
(`LoQueVale.Valor v`). En cada una, `m.estado().name()` pasa a ser `m.estado().conGrado(v.grade()).name()`:

| Ruta | Para qué lo usa la web |
|---|---|
| `GET /api/stock-telefonos` (`stock`) | La página: totales, columna lateral, tabla color × grado, «Qué revisar». |
| `GET …/buscar` (`buscar`) | El buscador de IMEI de la cabecera. |
| `GET …/{imeiSn}` (`ficha`) | La ficha del detalle del IMEI. |
| `POST …/comprobar` (`comprobar`) | «Pegar varios IMEIs». |

Lo demás de esas respuestas sigue saliendo del estado guardado: si está en la empresa (días, espera de revisión), la fecha de
venta y la ubicación. Un `NO_CUENTA` ya contaba como «en la empresa», así que nada de eso cambia.

El contrato de la API no cambia: `estado` sigue siendo uno de los cinco valores de siempre. `OpenApiContractTest` no se toca.

Lo que **no** cambia: el importador, el comparador, los eventos del rastro, «Por pasar a NSYS» y sus tarjetas, y
`NsysDAO.enEmpresa` (ya trae `STOCK`, `DE_PEDIDO` y `NO_CUENTA`).

## 6. Los pedidos (pasan a la 0.9.31, sin fusionar)

Su plan define otro conjunto `NO_VENDIBLES` (F, FD, RMA) para marcar `GRADO_NO_VENDIBLE`. Cuando esa rama traiga `main`, debe
usar `EstadoMovil.NO_VENDIBLES` y borrar el suyo. Lo hace la sesión de los pedidos; esta versión no toca su rama.

## 7. Mejora futura: avisos de caja (apuntado, sin diseñar)

Según almacén, cada uno de estos grados tiene su caja: **RMA en «Funcionalidad pendiente», F en «CF021» y FD en
«Desguace»**. Un aviso «este móvil no está en la caja de su grado» ayudaría a ordenar NSYS. Se deja para después: lo que
importa, no ofrecer lo que no se vende, ya lo protege el stock que se publica, y con esta versión el ERP deja de contarlo.

## 8. Pruebas

- `EstadoMovilTest`: `conGrado` con F, FD, RMA, «Fd», « rma » → `NO_CUENTA` desde `STOCK`; ID, n/a, A, A100, `null` y vacío
  → sigue `STOCK`; desde `DE_PEDIDO`, `NO_CUENTA`, `VENDIDO` y `BAJA`, el mismo estado con cualquier grado.
- `StockTelefonosControllerTest`, una por ruta: un móvil `STOCK` con grado RMA sale `NO_CUENTA` en `stock`, `buscar`, `ficha` y
  `comprobar`; uno con grado RMA en NSYS y corregido a «A» en el taller sale `STOCK`; uno «A» en NSYS y corregido a «F» sale
  `NO_CUENTA`; un `DE_PEDIDO` con grado F sigue `DE_PEDIDO`.
- `mvn test` entero (lleva el test de arranque).
- En preprod, con los datos copiados de producción: el total de la cabecera baja en los que haya (29 el 10/11) y el modelo
  de la captura que originó esto pierde su columna RMA salvo con el interruptor.

## 9. Despliegue

Solo servidor, sin migración y sin cambio de contrato: la web que hay sirve tal cual. Vuelta atrás, el servidor anterior.
Novedades para el taller: «Los móviles con grado F, FD o RMA ya no cuentan en el stock de teléfonos; se ven con “Ver también
lo que no cuenta como stock”».
