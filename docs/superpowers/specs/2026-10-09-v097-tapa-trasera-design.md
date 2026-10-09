# 0.9.7 — Tapa trasera como pieza propia

Fecha: 2026-10-09. Versión del producto: **0.9.7** (servidor y web etiquetados juntos).
Antecedentes: saneamiento del catálogo del 2026-10-09 (solo datos, ya hecho en producción; ver §2).

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de las máquinas y
el registro de los despliegues viven fuera de git, en `Apuntes/`.

## 1. Objetivo

Hasta ahora el cambio de tapa trasera se apuntaba como acción «otro» con una descripción: sin SKU, sin stock, sin
color y puntuando como «otro» (0,50). Se formaliza como **pieza propia**, igual que batería, chasis, pantalla o cámara:

1. SKU por modelo y color, con stock, mínimo, previsión y pedidos como el resto de piezas.
2. Su propia puntuación de dificultad (**1,00**).
3. Su propia fila **«Tapa trasera»** en el formulario de reparación, justo después de Chasis.

De paso se pule el formulario: una fila de un tipo que **no existe** para el modelo elegido deja de pintarse (§5.2).

## 2. Antecedente: catálogo saneado (hecho, solo datos)

Hecho en producción el 2026-10-09, sin código. Se resume porque las tapas se generan a partir de él:

- **Modelos activos:** solo las series 12 a 17 (con mini, Plus, Pro, Pro Max, 16e y Air). SE 2020 desactivado; del 6s
  al 11 Pro Max ya lo estaban.
- **Colores de chasis = nombre oficial de Apple** en minúsculas y sin espacios (`ultramarine`, `deserttitanium`,
  `cloudwhite`…). Se renombraron los chasis de las series 16 y 17 y del Air (estaban en castellano) y se normalizaron
  los de modelos retirados (`spacegrey` → `spacegray`, `negro` → `black`/`spacegray`).
- **Alias** cuando el nombre oficial choca con la lectura del modelo del SKU (`extraerModelo`: gana el código de modelo
  más largo que sea prefijo): `red` por (PRODUCT)RED (`productred` se leería «…pro»), y en la X `plata`/`grey` por
  Silver/Space Gray (`xsilver` se leería XS). Los tres alias están registrados en `Color_equivalencia`.
  **Regla:** ningún color puede empezar por lo que sigue a un código de modelo en `MODELOS_ORDENADOS` (`pro`, `plus`,
  `mini`, `max`, `e`; `r`/`s` tras la X).
- SKU «otro» creados para 17, Air, 17 Pro y 17 Pro Max (faltaban: sin ellos no salía OTRAS ACCIONES en esos modelos).

## 3. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Prefijo del SKU | **`tapa`** (`tapai16ultramarine`) | Sin `i`: el servidor corta el prefijo en la primera `i` (`extraerPrefijo`). No es prefijo de ningún tipo existente ni al revés |
| Modelos con tapa | **14, 14 Plus, serie 15 completa, serie 16 completa (con 16e), 17, Air, 17 Pro, 17 Pro Max** (15 modelos). **No:** series 12 y 13, 14 Pro, 14 Pro Max | En esos modelos el cristal trasero va pegado al chasis y se cambia el chasis |
| Colores | **Los mismos que el chasis de cada modelo**, mismos nombres y alias | La tapa es del color del teléfono |
| Variante eSIM | **No** | La diferencia eSIM está en la bandeja SIM del chasis; la tapa es la misma |
| Cuántas | **65** (una por color de chasis activo, sin `esim`, de los 15 modelos) | Se generan desde los chasis, no a mano |
| Stock y mínimo iniciales | **Stock 9999, mínimo 2**, activas, sin grupo compartido, modo Manual | Como los chasis: su stock no se cuenta (cambio del 2026-10-09 tras desplegar en preprod; antes, stock 0) |
| Puntos | Clave `tapa` en `Dificultad_puntos` con **1,00** | Decisión del usuario; antes puntuaba como «otro» (0,50) |
| Formulario | Solo en **Reparación**, nunca en Glass; fila **después de Chasis** | La hace el técnico de reparación; chasis y tapa van por color, juntos |
| Historial «otro» | Las acciones «otro» antiguas que describían una tapa **se quedan como están** | No guardaron el color (no hay SKU al que pasarlas) y cambiarían a posteriori puntos ya calculados |
| Tipo inexistente para el modelo | La fila **no se pinta** (§5.2); con SKU pero todos desactivados, sigue en gris | Petición del usuario: «en gris» debe significar «existe pero está desactivado», no «no existe» |

## 4. Servidor

### 4.1 Puntos (`PuntosCalculo`)

`PREFIJO_CLAVE` gana `tapa` → `tapa` (antes de `g`, que sigue siendo la última). Sin esto, `claveDeTipo("tapai15black")`
devolvería `otro`. Si la fila `tapa` faltara en `Dificultad_puntos`, la tapa puntuaría 0 (`getOrDefault`): por eso el
SQL de datos (§6) la crea antes que las tapas.

### 4.2 Orden de los grupos (`ComponenteDAO.getAgrupadosPorTipo`)

La lista de orden pasa a `bat, cha, tapa, g, mc, lcd` (el resto, `cam` y `otro`, sigue detrás en orden alfabético).
El formulario pinta las filas en el orden de claves del servidor (`prefijosDeFila`), así que la tapa sale tras Chasis.

### 4.3 Lo que no cambia

Descuento y devolución de stock (`ReparacionComponenteDAO`), stock bajo, evolución de stock, previsión, pedidos y
solicitudes de pieza trabajan con cualquier `ID_COM` que no sea `otro%`: la tapa entra sin cambios. La marca «Chasis»
de la reparación sigue mirando solo `cha%`.

### 4.4 SQL

- `crear_bd.sql`: `('tapa', 1.00)` en el `INSERT` de `Dificultad_puntos`.
- Nuevo `sql/datos-tapa-trasera.sql` (§6), idempotente, que aplica el usuario.

## 5. Web

### 5.1 Nombre del tipo (`lib/piezas.ts`)

`tapa` → **«Tapa trasera»** en `PREFIJOS`/`ETIQUETAS` (categoría del historial y su filtro «Pieza») y en
`NOMBRES_TIPO` (nombre de la fila). `prefijosDeFila` no cambia: la tapa no es `g`/`mc` ni `otro`, así que cae en
Reparación y nunca en Glass.

### 5.2 Filas de tipos inexistentes para el modelo (`formulario/estado.ts`)

Hoy cada fila guarda solo los SKU **activos** de su tipo y, si ninguno es del modelo, se pinta en gris y deshabilitada
(`filaSinSku`). Pasa a distinguir:

| Caso (modelo elegido) | Fila |
|---|---|
| Hay algún SKU activo del tipo para el modelo | Normal (sin cambios) |
| Hay SKU del tipo para el modelo, pero todos desactivados | En gris y deshabilitada (sin cambios) |
| **No hay ningún SKU del tipo para el modelo** (ni activo ni desactivado) | **No se pinta** |

- La comprobación usa el catálogo completo del tipo (`agrupados[prefijo]`, que ya trae los desactivados).
- **Sin modelo elegido** se pinta todo como hoy.
- **Salvaguarda:** una fila con estado propio (guardada, «ya reparada», solicitud o agotado confirmado) **nunca** se
  oculta.
- Ocultar es solo de presentación: la fila sigue en el estado, así que borrador, guardado y edición no cambian.
- Vale para **todos** los tipos, no solo la tapa.

### 5.3 Lo que no cambia

El cuerpo del formulario ya se desplaza (cabecera y zona de guardar fijas): una fila más no necesita scroll nuevo.
Stock, nuevo pedido, previsión e historial listan componentes de cualquier tipo.

## 6. Datos (`sql/datos-tapa-trasera.sql`)

Se aplica **después** de desplegar servidor y web (con el código viejo la fila saldría como «tapa», al final y
puntuando como «otro»).

```sql
-- Puntos de la tapa (idempotente)
INSERT IGNORE INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES ('tapa', 1.00);

-- Vista previa: debe dar 65
SELECT COUNT(*) FROM Componente
 WHERE TIPO LIKE 'chai%' AND TIPO NOT LIKE '%esim' AND ACTIVO = 1
   AND TIPO REGEXP '^chai(14|15|16|17|air)' AND TIPO NOT REGEXP '^chai14pro';

-- Una tapa por chasis activo sin eSIM de los 15 modelos, mismo nombre con 'tapa' en vez de 'cha' (idempotente)
INSERT INTO Componente (TIPO, STOCK, STOCK_MINIMO, ACTIVO)
SELECT CONCAT('tapa', SUBSTRING(c.TIPO, 4)), 9999, 2, 1
  FROM Componente c
 WHERE c.TIPO LIKE 'chai%' AND c.TIPO NOT LIKE '%esim' AND c.ACTIVO = 1
   AND c.TIPO REGEXP '^chai(14|15|16|17|air)' AND c.TIPO NOT REGEXP '^chai14pro'
   AND NOT EXISTS (SELECT 1 FROM Componente t WHERE t.TIPO = CONCAT('tapa', SUBSTRING(c.TIPO, 4)));
```

`^chai(14|15|16|17|air)` recoge 14, 14 Plus, 14 Pro y 14 Pro Max y todas las variantes de 15, 16 y 17; el segundo
filtro quita 14 Pro y 14 Pro Max. Se genera desde los chasis **de la base donde se ejecuta**, así que en preprod hay que
refrescar antes la base desde una copia de producción posterior al saneamiento del §2.

## 7. Pruebas

- **Servidor:** `claveDeTipo("tapai15black") = "tapa"` y una reparación con tapa puntúa 1,00; orden de
  `getAgrupadosPorTipo` con `tapa` tras `cha`.
- **Web:** `categoriaPieza` y `nombreTipo` de `tapa`; `prefijosDeFila` con la tapa tras chasis en Reparación y fuera de
  Glass; las tres filas de la tabla del §5.2 y la salvaguarda (fila guardada de un tipo inexistente no se oculta).
- **Preprod (a mano, con la base refrescada):** un 15 Pro muestra «Tapa trasera» tras Chasis con sus 4 colores; un 13
  no muestra la fila; Stock lista las 65 con stock 9999 y mínimo 2; guardar una reparación con tapa descuenta stock.

## 8. Despliegue

1. Preprod: copia **manual** de producción (la nocturna es anterior al saneamiento) → refresco saneado de preprod
   (`preprod/refresco-preprod.md`) → servidor y web 0.9.7 → `datos-tapa-trasera.sql` → pruebas del §7.
2. Producción, en horario (corte despreciable, sin cambio de esquema): servidor y web 0.9.7 → `datos-tapa-trasera.sql`.
3. Tags `v0.9.7` en servidor y web, novedades de la 0.9.7 y gitlinks en la raíz.

## 9. Añadido (2026-10-09, tras validar en preprod): selector de chasis y tapa trasera

Antes de llevar la 0.9.7 a producción se le añade una mejora **solo de la web** para reducir el error humano al elegir
el color: hoy el desplegable viene con un SKU preseleccionado (el primero con stock; como chasis y tapas están a 9999,
siempre el primero de la lista) y un «+» con prisa registra un color que no es. Servidor y datos no cambian.

### 9.1 Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Filas afectadas | Solo **Chasis** (`cha`) y **Tapa trasera** (`tapa`) | Son las que van por color; el resto sigue igual |
| SKU por defecto | **Ninguno**: el botón dice **«— Elige color —»** (con modelo y sin modelo) | Obliga a elegir el color a conciencia |
| Sin SKU elegido | «+» y «Reutilizado» **desactivados**; Stock «—» | Primero se elige y luego se suma: nunca hay una fila usada sin color, sin bloqueos ni avisos al guardar |
| Al cambiar de modelo | Chasis y tapa vuelven a «— Elige color —» (salvo filas bloqueadas, §9.3) | El color del modelo anterior no vale |
| Muestra de color | **Círculo** (~12 px) del tono aproximado del color, con **borde fino semitransparente en todos** | Ayuda visual; el borde hace visibles blanco, starlight o plata sobre fondo blanco |
| Texto en la lista | **Nombre oficial legible** del color («Ultramarine», «Black Titanium», «(PRODUCT)RED»); el SKU completo en el `title` | Menos que leer, menos confusión |
| Texto en el botón (elegido) | Círculo + **SKU completo** (`chai16ultramarineesim`) | Se ve exactamente qué pieza queda registrada |
| SIM / eSIM (solo chasis) | Lista en dos bloques con título **«SIM»** y **«eSIM»**, en ese orden; sin títulos si el modelo solo tiene SIM. La tapa, sin bloques | Facilita e incentiva elegir bien la variante |
| Enlace chasis → tapa | Elegir el color del chasis pone la tapa en **ese color** | La tapa es siempre del color del chasis |
| Enlace tapa → chasis | Con chasis ya elegido, elegir la tapa cambia el chasis a ese color **conservando su SIM/eSIM**. Con chasis **sin elegir**, el chasis **sigue sin elegir** y en su lista se **resaltan** las opciones de ese color (SIM y eSIM) | La tapa no puede saber si el teléfono es SIM o eSIM (decisión del usuario, opción A) |
| Qué cambia el enlace | Solo el **SKU elegido**, nunca la cantidad ni «Reutilizado» (usa la misma regla que elegir el SKU a mano) | Elegir no es usar |
| Dónde se ve el círculo | Solo en el formulario de reparación | Es donde se elige y donde está el riesgo |
| Aviso de color distinto al del teléfono | **Fuera** (plan maestro, cuando exista el importador; nunca bloqueante: hay cambios de color) | Depende de datos que hoy no están garantizados |

### 9.2 Color de un SKU

- Del SKU se saca el **modelo** (`extraerModelo`), la **variante** (`esim` al final) y el **token de color** (lo que queda
  entre el modelo y `esim`): `chai16ultramarineesim` → modelo `16`, eSIM, `ultramarine`; `tapai16ultramarine` → `16`,
  `ultramarine`.
- Una **tabla de colores** en la web da, por token, el **nombre oficial** y un **tono** aproximado; los tokens que Apple
  repite con tono distinto según el modelo (`blue`, `green`, `pink`, `purple`, `yellow`, `gold`…) llevan tono propio por
  modelo. `red` se muestra como «(PRODUCT)RED».
- Token desconocido (no está en la tabla): se muestra el token tal cual y un círculo **gris con borde discontinuo**; no
  rompe nada.
- Chasis y tapa «coinciden» si tienen el mismo modelo y el mismo token.

### 9.3 Filas que el enlace y el reseteo no tocan

Nunca se cambia el SKU de una fila **bloqueada** (guardada, con agotado confirmado, «ya reparada», guardando o sin SKU),
**con solicitud**, ni de la fila **en edición** (`editada`): esa fila es una reparación ya registrada. Al abrir:
- **Editar** una reparación: su fila sale con su SKU guardado, como hoy.
- **Solicitud** de pieza: sigue preseleccionando su SKU, como hoy.
- **Borrador**: recupera lo elegido, como hoy (una fila sin elegir vuelve sin elegir).
- Si el chasis abre ya con SKU (edición o solicitud), la tapa empieza con su color (si la tapa no está bloqueada).

### 9.4 Componentes

- `ComboNavy` (`shared/ui`) gana campos **opcionales** por opción: `color` (muestra), `grupo` (título del bloque),
  `resaltada` (marca de «coincide con la tapa») y `titulo` (texto al pasar el ratón); y una `etiquetaBoton` opcional para
  que el botón muestre el SKU completo mientras la lista muestra el nombre del color. Los demás usos de `ComboNavy` no
  cambian.
- `formulario/estado.ts`: chasis y tapa arrancan sin SKU; el enlace vive en el reductor (`CAMBIAR_SKU`), puro y testeable.
- Tabla de colores y lectura del SKU en un fichero propio de `taller/lib`.

### 9.5 Pruebas

- Lectura del SKU (modelo, eSIM, token) y tabla de colores (nombre, tono por modelo, desconocido).
- Estado: chasis y tapa sin SKU al elegir modelo; «+»/«Reutilizado» desactivados hasta elegir; enlace chasis → tapa;
  tapa → chasis conservando SIM/eSIM; tapa con chasis sin elegir → chasis sin elegir y resaltado; filas bloqueadas,
  con solicitud y en edición no se tocan; edición/solicitud/borrador como hoy; las demás filas siguen preseleccionando.
- `ComboNavy`: círculo, bloques con título, resaltado, `title` y `etiquetaBoton`; sin campos nuevos se pinta igual que hoy.
- Formulario (interfaz): en un 16, «— Elige color —», bloques SIM/eSIM, elegir chasis pone la tapa.
- E2E `formulario.spec.ts`: revisar que no dependa de un chasis preseleccionado.

### 9.6 Entrega

Va en la **0.9.7** (sin tag todavía). En preprod solo cambia la web: `git pull` de la web y reconstruir `nginx`; probar a
mano el §9.5 en un 16 (chasis y tapa) y en un 13 (solo chasis, sin bloques eSIM). Producción, como en §8, con todo junto.
