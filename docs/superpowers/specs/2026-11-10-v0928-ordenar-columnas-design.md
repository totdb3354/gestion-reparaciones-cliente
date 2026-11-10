# 0.9.28 — ordenar por columna en «Stock de teléfonos»

Spec de diseño. Aprobada por el usuario en el brainstorming del 10/11/2026. Sin prototipo (el cambio visual es una flecha en la
cabecera). **Solo web**: sin servidor ni base de datos.

## 1. El problema

En «Stock de teléfonos» las tablas salen en un orden fijo. Para ver «los que más días llevan», «todos los de una caja» o «por
grado» hay que recorrer la lista. Se pide lo habitual: clic en la cabecera de una columna y la tabla se ordena por ella, de
mayor a menor y de menor a mayor.

## 2. Decisiones (brainstorming del 10/11)

| Qué | Decisión |
|---|---|
| Dónde | La tabla de **móviles** del modelo y las dos tablas de **«Qué revisar»** («Revisar sin grado» y «Subir B a A»). |
| Dónde no | «Por pasar a NSYS» (listas de trabajo con casillas, orden de caja), «Pegar varios IMEIs» (orden de lo pegado), la tabla color × grado (es un filtro) y el panel de la derecha de «Qué revisar» (listas cortas, ya por días). El resto de pantallas del ERP, en otra versión. |
| Los clics | Primero ordena, segundo invierte, **tercero vuelve al orden de siempre**. Una sola columna a la vez. |
| Sentido inicial | Texto, de la A a la Z. Números (Días, Sin grado, B, B100), de mayor a menor. |
| Empates y vacíos | Las filas que empatan conservan entre sí el orden de siempre. Los vacíos («—», sin días, sin SKU) van **siempre al final**, en los dos sentidos. |
| Bloques de «Revisar sin grado» | Se ordena **dentro de cada bloque**: «3 o más» y «Menos de 3» siguen en su sitio, con sus títulos y cifras. |
| Excel de «Qué revisar» | Sale **en el orden de la pantalla**. |
| Se recuerda | Al volver del detalle de un IMEI y al cambiar de modelo, de pestaña de capacidad, de vista o de tarjeta. Se pierde al cerrar sesión, como los filtros. |
| Número | **0.9.28** (decisión del usuario): sale antes que los pedidos, que esperaban como 0.9.28 y pasan a la **0.9.29**. |

Descartado: pasar «Qué revisar» a la tabla compartida (`DataTable`) para tener una sola forma de ordenar (rehace una pantalla
recién estrenada: bloques, fila elegida y panel), y ordenar a mano también en móviles (duplica lo que `DataTable` ya hace).

## 3. La cabecera ordenable

Una pieza compartida nueva, `shared/ui/CabeceraOrden.tsx`: el botón que va dentro del `<th>` de una columna ordenable. La usan
`DataTable` y las tablas de «Qué revisar», para que se vean y se anuncien igual.

- Pinta el nombre de la columna y, si es la ordenada, **▲** (ascendente) o **▼** (descendente), con `aria-hidden`.
- Cursor de mano y subrayado al pasar el ratón, en todas las ordenables.
- El `<th>` lleva `aria-sort="ascending"` o `"descending"` en la columna ordenada (`DataTable` ya lo pone).
- Recibe `texto`, `direccion` (`'asc' | 'desc' | null`) y `onClick`. No sabe nada de datos.

## 4. `DataTable`

`DataTable` ya tiene la ordenación de TanStack detrás de `ordenacion` (apagada por defecto; ninguna pantalla la enciende). Cambios:

- La cabecera ordenable pasa a usar `CabeceraOrden` (hoy es un botón sin flecha).
- Dos propiedades nuevas y opcionales, `orden` y `onOrden` (`SortingState`): si llegan, el orden lo guarda la página; si no,
  sigue en el estado interno de la tabla, como ahora.
- `enableMultiSort: false`. El tercer clic quita el orden (comportamiento por defecto de TanStack, `enableSortingRemoval`).

Con `ordenacion` apagada no cambia nada: las demás pantallas quedan como están.

## 5. La tabla de móviles

`StockTelefonosPage` pasa `ordenacion`, `orden` y `onOrden` a su `DataTable`. Los datos siguen llegando en el orden de siempre
(`ordenarTabla`): es el orden sin columna elegida y el desempate (la ordenación de TanStack es estable).

Las ocho columnas de `columnasMoviles` ganan su valor de orden (`accessorFn`) y, donde hace falta, su comparación (`sortingFn`).
Lo que se pinta en la celda no cambia.

| Columna | Se ordena por | Primer clic |
|---|---|---|
| IMEI | El IMEI/SN, como texto | A → Z |
| Cap. | La capacidad como número (`compararCapacidades`) | menor → mayor |
| Color | El color que vale (`color`), alfabético en español | A → Z |
| Grado | El grado que vale, en su orden (`compararGrados`: A+, A, A-, B…) | mejor → peor |
| SKU | El SKU que vale, como texto | A → Z |
| Ubicación | La caja, con los números como números («Caja 2» antes que «Caja 10») | A → Z |
| Quién lo tiene | El texto de la celda (`quien.texto`) | A → Z |
| Días | Los días como número | mayor → menor |

- Vacíos al final: sin color, sin grado, sin SKU, sin ubicación, sin nadie que lo tenga y sin días.
- Con «Ver también lo que no cuenta como stock» encendido y una columna ordenada, los que no cuentan se mezclan con los demás
  (siguen en gris). Con el orden de siempre van al final, como ahora.
- Las marcas «100» y «eSIM» de Grado y «NO CUENTA» de Ubicación no intervienen en el orden.

El orden se guarda en el store de la página (`estado.ts`): `ordenMoviles: SortingState`, vacío al empezar. Es uno para la tabla,
no por modelo. La columna «Cap.» solo existe con la pestaña «Todas»: si el orden guardado es por «Cap.» y se elige una capacidad,
la tabla sale en el orden de siempre y el orden guardado se limpia.

## 6. «Qué revisar»

Las dos tablas están hechas a mano (`VistaQueRevisar.tsx`). Su `Cabecera` pasa a pintar un `CabeceraOrden` por columna.

En `queRevisar.ts`, dos funciones puras nuevas, `ordenarRevisar(lista, orden)` y `ordenarSubir(lista, orden)`, con
`orden: { columna, desc } | null`. Sin orden devuelven la lista tal cual. Ordenan con `Array.sort`, que es estable: los empates
conservan el orden de siempre.

| Columna | Se ordena por | Primer clic |
|---|---|---|
| Modelo | El orden de la columna lateral (`compararModelos`), el físico antes que el eSIM | como la columna lateral |
| Cap. | `compararCapacidades` | menor → mayor |
| Color | Alfabético en español | A → Z |
| Sin grado | `pendientes` (sin los del técnico) | mayor → menor |
| B | `b` (sin los del técnico) | mayor → menor |
| B100 | `b100` (sin los del técnico) | mayor → menor |

Capacidad y color «—» van al final.

En `VistaQueRevisar`, la lista visible de cada tarjeta es `ordenar(filtrar(lista, palabras), orden)`. De esa misma lista salen:

- la tabla (en «Revisar sin grado», `bloquesRevisar` la parte en dos conservando el orden: queda ordenada dentro de cada bloque);
- el Excel (`excelRevisar` y `excelSubir`), que así lleva el orden de la pantalla.

Las cifras de las tarjetas, el número de la pestaña, el panel de la derecha y el modelo-color que elige un IMEI buscado
(`elegidoPorImei`) no cambian.

El orden se guarda en el store, dentro de `queRevisar`: `orden: Record<TarjetaQR, { columna: string; desc: boolean } | null>`,
uno por tarjeta (las columnas no son las mismas), los dos `null` al empezar.

## 7. Pruebas

- **Unidad** (`queRevisar.test.ts`): cada columna en los dos sentidos, empates en el orden de siempre, «—» al final, sin orden
  la lista no cambia, y el Excel en el orden pedido.
- **Unidad** (`columnas.test.tsx` de teléfonos, nuevo): el valor y la comparación de cada columna (grados, cajas con números,
  vacíos al final).
- **`DataTable.test.tsx`**: la flecha y `aria-sort`, los tres clics, `orden`/`onOrden` controlados, y que con `ordenacion`
  apagada la cabecera sigue sin botón.
- **Páginas** (`StockTelefonosPage.test.tsx`, `VistaQueRevisar.test.tsx`): ordenar por Días y por Grado; el orden sigue al
  cambiar de modelo y al volver a montar la página; en «Revisar sin grado» se ordena dentro de cada bloque; el Excel se pide con
  las filas en el orden de la pantalla.
- **E2E en preprod** (Chromium): ordenar la tabla de móviles por Días en los dos sentidos y volver al orden de siempre; ordenar
  «Subir B a A» por B. Solo lectura: no crea datos.

## 8. Lo que no entra

- Ordenar por varias columnas a la vez.
- Recordar el orden entre sesiones.
- Ordenar en otras pantallas del ERP (Stock de piezas, Historial, Clientes…): `DataTable` queda listo, pero cada pantalla se
  decide aparte. Pedidos (bloques) y Asignaciones (orden fijo de urgencia) no.

## 9. Entrega

Una rama de la web (`dev_v0928_ordenar_columnas`). Novedades de la 0.9.28 en `docs/novedades/`. Despliegue de solo web, sin
corte: se puede subir en horario.
