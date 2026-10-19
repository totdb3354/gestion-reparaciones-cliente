# 0.9.18 — Stock de teléfonos: columna compacta, buscador de SKU, «Quién lo tiene» y «Revisado»

Fecha: 2026-10-19. Estado: **borrador, pendiente de la revisión del usuario.** Versión **0.9.18**; plan: por escribir.

Toca **servidor** y **web**. **No toca la base de datos.** Parte de la 0.9.17 (servidor `main` `d2b717d`, web `main`
`f460c75`) y se despliega **después** de que la 0.9.17 esté en producción.

Es una mejora de la pieza 1 del módulo de teléfonos (spec `2026-10-16-pieza1-stock-telefonos-design.md`), hecha tras
usar la 0.9.17 en preprod con datos reales y tras comparar con NSYS. El prototipo
[`assets/2026-10-19-v0918-prototipo-stock-telefonos.html`](assets/2026-10-19-v0918-prototipo-stock-telefonos.html)
lleva datos inventados. Su barra morada permite encender y apagar lo que va marcado con **(D)** más abajo.

## 1. Problema y objetivo

- **La lista de la izquierda** tiene una fila por modelo + capacidad. Son muchas, y obliga a hacer scroll.
- **Solo se busca por modelo.** Para mirar un SKU (el de un pedido, el del Excel) hay que deducir el modelo y la
  capacidad y filtrar a mano.
- **Con una caja de taller no se sabe quién tiene el móvil.** «Taller · Reparación» no dice si el móvil espera en la
  caja o si ya lo tiene un técnico, ni si hay glass. Hoy se copia el IMEI y se busca la asignación. El ERP ya sabe
  las asignaciones abiertas: falta cruzarlas con el espejo.
- **Los chips de color y de grado** dan los totales por separado. «¿Cuántos Grafito A tengo?» exige pulsar y leer
  varias veces. NSYS lo resuelve con su informe «Balance»: producto × grado × estado.
- **No se ve cuándo se revisó un móvil.** El rastro guarda el paso «En revisión → caja», pero no dice que eso es la
  revisión.

Objetivo: responder sin salir de la pantalla a «¿cuántos hay de cada color y grado?», «¿dónde está este SKU?» y
«¿quién tiene este móvil?», y que la ficha diga cuándo se revisó.

## 2. Decisiones del usuario (2026-10-17 y 2026-10-19)

- **Columna lateral compacta:**
  - una fila por modelo, con sus capacidades en línea (pastillas con el número);
  - la barra de listos / taller / revisión.
  - Se descartó el «mapa de la gama» (generaciones × variantes).
- **El buscador de la columna acepta modelo o SKU:** el SKU entero o solo el principio.
- **«Quién lo tiene»:**
  - una columna en la tabla de móviles y un filtro «Técnico»;
  - la Ubicación sigue siendo solo la caja de NSYS, sin mezclar.
  - Se descartaron «dentro de la ubicación» y «solo en la lista izquierda».
- **Revisado:** el móvil pasa de «sin caja» (En revisión) a tener caja. Lo pendiente de revisar no tiene caja.
- **El buscador de IMEI se queda como está:** al elegir un IMEI se abre su página en IMEIs, con «← Volver» al stock.
- **(D) Por defecto del prototipo v8, recomendados y sin confirmar del todo; se confirman en esta revisión:**
  - la tabla color × grado **en lugar de** los chips de color y de grado;
  - el grado con color por familias;
  - «Pegar varios IMEIs».
- **Fuera:**
  - el stock a una fecha pasada (para estadísticas);
  - elegir columnas;
  - importar los fallos de «Diagnostics». Esta columna del Items solo lista las pruebas fallidas: vacío vale tanto para
    «sin fallos» como para «sin diagnosticar», y no trae fecha. Queda como idea.

## 3. Qué ve cada rol

Igual que en la 0.9.17: «Stock de teléfonos», la ficha y el rastro, solo **SUPERTECNICO** y **ADMIN**. El TECNICO no
ve nada nuevo. La ruta nueva de la API (§6.2) lleva la misma guarda.

## 4. Pantalla «Stock de teléfonos»

La cabecera no cambia: título, total en stock, píldora de NSYS, banda roja, buscador de IMEI, botón «Pegar varios
IMEIs» (D) e interruptor «Ver también lo que no cuenta como stock».

### 4.1 Columna lateral compacta

- **Una fila por modelo** con stock (en el orden del catálogo, del más nuevo al más antiguo), con:
  - el nombre y el total;
  - una pastilla por capacidad con stock y su número;
  - la barra de listos / taller / revisión en el borde de abajo.
- **Al pulsar el nombre** se abre el modelo con la capacidad «Todas». Al pulsar una pastilla, con esa capacidad.
- **La fila elegida** queda resaltada y se desplaza a la vista si se llega a ella por el buscador.
- **Con el interruptor encendido**, el total sigue siendo solo el stock; lo que no cuenta se ve dentro del modelo.

### 4.2 Buscador de modelo o SKU

Encima de la columna: «Buscar modelo o SKU…». Ni distingue mayúsculas ni tiene en cuenta los espacios.

- **Modelo:**
  - escribir filtra la lista de modelos («13 pro» deja 13 Pro y 13 Pro Max);
  - con Enter, si el texto es exactamente un modelo y ningún SKU empieza así, se abre ese modelo.
- **SKU** (a partir de 2 caracteres):
  - debajo sale una lista con los SKU **en stock** que empiezan por lo escrito. Cada uno lleva su modelo, su capacidad
    y cuántos hay, con la parte escrita en negrita: hasta 7, y «y N más»;
  - la lista de modelos se reduce a los que tienen esos SKU.
- **Elegir un SKU** (con un clic, o con las flechas y Enter) abre su modelo y su capacidad y filtra la tabla a **ese SKU
  exacto**.
- **Enter sin elegir ninguno:**
  - filtra por los SKU que **empiezan** por lo escrito;
  - si todos son de un modelo, lo abre, y si son de una sola capacidad, también la elige;
  - si son de varios modelos, avisa: «Hay SKU de N modelos que empiezan así: elige uno o escribe más».
- **El filtro de SKU** sale en la cabecera del modelo como una pastilla, «SKU i13PRO128GrafitoA ×» o «SKU empieza por
  i13PRO128 ×», y la parte buscada sale resaltada en la columna SKU.
- **Escape** borra lo escrito.

### 4.3 Cabecera del modelo

- **El nombre del modelo** («iPhone 13 Pro»).
- **Pestañas de capacidad:** «Todas» y una por capacidad del catálogo, con su número; las que no tienen stock, apagadas.
  - Cambiar de pestaña quita los filtros, también el de SKU, porque un SKU es de una capacidad concreta.
  - Con «Todas», la tabla de móviles añade la columna «Cap.».
- **Cuántos móviles salen**, la pastilla del SKU si la hay y «Quitar filtros (n)».

### 4.4 Tabla color × grado (D)

En lugar de los chips de color y de grado (se puede ver en el prototipo):
- **Filas:** los colores del modelo con stock. **Columnas:** los grados con stock, en el orden de siempre.
- **Cada casilla:** el número y una barrita con el reparto listos / taller / revisión. Al pasar el ratón: «Grafito A:
  4 listos · 2 en taller · 1 en revisión». Las vacías, con un punto.
- **Fila y columna de totales.**
- **Filtrar:**
  - pulsar una casilla filtra por ese color y grado, y volver a pulsarla lo quita;
  - pulsar un color o un grado de la cabecera filtra solo por eso;
  - **Ctrl+clic** suma casillas.
- **Cuenta solo lo que es stock.** Respeta los filtros de Batería, Ubicación y Técnico, pero no su propia selección:
  la tabla se ve entera, con lo elegido resaltado.
- **A su derecha:** la ayuda, los desplegables (§4.5) y los avisos de «Quién lo tiene» (§4.7).

Si en la revisión se prefieren los chips, se quedan como en la 0.9.17. Los desplegables y los avisos van entonces
debajo de los chips.

### 4.5 Filtros

- **Batería** y **Ubicación:** como en la 0.9.17, con casillas y su número.
- **Técnico** (nuevo), con casillas y su número:
  - cada técnico que tiene algún trabajo abierto con los móviles del modelo;
  - y, separados, **«Sin asignar (en caja de taller)»** y **«Con descuadre»**.
  - Un móvil puede salir en varios (reparación de uno y glass de otro): basta con que coincida uno.
- **Estado:** el desplegable se quita.
  - Con el interruptor apagado todo es «Libre».
  - Con el interruptor encendido, lo que no cuenta ya se distingue en la tabla (§4.6).
- **Entre filtros distintos:** se combinan con Y. **Dentro del mismo filtro:** con O.

### 4.6 Tabla de móviles

Columnas: **IMEI · (Cap.) · Color · Grado · SKU · Ubicación · Quién lo tiene · Días**.
- **Grado:** la píldora del grado (§4.8) y, detrás, unas marcas pequeñas: «100» si la batería es nueva y «eSIM».
  Sustituyen a las columnas Batería y eSIM.
- **Ubicación:** la píldora de la caja. Lo que no cuenta como stock lleva detrás «NO CUENTA · De pedido» o «NO CUENTA ·
  No cuenta como stock», y la fila en gris como hoy. Sustituye a la columna Estado.
- **Quién lo tiene:** §4.7. Es la única columna que puede partirse en dos líneas.
- **Orden:**
  1. primero lo que cuenta;
  2. dentro de eso, los descuadres y los «en caja, sin asignar»;
  3. luego por días, de menos a más.
- **Clic en una fila:** abre la página del IMEI, como hoy.
- **A 1366×768 y a 1920×1080:** sin scroll de página ni scroll lateral en la tabla (comprobado en el prototipo).

### 4.7 «Quién lo tiene»

**De dónde sale:** de las asignaciones **abiertas** del ERP para el IMEI (`Reparacion` con `ID_REP` que empieza por `A`
y `FECHA_FIN` nula):
- `A…` es una reparación;
- `AG…`, una glass, con su entrega (`ENTREGADO_AT`);
- `AP…`, un pulido.

**Qué se ve:**

| Caso | Texto |
|---|---|
| Reparación | **Juan** |
| Reparación y glass | **Juan** · glass **María** `sin entregar` / `llegó 10:30` |
| Solo glass | **María** (glass) `sin entregar` / `llegó 10:30` |
| Pulido | **Pedro** (pulido) |
| Caja «Taller · …» y ningún trabajo abierto | **⚠ En caja, sin asignar** (ámbar) |
| Descuadre (abajo) | **⚠ Juan: trabajo abierto** (rojo) |
| Ningún trabajo y fuera de taller | — |

- **«llegó»** lleva la hora si es de hoy y el día si es de otro (como en Asignaciones).
- **Al pasar el ratón** se ve el detalle: «Reparación de Juan desde el 17/10 · Glass de María desde el 18/10: el móvil
  aún no le ha llegado». En un descuadre: «NSYS dice "Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o mover la
  caja?».
- **Descuadre:** hay un trabajo abierto asignado **hace más de 60 minutos** y el móvil está en «Listos» o es de pedido
  (caja «Pedidos realizados»).
  - Los 60 minutos cubren el retraso de NSYS: hasta 30 minutos de la exportación, más los 5 de la importación.
  - Un «Listos» con trabajo abierto puede publicarse en las webs mientras se repara, y por eso se avisa.
- **Avisos junto a los filtros,** para el modelo y la capacidad elegidos: «⚠ N en caja de taller sin asignar» y «⚠ N con
  descuadre». Solo salen si N > 0, y cuentan los móviles que se ven: con el interruptor apagado, solo el stock.
- **Frescura:** las asignaciones son del momento; la caja, de la última exportación de NSYS. El pie de la tabla lo
  explica en una línea.

### 4.8 Grado con color (D)

- **Una píldora con borde de color y fondo blanco por familia:**
  - A (A+, A, A-): azul;
  - B: violeta;
  - C: naranja;
  - Ms…: verde azulado;
  - ID: rosa;
  - `n/a`: ámbar con borde discontinuo.
- **Va con borde** para no confundirse con las ubicaciones, que son píldoras rellenas.
- **Dónde:** en la tabla de móviles, en la cabecera de la tabla color × grado y en el diálogo de §4.9. La ficha no
  cambia.
- **Los colores exactos** salen de los tokens del tema y se fijan en el plan.

### 4.9 Pegar varios IMEIs (D)

- **Cómo se abre:**
  - con el botón «Pegar varios IMEIs», junto al buscador de IMEI;
  - o pegando en ese buscador un texto con **más de un** IMEI, que abre el diálogo con lo pegado.
- **El diálogo «Comprobar una lista de IMEIs»** tiene un cuadro de texto y «Comprobar».
  - Se aceptan los IMEIs como vengan: uno por línea, separados por comas, puntos y comas o tabuladores, o copiados de
    una columna de Excel.
  - Como mucho **500** por vez.
- **Cada línea** se marca de una de estas formas:
  - **No es un IMEI:** no son 15 cifras ni un número de serie de 8-20 letras y cifras;
  - **Repetido (línea n);**
  - **No está en NSYS;**
  - **encontrado:** modelo y capacidad, color, grado, ubicación, «Quién lo tiene» y estado (Libre / De pedido / No
    cuenta / **Vendido dd/mm/aaaa** / Baja en NSYS).
- **Busca en todos los móviles del espejo**, también en los vendidos.
- **Arriba, un resumen:** «N en stock · N de pedido · N vendidos · N no están en NSYS · N repetidos · N no son un IMEI».
- **«Copiar el resultado (para Excel)»** copia una tabla separada por tabuladores al portapapeles. No descarga nada.
- **Una fila encontrada** abre su página del IMEI.

### 4.10 Lo que no cambia

- El buscador de IMEI de arriba y la página del IMEI.
- **«← Volver» al stock** conserva todo: el modelo, la capacidad, el filtro de SKU, los filtros y el interruptor. Se
  amplía el store de la 0.9.17 (`telefonos/stock/estado.ts`).
- El aviso de datos antiguos, la banda roja y la píldora de NSYS.

## 5. Ficha y rastro (IMEIs)

### 5.1 Regla de «revisado»

**Un lote se revisa al pasar de «sin caja» a una caja.** En el espejo, «sin caja» es la ubicación «En revisión», y el
cambio de caja es un evento `UBICACION`.

- **Revisión de entrada:** el primer `UBICACION` del lote con `DE` = «En revisión» y cuya caja de destino (`A_NSYS`)
  **no** es «Pedidos realizados».
  - Pasar de «sin caja» a «Pedidos realizados» es una **venta sin revisar** (Simulador y algo de Amazon), no una
    revisión.
- **Revisión de una devolución:** un `UBICACION` con `DE` = «Devolución» (la caja «Funcionalidad pendiente») hacia una
  caja que no sea «Pedidos realizados».
- **La hora** es la de la exportación en que se vio el cambio. La ficha y el rastro ya avisan de esos ± 30 min.
- **Solo hay pasos intermedios desde dic. 2023.** De antes, la ficha no dice nada de la revisión.

### 5.2 Ficha

Junto a la ubicación, una de estas:
- **«Revisado el 17/10/2026, hacia las 10:31»:** la última revisión del lote actual, de entrada o de devolución.
- **«Pendiente de revisar desde hace N días»:** si ahora mismo está en «En revisión».
  - N cuenta desde el último evento que lo dejó en «En revisión» y, si no lo hay, desde la fecha de recepción del lote.
- **Nada:** si no hay datos (anterior a dic. 2023, o vendido sin revisar).

### 5.3 Rastro

- **El evento de revisión** se rotula **«Revisado → Taller · Pulir»** (o «Revisado (devolución) → …») en lugar de
  «Ubicación: En revisión → Taller · Pulir».
- **Lleva un icono propio** y entra en la leyenda.
- **Los demás eventos no cambian.**

## 6. API (servidor)

Todo en `StockTelefonosController` (`/api/stock-telefonos`, SUPERTECNICO y ADMIN). Se regenera el contrato OpenAPI y los
tipos de la web.

### 6.1 `GET /api/stock-telefonos`: trabajos abiertos

- **Cada `MovilStock` añade `trabajos`:** la lista de sus trabajos abiertos, vacía si no tiene. Cada trabajo lleva:

  ```
  TrabajoAbierto { categoria: REPARACION | GLASS | PULIDO, tecnico: string,
                   desde: fecha-hora (Madrid), entregado: fecha-hora (Madrid) | null }
  ```

  - `entregado` solo se rellena en la glass.
  - Las horas de `Reparacion` (UTC) pasan a Madrid con `NsysDAO.utcAMadrid`, como en el rastro.
- **Sale de una sola consulta nueva en `NsysDAO`:** las asignaciones abiertas (`ID_REP LIKE 'A%'` y `FECHA_FIN IS
  NULL`, con el nombre del técnico). Se cruzan en Java por IMEI.
  - No se cruzan en SQL, para no depender de las intercalaciones entre `Nsys_movil` y `Reparacion`.
  - Son unos cientos de filas como mucho.
- **El descuadre, «en caja, sin asignar» y los textos los calcula la web.** El servidor da los datos.

### 6.2 `POST /api/stock-telefonos/comprobar` (nuevo)

- **Cuerpo:** `{ "imeis": ["…", …] }`. La web ya manda los IMEIs limpios y sin repetir, 500 como mucho; si llegan más,
  responde 400.
- **Respuesta:** una entrada por IMEI pedido.
  - Si no está en el espejo, con `encontrado: false`.
  - Si está, con los mismos datos que `buscar` (modelo, capacidad, color, grado sin batería, batería nueva, ubicación
    visible, estado y fecha de venta) más sus `trabajos`. Del IMEI se toma el lote más reciente.
- **Consulta:** `IN` por tandas de 500 sobre `Nsys_movil` y las asignaciones abiertas de esos IMEIs.

### 6.3 `GET /api/stock-telefonos/{imei}`: revisión

`FichaMovil` añade `revisadoEl` (fecha-hora o null), `revisadoDevolucion` (boolean) y `pendienteRevisionDesde` (fecha o
null), según §5.1-§5.2. Se calculan con los eventos que ya lee la ficha.

### 6.4 `GET /api/stock-telefonos/{imei}/rastro`: revisión

`EventoRastro` añade `revision` (null, `ENTRADA` o `DEVOLUCION`). Lo pone `Rastro.unir` en los eventos `UBICACION` que
cumplen §5.1. No cambian ni la tabla `Nsys_evento` ni el enum `TipoEvento`.

## 7. Rendimiento

- **El stock** suma una consulta pequeña (las asignaciones abiertas), muy por debajo del límite de 15 s.
- **La web sigue filtrando en local.** La tabla color × grado y los recuentos salen de la lista ya cargada.
- **`comprobar`:** con 500 IMEIs, una consulta con `IN` por tabla.

## 8. Pruebas

- **Servidor (JUnit):**
  - **Trabajos abiertos:**
    - solo los `A`/`AG`/`AP` con `FECHA_FIN` nula;
    - la categoría por el prefijo;
    - la entrega solo en la glass;
    - las horas en Madrid;
    - un IMEI con reparación y glass da dos trabajos.
  - **`comprobar`:**
    - encontrado, no encontrado y vendido con su fecha;
    - el lote más reciente;
    - más de 500 da 400;
    - la guarda de roles (TECNICO, 403).
  - **Revisado** (ficha y rastro):
    - de «En revisión» a una caja, `ENTRADA`;
    - de «En revisión» a «Pedidos realizados», nada;
    - de «Devolución» a una caja, `DEVOLUCION`;
    - pendiente desde el último evento a «En revisión», o desde la recepción;
    - sin eventos, todo null.
  - **`OpenApiContractTest`** al día.
- **Web (Vitest + MSW):**
  - **La columna compacta:** totales, pastillas, clic en el nombre o en una pastilla.
  - **El buscador:**
    - modelo exacto;
    - SKU exacto con flechas y Enter;
    - principio de SKU con Enter;
    - varios modelos con aviso;
    - Escape.
  - **La pastilla del SKU y su ×;** cambiar de pestaña quita el SKU.
  - **La tabla color × grado:** casilla, fila, columna, Ctrl+clic y recuentos con los demás filtros.
  - **El filtro Técnico:**
    - varios valores;
    - «Sin asignar»;
    - «Con descuadre»;
    - el límite de 60 minutos.
  - **Los textos de «Quién lo tiene»:** cada fila de la tabla de §4.7 y el tooltip.
  - **Las clases del grado** por familia.
  - **El diálogo de varios IMEIs:**
    - partir el texto, repetidos, no IMEI y más de 500;
    - el resumen;
    - copiar;
    - abrir por pegado.
  - **La ficha:** «Revisado el …», «Pendiente de revisar…» y nada.
  - **El rastro:** el rótulo «Revisado →».
  - **«← Volver»** conserva capacidad, SKU y filtros.
- **E2E (preprod, solo leen):** se amplía `stock-telefonos.spec.ts`:
  - **El supertécnico** ve «Quién lo tiene» y el filtro Técnico.
  - **El SKU:** busca un SKU tomado de la propia tabla y comprueba la pastilla.
  - **El diálogo de varios IMEIs:** lo abre con dos IMEIs tomados de la tabla y un número inventado, y ve «No está en NSYS».
  - **El técnico** sigue sin ver nada.

## 9. Compatibilidad, despliegue y vuelta atrás

- **Sin migración.** Solo se añaden campos a las respuestas y una ruta nueva.
  - La web 0.9.17 funciona con el servidor 0.9.18, porque los campos nuevos se ignoran.
  - La web 0.9.18 necesita el servidor 0.9.18. Se despliegan juntos: primero el servidor, luego la web.
- **Orden:**
  1. la 0.9.17 en producción, con su histórico terminado o en marcha;
  2. la 0.9.18 en preprod, con E2E y pruebas con datos reales;
  3. la 0.9.18 en producción, en horario si el corte es despreciable (sin migración, vuelta atrás simple).
- **Vuelta atrás:** volver a las imágenes de la 0.9.17. No hay nada que deshacer en la base de datos.

## 10. Pendiente (no bloquea el plan)

- **Confirmar los tres (D)** en la revisión de esta spec: tabla color × grado o chips, grado con color, y pegar varios IMEIs.
- **El límite de 60 minutos del descuadre:** revisarlo con el uso real.
- **Los fallos de diagnóstico** (columna «Diagnostics»): idea para más adelante (§2).
