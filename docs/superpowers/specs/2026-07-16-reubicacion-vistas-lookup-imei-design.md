# Reubicación de vistas (IMEIs taller / Inventario gestión) + lookup IMEI en alta manual — Diseño

> Estado: **aprobado por el usuario (2026-07-16, brainstorm en sesión)**. Ejecución
> prevista en sesión nueva con subagent-driven-development tras el plan.

## 1. Idea y problema

Principio rector (palabras del usuario): *"es casi como si desde el inicio
hubiéramos hecho todo esto del IMEI en una vista nueva, sin tocar prácticamente
el historial de reparaciones"*. F2a convirtió la vista "IMEIs" (historial de
reparaciones agrupado por IMEI, del taller) en el inventario completo de
teléfonos — y con ello los TÉCNICOS ven inventario entero, clientes y precios
de compra (nota pendiente del review final de F2a). Cada vista responde una
pregunta distinta:

- **Taller**: "¿qué se le ha hecho a este teléfono?" → historial por IMEI.
- **Gestión**: "¿qué tengo, dónde y en qué estado?" → inventario completo.

Esta fase las separa y, de paso, cierra la rama pequeña pendiente del lookup
IMEI en el alta manual de lotes.

## 2. Decisiones (con su porqué)

1. **"IMEIs" (sidebar de Reparaciones) vuelve a ser el historial por IMEI**,
   como antes de F2a, para TODOS los roles (es la herramienta del taller):
   - Solo teléfonos **con trabajos**.
   - Maestro: IMEI, Modelo (**con sufijo " eSIM"** — lo único nuevo), Trabajos,
     Última actividad, Cliente, Observación del teléfono. **Sin columna
     OK/Revisión** (desaparece de esta vista ya; del inventario la retirará F2c).
     Sin GB/Color/Grado/Estado/Ubicación/Lote/precios.
   - **Drill-down de trabajos intacto** (reparador, asignado por, fecha,
     componente, incidencia, estado…).
   - **Export CSV se conserva** (decisión usuario: "es el registro de lo que ha
     trabajado el técnico"): mismos botones de export que la versión pre-F2a
     (maestro y detalle si existía), con la convención CSV = espejo semántico de
     la tabla (sin OK, modelo con sufijo eSIM). El plan verifica en git
     (`cf9df9c`) los botones/columnas exactos de la versión anterior.
   - Sin botones Importar lote / Alta manual, sin editar atributos, filtros
     reducidos a los que tenía la versión anterior.
2. **Pestaña superior nueva "Inventario"** (nivel Stock/Estadísticas/Clientes)
   con la vista completa ACTUAL tal cual (filtros F2a, importar, alta manual,
   editar atributos, CSV 19 columnas, columna Revisión). Visible **solo
   SUPERTECNICO + ADMIN** — gating en un único punto (el botón del menú en
   MainController, como el resto de visibilidades por rol del cliente). El rol
   LOGISTICA se sumará en F3. La autorización EN SERVIDOR por rol sigue siendo
   asunto de F3 (los endpoints no cambian en esta fase).
3. **Ruta de implementación: 1 vista, 2 configuraciones.** `AgrupadoController`
   gana un modo explícito (`SIMPLE` / `INVENTARIO`) que se fija al cargar la
   vista. La configuración (columnas visibles, botones, filtros, filtro base
   "solo con trabajos", exports) vive en UN bloque por modo — no ifs dispersos
   por el controlador (riesgo señalado: el fichero ya es muy grande; el review
   lo vigilará). Descartado resucitar el controlador pre-F2a (GrupoImei):
   duplicaría mantenimiento de dos maestro-detalle casi iguales.
4. **Lookup IMEI en el alta manual** (`AltaManualLoteDialog`): al pegar/escanear
   IMEIs, lookup de modelo en background por fila usando el mecanismo servidor
   EXISTENTE (`GET /api/telefonos/{imei}/modelo`, BD primero y API imeicheck
   como fallback; el cliente ya lo consume vía `TelefonoDAO`). Reglas:
   - **Precedencia (decisión usuario): el detectado gana en su fila**; el campo
     "Modelo común" solo rellena las filas que el lookup no resuelve; la
     edición manual por fila pisa a todo.
   - **Rate limit**: cola secuencial respetando 30 req/min (pegados grandes se
     encolan; UI no bloqueante con indicador "detectando…" por fila).
   - Lookup **solo para IMEIs que la BD no conozca** (los conocidos ya traen
     modelo de BD, sin gastar cupo API).
   - Sin cambios de servidor ni BD (`TelefonoImport.modelo` ya es por fila).
   - La API key vive SOLO en `application.properties` de la VM (repos públicos —
     no se toca nada de eso en esta fase).

## 3. Componentes afectados (todo cliente)

- `MainController` (+ FXML del menú): pestaña "Inventario" con visibilidad por
  rol; wiring del modo al cargar cada vista.
- `AgrupadoController`: bloque de configuración por modo (columnas, botones,
  filtros, filtro base, exports). El modo INVENTARIO debe quedar byte-a-byte
  como la vista actual.
- `AltaManualLoteDialog`: lookup por fila + cola + precedencia.
- Sin cambios: servidor, BD, endpoints, `TelefonoDAO` (ya tiene `getModelo`).

## 4. Fuera de alcance

- Autorización por rol en servidor (F3).
- Retirar la columna Revisión/OK del inventario (F2c).
- Cruzar filas `MODELO_SIN_MAPEAR` del importador con la API (idea futura).
- Cualquier cambio visual del rediseño UI (spec aparte, post-v0.17.0).

## 5. Testing

- Unit: lógica de configuración por modo (qué columnas/botones/filtro base
  produce cada modo) si es extraíble sin UI; merge de precedencia de modelos
  del alta manual (detectado/común/manual) como función pura testeable.
- El pegamento JavaFX (wiring de menú, celdas) va sin test propio con smoke
  manual (precedente F2a/atributos).
- Smoke con el usuario: como TECNICO ver "IMEIs" simple (sin OK, sin botones,
  con eSIM y CSV); como SUPERTECNICO/ADMIN ver la pestaña Inventario completa;
  alta manual pegando lote mixto (IMEIs conocidos por BD + desconocidos que
  resuelve la API + alguno irresoluble que cae al modelo común).

## 6. Riesgos

- Tamaño de `AgrupadoController`: la configuración de modo debe quedar agrupada
  (bloque único por modo) o la vista se vuelve inmantenible.
- Rate limit API en pegados grandes: cola secuencial, nunca ráfaga; si el cupo
  diario fallara, la fila cae al modelo común (degradación silenciosa aceptable,
  el usuario siempre puede fijar modelo).
- Regresión del modo INVENTARIO: debe quedar idéntico a la vista actual (el
  review final compara comportamiento, no solo compilación).
