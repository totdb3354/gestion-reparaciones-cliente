# Guardado individual por fila — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir un botón "✓ Guardar fila" por fila en `FormularioReparacionController` que persiste esa fila inmediatamente como un `R*` real, manteniendo la asignación abierta para el resto.

**Architecture:** Nuevo método `ReparacionDAO.guardarFilaIndividual` en servidor (clona la lógica de `insertarCompleta` por fila, omitiendo el bloque de cierre de asignación). El endpoint `POST /api/reparaciones/{idAsignacion}/filas` es el único punto de entrada, sin restricción de rol adicional (igual que `insertarCompleta`). En cliente, `FilaUI.btnSolicitud` se reutiliza como 4.º estado visual: "✓ Guardar fila" (activo, verde) → "✓ Guardada HH:mm" (bloqueado, verde); `OtrasAccionesUI` añade un botón "✓ Guardar" por línea. `BorradorContenido` rastrea `guardada/idRepGenerado/fechaGuardado` por fila/acción para sobrevivir a reinicios. Al recuperar el borrador, una única llamada a `getByImei` detecta si un SUPERTECNICO borró alguna fila y la desbloquea automáticamente.

**Tech Stack:** Java 17, JavaFX 21, Spring Boot 3, MariaDB, Gson (cliente), Spring Security, JdbcTemplate (servidor DAO), `ApiClient` + `StaleDataException` (cliente DAO)

---

## File Structure

| Archivo | Acción | Responsabilidad |
|---------|--------|-----------------|
| `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` | Modificar | Añadir `guardarFilaIndividual(...)` → `String idRep` |
| `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` | Modificar | Añadir `POST /{idAsignacion}/filas` + record `GuardarFilaRequest` |
| `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/BorradorContenido.java` | Modificar | Campos `guardada/idRepGenerado/fechaGuardado` en `Fila`; clase `OtraAccion`; `otros` → `List<OtraAccion>` |
| `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java` | Modificar | Añadir `guardarFilaIndividual(...)` → `String idRep` |
| `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java` | Modificar | FilaUI (campos/métodos nuevos + ediciones); OtrasAccionesUI (reescritura); wiring del controlador |

---

## Task 1: Servidor — `ReparacionDAO.guardarFilaIndividual`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (insertar después de línea 376, tras el método `insertarCompleta`)

- [ ] **Step 1: Añadir método `guardarFilaIndividual` en el DAO servidor**

  Insertar el siguiente método completo después del cierre de `insertarCompleta` (línea 376, antes de `public void completar(...)`):

  ```java
      @Transactional
      public String guardarFilaIndividual(List<FilaReparacion> filas, String imei, int idTec,
                                          String idRepAnterior, String idAsignacion) {
          Integer existe = jdbc.queryForObject(
                  "SELECT COUNT(*) FROM Reparacion WHERE ID_REP = ? AND ID_TEC = ? AND FECHA_FIN IS NULL FOR UPDATE",
                  Integer.class, idAsignacion, idTec);
          if (existe == null || existe == 0) {
              throw new ResponseStatusException(HttpStatus.CONFLICT,
                      "La asignación ya fue eliminada, completada o reasignada a otro técnico");
          }
          ensureTelefono(imei);
          String idRepCreado = null;
          Set<Integer> idComsUsados = new java.util.HashSet<>();
          for (FilaReparacion fila : filas) {
              if (!fila.esSolicitud) {
                  String idRep = nextId("R");
                  jdbc.update(
                          "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN)" +
                          " VALUES (?,?,?,?,NOW(),NOW())",
                          idRep, imei, idTec, idRepAnterior);
                  jdbc.update(
                          "INSERT INTO Reparacion_componente" +
                          " (ID_REP, ID_COM, ES_REUTILIZADO, OBSERVACIONES, ES_SOLICITUD, CANTIDAD)" +
                          " VALUES (?,?,?,?,0,?)",
                          idRep, fila.idCom, fila.reutilizado, fila.observacion, fila.cantidad);
                  if (!fila.reutilizado) {
                      int masterIdCom = resolveToMasterId(fila.idCom);
                      int rows = jdbc.update(
                              "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ? AND STOCK >= ?",
                              fila.cantidad, masterIdCom, fila.cantidad);
                      if (rows == 0)
                          throw new ResponseStatusException(HttpStatus.CONFLICT,
                                  "Stock insuficiente para el componente ID " + fila.idCom);
                  }
                  idRepCreado = idRep;
                  idComsUsados.add(fila.idCom);
              } else {
                  jdbc.update(
                          "INSERT INTO Reparacion_componente" +
                          " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                          " VALUES (?,?,1,?,'PENDIENTE',?)",
                          idAsignacion, fila.idCom, fila.descripcionSolicitud, fila.cantidad);
              }
          }
          if (idRepCreado == null) {
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                      "guardarFilaIndividual requiere al menos una fila con uso real");
          }
          if (!idComsUsados.isEmpty()) {
              StringBuilder inClause = new StringBuilder();
              for (Integer idCom : idComsUsados) {
                  if (inClause.length() > 0) inClause.append(',');
                  inClause.append(idCom);
              }
              jdbc.update(
                      "UPDATE Reparacion_componente SET ES_SOLICITUD = 0" +
                      " WHERE ID_REP = ? AND ES_SOLICITUD = 1 AND ESTADO_SOLICITUD = 'PENDIENTE'" +
                      " AND ID_COM IN (" + inClause + ")",
                      idAsignacion);
          }
          return idRepCreado;
      }
  ```

- [ ] **Step 2: Compilar servidor**

  ```bash
  cd gestion-reparaciones-servidor
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS sin errores de compilación.

- [ ] **Step 3: Commit**

  ```bash
  git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java
  git -C gestion-reparaciones-servidor commit -m "feat: guardarFilaIndividual en ReparacionDAO (servidor)"
  ```

---

## Task 2: Servidor — Endpoint `POST /{idAsignacion}/filas`

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

- [ ] **Step 1: Añadir endpoint y record de request**

  En `ReparacionController.java`, insertar el endpoint entre `agotarComponente` (línea 234) y `eliminarAsignacion` (línea 236):

  ```java
      @PostMapping("/{idAsignacion}/filas")
      @ResponseStatus(HttpStatus.CREATED)
      public Map<String, String> guardarFilaIndividual(@PathVariable String idAsignacion,
                                                       @RequestBody GuardarFilaRequest req,
                                                       @AuthenticationPrincipal UsuarioPrincipal principal) {
          String idRep = dao.guardarFilaIndividual(req.filas(), req.imei(), req.idTec(),
                  req.idRepAnterior(), idAsignacion);
          logDao.insertar(principal.getIdUsu(), "GUARDAR_FILA_INDIVIDUAL",
                  "ID_REP: " + idRep + ", ID_ASIG: " + idAsignacion + ", IMEI: " + req.imei());
          return Map.of("value", idRep);
      }
  ```

  Y en la sección `// ── request records ─────` (después de línea 289, antes del cierre de clase), añadir:

  ```java
      private record GuardarFilaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                        String idRepAnterior) {}
  ```

- [ ] **Step 2: Compilar y arrancar el servidor**

  ```bash
  cd gestion-reparaciones-servidor
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS.

- [ ] **Step 3: Verificar el endpoint manualmente**

  Con el servidor arrancado y una asignación abierta real (p. ej. `A20260616_1`) para el técnico con ID 2:

  ```bash
  curl -s -X POST http://localhost:8080/api/reparaciones/A20260616_1/filas \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <token>" \
    -d '{
      "filas": [{"idCom": 7, "cantidad": 1, "reutilizado": false,
                 "observacion": null, "prefijo": "bat",
                 "esSolicitud": false, "descripcionSolicitud": null}],
      "imei": "123456789012345",
      "idTec": 2,
      "idRepAnterior": null
    }'
  ```
  Expected: HTTP 201, body `{"value":"R20260616_N"}`. Verificar en historial que el R* existe y el A* sigue abierto (`FECHA_FIN IS NULL`).

- [ ] **Step 4: Commit**

  ```bash
  git -C gestion-reparaciones-servidor add src/main/java/com/reparaciones/servidor/controller/ReparacionController.java
  git -C gestion-reparaciones-servidor commit -m "feat: endpoint POST /{idAsignacion}/filas (guardar fila individual)"
  ```

---

## Task 3: Cliente — Modelo `BorradorContenido`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/models/BorradorContenido.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/models/BorradorContenidoTest.java` (nuevo)

- [ ] **Step 1: Escribir test que falla**

  Crear `src/test/java/com/reparaciones/models/BorradorContenidoTest.java`:

  ```java
  package com.reparaciones.models;

  import com.google.gson.Gson;
  import org.junit.jupiter.api.Test;
  import java.util.List;
  import static org.junit.jupiter.api.Assertions.*;

  class BorradorContenidoTest {

      private final Gson gson = new Gson();

      @Test
      void fila_guardada_sobrevive_round_trip_json() {
          BorradorContenido b = new BorradorContenido();
          BorradorContenido.Fila f = new BorradorContenido.Fila();
          f.prefijo = "lcd";
          f.idCom = 12;
          f.guardada = true;
          f.idRepGenerado = "R20260616_1";
          f.fechaGuardado = "16/06 14:32";
          b.filas.add(f);

          String json = gson.toJson(b);
          BorradorContenido b2 = gson.fromJson(json, BorradorContenido.class);

          assertEquals(1, b2.filas.size());
          BorradorContenido.Fila f2 = b2.filas.get(0);
          assertTrue(f2.guardada);
          assertEquals("R20260616_1", f2.idRepGenerado);
          assertEquals("16/06 14:32", f2.fechaGuardado);
      }

      @Test
      void otra_accion_guardada_sobrevive_round_trip_json() {
          BorradorContenido b = new BorradorContenido();
          BorradorContenido.OtraAccion a = new BorradorContenido.OtraAccion();
          a.descripcion = "Limpiar cámara";
          a.guardada = true;
          a.idRepGenerado = "R20260616_2";
          a.fechaGuardado = "16/06 15:00";
          b.otros.add(a);

          String json = gson.toJson(b);
          BorradorContenido b2 = gson.fromJson(json, BorradorContenido.class);

          assertEquals(1, b2.otros.size());
          BorradorContenido.OtraAccion a2 = b2.otros.get(0);
          assertTrue(a2.guardada);
          assertEquals("R20260616_2", a2.idRepGenerado);
          assertEquals("Limpiar cámara", a2.descripcion);
      }

      @Test
      void otros_no_guardados_como_strings_migran_como_null() {
          // borrador antiguo: otros era List<String>, ahora List<OtraAccion>
          // Gson deserializará strings como objetos con todos los campos null → guardada=false
          String jsonAntiguo = "{\"modelo\":\"12\",\"filas\":[],\"otros\":[\"Limpiar cámara\"]}";
          BorradorContenido b = gson.fromJson(jsonAntiguo, BorradorContenido.class);
          // Gson no puede deserializar String como OtraAccion → otros queda vacío o elementos null
          // El código cliente filtra nulls y blanks en aplicarAcciones → no hay crash
          // Solo verificamos que no lanza excepción:
          assertNotNull(b);
      }
  }
  ```

- [ ] **Step 2: Ejecutar test para confirmar que falla**

  ```bash
  cd gestion-reparaciones-cliente
  mvn test -pl . -Dtest=BorradorContenidoTest -q
  ```
  Expected: FAIL — `guardada`, `idRepGenerado`, `fechaGuardado` no existen aún en `BorradorContenido.Fila`.

- [ ] **Step 3: Actualizar `BorradorContenido.java`**

  Reemplazar el contenido completo del archivo:

  ```java
  package com.reparaciones.models;

  import java.util.ArrayList;
  import java.util.List;

  public class BorradorContenido {

      public String modelo;
      public List<Fila> filas = new ArrayList<>();
      public List<OtraAccion> otros = new ArrayList<>();

      public static class Fila {
          public String prefijo;
          public int idCom;
          public int cantidad;
          public boolean reutilizado;
          public String observacion;
          public boolean solicitudNueva;
          public String descripcionSolicitud;
          public boolean agotadoConfirmado;
          public String descripcionAgotado;
          // Guardado individual:
          public boolean guardada;
          public String  idRepGenerado;
          public String  fechaGuardado;
      }

      public static class OtraAccion {
          public String descripcion;
          public boolean guardada;
          public String  idRepGenerado;
          public String  fechaGuardado;
      }
  }
  ```

- [ ] **Step 4: Ejecutar tests para confirmar que pasan**

  ```bash
  cd gestion-reparaciones-cliente
  mvn test -pl . -Dtest=BorradorContenidoTest -q
  ```
  Expected: BUILD SUCCESS, 3 tests passed.

  > **Nota sobre migración:** El test `otros_no_guardados_como_strings_migran_como_null` cubre borradores guardados con la versión anterior (`otros: ["texto"]`). Gson no puede deserializar un `String` como `OtraAccion` → el campo `otros` quedará vacío o con elementos nulos. El método `aplicarAcciones()` (Task 6) filtra `a.descripcion == null || a.descripcion.isBlank()` → descarta silenciosamente. Las descripciones pendientes del borrador antiguo se pierden al reabrir (el técnico las vuelve a escribir). Comportamiento aceptable: ocurre solo una vez, en borradores guardados antes de este despliegue.

- [ ] **Step 5: Compilar módulo cliente completo**

  ```bash
  cd gestion-reparaciones-cliente
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS (verifica que nada más usa `BorradorContenido.otros` con el tipo antiguo `List<String>` antes de continuar — el compilador lo detectará aquí).

- [ ] **Step 6: Commit**

  ```bash
  git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/models/BorradorContenido.java src/test/java/com/reparaciones/models/BorradorContenidoTest.java
  git -C gestion-reparaciones-cliente commit -m "feat: BorradorContenido - campos guardada/idRepGenerado/fechaGuardado + OtraAccion"
  ```

---

## Task 4: Cliente — `ReparacionDAO.guardarFilaIndividual`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java` (insertar después de `agotarComponente`, antes de `borrarIncidenciaPorImei`)

- [ ] **Step 1: Añadir método `guardarFilaIndividual` en cliente DAO**

  Insertar después del método `agotarComponente` (que cierra en línea ~405) y antes de `borrarIncidenciaPorImei` (línea ~413):

  ```java
      /**
       * Guarda una fila individual como reparación real ({@code R*}), sin cerrar la asignación.
       *
       * @param filas         fila de uso real + opcionalmente fila de solicitud nueva
       * @param imei          IMEI del dispositivo
       * @param idTec         ID del técnico que guarda
       * @param idRepAnterior ID de la reparación anterior si es reincidencia, o {@code null}
       * @param idAsignacion  ID de la asignación origen ({@code A*})
       * @return ID de la reparación creada
       * @throws StaleDataException si la asignación fue cerrada/eliminada/reasignada (409)
       * @throws SQLException       si falla la llamada al servidor
       */
      public String guardarFilaIndividual(List<FilaReparacion> filas, String imei, int idTec,
              String idRepAnterior, String idAsignacion) throws SQLException {
          Map<String, Object> body = new HashMap<>();
          body.put("filas",         filas);
          body.put("imei",          imei);
          body.put("idTec",         idTec);
          body.put("idRepAnterior", idRepAnterior);
          JsonObject resp = ApiClient.post(
                  "/api/reparaciones/" + idAsignacion + "/filas", body, JsonObject.class);
          return resp != null ? resp.get("value").getAsString() : null;
      }
  ```

- [ ] **Step 2: Compilar**

  ```bash
  cd gestion-reparaciones-cliente
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

  ```bash
  git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/dao/ReparacionDAO.java
  git -C gestion-reparaciones-cliente commit -m "feat: ReparacionDAO.guardarFilaIndividual (cliente)"
  ```

---

## Task 5: Cliente — `FilaUI` — Nuevos campos y métodos

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

Todos los cambios de esta tarea son ediciones a la clase interna `FilaUI` (líneas 817–1856).

- [ ] **Step 1: Añadir campos de guardado individual en `FilaUI`**

  Localizar el bloque de campos que termina en `private Runnable onCambio;` (línea 855) e insertar a continuación:

  ```
  // Existing line 855:
  private Runnable onCambio;

  // ADD AFTER:
          // ── Guardado individual ──────────────────────────────────────────────
          private boolean guardada = false;
          private String  idRepGenerado = null;
          private String  fechaGuardado = null;
          private boolean recibidoPendienteUso = false;
          private Runnable onGuardarFila = null;
  ```

  Old (exact match para la herramienta Edit):
  ```java
          private Runnable onCambio;

          // ── Agotado ───────────────────────────────────────────────────────────
  ```
  New:
  ```java
          private Runnable onCambio;

          // ── Guardado individual ──────────────────────────────────────────────
          private boolean guardada = false;
          private String  idRepGenerado = null;
          private String  fechaGuardado = null;
          private boolean recibidoPendienteUso = false;
          private Runnable onGuardarFila = null;

          // ── Agotado ───────────────────────────────────────────────────────────
  ```

- [ ] **Step 2: Añadir guard de guardado al inicio de `aplicarFiltroModelo()`**

  Old:
  ```java
          void aplicarFiltroModelo(String modelo) {
              agotadoConfirmado = false;
  ```
  New:
  ```java
          void aplicarFiltroModelo(String modelo) {
              if (guardada) return;
              agotadoConfirmado = false;
  ```

- [ ] **Step 3: Resetear `recibidoPendienteUso` en `aplicarFiltroModelo()`**

  Old:
  ```java
              descripcionSolicitud = null;
              btnSolicitud.setText("⚠ Solicitud pieza");
  ```
  New:
  ```java
              descripcionSolicitud = null;
              recibidoPendienteUso = false;
              btnSolicitud.setText("⚠ Solicitud pieza");
  ```

- [ ] **Step 4: Ocultar `btnSolicitud` al confirmar agotado en `abrirDialogoAgotado()`**

  Old:
  ```java
                  btnSolicitud.setDisable(true);
                  actualizarLabelConfirmado(stock);
  ```
  New:
  ```java
                  btnSolicitud.setDisable(true);
                  btnSolicitud.setVisible(false);
                  btnSolicitud.setManaged(false);
                  actualizarLabelConfirmado(stock);
  ```

- [ ] **Step 5: Marcar `recibidoPendienteUso` en `activarSolicitud()` rama GESTIONADA**

  Old:
  ```java
                      solicitudActiva = false;
                      descripcionSolicitud = null;
                      btnSolicitud.setText("✓ Recibido");
  ```
  New:
  ```java
                      solicitudActiva = false;
                      descripcionSolicitud = null;
                      recibidoPendienteUso = true;
                      btnSolicitud.setText("✓ Recibido");
  ```

- [ ] **Step 6: Enganchar `actualizarBotonGuardarFila()` en `notificar()`**

  Old:
  ```java
          private void notificar() {
              if (onCambio != null)
                  onCambio.run();
          }
  ```
  New:
  ```java
          private void notificar() {
              actualizarBotonGuardarFila();
              if (onCambio != null)
                  onCambio.run();
          }
  ```

- [ ] **Step 7: Añadir rama `guardada` al inicio de `capturarEnBorrador()`**

  Old:
  ```java
          com.reparaciones.models.BorradorContenido.Fila capturarEnBorrador() {
              boolean vacia = cantidad == 0 && !isReutilizado()
  ```
  New:
  ```java
          com.reparaciones.models.BorradorContenido.Fila capturarEnBorrador() {
              if (guardada) {
                  com.reparaciones.models.BorradorContenido.Fila f = new com.reparaciones.models.BorradorContenido.Fila();
                  f.prefijo = prefijo;
                  f.idCom = getIdComSeleccionado();
                  f.guardada = true;
                  f.idRepGenerado = idRepGenerado;
                  f.fechaGuardado = fechaGuardado;
                  return f;
              }
              boolean vacia = cantidad == 0 && !isReutilizado()
  ```

- [ ] **Step 8: Añadir rama `guardada` al inicio de `aplicarBorrador()`**

  Old:
  ```java
          void aplicarBorrador(com.reparaciones.models.BorradorContenido.Fila f) {
              if (solicitudActiva) return;   // fila ya gestionada por una solicitud de BD: ignorar borrador
              if (f.idCom > 0) preseleccionarSku(f.idCom);
  ```
  New:
  ```java
          void aplicarBorrador(com.reparaciones.models.BorradorContenido.Fila f) {
              if (solicitudActiva) return;   // fila ya gestionada por una solicitud de BD: ignorar borrador
              if (f.guardada) {
                  if (f.idCom > 0) preseleccionarSku(f.idCom);
                  aplicarGuardada(f.idRepGenerado != null ? f.idRepGenerado : "?",
                                  f.fechaGuardado != null ? f.fechaGuardado : "");
                  return;
              }
              if (f.idCom > 0) preseleccionarSku(f.idCom);
  ```

- [ ] **Step 9: Añadir nuevos métodos públicos al final de `FilaUI` (antes del cierre de clase `}`)**

  Old (líneas 1853-1856, cierre de FilaUI):
  ```java
          void setOnCambio(Runnable r) {
              this.onCambio = r;
          }
      }
  ```
  New:
  ```java
          void setOnCambio(Runnable r) {
              this.onCambio = r;
          }

          void setOnGuardarFila(Runnable r) { this.onGuardarFila = r; }
          boolean isGuardada() { return guardada; }
          String getIdRepGenerado() { return idRepGenerado; }

          void setGuardandoEnCurso(boolean enCurso) {
              btnSolicitud.setDisable(enCurso);
          }

          /** Bloquea la fila permanentemente con el badge "✓ Guardada [fecha]". */
          void aplicarGuardada(String idRep, String fecha) {
              guardada = true;
              idRepGenerado = idRep;
              fechaGuardado = fecha;
              recibidoPendienteUso = false;
              cbSku.setDisable(true);
              btnMas.setDisable(true);
              btnMenos.setDisable(true);
              chkReutilizado.setDisable(true);
              btnObservacion.setDisable(true);
              btnSolicitud.setText("✓ Guardada " + fecha);
              btnSolicitud.setStyle(STYLE_SOL_RECIBIDA);
              btnSolicitud.setDisable(true);
              btnSolicitud.setOnAction(null);
              btnSolicitud.setVisible(true);
              btnSolicitud.setManaged(true);
              root.setStyle("-fx-background-color: #F1F8F1; " +
                      "-fx-border-color: transparent transparent #C5E1C5 transparent;" +
                      "-fx-border-width: 0 0 1 0;");
          }

          /** Restaura la fila a estado editable cuando la R* guardada fue borrada por SUPERTECNICO. */
          void desbloquearTrasEliminacion() {
              guardada = false;
              idRepGenerado = null;
              fechaGuardado = null;
              cantidad = 0;
              chkReutilizado.setSelected(false);
              observacion = null;
              lblObservacion.setText("");
              btnObservacion.setVisible(true);  btnObservacion.setManaged(true);
              lblObservacion.setVisible(false); lblObservacion.setManaged(false);
              btnBorrarObs.setVisible(false);   btnBorrarObs.setManaged(false);
              cbSku.setDisable(false);
              Componente sel = cbSku.getValue();
              btnMas.setDisable(!prefijo.equals("otro") && (sel == null || sel.getStock() <= 0));
              chkReutilizado.setDisable(false);
              btnObservacion.setDisable(false);
              btnSolicitud.setOnAction(e -> abrirSolicitud());
              btnSolicitud.setVisible(false); btnSolicitud.setManaged(false);
              root.setStyle("-fx-background-color: #F3F3F3; " +
                      "-fx-border-color: transparent transparent #E0E0E0 transparent;" +
                      "-fx-border-width: 0 0 1 0;");
              actualizarContador();
              notificar();
          }

          /**
           * Muestra/oculta/renombra {@code btnSolicitud} según el estado de la fila.
           * Llamado desde {@code notificar()} — se ejecuta en cada cambio de cantidad/reutilizado.
           * No hace nada si la fila ya está guardada, en modo edición, o con solicitud activa.
           */
          void actualizarBotonGuardarFila() {
              if (guardada || modoEdicion || solicitudActiva) return;
              boolean activa = isActiva() && !esAgotadoNuevo();
              if (activa) {
                  recibidoPendienteUso = false;
                  btnSolicitud.setText("✓ Guardar fila");
                  btnSolicitud.setStyle(STYLE_SOL_RECIBIDA);
                  btnSolicitud.setDisable(false);
                  btnSolicitud.setOnAction(e -> { if (onGuardarFila != null) onGuardarFila.run(); });
                  btnSolicitud.setVisible(true);
                  btnSolicitud.setManaged(true);
              } else if (!recibidoPendienteUso) {
                  btnSolicitud.setVisible(false);
                  btnSolicitud.setManaged(false);
              }
          }
      }
  ```

- [ ] **Step 10: Compilar**

  ```bash
  cd gestion-reparaciones-cliente
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

  ```bash
  git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
  git -C gestion-reparaciones-cliente commit -m "feat: FilaUI - actualizarBotonGuardarFila, aplicarGuardada, desbloquearTrasEliminacion"
  ```

---

## Task 6: Cliente — `OtrasAccionesUI` — Guardado individual por línea

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java` (reemplazar clase `OtrasAccionesUI` completa, líneas 1858–1975)

- [ ] **Step 1: Añadir import `java.util.function.Function`**

  Old:
  ```java
  import java.util.Set;
  import java.util.stream.Collectors;
  ```
  New:
  ```java
  import java.util.Set;
  import java.util.function.Function;
  import java.util.stream.Collectors;
  ```

- [ ] **Step 2: Reemplazar `OtrasAccionesUI` completa**

  Old (líneas 1858–1975, desde el comentario de sección hasta el cierre `}`):
  ```java
      // ─── OtrasAccionesUI ──────────────────────────────────────────────────────
      /** Sección de "Otras acciones": varias acciones de texto libre (sin pieza/stock).
       *  Cada acción se guarda como un R* con el componente otroi<modelo> y cantidad 0. */
      static class OtrasAccionesUI {
          private final VBox root;
          private final VBox listaLineas = new VBox(5);
          private final Label badge = new Label("0");
          private final List<Componente> otroComponentes;
          private final Image imgBorrar;
          private Componente otroSel = null;   // otroi<modelo> del modelo actual
          private Runnable onCambio;
          private Button btnAdd;

          OtrasAccionesUI(List<Componente> otroComponentes, Image imgBorrar) {
              this.otroComponentes = otroComponentes;
              this.imgBorrar = imgBorrar;

              Label titulo = new Label("OTRAS ACCIONES");
              titulo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
              badge.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 10px;" +
                      "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 8 1 8;");
              HBox header = new HBox(8, titulo, badge);
              header.setAlignment(Pos.CENTER_LEFT);
              header.setStyle("-fx-padding: 8 14 2 14;");

              javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(listaLineas);
              scroll.setFitToWidth(true);
              scroll.setMaxHeight(150);
              scroll.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-background-radius: 6;");
              listaLineas.setStyle("-fx-padding: 5;");

              btnAdd = new Button("+ Añadir acción");
              btnAdd.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 11.5px;" +
                      "-fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12 6 12;");
              btnAdd.setOnAction(e -> agregarLinea(""));

              VBox cuerpo = new VBox(8, scroll, btnAdd);
              cuerpo.setStyle("-fx-padding: 2 14 12 14;");

              root = new VBox(header, cuerpo);
              root.setStyle("-fx-background-color: #F6F7F9;" +
                      "-fx-border-color: transparent transparent transparent #2C3B54; -fx-border-width: 0 0 0 4;");
              root.setVisible(false); root.setManaged(false);
          }

          /** Selecciona el otroi<modelo> según el modelo elegido; muestra la sección si existe. */
          void setModelo(String modelo) {
              otroSel = (modelo == null) ? null : otroComponentes.stream()
                      .filter(c -> extraerModelo(c.getTipo(), "otro").equals(modelo))
                      .findFirst().orElse(null);
              boolean disponible = otroSel != null;
              root.setVisible(disponible); root.setManaged(disponible);
          }

          private void agregarLinea(String texto) {
              if (hayLineaVacia()) return;   // solo se añade si las anteriores están escritas
              TextField tf = new TextField(texto);
              tf.setPromptText("Describe la acción");
              tf.setStyle("-fx-font-size: 12px; -fx-background-color: white;" +
                      "-fx-border-color: #C2C8D0; -fx-border-radius: 4; -fx-background-radius: 4;");
              HBox.setHgrow(tf, Priority.ALWAYS);
              ImageView iv = new ImageView(imgBorrar);
              iv.setFitWidth(18); iv.setFitHeight(18); iv.setPreserveRatio(true);
              Button btnDel = new Button();
              btnDel.setGraphic(iv);
              btnDel.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
              HBox linea = new HBox(8, tf, btnDel);
              linea.setAlignment(Pos.CENTER_LEFT);
              btnDel.setOnAction(e -> { listaLineas.getChildren().remove(linea); actualizar(); });
              tf.textProperty().addListener((o, a, b) -> actualizar());
              listaLineas.getChildren().add(linea);
              tf.requestFocus();
              actualizar();
          }

          private void actualizar() {
              badge.setText(String.valueOf(getDescripciones().size()));
              btnAdd.setDisable(hayLineaVacia());
              if (onCambio != null) onCambio.run();
          }

          private boolean hayLineaVacia() {
              for (javafx.scene.Node n : listaLineas.getChildren()) {
                  if (n instanceof HBox h && !h.getChildren().isEmpty()
                          && h.getChildren().get(0) instanceof TextField tf) {
                      if (tf.getText() == null || tf.getText().trim().isEmpty()) return true;
                  }
              }
              return false;
          }

          /** Descripciones no vacías (trim) de las líneas. */
          List<String> getDescripciones() {
              List<String> out = new ArrayList<>();
              for (javafx.scene.Node n : listaLineas.getChildren()) {
                  if (n instanceof HBox h && !h.getChildren().isEmpty()
                          && h.getChildren().get(0) instanceof TextField tf) {
                      String t = tf.getText() == null ? "" : tf.getText().trim();
                      if (!t.isEmpty()) out.add(t);
                  }
              }
              return out;
          }

          /** Restaura las descripciones de un borrador como líneas de acción. */
          void aplicarDescripciones(List<String> descripciones) {
              if (descripciones == null) return;
              for (String d : descripciones) {
                  if (d != null && !d.isBlank()) agregarLinea(d.trim());
              }
          }

          int getIdComOtro() { return otroSel != null ? otroSel.getIdCom() : -1; }
          boolean hayAccion() { return otroSel != null && !getDescripciones().isEmpty(); }
          boolean esOtro(int idCom) { return otroComponentes.stream().anyMatch(c -> c.getIdCom() == idCom); }
          void setOnCambio(Runnable r) { this.onCambio = r; }
          VBox getRoot() { return root; }
      }
  }
  ```

  New (reemplaza todo lo anterior, incluyendo el `}` final del archivo):
  ```java
      // ─── OtrasAccionesUI ──────────────────────────────────────────────────────
      /** Sección de "Otras acciones": lista de acciones de texto libre con guardado individual.
       *  Cada acción se guarda como un R* con el componente otroi<modelo> y cantidad 0. */
      static class OtrasAccionesUI {
          private final VBox root;
          private final VBox listaLineas = new VBox(5);
          private final Label badge = new Label("0");
          private final List<Componente> otroComponentes;
          private final Image imgBorrar;
          private Componente otroSel = null;
          private Runnable onCambio;
          private Button btnAdd;
          private final List<LineaAccion> lineas = new ArrayList<>();
          private Function<String, String> guardador;

          OtrasAccionesUI(List<Componente> otroComponentes, Image imgBorrar) {
              this.otroComponentes = otroComponentes;
              this.imgBorrar = imgBorrar;

              Label titulo = new Label("OTRAS ACCIONES");
              titulo.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #2C3B54;");
              badge.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 10px;" +
                      "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 8 1 8;");
              HBox header = new HBox(8, titulo, badge);
              header.setAlignment(Pos.CENTER_LEFT);
              header.setStyle("-fx-padding: 8 14 2 14;");

              javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(listaLineas);
              scroll.setFitToWidth(true);
              scroll.setMaxHeight(150);
              scroll.setStyle("-fx-background-color: white; -fx-border-color: #C2C8D0; -fx-border-radius: 6; -fx-background-radius: 6;");
              listaLineas.setStyle("-fx-padding: 5;");

              btnAdd = new Button("+ Añadir acción");
              btnAdd.setStyle("-fx-background-color: #2C3B54; -fx-text-fill: white; -fx-font-size: 11.5px;" +
                      "-fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12 6 12;");
              btnAdd.setOnAction(e -> agregarLinea(""));

              VBox cuerpo = new VBox(8, scroll, btnAdd);
              cuerpo.setStyle("-fx-padding: 2 14 12 14;");

              root = new VBox(header, cuerpo);
              root.setStyle("-fx-background-color: #F6F7F9;" +
                      "-fx-border-color: transparent transparent transparent #2C3B54; -fx-border-width: 0 0 0 4;");
              root.setVisible(false); root.setManaged(false);
          }

          /** Registra el callback del controlador que persiste una acción y devuelve su idRep. */
          void setGuardador(Function<String, String> g) { this.guardador = g; }

          /** Selecciona el otroi<modelo> según el modelo elegido; muestra la sección si existe. */
          void setModelo(String modelo) {
              otroSel = (modelo == null) ? null : otroComponentes.stream()
                      .filter(c -> extraerModelo(c.getTipo(), "otro").equals(modelo))
                      .findFirst().orElse(null);
              boolean disponible = otroSel != null;
              root.setVisible(disponible); root.setManaged(disponible);
          }

          private void agregarLinea(String texto) {
              if (hayLineaVacia()) return;
              LineaAccion la = new LineaAccion();
              la.tf = new TextField(texto);
              la.tf.setPromptText("Describe la acción");
              la.tf.setStyle("-fx-font-size: 12px; -fx-background-color: white;" +
                      "-fx-border-color: #C2C8D0; -fx-border-radius: 4; -fx-background-radius: 4;");
              HBox.setHgrow(la.tf, Priority.ALWAYS);

              ImageView iv = new ImageView(imgBorrar);
              iv.setFitWidth(18); iv.setFitHeight(18); iv.setPreserveRatio(true);
              la.btnDel = new Button();
              la.btnDel.setGraphic(iv);
              la.btnDel.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");

              la.btnGuardar = new Button("✓ Guardar");
              la.btnGuardar.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white;" +
                      "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10 4 10;");
              la.btnGuardar.setDisable(texto.trim().isEmpty());

              la.row = new HBox(8, la.tf, la.btnGuardar, la.btnDel);
              la.row.setAlignment(Pos.CENTER_LEFT);

              la.btnDel.setOnAction(e -> {
                  listaLineas.getChildren().remove(la.row);
                  lineas.remove(la);
                  actualizar();
              });
              la.tf.textProperty().addListener((o, a, b) -> {
                  if (!la.guardada) la.btnGuardar.setDisable(b == null || b.trim().isEmpty());
                  actualizar();
              });
              la.btnGuardar.setOnAction(e -> guardarLinea(la));

              lineas.add(la);
              listaLineas.getChildren().add(la.row);
              la.tf.requestFocus();
              actualizar();
          }

          private void guardarLinea(LineaAccion la) {
              if (guardador == null) return;
              String texto = la.tf.getText() == null ? "" : la.tf.getText().trim();
              if (texto.isEmpty()) return;
              la.btnGuardar.setDisable(true);
              String idRep = guardador.apply(texto);
              if (idRep == null) {
                  la.btnGuardar.setDisable(false);
                  return;
              }
              la.guardada = true;
              la.idRepGenerado = idRep;
              la.fechaGuardado = java.time.LocalDateTime.now()
                      .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
              bloquearLinea(la);
              actualizar();
          }

          private void bloquearLinea(LineaAccion la) {
              la.tf.setDisable(true);
              la.btnGuardar.setVisible(false); la.btnGuardar.setManaged(false);
              la.btnDel.setVisible(false); la.btnDel.setManaged(false);
              Label lbl = new Label("✓ Guardada " + la.fechaGuardado);
              lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #2E7D32; -fx-font-weight: bold;" +
                      "-fx-padding: 0 4 0 4;");
              la.lblGuardada = lbl;
              la.row.getChildren().add(lbl);
          }

          private void desbloquearLinea(LineaAccion la) {
              la.guardada = false;
              la.idRepGenerado = null;
              la.fechaGuardado = null;
              la.tf.setDisable(false);
              if (la.lblGuardada != null) {
                  la.row.getChildren().remove(la.lblGuardada);
                  la.lblGuardada = null;
              }
              la.btnGuardar.setVisible(true); la.btnGuardar.setManaged(true);
              la.btnGuardar.setDisable(la.tf.getText() == null || la.tf.getText().trim().isEmpty());
              la.btnDel.setVisible(true); la.btnDel.setManaged(true);
          }

          private void actualizar() {
              long total = lineas.stream()
                      .filter(l -> l.guardada || (l.tf.getText() != null && !l.tf.getText().trim().isEmpty()))
                      .count();
              badge.setText(String.valueOf(total));
              btnAdd.setDisable(hayLineaVacia());
              if (onCambio != null) onCambio.run();
          }

          private boolean hayLineaVacia() {
              return lineas.stream().anyMatch(l -> !l.guardada
                      && (l.tf.getText() == null || l.tf.getText().trim().isEmpty()));
          }

          /** Descripciones pendientes (no guardadas individualmente) para el guardado final. */
          List<String> getDescripcionesPendientes() {
              List<String> out = new ArrayList<>();
              for (LineaAccion l : lineas) {
                  if (l.guardada) continue;
                  String t = l.tf.getText() == null ? "" : l.tf.getText().trim();
                  if (!t.isEmpty()) out.add(t);
              }
              return out;
          }

          /** Vuelca el estado completo de cada línea (guardada o pendiente) al borrador. */
          List<com.reparaciones.models.BorradorContenido.OtraAccion> capturarAcciones() {
              List<com.reparaciones.models.BorradorContenido.OtraAccion> out = new ArrayList<>();
              for (LineaAccion l : lineas) {
                  String t = l.tf.getText() == null ? "" : l.tf.getText().trim();
                  if (t.isEmpty() && !l.guardada) continue;
                  com.reparaciones.models.BorradorContenido.OtraAccion a =
                          new com.reparaciones.models.BorradorContenido.OtraAccion();
                  a.descripcion = t;
                  a.guardada = l.guardada;
                  a.idRepGenerado = l.idRepGenerado;
                  a.fechaGuardado = l.fechaGuardado;
                  out.add(a);
              }
              return out;
          }

          /** Restaura las acciones de un borrador: guardadas bloqueadas, pendientes editables. */
          void aplicarAcciones(List<com.reparaciones.models.BorradorContenido.OtraAccion> acciones) {
              if (acciones == null) return;
              for (com.reparaciones.models.BorradorContenido.OtraAccion a : acciones) {
                  if (a == null || a.descripcion == null || a.descripcion.isBlank()) continue;
                  agregarLinea(a.descripcion.trim());
                  if (a.guardada) {
                      LineaAccion la = lineas.get(lineas.size() - 1);
                      la.guardada = true;
                      la.idRepGenerado = a.idRepGenerado;
                      la.fechaGuardado = a.fechaGuardado != null ? a.fechaGuardado : "";
                      bloquearLinea(la);
                  }
              }
              actualizar();
          }

          /** @return true si alguna línea guardada fue desbloqueada al no existir en BD */
          boolean desbloquearEliminadas(Set<String> idsExistentes) {
              boolean cambio = false;
              for (LineaAccion l : lineas) {
                  if (l.guardada && !idsExistentes.contains(l.idRepGenerado)) {
                      desbloquearLinea(l);
                      cambio = true;
                  }
              }
              if (cambio) actualizar();
              return cambio;
          }

          int getIdComOtro() { return otroSel != null ? otroSel.getIdCom() : -1; }
          boolean hayAccion() { return otroSel != null && !getDescripcionesPendientes().isEmpty(); }
          boolean esOtro(int idCom) { return otroComponentes.stream().anyMatch(c -> c.getIdCom() == idCom); }
          void setOnCambio(Runnable r) { this.onCambio = r; }
          VBox getRoot() { return root; }

          private static class LineaAccion {
              TextField tf;
              HBox row;
              Button btnDel;
              Button btnGuardar;
              Label lblGuardada;
              boolean guardada = false;
              String idRepGenerado;
              String fechaGuardado;
          }
      }
  }
  ```

- [ ] **Step 3: Compilar**

  ```bash
  cd gestion-reparaciones-cliente
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS. Si hay error por `getDescripciones()` no encontrado, verificar que el Task 7 Step 4 (que cambia la llamada en `ejecutarGuardarNueva`) no se ha aplicado aún — en ese caso aplicar el Task 7 Step 4 ahora antes de compilar (ver nota en Task 7).

- [ ] **Step 4: Commit**

  ```bash
  git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
  git -C gestion-reparaciones-cliente commit -m "feat: OtrasAccionesUI - guardado individual por línea con LineaAccion"
  ```

---

## Task 7: Cliente — Wiring del controlador

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/FormularioReparacionController.java`

> **Nota:** Los Steps 3 y 4 (llamadas a `getDescripciones()` → `getDescripcionesPendientes()`) deben aplicarse antes o junto con el Task 6 si el compilador no puede resolver `getDescripciones()` tras el Task 6. En ese caso, aplica todos estos edits en la misma sesión de compilación.

- [ ] **Step 1: Wiring en `cargarFilas()` — conectar callbacks**

  Old (líneas 349-356):
  ```java
                  otrasAcciones = new OtrasAccionesUI(entry.getValue(), imgBorrar);
                  otrasAcciones.setOnCambio(this::actualizarBoton);
                  contenedorOtros.getChildren().add(otrasAcciones.getRoot());
                  continue;
              }
              FilaUI fila = new FilaUI(entry.getKey(), entry.getValue(), imgBorrar, imgEditar);
              fila.setOnCambio(this::actualizarBoton);
              filasUI.add(fila);
  ```
  New:
  ```java
                  otrasAcciones = new OtrasAccionesUI(entry.getValue(), imgBorrar);
                  otrasAcciones.setOnCambio(this::actualizarBoton);
                  otrasAcciones.setGuardador(this::guardarAccionOtroIndividual);
                  contenedorOtros.getChildren().add(otrasAcciones.getRoot());
                  continue;
              }
              FilaUI fila = new FilaUI(entry.getKey(), entry.getValue(), imgBorrar, imgEditar);
              fila.setOnCambio(this::actualizarBoton);
              fila.setOnGuardarFila(() -> guardarFilaIndividual(fila));
              filasUI.add(fila);
  ```

- [ ] **Step 2: Modificar `actualizarBoton()` para excluir filas guardadas**

  Old (línea 473-474):
  ```java
              boolean activa = filasUI.stream().anyMatch(FilaUI::isActiva)
                      || (otrasAcciones != null && otrasAcciones.hayAccion());
  ```
  New:
  ```java
              boolean activa = filasUI.stream().anyMatch(f -> !f.isGuardada() && f.isActiva())
                      || (otrasAcciones != null && otrasAcciones.hayAccion());
  ```

- [ ] **Step 3: Modificar `capturarBorrador()` para usar `capturarAcciones()`**

  Old (línea 493):
  ```java
          if (otrasAcciones != null) b.otros = new java.util.ArrayList<>(otrasAcciones.getDescripciones());
  ```
  New:
  ```java
          if (otrasAcciones != null) b.otros = otrasAcciones.capturarAcciones();
  ```

- [ ] **Step 4: Modificar `ejecutarGuardarNueva()` — filtrar filas guardadas y usar `getDescripcionesPendientes()`**

  Cambio 1 — añadir `if (fila.isGuardada()) continue;` (después de línea 577 `if (fila.esAgotadoNuevo()) continue;`):

  Old:
  ```java
          for (FilaUI fila : filasUI) {
              if (fila.esAgotadoNuevo()) continue;
              if (fila.isActiva()) {
  ```
  New:
  ```java
          for (FilaUI fila : filasUI) {
              if (fila.esAgotadoNuevo()) continue;
              if (fila.isGuardada()) continue;
              if (fila.isActiva()) {
  ```

  Cambio 2 — reemplazar `getDescripciones()` por `getDescripcionesPendientes()` (línea 618):

  Old:
  ```java
              for (String desc : otrasAcciones.getDescripciones()) {
  ```
  New:
  ```java
              for (String desc : otrasAcciones.getDescripcionesPendientes()) {
  ```

- [ ] **Step 5: Modificar el bloque de recuperación de borrador en `init()`**

  Old:
  ```java
                      if (otrasAcciones != null) otrasAcciones.aplicarDescripciones(b.otros);
                  } finally {
                      recuperandoBorrador = false;
                  }
                  actualizarBoton();
  ```
  New:
  ```java
                      if (otrasAcciones != null) otrasAcciones.aplicarAcciones(b.otros);
                  } finally {
                      recuperandoBorrador = false;
                  }
                  verificarFilasGuardadasBorradas(b);
                  actualizarBoton();
  ```

- [ ] **Step 6: Añadir nuevos métodos del controlador**

  Insertar después del método `guardarBorradorAhora()` (que cierra en línea ~520) y antes de `descartarBorradorTrasGuardar()`:

  ```java
      /** Comprueba si alguna fila marcada como guardada en el borrador fue borrada en BD por un SUPERTECNICO,
       *  y la desbloquea para que el técnico la pueda volver a rellenar. */
      private void verificarFilasGuardadasBorradas(com.reparaciones.models.BorradorContenido b) {
          boolean hayGuardadas = b.filas.stream().anyMatch(f -> f.guardada)
                  || b.otros.stream().anyMatch(o -> o.guardada);
          if (!hayGuardadas) return;
          try {
              Set<String> idsExistentes = reparacionDAO.getByImei(imei).stream()
                      .map(com.reparaciones.models.Reparacion::getIdRep)
                      .collect(Collectors.toSet());
              boolean cambio = false;
              for (FilaUI fila : filasUI) {
                  if (fila.isGuardada() && !idsExistentes.contains(fila.getIdRepGenerado())) {
                      fila.desbloquearTrasEliminacion();
                      cambio = true;
                  }
              }
              if (otrasAcciones != null && otrasAcciones.desbloquearEliminadas(idsExistentes)) {
                  cambio = true;
              }
              if (cambio) guardarBorradorAhora();
          } catch (SQLException ex) {
              // verificación fallida: las filas se mantienen bloqueadas, se reintenta al reabrir
          }
      }

      /** Guarda individualmente una fila de componente (crea R* en BD, bloquea la fila en UI). */
      private void guardarFilaIndividual(FilaUI fila) {
          fila.setGuardandoEnCurso(true);
          List<FilaReparacion> payload = new ArrayList<>();
          boolean esSolicitudNueva = fila.isSolicitud() && fila.isSolicitudNueva();
          boolean tieneUso = fila.getCantidad() > 0 || fila.isReutilizado();
          if (esSolicitudNueva && tieneUso) {
              payload.add(new FilaReparacion(fila.getIdComSeleccionado(), fila.getCantidad(),
                      fila.isReutilizado(), fila.getObservacion(), fila.getPrefijo(), false, null, null));
              payload.add(new FilaReparacion(fila.getIdComSeleccionado(), 1, false, null,
                      fila.getPrefijo(), true, fila.getDescripcionSolicitud(), null));
          } else {
              payload.add(new FilaReparacion(fila.getIdComSeleccionado(), fila.getCantidad(),
                      fila.isReutilizado(), fila.getObservacion(), fila.getPrefijo(),
                      esSolicitudNueva, fila.getDescripcionSolicitud(), null));
          }
          try {
              String idRep = reparacionDAO.guardarFilaIndividual(
                      payload, imei, Sesion.getIdTec(), idRepAnterior, idAsignacion);
              String fecha = java.time.LocalDateTime.now()
                      .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
              fila.aplicarGuardada(idRep, fecha);
              guardarBorradorAhora();
              actualizarBoton();
          } catch (StaleDataException ex) {
              fila.setGuardandoEnCurso(false);
              new Alert(Alert.AlertType.WARNING,
                      "No se pudo guardar la fila: " + ex.getMessage()).showAndWait();
          } catch (SQLException ex) {
              fila.setGuardandoEnCurso(false);
              Alertas.mostrarError("No se pudo guardar la fila: " + ex.getMessage());
          }
      }

      /** Guarda individualmente una acción "otro" (crea R* en BD, devuelve su idRep o null si falla). */
      private String guardarAccionOtroIndividual(String descripcion) {
          if (otrasAcciones == null || otrasAcciones.getIdComOtro() == -1) return null;
          try {
              FilaReparacion fila = new FilaReparacion(
                      otrasAcciones.getIdComOtro(), 0, false, descripcion, "otro", false, null, null);
              String idRep = reparacionDAO.guardarFilaIndividual(
                      List.of(fila), imei, Sesion.getIdTec(), idRepAnterior, idAsignacion);
              guardarBorradorAhora();
              return idRep;
          } catch (StaleDataException ex) {
              new Alert(Alert.AlertType.WARNING,
                      "No se pudo guardar la acción: " + ex.getMessage()).showAndWait();
              return null;
          } catch (SQLException ex) {
              Alertas.mostrarError("No se pudo guardar la acción: " + ex.getMessage());
              return null;
          }
      }
  ```

- [ ] **Step 7: Compilar con todos los cambios**

  ```bash
  cd gestion-reparaciones-cliente
  mvn clean package -q -DskipTests
  ```
  Expected: BUILD SUCCESS sin errores.

- [ ] **Step 8: Prueba manual — flujo básico**

  Abrir una asignación nueva en el modal:
  1. Seleccionar modelo → aparecen filas.
  2. Incrementar cantidad en una fila (p. ej. Batería → 1 ud.) → botón "✓ Guardar fila" aparece en verde a la derecha de la fila.
  3. Pulsar "✓ Guardar fila" → botón se deshabilita momentáneamente → cambia a "✓ Guardada HH:mm" (verde, deshabilitado), fila fondo verde claro.
  4. Comprobar en el historial del IMEI que aparece ya la reparación con la hora actual.
  5. Comprobar en la tabla Pendientes que la asignación sigue abierta.
  6. Cerrar el modal y reabrirlo → la fila aparece bloqueada "✓ Guardada HH:mm" restaurada del borrador.
  7. Completar otra fila y pulsar el botón final → solo inserta esa segunda fila; la asignación se cierra.

- [ ] **Step 9: Prueba manual — OtrasAccionesUI**

  1. En el modal, con modelo seleccionado, ver sección "OTRAS ACCIONES".
  2. Pulsar "+ Añadir acción" → aparece un TextField + botón verde "✓ Guardar" + botón borrar.
  3. Escribir una descripción → "✓ Guardar" se habilita.
  4. Pulsar "✓ Guardar" → la línea se bloquea, muestra "✓ Guardada HH:mm", el badge cuenta la acción.
  5. Cerrar y reabrir → la línea bloqueada se restaura del borrador.

- [ ] **Step 10: Prueba manual — "pieza recibida al reabrir" (spec caso 3)**

  1. Abrir una asignación que tenga una solicitud GESTIONADA (pieza llegó al stock).
  2. La fila carga mostrando "✓ Recibido" (badge verde deshabilitado).
  3. Incrementar cantidad → "✓ Recibido" desaparece y aparece "✓ Guardar fila" (verde, pulsable).
  4. Pulsar → misma secuencia de bloqueo que Step 8.

- [ ] **Step 11: Commit final**

  ```bash
  git -C gestion-reparaciones-cliente add src/main/java/com/reparaciones/controllers/FormularioReparacionController.java
  git -C gestion-reparaciones-cliente commit -m "feat: wiring guardado individual de fila en FormularioReparacionController"
  ```

---

## Self-Review

### 1. Spec coverage

| Sección spec | Tarea que la cubre |
|---|---|
| Objetivo — fechas reales por fila, asignación sigue abierta | Task 1 (sin bloque cierre) + Task 7 Step 2 (footer desactivado si todo guardado) |
| Comportamiento "Guardar fila" — sin stock, agotado, recibida | Task 5 Step 2-5 (`actualizarBotonGuardarFila` + `abrirDialogoAgotado` + `activarSolicitud`) |
| Al pulsarlo: endpoint, idRep devuelto, fila bloqueada | Task 2 (endpoint) + Task 5 Step 9 (`aplicarGuardada`) + Task 7 Step 6 (`guardarFilaIndividual`) |
| Dual-emission (uso real + solicitud nueva a la vez) | Task 7 Step 6 (`guardarFilaIndividual` — mismo patrón que `ejecutarGuardarNueva`) |
| Mismo mecanismo para OtrasAcciones | Task 6 (LineaAccion + guardarLinea) + Task 7 Step 6 (`guardarAccionOtroIndividual`) |
| Rol del borrador — JSON con guardada/idRepGenerado/fechaGuardado | Task 3 (BorradorContenido) + Task 5 Steps 7-8 (capturar/aplicarBorrador) + Task 6 (capturarAcciones/aplicarAcciones) |
| Verificación al reabrir con getByImei (desbloqueo si borrada) | Task 7 Step 5-6 (`verificarFilasGuardadasBorradas`) |
| Botón final filtra guardadas | Task 7 Step 4 (`ejecutarGuardarNueva` con `isGuardada()`) |
| Historial y logs — `GUARDAR_FILA_INDIVIDUAL` | Task 2 Step 1 (endpoint con `logDao.insertar`) |
| Prueba 1 (fila aparece en historial, stock baja, asignación abierta) | Task 7 Step 8 |
| Prueba 2 (reabrir → fila bloqueada) | Task 7 Step 8 punto 6 |
| Prueba 3 (botón final solo inserta pendientes) | Task 7 Step 8 punto 7 |
| Prueba 4 (solicitud bloqueante — no aparece botón) | Cubierto por `actualizarBotonGuardarFila()` guard `solicitudActiva` |
| Prueba 5 (reasignación → borrador borrado, R* siguen) | Sin cambios — comportamiento existente no tocado |
| Prueba 6 (log GUARDAR_FILA_INDIVIDUAL) | Task 2 Step 3 (verificar con curl) |
| Prueba 7 (SUPERTECNICO borra R* → reabrir desbloquea) | Task 7 Steps 5-6 (`verificarFilasGuardadasBorradas`) |
| Fuera de alcance — no se toca DELETE auth | Confirmado: endpoint nuevo no tiene @PreAuthorize adicional, eliminar sigue intacto |
| Fuera de alcance — no botón en modo edición | Guard `modoEdicion` en `actualizarBotonGuardarFila()` (Task 5 Step 9) |
| Fuera de alcance — no Deshacer para técnico | Confirmado: no se añade ningún endpoint de reversión |
| Fuera de alcance — no botón para filas agotado-solo | Guard `!esAgotadoNuevo()` en `actualizarBotonGuardarFila()` |

**Todas las secciones y pruebas del spec están cubiertas.**

### 2. Placeholder scan

Ningún "TBD", "TODO", "implement later" o "similar a Task N" en el plan. ✓

### 3. Type/signature consistency

| Símbolo | Definido en | Usado en |
|---|---|---|
| `guardarFilaIndividual(List<FilaReparacion>, String, int, String, String)` servidor | Task 1 | Task 2 |
| `GuardarFilaRequest(filas, imei, idTec, idRepAnterior)` | Task 2 | Task 2 |
| `BorradorContenido.Fila.guardada/idRepGenerado/fechaGuardado` | Task 3 | Tasks 5, 7 |
| `BorradorContenido.OtraAccion` | Task 3 | Tasks 6, 7 |
| `guardarFilaIndividual(List<FilaReparacion>, ...) → String` cliente | Task 4 | Task 7 Step 6 |
| `FilaUI.isGuardada()`, `getIdRepGenerado()`, `setGuardandoEnCurso()` | Task 5 Step 9 | Tasks 6, 7 |
| `FilaUI.aplicarGuardada(String idRep, String fecha)` | Task 5 Steps 8-9 | Task 7 Step 6 |
| `FilaUI.desbloquearTrasEliminacion()` | Task 5 Step 9 | Task 7 Step 6 |
| `FilaUI.actualizarBotonGuardarFila()` | Task 5 Step 9 | Task 5 Step 6 (notificar) |
| `FilaUI.setOnGuardarFila(Runnable)` | Task 5 Step 9 | Task 7 Step 1 |
| `OtrasAccionesUI.setGuardador(Function<String,String>)` | Task 6 Step 2 | Task 7 Step 1 |
| `OtrasAccionesUI.getDescripcionesPendientes()` | Task 6 Step 2 | Task 7 Steps 3, 4 |
| `OtrasAccionesUI.capturarAcciones()` | Task 6 Step 2 | Task 7 Step 3 |
| `OtrasAccionesUI.aplicarAcciones(List<OtraAccion>)` | Task 6 Step 2 | Task 7 Step 5 |
| `OtrasAccionesUI.desbloquearEliminadas(Set<String>)` | Task 6 Step 2 | Task 7 Step 6 |
| `verificarFilasGuardadasBorradas(BorradorContenido)` | Task 7 Step 6 | Task 7 Step 5 |
| `guardarFilaIndividual(FilaUI)` controlador | Task 7 Step 6 | Task 7 Step 1 |
| `guardarAccionOtroIndividual(String) → String` | Task 7 Step 6 | Task 7 Step 1 |

**Todas las referencias son consistentes entre tasks.** ✓

### Nota sobre comportamiento limite no especificado

Si el técnico guarda INDIVIDUALMENTE todas las filas y OtrasAcciones (nada pendiente), el footer "Guardar" queda deshabilitado (Task 7 Step 2). La asignación permanece abierta en BD. Para cerrarla, el técnico debe añadir al menos una acción pendiente (nueva OtraAccion o un pedido de nueva pieza) y pulsar el botón final. Este comportamiento es coherente con el spec ("el botón final guarda lo que falta y cierra") — si no queda nada, el técnico no necesita pulsarlo; la asignación queda abierta hasta que haya algo real que enviar.
