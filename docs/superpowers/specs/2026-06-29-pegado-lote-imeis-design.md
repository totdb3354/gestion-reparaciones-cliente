# Diseño — Pegado de lote de IMEIs en los modales de asignación

**Fecha:** 2026-06-29
**Estado:** aprobado para plan de implementación

## 1. Motivación

En los modales de **asignación de reparaciones** y de **asignación de pulidos**, el IMEI se introduce con **pistola escáner** (un IMEI = 15 dígitos + Enter, uno a uno). Eso funciona bien. Pero a veces el técnico **pega un lote** de varios IMEIs de golpe (p. ej. 10), copiados ya concatenados. Hoy el campo, ante un pegado de más de 15 dígitos, **trunca a los 15 primeros y descarta el resto** — se pierden los demás IMEIs.

Se quiere que el **mismo campo de escaneo** acepte además un pegado de varios IMEIs concatenados y los reparta en varias entradas del lote.

## 2. Punto de partida (lo que ya existe)

Ambos modales **ya funcionan como un lote** acumulando entradas antes de guardar:

| Modal | Controlador / método | Campo escaneo | Lote | "Añadir uno" |
|-------|----------------------|---------------|------|--------------|
| Asignación normal | `PendientesSuperTecnicoController.abrirFormularioAsignacion` | `tfScan` | `List<EntradaAsignacion> pila` | `intentarAnadir` (al llegar a 15 dígitos) |
| Pulidos | `PulidoSuperTecnicoController.abrirFormularioAsignacion` | `tfImei` | `List<ImeiConf> lote` | `intentarEnviar` (al llegar a 15 dígitos) |

En ambos, el listener de texto del campo: (1) quita lo no-numérico, (2) si `> 15` **trunca a 15** ← *esto es lo que cambia*, (3) si `== 15` ejecuta el "añadir uno" (que **deduplica**: si el IMEI ya está en el lote, avisa y no lo añade). La pistola escribe 15 dígitos uno a uno → nunca entra por la rama de `>15`.

## 3. Alcance

**Dentro:**
- Helper de parseo compartido (puro, testeable).
- Enganche en los **dos** modales: un pegado concatenado se reparte en varias entradas; lote corrupto se rechaza entero.

**Fuera (YAGNI):**
- Validación de checksum **Luhn** por IMEI (ver §8).
- Cualquier cambio en el guardado, el render del lote, o el escaneo single (la pistola sigue igual).
- Pegados con separadores como caso primario: el helper los tolera (quita no-dígitos), pero el formato objetivo es **concatenado sin separadores**.

## 4. Diseño

### 4.1 Helper de parseo (nuevo, compartido)

Función pura `String → resultado`, sin dependencias de UI ni de lote. Único punto con test automático.

```java
// com.reparaciones.utils.ImeiUtils  (clase nueva)
public final class ImeiUtils {
    public enum TipoPegado { INCOMPLETO, UNICO, LOTE, CORRUPTO }
    public record ResultadoPegado(TipoPegado tipo, java.util.List<String> imeis) {}

    /**
     * Clasifica el texto del campo de escaneo.
     * - Quita todo lo que no sea dígito.
     * - < 15 dígitos  -> INCOMPLETO (sigue tecleando), imeis = []
     * - == 15         -> UNICO,      imeis = [ese imei]
     * - > 15 múltiplo de 15 -> LOTE, imeis = troceado en chunks de 15
     * - > 15 NO múltiplo de 15 -> CORRUPTO, imeis = []
     */
    public static ResultadoPegado parsearPegadoImeis(String texto) { ... }
}
```

Reglas exactas:
- `digitos = texto.replaceAll("\\D", "")` (null-safe → "").
- `len < 15` → `INCOMPLETO`.
- `len == 15` → `UNICO` con `[digitos]`.
- `len > 15` y `len % 15 != 0` → `CORRUPTO`.
- `len > 15` y `len % 15 == 0` → `LOTE` con los `len/15` trozos de 15 en orden.

### 4.2 Enganche en cada modal

El listener de texto **solo invoca el helper en la rama de `> 15` dígitos** (el pegado). El resto del listener no cambia: `< 15` sigue tecleando y `== 15` ejecuta el "añadir uno" de siempre (escaneo con pistola). Se **sustituye la rama actual `if (len > 15) { truncar a 15 }`** por:

```
res = ImeiUtils.parsearPegadoImeis(textoActual)   // aquí len > 15, así que tipo es LOTE o CORRUPTO
switch (res.tipo):
  CORRUPTO -> mostrar error amable; no añadir nada; dejar el campo para reintentar
  LOTE     -> por cada imei de res.imeis: reutilizar la lógica de "añadir uno"
              (misma dedup contra el lote); render una vez; resumen; limpiar+focus campo
```

Los valores `INCOMPLETO` / `UNICO` del enum existen para que el helper sea un clasificador **completo e independientemente testeable**; en la práctica el controlador solo actúa sobre `LOTE` y `CORRUPTO` (las únicas posibles cuando `len > 15`).

- **Reutiliza la creación de entrada y la dedup existentes** (`intentarAnadir` / `intentarEnviar`). Si hace falta, se extrae el núcleo "añade un IMEI al lote y devuelve si se añadió o era duplicado" para llamarlo en bucle, sin los efectos secundarios de un escaneo single (limpiar/foco/cargar-en-form se hacen una vez al final del lote, no por IMEI).
- **No auto-cargar cada IMEI en el formulario de configuración** en el caso lote: todos entran como *pendientes de asignar* y el técnico los configura desde la lista (el escaneo single sí carga el último en el form; el lote no, para no "saltar" entre 10). *(Detalle de UX a fijar en el plan; por defecto: no auto-cargar.)*

### 4.3 Mensajes al usuario

- **Lote corrupto:** mensaje **amable**, sin jerga de "múltiplo de 15": p. ej.
  `"Algún IMEI del pegado está corrupto. Revisa y vuelve a pegar."`
- **Lote válido:** resumen breve y neutral/positivo: p. ej.
  `"8 IMEIs añadidos · 2 ya estaban en la lista."` (omitir la parte de duplicados si es 0).

### 4.4 Dedup

Igual que hoy: un IMEI ya presente en el lote (pila/lote) **se salta**, no rompe el pegado. Los duplicados (ya presentes o repetidos dentro del propio pegado) se cuentan para el resumen. Un lote válido con duplicados **sí** añade los nuevos (no es "corrupto").

## 5. Casos límite

- Pegado de N×15 dígitos → N entradas menos duplicados.
- Pegado con un dígito de más/menos (no múltiplo de 15) → **rechazo total** + mensaje amable; el lote no cambia.
- Pegado de exactamente 15 → un IMEI (igual que el escaneo normal).
- Escaneo con pistola (uno a uno) → **sin cambios**.
- Texto con separadores accidentales (espacios, saltos) → el helper los ignora (quita no-dígitos); se reparte por 15 igualmente.
- Campo vacío / solo no-dígitos → `INCOMPLETO`, no pasa nada.

## 6. Roles / permisos

Ambos modales se abren desde acciones de **SUPERTECNICO** ("Asignar reparación" / "Asignar pulidos"). No cambia: la feature vive dentro de esos modales ya restringidos.

## 7. Testing

- **Helper `ImeiUtils.parsearPegadoImeis`** → **test JUnit puro** (el repo ya tiene JUnit). Casos: 1 IMEI (15), lote exacto (2-3×15), no múltiplo (16, 29, 31…), vacío/null, con separadores intercalados, no-dígitos. Es el único punto con test automático y es barato.
- **UI (los dos modales)** → prueba manual (no hay arnés JavaFX en el repo): pegar un lote válido (entran N), pegar un lote corrupto (rechazo + mensaje), pegar duplicados (resumen), y confirmar que el **escaneo con pistola sigue igual**.

## 8. Fuera de alcance — nota sobre Luhn

Validar el **checksum Luhn** de cada IMEI detectaría desalineación aunque el total de dígitos cuadre (p. ej. un IMEI de 14 y otro de 16 = 30 dígitos, múltiplo de 15, pero mal). **No se incluye** porque el escaneo single actual **no** valida Luhn: añadirlo solo al lote sería incoherente y podría rechazar IMEIs que hoy se aceptan. Queda como posible mejora futura (aplicada por igual a single y lote).
