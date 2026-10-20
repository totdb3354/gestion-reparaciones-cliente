# 0.9.18.2 — Un móvil vendido o dado de baja no está «En revisión»

Fecha: 2026-10-20. Estado: diseño aprobado por el usuario (20/10); plan en
`docs/superpowers/plans/2026-10-20-v09182-vendido-no-en-revision.md`.
Arreglo aparte del **servidor**, sacado del tag `v0.9.18` (hoy `main` del servidor = `v0.9.18`). **Servidor + limpieza de
datos** en las tablas `Nsys_*`: sin cambios de esquema ni de web. Funciona con la web 0.9.18, 0.9.18.1 y 0.9.19.

## 1. Qué pasa

- NSYS **vacía la caja** (`Location`) cuando un móvil pasa a `Released` o a `Removed`. Comprobado en el Items del 20/10:
  ningún móvil vendido en los últimos 13 meses conserva la caja; solo la conservan ventas más antiguas.
- El ERP traduce la caja vacía como «sin caja» = **«En revisión»** (`Nsys_ubicacion`, `LOCATION` vacía). Esa regla es para
  los móviles que siguen en la empresa; con un móvil vendido o de baja no significa nada.
- Consecuencias:
  - **Rastro:** `Comparador.cambios` apunta un `UBICACION` «… → En revisión» a la vez que la `VENTA` o el `ESTADO`. Sale un
    «Pasa a En revisión» falso en cada venta y en cada baja, también en todo el histórico (desde dic. 2023).
  - **Ficha:** `Nsys_movil.UBICACION` queda en «En revisión». La ficha enseña la píldora «En revisión» y «Pendiente de
    revisar desde …» en un móvil vendido (`Revision.pendienteDesde` solo mira la ubicación).
  - **«Pegar varios IMEIs»:** un vendido o una baja sale con «En revisión» en la columna Ubicación y en su copia para
    Excel. Con el arreglo, la columna queda vacía (la web ya lo trata así; no cambia).
  - **No afecta al stock:** `GET /api/stock-telefonos` solo devuelve móviles en stock, de pedido o que no cuentan. El
    buscador de IMEI no enseña la ubicación de un vendido o de una baja.
  - **Devoluciones, de rebote:** en NSYS una devolución es el mismo lote, que vuelve de `Released` (sin caja) a un `Status`
    vivo. Primero pasa un tiempo sin caja y después va a «Funcionalidad pendiente», que la distingue como devolución. Hoy
    es el evento falso de la venta el que pone fecha a la espera: la vuelta sin caja no cambia la caja de NSYS (vacía →
    vacía) y no da ningún `UBICACION`. Además, la vuelta a una caja se apunta con un «Desde: En revisión» falso. Por eso
    la ficha de una devolución dice «Pendiente de revisar desde» **el día de la venta**, y no el de la vuelta.
- **Caso visto el 20/10:** un móvil vendido (de «Listos» a «Pedidos realizados») se transforma en el taller (chasis de otro
  color y batería). En NSYS se da de baja su lote y se vuelve a dar de alta en un lote nuevo, ya en «Pedidos realizados»,
  con el producto nuevo; horas después pasa a `Released`. Salen dos «Pasa a En revisión» falsos (la baja del lote viejo y
  la venta del nuevo) y la ficha del lote nuevo dice «En revisión · Pendiente de revisar desde hoy · Vendido el 20/10».

## 2. Regla nueva

Con un `Status` que **no es vivo** (todo lo que no es `Available`, `InProcess` ni `Repair`: `Released`, `Removed`, `RMA`,
`ReturnedToSupplier`…), **una caja vacía no es una ubicación**: NSYS la vacía al cerrar el móvil, no lo manda a revisión.

- **`Comparador.construir`:** `UBICACION` null y `UBICACION_ULTIMA` la de antes (como ya se hace con «Pedidos realizados»).
  El `ESTADO` no cambia (`VENDIDO` o `BAJA`).
- **`Comparador.cambios`:** no apunta `UBICACION` cuando la caja pasa a vacía con un `Status` no vivo. Sí lo apunta si la
  caja cambia a una de verdad, aunque el `Status` no sea vivo (por ejemplo, a «Desguace»).
- **Vuelta de un vendido o de una baja** (`VENTA_DESHECHA`, o un `ESTADO` de un `Status` no vivo a uno vivo):
  - si vuelve a una caja, el `UBICACION` sale sin «Desde» (`DE` null), así no cuenta como revisión ni dice «Desde: En
    revisión»;
  - si vuelve sin caja, queda en «En revisión», como cualquier móvil vivo sin caja; no hay `UBICACION`, porque la caja de
    NSYS no cambia;
  - **`Revision.pendienteDesde`:** la vuelta del lote empieza un periodo pendiente nuevo. Si el móvil está ahora en «En
    revisión» o en «Devolución» y no ha pasado por una caja de verdad desde que volvió, espera desde el día de la vuelta.
    Funciona igual con los eventos que ya están guardados.
  - **Flujo de una devolución con la regla nueva:** vendido → «Vendido el …» (sin «En revisión»); vuelve sin caja →
    «En revisión · Pendiente de revisar desde» el día de la vuelta; «Funcionalidad pendiente» → «Devolución», con la misma
    fecha; a una caja → «Revisado (devolución) el …».
  - La regla del 19/10 no cambia: un vendido que vuelve directo a una caja no cuenta como revisión (las devoluciones
    pasan por «Funcionalidad pendiente»).
- **Una fila nueva** que llega ya con un `Status` no vivo y sin caja: `ENTRADA` sin ubicación.
- **Un móvil vivo sin caja** sigue en «En revisión», igual que hoy.
- **Ficha (defensa):** «Pendiente de revisar desde …» solo con el móvil en la empresa (stock, de pedido o que no cuenta).
  Así tampoco sale en un vendido antiguo que conserva una caja pendiente («Funcionalidad pendiente»).
- **El histórico** usa el mismo `Comparador`. Ya está terminado y no se vuelve a pasar: lo que dejó guardado se arregla con
  el SQL de §3.

## 3. Datos (preprod y producción)

Dos SQL en `sql/` del servidor: `recuento-nsys-vendido-en-revision.sql` (solo lee) y `limpieza-nsys-vendido-en-revision.sql`
(en una transacción, en `READ COMMITTED` para no frenar a la importación). Se ejecutan después de desplegar el backend y
mejor justo después de una importación. (Ajustado tras la revisión final: el estado del lote ya no se deduce del esqueleto,
el «Desde» falso se quita en cualquier lote cerrado y la última caja sale también del «Desde».)

**Estado del lote en un momento:** solo cuentan los cambios de estado con hora (`VENTA`, `VENTA_DESHECHA`, `ESTADO`); la
venta del esqueleto (solo el día) no, porque el esqueleto la apunta en cualquier fila con fecha de venta. Es el `A` del
último cambio hasta ese momento; si no lo hay, el `DE` del primero después; si tampoco, el `Status` de hoy.

1. **Destinos falsos:** los `UBICACION` y las `ENTRADA` hacia la ubicación de la caja vacía (la de `Nsys_ubicacion` con
   `LOCATION` vacía), con `A_NSYS` vacío, de un lote **cerrado en ese momento**. Los `UBICACION` se borran; las `ENTRADA`
   (un lote que llegó ya vendido o de baja) se quedan sin ubicación de destino.
2. **«Desde» falsos:** `DE` = NULL en los `UBICACION` con `DE` = esa ubicación de un lote **cerrado justo antes** (el `DE`
   del cambio de estado de la misma exportación o, si no lo hay, el estado de antes). Son las vueltas de un vendido o de
   una baja a una caja y los cambios de caja de una baja (a «Desguace», por ejemplo), que si no contarían como revisión.
3. **`Nsys_movil`:** donde el `Status` no es vivo y la caja está vacía, `UBICACION` = NULL y `UBICACION_ULTIMA` = la
   última ubicación del ERP del rastro: el `A` del último `UBICACION` o `ENTRADA` o, si no lo tiene, su `DE` cuando es una
   ubicación del ERP (los destinos falsos se miran sin su `A`); NULL si no la hay. Hoy esa columna dice «En revisión» por
   el mismo fallo, y saldría como caja si la venta se deshace y el móvil vuelve a «Pedidos realizados» (pasa a menudo:
   cambios de IMEI en un pedido, ventas anuladas). Lo mismo en los de pedido con «En revisión» de última caja, sacado del
   rastro; si no hay rastro, se deja. El código nuevo deja tal cual la última caja de una fila sin ubicación, así la
   siguiente importación no vuelve a guardar decenas de miles de filas.

El recuento se pasa antes y después: destinos falsos (por tipo, con o sin cambio de estado a la vez), «Desde» falsos,
filas de `Nsys_movil` que cambian, móviles hoy pendientes de revisar tras una vuelta y dos comprobaciones (falsos de un lote
vivo hoy sin ninguna vuelta después: 0; móviles vivos con fecha de venta). En producción, copia a mano antes
(`backup-erp.sh`).

## 4. Despliegue

- Solo el backend: nginx y MariaDB no se tocan; unos 5 s de corte. Después, el SQL de limpieza.
- Si entra un Items entre el despliegue y el SQL, no pasa nada: el importador nuevo corrige él mismo esas filas de
  `Nsys_movil` (con `CAMBIADO` de ese momento, que no se enseña) y el SQL solo borra los eventos.
- Primero preprod: recuento, despliegue, limpieza, recuento, la ficha y el rastro de un móvil vendido de la copia, y las E2E
  `stock-telefonos`. Después producción con el mismo guion.
- **Vuelta atrás:** servidor `728f2f9` + `up -d --build backend`. Los eventos borrados solo vuelven desde la copia de antes.

## 5. Pruebas

- `ComparadorTest`:
  - venta con la caja vaciada: solo `VENTA`; ubicación null y la última, la de antes;
  - baja (`Removed`) con la caja vaciada: solo `ESTADO`;
  - el mismo vendido sin caja en el fichero siguiente: no se vuelve a guardar (mismo contenido);
  - venta deshecha a una caja: `UBICACION` sin `DE`; a «Pedidos realizados»: la última caja, la de antes de la venta;
  - venta deshecha sin caja: «En revisión», sin `UBICACION`;
  - fila nueva `Released` sin caja: `ENTRADA` sin ubicación;
  - sin cambios: un móvil vivo que se queda sin caja sigue dando «→ En revisión»; un `Removed` que pasa a «Desguace» sigue
    dando su `UBICACION`.
- `RevisionTest`:
  - devolución con el código nuevo (venta, vuelta sin caja, «Funcionalidad pendiente»): pendiente desde el día de la
    vuelta; al pasar a una caja, «Revisado (devolución)»;
  - devolución con los eventos de hoy, ya limpiados por el SQL (sin el falso de la venta, con la vuelta a «Devolución»):
    pendiente desde el día de la vuelta;
  - una vuelta seguida de una caja de verdad y otra vez «sin caja»: pendiente desde la segunda vez, no desde la vuelta.
- Histórico: una venta con la caja vaciada no apunta `UBICACION` (en `ComparadorTest` con `Modo.HISTORICO`, que es lo
  que llama `HistoricoNsys`).
- `StockTelefonosControllerTest`: la ficha de un vendido que conserva una caja pendiente no trae `pendienteRevisionDesde`.
- Tras la revisión final: un vendido que conservaba la caja y la vacía en otra exportación; un Status vacío (de vendido a
  sin Status no rompe la importación); una fila guardada con el fallo no da «Desde: En revisión»; la ficha de una baja
  guardada con el fallo; una devolución con los eventos ya limpios.
- Los dos SQL, en preprod con el recuento antes y después y un ensayo con `ROLLBACK`.

## 6. Fuera de este arreglo

El lote en cada evento del rastro, el cambio de producto y grado al volver a entrar en otro lote y el «Revisado» heredado
del lote anterior. Dependen de cómo case el «SKU del taller» (0.9.19) los lotes de un mismo IMEI; están apuntados en
`plan-futuro.md`.
