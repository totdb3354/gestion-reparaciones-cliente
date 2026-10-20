# 0.9.18.2 — Un móvil vendido o de baja no está «En revisión» — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** que la caja vacía que NSYS pone al vender o dar de baja un móvil deje de leerse como «En revisión», y que una
devolución espere desde el día que vuelve.

**Architecture:** el arreglo está en el espejo de NSYS del servidor: `Comparador` (foto y eventos de cada exportación),
`Revision` (desde cuándo espera un móvil) y la ficha de `StockTelefonosController`. Dos SQL limpian lo que ya está guardado
en `Nsys_evento` y `Nsys_movil`. La web no cambia: ya trata una ubicación nula y un «Desde» nulo.

**Tech Stack:** Java 17, Spring Boot 3.3, JUnit 5 + Mockito, MariaDB 11.8 (`utf8mb4_uca1400_ai_ci`).

**Spec:** `docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md` (repo raíz, commit `20334c6`).

## Global Constraints

- **Solo servidor + datos:** sin cambios de esquema ni de web. La API no cambia de forma (mismos campos), así que
  `openapi.json` y `schema.d.ts` no se tocan.
- **Rama `hotfix/0.9.18.2`** sacada del tag `v0.9.18` (servidor `728f2f9`), en el worktree
  `C:/Users/dev/Documents/wt/servidor-09182`. El clon principal del servidor no se toca.
- **Status vivos:** `Available`, `InProcess`, `Repair` (`EstadoMovil.VIVOS`). Todo lo demás (`Released`, `Removed`, `RMA`,
  `ReturnedToSupplier`, null…) no es vivo.
- **Caja vacía** = `Normalizacion.caja(location)` vacío (`LOCATION = ''` en la base; la columna es `NOT NULL DEFAULT ''`).
- **Commits:** sin línea `Co-Authored-By`; mensajes en español sin tildes, como los anteriores (`fix: …`, `test: …`).
- **Merge, push y tags, solo con el OK del usuario.** Antes de un merge o un push, `git fetch` y comparar con `origin`:
  hay otras sesiones trabajando.
- **Claude no hace SSH.** Prepara los bloques y el usuario los pega. Cada bloque empieza con `hostname` y la comprobación
  de `backup-erp.sh` (solo existe en producción).
- **Los SQL no teclean tildes:** «En revisión» y «Devolución» salen de `Nsys_ubicacion`, y se lanzan con `< fichero`.
- **Repos públicos:** ni IMEIs reales, ni nombres de máquinas, ni recuentos reales en el repo. Los guiones y los registros
  van a `C:/Users/dev/Documents/Apuntes`.

**Maven en este PC** (Git Bash), delante de cada orden `mvn`:

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17
export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
```

---

### Task 1: El espejo no lee la caja vacía de un móvil cerrado

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/nsys/EstadoMovil.java`
- Modify: `src/main/java/com/reparaciones/servidor/nsys/Comparador.java` (`construir`, `cambios` y dos ayudas nuevas)
- Test: `src/test/java/com/reparaciones/servidor/nsys/ComparadorTest.java`

**Interfaces:**
- Produces: `EstadoMovil.vivo(String status): boolean` (null → false), que usa la Task 2.
- Produces: `Comparador.cerradoSinCaja(String status, String location): boolean`, de paquete.

- [ ] **Step 1: Worktree y rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch -q origin && git log --oneline -1 v0.9.18
git worktree add -b hotfix/0.9.18.2 /c/Users/dev/Documents/wt/servidor-09182 v0.9.18
cd /c/Users/dev/Documents/wt/servidor-09182 && git log --oneline -1
```

Expected: `728f2f9 merge: el historico de NSYS de 19:00 a 7:00 y con 5 s entre ficheros` las dos veces.

Línea base (con el prefijo de Maven de arriba):

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 1108 tests. Si la cifra es otra, apuntarla: es la base de la Task 4.

- [ ] **Step 2: Los tests que fallan**

En `ComparadorTest.java`, detrás de `ventaVentaDeshechaYBajaPorReentrada()` (hacia la línea 145), añadir:

```java
    // 0.9.18.2: NSYS vacía la caja al cerrar un móvil (Released, Removed…). Con un Status que no es vivo, la caja vacía
    // no es «En revisión» ni un movimiento.

    @Test void ventaConLaCajaVaciadaSoloApuntaLaVentaYGuardaLaUltimaCaja() {
        FilaItems antes = fila("350000000000017", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems vendido = fila("350000000000017", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = normal(previo(antes), vendido);

        assertEquals(List.of(new EventoNsys("350000000000017", "BT-9001", HOY, false, TipoEvento.VENTA, "Available",
                "Released", null)), r.eventos());
        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.VENDIDO, guardado.estado());
        assertNull(guardado.ubicacion());
        assertEquals("Listos", guardado.ubicacionUltima());
    }

    @Test void bajaConLaCajaVaciadaSoloApuntaElEstado() {
        FilaItems antes = fila("350000000000018", "BT-9001", "A", "InProcess", "CAJA GLASS");
        FilaItems quitado = fila("350000000000018", "BT-9001", "A", "Removed", "");
        ResultadoComparacion r = normal(previo(antes), quitado);

        assertEquals(List.of(TipoEvento.ESTADO), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals("Removed", r.eventos().get(0).a());
        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.BAJA, guardado.estado());
        assertNull(guardado.ubicacion());
        assertEquals("Taller · Glass", guardado.ubicacionUltima());
    }

    @Test void unVendidoSinCajaComoLoDejaLaLimpiezaNoSeVuelveAGuardar() {
        FilaItems vendido = fila("350000000000019", "BT-9001", "A", "Released", "");
        MovilNsys m = previo(vendido).get(vendido.clave());
        MovilNsys limpio = new MovilNsys(m.imeiSn(), m.batch(), m.goods(), m.grade(), m.status(), m.location(),
                m.supplier(), m.receivingDate(), m.soldDate(), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(),
                m.color(), m.gradoDetalle(), m.esEsim(), null, "Listos", m.estado(), m.vistoPrimero(), m.cambiado());
        ResultadoComparacion r = normal(new HashMap<>(Map.of(limpio.clave(), limpio)), vendido);

        assertTrue(r.aGuardar().isEmpty(), "sin ubicación y con su última caja: el mismo contenido");
        assertTrue(r.eventos().isEmpty());
    }

    @Test void ventaDeshechaAUnaCajaSaleSinDesde() {
        FilaItems vendido = fila("350000000000020", "BT-9001", "A", "Released", "");
        FilaItems vuelta = fila("350000000000020", "BT-9001", "A", "InProcess", "CAJA LISTOS");
        ResultadoComparacion r = normal(previo(vendido), vuelta);

        assertEquals(List.of(
                new EventoNsys("350000000000020", "BT-9001", HOY, false, TipoEvento.UBICACION, null, "Listos", "CAJA LISTOS"),
                new EventoNsys("350000000000020", "BT-9001", HOY, false, TipoEvento.VENTA_DESHECHA, "Released", "InProcess", null)),
                r.eventos());
    }

    @Test void ventaDeshechaAPedidosRealizadosConservaLaCajaDeAntesDeLaVenta() {
        FilaItems listo = fila("350000000000021", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems vendido = fila("350000000000021", "BT-9001", "A", "Released", "");
        FilaItems dePedido = fila("350000000000021", "BT-9001", "A", "InProcess", "Pedidos realizados");
        MovilNsys trasLaVenta = normal(previo(listo), vendido).aGuardar().get(0);
        ResultadoComparacion r = normal(new HashMap<>(Map.of(trasLaVenta.clave(), trasLaVenta)), dePedido);

        MovilNsys guardado = r.aGuardar().get(0);
        assertEquals(EstadoMovil.DE_PEDIDO, guardado.estado());
        assertEquals("Listos", guardado.ubicacionUltima());
        assertEquals(TipoEvento.UBICACION, r.eventos().get(0).tipo());
        assertNull(r.eventos().get(0).de(), "de vendido no sale de ninguna caja");
    }

    @Test void ventaDeshechaSinCajaQuedaEnRevisionSinEventoDeUbicacion() {
        FilaItems vendido = fila("350000000000022", "BT-9001", "A", "Released", "");
        FilaItems vuelta = fila("350000000000022", "BT-9001", "A", "InProcess", "");
        ResultadoComparacion r = normal(previo(vendido), vuelta);

        assertEquals(List.of(TipoEvento.VENTA_DESHECHA), r.eventos().stream().map(EventoNsys::tipo).toList());
        assertEquals("En revisión", r.aGuardar().get(0).ubicacion());
    }

    @Test void filaNuevaYaVendidaSinCajaEntraSinUbicacion() {
        FilaItems otro = fila("350000000000023", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems nuevo = fila("350000000000024", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = normal(previo(otro), otro, nuevo);

        assertEquals(new EventoNsys("350000000000024", "BT-9001", HOY, false, TipoEvento.ENTRADA, null, null, ""),
                r.eventos().get(0));
        assertNull(r.aGuardar().get(0).ubicacion());
    }

    @Test void unMovilVivoQueSeQuedaSinCajaSigueYendoAEnRevision() {
        FilaItems antes = fila("350000000000025", "BT-9001", "A", "Available", "CAJA LISTOS");
        FilaItems ahora = fila("350000000000025", "BT-9001", "A", "Available", "");
        ResultadoComparacion r = normal(previo(antes), ahora);

        assertEquals(new EventoNsys("350000000000025", "BT-9001", HOY, false, TipoEvento.UBICACION, "Listos",
                "En revisión", ""), r.eventos().get(0));
        assertEquals("En revisión", r.aGuardar().get(0).ubicacion());
    }

    @Test void unaBajaQuePasaADesguaceSigueApuntandoLaCaja() {
        FilaItems quitado = fila("350000000000026", "BT-9001", "A", "Removed", "");
        FilaItems desguace = fila("350000000000026", "BT-9001", "A", "Removed", "Desguace");
        ResultadoComparacion r = normal(previo(quitado), desguace);

        assertEquals(List.of(new EventoNsys("350000000000026", "BT-9001", HOY, false, TipoEvento.UBICACION, null, null,
                "Desguace")), r.eventos());
    }

    @Test void elHistoricoTampocoApuntaLaCajaVaciadaDeUnaVenta() {
        FilaItems antes = fila("350000000000027", "BT-9001", "A", "Available", "Pedidos realizados");
        FilaItems vendido = fila("350000000000027", "BT-9001", "A", "Released", "");
        ResultadoComparacion r = Comparador.comparar(previo(antes), List.of(vendido), HOY, Comparador.Modo.HISTORICO, T);

        assertEquals(List.of(TipoEvento.VENTA), r.eventos().stream().map(EventoNsys::tipo).toList());
    }
```

El histórico usa el mismo `Comparador.comparar` con `Modo.HISTORICO` (`HistoricoNsys.procesar`), así que su test va
aquí y no en `HistoricoNsysTest`.

- [ ] **Step 3: Comprobar que fallan**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test -Dtest=ComparadorTest 2>&1 | grep -E "Tests run|FAIL|ComparadorTest\." | head -20
```

Expected: fallan 8 de los 10 nuevos. `ventaDeshechaSinCajaQuedaEnRevisionSinEventoDeUbicacion` y
`unMovilVivoQueSeQuedaSinCajaSigueYendoAEnRevision` pasan ya: son de regresión (lo que no debe cambiar). Los 14 de antes
pasan.

- [ ] **Step 4: `EstadoMovil.vivo`**

En `EstadoMovil.java`, detrás de `VIVOS`:

```java
    /** ¿Es un Status de móvil en la empresa? Sin Status, no (como en {@link #de}). */
    public static boolean vivo(String status) {
        return status != null && VIVOS.contains(status);
    }
```

(`Set.of(...).contains(null)` lanza `NullPointerException`, por eso la ayuda.)

- [ ] **Step 5: `Comparador`**

En `construir`, cambiar la línea de la ubicación:

```java
        // NSYS vacía la caja al cerrar el móvil (Released, Removed…): sin caja, un vendido o una baja no está «En
        // revisión»; se queda sin ubicación y con la última (spec 0.9.18.2 §2).
        String ubicacion = caja == null || cerradoSinCaja(f.status(), f.location()) ? null : caja.ubicacion();
```

En `cambios`, sustituir el primer `if` (el de la ubicación, que hoy es
`if (!Objects.equals(antes.location(), ahora.location())) eventos.add(evento(f, fecha, false, TipoEvento.UBICACION, antes.ubicacion() != null ? antes.ubicacion() : antes.location(), ahora.ubicacion(), ahora.location()));`,
en tres líneas) por:

```java
        // Al cerrar el móvil NSYS vacía la caja: no es un movimiento (spec 0.9.18.2 §2).
        if (!Objects.equals(antes.location(), ahora.location()) && !cerradoSinCaja(ahora.status(), ahora.location()))
            eventos.add(evento(f, fecha, false, TipoEvento.UBICACION, desde(antes), ahora.ubicacion(), ahora.location()));
```

Y detrás de `cambios`, las dos ayudas:

```java
    /** Un móvil cerrado (Status que no es vivo) con la caja vacía: NSYS se la vacía al venderlo o darlo de baja (spec
     *  0.9.18.2 §2). Un móvil vivo sin caja, en cambio, está «En revisión». */
    static boolean cerradoSinCaja(String status, String location) {
        return !EstadoMovil.vivo(status) && Normalizacion.caja(location).isEmpty();
    }

    /** De dónde sale el móvil en un cambio de caja: su ubicación del ERP o, si no tiene, la caja de NSYS. Un móvil
     *  cerrado sin caja no sale de ninguna: sin «Desde», así su vuelta no cuenta como revisión (spec 0.9.18.2 §2). */
    private static String desde(MovilNsys antes) {
        if (antes.ubicacion() != null) return antes.ubicacion();
        return cerradoSinCaja(antes.status(), antes.location()) ? null : antes.location();
    }
```

La ayuda `ultima` de `construir` no cambia: con la ubicación nula ya se queda con la de antes.

- [ ] **Step 6: Comprobar que pasan**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test -Dtest='ComparadorTest,HistoricoNsysTest,ImportadorNsysTest' 2>&1 | grep -E "Tests run|FAIL|BUILD" | tail -5
```

Expected: todos en verde (24 de `ComparadorTest`).

- [ ] **Step 7: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182
git add src/main/java/com/reparaciones/servidor/nsys/EstadoMovil.java src/main/java/com/reparaciones/servidor/nsys/Comparador.java src/test/java/com/reparaciones/servidor/nsys/ComparadorTest.java
git commit -m "fix: la caja vacia de un movil vendido o de baja no es en revision ni un cambio de caja (0.9.18.2)"
```

---

### Task 2: Una devolución espera desde el día que vuelve

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/nsys/Revision.java` (`pendienteDesde` y una ayuda nueva)
- Test: `src/test/java/com/reparaciones/servidor/nsys/RevisionTest.java`

**Interfaces:**
- Consumes: `EstadoMovil.vivo(String)` (Task 1).
- Produces: `Revision.pendienteDesde(MovilNsys, List<EventoNsys>)` con la misma firma; cambia el resultado tras una vuelta.

- [ ] **Step 1: Los tests que fallan**

En `RevisionTest.java`, añadir `import java.util.ArrayList;` y, detrás de `fueraDeRevisionNoHayPendienteYSinEventosNoHayNada()`:

```java
    // 0.9.18.2: la vuelta de un vendido o de una baja (venta deshecha, o de un Status que no es vivo a uno vivo) empieza
    // otra espera. Si vuelve sin caja, la caja de NSYS no cambia y no hay evento de ubicación.

    private static EventoNsys estado(LocalDateTime fecha, TipoEvento tipo, String de, String a) {
        return new EventoNsys("350000000000001", "BT-9001", fecha, false, tipo, de, a, null);
    }

    @Test void devolucionQueVuelveSinCajaYPasaAFuncionalidadPendienteEsperaDesdeLaVuelta() {
        List<EventoNsys> e = new ArrayList<>(List.of(
                ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                estado(t(14, 10), TipoEvento.VENTA_DESHECHA, "Released", "InProcess"),
                ubicacion("BT-9001", t(15, 10), "En revisión", "Devolución", "Funcionalidad pendiente")));
        assertEquals(LocalDate.of(2026, 10, 14), Revision.pendienteDesde(movil("Devolución", LocalDate.of(2026, 10, 1)), e));

        e.add(ubicacion("BT-9001", t(16, 10), "Devolución", "Listos", "CAJA LISTOS"));
        assertEquals(new Revision.Ultima(t(16, 10), true), Revision.ultima(e, "BT-9001"));
    }

    @Test void vueltaDirectaADevolucionConElDesdeFalsoDeLosEventosAntiguosEsperaDesdeLaVuelta() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                ubicacion("BT-9001", t(14, 10), "En revisión", "Devolución", "Funcionalidad pendiente"),
                estado(t(14, 10), TipoEvento.VENTA_DESHECHA, "Released", "InProcess"));
        assertEquals(LocalDate.of(2026, 10, 14), Revision.pendienteDesde(movil("Devolución", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void bajaDeshechaSinCajaEsperaDesdeLaVuelta() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "En revisión", "Listos", "CAJA LISTOS"),
                estado(t(10, 10), TipoEvento.ESTADO, "InProcess", "Removed"),
                estado(t(13, 10), TipoEvento.ESTADO, "Removed", "InProcess"));
        assertEquals(LocalDate.of(2026, 10, 13), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void trasLaVueltaUnaCajaYOtraVezSinCajaEsperaDesdeLaSegundaVez() {
        List<EventoNsys> e = List.of(estado(t(10, 10), TipoEvento.VENTA, "Available", "Released"),
                ubicacion("BT-9001", t(12, 10), null, "Listos", "CAJA LISTOS"),
                estado(t(12, 10), TipoEvento.VENTA_DESHECHA, "Released", "Available"),
                ubicacion("BT-9001", t(15, 10), "Listos", "En revisión", ""));
        assertEquals(LocalDate.of(2026, 10, 15), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }

    @Test void unCambioEntreStatusVivosNoEsUnaVuelta() {
        List<EventoNsys> e = List.of(ubicacion("BT-9001", t(8, 10), "Listos", "En revisión", ""),
                estado(t(12, 10), TipoEvento.ESTADO, "InProcess", "Available"));
        assertEquals(LocalDate.of(2026, 10, 8), Revision.pendienteDesde(movil("En revisión", LocalDate.of(2026, 10, 1)), e));
    }
```

Los eventos de una misma exportación van en el orden de `Comparador.cambios` (primero `UBICACION`, después el de estado),
que es el de `NsysDAO.eventos` (`ORDER BY FECHA, ID_EVENTO`).

- [ ] **Step 2: Comprobar que fallan**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test -Dtest=RevisionTest 2>&1 | grep -E "Tests run|FAIL|RevisionTest\." | head -12
```

Expected: fallan los 3 primeros: dan `2026-10-01`, la recepción del lote. Los 2 últimos pasan ya: son de regresión.

- [ ] **Step 3: `Revision.pendienteDesde`**

Sustituir el método y su comentario por:

```java
    /** Si el móvil está ahora pendiente de revisar («En revisión» o «Devolución»), desde cuándo: el principio del periodo
     *  pendiente actual del lote, el último evento que lo llevó a una ubicación pendiente desde una que no lo es (o su
     *  entrada); un paso entre las dos pendientes no lo cambia. La vuelta del lote desde vendido o de baja también empieza
     *  un periodo: si vuelve sin caja, la caja de NSYS no cambia y no hay evento de ubicación (0.9.18.2). Si no lo hay, la
     *  recepción del lote. null si no está pendiente o no hay fecha. */
    public static LocalDate pendienteDesde(MovilNsys m, List<EventoNsys> eventos) {
        if (!pendiente(m.ubicacion())) return null;
        LocalDate desde = null;
        for (EventoNsys e : eventos) {
            if (!Objects.equals(e.batch(), m.batch())) continue;
            if (esVuelta(e)) desde = e.fecha().toLocalDate();
            else if (llevaUbicacion(e)) {
                if (!pendiente(e.a())) desde = null;
                else if (e.tipo() == TipoEvento.ENTRADA || !pendiente(e.de())) desde = e.fecha().toLocalDate();
            }
        }
        return desde != null ? desde : m.receivingDate();
    }

    /** La vuelta de un lote a la empresa: una venta deshecha, o un cambio de un Status que no es vivo a uno vivo (una
     *  baja deshecha). */
    private static boolean esVuelta(EventoNsys e) {
        return e.tipo() == TipoEvento.VENTA_DESHECHA
                || e.tipo() == TipoEvento.ESTADO && !EstadoMovil.vivo(e.de()) && EstadoMovil.vivo(e.a());
    }
```

`tipos` y `ultima` no cambian: la regla del 19/10 sigue igual (revisión = de una ubicación pendiente a una caja de verdad).

- [ ] **Step 4: Comprobar que pasan**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test -Dtest='RevisionTest,StockTelefonosControllerTest' 2>&1 | grep -E "Tests run|FAIL|BUILD" | tail -5
```

Expected: todos en verde (20 de `RevisionTest`).

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182
git add src/main/java/com/reparaciones/servidor/nsys/Revision.java src/test/java/com/reparaciones/servidor/nsys/RevisionTest.java
git commit -m "fix: una devolucion espera desde el dia que vuelve de vendido o de baja (0.9.18.2)"
```

---

### Task 3: La ficha solo dice «Pendiente de revisar» con el móvil en la empresa

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java:117` (`ficha`)
- Test: `src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java`

**Interfaces:**
- Consumes: `Revision.pendienteDesde` (Task 2). La respuesta (`FichaMovil`) no cambia de campos.

- [ ] **Step 1: El test que falla**

En `StockTelefonosControllerTest.java`, detrás de `fichaDeUnMovilEnRevisionSinEventosPendienteDesdeLaRecepcion()`:

```java
    @Test void laFichaDeUnVendidoNoEstaPendienteDeRevisarAunqueConserveUnaCajaPendiente() {
        // Una venta antigua, de cuando NSYS aún no vaciaba la caja (0.9.18.2).
        MovilNsys vendido = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Released",
                "Funcionalidad pendiente", "Proveedor A", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 5, 2),
                "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, "Devolución", "Devolución", EstadoMovil.VENDIDO,
                AHORA.minusDays(15), AHORA.minusDays(1));
        when(dao.porImei("350000000000001")).thenReturn(List.of(vendido));
        when(dao.eventos("350000000000001")).thenReturn(List.of(new EventoNsys("350000000000001", "BT-9001",
                AHORA.minusDays(5), false, TipoEvento.UBICACION, "Listos", "Devolución", "Funcionalidad pendiente")));
        JsonNode ficha = json(api.ficha("350000000000001"));
        assertTrue(ficha.get("pendienteRevisionDesde").isNull());
        assertEquals("2024-05-02", ficha.get("fechaVenta").asText());
    }
```

- [ ] **Step 2: Comprobar que falla**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test -Dtest=StockTelefonosControllerTest 2>&1 | grep -E "Tests run|FAIL" | head -5
```

Expected: falla el nuevo (`pendienteRevisionDesde` = `2026-10-11`).

- [ ] **Step 3: El cambio**

En `ficha`, la última línea del `new FichaMovil(...)`:

```java
                enEmpresa ? Revision.pendienteDesde(m, eventos) : null);
```

Y el comentario de encima de `Revision.Ultima revisado`:

```java
        // 0.9.18 (spec §5.2): la última revisión del lote y, si está en «En revisión», desde cuándo. Un vendido o una baja
        // no espera revisión (0.9.18.2).
```

- [ ] **Step 4: Comprobar que pasa**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test -Dtest='StockTelefonosControllerTest,RolesStockTelefonosTest' 2>&1 | grep -E "Tests run|FAIL|BUILD" | tail -5
```

Expected: todos en verde.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182
git add src/main/java/com/reparaciones/servidor/nsys/StockTelefonosController.java src/test/java/com/reparaciones/servidor/nsys/StockTelefonosControllerTest.java
git commit -m "fix: la ficha de un vendido o de una baja no dice pendiente de revisar (0.9.18.2)"
```

---

### Task 4: Los SQL de recuento y limpieza, la suite y las novedades

**Files:**
- Create: `sql/recuento-nsys-vendido-en-revision.sql` (solo lee)
- Create: `sql/limpieza-nsys-vendido-en-revision.sql` (escribe, en una transacción)
- Modify (repo raíz, al cerrar): `docs/novedades/NOVEDADES-v0.9.18.md`

**Interfaces:**
- Consumes: el comportamiento del código nuevo (Tasks 1-3). El paso 3 de la limpieza deja `Nsys_movil` igual que lo
  calcula `Comparador.construir`, para que la importación siguiente no vuelva a guardar esas filas.

No hay MariaDB en este PC: los SQL se prueban en preprod (Task 5). Lo que se puede comprobar aquí es que las selecciones
del recuento y de la limpieza son las mismas (Step 3).

- [ ] **Step 1: `sql/recuento-nsys-vendido-en-revision.sql`**

```sql
-- 0.9.18.2: recuento de lo que arregla limpieza-nsys-vendido-en-revision.sql. Solo lee: las tablas temporales son de
-- la sesión. Se pasa antes y después de la limpieza. Spec en el repo raíz:
-- docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md, §3.
-- NSYS vacía la caja al vender o dar de baja un móvil, y el ERP lo leía como «sin caja»: un «Pasa a En revisión» falso
-- en el rastro y la ubicación «En revisión» en la ficha de un vendido.
-- Los nombres con tilde salen de Nsys_ubicacion, para no teclearlos por ssh.
SET @sin_caja = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = '');
SET @devolucion = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = 'Funcionalidad pendiente');
SELECT @sin_caja AS sin_caja, HEX(@sin_caja) AS hex, @devolucion AS devolucion;
SELECT COUNT(*) AS eventos FROM Nsys_evento;

-- 1) «→ En revisión» de una caja vaciada con el lote cerrado. El estado del lote en ese momento es su último evento de
--    estado hasta esa hora; el del esqueleto (solo el día) cuenta al final de su día.
CREATE TEMPORARY TABLE tmp_falsos (ID_EVENTO BIGINT NOT NULL PRIMARY KEY, MISMA_HORA BOOLEAN NOT NULL)
SELECT x.ID_EVENTO, (s.FECHA = x.FECHA AND NOT s.SOLO_DIA) AS MISMA_HORA
  FROM (SELECT u.ID_EVENTO, u.FECHA,
               (SELECT s2.ID_EVENTO FROM Nsys_evento s2
                 WHERE s2.IMEI_SN = u.IMEI_SN AND s2.BATCH = u.BATCH
                   AND s2.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO')
                   AND IF(s2.SOLO_DIA, s2.FECHA + INTERVAL 1 DAY - INTERVAL 1 SECOND, s2.FECHA) <= u.FECHA
                 ORDER BY IF(s2.SOLO_DIA, s2.FECHA + INTERVAL 1 DAY - INTERVAL 1 SECOND, s2.FECHA) DESC, s2.ID_EVENTO DESC
                 LIMIT 1) AS ID_ESTADO
          FROM Nsys_evento u
         WHERE u.TIPO = 'UBICACION' AND u.A = @sin_caja AND COALESCE(u.A_NSYS, '') = '') x
  JOIN Nsys_evento s ON s.ID_EVENTO = x.ID_ESTADO
 WHERE s.TIPO = 'VENTA'
    OR (s.TIPO = 'ESTADO' AND (s.A IS NULL OR s.A NOT IN ('Available', 'InProcess', 'Repair')));
SELECT COUNT(*) AS falsos, COALESCE(SUM(MISMA_HORA), 0) AS a_la_hora_del_cierre,
       COALESCE(SUM(NOT MISMA_HORA), 0) AS despues
  FROM tmp_falsos;

-- 2) Vueltas de un vendido o de una baja a una caja con el «Desde: En revisión» falso.
CREATE TEMPORARY TABLE tmp_vueltas (ID_EVENTO BIGINT NOT NULL PRIMARY KEY)
SELECT u.ID_EVENTO
  FROM Nsys_evento u
 WHERE u.TIPO = 'UBICACION' AND u.DE = @sin_caja
   AND EXISTS (SELECT 1 FROM Nsys_evento v
                WHERE v.IMEI_SN = u.IMEI_SN AND v.BATCH = u.BATCH AND v.FECHA = u.FECHA AND NOT v.SOLO_DIA
                  AND (v.TIPO = 'VENTA_DESHECHA'
                       OR (v.TIPO = 'ESTADO' AND (v.DE IS NULL OR v.DE NOT IN ('Available', 'InProcess', 'Repair'))
                           AND v.A IN ('Available', 'InProcess', 'Repair'))));
SELECT COUNT(*) AS vueltas_con_desde_falso FROM tmp_vueltas;

-- 3) Vendidos y bajas sin caja en la foto.
SELECT COUNT(*) AS cerrados_sin_caja, COALESCE(SUM(UBICACION IS NOT NULL), 0) AS con_ubicacion,
       COALESCE(SUM(UBICACION_ULTIMA <=> @sin_caja), 0) AS ultima_sin_caja
  FROM Nsys_movil
 WHERE (STATUS IS NULL OR STATUS NOT IN ('Available', 'InProcess', 'Repair')) AND LOCATION = '';

-- 3b) De pedido con «En revisión» de última caja: los de verdad (vendidos desde «sin caja») y los de una venta deshecha
--     con el código viejo, que la limpieza corrige desde el rastro.
SELECT COUNT(*) AS de_pedido_con_ultima_sin_caja
  FROM Nsys_movil
 WHERE ESTADO = 'DE_PEDIDO' AND UBICACION_ULTIMA <=> @sin_caja;

-- 4) Móviles en la empresa pendientes de revisar tras una vuelta: para mirar su ficha antes y después.
SELECT m.IMEI_SN, m.BATCH, m.UBICACION, MAX(v.FECHA) AS vuelta
  FROM Nsys_movil m
  JOIN Nsys_evento v ON v.IMEI_SN = m.IMEI_SN AND v.BATCH = m.BATCH AND NOT v.SOLO_DIA
       AND (v.TIPO = 'VENTA_DESHECHA'
            OR (v.TIPO = 'ESTADO' AND (v.DE IS NULL OR v.DE NOT IN ('Available', 'InProcess', 'Repair'))
                AND v.A IN ('Available', 'InProcess', 'Repair')))
 WHERE m.ESTADO IN ('STOCK', 'DE_PEDIDO', 'NO_CUENTA') AND m.UBICACION IN (@sin_caja, @devolucion)
 GROUP BY m.IMEI_SN, m.BATCH, m.UBICACION
 ORDER BY vuelta DESC
 LIMIT 10;
```

- [ ] **Step 2: `sql/limpieza-nsys-vendido-en-revision.sql`**

```sql
-- 0.9.18.2: limpieza de lo que dejó guardado el fallo de la caja vacía (spec en el repo raíz:
-- docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md, §3).
-- Se pasa DESPUÉS de desplegar el backend 0.9.18.2: el importador viejo volvería a dejar «En revisión» en Nsys_movil.
-- Todo en una transacción. Ensayo sin escribir: cambiar el COMMIT del final por ROLLBACK (el guion lo hace con sed).
-- Nsys_evento solo se añade; esto es la excepción, para quitar eventos que nunca pasaron.
-- Antes y después: recuento-nsys-vendido-en-revision.sql.
SET @sin_caja = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = '');
SELECT @sin_caja AS sin_caja, HEX(@sin_caja) AS hex;

START TRANSACTION;

-- 1) Se borran los «→ En revisión» de una caja vaciada con el lote cerrado (misma selección que el recuento).
CREATE TEMPORARY TABLE tmp_falsos (ID_EVENTO BIGINT NOT NULL PRIMARY KEY, MISMA_HORA BOOLEAN NOT NULL)
SELECT x.ID_EVENTO, (s.FECHA = x.FECHA AND NOT s.SOLO_DIA) AS MISMA_HORA
  FROM (SELECT u.ID_EVENTO, u.FECHA,
               (SELECT s2.ID_EVENTO FROM Nsys_evento s2
                 WHERE s2.IMEI_SN = u.IMEI_SN AND s2.BATCH = u.BATCH
                   AND s2.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO')
                   AND IF(s2.SOLO_DIA, s2.FECHA + INTERVAL 1 DAY - INTERVAL 1 SECOND, s2.FECHA) <= u.FECHA
                 ORDER BY IF(s2.SOLO_DIA, s2.FECHA + INTERVAL 1 DAY - INTERVAL 1 SECOND, s2.FECHA) DESC, s2.ID_EVENTO DESC
                 LIMIT 1) AS ID_ESTADO
          FROM Nsys_evento u
         WHERE u.TIPO = 'UBICACION' AND u.A = @sin_caja AND COALESCE(u.A_NSYS, '') = '') x
  JOIN Nsys_evento s ON s.ID_EVENTO = x.ID_ESTADO
 WHERE s.TIPO = 'VENTA'
    OR (s.TIPO = 'ESTADO' AND (s.A IS NULL OR s.A NOT IN ('Available', 'InProcess', 'Repair')));
DELETE e FROM Nsys_evento e JOIN tmp_falsos f ON f.ID_EVENTO = e.ID_EVENTO;
SELECT ROW_COUNT() AS eventos_borrados;

-- 2) Las vueltas a una caja se quedan sin el «Desde: En revisión» falso.
CREATE TEMPORARY TABLE tmp_vueltas (ID_EVENTO BIGINT NOT NULL PRIMARY KEY)
SELECT u.ID_EVENTO
  FROM Nsys_evento u
 WHERE u.TIPO = 'UBICACION' AND u.DE = @sin_caja
   AND EXISTS (SELECT 1 FROM Nsys_evento v
                WHERE v.IMEI_SN = u.IMEI_SN AND v.BATCH = u.BATCH AND v.FECHA = u.FECHA AND NOT v.SOLO_DIA
                  AND (v.TIPO = 'VENTA_DESHECHA'
                       OR (v.TIPO = 'ESTADO' AND (v.DE IS NULL OR v.DE NOT IN ('Available', 'InProcess', 'Repair'))
                           AND v.A IN ('Available', 'InProcess', 'Repair'))));
UPDATE Nsys_evento e JOIN tmp_vueltas t ON t.ID_EVENTO = e.ID_EVENTO SET e.DE = NULL;
SELECT ROW_COUNT() AS vueltas_sin_desde;

-- 3) Vendidos y bajas sin caja: sin ubicación y, de última, la última ubicación del rastro (ya sin los falsos), o nada.
--    Es lo que calcula el importador nuevo, así no vuelve a guardar estas filas.
UPDATE Nsys_movil m
   SET m.UBICACION = NULL,
       m.UBICACION_ULTIMA = (SELECT e.A FROM Nsys_evento e
                              WHERE e.IMEI_SN = m.IMEI_SN AND e.BATCH = m.BATCH
                                AND e.TIPO IN ('UBICACION', 'ENTRADA') AND e.A IS NOT NULL
                              ORDER BY e.FECHA DESC, e.ID_EVENTO DESC
                              LIMIT 1)
 WHERE (m.STATUS IS NULL OR m.STATUS NOT IN ('Available', 'InProcess', 'Repair')) AND m.LOCATION = '';
SELECT ROW_COUNT() AS moviles_cambiados;

-- 3b) De pedido tras una venta deshecha con el código viejo: la última caja era la «En revisión» falsa. Se saca del
--     rastro, igual que en 3); a uno vendido de verdad desde «sin caja» le sale la misma, y sin rastro se deja como está.
UPDATE Nsys_movil m
   SET m.UBICACION_ULTIMA = COALESCE((SELECT e.A FROM Nsys_evento e
                                       WHERE e.IMEI_SN = m.IMEI_SN AND e.BATCH = m.BATCH
                                         AND e.TIPO IN ('UBICACION', 'ENTRADA') AND e.A IS NOT NULL
                                       ORDER BY e.FECHA DESC, e.ID_EVENTO DESC
                                       LIMIT 1), m.UBICACION_ULTIMA)
 WHERE m.ESTADO = 'DE_PEDIDO' AND m.UBICACION_ULTIMA <=> @sin_caja;
SELECT ROW_COUNT() AS de_pedido_cambiados;

COMMIT;
```

- [ ] **Step 3: Las selecciones del recuento y de la limpieza son iguales**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182/sql
diff <(sed -n '/^CREATE TEMPORARY TABLE tmp_falsos/,/NOT IN (.Available., .InProcess., .Repair.)));$/p' recuento-nsys-vendido-en-revision.sql) \
     <(sed -n '/^CREATE TEMPORARY TABLE tmp_falsos/,/NOT IN (.Available., .InProcess., .Repair.)));$/p' limpieza-nsys-vendido-en-revision.sql) && echo "falsos: iguales"
diff <(sed -n '/^CREATE TEMPORARY TABLE tmp_vueltas/,/Repair.))));$/p' recuento-nsys-vendido-en-revision.sql) \
     <(sed -n '/^CREATE TEMPORARY TABLE tmp_vueltas/,/Repair.))));$/p' limpieza-nsys-vendido-en-revision.sql) && echo "vueltas: iguales"
grep -c "COMMIT;" limpieza-nsys-vendido-en-revision.sql
```

Expected: `falsos: iguales`, `vueltas: iguales` y `1` (un solo `COMMIT;`, que el ensayo cambia por `ROLLBACK;`).

- [ ] **Step 4: La suite entera**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && mvn -q test 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 1124 tests (la base de la Task 1 + 16 nuevos). Incluye `OpenApiContractTest`, que arranca el
contexto.

- [ ] **Step 5: Commit de los SQL**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182
git add sql/recuento-nsys-vendido-en-revision.sql sql/limpieza-nsys-vendido-en-revision.sql
git commit -m "feat: recuento y limpieza de los en revision falsos de vendidos y bajas en el espejo de NSYS (0.9.18.2)"
```

- [ ] **Step 6: Revisión final de la rama**

Revisión de toda la rama (`git diff v0.9.18..hotfix/0.9.18.2`) frente a la spec, con `superpowers:requesting-code-review`.
Se arregla lo importante antes de seguir; lo menor se apunta en `plan-futuro.md`.

- [ ] **Step 7: Novedades (repo raíz)**

En `docs/novedades/NOVEDADES-v0.9.18.md`, al final de la sección «🔎 IMEIs», añadir:

```markdown
- Un móvil **vendido o dado de baja** ya no sale **«En revisión»** ni **pendiente de revisar**. NSYS le vacía la caja al
  cerrarlo, y el rastro ya no apunta un «Pasa a En revisión» en cada venta o baja; también se han quitado los del
  histórico (0.9.18.2).
- Una **devolución** espera la revisión desde el día que vuelve, no desde la venta (0.9.18.2).
```

Y en «📱 Stock de teléfonos», en el punto de «Pegar varios IMEIs», añadir al final: `Un vendido sale sin ubicación
(0.9.18.2).`

Antes del commit, `git fetch` y `git status` en la raíz (otras sesiones tocan `docs/novedades/`):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones && git fetch -q && git status -sb | head -5
git add docs/novedades/NOVEDADES-v0.9.18.md && git commit -m "docs: novedades de la 0.9.18 con el vendido que no esta en revision (0.9.18.2)"
```

- [ ] **Step 8: Subir la rama (con el OK del usuario)**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182 && git push -u origin hotfix/0.9.18.2
```

---

### Task 5: Preproducción

**Files:**
- Create: `C:/Users/dev/Documents/Apuntes/preprod/v09182-preprod.md` (guion, fuera de los repos)
- Modify: `C:/Users/dev/Documents/Apuntes/despliegue_preprod.md` (registro)

**Requisitos:** Task 4 con la rama subida. **Preguntar al usuario si preprod está libre:** otras sesiones prueban allí la
0.9.18.1 y la 0.9.19.

- [ ] **Step 1: Guion**

Basado en el bloque del servidor de `prod-v0918.md`. Preprod no tiene `backup-erp.sh`: cada bloque empieza con
`hostname` y lo comprueba para avisar si se pega en producción por error.

- **Bloque 1 (solo lee):**
  - commit del servidor (`git -C gestion-reparaciones-servidor log --oneline -1`; **se apunta para la vuelta atrás**) y
    `git -C gestion-reparaciones-servidor fetch -q origin && git -C gestion-reparaciones-servidor log --oneline -1 origin/hotfix/0.9.18.2`;
  - contenedores y `sin_terminar` de `Nsys_importacion`;
  - copia de las dos tablas, porque preprod no tiene copias nocturnas:
    `docker exec reparaciones-mariadb-1 sh -c 'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones Nsys_evento Nsys_movil' | gzip > ~/nsys-antes-09182.sql.gz && ls -l ~/nsys-antes-09182.sql.gz`.
- **Bloque 2 (escribe), con el OK de Claude:**
  - `git -C gestion-reparaciones-servidor checkout --detach origin/hotfix/0.9.18.2`;
  - `docker compose build backend && docker compose up -d --no-deps backend` (avisar: ~1 min sin salida);
  - `Started App` sin `ERROR`.
- **Bloque 3 (solo lee):** el recuento, desde el clon:
  `docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones' < gestion-reparaciones-servidor/sql/recuento-nsys-vendido-en-revision.sql`.
  Esperado: `hex` = `456E20726576697369C3B36E`; `falsos` del orden de las ventas y bajas desde dic. 2023; casi todos
  `a_la_hora_del_cierre`. **Si `despues` es grande, parar y mirar unos casos antes de seguir.**
- **Bloque 4 (ensayo, sin escribir):** la limpieza con `ROLLBACK`:
  `sed 's/^COMMIT;/ROLLBACK;/' gestion-reparaciones-servidor/sql/limpieza-nsys-vendido-en-revision.sql | docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones'`.
  Esperado: `eventos_borrados` = `falsos`, `vueltas_sin_desde` = `vueltas_con_desde_falso`, `moviles_cambiados` cerca de
  `cerrados_sin_caja`. Después, el recuento otra vez: los mismos números que en el bloque 3 (no se ha escrito nada).
- **Bloque 5 (escribe):** la limpieza de verdad (`< …/limpieza-nsys-vendido-en-revision.sql`) y el recuento. Esperado:
  `falsos` 0, `vueltas_con_desde_falso` 0, `con_ubicacion` 0; `de_pedido_con_ultima_sin_caja` igual o menor que antes
  (quedan los vendidos de verdad desde «sin caja»), menos `de_pedido_cambiados`; la lista 4 con los mismos móviles.
- **Comprobaciones de Claude desde el PC** (preprod, cuentas de `~/.env.e2e`):
  - `GET /api/stock-telefonos/{imei}` de un móvil vendido (lo saca Claude de `GET /api/stock-telefonos/buscar`):
    `ubicacion` null y `pendienteRevisionDesde` null;
  - `GET /api/stock-telefonos/{imei}/rastro`: sin «→ En revisión» a la hora de la venta;
  - la ficha de un móvil de la lista 4: `pendienteRevisionDesde` = el día de su vuelta;
  - E2E `stock-telefonos` (solo lee) con `E2E_BASE_URL` de preprod.
- **Pruebas a mano con el usuario:** la ficha y el rastro del IMEI de la captura del 20/10 (si está en la copia de
  preprod) y de una devolución de la lista 4.
- **Bloque 6, vuelta atrás (solo si algo falla):** `git -C gestion-reparaciones-servidor checkout <commit del bloque 1>`,
  `docker compose build backend && docker compose up -d --no-deps backend` y, para los datos,
  `gunzip -c ~/nsys-antes-09182.sql.gz | docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" gestion_reparaciones'`.

- [ ] **Step 2: Ejecución con el usuario y registro**

1. El usuario pega los bloques y Claude valida cada salida antes de seguir.
2. Entrada en `despliegue_preprod.md` con los commits, los recuentos, las comprobaciones y la E2E.
3. **Preprod se queda en `hotfix/0.9.18.2`** hasta producción (no hay bloque de vuelta a `main`: la limpieza ya está
   hecha y el código viejo volvería a dejar «En revisión»). Si otra sesión necesita preprod en `main` antes, se le avisa.
4. **Producción solo** cuando cuadra y el usuario da el OK.

---

### Task 6: Producción, tag y merge a `main`

**Files:**
- Create: `C:/Users/dev/Documents/Apuntes/prod-v09182.md` (guion)
- Modify: `C:/Users/dev/Documents/Apuntes/despliegue_vdc_produccion.md` (registro), `C:/Users/dev/Documents/Apuntes/plan-futuro.md`;
  en la memoria, `project_produccion_vdc.md` y `project_stock_telefonos_nsys.md`; en la raíz, el gitlink del servidor

- [ ] **Step 1: Guion y despliegue**

El mismo guion que preprod, con estos cambios:
- **En horario:** solo el backend (unos 5 s de corte), sin migración. La web que tengan abierta sigue funcionando. Mejor
  entre dos Items (las exportaciones son a :01 y :31), aunque si entra uno en medio no pasa nada (spec §4).
- **Bloque 1:** `backup-erp.sh` (copia de toda la base; **anotar nombre y tamaño**: es la vuelta atrás de los datos) en vez
  del `mariadb-dump` de las dos tablas. El servidor debería estar en `728f2f9`.
- **Bloque 2:** `git -C gestion-reparaciones-servidor fetch -q origin && git -C gestion-reparaciones-servidor checkout --detach origin/hotfix/0.9.18.2`
  (**no `pull`**: así no entra nada que haya llegado a `main` del servidor para la 0.9.19).
- **Bloques 3-5:** recuento, ensayo con `ROLLBACK`, limpieza y recuento. Los números deben parecerse a los de preprod.
- **Comprobaciones:** Claude, sin sesión: `/version.json` sigue en la web de antes y `GET /api/stock-telefonos/x` da 403.
  El usuario, con su cuenta y sin guardar nada: la ficha y el rastro del IMEI de la captura del 20/10, y una devolución
  de la lista 4.
- **Vuelta atrás:** `checkout 728f2f9` + build del backend; los datos, desde la copia del bloque 1.
- **Aviso en el guion y en el registro:** el servidor de producción queda en un commit suelto. La próxima subida del
  servidor empieza con `git -C gestion-reparaciones-servidor checkout main` antes del `pull --ff-only`.
- **Sin E2E en producción.**

- [ ] **Step 2: Tag (con el OK del usuario)**

```bash
cd /c/Users/dev/Documents/wt/servidor-09182
git tag v0.9.18.2 && git push origin v0.9.18.2
```

- [ ] **Step 3: Merge a `main` del servidor (con el OK del usuario)**

Solo si en el clon principal `git status` está limpio, en `main`, y `git fetch` deja `main` igual a `origin/main`; si no,
parar y preguntar (otras sesiones pueden haber empezado la 0.9.19 en el servidor).

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git fetch -q origin && git status -sb | head -3
git merge --no-ff hotfix/0.9.18.2 -m "merge: un movil vendido o de baja no esta en revision y las devoluciones esperan desde la vuelta (0.9.18.2)"
mvn -q test 2>&1 | tail -3
git push origin main
```

Expected: el merge sin conflictos si `main` sigue en `728f2f9`; la suite en verde.

- [ ] **Step 4: Raíz, limpieza y registro**

1. Raíz: `git add gestion-reparaciones-servidor && git commit -m "chore: gitlink del servidor con la 0.9.18.2"` (con
   `git fetch` y `git status` antes) y plan marcado; push con el OK del usuario.
2. Worktree y rama: `git worktree remove /c/Users/dev/Documents/wt/servidor-09182` y `git branch -d hotfix/0.9.18.2` (la
   remota, con el OK).
3. Registro en `despliegue_vdc_produccion.md`; `plan-futuro.md` con la tarea del falso «En revisión» marcada; memoria
   de producción y del módulo de teléfonos al día.
