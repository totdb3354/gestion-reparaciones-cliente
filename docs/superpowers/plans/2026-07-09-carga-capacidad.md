# Carga de técnicos v2 (% sobre capacidad diaria) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar la spec `docs/superpowers/specs/2026-07-09-carga-capacidad-diaria-design.md`: el % de la ventana "Carga de técnicos" pasa a medir la fracción de la jornada de HOY consumida (hecho hoy + pendiente), con toggle Pedidos|Total, barra de dos tramos coloreada por nivel, y los dos % en el modal de asignar.

**Architecture:** Descubrimiento clave del recon: al completar, la asignación `A%`/`AG%` se cierra con `FECHA_FIN` conservando `ES_CHASIS`/`POR_CERRAR`/cliente (las filas `R%` NO llevan `ES_CHASIS` y hay una por componente — no sirven para contar). Por tanto **"hecho hoy" = asignaciones cerradas hoy**, misma unidad que "pendiente" → un único motor de pesado en fracciones de jornada (`CargaTecnicos.calcularDia`) alimentado por dos listas: abiertas (ya la tiene la vista) + cerradas-hoy (endpoint nuevo). El corte de "hoy" es el inicio del día en Madrid, reutilizando el patrón ya testeado de `UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid`.

**Tech Stack:** Java 17 · JavaFX 21 · Spring Boot 3.3.4 + JdbcTemplate · JUnit 5.

## Restricciones globales

- **Ramas `feature/carga-capacidad` YA CREADAS en ambos repos** (raíz desde main `ea11316`; servidor desde main `915225a`). Verificar con `git branch --show-current` antes de tocar nada. **La F2a está pausada en sus ramas `feature/fase2a-lotes-telefonos` — NO tocarlas ni tocar sus ficheros** (AgrupadoController, LoteController, TelefonoDAO/Controller del servidor).
- Commits SIN Co-Authored-By; mensajes `feat/fix(ámbito): descripción en español`. Servidor commitea en su repo (cwd `gestion-reparaciones-servidor`), cliente en el raíz.
- Comandos por Bash: cliente `cd gestion-reparaciones-cliente && mvn -q test`; servidor `cd gestion-reparaciones-servidor && mvn -q test`.
- Merge/push/tag solo con OK del usuario. Orden de despliegue final: **servidor primero** (endpoint nuevo), luego cliente. Sin ALTER de BD.
- **Parámetros con procedencia documentada en el código** (petición explícita del usuario): cada constante lleva comentario de dónde sale y por qué. Valores exactos de la spec: jornadas L-M 9h · X-J 8h · V 6h · S-D 0; topes sobre jornada de 9h: **chasis 8/día, glass 17/día** (medición del taller, 2026-07-09), **normales `TOPE_NORMALES_9H` = dato que entrega el controlador** (derivado con el usuario de la query de la spec §5 — si no está en el encargo, parar con NEEDS_CONTEXT, NUNCA inventarlo); por cerrar = 8,3% del tiempo de su tipo (`× 1.0/12`); solicitud de pieza pendiente = 0 (libera carga, solo aplica a abiertas); **pulido NO cuenta** (decisión A5: "quien pule, ese día solo pule").
- Colores por nivel (barra y número SIEMPRE sincronizados; el texto usa el paso oscuro): `<70%` azul (barra `#1565C0`, texto `#0D47A1`) · `70–89%` ámbar (barra `#F9A825`, texto `#B26A00`) · `≥90%` rojo (barra `#E53935`, texto `#C62828`). Puntos de identidad FIJOS: **violeta `#7B1FA2` = Pedidos, azul `#1565C0` = Total** (nunca cambian por nivel). Barra satura al 100%; el número sigue ("112%").
- Filtro Pedidos = asignación con cliente (`getCliente()` no vacío); Total = todas. Pedidos es la vista por defecto y va PRIMERO donde aparezcan ambos.

## Contexto imprescindible (del código real)

- `utils/CargaTecnicos.java` (v1, en main): `PESO_POR_CERRAR = 1.0/12`, `PESO_GLASS = 1.5`, `Desglose(normales, chasis, porCerrar, glass, enEsperaPieza, carga)`, `calcular(List<ReparacionResumen>)` (unidades v1 — SE CONSERVA para el hover), `porcentajes(...)` (SE ELIMINA, C13), `formatearCarga(double)` (se conserva), `enEsperaDePieza(r)` privado (criterio del badge "Recibido": `esSolicitud>0` y no (`GESTIONADA` && `stock>0`)).
- `controllers/PendientesSuperTecnicoController.java`: `cargasActuales` (Map idTec→Desglose) recalculado en `actualizarFranjaCarga()` desde `cargar()`; ventana en `abrirCargaTecnicos()` (~línea 829) + `filaCargaTecnico(...)` (~912) + hover `resaltarFilaCarga`/`quitarResaltadoFilaCarga`/`fundirOpacidad`; modal usa `etiquetaConCarga(Tecnico)` (~853) en 3 puntos de render. La lista `tecnicos` = activos; `datos` = asignaciones abiertas (rep+glass+pulido).
- Servidor `dao/ReparacionDAO.java`: `ASIGNACION_SELECT` (~línea 69, `WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.FECHA_FIN IS NULL` — VERIFICAR el WHERE exacto al implementar) + `RESUMEN_MAPPER` + el GROUP BY estándar (ver `getAsignaciones` ~190). `job/UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(Clock)` es `public static`, testeada, devuelve `Timestamp` del inicio de hoy en Madrid.
- Cliente `dao/ReparacionDAO.java`: patrón `ApiClient.getList("/api/reparaciones/...", ReparacionResumen.class)`.
- `TipoTrabajo.desde(idRep)`: AG→GLASS, AP→PULIDO, A→REPARACION.
- `ReparacionResumen` (cliente): `getIdTec/getCliente/isEsChasis/isPorCerrar/getEsSolicitud/getEstadoSolicitud/getStockSolicitud/getFechaFin` + setters de test.

## Estructura de ficheros

**Servidor:** Modify `dao/ReparacionDAO.java` (+`getAsignacionesCompletadasHoy`), `controller/ReparacionController.java` (+GET).
**Cliente:** Modify `utils/CargaTecnicos.java` + `utils/CargaTecnicosTest.java` (núcleo v2, TDD), `dao/ReparacionDAO.java` (+`getAsignacionesCompletadasHoy`), `controllers/PendientesSuperTecnicoController.java` (ventana v2 + modal), spec si hay ajustes.

---

### Tarea 1: Servidor — asignaciones completadas hoy

**Files:**
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Modify: `gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`

**Interfaces:**
- Produces: `GET /api/reparaciones/asignaciones/completadas-hoy` → `List<ReparacionResumen>` (asignaciones `A%`/`AG%` con `FECHA_FIN >= inicio de hoy Madrid`), MISMO shape JSON que `/asignaciones` (el cliente reutiliza su modelo). Cualquier usuario autenticado.

- [ ] **Paso 1: Verificar rama** — `cd gestion-reparaciones-servidor && git branch --show-current` → `feature/carga-capacidad`; si no, BLOCKED.

- [ ] **Paso 2: DAO.** Junto a `getAsignaciones` (~línea 190), añadir un método que reutiliza `ASIGNACION_SELECT` cambiando el criterio temporal. OJO: `ASIGNACION_SELECT` termina en un `WHERE` que incluye `FECHA_FIN IS NULL` (verificar el literal exacto en el fichero); NO se puede concatenar sin quitarlo. Hacerlo sin duplicar las ~30 líneas del SELECT: `String sql = ASIGNACION_SELECT.replace("r.FECHA_FIN IS NULL", "r.FECHA_FIN >= ?");` y validar con un `if (!sql.contains("r.FECHA_FIN >= ?")) throw new IllegalStateException("ASIGNACION_SELECT cambió: revisar getAsignacionesCompletadasHoy");` (guarda contra ediciones futuras del SELECT).

```java
/** Asignaciones (A/AG) COMPLETADAS desde el corte dado (inicio de hoy en Madrid):
 *  la unidad de "hecho hoy" de la carga por capacidad — conservan ES_CHASIS,
 *  POR_CERRAR y cliente, cosa que las filas R% no hacen. */
public List<ReparacionResumen> getAsignacionesCompletadasHoy(java.sql.Timestamp cutoff) {
    String sql = ASIGNACION_SELECT.replace("r.FECHA_FIN IS NULL", "r.FECHA_FIN >= ?");
    if (!sql.contains("r.FECHA_FIN >= ?"))
        throw new IllegalStateException("ASIGNACION_SELECT cambió: revisar getAsignacionesCompletadasHoy");
    String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                     " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR, ta.NOMBRE, cli.NOMBRE" +
                     " ORDER BY r.FECHA_FIN ASC";
    return jdbc.query(sql + groupBy, RESUMEN_MAPPER, cutoff);
}
```

- [ ] **Paso 3: Endpoint.** En `ReparacionController`, junto a los GET de asignaciones (~línea 73):

```java
/** Asignaciones completadas hoy (corte = inicio de hoy en Madrid) — "hecho hoy" de la carga v2. */
@GetMapping("/asignaciones/completadas-hoy")
public List<ReparacionResumen> getAsignacionesCompletadasHoy() {
    return dao.getAsignacionesCompletadasHoy(
            com.reparaciones.servidor.job.UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid(
                    java.time.Clock.system(java.time.ZoneId.of("Europe/Madrid"))));
}
```

- [ ] **Paso 4: Suite + commit (repo SERVIDOR)** — `mvn -q test` → 18 verdes, pegar cola en el informe.

```bash
git add src/main/java && git commit -m "feat(servidor): asignaciones completadas hoy (corte Madrid) para la carga por capacidad"
```

---

### Tarea 2: Cliente — núcleo `CargaTecnicos` v2 (TDD estricto)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CargaTecnicos.java`
- Test: `gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/CargaTecnicosTest.java`

**Interfaces:**
- Consumes: `ReparacionResumen`, `TipoTrabajo.desde`.
- Produces (lo consumen T4/T5):
  - Constantes documentadas: `JORNADA_HORAS: Map<DayOfWeek,Integer>` {MON 9, TUE 9, WED 8, THU 8, FRI 6, SAT 0, SUN 0}; `TOPE_CHASIS_9H = 8`, `TOPE_GLASS_9H = 17`, `TOPE_NORMALES_9H = <VALOR DEL CONTROLADOR>` (todas con comentario de procedencia).
  - `record DiaTecnico(double pctHecho, double pctPendiente, Desglose hecho, Desglose pendiente, boolean sinJornada)` — pcts en 0..100+ ya escalados a la jornada del día; `pctTotal()` = suma; con `sinJornada` los pct valen 0.
  - `static Map<Integer, DiaTecnico> calcularDia(List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, DayOfWeek dia, boolean soloPedidos)`.
  - `static String formatearPct(double pct)` → entero redondeado + "%" ("79%", "112%").
  - Se ELIMINA `porcentajes(...)` y sus tests (jubilado, C13). `calcular` (unidades v1) y `formatearCarga` se conservan tal cual (hover).

- [ ] **Paso 1: Tests nuevos que fallan** (sustituyen a `porcentajesRelativosQueSumanCien`; añadir al fichero existente; el helper `asig(...)` ya existe — extenderlo con variante que fija `fechaFin` para cerradas):

```java
// Fracciones sobre jornada de 9h (lunes): 4 chasis + 5 glass = 4/8 + 5/17 = 79,4%
@Test void fraccionesSobreJornadaLarga() {
    List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) abiertas.add(asig("A_c" + i, 1, "WEB", true, false));
    for (int i = 0; i < 5; i++) abiertas.add(asig("AG_g" + i, 1, "WEB", false, false));
    var dia = CargaTecnicos.calcularDia(abiertas, List.of(), java.time.DayOfWeek.MONDAY, false);
    assertEquals(0.0, dia.get(1).pctHecho(), 0.01);
    assertEquals(79.41, dia.get(1).pctPendiente(), 0.05);
    assertFalse(dia.get(1).sinJornada());
}

// El viernes (6h) la misma carga pesa 9/6 más: 79,4 × 1,5 = 119,1% (>100 permitido)
@Test void escaladoPorJornadaCorta() {
    List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) abiertas.add(asig("A_c" + i, 1, "WEB", true, false));
    for (int i = 0; i < 5; i++) abiertas.add(asig("AG_g" + i, 1, "WEB", false, false));
    var dia = CargaTecnicos.calcularDia(abiertas, List.of(), java.time.DayOfWeek.FRIDAY, false);
    assertEquals(119.11, dia.get(1).pctPendiente(), 0.1);
}

// Hecho hoy (asignaciones cerradas) va al tramo hecho, con los mismos pesos
@Test void hechoHoySumaEnSuTramo() {
    var cerrada = asig("A_done", 1, "WEB", true, false);
    cerrada.setFechaFin(java.time.LocalDateTime.now());
    var dia = CargaTecnicos.calcularDia(List.of(asig("A_p", 1, "WEB", false, false)),
            List.of(cerrada), java.time.DayOfWeek.MONDAY, false);
    assertEquals(100.0 / 8, dia.get(1).pctHecho(), 0.01);        // 1 chasis = 12,5%
    assertEquals(100.0 / CargaTecnicos.TOPE_NORMALES_9H, dia.get(1).pctPendiente(), 0.01);
    assertEquals(1, dia.get(1).hecho().chasis());
    assertEquals(1, dia.get(1).pendiente().normales());
}

// Vista Pedidos: sin cliente no cuenta; vista Total sí
@Test void vistaPedidosFiltraPorCliente() {
    var conCliente = asig("A_1", 1, "WEB", false, false);
    var sinCliente = asig("A_2", 1, null, false, false);
    var pedidos = CargaTecnicos.calcularDia(List.of(conCliente, sinCliente), List.of(), java.time.DayOfWeek.MONDAY, true);
    var total   = CargaTecnicos.calcularDia(List.of(conCliente, sinCliente), List.of(), java.time.DayOfWeek.MONDAY, false);
    assertEquals(100.0 / CargaTecnicos.TOPE_NORMALES_9H,     pedidos.get(1).pctPendiente(), 0.01);
    assertEquals(2 * 100.0 / CargaTecnicos.TOPE_NORMALES_9H, total.get(1).pctPendiente(), 0.01);
}

// Por cerrar = 8,3% del tiempo de su tipo; solicitud pendiente libera (solo abiertas); pulido fuera
@Test void reglasDePesoSeMantienenEnFracciones() {
    var porCerrarChasis = asig("A_pc", 1, "WEB", true, true);            // 0,083 × 1/8
    var conSolicitud    = asig("A_sol", 1, "WEB", false, false);
    conSolicitud.setEsSolicitud(1); conSolicitud.setEstadoSolicitud("PENDIENTE");
    var pulido          = asig("AP_1", 1, "WEB", false, false);
    var dia = CargaTecnicos.calcularDia(List.of(porCerrarChasis, conSolicitud, pulido),
            List.of(), java.time.DayOfWeek.MONDAY, false);
    assertEquals((1.0 / 12) * (100.0 / 8), dia.get(1).pctPendiente(), 0.01);
    assertEquals(1, dia.get(1).pendiente().enEsperaPieza());
    assertEquals(0, dia.get(1).pendiente().normales());
}

// Sábado/domingo: sin jornada
@Test void finDeSemanaSinJornada() {
    var dia = CargaTecnicos.calcularDia(List.of(asig("A_1", 1, "WEB", false, false)),
            List.of(), java.time.DayOfWeek.SATURDAY, false);
    assertTrue(dia.get(1).sinJornada());
    assertEquals(0.0, dia.get(1).pctTotal(), 0.001);
}

@Test void formateaPctEntero() {
    assertEquals("79%", CargaTecnicos.formatearPct(79.41));
    assertEquals("112%", CargaTecnicos.formatearPct(119.11) == null ? null : CargaTecnicos.formatearPct(112.4));
    assertEquals("0%", CargaTecnicos.formatearPct(0));
}
```

(El segundo assert de `formateaPctEntero` tal cual está es enrevesado: simplificarlo a `assertEquals("112%", CargaTecnicos.formatearPct(112.4));`.) Si el helper `asig` no admite cliente null o falta `setFechaFin`/`setEsSolicitud`, extenderlo/añadir setters como se hizo antes.

- [ ] **Paso 2: RED** — `mvn -q test -Dtest=CargaTecnicosTest` → no compila (`calcularDia`, `DiaTecnico`, `TOPE_NORMALES_9H` no existen). Pegar transcript.

- [ ] **Paso 3: Implementación** (añadir a `CargaTecnicos`; `porcentajes` se borra):

```java
// ── Capacidad diaria (v2, spec 2026-07-09-carga-capacidad-diaria) ────────────
// Topes medidos por el usuario en el taller (2026-07-09) sobre la jornada larga
// de 9h: si SOLO haces eso en el día salen ~8 chasis o ~17 glass. El de normales
// se derivó de la BD (mediana de días "casi puros" desde 2026-06-30, spec §5).
// El resto de jornadas escala lineal. Futuro apuntado: mover a tabla configurable.
public static final int TOPE_CHASIS_9H   = 8;
public static final int TOPE_GLASS_9H    = 17;
public static final int TOPE_NORMALES_9H = /*VALOR_DEL_CONTROLADOR*/;

/** Horas de jornada por día de semana (dato del usuario, 2026-07-09). */
public static final java.util.Map<java.time.DayOfWeek, Integer> JORNADA_HORAS = java.util.Map.of(
        java.time.DayOfWeek.MONDAY, 9,  java.time.DayOfWeek.TUESDAY, 9,
        java.time.DayOfWeek.WEDNESDAY, 8, java.time.DayOfWeek.THURSDAY, 8,
        java.time.DayOfWeek.FRIDAY, 6,
        java.time.DayOfWeek.SATURDAY, 0, java.time.DayOfWeek.SUNDAY, 0);

/** Carga del día de un técnico: % ya escalados a la jornada de hoy (pueden superar 100). */
public record DiaTecnico(double pctHecho, double pctPendiente,
                         Desglose hecho, Desglose pendiente, boolean sinJornada) {
    public double pctTotal() { return pctHecho + pctPendiente; }
}

/** Fracción de jornada de 9h que consume una asignación (0 si no computa). */
private static double fraccion9h(ReparacionResumen r, boolean esAbierta, boolean soloPedidos) {
    if (soloPedidos && (r.getCliente() == null || r.getCliente().isEmpty())) return 0;
    if (esAbierta && enEsperaDePieza(r)) return 0;   // solicitud pendiente libera carga
    TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
    double base = switch (tipo) {
        case GLASS      -> 1.0 / TOPE_GLASS_9H;
        case REPARACION -> r.isEsChasis() ? 1.0 / TOPE_CHASIS_9H : 1.0 / TOPE_NORMALES_9H;
        case PULIDO     -> 0;   // quien pule, ese día solo pule (decisión A5)
    };
    // Por cerrar = 8,3% del tiempo de su tipo (spec §1); PESO_POR_CERRAR = 1.0/12
    return (r.isPorCerrar() && tipo == TipoTrabajo.REPARACION) ? base * PESO_POR_CERRAR : base;
}
```

```java
public static java.util.Map<Integer, DiaTecnico> calcularDia(
        List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
        java.time.DayOfWeek dia, boolean soloPedidos) {
    int horas = JORNADA_HORAS.getOrDefault(dia, 0);
    boolean sinJornada = horas == 0;
    double factor = sinJornada ? 0 : 9.0 / horas;   // pct del día = fracción9h × 9/horas × 100
    java.util.Map<Integer, double[]> acum = new java.util.HashMap<>();      // [hecho, pendiente]
    java.util.Map<Integer, Desglose[]> desg = new java.util.HashMap<>();    // [hecho, pendiente]
    java.util.function.BiConsumer<ReparacionResumen, Boolean> suma = (r, esAbierta) -> {
        if (TipoTrabajo.desde(r.getIdRep()) == TipoTrabajo.PULIDO) return;
        if (soloPedidos && (r.getCliente() == null || r.getCliente().isEmpty())) return;
        double f = fraccion9h(r, esAbierta, soloPedidos);
        int idx = esAbierta ? 1 : 0;
        acum.computeIfAbsent(r.getIdTec(), k -> new double[2])[idx] += f;
        Desglose[] d = desg.computeIfAbsent(r.getIdTec(),
                k -> new Desglose[]{ new Desglose(0,0,0,0,0,0), new Desglose(0,0,0,0,0,0) });
        d[idx] = sumarDesglose(d[idx], r, esAbierta, f);
    };
    cerradasHoy.forEach(r -> suma.accept(r, false));
    abiertas.forEach(r -> suma.accept(r, true));
    java.util.Map<Integer, DiaTecnico> out = new java.util.HashMap<>();
    boolean finalSin = sinJornada;
    acum.forEach((idTec, a) -> out.put(idTec, new DiaTecnico(
            a[0] * factor * 100, a[1] * factor * 100,
            desg.get(idTec)[0], desg.get(idTec)[1], finalSin)));
    return out;
}

/** Cuenta la asignación en el contador que le toca (mismos criterios que calcular v1). */
private static Desglose sumarDesglose(Desglose base, ReparacionResumen r, boolean esAbierta, double fraccion) {
    if (esAbierta && enEsperaDePieza(r)) return base.sumar(0, 0, 0, 0, 1, 0);
    TipoTrabajo tipo = TipoTrabajo.desde(r.getIdRep());
    if (tipo == TipoTrabajo.GLASS)  return base.sumar(0, 0, 0, 1, 0, fraccion);
    if (r.isPorCerrar())            return base.sumar(0, 0, 1, 0, 0, fraccion);
    if (r.isEsChasis())             return base.sumar(0, 1, 0, 0, 0, fraccion);
    return base.sumar(1, 0, 0, 0, 0, fraccion);
}

/** "79%" — entero redondeado; el color lo pone la UI según nivel. */
public static String formatearPct(double pct) {
    return Math.round(pct) + "%";
}
```

Notas de implementación: `enEsperaDePieza` hoy es privado — se queda privado (se usa desde aquí). `TOPE_NORMALES_9H`: usar el valor entregado en el encargo (NEEDS_CONTEXT si falta). Borrar `porcentajes(...)` y el test `porcentajesRelativosQueSumanCien` + `listaVaciaDaMapasVacios` adaptar (quitar la parte de porcentajes).

- [ ] **Paso 4: GREEN** — focalizados verdes; suite completa verde (número final = anterior − 2 tests jubilados + 7 nuevos; declararlo). Pegar transcripts.

- [ ] **Paso 5: Commit (repo RAÍZ)**

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): motor de carga por capacidad diaria (fracciones de jornada, hecho+pendiente, vistas pedidos/total)"
```

---

### Tarea 3: Cliente — DAO completadas-hoy y recálculo en `cargar()`

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/dao/ReparacionDAO.java`
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java`

**Interfaces:**
- Consumes: endpoint T1; `CargaTecnicos.calcularDia` (T2).
- Produces (para T4/T5): en el controller, campos `private List<ReparacionResumen> cerradasHoy = List.of();`, `private Map<Integer, CargaTecnicos.DiaTecnico> cargaDiaPedidos = Map.of();` y `private Map<Integer, CargaTecnicos.DiaTecnico> cargaDiaTotal = Map.of();`, recalculados en `actualizarFranjaCarga()`.

- [ ] **Paso 1: DAO cliente** — junto a `getAsignaciones()`:

```java
/** Asignaciones completadas hoy (corte de Madrid en servidor) — "hecho hoy" de la carga v2. */
public List<ReparacionResumen> getAsignacionesCompletadasHoy() throws SQLException {
    return ApiClient.getList("/api/reparaciones/asignaciones/completadas-hoy", ReparacionResumen.class);
}
```

- [ ] **Paso 2: Controller.** En `cargar()`, donde se cargan los `datos`, añadir la carga de `cerradasHoy` (misma tanda, con el mismo manejo de errores del método). En `actualizarFranjaCarga()`:

```java
private void actualizarFranjaCarga() {
    cargasActuales = CargaTecnicos.calcular(datos);   // unidades v1 (hover)
    java.time.DayOfWeek hoy = java.time.LocalDate.now().getDayOfWeek();
    cargaDiaPedidos = CargaTecnicos.calcularDia(datos, cerradasHoy, hoy, true);
    cargaDiaTotal   = CargaTecnicos.calcularDia(datos, cerradasHoy, hoy, false);
}
```

- [ ] **Paso 3: Suite + commit (repo RAÍZ)** — `mvn -q test` verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): carga de asignaciones completadas hoy y calculo dual pedidos/total"
```

---

### Tarea 4: Cliente — ventana v2 (toggle, barra de dos tramos, colores por nivel)

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`abrirCargaTecnicos`, `filaCargaTecnico`, hover)

**Interfaces:**
- Consumes: `cargaDiaPedidos`/`cargaDiaTotal`/`cargasActuales` (T3), `CargaTecnicos.formatearPct/formatearCarga`, colores/puntos de las Restricciones globales.

- [ ] **Paso 1: Toggle y estado.** En `abrirCargaTecnicos()`: dos `ToggleButton` "Pedidos" / "Total" en un `ToggleGroup` (patrón del selector Reparaciones|Glass|Pulidos del Historial — localizarlo con grep `ToggleGroup` en `HistorialController` o equivalente y clonar estilo), **Pedidos seleccionado por defecto**, colocados junto al título. Al cambiar, repintar las filas (extraer el pintado a un `Runnable repintar` que lee el mapa activo `cargaDiaPedidos` o `cargaDiaTotal` y reconstruye `filas.getChildren()`).

- [ ] **Paso 2: Fila v2** (`filaCargaTecnico` reescrita). Por técnico, con `DiaTecnico dt` del mapa activo (ausente → todo 0):
  - **Cifra principal**: `CargaTecnicos.formatearPct(dt.pctTotal())` teñida por nivel (texto: `#0D47A1` <70, `#B26A00` 70–89, `#C62828` ≥90), precedida del **punto de identidad** de la vista activa (`Label "●"` violeta `#7B1FA2` Pedidos / azul `#1565C0` Total — el punto NUNCA cambia por nivel). Con `dt.sinJornada()`: cifra = unidades v1 (`formatearCarga`) y un label pequeño "sin jornada hoy"; sin %.
  - **Barra dos tramos**: StackPane con track `#E8EAF0` + DOS fills alineados a la izquierda: `fillHecho` (color de nivel VIVO: `#1565C0`/`#F9A825`/`#E53935` según `dt.pctTotal()`) con ancho = `min(pctHecho,100)/100 × track`; `fillPendiente` (MISMO color al 45% de opacidad — usar el color con sufijo alfa vía `-fx-background-color: <color>73;` o `setOpacity(0.45)` en un Region apilado con translate... implementación recomendada: un HBox de dos Regions DENTRO de un clip redondeado, hecho primero y pendiente después, anchos `min(pctHecho,100)` y `min(pctTotal,100)-min(pctHecho,100)`). **Saturación**: los anchos se recortan a 100; el número no.
  - **Hover** (reutilizar `resaltarFilaCarga`/`fundirOpacidad`): al entrar, la cifra pasa a añadir ` · X uds` (unidades v1 de `cargasActuales`, `formatearCarga`) — el "· N%" relativo de v1 DESAPARECE. Atenuación igual que ahora.
  - **Tooltip**: desglose del tramo pendiente + hecho: `"Pendiente: 3 normales · 1 chasis · 2 en espera de pieza — Hecho hoy: 2 normales · 1 glass"` (omitir ceros; si ambos vacíos → "sin carga de cliente" en Pedidos / "sin carga" en Total).
  - Orden de filas: `pctTotal()` desc, empates por nombre.
- [ ] **Paso 3: Suite + commit (repo RAÍZ)** — `mvn -q test` verde (la ventana no tiene test; validación en smoke).

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): ventana de carga v2 — toggle pedidos/total, barra hecho+pendiente y colores por nivel"
```

---

### Tarea 5: Cliente — modal con los dos porcentajes

**Files:**
- Modify: `gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/PendientesSuperTecnicoController.java` (`etiquetaConCarga` y sus 3 puntos de render)

**Interfaces:**
- Consumes: `cargaDiaPedidos`/`cargaDiaTotal` (T3), colores por nivel (texto oscuro) y puntos de identidad.

- [ ] **Paso 1:** Los 3 puntos de render del modal (converters de `cbTecTop`/`cbTecDet` y el CheckBox de "Técnicos a asignar") hoy pintan TEXTO plano vía `etiquetaConCarga`. Con puntos de color hacen falta **nodos**: donde el punto de render admita `graphic` (ListCell del ComboBox y CheckBox → `setGraphic`), construir un `HBox(4)` con: `Label nombre` (tinta) + `Label "●"` violeta + `Label pct pedidos` (teñido por su nivel) + `Label "●"` azul + `Label pct total` (teñido por su nivel), vía un helper nuevo:

```java
/** "Nombre (●62% · ●85%)" como nodos: punto de identidad fijo + % teñido por su nivel. */
private javafx.scene.layout.HBox etiquetaConCargaNodo(Tecnico t) { ... }
```

(implementarlo completo: pct de `cargaDiaPedidos`/`cargaDiaTotal` con `getOrDefault`→0; color del texto por nivel con el mismo helper de T4 — extraer `private static String colorTextoNivel(double pct)`). Donde SOLO quepa texto (el buttonCell del ComboBox vía StringConverter), degradar honestamente a `"Nombre (P62% · T85%)"` sin color, con `etiquetaConCarga` reescrita a ese formato — documentar en el código por qué.

- [ ] **Paso 2: Suite + commit (repo RAÍZ)** — `mvn -q test` verde.

```bash
git add gestion-reparaciones-cliente/src && git commit -m "feat(asignaciones): porcentajes pedidos/total con puntos de identidad en el modal de asignar"
```

---

### Tarea 6: Cierre — suites, smoke y merge con OK

- [ ] **Paso 1:** Suites finales ambos repos + `mvn -q -DskipTests package` en servidor.
- [ ] **Paso 2: PARADA usuario.** Desplegar **servidor primero** (endpoint nuevo; sin ALTER) → smoke con el cliente de la rama:
  1. Ventana: toggle Pedidos|Total (Pedidos default), punto violeta/azul, barra dos tramos (hecho sólido + pendiente claro), colores por nivel en barra+número sincronizados (azul/ámbar/rojo con texto oscuro), satura a 100 con número ">100%", "sin jornada hoy" si se prueba en fin de semana, hover con unidades, tooltip con desglose hecho/pendiente.
  2. Completar una asignación → recargar → su fracción salta del tramo pendiente al hecho.
  3. Modal: "(●P% · ●T%)" con niveles correctos; buttonCell degradado a texto legible.
  4. Verificar el corte de medianoche Madrid: las completadas de ayer NO aparecen en "hecho hoy".
- [ ] **Paso 3:** Review final de rama (requesting-code-review) → presentar al usuario → merge/push SOLO con su OK (¿tag?) → **reanudar F2a en T10**.

## Self-review (hecho al escribir el plan)

- Spec §1 (modelo fracciones, hecho+pendiente, >100%) → T2/T4. §2 (jornadas, escalado, sin jornada) → T2 (constantes+tests) y T4 (UI). §3 (toggle solo ventana, Pedidos default, puntos identidad, modal sin toggle) → T4/T5. §4 (colores sincronizados, dos tramos, hover unidades, jubilar % relativo) → T2 (borrar porcentajes) + T4. §5 (tope normales de la query del usuario) → Restricciones globales + T2 (NEEDS_CONTEXT si falta). §6 (constantes documentadas, endpoint servidor) → T1/T2. §7 (secuencia) → T6.
- Placeholders: el valor de `TOPE_NORMALES_9H` es un dato pendiente del usuario por diseño — el plan lo hace explícito con la regla NEEDS_CONTEXT (no es un TBD del plan sino una entrada externa que el controlador inyecta en el encargo de la T2).
- Tipos consistentes: `DiaTecnico`/`calcularDia`/`formatearPct` idénticos en T2/T3/T4/T5; `cerradasHoy`/`cargaDiaPedidos`/`cargaDiaTotal` definidos en T3 y consumidos en T4/T5.
