# 0.9.25 — «Qué revisar» en Stock de teléfonos

Fecha: 2026-11-04. Estado: **diseño aprobado** (prototipo y spec, 04/11). Plan: `docs/superpowers/plans/2026-11-04-v0925-que-revisar.md`.
**Ajustes posteriores (04/11 tarde, tras la prueba en preprod): §3.6 (buscar) y §10 (prioridad por pedidos, «3 o más» y
Excel). Donde contradigan a lo de antes, mandan ellos. Con §10 la versión pasa a servidor + web. El 05/11, §10.10 («Revisar
sin grado» como la hoja, sin «Pedidos 15 d») manda sobre §10.1, §10.2, §10.6 y §10.9, y §10.11 («Subir B a A» con ningún
A) sobre §2.5.**
Prototipo: `assets/2026-11-04-v0925-prototipo-que-revisar.html`.
**Solo web**: sin cambios en el servidor ni en la base. Usa `GET /api/stock-telefonos`, que ya está en producción.
El número 0.9.25 es provisional: si otra versión entra antes, quien mergee segundo lo cambia.

## 1. Qué cambia

**Hoy** lo hace un Apps Script en una hoja de Google, `priorizarGradacion`:
- copia la «Public Stock Price List EU» (la lista pública, que sale del Items de NSYS a través del Excel de stock);
- escribe la hoja PriorizarRevision con tres tablas, agrupadas por modelo + capacidad + color:
  - «Solo n/a»: modelos-color que solo tienen `n/a`;
  - «Grado A con 1 unidad»: cada grado A, A-, A100 o A-100 con una sola unidad, y cuántos `n/a` hay;
  - «B para subir a grado A»: modelos-color con B y ningún A;
- escribe la hoja MENSAJES con un texto por cada fila `n/a`, B o B100;
- avisa a Zapier.

No usa precios ni ventas, y no ordena nada: las filas salen en el orden de la lista pública, que es orden de texto (64 GB
detrás de 256 GB). Tiene estos fallos:
- las columnas Unidades_A, Unidades_A- y Unidades_A100 siempre salen a 0, y además cuentan filas, no unidades;
- la tabla «Solo n/a» sale dos veces;
- MENSAJES no aplica ningún filtro: avisa de todos los `n/a` y de todos los B, aunque el modelo tenga A de sobra;
- «A con 1 unidad» mira grado a grado: un modelo con 1 A y 1 A100 sale dos veces;
- el B100 va en MENSAJES pero no en la tabla.

**Ahora** la pestaña «Qué revisar» de Stock de teléfonos hace lo mismo desde el espejo de NSYS, con estos fallos corregidos y con
los IMEI de cada móvil. Se actualiza con cada Items y lleva lo que ha corregido el taller.

**Decisiones del usuario (04/11):**

| Tema | Decisión |
|---|---|
| Dónde | Pestaña nueva «Qué revisar» en Stock de teléfonos, al lado de «Por pasar a NSYS». |
| Quién la ve | SUPERTECNICO y ADMIN, como el resto de Stock de teléfonos. Por eso la versión es solo web. |
| Qué stock entra | Solo lo que cuenta como stock. |
| Qué es «sin grado» | Como en el ERP: `n/a`, vacío o un código que no es un grado (§2.3). |
| Móviles con técnico | Salen marcados con quién lo tiene y no cuentan como pendientes. |
| eSIM | Aparte del físico. |
| Qué es «A» | A+, A y A-, con o sin batería nueva. Lo que lleva Ms no es A. |
| Tablas | Una lista de modelos-color con móviles sin grado, con su motivo, y otra de B para subir a A. |
| Pocos A | El total de A del modelo-color es 0 o 1. Número fijo en el código. |
| Subir de grado | B y B100 (B → A y B100 → A100), con la misma regla de pocos A. El MsB no sale. |
| Orden | Por motivo; dentro, modelo del más nuevo al más antiguo y capacidad de menor a mayor como número. Las ventas, al plan. |
| Detalle | Al desplegar un modelo-color, sus IMEI con ubicación, días en stock y quién lo tiene. |
| Acciones | Solo se mira. Un móvil se va de la lista solo, cuando NSYS o el taller le ponen grado. |
| Texto para pegar | Botón «Copiar lista» con el texto de MENSAJES, solo con lo pendiente. |
| Zapier | Fuera. |
| El script | Unos días a la vez, comparando las dos listas; después se apaga `priorizarGradacion`. Los dos scripts de correos no se tocan. |

## 2. Reglas

### 2.1 Qué móviles entran
Solo los que cuentan como stock (`cuentaComoStock`: estado `STOCK`), con el color y el grado que valen (lo del taller por encima
de NSYS). El interruptor «Ver también lo que no cuenta como stock» no cambia nada en esta pestaña.

### 2.2 Grupo
Un modelo-color es modelo + capacidad + color:
- **modelo**: el nombre de la columna lateral con « eSIM» detrás si el móvil es solo eSIM (`nombreMovilNsys`). Así
  «iPhone 14» e «iPhone 14 eSIM» son grupos distintos;
- **capacidad**: `capacidadDe` («—» si NSYS no la sabe);
- **color**: el que vale («—» si no hay).

### 2.3 Qué grado tiene cada móvil
El grado llega a la web sin el «100»: la batería nueva va aparte, en `bateriaNueva`.

- **Con grado**: A+, A, A-, B+, B, B-, C, con o sin Ms delante; Ms solo; ID, F, FD y RMA. Ms se mira sin distinguir
  mayúsculas; lo demás, también.
- **Sin grado**: todo lo demás: `n/a`, vacío o un código que no es ninguno de esos.
- **A**: A+, A o A-, sin Ms.
- **B para subir**: exactamente B, sin Ms. B+ y B- no salen, como en el script.

Es la regla de `GradoTaller` (grupo «como n/a»), con un ajuste: **Ms solo, B+ y B- cuentan como con grado**. `GradoTaller` los
trata como `n/a` porque no sabe qué pieza ponerles, pero aquí lo que importa es si el móvil está revisado, y lo están. La lista
pública tiene móviles «Ms» con precio.

### 2.4 Lista «Revisar sin grado»
Sale un modelo-color si tiene al menos un móvil sin grado y como mucho un A. Su motivo:

| Motivo | Cuándo | Pastilla |
|---|---|---|
| Nada con grado | Ningún móvil del grupo tiene grado. | roja |
| Ningún A | Hay alguno con grado (B, ID, Ms…), pero ningún A. | naranja |
| Solo 1 A | Hay exactamente un A. | ámbar |

El tope de un A es `MAX_A = 1`, una constante.

### 2.5 Lista «Subir B a A»
Sale un modelo-color si tiene al menos un B y como mucho un A. Cada móvil lleva su paso: «B → A», o «B100 → A100» si tiene
batería nueva. Es lo que hace el chasis en el ERP (`GradoTaller.conPieza`: la letra pasa a A y el 100 se queda). Un mismo
modelo-color puede salir en las dos listas.

### 2.6 Móviles con técnico
Un móvil con algún trabajo abierto (reparación, glass o pulido; `trabajos` no vacío) sale en gris con quién lo tiene (la
misma celda «Quién lo tiene» de la tabla de móviles). **No cuenta como pendiente** y no va en «Copiar lista». Un modelo-color cuyos móviles de esa lista
tiene todos algún técnico sale en gris, en su sitio del orden, y no cuenta en la cifra de la tarjeta. «Sin asignar (en caja de taller)» no es un
técnico: cuenta como pendiente.

En las cuentas de A y de «con grado» entran todos los móviles del grupo, los tenga un técnico o no: dicen lo que hay en stock.

### 2.7 Orden
- **Grupos**: por motivo (Nada con grado → Ningún A → Solo 1 A), solo en la primera lista. Después, por modelo, como la columna
  lateral (`modelosLista`): los del catálogo del más nuevo al más antiguo, después los que solo tienen el modelo del maestro y
  al final los que no tienen modelo. Después, capacidad de menor a mayor como número (`capacidadGb`: 256 GB antes que 1 TB), y
  color por orden alfabético. Del mismo modelo, el físico va antes que el eSIM.
- **Móviles de un grupo**: los de más días en stock primero; a igualdad, por IMEI.

### 2.8 Diferencias con el script
Las tiene que conocer quien compare las dos listas (§7, punto 5):

| Caso | Script | ERP |
|---|---|---|
| Modelo con 1 A y 1 A100 | Sale dos veces en «A con 1 unidad». | Tiene 2 A: no sale. |
| `n/a` de un modelo con B, ID o Ms y ningún A | No sale en ninguna tabla (solo en MENSAJES). | «Ningún A». |
| Modelo-color con 1 A, sin `n/a` ni B | Sale en «A con 1 unidad» con 0 `n/a`. | No sale: no hay nada que revisar ni que subir. |
| B100 | Solo en MENSAJES. | En «Subir B a A», como B100 → A100. |
| A+ | No cuenta como A. | Cuenta como A. |
| Grado vacío | No cuenta como `n/a` ni como grado. | Sin grado. |
| Móvil con técnico | Cuenta. | Sale en gris y no cuenta. |
| Grado corregido en el taller y aún no pasado a NSYS | El de NSYS. | El del taller. |
| MENSAJES | Todos los `n/a`, B y B100. | Solo lo pendiente de las dos listas. |

## 3. Pantalla

### 3.1 Pestaña
El grupo «Vista» de la cabecera tiene un tercer botón: **«Qué revisar (N)»**. N va en el color de «Por pasar a NSYS»
(`text-taller-text`) y es la suma de las cifras de las dos tarjetas. La vista se guarda en el estado de la página
(`estadoStockTelefonos.vista`), como las otras dos.

### 3.2 Tarjetas
Como en «Por pasar a NSYS»: arriba dos tarjetas con su cifra, un subtítulo y un botón. Debajo, la ayuda y la tabla de la
elegida. Se entra en la primera.

| Tarjeta | Cifra | Subtítulo | Botón |
|---|---|---|---|
| Revisar sin grado | Modelos-color con algún móvil sin grado pendiente. | n/a de modelos con 0 o 1 A | Copiar lista (N) |
| Subir B a A | Modelos-color con algún B pendiente. | B y B100 de modelos con 0 o 1 A | Copiar lista (N) |

Junto a la cifra, cuántos móviles pendientes suman: «8 modelos-color · 19 móviles». N del botón son los modelos-color. El
borde de arriba de cada tarjeta lleva un color: ámbar en «Revisar sin grado» y violeta en «Subir B a A».

Ayudas:
- **Revisar sin grado**: «Los modelos-color con móviles sin grado (n/a) y como mucho un A en stock. Primero los que no tienen
  nada con grado, después los que no tienen ningún A y al final los que solo tienen uno. Un móvil sale de la lista cuando NSYS
  o el taller le ponen grado.»
- **Subir B a A**: «Los modelos-color con móviles B y como mucho un A en stock: candidatos a chasis (B → A; con batería nueva,
  B100 → A100). Un móvil sale de la lista cuando su grado pasa a A.»

### 3.3 Tablas
Una fila por modelo-color. Al pulsarla se despliega con sus móviles, y al volver a pulsarla se pliega. La tarjeta elegida y lo
desplegado se guardan en el estado de la página (`estadoStockTelefonos.queRevisar`). Así siguen igual al volver del detalle de un
IMEI con «← Volver», al cambiar de pestaña y volver, y cuando el sondeo de 60 s trae el stock nuevo. Se limpian al cerrar
sesión. (Ajuste de la revisión final del 04/11: antes iban en el componente y se perdían al abrir un IMEI.)

- **Revisar sin grado**: Motivo (pastilla) · Modelo · Cap. · Color · Sin grado (los pendientes y, en gris, «+1 con técnico» si
  hay) · A en stock (0 o 1; al pasar el ratón, cuál: «1 A+») · Con grado (cuántos móviles del grupo tienen grado).
- **Subir B a A**: Modelo · Cap. · Color · A en stock · B · B100.
- **Móviles desplegados**: solo los de esa lista (en «Revisar sin grado», los sin grado; en «Subir B a A», los B). Columnas:
  IMEI (enlace al detalle del IMEI, como en la tabla de móviles) · Grado (en «Subir B a A», «B → A» o «B100 → A100») ·
  Ubicación (píldora) · Días en stock · Quién lo tiene.
- **Pie**, debajo de la tabla: «Solo cuenta lo que es stock. Un móvil con un trabajo abierto sale en gris y no cuenta ni se
  copia. «A» es A+, A o A-, con o sin batería nueva.»

### 3.4 Copiar lista
Copia al portapapeles lo pendiente de la tarjeta, en el orden de la tabla, con el formato de MENSAJES. Los bloques van
separados por una línea en blanco:

```
🔍 REVISAR PRIORITARIO
iPhone 13 128 GB Medianoche
Sin grado: 3 · Nada con grado
```

```
⬆️ SUBIR DE GRADO
iPhone 13 512 GB Azul
B → A: 1 · B100 → A100: 1
```

En «Subir de grado» se quita la parte que valga 0. La capacidad se escribe como en las pestañas de capacidad
(`textoCapacidad`: «128 GB», con espacio). Después sale el aviso «Copiada la lista: N modelos-color». El prototipo enseña
además el texto en un diálogo, para verlo; la app no lo lleva. Sin nada
pendiente, el botón está apagado, con el título «No hay nada pendiente». Se usan `copiarAlPortapapeles` y `avisar`, como en
«Cambio de grado».

### 3.5 Vacío, carga y error
- **Vacío**:
  - Revisar sin grado: «Nada que revisar: todos los modelos-color con móviles sin grado tienen 2 A o más.»
  - Subir B a A: «Ningún B que subir.»
- **Carga y error**: los mismos textos que la tabla de móviles. Mientras carga, «Cargando…». Si no hay datos, «No se pudo cargar
  el stock de teléfonos.».
- **El número de la pestaña**: sin datos todavía, solo «Qué revisar».

### 3.6 Buscar por palabras clave (añadido del 04/11, tras la primera prueba en preprod)

Petición del usuario: «buscaría por IMEI y también por SKU o modelo», con **la misma caja de arriba**, sin otra nueva.

- **En «Qué revisar»**, la caja de arriba («Buscar IMEI en todos los móviles…») pasa a decir **«Buscar IMEI, modelo o
  SKU…»** (nombre accesible «Buscar IMEI, modelo o SKU en Qué revisar») y admite espacios. En «Móviles» y «Por pasar a NSYS»
  no cambia nada: sigue quitando lo que no son letras y cifras y solo busca IMEI.
- **Filtra las filas** de las dos tablas (la de la tarjeta elegida y la otra al cambiar): queda un modelo-color si **cada
  palabra** encaja en alguno de estos sitios. Las palabras se comparan sin mayúsculas y sin acentos:
  - su descripción, partida en **piezas**: cada palabra del modelo, la capacidad y el color, y los tres enteros y sin espacios
    («iphone12pro», «128gb», «1tb», «titanionegro»). Una palabra **numérica** tiene que ser una pieza **entera** («12»
    encuentra el iPhone 12, 12 Mini o 12 Pro, pero no «128 GB» ni «512 GB»); una palabra **con letras**, el **principio** de una
    pieza («tit» encuentra «Titanio»). Ejemplos: «13 pro 256», «128gb», «1tb», «14 esim», «púrpura». (Ajuste del 04/11 tras la
    prueba en preprod: antes se buscaba un trozo del texto sin espacios y «iphone 12» encontraba todos los de 128 y 512 GB.)
  - el **principio** del SKU de alguno de sus móviles, los de esa lista o no («i13128Azul»). Un `n/a` casi nunca tiene SKU en
    NSYS, así que un modelo-color con solo `n/a` no se encuentra por SKU;
  - con **6 caracteres o más** (`MIN_IMEI`), el IMEI de uno de los móviles de esa lista. Ese modelo-color se abre solo y el
    móvil sale resaltado en amarillo mientras se busca. Con menos de 6, «13» o «128» no encuentran IMEIs.
- **Las tarjetas, el número de la pestaña y «Copiar lista»** siguen con todo lo pendiente, no solo con lo filtrado.
- Sin nada que coincida: **«Ningún modelo-color con «…».»**
- **Sugerencias de IMEI** (la lista de siempre para abrir la ficha): en «Qué revisar» solo salen si lo escrito son solo cifras
  y al menos 6; con palabras o un SKU no salen.
- Lo escrito se guarda en el estado de la página (`estadoStockTelefonos.queRevisar.buscar`): sigue igual al volver del detalle
  de un IMEI.

## 4. Datos

- **`GET /api/stock-telefonos`** (ya en producción), con `useStockTelefonos`: la página ya lo pide y lo sondea cada 60 s. La
  pestaña usa los mismos `moviles` ya preparados (`preparar`, con «Quién lo tiene»), sin otra petición.
- **Campos que se usan**: `modelo`, `modeloMaestro`, `esEsim`, `capacidad`, `color`, `grado`, `bateriaNueva`, `ubicacion`,
  `estado`, `dias`, `trabajos` (y `quien`, calculado en la web).
- **Permisos**: SUPERTECNICO y ADMIN (`StockTelefonosController`), como la página.

## 5. Código (web)

En `src/modules/telefonos/stock/`:

- **`queRevisar.ts`** (puro):
  - `tieneGrado(grado)`, `esA(grado)` y `esB(grado)`, con las reglas de §2.3;
  - `MAX_A = 1`;
  - `listaRevisar(moviles)` y `listaSubir(moviles)`: los grupos de §2.4 y §2.5 en el orden de §2.7, cada uno con su clave,
    nombre, capacidad, color, motivo, sus cuentas (pendientes, con técnico, A, con grado, B, B100) y sus móviles ordenados;
  - `textoRevisar(grupos)` y `textoSubir(grupos)`: el texto de §3.4;
  - `cifras(moviles)`: las dos cifras de las tarjetas, para la pestaña.

  El orden de modelos y capacidades sale de lo que ya usa `modelosLista` (`MODELOS_ORDENADOS`, el rango sin catálogo,
  `capacidadGb`). Si hace falta, se saca a una función compartida dentro de `modelo.ts`, sin cambiar el orden de la columna.
- **`VistaQueRevisar.tsx`** (no `QueRevisar.tsx`: en Windows chocaría con `queRevisar.ts` al importar): las tarjetas, la
  ayuda, las dos tablas y el desplegado. Las tarjetas son propias, con las mismas clases que `TarjetasNsys`. Las píldoras de
  ubicación, los colores de grado y la celda «Quién lo tiene» (`CeldaQuien`), los de la tabla de móviles.
- **`StockTelefonosPage.tsx`**: el tercer botón de la vista con su número y, en esa vista, `<VistaQueRevisar … />` con las dos
  listas, que la página calcula con `useMemo` (las necesita también para el número de la pestaña).
  El tipo de `vista` de `estadoStockTelefonos` suma `'que-revisar'`.

## 6. Casos límite

- **Grado corregido en el taller**: cuenta el que vale. Un B al que el taller pone chasis pasa a A en cuanto se guarda, aunque
  NSYS siga diciendo B. Sale de «Subir B a A» y cuenta como A.
- **Modelos fuera del catálogo** (solo con el modelo del maestro, o «Sin modelo en el maestro»): son grupos como los demás y van
  al final, como en la columna lateral.
- **«En revisión»** (sin caja en NSYS) es la pila que espera a que la revisen (confirmado por el usuario el 04/11). Sus
  móviles sin grado cuentan como pendientes, igual que los de cualquier otra caja.
- **Revisado y aún sin grado en el ERP**: el ERP no sabe qué tiene alguien en la mano ni si ya lo ha revisado. Un móvil sale
  de la lista cuando su grado llega con el siguiente Items (más o menos cada media hora) o cuando el taller lo corrige; hasta
  entonces sigue en ella. La hora del último Items está en la píldora NSYS de la cabecera.
- **Stock grande**: el stock entero ya llega a la web. Las listas son una pasada en memoria con `useMemo`.
- **Sondeo con la pestaña abierta**: las listas se recalculan solas. Un grupo que se queda sin nada desaparece; los desplegados
  siguen desplegados.

## 7. Pruebas

1. **`queRevisar.test.ts`**:
   - cada regla de §2.3, con vacío, `n/a`, A+, Ms, MsB, B+, F, ID y un código raro;
   - los tres motivos y el tope de un A;
   - eSIM aparte;
   - móviles con técnico: no cuentan, y un grupo solo con técnico sale en gris;
   - B y B100 por separado, y el MsB fuera;
   - el orden (256 GB antes que 1 TB, modelo del más nuevo, físico antes que eSIM, días);
   - el texto exacto de los dos «Copiar lista»;
   - los casos del prototipo.
2. **`VistaQueRevisar.test.tsx`**: las cifras de las tarjetas, el cambio de tarjeta, desplegar y plegar, «Copiar lista» (texto y
   aviso) y los vacíos.
3. **`StockTelefonosPage.test.tsx`**: el tercer botón con su número y el cambio de vista.
4. **E2E en preprod** (solo lectura): abrir Stock de teléfonos, ir a «Qué revisar», ver las dos tarjetas, desplegar una fila si
   hay y comprobar que sale un IMEI. No escribe nada.
5. **Comparar con el script** (decisión 15): unos días en producción, mirar la lista del ERP al lado de PriorizarRevision. Las
   diferencias tienen que ser solo las de §2.8.

## 8. Versión y entrega

- Rama `dev_v0925_que_revisar` de la web desde `main` (`344c29d`, 0.9.24), en un worktree (`wt/web-0925-que-revisar`).
- `package.json` a `0.9.25`. CHANGELOG: entrada `[0.9.25]`.
- Preprod (E2E) → producción **en horario**: solo web, sin migración, con un corte despreciable → merge `--no-ff` a `main` de
  la web → gitlink en la raíz.
- Tras la comparación (§7, punto 5), el usuario quita el activador de `pipelineCompleto` en el Apps Script.
- Merge, push, tag y despliegues, solo con el OK del usuario.

## 10. Ajuste del 04/11 tarde: prioridad por pedidos, «3 o más» y Excel (manda sobre §2-§8)

**Por qué:** el usuario explicó cómo se usa hoy la hoja. Se abre el fichero del script, en «Solo n/a» se filtra «Saldo
stock» a **3 o más** y eso es lo que se revisa, sobre papel impreso. Las otras tablas no se miran. Decisiones del usuario:

| Tema | Decisión |
|---|---|
| Qué entra en «Revisar sin grado» | **Todos** los modelos-color con algún móvil sin grado: se quita el tope de 1 A. |
| Orden | **Lo que más se pide primero** («Pedidos 15 d») y, a igualdad, **lo que más queda sin revisar**. |
| «Lo que se pide» | Vendidos en los **últimos 15 días** + los que están **de pedido** ahora, en una sola columna. |
| «3 o más» | Interruptor «Solo con 3 o más sin revisar», **encendido al entrar**. |
| «Subir B a A» | **Como está**: tope de 1 A y orden por modelo. Es una tabla pequeña. Solo cambia lo del Excel. |
| Texto para WhatsApp | **Fuera.** «Copiar lista» se cambia por **«⬇ Descargar Excel»** para imprimir. |
| Excel | **Una fila por móvil**, con una columna vacía para marcar a boli. |
| Versión | **Todo en la 0.9.25**, que pasa a servidor + web (sin migración). Se vuelve a probar en preprod. |

### 10.1 «Revisar sin grado»: qué entra y en qué orden
- Entra un modelo-color si tiene al menos un móvil sin grado (§2.3). `MAX_A` deja de aplicarse en esta lista y sigue en
  «Subir B a A».
- Motivo: los tres de §2.4 y uno nuevo, **«Con A»** (2 A o más), con pastilla gris (`bg-crema text-azul-gris`).
- Orden:
  1. «Pedidos 15 d», de más a menos;
  2. pendientes (sin grado y sin técnico), de más a menos;
  3. motivo (Nada con grado → Ningún A → Solo 1 A → Con A);
  4. el orden de §2.7 (modelo, físico antes que eSIM, capacidad como número, color).
- **Interruptor «Solo con 3 o más sin revisar»**, encima de la tabla y encendido al entrar. Deja solo los modelos-color con
  **3 o más pendientes** (`MIN_PENDIENTES = 3`; los del técnico no cuentan). Se guarda en el estado de la página
  (`estadoStockTelefonos.queRevisar.soloTres`, `true` al empezar). Solo se ve en «Revisar sin grado».
- Las tarjetas y el número de la pestaña siguen con todo, como con la búsqueda (§3.6). El interruptor y la búsqueda solo
  filtran la tabla y el Excel.

### 10.2 «Pedidos 15 d»
- Por modelo-color, con la misma clave de §2.2 (modelo con eSIM, capacidad y color):
  - los **vendidos** con fecha de venta en los últimos 15 días (`DIAS_PEDIDOS = 15`; fecha de venta ≥ hoy − 14, hora de
    Madrid, hoy incluido);
  - más los que están **de pedido** ahora (estado `DE_PEDIDO`, ya en `GET /api/stock-telefonos`).

  Un móvil nunca está en los dos a la vez.
- Columna **«Pedidos 15 d»** en «Revisar sin grado», detrás de «Sin grado». Al pasar el ratón: «9 vendidos + 3 de pedido».
- **Datos de ventas: ruta nueva `GET /api/stock-telefonos/vendidos?dias=15`** (SUPERTECNICO y ADMIN; `dias` de 1 a 90, 15 por
  defecto). Devuelve un elemento por IMEI vendido en esos días, con su lote más reciente: `modelo`, `modeloMaestro`, `esEsim`,
  `capacidad`, `color` (el que vale, con lo del taller) y `fechaVenta`. Lee los `VENDIDO` del espejo con `SOLD_DATE` en el
  rango. La web los agrupa con la misma clave que el stock. Se pide una vez al abrir la pestaña y con el mismo sondeo que el
  stock.
- Si la ruta de ventas falla, solo cuentan los de pedido. La columna lleva una marca «sin ventas» en el título y no se bloquea
  nada.

### 10.3 «⬇ Descargar Excel» en vez de «Copiar lista»
- Se quitan «Copiar lista», sus textos (`textoRevisar`, `textoSubir`, `avisoCopiadoLista`) y los bloques «🔍 REVISAR
  PRIORITARIO» / «⬆️ SUBIR DE GRADO» de §3.4. Las tarjetas se quedan sin botón.
- Encima de la tabla de la tarjeta elegida, a la derecha (en «Revisar sin grado», junto al interruptor): **«⬇ Descargar Excel
  (N)»**. N son los móviles que irán en el Excel: lo que se ve (interruptor y búsqueda), sin los del técnico. Sin nada, va
  apagado y con el título «No hay nada pendiente».
- Al pulsarlo se abre «Guardar como» en el mismo clic (`elegirDestino` y `TIPO_XLSX`, como las plantillas de NSYS en la
  0.9.22.1), con el nombre `Que_revisar_sin_grado_AAAA-MM-DD.xlsx` o `Que_revisar_subir_B_a_A_AAAA-MM-DD.xlsx`.
- Después, la web manda las filas a **`POST /api/stock-telefonos/que-revisar/excel`** y guarda el xlsx que vuelve. Aviso
  «Descargado: N móviles». Si se cancela, no se pide nada.
- **Una fila por móvil**, en el orden de la tabla, móvil a móvil dentro de cada modelo-color:
  - Revisar sin grado: Modelo · Capacidad · Color · IMEI · Ubicación · Días en stock · Pedidos 15 d · **Revisado** (vacía);
  - Subir B a A: Modelo · Capacidad · Color · IMEI · Paso («B → A» / «B100 → A100») · Ubicación · Días en stock · **Hecho**
    (vacía).
- **El servidor no sabe las reglas.** Recibe `{ hoja, columnas, filas }` (textos) y escribe el xlsx con Apache POI, como
  `PlantillaNsys`. Lo prepara para imprimir:
  - cabecera en negrita, repetida en cada página;
  - apaisado y ajustado a una página de ancho;
  - bordes finos;
  - anchos de columna según el contenido.

  Límites (400 si se pasan): hasta 5000 filas, de 1 a 12 columnas, celdas de hasta 200 caracteres y el nombre de hoja de
  hasta 31. SUPERTECNICO y ADMIN.

### 10.4 Lo que no cambia
- La búsqueda (§3.6), el desplegado, «← Volver», «Subir B a A» (salvo el Excel), los permisos y que sea de solo lectura.

### 10.5 Servidor y entrega
- Servidor 0.9.25: las dos rutas nuevas, sin migración (usa `Nsys_movil`). Su índice `idx_nsys_movil_estado` filtra los
  `VENDIDO`. Hay que medirlo en preprod con el histórico entero, contra el límite de 15 s.
- La web nueva necesita el servidor nuevo. Con el servidor 0.9.24:
  - las ventas fallan y solo cuentan los de pedido (§10.2);
  - el Excel falla con su aviso de error.

  Por eso en producción va primero el servidor y después la web, o los dos a la vez. El servidor nuevo no rompe la web 0.9.24.
- Pruebas: §7 cambia con esto. Las reglas (orden, sin tope, «Con A», interruptor, pedidos), las dos rutas del servidor (con el
  test de contrato `OpenApiContractTest`), la web (columna, interruptor, botón, descarga) y la E2E de solo lectura (columna e
  interruptor; descarga del Excel sin escribir nada en la base).

### 10.7 Fuera el interruptor «3 o más» (05/11, prueba en preprod)
El usuario, al probarlo: «lo de solo tres o más no hace falta». El orden ya pone arriba lo que más se pide y, a igualdad, lo que
más queda sin revisar, así que se ve **todo** y el Excel lo lleva **todo**. Se quitan `MIN_PENDIENTES`, `soloTres` (función y
estado), el interruptor y el mensaje «Ningún modelo-color con 3 o más sin revisar…». Lo que queda de §10.1-§10.3 no cambia. El
servidor no cambia.

### 10.9 «De pedido» solo lo que entró en «Pedidos realizados» en los últimos 15 días (05/11)
**Lo que se vio en preprod:** «de pedido» contaba todo lo que hay en «Pedidos realizados» (1061 móviles). Entre ellos había restos
de hace años que nadie liberó en NSYS. Ejemplo: el grupo «Sin modelo en el maestro» sumaba 68 pedidos, 8 vendidos y 60 de pedido
con una mediana de 988 días en stock. El usuario preguntó por ShippyPro. Cubre solo Amazon y las webs y da los pedidos pendientes;
el Simulador va aparte. La fuente buena son los pedidos de la **pieza 2** (0.9.26: ShippyPro + Simulador + PEDIDOS 2026 en el
ERP).

**Decisión:**
- **Ahora**, NSYS limpio: «Pedidos 15 d» = vendidos (`Released`) en 15 días + los móviles de pedido **que entraron** en una caja de
  pedido (`Nsys_ubicacion.ES_PEDIDO`, p. ej. «Pedidos realizados») en los últimos 15 días. La fecha es la del cambio de caja en los
  eventos del espejo (`Nsys_evento`).
- **Después**, con la 0.9.26 en producción, pasar a los pedidos de la pieza 2 (al backlog).

**Cómo:**
- **Servidor:** ruta nueva `GET /api/stock-telefonos/de-pedido?dias=15` (1-90, 15 por defecto, SUPERTECNICO y ADMIN). Devuelve un
  elemento por IMEI de pedido (estado `DE_PEDIDO`, su lote más reciente) cuya última entrada en una caja de pedido cae en el
  rango, con los mismos campos que `vendidos` más `pedidoDesde`.
- **Web:**
  - deja de contar como de pedido los `DE_PEDIDO` del stock y usa esa ruta;
  - un IMEI que esté en las dos cuenta una vez, como de pedido;
  - si falla una de las dos lecturas, la columna lo dice como hoy con las ventas (cabecera, su título y el de cada celda):

    | Falla | Cabecera | Título de la cabecera | Título de la celda |
    |---|---|---|---|
    | ventas | «Pedidos 15 d (sin ventas)» | «No se pudieron leer las ventas: solo cuentan los de pedido» | «3 de pedido (sin ventas)» |
    | de pedido | «Pedidos 15 d (sin de pedido)» | «No se pudieron leer los de pedido: solo cuentan las ventas» | «9 vendidos (sin de pedido)» |
    | las dos | «Pedidos 15 d (sin datos)» | «No se pudieron leer ni las ventas ni los de pedido» | «Sin datos de pedidos» |

    La cabecera del Excel es la misma que la de la tabla.
  - la ayuda dice «vendidos y pasados a pedido en los últimos 15 días» donde decía «vendidos en los últimos 15 días y de pedido
    ahora».
- **Sin modelo:** el grupo «Sin modelo en el maestro» no lleva pedidos («—», cuenta como 0). Junta productos distintos que no se
  sabe qué modelo son. En la tabla y en el Excel sale «—»; el título de la celda dice «Sin modelo: no se sabe qué se pide».

### 10.8 F, FD y RMA no cuentan para el motivo (05/11, comparación con el script)
Al comparar en preprod la tabla principal del script con «Nada con grado», coincidían las 63 filas salvo, entre otras, dos
modelos-color con un **F** o un **RMA** además de sus `n/a`. La lista pública no lleva esos grados, así que la hoja los sacaba
como «solo n/a», y el ERP como «Ningún A». Decisión del usuario: F, FD y RMA siguen siendo «revisados» (§2.3: no son pendientes),
pero **para el motivo y la columna «Con grado» solo cuenta un grado vendible**: A±, B±, C con o sin Ms, Ms solo e ID. Un
modelo-color con `n/a` y solo F/FD/RMA es «Nada con grado».

### 10.6 Textos y aclaraciones que fija el plan (Tasks 10-16)
- **Textos de «Revisar sin grado»:**
  - subtítulo: «n/a en stock, lo más pedido primero»;
  - ayuda: «Los modelos-color con móviles sin grado (n/a). Primero lo que más se pide (vendidos en los últimos 15 días y de
    pedido ahora) y, a igualdad, lo que más queda sin revisar. Un móvil sale de la lista cuando NSYS o el taller le ponen
    grado.»;
  - vacío: «Nada que revisar: ningún móvil sin grado en stock.»;
  - con el interruptor encendido y nada con 3 o más: «Ningún modelo-color con 3 o más sin revisar: apaga «Solo con 3 o más sin
    revisar» para verlos todos.»;
  - pie: «Solo cuenta lo que es stock. Un móvil con un trabajo abierto sale en gris y no cuenta ni va en el Excel. «A» es A+, A
    o A-, con o sin batería nueva.»
- **Sin ventas:**
  - la cabecera dice «Pedidos 15 d (sin ventas)», con el título «No se pudieron leer las ventas: solo cuentan los de pedido»;
  - la celda dice «3 de pedido (sin ventas)»;
  - en el Excel, la cabecera también lleva «(sin ventas)».
- **«Nunca en los dos a la vez»** lo hace cumplir la web. Un IMEI vendido que hoy está de pedido cuenta solo como de pedido.
  Un vendido que volvió y hoy está en stock sigue contando como vendido, porque fue una venta.
- **Cuándo se piden los vendidos:** solo con la pestaña abierta, con el mismo sondeo que el stock. El número de la pestaña no
  depende de ellos.
- **Qué mensaje sale:** si la búsqueda deja filas pero el interruptor las quita todas, el del interruptor. Si la búsqueda no
  deja ninguna, el de la búsqueda.
- **Anchos del Excel:** se calculan por caracteres, con un tope de 60, en vez de con `autoSizeColumn`, que necesita fuentes del
  sistema que el contenedor no tiene.
- **Servidor 0.9.24:** `GET …/vendidos` cae en `GET /{imeiSn}` y da 404. La web lo toma como «sin ventas», sin diálogo.

### 10.10 «Revisar sin grado» como la hoja (05/11; manda sobre §10.1, §10.2, §10.6 y §10.9)
**Por qué:** tras ver los «de pedido» en preprod, el usuario: «vamos a dejarlo simple, como lo hacemos en el Google Sheet,
usamos la misma forma de calcular y a ver si sale lo mismo; soy consciente de que perderíamos la mejora de lo que más se
vende». Los datos de ventas no son fiables hoy: hay de pedido de hace años, y los vendidos dependen de cuándo NSYS los pasa a
Released. La prioridad por lo que más se pide vuelve con los pedidos de la pieza 2 (0.9.26), al backlog. Con los datos de
preprod del 05/11: 66 modelos-color con 126 móviles salen como en «Solo n/a», 11 de ellos con 3 o más. Quedan fuera 472 `n/a`
de 144 modelos-color que ya tienen algún grado; la hoja tampoco los saca. El usuario eligió **solo como la hoja**.

**Reglas de «Revisar sin grado»:**
- Entra un modelo-color si tiene **algún móvil sin grado** (§2.3) y **ningún grado vendible** (§10.8: F, FD y RMA no cuentan).
  Es la tabla «Solo n/a» de la hoja. En cuanto NSYS o el taller le ponen un grado vendible a un móvil, el modelo-color entero
  sale de la lista, como en la hoja.
- **Orden:** primero lo que más queda sin revisar (pendientes: sin grado y sin técnico; es el «Saldo stock» de la hoja); a
  igualdad, el orden de §2.7. Los de 3 o más quedan arriba sin interruptor (§10.7).
- **Fuera:** el motivo (todos serían «Nada con grado»), «Pedidos 15 d», «A en stock» y «Con grado» (siempre 0). También
  `MAX_A` deja de aplicarse aquí; sigue en «Subir B a A».
- **Columnas:** Modelo (con el desplegable delante, como en «Subir B a A») · Cap. · Color · Sin grado (con «+N con técnico»).

**Textos:**
- subtítulo: «Solo n/a, lo que más queda primero»;
- ayuda: «Los modelos-color en los que nada tiene grado, solo n/a (la tabla «Solo n/a» de la hoja). Primero lo que más queda sin
  revisar. Un modelo-color sale de la lista en cuanto NSYS o el taller le ponen grado a uno de sus móviles; F, FD y RMA no
  cuentan.»;
- vacío: «Nada que revisar: ningún modelo-color con todo sin grado en stock.»;
- el pie no cambia.

**Excel de «Revisar sin grado»:** Modelo · Capacidad · Color · IMEI · Ubicación · Días en stock · Revisado (vacía). Sin
«Pedidos 15 d».

**Servidor:** se quita `GET /api/stock-telefonos/vendidos` (ruta, DAO, tests, contrato). La ruta de «de pedido» de §10.9 no se
hace. Queda solo `POST /api/stock-telefonos/que-revisar/excel`. La 0.9.25 sigue siendo servidor + web. Con el servidor 0.9.24,
el Excel falla con su aviso y lo demás funciona.

**Web:** fuera `useVendidos`, el tipo de los vendidos, «sin ventas» y sus textos, `DIAS_PEDIDOS` y la cuenta de pedidos.
«Subir B a A», la búsqueda, el Excel de «Subir B a A», las tarjetas y el número de la pestaña no cambian.

### 10.11 «Subir B a A» con ningún A, como la hoja (05/11; manda sobre §2.5 y el «Pocos A» de §1)
**Por qué:** el usuario, al ver cómo decide: «faltan A» en un modelo-color es lo que dispara subir sus B. La hoja lo pide con
**0 A**; el ERP lo pedía con 0 o 1 A, para adelantarse. El usuario eligió **solo 0 A, como la hoja**. Los B100 se quedan
(«B100 → A100»): la hoja ya los avisaba por MENSAJES. Con los datos de preprod del 05/11 salen 6 modelos-color, en vez de 9.
Dejan de salir el 13 mini 128 Blanco, el 15 Plus eSIM 512 Azul y el 14 Plus eSIM 512 Azul, que tienen 1 A.

**Reglas:** sale un modelo-color si tiene algún B o B100 (exactamente B, sin Ms) y **ningún A** (A+, A o A-, con o sin batería
nueva; cuentan también los del técnico). `MAX_A = 0`. El orden (§2.7), el paso de cada móvil, los del técnico y el Excel no
cambian. Un modelo-color sale de la lista en cuanto tiene un A.

**Pantalla:** fuera la columna «A en stock» (siempre 0). Columnas: Modelo · Cap. · Color · B · B100.

**Textos:**
- subtítulo: «B y B100 de modelos sin ningún A»;
- ayuda: «Los modelos-color con móviles B y ningún A en stock: candidatos a chasis (B → A; con batería nueva, B100 → A100). Un
  modelo-color sale de la lista en cuanto tiene un A.»;
- vacío y pie: no cambian.

**Diferencias con la hoja que quedan:** el B100 sale en la tabla; el A+ cuenta como A (en la hoja no cuenta: un modelo-color con
un A+ y un B sale en la hoja y no en el ERP); lo del técnico y lo corregido en el taller, como en §2.8.

### 10.12 Los Excel, una fila por modelo-color, como las tablas de la hoja (05/11; manda sobre la «una fila por móvil» de §10.3)
**Por qué:** comparado con la hoja (las 93 filas de «Solo n/a» salen iguales), el usuario: «lo que ven ellos del ERP es mucho; la
primera tabla en la hoja se diferencia claramente, en el ERP no». El Excel de «Revisar sin grado» llevaba 273 filas, una por
móvil. Decisión: los dos Excel, **una fila por modelo-color**, como «Solo n/a» y «B para subir a grado A». Los IMEI se ven en
pantalla al desplegar.

- **Revisar sin grado:** Modelo · Capacidad · Color · Sin grado · Revisado (vacía). «Sin grado» son los pendientes (sin los del
  técnico), el «Saldo stock» de la hoja.
- **Subir B a A:** Modelo · Capacidad · Color · B · B100 · Hecho (vacía). B y B100, sin los del técnico.
- En las dos: las filas de lo que se ve (con la búsqueda), en el orden de la tabla; un modelo-color sin nada pendiente (todo con
  técnico) no va. Modelo, capacidad y color escritos como hoy en el Excel.
- El botón dice cuántas filas lleva: «⬇ Descargar Excel (N)», con N modelos-color; apagado y con el título «No hay nada
  pendiente» si no hay ninguno. El aviso al terminar: «Descargado: N modelos-color» («1 modelo-color» en singular).
- Lo demás de §10.3 no cambia: «Guardar como» en el mismo clic, los nombres de fichero, la ruta del servidor y su formato para
  imprimir. El servidor no cambia.

### 10.13 La pantalla de «Revisar sin grado», compacta y en dos bloques (05/11)
**Por qué:** con la captura de preprod delante, la tabla iba a todo el ancho, con «Sin grado» en el borde derecho, lejos del
modelo, y los de 3 o más seguían a los de 1 o 2 sin ninguna marca. En la hoja se filtra «Saldo stock ≥ 3» y queda separado.
El usuario eligió las dos cosas.

- **Compacta:** la tabla ocupa lo que necesitan sus columnas, no todo el ancho, con «Sin grado» pegado al color. El número de
  pendientes va en negrita; «+N con técnico» sigue en gris. «Subir B a A» va igual de compacta (sin bloques).
- **Dos bloques**, cada uno con una fila de título en la tabla, que ocupa todas las columnas:
  - «3 o más sin revisar · N modelos-color · M móviles»: los modelos-color con 3 o más pendientes (`MIN_BLOQUE = 3`);
  - «Menos de 3 sin revisar · N modelos-color · M móviles»: los demás (1 o 2 pendientes y, al final y en gris, los que lo tienen
    todo con técnico).

  N y M son los modelos-color y los pendientes del bloque (lo que se ve, con la búsqueda), con «modelo-color» y «móvil» en
  singular cuando toca. Un bloque sin filas no sale. Dentro de cada bloque, el orden de §10.10.
- La cabecera de columnas (Modelo · Cap. · Color · Sin grado), el desplegable, el resaltado del IMEI buscado y los mensajes
  (vacío, sin coincidencias) no cambian.
- **El Excel no cambia** (§10.12): una sola tabla en el mismo orden, con los de 3 o más arriba.

### 10.14 Los móviles del modelo-color, en un panel a la derecha (05/11; manda sobre el desplegado de §3)
**Por qué:** con la tabla compacta quedaba un hueco blanco a la derecha. El usuario propuso aprovecharlo y eligió el detalle a la
derecha.

- **Dos columnas** en las dos tarjetas: a la izquierda, la tabla de modelos-color (con sus bloques en «Revisar sin grado»); a la
  derecha, el **panel del modelo-color elegido**. En pantallas estrechas (menos de 1024 px), el panel va debajo de la tabla.
- **Elegir:** pulsar una fila (o su botón, con el teclado) la elige: queda resaltada y su panel sale a la derecha. Solo hay una
  elegida por tarjeta. Pulsar otra la cambia. **Ya no se despliega dentro de la tabla**, así que la lista no se mueve.
- **Al entrar** no hay ninguna elegida; el panel dice «Pulsa un modelo-color para ver sus móviles.»
- **El panel:** el título es la descripción del modelo-color con su cuenta. En «Revisar sin grado», por ejemplo, «iPhone 14 128 GB
  Medianoche · 20 sin grado», con « · +2 con técnico» si los hay. En «Subir B a A», «… · 1 B · 2 B100». Debajo, sus móviles con
  las mismas columnas y el mismo orden que tenía el desplegado (IMEI con el enlace que abre su ficha, ubicación, días en stock,
  quién lo tiene, y el paso en «Subir B a A»). Los del técnico salen en gris.
- El panel se queda a la vista al bajar por la lista (pegado arriba, con su propio desplazamiento si es largo).
- **Buscar un IMEI** elige su modelo-color y resalta ese móvil en el panel, como antes lo abría y lo resaltaba en la tabla.
- **Lo elegido se guarda** en el estado de la página, como antes lo desplegado: sobrevive a «← Volver» y a cambiar de
  tarjeta. Si el modelo-color elegido deja de estar en la lista (un Items nuevo, o la búsqueda lo quita), el panel vuelve al
  texto de «Pulsa…».
- Los bloques, los textos, el Excel, las tarjetas y la búsqueda por palabras no cambian.

### 10.15 El Excel, con números y en vertical (05/11; manda sobre el «apaisado» de §10.3)
**Por qué:** en el primer Excel por modelo-color, «Sin grado» salía como texto. Excel lo marca con el triángulo verde de «número
almacenado como texto» y no deja filtrar «≥ 3» como se hace en la hoja. Además, con 5 o 6 columnas estrechas, el apaisado
desperdicia papel.

- **Números:** el servidor (`HojaImprimible`) escribe como número una celda cuyo texto es un entero de 1 a 9 cifras, sin signo ni
  espacios. Lo demás va como texto: un IMEI tiene 15 cifras y no se toca.
- **Orientación:** vertical si la suma de los anchos de las columnas (los que ya calcula, en caracteres) es 90 o menos;
  apaisado si pasa. Los dos Excel de «Qué revisar» salen en vertical. Sigue ajustado a una página de ancho, con la cabecera
  repetida.
- La ruta y su contrato (`{ hoja, columnas, filas }` de textos) no cambian. La web no cambia por esto.

## 9. Pendiente

- **Al plan maestro** (backlog, `Apuntes/plan-futuro.md`):
  - ordenar por lo que más se vende (fechas de venta del espejo; necesita el servidor);
  - que los técnicos vean solo esta lista (ruta propia en el servidor);
  - si hay chasis en stock para subir un B;
  - avisar de un modelo-color con 1 A y nada que revisar ni subir, que la lista ya no saca (§2.8).
