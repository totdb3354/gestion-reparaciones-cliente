# Validar nombre de técnico único — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rechazar el alta de un usuario técnico si el nombre del técnico ya existe (case-insensitive + trim), con mensaje claro, y arreglar el feedback del login duplicado en el cliente.

**Architecture:** El servidor comprueba duplicados (nombre de técnico y de usuario) antes de insertar y devuelve 409 con `{message}`. El cliente muestra el mensaje del servidor en el formulario de registro. Sin BD.

**Tech Stack:** Spring Boot (JdbcTemplate), JavaFX 17.

## Global Constraints

- Validación por comprobación previa en servidor (sin constraint UNIQUE en `Tecnico.NOMBRE`, sin migración).
- Comparación case-insensitive + trim: `LOWER(TRIM(NOMBRE)) = LOWER(TRIM(?))`.
- Mensajes: "Ya existe un técnico con ese nombre." / "Ese nombre de usuario ya existe.".
- Se mantienen los dos campos del formulario (nombre técnico + login).
- Servidor y cliente se despliegan juntos. UI/DAO sin tests automáticos: verificación por compilación + manual.

---

## Task 1: Servidor — comprobar duplicados y devolver 409 con mensaje

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/UsuarioController.java`

**Interfaces:**
- Produces: `UsuarioDAO.existeNombreTecnico(String)`, `UsuarioDAO.existeNombreUsuario(String)` — usados por el controller.

- [ ] **Step 1: Añadir los métodos de comprobación a UsuarioDAO**

Añadir al final de `UsuarioDAO`, antes del `}` de cierre de la clase:
```java
    public boolean existeNombreTecnico(String nombre) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Tecnico WHERE LOWER(TRIM(NOMBRE)) = LOWER(TRIM(?))",
                Integer.class, nombre);
        return n != null && n > 0;
    }

    public boolean existeNombreUsuario(String nombreUsuario) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Usuario WHERE LOWER(TRIM(NOMBRE_USUARIO)) = LOWER(TRIM(?))",
                Integer.class, nombreUsuario);
        return n != null && n > 0;
    }
```

- [ ] **Step 2: Asegurar el import de Map en UsuarioController**

Si no está ya, añadir junto a los imports de `UsuarioController.java`:
```java
import java.util.Map;
```

- [ ] **Step 3: Comprobar duplicados en registrarTecnico**

En `UsuarioController.registrarTecnico`, añadir las comprobaciones al inicio del método (antes del `try`):
```java
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        if (dao.existeNombreTecnico(req.nombreTecnico())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ya existe un técnico con ese nombre."));
        }
        if (dao.existeNombreUsuario(req.nombreUsuario())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        try {
            String rol = req.rol() != null ? req.rol() : "TECNICO";
            dao.registrarTecnico(req.nombreTecnico(), req.nombreUsuario(), req.password(), rol);
            logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                    "NOMBRE_USUARIO: " + req.nombreUsuario() + ", ROL: " + rol + ", TECNICO: " + req.nombreTecnico());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
```
(El `catch (DataIntegrityViolationException)` pasa a devolver también `{message}` como red de seguridad ante concurrencia, en vez de un 409 vacío.)

- [ ] **Step 4: Compilar el servidor**

Run: `cd gestion-reparaciones-servidor && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/UsuarioController.java
git commit -m "feat(validar-nombre): rechazar alta con nombre de técnico o usuario duplicado (409 + mensaje)"
```

---

## Task 2: Cliente — mostrar el mensaje del servidor en el registro

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/RegisterController.java`

**Interfaces:**
- Consumes: el 409 con `{message}` de Task 1 (llega como `SQLException`/`StaleDataException` cuyo `getMessage()` es el mensaje del servidor).

- [ ] **Step 1: Sustituir el manejo de error del alta**

En `RegisterController`, en el `catch (SQLException e)` del método de registro, reemplazar:
```java
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                mostrarError("Ese nombre de usuario ya existe.");
            } else {
                mostrarError("Error al registrar. Inténtalo de nuevo.");
            }
        }
```
por:
```java
        } catch (SQLException e) {
            String msg = e.getMessage();
            mostrarError(msg != null && !msg.isBlank() ? msg : "Error al registrar. Inténtalo de nuevo.");
        }
```

- [ ] **Step 2: Compilar el cliente**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Test manual E2E (cubre ambas tareas)**

Arrancar servidor + cliente. Como **admin**, en el alta de usuarios:
1. Nombre de técnico ya existente (login nuevo) → "Ya existe un técnico con ese nombre.".
2. Mismo nombre con distinto case / espacios ("juan", " Juan ") → mismo rechazo.
3. Login ya existente (nombre de técnico nuevo) → "Ese nombre de usuario ya existe.".
4. Nombre y login nuevos → se crea, el formulario se limpia y la lista se recarga.
5. Validaciones previas (campos vacíos, contraseñas distintas, < 6) siguen funcionando.

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/RegisterController.java
git commit -m "feat(validar-nombre): mostrar el mensaje del servidor en el alta (arregla feedback de duplicados)"
```

---

## Self-Review

### Spec coverage

| Requisito del spec | Task/Step |
|---|---|
| `existeNombreTecnico` (case-insensitive + trim) | T1 Step 1 |
| `existeNombreUsuario` (case-insensitive + trim) | T1 Step 1 |
| Controller comprueba antes de insertar → 409 con mensaje | T1 Step 3 |
| Mensaje técnico / usuario distintos | T1 Step 3 |
| `DataIntegrityViolationException` como red de seguridad → 409 con mensaje | T1 Step 3 |
| Cliente muestra mensaje del servidor (no errorCode 1062) | T2 Step 1 |
| Validaciones previas de campos sin cambios | T2 (no se tocan) |
| Sin migración de BD | Todo el plan |

### Notas
- Sin TDD (DAO/UI sin tests automáticos en el proyecto); verificación por compilación + manual.
- `jdbc` es el campo `JdbcTemplate` existente en `UsuarioDAO`. `Map` se importa en Task 1 Step 2 si falta.
