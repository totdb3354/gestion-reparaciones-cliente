# Pedidos: reserva de IMEI al entrar el pedido — diseño

Fecha: 2026-11-24. Estado: criterios aprobados en conversación con Imad; texto escrito con su permiso para empezar.
Versión: la siguiente libre al sacarla (servidor + web + migración aditiva).
Base: Pedidos pieza 2 (`EstadoUnidad`, `Recomendador`, `LecturaFilas`, `VendidosController`), «Caja · …» y celda del IMEI
(`2026-11-24-pedidos-celda-imei-y-cajas-design.md`, otra sesión), devoluciones del bot (otra sesión, `Apuntes/prompt-bot-devoluciones.md`).
Prototipo aprobado (v5): `assets/2026-11-24-pedidos-reserva-imei-prototipo.html` (celda, historial y simulador del reparto).

## Para qué

Hoy el IMEI de una fila lo pone logística al empaquetar. Hasta entonces el teléfono sigue en NSYS como stock y las webs y Amazon lo
siguen vendiendo: en fin de semana se vende lo que ya está vendido. Con la reserva, **en cuanto entra el pedido el ERP le aparta un
teléfono concreto**, elegido con los criterios de Imad, y el bot de stock lo descuenta de NSYS.

## 1. Qué es una reserva

- Un IMEI que el ERP aparta para una fila viva de ShippyPro **sin IMEI**. Va en una columna propia (`IMEI_RESERVADO`), no en `IMEI`.
- **Cuenta como «sin IMEI» para todo** (contadores, filtro, Listado, plugin de la web, libros de Amazon, asociación), porque todo eso
  lee `IMEI`. **Solo el bot de vendidos lo descuenta** (§6).
- **Una reserva no cambia de fila**: el reparto nunca quita una reserva para dársela a otro pedido, aunque sea de un canal con más
  prioridad. La prioridad ordena solo a las filas que esperan teléfono en la misma pasada.
- Sin reserva = no hay stock que cumpla los criterios.

## 2. Qué filas reservan

Filas de `Pedido_fila` con `ORIGEN = 'SHIPPYPRO'`, `CERRADA_EN IS NULL`, `MARCA IS NULL`, sin `IMEI`, con canal y con un SKU que el
maestro conoce para ese canal (`AceptaSku`). Una fila con cualquier marca (Cancelado, Pendiente de color…) no reserva: la marca dice
por qué sigue sin IMEI.

**Orden en que reservan:** canal (Amazon y Amazon Stockroom → Distribuidores → Particulares → BackiPhone), después la fecha del pedido
(`FECHA_COMPRA`; `FECHA` si falta), después `ID_FILA`. Un pedido de varias unidades son varias filas: cada una reserva lo que haya,
aunque el pedido quede a medias.

## 3. Qué teléfonos se pueden reservar

Los del espejo de NSYS en la empresa con estado **STOCK** (las siete ubicaciones vendibles; nunca «Pedidos realizados», revisar,
CF…), que no estén puestos en otra fila (`IMEI` de una fila viva, como «ya puestos» de `VistaFilas`) ni reservados en otra. Mismo
modelo y capacidad que pide el pedido (`Aceptacion.pide()`), siempre. Nunca: grado **ID** (solo lo pone una persona si el cliente lo
pide), F, FD ni RMA. Con un trabajo del taller abierto, solo en el puesto 1 y si su SKU que vale ya es uno de los que acepta el pedido
(la regla del recomendador: no se le cambia el trabajo al técnico).

**Grado que cuenta:** el que vale (NSYS con lo del taller, `LoQueVale`). Los teléfonos en **Devolución** («Funcionalidad pendiente»)
o **sin caja** («En revisión») cuentan como **N/A** sea cual sea su grado: están por revisar.

## 4. Qué teléfono se elige

### 4.1 Amazon (AMZ y FNSK), pedido con SIM física

| Puesto | Teléfono | Formato | Color |
|---|---|---|---|
| 1 | Encaja tal cual: en el abanico del maestro (`ac.acepta(sku)` o `aceptaColorYGrado`, sin Ms) o N/A | SIM física | el del pedido |
| 2 | B o C (el taller le pone chasis → A) | SIM física | cualquiera |
| 3 | Grado del abanico, o N/A | eSIM | el del pedido |
| 4 | B o C | eSIM | cualquiera |
| 5 | N/A | SIM física | otro |
| 6 | N/A | eSIM | otro |
| 7 | A o A- (hay que cambiarle el chasis; es una pena gastarlo) | SIM física | otro |
| 8 | A o A- | eSIM | otro |
**Pedido eSIM:** la misma tabla cambiando SIM física por eSIM y al revés (inverso exacto).

**Ms: nunca se reserva** (Imad, 25/11; sustituye al «puesto 9, último recurso a España» del 24/11). Ninguna plataforma vende un Ms
como A, B o C: darlo es una decisión excepcional de una persona. Si no hay nada en los puestos 1-8, la fila se queda «sin stock» y quien
la mira puede picar un Ms a mano. El recomendador de la celda sigue proponiendo los Ms, pero siempre detrás de todos los demás.

### 4.2 Webs (Distribuidores, Particulares, BackiPhone)

| Puesto | Teléfono | Formato | Color |
|---|---|---|---|
| 1 | El SKU exacto del pedido en la columna del canal (`ac.acepta(skuQueVale)`; grado y «100» incluidos) | el del pedido | el del pedido |
| 2 | B o C, **solo si el pedido es A o A100** (la batería va con la reparación); para A- no | el del pedido | el del pedido |

Nunca otro formato, otro color ni Ms en la web: eso lo negocia y lo pone una persona. Los pedidos web de N/A no existen hoy; cuando
existan, el puesto 1 (SKU exacto) ya los cubre.

### 4.3 Dentro de un puesto

1. **La caja:** Caja · Listos → Caja · Reparación = Caja · Pantalla rota → Caja · Pulir → Caja · Glass → Devolución = En revisión.
2. **El grado:** A- → A-100 → A / A+ → A100 / A+100 → N/A. En los puestos de B o C: B → C.
3. **Más tiempo en stock:** `receivingDate` más antigua primero; después el IMEI.

Principio (Imad): no sobrecargar a los técnicos. De Listos solo se coge lo que encaja tal cual; lo que necesita una reparación que lo
cambie sale de las cajas del taller.

## 5. Ciclo de vida

Una **pasada de reservas** al final de cada vuelta de `LecturaFilas` (cada 5 min), después de las fechas de salida. Es secundaria: si
falla, la vuelta sigue OK con un aviso. En este orden:

1. **El IMEI ya está puesto** en una fila con reserva (lo picó logística, lo puso la asociación del libro, un traspaso…):
   - mismo IMEI que el reservado → `RESERVA_CONFIRMADA`;
   - otro → `RESERVA_SUSTITUIDA` (el reservado vuelve a stock).
   En los dos se borra la reserva. Si lo pone una persona por la celda, se hace en ese momento y con su usuario (§7); la pasada
   cubre lo que no pasa por la celda.
2. **Soltar reservas** (`RESERVA_QUITADA`, con el motivo en `DETALLE`):
   En este orden (el mismo criterio que la 0.9.47 para las cancelaciones; separado el 25/11):
   - `ENVIADA_SIN_IMEI`: la fila se cerró **con** envío pero sin IMEI (ver abajo);
   - `CANCELADA`: «Cancelar móvil» (marca `CANCELADO`) sin la etiqueta impresa;
   - `YA_NO_ESTA`: la fila se cerró sin envío y sin «Cancelar móvil» (ya no está en ShippyPro);
   - `MARCADA`: otra marca (`ESPERA_STOCK`, `EN_REVISION`, `PENDIENTE_COLOR`) sin la etiqueta impresa;
   - `CON_ETIQUETA`: con marca y con la etiqueta ya impresa: el teléfono pudo salir con ella;
   - `NO_ENCONTRADO`: el teléfono está en «Pendiente revisar si hay error de inventario» o en la «MV …» (lo movió una persona en
     NSYS porque no lo encuentra);
   - `NO_DISPONIBLE`: el teléfono ya no está en la empresa o su estado no es STOCK (vendido, baja, otra caja que no cuenta). «De
     pedido» solo vale con `al-bot` encendido (es el descuento de esta misma reserva); con `al-bot` apagado, un reservado «de
     pedido» se vendió por otro lado (p. ej. una venta directa del Simulador) y se suelta como `NO_DISPONIBLE`;
   - `PUESTO_EN_OTRA`: el IMEI reservado se ha puesto en otra fila (no es candidato a devolución: se vende por esa fila).
   - (detalle de `ENVIADA_SIN_IMEI`) seguramente salió el reservado sin picarlo: se suelta, pero **no** es candidato a devolución.
   Con `al-bot` encendido, un teléfono en «Pedidos realizados» con reserva es lo esperado (el bot ya lo descontó): **no** se suelta. Todo se vuelve a
   comprobar con la fila bloqueada: si una persona acaba de poner el IMEI, no se suelta (la pasada siguiente lo resuelve).
3. **Reservar** las filas que no tienen (orden §2, teléfono §4). Cada reserva apunta `RESERVADO` con `IMEI_NUEVO` = el reservado y
   `DETALLE` = el porqué («Puesto 1 · Caja · Listos · A-»). Si no hay teléfono, apunta **una sola vez** `SIN_STOCK` (columna
   `SIN_STOCK_EN`; se borra al reservar).

Todo en una transacción por fila, con el `SELECT … FOR UPDATE` de la fila como hoy (`SQL_FILA_MARCA_BLOQUEADA`), para no pisar a una
persona que esté poniendo el IMEI a la vez.

## 6. Bot de stock (vendidos) y devoluciones

- `GET /api/pedidos/vendidos` sirve también los IMEI **reservados** de filas vivas (fecha: `RESERVADA_EN`), **solo con el ajuste
  `pedidos.reserva.al-bot=true`** (por defecto `false`).
- **Se enciende a mano** (`PEDIDOS_RESERVA_AL_BOT=true` en el compose) **cuando la devolución del bot lleve unos días funcionando en
  producción** (Imad 25/11; orden: 0.9.47 → bot informa → bot devuelve → 0.9.48 → encender), a la vez que el ajuste
  `"informar_vendido_panel"` del bot. Mientras está apagado, la reserva se ve en el ERP y guía a logística, pero no toca NSYS; por eso
  la 0.9.48 puede subir antes.
- Con `al-bot` encendido, los reservados salen de «Saldo stock» en NSYS y el ciclo de stock los quita de las webs, de Amazon y de la
  lista pública (`Public stock & price list EU`, la que usan las ventas directas del Simulador): no hace falta tocar esos ficheros.
- **Devoluciones (en esta versión, sobre la 0.9.47; `bot/BotDAO`, `bot/Devoluciones`):** una reserva soltada es una «salida» del
  IMEI y el bot la devuelve a su caja con el motivo de lista **`RESERVA_LIBERADA`** (nota del historial «reserva soltada por …»):
  - salen: `RESERVA_SUSTITUIDA` (la devolución prioritaria: se picó otro encima), `RESERVA_QUITADA` con `CANCELADA` o `MARCADA`;
  - `RESERVA_QUITADA` con `YA_NO_ESTA` → «no se devuelve solo» (`SIN_DESTINO`, una persona comprueba si salió), como en la 0.9.47;
  - no son salida: `ENVIADA_SIN_IMEI`, `CON_ETIQUETA`, `NO_ENCONTRADO`, `PUESTO_EN_OTRA` y `NO_DISPONIBLE` (salió, lo tiene una
    persona o se vendió por otro lado: devolverlo dejaría un móvil fantasma en NSYS);
  - `RESERVADO` cuenta como «puesto»; una reserva viva en una fila abierta impide devolver (vender gana a devolver).
- **Simulador:** si en directo se coge justo la unidad reservada, logística pica otra y la reserva queda sustituida, pero ese móvil ya
  salió. El bot no devuelve un IMEI vendido en su Panel de Control (Simulador y PEDIDOS 2026) y lo informa con el resultado
  **`VENDIDO_PANEL`** (final, como `NO_APARECE`; historial «No se pudo devolver · vendido en el Panel: no se devuelve»).

## 7. Poner el IMEI en una fila reservada

`FilasDAO.ponerImei` (la celda), en la misma transacción que hoy:
- IMEI = el reservado → `RESERVA_CONFIRMADA` con el usuario («Confirmado por Fati»);
- otro IMEI → `RESERVA_SUSTITUIDA` con el usuario (`IMEI_ANTERIOR` el reservado, `IMEI_NUEVO` el puesto);
- y el apunte `IMEI` de siempre (la lista de vendidos y el resto lo siguen leyendo). Se borran `IMEI_RESERVADO` y `RESERVADA_EN`.

## 8. Base de datos (migración aditiva `sql/migracion-pedidos-reserva.sql`)

`Pedido_fila`:
- `IMEI_RESERVADO VARCHAR(20) NULL` — el IMEI apartado; NULL = sin reserva;
- `RESERVADA_EN DATETIME NULL` — cuándo se reservó;
- `SIN_STOCK_EN DATETIME NULL` — cuándo se apuntó «sin stock» (una sola vez hasta que reserve);
- índice `idx_fila_reservado (IMEI_RESERVADO)`.

`Pedido_fila_cambio.TIPO` (VARCHAR(20), sin cambio de tipo) gana: `RESERVADO`, `RESERVA_CONFIRMADA`, `RESERVA_SUSTITUIDA`,
`RESERVA_QUITADA`, `SIN_STOCK`. Comentarios de las columnas al día. `crear_bd.sql` igual. Vuelta atrás: quitar las tres columnas y el
índice (`migracion-pedidos-reserva-atras.sql`); los apuntes nuevos se pueden quedar (la web vieja los enseña como cambio de IMEI).

## 9. Servidor

- **`Reparto`** (nuevo, puro): `de(filas, stock, vale, trabajos, ocupados)` (cada `Fila` lleva su `Aceptacion`) → reservas y «sin
  stock», con §2-§4.
  Comparte con `Recomendador` `EstadoMovil`, `LoQueVale` y `Aceptacion`.
- **`Recomendador`** (los recomendados del ▾ de la celda, Imad 25/11): el IMEI reservado para la fila sale **el primero** con
  `reservado = true` («reservado para este pedido»), aunque esté «de pedido» o sea un sustituto que el recomendador no propondría; los
  reservados para **otras** filas no se proponen (se pueden picar a mano: la reserva de esa otra fila se suelta como `PUESTO_EN_OTRA`
  y la pasada siguiente le busca otro). Los Ms, siempre al final.
- **`Reservas`** (nuevo, `@Service`): la pasada de §5 con `FilasDAO`, `NsysDAO` y `TallerNsys`. La llama `LecturaFilas` al final de
  la vuelta.
- **`FilasDAO`**: leer las columnas nuevas en `FilaPedido`; `reservar`, `soltarReserva`, `apuntarSinStock`, `resolverReservaPuesta`;
  `ponerImei` con §7; `SQL_VENDIDOS` con la unión de los reservados cuando el ajuste está encendido.
- **`FilaVista`**: campos aditivos al final `imeiReservado` (nullable) y `reservadaEn` (nullable). OpenAPI y `docs/api_contract.md`.
- **`CambioImei`**: las constantes nuevas y su javadoc.

## 10. Web

- **Celda** (`CeldaImei.tsx`, sobre la celda en línea de la 0.9.46.3): sin IMEI y con reserva → el IMEI reservado con **menos
  opacidad**, sin texto. Al pulsar, el campo se abre vacío como en cualquier fila sin IMEI (sin texto de ayuda: como el prototipo,
  Imad 25/11); el `title` dice «Reservado: pica este IMEI para confirmarlo o pon otro». El rojo de «IMEI no válido» manda sobre el
  tenue. Sin reserva → como hoy.
- **Recomendados** (▾): el reservado de la fila, el primero, con la píldora azul «reservado para este pedido» en vez del nivel.
- **Contadores** (`filas.ts`, `PedidosPage.tsx`): «N sin IMEI» sigue igual (las reservadas cuentan); dos nuevos pulsables
  **«N reservadas»** y **«N sin stock»** (sin IMEI y sin reserva, editables y sin marca), con su filtro.
- **Historial** (`historial.ts`, `PanelFila.tsx`): los apuntes nuevos con **etiqueta de color** como el prototipo: Reservado (azul),
  Confirmado (verde, «Confirmado por {usuario}»), Picó otro (gris) y Vuelve a stock (morado) en `RESERVA_SUSTITUIDA`, Reserva quitada
  (ámbar; «Cancelado» naranja si `CANCELADA` o `YA_NO_ESTA`), Sin stock (naranja). La caja del `DETALLE` como píldora con su color (`CLASES_UBICACION`).
- **Ayuda** (`AyudaPedidos.tsx`): qué es una reserva y cómo confirmarla.
- Tipos regenerados (`openapi.json`, `schema.d.ts`), `fabrica.ts`.

## 11. Pruebas

- **Servidor:** `RepartoTest` con cada puesto de §4.1 y §4.2 (incluidos: Ms nunca, en ningún país ni caja; ID nunca; Devolución y sin
  caja como N/A; B/C web solo para A; caja antes que grado; antigüedad; prioridad de canal; varias unidades; un ocupado no entra);
  `ReservasTest` (confirmar, sustituir, soltar por cancelada / revisar / no disponible, «de pedido» no suelta, sin stock una vez);
  `FilasDAOTest` (columnas nuevas, `ponerImei` sobre reservada, vendidos con y sin el ajuste); `LecturaFilasTest` (la pasada al final;
  si falla, aviso y vuelta OK); `VistaFilasTest`; `OpenApiContractTest`.
- **Web:** `filas.test.ts` (contadores y filtros), `historial.test.ts` (los tipos nuevos), `CeldaImei` (tenue), `PedidosPage`.
- **Servidor (devoluciones):** `BotDAOTest` (salidas por apunte, SQL de candidatos, reservas vivas, última salida), `DevolucionesTest`
  (`RESERVA_LIBERADA`, «ya no está» sin destino, con etiqueta / enviada / puesto en otra no salen, nota «reserva soltada por»,
  `VENDIDO_PANEL`), `ReglaDevolucionesTest` (`VENDIDO_PANEL` final), `RecomendadorTest` y `VistaFilasTest` (el reservado primero,
  los de otras filas fuera).
- **Preprod:** migración, y una pasada real con el ajuste apagado; revisar con Imad las reservas que salen (E2E con datos sintéticos
  por API y limpieza, nunca en producción). Guion: `Apuntes/preprod/v0948-preprod.md`.

## 12. Despliegue

Migración aditiva; servidor y web juntos (la web vieja ignora los campos nuevos). `pedidos.reserva.al-bot` por defecto `false`:
se mira unos días cómo reserva. Se enciende a mano cuando la devolución del bot lleve días bien en producción (§6).

## Fuera de alcance

- El bot de NSYS (otra sesión; solo se le añaden aquí los casos de la reserva en la lista de devoluciones del ERP).
- Mover una reserva entre filas o recolocar reservas.
- «No lo encuentro» dentro del ERP (se hace en NSYS).
