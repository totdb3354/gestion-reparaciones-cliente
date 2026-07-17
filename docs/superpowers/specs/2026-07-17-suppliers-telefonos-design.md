# Suppliers de teléfonos — tipo en Proveedor + panel en Inventario (diseño)

**Fecha:** 2026-07-17 · **Decisión de fondo (usuario, 2026-07-16):** opción **A** — solo se
almacena el nombre del supplier, así que NO hay tabla `Supplier` propia: la tabla
`Proveedor` existente gana un campo `TIPO` que separa los dos mundos.

## Decisiones del usuario que fijan el diseño

1. **Mundos separados**: un proveedor es de piezas O de teléfonos, nunca ambos. Si algún
   día apareciera un mixto real, se crean dos filas con el mismo nombre (vía de escape
   barata, sin coste hoy).
2. **Migración auto por uso**: los proveedores ya referenciados por algún `Lote` pasan a
   `TELEFONOS`; el resto queda `COMPONENTES`. Si alguno estuviera usado en ambos
   contextos, el script lo lista en la vista previa y lo decide el usuario antes de aplicar.
3. **El tipo es implícito por ubicación — sin columna "Tipo" en ninguna UI**: los
   suppliers se gestionan en un panel propio dentro de la pestaña **Inventario** (sidebar
   nueva), y Stock → Proveedores queda solo para piezas, sin cambio visual.

## 1. BD y servidor

- **ALTER** (script `sql/migracion-suppliers.sql` en el repo servidor, con vista previa
  primero — convención de la casa):
  - `ALTER TABLE Proveedor ADD COLUMN TIPO ENUM('COMPONENTES','TELEFONOS') NOT NULL DEFAULT 'COMPONENTES';`
  - Vista previa: (a) proveedores referenciados por `Lote` (pasarán a TELEFONOS);
    (b) **conflictos** = referenciados por `Lote` Y por `Compra_componente`/`Compra_otro`
    (se listan y decide el usuario; con mundos separados no debería haber ninguno);
    (c) recuento por tipo resultante.
  - UPDATE de migración: `TELEFONOS` donde exista lote que lo referencie.
  - `crear_bd.sql` sincronizado con la columna.
- **Servidor** (`Proveedor` model + DAO + controller):
  - El JSON de proveedor gana `tipo`.
  - `GET /api/proveedores` (y su variante de activos) acepta **parámetro opcional
    `tipo`**: sin parámetro devuelve todos (compatibilidad con cliente viejo — hoy los
    combos sin filtrar son el comportamiento actual, no una regresión).
  - El alta acepta `tipo`; si no llega, `COMPONENTES` (compatibilidad hacia atrás).
    Verificar si el alta acepta ya `divisa` (el `insertar` del cliente hoy solo manda
    nombre): el diálogo inline la necesita — si no la acepta, añadirla con default EUR.
  - Las rutas NO cambian; no hay endpoints nuevos.
- **Orden de despliegue**: ALTER → servidor → cliente (el de siempre). Ventanas: servidor
  nuevo + cliente viejo = combos sin filtrar (como hoy), inocuo; cliente nuevo + servidor
  viejo NO se despliega (regla de la casa: cliente siempre el último).

## 2. Filtrado por contexto (combos)

- Compras de componentes y pedidos "otros" (`FormularioCompra*`, `FormularioOtroPedido*`):
  combos con `tipo=COMPONENTES`.
- Importador de lotes y alta manual (`ImportadorLoteDialog`, `AltaManualLoteDialog`):
  combos con `tipo=TELEFONOS`, con etiqueta **"Supplier"** en esos diálogos.
- La columna "Proveedor" del inventario agrupado y su CSV de 19 columnas **no se tocan**
  (blindados como regresión cero en la fase de reubicación, merge `9319135`).
- Cliente: `ProveedorDAO` gana el parámetro de tipo (un método/overload; los call-sites
  eligen su contexto).

## 3. Creación inline de suppliers

- **Importador**: el match por Supplier Name (case-insensitive + trim,
  `buscarProveedorPorNombre` ya existente) se mantiene. Si NO matchea, junto al aviso
  "Elige proveedor" aparece la acción **"Crear supplier \<nombre\>"** → diálogo de
  confirmación explícita con el nombre (editable) y selector de **divisa (default EUR)**
  → se crea como `TELEFONOS`, se refresca el combo y queda seleccionado. Nunca creación
  silenciosa (evita duplicados por erratas del fichero).
- **Alta manual**: entrada "Crear supplier…" como última opción del combo → mismo diálogo
  de confirmación. (Extensión coherente aprobada por el usuario.)

## 4. Gestión: pestaña Inventario con sidebar

- La pestaña Inventario pasa de cargar `AgrupadoView.fxml` a pelo a cargar un **host
  nuevo** `InventarioView.fxml` + `InventarioController` con sidebar (patrón de los hosts
  de Reparaciones): apartados **"Inventario"** (el agrupado tal cual, vía `fx:include`) y
  **"Suppliers"** (panel nuevo).
- El wiring actual de la pestaña (T3 de la fase anterior) se **mueve al host**:
  `configurar(Rol, Vista.INVENTARIO)` + `cargar()` en cada visita, `Exportable`/`Recargable`
  con **delegación al panel visible** (mismo patrón que `ReparacionControllerSuperTecnico`
  con `pnlAgrupado.isVisible()`): agrupado visible → CSV de 19 columnas de siempre;
  suppliers visible → sin CSV por ahora (YAGNI).
- **Panel Suppliers**: clon funcional del panel Proveedores de Stock (tabla nombre /
  divisa / comentario / activo + alta + editar + activar-desactivar), filtrado
  `TELEFONOS`; el alta desde aquí crea `TELEFONOS` con divisa default EUR. Sin columna
  de tipo (implícito).
- **Stock → Proveedores**: mismas pantallas, pero lista y crea solo `COMPONENTES`.
- Visibilidad: la de la pestaña (SUPERTECNICO + ADMIN; LOGISTICA se sumará en F3).
  El agrupado en modo INVENTARIO no cambia en nada (regresión cero de nuevo: columnas,
  filtros, botones por rol, CSV).

## 5. Testing

- JUnit en lo puro/extraíble: predicado de migración del script verificado a mano sobre
  la vista previa; en cliente, la lógica de filtrado/selección que sea extraíble sin UI.
- Servidor: suite completa + **validar arranque/contexto Spring** antes del merge (regla
  de la memoria: los cambios de wiring de beans pueden romper el arranque sin que la
  suite lo pille).
- Pegamento JavaFX (combos, diálogo inline, sidebar) sin test propio, con smoke del
  usuario: combos filtrados en los 4 contextos, crear supplier inline desde xlsx con
  supplier nuevo, alta manual con "Crear supplier…", gestión en el panel nuevo, Stock
  intacto, agrupado INVENTARIO intacto (regresión cero), CSV por menú.

## 6. Riesgos

- **Mover el agrupado a un host** re-toca el wiring recién hecho (T3 reubicación):
  mitigado calcando el patrón de delegación ya probado de Reparaciones y con regresión
  cero del agrupado como constraint explícita + smoke.
- **Compat de ventanas de despliegue**: cubierta en §1 (parámetro opcional, default
  COMPONENTES en alta).
- **`UNIQUE (BATCH_NUMBER, ID_PROV)`** de `Lote` no se ve afectado (la FK no cambia).
- Duplicados por erratas del xlsx: mitigados por confirmación explícita + match
  case-insensitive/trim previo.

## Fuera de alcance

- Contacto/condiciones/plazos del supplier (fue el descarte de la opción B).
- CSV del panel Suppliers.
- Cruce de `MODELO_SIN_MAPEAR` con la API (idea futura aparte, plan-futuro §lookup).
- Retirada de la columna OK del inventario (F2c) y rol LOGISTICA (F3).
