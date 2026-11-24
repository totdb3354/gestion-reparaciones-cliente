# Asociación de Amazon: el libro de ventas manda también en pedidos con etiqueta — diseño

Fecha: 2026-11-24. Estado: aprobado en conversación, pendiente de revisar este texto.
Versión: la siguiente libre **después** de la de «textos y colores de la celda del IMEI» (`2026-11-24-pedidos-celda-imei-y-cajas-design.md`),
porque usa su comparación del móvil con el pedido. Prevista 0.9.45; el número se pone al sacarla. Servidor + web (solo los textos
de la lista «para revisar») + migración aditiva (columna `DETALLE` en `Pedido_asociacion_revisar`, para decir qué es el móvil).
Base: asociación IMEI → pedido de Amazon (`2026-11-13-pedidos-amazon-asociacion-design.md`, `AsociacionAmazon`), «lo que vale»
(`LoQueVale`), aviso del IMEI (`AvisoImei`).

## Para qué

En producción (24/11) la lista «para revisar» de la asociación tenía 46 líneas, casi todas de pedidos de Amazon enviados el 23/11 en los
que el libro de ventas y el ERP dicen IMEIs distintos. Comprobado con dos casos reales (302-1492985-8237123 y 408-4289966-0182715): se
lee un móvil en el ERP, al empaquetar se mete otro (el primero tenía un fallo y fue al taller, o se cruzan dos iguales) y el cambio solo
se apunta en el libro. El ERP no se corrige porque la asociación **nunca toca una fila con etiqueta**.

Consecuencias: el ERP da por vendidos móviles que siguen en casa, el bot de stock los mueve en NSYS a «Pedidos realizados» (NSYS con
menos stock del real) y la lista «para revisar» crece cada día.

El libro es lo único que se apunta con la caja delante: **el libro manda**, también en pedidos con etiqueta (generada o impresa),
con comprobaciones que paran los errores del propio libro.

## Qué cambia

### 1. Antes de aplicar: el libro no puede contradecirse

- Un IMEI que el libro pone en **dos pedidos distintos** (entre las líneas válidas de la pasada): todas sus líneas van a «para revisar»
  con el motivo nuevo `REPETIDO_EN_LIBRO` y no se aplica ninguna. Sustituye a la regla de hoy «si un IMEI sale varias veces, vale la
  última» **solo cuando los pedidos son distintos**; el mismo par repetido sigue contando una vez (las repeticiones, descartadas).
- Las líneas sin pedido o sin IMEI de 15 cifras se descartan, como hoy.

### 2. Reglas para cada línea «IMEI X → pedido B»

1. **X ya está en una fila de B:** se confirma (o «ya estaba»), como hoy. Vale aunque la fila tenga etiqueta, como hoy.
2. **B tiene alguna fila sin etiqueta y sin confirmar** (no cancelada): las reglas 2 y 3 de hoy **sin cambios** (intercambio con la
   fila de otro pedido sin etiqueta; fila libre; `YA_SALIO`, `CANCELADO`, `SIN_HUECO`).
3. **B no tiene ninguna y sí tiene filas con etiqueta sin confirmar** (`ENVIO_FECHA`, impresa o no; en Amazon un envío es del pedido
   entero) — **lo nuevo**:
   - **Comprobación del móvil X**, con «lo que vale» (NSYS + cambios del taller vigentes) y la misma comparación que el aviso de la
     celda del IMEI (`AvisoImei` tras la versión de la celda). **Regla: va a «para revisar» lo que en la celda cuenta en «no
     cuadran»** (columna «Cuenta en no cuadran = sí» de la tabla de esa spec): **no está en NSYS**, **modelo**, **capacidad**,
     **color**, **grado**, **otro color o grado** (combinación que no existe), **grado que no se vende** y **SKU desconocido**. «En
     otro pedido» no cuenta aquí: es justo lo que resuelve el libro. **No** paran (no cuentan en «no cuadran»): eSIM / SIM física
     (en las dos direcciones, aunque su texto sea naranja), n/a, Ms, B por A, ni que lo tenga un técnico. Motivos nuevos en «para
     revisar»: `NO_EN_NSYS` y `OTRO_MODELO` (cualquiera de los demás; el texto dice cuál y qué es el móvil, p. ej. «el pedido es
     i16PRO128NegroA y el móvil es i16PROeSIM256NegroNA»).
   - **Fila destino de B:** la primera por posición que no esté confirmada, no esté cancelada y cuyo IMEI actual (o ninguno) no sea
     un IMEI que el libro pone en B en esta pasada. Si no hay ninguna, `SIN_HUECO` (como hoy).
   - **Si X está en una fila de otro pedido O:**
     - O cancelada → `CANCELADO` (como hoy); O **confirmada** por el libro o por el histórico → `YA_SALIO` (no se le quita: es otra
       palabra del libro, o de antes de la ventana de 30 días, contra esta).
     - O sin confirmar **con etiqueta** y **O está en el libro de esta pasada** → O se queda **sin IMEI y sin asociar** (el ERP no
       inventa qué fue en O; su línea del libro lo pondrá).
     - O sin confirmar con etiqueta y **O no está en el libro de esta pasada** (p. ej. un pedido de hace más de 30 días: móvil
       devuelto y revendido) → `YA_SALIO` y no se toca nada (decisión de Imad, 24/11: su línea no llegaría nunca y O perdería el IMEI).
     - O sin confirmar **sin etiqueta** (pendiente) → intercambio como hoy: O recibe el IMEI que tenía la fila de B.
   - **Se escribe:** la fila de B recibe X y queda confirmada (`ASOCIADA_EN`, origen `LIBRO`). Se quita «En espera de stock» como hoy.
4. **No cambia:** nunca se toca una fila cancelada ni el histórico; una fila cancelada no se confirma.

Los `SIN_HUECO` se reintentan al final de la pasada como hoy (hasta 3 vueltas mientras alguno avance).

### 3. Rastro en el historial (para la devolución automática futura)

Cada IMEI que el libro **quita** de una fila con etiqueta (el que tenía la fila de B, o el de O) queda en `Pedido_fila_cambio` con
`IMEI_ANTERIOR`, `IMEI_NUEVO` (X o nulo), `TIPO = IMEI` y `DETALLE = LIBRO`, sin usuario. Con eso, la futura devolución automática
del bot (otra sesión; ver la reserva de IMEI: segunda lista ERP → bot, por destino, «vender gana a devolver») podrá sacar los móviles
a devolver: quitados por el libro, en ninguna otra fila y en «Pedidos realizados» en NSYS. **Esta versión no añade pantalla ni ruta
para eso.**

El panel de la fila enseña esos apuntes como los demás cambios de IMEI («sin usuario · libro»).

### 4. Al subir

- Se fuerza una pasada de los dos canales (`UPDATE Pedido_asociacion_pasada SET LIBRO_MODIFICADO = NULL`). Corrige los pedidos del
  23/11. Simulación del 24/11 sobre la lista de entonces: de 42 pedidos de una unidad, 41 se corrigen solos y queda el de capacidad
  (306-0903515-6120315); el 302-0312195-2108359 puede quedar como `OTRO_MODELO` por una errata del SKU en NSYS («i14PRO512AOro»).
- Los móviles que el bot ya movió mal a «Pedidos realizados» **no** vuelven con esta versión (lo hará la devolución automática).

## Efectos en otras partes (comprobar en las pruebas)

- **Vendidos para el bot** (`GET /api/pedidos/vendidos`): el IMEI nuevo entra (su último cambio de IMEI es el del libro) y el viejo
  deja de salir. Correcto: el bot no deshace nada, solo deja de recibir el equivocado.
- **Aviso de la celda, stock de Pedidos y buscador:** se recalculan con el IMEI nuevo; las filas corregidas quedan asociadas y se
  pueden buscar por número de pedido.
- **Duplicados** (`SQL_DUPLICADO`): una fila enviada que recibe X no choca con nada, porque X se quita de O en la misma transacción.

## Lo que no entra

- Aviso de «enviados sin asociar» (pedidos impresos que el libro no tiene): otra versión si hace falta.
- Lista o pantalla de «Devolver a stock» y la devolución automática del bot: otra sesión.
- Pedidos web: no tienen libro; nada cambia.

## Pruebas

- `AsociacionAmazonTest`: con etiqueta (generada sin imprimir e impresa) se pone X y se confirma; varias unidades (elige la fila cuyo
  IMEI no está en el libro); IMEI en dos pedidos → los dos `REPETIDO_EN_LIBRO` y nada cambia; el mismo par repetido no es repetido;
  `NO_EN_NSYS`; `OTRO_MODELO` por capacidad y por color; eSIM ↔ SIM física pasa en las dos direcciones; un cambio de color del
  taller vigente hace que pase; O con etiqueta queda sin IMEI ni asociar; O pendiente intercambia como hoy; O confirmada → `YA_SALIO`;
  O cancelada → `CANCELADO`; apuntes con `DETALLE = LIBRO`; las reglas sin etiqueta, como hoy (las pruebas de hoy siguen verdes).
- Vendidos: tras una corrección, sale X y no el viejo.
- Preprod con el libro real (copia de producción): forzar la pasada y comparar con la simulación.
