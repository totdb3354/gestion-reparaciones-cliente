# F2a — Lotes y ciclo de vida del teléfono: Cimientos — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar la sub-fase F2a de la spec `docs/superpowers/specs/2026-07-07-fase2-lotes-telefonos-design.md` (§5): esquema BD (Lote + columnas de Telefono + Movimiento_telefono), importador xlsx con vista previa en el cliente, alta manual de lotes, y la vista IMEIs evolucionada a inventario completo con edición de atributos y filtros. Sin revisión todavía: solo estado RECIBIDO y derivación básica de ubicación.

**Architecture:** Cliente JavaFX parsea el xlsx (Apache POI) y muestra la vista previa; el servidor Spring Boot recibe el alta en bloque ya limpia (JSON) y persiste transaccionalmente (Lote + upsert de Telefono + Movimiento_telefono + Log_Actividad). La ubicación NUNCA se almacena: se deriva en UNA única función (`UbicacionDerivador`, servidor) a partir de estado + trabajos abiertos + cliente. La vista maestra del apartado "IMEIs" (AgrupadoController) pasa de agrupar trabajos (`GrupoImei`) a listar el inventario completo (`TelefonoInventario`, un teléfono por fila, incluidos históricos sin trabajos); el drill-down de trabajos por IMEI se conserva tal cual.

**Tech Stack:** Java 17 · JavaFX 21 · Apache POI 5.2.5 (nuevo, solo cliente) · Gson 2.10.1 · JUnit 5 + Mockito (cliente) · Spring Boot 3.3.4 + JdbcTemplate + Spring Security (servidor) · MariaDB.

## Restricciones globales

- **Dos repos git**: el repo raíz es `c:\Users\info\Documents\ProgramaReparaciones` (contiene `gestion-reparaciones-cliente`, `docs/`, `Apuntes/`); `gestion-reparaciones-servidor` es su PROPIO repo anidado (aparece como gitlink `M ../gestion-reparaciones-servidor` en el status del raíz). Cada commit va en el repo cuyo código toca; los commits de servidor se hacen con `cwd` en `gestion-reparaciones-servidor`.
- **Rama**: `feature/fase2a-lotes-telefonos` en AMBOS repos (raíz y servidor), partiendo de `main` (v0.14.0 taggeada, todo limpio).
- **Commits SIN `Co-Authored-By`** (regla del usuario). Mensajes estilo del historial: `feat(ámbito): descripción en español`.
- **Merge, push y tags: SOLO con OK explícito del usuario.** Este plan termina con las ramas locales listas; nada se mergea ni pushea.
- **Migraciones SQL: NUNCA ejecutarlas.** Se escriben en `gestion-reparaciones-servidor/sql/` y las aplica el usuario a mano en MariaDB (`gestion_reparaciones`).
- **Comandos por Bash** (regla del usuario), Maven incluido: cliente `cd gestion-reparaciones-cliente && mvn -q test`; servidor `cd gestion-reparaciones-servidor && mvn -q test` (y `mvn -q -DskipTests package` para compilar).
- Convenciones BD: tablas `PascalCase` / `Snake_case` (como `Reparacion_componente`), columnas `MAYÚSCULAS_CON_GUIONES_BAJOS`, concurrencia optimista vía `UPDATED_AT` (update con `WHERE ... AND UPDATED_AT = ?` → 0 filas ⇒ HTTP 409 ⇒ `StaleDataException` en cliente).
- Convenciones código: español en nombres/UI/javadoc; DAOs cliente = wrappers finos de `ApiClient`; DAOs servidor = `JdbcTemplate` con SQL a pelo; UI de diálogos construida en código (patrón `SelectorModeloDialog`/`PendientesSuperTecnicoController`).
- JSON cliente↔servidor: Gson (cliente) serializa por **nombre de campo**; Jackson (servidor) por **componente de record / getter**. Los nombres deben coincidir EXACTAMENTE (camelCase) — están fijados en cada tarea, no cambiarlos.
- Permisos F2a (spec §4): importar / editar atributos / equivalencias = `SUPERTECNICO` (con `@PreAuthorize` en servidor Y ocultación en UI); lectura de inventario y lotes = cualquier usuario autenticado.
- `REVISION_LOGISTICA` (check antiguo) NO se toca: se retira en F2c.
- El servidor no tiene ningún test previo ni test de contexto Spring: los cambios de wiring solo se validan arrancándolo (tarea 17). No introducir beans con dependencias circulares ni constructores nuevos raros.

## Contexto imprescindible (leído del código real)

**Plantilla xlsx** (analizada de `C:\Users\info\Downloads\Upload batch with IMEI.xlsx` y `...\Hy5 - 2026.07.06.xlsx`): primera hoja (nombre ruso "Лист1" — usar SIEMPRE `getSheetAt(0)`, nunca por nombre). Cabeceras en filas 5–6 (índices 4–5), datos desde la fila 7 (índice 6). Columnas por POSICIÓN FIJA (la propia plantilla prohíbe renombrarlas):

| Col | Índice | Contenido | Fila 5 / 6 |
|---|---|---|---|
| A | 0 | IMEI/SN (obligatorio) | `IMEI/SN *` (texto enriquecido, empieza por `IMEI/SN`) |
| B | 1 | Manufacturer (obligatorio) | fila 6 |
| C | 2 | Model (obligatorio), p. ej. `iPhone 12 mini` | fila 6 |
| D | 3 | Storage (GB, numérico: 128, 256…) | fila 6 |
| E | 4 | Color | fila 6 |
| F | 5 | Grade (proveedor, puede venir vacío) | fila 5 `Grade` |
| G | 6 | Purchase price (double con muchos decimales, p. ej. `152.24622210318043`) | fila 5 |
| H | 7 | Supplier Name (p. ej. `Hy5`) | fila 5 |
| I | 8 | Batch Number (p. ej. `1445947`; puede haber VARIOS en un fichero) | fila 5 `Batch Number` |
| J–M | 9–12 | Group of Goods / EAN / UPC / Description — SE IGNORAN | |
| N | 13 | Status (0–6; solo entran filas con 0) | fila 5 `Status` |

El fichero real Hy5 tiene 395 filas de datos (filas 7–401). Las celdas de IMEI/Storage/Status/Price pueden ser NUMÉRICAS o de TEXTO según cómo se generó el fichero: leer ambas variantes. Un IMEI de 15 dígitos cabe exacto en un double (< 2^53), así que `(long) cell.getNumericCellValue()` es seguro.

**Catálogo de modelos** (cliente): `FormularioReparacionController.MODELOS_ORDENADOS` (package-private, `controllers`) = códigos internos `"6s","6splus","7","7plus","8","8plus","se2020","x","xr","xs","xsmax","11","11pro","11promax","12","12mini","12pro","12promax","13","13mini","13pro","13promax","14","14plus","14pro","14promax","15","15plus","15pro","15promax","16","16e","16plus","16pro","16promax","17","air","17pro","17promax"`. `FormularioReparacionController.traducirModelo(interno)` → nombre comercial ("12mini" → "iPhone 12 Mini"). `Telefono.MODELO` guarda el código interno. Los textos tipo "iPhone 16 eSIM" NO tienen código interno → se resuelven a mano en la vista previa y la equivalencia se recuerda en BD.

**Prefijos de `Reparacion.ID_REP`** (una sola tabla para todo): `A…` asignación normal abierta/cerrada, `AG…` asignación glass, `AP…` asignación pulido; `R…` reparación hecha, `G…` glass hecho, `P…` pulido hecho. Trabajo ABIERTO = `FECHA_FIN IS NULL` en un ID `A%`. "Tiene asignaciones" (para bloquear el toggle de revisión, paridad con `TelefonoDAO.tieneAsignacionesActivas` del servidor) = abiertas de tipo normal o glass (`A%` no `AP%`).

**Vista IMEIs actual** = `AgrupadoView.fxml` + `AgrupadoController` (1315 líneas, compartido por los 3 roles vía `fx:include`; el host llama `configurar(Rol)`, `cargar()`, `resetarModo()`, `exportarCSV(Stage)`, `enDetalle()`, `volverAlMaestro()` — esa API pública NO puede cambiar de firma). Maestro = filas `GrupoImei` construidas en cliente agrupando `ReparacionResumen`; detalle = filas `ReparacionResumen` del IMEI. La tabla es `TableView<Object>` polimórfica y cada cellFactory hace `instanceof`.

**Utilidades existentes que se REUTILIZAN (no reinventar):** `ImeiUtils.parsearPegadoImeis` (pegado masivo), `FiltroImei` (filtro multi-IMEI), `MultiSelectDropdown.setup(...)` + `MultiSelectComboBox` (filtros desplegables), `SelectorModeloDialog.elegir(modeloActual)`, `SelectorClienteDialog.elegir(activos, idActual)`, `TipoCambioDAO.getTasa(divisa)` (conversión a EUR, patrón de `FormularioCompraController` con caché), `CsvExporter`, `FechaUtils`, `Alertas`, `ConfirmDialog`, `Colores`, `ApiClient` (get/getList/post/put/patch, 409→`StaleDataException`), `LogDAO.insertar(idUsu, accion, detalle)` (servidor), `Sesion.esSuperTecnico()`.

## Estructura de ficheros

**Servidor** (`gestion-reparaciones-servidor/`):
- Create: `sql/migracion-f2a-lotes.sql` · Modify: `sql/crear_bd.sql`
- Create: `src/main/java/com/reparaciones/servidor/service/UbicacionDerivador.java` (+ primer test del servidor: `src/test/java/com/reparaciones/servidor/service/UbicacionDerivadorTest.java`)
- Create: `model/Lote.java`, `model/TelefonoInventario.java`, `model/VerificacionImei.java`, `model/ImportacionRequest.java`
- Create: `dao/LoteDAO.java`, `dao/MovimientoTelefonoDAO.java`, `dao/EquivalenciaModeloDAO.java`
- Modify: `dao/TelefonoDAO.java` (inventario, verificación, upsert de importación, atributos)
- Create: `service/LoteImportService.java`
- Create: `controller/LoteController.java`, `controller/EquivalenciaModeloController.java`
- Modify: `controller/TelefonoController.java` (GET /inventario, PATCH /{imei}/atributos)

**Cliente** (`gestion-reparaciones-cliente/`):
- Modify: `pom.xml` (Apache POI)
- Create: `src/main/java/com/reparaciones/utils/LoteXlsxParser.java` + `src/test/java/com/reparaciones/utils/LoteXlsxParserTest.java`
- Create: `src/main/java/com/reparaciones/controllers/ModeloMapper.java` + `src/test/java/com/reparaciones/controllers/ModeloMapperTest.java`
- Create: `src/main/java/com/reparaciones/utils/ClasificadorImportacion.java` + `src/test/java/com/reparaciones/utils/ClasificadorImportacionTest.java`
- Create: `src/main/java/com/reparaciones/utils/UbicacionTexto.java` + `src/test/java/com/reparaciones/utils/UbicacionTextoTest.java`
- Create: `models/TelefonoInventario.java` (+ `src/test/java/com/reparaciones/models/TelefonoInventarioTest.java`), `models/Lote.java`, `models/VerificacionImei.java`, `models/Importacion.java`
- Create: `dao/LoteDAO.java`, `dao/EquivalenciaModeloDAO.java` · Modify: `dao/TelefonoDAO.java`
- Create: `controllers/ImportadorLoteDialog.java`, `controllers/AltaManualLoteDialog.java`
- Modify: `controllers/AgrupadoController.java`, `src/main/resources/views/AgrupadoView.fxml`, `controllers/LogController.java`
- Delete: `models/GrupoImei.java`, `src/test/java/com/reparaciones/models/GrupoImeiTest.java`

---

### Tarea 1: Ramas + migración SQL + crear_bd.sql

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-f2a-lotes.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql`

**Interfaces:**
- Produces: el esquema canónico que usan TODAS las tareas siguientes. Nombres definitivos (spec §8 pedía fijarlos): tabla `Lote`; columnas nuevas de `Telefono` = `ID_LOTE, ESTADO, STORAGE_GB, COLOR, GRADO_PROVEEDOR, GRADO_PROPIO, PRECIO_COMPRA, DIVISA, PRECIO_COMPRA_EUR, ES_DEVOLUCION`; tablas `Movimiento_telefono` y `Modelo_equivalencia`. Ubicaciones canónicas (VARCHAR): `ALMACEN, PARA_REVISAR, BLOQUEO, REPARACIONES, LISTOS, PEDIDOS, ENVIADO, DESGUACE`.

- [ ] **Paso 1: Crear las ramas en ambos repos**

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones" && git checkout -b feature/fase2a-lotes-telefonos
cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && git checkout -b feature/fase2a-lotes-telefonos
```

Esperado: `Switched to a new branch 'feature/fase2a-lotes-telefonos'` en los dos.

- [ ] **Paso 2: Escribir `sql/migracion-f2a-lotes.sql`**

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2a-lotes.sql — Fase 2a: Lote, ciclo de vida de Telefono, movimientos
-- La aplica el usuario a mano en gestion_reparaciones. Idempotencia no requerida
-- (mismo criterio que las migraciones anteriores); relanzar = error de "ya existe".
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Lote de compra: todos los teléfonos entran por lote (grande o pequeño).
CREATE TABLE Lote (
    ID_LOTE      INT          NOT NULL AUTO_INCREMENT,
    BATCH_NUMBER VARCHAR(100) NOT NULL,
    ID_PROV      INT          NOT NULL,
    FECHA_IMPORT DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         TEXT,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_LOTE),
    UNIQUE KEY uq_lote_batch_prov (BATCH_NUMBER, ID_PROV),
    CONSTRAINT fk_lote_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

-- Ciclo de vida del teléfono. EN_REPARACION no se almacena: es implícito
-- (tiene trabajo abierto) y lo deriva UbicacionDerivador. ESTADO NULL = histórico
-- pre-fase, fuera del ciclo. GRADO_PROPIO = grado del chasis (escala propia).
ALTER TABLE Telefono
    ADD COLUMN ID_LOTE           INT NULL AFTER ID_CLI,
    ADD COLUMN ESTADO            ENUM('RECIBIDO','EN_REVISION','BLOQUEADO','OK','ENVIADO','DESGUACE') NULL AFTER ID_LOTE,
    ADD COLUMN STORAGE_GB        INT NULL AFTER ESTADO,
    ADD COLUMN COLOR             VARCHAR(50) NULL AFTER STORAGE_GB,
    ADD COLUMN GRADO_PROVEEDOR   VARCHAR(20) NULL AFTER COLOR,
    ADD COLUMN GRADO_PROPIO      ENUM('C','B','A-','A','A+') NULL AFTER GRADO_PROVEEDOR,
    ADD COLUMN PRECIO_COMPRA     DECIMAL(10,2) NULL AFTER GRADO_PROPIO,
    ADD COLUMN DIVISA            VARCHAR(3) NULL AFTER PRECIO_COMPRA,
    ADD COLUMN PRECIO_COMPRA_EUR DECIMAL(10,2) NULL AFTER DIVISA,
    ADD COLUMN ES_DEVOLUCION     BOOLEAN NOT NULL DEFAULT FALSE AFTER PRECIO_COMPRA_EUR,
    ADD CONSTRAINT fk_telefono_lote FOREIGN KEY (ID_LOTE) REFERENCES Lote (ID_LOTE);

-- Trazabilidad append-only de ubicaciones (diseño de la spec previa del usuario).
-- Ubicaciones canónicas: ALMACEN, PARA_REVISAR, BLOQUEO, REPARACIONES, LISTOS,
-- PEDIDOS, ENVIADO, DESGUACE. ORIGEN NULL = entrada al sistema.
CREATE TABLE Movimiento_telefono (
    ID_MOV            INT          NOT NULL AUTO_INCREMENT,
    IMEI              VARCHAR(15)  NOT NULL,
    UBICACION_ORIGEN  VARCHAR(30)  NULL,
    UBICACION_DESTINO VARCHAR(30)  NOT NULL,
    FECHA             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ID_USU            INT          NOT NULL,
    MOTIVO            VARCHAR(255) NULL,
    REFERENCIA        VARCHAR(100) NULL,
    PRIMARY KEY (ID_MOV),
    KEY idx_mov_imei (IMEI),
    CONSTRAINT fk_mov_telefono FOREIGN KEY (IMEI)   REFERENCES Telefono (IMEI),
    CONSTRAINT fk_mov_usuario  FOREIGN KEY (ID_USU) REFERENCES Usuario  (ID_USU)
);

-- Equivalencias de modelo recordadas por el importador ("iphone16esim" → "16").
-- TEXTO_EXTERNO se guarda NORMALIZADO (minúsculas, solo [a-z0-9], sin prefijo "iphone").
CREATE TABLE Modelo_equivalencia (
    TEXTO_EXTERNO  VARCHAR(100) NOT NULL,
    MODELO_INTERNO VARCHAR(100) NOT NULL,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (TEXTO_EXTERNO)
);
```

- [ ] **Paso 3: Actualizar `sql/crear_bd.sql`** (script de reset completo — debe seguir siendo canónico):
  1. En el bloque de `DROP TABLE IF EXISTS` añadir, ANTES de `DROP TABLE IF EXISTS Telefono;`: `DROP TABLE IF EXISTS Movimiento_telefono;`, `DROP TABLE IF EXISTS Modelo_equivalencia;`, `DROP TABLE IF EXISTS Lote;`.
  2. En `CREATE TABLE Telefono` añadir las 10 columnas nuevas con la misma definición del Paso 2 (entre `ID_CLI` y `REVISION_LOGISTICA`), SIN la FK a Lote todavía.
  3. Tras `CREATE TABLE Proveedor` (línea ~119) añadir el `CREATE TABLE Lote` completo del Paso 2, y después `ALTER TABLE Telefono ADD CONSTRAINT fk_telefono_lote FOREIGN KEY (ID_LOTE) REFERENCES Lote (ID_LOTE);` (Lote requiere Proveedor, que se crea después que Telefono — por eso la FK va aparte).
  4. Tras `CREATE TABLE Log_Actividad` añadir los `CREATE TABLE Movimiento_telefono` y `CREATE TABLE Modelo_equivalencia` completos del Paso 2 (Movimiento_telefono requiere Usuario).

- [ ] **Paso 4: Revisar contra la spec §2.3 y commit (repo SERVIDOR)**

Verifica: todos los campos de §2.3 tienen columna (ID_LOTE ✓, ESTADO ✓, STORAGE ✓ como STORAGE_GB, COLOR ✓, GRADO_PROVEEDOR ✓, GRADO_PROPIO ✓, PRECIO_COMPRA+DIVISA+PRECIO_COMPRA_EUR ✓, ES_DEVOLUCION ✓; Movimiento con IMEI/origen/destino/fecha/usuario/motivo/referencia ✓). La tabla `Revision` es F2b: NO va aquí.

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && git add sql/ && git commit -m "feat(sql): esquema F2a — Lote, ciclo de vida de Telefono, Movimiento_telefono y Modelo_equivalencia"
```

---

### Tarea 2: Servidor — `UbicacionDerivador` (la ÚNICA función de derivación) + primer test del servidor

**Files:**
- Create: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/UbicacionDerivador.java`
- Test: `gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/service/UbicacionDerivadorTest.java`

**Interfaces:**
- Produces: `UbicacionDerivador.derivar(String estado, int pulAbiertos, int glassAbiertos, int normalAbiertos, Integer idCli)` → `UbicacionDerivador.Resultado(String estadoEfectivo, String ubicacion, List<String> subUbicaciones)`. Valores de `ubicacion`: `ALMACEN | PARA_REVISAR | BLOQUEO | REPARACIONES | LISTOS | PEDIDOS | null` (null = fuera del ciclo o enviado/desguace). `subUbicaciones` ⊆ {`PULIDO`,`GLASS`,`NORMAL`} solo cuando ubicacion=REPARACIONES. La usa la Tarea 4 (inventario). El punto de enchufe de la futura entidad Pedido es el caso `OK` + `idCli`.

- [ ] **Paso 1: Escribir el test que falla** (es el PRIMER test del servidor: crea el árbol `src/test/java/...`)

```java
package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UbicacionDerivadorTest {

    @Test void recibidoSinTrabajosEstaEnAlmacen() {
        var r = UbicacionDerivador.derivar("RECIBIDO", 0, 0, 0, null);
        assertEquals("RECIBIDO", r.estadoEfectivo());
        assertEquals("ALMACEN", r.ubicacion());
        assertTrue(r.subUbicaciones().isEmpty());
    }

    @Test void cualquierTrabajoAbiertoMandaAReparaciones() {
        var r = UbicacionDerivador.derivar("RECIBIDO", 0, 0, 1, null);
        assertEquals("EN_REPARACION", r.estadoEfectivo());
        assertEquals("REPARACIONES", r.ubicacion());
        assertEquals(List.of("NORMAL"), r.subUbicaciones());
    }

    @Test void telefonoDivididoPantallaYCuerpo() {
        // pulido + glass + normal abiertos: la parte pantalla muestra el PRIMER
        // trabajo de pantalla (pulido antes que glass), la parte cuerpo el normal
        var r = UbicacionDerivador.derivar("RECIBIDO", 1, 1, 1, null);
        assertEquals(List.of("PULIDO", "NORMAL"), r.subUbicaciones());
    }

    @Test void alCompletarPulidoSaltaAGlass() {
        var r = UbicacionDerivador.derivar("RECIBIDO", 0, 1, 1, null);
        assertEquals(List.of("GLASS", "NORMAL"), r.subUbicaciones());
    }

    @Test void soloTrabajoDePantalla() {
        var r = UbicacionDerivador.derivar(null, 1, 0, 0, null);
        assertEquals("EN_REPARACION", r.estadoEfectivo());
        assertEquals(List.of("PULIDO"), r.subUbicaciones());
    }

    @Test void historicoSinTrabajosEstaFueraDelCiclo() {
        var r = UbicacionDerivador.derivar(null, 0, 0, 0, null);
        assertNull(r.estadoEfectivo());
        assertNull(r.ubicacion());
    }

    @Test void bloqueadoMandaSobreTrabajos() {
        var r = UbicacionDerivador.derivar("BLOQUEADO", 0, 0, 1, null);
        assertEquals("BLOQUEADO", r.estadoEfectivo());
        assertEquals("BLOQUEO", r.ubicacion());
    }

    @Test void enviadoYDesguaceEstanFuera() {
        assertNull(UbicacionDerivador.derivar("ENVIADO", 0, 0, 0, null).ubicacion());
        assertNull(UbicacionDerivador.derivar("DESGUACE", 0, 0, 0, null).ubicacion());
    }

    @Test void enRevisionVaAParaRevisar() {
        assertEquals("PARA_REVISAR", UbicacionDerivador.derivar("EN_REVISION", 0, 0, 0, null).ubicacion());
    }

    @Test void okConClienteEsPedidoYSinClienteEsListo() {
        // "Es pedido" = tiene cliente asignado. Punto de enchufe de la futura entidad Pedido.
        assertEquals("PEDIDOS", UbicacionDerivador.derivar("OK", 0, 0, 0, 7).ubicacion());
        assertEquals("LISTOS",  UbicacionDerivador.derivar("OK", 0, 0, 0, null).ubicacion());
    }

    @Test void okConTrabajoNuevoVuelveSoloAReparaciones() {
        var r = UbicacionDerivador.derivar("OK", 1, 0, 0, null);
        assertEquals("EN_REPARACION", r.estadoEfectivo());
        assertEquals("REPARACIONES", r.ubicacion());
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que falla**

Run: `cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q test`
Esperado: error de compilación `cannot find symbol: UbicacionDerivador`.

- [ ] **Paso 3: Implementación mínima**

```java
package com.reparaciones.servidor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * LA función de derivación de ubicación (spec F2 §2.2): la ubicación nunca se
 * almacena ni se mantiene a mano, se deriva de estado + trabajos abiertos + cliente.
 * <p>Prioridades: BLOQUEADO/ENVIADO/DESGUACE mandan sobre todo; después cualquier
 * trabajo abierto ⇒ REPARACIONES (estado efectivo EN_REPARACION, implícito);
 * después el estado almacenado. "Es pedido" = tiene cliente asignado (ID_CLI):
 * cuando exista la entidad Pedido se enchufa SOLO aquí.</p>
 */
public final class UbicacionDerivador {

    private UbicacionDerivador() {}

    public record Resultado(String estadoEfectivo, String ubicacion, List<String> subUbicaciones) {}

    public static Resultado derivar(String estado, int pulAbiertos, int glassAbiertos,
                                    int normalAbiertos, Integer idCli) {
        if ("BLOQUEADO".equals(estado)) return new Resultado("BLOQUEADO", "BLOQUEO", List.of());
        if ("ENVIADO".equals(estado))   return new Resultado("ENVIADO", null, List.of());
        if ("DESGUACE".equals(estado))  return new Resultado("DESGUACE", null, List.of());

        if (pulAbiertos + glassAbiertos + normalAbiertos > 0) {
            List<String> subs = new ArrayList<>(2);
            if      (pulAbiertos   > 0) subs.add("PULIDO");   // parte pantalla: pulido antes que glass
            else if (glassAbiertos > 0) subs.add("GLASS");
            if (normalAbiertos > 0) subs.add("NORMAL");        // parte cuerpo
            return new Resultado("EN_REPARACION", "REPARACIONES", List.copyOf(subs));
        }
        if (estado == null) return new Resultado(null, null, List.of()); // histórico fuera del ciclo
        return switch (estado) {
            case "RECIBIDO"    -> new Resultado("RECIBIDO", "ALMACEN", List.of());
            case "EN_REVISION" -> new Resultado("EN_REVISION", "PARA_REVISAR", List.of());
            case "OK"          -> new Resultado("OK", idCli != null ? "PEDIDOS" : "LISTOS", List.of());
            default            -> new Resultado(estado, null, List.of());
        };
    }
}
```

- [ ] **Paso 4: Ejecutar y ver que pasa**

Run: `mvn -q test` (en el repo servidor). Esperado: `Tests run: 11, Failures: 0`.

- [ ] **Paso 5: Commit (repo SERVIDOR)**

```bash
git add src/main/java/com/reparaciones/servidor/service/UbicacionDerivador.java src/test && git commit -m "feat(servidor): UbicacionDerivador — derivación única de ubicación y estado efectivo (primer test del servidor)"
```

---

### Tarea 3: Servidor — Lote, movimientos y `GET /api/lotes`

**Files:**
- Create: `model/Lote.java`, `dao/LoteDAO.java`, `dao/MovimientoTelefonoDAO.java`, `controller/LoteController.java` (bajo `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/`)

**Interfaces:**
- Produces (JSON de `GET /api/lotes`, lo consume el cliente en Tarea 11): lista de `{idLote:int, batchNumber:String, idProv:int, proveedor:String, fechaImport:LocalDateTime, nota:String|null, numTelefonos:int, updatedAt:LocalDateTime}` ordenada por fecha desc.
- Produces (para Tarea 6): `LoteDAO.obtenerOCrear(String batchNumber, int idProv, String nota)` → `int idLote` (reutiliza si ya existe ese batch+proveedor); `MovimientoTelefonoDAO.insertar(String imei, String origen, String destino, int idUsu, String motivo, String referencia)`.

- [ ] **Paso 1: `model/Lote.java`**

```java
package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Lote {
    private int idLote;
    private String batchNumber;
    private int idProv;
    private String proveedor;
    private LocalDateTime fechaImport;
    private String nota;
    private int numTelefonos;
    private LocalDateTime updatedAt;

    public Lote() {}

    public Lote(int idLote, String batchNumber, int idProv, String proveedor,
                LocalDateTime fechaImport, String nota, int numTelefonos, LocalDateTime updatedAt) {
        this.idLote = idLote; this.batchNumber = batchNumber; this.idProv = idProv;
        this.proveedor = proveedor; this.fechaImport = fechaImport; this.nota = nota;
        this.numTelefonos = numTelefonos; this.updatedAt = updatedAt;
    }

    public int getIdLote()               { return idLote; }
    public String getBatchNumber()       { return batchNumber; }
    public int getIdProv()               { return idProv; }
    public String getProveedor()         { return proveedor; }
    public LocalDateTime getFechaImport(){ return fechaImport; }
    public String getNota()              { return nota; }
    public int getNumTelefonos()         { return numTelefonos; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }
}
```

- [ ] **Paso 2: `dao/LoteDAO.java`**

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Lote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;

@Repository
public class LoteDAO {

    private final JdbcTemplate jdbc;

    public LoteDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Lote> getAll() {
        return jdbc.query(
            "SELECT l.ID_LOTE, l.BATCH_NUMBER, l.ID_PROV, p.NOMBRE AS PROVEEDOR, l.FECHA_IMPORT," +
            "       l.NOTA, l.UPDATED_AT, COUNT(t.IMEI) AS NUM_TELEFONOS" +
            " FROM Lote l" +
            " JOIN Proveedor p ON p.ID_PROV = l.ID_PROV" +
            " LEFT JOIN Telefono t ON t.ID_LOTE = l.ID_LOTE" +
            " GROUP BY l.ID_LOTE, l.BATCH_NUMBER, l.ID_PROV, p.NOMBRE, l.FECHA_IMPORT, l.NOTA, l.UPDATED_AT" +
            " ORDER BY l.FECHA_IMPORT DESC",
            (rs, row) -> new Lote(
                rs.getInt("ID_LOTE"), rs.getString("BATCH_NUMBER"), rs.getInt("ID_PROV"),
                rs.getString("PROVEEDOR"), rs.getTimestamp("FECHA_IMPORT").toLocalDateTime(),
                rs.getString("NOTA"), rs.getInt("NUM_TELEFONOS"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()));
    }

    /** Devuelve el ID del lote batch+proveedor, creándolo si no existe (re-importaciones del mismo batch reutilizan el lote). */
    public int obtenerOCrear(String batchNumber, int idProv, String nota) {
        List<Integer> existente = jdbc.query(
            "SELECT ID_LOTE FROM Lote WHERE BATCH_NUMBER = ? AND ID_PROV = ?",
            (rs, row) -> rs.getInt("ID_LOTE"), batchNumber, idProv);
        if (!existente.isEmpty()) return existente.get(0);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO Lote (BATCH_NUMBER, ID_PROV, NOTA) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, batchNumber);
            ps.setInt(2, idProv);
            ps.setString(3, nota);
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }
}
```

- [ ] **Paso 3: `dao/MovimientoTelefonoDAO.java`**

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Trazabilidad append-only de ubicaciones (spec F2 §2.2). Solo inserciones; la consulta llega en F2c. */
@Repository
public class MovimientoTelefonoDAO {

    private final JdbcTemplate jdbc;

    public MovimientoTelefonoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insertar(String imei, String origen, String destino, int idUsu, String motivo, String referencia) {
        jdbc.update(
            "INSERT INTO Movimiento_telefono (IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, ID_USU, MOTIVO, REFERENCIA)" +
            " VALUES (?, ?, ?, ?, ?, ?)",
            imei, origen, destino, idUsu, motivo, referencia);
    }
}
```

- [ ] **Paso 4: `controller/LoteController.java`** (solo GET por ahora; verificar/importar se añaden en Tareas 5–6)

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LoteDAO;
import com.reparaciones.servidor.model.Lote;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteDAO loteDao;

    public LoteController(LoteDAO loteDao) { this.loteDao = loteDao; }

    @GetMapping
    public List<Lote> getAll() { return loteDao.getAll(); }
}
```

- [ ] **Paso 5: Compilar + tests y commit (repo SERVIDOR)**

Run: `mvn -q test` → `Tests run: 11` y BUILD SUCCESS.

```bash
git add src/main/java && git commit -m "feat(servidor): entidad Lote (listado con conteo) y DAO de movimientos de teléfono"
```

---

### Tarea 4: Servidor — inventario de teléfonos (`GET /api/telefonos/inventario`)

**Files:**
- Create: `model/TelefonoInventario.java`
- Modify: `dao/TelefonoDAO.java` (añadir `getInventario()`)
- Modify: `controller/TelefonoController.java` (añadir endpoint)

**Interfaces:**
- Consumes: `UbicacionDerivador.derivar(...)` (Tarea 2).
- Produces (JSON, lo consume el cliente en Tareas 11 y 14 — nombres EXACTOS): `{imei, modelo, storageGb, color, gradoProveedor, gradoPropio, estado, estadoEfectivo, ubicacion, subUbicaciones:[String], esDevolucion:boolean, observacion, idCli:Integer|null, cliente:String|null, idLote:Integer|null, batchNumber, proveedor, revisionLogistica:boolean, telefonoUpdatedAt:LocalDateTime, repHechas:int, glassHechas:int, pulHechos:int, pulAbiertos:int, glassAbiertos:int, normalAbiertos:int, incAbiertas:int, solicitudesPendientes:int, ultimaActividad:LocalDateTime|null}`.

- [ ] **Paso 1: `model/TelefonoInventario.java`** — POJO con esos 27 campos, constructor completo y getters (mismo estilo que `Lote`). `subUbicaciones` es `List<String>`. `ultimaActividad` = max(último trabajo, fecha import del lote), calculado en el mapper del DAO.

```java
package com.reparaciones.servidor.model;

import java.time.LocalDateTime;
import java.util.List;

/** Fila del inventario completo de teléfonos (vista IMEIs evolucionada, F2a). */
public class TelefonoInventario {
    private String imei;
    private String modelo;
    private Integer storageGb;
    private String color;
    private String gradoProveedor;
    private String gradoPropio;
    private String estado;           // almacenado (null = histórico)
    private String estadoEfectivo;   // derivado (EN_REPARACION si trabajo abierto)
    private String ubicacion;        // derivada; null = fuera del ciclo
    private List<String> subUbicaciones;
    private boolean esDevolucion;
    private String observacion;
    private Integer idCli;
    private String cliente;
    private Integer idLote;
    private String batchNumber;
    private String proveedor;
    private boolean revisionLogistica;
    private LocalDateTime telefonoUpdatedAt;
    private int repHechas;
    private int glassHechas;
    private int pulHechos;
    private int pulAbiertos;
    private int glassAbiertos;
    private int normalAbiertos;
    private int incAbiertas;
    private int solicitudesPendientes;
    private LocalDateTime ultimaActividad;

    public TelefonoInventario() {}

    // Getters + setters de TODOS los campos (Jackson serializa por getter).
    // Generarlos completos, nombres getImei()/setImei(...) etc.
}
```

(El comentario final del bloque es instrucción para el implementador: escribir los getters/setters completos, no dejar el comentario.)

- [ ] **Paso 2: `TelefonoDAO.getInventario()`** — añadir al DAO servidor existente:

```java
public List<com.reparaciones.servidor.model.TelefonoInventario> getInventario() {
    String sql =
        "SELECT t.IMEI, t.MODELO, t.STORAGE_GB, t.COLOR, t.GRADO_PROVEEDOR, t.GRADO_PROPIO," +
        "       t.ESTADO, t.ES_DEVOLUCION, t.OBSERVACION, t.REVISION_LOGISTICA, t.UPDATED_AT," +
        "       t.ID_CLI, c.NOMBRE AS CLIENTE, t.ID_LOTE, l.BATCH_NUMBER, l.FECHA_IMPORT, p.NOMBRE AS PROVEEDOR," +
        "       COALESCE(w.PUL_ABIERTOS,0) PUL_ABIERTOS, COALESCE(w.GLASS_ABIERTOS,0) GLASS_ABIERTOS," +
        "       COALESCE(w.NORMAL_ABIERTOS,0) NORMAL_ABIERTOS, COALESCE(w.REP_HECHAS,0) REP_HECHAS," +
        "       COALESCE(w.GLASS_HECHAS,0) GLASS_HECHAS, COALESCE(w.PUL_HECHOS,0) PUL_HECHOS," +
        "       w.ULTIMO_TRABAJO," +
        "       COALESCE(i.INC_ABIERTAS,0) INC_ABIERTAS, COALESCE(s.SOL_PENDIENTES,0) SOL_PENDIENTES" +
        " FROM Telefono t" +
        " LEFT JOIN Cliente c   ON c.ID_CLI  = t.ID_CLI" +
        " LEFT JOIN Lote l      ON l.ID_LOTE = t.ID_LOTE" +
        " LEFT JOIN Proveedor p ON p.ID_PROV = l.ID_PROV" +
        " LEFT JOIN (SELECT r.IMEI," +
        "        SUM(r.ID_REP LIKE 'AP%' AND r.FECHA_FIN IS NULL) AS PUL_ABIERTOS," +
        "        SUM(r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL) AS GLASS_ABIERTOS," +
        "        SUM(r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.ID_REP NOT LIKE 'AG%'" +
        "            AND r.FECHA_FIN IS NULL) AS NORMAL_ABIERTOS," +
        "        SUM(r.ID_REP LIKE 'R%') AS REP_HECHAS," +
        "        SUM(r.ID_REP LIKE 'G%') AS GLASS_HECHAS," +
        "        SUM(r.ID_REP LIKE 'P%') AS PUL_HECHOS," +
        "        MAX(COALESCE(r.FECHA_FIN, r.FECHA_ASIG)) AS ULTIMO_TRABAJO" +
        "    FROM Reparacion r GROUP BY r.IMEI) w ON w.IMEI = t.IMEI" +
        " LEFT JOIN (SELECT r2.IMEI, COUNT(*) AS INC_ABIERTAS" +
        "    FROM Reparacion_componente rc JOIN Reparacion r2 ON r2.ID_REP = rc.ID_REP" +
        "    WHERE rc.ES_INCIDENCIA AND NOT rc.ES_RESUELTO GROUP BY r2.IMEI) i ON i.IMEI = t.IMEI" +
        " LEFT JOIN (SELECT r3.IMEI, COUNT(*) AS SOL_PENDIENTES" +
        "    FROM Reparacion_componente rc2 JOIN Reparacion r3 ON r3.ID_REP = rc2.ID_REP" +
        "    WHERE rc2.ES_SOLICITUD AND rc2.ESTADO_SOLICITUD = 'PENDIENTE' AND r3.FECHA_FIN IS NULL" +
        "    GROUP BY r3.IMEI) s ON s.IMEI = t.IMEI";
    return jdbc.query(sql, (rs, row) -> {
        var inv = new com.reparaciones.servidor.model.TelefonoInventario();
        inv.setImei(rs.getString("IMEI"));
        inv.setModelo(rs.getString("MODELO"));
        inv.setStorageGb((Integer) rs.getObject("STORAGE_GB"));
        inv.setColor(rs.getString("COLOR"));
        inv.setGradoProveedor(rs.getString("GRADO_PROVEEDOR"));
        inv.setGradoPropio(rs.getString("GRADO_PROPIO"));
        inv.setEstado(rs.getString("ESTADO"));
        inv.setEsDevolucion(rs.getBoolean("ES_DEVOLUCION"));
        inv.setObservacion(rs.getString("OBSERVACION"));
        inv.setRevisionLogistica(rs.getBoolean("REVISION_LOGISTICA"));
        inv.setTelefonoUpdatedAt(rs.getTimestamp("UPDATED_AT").toLocalDateTime());
        inv.setIdCli((Integer) rs.getObject("ID_CLI"));
        inv.setCliente(rs.getString("CLIENTE"));
        inv.setIdLote((Integer) rs.getObject("ID_LOTE"));
        inv.setBatchNumber(rs.getString("BATCH_NUMBER"));
        inv.setProveedor(rs.getString("PROVEEDOR"));
        inv.setPulAbiertos(rs.getInt("PUL_ABIERTOS"));
        inv.setGlassAbiertos(rs.getInt("GLASS_ABIERTOS"));
        inv.setNormalAbiertos(rs.getInt("NORMAL_ABIERTOS"));
        inv.setRepHechas(rs.getInt("REP_HECHAS"));
        inv.setGlassHechas(rs.getInt("GLASS_HECHAS"));
        inv.setPulHechos(rs.getInt("PUL_HECHOS"));
        inv.setIncAbiertas(rs.getInt("INC_ABIERTAS"));
        inv.setSolicitudesPendientes(rs.getInt("SOL_PENDIENTES"));
        java.sql.Timestamp ultimoTrabajo = rs.getTimestamp("ULTIMO_TRABAJO");
        java.sql.Timestamp fechaImport   = rs.getTimestamp("FECHA_IMPORT");
        java.time.LocalDateTime ultima = null;
        if (ultimoTrabajo != null) ultima = ultimoTrabajo.toLocalDateTime();
        if (fechaImport != null && (ultima == null || fechaImport.toLocalDateTime().isAfter(ultima)))
            ultima = fechaImport.toLocalDateTime();
        inv.setUltimaActividad(ultima);
        var d = com.reparaciones.servidor.service.UbicacionDerivador.derivar(
                inv.getEstado(), inv.getPulAbiertos(), inv.getGlassAbiertos(),
                inv.getNormalAbiertos(), inv.getIdCli());
        inv.setEstadoEfectivo(d.estadoEfectivo());
        inv.setUbicacion(d.ubicacion());
        inv.setSubUbicaciones(d.subUbicaciones());
        return inv;
    });
}
```

- [ ] **Paso 3: Endpoint en `TelefonoController`** (cualquier autenticado; los técnicos tienen consulta):

```java
@GetMapping("/inventario")
public List<com.reparaciones.servidor.model.TelefonoInventario> getInventario() {
    return dao.getInventario();
}
```

- [ ] **Paso 4: Compilar + tests y commit (repo SERVIDOR)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add src/main/java && git commit -m "feat(servidor): inventario completo de teléfonos con ubicación derivada (GET /api/telefonos/inventario)"
```

---

### Tarea 5: Servidor — verificación de IMEIs para la vista previa (`POST /api/lotes/verificar`)

**Files:**
- Create: `model/VerificacionImei.java`
- Modify: `dao/TelefonoDAO.java` (añadir `verificar(List<String>)`)
- Modify: `controller/LoteController.java`

**Interfaces:**
- Produces (JSON, consume Tarea 10/11): request `{"imeis": ["123...", ...]}` → response lista de `{imei, existe:boolean, estado:String|null, trabajosAbiertos:int, modelo:String|null}`. Solo devuelve entradas para IMEIs que EXISTEN (el cliente asume NUEVO para los ausentes).

- [ ] **Paso 1: `model/VerificacionImei.java`**

```java
package com.reparaciones.servidor.model;

/** Hechos crudos de un IMEI existente para que el importador clasifique duplicados. */
public class VerificacionImei {
    private String imei;
    private boolean existe;
    private String estado;         // null = histórico
    private int trabajosAbiertos;  // asignaciones sin FECHA_FIN (A%, AG%, AP%)
    private String modelo;

    public VerificacionImei() {}

    public VerificacionImei(String imei, boolean existe, String estado, int trabajosAbiertos, String modelo) {
        this.imei = imei; this.existe = existe; this.estado = estado;
        this.trabajosAbiertos = trabajosAbiertos; this.modelo = modelo;
    }

    public String getImei()          { return imei; }
    public boolean isExiste()        { return existe; }
    public String getEstado()        { return estado; }
    public int getTrabajosAbiertos() { return trabajosAbiertos; }
    public String getModelo()        { return modelo; }
}
```

- [ ] **Paso 2: `TelefonoDAO.verificar(...)`**

```java
public List<com.reparaciones.servidor.model.VerificacionImei> verificar(List<String> imeis) {
    if (imeis == null || imeis.isEmpty()) return List.of();
    String placeholders = String.join(",", java.util.Collections.nCopies(imeis.size(), "?"));
    String sql =
        "SELECT t.IMEI, t.ESTADO, t.MODELO, COALESCE(w.ABIERTOS,0) AS ABIERTOS" +
        " FROM Telefono t" +
        " LEFT JOIN (SELECT IMEI, COUNT(*) AS ABIERTOS FROM Reparacion" +
        "            WHERE ID_REP LIKE 'A%' AND FECHA_FIN IS NULL GROUP BY IMEI) w ON w.IMEI = t.IMEI" +
        " WHERE t.IMEI IN (" + placeholders + ")";
    return jdbc.query(sql, (rs, row) -> new com.reparaciones.servidor.model.VerificacionImei(
            rs.getString("IMEI"), true, rs.getString("ESTADO"),
            rs.getInt("ABIERTOS"), rs.getString("MODELO")),
        imeis.toArray());
}
```

- [ ] **Paso 3: Endpoint en `LoteController`**

```java
@PostMapping("/verificar")
public List<com.reparaciones.servidor.model.VerificacionImei> verificar(@RequestBody VerificarRequest req) {
    return telefonoDao.verificar(req.imeis());
}

public record VerificarRequest(List<String> imeis) {}
```

Añadir `private final TelefonoDAO telefonoDao;` al constructor de `LoteController`.

- [ ] **Paso 4: Compilar + tests + commit (repo SERVIDOR)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add src/main/java && git commit -m "feat(servidor): verificación de IMEIs para la vista previa del importador"
```

---

### Tarea 6: Servidor — importación transaccional (`POST /api/lotes/importar`)

**Files:**
- Create: `model/ImportacionRequest.java`, `service/LoteImportService.java`
- Modify: `dao/TelefonoDAO.java` (upsert de importación), `controller/LoteController.java`

**Interfaces:**
- Consumes: `LoteDAO.obtenerOCrear`, `MovimientoTelefonoDAO.insertar`, `TelefonoDAO.verificar`, `LogDAO.insertar`.
- Produces (JSON, consume Tarea 11 — nombres EXACTOS): request `{"lotes":[{"batchNumber","idProv":int,"nota":String|null,"telefonos":[{"imei","modelo","storageGb":Integer|null,"color","gradoProveedor","precioCompra":BigDecimal|null,"divisa","precioCompraEur":BigDecimal|null}]}]}` → response `{"lotes":int,"telefonos":int,"conflictosOmitidos":[String]}`. Log de actividad: acción `IMPORTAR_LOTE` por lote. Movimiento por teléfono: `NULL → ALMACEN`.

- [ ] **Paso 1: `model/ImportacionRequest.java`**

```java
package com.reparaciones.servidor.model;

import java.math.BigDecimal;
import java.util.List;

/** Alta en bloque del importador. El servidor es agnóstico del parser (spec §3). */
public record ImportacionRequest(List<LoteImport> lotes) {

    public record LoteImport(String batchNumber, int idProv, String nota, List<TelefonoImport> telefonos) {}

    public record TelefonoImport(String imei, String modelo, Integer storageGb, String color,
                                 String gradoProveedor, BigDecimal precioCompra, String divisa,
                                 BigDecimal precioCompraEur) {}

    public record Respuesta(int lotes, int telefonos, List<String> conflictosOmitidos) {}
}
```

- [ ] **Paso 2: upsert en `TelefonoDAO` (servidor)** — nuevo/re-entrada en una sola sentencia; conserva OBSERVACION, ID_CLI, GRADO_PROPIO y ES_DEVOLUCION del teléfono existente (re-entrada legítima conserva su historial, spec §3):

```java
/** Alta/re-entrada de un teléfono de lote: fija lote, atributos del fichero y ESTADO=RECIBIDO. */
public void upsertImportacion(String imei, String modelo, Integer idLote, Integer storageGb,
                              String color, String gradoProveedor,
                              java.math.BigDecimal precioCompra, String divisa,
                              java.math.BigDecimal precioCompraEur) {
    jdbc.update(
        "INSERT INTO Telefono (IMEI, MODELO, ID_LOTE, ESTADO, STORAGE_GB, COLOR, GRADO_PROVEEDOR," +
        "                      PRECIO_COMPRA, DIVISA, PRECIO_COMPRA_EUR)" +
        " VALUES (?, ?, ?, 'RECIBIDO', ?, ?, ?, ?, ?, ?)" +
        " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_LOTE = ?, ESTADO = 'RECIBIDO'," +
        "  STORAGE_GB = ?, COLOR = ?, GRADO_PROVEEDOR = ?, PRECIO_COMPRA = ?, DIVISA = ?, PRECIO_COMPRA_EUR = ?",
        imei, modelo, idLote, storageGb, color, gradoProveedor, precioCompra, divisa, precioCompraEur,
        modelo, idLote, storageGb, color, gradoProveedor, precioCompra, divisa, precioCompraEur);
}
```

- [ ] **Paso 3: `service/LoteImportService.java`**

```java
package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.LoteDAO;
import com.reparaciones.servidor.dao.MovimientoTelefonoDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.model.ImportacionRequest;
import com.reparaciones.servidor.model.VerificacionImei;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Importación de lotes en bloque. Re-verifica los conflictos en servidor (otro
 * usuario puede haber importado entre la vista previa y el confirmar): los IMEIs
 * ACTIVOS (estado RECIBIDO/EN_REVISION/BLOQUEADO o con trabajo abierto) se omiten
 * y se devuelven en la respuesta; el resto se inserta o re-entra (ESTADO=RECIBIDO).
 */
@Service
public class LoteImportService {

    private static final java.util.Set<String> ESTADOS_ACTIVOS =
            java.util.Set.of("RECIBIDO", "EN_REVISION", "BLOQUEADO");

    private final LoteDAO loteDao;
    private final TelefonoDAO telefonoDao;
    private final MovimientoTelefonoDAO movimientoDao;
    private final LogDAO logDao;

    public LoteImportService(LoteDAO loteDao, TelefonoDAO telefonoDao,
                             MovimientoTelefonoDAO movimientoDao, LogDAO logDao) {
        this.loteDao = loteDao; this.telefonoDao = telefonoDao;
        this.movimientoDao = movimientoDao; this.logDao = logDao;
    }

    @Transactional
    public ImportacionRequest.Respuesta importar(ImportacionRequest req, int idUsu) {
        List<String> conflictos = new ArrayList<>();
        int lotes = 0, telefonos = 0;
        for (ImportacionRequest.LoteImport lote : req.lotes()) {
            if (lote.telefonos() == null || lote.telefonos().isEmpty()) continue;
            Map<String, VerificacionImei> existentes = telefonoDao.verificar(
                    lote.telefonos().stream().map(ImportacionRequest.TelefonoImport::imei).toList())
                .stream().collect(Collectors.toMap(VerificacionImei::getImei, v -> v));
            int idLote = loteDao.obtenerOCrear(lote.batchNumber(), lote.idProv(), lote.nota());
            lotes++;
            int nLote = 0;
            for (ImportacionRequest.TelefonoImport t : lote.telefonos()) {
                VerificacionImei v = existentes.get(t.imei());
                boolean activo = v != null && (v.getTrabajosAbiertos() > 0
                        || (v.getEstado() != null && ESTADOS_ACTIVOS.contains(v.getEstado())));
                if (activo) { conflictos.add(t.imei()); continue; }
                boolean reentrada = v != null;
                telefonoDao.upsertImportacion(t.imei(), t.modelo(), idLote, t.storageGb(), t.color(),
                        t.gradoProveedor(), t.precioCompra(), t.divisa(), t.precioCompraEur());
                movimientoDao.insertar(t.imei(), null, "ALMACEN", idUsu,
                        reentrada ? "Re-entrada por importación" : "Importación",
                        "LOTE:" + lote.batchNumber());
                nLote++; telefonos++;
            }
            logDao.insertar(idUsu, "IMPORTAR_LOTE",
                    "BATCH: " + lote.batchNumber() + ", TELEFONOS: " + nLote);
        }
        return new ImportacionRequest.Respuesta(lotes, telefonos, conflictos);
    }
}
```

- [ ] **Paso 4: Endpoint en `LoteController`** (inyectar `LoteImportService`):

```java
@PostMapping("/importar")
@PreAuthorize("hasRole('SUPERTECNICO')")
public com.reparaciones.servidor.model.ImportacionRequest.Respuesta importar(
        @RequestBody com.reparaciones.servidor.model.ImportacionRequest req,
        @AuthenticationPrincipal UsuarioPrincipal principal) {
    return importService.importar(req, principal.getIdUsu());
}
```

Imports nuevos: `org.springframework.security.access.prepost.PreAuthorize`, `org.springframework.security.core.annotation.AuthenticationPrincipal`, `com.reparaciones.servidor.security.UsuarioPrincipal`, `com.reparaciones.servidor.service.LoteImportService`.

- [ ] **Paso 5: Compilar + tests + commit (repo SERVIDOR)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add src/main/java && git commit -m "feat(servidor): importación transaccional de lotes con re-entradas, movimientos y log"
```

---

### Tarea 7: Servidor — edición de atributos + equivalencias de modelo

**Files:**
- Modify: `dao/TelefonoDAO.java`, `controller/TelefonoController.java`
- Create: `dao/EquivalenciaModeloDAO.java`, `controller/EquivalenciaModeloController.java`

**Interfaces:**
- Produces (consume Tarea 11): `PATCH /api/telefonos/{imei}/atributos` body `{modelo, storageGb:Integer|null, color, gradoProveedor, gradoPropio, updatedAt:LocalDateTime}` (semántica SET total: lo que llegue null se guarda null, salvo modelo que es obligatorio) → 204, o 409 si UPDATED_AT no casa. `GET /api/modelos/equivalencias` → lista de `{textoExterno, modeloInterno}`. `PUT /api/modelos/equivalencias` body `{textoExterno, modeloInterno}` → 204 (upsert).

- [ ] **Paso 1: `TelefonoDAO.actualizarAtributos` (servidor)** — mismo patrón de lock optimista que `actualizarObservacion`:

```java
public void actualizarAtributos(String imei, String modelo, Integer storageGb, String color,
                                String gradoProveedor, String gradoPropio, LocalDateTime updatedAt) {
    int filas = jdbc.update(
        "UPDATE Telefono SET MODELO = ?, STORAGE_GB = ?, COLOR = ?, GRADO_PROVEEDOR = ?, GRADO_PROPIO = ?" +
        " WHERE IMEI = ? AND UPDATED_AT = ?",
        modelo, storageGb, color, gradoProveedor, gradoPropio, imei,
        Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
    if (filas == 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
    }
}
```

- [ ] **Paso 2: endpoint en `TelefonoController`** (junto a los PATCH existentes; misma estructura):

```java
@PatchMapping("/{imei}/atributos")
@ResponseStatus(HttpStatus.NO_CONTENT)
@PreAuthorize("hasRole('SUPERTECNICO')")
public void actualizarAtributos(@PathVariable String imei,
                                @RequestBody AtributosRequest req,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
    dao.actualizarAtributos(imei, req.modelo(), req.storageGb(), req.color(),
            req.gradoProveedor(), req.gradoPropio(), req.updatedAt());
    logDao.insertar(principal.getIdUsu(), "EDITAR_ATRIBUTOS",
            "IMEI: " + imei + ", MODELO: " + req.modelo());
}

private record AtributosRequest(String modelo, Integer storageGb, String color,
                                String gradoProveedor, String gradoPropio,
                                java.time.LocalDateTime updatedAt) {}
```

- [ ] **Paso 3: `dao/EquivalenciaModeloDAO.java` + `controller/EquivalenciaModeloController.java`**

```java
package com.reparaciones.servidor.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class EquivalenciaModeloDAO {

    private final JdbcTemplate jdbc;

    public EquivalenciaModeloDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, String>> getAll() {
        return jdbc.query("SELECT TEXTO_EXTERNO, MODELO_INTERNO FROM Modelo_equivalencia",
            (rs, row) -> Map.of("textoExterno", rs.getString("TEXTO_EXTERNO"),
                                "modeloInterno", rs.getString("MODELO_INTERNO")));
    }

    public void guardar(String textoExterno, String modeloInterno) {
        jdbc.update("INSERT INTO Modelo_equivalencia (TEXTO_EXTERNO, MODELO_INTERNO) VALUES (?, ?)" +
                    " ON DUPLICATE KEY UPDATE MODELO_INTERNO = ?",
            textoExterno, modeloInterno, modeloInterno);
    }
}
```

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EquivalenciaModeloDAO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modelos/equivalencias")
public class EquivalenciaModeloController {

    private final EquivalenciaModeloDAO dao;

    public EquivalenciaModeloController(EquivalenciaModeloDAO dao) { this.dao = dao; }

    @GetMapping
    public List<Map<String, String>> getAll() { return dao.getAll(); }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERTECNICO')")
    public void guardar(@RequestBody EquivalenciaRequest req) {
        dao.guardar(req.textoExterno(), req.modeloInterno());
    }

    private record EquivalenciaRequest(String textoExterno, String modeloInterno) {}
}
```

- [ ] **Paso 4: Compilar + tests + commit (repo SERVIDOR)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add src/main/java && git commit -m "feat(servidor): edición de atributos de teléfono y equivalencias de modelo del importador"
```

---

### Tarea 8: Cliente — Apache POI + `LoteXlsxParser` (TDD)

**Files:**
- Modify: `gestion-reparaciones-cliente/pom.xml`
- Create: `src/main/java/com/reparaciones/utils/LoteXlsxParser.java`
- Test: `src/test/java/com/reparaciones/utils/LoteXlsxParserTest.java`

**Interfaces:**
- Produces: `LoteXlsxParser.parsear(InputStream)` → `Resultado(List<Fila> filas, List<String> avisos)`; `Fila(int numFila, String imei, String fabricante, String modeloTexto, Integer storageGb, String color, String grado, BigDecimal precioCompra, String proveedorNombre, String batchNumber, Integer status)`. `numFila` es el número humano de Excel (7, 8…). Lanza `IOException` con mensaje claro si el fichero no es la plantilla. El parser SOLO extrae (validación de negocio en Tarea 10).

- [ ] **Paso 1: Añadir POI al `pom.xml`** (tras el bloque de Gson; `maven-dependency-plugin` ya copia los runtime a `target/libs`, no hay que tocar el empaquetado):

```xml
<!-- Apache POI — lectura de la plantilla xlsx del importador de lotes -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

- [ ] **Paso 2: Test que falla** — fixture construido en memoria con POI (sin binarios en el repo):

```java
package com.reparaciones.utils;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoteXlsxParserTest {

    /** Libro con la estructura real de la plantilla: cabeceras en filas 5-6, datos desde la 7. */
    private InputStream libro(Object[]... filasDatos) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Лист1");
            Row r5 = sh.createRow(4);
            r5.createCell(0).setCellValue("IMEI/SN *");
            r5.createCell(5).setCellValue("Grade");
            r5.createCell(6).setCellValue("Purchase price");
            r5.createCell(7).setCellValue("Supplier Name");
            r5.createCell(8).setCellValue("Batch Number");
            r5.createCell(13).setCellValue("Status");
            Row r6 = sh.createRow(5);
            r6.createCell(1).setCellValue("Manufacturer");
            r6.createCell(2).setCellValue("Model");
            r6.createCell(3).setCellValue("Storage");
            r6.createCell(4).setCellValue("Color");
            int n = 6;
            for (Object[] fila : filasDatos) {
                Row r = sh.createRow(n++);
                for (int c = 0; c < fila.length; c++) {
                    if (fila[c] == null) continue;
                    if (fila[c] instanceof Number num) r.createCell(c).setCellValue(num.doubleValue());
                    else r.createCell(c).setCellValue(fila[c].toString());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test void parseaFilaCompletaConCeldasNumericas() throws IOException {
        // IMEI y storage numéricos y precio con muchos decimales, como el fichero real de Hy5
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{352513424271910L, "Apple", "iPhone 12", 128, "White", null,
                         152.24622210318043, "Hy5", "1445947", "iPhones", null, null, null, 0}));
        assertEquals(1, res.filas().size());
        LoteXlsxParser.Fila f = res.filas().get(0);
        assertEquals(7, f.numFila());
        assertEquals("352513424271910", f.imei());
        assertEquals("Apple", f.fabricante());
        assertEquals("iPhone 12", f.modeloTexto());
        assertEquals(128, f.storageGb());
        assertEquals("White", f.color());
        assertNull(f.grado());
        assertEquals(new BigDecimal("152.25"), f.precioCompra());
        assertEquals("Hy5", f.proveedorNombre());
        assertEquals("1445947", f.batchNumber());
        assertEquals(0, f.status());
    }

    @Test void parseaCeldasDeTexto() throws IOException {
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{"352513424271910", "Apple", "iPhone 12 mini", "256", "Black", "A",
                         "117.50", "Hy5", "1445948", null, null, null, null, "5"}));
        LoteXlsxParser.Fila f = res.filas().get(0);
        assertEquals("352513424271910", f.imei());
        assertEquals(256, f.storageGb());
        assertEquals("A", f.grado());
        assertEquals(new BigDecimal("117.50"), f.precioCompra());
        assertEquals(5, f.status());
    }

    @Test void ignoraFilasVacias() throws IOException {
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{352513424271910L, "Apple", "iPhone 12", null, null, null, null, "Hy5", "1", null, null, null, null, 0},
            new Object[]{},   // fila totalmente vacía
            new Object[]{352513424271911L, "Apple", "iPhone 12", null, null, null, null, "Hy5", "1", null, null, null, null, 0}));
        assertEquals(2, res.filas().size());
        assertEquals(List.of(), res.avisos());
    }

    @Test void statusVacioSeInterpretaComoCero() throws IOException {
        var res = LoteXlsxParser.parsear(libro(
            new Object[]{352513424271910L, "Apple", "iPhone 12", null, null, null, null, "Hy5", "1"}));
        assertEquals(0, res.filas().get(0).status());
    }

    @Test void rechazaUnLibroQueNoEsLaPlantilla() {
        assertThrows(IOException.class, () -> {
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                wb.createSheet("Hoja1").createRow(0).createCell(0).setCellValue("cualquier cosa");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);
                LoteXlsxParser.parsear(new ByteArrayInputStream(out.toByteArray()));
            }
        });
    }
}
```

- [ ] **Paso 3: Ejecutar y ver que falla**

Run: `cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test -Dtest=LoteXlsxParserTest`
Esperado: error de compilación `cannot find symbol: LoteXlsxParser`.

- [ ] **Paso 4: Implementación**

```java
package com.reparaciones.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de la plantilla xlsx de la otra plataforma de stock (spec F2 §1.2).
 * Columnas por posición fija A–N, cabeceras en filas 5–6, datos desde la fila 7.
 * SOLO extrae: la validación de negocio (status, duplicados, mapeo de modelos)
 * la hace {@link ClasificadorImportacion}.
 */
public final class LoteXlsxParser {

    private LoteXlsxParser() {}

    public record Fila(int numFila, String imei, String fabricante, String modeloTexto,
                       Integer storageGb, String color, String grado, BigDecimal precioCompra,
                       String proveedorNombre, String batchNumber, Integer status) {}

    public record Resultado(List<Fila> filas, List<String> avisos) {}

    private static final int PRIMERA_FILA_DATOS = 6;  // fila 7 de Excel

    public static Resultado parsear(InputStream in) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);   // la hoja se llama "Лист1": SIEMPRE por índice
            validarPlantilla(hoja);
            List<Fila> filas = new ArrayList<>();
            List<String> avisos = new ArrayList<>();
            for (int i = PRIMERA_FILA_DATOS; i <= hoja.getLastRowNum(); i++) {
                Row row = hoja.getRow(i);
                if (row == null) continue;
                String imei   = texto(row.getCell(0));
                String modelo = texto(row.getCell(2));
                if ((imei == null || imei.isBlank()) && (modelo == null || modelo.isBlank())) continue;
                Integer status = entero(row.getCell(13));
                filas.add(new Fila(i + 1, imei, texto(row.getCell(1)), modelo,
                        entero(row.getCell(3)), texto(row.getCell(4)), texto(row.getCell(5)),
                        decimal(row.getCell(6)), texto(row.getCell(7)), texto(row.getCell(8)),
                        status == null ? 0 : status));
            }
            return new Resultado(filas, avisos);
        }
    }

    private static void validarPlantilla(Sheet hoja) throws IOException {
        Row r5 = hoja.getRow(4);
        String a5 = r5 == null ? null : texto(r5.getCell(0));
        String i5 = r5 == null ? null : texto(r5.getCell(8));
        if (a5 == null || !a5.startsWith("IMEI/SN") || !"Batch Number".equals(i5)) {
            throw new IOException("El fichero no parece la plantilla de lotes: faltan las cabeceras" +
                    " \"IMEI/SN\" (A5) y \"Batch Number\" (I5).");
        }
    }

    /** Texto de una celda que puede ser STRING o NUMERIC (un IMEI de 15 dígitos cabe exacto en double). */
    private static String texto(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.STRING) {
            String s = c.getStringCellValue().trim();
            return s.isEmpty() ? null : s;
        }
        if (c.getCellType() == CellType.NUMERIC) {
            double v = c.getNumericCellValue();
            if (v == Math.floor(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        return null;
    }

    private static Integer entero(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
        String s = texto(c);
        if (s == null) return null;
        try { return Integer.parseInt(s.replaceAll("\\D", "")); }
        catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal decimal(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(c.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        String s = texto(c);
        if (s == null) return null;
        try { return new BigDecimal(s.replace(',', '.')).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return null; }
    }
}
```

- [ ] **Paso 5: Ejecutar tests y verificación con el fichero REAL** (una vez verde el unit test):

Run: `mvn -q test -Dtest=LoteXlsxParserTest` → verde. Después, smoke rápido del parser contra el fichero real con JShell NO es viable con deps; en su lugar añadir TEMPORALMENTE al test un `main` no; **omitir**: la verificación con fichero real se hace en la Tarea 12 con la app arrancada.

Run final: `mvn -q test` → toda la suite del cliente verde (113+ tests).

- [ ] **Paso 6: Commit (repo RAÍZ)**

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones" && git add gestion-reparaciones-cliente/pom.xml gestion-reparaciones-cliente/src && git commit -m "feat(importador): Apache POI y parser de la plantilla xlsx de lotes"
```

---

### Tarea 9: Cliente — `ModeloMapper` (TDD)

**Files:**
- Create: `src/main/java/com/reparaciones/controllers/ModeloMapper.java` (en `controllers` para poder leer `FormularioReparacionController.MODELOS_ORDENADOS`, mismo motivo documentado en `SelectorModeloDialog`)
- Test: `src/test/java/com/reparaciones/controllers/ModeloMapperTest.java`

**Interfaces:**
- Produces: `ModeloMapper.normalizar(String)` → clave normalizada (minúsculas, solo `[a-z0-9]`, sin prefijo `iphone`); `ModeloMapper.mapear(Collection<String> textos, Map<String,String> equivalencias)` → `Map<String,String>` texto original → código interno o `null` si no casa. Las claves del mapa de equivalencias son textos NORMALIZADOS (así se guardan en BD, Tarea 7).

- [ ] **Paso 1: Test que falla**

```java
package com.reparaciones.controllers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModeloMapperTest {

    @Test void normalizaQuitandoPrefijoIphoneYSimbolos() {
        assertEquals("12mini", ModeloMapper.normalizar("iPhone 12 mini"));
        assertEquals("se2020", ModeloMapper.normalizar(" iPhone SE 2020 "));
        assertEquals("xsmax", ModeloMapper.normalizar("iPhone XS Max"));
        assertEquals("16esim", ModeloMapper.normalizar("iPhone 16 eSIM"));
    }

    @Test void mapeaDirectoContraElCatalogo() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 12 mini", "iPhone 12"), Map.of());
        assertEquals("12mini", m.get("iPhone 12 mini"));
        assertEquals("12", m.get("iPhone 12"));
    }

    @Test void sinCorrespondenciaDevuelveNull() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 16 eSIM", "Galaxy S24"), Map.of());
        assertNull(m.get("iPhone 16 eSIM"));
        assertNull(m.get("Galaxy S24"));
    }

    @Test void lasEquivalenciasGuardadasResuelvenLoQueNoCasa() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 16 eSIM"), Map.of("16esim", "16"));
        assertEquals("16", m.get("iPhone 16 eSIM"));
    }

    @Test void laEquivalenciaGanaAlMatchDirecto() {
        Map<String, String> m = ModeloMapper.mapear(List.of("iPhone 12"), Map.of("12", "12pro"));
        assertEquals("12pro", m.get("iPhone 12"));
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que falla**

Run: `mvn -q test -Dtest=ModeloMapperTest` → `cannot find symbol: ModeloMapper`.

- [ ] **Paso 3: Implementación**

```java
package com.reparaciones.controllers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mapeo de los textos de modelo del xlsx ("iPhone 12 mini") al código interno del
 * catálogo ("12mini"). En {@code controllers} para leer
 * {@link FormularioReparacionController#MODELOS_ORDENADOS} (mismo motivo que
 * {@link SelectorModeloDialog}). Las equivalencias guardadas (BD, tabla
 * Modelo_equivalencia) van indexadas por texto normalizado y tienen prioridad.
 */
public final class ModeloMapper {

    private ModeloMapper() {}

    /** minúsculas, solo [a-z0-9] y sin el prefijo "iphone": "iPhone SE 2020" → "se2020". */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return s.startsWith("iphone") ? s.substring("iphone".length()) : s;
    }

    /** @return mapa texto original → código interno, o null si no hay correspondencia. */
    public static Map<String, String> mapear(Collection<String> textos, Map<String, String> equivalencias) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String texto : textos) {
            if (texto == null || out.containsKey(texto)) continue;
            String clave = normalizar(texto);
            String interno = equivalencias.get(clave);
            if (interno == null && FormularioReparacionController.MODELOS_ORDENADOS.contains(clave))
                interno = clave;
            out.put(texto, interno);
        }
        return out;
    }
}
```

- [ ] **Paso 4: Verde + commit (repo RAÍZ)**

Run: `mvn -q test -Dtest=ModeloMapperTest` → verde; `mvn -q test` → suite completa verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): mapeo de modelos del xlsx al catálogo interno con equivalencias recordadas"
```

---

### Tarea 10: Cliente — `ClasificadorImportacion` (TDD, el "cerebro" de la vista previa)

**Files:**
- Create: `src/main/java/com/reparaciones/utils/ClasificadorImportacion.java`
- Test: `src/test/java/com/reparaciones/utils/ClasificadorImportacionTest.java`

**Interfaces:**
- Consumes: `LoteXlsxParser.Fila` (Tarea 8), `models.VerificacionImei` (Tarea 11 — para no bloquear, esta tarea crea YA `models/VerificacionImei.java` del cliente, ver Paso 1).
- Produces: `ClasificadorImportacion.clasificar(List<Fila> filas, Map<String,String> mapeoModelos, Map<String,VerificacionImei> existentes)` → `Plan(List<LotePlan> lotes, List<FilaClasificada> excluidas)`, donde `LotePlan(String batchNumber, String proveedorNombre, List<FilaClasificada> filas)` agrupa las filas IMPORTABLES por batch, y `FilaClasificada(Fila fila, Destino destino, String modeloInterno, String detalle)` con `Destino ∈ {NUEVO, REENTRADA, CONFLICTO, STATUS_DISTINTO, INVALIDO, DUPLICADO_FICHERO, MODELO_SIN_MAPEAR}`. Importables = NUEVO y REENTRADA; el resto va a `excluidas`.

- [ ] **Paso 1: `models/VerificacionImei.java` del cliente** (espejo del DTO del servidor, nombres idénticos):

```java
package com.reparaciones.models;

/** Respuesta de POST /api/lotes/verificar para un IMEI que ya existe. */
public class VerificacionImei {

    private String imei;
    private boolean existe;
    private String estado;          // null = histórico
    private int trabajosAbiertos;
    private String modelo;

    public VerificacionImei(String imei, boolean existe, String estado, int trabajosAbiertos, String modelo) {
        this.imei = imei; this.existe = existe; this.estado = estado;
        this.trabajosAbiertos = trabajosAbiertos; this.modelo = modelo;
    }

    public String getImei()          { return imei; }
    public boolean isExiste()        { return existe; }
    public String getEstado()        { return estado; }
    public int getTrabajosAbiertos() { return trabajosAbiertos; }
    public String getModelo()        { return modelo; }

    /** Activo = en el ciclo (estado no final) o con trabajo abierto ⇒ conflicto al importar. */
    public boolean esActivo() {
        if (trabajosAbiertos > 0) return true;
        return estado != null && !estado.equals("OK") && !estado.equals("ENVIADO") && !estado.equals("DESGUACE");
    }
}
```

- [ ] **Paso 2: Test que falla** (usar un helper `fila(imei, modelo, batch, status)` que construya `LoteXlsxParser.Fila` con el resto de campos fijos):

```java
package com.reparaciones.utils;

import com.reparaciones.models.VerificacionImei;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClasificadorImportacionTest {

    private LoteXlsxParser.Fila fila(String imei, String modelo, String batch, int status) {
        return new LoteXlsxParser.Fila(7, imei, "Apple", modelo, 128, "White", null,
                new BigDecimal("100.00"), "Hy5", batch, status);
    }

    private static final Map<String, String> MAPEO = Map.of("iPhone 12", "12");

    @Test void filaNuevaConStatusCeroEsImportable() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "B1", 0)), MAPEO, Map.of());
        assertEquals(1, plan.lotes().size());
        var f = plan.lotes().get(0).filas().get(0);
        assertEquals(ClasificadorImportacion.Destino.NUEVO, f.destino());
        assertEquals("12", f.modeloInterno());
        assertTrue(plan.excluidas().isEmpty());
    }

    @Test void agrupaPorBatchNumberUnLotePorCadaUno() {
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("352513424271910", "iPhone 12", "B1", 0),
                fila("352513424271911", "iPhone 12", "B2", 0),
                fila("352513424271912", "iPhone 12", "B1", 0)), MAPEO, Map.of());
        assertEquals(2, plan.lotes().size());
        assertEquals("B1", plan.lotes().get(0).batchNumber());
        assertEquals(2, plan.lotes().get(0).filas().size());
    }

    @Test void statusDistintoDeCeroNoEntraYSeAvisa() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", "B1", 5)), MAPEO, Map.of());
        assertTrue(plan.lotes().isEmpty());
        assertEquals(ClasificadorImportacion.Destino.STATUS_DISTINTO, plan.excluidas().get(0).destino());
    }

    @Test void imeiInvalidoNoEntra() {
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("123", "iPhone 12", "B1", 0),
                fila(null, "iPhone 12", "B1", 0)), MAPEO, Map.of());
        assertEquals(2, plan.excluidas().size());
        assertEquals(ClasificadorImportacion.Destino.INVALIDO, plan.excluidas().get(0).destino());
    }

    @Test void sinBatchNumberEsInvalido() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 12", null, 0)), MAPEO, Map.of());
        assertEquals(ClasificadorImportacion.Destino.INVALIDO, plan.excluidas().get(0).destino());
    }

    @Test void imeiRepetidoEnElFicheroSoloEntraLaPrimera() {
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("352513424271910", "iPhone 12", "B1", 0),
                fila("352513424271910", "iPhone 12", "B1", 0)), MAPEO, Map.of());
        assertEquals(1, plan.lotes().get(0).filas().size());
        assertEquals(ClasificadorImportacion.Destino.DUPLICADO_FICHERO, plan.excluidas().get(0).destino());
    }

    @Test void modeloSinMapearBloqueaLaFila() {
        var plan = ClasificadorImportacion.clasificar(
                List.of(fila("352513424271910", "iPhone 16 eSIM", "B1", 0)),
                Map.of(), Map.of());
        assertEquals(ClasificadorImportacion.Destino.MODELO_SIN_MAPEAR, plan.excluidas().get(0).destino());
    }

    @Test void imeiActivoEsConflictoYFinalOHistoricoEsReentrada() {
        var verif = Map.of(
                "352513424271910", new VerificacionImei("352513424271910", true, "RECIBIDO", 0, "12"),
                "352513424271911", new VerificacionImei("352513424271911", true, null, 2, "12"),
                "352513424271912", new VerificacionImei("352513424271912", true, null, 0, "12"),
                "352513424271913", new VerificacionImei("352513424271913", true, "ENVIADO", 0, "12"));
        var plan = ClasificadorImportacion.clasificar(List.of(
                fila("352513424271910", "iPhone 12", "B1", 0),
                fila("352513424271911", "iPhone 12", "B1", 0),
                fila("352513424271912", "iPhone 12", "B1", 0),
                fila("352513424271913", "iPhone 12", "B1", 0)), MAPEO, verif);
        assertEquals(2, plan.excluidas().size());   // los dos activos
        assertTrue(plan.excluidas().stream().allMatch(f -> f.destino() == ClasificadorImportacion.Destino.CONFLICTO));
        List<ClasificadorImportacion.FilaClasificada> importables = plan.lotes().get(0).filas();
        assertEquals(2, importables.size());        // histórico sin trabajos + enviado ⇒ re-entradas
        assertTrue(importables.stream().allMatch(f -> f.destino() == ClasificadorImportacion.Destino.REENTRADA));
    }
}
```

- [ ] **Paso 3: Ejecutar y ver que falla** — `mvn -q test -Dtest=ClasificadorImportacionTest` → no compila.

- [ ] **Paso 4: Implementación**

```java
package com.reparaciones.utils;

import com.reparaciones.models.VerificacionImei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clasifica las filas parseadas del xlsx cruzándolas con el mapeo de modelos y la
 * verificación de IMEIs del servidor (spec F2 §3): solo entran filas Status=0;
 * duplicados finales/históricos = re-entrada legítima, activos = conflicto.
 * Lógica pura sin UI ni red: la vista previa (ImportadorLoteDialog) solo pinta esto.
 */
public final class ClasificadorImportacion {

    private ClasificadorImportacion() {}

    public enum Destino { NUEVO, REENTRADA, CONFLICTO, STATUS_DISTINTO, INVALIDO, DUPLICADO_FICHERO, MODELO_SIN_MAPEAR }

    public record FilaClasificada(LoteXlsxParser.Fila fila, Destino destino, String modeloInterno, String detalle) {}

    public record LotePlan(String batchNumber, String proveedorNombre, List<FilaClasificada> filas) {}

    public record Plan(List<LotePlan> lotes, List<FilaClasificada> excluidas) {}

    public static Plan clasificar(List<LoteXlsxParser.Fila> filas,
                                  Map<String, String> mapeoModelos,
                                  Map<String, VerificacionImei> existentes) {
        Map<String, LotePlan> lotes = new LinkedHashMap<>();
        List<FilaClasificada> excluidas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (LoteXlsxParser.Fila f : filas) {
            String imei = f.imei() == null ? "" : f.imei().replaceAll("\\D", "");
            if (f.status() != null && f.status() != 0) {
                excluidas.add(new FilaClasificada(f, Destino.STATUS_DISTINTO, null,
                        "Status " + f.status() + " (solo entran filas con Status 0)"));
                continue;
            }
            if (imei.length() != 15) {
                excluidas.add(new FilaClasificada(f, Destino.INVALIDO, null,
                        "IMEI inválido: \"" + (f.imei() == null ? "" : f.imei()) + "\""));
                continue;
            }
            if (f.batchNumber() == null || f.batchNumber().isBlank()) {
                excluidas.add(new FilaClasificada(f, Destino.INVALIDO, null, "Fila sin Batch Number"));
                continue;
            }
            if (!vistos.add(imei)) {
                excluidas.add(new FilaClasificada(f, Destino.DUPLICADO_FICHERO, null,
                        "IMEI repetido en el fichero (solo entra la primera aparición)"));
                continue;
            }
            String interno = mapeoModelos.get(f.modeloTexto());
            if (interno == null) {
                excluidas.add(new FilaClasificada(f, Destino.MODELO_SIN_MAPEAR, null,
                        "Modelo \"" + f.modeloTexto() + "\" sin correspondencia en el catálogo"));
                continue;
            }
            VerificacionImei v = existentes.get(imei);
            Destino destino = Destino.NUEVO;
            String detalle = null;
            if (v != null && v.esActivo()) {
                excluidas.add(new FilaClasificada(f, Destino.CONFLICTO, interno,
                        "Ya está activo en el sistema (estado " +
                        (v.getEstado() == null ? "histórico con trabajo abierto" : v.getEstado()) + ")"));
                continue;
            }
            if (v != null) { destino = Destino.REENTRADA; detalle = "Re-entrada: conserva su historial"; }
            lotes.computeIfAbsent(f.batchNumber(),
                    b -> new LotePlan(b, f.proveedorNombre(), new ArrayList<>()))
                 .filas().add(new FilaClasificada(f, destino, interno, detalle));
        }
        return new Plan(new ArrayList<>(lotes.values()), excluidas);
    }
}
```

- [ ] **Paso 5: Verde + commit (repo RAÍZ)**

Run: `mvn -q test` → suite completa verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): clasificador de filas de importación (nuevos, re-entradas, conflictos y avisos)"
```

---

### Tarea 11: Cliente — modelos + DAOs nuevos (+ `UbicacionTexto`)

**Files:**
- Create: `models/TelefonoInventario.java`, `models/Lote.java`, `models/Importacion.java`
- Create: `dao/LoteDAO.java`, `dao/EquivalenciaModeloDAO.java`
- Modify: `dao/TelefonoDAO.java`
- Create: `utils/UbicacionTexto.java`
- Tests: `src/test/java/com/reparaciones/models/TelefonoInventarioTest.java`, `src/test/java/com/reparaciones/utils/UbicacionTextoTest.java`

**Interfaces:**
- Consumes: JSON del servidor (Tareas 4–7). Los nombres de campo Java = nombres JSON (Gson).
- Produces (para Tareas 12–16):
  - `TelefonoInventario` con getters de los 27 campos del DTO + derivados: `getResumenTipos()` ("2 Rep · 1 Glass · 1 Pul", cuenta HECHAS), `isTieneAsignaciones()` (= `glassAbiertos + normalAbiertos > 0`, paridad exacta con el toggle actual), `getTrabajosAbiertos()` (= suma de los 3).
  - `Lote` (campos de Tarea 3) con `toString()` → `batchNumber + " (" + proveedor + ")"`.
  - `Importacion.LoteImport/TelefonoImport/Request/Respuesta` (records espejo de Tarea 6).
  - `LoteDAO.getAll()`, `LoteDAO.verificar(List<String>)` → `List<VerificacionImei>`, `LoteDAO.importar(Importacion.Request)` → `Importacion.Respuesta`.
  - `EquivalenciaModeloDAO.getAll()` → `Map<String,String>` (textoExterno→modeloInterno), `EquivalenciaModeloDAO.guardar(textoExterno, modeloInterno)`.
  - `TelefonoDAO.getInventario()` → `List<TelefonoInventario>`; `TelefonoDAO.actualizarAtributos(imei, modelo, storageGb, color, gradoProveedor, gradoPropio, updatedAt)`.
  - `UbicacionTexto.ubicacion(TelefonoInventario)` → "Almacén" / "Reparaciones → Pulido + Normal" / "⏳ Reparaciones → Glass" / "—"; `UbicacionTexto.estado(TelefonoInventario)` → "Recibido" / "En reparación" / "En revisión" / "Bloqueado" / "OK" / "Enviado" / "Desguace" / "Histórico".

- [ ] **Paso 1: Tests que fallan** (los dos con lógica):

```java
package com.reparaciones.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelefonoInventarioTest {

    private TelefonoInventario tel(int repH, int glassH, int pulH, int pulA, int glassA, int normalA) {
        TelefonoInventario t = new TelefonoInventario();
        t.setRepHechas(repH); t.setGlassHechas(glassH); t.setPulHechos(pulH);
        t.setPulAbiertos(pulA); t.setGlassAbiertos(glassA); t.setNormalAbiertos(normalA);
        return t;
    }

    @Test void resumenTiposOmiteLosCeros() {
        assertEquals("2 Rep · 1 Glass", tel(2, 1, 0, 0, 0, 0).getResumenTipos());
        assertEquals("1 Pul", tel(0, 0, 1, 0, 0, 0).getResumenTipos());
        assertEquals("—", tel(0, 0, 0, 0, 0, 0).getResumenTipos());
    }

    @Test void tieneAsignacionesIgnoraLosPulidos() {
        // paridad con TelefonoDAO.tieneAsignacionesActivas del servidor (A% no AP%)
        assertFalse(tel(0, 0, 0, 3, 0, 0).isTieneAsignaciones());
        assertTrue(tel(0, 0, 0, 0, 1, 0).isTieneAsignaciones());
        assertTrue(tel(0, 0, 0, 0, 0, 1).isTieneAsignaciones());
    }
}
```

```java
package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UbicacionTextoTest {

    private TelefonoInventario tel(String estadoEfectivo, String ubicacion, List<String> subs, int solicitudes) {
        TelefonoInventario t = new TelefonoInventario();
        t.setEstadoEfectivo(estadoEfectivo);
        t.setUbicacion(ubicacion);
        t.setSubUbicaciones(subs);
        t.setSolicitudesPendientes(solicitudes);
        return t;
    }

    @Test void formateaUbicacionesSimplesYCompuestas() {
        assertEquals("Almacén", UbicacionTexto.ubicacion(tel("RECIBIDO", "ALMACEN", List.of(), 0)));
        assertEquals("Reparaciones → Pulido + Normal",
                UbicacionTexto.ubicacion(tel("EN_REPARACION", "REPARACIONES", List.of("PULIDO", "NORMAL"), 0)));
        assertEquals("—", UbicacionTexto.ubicacion(tel(null, null, List.of(), 0)));
    }

    @Test void elRelojDeSolicitudPendienteVaDelante() {
        assertEquals("⏳ Reparaciones → Glass",
                UbicacionTexto.ubicacion(tel("EN_REPARACION", "REPARACIONES", List.of("GLASS"), 2)));
    }

    @Test void etiquetasDeEstado() {
        assertEquals("Recibido", UbicacionTexto.estado(tel("RECIBIDO", "ALMACEN", List.of(), 0)));
        assertEquals("En reparación", UbicacionTexto.estado(tel("EN_REPARACION", "REPARACIONES", List.of(), 0)));
        assertEquals("Histórico", UbicacionTexto.estado(tel(null, null, List.of(), 0)));
    }
}
```

- [ ] **Paso 2: Ejecutar y ver que fallan** — `mvn -q test -Dtest='TelefonoInventarioTest,UbicacionTextoTest'` → no compila.

- [ ] **Paso 3: Implementar los modelos**

`models/TelefonoInventario.java`: POJO con los 27 campos del DTO del servidor (Tarea 4, nombres idénticos), constructor vacío, getters y setters completos, y estos derivados:

```java
/** Resumen de trabajos HECHOS para la columna Trabajos, p. ej. "2 Rep · 1 Glass". */
public String getResumenTipos() {
    StringBuilder sb = new StringBuilder();
    if (repHechas   > 0) sb.append(repHechas).append(" Rep");
    if (glassHechas > 0) { if (sb.length() > 0) sb.append(" · "); sb.append(glassHechas).append(" Glass"); }
    if (pulHechos   > 0) { if (sb.length() > 0) sb.append(" · "); sb.append(pulHechos).append(" Pul"); }
    return sb.length() == 0 ? "—" : sb.toString();
}

/** Paridad con TelefonoDAO.tieneAsignacionesActivas del servidor: A% que no sea AP%. */
public boolean isTieneAsignaciones() { return glassAbiertos + normalAbiertos > 0; }

public int getTrabajosAbiertos() { return pulAbiertos + glassAbiertos + normalAbiertos; }

/** Índice del teléfono con ese IMEI en una lista mixta de items de tabla, o -1. */
public static int indiceDe(java.util.List<?> items, String imei) {
    if (imei == null) return -1;
    for (int i = 0; i < items.size(); i++)
        if (items.get(i) instanceof TelefonoInventario t && imei.equals(t.getImei())) return i;
    return -1;
}
```

`models/Lote.java`: POJO campos `idLote, batchNumber, idProv, proveedor, fechaImport (LocalDateTime), nota, numTelefonos, updatedAt`, getters, y `@Override public String toString() { return batchNumber + " (" + proveedor + ")"; }`.

`models/Importacion.java`:

```java
package com.reparaciones.models;

import java.math.BigDecimal;
import java.util.List;

/** DTOs del alta en bloque del importador (espejo exacto de ImportacionRequest del servidor). */
public final class Importacion {

    private Importacion() {}

    public record TelefonoImport(String imei, String modelo, Integer storageGb, String color,
                                 String gradoProveedor, BigDecimal precioCompra, String divisa,
                                 BigDecimal precioCompraEur) {}

    public record LoteImport(String batchNumber, int idProv, String nota, List<TelefonoImport> telefonos) {}

    public record Request(List<LoteImport> lotes) {}

    public record Respuesta(int lotes, int telefonos, List<String> conflictosOmitidos) {}
}
```

`utils/UbicacionTexto.java`:

```java
package com.reparaciones.utils;

import com.reparaciones.models.TelefonoInventario;

import java.util.Map;
import java.util.stream.Collectors;

/** Textos de UI de la ubicación derivada y el estado efectivo (valores canónicos del servidor). */
public final class UbicacionTexto {

    private UbicacionTexto() {}

    private static final Map<String, String> UBICACIONES = Map.of(
            "ALMACEN", "Almacén", "PARA_REVISAR", "Para revisar", "BLOQUEO", "Bloqueo",
            "REPARACIONES", "Reparaciones", "LISTOS", "Listos", "PEDIDOS", "Pedidos");

    private static final Map<String, String> SUBS = Map.of(
            "PULIDO", "Pulido", "GLASS", "Glass", "NORMAL", "Normal");

    private static final Map<String, String> ESTADOS = Map.of(
            "RECIBIDO", "Recibido", "EN_REVISION", "En revisión", "BLOQUEADO", "Bloqueado",
            "EN_REPARACION", "En reparación", "OK", "OK", "ENVIADO", "Enviado", "DESGUACE", "Desguace");

    public static String ubicacion(TelefonoInventario t) {
        if (t.getUbicacion() == null) return "—";
        String base = UBICACIONES.getOrDefault(t.getUbicacion(), t.getUbicacion());
        if (t.getSubUbicaciones() != null && !t.getSubUbicaciones().isEmpty()) {
            base += " → " + t.getSubUbicaciones().stream()
                    .map(s -> SUBS.getOrDefault(s, s)).collect(Collectors.joining(" + "));
        }
        return (t.getSolicitudesPendientes() > 0 ? "⏳ " : "") + base;
    }

    public static String estado(TelefonoInventario t) {
        if (t.getEstadoEfectivo() == null) return "Histórico";
        return ESTADOS.getOrDefault(t.getEstadoEfectivo(), t.getEstadoEfectivo());
    }
}
```

- [ ] **Paso 4: Implementar los DAOs** (wrappers finos, patrón exacto de los DAOs existentes):

`dao/LoteDAO.java`:

```java
package com.reparaciones.dao;

import com.reparaciones.models.Importacion;
import com.reparaciones.models.Lote;
import com.reparaciones.models.VerificacionImei;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Acceso a lotes e importación vía API REST. */
public class LoteDAO {

    public List<Lote> getAll() throws SQLException {
        return ApiClient.getList("/api/lotes", Lote.class);
    }

    public List<VerificacionImei> verificar(List<String> imeis) throws SQLException {
        // ApiClient.post con tipo de respuesta deserializa un objeto; aquí la respuesta es
        // una lista: usar el mismo patrón getList sobre POST no existe, así que se
        // deserializa como array.
        VerificacionImei[] res = ApiClient.post("/api/lotes/verificar", Map.of("imeis", imeis),
                VerificacionImei[].class);
        return res == null ? List.of() : List.of(res);
    }

    public Importacion.Respuesta importar(Importacion.Request request) throws SQLException {
        return ApiClient.post("/api/lotes/importar", request, Importacion.Respuesta.class);
    }
}
```

`dao/EquivalenciaModeloDAO.java`:

```java
package com.reparaciones.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reparaciones.utils.ApiClient;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Equivalencias de modelo recordadas por el importador (clave = texto normalizado). */
public class EquivalenciaModeloDAO {

    public Map<String, String> getAll() throws SQLException {
        JsonArray arr = ApiClient.get("/api/modelos/equivalencias", JsonArray.class);
        Map<String, String> out = new LinkedHashMap<>();
        if (arr != null) for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.put(o.get("textoExterno").getAsString(), o.get("modeloInterno").getAsString());
        }
        return out;
    }

    public void guardar(String textoExterno, String modeloInterno) throws SQLException {
        ApiClient.put("/api/modelos/equivalencias",
                Map.of("textoExterno", textoExterno, "modeloInterno", modeloInterno));
    }
}
```

`dao/TelefonoDAO.java` (cliente) — añadir:

```java
/** Inventario completo para la vista IMEIs evolucionada (F2a). */
public List<com.reparaciones.models.TelefonoInventario> getInventario() throws SQLException {
    return ApiClient.getList("/api/telefonos/inventario", com.reparaciones.models.TelefonoInventario.class);
}

/** Edición de atributos (modelo/storage/color/grados) con lock optimista. */
public void actualizarAtributos(String imei, String modelo, Integer storageGb, String color,
                                String gradoProveedor, String gradoPropio,
                                java.time.LocalDateTime updatedAt) throws SQLException {
    java.util.Map<String, Object> body = new java.util.HashMap<>();
    body.put("modelo", modelo);
    body.put("storageGb", storageGb);
    body.put("color", color);
    body.put("gradoProveedor", gradoProveedor);
    body.put("gradoPropio", gradoPropio);
    body.put("updatedAt", updatedAt);
    ApiClient.patch("/api/telefonos/" + imei + "/atributos", body);
}
```

Nota: si `ApiClient` serializa mal `LocalDateTime` dentro de un `Map` (comprobar cómo lo hace `actualizarObservacion`, que ya envía `updatedAt`): copiar EXACTAMENTE el mecanismo de `actualizarObservacion` (record interno o Map, lo que use).

- [ ] **Paso 5: Verde + commit (repo RAÍZ)**

Run: `mvn -q test` → suite completa verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(cliente): modelos y DAOs de inventario, lotes, importación y equivalencias"
```

---

### Tarea 12: Cliente — diálogo importador con vista previa

**Files:**
- Create: `src/main/java/com/reparaciones/controllers/ImportadorLoteDialog.java`

**Interfaces:**
- Consumes: `LoteXlsxParser`, `ModeloMapper`, `ClasificadorImportacion`, `LoteDAO`, `EquivalenciaModeloDAO`, `ProveedorDAO.getActivos()`, `TipoCambioDAO.getTasa(divisa)`, `SelectorModeloDialog.elegir(...)`.
- Produces: `ImportadorLoteDialog.abrir(javafx.stage.Window owner, Runnable onImportado)` — abre FileChooser, muestra la vista previa y, al confirmar, llama a `LoteDAO.importar` y ejecuta `onImportado`. Lo invoca la Tarea 14 desde el botón "Importar lote".

- [ ] **Paso 1: Implementar el diálogo.** Estructura (patrón de diálogo en código como `PendientesSuperTecnicoController`; TODO el acceso a red en `new Thread(...)` + `Platform.runLater`, patrón del proyecto):

1. `abrir(...)`: `FileChooser` con filtro `*.xlsx` → si hay fichero, hilo que: parsea (`LoteXlsxParser.parsear`), carga `EquivalenciaModeloDAO.getAll()`, `ProveedorDAO.getActivos()`, `LoteDAO.verificar(imeis de 15 dígitos del fichero)` → `Platform.runLater(() -> mostrarVistaPrevia(...))`. Errores → `Alertas.mostrarError`.
2. `mostrarVistaPrevia`: calcula `ModeloMapper.mapear(textos distintos de modelo, equivalencias)` y `ClasificadorImportacion.clasificar(...)`, y construye un `Stage APPLICATION_MODAL` (~900×640, stylesheet `/styles/app.css`) con:
   - Cabecera: nombre del fichero + badges de totales: `N nuevos · N re-entradas · N conflictos · N avisos` (contando destinos).
   - **Bloque "Modelos sin mapear"** (visible solo si hay `MODELO_SIN_MAPEAR`): una fila por texto de modelo distinto sin mapear con label `"iPhone 16 eSIM" (12 filas)` y botón `Elegir modelo…` → `SelectorModeloDialog.elegir(null)`; al elegir: `EquivalenciaModeloDAO.guardar(ModeloMapper.normalizar(texto), interno)` (en hilo), re-mapear y re-clasificar TODO y repintar (método `reclasificar()` que regenera las tablas del diálogo).
   - **Un `TitledPane` por `LotePlan`**: título `Lote <batch> — <proveedor> — N teléfonos`; dentro: selector de proveedor (`ComboBox<Proveedor>` con los activos; preseleccionado si `p.getNombre().equalsIgnoreCase(proveedorNombre.trim())`; si no hay match queda vacío y BLOQUEA importar con aviso rojo "Elige proveedor") + `TableView<FilaClasificada>` (columnas: IMEI, Modelo (`traducirModelo(modeloInterno)`), Storage, Color, Grado, Precio, Destino (badge NUEVO verde `Colores.FILA_REPARADO_*` / REENTRADA azul `#E3F2FD`), Detalle) + label de total del lote: `Total: X.XX <divisa proveedor> ≈ Y.YY €` usando `TipoCambioDAO.getTasa(divisa)` con caché en `Map<String,Double>` (tasa 1.0 si divisa EUR; patrón de `FormularioCompraController`).
   - **`TitledPane` colapsado "No entran (N)"**: `TableView` de `excluidas` (Fila Excel, IMEI, Modelo, Destino, Detalle) — conflictos, avisos de status, inválidos, duplicados.
   - `CheckBox "Incluir re-entradas (N)"`, seleccionado por defecto (spec: re-entrada previa confirmación — la confirmación es este check + el botón).
   - Botones: `Cancelar` y `Importar N teléfonos` (deshabilitado si 0 importables tras filtros o falta algún proveedor).
3. Confirmar: construir `Importacion.Request` con un `LoteImport` por lote (filtrando re-entradas si el check está desmarcado), donde cada `TelefonoImport` lleva: `imei` (los 15 dígitos limpios), `modelo` = interno, `storageGb`, `color`, `gradoProveedor` = `fila.grado()`, `precioCompra`, `divisa` = divisa del proveedor elegido, `precioCompraEur` = `precioCompra × tasa` redondeado a 2 (null si precio null). En hilo: `LoteDAO.importar(request)` → `Platform.runLater`: cerrar, `Alert INFORMATION` con `resp.telefonos() + " teléfonos importados en " + resp.lotes() + " lotes"` (+ aviso con los `conflictosOmitidos` si los hay) y `onImportado.run()`.

- [ ] **Paso 2: Compilar + suite**

Run: `mvn -q test` → BUILD SUCCESS (el diálogo no tiene test unitario: toda su lógica está en las utilidades ya testeadas; es solo pegamento UI).

- [ ] **Paso 3: Commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): vista previa interactiva del xlsx con resolución de modelos e importación en bloque"
```

---

### Tarea 13: Cliente — alta manual de lote

**Files:**
- Create: `src/main/java/com/reparaciones/controllers/AltaManualLoteDialog.java`

**Interfaces:**
- Consumes: `ImeiUtils.parsearPegadoImeis`, `SelectorModeloDialog`, `ProveedorDAO.getActivos()`, `TipoCambioDAO.getTasa`, `LoteDAO.verificar/importar`.
- Produces: `AltaManualLoteDialog.abrir(javafx.stage.Window owner, Runnable onImportado)`. Lo invoca la Tarea 14 desde el botón "Alta manual".

- [ ] **Paso 1: Implementar el diálogo** (spec §3: "alta manual = mismo flujo con lote creado a mano + pegado masivo de IMEIs"). `Stage APPLICATION_MODAL` (~520×640) con:

1. Campos del lote: `TextField` Batch number (obligatorio, `promptText` "p. ej. MANUAL-2026-07-08"); `ComboBox<Proveedor>` (activos, obligatorio).
2. Atributos comunes opcionales (se aplican a todos los IMEIs): botón `Modelo…` → `SelectorModeloDialog.elegir(null)` (label con `traducirModelo`); `TextField` Storage GB (numérico); `TextField` Color; `TextField` Grado proveedor; `TextField` Precio/unidad (en divisa del proveedor; label con `≈ €` usando la tasa como en Tarea 12).
3. Escaneo: `TextField` de escaneo con el patrón EXACTO del modal de asignación masiva (`PendientesSuperTecnicoController` líneas ~1800-1840): al cambiar el texto, `ImeiUtils.parsearPegadoImeis`; `UNICO`/`LOTE` → añadir a un `ListView<String>` (sin duplicados, con contador "N IMEIs" y botón quitar por fila), `CORRUPTO` → label de error rojo "Algún IMEI del pegado está corrupto…".
4. Botón `Crear lote (N teléfonos)` (deshabilitado si batch vacío, sin proveedor o 0 IMEIs): en hilo → `LoteDAO.verificar(imeis)`; los ACTIVOS (`VerificacionImei.esActivo()`) se muestran en un `ConfirmDialog`/`Alert` de aviso listándolos y se EXCLUYEN; con el resto se construye `Importacion.Request` con UN `LoteImport` (todos los `TelefonoImport` con los atributos comunes; modelo puede ir null — el alta manual permite teléfono sin modelo, igual que hoy) → `LoteDAO.importar` → cerrar + info + `onImportado.run()`.

- [ ] **Paso 2: Compilar + suite + commit (repo RAÍZ)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(importador): alta manual de lote con pegado masivo de IMEIs"
```

---

### Tarea 14: Cliente — vista IMEIs evolucionada a inventario (maestro nuevo, drill-down conservado)

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/AgrupadoController.java`
- Modify: `src/main/resources/views/AgrupadoView.fxml`
- Delete: `src/main/java/com/reparaciones/models/GrupoImei.java`, `src/test/java/com/reparaciones/models/GrupoImeiTest.java`

**Interfaces:**
- Consumes: `TelefonoDAO.getInventario()`, `TelefonoInventario`, `UbicacionTexto`, `ImportadorLoteDialog.abrir`, `AltaManualLoteDialog.abrir`.
- Produces: API pública INTACTA (`configurar(Rol)`, `cargar()`, `resetarModo()`, `exportarCSV(Stage)`, `enDetalle()`, `volverAlMaestro()`). Maestro = filas `TelefonoInventario`; detalle = filas `ReparacionResumen` (sin cambios). Los filtros nuevos llegan en la Tarea 15: esta tarea deja los filtros EXISTENTES funcionando sobre el inventario.

- [ ] **Paso 1: FXML** — en `AgrupadoView.fxml`:
  1. En el `HBox` del título, tras `lblContador` y antes del `Region`, añadir: `<Button fx:id="btnImportar" text="Importar lote" styleClass="btn-secondary" onAction="#importarLote" visible="false" managed="false"/>` y `<Button fx:id="btnAltaManual" text="Alta manual" styleClass="btn-secondary" onAction="#altaManualLote" visible="false" managed="false"/>`.
  2. En `<columns>`, tras `colModelo`, añadir: `<TableColumn fx:id="colStorage" minWidth="70" maxWidth="90" text="GB"/>`, `<TableColumn fx:id="colColor" minWidth="80" maxWidth="110" text="Color"/>`, `<TableColumn fx:id="colGrado" minWidth="80" maxWidth="100" text="Grado"/>`; y tras `colEstado`: `<TableColumn fx:id="colUbicacion" minWidth="150" text="Ubicación"/>`, `<TableColumn fx:id="colLote" minWidth="110" text="Lote"/>`.

- [ ] **Paso 2: Controller — datos y carga.** Añadir campos `@FXML` de las 5 columnas nuevas y los 2 botones; añadir `private final ObservableList<TelefonoInventario> inventario = FXCollections.observableArrayList();`. En `cargar()` añadir la carga del inventario para TODOS los roles (los `datos` de trabajos se siguen cargando igual para el detalle y el filtro por técnico):

```java
public void cargar() {
    try {
        List<ReparacionResumen> merge = new ArrayList<>();
        if (rol == Rol.TECNICO) {
            Integer idTec = Sesion.getIdTec();
            if (idTec != null) {
                merge.addAll(reparacionDAO.getReparacionesPorTecnico(idTec));
                merge.addAll(glassDAO.getHistorialGlassPorTecnico(idTec));
                merge.addAll(pulidoDAO.getHistorialPulidoPorTecnico(idTec));
            }
        } else {
            merge.addAll(reparacionDAO.getReparacionesResumen());
            merge.addAll(glassDAO.getHistorialGlass());
            merge.addAll(pulidoDAO.getHistorialPulido());
        }
        datos.setAll(merge);
        inventario.setAll(telefonoDAO.getInventario());   // inventario completo, todos los roles (consulta)
        poblarFiltroCliente();
        aplicarFiltros();
    } catch (SQLException e) {
        mostrarError(e);
    }
}
```

En `configurar(Rol)`, tras fijar `esSuper`, mostrar los botones solo al supertécnico: `btnImportar.setVisible(esSuper); btnImportar.setManaged(esSuper);` (ídem `btnAltaManual`). Añadir los handlers:

```java
@FXML private void importarLote()  { ImportadorLoteDialog.abrir(raiz.getScene().getWindow(), this::cargar); }
@FXML private void altaManualLote(){ AltaManualLoteDialog.abrir(raiz.getScene().getWindow(), this::cargar); }
```

- [ ] **Paso 3: Maestro nuevo.** Sustituir `buildTablaItems()` (que agrupaba `datosFiltrados` en `GrupoImei`) por un filtrado del inventario. La rama maestro de `aplicarFiltros()` pasa a:

```java
String imeiStr = filtroImei.getText().trim();
Set<String> imeisFiltro = FiltroImei.imeisValidos(imeiStr);
// Filtro por técnico: IMEIs con algún trabajo de los técnicos marcados (los datos ya están cargados)
Set<String> imeisDeTecnicos = idsTecFiltro.isEmpty() ? null
        : datos.stream().filter(r -> idsTecFiltro.contains(r.getIdTec()))
               .map(ReparacionResumen::getImei).collect(Collectors.toSet());
List<TelefonoInventario> filtrados = inventario.stream().filter(t -> {
    if (!imeisFiltro.isEmpty() && !imeisFiltro.contains(t.getImei())) return false;
    if (imeisDeTecnicos != null && !imeisDeTecnicos.contains(t.getImei())) return false;
    if (desde != null || hasta != null) {
        if (t.getUltimaActividad() == null) return false;
        LocalDate f = FechaUtils.toLocalDate(t.getUltimaActividad());
        if (desde != null && f.isBefore(desde)) return false;
        if (hasta != null && f.isAfter(hasta))  return false;
    }
    if (!clientesFiltro.isEmpty()) {
        String cli = t.getCliente();
        boolean sin = (cli == null || cli.isEmpty());
        if (!((sin && clientesFiltro.contains(SIN_CLIENTE)) || (!sin && clientesFiltro.contains(cli)))) return false;
    }
    boolean filtrarInc    = cbIncidenciasAbiertas != null && cbIncidenciasAbiertas.isSelected();
    boolean filtrarNormal = cbNormales != null && cbNormales.isSelected();
    if (filtrarInc || filtrarNormal) {
        boolean tieneInc = t.getIncAbiertas() > 0;
        if (!((filtrarInc && tieneInc) || (filtrarNormal && !tieneInc))) return false;
    }
    return true;
}).sorted(Comparator.comparing(TelefonoInventario::getUltimaActividad,
        Comparator.nullsLast(Comparator.reverseOrder()))).collect(Collectors.toList());
tablaItems.setAll(filtrados);
restaurarSeleccion();
int n = tablaItems.size();
lblContador.setText(n + (n == 1 ? " teléfono" : " teléfonos"));
lblContador.setVisible(true); lblContador.setManaged(true);
```

`poblarFiltroCliente()` pasa a alimentarse del inventario (`inventario.stream().map(TelefonoInventario::getCliente)...`, misma lógica de `SIN_CLIENTE`). `restaurarSeleccion()` usa `TelefonoInventario.indiceDe(tablaItems, imeiARestaurar)`.

- [ ] **Paso 4: Columnas.** En cada cellFactory/cellValueFactory del maestro, sustituir la rama `instanceof GrupoImei` por `instanceof TelefonoInventario` (las ramas `ReparacionResumen` del detalle NO se tocan):
  - `colImei`: igual que hoy (label negrita + icono historial → `mostrarDetalle(t.getImei())`).
  - `colModelo`: `t.getModelo()` → `traducirModelo`.
  - `colStorage` (nueva): `t.getStorageGb() == null ? "" : t.getStorageGb() + " GB"`. Visible solo en maestro.
  - `colColor` (nueva): `t.getColor()`. Visible solo en maestro.
  - `colGrado` (nueva): VBox dos labels (patrón visual de `colFecha`): arriba grande el `gradoPropio` (o "—"), debajo pequeño gris `"prov: " + gradoProveedor` si existe. Visible solo en maestro.
  - `colFecha`: para `TelefonoInventario` una sola línea con `ultimaActividad` formateada (`FORMATO_FECHA`) o "—"; cabecera dinámica: en `aplicarColumnasMaestro()` → `colFecha.setText("Última actividad")`, en `aplicarColumnasDetalle()` → `colFecha.setText("Fechas")`.
  - `colComponente` ("Trabajos" en maestro): `t.getResumenTipos()` (+ sufijo ` · N abiertos` si `t.getTrabajosAbiertos() > 0`).
  - `colEstado`: badges por `UbicacionTexto.estado(t)`: "Recibido" azul (`#E3F2FD`/`#1565C0`), "En reparación" ámbar (`Colores.FILA_INCIDENCIA_BG`/`FILA_INCIDENCIA_BRD`), "Histórico" gris (`#E8EAF0`/`#586376`); resto de estados (F2b) gris. La rama detalle (Incidencia/Resuelta/Normal de `ReparacionResumen`) NO cambia.
  - `colUbicacion` (nueva): `UbicacionTexto.ubicacion(t)` con `labelExpandible`. Visible solo en maestro.
  - `colLote` (nueva): VBox dos labels: `batchNumber` (o "—") y debajo pequeño gris `proveedor`. Visible solo en maestro.
  - `colObservacionTelefono`, `colCliente`: leen de `t.getObservacion()` / `t.getCliente()`.
  - `colRevision`: el toggle usa `t.isRevisionLogistica()`, `t.isTieneAsignaciones()`, `t.getTelefonoUpdatedAt()` — misma lógica.
  - `aplicarColumnasMaestro()/aplicarColumnasDetalle()`: añadir `colStorage/colColor/colGrado/colUbicacion/colLote` al grupo visible-en-maestro/oculto-en-detalle.
- Filas (`configurarFilas().aplicarEstilo`): la rama `GrupoImei` pasa a `TelefonoInventario` con el mismo estilo (borde izquierdo rojo si `getIncAbiertas() > 0`, azul oscuro si no).

- [ ] **Paso 5: Drill-down, menú contextual, copiar celda y CSV.**
  - `mostrarDetalle(GrupoImei)` → `mostrarDetalle(String imei)` (el cuerpo ya solo usa el IMEI; actualizar los 2 call-sites).
  - Menú contextual: `editarObs`/`editarCli` reciben `TelefonoInventario` (`abrirDialogoObservacionTelefono(TelefonoInventario)` usa `getObservacion()/getImei()/getTelefonoUpdatedAt()`; `editarCli` igual con `getCliente()`). La condición `esGrupo` pasa a `getItem() instanceof TelefonoInventario`.
  - `textoDeCelda`: rama `GrupoImei` → `TelefonoInventario` añadiendo las columnas nuevas (storage/color/grado/ubicación/lote).
  - `exportarCSV` maestro: cabeceras `"IMEI","Modelo","Storage","Color","Grado propio","Grado proveedor","Estado","Ubicación","Lote","Proveedor","Última actividad","Reparaciones","Glass","Pulidos","Abiertos","Inc. abiertas","Observación","Cliente","Revisión logística"` con los campos correspondientes de `TelefonoInventario` (fechas con `fmt`, `CsvExporter.textoForzado(imei)`).
  - Borrar `models/GrupoImei.java` y `GrupoImeiTest.java`; quitar el import en `AgrupadoController`.

- [ ] **Paso 6: Compilar + suite + commit (repo RAÍZ)**

Run: `mvn -q test` → BUILD SUCCESS, 0 referencias a `GrupoImei` (`grep -r GrupoImei gestion-reparaciones-cliente/src` vacío).

```bash
git add -A gestion-reparaciones-cliente/src && git commit -m "feat(inventario): la vista IMEIs pasa a inventario completo de teléfonos con ubicación derivada (drill-down intacto)"
```

---

### Tarea 15: Cliente — filtros nuevos del inventario (estado, ubicación, lote, modelo)

**Files:**
- Modify: `src/main/resources/views/AgrupadoView.fxml`, `src/main/java/com/reparaciones/controllers/AgrupadoController.java`

**Interfaces:**
- Consumes: `LoteDAO.getAll()` (Tarea 11), etiquetas de `UbicacionTexto`.
- Produces: filtros multiselección por Estado / Ubicación / Lote / Modelo en el maestro (spec §4: "filtros por estado, ubicación, lote, cliente, modelo, IMEI-pegado" — cliente e IMEI ya existen), ocultos en modo detalle, incluidos en `limpiarFiltros()`.

- [ ] **Paso 1: FXML** — en el `FlowPane` de filtros, tras `filtroCliente`: `<MultiSelectComboBox fx:id="filtroEstado" prefWidth="130"/>`, `<MultiSelectComboBox fx:id="filtroUbicacion" prefWidth="140"/>`, `<MultiSelectComboBox fx:id="filtroLote" prefWidth="150"/>`, `<MultiSelectComboBox fx:id="filtroModelo" prefWidth="140"/>`.

- [ ] **Paso 2: Controller.** Campos: `@FXML private MultiSelectComboBox<String> filtroEstado, filtroUbicacion, filtroModelo; @FXML private MultiSelectComboBox<Lote> filtroLote;` + sets de selección (`estadosFiltro`, `ubicacionesFiltro`, `idsLoteFiltro:Set<Integer>`, `modelosFiltro:Set<String>`) + `StringProperty` de etiqueta y `MultiSelectDropdown.Handle` por filtro (patrón EXACTO de `filtroCliente`).
  - `filtroEstado`: opciones fijas F2a `List.of("Recibido", "En reparación", "Histórico")` — se comparan contra `UbicacionTexto.estado(t)`.
  - `filtroUbicacion`: opciones fijas `List.of("Almacén", "Reparaciones", "Fuera / —")`; predicado: `"Fuera / —"` casa con `t.getUbicacion() == null`; las otras contra el nivel padre (`UBICACIONES` de `UbicacionTexto` — exponer helper `UbicacionTexto.padre(t)` que devuelva "Almacén"/"Reparaciones"/… sin subs ni ⏳).
  - `filtroLote`: se puebla en `cargar()` con `new LoteDAO().getAll()` (mismo hilo que el resto de la carga); `Lote::toString` como etiqueta; predicado por `t.getIdLote()`.
  - `filtroModelo`: se puebla en `poblarFiltroCliente()` (renombrar a `poblarFiltrosMaestro()`) con los modelos distintos presentes en el inventario, traducidos (`traducirModelo`) y ordenados; predicado contra `traducirModelo(t.getModelo())`.
  - Añadir los 4 predicados a la cadena del filtro maestro (Tarea 14 Paso 3), los 4 clears a `limpiarFiltros()`, y ocultar/mostrar los 4 combos en `adaptarFiltrosDetalle()/adaptarFiltrosMaestro()` (mismo mecanismo que `filtroCliente`).
  - **Además (petición del usuario 2026-07-08):** ocultar `filtroTecnico` (visible+managed false) cuando `rol == Rol.TECNICO` — el técnico solo carga sus propios trabajos, así que ese filtro no aporta nada en su perfil. Hacerlo en `configurar(Rol)` o en `adaptarFiltrosMaestro()` según encaje mejor.

- [ ] **Paso 3: Compilar + suite + commit (repo RAÍZ)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(inventario): filtros por estado, ubicación, lote y modelo en la vista IMEIs"
```

---

### Tarea 16: Cliente — edición de atributos + acciones nuevas en el log

**Files:**
- Modify: `src/main/java/com/reparaciones/controllers/AgrupadoController.java`, `src/main/java/com/reparaciones/controllers/LogController.java`

**Interfaces:**
- Consumes: `TelefonoDAO.actualizarAtributos` (Tarea 11), `SelectorModeloDialog`.
- Produces: ítem "Editar atributos" en el menú contextual del maestro (solo SUPERTECNICO) — mata el workaround "Removed" de la otra plataforma (spec §4).

- [ ] **Paso 1: Diálogo de atributos en `AgrupadoController`.** Nuevo `MenuItem editarAtr = new MenuItem("Editar atributos");` en `configurarFilas()`, visible con la misma condición que `editarCli` (`esSuper && esTelefono && modoActual == Modo.MAESTRO`). Handler `abrirDialogoAtributos(TelefonoInventario t)` (patrón de `abrirDialogoObservacionTelefono`):

```java
private void abrirDialogoAtributos(TelefonoInventario t) {
    final String[] modeloSel = { t.getModelo() };
    Label lblModelo = new Label(t.getModelo() != null && !t.getModelo().isEmpty()
            ? FormularioReparacionController.traducirModelo(t.getModelo()) : "—");
    Button btnModelo = new Button("Cambiar…");
    btnModelo.setOnAction(e -> SelectorModeloDialog.elegir(modeloSel[0]).ifPresent(m -> {
        modeloSel[0] = m;
        lblModelo.setText(FormularioReparacionController.traducirModelo(m));
    }));
    HBox filaModelo = new HBox(8, new Label("Modelo:"), lblModelo, btnModelo);
    filaModelo.setAlignment(Pos.CENTER_LEFT);

    TextField tfStorage = new TextField(t.getStorageGb() == null ? "" : String.valueOf(t.getStorageGb()));
    tfStorage.setPromptText("GB (vacío = sin dato)");
    tfStorage.textProperty().addListener((obs, o, n) -> { if (!n.matches("\\d*")) tfStorage.setText(o); });
    TextField tfColor = new TextField(t.getColor() != null ? t.getColor() : "");
    TextField tfGradoProv = new TextField(t.getGradoProveedor() != null ? t.getGradoProveedor() : "");
    ComboBox<String> cbGradoPropio = new ComboBox<>(FXCollections.observableArrayList("—", "C", "B", "A-", "A", "A+"));
    cbGradoPropio.setValue(t.getGradoPropio() != null ? t.getGradoPropio() : "—");

    Button btnGuardar = new Button("Guardar");
    btnGuardar.setMaxWidth(Double.MAX_VALUE);
    btnGuardar.setStyle("-fx-background-color: " + Colores.FILA_REPARADO_ICO +
            "; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4; -fx-padding: 8; -fx-cursor: hand;");

    VBox form = new VBox(8, filaModelo,
            new Label("Storage (GB)"), tfStorage,
            new Label("Color"), tfColor,
            new Label("Grado proveedor"), tfGradoProv,
            new Label("Grado propio (chasis)"), cbGradoPropio,
            btnGuardar);
    form.setPadding(new Insets(16));
    form.setStyle("-fx-background-color: " + Colores.FONDO_INPUT + "; -fx-background-radius: 8;");
    form.setPrefWidth(420);

    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle("Editar atributos — IMEI " + t.getImei());
    dialog.getDialogPane().setContent(form);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    btnGuardar.setOnAction(e -> {
        Integer storage = tfStorage.getText().isBlank() ? null : Integer.valueOf(tfStorage.getText().trim());
        String gradoPropio = "—".equals(cbGradoPropio.getValue()) ? null : cbGradoPropio.getValue();
        try {
            telefonoDAO.actualizarAtributos(t.getImei(), modeloSel[0], storage,
                    tfColor.getText().isBlank() ? null : tfColor.getText().trim(),
                    tfGradoProv.getText().isBlank() ? null : tfGradoProv.getText().trim(),
                    gradoPropio, t.getTelefonoUpdatedAt());
            dialog.close();
            cargar();
        } catch (com.reparaciones.utils.StaleDataException ex) {
            Alertas.mostrarError("El teléfono fue modificado por otro usuario. Se recargan los datos.");
            dialog.close();
            cargar();
        } catch (SQLException ex) {
            Alertas.mostrarError("No se pudo guardar: " + ex.getMessage());
        }
    });
    dialog.showAndWait();
}
```

- [ ] **Paso 2: `LogController.TIPOS_ACCION`** — añadir tras `"MARCAR_REVISION", "QUITAR_REVISION",` la línea `"IMPORTAR_LOTE", "EDITAR_ATRIBUTOS",`. Si `LogControllerTest` fija la lista, actualizarlo en el mismo commit.

- [ ] **Paso 3: Compilar + suite + commit (repo RAÍZ)**

Run: `mvn -q test` → BUILD SUCCESS.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(inventario): edición de atributos del teléfono (modelo, storage, color, grados) con lock optimista"
```

---

### Tarea 17: Cierre — suites, arranque del servidor, smoke manual y MER

**Files:**
- Modify: `Apuntes/Tabla BBDD(Corregido).drawio` (añadir Lote, Movimiento_telefono, Modelo_equivalencia y columnas nuevas de Telefono — es XML de draw.io: añadir las celdas/tabla siguiendo el formato de las existentes; si el formato interno resulta demasiado frágil, AVISAR al usuario para que lo actualice él y no romper el fichero)

- [ ] **Paso 1: Suites completas en ambos repos**

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-cliente" && mvn -q test
cd "c:/Users/info/Documents/ProgramaReparaciones/gestion-reparaciones-servidor" && mvn -q test && mvn -q -DskipTests package
```

Esperado: todo verde y `BUILD SUCCESS`.

- [ ] **Paso 2: PARADA — coordinación con el usuario (no automatizable).** Avisar al usuario de que:
  1. Aplique `sql/migracion-f2a-lotes.sql` en MariaDB (`gestion_reparaciones`).
  2. Arranque el servidor (`mvn spring-boot:run` o el jar) para **validar el contexto Spring** (regla: el servidor no tiene test de contexto; los beans nuevos — LoteDAO, MovimientoTelefonoDAO, EquivalenciaModeloDAO, LoteImportService, LoteController, EquivalenciaModeloController — solo se validan arrancando). Confirmar en el log `Started App`.

- [ ] **Paso 3: Smoke manual guiado (con la app cliente arrancada por el usuario, rol SUPERTECNICO):**
  1. Vista IMEIs: se ve el inventario con históricos (estado "Histórico", ubicación "—" o "Reparaciones → …" si tienen trabajo abierto); drill-down de un IMEI con trabajos intacto; volver restaura selección.
  2. Importar `Hy5 - 2026.07.06.xlsx` (Descargas): vista previa con 1 lote (batch 1445947), proveedor Hy5 auto-casado, ~395 nuevos; si aparece un modelo sin mapear, resolverlo y comprobar que se recuerda (reabrir la vista previa). Confirmar → los teléfonos aparecen RECIBIDO / Almacén con su lote.
  3. Re-importar el MISMO fichero: todos deben salir como CONFLICTO (0 importables).
  4. Alta manual: lote pequeño con 2 IMEIs pegados → aparecen en el inventario.
  5. Editar atributos de un teléfono (modelo/color/storage/grados) y verificar el cambio y el log (acciones IMPORTAR_LOTE / EDITAR_ATRIBUTOS en la vista Log).
  6. Filtros: estado, ubicación, lote, modelo, cliente e IMEIs pegados; CSV del maestro con las columnas nuevas.
  7. Con TECNICO y ADMIN: la vista es de consulta (sin botones de importar ni menús de edición).
- [ ] **Paso 4: MER** — actualizar el drawio (o avisar, según el criterio del header de esta tarea) y commit final (repo RAÍZ):

```bash
cd "c:/Users/info/Documents/ProgramaReparaciones" && git add Apuntes/ docs/ && git commit -m "docs(f2a): MER actualizado con Lote, Movimiento_telefono y ciclo de vida del teléfono"
```

- [ ] **Paso 5: NO mergear ni pushear.** Presentar al usuario el resumen de las dos ramas y esperar su OK (skill: superpowers:finishing-a-development-branch).

## Self-review (hecho al escribir el plan)

- Cobertura spec §5 F2a: esquema BD (T1) ✓ · importador con vista previa (T5,T6,T8–T12) ✓ · alta manual (T13) ✓ · vista de teléfonos con edición de atributos y filtros (T14–T16) ✓ · estados RECIBIDO y derivación básica (T2,T4) ✓. La tabla `Revision`, la ficha, OK/bloqueo/desguace/enviar/devolución, la vista de lotes con %, y retirar el check antiguo son F2b/F2c — fuera de alcance, correcto.
- Decisiones de spec respetadas: solo Status=0 entra con aviso del resto (T10) · duplicados finales→re-entrada / activos→conflicto (T10, `VerificacionImei.esActivo`) · multi-batch → un lote por batch (T10) · parser en cliente / servidor agnóstico (T8/T6) · equivalencias recordadas (T7/T9/T12) · ubicación derivada en UNA función con punto de enchufe Pedido (T2) · movimientos append-only desde el primer alta (T6) · precio por teléfono para F4 (columnas PRECIO_COMPRA*, T1/T6) · históricos fuera del ciclo (ESTADO null, T2/T4).
- Consistencia de tipos revisada: nombres JSON idénticos en DTOs servidor (T4/T5/T6/T7) y cliente (T10 Paso 1/T11); `mostrarDetalle(String)` coherente entre T14 pasos 4-5; `UbicacionTexto.padre` se define en T15 (añadirlo a `UbicacionTexto` al hacer esa tarea).
