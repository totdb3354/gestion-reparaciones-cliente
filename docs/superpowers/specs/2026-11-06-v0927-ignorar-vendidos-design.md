# 0.9.27 — «ignorar» en los vendidos y de baja que salen en ámbar

Spec de diseño. Aprobada por el usuario en el brainstorming del 06/11/2026, con el prototipo
`assets/2026-11-06-v0927-prototipo-ignorar.html` (v3, datos inventados). Base, servidor y web.

## 1. El problema

Desde la 0.9.22 (pieza 4 §6), un móvil **vendido** o **de baja** en NSYS sale en «Cambio de color», «Cambio de grado» y «Revisar a
mano» si **ha vuelto**: el taller tiene un trabajo suyo un día posterior a la venta (o a la baja). Sale con una línea ámbar («Vendido
en NSYS el … · el taller lo tiene desde el …: haz antes la devolución en NSYS») y no va en la plantilla de color.

Esa regla acierta cuando el cliente devuelve el móvil y nadie apunta la devolución en NSYS (la devolución es a mano y puede
olvidarse: el aviso es el recordatorio). Pero también salta cuando **no ha vuelto**. Caso real del 06/11: un iPhone 15 con fecha de
venta en NSYS del 26/10, que el espejo vio «En revisión» hasta el 28/10 a las 13:01 (ahí vio «Vendido (Released)»). El taller le
cambió el chasis esa mañana, antes de que saliera. El ERP ve un trabajo del 28 después de una venta del 26 y lo da por vuelto. La
fila se queda para siempre en las tarjetas (nadie va a apuntar una devolución que no ha habido).

## 2. Decisiones (brainstorming del 06/11)

| Qué | Decisión |
|---|---|
| Qué se hace | El ámbar se queda (es el control de las devoluciones olvidadas). Cada fila ámbar gana un enlace **«ignorar»**: «está vendido de verdad, no ha vuelto». |
| Dónde | Al lado de «Hecho», como «descartar» de «No está en NSYS» (gris, rojo al pasar el ratón). En «Revisar a mano», que no tiene «Hecho», al lado de «Corregir…». |
| Ventana | **Ninguna**: un clic. Se deshace desde «Ver ignorados». Sin motivo. |
| Alcance | El **móvil entero**: sale de las tres tarjetas a la vez y no cuenta en sus números ni en el de la pestaña. Vendidos **y** de baja. |
| Cuándo vuelve a salir | **Sin ámbar** cuando NSYS lo vuelve a tener en la empresa (la devolución lo pasa de «Released» a «In process»): fila normal, con su descuadre, y en la plantilla. **En ámbar**, con «Salió otra vez…», si el taller le hace un trabajo nuevo (una asignación posterior al ignorado), como «descartar». |
| Deshacer | «Ver ignorados (n)» al pie de **cada** tabla, con los ignorados que saldrían en esa; «deshacer» lo devuelve a las tres. |
| Dónde se guarda | Tabla nueva **`Taller_vuelta_ignorada`** (no en `Taller_descarte`: los descartes se borran solos cuando NSYS conoce el IMEI, y un vendido siempre está en el espejo). No se llama solo «ignorado» porque los grados F, FD, RMA e ID ya son «ignorados» en el código (`GradoTaller`). |
| Número | **0.9.27** (decisión del usuario). Los pedidos, que esperaban como 0.9.27, pasan a la **0.9.28**. |

Descartado en el brainstorming: quitar el ámbar y fiarse solo del estado de NSYS (se perdía el recordatorio), y cambiar la fecha de
la regla (día más tardío entre la venta de NSYS y el día en que el espejo la vio): arreglaba este caso, pero no el de un móvil
vendido días antes de repararlo.

## 3. Base de datos

`sql/migracion-ignorar-vuelta-nsys.sql` (aditiva, no idempotente, con vista previa `YA_EXISTEN` 0 y comprobación `CREADAS` 1),
como la de los descartes, y lo mismo en `crear_bd.sql` (con su `DROP TABLE IF EXISTS`) y en `reset-uat.sql` (`DELETE FROM`):

```sql
CREATE TABLE Taller_vuelta_ignorada (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del espejo, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se ignoró (hora de Madrid)',
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_vuelta_ignorada_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

Un ignorado por IMEI: ignorar otra vez lo sustituye (quién y cuándo de ahora). El servidor anterior no lee la tabla (vuelta atrás
sin tocar la base).

## 4. Servidor

### 4.1 Cuándo vale un ignorado

Solo cuenta en un móvil **vendido o de baja que ha vuelto** (la regla de la pieza 4 §6 no cambia). Su ignorado (por `claveCruce`)
**vale** si su `FECHA` **no es anterior** a la **última asignación** del taller de ese IMEI (A…, AG…, AP…; la de mayor fecha de
asignación y luego por id; sin asignaciones, el último trabajo), como `AltaTaller.ultimoEn` de los descartes: terminar la asignación
que estaba abierta al ignorar no lo saca (su fila R/G/P lleva la hora del cierre, que no es un trabajo nuevo).

- **Vale** → el móvil no sale en ninguna de las tres tarjetas: ni en «Cambio de color», ni en «Cambio de grado», ni en «Revisar a
  mano» (avisos y «Sin grado en NSYS»). Tampoco cuenta en sus números ni en el repaso de las marcas «Hecho» (sus marcas se borran
  en la importación siguiente, como las de cualquier fila que ya no está).
- **Superado** (hay una asignación posterior) → sale como hoy, en ámbar, y su fila lleva quién y cuándo lo ignoraron.
- Un móvil **en la empresa** no mira el ignorado: sale como cualquier otro, sin ámbar.

### 4.2 Las tarjetas (`GET /api/stock-telefonos/por-pasar/tarjetas`)

- `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys` y `FilaSinGradoNsys` ganan al final `ignoradoAntesPor` y `ignoradoAntesEn`
  (null salvo con un ignorado superado por un trabajo nuevo).
- `TarjetasPorPasar` gana al final `ignorados: FilaIgnoradaNsys[]`, del ignorado más reciente al más antiguo (y por IMEI): uno por
  móvil ignorado que, sin el ignorado, saldría en alguna tarjeta. Campos: `imeiSn`, `modelo`, `modeloMaestro`, `capacidad`,
  `esEsim` (para su descripción, como las demás filas), `vendidoEn`, `bajaEn`, `vueltoDesde` (su vuelta), `ignoradoPor` (su
  nombre; null si el usuario ya no existe), `ignoradoEn` y `tarjetas`: en cuáles saldría, de entre `COLOR`, `GRADO` y `REVISAR`,
  en ese orden. «En cuáles saldría» se calcula con las mismas reglas de cada tarjeta, como si no estuviera ignorado.
- La plantilla de color no cambia (ya no lleva los que han vuelto). `GET /por-pasar` (la lista de la 0.9.21) no cambia.

### 4.3 Rutas nuevas

Bajo `/api/stock-telefonos/por-pasar`, con el permiso del resto (SUPERTECNICO y ADMIN; 403 a TECNICO y sin sesión), cuerpo
`{imeiSn}` (esquema `PorPasarPeticionIgnorado`):

- `PUT /ignorado` → 204. Ignora (o sustituye el ignorado del IMEI) y apunta en el registro de actividad `IGNORAR_VUELTA_NSYS`,
  detalle `IMEI: <imei>`. No comprueba que el móvil esté en ámbar (como «descartar»): un ignorado de un móvil en la empresa no hace
  nada y lo quita el repaso.
- `DELETE /ignorado` → 204. Quita el ignorado y, si había uno, apunta `DESHACER_IGNORAR_VUELTA_NSYS`, detalle `IMEI: <imei>`. Sin
  ignorado no hace nada (204).
- 400 con «Falta el IMEI» o «Un IMEI no puede pasar de 40 caracteres» (el IMEI se limpia de espacios a los lados).
- Cada una en **una transacción READ COMMITTED** (la escritura y la línea del registro juntas, como «descartar»).

### 4.4 Tras cada importación ACTUAL que acaba en OK

Junto al repaso de los descartes (y con la misma condición): se **borran los ignorados de los IMEI que NSYS vuelve a tener en la
empresa** (la fila más reciente del espejo de ese IMEI en stock, de pedido o en una caja que no cuenta). Así, si se vende otra vez
y vuelve, se decide de nuevo. Los superados por un trabajo nuevo se quedan (dan «Salió otra vez…») hasta que se ignore otra vez,
se deshaga o NSYS lo tenga en la empresa. Un fallo aquí no rompe la importación (se apunta en el log del servidor). Línea de log si
borra alguno: `NSYS: ignorados de las listas quitados: N (otra vez en la empresa)`.

### 4.5 Compatibilidad

Los campos nuevos van al final y las rutas viejas no cambian: la web 0.9.26 funciona con este servidor (no ve los ignorados ni
puede deshacerlos, pero sí ve las listas sin ellos). El servidor 0.9.26 funciona con la tabla puesta. La web nueva con el servidor
0.9.26 (vuelta atrás): `ignorados` no llega (se trata como vacío) y «ignorar» daría error, así que la web se despliega después del
servidor.

## 5. Web

### 5.1 «ignorar»

En las filas con línea ámbar (`vueltoDesde`) de «Cambio de color» y «Cambio de grado», al lado de «Hecho» (solo sin marca, como
«descartar»), y de «Revisar a mano», al lado de «Corregir…»: enlace **«ignorar»** con las clases de «descartar» (`text-[11px]
text-texto-suave`, `hover:text-fila-urgente-brd`), `title` «No ha vuelto: quitarlo de las listas hasta que NSYS lo tenga otra vez
en la empresa (o el taller le haga un trabajo nuevo)», `aria-label` «Ignorar <imei>». Deshabilitado mientras se guarda algo de la
tarjeta. Un clic, sin ventana: `PUT /ignorado`, recarga de las tarjetas y aviso **«Ignorado «<imei>»: sale en «Ver ignorados» hasta
que vuelva»**.

### 5.2 «Ver ignorados»

Al pie de las tablas de «Cambio de color», «Cambio de grado» y «Revisar a mano», si esa tarjeta tiene ignorados (los de
`ignorados` cuyo `tarjetas` la incluye): enlace gris **«Ver ignorados (n)»** / **«Ocultar ignorados»** (plegado al entrar), que
despliega una tabla en gris (`aria-label` «Ignorados»), como «Ver descartados»: IMEI, Modelo (su descripción, como las demás
filas), En NSYS («Vendido en NSYS el dd/mm/aaaa» o «De baja en NSYS desde el dd/mm/aaaa»), Ignorado (`<quién> · dd/mm/aaaa hh:mm`;
sin nombre, «—») y **«deshacer»** (`aria-label` «Deshacer ignorar <imei>», sin confirmación): `DELETE /ignorado`, recarga y aviso
**««<imei>» vuelve a las listas»**. Sin ignorados no sale (y vuelve plegado).

### 5.3 «Salió otra vez…»

Una fila con `ignoradoAntesEn` lleva, debajo de su línea ámbar y en ámbar, en negrita: **«Salió otra vez: trabajo nuevo después
de que <quién> lo ignorara el dd/mm/aaaa hh:mm»** (sin nombre, «—»). Se puede ignorar otra vez.

### 5.4 Ayuda

La ayuda de «Cambio de color», «Cambio de grado» y «Revisar a mano» (`AYUDA`) gana al final, tal cual: « Un vendido o de baja que
sale en ámbar tiene trabajo del taller después de la venta o de la baja: si es una devolución, apúntala en NSYS; si no ha vuelto,
«ignorar» lo quita de las tres listas hasta que NSYS lo tenga otra vez en la empresa o el taller le haga un trabajo nuevo.»

### 5.5 Lo demás

Los números de las tarjetas y de la pestaña ya llegan sin los ignorados (no cambia `cuentas`). Con un servidor anterior,
`ignorados`, `ignoradoAntesPor` e `ignoradoAntesEn` no llegan: sin «Ver ignorados» ni «Salió otra vez…».

## 6. Pruebas

- **Servidor (unitarias):** la regla de §4.1 (vale, superado por una asignación posterior, la R/G/P del cierre no lo supera, sin
  asignaciones, en la empresa no mira); que un ignorado que vale saca el móvil de las cuatro listas y sale en `ignorados` con sus
  `tarjetas`; el superado lleva `ignoradoAntes*`; el DAO con el JdbcTemplate mockeado; la migración igual que `crear_bd.sql`
  (como `DdlDescarteTest`); las rutas y sus 400; los permisos (`RolesStockTelefonosTest`); el repaso tras la importación; el
  contrato OpenAPI.
- **Web (Vitest):** los textos de §5; «ignorar» solo en las filas ámbar y sin marca (en «Revisar a mano», al lado de «Corregir…»);
  «Ver ignorados» por tarjeta, desplegar, «deshacer»; «Salió otra vez…»; con un servidor anterior (sin los campos), nada nuevo.
- **E2E en preprod (Chromium):** con los móviles que han vuelto de la copia de producción (si no hay ninguno, se salta, como
  `revisar-a-mano.spec.ts`): «ignorar» lo quita de las tres tarjetas y de sus números, sale en «Ver ignorados» de cada una,
  «deshacer» lo devuelve; al final lo deja sin ignorado (también si la prueba falla). El «trabajo nuevo» se cubre en las
  unitarias del servidor (no se crean asignaciones sobre móviles de la copia de producción).

## 7. Entrega

- Ramas `dev_v0927_ignorar_vendidos` en servidor y web, en worktrees nuevos `wt/servidor-0927-ignorar` y `wt/web-0927-ignorar`
  desde `origin/main` (que ya lleva la 0.9.26).
- Orden de despliegue: **migración → servidor → web**. Cumple el despliegue en horario (migración aditiva y rápida, la web 0.9.26
  aguanta con el servidor nuevo, vuelta atrás = código anterior).
- Versión **0.9.27** (web `package.json`, `CHANGELOG.md`, `docs/novedades/NOVEDADES-v0.9.27.md` de la raíz). Las novedades de los
  pedidos que hoy se llaman `NOVEDADES-v0.9.27.md` pasan a `NOVEDADES-v0.9.28.md` (los pedidos se renumeran al traer `main` a sus
  ramas, fuera de esto).

## 8. Fuera de alcance

- Un control propio de las devoluciones olvidadas (por ejemplo, una marca al asignar un IMEI que NSYS da por vendido): se apunta
  en `Apuntes/plan-futuro.md` como backlog.
- Motivo al ignorar, y el ignorado en el rastro del IMEI.
- El número del trabajo nuevo (A…) en «Salió otra vez…» (el prototipo lo enseñaba; se deja como en «descartar», sin él).
