# 0.9.26 — «Descargar cajas» en «Cambio de color»

Fecha: 2026-11-06. Estado: **spec aprobada** por el usuario el 06/11, pendiente del plan.
**Versión 0.9.26** (decisión del usuario, 06/11). Los pedidos, terminados y a la espera, pasan a ser la 0.9.27 y se renumeran al
traer `main` a sus ramas.
**Servidor + web, sin migración.** Se apoya en la 0.9.22 (tarjeta «Cambio de color» y su plantilla, spec
`2026-10-25-v0922-plantillas-nsys-design.md` §3.2 y §4) y en la 0.9.25 (el Excel para imprimir de «Qué revisar»,
`POST /api/stock-telefonos/que-revisar/excel` con `HojaImprimible`, spec `2026-11-04-v0925-que-revisar-design.md` §10.3).

## 1. El problema

La plantilla «Upload batch with IMEI» de NSYS no tiene columna de caja. Sus 14 columnas son IMEI, fabricante, modelo,
capacidad, color, grado, precio, proveedor, lote, grupo, EAN, UPC, descripción y estado (comprobado en
`src/main/resources/nsys/plantilla-nsys-vacia.xlsx`). Para cambiar el color, el móvil se borra en NSYS y se sube otra vez con
la plantilla, y entra en el lote `COLOR` **sin caja**. Si estaba en «CAJA LISTOS», deja de estar allí y en el ERP pasa a «En
revisión».

Para devolverlo a su caja hay que saber dónde estaba. Hoy el ERP no lo enseña, y la fila de la tarjeta se va en la importación
siguiente (cada 30 min), con lo que se pierde el dato en pantalla.

Los móviles que ya han pasado por esto se arreglan aparte, antes de esta versión, con la consulta de solo lectura de
`Apuntes/diagnostico-cajas-perdidas-color.md`.

## 2. Decisiones del usuario (06/11)

| Tema | Decisión |
|---|---|
| Qué se descarga | Un **fichero nuevo y aparte**. La plantilla de color no cambia. |
| Qué móviles | **Solo los del cambio de color**: los mismos que se van a cambiar ahora. Nada sale del histórico. |
| Columnas | Lo esencial es el **IMEI y la caja**. De añadido, el **modelo entero** (del maestro, como el SKU) y el **color nuevo**. |
| En pantalla | Una columna **«Caja»** en la tarjeta. |
| Cómo se devuelven en NSYS | **Por bloques**: se marcan todos los de la misma caja y se les cambia con «Location». No se sube ningún Excel. |

**Descartadas:**
- **Sacar la caja del histórico**, para que también salgan los móviles ya subidos que siguen sin caja. Podría traer casos que no
  son del cambio de color.
- **Lo mismo para cualquier corrección de NSYS** (grado, producto, a mano), por el mismo motivo.

## 3. Qué se ve

### 3.1 Columna «Caja» en «Cambio de color»

- Va entre «En NSYS hoy» y «Poner en NSYS». Enseña la caja del móvil en NSYS tal cual la escribe NSYS («CAJA LISTOS»), en
  texto normal.
- Si el móvil no tiene caja, pone «sin caja» en gris.
- Con un servidor anterior, que no manda el dato, pone «—».
- Sale también en las filas marcadas «Hecho», en gris como el resto de la fila.

### 3.2 Botón «⬇ Descargar cajas (N)»

- Va en la tarjeta «Cambio de color», detrás de «⬇ Descargar plantilla (N)». Es secundario, como «Copiar lista»: el principal
  sigue siendo la plantilla.
- **N** es cuántos móviles lleva (§4). Con 0 está desactivado, con el mismo texto al pasar el ratón que los otros botones
  (`SIN_NADA`). Con alguno, el texto dice cuántos móviles.
- Al pulsarlo, se abre «Guardar como» con el nombre ya puesto, `Cajas_color_AAAA-MM-DD.xlsx` (fecha de Madrid), y después se
  pide el Excel, como en «Qué revisar» (0.9.22.1: si se esperara al servidor, el navegador perdería la descarga). Cancelar no
  pide nada.
- Al guardarlo sale un aviso con el formato de los de la plantilla: «Descargado «Cajas_color_2026-11-06.xlsx» · 12 móviles en
  3 cajas» (en singular, «1 móvil en 1 caja»). «sin caja» cuenta como una caja más. Un error sale en el diálogo de siempre.

### 3.3 El Excel

Una hoja, «Cajas cambio de color», con cuatro columnas:

| Columna | Qué lleva |
|---|---|
| IMEI | El IMEI/SN del móvil. |
| Caja | Lo mismo que la columna de §3.1; «sin caja» si no tiene. |
| Modelo | Lo mismo que la columna «Modelo» de la tarjeta (`modeloDeFila`): «iPhone 14 eSIM 256GB». |
| Color nuevo | El color que se pone en NSYS, como lo escribe NSYS (`colorA`); vacío si no se sabe. |

- **Orden:** por caja (de la A a la Z sin distinguir mayúsculas, «sin caja» al final) y, dentro de cada caja, por IMEI. Así cada caja es un bloque
  seguido, como se seleccionan en NSYS.
- **Formato:** el de `HojaImprimible`. El IMEI se queda como texto; solo un entero de 1 a 9 cifras va como número, y aquí no hay
  ninguno. La cabecera va en negrita y se repite en cada página, con bordes finos y cada columna tan ancha como su texto más
  largo. Con estos anchos (unos 80 caracteres) sale en vertical.

### 3.4 Ayuda de la tarjeta

La ayuda de «Cambio de color» (`AYUDA.color`) dice el orden de los pasos y avisa de la caja. También lleva la frase de que se
pueden borrar todos de una vez, que estaba pendiente desde la 0.9.22:

> Móviles a los que el taller ha cambiado el color. Antes de tocarlos en NSYS, descarga las cajas: la plantilla no lleva la caja
> y NSYS los deja sin ella. En NSYS: bórralos (se pueden borrar todos de una vez), súbelos otra vez con la plantilla, que lleva
> el grado que tienen hoy en NSYS (el grado nuevo, si lo hay, va por «Cambio de grado», después), y devuélvelos a su caja con
> «Location». Cada fila se va sola cuando la importación (cada 30 min) ve el color nuevo en NSYS.

## 4. Qué móviles lleva

- Los **mismos que la plantilla de color** (`paraPlantillaColor`), es decir, los que tienen producto a poner y no son vendidos ni
  bajas que han vuelto, **y además los marcados «Hecho»**. Después de subir la plantilla se marcan las filas, y justo entonces
  hacen falta las cajas: si la descarga dejara fuera los marcados, saldría vacía.
- Cada móvil está en el fichero mientras su fila esté en la tarjeta. La fila se va cuando la importación ve el color nuevo o el
  móvil de baja, como hasta ahora. Por eso la ayuda dice que se descarguen las cajas **antes** de borrar en NSYS.
- Si se olvida y la fila ya se ha ido, el fichero no tiene ese móvil. Para esos está la consulta de `Apuntes`.

## 5. Cómo se hace

### 5.1 Servidor

- **`FilaColorNsys`** lleva un campo nuevo, **`caja`** (nullable): la caja de NSYS del lote del móvil, `m.location()`, que ya se
  guarda sin espacios a los lados, o null si está vacía. Se rellena en `TarjetasNsys.color`, que ya tiene el lote.
  - El constructor corto de las pruebas pasa null.
- Nada más: ni ruta nueva ni migración. El Excel lo escribe la ruta de la 0.9.25 (`POST /api/stock-telefonos/que-revisar/excel`),
  que no sabe nada de la lista que imprime y ya tiene la guarda de SUPERTECNICO y ADMIN.
- **Contrato:** el esquema `FilaColorNsys` del OpenAPI lleva `caja`. Se actualizan `OpenApiContractTest` y los tipos generados
  de la web.

### 5.2 Web

- **`porPasar.ts`:**
  - `paraCajasColor(filas)`: el filtro de la plantilla sin la condición de «Hecho».
  - `excelCajasColor(filas): HojaExcel`: la hoja de §3.3, con su orden.
  - `nombreCajas(ahora)`: el nombre del fichero.
  - `avisoCajas(nombre, filas)`: el aviso, con los móviles y las cajas distintas.
  - El texto nuevo de `AYUDA.color`.
- **`ListasPorPasar.tsx`:** la columna «Caja» de `ListaColor`.
- **`PorPasarNsys.tsx`:** en la tarjeta de color, `boton('color')` devuelve los dos botones. «Descargar cajas» usa
  `elegirDestino` y `useDescargarExcel`, como «Qué revisar».
- **Versión 0.9.26:** `package.json` y su lock, `CHANGELOG.md`, y `NOVEDADES-v0.9.26.md` en la raíz.

### 5.3 Compatibilidad

- **Web nueva con el servidor de la 0.9.25:** no llega `caja`. La columna pone «—» y el Excel también. Se sube primero el
  servidor y enseguida la web, como siempre.
- **Web de la 0.9.25 con el servidor nuevo:** el campo de más se ignora.

## 6. Pruebas

- **Servidor (`TarjetasNsysTest`):**
  - la fila lleva la caja de su lote;
  - un lote sin caja da null.
  - `OpenApiContractTest` con el campo nuevo.
- **Web, unitarias:**
  - `paraCajasColor`: lleva los marcados «Hecho» y deja fuera los que no tienen producto y los que han vuelto.
  - `excelCajasColor`: columnas, orden por caja con «sin caja» al final y luego por IMEI, y modelo con eSIM.
  - `avisoCajas`: singular y plural.
  - `ListaColor` enseña la caja, «sin caja» y «—».
  - `PorPasarNsys`: el número del botón, desactivado con 0, «Guardar como» con su nombre, cancelar no pide nada, y la petición
    lleva la hoja.
- **E2E en preprod** (`tests/e2e/plantillas-nsys.spec.ts`, datos sintéticos por API y limpieza, como la plantilla de color):
  descargar las cajas, leer el xlsx con `tests/e2e/xlsx.ts` y comprobar el IMEI y la caja del móvil de la prueba.

## 7. Fuera de esta versión

- **Sacar la caja del histórico** para los móviles que ya la perdieron. Se hace una vez, a mano, con la consulta de `Apuntes`.
- **Devolver la caja en NSYS desde el ERP.** La plantilla de NSYS no la admite, y el ERP solo lee NSYS.
- **«Cambio de grado»:** en NSYS el grado se corrige sin borrar el móvil, así que no pierde la caja.
- **«No está en NSYS»:** son móviles que NSYS no conoce, así que no tienen caja que recuperar.
