# Quitar los clientes ficticios de incidencias — Plan de ejecución

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quitar los clientes «Incidencias Amazon» e «Incidencias profesionales»: sus teléfonos quedan sin cliente, el
nombre pasa al Comentario de sus asignaciones abiertas, esas asignaciones pierden el urgente y los dos clientes se borran.

**Architecture:** Operación **solo de datos**, una vez. Un script SQL en `Apuntes/` que se pega bloque a bloque en la
consola de MariaDB: parámetros → comprobación de solo lectura → cambio en una transacción con `COMMIT` a mano →
comprobación posterior. El borrado de los clientes se hace desde la web (pestaña Clientes). Ensayo completo en
preproducción antes de producción.

**Tech Stack:** MariaDB 11 (consola `mariadb` dentro del contenedor), web del ERP.

**Spec:** `docs/superpowers/specs/2026-10-09-quitar-clientes-incidencias-design.md`.

## Global Constraints

- **Solo datos:** sin código, sin esquema, sin despliegue, sin versión.
- **Claude no hace SSH** a las máquinas: prepara y revisa; el usuario ejecuta en la consola y pega aquí la salida.
- **Repos públicos:** el script, los comandos de las máquinas y el registro de la ejecución van en `Apuntes/`, nunca en
  `docs/`.
- **Producción es el ERP real del taller:** copia a mano antes (`/usr/local/sbin/backup-erp.sh`), momento tranquilo y
  aviso previo a los técnicos.
- **Asignación abierta** = `ID_REP LIKE 'A%' AND FECHA_FIN IS NULL` (reparación `A…`, glass `AG…`, pulido `AP…`).
- **Comentario:** `<NOMBRE DEL CLIENTE> · <comentario anterior>`, o solo el nombre si estaba vacío; si ya contiene el
  nombre, no se toca.
- **Registro:** una línea `CAMBIAR_CLIENTE` por teléfono, detalle `IMEI: <imei>, ID_CLI: —`, motivo que empieza por
  `Limpieza de clientes ficticios:`, a nombre del usuario que ejecuta.
- **Urgente:** se quita en todas las asignaciones abiertas de esos teléfonos y se conserva `UPDATED_AT` (como
  `ReparacionDAO.propagarUrgente`).

---

### Task 1: Script SQL y nota en el plan maestro

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\quitar-clientes-incidencias.sql` (fuera de git)
- Modify: `C:\Users\dev\Documents\Apuntes\plan-futuro.md` (§2, «F2c — Ciclo completo»; fuera de git)
- Commit (repo raíz): este plan y el ajuste de la spec (§3.2, `UPDATED_AT` del urgente)

**Interfaces:**
- Produces: el script con los bloques **0** (parámetros), **1** (comprobación), **2** (cambio en transacción),
  **3** (después). Variables de sesión `@usuario`, `@ids` (ID de los dos clientes, separados por comas) e `@imeis`
  (IMEIs afectados, capturados al empezar el bloque 2). Cifras del bloque 1.4: `ABIERTAS`, `YA_LLEVAN_NOMBRE`,
  `URGENTES`, `TELEFONOS`.

- [ ] **Step 1: Crear el script**

Contenido completo de `Apuntes/quitar-clientes-incidencias.sql`:

```sql
-- Quitar los clientes ficticios de incidencias (2026-10-09)
-- Spec: docs/superpowers/specs/2026-10-09-quitar-clientes-incidencias-design.md (repo raiz)
-- Plan: docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md
--
-- Se pega en la consola de MariaDB BLOQUE A BLOQUE y en la MISMA sesion: usa variables de sesion
-- (@usuario, @ids, @imeis). Si se cierra la consola, volver a empezar por el bloque 0.
--
-- Los caracteres especiales van en hexadecimal para no depender de la terminal:
--   X'20C2B720' = " · " (separador del comentario)
--   X'E28094'   = "—"   (lo que escribe la web en el registro al dejar un IMEI sin cliente)

-- == Bloque 0: parametros ====================================================
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @usuario := 'NOMBRE_DE_USUARIO';  -- <- cambiar por el usuario de la web que firma el registro (el tuyo)

-- 0.1 Debe salir 1 fila
SELECT ID_USU, NOMBRE_USUARIO FROM Usuario WHERE NOMBRE_USUARIO = @usuario;

-- 0.2 Las cinco tablas deben salir con utf8mb4_unicode_ci.
--     Si salen con otra, cambiar la collation del SET NAMES de arriba por esa y volver a pegar el bloque 0.
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('Cliente', 'Telefono', 'Reparacion', 'Usuario', 'Log_Actividad');

-- == Bloque 1: comprobacion (solo lectura) ===================================
-- 1.1 Los clientes: deben salir exactamente los dos de incidencias
SELECT ID_CLI, NOMBRE, ACTIVO FROM Cliente WHERE NOMBRE LIKE '%incidencia%';
SELECT GROUP_CONCAT(ID_CLI ORDER BY ID_CLI) INTO @ids FROM Cliente WHERE NOMBRE LIKE '%incidencia%';
SELECT @ids AS IDS;
-- Si 1.1 sacara alguno de mas, fijar a mano los dos buenos (ejemplo: SET @ids := '7,9';) y repetir SELECT @ids.

-- 1.2 Telefonos de cada uno y cuantos tienen asignaciones abiertas
SELECT c.NOMBRE AS CLIENTE, COUNT(*) AS TELEFONOS,
       SUM(EXISTS (SELECT 1 FROM Reparacion r
                   WHERE r.IMEI = t.IMEI AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL)) AS CON_ABIERTAS
FROM Telefono t
JOIN Cliente c ON c.ID_CLI = t.ID_CLI
WHERE FIND_IN_SET(t.ID_CLI, @ids)
GROUP BY c.NOMBRE;

-- 1.3 Asignaciones abiertas de esos telefonos (reparacion A..., glass AG..., pulido AP...)
--     Guardar esta salida: es la referencia para volver atras.
SELECT r.ID_REP, r.IMEI, c.NOMBRE AS CLIENTE, tec.NOMBRE AS TECNICO, r.URGENTE, r.COMENTARIO_ASIGNACION
FROM Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente c ON c.ID_CLI = t.ID_CLI
LEFT JOIN Tecnico tec ON tec.ID_TEC = r.ID_TEC
WHERE FIND_IN_SET(t.ID_CLI, @ids) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL
ORDER BY r.IMEI, r.ID_REP;

-- 1.4 Cifras que el bloque 2 debe reproducir
SELECT COUNT(*) AS ABIERTAS,
       COALESCE(SUM(r.COMENTARIO_ASIGNACION LIKE CONCAT('%', c.NOMBRE, '%')), 0) AS YA_LLEVAN_NOMBRE,
       COALESCE(SUM(r.URGENTE), 0) AS URGENTES
FROM Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente c ON c.ID_CLI = t.ID_CLI
WHERE FIND_IN_SET(t.ID_CLI, @ids) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL;
SELECT COUNT(*) AS TELEFONOS FROM Telefono WHERE FIND_IN_SET(ID_CLI, @ids);

-- 1.5 Envios con esos clientes: debe ser 0.
--     Si da error "Table ... doesn't exist", tambien vale (no hay envios). Si da mas de 0, PARAR.
SELECT COUNT(*) AS ENVIOS FROM Envio WHERE FIND_IN_SET(ID_CLI, @ids);

-- == Bloque 2: cambio (una transaccion) ======================================
-- Pegar entero. Comparar cada "Changed" / "rows affected" con el bloque 1.4:
--   2.1 = ABIERTAS - YA_LLEVAN_NOMBRE    2.2 = URGENTES    2.3 = TELEFONOS    2.4 = TELEFONOS    2.5 QUEDAN = 0
-- y terminar a mano con COMMIT; (todo cuadra) o ROLLBACK; (algo no cuadra o ha salido cualquier ERROR:
-- la consola sigue con las siguientes sentencias aunque una falle).
-- Mientras la transaccion esta abierta, esas filas quedan bloqueadas: no dejarla abierta mas de un par de minutos.
START TRANSACTION;

SELECT GROUP_CONCAT(IMEI) INTO @imeis FROM Telefono WHERE FIND_IN_SET(ID_CLI, @ids);

-- 2.1 Nombre del cliente delante del comentario de las asignaciones abiertas
UPDATE Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente c ON c.ID_CLI = t.ID_CLI
SET r.COMENTARIO_ASIGNACION = CASE
      WHEN r.COMENTARIO_ASIGNACION IS NULL OR TRIM(r.COMENTARIO_ASIGNACION) = '' THEN c.NOMBRE
      ELSE CONCAT(c.NOMBRE, _utf8mb4 X'20C2B720' COLLATE utf8mb4_unicode_ci, r.COMENTARIO_ASIGNACION)
    END
WHERE FIND_IN_SET(t.ID_CLI, @ids) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL
  AND (r.COMENTARIO_ASIGNACION IS NULL OR r.COMENTARIO_ASIGNACION NOT LIKE CONCAT('%', c.NOMBRE, '%'));

-- 2.2 Quitar el urgente. UPDATED_AT se conserva, como hace la web (ReparacionDAO.propagarUrgente).
UPDATE Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
SET r.URGENTE = FALSE, r.UPDATED_AT = r.UPDATED_AT
WHERE FIND_IN_SET(t.ID_CLI, @ids) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL AND r.URGENTE = TRUE;

-- 2.3 Una linea CAMBIAR_CLIENTE por telefono, con el mismo detalle que pone la web (TelefonoController.actualizarCliente)
INSERT INTO Log_Actividad (ID_USU, NOMBRE_USUARIO, ACCION, DETALLE, MOTIVO)
SELECT u.ID_USU, u.NOMBRE_USUARIO, 'CAMBIAR_CLIENTE',
       CONCAT('IMEI: ', t.IMEI, ', ID_CLI: ', _utf8mb4 X'E28094' COLLATE utf8mb4_unicode_ci),
       CONCAT('Limpieza de clientes ficticios: ', c.NOMBRE,
              ' pasa al comentario de sus asignaciones abiertas y se les quita el urgente')
FROM Telefono t
JOIN Cliente c ON c.ID_CLI = t.ID_CLI
JOIN Usuario u ON u.NOMBRE_USUARIO = @usuario
WHERE FIND_IN_SET(t.ID_CLI, @ids);

-- 2.4 Telefonos sin cliente
UPDATE Telefono SET ID_CLI = NULL WHERE FIND_IN_SET(ID_CLI, @ids);

-- 2.5 Comprobar dentro de la transaccion
SELECT COUNT(*) AS QUEDAN FROM Telefono WHERE FIND_IN_SET(ID_CLI, @ids);

-- Si todas las cifras cuadran:  COMMIT;
-- Si alguna no cuadra:          ROLLBACK;

-- == Bloque 3: despues del COMMIT ============================================
-- 3.1 Sus asignaciones abiertas: URGENTE = 0 y el nombre del cliente al principio del comentario
SELECT r.ID_REP, r.IMEI, r.URGENTE, r.COMENTARIO_ASIGNACION
FROM Reparacion r
WHERE FIND_IN_SET(r.IMEI, @imeis) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL
ORDER BY r.IMEI, r.ID_REP;

-- 3.2 Lineas del registro de esta limpieza (= TELEFONOS)
SELECT COUNT(*) AS LINEAS FROM Log_Actividad
WHERE ACCION = 'CAMBIAR_CLIENTE' AND MOTIVO LIKE 'Limpieza de clientes ficticios:%';

-- Ahora borrar los dos clientes en la web: pestana Clientes -> Borrar (uno y otro).

-- 3.3 Tras borrarlos en la web: debe salir vacio
SELECT ID_CLI, NOMBRE FROM Cliente WHERE FIND_IN_SET(ID_CLI, @ids);
```

Por qué así (para quien revise):
- `SET NAMES … COLLATE utf8mb4_unicode_ci` (la de `crear_bd.sql`): las variables de usuario toman la collation de la
  conexión; si no coincide con la de las columnas, `u.NOMBRE_USUARIO = @usuario` da «Illegal mix of collations». El 0.2
  lo comprueba antes de tocar nada.
- Los literales con `·` y `—` en hexadecimal con introductor y `COLLATE` explícito: el resultado no depende de cómo
  envíe la terminal los caracteres, y el `COLLATE` explícito evita el mismo error en el `CONCAT`.
- `@ids` en vez de una tabla temporal: MariaDB no deja abrir dos veces una tabla temporal en la misma sentencia.
- `@imeis` se captura antes de quitar el cliente: después ya no hay forma de encontrarlos por cliente.

- [ ] **Step 2: Revisar el script contra la spec**

Comprobar uno a uno: §2 (comentario delante con ` · `, no tocar si ya lo contiene; urgente fuera en las tres
categorías; `CAMBIAR_CLIENTE` por teléfono con `IMEI: x, ID_CLI: —`), §3.1 (los cuatro puntos de la comprobación),
§3.2 (orden comentario → urgente → registro → teléfono, una transacción), §3.3 (borrado desde la web).

Comprobar que los únicos caracteres no ASCII del script están en comentarios:

Run: `grep -nP '[^\x00-\x7F]' /c/Users/dev/Documents/Apuntes/quitar-clientes-incidencias.sql`
Expected: solo líneas que empiezan por `--`.

- [ ] **Step 3: Nota en el plan maestro**

En `Apuntes/plan-futuro.md`, sección «F2c — Ciclo completo», añadir tras la línea de «Enviar a externo…»:

```markdown
- [ ] Devoluciones de venta (Amazon / profesional) en la web: hoy se apuntan a mano en el **Comentario de la asignación** (decisión 2026-10-09, al quitar los clientes ficticios «Incidencias Amazon» / «Incidencias profesionales»: spec `2026-10-09-quitar-clientes-incidencias-design.md`). Su sitio es el módulo de ubicaciones/devoluciones (`FLUJO_UBICACIONES_v17.drawio`, `Modulo Ubicaciones - Spec.md`) cuando llegue a la web
```

- [ ] **Step 4: Commit (repo raíz)**

```bash
git add docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md docs/superpowers/specs/2026-10-09-quitar-clientes-incidencias-design.md
git commit -m "docs: plan para quitar los clientes ficticios de incidencias"
```

---

### Task 2: Ensayo en preproducción

Preproducción se refrescó desde producción el 2026-10-09 a las 10:50 (`Apuntes/despliegue_preprod.md`, registro de ese
día): debería tener los dos clientes y sus teléfonos reales de esa hora. El ensayo es el procedimiento completo de
producción sobre esos datos.

**Files:**
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_preprod.md` (§Registro de sesiones: entrada nueva)

**Interfaces:**
- Consumes: el script de la Task 1 y el usuario administrador (mismo nombre en preprod y en producción; contraseña de
  preprod en `~/.env.e2e`, `ADMIN_USER`/`ADMIN_PASS`).
- Produces: las cifras del ensayo y, si hubo que tocarlo, el script corregido.

- [ ] **Step 1: Abrir la consola de MariaDB en preprod (usuario)**

Desde PowerShell: `ssh preprod`, y en la máquina la línea de consola de `Apuntes/despliegue_vdc_produccion.md`
§«Entrar en MariaDB sin escribir la contraseña» (la primera, con `-it`).

- [ ] **Step 2: Bloque 0 (usuario pega, Claude revisa)**

Con `NOMBRE_DE_USUARIO` sustituido por el nombre de usuario del administrador.
Expected: 0.1 una fila; 0.2 las cinco tablas con `utf8mb4_unicode_ci`.

- [ ] **Step 3: Bloque 1 (usuario pega la salida aquí, Claude la revisa)**

Expected: 1.1 exactamente dos clientes; `@ids` con sus dos ID; 1.5 = 0 (o «doesn't exist»). Anotar `ABIERTAS`,
`YA_LLEVAN_NOMBRE`, `URGENTES` y `TELEFONOS`.

Si la base no trae ningún caso de alguna variante (asignación con comentario, asignación sin comentario, urgente,
teléfono sin asignaciones abiertas), seguir igualmente: el ensayo vale para comprobar sintaxis, collations y cifras, y
las variantes que falten se miran en la salida del 1.3 de producción antes de su bloque 2.

- [ ] **Step 4: Bloque 2 y COMMIT (usuario pega, Claude compara cifras)**

Expected: 2.1 `Changed` = `ABIERTAS − YA_LLEVAN_NOMBRE`; 2.2 = `URGENTES`; 2.3 y 2.4 = `TELEFONOS`; 2.5 `QUEDAN 0`;
ningún `ERROR`. Si todo cuadra, `COMMIT;`; si no, `ROLLBACK;`, se corrige el script (Task 1) y se repite desde el
bloque 0.

- [ ] **Step 5: Bloque 3.1 y 3.2**

Expected: 3.1 todas con `URGENTE = 0` y el comentario empezando por el nombre del cliente (con ` · ` delante del
comentario anterior cuando lo había); 3.2 = `TELEFONOS`.

- [ ] **Step 6: Comprobar en la web de preprod**

Con el administrador:
- **Asignaciones / Pendientes:** esos IMEIs sin cliente, sin «Urgente», y el Comentario con el nombre; el `·` y los
  acentos del comentario anterior se ven bien (no `Â·` ni similares).
- **Menú de usuario → Ver logs:** filtrar por `CAMBIAR_CLIENTE`: las líneas con `ID_CLI: —` y el motivo; buscar
  uno de los IMEIs y que salga.
- **Pestaña Clientes:** borrar «Incidencias Amazon» y «Incidencias profesionales». Debe dejar.

- [ ] **Step 7: Bloque 3.3**

Expected: vacío.

- [ ] **Step 8: Registrar el ensayo**

Entrada nueva en `Apuntes/despliegue_preprod.md` §Registro de sesiones: fecha y hora, cifras del bloque 1.4 y de cada
paso del bloque 2, incidencias y correcciones del script si las hubo.

---

### Task 3: Producción

**Files:**
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_vdc_produccion.md` (registro de sesiones: entrada nueva, mismo
  formato que «2026-10-09 (mañana, en horario, sin corte) — Saneamiento del catálogo»)
- Modify: memoria `project_produccion_vdc.md` (y el índice `MEMORY.md` si cambia el resumen)

**Interfaces:**
- Consumes: el script ensayado (Task 2) y las cifras del ensayo como orden de magnitud.

- [ ] **Step 1: Aviso a los técnicos (usuario)**

Antes de empezar: «A partir de hoy, las devoluciones de Amazon y de profesionales se apuntan en el Comentario de la
asignación, no en Cliente. Esos dos clientes desaparecen; cuando acabe, recargad la página.»

- [ ] **Step 2: Copia a mano (usuario)**

`ssh prod` → `/usr/local/sbin/backup-erp.sh` → `tail -n 1 /var/log/backup-erp.log`.
Expected: línea `OK` con el nombre de la copia (`erp-2026-10-…sql.gz`). Anotar nombre y tamaño.

- [ ] **Step 3: Consola de MariaDB y bloque 0**

La misma línea de consola que en preprod (Task 2, Step 1), ahora en `ssh prod`.
Expected: 0.1 una fila; 0.2 `utf8mb4_unicode_ci` en las cinco tablas.

- [ ] **Step 4: Bloque 1 (usuario pega la salida aquí, Claude la revisa)**

Expected: dos clientes; 1.5 = 0. **Guardar la salida del 1.3** (es la vuelta atrás): Claude la pega en la entrada del
registro de producción de `Apuntes/` (Step 8).

- [ ] **Step 5: Bloque 2 y COMMIT**

Expected: las mismas reglas que en preprod (Task 2, Step 4) con las cifras de producción. `COMMIT;` solo si todo
cuadra y no hay ningún `ERROR`.

- [ ] **Step 6: Bloque 3.1–3.2, borrado en la web y bloque 3.3**

Expected: como en preprod (Task 2, Steps 5–7), con la web de producción.

- [ ] **Step 7: Recargar (técnicos)**

Avisar de que recarguen la página. Si alguien tenía abierto el editor de comentario de una de esas filas y le sale
«Dato modificado por otro usuario», que cierre, recargue y vuelva a escribir.

- [ ] **Step 8: Registrar en `Apuntes/despliegue_vdc_produccion.md`**

Entrada «2026-10-09 (…, sin corte) — Quitar los clientes ficticios de incidencias (solo datos)» con: qué y por qué
(enlace a la spec), copia a mano (nombre, tamaño), cifras del 1.4 y de cada paso del 2, salida del 1.3 (vuelta atrás),
borrado de los clientes y **vuelta atrás**: volver a crear los dos clientes en la web, `UPDATE Telefono SET ID_CLI = <id
nuevo> WHERE IMEI IN (…)` con los IMEIs del 1.3/registro, y devolver `COMENTARIO_ASIGNACION`/`URGENTE` a lo que dice
el 1.3 (`UPDATE Reparacion … WHERE ID_REP = …`, uno por fila).

- [ ] **Step 9: Comprobar al día siguiente**

Tras las 00:00: esos IMEIs siguen sin urgente (el urgente automático ya no los ve). Desde la consola:

```sql
SELECT r.ID_REP, r.IMEI, r.URGENTE FROM Reparacion r
WHERE r.IMEI IN (<IMEIs del 1.3>) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL AND r.URGENTE = TRUE;
```

Expected: vacío, salvo las que el supertécnico haya vuelto a marcar a mano (salen en el registro como `MARCAR_URGENTE`).

- [ ] **Step 10: Memoria**

Actualizar `project_produccion_vdc.md` con una línea: «2026-10-09: clientes ficticios de incidencias quitados (solo
datos); las devoluciones van en el Comentario de la asignación».
