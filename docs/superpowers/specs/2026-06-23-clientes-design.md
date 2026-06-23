# Diseño: Gestión de clientes y vínculo con teléfonos

**Fecha:** 2026-06-23
**Estado:** Aprobado para planificación

## Objetivo

Introducir el concepto de **cliente** en el sistema de reparaciones. Hasta ahora
no existe: la tabla `Telefono` solo guarda `IMEI`, `MODELO` y `OBSERVACION`. Se
quiere:

1. Un **catálogo de clientes** gestionable (alta, edición, activar/desactivar).
2. Asociar un cliente a cada teléfono.
3. Elegir el cliente en el **modal de asignación** por lotes.
4. Mostrar el cliente como columna en la **tabla de asignaciones** y en la
   **vista agrupada del historial** (no en la vista plana).
5. Poder **editar** el cliente de un teléfono después, desde la tabla de
   asignaciones y desde la vista agrupada del historial.

## Decisiones tomadas

| Decisión | Elección |
|----------|----------|
| Naturaleza del cliente | Entidad propia con catálogo (no texto suelto) |
| Datos del cliente | Solo nombre (+ id, + activo), calcado de `Tecnico` |
| Vínculo cliente–reparación | FK `ID_CLI` en `Telefono` (el cliente es del dispositivo) |
| Gestión del catálogo | Pantalla CRUD propia (como Técnicos/Proveedores) |
| Entrada en el modal | Cliente **por IMEI** en el formulario por-entrada (se arrastra del IMEI anterior, editable por entrada y desde la pila), elegido del catálogo |
| Crear cliente al vuelo en el modal | No; solo se elige de la lista |
| Selector de cliente | Combo con buscador (patrón del buscador de modelo/SKU) |
| Obligatoriedad | Opcional al asignar |
| Urgente al asociar cliente | Sí por defecto al asignar, editable después |
| Columna en tablas | Columna propia "Cliente" en Asignaciones y en Historial **agrupado** (no en la vista plana) |
| Edición posterior | Sí, por IMEI, desde Asignaciones y desde Historial agrupado (con confirmación); sin multi-selección en v1 |
| Permisos | Escritura de cliente (catálogo, asignación, cambio por IMEI) y edición de observación: **SUPERTECNICO únicamente**. ADMIN y demás roles: solo lectura |
| Auditoría | Log de actividad para todas las escrituras nuevas |
| Concurrencia | Bloqueo optimista completo (`UPDATED_AT` + `StaleDataException`/409) en `Cliente` y `Telefono` |

### Consecuencia asumida del vínculo en `Telefono`

Como el cliente cuelga del IMEI (no de cada reparación), cambiar el cliente de un
teléfono afecta a **todas** sus reparaciones (pasadas y presentes), porque todas
lo leen vía `JOIN`. Se acepta de forma consciente.

## Modelo de datos

Tabla nueva calcada de `Tecnico`, más una FK nullable en `Telefono`:

```sql
CREATE TABLE Cliente (
    ID_CLI     INT          NOT NULL AUTO_INCREMENT,
    NOMBRE     VARCHAR(150) NOT NULL,
    ACTIVO     BOOLEAN      NOT NULL DEFAULT TRUE,
    UPDATED_AT TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_CLI)
);

ALTER TABLE Telefono
    ADD COLUMN ID_CLI INT NULL AFTER OBSERVACION,
    ADD COLUMN UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT fk_telefono_cliente FOREIGN KEY (ID_CLI) REFERENCES Cliente (ID_CLI);
```

`Cliente` y `Telefono` llevan `UPDATED_AT` para el bloqueo optimista (ver
sección **Concurrencia**). En `crear_bd.sql`, `Telefono` se crea ya con ambas
columnas.

Entrega:
- `sql/migracion-cliente.sql` para entornos existentes (crear tabla + alterar
  `Telefono` + sembrar WEB/OTRO).
- Actualizar `sql/crear_bd.sql`: añadir `Cliente` al bloque de `DROP TABLE`
  (en orden correcto respecto a la FK), su `CREATE TABLE`, la columna +
  constraint en `Telefono`, y el seed de WEB/OTRO.

`ID_CLI` nullable: un teléfono puede no tener cliente asociado.

**Seed de clientes genéricos:** se insertan por defecto dos clientes activos:

```sql
INSERT INTO Cliente (NOMBRE, ACTIVO) VALUES ('WEB', TRUE), ('OTRO', TRUE);
```

### Clientes genéricos (WEB / OTRO)

`WEB` y `OTRO` son clientes del catálogo como cualquier otro, pero **genéricos**:
no identifican a una persona concreta. Cuando un teléfono se asocia a uno de
ellos, el detalle (quién es realmente, referencia del pedido web, etc.) se anota
en la **observación del teléfono** (`Telefono.OBSERVACION`), que es un campo
**per-IMEI** mostrado en la **vista agrupada** (`colObservacionTelefono`).

Importante — no confundir dos campos distintos de la BD:

| Campo | Nivel | Vista | Uso |
|-------|-------|-------|-----|
| `Telefono.OBSERVACION` | Teléfono (per-IMEI) | Agrupada | Nota del dispositivo; aquí va el detalle del cliente WEB/OTRO |
| `Reparacion_componente.OBSERVACIONES` | Reparación/componente | Detalle | Notas de la reparación concreta (p. ej. "pantalla rota") |

No se añade ningún campo nuevo para esto: se reutiliza `Telefono.OBSERVACION`,
que ya existe, ya se muestra en la vista agrupada y ya es editable
(`actualizarObservacion`).

## Componentes

### Servidor (`gestion-reparaciones-servidor`)

- **`model/Cliente.java`** — `idCli`, `nombre`, `activo`, `updatedAt`,
  getters/setters y constructores (espejo de `Tecnico` + `updatedAt`).
- **`dao/ClienteDAO.java`** — `getAll()`, `getActivos()`, `insertar(nombre)`,
  `editar(idCli, nombre, updatedAt)`, `setActivo(idCli, activo, updatedAt)`,
  `tieneTelefonos(idCli)`. `editar`/`setActivo` aplican bloqueo optimista:
  `UPDATE ... WHERE ID_CLI=? AND UPDATED_AT=?`; si afecta 0 filas, lanzan
  `StaleDataException`.
- **`controller/ClienteController.java`** (`/api/clientes`):
  - `GET /api/clientes` → todos (cualquier rol autenticado)
  - `GET /api/clientes/activos` → solo activos (para el desplegable)
  - `POST /api/clientes` → alta `{nombre}` → log `CREAR_CLIENTE`
  - `PUT /api/clientes/{id}` → editar `{nombre, updatedAt}` → log `EDITAR_CLIENTE`
  - `PATCH /api/clientes/{id}/activo` → `{activo, updatedAt}` → log
    `ALTA_CLIENTE`/`BAJA_CLIENTE`
  - `GET /api/clientes/{id}/tiene-telefonos` → boolean
  - Escritura restringida con `@PreAuthorize` a **SUPERTECNICO únicamente**. Los
    handlers de escritura reciben `@AuthenticationPrincipal` para el log y
    devuelven `409 CONFLICT` ante `StaleDataException`.
- **`dao/TelefonoDAO.java`**:
  - `insertar(imei, modelo, idCli)` — ampliar el `INSERT ... ON DUPLICATE KEY
    UPDATE` para incluir `ID_CLI = COALESCE(?, ID_CLI)` (un `idCli` null no borra
    el cliente existente del IMEI). El upsert es atómico: no necesita bloqueo
    optimista.
  - `actualizarCliente(imei, idCli, updatedAt)` — `UPDATE Telefono SET ID_CLI=?
    WHERE IMEI=? AND UPDATED_AT=?` (null = quitar); `StaleDataException` si 0
    filas.
  - `actualizarObservacion(imei, observacion, updatedAt)` — añadir el predicado
    `AND UPDATED_AT=?` (retrofit a bloqueo optimista).
  - `actualizarRevisionLogistica` — mismo retrofit de `UPDATED_AT` por
    coherencia (toca `Telefono`).
- **`controller/TelefonoController.java`**:
  - Ampliar el `record` de POST para aceptar `idCli` opcional. El POST no lleva
    `@PreAuthorize` (también crea teléfonos sin cliente), pero **gatea la
    asignación de cliente**: si llega `idCli` no nulo y el rol no es
    SUPERTECNICO, responde `403 FORBIDDEN`. Al asignar con cliente, log
    `ASIGNAR_CLIENTE` (`IMEI: x, ID_CLI: y`).
  - `PATCH /api/telefonos/{imei}/cliente` con `{idCli, updatedAt}` (null =
    quitar), restringido a **SUPERTECNICO** → log `CAMBIAR_CLIENTE`.
  - `actualizarObservacion`: se mantiene en **SUPERTECNICO** (`hasRole`); se le
    añade `updatedAt` (bloqueo optimista) y log `EDITAR_OBSERVACION` (hoy no se
    registra).
  - Todos los endpoints de escritura devuelven `409 CONFLICT` ante
    `StaleDataException`.
- **`dao/ReparacionDAO.java` — alta de asignación con urgencia:**
  - `insertarAsignacion` pasa a aceptar un flag `urgente` (por defecto `false`),
    que se persiste en `Reparacion.URGENTE` al crear la fila. Alternativamente,
    reutilizar `actualizarUrgente` tras el insert; se prefiere el parámetro para
    no hacer dos viajes.
- **`dao/ReparacionDAO.java`** — en las consultas que alimentan las vistas donde
  se muestra el cliente: las de **asignaciones** (`ASIGNACION_SELECT`, usado por
  `getAsignaciones`/`getAsignacionById`/`getAsignacionesPorImei`) y la de
  **historial** (`HISTORIAL_SELECT`, vista agrupada):
  - Añadir `LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI` (el `LEFT JOIN
    Telefono tel` ya existe).
  - Añadir `cli.NOMBRE AS CLIENTE` al `SELECT`.
  - En las variantes con `GROUP BY` (las de asignaciones), añadir `cli.NOMBRE`
    al `GROUP BY`.
  - Mapear `CLIENTE` en `RESUMEN_MAPPER` hacia `ReparacionResumen.setCliente(...)`.

### Cliente (`gestion-reparaciones-cliente`)

- **`models/Cliente.java`** — espejo del modelo servidor (`idCli`, `nombre`,
  `activo`, `updatedAt`), con `toString()` devolviendo el nombre (para el
  selector).
- **`dao/ClienteDAO.java`** — espejo vía `ApiClient`: `getAll`, `getActivos`,
  `insertar`, `editar(idCli, nombre, updatedAt)`, `setActivo(idCli, activo,
  updatedAt)`, `tieneTelefonos`. `editar`/`setActivo` propagan
  `StaleDataException` (igual que `CompraComponenteDAO`).
- **`dao/TelefonoDAO.java`**:
  - `insertar(imei, modelo, idCli)` (sobrecarga; mantener las existentes).
  - `actualizarCliente(imei, idCli, updatedAt)` → `PATCH
    /api/telefonos/{imei}/cliente`, propaga `StaleDataException`.
  - `actualizarObservacion(imei, observacion, updatedAt)` — añadir `updatedAt`.
- **`models/ReparacionResumen.java`** — campo `cliente` (String) + getter/setter,
  al estilo de `observacionTelefono`.
- **`models/GrupoImei` y `ReparacionResumen`** deben exponer el `updatedAt` del
  **teléfono** (no solo el de la reparación) para poder enviar el `updatedAt`
  esperado al editar cliente/observación desde la vista agrupada. Se añade al
  `SELECT` (`tel.UPDATED_AT`) y al modelo. Manejar `StaleDataException` en los
  controladores que editan (recargar y avisar), como ya se hace en otras tablas.

### Pantalla de gestión de clientes

- Vista FXML + controller nuevos con tabla de clientes y acciones de alta,
  edición y activar/desactivar, siguiendo el patrón de gestión ya existente en
  la app.
- **Borrado seguro:** no se borra un cliente en uso; se **desactiva**
  (`ACTIVO=false`). Los inactivos no aparecen en el desplegable pero siguen
  mostrándose en históricos.
- **Ubicación: nuevo módulo de nivel superior "Clientes" en la navbar**
  (`MainView.fxml` / `MainController`), como cuarto botón junto a
  Reparaciones / Stock / Estadísticas:
  - Nuevo `Button fx:id="btnClientes"` en la `HBox.nav-switch`, con su handler
    `#mostrarClientes` que carga la vista en el `contenedor` central (mismo
    mecanismo `mostrarVista(...)` que los otros módulos).
  - El botón es **visible para todos los roles** (SUPERTECNICO, ADMIN y
    TECNICO).
- **Acceso por rol a la vista:**
  - **SUPERTECNICO: acceso completo** (alta, edición, activar/desactivar).
  - **ADMIN y TECNICO: solo lectura.** Ven el listado de clientes pero no se
    les muestran las acciones de escritura (modo `soloLectura`).
  - En el servidor, los GET de `/api/clientes` son accesibles a todos los roles
    autenticados; la escritura (POST/PUT/PATCH) está restringida a
    **SUPERTECNICO** vía `@PreAuthorize("hasRole('SUPERTECNICO')")`.
- **Nota de escalabilidad (ERP):** se prevé que el área de clientes crezca
  (fichas con contacto, historial por cliente, filtros, etc.). El diseño actual
  es deliberadamente mínimo (solo nombre); el módulo de navbar y la entidad se
  conciben como **punto de anclaje** de ese futuro módulo. No se implementa nada
  de eso ahora.

### Modal de asignación (`PendientesSuperTecnicoController.abrirFormularioAsignacion`)

- Añadir un **selector de cliente con buscador** ("Cliente (opcional)") en el
  **formulario por-IMEI** del modal (junto a modelo/técnicos/comentario), no en
  la cabecera: el cliente es **por entrada** (`EntradaAsignacion.cliente`). Se
  **arrastra** del IMEI anterior (como los técnicos) pero es editable por IMEI, y
  se carga al clicar una entrada de la pila. Reutiliza el patrón/estilo del
  **selector de componentes (SKU) del modal de pedidos** (`TextField` píldora
  oscura + `Popup` + `ListView` filtrado por texto). No es un `ComboBox` plano.
- En el guardado final (`btnGuardar`), por cada entrada se pasa su propio
  `idCli`: `telefonoDAO.insertar(e.imei, e.modeloCode, e.cliente?idCli:null)`. Si
  la entrada no tiene cliente → `null` → no se toca el cliente previo del IMEI.
- **Urgente automático:** cada asignación cuyo IMEI lleve cliente se crea con
  `URGENTE=true`
  (`insertarAsignacion(imei, idTec, comentario, true)`). Aplica **solo en el
  momento de la creación**; queda editable luego con el menú contextual
  "Marcar/Quitar urgente" ya existente. Editar el cliente de un teléfono más
  tarde **no** re-marca urgente.
- **Recordatorio para clientes genéricos:** si alguna entrada del lote lleva
  cliente `WEB` u `OTRO`, al guardar se muestra un aviso suave (no bloqueante)
  recordando añadir el detalle en la observación del teléfono desde la vista
  agrupada. No se fuerza
  ni se rellena automáticamente la observación.

### Dónde se muestra la columna "Cliente"

El cliente es un atributo del IMEI, así que solo se muestra donde el IMEI es la
unidad significativa:

| Vista | Controlador | Columna Cliente | Editar aquí |
|-------|-------------|-----------------|-------------|
| Asignaciones (pendientes, plana) | `PendientesSuperTecnicoController` | **Sí** | **Sí** |
| Historial — vista **agrupada** (por IMEI) | `ReparacionControllerSuperTecnico` (modo MAESTRO) | **Sí** | **Sí** |
| Historial — vista **plana** (PLANO) | `ReparacionControllerSuperTecnico` | **No** (redundante por fila) | No |

- Aunque la pestaña de Asignaciones es plana (una fila por asignación), sí
  muestra el cliente: es donde el operario ve "para quién" se repara el
  teléfono.
- Columna estrecha, con la política de redimensionado actual. Se descarta
  apilar el cliente bajo el IMEI/modelo: las tablas son de una sola línea y la
  función de *copiar celda / menú contextual por columna* localiza la columna
  por su posición horizontal, que una segunda línea rompería.

### Edición posterior del cliente

- Se edita **siempre a nivel de IMEI** (no por reparación individual), mediante
  menú contextual **"Editar cliente"** (mismo patrón que "Editar comentario"):
  - En la pestaña de **Asignaciones** (cubre IMEIs que solo tienen pendientes y
    aún no aparecen en el historial).
  - En la **vista agrupada del Historial** (modo MAESTRO).
- El menú abre el mismo selector con buscador (clientes activos + opción
  "Sin cliente") y llama a `telefonoDAO.actualizarCliente(imei, idCli, updatedAt)`.
- **Confirmación obligatoria:** antes de aplicar, un diálogo avisa de que el
  cambio afecta a todas las asignaciones del IMEI, indicando cuántas:
  *"Se cambiará el cliente de las N asignaciones del IMEI XXXX"*. Así el efecto
  por-IMEI es explícito (no engañoso) incluso al editar desde una fila de la
  vista plana de Asignaciones.
- Es un único `UPDATE` atómico sobre `Telefono`: no hay estados parciales ni
  inconsistencia posible entre asignaciones del mismo IMEI.
- Sin multi-selección en v1 (editar un IMEI ya cambia todas sus asignaciones a
  la vez). Corregir un lote con varios IMEIs distintos se hace IMEI por IMEI;
  la multi-edición puede añadirse después.
- En la vista plana del historial no se ofrece la acción (no muestra la
  columna).

## Auditoría (logs)

Toda escritura nueva registra un evento en `Log_Actividad` vía
`logDao.insertar(principal.getIdUsu(), ACCION, detalle)`, siguiendo la convención
existente (`CREAR_TECNICO`, `ALTA/BAJA_COMPONENTE`, `MARCAR_REVISION`…):

| Acción | Cuándo | Detalle |
|--------|--------|---------|
| `CREAR_CLIENTE`     | alta de cliente               | `NOMBRE: x` |
| `EDITAR_CLIENTE`    | editar nombre                 | `ID_CLI: x, NOMBRE: viejo → nuevo` |
| `ALTA_CLIENTE`      | reactivar cliente             | `ID_CLI: x` |
| `BAJA_CLIENTE`      | desactivar cliente            | `ID_CLI: x` |
| `ASIGNAR_CLIENTE`   | asignar lote con cliente      | `IMEI: x, CLIENTE: y` |
| `CAMBIAR_CLIENTE`   | editar cliente de un IMEI     | `IMEI: x, CLIENTE: viejo → nuevo` |
| `EDITAR_OBSERVACION`| editar observación del teléfono | `IMEI: x` |

`EDITAR_OBSERVACION` se añade ahora (hoy `actualizarObservacion` no registra
nada). El resto de operaciones de reparación/asignación ya tienen sus logs.

## Concurrencia (bloqueo optimista)

Se aplica el patrón ya usado en `Componente`/`Compra_componente`: el registro
editable lleva `UPDATED_AT`; el cliente envía el `updatedAt` que tenía; el `UPDATE`
incluye `... WHERE clave=? AND UPDATED_AT=?`; si afecta 0 filas, el DAO lanza
`StaleDataException` y el endpoint responde `409 CONFLICT`. El cliente captura el
conflicto, recarga y avisa al usuario.

Alcance ("optimista completo"):

- **`Cliente`:** `editar` y `activar/desactivar` con `UPDATED_AT`.
- **`Telefono`:** `actualizarCliente` y `actualizarObservacion` con `UPDATED_AT`.
  Esto implica **retrofit** de `Telefono`: añadir la columna y migrar también
  `actualizarObservacion` y `actualizarRevisionLogistica` (que hoy no comprueban
  versión) al mismo patrón, para no dejar la tabla a medias.
- **Upsert de asignación** (`insertar` con `ON DUPLICATE KEY UPDATE ... COALESCE`):
  ya es atómico; no necesita `UPDATED_AT`.
- La vista agrupada debe transportar `tel.UPDATED_AT` para enviarlo al editar.

> Nota de fases: el retrofit de `UPDATED_AT`/bloqueo optimista en `Telefono`
> toca código preexistente (observación, revisión logística, upsert) más allá de
> clientes; en el plan de implementación va como **primera fase aislada**, antes
> de construir la funcionalidad de clientes encima.

## Casos límite

- **Lote sin cliente:** no borra clientes previos de los IMEIs (gracias a
  `COALESCE` en el `insertar`).
- **Desactivar cliente en uso:** permitido; desaparece del desplegable, se
  mantiene en históricos y en los teléfonos ya vinculados.
- **Editar cliente de un IMEI:** se refleja en todas sus reparaciones (asumido).
- **Borrar una asignación NO cambia el cliente:** el cliente vive en `Telefono`,
  no en la asignación. Solo se limpiaría si se borran *todas* las asignaciones
  del IMEI y el teléfono queda huérfano (se elimina). Por eso la corrección de un
  cliente equivocado se hace con "Editar cliente", no borrando asignaciones.
- **Permisos:** toda escritura de cliente (catálogo, asignación, cambio por IMEI)
  y edición de observación es **SUPERTECNICO únicamente**. ADMIN y TECNICO ven el
  catálogo y las columnas de cliente en todas las vistas, pero no pueden editar:
  la pantalla de gestión queda en modo solo-lectura y las acciones "Editar
  cliente" / "Editar observación" no se les muestran.
- **IMEI presente en ambas vistas:** un IMEI a medias (con reparaciones hechas
  —aparece en Historial— y otras pendientes —aparece en Asignaciones—) comparte
  el mismo y único cliente. Editarlo desde cualquiera de las dos vistas lo cambia
  para todo a la vez (un solo `UPDATE` sobre `Telefono`); ambas vistas reflejan
  el nuevo cliente al recargar.

## Fuera de alcance

- Datos de contacto del cliente (teléfono, email): el cliente es solo un nombre.
- Crear clientes al vuelo desde el modal de asignación.
- Filtrar las tablas por cliente (puede añadirse después si hace falta).
- Vincular el cliente a la reparación en vez de al teléfono.
