# Pedidos "Otros" (apuntes ajenos al stock) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir anotar en la vista de Pedidos pedidos ajenos al inventario (consumibles: alcohol, cajas…) que reutilizan proveedor, urgente, precio y la lógica de recepción, pero **sin afectar al stock**.

**Architecture:** Tabla nueva `Compra_otro` (gemela de `Compra_componente`, con `CONCEPTO` en vez de `ID_COM`, única FK a `Proveedor`). Tríada servidor paralela (modelo/DAO/controller en `/api/compras-otros`) idéntica a la de compras **menos** todo `UPDATE Componente SET STOCK`. En el cliente, un toggle "Componentes | Otros" dentro de Pedidos con una segunda tabla y un formulario de un registro. Auditoría con códigos `*_OTRO`.

**Tech Stack:** Java 21, Spring Boot + JdbcTemplate (servidor), JavaFX + FXML (cliente), MariaDB, Maven.

**Spec:** `docs/superpowers/specs/2026-06-26-pedidos-otros-design.md`

## Global Constraints

- `ESTADO` es `ENUM('pendiente','en_camino','parcial','recibido','cancelado')` (idéntico a `Compra_componente`).
- `Compra_otro` **nunca** ejecuta `UPDATE Componente SET STOCK …` ni toca `Componente`. Su única FK es `ID_PROV → Proveedor`.
- Bloqueo optimista por `UPDATED_AT` truncado a segundos (mismo `checkUpdatedAt` que compras).
- Mutaciones: `@PreAuthorize("hasRole('SUPERTECNICO')")`; lecturas: `hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')`.
- Conversión a EUR vía `TipoCambioDAO.getTasa(divisa)`; divisas soportadas en UI: `EUR`, `USD`.
- Logs con códigos propios sufijo `_OTRO`; detalle con `CONCEPTO: …, PROVEEDOR: …, CANT: …`.
- **Testing approach:** el repo no tiene tests en capas DAO/controller/FXML ni arnés JavaFX/JDBC. Conforme al §10 del spec, la verificación de cada tarea es **compilar** + **arranque del contexto Spring** (servidor) + **prueba manual del flujo**. No se introducen tests automáticos nuevos.
- Trabajar en la rama `feat/pedidos-otros`. Commits frecuentes, uno por tarea. **Sin** `Co-Authored-By`.

---

# FASE A — Servidor (`gestion-reparaciones-servidor`)

Produce la API `/api/compras-otros` funcional y auditada. Verificable de forma independiente (arranque + curl/Postman).

### Task A1: Tabla `Compra_otro` (DDL + migración)

**Files:**
- Modify: `sql/crear_bd.sql` (tras el bloque `CREATE TABLE Compra_componente`, línea ~142)
- Create: `sql/migracion-pedidos-otros.sql`

**Interfaces:**
- Produces: tabla `Compra_otro` con columnas `ID_COMPRA_OTRO, ID_PROV, CONCEPTO, CANTIDAD, CANTIDAD_RECIBIDA, ES_URGENTE, FECHA_PEDIDO, FECHA_LLEGADA, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO, UPDATED_AT`.

- [ ] **Step 1: Añadir el DDL en `crear_bd.sql`** (después de la tabla `Compra_componente`)

```sql
CREATE TABLE Compra_otro (
    ID_COMPRA_OTRO       INT           NOT NULL AUTO_INCREMENT,
    ID_PROV              INT           NOT NULL,
    CONCEPTO             VARCHAR(255)  NOT NULL,
    CANTIDAD             INT           NOT NULL,
    CANTIDAD_RECIBIDA    INT,
    ES_URGENTE           BOOLEAN       NOT NULL DEFAULT FALSE,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FECHA_LLEGADA        DATETIME,
    PRECIO_UNIDAD_PEDIDO DECIMAL(10,2) NOT NULL DEFAULT 0,
    DIVISA               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    PRECIO_EUR           DECIMAL(10,2) NOT NULL DEFAULT 0,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA_OTRO),
    CONSTRAINT fk_compra_otro_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);
```

Y añadir `DROP TABLE IF EXISTS Compra_otro;` en el bloque de DROPs del principio (antes de `DROP TABLE IF EXISTS Compra_componente;`).

- [ ] **Step 2: Crear `sql/migracion-pedidos-otros.sql`** (para aplicar sobre BD existente; mismo patrón que las demás `migracion-*.sql`)

```sql
-- Migración: pedidos "Otros" (apuntes ajenos al stock). Aplicar sobre BD existente.
CREATE TABLE IF NOT EXISTS Compra_otro (
    ID_COMPRA_OTRO       INT           NOT NULL AUTO_INCREMENT,
    ID_PROV              INT           NOT NULL,
    CONCEPTO             VARCHAR(255)  NOT NULL,
    CANTIDAD             INT           NOT NULL,
    CANTIDAD_RECIBIDA    INT,
    ES_URGENTE           BOOLEAN       NOT NULL DEFAULT FALSE,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FECHA_LLEGADA        DATETIME,
    PRECIO_UNIDAD_PEDIDO DECIMAL(10,2) NOT NULL DEFAULT 0,
    DIVISA               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    PRECIO_EUR           DECIMAL(10,2) NOT NULL DEFAULT 0,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA_OTRO),
    CONSTRAINT fk_compra_otro_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);
```

- [ ] **Step 3: Verificación** — revisar que la sintaxis es válida (mismo dialecto MariaDB que el resto del fichero). Aplicar el script en la BD de desarrollo local: `mysql <bd> < sql/migracion-pedidos-otros.sql` y comprobar `DESCRIBE Compra_otro;`.

- [ ] **Step 4: Commit**

```bash
git add sql/crear_bd.sql sql/migracion-pedidos-otros.sql
git commit -m "feat(bd): tabla Compra_otro para pedidos ajenos al stock"
```

---

### Task A2: Modelo `CompraOtro` (servidor)

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/CompraOtro.java`

**Interfaces:**
- Produces: clase `CompraOtro` con getters: `getIdCompraOtro():int`, `getIdProv():int`, `getNombreProveedor():String`, `getConcepto():String`, `getCantidad():int`, `getCantidadRecibida():Integer`, `isEsUrgente():boolean`, `getFechaPedido():LocalDateTime`, `getFechaLlegada():LocalDateTime`, `getPrecioUnidadPedido():double`, `getDivisa():String`, `getPrecioEur():double`, `getEstado():String`, `getUpdatedAt():LocalDateTime`.

- [ ] **Step 1: Crear el modelo** (espejo de `model/CompraComponente.java`, `idCom`/`tipoComponente` → `concepto`)

```java
package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class CompraOtro {
    private int           idCompraOtro;
    private int           idProv;
    private String        nombreProveedor;
    private String        concepto;
    private int           cantidad;
    private Integer       cantidadRecibida;
    private boolean       esUrgente;
    private LocalDateTime fechaPedido;
    private LocalDateTime fechaLlegada;
    private double        precioUnidadPedido;
    private String        divisa;
    private double        precioEur;
    private String        estado;
    private LocalDateTime updatedAt;

    public CompraOtro() {}

    public CompraOtro(int idCompraOtro, int idProv, String nombreProveedor, String concepto,
                      int cantidad, Integer cantidadRecibida, boolean esUrgente,
                      LocalDateTime fechaPedido, LocalDateTime fechaLlegada,
                      double precioUnidadPedido, String divisa, double precioEur,
                      String estado, LocalDateTime updatedAt) {
        this.idCompraOtro       = idCompraOtro;
        this.idProv             = idProv;
        this.nombreProveedor    = nombreProveedor;
        this.concepto           = concepto;
        this.cantidad           = cantidad;
        this.cantidadRecibida   = cantidadRecibida;
        this.esUrgente          = esUrgente;
        this.fechaPedido        = fechaPedido;
        this.fechaLlegada       = fechaLlegada;
        this.precioUnidadPedido = precioUnidadPedido;
        this.divisa             = divisa;
        this.precioEur          = precioEur;
        this.estado             = estado;
        this.updatedAt          = updatedAt;
    }

    public int           getIdCompraOtro()       { return idCompraOtro; }
    public int           getIdProv()             { return idProv; }
    public String        getNombreProveedor()    { return nombreProveedor; }
    public String        getConcepto()           { return concepto; }
    public int           getCantidad()           { return cantidad; }
    public Integer       getCantidadRecibida()   { return cantidadRecibida; }
    public boolean       isEsUrgente()           { return esUrgente; }
    public LocalDateTime getFechaPedido()        { return fechaPedido; }
    public LocalDateTime getFechaLlegada()       { return fechaLlegada; }
    public double        getPrecioUnidadPedido() { return precioUnidadPedido; }
    public String        getDivisa()             { return divisa; }
    public double        getPrecioEur()          { return precioEur; }
    public String        getEstado()             { return estado; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
}
```

- [ ] **Step 2: Verificación** — `mvn -q -o compile` (debe compilar sin tocar nada más).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/CompraOtro.java
git commit -m "feat(servidor): modelo CompraOtro"
```

---

### Task A3: DAO `CompraOtroDAO` (servidor)

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java`

**Interfaces:**
- Consumes: `CompraOtro` (Task A2).
- Produces: `@Repository CompraOtroDAO` con métodos: `getAll():List<CompraOtro>`, `getById(int):Optional<CompraOtro>`, `insertar(int idProv, String concepto, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur):void`, `editar(int id, int idProv, String concepto, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt):void`, `confirmar(int,LocalDateTime)`, `confirmarRecibido(int,LocalDateTime)`, `confirmarParcial(int,int,LocalDateTime)`, `recibirResto(int,int,LocalDateTime)`, `confirmarAlterado(int,LocalDateTime)`, `cancelar(int,LocalDateTime)`, `desrecibir(int,LocalDateTime)`, `borrarPendiente(int)`.

> Espejo de `dao/CompraComponenteDAO.java` **sin** ninguna sentencia `UPDATE Componente …`, sin `resolveToMasterId`, sin `getEnCamino`/`getCantidadEnCaminoPorComponente`. `desrecibir` solo revierte estado (sin chequeo de stock).

- [ ] **Step 1: Crear el DAO completo**

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraOtro;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Repository
public class CompraOtroDAO {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT co.ID_COMPRA_OTRO, co.ID_PROV, p.NOMBRE AS NOMBRE_PROVEEDOR, co.CONCEPTO," +
            " co.CANTIDAD, co.CANTIDAD_RECIBIDA, co.ES_URGENTE, co.FECHA_PEDIDO, co.FECHA_LLEGADA," +
            " co.PRECIO_UNIDAD_PEDIDO, co.DIVISA, co.PRECIO_EUR, co.ESTADO, co.UPDATED_AT" +
            " FROM Compra_otro co JOIN Proveedor p ON co.ID_PROV = p.ID_PROV";

    private static final RowMapper<CompraOtro> MAPPER = (rs, row) -> new CompraOtro(
            rs.getInt("ID_COMPRA_OTRO"),
            rs.getInt("ID_PROV"),
            rs.getString("NOMBRE_PROVEEDOR"),
            rs.getString("CONCEPTO"),
            rs.getInt("CANTIDAD"),
            rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
            rs.getBoolean("ES_URGENTE"),
            rs.getTimestamp("FECHA_PEDIDO") != null ? rs.getTimestamp("FECHA_PEDIDO").toLocalDateTime() : null,
            rs.getTimestamp("FECHA_LLEGADA") != null ? rs.getTimestamp("FECHA_LLEGADA").toLocalDateTime() : null,
            rs.getDouble("PRECIO_UNIDAD_PEDIDO"),
            rs.getString("DIVISA"),
            rs.getDouble("PRECIO_EUR"),
            rs.getString("ESTADO"),
            rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS));

    public CompraOtroDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompraOtro> getAll() {
        return jdbc.query(SELECT_BASE + " ORDER BY co.FECHA_PEDIDO DESC", MAPPER);
    }

    public Optional<CompraOtro> getById(int id) {
        List<CompraOtro> rows = jdbc.query(SELECT_BASE + " WHERE co.ID_COMPRA_OTRO = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public void insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) {
        jdbc.update(
                "INSERT INTO Compra_otro" +
                " (ID_PROV, CONCEPTO, CANTIDAD, ES_URGENTE, FECHA_PEDIDO, PRECIO_UNIDAD_PEDIDO, DIVISA, PRECIO_EUR, ESTADO)" +
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, 'pendiente')",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur);
    }

    public void editar(int id, int idProv, String concepto, int cantidad, boolean esUrgente,
                       double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update(
                "UPDATE Compra_otro SET ID_PROV=?, CONCEPTO=?, CANTIDAD=?, ES_URGENTE=?," +
                " PRECIO_UNIDAD_PEDIDO=?, DIVISA=?, PRECIO_EUR=? WHERE ID_COMPRA_OTRO=?",
                idProv, concepto, cantidad, esUrgente, precioUnidad, divisa, precioEur, id);
    }

    public void confirmar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int n = jdbc.update(
                "UPDATE Compra_otro SET ESTADO='en_camino' WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente");
    }

    public void confirmarRecibido(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='recibido', FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=?", id);
    }

    public void confirmarParcial(int id, int cantidadRecibida, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update(
                "UPDATE Compra_otro SET ESTADO='parcial', CANTIDAD_RECIBIDA=?, FECHA_LLEGADA=NOW() WHERE ID_COMPRA_OTRO=?",
                cantidadRecibida, id);
    }

    public void recibirResto(int id, int cantidadExtra, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        int nuevaRecibida = (r.cantidadRecibida() != null ? r.cantidadRecibida() : 0) + cantidadExtra;
        boolean completo = nuevaRecibida >= r.cantidad();
        jdbc.update(
                "UPDATE Compra_otro" +
                " SET CANTIDAD_RECIBIDA = COALESCE(CANTIDAD_RECIBIDA, 0) + ?" +
                (completo ? ", ESTADO = 'recibido'" : "") +
                " WHERE ID_COMPRA_OTRO=?",
                cantidadExtra, id);
    }

    public void confirmarAlterado(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='recibido' WHERE ID_COMPRA_OTRO=?", id);
    }

    public void cancelar(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='cancelado' WHERE ID_COMPRA_OTRO=?", id);
    }

    /** Revierte a 'en_camino'. Sin stock que descontar → no hay chequeo. */
    public void desrecibir(int id, LocalDateTime updatedAt) {
        Row r = getRow(id);
        checkUpdatedAt(r.updatedAt(), updatedAt);
        jdbc.update("UPDATE Compra_otro SET ESTADO='en_camino', FECHA_LLEGADA=NULL, CANTIDAD_RECIBIDA=NULL" +
                " WHERE ID_COMPRA_OTRO=?", id);
    }

    public void borrarPendiente(int id) {
        int n = jdbc.update("DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente'", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El pedido ya no está pendiente (no se puede borrar)");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record Row(int cantidad, Integer cantidadRecibida, LocalDateTime updatedAt) {}

    private Row getRow(int id) {
        return jdbc.queryForObject(
                "SELECT CANTIDAD, CANTIDAD_RECIBIDA, UPDATED_AT FROM Compra_otro WHERE ID_COMPRA_OTRO = ?",
                (rs, row) -> new Row(
                        rs.getInt("CANTIDAD"),
                        rs.getObject("CANTIDAD_RECIBIDA", Integer.class),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)),
                id);
    }

    private void checkUpdatedAt(LocalDateTime bdAt, LocalDateTime clientAt) {
        if (!clientAt.truncatedTo(ChronoUnit.SECONDS).equals(bdAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }
}
```

- [ ] **Step 2: Verificación** — `mvn -q -o compile`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/CompraOtroDAO.java
git commit -m "feat(servidor): CompraOtroDAO (recepción sin efecto en stock)"
```

---

### Task A4: Controller `CompraOtroController` (servidor) + logs `_OTRO`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java`

**Interfaces:**
- Consumes: `CompraOtroDAO` (A3), `LogDAO.insertar(int idUsu, String accion, String detalle)`, `ProveedorDAO.getNombreById(int):String`, `UsuarioPrincipal.getIdUsu():int`.
- Produces: endpoints REST en `/api/compras-otros` (ver tabla del spec §4.3).

> Espejo de `controller/CompraController.java`. Inyecta `CompraOtroDAO`, `LogDAO`, `ProveedorDAO` (no `ComponenteDAO`). Records de request idénticos a los de compras pero con `concepto` en vez de `idCom`.

- [ ] **Step 1: Crear el controller**

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraOtro;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/compras-otros")
public class CompraOtroController {

    private final CompraOtroDAO dao;
    private final LogDAO        logDao;
    private final ProveedorDAO  proveedorDao;

    public CompraOtroController(CompraOtroDAO dao, LogDAO logDao, ProveedorDAO proveedorDao) {
        this.dao          = dao;
        this.logDao       = logDao;
        this.proveedorDao = proveedorDao;
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN', 'TECNICO')")
    @GetMapping
    public List<CompraOtro> getAll() {
        return dao.getAll();
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur());
        String proveedor = proveedorDao.getNombreById(req.idProv());
        logDao.insertar(principal.getIdUsu(), "CREAR_PEDIDO_OTRO",
                "CONCEPTO: " + req.concepto() + ", PROVEEDOR: " + proveedor + ", CANT: " + req.cantidad());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{id}")
    public void editar(@PathVariable int id, @RequestBody EditarRequest req,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.editar(id, req.idProv(), req.concepto(), req.cantidad(), req.esUrgente(),
                req.precioUnidad(), req.divisa(), req.precioEur(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar")
    public void confirmar(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmar(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CONFIRMAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-recibido")
    public void confirmarRecibido(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        CompraOtro c = dao.getById(id).orElse(null);
        dao.confirmarRecibido(id, req.updatedAt());
        String detalle = c != null
                ? "ID_COMPRA_OTRO: " + id + ", CONCEPTO: " + c.getConcepto() + ", CANT: " + c.getCantidad()
                : "ID_COMPRA_OTRO: " + id;
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PEDIDO_OTRO", detalle);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-parcial")
    public void confirmarParcial(@PathVariable int id, @RequestBody ConfirmarParcialRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.confirmarParcial(id, req.cantidadRecibida(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_PARCIAL_OTRO",
                "ID_COMPRA_OTRO: " + id + ", CANT_RECIBIDA: " + req.cantidadRecibida());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/recibir-resto")
    public void recibirResto(@PathVariable int id, @RequestBody RecibirRestoRequest req,
                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.recibirResto(id, req.cantidadExtra(), req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "RECIBIR_RESTO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/confirmar-alterado")
    public void confirmarAlterado(@PathVariable int id, @RequestBody UpdatedAtRequest req) {
        dao.confirmarAlterado(id, req.updatedAt());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/desrecibir")
    public void desrecibir(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.desrecibir(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "EDITAR_PEDIDO_OTRO", "DESRECIBIR ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{id}/cancelar")
    public void cancelar(@PathVariable int id, @RequestBody UpdatedAtRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.cancelar(id, req.updatedAt());
        logDao.insertar(principal.getIdUsu(), "CANCELAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{id}")
    public void borrar(@PathVariable int id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrarPendiente(id);
        logDao.insertar(principal.getIdUsu(), "BORRAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: " + id);
    }

    private record InsertarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                                   double precioUnidad, String divisa, double precioEur) {}
    private record EditarRequest(int idProv, String concepto, int cantidad, boolean esUrgente,
                                 double precioUnidad, String divisa, double precioEur, LocalDateTime updatedAt) {}
    private record UpdatedAtRequest(LocalDateTime updatedAt) {}
    private record ConfirmarParcialRequest(int cantidadRecibida, LocalDateTime updatedAt) {}
    private record RecibirRestoRequest(int cantidadExtra, LocalDateTime updatedAt) {}
}
```

> **Nota:** verificar el paquete real de `UsuarioPrincipal` mirando los `import` de `CompraController.java` y ajustarlo si difiere. Las acciones `confirmar-alterado` y `desrecibir` siguen el criterio de auditoría de las compras (la primera sin log; aquí `desrecibir` se registra como `EDITAR_PEDIDO_OTRO` para dejar rastro — si se prefiere paridad exacta sin log, eliminar esa línea).

- [ ] **Step 2: Verificación de compilación** — `mvn -q -o compile`.

- [ ] **Step 3: Verificación de arranque del contexto Spring** (CRÍTICO — no hay test de contexto)

Run: `mvn -q -o spring-boot:run` (o arranque desde el IDE) contra la BD local con `Compra_otro` ya creada.
Expected: el contexto levanta sin errores de wiring; en el log aparece `Started ... in N seconds`. Probar:
```bash
curl -s -H "Authorization: Bearer <token-supertecnico>" http://localhost:8080/api/compras-otros
```
Expected: `[]` (lista vacía) con HTTP 200.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/CompraOtroController.java
git commit -m "feat(servidor): CompraOtroController /api/compras-otros + logs _OTRO"
```

---

### Task A5: `ProveedorDAO.tienePedidos` cuenta también `Compra_otro`

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/dao/ProveedorDAO.java` (método `tienePedidos`, ~líneas 39-44)

**Interfaces:**
- Modifies: `tienePedidos(int idProv):boolean` → true si el proveedor tiene pedidos en `Compra_componente` **o** en `Compra_otro`.

- [ ] **Step 1: Reemplazar el cuerpo de `tienePedidos`**

```java
    public boolean tienePedidos(int idProv) {
        Integer count = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM Compra_componente WHERE ID_PROV = ?)" +
                "     + (SELECT COUNT(*) FROM Compra_otro       WHERE ID_PROV = ?)",
                Integer.class, idProv, idProv);
        return count != null && count > 0;
    }
```

- [ ] **Step 2: Verificación** — `mvn -q -o compile`. Manual: con un proveedor que solo tenga un "otro pedido", intentar borrarlo desde la UI de Proveedores → debe **bloquearse**.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/ProveedorDAO.java
git commit -m "fix(servidor): tienePedidos cuenta también Compra_otro (evita huérfanos)"
```

---

# FASE B — Cliente (`gestion-reparaciones-cliente`)

Consume la API de la Fase A. Toggle "Componentes | Otros" en Pedidos + formulario + logs.

### Task B1: Modelo `CompraOtro` (cliente)

**Files:**
- Create: `src/main/java/com/reparaciones/models/CompraOtro.java`

**Interfaces:**
- Consumes: enum `Estado` existente (definido en `models/CompraComponente.java`).
- Produces: clase `CompraOtro` con getters análogos a `CompraComponente` pero con `getConcepto():String` (sin `idCom`/`tipoComponente`) y `getEstado():Estado`.

- [ ] **Step 1: Crear el modelo** (espejo de `models/CompraComponente.java`; reutiliza el enum `CompraComponente.Estado`). Campos: `idCompraOtro, idProv, nombreProveedor, concepto, cantidad, cantidadRecibida(Integer), esUrgente, fechaPedido, fechaLlegada, precioUnidadPedido, divisa, precioEur, estado(Estado), updatedAt`. Getters con los mismos nombres que en `CompraComponente` salvo `getIdCompraOtro()` y `getConcepto()`.

> Para deserializar el campo `estado` (String del JSON) al enum `Estado`, replicar exactamente el mecanismo que usa `CompraComponente` (mismo tipo de campo `Estado estado` — Gson mapea el string al valor del enum por nombre). Si `Estado` está anidado en `CompraComponente`, referenciarlo como `CompraComponente.Estado`.

- [ ] **Step 2: Verificación** — `mvn -q -o compile`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/models/CompraOtro.java
git commit -m "feat(cliente): modelo CompraOtro"
```

---

### Task B2: DAO HTTP `CompraOtroDAO` (cliente)

**Files:**
- Create: `src/main/java/com/reparaciones/dao/CompraOtroDAO.java`

**Interfaces:**
- Consumes: `ApiClient.getList`, `ApiClient.post`, `ApiClient.put`, `ApiClient.patch`, `ApiClient.delete`; `CompraOtro` (B1); `StaleDataException`.
- Produces: `CompraOtroDAO` con `getAll():List<CompraOtro>`, `insertar(int idProv, String concepto, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur)`, `editar(CompraOtro, int idProv, String concepto, int cantidad, boolean esUrgente, double precioUnidad, String divisa, double precioEur)`, `confirmar(CompraOtro)`, `confirmarRecibido(CompraOtro)`, `confirmarParcial(CompraOtro, int)`, `recibirResto(CompraOtro, int)`, `confirmarAlterado(CompraOtro)`, `cancelar(CompraOtro)`, `desrecibir(CompraOtro)`, `borrar(CompraOtro)`.

> Espejo de `dao/CompraComponenteDAO.java` (cliente) apuntando a `/api/compras-otros`. Cada método de mutación pasa `Map.of("updatedAt", pedido.getUpdatedAt())` salvo insertar/editar (que pasan los campos). Endpoints: POST ``/api/compras-otros``, PUT ``/{id}``, PATCH ``/{id}/confirmar``, ``/confirmar-recibido``, ``/confirmar-parcial`` (`cantidadRecibida`), ``/recibir-resto`` (`cantidadExtra`), ``/confirmar-alterado``, ``/desrecibir``, ``/cancelar``, DELETE ``/{id}``.

- [ ] **Step 1: Crear el DAO** siguiendo el patrón exacto del de componentes. Ejemplo de los métodos clave:

```java
    public List<CompraOtro> getAll() throws SQLException {
        return ApiClient.getList("/api/compras-otros", CompraOtro.class);
    }

    public void insertar(int idProv, String concepto, int cantidad, boolean esUrgente,
                         double precioUnidad, String divisa, double precioEur) throws SQLException {
        ApiClient.post("/api/compras-otros", Map.of(
                "idProv", idProv, "concepto", concepto, "cantidad", cantidad,
                "esUrgente", esUrgente, "precioUnidad", precioUnidad,
                "divisa", divisa, "precioEur", precioEur));
    }

    public void confirmar(CompraOtro p) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/confirmar",
                Map.of("updatedAt", p.getUpdatedAt()));
    }

    public void confirmarParcial(CompraOtro p, int cantidadRecibida) throws SQLException, StaleDataException {
        ApiClient.patch("/api/compras-otros/" + p.getIdCompraOtro() + "/confirmar-parcial",
                Map.of("updatedAt", p.getUpdatedAt(), "cantidadRecibida", cantidadRecibida));
    }
    // … resto análogo (confirmarRecibido, recibirResto[cantidadExtra], confirmarAlterado, cancelar, desrecibir, editar, borrar)
```

- [ ] **Step 2: Verificación** — `mvn -q -o compile`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/reparaciones/dao/CompraOtroDAO.java
git commit -m "feat(cliente): CompraOtroDAO (HTTP /api/compras-otros)"
```

---

### Task B3: FXML — toggle + segunda tabla en el panel Pedidos

**Files:**
- Modify: `src/main/resources/views/StockView.fxml` (panel `pnlPedidos`, ~líneas 109-141)

**Interfaces:**
- Produces (fx:id para StockController): `toggleCompPedidos`, `toggleOtrosPedidos` (ToggleButton); `tablaOtros` (TableView) con columnas `coFecha, coConcepto, coProveedor, coCantidad, coPrecio, coEur, coEstado` (+ `coId` oculta); `btnNuevoOtroPedido` (Button).

- [ ] **Step 1: En la fila del título de `pnlPedidos`**, añadir el toggle a la derecha (patrón Agrupado|Plano del historial):

```xml
<HBox alignment="CENTER_LEFT" spacing="12">
    <Label text="Pedidos" styleClass="vista-titulo"/>
    <Region HBox.hgrow="ALWAYS"/>
    <HBox spacing="0" alignment="CENTER_LEFT">
        <ToggleButton fx:id="toggleCompPedidos" text="Componentes" selected="true" styleClass="toggle-pill-left"/>
        <ToggleButton fx:id="toggleOtrosPedidos" text="Otros" styleClass="toggle-pill-right"/>
    </HBox>
</HBox>
```

- [ ] **Step 2: Añadir `btnNuevoOtroPedido`** al final del `FlowPane` de filtros (junto a `btnNuevoPedido`, oculto/visible según toggle — se gestiona en B4):

```xml
<Button fx:id="btnNuevoOtroPedido" text="Nuevo otro pedido" styleClass="btn-primary"
        onAction="#nuevoOtroPedido" visible="false" managed="false">
    <FlowPane.margin><Insets left="24"/></FlowPane.margin>
</Button>
```

- [ ] **Step 3: Añadir `tablaOtros`** justo después de `tablaPedidos`, inicialmente oculta:

```xml
<TableView fx:id="tablaOtros" styleClass="tabla-reparaciones" VBox.vgrow="ALWAYS"
           visible="false" managed="false">
    <columns>
        <TableColumn fx:id="coFecha"     text="Pedido"     prefWidth="115"/>
        <TableColumn fx:id="coConcepto"  text="Concepto"   prefWidth="220"/>
        <TableColumn fx:id="coProveedor" text="Proveedor"  prefWidth="130"/>
        <TableColumn fx:id="coCantidad"  text="Cant."      prefWidth="60"/>
        <TableColumn fx:id="coPrecio"    text="P.Unit."    prefWidth="80"/>
        <TableColumn fx:id="coEur"       text="EUR"        prefWidth="80"/>
        <TableColumn fx:id="coEstado"    text="Estado"     prefWidth="110"/>
        <TableColumn fx:id="coId"        text="ID"         prefWidth="50" visible="false"/>
    </columns>
    <placeholder><Label text="Sin otros pedidos"/></placeholder>
</TableView>
```

- [ ] **Step 4: Verificación** — `mvn -q -o compile` (el FXML se valida al cargar; la validación real es el arranque de la app en B4). Revisar que los `fx:id` y `onAction` no choquen con los existentes.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/views/StockView.fxml
git commit -m "feat(cliente): toggle Componentes|Otros y tabla de otros pedidos (FXML)"
```

---

### Task B4: StockController — toggle, tabla Otros, filtros, menú contextual, carga, rol

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/StockController.java`

**Interfaces:**
- Consumes: `CompraOtroDAO` (B2), `CompraOtro` (B1), fx:id de B3.
- Produces: comportamiento del panel Pedidos con el toggle.

> Reutiliza los *cell factories* de `configurarTablaPedidos` (cantidad/precio/EUR, badge de estado, color de fila por estado) — son genéricos sobre `getEstado()/getCantidad()/…`; aplícalos a `CompraOtro`. La celda `coConcepto` es un `Label` simple (sin navegación). El menú contextual reproduce `construirMenuContextual` para `CompraOtro` (mismas acciones, llamando a `compraOtroDAO`).

- [ ] **Step 1:** Declarar campos: `@FXML private ToggleButton toggleCompPedidos, toggleOtrosPedidos;` `@FXML private TableView<CompraOtro> tablaOtros;` las `@FXML private TableColumn<…> coFecha, coConcepto, coProveedor, coCantidad, coPrecio, coEur, coEstado, coId;` `@FXML private Button btnNuevoOtroPedido;` `private final CompraOtroDAO compraOtroDAO = new CompraOtroDAO();` `private final ObservableList<CompraOtro> datosOtros = FXCollections.observableArrayList();`

- [ ] **Step 2:** En `initialize()`, tras `configurarTablaPedidos()`, llamar a `configurarTablaOtros()` y `configurarTogglePedidos()`. Ocultar `btnNuevoOtroPedido` si `!Sesion.esSuperTecnico()` (junto al bloque que ya oculta `btnNuevoPedido`).

- [ ] **Step 3:** Implementar `configurarTogglePedidos()`:

```java
private void configurarTogglePedidos() {
    ToggleGroup grupo = new ToggleGroup();
    toggleCompPedidos.setToggleGroup(grupo);
    toggleOtrosPedidos.setToggleGroup(grupo);
    grupo.selectedToggleProperty().addListener((obs, old, sel) -> {
        if (sel == null) { old.setSelected(true); return; }   // no permitir deselección
        boolean otros = sel == toggleOtrosPedidos;
        tablaPedidos.setVisible(!otros); tablaPedidos.setManaged(!otros);
        tablaOtros.setVisible(otros);    tablaOtros.setManaged(otros);
        boolean superT = com.reparaciones.Sesion.esSuperTecnico();
        btnNuevoPedido.setVisible(!otros && superT);     btnNuevoPedido.setManaged(!otros && superT);
        btnNuevoOtroPedido.setVisible(otros && superT);  btnNuevoOtroPedido.setManaged(otros && superT);
        if (otros) cargarOtros(); else cargarPedidos();
    });
}
```

- [ ] **Step 4:** Implementar `configurarTablaOtros()` y `cargarOtros()` (espejo de `configurarTablaPedidos`/`cargarPedidos`, con `coConcepto` = Label simple, filtros por estado/proveedor/fecha y buscador por concepto, menú contextual por estado llamando a `compraOtroDAO`). `cargarOtros()`:

```java
private void cargarOtros() {
    try {
        datosOtros.setAll(compraOtroDAO.getAll());
        String hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (lblUltimaActPedidos != null) lblUltimaActPedidos.setText("Actualizado " + hora);
    } catch (SQLException e) { mostrarError(e); }
}
```

- [ ] **Step 5:** `@FXML private void nuevoOtroPedido()` → abre el formulario (Task B5):

```java
@FXML private void nuevoOtroPedido() {
    FormularioOtroPedidoController.abrir(() -> cargarOtros());
}
```

- [ ] **Step 6:** Ajustar `recargar()` para refrescar la tabla activa del toggle cuando `pnlPedidos` está visible:

```java
if (pnlPedidos.isVisible()) { if (toggleOtrosPedidos.isSelected()) cargarOtros(); else cargarPedidos(); }
```

- [ ] **Step 7: Verificación** — `mvn -q -o compile`, luego **arrancar la app** (Task de ejecución usará el skill `run`). Manual: ir a Stock → Pedidos, alternar el toggle; la tabla cambia, el botón cambia ("Nuevo pedido" ↔ "Nuevo otro pedido"), los filtros aplican a la tabla activa.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/StockController.java
git commit -m "feat(cliente): toggle Componentes|Otros, tabla y carga de otros pedidos"
```

---

### Task B5: Formulario "Nuevo otro pedido"

**Files:**
- Create: `src/main/resources/views/FormularioOtroPedidoView.fxml`
- Create: `src/main/java/com/reparaciones/controllers/FormularioOtroPedidoController.java`

**Interfaces:**
- Consumes: `ProveedorDAO.getActivos()`, `TipoCambioDAO.getTasa(String)`, `CompraOtroDAO.insertar(...)` (B2).
- Produces: `static void abrir(Runnable onGuardado)`.

> Modelado sobre `FormularioCompraEditarController` (un registro): añade `txtConcepto` (TextField) y **quita** `lblComponente`. Campos: `txtConcepto, cmbProveedor (ComboBox<Proveedor>), txtCantidad, chkUrgente, txtPrecio, cmbDivisa, lblTotalEur`. Reutiliza `fetchTasaAsync`/`calcularTotal`.

- [ ] **Step 1: Crear el FXML** con los controles anteriores (mirar `FormularioCompraEditar.fxml` para estilos), botones `Guardar`/`Cancelar` con `onAction="#confirmar"`/`#cancelar` y `fx:controller="com.reparaciones.controllers.FormularioOtroPedidoController"`.

- [ ] **Step 2: Crear el controller** (basado en `FormularioCompraEditarController`, sin `pedidoEditar`; `confirmar()` valida concepto no vacío, cantidad > 0, precio ≥ 0, calcula `precioEur = precio * tasa` y llama `compraOtroDAO.insertar(prov.getIdProv(), concepto, cant, urgente, precio, divisa, precioEur)`). Método estático `abrir`:

```java
public static void abrir(Runnable onGuardado) {
    try {
        FXMLLoader loader = new FXMLLoader(
                FormularioOtroPedidoController.class.getResource("/views/FormularioOtroPedidoView.fxml"));
        Parent root = loader.load();
        FormularioOtroPedidoController ctrl = loader.getController();
        ctrl.init(onGuardado);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Nuevo otro pedido");
        stage.setScene(new Scene(root));
        stage.showAndWait();
    } catch (Exception e) {
        com.reparaciones.utils.Alertas.mostrarError("No se pudo abrir el formulario: " + e.getMessage());
    }
}
```

> Verificar el patrón exacto de apertura (`abrir`/`abrirEditar`) en `FormularioCompraController`/`FormularioCompraEditarController` y replicarlo (estilos de Scene, `initModality`, etc.).

- [ ] **Step 3: Verificación** — `mvn -q -o compile` + arrancar app: con sesión SUPERTECNICO, Pedidos → Otros → "Nuevo otro pedido", crear uno (p. ej. "Alcohol isopropílico", cantidad 2, 5,00 €). Aparece en la tabla en estado `pendiente`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/views/FormularioOtroPedidoView.fxml src/main/java/com/reparaciones/controllers/FormularioOtroPedidoController.java
git commit -m "feat(cliente): formulario Nuevo otro pedido"
```

---

### Task B6: Logs — registrar códigos `_OTRO` en el visor

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/LogController.java` (lista de acciones conocidas, ~líneas 43-47, y el mapa de etiquetas/iconos si existe)

**Interfaces:**
- Produces: las acciones `CREAR_PEDIDO_OTRO, EDITAR_PEDIDO_OTRO, CONFIRMAR_PEDIDO_OTRO, RECIBIR_PEDIDO_OTRO, RECIBIR_PARCIAL_OTRO, RECIBIR_RESTO_OTRO, CANCELAR_PEDIDO_OTRO, BORRAR_PEDIDO_OTRO` aparecen y se filtran correctamente en el visor de logs.

- [ ] **Step 1:** Añadir los 8 códigos `_OTRO` a la lista de acciones conocidas (junto a `"CREAR_PEDIDO", …`).

- [ ] **Step 2:** Localizar el mapa de presentación (etiqueta legible + icono/color) usado por los "logs enriquecidos" y añadir una entrada por cada código nuevo (etiquetas: "Crear pedido (otro)", "Recibir pedido (otro)", etc.). Si no existe tal mapa y se renderiza el código tal cual, omitir este paso.

- [ ] **Step 3: Verificación** — `mvn -q -o compile` + arrancar app: crear/confirmar/cancelar un "otro pedido" y comprobar en la vista de Logs que aparecen las nuevas acciones con su etiqueta y que el filtro por acción las incluye.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/reparaciones/controllers/LogController.java
git commit -m "feat(cliente): visor de logs reconoce acciones *_OTRO"
```

---

## Verificación final (manual, end-to-end)

- [ ] Con la BD migrada y servidor arrancado, recorrer el ciclo completo en "Otros": crear → confirmar → recepción parcial → recibir resto / cerrar sin resto → cancelar → revertir.
- [ ] En **cada** paso, abrir Stock → Stock actual y confirmar que el **stock de los componentes NO cambia** (es la garantía central).
- [ ] Borrado de proveedor con un "otro pedido" asociado → bloqueado.
- [ ] Logs muestran las acciones `_OTRO` con el usuario correcto.

## Cierre de la feature

- [ ] Merge `feat/pedidos-otros` → `main` (`--no-ff`), tras confirmación del usuario. Push tras confirmación.
- [ ] Desplegar: aplicar `sql/migracion-pedidos-otros.sql` en preproducción/producción **antes** de publicar el servidor nuevo.

---

## Self-Review (cobertura del spec)

- §3 tabla `Compra_otro` → Task A1 ✔ (DDL calcado, `ESTADO ENUM`, FK única a Proveedor).
- §4.1 modelo / §4.2 DAO / §4.3 controller → A2, A3, A4 ✔ (sin stock, mismos estados, mismos endpoints salvo en-camino/cantidad-en-camino).
- §4.4 `tienePedidos` → A5 ✔.
- §4.5 arranque Spring → verificación de A4 ✔.
- §5.1 modelo+DAO cliente / enum Estado → B1, B2 ✔.
- §5.2 toggle + tabla + filtros + menú + botón → B3, B4 ✔.
- §5.3 formulario un registro → B5 ✔.
- §5.4 roles UI → B4 (gating SUPERTECNICO) ✔.
- §6 máquina de estados → A3/A4 (transiciones) + B4 (menú) ✔.
- §7 logs `_OTRO` → A4 (emisión) + B6 (visor) ✔.
- §8 casos límite (409, proveedor, validación, parcial, revertir) → A3/A5/B5 ✔.
- §9 migración/despliegue → A1 + Cierre ✔.
- §10 testing → "Testing approach" + Verificación final ✔.
