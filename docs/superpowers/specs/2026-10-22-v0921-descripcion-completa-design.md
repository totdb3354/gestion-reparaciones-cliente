# 0.9.21 — Descripción completa donde pone «Modelo»

Fecha: 2026-10-22. Estado: diseño aprobado; plan escrito (`plans/2026-10-22-v0921-descripcion-completa.md`). Los ajustes del
plan están en el §7 y mandan sobre lo de arriba si chocan.
Va en la **0.9.21**, antes de producción, junto con la «Corrección en NSYS», el panel de carga más grande y el SKU del taller
(piezas 1 y 2; specs `2026-10-21-v0921-*`), en los que se apoya. **Servidor + web, sin base de datos.**
Idea del 2026-10-20 apuntada en `Apuntes/plan-futuro.md` §5 («Descripción completa donde pone «Modelo»»).

## 1. Qué cambia

**Hoy** las listas del taller enseñan solo el modelo que puso el taller («iPhone 13»). **Ahora** enseñan la descripción
completa del móvil, «iPhone 13 128GB Azul», como la ficha del IMEI: el nombre del modelo de siempre, y la capacidad y el
color del espejo de NSYS, con lo del taller por encima (el color que vale de la 0.9.21).

**Decisiones del usuario (22/10):**

| Tema | Decisión |
|---|---|
| Quién lo ve | **Todos los roles**, también el técnico (es lo que tiene el móvil en la mano; sin precios ni stock). El técnico sigue sin ver el stock de teléfonos ni la ficha de NSYS. |
| Qué lleva | Modelo, capacidad y color. **Sin grado** y sin la marca «taller» (la marca se queda en el stock y en la ficha). |
| Cómo llega | El servidor añade la capacidad y el color a las mismas listas (sin una ruta aparte). |
| CSV | Los que ya llevan la columna «Modelo» ganan «Capacidad» y «Color» al lado. |
| Historial | El color **de hoy**, no el de cuando se hizo el trabajo (como ya pasa con el modelo). |

## 2. Comportamiento

### 2.1 El texto
- `<nombre del modelo> <capacidad> <color>`, con un espacio entre partes y sin las que falten: «iPhone 13 128GB Azul»;
  sin color, «iPhone 13 128GB»; sin nada de NSYS, el nombre del modelo solo, como hoy.
- El **nombre del modelo** sale de donde sale hoy en cada lista (`Telefono.MODELO`, el código del ERP traducido con
  `traducirModelo`). NSYS ya lo rellena cuando está vacío tras cada importación; si sigue vacío, la lista dice lo de hoy.
- La **capacidad** es la del espejo (`Nsys_movil.CAPACIDAD`) y el **color** es el que vale (lo del taller vigente o, si no
  hay, `Nsys_movil.COLOR`), de la fila más reciente del IMEI, como en el resto de la 0.9.21.
- Un IMEI que **no está en el espejo** (cliente externo) no lleva capacidad ni color.
- Una sola función de la web compone el texto (en `src/shared/lib/telefonoNsys.ts`) y sustituye las cuatro copias de
  «modelo capacidad» que ya hay (ficha del IMEI, buscador de IMEI, «Por pasar a NSYS» y «Pegar varios IMEIs»), sin cambiar lo
  que enseñan.

### 2.2 Dónde
- **Pantallas** (columna «Modelo», «Copiar celda» y textos con el modelo):
  - Asignaciones (las tres listas: reparación, glass y pulido);
  - Pendientes (reparación y glass) y Pulidos pendientes, con el texto de confirmación «IMEI · modelo»;
  - Historial de trabajos (R/G) e Historial de pulidos (P);
  - IMEIs (el modelo del grupo) y la barra del detalle del IMEI.
- **CSV** (se generan en la web): `reparaciones_pendientes` (Asignaciones), `pulidos_pendientes`, `historial_pulidos` y
  `agrupado_resumen` (IMEIs) ganan las columnas «Capacidad» y «Color» detrás de «Modelo». Los que no llevan «Modelo»
  (`mis_pendientes`, `historial_*`/`mis_*` de trabajos, `agrupado_<imei>`) no cambian.
- Lo demás que usa el mismo dato del servidor (carga de técnicos, predicción de glass) no cambia.

### 2.3 Servidor
- El dato que devuelven esas listas (`ReparacionResumen`) gana dos campos nullables al final: `capacidad` y `color`.
- Las rutas de esas listas (asignaciones, historial y sus equivalentes de glass y pulido) los rellenan **después** de leer
  la lista: se juntan los IMEIs **sin repetir** (en el historial sale una fila por componente), se leen del espejo **por
  tandas de 500** (como `NsysDAO.porImeis`) y lo del taller con **una sola consulta** de los vigentes
  (`TallerNsys.valedorDeTodos`); el maestro solo se carga si algún móvil tiene algo del taller. Nunca una consulta por fila
  y nunca un JOIN en SQL con el espejo (las tablas tienen intercalaciones distintas; el cruce va por `claveCruce`).
- Si la lectura del espejo o del taller falla, la lista sale **como hoy** (sin capacidad ni color) y se apunta en el log:
  la descripción nunca rompe una lista del taller.
- Las demás rutas que construyen `ReparacionResumen` dejan esos campos a `null`.

## 3. Errores y casos límite
- **IMEI en varios lotes:** vale la fila más reciente (como `TallerNsys.masRecientes`).
- **El modelo del taller no coincide con el de NSYS:** se enseña el nombre del taller con la capacidad y el color de NSYS
  (caso raro; se corrige con «Editar modelo» o en NSYS).
- **Listas grandes:** el historial entero del supertécnico (≈8.000 filas, ≈4.000 IMEIs) son ≈8 consultas al espejo y 1 al
  taller. Las asignaciones y pendientes se recargan cada 60 s: son cientos de filas como mucho.
- **Rendimiento:** el límite de 15 s por petición no se sube; en preprod se mide el historial entero del supertécnico antes
  y después.

## 4. Pruebas
- **Servidor (JUnit):** el relleno con IMEIs repetidos (una lectura por IMEI), por tandas (más de 500), sin NSYS (sin
  capacidad ni color), con un color del taller por encima de NSYS, y que un fallo del espejo deja la lista como hoy; las rutas
  de las listas (también para el técnico) devuelven los dos campos; `OpenApiContractTest` con los dos campos nullables.
- **Web (Vitest):** la función del texto (con y sin capacidad, color o NSYS), las columnas y «Copiar celda» de cada
  pantalla, los CSV con «Capacidad» y «Color», y que las cuatro copias de «modelo capacidad» siguen enseñando lo mismo.
- **Preprod:** un vistazo con datos reales a las pantallas y los CSV, y la medida del historial entero.

## 5. Entrega
- En `main` de los dos repos (rama propia desde `main`), antes de la producción de la 0.9.21; CHANGELOG `[0.9.21]`,
  novedades de la 0.9.21 y paridad de las pantallas del taller.
- Merge `--no-ff`; push y tags con el OK del usuario.

## 6. Fuera
- El color de cuando se hizo el trabajo en el historial.
- El grado en las listas.
- Una búsqueda o un filtro por capacidad o color en estas pantallas.

## 7. Ajustes del plan (2026-10-22)

Fijados al escribir el plan; mandan sobre lo de arriba si chocan. Ancho de las columnas «Modelo»: el de hoy (se corta con
«…» y «Copiar celda» copia el texto entero); se mira en preprod y se ensancha si molesta (decisión del controlador con lo
recomendado).

1. **`ReparacionResumen` es una clase con setters, no un record:** `capacidad` y `color` van al final con su getter y su setter
   y nacen a `null`. El constructor no cambia, así que los demás `new ReparacionResumen(…)` del repo (`RESUMEN_MAPPER`,
   `CargaTecnicos`, `PrediccionGlass` y sus tests) no se tocan y salen con `null` sin escribirlo.
2. **El color sale de un método nuevo, `TallerNsys.colorDeTodos()`:** la misma consulta única de los vigentes que
   `valedorDeTodos()` y el mismo color, pero **sin cargar nunca el maestro**, que solo hace falta para el SKU (estas listas no lo
   llevan).
3. **Todo o nada:** si falla la lectura del espejo o la del taller, ninguna fila lleva capacidad ni color (no se enseña el color de
   NSYS sin saber si el taller lo cambió). Si ningún IMEI de la lista está en el espejo, no se lee el taller.
4. **El móvil de un IMEI en la vista IMEIs** (modelo del grupo y barra del detalle) es el primer modelo, la primera capacidad y el
   primer color no vacíos de sus trabajos (`movilDe`), como ya se hacía con el modelo: si una de las tres listas no pudo leer
   NSYS, vale lo de otra.
5. **Los CSV de «Pulidos pendientes» e «Historial de pulidos» salen de las páginas a funciones puras** (`pendientes/csvPulidos.ts`
   y `historial/csvTrabajos.ts`) para probarlos sin montar el `AppLayout`; mismo fichero y mismas columnas, más las dos nuevas.
6. **Anchos:** las columnas «Modelo» conservan su ancho; un texto largo se corta con «…» como cualquier celda y «Copiar celda»
   copia el texto entero.
