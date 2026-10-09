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
| Stock y mínimo iniciales | **Stock 0, mínimo 2**, activas, sin grupo compartido | Hay que contarlas; con mínimo 2 salen en «Sin stock» y en la previsión desde el primer día |
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
SELECT CONCAT('tapa', SUBSTRING(c.TIPO, 4)), 0, 2, 1
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
  no muestra la fila; Stock lista las 65 en «Sin stock» con mínimo 2; guardar una reparación con tapa descuenta stock.

## 8. Despliegue

1. Preprod: copia **manual** de producción (la nocturna es anterior al saneamiento) → refresco saneado de preprod
   (`preprod/refresco-preprod.md`) → servidor y web 0.9.7 → `datos-tapa-trasera.sql` → pruebas del §7.
2. Producción, en horario (corte despreciable, sin cambio de esquema): servidor y web 0.9.7 → `datos-tapa-trasera.sql`.
3. Tags `v0.9.7` en servidor y web, novedades de la 0.9.7 y gitlinks en la raíz.
