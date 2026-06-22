# Validar nombre de técnico único al crear usuarios — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

Al dar de alta un usuario técnico, se valida que el **nombre del técnico** no esté repetido (comparación *case-insensitive* + *trim*), igual que ya ocurre con el nombre de usuario (login). Se mantienen los dos campos del formulario (nombre de técnico y login) por legibilidad. De paso, se arregla el feedback de error del registro, que tras la migración a HTTP dejó de mostrar el mensaje específico de duplicado.

## Contexto y problema

- El formulario (`RegisterController`) pide dos campos: `campoNombreTecnico` (el nombre mostrado en todas las vistas) y `campoNombreUsuario` (login).
- `Usuario.NOMBRE_USUARIO` es `UNIQUE`: el login no se puede duplicar (lo garantiza la BD).
- `Tecnico.NOMBRE` **no** tiene restricción: hoy se pueden crear dos técnicos con el mismo nombre, que serían indistinguibles en las vistas (Reparador, Asignado por, filtro de técnico, reasignación).
- El cliente intenta mostrar "Ese nombre de usuario ya existe." comprobando `e.getErrorCode() == 1062` (código JDBC de MySQL). Con el flujo HTTP actual, un 409 llega como `StaleDataException` **sin** ese errorCode, por lo que ese mensaje específico ya no se muestra — cae en el genérico "Error al registrar. Inténtalo de nuevo.".

## Decisión

- **Validación por comprobación previa en el servidor** (no constraint `UNIQUE` en BD): seguro ante posibles duplicados ya existentes y sin migración. Si en el futuro se confirma BD limpia, el `UNIQUE` se puede añadir como refuerzo.
- Comparación **case-insensitive + trim** ("Juan", "juan", "Juan " son el mismo nombre).
- Se mantienen los dos campos del formulario.

## Cambios

### Servidor — `UsuarioDAO`
- Añadir `boolean existeNombreTecnico(String nombre)`:
  `SELECT COUNT(*) FROM Tecnico WHERE LOWER(TRIM(NOMBRE)) = LOWER(TRIM(?))` → `> 0`.
- Añadir `boolean existeNombreUsuario(String nombreUsuario)`:
  `SELECT COUNT(*) FROM Usuario WHERE LOWER(TRIM(NOMBRE_USUARIO)) = LOWER(TRIM(?))` → `> 0`.

### Servidor — `UsuarioController.registrarTecnico`
- Antes de `dao.registrarTecnico(...)`, comprobar:
  - Si `existeNombreTecnico(req.nombreTecnico())` → `throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un técnico con ese nombre.")`.
  - Si `existeNombreUsuario(req.nombreUsuario())` → `throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya existe.")`.
- Mantener el `try/catch (DataIntegrityViolationException)` → 409 como red de seguridad (concurrencia), pero el caso normal ahora da mensaje claro.

### Cliente — `RegisterController`
- En el `catch (SQLException e)` del alta, sustituir la lógica de `errorCode == 1062` por mostrar el mensaje del servidor: `mostrarError(e.getMessage())` (el 409 llega como `StaleDataException` cuyo `getMessage()` es el `message` del cuerpo, extraído por `ApiClient.extractMessage`). Si el mensaje viniera vacío, usar un fallback "Error al registrar. Inténtalo de nuevo.".
- La validación previa de campos (obligatorios, contraseñas coinciden, longitud ≥ 6) no cambia.

## Alcance

- Servidor (`UsuarioDAO`, `UsuarioController`) + cliente (`RegisterController`). Sin migración de BD.
- **Fuera de alcance:** renombrar técnicos existentes (no existe esa función); constraint `UNIQUE` en `Tecnico.NOMBRE` (posible refuerzo futuro); duplicados ya existentes en la BD (no se tocan).

## Testing

- Servidor: alta con nombre de técnico ya existente (mismo, distinto case, con espacios) → 409 "Ya existe un técnico con ese nombre."; alta con login existente → 409 "Ese nombre de usuario ya existe."; alta válida → 201.
- Cliente (manual): el formulario muestra el mensaje correcto en cada caso de duplicado, y un alta válida limpia el formulario y recarga la lista.
- Regresión: altas con nombres nuevos siguen funcionando.

## Despliegue

Recompilar y desplegar servidor y cliente juntos. Sin BD.
