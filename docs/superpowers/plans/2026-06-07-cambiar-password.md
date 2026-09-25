# Cambiar Contraseña — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Cambiar contraseña" flow: a menu item in the navbar opens a modal where any authenticated user can change their own password.

**Architecture:** Two-repo project (Spring Boot server + JavaFX client). Server exposes a new `PATCH /api/auth/cambiar-password` endpoint that verifies the current password with BCrypt, hashes the new one, and updates the DB. The JavaFX client adds a modal window triggered from the user menu; the modal validates inline and calls the DAO method that sends the PATCH request with the JWT already wired in `ApiClient`.

**Tech Stack:** Java 21, Spring Boot (Spring Security + JDBC + BCrypt), JavaFX 21, JWT (already wired in `ApiClient`)

---

## File Map

| File | Change |
|------|--------|
| `gestion-reparaciones-servidor/.../dao/UsuarioDAO.java` | Add `cambiarPassword(int, String, String)` |
| `gestion-reparaciones-servidor/.../controller/AuthController.java` | Add `UsuarioDAO` field + `PATCH /cambiar-password` + `CambiarPasswordRequest` record |
| `gestion-reparaciones-cliente/.../dao/UsuarioDAO.java` | Add `cambiarPassword(String, String)` |
| `gestion-reparaciones-cliente/.../views/CambiarPasswordView.fxml` | Create — modal FXML (3 password fields + eye toggles + error label + buttons) |
| `gestion-reparaciones-cliente/.../controllers/CambiarPasswordController.java` | Create — toggle logic, validation, DAO call |
| `gestion-reparaciones-cliente/.../controllers/MainController.java` | Add `MenuItem` "Cambiar contraseña" + `abrirCambiarPassword()` |

---

### Task 1: Servidor — UsuarioDAO.cambiarPassword()

**Files:**
- Modify: `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor\src\main\java\com\reparaciones\servidor\dao\UsuarioDAO.java`

**Context:** The server `UsuarioDAO` already has `PasswordEncoder` injected (used in `registrarTecnico`). This new method retrieves the stored BCrypt hash, verifies with `matches()`, hashes the new password, and updates the DB. It throws `IllegalArgumentException` if the current password is wrong — the controller (Task 2) maps this to `HTTP 401`.

- [ ] **Step 1: Add `cambiarPassword` to server UsuarioDAO**

Open `UsuarioDAO.java` (server). Find the closing `}` of the class (after `eliminarTecnico`). Insert the new method before the final `}`:

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

- [ ] **Step 2: Compile server to verify no errors**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor"
mvn compile -q
```

Expected: exit code 0, no output.

- [ ] **Step 3: Commit**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor"
git add src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java
git commit -m "feat: cambiar-password — DAO server-side"
```

---

### Task 2: Servidor — AuthController PATCH /cambiar-password

**Files:**
- Modify: `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor\src\main\java\com\reparaciones\servidor\controller\AuthController.java`

**Context:** The endpoint accepts `{ "passwordActual": "...", "passwordNueva": "..." }` with a valid JWT. `@AuthenticationPrincipal` is populated by `JwtAuthFilter` — it reads the JWT and sets a `UsuarioPrincipal` in the security context before the controller runs. Spring Boot auto-injects `UsuarioDAO` via constructor injection (just add the parameter). `IllegalArgumentException` from the DAO → `401`; success → `204 No Content`. The `SecurityConfig` has `anyRequest().authenticated()` — no changes needed there.

- [ ] **Step 1: Replace the entire AuthController.java**

Replace the full content of `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor\src\main\java\com\reparaciones\servidor\controller\AuthController.java` with:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil               jwtUtil;
    private final LogDAO                logDao;
    private final UsuarioDAO            usuarioDao;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil,
                          LogDAO logDao, UsuarioDAO usuarioDao) {
        this.authManager = authManager;
        this.jwtUtil     = jwtUtil;
        this.logDao      = logDao;
        this.usuarioDao  = usuarioDao;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.usuario(), req.password()));
            var principal = (UsuarioPrincipal) auth.getPrincipal();
            String token  = jwtUtil.generateToken(principal);

            logDao.insertar(principal.getIdUsu(), "LOGIN", "");

            Map<String, Object> resp = new HashMap<>();
            resp.put("idUsu",         principal.getIdUsu());
            resp.put("nombreUsuario", principal.getUsername());
            resp.put("rol",           principal.getRol());
            resp.put("idTec",         principal.getIdTec());
            resp.put("token",         token);
            return ResponseEntity.ok(resp);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
    }

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

    private record LoginRequest(String usuario, String password) {}
    private record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
}
```

- [ ] **Step 2: Compile server to verify no errors**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor"
mvn compile -q
```

Expected: exit code 0, no output.

- [ ] **Step 3: Commit**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-servidor"
git add src/main/java/com/reparaciones/servidor/controller/AuthController.java
git commit -m "feat: cambiar-password — PATCH /api/auth/cambiar-password"
```

---

### Task 3: Cliente — UsuarioDAO.cambiarPassword()

**Files:**
- Modify: `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente\src\main\java\com\reparaciones\dao\UsuarioDAO.java`

**Context:** The client DAO is a thin HTTP wrapper around `ApiClient`. When the server returns `401` (wrong password), `ApiClient.handleErrors()` throws `SQLException` with message `"Sesión expirada. Vuelve a iniciar sesión."`. The controller (Task 5) catches this and shows "Contraseña actual incorrecta." — it identifies the case by checking `message.startsWith("Sesión expirada")`.

- [ ] **Step 1: Add `cambiarPassword` to client UsuarioDAO**

Open `UsuarioDAO.java` (client). Find the closing `}` of the class (after `registrarTecnico`). Insert before the final `}`:

```java
    public void cambiarPassword(String passwordActual, String passwordNueva) throws SQLException {
        ApiClient.patch("/api/auth/cambiar-password",
                Map.of("passwordActual", passwordActual, "passwordNueva", passwordNueva));
    }
```

- [ ] **Step 2: Compile client to verify no errors**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
mvn compile -q
```

Expected: exit code 0, no output.

- [ ] **Step 3: Commit**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
git add src/main/java/com/reparaciones/dao/UsuarioDAO.java
git commit -m "feat: cambiar-password — DAO client-side"
```

---

### Task 4: Cliente — CambiarPasswordView.fxml

**Files:**
- Create: `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente\src\main\resources\views\CambiarPasswordView.fxml`

**Context:** Style A from the design: dark navy header (`#001232`) + white card body. Three password fields, each using the same PasswordField + TextField stacked pattern from `LoginView.fxml` — a `StackPane` holds the `PasswordField` (visible by default), a `TextField` (hidden), and a transparent eye `Button`. The controller handles show/hide on toggle. Images `ojo_activar.png` and `ojo_desactivar.png` already exist at `src/main/resources/images/`. The FXML uses `@/images/...` paths (absolute classpath), same as `LoginView.fxml`.

- [ ] **Step 1: Create the FXML file**

Create `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente\src\main\resources\views\CambiarPasswordView.fxml` with this exact content:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.*?>
<?import javafx.scene.image.*?>
<?import javafx.scene.layout.*?>

<VBox xmlns:fx="http://javafx.com/fxml/1"
      xmlns="http://javafx.com/javafx/17"
      fx:controller="com.reparaciones.controllers.CambiarPasswordController"
      prefWidth="380" spacing="0">

    <!-- Barra de título navy -->
    <VBox style="-fx-background-color: #001232; -fx-padding: 16 20 16 20;">
        <Label text="Cambiar contraseña"
               style="-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;"/>
    </VBox>

    <!-- Cuerpo blanco -->
    <VBox style="-fx-background-color: white;" spacing="16">
        <padding><Insets top="24" right="24" bottom="24" left="24"/></padding>

        <!-- Campo: Contraseña actual -->
        <VBox spacing="6">
            <Label text="Contraseña actual"
                   style="-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #555;"/>
            <StackPane maxWidth="Infinity">
                <PasswordField fx:id="campoActual" promptText="Contraseña actual"
                               style="-fx-background-color: white; -fx-border-color: #D4D8DE;
                                      -fx-border-radius: 8; -fx-background-radius: 8;
                                      -fx-padding: 13 44 13 14; -fx-font-size: 13px;
                                      -fx-text-fill: #2C3B54; -fx-prompt-text-fill: #A0A8B4;"/>
                <TextField fx:id="campoActualVisible" promptText="Contraseña actual"
                           visible="false" managed="false"
                           style="-fx-background-color: white; -fx-border-color: #D4D8DE;
                                  -fx-border-radius: 8; -fx-background-radius: 8;
                                  -fx-padding: 13 44 13 14; -fx-font-size: 13px;
                                  -fx-text-fill: #2C3B54; -fx-prompt-text-fill: #A0A8B4;"/>
                <Button onAction="#toggleActual" StackPane.alignment="CENTER_RIGHT"
                        style="-fx-background-color: transparent; -fx-border-color: transparent;
                               -fx-cursor: hand; -fx-padding: 0 12 0 0;">
                    <graphic>
                        <ImageView fx:id="imgOjoActual" fitWidth="18" fitHeight="18" preserveRatio="true">
                            <image><Image url="@/images/ojo_activar.png"/></image>
                        </ImageView>
                    </graphic>
                </Button>
            </StackPane>
        </VBox>

        <!-- Campo: Nueva contraseña -->
        <VBox spacing="6">
            <Label text="Nueva contraseña"
                   style="-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #555;"/>
            <StackPane maxWidth="Infinity">
                <PasswordField fx:id="campoNueva" promptText="Nueva contraseña"
                               style="-fx-background-color: white; -fx-border-color: #D4D8DE;
                                      -fx-border-radius: 8; -fx-background-radius: 8;
                                      -fx-padding: 13 44 13 14; -fx-font-size: 13px;
                                      -fx-text-fill: #2C3B54; -fx-prompt-text-fill: #A0A8B4;"/>
                <TextField fx:id="campoNuevaVisible" promptText="Nueva contraseña"
                           visible="false" managed="false"
                           style="-fx-background-color: white; -fx-border-color: #D4D8DE;
                                  -fx-border-radius: 8; -fx-background-radius: 8;
                                  -fx-padding: 13 44 13 14; -fx-font-size: 13px;
                                  -fx-text-fill: #2C3B54; -fx-prompt-text-fill: #A0A8B4;"/>
                <Button onAction="#toggleNueva" StackPane.alignment="CENTER_RIGHT"
                        style="-fx-background-color: transparent; -fx-border-color: transparent;
                               -fx-cursor: hand; -fx-padding: 0 12 0 0;">
                    <graphic>
                        <ImageView fx:id="imgOjoNueva" fitWidth="18" fitHeight="18" preserveRatio="true">
                            <image><Image url="@/images/ojo_activar.png"/></image>
                        </ImageView>
                    </graphic>
                </Button>
            </StackPane>
        </VBox>

        <!-- Campo: Confirmar nueva contraseña -->
        <VBox spacing="6">
            <Label text="Confirmar nueva contraseña"
                   style="-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #555;"/>
            <StackPane maxWidth="Infinity">
                <PasswordField fx:id="campoConfirmar" promptText="Confirmar contraseña"
                               style="-fx-background-color: white; -fx-border-color: #D4D8DE;
                                      -fx-border-radius: 8; -fx-background-radius: 8;
                                      -fx-padding: 13 44 13 14; -fx-font-size: 13px;
                                      -fx-text-fill: #2C3B54; -fx-prompt-text-fill: #A0A8B4;"/>
                <TextField fx:id="campoConfirmarVisible" promptText="Confirmar contraseña"
                           visible="false" managed="false"
                           style="-fx-background-color: white; -fx-border-color: #D4D8DE;
                                  -fx-border-radius: 8; -fx-background-radius: 8;
                                  -fx-padding: 13 44 13 14; -fx-font-size: 13px;
                                  -fx-text-fill: #2C3B54; -fx-prompt-text-fill: #A0A8B4;"/>
                <Button onAction="#toggleConfirmar" StackPane.alignment="CENTER_RIGHT"
                        style="-fx-background-color: transparent; -fx-border-color: transparent;
                               -fx-cursor: hand; -fx-padding: 0 12 0 0;">
                    <graphic>
                        <ImageView fx:id="imgOjoConfirmar" fitWidth="18" fitHeight="18" preserveRatio="true">
                            <image><Image url="@/images/ojo_activar.png"/></image>
                        </ImageView>
                    </graphic>
                </Button>
            </StackPane>
        </VBox>

        <!-- Error inline -->
        <Label fx:id="lblError" text="" visible="false" managed="false" wrapText="true"
               style="-fx-text-fill: #CC0000; -fx-font-size: 12px;"/>

        <!-- Botones -->
        <HBox spacing="8" alignment="CENTER_RIGHT">
            <Button fx:id="btnCancelar" text="Cancelar" onAction="#cancelar"
                    style="-fx-background-color: white; -fx-border-color: #C2C8D0;
                           -fx-border-radius: 6; -fx-background-radius: 6;
                           -fx-font-size: 13px; -fx-padding: 8 18; -fx-cursor: hand;"/>
            <Button fx:id="btnGuardar" text="Guardar" onAction="#guardar"
                    style="-fx-background-color: #001232; -fx-text-fill: white;
                           -fx-border-radius: 6; -fx-background-radius: 6;
                           -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 18; -fx-cursor: hand;"/>
        </HBox>
    </VBox>
</VBox>
```

- [ ] **Step 2: Commit**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
git add src/main/resources/views/CambiarPasswordView.fxml
git commit -m "feat: cambiar-password — CambiarPasswordView.fxml"
```

---

### Task 5: Cliente — CambiarPasswordController.java

**Files:**
- Create: `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente\src\main\java\com\reparaciones\controllers\CambiarPasswordController.java`

**Context:**
- `@FXML` fields match the `fx:id` values defined in Task 4's FXML exactly.
- Static images are loaded once from classpath — same pattern as `LoginController`.
- `toggle()` is a private helper to avoid repeating show/hide logic for each of the 3 fields.
- `getTexto()` reads from whichever of the `PasswordField`/`TextField` pair is currently visible.
- `guardar()` validates in order: non-empty → nueva ≥ 6 chars → nueva == confirmar → call DAO.
- On `401` from server: `ApiClient` throws `SQLException("Sesión expirada. Vuelve a iniciar sesión.")` — catch it by checking `startsWith("Sesión expirada")` and show "Contraseña actual incorrecta.".
- On success: save the Stage reference first, close the modal, then show the `Alert` (both steps happen inside the same FX event handler — JavaFX handles nested event loops correctly).
- `btnGuardar.setDisable(true)` on start, restored in `catch` only — not in `finally` — because on success the stage is closed and there's nothing to re-enable.

- [ ] **Step 1: Create CambiarPasswordController.java**

Create `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente\src\main\java\com\reparaciones\controllers\CambiarPasswordController.java`:

```java
package com.reparaciones.controllers;

import com.reparaciones.dao.UsuarioDAO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.sql.SQLException;

public class CambiarPasswordController {

    private static final Image IMG_OJO_ACTIVAR   = new Image(
            CambiarPasswordController.class.getResourceAsStream("/images/ojo_activar.png"));
    private static final Image IMG_OJO_DESACTIVAR = new Image(
            CambiarPasswordController.class.getResourceAsStream("/images/ojo_desactivar.png"));

    @FXML private PasswordField campoActual;
    @FXML private TextField     campoActualVisible;
    @FXML private ImageView     imgOjoActual;

    @FXML private PasswordField campoNueva;
    @FXML private TextField     campoNuevaVisible;
    @FXML private ImageView     imgOjoNueva;

    @FXML private PasswordField campoConfirmar;
    @FXML private TextField     campoConfirmarVisible;
    @FXML private ImageView     imgOjoConfirmar;

    @FXML private Label  lblError;
    @FXML private Button btnGuardar;
    @FXML private Button btnCancelar;

    private boolean visActual    = false;
    private boolean visNueva     = false;
    private boolean visConfirmar = false;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML private void toggleActual() {
        visActual = !visActual;
        toggle(campoActual, campoActualVisible, imgOjoActual, visActual);
    }

    @FXML private void toggleNueva() {
        visNueva = !visNueva;
        toggle(campoNueva, campoNuevaVisible, imgOjoNueva, visNueva);
    }

    @FXML private void toggleConfirmar() {
        visConfirmar = !visConfirmar;
        toggle(campoConfirmar, campoConfirmarVisible, imgOjoConfirmar, visConfirmar);
    }

    private void toggle(PasswordField pf, TextField tf, ImageView img, boolean mostrar) {
        if (mostrar) {
            tf.setText(pf.getText());
            tf.setVisible(true);  tf.setManaged(true);
            pf.setVisible(false); pf.setManaged(false);
            img.setImage(IMG_OJO_DESACTIVAR);
        } else {
            pf.setText(tf.getText());
            pf.setVisible(true);  pf.setManaged(true);
            tf.setVisible(false); tf.setManaged(false);
            img.setImage(IMG_OJO_ACTIVAR);
        }
    }

    private String getTexto(PasswordField pf, TextField tf) {
        return pf.isVisible() ? pf.getText() : tf.getText();
    }

    @FXML private void cancelar() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }

    @FXML private void guardar() {
        ocultarError();
        String actual    = getTexto(campoActual,    campoActualVisible);
        String nueva     = getTexto(campoNueva,     campoNuevaVisible);
        String confirmar = getTexto(campoConfirmar, campoConfirmarVisible);

        if (actual.isEmpty() || nueva.isEmpty() || confirmar.isEmpty()) {
            mostrarError("Rellena todos los campos.");
            return;
        }
        if (nueva.length() < 6) {
            mostrarError("La contraseña debe tener al menos 6 caracteres.");
            return;
        }
        if (!nueva.equals(confirmar)) {
            mostrarError("Las contraseñas nuevas no coinciden.");
            return;
        }

        btnGuardar.setDisable(true);
        Stage ventana = (Stage) btnGuardar.getScene().getWindow();
        try {
            usuarioDAO.cambiarPassword(actual, nueva);
            ventana.close();
            new Alert(Alert.AlertType.INFORMATION, "Contraseña cambiada correctamente.").showAndWait();
        } catch (SQLException ex) {
            btnGuardar.setDisable(false);
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            mostrarError(msg.startsWith("Sesión expirada")
                    ? "Contraseña actual incorrecta."
                    : msg);
        }
    }

    private void mostrarError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
```

- [ ] **Step 2: Compile client to verify no errors**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
mvn compile -q
```

Expected: exit code 0, no output.

- [ ] **Step 3: Commit**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
git add src/main/java/com/reparaciones/controllers/CambiarPasswordController.java
git commit -m "feat: cambiar-password — CambiarPasswordController"
```

---

### Task 6: Cliente — MainController integration

**Files:**
- Modify: `c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente\src\main\java\com\reparaciones\controllers\MainController.java`

**Context:** Two changes in `MainController.java`:
1. `inicializarMenuUsuario()` — add `MenuItem itemCambiarPassword` inserted between `itemDescargar` and `sep` (the separator before "Cerrar Sesión"). This makes it visible for all roles since there's no role guard.
2. New private method `abrirCambiarPassword()` — follows the exact same pattern as `abrirGestionTecnicos()` (loads FXML, creates `APPLICATION_MODAL` Stage, calls `showAndWait()`). `Modality` is already imported.

- [ ] **Step 1: Modify `inicializarMenuUsuario()`**

Find this exact block in `MainController.java`:

```java
    private void inicializarMenuUsuario() {
        MenuItem itemDescargar = new MenuItem("Descargar CSV");
        SeparatorMenuItem sep  = new SeparatorMenuItem();
        MenuItem itemCerrar    = new MenuItem("Cerrar Sesión");
        itemDescargar.setOnAction(e -> descargarCSV());
        itemCerrar.setOnAction(e -> cerrarSesion());
        menuUsuario = new ContextMenu();
        if (Sesion.esAdmin()) {
            MenuItem itemGestionar = new MenuItem("Gestionar técnicos");
            itemGestionar.setOnAction(e -> abrirGestionTecnicos());
            MenuItem itemLogs = new MenuItem("Ver logs");
            itemLogs.setOnAction(e -> abrirLogs());
            menuUsuario.getItems().addAll(itemGestionar, itemLogs, new SeparatorMenuItem());
        }
        menuUsuario.getItems().addAll(itemDescargar, sep, itemCerrar);
    }
```

Replace with:

```java
    private void inicializarMenuUsuario() {
        MenuItem itemDescargar       = new MenuItem("Descargar CSV");
        MenuItem itemCambiarPassword = new MenuItem("Cambiar contraseña");
        SeparatorMenuItem sep        = new SeparatorMenuItem();
        MenuItem itemCerrar          = new MenuItem("Cerrar Sesión");
        itemDescargar.setOnAction(e -> descargarCSV());
        itemCambiarPassword.setOnAction(e -> abrirCambiarPassword());
        itemCerrar.setOnAction(e -> cerrarSesion());
        menuUsuario = new ContextMenu();
        if (Sesion.esAdmin()) {
            MenuItem itemGestionar = new MenuItem("Gestionar técnicos");
            itemGestionar.setOnAction(e -> abrirGestionTecnicos());
            MenuItem itemLogs = new MenuItem("Ver logs");
            itemLogs.setOnAction(e -> abrirLogs());
            menuUsuario.getItems().addAll(itemGestionar, itemLogs, new SeparatorMenuItem());
        }
        menuUsuario.getItems().addAll(itemDescargar, itemCambiarPassword, sep, itemCerrar);
    }
```

- [ ] **Step 2: Add `abrirCambiarPassword()` method**

Find the closing `}` of the existing `abrirLogs()` method:

```java
    private void abrirLogs() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/LogView.fxml"));
            Parent root = loader.load();
            Stage ventana = new Stage();
            ventana.setTitle("Log de actividad");
            ventana.setScene(new Scene(root));
            ventana.setResizable(true);
            ventana.show();
        } catch (IOException e) {
            mostrarError(e);
        }
    }
```

Insert the new method directly after that closing `}`:

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
        } catch (IOException e) {
            mostrarError(e);
        }
    }
```

- [ ] **Step 3: Compile client to verify no errors**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
mvn compile -q
```

Expected: exit code 0, no output.

- [ ] **Step 4: Commit**

```powershell
cd "c:\Users\info\Documents\ProgramaReparaciones\gestion-reparaciones-cliente"
git add src/main/java/com/reparaciones/controllers/MainController.java
git commit -m "feat: cambiar-password — MainController integration"
```
