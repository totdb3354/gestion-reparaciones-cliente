# Manejo de errores de conexión y sesión — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que los errores de conexión transitorios (5xx / sin red) del refresco de fondo no abran un modal huérfano, sino un banner no bloqueante que se autocura; que un 401 desloguee de forma controlada; y que el resto de errores de acción salgan como diálogo pegado a su ventana.

**Architecture:** Cambios **solo en el cliente** (sin tocar servidor/BD/API). `ApiClient` clasifica los errores con dos excepciones marcadoras (`ConexionException`, `SesionExpiradaException`, ambas `extends SQLException`) y alimenta centralmente un estado global de conexión (`ConexionEstado`) y un hook de sesión caducada. La UI muestra un banner ligado a `ConexionEstado`; los pollers se auto-reprograman (60 s normal / 5 s desconectado); `Alertas` añade owner y suprime diálogos durante el logout.

**Tech Stack:** Java 21, JavaFX (FXML), JUnit 5 (Jupiter), Maven. Cliente en `gestion-reparaciones-cliente`.

## Global Constraints

- **Solo cliente.** No se toca servidor, BD ni el contrato de la API. Solo cambia la interpretación de los códigos HTTP en el cliente.
- **`ConexionException` y `SesionExpiradaException` DEBEN `extends java.sql.SQLException`** (igual que `StaleDataException`), para que todo `catch (SQLException)` y `catch (Exception)` existente las siga capturando sin cambios.
- **Mensajes preservados literalmente.** El 401 conserva el mensaje **`"Sesión expirada. Vuelve a iniciar sesión."`** porque `UsuarioDAO.login` hace `e.getMessage().startsWith("Sesión expirada")` para tratar el 401-de-login como credenciales incorrectas.
- **El hook de 401 solo actúa con sesión activa** (`Sesion.haySession()` true). Durante el login (`haySession()` false) NO debe dispararse el auto-logout.
- **Dedup del 401:** un único modal de sesión aunque fallen varias llamadas a la vez (flag `logoutEnCurso`).
- **Pollers sin fugas:** la auto-reprogramación reprograma siempre; al `detenerPolling()` (que hace `poller.shutdownNow()`) la tarea pendiente deja de reprogramarse (capturar `RejectedExecutionException`).
- **Hilos:** toda actualización de UI / `BooleanProperty` y el disparo del hook 401 deben ir al hilo de JavaFX (`Platform.runLater` cuando no se está en él), porque hay llamadas a `ApiClient` desde `new Thread(...)` (tasa de cambio en los formularios de compra).
- **No `Co-Authored-By` en los commits.** Trabajar en la rama `feat/manejo-errores-conexion` (ya creada).
- Paquetes: utilidades en `com.reparaciones.utils`; `Sesion` en `com.reparaciones`; controladores en `com.reparaciones.controllers`.

---

## File Structure

**Nuevos:**
- `src/main/java/com/reparaciones/utils/ConexionException.java` — marcador de error transitorio (5xx / sin red).
- `src/main/java/com/reparaciones/utils/SesionExpiradaException.java` — marcador de 401.
- `src/main/java/com/reparaciones/utils/ConexionEstado.java` — estado global de conexión (boolean fuente de verdad + property para binding) y flag `enRefresco`.
- `src/main/java/com/reparaciones/utils/Poller.java` — helper de auto-reprogramación 60 s/5 s.
- `src/test/java/com/reparaciones/utils/ApiClientClasificarTest.java` — test de la clasificación de status→excepción.
- `src/test/java/com/reparaciones/utils/ConexionEstadoTest.java` — test del estado y la selección de delay.
- `src/test/java/com/reparaciones/utils/SesionExpiradaHookTest.java` — test del gating/dedup del hook 401.

**Modificados:**
- `src/main/java/com/reparaciones/utils/ApiClient.java` — `clasificar` puro, marcadores, feed central de `ConexionEstado`, hook 401.
- `src/main/java/com/reparaciones/utils/Alertas.java` — owner (ventana enfocada) + supresión durante logout.
- `src/main/resources/views/MainView.fxml` — banner bajo la navbar.
- `src/main/resources/styles/app.css` — clase `.banner-conexion`.
- `src/main/java/com/reparaciones/controllers/MainController.java` — bind del banner, registro del hook 401, auto-logout, conversión del poller de la campana + supresión de su modal de fondo.
- `src/main/java/com/reparaciones/controllers/StockController.java`, `ReparacionControllerTecnico.java`, `ReparacionControllerSuperTecnico.java` — `scheduleAtFixedRate` → `Poller.programarSiguiente`.
- Helpers `mostrarError(Exception)` de los controladores con refresco: `StockController`, `ReparacionControllerTecnico`, `ReparacionControllerSuperTecnico`, `PendientesTecnicoController`, `PulidoTecnicoController`, `PendientesSuperTecnicoController`, `PulidoSuperTecnicoController`, `HistorialPulidoController` — suprimir el modal de `ConexionException` cuando `ConexionEstado.enRefresco()`.

---

### Task 1: Excepciones marcadoras + clasificación pura en `ApiClient`

Introduce los dos marcadores y extrae la lógica status→excepción a un método **puro y testeable**, sin cambiar todavía el comportamiento de UI (las excepciones siguen siendo `SQLException`, así que todos los `catch` actuales siguen igual).

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ConexionException.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/SesionExpiradaException.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java` (`handleErrors` 287-303, `send` 278-285)
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ApiClientClasificarTest.java`

**Interfaces:**
- Produces:
  - `class ConexionException extends java.sql.SQLException` con ctor `(String)` y `(String, Throwable)`.
  - `class SesionExpiradaException extends java.sql.SQLException` con ctor `(String)`.
  - `static java.sql.SQLException ApiClient.clasificar(int status, String msg)` — package-private, **no lanza, devuelve** la excepción correspondiente.

- [ ] **Step 1: Crear `ConexionException`**

```java
package com.reparaciones.utils;

/**
 * Marcador de error de conexión transitorio: el servidor no responde (5xx) o no hay
 * red (IOException/timeout). Extiende SQLException para no romper los catch existentes;
 * el código nuevo puede distinguirla con instanceof para mostrar un banner en vez de un modal.
 */
public class ConexionException extends java.sql.SQLException {
    public ConexionException(String mensaje) { super(mensaje); }
    public ConexionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
```

- [ ] **Step 2: Crear `SesionExpiradaException`**

```java
package com.reparaciones.utils;

/**
 * Marcador de sesión caducada (HTTP 401). Extiende SQLException para no romper los catch
 * existentes. El mensaje se conserva como "Sesión expirada. Vuelve a iniciar sesión."
 * porque UsuarioDAO.login lo comprueba con startsWith("Sesión expirada").
 */
public class SesionExpiradaException extends java.sql.SQLException {
    public SesionExpiradaException(String mensaje) { super(mensaje); }
}
```

- [ ] **Step 3: Escribir el test de `clasificar` (falla al no existir el método)**

Crear `ApiClientClasificarTest.java`:

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ApiClientClasificarTest {

    @Test
    void status_500_es_ConexionException() {
        assertInstanceOf(ConexionException.class, ApiClient.clasificar(500, "x"));
    }

    @Test
    void status_503_es_ConexionException() {
        assertInstanceOf(ConexionException.class, ApiClient.clasificar(503, "x"));
    }

    @Test
    void status_401_es_SesionExpiradaException_con_mensaje_preservado() {
        SQLException e = ApiClient.clasificar(401, "x");
        assertInstanceOf(SesionExpiradaException.class, e);
        assertTrue(e.getMessage().startsWith("Sesión expirada"),
                "el mensaje debe empezar por 'Sesión expirada' (UsuarioDAO depende de ello)");
    }

    @Test
    void status_409_es_StaleDataException() {
        assertInstanceOf(StaleDataException.class, ApiClient.clasificar(409, "conflicto"));
    }

    @Test
    void status_403_es_SQLException_pero_no_marcador() {
        SQLException e = ApiClient.clasificar(403, "x");
        assertFalse(e instanceof ConexionException);
        assertFalse(e instanceof SesionExpiradaException);
    }

    @Test
    void status_404_y_422_son_SQLException_simple() {
        assertFalse(ApiClient.clasificar(404, "x") instanceof ConexionException);
        assertFalse(ApiClient.clasificar(422, "x") instanceof ConexionException);
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que NO compila / falla**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=ApiClientClasificarTest test`
Expected: error de compilación "cannot find symbol: method clasificar".

- [ ] **Step 5: Extraer `clasificar` y usarlo en `handleErrors`**

En `ApiClient.java`, reemplazar el método `handleErrors` (líneas 287-303) por:

```java
    private static void handleErrors(HttpResponse<String> response) throws SQLException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;
        throw clasificar(status, extractMessage(response.body()));
    }

    /** Mapea un código HTTP de error a su excepción. Puro (no lanza, devuelve). */
    static SQLException clasificar(int status, String msg) {
        return switch (status) {
            case 401 -> new SesionExpiradaException("Sesión expirada. Vuelve a iniciar sesión.");
            case 403 -> new SQLException("No tienes permisos para realizar esta acción.");
            case 404 -> new SQLException("Recurso no encontrado.");
            case 409 -> new StaleDataException(msg);
            case 422 -> new SQLException("Contraseña actual incorrecta.");
            default  -> (status >= 500)
                    ? new ConexionException("El servidor no está disponible. Inténtalo de nuevo en unos segundos.")
                    : new SQLException("Error del servidor (" + status + "): " + msg);
        };
    }
```

- [ ] **Step 6: Hacer que `send` lance `ConexionException` para errores de red**

En `ApiClient.send` (281-284), cambiar el `throw`:

```java
        } catch (IOException | InterruptedException e) {
            String detalle = e.getMessage() != null ? ": " + e.getMessage() : "";
            throw new ConexionException("Sin conexión con el servidor" + detalle, e);
        }
```

- [ ] **Step 7: Ejecutar los tests y verificar que pasan**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=ApiClientClasificarTest test`
Expected: PASS (6 tests). Después `mvn -q compile` debe compilar todo el módulo sin errores.

- [ ] **Step 8: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ConexionException.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/SesionExpiradaException.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ApiClientClasificarTest.java
git commit -m "feat(errores): marcadores ConexionException/SesionExpiradaException + clasificacion pura en ApiClient"
```

---

### Task 2: `ConexionEstado` + `Poller`

Estado global de conexión (boolean fuente de verdad para lógica + `BooleanProperty` para binding del banner) con el flag `enRefresco`, y el helper de auto-reprogramación 60 s/5 s.

**Files:**
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ConexionEstado.java`
- Create: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/Poller.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ConexionEstadoTest.java`

**Interfaces:**
- Consumes: `ConexionException` (Task 1).
- Produces:
  - `ConexionEstado.reportarExito()` / `reportarFallo()` — actualizan el estado (thread-safe, marshalan a FX para la property).
  - `boolean ConexionEstado.isDesconectado()` — lectura síncrona de la fuente de verdad (sin FX).
  - `javafx.beans.property.BooleanProperty ConexionEstado.desconectadoProperty()` — para `bind` del banner.
  - `void ConexionEstado.enRefresco(boolean)` / `boolean ConexionEstado.enRefresco()` — marca de "estoy en un refresco de fondo".
  - `static long ConexionEstado.delaySegundos()` — 5 si desconectado, 60 si conectado.
  - `void Poller.programarSiguiente(ScheduledExecutorService exec, Runnable tareaFx)` — ejecuta `tareaFx` en el hilo FX dentro de un wrapper `enRefresco(true/false)` y se reprograma con `delaySegundos()`; para al cerrarse el executor.

- [ ] **Step 1: Escribir el test (falla al no existir la clase)**

Crear `ConexionEstadoTest.java`:

```java
package com.reparaciones.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConexionEstadoTest {

    @Test
    void arranca_conectado() {
        ConexionEstado.reportarExito();
        assertFalse(ConexionEstado.isDesconectado());
    }

    @Test
    void reportar_fallo_marca_desconectado_y_exito_reconecta() {
        ConexionEstado.reportarFallo();
        assertTrue(ConexionEstado.isDesconectado());
        ConexionEstado.reportarExito();
        assertFalse(ConexionEstado.isDesconectado());
    }

    @Test
    void delay_es_5_desconectado_y_60_conectado() {
        ConexionEstado.reportarFallo();
        assertEquals(5, ConexionEstado.delaySegundos());
        ConexionEstado.reportarExito();
        assertEquals(60, ConexionEstado.delaySegundos());
    }

    @Test
    void flag_enRefresco_por_defecto_false() {
        ConexionEstado.enRefresco(false);
        assertFalse(ConexionEstado.enRefresco());
        ConexionEstado.enRefresco(true);
        assertTrue(ConexionEstado.enRefresco());
        ConexionEstado.enRefresco(false);
    }
}
```

- [ ] **Step 2: Verificar que el test falla**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=ConexionEstadoTest test`
Expected: error de compilación "cannot find symbol: ConexionEstado".

- [ ] **Step 3: Crear `ConexionEstado`**

```java
package com.reparaciones.utils;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Estado global de conexión con el servidor.
 * <p>Fuente de verdad síncrona en {@code desconectado} (volatile, legible sin JavaFX para
 * la lógica de delay y los tests). La {@link #desconectadoProperty()} es un espejo para
 * enlazar (bind) el banner; se actualiza siempre en el hilo de JavaFX.</p>
 * <p>{@code enRefresco} marca que estamos dentro de un refresco de fondo (poll): los catch
 * de refresco usan esta marca para no abrir un modal por {@link ConexionException} (lo
 * indica el banner). Solo se toca en el hilo de JavaFX (single-thread), así que un boolean
 * simple basta.</p>
 */
public final class ConexionEstado {

    private static volatile boolean desconectado = false;
    private static final BooleanProperty prop = new SimpleBooleanProperty(false);
    private static boolean enRefresco = false;

    private ConexionEstado() {}

    public static void reportarExito() { set(false); }
    public static void reportarFallo() { set(true); }

    public static boolean isDesconectado() { return desconectado; }
    public static BooleanProperty desconectadoProperty() { return prop; }

    /** 5 s mientras está desconectado (reintento rápido); 60 s en estado normal. */
    public static long delaySegundos() { return desconectado ? 5 : 60; }

    public static void enRefresco(boolean v) { enRefresco = v; }
    public static boolean enRefresco() { return enRefresco; }

    private static void set(boolean v) {
        desconectado = v;                       // fuente de verdad, inmediata
        try {
            if (Platform.isFxApplicationThread()) prop.set(v);
            else Platform.runLater(() -> prop.set(v));
        } catch (IllegalStateException toolkitNoIniciado) {
            // p. ej. en tests sin JavaFX arrancado: el boolean (fuente de verdad) ya está puesto
        }
    }
}
```

- [ ] **Step 4: Crear `Poller`**

```java
package com.reparaciones.utils;

import javafx.application.Platform;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Auto-reprogramación del refresco de fondo: ejecuta {@code tareaFx} en el hilo de JavaFX
 * marcando {@link ConexionEstado#enRefresco(boolean)} alrededor, y se reprograma con
 * {@link ConexionEstado#delaySegundos()} (60 s normal, 5 s desconectado). Se detiene solo
 * cuando el executor se cierra ({@code shutdownNow} en {@code detenerPolling}).
 */
public final class Poller {

    private Poller() {}

    public static void programarSiguiente(ScheduledExecutorService exec, Runnable tareaFx) {
        try {
            exec.schedule(() -> {
                Platform.runLater(() -> {
                    ConexionEstado.enRefresco(true);
                    try { tareaFx.run(); }
                    finally { ConexionEstado.enRefresco(false); }
                });
                programarSiguiente(exec, tareaFx);   // reprograma siempre
            }, ConexionEstado.delaySegundos(), TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            // el executor se cerró (detenerPolling): dejar de reprogramar
        }
    }
}
```

- [ ] **Step 5: Ejecutar los tests y verificar que pasan**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=ConexionEstadoTest test`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ConexionEstado.java \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/Poller.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/ConexionEstadoTest.java
git commit -m "feat(errores): ConexionEstado (estado global + flag enRefresco) y helper Poller 60s/5s"
```

---

### Task 3: Feed central de `ConexionEstado` + hook de sesión caducada en `ApiClient`

`ApiClient` pasa a alimentar el estado global en cada llamada (éxito 2xx → conectado; `ConexionException` → desconectado) y a disparar un hook de sesión caducada en el 401, **gateado a sesión activa** y **deduplicado**.

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/SesionExpiradaHookTest.java`

**Interfaces:**
- Consumes: `ConexionEstado` (Task 2), `Sesion.haySession()` (`com.reparaciones.Sesion`), `Sesion.iniciar(...)`.
- Produces:
  - `static void ApiClient.setSesionExpiradaHandler(Runnable h)` — registra el flujo de logout (lo registra `MainController`).
  - `static boolean ApiClient.isLogoutEnCurso()` — lo consulta `Alertas` para suprimir diálogos durante el logout.
  - Efecto: `setToken(jwt)` rearma el flag (`logoutEnCurso = false`) tras un login correcto.

- [ ] **Step 1: Escribir el test del gating/dedup (falla al no existir la API)**

Crear `SesionExpiradaHookTest.java`. El hook se dispara desde `handleErrors`, pero la lógica de disparo es comprobable a través del comportamiento observable: registramos un handler que cuenta invocaciones y forzamos 401 vía `clasificar` + el método de disparo. Para poder testear sin red, exponer un método package-private `dispararSesionExpirada()` y conducir el estado con `Sesion`/`setToken`.

```java
package com.reparaciones.utils;

import com.reparaciones.Sesion;
import com.reparaciones.models.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SesionExpiradaHookTest {

    private AtomicInteger veces;

    @BeforeEach
    void setUp() {
        veces = new AtomicInteger(0);
        ApiClient.setSesionExpiradaHandler(veces::incrementAndGet);
        Sesion.cerrar();
        ApiClient.setToken("tok"); // rearma logoutEnCurso = false
    }

    @AfterEach
    void tearDown() {
        ApiClient.setSesionExpiradaHandler(null);
        Sesion.cerrar();
    }

    @Test
    void sin_sesion_activa_no_dispara() {            // contexto login: 401 = credenciales malas
        Sesion.cerrar();
        ApiClient.dispararSesionExpirada();
        assertEquals(0, veces.get());
    }

    @Test
    void con_sesion_activa_dispara_una_vez_y_deduplica() {
        Sesion.iniciar(new Usuario(1, "ana", "SUPERTECNICO", null));
        ApiClient.dispararSesionExpirada();
        ApiClient.dispararSesionExpirada();          // segundo 401 concurrente
        assertEquals(1, veces.get());
        assertTrue(ApiClient.isLogoutEnCurso());
    }

    @Test
    void setToken_rearma_tras_relogin() {
        Sesion.iniciar(new Usuario(1, "ana", "SUPERTECNICO", null));
        ApiClient.dispararSesionExpirada();
        ApiClient.setToken("nuevo");                 // relogin
        assertFalse(ApiClient.isLogoutEnCurso());
    }
}
```

> Nota para el implementador: confirma la firma real del constructor de `Usuario` en `com.reparaciones.models.Usuario` (en `UsuarioDAO.login` se usa `new Usuario(idUsu, nombreUsuario, rol, idTec)` con `int, String, String, Integer`). Ajusta el `new Usuario(...)` del test a esa firma si difiere.

- [ ] **Step 2: Verificar que el test falla**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=SesionExpiradaHookTest test`
Expected: error de compilación (`setSesionExpiradaHandler` / `dispararSesionExpirada` / `isLogoutEnCurso` no existen).

- [ ] **Step 3: Añadir el hook y el flag en `ApiClient`**

Tras la línea `private static String token;` (69) añadir los campos y la importación de `Sesion`:

```java
    private static Runnable sesionExpiradaHandler;
    private static volatile boolean logoutEnCurso = false;
```

Añadir el import al principio del fichero: `import com.reparaciones.Sesion;`

Añadir métodos públicos/package-private (junto a `setToken`/`clearToken`):

```java
    /** Registra el flujo de "sesión caducada" (lo llama MainController al arrancar). */
    public static void setSesionExpiradaHandler(Runnable h) { sesionExpiradaHandler = h; }

    /** @return true mientras el flujo de logout por sesión caducada está en curso. */
    public static boolean isLogoutEnCurso() { return logoutEnCurso; }

    /**
     * Dispara el flujo de sesión caducada una sola vez. No hace nada si no hay sesión activa
     * (p. ej. un 401 durante el login = credenciales incorrectas) o no hay handler registrado.
     */
    static void dispararSesionExpirada() {
        if (logoutEnCurso) return;
        if (!Sesion.haySession() || sesionExpiradaHandler == null) return;
        logoutEnCurso = true;
        sesionExpiradaHandler.run();   // el handler marshala a FX por su cuenta
    }
```

En `setToken` (86) rearmar el flag tras un login correcto:

```java
    public static void setToken(String jwt) { token = jwt; logoutEnCurso = false; }
```

- [ ] **Step 4: Alimentar `ConexionEstado` y disparar el hook en `handleErrors` y `send`**

Reemplazar `handleErrors` (la versión de la Task 1) por:

```java
    private static void handleErrors(HttpResponse<String> response) throws SQLException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) { ConexionEstado.reportarExito(); return; }
        SQLException ex = clasificar(status, extractMessage(response.body()));
        if (ex instanceof ConexionException) ConexionEstado.reportarFallo();
        if (ex instanceof SesionExpiradaException) dispararSesionExpirada();
        throw ex;
    }
```

En `send` (281-284), reportar fallo antes de lanzar:

```java
        } catch (IOException | InterruptedException e) {
            ConexionEstado.reportarFallo();
            String detalle = e.getMessage() != null ? ": " + e.getMessage() : "";
            throw new ConexionException("Sin conexión con el servidor" + detalle, e);
        }
```

- [ ] **Step 5: Ejecutar los tests y verificar que pasan**

Run: `cd gestion-reparaciones-cliente && mvn -q -Dtest=SesionExpiradaHookTest,ApiClientClasificarTest test`
Expected: PASS. Luego `mvn -q compile` sin errores.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/ApiClient.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/SesionExpiradaHookTest.java
git commit -m "feat(errores): ApiClient alimenta ConexionEstado y dispara hook 401 (gateado a sesion activa + dedup)"
```

---

### Task 4: `Alertas` con owner y supresión durante el logout

Que el diálogo de error vaya pegado a la **ventana enfocada** (no una ventana fantasma) y que no se abra ningún diálogo mientras el flujo de sesión caducada está en curso.

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/Alertas.java`

**Interfaces:**
- Consumes: `ApiClient.isLogoutEnCurso()` (Task 3).
- Produces: `Alertas.mostrarError(String)` con owner = ventana enfocada y dedup (sin cambiar la firma; los llamantes no cambian).

- [ ] **Step 1: Reescribir `Alertas`**

```java
package com.reparaciones.utils;

import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class Alertas {

    private static Stage errorActivo = null;

    public static void mostrarError(String mensaje) {
        if (ApiClient.isLogoutEnCurso()) return;                 // no molestar durante el logout
        if (errorActivo != null && errorActivo.isShowing()) return;  // dedup
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.initModality(Modality.NONE);
        alert.setHeaderText(null);
        Window owner = ventanaEnfocada();
        if (owner != null) alert.initOwner(owner);               // pegado a su ventana, no fantasma
        alert.show();
        errorActivo = (Stage) alert.getDialogPane().getScene().getWindow();
    }

    /** Ventana actualmente enfocada (la topmost real, incluso si hay un modal abierto). */
    private static Window ventanaEnfocada() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `cd gestion-reparaciones-cliente && mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Prueba manual (registrar en el informe)**

Con el servidor levantado: forzar un error de acción (p. ej. una operación inválida) y confirmar que el diálogo de error sale **pegado a su ventana** (no como ventana suelta) y que, si hay un modal abierto, aparece **delante** de él, no detrás.

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/Alertas.java
git commit -m "feat(errores): Alertas con owner (ventana enfocada) y supresion durante logout"
```

---

### Task 5: Banner en `MainView` + registro del hook 401, auto-logout y poller de la campana

UI del banner no bloqueante ligado a `ConexionEstado`, registro del flujo de auto-logout, y conversión del poller de la campana (que hoy abre un modal de fondo en el 401/5xx) a `Poller` con supresión.

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/MainView.fxml` (top, 11-75)
- Modify: `gestion-reparaciones-cliente/src/main/resources/styles/app.css` (añadir `.banner-conexion`)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/MainController.java` (initialize 105-136; poller campana 309-320; catch campana 303; cerrarSesion 873-898)

**Interfaces:**
- Consumes: `ConexionEstado.desconectadoProperty()` (Task 2), `Poller.programarSiguiente` (Task 2), `ApiClient.setSesionExpiradaHandler` (Task 3), `ConexionEstado.enRefresco()` (Task 2).
- Produces: campo `@FXML private javafx.scene.layout.HBox bannerConexion;` y método `private void forzarCierreSesionPorCaducidad()`.

- [ ] **Step 1: Añadir el banner en `MainView.fxml`**

Envolver el contenido de `<top>` en un `VBox` con la navbar existente + el banner. Sustituir el bloque `<top> ... </top>` (líneas 11-75) por:

```xml
    <top>
        <VBox>
            <HBox fx:id="navbar" styleClass="navbar" alignment="CENTER_LEFT" spacing="10">
                <padding>
                    <Insets top="0" right="20" bottom="0" left="20"/>
                </padding>

                <!-- Logo clicable → inicio según rol -->
                <Button styleClass="navbar-logo-btn" onAction="#irAInicio">
                    <graphic>
                        <ImageView fitHeight="38" preserveRatio="true">
                            <image><Image url="@/images/logoNavBar.png"/></image>
                        </ImageView>
                    </graphic>
                </Button>
                <Label text="FSGR:" styleClass="navbar-title" style="-fx-font-weight: bold;"/>
                <Label text="Gestión de Stock y Reparaciones V.0.11.1" styleClass="navbar-title" style="-fx-font-weight: normal;"/>

                <!-- Botones de navegación -->
                <HBox styleClass="nav-switch" spacing="2" alignment="CENTER">
                    <Button fx:id="btnReparaciones" text="Reparaciones"
                            styleClass="nav-btn"
                            onAction="#mostrarReparaciones"/>
                    <Button fx:id="btnStock" text="Stock"
                            styleClass="nav-btn"
                            onAction="#mostrarStock"/>
                    <Button fx:id="btnEstadisticas" text="Estadísticas"
                            styleClass="nav-btn"
                            onAction="#mostrarEstadisticas"/>
                    <Button fx:id="btnClientes" text="Clientes"
                            styleClass="nav-btn"
                            onAction="#mostrarClientes"/>
                </HBox>
                <!-- Espaciador -->
                <HBox HBox.hgrow="ALWAYS"/>

                <!-- Campana solicitudes (solo supertécnico) -->
                <StackPane fx:id="campanaPane" visible="false" managed="false" alignment="CENTER">
                    <Button fx:id="btnCampana" styleClass="navbar-bell-btn" onAction="#abrirSolicitudes">
                        <graphic>
                            <ImageView fx:id="ivCampana" fitWidth="30" fitHeight="30" preserveRatio="true"/>
                        </graphic>
                    </Button>
                    <StackPane fx:id="badgePane" visible="false" managed="false"
                               mouseTransparent="true" StackPane.alignment="TOP_RIGHT"
                               translateX="10" translateY="-7">
                        <ImageView fitWidth="16" fitHeight="16" preserveRatio="true">
                            <image><Image url="@/images/Badge.png"/></image>
                        </ImageView>
                        <Label fx:id="lblBadge" styleClass="navbar-badge" mouseTransparent="true"/>
                    </StackPane>
                </StackPane>

                <!-- Menú de usuario -->
                <Button fx:id="btnUsuario" styleClass="navbar-user-btn" onAction="#mostrarMenuUsuario">
                    <graphic>
                        <HBox spacing="10" alignment="CENTER">
                            <ImageView fitWidth="28" fitHeight="28" preserveRatio="true">
                                <image><Image url="@/images/user.png"/></image>
                            </ImageView>
                            <Label fx:id="lblUsuario" styleClass="navbar-user"/>
                        </HBox>
                    </graphic>
                </Button>
            </HBox>

            <!-- Banner de conexión: no bloqueante, oculto salvo desconexión -->
            <HBox fx:id="bannerConexion" styleClass="banner-conexion" alignment="CENTER"
                  visible="false" managed="false">
                <Label text="⚠ Sin conexión con el servidor. Reintentando…"/>
            </HBox>
        </VBox>
    </top>
```

- [ ] **Step 2: Añadir el estilo `.banner-conexion` en `app.css`**

Añadir al final de `gestion-reparaciones-cliente/src/main/resources/styles/app.css`:

```css
/* Banner de conexión (no bloqueante) */
.banner-conexion {
    -fx-background-color: #F6C453;
    -fx-padding: 4 12 4 12;
    -fx-min-height: 24;
}
.banner-conexion .label {
    -fx-text-fill: #5A4500;
    -fx-font-size: 12px;
    -fx-font-weight: bold;
}
```

- [ ] **Step 3: Declarar el campo y enlazar el banner en `MainController.initialize`**

Añadir el campo junto a los demás `@FXML` del controlador:

```java
    @FXML private javafx.scene.layout.HBox bannerConexion;
```

Al final de `initialize()` (antes del cierre del método, sobre la línea 135), añadir el bind y el registro del hook:

```java
        // Banner de conexión (no bloqueante) ligado al estado global
        bannerConexion.visibleProperty().bind(com.reparaciones.utils.ConexionEstado.desconectadoProperty());
        bannerConexion.managedProperty().bind(com.reparaciones.utils.ConexionEstado.desconectadoProperty());

        // Auto-logout controlado al caducar la sesión (un 401 con sesión activa)
        com.reparaciones.utils.ApiClient.setSesionExpiradaHandler(
                () -> javafx.application.Platform.runLater(this::forzarCierreSesionPorCaducidad));
```

- [ ] **Step 4: Añadir el método de auto-logout (reutiliza `cerrarSesion`)**

Añadir junto a `cerrarSesion()` (tras la línea 898):

```java
    /** Flujo de sesión caducada: avisa con un modal bloqueante (margen de tiempo) y vuelve al login. */
    private void forzarCierreSesionPorCaducidad() {
        javafx.scene.control.Alert aviso = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.WARNING,
                "Tu sesión ha caducado. Vuelve a iniciar sesión.",
                javafx.scene.control.ButtonType.OK);
        aviso.setHeaderText(null);
        java.util.Optional.ofNullable(btnUsuario.getScene()).map(s -> s.getWindow())
                .ifPresent(aviso::initOwner);
        aviso.showAndWait();   // espera el OK = margen ilimitado para reaccionar
        cerrarSesion();        // detiene pollers, Sesion.cerrar(), carga LoginView
    }
```

- [ ] **Step 5: Convertir el poller de la campana y suprimir su modal de fondo**

En el método que construye la ventana de notificaciones, sustituir el bloque `poller.scheduleAtFixedRate(...)` (315-320) por:

```java
        com.reparaciones.utils.Poller.programarSiguiente(poller, () -> {
            recargarRef[0].run();
            recargarAlertas.run();
        });
```

Y en el `catch` del refresco de la campana (línea 303), suprimir el modal cuando es error de conexión en refresco de fondo:

```java
            } catch (SQLException ex) {
                if (!(ex instanceof com.reparaciones.utils.ConexionException
                        && com.reparaciones.utils.ConexionEstado.enRefresco())) mostrarError(ex);
            }
```

> El `poller` de la campana ya se cierra con `poller.shutdownNow()` en `setOnHidden` (409); `Poller.programarSiguiente` deja de reprogramarse al detectar el cierre (RejectedExecutionException). Sin cambios ahí.

- [ ] **Step 6: Verificar compilación**

Run: `cd gestion-reparaciones-cliente && mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Prueba manual (registrar en el informe)**

Con un usuario SUPERTECNICO: parar el backend → en pocos segundos aparece el banner ámbar bajo la navbar y la app sigue usable; arrancar el backend → el banner desaparece en ≤5 s. Abrir la campana con el backend caído: no salta modal huérfano. (El auto-logout 401 se prueba en la Task 6 junto al resto, o forzando un token caducado.)

- [ ] **Step 8: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/MainView.fxml \
        gestion-reparaciones-cliente/src/main/resources/styles/app.css \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/MainController.java
git commit -m "feat(errores): banner de conexion + auto-logout 401 + poller campana sin modal de fondo"
```

---

### Task 6: Convertir los pollers de módulo y suprimir el modal de refresco

Migrar los 3 pollers de módulo a `Poller` y hacer que el refresco de fondo (poll, carga inicial, navegación) **no** abra modal por `ConexionException` (lo cubre el banner), conservando el diálogo para las acciones de usuario.

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/StockController.java` (poller 179-181; helper `mostrarError`)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java` (poller 217-219; helper `mostrarError`)
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java` (poller 262-264; helper `mostrarError`)
- Modify (solo helper `mostrarError`): `PendientesTecnicoController.java`, `PulidoTecnicoController.java`, `PendientesSuperTecnicoController.java`, `PulidoSuperTecnicoController.java`, `HistorialPulidoController.java`

**Interfaces:**
- Consumes: `Poller.programarSiguiente`, `ConexionEstado.enRefresco()`, `ConexionException` (Tasks 1-2).

**Patrón P (poller):** sustituir en cada controlador de módulo el bloque

```java
        poller.scheduleAtFixedRate(
                () -> javafx.application.Platform.runLater(this::recargar),
                60, 60, java.util.concurrent.TimeUnit.SECONDS);
```

por

```java
        com.reparaciones.utils.Poller.programarSiguiente(poller, this::recargar);
```

**Patrón M (helper de error):** en el `private void mostrarError(Exception e)` de cada controlador afectado (el helper que delega en `Alertas.mostrarError(e.getMessage())`), suprimir el modal cuando es conexión durante un refresco de fondo:

```java
    private void mostrarError(Exception e) {
        if (e instanceof com.reparaciones.utils.ConexionException
                && com.reparaciones.utils.ConexionEstado.enRefresco()) return;   // refresco: lo indica el banner
        Alertas.mostrarError(e.getMessage());
    }
```

> Por qué el helper y no cada `catch`: todas las cargas de refresco (`recargar`→`cargarStock`/`cargarPedidos`/`cargarProveedores`/`cargarOtros`, y los `cargar()` de los sub-controladores) funnelan su `catch (SQLException) { mostrarError(e); }` por este único helper. Cambiándolo una vez por controlador, el refresco de fondo deja de abrir modal por conexión, mientras que una **acción de usuario** (Guardar/Asignar), que se ejecuta con `enRefresco()==false`, **sí** muestra el diálogo (Cubo C). La carga inicial y la navegación entran por el mismo helper con `enRefresco()==false`; ahí un fallo de conexión sí mostraría diálogo — aceptable, pero como el poll posterior (≤5 s) rellena la vista, la experiencia es coherente con el banner.

- [ ] **Step 1: Localizar el helper `mostrarError` en cada controlador**

Run: `cd gestion-reparaciones-cliente && grep -rn "private.*void mostrarError" src/main/java/com/reparaciones/controllers/`
Expected: una declaración por cada controlador afectado. Si algún controlador NO tiene helper propio y llama directo a `Alertas.mostrarError`, aplicar el Patrón M en el punto equivalente del refresco (envolver el `catch` del/los `cargar()` de ese controlador con la misma condición).

- [ ] **Step 2: Aplicar el Patrón P en los 3 controladores de módulo**

`StockController.java` (179-181), `ReparacionControllerTecnico.java` (217-219), `ReparacionControllerSuperTecnico.java` (262-264): sustituir el `poller.scheduleAtFixedRate(...)` por `com.reparaciones.utils.Poller.programarSiguiente(poller, this::recargar);` (Patrón P).

- [ ] **Step 3: Aplicar el Patrón M en cada controlador afectado**

Editar el helper `mostrarError(Exception)` (Patrón M) en: `StockController`, `ReparacionControllerTecnico`, `ReparacionControllerSuperTecnico`, `PendientesTecnicoController`, `PulidoTecnicoController`, `PendientesSuperTecnicoController`, `PulidoSuperTecnicoController`, `HistorialPulidoController`.

- [ ] **Step 4: Verificar compilación**

Run: `cd gestion-reparaciones-cliente && mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Ejecutar toda la batería de tests**

Run: `cd gestion-reparaciones-cliente && mvn -q test`
Expected: PASS (sin regresiones).

- [ ] **Step 6: Prueba manual (registrar en el informe)**

Con el backend caído, estando en Stock y en Reparaciones (Tec y SuperTec): el refresco de fondo **no** abre modal (solo banner) y reintenta cada ~5 s; al volver el backend, el banner se va en ≤5 s y los datos se refrescan. Una **acción de escritura** (p. ej. Guardar/Asignar) con el backend caído **sí** muestra diálogo. Forzar un 401 (token caducado en BD o expirando el JWT): aparece el modal "Tu sesión ha caducado", y al Aceptar vuelve al login; tras volver a entrar, todo normal (flag rearmado). Verificar también que un **login con contraseña incorrecta** sigue mostrando "credenciales incorrectas" y NO dispara el auto-logout.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/
git commit -m "feat(errores): pollers de modulo con Poller 60s/5s y refresco de fondo sin modal de conexion"
```

---

## Notas de verificación final (post-tasks)

- **Login intacto:** 401 en login → `UsuarioDAO` devuelve null (credenciales incorrectas), sin auto-logout, porque `Sesion.haySession()` es false. Mensaje "Sesión expirada…" preservado.
- **Sin tocar servidor/BD/API.** Solo cliente.
- **Sin fugas de pollers:** `detenerPolling()`/`shutdownNow()` corta la reprogramación (RejectedExecutionException).
- **Hilos:** `ConexionEstado` marshala la property a FX; el hook 401 marshala con `Platform.runLater`; el banner se actualiza siempre en FX.
- **Flapping de banner (riesgo conocido, acotado):** el estado se alimenta centralmente desde `ApiClient` con regla "último resultado gana"; con el servidor totalmente caído o totalmente sano no parpadea; solo en el borde realmente intermitente, y se autocorrige en el siguiente ciclo (≤5 s).
