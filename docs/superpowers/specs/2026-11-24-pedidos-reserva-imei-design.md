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
| 9 | **Ms**, último recurso: solo si el pedido va a **España** (`PAIS = 'ES'`) | SIM física | el del pedido |

**Pedido eSIM:** la misma tabla cambiando SIM física por eSIM y al revés (inverso exacto).

### 4.2 Webs (Distribuidores, Particulares, BackiPhone)

| Puesto | Teléfono | Formato | Color |
|---|---|---|---|
| 1 | El SKU exacto del pedido en la columna del canal (`ac.acepta(skuQueVale)`; grado y «100» incluidos) | el del pedido | el del pedido |
| 2 | B o C, **solo si el pedido es A o A100** (la batería va con la reparación); para A- no | el del pedido | el del pedido |

Nunca otro formato, otro color ni Ms en la web: eso lo negocia y lo pone una persona. Los pedidos web de N/A no existen hoy; cuando
existan, el puesto 1 (SKU exacto) ya los cubre.

### 4.3 Dentro de un puesto

1. **La caja:** Caja · Listos → Caja · Reparación = Caja · Pantalla rota → Caja · Pulir → Caja · Glass → Devolución = En revisión.
2. **El grado:** A- → A-100 → A / A+ → A100 / A+100 → N/A. En los puestos de B o C: B → C. (Ms solo existe en el puesto 9; dentro,
   el mismo orden sin el «Ms».)
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
   - `CANCELADA`: la fila se cerró sin envío (ya no está en ShippyPro) o tiene una marca;
   - `NO_ENCONTRADO`: el teléfono está en «Pendiente revisar si hay error de inventario» o en la «MV …» (lo movió una persona en
     NSYS porque no lo encuentra);
   - `NO_DISPONIBLE`: el teléfono ya no está en la empresa o su estado no es STOCK ni «de pedido» (vendido, baja, otra caja que no
     cuenta), o está puesto en otra fila.
   Un teléfono en «Pedidos realizados» con reserva es lo esperado cuando el bot ya lo descontó: **no** se suelta.
3. **Reservar** las filas que no tienen (orden §2, teléfono §4). Cada reserva apunta `RESERVADO` con `IMEI_NUEVO` = el reservado y
   `DETALLE` = el porqué («Puesto 1 · Caja · Listos · A-»). Si no hay teléfono, apunta **una sola vez** `SIN_STOCK` (columna
   `SIN_STOCK_EN`; se borra al reservar).

Todo en una transacción por fila, con el `SELECT … FOR UPDATE` de la fila como hoy (`SQL_FILA_MARCA_BLOQUEADA`), para no pisar a una
persona que esté poniendo el IMEI a la vez.

## 6. Bot de stock (vendidos) y devoluciones

- `GET /api/pedidos/vendidos` sirve también los IMEI **reservados** de filas vivas (fecha: `RESERVADA_EN`), **solo con el ajuste
  `pedidos.reserva.al-bot=true`** (por defecto `false`).
- **Se enciende cuando exista la lista de devoluciones del bot** (otra sesión). Antes, una reserva soltada después de que el bot la
  descontara dejaría el teléfono en «Pedidos realizados». Mientras está apagado, la reserva se ve en el ERP y guía a logística, pero
  no toca NSYS.
- **Contrato con las devoluciones** (para la otra sesión): una reserva soltada deja en `Pedido_fila_cambio` un apunte
  `RESERVA_SUSTITUIDA` o `RESERVA_QUITADA` con `IMEI_ANTERIOR` = el reservado. La condición 1 de las devoluciones («estuvo en una
  fila») cuenta también los `RESERVADO`. Lo que no se devuelve: `NO_ENCONTRADO` (el teléfono está en revisar a propósito).

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

- **`Reparto`** (nuevo, puro): `de(filas, stock, vale, aceptaciones, trabajos, ocupados)` → reservas y «sin stock», con §2-§4.
  `Recomendador` **no cambia** (lo toca la sesión de la celda); comparten `EstadoMovil`, `LoQueVale` y `Aceptacion`.
- **`Reservas`** (nuevo, `@Service`): la pasada de §5 con `FilasDAO`, `NsysDAO` y `TallerNsys`. La llama `LecturaFilas` al final de
  la vuelta.
- **`FilasDAO`**: leer las columnas nuevas en `FilaPedido`; `reservar`, `soltarReserva`, `apuntarSinStock`, `resolverReservaPuesta`;
  `ponerImei` con §7; `SQL_VENDIDOS` con la unión de los reservados cuando el ajuste está encendido.
- **`FilaVista`**: campos aditivos al final `imeiReservado` (nullable) y `reservadaEn` (nullable). OpenAPI y `docs/api_contract.md`.
- **`CambioImei`**: las constantes nuevas y su javadoc.

## 10. Web

- **Celda** (`CeldaImei.tsx`): sin IMEI y con reserva → el IMEI reservado con **menos opacidad**, sin texto. Al pulsar, se edita como
  hoy (vacía); el `title` dice «Reservado: pica este IMEI para confirmarlo o pon otro». Sin reserva → como hoy.
- **Contadores** (`filas.ts`, `PedidosPage.tsx`): «N sin IMEI» sigue igual (las reservadas cuentan); dos nuevos pulsables
  **«N reservadas»** y **«N sin stock»** (sin IMEI y sin reserva, editables y sin marca), con su filtro.
- **Historial** (`historial.ts`, `PanelFila.tsx`): los apuntes nuevos con **etiqueta de color** como el prototipo: Reservado (azul),
  Confirmado (verde, «Confirmado por {usuario}»), Picó otro (gris) y Vuelve a stock (morado) en `RESERVA_SUSTITUIDA`, Reserva quitada
  (ámbar; naranja si `CANCELADA`), Sin stock (naranja). La caja del `DETALLE` como píldora con su color (`CLASES_UBICACION`).
- **Ayuda** (`AyudaPedidos.tsx`): qué es una reserva y cómo confirmarla.
- Tipos regenerados (`openapi.json`, `schema.d.ts`), `fabrica.ts`.

## 11. Pruebas

- **Servidor:** `RepartoTest` con cada puesto de §4.1 y §4.2 (incluidos: Ms solo a España y el último; ID nunca; Devolución y sin
  caja como N/A; B/C web solo para A; caja antes que grado; antigüedad; prioridad de canal; varias unidades; un ocupado no entra);
  `ReservasTest` (confirmar, sustituir, soltar por cancelada / revisar / no disponible, «de pedido» no suelta, sin stock una vez);
  `FilasDAOTest` (columnas nuevas, `ponerImei` sobre reservada, vendidos con y sin el ajuste); `LecturaFilasTest` (la pasada al final;
  si falla, aviso y vuelta OK); `VistaFilasTest`; `OpenApiContractTest`.
- **Web:** `filas.test.ts` (contadores y filtros), `historial.test.ts` (los tipos nuevos), `CeldaImei` (tenue), `PedidosPage`.
- **Preprod:** migración, y una pasada real con el ajuste apagado; revisar con Imad las reservas que salen (E2E con datos sintéticos
  por API y limpieza, nunca en producción).

## 12. Despliegue

Migración aditiva; servidor y web juntos (la web vieja ignora los campos nuevos). `pedidos.reserva.al-bot=false` en el primer
despliegue: se mira unos días cómo reserva. Se enciende cuando estén las devoluciones del bot.

## Fuera de alcance

- La lista de devoluciones y el cambio del bot (otra sesión).
- Mover una reserva entre filas o recolocar reservas.
- «No lo encuentro» dentro del ERP (se hace en NSYS).
