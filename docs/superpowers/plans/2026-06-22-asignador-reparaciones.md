# Mostrar quién asignó la reparación — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mostrar en las asignaciones y en el historial (modo plano) quién asignó la reparación al técnico; al reasignar a otro técnico, mostrar al reasignador.

**Architecture:** Nueva columna `Reparacion.ID_TEC_ASIGNA` (FK a `Tecnico`). El servidor la escribe con `principal.getIdTec()` al crear/reasignar y la copia a las filas `R*` al completar; la lee con `LEFT JOIN Tecnico`. El cliente añade una columna "Asignado por" en pendientes (siempre) e historial (solo modo plano/detalle, siguiendo la visibilidad de `colReparador`).

**Tech Stack:** MariaDB, Spring Boot (JdbcTemplate), JavaFX 17, Java HttpClient, Gson.

## Global Constraints

- No añadir librerías externas nuevas.
- `ID_TEC_ASIGNA` es `NULL`able: datos antiguos y asignadores sin técnico asociado quedan null → UI muestra `—`.
- "Reasignar" = `actualizarAsignacion` cambia el `ID_TEC`. Editar solo el comentario no actualiza el asignador.
- La columna en el historial se muestra solo donde la fila es una reparación individual (modos DETALLE y PLANO), nunca en MAESTRO (agrupado por IMEI).
- Servidor y cliente se despliegan juntos. La migración SQL se aplica manualmente en preproducción (único entorno) antes de desplegar.
- Nombre del campo en modelos: `nombreTecnicoAsigna`. Alias SQL: `NOMBRE_TEC_ASIGNA`. Columna BD: `ID_TEC_ASIGNA`.

---

## Task 1: BD + lectura servidor (migración, modelo, SELECTs, mapper)

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/resources/db/migracion-asignador.sql`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Produces: `ReparacionResumen.getNombreTecnicoAsigna()` / `setNombreTecnicoAsigna(String)` — usado en Tasks 5, 6 (vía JSON al cliente)
- Produces: alias SQL `NOMBRE_TEC_ASIGNA` en `ASIGNACION_SELECT` y `HISTORIAL_SELECT`

- [ ] **Step 1: Crear el script de migración**

Crear `gestion-reparaciones-servidor/src/main/resources/db/migracion-asignador.sql`:
```sql
ALTER TABLE Reparacion ADD COLUMN ID_TEC_ASIGNA INT NULL,
  ADD CONSTRAINT fk_rep_tec_asigna FOREIGN KEY (ID_TEC_ASIGNA) REFERENCES Tecnico(ID_TEC);
```

- [ ] **Step 2: Añadir campo al modelo servidor**

En `ReparacionResumen.java`, añadir el campo junto a los otros opcionales (tras `private boolean tieneAsignaciones;`):
```java
    private String        nombreTecnicoAsigna;
```
Y el getter/setter junto a los demás (tras `setTieneAsignaciones`):
```java
    public String        getNombreTecnicoAsigna()             { return nombreTecnicoAsigna; }
    public void          setNombreTecnicoAsigna(String v)     { this.nombreTecnicoAsigna = v; }
```

- [ ] **Step 3: Rellenar el campo en el mapper**

En `ReparacionDAO.java`, dentro de `RESUMEN_MAPPER`, junto a los otros campos opcionales protegidos con try/catch (tras la línea `try { rr.setTieneAsignaciones(rs.getInt("TIENE_ASIGNACIONES") > 0); } catch (Exception ignored) {}`):
```java
        try { rr.setNombreTecnicoAsigna(rs.getString("NOMBRE_TEC_ASIGNA")); } catch (Exception ignored) {}
```
(El try/catch protege los SELECT que no traen la columna, p.ej. los de pulido.)

- [ ] **Step 4: Añadir JOIN y columna a HISTORIAL_SELECT**

En `HISTORIAL_SELECT`, añadir el alias en la lista de columnas. Buscar la línea que termina la lista de columnas antes de `" FROM Reparacion r"`:
```java
            " r.UPDATED_AT, tel.MODELO, NULL AS COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO," +
            " COALESCE(tel.REVISION_LOGISTICA, 0) AS REVISION_LOGISTICA," +
```
Tras la línea de `REVISION_LOGISTICA` (y antes de la subconsulta `TIENE_ASIGNACIONES`), añadir:
```java
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
```
Y en la cláusula FROM, tras `" JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +`, añadir:
```java
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
```

- [ ] **Step 5: Añadir JOIN y columna a ASIGNACION_SELECT**

En `ASIGNACION_SELECT`, en la lista de columnas, tras `" r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +` y la línea `" tel.OBSERVACION AS OBSERVACION_TELEFONO, r.URGENTE" +`, cambiar esa última para insertar el alias antes de `" FROM Reparacion r"`. Es decir, tras `... r.URGENTE" +` añadir:
```java
            " , ta.NOMBRE AS NOMBRE_TEC_ASIGNA" +
```
Y en la cláusula FROM de `ASIGNACION_SELECT`, tras `" JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +`, añadir:
```java
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
```

- [ ] **Step 6: Compilar el servidor**

Run: `cd gestion-reparaciones-servidor && mvn compile -q`
Expected: BUILD SUCCESS sin errores.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/resources/db/migracion-asignador.sql \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(asignador): columna ID_TEC_ASIGNA + lectura con JOIN Tecnico en asignaciones e historial"
```

---

## Task 2: Escritura servidor — crear y reasignar

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

**Interfaces:**
- Consumes: columna `ID_TEC_ASIGNA` (Task 1)
- Produces: firmas `insertarAsignacion(String, int, String, Integer)`, `marcarIncidenciaYAsignar(String, String, String, int, Integer)`, `actualizarAsignacion(String, int, String, LocalDateTime, Integer)` — el último parámetro `Integer idTecAsigna` es `principal.getIdTec()`

- [ ] **Step 1: Modificar `insertarAsignacion` en el DAO**

Reemplazar el método `insertarAsignacion`:
```java
    public String insertarAsignacion(String imei, int idTec, String comentario, Integer idTecAsigna) {
        ensureTelefono(imei);
        String idRep = nextId("A");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA) VALUES (?,?,?,NOW(),?,?)",
                idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null, idTecAsigna);
        jdbc.update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", imei);
        return idRep;
    }
```

- [ ] **Step 2: Modificar `marcarIncidenciaYAsignar` en el DAO**

En el método `marcarIncidenciaYAsignar`, cambiar la firma para añadir `Integer idTecAsigna` como último parámetro, y el `INSERT` de la nueva asignación. Reemplazar la firma y el INSERT:
```java
    public void marcarIncidenciaYAsignar(String idRep, String comentario, String imei, int idTec, Integer idTecAsigna) {
```
y el INSERT de la asignación nueva:
```java
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA) VALUES (?,?,?,?,NOW(),?,?)",
                idAsig, imei, idTec, idRep, comentario, idTecAsigna);
```
(El resto del método —`ensureTelefono`, `nextId("A")`, el `UPDATE Telefono`— se mantiene igual.)

- [ ] **Step 3: Modificar `actualizarAsignacion` en el DAO (regla: solo si cambia el técnico)**

Reemplazar el método `actualizarAsignacion`:
```java
    @Transactional
    public void actualizarAsignacion(String idRep, int idTec, String comentarioAsignacion,
                                     LocalDateTime updatedAt, Integer idTecAsigna) {
        Integer idTecActual = jdbc.queryForObject(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?", Integer.class, idRep);
        int filas = jdbc.update(
                "UPDATE Reparacion SET ID_TEC = ?, COMENTARIO_ASIGNACION = ? WHERE ID_REP = ? AND UPDATED_AT = ?",
                idTec, comentarioAsignacion, idRep,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        if (idTecActual != null && idTecActual != idTec) {
            jdbc.update("UPDATE Reparacion SET ID_TEC_ASIGNA = ? WHERE ID_REP = ?", idTecAsigna, idRep);
            borradorDao.eliminar(idRep);   // reasignada a otro técnico: el borrador del técnico anterior ya no aplica
        }
    }
```

- [ ] **Step 4: Actualizar las llamadas en `ReparacionController`**

En `ReparacionController.java`:

En `insertarAsignacion`, cambiar:
```java
        String idRep = dao.insertarAsignacion(req.imei(), req.idTec(), req.comentario());
```
por:
```java
        String idRep = dao.insertarAsignacion(req.imei(), req.idTec(), req.comentario(), principal.getIdTec());
```

En `marcarIncidenciaYAsignar`, cambiar:
```java
        dao.marcarIncidenciaYAsignar(idRep, req.comentario(), req.imei(), req.idTec());
```
por:
```java
        dao.marcarIncidenciaYAsignar(idRep, req.comentario(), req.imei(), req.idTec(), principal.getIdTec());
```

En `actualizarAsignacion`, cambiar:
```java
        dao.actualizarAsignacion(idRep, req.idTec(), req.comentarioAsignacion(), req.updatedAt());
```
por:
```java
        dao.actualizarAsignacion(idRep, req.idTec(), req.comentarioAsignacion(), req.updatedAt(), principal.getIdTec());
```

- [ ] **Step 5: Compilar el servidor**

Run: `cd gestion-reparaciones-servidor && mvn compile -q`
Expected: BUILD SUCCESS. Si algún otro llamador de estos métodos falla por la firma, actualizarlo pasando el `idTecAsigna` correspondiente.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java \
        gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
git commit -m "feat(asignador): registrar asignador al crear, marcar incidencia y reasignar (solo si cambia técnico)"
```

---

## Task 3: Escritura servidor — propagar el asignador a las R* al completar

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`

**Interfaces:**
- Consumes: columna `ID_TEC_ASIGNA` (Task 1)
- El asignador de las filas `R*` se hereda de la asignación origen (`idAsignacion`), no llega del controller.

- [ ] **Step 1: Propagar en `insertarCompleta`**

En `insertarCompleta`, justo después de `ensureTelefono(imei);` (antes del bucle `for (FilaReparacion fila : filas)`), añadir la lectura del asignador de la asignación origen:
```java
        Integer idTecAsigna = null;
        if (idAsignacion != null) {
            idTecAsigna = jdbc.queryForObject(
                    "SELECT ID_TEC_ASIGNA FROM Reparacion WHERE ID_REP = ?", Integer.class, idAsignacion);
        }
```
Y en el `INSERT` de las filas `R*` dentro del bucle, cambiar:
```java
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN)" +
                        " VALUES (?,?,?,?,NOW(),NOW())",
                        idRep, imei, idTec, idRepAnterior);
```
por:
```java
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?)",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna);
```

- [ ] **Step 2: Propagar en `guardarFilaIndividual`**

En `guardarFilaIndividual`, después de `ensureTelefono(imei);` (antes del bucle), añadir:
```java
        Integer idTecAsigna = jdbc.queryForObject(
                "SELECT ID_TEC_ASIGNA FROM Reparacion WHERE ID_REP = ?", Integer.class, idAsignacion);
```
(En este método `idAsignacion` nunca es null: se valida al inicio con el `SELECT COUNT(*) ... FOR UPDATE`.)

Y en el `INSERT` de la fila `R*`, cambiar:
```java
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN)" +
                        " VALUES (?,?,?,?,NOW(),NOW())",
                        idRep, imei, idTec, idRepAnterior);
```
por:
```java
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?)",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna);
```

- [ ] **Step 3: Compilar el servidor**

Run: `cd gestion-reparaciones-servidor && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
git commit -m "feat(asignador): el historial (R*) hereda el asignador de la asignación origen al completar"
```

---

## Task 4: Cliente — modelo `ReparacionResumen` (con test)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ReparacionResumen.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/models/ReparacionResumenTest.java`

**Interfaces:**
- Produces: `ReparacionResumen.getNombreTecnicoAsigna()` / `setNombreTecnicoAsigna(String)` — usado en Tasks 5, 6. Gson lo rellena automáticamente desde el JSON del servidor (campo `nombreTecnicoAsigna`).

- [ ] **Step 1: Escribir el test que falla**

En `ReparacionResumenTest.java`, añadir:
```java
    @Test
    void nombreTecnicoAsigna_getterYSetter() {
        ReparacionResumen r = new ReparacionResumen();
        assertNull(r.getNombreTecnicoAsigna());
        r.setNombreTecnicoAsigna("Diego");
        assertEquals("Diego", r.getNombreTecnicoAsigna());
    }
```
(Si el import de `assertNull`/`assertEquals` no está, usar `import static org.junit.jupiter.api.Assertions.*;` — verificar al principio del archivo.)

- [ ] **Step 2: Ejecutar el test para verque falla**

Run: `cd gestion-reparaciones-cliente && mvn test -Dtest=ReparacionResumenTest -q`
Expected: FALLO de compilación — `getNombreTecnicoAsigna`/`setNombreTecnicoAsigna` no existen.

- [ ] **Step 3: Añadir campo + getter + setter al modelo**

En `ReparacionResumen.java` (cliente), añadir el campo junto a los demás `private String` (p.ej. tras `private String observacionTelefono;`):
```java
    private String        nombreTecnicoAsigna;
```
Y getter/setter junto a los demás (tras `setNombreTecnico`):
```java
    public String getNombreTecnicoAsigna()              { return nombreTecnicoAsigna; }
    public void   setNombreTecnicoAsigna(String v)      { this.nombreTecnicoAsigna = v; }
```

- [ ] **Step 4: Ejecutar el test para verque pasa**

Run: `cd gestion-reparaciones-cliente && mvn test -Dtest=ReparacionResumenTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/ReparacionResumen.java \
        gestion-reparaciones-cliente/src/test/java/com/reparaciones/models/ReparacionResumenTest.java
git commit -m "feat(asignador): campo nombreTecnicoAsigna en modelo cliente + test"
```

---

## Task 5: Cliente — columna "Asignado por" en pendientes

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/PendientesTecnicoView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/PendientesSuperTecnicoView.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: `ReparacionResumen.getNombreTecnicoAsigna()` (Task 4)

- [ ] **Step 1: Añadir la columna al FXML de pendientes técnico**

En `PendientesTecnicoView.fxml`, tras la línea de `cComentario`, añadir:
```xml
            <TableColumn fx:id="cAsignadoPor" text="Asignado por"   prefWidth="120"/>
```

- [ ] **Step 2: Declarar y enlazar la columna en `PendientesTecnicoController`**

Declarar el campo junto a las otras columnas (tras `cComentario`):
```java
    @FXML private TableColumn<ReparacionResumen, String> cAsignadoPor;
```
Y en `initialize()`, tras el `cComentario.setCellValueFactory(...)`, añadir:
```java
        cAsignadoPor.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getNombreTecnicoAsigna() != null ? d.getValue().getNombreTecnicoAsigna() : "—"));
```

- [ ] **Step 3: Añadir la columna al FXML de pendientes supertécnico**

En `PendientesSuperTecnicoView.fxml`, localizar la columna de comentario (`fx:id` que muestra "Comentario") y añadir justo después:
```xml
            <TableColumn fx:id="cAsignadoPor" text="Asignado por"   prefWidth="120"/>
```

- [ ] **Step 4: Declarar y enlazar la columna en `PendientesSuperTecnicoController`**

Declarar el campo junto a las otras columnas `@FXML private TableColumn<ReparacionResumen, String>`:
```java
    @FXML private TableColumn<ReparacionResumen, String> cAsignadoPor;
```
Y en `initialize()`, junto a los otros `setCellValueFactory`, añadir:
```java
        cAsignadoPor.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getNombreTecnicoAsigna() != null ? d.getValue().getNombreTecnicoAsigna() : "—"));
```

- [ ] **Step 5: Compilar el cliente**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS. Si la carga del FXML fallara en runtime por un `fx:id` sin campo, revisar que el nombre coincide exactamente.

- [ ] **Step 6: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/PendientesTecnicoView.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesTecnicoController.java \
        gestion-reparaciones-cliente/src/main/resources/views/PendientesSuperTecnicoView.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java
git commit -m "feat(asignador): columna 'Asignado por' en pendientes (técnico y supertécnico)"
```

---

## Task 6: Cliente — columna "Asignado por" en historial (solo plano/detalle)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewTecnico.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewSuperTecnico.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`
- Modify: `gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewAdmin.fxml`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java`

**Interfaces:**
- Consumes: `ReparacionResumen.getNombreTecnicoAsigna()` (Task 4)
- La columna sigue exactamente la misma visibilidad que `colReparador` (visible en DETALLE/PLANO, oculta en MAESTRO).

- [ ] **Step 1: Añadir la columna a los 3 FXML del historial**

En cada uno de `ReparacionViewTecnico.fxml`, `ReparacionViewSuperTecnico.fxml` y `ReparacionViewAdmin.fxml`, localizar la línea de `colReparador` y añadir justo después:
```xml
                                    <TableColumn fx:id="colAsignadoPor" minWidth="100" text="Asignado por"/>
```
(En `ReparacionViewAdmin.fxml` la indentación es menor; usar la misma que las columnas vecinas de ese archivo.)

- [ ] **Step 2: Declarar la columna en los 3 controllers**

En cada uno de `ReparacionControllerTecnico.java`, `ReparacionControllerSuperTecnico.java`, `ReparacionControllerAdmin.java`, junto a la declaración de `colReparador`:
```java
    @FXML private TableColumn<Object, String> colAsignadoPor;
```

- [ ] **Step 3: Enlazar el valor en los 3 controllers**

En cada controller, justo después del bloque `colReparador.setCellValueFactory(...)`, añadir:
```java
        colAsignadoPor.setCellValueFactory(d -> {
            Object o = d.getValue();
            if (o instanceof ReparacionResumen rep)
                return new javafx.beans.property.SimpleStringProperty(
                    rep.getNombreTecnicoAsigna() != null ? rep.getNombreTecnicoAsigna() : "—");
            return new javafx.beans.property.SimpleStringProperty("");
        });
```

- [ ] **Step 4: Replicar la visibilidad de `colReparador` en los 3 controllers**

En cada controller, en TODAS las líneas donde aparece `colReparador.setVisible(x);`, añadir a continuación la línea hermana con el mismo valor. Por ejemplo, donde haya:
```java
        colIdRep.setVisible(false); colReparador.setVisible(false);
```
queda:
```java
        colIdRep.setVisible(false); colReparador.setVisible(false); colAsignadoPor.setVisible(false);
```
Y donde haya:
```java
        colIdRep.setVisible(true); colReparador.setVisible(true);
```
queda:
```java
        colIdRep.setVisible(true); colReparador.setVisible(true); colAsignadoPor.setVisible(true);
```
(En `ReparacionControllerTecnico` esto aparece en los bloques de modo MAESTRO, DETALLE y PLANO; replicar en cada una. Hacer lo mismo en SuperTecnico y Admin, que siguen el mismo patrón de visibilidad por modo.)

- [ ] **Step 5: Compilar el cliente**

Run: `cd gestion-reparaciones-cliente && mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Test manual**

Arrancar servidor + cliente. En el historial:
1. Modo MAESTRO (agrupado por IMEI): la columna "Asignado por" NO se ve.
2. Entrar en un IMEI (DETALLE) o cambiar a PLANO: la columna SÍ se ve, con el nombre del asignador o `—`.

- [ ] **Step 7: Commit**

```bash
git add gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewTecnico.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerTecnico.java \
        gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewSuperTecnico.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java \
        gestion-reparaciones-cliente/src/main/resources/views/ReparacionViewAdmin.fxml \
        gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerAdmin.java
git commit -m "feat(asignador): columna 'Asignado por' en historial (solo modo plano/detalle)"
```

---

## Verificación E2E final (tras aplicar la migración en preproducción)

1. **Crear asignación** → en pendientes del técnico, "Asignado por" muestra tu nombre.
2. **Reasignar a otro técnico** (cambiar técnico en editar asignación) → "Asignado por" pasa al reasignador.
3. **Editar solo el comentario** (sin cambiar técnico) → "Asignado por" NO cambia.
4. **Completar la reparación** → en el historial (plano), "Asignado por" conserva el nombre.
5. **Marcar incidencia y reasignar** → la nueva asignación muestra al supertécnico que la marcó.
6. **Asignación/reparación antigua** (anterior a la migración) → muestra `—`.
7. **Historial agrupado por IMEI** → la columna no aparece.

---

## Self-Review

### Spec coverage

| Requisito del spec | Task |
|---|---|
| `ALTER TABLE Reparacion ADD COLUMN ID_TEC_ASIGNA` + FK | Task 1 |
| Lectura: `LEFT JOIN Tecnico`, alias `NOMBRE_TEC_ASIGNA` en asignaciones e historial | Task 1 |
| Modelo servidor `nombreTecnicoAsigna` + mapper | Task 1 |
| Escritura al crear asignación (`insertarAsignacion`) | Task 2 |
| Escritura al marcar incidencia (`marcarIncidenciaYAsignar`) | Task 2 |
| Reasignar solo si cambia técnico (`actualizarAsignacion`) | Task 2 |
| Controller pasa `principal.getIdTec()` | Task 2 |
| Copiar a R* al completar (`insertarCompleta`, `guardarFilaIndividual`) | Task 3 |
| Modelo cliente `nombreTecnicoAsigna` | Task 4 |
| Columna "Asignado por" en pendientes (técnico + supertécnico) | Task 5 |
| Columna "Asignado por" en historial, solo plano/detalle | Task 6 |
| Antiguas → `—` | Tasks 5, 6 (fallback en cellValueFactory) |

### Notas
- TDD real solo en Task 4 (modelo); el resto se valida por compilación + pruebas manuales, siguiendo el patrón del proyecto (sin tests de integración de DAO/JavaFX).
- La migración SQL se aplica manualmente en preproducción antes de desplegar (no hay producción).
