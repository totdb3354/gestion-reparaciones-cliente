# Pedidos: reserva de IMEI — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que el ERP aparte un IMEI para cada fila viva de ShippyPro sin IMEI en cada lectura (cada 5 min), con los criterios de
Imad, y lo enseñe tenue en la celda, con su historial; el bot solo lo descuenta con un ajuste (apagado al principio).

**Architecture:** columnas nuevas en `Pedido_fila` (`IMEI_RESERVADO`, `RESERVADA_EN`, `SIN_STOCK_EN`); clase pura `Reparto` (criterios)
y servicio `Reservas` (la pasada: resolver, soltar, reservar) llamado al final de `LecturaFilas`; apuntes nuevos en
`Pedido_fila_cambio`; `FilaVista` con `imeiReservado`; la web pinta la reserva tenue, dos contadores y el historial con etiquetas.

**Tech Stack:** Java 17, Spring Boot 3.3, JdbcTemplate (tests con JdbcTemplate simulado, como `MarcaDAOTest`), JUnit 5 + AssertJ +
Mockito; React + TS, Vitest, Tailwind.

**Spec:** `docs/superpowers/specs/2026-11-24-pedidos-reserva-imei-design.md` (raíz). **Prototipo:**
`docs/superpowers/specs/assets/2026-11-24-pedidos-reserva-imei-prototipo.html` (el simulador lleva el algoritmo de §4 en JS: es la
referencia del orden).

## Global Constraints

- Ramas `dev_pedidos_reserva_imei` en servidor y web, en worktrees `C:/Users/dev/Documents/wt/servidor-pedidos-reserva-imei` y
  `wt/web-pedidos-reserva-imei`, desde `origin/main` (servidor `7d44ac8`, web `3559eb6`). No tocar otras ramas ni worktrees.
- **`Recomendador`, `EstadoUnidad`, `AvisoImei` no se tocan** (los cambia la sesión de la celda del IMEI). `FilaVista`: los campos
  nuevos van **al final** y con constructor de compatibilidad (como `salidaDesde`), para que el merge con la celda sea trivial.
- Commits sin Co-Authored-By. Nada de merge, push ni despliegue sin OK de Imad.
- Maven: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`;
  varias clases con `-Dtest=A,B`. Antes de dar el servidor por bueno: `mvn -q test` entero (incluye `OpenApiContractTest`).
- Web: vitest por carpeta (`npx vitest run src/modules/telefonos/pedidos`); la suite entera no termina en este PC.
- Textos de la interfaz en español, como el prototipo. Ajuste: `pedidos.reserva.al-bot` (boolean, por defecto `false`).
- Constantes de canal, caja y grado: las que ya hay (`Canal`, `EstadoMovil`, `Nsys_ubicacion` → «Taller · …»/«Listos» hoy; la sesión
  de la celda las renombra a «Caja · …»: **comparar por la LOCATION de NSYS** (`CAJA LISTOS`, `CAJA REP. OFICINA`,
  `C.R. Pantalla Rota`, `CAJA PULIR`, `CAJA GLASS`, `Funcionalidad pendiente`, vacía), no por el nombre del ERP).

---

### Task 1: Base de datos, lectura de las columnas y `FilaVista`

**Files:**
- Create: `sql/migracion-pedidos-reserva.sql`, `sql/migracion-pedidos-reserva-atras.sql`
- Modify: `sql/crear_bd.sql` (tabla `Pedido_fila` y comentario de `Pedido_fila_cambio.TIPO`)
- Modify: `pedidos/FilaPedido.java` (+ `imeiReservado`, `reservadaEn`, `sinStockEn` al final, con constructor de compatibilidad),
  `pedidos/FilasDAO.java` (`SQL_FILAS` y el mapeo), `pedidos/FilaVista.java` (+ `imeiReservado`, `reservadaEn` al final, nullable,
  constructor de compatibilidad), `pedidos/VistaFilas.java` (los pasa), `pedidos/CambioImei.java` (constantes `RESERVADO`,
  `RESERVA_CONFIRMADA`, `RESERVA_SUSTITUIDA`, `RESERVA_QUITADA`, `SIN_STOCK` y javadoc), `docs/api_contract.md`
- Test: `FilasDAOTest`, `VistaFilasTest`, `OpenApiContractTest`

Migración (una transacción no hace falta: `ALTER` aditivos):
```sql
ALTER TABLE Pedido_fila
  ADD COLUMN IMEI_RESERVADO VARCHAR(20) NULL COMMENT 'IMEI que el ERP aparta para la fila (reserva); NULL = sin reserva. Cuenta como sin IMEI',
  ADD COLUMN RESERVADA_EN DATETIME NULL COMMENT 'Cuando se reservo',
  ADD COLUMN SIN_STOCK_EN DATETIME NULL COMMENT 'Cuando se apunto «sin stock» (una vez hasta que reserve)',
  ADD KEY idx_fila_reservado (IMEI_RESERVADO);
```
Atrás: `DROP KEY` y `DROP COLUMN` de las tres.

- [ ] Tests rojos: `FilasDAOTest` lee las tres columnas; `VistaFilasTest` pasa `imeiReservado`/`reservadaEn` a la vista (y nulos sin
  reserva); `OpenApiContractTest` en verde con el contrato nuevo.
- [ ] Implementar; `mvn -q test -Dtest=FilasDAOTest,VistaFilasTest,OpenApiContractTest` verde.
- [ ] Commit `feat(pedidos): columnas de la reserva de IMEI y la vista con imeiReservado`.

### Task 2: `Reparto` (criterios, puro)

**Files:** Create `pedidos/Reparto.java`, Test `pedidos/RepartoTest.java`.

**Interfaces (Produces):**
```java
public final class Reparto {
    /** Una fila que espera teléfono. */
    public record Fila(int id, Canal canal, String pais, LocalDate fechaPedido, Aceptacion aceptacion) {}
    /** Lo que sale: la reserva de una fila, con su puesto y el porqué («Puesto 1 · Caja · Listos · A-»), o sin stock (imei null). */
    public record Resultado(int idFila, String imei, Integer puesto, String porque) {}
    /** Puro. filas: las que esperan (en cualquier orden: aquí se ordenan por §2). stock: móviles en la empresa. ocupados: claves de
     *  cruce (NsysDAO.claveCruce) de IMEI puestos o reservados en otras filas. Devuelve un Resultado por fila, en el orden de reparto. */
    public static List<Resultado> de(List<Fila> filas, List<MovilNsys> stock, Function<MovilNsys, LoQueVale.Valor> vale,
                                     Map<String, List<TrabajoAbierto>> trabajos, Set<String> ocupados) { ... }
    static Integer puestoAmazon(Fila f, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo);
    static Integer puestoWeb(Fila f, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo);
}
```
Reglas: spec §2-§4 (tablas 4.1 y 4.2, orden 4.3). Elegibles: `m.estado().conGrado(v.grado()) == EstadoMovil.STOCK` (como
`Recomendador`), no en `ocupados`, mismo `modeloMaestro` y `capacidad` que `aceptacion.pide()`; nunca ID; grado que cuenta = N/A si
`m.location()` es `Funcionalidad pendiente` o vacía; con trabajo abierto solo puesto 1 y si `aceptacion.acepta(v.sku())`.
«Encaja tal cual» Amazon = `ac.acepta(v.sku()) || ac.aceptaColorYGrado(v.color(), v.grade())` sin Ms. Web exacto = `ac.acepta(v.sku())`.
Color del pedido = `ac.pide().color()`; grado del pedido = `ac.pide().grado()`. Orden de cajas por `m.location()`:
`CAJA LISTOS`=1, `CAJA REP. OFICINA`=`C.R. Pantalla Rota`=2, `CAJA PULIR`=3, `CAJA GLASS`=4, `Funcionalidad pendiente`=vacía=5.

Tests (uno por regla; datos inventados, un iPhone 13 128):
- [ ] puesto 1 Amazon (A-, A-100, A, A100, N/A del mismo color y formato) y orden de grado dentro de Listos;
- [ ] caja antes que grado (A en Listos gana a A- en Glass); antigüedad desempata;
- [ ] puesto 2 (B de otro color) antes que puesto 3 (eSIM mismo color); B antes que C;
- [ ] puestos 5-8 y el inverso exacto para pedido eSIM;
- [ ] Ms: solo si `pais = "ES"`, y después de todos (incluso de un A de otro color, puesto 7); nunca en la web;
- [ ] ID nunca; F/FD/RMA nunca (no son STOCK); Devolución y sin caja = N/A (no valen como exacto en la web);
- [ ] web: exacto; B/C mismo color y formato solo con pedido A o A100; nada de otro color/formato;
- [ ] prioridad: Amazon antes que web aunque la web sea más antigua; Distribuidores → Particulares → BackiPhone; fecha; id;
- [ ] varias filas del mismo pedido: cada una el siguiente; sin stock = Resultado con imei null;
- [ ] `ocupados` y trabajo abierto (solo puesto 1 con SKU aceptado);
- [ ] el caso del simulador del prototipo (12 teléfonos, 7 pedidos) da lo mismo que el prototipo.
- [ ] `mvn -q test -Dtest=RepartoTest` verde. Commit `feat(pedidos): reparto de reservas de IMEI con los criterios de Amazon y web`.

### Task 3: `Reservas` (la pasada) y el DAO

**Files:** Create `pedidos/Reservas.java`, Test `pedidos/ReservasTest.java`; Modify `pedidos/FilasDAO.java` (+ test en
`FilasDAOTest` o `ReservaDAOTest` nuevo), `pedidos/LecturaFilas.java` (+ `LecturaFilasTest`).

DAO (cada método bloquea la fila con `FOR UPDATE` y comprueba que sigue igual antes de escribir; apunta en `Pedido_fila_cambio`):
- `List<FilaPedido> conReservaOEsperando()` — filas ShippyPro vivas (`CERRADA_EN IS NULL`) sin marca, sin IMEI, y además las que
  tienen `IMEI_RESERVADO` (estén como estén).
- `boolean reservar(int idFila, String imei, String porque, LocalDateTime ahora)` — solo si sigue sin IMEI, sin marca, viva y sin
  reserva; `IMEI_RESERVADO`, `RESERVADA_EN`, `SIN_STOCK_EN = NULL`; apunte `RESERVADO` (IMEI_NUEVO, DETALLE).
- `void soltarReserva(int idFila, String motivo, LocalDateTime ahora)` — borra las dos columnas; apunte `RESERVA_QUITADA`
  (IMEI_ANTERIOR, DETALLE = `CANCELADA` | `NO_ENCONTRADO` | `NO_DISPONIBLE`).
- `void resolverReservaPuesta(int idFila, Integer idUsu, LocalDateTime ahora)` — con IMEI y reserva: `RESERVA_CONFIRMADA` si son
  iguales (por clave de cruce), `RESERVA_SUSTITUIDA` (IMEI_ANTERIOR reservado, IMEI_NUEVO puesto) si no; borra la reserva.
- `void apuntarSinStock(int idFila, LocalDateTime ahora)` — solo si `SIN_STOCK_EN IS NULL`; apunte `SIN_STOCK`.

`Reservas.pasada(LocalDateTime ahora)` — spec §5 en orden: resolver puestas → soltar (cerrada sin envío o con marca → `CANCELADA`;
móvil en LOCATION `Pendiente revisar si hay error de inventario` o `MV Pendiente revisar si hay error de inventario` →
`NO_ENCONTRADO`; no en la empresa, estado distinto de STOCK y de DE_PEDIDO, o puesto en otra fila → `NO_DISPONIBLE`) → reservar con
`Reparto.de` (aceptaciones con `AceptaSku` de los SKU de las filas; ocupados = puestos de `VistaFilas` (mismo criterio, 60 días) +
reservados que quedan) → `apuntarSinStock` de las que salen sin IMEI. Devuelve un resumen (reservadas, soltadas, sin stock).
`LecturaFilas.vuelta()`: al final, antes de `terminarLectura`, en try/catch como las salidas: si falla, `avisos.add(AVISO_RESERVAS)`.

- [ ] Tests rojos de cada caso de §5 (`ReservasTest` con DAO y NSYS simulados), del DAO (SQL y apuntes) y de `LecturaFilasTest`
  (se llama; si lanza, aviso y vuelta OK).
- [ ] Implementar; tests verdes. Commit `feat(pedidos): pasada de reservas al final de cada lectura de ShippyPro`.

### Task 4: Poner el IMEI sobre una reserva y la lista de vendidos

**Files:** Modify `pedidos/FilasDAO.java` (`ponerImei`, `SQL_VENDIDOS`), `pedidos/VendidosController.java` (ajuste),
`application.properties` (`pedidos.reserva.al-bot=false`); Test `FilasDAOTest`/`VendidosTest`.

- `ponerImei`: si la fila tiene reserva, en la misma transacción `resolverReservaPuesta` con el usuario (spec §7), además del apunte
  `IMEI` de siempre.
- Vendidos: con el ajuste encendido, unión de las filas ShippyPro vivas sin IMEI, sin marca y con `IMEI_RESERVADO` cuya
  `RESERVADA_EN` cae en la ventana (IMEI = el reservado, fecha = `RESERVADA_EN`); el dedup por IMEI de siempre. Apagado: igual que hoy.
- [ ] Tests rojos (confirmar, sustituir, sin reserva igual que hoy; vendidos con y sin ajuste). Verde. `mvn -q test` entero verde.
- [ ] Commit `feat(pedidos): confirmar o sustituir la reserva al poner el IMEI; reservados al bot con pedidos.reserva.al-bot`.

### Task 5: Web — tipos, celda y contadores

**Files:** `api/openapi.json` y `schema.d.ts` regenerados con el servidor de la rama; `fabrica.ts` (+ `imeiReservado: null`,
`reservadaEn: null`); `CeldaImei.tsx`; `filas.ts` (`recuento` con `reservadas` y `sinStock`, `Contador` + `'reservadas' | 'sinStock'`,
`filtrosDeContador`, filtro `soloReservadas` / `soloSinStock`); `PedidosPage.tsx` (los dos contadores pulsables). Tests
`filas.test.ts`, `CeldaImei` (si no hay test propio, en `TablaFilas.test.tsx`), `PedidosPage.test.tsx`.

- Celda sin IMEI con `imeiReservado`: el IMEI con `opacity-45` y `title` «Reservado: pica este IMEI para confirmarlo o pon otro».
- «sin stock» = `faltaImei(f) && !f.imeiReservado && !f.marca`; «reservadas» = `faltaImei(f) && !!f.imeiReservado`.
- [ ] Tests rojos → verdes (`npx vitest run src/modules/telefonos/pedidos`). Commit `feat(pedidos): la reserva tenue en la celda y
  los contadores de reservadas y sin stock`.

### Task 6: Web — historial con etiquetas y ayuda

**Files:** `historial.ts` (+ `tono` y `caja` en las entradas de los tipos nuevos), `PanelFila.tsx` (etiqueta de color y píldora de
caja con `CLASES_UBICACION`), `AyudaPedidos.tsx`; tests `historial.test.ts`, `PedidosPanel.test.tsx`, `PedidosAyuda.test.tsx`.

Textos (como el prototipo v5): `RESERVADO` → «Reservado» azul + `{IMEI} · {DETALLE}`; `RESERVA_CONFIRMADA` → «Confirmado» verde,
«por {usuario} · {IMEI}»; `RESERVA_SUSTITUIDA` → dos entradas: «Picó otro» gris «{nuevo} en lugar del reservado {anterior}» y
«Vuelve a stock» morado «{anterior}»; `RESERVA_QUITADA` → «Reserva quitada» (ámbar; «Cancelado» naranja si `CANCELADA`) con el
motivo en palabras; `SIN_STOCK` → «Sin stock» naranja «Ningún teléfono cumple los criterios».
- [ ] Tests rojos → verdes. Commit `feat(pedidos): historial de la reserva con etiquetas de color y ayuda`.

### Task 7: Versión, documentación y E2E

- `package.json` y `CHANGELOG.md` (número al sacarla, coordinado con las otras sesiones); `docs/modulos/pedidos.md` (raíz) con la
  reserva; E2E `tests/e2e/pedidos-reserva.spec.ts` para preprod (datos sintéticos por API y limpieza; con el ajuste apagado).
- [ ] Commit. Revisión final de toda la rama. **Parar aquí: merge, preprod y producción con Imad.**
