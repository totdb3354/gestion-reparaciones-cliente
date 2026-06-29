# Diseño — Manejo de errores de conexión y sesión (UX)

**Fecha:** 2026-06-29
**Estado:** aprobado para plan de implementación

## 1. Motivación

Hoy, un error del servidor (aunque dure 1 segundo) abre un **modal de error que no se
autocierra y aparece como ventana suelta**; si el servidor se recupera, el modal se queda
"colgado" y hay que cerrarlo a mano. El disparador habitual es el **refresco de fondo**
(poller cada 60s): un proceso de fondo está usando un diálogo pensado para el usuario.

Se quiere alinear el manejo de errores con el estándar de escritorio: **los errores de
fondo no interrumpen** (aviso ambiental que se cura solo), y **solo las acciones del
usuario muestran diálogo**.

## 2. Punto de partida (lo que hay)

- `utils/Alertas.mostrarError(String)`: crea un `Alert(ERROR)` con **`Modality.NONE` y sin
  owner** → ventana suelta; `.show()` no bloqueante; tiene **dedup** (`errorActivo.isShowing()`
  → no abre otro); **no se autocierra**.
- **Pollers** en los controllers con auto-refresco (MainController, StockController, y los 3
  `ReparacionController*`): `scheduleAtFixedRate(recargar, 60, 60, SECONDS)`; `recargar` hace
  `catch (SQLException) { mostrarError(e); }` → de ahí el modal de fondo.
- **`ApiClient.handleErrors`** ya clasifica por código: 401→"Sesión expirada", 403→"No tienes
  permisos", 404, 409→`StaleDataException`, 422, **5xx→"El servidor no está disponible"**, y
  `IOException`→"Sin conexión con el servidor". (La clasificación ya existe; falta explotarla.)
- 401 hoy solo muestra el diálogo; **no hay auto-logout** (te quedas en pantalla con todo
  fallando). El logout manual existe en el menú (`MainController.cerrarSesion()` → `LoginView`).

## 3. Modelo: 2 ejes → 3 cubos

Clasificación por **tipo** (transitorio 5xx/sin-conexión · accionable 4xx · sesión 401) y
**origen** (poller de fondo · acción del usuario):

| Cubo | Cuándo | Tratamiento |
|------|--------|-------------|
| **A** | Transitorio (5xx/sin conexión) **desde el poller de fondo** | **Banner** no bloqueante + reintento rápido + self-heal. Sin diálogo. |
| **B** | **Sesión expirada (401)**, venga de donde venga | **Auto-logout controlado** (modal bloqueante → login). |
| **C** | Resto: 4xx accionables, y transitorios **de una acción del usuario** | **Diálogo con owner** (no ventana suelta). |

## 4. Diseño

### 4.1 Clasificación de errores (en `ApiClient`)

Para distinguir "transitorio" de forma robusta (no por matching de strings), `ApiClient`
lanza un **marcador** para los transitorios y otro para la sesión:
- `ConexionException extends SQLException` → para **5xx** y para `IOException`/timeout
  (servidor no responde). Mensaje de usuario: "El servidor no está disponible. Inténtalo de
  nuevo en unos segundos."
- `SesionExpiradaException extends SQLException` → para **401**.
- Los demás 4xx (403/404/409/422) siguen como hoy (`StaleDataException` para 409 ya existe).

Así, los catch pueden hacer `instanceof` en vez de comparar mensajes.

### 4.2 Cubo A — Banner de "sin conexión" (refresco de fondo)

- **Estado global de conexión** `ConexionEstado` (singleton observable, p. ej.
  `BooleanProperty desconectado`). El poller activo lo actualiza: en éxito
  `reportarExito()` (desconectado=false), en `ConexionException` `reportarFallo()`
  (desconectado=true). *(Solo hay un módulo/poller activo a la vez, así que el estado es
  consistente.)*
- **Banner** (franja fina) en **`MainView`, bajo la barra de navegación**: nodo visible/managed
  ligado a `ConexionEstado.desconectado`, con texto "⚠ Sin conexión con el servidor.
  Reintentando…" (ámbar). **No bloqueante** (~24px, la app sigue usable). Aparece al fallar,
  desaparece al reconectar. Un único banner global (no por vista).
- **Reintento rápido:** el poller pasa de `scheduleAtFixedRate(60s)` a **auto-reprogramarse**
  (`schedule` con delay dinámico): **60s** en estado normal, **5s** mientras `desconectado`.
  Al reconectar, vuelve a 60s. *(Conviene un helper/patrón compartido para no duplicar esto en
  cada poller.)*
- El poller **no** llama a `mostrarError` para `ConexionException` (solo actualiza el estado).

### 4.3 Cubo B — Sesión expirada (401) → auto-logout controlado

- **Hook central:** cuando `ApiClient` detecta un 401, dispara una vez (deduplicado) el flujo
  de sesión caducada — vía un callback registrado por la app (p. ej. `SesionExpiradaHandler`
  que `MainController`/`App` registra al arrancar). Así no se repite lógica en cada catch.
- El flujo: **modal bloqueante** (`showAndWait`, con owner) "Tu sesión ha caducado. Vuelve a
  iniciar sesión." → al **Aceptar** → `Sesion.cerrar()` + cargar `LoginView` en la ventana
  principal. El modal que espera el OK **es el margen** (tiempo ilimitado para reaccionar).
- Se detienen los pollers al hacer logout (ya existe `detenerPolling()`).
- **Evitar el doble diálogo:** como el hook central ya muestra el modal de sesión, un catch de
  usuario que reciba `SesionExpiradaException` no debe abrir otro. Se resuelve con un flag de
  "logout en curso": `Alertas.mostrarError` (y el hook) lo respetan → mientras el flujo de
  sesión caducada está activo, no se abren más diálogos de error.

### 4.4 Cubo C — Diálogo con owner (resto de errores de acción de usuario)

- `Alertas.mostrarError` añade **`initOwner(ventanaActiva)`** (la `Window` enfocada) para que el
  diálogo vaya pegado a su ventana y no sea una ventana fantasma. Se mantiene el **dedup**.
- Los catch de acciones de usuario (Guardar/Confirmar/Asignar…) que reciben un error NO-401 y
  NO-transitorio-de-fondo siguen llamando a `mostrarError` (ahora con owner). Un
  `ConexionException` lanzado por una **acción del usuario** sí muestra diálogo ("servidor no
  disponible, reinténtalo"), porque el usuario espera respuesta.

## 5. Interacción entre banner y diálogo (clave)

- El **banner no bloquea**: con él en pantalla, la app es 100% usable.
- Si el usuario **navega/filtra** (solo lectura) → funciona sobre lo ya cargado.
- Si el usuario **hace una acción de escritura** estando caído → esa acción lanza
  `ConexionException` → **diálogo con owner** (Cubo C). No se queda confuso ni se pierde nada.
- **Nunca** coinciden "por lo mismo": el banner lo dispara el fondo, el diálogo solo una
  acción del usuario. Pueden estar a la vez en pantalla (banner + diálogo) solo porque el
  usuario hizo algo, no por bombardeo del fondo.
- **No** se deshabilitan botones mientras hay banner (entre poll y poll puede haber
  reconectado; mejor dejar intentar y, si falla, avisar).

## 6. Alcance

**Dentro:**
- `ApiClient`: marcadores `ConexionException`/`SesionExpiradaException` + hook 401.
- `ConexionEstado` (estado global) + banner en `MainView`.
- Pollers de los controllers con auto-refresco (MainController + StockController + los 3
  `ReparacionController*`): auto-reprogramación 60s/5s + reporte de estado, sin `mostrarError`
  para transitorios. Helper compartido para el patrón de poll si reduce duplicación.
- `Alertas`: `initOwner`.

**Fuera (YAGNI):**
- Validación Luhn (no aplica aquí).
- Sustituir el banner por una barra de conexión más elaborada (iconos, animaciones ricas).
- Deshabilitar acciones mientras desconectado.
- Tocar la lógica de filtrado/negocio.

## 7. Testing

- **Clasificación / helpers puros** (p. ej. el mapeo status→excepción en `ApiClient`, si se
  extrae a algo testeable) → JUnit donde sea viable.
- **Banner, pollers, auto-logout, owner** → **prueba manual** (no hay arnés JavaFX): simular
  servidor caído (parar el backend) y comprobar: aparece el banner, la app sigue usable,
  reintenta cada ~5s, al volver el servidor el banner desaparece en segundos; una acción de
  escritura mientras está caído da diálogo; forzar 401 (token caducado) saca el modal de
  sesión y vuelve al login al aceptar; los diálogos salen pegados a su ventana (no sueltos).

## 8. Riesgos

- **Transversal:** toca `ApiClient` (núcleo de toda llamada) y varios pollers. La
  clasificación con marcadores evita romper los catch existentes (siguen siendo `SQLException`).
- **Pollers heterogéneos:** cada controller tiene su poller/`recargar`; el plan debe migrarlos
  uno a uno preservando su comportamiento, idealmente con un helper compartido.
- **Doble disparo del 401:** el hook central debe deduplicar (no abrir N modales de sesión si
  varias llamadas fallan a la vez).

### Restricciones de compatibilidad (obligatorias)

- **El hook de 401 solo actúa con sesión activa.** Un 401 **durante el login** = credenciales
  incorrectas, NO sesión caducada → el auto-logout NO debe dispararse. Gatear el hook a
  `Sesion` logueada. `UsuarioDAO` ya trata el 401-de-login como "credenciales incorrectas"
  (comprueba el mensaje "Sesión expirada") → **preservar ese comportamiento**.
- **`ConexionException`/`SesionExpiradaException` DEBEN extender `SQLException`** y **conservar
  los mismos mensajes** que hoy, para que todos los `catch (SQLException)` y las comprobaciones
  de mensaje existentes (p. ej. `UsuarioDAO`) sigan funcionando sin cambios. El cambio es
  aditivo; no rompe ningún catch.
- **Sin impacto en servidor/BD ni en el contrato de la API.** Solo cambia la interpretación
  de los códigos en el cliente.
- **Poller:** la auto-reprogramación (60s/5s) debe reprogramar la tarea **siempre** (éxito y
  fallo) y al detener el polling (`detenerPolling`) cancelar la tarea pendiente — sin fugas.
