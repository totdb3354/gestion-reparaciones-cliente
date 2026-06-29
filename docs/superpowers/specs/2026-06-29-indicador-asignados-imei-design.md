# Diseño — Sub-indicador "N asignados" bajo el IMEI (vista Asignaciones)

**Fecha:** 2026-06-29
**Estado:** aprobado para plan de implementación

## 1. Motivación

En la vista **Asignaciones** (SuperTécnico), un mismo IMEI puede estar repartido entre
varios técnicos (p. ej. pantalla a un técnico, batería a otro). Hoy no hay forma de verlo
de un vistazo en la tabla. Se quiere un pequeño sub-indicador bajo el IMEI —"2 asignados"—
que avise de que ese dispositivo lo tienen varios técnicos, con el mismo look discreto que
el indicador "Reutilizado" de la columna de componente.

## 2. Punto de partida (lo que hay)

- **Vista Asignaciones** = `PendientesSuperTecnicoController`. Su `cargar()` (≈615) hace
  `datos.setAll(reparacionDAO.getAsignaciones())` → **todas** las asignaciones pendientes
  (`ID_REP LIKE 'A%'`). Cada fila es una asignación de **un técnico** a **un IMEI**, así que
  el mismo IMEI aparece en **varias filas** si lo tienen varios técnicos.
- **Celda del IMEI** (`cImei`, ≈192-213): un `TableCell` con un único `Label lbl` que muestra
  `getImei()`, con listener de selección (blanco si la fila está seleccionada, `#2C3B54` si no).
- **Patrón "Reutilizado"** (referencia visual, en `ReparacionControllerSuperTecnico.colComponente`,
  ≈489-518): `VBox(1, lblTipo, lblReut)` con un sub-label `lblReut` estilado
  `-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: #9AA0AA` (blanco si la fila está
  seleccionada), `setVisible/Managed` condicional.
- **Refresco:** `cargar()` se invoca tras cada acción (incluido el borrado de asignación:
  `eliminarAsignacion(...)` en ≈449 va seguido de `cargar()` en ≈450), al recuperar el foco de
  la ventana, al pulsar "Actualizado HH:mm", y por el poller periódico (60 s; 5 s en modo
  desconectado).
- El dato "técnicos con asignación activa para un IMEI" ya existe como endpoint
  (`reparacionDAO.getTecnicosConAsignacionActiva(imei)`), usado por la **píldora** del modal de
  asignación ("N asignados"). Aquí **no** se usa ese endpoint (ver §4): se deriva en cliente.

## 3. Alcance

**Dentro:**
- `PendientesSuperTecnicoController`: la celda `cImei` y el cálculo del conteo en `cargar()`.
- Un método de conteo **puro** (testeable con JUnit).

**Fuera (YAGNI):**
- La vista "Mis Pendientes" del técnico (decidido: solo Asignaciones — su lista no trae el dato
  global y obligaría a peticiones extra/servidor).
- Cualquier cambio en modelo, DAO, servidor, FXML o en el endpoint existente.
- Paridad exacta con la píldora del modal en el caso de reparación ya en curso (ver §5).

## 4. Diseño

### 4.1 Conteo (derivado en cliente, sin peticiones)

En `cargar()`, **tras obtener la lista** y **antes de** `datos.setAll(...)`, se calcula un mapa
IMEI → nº de técnicos distintos, a partir de la propia lista cargada, y se guarda en un campo
del controlador (`Map<String,Integer> conteoTecnicosPorImei`).

El cálculo se extrae a un método **puro y estático** (testeable), p. ej. en el propio controller
o en un helper:

```java
static java.util.Map<String,Integer> contarTecnicosPorImei(java.util.List<ReparacionResumen> filas) {
    java.util.Map<String, java.util.Set<Integer>> tecnicosPorImei = new java.util.HashMap<>();
    for (ReparacionResumen r : filas) {
        if (r.getImei() == null) continue;
        tecnicosPorImei.computeIfAbsent(r.getImei(), k -> new java.util.HashSet<>()).add(r.getIdTec());
    }
    java.util.Map<String,Integer> conteo = new java.util.HashMap<>();
    tecnicosPorImei.forEach((imei, tecnicos) -> conteo.put(imei, tecnicos.size()));
    return conteo;
}
```

Se cuentan **técnicos distintos** (`getIdTec`) por IMEI, no filas, por robustez ante cualquier
fila duplicada (técnico+IMEI repetidos). El mapa se reconstruye en **cada** `cargar()`, así que
el sub-indicador queda **tan fresco como la propia tabla** (mismo refresco; nunca incoherente con
las filas mostradas).

### 4.2 Celda del IMEI (`cImei`)

La celda pasa de un `Label` suelto a un `VBox(1, lblImei, lblAsignados)`, espejando el patrón
"Reutilizado":
- `lblImei`: el IMEI, con el estilo actual (12px, `#2C3B54`/blanco si seleccionada).
- `lblAsignados`: sub-label "N asignados", estilo `-fx-font-size: 10px; -fx-font-style: italic;
  -fx-text-fill: #9AA0AA` (y blanco si la fila está seleccionada, igual que `lblReut`).
- `VBox` alineado `CENTER_LEFT`, spacing 1.

En `updateItem`, para la fila actual: `lblImei.setText(getImei())`; se lee
`n = conteoTecnicosPorImei.getOrDefault(getImei(), 1)`; **`lblAsignados` es visible/managed solo
si `n >= 2`** y su texto es `n + " asignados"` (siempre plural: solo se muestra con n≥2). Se
mantiene el listener de selección para que ambos labels pasen a blanco cuando la fila está
seleccionada.

### 4.3 Umbral N ≥ 2

El sub-indicador se muestra **solo cuando N ≥ 2**. Toda fila tiene al menos su propio técnico
(N ≥ 1), así que "1 asignado" en cada fila sería ruido sin señal; lo útil es resaltar los
dispositivos repartidos entre varios técnicos.

## 5. Semántica del conteo

"N asignados" cuenta los técnicos con una **asignación pendiente** para ese IMEI dentro de la
lista de esta vista (las filas `A%`). Es exactamente "cuántos técnicos se reparten ahora mismo el
trabajo pendiente de este dispositivo". Puede diferir de la píldora del modal en un caso de borde:
si un técnico ya hubiera convertido su asignación en una reparación **en curso**, el endpoint de
la píldora podría contarlo y aquí no — porque para mantenerlo sin coste se deriva del listado de
asignaciones, no del endpoint. Para esta vista, el conteo de asignaciones pendientes es la lectura
natural y se obtiene gratis. (Si en el futuro se quisiera paridad exacta, requeriría llamadas por
IMEI o un campo nuevo en el DTO del servidor — fuera de alcance.)

## 6. Refresco / latencia

- **Borrado de una asignación en esta vista (acción del usuario):** `eliminarAsignacion(...)` va
  seguido de `cargar()`, que recarga la lista y reconstruye el mapa → la(s) otra(s) fila(s) del
  mismo IMEI actualizan su sub-indicador **al instante** (de "2 asignados" a sin sub-label cuando
  N baja a 1). No se espera al poller.
- **Cambios hechos por otra sesión / al finalizar el técnico:** se reflejan en el **próximo
  refresco** (poller 60 s, o 5 s en modo desconectado), o antes al recuperar el foco de la
  ventana o con el refresco manual. Es la misma frescura que el resto de la tabla.

## 7. Testing

- **`contarTecnicosPorImei(List<ReparacionResumen>)`** → **test JUnit puro** (mismo estilo que los
  helpers existentes). Casos: lista vacía → mapa vacío; un IMEI con 1 técnico → 1; un IMEI con 2
  técnicos distintos → 2; un IMEI con el **mismo** técnico repetido → 1 (distintos, no filas);
  varios IMEIs mezclados; fila con IMEI null se ignora.
- **Render de la celda (umbral, estilo, selección)** → **prueba manual** (no hay arnés JavaFX):
  con un IMEI asignado a 2 técnicos, ver "2 asignados" bajo el IMEI en ambas filas; borrar una de
  las dos asignaciones → el sub-indicador desaparece de la fila restante al instante; comprobar
  que con 1 técnico no aparece nada y que al seleccionar la fila el sub-label se ve en blanco.

## 8. Riesgos

- **Frescura del mapa en el render:** el mapa debe estar reconstruido **antes** de que las celdas
  se repinten. Construirlo a partir de la lista obtenida y **antes** de `datos.setAll(...)` lo
  garantiza (el repintado de `setAll` ya lee el mapa nuevo).
- **`getIdTec` null:** las filas de asignación (`A%`) tienen técnico asignado; aun así, el conteo
  por `Set<Integer>` tolera un null como un "técnico" más sin romper. No se espera en la práctica.
- Cambio muy localizado (una celda + un mapa en un único controlador); sin impacto en otras vistas
  ni en el servidor.
