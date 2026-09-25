# Aviso IMEI con otro técnico asignado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mostrar un aviso en el modal de reparación (`FormularioReparacionController`) cuando el IMEI de la asignación actual también está asignado activamente a otro técnico.

**Architecture:** Nuevo endpoint `GET /api/reparaciones/asignaciones/imei/{imei}` en el servidor que reutiliza `ASIGNACION_SELECT` filtrando por IMEI. El cliente llama a este endpoint al abrir el modal (`init()`), descarta la asignación propia, y muestra un `HBox` con el/los nombre(s) del técnico conflictivo. El aviso se sitúa justo bajo el IMEI, antes del aviso de incidencia y del borrador.

**Tech Stack:** Spring Boot (servidor), JavaFX + FXML (cliente), MariaDB, `JdbcTemplate`, `ApiClient`.

---

## Ficheros afectados

| Repo | Fichero | Acción |
|---|---|---|
| servidor | `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` | Añadir método `getAsignacionesPorImei` |
| servidor | `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` | Añadir endpoint `GET /asignaciones/imei/{imei}` |
| cliente | `src/main/java/com/reparaciones/dao/ReparacionDAO.java` | Añadir método `getAsignacionesPorImei` |
| cliente | `src/main/resources/views/FormularioReparacionView.fxml` | Añadir `filaConflictoTecnico` HBox |
| cliente | `src/main/java/com/reparaciones/controllers/FormularioReparacionController.java` | Añadir campos FXML + lógica en `init()` |

---

## Task 1: Método DAO en el servidor

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (tras el método `getAsignacionById`, ~línea 184)

- [ ] **Step 1: Añadir el método**

Insertar inmediatamente después del cierre de `getAsignacionById`:

```java
public List<ReparacionResumen> getAsignacionesPorImei(String imei) {
    String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                     " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, r.URGENTE" +
                     " ORDER BY r.FECHA_ASIG ASC";
    return jdbc.query(ASIGNACION_SELECT + " AND r.IMEI = ?" + groupBy, RESUMEN_MAPPER, imei);
}
```

`ASIGNACION_SELECT` ya filtra `ID_REP LIKE 'A%'`, `ID_REP NOT LIKE 'AP%'` y `FECHA_FIN IS NULL`, así que este método devuelve solo asignaciones activas.

- [ ] **Step 2: Compilar el servidor para verificar que no hay errores**

```bash
cd gestion-reparaciones-servidor
mvn compile -q
```

Salida esperada: sin errores.

---

## Task 2: Endpoint REST en el servidor

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` (tras `getAsignacionById`, ~línea 79)

- [ ] **Step 1: Añadir el endpoint**

Insertar después del método `getAsignacionById`:

```java
@GetMapping("/asignaciones/imei/{imei}")
public List<ReparacionResumen> getAsignacionesPorImei(@PathVariable String imei) {
    return dao.getAsignacionesPorImei(imei);
}
```

- [ ] **Step 2: Compilar y arrancar el servidor**

```bash
mvn compile -q
mvn spring-boot:run
```

- [ ] **Step 3: Verificar manualmente el endpoint**

Con el servidor arrancado, abrir un IMEI que tenga al menos una asignación activa (ID_REP empezando por "A") y llamar desde el navegador o Postman:

```
GET http://localhost:8080/api/reparaciones/asignaciones/imei/<IMEI>
```

Respuesta esperada: array JSON con los campos `idRep`, `imei`, `nombreTecnico`, etc. Si el IMEI no tiene asignaciones activas devuelve `[]`.

- [ ] **Step 4: Commit en el servidor**

```bash
git add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git add src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat: endpoint GET /api/reparaciones/asignaciones/imei/{imei}"
```

---

## Task 3: Método DAO en el cliente

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java`

Buscar la sección `// ── Lectura: asignaciones ─────` y localizar `getAsignacionesPorTecnico`. Añadir el nuevo método justo después:

- [ ] **Step 1: Añadir el método**

```java
/**
 * Devuelve las asignaciones activas para un IMEI concreto.
 *
 * @param imei IMEI del dispositivo
 * @return lista de asignaciones activas para ese IMEI
 * @throws SQLException si falla la llamada al servidor
 */
public List<ReparacionResumen> getAsignacionesPorImei(String imei) throws SQLException {
    return ApiClient.getList("/api/reparaciones/asignaciones/imei/" + imei, ReparacionResumen.class);
}
```

- [ ] **Step 2: Compilar el cliente**

```bash
cd gestion-reparaciones-cliente
mvn compile -q
```

Salida esperada: sin errores.

---

## Task 4: Nuevo nodo FXML en el modal

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/FormularioReparacionView.fxml`

El archivo tiene esta estructura en la cabecera (líneas 14-33):

```xml
<!-- Cabecera info -->
<HBox ...>  <!-- lblImei + cbFiltroModelo -->
</HBox>

<!-- Fila de incidencia pendiente -->
<HBox fx:id="filaIncidencia" ...>
    <Label fx:id="lblIncidencia" .../>
</HBox>
```

- [ ] **Step 1: Insertar `filaConflictoTecnico` entre la cabecera y `filaIncidencia`**

Añadir el bloque siguiente inmediatamente después del cierre del `</HBox>` de la cabecera info y antes del comentario `<!-- Fila de incidencia pendiente -->`:

```xml
<!-- Aviso: mismo IMEI asignado a otro técnico -->
<HBox alignment="CENTER_LEFT"
      style="-fx-padding: 5 16 5 16; -fx-background-color: #FFF3E0;"
      visible="false" managed="false" fx:id="filaConflictoTecnico">
    <Label fx:id="lblConflictoTecnico"
           style="-fx-text-fill: #E65100; -fx-font-size: 12px; -fx-font-weight: bold;"
           wrapText="true" maxWidth="Infinity" HBox.hgrow="ALWAYS"/>
</HBox>
```

El resultado final de esa zona del FXML debe quedar así:

```xml
<!-- Cabecera info -->
<HBox alignment="CENTER_LEFT" spacing="16"
      style="-fx-background-color: #C8CDD6; -fx-padding: 10 16 10 16;">
    <Label fx:id="lblImei" style="-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2C3B54;"/>
    <HBox HBox.hgrow="ALWAYS"/>
    <Label text="Filtrar por modelo:"
           style="-fx-font-size: 12px; -fx-text-fill: #586376;"/>
    <ComboBox fx:id="cbFiltroModelo"
              prefWidth="180"
              style="-fx-font-size: 11px;"/>
</HBox>

<!-- Aviso: mismo IMEI asignado a otro técnico -->
<HBox alignment="CENTER_LEFT"
      style="-fx-padding: 5 16 5 16; -fx-background-color: #FFF3E0;"
      visible="false" managed="false" fx:id="filaConflictoTecnico">
    <Label fx:id="lblConflictoTecnico"
           style="-fx-text-fill: #E65100; -fx-font-size: 12px; -fx-font-weight: bold;"
           wrapText="true" maxWidth="Infinity" HBox.hgrow="ALWAYS"/>
</HBox>

<!-- Fila de incidencia pendiente -->
<HBox alignment="CENTER_LEFT"
      style="-fx-padding: 5 16 5 16;"
      visible="false" managed="false" fx:id="filaIncidencia">
    <Label fx:id="lblIncidencia"
           style="-fx-text-fill: #CC4444; -fx-font-size: 12px;"
           wrapText="true" maxWidth="Infinity" HBox.hgrow="ALWAYS"/>
</HBox>
```

---

## Task 5: Lógica en el controlador

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

- [ ] **Step 1: Añadir import de `ReparacionResumen`**

Insertar entre los imports existentes de `com.reparaciones.models.*`:

```java
import com.reparaciones.models.ReparacionResumen;
```

- [ ] **Step 2: Declarar los campos `@FXML`**

Justo después de la línea:
```java
@FXML private javafx.scene.layout.HBox filaIncidencia;
```
Añadir:
```java
@FXML private javafx.scene.layout.HBox filaConflictoTecnico;
@FXML private Label lblConflictoTecnico;
```

- [ ] **Step 3: Añadir la comprobación en `init()`**

El método `init()` actualmente tiene este bloque tras `lblImei.setText(...)`:

```java
lblImei.setText("IMEI: " + imei);
if (this.idRepAnterior != null) {
    lblIncidencia.setText("⚠ Resuelve incidencia: " + this.idRepAnterior);
    filaIncidencia.setVisible(true);
    filaIncidencia.setManaged(true);
}
```

Añadir el chequeo de conflicto **justo después de `lblImei.setText(...)` y antes del bloque `if (this.idRepAnterior != null)`**:

```java
lblImei.setText("IMEI: " + imei);

// Aviso si el mismo IMEI está asignado activamente a otro técnico
try {
    List<ReparacionResumen> otras = reparacionDAO.getAsignacionesPorImei(imei)
            .stream()
            .filter(a -> !a.getIdRep().equals(idAsignacion))
            .collect(Collectors.toList());
    if (!otras.isEmpty()) {
        String nombres = otras.stream()
                .map(ReparacionResumen::getNombreTecnico)
                .distinct()
                .collect(Collectors.joining(", "));
        lblConflictoTecnico.setText("⚠ Este IMEI también está asignado a: " + nombres);
        filaConflictoTecnico.setVisible(true);
        filaConflictoTecnico.setManaged(true);
    }
} catch (SQLException e) {
    // no crítico: el modal sigue funcionando aunque falle esta consulta
}

if (this.idRepAnterior != null) {
    lblIncidencia.setText("⚠ Resuelve incidencia: " + this.idRepAnterior);
    filaIncidencia.setVisible(true);
    filaIncidencia.setManaged(true);
}
```

- [ ] **Step 4: Compilar**

```bash
mvn compile -q
```

Salida esperada: sin errores.

- [ ] **Step 5: Prueba manual**

1. Crear (o buscar) un IMEI que tenga dos asignaciones activas con técnicos distintos.
2. Abrir el modal de uno de ellos.
3. Verificar que aparece el banner naranja con el texto `"⚠ Este IMEI también está asignado a: [Nombre]"`.
4. Abrir el modal de un IMEI sin conflicto y verificar que no aparece el banner.
5. Verificar que el banner de incidencia y el de borrador siguen funcionando con independencia.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
git add src/main/resources/views/FormularioReparacionView.fxml
git add src/main/java/com/reparaciones/dao/ReparacionDAO.java
git commit -m "feat: aviso en modal cuando el IMEI tiene otra asignacion activa con distinto tecnico"
```
