# Pedidos: el periodo se respeta (rango estricto con aviso de pendientes) — diseño

Fecha: 2026-11-23. Estado: aprobado en conversación. Versión 0.9.38, **solo web**.
Prototipo: `assets/2026-11-23-pedidos-rango-estricto-prototipo.html` (botón Antes/Después).

## Por qué

En Teléfonos › Pedidos el periodo (Último mes, 3 meses, Este año, Todo, Rango…) no es estricto: el servidor devuelve las filas
con fecha dentro del periodo **y además todas las que siguen abiertas** (`CERRADA_EN IS NULL`), sean del día que sean
(`FilasDAO.SQL_DEL_PERIODO`). Se hizo para que ningún pendiente quedara escondido, pero confunde: se elige del 20 al 23/11 y salen
filas del 2/11. Logística viene del Excel, donde un filtro de fechas enseña solo esas fechas.

## Qué cambia

1. **La tabla enseña solo las filas con fecha dentro del periodo**, sea cual sea el periodo. La fecha que cuenta es la de la columna
   «Fecha» (día de Madrid), la misma que usa hoy el servidor.
2. **Aviso encima de la tabla**, solo si hay pendientes de fuera:
   «⚠ Hay **N pendientes de antes del dd/mm** (M con retraso). · Ver». Si quedan después del final, dice «de después del dd/mm»;
   si hay a los dos lados, «fuera de estas fechas». «(M con retraso)» solo si M > 0.
   - Cuenta las filas recibidas fuera del periodo que son **pendientes** según `esPendiente` (pendiente o «Falta por enviar»,
     sin «Cancelado»). Los cancelados abiertos de fuera no se cuentan ni se enseñan.
   - Con retraso = las que el contador «con retraso» ya da por retrasadas (`nivelSalida(f, hoy) === 'RETRASO'`).
3. **«Ver»** cambia la tabla a **solo esos pendientes de fuera**, con:
   - una etiqueta «Pendientes fuera del periodo ✕» junto a las de los filtros de columna.
   (La raya amarilla del prototipo se quitó al implementar: la raya azul ya marca la fila seleccionada y, con «Ver», todas las
   filas son de fuera.)
   Se vuelve al periodo quitando la etiqueta, cambiando el periodo o las fechas, o con «Quitar filtros». Los filtros de columna
   y los contadores siguen funcionando encima.
4. **Contadores** (filas, pendientes, sin IMEI, salen hoy, con retraso) y las listas de los filtros de columna (tiendas, SKU,
   recuentos de Enviado y de marcas): se calculan sobre la base que se ve (las del periodo o, con «Ver», las de fuera), no sobre todo
   lo recibido. Así cuadran con la tabla.

**No cambia:**
- El servidor y la petición: sigue mandando periodo + abiertas; la web las separa. Sin migración ni cambios en el contrato.
- **El buscador**: con texto sigue mirando en todo el historial y el periodo no se aplica (ni aviso ni separación).
- **El Listado**: se calcula en el servidor y cuenta todos los pendientes.
- El filtro de Plataforma, que está bien como está.

## Diseño en la web (`src/modules/telefonos/pedidos/`)

- **`filas.ts`**: `separarPorPeriodo(filas, desde, hasta)` → `{ delPeriodo, fuera }` (`fuera` = pendientes fuera del periodo, en
  orden de hoja; el día de cada fila con `fechaLocal`, como `pasaFechas` en Almacén) y `avisoFuera(fuera, desde, hasta, hoy)` → `{ n, conRetraso, donde: 'antes' | 'despues' | 'ambos' } | null`.
  Funciones puras.
- **`estado.ts`**: `verFuera: boolean` en `EstadoPedidos` (por defecto `false`). Se pone a `false` al cambiar el periodo o el rango,
  y con «Quitar filtros». Se reinicia al cerrar sesión, como el resto del store.
- **`PedidosPage.tsx`**: sin búsqueda, `base = verFuera ? fuera : delPeriodo`. Con búsqueda, `base` = lo recibido. Todo lo que hoy
  usa `filas` para contar, para las listas de filtros y para `filtrarColumnas` pasa a usar `base`. El panel de la fila sigue
  buscando la fila elegida entre lo recibido. Aviso nuevo (componente pequeño `AvisoFuera`) entre la barra y la tabla.
- **`AyudaPedidos.tsx`**: una línea en la ayuda: «El periodo enseña solo las filas de esas fechas; si quedan pendientes de otros
  días, lo dice el aviso amarillo».
- Si `verFuera` está puesto y ya no queda ningún pendiente fuera (se enviaron), la tabla queda vacía con el texto de siempre y la
  etiqueta para quitarlo.

## Pruebas

- Los tests de pantalla de Pedidos fijan «hoy» en el 23/11/2026 (`test/hoy.ts`, `congelarHoy`): con el periodo estricto, las filas de
  prueba (con fechas fijas) dejarían de verse con «Último mes» al pasar un mes.
- `filas.test.ts`: separación (dentro, fuera antes, fuera después, cancelado de fuera excluido, «Falta por enviar» de fuera
  incluido, límites `desde`/`hasta` incluidos), texto del aviso (antes, después, ambos; con y sin retraso).
- Test de pantalla (`PedidosRango.test.tsx`): con un rango, la tabla no enseña los pendientes de fuera y sale el aviso; «Ver» enseña
  solo esos y la etiqueta; quitarla vuelve; con búsqueda no hay aviso; los contadores cuentan la base.
- E2E en preprod con datos sintéticos por API y limpieza (nunca en producción).

## Versión y despliegue

0.9.38 solo web (sube el número visible). Se despliega como las de solo web; la vuelta atrás es volver a la imagen de la 0.9.37.
Actualizar `docs/modulos/pedidos.md` §3 (la tabla) con el comportamiento del periodo y el aviso.
