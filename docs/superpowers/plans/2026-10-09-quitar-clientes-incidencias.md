# Quitar los clientes ficticios de incidencias — Plan de ejecución

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quitar los clientes «Incidencias Amazon» e «Incidencias profesionales» de los teléfonos: el nombre pasa al Comentario de sus asignaciones abiertas, esas asignaciones pierden el urgente, los teléfonos quedan sin cliente y los dos clientes se desactivan. Los trabajos ya cerrados conservan la etiqueta gracias a la 0.9.8.

**Architecture:** Operación **solo de datos**, una vez por entorno, **después** de la 0.9.8 y su relleno. Un script SQL en `Apuntes/` que se pega bloque a bloque en la consola de MariaDB: parámetros y requisitos → comprobación de solo lectura → cambio en una transacción con **análisis antes del `COMMIT`** y `COMMIT` a mano → comprobación posterior. La desactivación de los clientes se hace desde la web (pestaña Clientes). Ensayo completo en preproducción antes de producción.

**Tech Stack:** MariaDB 11 (consola `mariadb` dentro del contenedor), web del ERP.

**Spec:** `docs/superpowers/specs/2026-10-09-quitar-clientes-incidencias-design.md`. Requisito: `docs/superpowers/plans/2026-10-09-v098-cliente-por-trabajo.md` (Tasks 6 y 7).

## Global Constraints

- **Solo datos:** sin código, sin esquema, sin despliegue, sin versión.
- **Requisito en cada entorno:** 0.9.8 desplegada y su relleno confirmado (el bloque 0.3 lo comprueba).
- **Claude no hace SSH** a las máquinas: prepara y revisa; el usuario ejecuta en la consola y pega aquí la salida.
- **Repos públicos:** el script, los comandos de las máquinas y el registro de la ejecución van en `Apuntes/`, nunca en
  `docs/`.
- **Producción es el ERP real del taller:** copia a mano antes (`/usr/local/sbin/backup-erp.sh`), momento sin
  actividad y aviso previo a los técnicos.
- **Asignación abierta** = `ID_REP LIKE 'A%' AND FECHA_FIN IS NULL` (reparación `A…`, glass `AG…`, pulido `AP…`).
- **Comentario:** `<NOMBRE DEL CLIENTE> · <comentario anterior>`, o solo el nombre si estaba vacío; si ya contiene el
  nombre, no se toca.
- **Registro:** una línea `CAMBIAR_CLIENTE` por teléfono, detalle `IMEI: <imei>, ID_CLI: —`, motivo que empieza por
  `Limpieza de clientes ficticios:`, a nombre del usuario que ejecuta.
- **Urgente:** se quita en todas las asignaciones abiertas de esos teléfonos y se conserva `UPDATED_AT` (como
  `ReparacionDAO.propagarUrgente`).
- **Los dos clientes se desactivan, no se borran** (el historial los nombra por la clave ajena de la 0.9.8).
- **Confirmación:** el análisis 2.6 se revisa con el usuario **dentro de la transacción** y solo entonces `COMMIT;`.

---

### Task 1: Script SQL y nota en el plan maestro

Hecho el 2026-10-09 (primera versión con borrado; rehecho el mismo día para la opción B: requisito 0.9.8, análisis
dentro de la transacción, desactivar en vez de borrar).

**Files:**
- Create: `C:\Users\dev\Documents\Apuntes\quitar-clientes-incidencias.sql` (fuera de git)
- Modify: `C:\Users\dev\Documents\Apuntes\plan-futuro.md` (§2, «F2c — Ciclo completo»; fuera de git)

**Interfaces:**
- Produces: bloques **0** (parámetros y requisitos), **1** (comprobación), **2** (cambio y análisis en transacción),
  **3** (después). Variables `@usuario`, `@ids`; tabla temporal `tel_inc` (IMEI y cliente que tenían). Cifras del
  bloque 1.4: `ABIERTAS`, `YA_LLEVAN_NOMBRE`, `URGENTES`, `TELEFONOS`.

- [x] **Step 1: Crear el script**

Contenido completo de `Apuntes/quitar-clientes-incidencias.sql`:

```sql
-- Quitar los clientes ficticios de incidencias (2026-10-09)
-- Spec: docs/superpowers/specs/2026-10-09-quitar-clientes-incidencias-design.md (repo raiz)
-- Plan: docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md
--
-- REQUISITO: en este entorno, la 0.9.8 desplegada y su relleno (relleno-cliente-por-trabajo.sql) confirmado.
--
-- Se pega en la consola de MariaDB BLOQUE A BLOQUE y en la MISMA sesion: usa variables y una tabla temporal de
-- sesion (@usuario, @ids, tel_inc). Si se cierra la consola, volver a empezar por el bloque 0.
--
-- Los caracteres especiales van en hexadecimal para no depender de la terminal:
--   X'20C2B720' = " · " (separador del comentario)
--   X'E28094'   = "—"   (lo que escribe la web en el registro al dejar un IMEI sin cliente)

-- == Bloque 0: parametros y requisitos =======================================
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @usuario := 'NOMBRE_DE_USUARIO';  -- <- cambiar por el usuario de la web que firma el registro (el tuyo)

-- 0.1 Debe salir 1 fila
SELECT ID_USU, NOMBRE_USUARIO FROM Usuario WHERE NOMBRE_USUARIO = @usuario;

-- 0.2 Las cinco tablas deben salir con utf8mb4_unicode_ci.
--     Si salen con otra, cambiar la collation del SET NAMES de arriba por esa y volver a pegar el bloque 0.
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('Cliente', 'Telefono', 'Reparacion', 'Usuario', 'Log_Actividad');

-- 0.3 Relleno de la 0.9.8 hecho: CERRADAS_CON_CLIENTE > 0. Si sale 0 (o error de columna), PARAR.
SELECT COUNT(*) AS CERRADAS_CON_CLIENTE FROM Reparacion WHERE FECHA_FIN IS NOT NULL AND ID_CLI IS NOT NULL;

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

-- == Bloque 2: cambio y analisis (una transaccion) ===========================
-- Pegar entero. Comparar cada "Changed" / "rows affected" con el bloque 1.4:
--   2.1 = ABIERTAS - YA_LLEVAN_NOMBRE    2.2 = URGENTES    2.3 = TELEFONOS    2.4 = TELEFONOS    2.5 QUEDAN = 0
-- Revisar el analisis 2.6 y terminar a mano con COMMIT; (todo cuadra) o ROLLBACK; (algo no cuadra o ha salido
-- cualquier ERROR: la consola sigue con las siguientes sentencias aunque una falle).
-- Mientras la transaccion esta abierta, las filas de esos telefonos quedan bloqueadas: no tardar mas de un par de minutos.
START TRANSACTION;

-- Foto de los telefonos afectados y su cliente, antes de quitarlo (tabla temporal: no confirma la transaccion)
DROP TEMPORARY TABLE IF EXISTS tel_inc;
CREATE TEMPORARY TABLE tel_inc (IMEI VARCHAR(15) NOT NULL PRIMARY KEY, CLIENTE VARCHAR(150) NOT NULL)
SELECT t.IMEI, c.NOMBRE AS CLIENTE
FROM Telefono t JOIN Cliente c ON c.ID_CLI = t.ID_CLI
WHERE FIND_IN_SET(t.ID_CLI, @ids);

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

-- 2.5 QUEDAN = 0
SELECT COUNT(*) AS QUEDAN FROM Telefono WHERE FIND_IN_SET(ID_CLI, @ids);

-- 2.6 Analisis antes de confirmar
-- a) IMEIs que se quedan sin cliente: el que tenian, asignaciones abiertas y trabajos hechos (R/G/P)
SELECT x.IMEI, x.CLIENTE AS TENIA,
       COALESCE(SUM(r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL), 0) AS ABIERTAS,
       COALESCE(SUM(r.ID_REP NOT LIKE 'A%'), 0) AS HECHOS
FROM tel_inc x LEFT JOIN Reparacion r ON r.IMEI = x.IMEI
GROUP BY x.IMEI, x.CLIENTE ORDER BY x.CLIENTE, x.IMEI;
-- b) Sus trabajos hechos por cliente guardado: es lo que seguira mostrando el Historial
SELECT x.CLIENTE AS TENIA, COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COUNT(*) AS TRABAJOS
FROM tel_inc x
JOIN Reparacion r ON r.IMEI = x.IMEI
LEFT JOIN Cliente cg ON cg.ID_CLI = r.ID_CLI
WHERE r.ID_REP NOT LIKE 'A%'
GROUP BY TENIA, GUARDADO ORDER BY TENIA, TRABAJOS DESC;
-- c) Sus asignaciones abiertas tal como quedan: URGENTE = 0 y el nombre al principio del comentario
SELECT r.ID_REP, r.IMEI, r.URGENTE, r.COMENTARIO_ASIGNACION
FROM tel_inc x JOIN Reparacion r ON r.IMEI = x.IMEI
WHERE r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL
ORDER BY r.IMEI, r.ID_REP;

-- Si todo cuadra:  COMMIT;
-- Si no:           ROLLBACK;

-- == Bloque 3: despues del COMMIT ============================================
-- 3.1 Lineas del registro de esta limpieza (= TELEFONOS)
SELECT COUNT(*) AS LINEAS FROM Log_Actividad
WHERE ACCION = 'CAMBIAR_CLIENTE' AND MOTIVO LIKE 'Limpieza de clientes ficticios:%';

-- Ahora desactivar los dos clientes en la web: pestana Clientes -> interruptor de activo (uno y otro).

-- 3.2 Tras desactivarlos en la web: los dos con ACTIVO = 0
SELECT ID_CLI, NOMBRE, ACTIVO FROM Cliente WHERE FIND_IN_SET(ID_CLI, @ids);

DROP TEMPORARY TABLE IF EXISTS tel_inc;
```

Por qué así (para quien revise):
- `SET NAMES … COLLATE utf8mb4_unicode_ci` (la de `crear_bd.sql`): las variables de usuario toman la collation de la
  conexión; si no coincide con la de las columnas, `u.NOMBRE_USUARIO = @usuario` da «Illegal mix of collations». El 0.2
  lo comprueba antes de tocar nada.
- Los literales con `·` y `—` en hexadecimal con introductor y `COLLATE` explícito: el resultado no depende de cómo
  envíe la terminal los caracteres, y el `COLLATE` explícito evita el mismo error en el `CONCAT`.
- `@ids` en vez de una tabla temporal para los clientes: MariaDB no deja abrir dos veces una tabla temporal en la misma
  sentencia. `tel_inc` sí es temporal, pero cada consulta la nombra una sola vez; se crea dentro de la transacción (una
  tabla temporal no la confirma) para tener el cliente que tenían después de quitarlo.

- [x] **Step 2: Revisar el script contra la spec**

§2 (comentario delante con ` · `, no tocar si ya lo contiene; urgente fuera en las tres categorías; `CAMBIAR_CLIENTE`
por teléfono con `IMEI: x, ID_CLI: —`; desactivar), §3.1 (requisito y los tres puntos de la comprobación), §3.2 (orden
comentario → urgente → registro → teléfono y análisis antes del `COMMIT`), §3.3 (desactivación desde la web).

Run: `grep -nP '[^\x00-\x7F]' /c/Users/dev/Documents/Apuntes/quitar-clientes-incidencias.sql | grep -v '^[0-9]*:--' || echo SOLO_EN_COMENTARIOS`
Expected: `SOLO_EN_COMENTARIOS`.

- [x] **Step 3: Nota en el plan maestro**

En `Apuntes/plan-futuro.md`, sección «F2c — Ciclo completo», tras la línea de «Enviar a externo…»:

```markdown
- [ ] Devoluciones de venta (Amazon / profesional) en la web: hoy se apuntan a mano en el **Comentario de la asignación** (decisión 2026-10-09, al quitar los clientes ficticios «Incidencias Amazon» / «Incidencias profesionales»: spec `2026-10-09-quitar-clientes-incidencias-design.md`). Su sitio es el módulo de ubicaciones/devoluciones (`FLUJO_UBICACIONES_v17.drawio`, `Modulo Ubicaciones - Spec.md`) cuando llegue a la web
```

- [ ] **Step 4: Commit (repo raíz)**

```bash
git add docs/superpowers/plans/2026-10-09-quitar-clientes-incidencias.md
git commit -m "docs: plan de la limpieza de incidencias tras la 0.9.8 (desactivar y analisis antes del commit)"
```

---

### Task 2: Ensayo en preproducción

Después de la Task 6 de la 0.9.8 (Steps 1–3: desplegada, relleno confirmado y probada). Preproducción tiene la base de
producción del 2026-10-09 10:50: debería tener los dos clientes y sus teléfonos reales de esa hora.

**Files:**
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_preprod.md` (la entrada de la sesión de la 0.9.8)

**Interfaces:**
- Consumes: el script de la Task 1 y el usuario administrador (mismo nombre en preprod y en producción; contraseña de
  preprod en `~/.env.e2e`, `ADMIN_USER`/`ADMIN_PASS`).

- [ ] **Step 1: Consola de MariaDB en preprod (usuario)**

La misma sesión del relleno de la 0.9.8 sirve; si se cerró: `ssh preprod` y la línea de consola de
`Apuntes/despliegue_vdc_produccion.md` §«Entrar en MariaDB sin escribir la contraseña» (la primera, con `-it`).

- [ ] **Step 2: Bloque 0 (usuario pega, Claude revisa)**

Con `NOMBRE_DE_USUARIO` sustituido por el nombre de usuario del administrador.
Expected: 0.1 una fila; 0.2 las cinco tablas con `utf8mb4_unicode_ci`; 0.3 `CERRADAS_CON_CLIENTE` > 0.

- [ ] **Step 3: Bloque 1 (usuario pega la salida aquí, Claude la revisa)**

Expected: 1.1 exactamente dos clientes; `@ids` con sus dos ID. Anotar `ABIERTAS`, `YA_LLEVAN_NOMBRE`, `URGENTES` y
`TELEFONOS`.

- [ ] **Step 4: Bloque 2, análisis y COMMIT (usuario pega, Claude compara y revisa con el usuario)**

Expected: 2.1 `Changed` = `ABIERTAS − YA_LLEVAN_NOMBRE`; 2.2 = `URGENTES`; 2.3 y 2.4 = `TELEFONOS`; 2.5 `QUEDAN 0`;
ningún `ERROR`. Análisis 2.6, revisado juntos antes de confirmar:
- a) un IMEI por teléfono afectado (`TELEFONOS` filas), con su cliente, abiertas y hechos;
- b) los trabajos hechos de esos teléfonos conservan «Incidencias …» en `GUARDADO` (los que salgan «(sin cliente)»
  son los de antes de que el teléfono tuviera esa etiqueta, según el relleno);
- c) las asignaciones abiertas con `URGENTE 0` y el comentario empezando por el nombre (` · ` delante del anterior).
Si todo cuadra, `COMMIT;`; si no, `ROLLBACK;`, se corrige el script (Task 1) y se repite desde el bloque 0.

- [ ] **Step 5: Bloque 3.1**

Expected: `LINEAS` = `TELEFONOS`.

- [ ] **Step 6: Comprobar en la web de preprod**

Con el administrador:
- **Asignaciones / Pendientes:** esos IMEIs sin cliente, sin «Urgente», y el Comentario con el nombre; el `·` y los
  acentos del comentario anterior se ven bien (no `Â·` ni similares).
- **Historial:** un trabajo hecho de uno de esos IMEIs sigue con «Incidencias …» en Cliente.
- **Menú de usuario → Ver logs:** filtrar por `CAMBIAR_CLIENTE`: las líneas con `ID_CLI: —` y el motivo; buscar uno de
  los IMEIs y que salga.
- **Pestaña Clientes:** desactivar «Incidencias Amazon» y «Incidencias profesionales» (no ofrecen «Borrar»).

- [ ] **Step 7: Bloque 3.2**

Expected: los dos con `ACTIVO = 0`.

- [ ] **Step 8: Registrar el ensayo**

En la entrada de la sesión de la 0.9.8 de `Apuntes/despliegue_preprod.md`: cifras del bloque 1.4 y de cada paso del
bloque 2, lo visto en el análisis 2.6, incidencias y correcciones del script si las hubo.

---

### Task 3: Producción

Después de la Task 7 de la 0.9.8 (Steps 1–3: desplegada, relleno confirmado y comprobada), en la misma ventana sin
actividad.

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
Expected: línea `OK` con el nombre de la copia (`erp-2026-10-…sql.gz`). Anotar nombre y tamaño. (Es posterior al
relleno de la 0.9.8: sirve de vuelta atrás de esta limpieza sola.)

- [ ] **Step 3: Consola de MariaDB y bloque 0**

La misma línea de consola que en preprod (Task 2, Step 1), ahora en `ssh prod`.
Expected: 0.1 una fila; 0.2 `utf8mb4_unicode_ci` en las cinco tablas; 0.3 > 0.

- [ ] **Step 4: Bloque 1 (usuario pega la salida aquí, Claude la revisa)**

Expected: dos clientes. **Guardar la salida del 1.3** (es la vuelta atrás): Claude la pega en la entrada del registro de
producción de `Apuntes/` (Step 8).

- [ ] **Step 5: Bloque 2, análisis y COMMIT**

Expected: las mismas reglas que en preprod (Task 2, Step 4) con las cifras de producción. El análisis 2.6 se repasa en
un par de minutos (ya visto en preprod) y `COMMIT;` solo si todo cuadra y no hay ningún `ERROR`.

- [ ] **Step 6: Bloque 3.1, desactivación en la web y bloque 3.2**

Expected: como en preprod (Task 2, Steps 5–7), con la web de producción.

- [ ] **Step 7: Recargar (técnicos)**

Avisar de que recarguen la página. Si alguien tenía abierto el editor de comentario de una de esas filas y le sale
«Dato modificado por otro usuario», que cierre, recargue y vuelva a escribir.

- [ ] **Step 8: Registrar en `Apuntes/despliegue_vdc_produccion.md`**

Entrada «2026-10-… (…, sin corte) — Quitar los clientes ficticios de incidencias (solo datos)» con: qué y por qué
(enlace a la spec), copia a mano (nombre, tamaño), cifras del 1.4 y de cada paso del 2, el análisis 2.6, salida del 1.3
(vuelta atrás), desactivación de los clientes y **vuelta atrás**: reactivar los dos clientes en la web,
`UPDATE Telefono SET ID_CLI = <id> WHERE IMEI IN (…)` con los IMEIs del 2.6 a) por cliente, y devolver
`COMENTARIO_ASIGNACION`/`URGENTE` a lo que dice el 1.3 (`UPDATE Reparacion … WHERE ID_REP = …`, uno por fila).

- [ ] **Step 9: Comprobar al día siguiente**

Tras las 00:00: esos IMEIs siguen sin urgente (el urgente automático ya no los ve). Desde la consola:

```sql
SELECT r.ID_REP, r.IMEI, r.URGENTE FROM Reparacion r
WHERE r.IMEI IN (<IMEIs del 2.6 a)>) AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL AND r.URGENTE = TRUE;
```

Expected: vacío, salvo las que el supertécnico haya vuelto a marcar a mano (salen en el registro como `MARCAR_URGENTE`).

- [ ] **Step 10: Memoria**

Actualizar `project_produccion_vdc.md` con una línea: «clientes ficticios de incidencias quitados y desactivados (solo
datos, tras la 0.9.8); las devoluciones van en el Comentario de la asignación».
