# Pedidos: textos y colores de la celda del IMEI, y cajas de NSYS con «Caja · …» — diseño

Fecha: 2026-11-24. Estado: aprobado en conversación, pendiente de revisar este texto.
Versión: la siguiente a la de «IMEI duplicados en pedidos» (el número se pone al sacarla). Servidor + web + migración.
Base: avisos del IMEI de la pieza 2 (`EstadoUnidad`, `AvisoImei`), «Validar SKU» (`2026-11-16-pedidos-validar-sku-design.md`),
«Quién lo tiene» de Stock (`quien.ts`).
Prototipo aprobado: `assets/2026-11-24-pedidos-celda-imei-prototipo.html` (botón «Antes / Después», 26 casos).

## Para qué

- «En el taller» en Pedidos y «Taller · …» en Stock son la caja de NSYS, no que un técnico lo tenga. Se confunde con
  «reparándose» (asignación abierta).
- La celda del IMEI pinta en amarillo cosas que no son problema (en el taller, Ms, B por A) y en naranja cosas que solo hay que
  saber (n/a, eSIM). El naranja «otro color o grado» no dice cuál de los dos falla.
- Cuando se cumplen varias cosas a la vez solo se ve una.

## Qué cambia

### 1. Cajas de NSYS: «Caja · …»

| Caja de NSYS | Hoy | Pasa a |
|---|---|---|
| CAJA PULIR | Taller · Pulir | Caja · Pulir |
| CAJA GLASS | Taller · Glass | Caja · Glass |
| CAJA REP. OFICINA | Taller · Reparación | Caja · Reparación |
| C.R. Pantalla Rota | Taller · Pantalla rota | Caja · Pantalla rota |
| CAJA LISTOS | Listos | Caja · Listos |

En revisión, Devolución, Esperando pieza y No vendible no cambian.

**Migración** `sql/renombrar-cajas-nsys.sql` (una transacción, cliente utf8mb4):
- `Nsys_ubicacion.UBICACION` de las cinco filas.
- `Nsys_movil.UBICACION` y `Nsys_movil.UBICACION_ULTIMA`.
- `Nsys_evento.DE` con `TIPO = 'UBICACION'` y `Nsys_evento.A` con `TIPO IN ('UBICACION','ENTRADA')` (en los demás tipos DE y A son
  grados o productos).
- Al final, la comprobación por `HEX(UBICACION)` como la de `migracion-nsys-espejo.sql`, con los nombres nuevos: debe dar 7.
- Vuelta atrás: el mismo fichero con los nombres al revés (`renombrar-cajas-nsys-atras.sql`).
- Antes de producción se mide en preprod cuánto tarda el UPDATE de `Nsys_evento`. Si pasa de unos 30 s, se hace fuera de horario.

`crear_bd.sql` y `migracion-nsys-espejo.sql` (inserts y la comprobación HEX) pasan a los nombres nuevos.

**Web** (sale a la vez que la migración, porque busca los nombres exactos):
- `shared/lib/telefonoNsys.ts` `CLASES_UBICACION`: mismas clases con las claves nuevas (Caja · Listos con el verde de Listos).
- `stock/quien.ts`: `startsWith('Taller')` → `startsWith('Caja · ')` sin contar «Caja · Listos»; `=== 'Listos'` →
  `=== 'Caja · Listos'`; el aviso «NSYS dice "Listos"…» → «NSYS dice "Caja · Listos"…».
- `stock/modelo.ts:101-102`: igual que `quien.ts` (listos y taller).
- `taller/imeis/rastro.ts:44-45` y la leyenda de `PanelRastro.tsx` («Taller» → «Cajas del taller», «Listos» → «Caja · Listos»).
- Tests y fábricas de la web con los nombres nuevos (los del servidor no comparan nombres: se quedan).

### 2. Celda del IMEI en Pedidos: piezas, color de texto y fondo

La celda enseña el IMEI y, a su lado, **todas** las piezas que tocan, separadas por « · », en este orden:
1. La palabra de SIM (si la hay).
2. Modelo o capacidad (si la hay).
3. El resto.

**Fondo de la celda = el peor nivel:** naranja (`#FFE0B2`) si hay alguna pieza naranja; si no, amarillo claro
(`form-agotado-bg`) si hay «N/A»; si no, sin fondo. **«N/A» es el único caso amarillo.**

| Caso | Texto | Color del texto | Cuenta en «no cuadran» | «Validar SKU» |
|---|---|---|---|---|
| Lo tiene un técnico | reparándose · Juan | ámbar | no | no |
| Grado n/a | N/A | ámbar | no | no |
| Grado Ms aceptado | MS | ámbar | no | no |
| B en lugar de A (Amazon) | B por A | ámbar | no | no |
| Pide SIM física, es eSIM | eSIM | naranja | no | no |
| Pide eSIM, es SIM física | SIM física | naranja | no | no |
| Otro color | color: Rosa | naranja | sí | sí |
| Otro grado | grado: B (un C sin reparar: grado: C) | naranja | sí | sí |
| Color y grado valen por separado pero esa combinación no existe | otro color o grado | naranja | sí | sí |
| Otro modelo | modelo: 14 | naranja | sí | sí |
| Otra capacidad | capacidad: 256GB | naranja | sí | sí |
| Grado que no se vende | no se vende: F | naranja | sí | sí |
| SKU que el maestro no tiene | SKU desconocido | naranja | sí | sí |
| No está en NSYS | no está en NSYS | naranja | sí | no |
| Ya en otra fila | en otro pedido | naranja | sí | no |

Ejemplos de varias a la vez: «eSIM · color: Rosa» (fondo naranja), «eSIM · N/A» (amarillo), «N/A · color: Rosa» (naranja),
«MS · color: Rojo» (naranja), «SIM física · capacidad: 128GB» (naranja).

Al pasar el ratón: la frase larga de hoy, con el SKU completo del móvil, una línea por pieza.

**Reparándose:** mismo formato que «Quién lo tiene» de Stock, sin la entrega: «reparándose · Juan», «reparándose · Ana (glass)»,
«reparándose · Juan · glass Ana», «reparándose · Luis (pulido)». Se saca la parte de texto de `quien.ts` a una función que usan las
dos pantallas. Con un técnico, no se comprueban ni color ni grado (como hoy).
`TEXTO_NIVEL[2]` del recomendador: «en el taller, acabará igual» → «reparándose, acabará igual».

Lo que **no** cambia: no se pone ninguna marca automática (tampoco «En revisión» con los n/a); el filtro «no cuadran» sigue siendo
`nivel = MAL`; el panel de la fila y la ayuda de Pedidos (`AyudaPedidos.tsx`) se actualizan con los textos nuevos.

### 3. Servidor: lo que sale con cada fila

`FilaVista` gana un campo **aditivo** `detalle` (objeto `DetalleAviso`; nulo sin aviso o sin nada que decir). La web vieja lo ignora:

| Campo | Valores | Qué es |
|---|---|---|
| `sim` | null, `ESIM`, `SIM_FISICA` | El móvil es eSIM y el pedido no (`ESIM`), o al revés (`SIM_FISICA`). |
| `na` | boolean | El grado que cuenta es n/a. |
| `ms` | boolean | Es un Ms (deja de ser motivo: así sale «MS · color: Rojo»). |
| `dato` | texto o null | Lo que tiene el móvil para el motivo: el color, el grado, el modelo, la capacidad o el grado no vendible. |
| `trabajos` | lista `TrabajoAbierto` | Las asignaciones abiertas, solo con motivo `EN_TALLER` (el mismo tipo que usa Stock). |

`motivo` sigue siendo uno (el principal) y gana valores: `COLOR`, `GRADO`, `CAPACIDAD`; `MS` ya no sale (va en `ms`). `COLOR_O_GRADO` queda solo para la
combinación que no existe; `MODELO` queda solo para el modelo. `MOTIVOS_SKU` = MODELO, CAPACIDAD, COLOR, GRADO, COLOR_O_GRADO,
GRADO_NO_VENDIBLE, SKU_DESCONOCIDO. Las validaciones ya guardadas siguen valiendo: se comprueban por IMEI y `esDeSku()`, no por el
motivo.

**`EstadoUnidad`, orden nuevo:**
1. No está en NSYS → `NO_EN_NSYS`. Sin aceptación → `SKU_DESCONOCIDO`.
2. Modelo distinto → `MODELO`; capacidad distinta → `CAPACIDAD`. Se apunta `sim` aunque falle (va delante en el texto).
3. SIM distinta con modelo y capacidad iguales: se apunta `sim` y **se sigue**.
4. Grado no vendible → `GRADO_NO_VENDIBLE`.
5. Con trabajos abiertos → `EN_TALLER` (no se miran color ni grado).
6. C sin reparar → `GRADO` con dato «C».
7. Grado n/a: se apunta `na`; solo se comprueba el color (algún producto aceptado con ese color). Falla → `COLOR`.
8. Resto: como hoy (SKU aceptado, Ms, B por A). Si no:
   - color aceptado por algún producto y grado aceptado por alguno → `COLOR_O_GRADO`;
   - si no, `COLOR` si falla el color, `GRADO` si falla el grado (si fallan los dos, `COLOR`).

`Aceptacion` gana `aceptaColor(color)` y `aceptaGrado(grado)` (sobre los mismos productos que `aceptaColorYGrado`).

**Nivel:** MAL si el motivo es de la tabla con «sí» en «no cuadran»; AVISO si hay `sim`, `na`, `ms` o motivo EN_TALLER o B_POR_A;
OK si nada. Una fila validada queda OK sin motivo, pero conserva `sim`, `na` y `ms` (se ven, en su color, y no cuentan).

**Recomendador:** sin cambios de comportamiento. Un móvil con `sim` distinta no se propone (hoy es MODELO); un n/a sigue en el nivel
3 («hay que transformarlo»).

**Bot y plugin** (`/api/pedidos/vendidos`, libros): no cambian; dependen de las marcas e IMEI, no del aviso.

### 4. Web: de los campos al texto

`filas.ts`:
- `partesAviso(f)` → lista de `{texto, tono: 'naranja' | 'ambar'}` en el orden de §2; `textoMotivo` queda para el tooltip y el
  panel.
- `claseCelda(f)`: `mal` si nivel MAL; `na` si `f.na`; si no, `ok`. Desaparece el fondo amarillo de `aviso`.
- `noCuadra` sin cambios (`nivel === 'MAL'`).
- La web vieja con el servidor nuevo: verá `COLOR`, `GRADO`, `CAPACIDAD` como motivo desconocido («revisar»). Por eso servidor y web
  salen juntos.

`CeldaImei.tsx` pinta las piezas en naranja o ámbar (los colores del prototipo; si no hay token, se añade a la paleta) y el fondo según `claseCelda`.

## Pruebas

- Servidor: `EstadoUnidadTest` con cada fila de la tabla de §2 y las cinco combinaciones de ejemplo; `AvisoImeiTest` con los niveles;
  `RecomendadorTest` (SIM distinta fuera, n/a en nivel 3); `VistaFilasTest` (validada conserva `sim` y `na`; `trabajos` llega);
  `OpenApiContractTest`.
- Web: `filas.test.ts` (`partesAviso`, `claseCelda`), `CeldaImei` con los casos del prototipo, `quien.test.ts` con la función
  compartida, y los tests de Stock y rastro con los nombres nuevos.
- Preprod: aplicar la migración midiendo el tiempo, comprobación HEX = 7 (las cinco «Caja · …», En revisión y Devolución), y revisar Pedidos y Stock con datos reales.

## Fuera de alcance

- Marca automática «En revisión» para los n/a (descartada).
- Cambiar la columna «Quién lo tiene» de Stock más allá de los nombres de caja.
