# Política de contraseñas — medidor de seguridad y regla única en el servidor

Fecha: 2026-10-05. Versión del producto: **0.9.2** (servidor y web etiquetados juntos).
Antecedentes: spec del SP7b (`2026-09-28-sp7b-autorizacion-sesiones-design.md`, §5: contraseña temporal,
restablecer, freno de intentos).

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de la máquina
(dominio, nombre de la empresa, cuentas) y el detalle operativo del corte viven fuera de git, en `Apuntes/`.

## 1. Objetivo

Que cada contraseña que se ponga a partir de la 0.9.2 sea **difícil de adivinar**, con una regla que manda en el
servidor y una barra que lo enseña mientras se escribe. Después del corte, todas las contraseñas existentes se
sustituyen por otras que cumplan la regla.

Hoy la única regla es un mínimo de 6 caracteres, heredado del cliente JavaFX.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Cómo se mide | **zxcvbn**: estima cuántos intentos haría falta para adivinarla (contraseñas comunes, diccionario, teclado, series, fechas, sustituciones, datos del propio usuario). Nota de 0 a 4 | Contar tipos de caracteres premia `Empresa2026!` y castiga una frase larga; las recomendaciones actuales (NIST SP 800-63B) desaconsejan exigir mayúsculas, números o símbolos y la caducidad periódica |
| Quién calcula la nota | **Solo el servidor** (`zxcvbn4j`, Java). La web pide la nota mientras se escribe y pinta la barra | Una sola fuente de verdad: la barra y el rechazo al guardar nunca discrepan; la web no carga diccionarios |
| Umbral | Nota **≥ 3 ("Segura")** para TECNICO y SUPERTECNICO; **4 ("Muy segura")** para ADMIN | La cuenta de administración lo puede todo y la usa una sola persona |
| Longitud | Mínimo **10** caracteres; máximo **64** caracteres y **72 bytes** en UTF-8 | 10 como suelo; BCrypt solo usa los primeros 72 bytes (la versión de Spring Security en uso los trunca sin avisar) |
| Repetición | La nueva tiene que ser **distinta de la actual** | Si no, el cambio obligatorio se esquiva poniendo la misma |
| Alta de usuarios | **La contraseña inicial la genera el servidor**, como "Restablecer", y se enseña una sola vez | El administrador no inventa una contraseña que solo vive hasta el primer inicio de sesión; las temporales son siempre fuertes |
| Contraseñas existentes | Tras el corte, una orden SQL las marca como temporales (salvo ADMIN); cada uno la cambia al entrar con la suya | No hay que repartir temporales ni programar nada: la marca existe desde la 0.9.0 |

## 3. Servidor

### 3.1 `PoliticaPassword`

Una sola clase con una sola entrada, usada por todos los sitios que fijan una contraseña elegida por una persona.

Entrada: la contraseña propuesta, la actual (si la hay), el nombre de usuario, el nombre del técnico (si lo hay) y
el rol. Salida: la nota (0-4), si es aceptable, el primer motivo de rechazo y los consejos.

Comprobaciones, en este orden; se para en la primera que falla (código 422, como el resto de reglas):

| # | Regla | Mensaje |
|---|---|---|
| 1 | Vacía | `Rellena todos los campos.` (el de hoy) |
| 2 | Menos de 10 caracteres | `La contraseña debe tener al menos 10 caracteres.` |
| 3 | Más de 64 caracteres o más de 72 bytes | `La contraseña es demasiado larga (máximo 64 caracteres).` |
| 4 | Igual que la actual | `La nueva contraseña tiene que ser distinta de la actual.` |
| 5 | Nota por debajo del umbral del rol | `La contraseña es poco segura.` seguido del consejo principal de zxcvbn |

Los caracteres se cuentan como hoy (`String.length()`, sin recortar espacios). Los espacios valen.

**Palabras que penalizan.** A zxcvbn se le pasan como "datos del usuario":

- el nombre de usuario y el nombre del técnico;
- una lista **genérica** versionada en el repo (palabras del oficio: taller, reparaciones, iphone, pantalla,
  batería, móvil…);
- una lista **propia** leída de configuración (propiedad `politica.password.palabras-propias`, variable de entorno
  en el compose de la máquina), con el nombre de la empresa y lo que se quiera añadir. Vacía por defecto. No va en
  los repos.

Consejos en **español** (los trae `zxcvbn4j`). El objeto `Zxcvbn` se construye una vez y se reutiliza.

### 3.2 Endpoints

- **Nuevo `POST /api/auth/evaluar-password`.** Cuerpo `{password}`. Respuesta `{nota, aceptable, mensaje, consejos}`,
  donde `mensaje` es el de la tabla de 3.1 o `null`.
  - Exige sesión; cualquier rol. Usuario, técnico y rol salen de la sesión, nunca del cuerpo.
  - Se permite **también con la contraseña temporal** (excepción en `JwtAuthFilter`, junto a `cambiar-password`).
  - No consulta la contraseña actual (no la conoce): la regla 4 solo se aplica al guardar.
  - No escribe nada, no registra actividad y nunca registra la contraseña en ningún log.
- **`PATCH /api/auth/cambiar-password`** (cambio normal y obligatorio): sustituye la regla de los 6 caracteres por
  `PoliticaPassword`. Orden: campos vacíos → política → comprobación de la actual (como hoy, con su freno de
  intentos).
- **`POST /api/usuarios/tecnicos` (alta):** deja de recibir `password`. Genera la temporal con
  `PasswordTemporal.generar()`, la guarda marcada como temporal (como hoy) y la devuelve una sola vez en la
  respuesta (`ValorTexto`, igual que restablecer). El resto de validaciones del alta y los 409 de duplicado, igual.
- **Restablecer** (`POST /api/usuarios/{idUsu}/password-temporal`): sin cambios. Las temporales generadas no pasan
  por la política.

### 3.3 Sin cambios de esquema

Ninguna migración. La columna `Usuario.PASSWORD_TEMPORAL` ya existe desde la 0.9.0.

## 4. Web

### 4.1 `MedidorPassword`

Debajo del campo "Nueva contraseña":

```
▰▰▰▱▱  Poco segura
Añade otra palabra; mejor si no tienen relación entre sí.
Mínimo 10 caracteres.
```

- Barra de 5 tramos (nota 0-4) con texto: Muy débil, Débil, Poco segura, Segura, Muy segura. Colores: rojo (0-1),
  naranja (2), verde (3), verde oscuro (4).
- Debajo, el mensaje y los consejos que devuelve el servidor (si los hay).
- Ayuda fija: `Mínimo 10 caracteres.` Para el administrador, además: `Para el administrador se pide «Muy segura».`
- Pide la nota **0,3 s después de la última tecla**; descarta las respuestas que lleguen tarde; con el campo vacío
  no pinta la barra.
- Si la petición falla: `No se pudo comprobar` y **no bloquea** (al guardar decide el servidor).

### 4.2 Dónde va

- **Diálogo "Cambiar contraseña"** y **pantalla de cambio obligatorio**: el medidor bajo "Nueva contraseña".
  "Guardar" desactivado mientras la nota no llegue al umbral del rol (salvo si la comprobación falló). La
  validación local pasa a: campos rellenos → 10-64 caracteres → distinta de la actual → nueva y repetida
  coinciden. Los 422 del servidor se enseñan como hoy.
- **Alta de técnico** (Gestión → Técnicos): desaparecen "Contraseña" y "Confirmar". Tras el alta se abre la ventana
  de la contraseña temporal (la de restablecer), con su texto de entrega en persona.

## 5. Despliegue

1. Servidor y web **0.9.2** juntos, **antes del corte**, fuera de horario: primero el servidor y a continuación la
   web (el alta cambia de forma).
2. Compose de la máquina: añadir la variable de las palabras propias y recrear el backend. Documentarlo en la guía
   de la máquina.
3. Comprobación en producción con **un usuario sintético de usar y tirar** (alta, temporal, cambio obligatorio,
   débil rechazada, buena aceptada, borrado). En la cuenta de administración solo se mira la barra, sin guardar.
4. **En el corte** (guion fuera de git): la contraseña nueva del administrador tiene que llegar a "Muy segura"; y,
   después de las comprobaciones con una cuenta de cada rol, una orden SQL marca como temporales las contraseñas de
   todos los usuarios que no son ADMIN. Al día siguiente, cada uno entra con la suya y la cambia.

**Vuelta atrás:** volver a la 0.9.1 (servidor y web) sin tocar la base: las contraseñas ya cambiadas siguen siendo
válidas (son hashes BCrypt normales).

## 6. Verificación

- **Servidor:** `PoliticaPassword` (cada regla y su orden, umbral 3/4 por rol, penalización de usuario, técnico,
  lista genérica y lista propia, 72 bytes con tildes); `evaluar-password` (exige sesión, permitido con temporal,
  sin registro de actividad); `cambiar-password` (débil rechazada, buena aceptada, igual a la actual rechazada); alta
  sin contraseña (devuelve temporal, queda marcada, no acepta `password`); contrato OpenAPI regenerado.
- **Web:** `MedidorPassword` (estados, pausa, respuestas viejas, fallo de red); "Guardar" bloqueado y desbloqueado
  en el diálogo y en el cambio obligatorio; alta sin campos de contraseña y con la ventana de la temporal.
- **E2E:** adaptar los que dan altas o cambian contraseñas.

## 7. Fuera de alcance

- Forzar el cambio solo a quien tenga una contraseña débil (puntuarla al entrar).
- Caducidad periódica de contraseñas.
- Comprobar contraseñas filtradas contra servicios externos.
- El cliente JavaFX (se retira en el corte).

## 8. Documentación

- `CHANGELOG.md` de servidor y web; `docs/novedades/NOVEDADES-v0.9.2.md` en la raíz, en lenguaje del taller
  ("al entrar te pedirá una contraseña nueva; la barra te dice cuándo es suficientemente segura").
- Fuera de git: guion del corte (paso de la orden SQL y la regla del administrador), guía de la máquina (variable
  nueva del compose, registro de la sesión de despliegue) y `plan-futuro.md` (la política pasa a hecha).
