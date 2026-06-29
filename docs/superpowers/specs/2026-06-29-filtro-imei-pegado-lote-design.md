# Diseño — Pegado de lote en los filtros de IMEI

**Fecha:** 2026-06-29
**Estado:** aprobado para plan de implementación

## 1. Motivación

Tras añadir el pegado de lote concatenado en los modales de asignación, se quiere lo
mismo en el **campo de filtro de IMEI** de las listas (historial, pendientes, pulidos),
para **todos los roles**. Hoy el filtro ya admite **varios IMEIs separados por comas**,
pero si pegas un **blob concatenado** (sin comas), no lo reparte: se queda como un único
token gigante e inútil.

A diferencia de los modales, aquí **no hay validación de grupo / reject-all**: si un
trozo no encaja (no llega a 15 dígitos), simplemente queda como IMEI incompleto y el
campo muestra **borde rojo** (indicación que ya existe).

## 2. Punto de partida (lo que ya existe)

El `filtroImei` de cada vista tiene un listener que:
- Normaliza separadores a `", "` y limpia caracteres no válidos.
- Tras teclear/escanear un IMEI completo (15 dígitos), **añade `", "`** automáticamente.
- Calcula si hay tokens incompletos (<15) o válidos (==15) y pinta el **borde**:
  vacío → sin borde · incompleto → **rojo** · válido → **verde**.
- Filtra la lista por los IMEIs de 15 dígitos (`parsearImeis`, que separa por comas y
  toma los de longitud 15).

Este listener está **duplicado en 8 controllers**, y el método `parsearImeis` también:
`ReparacionControllerTecnico/SuperTecnico/Admin`, `PendientesSuperTecnico/Tecnico`,
`PulidoSuperTecnico/Tecnico`, `HistorialPulido`.

**Lo único que falta** es partir un pegado concatenado cada 15 dígitos; el resto
(borde rojo/verde, multi-IMEI, filtrar por válidos) ya está.

## 3. Alcance

**Dentro:**
- Helper compartido `FiltroImei` (utils) que centraliza la lógica del filtro de IMEI.
- Migración de los **8 controllers** a ese helper (elimina la duplicación, incluido
  `parsearImeis`).
- Nuevo comportamiento: el helper **parte un token de >15 dígitos en trozos de 15**.

**Fuera (YAGNI):**
- Validación de checksum **Luhn**.
- Cambiar el modelo de separador (sigue siendo coma + `", "` para mostrar).
- La lógica de filtrado en sí (qué columnas, cómo se aplica el predicado) — solo cambia
  **cómo se interpretan** los IMEIs del campo.
- Reject-all (es justo lo que NO se quiere aquí).

## 4. Diseño

### 4.1 Helper `FiltroImei` (nuevo, en `com.reparaciones.utils`)

Puro respecto a JavaFX (no toca nodos; solo `String`/`Set`/strings de estilo). Métodos:

```java
public final class FiltroImei {
    private FiltroImei() {}

    /** Forma canónica del texto del filtro: limpia, separa por ", ", parte cada token de
     *  >15 dígitos en trozos de 15 (el resto <15 queda como último token), y añade ", "
     *  tras un IMEI completo. Idempotente. */
    public static String canonicalizar(String texto);

    /** IMEIs de exactamente 15 dígitos presentes en el texto (sustituye a parsearImeis). */
    public static java.util.Set<String> imeisValidos(String texto);

    public enum EstadoFiltro { VACIO, INCOMPLETO, VALIDO }

    /** Clasifica el contenido para el borde: VACIO (sin texto) · INCOMPLETO (hay algún
     *  token <15) · VALIDO (hay al menos un IMEI de 15). Cada vista mapea esto a SU propio
     *  estilo de borde (no se unifican los estilos — ver §6). */
    public static EstadoFiltro estado(String texto);
}
```

> **Nota:** el helper **NO** decide el string de estilo. Devuelve la clasificación; cada controller conserva su `setStyle(...)` actual. Esto se debe a que 2 de las 8 vistas tienen estilos distintos (ver §6) y queremos **cero cambio visual**.

**Reglas de `canonicalizar`** (= la canonicalización actual **+** el split nuevo):
1. Quitar todo lo que no sea dígito o coma; normalizar `", "`→`,`, colapsar comas
   repetidas, quitar coma inicial.
2. **NUEVO:** por cada token entre comas, si tiene **>15 dígitos**, partirlo en trozos
   de 15 (el último puede ser <15 = resto). Reunir todos los trozos.
3. Unir con `", "` para mostrar.
4. Si el último token tiene exactamente 15 dígitos (y hay contenido), añadir `", "`
   al final (comportamiento actual: listo para el siguiente IMEI).

Es **idempotente**: tras el paso 2 ningún token supera 15 dígitos, así que re-canonicalizar
no vuelve a partir; el `", "` final ya lo maneja la lógica actual sin bucle.

### 4.2 Migración de los 8 controllers

Cada listener de `filtroImei` se reduce a:

```java
filtroImei.textProperty().addListener((obs, o, n) -> {
    String can = com.reparaciones.utils.FiltroImei.canonicalizar(n);
    if (!can.equals(n)) {
        javafx.application.Platform.runLater(() -> { filtroImei.setText(can); filtroImei.positionCaret(can.length()); });
        return;
    }
    switch (com.reparaciones.utils.FiltroImei.estado(n)) {
        case VACIO      -> filtroImei.setStyle("");
        case INCOMPLETO -> filtroImei.setStyle(<estilo rojo DE ESTA vista, sin tocar>);
        case VALIDO     -> filtroImei.setStyle(<estilo verde DE ESTA vista, sin tocar>);
    }
    <llamada de filtrado propia del controller>;   // aplicarFiltros() / aplicarFiltro(), sin cambios
});
```

- El `setStyle` de cada caso usa **exactamente los strings de estilo que esa vista tiene hoy** (6 vistas "estándar", PendientesTecnico recortado, ReparacionControllerTecnico hardcodeado). No se unifican.
- Donde el controller use `parsearImeis(n)` para filtrar → `FiltroImei.imeisValidos(n)`. Se **elimina** el método `parsearImeis` duplicado de cada controller.
- La llamada de filtrado de cada vista (el predicado, qué lista filtra; `aplicarFiltros()` o `aplicarFiltro()` en PulidoTecnico) **no cambia**.

## 5. Comportamiento detallado / casos

- Pegar `imei1imei2imei3` (45) → `imei1, imei2, imei3, ` (borde verde) y filtra por los 3.
- Pegar 47 dígitos (3×15 + 2) → `imei1, imei2, imei3, 12` (borde **rojo**, el `12` es
  incompleto) y filtra por los 3 válidos. **No** se rechaza nada.
- Teclear/escanear un IMEI (15) → se añade `", "` (igual que hoy).
- Mezcla con comas existentes (`imei1, imei2imei3` pegado) → se reparte igualmente.
- Campo vacío → sin borde, sin filtro.

## 6. Verificación de las 8 vistas (hecha) y diferencias

Se compararon los 8 listeners. El **núcleo es idéntico** en todos: la canonicalización
(`withoutSep`/`limpio`), el auto-`", "` al llegar a 15, el cálculo `hayIncompleto`/
`hayValido`, y el método `parsearImeis`. Esto es lo que se consolida en `FiltroImei`.

**Diferencias reales encontradas** (se preservan, NO se unifican):
- **6 vistas** (Admin, SuperTecnico, PulidoSuperTecnico, PulidoTecnico, PendientesSuperTecnico,
  HistorialPulido): estilo "estándar" — `FONDO_INPUT` + `FILA_INCIDENCIA_BRD`/`FILA_REPARADO_ICO`
  + redondeo/padding/font.
- **PendientesTecnico**: estilo recortado — solo fondo + color de borde, **sin** redondeo/padding/font.
- **ReparacionControllerTecnico**: colores **hardcodeados** — fondo `#F3F3F3`, verde `#8AC7AF`
  (en vez de las constantes `Colores.*`).
- **PulidoTecnico**: la llamada de filtrado es `aplicarFiltro()` (singular).

**Decisión:** cada controller conserva su `setStyle(...)` y su llamada de filtrado **tal cual**.
El helper solo aporta parseo + clasificación. Objetivo: DRY del parseo con **cero cambio
visual ni de comportamiento**.

## 7. Roles

Aplica a **todas las vistas de todos los roles** (TECNICO, SUPERTECNICO, ADMIN) que tienen
filtro de IMEI. No cambia ningún permiso; solo el parseo del campo.

## 8. Testing

- **`FiltroImei.canonicalizar`, `imeisValidos` y `estado`** → **tests JUnit puros** (mismo
  estilo que `ImeiUtilsTest`/`PiezasTest`). Casos de `canonicalizar`: concatenado exacto
  (2-3×15), concatenado con resto, ya con comas, mezcla, vacío, idempotencia
  (`canonicalizar(canonicalizar(x)) == canonicalizar(x)`). Casos de `imeisValidos`: varios
  válidos, con incompletos, vacío. Casos de `estado`: VACIO (vacío), INCOMPLETO (token <15),
  VALIDO (al menos uno de 15).
- **UI (los 8 controllers)** → prueba manual (sin arnés JavaFX): en una vista por rol,
  pegar un blob válido (se reparte, borde verde, filtra) y uno con resto (borde rojo,
  filtra por los válidos); confirmar que **teclear/escanear single** sigue igual.

## 9. Fuera de alcance — Luhn

Igual que en la feature anterior: no se valida checksum por IMEI. Posible mejora futura
aplicada por igual a modales y filtros.
