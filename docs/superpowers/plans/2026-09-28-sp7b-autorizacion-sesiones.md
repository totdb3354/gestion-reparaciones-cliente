# SP7 parte 1 (7b) — Autorización, sesiones y dos mejoras de la web — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el servidor exija por sí mismo el rol y la propiedad en cada endpoint, que desactivar o borrar a un usuario surta efecto de inmediato, y que la web cierre la sesión por inactividad y avise de versión nueva.

**Architecture:** Tres entregas de servidor desplegables por separado, más la web en paralelo, más un cierre diferido. Los permisos se expresan con los mecanismos que el servidor ya tiene (`@PreAuthorize`, `FiltroTecnico`, `PropiedadAsignacion`); lo nuevo es un interceptor único para las rutas retiradas, un servicio de estado de usuario consultado por el filtro de sesión, y en la web una marca de actividad separada de la marca de sesión.

**Tech Stack:** Servidor Spring Boot 3 + JdbcTemplate + jjwt 0.12.6, tests JUnit 5 con MockMvc y Mockito. Web Vite + React 19 + React Router + TanStack Query, tests Vitest, e2e Playwright. MariaDB 11 en Docker.

**Spec:** `docs/superpowers/specs/2026-09-28-sp7b-autorizacion-sesiones-design.md`
**Reparto de endpoints (privado, fuera de git):** `Apuntes/sp7/sp7b-endpoints-por-entrega.md`
**Inventarios (privados):** `Apuntes/sp7/inventario-a-autorizacion-servidor.md`, `inventario-b-llamantes-endpoints.md`, `inventario-c-sesion-jwt.md`

## Global Constraints

- **El cliente JavaFX no se modifica.** Referencia de lectura: rama `hotfix/0.16.3` del repo raíz, siempre con `git show hotfix/0.16.3:gestion-reparaciones-cliente/<ruta>` o `git grep <patrón> hotfix/0.16.3 -- gestion-reparaciones-cliente`. **Nunca `git checkout`.**
- **Al leer una guarda, leer el bloque completo del método.** El `@PreAuthorize` aparece 71 veces antes del `@...Mapping` y 18 veces después. Leerlo "hacia arriba" atribuye guardas falsas.
- **Una ruta en un `dao/*.java` del cliente no prueba que el taller la use.** 26 métodos de DAO están muertos y hay un controlador y su FXML huérfanos. La cadena válida es DAO → controlador → vista incluida.
- **Ante una sesión que ya no vale, 401. Nunca 403.** Solo el 401 expulsa en los dos clientes (`web/src/shared/api/client.ts:102`, `cliente/utils/ApiClient.java:318`).
- **Los tres repos son públicos.** En código, tests, mensajes de commit y docs de repo se describe el comportamiento nuevo, nunca el hueco anterior. Ni IPs, ni dominios, ni nombres de personas.
- **Commits en español, en minúscula tras el prefijo, sin `Co-Authored-By`.** Nunca `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Claude no hace SSH.** Los comandos de máquina se preparan y los ejecuta el usuario.
- **Ni dominios ni direcciones en este documento** (el repositorio es público): las máquinas se nombran con sus alias de SSH (`prod`, `preprod`) y la dirección de la web sale de `$E2E_BASE_URL` de `~/.env.e2e`, que se exporta con `set -a; . ~/.env.e2e; set +a` y nunca se imprime.
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`
- **Versión del producto: 0.9.0.** Servidor y web etiquetados juntos al final.
- **Ramas:** `feature/sp7b-autorizacion` en el repo del servidor (la crea la Task 1) y `feature/sp7b-web-sesion` en el repo de la web (la crea la Task 25).

## Estructura de ficheros

**Servidor — se crean:**

| Fichero | Responsabilidad |
|---|---|
| `security/RutasRetiradas.java` | La lista de rutas de primera generación y la regla de coincidencia. Un solo sitio que enumera las 24. |
| `security/RutasRetiradasInterceptor.java` | Rechaza esas rutas con 403 y anota el intento. |
| `config/WebMvcConfig.java` | Registra el interceptor. |
| `security/EstadoUsuarioService.java` | ¿Existe y está operativo este usuario? Con caché de 30 s e invalidación explícita. |
| `security/FrenoLookup.java` | Ritmo máximo por usuario del lookup externo de IMEI. |
| `security/EstadisticasPropias.java` | Recorte de las filas de estadísticas a las del técnico del token. |
| `security/IntentosFallidos.java` | Contador de fallos de contraseña por cuenta y espera creciente. |

**Servidor — se modifican:** `security/JwtAuthFilter.java`, `controller/PulidoController.java`, `controller/ReparacionController.java`, `controller/TelefonoController.java`, `controller/AuthController.java`, `controller/UsuarioController.java`, `controller/ComponenteController.java`, `controller/LoteController.java`, `controller/SolicitudController.java`, `controller/DificultadController.java`, `controller/TipoCambioController.java`, `dao/TelefonoDAO.java`, `dao/LogDAO.java`, `dao/UsuarioDAO.java`, `service/ImeiLookupService.java`.

**Web — se crean:** `src/shared/session/actividad.ts`, `src/shared/session/VigilanciaInactividad.tsx`, `src/shared/version/versionPublicada.ts`, `src/shared/version/AvisoVersion.tsx`, `src/app/cuenta/CambioObligatorioPage.tsx`.
**Web — se modifican:** `src/main.tsx`, `src/shared/session/arranque.ts`, `src/app/router.tsx`, `vite.config.ts`, `package.json`.

---

## ENTREGA 1 — Lo que no puede romper al taller

Objetivo: cerrar todo lo que ningún camino del JavaFX alcanza, más el borrado de teléfono y el freno del lookup. Ningún cambio de esquema.

### Task 1: Mecanismo de rutas retiradas

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/RutasRetiradas.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/RutasRetiradasInterceptor.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/config/WebMvcConfig.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/security/RutasRetiradasTest.java`

**Interfaces:**
- Produces: `RutasRetiradas.LISTA` (`List<RutasRetiradas.Ruta>`), `RutasRetiradas.Ruta(String metodo, String patron)`, `RutasRetiradas.coincide(String metodo, String uri)` → `boolean`, `RutasRetiradas.MSG_RETIRADA` (`String`), `RutasRetiradas.ACCION_LOG` (`String`). La Task 2 rellena `LISTA`.
- Consumes: `LogDAO.insertar(int idUsu, String accion, String detalle)` (existe).

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-servidor
git checkout -b feature/sp7b-autorizacion
git log -1 --oneline   # debe ser df14d6d
```

- [ ] **Step 2: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/security/RutasRetiradasTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La regla de coincidencia de las rutas de primera generación (spec sp7b §4.1). */
class RutasRetiradasTest {

    @Test void coincideLaRutaExactaYElMetodoExacto() {
        assertTrue(RutasRetiradas.coincide("GET", "/api/componentes"));
        assertFalse(RutasRetiradas.coincide("POST", "/api/componentes/agrupados"));
    }

    @Test void noCoincideOtroMetodoDeLaMismaRuta() {
        assertFalse(RutasRetiradas.coincide("PUT", "/api/componentes"));
    }

    @Test void coincideConVariableDeRuta() {
        assertTrue(RutasRetiradas.coincide("GET", "/api/telefonos/355400000000111/exists"));
    }

    @Test void noCoincideUnaRutaViva() {
        assertFalse(RutasRetiradas.coincide("GET", "/api/componentes/agrupados"));
        assertFalse(RutasRetiradas.coincide("GET", "/api/reparaciones/historial"));
        assertFalse(RutasRetiradas.coincide("DELETE", "/api/telefonos/355400000000111"));
    }
}
```

- [ ] **Step 3: Ejecutar el test y comprobar que falla**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q -Dtest=RutasRetiradasTest test
```

Esperado: FAIL con `cannot find symbol: class RutasRetiradas`.

- [ ] **Step 4: Escribir `RutasRetiradas`**

`src/main/java/com/reparaciones/servidor/security/RutasRetiradas.java`:

```java
package com.reparaciones.servidor.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Rutas de la primera generación de la API: las pantallas actuales no las usan, están sustituidas por otras y
 * ninguno de los dos clientes las llama. Responden 403 y dejan constancia del intento, para poder retirarlas con
 * una prueba real de que nadie las usa (spec sp7b §4.1 y decisión D7). El reparto y la justificación de cada una,
 * en el inventario privado.
 *
 * Al retirar la lista entera, borrar también el interceptor y su registro en WebMvcConfig.
 */
public final class RutasRetiradas {

    public record Ruta(String metodo, String patron) {}

    public static final String MSG_RETIRADA = "Esta operación ya no está disponible.";
    public static final String ACCION_LOG   = "RUTA_RETIRADA";

    /** La rellena la Task 2. */
    public static final List<Ruta> LISTA = List.of(
            new Ruta("GET", "/api/componentes"),
            new Ruta("GET", "/api/telefonos/{imei}/exists")
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private RutasRetiradas() {}

    /** @param metodo método HTTP en mayúsculas; @param uri ruta de la petición, sin query string. */
    public static boolean coincide(String metodo, String uri) {
        for (Ruta r : LISTA) {
            if (r.metodo().equals(metodo) && MATCHER.match(r.patron(), uri)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 5: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RutasRetiradasTest test
```

Esperado: PASS, 4 tests.

- [ ] **Step 6: Escribir el interceptor**

`src/main/java/com/reparaciones/servidor/security/RutasRetiradasInterceptor.java`:

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.LogDAO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rechaza con 403 las rutas de {@link RutasRetiradas} y anota el intento en el registro de actividad, con quién,
 * qué pidió y desde dónde. Corre después de la cadena de seguridad, así que el usuario del token ya está en el
 * contexto. Si el registro falla, el rechazo se mantiene: la traza va al log de la aplicación.
 */
@Component
public class RutasRetiradasInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RutasRetiradasInterceptor.class);

    private final LogDAO logDao;

    public RutasRetiradasInterceptor(LogDAO logDao) {
        this.logDao = logDao;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String metodo = request.getMethod();
        String uri    = request.getRequestURI();
        if (!RutasRetiradas.coincide(metodo, uri)) return true;
        anotar(metodo, uri, origen(request));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, RutasRetiradas.MSG_RETIRADA);
    }

    /** La dirección que ve la aplicación: la del proxy si la pone, y si no la del socket. */
    private static String origen(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Real-IP");
        if (reenviada != null && !reenviada.isBlank()) return reenviada;
        String cadena = request.getHeader("X-Forwarded-For");
        if (cadena != null && !cadena.isBlank()) return cadena.split(",")[0].trim();
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private void anotar(String metodo, String uri, String origen) {
        Integer idUsu = idUsuarioDelContexto();
        String detalle = metodo + " " + uri + (origen.isBlank() ? "" : ", ORIGEN: " + origen);
        if (idUsu == null) {
            log.warn("Intento a ruta retirada sin usuario en el contexto: {}", detalle);
            return;
        }
        try {
            logDao.insertar(idUsu, RutasRetiradas.ACCION_LOG, detalle);
        } catch (RuntimeException e) {
            log.warn("No se pudo anotar el intento a ruta retirada ({}): {}", detalle, e.toString());
        }
    }

    private static Integer idUsuarioDelContexto() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal p)) return null;
        return p.getIdUsu();
    }
}
```

- [ ] **Step 7: Registrar el interceptor**

Comprobar primero que no hay ya un `WebMvcConfigurer` en el proyecto:

```bash
cd gestion-reparaciones-servidor && grep -rn "WebMvcConfigurer" src/main/java
```

Si no hay ninguno, crear `src/main/java/com/reparaciones/servidor/config/WebMvcConfig.java`:

```java
package com.reparaciones.servidor.config;

import com.reparaciones.servidor.security.RutasRetiradasInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RutasRetiradasInterceptor rutasRetiradas;

    public WebMvcConfig(RutasRetiradasInterceptor rutasRetiradas) {
        this.rutasRetiradas = rutasRetiradas;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rutasRetiradas).addPathPatterns("/api/**");
    }
}
```

Si ya existe un `WebMvcConfigurer`, añadir el `addInterceptors` a ese en lugar de crear otro.

- [ ] **Step 8: Escribir el test de extremo a extremo del interceptor**

`src/test/java/com/reparaciones/servidor/security/RutasRetiradasInterceptorTest.java`:

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las rutas retiradas responden 403 y dejan constancia, y las vivas siguen pasando (spec sp7b §4.1). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RutasRetiradasInterceptorTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean LogDAO logDao;
    @MockBean ComponenteDAO componenteDao;

    private String tecnico() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
    }

    @Test void rutaRetiradaEs403YSeAnota() throws Exception {
        mvc.perform(get("/api/componentes").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        verify(logDao).insertar(eq(8), eq(RutasRetiradas.ACCION_LOG), contains("GET /api/componentes"));
        verifyNoInteractions(componenteDao);
    }

    @Test void rutaRetiradaConVariableEs403() throws Exception {
        mvc.perform(get("/api/telefonos/355400000000111/exists").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
    }

    @Test void sinTokenSigueSiendo401YNoSeAnota() throws Exception {
        mvc.perform(get("/api/componentes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(logDao);
    }
}
```

- [ ] **Step 9: Ejecutar los dos tests**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest='RutasRetiradas*Test' test
```

Esperado: PASS, 7 tests. Si `sinTokenSigueSiendo401YNoSeAnota` falla con 403, el interceptor corre antes de la cadena de seguridad: revisar que `WebMvcConfig` no lo registre como filtro.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/security/RutasRetiradas.java \
        src/main/java/com/reparaciones/servidor/security/RutasRetiradasInterceptor.java \
        src/main/java/com/reparaciones/servidor/config/WebMvcConfig.java \
        src/test/java/com/reparaciones/servidor/security/RutasRetiradasTest.java \
        src/test/java/com/reparaciones/servidor/security/RutasRetiradasInterceptorTest.java
git commit -m "feat: las rutas sustituidas por otras responden 403 y dejan constancia del intento"
```

### Task 2: La lista completa de rutas retiradas

**Files:**
- Modify: `security/RutasRetiradas.java` (la constante `LISTA`)
- Test: `src/test/java/com/reparaciones/servidor/security/RutasRetiradasListaTest.java`

**Interfaces:**
- Consumes: `RutasRetiradas.LISTA`, `RutasRetiradas.coincide` (Task 1).
- Produces: nada nuevo; la lista pasa de 2 a 24 entradas.

**Contexto:** son 24 rutas. Ninguna la llama la web, ni `hotfix/0.16.3`, ni `main` del cliente: el método de DAO del cliente existe pero ninguna vista lo invoca. `DELETE /api/telefonos/{imei}` **no** entra aquí (va a la Task 5) y `GET /api/reparaciones/asignaciones/{idRep}` **tampoco** (lo usa la web en `modules/taller/formulario/api.ts:26`).

- [ ] **Step 1: Confirmar que ninguna de las 24 tiene llamante**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
for m in ComponenteDAO.getAll ComponenteDAO.getStockBajo ComponenteDAO.getChasisPorColor \
         ComponenteDAO.getEvolucionStock ComponenteDAO.insertar ComponenteDAO.actualizarStock \
         ComponenteDAO.eliminar CompraComponenteDAO.getEnCamino \
         ReparacionComponenteDAO.getByReparacion ReparacionComponenteDAO.insertar \
         ReparacionComponenteDAO.eliminar ReparacionComponenteDAO.marcarIncidencia \
         ReparacionDAO.getAll ReparacionDAO.countByImei ReparacionDAO.getResumenPorImei \
         ReparacionDAO.getAsignacionesPorImei ReparacionDAO.getTecnicosConAsignacionActiva \
         ReparacionDAO.getEstadisticasPorTecnico ReparacionDAO.insertar ReparacionDAO.completar \
         TecnicoDAO.insertar TecnicoDAO.eliminar TelefonoDAO.getAll TelefonoDAO.exists ; do
  nombre="${m#*.}"
  echo "== $m"
  git grep -n "\.$nombre(" hotfix/0.16.3 -- gestion-reparaciones-cliente/src/main/java \
    | grep -v "/dao/" | head -3
done
```

Esperado: ninguna línea de salida bajo ningún `==`. Cualquier línea que aparezca **saca esa ruta de la lista** y se anota en `Apuntes/sp7/sp7b-endpoints-por-entrega.md`. Repetir el mismo bucle cambiando `hotfix/0.16.3` por `main`.

- [ ] **Step 2: Confirmar que la web no las llama**

```bash
cd gestion-reparaciones-web
for r in "'/api/componentes'" "/api/componentes/stock-bajo" "/api/componentes/chasis" \
         "/api/componentes/evolucion-stock" "/api/compras/en-camino" "/api/reparacion-componentes" \
         "'/api/reparaciones'" "/api/reparaciones/imei/{imei}/count" "/api/reparaciones/historial/imei" \
         "/api/reparaciones/asignaciones/imei" "tecnicos-asignados" "'/api/reparaciones/estadisticas'" \
         "/api/reparaciones/{idRep}/completar" "'/api/tecnicos'" "'/api/telefonos'" "/exists" ; do
  echo "== $r"; grep -rn -- "$r" src/ | head -3
done
```

Esperado: para `'/api/tecnicos'` aparecerá la lectura de la lista de técnicos (`GET`), que **no** se retira: solo se retiran `POST /api/tecnicos` y `DELETE /api/tecnicos/{idTec}`. Para el resto, ninguna línea. Cualquier otra coincidencia saca la ruta de la lista.

- [ ] **Step 3: Escribir el test de la lista**

`src/test/java/com/reparaciones/servidor/security/RutasRetiradasListaTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las 24 rutas de primera generación, y las vivas que se les parecen (spec sp7b §4.1). */
class RutasRetiradasListaTest {

    private static final List<String> RETIRADAS = List.of(
            "GET /api/componentes",
            "GET /api/componentes/stock-bajo",
            "GET /api/componentes/chasis",
            "GET /api/componentes/evolucion-stock",
            "POST /api/componentes",
            "PATCH /api/componentes/101/stock",
            "DELETE /api/componentes/101",
            "GET /api/compras/en-camino",
            "GET /api/reparacion-componentes/R20260928_1",
            "POST /api/reparacion-componentes",
            "DELETE /api/reparacion-componentes/R20260928_1/101",
            "PATCH /api/reparacion-componentes/R20260928_1/incidencia",
            "GET /api/reparaciones",
            "GET /api/reparaciones/imei/355400000000111/count",
            "GET /api/reparaciones/historial/imei/355400000000111",
            "GET /api/reparaciones/asignaciones/imei/355400000000111",
            "GET /api/reparaciones/imei/355400000000111/tecnicos-asignados",
            "GET /api/reparaciones/estadisticas",
            "POST /api/reparaciones",
            "PATCH /api/reparaciones/R20260928_1/completar",
            "POST /api/tecnicos",
            "DELETE /api/tecnicos/7",
            "GET /api/telefonos",
            "GET /api/telefonos/355400000000111/exists");

    /** Rutas vivas que comparten prefijo con alguna retirada: el matcher no debe tocarlas. */
    private static final List<String> VIVAS = List.of(
            "GET /api/componentes/agrupados",
            "GET /api/componentes/gestionados",
            "GET /api/compras",
            "GET /api/reparaciones/historial",
            "GET /api/reparaciones/asignaciones",
            "GET /api/reparaciones/asignaciones/R20260928_1",
            "GET /api/reparaciones/estadisticas/puntos",
            "PATCH /api/reparaciones/R20260928_1/por-cerrar",
            "GET /api/tecnicos",
            "GET /api/tecnicos/activos",
            "DELETE /api/telefonos/355400000000111",
            "GET /api/telefonos/355400000000111/modelo",
            "POST /api/telefonos");

    @Test void laListaTiene24Entradas() {
        assertEquals(24, RutasRetiradas.LISTA.size());
    }

    @Test void todasLasRetiradasCoinciden() {
        for (String caso : RETIRADAS) {
            String[] p = caso.split(" ", 2);
            assertTrue(RutasRetiradas.coincide(p[0], p[1]), "debería estar retirada: " + caso);
        }
    }

    @Test void ningunaRutaVivaCoincide() {
        for (String caso : VIVAS) {
            String[] p = caso.split(" ", 2);
            assertTrue(!RutasRetiradas.coincide(p[0], p[1]), "NO debería estar retirada: " + caso);
        }
    }

    @Test void noHayEntradasDuplicadas() {
        Set<String> vistas = new HashSet<>();
        for (RutasRetiradas.Ruta r : RutasRetiradas.LISTA) {
            assertTrue(vistas.add(r.metodo() + " " + r.patron()), "duplicada: " + r);
        }
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RutasRetiradasListaTest test
```

Esperado: FAIL en `laListaTiene24Entradas` (expected 24, actual 2).

- [ ] **Step 5: Rellenar la lista**

Sustituir la constante `LISTA` de `security/RutasRetiradas.java` por:

```java
    public static final List<Ruta> LISTA = List.of(
            // Almacén de primera generación: el stock lo mueve el servidor al guardar reparaciones,
            // y las pantallas usan /api/componentes/agrupados y /gestionados.
            new Ruta("GET",    "/api/componentes"),
            new Ruta("GET",    "/api/componentes/stock-bajo"),
            new Ruta("GET",    "/api/componentes/chasis"),
            new Ruta("GET",    "/api/componentes/evolucion-stock"),
            new Ruta("POST",   "/api/componentes"),
            new Ruta("PATCH",  "/api/componentes/{idCom}/stock"),
            new Ruta("DELETE", "/api/componentes/{idCom}"),
            new Ruta("GET",    "/api/compras/en-camino"),
            // Piezas de una reparación: las pantallas usan /api/reparaciones/{idAsignacion}/filas
            // y el endpoint de incidencias.
            new Ruta("GET",    "/api/reparacion-componentes/{idRep}"),
            new Ruta("POST",   "/api/reparacion-componentes"),
            new Ruta("DELETE", "/api/reparacion-componentes/{idRep}/{idCom}"),
            new Ruta("PATCH",  "/api/reparacion-componentes/{idRep}/incidencia"),
            // Reparaciones: lo hacen hoy los endpoints del formulario y del historial.
            new Ruta("GET",    "/api/reparaciones"),
            new Ruta("GET",    "/api/reparaciones/imei/{imei}/count"),
            new Ruta("GET",    "/api/reparaciones/historial/imei/{imei}"),
            new Ruta("GET",    "/api/reparaciones/asignaciones/imei/{imei}"),
            new Ruta("GET",    "/api/reparaciones/imei/{imei}/tecnicos-asignados"),
            new Ruta("GET",    "/api/reparaciones/estadisticas"),
            new Ruta("POST",   "/api/reparaciones"),
            new Ruta("PATCH",  "/api/reparaciones/{idRep}/completar"),
            // Técnicos: el alta y el borrado reales van por /api/usuarios/tecnicos.
            new Ruta("POST",   "/api/tecnicos"),
            new Ruta("DELETE", "/api/tecnicos/{idTec}"),
            // Teléfonos: las pantallas usan las consultas concretas del formulario.
            new Ruta("GET",    "/api/telefonos"),
            new Ruta("GET",    "/api/telefonos/{imei}/exists")
    );
```

- [ ] **Step 6: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest='RutasRetiradas*Test' test
```

Esperado: PASS, 11 tests. Si `ningunaRutaVivaCoincide` falla en `GET /api/reparaciones/asignaciones/R20260928_1`, el patrón `/api/reparaciones/asignaciones/imei/{imei}` está mal escrito: `{imei}` no puede tragarse un segmento intermedio.

- [ ] **Step 7: Ejecutar la suite completa**

```bash
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos. Cualquier test existente que pase por una de las 24 rutas empezará a recibir 403: eso significa que la ruta **sí** se usa en algún sitio; sacarla de la lista y anotarlo.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/security/RutasRetiradas.java \
        src/test/java/com/reparaciones/servidor/security/RutasRetiradasListaTest.java
git commit -m "feat: completar las veinticuatro rutas sustituidas por las pantallas actuales"
```

### Task 3: Rol en los módulos que el taller no usa

**Files:**
- Modify: `controller/ColorEquivalenciaController.java`, `controller/ModeloEquivalenciaController.java`, `controller/LoteController.java`, `controller/TelefonoController.java` (los GET de inventario, revisión y movimientos)
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesModulosInventarioTest.java`

**Interfaces:**
- Consumes: nada de tareas anteriores.
- Produces: nada. Solo anotaciones.

**Contexto:** siete rutas de estos módulos están hoy sin guarda. Los módulos completos (lotes, inventario, revisión, envíos, atributos, devoluciones) solo existen en `main` del cliente; la tienda ejecuta `hotfix/0.16.3` y no los tiene. El resto de rutas de esos módulos ya exigen SUPERTECNICO.

- [ ] **Step 1: Localizar las siete rutas y su fichero**

```bash
cd gestion-reparaciones-servidor
grep -rn "colores/equivalencias\|modelos/equivalencias" src/main/java/com/reparaciones/servidor/controller/
grep -rn "@GetMapping" src/main/java/com/reparaciones/servidor/controller/LoteController.java
grep -n "inventario\|{imei}/revision\"\|{imei}/movimientos\|lotes/verificar" \
     src/main/java/com/reparaciones/servidor/controller/TelefonoController.java \
     src/main/java/com/reparaciones/servidor/controller/LoteController.java
```

Apuntar fichero y línea de cada una de: `GET /api/colores/equivalencias`, `GET /api/modelos/equivalencias`, `GET /api/lotes`, `POST /api/lotes/verificar`, `GET /api/telefonos/inventario`, `GET /api/telefonos/{imei}/revision`, `GET /api/telefonos/{imei}/movimientos`.

- [ ] **Step 2: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/controller/RolesModulosInventarioTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los módulos que la tienda no usa (lotes, inventario, revisión, equivalencias) exigen SUPERTECNICO
 * también en sus lecturas (spec sp7b §4.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesModulosInventarioTest {

    /** Las siete lecturas que estaban abiertas. Formato: "METODO ruta". */
    private static final List<String> SOLO_SUPERTECNICO = List.of(
            "GET /api/colores/equivalencias",
            "GET /api/modelos/equivalencias",
            "GET /api/lotes",
            "GET /api/telefonos/inventario",
            "GET /api/telefonos/355400000000111/revision",
            "GET /api/telefonos/355400000000111/movimientos");

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TelefonoDAO telefonoDao;
    @MockBean LoteDAO loteDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }

    @Test void unTecnicoNoLeeLosModulosDeInventario() throws Exception {
        for (String caso : SOLO_SUPERTECNICO) {
            String ruta = caso.split(" ", 2)[1];
            mvc.perform(get(ruta).header("Authorization", tecnico()))
               .andExpect(status().isForbidden());
        }
    }

    @Test void unSupertecnicoSiLosLee() throws Exception {
        for (String caso : SOLO_SUPERTECNICO) {
            String ruta = caso.split(" ", 2)[1];
            mvc.perform(get(ruta).header("Authorization", supertecnico()))
               .andExpect(status().isOk());
        }
    }

    @Test void verificarLoteExigeSupertecnico() throws Exception {
        mvc.perform(post("/api/lotes/verificar").header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imeis\":[]}"))
           .andExpect(status().isForbidden());
    }
}
```

Si el nombre real del DAO de lotes o el cuerpo de `POST /api/lotes/verificar` no coinciden, ajustar el `@MockBean` y el `content` a lo que diga el controlador; el resto del test no cambia.

- [ ] **Step 3: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RolesModulosInventarioTest test
```

Esperado: FAIL en `unTecnicoNoLeeLosModulosDeInventario` (recibe 200 donde espera 403).

- [ ] **Step 4: Añadir la anotación a las siete rutas**

En cada una de las siete, añadir la línea **inmediatamente después** del `@...Mapping` (el estilo del proyecto admite los dos órdenes; se elige después para que quede pegada al método):

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
```

Con el comentario, una sola vez por fichero, encima de la primera que se anote:

```java
    // Inventario y lotes no los usa la tienda: sus lecturas exigen el mismo rol que sus escrituras (spec sp7b §4.2).
```

Comprobar que el import existe en cada fichero; si no:

```java
import org.springframework.security.access.prepost.PreAuthorize;
```

- [ ] **Step 5: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RolesModulosInventarioTest test
```

Esperado: PASS, 3 tests.

- [ ] **Step 6: Comprobar que no queda ninguna ruta de esos módulos sin guarda**

```bash
cd gestion-reparaciones-servidor
for f in ColorEquivalenciaController ModeloEquivalenciaController LoteController EnvioController; do
  echo "== $f"; grep -n "Mapping\|PreAuthorize" src/main/java/com/reparaciones/servidor/controller/$f.java 2>/dev/null
done
```

Esperado: cada `@...Mapping` tiene un `@PreAuthorize` pegado (antes o después). Leer el bloque completo de cada método, no la línea anterior.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java/com/reparaciones/servidor/controller src/test/java/com/reparaciones/servidor/controller/RolesModulosInventarioTest.java
git commit -m "feat: las lecturas de inventario, lotes, revision y equivalencias exigen supertecnico"
```

### Task 4: Rol en las rutas que solo alcanza un rol en pantalla

**Files:**
- Modify: `controller/SolicitudController.java`, `controller/DificultadController.java`, `controller/TipoCambioController.java`, `controller/ReparacionController.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesRutasDeUnRolTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: nada. Solo anotaciones.

**Contexto y evidencia del JavaFX** (del inventario privado; cada línea es un camino comprobado):

| Ruta | Rol | Único camino en el JavaFX |
|---|---|---|
| `GET /api/solicitudes` | SUPERTECNICO | la campana, `MainController:112-117`, solo se monta para supertécnico |
| `GET /api/solicitudes/count` | SUPERTECNICO | la campana |
| `PATCH /api/solicitudes/{idRc}/estado` | SUPERTECNICO | la campana |
| `PATCH /api/solicitudes/{idRc}/limpiar` | SUPERTECNICO | la campana |
| `GET /api/valores-dificultad` | ADMIN | modal "Valores de dificultad", `EstadisticasController:182-185` y `:244` |
| `GET /api/tipo-cambio/{divisa}` | SUPERTECNICO | formularios de pedido |
| `GET /api/reparaciones/asignaciones/completadas-hoy` | SUPERTECNICO **y ADMIN** | `PendientesSuperTecnicoController:1263`; el ADMIN reutiliza esa vista en solo lectura |
| `GET /api/reparaciones/imei/{imei}/tiene-asignacion` | SUPERTECNICO | `PendientesSuperTecnicoController:2689/2701` |
| `GET /api/reparaciones/{idRep}/referenciadora` | SUPERTECNICO | borrado de `AgrupadoController:1241` (bajo `esSuper`) y `ReparacionControllerSuperTecnico:1137` |

**Ojo con `completadas-hoy`:** cerrarla solo a SUPERTECNICO deja al ADMIN sin la pestaña de asignaciones, porque la reutiliza en solo lectura. Lleva los dos roles.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/controller/RolesRutasDeUnRolTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada ruta exige el rol que la alcanza en pantalla (spec sp7b §4.3). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesRutasDeUnRolTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean SolicitudDAO solicitudDao;
    @MockBean DificultadPuntosDAO dificultadDao;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void laCampanaEsDelSupertecnico() throws Exception {
        mvc.perform(get("/api/solicitudes").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/solicitudes/count").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/solicitudes").header("Authorization", supertecnico())).andExpect(status().isOk());
    }

    @Test void losValoresDeDificultadSonDelAdmin() throws Exception {
        mvc.perform(get("/api/valores-dificultad").header("Authorization", tecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/valores-dificultad").header("Authorization", supertecnico())).andExpect(status().isForbidden());
        mvc.perform(get("/api/valores-dificultad").header("Authorization", admin())).andExpect(status().isOk());
    }

    @Test void elTipoDeCambioEsDelSupertecnico() throws Exception {
        mvc.perform(get("/api/tipo-cambio/USD").header("Authorization", tecnico())).andExpect(status().isForbidden());
    }

    /** El ADMIN reutiliza la vista de asignaciones en solo lectura: completadas-hoy lleva los dos roles. */
    @Test void completadasHoyEsDelSupertecnicoYDelAdmin() throws Exception {
        mvc.perform(get("/api/reparaciones/asignaciones/completadas-hoy").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reparaciones/asignaciones/completadas-hoy").header("Authorization", supertecnico()))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reparaciones/asignaciones/completadas-hoy").header("Authorization", admin()))
           .andExpect(status().isOk());
    }

    @Test void tieneAsignacionYReferenciadoraSonDelSupertecnico() throws Exception {
        mvc.perform(get("/api/reparaciones/imei/355400000000111/tiene-asignacion").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reparaciones/R20260928_1/referenciadora").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RolesRutasDeUnRolTest test
```

Esperado: FAIL, varios 200 donde se esperan 403.

- [ ] **Step 3: Anotar las cuatro rutas de la campana**

`controller/SolicitudController.java` ya tiene un `@PreAuthorize` **de clase** en la línea 14. Comprobar qué dice:

```bash
cd gestion-reparaciones-servidor && sed -n '1,40p' src/main/java/com/reparaciones/servidor/controller/SolicitudController.java
```

Si la anotación de clase ya es `hasRole('SUPERTECNICO')`, estas cuatro rutas están en otro controlador: localizarlo con `grep -rn "api/solicitudes" src/main/java` y anotarlas allí. Si la de clase es más laxa, añadir a cada uno de los cuatro métodos:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
```

- [ ] **Step 4: Anotar las cinco restantes**

`GET /api/valores-dificultad`:

```java
    // El modal que los muestra es solo del administrador (spec sp7b §4.3).
    @PreAuthorize("hasRole('ADMIN')")
```

`GET /api/tipo-cambio/{divisa}`, `GET /api/reparaciones/imei/{imei}/tiene-asignacion` y `GET /api/reparaciones/{idRep}/referenciadora`:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
```

`GET /api/reparaciones/asignaciones/completadas-hoy` (los dos roles, porque el ADMIN reutiliza la vista):

```java
    // El ADMIN reutiliza la vista de asignaciones en solo lectura y también la lee (spec sp7b §4.3).
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
```

- [ ] **Step 5: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RolesRutasDeUnRolTest test
```

Esperado: PASS, 5 tests.

- [ ] **Step 6: Ejecutar la suite completa**

```bash
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: la campana, los valores de dificultad y el tipo de cambio exigen el rol que los abre"
```

### Task 5: Borrar un teléfono exige supertécnico y explica por qué no se puede

**Files:**
- Modify: `controller/TelefonoController.java:125-129`
- Modify: `dao/TelefonoDAO.java:113-115`
- Test: `src/test/java/com/reparaciones/servidor/dao/TelefonoDAOEliminarTest.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/TelefonoControllerEliminarTest.java`

**Interfaces:**
- Produces: `TelefonoDAO.MSG_TIENE_TRABAJOS` (`String`), usado por los dos tests.
- Consumes: nada.

**Contexto:** hoy `DELETE /api/telefonos/{imei}` no tiene guarda —la anotación de `TelefonoController:138` pertenece al `PutMapping` de `:136`, no a este borrado— y `TelefonoDAO.eliminar` es un `DELETE FROM Telefono` pelado. Cuando queda una fila de reparación apuntando al teléfono, la clave ajena `Reparacion_ibfk_1` provoca un error interno. Causa raíz documentada en el informe R3-02: al completar un pulido se inserta una fila `P…` nueva y a la `AP…` original solo se le pone `FECHA_FIN`; borrar el pulido desde el Historial solo borra la `P…`, así que la `AP…` queda huérfana y sigue referenciando al teléfono. **No se cambia ese comportamiento del pulido**: solo se explica el impedimento.

- [ ] **Step 1: Escribir el test del DAO**

`src/test/java/com/reparaciones/servidor/dao/TelefonoDAOEliminarTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Borrar un teléfono con trabajos registrados se explica en vez de romper (spec sp7b §4.4). */
class TelefonoDAOEliminarTest {

    private static final String IMEI = "355400000000111";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TelefonoDAO dao = new TelefonoDAO(jdbc);

    @Test void conTrabajosRegistradosEs409ConMensajeYNoBorra() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(TelefonoDAO.MSG_TIENE_TRABAJOS, ex.getReason());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test void sinTrabajosBorra() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(0);
        dao.eliminar(IMEI);
        verify(jdbc).update("DELETE FROM Telefono WHERE IMEI = ?", IMEI);
    }

    /** Una fila de pulido ya cerrada cuenta como trabajo registrado: es el caso del informe R3-02. */
    @Test void unaAsignacionDePulidoCerradaTambienImpideBorrar() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
    }
}
```

Si el constructor de `TelefonoDAO` recibe más dependencias, pasarlas como `mock(...)`.

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=TelefonoDAOEliminarTest test
```

Esperado: FAIL con `cannot find symbol: MSG_TIENE_TRABAJOS`.

- [ ] **Step 3: Cambiar el DAO**

En `dao/TelefonoDAO.java`, añadir la constante junto a las demás del principio de la clase:

```java
    public static final String MSG_TIENE_TRABAJOS =
            "No se puede borrar el teléfono: tiene trabajos registrados en su historial.";
```

Y sustituir el método `eliminar` (líneas 113-115) por:

```java
    /**
     * Borra el teléfono solo si no queda ninguna fila de Reparacion apuntándolo. Una asignación ya cerrada que no
     * aparece en ninguna vista también cuenta: la clave ajena la protege, y sin esta comprobación el borrado salía
     * como error interno (informe R3-02).
     */
    @Transactional
    public void eliminar(String imei) {
        Integer trabajos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ?", Integer.class, imei);
        if (trabajos != null && trabajos > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_TIENE_TRABAJOS);
        }
        jdbc.update("DELETE FROM Telefono WHERE IMEI = ?", imei);
    }
```

Añadir los imports que falten:

```java
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=TelefonoDAOEliminarTest test
```

Esperado: PASS, 3 tests.

- [ ] **Step 5: Escribir el test del controlador**

`src/test/java/com/reparaciones/servidor/controller/TelefonoControllerEliminarTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo el supertécnico borra teléfonos, y el borrado queda registrado (spec sp7b §4.4 y §5.7). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class TelefonoControllerEliminarTest {

    private static final String IMEI = "355400000000111";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TelefonoDAO dao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }

    @Test void unTecnicoNoBorraTelefonos() throws Exception {
        mvc.perform(delete("/api/telefonos/" + IMEI).header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
        verify(dao, never()).eliminar(IMEI);
    }

    @Test void elSupertecnicoBorraYQuedaRegistrado() throws Exception {
        mvc.perform(delete("/api/telefonos/" + IMEI).header("Authorization", supertecnico()))
           .andExpect(status().isNoContent());
        verify(dao).eliminar(IMEI);
        verify(logDao).insertar(eq(7), eq("ELIMINAR_TELEFONO"), contains(IMEI));
    }
}
```

- [ ] **Step 6: Cambiar el controlador**

Sustituir el método de `controller/TelefonoController.java:125-129` por:

```java
    @DeleteMapping("/{imei}")
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String imei,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.eliminar(imei);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_TELEFONO", "IMEI: " + imei);
    }
```

- [ ] **Step 7: Ejecutar los dos tests**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest='TelefonoDAOEliminarTest,TelefonoControllerEliminarTest' test
```

Esperado: PASS, 5 tests.

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: borrar un telefono exige supertecnico y explica cuando tiene trabajos registrados"
```

### Task 6: Freno del lookup externo de IMEI en el servidor

**Files:**
- Create: `security/FrenoLookup.java`
- Modify: `controller/TelefonoController.java` (el `GET /{imei}/modelo` de la línea 65)
- Test: `src/test/java/com/reparaciones/servidor/security/FrenoLookupTest.java`

**Interfaces:**
- Produces: `FrenoLookup.comprobar(int idUsu)` (lanza 429), `FrenoLookup.MSG_DEMASIADAS` (`String`), constructor de test `FrenoLookup(Supplier<Long> reloj)`.
- Consumes: nada.

**Contexto:** `GET /api/telefonos/{imei}/modelo` **no cambia de rol**: el técnico lo usa en el formulario. Dispara una consulta a un servicio externo de pago (`ImeiLookupService.lookupModeloInterno`) y hoy el único freno lo pone el cliente. El patrón de reloj inyectable es el de `idempotencia/RegistroIdempotencia`.

- [ ] **Step 1: Leer el ritmo que aplica hoy el cliente**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git grep -n -i "pacing\|throttle\|ultimoLookup\|MIN_INTERVALO\|sleep" hotfix/0.16.3 -- \
  gestion-reparaciones-cliente/src/main/java | grep -i "imei\|lookup\|modelo"
```

Anotar el valor encontrado y usarlo como `INTERVALO_MIN_MS` en el paso 3. Si no aparece ninguno, usar **1000 ms** y anotar en `Apuntes/sp7/sp7b-endpoints-por-entrega.md` que el cliente no tenía freno explícito.

- [ ] **Step 2: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/security/FrenoLookupTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ritmo máximo del lookup externo de IMEI, por usuario (spec sp7b §4.4). */
class FrenoLookupTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final FrenoLookup freno = new FrenoLookup(ahora::get);

    @Test void laPrimeraConsultaPasa() {
        assertDoesNotThrow(() -> freno.comprobar(8));
    }

    @Test void dosSeguidasDelMismoUsuarioSonDemasiadas() {
        freno.comprobar(8);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> freno.comprobar(8));
        assertEquals(429, ex.getStatusCode().value());
        assertEquals(FrenoLookup.MSG_DEMASIADAS, ex.getReason());
    }

    @Test void pasadoElIntervaloVuelveAPasar() {
        freno.comprobar(8);
        ahora.addAndGet(FrenoLookup.INTERVALO_MIN_MS);
        assertDoesNotThrow(() -> freno.comprobar(8));
    }

    @Test void elFrenoEsPorUsuario() {
        freno.comprobar(8);
        assertDoesNotThrow(() -> freno.comprobar(7));
    }
}
```

- [ ] **Step 3: Escribir `FrenoLookup`**

`src/main/java/com/reparaciones/servidor/security/FrenoLookup.java`:

```java
package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ritmo máximo por usuario de la consulta de modelo por IMEI, que llama a un servicio externo de pago. Hasta ahora
 * el ritmo lo ponía solo el cliente (spec sp7b §4.4). En memoria y por instancia, igual que el registro de
 * reintentos: si el servidor se reinicia, el contador arranca de cero, lo que es inocuo para lo que protege.
 */
@Component
public class FrenoLookup {

    /** Separación mínima entre dos consultas del mismo usuario. */
    public static final long INTERVALO_MIN_MS = 1_000L;
    public static final String MSG_DEMASIADAS = "Demasiadas consultas de IMEI seguidas. Espera un momento.";

    private final Map<Integer, Long> ultima = new ConcurrentHashMap<>();
    private final Supplier<Long> reloj;

    public FrenoLookup() {
        this(System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public FrenoLookup(Supplier<Long> reloj) {
        this.reloj = reloj;
    }

    /** @throws ResponseStatusException 429 si este usuario consultó hace menos de {@link #INTERVALO_MIN_MS}. */
    public void comprobar(int idUsu) {
        long ahora = reloj.get();
        Long previa = ultima.put(idUsu, ahora);
        if (previa != null && ahora - previa < INTERVALO_MIN_MS) {
            ultima.put(idUsu, previa);   // un rechazo no desplaza la ventana
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_DEMASIADAS);
        }
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=FrenoLookupTest test
```

Esperado: PASS, 4 tests.

- [ ] **Step 5: Llamar al freno desde el endpoint**

En `controller/TelefonoController.java`, inyectar `FrenoLookup` en el constructor y en el `GET /{imei}/modelo` de la línea 65 añadir, como primera línea del cuerpo:

```java
        frenoLookup.comprobar(principal.getIdUsu());
```

Añadiendo el parámetro si no lo tiene:

```java
                                  @AuthenticationPrincipal UsuarioPrincipal principal
```

- [ ] **Step 6: Ejecutar la suite completa**

```bash
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos. Si algún test existente pide dos veces seguidas el modelo del mismo IMEI con el mismo usuario, recibirá 429: separar esas llamadas con usuarios distintos en el test, no subir el intervalo.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: limitar el ritmo de la consulta de modelo por imei en el servidor"
```

### Task 7: Robustez del filtro de sesión

**Files:**
- Modify: `security/JwtAuthFilter.java:42-48`
- Test: `src/test/java/com/reparaciones/servidor/security/JwtAuthFilterTest.java`

**Interfaces:**
- Consumes: `JwtUtil.generateToken`, `JwtUtil.parseToken` (existen).
- Produces: nada. La Task 20 vuelve a tocar este filtro.

**Contexto:** hoy `int idUsu = claims.get("idUsu", Integer.class)` desempaqueta sin comprobar nulo, así que un token firmado sin ese dato provoca un error interno; y un token sin `rol` produce la autoridad `ROLE_null`. Los dos casos deben ser 401. No hay ni un test de este filtro.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/security/JwtAuthFilterTest.java`:

```java
package com.reparaciones.servidor.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un token bien firmado pero incompleto es 401, no un error interno (spec sp7b §4.5). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class JwtAuthFilterTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @Value("${jwt.secret}") String secreto;

    @MockBean com.reparaciones.servidor.dao.ReparacionDAO reparacionDao;
    @MockBean com.reparaciones.servidor.dao.LogDAO logDao;

    private String firmar(java.util.Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("alguien")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        claims.forEach(b::claim);
        return b.signWith(key).compact();
    }

    @Test void tokenSinIdUsuEs401() throws Exception {
        String token = firmar(java.util.Map.of("rol", "TECNICO"));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    @Test void tokenSinRolEs401() throws Exception {
        String token = firmar(java.util.Map.of("idUsu", 8));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    @Test void tokenConRolVacioEs401() throws Exception {
        String token = firmar(java.util.Map.of("idUsu", 8, "rol", "  "));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }

    @Test void tokenCompletoPasa() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());
    }

    @Test void sinCabeceraEs401() throws Exception {
        mvc.perform(get("/api/reparaciones/historial")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=JwtAuthFilterTest test
```

Esperado: FAIL en `tokenSinIdUsuEs401` con un 500 (`NullPointerException` al desempaquetar) y en `tokenSinRolEs401`.

- [ ] **Step 3: Cambiar el filtro**

En `security/JwtAuthFilter.java`, sustituir el bloque de las líneas 42-48 por:

```java
        Claims  claims   = jwtUtil.parseToken(token);
        String  username = claims.getSubject();
        String  rol      = claims.get("rol", String.class);
        Integer idUsu    = claims.get("idUsu", Integer.class);
        Integer idTec    = claims.get("idTec", Integer.class);

        // Un token bien firmado pero sin los datos del usuario no identifica a nadie: 401, no un error interno,
        // y sin autoridades inventadas (spec sp7b §4.5).
        if (idUsu == null || username == null || rol == null || rol.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        }

        var principal = new UsuarioPrincipal(idUsu, username, "", rol, idTec);
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=JwtAuthFilterTest test
```

Esperado: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: un token sin los datos del usuario responde no autorizado"
```

### Task 8: Cierre de la entrega 1

**Files:** ninguno de código. Toca `Apuntes/sp7/sp7b-endpoints-por-entrega.md` (privado) y `.superpowers/sdd/progress.md`.

**Interfaces:** ninguna.

- [ ] **Step 1: Suite completa y contrato**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -25
```

Esperado: 0 fallos y un total **mayor** que los 525 de `df14d6d`. Anotar el número exacto.

- [ ] **Step 2: Comprobar que el contrato publicado sigue siendo el que consume la web**

```bash
cd gestion-reparaciones-servidor && mvn -q -DskipTests spring-boot:run &
sleep 40
curl -s http://localhost:8080/v3/api-docs > /tmp/openapi-rama.json
cd ../gestion-reparaciones-web && python -c "import json;d=json.load(open('/tmp/openapi-rama.json'));print(len(d['paths']),'rutas',len(d['components']['schemas']),'esquemas')"
cmp /tmp/openapi-rama.json api/openapi.json && echo "IDENTICO" || echo "CAMBIA: revisar"
kill %1
```

Esperado: 137 rutas y 123 esquemas, igual que en `v0.8.5`. **Las guardas no cambian el contrato**; si `cmp` dice que cambia, mirar el diff: solo debería aparecer si se ha tocado una firma, y entonces hay que regenerar los tipos de la web.

- [ ] **Step 3: Revisión de la rama**

Pedir revisión con `superpowers:requesting-code-review` sobre el diff completo de `feature/sp7b-autorizacion` contra `df14d6d`, con foco en: guardas leídas del bloque completo del método, ninguna ruta viva dentro de `RutasRetiradas.LISTA`, y ningún texto que describa el hueco anterior en código, tests o commits.

- [ ] **Step 4: Preparar el resumen para el usuario y PEDIR OK**

Escribir, para que el usuario lo lea antes de decidir:
- número de tests, commits de la rama y `git log --oneline df14d6d..HEAD`
- las 24 rutas retiradas y las 16 que cambian de rol
- que el contrato no cambia
- que el despliegue va **primero a producción**, donde hoy no trabaja nadie

**No hacer `merge` ni `push`.** Esperar OK explícito.

- [ ] **Step 5: Merge y push (tras el OK, uno a uno)**

```bash
cd gestion-reparaciones-servidor
git checkout main && git merge --no-ff feature/sp7b-autorizacion
# pedir OK del push por separado
git push origin main
```

- [ ] **Step 6: Despliegue en PRODUCCIÓN — comandos para el usuario**

**Máquina:** producción. **Hora:** cualquiera; hoy en esa máquina solo hay datos de prueba y no trabaja nadie. Procedimiento P8 de `Apuntes/despliegue_vdc_produccion.md`:

```bash
ssh prod
cd /opt/reparaciones/gestion-reparaciones-servidor && git pull
cd /opt/reparaciones && docker compose up -d --build
docker compose ps
docker compose logs --tail 40 backend | grep -i "started\|error"
```

Esperado: `Started App`, el contenedor del backend `Up`.

- [ ] **Step 7: Comprobar en producción**

Con las credenciales de `~/.env.e2e` exportadas y **nunca impresas**:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
set -a; . ~/.env.e2e; set +a
TOKEN=$(curl -s -X POST $E2E_BASE_URL/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"usuario\":\"$E2E_TECNICO_USUARIO\",\"password\":\"$E2E_TECNICO_PASSWORD\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
for r in /api/componentes /api/telefonos /api/reparaciones /api/lotes /api/telefonos/inventario /api/solicitudes /api/valores-dificultad; do
  printf "%-34s %s\n" "$r" "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$E2E_BASE_URL$r")"
done
printf "%-34s %s\n" "/api/reparaciones/historial" "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" '$E2E_BASE_URL/api/reparaciones/historial')"
```

Esperado: `403` en las siete primeras y `200` en el historial del técnico. Después, en la web, recorrido de los tres roles con las sesiones guardadas de `Apuntes/superverificacion/sesiones/entrar.mjs` (los ficheros de uno en uno, con un minuto de separación, porque cada uno inicia sesión).

- [ ] **Step 8: Probar el JavaFX real contra producción**

**Este paso no se puede omitir.** Levantar el cliente de la rama del taller apuntado a producción, como se hizo para las capturas del almacén:

```bash
cd /c/Users/dev/Documents/_ref-hotfix-0163
grep -n "api.url" gestion-reparaciones-cliente/src/main/resources/config.properties
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-cliente && mvn -q javafx:run
```

Si ese worktree ya no existe, crearlo con `git worktree add` sobre `hotfix/0.16.3` y apuntar su `config.properties` a producción (no versionado).

Recorrido mínimo, con el usuario **técnico** de prueba: Mis pendientes carga; abrir el formulario de una asignación y guardar una fila; Historial en los tres toggles; pestaña IMEIs con su filtro de técnicos; Stock; Clientes; Estadísticas. Después con el **supertécnico**: asignaciones, la campana, y **borrar un pulido desde la vista Agrupado**. Anotar cualquier diálogo de error.

- [ ] **Step 9: Despliegue en PREPRODUCCIÓN — comandos para el usuario**

**Máquina:** preproducción, la del taller. **Hora: FUERA DE HORARIO**, nunca en jornada. Solo después de que los pasos 7 y 8 estén en verde.

```bash
ssh preprod
cd /opt/reparaciones/gestion-reparaciones-servidor && git log -1 --oneline   # anotar para la vuelta atrás
git pull
cd /opt/reparaciones && docker compose up -d --build
docker compose logs --tail 40 backend | grep -i "started\|error"
```

**Vuelta atrás:** `git checkout <commit anotado>` en el clon del servidor y `docker compose up -d --build`.

A partir de este despliegue empieza el **periodo de observación** de las rutas retiradas (Task 30).

- [ ] **Step 10: Documentar y anotar**

- Entrada en el registro de sesiones de `Apuntes/despliegue_vdc_produccion.md` con los comandos ejecutados y su salida, y lo mismo en `despliegue_vdc.md` para preproducción.
- Actualizar `Apuntes/sp7/sp7b-endpoints-por-entrega.md` con lo que haya cambiado respecto al reparto previsto.
- Línea en `.superpowers/sdd/progress.md` con el commit de `main`, el número de tests y la fecha de inicio de la observación.

---

## ENTREGA 2 — Lo delicado: lo que el técnico usa hoy

**Lectura del código antes de empezar (hecha el 2026-09-28, verificada línea a línea).** De los 23 casos que el inventario marcó en rojo, la mayoría **ya están cerrados**; el rojo significaba "no los cierres a supertécnico o rompes al taller", no "son huecos":

| Ya cerrado | Dónde |
|---|---|
| Las 3 listas de asignaciones y los 3 historiales verifican `?tecnico=` contra el token | `ReparacionController:90,104`, `GlassController:55,61`, `PulidoController:40,47`, todos vía `FiltroTecnico.efectivo` |
| `pendientes/contadores` idem | `ReparacionController:159-167` |
| Los cuatro toggles del menú contextual comprueban el dueño, con mensaje propio | `ReparacionController:366-389` (por-cerrar), `:396-...` (entrega-glass), y los dos de llegada |
| Las escrituras del formulario comprueban dueño | `ReparacionController:303, 331, 610, 632, 696, 704, 712` vía `PropiedadAsignacion` |
| Editar una reparación ya hecha exige supertécnico | `ReparacionController:530-531` |
| Borrar una reparación exige supertécnico | `ReparacionController:667-668` |

Queda por hacer, entonces: **completar pulidos en lote** (el único caso en que un usuario con sesión puede cerrar trabajo ajeno), **estadísticas**, **editar el modelo de un teléfono**, y **fijar con tests lo que ya está bien** para que nadie lo retire sin darse cuenta.

### Task 9: Completar pulidos en lote solo los propios y solo pulidos

**Files:**
- Modify: `controller/PulidoController.java:68-75`
- Test: `src/test/java/com/reparaciones/servidor/controller/PulidoControllerCompletarLoteTest.java`

**Interfaces:**
- Consumes: `PropiedadAsignacion.tecnicoEfectivo(UsuarioPrincipal, Integer)` y `PropiedadAsignacion.MSG_NO_ES_TUYA` (existen), `ReparacionDAO.getIdTecDeAsignacion(String)` → `Integer` (existe, `ReparacionDAO:307`).
- Produces: `PulidoController.MSG_NO_ES_PULIDO` (`String`).

**Contexto:** hoy `completarLote` llama directo a `dao.completarPulidoLote(req.ids())` sin mirar nada, y `completarPulidoLote` (`ReparacionDAO:1175`) recorre los ids llamando a `completarPulido`. Cualquier usuario con sesión puede cerrar cualquier asignación abierta, de cualquier tipo y de cualquier técnico. El botón que lo usa es "Completar seleccionados" de la lista de pulidos pendientes **del propio técnico** (`PulidoTecnicoController:226-237`), así que exigir dueño y prefijo `AP` no le quita nada.

- [ ] **Step 1: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/controller/PulidoControllerCompletarLoteTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.security.PropiedadAsignacion;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Completar pulidos en lote: solo los propios y solo pulidos (spec sp7b §4.4). */
class PulidoControllerCompletarLoteTest {

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final PulidoController ctl = new PulidoController(
            dao, logDao, mock(TelefonoDAO.class), mock(ImeiLookupService.class));

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal otroTecnico = new UsuarioPrincipal(9, "tecnico_o", "x", "TECNICO", 5);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    private static PulidoController.LoteRequest lote(String... ids) {
        return new PulidoController.LoteRequest(List.of(ids));
    }

    @Test void losPropiosSeCompletan() {
        when(dao.getIdTecDeAsignacion("AP20260928_1")).thenReturn(4);
        when(dao.getIdTecDeAsignacion("AP20260928_2")).thenReturn(4);
        ctl.completarLote(lote("AP20260928_1", "AP20260928_2"), tecnico);
        verify(dao).completarPulidoLote(List.of("AP20260928_1", "AP20260928_2"));
    }

    @Test void unPulidoAjenoEs403YNoCompletaNinguno() {
        when(dao.getIdTecDeAsignacion("AP20260928_1")).thenReturn(4);
        when(dao.getIdTecDeAsignacion("AP20260928_9")).thenReturn(5);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.completarLote(lote("AP20260928_1", "AP20260928_9"), tecnico));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        verify(dao, never()).completarPulidoLote(any());
        verifyNoInteractions(logDao);
    }

    @Test void unaAsignacionQueNoEsDePulidoEs422() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.completarLote(lote("A20260928_1"), tecnico));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals(PulidoController.MSG_NO_ES_PULIDO, ex.getReason());
        verify(dao, never()).completarPulidoLote(any());
    }

    @Test void unaGlassTampocoCuela() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.completarLote(lote("AG20260928_1"), tecnico));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test void elAdminNoCompletaPulidos() {
        when(dao.getIdTecDeAsignacion("AP20260928_1")).thenReturn(4);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.completarLote(lote("AP20260928_1"), admin));
        assertEquals(403, ex.getStatusCode().value());
        verify(dao, never()).completarPulidoLote(any());
    }

    @Test void otroTecnicoNoCompletaLosDelPrimero() {
        when(dao.getIdTecDeAsignacion("AP20260928_1")).thenReturn(4);
        assertThrows(ResponseStatusException.class, () -> ctl.completarLote(lote("AP20260928_1"), otroTecnico));
        verify(dao, never()).completarPulidoLote(any());
    }

    /** Una lista vacía no escribe nada y no falla: es lo que hacía antes. */
    @Test void laListaVaciaNoEscribe() {
        ctl.completarLote(lote(), tecnico);
        verify(dao).completarPulidoLote(List.of());
    }
}
```

Ajustar la lista de dependencias del constructor de `PulidoController` a la real; leerla con `sed -n '1,40p' src/main/java/com/reparaciones/servidor/controller/PulidoController.java`.

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q -Dtest=PulidoControllerCompletarLoteTest test
```

Esperado: FAIL con `cannot find symbol: MSG_NO_ES_PULIDO` y, una vez añadida la constante, con los 403 y 422 que no se lanzan.

- [ ] **Step 3: Cambiar el controlador**

Añadir la constante al principio de la clase `PulidoController`:

```java
    public static final String MSG_NO_ES_PULIDO = "Solo se pueden completar asignaciones de pulido";
```

Y sustituir el método de las líneas 68-75 por:

```java
    /**
     * Cierra en bloque los pulidos pendientes del técnico que llama. Comprueba los dos límites antes de escribir
     * nada: que cada id es una asignación de pulido (prefijo AP) y que es suya. Así el botón "Completar
     * seleccionados" de la lista propia sigue funcionando igual y no se puede cerrar trabajo de nadie más
     * (spec sp7b §4.4). Las dos comprobaciones van en una pasada previa: o se completan todos o ninguno.
     */
    @PostMapping("/asignaciones/completar-lote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completarLote(@RequestBody LoteRequest req,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        for (String idAP : req.ids()) {
            if (idAP == null || !idAP.startsWith("AP")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_NO_ES_PULIDO);
            }
            PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idAP));
        }
        dao.completarPulidoLote(req.ids());
        logDao.insertar(principal.getIdUsu(), "COMPLETAR_PULIDO_LOTE",
                "IDS: " + String.join(",", req.ids()));
    }
```

Añadir los imports que falten:

```java
import com.reparaciones.servidor.security.PropiedadAsignacion;
import org.springframework.web.server.ResponseStatusException;
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=PulidoControllerCompletarLoteTest test
```

Esperado: PASS, 7 tests.

- [ ] **Step 5: Comprobar en el cliente que el botón es de la lista propia**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/com/reparaciones/cliente/controller/PulidoTecnicoController.java | sed -n '210,250p'
```

Confirmar que los ids que envía salen de la tabla de pendientes del técnico de la sesión. Si alguna vista permitiera seleccionar pulidos de otro técnico, **parar y consultar**: el cambio rompería al taller.

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: completar pulidos en lote solo acepta pulidos propios"
```

### Task 10: Las estadísticas por puntos devuelven solo las filas propias

**Files:**
- Create: `security/EstadisticasPropias.java`
- Modify: `controller/ReparacionController.java:250-256`
- Test: `src/test/java/com/reparaciones/servidor/security/EstadisticasPropiasTest.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEstadisticasTest.java`

**Interfaces:**
- Produces: `EstadisticasPropias.recortar(UsuarioPrincipal principal, List<PuntoEstadisticaPuntos> todos, String nombrePropio)` → `List<PuntoEstadisticaPuntos>`.
- Consumes: `PuntoEstadisticaPuntos.getNombreTecnico()` (existe), `ReparacionDAO.getNombreTecnicoById(int)` → `String` (existe), `ReparacionDAO.getEstadisticasPuntos(String, LocalDate, LocalDate, ...)` → `List<PuntoEstadisticaPuntos>` (existe, no se toca).

**Contexto:** el cálculo **no se toca** (los agregados de equipo en servidor son SP5, después del corte). Se recorta la respuesta. `PuntoEstadisticaPuntos` identifica al técnico por `nombreTecnico`, no por id, así que el recorte compara nombres y el nombre propio se resuelve con `getNombreTecnicoById`. `GET /api/reparaciones/estadisticas` (la vieja, sin puntos) está retirada en la Task 2, así que solo queda `/estadisticas/puntos`.

- [ ] **Step 1: Comprobar qué pinta el cliente con las filas ajenas**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git grep -n "Promedio del equipo\|promedioEquipo\|esAdmin" hotfix/0.16.3 -- \
  gestion-reparaciones-cliente/src/main/java/com/reparaciones/cliente/controller/EstadisticasController.java | head -20
```

Anotar si la línea "Promedio del equipo" se calcula con **todas** las filas o solo con las del técnico. Si usa todas, después del recorte esa línea pasará a valer lo mismo que la propia: se anota como diferencia esperada y se comprueba en el paso 8. **No se añade el agregado en esta tarea**; si el usuario lo quiere, sale como tarea nueva.

- [ ] **Step 2: Escribir el test del recorte**

`src/test/java/com/reparaciones/servidor/security/EstadisticasPropiasTest.java`:

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Un no-administrador recibe solo sus filas de estadísticas (spec sp7b §4.4 y decisión D6). */
class EstadisticasPropiasTest {

    private static PuntoEstadisticaPuntos punto(String tecnico) {
        return new PuntoEstadisticaPuntos(tecnico, "2026-09", 10, 5, 3, 2, 1, 1, 1, 0, 3, 8, 2);
    }

    private final List<PuntoEstadisticaPuntos> todos =
            List.of(punto("ana"), punto("bruno"), punto("ana"));

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "ana_u", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "bruno_u", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void elAdminLoRecibeTodo() {
        assertEquals(todos, EstadisticasPropias.recortar(admin, todos, null));
    }

    @Test void unTecnicoRecibeSoloLoSuyo() {
        List<PuntoEstadisticaPuntos> r = EstadisticasPropias.recortar(tecnico, todos, "ana");
        assertEquals(2, r.size());
        r.forEach(p -> assertEquals("ana", p.getNombreTecnico()));
    }

    @Test void unSupertecnicoTambienRecibeSoloLoSuyo() {
        List<PuntoEstadisticaPuntos> r = EstadisticasPropias.recortar(supertecnico, todos, "bruno");
        assertEquals(1, r.size());
        assertEquals("bruno", r.get(0).getNombreTecnico());
    }

    @Test void unNoAdminSinTecnicoEs403() {
        UsuarioPrincipal raro = new UsuarioPrincipal(5, "raro", "x", "TECNICO", null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> EstadisticasPropias.recortar(raro, todos, null));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test void siElNombrePropioNoSeResuelveEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> EstadisticasPropias.recortar(tecnico, todos, null));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test void sinFilasPropiasDevuelveListaVacia() {
        assertEquals(List.of(), EstadisticasPropias.recortar(tecnico, todos, "carla"));
    }
}
```

- [ ] **Step 3: Escribir `EstadisticasPropias`**

`src/main/java/com/reparaciones/servidor/security/EstadisticasPropias.java`:

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Recorte de las estadísticas por puntos: el administrador las recibe completas y cualquier otro rol solo sus
 * propias filas (spec sp7b §4.4, decisión D6). No cambia el cálculo: filtra lo que el DAO ya devolvió. Los
 * agregados de equipo calculados en servidor son trabajo del sub-proyecto de Estadísticas.
 */
public final class EstadisticasPropias {

    public static final String MSG_SIN_TECNICO = "Solo puedes consultar tus propias estadísticas";

    private EstadisticasPropias() {}

    /**
     * @param principal    usuario del token
     * @param todos        filas que devolvió el DAO
     * @param nombrePropio nombre del técnico del token, o {@code null} si no se ha podido resolver
     * @throws ResponseStatusException 403 si el rol no es ADMIN y no hay nombre propio con el que filtrar
     */
    public static List<PuntoEstadisticaPuntos> recortar(UsuarioPrincipal principal,
                                                        List<PuntoEstadisticaPuntos> todos,
                                                        String nombrePropio) {
        if ("ADMIN".equals(principal.getRol())) return todos;
        if (principal.getIdTec() == null || nombrePropio == null || nombrePropio.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SIN_TECNICO);
        }
        return todos.stream()
                .filter(p -> nombrePropio.equals(p.getNombreTecnico()))
                .toList();
    }
}
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=EstadisticasPropiasTest test
```

Esperado: PASS, 6 tests.

- [ ] **Step 5: Cambiar el endpoint**

Sustituir el método de `controller/ReparacionController.java:250-256` por:

```java
    @GetMapping("/estadisticas/puntos")
    public List<PuntoEstadisticaPuntos> getEstadisticasPuntos(
            @RequestParam String granularidad,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<PuntoEstadisticaPuntos> todos =
                dao.getEstadisticasPuntos(granularidad, desde, hasta, dificultadDao.getValores());
        // El recorte va aquí y no en el SQL: el cálculo por periodos no se toca (spec sp7b decisión D6).
        String nombrePropio = principal.getIdTec() == null ? null : dao.getNombreTecnicoById(principal.getIdTec());
        return EstadisticasPropias.recortar(principal, todos, nombrePropio);
    }
```

Añadir el import:

```java
import com.reparaciones.servidor.security.EstadisticasPropias;
```

- [ ] **Step 6: Escribir el test del endpoint**

`src/test/java/com/reparaciones/servidor/controller/ReparacionControllerEstadisticasTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un técnico solo ve sus filas de estadísticas; el administrador las ve todas (spec sp7b §4.4). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class ReparacionControllerEstadisticasTest {

    private static final String RUTA =
            "/api/reparaciones/estadisticas/puntos?granularidad=mes&desde=2026-09-01&hasta=2026-09-30";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean DificultadPuntosDAO dificultadDao;
    @MockBean LogDAO logDao;

    private static PuntoEstadisticaPuntos punto(String tecnico) {
        return new PuntoEstadisticaPuntos(tecnico, "2026-09", 10, 5, 3, 2, 1, 1, 1, 0, 3, 8, 2);
    }

    private void datos() {
        when(dao.getEstadisticasPuntos(anyString(), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of(punto("ana"), punto("bruno")));
        when(dao.getNombreTecnicoById(4)).thenReturn("ana");
    }

    @Test void elTecnicoSoloVeLoSuyo() throws Exception {
        datos();
        String token = jwtUtil.generateToken(new UsuarioPrincipal(8, "ana_u", "", "TECNICO", 4));
        mvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].nombreTecnico").value("ana"));
    }

    @Test void elAdminLoVeTodo() throws Exception {
        datos();
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        mvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2));
    }
}
```

- [ ] **Step 7: Ejecutar los dos tests y la suite**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest='EstadisticasPropiasTest,ReparacionControllerEstadisticasTest' test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS, 8 tests en el primer comando; 0 fallos en la suite.

- [ ] **Step 8: Anotar la comprobación pendiente en pantalla**

Escribir en `Apuntes/sp7/sp7b-endpoints-por-entrega.md`, sección de E2, qué hay que mirar en el cierre de la entrega: la pantalla de Estadísticas del **técnico** en el JavaFX y en la web, y si alguna tarjeta o línea de promedio de equipo queda a cero o repite la propia.

- [ ] **Step 9: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: las estadisticas por puntos devuelven a cada tecnico solo sus filas"
```

### Task 11: Editar el modelo de un teléfono exige supertécnico

**Files:**
- Modify: `controller/TelefonoController.java:74-90` (el `POST` de la línea 74)
- Test: `src/test/java/com/reparaciones/servidor/controller/TelefonoControllerInsertarTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: nada.

**Contexto:** `POST /api/telefonos` es a la vez alta del teléfono al asignar y edición del modelo (upsert). En `hotfix/0.16.3` solo lo llama `PendientesSuperTecnicoController` (líneas 2143, 2685, 2705 y 2875), oculto para el ADMIN con `soloLectura`; en la web lo llama `useEditarModeloTelefono` (`modules/taller/api.ts:230-236`), desde pantallas de supertécnico. El propio método ya exige SUPERTECNICO **solo** cuando cambia el cliente.

**Esta tarea es condicional.** Si el paso 1 encuentra un camino de técnico, no se cambia: se anota y se pasa a la Task 12.

- [ ] **Step 1: Comprobar que ningún técnico lo alcanza**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git grep -n "telefonos\"" hotfix/0.16.3 -- gestion-reparaciones-cliente/src/main/java | grep -i "post\|insertar"
git grep -n "insertarTelefono\|guardarTelefono" hotfix/0.16.3 -- gestion-reparaciones-cliente/src/main/java | grep -v "/dao/"
```

Para cada resultado, abrir el controlador en esa línea y comprobar bajo qué condición de rol está el código:

```bash
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/com/reparaciones/cliente/controller/PendientesSuperTecnicoController.java | sed -n '2130,2150p;2680,2712p;2865,2885p'
```

Esperado: los cuatro caminos en vistas de supertécnico. **Si alguno está fuera de una comprobación de rol, parar aquí**, anotarlo en el inventario privado y saltar a la Task 12 sin cambiar nada.

- [ ] **Step 2: Comprobar que la web tampoco lo llama desde una pantalla de técnico**

```bash
cd gestion-reparaciones-web
grep -rn "useEditarModeloTelefono" src/ | head
grep -rn "POST('/api/telefonos'" src/ | head
```

Esperado: solo desde pantallas bajo permiso de edición (asignaciones, historial de pulidos). Si aparece en el formulario del técnico, parar igual.

- [ ] **Step 3: Escribir el test que falla**

`src/test/java/com/reparaciones/servidor/controller/TelefonoControllerInsertarTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dar de alta un teléfono o cambiarle el modelo es del supertécnico (spec sp7b §4.4). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class TelefonoControllerInsertarTest {

    private static final String CUERPO =
            "{\"imei\":\"355400000000111\",\"modelo\":\"13\",\"idCli\":null,\"clienteExplicito\":null}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TelefonoDAO dao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void unTecnicoNoEditaElModelo() throws Exception {
        mvc.perform(post("/api/telefonos").header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
           .andExpect(status().isForbidden());
        verify(dao, never()).insertar(anyString(), anyString());
    }

    @Test void elAdminTampoco() throws Exception {
        mvc.perform(post("/api/telefonos").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
           .andExpect(status().isForbidden());
    }

    @Test void elSupertecnicoSi() throws Exception {
        mvc.perform(post("/api/telefonos").header("Authorization", supertecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
           .andExpect(status().isCreated());
    }
}
```

Ajustar el cuerpo y el estado esperado (`isCreated` u `isOk`) a lo que devuelva el método real.

- [ ] **Step 4: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=TelefonoControllerInsertarTest test
```

Esperado: FAIL, el técnico recibe 201.

- [ ] **Step 5: Añadir la anotación**

En `controller/TelefonoController.java`, sobre el `@PostMapping` de la línea 74:

```java
    // Alta del teléfono al asignar y edición del modelo: los dos flujos son de supertécnico en los dos
    // clientes (spec sp7b §4.4). La comprobación interna del cambio de cliente se mantiene.
    @PreAuthorize("hasRole('SUPERTECNICO')")
```

- [ ] **Step 6: Ejecutar el test y la suite**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=TelefonoControllerInsertarTest test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS, 3 tests; 0 fallos en la suite. **Ojo:** `PulidoController.insertarAsignacionPulido` llama a `telefonoDAO.insertar` directamente (línea 59), no por HTTP, así que no le afecta.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: dar de alta un telefono o cambiarle el modelo exige supertecnico"
```

### Task 12: Fijar con tests lo que ya está bien

**Files:**
- Test: `src/test/java/com/reparaciones/servidor/controller/GuardasVigentesTest.java`
- Modify: `Apuntes/sp7/sp7b-endpoints-por-entrega.md` (privado)

**Interfaces:**
- Consumes: `FiltroTecnico.MSG_SOLO_PROPIOS`, `PropiedadAsignacion.MSG_NO_ES_TUYA` (existen).
- Produces: nada.

**Por qué esta tarea existe:** seis grupos de guardas ya están bien, y la lección del 2026-09-27 es que un cambio bienintencionado puede retirarlas sin que nada se queje. Un test que las fije cuesta poco y convierte "está bien hoy" en "no se puede romper en silencio".

- [ ] **Step 1: Escribir el test**

`src/test/java/com/reparaciones/servidor/controller/GuardasVigentesTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.security.FiltroTecnico;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guardas que ya estaban puestas antes del sp7b y que no deben desaparecer sin que un test se queje.
 * No prueban comportamiento nuevo: son el cinturón de la lección del 2026-09-27.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class GuardasVigentesTest {

    /** Las seis listas del taller: un técnico no puede pedir el trabajo de otro. */
    private static final List<String> LISTAS_DEL_TALLER = List.of(
            "/api/reparaciones/asignaciones",
            "/api/reparaciones/historial",
            "/api/glass/asignaciones",
            "/api/glass/historial",
            "/api/pulidos/asignaciones",
            "/api/pulidos/historial");

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;
    @MockBean BorradorDAO borradorDao;

    private String tecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }

    @Test void unTecnicoNoPidePorOtroTecnicoEnLasSeisListas() throws Exception {
        for (String ruta : LISTAS_DEL_TALLER) {
            mvc.perform(get(ruta + "?tecnico=9").header("Authorization", tecnico()))
               .andExpect(status().isForbidden());
        }
    }

    @Test void unTecnicoSiPidePorSiMismo() throws Exception {
        for (String ruta : LISTAS_DEL_TALLER) {
            mvc.perform(get(ruta + "?tecnico=4").header("Authorization", tecnico()))
               .andExpect(status().isOk());
        }
    }

    @Test void losContadoresTampocoAceptanOtroTecnico() throws Exception {
        mvc.perform(get("/api/reparaciones/pendientes/contadores?tecnico=9").header("Authorization", tecnico()))
           .andExpect(status().isForbidden());
    }

    @Test void editarUnaReparacionHechaSigueSiendoDelSupertecnico() throws Exception {
        String cuerpo = "{\"idComNuevo\":101,\"esReutilizadoNuevo\":false,\"observacionNueva\":null,"
                + "\"nNuevas\":1,\"updatedAt\":\"2026-09-28T07:02:00\"}";
        mvc.perform(put("/api/reparaciones/R20260928_1").header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
           .andExpect(status().isForbidden());
    }

    @Test void marcarPorCerrarSigueSiendoSoloDeLoPropio() throws Exception {
        org.mockito.Mockito.when(reparacionDao.getAsignacionAnyById("A20260928_1"))
                .thenReturn(java.util.Optional.empty());
        mvc.perform(patch("/api/reparaciones/asignaciones/A20260928_1/por-cerrar")
                        .header("Authorization", tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"porCerrar\":true}"))
           .andExpect(status().isNotFound());   // la guarda de dueño va después de comprobar que existe
    }

    @Test void elMensajeDeSoloPropiosNoCambia() {
        org.junit.jupiter.api.Assertions.assertEquals(
                "Solo puedes consultar tus propios trabajos", FiltroTecnico.MSG_SOLO_PROPIOS);
    }
}
```

- [ ] **Step 2: Ejecutar el test**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=GuardasVigentesTest test
```

Esperado: PASS a la primera, 6 tests. **Este test no debe fallar:** si falla, una guarda que se creía puesta no lo está, o una de las tareas anteriores la ha roto. Investigar antes de seguir.

- [ ] **Step 3: Actualizar el inventario privado**

En `Apuntes/sp7/sp7b-endpoints-por-entrega.md`, marcar en la tabla de E2 qué casos estaban **ya cerrados** (los seis grupos de la tabla de cabecera de esta entrega) y qué se ha cambiado de verdad: completar-lote, estadísticas y editar modelo. Anotar que `ya-reparados`, `imei/{imei}/acciones`, `imei/{imei}/incidencia-activa`, `imei/{imei}/asignaciones-activas`, `asignaciones/{idAsignacion}/solicitudes` y `asignaciones/{idRep}` **se quedan accesibles a cualquier sesión válida**, con el motivo: están indexadas por IMEI o por asignación concreta y el formulario de cualquier técnico consulta legítimamente el IMEI en el que trabaja, incluso cuando el teléfono tiene trabajos de varios técnicos; exigir dueño rompería el formulario.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/reparaciones/servidor/controller/GuardasVigentesTest.java
git commit -m "test: fijar las guardas de rol y de dueño que ya estaban puestas"
```

### Task 13: Cierre de la entrega 2

**Files:** ninguno de código.

**Interfaces:** ninguna.

- [ ] **Step 1: Suite completa y contrato**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -25
```

Esperado: 0 fallos. Repetir la comprobación del contrato del paso 2 de la Task 8: sigue siendo 137 rutas y 123 esquemas.

- [ ] **Step 2: Revisión de la rama**

`superpowers:requesting-code-review` sobre el diff de la entrega 2, con foco en: que `completar-lote` no escribe nada si alguna comprobación falla, que el recorte de estadísticas no cambia el cálculo, y que `POST /api/telefonos` no se ha cerrado si el paso 1 de la Task 11 encontró un camino de técnico.

- [ ] **Step 3: Pedir OK, merge y push (uno a uno)**

Igual que en la Task 8, pasos 4 y 5. **No hacer nada sin OK explícito.**

- [ ] **Step 4: Despliegue en PRODUCCIÓN y comprobación**

**Máquina:** producción. **Hora:** cualquiera. Procedimiento P8 (igual que Task 8 paso 6).

Comprobación por API, con las credenciales de `~/.env.e2e` exportadas y nunca impresas:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
set -a; . ~/.env.e2e; set +a
TOKEN=$(curl -s -X POST $E2E_BASE_URL/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"usuario\":\"$E2E_TECNICO_USUARIO\",\"password\":\"$E2E_TECNICO_PASSWORD\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
echo "-- estadisticas del tecnico: deben venir solo sus filas"
curl -s -H "Authorization: Bearer $TOKEN" \
  '$E2E_BASE_URL/api/reparaciones/estadisticas/puntos?granularidad=mes&desde=2026-09-01&hasta=2026-09-30' \
  | python -c "import sys,json;d=json.load(sys.stdin);print(len(d),'filas,',sorted({p['nombreTecnico'] for p in d}))"
echo "-- editar modelo como tecnico: 403"
curl -s -o /dev/null -w '%{http_code}\n' -X POST $E2E_BASE_URL/api/telefonos \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"imei":"355400000000901","modelo":"13","idCli":null,"clienteExplicito":null}'
```

Esperado: un solo nombre de técnico en las estadísticas, y `403` al editar el modelo.

- [ ] **Step 5: Probar el JavaFX real contra producción — las dos pruebas obligatorias**

Con el cliente de `hotfix/0.16.3` apuntado a producción (Task 8 paso 8):

1. **Completar pulidos en lote** con el usuario técnico: seleccionar dos pulidos pendientes propios y pulsar "Completar seleccionados". Deben cerrarse los dos. Comprobar por API que las filas `P…` existen.
2. **Borrar un pulido desde la vista Agrupado** con el supertécnico. Esta prueba existe porque ese borrado reutiliza la ruta de reparaciones para filas de pulido y es exactamente lo que se rompió el 2026-09-27.
3. **Estadísticas del técnico**: abrir la pantalla y mirar si alguna tarjeta o la línea de promedio de equipo queda a cero o repite la propia. Anotar lo que se vea, con captura, en `Apuntes/sp7/`. Si el usuario quiere el promedio de vuelta, sale como tarea nueva.

- [ ] **Step 6: Despliegue en PREPRODUCCIÓN**

**Máquina:** preproducción, la del taller. **Hora: FUERA DE HORARIO.** Igual que la Task 8 paso 9, anotando antes el commit para la vuelta atrás.

- [ ] **Step 7: Documentar y anotar**

Entrada en el registro de sesiones de las dos guías, actualización del inventario privado y línea en `.superpowers/sdd/progress.md`.

---

## ENTREGA 3 — Sesiones, contraseñas y auditoría

Es la única entrega con cambios de esquema. **Orden obligatorio:** Task 14 (comprobar el cliente) → Task 15 (SQL, lo ejecuta el usuario) → Task 16 (código que usa las columnas nuevas) → el resto.

### Task 14: Comprobar que el cliente aguanta una clave de usuario nula

**Files:** ninguno. Solo lectura del cliente y anotación en `Apuntes/sp7/`.

**Interfaces:** ninguna. Su resultado decide el diseño de la Task 15.

**Por qué:** `LogDAO.getFiltered` (`LogDAO:66`) monta `SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, ... FROM Log_Actividad l JOIN Usuario u ...`. Con un `JOIN` interno, una fila cuyo `ID_USU` quede a nulo **desaparecería del listado**, que es lo contrario de lo que se quiere. El plan lo resuelve con `LEFT JOIN` y el nombre guardado en la propia fila, pero antes hay que confirmar qué hace el cliente con ese campo.

- [ ] **Step 1: Ver cómo pinta el registro el cliente**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git grep -ln "Log_Actividad\|LogActividad\|nombreUsuario" hotfix/0.16.3 -- gestion-reparaciones-cliente/src/main/java
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/com/reparaciones/cliente/model/LogActividad.java
```

Anotar: el tipo del campo del nombre, si el cliente lo usa para ordenar o agrupar, y si alguna celda llamaría a un método sobre un nombre nulo.

- [ ] **Step 2: Ver la vista del registro**

```bash
git grep -n "nombreUsuario\|getNombreUsuario" hotfix/0.16.3 -- gestion-reparaciones-cliente/src/main/java | grep -i "log\|actividad"
```

Esperado: celdas de tabla que muestran el texto tal cual. Si alguna hace `.toLowerCase()`, `.trim()` o compara sin comprobar nulo, anotarlo: el diseño de la Task 16 ya evita el nulo con el nombre guardado, pero conviene saberlo.

- [ ] **Step 3: Comprobar la web**

```bash
cd gestion-reparaciones-web && grep -rn "nombreUsuario" src/modules/gestion/logs/ | head
```

- [ ] **Step 4: Escribir la conclusión**

En `Apuntes/sp7/sp7b-endpoints-por-entrega.md`, sección E3: si los dos clientes muestran el nombre como texto, se sigue con el plan tal cual. **Si alguno se rompe con un nombre ausente**, el diseño cambia a "impedir borrar usuarios con historial" (se desactivan en lugar de borrarse) y se consulta al usuario antes de seguir: es un cambio de lo que el administrador puede hacer.

### Task 15: SQL de migración

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-sp7b-auditoria-password.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql` (las definiciones de `Usuario` y `Log_Actividad`)

**Interfaces:**
- Produces: las columnas `Log_Actividad.NOMBRE_USUARIO`, `Usuario.PASSWORD_TEMPORAL` y el `ID_USU` nulable que consumen las Tasks 16, 18 y 19.

**Contexto del esquema actual:** `Usuario` (`crear_bd.sql:92-100`) tiene `ID_USU`, `NOMBRE_USUARIO UNIQUE`, `PASSWORD`, `ROL`, `ID_TEC`. `Log_Actividad` (`:321-...`) tiene `ID_LOG`, `FECHA`, `ID_USU NOT NULL`, `ACCION`, `DETALLE`, `MOTIVO`, con clave ajena a `Usuario`.

- [ ] **Step 1: Leer el nombre exacto de la clave ajena**

El `ALTER` necesita el nombre real de la restricción. Comando **para el usuario**, en producción (hoy sin nadie trabajando):

```bash
ssh prod "docker exec reparaciones-mariadb-1 sh -c 'mariadb -uroot -p\"\$MARIADB_ROOT_PASSWORD\" gestion_reparaciones -e \"SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=\\\"Log_Actividad\\\" AND REFERENCED_TABLE_NAME IS NOT NULL;\"'"
```

Anotar el `CONSTRAINT_NAME` y usarlo en el paso 2. Si no coincide entre producción y preproducción, el script lleva las dos variantes comentadas.

- [ ] **Step 2: Escribir el script**

`gestion-reparaciones-servidor/sql/migracion-sp7b-auditoria-password.sql`:

```sql
-- SP7b: el registro de actividad sobrevive al borrado del usuario, y el administrador puede entregar una
-- contraseña temporal. Aditivo: un servidor anterior sigue funcionando sobre este esquema.
-- Aplicar en la BD viva de cada máquina ANTES de desplegar el servidor de la entrega 3.
USE gestion_reparaciones;

-- 1) El nombre viaja con la línea del registro, para que siga siendo legible cuando el usuario ya no exista.
ALTER TABLE Log_Actividad
    ADD COLUMN NOMBRE_USUARIO VARCHAR(50) NULL AFTER ID_USU;

-- 2) Rellenar el nombre de todo lo que ya hay.
UPDATE Log_Actividad l
  JOIN Usuario u ON l.ID_USU = u.ID_USU
   SET l.NOMBRE_USUARIO = u.NOMBRE_USUARIO;

-- 3) La clave del usuario pasa a admitir nulo y a quedarse a nulo cuando el usuario se borre.
--    Sustituir fk_log_usuario por el CONSTRAINT_NAME real leído en el paso 1.
ALTER TABLE Log_Actividad
    DROP FOREIGN KEY fk_log_usuario;

ALTER TABLE Log_Actividad
    MODIFY COLUMN ID_USU INT NULL;

ALTER TABLE Log_Actividad
    ADD CONSTRAINT fk_log_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;

-- 4) Marca de contraseña entregada por el administrador: obliga a cambiarla al entrar.
ALTER TABLE Usuario
    ADD COLUMN PASSWORD_TEMPORAL TINYINT(1) NOT NULL DEFAULT 0;

-- Comprobación (debe devolver: NOMBRE_USUARIO YES, ID_USU YES, PASSWORD_TEMPORAL NO, y 0 filas sin nombre)
SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Log_Actividad' AND COLUMN_NAME IN ('ID_USU','NOMBRE_USUARIO');
SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Usuario' AND COLUMN_NAME = 'PASSWORD_TEMPORAL';
SELECT COUNT(*) AS lineas_sin_nombre FROM Log_Actividad WHERE NOMBRE_USUARIO IS NULL;
SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'Log_Actividad';
```

- [ ] **Step 3: Actualizar el esquema de referencia**

En `sql/crear_bd.sql`, dejar `Usuario` y `Log_Actividad` como quedan después de la migración, para que una base nueva nazca igual:

```sql
CREATE TABLE Usuario (
    ID_USU            INT          NOT NULL AUTO_INCREMENT,
    NOMBRE_USUARIO    VARCHAR(50)  NOT NULL UNIQUE,
    PASSWORD          VARCHAR(255) NOT NULL,
    ROL               ENUM('ADMIN','SUPERTECNICO','TECNICO') NOT NULL,
    ID_TEC            INT          NULL,
    PASSWORD_TEMPORAL TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (ID_USU),
    CONSTRAINT fk_usuario_tecnico FOREIGN KEY (ID_TEC) REFERENCES Tecnico (ID_TEC)
);
```

Y en `Log_Actividad`: `ID_USU INT NULL`, la columna `NOMBRE_USUARIO VARCHAR(50) NULL` detrás, y la clave ajena con `ON DELETE SET NULL`. Mantener el resto igual.

- [ ] **Step 4: Comprobar que el arranque del servidor sigue funcionando**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -10
```

Esperado: 0 fallos. El test de contexto no toca la base de datos, así que esto solo confirma que nada se ha roto de compilación.

- [ ] **Step 5: Aplicar el SQL — comandos para el usuario**

**Antes de nada, un dump.** Esta es la primera operación de esta clase en producción y no hay ninguna copia previa:

```bash
ssh prod "docker exec reparaciones-mariadb-1 sh -c 'mariadb-dump -uroot -p\"\$MARIADB_ROOT_PASSWORD\" --databases gestion_reparaciones --single-transaction --routines --triggers'" > ~/Documents/Backups-BD/prod-antes-sp7b-$(date +%F).sql
tail -1 ~/Documents/Backups-BD/prod-antes-sp7b-$(date +%F).sql   # debe decir "Dump completed on ..."
```

**Máquina:** producción. **Hora:** cualquiera.

```bash
scp gestion-reparaciones-servidor/sql/migracion-sp7b-auditoria-password.sql prod:/opt/reparaciones/sql/
ssh prod "docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p\"\$MARIADB_ROOT_PASSWORD\" gestion_reparaciones' < /opt/reparaciones/sql/migracion-sp7b-auditoria-password.sql"
```

Esperado: las cuatro consultas de comprobación con `NOMBRE_USUARIO YES`, `ID_USU YES`, `PASSWORD_TEMPORAL NO`, `lineas_sin_nombre 0` y `DELETE_RULE SET NULL`.

**Vuelta atrás:** restaurar el dump, o deshacer a mano:

```sql
ALTER TABLE Log_Actividad DROP FOREIGN KEY fk_log_usuario;
ALTER TABLE Log_Actividad MODIFY COLUMN ID_USU INT NOT NULL;
ALTER TABLE Log_Actividad ADD CONSTRAINT fk_log_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU);
ALTER TABLE Log_Actividad DROP COLUMN NOMBRE_USUARIO;
ALTER TABLE Usuario DROP COLUMN PASSWORD_TEMPORAL;
```

(Deshacer solo funciona si no se ha borrado ningún usuario desde la migración: una fila con `ID_USU` nulo impediría volver a `NOT NULL`.)

**En preproducción, el mismo SQL, FUERA DE HORARIO**, con su propio dump antes.

- [ ] **Step 6: Commit**

```bash
git add sql/migracion-sp7b-auditoria-password.sql sql/crear_bd.sql
git commit -m "feat: esquema para el nombre en el registro de actividad y la contrasena temporal"
```

### Task 16: El registro de actividad sobrevive al borrado del usuario

**Files:**
- Modify: `dao/LogDAO.java` (`insertar`, el `MAPPER` y `getFiltered`)
- Modify: `dao/UsuarioDAO.java:139-144` (`eliminarTecnico`)
- Test: `src/test/java/com/reparaciones/servidor/dao/LogDAONombreTest.java`
- Test: `src/test/java/com/reparaciones/servidor/dao/UsuarioDAOEliminarTest.java`

**Interfaces:**
- Produces: `LogDAO.insertarIntento(String nombreUsuario, String accion, String detalle)`, que consume la Task 18 para anotar fallos de usuarios que no existen.
- Consumes: las columnas de la Task 15.

- [ ] **Step 1: Escribir el test del DAO del registro**

`src/test/java/com/reparaciones/servidor/dao/LogDAONombreTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El nombre del usuario viaja con la línea del registro (spec sp7b §5.6). */
class LogDAONombreTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LogDAO dao = new LogDAO(jdbc);

    @Test void insertarGuardaElNombreResolviendoloDelUsuario() {
        dao.insertar(8, "LOGIN", "");
        verify(jdbc).update(contains("SELECT ?, NOMBRE_USUARIO"), eq(8), eq("LOGIN"), eq(""), eq(null), eq(8));
    }

    @Test void insertarIntentoGuardaElNombreSinUsuario() {
        dao.insertarIntento("alguien", "LOGIN_FALLIDO", "ORIGEN: 10.0.0.1");
        verify(jdbc).update(contains("VALUES (NULL, ?, ?, ?, NULL)"),
                eq("alguien"), eq("LOGIN_FALLIDO"), eq("ORIGEN: 10.0.0.1"));
    }

    @Test void elListadoUsaUnJoinQueConservaLasLineasSinUsuario() {
        dao.getFiltered(null, null, null, null);
        verify(jdbc).query(contains("LEFT JOIN Usuario u"), any(org.springframework.jdbc.core.RowMapper.class));
    }
}
```

El tercer test comprueba la forma del SQL, que es justo lo que importa aquí: un `JOIN` interno haría desaparecer del listado las líneas de un usuario borrado. Si la firma real de `getFiltered` con todo a nulo usa otra sobrecarga de `query`, ajustar el `verify` a la que use.

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=LogDAONombreTest test
```

Esperado: FAIL con `cannot find symbol: insertarIntento` y los dos `verify` sin coincidencia.

- [ ] **Step 3: Cambiar `LogDAO`**

Sustituir los dos `insertar` por:

```java
    public void insertar(int idUsu, String accion, String detalle) {
        insertar(idUsu, accion, detalle, null);
    }

    /**
     * Guarda el nombre del usuario en la propia línea, resuelto en la misma sentencia: así la línea sigue
     * diciendo quién hizo qué cuando el usuario ya no exista (spec sp7b §5.6). Si el usuario no existe, no se
     * inserta nada: no hay actividad que registrar sin actor.
     */
    public void insertar(int idUsu, String accion, String detalle, String motivo) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, NOMBRE_USUARIO, ACCION, DETALLE, MOTIVO) " +
                "SELECT ?, NOMBRE_USUARIO, ?, ?, ? FROM Usuario WHERE ID_USU = ?",
                idUsu, accion, detalle, motivo, idUsu);
    }

    /**
     * Anota algo que no tiene usuario detrás, como un intento de entrar con un nombre que no existe: la clave
     * queda a nulo y el nombre intentado se guarda tal cual (spec sp7b §5.2).
     */
    public void insertarIntento(String nombreUsuario, String accion, String detalle) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, NOMBRE_USUARIO, ACCION, DETALLE, MOTIVO) " +
                "VALUES (NULL, ?, ?, ?, NULL)",
                nombreUsuario, accion, detalle);
    }
```

En `getFiltered`, cambiar el `SELECT` de la línea 66 para que la línea sobreviva sin usuario:

```java
                "SELECT l.ID_LOG, l.FECHA, COALESCE(l.NOMBRE_USUARIO, u.NOMBRE_USUARIO) AS NOMBRE_USUARIO, " +
                "       l.ACCION, l.DETALLE, l.MOTIVO " +
                "FROM Log_Actividad l LEFT JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1 = 1"
```

Y el filtro por usuario de la línea 75, para que siga encontrando al usuario borrado:

```java
            sql.append(" AND COALESCE(l.NOMBRE_USUARIO, u.NOMBRE_USUARIO) = ?");
```

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=LogDAONombreTest test
```

Esperado: PASS, 3 tests.

- [ ] **Step 5: Escribir el test del borrado de usuario**

`src/test/java/com/reparaciones/servidor/dao/UsuarioDAOEliminarTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Borrar un usuario ya no borra su registro de actividad (spec sp7b §5.6, revisa la decisión G8 del SP6). */
class UsuarioDAOEliminarTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioDAO dao = new UsuarioDAO(jdbc, mock(PasswordEncoder.class));

    @Test void borrarUnTecnicoNoBorraSuRegistroDeActividad() {
        dao.eliminarTecnico(4, 8);
        verify(jdbc, never()).update(contains("DELETE FROM Log_Actividad"), anyInt());
        verify(jdbc).update("DELETE FROM Usuario WHERE ID_USU = ?", 8);
        verify(jdbc).update("DELETE FROM Tecnico WHERE ID_TEC = ?", 4);
    }
}
```

Ajustar el constructor de `UsuarioDAO` si recibe más dependencias.

- [ ] **Step 6: Cambiar `eliminarTecnico`**

Sustituir el método de `dao/UsuarioDAO.java:139-144` por:

```java
    /**
     * Borra al usuario y a su técnico, y deja su registro de actividad en su sitio: la clave ajena lo pone a nulo
     * y el nombre ya está guardado en cada línea, así que la auditoría sigue siendo legible (spec sp7b §5.6).
     * Antes se borraba a propósito por calco del comportamiento anterior (decisión G8 del sub-proyecto 6).
     */
    @Transactional
    public void eliminarTecnico(int idTec, int idUsu) {
        jdbc.update("DELETE FROM Usuario WHERE ID_USU = ?", idUsu);
        jdbc.update("DELETE FROM Tecnico WHERE ID_TEC = ?", idTec);
    }
```

Y corregir el comentario de `REFERENCIAS_USUARIO` (línea ~130), que dice que `Log_Actividad` se borra a propósito: ya no.

- [ ] **Step 7: Ejecutar los tests y la suite**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest='LogDAONombreTest,UsuarioDAOEliminarTest' test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS, 4 tests; 0 fallos en la suite. Los tests de `LogControllerTest` y `UsuarioControllerTest` que verifiquen el SQL antiguo hay que actualizarlos: **el comportamiento nuevo es el correcto**, no se ajusta el código al test viejo.

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: el registro de actividad conserva el nombre y sobrevive al borrado del usuario"
```

### Task 17: El usuario se comprueba en cada petición

**Files:**
- Create: `security/EstadoUsuarioService.java`
- Modify: `security/JwtAuthFilter.java`
- Test: `src/test/java/com/reparaciones/servidor/security/EstadoUsuarioServiceTest.java`
- Test: `src/test/java/com/reparaciones/servidor/security/JwtAuthFilterEstadoTest.java`

**Interfaces:**
- Produces: `EstadoUsuarioService.estaOperativo(int idUsu)` → `boolean`, `EstadoUsuarioService.TTL_MS` (`long`), constructor de test `EstadoUsuarioService(JdbcTemplate, Supplier<Long>)`.
- Consumes: `JwtAuthFilter` de la Task 7 (ya devuelve 401 con token incompleto).

**Contexto:** el filtro reconstruye el usuario solo con los datos del token y no consulta la base de datos, así que un usuario desactivado, **borrado** o con el rol cambiado sigue operando hasta veinticuatro horas. La condición que decide si alguien puede entrar ya existe en `UserDetailsServiceImpl:21-27`, pero solo la usa el inicio de sesión.

**Sin invalidación explícita, a propósito:** la caché dura treinta segundos y ese es el techo de retardo aceptado entre desactivar a alguien y que deje de poder operar. Una invalidación por identificador obligaría a mapear el técnico al usuario en cada sitio que desactiva o borra, y a nada más.

- [ ] **Step 1: Escribir el test del servicio**

`src/test/java/com/reparaciones/servidor/security/EstadoUsuarioServiceTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ¿Sigue existiendo y operativo el usuario del token? Con caché corta (spec sp7b §5.1). */
class EstadoUsuarioServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final EstadoUsuarioService servicio = new EstadoUsuarioService(jdbc, ahora::get);

    private void responde(int idUsu, boolean operativo) {
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(idUsu)))
                .thenReturn(operativo ? List.of(1) : List.of());
    }

    @Test void unUsuarioActivoEsOperativo() {
        responde(8, true);
        assertTrue(servicio.estaOperativo(8));
    }

    @Test void unUsuarioDesactivadoOBorradoNoLoEs() {
        responde(8, false);
        assertFalse(servicio.estaOperativo(8));
    }

    @Test void dentroDeLaVentanaNoVuelveAConsultar() {
        responde(8, true);
        servicio.estaOperativo(8);
        ahora.addAndGet(EstadoUsuarioService.TTL_MS - 1);
        servicio.estaOperativo(8);
        verify(jdbc, times(1)).queryForList(anyString(), eq(Integer.class), eq(8));
    }

    @Test void pasadaLaVentanaVuelveAConsultar() {
        responde(8, true);
        servicio.estaOperativo(8);
        ahora.addAndGet(EstadoUsuarioService.TTL_MS);
        servicio.estaOperativo(8);
        verify(jdbc, times(2)).queryForList(anyString(), eq(Integer.class), eq(8));
    }

    @Test void unaDesactivacionSeNotaAlCaducarLaVentana() {
        responde(8, true);
        assertTrue(servicio.estaOperativo(8));
        responde(8, false);
        ahora.addAndGet(EstadoUsuarioService.TTL_MS);
        assertFalse(servicio.estaOperativo(8));
    }

    @Test void laCacheEsPorUsuario() {
        responde(8, true);
        responde(9, false);
        assertTrue(servicio.estaOperativo(8));
        assertFalse(servicio.estaOperativo(9));
    }

    /** Si la base de datos falla, no se expulsa a nadie: se deja pasar y se registra. */
    @Test void siLaConsultaFallaDejaPasar() {
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(8)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("caída"));
        assertTrue(servicio.estaOperativo(8));
    }
}
```

- [ ] **Step 2: Escribir el servicio**

`src/main/java/com/reparaciones/servidor/security/EstadoUsuarioService.java`:

```java
package com.reparaciones.servidor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * ¿El usuario del token sigue existiendo y puede operar? Misma condición que aplica el inicio de sesión
 * (UserDetailsServiceImpl): los administradores no tienen técnico y siempre pasan; el resto necesita su técnico
 * activo. Con esto, desactivar, borrar o cambiar el rol de alguien surte efecto sin esperar a que caduque su
 * token (spec sp7b §5.1).
 *
 * La respuesta se cachea {@link #TTL_MS}: la web sondea cada minuto y cada pestaña abierta multiplica las
 * peticiones, así que consultar en cada una sería un coste por nada. Ese tiempo es también el techo de retardo
 * aceptado entre desactivar a alguien y que deje de poder operar; por eso no hay invalidación explícita.
 *
 * Si la consulta falla, se deja pasar: una caída de la base de datos no debe expulsar al taller entero.
 */
@Component
public class EstadoUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(EstadoUsuarioService.class);

    public static final long TTL_MS = 30_000L;

    private static final String SQL = """
            SELECT 1
            FROM Usuario u
            LEFT JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
            WHERE u.ID_USU = ?
              AND (u.ID_TEC IS NULL OR t.ACTIVO = 1)
            """;

    private record Entrada(boolean operativo, long hasta) {}

    private final Map<Integer, Entrada> cache = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final Supplier<Long> reloj;

    public EstadoUsuarioService(JdbcTemplate jdbc) {
        this(jdbc, System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public EstadoUsuarioService(JdbcTemplate jdbc, Supplier<Long> reloj) {
        this.jdbc  = jdbc;
        this.reloj = reloj;
    }

    public boolean estaOperativo(int idUsu) {
        long ahora = reloj.get();
        Entrada e = cache.get(idUsu);
        if (e != null && ahora < e.hasta()) return e.operativo();
        boolean operativo;
        try {
            operativo = !jdbc.queryForList(SQL, Integer.class, idUsu).isEmpty();
        } catch (DataAccessException ex) {
            log.warn("No se pudo comprobar el estado del usuario {}: {}", idUsu, ex.toString());
            return true;
        }
        cache.put(idUsu, new Entrada(operativo, ahora + TTL_MS));
        return operativo;
    }
}
```

- [ ] **Step 3: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=EstadoUsuarioServiceTest test
```

Esperado: PASS, 7 tests.

- [ ] **Step 4: Escribir el test del filtro**

`src/test/java/com/reparaciones/servidor/security/JwtAuthFilterEstadoTest.java`:

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un usuario que ya no puede operar recibe 401, que es lo que expulsa en los dos clientes (spec sp7b §5.1 y D4). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class JwtAuthFilterEstadoTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean EstadoUsuarioService estado;
    @MockBean ReparacionDAO reparacionDao;
    @MockBean LogDAO logDao;

    private String token() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
    }

    @Test void unUsuarioOperativoPasa() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isOk());
    }

    @Test void unUsuarioDesactivadoOBorradoEs401NoEs403() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isUnauthorized());
    }

    /** El inicio de sesión no pasa por el filtro: un usuario desactivado debe poder recibir su 401 del login. */
    @Test void elLoginNoPasaPorLaComprobacion() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"x\",\"password\":\"y\"}"))
           .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 5: Cambiar el filtro**

En `security/JwtAuthFilter.java`, inyectar el servicio y comprobarlo justo después de validar los datos del token (el bloque que añadió la Task 7):

```java
    private final JwtUtil jwtUtil;
    private final EstadoUsuarioService estadoUsuario;

    public JwtAuthFilter(JwtUtil jwtUtil, EstadoUsuarioService estadoUsuario) {
        this.jwtUtil       = jwtUtil;
        this.estadoUsuario = estadoUsuario;
    }
```

Y, tras la comprobación de los datos del token:

```java
        // El token puede ser válido y el usuario ya no: desactivado, borrado o con otro rol. 401 y no 403,
        // porque es lo único que devuelve al usuario a la pantalla de entrada en los dos clientes (spec sp7b D4).
        if (!estadoUsuario.estaOperativo(idUsu)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        }
```

- [ ] **Step 6: Ejecutar el test y la suite**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=JwtAuthFilterEstadoTest test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS, 3 tests. En la suite, **muchos tests de controlador van a empezar a fallar con 401**, porque su base de datos es un mock y la consulta devolverá vacío. Arreglo: añadir `@MockBean EstadoUsuarioService estado;` con `when(estado.estaOperativo(anyInt())).thenReturn(true)` en un `@BeforeEach`. Para no repetirlo en treinta ficheros, crear una clase base o un `@TestConfiguration` compartido:

```java
package com.reparaciones.servidor;

import com.reparaciones.servidor.security.EstadoUsuarioService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** En los tests de web todos los usuarios están operativos, salvo que el test diga lo contrario. */
@TestConfiguration
public class UsuariosOperativosTestConfig {

    @Bean
    @Primary
    EstadoUsuarioService estadoUsuarioService() {
        EstadoUsuarioService m = mock(EstadoUsuarioService.class);
        when(m.estaOperativo(anyInt())).thenReturn(true);
        return m;
    }
}
```

Y añadir `@Import(UsuariosOperativosTestConfig.class)` a los tests de `@SpringBootTest` con `MockMvc`. `JwtAuthFilterEstadoTest` **no** lo importa: usa su propio `@MockBean`.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: un usuario desactivado o borrado deja de operar sin esperar a que caduque su sesion"
```

### Task 18: Fallos de contraseña registrados y freno por cuenta

**Files:**
- Create: `security/IntentosFallidos.java`
- Modify: `controller/AuthController.java`
- Test: `src/test/java/com/reparaciones/servidor/security/IntentosFallidosTest.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/AuthControllerIntentosTest.java`

**Interfaces:**
- Produces: `IntentosFallidos.comprobar(String usuario)` (lanza 429), `IntentosFallidos.registrarFallo(String usuario)` → `int` (número de fallos consecutivos), `IntentosFallidos.limpiar(String usuario)`, `IntentosFallidos.UMBRAL` (`int`), `IntentosFallidos.ESPERA_MAX_MS` (`long`), `IntentosFallidos.MSG_ESPERA` (`String`), constructor de test con `Supplier<Long>`.
- Consumes: `LogDAO.insertarIntento(String, String, String)` (Task 16).

**Contexto:** hoy el inicio de sesión devuelve 401 sin registrar el fallo (`AuthController:53-55`) y el cambio de contraseña solo escribe en el registro cuando va bien (`:71`). El único freno es el de nginx, por dirección, que el taller comparte y **no se toca**.

- [ ] **Step 1: Escribir el test del contador**

`src/test/java/com/reparaciones/servidor/security/IntentosFallidosTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Freno por cuenta con espera creciente, sin bloquear (spec sp7b §5.3 y decisión D9). */
class IntentosFallidosTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final IntentosFallidos intentos = new IntentosFallidos(ahora::get);

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) intentos.registrarFallo("ana");
    }

    @Test void sinFallosNoHayEspera() {
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void hastaElUmbralNoHayEspera() {
        fallar(IntentosFallidos.UMBRAL - 1);
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void enElUmbralHayEspera() {
        fallar(IntentosFallidos.UMBRAL);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> intentos.comprobar("ana"));
        assertEquals(429, ex.getStatusCode().value());
        assertEquals(IntentosFallidos.MSG_ESPERA, ex.getReason());
    }

    @Test void laEsperaCreceConCadaFallo() {
        fallar(IntentosFallidos.UMBRAL);
        long primera = intentos.esperaMs("ana");
        intentos.registrarFallo("ana");
        assertEquals(true, intentos.esperaMs("ana") > primera);
    }

    @Test void laEsperaTieneTope() {
        fallar(IntentosFallidos.UMBRAL + 50);
        assertEquals(IntentosFallidos.ESPERA_MAX_MS, intentos.esperaMs("ana"));
    }

    @Test void pasadaLaEsperaVuelveAPasar() {
        fallar(IntentosFallidos.UMBRAL);
        ahora.addAndGet(intentos.esperaMs("ana"));
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void unaEntradaCorrectaLimpiaElContador() {
        fallar(IntentosFallidos.UMBRAL);
        intentos.limpiar("ana");
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void elFrenoEsPorCuentaYNoAfectaAOtras() {
        fallar(IntentosFallidos.UMBRAL);
        assertDoesNotThrow(() -> intentos.comprobar("bruno"));
    }

    @Test void registrarFalloDevuelveElNumeroDeFallosSeguidos() {
        assertEquals(1, intentos.registrarFallo("ana"));
        assertEquals(2, intentos.registrarFallo("ana"));
    }

    @Test void elNombreNuloNoRompe() {
        assertDoesNotThrow(() -> intentos.comprobar(null));
        assertEquals(0, intentos.registrarFallo(null));
    }
}
```

- [ ] **Step 2: Escribir `IntentosFallidos`**

`src/main/java/com/reparaciones/servidor/security/IntentosFallidos.java`:

```java
package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Freno por cuenta de los fallos de contraseña: a partir del quinto fallo seguido, cada intento espera un poco
 * más, hasta un minuto. No bloquea la cuenta a propósito (spec sp7b §5.3, decisión D9): bloquear permitiría
 * dejar fuera a un compañero probando su contraseña, y en un taller con equipos compartidos obligaría al
 * administrador a desbloquear. Un acierto limpia el contador.
 *
 * Es complementario del límite por dirección del proxy, que el taller comparte y no se toca. En memoria y por
 * instancia, como el registro de reintentos: un reinicio pone los contadores a cero, lo que solo relaja el freno.
 */
@Component
public class IntentosFallidos {

    /** Fallos seguidos a partir de los cuales empieza la espera. */
    public static final int UMBRAL = 5;
    /** Tope de la espera. */
    public static final long ESPERA_MAX_MS = 60_000L;
    /** Cuánto crece la espera por cada fallo más allá del umbral. */
    public static final long PASO_MS = 5_000L;
    public static final String MSG_ESPERA = "Demasiados intentos fallidos. Espera unos segundos y vuelve a intentarlo.";

    private record Estado(int fallos, long ultimo) {}

    private final Map<String, Estado> porCuenta = new ConcurrentHashMap<>();
    private final Supplier<Long> reloj;

    public IntentosFallidos() {
        this(System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public IntentosFallidos(Supplier<Long> reloj) {
        this.reloj = reloj;
    }

    /** @throws ResponseStatusException 429 si esta cuenta todavía está en su espera. */
    public void comprobar(String usuario) {
        if (usuario == null) return;
        Estado e = porCuenta.get(usuario);
        if (e == null || e.fallos() < UMBRAL) return;
        if (reloj.get() - e.ultimo() < esperaDe(e.fallos())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_ESPERA);
        }
    }

    /** @return fallos seguidos de esta cuenta después de contar este. */
    public int registrarFallo(String usuario) {
        if (usuario == null) return 0;
        Estado nuevo = porCuenta.compute(usuario, (k, e) ->
                new Estado(e == null ? 1 : e.fallos() + 1, reloj.get()));
        return nuevo.fallos();
    }

    /** Tras una entrada correcta. */
    public void limpiar(String usuario) {
        if (usuario != null) porCuenta.remove(usuario);
    }

    /** Espera vigente de esta cuenta, en milisegundos (0 si no tiene). */
    public long esperaMs(String usuario) {
        if (usuario == null) return 0L;
        Estado e = porCuenta.get(usuario);
        return e == null || e.fallos() < UMBRAL ? 0L : esperaDe(e.fallos());
    }

    private static long esperaDe(int fallos) {
        long espera = (long) (fallos - UMBRAL + 1) * PASO_MS;
        return Math.min(espera, ESPERA_MAX_MS);
    }
}
```

- [ ] **Step 3: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=IntentosFallidosTest test
```

Esperado: PASS, 10 tests.

- [ ] **Step 4: Escribir el test del controlador**

`src/test/java/com/reparaciones/servidor/controller/AuthControllerIntentosTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.IntentosFallidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los fallos de contraseña dejan constancia y frenan la cuenta (spec sp7b §5.2 y §5.3). */
class AuthControllerIntentosTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final IntentosFallidos intentos = new IntentosFallidos();
    private final AuthController ctl = new AuthController(authManager, jwtUtil, logDao, usuarioDao, intentos);

    private static AuthController.LoginRequest login(String usuario) {
        return new AuthController.LoginRequest(usuario, "mala");
    }

    @Test void unLoginFallidoSeRegistraConElNombreIntentado() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        var resp = ctl.login(login("ana"), null);
        assertEquals(401, resp.getStatusCode().value());
        verify(logDao).insertarIntento(eq("ana"), eq("LOGIN_FALLIDO"), contains("INTENTOS: 1"));
    }

    @Test void pasadoElUmbralLaCuentaEspera() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        for (int i = 0; i < IntentosFallidos.UMBRAL; i++) ctl.login(login("ana"), null);
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> ctl.login(login("ana"), null));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test void otraCuentaNoSeVeAfectada() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        for (int i = 0; i < IntentosFallidos.UMBRAL; i++) ctl.login(login("ana"), null);
        var resp = ctl.login(login("bruno"), null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test void unaEntradaCorrectaLimpiaElContador() {
        var principal = new UsuarioPrincipal(8, "ana", "", "TECNICO", 4);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        for (int i = 0; i < IntentosFallidos.UMBRAL - 1; i++) ctl.login(login("ana"), null);
        when(authManager.authenticate(any())).thenReturn(auth);
        when(jwtUtil.generateToken(any())).thenReturn("un-token");
        ctl.login(login("ana"), null);
        assertEquals(0L, intentos.esperaMs("ana"));
    }
}
```

El segundo parámetro de `login` es la petición HTTP, que el controlador usa para la dirección de origen; en el test va a `null` y el controlador lo tolera.

- [ ] **Step 5: Cambiar `AuthController`**

Inyectar `IntentosFallidos` en el constructor y sustituir los dos métodos por:

```java
    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            content = @io.swagger.v3.oas.annotations.media.Content(
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req,
                                               jakarta.servlet.http.HttpServletRequest http) {
        intentos.comprobar(req.usuario());
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.usuario(), req.password()));
            var principal = (UsuarioPrincipal) auth.getPrincipal();
            String token  = jwtUtil.generateToken(principal);

            intentos.limpiar(req.usuario());
            logDao.insertar(principal.getIdUsu(), "LOGIN", "");

            return ResponseEntity.ok(new LoginResponse(
                    principal.getIdUsu(), principal.getUsername(), principal.getRol(),
                    principal.getIdTec(), token));
        } catch (BadCredentialsException e) {
            // Deja constancia del intento con el nombre tal cual se escribió, aunque no exista ese usuario
            // (spec sp7b §5.2). Antes no se registraba ningún fallo.
            int fallos = intentos.registrarFallo(req.usuario());
            logDao.insertarIntento(req.usuario(), "LOGIN_FALLIDO",
                    "INTENTOS: " + fallos + origenDe(http));
            return ResponseEntity.status(401).build();
        }
    }

    @PatchMapping("/cambiar-password")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<?> cambiarPassword(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody CambiarPasswordRequest req) {
        intentos.comprobar(principal.getUsername());
        ValidacionUsuarios.validarCambioPassword(req.passwordActual(), req.passwordNueva());
        try {
            usuarioDao.cambiarPassword(principal.getIdUsu(), req.passwordActual(), req.passwordNueva());
            intentos.limpiar(principal.getUsername());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            int fallos = intentos.registrarFallo(principal.getUsername());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD_FALLIDO", "INTENTOS: " + fallos);
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        }
    }

    /** La dirección que ve la aplicación, para el registro; vacío si no hay petición (tests). */
    private static String origenDe(jakarta.servlet.http.HttpServletRequest http) {
        if (http == null) return "";
        String reenviada = http.getHeader("X-Real-IP");
        if (reenviada == null || reenviada.isBlank()) reenviada = http.getRemoteAddr();
        return reenviada == null || reenviada.isBlank() ? "" : ", ORIGEN: " + reenviada;
    }
```

Y en los records del final, dejar `LoginRequest` y `CambiarPasswordRequest` como están.

- [ ] **Step 6: Ejecutar los tests y la suite**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest='IntentosFallidosTest,AuthControllerIntentosTest' test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS, 14 tests. `AuthControllerTest` y `AuthControllerCambiarPasswordTest` existentes hay que actualizarlos al constructor nuevo y, si alguno falla la contraseña cinco veces seguidas con el mismo usuario, separarlos en usuarios distintos.

- [ ] **Step 7: Comprobar que el contrato solo gana respuestas**

```bash
cd gestion-reparaciones-servidor && mvn -q -DskipTests spring-boot:run &
sleep 40
curl -s http://localhost:8080/v3/api-docs > /tmp/openapi-e3.json
python -c "import json;d=json.load(open('/tmp/openapi-e3.json'));print(len(d['paths']),'rutas',len(d['components']['schemas']),'esquemas')"
kill %1
```

Esperado: siguen siendo 137 rutas y 123 esquemas; lo único que cambia son las respuestas documentadas (`429`), que no son un tipo nuevo. Si el número sube, la Task 19 lo explicará (añade una ruta).

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: los fallos de contrasena quedan registrados y frenan la cuenta con espera creciente"
```

### Task 19: El administrador entrega una contraseña temporal

**Files:**
- Modify: `dao/UsuarioDAO.java` (nuevos `fijarPasswordTemporal` y `limpiarPasswordTemporal`, y `cambiarPassword`)
- Modify: `controller/UsuarioController.java` (nueva ruta)
- Modify: `controller/AuthController.java` (`LoginResponse` informa de la marca)
- Modify: `model/LoginResponse.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/UsuarioControllerResetPasswordTest.java`

**Interfaces:**
- Produces: `POST /api/usuarios/{idUsu}/password-temporal` (solo ADMIN) que devuelve `{"value":"<contraseña>"}`; `UsuarioDAO.fijarPasswordTemporal(int idUsu, String password)`; `LoginResponse.passwordTemporal()` (`boolean`), que consume la Task 24 en la web.
- Consumes: la columna `Usuario.PASSWORD_TEMPORAL` (Task 15).

**Contexto:** hoy no existe ninguna forma de que un administrador restablezca la contraseña de otro: solo borrar y recrear al usuario, o un `UPDATE` a mano. `cambiarPassword` exige la actual y solo actúa sobre el propio usuario.

- [ ] **Step 1: Escribir el test**

`src/test/java/com/reparaciones/servidor/controller/UsuarioControllerResetPasswordTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo el administrador entrega una contraseña temporal, y queda registrado (spec sp7b §5.4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class UsuarioControllerResetPasswordTest {

    private static final String RUTA = "/api/usuarios/8/password-temporal";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean UsuarioDAO dao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(9, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    @Test void unTecnicoNoRestableceContrasenasAjenas() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verify(dao, never()).fijarPasswordTemporal(anyInt(), anyString());
    }

    @Test void unSupertecnicoTampoco() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", supertecnico())).andExpect(status().isForbidden());
    }

    @Test void elAdminRecibeUnaContrasenaYQuedaRegistrado() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", admin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.value").isString());
        verify(dao).fijarPasswordTemporal(eq(8), anyString());
        verify(logDao).insertar(eq(1), eq("RESTABLECER_PASSWORD"), contains("ID_USU: 8"));
    }

    /** La contraseña entregada no se repite entre llamadas. */
    @Test void cadaLlamadaEntregaUnaDistinta() throws Exception {
        String una = mvc.perform(post(RUTA).header("Authorization", admin()))
                .andReturn().getResponse().getContentAsString();
        String otra = mvc.perform(post(RUTA).header("Authorization", admin()))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(una, otra);
    }
}
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=UsuarioControllerResetPasswordTest test
```

Esperado: FAIL con `cannot find symbol: fijarPasswordTemporal` y 404 en la ruta.

- [ ] **Step 3: Añadir los métodos al DAO**

En `dao/UsuarioDAO.java`:

```java
    /**
     * Deja una contraseña entregada por el administrador y marca al usuario para que tenga que cambiarla al
     * entrar (spec sp7b §5.4). El administrador nunca fija una contraseña definitiva ajena.
     */
    public void fijarPasswordTemporal(int idUsu, String password) {
        jdbc.update("UPDATE Usuario SET PASSWORD = ?, PASSWORD_TEMPORAL = 1 WHERE ID_USU = ?",
                passwordEncoder.encode(password), idUsu);
    }

    public boolean tienePasswordTemporal(int idUsu) {
        Boolean b = jdbc.queryForObject(
                "SELECT PASSWORD_TEMPORAL FROM Usuario WHERE ID_USU = ?", Boolean.class, idUsu);
        return Boolean.TRUE.equals(b);
    }
```

Y en `cambiarPassword`, retirar la marca al terminar bien (el usuario ya ha puesto la suya):

```java
        String hashNuevo = passwordEncoder.encode(passwordNueva);
        jdbc.update("UPDATE Usuario SET PASSWORD = ?, PASSWORD_TEMPORAL = 0 WHERE ID_USU = ?", hashNuevo, idUsu);
```

- [ ] **Step 4: Añadir la ruta**

En `controller/UsuarioController.java` (que ya exige ADMIN a nivel de clase o de método; comprobarlo con `sed -n '1,40p'` y añadir la anotación al método si la clase no la lleva):

```java
    /**
     * Entrega una contraseña temporal para otro usuario. La genera el servidor, se devuelve una sola vez para
     * que el administrador la comunique, y el usuario está obligado a cambiarla al entrar (spec sp7b §5.4).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{idUsu}/password-temporal")
    public ValorTexto entregarPasswordTemporal(@PathVariable int idUsu,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        String password = PasswordTemporal.generar();
        dao.fijarPasswordTemporal(idUsu, password);
        logDao.insertar(principal.getIdUsu(), "RESTABLECER_PASSWORD", "ID_USU: " + idUsu);
        return new ValorTexto(password);
    }
```

Usar el mismo tipo de respuesta `{value}` que ya emplean otros endpoints del proyecto; si la clase se llama distinto, adaptarlo (`grep -rn "class ValorTexto\|record ValorTexto" src/main/java`).

- [ ] **Step 5: Escribir el generador**

`src/main/java/com/reparaciones/servidor/security/PasswordTemporal.java`:

```java
package com.reparaciones.servidor.security;

import java.security.SecureRandom;

/**
 * Contraseña de un solo uso que el administrador comunica al usuario. Se leen y se teclean en voz alta, así que
 * no lleva caracteres que se confundan (0/O, 1/l/I) ni símbolos.
 */
public final class PasswordTemporal {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LONGITUD = 10;
    private static final SecureRandom AZAR = new SecureRandom();

    private PasswordTemporal() {}

    public static String generar() {
        StringBuilder sb = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            sb.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
```

Diez caracteres de este alfabeto superan el mínimo de seis que exige el cambio de contraseña.

- [ ] **Step 6: Informar de la marca al entrar**

En `model/LoginResponse.java`, añadir el campo `boolean passwordTemporal` al final del record (o de la clase), y en `AuthController.login`, al construir la respuesta:

```java
            return ResponseEntity.ok(new LoginResponse(
                    principal.getIdUsu(), principal.getUsername(), principal.getRol(),
                    principal.getIdTec(), token, usuarioDao.tienePasswordTemporal(principal.getIdUsu())));
```

- [ ] **Step 7: Ejecutar el test, la suite y el contrato**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=UsuarioControllerResetPasswordTest test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS, 4 tests; 0 fallos. El contrato **sí cambia** aquí: una ruta más (138) y un campo más en `LoginResponse`. Regenerar los tipos de la web al llegar a la Task 24:

```bash
cd gestion-reparaciones-web && npm run api:types
git diff --stat src/shared/api/schema.d.ts api/openapi.json
```

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: el administrador entrega una contrasena temporal que hay que cambiar al entrar"
```

### Task 20: Registrar las escrituras que hoy no dejan rastro

**Files:**
- Modify: `controller/ReparacionComponenteController.java` (los dos métodos sin registro)
- Test: `src/test/java/com/reparaciones/servidor/controller/RegistroEscriturasTest.java`

**Interfaces:**
- Consumes: `LogDAO.insertar(int, String, String)` (Task 16).
- Produces: nada.

**Contexto:** el borrado de teléfono ya quedó registrado en la Task 5. Quedan las operaciones sobre piezas de una reparación y las escrituras que el inventario marcó como "con guarda pero sin registro". **Las rutas de `reparacion-componentes` están retiradas en la Task 2**, así que aquí solo entran las que siguen vivas.

- [ ] **Step 1: Listar las escrituras vivas sin registro**

```bash
cd gestion-reparaciones-servidor
for f in src/main/java/com/reparaciones/servidor/controller/*Controller.java; do
  python - "$f" <<'PY'
import re,sys
ruta = sys.argv[1]
texto = open(ruta, encoding='utf-8').read()
bloques = re.split(r'\n(?=    @)', texto)
for b in bloques:
    if re.search(r'@(Post|Put|Patch|Delete)Mapping', b) and 'logDao' not in b:
        m = re.search(r'@(Post|Put|Patch|Delete)Mapping\(?"?([^")]*)', b)
        if m: print(ruta.split('/')[-1], m.group(1), m.group(2))
PY
done
```

Esperado: una lista corta. Descartar las que estén en `RutasRetiradas.LISTA` (ya responden 403) y las que solo leen. Con lo que quede, seguir.

- [ ] **Step 2: Escribir el test**

`src/test/java/com/reparaciones/servidor/controller/RegistroEscriturasTest.java`, un test por escritura que quede, con esta forma (aquí el ejemplo del borrado de teléfono, que ya está hecho, como patrón a repetir para cada una):

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/** Toda escritura viva deja una línea en el registro de actividad (spec sp7b §5.7). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RegistroEscriturasTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TelefonoDAO telefonoDao;
    @MockBean LogDAO logDao;

    private String supertecnico() {
        return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3));
    }

    @Test void borrarUnTelefonoDejaLinea() throws Exception {
        mvc.perform(delete("/api/telefonos/355400000000111").header("Authorization", supertecnico()));
        verify(logDao).insertar(eq(7), eq("ELIMINAR_TELEFONO"), contains("355400000000111"));
    }
}
```

Añadir un test igual por cada escritura de la lista del paso 1, con su ruta, su rol y el nombre de acción que se elija.

- [ ] **Step 3: Añadir el registro a cada escritura**

Patrón, dentro de cada método, después de la escritura y con los datos que identifiquen la fila:

```java
        logDao.insertar(principal.getIdUsu(), "<ACCION>", "<CLAVE>: " + <valor>);
```

Los nombres de acción siguen el estilo del proyecto: verbo y entidad en mayúsculas separados por guion bajo, como `ELIMINAR_TELEFONO`.

- [ ] **Step 4: Ejecutar el test y la suite**

```bash
cd gestion-reparaciones-servidor && mvn -q -Dtest=RegistroEscriturasTest test
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: PASS; 0 fallos en la suite.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: las escrituras que faltaban dejan linea en el registro de actividad"
```

### Task 21: Cierre de la entrega 3

**Files:** ninguno de código.

- [ ] **Step 1: Suite, contrato y revisión**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -25
```

Esperado: 0 fallos. El contrato pasa de 137 a **138 rutas** y `LoginResponse` gana un campo: es el único cambio de contrato de todo el sub-proyecto, y lo consume la Task 24.

Revisión con `superpowers:requesting-code-review`, con foco en: que una caída de la base de datos no expulse a nadie, que el inicio de sesión no pase por la comprobación de estado, que el listado del registro conserve las líneas de un usuario borrado, y que la contraseña temporal no se registre en ningún log.

- [ ] **Step 2: Pedir OK, merge y push (uno a uno)**

- [ ] **Step 3: Aplicar el SQL y desplegar en PRODUCCIÓN**

**Orden obligatorio:** dump → SQL (Task 15 paso 5) → despliegue del servidor. **Máquina:** producción. **Hora:** cualquiera.

- [ ] **Step 4: Comprobar en producción**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
set -a; . ~/.env.e2e; set +a
echo "-- un nombre que no existe: 401 y queda registrado"
curl -s -o /dev/null -w '%{http_code}\n' -X POST $E2E_BASE_URL/api/auth/login \
  -H 'Content-Type: application/json' -d '{"usuario":"no-existe-sp7b","password":"x"}'
echo "-- el registro lo recoge (como ADMIN)"
TOKEN=$(curl -s -X POST $E2E_BASE_URL/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"usuario\":\"$E2E_ADMIN_USUARIO\",\"password\":\"$E2E_ADMIN_PASSWORD\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -H "Authorization: Bearer $TOKEN" '$E2E_BASE_URL/api/logs?limite=20' \
  | python -c "import sys,json;[print(l['accion'],l['nombreUsuario'],l['detalle']) for l in json.load(sys.stdin) if 'FALLIDO' in l['accion']]"
```

Esperado: `401`, y una línea `LOGIN_FALLIDO no-existe-sp7b INTENTOS: 1` en el registro.

Después, la prueba que da sentido a la entrega: **desactivar al técnico de prueba desde la web con el administrador**, y comprobar que en menos de un minuto su sesión deja de funcionar (el JavaFX muestra su aviso de sesión caducada; la web lleva al login). **Volver a activarlo al terminar.**

- [ ] **Step 5: Probar el JavaFX real contra producción**

Con el cliente de `hotfix/0.16.3` apuntado a producción: entrar con el técnico, trabajar un minuto, y que un administrador lo desactive desde la web. Esperado: en menos de un minuto, el aviso "Tu sesión ha caducado. Vuelve a iniciar sesión." y vuelta a la pantalla de entrada. Comprobar también que el **registro de actividad del JavaFX** sigue listando líneas y que el nombre se ve (la vista es de administrador).

- [ ] **Step 6: Aplicar el SQL y desplegar en PREPRODUCCIÓN**

**Máquina:** preproducción, la del taller. **Hora: FUERA DE HORARIO.** Dump antes, SQL, despliegue, y comprobar con el JavaFX que el registro de actividad se ve bien.

- [ ] **Step 7: Documentar y anotar**

Entradas en el registro de sesiones de las dos guías, con el SQL aplicado; actualización del inventario privado; línea en el ledger.

---

## WEB — Inactividad y aviso de versión (misma 0.9.0)

Se desarrolla en paralelo a las entregas del servidor. Solo la Task 24 depende del servidor (del campo que añade la Task 19).

### Task 22: Marca de actividad de la persona

**Files:**
- Create: `gestion-reparaciones-web/src/shared/session/actividad.ts`
- Create: `gestion-reparaciones-web/src/shared/session/actividad.test.ts`

**Interfaces:**
- Produces: `CLAVE_ACTIVIDAD` (`'fsgr.actividad'`), `INACTIVIDAD_MS` (`number`), `AVISO_MS` (`number`), `escribirActividad(ahora?: number): void`, `leerActividad(): number | null`, `arrancarMarcaDeActividad(): () => void`. Los consume la Task 23.
- Consumes: `leerSesion` de `./storage`.

**Por qué una marca nueva y no `fsgr.latido`:** el latido de la 0.8.5 se escribe cada treinta segundos mientras la pestaña vive, y también en `visibilitychange`, `focus`, `pageshow`, `pagehide` y `resume` (`latido.ts:29-34`). Mide que la pestaña existe, no que haya alguien delante. Si el cierre por inactividad se colgara de él, una pestaña olvidada en un equipo del taller no caducaría nunca.

**Qué cuenta como actividad:** `pointerdown`, `keydown`, `wheel` y `touchstart` dentro del documento. **Qué no:** el refresco automático de las tablas (`useIntervaloRefresco`, 60 s / 5 s), el refresco al volver a la ventana (`refetchOnWindowFocus`), y el propio `focus` de la ventana.

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-web
git checkout -b feature/sp7b-web-sesion
git log -1 --oneline   # debe ser a6ba0c6
```

- [ ] **Step 2: Escribir el test que falla**

`src/shared/session/actividad.test.ts`:

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AVISO_MS,
  CLAVE_ACTIVIDAD,
  INACTIVIDAD_MS,
  arrancarMarcaDeActividad,
  escribirActividad,
  leerActividad,
} from './actividad'
import { CLAVE_SESION } from './storage'

const SESION = JSON.stringify({ idUsu: 8, nombreUsuario: 'ana', rol: 'TECNICO', idTec: 4, token: 't' })

describe('marca de actividad', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('el tope de inactividad son dos horas y el aviso un minuto antes', () => {
    expect(INACTIVIDAD_MS).toBe(2 * 60 * 60 * 1000)
    expect(AVISO_MS).toBe(60_000)
  })

  it('escribe y lee la hora', () => {
    escribirActividad(1_700_000_000_000)
    expect(localStorage.getItem(CLAVE_ACTIVIDAD)).toBe('1700000000000')
    expect(leerActividad()).toBe(1_700_000_000_000)
  })

  it('sin marca devuelve null', () => {
    expect(leerActividad()).toBeNull()
  })

  it('una marca que no es un número devuelve null', () => {
    localStorage.setItem(CLAVE_ACTIVIDAD, 'ayer')
    expect(leerActividad()).toBeNull()
  })

  it('el ratón y el teclado escriben la marca, solo si hay sesión', () => {
    localStorage.setItem(CLAVE_SESION, SESION)
    const detener = arrancarMarcaDeActividad()
    vi.setSystemTime(1_000)
    document.dispatchEvent(new Event('pointerdown'))
    expect(leerActividad()).toBe(1_000)
    vi.setSystemTime(2_000)
    document.dispatchEvent(new Event('keydown'))
    expect(leerActividad()).toBe(2_000)
    detener()
  })

  it('sin sesión no escribe nada', () => {
    const detener = arrancarMarcaDeActividad()
    document.dispatchEvent(new Event('pointerdown'))
    expect(leerActividad()).toBeNull()
    detener()
  })

  it('el foco de la ventana NO cuenta como actividad', () => {
    localStorage.setItem(CLAVE_SESION, SESION)
    const detener = arrancarMarcaDeActividad()
    vi.setSystemTime(5_000)
    window.dispatchEvent(new Event('focus'))
    expect(leerActividad()).toBeNull()
    detener()
  })

  it('al detener deja de escribir', () => {
    localStorage.setItem(CLAVE_SESION, SESION)
    const detener = arrancarMarcaDeActividad()
    vi.setSystemTime(1_000)
    document.dispatchEvent(new Event('pointerdown'))
    detener()
    vi.setSystemTime(9_000)
    document.dispatchEvent(new Event('pointerdown'))
    expect(leerActividad()).toBe(1_000)
  })

  it('arrancarla dos veces no duplica la escucha', () => {
    localStorage.setItem(CLAVE_SESION, SESION)
    const primera = arrancarMarcaDeActividad()
    const segunda = arrancarMarcaDeActividad()
    vi.setSystemTime(3_000)
    document.dispatchEvent(new Event('pointerdown'))
    expect(leerActividad()).toBe(3_000)
    segunda()
    vi.setSystemTime(7_000)
    document.dispatchEvent(new Event('pointerdown'))
    expect(leerActividad()).toBe(3_000)
    primera()
  })

  it('si el almacenamiento falla no lanza', () => {
    const original = localStorage.setItem
    localStorage.setItem = () => {
      throw new Error('bloqueado')
    }
    expect(() => escribirActividad(1)).not.toThrow()
    localStorage.setItem = original
  })
})
```

- [ ] **Step 3: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-web && npx vitest run src/shared/session/actividad.test.ts
```

Esperado: FAIL, no existe el módulo.

- [ ] **Step 4: Escribir el módulo**

`src/shared/session/actividad.ts`:

```ts
import { leerSesion } from './storage'

/**
 * Hora (ms desde epoch) de la última señal de que hay ALGUIEN usando el ERP, en cualquier pestaña del navegador.
 * Es distinta de `fsgr.latido`, que dice que una pestaña sigue viva aunque no haya nadie delante: colgar el
 * cierre por inactividad del latido dejaría una pestaña olvidada abierta para siempre.
 */
export const CLAVE_ACTIVIDAD = 'fsgr.actividad'

/** Tope de inactividad antes de cerrar la sesión: dos horas. */
export const INACTIVIDAD_MS = 2 * 60 * 60 * 1000
/** Cuánto antes del cierre se avisa, con botón para seguir. */
export const AVISO_MS = 60_000

/**
 * Lo que cuenta como actividad de una persona. Deliberadamente NO están el `focus` de la ventana ni
 * `visibilitychange`: en un equipo compartido, cambiar de aplicación y volver no es trabajar, y el refresco
 * automático de las tablas es independiente de todo esto.
 */
const EVENTOS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const

let detenerActual: (() => void) | null = null

export function escribirActividad(ahora: number = Date.now()) {
  try {
    localStorage.setItem(CLAVE_ACTIVIDAD, String(ahora))
  } catch {
    // Almacenamiento no disponible: sin marca, la vigilancia trata la sesión como recién activa.
  }
}

export function leerActividad(): number | null {
  try {
    const raw = localStorage.getItem(CLAVE_ACTIVIDAD)
    if (raw === null || raw.trim() === '') return null
    const n = Number(raw)
    return Number.isFinite(n) ? n : null
  } catch {
    return null
  }
}

/**
 * Escucha el ratón y el teclado y anota la hora mientras haya sesión. La marca vive en `localStorage`, así que la
 * actividad de cualquier pestaña cuenta para todas, que es lo que corresponde a una sesión compartida. Una sola
 * vez por documento: arrancarla de nuevo sustituye a la anterior. Devuelve cómo detenerla.
 */
export function arrancarMarcaDeActividad(): () => void {
  detenerActual?.()
  const anotar = () => {
    if (leerSesion() !== null) escribirActividad()
  }
  for (const evento of EVENTOS) {
    document.addEventListener(evento, anotar, { passive: true })
  }
  const detener = () => {
    for (const evento of EVENTOS) document.removeEventListener(evento, anotar)
    if (detenerActual === detener) detenerActual = null
  }
  detenerActual = detener
  return detener
}
```

- [ ] **Step 5: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-web && npx vitest run src/shared/session/actividad.test.ts
```

Esperado: PASS, 10 tests.

- [ ] **Step 6: Commit**

```bash
git add src/shared/session/actividad.ts src/shared/session/actividad.test.ts
git commit -m "feat: marca de actividad de la persona, separada de la marca de pestaña viva"
```

### Task 23: Cierre por inactividad con aviso de un minuto

**Files:**
- Create: `gestion-reparaciones-web/src/shared/session/VigilanciaInactividad.tsx`
- Create: `gestion-reparaciones-web/src/shared/session/VigilanciaInactividad.test.tsx`
- Modify: `gestion-reparaciones-web/src/app/shell/AppLayout.tsx`
- Modify: `gestion-reparaciones-web/src/shared/session/arranque.ts`

**Interfaces:**
- Consumes: `INACTIVIDAD_MS`, `AVISO_MS`, `leerActividad`, `escribirActividad`, `arrancarMarcaDeActividad` (Task 22); `dispararSesionExpirada` de `./expiracion`; `ConfirmDialog` de `@/shared/ui/ConfirmDialog`.
- Produces: `<VigilanciaInactividad />`, que se monta dentro del layout de la aplicación.

**Cómo expulsa:** por el camino que ya existe, `dispararSesionExpirada()`, que el manejador de `main.tsx:24-28` convierte en borrar la sesión y llevar al login con mensaje. Así el comportamiento es idéntico al de una sesión caducada y no hay un segundo camino que mantener.

**Trabajo sin guardar:** el formulario de reparación tiene borrador en el servidor. El modal "Asignar trabajos" y las líneas de un pedido viven solo en memoria, y `useAvisoAlSalir` se calla a propósito cuando la sesión ya se ha borrado. Por eso el aviso previo es la protección, y su texto lo dice.

- [ ] **Step 1: Escribir el test que falla**

`src/shared/session/VigilanciaInactividad.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AVISO_MS, CLAVE_ACTIVIDAD, INACTIVIDAD_MS, leerActividad } from './actividad'
import { CLAVE_SESION } from './storage'
import { VigilanciaInactividad } from './VigilanciaInactividad'

const SESION = JSON.stringify({ idUsu: 8, nombreUsuario: 'ana', rol: 'TECNICO', idTec: 4, token: 't' })

const expulsar = vi.fn()
vi.mock('./expiracion', () => ({
  dispararSesionExpirada: () => expulsar(),
}))

describe('cierre por inactividad', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem(CLAVE_SESION, SESION)
    expulsar.mockClear()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(0)
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('con actividad reciente no avisa ni expulsa', () => {
    localStorage.setItem(CLAVE_ACTIVIDAD, '0')
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(60_000)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(expulsar).not.toHaveBeenCalled()
  })

  it('avisa un minuto antes del cierre', () => {
    localStorage.setItem(CLAVE_ACTIVIDAD, '0')
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS - AVISO_MS)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(expulsar).not.toHaveBeenCalled()
  })

  it('el aviso dice que se puede perder lo no guardado', () => {
    localStorage.setItem(CLAVE_ACTIVIDAD, '0')
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS - AVISO_MS)
    expect(screen.getByRole('dialog').textContent).toMatch(/sin guardar/i)
  })

  it('pasado el tope expulsa por el camino de sesión caducada', () => {
    localStorage.setItem(CLAVE_ACTIVIDAD, '0')
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS)
    expect(expulsar).toHaveBeenCalledTimes(1)
  })

  it('el botón de seguir renueva la marca y cierra el aviso', async () => {
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    localStorage.setItem(CLAVE_ACTIVIDAD, '0')
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS - AVISO_MS)
    await usuario.click(screen.getByRole('button', { name: /seguir/i }))
    expect(leerActividad()).toBeGreaterThan(0)
    expect(screen.queryByRole('dialog')).toBeNull()
    vi.advanceTimersByTime(AVISO_MS + 1_000)
    expect(expulsar).not.toHaveBeenCalled()
  })

  it('la actividad en otra pestaña retira el aviso', () => {
    localStorage.setItem(CLAVE_ACTIVIDAD, '0')
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS - AVISO_MS)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    localStorage.setItem(CLAVE_ACTIVIDAD, String(Date.now()))
    vi.advanceTimersByTime(2_000)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('sin sesión no hace nada', () => {
    localStorage.removeItem(CLAVE_SESION)
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS + 10_000)
    expect(expulsar).not.toHaveBeenCalled()
  })

  it('sin marca previa arranca contando desde ahora', () => {
    render(<VigilanciaInactividad />)
    vi.advanceTimersByTime(INACTIVIDAD_MS - AVISO_MS - 5_000)
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
```

- [ ] **Step 2: Ejecutar el test y comprobar que falla**

```bash
cd gestion-reparaciones-web && npx vitest run src/shared/session/VigilanciaInactividad.test.tsx
```

Esperado: FAIL, no existe el componente.

- [ ] **Step 3: Escribir el componente**

`src/shared/session/VigilanciaInactividad.tsx`:

```tsx
import { useCallback, useEffect, useRef, useState } from 'react'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { AVISO_MS, INACTIVIDAD_MS, escribirActividad, leerActividad } from './actividad'
import { dispararSesionExpirada } from './expiracion'
import { leerSesion } from './storage'

/** Cada cuánto se mira la marca. Un segundo es suficiente y no gasta nada. */
const LATIDO_VIGILANCIA_MS = 1_000

export const MSG_AVISO_TITULO = 'Vas a salir por inactividad'
export const MSG_AVISO_CUERPO =
  'Llevas dos horas sin usar el programa y la sesión se va a cerrar en un minuto. ' +
  'Lo que tengas sin guardar en un pedido o en el reparto de trabajos se perderá; ' +
  'las reparaciones a medias se guardan solas.'

/**
 * Cierra la sesión cuando nadie ha tocado el ERP en dos horas, avisando un minuto antes con un botón para seguir
 * (spec sp7b §6.1). Cuenta el ratón y el teclado, nunca el refresco de las tablas ni volver a la ventana; la
 * marca vive en `localStorage`, así que la actividad de cualquier pestaña cuenta para todas.
 *
 * Expulsa por `dispararSesionExpirada`, el mismo camino que un 401: borra la sesión en todas las pestañas y lleva
 * al login con su mensaje. Se monta dentro del layout de la aplicación, así que solo vive con sesión abierta.
 */
export function VigilanciaInactividad() {
  const [avisando, setAvisando] = useState(false)
  const expulsado = useRef(false)

  const seguir = useCallback(() => {
    escribirActividad()
    setAvisando(false)
  }, [])

  useEffect(() => {
    if (leerSesion() === null) return
    // Sin marca previa (primera carga tras entrar) se cuenta desde ahora.
    if (leerActividad() === null) escribirActividad()

    const mirar = () => {
      if (expulsado.current) return
      if (leerSesion() === null) return
      const marca = leerActividad() ?? Date.now()
      const inactivo = Date.now() - marca
      if (inactivo >= INACTIVIDAD_MS) {
        expulsado.current = true
        setAvisando(false)
        dispararSesionExpirada()
        return
      }
      setAvisando(inactivo >= INACTIVIDAD_MS - AVISO_MS)
    }

    mirar()
    const intervalo = window.setInterval(mirar, LATIDO_VIGILANCIA_MS)
    return () => window.clearInterval(intervalo)
  }, [])

  if (!avisando) return null
  return (
    <ConfirmDialog
      open
      titulo={MSG_AVISO_TITULO}
      mensaje={MSG_AVISO_CUERPO}
      textoConfirmar="Seguir trabajando"
      onConfirmar={seguir}
      onCancelar={seguir}
    />
  )
}
```

Ajustar los nombres de las propiedades de `ConfirmDialog` a los reales; leerlos antes con:

```bash
cd gestion-reparaciones-web && sed -n '1,40p' src/shared/ui/ConfirmDialog.tsx
```

El diálogo **no** ofrece "cerrar sesión ahora": cerrar el aviso equivale a seguir, porque el cierre llega igual si nadie toca nada.

- [ ] **Step 4: Ejecutar el test y comprobar que pasa**

```bash
cd gestion-reparaciones-web && npx vitest run src/shared/session/VigilanciaInactividad.test.tsx
```

Esperado: PASS, 8 tests.

- [ ] **Step 5: Arrancar la marca y montar la vigilancia**

En `src/shared/session/arranque.ts`, dentro de `comprobarSesionAlArrancar`, arrancar también la marca de actividad justo donde arranca el latido, y devolver una función que detenga las dos:

```ts
  adoptarSesionGuardada()
  const detenerLatido = arrancarLatido()
  const detenerActividad = arrancarMarcaDeActividad()
  return () => {
    detenerLatido()
    detenerActividad()
  }
```

Con el import correspondiente:

```ts
import { arrancarMarcaDeActividad } from './actividad'
```

Hacer el mismo cambio en `decidirSoloPorLatido`, que tiene el mismo bloque final.

En `src/app/shell/AppLayout.tsx`, montar el componente dentro del layout:

```tsx
      <VigilanciaInactividad />
```

con su import. Va en el layout y no en `main.tsx` porque el layout solo existe con sesión abierta.

- [ ] **Step 6: Ejecutar la suite de la web**

```bash
cd gestion-reparaciones-web && npm run check 2>&1 | tail -20
```

Esperado: lint, tipos y tests en verde, con un total mayor que los 1670 de `v0.8.5`. Si algún test del layout falla por temporizadores, envolverlo con `vi.useFakeTimers()` o excluir el componente con un mock: la vigilancia no debe obligar a tocar tests ajenos.

- [ ] **Step 7: Commit**

```bash
git add -A src/shared/session src/app/shell/AppLayout.tsx
git commit -m "feat: cerrar la sesion tras dos horas sin actividad, avisando un minuto antes"
```

### Task 24: Cambio obligatorio de la contraseña entregada por el administrador

**Files:**
- Create: `gestion-reparaciones-web/src/app/cuenta/CambioObligatorioPage.tsx`
- Create: `gestion-reparaciones-web/src/app/cuenta/CambioObligatorioPage.test.tsx`
- Modify: `gestion-reparaciones-web/src/app/router.tsx`
- Modify: `gestion-reparaciones-web/src/shared/session/storage.ts` (el tipo `Sesion`)
- Modify: `gestion-reparaciones-web/src/shared/session/SessionProvider.tsx`
- Modify: `gestion-reparaciones-web/src/modules/gestion/tecnicos/TecnicosPage.tsx` (la acción del administrador)

**Interfaces:**
- Consumes: `LoginResponse.passwordTemporal` y `POST /api/usuarios/{idUsu}/password-temporal` (Task 19).
- Produces: `Sesion.passwordTemporal` (`boolean`), la ruta `/cuenta/cambiar-obligatorio`.

**Requisito:** la Task 19 tiene que estar desplegada, porque este trabajo consume un campo y una ruta nuevos del contrato.

- [ ] **Step 1: Regenerar los tipos del contrato**

```bash
cd gestion-reparaciones-web && npm run api:types
git diff --stat api/openapi.json src/shared/api/schema.d.ts
```

Esperado: `LoginResponse` con `passwordTemporal` y la ruta nueva. **Si no aparecen, el servidor desplegado no es el de la entrega 3:** parar.

- [ ] **Step 2: Escribir el test de la pantalla**

`src/app/cuenta/CambioObligatorioPage.test.tsx`, con la forma que usan los tests de página del proyecto (mirar `src/modules/gestion/tecnicos/TecnicosPage.test.tsx` para el envoltorio de providers y el mock de `api`):

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CambioObligatorioPage } from './CambioObligatorioPage'

const patch = vi.fn()
vi.mock('@/shared/api/client', () => ({
  api: { PATCH: (...args: unknown[]) => patch(...args) },
}))

describe('cambio obligatorio de contraseña', () => {
  it('explica por qué está aquí y no ofrece salir sin cambiarla', () => {
    render(<CambioObligatorioPage />)
    expect(screen.getByText(/temporal/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /volver/i })).toBeNull()
  })

  it('envía la actual y la nueva', async () => {
    patch.mockResolvedValue({ data: undefined, error: undefined })
    const usuario = userEvent.setup()
    render(<CambioObligatorioPage />)
    await usuario.type(screen.getByLabelText(/actual/i), 'LaTemporal9')
    await usuario.type(screen.getByLabelText(/nueva/i), 'MiClaveNueva1')
    await usuario.type(screen.getByLabelText(/repite/i), 'MiClaveNueva1')
    await usuario.click(screen.getByRole('button', { name: /cambiar/i }))
    expect(patch).toHaveBeenCalledWith('/api/auth/cambiar-password', {
      body: { passwordActual: 'LaTemporal9', passwordNueva: 'MiClaveNueva1' },
    })
  })

  it('si las dos nuevas no coinciden no llama al servidor', async () => {
    const usuario = userEvent.setup()
    render(<CambioObligatorioPage />)
    await usuario.type(screen.getByLabelText(/actual/i), 'LaTemporal9')
    await usuario.type(screen.getByLabelText(/nueva/i), 'MiClaveNueva1')
    await usuario.type(screen.getByLabelText(/repite/i), 'otra')
    await usuario.click(screen.getByRole('button', { name: /cambiar/i }))
    expect(patch).not.toHaveBeenCalled()
    expect(screen.getByText(/no coinciden/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Escribir la pantalla**

`src/app/cuenta/CambioObligatorioPage.tsx`: un formulario con tres campos `CampoPassword` (actual, nueva, repetir), que llama a `PATCH /api/auth/cambiar-password` y, al terminar bien, marca la sesión como ya sin contraseña temporal y navega a `/`. Reutilizar el diálogo de cambio de contraseña que ya existe en el menú de usuario como referencia de textos y validaciones:

```bash
cd gestion-reparaciones-web && grep -rln "cambiar-password" src/modules src/app
```

El texto explica la situación: la contraseña la ha entregado el administrador y hay que poner una propia antes de seguir.

- [ ] **Step 4: Llevar al usuario allí**

En `storage.ts`, añadir `passwordTemporal: boolean` al tipo `Sesion` y aceptarlo en `leerSesion` (tolerando que falte, para sesiones guardadas antes del despliegue: `s.passwordTemporal === true`).

En `SessionProvider.login`, guardarlo desde la respuesta:

```ts
    const s: Sesion = {
      idUsu: data.idUsu,
      nombreUsuario: data.nombreUsuario,
      rol: data.rol,
      idTec: data.idTec ?? null,
      token: data.token,
      passwordTemporal: data.passwordTemporal === true,
    }
```

En `router.tsx`, añadir la ruta dentro de `RequireSesion` y **fuera** de `AppLayout`, y hacer que `RequireSesion` mande allí mientras la marca esté puesta:

```tsx
  { path: '/cuenta/cambiar-obligatorio', element: <CambioObligatorioPage /> },
```

En `RequireSesion`, antes de pintar la aplicación:

```tsx
  if (sesion.passwordTemporal && location.pathname !== '/cuenta/cambiar-obligatorio') {
    return <Navigate to="/cuenta/cambiar-obligatorio" replace />
  }
```

- [ ] **Step 5: Añadir la acción del administrador**

En la pantalla de técnicos, una acción "Restablecer contraseña" por fila, visible solo al administrador, que pida confirmación, llame a `POST /api/usuarios/{idUsu}/password-temporal` y **muestre la contraseña devuelta una sola vez**, con un botón para copiarla (`shared/ui/copiar.ts`) y el aviso de que no se volverá a mostrar y que el usuario tendrá que cambiarla al entrar.

- [ ] **Step 6: Ejecutar la suite**

```bash
cd gestion-reparaciones-web && npm run check 2>&1 | tail -20
```

Esperado: todo en verde.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "feat: la contrasena entregada por el administrador hay que cambiarla antes de seguir"
```

### Task 25: Aviso de versión nueva tras un despliegue

**Files:**
- Modify: `gestion-reparaciones-web/vite.config.ts`
- Create: `gestion-reparaciones-web/src/shared/version/versionPublicada.ts`
- Create: `gestion-reparaciones-web/src/shared/version/versionPublicada.test.ts`
- Create: `gestion-reparaciones-web/src/shared/version/AvisoVersion.tsx`
- Create: `gestion-reparaciones-web/src/shared/version/AvisoVersion.test.tsx`
- Modify: `gestion-reparaciones-web/src/app/shell/AppLayout.tsx`
- Modify: `gestion-reparaciones-web/deploy/nginx/default.conf`

**Interfaces:**
- Consumes: `APP_VERSION` de `@/shared/lib/version` (existe).
- Produces: `RUTA_VERSION` (`'/version.json'`), `INTERVALO_VERSION_MS` (`number`), `useVersionNueva(): boolean`, `<AvisoVersion />`.

**Contexto:** la versión ya está dentro del bundle (`__APP_VERSION__` desde `package.json`, `vite.config.ts:14`, pintada en `TopBar.tsx:26`). Lo que no hay es forma de preguntar cuál es la versión **publicada**. nginx ya sirve `/index.html` con `no-cache` y los assets con hash y `expires 7d` (`deploy/nginx/default.conf:61-79`), pero un `.json` cae en el `location /` sin cabecera de caché explícita, así que hay que añadirle la suya.

**Nunca recarga sola:** el botón lo pulsa la persona, porque recargar pierde lo escrito.

- [ ] **Step 1: Emitir el fichero de versión en la compilación**

En `vite.config.ts`, añadir el plugin y registrarlo:

```ts
/** Publica la versión junto al bundle, para que una pestaña abierta pueda saber si hay una nueva desplegada. */
function emitirVersion(version: string) {
  return {
    name: 'emitir-version',
    generateBundle() {
      this.emitFile({
        type: 'asset' as const,
        fileName: 'version.json',
        source: JSON.stringify({ version }),
      })
    },
  }
}
```

Y en la lista de plugins:

```ts
    plugins: [react(), tailwindcss(), emitirVersion(pkg.version)],
```

Comprobar que sale:

```bash
cd gestion-reparaciones-web && npm run build >/dev/null && cat dist/version.json
```

Esperado: `{"version":"0.9.0"}` una vez subida la versión, o la que tenga `package.json` en ese momento.

- [ ] **Step 2: Escribir el test del sondeo**

`src/shared/version/versionPublicada.test.ts`:

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { INTERVALO_VERSION_MS, RUTA_VERSION, leerVersionPublicada } from './versionPublicada'

describe('versión publicada', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sondea cada cinco minutos', () => {
    expect(INTERVALO_VERSION_MS).toBe(5 * 60 * 1000)
  })

  it('pide el fichero sin caché', async () => {
    const fetchFalso = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ version: '0.9.1' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchFalso)
    expect(await leerVersionPublicada()).toBe('0.9.1')
    expect(fetchFalso).toHaveBeenCalledWith(RUTA_VERSION, { cache: 'no-store' })
  })

  it('un 404 no es un error: devuelve null', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 404 })))
    expect(await leerVersionPublicada()).toBeNull()
  })

  it('un fallo de red devuelve null', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')))
    expect(await leerVersionPublicada()).toBeNull()
  })

  it('un cuerpo que no es el esperado devuelve null', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"otra":1}', { status: 200 })))
    expect(await leerVersionPublicada()).toBeNull()
  })
})
```

- [ ] **Step 3: Escribir el módulo**

`src/shared/version/versionPublicada.ts`:

```ts
/** Fichero que la compilación deja junto al bundle con la versión publicada. */
export const RUTA_VERSION = '/version.json'
/** Cada cuánto se comprueba. Cinco minutos: un despliegue no es urgente y no conviene gastar peticiones. */
export const INTERVALO_VERSION_MS = 5 * 60 * 1000

/**
 * Versión que hay publicada ahora mismo, o null si no se puede saber. No es un error que falte: en desarrollo el
 * fichero no existe, y una pestaña sin red simplemente no se enterará todavía. Se pide sin caché porque el
 * navegador guardaría la respuesta y el aviso no llegaría nunca.
 */
export async function leerVersionPublicada(): Promise<string | null> {
  try {
    const respuesta = await fetch(RUTA_VERSION, { cache: 'no-store' })
    if (!respuesta.ok) return null
    const cuerpo: unknown = await respuesta.json()
    if (typeof cuerpo !== 'object' || cuerpo === null) return null
    const version = (cuerpo as { version?: unknown }).version
    return typeof version === 'string' && version !== '' ? version : null
  } catch {
    return null
  }
}
```

- [ ] **Step 4: Escribir el test del aviso**

`src/shared/version/AvisoVersion.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AvisoVersion } from './AvisoVersion'

vi.mock('@/shared/lib/version', () => ({ APP_VERSION: '0.9.0' }))

const leer = vi.fn()
vi.mock('./versionPublicada', async (importar) => {
  const real = await importar<typeof import('./versionPublicada')>()
  return { ...real, leerVersionPublicada: () => leer() }
})

describe('aviso de versión nueva', () => {
  it('con la misma versión no muestra nada', async () => {
    leer.mockResolvedValue('0.9.0')
    render(<AvisoVersion />)
    await waitFor(() => expect(leer).toHaveBeenCalled())
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('con una versión distinta ofrece recargar', async () => {
    leer.mockResolvedValue('0.9.1')
    render(<AvisoVersion />)
    expect(await screen.findByRole('button', { name: /recargar/i })).toBeInTheDocument()
  })

  it('si no se puede saber la versión no muestra nada', async () => {
    leer.mockResolvedValue(null)
    render(<AvisoVersion />)
    await waitFor(() => expect(leer).toHaveBeenCalled())
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('no recarga por su cuenta', async () => {
    leer.mockResolvedValue('0.9.1')
    const recargar = vi.fn()
    vi.stubGlobal('location', { ...window.location, reload: recargar })
    render(<AvisoVersion />)
    await screen.findByRole('button', { name: /recargar/i })
    expect(recargar).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 5: Escribir el aviso**

`src/shared/version/AvisoVersion.tsx`: un `<div role="status">` fijo abajo con el texto "Hay una versión nueva del programa." y un botón "Recargar" que llama a `window.location.reload()`. Sondea con `useEffect` + `setInterval(INTERVALO_VERSION_MS)`, compara con `APP_VERSION`, y una vez detectada la diferencia deja de sondear. Estilo: los tokens y el aspecto de los avisos que ya existen (`shared/ui/alertas.ts`, `CapaCarga.tsx`).

- [ ] **Step 6: Montarlo y dar cabecera al fichero en nginx**

En `AppLayout.tsx`, junto a `<VigilanciaInactividad />`:

```tsx
      <AvisoVersion />
```

En `deploy/nginx/default.conf`, añadir un `location` propio antes de `location /`, repitiendo las cabeceras de seguridad porque nginx sustituye las del padre en cuanto un hijo define alguna:

```nginx
    # La versión publicada no se cachea: es lo que delata un despliegue a las pestañas abiertas.
    location = /version.json {
        root /usr/share/nginx/html;
        add_header Cache-Control "no-cache" always;
        add_header X-Frame-Options DENY always;
        add_header X-Content-Type-Options nosniff always;
        add_header Referrer-Policy same-origin always;
        add_header Strict-Transport-Security "max-age=31536000" always;
    }
```

- [ ] **Step 7: Ejecutar la suite y la compilación**

```bash
cd gestion-reparaciones-web && npm run check 2>&1 | tail -20
cd gestion-reparaciones-web && npm run build >/dev/null && cat dist/version.json
```

Esperado: todo en verde y el fichero con la versión.

- [ ] **Step 8: Commit**

```bash
git add -A src vite.config.ts deploy/nginx/default.conf
git commit -m "feat: avisar de que hay una version nueva publicada, con boton para recargar"
```

### Task 26: Cierre de la web y tag 0.9.0

**Files:**
- Modify: `gestion-reparaciones-web/package.json` (versión)
- Modify: `gestion-reparaciones-web/CHANGELOG.md`
- Modify: `docs/novedades/NOVEDADES-v0.9.0.md` (raíz)

**Interfaces:** ninguna.

- [ ] **Step 1: Subir la versión y escribir los documentos de la release**

`package.json` a `0.9.0`; sección `## [0.9.0] - 2026-...` en el `CHANGELOG.md` de la web con los enlaces de comparación del final actualizados; y `docs/novedades/NOVEDADES-v0.9.0.md` en la raíz, en lenguaje de usuario, contando: la sesión se cierra tras dos horas sin usar el programa, con aviso previo; hay aviso cuando se publica una versión nueva; el administrador puede entregar una contraseña temporal; y cada operación exige el permiso que le corresponde. **Sin mencionar ningún hueco anterior.**

- [ ] **Step 2: Suite completa, compilación y verificación**

```bash
cd gestion-reparaciones-web && npm run check 2>&1 | tail -20
cd gestion-reparaciones-web && npm run build 2>&1 | tail -5
ls -la dist/assets/*.js dist/version.json
```

Esperado: todo en verde; anotar el nombre del bundle para comprobarlo después en la máquina.

- [ ] **Step 3: Smoke contra producción**

Con la web de la rama en local (`npm run dev` con `.env.local` apuntando a producción) y después la suite e2e, **los ficheros de uno en uno con un minuto de separación**, porque cada uno inicia sesión y el límite del proxy es de cinco por minuto:

```bash
cd gestion-reparaciones-web
set -a; . ~/.env.e2e; set +a
for f in tests/e2e/*.spec.ts; do echo "== $f"; npx playwright test "$f" --workers=1; sleep 75; done
```

Esperado: 7 de 7 ficheros en verde.

- [ ] **Step 4: Revisión, OK, merge y push (uno a uno)**

`superpowers:requesting-code-review` de la rama, con foco en: que la vigilancia no cuenta el foco ni el refresco de tablas, que el aviso de versión no recarga solo, y que la contraseña temporal no se guarda ni se registra en ninguna parte del cliente.

Después, **pedir OK y esperar**, para el merge, para el push y para el tag, uno a uno.

- [ ] **Step 5: Desplegar la web en PRODUCCIÓN**

**Máquina:** producción. **Hora:** cualquiera. P8 de la guía, y además el cambio de `default.conf` con **P8-b** (diff, copia `.bak`, `cp`, `nginx -t`, `restart nginx`), porque un `up --build` no recarga un fichero de configuración montado:

```bash
ssh prod
cd /opt/reparaciones/gestion-reparaciones-web && git pull
cd /opt/reparaciones && docker compose up -d --build
curl -s -I $E2E_BASE_URL/version.json | grep -i "cache-control\|http/"
curl -s $E2E_BASE_URL/version.json
```

Esperado: `Cache-Control: no-cache` y el JSON con `0.9.0`.

- [ ] **Step 6: Comprobar el aviso de versión de verdad**

Es la única forma de probar el caso E-09: dejar una pestaña abierta con la versión anterior, desplegar, y esperar cinco minutos a que aparezca el aviso con su botón. Anotar el resultado con captura en `Apuntes/sp7/`.

- [ ] **Step 7: Comprobar el cierre por inactividad**

Con una sesión abierta, sin tocar nada, comprobar que a las dos horas menos un minuto sale el aviso y que el botón lo posterga. Para no esperar dos horas, adelantar la marca desde la consola del navegador:

```js
localStorage.setItem('fsgr.actividad', String(Date.now() - (2 * 60 * 60 * 1000 - 70_000)))
```

Esperado: el aviso aparece en unos diez segundos; pulsar "Seguir trabajando" lo retira y no vuelve. Repetir poniendo la marca a dos horas justas: debe llevar al login con su mensaje. **Comprobar además que el refresco automático de una tabla no retrasa el cierre:** dejar la pestaña de Pendientes en primer plano sin tocar nada y confirmar que el aviso llega igual.

- [ ] **Step 8: Tag 0.9.0 (tras el OK)**

El producto se etiqueta con servidor y web juntos. Servidor y web ya en `main` y desplegados:

```bash
cd gestion-reparaciones-web && git tag -a v0.9.0 -m "0.9.0: autorizacion en servidor, sesiones y avisos de la web" && git push origin v0.9.0
cd ../gestion-reparaciones-servidor && git tag -a v0.9.0 -m "0.9.0: autorizacion en servidor, sesiones y avisos de la web" && git push origin v0.9.0
cd .. && git add gestion-reparaciones-servidor gestion-reparaciones-web docs/novedades/NOVEDADES-v0.9.0.md
git commit -m "chore: gitlinks servidor y web tras la 0.9.0 (v0.9.0)"
```

Cada `push` y el tag, **con OK por separado**.

- [ ] **Step 9: Desplegar la web en PREPRODUCCIÓN**

Solo si el usuario quiere la web también allí. **Hora: FUERA DE HORARIO.** Hoy preproducción no sirve la web: si se decide montarla, es trabajo aparte y se consulta.

---

## CIERRE — Retirar la API de primera generación

### Task 27: Borrar las rutas retiradas, con la prueba en la mano

**Files:**
- Delete: los métodos de controlador de las 24 rutas, y los métodos de DAO que solo ellos usaban
- Delete: `security/RutasRetiradas.java`, `security/RutasRetiradasInterceptor.java` y su registro en `config/WebMvcConfig.java`
- Delete: `src/test/java/com/reparaciones/servidor/security/RutasRetiradas*Test.java`

**Interfaces:**
- Consumes: el registro de intentos que dejó la Task 1.

**Requisito:** que hayan pasado **dos o tres semanas** desde que la entrega 1 llegó a preproducción, que es donde el taller genera tráfico real.

- [ ] **Step 1: Comprobar que nadie las ha llamado**

Comando **para el usuario**, en las dos máquinas:

```bash
ssh prod "docker exec reparaciones-mariadb-1 sh -c 'mariadb -uroot -p\"\$MARIADB_ROOT_PASSWORD\" gestion_reparaciones -e \"SELECT NOMBRE_USUARIO, DETALLE, COUNT(*) n, MIN(FECHA), MAX(FECHA) FROM Log_Actividad WHERE ACCION=\\\"RUTA_RETIRADA\\\" GROUP BY NOMBRE_USUARIO, DETALLE ORDER BY n DESC;\"'"
```

Repetir con `preprod`. **Esperado: cero filas en las dos.**

Si aparece alguna fila, **no se borra nada**: se saca esa ruta de la lista, se vuelve a abrir con la guarda de rol que le corresponda según quién la llamó, y se anota en el inventario privado. El resto puede seguir adelante.

- [ ] **Step 2: Comprobarlo también en los registros del proxy**

```bash
ssh prod "cd /opt/reparaciones/logs-nginx && grep -c -E 'GET /api/componentes |GET /api/telefonos |POST /api/tecnicos|DELETE /api/tecnicos/' erp.access.log || echo 0"
```

Los registros del proxy no rotan, así que cubren todo el periodo. Cero es cero.

- [ ] **Step 3: Borrar los métodos de controlador**

Uno por uno, con la lista de `RutasRetiradas.LISTA` delante. Después de cada grupo, compilar:

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor && mvn -q -DskipTests compile
```

- [ ] **Step 4: Borrar los métodos de DAO que se queden sin uso**

```bash
cd gestion-reparaciones-servidor
for m in getAll getStockBajo getChasisPorColor getEvolucionStock countByImei getResumenPorImei \
         getAsignacionesPorImei getTecnicosConAsignacionActiva getEstadisticasPorTecnico exists; do
  echo "== $m"; grep -rn "\.$m(" src/main/java | grep -v "/dao/" | head -3
done
```

Borrar solo los que no tengan ninguna línea. Los que sí tengan, se quedan.

- [ ] **Step 5: Retirar el mecanismo**

Borrar `RutasRetiradas`, el interceptor, su registro en `WebMvcConfig` (y el propio `WebMvcConfig` si no le queda nada más) y los dos tests. Retirar de `GuardasVigentesTest` las rutas de la lista `VIVAS` que ya no existan.

- [ ] **Step 6: Suite, contrato y revisión**

```bash
cd gestion-reparaciones-servidor && mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos. **El contrato cambia aquí de verdad**: baja de 138 a 114 rutas. Regenerar los tipos de la web y comprobar que no se usa ninguna:

```bash
cd ../gestion-reparaciones-web && npm run api:types && npm run check 2>&1 | tail -10
```

Esperado: la web compila sin tocar nada.

- [ ] **Step 7: Cierre**

OK del usuario, merge, push, despliegue en producción, prueba del JavaFX real (recorrido completo de los tres roles, con las dos pruebas obligatorias), y despliegue en preproducción fuera de horario. Sale como **0.9.1** si el tag 0.9.0 ya está puesto.

---

## Autorrevisión del plan

**Cobertura de la spec, sección por sección:**

| Spec | Tareas |
|---|---|
| §4.1 rutas de primera generación | 1, 2, 27 |
| §4.2 módulos que el taller no usa | 3 |
| §4.3 rutas de un solo rol | 4 |
| §4.4 borrado de teléfono y freno del lookup | 5, 6 |
| §4.4 completar pulidos en lote | 9 |
| §4.4 estadísticas | 10 |
| §4.4 editar el modelo | 11 |
| §4.4 lo que ya estaba cerrado | 12 (tests que lo fijan) |
| §4.5 robustez del filtro | 7 |
| §5.1 usuario comprobado en cada petición | 17 |
| §5.2 y §5.3 fallos registrados y freno | 18 |
| §5.4 contraseña temporal | 19 (servidor), 24 (web) |
| §5.5 cambiar la propia contraseña | 18 |
| §5.6 el registro sobrevive al borrado | 14, 15, 16 |
| §5.7 escrituras sin rastro | 5, 20 |
| §6.1 inactividad | 22, 23 |
| §6.2 aviso de versión | 25 |
| §7 esquema | 15 |
| §8 entregas y despliegue | 8, 13, 21, 26, 27 |
| §9 verificación | los pasos de máquina de 8, 13, 21, 26 |

**Dos desviaciones de la spec, a propósito, para que el usuario las vea:**

1. **§4.4 decía que las cuatro rutas que la web abre al técnico se filtrarían por dueño.** Al leer el código, `pendientes/contadores` ya lo hace, y las otras tres (`ya-reparados`, `imei/{imei}/acciones`, `asignaciones/{idRep}`) están indexadas por IMEI o por asignación concreta: el formulario de cualquier técnico consulta legítimamente el IMEI en el que trabaja, incluso cuando ese teléfono tiene trabajos de varios técnicos. Exigir dueño rompería el formulario. **Se quedan abiertas y documentadas**, como las listas de catálogo de la decisión D13. Está en la Task 12 paso 3.
2. **§4.4 decía "escrituras del formulario: se comprueba que la reparación es de quien la edita o la completa".** Ya está hecho desde el sub-proyecto 2 (`ReparacionController:303, 331, 610, 632`) y editar una reparación ya hecha exige supertécnico (`:530`). La Task 12 lo fija con tests en lugar de reimplementarlo.

**Consistencia de nombres comprobada:** `RutasRetiradas.LISTA/coincide/MSG_RETIRADA/ACCION_LOG` (Tasks 1, 2, 27); `TelefonoDAO.MSG_TIENE_TRABAJOS` (Task 5); `FrenoLookup.comprobar/INTERVALO_MIN_MS/MSG_DEMASIADAS` (Task 6); `PulidoController.MSG_NO_ES_PULIDO` (Task 9); `EstadisticasPropias.recortar` (Task 10); `LogDAO.insertarIntento` (Tasks 16, 18); `EstadoUsuarioService.estaOperativo/TTL_MS` (Task 17); `IntentosFallidos.comprobar/registrarFallo/limpiar/esperaMs/UMBRAL/ESPERA_MAX_MS/MSG_ESPERA` (Task 18); `UsuarioDAO.fijarPasswordTemporal/tienePasswordTemporal` y `LoginResponse.passwordTemporal` (Tasks 19, 24); `actividad.ts` con `CLAVE_ACTIVIDAD/INACTIVIDAD_MS/AVISO_MS/escribirActividad/leerActividad/arrancarMarcaDeActividad` (Tasks 22, 23); `versionPublicada.ts` con `RUTA_VERSION/INTERVALO_VERSION_MS/leerVersionPublicada` (Task 25).

**El contrato solo cambia en dos sitios:** la Task 19 lo sube a 138 rutas y añade un campo a la respuesta del login; la Task 27 lo baja a 114. En medio, idéntico a `v0.8.5`.

