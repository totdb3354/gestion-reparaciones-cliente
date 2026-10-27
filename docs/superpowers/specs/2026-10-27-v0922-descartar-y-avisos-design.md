# 0.9.22 pieza 3 — «Descartar» en «No está en NSYS» y avisos con barra y ×

Diseño aprobado por el usuario el 2026-10-27, antes de subir la 0.9.22 a producción (va con ella). Prototipo:
`assets/2026-10-27-v0922-prototipo-descartar.html` (v2, sobre el aprobado el 25/10; datos inventados). Spec de las piezas 1 y 2:
`2026-10-25-v0922-plantillas-nsys-design.md`.

## 1. Por qué

- En «No está en NSYS» (spec 0.9.22 §3.4) salen los IMEI con trabajos del taller que NSYS no conoce. Con la base de producción del
  26/10 hay 12, y alguno no debería estar: un aparato sin IMEI («SIN-IMEI-CAM», una reparación de cámara), móviles de clientes
  que nunca serán stock… «Hecho» no sirve para eso: quiere decir «ya está dado de alta» y vuelve a salir si NSYS sigue sin tenerlo.
- Los avisos de abajo de la app no son iguales: el aviso breve tiene barra de tiempo pero no × para cerrarlo; el de Asignaciones
  con «Deshacer» no tiene ni barra ni ×.

## 2. Decisiones

| Tema | Decisión |
|---|---|
| Dónde | **Solo en «No está en NSYS».** En Color y Grado ya está «Volver a lo de NSYS» (desde «Corregir…») y en Revisar a mano «✓ Revisado». |
| Qué es | Un enlace **«descartar»** discreto al lado de «Hecho». Pide **motivo obligatorio** en una ventana de confirmación. |
| Cuánto dura | Las importaciones de NSYS **no** lo devuelven. **Solo vuelve a salir si el taller le hace un trabajo nuevo** (una asignación posterior al descarte). Si NSYS lo da de alta, la fila se va sola. |
| Deshacer | **«Ver descartados (n)»** al pie de la tabla: quién, cuándo, motivo y «deshacer». |
| Dónde se guarda | **Tabla nueva `Taller_descarte`**, con su migración aditiva propia (no se mezcla con «Hecho»). |
| Avisos de abajo | **Todos** los temporales con la barra que se consume y una **×** a la derecha. Los que se quedan hasta que pasa algo («Hay una versión nueva», «Sin conexión», el de inactividad, los errores) **no cambian**. |
| Versión | Sigue siendo la **0.9.22** (aún no está en producción): pieza 3, con su plan y sus ramas. |

## 3. Datos

`sql/migracion-descartar-nsys.sql` (aditiva, no idempotente, con vista previa `YA_EXISTEN` 0 y comprobación `CREADAS` 1), también
en `sql/crear_bd.sql` (y su DROP), como la pieza 2:

```sql
CREATE TABLE Taller_descarte (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del taller, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se descartó (hora de Madrid)',
    MOTIVO   VARCHAR(500) NOT NULL,
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_descarte_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
```

Un descarte por IMEI: descartar otra vez lo sustituye. El servidor anterior no lee la tabla (vuelta atrás sin tocar la base).

## 4. Servidor

### 4.1 Cuándo vale un descarte

Una fila de «No está en NSYS» está **descartada** si su IMEI (por `claveCruce`) tiene descarte con `FECHA` **no anterior** a la
**última asignación** del taller de ese IMEI. `AltaTaller` gana `ultimoEn`: la fecha de asignación de su último trabajo (el mismo
que ya da `ultimo`, por asignación y luego por id; hoy solo guarda `desde`, la primera). Horas de Madrid en los dos lados.

Si hay descarte pero es anterior a la última asignación (trabajo nuevo después), la fila **sale** con el aviso de §5.4.

### 4.2 Las tarjetas (`GET /api/stock-telefonos/por-pasar/tarjetas`)

- `altas`: sin las descartadas. No cuentan en la tarjeta ni en la pestaña. La plantilla de altas (`GET …/plantilla-altas`) tampoco
  las lleva.
- `FilaAltaNsys` gana al final `descartadoAntesPor`, `descartadoAntesEn`, `descartadoAntesMotivo` (null si no hay un descarte
  superado por un trabajo nuevo).
- `TarjetasPorPasar` gana al final `descartadas: FilaDescartadaNsys[]`, del descarte más reciente al más antiguo:
  `imeiSn`, `modelo`, `ultimoTrabajo`, `categoria` (como en `altas`), `descartadoPor`, `descartadoEn`, `motivo`.

### 4.3 Rutas nuevas

Bajo `/api/stock-telefonos/por-pasar`, con el permiso del resto (SUPERTECNICO y ADMIN; 403 a TECNICO y sin sesión):

- `PUT /descarte` `{imeiSn, motivo}` → 204. Descarta (o sustituye el descarte del IMEI), **quita la marca «Hecho» de esa fila
  (tarjeta ALTA) si la hay** y apunta en el registro de actividad `DESCARTAR_ALTA_NSYS`, detalle `IMEI: <imei>`, motivo = el
  motivo. 400 con: «Falta el IMEI», «Un IMEI no puede pasar de 40 caracteres», «Falta el motivo», «El motivo no puede pasar de
  500 caracteres». IMEI y motivo se limpian de espacios a los lados.
- `DELETE /descarte` `{imeiSn}` → 204. Quita el descarte y, si había uno, apunta `DESHACER_DESCARTE_NSYS`, detalle
  `IMEI: <imei>`. Sin descarte no hace nada (204). 400 con «Falta el IMEI» o «Un IMEI no puede pasar de 40 caracteres».
- Cada una en **una transacción READ COMMITTED** (escritura y línea del registro juntas; ver la lección del error 1020 de MariaDB
  con «Revisado»).

### 4.4 Tras cada importación ACTUAL que acaba en OK

Junto al repaso de las marcas «Hecho» (y con la misma condición: solo si el cierre fue bien): se **borran los descartes de los IMEI
que NSYS ya conoce** (están en el espejo, por `claveCruce`). Los descartes superados por un trabajo nuevo **se quedan** (dan el aviso
de §5.4) hasta que el IMEI se dé de alta, se descarte otra vez o se deshaga. Un fallo aquí no rompe la importación (se apunta en el
log del servidor, como el repaso). Línea de log si borra alguno: `NSYS: descartes de «No está en NSYS» quitados: N (ya en NSYS)`.

### 4.5 Compatibilidad

Los campos nuevos van al final y las rutas viejas no cambian: la web 0.9.21 y la web de las piezas 1-2 siguen funcionando con este
servidor. El servidor anterior funciona con la tabla nueva puesta.

## 5. Web

### 5.1 «descartar»

En las filas de «No está en NSYS» **sin marca «Hecho» que valga**, al lado del botón «Hecho»: enlace de 11 px, gris (`#8a97a8`),
rojo al pasar el ratón, texto **«descartar»**, `title` «No va a NSYS: quitarlo de la lista (con motivo)», `aria-label`
«Descartar <imei>». Deshabilitado mientras se guarda algo de la tarjeta (como «Hecho»).

### 5.2 La ventana

Diálogo modal (el de la app), título **«Descartar de «No está en NSYS»»**:

- Texto: «**<imei>** deja de salir aquí y no va en la plantilla de altas. Las importaciones de NSYS no lo devuelven; solo sale otra
  vez si el taller le hace un trabajo nuevo.»
- Campo **«Motivo (obligatorio)»** (área de texto, placeholder «Por qué no va a NSYS», hasta 500 caracteres), debajo en gris:
  «Ejemplos: «no es un móvil, es una cámara», «móvil de un cliente, no es stock».»
- Botones **«Cancelar»** y **«Descartar»** (rojo). «Descartar» no se activa con el motivo vacío o solo con espacios.
- Se cierra con «Cancelar», Esc o clic fuera (no mientras guarda). Un doble clic no envía dos veces. Si falla, el error sale por el
  diálogo de siempre y la ventana sigue abierta con lo escrito; si va bien, se cierra y sale el aviso «Descartado «<imei>»».

### 5.3 «Ver descartados»

Al pie de la tabla de «No está en NSYS», si hay descartadas: enlace gris **«Ver descartados (n)»** / **«Ocultar descartados»** que
despliega una tabla en gris: IMEI, Modelo, Último trabajo, Descartado (`<quién> · dd/mm/aaaa hh:mm`), Motivo (en cursiva, entre
comillas), y **«deshacer»** en cada fila (sin confirmación). Al deshacer, aviso ««<imei>» vuelve a la lista». Sin descartadas no sale
el enlace (y la tabla se pliega).

### 5.4 Una fila que vuelve por un trabajo nuevo

Debajo del IMEI, en ámbar (11 px): «Salió otra vez: trabajo nuevo después de que <quién> lo descartara el dd/mm/aaaa hh:mm
(«<motivo>»)».

### 5.5 Ayuda de la tarjeta

Se añade al final de la ayuda de «No está en NSYS»: «Si una fila no debería estar aquí (no es un móvil, es de un cliente…),
«descartar» la quita con un motivo; solo vuelve si el taller le hace un trabajo nuevo.»

### 5.6 Avisos de abajo (toda la app)

- **Aviso breve** (`AvisoBreveHost`, 4 s): conserva la barra amarilla y el cierre al hacer clic; gana una **×** a la derecha
  (`aria-label` «Cerrar aviso», `title` «Cerrar»).
- **Aviso con «Deshacer»** (`useAccionConDeshacer`, Asignaciones, 8 s): gana la **barra amarilla** que se consume en 8 s y la **×**
  (cierra sin deshacer). Sigue sin cerrarse al hacer clic en el texto (para no perder el «Deshacer»).
- La barra respeta `prefers-reduced-motion` como la de hoy.
- No cambian: «Hay una versión nueva del programa · Recargar» (se queda hasta recargar, decisión del usuario del 27/10),
  «Sin conexión», «Vas a salir por inactividad» y los errores.

## 6. Pruebas

- **Servidor:** la regla de §4.1 (descarte más reciente, igual o más antiguo que la última asignación; IMEI en mayúsculas o
  minúsculas); `altas` sin descartadas y con el aviso de §5.4; `descartadas` en orden; la plantilla de altas sin descartadas;
  `PUT`/`DELETE` (400 de cada caso, 204, registro de actividad, la marca «Hecho» que se quita); el repaso de §4.4 (borra los de
  IMEI ya en NSYS, deja los superados; un fallo no rompe la importación); permisos (403); DDL de la migración y `crear_bd.sql`;
  contrato OpenAPI.
- **Web:** «descartar» solo en «No está en NSYS» y sin marca; la ventana (sin motivo no deja, Cancelar, Esc, no envía dos veces,
  error que deja la ventana abierta); «Ver descartados» con «deshacer»; el aviso de §5.4; la × y la barra en los dos avisos; el de
  «Deshacer» dura 8 s y no se cierra al hacer clic en el texto.
- **E2E en preprod** (`plantillas-nsys`): crear por la API una asignación de un IMEI inventado que NSYS no conoce; descartarlo con
  motivo (sale de la lista, de la cuenta y de la plantilla; está en «Ver descartados»); deshacer (vuelve); descartarlo otra vez,
  crear un trabajo nuevo y ver que vuelve con el aviso; limpiar (asignaciones, descarte y su línea del registro).

## 7. Despliegue

- **Preprod:** `migracion-descartar-nsys.sql` (`YA_EXISTEN` 0 → `CREADAS` 1), servidor y web; E2E.
- **Producción:** el guion de la 0.9.22 pasa a **tres migraciones** (pieza 1, pieza 2 y esta), todas aditivas e instantáneas, luego
  servidor y web. En horario.
- **Vuelta atrás:** servidor y web anteriores con la tabla puesta; no hace falta quitarla.

## 8. Fuera de alcance

- «Descartar» en Color, Grado o Revisar a mano; «Revisado todo».
- El descarte en el rastro del IMEI (queda en el registro de actividad).
- Cambiar los avisos que se quedan (versión nueva, sin conexión, inactividad, errores).
