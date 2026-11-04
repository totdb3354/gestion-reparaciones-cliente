# 0.9.25 — «Qué revisar» en Stock de teléfonos

Fecha: 2026-11-04. Estado: **diseño aprobado** (prototipo y spec, 04/11). Plan: `docs/superpowers/plans/2026-11-04-v0925-que-revisar.md`.
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

## 9. Pendiente

- **Al plan maestro** (backlog, `Apuntes/plan-futuro.md`):
  - ordenar por lo que más se vende (fechas de venta del espejo; necesita el servidor);
  - que los técnicos vean solo esta lista (ruta propia en el servidor);
  - si hay chasis en stock para subir un B;
  - avisar de un modelo-color con 1 A y nada que revisar ni subir, que la lista ya no saca (§2.8).
