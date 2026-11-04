# 0.9.26 — Pieza 2 del módulo de teléfonos: «Pedidos» que lee y avisa · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** el ERP lee cada 5 minutos los pedidos pendientes de ShippyPro, los IMEIs tecleados en PEDIDOS 2026 y las ventas del
Simulador, y enseña en una pantalla «Pedidos» (grupo Teléfonos) cada unidad con su móvil y si cuadra con el SKU del taller;
Asignaciones enseña el pedido de cada IMEI y «Pedidos» de Stock pasa a llamarse «Compras». Sin sugerencia todavía (0.9.27).

**Architecture:**
- **Servidor:** paquete nuevo `com.reparaciones.servidor.pedidos`.
  - **Reglas puras, probadas sin base de datos:** canales, qué SKU acepta un pedido (`AceptaSku`), el cruce de las tres fuentes
    (`CrucePedidos`) y el estado de cada unidad (`EstadoUnidad`).
  - **Lectores:** `ClienteShippyPro` (REST, `GetPendingOrders`) y `ClienteSheets` (API de Google Sheets, solo lectura, con la
    cuenta de servicio de la pieza 1), más dos traductores de filas (`LectorPedidos2026`, `LectorSimulador`).
  - **Datos:** `PedidosDAO` con JdbcTemplate sobre tres tablas nuevas (`Pedido`, `Pedido_unidad`, `Pedido_lectura`); el maestro
    gana tres columnas de SKU de canal en `Nsys_producto`.
  - **Tarea:** `LecturaPedidos` + `TareaLecturaPedidos` cada 5 min, encendida por ajuste.
  - **API:** `PedidosController` (`GET /api/pedidos`, `GET /api/pedidos/fuentes`) y cuatro campos nuevos en `ReparacionResumen`.
- **Web:** pantalla `telefonos/pedidos` (disposición A del prototipo, sin la sugerencia), línea de pedido en la celda del IMEI del
  taller y el cambio de nombre a «Compras».
- **E2E** de solo lectura en preprod.

**Tech Stack:**
- **Servidor:** Spring Boot 3.3, JdbcTemplate, MariaDB, Jackson, `java.net.http.HttpClient`, google-auth-library 1.23.0
  (ya está). Pruebas con JUnit 5, Mockito y MockMvc (sin base de datos).
- **Web:** React 19 + TypeScript, React Router (data router), TanStack Query, openapi-fetch. Pruebas con Vitest, Testing Library,
  user-event, MSW y Playwright.

**Spec:** `docs/superpowers/specs/2026-11-04-pieza2-pedidos-design.md` (raíz). Prototipo:
`docs/superpowers/specs/assets/2026-11-04-pieza2-prototipo-pedidos.html` (la sugerencia, «¿Prefieres otro?» y «Asignar
trabajo» son de la 0.9.27: **no** entran aquí).

## Global Constraints

**Versión, código de partida y base de datos**
- Versión **0.9.26** (provisional: otra sesión lleva la 0.9.25 «Qué revisar», solo web; si entra después, quien mergee segundo
  cambia el número). Servidor + base + web.
- **Base de datos:** migración aditiva `gestion-reparaciones-servidor/sql/migracion-pedidos.sql` (tres tablas y tres columnas en
  `Nsys_producto`), más los mismos cambios en `sql/crear_bd.sql`. **La aplica el usuario a mano**, antes del servidor.
- El servidor no tiene CHANGELOG ni versión en el `pom.xml`: su 0.9.26 es el tag y la entrada del CHANGELOG de la web, que dice
  «servidor y web». El contrato se apunta en `gestion-reparaciones-servidor/docs/api_contract.md`.
- **Compatibilidad:** la web vieja ignora los campos nuevos de `ReparacionResumen` y no llama a `/api/pedidos`; la web nueva con
  un servidor viejo recibe 404 en «Pedidos» (no se despliega así) y en Asignaciones ve los campos `undefined` (no pinta la línea).

**Ramas, worktrees y registro**
- Rama **`dev_v0926_pedidos`** en los dos repos, desde `origin/main` **en el momento de crearla** (`git fetch` antes y mirar el
  commit; no dar por hecho ningún hash), en worktrees:
  - Servidor: `/c/Users/dev/Documents/wt/servidor-0926-pedidos` (lo crea la Task 1).
  - Web: `/c/Users/dev/Documents/wt/web-0926-pedidos`, con `npm ci` dentro (lo crea la Task 10).
  - **Los clones normales y los worktrees de otras ramas no se tocan** (hay otra sesión con `wt/web-0925-que-revisar`). Las rutas
    `gestion-reparaciones-servidor/…` y `gestion-reparaciones-web/…` del plan son las de los worktrees; `docs/…` sin prefijo es la
    raíz.
- Registro de progreso de los subagentes: **`.superpowers/sdd/v0926-pedidos/progress.md`** (raíz; `.superpowers/` está en el
  `.gitignore`).

**Commits, merge y datos de prueba**
- Commits en español **sin tildes**, con prefijo `feat:`, `fix:`, `test:`, `docs:` o `chore:`, y **sin** línea `Co-Authored-By`.
- Merge `--no-ff`, push, tags, gitlinks, preproducción y producción: **fuera de este plan** (ver «Después de este plan»).
- **Repos públicos:** datos de prueba sintéticos (IMEI `35000000000xxxx`, pedidos `402-0000000-00000xx`, «Cliente A», «Tienda
  Norte»). Nunca identificadores de hojas de Google ni de marketplaces de ShippyPro, claves, correos de cuentas de servicio, IP ni
  datos reales. Los ensayos con datos reales escriben su resultado en `C:/Users/dev/Documents/Apuntes/`, nunca en el repo.

**Reglas de rol** (spec §3)
- `/api/pedidos/**`: `@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")` en la clase (no hay jerarquía de roles).
- Los campos de pedido de `ReparacionResumen` los ven todos los roles (van en las listas del taller).
- **Web:** «Pedidos» en el grupo Teléfonos solo con `esAdminOSuperTecnico(sesion)`; la ruta `/telefonos/pedidos` va dentro de
  `RequiereAccesoTelefonos`.

**Ajustes del servidor** (por variable de entorno, como los de NSYS)

| Propiedad | Variable de entorno | Por defecto |
|---|---|---|
| `pedidos.lectura.activa` | `PEDIDOS_LECTURA_ACTIVA` | `false` |
| `pedidos.shippypro.clave` | `PEDIDOS_SHIPPYPRO_CLAVE` | `""` (ruta del fichero con la clave) |
| `pedidos.shippypro.canales` | `PEDIDOS_SHIPPYPRO_CANALES` | `""` (`"<marketplace>:AMAZON,<marketplace>:AMAZON_STOCKROOM,…"`) |
| `pedidos.pedidos2026.hoja` | `PEDIDOS_PEDIDOS2026_HOJA` | `""` |
| `pedidos.pedidos2026.pestana` | `PEDIDOS_PEDIDOS2026_PESTANA` | `PEDIDOS 2026` |
| `pedidos.simulador.hoja` | `PEDIDOS_SIMULADOR_HOJA` | `""` |
| `pedidos.simulador.pestana` | `PEDIDOS_SIMULADOR_PESTANA` | `Registro actividad` |
| `pedidos.filas` | `PEDIDOS_FILAS` | `4000` |
| `pedidos.simulador.dias` | `PEDIDOS_SIMULADOR_DIAS` | `14` |

- Las credenciales de Google son las de la pieza 1: `nsys.drive.credenciales`.
- `TareaLecturaPedidos` lleva `@ConditionalOnProperty(name = "pedidos.lectura.activa", havingValue = "true")`: en los tests no
  existe. `ClienteShippyPro` y `ClienteSheets` leen la clave y las credenciales la primera vez que hacen falta, nunca al arrancar.
- `UnidadesDeImei` (la línea de Asignaciones) no consulta nada con la lectura apagada.

**Horas**
- Las horas de `Pedido`, `Pedido_unidad` y `Pedido_lectura` son de **Europe/Madrid**, `LocalDateTime` sin zona, como el espejo de
  NSYS (`ImportadorNsys.MADRID`). La web las corta tal cual (`diaNsys`), sin `formatear`.
- **Tarea:** `@Scheduled(initialDelay = 90_000, fixedDelay = 300_000)`. Comparte el único hilo del planificador con NSYS.
- **Aviso de datos antiguos** por fuente: `AvisoNsys.nivel(ultimaBuena, ahora)` (ámbar a 60 min hábiles, rojo a 180; de 7 a 23).

**Valores fijos**
- Fuentes (`FuentePedidos`): `SHIPPYPRO`, `PEDIDOS_2026`, `SIMULADOR`. Resultados de lectura: `EN_CURSO`, `OK`, `ERROR`,
  `RECHAZADA` (columnas cambiadas o ajuste que falta), `INCOMPLETA` (freno del 90 %).
- Canales (`Canal`): `AMAZON`, `AMAZON_STOCKROOM`, `DISTRIBUIDORES`, `PARTICULARES`, `BACKIPHONE`, `SIMULADOR`.
- Estados de una unidad: `SIN_MOVIL`, `NO_EN_NSYS`, `NO_CUADRA`, `EN_TALLER`, `NECESITA_TALLER`, `CUADRA`, `CON_AVISO`.
  Motivos: `MODELO`, `GRADO_NO_VENDIBLE`, `SKU_DESCONOCIDO`, `C_SIN_REPARAR`, `COLOR_O_GRADO`, `MS`, `B_POR_A`.
- Cierres de pedido: `YA_NO_PENDIENTE`, `TODO_VENDIDO`, `ABONADO`, `FUERA_DE_VENTANA`. De unidad: `RELEASED`, `ABONO`.

**Comandos**
- **Servidor** (Git Bash, en `/c/Users/dev/Documents/wt/servidor-0926-pedidos`):
  `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`
  y después `mvn -q test` (o `mvn -q test -Dtest='Clase1,Clase2'`). Con `-q`, Maven no escribe nada si todo pasa: código de
  salida 0 y sin `FAIL` ni `ERROR`. `OpenApiContractTest` levanta el contexto entero y deja `target/openapi.json` para la web.
- **Web** (en `/c/Users/dev/Documents/wt/web-0926-pedidos`): `npx vitest run <rutas>`, `npx tsc -b`, `npm run lint` y, al cerrar,
  `npm run check`. **Un módulo no importa de otro**: lo común va a `src/shared`.
- **Inestabilidad conocida de la web:** con la suite entera, algún test puede pasar de los 5 s («orden de llamadas…» de
  `FormularioReparacion`, `NuevoPedidoDialog`, `StockPage`, `HistorialPage`); solos pasan. Si pasa, se repite ese fichero solo y
  se apunta en el registro; no se tocan.

## Ajustes a la spec (se suben a la spec, como §15, en el commit del plan)

1. **Numeración:** 0.9.26 (esta) y 0.9.27 (sugerencia), por la 0.9.25 de otra sesión. Rama `dev_v0926_pedidos`.
2. **ShippyPro, comprobado el 04/11** con una llamada de solo lectura: `{Result, Total, Orders}`; de cada pedido,
   `transaction_id`, `date` (Unix en segundos, como texto), `marketplace_id` (texto), `name`, `country`, `items[{sku, quantity
   (texto), title, price}]`. Sin un User-Agent propio ShippyPro responde **403**: el cliente manda el suyo.
3. **Hojas de Google:** cada lectura son tres llamadas `values.get` (la cabecera, la columna clave entera para saber cuántas filas
   hay y las últimas `pedidos.filas` filas), con `valueRenderOption=UNFORMATTED_VALUE` y `dateTimeRenderOption=SERIAL_NUMBER`.
   La columna clave es «SKU» en PEDIDOS 2026 y «Order id» en el Simulador. La cabecera del Simulador es «Linea facturación».
4. **PEDIDOS 2026:** una fila es venta con la regla de `Deduccion_IMEIs.gs` (Plataforma no vacía, ni NP, ni con INCIDENCIA o
   CANCEL) y un IMEI de 15 cifras. **Una fila solo cubre un pedido del mismo día o de antes** (Fecha de la fila ≥ fecha del
   pedido): así las filas viejas de pedidos ya enviados que NSYS no liberó no tapan pedidos nuevos.
5. **SKU desconocidos:** solo cuentan los que parecen de teléfono (empiezan por «i» y una cifra); los demás (portes, cajas) se
   ignoran sin avisar.
6. **Una vuelta solo aplica** si las tres fuentes tienen datos, de esa vuelta o de una anterior desde el arranque (se guardan en
   memoria). Recién arrancado y con una fuente caída, no se toca nada.
7. **Unidades estables:** una unidad es (pedido, posición); su `ID_UNIDAD` no cambia entre lecturas.
8. **La línea de pedido** se rellena en `DescripcionMovil` (las seis listas del taller), solo en las filas de trabajos abiertos
   (`fechaFin` nula): sale en Asignaciones y también en Pendientes, que usa la misma celda (`CeldaImeiPendiente`).
9. **Maestro:** se recarga una vez tras el despliegue si ningún producto tiene los SKU de los canales nuevos (como los nombres en la
   0.9.22).
10. **«Ver rastro»** abre el detalle del IMEI (`/reparaciones/imeis/:imei`), como el stock de teléfonos.
11. **`Pedido_lectura`** se poda: las lecturas de más de 30 días se borran en cada vuelta.
12. **En la API, `pide` y `movil` de cada unidad nunca son nulos:** springdoc no tiene ningún objeto anidado nulo en el contrato y
    no se sabe cómo lo marcaría. Sin SKU conocido van vacíos (`Atributos.NINGUNO`) y sin móvil, `MovilUnidad.NINGUNO`, con
    `enNsys = false`.
13. **Un pedido de ShippyPro sin fecha válida se salta** (decisión del usuario tras la revisión de la Task 3, 04/11): los demás se
    leen; su número queda en el mensaje de la lectura («Pedidos sin fecha válida, saltados: …») y en el log. Una fecha
    «1793629400.0» vale.
14. **Amazon: se cubren todos los pedidos posibles** (decisión del usuario tras la revisión de la Task 5, 04/11): primero se
    decide qué pedidos quedan cubiertos (emparejamiento máximo, prefiriendo los más antiguos); después, entre los repartos que
    los cubren a todos, el móvil más avanzado va al pedido más antiguo.
15. **Simulador: tras un abono, venta nueva** (decisión del usuario, 04/11): el abono cierra la unidad de antes; la siguiente línea
    de ese IMEI es una unidad nueva; solo un IMEI en una unidad **abierta** del Simulador deja de usarse en PEDIDOS 2026. Las
    líneas con un SKU que el maestro no conoce no cuentan.
16. **Posiciones con huecos:** todas las unidades de un artículo de ShippyPro (aunque el maestro no conozca su SKU) y todas las
    líneas no BB de un pedido del Simulador cuentan para la posición; al guardar se quitan las posiciones que no han salido.
17. **Un fallo al cruzar o guardar se ve** (decisión del usuario tras la revisión de la Task 6, 04/11): las lecturas OK de esa
    vuelta pasan a ERROR («No se pudo cruzar o guardar: …») y el error va al log con su traza; `podarLecturas` corre al empezar
    cada vuelta; sin `pedidos.shippypro.canales`, ShippyPro es RECHAZADA; el freno compara con la última lectura con filas > 0.

## Mapa de ficheros

**Servidor** (`gestion-reparaciones-servidor/`; clases en `src/main/java/com/reparaciones/servidor/` y pruebas en
`src/test/java/com/reparaciones/servidor/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `sql/migracion-pedidos.sql` (nuevo), `sql/crear_bd.sql` | Tablas y columnas | 1 |
| `nsys/ProductoMaestro.java`, `nsys/LectorMaestro.java`, `nsys/NsysDAO.java`, `nsys/ImportadorNsys.java` (+ tests) | SKU de canales del maestro | 1 |
| `pedidos/{Canal,FuentePedidos,Atributos,Aceptacion,AceptaSku,Textos}.java` (+ tests) | Reglas puras | 2 |
| `pedidos/{ClienteShippyPro,PedidoShippyPro}.java` (+ test) | ShippyPro | 3 |
| `pedidos/{ClienteSheets,TablaHoja,FormatoHojaException,FilaPedidos2026,FilaSimulador,LectorPedidos2026,LectorSimulador}.java` (+ tests) | Hojas de Google | 4 |
| `pedidos/{PedidoLeido,CrucePedidos}.java` (+ test) | Cruce | 5 |
| `pedidos/{PedidosDAO,PedidoGuardado,UnidadGuardada,LecturaGuardada,LecturaPedidos,TareaLecturaPedidos}.java` (+ tests) | Datos y lectura | 6 |
| `pedidos/{EstadoUnidad,VistaPedidos,PedidosController,PedidoVista,UnidadVista,MovilUnidad,FuentePedidosVista}.java` (+ tests), `controller/RolesPedidosTest.java`, `OpenApiContractTest.java`, `docs/api_contract.md` | Estado y API | 7 |
| `model/ReparacionResumen.java`, `pedidos/UnidadesDeImei.java`, `nsys/DescripcionMovil.java` (+ tests) | Línea de Asignaciones | 8 |
| `pedidos/EnsayoPedidosTest.java` (prueba, solo con variable de entorno) | Ensayo con datos reales | 9 |

**Web** (`gestion-reparaciones-web/`)

| Fichero | Responsabilidad | Task |
|---|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts`, `src/modules/telefonos/{api.ts,rutas.tsx}`, `src/app/router.tsx`, `src/modules/almacen/{rutas.tsx,pedidos/PedidosPage.tsx}`, `src/modules/telefonos/test/fabrica.ts`, tests de navegación | Contrato, rutas, menú y «Compras» | 10 |
| `src/modules/telefonos/pedidos/pedidos.ts` (+ test) | Reglas de la pantalla | 11 |
| `src/modules/telefonos/pedidos/{PedidosPage,ListaPedidos,DetalleUnidad,FiltrosPedidos,PildoraFuentes}.tsx` (+ test) | Pantalla | 12 |
| `src/modules/taller/componentes/CeldaImeiPendiente.tsx` (+ test) | Línea de pedido | 13 |
| `tests/e2e/pedidos-clientes.spec.ts` (nuevo), E2E que pulsan «Pedidos» de Stock, `package.json`, `package-lock.json`, `CHANGELOG.md` | E2E, versión y documentación | 14 |

**Raíz y apuntes** (fuera de los worktrees): `docs/novedades/NOVEDADES-v0.9.26.md` (nuevo) y
`C:/Users/dev/Documents/Apuntes/plan-futuro.md` (Task 14).

---

### Task 1: Base de datos y SKU de los canales en el maestro

**Files:**
- Create: `gestion-reparaciones-servidor/sql/migracion-pedidos.sql`
- Modify: `gestion-reparaciones-servidor/sql/crear_bd.sql`
- Modify: `src/main/java/com/reparaciones/servidor/nsys/ProductoMaestro.java`, `nsys/LectorMaestro.java`, `nsys/NsysDAO.java`,
  `nsys/ImportadorNsys.java`
- Test: `src/test/java/com/reparaciones/servidor/nsys/LectorMaestroTest.java`, `nsys/NsysDAOTest.java`,
  `nsys/ImportadorNsysTest.java`

**Interfaces:**
- Produces: `ProductoMaestro` con tres componentes nuevos al final, `skuAmazonEsim`, `skuParticulares`, `skuBackiphone` (los
  constructores de 10 y 11 argumentos siguen y los ponen a null); `NsysDAO.productosPorSkuDeCanal(Collection<String> skus):
  List<ProductoMaestro>` y `NsysDAO.productosSinSkuDeCanales(): boolean`.

- [ ] **Step 1: Crear la rama y el worktree del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch origin
git log -1 --format='%h %s' origin/main
git worktree add -b dev_v0926_pedidos /c/Users/dev/Documents/wt/servidor-0926-pedidos origin/main
cd /c/Users/dev/Documents/wt/servidor-0926-pedidos
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
mvn -q test
```

Expected: el hash de `origin/main` queda apuntado en el registro y `mvn -q test` sale con 0.

- [ ] **Step 2: Escribir la migración**

`gestion-reparaciones-servidor/sql/migracion-pedidos.sql`:

```sql
-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos.sql — 0.9.26, pieza 2 del módulo de teléfonos: «Pedidos» (1ª entrega)
-- (spec docs/superpowers/specs/2026-11-04-pieza2-pedidos-design.md §5, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor 0.9.26.
-- Aditiva: el servidor anterior no lee las tablas nuevas ni las columnas nuevas de Nsys_producto.
-- NO idempotente (relanzar = error de tabla o columna existente).
-- 1) Vista previa: YA_EXISTEN y COLUMNAS_YA deben dar 0.
-- 2) Tres tablas nuevas y tres columnas en Nsys_producto.
-- 3) Comprobación final: CREADAS debe dar 3 y COLUMNAS, 3.
-- Vuelta atrás: el servidor anterior funciona con todo puesto; no hace falta quitar nada.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): las dos deben dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido', 'Pedido_unidad', 'Pedido_lectura');
SELECT COUNT(*) AS COLUMNAS_YA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');

-- Un pedido de un cliente (spec §5.1): de ShippyPro (Amazon y webs) o del Simulador. Los cerrados no se borran.
CREATE TABLE Pedido (
    ID_PEDIDO     INT          NOT NULL AUTO_INCREMENT,
    CANAL         VARCHAR(20)  NOT NULL COMMENT 'AMAZON, AMAZON_STOCKROOM, DISTRIBUIDORES, PARTICULARES, BACKIPHONE o SIMULADOR',
    NUMERO        VARCHAR(60)  NOT NULL COMMENT 'Nº de pedido de ShippyPro u Order id del Simulador',
    CLIENTE       VARCHAR(150) NULL,
    PAIS          VARCHAR(10)  NULL,
    FECHA         DATETIME     NOT NULL COMMENT 'Hora de Madrid',
    FUENTE        VARCHAR(12)  NOT NULL COMMENT 'SHIPPYPRO o SIMULADOR',
    VISTO_PRIMERO DATETIME     NOT NULL,
    VISTO_ULTIMO  DATETIME     NOT NULL,
    CERRADO_EN    DATETIME     NULL COMMENT 'NULL = abierto',
    MOTIVO_CIERRE VARCHAR(20)  NULL COMMENT 'YA_NO_PENDIENTE, TODO_VENDIDO, ABONADO o FUERA_DE_VENTANA',
    PRIMARY KEY (ID_PEDIDO),
    UNIQUE KEY uq_pedido_canal_numero (CANAL, NUMERO),
    KEY idx_pedido_cerrado (CERRADO_EN)
);

-- Una unidad de un pedido (spec §5.2): (pedido, posición) no cambia entre lecturas.
CREATE TABLE Pedido_unidad (
    ID_UNIDAD           INT         NOT NULL AUTO_INCREMENT,
    ID_PEDIDO           INT         NOT NULL,
    POSICION            INT         NOT NULL,
    SKU                 VARCHAR(60) NOT NULL COMMENT 'El SKU tal como lo da la fuente',
    IMEI                VARCHAR(40) NULL,
    IMEI_FUENTE         VARCHAR(14) NULL COMMENT 'PEDIDOS_2026 o SIMULADOR',
    IMEI_REF            INT         NULL COMMENT 'Fila de la hoja',
    IMEI_TECLEADO_EN    DATETIME    NULL,
    SIN_PEDIDO_CONCRETO BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'Amazon: fila «Amazon» + SKU de PEDIDOS 2026',
    OTRO_ORDER_ID       VARCHAR(30) NULL COMMENT 'Simulador: otro Order id del mismo IMEI',
    CERRADA_EN          DATETIME    NULL,
    MOTIVO_CIERRE       VARCHAR(12) NULL COMMENT 'RELEASED o ABONO',
    PRIMARY KEY (ID_UNIDAD),
    UNIQUE KEY uq_unidad_posicion (ID_PEDIDO, POSICION),
    KEY idx_unidad_imei (IMEI),
    CONSTRAINT fk_unidad_pedido FOREIGN KEY (ID_PEDIDO) REFERENCES Pedido (ID_PEDIDO) ON DELETE CASCADE
);

-- Una lectura de una fuente (spec §5.3), para la píldora. Se borran a los 30 días.
CREATE TABLE Pedido_lectura (
    ID_LECTURA       INT           NOT NULL AUTO_INCREMENT,
    FUENTE           VARCHAR(14)   NOT NULL COMMENT 'SHIPPYPRO, PEDIDOS_2026 o SIMULADOR',
    INICIO           DATETIME      NOT NULL,
    FIN              DATETIME      NULL,
    RESULTADO        VARCHAR(12)   NOT NULL DEFAULT 'EN_CURSO' COMMENT 'EN_CURSO, OK, ERROR, RECHAZADA o INCOMPLETA',
    FILAS            INT           NOT NULL DEFAULT 0,
    SKU_DESCONOCIDOS VARCHAR(500)  NULL,
    MENSAJE          VARCHAR(1000) NULL,
    PRIMARY KEY (ID_LECTURA),
    KEY idx_lectura_fuente (FUENTE, ID_LECTURA),
    KEY idx_lectura_inicio (INICIO)
);

-- Los SKU de los canales que faltaban en el maestro (spec §5.4).
ALTER TABLE Nsys_producto
    ADD COLUMN SKU_AMAZON_ESIM  VARCHAR(60) NULL COMMENT '«SKU (Amazon) con eSIM» del maestro (0.9.26)',
    ADD COLUMN SKU_PARTICULARES VARCHAR(60) NULL COMMENT '«SKU (stock fonestore.es) Conjunto» (0.9.26)',
    ADD COLUMN SKU_BACKIPHONE   VARCHAR(60) NULL COMMENT '«SKU BackiPhone» (0.9.26)';

-- Comprobación final (no modifica nada): CREADAS = 3 y COLUMNAS = 3; si no, NO desplegar el servidor 0.9.26.
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido', 'Pedido_unidad', 'Pedido_lectura');
SELECT COUNT(*) AS COLUMNAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');
```

En `sql/crear_bd.sql`: añadir a `CREATE TABLE Nsys_producto`, detrás de `ES_ESIM`, las tres columnas (`SKU_AMAZON_ESIM`,
`SKU_PARTICULARES`, `SKU_BACKIPHONE`, `VARCHAR(60) NULL`) y, al final del fichero, los tres `CREATE TABLE` de arriba con un
comentario `-- 0.9.26: pedidos (spec pieza 2 §5)`. Comprobar a ojo que `NOMBRE` sigue en `Nsys_producto` (lo puso la 0.9.22).

- [ ] **Step 3: Escribir las pruebas que fallan**

En `LectorMaestroTest` (al final de la clase):

```java
    @Test void leeLosSkuDeLosCanalesSiElMaestroLosTrae() throws Exception {
        List<Object> cabecera = new ArrayList<>(CABECERA);
        cabecera.addAll(List.of("SKU (Amazon) con eSIM", "SKU (stock fonestore.es) Conjunto", "SKU BackiPhone"));
        List<List<Object>> todo = new ArrayList<>();
        todo.add(cabecera);
        List<Object> f = new ArrayList<>(fila("Apple iPhone 13 128GB Blue A", "iPhone", "13", "Azul", "A", "i13128AzulA",
                "i13128AzulA"));
        f.addAll(List.of("i13128AzulAesim", "i13128AzulAP", "BK-i13128AzulA"));
        todo.add(f);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, todo);

        ProductoMaestro p = leer(XlsxDePrueba.libro(hojas)).productos().get(0);

        assertEquals("i13128AzulAesim", p.skuAmazonEsim());
        assertEquals("i13128AzulAP", p.skuParticulares());
        assertEquals("BK-i13128AzulA", p.skuBackiphone());
    }

    @Test void sinLasColumnasDeLosCanalesSeCargaIgualConNulos() throws Exception {
        ProductoMaestro p = leer(maestro(List.of(fila("Apple iPhone 13 128GB Blue A", "iPhone", "13", "Azul", "A",
                "i13128AzulA", "i13128AzulA")))).productos().get(0);
        assertNull(p.skuAmazonEsim());
        assertNull(p.skuParticulares());
        assertNull(p.skuBackiphone());
    }
```

En `NsysDAOTest`, en `cargarProductosLeeElNombre` el `contains(", NOMBRE FROM Nsys_producto")` pasa a
`contains("NOMBRE, SKU_AMAZON_ESIM, SKU_PARTICULARES, SKU_BACKIPHONE FROM Nsys_producto")` (las columnas nuevas van detrás de
`NOMBRE`); cambiar `reemplazarProductosBorraYCargaTodo` para que el array esperado termine en
`"Apple iPhone 13 128GB Blue", null, null, null` y la comprobación del SQL sea
`assertTrue(NsysDAO.SQL_PRODUCTO.contains("SKU_BACKIPHONE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), NsysDAO.SQL_PRODUCTO);`,
y añadir:

```java
    @SuppressWarnings("unchecked")
    @Test void cargarProductosLeeLosSkuDeLosCanales() throws SQLException {
        dao.cargarProductos();
        ArgumentCaptor<RowMapper<ProductoMaestro>> mapeo = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(contains("SKU_BACKIPHONE FROM Nsys_producto"), mapeo.capture());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("SKU_AMAZON_ESIM")).thenReturn("a-esim");
        when(rs.getString("SKU_PARTICULARES")).thenReturn("p");
        when(rs.getString("SKU_BACKIPHONE")).thenReturn("b");
        ProductoMaestro p = mapeo.getValue().mapRow(rs, 0);
        assertEquals(List.of("a-esim", "p", "b"), List.of(p.skuAmazonEsim(), p.skuParticulares(), p.skuBackiphone()));
    }

    @SuppressWarnings("unchecked")
    @Test void productosPorSkuDeCanalBuscaEnLasCincoColumnasEnTandas() {
        List<Object[]> llamadas = new ArrayList<>();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            llamadas.add(inv.getRawArguments());   // [sql, mapeo, Object[] con los argumentos]
            return List.of();
        });
        List<String> skus = new ArrayList<>();
        for (int i = 0; i < 600; i++) skus.add("sku" + i);

        dao.productosPorSkuDeCanal(skus);

        assertEquals(2, llamadas.size());
        String sql = (String) llamadas.get(0)[0];
        assertTrue(sql.contains("WHERE SKU_WEB IN (?"), sql);
        assertTrue(sql.contains("OR SKU_BACKIPHONE IN (?"), sql);
        assertEquals(500 * 5, ((Object[]) llamadas.get(0)[2]).length);
        assertEquals(100 * 5, ((Object[]) llamadas.get(1)[2]).length);
        assertTrue(dao.productosPorSkuDeCanal(List.of()).isEmpty());
    }

    @Test void productosSinSkuDeCanalesMiraSiNingunoLosTiene() {
        when(jdbc.queryForObject(NsysDAO.SQL_PRODUCTOS_SIN_SKU_DE_CANALES, Boolean.class)).thenReturn(true);
        assertTrue(dao.productosSinSkuDeCanales());
        when(jdbc.queryForObject(NsysDAO.SQL_PRODUCTOS_SIN_SKU_DE_CANALES, Boolean.class)).thenReturn(null);
        assertFalse(dao.productosSinSkuDeCanales());
    }
```

En `ImportadorNsysTest`, copiar `sinNombresEnElMaestroGuardadoSeRecargaUnaVezAunqueNoHayaCambiado` como
`sinSkuDeCanalesEnElMaestroGuardadoSeRecargaUnaVez`, cambiando la primera línea por
`when(dao.productosSinSkuDeCanales()).thenReturn(true);` y la última por `verify(dao, times(1)).productosSinSkuDeCanales();`
(el resto igual: dos pasadas, una sola descarga del maestro).

- [ ] **Step 4: Ejecutar las pruebas y ver que fallan**

Run: `mvn -q test -Dtest='LectorMaestroTest,NsysDAOTest,ImportadorNsysTest'`
Expected: FAIL de compilación (`skuAmazonEsim()`, `productosPorSkuDeCanal`, `SQL_PRODUCTOS_SIN_SKU_DE_CANALES` no existen).

- [ ] **Step 5: Implementar**

`nsys/ProductoMaestro.java` (entero):

```java
package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_producto: lo que el maestro dice de un «Goods + Grade». {@code modelo} es el código del ERP (null si
 *  no está en el catálogo); {@code modeloMaestro}, el texto de la columna «Modelo»; {@code nombre}, la columna «Product Name»
 *  tal cual (0.9.22). {@code skuAmazonEsim}, {@code skuParticulares} y {@code skuBackiphone} (0.9.26, spec pieza 2 §5.4): los SKU
 *  con los que ese producto llega de Amazon con eSIM, de la web de particulares y de BackiPhone; null si el maestro no los trae. */
public record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                              String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim,
                              String nombre, String skuAmazonEsim, String skuParticulares, String skuBackiphone) {

    /** Sin los SKU de los canales: las pruebas que no los necesitan y los conflictos del maestro. */
    public ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                           String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim,
                           String nombre) {
        this(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, nombre,
                null, null, null);
    }

    /** Sin el nombre del producto: las pruebas que no lo necesitan. */
    public ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                           String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim) {
        this(clave, modelo, modeloMaestro, capacidad, color, calidadDetalle, skuWeb, skuAmazon, skuSinGrado, esEsim, null);
    }
}
```

`nsys/LectorMaestro.java`: detrás de `NOMBRE`, añadir

```java
    /** Los SKU de los canales que no son el nuestro ni el de Amazon (0.9.26). Opcionales: sin ellas, esos pedidos no se
     *  reconocen y el maestro se carga igual. */
    static final String SKU_AMAZON_ESIM = "SKU (Amazon) con eSIM";
    static final String SKU_PARTICULARES = "SKU (stock fonestore.es) Conjunto";
    static final String SKU_BACKIPHONE = "SKU BackiPhone";
```

en la rama de la cabecera, detrás del aviso de `NOMBRE`:

```java
                for (String c : List.of(SKU_AMAZON_ESIM, SKU_PARTICULARES, SKU_BACKIPHONE))
                    if (!columnas.containsKey(c)) log.warn("NSYS: el maestro no trae la columna «{}»: esos pedidos no se reconocerán", c);
```

y el producto pasa a construirse con los catorce componentes:

```java
            ProductoMaestro p = new ProductoMaestro(Normalizacion.clave(nombre), modelo, modeloMaestro,
                    valor(celdas, "Capacidad", AnchosNsys.CAPACIDAD), valor(celdas, "Color", AnchosNsys.COLOR),
                    valor(celdas, "Calidad Detalle", AnchosNsys.CALIDAD_DETALLE),
                    valor(celdas, "SKU (stock web)", AnchosNsys.SKU), valor(celdas, "SKU (Amazon)", AnchosNsys.SKU),
                    valor(celdas, "SKU sin grado", AnchosNsys.SKU), Normalizacion.esEsim(nombre),
                    valor(celdas, NOMBRE, AnchosNsys.NOMBRE), valor(celdas, SKU_AMAZON_ESIM, AnchosNsys.SKU),
                    valor(celdas, SKU_PARTICULARES, AnchosNsys.SKU), valor(celdas, SKU_BACKIPHONE, AnchosNsys.SKU));
```

(la rama del conflicto no cambia: con el constructor de 11 argumentos, un conflicto se queda sin ningún SKU).

`nsys/NsysDAO.java`:

```java
    static final String COLUMNAS_PRODUCTO = "CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, CALIDAD_DETALLE, SKU_WEB,"
            + " SKU_AMAZON, SKU_SIN_GRADO, ES_ESIM, NOMBRE, SKU_AMAZON_ESIM, SKU_PARTICULARES, SKU_BACKIPHONE";
    static final String SQL_PRODUCTO = "INSERT INTO Nsys_producto (" + COLUMNAS_PRODUCTO + ")"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    /** El maestro guardado no tiene ningún SKU de los canales nuevos (se cargó antes de la 0.9.26). */
    static final String SQL_PRODUCTOS_SIN_SKU_DE_CANALES = "SELECT COUNT(*) > 0 AND COALESCE(SUM(SKU_AMAZON_ESIM IS NOT NULL"
            + " OR SKU_PARTICULARES IS NOT NULL OR SKU_BACKIPHONE IS NOT NULL), 0) = 0 FROM Nsys_producto";
    /** Los productos cuyo SKU de algún canal es uno de estos (la intercalación de la tabla no distingue mayúsculas). */
    static final String SQL_PRODUCTOS_POR_SKU = "SELECT " + COLUMNAS_PRODUCTO + " FROM Nsys_producto WHERE SKU_WEB IN (%1$s)"
            + " OR SKU_AMAZON IN (%1$s) OR SKU_AMAZON_ESIM IN (%1$s) OR SKU_PARTICULARES IN (%1$s) OR SKU_BACKIPHONE IN (%1$s)";
```

(sustituye a la `SQL_PRODUCTO` de antes), y:

```java
    public List<ProductoMaestro> cargarProductos() {
        return jdbc.query("SELECT " + COLUMNAS_PRODUCTO + " FROM Nsys_producto", NsysDAO::producto);
    }

    /** Los productos del maestro que un pedido puede pedir con estos SKU de canal (0.9.26, spec pieza 2 §6.4.1), en tandas. */
    public List<ProductoMaestro> productosPorSkuDeCanal(Collection<String> skus) {
        List<ProductoMaestro> productos = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(skus), TANDA_IN)) {
            Object[] args = new Object[tanda.size() * 5];
            for (int i = 0; i < 5; i++) System.arraycopy(tanda.toArray(), 0, args, i * tanda.size(), tanda.size());
            productos.addAll(jdbc.query(String.format(SQL_PRODUCTOS_POR_SKU, marcas(tanda.size())), NsysDAO::producto, args));
        }
        return productos;
    }

    public boolean productosSinSkuDeCanales() {
        return Boolean.TRUE.equals(jdbc.queryForObject(SQL_PRODUCTOS_SIN_SKU_DE_CANALES, Boolean.class));
    }

    static ProductoMaestro producto(ResultSet rs, int n) throws SQLException {
        return new ProductoMaestro(rs.getString("CLAVE"), rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"),
                rs.getString("CAPACIDAD"), rs.getString("COLOR"), rs.getString("CALIDAD_DETALLE"), rs.getString("SKU_WEB"),
                rs.getString("SKU_AMAZON"), rs.getString("SKU_SIN_GRADO"), rs.getBoolean("ES_ESIM"), rs.getString("NOMBRE"),
                rs.getString("SKU_AMAZON_ESIM"), rs.getString("SKU_PARTICULARES"), rs.getString("SKU_BACKIPHONE"));
    }
```

y en `reemplazarProductos`, el array de cada producto termina en
`p.nombre(), p.skuAmazonEsim(), p.skuParticulares(), p.skuBackiphone()`. (`trozos(lista, tamaño)` y `marcas(n)` ya existen en
`NsysDAO`; si `trozos` solo tiene la versión de un argumento, usar la de dos que ya usa `trabajosAbiertos(Collection)`.)

`nsys/ImportadorNsys.java`, en el método que mira si faltan nombres (`faltanNombres`, el que usa `dao.productosSinNombre()`):
la condición pasa a `if (dao.productosSinNombre() || dao.productosSinSkuDeCanales()) return true;` y su comentario dice «sin
nombres de producto (0.9.22) o sin los SKU de los canales (0.9.26)». El log de la recarga dice además cuántos productos traen
`skuParticulares`.

- [ ] **Step 6: Ejecutar las pruebas**

Run: `mvn -q test`
Expected: PASS (todas: las que construyen `ProductoMaestro` con 10 u 11 argumentos siguen compilando).

- [ ] **Step 7: Commit**

```bash
git add sql/migracion-pedidos.sql sql/crear_bd.sql src/main/java/com/reparaciones/servidor/nsys src/test/java/com/reparaciones/servidor/nsys
git commit -m "feat: tablas de pedidos y SKU de los canales en el maestro (0.9.26)"
```

---

### Task 2: Reglas puras — canales, textos y qué SKU acepta un pedido

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/{Canal,FuentePedidos,Atributos,Aceptacion,AceptaSku,Textos}.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/{CanalTest,TextosTest,AceptaSkuTest}.java`

**Interfaces:**
- Consumes: `ProductoMaestro` (Task 1).
- Produces:
  - `enum Canal { AMAZON, AMAZON_STOCKROOM, DISTRIBUIDORES, PARTICULARES, BACKIPHONE, SIMULADOR }` con `etiqueta()`,
    `esAmazon()`, `esWeb()` y `static Map<String, Canal> deAjuste(String)`.
  - `enum FuentePedidos { SHIPPYPRO, PEDIDOS_2026, SIMULADOR }`.
  - `record Atributos(String modelo, String capacidad, String color, String grado, boolean esEsim)` (el modelo es el del maestro).
  - `record Aceptacion(List<ProductoMaestro> productos, List<ProductoMaestro> productosBporA)` con `pide(): Atributos`,
    `acepta(String sku)`, `aceptaBporA(String sku)`, `aceptaColorYGrado(String color, String grado)`.
  - `final class AceptaSku` con `AceptaSku(Collection<ProductoMaestro>)`, `Aceptacion para(Canal, String sku)` (null si el maestro
    no lo conoce) y `static Set<String> conVariantes(Collection<String> skus)`.
  - `final class Textos` con `cliente(String)`, `sku(String)`, `imei(Object)`, `pareceTelefono(String)`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`pedidos/CanalTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanalTest {

    @Test void elAjusteDaElCanalDeCadaMarketplaceYSaltaLoMalEscrito() {
        assertEquals(Map.of("100", Canal.AMAZON, "200", Canal.AMAZON_STOCKROOM, "300", Canal.DISTRIBUIDORES),
                Canal.deAjuste(" 100:AMAZON, 200:amazon_stockroom ,300:DISTRIBUIDORES, 400:OTRO, sin-dos-puntos, 500:SIMULADOR"));
        assertEquals(Map.of(), Canal.deAjuste(""));
        assertEquals(Map.of(), Canal.deAjuste(null));
    }

    @Test void amazonYWebs() {
        assertTrue(Canal.AMAZON_STOCKROOM.esAmazon());
        assertFalse(Canal.SIMULADOR.esAmazon());
        assertTrue(Canal.BACKIPHONE.esWeb());
        assertFalse(Canal.AMAZON.esWeb());
        assertEquals("Amazon Stockroom", Canal.AMAZON_STOCKROOM.etiqueta());
    }
}
```

`pedidos/TextosTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextosTest {

    @Test void elClienteSeComparaSinMayusculasTildesNiEspaciosDeMas() {
        assertEquals("tienda norte sl", Textos.cliente("  Tiénda   NORTE SL "));
        assertNull(Textos.cliente("   "));
        assertNull(Textos.cliente(null));
    }

    @Test void elImeiSonQuinceCifrasVengaComoTextoOComoNumero() {
        assertEquals("350000000000001", Textos.imei("350000000000001"));
        assertEquals("350000000000001", Textos.imei(" 350000000000001 "));
        assertEquals("350000000000001", Textos.imei(new TextNode("350000000000001")));
        assertEquals("350000000000001", Textos.imei(LongNode.valueOf(350000000000001L)));
        assertEquals("350000000000001", Textos.imei(DoubleNode.valueOf(3.50000000000001E14)));
        assertNull(Textos.imei("cancelado"));
        assertNull(Textos.imei("35000000000001"));
        assertNull(Textos.imei(null));
    }

    @Test void pareceTelefonoSiEmpiezaPorIYUnaCifra() {
        assertTrue(Textos.pareceTelefono("i13128AzulA"));
        assertTrue(Textos.pareceTelefono("I16Proesim256NegroA"));
        assertFalse(Textos.pareceTelefono("GLS24"));
        assertFalse(Textos.pareceTelefono("BoxIphoneSmall"));
        assertFalse(Textos.pareceTelefono(null));
    }

    @Test void elSkuSeComparaSinMayusculasNiEspacios() {
        assertEquals("i13128azula", Textos.sku(" i13128AzulA "));
        assertNull(Textos.sku(""));
    }
}
```

`pedidos/AceptaSkuTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AceptaSkuTest {

    /** Un producto del maestro: iPhone 13 128GB del color y grado dados, con sus SKU de canal. */
    static ProductoMaestro producto(String color, String grado, String web, String amazon, String particulares,
                                    String backiphone) {
        return new ProductoMaestro("clave-" + color + grado + web, "13", "13", "128GB", color, grado, web, amazon,
                "i13128" + color, false, "Apple iPhone 13 128GB " + color, null, particulares, backiphone);
    }

    private static final List<ProductoMaestro> MAESTRO = List.of(
            producto("Azul", "A", "i13128AzulA", "i13128AzulA", "i13128AzulAP", "bk-azul-a"),
            producto("Azul", "A+", "i13128AzulA", "i13128AzulA", "i13128AzulAP", "bk-azul-a"),
            producto("Azul", "A-", "i13128AzulA-", "i13128AzulA", "i13128AzulA-P", "bk-azul-am"),
            producto("Azul", "MsA", "i13128AzulMs", "i13128AzulA", null, null),
            producto("Azul", "B", "i13128AzulB", "i13128AzulB", "i13128AzulBP", "bk-azul-b"),
            producto("Rojo", "A", "i13128RojoA", "i13128RojoA", "i13128RojoAP", null));

    private final AceptaSku acepta = new AceptaSku(MAESTRO);

    @Test void amazonAceptaElAbanicoDeSuSkuYAparteLosB() {
        Aceptacion a = acepta.para(Canal.AMAZON, "I13128AZULA");
        assertTrue(a.acepta("i13128AzulA"));
        assertTrue(a.acepta("i13128azula-"));
        assertTrue(a.acepta("i13128AzulMs"));
        assertFalse(a.acepta("i13128AzulB"));
        assertTrue(a.aceptaBporA("i13128AzulB"));
        assertFalse(a.acepta("i13128RojoA"));
        assertEquals(new Atributos("13", "128GB", "Azul", "A", false), a.pide());
    }

    @Test void cadaWebUsaSuColumnaYElSimuladorLaNuestra() {
        assertTrue(acepta.para(Canal.PARTICULARES, "i13128AzulAP").acepta("i13128AzulA"));
        assertNull(acepta.para(Canal.PARTICULARES, "i13128AzulA"), "Particulares no usa nuestro SKU");
        assertTrue(acepta.para(Canal.BACKIPHONE, "bk-azul-a").acepta("i13128AzulA"));
        assertTrue(acepta.para(Canal.DISTRIBUIDORES, "i13128AzulA").acepta("i13128AzulA"));
        assertFalse(acepta.para(Canal.DISTRIBUIDORES, "i13128AzulA").acepta("i13128AzulA-"));
        assertTrue(acepta.para(Canal.SIMULADOR, "i13128RojoA").acepta("i13128RojoA"));
        assertTrue(acepta.para(Canal.DISTRIBUIDORES, "i13128AzulA").productosBporA().isEmpty(), "B por A solo en Amazon");
    }

    @Test void unSkuQueElMaestroNoConoceNoSeAcepta() {
        assertNull(acepta.para(Canal.AMAZON, "i99128VerdeA"));
        assertNull(acepta.para(Canal.SIMULADOR, null));
    }

    @Test void colorYGradoParaUnMovilSinSku() {
        Aceptacion a = acepta.para(Canal.AMAZON, "i13128AzulA");
        assertTrue(a.aceptaColorYGrado("azul", "A+"));
        assertFalse(a.aceptaColorYGrado("Rojo", "A"));
    }

    @Test void lasVariantesBSeAnadenParaCargarElMaestro() {
        assertEquals(Set.of("i13128AzulA", "i13128AzulB", "GLS24"), AceptaSku.conVariantes(List.of("i13128AzulA", "GLS24")));
    }
}
```

- [ ] **Step 2: Ejecutar las pruebas y ver que fallan**

Run: `mvn -q test -Dtest='CanalTest,TextosTest,AceptaSkuTest'`
Expected: FAIL de compilación (el paquete `pedidos` no existe).

- [ ] **Step 3: Implementar**

`pedidos/FuentePedidos.java`:

```java
package com.reparaciones.servidor.pedidos;

/** De dónde llega un dato de pedidos (spec pieza 2 §4). Se guarda con su nombre en Pedido.FUENTE, Pedido_unidad.IMEI_FUENTE y
 *  Pedido_lectura.FUENTE. */
public enum FuentePedidos { SHIPPYPRO, PEDIDOS_2026, SIMULADOR }
```

`pedidos/Canal.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** El canal de un pedido (spec pieza 2 §4.1). El Simulador no llega de ShippyPro. */
public enum Canal {
    AMAZON("Amazon"), AMAZON_STOCKROOM("Amazon Stockroom"), DISTRIBUIDORES("Distribuidores"), PARTICULARES("Particulares"),
    BACKIPHONE("BackiPhone"), SIMULADOR("Simulador");

    private static final Logger log = LoggerFactory.getLogger(Canal.class);
    private final String etiqueta;

    Canal(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean esAmazon() {
        return this == AMAZON || this == AMAZON_STOCKROOM;
    }

    public boolean esWeb() {
        return this == DISTRIBUIDORES || this == PARTICULARES || this == BACKIPHONE;
    }

    /** «100:AMAZON, 200:AMAZON_STOCKROOM» → marketplace de ShippyPro → canal (ajuste pedidos.shippypro.canales). Un trozo mal
     *  escrito, un canal desconocido o el SIMULADOR se saltan con un aviso en el log. */
    public static Map<String, Canal> deAjuste(String texto) {
        Map<String, Canal> canales = new HashMap<>();
        if (texto == null || texto.isBlank()) return canales;
        for (String trozo : texto.split(",")) {
            String[] partes = trozo.strip().split(":");
            if (partes.length != 2 || partes[0].isBlank()) {
                if (!trozo.isBlank()) log.warn("Pedidos: «{}» en pedidos.shippypro.canales no es «marketplace:CANAL»", trozo.strip());
                continue;
            }
            try {
                Canal c = Canal.valueOf(partes[1].strip().toUpperCase(Locale.ROOT));
                if (c == SIMULADOR) throw new IllegalArgumentException();
                canales.put(partes[0].strip(), c);
            } catch (IllegalArgumentException e) {
                log.warn("Pedidos: «{}» en pedidos.shippypro.canales no es un canal de ShippyPro", partes[1].strip());
            }
        }
        return canales;
    }
}
```

`pedidos/Atributos.java`:

```java
package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Modelo (el texto del maestro), capacidad, color, grado y eSIM: lo que pide un pedido o lo que es un móvil. Nunca va nulo en
 *  la API (springdoc no marca bien un objeto anidado nulo): si no se sabe, {@link #NINGUNO}, con todo vacío. */
public record Atributos(@Schema(nullable = true) String modelo, @Schema(nullable = true) String capacidad,
                        @Schema(nullable = true) String color, @Schema(nullable = true) String grado, boolean esEsim) {

    public static final Atributos NINGUNO = new Atributos(null, null, null, null, false);
}
```

`pedidos/Textos.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Cómo se comparan los textos de las fuentes de pedidos (spec pieza 2 §6). Es puro. */
public final class Textos {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern QUINCE_CIFRAS = Pattern.compile("\\d{15}");
    private static final Pattern TELEFONO = Pattern.compile("^[iI]\\d.*");

    private Textos() {}

    /** Un nombre de cliente para comparar: sin tildes, en minúsculas y con un solo espacio. null si queda vacío. */
    public static String cliente(String nombre) {
        if (nombre == null) return null;
        String s = MARCAS.matcher(Normalizer.normalize(nombre, Normalizer.Form.NFD)).replaceAll("");
        s = ESPACIOS.matcher(s.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** Un SKU para comparar: sin espacios a los lados y en minúsculas. null si queda vacío. */
    public static String sku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        return sku.strip().toLowerCase(Locale.ROOT);
    }

    /** Un IMEI de 15 cifras, venga como texto, como número o como celda de una hoja (JsonNode); null si no lo es
     *  («cancelado», vacío, otra longitud). */
    public static String imei(Object valor) {
        if (valor == null) return null;
        String texto;
        if (valor instanceof JsonNode n) {
            if (n.isNull() || n.isMissingNode()) return null;
            texto = n.isNumber() ? n.decimalValue().toBigInteger().toString() : n.asText();
        } else if (valor instanceof Number num) {
            texto = new BigDecimal(num.toString()).toBigInteger().toString();
        } else {
            texto = valor.toString();
        }
        texto = texto.strip();
        return QUINCE_CIFRAS.matcher(texto).matches() ? texto : null;
    }

    /** ¿Parece el SKU de un teléfono? Empieza por «i» y una cifra (los portes y las cajas, no). */
    public static boolean pareceTelefono(String sku) {
        return sku != null && TELEFONO.matcher(sku.strip()).matches();
    }
}
```

`pedidos/Aceptacion.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Los productos del maestro que acepta el SKU de un pedido (spec pieza 2 §6.4.1) y, en Amazon, los que acepta con el aviso
 *  «B por A». Es pura. */
public record Aceptacion(List<ProductoMaestro> productos, List<ProductoMaestro> productosBporA) {

    public Aceptacion {
        productos = List.copyOf(productos);
        productosBporA = List.copyOf(productosBporA);
    }

    /** ¿Es uno de los nuestros (SKU (stock web)) que acepta el pedido? */
    public boolean acepta(String sku) {
        return contiene(productos, sku);
    }

    public boolean aceptaBporA(String sku) {
        return contiene(productosBporA, sku);
    }

    /** Para un móvil sin SKU en el maestro: ¿hay un producto aceptado con ese color y ese grado (Calidad Detalle)? */
    public boolean aceptaColorYGrado(String color, String grado) {
        return productos.stream().anyMatch(p -> igual(p.color(), color) && igual(p.calidadDetalle(), grado));
    }

    /** Lo que pide el pedido, para la tabla de diferencias: el modelo, la capacidad y la eSIM de sus productos, y el color y el
     *  grado del producto de grado A si lo hay (si no, del primero). */
    public Atributos pide() {
        ProductoMaestro base = productos.stream().filter(p -> igual(p.calidadDetalle(), "A")).findFirst().orElse(productos.get(0));
        String color = productos.stream().map(ProductoMaestro::color).filter(c -> c != null && !c.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey).orElse(base.color());
        return new Atributos(base.modeloMaestro(), base.capacidad(), color, base.calidadDetalle(), base.esEsim());
    }

    private static boolean contiene(List<ProductoMaestro> lista, String sku) {
        String s = Textos.sku(sku);
        return s != null && lista.stream().anyMatch(p -> s.equals(Textos.sku(p.skuWeb())));
    }

    private static boolean igual(String a, String b) {
        return a != null && b != null && a.strip().equalsIgnoreCase(b.strip());
    }
}
```

`pedidos/AceptaSku.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Qué productos del maestro acepta el SKU de un pedido de cada canal (spec pieza 2 §6.4.1): Amazon, por «SKU (Amazon)» y
 *  «SKU (Amazon) con eSIM»; Particulares, por «SKU (stock fonestore.es) Conjunto»; BackiPhone, por «SKU BackiPhone»;
 *  Distribuidores y el Simulador, por el nuestro («SKU (stock web)»). Es puro: se construye con los productos que hagan falta
 *  ({@link com.reparaciones.servidor.nsys.NsysDAO#productosPorSkuDeCanal}). */
public final class AceptaSku {

    private final Map<String, List<ProductoMaestro>> nuestro = new HashMap<>();
    private final Map<String, List<ProductoMaestro>> amazon = new HashMap<>();
    private final Map<String, List<ProductoMaestro>> particulares = new HashMap<>();
    private final Map<String, List<ProductoMaestro>> backiphone = new HashMap<>();

    public AceptaSku(Collection<ProductoMaestro> productos) {
        for (ProductoMaestro p : productos) {
            poner(nuestro, p.skuWeb(), p);
            poner(amazon, p.skuAmazon(), p);
            poner(amazon, p.skuAmazonEsim(), p);
            poner(particulares, p.skuParticulares(), p);
            poner(backiphone, p.skuBackiphone(), p);
        }
    }

    /** Lo que acepta el SKU {@code sku} de un pedido de {@code canal}; null si el maestro no lo conoce. */
    public Aceptacion para(Canal canal, String sku) {
        String s = Textos.sku(sku);
        if (s == null) return null;
        Map<String, List<ProductoMaestro>> columna = switch (canal) {
            case AMAZON, AMAZON_STOCKROOM -> amazon;
            case PARTICULARES -> particulares;
            case BACKIPHONE -> backiphone;
            case DISTRIBUIDORES, SIMULADOR -> nuestro;
        };
        List<ProductoMaestro> productos = columna.get(s);
        if (productos == null || productos.isEmpty()) return null;
        List<ProductoMaestro> b = canal.esAmazon() && s.endsWith("a") ? amazon.getOrDefault(varianteB(s), List.of()) : List.of();
        return new Aceptacion(productos, b);
    }

    /** Los SKU más sus variantes B (los que acaban en «A»), para cargar del maestro todo lo que puede hacer falta. */
    public static Set<String> conVariantes(Collection<String> skus) {
        Set<String> todos = new LinkedHashSet<>();
        for (String sku : skus) {
            if (sku == null || sku.isBlank()) continue;
            String s = sku.strip();
            todos.add(s);
            if (s.endsWith("A") || s.endsWith("a")) todos.add(s.substring(0, s.length() - 1) + "B");
        }
        return todos;
    }

    private static String varianteB(String skuEnMinusculas) {
        return skuEnMinusculas.substring(0, skuEnMinusculas.length() - 1) + "b";
    }

    private static void poner(Map<String, List<ProductoMaestro>> mapa, String sku, ProductoMaestro p) {
        String s = Textos.sku(sku);
        if (s != null) mapa.computeIfAbsent(s, k -> new ArrayList<>()).add(p);
    }
}
```

- [ ] **Step 4: Ejecutar las pruebas**

Run: `mvn -q test -Dtest='CanalTest,TextosTest,AceptaSkuTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos src/test/java/com/reparaciones/servidor/pedidos
git commit -m "feat: canales, textos y que SKU acepta un pedido (0.9.26)"
```

---

### Task 3: ShippyPro — los pedidos pendientes

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/{ClienteShippyPro,PedidoShippyPro}.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/ClienteShippyProTest.java`

**Interfaces:**
- Produces: `record PedidoShippyPro(String numero, String marketplace, LocalDateTime fecha, String cliente, String pais,
  List<PedidoShippyPro.Articulo> articulos)` con `record Articulo(String sku, int cantidad)`; `ClienteShippyPro.pendientes():
  List<PedidoShippyPro>` (lanza `IOException` / `InterruptedException`).

- [ ] **Step 1: Escribir la prueba que falla**

`pedidos/ClienteShippyProTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** ShippyPro sin red: un HttpClient mockeado (sendAsync) y una clave fija (patrón de ClienteDriveTest). */
class ClienteShippyProTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteShippyPro cliente = new ClienteShippyPro(http, () -> "clave-1", Duration.ofSeconds(5));

    @SuppressWarnings("unchecked")
    private void responde(int estado, String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(cuerpo);
        doReturn(CompletableFuture.completedFuture(r)).when(http).sendAsync(any(HttpRequest.class), any());
    }

    @Test void pideLosPendientesConLaClaveYUnAgentePropioYLosTraduce() throws Exception {
        responde(200, """
                {"Result":"OK","Total":2,"Orders":[
                 {"transaction_id":"402-0000000-0000001","date":"1793629400","marketplace_id":"100","name":"Cliente A",
                  "country":"DE","merged":"0","items":[{"sku":"i13128AzulA","quantity":"2","title":"t","price":"1"},
                  {"sku":"","quantity":"1"}]},
                 {"transaction_id":"18832","date":"1793690392","marketplace_id":"300","name":"","country":"",
                  "items":[{"sku":"i14256AzulA","quantity":"1.0"}]}]}""");

        List<PedidoShippyPro> pedidos = cliente.pendientes();

        assertEquals(new PedidoShippyPro("402-0000000-0000001", "100", LocalDateTime.of(2026, 11, 2, 15, 23, 20), "Cliente A",
                "DE", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 2))), pedidos.get(0));
        assertEquals(new PedidoShippyPro("18832", "300", LocalDateTime.of(2026, 11, 3, 8, 19, 52), null, null,
                List.of(new PedidoShippyPro.Articulo("i14256AzulA", 1))), pedidos.get(1));
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(c.capture(), any());
        HttpRequest p = c.getValue();
        assertEquals("POST", p.method());
        assertEquals(ClienteShippyPro.API, p.uri().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("clave-1:".getBytes()), p.headers().firstValue("Authorization").orElseThrow());
        assertEquals(ClienteShippyPro.AGENTE, p.headers().firstValue("User-Agent").orElseThrow());
    }

    @Test void unaRespuestaQueNoEs200OUnResultQueNoEsOkEsUnFallo() {
        responde(403, "<html>");
        assertEquals("ShippyPro respondió 403", assertThrows(IOException.class, cliente::pendientes).getMessage());
        responde(200, "{\"Result\":\"ERROR\",\"Error\":\"Invalid API key\"}");
        assertEquals("ShippyPro: ERROR Invalid API key", assertThrows(IOException.class, cliente::pendientes).getMessage());
    }

    @Test void sinRutaDeLaClaveFallaAlPedirNoAlCrearse() {
        ClienteShippyPro sinClave = new ClienteShippyPro("");
        assertEquals("Falta el ajuste pedidos.shippypro.clave",
                assertThrows(IOException.class, sinClave::pendientes).getMessage());
    }
}
```

(1793629400 = 2026-11-02 14:23:20 UTC = 15:23:20 en Madrid, CET; 1793690392 = 2026-11-03 07:19:52 UTC = 08:19:52 en Madrid.)

- [ ] **Step 2: Ejecutar la prueba y ver que falla**

Run: `mvn -q test -Dtest='ClienteShippyProTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

`pedidos/PedidoShippyPro.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido pendiente de ShippyPro con lo mínimo para trabajar (spec pieza 2 §4.1): sin dirección, email, teléfono ni precios.
 *  {@code fecha} en hora de Madrid; {@code cliente} y {@code pais}, null si vienen vacíos. */
public record PedidoShippyPro(String numero, String marketplace, LocalDateTime fecha, String cliente, String pais,
                              List<Articulo> articulos) {

    public PedidoShippyPro {
        articulos = List.copyOf(articulos);
    }

    public record Articulo(String sku, int cantidad) {}
}
```

`pedidos/ClienteShippyPro.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Los pedidos pendientes de ShippyPro (spec pieza 2 §4.1): POST JSON con «Method»/«Params» y la clave por Basic (la clave como
 *  usuario, sin contraseña), como `devoluciones` de Automatización. Sin un User-Agent propio ShippyPro responde 403. */
@Component
public class ClienteShippyPro {

    static final String API = "https://www.shippypro.com/api";
    static final String AGENTE = "FSGR-ERP/0.9.26 (lectura de pedidos)";
    private static final Duration ESPERA_CABECERAS = Duration.ofSeconds(60);
    static final Duration ESPERA = Duration.ofMinutes(2);
    private static final String CUERPO = "{\"Method\":\"GetPendingOrders\",\"Params\":{}}";

    /** Da la clave de ShippyPro; los tests la sustituyen por una fija. */
    interface ProveedorClave {
        String clave() throws IOException;
    }

    private final HttpClient http;
    private final ProveedorClave clave;
    private final Duration espera;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public ClienteShippyPro(@Value("${pedidos.shippypro.clave:}") String ruta) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), deFichero(ruta), ESPERA);
    }

    ClienteShippyPro(HttpClient http, ProveedorClave clave, Duration espera) {
        this.http = http;
        this.clave = clave;
        this.espera = espera;
    }

    /** La clave se lee la primera vez que hace falta: el contexto arranca sin el fichero (tests y lectura apagada). */
    static ProveedorClave deFichero(String ruta) {
        return new ProveedorClave() {
            private String leida;

            @Override public synchronized String clave() throws IOException {
                if (leida == null) {
                    if (ruta == null || ruta.isBlank()) throw new IOException("Falta el ajuste pedidos.shippypro.clave");
                    leida = Files.readString(Path.of(ruta), StandardCharsets.UTF_8).strip();
                }
                return leida;
            }
        };
    }

    public List<PedidoShippyPro> pendientes() throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((clave.clave() + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(API)).timeout(ESPERA_CABECERAS)
                .header("Content-Type", "application/json").header("Authorization", "Basic " + basic)
                .header("User-Agent", AGENTE).POST(HttpRequest.BodyPublishers.ofString(CUERPO)).build();
        CompletableFuture<HttpResponse<String>> futura = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> respuesta;
        try {
            respuesta = futura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futura.cancel(true);
            throw new IOException("ShippyPro no respondió a tiempo");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("Fallo al pedir a ShippyPro", e.getCause());
        }
        if (respuesta.statusCode() != 200) throw new IOException("ShippyPro respondió " + respuesta.statusCode());
        JsonNode cuerpo = json.readTree(respuesta.body());
        String resultado = cuerpo.path("Result").asText("");
        if (!"OK".equals(resultado))
            throw new IOException(("ShippyPro: " + resultado + " " + cuerpo.path("Error").asText("")).strip());
        List<PedidoShippyPro> pedidos = new ArrayList<>();
        for (JsonNode o : cuerpo.path("Orders")) pedidos.add(pedido(o));
        return pedidos;
    }

    static PedidoShippyPro pedido(JsonNode o) {
        LocalDateTime fecha = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(o.path("date").asText("0").strip())),
                ImportadorNsys.MADRID);
        List<PedidoShippyPro.Articulo> articulos = new ArrayList<>();
        for (JsonNode i : o.path("items")) {
            String sku = i.path("sku").asText("").strip();
            int cantidad = cantidad(i.path("quantity").asText(""));
            if (!sku.isEmpty() && cantidad > 0) articulos.add(new PedidoShippyPro.Articulo(sku, cantidad));
        }
        return new PedidoShippyPro(o.path("transaction_id").asText("").strip(), o.path("marketplace_id").asText("").strip(),
                fecha, vacioANulo(o.path("name").asText("")), vacioANulo(o.path("country").asText("")), articulos);
    }

    private static int cantidad(String texto) {
        try {
            return new BigDecimal(texto.strip()).intValue();
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
```

- [ ] **Step 4: Ejecutar la prueba**

Run: `mvn -q test -Dtest='ClienteShippyProTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos src/test/java/com/reparaciones/servidor/pedidos
git commit -m "feat: lectura de los pedidos pendientes de ShippyPro (0.9.26)"
```

---

### Task 4: Hojas de Google — PEDIDOS 2026 y el Simulador

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/{ClienteSheets,TablaHoja,FormatoHojaException,FilaPedidos2026,FilaSimulador,LectorPedidos2026,LectorSimulador}.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/{ClienteSheetsTest,LectoresHojaTest}.java`

**Interfaces:**
- Produces:
  - `record TablaHoja(int total, Map<String, Integer> columnas, List<TablaHoja.Fila> filas)` con `record Fila(int numero,
    List<JsonNode> celdas)` y `JsonNode celda(Fila f, String columna)` (null si la columna no está o la fila es más corta).
  - `ClienteSheets.leer(String hoja, String pestana, List<String> obligatorias, String columnaClave, int filas): TablaHoja`
    (lanza `FormatoHojaException` si falta una columna, `IOException`, `InterruptedException`).
  - `record FilaPedidos2026(int fila, LocalDateTime fecha, String plataforma, String sku, String imei)` y
    `LectorPedidos2026.filas(TablaHoja): List<FilaPedidos2026>`, `LectorPedidos2026.OBLIGATORIAS`, `CLAVE = "SKU"`.
  - `record FilaSimulador(int fila, String orderId, String linea, LocalDate fecha, String cliente, String sku, int cantidad,
    String imei)` y `LectorSimulador.filas(TablaHoja)`, `LectorSimulador.OBLIGATORIAS`, `CLAVE = "Order id"`.
  - `static LocalDateTime TablaHoja.fecha(JsonNode)` y `static String TablaHoja.texto(JsonNode)`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`pedidos/ClienteSheetsTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClienteSheetsTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteSheets sheets = new ClienteSheets(http, () -> "tok-1", Duration.ofSeconds(5));

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<String>> respuesta(String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(cuerpo);
        return CompletableFuture.completedFuture(r);
    }

    @Test void leeLaCabeceraCuentaLasFilasPorLaColumnaClaveYTraeSoloLasUltimas() throws Exception {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"Fecha_Traspaso\",\"Plataforma\",\"SKU\",\"Pendiente\",\"IMEI\"]]}"),
                respuesta("{\"values\":[[\"SKU\"],[\"a\"],[\"b\"],[\"c\"],[\"d\"]]}"),
                respuesta("{\"values\":[[46000.5,\"\",\"Amazon\",\"c\",\"\",\"350000000000001\"],[46001,\"\",\"Amazon\",\"d\"]]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        TablaHoja t = sheets.leer("hoja-1", "PEDIDOS 2026", List.of("Fecha", "Plataforma", "SKU", "IMEI"), "SKU", 2);

        assertEquals(4, t.total());
        assertEquals(Map.of("Fecha", 0, "Fecha_Traspaso", 1, "Plataforma", 2, "SKU", 3, "Pendiente", 4, "IMEI", 5), t.columnas());
        assertEquals(List.of(4, 5), t.filas().stream().map(TablaHoja.Fila::numero).toList());
        assertEquals("350000000000001", t.celda(t.filas().get(0), "IMEI").asText());
        assertNull(t.celda(t.filas().get(1), "IMEI"), "fila más corta: celda vacía");
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).sendAsync(c.capture(), any());
        List<String> rangos = c.getAllValues().stream()
                .map(p -> URLDecoder.decode(p.uri().getRawPath(), StandardCharsets.UTF_8)).toList();
        assertTrue(rangos.get(0).endsWith("/hoja-1/values/'PEDIDOS 2026'!1:1"), rangos.get(0));
        assertTrue(rangos.get(1).endsWith("/values/'PEDIDOS 2026'!D:D"), rangos.get(1));
        assertTrue(rangos.get(2).endsWith("/values/'PEDIDOS 2026'!A4:F5"), rangos.get(2));
        assertTrue(c.getAllValues().get(2).uri().getQuery().contains("valueRenderOption=UNFORMATTED_VALUE"));
        assertEquals("Bearer tok-1", c.getAllValues().get(0).headers().firstValue("Authorization").orElseThrow());
    }

    @Test void unaColumnaQueFaltaRechazaLaHoja() {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"SKU\"]]}")).when(http).sendAsync(any(HttpRequest.class), any());
        FormatoHojaException e = assertThrows(FormatoHojaException.class,
                () -> sheets.leer("h", "PEDIDOS 2026", List.of("Fecha", "Plataforma", "SKU", "IMEI"), "SKU", 10));
        assertEquals("Falta la columna «Plataforma» en «PEDIDOS 2026»", e.getMessage());
    }

    @Test void lasLetrasDeLasColumnas() {
        assertEquals(List.of("A", "Z", "AA", "AF"), List.of(ClienteSheets.letra(0), ClienteSheets.letra(25),
                ClienteSheets.letra(26), ClienteSheets.letra(31)));
    }
}
```

`pedidos/LectoresHojaTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LectoresHojaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Una tabla con esta cabecera y estas filas (en JSON, como las da Sheets), numeradas desde la 2. */
    static TablaHoja tabla(List<String> cabecera, String... filas) throws Exception {
        Map<String, Integer> columnas = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) columnas.put(cabecera.get(i), i);
        List<TablaHoja.Fila> lista = new ArrayList<>();
        for (int i = 0; i < filas.length; i++) {
            List<JsonNode> celdas = new ArrayList<>();
            JSON.readTree(filas[i]).forEach(celdas::add);
            lista.add(new TablaHoja.Fila(i + 2, celdas));
        }
        return new TablaHoja(filas.length, columnas, lista);
    }

    @Test void pedidos2026SoloTraeLasVentasConImeiYFecha() throws Exception {
        TablaHoja t = tabla(List.of("Fecha", "Plataforma", "SKU", "IMEI"),
                "[46329.5, \"Amazon\", \"i13128AzulA\", \"350000000000001\"]",
                "[46329, \"NP\", \"i13128AzulA\", \"350000000000002\"]",
                "[46329, \"Tienda Norte SL\", \"i14256AzulA\", 350000000000003]",
                "[46329, \"INCIDENCIA AMZ\", \"i13128AzulA\", \"350000000000004\"]",
                "[46329, \"Amazon\", \"i13128AzulA\", \"cancelado\"]",
                "[\"\", \"Amazon\", \"i13128AzulA\", \"350000000000006\"]",
                "[46329, \"Amazon\", \"\", \"350000000000007\"]");

        assertEquals(List.of(
                new FilaPedidos2026(2, LocalDateTime.of(2026, 11, 3, 12, 0), "Amazon", "i13128AzulA", "350000000000001"),
                new FilaPedidos2026(4, LocalDateTime.of(2026, 11, 3, 0, 0), "Tienda Norte SL", "i14256AzulA", "350000000000003")),
                LectorPedidos2026.filas(t));
    }

    @Test void simuladorTraeCadaLineaConSuImeiSiLoTiene() throws Exception {
        TablaHoja t = tabla(List.of("Order id", "Linea facturación", "Fecha", "Cliente", "SKU", "Cantidad", "IMEI"),
                "[6488, \"X\", 46328, \"Móviles Levante SL\", \"i13128AzulA\", 1, \"350000000000001\"]",
                "[6488, \"x\", 46328, \"Móviles Levante SL\", \"i14128NegroA\", 1, \"\"]",
                "[6490, \"BB\", 46330, \"TecnoSur\", \"i13128AzulA\", -1, \"350000000000001\"]",
                "[\"\", \"X\", 46330, \"TecnoSur\", \"i13128AzulA\", 1, \"\"]");

        assertEquals(List.of(
                new FilaSimulador(2, "6488", "X", LocalDate.of(2026, 11, 2), "Móviles Levante SL", "i13128AzulA", 1, "350000000000001"),
                new FilaSimulador(3, "6488", "X", LocalDate.of(2026, 11, 2), "Móviles Levante SL", "i14128NegroA", 1, null),
                new FilaSimulador(4, "6490", "BB", LocalDate.of(2026, 11, 4), "TecnoSur", "i13128AzulA", -1, "350000000000001")),
                LectorSimulador.filas(t));
    }

    @Test void lasFechasLleganComoNumeroDeSerieOComoTexto() throws Exception {
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), TablaHoja.fecha(JSON.readTree("46329.25")));
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), TablaHoja.fecha(JSON.readTree("\"03/11/2026\"")));
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 30), TablaHoja.fecha(JSON.readTree("\"03/11/2026 9:30:00\"")));
        assertNull(TablaHoja.fecha(JSON.readTree("\"\"")));
        assertNull(TablaHoja.fecha(null));
    }
}
```

(46329 es el número de serie de Google/Excel del 03/11/2026: días desde el 30/12/1899.)

- [ ] **Step 2: Ejecutar las pruebas y ver que fallan**

Run: `mvn -q test -Dtest='ClienteSheetsTest,LectoresHojaTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

`pedidos/FormatoHojaException.java`:

```java
package com.reparaciones.servidor.pedidos;

/** La hoja no tiene las columnas que hacen falta: la lectura se rechaza (RECHAZADA) y la píldora lo dice. */
public class FormatoHojaException extends RuntimeException {
    public FormatoHojaException(String mensaje) {
        super(mensaje);
    }
}
```

`pedidos/TablaHoja.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Las últimas filas de una pestaña: {@code total}, cuántas filas de datos tiene (sin la cabecera); {@code columnas}, cabecera
 *  → posición; cada fila con su número en la hoja. Las celdas son JSON sin formato (números, textos; fechas como número de
 *  serie). */
public record TablaHoja(int total, Map<String, Integer> columnas, List<Fila> filas) {

    private static final LocalDateTime CERO = LocalDateTime.of(1899, 12, 30, 0, 0);
    private static final List<DateTimeFormatter> FORMATOS = List.of(DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm"));
    private static final DateTimeFormatter SOLO_DIA = DateTimeFormatter.ofPattern("d/M/yyyy");

    public TablaHoja {
        columnas = Map.copyOf(columnas);
        filas = List.copyOf(filas);
    }

    public record Fila(int numero, List<JsonNode> celdas) {}

    /** La celda de esa columna en esa fila; null si la columna no está o la fila no llega (Sheets corta las vacías del final). */
    public JsonNode celda(Fila f, String columna) {
        Integer i = columnas.get(columna);
        return i == null || i >= f.celdas().size() ? null : f.celdas().get(i);
    }

    /** Una celda como texto, sin espacios a los lados; los números enteros sin «.0»; null si está vacía. */
    public static String texto(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        String s = v.isNumber() && v.decimalValue().stripTrailingZeros().scale() <= 0
                ? v.decimalValue().toBigInteger().toString() : v.asText();
        s = s.strip();
        return s.isEmpty() ? null : s;
    }

    /** Una fecha como número de serie (días desde el 30/12/1899, con la hora en la parte decimal) o como texto «d/M/yyyy[ H:mm[:ss]]». */
    public static LocalDateTime fecha(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        if (v.isNumber()) return CERO.plusSeconds(Math.round(v.asDouble() * 86_400));
        String s = v.asText().strip();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : FORMATOS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException e) {
                // otro formato
            }
        }
        try {
            return LocalDate.parse(s, SOLO_DIA).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
```

`pedidos/ClienteSheets.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Lee pestañas de hojas de Google con la API de Sheets, solo lectura (spec pieza 2 §4.2-4.3), con la cuenta de servicio de la
 *  pieza 1 (`nsys.drive.credenciales`). Cada lectura son tres llamadas: la cabecera, la columna clave (para saber cuántas filas
 *  hay) y las últimas N filas (ajuste 3 de la spec). */
@Component
public class ClienteSheets {

    static final String API = "https://sheets.googleapis.com/v4/spreadsheets/";
    static final String ALCANCE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    private static final Duration ESPERA_CABECERAS = Duration.ofSeconds(60);
    static final Duration ESPERA = Duration.ofMinutes(2);
    private static final String OPCIONES = "?valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER&majorDimension=ROWS";

    interface ProveedorToken {
        String token() throws IOException;
    }

    private final HttpClient http;
    private final ProveedorToken tokens;
    private final Duration espera;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public ClienteSheets(@Value("${nsys.drive.credenciales:}") String credenciales) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                cuentaDeServicio(credenciales), ESPERA);
    }

    ClienteSheets(HttpClient http, ProveedorToken tokens, Duration espera) {
        this.http = http;
        this.tokens = tokens;
        this.espera = espera;
    }

    static ProveedorToken cuentaDeServicio(String ruta) {
        return new ProveedorToken() {
            private GoogleCredentials credenciales;

            @Override public synchronized String token() throws IOException {
                if (credenciales == null) {
                    if (ruta == null || ruta.isBlank()) throw new IOException("Falta el ajuste nsys.drive.credenciales");
                    try (InputStream in = Files.newInputStream(Path.of(ruta))) {
                        credenciales = GoogleCredentials.fromStream(in).createScoped(List.of(ALCANCE));
                    }
                }
                credenciales.refreshIfExpired();
                return credenciales.getAccessToken().getTokenValue();
            }
        };
    }

    public TablaHoja leer(String hoja, String pestana, List<String> obligatorias, String columnaClave, int filas)
            throws IOException, InterruptedException {
        String p = "'" + pestana.replace("'", "''") + "'!";
        List<List<JsonNode>> cabecera = valores(hoja, p + "1:1");
        Map<String, Integer> columnas = new HashMap<>();
        if (!cabecera.isEmpty()) {
            List<JsonNode> c = cabecera.get(0);
            for (int i = 0; i < c.size(); i++) {
                String nombre = TablaHoja.texto(c.get(i));
                if (nombre != null) columnas.putIfAbsent(nombre, i);
            }
        }
        for (String o : obligatorias)
            if (!columnas.containsKey(o)) throw new FormatoHojaException("Falta la columna «" + o + "» en «" + pestana + "»");
        String clave = letra(columnas.get(columnaClave));
        int ultima = valores(hoja, p + clave + ":" + clave).size();
        if (ultima <= 1) return new TablaHoja(0, columnas, List.of());
        int desde = Math.max(2, ultima - filas + 1);
        int ancho = obligatorias.stream().mapToInt(columnas::get).max().orElse(0);
        List<List<JsonNode>> cola = valores(hoja, p + "A" + desde + ":" + letra(ancho) + ultima);
        List<TablaHoja.Fila> lista = new ArrayList<>();
        for (int i = 0; i < cola.size(); i++) lista.add(new TablaHoja.Fila(desde + i, cola.get(i)));
        return new TablaHoja(ultima - 1, columnas, lista);
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String letra(int indice) {
        StringBuilder s = new StringBuilder();
        for (int n = indice + 1; n > 0; n = (n - 1) / 26) s.insert(0, (char) ('A' + (n - 1) % 26));
        return s.toString();
    }

    private List<List<JsonNode>> valores(String hoja, String rango) throws IOException, InterruptedException {
        String url = API + URLEncoder.encode(hoja, StandardCharsets.UTF_8) + "/values/"
                + URLEncoder.encode(rango, StandardCharsets.UTF_8).replace("+", "%20") + OPCIONES;
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url)).timeout(ESPERA_CABECERAS)
                .header("Authorization", "Bearer " + tokens.token()).GET().build();
        CompletableFuture<HttpResponse<String>> futura = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> respuesta;
        try {
            respuesta = futura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futura.cancel(true);
            throw new IOException("Google Sheets no respondió a tiempo");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("Fallo al pedir a Google Sheets", e.getCause());
        }
        if (respuesta.statusCode() != 200) throw new IOException("Google Sheets respondió " + respuesta.statusCode());
        List<List<JsonNode>> filas = new ArrayList<>();
        for (JsonNode fila : json.readTree(respuesta.body()).path("values")) {
            List<JsonNode> celdas = new ArrayList<>();
            fila.forEach(celdas::add);
            filas.add(celdas);
        }
        return filas;
    }
}
```

(En la prueba, la columna «SKU» es la D: `D:D`. La cola va de la A a la última columna obligatoria, la F.)

`pedidos/FilaPedidos2026.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una venta de PEDIDOS 2026 con su IMEI (spec pieza 2 §4.2): {@code fila}, su número en la hoja; {@code fecha}, cuándo se
 *  tecleó el IMEI; {@code plataforma}, «Amazon» o el nombre del cliente. */
public record FilaPedidos2026(int fila, LocalDateTime fecha, String plataforma, String sku, String imei) {}
```

`pedidos/LectorPedidos2026.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Las ventas con IMEI de PEDIDOS 2026 (spec pieza 2 §4.2 y ajuste 4). Una fila es venta con la regla de Deduccion_IMEIs.gs:
 *  Plataforma no vacía, ni NP, ni con INCIDENCIA o CANCEL. Sin IMEI de 15 cifras, sin SKU o sin fecha, no cuenta. */
public final class LectorPedidos2026 {

    public static final List<String> OBLIGATORIAS = List.of("Fecha", "Plataforma", "SKU", "IMEI");
    public static final String CLAVE = "SKU";

    private LectorPedidos2026() {}

    public static List<FilaPedidos2026> filas(TablaHoja t) {
        List<FilaPedidos2026> ventas = new ArrayList<>();
        for (TablaHoja.Fila f : t.filas()) {
            String plataforma = TablaHoja.texto(t.celda(f, "Plataforma"));
            if (!esVenta(plataforma)) continue;
            String imei = Textos.imei(t.celda(f, "IMEI"));
            String sku = TablaHoja.texto(t.celda(f, "SKU"));
            LocalDateTime fecha = TablaHoja.fecha(t.celda(f, "Fecha"));
            if (imei == null || sku == null || fecha == null) continue;
            ventas.add(new FilaPedidos2026(f.numero(), fecha, plataforma, sku, imei));
        }
        return ventas;
    }

    static boolean esVenta(String plataforma) {
        if (plataforma == null) return false;
        String p = plataforma.strip().toUpperCase(Locale.ROOT);
        return !p.isEmpty() && !p.equals("NP") && !p.contains("INCIDENCIA") && !p.contains("CANCEL");
    }
}
```

`pedidos/FilaSimulador.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;

/** Una línea de «Registro actividad» del Simulador (spec pieza 2 §4.3): {@code linea} en mayúsculas (X, E, BB…); {@code imei}
 *  null si todavía no se ha tecleado. */
public record FilaSimulador(int fila, String orderId, String linea, LocalDate fecha, String cliente, String sku, int cantidad,
                            String imei) {}
```

`pedidos/LectorSimulador.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Las líneas del Simulador (spec pieza 2 §4.3). Sin Order id, sin SKU o sin fecha, no cuentan. No lee precios, IVA, forma de
 *  pago ni nada de facturación. */
public final class LectorSimulador {

    public static final List<String> OBLIGATORIAS = List.of("Order id", "Linea facturación", "Fecha", "Cliente", "SKU",
            "Cantidad", "IMEI");
    public static final String CLAVE = "Order id";

    private LectorSimulador() {}

    public static List<FilaSimulador> filas(TablaHoja t) {
        List<FilaSimulador> lineas = new ArrayList<>();
        for (TablaHoja.Fila f : t.filas()) {
            String orderId = TablaHoja.texto(t.celda(f, "Order id"));
            String sku = TablaHoja.texto(t.celda(f, "SKU"));
            LocalDateTime fecha = TablaHoja.fecha(t.celda(f, "Fecha"));
            if (orderId == null || sku == null || fecha == null) continue;
            String linea = TablaHoja.texto(t.celda(f, "Linea facturación"));
            lineas.add(new FilaSimulador(f.numero(), orderId, linea == null ? "" : linea.toUpperCase(Locale.ROOT),
                    fecha.toLocalDate(), TablaHoja.texto(t.celda(f, "Cliente")), sku, cantidad(TablaHoja.texto(t.celda(f, "Cantidad"))),
                    Textos.imei(t.celda(f, "IMEI"))));
        }
        return lineas;
    }

    private static int cantidad(String texto) {
        if (texto == null) return 1;
        try {
            return new BigDecimal(texto).intValue();
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
```

- [ ] **Step 4: Ejecutar las pruebas**

Run: `mvn -q test -Dtest='ClienteSheetsTest,LectoresHojaTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos src/test/java/com/reparaciones/servidor/pedidos
git commit -m "feat: lectura de PEDIDOS 2026 y del Simulador por la API de Google Sheets (0.9.26)"
```

---

### Task 5: El cruce de las tres fuentes

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/{PedidoLeido,CrucePedidos}.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/CrucePedidosTest.java`

**Interfaces:**
- Consumes: `PedidoShippyPro` (Task 3), `FilaPedidos2026`, `FilaSimulador` (Task 4), `AceptaSku`, `Canal`, `FuentePedidos`,
  `Textos` (Task 2), `MovilNsys`, `EstadoMovil`, `NsysDAO.claveCruce` (nsys).
- Produces:
  - `record PedidoLeido(Canal canal, String numero, String cliente, String pais, LocalDateTime fecha, FuentePedidos fuente,
    List<PedidoLeido.Unidad> unidades)` con `record Unidad(int posicion, String sku, String imei, FuentePedidos imeiFuente,
    Integer imeiRef, LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto, String otroOrderId, String motivoCierre)`.
  - `CrucePedidos.cruzar(CrucePedidos.Entrada): CrucePedidos.Resultado`, con `record Entrada(List<PedidoShippyPro> shippyPro,
    Map<String, Canal> canales, List<FilaPedidos2026> pedidos2026, List<FilaSimulador> simulador, AceptaSku acepta,
    Map<String, MovilNsys> moviles)` (los móviles por `NsysDAO.claveCruce`) y `record Resultado(List<PedidoLeido> pedidos,
    Set<String> desconocidosShippyPro, Set<String> desconocidosSimulador)`.

Reglas (spec §6.1-6.3, ajustes 4 y 5):
- **Simulador:** cada IMEI cuenta una vez, en la primera línea que no es BB (por número de fila); su unidad lleva el Order id de
  otra línea del mismo IMEI en `otroOrderId`. Una línea BB cierra la unidad de su IMEI (`ABONO`); un IMEI en Released la cierra
  (`RELEASED`). Las líneas sin IMEI son unidades sin móvil. Un SKU que no conoce el maestro no es unidad (y si parece de teléfono,
  va a `desconocidosSimulador`). Un pedido sin unidades no sale. Fecha del pedido: la más antigua de sus líneas.
- **ShippyPro:** solo los marketplaces del ajuste; cantidad N → N unidades (posiciones 1…N en el orden de los artículos).
- **PEDIDOS 2026:** no cuentan las filas cuyo IMEI está en una unidad del Simulador ni las de un IMEI en Released. Una fila cubre
  una unidad si su SKU es el de la unidad o uno de los nuestros que acepta, y su fecha es del mismo día o posterior a la del
  pedido.
  - **Amazon (y Stockroom):** filas con Plataforma «Amazon»; las unidades, de la más antigua a la más nueva, cogen la mejor fila
    libre: primero Listos, luego taller (ubicación que empieza por «Taller»), luego el resto; en empate, la de fecha de
    recepción más antigua y después la de número de fila más bajo. Llevan `sinPedidoConcreto`.
  - **Webs:** filas cuya Plataforma es el cliente del pedido (`Textos.cliente`); las unidades, de la más antigua a la más nueva,
    cogen la primera fila libre (por número de fila).

- [ ] **Step 1: Escribir la prueba que falla**

`pedidos/CrucePedidosTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.ProductoMaestro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CrucePedidosTest {

    private static final Map<String, Canal> CANALES = Map.of("100", Canal.AMAZON, "200", Canal.AMAZON_STOCKROOM,
            "300", Canal.DISTRIBUIDORES);
    private static final AceptaSku ACEPTA = new AceptaSku(List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "A-", "i13128AzulA-", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Negro", "A", "i13128NegroA", "i13128NegroA", null, null)));

    private static LocalDateTime dia(int d) {
        return LocalDate.of(2026, 10, d).atTime(10, 0);
    }

    private static PedidoShippyPro amazon(String numero, int d, String sku, int cantidad) {
        return new PedidoShippyPro(numero, "100", dia(d), "Comprador " + numero, "DE", List.of(new PedidoShippyPro.Articulo(sku, cantidad)));
    }

    private static MovilNsys movil(String imei, String ubicacion, EstadoMovil estado, LocalDate recepcion) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA", "Proveedor A", recepcion,
                null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, ubicacion, ubicacion, estado, null, null);
    }

    private static CrucePedidos.Resultado cruzar(List<PedidoShippyPro> s, List<FilaPedidos2026> p, List<FilaSimulador> m,
                                                 Map<String, MovilNsys> moviles) {
        return CrucePedidos.cruzar(new CrucePedidos.Entrada(s, CANALES, p, m, ACEPTA, moviles));
    }

    private static PedidoLeido pedido(CrucePedidos.Resultado r, String numero) {
        return r.pedidos().stream().filter(x -> x.numero().equals(numero)).findFirst().orElseThrow();
    }

    @Test void amazonDaLosImeisTecleadosALosPedidosMasAntiguosYElMasAvanzadoAlPrimero() {
        Map<String, MovilNsys> moviles = new HashMap<>();
        moviles.put("350000000000001", movil("350000000000001", "Taller · Glass", EstadoMovil.STOCK, LocalDate.of(2026, 9, 1)));
        moviles.put("350000000000002", movil("350000000000002", "Listos", EstadoMovil.STOCK, LocalDate.of(2026, 9, 20)));
        CrucePedidos.Resultado r = cruzar(
                List.of(amazon("402-0000000-0000003", 30, "i13128AzulA", 1), amazon("402-0000000-0000001", 28, "i13128AzulA", 1),
                        amazon("402-0000000-0000002", 29, "I13128AZULA", 1)),
                List.of(new FilaPedidos2026(10, dia(29), "Amazon", "i13128AzulA", "350000000000001"),
                        new FilaPedidos2026(11, dia(30), "amazon", "i13128AzulA-", "350000000000002")),
                List.of(), moviles);

        PedidoLeido.Unidad primero = pedido(r, "402-0000000-0000001").unidades().get(0);
        assertEquals("350000000000002", primero.imei(), "el de Listos va al pedido más antiguo");
        assertTrue(primero.sinPedidoConcreto());
        assertEquals(FuentePedidos.PEDIDOS_2026, primero.imeiFuente());
        assertEquals(11, primero.imeiRef());
        assertEquals("350000000000001", pedido(r, "402-0000000-0000002").unidades().get(0).imei());
        assertNull(pedido(r, "402-0000000-0000003").unidades().get(0).imei(), "dos IMEIs cubren los dos más antiguos");
    }

    @Test void unaFilaAnteriorAlPedidoOConElImeiEnReleasedNoCuenta() {
        Map<String, MovilNsys> moviles = Map.of("350000000000002",
                movil("350000000000002", "Listos", EstadoMovil.VENDIDO, LocalDate.of(2026, 9, 1)));
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000001", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(5, dia(27), "Amazon", "i13128AzulA", "350000000000001"),
                        new FilaPedidos2026(6, dia(29), "Amazon", "i13128AzulA", "350000000000002")),
                List.of(), moviles);
        assertNull(pedido(r, "402-0000000-0000001").unidades().get(0).imei());
    }

    @Test void unaCantidadDeDosSonDosUnidadesYUnSkuDesconocidoSeApunta() {
        CrucePedidos.Resultado r = cruzar(List.of(new PedidoShippyPro("402-0000000-0000009", "100", dia(28), "C", "DE",
                        List.of(new PedidoShippyPro.Articulo("i13128NegroA", 2), new PedidoShippyPro.Articulo("i99999RaroA", 1),
                                new PedidoShippyPro.Articulo("FUNDA-1", 1))),
                new PedidoShippyPro("1", "999", dia(28), "Otro", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(), List.of(), Map.of());
        assertEquals(List.of(1, 2), pedido(r, "402-0000000-0000009").unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
        assertEquals(Set.of("i99999RaroA"), r.desconocidosShippyPro());
        assertTrue(r.pedidos().stream().noneMatch(p -> p.numero().equals("1")), "un marketplace fuera del ajuste se ignora");
    }

    @Test void laWebSeCruzaPorClienteYSkuAlPedidoAbiertoMasAntiguo() {
        CrucePedidos.Resultado r = cruzar(List.of(
                        new PedidoShippyPro("18840", "300", dia(29), "Tienda Norte SL", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1))),
                        new PedidoShippyPro("18832", "300", dia(28), "TIENDA NORTE S.L.", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1))),
                        new PedidoShippyPro("18833", "300", dia(28), "Tienda Norte SL", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(new FilaPedidos2026(20, dia(30), "tiénda norte sl", "i13128AzulA", "350000000000005")),
                List.of(), Map.of());
        assertEquals("350000000000005", pedido(r, "18833").unidades().get(0).imei());
        assertFalse(pedido(r, "18833").unidades().get(0).sinPedidoConcreto());
        assertNull(pedido(r, "18840").unidades().get(0).imei());
        assertNull(pedido(r, "18832").unidades().get(0).imei(), "otro nombre («S.L.») no es el mismo cliente");
    }

    @Test void simuladorCuentaCadaImeiUnaVezCierraLosAbonadosYNoRepiteEnPedidos2026() {
        Map<String, MovilNsys> moviles = Map.of("350000000000003",
                movil("350000000000003", "Listos", EstadoMovil.VENDIDO, LocalDate.of(2026, 9, 1)));
        CrucePedidos.Resultado r = cruzar(List.of(new PedidoShippyPro("18850", "300", dia(28), "Móviles Levante SL", "ES",
                        List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(new FilaPedidos2026(30, dia(29), "Móviles Levante SL", "i13128AzulA", "350000000000001")),
                List.of(new FilaSimulador(2, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128AzulA", 1, "350000000000001"),
                        new FilaSimulador(3, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128NegroA", 1, null),
                        new FilaSimulador(4, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "GLS24", 1, null),
                        new FilaSimulador(5, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128AzulA", 1, "350000000000002"),
                        new FilaSimulador(6, "6481", "E", LocalDate.of(2026, 10, 28), "Móviles Levante SL", "i13128AzulA", 1, "350000000000001"),
                        new FilaSimulador(7, "6482", "X", LocalDate.of(2026, 10, 28), "TecnoSur", "i13128AzulA", 1, "350000000000003"),
                        new FilaSimulador(8, "6483", "BB", LocalDate.of(2026, 10, 29), "Móviles Levante SL", "i13128AzulA", -1, "350000000000002")),
                moviles);

        PedidoLeido x = pedido(r, "6480");
        assertEquals(Canal.SIMULADOR, x.canal());
        assertEquals(LocalDate.of(2026, 10, 27).atStartOfDay(), x.fecha());
        assertEquals(3, x.unidades().size(), "GLS24 no es un teléfono");
        assertEquals("6481", x.unidades().get(0).otroOrderId());
        assertEquals(FuentePedidos.SIMULADOR, x.unidades().get(0).imeiFuente());
        assertNull(x.unidades().get(1).imei());
        assertEquals("ABONO", x.unidades().get(2).motivoCierre());
        assertTrue(r.pedidos().stream().noneMatch(p -> p.numero().equals("6481")), "la E solo repite IMEIs: no hay pedido");
        assertEquals("RELEASED", pedido(r, "6482").unidades().get(0).motivoCierre());
        assertNull(pedido(r, "18850").unidades().get(0).imei(), "el IMEI del Simulador no se usa otra vez en PEDIDOS 2026");
        assertTrue(r.desconocidosSimulador().isEmpty(), "GLS24 no parece un teléfono");
    }
}
```

- [ ] **Step 2: Ejecutar la prueba y ver que falla**

Run: `mvn -q test -Dtest='CrucePedidosTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

`pedidos/PedidoLeido.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido tal como sale del cruce de una vuelta (spec pieza 2 §6), antes de guardarlo. */
public record PedidoLeido(Canal canal, String numero, String cliente, String pais, LocalDateTime fecha, FuentePedidos fuente,
                          List<Unidad> unidades) {

    public PedidoLeido {
        unidades = List.copyOf(unidades);
    }

    /** {@code motivoCierre}: RELEASED o ABONO (solo el Simulador); null si sigue abierta. */
    public record Unidad(int posicion, String sku, String imei, FuentePedidos imeiFuente, Integer imeiRef,
                         LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto, String otroOrderId, String motivoCierre) {}
}
```

`pedidos/CrucePedidos.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Junta las tres fuentes de una vuelta en pedidos con sus unidades y su IMEI (spec pieza 2 §6.1-6.3). Es puro. */
public final class CrucePedidos {

    public record Entrada(List<PedidoShippyPro> shippyPro, Map<String, Canal> canales, List<FilaPedidos2026> pedidos2026,
                          List<FilaSimulador> simulador, AceptaSku acepta, Map<String, MovilNsys> moviles) {}

    public record Resultado(List<PedidoLeido> pedidos, Set<String> desconocidosShippyPro, Set<String> desconocidosSimulador) {}

    /** Una unidad de ShippyPro mientras se le busca IMEI. */
    private static final class Hueco {
        final PedidoShippyPro pedido;
        final int posicion;
        final String sku;
        final Aceptacion aceptacion;
        FilaPedidos2026 fila;
        boolean sinPedidoConcreto;

        Hueco(PedidoShippyPro pedido, int posicion, String sku, Aceptacion aceptacion) {
            this.pedido = pedido;
            this.posicion = posicion;
            this.sku = sku;
            this.aceptacion = aceptacion;
        }
    }

    private CrucePedidos() {}

    public static Resultado cruzar(Entrada e) {
        Set<String> desconocidosSimulador = new TreeSet<>();
        List<PedidoLeido> pedidos = new ArrayList<>(simulador(e, desconocidosSimulador));
        Set<String> delSimulador = new HashSet<>();
        for (PedidoLeido p : pedidos)
            for (PedidoLeido.Unidad u : p.unidades()) if (u.imei() != null) delSimulador.add(NsysDAO.claveCruce(u.imei()));

        Set<String> desconocidosShippyPro = new TreeSet<>();
        Map<PedidoShippyPro, Canal> canalDe = new LinkedHashMap<>();
        List<Hueco> huecos = new ArrayList<>();
        for (PedidoShippyPro p : e.shippyPro()) {
            Canal canal = e.canales().get(p.marketplace());
            if (canal == null) continue;
            canalDe.put(p, canal);
            int posicion = 0;
            for (PedidoShippyPro.Articulo a : p.articulos()) {
                Aceptacion ac = e.acepta().para(canal, a.sku());
                if (ac == null) {
                    if (Textos.pareceTelefono(a.sku())) desconocidosShippyPro.add(a.sku());
                    continue;
                }
                for (int i = 0; i < a.cantidad(); i++) huecos.add(new Hueco(p, ++posicion, a.sku(), ac));
            }
        }
        huecos.sort(Comparator.comparing((Hueco h) -> h.pedido.fecha()).thenComparing(h -> h.pedido.numero())
                .thenComparingInt(h -> h.posicion));

        List<FilaPedidos2026> filas = e.pedidos2026().stream()
                .filter(f -> !delSimulador.contains(NsysDAO.claveCruce(f.imei())))
                .filter(f -> !vendido(e.moviles().get(NsysDAO.claveCruce(f.imei()))))
                .toList();
        repartirAmazon(huecos, canalDe, filas, e.moviles());
        repartirWebs(huecos, canalDe, filas);

        Map<PedidoShippyPro, List<PedidoLeido.Unidad>> unidades = new LinkedHashMap<>();
        for (Hueco h : huecos) {
            FilaPedidos2026 f = h.fila;
            unidades.computeIfAbsent(h.pedido, k -> new ArrayList<>()).add(new PedidoLeido.Unidad(h.posicion, h.sku,
                    f == null ? null : f.imei(), f == null ? null : FuentePedidos.PEDIDOS_2026, f == null ? null : f.fila(),
                    f == null ? null : f.fecha(), h.sinPedidoConcreto, null, null));
        }
        unidades.forEach((p, u) -> {
            List<PedidoLeido.Unidad> ordenadas = u.stream().sorted(Comparator.comparingInt(PedidoLeido.Unidad::posicion)).toList();
            pedidos.add(new PedidoLeido(canalDe.get(p), p.numero(), p.cliente(), p.pais(), p.fecha(), FuentePedidos.SHIPPYPRO, ordenadas));
        });
        return new Resultado(pedidos, desconocidosShippyPro, desconocidosSimulador);
    }

    private static void repartirAmazon(List<Hueco> huecos, Map<PedidoShippyPro, Canal> canalDe, List<FilaPedidos2026> filas,
                                       Map<String, MovilNsys> moviles) {
        List<FilaPedidos2026> libres = new ArrayList<>(filas.stream()
                .filter(f -> "amazon".equals(Textos.cliente(f.plataforma())))
                .sorted(Comparator.comparingInt((FilaPedidos2026 f) -> avance(moviles.get(NsysDAO.claveCruce(f.imei()))))
                        .thenComparing(f -> recepcion(moviles.get(NsysDAO.claveCruce(f.imei()))), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(FilaPedidos2026::fila))
                .toList());
        for (Hueco h : huecos) {
            if (!canalDe.get(h.pedido).esAmazon()) continue;
            FilaPedidos2026 f = primeraQueCubre(libres, h);
            if (f != null) {
                h.fila = f;
                h.sinPedidoConcreto = true;
                libres.remove(f);
            }
        }
    }

    private static void repartirWebs(List<Hueco> huecos, Map<PedidoShippyPro, Canal> canalDe, List<FilaPedidos2026> filas) {
        List<FilaPedidos2026> libres = new ArrayList<>(filas.stream()
                .filter(f -> !"amazon".equals(Textos.cliente(f.plataforma())))
                .sorted(Comparator.comparingInt(FilaPedidos2026::fila)).toList());
        for (Hueco h : huecos) {
            if (!canalDe.get(h.pedido).esWeb()) continue;
            String cliente = Textos.cliente(h.pedido.cliente());
            if (cliente == null) continue;
            FilaPedidos2026 f = primeraQueCubre(libres.stream().filter(x -> cliente.equals(Textos.cliente(x.plataforma()))).toList(), h);
            if (f != null) {
                h.fila = f;
                libres.remove(f);
            }
        }
    }

    /** La primera fila de la lista que cubre el hueco (ajuste 4: SKU del pedido o aceptado, y del mismo día o después). No la
     *  quita de la lista: lo hace quien la usa. */
    private static FilaPedidos2026 primeraQueCubre(List<FilaPedidos2026> filas, Hueco h) {
        LocalDate dia = h.pedido.fecha().toLocalDate();
        for (FilaPedidos2026 f : filas) {
            boolean mismoSku = Objects.equals(Textos.sku(f.sku()), Textos.sku(h.sku)) || h.aceptacion.acepta(f.sku());
            if (mismoSku && !f.fecha().toLocalDate().isBefore(dia)) return f;
        }
        return null;
    }

    private static List<PedidoLeido> simulador(Entrada e, Set<String> desconocidos) {
        List<FilaSimulador> filas = e.simulador().stream().sorted(Comparator.comparingInt(FilaSimulador::fila)).toList();
        Map<String, FilaSimulador> primera = new HashMap<>();
        Map<String, String> otroOrder = new HashMap<>();
        Set<String> abonados = new HashSet<>();
        for (FilaSimulador f : filas) {
            if (f.imei() == null) continue;
            String clave = NsysDAO.claveCruce(f.imei());
            if ("BB".equals(f.linea())) {
                abonados.add(clave);
                continue;
            }
            FilaSimulador previa = primera.putIfAbsent(clave, f);
            if (previa != null && !previa.orderId().equals(f.orderId())) otroOrder.putIfAbsent(clave, f.orderId());
        }
        Map<String, List<FilaSimulador>> porPedido = new LinkedHashMap<>();
        for (FilaSimulador f : filas) if (!"BB".equals(f.linea())) porPedido.computeIfAbsent(f.orderId(), k -> new ArrayList<>()).add(f);

        List<PedidoLeido> pedidos = new ArrayList<>();
        porPedido.forEach((orderId, lineas) -> {
            List<PedidoLeido.Unidad> unidades = new ArrayList<>();
            int posicion = 0;
            for (FilaSimulador f : lineas) {
                if (e.acepta().para(Canal.SIMULADOR, f.sku()) == null) {
                    if (Textos.pareceTelefono(f.sku())) desconocidos.add(f.sku());
                    continue;
                }
                String clave = f.imei() == null ? null : NsysDAO.claveCruce(f.imei());
                if (clave != null && primera.get(clave).fila() != f.fila()) continue;
                String cierre = clave == null ? null : abonados.contains(clave) ? "ABONO"
                        : vendido(e.moviles().get(clave)) ? "RELEASED" : null;
                unidades.add(new PedidoLeido.Unidad(++posicion, f.sku(), f.imei(), f.imei() == null ? null : FuentePedidos.SIMULADOR,
                        f.imei() == null ? null : f.fila(), f.imei() == null ? null : f.fecha().atStartOfDay(), false,
                        clave == null ? null : otroOrder.get(clave), cierre));
            }
            if (unidades.isEmpty()) return;
            LocalDate fecha = lineas.stream().map(FilaSimulador::fecha).min(Comparator.naturalOrder()).orElseThrow();
            String cliente = lineas.stream().map(FilaSimulador::cliente).filter(c -> c != null && !c.isBlank()).findFirst().orElse(null);
            pedidos.add(new PedidoLeido(Canal.SIMULADOR, orderId, cliente, null, fecha.atStartOfDay(), FuentePedidos.SIMULADOR, unidades));
        });
        return pedidos;
    }

    private static boolean vendido(MovilNsys m) {
        return m != null && m.estado() == EstadoMovil.VENDIDO;
    }

    /** 0 Listos, 1 taller, 2 el resto (sin revisar, devolución…), 3 sin el móvil en el espejo. Un móvil de pedido, por su última
     *  caja. */
    private static int avance(MovilNsys m) {
        if (m == null) return 3;
        String u = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
        if (u == null) return 2;
        if (u.strip().equalsIgnoreCase("Listos")) return 0;
        return u.strip().startsWith("Taller") ? 1 : 2;
    }

    private static LocalDate recepcion(MovilNsys m) {
        return m == null ? null : m.receivingDate();
    }
}
```

- [ ] **Step 4: Ejecutar la prueba**

Run: `mvn -q test -Dtest='CrucePedidosTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos src/test/java/com/reparaciones/servidor/pedidos
git commit -m "feat: cruce de ShippyPro, PEDIDOS 2026 y el Simulador en pedidos con unidades (0.9.26)"
```

---

### Task 6: Guardar los pedidos y la lectura cada 5 minutos

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/{PedidosDAO,PedidoGuardado,UnidadGuardada,LecturaGuardada,LecturaPedidos,TareaLecturaPedidos}.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/{PedidosDAOTest,LecturaPedidosTest}.java`

**Interfaces:**
- Consumes: Tasks 2-5 — ojo: tras la revisión de la Task 3, `ClienteShippyPro.pendientes()` devuelve
  `ClienteShippyPro.Leidos(List<PedidoShippyPro> pedidos, List<String> descartados)` (los pedidos sin fecha válida se saltan y
  van en `descartados`, decisión del usuario del 04/11); `NsysDAO.porImeis(Collection<String>)`, `NsysDAO.productosPorSkuDeCanal`
  (Task 1), `ImportadorNsys.MADRID`.
- Produces:
  - `record PedidoGuardado(int id, Canal canal, String numero, String cliente, String pais, LocalDateTime fecha, FuentePedidos fuente)`.
  - `record UnidadGuardada(int id, int idPedido, int posicion, String sku, String imei, String imeiFuente, LocalDateTime imeiTecleadoEn,
    boolean sinPedidoConcreto, String otroOrderId)`.
  - `record LecturaGuardada(int id, FuentePedidos fuente, LocalDateTime inicio, LocalDateTime fin, String resultado, int filas,
    String skuDesconocidos, String mensaje)`.
  - `PedidosDAO`: `int empezarLectura(FuentePedidos, LocalDateTime)`, `void terminarLectura(int, String, int, String, LocalDateTime)`,
    `void anotarDesconocidos(int, Collection<String>)`, `Optional<LecturaGuardada> ultimaLectura(FuentePedidos, Set<String>)`,
    `void podarLecturas(LocalDateTime)`, `void aplicar(List<PedidoLeido>, LocalDateTime)`, `List<PedidoGuardado> abiertos()`,
    `List<UnidadGuardada> unidades(Collection<Integer> idPedidos)`,
    `List<UnidadDeImei> unidadesAbiertasDeImeis(Collection<String> imeis)` con `record UnidadDeImei(String imei, String numero,
    String canal, String sku)`.
  - `LecturaPedidos.pasada()`; constantes `OK`, `ERROR`, `RECHAZADA`, `INCOMPLETA`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`pedidos/PedidosDAOTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PedidosDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PedidosDAO dao = new PedidosDAO(jdbc);

    private static PedidoLeido.Unidad unidad(int pos, String imei, String cierre) {
        return new PedidoLeido.Unidad(pos, "i13128AzulA", imei, imei == null ? null : FuentePedidos.SIMULADOR, imei == null ? null : 7,
                imei == null ? null : AHORA, false, null, cierre);
    }

    @SuppressWarnings("unchecked")
    @Test void aplicarInsertaLosNuevosActualizaLosQueYaEstanYCierraLosQueNoHanSalido() throws Exception {
        // Ya están: el 6480 del Simulador (id 5) y un pedido de ShippyPro abierto que esta vez no sale (id 9).
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_PEDIDO")).thenReturn(5);
            when(rs.getString("CANAL")).thenReturn("SIMULADOR");
            when(rs.getString("NUMERO")).thenReturn("6480");
            h.processRow(rs);
            return null;
        }).when(jdbc).query(startsWith("SELECT ID_PEDIDO, CANAL, NUMERO FROM Pedido WHERE NUMERO IN"), any(RowCallbackHandler.class), any(Object[].class));
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            for (Object[] fila : new Object[][]{{5, "SIMULADOR"}, {9, "SHIPPYPRO"}}) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getInt("ID_PEDIDO")).thenReturn((Integer) fila[0]);
                when(rs.getString("FUENTE")).thenReturn((String) fila[1]);
                h.processRow(rs);
            }
            return null;
        }).when(jdbc).query(eq(PedidosDAO.SQL_ABIERTOS_IDS), any(RowCallbackHandler.class));
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            ((KeyHolder) inv.getArgument(1)).getKeyList().add(Map.of("insert_id", 12));
            return 1;
        });

        dao.aplicar(List.of(
                new PedidoLeido(Canal.SIMULADOR, "6480", "Móviles Levante SL", null, AHORA.minusDays(2), FuentePedidos.SIMULADOR,
                        List.of(unidad(1, "350000000000001", "RELEASED"), unidad(2, "350000000000002", "ABONO"))),
                new PedidoLeido(Canal.AMAZON, "402-0000000-0000001", "Cliente A", "DE", AHORA.minusDays(1), FuentePedidos.SHIPPYPRO,
                        List.of(unidad(1, null, null)))), AHORA);

        Timestamp ahora = Timestamp.valueOf(AHORA);
        verify(jdbc).update(PedidosDAO.SQL_ACTUALIZAR_PEDIDO, "Móviles Levante SL", null, Timestamp.valueOf(AHORA.minusDays(2)), ahora,
                "TODO_VENDIDO", ahora, "TODO_VENDIDO", 5);
        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        ArgumentCaptor<List<Object[]>> unidades = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(PedidosDAO.SQL_GUARDAR_UNIDAD), unidades.capture());
        assertEquals(3, unidades.getValue().size());
        assertArrayEquals(new Object[]{5, 1, "i13128AzulA", "350000000000001", "SIMULADOR", 7, ahora, false, null, ahora, "RELEASED"},
                unidades.getValue().get(0));
        assertArrayEquals(new Object[]{12, 1, "i13128AzulA", null, null, null, null, false, null, null, null}, unidades.getValue().get(2));
        verify(jdbc).update("DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (?, ?)", 5, 1, 2);
        verify(jdbc).update("DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (?)", 12, 1);
        ArgumentCaptor<List<Object[]>> cierres = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(PedidosDAO.SQL_CERRAR_PEDIDO), cierres.capture());
        assertEquals(1, cierres.getValue().size());
        assertArrayEquals(new Object[]{ahora, "YA_NO_PENDIENTE", 9}, cierres.getValue().get(0));
    }

    @Test void lasUnidadesSeGuardanPorPedidoYPosicionSinCambiarDeId() {
        assertTrue(PedidosDAO.SQL_GUARDAR_UNIDAD.startsWith("INSERT INTO Pedido_unidad"), PedidosDAO.SQL_GUARDAR_UNIDAD);
        assertTrue(PedidosDAO.SQL_GUARDAR_UNIDAD.contains("ON DUPLICATE KEY UPDATE SKU = VALUES(SKU)"), PedidosDAO.SQL_GUARDAR_UNIDAD);
        assertEquals("DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (%s)", PedidosDAO.SQL_RECORTAR_UNIDADES);
    }

    @Test void losDesconocidosSeResumenEnQuinientosCaracteres() {
        dao.anotarDesconocidos(3, List.of("i1", "i2"));
        verify(jdbc).update(PedidosDAO.SQL_DESCONOCIDOS, "i1, i2", 3);
        dao.anotarDesconocidos(4, List.of());
        verify(jdbc).update(PedidosDAO.SQL_DESCONOCIDOS, null, 4);
        assertEquals("i1, i2, … y 1 más", PedidosDAO.resumen(List.of("i1", "i2", "i3"), 2));
    }

    @SuppressWarnings("unchecked")
    @Test void laUltimaLecturaDeUnaFuenteConEsosResultados() {
        dao.ultimaLectura(FuentePedidos.PEDIDOS_2026, java.util.Set.of("OK"));
        verify(jdbc).query(contains("WHERE FUENTE = ? AND RESULTADO IN (?) ORDER BY ID_LECTURA DESC LIMIT 1"),
                any(RowMapper.class), eq("PEDIDOS_2026"), eq("OK"));
    }
}
```

`pedidos/LecturaPedidosTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.NsysDAO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LecturaPedidosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final ClienteShippyPro shippy = mock(ClienteShippyPro.class);
    private final ClienteSheets sheets = mock(ClienteSheets.class);
    private final PedidosDAO dao = mock(PedidosDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final LecturaPedidos lectura = new LecturaPedidos(shippy, sheets, dao, nsys, "100:AMAZON", "hoja-p", "PEDIDOS 2026",
            "hoja-s", "Registro actividad", 4000, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    /** ShippyPro sin pendientes y sin pedidos saltados. */
    private static final ClienteShippyPro.Leidos NINGUNO = new ClienteShippyPro.Leidos(List.of(), List.of());

    private static TablaHoja vacia(int total) {
        return new TablaHoja(total, Map.of(), List.of());
    }

    private void hojasBien(int filasPedidos, int filasSimulador) throws Exception {
        when(sheets.leer(eq("hoja-p"), any(), any(), any(), anyInt())).thenReturn(vacia(filasPedidos));
        when(sheets.leer(eq("hoja-s"), any(), any(), any(), anyInt())).thenReturn(vacia(filasSimulador));
    }

    @Test void conLasTresFuentesBienAplicaYApuntaCadaLectura() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        hojasBien(100, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.OK), eq(0), isNull(), any());
        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.OK), eq(100), isNull(), any());
        verify(dao).terminarLectura(eq(3), eq(LecturaPedidos.OK), eq(200), isNull(), any());
        verify(dao).aplicar(anyList(), eq(AHORA));
        verify(dao).podarLecturas(AHORA.minusDays(30));
    }

    @Test void recienArrancadoYConShippyProCaidoNoTocaNadaYDespuesUsaLoUltimoBueno() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3, 4, 5, 6, 7, 8, 9);
        when(shippy.pendientes()).thenThrow(new IOException("ShippyPro respondió 500")).thenReturn(NINGUNO)
                .thenThrow(new IOException("ShippyPro respondió 500"));
        hojasBien(100, 200);

        lectura.pasada();
        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.ERROR), eq(0), contains("500"), any());
        verify(dao, never()).aplicar(anyList(), any());

        lectura.pasada();
        lectura.pasada();
        verify(dao, times(2)).aplicar(anyList(), eq(AHORA));
    }

    @Test void unaHojaConMenosDelNoventaPorCientoEsIncompletaYNoSeUsa() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(dao.ultimaLectura(eq(FuentePedidos.PEDIDOS_2026), any()))
                .thenReturn(Optional.of(new LecturaGuardada(7, FuentePedidos.PEDIDOS_2026, AHORA, AHORA, "OK", 1000, null, null)));
        hojasBien(899, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.INCOMPLETA), eq(899), contains("1000"), any());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void unaColumnaQueFaltaEsRechazada() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);
        when(sheets.leer(eq("hoja-p"), any(), any(), any(), anyInt()))
                .thenThrow(new FormatoHojaException("Falta la columna «IMEI» en «PEDIDOS 2026»"));
        when(sheets.leer(eq("hoja-s"), any(), any(), any(), anyInt())).thenReturn(vacia(10));

        lectura.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.RECHAZADA), eq(0), eq("Falta la columna «IMEI» en «PEDIDOS 2026»"), any());
        verify(dao, never()).aplicar(anyList(), any());
    }

    @Test void sinElAjusteDeUnaHojaSeApuntaRechazadaSinLlamarAGoogle() throws Exception {
        LecturaPedidos sinHoja = new LecturaPedidos(shippy, sheets, dao, nsys, "", "", "PEDIDOS 2026", "hoja-s", "Registro actividad",
                4000, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(NINGUNO);

        sinHoja.pasada();

        verify(dao).terminarLectura(eq(2), eq(LecturaPedidos.RECHAZADA), eq(0), eq("Falta el ajuste pedidos.pedidos2026.hoja"), any());
        verify(sheets, never()).leer(eq(""), any(), any(), any(), anyInt());
    }

    @Test void losPedidosSinFechaValidaSeApuntanEnUnaLecturaBuena() throws Exception {
        when(dao.empezarLectura(any(), any())).thenReturn(1, 2, 3);
        when(shippy.pendientes()).thenReturn(new ClienteShippyPro.Leidos(List.of(), List.of("402-0000000-0000008", "(sin número)")));
        hojasBien(100, 200);

        lectura.pasada();

        verify(dao).terminarLectura(eq(1), eq(LecturaPedidos.OK), eq(0),
                eq("Pedidos sin fecha válida, saltados: 402-0000000-0000008, (sin número)"), any());
        verify(dao).aplicar(anyList(), eq(AHORA));
    }
}
```

- [ ] **Step 2: Ejecutar las pruebas y ver que fallan**

Run: `mvn -q test -Dtest='PedidosDAOTest,LecturaPedidosTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

`pedidos/PedidoGuardado.java`, `pedidos/UnidadGuardada.java`, `pedidos/LecturaGuardada.java`:

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Un pedido abierto de la tabla Pedido. */
public record PedidoGuardado(int id, Canal canal, String numero, String cliente, String pais, LocalDateTime fecha,
                             FuentePedidos fuente) {}
```

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una unidad abierta de Pedido_unidad. {@code imeiFuente}: PEDIDOS_2026 o SIMULADOR (null sin IMEI). */
public record UnidadGuardada(int id, int idPedido, int posicion, String sku, String imei, String imeiFuente,
                             LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto, String otroOrderId) {}
```

```java
package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una fila de Pedido_lectura. */
public record LecturaGuardada(int id, FuentePedidos fuente, LocalDateTime inicio, LocalDateTime fin, String resultado, int filas,
                              String skuDesconocidos, String mensaje) {}
```

`pedidos/PedidosDAO.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Pedido, Pedido_unidad y Pedido_lectura (spec pieza 2 §5). Horas de Madrid sin zona, como el espejo de NSYS. */
@Repository
public class PedidosDAO {

    static final int TANDA = 500;
    static final int MAX_DESCONOCIDOS = 500;

    static final String SQL_IDS = "SELECT ID_PEDIDO, CANAL, NUMERO FROM Pedido WHERE NUMERO IN (%s)";
    static final String SQL_INSERTAR_PEDIDO = "INSERT INTO Pedido (CANAL, NUMERO, CLIENTE, PAIS, FECHA, FUENTE, VISTO_PRIMERO,"
            + " VISTO_ULTIMO, CERRADO_EN, MOTIVO_CIERRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_ACTUALIZAR_PEDIDO = "UPDATE Pedido SET CLIENTE = ?, PAIS = ?, FECHA = ?, VISTO_ULTIMO = ?,"
            + " CERRADO_EN = CASE WHEN ? IS NULL THEN NULL ELSE COALESCE(CERRADO_EN, ?) END, MOTIVO_CIERRE = ? WHERE ID_PEDIDO = ?";
    static final String SQL_GUARDAR_UNIDAD = "INSERT INTO Pedido_unidad (ID_PEDIDO, POSICION, SKU, IMEI, IMEI_FUENTE, IMEI_REF,"
            + " IMEI_TECLEADO_EN, SIN_PEDIDO_CONCRETO, OTRO_ORDER_ID, CERRADA_EN, MOTIVO_CIERRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE SKU = VALUES(SKU), IMEI = VALUES(IMEI), IMEI_FUENTE = VALUES(IMEI_FUENTE),"
            + " IMEI_REF = VALUES(IMEI_REF), IMEI_TECLEADO_EN = VALUES(IMEI_TECLEADO_EN),"
            + " SIN_PEDIDO_CONCRETO = VALUES(SIN_PEDIDO_CONCRETO), OTRO_ORDER_ID = VALUES(OTRO_ORDER_ID),"
            + " CERRADA_EN = IF(VALUES(MOTIVO_CIERRE) IS NULL, NULL, COALESCE(CERRADA_EN, VALUES(CERRADA_EN))),"
            + " MOTIVO_CIERRE = VALUES(MOTIVO_CIERRE)";
    /** Las posiciones pueden tener huecos (un artículo que el maestro no conoce también cuenta: arreglo de la Task 5): se borran
     *  las que no han salido en esta lectura. */
    static final String SQL_RECORTAR_UNIDADES = "DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (%s)";
    static final String SQL_BORRAR_UNIDADES = "DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ?";
    static final String SQL_ABIERTOS_IDS = "SELECT ID_PEDIDO, FUENTE FROM Pedido WHERE CERRADO_EN IS NULL";
    static final String SQL_CERRAR_PEDIDO = "UPDATE Pedido SET CERRADO_EN = ?, MOTIVO_CIERRE = ? WHERE ID_PEDIDO = ?";
    static final String SQL_ABIERTOS = "SELECT ID_PEDIDO, CANAL, NUMERO, CLIENTE, PAIS, FECHA, FUENTE FROM Pedido"
            + " WHERE CERRADO_EN IS NULL ORDER BY FECHA, NUMERO";
    static final String SQL_UNIDADES = "SELECT ID_UNIDAD, ID_PEDIDO, POSICION, SKU, IMEI, IMEI_FUENTE, IMEI_TECLEADO_EN,"
            + " SIN_PEDIDO_CONCRETO, OTRO_ORDER_ID FROM Pedido_unidad WHERE CERRADA_EN IS NULL AND ID_PEDIDO IN (%s)"
            + " ORDER BY ID_PEDIDO, POSICION";
    static final String SQL_UNIDADES_DE_IMEIS = "SELECT u.IMEI, p.NUMERO, p.CANAL, u.SKU FROM Pedido_unidad u"
            + " JOIN Pedido p ON p.ID_PEDIDO = u.ID_PEDIDO WHERE p.CERRADO_EN IS NULL AND u.CERRADA_EN IS NULL AND u.IMEI IN (%s)"
            + " ORDER BY p.FECHA, p.NUMERO";
    static final String SQL_EMPEZAR_LECTURA = "INSERT INTO Pedido_lectura (FUENTE, INICIO) VALUES (?, ?)";
    static final String SQL_TERMINAR_LECTURA = "UPDATE Pedido_lectura SET FIN = ?, RESULTADO = ?, FILAS = ?, MENSAJE = ?"
            + " WHERE ID_LECTURA = ?";
    static final String SQL_DESCONOCIDOS = "UPDATE Pedido_lectura SET SKU_DESCONOCIDOS = ? WHERE ID_LECTURA = ?";
    static final String SQL_ULTIMA_LECTURA = "SELECT ID_LECTURA, FUENTE, INICIO, FIN, RESULTADO, FILAS, SKU_DESCONOCIDOS, MENSAJE"
            + " FROM Pedido_lectura WHERE FUENTE = ? AND RESULTADO IN (%s) ORDER BY ID_LECTURA DESC LIMIT 1";
    static final String SQL_PODAR = "DELETE FROM Pedido_lectura WHERE INICIO < ?";

    /** Una unidad abierta con IMEI, para la línea de Asignaciones. */
    public record UnidadDeImei(String imei, String numero, String canal, String sku) {}

    private final JdbcTemplate jdbc;

    public PedidosDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- lecturas

    public int empezarLectura(FuentePedidos fuente, LocalDateTime inicio) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_EMPEZAR_LECTURA, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, fuente.name());
            ps.setTimestamp(2, Timestamp.valueOf(inicio));
            return ps;
        }, claves);
        return clave(claves);
    }

    public void terminarLectura(int id, String resultado, int filas, String mensaje, LocalDateTime fin) {
        jdbc.update(SQL_TERMINAR_LECTURA, Timestamp.valueOf(fin), resultado, filas, mensaje, id);
    }

    public void anotarDesconocidos(int id, Collection<String> skus) {
        jdbc.update(SQL_DESCONOCIDOS, skus.isEmpty() ? null : resumen(List.copyOf(skus), 20), id);
    }

    /** «a, b, … y N más», cortado a {@value #MAX_DESCONOCIDOS} caracteres. */
    static String resumen(List<String> skus, int como) {
        String texto = skus.stream().limit(como).collect(Collectors.joining(", "));
        if (skus.size() > como) texto += ", … y " + (skus.size() - como) + " más";
        return texto.length() > MAX_DESCONOCIDOS ? texto.substring(0, MAX_DESCONOCIDOS) : texto;
    }

    public Optional<LecturaGuardada> ultimaLectura(FuentePedidos fuente, Set<String> resultados) {
        List<Object> args = new ArrayList<>();
        args.add(fuente.name());
        args.addAll(resultados);
        return jdbc.query(String.format(SQL_ULTIMA_LECTURA, marcas(resultados.size())), PedidosDAO::lectura, args.toArray())
                .stream().findFirst();
    }

    public void podarLecturas(LocalDateTime antesDe) {
        jdbc.update(SQL_PODAR, Timestamp.valueOf(antesDe));
    }

    // ---------------------------------------------------------------- pedidos

    /** Guarda una vuelta (spec §5 y §6.3): los pedidos nuevos se insertan, los que ya estaban se actualizan (y se reabren si
     *  estaban cerrados), sus unidades se guardan por posición y se quitan las que sobran; los abiertos que no han salido se
     *  cierran. Un pedido del Simulador con todas sus unidades cerradas se cierra. READ COMMITTED como «Revisado» (MariaDB 11.6+). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void aplicar(List<PedidoLeido> pedidos, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        Map<String, Integer> ids = ids(pedidos);
        Set<Integer> vistos = new HashSet<>();
        List<Object[]> unidades = new ArrayList<>();
        Map<Integer, List<Integer>> posiciones = new HashMap<>();
        for (PedidoLeido p : pedidos) {
            String motivo = motivoCierre(p);
            Timestamp cierre = motivo == null ? null : t;
            Integer id = ids.get(clave(p.canal(), p.numero()));
            if (id == null) {
                id = insertar(p, t, cierre, motivo);
            } else {
                jdbc.update(SQL_ACTUALIZAR_PEDIDO, p.cliente(), p.pais(), Timestamp.valueOf(p.fecha()), t, motivo, cierre, motivo, id);
            }
            vistos.add(id);
            for (PedidoLeido.Unidad u : p.unidades()) unidades.add(argumentos(id, u, t));
            posiciones.put(id, p.unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
        }
        for (List<Object[]> lote : trozos(unidades)) jdbc.batchUpdate(SQL_GUARDAR_UNIDAD, lote);
        posiciones.forEach(this::recortar);
        List<Object[]> cierres = new ArrayList<>();
        jdbc.query(SQL_ABIERTOS_IDS, rs -> {
            int id = rs.getInt("ID_PEDIDO");
            if (!vistos.contains(id))
                cierres.add(new Object[]{t, "SHIPPYPRO".equals(rs.getString("FUENTE")) ? "YA_NO_PENDIENTE" : "FUERA_DE_VENTANA", id});
        });
        if (!cierres.isEmpty()) jdbc.batchUpdate(SQL_CERRAR_PEDIDO, cierres);
    }

    /** Quita las unidades del pedido cuyas posiciones no han salido en esta lectura (todas, si no ha salido ninguna). */
    private void recortar(int idPedido, List<Integer> posiciones) {
        if (posiciones.isEmpty()) {
            jdbc.update(SQL_BORRAR_UNIDADES, idPedido);
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(idPedido);
        args.addAll(posiciones);
        jdbc.update(String.format(SQL_RECORTAR_UNIDADES, marcas(posiciones.size())), args.toArray());
    }

    static Object[] argumentos(int idPedido, PedidoLeido.Unidad u, Timestamp ahora) {
        return new Object[]{idPedido, u.posicion(), u.sku(), u.imei(), u.imeiFuente() == null ? null : u.imeiFuente().name(),
                u.imeiRef(), u.imeiTecleadoEn() == null ? null : Timestamp.valueOf(u.imeiTecleadoEn()), u.sinPedidoConcreto(),
                u.otroOrderId(), u.motivoCierre() == null ? null : ahora, u.motivoCierre()};
    }

    /** TODO_VENDIDO o ABONADO si es del Simulador y todas sus unidades están cerradas (ABONADO si todas por abono). */
    static String motivoCierre(PedidoLeido p) {
        if (p.fuente() != FuentePedidos.SIMULADOR || p.unidades().isEmpty()) return null;
        if (p.unidades().stream().anyMatch(u -> u.motivoCierre() == null)) return null;
        return p.unidades().stream().allMatch(u -> "ABONO".equals(u.motivoCierre())) ? "ABONADO" : "TODO_VENDIDO";
    }

    private Integer insertar(PedidoLeido p, Timestamp ahora, Timestamp cierre, String motivo) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERTAR_PEDIDO, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, p.canal().name());
            ps.setString(2, p.numero());
            ps.setString(3, p.cliente());
            ps.setString(4, p.pais());
            ps.setTimestamp(5, Timestamp.valueOf(p.fecha()));
            ps.setString(6, p.fuente().name());
            ps.setTimestamp(7, ahora);
            ps.setTimestamp(8, ahora);
            ps.setTimestamp(9, cierre);
            ps.setString(10, motivo);
            return ps;
        }, claves);
        return clave(claves);
    }

    private Map<String, Integer> ids(List<PedidoLeido> pedidos) {
        Map<String, Integer> ids = new HashMap<>();
        List<String> numeros = pedidos.stream().map(PedidoLeido::numero).distinct().toList();
        for (List<String> tanda : trozos(numeros))
            jdbc.query(String.format(SQL_IDS, marcas(tanda.size())),
                    rs -> { ids.put(rs.getString("CANAL") + "|" + rs.getString("NUMERO"), rs.getInt("ID_PEDIDO")); }, tanda.toArray());
        return ids;
    }

    public List<PedidoGuardado> abiertos() {
        return jdbc.query(SQL_ABIERTOS, (rs, n) -> new PedidoGuardado(rs.getInt("ID_PEDIDO"), Canal.valueOf(rs.getString("CANAL")),
                rs.getString("NUMERO"), rs.getString("CLIENTE"), rs.getString("PAIS"), rs.getTimestamp("FECHA").toLocalDateTime(),
                FuentePedidos.valueOf(rs.getString("FUENTE"))));
    }

    public List<UnidadGuardada> unidades(Collection<Integer> idPedidos) {
        List<UnidadGuardada> unidades = new ArrayList<>();
        for (List<Integer> tanda : trozos(List.copyOf(idPedidos)))
            unidades.addAll(jdbc.query(String.format(SQL_UNIDADES, marcas(tanda.size())), (rs, n) -> new UnidadGuardada(
                    rs.getInt("ID_UNIDAD"), rs.getInt("ID_PEDIDO"), rs.getInt("POSICION"), rs.getString("SKU"), rs.getString("IMEI"),
                    rs.getString("IMEI_FUENTE"), hora(rs, "IMEI_TECLEADO_EN"), rs.getBoolean("SIN_PEDIDO_CONCRETO"),
                    rs.getString("OTRO_ORDER_ID")), tanda.toArray()));
        return unidades;
    }

    public List<UnidadDeImei> unidadesAbiertasDeImeis(Collection<String> imeis) {
        List<UnidadDeImei> unidades = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis)))
            unidades.addAll(jdbc.query(String.format(SQL_UNIDADES_DE_IMEIS, marcas(tanda.size())), (rs, n) -> new UnidadDeImei(
                    rs.getString("IMEI"), rs.getString("NUMERO"), rs.getString("CANAL"), rs.getString("SKU")), tanda.toArray()));
        return unidades;
    }

    // ---------------------------------------------------------------- ayudas

    private static LecturaGuardada lectura(ResultSet rs, int n) throws SQLException {
        return new LecturaGuardada(rs.getInt("ID_LECTURA"), FuentePedidos.valueOf(rs.getString("FUENTE")), hora(rs, "INICIO"),
                hora(rs, "FIN"), rs.getString("RESULTADO"), rs.getInt("FILAS"), rs.getString("SKU_DESCONOCIDOS"), rs.getString("MENSAJE"));
    }

    private static LocalDateTime hora(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toLocalDateTime();
    }

    private static String clave(Canal canal, String numero) {
        return canal.name() + "|" + numero;
    }

    private static int clave(KeyHolder claves) {
        Number n = claves.getKey();
        if (n == null) throw new IllegalStateException("La base de datos no devolvió el id");
        return n.intValue();
    }

    static String marcas(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    static <T> List<List<T>> trozos(List<T> lista) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += TANDA) trozos.add(lista.subList(i, Math.min(lista.size(), i + TANDA)));
        return trozos;
    }
}
```

`pedidos/LecturaPedidos.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Una vuelta de lectura de pedidos (spec pieza 2 §4.4): las tres fuentes por separado, con su registro; si las tres tienen datos
 *  (de esta vuelta o de una anterior desde el arranque: ajuste 6), el cruce y su guardado. */
@Service
public class LecturaPedidos {

    private static final Logger log = LoggerFactory.getLogger(LecturaPedidos.class);
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String INCOMPLETA = "INCOMPLETA";
    static final double MINIMO_FILAS = 0.9;
    static final int DIAS_REGISTRO = 30;
    static final int MAX_MENSAJE = 1000;

    private final ClienteShippyPro shippyPro;
    private final ClienteSheets sheets;
    private final PedidosDAO dao;
    private final NsysDAO nsysDao;
    private final Map<String, Canal> canales;
    private final String hojaPedidos;
    private final String pestanaPedidos;
    private final String hojaSimulador;
    private final String pestanaSimulador;
    private final int filas;
    private final Clock clock;
    private List<PedidoShippyPro> ultimosShippyPro;
    private List<FilaPedidos2026> ultimasPedidos2026;
    private List<FilaSimulador> ultimasSimulador;

    @Autowired
    public LecturaPedidos(ClienteShippyPro shippyPro, ClienteSheets sheets, PedidosDAO dao, NsysDAO nsysDao,
                          @Value("${pedidos.shippypro.canales:}") String canales,
                          @Value("${pedidos.pedidos2026.hoja:}") String hojaPedidos,
                          @Value("${pedidos.pedidos2026.pestana:PEDIDOS 2026}") String pestanaPedidos,
                          @Value("${pedidos.simulador.hoja:}") String hojaSimulador,
                          @Value("${pedidos.simulador.pestana:Registro actividad}") String pestanaSimulador,
                          @Value("${pedidos.filas:4000}") int filas) {
        this(shippyPro, sheets, dao, nsysDao, canales, hojaPedidos, pestanaPedidos, hojaSimulador, pestanaSimulador, filas,
                Clock.system(ImportadorNsys.MADRID));
    }

    LecturaPedidos(ClienteShippyPro shippyPro, ClienteSheets sheets, PedidosDAO dao, NsysDAO nsysDao, String canales,
                   String hojaPedidos, String pestanaPedidos, String hojaSimulador, String pestanaSimulador, int filas, Clock clock) {
        this.shippyPro = shippyPro;
        this.sheets = sheets;
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.canales = Canal.deAjuste(canales);
        this.hojaPedidos = hojaPedidos == null ? "" : hojaPedidos;
        this.pestanaPedidos = pestanaPedidos;
        this.hojaSimulador = hojaSimulador == null ? "" : hojaSimulador;
        this.pestanaSimulador = pestanaSimulador;
        this.filas = filas;
        this.clock = clock;
    }

    private record Leida<T>(Integer id, T datos) {}

    public void pasada() {
        LocalDateTime ahora = ahora();
        Leida<List<PedidoShippyPro>> s = leerShippyPro();
        Leida<List<FilaPedidos2026>> p = leerHoja(FuentePedidos.PEDIDOS_2026, hojaPedidos, "pedidos.pedidos2026.hoja",
                pestanaPedidos, LectorPedidos2026.OBLIGATORIAS, LectorPedidos2026.CLAVE, LectorPedidos2026::filas);
        Leida<List<FilaSimulador>> m = leerHoja(FuentePedidos.SIMULADOR, hojaSimulador, "pedidos.simulador.hoja",
                pestanaSimulador, LectorSimulador.OBLIGATORIAS, LectorSimulador.CLAVE, LectorSimulador::filas);
        if (s.datos() != null) ultimosShippyPro = s.datos();
        if (p.datos() != null) ultimasPedidos2026 = p.datos();
        if (m.datos() != null) ultimasSimulador = m.datos();
        if (ultimosShippyPro == null || ultimasPedidos2026 == null || ultimasSimulador == null) {
            log.warn("Pedidos: falta alguna fuente desde el arranque; no se aplica nada en esta vuelta");
            return;
        }
        try {
            Set<String> imeis = new LinkedHashSet<>();
            ultimasPedidos2026.forEach(f -> imeis.add(f.imei()));
            ultimasSimulador.stream().map(FilaSimulador::imei).filter(i -> i != null).forEach(imeis::add);
            Map<String, MovilNsys> moviles = new HashMap<>();
            if (!imeis.isEmpty())
                for (MovilNsys mv : nsysDao.porImeis(imeis)) moviles.putIfAbsent(NsysDAO.claveCruce(mv.imeiSn()), mv);
            List<String> skus = new ArrayList<>();
            ultimosShippyPro.forEach(x -> x.articulos().forEach(a -> skus.add(a.sku())));
            ultimasPedidos2026.forEach(f -> skus.add(f.sku()));
            ultimasSimulador.forEach(f -> skus.add(f.sku()));
            Set<String> todos = AceptaSku.conVariantes(skus);
            AceptaSku acepta = new AceptaSku(todos.isEmpty() ? List.of() : nsysDao.productosPorSkuDeCanal(todos));
            CrucePedidos.Resultado r = CrucePedidos.cruzar(new CrucePedidos.Entrada(ultimosShippyPro, canales,
                    ultimasPedidos2026, ultimasSimulador, acepta, moviles));
            dao.aplicar(r.pedidos(), ahora);
            if (s.id() != null && s.datos() != null) dao.anotarDesconocidos(s.id(), r.desconocidosShippyPro());
            if (m.id() != null && m.datos() != null) dao.anotarDesconocidos(m.id(), r.desconocidosSimulador());
            dao.podarLecturas(ahora.minusDays(DIAS_REGISTRO));
        } catch (RuntimeException e) {
            log.warn("Pedidos: no se pudo cruzar o guardar la vuelta: {}", e.toString());
        }
    }

    private Leida<List<PedidoShippyPro>> leerShippyPro() {
        int id = dao.empezarLectura(FuentePedidos.SHIPPYPRO, ahora());
        try {
            ClienteShippyPro.Leidos leidos = shippyPro.pendientes();
            // Un pedido sin fecha válida se salta (decisión del usuario, 04/11): la lectura es buena y lo dice.
            String aviso = leidos.descartados().isEmpty() ? null
                    : corta("Pedidos sin fecha válida, saltados: " + String.join(", ", leidos.descartados()));
            dao.terminarLectura(id, OK, leidos.pedidos().size(), aviso, ahora());
            return new Leida<>(id, leidos.pedidos());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarLectura(id, ERROR, 0, "Interrumpida", ahora());
        } catch (IOException | RuntimeException e) {
            log.warn("Pedidos: no se pudo leer ShippyPro: {}", e.toString());
            dao.terminarLectura(id, ERROR, 0, corta(e.toString()), ahora());
        }
        return new Leida<>(id, null);
    }

    private <T> Leida<List<T>> leerHoja(FuentePedidos fuente, String hoja, String ajuste, String pestana, List<String> obligatorias,
                                        String clave, Function<TablaHoja, List<T>> traducir) {
        int id = dao.empezarLectura(fuente, ahora());
        if (hoja.isBlank()) {
            dao.terminarLectura(id, RECHAZADA, 0, "Falta el ajuste " + ajuste, ahora());
            return new Leida<>(id, null);
        }
        try {
            TablaHoja t = sheets.leer(hoja, pestana, obligatorias, clave, filas);
            Optional<LecturaGuardada> previa = dao.ultimaLectura(fuente, Set.of(OK, INCOMPLETA)).filter(l -> l.filas() > 0);
            if (previa.isPresent() && t.total() < previa.get().filas() * MINIMO_FILAS) {
                dao.terminarLectura(id, INCOMPLETA, t.total(), String.format(
                        "Trae %d filas frente a las %d de la lectura anterior: se trata como incompleta", t.total(),
                        previa.get().filas()), ahora());
                return new Leida<>(id, null);
            }
            List<T> datos = traducir.apply(t);
            dao.terminarLectura(id, OK, t.total(), null, ahora());
            return new Leida<>(id, datos);
        } catch (FormatoHojaException e) {
            dao.terminarLectura(id, RECHAZADA, 0, e.getMessage(), ahora());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dao.terminarLectura(id, ERROR, 0, "Interrumpida", ahora());
        } catch (IOException | RuntimeException e) {
            log.warn("Pedidos: no se pudo leer {}: {}", fuente, e.toString());
            dao.terminarLectura(id, ERROR, 0, corta(e.toString()), ahora());
        }
        return new Leida<>(id, null);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String corta(String s) {
        return s.length() > MAX_MENSAJE ? s.substring(0, MAX_MENSAJE) : s;
    }
}
```

`pedidos/TareaLecturaPedidos.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 5 minutos, una vuelta de lectura de pedidos (spec pieza 2 §4.4). Solo existe con pedidos.lectura.activa=true: en los
 *  tests y en los entornos sin las fuentes no hay tarea. */
@Component
@ConditionalOnProperty(name = "pedidos.lectura.activa", havingValue = "true")
public class TareaLecturaPedidos {

    private final LecturaPedidos lectura;

    public TareaLecturaPedidos(LecturaPedidos lectura) {
        this.lectura = lectura;
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 300_000)
    public void ejecutar() {
        lectura.pasada();
    }
}
```

- [ ] **Step 4: Ejecutar las pruebas**

Run: `mvn -q test -Dtest='PedidosDAOTest,LecturaPedidosTest'` y después `mvn -q test` (el contexto entero arranca con las clases
nuevas; `OpenApiContractTest` lo comprueba).
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos src/test/java/com/reparaciones/servidor/pedidos
git commit -m "feat: guardado de pedidos y lectura cada 5 minutos con su registro (0.9.26)"
```

---

### Task 7: El estado de cada unidad y la API de «Pedidos»

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/pedidos/{EstadoUnidad,VistaPedidos,PedidosController,PedidoVista,UnidadVista,MovilUnidad,FuentePedidosVista}.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/{EstadoUnidadTest,VistaPedidosTest}.java`,
  `src/test/java/com/reparaciones/servidor/controller/RolesPedidosTest.java`,
  `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`
- Modify: `gestion-reparaciones-servidor/docs/api_contract.md`

**Interfaces:**
- Consumes: Tasks 2 y 6; `TallerNsys.valedor(Collection<String>)`, `LoQueVale.Valor`, `NsysDAO.porImeis`,
  `NsysDAO.trabajosAbiertos(Collection<String>)`, `TrabajoAbierto`, `AvisoNsys.nivel`.
- Produces:
  - `EstadoUnidad.de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos, Canal canal):
    EstadoUnidad.Resultado` con `record Resultado(String estado, String motivo)` (para una unidad **con** IMEI).
  - DTO (nombres de esquema iguales en la web):
    `record PedidoVista(int idPedido, String canal, String numero, String cliente, String pais, LocalDateTime fecha, String fuente,
    boolean viejo, List<UnidadVista> unidades)`;
    `record UnidadVista(int idUnidad, int posicion, String sku, Atributos pide, String imei, String imeiFuente,
    LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto, String otroOrderId, boolean repetido, String estado, String motivo,
    MovilUnidad movil)` — `pide` y `movil` **nunca** son nulos (springdoc no marca bien un objeto anidado nulo): sin SKU conocido,
    `Atributos.NINGUNO`; sin IMEI o sin el móvil en NSYS, `MovilUnidad.NINGUNO` (`enNsys = false`);
    `record MovilUnidad(boolean enNsys, String modelo, String modeloMaestro, String capacidad, String color, String grado,
    boolean bateriaNueva, boolean esEsim, String sku, String ubicacion, String estadoNsys, List<TrabajoAbierto> trabajos)`;
    `record FuentePedidosVista(String fuente, LocalDateTime ultimaBuena, LocalDateTime ultimaLectura, String resultado, int filas,
    String skuDesconocidos, String motivo, String nivel)`.
  - `GET /api/pedidos?viejos=false` → `List<PedidoVista>`; `GET /api/pedidos/fuentes` → `List<FuentePedidosVista>`.

Reglas de `EstadoUnidad` (spec §6.4.3, en este orden; la unidad sin IMEI es `SIN_MOVIL` y no pasa por aquí):
1. `m == null` → `NO_EN_NSYS`.
2. `ac == null` → `NO_CUADRA` / `SKU_DESCONOCIDO`.
3. Modelo (`modeloMaestro`), capacidad o eSIM distintos de `ac.pide()` → `NO_CUADRA` / `MODELO` (sin distinguir mayúsculas ni
   espacios a los lados).
4. Grado que vale (`v.grade()`, en mayúsculas) `F`, `FD` o `RMA`, o `ID` en Amazon → `NO_CUADRA` / `GRADO_NO_VENDIBLE`.
5. Con trabajos abiertos → `EN_TALLER`.
6. Grado de NSYS (`m.grade()`) C o C100 (con o sin Ms delante) y el taller no lo ha cambiado (`v.gradoNsys() == null`) →
   `NECESITA_TALLER` / `C_SIN_REPARAR`.
7. El SKU que vale (`v.sku()`) lo acepta el pedido (o, sin SKU, `aceptaColorYGrado(v.color(), v.grade())`) → `CUADRA`; si el grado
   empieza por «Ms» → `CON_AVISO` / `MS`. Con el grado sin «Ms» aceptado por color y grado → `CON_AVISO` / `MS` también.
8. Lo acepta como «B por A» → `CON_AVISO` / `B_POR_A`.
9. Si no → `NECESITA_TALLER` / `COLOR_O_GRADO`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`pedidos/EstadoUnidadTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EstadoUnidadTest {

    private static final AceptaSku ACEPTA = new AceptaSku(List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "MsA", "i13128AzulMs", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "B", "i13128AzulB", "i13128AzulB", null, null)));
    private static final Aceptacion AMAZON_A = ACEPTA.para(Canal.AMAZON, "i13128AzulA");
    private static final Aceptacion WEB_A = ACEPTA.para(Canal.DISTRIBUIDORES, "i13128AzulA");
    private static final List<TrabajoAbierto> NINGUNO = List.of();

    private static MovilNsys movil(String modeloMaestro, String grade) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, "128GB", "Azul", grade, false,
                "Listos", "Listos", EstadoMovil.STOCK, null, null);
    }

    private static LoQueVale.Valor vale(String color, String grade, String sku, String gradoNsys) {
        return new LoQueVale.Valor(color, grade, sku, null, gradoNsys, null, gradoNsys != null);
    }

    private static EstadoUnidad.Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> t, Canal c) {
        return EstadoUnidad.de(ac, m, v, t, c);
    }

    @Test void losCasosDeLaSpecEnSuOrden() {
        MovilNsys a = movil("13", "A");
        assertEquals(new EstadoUnidad.Resultado("NO_EN_NSYS", null), de(AMAZON_A, null, null, NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "SKU_DESCONOCIDO"), de(null, a, vale("Azul", "A", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "MODELO"), de(AMAZON_A, movil("13 mini", "A"), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE"), de(AMAZON_A, movil("13", "Fd"), vale("Azul", "Fd", null, null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE"), de(AMAZON_A, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.AMAZON));
        assertEquals("NECESITA_TALLER", de(WEB_A, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.DISTRIBUIDORES).estado(),
                "un ID no está prohibido fuera de Amazon: no cuadra por grado");
        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Técnico 1", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        assertEquals(new EstadoUnidad.Resultado("EN_TALLER", null), de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), List.of(glass), Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "C_SIN_REPARAR"), de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null), de(AMAZON_A, movil("13", "C"), vale("Azul", "A", "i13128AzulA", "C"), NINGUNO, Canal.AMAZON),
                "un C que el taller ya dejó en A cuadra");
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null), de(AMAZON_A, a, vale("Azul", "A", "I13128AZULA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "MS"), de(AMAZON_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "MS"), de(WEB_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.DISTRIBUIDORES),
                "en la web, un Ms del color y grado del pedido también avisa");
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "B_POR_A"), de(AMAZON_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "COLOR_O_GRADO"), de(WEB_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.DISTRIBUIDORES));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "COLOR_O_GRADO"), de(AMAZON_A, a, vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
    }
}
```

`pedidos/VistaPedidosTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VistaPedidosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final PedidosDAO dao = mock(PedidosDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final VistaPedidos vista = new VistaPedidos(dao, nsys, taller, 14, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @Test void cadaUnidadConSuEstadoLosRepetidosYLosDelSimuladorViejosSoloSiSePiden() {
        when(dao.abiertos()).thenReturn(List.of(
                new PedidoGuardado(1, Canal.AMAZON, "402-0000000-0000001", "Cliente A", "DE", AHORA.minusDays(3), FuentePedidos.SHIPPYPRO),
                new PedidoGuardado(2, Canal.SIMULADOR, "6400", "Cliente B", null, AHORA.minusDays(20), FuentePedidos.SIMULADOR)));
        when(dao.unidades(any())).thenReturn(List.of(
                new UnidadGuardada(10, 1, 1, "i13128AzulA", "350000000000001", "PEDIDOS_2026", AHORA, true, null),
                new UnidadGuardada(11, 1, 2, "i13128AzulA", null, null, null, false, null),
                new UnidadGuardada(20, 2, 1, "i13128AzulA", "350000000000001", "SIMULADOR", AHORA, false, "6401")));
        MovilNsys m = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, "Listos",
                "Listos", EstadoMovil.STOCK, null, null);
        when(nsys.porImeis(any())).thenReturn(List.of(m));
        when(nsys.trabajosAbiertos(anyCollection())).thenReturn(Map.of());
        when(taller.valedor(any())).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));

        List<PedidoVista> sinViejos = vista.pedidos(false);
        assertEquals(List.of("402-0000000-0000001"), sinViejos.stream().map(PedidoVista::numero).toList());
        UnidadVista con = sinViejos.get(0).unidades().get(0);
        assertEquals("CUADRA", con.estado());
        assertEquals("Listos", con.movil().ubicacion());
        assertTrue(con.sinPedidoConcreto());
        assertFalse(con.repetido(), "el pedido viejo del Simulador no se enseña, así que no cuenta para repetidos");
        assertEquals("SIN_MOVIL", sinViejos.get(0).unidades().get(1).estado());
        assertEquals(new Atributos("13", "128GB", "Azul", "A", false), sinViejos.get(0).unidades().get(1).pide());

        List<PedidoVista> conViejos = vista.pedidos(true);
        assertEquals(2, conViejos.size());
        assertTrue(conViejos.get(1).viejo());
        assertTrue(conViejos.get(0).unidades().get(0).repetido());
        assertEquals("6401", conViejos.get(1).unidades().get(0).otroOrderId());
    }

    @Test void lasFuentesConSuUltimaLecturaBuenaYSuNivel() {
        when(dao.ultimaLectura(eq(FuentePedidos.SHIPPYPRO), eq(java.util.Set.of(LecturaPedidos.OK)))).thenReturn(Optional.of(
                new LecturaGuardada(1, FuentePedidos.SHIPPYPRO, AHORA.minusMinutes(3), AHORA.minusMinutes(3), "OK", 12, "i99RaroA", null)));
        when(dao.ultimaLectura(eq(FuentePedidos.SHIPPYPRO), argThat(s -> s.size() > 1))).thenReturn(Optional.of(
                new LecturaGuardada(2, FuentePedidos.SHIPPYPRO, AHORA.minusMinutes(1), AHORA.minusMinutes(1), "ERROR", 0, null,
                        "java.io.IOException: con un id de hoja")));
        when(dao.ultimaLectura(eq(FuentePedidos.PEDIDOS_2026), any())).thenReturn(Optional.empty());
        when(dao.ultimaLectura(eq(FuentePedidos.SIMULADOR), any())).thenReturn(Optional.empty());

        List<FuentePedidosVista> f = vista.fuentes();

        assertEquals(List.of("SHIPPYPRO", "PEDIDOS_2026", "SIMULADOR"), f.stream().map(FuentePedidosVista::fuente).toList());
        assertEquals("OK", f.get(0).nivel());
        assertEquals("ERROR", f.get(0).resultado());
        assertEquals(VistaPedidos.MOTIVO_ERROR, f.get(0).motivo(), "un ERROR no saca su texto (puede llevar ids)");
        assertEquals("i99RaroA", f.get(0).skuDesconocidos());
        assertEquals("SIN_DATOS", f.get(1).nivel());
    }
}
```

`controller/RolesPedidosTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.UsuariosOperativosTestConfig;
import com.reparaciones.servidor.pedidos.VistaPedidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Pedidos» (0.9.26, spec pieza 2 §3): SUPERTECNICO y ADMIN; el TECNICO recibe 403 sin llegar a la vista. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuariosOperativosTestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesPedidosTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockBean VistaPedidos vista;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico2", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private static final List<String> RUTAS = List.of("/api/pedidos", "/api/pedidos?viejos=true", "/api/pedidos/fuentes");

    @Test void elTecnicoRecibe403SinLlegarALaVista() throws Exception {
        for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", tecnico())).andExpect(status().isForbidden());
        verifyNoInteractions(vista);
    }

    @Test void supertecnicoYAdminLasLeen() throws Exception {
        when(vista.pedidos(anyBoolean())).thenReturn(List.of());
        when(vista.fuentes()).thenReturn(List.of());
        for (String token : List.of(supertecnico(), admin()))
            for (String ruta : RUTAS) mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk());
    }
}
```

En `OpenApiContractTest.elContratoPublicaLosEsquemasDeLaWeb`: añadir `"/api/pedidos"` y `"/api/pedidos/fuentes"` a la lista de
rutas que comprueba, y `"PedidoVista"`, `"UnidadVista"`, `"MovilUnidad"`, `"Atributos"`, `"FuentePedidosVista"` a la de
esquemas; y, con los ayudantes que ya usa el test:
`assertNullable(esquemas, "UnidadVista", "imei", "imeiFuente", "imeiTecleadoEn", "otroOrderId", "motivo");`,
`assertNoNullable(esquemas, "UnidadVista", "repetido", "sinPedidoConcreto", "estado", "pide", "movil");`,
`assertNullable(esquemas, "MovilUnidad", "modelo", "modeloMaestro", "capacidad", "color", "grado", "sku", "ubicacion", "estadoNsys");`,
`assertNoNullable(esquemas, "MovilUnidad", "enNsys");`,
`assertNullable(esquemas, "Atributos", "modelo", "capacidad", "color", "grado");`,
`assertNullable(esquemas, "PedidoVista", "cliente", "pais");`,
`assertNullable(esquemas, "FuentePedidosVista", "ultimaBuena", "ultimaLectura", "resultado", "skuDesconocidos", "motivo");`.

- [ ] **Step 2: Ejecutar las pruebas y ver que fallan**

Run: `mvn -q test -Dtest='EstadoUnidadTest,VistaPedidosTest,RolesPedidosTest,OpenApiContractTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

`pedidos/EstadoUnidad.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Si el IMEI tecleado de una unidad cuadra con su pedido (spec pieza 2 §6.4.3), con lo que vale del taller (0.9.21). Es puro. */
public final class EstadoUnidad {

    public record Resultado(String estado, String motivo) {}

    private static final Set<String> NO_VENDIBLES = Set.of("F", "FD", "RMA");
    private static final Pattern C_DE_NSYS = Pattern.compile("(?i)(Ms)?C(100)?");

    private EstadoUnidad() {}

    public static Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> trabajos, Canal canal) {
        if (m == null) return new Resultado("NO_EN_NSYS", null);
        if (ac == null) return new Resultado("NO_CUADRA", "SKU_DESCONOCIDO");
        Atributos pide = ac.pide();
        if (!igual(pide.modelo(), m.modeloMaestro()) || !igual(pide.capacidad(), m.capacidad()) || pide.esEsim() != m.esEsim())
            return new Resultado("NO_CUADRA", "MODELO");
        String grado = v.grade() == null ? "" : v.grade().strip().toUpperCase(Locale.ROOT);
        if (NO_VENDIBLES.contains(grado) || (canal.esAmazon() && grado.equals("ID")))
            return new Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE");
        if (trabajos != null && !trabajos.isEmpty()) return new Resultado("EN_TALLER", null);
        if (m.grade() != null && C_DE_NSYS.matcher(m.grade().strip()).matches() && v.gradoNsys() == null)
            return new Resultado("NECESITA_TALLER", "C_SIN_REPARAR");
        boolean ms = grado.startsWith("MS");
        boolean aceptado = v.sku() != null ? ac.acepta(v.sku()) : ac.aceptaColorYGrado(v.color(), v.grade());
        if (aceptado) return ms ? new Resultado("CON_AVISO", "MS") : new Resultado("CUADRA", null);
        if (ms && ac.aceptaColorYGrado(v.color(), v.grade().strip().substring(2))) return new Resultado("CON_AVISO", "MS");
        if (v.sku() != null && ac.aceptaBporA(v.sku())) return new Resultado("CON_AVISO", "B_POR_A");
        return new Resultado("NECESITA_TALLER", "COLOR_O_GRADO");
    }

    private static boolean igual(String a, String b) {
        return (a == null ? "" : a.strip()).equalsIgnoreCase(b == null ? "" : b.strip());
    }
}
```

(La prueba del Ms en la web: `i13128AzulMs` no está entre los aceptados de Distribuidores `i13128AzulA`, pero el grado sin «Ms»,
«A», con color Azul sí: `CON_AVISO` / `MS`.)

`pedidos/MovilUnidad.java`, `pedidos/UnidadVista.java`, `pedidos/PedidoVista.java`, `pedidos/FuentePedidosVista.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.TrabajoAbierto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** El móvil de una unidad, con lo que vale del taller (0.9.21) y sus trabajos abiertos. {@code enNsys}: false sin IMEI o si NSYS
 *  no lo conoce (entonces todo va vacío: {@link #NINGUNO}). {@code ubicacion}: la visible (la última caja si es de pedido);
 *  {@code estadoNsys}: STOCK, DE_PEDIDO, NO_CUENTA, VENDIDO o BAJA. */
public record MovilUnidad(boolean enNsys, @Schema(nullable = true) String modelo, @Schema(nullable = true) String modeloMaestro,
                          @Schema(nullable = true) String capacidad, @Schema(nullable = true) String color,
                          @Schema(nullable = true) String grado, boolean bateriaNueva, boolean esEsim,
                          @Schema(nullable = true) String sku, @Schema(nullable = true) String ubicacion,
                          @Schema(nullable = true) String estadoNsys, List<TrabajoAbierto> trabajos) {

    public static final MovilUnidad NINGUNO = new MovilUnidad(false, null, null, null, null, null, false, false, null, null,
            null, List.of());
}
```

```java
package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una unidad de un pedido para la pantalla (spec pieza 2 §9.2). {@code pide}: {@link Atributos#NINGUNO} si el maestro no conoce
 *  el SKU; {@code movil}: {@link MovilUnidad#NINGUNO} sin IMEI o si NSYS no lo conoce. Ninguno de los dos es nulo. */
public record UnidadVista(int idUnidad, int posicion, String sku, Atributos pide,
                          @Schema(nullable = true) String imei, @Schema(nullable = true) String imeiFuente,
                          @Schema(nullable = true) LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto,
                          @Schema(nullable = true) String otroOrderId, boolean repetido, String estado,
                          @Schema(nullable = true) String motivo, MovilUnidad movil) {}
```

```java
package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido abierto para la pantalla. {@code viejo}: del Simulador con más días que la ventana (sale con el interruptor). */
public record PedidoVista(int idPedido, String canal, String numero, @Schema(nullable = true) String cliente,
                          @Schema(nullable = true) String pais, LocalDateTime fecha, String fuente, boolean viejo,
                          List<UnidadVista> unidades) {}
```

```java
package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una fuente para la píldora (spec pieza 2 §4.4 y §9.2): su última lectura buena, la última de todas, el nivel del aviso de
 *  datos antiguos (OK, RETRASO, SIN_DATOS) y los SKU de teléfono que el maestro no conoce. */
public record FuentePedidosVista(String fuente, @Schema(nullable = true) LocalDateTime ultimaBuena,
                                 @Schema(nullable = true) LocalDateTime ultimaLectura, @Schema(nullable = true) String resultado,
                                 int filas, @Schema(nullable = true) String skuDesconocidos, @Schema(nullable = true) String motivo,
                                 String nivel) {}
```

`pedidos/VistaPedidos.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AvisoNsys;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Lo que enseña «Pedidos» (spec pieza 2 §8): los pedidos abiertos con el estado de cada unidad, calculado al pedirlo con lo
 *  último del espejo de NSYS y del taller, y las fuentes para la píldora. */
@Service
public class VistaPedidos {

    static final String MOTIVO_ERROR = "Error al leer la fuente; ver el registro del servidor";

    private final PedidosDAO dao;
    private final NsysDAO nsysDao;
    private final TallerNsys taller;
    private final int diasSimulador;
    private final Clock clock;

    @Autowired
    public VistaPedidos(PedidosDAO dao, NsysDAO nsysDao, TallerNsys taller, @Value("${pedidos.simulador.dias:14}") int diasSimulador) {
        this(dao, nsysDao, taller, diasSimulador, Clock.system(ImportadorNsys.MADRID));
    }

    VistaPedidos(PedidosDAO dao, NsysDAO nsysDao, TallerNsys taller, int diasSimulador, Clock clock) {
        this.dao = dao;
        this.nsysDao = nsysDao;
        this.taller = taller;
        this.diasSimulador = diasSimulador;
        this.clock = clock;
    }

    public List<PedidoVista> pedidos(boolean viejos) {
        LocalDate limite = LocalDate.now(clock).minusDays(diasSimulador);
        List<PedidoGuardado> pedidos = dao.abiertos().stream()
                .filter(p -> viejos || !esViejo(p, limite)).toList();
        if (pedidos.isEmpty()) return List.of();
        Set<Integer> ids = new LinkedHashSet<>(pedidos.stream().map(PedidoGuardado::id).toList());
        Map<Integer, List<UnidadGuardada>> porPedido = new HashMap<>();
        // Solo las de los pedidos que se enseñan: un IMEI que solo se repite en un pedido viejo escondido no es «repetido».
        for (UnidadGuardada u : dao.unidades(ids))
            if (ids.contains(u.idPedido())) porPedido.computeIfAbsent(u.idPedido(), k -> new ArrayList<>()).add(u);
        Set<String> imeis = new LinkedHashSet<>();
        Map<String, Integer> veces = new HashMap<>();
        porPedido.values().forEach(l -> l.forEach(u -> {
            if (u.imei() == null) return;
            imeis.add(u.imei());
            veces.merge(NsysDAO.claveCruce(u.imei()), 1, Integer::sum);
        }));
        Map<String, MovilNsys> moviles = new HashMap<>();
        Map<String, List<TrabajoAbierto>> trabajos = Map.of();
        Function<MovilNsys, LoQueVale.Valor> vale = m -> null;
        if (!imeis.isEmpty()) {
            for (MovilNsys m : nsysDao.porImeis(imeis)) moviles.putIfAbsent(NsysDAO.claveCruce(m.imeiSn()), m);
            trabajos = nsysDao.trabajosAbiertos(imeis);
            vale = taller.valedor(imeis);
        }
        Set<String> skus = new LinkedHashSet<>();
        porPedido.values().forEach(l -> l.forEach(u -> skus.add(u.sku())));
        AceptaSku acepta = new AceptaSku(nsysDao.productosPorSkuDeCanal(AceptaSku.conVariantes(skus)));

        List<PedidoVista> vistas = new ArrayList<>();
        for (PedidoGuardado p : pedidos) {
            List<UnidadVista> unidades = new ArrayList<>();
            for (UnidadGuardada u : porPedido.getOrDefault(p.id(), List.of()))
                unidades.add(unidad(u, p.canal(), acepta, moviles, trabajos, vale, veces));
            if (!unidades.isEmpty())
                vistas.add(new PedidoVista(p.id(), p.canal().name(), p.numero(), p.cliente(), p.pais(), p.fecha(), p.fuente().name(),
                        esViejo(p, limite), unidades));
        }
        return vistas;
    }

    private static UnidadVista unidad(UnidadGuardada u, Canal canal, AceptaSku acepta, Map<String, MovilNsys> moviles,
                                      Map<String, List<TrabajoAbierto>> trabajos, Function<MovilNsys, LoQueVale.Valor> vale,
                                      Map<String, Integer> veces) {
        Aceptacion ac = acepta.para(canal, u.sku());
        Atributos pide = ac == null ? Atributos.NINGUNO : ac.pide();
        if (u.imei() == null)
            return new UnidadVista(u.id(), u.posicion(), u.sku(), pide, null, null, null, u.sinPedidoConcreto(), u.otroOrderId(),
                    false, "SIN_MOVIL", null, MovilUnidad.NINGUNO);
        String clave = NsysDAO.claveCruce(u.imei());
        MovilNsys m = moviles.get(clave);
        List<TrabajoAbierto> t = trabajos.getOrDefault(clave, List.of());
        LoQueVale.Valor v = m == null ? null : vale.apply(m);
        EstadoUnidad.Resultado r = EstadoUnidad.de(ac, m, v, t, canal);
        MovilUnidad movil = m == null ? MovilUnidad.NINGUNO : new MovilUnidad(true, m.modelo(), m.modeloMaestro(), m.capacidad(),
                v.color(), v.grado(), v.bateriaNueva(), m.esEsim(), v.sku(),
                m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion(), m.estado().name(), t);
        return new UnidadVista(u.id(), u.posicion(), u.sku(), pide, u.imei(), u.imeiFuente(), u.imeiTecleadoEn(),
                u.sinPedidoConcreto(), u.otroOrderId(), veces.getOrDefault(clave, 0) > 1, r.estado(), r.motivo(), movil);
    }

    private static boolean esViejo(PedidoGuardado p, LocalDate limite) {
        return p.fuente() == FuentePedidos.SIMULADOR && p.fecha().toLocalDate().isBefore(limite);
    }

    public List<FuentePedidosVista> fuentes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        List<FuentePedidosVista> fuentes = new ArrayList<>();
        for (FuentePedidos f : FuentePedidos.values()) {
            Optional<LecturaGuardada> buena = dao.ultimaLectura(f, Set.of(LecturaPedidos.OK));
            Optional<LecturaGuardada> ultima = dao.ultimaLectura(f, Set.of(LecturaPedidos.OK, LecturaPedidos.ERROR,
                    LecturaPedidos.RECHAZADA, LecturaPedidos.INCOMPLETA));
            LocalDateTime cuando = buena.map(LecturaGuardada::fin).orElse(null);
            String resultado = ultima.map(LecturaGuardada::resultado).orElse(null);
            String motivo = ultima.map(l -> LecturaPedidos.ERROR.equals(l.resultado()) ? MOTIVO_ERROR : l.mensaje()).orElse(null);
            fuentes.add(new FuentePedidosVista(f.name(), cuando, ultima.map(LecturaGuardada::fin).orElse(null), resultado,
                    buena.map(LecturaGuardada::filas).orElse(0), buena.map(LecturaGuardada::skuDesconocidos).orElse(null), motivo,
                    AvisoNsys.nivel(cuando, ahora)));
        }
        return fuentes;
    }
}
```

`pedidos/PedidosController.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** «Pedidos» (spec pieza 2 §8): solo lectura, SUPERTECNICO y ADMIN (no hay jerarquía de roles: van los dos nombrados). */
@RestController
@RequestMapping("/api/pedidos")
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
public class PedidosController {

    private final VistaPedidos vista;

    public PedidosController(VistaPedidos vista) {
        this.vista = vista;
    }

    /** Los pedidos abiertos, del más antiguo al más nuevo; {@code viejos}, también los del Simulador fuera de la ventana. */
    @GetMapping
    public List<PedidoVista> pedidos(@RequestParam(defaultValue = "false") boolean viejos) {
        return vista.pedidos(viejos);
    }

    @GetMapping("/fuentes")
    public List<FuentePedidosVista> fuentes() {
        return vista.fuentes();
    }
}
```

En `docs/api_contract.md` del servidor, una sección «0.9.26 · Pedidos» con las dos rutas, el rol, los esquemas y los valores de
`estado`, `motivo`, `fuente` y `nivel` (copiados de «Valores fijos» de este plan).

- [ ] **Step 4: Ejecutar las pruebas**

Run: `mvn -q test`
Expected: PASS (con `target/openapi.json` regenerado).

- [ ] **Step 5: Commit**

```bash
git add src docs/api_contract.md
git commit -m "feat: estado de cada unidad y GET /api/pedidos y /api/pedidos/fuentes (0.9.26)"
```

---

### Task 8: El pedido de cada IMEI en las listas del taller

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/model/ReparacionResumen.java`
- Create: `src/main/java/com/reparaciones/servidor/pedidos/UnidadesDeImei.java`
- Modify: `src/main/java/com/reparaciones/servidor/nsys/DescripcionMovil.java`
- Test: `src/test/java/com/reparaciones/servidor/pedidos/UnidadesDeImeiTest.java`, `nsys/DescripcionMovilTest.java`,
  `OpenApiContractTest.java`

**Interfaces:**
- Consumes: `PedidosDAO.unidadesAbiertasDeImeis` (Task 6).
- Produces: `ReparacionResumen` con `pedidoNumero`, `pedidoCanal`, `pedidoSku` (String, nullable) y `pedidoRepetido` (boolean), al
  final del contrato; `UnidadesDeImei.abiertasPorImei(Collection<String>): Map<String, UnidadesDeImei.PedidoDeImei>` (clave
  `NsysDAO.claveCruce`), con `record PedidoDeImei(String numero, String canal, String sku, boolean repetido)`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`pedidos/UnidadesDeImeiTest.java`:

```java
package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnidadesDeImeiTest {

    private final PedidosDAO dao = mock(PedidosDAO.class);

    @Test void elPedidoMasAntiguoDeCadaImeiYSiEstaEnDos() {
        when(dao.unidadesAbiertasDeImeis(any())).thenReturn(List.of(
                new PedidosDAO.UnidadDeImei("350000000000001", "402-0000000-0000001", "AMAZON", "i13128AzulA"),
                new PedidosDAO.UnidadDeImei("350000000000001", "6480", "SIMULADOR", "i13128AzulA"),
                new PedidosDAO.UnidadDeImei("350000000000002", "18832", "DISTRIBUIDORES", "i14256AzulA")));

        Map<String, UnidadesDeImei.PedidoDeImei> m = new UnidadesDeImei(dao, true).abiertasPorImei(List.of("350000000000001", "350000000000002"));

        assertEquals(new UnidadesDeImei.PedidoDeImei("402-0000000-0000001", "AMAZON", "i13128AzulA", true), m.get("350000000000001"));
        assertFalse(m.get("350000000000002").repetido());
    }

    @Test void conLaLecturaApagadaNoPreguntaNada() {
        assertEquals(Map.of(), new UnidadesDeImei(dao, false).abiertasPorImei(List.of("350000000000001")));
        verifyNoInteractions(dao);
    }
}
```

En `nsys/DescripcionMovilTest.java`, una prueba nueva (con los mocks que ya usa la clase para `NsysDAO` y `TallerNsys`, y un mock
de `UnidadesDeImei`):

```java
    @Test void lasFilasDeTrabajosAbiertosLlevanSuPedidoYLasCerradasNo() {
        UnidadesDeImei pedidos = mock(UnidadesDeImei.class);
        when(pedidos.abiertasPorImei(any())).thenReturn(Map.of("350000000000001",
                new UnidadesDeImei.PedidoDeImei("402-0000000-0000001", "AMAZON", "i13128AzulA", true)));
        DescripcionMovil d = new DescripcionMovil(nsys, taller, pedidos);
        ReparacionResumen abierta = new ReparacionResumen();
        abierta.setImei("350000000000001");
        ReparacionResumen cerrada = new ReparacionResumen();
        cerrada.setImei("350000000000001");
        cerrada.setFechaFin(java.time.LocalDateTime.of(2026, 11, 1, 10, 0));

        d.rellenar(List.of(abierta, cerrada));

        assertEquals("402-0000000-0000001", abierta.getPedidoNumero());
        assertEquals("AMAZON", abierta.getPedidoCanal());
        assertEquals("i13128AzulA", abierta.getPedidoSku());
        assertTrue(abierta.isPedidoRepetido());
        assertNull(cerrada.getPedidoNumero());
        verify(pedidos).abiertasPorImei(java.util.Set.of("350000000000001"));
    }

    @Test void siLosPedidosFallanLaListaSaleIgual() {
        UnidadesDeImei pedidos = mock(UnidadesDeImei.class);
        when(pedidos.abiertasPorImei(any())).thenThrow(new RuntimeException("sin base de datos"));
        ReparacionResumen r = new ReparacionResumen();
        r.setImei("350000000000001");
        assertEquals(List.of(r), new DescripcionMovil(nsys, taller, pedidos).rellenar(List.of(r)));
        assertNull(r.getPedidoNumero());
    }
```

(Si `ReparacionResumen` no tiene `setImei`/`setFechaFin`, usar el constructor que ya usan las pruebas de la clase con `imei` y
`fechaFin`; si el nombre de los mocks de la clase es otro, adaptarlo.)

En `OpenApiContractTest`: `assertNullable(esquemas, "ReparacionResumen", "pedidoNumero", "pedidoCanal", "pedidoSku");` y
`assertNoNullable(esquemas, "ReparacionResumen", "pedidoRepetido");`.

- [ ] **Step 2: Ejecutar las pruebas y ver que fallan**

Run: `mvn -q test -Dtest='UnidadesDeImeiTest,DescripcionMovilTest,OpenApiContractTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

`model/ReparacionResumen.java`, detrás de `esEsim`:

```java
    // 0.9.26 (spec pieza 2 §9.3): el pedido abierto del IMEI (el más antiguo si está en varios), el SKU en el que tiene que acabar
    // y si el IMEI está en dos pedidos. Solo en las filas de trabajos abiertos de las listas del taller (nsys.DescripcionMovil);
    // null/false fuera de un pedido, con la lectura de pedidos apagada o si falla. Al final del contrato.
    @Schema(nullable = true) private String        pedidoNumero;
    @Schema(nullable = true) private String        pedidoCanal;
    @Schema(nullable = true) private String        pedidoSku;
    private boolean       pedidoRepetido;
```

con sus getters y setters al final, en el mismo estilo que `capacidad`/`esEsim`.

`pedidos/UnidadesDeImei.java`:

```java
package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.NsysDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** El pedido abierto de cada IMEI, para la línea de las listas del taller (spec pieza 2 §9.3). Con la lectura apagada no pregunta
 *  nada (los tests, y la vuelta atrás). */
@Service
public class UnidadesDeImei {

    public record PedidoDeImei(String numero, String canal, String sku, boolean repetido) {}

    private final PedidosDAO dao;
    private final boolean activa;

    @Autowired
    public UnidadesDeImei(PedidosDAO dao, @Value("${pedidos.lectura.activa:false}") boolean activa) {
        this.dao = dao;
        this.activa = activa;
    }

    /** Por {@link NsysDAO#claveCruce}: el pedido más antiguo de cada IMEI (la consulta ya viene por fecha) y si está en más de uno. */
    public Map<String, PedidoDeImei> abiertasPorImei(Collection<String> imeis) {
        if (!activa || imeis == null || imeis.isEmpty()) return Map.of();
        Map<String, PedidoDeImei> porImei = new HashMap<>();
        for (PedidosDAO.UnidadDeImei u : dao.unidadesAbiertasDeImeis(imeis)) {
            String clave = NsysDAO.claveCruce(u.imei());
            PedidoDeImei previo = porImei.get(clave);
            if (previo == null) porImei.put(clave, new PedidoDeImei(u.numero(), u.canal(), u.sku(), false));
            else porImei.put(clave, new PedidoDeImei(previo.numero(), previo.canal(), previo.sku(), true));
        }
        return porImei;
    }
}
```

`nsys/DescripcionMovil.java`: un campo `private final UnidadesDeImei pedidos;`, dos constructores

```java
    @Autowired
    public DescripcionMovil(NsysDAO nsysDao, TallerNsys taller, UnidadesDeImei pedidos) {
        this.nsysDao = nsysDao;
        this.taller = taller;
        this.pedidos = pedidos;
    }

    /** Sin pedidos: las pruebas de la 0.9.21 y la 0.9.24. */
    DescripcionMovil(NsysDAO nsysDao, TallerNsys taller) {
        this(nsysDao, taller, null);
    }
```

(con `import org.springframework.beans.factory.annotation.Autowired;` y `import com.reparaciones.servidor.pedidos.UnidadesDeImei;`),
y `rellenar` pasa a:

```java
    public List<ReparacionResumen> rellenar(List<ReparacionResumen> filas) {
        if (filas == null || filas.isEmpty()) return filas;
        rellenarDelEspejo(filas);
        rellenarPedidos(filas);
        return filas;
    }

    /** Lo de antes (0.9.21 y 0.9.24): capacidad, color y eSIM; si falla una lectura, ninguna fila cambia. */
    private void rellenarDelEspejo(List<ReparacionResumen> filas) {
        Map<String, DelEspejo> moviles;
        try {
            moviles = leer(filas);
        } catch (RuntimeException e) {
            log.warn("Listas del taller sin capacidad, color ni eSIM ({} filas): no se pudo leer el espejo de NSYS o lo del "
                    + "taller: {}", filas.size(), e.toString());
            return;
        }
        for (ReparacionResumen r : filas) {
            DelEspejo m = moviles.get(NsysDAO.claveCruce(r.getImei()));
            if (m == null) continue;
            r.setCapacidad(m.capacidad());
            r.setColor(m.color());
            r.setEsEsim(m.esEsim());
        }
    }

    /** 0.9.26 (spec pieza 2 §9.3): el pedido de los IMEI de las filas de trabajos abiertos; si falla, las filas salen sin él. */
    private void rellenarPedidos(List<ReparacionResumen> filas) {
        if (pedidos == null) return;
        Set<String> imeis = new LinkedHashSet<>();
        for (ReparacionResumen r : filas)
            if (r.getFechaFin() == null && r.getImei() != null && !r.getImei().isBlank()) imeis.add(r.getImei());
        if (imeis.isEmpty()) return;
        Map<String, UnidadesDeImei.PedidoDeImei> porImei;
        try {
            porImei = pedidos.abiertasPorImei(imeis);
        } catch (RuntimeException e) {
            log.warn("Listas del taller sin pedidos ({} IMEIs): {}", imeis.size(), e.toString());
            return;
        }
        for (ReparacionResumen r : filas) {
            if (r.getFechaFin() != null || r.getImei() == null) continue;
            UnidadesDeImei.PedidoDeImei p = porImei.get(NsysDAO.claveCruce(r.getImei()));
            if (p == null) continue;
            r.setPedidoNumero(p.numero());
            r.setPedidoCanal(p.canal());
            r.setPedidoSku(p.sku());
            r.setPedidoRepetido(p.repetido());
        }
    }
```

- [ ] **Step 4: Ejecutar las pruebas**

Run: `mvn -q test`
Expected: PASS. Las pruebas de Spring que montan `DescripcionMovil` real (`DescripcionMovilRutasTest`…) no tocan la base de datos
por los pedidos: sin `pedidos.lectura.activa`, `UnidadesDeImei` no pregunta nada.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: el pedido de cada IMEI en las filas de trabajos abiertos del taller (0.9.26)"
```

---

### Task 9: Ensayo con datos reales (en este PC, sin red)

**Files:**
- Create: `src/test/java/com/reparaciones/servidor/pedidos/EnsayoPedidosTest.java`

**Interfaces:**
- Consumes: `CrucePedidos`, `ClienteShippyPro.pedido(JsonNode)` (Task 3), `LectorPedidos2026`, `LectorSimulador`, `TablaHoja`
  (Task 4), `AceptaSku` (Task 2), `LectorMaestro`, `LectorItems`, `FilaItems` (nsys).

La prueba solo corre con la variable de entorno `ENSAYO_PEDIDOS` (una carpeta) y escribe un resumen en la ruta de
`ENSAYO_PEDIDOS_SALIDA`. En la carpeta tiene que haber: `shippypro.json` (la respuesta de `GetPendingOrders`), `pedidos2026.xlsx`
(PEDIDOS 2026 descargada como Excel), `simulador.xlsx` (la copia de Drive), `items.xlsx` (el último Items) y `maestro.xlsx`. **Nunca
se suben esos ficheros ni la salida**: van a `C:/Users/dev/Documents/Apuntes/` o a la carpeta temporal de la sesión.

- [ ] **Step 1: Escribir la prueba**

```java
package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.FilaItems;
import com.reparaciones.servidor.nsys.LectorItems;
import com.reparaciones.servidor.nsys.LectorMaestro;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Ensayo del cruce con copias reales (spec pieza 2 §10), sin red ni base de datos: solo con ENSAYO_PEDIDOS. Escribe el
 *  resumen en ENSAYO_PEDIDOS_SALIDA; nada de esto va al repo. */
@EnabledIfEnvironmentVariable(named = "ENSAYO_PEDIDOS", matches = ".+")
class EnsayoPedidosTest {

    @Test void cruzaLasCopiasRealesYEscribeElResumen() throws Exception {
        Path dir = Path.of(System.getenv("ENSAYO_PEDIDOS"));
        List<PedidoShippyPro> shippy = new ArrayList<>();
        List<String> sinFecha = new ArrayList<>();
        for (JsonNode o : new ObjectMapper().readTree(dir.resolve("shippypro.json").toFile()).path("Orders")) {
            try {
                shippy.add(ClienteShippyPro.pedido(o));
            } catch (IllegalArgumentException e) {
                sinFecha.add(o.path("transaction_id").asText("(sin número)"));
            }
        }
        List<FilaPedidos2026> p2026 = LectorPedidos2026.filas(tabla(dir.resolve("pedidos2026.xlsx"), "PEDIDOS 2026", 4000));
        List<FilaSimulador> sim = LectorSimulador.filas(tabla(dir.resolve("simulador.xlsx"), "Registro actividad", 4000));
        Map<String, MovilNsys> moviles = new HashMap<>();
        try (InputStream in = Files.newInputStream(dir.resolve("items.xlsx"))) {
            for (FilaItems f : LectorItems.leer(in)) {
                EstadoMovil e = "Released".equals(f.status()) ? EstadoMovil.VENDIDO : EstadoMovil.STOCK;
                moviles.put(NsysDAO.claveCruce(f.imeiSn()), new MovilNsys(f.imeiSn(), f.batch(), f.goods(), f.grade(), f.status(),
                        f.location(), f.supplier(), f.receivingDate(), f.soldDate(), null, null, null, null, null, null, false,
                        f.location(), f.location(), e, null, null));
            }
        }
        AceptaSku acepta;
        try (InputStream in = Files.newInputStream(dir.resolve("maestro.xlsx"))) {
            acepta = new AceptaSku(LectorMaestro.leer(in).productos());
        }
        String canales = System.getenv().getOrDefault("ENSAYO_PEDIDOS_CANALES", "");
        CrucePedidos.Resultado r = CrucePedidos.cruzar(new CrucePedidos.Entrada(shippy, Canal.deAjuste(canales), p2026, sim, acepta, moviles));

        Map<String, int[]> porCanal = new TreeMap<>();
        for (PedidoLeido p : r.pedidos()) {
            int[] c = porCanal.computeIfAbsent(p.canal().name(), k -> new int[4]);
            c[0]++;
            for (PedidoLeido.Unidad u : p.unidades()) {
                if (u.motivoCierre() != null) continue;
                c[1]++;
                if (u.imei() != null) c[2]++;
                if (u.sinPedidoConcreto()) c[3]++;
            }
        }
        StringBuilder s = new StringBuilder("Ensayo de pedidos\n\nCanal · pedidos · unidades abiertas · con IMEI · sin pedido concreto\n");
        porCanal.forEach((k, c) -> s.append(String.format("%s · %d · %d · %d · %d%n", k, c[0], c[1], c[2], c[3])));
        s.append("\nPedidos de ShippyPro sin fecha válida (saltados): ").append(sinFecha)
                .append("\nSKU desconocidos de ShippyPro: ").append(r.desconocidosShippyPro())
                .append("\nSKU desconocidos del Simulador: ").append(r.desconocidosSimulador())
                .append("\nFilas de PEDIDOS 2026 leídas: ").append(p2026.size())
                .append("\nLíneas del Simulador leídas: ").append(sim.size()).append('\n');
        Files.writeString(Path.of(System.getenv("ENSAYO_PEDIDOS_SALIDA")), s.toString());
    }

    /** Las últimas {@code filas} filas de una pestaña, como las daría Sheets: números (las fechas, como número de serie) y textos. */
    private static TablaHoja tabla(Path xlsx, String pestana, int filas) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(Files.newInputStream(xlsx))) {
            Sheet hoja = libro.getSheet(pestana);
            Map<String, Integer> columnas = new HashMap<>();
            Row cab = hoja.getRow(0);
            for (Cell c : cab) if (c.getCellType() == CellType.STRING) columnas.putIfAbsent(c.getStringCellValue().strip(), c.getColumnIndex());
            int ultima = hoja.getLastRowNum();
            List<TablaHoja.Fila> lista = new ArrayList<>();
            for (int i = Math.max(1, ultima - filas + 1); i <= ultima; i++) {
                Row row = hoja.getRow(i);
                if (row == null) continue;
                List<JsonNode> celdas = new ArrayList<>();
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell c = row.getCell(j);
                    celdas.add(c == null ? null : switch (c.getCellType()) {
                        case NUMERIC -> DoubleNode.valueOf(c.getNumericCellValue());
                        case STRING -> TextNode.valueOf(c.getStringCellValue());
                        case FORMULA -> c.getCachedFormulaResultType() == CellType.NUMERIC ? DoubleNode.valueOf(c.getNumericCellValue())
                                : TextNode.valueOf(c.getStringCellValue());
                        default -> null;
                    });
                }
                lista.add(new TablaHoja.Fila(i + 1, celdas));
            }
            return new TablaHoja(ultima, columnas, lista);
        }
    }
}
```

- [ ] **Step 2: Comprobar que sin la variable no corre**

Run: `mvn -q test -Dtest='EnsayoPedidosTest'`
Expected: sale con 0 (la prueba se salta).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/reparaciones/servidor/pedidos/EnsayoPedidosTest.java
git commit -m "test: ensayo del cruce de pedidos con copias reales, solo con ENSAYO_PEDIDOS (0.9.26)"
```

- [ ] **Step 4 (lo hace el controlador, no el subagente): ensayar con datos reales**

1. Preparar la carpeta en la carpeta temporal de la sesión (no en el repo): la respuesta de `GetPendingOrders` con la clave de
   `devoluciones` y `requests` (como el sondeo del 04/11), la copia del Simulador y el último Items de Drive, el maestro, y
   PEDIDOS 2026: pedir al usuario que la descargue como Excel («Archivo › Descargar › Microsoft Excel») y diga dónde.
2. `ENSAYO_PEDIDOS=<carpeta> ENSAYO_PEDIDOS_SALIDA=C:/Users/dev/Documents/Apuntes/diseno-modulo-telefonos/ensayo-pedidos-AAAA-MM-DD.txt ENSAYO_PEDIDOS_CANALES="<los marketplaces reales>" mvn -q test -Dtest='EnsayoPedidosTest'`.
3. Revisar con el usuario: unidades con IMEI por canal frente a lo que él ve en PEDIDOS 2026, los SKU desconocidos (si salen
   SKU de Particulares o BackiPhone, ¿es la columna «Conjunto» la buena?), y si el cruce de la web por nombre encuentra sus
   filas. Si algo no cuadra, se corrige en una tarea de arreglo antes de seguir.
4. Apuntar lo visto en `Apuntes/mapa-flujo-telefono.md` (§11, «Pieza 2») y en el registro.

---

### Task 10: Web — contrato, rutas, menú y «Compras»

**Files:**
- Modify: `gestion-reparaciones-web/api/openapi.json`, `src/shared/api/schema.d.ts` (generados), `src/shared/api/client.ts`
- Modify: `src/modules/telefonos/rutas.tsx`, `src/modules/telefonos/api.ts`, `src/app/router.tsx`
- Modify: `src/modules/almacen/rutas.tsx`, `src/modules/almacen/pedidos/PedidosPage.tsx`
- Modify: `src/modules/telefonos/test/fabrica.ts`
- Create: `src/modules/telefonos/pedidos/PedidosPage.tsx` (mínima, se completa en la Task 12)
- Test: `src/app/shell/navegacion.test.ts`, `src/app/shell/SubNav.test.tsx`, `src/modules/telefonos/rutas.test.tsx`,
  `src/modules/almacen/pedidos/PedidosPage.test.tsx`

**Interfaces:**
- Produces: tipos `PedidoVista`, `UnidadVista`, `MovilUnidad`, `Atributos`, `FuentePedidosVista` en `@/shared/api/client`;
  `usePedidos(viejos: boolean)` y `useFuentesPedidos()` en `src/modules/telefonos/api.ts`; fábricas `pedido()`, `unidad()`,
  `movilUnidad()`, `atributos()`, `fuentePedidos()` y la constante `SIN_MOVIL` en `src/modules/telefonos/test/fabrica.ts`; ruta
  `/telefonos/pedidos`. En los tipos generados, `UnidadVista.pide` y `.movil` **no** son nulos (comprobarlo en `schema.d.ts`):
  la web mira `pide.modelo` y `movil.enNsys`.

- [ ] **Step 1: Crear la rama y el worktree de la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git fetch origin
git log -1 --format='%h %s' origin/main
git worktree add -b dev_v0926_pedidos /c/Users/dev/Documents/wt/web-0926-pedidos origin/main
cd /c/Users/dev/Documents/wt/web-0926-pedidos
npm ci
```

- [ ] **Step 2: Traer el contrato y generar los tipos**

```bash
cp /c/Users/dev/Documents/wt/servidor-0926-pedidos/target/openapi.json api/openapi.json
npm run api:types:offline
```

En `src/shared/api/client.ts`, junto a los demás:

```ts
export type PedidoVista = components['schemas']['PedidoVista']
export type UnidadVista = components['schemas']['UnidadVista']
export type MovilUnidad = components['schemas']['MovilUnidad']
export type Atributos = components['schemas']['Atributos']
export type FuentePedidosVista = components['schemas']['FuentePedidosVista']
```

- [ ] **Step 3: Escribir las pruebas que fallan**

En `navegacion.test.ts`: el grupo Stock pasa a `['Stock actual', 'Compras', 'Proveedores']` y el de Teléfonos a
`[['Stock de teléfonos', '/telefonos/stock'], ['Pedidos', '/telefonos/pedidos']]`. En `SubNav.test.tsx`, cada `'Pedidos'` que se
refiere al enlace de Stock (`/stock/pedidos`) pasa a `'Compras'` (las líneas 49, 68, 74, 235, 255 y 291-295 del 04/11; buscar
`name: 'Pedidos'`). En `almacen/pedidos/PedidosPage.test.tsx`, el título esperado pasa a `'Compras'`. En `telefonos/rutas.test.tsx`
añadir:

```tsx
it('el supertécnico tiene Pedidos en Teléfonos y el técnico no', () => {
  expect(enlacesTelefonos(SESION_SUPER).map((e) => e.to)).toEqual(['/telefonos/stock', '/telefonos/pedidos'])
  expect(enlacesTelefonos(SESION_TECNICO)).toEqual([])
})
```

(con las sesiones de prueba que ya importa ese fichero; si no las importa, desde `@/test/render`).

- [ ] **Step 4: Ejecutar las pruebas y ver que fallan**

Run: `npx vitest run src/app/shell src/modules/telefonos/rutas.test.tsx src/modules/almacen/pedidos/PedidosPage.test.tsx`
Expected: FAIL.

- [ ] **Step 5: Implementar**

`src/modules/telefonos/rutas.tsx`:

```tsx
export function enlacesTelefonos(sesion: Sesion | null): Enlace[] {
  return esAdminOSuperTecnico(sesion)
    ? [
        { to: '/telefonos/stock', label: 'Stock de teléfonos', icono: Smartphone },
        { to: '/telefonos/pedidos', label: 'Pedidos', icono: ClipboardList },
      ]
    : []
}
```

(con `ClipboardList` en el import de `lucide-react`, y el comentario: «0.9.26: «Pedidos» de clientes, spec pieza 2 §9.1»).

`src/app/router.tsx`, en los hijos de `RequiereAccesoTelefonos`:

```tsx
            children: [
              { path: '/telefonos/stock', element: <StockTelefonosPage /> },
              { path: '/telefonos/pedidos', element: <PedidosPage /> },
            ],
```

con `import { PedidosPage } from '@/modules/telefonos/pedidos/PedidosPage'`.

`src/modules/almacen/rutas.tsx`: `{ to: '/stock/pedidos', label: 'Compras', icono: ShoppingCart }` (la dirección no cambia; el
comentario de arriba dice «0.9.26: «Pedidos» pasa a «Compras», spec pieza 2 §9.1»). `almacen/pedidos/PedidosPage.tsx`: el
`<h1>` dice `Compras`.

`src/modules/telefonos/api.ts`:

```ts
export const CLAVE_PEDIDOS = ['telefonos', 'pedidos'] as const
export const CLAVE_FUENTES_PEDIDOS = ['telefonos', 'pedidos', 'fuentes'] as const

/** GET /api/pedidos (spec pieza 2 §8): los pedidos abiertos con el estado de cada unidad. Sondea como las demás vistas.
 *  `viejos`: también los del Simulador fuera de la ventana. */
export function usePedidos(viejos: boolean): UseQueryResult<PedidoVista[]> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: [...CLAVE_PEDIDOS, { viejos }],
    queryFn: async () => (await api.GET('/api/pedidos', { params: { query: { viejos } } })).data ?? [],
    refetchInterval: intervalo,
  })
}

/** GET /api/pedidos/fuentes: las tres fuentes para la píldora. */
export function useFuentesPedidos(): UseQueryResult<FuentePedidosVista[]> {
  const intervalo = useIntervaloRefresco(true)
  return useQuery({
    queryKey: CLAVE_FUENTES_PEDIDOS,
    queryFn: async () => (await api.GET('/api/pedidos/fuentes')).data ?? [],
    refetchInterval: intervalo,
    meta: { sinCapa: true },
  })
}
```

(con `PedidoVista` y `FuentePedidosVista` en el import de `@/shared/api/client`).

`src/modules/telefonos/pedidos/PedidosPage.tsx` (mínima por ahora):

```tsx
/** «Pedidos» de clientes (0.9.26, spec pieza 2 §9.2). La Task 12 la completa. */
export function PedidosPage() {
  return <h1 className="text-2xl font-bold text-azul-medio">Pedidos</h1>
}
```

`src/modules/telefonos/test/fabrica.ts`, al final (con los tipos en el import de arriba):

```ts
/** Datos inventados de «Pedidos» (repos públicos). */
export function atributos(p: Partial<Atributos> = {}): Atributos {
  return { modelo: '13', capacidad: '128GB', color: 'Azul', grado: 'A', esEsim: false, ...p }
}

export function movilUnidad(p: Partial<MovilUnidad> = {}): MovilUnidad {
  return {
    enNsys: true, modelo: '13', modeloMaestro: '13', capacidad: '128GB', color: 'Azul', grado: 'A', bateriaNueva: false,
    esEsim: false, sku: 'i13128AzulA', ubicacion: 'Listos', estadoNsys: 'STOCK', trabajos: [], ...p,
  }
}

/** Lo que manda el servidor sin IMEI o sin el móvil en NSYS (MovilUnidad.NINGUNO). */
export const SIN_MOVIL: MovilUnidad = {
  enNsys: false, modelo: null, modeloMaestro: null, capacidad: null, color: null, grado: null, bateriaNueva: false, esEsim: false,
  sku: null, ubicacion: null, estadoNsys: null, trabajos: [],
}

export function unidad(p: Partial<UnidadVista> = {}): UnidadVista {
  return {
    idUnidad: 1, posicion: 1, sku: 'i13128AzulA', pide: atributos(), imei: null, imeiFuente: null, imeiTecleadoEn: null,
    sinPedidoConcreto: false, otroOrderId: null, repetido: false, estado: 'SIN_MOVIL', motivo: null, movil: SIN_MOVIL, ...p,
  }
}

export function pedido(p: Partial<PedidoVista> = {}): PedidoVista {
  return {
    idPedido: 1, canal: 'AMAZON', numero: '402-0000000-0000001', cliente: 'Cliente A', pais: 'DE', fecha: '2026-10-30T10:00:00',
    fuente: 'SHIPPYPRO', viejo: false, unidades: [unidad()], ...p,
  }
}

export function fuentePedidos(p: Partial<FuentePedidosVista> = {}): FuentePedidosVista {
  return {
    fuente: 'SHIPPYPRO', ultimaBuena: '2026-11-04T09:42:00', ultimaLectura: '2026-11-04T09:42:00', resultado: 'OK', filas: 12,
    skuDesconocidos: null, motivo: null, nivel: 'OK', ...p,
  }
}
```

- [ ] **Step 6: Ejecutar las pruebas**

Run: `npx vitest run src/app src/modules/telefonos src/modules/almacen && npx tsc -b && npm run lint`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add api src
git commit -m "feat: contrato de pedidos, Pedidos en Telefonos y Compras en Stock (0.9.26)"
```

---

### Task 11: Web — las reglas de la pantalla

**Files:**
- Create: `src/modules/telefonos/pedidos/pedidos.ts`
- Test: `src/modules/telefonos/pedidos/pedidos.test.ts`

**Interfaces:**
- Consumes: tipos de la Task 10.
- Produces:
  - `type GrupoEstado = 'sin' | 'nocuadra' | 'aviso' | 'taller' | 'cuadra'`, `GRUPOS`, `grupoDe(estado)`.
  - `CANALES`, `textoCanal(canal)`, `dondeTeclear(canal)`.
  - `type Filtros = { canales: Set<string>; grupos: Set<GrupoEstado>; texto: string }`, `filtrar(pedidos, f)`,
    `recuentos(pedidos, canales)`.
  - `plegar(unidades, expandido)`: `{ visibles, plegadas, plegable }`.
  - `type Diferencia = { dato: string; pide: string; es: string; distinto: boolean; taller: string }`, `diferencias(pide, movil)`.
  - `textoEstado(u)`, `queHacer(u, canal)`, `resumenMovil(u)`, `hace(fecha, hoy)`, `textoSinPedidoConcreto(u, pedidos)`.

- [ ] **Step 1: Escribir la prueba que falla**

`src/modules/telefonos/pedidos/pedidos.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { atributos, movilUnidad, pedido, trabajo, unidad } from '../test/fabrica'
import {
  diferencias, filtrar, grupoDe, hace, plegar, queHacer, recuentos, resumenMovil, textoEstado, textoSinPedidoConcreto,
} from './pedidos'

const SIN = new Set<string>()
const P = [
  pedido({ idPedido: 1, unidades: [unidad({ idUnidad: 1 }), unidad({ idUnidad: 2, imei: '350000000000001', estado: 'CUADRA', movil: movilUnidad() })] }),
  pedido({
    idPedido: 2, canal: 'SIMULADOR', numero: '6488', cliente: 'Móviles Levante SL', fuente: 'SIMULADOR',
    unidades: [unidad({ idUnidad: 3, sku: 'i14128NegroA', imei: '350000000000002', estado: 'NO_CUADRA', motivo: 'MODELO', otroOrderId: '6489' })],
  }),
]

describe('reglas de «Pedidos» (spec pieza 2 §9.2)', () => {
  it('cada estado va a su grupo de filtro', () => {
    expect(['SIN_MOVIL', 'NO_EN_NSYS', 'NO_CUADRA', 'NECESITA_TALLER', 'EN_TALLER', 'CUADRA', 'CON_AVISO'].map(grupoDe))
      .toEqual(['sin', 'nocuadra', 'nocuadra', 'nocuadra', 'taller', 'cuadra', 'aviso'])
  })

  it('filtra por canal, por estado y por texto (nº, otro Order id, cliente, SKU, IMEI) y quita los pedidos sin unidades', () => {
    expect(filtrar(P, { canales: new Set(['SIMULADOR']), grupos: new Set(), texto: '' }).map((p) => p.numero)).toEqual(['6488'])
    expect(filtrar(P, { canales: SIN, grupos: new Set(['sin']), texto: '' })[0].unidades.map((u) => u.idUnidad)).toEqual([1])
    expect(filtrar(P, { canales: SIN, grupos: new Set(), texto: '6489' }).map((p) => p.numero)).toEqual(['6488'])
    expect(filtrar(P, { canales: SIN, grupos: new Set(), texto: 'levante' }).map((p) => p.numero)).toEqual(['6488'])
    expect(filtrar(P, { canales: SIN, grupos: new Set(), texto: '0000000000001' })[0].unidades.map((u) => u.idUnidad)).toEqual([2])
  })

  it('cuenta unidades por canal (de todo) y por estado (dentro del canal elegido)', () => {
    const r = recuentos(P, new Set(['AMAZON']))
    expect(r.porCanal).toEqual({ AMAZON: 2, SIMULADOR: 1 })
    expect(r.porGrupo).toEqual({ sin: 1, nocuadra: 0, aviso: 0, taller: 0, cuadra: 1 })
  })

  it('pliega las que cuadran si son 3 o más', () => {
    const us = [1, 2, 3].map((i) => unidad({ idUnidad: i, estado: 'CUADRA' })).concat(unidad({ idUnidad: 4 }))
    expect(plegar(us, false)).toEqual({ visibles: [us[3]], plegadas: 3, plegable: true })
    expect(plegar(us, true)).toEqual({ visibles: us, plegadas: 0, plegable: true })
    expect(plegar(us.slice(2), false).plegable).toBe(false)
  })

  it('la tabla de diferencias dice qué no coincide y si lo cambia el taller', () => {
    const d = diferencias(atributos(), movilUnidad({ modeloMaestro: '13 mini', color: 'Rojo' }))
    expect(d.filter((x) => x.distinto).map((x) => [x.dato, x.pide, x.es, x.taller]))
      .toEqual([['Modelo', 'iPhone 13', 'iPhone 13 mini', 'nunca'], ['Color', 'Azul', 'Rojo', 'sí (chasis)']])
  })

  it('el texto del estado y qué hacer', () => {
    expect(textoEstado(unidad())).toBe('Sin móvil')
    expect(queHacer(unidad(), 'AMAZON')).toBe('Tecléalo en PEDIDOS 2026, como hoy. El ERP lo lee en unos minutos.')
    expect(queHacer(unidad(), 'SIMULADOR')).toBe('Tecléalo en el Simulador, como hoy. El ERP lo lee en unos minutos.')
    const enTaller = unidad({ imei: '350000000000001', estado: 'EN_TALLER', movil: movilUnidad({ trabajos: [trabajo({ tecnico: 'Técnico 1' })] }) })
    expect(textoEstado(enTaller)).toBe('En el taller con Técnico 1 · tiene que acabar en i13128AzulA')
    expect(textoEstado(unidad({ estado: 'NO_CUADRA', motivo: 'MODELO' }))).toBe('No cuadra: el modelo, la capacidad o el eSIM no son los del pedido')
    expect(queHacer(unidad({ estado: 'NO_CUADRA', motivo: 'MODELO' }), 'SIMULADOR')).toBe('Hay que cambiar el IMEI en el Simulador: el taller no cambia eso.')
    expect(textoEstado(unidad({ estado: 'CON_AVISO', motivo: 'B_POR_A' }))).toBe('Cuadra, con aviso: B por A (en Amazon un B puede ir por A; míralo antes)')
  })

  it('el resumen del móvil para la lista y los días', () => {
    expect(resumenMovil(unidad())).toBe('sin móvil')
    expect(resumenMovil(unidad({ imei: '350000000000001', movil: movilUnidad() }))).toBe('3500…0001 · Listos')
    expect(hace('2026-10-30T10:00:00', '2026-11-04')).toBe('hace 5 días')
    expect(hace('2026-11-04T08:00:00', '2026-11-04')).toBe('hoy')
    expect(hace('2026-11-03T08:00:00', '2026-11-04')).toBe('ayer')
  })

  it('por qué «sin pedido concreto»: cuántos IMEIs de ese SKU hay tecleados y cuántos pedidos pendientes', () => {
    const amazon = [
      pedido({ idPedido: 1, unidades: [unidad({ idUnidad: 1, imei: '350000000000001', sinPedidoConcreto: true })] }),
      pedido({ idPedido: 2, numero: '402-0000000-0000002', unidades: [unidad({ idUnidad: 2, imei: '350000000000002', sinPedidoConcreto: true })] }),
      pedido({ idPedido: 3, numero: '402-0000000-0000003', unidades: [unidad({ idUnidad: 3 })] }),
    ]
    expect(textoSinPedidoConcreto(amazon[0].unidades[0], amazon)).toBe(
      'En PEDIDOS 2026 hay 2 IMEIs tecleados para i13128AzulA en filas «Amazon», sin número de pedido. El ERP se los da a los 2 '
      + 'pedidos más antiguos de ese SKU (hay 3 pendientes): el móvil más avanzado va al pedido más antiguo, como hoy al preparar el envío.')
  })
})
```

- [ ] **Step 2: Ejecutar la prueba y ver que falla**

Run: `npx vitest run src/modules/telefonos/pedidos/pedidos.test.ts`
Expected: FAIL (no existe `./pedidos`).

- [ ] **Step 3: Implementar**

`src/modules/telefonos/pedidos/pedidos.ts`:

```ts
import type { Atributos, MovilUnidad, PedidoVista, UnidadVista } from '@/shared/api/client'

/** Reglas puras de «Pedidos» (0.9.26, spec pieza 2 §9.2). */

export type GrupoEstado = 'sin' | 'nocuadra' | 'aviso' | 'taller' | 'cuadra'

export const GRUPOS: { clave: GrupoEstado; texto: string }[] = [
  { clave: 'sin', texto: 'Sin móvil' }, { clave: 'nocuadra', texto: 'No cuadra' }, { clave: 'aviso', texto: 'Con aviso' },
  { clave: 'taller', texto: 'En el taller' }, { clave: 'cuadra', texto: 'Cuadra' },
]

export function grupoDe(estado: string): GrupoEstado {
  switch (estado) {
    case 'SIN_MOVIL': return 'sin'
    case 'EN_TALLER': return 'taller'
    case 'CUADRA': return 'cuadra'
    case 'CON_AVISO': return 'aviso'
    default: return 'nocuadra'
  }
}

export const CANALES: { clave: string; texto: string }[] = [
  { clave: 'AMAZON', texto: 'Amazon' }, { clave: 'AMAZON_STOCKROOM', texto: 'Amazon Stockroom' },
  { clave: 'DISTRIBUIDORES', texto: 'Distribuidores' }, { clave: 'PARTICULARES', texto: 'Particulares' },
  { clave: 'BACKIPHONE', texto: 'BackiPhone' }, { clave: 'SIMULADOR', texto: 'Simulador' },
]

export const textoCanal = (canal: string): string => CANALES.find((c) => c.clave === canal)?.texto ?? canal
export const dondeTeclear = (canal: string): string => (canal === 'SIMULADOR' ? 'el Simulador' : 'PEDIDOS 2026')

export type Filtros = { canales: Set<string>; grupos: Set<GrupoEstado>; texto: string }

/** Los pedidos con solo sus unidades que pasan los filtros; un pedido sin unidades no sale. */
export function filtrar(pedidos: PedidoVista[], f: Filtros): PedidoVista[] {
  const q = f.texto.trim().toLowerCase()
  return pedidos
    .filter((p) => f.canales.size === 0 || f.canales.has(p.canal))
    .map((p) => ({
      ...p,
      unidades: p.unidades.filter((u) => {
        if (f.grupos.size > 0 && !f.grupos.has(grupoDe(u.estado))) return false
        if (!q) return true
        return [p.numero, u.otroOrderId, p.cliente, u.sku, u.imei].some((t) => t?.toLowerCase().includes(q))
      }),
    }))
    .filter((p) => p.unidades.length > 0)
}

/** Unidades por canal (de todos los pedidos) y por grupo de estado (dentro de los canales elegidos). */
export function recuentos(pedidos: PedidoVista[], canales: Set<string>) {
  const porCanal: Record<string, number> = {}
  const porGrupo: Record<GrupoEstado, number> = { sin: 0, nocuadra: 0, aviso: 0, taller: 0, cuadra: 0 }
  for (const p of pedidos) {
    porCanal[p.canal] = (porCanal[p.canal] ?? 0) + p.unidades.length
    if (canales.size > 0 && !canales.has(p.canal)) continue
    for (const u of p.unidades) porGrupo[grupoDe(u.estado)]++
  }
  return { porCanal, porGrupo }
}

/** Con 3 o más unidades que cuadran, se pliegan en una línea salvo que el pedido esté desplegado. */
export function plegar(unidades: UnidadVista[], expandido: boolean) {
  const cuadran = unidades.filter((u) => u.estado === 'CUADRA')
  const plegable = cuadran.length >= 3
  if (!plegable || expandido) return { visibles: unidades, plegadas: 0, plegable }
  return { visibles: unidades.filter((u) => u.estado !== 'CUADRA'), plegadas: cuadran.length, plegable }
}

export type Diferencia = { dato: string; pide: string; es: string; distinto: boolean; taller: string }

const igual = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

export function diferencias(pide: Atributos, m: MovilUnidad): Diferencia[] {
  const filas: [string, string, string, string][] = [
    ['Modelo', `iPhone ${pide.modelo ?? '—'}`, `iPhone ${m.modeloMaestro ?? m.modelo ?? '—'}`, 'nunca'],
    ['Capacidad', pide.capacidad ?? '—', m.capacidad ?? '—', 'nunca'],
    ['eSIM', pide.esEsim ? 'sí' : 'no', m.esEsim ? 'sí' : 'no', 'nunca'],
    ['Color', pide.color ?? '—', m.color ?? '—', 'sí (chasis)'],
    ['Grado', pide.grado ?? '—', m.grado ?? '—', 'sí'],
  ]
  return filas.map(([dato, a, b, taller]) => ({ dato, pide: a, es: b, distinto: !igual(a, b), taller }))
}

const TEXTOS: Record<string, string> = {
  NO_EN_NSYS: 'No cuadra: no está en NSYS',
  'NO_CUADRA:MODELO': 'No cuadra: el modelo, la capacidad o el eSIM no son los del pedido',
  'NO_CUADRA:GRADO_NO_VENDIBLE': 'No cuadra: ese grado no se vende así',
  'NO_CUADRA:SKU_DESCONOCIDO': 'No cuadra: el maestro no conoce el SKU del pedido',
  'NECESITA_TALLER:C_SIN_REPARAR': 'No cuadra, necesita taller: un C no se vende sin reparar',
  'NECESITA_TALLER:COLOR_O_GRADO': 'No cuadra, necesita taller: el color o el grado no son los del pedido',
  'CON_AVISO:MS': 'Cuadra, con aviso: es Ms (con marcas); míralo antes',
  'CON_AVISO:B_POR_A': 'Cuadra, con aviso: B por A (en Amazon un B puede ir por A; míralo antes)',
}

export function textoEstado(u: UnidadVista): string {
  if (u.estado === 'SIN_MOVIL') return 'Sin móvil'
  if (u.estado === 'CUADRA') return `Cuadra${u.movil.ubicacion ? ` · ${u.movil.ubicacion}` : ''}`
  if (u.estado === 'EN_TALLER') {
    const quien = [...new Set(u.movil.trabajos.map((t) => t.tecnico))].join(', ')
    return `En el taller${quien ? ` con ${quien}` : ''} · tiene que acabar en ${u.sku}`
  }
  return TEXTOS[`${u.estado}:${u.motivo}`] ?? TEXTOS[u.estado] ?? u.estado
}

export function queHacer(u: UnidadVista, canal: string): string | null {
  if (u.estado === 'SIN_MOVIL') return `Tecléalo en ${dondeTeclear(canal)}, como hoy. El ERP lo lee en unos minutos.`
  if (u.estado === 'NO_EN_NSYS' || (u.estado === 'NO_CUADRA' && u.motivo === 'MODELO'))
    return `Hay que cambiar el IMEI en ${dondeTeclear(canal)}: el taller no cambia eso.`
  if (u.estado === 'NECESITA_TALLER') return 'Hay que asignarle el trabajo en Asignaciones.'
  if (u.estado === 'EN_TALLER') return 'Es lo normal: se vende mientras se repara. No es un error.'
  return null
}

const corto = (imei: string) => `${imei.slice(0, 4)}…${imei.slice(-4)}`

export function resumenMovil(u: UnidadVista): string {
  if (!u.imei) return 'sin móvil'
  if (u.estado === 'EN_TALLER') {
    const quien = u.movil.trabajos[0]?.tecnico
    return `${corto(u.imei)}${quien ? ` · con ${quien}` : ''}`
  }
  return `${corto(u.imei)}${u.movil.ubicacion ? ` · ${u.movil.ubicacion}` : ''}`
}

/** «hoy», «ayer» o «hace N días» entre la fecha del pedido (hora de Madrid, del servidor) y hoy en Madrid («AAAA-MM-DD»). */
export function hace(fecha: string, hoy: string): string {
  const dia = (s: string) => Date.UTC(Number(s.slice(0, 4)), Number(s.slice(5, 7)) - 1, Number(s.slice(8, 10)))
  const n = Math.round((dia(hoy) - dia(fecha)) / 86_400_000)
  return n <= 0 ? 'hoy' : n === 1 ? 'ayer' : `hace ${n} días`
}

/** Por qué una unidad de Amazon lleva «sin pedido concreto» (decisión del 04/11). */
export function textoSinPedidoConcreto(u: UnidadVista, pedidos: PedidoVista[]): string {
  const delSku = pedidos.filter((p) => p.canal === 'AMAZON' || p.canal === 'AMAZON_STOCKROOM')
    .flatMap((p) => p.unidades).filter((x) => x.sku.toLowerCase() === u.sku.toLowerCase())
  const n = delSku.filter((x) => x.sinPedidoConcreto && x.imei).length
  return `En PEDIDOS 2026 hay ${n} IMEI${n === 1 ? '' : 's'} tecleado${n === 1 ? '' : 's'} para ${u.sku} en filas «Amazon», `
    + `sin número de pedido. El ERP se ${n === 1 ? 'lo da al pedido más antiguo' : `los da a los ${n} pedidos más antiguos`} `
    + `de ese SKU (hay ${delSku.length} pendiente${delSku.length === 1 ? '' : 's'}): el móvil más avanzado va al pedido más `
    + 'antiguo, como hoy al preparar el envío.'
}
```

- [ ] **Step 4: Ejecutar la prueba**

Run: `npx vitest run src/modules/telefonos/pedidos/pedidos.test.ts && npx tsc -b`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/modules/telefonos/pedidos
git commit -m "feat: reglas de la pantalla Pedidos (0.9.26)"
```

---

### Task 12: Web — la pantalla «Pedidos»

**Files:**
- Modify: `src/modules/telefonos/pedidos/PedidosPage.tsx`
- Create: `src/modules/telefonos/pedidos/{ListaPedidos,DetalleUnidad,FiltrosPedidos,PildoraFuentes}.tsx`
- Test: `src/modules/telefonos/pedidos/PedidosPage.test.tsx`

**Interfaces:**
- Consumes: `usePedidos`, `useFuentesPedidos` (Task 10), reglas (Task 11), `diaNsys`, `nombreModeloNsys`, `descripcionMovil`,
  `clasesUbicacion` de `@/shared/lib/telefonoNsys`, `hoyMadrid` de `@/shared/lib/fechas`, `Popover` de `@/shared/ui/popover`.
- Produces: la pantalla del prototipo, disposición A, **sin** caja «Sugerido», lista «Para este pedido», «¿Prefieres otro?»,
  «Copiar IMEI» ni «Asignar trabajo» (0.9.27).

Accesibilidad (la usan las pruebas y la E2E): `heading` «Pedidos»; región «Lista de pedidos» con un `article` por pedido (nombre:
su número) y un botón por unidad (nombre: `«{sku}: {texto del grupo}»`); región «Unidad elegida»; grupo de botones «Canal» y
«Estado» con `aria-pressed`; buscador con `aria-label="Buscar pedidos"`; interruptor `role="switch"` «Ver también los del
Simulador de más de 14 días»; botón de la píldora con nombre que empieza por «Fuentes:».

- [ ] **Step 1: Escribir la prueba que falla**

`src/modules/telefonos/pedidos/PedidosPage.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route, useLocation } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { fuentePedidos, movilUnidad, pedido, trabajo, unidad } from '../test/fabrica'
import { PedidosPage } from './PedidosPage'

const PEDIDOS = [
  pedido({ idPedido: 1, numero: '402-0000000-0000001', fecha: '2026-10-28T10:00:00', unidades: [
    unidad({ idUnidad: 1, imei: '350000000000001', imeiFuente: 'PEDIDOS_2026', sinPedidoConcreto: true, estado: 'EN_TALLER',
      movil: movilUnidad({ grado: 'C', ubicacion: 'Taller · Glass', trabajos: [trabajo({ tecnico: 'Técnico 1', categoria: 'GLASS' })] }) }),
  ] }),
  pedido({ idPedido: 2, numero: '402-0000000-0000002', fecha: '2026-10-30T10:00:00', unidades: [unidad({ idUnidad: 2 })] }),
  pedido({ idPedido: 3, canal: 'DISTRIBUIDORES', numero: '18832', cliente: 'Tienda Norte SL', pais: 'ES', fecha: '2026-10-31T10:00:00', unidades: [
    unidad({ idUnidad: 3, sku: 'i14256AzulA', imei: '350000000000003', imeiFuente: 'PEDIDOS_2026', estado: 'NECESITA_TALLER', motivo: 'COLOR_O_GRADO',
      pide: { modelo: '14', capacidad: '256GB', color: 'Azul', grado: 'A', esEsim: false },
      movil: movilUnidad({ modelo: '14', modeloMaestro: '14', capacidad: '256GB', color: 'Rojo', sku: 'i14256RojoA' }) }),
  ] }),
  pedido({ idPedido: 4, canal: 'SIMULADOR', numero: '6488', cliente: 'Móviles Levante SL', pais: null, fuente: 'SIMULADOR',
    fecha: '2026-11-02T00:00:00', unidades: [4, 5, 6].map((i) => unidad({ idUnidad: i, imei: `35000000000000${i}`, imeiFuente: 'SIMULADOR',
      estado: 'CUADRA', movil: movilUnidad() })) }),
]

beforeEach(() => {
  server.use(
    http.get('*/api/pedidos', () => HttpResponse.json(PEDIDOS)),
    http.get('*/api/pedidos/fuentes', () => HttpResponse.json([fuentePedidos(), fuentePedidos({ fuente: 'PEDIDOS_2026' }),
      fuentePedidos({ fuente: 'SIMULADOR', nivel: 'RETRASO' })])),
  )
})

function DetalleFalso() {
  const desde = (useLocation().state as { desde?: string } | null)?.desde
  return <p>Detalle del IMEI{desde ? ` desde ${desde}` : ''}</p>
}

const abrir = () => renderConProviders(<PedidosPage />, {
  sesion: SESION_SUPER, ruta: '/telefonos/pedidos',
  rutas: <Route path="/reparaciones/imeis/:imei" element={<DetalleFalso />} />,
})
const lista = () => screen.getByRole('region', { name: 'Lista de pedidos' })
const detalle = () => screen.getByRole('region', { name: 'Unidad elegida' })

describe('«Pedidos» (spec pieza 2 §9.2)', () => {
  it('los pedidos de más antiguo a más nuevo, con su cabecera, y la primera unidad sin móvil elegida', async () => {
    abrir()
    expect(await screen.findByRole('heading', { name: 'Pedidos' })).toBeInTheDocument()
    expect(await screen.findByText('6 unidades en 4 pedidos · los más antiguos arriba')).toBeInTheDocument()
    expect(within(lista()).getAllByRole('article').map((a) => a.getAttribute('aria-label')))
      .toEqual(['402-0000000-0000001', '402-0000000-0000002', '18832', '6488'])
    expect(within(detalle()).getByText('Pedido 402-0000000-0000002')).toBeInTheDocument()
    expect(within(detalle()).getByText('Tecléalo en PEDIDOS 2026, como hoy. El ERP lo lee en unos minutos.')).toBeInTheDocument()
  })

  it('el que está en el taller dice con quién y en qué tiene que acabar, y por qué «sin pedido concreto»', async () => {
    abrir()
    await userEvent.click(await within(lista()).findByRole('button', { name: 'i13128AzulA: En el taller' }))
    expect(within(detalle()).getByText('En el taller con Técnico 1 · tiene que acabar en i13128AzulA')).toBeInTheDocument()
    expect(within(detalle()).getByText('sin pedido concreto')).toBeInTheDocument()
    expect(within(detalle()).getByText(/^En PEDIDOS 2026 hay 1 IMEI tecleado para i13128AzulA/)).toBeInTheDocument()
  })

  it('el que no cuadra enseña la tabla de diferencias y qué hacer', async () => {
    abrir()
    await userEvent.click(await within(lista()).findByRole('button', { name: 'i14256AzulA: No cuadra' }))
    const tabla = within(detalle()).getByRole('table', { name: 'Diferencias' })
    expect(within(tabla).getByRole('row', { name: /Color Azul Rojo/ })).toHaveAttribute('data-distinto', 'true')
    expect(within(detalle()).getByText('Hay que asignarle el trabajo en Asignaciones.')).toBeInTheDocument()
  })

  it('las del Simulador que cuadran van plegadas y se despliegan', async () => {
    abrir()
    const simulador = await within(lista()).findByRole('article', { name: '6488' })
    await userEvent.click(within(simulador).getByRole('button', { name: '✔ 3 unidades más cuadran · ver' }))
    expect(within(simulador).getAllByRole('button', { name: 'i13128AzulA: Cuadra' })).toHaveLength(3)
  })

  it('filtra por canal, por estado y por texto', async () => {
    abrir()
    await within(lista()).findAllByRole('article')
    await userEvent.click(within(screen.getByRole('group', { name: 'Canal' })).getByRole('button', { name: /^Distribuidores/ }))
    expect(within(lista()).getAllByRole('article').map((a) => a.getAttribute('aria-label'))).toEqual(['18832'])
    await userEvent.click(within(screen.getByRole('group', { name: 'Canal' })).getByRole('button', { name: /^Todos/ }))
    await userEvent.click(within(screen.getByRole('group', { name: 'Estado' })).getByRole('button', { name: /^Sin móvil/ }))
    expect(within(lista()).getAllByRole('article').map((a) => a.getAttribute('aria-label'))).toEqual(['402-0000000-0000002'])
    await userEvent.click(within(screen.getByRole('group', { name: 'Estado' })).getByRole('button', { name: /^Todos/ }))
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar pedidos' }), 'levante')
    expect(within(lista()).getAllByRole('article').map((a) => a.getAttribute('aria-label'))).toEqual(['6488'])
  })

  it('el interruptor pide también los del Simulador viejos', async () => {
    const pedidas: string[] = []
    server.use(http.get('*/api/pedidos', ({ request }) => { pedidas.push(new URL(request.url).search); return HttpResponse.json(PEDIDOS) }))
    abrir()
    await userEvent.click(await screen.findByRole('switch', { name: 'Ver también los del Simulador de más de 14 días' }))
    await waitFor(() => expect(pedidas).toContain('?viejos=true'))
  })

  it('«Ver rastro» abre el detalle del IMEI', async () => {
    abrir()
    await userEvent.click(await within(lista()).findByRole('button', { name: 'i14256AzulA: No cuadra' }))
    await userEvent.click(within(detalle()).getByRole('button', { name: 'Ver rastro' }))
    expect(await screen.findByText('Detalle del IMEI desde /telefonos/pedidos')).toBeInTheDocument()
  })

  it('la píldora de las fuentes dice el peor nivel y abre el detalle', async () => {
    abrir()
    const pildora = await screen.findByRole('button', { name: /^Fuentes:/ })
    expect(pildora).toHaveAccessibleName('Fuentes: con retraso')
    await userEvent.click(pildora)
    expect(await screen.findByText('pedidos de Amazon y webs, sin IMEI')).toBeInTheDocument()
    expect(screen.getByText('pedido e IMEI juntos')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Ejecutar la prueba y ver que falla**

Run: `npx vitest run src/modules/telefonos/pedidos/PedidosPage.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implementar**

`src/modules/telefonos/pedidos/PildoraFuentes.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { diaNsys } from '@/shared/lib/telefonoNsys'
import { Popover, PopoverContent, PopoverTrigger } from '@/shared/ui/popover'
import { useFuentesPedidos } from '../api'

const ORDEN = ['OK', 'RETRASO', 'SIN_DATOS']
const TEXTO: Record<string, string> = { OK: 'al día', RETRASO: 'con retraso', SIN_DATOS: 'sin datos' }
const CLASES: Record<string, string> = {
  OK: 'border-borde-input bg-superficie text-azul-gris',
  RETRASO: 'border-ambar bg-form-agotado-bg text-form-agotado-text',
  SIN_DATOS: 'border-fila-urgente-brd bg-urgente-bg text-fila-urgente-brd',
}
const NOMBRE: Record<string, string> = { SHIPPYPRO: 'ShippyPro', PEDIDOS_2026: 'PEDIDOS 2026', SIMULADOR: 'Simulador' }
const PAPEL: Record<string, string> = {
  SHIPPYPRO: 'pedidos de Amazon y webs, sin IMEI', PEDIDOS_2026: 'los IMEIs tecleados para esos pedidos',
  SIMULADOR: 'pedido e IMEI juntos',
}
const hora = (iso: string | null | undefined) => (iso ? `${diaNsys(iso)} ${iso.slice(11, 16)}` : '—')

/** Píldora de las fuentes de «Pedidos» (spec pieza 2 §9.2): el peor nivel de las tres y, al pulsarla, el papel de cada una, su
 *  última lectura buena, si la última falló y los SKU que el maestro no conoce. */
export function PildoraFuentes() {
  const { data = [] } = useFuentesPedidos()
  const nivel = data.reduce((peor, f) => (ORDEN.indexOf(f.nivel) > ORDEN.indexOf(peor) ? f.nivel : peor), 'OK')
  const texto = `Fuentes: ${TEXTO[nivel] ?? nivel}`
  return (
    <Popover>
      <PopoverTrigger aria-label={texto} className="ml-auto cursor-pointer rounded-full">
        <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11.5px]', CLASES[nivel] ?? CLASES.OK)}>
          ShippyPro · PEDIDOS 2026 · Simulador: {TEXTO[nivel] ?? nivel}
        </span>
      </PopoverTrigger>
      <PopoverContent align="end" aria-label="Fuentes de Pedidos" className="w-[420px] rounded-lg border border-fila-sep bg-superficie p-3 text-[12px] shadow-md">
        <ul className="flex flex-col gap-2">
          {data.map((f) => (
            <li key={f.fuente}>
              <div className="flex gap-2"><b className="w-28 shrink-0">{NOMBRE[f.fuente] ?? f.fuente}</b><span>{PAPEL[f.fuente]}</span></div>
              <div className="pl-30 text-texto-sub">
                Última lectura buena: {hora(f.ultimaBuena)}
                {f.resultado && f.resultado !== 'OK' && <> · la última, {f.resultado.toLowerCase()}{f.motivo ? `: ${f.motivo}` : ''}</>}
                {f.skuDesconocidos && <> · SKU que no están en el maestro: {f.skuDesconocidos}</>}
              </div>
            </li>
          ))}
        </ul>
        <p className="mt-2 text-texto-sub">Lo que NSYS da por vendido (Released) ya no sale. Las ventas del Simulador con reparación también están en PEDIDOS 2026: cada IMEI cuenta una sola vez.</p>
      </PopoverContent>
    </Popover>
  )
}
```

(Si la clase `pl-30` no existe en el tema, usar `pl-[120px]`.)

`src/modules/telefonos/pedidos/FiltrosPedidos.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { CANALES, GRUPOS, type Filtros, type GrupoEstado } from './pedidos'

type Props = {
  filtros: Filtros
  porCanal: Record<string, number>
  porGrupo: Record<GrupoEstado, number>
  total: number
  totalCanal: number
  viejos: boolean
  onFiltros: (f: Filtros) => void
  onViejos: (v: boolean) => void
}

const chip = 'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[12px] font-semibold'
const chipOn = 'border-azul-medio bg-azul-medio text-white'
const chipOff = 'border-borde-input bg-superficie text-azul-gris'

function alternar<T>(s: Set<T>, v: T): Set<T> {
  const n = new Set(s)
  if (n.has(v)) n.delete(v)
  else n.add(v)
  return n
}

/** Buscador, chips de canal y de estado con su recuento, e interruptor de los del Simulador viejos (spec pieza 2 §9.2). */
export function FiltrosPedidos({ filtros, porCanal, porGrupo, total, totalCanal, viejos, onFiltros, onViejos }: Props) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <input type="search" aria-label="Buscar pedidos" placeholder="Nº de pedido, cliente, SKU o IMEI" value={filtros.texto}
          onChange={(e) => onFiltros({ ...filtros, texto: e.target.value })}
          className="h-8 w-72 rounded-lg border border-borde-input bg-superficie px-3 text-[13px]" />
        <div role="group" aria-label="Canal" className="flex flex-wrap items-center gap-1.5">
          <button type="button" aria-pressed={filtros.canales.size === 0} className={cn(chip, filtros.canales.size === 0 ? chipOn : chipOff)}
            onClick={() => onFiltros({ ...filtros, canales: new Set() })}>Todos <span>{total}</span></button>
          {CANALES.filter((c) => porCanal[c.clave]).map((c) => (
            <button key={c.clave} type="button" aria-pressed={filtros.canales.has(c.clave)}
              className={cn(chip, filtros.canales.has(c.clave) ? chipOn : chipOff)}
              onClick={() => onFiltros({ ...filtros, canales: alternar(filtros.canales, c.clave) })}>
              {c.texto} <span>{porCanal[c.clave]}</span>
            </button>
          ))}
        </div>
        <label className="ml-auto flex cursor-pointer items-center gap-2 text-[12px] font-semibold">
          <button type="button" role="switch" aria-checked={viejos} aria-label="Ver también los del Simulador de más de 14 días"
            onClick={() => onViejos(!viejos)}
            className={cn('relative h-[18px] w-[34px] rounded-full', viejos ? 'bg-azul-medio' : 'bg-gris-borde')}>
            <span className={cn('absolute top-[2px] h-[14px] w-[14px] rounded-full bg-white', viejos ? 'left-[18px]' : 'left-[2px]')} />
          </button>
          Simulador de más de 14 días
        </label>
      </div>
      <div role="group" aria-label="Estado" className="flex flex-wrap items-center gap-1.5">
        <button type="button" aria-pressed={filtros.grupos.size === 0} className={cn(chip, filtros.grupos.size === 0 ? chipOn : chipOff)}
          onClick={() => onFiltros({ ...filtros, grupos: new Set() })}>Todos <span>{totalCanal}</span></button>
        {GRUPOS.map((g) => (
          <button key={g.clave} type="button" aria-pressed={filtros.grupos.has(g.clave)}
            className={cn(chip, filtros.grupos.has(g.clave) ? chipOn : chipOff)}
            onClick={() => onFiltros({ ...filtros, grupos: alternar(filtros.grupos, g.clave) })}>
            {g.texto} <span>{porGrupo[g.clave]}</span>
          </button>
        ))}
      </div>
    </div>
  )
}
```

`src/modules/telefonos/pedidos/ListaPedidos.tsx`:

```tsx
import type { PedidoVista } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { diaNsys } from '@/shared/lib/telefonoNsys'
import { GRUPOS, grupoDe, hace, plegar, resumenMovil, textoCanal } from './pedidos'

export const CLASE_GRUPO: Record<string, string> = {
  sin: 'bg-form-agotado-bg text-form-agotado-text', nocuadra: 'bg-urgente-bg text-fila-urgente-brd',
  aviso: 'border border-ambar text-form-agotado-text', taller: 'bg-azul-claro text-azul-medio', cuadra: 'bg-verde-claro text-verde-ok',
}
const textoGrupo = (estado: string) => GRUPOS.find((g) => g.clave === grupoDe(estado))?.texto ?? estado

type Props = {
  pedidos: PedidoVista[]
  hoy: string
  elegida: number | null
  expandidos: Set<number>
  onElegir: (idUnidad: number) => void
  onExpandir: (idPedido: number) => void
}

/** La lista de la izquierda (disposición A): un artículo por pedido con sus unidades (spec pieza 2 §9.2). */
export function ListaPedidos({ pedidos, hoy, elegida, expandidos, onElegir, onExpandir }: Props) {
  return (
    <section aria-label="Lista de pedidos" className="w-[52%] shrink-0 overflow-y-auto rounded-[10px] bg-superficie py-1">
      {pedidos.length === 0 && <p className="p-4 text-texto-sub">Ningún pedido con estos filtros.</p>}
      {pedidos.map((p) => {
        const { visibles, plegadas, plegable } = plegar(p.unidades, expandidos.has(p.idPedido))
        return (
          <article key={p.idPedido} aria-label={p.numero} className="border-b border-fila-sep px-2.5 py-2">
            <div className="mb-1 flex flex-wrap items-center gap-2 text-[12.5px]">
              <span className="rounded-md bg-fila-sep px-2 py-px text-[11.5px] font-bold">{textoCanal(p.canal)}</span>
              <b className="font-mono">{p.numero}</b>
              <span>{p.cliente ?? '—'}{p.pais && p.canal !== 'SIMULADOR' ? ` · ${p.pais}` : ''}</span>
              <span className="ml-auto text-texto-sub">{diaNsys(p.fecha).slice(0, 5)} · {hace(p.fecha, hoy)}{p.unidades.length > 1 ? ` · ${p.unidades.length} uds.` : ''}</span>
            </div>
            {visibles.map((u) => (
              <button key={u.idUnidad} type="button" aria-label={`${u.sku}: ${textoGrupo(u.estado)}`} aria-current={u.idUnidad === elegida || undefined}
                onClick={() => onElegir(u.idUnidad)}
                className={cn('flex w-full items-center gap-2 rounded-md border border-transparent px-2 py-1 text-left hover:bg-fila-hover',
                  u.idUnidad === elegida && 'border-azul-borde bg-azul-claro')}>
                <span className="rounded bg-fila-sep px-1.5 font-mono text-[12px] font-bold">{u.sku}</span>
                <span className={cn('rounded-full px-2 text-[11.5px] font-bold', CLASE_GRUPO[grupoDe(u.estado)])}>{textoGrupo(u.estado)}</span>
                {u.repetido && <span className="text-[11px] font-bold text-rojo-accion">IMEI en dos pedidos</span>}
                <span className="ml-auto truncate text-[12px] text-texto-sub">{resumenMovil(u)}</span>
              </button>
            ))}
            {plegadas > 0 && (
              <button type="button" onClick={() => onExpandir(p.idPedido)} className="w-full px-2 py-1 text-left text-[12px] font-semibold text-verde-ok">
                ✔ {plegadas} unidades más cuadran · ver
              </button>
            )}
            {plegadas === 0 && plegable && (
              <button type="button" onClick={() => onExpandir(p.idPedido)} className="w-full px-2 py-1 text-left text-[12px] font-semibold text-verde-ok">
                Plegar las que cuadran
              </button>
            )}
          </article>
        )
      })}
    </section>
  )
}
```

(Los nombres de los colores del tema — `bg-azul-claro`, `bg-verde-claro`, `text-verde-ok`, `bg-fila-hover`, `border-azul-borde` —
se comprueban en `src/index.css`; si alguno no existe, se usa el más parecido que ya usen `PildoraNsys` y `ListaModelos`.)

`src/modules/telefonos/pedidos/DetalleUnidad.tsx`:

```tsx
import type { PedidoVista, UnidadVista } from '@/shared/api/client'
import { cn } from '@/shared/lib/utils'
import { diaNsys, nombreModeloNsys } from '@/shared/lib/telefonoNsys'
import { CLASE_GRUPO } from './ListaPedidos'
import { diferencias, dondeTeclear, grupoDe, hace, queHacer, textoCanal, textoEstado, textoSinPedidoConcreto } from './pedidos'

type Props = { pedido: PedidoVista | null; unidad: UnidadVista | null; todos: PedidoVista[]; hoy: string; onRastro: (imei: string) => void }

const FUENTE: Record<string, string> = { PEDIDOS_2026: 'PEDIDOS 2026', SIMULADOR: 'el Simulador' }

/** La unidad elegida (derecha de la disposición A): lo que pide, su móvil y si cuadra (spec pieza 2 §9.2). */
export function DetalleUnidad({ pedido, unidad, todos, hoy, onRastro }: Props) {
  return (
    <section aria-label="Unidad elegida" className="min-w-0 flex-1 overflow-y-auto rounded-[10px] bg-superficie px-3.5 py-3">
      {!pedido || !unidad ? <p className="text-texto-sub">Elige una unidad.</p> : (
        <>
          <div className="flex flex-wrap items-center gap-2 text-[12.5px]">
            <span className="rounded-md bg-fila-sep px-2 py-px text-[11.5px] font-bold">{textoCanal(pedido.canal)}</span>
            <b>Pedido {pedido.numero}</b>
            <span>{pedido.cliente ?? '—'}{pedido.pais && pedido.canal !== 'SIMULADOR' ? ` · ${pedido.pais}` : ''}</span>
            <span className="text-texto-sub">{diaNsys(pedido.fecha).slice(0, 5)} · {hace(pedido.fecha, hoy)}</span>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            Pide <span className="rounded bg-fila-sep px-1.5 font-mono font-bold">{unidad.sku}</span>
            {unidad.pide.modelo && <span className="text-texto-sub">iPhone {unidad.pide.modelo} · {unidad.pide.capacidad} · {unidad.pide.color} · {unidad.pide.grado}</span>}
          </div>
          <div className="mt-2 rounded-[10px] border border-fila-sep p-3">
            <h3 className="mb-1.5 text-[11px] font-bold uppercase tracking-wide text-texto-sub">Móvil</h3>
            {unidad.imei ? (
              <>
                <div className="font-mono text-[16px] font-bold">{unidad.imei}</div>
                <div className="text-texto-sub">
                  Tecleado en {FUENTE[unidad.imeiFuente ?? ''] ?? '—'}{unidad.imeiTecleadoEn ? ` el ${diaNsys(unidad.imeiTecleadoEn)}` : ''}
                  {unidad.sinPedidoConcreto && <> · <span className="rounded bg-fila-sep px-1.5 text-[11px] font-bold">sin pedido concreto</span></>}
                  {unidad.otroOrderId && <> · también en el pedido {unidad.otroOrderId}</>}
                </div>
                {unidad.movil.enNsys && (
                  <div className="mt-1.5 font-semibold">
                    {nombreModeloNsys(unidad.movil.modelo, unidad.movil.modeloMaestro)} · {unidad.movil.capacidad} · {unidad.movil.color} · {unidad.movil.grado}
                    {unidad.movil.ubicacion && <span className="font-normal text-texto-sub"> · {unidad.movil.ubicacion}</span>}
                  </div>
                )}
              </>
            ) : <div className="text-texto-sub">Todavía no se ha tecleado ningún IMEI para esta unidad.</div>}
            <div className={cn('mt-2 rounded-md px-2.5 py-1.5 font-semibold', CLASE_GRUPO[grupoDe(unidad.estado)])}>{textoEstado(unidad)}</div>
            {unidad.repetido && <div className="mt-1.5 font-semibold text-rojo-accion">Este IMEI está tecleado en dos pedidos.</div>}
            {unidad.pide.modelo && unidad.movil.enNsys && (unidad.estado === 'NO_CUADRA' || unidad.estado === 'NECESITA_TALLER') && (
              <table aria-label="Diferencias" className="mt-2 w-full text-[12px]">
                <thead><tr className="text-texto-sub"><th /><th className="text-left">Pide</th><th className="text-left">Es</th><th className="text-left">¿Lo cambia el taller?</th></tr></thead>
                <tbody>
                  {diferencias(unidad.pide, unidad.movil).map((d) => (
                    <tr key={d.dato} data-distinto={d.distinto} className={cn(d.distinto && 'font-bold text-fila-urgente-brd')}>
                      <td>{d.dato}</td><td>{d.pide}</td><td>{d.es}{d.distinto ? ' ✘' : ''}</td><td>{d.taller}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {queHacer(unidad, pedido.canal) && <p className="mt-2 text-[12px] font-semibold">{queHacer(unidad, pedido.canal)}</p>}
            {unidad.imei && (
              <button type="button" onClick={() => onRastro(unidad.imei as string)}
                className="mt-2.5 h-8 rounded-lg border border-borde-input bg-superficie px-3 font-semibold hover:bg-fila-hover">Ver rastro</button>
            )}
          </div>
          {unidad.sinPedidoConcreto && (
            <div className="mt-2 rounded-[10px] border border-fila-sep p-3">
              <h3 className="mb-1.5 text-[11px] font-bold uppercase tracking-wide text-texto-sub">Por qué «sin pedido concreto»</h3>
              <p>{textoSinPedidoConcreto(unidad, todos)}</p>
            </div>
          )}
          {!unidad.imei && <p className="mt-2 text-[11.5px] text-texto-sub">La sugerencia de un móvil llega en la 0.9.27. Hasta entonces, búscalo como hoy y tecléalo en {dondeTeclear(pedido.canal)}.</p>}
        </>
      )}
    </section>
  )
}
```

`src/modules/telefonos/pedidos/PedidosPage.tsx`:

```tsx
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { hoyMadrid } from '@/shared/lib/fechas'
import { usePedidos } from '../api'
import { DetalleUnidad } from './DetalleUnidad'
import { FiltrosPedidos } from './FiltrosPedidos'
import { ListaPedidos } from './ListaPedidos'
import { PildoraFuentes } from './PildoraFuentes'
import { filtrar, recuentos, type Filtros } from './pedidos'

/** «Pedidos» de clientes (0.9.26, spec pieza 2 §9.2): solo lee; disposición A del prototipo, sin la sugerencia (0.9.27). Se
 *  refresca cada minuto (usePedidos); la unidad elegida se conserva por su id. */
export function PedidosPage() {
  const [viejos, setViejos] = useState(false)
  const [filtros, setFiltros] = useState<Filtros>({ canales: new Set(), grupos: new Set(), texto: '' })
  const [elegida, setElegida] = useState<number | null>(null)
  const [expandidos, setExpandidos] = useState<Set<number>>(new Set())
  const { data: pedidos = [] } = usePedidos(viejos)
  const navigate = useNavigate()
  const hoy = hoyMadrid()

  const visibles = useMemo(() => filtrar(pedidos, filtros), [pedidos, filtros])
  const { porCanal, porGrupo } = useMemo(() => recuentos(pedidos, filtros.canales), [pedidos, filtros.canales])
  const totalUnidades = pedidos.reduce((n, p) => n + p.unidades.length, 0)
  const totalCanal = pedidos.filter((p) => filtros.canales.size === 0 || filtros.canales.has(p.canal)).reduce((n, p) => n + p.unidades.length, 0)

  const todasVisibles = visibles.flatMap((p) => p.unidades.map((u) => ({ p, u })))
  const actual = todasVisibles.find((x) => x.u.idUnidad === elegida)
    ?? todasVisibles.find((x) => x.u.estado === 'SIN_MOVIL') ?? todasVisibles[0] ?? null

  const expandir = (id: number) => setExpandidos((s) => {
    const n = new Set(s)
    if (n.has(id)) n.delete(id)
    else n.add(id)
    return n
  })

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Pedidos</h1>
        <span className="rounded-full bg-fila-sep px-2.5 py-0.5 text-[12px] font-semibold">
          {totalUnidades} unidades en {pedidos.length} pedidos · los más antiguos arriba
        </span>
        <PildoraFuentes />
      </div>
      <FiltrosPedidos filtros={filtros} porCanal={porCanal} porGrupo={porGrupo} total={totalUnidades} totalCanal={totalCanal}
        viejos={viejos} onFiltros={setFiltros} onViejos={setViejos} />
      <div className="flex min-h-0 flex-1 gap-3">
        <ListaPedidos pedidos={visibles} hoy={hoy} elegida={actual?.u.idUnidad ?? null} expandidos={expandidos}
          onElegir={setElegida} onExpandir={expandir} />
        <DetalleUnidad pedido={actual?.p ?? null} unidad={actual?.u ?? null} todos={pedidos} hoy={hoy}
          onRastro={(imei) => navigate(`/reparaciones/imeis/${encodeURIComponent(imei)}`, { state: { desde: '/telefonos/pedidos' } })} />
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Ejecutar las pruebas**

Run: `npx vitest run src/modules/telefonos && npx tsc -b && npm run lint`
Expected: PASS. Si una prueba no encuentra un nombre accesible, ajustar el componente (no la prueba) a lo que dice
«Accesibilidad» de esta tarea.

- [ ] **Step 5: Mirarla en el navegador contra el prototipo**

`npm run dev` con MSW o contra el servidor del worktree; comparar a 1366×768 y 1920×1080 con
`docs/superpowers/specs/assets/2026-11-04-pieza2-prototipo-pedidos.html` (disposición A): sin scroll de página, la lista y el
detalle con scroll propio. Apuntar en el registro cualquier diferencia que se deje a propósito.

- [ ] **Step 6: Commit**

```bash
git add src/modules/telefonos/pedidos
git commit -m "feat: pantalla Pedidos con lista, detalle, filtros y fuentes (0.9.26)"
```

---

### Task 13: Web — el pedido en la celda del IMEI del taller

**Files:**
- Modify: `src/modules/taller/componentes/CeldaImeiPendiente.tsx`
- Test: `src/modules/taller/componentes/CeldaImeiPendiente.test.tsx` (crear si no existe)

**Interfaces:**
- Consumes: `ReparacionResumen.pedidoNumero`, `pedidoSku`, `pedidoRepetido` (Task 10, opcionales en el tipo).

- [ ] **Step 1: Escribir la prueba que falla**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CeldaImeiPendiente } from './CeldaImeiPendiente'
import { resumen } from '../test/fabrica'

describe('CeldaImeiPendiente · pedido (spec pieza 2 §9.3)', () => {
  it('con pedido dice su número y el SKU en el que tiene que acabar', () => {
    render(<CeldaImeiPendiente rep={resumen({ imei: '350000000000001', pedidoNumero: '402-0000000-0000001', pedidoSku: 'i13128AzulA' })} />)
    expect(screen.getByText('Pedido 402-0000000-0000001 · acabar en i13128AzulA')).toBeInTheDocument()
  })

  it('avisa si el IMEI está en dos pedidos y no dice nada sin pedido', () => {
    const { rerender } = render(<CeldaImeiPendiente rep={resumen({ imei: '350000000000001', pedidoNumero: '6488', pedidoSku: 'i13128AzulA', pedidoRepetido: true })} />)
    expect(screen.getByText('IMEI en dos pedidos')).toBeInTheDocument()
    rerender(<CeldaImeiPendiente rep={resumen({ imei: '350000000000001' })} />)
    expect(screen.queryByText(/^Pedido /)).not.toBeInTheDocument()
  })
})
```

(La fábrica de `ReparacionResumen` del taller está en `src/modules/taller/test/fabrica.ts`; usar su nombre real si no es
`resumen`.)

- [ ] **Step 2: Ejecutar la prueba y ver que falla**

Run: `npx vitest run src/modules/taller/componentes/CeldaImeiPendiente.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implementar**

En `CeldaImeiPendiente.tsx`, detrás de la línea de «N asignados»:

```tsx
      {/* 0.9.26 (spec pieza 2 §9.3): el pedido abierto del IMEI y el SKU en el que tiene que acabar. */}
      {rep.pedidoNumero && (
        <span className={cn('text-[10px] text-texto-sub', CREMA_EN_FILA_SELECCIONADA)}>
          Pedido {rep.pedidoNumero}{rep.pedidoSku ? ` · acabar en ${rep.pedidoSku}` : ''}
        </span>
      )}
      {rep.pedidoRepetido && <span className="text-[10px] font-semibold text-rojo-accion">IMEI en dos pedidos</span>}
```

- [ ] **Step 4: Ejecutar las pruebas**

Run: `npx vitest run src/modules/taller && npx tsc -b`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/componentes
git commit -m "feat: el pedido del IMEI en Asignaciones y Pendientes (0.9.26)"
```

---

### Task 14: E2E, versión y documentación

**Files:**
- Create: `gestion-reparaciones-web/tests/e2e/pedidos-clientes.spec.ts`
- Modify: `tests/e2e/{bloques,columna-lateral,otros-en-bloques,pedidos,roles-stock}.spec.ts` («Pedidos» de Stock → «Compras»)
- Modify: `package.json`, `package-lock.json`, `CHANGELOG.md`
- Create (raíz): `docs/novedades/NOVEDADES-v0.9.26.md`
- Modify (apuntes): `C:/Users/dev/Documents/Apuntes/plan-futuro.md`

- [ ] **Step 1: Cambiar «Pedidos» por «Compras» en las E2E de Stock**

En las cinco E2E, el enlace y el título de la pantalla de piezas (`/stock/pedidos`) pasan de `'Pedidos'` a `'Compras'` (las líneas
del 04/11: `bloques.spec.ts:220`, `columna-lateral.spec.ts:28,37,39`, `otros-en-bloques.spec.ts:173`, `pedidos.spec.ts:87,89`,
`roles-stock.spec.ts:32,47,49`). **Ojo:** el supertécnico tiene ahora otro enlace «Pedidos» en Teléfonos, así que cualquier
`getByRole('link', { name: 'Pedidos', exact: true })` que quede apuntaría a otra pantalla.

- [ ] **Step 2: Escribir la E2E de solo lectura**

`tests/e2e/pedidos-clientes.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// pedidos-clientes.spec.ts (0.9.26, spec pieza 2): el técnico no tiene «Pedidos» de Teléfonos (ni en la columna, ni por URL,
// ni por la API); el supertécnico la ve con su píldora de fuentes, filtra y abre una unidad. Solo LEE: los datos son los que la
// lectura trae de ShippyPro, PEDIDOS 2026 y el Simulador; no se apunta ningún pedido ni IMEI en el código. Como todas las E2E,
// solo contra PREPRODUCCIÓN: se niega a seguir sin el letrero.
const LETRERO_PRE = 'PREPRODUCCIÓN · datos de prueba'

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  expect(await page.getByText(LETRERO_PRE).isVisible(), `solo corre contra PREPRODUCCIÓN (letrero «${LETRERO_PRE}»)`).toBe(true)
}

async function estado(page: Page, ruta: string): Promise<number> {
  return page.evaluate(async (r) => {
    const sesion = JSON.parse(localStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    return (await fetch(r, { headers: { Authorization: `Bearer ${sesion.token}` } })).status
  }, ruta)
}

test('el técnico no tiene Pedidos de Teléfonos: ni en la columna, ni por URL, ni por la API', async ({ page }) => {
  const { usuario, clave } = credenciales('TEC_USER', 'TEC_PASS')
  await entrar(page, usuario, clave)
  const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
  await expect(columna.getByRole('link', { name: 'Pedidos', exact: true })).toHaveCount(0)
  await page.goto('/telefonos/pedidos')
  await expect(page.getByRole('dialog', { name: 'Error' })).toContainText('No tienes permisos para realizar esta acción.')
  expect(await estado(page, '/api/pedidos')).toBe(403)
})

test('el supertécnico ve Pedidos con sus fuentes, filtra y abre una unidad', async ({ page }) => {
  const { usuario, clave } = credenciales('SUPER_USER', 'SUPER_PASS')
  await entrar(page, usuario, clave)
  const columna = page.getByRole('navigation', { name: 'Sub-navegación' })
  await expect(columna.getByRole('link', { name: 'Compras', exact: true })).toBeVisible()
  await columna.getByRole('link', { name: 'Pedidos', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Pedidos', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /^Fuentes:/ })).toBeVisible()
  const pedidos = page.getByRole('region', { name: 'Lista de pedidos' }).getByRole('article')
  test.skip((await pedidos.count()) === 0, 'Preprod no tiene pedidos abiertos ahora mismo: no hay nada que abrir')
  const primera = pedidos.first().getByRole('button').first()
  await primera.click()
  await expect(page.getByRole('region', { name: 'Unidad elegida' }).getByText(/^Pide /)).toBeVisible()
  await page.getByRole('group', { name: 'Canal' }).getByRole('button').nth(1).click()
  await expect(pedidos.first()).toBeVisible()
  await page.setViewportSize({ width: 1366, height: 768 })
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight)).toBe(true)
})
```

(Los nombres de las variables de las credenciales son los de `.env.e2e.example`; si el supertécnico usa otros, los de ese
fichero.)

- [ ] **Step 3: Comprobar que las E2E se listan**

Run: `npx playwright test --list tests/e2e/pedidos-clientes.spec.ts`
Expected: las dos pruebas listadas (se ejecutan en preprod, fuera de este plan).

- [ ] **Step 4: Versión, CHANGELOG y novedades**

- `package.json` y `package-lock.json` a `0.9.26` (`npm version 0.9.26 --no-git-tag-version`).
- `CHANGELOG.md`: entrada `[0.9.26] — servidor y web, sin fecha (preprod)`: «Pedidos» (Teléfonos, supertécnico y admin) con los
  pedidos pendientes de Amazon, las webs y el Simulador, si cada unidad tiene móvil y si cuadra con lo que dejó el taller;
  Asignaciones dice el pedido de cada IMEI; «Pedidos» de Stock pasa a llamarse «Compras». Sin la sugerencia (0.9.27).
- `docs/novedades/NOVEDADES-v0.9.26.md` (raíz), para el taller: qué es «Pedidos», qué significa cada estado, que todo se sigue
  tecleando donde hoy, el «sin pedido concreto» de Amazon y la línea nueva de Asignaciones. Sin datos reales.
- `Apuntes/plan-futuro.md` §5: la pieza 2 con la 0.9.26 hecha (sin producción) y la 0.9.27 como siguiente; los pendientes de la
  spec §13.

- [ ] **Step 5: Comprobación final de la web**

Run: `npm run check`
Expected: PASS (con la inestabilidad conocida de «Comandos», repetir el fichero solo).

- [ ] **Step 6: Commits**

```bash
cd /c/Users/dev/Documents/wt/web-0926-pedidos
git add tests package.json package-lock.json CHANGELOG.md
git commit -m "chore: 0.9.26, E2E de pedidos y Compras en las E2E de Stock"
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/novedades/NOVEDADES-v0.9.26.md
git commit -m "docs: novedades de la 0.9.26 (Pedidos que lee y avisa)"
```

---

## Después de este plan (con el usuario; no lo hacen los subagentes)

1. **Revisión final** de las dos ramas (requesting-code-review) y ensayo con datos reales (Task 9, paso 4) revisado con el usuario.
2. **Merge `--no-ff`** de `dev_v0926_pedidos` en los dos `main`, **con el OK del usuario** y tras `git fetch` y comparar con
   `origin`. Si la 0.9.25 «Qué revisar» ha entrado antes en la web, traer `main` a la rama primero (y renumerar si hace falta).
   Push, gitlinks de la raíz.
3. **Deberes del usuario antes de preprod** (spec §11): compartir PEDIDOS 2026 y el Simulador con `erp-nsys-lector` como Lector,
   activar la API de Google Sheets en el proyecto de Google y decidir la clave de ShippyPro.
4. **Preprod:** el usuario aplica `migracion-pedidos.sql` (vista previa, después la migración); la clave a
   `/opt/reparaciones/secretos/` (600) y los ajustes `PEDIDOS_*` al compose (los IDs de las hojas están en `Deduccion_IMEIs.gs`;
   los marketplaces, en las notas). Bloques que empiezan por `hostname`. Primera vuelta: la píldora en verde, el maestro recargado
   una vez (los SKU de los canales), las E2E en Chromium.
5. **Aceptación** (spec §10): una mañana de trabajo comparando «Pedidos» con ShippyPro y PEDIDOS 2026; el usuario revisa 10-20
   pedidos. Con su OK, **producción en horario** (bloques con `hostname` y `backup-erp.sh`), fecha en el CHANGELOG, tag `v0.9.26`.
6. Después, la **0.9.27** (sugerencia, «¿Prefieres otro?», «Asignar trabajo»): su propio plan desde la spec §7 y §9.4-9.5.
