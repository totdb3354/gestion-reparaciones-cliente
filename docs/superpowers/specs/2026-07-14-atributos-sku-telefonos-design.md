# Atributos SKU-ready de teléfonos (+ derivación del SKU) — Diseño

> Brainstorm 2026-07-13/14 durante el smoke de F2a. Estado: aprobado por secciones
> por el usuario (1-4). Implementación: mini-fase post-merge F2a (secciones 1-3);
> la derivación del SKU (sección 4) se construye tras F2b.

## 1. Contexto y motivación

El usuario identifica los teléfonos vendibles con un **SKU de teléfono** — distinto
del SKU de componentes — que codifica la configuración completa del terminal:

```
i 15 ProMax esim 256 Black A 100
│  │    │     │    │    │   │  └─ % batería: SOLO si es 100 (opcional)
│  │    │     │    │    │   └─ grado estético PROPIO (escala C/B/A-/A/A+)
│  │    │     │    │    └─ color OFICIAL de Apple (inglés)
│  │    │     │    └─ capacidad GB
│  │    │     └─ "esim" si es la variante eSIM (opcional)
│  │    └─ variante: Pro/ProMax/Plus/mini/… (ausente = modelo base)
│  └─ serie: número o "x"/"SE…" (siempre)
└─ "i" de iPhone (siempre)
```

Un SKU identifica una **configuración vendible** (todos los teléfonos iguales
comparten SKU), no una unidad. Objetivo de fondo: todo el stock en el programa,
teléfonos bien categorizados — el SKU es la manifestación de esa categorización
(mostrar, agrupar, conciliar con la plataforma de ventas: todo eso llega después).

**Problema actual (F2a recién construida):** la tabla `Telefono` no captura todo
lo que el SKU necesita:
- El flag **eSIM se pierde** al importar: "iPhone 15 eSIM" se mapea al modelo `15`
  vía equivalencia y el matiz desaparece.
- El **color** entra como texto libre del proveedor (sin vocabulario: hoy "White"
  de Hy5, mañana cualquier cosa) — imposible agrupar con fiabilidad.
- La **batería** no existe como dato (llega con F2b, la produce la revisión).
- Grado propio vs proveedor: YA resuelto en F2a (columnas separadas, ambos visibles).

## 2. Decisiones de diseño (con su porqué)

1. **El SKU se DERIVA, nunca se almacena ni se edita** (paridad conceptual con la
   ubicación): se calcula de los atributos del teléfono. Corrección = editar el
   atributo (p. ej. batería) y el SKU se recalcula. Imposible que el SKU mienta.
   Descartado el modelo "SKU como texto editable" (estilo TIPO de componentes):
   los teléfonos tienen atributos estructurados; un string a mano discrepa seguro.
2. **Color canónico = nombre OFICIAL de Apple, en inglés** (Black, White, Midnight,
   Starlight, (PRODUCT)RED, Natural Titanium, Desert Titanium…). Los suppliers ya
   escriben así; los SKUs del usuario usan el oficial (no castellano). Las
   equivalencias quedan solo para erratas/variantes ("Product Red" → "(PRODUCT)RED";
   casos con trampa los decide el usuario la primera vez, como con los modelos).
3. **Catálogo de colores en CÓDIGO** (estilo `MODELOS_ORDENADOS`), no tabla con
   gestión UI: los colores nuevos llegan con las series nuevas de iPhone, que ya
   exigen release para ampliar el catálogo de modelos — se actualizan juntos.
   **Es una matriz modelo→colores** (paleta oficial de cada modelo: Starlight
   existe en 14/15, Black Titanium solo en 16 Pro…), no una lista plana
   (refinamiento del usuario, 2026-07-14): los selectores de color (editar
   atributos, alta manual) se filtran por el modelo del teléfono, y el importador
   valida el color contra la paleta del modelo; un color fuera de paleta sigue el
   flujo "sin mapear" (el usuario elige de la paleta y la equivalencia se recuerda).
4. **Vocabulario único en todo el sistema**: los TIPO de chasis del catálogo de
   componentes se renombran al mismo color oficial ("…negro" → "…Black"). Cambio de
   DATOS (UPDATE de strings; las referencias van por ID_COM y no se rompen); habilita
   a futuro "reparación con chasis Blue ⇒ teléfono pasa a Blue" parseando el TIPO.
5. **eSIM automático en el importador**: si el texto de modelo del proveedor contiene
   "esim" (normalizado, case-insensitive), `ES_ESIM = true` — sin preguntar y sin
   depender del mapeo de modelo elegido. El mapeo "iPhone 15 eSIM" → `15` deja de
   perder información.
6. **La batería la produce la revisión** (F2b): diagnóstico en la ficha funcional;
   cambio de batería en una reparación ⇒ 100 automático (vía `Reparacion_componente`);
   casos raros ⇒ edición manual del ATRIBUTO batería (no del SKU). En el SKU solo
   aparece el sufijo `100` cuando vale exactamente 100.
7. **SKU dinámico**: cambia cuando cambian los atributos (chasis de otro color ⇒
   color nuevo ⇒ SKU nuevo; batería cambiada ⇒ sufijo 100; chasis nuevo ⇒ grado
   al máximo ⇒ SKU nuevo — confirmado por el usuario 2026-07-14). Consecuencia
   directa de derivar en vez de almacenar.
8. **Grado del SKU = grado PROPIO** (C/B/A-/A/A+). El del proveedor no se pierde:
   columna aparte ya existente, visible en inventario ("prov: X").
   **Regla de reparación de chasis** (para la automatización de F2b/F2c): poner
   chasis nuevo sube el grado al máximo de la escala.
   **DECISIÓN PENDIENTE del usuario**: ¿A+ es un grado real distinto de A, o
   equivalen? Si se elimina A+, la escala queda C/B/A-/A (con migración de los
   A+ existentes a A y ajuste del ENUM `GRADO_PROPIO`). Afecta al valor "máximo"
   de la regla anterior y al render del SKU.

## 3. Alcance por fases

### Mini-fase "atributos SKU-ready" (post-merge F2a; antes de importar lotes reales)

Justificación del orden: cada lote importado sin esto pierde eSIM y mete color sin
validar. Debe entrar en la tanda post-merge (con suppliers, lookup IMEI y
reubicación de vistas) y ANTES del primer lote de producción.

**BD** (migración pequeña, la aplica el usuario):
- `ALTER TABLE Telefono ADD COLUMN ES_ESIM BOOLEAN NOT NULL DEFAULT FALSE` (tras GRADO_PROPIO).
- `CREATE TABLE Color_equivalencia (TEXTO_EXTERNO VARCHAR(100) PK, COLOR_OFICIAL VARCHAR(50), UPDATED_AT …)` — espejo de `Modelo_equivalencia`.
- Script de UPDATEs para renombrar los TIPO de chasis al color oficial (verificando
  antes que ningún código busca chasis por texto).

**Servidor:**
- `TelefonoInventario` + inventario SQL + upsert de importación + PATCH atributos:
  campo `esEsim`.
- Endpoints de equivalencias de color (GET/PUT, espejo de los de modelo).

**Cliente:**
- Catálogo `COLORES_OFICIALES` en código + `ColorMapper` (normalizar + mapear con
  equivalencias; espejo de `ModeloMapper`, con TDD).
- Importador: regla eSIM automática + bloque **"Colores sin mapear"** (idéntico al
  de modelos: visible solo si hay textos sin casar, "Elegir color…", equivalencia
  recordada, re-clasificación al resolver).
- Alta manual: campo Color pasa de texto libre a combo del catálogo; check eSIM
  como atributo común opcional.
- Editar atributos: Color pasa a combo del catálogo; se añade check eSIM.
- Vista inventario: columna Color muestra el canónico; (columna eSIM no — el dato
  se refleja en el SKU cuando llegue; en detalle/atributos sí visible).

### Con F2b (fuera de esta spec; anotado para la spec de F2b)
- Columna batería (`BATERIA_PCT` o similar — lo fija la spec F2b) + diagnóstico en
  la ficha de revisión + regla "cambio de batería ⇒ 100".
- Grado propio asignado sistemáticamente en la revisión.

### Tras F2b — derivación del SKU
- Función pura `SkuTelefono.derivar(TelefonoInventario) → String` (cliente; con TDD):

  `"i" + serie + variante + ("esim" si esEsim) + storageGb + colorOficial + gradoPropio + ("100" si bateria == 100)`

  - serie/variante desde el código interno de modelo mediante tabla de renderizado
    interna: `"15promax"` → `15`+`ProMax`; `"12mini"` → `12`+`mini`; `"x"` → `X`;
    `"se2020"` → `SE2020`; `"16e"` → `16e`; etc. (la tabla completa se fija en el
    plan de esa fase).
  - Atributo ausente (color/grado null, batería sin diagnosticar) ⇒ SKU incompleto
    o vacío — NUNCA inventa. Un teléfono sin revisar no tiene SKU completo y eso es
    información correcta.
- Columna SKU en inventario + CSV: **a valorar entonces** (decisión abierta a
  propósito; el usuario dijo "quizás una columna, lo valoramos después").

## 4. Fuera de alcance (explícito)

- Agrupación/conteo por SKU y exportación para la plataforma de ventas (candidatos
  a fase posterior; el modelo de datos ya lo dejará listo).
- Pantalla de gestión de colores (catálogo en código, ver decisión 3).
- Modelos eSIM como entradas propias del catálogo de modelos (se representa con el
  flag; si un día hiciera falta, sería ampliar `MODELOS_ORDENADOS`).
- Automatización "chasis usado ⇒ color/grado del teléfono" (habilitada por el
  vocabulario único, pero se diseñará con F2b/F2c cuando la reparación y la
  revisión estén conectadas al ciclo de vida).
- Backfill de teléfonos históricos (el lote del smoke se borra; si algún lote real
  entrara antes de esta mini-fase, se corregiría con un UPDATE puntual).

## 5. Criterios de éxito

- Importar un fichero con "iPhone 15 eSIM" deja el teléfono con modelo `15` y
  `ES_ESIM = true` sin intervención del usuario.
- Un color no reconocido bloquea la fila (como un modelo sin mapear) hasta que el
  usuario lo resuelve UNA vez; la equivalencia se recuerda.
- Todos los colores en BD (teléfonos nuevos) pertenecen al catálogo oficial.
- Los TIPO de chasis usan el mismo vocabulario de color que los teléfonos.
- (Tras F2b) `SkuTelefono.derivar` reproduce exactamente el formato del usuario,
  incluido el ejemplo canónico `i15ProMaxesim256BlackA100`.
