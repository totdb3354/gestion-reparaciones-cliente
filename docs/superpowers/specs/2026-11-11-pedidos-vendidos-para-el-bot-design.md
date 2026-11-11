# Pedidos: la lista de IMEI vendidos para el bot de stock

- **Fecha:** 11/11/2026
- **Estado:** enfoque aprobado por el usuario el 11/11 (el bot pide al ERP); pendiente de plan de implementación
- **Amplía:** `2026-11-10-pedidos-tabla-design.md` (§9: lo que lee la hoja fuera del ERP)
- **Bloquea el corte:** sin esto no se puede dejar de teclear en la hoja de pedidos

## 1. El problema

Un proceso automático externo al ERP (el «bot de stock») aparta en NSYS, cada media hora, los móviles vendidos: los mueve a la
ubicación de pedidos realizados para que dejen de contar como stock y de publicarse. Hoy saca los IMEI de dos sitios: las
ventas directas (que no cambian) y la hoja de pedidos, de la que lee solo cuatro columnas: Fecha, Plataforma, IMEI y SKU.

Cuando los IMEI de Amazon y de las webs se tecleen en el ERP, la hoja dejará de tenerlos. Si el bot no los recibe de otro
sitio, esos móviles siguen publicados como stock desde el primer día y se pueden vender dos veces.

## 2. La salida

El ERP sirve una consulta de solo lectura con los IMEI puestos en los últimos días. El bot la llama en cada vuelta y junta su
lista con la que ya tiene de las ventas directas. No hay puente con la hoja ni el ERP escribe en ningún sitio.

Descartado: que lo lea el script de la hoja de cálculo que hoy junta las ventas (un fallo del ERP tumbaría también la lista de
ventas directas) y que el ERP escriba en la hoja (decisión 1 de la spec base).

## 3. La consulta

`GET /api/pedidos/vendidos?dias=7`

**Respuesta:** `200` con una lista JSON, una entrada por IMEI (sin repetidos):

| Campo | Qué es |
|---|---|
| `imei` | 15 cifras |
| `sku` | El de la fila |
| `plataforma` | El texto de la columna |
| `canal` | El de `Pedido_fila` |
| `pedido` | Número de pedido del canal |
| `fechaImei` | Cuándo se puso ese IMEI en la fila (fecha y hora) |

El bot solo usa `imei`; los demás campos son para diagnosticar a mano qué ha pedido mover el ERP y por qué.

**Qué filas entran.** Todas las que cumplan a la vez:

1. `ORIGEN` = `SHIPPYPRO`. El histórico no entra: lo que venía de la hoja ya lo movió el bot antes del corte.
2. Estado **pendiente** o **enviada**. Las «ya no pendientes, sin envío» **no** entran: un pedido cancelado conserva su IMEI en
   la fila, y si saliera en la lista el bot apartaría un móvil que no se ha vendido.
3. IMEI de 15 cifras.
4. El **último cambio de IMEI** de la fila (`Pedido_fila_cambio`) es de los últimos `dias` días, contando por días de Madrid:
   hoy y los `dias - 1` anteriores. **No** se mira `Pedido_fila.FECHA`: un IMEI puesto ocho días después de entrar el pedido
   tiene que salir en la lista.

`dias`: entero de 1 a 31; por defecto, 7. Fuera de ese rango, `400`.

Si el mismo IMEI está en dos filas que cumplen, sale una vez (la del cambio más reciente).

**Los IMEI traspasados desde la hoja en la carga del histórico** (los de pedidos aún pendientes) tienen que quedar con un
cambio registrado, para que la regla 4 los vea. Que salgan en la lista aunque el bot ya los moviera no hace daño: mover un
móvil que ya está apartado no hace nada.

## 4. Quién puede llamarla

No es una ruta de usuario. Se entra con una **clave de máquina** en la cabecera `X-API-Key`:

- la clave está en una variable de entorno del servidor (`PEDIDOS_VENDIDOS_CLAVE`); si la variable está vacía, la ruta responde
  `404` para todos (apagada);
- sin cabecera o con otra clave, `401`; la comparación, en tiempo constante;
- la clave solo vale para esta ruta, y un token de usuario (del rol que sea) no vale para ella;
- la ruta queda bajo el mismo freno de peticiones que el resto de la API.

La clave se genera aparte para producción y para preprod y no se escribe en el repositorio ni en la documentación.

## 5. Uso previsto

Una llamada cada 30 minutos, de 7 a 23 h. Unos cientos de entradas por respuesta. El bot no guarda estado: cada vez pide toda
la ventana. Si el ERP no contesta, el bot reintenta dos veces, sigue con las ventas directas y lo deja en su registro; en la
vuelta siguiente lo vuelve a pedir.

## 6. Lo que se sabe y se acepta

- **Quitar o cambiar un IMEI ya servido:** el móvil que se quitó ya está apartado en NSYS y nadie lo devuelve al stock. Hoy pasa
  lo mismo con la hoja y se corrige a mano. Queda como mejora: una lista de «IMEI quitados de un pedido» para repasarlos.
- **Un pedido pendiente con IMEI cuenta como vendido** desde que se pone el IMEI, igual que hoy en la hoja.
- **ERP caído varios días:** los móviles no se apartan hasta que vuelva. El bot lo avisa en su registro; un aviso por correo
  cuando el fallo se repita queda fuera de esta entrega.

## 7. Cómo se comprueba

- Con la clave buena, `200` y la lista; sin clave o con otra, `401`; con un token de usuario y sin clave, `401`; con la
  variable vacía, `404`.
- Una fila pendiente con IMEI puesto hoy sale; una «ya no pendiente» con IMEI, no; una del histórico, no.
- Una fila con `FECHA` de hace 20 días e IMEI puesto ayer sale; una con IMEI puesto hace 8 días, no.
- Cambiar el IMEI de una fila: sale el nuevo y no el anterior. Quitarlo: la fila deja de salir.
- En preprod, antes del corte: la lista del ERP para una ventana, comparada con los IMEI que la hoja tiene en esas mismas
  filas, da los mismos móviles (es la prueba principal del ensayo del corte).
