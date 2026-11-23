# Pedidos 0.9.42: «Cancelar móvil» con botón y confirmación — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cancelar una fila de Pedidos deja de ser una marca del desplegable y pasa a un botón del panel con confirmación; cancelar quita «Falta por enviar» y quitar la cancelación comprueba el IMEI duplicado.

**Architecture:** En la BD nada cambia de forma (`MARCA = 'CANCELADO'`). El servidor añade dos reglas dentro de las transacciones que ya existen (`FilasDAO.ponerMarca` y `FilasDAO.ponerFaltaEnviar`) y dos 409 en `FilasController`. La web saca «Cancelado» del desplegable y añade `CancelarMovil.tsx` (botón + confirmación para cancelar y para quitar) en el panel de la fila.

**Tech Stack:** Java 17 + Spring Boot 3.3 + JdbcTemplate (MariaDB), JUnit 5 + Mockito; React + TypeScript + TanStack Query, Vitest + Testing Library + MSW, Playwright (E2E en preprod).

**Spec:** `docs/superpowers/specs/2026-11-23-pedidos-cancelar-movil-design.md` · **Prototipo:** `docs/superpowers/specs/assets/2026-11-23-pedidos-cancelar-boton-prototipo.html`

## Global Constraints

- Versión **0.9.42** (servidor + web). La web está en 0.9.41 (`package.json`); el servidor no lleva número propio (pom `1.0-SNAPSHOT`).
- Ramas `dev_pedidos_cancelar` en servidor y web, en worktrees `C:/Users/dev/Documents/wt/servidor-pedidos-cancelar` y `C:/Users/dev/Documents/wt/web-pedidos-cancelar`, desde `origin/main` (hacer `git fetch` y comparar antes: hay otras sesiones trabajando).
- Commits sin `Co-Authored-By` (norma del usuario). **Nada de merge, push, tag ni despliegue sin OK explícito del usuario.**
- Maven desde Bash: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"`.
- Textos exactos (de la spec):
  - Botón: «Cancelar móvil». Confirmación: título «¿Cancelar este móvil?», botones «Volver» y «Cancelar el móvil».
  - Quitar: botón «Quitar cancelación». Confirmación: título «¿Quitar la cancelación?», botones «Volver» y «Quitar cancelación».
  - 409 falta en cancelada: «Este móvil está cancelado: quita la cancelación antes de marcar «Falta por enviar»».
  - 409 duplicado: el `ImeiDuplicadoException.mensaje()` de siempre («Este IMEI ya está en el pedido X (fila del dd/MM)»).
  - Apunte del historial al cancelar con falta: tipo `YA_ENVIADO`, detalle `al cancelar`; la web lo pinta «Quitada «Falta por enviar» al cancelar».
- Nota de la cancelación: opcional, máx. 200 (`MAX_NOTA` de `EditorMarca.tsx`).
- Datos de prueba siempre inventados (repos públicos).

## Mapa de ficheros

Servidor (`gestion-reparaciones-servidor/`, en el worktree):
- Modify `src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java` — `SQL_FILA_MARCA_BLOQUEADA`, `SQL_FILA_FALTA_BLOQUEADA`, `Resultado.CANCELADA_NO_MARCABLE`, `comprobarDuplicado`, `ponerImei`, `ponerMarca`, `ponerFaltaEnviar`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/FilasController.java` — 409 del duplicado en `ponerMarca`, 409 `MSG_FALTA_CANCELADA` en `ponerFaltaEnviar`.
- Modify `src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java` — javadoc de `YA_ENVIADO` con detalle «al cancelar».
- Create `sql/migracion-cancelado-sin-falta.sql`.
- Test `src/test/java/com/reparaciones/servidor/pedidos/MarcaDAOTest.java`, `FaltaEnviarDAOTest.java`, `MarcaControllerTest.java`, `FaltaEnviarControllerTest.java`.

Web (`gestion-reparaciones-web/`, en el worktree):
- Modify `src/modules/telefonos/pedidos/filas.ts` — `MARCAS_DESPLEGABLE`.
- Modify `src/modules/telefonos/pedidos/EditorMarca.tsx` — usa `MARCAS_DESPLEGABLE`.
- Modify `src/modules/telefonos/pedidos/CeldaImei.tsx` — texto bajo el editor; en cancelada, aviso en vez del editor.
- Modify `src/modules/telefonos/pedidos/historial.ts` — `YA_ENVIADO` «al cancelar».
- Create `src/modules/telefonos/pedidos/CancelarMovil.tsx` — `BotonCancelarMovil` y `BotonQuitarCancelacion` con su confirmación.
- Modify `src/modules/telefonos/pedidos/PanelFila.tsx` — `SeccionMarca` y `MarcaFalta`.
- Modify `src/modules/telefonos/pedidos/AyudaPedidos.tsx` — la línea de «Cancelado» y la de cómo se eligen las marcas.
- Create `src/modules/telefonos/pedidos/PedidosCancelar.test.tsx`; modify `PedidosMarcas.test.tsx`, `PedidosAyuda.test.tsx` si mira esos textos.
- Modify `tests/e2e/pedidos-marcas.spec.ts` (test 4: cancelar por el panel).
- Modify `package.json`, `package-lock.json` (0.9.42), `CHANGELOG.md`.

Raíz (`ProgramaReparaciones/`):
- Modify `docs/modulos/pedidos.md` §3 y §4.

---

### Task 0: Ramas y worktrees

**Files:** ninguno.

- [ ] **Step 1: Traer origin y comparar**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git -C gestion-reparaciones-servidor fetch -q origin && git -C gestion-reparaciones-servidor status -sb | head -1
git -C gestion-reparaciones-web fetch -q origin && git -C gestion-reparaciones-web status -sb | head -1
```
Expected: `## main...origin/main` sin `ahead`/`behind` en los dos. Si hay `behind`, avisar al usuario antes de seguir (otra sesión subió algo).

- [ ] **Step 2: Crear los worktrees desde origin/main**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git -C gestion-reparaciones-servidor worktree add -b dev_pedidos_cancelar C:/Users/dev/Documents/wt/servidor-pedidos-cancelar origin/main
git -C gestion-reparaciones-web worktree add -b dev_pedidos_cancelar C:/Users/dev/Documents/wt/web-pedidos-cancelar origin/main
cd C:/Users/dev/Documents/wt/web-pedidos-cancelar && npm ci --silent
```
Expected: dos worktrees nuevos en `dev_pedidos_cancelar`; `npm ci` sin errores.

---

### Task 1: Servidor — `ponerMarca`: cancelar quita la falta y salir de cancelado comprueba el duplicado

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java` (`SQL_FILA_MARCA_BLOQUEADA` ~línea 82, `ponerImei` ~334-370, `BloqueadaMarca` y `ponerMarca` ~401-427)
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java` (javadoc)
- Test: `src/test/java/com/reparaciones/servidor/pedidos/MarcaDAOTest.java`

**Interfaces:**
- Produces: `FilasDAO.ponerMarca(int, String, String, String, Integer, LocalDateTime)` puede lanzar `ImeiDuplicadoException` (Task 2 la convierte en 409). Método privado `comprobarDuplicado(String imei, int idFila)`.

- [ ] **Step 1: Tests que fallan (al final de `MarcaDAOTest`, antes de la `}` de la clase)**

Añadir imports si faltan: `java.time.LocalDate` no hace falta; sí `static org.junit.jupiter.api.Assertions.*` (ya está). Añadir constantes y ayudantes junto a los de arriba:

```java
    private static final String QUITAR_FALTA = "UPDATE Pedido_fila SET FALTA_ENVIAR_EN = NULL, FALTA_ENVIAR_USU = NULL WHERE ID_FILA = ?";
    private static final String IMEI = "350000000000001";

    /** La fila bloqueada con su marca, su IMEI y si tiene «falta por enviar» (ShippyPro, sin nota). */
    private void filaCompleta(String marca, String imei, boolean conFalta) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_MARCA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("ORIGEN")).thenReturn("SHIPPYPRO");
            when(rs.getString("MARCA")).thenReturn(marca);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getTimestamp("FALTA_ENVIAR_EN")).thenReturn(conFalta ? Timestamp.valueOf(AHORA.minusDays(1)) : null);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    /** La consulta del duplicado devuelve otra fila viva con el IMEI (pedido 37400 de webs, del 20/11). */
    private void otraFilaConElImei() {
        when(jdbc.query(eq(FilasDAO.SQL_DUPLICADO), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("PEDIDO")).thenReturn("37400");
            when(rs.getDate("FECHA")).thenReturn(java.sql.Date.valueOf("2026-11-20"));
            when(rs.getString("CANAL")).thenReturn("PARTICULARES");
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }
```

Y los tests:

```java
    @Test void elBloqueoDeLaMarcaLeeTambienElImeiYLaFalta() {
        assertTrue(FilasDAO.SQL_FILA_MARCA_BLOQUEADA.contains("IMEI"));
        assertTrue(FilasDAO.SQL_FILA_MARCA_BLOQUEADA.contains("FALTA_ENVIAR_EN"));
        assertTrue(FilasDAO.SQL_FILA_MARCA_BLOQUEADA.endsWith("FOR UPDATE"));
    }

    @Test void cancelarUnaFilaConFaltaLaQuitaYApuntaYaEnviadoAlCancelar() {
        filaCompleta(null, IMEI, true);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "CANCELADO", "no cabe", "", 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).update(UPD, "CANCELADO", "no cabe", T_AHORA, 9, 5);
        orden.verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA", "CANCELADO — no cabe");
        orden.verify(jdbc).update(QUITAR_FALTA, 5);
        orden.verify(jdbc).update(INS, 5, 9, T_AHORA, "YA_ENVIADO", "al cancelar");
    }

    @Test void cancelarSinFaltaNoTocaLaFalta() {
        filaCompleta(null, IMEI, false);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "CANCELADO", null, "", 9, AHORA));
        verify(jdbc, never()).update(QUITAR_FALTA, 5);
        verify(jdbc, never()).update(eq(INS), eq(5), any(), any(), eq("YA_ENVIADO"), any());
    }

    @Test void otraMarcaEnUnaFilaConFaltaNoLaQuita() {
        filaCompleta(null, IMEI, true);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "EN_REVISION", null, "", 9, AHORA));
        verify(jdbc, never()).update(QUITAR_FALTA, 5);
    }

    @Test void quitarLaCancelacionConElImeiEnOtraFilaLanzaDuplicadoYNoEscribe() {
        filaCompleta("CANCELADO", IMEI, false);
        otraFilaConElImei();
        ImeiDuplicadoException e = assertThrows(ImeiDuplicadoException.class,
                () -> dao.ponerMarca(5, null, null, "CANCELADO", 9, AHORA));
        assertEquals("Este IMEI ya está en el pedido 37400 (fila del 20/11)", e.mensaje());
        verify(jdbc).query(eq(FilasDAO.SQL_DUPLICADO), any(RowMapper.class), eq(IMEI), eq(5));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void pasarDeCanceladoAOtraMarcaTambienCompruebaElDuplicado() {
        filaCompleta("CANCELADO", IMEI, false);
        otraFilaConElImei();
        assertThrows(ImeiDuplicadoException.class, () -> dao.ponerMarca(5, "EN_REVISION", null, "CANCELADO", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void quitarLaCancelacionSinDuplicadoQuitaLaMarca() {
        filaCompleta("CANCELADO", IMEI, false);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, null, null, "CANCELADO", 9, AHORA));
        verify(jdbc).query(eq(FilasDAO.SQL_DUPLICADO), any(RowMapper.class), eq(IMEI), eq(5));
        verify(jdbc).update(UPD, null, null, null, null, 5);
        verify(jdbc).update(INS, 5, 9, T_AHORA, "MARCA_QUITADA", "CANCELADO");
    }

    @Test void quitarLaCancelacionSinImeiNoConsultaElDuplicado() {
        filaCompleta("CANCELADO", null, false);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, null, null, "CANCELADO", 9, AHORA));
        verify(jdbc, never()).query(eq(FilasDAO.SQL_DUPLICADO), any(RowMapper.class), any(Object[].class));
    }

    @Test void cambiarSoloLaNotaDeUnaCanceladaNoConsultaElDuplicado() {
        filaCompleta("CANCELADO", IMEI, false);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerMarca(5, "CANCELADO", "otra nota", "CANCELADO", 9, AHORA));
        verify(jdbc, never()).query(eq(FilasDAO.SQL_DUPLICADO), any(RowMapper.class), any(Object[].class));
    }
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH="$JAVA_HOME/bin:/c/Users/dev/tools/apache-maven-3.9.16/bin:$PATH"
cd C:/Users/dev/Documents/wt/servidor-pedidos-cancelar && mvn -q test -Dtest=MarcaDAOTest
```
Expected: FAIL en `elBloqueoDeLaMarcaLeeTambienElImeiYLaFalta`, `cancelarUnaFilaConFalta…`, `quitarLaCancelacionCon…`, `pasarDeCancelado…`, `quitarLaCancelacionSinDuplicado…` (no consulta el duplicado). Los demás pueden pasar ya.

- [ ] **Step 3: Implementar en `FilasDAO.java`**

3a. Cambiar la constante (línea ~82):

```java
    static final String SQL_FILA_MARCA_BLOQUEADA = "SELECT ORIGEN, MARCA, MARCA_NOTA, IMEI, FALTA_ENVIAR_EN FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
```

3b. Sacar la consulta del duplicado de `ponerImei` a un método (ponerlo justo antes de `ponerImei`) y usarlo:

```java
    /** Lanza {@link ImeiDuplicadoException} (sin escribir nada) si el IMEI esta en otra fila que cuenta segun {@link #SQL_DUPLICADO}. */
    private void comprobarDuplicado(String imei, int idFila) {
        List<ImeiDuplicadoException> otras = jdbc.query(SQL_DUPLICADO, (rs, n) -> {
            String c = rs.getString("CANAL");
            Canal canal = c == null ? null : Canal.valueOf(c);
            return new ImeiDuplicadoException(rs.getString("PEDIDO"), rs.getDate("FECHA").toLocalDate(), canal,
                    canal != null && canal.esAmazon() && rs.getTimestamp("ASOCIADA_EN") == null);
        }, imei, idFila);
        if (!otras.isEmpty()) throw otras.get(0);
    }
```

En `ponerImei`, sustituir el bloque `if (nuevo != null) { List<ImeiDuplicadoException> otras = …; if (!otras.isEmpty()) throw otras.get(0); }` por:

```java
        if (nuevo != null) comprobarDuplicado(nuevo, idFila);
```

3c. `BloqueadaMarca` y `ponerMarca` completos:

```java
    private record BloqueadaMarca(String origen, String marca, String nota, String imei, boolean conFalta) {}

    /** Pone, cambia o quita (marca nula) la marca de una fila de ShippyPro y la apunta en el historial: MARCA (detalle «MARCA» o «MARCA — nota»)
     *  al poner o cambiar (tambien si solo cambia la nota), MARCA_QUITADA (detalle = la marca que tenia) al quitar. Misma transaccion que
     *  {@link #ponerImei}: READ COMMITTED y la fila bloqueada al leerla. Orden: NO_EXISTE; CERRADA (historico); CAMBIADA ({@code marcaAnterior}
     *  no nula y distinta de la actual; nula cuenta como ""); salir de CANCELADO (a sin marca o a otra marca) con IMEI comprueba el duplicado
     *  como {@link #ponerImei} y lanza {@link ImeiDuplicadoException} sin escribir; SIN_CAMBIO (misma marca y misma nota, o quitar sin marca:
     *  no escribe ni apunta); si no, PUESTO. Poner CANCELADO en una fila con «falta por enviar» la quita y apunta YA_ENVIADO «al cancelar»
     *  (spec 2026-11-23). La nota ya viene validada y recortada (nula = sin nota); se ignora al quitar. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerMarca(int idFila, String marca, String nota, String marcaAnterior, Integer idUsu, LocalDateTime ahora) {
        List<BloqueadaMarca> filas = jdbc.query(SQL_FILA_MARCA_BLOQUEADA, (rs, n) -> new BloqueadaMarca(rs.getString("ORIGEN"),
                rs.getString("MARCA"), rs.getString("MARCA_NOTA"), rs.getString("IMEI"), rs.getTimestamp("FALTA_ENVIAR_EN") != null), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        BloqueadaMarca f = filas.get(0);
        if ("HISTORICO".equals(f.origen())) return Resultado.CERRADA;
        if (marcaAnterior != null && !marcaAnterior.equals(f.marca() == null ? "" : f.marca())) return Resultado.CAMBIADA;
        String cancelado = Marca.CANCELADO.name();
        // Mientras estaba cancelada su IMEI estaba libre: al quitar la cancelacion no puede quedar en dos filas vivas.
        if (cancelado.equals(f.marca()) && !cancelado.equals(marca) && f.imei() != null && !f.imei().isBlank())
            comprobarDuplicado(f.imei().strip(), idFila);
        Timestamp t = Timestamp.valueOf(ahora);
        if (marca == null) {
            if (f.marca() == null) return Resultado.SIN_CAMBIO;
            jdbc.update(SQL_PONER_MARCA, null, null, null, null, idFila);
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.MARCA_QUITADA, f.marca());
            return Resultado.PUESTO;
        }
        if (marca.equals(f.marca()) && java.util.Objects.equals(nota, f.nota())) return Resultado.SIN_CAMBIO;
        jdbc.update(SQL_PONER_MARCA, marca, nota, t, idUsu, idFila);
        jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.MARCA, nota == null ? marca : marca + SEP_DETALLE + nota);
        // Cancelado y «falta por enviar» no pueden ir juntos: cancelar se la come (no vuelve al quitar la cancelacion).
        if (cancelado.equals(marca) && f.conFalta()) {
            jdbc.update(SQL_QUITAR_FALTA, idFila);
            jdbc.update(SQL_CAMBIO_DETALLE, idFila, idUsu, t, CambioImei.YA_ENVIADO, "al cancelar");
        }
        return Resultado.PUESTO;
    }
```

3d. En `CambioImei.java`, en el javadoc de la clase, cambiar `YA_ENVIADO (se quito esa marca)` por `YA_ENVIADO (se quito esa marca; detalle «al cancelar» si la quito el ERP al cancelar la fila)` y en la lista de detalles añadir `YA_ENVIADO (nulo, o "al cancelar")`. El comentario del constructor corto (`Un apunte sin detalle (IMEI, FALTA_ENVIAR o YA_ENVIADO)`) se deja.

- [ ] **Step 4: Ejecutar los tests de marcas, falta, duplicado y vendidos**

```bash
mvn -q test -Dtest='MarcaDAOTest,FilasDAOTest,FaltaEnviarDAOTest'
```
Expected: PASS (los tests viejos de `MarcaDAOTest` siguen bien: su ayudante `fila` deja IMEI y falta a nulo).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java src/main/java/com/reparaciones/servidor/pedidos/CambioImei.java src/test/java/com/reparaciones/servidor/pedidos/MarcaDAOTest.java
git commit -m "feat(pedidos): cancelar quita «falta por enviar» y quitar la cancelacion comprueba el IMEI duplicado (0.9.42)"
```

---

### Task 2: Servidor — «falta por enviar» en una cancelada y los dos 409

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java` (`SQL_FILA_FALTA_BLOQUEADA` ~línea 86, enum `Resultado` ~322, `BloqueadaFalta` y `ponerFaltaEnviar` ~429-448)
- Modify: `src/main/java/com/reparaciones/servidor/pedidos/FilasController.java` (constantes ~41-49, `ponerFaltaEnviar` ~139-155, `ponerMarca` ~167-189)
- Test: `FaltaEnviarDAOTest.java`, `FaltaEnviarControllerTest.java`, `MarcaControllerTest.java`

**Interfaces:**
- Consumes: `ponerMarca` puede lanzar `ImeiDuplicadoException` (Task 1).
- Produces: `FilasDAO.Resultado.CANCELADA_NO_MARCABLE`; `FilasController.MSG_FALTA_CANCELADA`.

- [ ] **Step 1: Tests que fallan**

En `FaltaEnviarDAOTest`, ayudante y tests:

```java
    /** Fila web con etiqueta generada, sin falta, marcada CANCELADO (o con falta y cancelada, datos viejos). */
    private void filaCancelada(LocalDateTime falta) {
        when(jdbc.query(eq(FilasDAO.SQL_FILA_FALTA_BLOQUEADA), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("ORIGEN")).thenReturn("SHIPPYPRO");
            when(rs.getString("CANAL")).thenReturn("PARTICULARES");
            when(rs.getTimestamp("ENVIO_FECHA")).thenReturn(Timestamp.valueOf(ENVIO));
            when(rs.getTimestamp("FALTA_ENVIAR_EN")).thenReturn(falta == null ? null : Timestamp.valueOf(falta));
            when(rs.getString("MARCA")).thenReturn("CANCELADO");
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void elBloqueoDeLaFaltaLeeTambienLaMarca() {
        assertTrue(FilasDAO.SQL_FILA_FALTA_BLOQUEADA.contains("MARCA"));
    }

    @Test void marcarFaltaEnUnaCanceladaEsCanceladaNoMarcableYNoEscribe() {
        filaCancelada(null);
        assertEquals(FilasDAO.Resultado.CANCELADA_NO_MARCABLE, dao.ponerFaltaEnviar(5, true, 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void quitarLaFaltaDeUnaCanceladaSiSePuede() {
        filaCancelada(ENVIO);
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerFaltaEnviar(5, false, 9, AHORA));
        verify(jdbc).update(UPD_FALTA, null, null, 5);
    }
```

En `FaltaEnviarControllerTest` (junto a `noMarcableEsUn409ConElMensajeParaElUsuario`):

```java
    @Test void canceladaEsUn409ConSuMensaje() {
        when(dao.porId(5)).thenReturn(Optional.of(fila("SHIPPYPRO")));
        when(dao.ponerFaltaEnviar(anyInt(), anyBoolean(), any(), any())).thenReturn(FilasDAO.Resultado.CANCELADA_NO_MARCABLE);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerFaltaEnviar(5, new PeticionFaltaEnviar(true), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("Este móvil está cancelado: quita la cancelación antes de marcar «Falta por enviar»", e.getReason());
        verify(vista, never()).fila(anyInt());
    }
```

En `MarcaControllerTest`:

```java
    @Test void elImeiDuplicadoAlQuitarLaCancelacionEsUn409ConElMensaje() {
        filaExiste();
        when(dao.ponerMarca(5, null, null, "CANCELADO", 7, AHORA))
                .thenThrow(new ImeiDuplicadoException("37400", LocalDate.of(2026, 11, 20), Canal.PARTICULARES, false));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.ponerMarca(5, new PeticionMarca(null, null, "CANCELADO"), super_, null));
        assertEquals(409, e.getStatusCode().value());
        assertEquals("Este IMEI ya está en el pedido 37400 (fila del 20/11)", e.getReason());
        verify(vista, never()).fila(anyInt());
    }
```
(Comprobar que `MarcaControllerTest` ya importa `LocalDate` y `ResponseStatusException`; si no, añadirlos.)

- [ ] **Step 2: Ejecutarlos y ver que fallan**

```bash
mvn -q test -Dtest='FaltaEnviarDAOTest,FaltaEnviarControllerTest,MarcaControllerTest'
```
Expected: no compila (`CANCELADA_NO_MARCABLE` no existe). Es el fallo esperado.

- [ ] **Step 3: Implementar**

`FilasDAO.java`:

```java
    static final String SQL_FILA_FALTA_BLOQUEADA = "SELECT ORIGEN, CANAL, ENVIO_FECHA, FALTA_ENVIAR_EN, MARCA FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
```

En el enum `Resultado`, después de `NO_MARCABLE` (cambiar `NO_MARCABLE }` por):

```java
        NO_MARCABLE,
        /** Solo de {@link #ponerFaltaEnviar}: la fila esta cancelada; hay que quitar la cancelacion antes de marcar (spec 2026-11-23). */
        CANCELADA_NO_MARCABLE }
```

`BloqueadaFalta` y `ponerFaltaEnviar`:

```java
    private record BloqueadaFalta(String origen, String canal, boolean conEtiqueta, LocalDateTime marca, boolean cancelada) {}

    /** Pone ({@code falta}) o quita la marca «falta enviar» de una fila y la apunta en el historial (FALTA_ENVIAR o YA_ENVIADO) con el usuario
     *  y la hora. Misma transaccion que {@link #ponerImei}: READ COMMITTED y la fila bloqueada al leerla; todo se decide con el bloqueo puesto.
     *  Orden: NO_EXISTE; CERRADA (historico); SIN_CAMBIO (ya marcada y se marca, o sin marca y se desmarca); CANCELADA_NO_MARCABLE (solo al
     *  poner: la fila tiene la marca CANCELADO); NO_MARCABLE (solo al poner: la fila debe ser de ShippyPro, de un canal web y con la etiqueta
     *  generada, ENVIO_FECHA no nula); si no, PUESTO. Quitar la marca no exige nada mas. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerFaltaEnviar(int idFila, boolean falta, Integer idUsu, LocalDateTime ahora) {
        List<BloqueadaFalta> filas = jdbc.query(SQL_FILA_FALTA_BLOQUEADA, (rs, n) -> new BloqueadaFalta(rs.getString("ORIGEN"),
                rs.getString("CANAL"), rs.getTimestamp("ENVIO_FECHA") != null, hora(rs, "FALTA_ENVIAR_EN"),
                Marca.CANCELADO.name().equals(rs.getString("MARCA"))), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        BloqueadaFalta f = filas.get(0);
        if ("HISTORICO".equals(f.origen())) return Resultado.CERRADA;
        if (falta == (f.marca() != null)) return Resultado.SIN_CAMBIO;
        if (falta && f.cancelada()) return Resultado.CANCELADA_NO_MARCABLE;
        if (falta && !(f.conEtiqueta() && "SHIPPYPRO".equals(f.origen()) && esWeb(f.canal()))) return Resultado.NO_MARCABLE;
        jdbc.update(SQL_PONER_FALTA, falta ? Timestamp.valueOf(ahora) : null, falta ? idUsu : null, idFila);
        jdbc.update(SQL_CAMBIO_TIPO, idFila, idUsu, Timestamp.valueOf(ahora), falta ? CambioImei.FALTA_ENVIAR : CambioImei.YA_ENVIADO);
        return Resultado.PUESTO;
    }
```

`FilasController.java`, junto a `MSG_NO_MARCABLE`:

```java
    static final String MSG_FALTA_CANCELADA = "Este móvil está cancelado: quita la cancelación antes de marcar «Falta por enviar»";
```

En `ponerFaltaEnviar`, tras la línea de `NO_MARCABLE`:

```java
            if (r == FilasDAO.Resultado.CANCELADA_NO_MARCABLE) throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_FALTA_CANCELADA);
```

En `ponerMarca`, sustituir la línea `FilasDAO.Resultado r = dao.ponerMarca(…);` por:

```java
            FilasDAO.Resultado r;
            try {
                r = dao.ponerMarca(id, marca, notaFinal, anterior, principal.getIdUsu(), LocalDateTime.now(clock));
            } catch (ImeiDuplicadoException e) {
                // Quitar la cancelacion de una fila cuyo IMEI ya se puso en otra (spec 2026-11-23 §3).
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.mensaje());
            }
```

Y en su javadoc añadir al final: `409 con el mensaje del IMEI duplicado si se quita la cancelacion y el IMEI ya esta en otra fila.`

- [ ] **Step 4: Toda la batería del servidor (incluye el test de arranque `OpenApiContractTest`)**

```bash
mvn -q test
```
Expected: BUILD SUCCESS, 0 fallos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/pedidos/FilasDAO.java src/main/java/com/reparaciones/servidor/pedidos/FilasController.java src/test/java/com/reparaciones/servidor/pedidos/FaltaEnviarDAOTest.java src/test/java/com/reparaciones/servidor/pedidos/FaltaEnviarControllerTest.java src/test/java/com/reparaciones/servidor/pedidos/MarcaControllerTest.java
git commit -m "feat(pedidos): no se marca «falta por enviar» en una fila cancelada y el duplicado al quitar la cancelacion es un 409 (0.9.42)"
```

---

### Task 3: Servidor — migración de los datos viejos

**Files:**
- Create: `sql/migracion-cancelado-sin-falta.sql`

- [ ] **Step 1: Escribir la migración**

```sql
-- 0.9.42 (spec 2026-11-23-pedidos-cancelar-movil-design.md §4): «Cancelado» y «Falta por enviar» ya no van juntos.
-- Desde la 0.9.42 cancelar quita la falta; esto limpia las filas que ya tuvieran las dos. Idempotente: con 0 filas no cambia nada.
-- Sin apunte en el historial (se anota en docs/modulos/pedidos.md).
SELECT COUNT(*) AS canceladas_con_falta FROM Pedido_fila WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;
UPDATE Pedido_fila SET FALTA_ENVIAR_EN = NULL, FALTA_ENVIAR_USU = NULL WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;
SELECT COUNT(*) AS quedan FROM Pedido_fila WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;
```

- [ ] **Step 2: Commit**

```bash
git add sql/migracion-cancelado-sin-falta.sql
git commit -m "chore(pedidos): migracion que quita «falta por enviar» de las filas canceladas (0.9.42)"
```

---

### Task 4: Web — «Cancelado» fuera del desplegable y el historial «al cancelar»

**Files:**
- Modify: `src/modules/telefonos/pedidos/filas.ts` (~línea 69-76)
- Modify: `src/modules/telefonos/pedidos/EditorMarca.tsx` (import y `<select>`, ~líneas 7 y 58-62)
- Modify: `src/modules/telefonos/pedidos/CeldaImei.tsx` (~línea 231)
- Modify: `src/modules/telefonos/pedidos/historial.ts` (~línea 48)
- Test: `src/modules/telefonos/pedidos/PedidosMarcas.test.tsx`

**Interfaces:**
- Produces: `export const MARCAS_DESPLEGABLE: { valor: string; texto: string }[]` en `filas.ts` (las tres marcas sin CANCELADO). `MARCAS` no cambia (lo siguen usando `textoMarca` y el filtro «Con marca»).

- [ ] **Step 1: Tests que fallan (en `PedidosMarcas.test.tsx`)**

1a. El test `un 409 por marcaAnterior recarga…` elige `'CANCELADO'` en la celda: cambiarlo a `'EN_REVISION'` (línea ~164):

```tsx
    await user.selectOptions(d.getByRole('combobox', { name: 'Marca' }), 'EN_REVISION')
```

1b. Añadir dentro de `describe('Pedidos: la marca en la celda del IMEI', …)`:

```tsx
  it('el desplegable no ofrece «Cancelado» (se cancela desde el panel) y lo dice debajo', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    await user.click(celda('M-0001'))
    const d = within(await desplegable())
    const opciones = within(d.getByRole('combobox', { name: 'Marca' })).getAllByRole('option').map((o) => o.textContent)
    expect(opciones).toEqual(['Sin marca', 'En espera de stock', 'En revisión', 'Pendiente de color'])
    expect(d.getByText('Para cancelar el móvil, abre el panel de la fila.')).toBeInTheDocument()
  })

  it('en una fila cancelada la celda no ofrece marcas: se cambia desde el panel', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    await user.click(celda('M-0005'))
    const d = within(await desplegable())
    expect(d.queryByRole('combobox', { name: 'Marca' })).not.toBeInTheDocument()
    expect(d.getByText('Fila cancelada: la marca se cambia desde el panel («Quitar cancelación»).')).toBeInTheDocument()
  })
```

1c. Añadir dentro de `describe('Pedidos: la marca en el panel', …)`:

```tsx
  it('el desplegable del panel tampoco ofrece «Cancelado»', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'M-0001')
    const opciones = within(within(panel).getByRole('combobox', { name: 'Marca' })).getAllByRole('option').map((o) => o.textContent)
    expect(opciones).not.toContain('Cancelado')
  })

  it('el historial dice «Quitada «Falta por enviar» al cancelar»', async () => {
    const user = userEvent.setup()
    cambios = { '5': [{ usuario: 'Supertécnico 1', fecha: '2026-11-12T09:30:00', imeiAnterior: null, imeiNuevo: null, tipo: 'YA_ENVIADO', detalle: 'al cancelar' }] }
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'M-0005')
    expect(await within(panel).findByText('Quitada «Falta por enviar» al cancelar')).toBeInTheDocument()
  })
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

```bash
cd C:/Users/dev/Documents/wt/web-pedidos-cancelar && npx vitest run src/modules/telefonos/pedidos/PedidosMarcas.test.tsx
```
Expected: FAIL en los 4 tests nuevos.

- [ ] **Step 3: Implementar**

`filas.ts`, justo después de `MARCAS`:

```ts
/** Las marcas que se eligen en el desplegable (celda del IMEI y panel). «Cancelado» no: se pone con «Cancelar móvil» del panel, con confirmación. */
export const MARCAS_DESPLEGABLE = MARCAS.filter((m) => m.valor !== 'CANCELADO')
```

`EditorMarca.tsx`: import `import { MARCAS, MARCAS_DESPLEGABLE, pedidoVisible } from './filas'` y en el `<select>`:

```tsx
        {MARCAS_DESPLEGABLE.map((m) => <option key={m.valor} value={m.valor}>{m.texto}</option>)}
```
(La línea siguiente, la de la marca desconocida con `MARCAS.some`, se deja igual.) Actualizar el comentario del componente: «El selector de marca de una fila con su nota (sin «Cancelado», que va con su botón en el panel)…».

`CeldaImei.tsx`, sustituir `<EditorMarca fila={fila} onHecho={onCerrar} />` por:

```tsx
      {fila.marca === 'CANCELADO' ? (
        <p className="mt-2 border-t border-form-fila-brd pt-2 text-[11.5px] text-azul-gris">Fila cancelada: la marca se cambia desde el panel («Quitar cancelación»).</p>
      ) : (
        <>
          <EditorMarca fila={fila} onHecho={onCerrar} />
          <p className="mt-1 px-0.5 text-[11px] text-azul-gris">Para cancelar el móvil, abre el panel de la fila.</p>
        </>
      )}
```

`historial.ts`, en el bloque `YA_ENVIADO`, antes del `return cambio.usuario ? …`:

```ts
      // La quitó el ERP al cancelar la fila (cancelado y falta por enviar no van juntos).
      if (cambio.detalle === 'al cancelar') return { tipo: 'marca', clave: `i${i}`, cuando, conHora: true, texto: 'Quitada «Falta por enviar» al cancelar', quien: cambio.usuario ?? '' }
```

- [ ] **Step 4: Ejecutar los tests de Pedidos**

```bash
npx vitest run src/modules/telefonos/pedidos
```
Expected: PASS. Si algún otro test elegía `'CANCELADO'` en un desplegable, cambiarlo a otra marca (la búsqueda previa solo encontró el de 1a).

- [ ] **Step 5: Commit**

```bash
git add src/modules/telefonos/pedidos/filas.ts src/modules/telefonos/pedidos/EditorMarca.tsx src/modules/telefonos/pedidos/CeldaImei.tsx src/modules/telefonos/pedidos/historial.ts src/modules/telefonos/pedidos/PedidosMarcas.test.tsx
git commit -m "feat(pedidos): «Cancelado» sale del desplegable de marcas y el historial dice «al cancelar» (0.9.42)"
```

---

### Task 5: Web — «Cancelar móvil» y «Quitar cancelación» en el panel

**Files:**
- Create: `src/modules/telefonos/pedidos/CancelarMovil.tsx`
- Modify: `src/modules/telefonos/pedidos/PanelFila.tsx` (`MarcaFalta` ~87-106, `SeccionMarca` ~110-126)
- Test: Create `src/modules/telefonos/pedidos/PedidosCancelar.test.tsx`

**Interfaces:**
- Consumes: `usePonerMarca()` de `../api` (`mutateAsync({ idFila, marca, nota, marcaAnterior, clave })`), `MAX_NOTA` de `./EditorMarca`, `pedidoVisible`, `textoMarca` de `./filas`, `estadoPedidos` de `./estado`.
- Produces: `export function BotonCancelarMovil({ fila }: { fila: FilaVista })` y `export function BotonQuitarCancelacion({ fila }: { fila: FilaVista })`.

- [ ] **Step 1: Test que falla — `PedidosCancelar.test.tsx`**

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FilaVista } from '@/shared/api/client'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { maquetarJsdom } from '@/test/layoutJsdom'
import { estadoNsys, fila, fuente } from '../test/fabrica'
import { estadoPedidos } from './estado'
import { PedidosPage } from './PedidosPage'
import { congelarHoy } from '../test/hoy'

congelarHoy()

// «Cancelar móvil» y «Quitar cancelación» del panel (spec 2026-11-23), con datos inventados.
const ENVIADA = { estado: 'ENVIADA', envioFecha: '2026-11-09T10:30:00', tieneEtiqueta: true } as const
const MARCADA = { marcaEn: '2026-11-12T09:30:00', marcaPor: 'Supertécnico 1' } as const
const FILAS = [
  fila({ idFila: 1, pedido: 'C-0001', imei: '350000000000011', marca: 'EN_REVISION', ...MARCADA }),
  fila({ idFila: 2, canal: 'PARTICULARES', plataforma: 'Cliente Web A', pedido: 'C-0002', imei: '350000000000022', ...ENVIADA, faltaEnviarEn: '2026-11-10T09:05:00' }),
  fila({ idFila: 3, canal: 'PARTICULARES', plataforma: 'Cliente Web B', pedido: 'C-0003', imei: '350000000000033', ...ENVIADA, marca: 'CANCELADO', marcaNota: 'cliente lo anula', ...MARCADA }),
]

let puts: { id: string; cuerpo: { marca: string | null; nota: string | null; marcaAnterior: string | null } }[] = []

afterEach(() => { vi.restoreAllMocks() })
beforeEach(() => {
  puts = []
  estadoPedidos.reset()
  maquetarJsdom()
  server.use(
    http.get('*/api/pedidos/filas', () => HttpResponse.json({ filas: FILAS, hayMas: false })),
    http.get('*/api/pedidos/fuentes', () => HttpResponse.json([fuente({ fuente: 'SHIPPYPRO' })])),
    http.get('*/api/stock-telefonos/estado', () => HttpResponse.json(estadoNsys())),
    http.get('*/api/pedidos/filas/:id/cambios', () => HttpResponse.json([])),
    http.put('*/api/pedidos/filas/:id/marca', async ({ request, params }) => {
      const cuerpo = (await request.json()) as (typeof puts)[number]['cuerpo']
      puts.push({ id: String(params.id), cuerpo })
      const base = FILAS.find((f) => f.idFila === Number(params.id))!
      const cancelada = cuerpo.marca === 'CANCELADO'
      const vuelta: FilaVista = { ...base, marca: cuerpo.marca, marcaNota: cuerpo.marca ? cuerpo.nota : null,
        marcaEn: cuerpo.marca ? '2026-11-13T10:00:00' : null, marcaPor: cuerpo.marca ? 'Supertécnico 1' : null,
        faltaEnviarEn: cancelada ? null : base.faltaEnviarEn }
      return HttpResponse.json(vuelta)
    }),
  )
})

const abrir = () => renderConProviders(<PedidosPage />, { sesion: SESION_SUPER, ruta: '/telefonos/pedidos' })
const tr = (pedido: string) => screen.getAllByText(pedido)[0].closest('tr')!
const esperar = () => screen.findByRole('button', { name: `${FILAS.length} filas` })
const abrirPanel = async (user: ReturnType<typeof userEvent.setup>, pedido: string) => {
  await user.click(within(tr(pedido)).getByRole('button', { name: pedido }))
  return waitFor(() => {
    const paneles = screen.getAllByRole('region', { name: 'Detalle del pedido' }).filter((p) => within(p).queryAllByText(pedido).length > 0)
    expect(paneles).toHaveLength(1)
    return paneles[0]
  })
}

describe('Pedidos: cancelar el móvil desde el panel', () => {
  it('«Cancelar móvil» abre la confirmación; «Volver» no manda nada', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'C-0001')
    await user.click(within(panel).getByRole('button', { name: 'Cancelar móvil' }))
    const d = within(await screen.findByRole('dialog', { name: '¿Cancelar este móvil?' }))
    expect(d.getByText('Pedido C-0001, IMEI 350000000000011.')).toBeInTheDocument()
    expect(d.getByText('No saldrá en este pedido y su IMEI quedará libre para otro pedido.')).toBeInTheDocument()
    expect(d.getByText('Sustituye a la marca «En revisión».')).toBeInTheDocument()
    await user.click(d.getByRole('button', { name: 'Volver' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '¿Cancelar este móvil?' })).not.toBeInTheDocument())
    expect(puts).toHaveLength(0)
  })

  it('Escape cierra la confirmación sin mandar nada', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'C-0001')
    await user.click(within(panel).getByRole('button', { name: 'Cancelar móvil' }))
    await screen.findByRole('dialog', { name: '¿Cancelar este móvil?' })
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '¿Cancelar este móvil?' })).not.toBeInTheDocument())
    expect(puts).toHaveLength(0)
  })

  it('confirmar manda CANCELADO con la nota y la marca anterior; en una fila con falta avisa de que se quita', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'C-0002')
    await user.click(within(panel).getByRole('button', { name: 'Cancelar móvil' }))
    const d = within(await screen.findByRole('dialog', { name: '¿Cancelar este móvil?' }))
    expect(d.getByText('Se quitará también «Falta por enviar».')).toBeInTheDocument()
    await user.type(d.getByRole('textbox', { name: 'Nota de la cancelación' }), '  no cabe  ')
    await user.click(d.getByRole('button', { name: 'Cancelar el móvil' }))
    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0]).toEqual({ id: '2', cuerpo: { marca: 'CANCELADO', nota: 'no cabe', marcaAnterior: '' } })
    await waitFor(() => expect(within(panel).getByRole('button', { name: 'Quitar cancelación' })).toBeInTheDocument())
    expect(within(panel).queryByText(/Envío parcial/)).not.toBeInTheDocument()
  })

  it('una fila cancelada enseña quién y cuándo, sin desplegable ni «Falta por enviar»; «Quitar cancelación» confirma y manda marca null', async () => {
    const user = userEvent.setup()
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'C-0003')
    expect(within(panel).getByText('cliente lo anula')).toBeInTheDocument()
    expect(within(panel).getByText(/Cancelado por Supertécnico 1 el 12\/11\/2026 09:30/)).toBeInTheDocument()
    expect(within(panel).queryByRole('combobox', { name: 'Marca' })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: /Falta por enviar|Ya ha salido/ })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Cancelar móvil' })).not.toBeInTheDocument()
    await user.click(within(panel).getByRole('button', { name: 'Quitar cancelación' }))
    const d = within(await screen.findByRole('dialog', { name: '¿Quitar la cancelación?' }))
    expect(d.getByText('«Falta por enviar» no vuelve: si hace falta, márcala otra vez.')).toBeInTheDocument()
    await user.click(d.getByRole('button', { name: 'Quitar cancelación' }))
    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0]).toEqual({ id: '3', cuerpo: { marca: null, nota: null, marcaAnterior: 'CANCELADO' } })
  })

  it('un 409 del duplicado al quitar la cancelación sale en el aviso y la fila sigue cancelada', async () => {
    const user = userEvent.setup()
    server.use(http.put('*/api/pedidos/filas/:id/marca', () =>
      HttpResponse.json({ message: 'Este IMEI ya está en el pedido 37400 (fila del 20/11)' }, { status: 409 })))
    abrir()
    await esperar()
    const panel = await abrirPanel(user, 'C-0003')
    await user.click(within(panel).getByRole('button', { name: 'Quitar cancelación' }))
    const d = within(await screen.findByRole('dialog', { name: '¿Quitar la cancelación?' }))
    await user.click(d.getByRole('button', { name: 'Quitar cancelación' }))
    expect(await screen.findByText('Pedido C-0003: Este IMEI ya está en el pedido 37400 (fila del 20/11)')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Quitar cancelación' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

```bash
npx vitest run src/modules/telefonos/pedidos/PedidosCancelar.test.tsx
```
Expected: FAIL (no hay botón «Cancelar móvil»).

- [ ] **Step 3: Crear `CancelarMovil.tsx`**

```tsx
import { useState, type ReactNode } from 'react'
import type { FilaVista } from '@/shared/api/client'
import { StaleDataError, mensajeDeError } from '@/shared/api/errors'
import { crearClavesIdempotencia } from '@/shared/lib/clavesIdempotencia'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { usePonerMarca } from '../api'
import { MAX_NOTA } from './EditorMarca'
import { estadoPedidos } from './estado'
import { pedidoVisible, textoMarca } from './filas'

const BOTON = 'h-7 cursor-pointer rounded-md border bg-superficie px-2.5 text-[12px] font-semibold disabled:cursor-default disabled:opacity-60'
const BOTON_DIALOGO = 'h-8 cursor-pointer rounded-md border px-3 text-[12.5px] font-semibold disabled:cursor-default disabled:opacity-60'

/** Guarda la marca CANCELADO (o la quita) con la misma clave de idempotencia al reintentar. Un fallo deja el aviso arriba de la tabla
 *  como el editor de marcas (un 409, con el mensaje del servidor: otra persona cambió la marca o el IMEI ya está en otro pedido). */
function useGuardarCancelacion(fila: FilaVista) {
  const poner = usePonerMarca()
  const [claves] = useState(() => crearClavesIdempotencia())
  const guardar = (cancelar: boolean, nota: string | null, alAcabar: () => void) => {
    const cuerpo = cancelar
      ? { marca: 'CANCELADO', nota, marcaAnterior: fila.marca ?? '' }
      : { marca: null, nota: null, marcaAnterior: 'CANCELADO' }
    const operacion = `cancelar:${fila.idFila}`
    poner.mutateAsync({ idFila: fila.idFila, ...cuerpo, clave: claves.para(operacion, cuerpo) }).then(
      () => {
        claves.hecha(operacion)
        estadoPedidos.set((s) => (s.avisoImei === null ? s : { ...s, avisoImei: null }))
        alAcabar()
      },
      (e: unknown) => {
        const pedido = pedidoVisible(fila) ?? fila.idFila
        const aviso = e instanceof StaleDataError
          ? `Pedido ${pedido}: ${mensajeDeError(e)}`
          : `No se guardó la cancelación del pedido ${pedido}. Vuelve a intentarlo.`
        estadoPedidos.set((s) => ({ ...s, avisoImei: aviso }))
        alAcabar()
      },
    )
  }
  return { guardar, guardando: poner.isPending }
}

function Confirmacion({ abierto, titulo, children, textoAccion, peligro, guardando, onConfirmar, onVolver }: {
  abierto: boolean; titulo: string; children: ReactNode; textoAccion: string; peligro: boolean; guardando: boolean
  onConfirmar: () => void; onVolver: () => void
}) {
  return (
    <Dialog open={abierto} onOpenChange={(o) => { if (!o) onVolver() }}>
      <DialogContent className="max-w-[min(420px,calc(100%-2rem))] gap-2 p-5">
        <DialogHeader>
          <DialogTitle className="text-[16px] font-bold text-azul-medio">{titulo}</DialogTitle>
          <DialogDescription asChild><div className="text-[13px] text-azul-noche">{children}</div></DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex-row justify-end gap-2">
          <button type="button" onClick={onVolver} className={`${BOTON_DIALOGO} border-borde-input bg-superficie`}>Volver</button>
          <button type="button" disabled={guardando} onClick={onConfirmar}
            className={`${BOTON_DIALOGO} ${peligro ? 'border-rojo-accion bg-rojo-accion text-crema' : 'border-azul-noche bg-azul-noche text-crema'}`}>{textoAccion}</button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** «Cancelar móvil» (spec 2026-11-23): con confirmación y nota opcional. Pone la marca CANCELADO; el servidor quita «Falta por enviar». */
export function BotonCancelarMovil({ fila }: { fila: FilaVista }) {
  const [abierto, setAbierto] = useState(false)
  const [nota, setNota] = useState('')
  const { guardar, guardando } = useGuardarCancelacion(fila)
  const pedido = pedidoVisible(fila)
  const que = [pedido && `Pedido ${pedido}`, fila.imei && `IMEI ${fila.imei}`].filter(Boolean).join(', ')
  const abrir = () => { setNota(''); setAbierto(true) }
  return (
    <>
      <button type="button" onClick={abrir} className={`${BOTON} mt-2.5 border-[#e7a29b] text-texto-error`}>Cancelar móvil</button>
      <Confirmacion abierto={abierto} titulo="¿Cancelar este móvil?" textoAccion="Cancelar el móvil" peligro guardando={guardando}
        onVolver={() => setAbierto(false)}
        onConfirmar={() => guardar(true, nota.trim() || null, () => setAbierto(false))}>
        {que && <p className="m-0 mb-1.5">{que}.</p>}
        <p className="m-0 mb-1.5">{fila.imei ? 'No saldrá en este pedido y su IMEI quedará libre para otro pedido.' : 'No saldrá en este pedido.'}</p>
        {fila.faltaEnviarEn && <p className="m-0 mb-1.5 font-semibold text-pill-aviso-text">Se quitará también «Falta por enviar».</p>}
        {fila.marca && <p className="m-0 mb-1.5 text-azul-gris">Sustituye a la marca «{textoMarca(fila.marca)}».</p>}
        <input aria-label="Nota de la cancelación" placeholder="Nota (opcional): motivo de la cancelación" autoComplete="off"
          maxLength={MAX_NOTA} value={nota} onChange={(e) => setNota(e.target.value)}
          className="mt-1 h-8 w-full rounded-md border border-borde-input px-2 text-[12.5px]" />
      </Confirmacion>
    </>
  )
}

/** «Quitar cancelación»: con confirmación. Quita la marca CANCELADO; el servidor da 409 si el IMEI ya está en otra fila viva. */
export function BotonQuitarCancelacion({ fila }: { fila: FilaVista }) {
  const [abierto, setAbierto] = useState(false)
  const { guardar, guardando } = useGuardarCancelacion(fila)
  return (
    <>
      <button type="button" onClick={() => setAbierto(true)} className={`${BOTON} mt-2 border-borde-input`}>Quitar cancelación</button>
      <Confirmacion abierto={abierto} titulo="¿Quitar la cancelación?" textoAccion="Quitar cancelación" peligro={false} guardando={guardando}
        onVolver={() => setAbierto(false)}
        onConfirmar={() => guardar(false, null, () => setAbierto(false))}>
        <p className="m-0 mb-1.5">{`${pedidoVisible(fila) ? `Pedido ${pedidoVisible(fila)}. ` : ''}La fila vuelve a estar pendiente${fila.imei ? ' y su IMEI vuelve a contar como ocupado' : ''}.`}</p>
        <p className="m-0 text-azul-gris">«Falta por enviar» no vuelve: si hace falta, márcala otra vez.</p>
      </Confirmacion>
    </>
  )
}
```

Notas para quien implemente: comprobar que los tokens `text-texto-error`, `text-pill-aviso-text`, `bg-rojo-accion`, `text-crema`, `border-borde-input`, `text-azul-noche` existen (todos se usan ya en `ConfirmDialog.tsx`, `PanelFila.tsx` o `CeldaImei.tsx`); si alguno no existe, usar el equivalente de `ConfirmDialog.tsx`.

- [ ] **Step 4: Conectar en `PanelFila.tsx`**

Import: `import { BotonCancelarMovil, BotonQuitarCancelacion } from './CancelarMovil'`.

En `MarcaFalta`, después de las dos líneas de hooks (`useFaltaEnviar` y `useState`) y antes de `const marcada`:

```tsx
  // Una fila cancelada no lleva «Falta por enviar» (cancelar se la come; el servidor da 409).
  if (f.marca === 'CANCELADO') return null
```

`SeccionMarca` completo:

```tsx
/** La marca de la fila (En espera de stock, En revisión o Pendiente de color): cuál es, su nota, quién la puso y cuándo, el editor para
 *  cambiarla o quitarla y «Cancelar móvil». Una fila cancelada enseña la cancelación y solo «Quitar cancelación» (spec 2026-11-23). */
function SeccionMarca({ fila: f }: { fila: FilaVista }) {
  if (!f.editable && !f.marca) return null
  if (f.marca === 'CANCELADO') {
    return (
      <div className="mt-2 text-[12.5px]">
        <p className="m-0 flex flex-wrap items-baseline gap-1.5">
          <span className={`inline-block rounded-lg px-2 py-px text-[11px] font-bold ${colorMarca(f.marca)}`}>{textoMarca(f.marca)}</span>
          {f.marcaNota && <span className="min-w-0 font-semibold [overflow-wrap:anywhere]">{f.marcaNota}</span>}
        </p>
        <p className="m-0 mt-0.5 text-[11px] text-azul-gris">
          {f.marcaEn ? `Cancelado${f.marcaPor ? ` por ${f.marcaPor}` : ''} el ${diaHora(f.marcaEn)}. ` : ''}Este móvil no sale en este pedido y su IMEI está libre.
        </p>
        {f.editable && <BotonQuitarCancelacion fila={f} />}
      </div>
    )
  }
  return (
    <div className="mt-2 text-[12.5px]">
      {f.marca ? (
        <>
          <p className="m-0 flex flex-wrap items-baseline gap-1.5">
            <span className={`inline-block rounded-lg px-2 py-px text-[11px] font-bold ${colorMarca(f.marca)}`}>{textoMarca(f.marca)}</span>
            {f.marcaNota && <span className="min-w-0 font-semibold [overflow-wrap:anywhere]">{f.marcaNota}</span>}
          </p>
          {f.marcaEn && <p className="m-0 mt-0.5 text-[11px] text-azul-gris">Puesta{f.marcaPor ? ` por ${f.marcaPor}` : ''} el {diaHora(f.marcaEn)}</p>}
        </>
      ) : <p className="m-0 text-[12px] text-azul-gris">Sin marca.</p>}
      {f.editable && <EditorMarca key={`${f.idFila}:${f.marca}:${f.marcaNota}:${f.marcaEn}`} fila={f} />}
      {f.editable && <BotonCancelarMovil key={`cancelar${f.idFila}`} fila={f} />}
    </div>
  )
}
```

- [ ] **Step 5: Ejecutar todo lo de Pedidos**

```bash
npx vitest run src/modules/telefonos/pedidos
```
Expected: PASS (incluido `PedidosParcial.test.tsx`: sus filas no están canceladas). Si la línea de «Cancelado por …» no casa con el texto del test por el espacio, ajustar el test al texto real, no el componente.

- [ ] **Step 6: Commit**

```bash
git add src/modules/telefonos/pedidos/CancelarMovil.tsx src/modules/telefonos/pedidos/PanelFila.tsx src/modules/telefonos/pedidos/PedidosCancelar.test.tsx
git commit -m "feat(pedidos): «Cancelar móvil» y «Quitar cancelación» en el panel, con confirmación (0.9.42)"
```

---

### Task 6: Web — ayuda, E2E, versión y changelog

**Files:**
- Modify: `src/modules/telefonos/pedidos/AyudaPedidos.tsx` (~líneas 73-76)
- Modify: `src/modules/telefonos/pedidos/PedidosAyuda.test.tsx` (solo si comprueba esos textos)
- Modify: `tests/e2e/pedidos-marcas.spec.ts` (test 4, ~líneas 236-250)
- Modify: `package.json`, `package-lock.json`, `CHANGELOG.md`

- [ ] **Step 1: Ayuda**

Sustituir el texto de la `Linea` de «Cancelado» y la siguiente por:

```tsx
          <Linea muestra={<span className={cn(ETIQUETA, COLOR_ETIQUETA.CANCELADO)}>Cancelado</span>}>
            El cliente anula ese móvil y el resto del pedido sigue. Se pone con «Cancelar móvil» en el panel de la fila (pide confirmación) y se
            quita con «Quitar cancelación». Quita «Falta por enviar». No cuenta como pendiente, el lector no salta a ella y su IMEI queda libre.
          </Linea>
          <Linea>Las otras marcas se eligen en la celda del IMEI (debajo del campo) o en el panel de la fila y se guardan al elegirlas; la nota, con Enter o al salir de su campo. Se ven junto al estado de envío. Una fila con marca no va al bot de vendidos.</Linea>
```

Comprobar: `grep -n "Cancelado\|se eligen en la celda" src/modules/telefonos/pedidos/PedidosAyuda.test.tsx`; si mira el texto viejo, actualizarlo al nuevo.

- [ ] **Step 2: E2E — el test 4 cancela por el panel**

En `tests/e2e/pedidos-marcas.spec.ts`, añadir ayudantes tras `quitarMarca`:

```ts
async function abrirPanel(e: Elegida) {
  await celdaDe(e).getByRole('button', { name: e.pedido, exact: true }).click()
  const panel = page.getByRole('region', { name: 'Detalle del pedido' })
  await expect(panel).toBeVisible()
  return panel
}
async function cancelarPorElPanel(e: Elegida) {
  const panel = await abrirPanel(e)
  await panel.getByRole('button', { name: 'Cancelar móvil' }).click()
  const d = page.getByRole('dialog', { name: '¿Cancelar este móvil?' })
  await d.getByRole('button', { name: 'Cancelar el móvil' }).click()
  await expect.poll(async () => (await filaPorApi(e.id)).marca).toBe('CANCELADO')
}
async function quitarCancelacionPorElPanel(e: Elegida) {
  const panel = await abrirPanel(e)
  await panel.getByRole('button', { name: 'Quitar cancelación' }).click()
  const d = page.getByRole('dialog', { name: '¿Quitar la cancelación?' })
  await d.getByRole('button', { name: 'Quitar cancelación' }).click()
  await expect.poll(async () => (await filaPorApi(e.id)).marca).toBeNull()
}
```

Y en el test 4 cambiar `await ponerMarca(C, 'CANCELADO')` por `await cancelarPorElPanel(C)` y `await quitarMarca(C)` por `await quitarCancelacionPorElPanel(C)`. Cambiar su título a `'4. «Cancelar móvil» (panel, con confirmación): la celda de envío lo dice, «N pendientes» baja en uno, y al quitarla vuelve'` y la cabecera del fichero (línea 5) a «…las marcas de la fila (En espera de stock, «Cancelar móvil» del panel, filtro «Con marca», historial)…». El `afterAll` ya deja la fila sin marca por la API: no cambia.

Comprobar que compila: `npx tsc -p tsconfig.node.json --noEmit` si los E2E entran ahí; si no, `npx playwright test --list tests/e2e/pedidos-marcas.spec.ts` (solo lista, no ejecuta).

- [ ] **Step 3: Versión y changelog**

```bash
npm version 0.9.42 --no-git-tag-version
```
Expected: `v0.9.42`; cambian `package.json` y `package-lock.json`.

En `CHANGELOG.md`, encima de la entrada más reciente:

```markdown
## [0.9.42] - 2026-11-23 — Pedidos: cancelar un móvil pide confirmación

- En **Pedidos**, «Cancelado» ya no está en el desplegable de marcas: se cancela con el botón **«Cancelar móvil»** del panel de la fila, que pide confirmación y admite una nota. Así un clic de más ya no cancela un móvil.
- Una fila cancelada enseña quién la canceló y cuándo, y el botón **«Quitar cancelación»** (también con confirmación). Mientras está cancelada no se le puede poner otra marca ni «Falta por enviar».
- Cancelar un móvil marcado **«Falta por enviar»** le quita esa marca: las dos cosas no pueden ir juntas. Al quitar la cancelación no vuelve.
- Al quitar la cancelación, si su IMEI ya se ha puesto en otro pedido, sale el aviso «Este IMEI ya está en el pedido X» y la fila sigue cancelada.
- Servidor y web.
```

(Si la última entrada del changelog es la 0.9.39 porque la 0.9.40/0.9.41 no se apuntaron, no inventarlas: poner la 0.9.42 arriba igualmente.)

- [ ] **Step 4: Batería completa de la web**

```bash
npm run check
```
Expected: lint, typecheck y tests en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/telefonos/pedidos/AyudaPedidos.tsx src/modules/telefonos/pedidos/PedidosAyuda.test.tsx tests/e2e/pedidos-marcas.spec.ts package.json package-lock.json CHANGELOG.md
git commit -m "chore: version 0.9.42, ayuda, E2E y changelog de «Cancelar movil»"
```

---

### Task 7: Doc del módulo (raíz) y cierre de la rama

**Files:**
- Modify: `C:/Users/dev/Documents/ProgramaReparaciones/docs/modulos/pedidos.md` (§3 tabla de estados ~línea 79, marcas ~86, panel ~91; §4 IMEI duplicado ~97 y envío parcial ~106)

- [ ] **Step 1: Editar la doc**

- Tabla de estados: `| Cancelado | Puesto a mano con «Cancelar móvil» del panel: ese móvil no sale |`.
- Marcas: `**Marcas de la fila** (una como mucho, con nota): En espera de stock · En revisión · Pendiente de color, en el desplegable de la celda del IMEI y del panel. **Cancelado** va aparte: botón «Cancelar móvil» del panel, con confirmación y nota opcional; se deshace con «Quitar cancelación» (también con confirmación). Una fila cancelada no lleva otra marca ni «Falta por enviar».`
- Panel: añadir «Cancelar móvil / Quitar cancelación» a la lista.
- §4 IMEI duplicado, añadir: `Quitar la cancelación pasa la misma comprobación: si el IMEI ya está en otra fila que cuenta, 409 y la fila sigue cancelada.`
- §4 Envío parcial, añadir: `Cancelar la fila quita «Falta por enviar» (apunte «al cancelar» en el historial) y no se puede marcar en una cancelada (409). En el despliegue de la 0.9.42 la migración sql/migracion-cancelado-sin-falta.sql limpió las filas viejas que tenían las dos (sin apunte).`

- [ ] **Step 2: Commit en la raíz**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/modulos/pedidos.md docs/superpowers/plans/2026-11-23-v0942-pedidos-cancelar-movil.md docs/superpowers/specs/2026-11-23-pedidos-cancelar-movil-design.md
git commit -m "docs: Pedidos, «Cancelar movil» con confirmacion (0.9.42)"
```

- [ ] **Step 3: Parar y pedir OK**

No hacer merge, push, tag ni despliegue. Avisar al usuario de que la rama `dev_pedidos_cancelar` está lista en los dos repos y de los pasos siguientes, que decide él: revisión final, merge `--no-ff` a `main` (tras `git fetch` y comparar con origin), preprod (migración + E2E `pedidos-marcas.spec.ts` con el nuevo test 4 y una prueba a mano del 409 del duplicado con datos sintéticos por API), y producción (cumple los criterios de despliegue en horario: migración idempotente, web vieja compatible porque el endpoint no cambia).
