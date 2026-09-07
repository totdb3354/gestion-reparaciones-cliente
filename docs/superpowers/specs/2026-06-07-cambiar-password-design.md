# Cambiar contraseña — Diseño

**Estado:** Aprobado  
**Fecha:** 2026-06-07

---

## 1. Punto de entrada

Nuevo `MenuItem` "Cambiar contraseña" en el `ContextMenu` del botón `btnUsuario` (navbar).

- **Accesible para todos los roles** (ADMIN, SUPERTECNICO, TECNICO).
- **Posición**: insertado entre `itemDescargar` (Descargar CSV) y `sep` (el separador final antes de "Cerrar Sesión").
- Abre el modal con `Stage.showAndWait()` — bloquea la ventana principal mientras el modal está abierto.

Resultado en `MainController.inicializarMenuUsuario()`:

```
[para ADMIN: Gestionar técnicos, Ver logs, SeparatorMenuItem]
Descargar CSV
Cambiar contraseña   ← nuevo
────────────────────  ← sep (separador final)
Cerrar Sesión
```

---

## 2. Modal UI

**Estilo A**: barra superior navy (`#001232`) con título blanco + cuerpo blanco.  
Dimensiones: `prefWidth="380"`, `resizable="false"`, `APPLICATION_MODAL`.

### Campos

| # | Campo | Tipo |
|---|-------|------|
| 1 | Contraseña actual | `PasswordField` + ojo toggle |
| 2 | Nueva contraseña | `PasswordField` + ojo toggle |
| 3 | Confirmar nueva contraseña | `PasswordField` + ojo toggle |

Cada campo sigue el mismo patrón de `LoginView.fxml`:
- `PasswordField` + `TextField` (visible=false) apilados en `StackPane`
- Botón transparente con `ImageView` del ojo (imágenes existentes: `ojo_activar.png` / `ojo_desactivar.png`)
- Padding `13 44 13 14`, `border-radius: 8`, color de borde `#D4D8DE`

### Botones

`HBox` con `Cancelar` (borde gris) y `Guardar` (fondo `#001232`, texto blanco), alineados a la derecha.

### Validación inline

Un `Label` de error (`lblError`) debajo de los campos, oculto por defecto (`visible="false" managed="false"`), texto en rojo `#CC0000`.

**Reglas (al pulsar Guardar, en orden):**

1. Los tres campos no están vacíos → "Rellena todos los campos."
2. Nueva contraseña ≥ 6 caracteres → "La contraseña debe tener al menos 6 caracteres."
3. Nueva == Confirmar → "Las contraseñas nuevas no coinciden."
4. Si pasa todas → llamada al DAO.

**Respuesta del servidor:**

- `401` (`"Sesión expirada"` en `ApiClient`) en contexto de cambio de contraseña → mostrar inline "Contraseña actual incorrecta."
- Cualquier otro `SQLException` → mostrar el mensaje inline.
- Éxito (`204`) → cerrar modal + `new Alert(AlertType.INFORMATION, "Contraseña cambiada correctamente.").showAndWait()`.

---

## 3. Arquitectura cliente–servidor

### Cliente

**`UsuarioDAO.java`** — nuevo método:

```java
public void cambiarPassword(String passwordActual, String passwordNueva) throws SQLException {
    ApiClient.patch("/api/auth/cambiar-password",
        Map.of("passwordActual", passwordActual, "passwordNueva", passwordNueva));
}
```

La `SQLException` con texto "Sesión expirada" que lanza `ApiClient.handleErrors` para `401` es la señal de "contraseña actual incorrecta" en este contexto.

**`CambiarPasswordController.java`** — `@FXML` para los seis campos (`PasswordField` + `TextField` por cada uno), tres botones de ojo, `btnGuardar`, `btnCancelar`, `lblError`. Patrón toggle idéntico al `LoginController`.

**`CambiarPasswordView.fxml`** — referencia de estilo: misma familia de campos que `LoginView.fxml`.  
Usa `fx:controller="com.reparaciones.controllers.CambiarPasswordController"` (igual que `RegisterView.fxml`).  
`MainController` no necesita obtener el controlador — solo carga y muestra la ventana.

**`MainController.java`** — método `abrirCambiarPassword()`:

```java
private void abrirCambiarPassword() {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/CambiarPasswordView.fxml"));
        Parent root = loader.load();
        Stage ventana = new Stage();
        ventana.initModality(Modality.APPLICATION_MODAL);
        ventana.initOwner(btnUsuario.getScene().getWindow());
        ventana.setTitle("Cambiar contraseña");
        ventana.setScene(new Scene(root));
        ventana.setResizable(false);
        ventana.showAndWait();
    } catch (IOException e) { mostrarError(e); }
}
```

### Servidor

**`/api/auth/cambiar-password` — `PATCH`** en `AuthController.java`:

```java
@PatchMapping("/cambiar-password")
public ResponseEntity<?> cambiarPassword(
        @AuthenticationPrincipal UsuarioPrincipal principal,
        @RequestBody CambiarPasswordRequest req) {
    try {
        usuarioDao.cambiarPassword(principal.getIdUsu(), req.passwordActual(), req.passwordNueva());
        logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD", "");
        return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
        return ResponseEntity.status(401).build();
    }
}
private record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
```

**`UsuarioDAO.java` (servidor)** — nuevo método:

```java
public void cambiarPassword(int idUsu, String passwordActual, String passwordNueva) {
    String hashActual = jdbc.queryForObject(
        "SELECT PASSWORD FROM Usuario WHERE ID_USU = ?", String.class, idUsu);
    if (!passwordEncoder.matches(passwordActual, hashActual))
        throw new IllegalArgumentException("Contraseña actual incorrecta");
    String hashNuevo = passwordEncoder.encode(passwordNueva);
    jdbc.update("UPDATE Usuario SET PASSWORD = ? WHERE ID_USU = ?", hashNuevo, idUsu);
}
```

El controller captura `IllegalArgumentException` → `401`.  
`SecurityConfig.java` no necesita cambios: `anyRequest().authenticated()` ya cubre el nuevo endpoint.

---

## 4. Archivos a crear/modificar

| Archivo | Repo | Acción |
|---------|------|--------|
| `src/main/resources/views/CambiarPasswordView.fxml` | cliente | Crear |
| `src/main/java/.../controllers/CambiarPasswordController.java` | cliente | Crear |
| `src/main/java/.../controllers/MainController.java` | cliente | Modificar — MenuItem + `abrirCambiarPassword()` |
| `src/main/java/.../dao/UsuarioDAO.java` | cliente | Modificar — `cambiarPassword()` |
| `src/main/java/.../controller/AuthController.java` | servidor | Modificar — `PATCH /api/auth/cambiar-password` |
| `src/main/java/.../dao/UsuarioDAO.java` | servidor | Modificar — `cambiarPassword()` |
