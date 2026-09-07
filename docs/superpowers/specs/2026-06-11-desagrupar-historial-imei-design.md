# Modo "desagrupar por IMEI" en el historial de reparaciones — Diseño

**Fecha:** 2026-06-11
**Item de backlog:** Item 3 (UAT junio 2026)

## Objetivo

Añadir al historial de reparaciones un modo **plano** (sin agrupar por IMEI), activado con un switch, que liste todas las reparaciones en una sola tabla. Resuelve la pregunta "¿cuántas reparaciones hizo este técnico (con filtros)?", que hoy obliga a entrar IMEI por IMEI y sumar.

## Estado actual (contexto)

El historial vive en tres controladores casi idénticos, uno por rol: `ReparacionControllerTecnico`, `ReparacionControllerAdmin`, `ReparacionControllerSuperTecnico`. Cada uno implementa `Recargable` y `Exportable`. No comparten clase base.

Hoy operan en dos modos (`enum Modo { MAESTRO, DETALLE }`):
- **MAESTRO**: una fila por IMEI (`GrupoImei`), construida en `buildTablaItems()` agrupando `datosFiltrados` por IMEI. Clicar una fila entra en DETALLE.
- **DETALLE**: drill-down a un solo IMEI; muestra sus reparaciones en plano (`ReparacionResumen`) con columnas por-reparación y una barra de navegación con "← Volver".

Hechos clave que el diseño aprovecha:
- Las columnas y celdas para filas planas (`ReparacionResumen`) **ya existen** (las usa DETALLE).
- El **CSV ya diferencia** la vista: en MAESTRO exporta agrupado; en DETALLE exporta plano vía `filaReparacion()`.
- Los filtros (IMEI, técnico, fecha, incidencias) ya se aplican en `aplicarFiltros()` para ambos modos.

## Diseño

### Modelo de interacción

- Un **pill segmentado "Agrupado | Plano"** al final (derecha) de la barra de filtros, reutilizando el estilo `toggle-pill-left/right` ya existente (el de "Reparaciones | Pulidos"). **"Agrupado" seleccionado por defecto.**
- **"Agrupado"** → comportamiento actual (MAESTRO + drill-down a DETALLE).
- **"Plano"** → nuevo modo **PLANO**: todas las reparaciones filtradas en una tabla plana, **sin agrupar y sin drill-down**. La vista se siente como el DETALLE: mismas columnas y mismos controles de filtro (incluido el de **Técnico**, que aparece igual que al entrar al detalle).
- **Memoria Nivel 1 (sesión):** el modo se mantiene mientras la app esté abierta — sobrevive a la recarga automática de 60s, a tocar filtros, a cambiar de panel (Pendientes/Historial) y al toggle Reparaciones↔Pulidos. Al cerrar y reabrir la app, arranca en "Agrupado". No se persiste en disco.

### Implementación (reutiliza lo existente)

- Ampliar el enum a `Modo { MAESTRO, DETALLE, PLANO }`.
- **`aplicarFiltros()`**: añadir el branch PLANO. Aplica los mismos filtros que MAESTRO sobre `datos`, pero vuelca **todas** las `ReparacionResumen` filtradas directamente a `tablaItems` **sin** llamar a `buildTablaItems()` (que es quien agrupa).
- **Columnas en PLANO**: usar el set de DETALLE (IMEI + Técnico/Reparador visibles, `colComponente` = "Componente", visibles `colIdRep`, `colObservaciones`, `colIncidencia`, `colIdAnterior`; oculta `colObservacionTelefono`).
- **Filtros en PLANO**: a diferencia de DETALLE, el `filtroImei` **permanece visible** (filtra entre todos los IMEIs). No hay barra de navegación "← Volver" (eso es exclusivo de DETALLE). Técnico, fecha e incidencias visibles y activos.
- **Drill-down**: deshabilitado en PLANO (no hay filas `GrupoImei`).
- El pill se cablea con un `ToggleGroup` (mismo patrón que el toggle "Reparaciones | Pulidos"): seleccionar "Plano" entra en PLANO; seleccionar "Agrupado" vuelve a MAESTRO. Dispara el re-render vía `aplicarFiltros()`.

### Contador de total

En PLANO, mostrar "N reparaciones" (recuento tras filtros), reutilizando el estilo del contador que ya existe en la barra de navegación del detalle (`lblNavCount`). Ubicación: **junto al pill** (a su lado, en la misma barra de filtros). Solo visible en PLANO.

### CSV

En PLANO, `exportarCSV()` enruta al formato **plano** existente (cabeceras por-reparación + `filaReparacion()` por cada fila visible), el mismo que ya usa DETALLE. Solo se añade el branch de modo en el método de export.

### Alcance

Se replica el mismo cambio en los **tres** controladores (`Tecnico`, `Admin`, `SuperTecnico`). No se extrae clase base (refactor grande y arriesgado en archivos de ~1400 líneas); la duplicación queda como deuda conocida.

## Riesgos

- **No es puramente aditivo**: toca el branching de modos (`aplicarFiltros`, adaptación de columnas/filtros, drill-down). Un descuido puede afectar a MAESTRO/DETALLE existentes.
- **Interacciones de estado** (Nivel 1): el modo debe preservarse a través de recarga de 60s, cambio de panel y toggle Rep↔Pulidos. Es la zona más propensa a bugs.
- **Replicación ×3**: la mayor superficie de error es una divergencia o typo entre los tres controladores.

## Pruebas (manual, los 3 roles)

1. Activar el switch → la tabla pasa a plana con todas las reparaciones; el filtro de IMEI sigue visible.
2. Filtrar por técnico en plano → solo sus reparaciones; el contador refleja el total.
3. Filtrar por fecha/incidencias en plano → aplican correctamente.
4. Esperar la recarga de 60s en plano → sigue en plano (Nivel 0).
5. Cambiar a Pendientes y volver a Historial → sigue en plano (Nivel 1).
6. Toggle a Pulidos y volver a Reparaciones → el modo se conserva coherentemente.
7. Exportar CSV en plano → formato por-reparación (no agrupado).
8. Volver a "Agrupar por IMEI" → MAESTRO y su drill-down funcionan como antes.
9. Cerrar y reabrir la app → arranca en "Agrupado".

## Fuera de alcance

- Persistencia del modo entre reinicios (Nivel 2).
- Refactor para extraer base común de los tres controladores.
- Cambios en el modo agrupado (MAESTRO) o en el drill-down (DETALLE) más allá de convivir con el nuevo modo.
