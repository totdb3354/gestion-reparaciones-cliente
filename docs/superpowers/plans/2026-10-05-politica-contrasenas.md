# Política de contraseñas (0.9.2) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que toda contraseña elegida por una persona a partir de la 0.9.2 sea difícil de adivinar, con una regla única en el servidor y una barra en la web que la enseña mientras se escribe; el alta pasa a generar una contraseña temporal.

**Architecture:** En el servidor, `PoliticaPassword` aplica las reglas (longitud, distinta de la actual, nota mínima por rol) sobre la nota de un `MedidorFuerza` implementado con `zxcvbn4j`; la usan `cambiar-password` y un endpoint nuevo `evaluar-password` que la web consulta mientras se escribe. El alta deja de recibir contraseña y devuelve una temporal generada. En la web, un hook pide la nota con una pausa de 0,3 s y `MedidorPassword` pinta la barra en el diálogo de cambio y en la pantalla de cambio obligatorio.

**Tech Stack:** Servidor Spring Boot 3.3.4 + JdbcTemplate + `com.nulab-inc:zxcvbn:1.9.0`, tests JUnit 5 + Mockito + MockMvc. Web Vite + React 19 + TanStack Query + openapi-fetch, tests Vitest + msw, e2e Playwright.

**Spec:** `docs/superpowers/specs/2026-10-05-politica-contrasenas-design.md`

## Global Constraints

- **Los tres repos son públicos.** Ni dominios, ni direcciones, ni nombres de personas, ni el nombre de la empresa en código, tests, commits ni docs de repo. El nombre de la empresa solo va en la variable de entorno del compose de la máquina.
- **Commits en español, en minúscula tras el prefijo, sin `Co-Authored-By`.** Nunca `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Claude no hace SSH.** Los comandos de máquina se preparan y los ejecuta el usuario, en una sola sesión por máquina.
- **La contraseña nunca se registra**: ni en el log de actividad, ni en el log de la aplicación, ni en el almacenamiento del navegador, ni en la caché de consultas o de mutaciones.
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`
- **Versión del producto: 0.9.2.** Servidor y web etiquetados juntos al final.
- **Ramas:** `feature/politica-contrasenas` en el repo del servidor (la crea la Task 1) y en el de la web (la crea la Task 7).
- **Reglas exactas** (spec §3.1): mínimo **10** caracteres; máximo **64** caracteres y **72 bytes** UTF-8; distinta de la actual; nota **≥ 3** para TECNICO y SUPERTECNICO y **4** para ADMIN. Mensajes literales:
  - `Rellena todos los campos.`
  - `La contraseña debe tener al menos 10 caracteres.`
  - `La contraseña es demasiado larga (máximo 64 caracteres).`
  - `La nueva contraseña tiene que ser distinta de la actual.`
  - `La contraseña es poco segura.` (+ espacio + primer consejo, si lo hay)
- **Textos de la barra:** `Muy débil`, `Débil`, `Poco segura`, `Segura`, `Muy segura`; ayuda fija `Mínimo 10 caracteres.`; para ADMIN además `Para el administrador se pide «Muy segura».`; si falla la consulta, `No se pudo comprobar`.

## Estructura de ficheros

**Servidor (`gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/`) — se crean:**

| Fichero | Responsabilidad |
|---|---|
| `security/MedidorFuerza.java` | Interfaz: nota 0-4 y consejos de una contraseña. Permite probar las reglas con una nota fija. |
| `security/MedidorZxcvbn.java` | Implementación con zxcvbn4j y consejos en español. |
| `security/PoliticaPassword.java` | Las reglas, su orden, el umbral por rol y las palabras que penalizan. |
| `model/EvaluacionPassword.java` | Respuesta `{nota, aceptable, mensaje}`. |

**Servidor — se modifican:** `pom.xml`, `controller/AuthController.java`, `controller/UsuarioController.java`, `controller/ValidacionUsuarios.java`, `security/JwtAuthFilter.java`.

**Web (`gestion-reparaciones-web/src/`) — se crean:** `modules/gestion/cuenta/medidor.ts` (tipos, consulta y hook), `modules/gestion/cuenta/MedidorPassword.tsx` (la barra).
**Web — se modifican:** `shared/api/schema.d.ts` y `api/openapi.json` (regenerados), `test/server.ts`, `modules/gestion/cuenta/validacion.ts`, `modules/gestion/cuenta/CambiarPasswordDialog.tsx`, `app/cuenta/CambioObligatorioPage.tsx`, `modules/gestion/tecnicos/{validacion.ts,api.ts,TecnicosPage.tsx}`, `tests/e2e/gestion.spec.ts`, `package.json`, `CHANGELOG.md`.

---

## SERVIDOR

### Task 1: Medidor de fuerza con zxcvbn4j

**Files:**
- Modify: `gestion-reparaciones-servidor/pom.xml`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/MedidorFuerza.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/MedidorZxcvbn.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/security/MedidorZxcvbnTest.java`

**Interfaces:**
- Produces: `interface MedidorFuerza { Medida medir(String password, List<String> palabrasDelUsuario); record Medida(int nota, List<String> consejos) {} }` y el bean `MedidorZxcvbn implements MedidorFuerza`.

- [ ] **Step 1: Rama y dependencia**

```bash
cd gestion-reparaciones-servidor && git checkout -b feature/politica-contrasenas
```

En `pom.xml`, dentro de `<dependencies>`, después de `jjwt-jackson`:

```xml
        <!-- Estimación de la fuerza de una contraseña (algoritmo zxcvbn de Dropbox), con consejos en español -->
        <dependency>
            <groupId>com.nulab-inc</groupId>
            <artifactId>zxcvbn</artifactId>
            <version>1.9.0</version>
        </dependency>
```

```bash
mvn -q dependency:resolve 2>&1 | tail -3
```

- [ ] **Step 2: Comprobar en el jar el nombre del paquete de mensajes en español**

```bash
J=~/.m2/repository/com/nulab-inc/zxcvbn/1.9.0/zxcvbn-1.9.0.jar
unzip -l "$J" | grep -i "messages"
unzip -p "$J" $(unzip -l "$J" | grep -io "[^ ]*messages_es[^ ]*\.properties" | head -1) | head -40
javap -cp "$J" com.nulab_inc.zxcvbn.Feedback | grep -i "withResourceBundle\|getWarning\|getSuggestions"
```

Esperado: un `.../messages_es.properties` y `withResourceBundle(java.util.ResourceBundle)`. El nombre base del paquete es la ruta del `.properties` sin `_es.properties` (p. ej. `com/nulab_inc/zxcvbn/messages`). **Si es otro, usar el que salga** en la constante `PAQUETE_MENSAJES` del Step 5. Anotar el texto en español del aviso de contraseña muy común (la clave que contiene `topTen` o `top10`): se usa en el Step 3.

- [ ] **Step 3: Test que falla**

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Con la librería real: lo obvio sale bajo, una frase de palabras sueltas sale alto, las palabras del usuario bajan la
 *  nota y los consejos están en español. */
class MedidorZxcvbnTest {

    private final MedidorZxcvbn medidor = new MedidorZxcvbn();

    @Test void unaSerieDeDigitosSacaNotaMinima() {
        assertTrue(medidor.medir("123456789012", List.of()).nota() <= 1);
    }

    @Test void unaFraseDePalabrasSueltasSacaLaNotaMaxima() {
        assertEquals(4, medidor.medir("tortuga violeta lampara nube 47", List.of()).nota());
    }

    @Test void lasPalabrasDelUsuarioBajanLaNota() {
        int sin = medidor.medir("zapateriagonzalez", List.of()).nota();
        int con = medidor.medir("zapateriagonzalez", List.of("zapateria", "gonzalez")).nota();
        assertTrue(con < sin, () -> "sin palabras " + sin + ", con palabras " + con);
    }

    @Test void losConsejosEstanEnEspanol() {
        List<String> consejos = medidor.medir("password", List.of()).consejos();
        assertFalse(consejos.isEmpty());
        assertNotEquals("This is a top-10 common password.", consejos.get(0));
        assertNotEquals("This is a top-10 common password", consejos.get(0));
        // El texto exacto del Step 2 (aviso de contraseña muy común en messages_es.properties):
        assertTrue(consejos.get(0).toLowerCase().contains("contraseña"), () -> "consejo: " + consejos.get(0));
    }

    @Test void laNotaVaDeCeroACuatro() {
        for (String p : List.of("a", "password", "Tr0ub4dor&3", "tortuga violeta lampara nube 47")) {
            int nota = medidor.medir(p, List.of()).nota();
            assertTrue(nota >= 0 && nota <= 4, () -> p + " → " + nota);
        }
    }
}
```

Si el texto del Step 2 no contiene la palabra "contraseña", sustituir esa última aserción por `assertEquals("<texto literal del Step 2>", consejos.get(0))`.

- [ ] **Step 4: Ejecutar y ver que falla**

```bash
mvn -q -Dtest=MedidorZxcvbnTest test 2>&1 | tail -15
```

Esperado: error de compilación, `MedidorZxcvbn` no existe.

- [ ] **Step 5: Implementación**

`MedidorFuerza.java`:

```java
package com.reparaciones.servidor.security;

import java.util.List;

/** Estima lo difícil que es adivinar una contraseña: nota de 0 (trivial) a 4 (muy difícil) y consejos para mejorarla.
 *  Interfaz para que las reglas de {@link PoliticaPassword} se prueben con una nota fija. */
@FunctionalInterface
public interface MedidorFuerza {

    Medida medir(String password, List<String> palabrasDelUsuario);

    /** {@code consejos}: primero el aviso (si lo hay) y después las sugerencias, ya traducidos. */
    record Medida(int nota, List<String> consejos) {}
}
```

`MedidorZxcvbn.java`:

```java
package com.reparaciones.servidor.security;

import com.nulab_inc.zxcvbn.Feedback;
import com.nulab_inc.zxcvbn.Strength;
import com.nulab_inc.zxcvbn.Zxcvbn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * {@link MedidorFuerza} con zxcvbn4j (el algoritmo zxcvbn de Dropbox): estima los intentos que haría falta buscando
 * contraseñas comunes, palabras de diccionario, patrones de teclado, series, fechas, sustituciones típicas y las
 * palabras del usuario. Una instancia para todo el proceso; {@code medir} va sincronizado porque la librería no dice si
 * es segura entre hilos, y a esta escala (unas pocas personas cambiando la contraseña) no cuesta nada.
 */
@Component
public class MedidorZxcvbn implements MedidorFuerza {

    /** Nombre base comprobado en el jar (Task 1, Step 2). */
    private static final String PAQUETE_MENSAJES = "com/nulab_inc/zxcvbn/messages";
    private static final ResourceBundle MENSAJES = ResourceBundle.getBundle(PAQUETE_MENSAJES, Locale.forLanguageTag("es"));

    private final Zxcvbn zxcvbn = new Zxcvbn();

    @Override
    public synchronized Medida medir(String password, List<String> palabrasDelUsuario) {
        Strength s = zxcvbn.measure(password, palabrasDelUsuario);
        Feedback f = s.getFeedback().withResourceBundle(MENSAJES);
        List<String> consejos = new ArrayList<>();
        if (f.getWarning() != null && !f.getWarning().isBlank()) consejos.add(f.getWarning());
        for (String c : f.getSuggestions()) {
            if (c != null && !c.isBlank()) consejos.add(c);
        }
        return new Medida(s.getScore(), List.copyOf(consejos));
    }
}
```

- [ ] **Step 6: Ejecutar y ver que pasa**

```bash
mvn -q -Dtest=MedidorZxcvbnTest test 2>&1 | tail -15
```

Esperado: 5 tests, 0 fallos. Si `unaFraseDePalabrasSueltasSacaLaNotaMaxima` da 3, **no** cambiar la librería: alargar la frase del test con otra palabra y anotarlo en el informe de la tarea.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java/com/reparaciones/servidor/security/MedidorFuerza.java src/main/java/com/reparaciones/servidor/security/MedidorZxcvbn.java src/test/java/com/reparaciones/servidor/security/MedidorZxcvbnTest.java
git commit -m "feat: medidor de la fuerza de una contrasena con zxcvbn4j y consejos en espanol"
```

---

### Task 2: La regla única, `PoliticaPassword`

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/EvaluacionPassword.java`
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/PoliticaPassword.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/security/PoliticaPasswordTest.java`

**Interfaces:**
- Consumes: `MedidorFuerza` (Task 1).
- Produces:
  - `record EvaluacionPassword(int nota, boolean aceptable, String mensaje)` (`mensaje` null si es aceptable).
  - `@Component class PoliticaPassword` con constructor `PoliticaPassword(MedidorFuerza medidor, @Value("${politica.password.palabras-propias:}") String palabrasPropias)` y método `EvaluacionPassword evaluar(String password, String actual, String nombreUsuario, String nombreTecnico, String rol)`. `actual` null = no se compara. Constantes públicas `MSG_RELLENA`, `MSG_CORTA`, `MSG_LARGA`, `MSG_IGUAL`, `MSG_POCO_SEGURA`.

- [ ] **Step 1: Test que falla**

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.model.EvaluacionPassword;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Las reglas y su orden con una nota fija (el medidor real se prueba en MedidorZxcvbnTest). */
class PoliticaPasswordTest {

    private static PoliticaPassword conNota(int nota, String... consejos) {
        return new PoliticaPassword((p, w) -> new MedidorFuerza.Medida(nota, List.of(consejos)), "");
    }

    private static final String BUENA = "abcdefghij";   // 10 caracteres

    @Test void vaciaONulaPideRellenarYNoMide() {
        List<String> medidas = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { medidas.add(p); return new MedidorFuerza.Medida(4, List.of()); }, "");
        assertEquals(new EvaluacionPassword(0, false, PoliticaPassword.MSG_RELLENA), politica.evaluar("", null, "u", null, "TECNICO"));
        assertEquals(new EvaluacionPassword(0, false, PoliticaPassword.MSG_RELLENA), politica.evaluar(null, null, "u", null, "TECNICO"));
        assertTrue(medidas.isEmpty());
    }

    @Test void menosDeDiezEsCortaAunqueLaNotaSeaAlta() {
        var ev = conNota(4).evaluar("abcdefghi", null, "u", null, "TECNICO");
        assertEquals(new EvaluacionPassword(4, false, "La contraseña debe tener al menos 10 caracteres."), ev);
    }

    @Test void diezYSesentaYCuatroSonValidos() {
        assertTrue(conNota(3).evaluar(BUENA, null, "u", null, "TECNICO").aceptable());
        assertTrue(conNota(3).evaluar("a".repeat(64), null, "u", null, "TECNICO").aceptable());
    }

    @Test void masDeSesentaYCuatroCaracteresEsLarga() {
        var ev = conNota(4).evaluar("a".repeat(65), null, "u", null, "TECNICO");
        assertEquals("La contraseña es demasiado larga (máximo 64 caracteres).", ev.mensaje());
        assertFalse(ev.aceptable());
    }

    /** 40 caracteres, 77 bytes: BCrypt solo usa 72. */
    @Test void masDeSetentaYDosBytesEsLargaAunqueTengaMenosDe64Caracteres() {
        String conTildes = "ñ".repeat(37) + "abc";
        assertEquals(40, conTildes.length());
        assertEquals(PoliticaPassword.MSG_LARGA, conNota(4).evaluar(conTildes, null, "u", null, "TECNICO").mensaje());
    }

    @Test void unaCadenaEnormeNoSeMide() {
        List<String> medidas = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { medidas.add(p); return new MedidorFuerza.Medida(4, List.of()); }, "");
        assertEquals(PoliticaPassword.MSG_LARGA, politica.evaluar("a".repeat(5000), null, "u", null, "TECNICO").mensaje());
        assertTrue(medidas.isEmpty());
    }

    @Test void igualALaActualSeRechaza() {
        assertEquals("La nueva contraseña tiene que ser distinta de la actual.",
                conNota(4).evaluar(BUENA, BUENA, "u", null, "TECNICO").mensaje());
        assertTrue(conNota(4).evaluar(BUENA, null, "u", null, "TECNICO").aceptable());
    }

    @Test void elOrdenEsVaciaCortaLargaIgualNota() {
        assertEquals(PoliticaPassword.MSG_CORTA, conNota(0).evaluar("abc", "abc", "u", null, "TECNICO").mensaje());
        assertEquals(PoliticaPassword.MSG_LARGA, conNota(0).evaluar("a".repeat(65), "a".repeat(65), "u", null, "TECNICO").mensaje());
        assertEquals(PoliticaPassword.MSG_IGUAL, conNota(0).evaluar(BUENA, BUENA, "u", null, "TECNICO").mensaje());
    }

    @Test void pocaSeguraLlevaElPrimerConsejo() {
        var ev = conNota(2, "Añade otra palabra.", "Evita las fechas.").evaluar(BUENA, null, "u", null, "TECNICO");
        assertEquals(new EvaluacionPassword(2, false, "La contraseña es poco segura. Añade otra palabra."), ev);
        assertEquals("La contraseña es poco segura.", conNota(2).evaluar(BUENA, null, "u", null, "TECNICO").mensaje());
    }

    @Test void notaTresValeParaTecnicoYSupertecnicoPeroNoParaAdmin() {
        assertTrue(conNota(3).evaluar(BUENA, null, "u", null, "TECNICO").aceptable());
        assertTrue(conNota(3).evaluar(BUENA, null, "u", null, "SUPERTECNICO").aceptable());
        assertFalse(conNota(3).evaluar(BUENA, null, "u", null, "ADMIN").aceptable());
        assertTrue(conNota(4).evaluar(BUENA, null, "u", null, "ADMIN").aceptable());
        assertNull(conNota(4).evaluar(BUENA, null, "u", null, "ADMIN").mensaje());
    }

    @Test void penalizaUsuarioTecnicoPalabrasGenericasYPropias() {
        List<List<String>> palabras = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { palabras.add(w); return new MedidorFuerza.Medida(4, List.of()); },
                " MarcaUno, otra ,, ");
        politica.evaluar(BUENA, null, "Usuario-A", "Juan Pérez", "TECNICO");
        List<String> w = palabras.get(0);
        assertTrue(w.containsAll(List.of("usuario-a", "juan pérez", "juan", "pérez", "marcauno", "otra", "taller", "reparaciones")),
                () -> "palabras: " + w);
        assertFalse(w.contains(""));
    }

    @Test void sinTecnicoNiPalabrasPropiasTambienFunciona() {
        List<List<String>> palabras = new ArrayList<>();
        var politica = new PoliticaPassword((p, w) -> { palabras.add(w); return new MedidorFuerza.Medida(4, List.of()); }, "");
        assertTrue(politica.evaluar(BUENA, null, "admin", null, "ADMIN").aceptable());
        assertTrue(palabras.get(0).contains("admin"));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

```bash
mvn -q -Dtest=PoliticaPasswordTest test 2>&1 | tail -15
```

Esperado: error de compilación (`PoliticaPassword` y `EvaluacionPassword` no existen).

- [ ] **Step 3: Implementación**

`model/EvaluacionPassword.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de comprobar una contraseña propuesta: la nota de 0 a 4 (la barra de la web), si se acepta y, si no, el
 *  primer motivo, con el mismo texto que el 422 al guardarla. */
public record EvaluacionPassword(int nota, boolean aceptable, @Schema(nullable = true) String mensaje) {}
```

`security/PoliticaPassword.java`:

```java
package com.reparaciones.servidor.security;

import com.reparaciones.servidor.model.EvaluacionPassword;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La regla de las contraseñas que elige una persona (alta ya no: la genera el servidor). La aplican el cambio de
 * contraseña (normal y obligatorio) al guardar y {@code /api/auth/evaluar-password} mientras se escribe, así que la barra
 * de la web y el rechazo al guardar no pueden discrepar. Comprueba en orden y se para en la primera que falla: vacía,
 * menos de 10 caracteres, más de 64 o de 72 bytes (BCrypt solo usa los 72 primeros), igual que la actual y nota por
 * debajo del umbral del rol (3, o 4 para ADMIN). La nota se calcula siempre que se pueda, para que la barra la enseñe
 * aunque falle otra regla.
 */
@Component
public class PoliticaPassword {

    public static final int MIN_CARACTERES    = 10;
    public static final int MAX_CARACTERES    = 64;
    public static final int MAX_BYTES         = 72;
    public static final int NOTA_MINIMA       = 3;
    public static final int NOTA_MINIMA_ADMIN = 4;
    /** Por encima ni se mide: ya es demasiado larga y medir cadenas enormes solo gasta CPU. */
    private static final int MAX_A_MEDIR = 200;

    public static final String MSG_RELLENA     = "Rellena todos los campos.";
    public static final String MSG_CORTA       = "La contraseña debe tener al menos 10 caracteres.";
    public static final String MSG_LARGA       = "La contraseña es demasiado larga (máximo 64 caracteres).";
    public static final String MSG_IGUAL       = "La nueva contraseña tiene que ser distinta de la actual.";
    public static final String MSG_POCO_SEGURA = "La contraseña es poco segura.";

    /** Palabras del oficio y de contraseñas típicas en español que el diccionario (en inglés) de zxcvbn no conoce. Las
     *  propias de la empresa no van aquí (el repo es público): llegan por {@code politica.password.palabras-propias}. */
    static final List<String> PALABRAS_GENERICAS = List.of(
            "taller", "reparaciones", "reparacion", "reparación", "tecnico", "técnico", "usuario", "admin",
            "administrador", "contraseña", "contrasena", "clave", "password", "iphone", "apple", "samsung", "movil",
            "móvil", "telefono", "teléfono", "pantalla", "bateria", "batería", "tienda", "hola", "teamo", "madrid",
            "barcelona", "españa", "espana", "futbol", "fútbol", "qwerty");

    private final MedidorFuerza medidor;
    private final List<String> palabrasPropias;

    public PoliticaPassword(MedidorFuerza medidor,
                            @Value("${politica.password.palabras-propias:}") String palabrasPropias) {
        this.medidor = medidor;
        this.palabrasPropias = Arrays.stream(palabrasPropias == null ? new String[0] : palabrasPropias.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * @param actual        la contraseña actual escrita por la persona, o null si no se conoce (la barra)
     * @param nombreTecnico nombre visible del técnico, o null si la cuenta no tiene técnico
     * @param rol           ADMIN, SUPERTECNICO o TECNICO
     */
    public EvaluacionPassword evaluar(String password, String actual, String nombreUsuario, String nombreTecnico,
                                      String rol) {
        if (password == null || password.isEmpty()) return new EvaluacionPassword(0, false, MSG_RELLENA);

        MedidorFuerza.Medida medida = password.length() > MAX_A_MEDIR
                ? new MedidorFuerza.Medida(0, List.of())
                : medidor.medir(password, palabras(nombreUsuario, nombreTecnico));
        int nota = medida.nota();

        String mensaje;
        if (password.length() < MIN_CARACTERES) {
            mensaje = MSG_CORTA;
        } else if (password.length() > MAX_CARACTERES
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            mensaje = MSG_LARGA;
        } else if (actual != null && password.equals(actual)) {
            mensaje = MSG_IGUAL;
        } else if (nota < umbral(rol)) {
            mensaje = medida.consejos().isEmpty() ? MSG_POCO_SEGURA : MSG_POCO_SEGURA + " " + medida.consejos().get(0);
        } else {
            mensaje = null;
        }
        return new EvaluacionPassword(nota, mensaje == null, mensaje);
    }

    private static int umbral(String rol) {
        return "ADMIN".equals(rol) ? NOTA_MINIMA_ADMIN : NOTA_MINIMA;
    }

    /** Lo que zxcvbn trata como "datos del usuario": el nombre de usuario, el del técnico y cada una de sus palabras, las
     *  genéricas y las propias. En minúsculas y sin repetir. */
    private List<String> palabras(String nombreUsuario, String nombreTecnico) {
        Set<String> todas = new LinkedHashSet<>();
        anadir(todas, nombreUsuario);
        anadir(todas, nombreTecnico);
        if (nombreTecnico != null) {
            for (String trozo : nombreTecnico.trim().split("\\s+")) {
                if (trozo.length() >= 3) anadir(todas, trozo);
            }
        }
        todas.addAll(PALABRAS_GENERICAS);
        todas.addAll(palabrasPropias);
        return new ArrayList<>(todas);
    }

    private static void anadir(Set<String> destino, String palabra) {
        if (palabra == null) return;
        String limpia = palabra.trim().toLowerCase(Locale.ROOT);
        if (!limpia.isEmpty()) destino.add(limpia);
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

```bash
mvn -q -Dtest='PoliticaPasswordTest,MedidorZxcvbnTest' test 2>&1 | tail -15
```

Esperado: 17 tests, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/EvaluacionPassword.java src/main/java/com/reparaciones/servidor/security/PoliticaPassword.java src/test/java/com/reparaciones/servidor/security/PoliticaPasswordTest.java
git commit -m "feat: regla unica de contrasenas con longitud, distinta de la actual y nota minima por rol"
```

---

### Task 3: `cambiar-password` aplica la política

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/AuthController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java:43-47`
- Modify (tests): `controller/AuthControllerCambiarPasswordTest.java`, `controller/AuthControllerTest.java`, `controller/AuthControllerIntentosTest.java`, `security/JwtAuthFilterEstadoTest.java` y cualquier otro test que envíe una contraseña nueva a `cambiar-password`.

**Interfaces:**
- Consumes: `PoliticaPassword.evaluar(...)`, `EvaluacionPassword` (Task 2).
- Produces: constructor `AuthController(AuthenticationManager, JwtUtil, LogDAO, UsuarioDAO, IntentosFallidos, EstadoUsuarioService, PoliticaPassword)` y el método privado `String nombreTecnico(UsuarioPrincipal)` que reutiliza la Task 4.

- [ ] **Step 1: Tests que fallan en `AuthControllerCambiarPasswordTest`**

Cambiar la construcción del controlador y el helper `falla422` (la política consulta el nombre del técnico, así que el DAO ya no queda intacto; lo que importa es que no cambia la contraseña ni registra):

```java
    private final List<List<String>> palabrasMedidas = new ArrayList<>();
    private int notaFija = 4;
    private final PoliticaPassword politica = new PoliticaPassword(
            (p, w) -> { palabrasMedidas.add(w); return new MedidorFuerza.Medida(notaFija, List.of("Añade otra palabra.")); }, "");
    private final AuthController ctl = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class),
            logDao, usuarioDao, new IntentosFallidos(), estadoUsuario, politica);

    /** Un 422 de validación no cambia la contraseña ni registra log. */
    private String falla422(AuthController.CambiarPasswordRequest req) {
        return falla422(usuario, req);
    }

    private String falla422(UsuarioPrincipal quien, AuthController.CambiarPasswordRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.cambiarPassword(quien, req));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verify(usuarioDao, never()).cambiarPassword(anyInt(), any(), any());
        verifyNoInteractions(logDao);
        return e.getReason();
    }
```

(imports: `com.reparaciones.servidor.security.MedidorFuerza`, `com.reparaciones.servidor.security.PoliticaPassword`, `java.util.ArrayList`, `java.util.List`, `static org.mockito.ArgumentMatchers.any`, `static org.mockito.ArgumentMatchers.anyInt`).

En todos los tests del fichero, sustituir la contraseña nueva `"nueva123"` por `"nueva-larga-123"`. Sustituir estos tres tests:

```java
    @Test void nuevaDeMenosDeDiezEs422() {
        assertEquals("La contraseña debe tener al menos 10 caracteres.", falla422(cambio("secreta1", "123456789")));
    }

    @Test void elOrdenEsCamposYDespuesLongitud() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "123")));
    }

    /** Sin trim: diez espacios son una contraseña de 10 caracteres (la nota la pone el medidor). */
    @Test void losEspaciosCuentanComoCaracteres() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "          "));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "          ");
    }
```

Y añadir:

```java
    /** Un rechazo por la regla no es un fallo de la contraseña actual: ni registra CAMBIAR_PASSWORD_FALLIDO ni gasta
     *  intentos del freno (seis rechazos seguidos y una buena después entra sin 429). */
    @Test void unaPocoSeguraEs422ConElConsejoYSinGastarIntentos() {
        notaFija = 2;
        assertEquals("La contraseña es poco segura. Añade otra palabra.", falla422(cambio("secreta1", "nueva-larga-123")));
        for (int i = 0; i < 5; i++) {
            assertThrows(ResponseStatusException.class, () -> ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123")));
        }
        notaFija = 4;
        assertEquals(204, ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123")).getStatusCode().value());
    }

    @Test void igualALaActualEs422() {
        assertEquals("La nueva contraseña tiene que ser distinta de la actual.",
                falla422(cambio("misma-de-antes-1", "misma-de-antes-1")));
    }

    @Test void elAdministradorNecesitaNotaCuatro() {
        notaFija = 3;
        UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "", "ADMIN", null);
        assertEquals("La contraseña es poco segura. Añade otra palabra.", falla422(admin, cambio("secreta1", "nueva-larga-123")));
    }

    @Test void elNombreDelTecnicoPenaliza() {
        when(usuarioDao.getNombreByIdTec(4)).thenReturn("Tecnico Prueba");
        ctl.cambiarPassword(usuario, cambio("secreta1", "nueva-larga-123"));
        assertTrue(palabrasMedidas.get(0).containsAll(List.of("usuario-a", "tecnico prueba", "prueba")));
    }
```

(imports `static org.junit.jupiter.api.Assertions.assertTrue`).

- [ ] **Step 2: Ejecutar y ver que falla**

```bash
mvn -q -Dtest=AuthControllerCambiarPasswordTest test 2>&1 | tail -15
```

Esperado: error de compilación (constructor de 7 argumentos).

- [ ] **Step 3: Implementación**

`ValidacionUsuarios.validarCambioPassword` deja solo los campos vacíos (la longitud la pone la política):

```java
    /** Cambiar contraseña: alguna vacía o ausente → "Rellena todos los campos.". El resto de reglas de la nueva las
     *  aplica {@link com.reparaciones.servidor.security.PoliticaPassword}. La confirmación no llega al servidor. */
    static void validarCambioPassword(String passwordActual, String passwordNueva) {
        if (vacio(passwordActual) || vacio(passwordNueva)) throw regla(MSG_RELLENA);
    }
```

(`MSG_PASSWORD_CORTA` sigue en uso por `validarAlta` hasta la Task 5.)

`AuthController`: añadir el campo y el parámetro del constructor:

```java
    private final PoliticaPassword      politica;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil,
                          LogDAO logDao, UsuarioDAO usuarioDao, IntentosFallidos intentos,
                          EstadoUsuarioService estadoUsuario, PoliticaPassword politica) {
        // ... asignaciones existentes ...
        this.politica      = politica;
    }
```

En `cambiarPassword`, justo después de `ValidacionUsuarios.validarCambioPassword(...)`:

```java
        // La regla de la nueva antes de comprobar la actual: un rechazo por la regla no es un intento fallido.
        EvaluacionPassword evaluacion = politica.evaluar(req.passwordNueva(), req.passwordActual(),
                principal.getUsername(), nombreTecnico(principal), principal.getRol());
        if (!evaluacion.aceptable()) throw ValidacionUsuarios.regla(evaluacion.mensaje());
```

Y el método:

```java
    /** Nombre visible del técnico de la sesión, para que la regla lo penalice; null sin técnico o si no se encuentra. */
    private String nombreTecnico(UsuarioPrincipal principal) {
        if (principal.getIdTec() == null) return null;
        try {
            return usuarioDao.getNombreByIdTec(principal.getIdTec());
        } catch (org.springframework.dao.DataAccessException e) {
            return null;
        }
    }
```

(imports `com.reparaciones.servidor.model.EvaluacionPassword`, `com.reparaciones.servidor.security.PoliticaPassword`.)

- [ ] **Step 4: Adaptar el resto de tests**

```bash
grep -rln "new AuthController(\|cambiar-password\|CambiarPasswordRequest" src/test/java
```

- En `AuthControllerTest` y `AuthControllerIntentosTest`: añadir el séptimo argumento `new PoliticaPassword((p, w) -> new MedidorFuerza.Medida(4, List.of()), "")` y alargar a ≥ 10 caracteres cualquier contraseña **nueva** de `cambiarPassword` (p. ej. `"nueva123"` → `"nueva-larga-123"`).
- En `JwtAuthFilterEstadoTest` (contexto real, medidor real): en `conPasswordTemporalCambiarPasswordSiLlega`, el cuerpo pasa a `{"passwordActual":"secreta1","passwordNueva":"tortuga violeta lampara nube 47"}`.
- Cualquier otro test de MockMvc que haga `PATCH /api/auth/cambiar-password` esperando 204: la misma frase.

- [ ] **Step 5: Ejecutar la suite**

```bash
mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos.

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: cambiar la contrasena exige la regla nueva y el administrador una muy segura"
```

---

### Task 4: Endpoint `evaluar-password`, también con la contraseña temporal

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/AuthController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/security/JwtAuthFilter.java:83-91`
- Test: `controller/AuthControllerEvaluarPasswordTest.java` (nuevo), `security/JwtAuthFilterEstadoTest.java`

**Interfaces:**
- Consumes: `PoliticaPassword`, `nombreTecnico(UsuarioPrincipal)` (Task 3).
- Produces: `POST /api/auth/evaluar-password`, cuerpo `AuthEvaluarPasswordRequest {password}`, respuesta `EvaluacionPassword {nota, aceptable, mensaje}`. La web (Task 8) la llama con `api.POST('/api/auth/evaluar-password', { body: { password }, signal })`.

- [ ] **Step 1: Tests que fallan**

`AuthControllerEvaluarPasswordTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.EvaluacionPassword;
import com.reparaciones.servidor.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** POST /api/auth/evaluar-password: la misma regla que al guardar, con el usuario y el rol de la sesión, sin escribir. */
class AuthControllerEvaluarPasswordTest {

    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final AuthController ctl = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class),
            logDao, usuarioDao, new IntentosFallidos(), mock(EstadoUsuarioService.class),
            new PoliticaPassword((p, w) -> new MedidorFuerza.Medida(3, List.of("Añade otra palabra.")), ""));

    @Test void devuelveLaNotaYSiSeAceptaSegunElRol() {
        var tecnico = new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4);
        var admin = new UsuarioPrincipal(1, "admin", "", "ADMIN", null);
        assertEquals(new EvaluacionPassword(3, true, null),
                ctl.evaluarPassword(tecnico, new AuthController.EvaluarPasswordRequest("nueva-larga-123")));
        assertEquals(new EvaluacionPassword(3, false, "La contraseña es poco segura. Añade otra palabra."),
                ctl.evaluarPassword(admin, new AuthController.EvaluarPasswordRequest("nueva-larga-123")));
    }

    @Test void noEscribeNiRegistraNada() {
        ctl.evaluarPassword(new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4),
                new AuthController.EvaluarPasswordRequest("nueva-larga-123"));
        verifyNoInteractions(logDao);
        verify(usuarioDao, never()).cambiarPassword(anyInt(), any(), any());
    }

    @Test void vaciaDevuelveRellenar() {
        assertEquals(new EvaluacionPassword(0, false, "Rellena todos los campos."),
                ctl.evaluarPassword(new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4),
                        new AuthController.EvaluarPasswordRequest(null)));
    }
}
```

En `JwtAuthFilterEstadoTest`, junto a `conPasswordTemporalCambiarPasswordSiLlega`:

```java
    @Test void conPasswordTemporalEvaluarPasswordSiLlega() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        when(estado.tienePasswordTemporal(anyInt())).thenReturn(true);
        mvc.perform(post("/api/auth/evaluar-password")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"tortuga violeta lampara nube 47\"}"))
           .andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.aceptable").value(true));
    }

    @Test void conPasswordTemporalOtraRutaDeAuthSigueSiendo403() throws Exception {
        when(estado.estaOperativo(anyInt())).thenReturn(true);
        when(estado.tienePasswordTemporal(anyInt())).thenReturn(true);
        mvc.perform(get("/api/reparaciones/historial").header("Authorization", token()))
           .andExpect(status().isForbidden());
    }

    @Test void evaluarPasswordSinSesionNoResponde() throws Exception {
        mvc.perform(post("/api/auth/evaluar-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"tortuga violeta lampara nube 47\"}"))
           .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
mvn -q -Dtest='AuthControllerEvaluarPasswordTest,JwtAuthFilterEstadoTest' test 2>&1 | tail -20
```

Esperado: error de compilación (`evaluarPassword` no existe).

- [ ] **Step 3: Implementación**

En `AuthController`, después de `cambiarPassword`:

```java
    /**
     * Nota de una contraseña propuesta, para la barra de la web mientras se escribe: la misma regla que al guardar,
     * con el usuario, el técnico y el rol de la sesión (nunca del cuerpo). No conoce la actual, así que no comprueba
     * que sea distinta (eso lo hace el guardado). No escribe, no registra actividad y no deja la contraseña en ningún
     * log. Se permite también con la contraseña temporal (JwtAuthFilter), porque se usa en el cambio obligatorio.
     */
    @PostMapping("/evaluar-password")
    public EvaluacionPassword evaluarPassword(@AuthenticationPrincipal UsuarioPrincipal principal,
                                              @RequestBody EvaluarPasswordRequest req) {
        return politica.evaluar(req.password(), null, principal.getUsername(), nombreTecnico(principal),
                principal.getRol());
    }
```

y el record, junto a los otros:

```java
    record EvaluarPasswordRequest(String password) {}
```

En `JwtAuthFilter`, sustituir el bloque de la contraseña temporal:

```java
        // Con la contraseña marcada como temporal solo se puede cambiarla (y pedir la nota de la nueva para la barra):
        // el resto de rutas responden 403, no 401, porque la sesión sigue siendo válida y solo falta ese paso
        // (spec sp7b §5.4, arreglo E3-2).
        String ruta = request.getRequestURI();
        boolean permitidaConTemporal =
                ("PATCH".equals(request.getMethod()) && "/api/auth/cambiar-password".equals(ruta))
                || ("POST".equals(request.getMethod()) && "/api/auth/evaluar-password".equals(ruta));
        if (!permitidaConTemporal && estadoUsuario.tienePasswordTemporal(idUsu)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, MSG_PASSWORD_TEMPORAL);
            return;
        }
```

- [ ] **Step 4: Ejecutar la suite**

```bash
mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: endpoint que puntua una contrasena propuesta, tambien durante el cambio obligatorio"
```

---

### Task 5: El alta genera la contraseña temporal

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/UsuarioController.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java`
- Modify (tests): `controller/UsuarioControllerTest.java`, `controller/IdempotenciaAltasControllerTest.java`

**Interfaces:**
- Consumes: `PasswordTemporal.generar()`, `ValorTexto` (existentes).
- Produces: `POST /api/usuarios/tecnicos` con cuerpo `UsuarioRegistrarTecnicoRequest {nombreTecnico, nombreUsuario, rol}` y respuesta **201 `ValorTexto {value: <temporal>}`**. `ValidacionUsuarios.validarAlta(String nombreTecnico, String nombreUsuario, String rol)`.

- [ ] **Step 1: Tests que fallan en `UsuarioControllerTest`**

Helper y tests del alta (sustituyen a los actuales de la sección "alta"):

```java
    private static UsuarioController.RegistrarTecnicoRequest alta(String tecnico, String usuario, String rol) {
        return new UsuarioController.RegistrarTecnicoRequest(tecnico, usuario, rol);
    }

    @Test void altaConCampoVacioEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", "usuario-a", "TECNICO")));
    }

    @Test void altaConNombresEnBlancoONulosEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("   ", "usuario-a", "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", null, "TECNICO")));
    }

    @Test void altaConUsuarioDeMasDe50Es422() {
        assertEquals("El nombre de usuario no puede superar 50 caracteres.",
                falla422(alta("tecnico-a", "u".repeat(51), "TECNICO")));
    }

    @Test void altaConTecnicoDeMasDe100Es422() {
        assertEquals("El nombre del técnico no puede superar 100 caracteres.",
                falla422(alta("t".repeat(101), "usuario-a", "TECNICO")));
    }

    @Test void altaConRolNoPermitidoEs422EnVezDe400() {
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "ADMIN")));
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "")));
    }

    @Test void elOrdenEsCamposCincuentaCienRol() {
        String largo51 = "u".repeat(51);
        String largo101 = "t".repeat(101);
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", largo51, "ADMIN")));
        assertEquals("El nombre de usuario no puede superar 50 caracteres.", falla422(alta(largo101, largo51, "ADMIN")));
        assertEquals("El nombre del técnico no puede superar 100 caracteres.", falla422(alta(largo101, "usuario-a", "ADMIN")));
    }

    @Test void losLimitesExactosSonValidos() {
        String usuario50 = "u".repeat(50);
        String tecnico100 = "t".repeat(100);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" " + tecnico100 + " ", " " + usuario50 + " ", "TECNICO"), admin, null);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico(eq(tecnico100), eq(usuario50), anyString(), eq("TECNICO"));
    }

    /** La contraseña la genera el servidor, se guarda (marcada temporal por el DAO) y se devuelve una sola vez. */
    @Test void altaValidaGeneraLaTemporalLaDevuelveYRegistraLogSinElla() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("  tecnico-a ", " usuario-a  ", "SUPERTECNICO"), admin, null);
        assertEquals(201, resp.getStatusCode().value());
        ArgumentCaptor<String> guardada = ArgumentCaptor.forClass(String.class);
        verify(dao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), guardada.capture(), eq("SUPERTECNICO"));
        assertEquals(10, guardada.getValue().length());
        assertEquals(new ValorTexto(guardada.getValue()), resp.getBody());
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: SUPERTECNICO, TECNICO: tecnico-a");
    }

    @Test void dosAltasRecibenTemporalesDistintas() {
        var r1 = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "TECNICO"), admin, null);
        var r2 = ctl.registrarTecnico(alta("tecnico-b", "usuario-b", "TECNICO"), admin, null);
        assertNotEquals(r1.getBody(), r2.getBody());
    }

    @Test void altaSinRolGuardaTecnico() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", null), admin, null);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"));
    }
```

En los tres tests de 409 (`tecnicoDuplicado…`, `usuarioDuplicado…`, `violacionDeIntegridad…`), quitar el argumento de contraseña de `alta(...)` y, en `violacionDeIntegridad…`, el `doThrow` pasa a `.when(dao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"))`. **Borrar** `altaConPasswordCortaEs422`, `elOrdenEsCamposSeisCincuentaCienRol`, `altaValidaGuardaLosNombresRecortadosYRegistraLog` (sustituido arriba) y `laHuellaDeLaContrasenaDependeDeLaClaveDelProceso`. Imports: `org.mockito.ArgumentCaptor`, `com.reparaciones.servidor.model.ValorTexto`, `static org.mockito.ArgumentMatchers.eq`.

En `IdempotenciaAltasControllerTest`, sección técnicos:

```java
    private static final String CUERPO_TECNICO =
            "{\"nombreTecnico\":\"tecnico-a\",\"nombreUsuario\":\"usuario-a\",\"rol\":\"TECNICO\"}";
```

- Cada `verify(usuarioDao, times(n)).registrarTecnico("tecnico-a", "usuario-a", "secreta1", "TECNICO")` pasa a `verify(usuarioDao, times(n)).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), anyString(), eq("TECNICO"))`.
- `tecnicoConLaMismaClaveYOtroCuerpoEs422`: el otro cuerpo pasa a `"{\"nombreTecnico\":\"tecnico-a\",\"nombreUsuario\":\"usuario-a\",\"rol\":\"SUPERTECNICO\"}"`.
- `tecnicoUn422DeValidacionNoSeRecuerdaConLaClave`: el primer cuerpo pasa a `"{\"nombreTecnico\":\"\",\"nombreUsuario\":\"usuario-a\",\"rol\":\"TECNICO\"}"` y el mensaje esperado a `"Todos los campos son obligatorios."`.
- Añadir:

```java
    /** El reintento con la misma clave devuelve la MISMA temporal (si la respuesta se perdió, es la única forma de tenerla). */
    @Test void tecnicoElReintentoDevuelveLaMismaTemporal() throws Exception {
        String clave = claveNueva();
        String primera = enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getContentAsString();
        String segunda = enviar(TECNICOS, admin(), CUERPO_TECNICO, clave).getContentAsString();
        assertTrue(primera.matches("\\{\"value\":\"[A-Za-z0-9]{10}\"\\}"), primera);
        assertEquals(primera, segunda);
    }

    /** Un cliente viejo que aún mande "password" no la impone: se ignora y la temporal es otra. */
    @Test void tecnicoUnaPasswordEnElCuerpoSeIgnora() throws Exception {
        var resp = enviar(TECNICOS, admin(),
                "{\"nombreTecnico\":\"tecnico-a\",\"nombreUsuario\":\"usuario-a\",\"password\":\"impuesta-1234\",\"rol\":\"TECNICO\"}", null);
        assertEquals(201, resp.getStatus());
        verify(usuarioDao).registrarTecnico(eq("tecnico-a"), eq("usuario-a"), argThat(p -> !"impuesta-1234".equals(p)), eq("TECNICO"));
    }
```

(imports `static org.mockito.ArgumentMatchers.anyString`, `static org.mockito.ArgumentMatchers.argThat`, `static org.junit.jupiter.api.Assertions.assertTrue` si faltan.)

- [ ] **Step 2: Ejecutar y ver que fallan**

```bash
mvn -q -Dtest='UsuarioControllerTest,IdempotenciaAltasControllerTest' test 2>&1 | tail -20
```

Esperado: error de compilación (`RegistrarTecnicoRequest` de 3 argumentos).

- [ ] **Step 3: Implementación**

`ValidacionUsuarios`: `validarAlta` sin contraseña, y fuera `MSG_PASSWORD_CORTA` (ya no la usa nadie):

```java
    /** Orden del alta: campos → 50 → 100 → rol; para en el primero. Los nombres llegan ya recortados (null si venían
     *  null). La contraseña ya no llega: la genera el servidor. {@code rol} null vale TECNICO. */
    static void validarAlta(String nombreTecnico, String nombreUsuario, String rol) {
        if (vacio(nombreTecnico) || vacio(nombreUsuario)) throw regla(MSG_CAMPOS);
        if (nombreUsuario.length() > 50) throw regla(MSG_USUARIO_LARGO);
        if (nombreTecnico.length() > 100) throw regla(MSG_TECNICO_LARGO);
        if (rol != null && !ROLES.contains(rol)) throw regla(MSG_ROL);
    }
```

`UsuarioController.registrarTecnico`:

```java
    /** Alta de usuario y técnico (spec 6 §4.1; spec política de contraseñas §3.2): los nombres se recortan antes de
     *  validar y se guardan recortados; los 422 de {@link ValidacionUsuarios#validarAlta} van antes que los dos 409 de
     *  duplicado. La contraseña la genera el servidor como en "Restablecer", el DAO la deja marcada como temporal y se
     *  devuelve una sola vez. Con clave de reintento, la respuesta guardada (con esa temporal) vive solo en la memoria
     *  de este proceso hasta que caduca: es lo que permite recuperarla si la primera respuesta se perdió. */
    @PostMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = ValorTexto.class))),
        @ApiResponse(responseCode = "409", content = @Content),
        @ApiResponse(responseCode = "422", content = @Content)
    })
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal,
                                               @RequestHeader(value = RegistroIdempotencia.CABECERA, required = false)
                                               String claveIdempotencia) {
        String nombreTecnico = recortar(req.nombreTecnico());
        String nombreUsuario = recortar(req.nombreUsuario());
        ValidacionUsuarios.validarAlta(nombreTecnico, nombreUsuario, req.rol());
        String rol = req.rol() != null ? req.rol() : "TECNICO";
        var peticion = new PeticionAltaTecnico(nombreTecnico, nombreUsuario, rol);
        try {
            return idempotencia.ejecutar(principal.getIdUsu(), OP_ALTA, claveIdempotencia, peticion,
                    () -> {
                        if (dao.existeNombreTecnico(nombreTecnico)) {
                            throw new Duplicado("Ya existe un técnico con ese nombre.");
                        }
                        if (dao.existeNombreUsuario(nombreUsuario)) {
                            throw new Duplicado("Ese nombre de usuario ya existe.");
                        }
                        String password = PasswordTemporal.generar();
                        try {
                            dao.registrarTecnico(nombreTecnico, nombreUsuario, password, rol);
                        } catch (DataIntegrityViolationException e) {
                            throw new Duplicado("Ese nombre de usuario ya existe.");
                        }
                        return ResponseEntity.status(HttpStatus.CREATED).body(new ValorTexto(password));
                    },
                    ignorado -> logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                            "NOMBRE_USUARIO: " + nombreUsuario + ", ROL: " + rol + ", TECNICO: " + nombreTecnico));
        } catch (Duplicado d) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", d.getMessage()));
        }
    }

    /** Lo que identifica un alta de técnico en el registro de reintentos. */
    private record PeticionAltaTecnico(String nombreTecnico, String nombreUsuario, String rol) {}
```

Borrar `claveHuella` (campo y asignación del constructor), `claveHuellaNueva()`, `huella(...)` y los imports que se queden sin uso (`Mac`, `SecretKeySpec`, `StandardCharsets`, `GeneralSecurityException`, `SecureRandom`, `HexFormat`). El record de la petición:

```java
    /** Package-private (no private) para que los tests lo construyan; springdoc lo publica con el mismo nombre. Un
     *  "password" en el cuerpo (cliente antiguo) se ignora: la contraseña del alta la genera el servidor. */
    record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String rol) {}
```

- [ ] **Step 4: Ejecutar la suite**

```bash
mvn -q test 2>&1 | tail -20
```

Esperado: 0 fallos. Si `UsuarioDAORegistrarTecnicoTest` u otro test construye `RegistrarTecnicoRequest`, adaptarlo igual.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: el alta genera una contrasena temporal y la devuelve una sola vez"
```

---

### Task 6: Cierre del servidor

**Files:** ninguno nuevo (comprobación y contrato).

- [ ] **Step 1: Suite completa y contrato**

```bash
mvn -q test 2>&1 | tail -20
ls -la target/openapi.json
node -e "const d=require('./target/openapi.json');console.log(!!d.paths['/api/auth/evaluar-password'], Object.keys(d.components.schemas).filter(s=>/Evaluacion|EvaluarPassword|RegistrarTecnico/.test(s)))"
node -e "const d=require('./target/openapi.json');console.log(JSON.stringify(d.components.schemas.UsuarioRegistrarTecnicoRequest.properties))"
```

Esperado: 0 fallos; `true` y los esquemas `EvaluacionPassword`, `AuthEvaluarPasswordRequest`, `UsuarioRegistrarTecnicoRequest`; este último **sin** `password`.

- [ ] **Step 2: Revisión de la rama**

`superpowers:requesting-code-review` de `feature/politica-contrasenas` del servidor, con foco en: que ninguna ruta registre la contraseña (ni `evaluar-password`, ni el alta, ni los 422), que la regla se aplica antes de comprobar la actual y no gasta intentos, que con la temporal solo pasan las dos rutas, y que el reintento del alta devuelve la misma temporal.

El merge, el push y el tag van en la Task 12, cada uno con su OK.

---

## WEB

### Task 7: Tipos del contrato, handler por defecto y validación local nueva

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `gestion-reparaciones-web/src/shared/api/schema.d.ts` (regenerados)
- Modify: `gestion-reparaciones-web/src/test/server.ts`
- Modify: `gestion-reparaciones-web/src/modules/gestion/cuenta/validacion.ts`
- Test: `gestion-reparaciones-web/src/modules/gestion/cuenta/validacion.test.ts` y los tests que usan contraseñas nuevas de menos de 10 caracteres.

**Interfaces:**
- Consumes: el contrato de la Task 6 (`target/openapi.json` del servidor).
- Produces: `validarCambioPassword(actual, nueva, confirmar): string | null` con las reglas nuevas; constantes `MSG_RELLENA`, `MSG_PASSWORD_CORTA`, `MSG_PASSWORD_LARGA`, `MSG_IGUAL_ACTUAL`, `MSG_NUEVAS_NO_COINCIDEN`; tipo `components['schemas']['EvaluacionPassword']`.

- [ ] **Step 1: Rama y tipos**

```bash
cd gestion-reparaciones-web && git checkout -b feature/politica-contrasenas
node -e "const fs=require('fs');const j=JSON.parse(fs.readFileSync('../gestion-reparaciones-servidor/target/openapi.json','utf8'));fs.writeFileSync('api/openapi.json',JSON.stringify(j,null,2)+'\n')"
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
grep -n "evaluar-password\|EvaluacionPassword\|UsuarioRegistrarTecnicoRequest" src/shared/api/schema.d.ts | head
```

Esperado: cambian solo esos dos ficheros; aparecen la ruta y los esquemas nuevos. Si el `diff --stat` del `openapi.json` es de miles de líneas por formato y no por contenido, comparar con `node -e` las claves de `paths` antes y después y seguir: el formato lo fija el `JSON.stringify` de arriba.

- [ ] **Step 2: Handler por defecto para la barra en los tests**

En `src/test/server.ts`, añadir a `setupServer(...)` (y al comentario, una línea: "la nota de la contraseña: «Muy segura», para que los formularios de contraseña de cualquier test se puedan guardar"):

```ts
  http.post('*/api/auth/evaluar-password', () => HttpResponse.json({ nota: 4, aceptable: true, mensaje: null })),
```

- [ ] **Step 3: Tests que fallan en `validacion.test.ts`**

Sustituir los casos de longitud por:

```ts
import { describe, expect, it } from 'vitest'
import { MSG_IGUAL_ACTUAL, MSG_NUEVAS_NO_COINCIDEN, MSG_PASSWORD_CORTA, MSG_PASSWORD_LARGA, MSG_RELLENA, validarCambioPassword } from './validacion'

describe('validarCambioPassword (regla 0.9.2)', () => {
  it('campos vacíos primero', () => {
    expect(validarCambioPassword('', 'nueva-larga-1', 'nueva-larga-1')).toBe(MSG_RELLENA)
    expect(validarCambioPassword('actual', '', '')).toBe(MSG_RELLENA)
  })
  it('menos de 10 caracteres', () => {
    expect(validarCambioPassword('actual', '123456789', '123456789')).toBe('La contraseña debe tener al menos 10 caracteres.')
    expect(MSG_PASSWORD_CORTA).toBe('La contraseña debe tener al menos 10 caracteres.')
  })
  it('más de 64 caracteres o más de 72 bytes', () => {
    expect(validarCambioPassword('actual', 'a'.repeat(65), 'a'.repeat(65))).toBe('La contraseña es demasiado larga (máximo 64 caracteres).')
    const conTildes = 'ñ'.repeat(37) + 'abc'
    expect(validarCambioPassword('actual', conTildes, conTildes)).toBe(MSG_PASSWORD_LARGA)
    expect(validarCambioPassword('actual', 'a'.repeat(64), 'a'.repeat(64))).toBeNull()
  })
  it('distinta de la actual', () => {
    expect(validarCambioPassword('misma-de-antes', 'misma-de-antes', 'misma-de-antes')).toBe('La nueva contraseña tiene que ser distinta de la actual.')
    expect(MSG_IGUAL_ACTUAL).toBe('La nueva contraseña tiene que ser distinta de la actual.')
  })
  it('la repetida tiene que coincidir, y es la última comprobación', () => {
    expect(validarCambioPassword('actual', 'nueva-larga-1', 'nueva-larga-2')).toBe(MSG_NUEVAS_NO_COINCIDEN)
    expect(validarCambioPassword('actual', '123', '456')).toBe(MSG_PASSWORD_CORTA)
  })
  it('sin trim: diez espacios valen como 10 caracteres', () => {
    expect(validarCambioPassword('actual', ' '.repeat(10), ' '.repeat(10))).toBeNull()
  })
})
```

```bash
npx vitest run src/modules/gestion/cuenta/validacion.test.ts 2>&1 | tail -15
```

Esperado: FAIL (no existen `MSG_PASSWORD_LARGA` ni `MSG_IGUAL_ACTUAL`; la longitud sigue en 6).

- [ ] **Step 4: Implementación**

`src/modules/gestion/cuenta/validacion.ts`:

```ts
/** Textos de CambiarPasswordController (`hotfix/0.16.3`, :84-95) y del Alert de éxito (:100-105), con la regla de la 0.9.2
 *  (mismos textos que PoliticaPassword del servidor, que es quien decide al guardar). */
export const MSG_RELLENA = 'Rellena todos los campos.'
export const MSG_PASSWORD_CORTA = 'La contraseña debe tener al menos 10 caracteres.'
export const MSG_PASSWORD_LARGA = 'La contraseña es demasiado larga (máximo 64 caracteres).'
export const MSG_IGUAL_ACTUAL = 'La nueva contraseña tiene que ser distinta de la actual.'
export const MSG_NUEVAS_NO_COINCIDEN = 'Las contraseñas nuevas no coinciden.'
/** Título por defecto del Alert INFORMATION con locale español; fijado con la captura del JavaFX el 2026-09-26. */
export const TITULO_EXITO = 'Mensaje'
export const MSG_EXITO = 'Contraseña cambiada correctamente.'

export const MIN_CARACTERES = 10
export const MAX_CARACTERES = 64
/** BCrypt solo usa los 72 primeros bytes. */
export const MAX_BYTES = 72

/** Sin trim ("   " cuenta como relleno), longitud en unidades UTF-16 como `String.length()` del servidor, comparación
 *  exacta. Orden: rellenos → 10 → 64/72 bytes → distinta de la actual → repetida igual. Para en el primer fallo; null =
 *  válido. La nota (la barra) la pone el servidor; esta validación solo evita viajes inútiles. */
export function validarCambioPassword(actual: string, nueva: string, confirmar: string): string | null {
  if (actual === '' || nueva === '' || confirmar === '') return MSG_RELLENA
  if (nueva.length < MIN_CARACTERES) return MSG_PASSWORD_CORTA
  if (nueva.length > MAX_CARACTERES || new TextEncoder().encode(nueva).length > MAX_BYTES) return MSG_PASSWORD_LARGA
  if (nueva === actual) return MSG_IGUAL_ACTUAL
  if (nueva !== confirmar) return MSG_NUEVAS_NO_COINCIDEN
  return null
}
```

- [ ] **Step 5: Adaptar los tests que usan contraseñas nuevas cortas**

```bash
grep -rln "nueva123\|secreta1\|al menos 6 caracteres" src --include=*.test.tsx --include=*.test.ts
```

En `CambiarPasswordDialog.test.tsx`, `app/cuenta/CambioObligatorioPage.test.tsx`, `modules/gestion/cuenta/api.test.tsx`, `app/shell/TopBar.test.tsx` y cualquier otro que salga: toda contraseña **nueva** de menos de 10 caracteres pasa a `nueva-larga-123`; los casos que esperaban `al menos 6 caracteres` pasan a esperar `MSG_PASSWORD_CORTA` con una nueva de 9 caracteres. Las contraseñas **actuales** y las del login no cambian. Los de `tecnicos/` se adaptan en la Task 10.

- [ ] **Step 6: Ejecutar**

```bash
npx vitest run src/modules/gestion/cuenta src/app 2>&1 | tail -15
npm run typecheck 2>&1 | tail -15
```

Esperado: verdes. El `typecheck` puede fallar **solo** en `modules/gestion/tecnicos` (el alta ya no lleva `password` en el contrato): se arregla en la Task 10. Cualquier otro error, arreglarlo aquí.

- [ ] **Step 7: Commit**

```bash
git add -A api src
git commit -m "feat: tipos del contrato 0.9.2 y validacion local con la regla nueva de contrasenas"
```

---

### Task 8: La barra, `MedidorPassword`

**Files:**
- Create: `gestion-reparaciones-web/src/modules/gestion/cuenta/medidor.ts`
- Create: `gestion-reparaciones-web/src/modules/gestion/cuenta/MedidorPassword.tsx`
- Test: `gestion-reparaciones-web/src/modules/gestion/cuenta/MedidorPassword.test.tsx`

**Interfaces:**
- Consumes: `api.POST('/api/auth/evaluar-password', ...)`, `EvaluacionPassword` (Task 7).
- Produces:
  - `type EstadoNota = { estado: 'vacio' } | { estado: 'comprobando' } | { estado: 'listo'; nota: number; aceptable: boolean; mensaje: string | null } | { estado: 'error' }`
  - `useNotaPassword(password: string): EstadoNota`
  - `bloqueaGuardar(e: EstadoNota): boolean` (true si `comprobando` o `listo && !aceptable`)
  - `<MedidorPassword estado={EstadoNota} esAdmin={boolean} />`
  - `PAUSA_MS = 300`, `ETIQUETAS_NOTA`, `AYUDA`, `AYUDA_ADMIN`, `MSG_NO_COMPROBADA`.

- [ ] **Step 1: Tests que fallan**

```tsx
import { act, render, renderHook, screen, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { AYUDA, AYUDA_ADMIN, bloqueaGuardar, MSG_NO_COMPROBADA, useNotaPassword } from './medidor'
import { MedidorPassword } from './MedidorPassword'

function registrarEvaluaciones(responder: (password: string) => Response | Promise<Response>) {
  const pedidas: string[] = []
  server.use(
    http.post('*/api/auth/evaluar-password', async ({ request }) => {
      const { password } = (await request.json()) as { password: string }
      pedidas.push(password)
      return responder(password)
    }),
  )
  return pedidas
}

describe('useNotaPassword', () => {
  it('vacía: no pide nada', async () => {
    const pedidas = registrarEvaluaciones(() => HttpResponse.json({ nota: 4, aceptable: true, mensaje: null }))
    const { result } = renderHook(() => useNotaPassword(''))
    expect(result.current).toEqual({ estado: 'vacio' })
    await new Promise((r) => setTimeout(r, 400))
    expect(pedidas).toHaveLength(0)
  })

  it('espera 0,3 s tras la última tecla y pide una sola vez', async () => {
    const pedidas = registrarEvaluaciones(() => HttpResponse.json({ nota: 2, aceptable: false, mensaje: 'La contraseña es poco segura.' }))
    const { result, rerender } = renderHook(({ p }) => useNotaPassword(p), { initialProps: { p: 'a' } })
    rerender({ p: 'ab' })
    rerender({ p: 'abc' })
    expect(result.current).toEqual({ estado: 'comprobando' })
    await waitFor(() => expect(result.current).toEqual({ estado: 'listo', nota: 2, aceptable: false, mensaje: 'La contraseña es poco segura.' }))
    expect(pedidas).toEqual(['abc'])
  })

  it('descarta una respuesta que llega tarde', async () => {
    registrarEvaluaciones(async (p) => {
      if (p === 'vieja-larga-1') await new Promise((r) => setTimeout(r, 300))
      return HttpResponse.json({ nota: p === 'vieja-larga-1' ? 0 : 4, aceptable: p !== 'vieja-larga-1', mensaje: null })
    })
    const { result, rerender } = renderHook(({ p }) => useNotaPassword(p), { initialProps: { p: 'vieja-larga-1' } })
    await new Promise((r) => setTimeout(r, 350))
    rerender({ p: 'nueva-larga-1' })
    await waitFor(() => expect(result.current).toMatchObject({ estado: 'listo', nota: 4 }))
    await new Promise((r) => setTimeout(r, 400))
    expect(result.current).toMatchObject({ estado: 'listo', nota: 4 })
  })

  it('si falla la consulta: error, que no bloquea', async () => {
    registrarEvaluaciones(() => new HttpResponse(null, { status: 500 }))
    const { result } = renderHook(() => useNotaPassword('nueva-larga-1'))
    await waitFor(() => expect(result.current).toEqual({ estado: 'error' }))
    expect(bloqueaGuardar(result.current)).toBe(false)
  })
})

describe('bloqueaGuardar', () => {
  it('bloquea mientras comprueba y si no se acepta; no bloquea vacía, aceptada ni con error', () => {
    expect(bloqueaGuardar({ estado: 'comprobando' })).toBe(true)
    expect(bloqueaGuardar({ estado: 'listo', nota: 2, aceptable: false, mensaje: 'x' })).toBe(true)
    expect(bloqueaGuardar({ estado: 'listo', nota: 3, aceptable: true, mensaje: null })).toBe(false)
    expect(bloqueaGuardar({ estado: 'vacio' })).toBe(false)
    expect(bloqueaGuardar({ estado: 'error' })).toBe(false)
  })
})

describe('MedidorPassword', () => {
  it('vacía: solo la ayuda, sin barra', () => {
    render(<MedidorPassword estado={{ estado: 'vacio' }} esAdmin={false} />)
    expect(screen.getByText(AYUDA)).toBeInTheDocument()
    expect(screen.queryByRole('meter')).toBeNull()
  })

  it('pinta la nota con su texto, los tramos encendidos y el mensaje', () => {
    render(<MedidorPassword estado={{ estado: 'listo', nota: 2, aceptable: false, mensaje: 'La contraseña es poco segura. Añade otra palabra.' }} esAdmin={false} />)
    const barra = screen.getByRole('meter', { name: 'Seguridad de la contraseña' })
    expect(barra).toHaveAttribute('aria-valuenow', '2')
    expect(barra).toHaveAttribute('aria-valuetext', 'Poco segura')
    expect(screen.getByText('Poco segura')).toBeInTheDocument()
    expect(barra.querySelectorAll('[data-encendido="true"]')).toHaveLength(3)
    expect(screen.getByText('La contraseña es poco segura. Añade otra palabra.')).toBeInTheDocument()
  })

  it.each([
    [0, 'Muy débil'], [1, 'Débil'], [2, 'Poco segura'], [3, 'Segura'], [4, 'Muy segura'],
  ])('nota %i → %s', (nota, texto) => {
    render(<MedidorPassword estado={{ estado: 'listo', nota, aceptable: nota >= 3, mensaje: null }} esAdmin={false} />)
    expect(screen.getByText(texto)).toBeInTheDocument()
  })

  it('al administrador le recuerda que se pide «Muy segura»', () => {
    render(<MedidorPassword estado={{ estado: 'vacio' }} esAdmin />)
    expect(screen.getByText(AYUDA_ADMIN)).toBeInTheDocument()
  })

  it('error: lo dice sin barra', () => {
    render(<MedidorPassword estado={{ estado: 'error' }} esAdmin={false} />)
    expect(screen.getByText(MSG_NO_COMPROBADA)).toBeInTheDocument()
    expect(screen.queryByRole('meter')).toBeNull()
  })

  it('comprobando: mantiene el hueco (sin barra ni texto de nota)', () => {
    const { container } = render(<MedidorPassword estado={{ estado: 'comprobando' }} esAdmin={false} />)
    expect(container.firstChild).toHaveClass('min-h-[52px]')
  })
})

void act
```

```bash
npx vitest run src/modules/gestion/cuenta/MedidorPassword.test.tsx 2>&1 | tail -15
```

Esperado: FAIL (no existen `medidor.ts` ni `MedidorPassword.tsx`).

- [ ] **Step 2: Implementación de `medidor.ts`**

```ts
import { useEffect, useState } from 'react'
import { api } from '@/shared/api/client'

/** Nota de una contraseña propuesta, de 0 a 4, según el servidor (la misma regla que al guardar: PoliticaPassword). */
export type EstadoNota =
  | { estado: 'vacio' }
  | { estado: 'comprobando' }
  | { estado: 'listo'; nota: number; aceptable: boolean; mensaje: string | null }
  | { estado: 'error' }

/** Pausa tras la última tecla antes de preguntar: una petición por pausa, no por letra. */
export const PAUSA_MS = 300

export const ETIQUETAS_NOTA = ['Muy débil', 'Débil', 'Poco segura', 'Segura', 'Muy segura'] as const
export const AYUDA = 'Mínimo 10 caracteres.'
export const AYUDA_ADMIN = 'Para el administrador se pide «Muy segura».'
export const MSG_NO_COMPROBADA = 'No se pudo comprobar'

/**
 * Pide al servidor la nota de `password` cuando deja de cambiar durante PAUSA_MS. Cada cambio cancela la consulta
 * anterior (AbortController), así que una respuesta vieja nunca pisa a la nueva. La contraseña solo viaja en el cuerpo de
 * esa petición: no se guarda en ninguna caché ni en el almacenamiento del navegador.
 */
export function useNotaPassword(password: string): EstadoNota {
  const [estado, setEstado] = useState<EstadoNota>({ estado: 'vacio' })

  useEffect(() => {
    if (password === '') {
      setEstado({ estado: 'vacio' })
      return
    }
    setEstado({ estado: 'comprobando' })
    const control = new AbortController()
    const temporizador = window.setTimeout(() => {
      api
        .POST('/api/auth/evaluar-password', { body: { password }, signal: control.signal })
        .then(({ data }) => {
          if (control.signal.aborted) return
          if (!data) setEstado({ estado: 'error' })
          else setEstado({ estado: 'listo', nota: data.nota, aceptable: data.aceptable, mensaje: data.mensaje ?? null })
        })
        .catch(() => {
          if (!control.signal.aborted) setEstado({ estado: 'error' })
        })
    }, PAUSA_MS)
    return () => {
      window.clearTimeout(temporizador)
      control.abort()
    }
  }, [password])

  return estado
}

/** "Guardar" espera a la nota y no deja guardar una que el servidor no acepta. Vacía no bloquea (al pulsar sale "Rellena
 *  todos los campos.") y un fallo de la consulta tampoco: al guardar decide el servidor. */
export function bloqueaGuardar(e: EstadoNota): boolean {
  return e.estado === 'comprobando' || (e.estado === 'listo' && !e.aceptable)
}
```

Nota para el implementador: si el lint (`react-hooks/set-state-in-effect`) se queja de los `setEstado` síncronos del efecto, usar el mismo comentario de desactivación que ya hay en `src/shared/ui/ConfirmDialog.tsx` (`// eslint-disable-next-line react-hooks/set-state-in-effect -- …`) con el motivo "reinicia la nota al cambiar la contraseña".

- [ ] **Step 3: Implementación de `MedidorPassword.tsx`**

```tsx
import { cn } from '@/shared/lib/utils'
import { AYUDA, AYUDA_ADMIN, ETIQUETAS_NOTA, MSG_NO_COMPROBADA, type EstadoNota } from './medidor'

/** Color de cada nota: rojo (0-1), naranja (2), verde (3), verde oscuro (4). */
const COLOR_NOTA = ['bg-red-600', 'bg-red-600', 'bg-orange-500', 'bg-green-600', 'bg-green-800'] as const
const COLOR_TEXTO = ['text-red-700', 'text-red-700', 'text-orange-600', 'text-green-700', 'text-green-900'] as const

type Props = { estado: EstadoNota; esAdmin: boolean }

/**
 * Barra de seguridad bajo "Nueva contraseña" (spec política de contraseñas §4.1): cinco tramos, el texto de la nota, el
 * motivo del servidor si no se acepta y la ayuda fija. Ocupa siempre su hueco para que el formulario no salte.
 */
export function MedidorPassword({ estado, esAdmin }: Props) {
  return (
    <div className="flex min-h-[52px] w-full flex-col gap-1">
      {estado.estado === 'listo' && (
        <div className="flex items-center gap-2">
          <div
            role="meter"
            aria-label="Seguridad de la contraseña"
            aria-valuemin={0}
            aria-valuemax={4}
            aria-valuenow={estado.nota}
            aria-valuetext={ETIQUETAS_NOTA[estado.nota]}
            className="flex flex-1 gap-1"
          >
            {[0, 1, 2, 3, 4].map((i) => (
              <span
                key={i}
                data-encendido={i <= estado.nota}
                className={cn('h-1.5 flex-1 rounded-full', i <= estado.nota ? COLOR_NOTA[estado.nota] : 'bg-fila-sep')}
              />
            ))}
          </div>
          <span className={cn('text-[11px] font-bold', COLOR_TEXTO[estado.nota])}>{ETIQUETAS_NOTA[estado.nota]}</span>
        </div>
      )}
      {estado.estado === 'listo' && estado.mensaje !== null && (
        <p className="text-[11px] text-error-password">{estado.mensaje}</p>
      )}
      {estado.estado === 'error' && <p className="text-[11px] text-azul-gris">{MSG_NO_COMPROBADA}</p>}
      <p className="text-[11px] text-azul-gris">{AYUDA}</p>
      {esAdmin && <p className="text-[11px] text-azul-gris">{AYUDA_ADMIN}</p>}
    </div>
  )
}
```

Antes de dar por buenos los colores, comprobar en `src/shared/styles/globals.css` si hay tokens de color de error/éxito (`--color-error-*`, `--color-verde-*`…) y usarlos en vez de los de Tailwind si existen; `bg-fila-sep`, `text-azul-gris` y `text-error-password` ya existen (los usan el diálogo y la tabla).

- [ ] **Step 4: Ejecutar**

```bash
npx vitest run src/modules/gestion/cuenta/MedidorPassword.test.tsx 2>&1 | tail -15
npm run lint 2>&1 | tail -10
```

Esperado: verde y sin errores de lint.

- [ ] **Step 5: Commit**

```bash
git add src/modules/gestion/cuenta/medidor.ts src/modules/gestion/cuenta/MedidorPassword.tsx src/modules/gestion/cuenta/MedidorPassword.test.tsx
git commit -m "feat: barra de seguridad de la contrasena con la nota del servidor"
```

---

### Task 9: La barra en "Cambiar contraseña" y en el cambio obligatorio

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/gestion/cuenta/CambiarPasswordDialog.tsx`
- Modify: `gestion-reparaciones-web/src/app/cuenta/CambioObligatorioPage.tsx`
- Test: `CambiarPasswordDialog.test.tsx`, `app/cuenta/CambioObligatorioPage.test.tsx`

**Interfaces:**
- Consumes: `useNotaPassword`, `bloqueaGuardar`, `MedidorPassword` (Task 8); `esAdmin` de `@/shared/session/storage`; `useSession().sesion`.

- [ ] **Step 1: Tests que fallan**

En `CambiarPasswordDialog.test.tsx`, un `describe` nuevo:

```tsx
describe('CambiarPasswordDialog: barra de seguridad', () => {
  it('la barra aparece bajo "Nueva contraseña" con la nota del servidor', async () => {
    server.use(http.post('*/api/auth/evaluar-password', () => HttpResponse.json({ nota: 3, aceptable: true, mensaje: null })))
    abrir()
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'nueva-larga-123')
    expect(await screen.findByText('Segura')).toBeInTheDocument()
    expect(screen.getByText('Mínimo 10 caracteres.')).toBeInTheDocument()
  })

  it('"Guardar" se desactiva si el servidor no la acepta y enseña el motivo', async () => {
    server.use(http.post('*/api/auth/evaluar-password', () =>
      HttpResponse.json({ nota: 1, aceptable: false, mensaje: 'La contraseña es poco segura. Añade otra palabra.' })))
    abrir()
    await rellenar('actual1', 'aaaaaaaaaa', 'aaaaaaaaaa')
    expect(await screen.findByText('La contraseña es poco segura. Añade otra palabra.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled()
  })

  it('si la comprobación falla, "Guardar" sigue disponible y decide el servidor', async () => {
    server.use(http.post('*/api/auth/evaluar-password', () => new HttpResponse(null, { status: 500 })))
    const cuerpos = registrarCambio()
    abrir()
    await rellenar('actual1', 'nueva-larga-123', 'nueva-larga-123')
    expect(await screen.findByText('No se pudo comprobar')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(cuerpos).toHaveLength(1))
  })
})
```

En `CambioObligatorioPage.test.tsx`, el mismo patrón (adaptando el render que ya use ese fichero), más:

```tsx
  it('con sesión de administrador recuerda que se pide «Muy segura»', async () => {
    // render de la página con SESION_ADMIN y passwordTemporal: true (mismo helper que el resto del fichero)
    expect(screen.getByText('Para el administrador se pide «Muy segura».')).toBeInTheDocument()
  })

  it('la explicación vale tanto para la temporal como para la contraseña de siempre', () => {
    // render normal
    expect(screen.getByText(EXPLICACION)).toBeInTheDocument()
    expect(EXPLICACION).toBe('Para seguir tienes que elegir una contraseña nueva. Será la que quede asociada a tu nombre en el registro de actividad.')
  })
```

```bash
npx vitest run src/modules/gestion/cuenta src/app/cuenta 2>&1 | tail -20
```

Esperado: FAIL (no hay barra; el texto de la explicación es el antiguo).

- [ ] **Step 2: Implementación en `CambiarPasswordDialog.tsx`**

En `Cuerpo`:

```tsx
  const { sesion } = useSession()
  const nota = useNotaPassword(nueva)
```

Bajo el `CampoPassword` de "Nueva contraseña", dentro de su `<Campo>`:

```tsx
        <MedidorPassword estado={nota} esAdmin={esAdmin(sesion)} />
```

En el botón "Guardar": `disabled={enviando || bloqueaGuardar(nota)}`. Y en `guardar`, después de `if (enviando) return`: `if (bloqueaGuardar(nota)) return` (el Enter del formulario no debe saltarse el bloqueo).

Imports: `useSession` de `@/shared/session/SessionProvider`, `esAdmin` de `@/shared/session/storage`, `bloqueaGuardar, useNotaPassword` de `./medidor`, `MedidorPassword` de `./MedidorPassword`.

- [ ] **Step 3: Implementación en `CambioObligatorioPage.tsx`**

```tsx
export const EXPLICACION =
  'Para seguir tienes que elegir una contraseña nueva. Será la que quede asociada a tu nombre en el registro de actividad.'
```

(La pantalla también la verá quien entre con su contraseña de siempre después de que el corte marque todas como temporales.)

`const { olvidarPasswordTemporal, logout, sesion } = useSession()`, `const nota = useNotaPassword(nueva)`, `<MedidorPassword estado={nota} esAdmin={esAdmin(sesion)} />` bajo el campo "Nueva contraseña", `disabled={enviando || bloqueaGuardar(nota)}` en "Guardar" y el mismo `if (bloqueaGuardar(nota)) return` en `guardar`. Imports desde `@/modules/gestion/cuenta/medidor` y `@/modules/gestion/cuenta/MedidorPassword`.

- [ ] **Step 4: Ejecutar**

```bash
npx vitest run src/modules/gestion/cuenta src/app 2>&1 | tail -20
```

Esperado: verde (los tests existentes siguen pasando gracias al handler por defecto de la Task 7, que devuelve «Muy segura»).

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: la barra de seguridad en cambiar contrasena y en el cambio obligatorio"
```

---

### Task 10: Alta sin contraseña, con la ventana de la temporal

**Files:**
- Modify: `gestion-reparaciones-web/src/modules/gestion/tecnicos/validacion.ts`
- Modify: `gestion-reparaciones-web/src/modules/gestion/tecnicos/api.ts`
- Modify: `gestion-reparaciones-web/src/modules/gestion/tecnicos/TecnicosPage.tsx`
- Test: `tecnicos/validacion.test.ts`, `tecnicos/api.test.tsx`, `tecnicos/TecnicosPage.test.tsx`, `tecnicos/TecnicosPage.cerrojo.test.tsx`

**Interfaces:**
- Consumes: `DialogoPasswordTemporal` (existente), contrato del alta (Task 7: cuerpo sin `password`, 201 `ValorTexto`).
- Produces: `DatosAlta = { nombreTecnico: string; nombreUsuario: string; rol: Rol }`, `CuerpoAlta` igual, `useRegistrar(): UseMutationResult<string, Error, { cuerpo: CuerpoAlta; clave: string }>` (devuelve la temporal).

- [ ] **Step 1: Tests que fallan**

`tecnicos/validacion.test.ts`: los casos de contraseña (`MSG_NO_COINCIDEN`, `MSG_PASSWORD_CORTA`) se borran; `validarAlta` y `cuerpoAlta` se prueban sin `password`/`confirmar`:

```ts
  it('solo exige los dos nombres', () => {
    expect(validarAlta({ nombreTecnico: '', nombreUsuario: 'u', rol: 'TECNICO' })).toBe(MSG_CAMPOS)
    expect(validarAlta({ nombreTecnico: 't', nombreUsuario: '  ', rol: 'TECNICO' })).toBe(MSG_CAMPOS)
    expect(validarAlta({ nombreTecnico: 't', nombreUsuario: 'u', rol: 'TECNICO' })).toBeNull()
  })
  it('el cuerpo no lleva contraseña', () => {
    expect(cuerpoAlta({ nombreTecnico: ' t ', nombreUsuario: ' u ', rol: 'SUPERTECNICO' })).toEqual({ nombreTecnico: 't', nombreUsuario: 'u', rol: 'SUPERTECNICO' })
  })
```

`tecnicos/TecnicosPage.test.tsx`: quitar el relleno de "Contraseña"/"Repite la contraseña" en todos los tests de alta; el cuerpo esperado del POST pasa a `{ nombreTecnico, nombreUsuario, rol }`; los handlers del POST responden `HttpResponse.json({ value: 'Temporal23' }, { status: 201 })`; borrar los tests de "no coinciden" y de 6 caracteres; y añadir:

```tsx
  it('el formulario ya no tiene campos de contraseña', () => {
    // render de la página como el resto del fichero (SESION_ADMIN)
    expect(screen.queryByPlaceholderText('Contraseña')).toBeNull()
    expect(screen.queryByPlaceholderText('Repite la contraseña')).toBeNull()
  })

  it('tras el alta enseña la temporal una sola vez, con el texto de entrega', async () => {
    server.use(http.post('*/api/usuarios/tecnicos', () => HttpResponse.json({ value: 'Temporal23' }, { status: 201 })))
    // render + rellenar nombre del técnico "Nuevo Técnico" y usuario, pulsar "Registrar técnico"
    const dlg = await screen.findByRole('dialog', { name: 'Contraseña temporal' })
    expect(within(dlg).getByLabelText('Contraseña temporal')).toHaveValue('Temporal23')
    expect(dlg).toHaveTextContent('Entrégasela a "Nuevo Técnico" en persona')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Contraseña temporal' })).toBeNull()
    expect(screen.queryByDisplayValue('Temporal23')).toBeNull()
  })
```

(Usar los helpers de render y relleno que ya tenga el fichero; si no hay, el patrón de `CambiarPasswordDialog.test.tsx`.) `TecnicosPage.cerrojo.test.tsx` y `tecnicos/api.test.tsx`: mismos ajustes de cuerpo y respuesta; en `api.test.tsx`, que `useRegistrar` devuelve `'Temporal23'`.

```bash
npx vitest run src/modules/gestion/tecnicos 2>&1 | tail -20
```

Esperado: FAIL.

- [ ] **Step 2: Implementación**

`tecnicos/validacion.ts`:

```ts
export type DatosAlta = { nombreTecnico: string; nombreUsuario: string; rol: Rol }
export type CuerpoAlta = { nombreTecnico: string; nombreUsuario: string; rol: Rol }

export const MSG_CAMPOS = 'Todos los campos son obligatorios.'
export const MSG_TECNICO_DUPLICADO = 'Ya existe un técnico con ese nombre.'
export const MSG_USUARIO_DUPLICADO = 'Ese nombre de usuario ya existe.'

/** Nombres con trim; para en el primer fallo. La contraseña ya no se escribe: la genera el servidor (0.9.2). */
export function validarAlta(d: DatosAlta): string | null {
  if (d.nombreTecnico.trim() === '' || d.nombreUsuario.trim() === '') return MSG_CAMPOS
  return null
}

/** Cuerpo de POST /api/usuarios/tecnicos con los nombres recortados (el servidor también recorta). */
export function cuerpoAlta(d: DatosAlta): CuerpoAlta {
  return { nombreTecnico: d.nombreTecnico.trim(), nombreUsuario: d.nombreUsuario.trim(), rol: d.rol }
}
```

(Borrar `MSG_NO_COINCIDEN` y `MSG_PASSWORD_CORTA` de este fichero si nadie más los importa: `grep -rn "MSG_NO_COINCIDEN\|tecnicos/validacion" src`.)

`tecnicos/api.ts`, `useRegistrar`:

```ts
/** `clave` va en la cabecera Idempotency-Key (la da la página con crearClavesIdempotencia). Devuelve la contraseña
 *  temporal generada por el servidor. `gcTime: 0` y el `reset()` de la página la sacan de la caché de mutaciones en
 *  cuanto la ventana la recoge: solo debe existir mientras se enseña. */
export function useRegistrar(): UseMutationResult<string, Error, { cuerpo: CuerpoAlta; clave: string }> {
  const recargar = useRecargaUsuarios()
  return useMutation({
    mutationFn: async ({ cuerpo, clave }: { cuerpo: CuerpoAlta; clave: string }) => {
      const { data } = await api.POST('/api/usuarios/tecnicos', { params: { header: { 'Idempotency-Key': clave } }, body: cuerpo })
      const password = data?.value
      if (typeof password !== 'string' || password === '') throw new Error(MSG_ERROR_REGISTRO)
      return password
    },
    gcTime: 0,
    meta: { silenciarError: true },
    onSuccess: recargar,
  })
}
```

(`MSG_ERROR_REGISTRO` se importa de `./textos`.)

`TecnicosPage.tsx`:
- `FORMULARIO_VACIO = { nombreTecnico: '', nombreUsuario: '', rol: 'TECNICO' }`.
- `CampoTexto = 'nombreTecnico' | 'nombreUsuario'` y `CAMPOS` sin las filas `password` y `confirmar` (actualizar el comentario de `CAMPOS`: "las contraseñas ya no se escriben en el alta: la genera el servidor").
- `onRegistrar`:

```tsx
    const cuerpo = cuerpoAlta(form)
    registrar.mutate({ cuerpo, clave: claves.para(OPERACION_ALTA, cuerpo) }, {
      onSuccess: (password) => {
        claves.hecha(OPERACION_ALTA)
        setForm(FORMULARIO_VACIO)
        setEntregada({ nombreTecnico: cuerpo.nombreTecnico, password })
        registrar.reset()
      },
      onError: (e) => setError(mensajeInline(e, MSG_ERROR_REGISTRO, [409, 422])),
    })
```

- El `<DialogoPasswordTemporal>` que ya existe sirve tal cual: `textoPasswordEntregada` ("Entrégasela a … en persona… Al entrar con ella tendrá que cambiarla por una propia.") vale para el alta y para restablecer.

- [ ] **Step 3: Ejecutar**

```bash
npx vitest run src/modules/gestion/tecnicos 2>&1 | tail -20
npm run check 2>&1 | tail -20
```

Esperado: verde, sin errores de lint ni de tipos.

- [ ] **Step 4: Commit**

```bash
git add -A src
git commit -m "feat: el alta de tecnico ya no pide contrasena y ensena la temporal generada"
```

---

### Task 11: E2E del alta y del cambio de contraseña

**Files:**
- Modify: `gestion-reparaciones-web/tests/e2e/gestion.spec.ts`

**Interfaces:** ninguna.

- [ ] **Step 1: Adaptar el test del administrador**

En `test('admin: técnico de prueba registrado…')`:

```ts
  // Contraseñas del usuario de prueba: frases de palabras sueltas, para que la barra las dé por «Segura» o más.
  const clave = `tortuga violeta ${marca} lampara`
  const claveNueva = `nube marina ${marca} cuaderno`
```

Paso "alta desde «Gestionar técnicos»": quitar los dos `fill` de contraseña; el cuerpo esperado pasa a `{ nombreTecnico, nombreUsuario, rol: 'TECNICO' }`; y tras el 201:

```ts
    const temporal = ((await respuesta.json()) as { value: string }).value
    expect(temporal).toMatch(/^[A-Za-z0-9]{10}$/)
    const ventana = page.getByRole('dialog', { name: 'Contraseña temporal' })
    await expect(ventana.getByLabel('Contraseña temporal')).toHaveValue(temporal)
    await page.keyboard.press('Escape')
    await expect(ventana).toBeHidden()
```

(`temporal` se declara con `let temporal = ''` antes del `test.step` para usarlo después.)

Paso del usuario de prueba:

```ts
    await entrarConTemporal(page, nombreUsuario, temporal, clave) // inicio de sesión 2/4
    await cambiarPassword(page, clave, claveNueva)
    await cerrarSesion(page)
    await entrarCon(page, nombreUsuario, claveNueva) // inicio de sesión 3/4
    await cerrarSesion(page)
```

En `entrarConTemporal` y `cambiarPassword`, antes de pulsar "Guardar", esperar a la barra:

```ts
  await expect(page.getByRole('meter', { name: 'Seguridad de la contraseña' })).toHaveAttribute('aria-valuenow', /^[34]$/)
  await expect(page.getByRole('button', { name: 'Guardar' })).toBeEnabled()
```

(en `cambiarPassword`, con `dlg.getByRole(...)` en vez de `page.getByRole(...)`).

- [ ] **Step 2: Compilar el e2e**

```bash
npx tsc --noEmit -p tsconfig.node.json 2>&1 | tail -5 || npx playwright test --list tests/e2e/gestion.spec.ts | tail -3
```

Esperado: sin errores (el e2e se ejecuta de verdad en la Task 12, contra producción con la 0.9.2 desplegada).

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/gestion.spec.ts
git commit -m "test: el e2e del administrador recoge la temporal del alta y espera a la barra"
```

---

## CIERRE

### Task 12: Versión 0.9.2, despliegue, comprobación y documentación

**Files:**
- Modify: `gestion-reparaciones-web/package.json` (+ `package-lock.json`), `gestion-reparaciones-web/CHANGELOG.md`
- Create: `docs/novedades/NOVEDADES-v0.9.2.md` (raíz)
- Fuera de git: `Apuntes/sp7/corte-definitivo.md`, `Apuntes/despliegue_vdc_produccion.md`, `Apuntes/plan-futuro.md`

- [ ] **Step 1: Versión y documentos de la release**

`npm version 0.9.2 --no-git-tag-version` en la web. Sección en el `CHANGELOG.md` de la web:

```markdown
## [0.9.2] - 2026-10-.. — Contraseñas seguras

- **Barra de seguridad.** Al elegir una contraseña nueva (menú de usuario → "Cambiar contraseña" y la pantalla "Cambia tu contraseña"), una barra dice lo fácil que es adivinarla: Muy débil, Débil, Poco segura, Segura o Muy segura. "Guardar" se activa a partir de "Segura"; para el administrador, solo con "Muy segura". Debajo sale el motivo cuando no vale ("La contraseña es poco segura. Añade otra palabra.").
- **Reglas nuevas.** Mínimo 10 caracteres, máximo 64, distinta de la actual. No hace falta poner mayúsculas, números ni símbolos: una frase de varias palabras sueltas es de lo más seguro.
- **Alta de técnicos sin contraseña.** El programa genera una contraseña temporal y la enseña una sola vez, como "Restablecer contraseña". Al entrar con ella hay que elegir una propia.
- Requiere el servidor 0.9.2. Sin cambios en la base de datos ni en la configuración de nginx.
```

`docs/novedades/NOVEDADES-v0.9.2.md` (raíz), con el formato de `NOVEDADES-v0.9.1.md`: un apartado "🔑 Contraseñas" para el taller ("la próxima vez que entres te pedirá una contraseña nueva; la barra te dice cuándo es suficientemente segura; una frase de tres o cuatro palabras sueltas funciona muy bien") y "🚚 Notas de despliegue" (servidor y web 0.9.2 juntos, primero el servidor; variable nueva del compose; sin migración).

- [ ] **Step 2: Suite completa, compilación y revisión de la web**

```bash
cd gestion-reparaciones-web && npm run check 2>&1 | tail -20 && npm run build 2>&1 | tail -5
```

`superpowers:requesting-code-review` de la rama de la web, con foco en: que la contraseña no queda en ninguna caché (mutación del alta con `gcTime: 0` y `reset()`, la nota sin TanStack Query), que el Enter no se salta el bloqueo de "Guardar" y que una respuesta vieja de la barra no pisa a la nueva.

- [ ] **Step 3: Merges y push (con OK, uno a uno)**

Pedir OK y esperar para cada uno: merge `--no-ff` de `feature/politica-contrasenas` a `main` en el servidor, push; lo mismo en la web.

```bash
cd gestion-reparaciones-servidor && git checkout main && git merge --no-ff feature/politica-contrasenas -m "merge: politica de contrasenas con barra de seguridad y alta con temporal generada (0.9.2)"
cd ../gestion-reparaciones-web && git checkout main && git merge --no-ff feature/politica-contrasenas -m "merge: barra de seguridad de la contrasena y alta sin contrasena (0.9.2)"
```

- [ ] **Step 4: Despliegue en PRODUCCIÓN (lo ejecuta el usuario, una sola sesión `ssh prod`)**

Fuera de horario (hoy nadie trabaja en producción). Primero la variable de las palabras propias en el compose (P8 de la guía; antes, `cp docker-compose.yml docker-compose.yml.bak-0.9.2`), en el servicio `backend`, bajo `environment:`:

```yaml
      POLITICA_PASSWORD_PALABRAS_PROPIAS: "<nombre de la empresa y variantes, separadas por comas>"
```

(Spring traduce `POLITICA_PASSWORD_PALABRAS_PROPIAS` a `politica.password.palabras-propias`.) Después, servidor y web:

```bash
cd /opt/reparaciones/gestion-reparaciones-servidor && git pull && git log --oneline -1
cd /opt/reparaciones/gestion-reparaciones-web && git pull && git log --oneline -1
cd /opt/reparaciones && docker compose up -d --build backend && sleep 25 && docker compose logs backend --tail 15 | grep -E "Started|ERROR|Exception"
docker compose up -d --build nginx
docker compose exec backend printenv POLITICA_PASSWORD_PALABRAS_PROPIAS | wc -c
/usr/local/sbin/backup-erp.sh && tail -n 1 /var/log/backup-erp.log
```

Esperado: los dos `merge` del Step 3, `Started App` sin excepciones, la variable con más de 1 carácter (no se imprime su valor), y una copia de la base hecha.

- [ ] **Step 5: Comprobación en producción**

Con la web 0.9.2 en el navegador (y con OK del usuario para crear el usuario sintético):

1. Como administrador: menú → "Cambiar contraseña", escribir en "Nueva contraseña" sin guardar: `1234567890` (roja), una frase de cuatro palabras (verde) y comprobar el texto «Muy segura» y la ayuda del administrador. **Cancelar sin guardar.**
2. Gestión → Técnicos: alta de `prueba-092` (técnico `Prueba 092`): sin campos de contraseña; sale la ventana con la temporal.
3. Cerrar sesión; entrar como `prueba-092` con la temporal: pantalla "Cambia tu contraseña" con el texto nuevo; probar una débil (Guardar desactivado, motivo en rojo) y una frase (Guardar activo); guardar; entra en la aplicación.
4. Cerrar sesión, entrar como administrador (separar los inicios de sesión: 5 por minuto), borrar `prueba-092` con la papelera.
5. E2E: `set -a; . ~/.env.e2e; set +a` y `npx playwright test tests/e2e/gestion.spec.ts --workers=1`.

Esperado: todo como se describe; anotarlo en el registro de sesiones de la guía.

- [ ] **Step 6: Tags y gitlinks (con OK, uno a uno)**

```bash
cd gestion-reparaciones-servidor && git tag -a v0.9.2 -m "0.9.2: politica de contrasenas" && git push origin main v0.9.2
cd ../gestion-reparaciones-web && git tag -a v0.9.2 -m "0.9.2: politica de contrasenas" && git push origin main v0.9.2
cd .. && git add gestion-reparaciones-servidor gestion-reparaciones-web docs/novedades/NOVEDADES-v0.9.2.md
git commit -m "chore: gitlinks servidor y web tras la 0.9.2 (v0.9.2)"
```

- [ ] **Step 7: Documentación fuera de git**

- `Apuntes/sp7/corte-definitivo.md`, fase 3: en el paso 4, "la contraseña nueva del administrador tiene que llegar a «Muy segura»"; paso nuevo **después** de las comprobaciones con una cuenta de cada rol:

  ```bash
  docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones' <<'SQL'
  SELECT ROL, COUNT(*) FROM Usuario GROUP BY ROL;
  UPDATE Usuario SET PASSWORD_TEMPORAL = 1 WHERE ROL <> 'ADMIN';
  SELECT ROL, SUM(PASSWORD_TEMPORAL) FROM Usuario GROUP BY ROL;
  SQL
  ```

  Esperado: las filas cambiadas = la suma de TECNICO + SUPERTECNICO; ADMIN con 0. Al día siguiente, cada uno entra con la suya y la cambia.
- `Apuntes/despliegue_vdc_produccion.md`: la variable nueva del compose (tabla de credenciales y secretos: no es un secreto, pero no va en los repos) y la entrada del registro de sesiones del despliegue.
- `Apuntes/plan-futuro.md`: la entrada "Política de contraseñas" pasa a hecha (0.9.2), con lo que queda para el corte (la orden SQL).

---

## Autorrevisión del plan

- **Cobertura de la spec:** §2 decisiones → Tasks 1-5 y 10; §3.1 reglas, orden, umbral por rol, 72 bytes, palabras genéricas y propias → Task 2; §3.2 `evaluar-password` con la excepción del filtro → Task 4, `cambiar-password` → Task 3, alta con temporal → Task 5; §3.3 sin esquema → ninguna migración; §4.1 barra, pausa, respuestas viejas, fallo → Task 8; §4.2 dónde va → Tasks 9 y 10; §5 despliegue, compose, comprobación con usuario sintético y orden SQL del corte → Task 12; §6 verificación → tests de cada tarea y Task 12; §8 documentación → Task 12.
- **Cambios respecto a la spec, a reflejar en ella:** la respuesta de `evaluar-password` es `{nota, aceptable, mensaje}` (sin lista de consejos: el primero va en `mensaje`, que es lo único que pinta la web); un `password` en el cuerpo del alta se ignora; el reintento del alta con la misma clave devuelve la misma temporal (vive en memoria del proceso hasta que caduca la entrada); el texto de la pantalla de cambio obligatorio pasa a valer para la contraseña de siempre.
- **Tipos:** `MedidorFuerza.Medida(int nota, List<String> consejos)`, `EvaluacionPassword(int nota, boolean aceptable, String mensaje)`, `PoliticaPassword.evaluar(password, actual, nombreUsuario, nombreTecnico, rol)`, `EstadoNota`, `useNotaPassword(password)`, `bloqueaGuardar(estado)`, `useRegistrar(): UseMutationResult<string, …>`: mismos nombres en todas las tareas.
