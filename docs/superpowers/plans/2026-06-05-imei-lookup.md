# IMEI Lookup — Auto-relleno del modelo al crear asignación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrar alpha.imeicheck.com para auto-detectar el modelo del iPhone al escribir el IMEI en el formulario de asignación, con fallback manual siempre disponible.

**Architecture:** El servidor expone `GET /api/telefonos/{imei}/modelo`; si el modelo no está en BD llama a imeicheck, convierte el nombre comercial ("iPhone 12 Pro") al código interno ("12pro") y lo devuelve. El cliente hace el lookup en background al completar 15 dígitos y pre-selecciona el campo modelo. Para asignaciones de pulido el servidor guarda el modelo en BD automáticamente. Un menú contextual en la tabla de pulido permite corrección manual vía selector de lista.

**Tech Stack:** Spring Boot (servidor) — `java.net.http.HttpClient` + Jackson; JavaFX (cliente) — hilo daemon + `Platform.runLater`.

---

## Archivos afectados

| Fichero | Acción |
|---|---|
| `servidor/src/main/resources/application.properties` | Modificar — añadir propiedad key |
| `servidor/src/main/java/…/service/ImeiLookupService.java` | **Crear** |
| `servidor/src/test/java/…/service/ImeiLookupServiceTest.java` | **Crear** |
| `servidor/src/main/java/…/controller/TelefonoController.java` | Modificar — inyectar service, fix null |
| `servidor/src/main/java/…/controller/PulidoController.java` | Modificar — auto-guardar modelo en pulido |
| `cliente/src/main/java/…/controllers/PendientesSuperTecnicoController.java` | Modificar — lookup background en IMEI |
| `cliente/src/main/java/…/controllers/PulidoSuperTecnicoController.java` | Modificar — "Editar modelo" en menú contextual |

---

## Task 1: ImeiLookupService — conversión comercial→interno

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/ImeiLookupService.java`
- Create: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/service/ImeiLookupServiceTest.java`

- [ ] **Step 1: Escribir el test de conversión (falla porque la clase no existe)**

```java
// gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/service/ImeiLookupServiceTest.java
package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ImeiLookupServiceTest {

    private final ImeiLookupService service = new ImeiLookupService("");

    @Test
    void convierte_nombres_comerciales_a_interno() {
        assertThat(service.comercialACodigoInterno("Apple iPhone 12 Pro")).isEqualTo("12pro");
        assertThat(service.comercialACodigoInterno("iPhone 13")).isEqualTo("13");
        assertThat(service.comercialACodigoInterno("iPhone XS Max")).isEqualTo("xsmax");
        assertThat(service.comercialACodigoInterno("iPhone SE 2020")).isEqualTo("se2020");
        assertThat(service.comercialACodigoInterno("iPhone 16e")).isEqualTo("16e");
        assertThat(service.comercialACodigoInterno("iPhone 14 Pro Max")).isEqualTo("14promax");
        assertThat(service.comercialACodigoInterno("iPhone 13 Mini")).isEqualTo("13mini");
    }

    @Test
    void android_devuelve_null() {
        assertThat(service.comercialACodigoInterno("Samsung Galaxy S24")).isNull();
        assertThat(service.comercialACodigoInterno("Moto G22")).isNull();
    }

    @Test
    void key_vacia_devuelve_null_sin_llamar_a_la_api() {
        // El service tiene key vacía → lookupModeloInterno no hace llamada HTTP
        assertThat(service.lookupModeloInterno("352322311421731")).isNull();
    }
}
```

- [ ] **Step 2: Ejecutar el test para verificar que falla (compilación)**

```
cd gestion-reparaciones-servidor
mvn test -Dtest=ImeiLookupServiceTest -pl .
```
Esperado: error de compilación — clase no existe.

- [ ] **Step 3: Crear ImeiLookupService**

```java
// gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/ImeiLookupService.java
package com.reparaciones.servidor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImeiLookupService {

    private static final Logger log = LoggerFactory.getLogger(ImeiLookupService.class);

    private static final String URL_BASE =
            "https://alpha.imeicheck.com/api/free_with_key/modelBrandName";

    // Lista sincronizada con FormularioReparacionController.MODELOS_ORDENADOS (cliente).
    // Al añadir modelos nuevos, actualizarlos en AMBOS sitios.
    private static final List<String> MODELOS_ORDENADOS = List.of(
            "6s", "6splus", "7", "7plus", "8", "8plus", "se2020",
            "x", "xr", "xs", "xsmax",
            "11", "11pro", "11promax",
            "12", "12mini", "12pro", "12promax",
            "13", "13mini", "13pro", "13promax",
            "14", "14plus", "14pro", "14promax",
            "15", "15plus", "15pro", "15promax",
            "16", "16e", "16plus", "16pro", "16promax"
    );

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> mapaComercialInterno;

    public ImeiLookupService(@Value("${imeicheck.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.mapaComercialInterno = construirMapa();
    }

    /**
     * Devuelve el código interno del modelo (ej. "12pro") a partir del IMEI,
     * o null si la key está vacía, el IMEI no se reconoce, o la API falla.
     * Nunca lanza excepción.
     */
    public String lookupModeloInterno(String imei) {
        if (apiKey == null || apiKey.isBlank()) return null;
        try {
            String url = URL_BASE + "?key=" + apiKey + "&imei=" + imei + "&format=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode obj = root.get("object");
            if (obj == null) return null;

            JsonNode brand = obj.get("brand");
            if (brand == null || !brand.asText().equalsIgnoreCase("Apple")) return null;

            JsonNode name = obj.get("name");
            if (name == null) return null;

            return comercialACodigoInterno(name.asText());
        } catch (Exception e) {
            log.warn("IMEI lookup fallido para {}: {}", imei, e.getMessage());
            return null;
        }
    }

    /** Visible para tests: convierte nombre comercial al código interno o null si no hay match. */
    String comercialACodigoInterno(String nombreComercial) {
        return mapaComercialInterno.get(normalizar(nombreComercial));
    }

    private Map<String, String> construirMapa() {
        Map<String, String> mapa = new HashMap<>();
        for (String codigo : MODELOS_ORDENADOS) {
            mapa.put(normalizar(traducirModelo(codigo)), codigo);
        }
        return mapa;
    }

    static String normalizar(String s) {
        return s.toLowerCase()
                .replace("apple", "")
                .replace("iphone", "")
                .replaceAll("\\s+", "");
    }

    static String traducirModelo(String modelo) {
        return switch (modelo) {
            case "se2020" -> "iPhone SE 2020";
            case "x"      -> "iPhone X";
            case "xr"     -> "iPhone XR";
            case "xs"     -> "iPhone XS";
            case "xsmax"  -> "iPhone XS Max";
            case "6s"     -> "iPhone 6S";
            case "6splus" -> "iPhone 6S Plus";
            default -> {
                String num      = modelo.replaceAll("[^0-9]", "");
                String variante = modelo.replaceAll("[0-9]", "");
                String sufijo   = switch (variante) {
                    case "plus"   -> " Plus";
                    case "mini"   -> " Mini";
                    case "pro"    -> " Pro";
                    case "promax" -> " Pro Max";
                    case "e"      -> "e";
                    default       -> "";
                };
                yield "iPhone " + num + sufijo;
            }
        };
    }
}
```

- [ ] **Step 4: Ejecutar test para verificar que pasa**

```
cd gestion-reparaciones-servidor
mvn test -Dtest=ImeiLookupServiceTest -pl .
```
Esperado: 3 tests — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/service/ImeiLookupService.java
git -C gestion-reparaciones-servidor add src/test/java/com/reparaciones/servidor/service/ImeiLookupServiceTest.java
git -C gestion-reparaciones-servidor commit -m "feat: ImeiLookupService — conversión comercial a código interno"
```

---

## Task 2: application.properties + TelefonoController

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/resources/application.properties`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/TelefonoController.java`

- [ ] **Step 1: Añadir propiedad en application.properties**

Añadir al final de `application.properties`:
```properties
# ── IMEI Lookup ───────────────────────────────────────────────────────────────
# En la VM: poner la key real. En desarrollo local: dejar vacío (no llamará a la API).
imeicheck.api-key=
```

- [ ] **Step 2: Modificar TelefonoController**

Reemplazar el archivo completo con:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.Telefono;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telefonos")
public class TelefonoController {

    private final TelefonoDAO dao;
    private final ImeiLookupService imeiLookupService;

    public TelefonoController(TelefonoDAO dao, ImeiLookupService imeiLookupService) {
        this.dao = dao;
        this.imeiLookupService = imeiLookupService;
    }

    @GetMapping
    public List<Telefono> getAll() {
        return dao.getAll();
    }

    @GetMapping("/{imei}/exists")
    public Map<String, Boolean> exists(@PathVariable String imei) {
        return Map.of("value", dao.exists(imei));
    }

    @GetMapping("/{imei}/modelo")
    public Map<String, String> getModelo(@PathVariable String imei) {
        String modelo = dao.getModelo(imei);
        if (modelo == null || modelo.isBlank()) {
            modelo = imeiLookupService.lookupModeloInterno(imei);
        }
        return Map.of("value", modelo != null ? modelo : "");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody ImeiRequest req) {
        dao.insertar(req.imei(), req.modelo());
    }

    @PatchMapping("/{imei}/observacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void actualizarObservacion(@PathVariable String imei,
                                      @RequestBody ObservacionRequest req) {
        dao.actualizarObservacion(imei, req.observacion());
    }

    @DeleteMapping("/{imei}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String imei) {
        dao.eliminar(imei);
    }

    private record ImeiRequest(String imei, String modelo) {}
    private record ObservacionRequest(String observacion) {}
}
```

- [ ] **Step 3: Compilar el servidor**

```
cd gestion-reparaciones-servidor
mvn compile -q
```
Esperado: BUILD SUCCESS sin errores.

- [ ] **Step 4: Commit**

```
git -C gestion-reparaciones-servidor add src/main/resources/application.properties
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/controller/TelefonoController.java
git -C gestion-reparaciones-servidor commit -m "feat: TelefonoController usa ImeiLookupService; fix devolver string null"
```

---

## Task 3: PulidoController — auto-guardar modelo en asignaciones de pulido

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/PulidoController.java`

El método `insertarAsignacion` (línea ~43) llama a `dao.insertarAsignacionPulido`. Antes de esa llamada, si el teléfono no tiene modelo, se intenta el lookup y se guarda.

- [ ] **Step 1: Leer el archivo para no sobreescribir imports existentes**

Leer `PulidoController.java` completo.

- [ ] **Step 2: Inyectar TelefonoDAO e ImeiLookupService en el constructor**

Localizar la declaración del constructor actual (que solo tiene `ReparacionDAO dao` y `LogDAO logDao`) y reemplazarla por:

```java
private final com.reparaciones.servidor.dao.ReparacionDAO dao;
private final com.reparaciones.servidor.dao.LogDAO logDao;
private final com.reparaciones.servidor.dao.TelefonoDAO telefonoDAO;
private final com.reparaciones.servidor.service.ImeiLookupService imeiLookupService;

public PulidoController(com.reparaciones.servidor.dao.ReparacionDAO dao,
                         com.reparaciones.servidor.dao.LogDAO logDao,
                         com.reparaciones.servidor.dao.TelefonoDAO telefonoDAO,
                         com.reparaciones.servidor.service.ImeiLookupService imeiLookupService) {
    this.dao = dao;
    this.logDao = logDao;
    this.telefonoDAO = telefonoDAO;
    this.imeiLookupService = imeiLookupService;
}
```
(Añadir también los imports necesarios al principio si no están ya.)

- [ ] **Step 3: Modificar insertarAsignacion para auto-guardar modelo**

Localizar el método `insertarAsignacion` y reemplazar solo su cuerpo:

```java
@PostMapping("/asignacion")
@ResponseStatus(HttpStatus.CREATED)
public Map<String, Object> insertarAsignacion(@RequestBody AsignacionPulidoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
    if (telefonoDAO.getModelo(req.imei()) == null) {
        String modelo = imeiLookupService.lookupModeloInterno(req.imei());
        if (modelo != null) {
            telefonoDAO.insertar(req.imei(), modelo);
        }
    }
    String idRep = dao.insertarAsignacionPulido(req.imei(), req.idTec(), req.comentario());
    logDao.insertar(principal.getIdUsu(), "CREAR_ASIGNACION_PULIDO",
            "ID_REP: " + idRep + ", IMEI: " + req.imei() + ", ID_TEC: " + req.idTec());
    return Map.of("value", idRep);
}
```

- [ ] **Step 4: Compilar**

```
cd gestion-reparaciones-servidor
mvn compile -q
```
Esperado: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/controller/PulidoController.java
git -C gestion-reparaciones-servidor commit -m "feat: PulidoController auto-guarda modelo iPhone via IMEI lookup al crear asignacion"
```

---

## Task 4: PendientesSuperTecnicoController — lookup en background al escribir IMEI

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

El campo `tfImei` ya tiene un listener en la línea ~738. Cuando el IMEI llega a 15 dígitos y `modeloSel[0]` es null (el técnico no ha seleccionado nada todavía), se lanza un hilo daemon para pedir el modelo al servidor.

- [ ] **Step 1: Localizar el listener de tfImei**

Buscar:
```java
tfImei.textProperty().addListener((obs, o, n) -> {
    if (!n.matches("\\d*")) tfImei.setText(n.replaceAll("[^\\d]", ""));
    if (tfImei.getText().length() > 15) tfImei.setText(tfImei.getText().substring(0, 15));
    validar.run();
});
```

- [ ] **Step 2: Reemplazar el listener con la versión con lookup**

```java
tfImei.textProperty().addListener((obs, o, n) -> {
    if (!n.matches("\\d*")) tfImei.setText(n.replaceAll("[^\\d]", ""));
    if (tfImei.getText().length() > 15) tfImei.setText(tfImei.getText().substring(0, 15));
    validar.run();
    String imeiActual = tfImei.getText();
    if (imeiActual.length() == 15 && modeloSel[0] == null) {
        Thread t = new Thread(() -> {
            try {
                String modelo = telefonoDAO.getModelo(imeiActual);
                if (modelo != null && !modelo.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        // Solo aplicar si el IMEI no ha cambiado y el modelo sigue vacío
                        if (tfImei.getText().equals(imeiActual) && modeloSel[0] == null) {
                            confirmarModelo.accept(modelo);
                        }
                    });
                }
            } catch (Exception ex) { /* silencioso: el técnico puede seleccionar manualmente */ }
        });
        t.setDaemon(true);
        t.start();
    }
});
```

Nota: `telefonoDAO` ya existe como field en este controller (se usa en línea ~821 con `telefonoDAO.insertar`). `confirmarModelo` está definido justo antes del listener.

- [ ] **Step 3: Compilar el cliente**

```
cd gestion-reparaciones-cliente
mvn compile -q
```
Esperado: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git -C gestion-reparaciones-cliente commit -m "feat: auto-relleno del modelo al escribir IMEI en formulario de asignacion"
```

---

## Task 5: PulidoSuperTecnicoController — menú contextual "Editar modelo"

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java`

El menú contextual ya tiene "Copiar celda" y "Editar comentario". Añadir "Editar modelo" que abre un diálogo con selector de lista filtrable.

- [ ] **Step 1: Localizar donde se añaden los items al menú**

Buscar:
```java
menu.getItems().add(editarComentario);
setContextMenu(menu);
```

- [ ] **Step 2: Añadir el MenuItem "Editar modelo" entre editarComentario y setContextMenu**

Insertar justo antes de `setContextMenu(menu)`:

```java
MenuItem editarModelo = new MenuItem("Editar modelo");
ImageView ivEditarModelo = new ImageView(imgEditar);
ivEditarModelo.setFitWidth(14); ivEditarModelo.setFitHeight(14); ivEditarModelo.setPreserveRatio(true);
editarModelo.setGraphic(ivEditarModelo);
editarModelo.setOnAction(e -> {
    if (getItem() == null) return;
    ReparacionResumen rep = getItem();
    abrirSelectorModelo(rep);
});
menu.getItems().add(editarModelo);
```

- [ ] **Step 3: Añadir el método abrirSelectorModelo en la clase**

Añadir como método privado en `PulidoSuperTecnicoController` (fuera del rowFactory):

```java
private void abrirSelectorModelo(ReparacionResumen rep) {
    javafx.collections.ObservableList<String> todos =
            javafx.collections.FXCollections.observableArrayList(
                    com.reparaciones.controllers.FormularioReparacionController.MODELOS_ORDENADOS);
    javafx.collections.transformation.FilteredList<String> filtrados =
            new javafx.collections.transformation.FilteredList<>(todos, s -> true);

    javafx.scene.control.TextField tfFiltro = new javafx.scene.control.TextField();
    tfFiltro.setPromptText("Filtrar modelo…");
    tfFiltro.textProperty().addListener((obs, o, n) -> {
        String lower = n == null ? "" : n.trim().toLowerCase();
        filtrados.setPredicate(c -> lower.isEmpty()
                || com.reparaciones.controllers.FormularioReparacionController
                        .traducirModelo(c).toLowerCase().contains(lower));
    });

    javafx.scene.control.ListView<String> lista = new javafx.scene.control.ListView<>(filtrados);
    lista.setPrefHeight(220);
    lista.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
        @Override protected void updateItem(String m, boolean empty) {
            super.updateItem(m, empty);
            setText((empty || m == null) ? null
                    : com.reparaciones.controllers.FormularioReparacionController.traducirModelo(m));
        }
    });
    if (rep.getModelo() != null && !rep.getModelo().isEmpty()) {
        lista.getSelectionModel().select(rep.getModelo());
        lista.scrollTo(rep.getModelo());
    }

    javafx.scene.control.Button btnConfirmar = new javafx.scene.control.Button("Guardar");
    javafx.scene.control.Button btnCancelar  = new javafx.scene.control.Button("Cancelar");
    btnConfirmar.disableProperty().bind(lista.getSelectionModel().selectedItemProperty().isNull());

    javafx.scene.layout.VBox contenido = new javafx.scene.layout.VBox(10,
            new javafx.scene.control.Label("Selecciona el modelo:"),
            tfFiltro, lista,
            new javafx.scene.layout.HBox(10, btnCancelar, btnConfirmar));
    contenido.setPadding(new javafx.geometry.Insets(20));
    contenido.setPrefWidth(320);
    contenido.setStyle("-fx-background-color: #DDE1E7;");

    javafx.stage.Stage ventana = new javafx.stage.Stage();
    ventana.setTitle("Editar modelo");
    ventana.initModality(javafx.stage.Modality.APPLICATION_MODAL);
    javafx.scene.Scene scene = new javafx.scene.Scene(contenido);
    scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
    ventana.setScene(scene);

    btnCancelar.setOnAction(ev -> ventana.close());
    btnConfirmar.setOnAction(ev -> {
        String codigoInterno = lista.getSelectionModel().getSelectedItem();
        if (codigoInterno == null) return;
        try {
            new com.reparaciones.dao.TelefonoDAO().insertar(rep.getImei(), codigoInterno);
            ventana.close();
            cargarDatos();
        } catch (java.sql.SQLException ex) {
            com.reparaciones.utils.Alertas.mostrarError(ex.getMessage());
        }
    });

    ventana.showAndWait();
}
```

- [ ] **Step 4: Compilar el cliente**

```
cd gestion-reparaciones-cliente
mvn compile -q
```
Esperado: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/PulidoSuperTecnicoController.java
git -C gestion-reparaciones-cliente commit -m "feat: menu contextual Editar modelo en tabla de pulido"
```

---

## Verificación manual (en la VM con la key real)

1. Añadir `imeicheck.api-key=K3MF-PZ67-RTWA-XN5C-DL8V-92JQ` en `application.properties` de la VM. Rebuild servidor.
2. **Asignación normal**: escribir un IMEI de iPhone conocido → el campo modelo se auto-rellena sin congelar el modal.
3. Cambiar el modelo a mano → se guarda el elegido (no el de la API).
4. Segunda asignación con el mismo IMEI → modelo viene de BD, sin llamada a la API.
5. IMEI de Android o desconocido → campo modelo queda vacío, sin error.
6. **Asignación de pulido**: crear con IMEI nuevo de iPhone → el modelo aparece relleno en la tabla tras crear.
7. Clic derecho en una fila de pulido → "Editar modelo" → selector de lista → cambiar → se refleja en la tabla.
8. Confirmar que la key NO está en ningún commit: `git -C gestion-reparaciones-servidor log --all -p -- src/main/resources/application.properties | grep api-key`
