# Web Formulario de reparación, solicitudes, borrador y campana (sub-proyecto 2) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrar a la web el formulario de reparación en sus tres modos (nuevo, glass, editar) con solicitud de pieza, componente agotado, otras acciones, guardado por fila y borrador persistente compatible con el cliente JavaFX, más la campana de notificaciones del supertécnico, con paridad contra las fichas `docs/paridad/{formulario,notificaciones}.md`; en el servidor, técnico tomado del token y propiedad de la asignación en las escrituras del formulario, roles de solicitudes de stock y del ajuste de stock, autodetección de chasis por SKU y contrato con nullabilidad para lo que la web empieza a consumir. Entrega: web **v0.3.0**.

**Architecture:** Servidor: regla única `PropiedadAsignacion` (junto a `FiltroTecnico`) aplicada en `ReparacionController`, roles con `@PreAuthorize`, autodetección de chasis dentro de las transacciones de `ReparacionDAO`, respuestas `Map` sustituidas por records tipados con el mismo JSON. Web: todo dentro de `src/modules/taller/`; `formulario/estado.ts` es un reductor puro con selectores y constructores de cuerpos (los componentes solo pintan y despachan), `formulario/borrador.ts` serializa/aplica el JSON del JavaFX, `formulario/api.ts` concentra las llamadas, `useBorrador` gestiona retardo y volcados; el formulario es un diálogo modal gobernado por rutas hijas de Pendientes, Historial e IMEIs; `notificaciones/` aporta la campana (montada por `app/shell/TopBar`) y su panel anclado.

**Tech Stack:** Spring Boot 3.3.4 + springdoc-openapi 2.6.0 + JUnit 5/Mockito + JDK 17 (servidor); React 19.3.0 + TypeScript 5.9.3 + Vite 8.3.0 + React Router 7.18.3 + TanStack Query 5.102.8 + TanStack Table 8.21.3 + `@tanstack/react-virtual` 3.14.13 + Tailwind 4.3.3 + radix-ui 1.6.7 (shadcn/ui) + openapi-fetch 0.17.0 / openapi-typescript 7.13.0 + Vitest 5.0.0 + Testing Library 16.3.3 + MSW 2.15.0 + Playwright 1.63.0 (web). Sin dependencias nuevas.

**Spec:** `docs/superpowers/specs/2026-09-19-web-formulario-design.md` (repo raíz). **Fichas (criterio de aceptación):** `gestion-reparaciones-web/docs/paridad/{formulario,notificaciones}.md` y las casillas "(sub-proyecto 2)" de `pendientes.md`, `historial.md` e `imeis.md`. Las capturas de referencia viven fuera de los repos.

## Global Constraints

- Ramas: web `feature/web-formulario` (ya existe, con las fichas; base `e398504`); servidor `feature/web-formulario` (se crea en la Task 1 desde `main` `6e2629a`). El repo raíz se queda en `main` y solo recibe este plan y, al cerrar, los gitlinks.
- Commits **sin** `Co-Authored-By`. **Nunca** `git push`, merge ni tag: los hace el usuario o se piden con su OK explícito, uno a uno.
- Maven en Bash necesita: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`. Servidor: `mvn -q test` en verde antes de cada commit (la suite actual + los nuevos). Web: `npm run check` (lint + typecheck + tests) en verde antes de cada commit.
- Textos idénticos a las fichas, tildes y dobles espacios incluidos (los tests los comprueban literalmente). Colores solo por tokens de `src/shared/styles/tokens.css` (Tailwind `bg-<token>` / `text-<token>` / `border-<token>`); si hace falta un color nuevo se añade como token (lista cerrada en "Interfaces compartidas · Tokens"), nunca escrito a mano en un componente.
- Capas (`eslint.config.js`): `shared` no importa de `app` ni de `modules`; un módulo no importa de otro ni de `app`; dentro del módulo, imports relativos. `app/shell/TopBar` sí puede importar `@/modules/taller/notificaciones/Campana`.
- Sin dependencias nuevas en ninguno de los dos repos. Versiones pinneadas.
- Datos de tests **sintéticos** desde el primer commit: IMEI de 15 dígitos claramente falsos (`355400000000111`, `355400000000222`…), técnicos "Técnico A/F/H/J", usuarios `tecnico_n` / `tecnico_f` / `admin`, SKU inventados, coherentes con `src/modules/taller/test/fabrica.ts` y `src/test/render.tsx` (`SESION_TEC` idTec 4, `SESION_SUPER` idTec 3, `SESION_ADMIN`).
- Nada sensible en los repos: ni IPs, ni nombres reales, ni capturas, ni rutas de la documentación privada. Lo relativo a permisos se redacta en positivo ("el servidor toma el técnico del token", "solo el supertécnico edita"), nunca como descripción de una carencia.
- Compatibilidad con el cliente JavaFX 0.16.x: el servidor **ignora, no rechaza**, el `idTec` del cuerpo en el flujo con asignación; los JSON de respuesta que se tipan conservan exactamente sus claves; sin cambios de esquema de base de datos.
- El formato del borrador es el del JavaFX (`BorradorContenido`, mismos nombres de campo); el servidor lo guarda opaco. Un JSON ilegible = formulario limpio.
- Rutas HTTP y cuerpos exactamente los del contrato `api/openapi.json` (regenerado en la Task 4). Las propiedades del contrato son todas `required`; lo que no tiene valor viaja como `null` (para el servidor equivale a omitirlo).
- Tests: Vitest al lado de cada fichero (`X.test.ts(x)`), Testing Library + MSW (`server.use(http.get('*/api/...'))`), `renderConProviders` / `renderConRouter` de `src/test/render.tsx`.
- Overrides de MSW: dentro de una misma llamada a `server.use(...)` gana el **primer** handler que coincide, y cada llamada nueva se **antepone** a las anteriores. Por eso un test registra primero los handlers por defecto (`server.use(...handlersFormulario(...))`, `...conRegistro(...).handlers`, `...handlersNotificaciones(...)`) y, en una **segunda** llamada a `server.use(...)`, el handler que los sustituye. Nunca se mezclan en la misma llamada con el override detrás.
- A partir de la Task 16, todo fichero de test que monte el formulario real (`FormularioReparacion`, `FormularioNuevoRuta`, `FormularioEditarRuta`) lleva a nivel de fichero `afterEach(async () => { cleanup(); await borradorEnReposo() })`: el formulario vuelca su borrador al desmontar y esa escritura debe llegar antes de que `src/test/setup.ts` retire los handlers.
- Las fichas mandan: si este plan discrepa de una ficha, se sigue la ficha y se anota la discrepancia en el informe de la tarea.
- Cada tarea termina con el entregable testado y committeado; un revisor debe poder aprobarla o rechazarla por separado.

---

## Mapa de ficheros

**Servidor (`gestion-reparaciones-servidor`, paquete `com.reparaciones.servidor`)**

| Fichero | N/M | Responsabilidad |
|---|---|---|
| `src/main/java/com/reparaciones/servidor/security/PropiedadAsignacion.java` | nuevo | Regla única: técnico efectivo = el del token; 403 si la asignación es de otro; exigencia de SUPERTECNICO para la edición |
| `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` | mod. | `getIdTecDeAsignacion`; autodetección de chasis en `insertarCompleta`, `guardarFilaIndividual`, `agotarComponente` |
| `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` | mod. | Aplica la regla en `completa`, `filas`, `agotar-componente`, `borrador` ×3, `completar`; SUPERTECNICO en `PUT /{idRep}` y `detalle-edicion`; respuestas tipadas; `@Schema(nullable)` en sus records |
| `src/main/java/com/reparaciones/servidor/controller/SolicitudStockController.java` | mod. | Roles por método; `EstadoRequest` tipado |
| `src/main/java/com/reparaciones/servidor/controller/SolicitudController.java` | mod. | `EstadoRequest` tipado; `count` → `ValorEntero` |
| `src/main/java/com/reparaciones/servidor/controller/ComponenteController.java` | mod. | `PATCH /{idCom}/stock` → SUPERTECNICO |
| `src/main/java/com/reparaciones/servidor/controller/TelefonoController.java` | mod. | `GET /{imei}/modelo` → `ValorTexto` |
| `src/main/java/com/reparaciones/servidor/model/ValorEntero.java`, `model/ContenidoBorrador.java` | nuevos | Envoltorios `{"value": n}` y `{"contenido": json\|null}` |
| `model/{FilaReparacion,Componente,SolicitudResumen,SolicitudStock,Reparacion}.java`, record `ReparacionDAO.DetalleEdicion` | mod. | `@Schema(nullable = true)` campo a campo |
| `src/test/java/com/reparaciones/servidor/security/PropiedadAsignacionTest.java` | nuevo | Regla pura |
| `src/test/java/com/reparaciones/servidor/controller/PropiedadAsignacionControllersTest.java` | nuevo | La regla aplicada en cada endpoint (Mockito, llamada directa) |
| `src/test/java/com/reparaciones/servidor/controller/RolesReparacionFormularioTest.java` | nuevo | `@PreAuthorize` de `PUT /{idRep}`, `detalle-edicion` y `borrador` (MockMvc) |
| `src/test/java/com/reparaciones/servidor/controller/RolesSolicitudesStockTest.java` | nuevo | Roles de `/api/solicitudes-stock` y `PATCH /api/componentes/{id}/stock` (MockMvc) |
| `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOChasisTest.java` | nuevo | Autodetección de chasis |
| `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` | mod. | Esquemas, nullabilidad y respuestas tipadas nuevas |
| `docs/autorizacion_endpoints.md`, `docs/api_contract.md` | mod. | Versión corta sin tabla por endpoint; contrato |

**Web (`gestion-reparaciones-web`)**

| Fichero | N/M | Responsabilidad |
|---|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` | mod. | Contrato regenerado |
| `src/shared/api/client.ts` (+ `client.test.ts`) | mod. | Tipos exportados nuevos |
| `src/shared/api/queryClient.ts` (+ test) | mod. | `queryMeta.silenciarError` |
| `src/shared/session/expiracion.ts` (+ test), `src/main.tsx` | mod. | `onSesionExpirada` devuelve `unsubscribe` |
| `src/shared/ui/AlertaProvider.tsx` (+ test) | mod. | Mensajes multilínea; el foco vuelve a donde estaba al cerrar |
| `src/shared/ui/ComboNavy.tsx` (+ test) | nuevo | Combo navy de selección única (modelo y SKU) |
| `src/shared/styles/tokens.css` | mod. | Tokens del formulario (Task 10) y de la campana (Task 19) |
| `src/test/render.tsx` (+ `src/test/render.test.tsx`, nuevo) | mod. | `renderConRouter` (data router en memoria) |
| `src/modules/taller/test/fabrica.ts` (+ `src/modules/taller/test/fabrica.test.ts`, nuevo) | mod. | Componentes agrupados, solicitudes, detalle de edición, borrador JavaFX sintético |
| `src/modules/taller/lib/modelos.ts`, `piezas.ts` (+ tests) | mod. | `extraerModelo`, tipos de fila, color por stock |
| `src/modules/taller/lib/textos.ts` | mod. | Se retira `TOOLTIP_FORMULARIO`; se añade `TOOLTIP_ALMACEN` |
| `src/modules/taller/formulario/estado.ts` (+ `estado.test.ts`, `estado.solicitudes.test.ts`, `estado.edicion.test.ts`) | nuevo | Tipos, reductor, selectores, constructores de cuerpos |
| `src/modules/taller/formulario/borrador.ts` (+ test) | nuevo | `capturar`, `serializar`, `leerBorrador`, `aplicarBorrador`, `borradorVacio` |
| `src/modules/taller/formulario/api.ts` (+ test), `formulario/test/handlers.ts` | nuevo | Cargas, mutaciones, borrador; handlers MSW |
| `src/modules/taller/formulario/useBorrador.ts` (+ test) | nuevo | Retardo 2 s, volcados, bandera de recuperación; cola serie de escrituras y `borradorEnReposo()` para los tests |
| `src/modules/taller/formulario/useGuardado.ts` (+ test) | nuevo | Orquestación de "Terminar asignación" y "Guardar cambios" |
| `src/modules/taller/formulario/{FormularioReparacion,CabeceraFormulario,FilaComponente,SubFilaAgotado,OtrasAcciones,ZonaGuardar}.tsx` (+ tests; `CabeceraFormulario` y `ZonaGuardar` se prueban desde `FormularioReparacion.test.tsx`) | nuevo | Vista. `FormularioReparacion.tsx` se parte en `FormularioReparacion` (elige por modo) → `FormularioNuevo` / `FormularioEditar` (carga, aviso de error, recarga al desmontar) → `FormularioCargado` (reductor, borrador, guardado, título, diálogo) + `GuardiaSalida` (solo edición); ver W11 |
| `src/modules/taller/formulario/{DialogoObservacionFila,DialogoSolicitarPieza,DialogoDescripcionSolicitud,DialogoSalirSinGuardar}.tsx` | nuevo | Diálogos |
| `src/modules/taller/formulario/rutas.tsx` (+ test) | nuevo | `FormularioNuevoRuta`, `FormularioEditarRuta` |
| `src/modules/taller/rutas.tsx` (+ test) | mod. | `RequiereSupertecnico` |
| `src/app/router.tsx` | mod. | Rutas hijas |
| `src/modules/taller/pendientes/PendientesPage.tsx`, `historial/HistorialPage.tsx`, `imeis/ImeiDetallePage.tsx`, `componentes/MenuHistorial.tsx` (+ tests), `componentes/useAccionesTrabajo.tsx` (solo el tipo de `acciones`) | mod. | `<Outlet />`, botón y "Editar" navegan |
| `src/modules/taller/pendientes/PendientesPage.test.tsx`, `historial/HistorialPage.test.tsx`, `imeis/ImeisPage.test.tsx`, `imeis/ImeiDetallePage.test.tsx` | mod. (Task 19) | Registran `handlersNotificaciones()` en su `beforeEach`: montan `<AppLayout />` con supertécnico y la barra pide los datos de la campana |
| `src/modules/taller/notificaciones/{api.ts,alertas.ts,solicitudes.ts}` (+ tests), `notificaciones/test/handlers.ts` | nuevo | Datos de la campana |
| `src/modules/taller/notificaciones/{Campana,PanelNotificaciones,TarjetaSolicitud,TarjetaAlerta}.tsx` (+ tests) | nuevo | Vista de la campana |
| `src/app/shell/TopBar.tsx` (+ test) | mod. | Monta `<Campana />` a la izquierda de `UserMenu` |
| `public/NotfON.png`, `public/NotifOFF.png`, `public/Badge.png` | nuevo | Copiados de `gestion-reparaciones-cliente/src/main/resources/images/` |
| `tests/e2e/formulario.spec.ts`, `.env.e2e.example`, `README.md`, `CHANGELOG.md`, `package.json` + lock (0.3.0), `docs/paridad/*.md` | nuevo/mod. | Cierre |

---

## Interfaces compartidas

Todo lo de este apartado es contrato entre tareas: nombres, firmas y semántica son vinculantes. Entre corchetes, la tarea que lo **produce**.

### S. Servidor

#### S1. `PropiedadAsignacion` [Task 1]

```java
package com.reparaciones.servidor.security;

/** Regla única de las escrituras del formulario: el técnico de cada trabajo es el del token y solo se
 *  trabaja sobre asignaciones propias. Igual para TECNICO y SUPERTECNICO. */
public final class PropiedadAsignacion {

    public static final String MSG_NO_ES_TUYA        = "Solo puedes trabajar sobre tus propias asignaciones";
    public static final String MSG_SOLO_SUPERTECNICO = "Solo el supertécnico puede corregir una reparación ya hecha";

    private PropiedadAsignacion() {}

    /**
     * @param principal   usuario del token
     * @param idTecDueno  ID_TEC de la asignación ({@code null} si la asignación no existe)
     * @return el técnico efectivo (siempre el del token)
     * @throws ResponseStatusException 403 (MSG_NO_ES_TUYA) si el token no tiene técnico o la asignación es de otro.
     *         Si {@code idTecDueno} es null NO lanza: deja que el DAO responda su 409 de "ya eliminada o completada".
     */
    public static int tecnicoEfectivo(UsuarioPrincipal principal, Integer idTecDueno);

    /** @throws ResponseStatusException 403 (MSG_SOLO_SUPERTECNICO) si el rol no es SUPERTECNICO. */
    public static void exigirSupertecnico(UsuarioPrincipal principal);
}
```

#### S2. DAO [Task 1 y Task 2]

```java
// ReparacionDAO [Task 1]
/** ID_TEC de una fila de Reparacion (asignación abierta o cerrada), o null si no existe. */
public Integer getIdTecDeAsignacion(String idAsignacion);
// SQL: SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?   (jdbc.query + lista vacía → null)

// ReparacionDAO [Task 2] — privado, llamado dentro de las tres transacciones
/** Marca ES_CHASIS = TRUE en la asignación si el SKU del componente empieza por "cha". Nunca lo quita.
 *  Sin asignación (idAsignacion == null) no hace nada. No toca UPDATED_AT (mismo patrón que actualizarChasis). */
private void marcarChasisSiProcede(String idAsignacion, int idCom);
// SQL: UPDATE Reparacion SET ES_CHASIS = TRUE, UPDATED_AT = UPDATED_AT
//      WHERE ID_REP = ? AND ES_CHASIS = FALSE
//        AND EXISTS (SELECT 1 FROM Componente c WHERE c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%')
```
Puntos de llamada [Task 2]: `insertarCompleta` (por cada fila de uso y por cada fila `esSolicitud` con asignación), `guardarFilaIndividual` (ídem), `agotarComponente` (tras insertar la solicitud).

#### S3. `ReparacionController` — firmas que cambian [Task 1; los tipos de retorno, Task 3]

```java
@PostMapping("/completa")                       // sin @PreAuthorize: la regla va dentro
public void insertarCompleta(@RequestBody InsertarCompletaRequest req, @AuthenticationPrincipal UsuarioPrincipal principal);
//  con idAsignacion:  idTec = PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(req.idAsignacion()))   (req.idTec() ignorado)
//  sin idAsignacion:  PropiedadAsignacion.exigirSupertecnico(principal); idTec = req.idTec()   (técnico original)
//  el log usa el técnico efectivo

@PostMapping("/{idAsignacion}/filas")
public ValorTexto guardarFilaIndividual(...);   // Task 1: regla; Task 3: Map<String,String> → ValorTexto (mismo JSON {"value": idRep})

@PostMapping("/{idAsignacion}/agotar-componente")
public void agotarComponente(...);              // regla antes de tocar el DAO

@PatchMapping("/{idRep}/completar")
public void completar(...);                     // regla con el dueño de idRep

@PreAuthorize("hasAnyRole('SUPERTECNICO','TECNICO')")
@GetMapping("/{idRep}/borrador")    public ContenidoBorrador getBorrador(@PathVariable String idRep, @AuthenticationPrincipal UsuarioPrincipal principal);
@PutMapping("/{idRep}/borrador")    public void guardarBorrador(@PathVariable String idRep, @RequestBody BorradorRequest req, @AuthenticationPrincipal UsuarioPrincipal principal);
@DeleteMapping("/{idRep}/borrador") public void eliminarBorrador(@PathVariable String idRep, @AuthenticationPrincipal UsuarioPrincipal principal);
//  los tres: PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idRep)) antes del BorradorDAO
//  (la variable de ruta sigue llamándose idRep en el contrato: es el id de la asignación)

@PreAuthorize("hasRole('SUPERTECNICO')") @PutMapping("/{idRep}")                 public void editarReparacion(...);
@PreAuthorize("hasRole('SUPERTECNICO')") @GetMapping("/{idRep}/detalle-edicion") public ReparacionDAO.DetalleEdicion getDetalleEdicion(...);

@GetMapping("/imei/{imei}/incidencia-activa") public ValorTexto getIncidenciaActivaPorImei(...);   // Task 3 (mismo JSON {"value": id|null})
```
Los records de petición conservan sus campos y tipos (`int idTec` incluido: la web lo envía siempre; ver W3). Cuatro de ellos (`BorradorRequest`, `InsertarCompletaRequest`, `AgotarRequest`, `GuardarFilaRequest`) dejan de ser `private` y quedan **package-private** (Task 1) para que `PropiedadAsignacionControllersTest`, del mismo paquete, pueda construirlos; el nombre del esquema OpenAPI no depende de la visibilidad.

#### S4. Roles [Task 2]

| Endpoint | `@PreAuthorize` |
|---|---|
| `POST /api/solicitudes-stock` | `hasAnyRole('TECNICO','SUPERTECNICO')` |
| `GET /api/solicitudes-stock`, `GET /api/solicitudes-stock/count` | `hasAnyRole('SUPERTECNICO','ADMIN')` |
| `PATCH /api/solicitudes-stock/{idSol}/estado`, `DELETE /api/solicitudes-stock/{idSol}` | `hasRole('SUPERTECNICO')` |
| `PATCH /api/componentes/{idCom}/stock` | `hasRole('SUPERTECNICO')` |

`/api/solicitudes` ya es SUPERTECNICO a nivel de clase: no cambia.

#### S5. Contrato [Task 3]

Records nuevos en `model/`: `public record ValorEntero(int value) {}` y `public record ContenidoBorrador(@Schema(nullable = true) String contenido) {}`.

Respuestas que pasan de `Map` a record (JSON idéntico): `GET /api/solicitudes/count` y `GET /api/solicitudes-stock/count` → `ValorEntero`; `GET /api/reparaciones/imei/{imei}/incidencia-activa` → `ValorTexto`; `GET /api/telefonos/{imei}/modelo` → `ValorTexto` (sigue devolviendo `""` si no hay modelo); `POST /api/reparaciones/{idAsignacion}/filas` → `ValorTexto`; `GET /api/reparaciones/{idRep}/borrador` → `ContenidoBorrador`.

Cuerpos que pasan de `Map<String,String>` a record privado `EstadoRequest(String estado)`: `PATCH /api/solicitudes/{idRc}/estado` (esquema `SolicitudEstadoRequest`) y `PATCH /api/solicitudes-stock/{idSol}/estado` (esquema `SolicitudStockEstadoRequest`).

`@Schema(nullable = true)`: `FilaReparacion.{observacion, prefijo, descripcionSolicitud, estadoSolicitud}`; `Componente.{ultimoPedido, idComMaster}`; `SolicitudResumen.{tipoComponente, descripcion}`; `SolicitudStock.{descripcion}`; `Reparacion.{fechaFin}`; `ReparacionDAO.DetalleEdicion.{observacion}`; `InsertarCompletaRequest.{idRepAnterior, idAsignacion, categoria}`; `GuardarFilaRequest.{idRepAnterior}`; `AgotarRequest.{descripcion}`; `EditarRequest.{observacionNueva}`; `SolicitudStockController.InsertarRequest.{descripcion}`. (Quien redacte la Task 3 verifica cada uno contra el `RowMapper`/DAO y añade los que falten.)

Nombres de esquema resultantes que la web usa (Task 4): `Componente`, `FilaReparacion`, `Reparacion`, `SolicitudResumen`, `SolicitudStock`, `ReparacionDAODetalleEdicion`, `ReparacionDAOAsignacionActiva`, `ReparacionInsertarCompletaRequest`, `ReparacionGuardarFilaRequest`, `ReparacionAgotarRequest`, `ReparacionEditarRequest`, `ReparacionBorradorRequest`, `SolicitudEstadoRequest`, `SolicitudStockEstadoRequest`, `ValorEntero`, `ValorTexto`, `ContenidoBorrador`.

#### S6. Tests del servidor (nombres) 

`PropiedadAsignacionTest` [1]: `duenoRecibeSuIdTec`, `asignacionAjenaEs403ConMensaje`, `tokenSinTecnicoEs403`, `supertecnicoSobreAsignacionAjenaTambienEs403`, `asignacionInexistenteNoLanza`, `exigirSupertecnicoDejaPasarSoloAlSupertecnico`.
`PropiedadAsignacionControllersTest` [1]: `completaConAsignacionUsaElTecnicoDelTokenEIgnoraElDelCuerpo`, `completaConAsignacionAjenaEs403SinEscribir`, `completaSinAsignacionExigeSupertecnicoYConservaElIdTecDelCuerpo`, `completaSinAsignacionSiendoTecnicoEs403`, `filasUsaElTecnicoDelToken`, `filasAjenaEs403`, `agotarAjenaEs403SinTocarStock`, `agotarPropiaLlamaAlDao`, `borradorLeerGuardarYBorrarSoloSobreAsignacionPropia`, `completarAjenaEs403`, `completarPropiaLlamaAlDao`.
`RolesReparacionFormularioTest` [1]: `editarSiendoTecnicoEs403`, `editarSiendoAdminEs403`, `editarSiendoSupertecnicoLlegaAlDao`, `detalleEdicionSiendoTecnicoEs403`, `borradorSiendoAdminEs403`.
`RolesSolicitudesStockTest` [2]: `crearTecnicoYSupertecnico201AdminEs403`, `listarYContarSupertecnicoYAdmin200TecnicoEs403`, `cambiarEstadoYBorrarSoloSupertecnico`, `ajusteDeStockSoloSupertecnico`.
`ReparacionDAOChasisTest` [2]: `completarConPiezaChaMarcaLaAsignacion`, `guardarFilaConPiezaChaMarcaLaAsignacion`, `agotarPiezaChaMarcaLaAsignacion`, `solicitudChaDentroDeCompletaMarcaLaAsignacion`, `piezaQueNoEsChaNoMarca`, `sinAsignacionNoMarca`.
`OpenApiContractTest` [3]: se amplía `elContratoPublicaLosEsquemasDeLaWeb` (rutas del formulario y de la campana presentes; esquemas de S5; `nullable` de S5; `$ref` de las seis respuestas tipadas y de los dos cuerpos de estado).

### W. Web

#### W1. Tipos del contrato en `src/shared/api/client.ts` [Task 4]

```ts
export type Componente = components['schemas']['Componente']
export type ComponentesAgrupados = Record<string, Componente[]>
export type FilaReparacion = components['schemas']['FilaReparacion']
/** Lo que devuelve GET …/asignaciones/{idAsignacion}/solicitudes: mismo esquema que una fila. */
export type SolicitudAsignacion = FilaReparacion
export type Reparacion = components['schemas']['Reparacion']
export type DetalleEdicion = components['schemas']['ReparacionDAODetalleEdicion']
export type AsignacionActiva = components['schemas']['ReparacionDAOAsignacionActiva']
export type SolicitudResumen = components['schemas']['SolicitudResumen']
export type SolicitudStock = components['schemas']['SolicitudStock']
export type GuardarFilaRequest = components['schemas']['ReparacionGuardarFilaRequest']
export type InsertarCompletaRequest = components['schemas']['ReparacionInsertarCompletaRequest']
export type AgotarRequest = components['schemas']['ReparacionAgotarRequest']
export type EditarReparacionRequest = components['schemas']['ReparacionEditarRequest']
```

#### W2. Fábrica de tests `src/modules/taller/test/fabrica.ts` [Task 4]

```ts
export function componente(parcial?: Partial<Componente>): Componente      // por defecto { idCom: 101, tipo: 'bati13', stock: 5, stockMinimo: 2, activo: true, idComMaster: null, enCamino: 0, ultimoPedido: null, fechaRegistro/updatedAt: '2026-09-01T08:00:00' }
/** Catálogo sintético canónico, en el orden de claves del servidor (bat, cha, g, mc, lcd, cam, otro). */
export function agrupados(): ComponentesAgrupados
export function solicitudAsignacion(parcial?: Partial<SolicitudAsignacion>): SolicitudAsignacion   // por defecto { idCom: 102, cantidad: 1, reutilizado: false, observacion: null, prefijo: null, esSolicitud: true, descripcionSolicitud: null, estadoSolicitud: 'PENDIENTE', enCamino: false }
export function detalleEdicion(parcial?: Partial<DetalleEdicion>): DetalleEdicion                 // por defecto { imei: '355400000000111', idTec: 4, idCom: 101, esReutilizado: false, observacion: null, cantidad: 1, updatedAt: '2026-09-16T07:02:00' }
export function asignacionActiva(parcial?: Partial<AsignacionActiva>): AsignacionActiva           // por defecto { idRep: 'AG20260916_2', nombreTecnico: 'Técnico H', idTec: 6 }
export function solicitudUrgente(parcial?: Partial<SolicitudResumen>): SolicitudResumen           // por defecto { idRc: 501, idRep: 'A20260916_1', imei: '355400000000111', nombreTecnico: 'Técnico A', idCom: 102, tipoComponente: 'bati14', descripcion: null, estado: 'PENDIENTE', fechaSolicitud: '2026-09-16T07:02:00' }
export function solicitudPreventiva(parcial?: Partial<SolicitudStock>): SolicitudStock            // por defecto { idSol: 701, idCom: 111, tipoComponente: 'lcdi13', idUsu: 8, nombreUsuario: 'tecnico_n', descripcion: null, estado: 'PENDIENTE', fecha: '2026-09-16T09:30:00' }
export function reparacion(parcial?: Partial<Reparacion>): Reparacion                             // por defecto { idRep: 'R20260916_5', imei: '355400000000111', idTec: 4, … }
/** Borrador tal cual lo escribe el JavaFX (Gson sin nulos): modelo '13', una fila bat guardada (idRepGenerado 'R20260916_5',
 *  fechaGuardado '16/09 09:15'), una fila lcd normal con cantidad 1 y observación, una fila cam con agotado confirmado y
 *  descripción, una acción guardada y otra pendiente. Cadena JSON, sin importar tipos de formulario/. */
export const BORRADOR_JAVAFX: string
```
Catálogo canónico de `agrupados()` (los tests de todas las tareas cuentan con estos ids):

| prefijo | idCom · SKU · stock · mínimo |
|---|---|
| `bat` | 101 `bati13` 5/2 · 102 `bati14` 0/2 · 103 `bati13promax` 1/2 · 104 `bati12` 3/1 **inactivo** |
| `cha` | 131 `chai13negro` 2/1 |
| `g` | 141 `gi13` 6/2 · 142 `gi14` 0/2 |
| `mc` | 151 `mci13` 2/1 |
| `lcd` | 111 `lcdi13` 1/1 · 112 `lcdi14` 3/1 |
| `cam` | 121 `cami13` 4/1 |
| `otro` | 161 `otroi13` 0/0 · 162 `otroi14` 0/0 |

(No hay `cam`, `cha` ni `mc` para el modelo `14`: sirve para "tipo sin SKU para el modelo".)

#### W3. `src/modules/taller/lib` [Task 5]

```ts
// modelos.ts (MODELOS_ORDENADOS y traducirModelo ya existen)
/** Código de modelo de un SKU: minúsculas, sin el prefijo del tipo y sin la "i" inicial; el código de MODELOS_ORDENADOS
 *  MÁS LARGO que sea prefijo del resto; null si ninguno. extraerModelo('bati13promaxneg', 'bat') → '13promax'. */
export function extraerModelo(sku: string, prefijo: string): string | null
/** Modelos de MODELOS_ORDENADOS (en ese orden) presentes en algún SKU activo de los grupos indicados. */
export function modelosDisponibles(grupos: { prefijo: string; skus: Componente[] }[]): string[]

// piezas.ts (categoriaPieza ya existe)
export const PREFIJO_OTRO = 'otro'
export const PREFIJOS_GLASS: readonly string[]            // ['g', 'mc']
/** 'bat'→'Batería', 'cha'→'Chasis', 'g'→'Glass', 'cam'→'Cámara', 'lcd'→'Pantalla', 'mc'→'Marco'; otro prefijo, tal cual. */
export function nombreTipo(prefijo: string): string
/** Prefijos que son fila, en orden: claves del servidor sin grupos vacíos ni 'otro'; 'g' y 'mc' siempre al final.
 *  glass=false → todos menos g/mc; glass=true → solo g/mc. */
export function prefijosDeFila(agrupados: ComponentesAgrupados, glass: boolean): string[]
export type NivelStock = 'sinStock' | 'bajo' | 'normal'
/** stock 0 → 'sinStock'; 0 < stock ≤ mínimo → 'bajo'; resto 'normal'. */
export function nivelStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): NivelStock
/** Clase Tailwind del SKU: 'text-rojo-sin-stock' | 'text-fila-solicitud-brd' | ''. */
export function claseStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): string

// textos.ts
export const TOOLTIP_ALMACEN = 'Disponible con Almacén (próxima entrega)'     // TOOLTIP_FORMULARIO se retira en la Task 18
```

#### W4. `src/modules/taller/formulario/estado.ts` — tipos [Task 6; los campos marcados, en 7 y 8]

```ts
import type { AgotarRequest, AsignacionActiva, Componente, ComponentesAgrupados, DetalleEdicion, EditarReparacionRequest,
  FilaReparacion, GuardarFilaRequest, InsertarCompletaRequest, SolicitudAsignacion } from '@/shared/api/client'

export type ModoFormulario = 'nuevo' | 'glass' | 'editar'
export type Categoria = 'R' | 'G'

/** Marca "✓ Guardada": vive solo en el borrador. */
export type Guardada = { idRep: string; fecha: string }                       // fecha 'dd/MM HH:mm' local del navegador
/** Solicitud local confirmada en esta sesión (aún no enviada). registrado = ya se envió su agotar-componente
 *  en un intento de "Terminar asignación" y no debe reenviarse. */
export type AgotadoLocal = { descripcion: string | null; registrado: boolean } // [Task 7]
/** Solicitud ya guardada en el servidor, aplicada a la fila al abrir. Las rechazadas y las recibidas no dejan este campo. */
export type SolicitudServidor = { estado: 'pendiente' | 'enCamino'; descripcion: string | null }   // [Task 7]
export type RolFila = 'normal' | 'editada' | 'yaReparado'                      // 'editada' y 'yaReparado' solo en modo editar [Task 8]
export type OriginalEdicion = { idCom: number; cantidad: number; reutilizado: boolean; observacion: string | null }

/** Habilitado de los controles, mantenido por el reductor paso a paso como lo hace la referencia (no se deriva: así se
 *  calcan rarezas como "+" habilitado con stock 0 tras desmarcar Reutilizado). Una fila sin SKU para el modelo
 *  (opciones vacías) se pinta siempre deshabilitada y atenuada, diga lo que diga este campo. */
export type ControlesFila = { mas: boolean; menos: boolean; reutilizado: boolean; sku: boolean; observacion: boolean }

export type FilaEstado = {
  prefijo: string
  nombre: string                       // nombreTipo(prefijo)
  skus: Componente[]                   // todos los ACTIVOS del tipo
  opciones: Componente[]               // skus filtrados por el modelo (todos si no hay modelo)
  idCom: number | null                 // SKU elegido; null si opciones está vacío
  cantidad: number
  reutilizado: boolean
  observacion: string | null
  controles: ControlesFila
  rol: RolFila
  guardada: Guardada | null
  confirmandoGuardar: boolean          // "✓ Confirmar" de "✓ Guardar fila"
  guardando: boolean                   // llamada en vuelo: botón deshabilitado
  agotado: AgotadoLocal | null         // [Task 7]
  solicitud: SolicitudServidor | null  // [Task 7]
  recibidoPendienteUso: boolean        // "✓ Recibido" aún visible [Task 7]
  original: OriginalEdicion | null     // solo rol 'editada' [Task 8]
}

export type OrigenAccion = 'nueva' | 'editada' | 'yaReparada'
export type OtraAccion = {
  id: number                           // identidad local estable (contador del estado)
  texto: string
  origen: OrigenAccion                 // 'editada' y 'yaReparada' solo en modo editar
  guardada: Guardada | null
  confirmando: boolean
  guardando: boolean
  /** Tras FALLO_GUARDAR_ACCION: el texto sigue en "✓ Confirmar" (confirmando = true) pero el siguiente clic vuelve a pedir
   *  confirmación en vez de guardar. Opcional: quien construye una OtraAccion no necesita ponerlo. [Task 7] */
  pideOtroClic?: boolean
}

export type EdicionEstado = {
  idRep: string
  tipo: 'pieza' | 'accion'
  idTecOriginal: number
  updatedAt: string
  textoAccionOriginal: string | null   // tipo 'accion'
  idComAccion: number | null           // tipo 'accion': el idCom 'otro…' de la reparación editada
}

export type GuardadoEstado = {
  clics: 0 | 1                         // 1 = el siguiente clic ejecuta
  textoConfirmacion: boolean           // una vez true, no vuelve a false
  enCurso: boolean
}

export type EstadoFormulario = {
  modo: ModoFormulario
  categoria: Categoria                 // 'G' si idAsignacion empieza por 'AG' o idRep por 'G'
  idAsignacion: string | null          // nuevo y glass
  imei: string
  incidencia: string | null            // idRepAnterior de todos los guardados
  modelo: string | null
  modeloBloqueado: boolean
  modelos: string[]                    // opciones del combo, en el orden de MODELOS_ORDENADOS
  tieneSolicitudesIniciales: boolean   // hubo solicitudes (de cualquier estado): las filas nunca se ocultan
  filas: FilaEstado[]
  componentesOtro: Componente[]        // grupo 'otro' completo (no se mira 'activo')
  otros: OtraAccion[]
  siguienteIdAccion: number
  edicion: EdicionEstado | null
  guardado: GuardadoEstado
  borradorRecuperado: boolean          // banda azul [la pone aplicarBorrador, Task 9]
  borradorDescartado: boolean          // tras un guardado real: no se vuelve a escribir borrador
  /** Sube SOLO con cambios de datos, que son los que reprograman el autoguardado (modelo, filas, agotado local, otras acciones).
   *  NO la suben: EDITAR_DESCRIPCION_AGOTADO, PEDIR_CONFIRMACION_*, INICIO_*, FALLO_*, AGOTADO_REGISTRADO, GUARDADO_COMPLETADO,
   *  REEMPLAZAR, ni FILA_GUARDADA / ACCION_GUARDADA / DESBLOQUEAR_BORRADAS (estas tres suben `volcados`). */
  revision: number
  /** Sube cuando hay que volcar el borrador YA: FILA_GUARDADA, ACCION_GUARDADA y DESBLOQUEAR_BORRADAS con cambios. */
  volcados: number
}
```
Una acción que no procede devuelve **el mismo objeto de estado** (ni re-render ni reprogramación). `useBorrador` cuelga el autoguardado de `revision` y el volcado inmediato de `volcados`.

#### W5. `estado.ts` — estado inicial y reductor

```ts
export type DatosNuevo = {
  modo: 'nuevo' | 'glass'
  idAsignacion: string
  imei: string
  agrupados: ComponentesAgrupados
  solicitudes: SolicitudAsignacion[]        // incluye rechazadas
  incidencia: string | null
  modeloTelefono: string | null             // '' o fuera de la lista = null; solo se usa si tras las solicitudes no hay modelo
}
export type DatosEditar = {
  modo: 'editar'
  idRep: string
  detalle: DetalleEdicion
  agrupados: ComponentesAgrupados
  yaReparados: number[]
  accionesYaReparadas: string[]
}
/** [Task 6: modo nuevo/glass sin solicitudes · Task 7: solicitudes (tres pasadas: aplicar, fijar modelo, reaplicar; rechazadas
 *  solo preseleccionan SKU) · Task 8: modo editar]. Orden del modelo en nuevo/glass: solicitudes → modeloTelefono (bloquea). */
export function estadoInicial(datos: DatosNuevo | DatosEditar): EstadoFormulario

export type AccionFormulario =
  // Task 6
  | { tipo: 'CAMBIAR_MODELO'; modelo: string | null }
  | { tipo: 'SUMAR'; prefijo: string }
  | { tipo: 'RESTAR'; prefijo: string }
  | { tipo: 'CAMBIAR_SKU'; prefijo: string; idCom: number }
  | { tipo: 'MARCAR_REUTILIZADO'; prefijo: string; valor: boolean }
  | { tipo: 'PONER_OBSERVACION'; prefijo: string; texto: string }          // recorta; vacío = sin efecto
  | { tipo: 'BORRAR_OBSERVACION'; prefijo: string }
  // Task 7
  | { tipo: 'PEDIR_CONFIRMACION_FILA'; prefijo: string }                   // 1.er clic → "✓ Confirmar"
  | { tipo: 'INICIO_GUARDAR_FILA'; prefijo: string }                       // 2.º clic: guardando = true
  | { tipo: 'FILA_GUARDADA'; prefijo: string; idRep: string; fecha: string }
  | { tipo: 'FALLO_GUARDAR_FILA'; prefijo: string }                        // rehabilita, vuelve a "✓ Guardar fila"
  | { tipo: 'CONFIRMAR_AGOTADO'; prefijo: string; descripcion: string }    // local; recorta; borra la observación
  | { tipo: 'EDITAR_DESCRIPCION_AGOTADO'; prefijo: string; descripcion: string }   // NO sube revision
  | { tipo: 'CANCELAR_AGOTADO'; prefijo: string }
  | { tipo: 'AGOTADO_REGISTRADO'; prefijo: string }                        // agotar-componente OK en este intento
  | { tipo: 'ANADIR_ACCION' }
  | { tipo: 'ESCRIBIR_ACCION'; id: number; texto: string }                 // devuelve "✓ Confirmar" a "✓ Guardar"
  | { tipo: 'QUITAR_ACCION'; id: number }
  | { tipo: 'PEDIR_CONFIRMACION_ACCION'; id: number }
  | { tipo: 'INICIO_GUARDAR_ACCION'; id: number }
  | { tipo: 'ACCION_GUARDADA'; id: number; idRep: string; fecha: string }
  | { tipo: 'FALLO_GUARDAR_ACCION'; id: number }                           // rehabilita; el texto SIGUE en "✓ Confirmar" pero pide otro clic (pideOtroClic = true)
  | { tipo: 'PEDIR_CONFIRMACION_GUARDAR' }                                 // zona: 1.er clic
  | { tipo: 'INICIO_GUARDADO' }                                            // zona: 2.º clic
  | { tipo: 'FALLO_GUARDADO' }                                             // clics = 0, enCurso = false, el texto no vuelve
  | { tipo: 'GUARDADO_COMPLETADO' }                                        // borradorDescartado = true
  // Task 9 (los usa borrador.ts / useBorrador)
  | { tipo: 'REEMPLAZAR'; estado: EstadoFormulario }                       // resultado de aplicarBorrador; no sube revision
  | { tipo: 'DESBLOQUEAR_BORRADAS'; idsExistentes: string[] }              // filas/acciones guardadas cuyo idRep ya no existe

export function reducir(estado: EstadoFormulario, accion: AccionFormulario): EstadoFormulario
```
Reglas transversales del reductor: una acción sobre una fila guardada, con `solicitud` activa, con `agotado` confirmado o `rol: 'yaReparado'` no cambia nada (salvo las específicas de ese estado: `MARCAR_REUTILIZADO` y observación sobre solicitud `pendiente`; `EDITAR_DESCRIPCION_AGOTADO`/`CANCELAR_AGOTADO` sobre agotado local). Cualquier cambio de fila pone `confirmandoGuardar = false`. `CAMBIAR_MODELO` ignora las filas guardadas y las de solicitud activa, resetea el resto y apaga `recibidoPendienteUso` de las filas que se quedan sin SKU.

#### W6. `estado.ts` — selectores y constructores

```ts
// Task 6
export function filasVisibles(e: EstadoFormulario): boolean              // false = "Selecciona un modelo de iPhone para continuar"
export function componenteDe(fila: FilaEstado): Componente | null        // el SKU elegido
export function stockDe(fila: FilaEstado): number | null                 // null → "—"
export function filaSinSku(fila: FilaEstado): boolean                    // opciones vacías → opacidad 0,4 y todo deshabilitado
export function filaActiva(fila: FilaEstado): boolean                    // cantidad > 0 || reutilizado || (agotado local && !solicitud)
export function etiquetaImei(e: EstadoFormulario): string                // 'IMEI: …' / 'IMEI: …  ·  Editando …' / '…  ·  Editando acción …'
export function tituloPestana(e: EstadoFormulario): string               // 'Nueva reparación — IMEI …' / 'Editar reparación — …'
export function textoConflicto(activas: AsignacionActiva[], idAsignacionPropia: string, idTecSesion: number | null): string | null

// Task 7
export type BotonDerecho =
  | { tipo: 'ninguno' }
  | { tipo: 'guardarFila'; texto: '✓ Guardar fila' | '✓ Confirmar'; deshabilitado: boolean }
  | { tipo: 'guardada'; texto: string }            // '✓ Guardada dd/MM HH:mm'
  | { tipo: 'enCamino' }                           // '⚠ En camino'
  | { tipo: 'recibido' }                           // '✓ Recibido'
  | { tipo: 'yaReparado' }                         // '✓  Ya reparado' [el tipo existe desde la Task 7; lo devuelve la Task 8; lo PINTA la Task 18 (hasta entonces cae en el default de FilaComponente)]
export function botonDerecho(e: EstadoFormulario, fila: FilaEstado): BotonDerecho
export type SubFila =
  | { tipo: 'oculta' }
  | { tipo: 'sinStock' }                           // texto y botón "Solicitar pieza"
  | { tipo: 'limite'; stock: number }              // texto y botón "Solicitar y descontar stock"
  | { tipo: 'confirmada'; texto: string; lapizHabilitado: boolean }   // lapizHabilitado = solicitud local
export function subFila(e: EstadoFormulario, fila: FilaEstado): SubFila     // siempre 'oculta' en modo editar
export const TEXTO_SIN_STOCK: string; export const TEXTO_LIMITE: string     // literales de la ficha (doble espacio tras ⚠)
export function otrasAccionesVisible(e: EstadoFormulario): boolean
export function idComOtro(e: EstadoFormulario): number | null              // componente 'otro' cuyo SKU da el modelo
export function contadorAcciones(e: EstadoFormulario): number              // guardadas o con texto (incluye 'yaReparada')
export function anadirAccionHabilitado(e: EstadoFormulario): boolean
export function accionPideConfirmacion(a: OtraAccion): boolean             // true → el clic en "✓ Guardar"/"✓ Confirmar" despacha PEDIR_CONFIRMACION_ACCION; false → guarda
export function zonaGuardarVisible(e: EstadoFormulario): boolean           // [Task 7: nuevo/glass · Task 8: editar]
export function textoBotonGuardar(e: EstadoFormulario): string             // 'Terminar asignación' | 'Guardar cambios' | '✓  Confirmar terminar'

// Task 8
export function hayCambioEnFilaEditada(e: EstadoFormulario): boolean
export function filaEditadaInvalida(e: EstadoFormulario): boolean          // cambio && cantidad 0 && !reutilizado → contador rojo
export function accionEditadaInvalida(e: EstadoFormulario): boolean
export function hayCambiosSinGuardar(e: EstadoFormulario): boolean         // modo editar && zonaGuardarVisible
export type PrevisionStock = { texto: string; tendencia: 'baja' | 'sube' | 'igual' }
export function previsionStock(e: EstadoFormulario, fila: FilaEstado): PrevisionStock | null   // solo rol 'editada'

export function filaDeCuerpo(fila: FilaEstado): FilaReparacion             // { idCom, cantidad, reutilizado, observacion, prefijo, esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }
export function filaDeAccion(idCom: number, texto: string): FilaReparacion // cantidad 0, prefijo 'otro', observacion = texto recortado
export function cuerpoGuardarFila(e: EstadoFormulario, prefijo: string, idTecSesion: number): GuardarFilaRequest
export function cuerpoGuardarAccion(e: EstadoFormulario, id: number, idTecSesion: number): GuardarFilaRequest | null   // null sin idComOtro
export type PlanTerminar = {
  agotados: { prefijo: string; cuerpo: AgotarRequest }[]      // solo los NO registrados, en orden de filas
  completa: InsertarCompletaRequest | null                    // null = paso 3 de la ficha (sin filas y hubo agotado nuevo, registrado o no).
                                                              // Quien lo ejecuta (Task 15) recalcula el plan con el estado resultante tras cada AGOTADO_REGISTRADO.
}
export function planTerminar(e: EstadoFormulario, idTecSesion: number): PlanTerminar
export type PlanGuardarCambios = {
  editarAccion: EditarReparacionRequest | null               // paso 0
  editarFila: EditarReparacionRequest | null                 // paso 1
  completaFilas: InsertarCompletaRequest | null              // paso 2 (idTec = original, idAsignacion/idRepAnterior null, categoria 'G' o null)
  completaAcciones: InsertarCompletaRequest | null           // paso 3
}
export function planGuardarCambios(e: EstadoFormulario): PlanGuardarCambios
```
`idTec` en los cuerpos: en nuevo/glass, el de la sesión (el servidor lo ignora); en edición, `edicion.idTecOriginal`. Los campos sin valor van a `null`.

#### W7. `src/modules/taller/formulario/borrador.ts` [Task 9]

```ts
export type BorradorFila = {
  prefijo: string
  idCom: number                        // -1 sin elegir
  cantidad: number
  reutilizado: boolean
  observacion?: string
  solicitudNueva: boolean              // siempre false
  descripcionSolicitud?: string
  agotadoConfirmado: boolean
  descripcionAgotado?: string
  guardada: boolean
  idRepGenerado?: string
  fechaGuardado?: string
}
export type BorradorAccion = { descripcion: string; guardada: boolean; idRepGenerado?: string; fechaGuardado?: string }
export type BorradorContenido = { modelo?: string; filas: BorradorFila[]; otros: BorradorAccion[] }

export function capturar(e: EstadoFormulario): BorradorContenido
export function borradorVacio(b: BorradorContenido): boolean             // sin filas y sin otros (el modelo solo no cuenta)
export function serializar(e: EstadoFormulario): string | null           // null = vacío → DELETE
/** null si json es null/vacío, no es JSON, no tiene la forma mínima o está vacío. Tolera claves ausentes (Gson omite nulos). */
export function leerBorrador(json: string | null): BorradorContenido | null
export function tieneGuardadas(b: BorradorContenido): boolean
/** Devuelve el estado con el borrador aplicado y borradorRecuperado = true; no toca revision ni volcados. */
export function aplicarBorrador(e: EstadoFormulario, b: BorradorContenido): EstadoFormulario
```
Al escribir: booleanos y números siempre presentes; cadenas sin valor, omitidas (así sale del JavaFX).
Al aplicar un agotado confirmado: `agotado = { descripcion, registrado: false }`, controles apagados y `cantidad` = la del borrador **acotada al stock actual** del SKU (0 si ya no hay stock), para no perder el descuento de la variante «límite» (decisión 7; corrección deliberada respecto al JavaFX, que la dejaba a 0). Una fila normal, en cambio, restaura la cantidad sin revalidarla (calco).

#### W8. Compartidos [Task 10]

```ts
// src/shared/ui/ComboNavy.tsx
export type OpcionCombo = { valor: string; etiqueta: string; clase?: string }      // clase: color del texto (SKU por stock)
export function ComboNavy(props: {
  valor: string | null
  opciones: OpcionCombo[]
  onChange: (valor: string) => void
  textoVacio: string                   // '— Selecciona modelo —' | '—'
  ancho: number                        // 180 | 170
  tamanoTexto?: 11 | 12                // por defecto 12
  visibles?: number                    // filas visibles de la lista (8 en SKU)
  disabled?: boolean
  'aria-label': string
}): JSX.Element
// Accesibilidad para tests: botón role="combobox" con el aria-label; lista role="listbox"; opciones role="option" (aria-selected).

// src/shared/api/queryClient.ts
declare module '@tanstack/react-query' { interface Register { queryMeta: { silenciarError?: boolean }; mutationMeta: { silenciarError?: boolean } } }
// QueryCache.onError: con meta.silenciarError no avisa de NADA (tampoco del corte de conexión: el banner basta).

// src/shared/session/expiracion.ts
export function onSesionExpirada(h: () => void): () => void               // devuelve unsubscribe (solo quita h si sigue siendo el actual)

// src/shared/ui/AlertaProvider.tsx
// mostrarError / mostrarAviso / mostrarTexto: el mensaje respeta '\n' (whitespace-pre-line) y, al cerrar, el foco vuelve
// al elemento que lo tenía al abrir (si sigue en el documento).

// src/test/render.tsx
import type { RouteObject } from 'react-router'
/** Igual que renderConProviders pero con createMemoryRouter + RouterProvider (necesario para useBlocker). */
export function renderConRouter(rutas: RouteObject[], opciones?: { sesion?: Sesion | null; ruta?: string }): ReturnType<typeof render> & { queryClient: QueryClient; router: ReturnType<typeof createMemoryRouter> }
```

#### W9. Tokens nuevos en `tokens.css`

[Task 10 — formulario] `--color-form-barra-bg: #C8CDD6`, `--color-form-cabecera-bg: #BCC2CB`, `--color-form-cabecera-brd: #A8AEB7`, `--color-form-fila-brd: #E0E0E0`, `--color-form-obs-bg: #888888`, `--color-form-guardada-bg: #F1F8F1`, `--color-form-agotado-bg: #FFF8E0`, `--color-form-agotado-text: #7A5C00`, `--color-ambar: #E8A825`, `--color-rojo-cancelar: #C94040`, `--color-form-otros-bg: #F6F7F9`, `--color-form-zona-bg: #ECEEF1`, `--color-form-zona-brd: #CFD3DA`, `--color-aviso-conflicto-bg: #FFF3E0`, `--color-aviso-conflicto-text: #E65100`, `--color-aviso-incidencia-text: #CC4444`.
Reutilizados (ya existen, mismo hex): `texto-incidencia` #000000 (el negro del contador y de la observación: `text-texto-incidencia`; decisión 5), `fondo-vista` #DDE1E7, `fondo-input` #F3F3F3, `gris-borde` #A9A9A9, `gris-disabled` #E7E7E7, `rojo-sin-stock` #B03040, `fila-solicitud-brd` #C07800, `fila-reparado-ico` #8AC7AF, `fila-reparado-bg/brd` #EBF5EB/#C5E1C5, `fila-edicion-bg/brd`, `recibido-bg/text` #E8F5E9/#2E7D32, `tipo-reparacion-bg/text` #E3F2FD/#1565C0, `verde-ok` #4CAF50, `fila-sep` #C2C8D0, `azul-*`, `crema`, `rojo-accion`.
[Task 19 — campana] `--color-notif-panel-brd: #C4C9D4`, `--color-notif-segmento-brd: #D0D4DC`, `--color-notif-tarjeta-alt: #F5F6F8`, `--color-notif-urgente-text: #D97B00`, `--color-notif-rechazar-bg: #F5A0A0`, `--color-notif-rechazar-text: #7A2020`, `--color-notif-rechazada-bg: #F0F1F3`, `--color-notif-rechazada-alt: #E9EAEC`, `--color-notif-avatar-apagado: #EDEEF0`, `--color-notif-texto-apagado: #B0B5BF`, `--color-notif-urgente-apagado-text: #C8A060`, `--color-notif-urgente-apagado-bg: #FFF8ED`, `--color-notif-sin-stock: #E8504A`, `--color-notif-stock-bajo: #E8903A`. Reutilizados: `amarillo`, `badge-neutro-bg` #E8EAF0, `texto-fecha-inicio` #9AA0AA, `aviso-conflicto-bg` #FFF3E0.

#### W10. `src/modules/taller/formulario/api.ts` [Task 11]

```ts
export const claveCargaNuevo = (idAsignacion: string) => ['formulario', 'nuevo', idAsignacion] as const
export const claveCargaEditar = (idRep: string) => ['formulario', 'editar', idRep] as const

export type CargaNuevo = { datos: DatosNuevo; asignacionesActivas: AsignacionActiva[]; borradorJson: string | null }
/** 1) GET /api/reparaciones/asignaciones/{idRep} (imei; 404 → NoEncontradoError). 2) En paralelo: GET /api/componentes/agrupados,
 *  GET …/asignaciones/{idAsignacion}/solicitudes, GET …/imei/{imei}/incidencia-activa?tipo=R|G, GET …/imei/{imei}/asignaciones-activas
 *  (fallo → []), GET /api/telefonos/{imei}/modelo (fallo → null), GET …/{idRep}/borrador (fallo → null, SALVO PermisoError, que se propaga). */
export async function cargarNuevo(idAsignacion: string): Promise<CargaNuevo>
/** GET …/{idRep}/detalle-edicion → después, en paralelo: agrupados, …/imei/{imei}/ya-reparados?excluir=, …/imei/{imei}/acciones?categoria=&excluir= (fallo → []). */
export async function cargarEditar(idRep: string): Promise<DatosEditar>
/** useQuery con gcTime 0, staleTime Infinity, sin refetch por foco ni intervalo, meta.silenciarError (el aviso lo da la vista). */
export function useCargaNuevo(idAsignacion: string): UseQueryResult<CargaNuevo>
export function useCargaEditar(idRep: string): UseQueryResult<DatosEditar>

// Mutaciones: todas con meta.silenciarError (la vista pone el literal) y SIN invalidar nada.
export function useGuardarFila(): UseMutationResult<string, unknown, { idAsignacion: string; cuerpo: GuardarFilaRequest }>   // → idRep ('?' si value null)
export function useAgotarComponente(): UseMutationResult<void, unknown, { idAsignacion: string; cuerpo: AgotarRequest }>
export function useCompleta(): UseMutationResult<void, unknown, InsertarCompletaRequest>
export function useEditarReparacion(): UseMutationResult<void, unknown, { idRep: string; cuerpo: EditarReparacionRequest }>

// Borrador y verificación: funciones sueltas que LANZAN; quien llama decide el silencio.
export async function guardarBorrador(idAsignacion: string, contenido: string): Promise<void>     // PUT  …/{idRep}/borrador { contenido }
export async function borrarBorrador(idAsignacion: string): Promise<void>                         // DELETE …/{idRep}/borrador
export async function idsReparacionesDelImei(imei: string): Promise<string[]>                     // GET /api/reparaciones/imei/{imei}

/** Al cerrar el formulario (guardado o no): invalida asignaciones R/G/P, CLAVE_CONTADORES, los tres historiales y CLAVE_NOTIF. */
export function useRecargarAlCerrar(): () => void
export const CLAVE_NOTIF = ['notificaciones'] as const       // se define aquí para no importar de notificaciones/; notificaciones/api.ts la reexporta
```
`src/modules/taller/formulario/test/handlers.ts`:
```ts
export type EscenarioFormulario = {
  asignacion?: Partial<ReparacionResumen>          // por defecto resumen({ idRep: 'A20260916_1' })
  agrupados?: ComponentesAgrupados                 // por defecto agrupados()
  solicitudes?: SolicitudAsignacion[]
  incidencia?: string | null
  activas?: AsignacionActiva[]
  modeloTelefono?: string                          // '' por defecto
  borrador?: string | null
  reparacionesImei?: Reparacion[]
  detalle?: DetalleEdicion; yaReparados?: number[]; acciones?: string[]
}
/** Handlers MSW de todas las lecturas + escrituras con éxito (filas → { value: 'R20260916_9' }). */
export function handlersFormulario(escenario?: EscenarioFormulario): RequestHandler[]
/** Registro de las escrituras recibidas, en orden, para comprobar orden y cuerpos. Formato exacto:
 *  metodo = request.method, en MAYÚSCULAS ('POST' | 'PUT' | 'DELETE'); ruta = pathname de la URL ('/api/reparaciones/A20260916_1/filas',
 *  sin origen ni query); cuerpo = JSON recibido ya parseado, o null si la petición no lleva cuerpo (DELETE).
 *  Se anotan TODAS las escrituras, también PUT/DELETE …/borrador: los tests que comprueban el orden de los guardados las filtran
 *  (`llamadas.filter((l) => !l.ruta.endsWith('/borrador'))`). Las lecturas no se anotan. */
export type LlamadaRegistrada = { metodo: string; ruta: string; cuerpo: unknown }
export function conRegistro(escenario?: EscenarioFormulario): { handlers: RequestHandler[]; llamadas: LlamadaRegistrada[] }
```
Datos por defecto de `handlersFormulario()` / `conRegistro()`: asignación `resumen({ idRep: 'A20260916_1' })` con el `idRep` de la URL; `agrupados()`; sin solicitudes, incidencia `null`, sin asignaciones activas, modelo `''`, borrador `null`, `reparacionesImei` `[]`, `detalleEdicion()`, `yaReparados` y `acciones` `[]`; escrituras: `completa` y `agotar-componente` 201 sin cuerpo, `filas` 201 `{ value: 'R20260916_9' }`, `PUT`/`DELETE …/borrador` y `PUT /{idRep}` 200. Un test que necesite otra respuesta la declara en una **segunda** llamada a `server.use(...)` (ver Global Constraints). `cargarEditar` lanza `NoEncontradoError` si `detalle-edicion` responde sin cuerpo; la vista (Task 18) lo trata como cualquier fallo de carga: aviso y cierre.

#### W11. Componentes del formulario (props)

```ts
// [Task 12]
export function FormularioReparacion(props:
  | { modo: 'nuevo' | 'glass'; idAsignacion: string; onCerrar: () => void }
  | { modo: 'editar'; idRep: string; onCerrar: () => void }): JSX.Element
// Partición interna de FormularioReparacion.tsx (no exportada; vinculante para las Tasks 13–18, que insertan sobre ella):
//   FormularioReparacion        sin hooks; elige por modo. Hasta la Task 18, modo 'editar' → null.
//   FormularioNuevo   [Task 12] useCargaNuevo; si la carga falla, aviso (mostrarError(mensajeDeError(e)), salvo error gestionado globalmente)
//                               y onCerrar; recarga la lista al DESMONTAR (useRecargarAlCerrar); monta el interior con key cuando hay datos.
//   FormularioEditar  [Task 18] lo mismo con useCargaEditar (NoEncontradoError incluido); pasa { datos, asignacionesActivas: [], borradorJson: null }.
//                               Lo común a los dos cargadores vive en el hook local useCierreDeCarga(falla, error, onCerrar) [Task 18].
//   FormularioCargado [Task 12] props { carga: CargaNuevo; onCerrar } (Task 18: carga: { datos: DatosNuevo | DatosEditar; asignacionesActivas; borradorJson }).
//                               useReducer(reducir, carga.datos, estadoInicial), document.title, <Dialog>, cabecera de columnas, filas;
//                               Task 13: useGuardado + FilaComponente · Task 14: SubFilaAgotado · Task 15: OtrasAcciones + ZonaGuardar ·
//                               Task 16: useBorrador({ borradorJson: carga.borradorJson, activo: modo !== 'editar' }), cerrar = volcarAhora() + onCerrar(),
//                               useGuardado({ onGuardado: onCerrar, antesDeCerrar: descartar }) · Task 18: salidaLibre + alGuardar + GuardiaSalida.
//   GuardiaSalida     [Task 18] useBlocker + DialogoSalirSinGuardar; SOLO se monta con modo 'editar' (useBlocker exige data router: los tests
//                               de edición usan renderConRouter; los de nuevo/glass pueden usar renderConProviders).
export function CabeceraFormulario(props: { estado: EstadoFormulario; conflicto: string | null; dispatch: Dispatch<AccionFormulario>; onCerrar: () => void }): JSX.Element
// formulario/rutas.tsx
export function FormularioNuevoRuta(props: { glass: boolean }): JSX.Element                         // useParams idAsignacion; cierra a /reparaciones/pendientes[/glass]
export function FormularioEditarRuta(props: { origen: 'historial' | 'historial-glass' | 'imei' }): JSX.Element   // [Task 18]
// taller/rutas.tsx
export function RequiereSupertecnico(): JSX.Element                                                 // [Task 18] no SUPERTECNICO → aviso MSG_SIN_PERMISOS y Navigate a la lista

// [Task 13]
export function FilaComponente(props: { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario>; onGuardarFila: (prefijo: string) => void; children?: ReactNode /* la sub-fila */ }): JSX.Element
export function DialogoObservacionFila(props: { abierto: boolean; tipo: string; inicial: string; onGuardar: (texto: string) => void; onCancelar: () => void }): JSX.Element

// [Task 14]
export function SubFilaAgotado(props: { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario> }): JSX.Element | null
export function DialogoSolicitarPieza(props: { abierto: boolean; tipo: string; variante: 'sinStock' | 'limite'; stock: number; inicial: string; onConfirmar: (descripcion: string) => void; onCancelar: () => void }): JSX.Element
export function DialogoDescripcionSolicitud(props: { abierto: boolean; inicial: string; onGuardar: (descripcion: string) => void; onCancelarSolicitud: () => void; onCancelar: () => void }): JSX.Element

// [Task 15]
export function OtrasAcciones(props: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardarAccion: (id: number) => void }): JSX.Element | null
export function ZonaGuardar(props: { estado: EstadoFormulario; onPulsar: () => void }): JSX.Element | null
// formulario/useGuardado.ts  [lo crea la Task 13 con guardarFila y fechaGuardado; la Task 15 añade pulsarGuardar y guardarAccion; la Task 18, la rama de edición]
export function fechaGuardado(d: Date): string     // 'dd/MM HH:mm' en hora LOCAL del navegador [Task 13]
export function useGuardado(args: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardado: () => void; antesDeCerrar?: () => Promise<void> }): {
  pulsarGuardar: () => void                      // 1.er clic PEDIR_CONFIRMACION_GUARDAR; 2.º INICIO_GUARDADO + ejecuta planTerminar / planGuardarCambios
  guardarFila: (prefijo: string) => void         // ídem dos clics sobre la fila
  guardarAccion: (id: number) => void            // decide con accionPideConfirmacion(accion): true → PEDIR_CONFIRMACION_ACCION; false → INICIO_GUARDAR_ACCION + POST.
                                                 // Sin refs propias: el "otro clic" tras un fallo lo recuerda el reductor (pideOtroClic). La vista pinta
                                                 // '✓ Confirmar' / '✓ Guardar' según accion.confirmando.
}
// antesDeCerrar: parámetro declarado desde la Task 13 y esperado por la Task 15 tras GUARDADO_COMPLETADO y antes de onGuardado (su fallo se
// ignora); lo inyecta la Task 16 (descartar borrador tras guardar). onGuardado cierra; la recarga la hace el desmontaje del cargador.
// "Terminar asignación" recalcula planTerminar con el estado resultante tras cada AGOTADO_REGISTRADO (no guarda una copia del plan del clic).

// [Task 16]
export function useBorrador(args: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; borradorJson: string | null; activo: boolean }): {
  listo: boolean                                 // borrador aplicado (o no había): hasta entonces no se escribe
  volcarAhora: () => Promise<void>               // cancela el temporizador y PUT/DELETE ya (silencioso)
  descartar: () => Promise<void>                 // tras guardado real: cancela, DELETE, y ya no vuelve a escribir
}
export const RETARDO_BORRADOR_MS = 2000
/** Se resuelve cuando no quedan escrituras de borrador en vuelo (cola serie de módulo). Para los tests que montan el formulario real:
 *  afterEach(async () => { cleanup(); await borradorEnReposo() }) — ver Global Constraints. */
export function borradorEnReposo(): Promise<void>
// El autoguardado (2 s) se reprograma con estado.revision; el volcado inmediato lo dispara estado.volcados; aplicar el borrador (REEMPLAZAR) no escribe.

// [Task 18]
export function DialogoSalirSinGuardar(props: { abierto: boolean; onSalir: () => void; onCancelar: () => void }): JSX.Element   // envoltorio de ConfirmDialog
```

#### W12. Rutas [Task 12 nuevo · Task 17 glass · Task 18 editar]

```tsx
{ path: '/reparaciones/pendientes', element: <PendientesPage tipo="REPARACION" />, children: [
    { path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass={false} /> } ] },
{ path: '/reparaciones/pendientes/glass', element: <PendientesPage tipo="GLASS" />, children: [
    { path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass /> } ] },
{ path: '/reparaciones/historial', element: <HistorialPage tipo="REPARACION" />, children: [
    { element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="historial" /> }] } ] },
{ path: '/reparaciones/historial/glass', element: <HistorialPage tipo="GLASS" />, children: [ …'editar/:idRep' origen="historial-glass" ] },
{ path: '/reparaciones/imeis/:imei', element: <ImeiDetallePage />, children: [ …'editar/:idRep' origen="imei" ] },
```
Cada página pinta `<Outlet />` al final de su JSX. "Añadir glass" sigue deshabilitado (con `TOOLTIP_FORMULARIO`) hasta la Task 17, que lo habilita y deja `PendientesPage` sin ese import; la Task 18 borra la constante. En `FormularioNuevoRuta` el modo lo decide el prefijo del id (`AG…` → glass); la prop `glass` solo decide a qué lista se vuelve.

#### W13. Nombres accesibles y `data-testid` (cuando el texto no basta)

| Elemento | Selector de test |
|---|---|
| Diálogo del formulario | `role="dialog"`, `aria-label` = `tituloPestana(estado)`; botón `✕` con `aria-label="Cerrar formulario"` |
| Combo de modelo / de SKU | `combobox` `"Filtrar por modelo"` / `"SKU de <tipo>"` |
| Fila de componente | `data-testid="fila-<prefijo>"`, con `data-estado="normal\|guardada\|editada\|yaReparado\|sinSku"` |
| `+` / `-` | botones `"Sumar <tipo>"` / `"Restar <tipo>"`; contador `data-testid="contador-<prefijo>"` |
| Stock | `data-testid="stock-<prefijo>"` |
| "Reutilizado" | `checkbox` `"Reutilizado <tipo>"` (texto visible "Reutilizado") |
| Papelera de observación | botón `"Borrar observación de <tipo>"`; deshabilitada con `!fila.controles.observacion` (igual que "Añadir observación") |
| Botón derecho | `data-testid="boton-derecho-<prefijo>"` |
| Sub-fila | `data-testid="subfila-<prefijo>"`, `data-variante="sinStock\|limite\|confirmada"`; lápiz `"Editar descripción de solicitud de <tipo>"` |
| Banda de aviso | `data-testid="banda-conflicto"`, `"banda-incidencia"`, `"banda-borrador"` |
| Otras acciones | sección `data-testid="otras-acciones"`; badge `data-testid="otras-acciones-badge"`; línea `data-testid="accion-<n>"` (n = posición desde 1); papelera `"Quitar acción"` |
| Zona de guardar | `data-testid="zona-guardar"` |
| Campana | botón `"Notificaciones"` (`data-testid="campana"`, `data-pulso="true\|false"`, `data-encendida`); badge `data-testid="campana-badge"` |
| Panel | `data-testid="panel-notificaciones"`; pestañas `role="tab"` "Solicitudes" / "Alertas" |
| Tarjetas | `data-testid="tarjeta-solicitud-U-<idRc>"` / `"tarjeta-solicitud-P-<idSol>"` (`data-grupo="pendiente\|rechazada"`), `data-testid="tarjeta-alerta-<idCom>"`; papelera `"Borrar solicitud"` |

#### W14. `src/modules/taller/notificaciones` [Task 19; la vista del panel, Task 20]

```ts
// alertas.ts (pura)
export function esAlerta(c: Componente): boolean                         // master (idComMaster == null), activo, stock <= stockMinimo
export type AlertaStock = { componente: Componente; nivel: 'sinStock' | 'stockBajo' }
/** Primero stock === 0, después stock > 0, cada grupo en el orden recibido. Los de stock negativo cuentan en hayAlertas pero no salen. */
export function alertasOrdenadas(componentes: Componente[]): AlertaStock[]
export function hayAlertas(componentes: Componente[]): boolean

// solicitudes.ts (pura)
export type TarjetaDatos =
  | { clase: 'U'; id: number; grupo: 'pendiente' | 'rechazada'; sol: SolicitudResumen }
  | { clase: 'P'; id: number; grupo: 'pendiente' | 'rechazada'; sol: SolicitudStock }
export type ListasSolicitudes = { pendientes: TarjetaDatos[]; rechazadas: TarjetaDatos[] }   // urgentes primero, preventivas después
export function componerListas(d: { urgPend: SolicitudResumen[]; prevPend: SolicitudStock[]; urgRech: SolicitudResumen[]; prevRech: SolicitudStock[] }): ListasSolicitudes
/** Conjunto de identificadores con su grupo y clase: si no cambia, el panel conserva las tarjetas que ya pinta. */
export function firma(l: ListasSolicitudes): string
export function lineaInfo(t: TarjetaDatos): string                       // separador '  ·  ', fechas dd/MM/yyyy HH:mm Madrid, reglas de la ficha

// api.ts
export { CLAVE_NOTIF } from '../formulario/api'
export const CLAVE_NOTIF_CONTADOR = ['notificaciones', 'contador'] as const
export const CLAVE_NOTIF_SOLICITUDES = ['notificaciones', 'solicitudes'] as const
export const CLAVE_NOTIF_COMPONENTES = ['notificaciones', 'componentes'] as const
/** GET /api/solicitudes/count + /api/solicitudes-stock/count → suma. refetchInterval = useIntervaloRefresco(activo); meta.silenciarError. */
export function useContadorNotificaciones(activo: boolean): UseQueryResult<number>
/** Las cuatro listas (estado=PENDIENTE|RECHAZADA de /api/solicitudes y /api/solicitudes-stock). enabled = abierto; sondea con el intervalo general. */
export function useSolicitudesPanel(abierto: boolean): UseQueryResult<ListasSolicitudes>
/** GET /api/componentes/gestionados. `sondea` = panel abierto. La primera carga (pulso) SÍ avisa del error; los sondeos, no. */
export function useComponentesGestionados(sondea: boolean): UseQueryResult<Componente[]>
export function useCambiarEstadoSolicitud(): UseMutationResult<void, unknown, { clase: 'U' | 'P'; id: number; estado: 'PENDIENTE' | 'RECHAZADA' }>   // PATCH …/estado; onSettled invalida CLAVE_NOTIF
export function useQuitarSolicitud(): UseMutationResult<void, unknown, { clase: 'U' | 'P'; id: number }>      // U: PATCH /api/solicitudes/{idRc}/limpiar · P: DELETE /api/solicitudes-stock/{idSol}; invalida CLAVE_NOTIF
/** Pide las PENDIENTE en ese momento y las rechaza una a una (urgentes, luego preventivas); al primer error se detiene y lanza.
 *  Con éxito invalida CLAVE_NOTIF_SOLICITUDES y CLAVE_NOTIF_CONTADOR (no las alertas). */
export function useRechazarTodo(): UseMutationResult<void, unknown, void>

// Vista
export function Campana(): JSX.Element | null                            // null si no es SUPERTECNICO
export function PanelNotificaciones(props: { pestanaInicial: 'solicitudes' | 'alertas'; anclaRef: RefObject<HTMLElement | null>; onCerrar: () => void }): JSX.Element
export function TarjetaSolicitud(props: { datos: TarjetaDatos; alterna: boolean; onRechazar: () => void; onRecuperar: () => void; onQuitar: () => void }): JSX.Element
export function TarjetaAlerta(props: { alerta: AlertaStock; alterna: boolean }): JSX.Element
```
`notificaciones/test/handlers.ts`: `handlersNotificaciones(e?: { urgPend?; prevPend?; urgRech?; prevRech?; gestionados? }): RequestHandler[]` (los `count` se derivan de las listas pendientes; las escrituras responden 200/204) y `conRegistroNotificaciones(e?)` con el mismo `LlamadaRegistrada`.

---

## Tareas

### Task 1: Servidor — propiedad de la asignación y técnico del token en las escrituras del formulario

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/security/PropiedadAsignacion.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`, `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Test: `src/test/java/com/reparaciones/servidor/security/PropiedadAsignacionTest.java`, `src/test/java/com/reparaciones/servidor/controller/PropiedadAsignacionControllersTest.java`, `src/test/java/com/reparaciones/servidor/controller/RolesReparacionFormularioTest.java`

**Interfaces:**
- Consumes: `UsuarioPrincipal.getRol()` / `getIdTec()` (existentes), `FiltroTecnico` (modelo de estilo), `BorradorDAO.get/guardar/eliminar` (existentes), `JwtUtil.generateToken(UsuarioPrincipal)` (tests MockMvc).
- Produces:
  - `PropiedadAsignacion.tecnicoEfectivo(UsuarioPrincipal principal, Integer idTecDueno): int` — devuelve siempre el técnico del token; lanza `ResponseStatusException(403, MSG_NO_ES_TUYA)` si el token no tiene técnico o la asignación es de otro; con `idTecDueno == null` no lanza.
  - `PropiedadAsignacion.exigirSupertecnico(UsuarioPrincipal principal): void` — lanza `ResponseStatusException(403, MSG_SOLO_SUPERTECNICO)` si el rol no es `SUPERTECNICO`.
  - Constantes `PropiedadAsignacion.MSG_NO_ES_TUYA = "Solo puedes trabajar sobre tus propias asignaciones"` y `PropiedadAsignacion.MSG_SOLO_SUPERTECNICO = "Solo el supertécnico puede corregir una reparación ya hecha"`.
  - `ReparacionDAO.getIdTecDeAsignacion(String idAsignacion): Integer` (`null` si no existe).
  - `ReparacionController`: `insertarCompleta`, `guardarFilaIndividual`, `agotarComponente`, `completar` aplican la regla; `getBorrador(String idRep, UsuarioPrincipal principal)`, `guardarBorrador(String idRep, BorradorRequest req, UsuarioPrincipal principal)`, `eliminarBorrador(String idRep, UsuarioPrincipal principal)` con `@PreAuthorize("hasAnyRole('SUPERTECNICO','TECNICO')")` y la regla; `editarReparacion` y `getDetalleEdicion` con `@PreAuthorize("hasRole('SUPERTECNICO')")`. Los tipos de retorno no cambian en esta tarea (los tipa la Task 3). Los records de petición conservan campos y tipos (`int idTec` incluido).

**Ficha:** `formulario.md` — "Roles: flujo nuevo y Glass, TECNICO y SUPERTECNICO sobre sus propias asignaciones…" (parte servidor); "403 al abrir (asignación de otro técnico, o edición sin ser SUPERTECNICO)…" (parte servidor); "Las filas y acciones nuevas añadidas en edición **conservan el técnico original**…" (parte servidor); párrafo inicial de "Llamadas a la API". Spec §5.1 y §5.2.

- [x] **Step 1: Crear la rama del servidor desde `main`**

```bash
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor status --short   # vacío
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor checkout main
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor rev-parse --short HEAD   # 6e2629a
git -C /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor checkout -b feature/web-formulario
```
Expected: `Switched to a new branch 'feature/web-formulario'`. Todos los comandos siguientes de las Tasks 1–3 se ejecutan desde `/c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor` y con el entorno de Maven cargado:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
```
(la herramienta de shell no conserva variables entre llamadas: repetir las dos líneas delante de cada `mvn`).

- [x] **Step 2: Escribir el test unitario de la regla**

`src/test/java/com/reparaciones/servidor/security/PropiedadAsignacionTest.java`:

```java
package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Regla de las escrituras del formulario (spec web-formulario 2026-09-19 §5.1). */
class PropiedadAsignacionTest {

    private static final UsuarioPrincipal TECNICO = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private static final UsuarioPrincipal SUPER   = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private static final UsuarioPrincipal ADMIN   = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void duenoRecibeSuIdTec() {
        assertEquals(4, PropiedadAsignacion.tecnicoEfectivo(TECNICO, 4));
        assertEquals(3, PropiedadAsignacion.tecnicoEfectivo(SUPER, 3));
    }

    @Test void asignacionAjenaEs403ConMensaje() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(TECNICO, 9));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        assertEquals("Solo puedes trabajar sobre tus propias asignaciones", PropiedadAsignacion.MSG_NO_ES_TUYA);
    }

    @Test void tokenSinTecnicoEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(ADMIN, 4));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(ADMIN, null)).getStatusCode().value());
    }

    @Test void supertecnicoSobreAsignacionAjenaTambienEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(SUPER, 4));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
    }

    @Test void asignacionInexistenteNoLanza() {
        // Dueño nulo = la asignación no existe: la regla deja pasar y el DAO responde su 409.
        assertEquals(4, PropiedadAsignacion.tecnicoEfectivo(TECNICO, null));
        assertEquals(3, PropiedadAsignacion.tecnicoEfectivo(SUPER, null));
    }

    @Test void exigirSupertecnicoDejaPasarSoloAlSupertecnico() {
        assertDoesNotThrow(() -> PropiedadAsignacion.exigirSupertecnico(SUPER));
        for (UsuarioPrincipal otro : new UsuarioPrincipal[] { TECNICO, ADMIN }) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> PropiedadAsignacion.exigirSupertecnico(otro));
            assertEquals(403, ex.getStatusCode().value());
            assertEquals(PropiedadAsignacion.MSG_SOLO_SUPERTECNICO, ex.getReason());
        }
        assertEquals("Solo el supertécnico puede corregir una reparación ya hecha",
                PropiedadAsignacion.MSG_SOLO_SUPERTECNICO);
    }
}
```

- [x] **Step 3: Ejecutarlo y ver que falla por compilación**

```bash
mvn -q test -Dtest=PropiedadAsignacionTest
```
Expected: `COMPILATION ERROR ... cannot find symbol ... PropiedadAsignacion`.

- [x] **Step 4: Implementar `PropiedadAsignacion`**

`src/main/java/com/reparaciones/servidor/security/PropiedadAsignacion.java`:

```java
package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regla única de las escrituras del formulario (spec web-formulario 2026-09-19 §5.1): el técnico de cada
 * trabajo es el del token y solo se trabaja sobre asignaciones propias. Igual para TECNICO y SUPERTECNICO.
 * El {@code idTec} que envíe el cliente en el cuerpo no interviene: los controllers usan el valor que
 * devuelve {@link #tecnicoEfectivo}.
 */
public final class PropiedadAsignacion {

    public static final String MSG_NO_ES_TUYA        = "Solo puedes trabajar sobre tus propias asignaciones";
    public static final String MSG_SOLO_SUPERTECNICO = "Solo el supertécnico puede corregir una reparación ya hecha";

    private PropiedadAsignacion() {}

    /**
     * @param principal   usuario del token (las rutas exigen sesión, nunca es nulo)
     * @param idTecDueno  ID_TEC de la asignación ({@code null} si la asignación no existe)
     * @return el técnico efectivo (siempre el del token)
     * @throws ResponseStatusException 403 (MSG_NO_ES_TUYA) si el token no tiene técnico o la asignación es de otro.
     *         Si {@code idTecDueno} es null NO lanza: deja que el DAO responda su 409 de "ya eliminada o completada".
     */
    public static int tecnicoEfectivo(UsuarioPrincipal principal, Integer idTecDueno) {
        Integer propio = principal.getIdTec();
        if (propio == null || (idTecDueno != null && !idTecDueno.equals(propio))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_NO_ES_TUYA);
        }
        return propio;
    }

    /** @throws ResponseStatusException 403 (MSG_SOLO_SUPERTECNICO) si el rol no es SUPERTECNICO. */
    public static void exigirSupertecnico(UsuarioPrincipal principal) {
        if (!"SUPERTECNICO".equals(principal.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SOLO_SUPERTECNICO);
        }
    }
}
```

- [x] **Step 5: Ejecutar el test y ver que pasa**

```bash
mvn -q test -Dtest=PropiedadAsignacionTest
```
Expected: termina sin errores (código de salida 0; 6 tests).

- [x] **Step 6: Escribir el test de la regla aplicada en el controller (falla por compilación)**

Los tests de controller del proyecto llaman al método Java directamente con DAOs de Mockito (no ejercitan `@PreAuthorize`; eso lo cubre el Step 9). Ojo con Mockito: un método sin programar que devuelve `Integer` responde `0`, no `null`; por eso cada test programa `getIdTecDeAsignacion` explícitamente.

`src/test/java/com/reparaciones/servidor/controller/PropiedadAsignacionControllersTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.security.PropiedadAsignacion;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** La regla de PropiedadAsignacion aplicada en cada escritura del formulario (spec web-formulario §5.1). */
class PropiedadAsignacionControllersTest {

    private static final String ASIG = "A20260916_1";
    private static final String IMEI = "355400000000111";

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final BorradorDAO borradorDao = mock(BorradorDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, borradorDao,
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class));

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    private final List<FilaReparacion> filas = List.of(fila(101));

    private static FilaReparacion fila(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        f.prefijo = "bat";
        return f;
    }

    private static ResponseStatusException rechazo(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    @Test void completaConAsignacionUsaElTecnicoDelTokenEIgnoraElDelCuerpo() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null), tecnico);
        verify(dao).insertarCompleta(filas, IMEI, 4, null, ASIG, null);
        verify(dao).getNombreTecnicoById(4);          // el log nombra al técnico efectivo
        verify(dao, never()).getNombreTecnicoById(9);
    }

    @Test void completaConAsignacionAjenaEs403SinEscribir() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        for (UsuarioPrincipal quien : List.of(tecnico, supertecnico, admin)) {
            ResponseStatusException ex = rechazo(() -> ctl.insertarCompleta(
                    new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null), quien));
            assertEquals(403, ex.getStatusCode().value());
            assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        }
        verify(dao, never()).insertarCompleta(any(), any(), anyInt(), any(), any(), any());
        verifyNoInteractions(logDao);
    }

    @Test void completaSinAsignacionExigeSupertecnicoYConservaElIdTecDelCuerpo() {
        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 4, null, null, "R"), supertecnico);
        verify(dao).insertarCompleta(filas, IMEI, 4, null, null, "R");   // técnico original, no el 3 del token
        verify(dao).getNombreTecnicoById(4);
        verify(dao, never()).getIdTecDeAsignacion(any());
    }

    @Test void completaSinAsignacionSiendoTecnicoEs403() {
        for (UsuarioPrincipal quien : List.of(tecnico, admin)) {
            ResponseStatusException ex = rechazo(() -> ctl.insertarCompleta(
                    new ReparacionController.InsertarCompletaRequest(filas, IMEI, 4, null, null, "R"), quien));
            assertEquals(403, ex.getStatusCode().value());
            assertEquals(PropiedadAsignacion.MSG_SOLO_SUPERTECNICO, ex.getReason());
        }
        verify(dao, never()).insertarCompleta(any(), any(), anyInt(), any(), any(), any());
        verifyNoInteractions(logDao);
    }

    @Test void filasUsaElTecnicoDelToken() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");
        ctl.guardarFilaIndividual(ASIG, new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico);
        verify(dao).guardarFilaIndividual(filas, IMEI, 4, null, ASIG);
        verify(dao).getNombreTecnicoById(4);
        verify(dao, never()).getNombreTecnicoById(9);
    }

    @Test void filasAjenaEs403() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        ResponseStatusException ex = rechazo(() -> ctl.guardarFilaIndividual(ASIG,
                new ReparacionController.GuardarFilaRequest(filas, IMEI, 4, null), tecnico));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        verify(dao, never()).guardarFilaIndividual(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(logDao);
    }

    @Test void agotarAjenaEs403SinTocarStock() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        for (UsuarioPrincipal quien : List.of(tecnico, supertecnico)) {
            ResponseStatusException ex = rechazo(() -> ctl.agotarComponente(ASIG,
                    new ReparacionController.AgotarRequest(102, 1, null), quien));
            assertEquals(403, ex.getStatusCode().value());
        }
        verify(dao, never()).agotarComponente(any(), anyInt(), anyInt(), any());
        verifyNoInteractions(logDao);
    }

    @Test void agotarPropiaLlamaAlDao() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, "sin existencias"), tecnico);
        verify(dao).agotarComponente(ASIG, 102, 1, "sin existencias");
    }

    @Test void borradorLeerGuardarYBorrarSoloSobreAsignacionPropia() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        assertEquals(403, rechazo(() -> ctl.getBorrador(ASIG, tecnico)).getStatusCode().value());
        assertEquals(403, rechazo(() -> ctl.guardarBorrador(ASIG,
                new ReparacionController.BorradorRequest("{}"), tecnico)).getStatusCode().value());
        assertEquals(403, rechazo(() -> ctl.eliminarBorrador(ASIG, tecnico)).getStatusCode().value());
        verifyNoInteractions(borradorDao);

        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.getBorrador(ASIG, tecnico);
        ctl.guardarBorrador(ASIG, new ReparacionController.BorradorRequest("{\"modelo\":\"13\"}"), tecnico);
        ctl.eliminarBorrador(ASIG, tecnico);
        verify(borradorDao).get(ASIG);
        verify(borradorDao).guardar(ASIG, "{\"modelo\":\"13\"}");
        verify(borradorDao).eliminar(ASIG);
    }

    @Test void completarAjenaEs403() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        ResponseStatusException ex = rechazo(() -> ctl.completar(ASIG, tecnico));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        verify(dao, never()).completar(any());
        verifyNoInteractions(logDao);
    }

    @Test void completarPropiaLlamaAlDao() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.completar(ASIG, tecnico);
        verify(dao).completar(ASIG);

        // Asignación inexistente: la regla no lanza y el DAO responde su 409 como hasta ahora.
        when(dao.getIdTecDeAsignacion("A20260916_99")).thenReturn(null);
        ctl.completar("A20260916_99", tecnico);
        verify(dao).completar("A20260916_99");
    }
}
```

```bash
mvn -q test -Dtest=PropiedadAsignacionControllersTest
```
Expected: `COMPILATION ERROR` (`getIdTecDeAsignacion` no existe, los records de petición son `private`, `getBorrador`/`guardarBorrador`/`eliminarBorrador` no reciben el principal).

- [x] **Step 7: `getIdTecDeAsignacion` en el DAO y la regla en el controller**

En `ReparacionDAO.java`, justo después de `getImeiByIdRep`:

```java
    /** ID_TEC de una fila de Reparacion (asignación abierta o cerrada), o {@code null} si no existe.
     *  Lo usa PropiedadAsignacion para saber de quién es la asignación antes de escribir. */
    public Integer getIdTecDeAsignacion(String idAsignacion) {
        List<Integer> result = jdbc.query(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?",
                (rs, row) -> rs.getInt(1), idAsignacion);
        return result.isEmpty() ? null : result.get(0);
    }
```

En `ReparacionController.java`:

1. Añadir `import com.reparaciones.servidor.security.PropiedadAsignacion;` (junto al de `FiltroTecnico`).

2. Quitar `private` a cuatro records de petición para que el test (mismo paquete) pueda construirlos; campos y tipos intactos. El nombre del esquema OpenAPI no depende de la visibilidad:

```java
    record BorradorRequest(String contenido) {}   // package-private: lo construye el test
```
```java
    record InsertarCompletaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                   String idRepAnterior, String idAsignacion, String categoria) {}   // package-private: lo construye el test
```
```java
    record AgotarRequest(int idCom, int cantidad, String descripcion) {}   // package-private: lo construye el test
```
```java
    record GuardarFilaRequest(List<FilaReparacion> filas, String imei, int idTec,
                              String idRepAnterior) {}   // package-private: lo construye el test
```

3. Sustituir `insertarCompleta` entero por:

```java
    /**
     * Con {@code idAsignacion} (flujo nuevo y Glass): el técnico es el del token y la asignación debe ser
     * suya; el {@code idTec} del cuerpo se ignora. Sin {@code idAsignacion} (filas y acciones añadidas al
     * editar una reparación ya hecha): exige SUPERTECNICO y conserva el {@code idTec} del cuerpo, que es el
     * técnico original del trabajo (spec web-formulario §5.1).
     */
    @PostMapping("/completa")
    @ResponseStatus(HttpStatus.CREATED)
    public void insertarCompleta(@RequestBody InsertarCompletaRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        int idTec;
        if (req.idAsignacion() != null) {
            idTec = PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(req.idAsignacion()));
        } else {
            PropiedadAsignacion.exigirSupertecnico(principal);
            idTec = req.idTec();
        }
        dao.insertarCompleta(req.filas(), req.imei(), idTec,
                req.idRepAnterior(), req.idAsignacion(), req.categoria());
        String modelo = dao.getModeloByImei(req.imei());
        String tecnico = dao.getNombreTecnicoById(idTec);
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(req.idAsignacion()) || "G".equals(req.categoria())
                        ? "COMPLETAR_GLASS" : "COMPLETAR_REPARACION",
                "ID_REP: " + req.idAsignacion() + ", IMEI: " + req.imei() +
                ", MODELO: " + modelo + ", TECNICO: " + tecnico + componentesDe(req.filas()));
    }
```

4. Sustituir `completar` entero por:

```java
    @PatchMapping("/{idRep}/completar")
    public void completar(@PathVariable String idRep,
                          @AuthenticationPrincipal UsuarioPrincipal principal) {
        PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idRep));
        ReparacionResumen rep = dao.getAsignacionAnyById(idRep).orElse(null);
        dao.completar(idRep);
        String detalle = rep != null
                ? "ID_REP: " + idRep + ", IMEI: " + rep.getImei() +
                  ", MODELO: " + (rep.getModelo() != null ? rep.getModelo() : "?") +
                  ", TECNICO: " + rep.getNombreTecnico()
                : "ID_REP: " + idRep;
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(idRep) ? "COMPLETAR_GLASS" : "COMPLETAR_REPARACION", detalle);
    }
```

5. Sustituir `agotarComponente` entero por:

```java
    @PostMapping("/{idAsignacion}/agotar-componente")
    @ResponseStatus(HttpStatus.CREATED)
    public void agotarComponente(@PathVariable String idAsignacion,
                                  @RequestBody AgotarRequest req,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idAsignacion));
        dao.agotarComponente(idAsignacion, req.idCom(), req.cantidad(), req.descripcion());
        String tipo = dao.getTipoComponenteById(req.idCom());
        String imei = dao.getImeiByIdRep(idAsignacion);
        logDao.insertar(principal.getIdUsu(), "AGOTAR_COMPONENTE",
                "ID_ASIG: " + idAsignacion + (imei != null ? ", IMEI: " + imei : "") + ", TIPO: " + tipo + ", CANT: " + req.cantidad());
    }
```

6. Sustituir `guardarFilaIndividual` entero por (el tipo de retorno sigue siendo `Map<String, String>`; lo tipa la Task 3):

```java
    @PostMapping("/{idAsignacion}/filas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> guardarFilaIndividual(@PathVariable String idAsignacion,
                                                     @RequestBody GuardarFilaRequest req,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        int idTec = PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idAsignacion));
        String idRep = dao.guardarFilaIndividual(req.filas(), req.imei(), idTec,
                req.idRepAnterior(), idAsignacion);
        String tecnico = dao.getNombreTecnicoById(idTec);
        logDao.insertar(principal.getIdUsu(),
                esGlassAsig(idAsignacion) ? "GUARDAR_FILA_INDIVIDUAL_GLASS" : "GUARDAR_FILA_INDIVIDUAL",
                "ID_REP: " + idRep + ", ID_ASIG: " + idAsignacion +
                ", IMEI: " + req.imei() + ", TECNICO: " + tecnico + componentesDe(req.filas()));
        return Map.of("value", idRep);
    }
```

7. Sustituir los tres métodos del bloque "borrador del modal" por (la anotación `@PreAuthorize` se deja **como está** en este paso; la cambia el Step 10 tras ver fallar su test):

```java
    // ── borrador del modal ─────────────────────────────────────────────────────
    // La variable de ruta se llama idRep en el contrato, pero es el id de la asignación: el borrador
    // solo se lee, guarda y borra sobre una asignación propia (PropiedadAsignacion).

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @GetMapping("/{idRep}/borrador")
    public Map<String, String> getBorrador(@PathVariable String idRep,
                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idRep));
        return java.util.Collections.singletonMap("contenido", borradorDao.get(idRep));
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @PutMapping("/{idRep}/borrador")
    public void guardarBorrador(@PathVariable String idRep, @RequestBody BorradorRequest req,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idRep));
        borradorDao.guardar(idRep, req.contenido());
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN','TECNICO')")
    @DeleteMapping("/{idRep}/borrador")
    public void eliminarBorrador(@PathVariable String idRep,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idRep));
        borradorDao.eliminar(idRep);
    }
```

No tocar `actualizarPorCerrar`, `actualizarEntregaGlass`, `marcarLlegadaGlass` ni `deshacerLlegadaGlass` (ya comprueban al dueño a mano y quedan fuera de esta tarea).

- [x] **Step 8: Ejecutar el test del controller y ver que pasa**

```bash
mvn -q test -Dtest=PropiedadAsignacionControllersTest
```
Expected: termina sin errores (11 tests).

- [x] **Step 9: Test de roles con MockMvc (falla)**

Copia la cabecera de `OpenApiContractTest` (contexto completo sin base de datos). El `JwtAuthFilter` construye el principal desde el token, así que `JwtUtil.generateToken(new UsuarioPrincipal(...))` basta; los `@MockBean` evitan que el caso permitido toque MariaDB.

`src/test/java/com/reparaciones/servidor/controller/RolesReparacionFormularioTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.BorradorDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** @PreAuthorize de la edición y del borrador (spec web-formulario §5.1 y §5.2), con la cadena de seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesReparacionFormularioTest {

    private static final String CUERPO_EDITAR =
            "{\"idComNuevo\":101,\"esReutilizadoNuevo\":false,\"observacionNueva\":null,"
            + "\"nNuevas\":1,\"updatedAt\":\"2026-09-16T07:02:00\"}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean ReparacionDAO dao;
    @MockBean ReparacionComponenteDAO rcDao;
    @MockBean LogDAO logDao;
    @MockBean BorradorDAO borradorDao;
    @MockBean ComponenteDAO componenteDao;
    @MockBean DificultadPuntosDAO dificultadDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private int editarComo(String autorizacion) throws Exception {
        return mvc.perform(put("/api/reparaciones/R20260916_5")
                        .header("Authorization", autorizacion)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EDITAR))
                .andReturn().getResponse().getStatus();
    }

    @Test void editarSiendoTecnicoEs403() throws Exception {
        assertEquals(403, editarComo(tecnico()));
        verifyNoInteractions(dao);
    }

    @Test void editarSiendoAdminEs403() throws Exception {
        assertEquals(403, editarComo(admin()));
        verifyNoInteractions(dao);
    }

    @Test void editarSiendoSupertecnicoLlegaAlDao() throws Exception {
        assertEquals(200, editarComo(supertecnico()));
        verify(dao).editarReparacion("R20260916_5", 101, false, null, 1, LocalDateTime.of(2026, 9, 16, 7, 2, 0));
    }

    @Test void detalleEdicionSiendoTecnicoEs403() throws Exception {
        int status = mvc.perform(get("/api/reparaciones/R20260916_5/detalle-edicion")
                .header("Authorization", tecnico())).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao);
    }

    @Test void borradorSiendoAdminEs403() throws Exception {
        int status = mvc.perform(get("/api/reparaciones/A20260916_1/borrador")
                .header("Authorization", admin())).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        verifyNoInteractions(dao, borradorDao);
    }
}
```

```bash
mvn -q test -Dtest=RolesReparacionFormularioTest
```
Expected: FAIL en `editarSiendoTecnicoEs403`, `editarSiendoAdminEs403` y `detalleEdicionSiendoTecnicoEs403` (`expected: <403> but was: <200>`) y en `borradorSiendoAdminEs403` (el 403 ya llega por la regla, pero `verifyNoInteractions(dao…)` falla porque se consultó `getIdTecDeAsignacion`). `editarSiendoSupertecnicoLlegaAlDao` pasa.

- [x] **Step 10: Roles de la edición y del borrador**

En `ReparacionController.java`:

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @GetMapping("/{idRep}/detalle-edicion")
    public ReparacionDAO.DetalleEdicion getDetalleEdicion(@PathVariable String idRep) {
        return dao.getDetalleEdicion(idRep);
    }
```

Anteponer `@PreAuthorize("hasRole('SUPERTECNICO')")` a `@PutMapping("/{idRep}")` de `editarReparacion` (el cuerpo del método no cambia):

```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PutMapping("/{idRep}")
    public void editarReparacion(@PathVariable String idRep, @RequestBody EditarRequest req,
                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
```

Y en los tres métodos del borrador, sustituir la anotación por:

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO','TECNICO')")
```

```bash
mvn -q test -Dtest=RolesReparacionFormularioTest
```
Expected: termina sin errores (5 tests).

- [x] **Step 11: Toda la suite en verde**

```bash
mvn -q test
```
Expected: sin fallos (la suite anterior + 22 tests nuevos). `OpenApiContractTest` sigue pasando: `@AuthenticationPrincipal` no entra en el contrato y los records conservan nombre y campos. Si algún test previo llamaba a `getBorrador(String)` con la firma antigua, `mvn` lo señalará por compilación: no hay ninguno en la suite actual.

- [x] **Step 12: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/security/PropiedadAsignacion.java src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/security/PropiedadAsignacionTest.java src/test/java/com/reparaciones/servidor/controller/PropiedadAsignacionControllersTest.java src/test/java/com/reparaciones/servidor/controller/RolesReparacionFormularioTest.java
git commit -m "feat(servidor): el técnico de las escrituras del formulario sale del token y la asignación debe ser propia; la edición es del supertécnico"
```

---

### Task 2: Servidor — roles de solicitudes de stock y del ajuste de stock; autodetección de chasis por SKU

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/controller/SolicitudStockController.java`, `src/main/java/com/reparaciones/servidor/controller/ComponenteController.java`, `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/RolesSolicitudesStockTest.java`, `src/test/java/com/reparaciones/servidor/dao/ReparacionDAOChasisTest.java`

**Interfaces:**
- Consumes: nada de la Task 1 (solo la rama `feature/web-formulario`, ya creada). `JwtUtil.generateToken(UsuarioPrincipal)` en el test MockMvc.
- Produces:
  - Roles: `POST /api/solicitudes-stock` → `hasAnyRole('TECNICO','SUPERTECNICO')`; `GET /api/solicitudes-stock` y `GET /api/solicitudes-stock/count` → `hasAnyRole('SUPERTECNICO','ADMIN')`; `PATCH /api/solicitudes-stock/{idSol}/estado` y `DELETE /api/solicitudes-stock/{idSol}` → `hasRole('SUPERTECNICO')`; `PATCH /api/componentes/{idCom}/stock` → `hasRole('SUPERTECNICO')`. `/api/solicitudes` no cambia (ya es SUPERTECNICO a nivel de clase).
  - `ReparacionDAO.marcarChasisSiProcede(String idAsignacion, int idCom)` (privado), con el SQL `UPDATE Reparacion SET ES_CHASIS = TRUE, UPDATED_AT = UPDATED_AT WHERE ID_REP = ? AND ES_CHASIS = FALSE AND EXISTS (SELECT 1 FROM Componente c WHERE c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%')`, llamado en `insertarCompleta` (cada fila de uso y cada fila `esSolicitud` con asignación), `guardarFilaIndividual` (ídem) y `agotarComponente` (tras insertar la solicitud).

**Ficha:** ninguna casilla de vista; cubre spec §5.3 y §5.4 y da soporte a `notificaciones.md` "Roles: solo SUPERTECNICO…".

- [x] **Step 1: Test de roles con MockMvc (falla)**

Todos los comandos se ejecutan desde el repo del servidor, en la rama `feature/web-formulario`, con el entorno de Maven:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git branch --show-current   # feature/web-formulario
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
```

`src/test/java/com/reparaciones/servidor/controller/RolesSolicitudesStockTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Roles de /api/solicitudes-stock y del ajuste de stock (spec web-formulario §5.3), con la cadena de seguridad real. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "jwt.secret=secreto-solo-para-tests-de-32-caracteres-o-mas",
        "jwt.expiration=86400000",
        "springdoc.api-docs.enabled=true"
})
class RolesSolicitudesStockTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean SolicitudStockDAO dao;
    @MockBean ComponenteDAO componenteDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null)); }

    private int status(MockHttpServletRequestBuilder peticion, String autorizacion) throws Exception {
        return mvc.perform(peticion.header("Authorization", autorizacion)).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test void crearTecnicoYSupertecnico201AdminEs403() throws Exception {
        String cuerpo = "{\"idCom\":111,\"descripcion\":null}";
        assertEquals(403, status(json(post("/api/solicitudes-stock"), cuerpo), admin()));
        verifyNoInteractions(dao);
        assertEquals(201, status(json(post("/api/solicitudes-stock"), cuerpo), tecnico()));
        verify(dao).insertar(111, 8, null);
        assertEquals(201, status(json(post("/api/solicitudes-stock"), cuerpo), supertecnico()));
        verify(dao).insertar(111, 7, null);
    }

    @Test void listarYContarSupertecnicoYAdmin200TecnicoEs403() throws Exception {
        assertEquals(403, status(get("/api/solicitudes-stock"), tecnico()));
        assertEquals(403, status(get("/api/solicitudes-stock/count"), tecnico()));
        verifyNoInteractions(dao);
        for (String quien : new String[] { supertecnico(), admin() }) {
            assertEquals(200, status(get("/api/solicitudes-stock?estado=PENDIENTE"), quien));
            assertEquals(200, status(get("/api/solicitudes-stock/count"), quien));
        }
    }

    @Test void cambiarEstadoYBorrarSoloSupertecnico() throws Exception {
        String cuerpo = "{\"estado\":\"RECHAZADA\"}";
        for (String quien : new String[] { tecnico(), admin() }) {
            assertEquals(403, status(json(patch("/api/solicitudes-stock/701/estado"), cuerpo), quien));
            assertEquals(403, status(delete("/api/solicitudes-stock/701"), quien));
        }
        verifyNoInteractions(dao);
        assertEquals(200, status(json(patch("/api/solicitudes-stock/701/estado"), cuerpo), supertecnico()));
        verify(dao).actualizarEstado(701, "RECHAZADA");
        assertEquals(204, status(delete("/api/solicitudes-stock/701"), supertecnico()));
        verify(dao).borrar(701);
    }

    @Test void ajusteDeStockSoloSupertecnico() throws Exception {
        String cuerpo = "{\"delta\":1}";
        assertEquals(403, status(json(patch("/api/componentes/101/stock"), cuerpo), tecnico()));
        assertEquals(403, status(json(patch("/api/componentes/101/stock"), cuerpo), admin()));
        verifyNoInteractions(componenteDao);
        assertEquals(200, status(json(patch("/api/componentes/101/stock"), cuerpo), supertecnico()));
        verify(componenteDao).actualizarStock(101, 1);
    }
}
```

```bash
mvn -q test -Dtest=RolesSolicitudesStockTest
```
Expected: FAIL en los cuatro tests con `expected: <403> but was: <201>` / `<200>` (las anotaciones de rol por método llegan en el Step 2).

- [x] **Step 2: Anotar los roles**

`SolicitudStockController.java`: añadir `import org.springframework.security.access.prepost.PreAuthorize;` y una anotación por método (cuerpos intactos):

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping
    public List<SolicitudStock> getSolicitudes(
            @RequestParam(required = false) String estado) {
        return dao.getSolicitudes(estado);
    }

    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/count")
    public Map<String, Object> count() {
        return Map.of("value", dao.contarPendientes());
    }

    @PreAuthorize("hasAnyRole('TECNICO','SUPERTECNICO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void insertar(@RequestBody InsertarRequest req,
                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.insertar(req.idCom(), principal.getIdUsu(), req.descripcion());
        logDao.insertar(principal.getIdUsu(), "SOLICITAR_STOCK",
                "ID_COM: " + req.idCom());
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idSol}/estado")
    public void actualizarEstado(@PathVariable int idSol,
                                  @RequestBody Map<String, String> body,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String estado = body.get("estado");
        dao.actualizarEstado(idSol, estado);
        String accion = "RECHAZADA".equalsIgnoreCase(estado)
                ? "RECHAZAR_SOLICITUD_STOCK" : "GESTIONAR_SOLICITUD_STOCK";
        logDao.insertar(principal.getIdUsu(), accion, "ID_SOL: " + idSol + ", ESTADO: " + estado);
    }

    @PreAuthorize("hasRole('SUPERTECNICO')")
    @DeleteMapping("/{idSol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable int idSol,
                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        dao.borrar(idSol);
        logDao.insertar(principal.getIdUsu(), "BORRAR_SOLICITUD_STOCK", "ID_SOL: " + idSol);
    }
```

`ComponenteController.java` (ya importa `PreAuthorize`):

```java
    /** Ajuste manual de stock: solo el supertécnico. El stock del día a día se mueve dentro de las
     *  transacciones del servidor (completar, editar, agotar, recibir pedidos). */
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idCom}/stock")
    public void actualizarStock(@PathVariable int idCom, @RequestBody DeltaRequest req) {
        dao.actualizarStock(idCom, req.delta());
    }
```

```bash
mvn -q test -Dtest=RolesSolicitudesStockTest
```
Expected: termina sin errores (4 tests).

- [x] **Step 3: Test de la autodetección de chasis (falla)**

Estilo de los tests de DAO del proyecto: `JdbcTemplate` de Mockito y se comprueba el SQL emitido. Para llegar hasta la llamada hay que dejar pasar las consultas previas de cada método (`COUNT(*) … FOR UPDATE`, `ID_TEC_ASIGNA`, `nextId`, `resolveToMasterId`, `entregaHeredable`): el mock responde `1` a todo `queryForObject` y a todo `update`, y un mapa vacío a `queryForMap`. Con eso `resolveToMasterId` devuelve `1`, distinto del `idCom` de la fila, y el test demuestra que se compara el SKU **de la fila** y no el del master. La decisión "empieza por cha" vive en el SQL (`EXISTS … LOWER(c.TIPO) LIKE 'cha%'`), así que `piezaQueNoEsChaNoMarca` comprueba que la sentencia emitida lleva ese filtro, que nunca escribe `FALSE` y que no toca `UPDATED_AT`.

`src/test/java/com/reparaciones/servidor/dao/ReparacionDAOChasisTest.java`:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/** Autodetección de chasis por SKU (spec web-formulario 2026-09-19 §5.4). */
class ReparacionDAOChasisTest {

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_1";
    private static final String SQL_CHASIS =
            "UPDATE Reparacion SET ES_CHASIS = TRUE, UPDATED_AT = UPDATED_AT" +
            " WHERE ID_REP = ? AND ES_CHASIS = FALSE" +
            " AND EXISTS (SELECT 1 FROM Componente c WHERE c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%')";

    /** JdbcTemplate que deja pasar las comprobaciones previas de las tres transacciones. */
    private static JdbcTemplate jdbcQueDejaPasar() {
        return mock(JdbcTemplate.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();
            if (metodo.equals("queryForObject")) return 1;
            if (metodo.equals("update")) return 1;
            if (metodo.equals("queryForMap")) return new HashMap<String, Object>();
            return RETURNS_DEFAULTS.answer(invocacion);
        });
    }

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    private static FilaReparacion uso(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        return f;
    }

    private static FilaReparacion solicitud(int idCom) {
        FilaReparacion f = uso(idCom);
        f.esSolicitud = true;
        return f;
    }

    /** Parámetros (idAsignacion, idCom) de cada UPDATE de chasis emitido, en orden. */
    private static List<List<Object>> marcados(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update"))
                .map(i -> Arrays.asList(i.getArguments()))          // varargs ya expandidos: [sql, p1, p2…]
                .filter(args -> SQL_CHASIS.equals(args.get(0)))
                .map(args -> args.subList(1, args.size()))
                .toList();
    }

    @Test void completarConPiezaChaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(uso(101), uso(131)), IMEI, 4, null, ASIG, null);
        assertEquals(List.of(List.of(ASIG, 101), List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void guardarFilaConPiezaChaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).guardarFilaIndividual(List.of(uso(131)), IMEI, 4, null, ASIG);
        assertEquals(List.of(List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void agotarPiezaChaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).agotarComponente(ASIG, 131, 1, null);
        assertEquals(List.of(List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void solicitudChaDentroDeCompletaMarcaLaAsignacion() {
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(solicitud(131)), IMEI, 4, null, ASIG, null);
        assertEquals(List.of(List.of(ASIG, 131)), marcados(jdbc));
    }

    @Test void piezaQueNoEsChaNoMarca() {
        // Quién es "cha" lo decide la base de datos dentro de la propia sentencia: para una batería (101) el
        // EXISTS no se cumple y el UPDATE no toca ninguna fila. Se pasa el idCom de la fila, no el del master.
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).guardarFilaIndividual(List.of(uso(101)), IMEI, 4, null, ASIG);
        assertEquals(List.of(List.of(ASIG, 101)), marcados(jdbc));
        assertTrue(SQL_CHASIS.contains("c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%'"), "el filtro por SKU va en el SQL");
        assertTrue(SQL_CHASIS.contains("AND ES_CHASIS = FALSE"), "solo marca, nunca desmarca");
        assertTrue(SQL_CHASIS.contains("UPDATED_AT = UPDATED_AT"), "no toca UPDATED_AT");
        boolean algunaDesmarca = mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update"))
                .map(i -> String.valueOf(i.getArguments()[0]))
                .anyMatch(sql -> sql.contains("ES_CHASIS = FALSE,") || sql.contains("ES_CHASIS = ?"));
        assertFalse(algunaDesmarca, "ninguna sentencia de estas transacciones quita el chasis");
    }

    @Test void sinAsignacionNoMarca() {
        // Filas añadidas al editar una reparación ya hecha: no hay asignación que marcar.
        JdbcTemplate jdbc = jdbcQueDejaPasar();
        dao(jdbc).insertarCompleta(List.of(uso(131)), IMEI, 4, null, null, "R");
        assertEquals(List.of(), marcados(jdbc));
    }
}
```

```bash
mvn -q test -Dtest=ReparacionDAOChasisTest
```
Expected: FAIL en los cinco primeros (`expected: <[[A20260916_1, 131]]> but was: <[]>` y equivalentes); `sinAsignacionNoMarca` pasa.

- [x] **Step 4: `marcarChasisSiProcede` y sus puntos de llamada**

En `ReparacionDAO.java`, en el bloque "helpers", después de `resolveToMasterId`:

```java
    /**
     * Autodetección de chasis (spec web-formulario §5.4): marca ES_CHASIS = TRUE en la asignación si el SKU
     * del componente de la fila (no el de su master) empieza por "cha". Nunca lo quita: el toggle manual
     * sigue igual. Sin asignación no hace nada. No toca UPDATED_AT (mismo patrón que actualizarChasis).
     */
    private void marcarChasisSiProcede(String idAsignacion, int idCom) {
        if (idAsignacion == null) return;
        jdbc.update(
                "UPDATE Reparacion SET ES_CHASIS = TRUE, UPDATED_AT = UPDATED_AT" +
                " WHERE ID_REP = ? AND ES_CHASIS = FALSE" +
                " AND EXISTS (SELECT 1 FROM Componente c WHERE c.ID_COM = ? AND LOWER(c.TIPO) LIKE 'cha%')",
                idAsignacion, idCom);
    }
```

En `insertarCompleta` (la variante de seis parámetros), dentro del `for`, en las dos ramas:

```java
                creoReparacion = true;
                idComsUsados.add(fila.idCom);
                marcarChasisSiProcede(idAsignacion, fila.idCom);
            } else if (idAsignacion != null) {
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,1,?,'PENDIENTE',?)",
                        idAsignacion, fila.idCom, fila.descripcionSolicitud, fila.cantidad);
                marcarChasisSiProcede(idAsignacion, fila.idCom);
            }
```

En `guardarFilaIndividual`, dentro del `for`, en las dos ramas:

```java
                idRepCreado = idRep;
                idComsUsados.add(fila.idCom);
                marcarChasisSiProcede(idAsignacion, fila.idCom);
            } else {
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,1,?,'PENDIENTE',?)",
                        idAsignacion, fila.idCom, fila.descripcionSolicitud, fila.cantidad);
                marcarChasisSiProcede(idAsignacion, fila.idCom);
            }
```

En `agotarComponente`, como última sentencia del método, tras el `INSERT INTO Reparacion_componente`:

```java
        jdbc.update(
                "INSERT INTO Reparacion_componente" +
                " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                " VALUES (?,?,1,?,'PENDIENTE',1)",
                idAsignacion, idCom, descripcion);
        marcarChasisSiProcede(idAsignacion, idCom);
    }
```

Las tres son `@Transactional`: si algo posterior falla, la marca se deshace con el resto.

```bash
mvn -q test -Dtest=ReparacionDAOChasisTest
```
Expected: termina sin errores (6 tests).

- [x] **Step 5: Toda la suite en verde**

```bash
mvn -q test
```
Expected: sin fallos (la suite tras la Task 1 + 10 tests nuevos). `OpenApiContractTest` no cambia: `@PreAuthorize` no entra en el contrato.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/SolicitudStockController.java src/main/java/com/reparaciones/servidor/controller/ComponenteController.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/test/java/com/reparaciones/servidor/controller/RolesSolicitudesStockTest.java src/test/java/com/reparaciones/servidor/dao/ReparacionDAOChasisTest.java
git commit -m "feat(servidor): roles en solicitudes de stock y en el ajuste de stock; la asignación pasa a chasis al usar o pedir una pieza cha"
```

---

### Task 3: Servidor — contrato con nullabilidad y respuestas tipadas, snapshot, documentación corta y arranque

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/ValorEntero.java`, `src/main/java/com/reparaciones/servidor/model/ContenidoBorrador.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`, `controller/SolicitudController.java`, `controller/SolicitudStockController.java`, `controller/TelefonoController.java`, `src/main/java/com/reparaciones/servidor/model/FilaReparacion.java`, `model/Componente.java`, `model/SolicitudResumen.java`, `model/SolicitudStock.java`, `model/Reparacion.java`, `src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java` (record `DetalleEdicion`), `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java`, `docs/autorizacion_endpoints.md`, `docs/api_contract.md`

**Interfaces:**
- Consumes: los controllers tal como quedan tras las Tasks 1 y 2; `OpenApiConfig.todasLasPropiedadesRequeridas` (ya marca todo `required`) y `OpenApiConfig.NombreEsquemaAnidado` (nombra los records anidados `<Controller sin sufijo><Record>`; los anidados en `ReparacionDAO` salen como `ReparacionDAO<Record>`).
- Produces:
  - `public record ValorEntero(int value) {}` y `public record ContenidoBorrador(@Schema(nullable = true) String contenido) {}` en `model/`.
  - Respuestas tipadas con el mismo JSON: `GET /api/solicitudes/count` y `GET /api/solicitudes-stock/count` → `ValorEntero`; `GET /api/reparaciones/imei/{imei}/incidencia-activa` → `ValorTexto`; `GET /api/telefonos/{imei}/modelo` → `ValorTexto` (sigue devolviendo `""` sin modelo); `POST /api/reparaciones/{idAsignacion}/filas` → `ValorTexto`; `GET /api/reparaciones/{idRep}/borrador` → `ContenidoBorrador`.
  - Cuerpos tipados: `PATCH /api/solicitudes/{idRc}/estado` → esquema `SolicitudEstadoRequest`; `PATCH /api/solicitudes-stock/{idSol}/estado` → `SolicitudStockEstadoRequest` (ambos `record EstadoRequest(String estado)`).
  - `@Schema(nullable = true)` en: `FilaReparacion.{observacion, prefijo, descripcionSolicitud, estadoSolicitud}`; `Componente.{ultimoPedido, idComMaster}`; `SolicitudResumen.{tipoComponente, descripcion}`; `SolicitudStock.{descripcion}`; `Reparacion.{fechaFin}`; `ReparacionDAO.DetalleEdicion.{observacion}`; `InsertarCompletaRequest.{idRepAnterior, idAsignacion, categoria}`; `GuardarFilaRequest.{idRepAnterior}`; `AgotarRequest.{descripcion}`; `EditarRequest.{observacionNueva}`; `SolicitudStockController.InsertarRequest.{descripcion}`.
  - Esquemas que usa la web (Task 4): `Componente`, `FilaReparacion`, `Reparacion`, `SolicitudResumen`, `SolicitudStock`, `ReparacionDAODetalleEdicion`, `ReparacionDAOAsignacionActiva`, `ReparacionInsertarCompletaRequest`, `ReparacionGuardarFilaRequest`, `ReparacionAgotarRequest`, `ReparacionEditarRequest`, `ReparacionBorradorRequest`, `SolicitudEstadoRequest`, `SolicitudStockEstadoRequest`, `ValorEntero`, `ValorTexto`, `ContenidoBorrador`.
  - `target/openapi.json` regenerado (lo copia la Task 4 a `gestion-reparaciones-web/api/openapi.json`).

**Ficha:** tabla "Llamadas a la API" de `formulario.md` (formas de respuesta) y las llamadas de `notificaciones.md` (`{value}` de los contadores, cuerpos `{estado}`); spec §5.5, §5.7 y §5.8.

**Cómo se regenera `openapi.json` en este repo:** no hay plugin ni script aparte. `OpenApiContractTest.elContratoPublicaLosEsquemasDeLaWeb` levanta el contexto completo de Spring sin base de datos, pide `GET /v3/api-docs` con un token de ADMIN, comprueba rutas, esquemas y nullabilidad y, como última sentencia, escribe el documento (con los códigos de respuesta ordenados para que el volcado sea estable) en `target/openapi.json`. Por tanto `mvn -q test` o `mvn -q test -Dtest=OpenApiContractTest` lo regeneran, y solo si todas las aserciones anteriores pasan. `target/` no está en git: el snapshot versionado vive en la web (`api/openapi.json`), que lo copia y ejecuta `npm run api:types:offline` en la Task 4.

- [x] **Step 1: Ampliar `OpenApiContractTest` (falla)**

Todos los comandos, desde el repo del servidor en `feature/web-formulario`:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git branch --show-current   # feature/web-formulario
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
```

En `elContratoPublicaLosEsquemasDeLaWeb`, justo **antes** de `Path destino = Path.of("target", "openapi.json");`, añadir:

```java
        // ── Sub-proyecto 2 (spec web-formulario §5.5): formulario de reparación y campana ──
        for (String esquema : List.of("Componente", "FilaReparacion", "Reparacion", "SolicitudResumen",
                "SolicitudStock", "ReparacionDAODetalleEdicion", "ReparacionDAOAsignacionActiva",
                "ReparacionInsertarCompletaRequest", "ReparacionGuardarFilaRequest", "ReparacionAgotarRequest",
                "ReparacionEditarRequest", "ReparacionBorradorRequest", "SolicitudStockInsertarRequest",
                "SolicitudEstadoRequest", "SolicitudStockEstadoRequest", "ValorEntero", "ValorTexto",
                "ContenidoBorrador")) {
            assertTrue(esquemas.has(esquema),
                    () -> "falta el esquema " + esquema + "; publicados: " + nombres(esquemas));
        }

        for (String ruta : List.of(
                // formulario
                "/api/componentes/agrupados",
                "/api/reparaciones/imei/{imei}",
                "/api/reparaciones/imei/{imei}/incidencia-activa",
                "/api/reparaciones/imei/{imei}/asignaciones-activas",
                "/api/reparaciones/imei/{imei}/ya-reparados",
                "/api/reparaciones/imei/{imei}/acciones",
                "/api/reparaciones/asignaciones/{idAsignacion}/solicitudes",
                "/api/reparaciones/{idRep}/borrador",
                "/api/reparaciones/{idRep}/detalle-edicion",
                "/api/reparaciones/{idRep}",
                "/api/reparaciones/{idAsignacion}/filas",
                "/api/reparaciones/{idAsignacion}/agotar-componente",
                "/api/reparaciones/completa",
                "/api/telefonos/{imei}/modelo",
                // campana
                "/api/componentes/gestionados",
                "/api/solicitudes", "/api/solicitudes/count",
                "/api/solicitudes/{idRc}/estado", "/api/solicitudes/{idRc}/limpiar",
                "/api/solicitudes-stock", "/api/solicitudes-stock/count",
                "/api/solicitudes-stock/{idSol}/estado", "/api/solicitudes-stock/{idSol}")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }

        // Nullabilidad campo a campo (todo lo demás es required y no nulo)
        assertNullable(esquemas, "FilaReparacion", "observacion", "prefijo", "descripcionSolicitud", "estadoSolicitud");
        assertNullable(esquemas, "Componente", "ultimoPedido", "idComMaster");
        assertNullable(esquemas, "SolicitudResumen", "tipoComponente", "descripcion");
        assertNullable(esquemas, "SolicitudStock", "descripcion");
        assertNullable(esquemas, "Reparacion", "fechaFin");
        assertNullable(esquemas, "ReparacionDAODetalleEdicion", "observacion");
        assertNullable(esquemas, "ReparacionInsertarCompletaRequest", "idRepAnterior", "idAsignacion", "categoria");
        assertNullable(esquemas, "ReparacionGuardarFilaRequest", "idRepAnterior");
        assertNullable(esquemas, "ReparacionAgotarRequest", "descripcion");
        assertNullable(esquemas, "ReparacionEditarRequest", "observacionNueva");
        assertNullable(esquemas, "SolicitudStockInsertarRequest", "descripcion");
        assertNullable(esquemas, "ContenidoBorrador", "contenido");
        assertNoNullable(esquemas, "FilaReparacion", "idCom", "cantidad", "reutilizado", "esSolicitud", "enCamino");
        assertNoNullable(esquemas, "Componente", "idCom", "tipo", "stock", "stockMinimo", "activo", "enCamino");
        assertNoNullable(esquemas, "SolicitudResumen", "idRc", "idRep", "imei", "nombreTecnico", "estado", "fechaSolicitud");
        assertNoNullable(esquemas, "SolicitudStock", "idSol", "idCom", "tipoComponente", "nombreUsuario", "estado", "fecha");
        assertNoNullable(esquemas, "ReparacionDAOAsignacionActiva", "idRep", "nombreTecnico", "idTec");
        assertNoNullable(esquemas, "ReparacionInsertarCompletaRequest", "filas", "imei", "idTec");
        assertNoNullable(esquemas, "ReparacionGuardarFilaRequest", "filas", "imei", "idTec");
        assertNoNullable(esquemas, "ReparacionBorradorRequest", "contenido");

        assertEquals("integer", esquemas.path("ValorEntero").path("properties").path("value").path("type").asText(),
                "ValorEntero.value debe ser integer");
        assertEquals("integer", esquemas.path("ReparacionInsertarCompletaRequest").path("properties").path("idTec")
                .path("type").asText(), "el idTec del cuerpo se conserva en el contrato (compatibilidad)");
        assertEquals("string", esquemas.path("SolicitudEstadoRequest").path("properties").path("estado")
                .path("type").asText());
        assertEquals("string", esquemas.path("SolicitudStockEstadoRequest").path("properties").path("estado")
                .path("type").asText());

        // Las seis respuestas que dejan de ser Map y los dos cuerpos de estado
        assertTrue(refDeLaRespuesta(paths, "/api/solicitudes/count", "get", "200").endsWith("/ValorEntero"));
        assertTrue(refDeLaRespuesta(paths, "/api/solicitudes-stock/count", "get", "200").endsWith("/ValorEntero"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/imei/{imei}/incidencia-activa", "get", "200")
                .endsWith("/ValorTexto"));
        assertTrue(refDeLaRespuesta(paths, "/api/telefonos/{imei}/modelo", "get", "200").endsWith("/ValorTexto"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idAsignacion}/filas", "post", "201")
                .endsWith("/ValorTexto"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/borrador", "get", "200")
                .endsWith("/ContenidoBorrador"));
        assertTrue(refDelCuerpo(paths, "/api/solicitudes/{idRc}/estado", "patch").endsWith("/SolicitudEstadoRequest"));
        assertTrue(refDelCuerpo(paths, "/api/solicitudes-stock/{idSol}/estado", "patch")
                .endsWith("/SolicitudStockEstadoRequest"));
        assertTrue(refDelCuerpo(paths, "/api/reparaciones/{idRep}/borrador", "put").endsWith("/ReparacionBorradorRequest"));
        assertTrue(refDeLaRespuesta(paths, "/api/reparaciones/{idRep}/detalle-edicion", "get", "200")
                .endsWith("/ReparacionDAODetalleEdicion"));
```

Y, junto a los demás métodos privados de la clase (p. ej. antes de `nombres`):

```java
    private static void assertNullable(JsonNode esquemas, String esquema, String... campos) {
        for (String campo : campos) {
            JsonNode propiedad = esquemas.path(esquema).path("properties").path(campo);
            assertFalse(propiedad.isMissingNode(), () -> esquema + " sin el campo " + campo);
            assertTrue(propiedad.path("nullable").asBoolean(false), () -> esquema + "." + campo + " debe ser nullable");
        }
    }

    private static void assertNoNullable(JsonNode esquemas, String esquema, String... campos) {
        for (String campo : campos) {
            JsonNode propiedad = esquemas.path(esquema).path("properties").path(campo);
            assertFalse(propiedad.isMissingNode(), () -> esquema + " sin el campo " + campo);
            assertFalse(propiedad.path("nullable").asBoolean(false), () -> esquema + "." + campo + " no debe ser nullable");
            assertTrue(esquemas.path(esquema).path("required").toString().contains("\"" + campo + "\""),
                    () -> esquema + "." + campo + " debe ser required");
        }
    }
```

```bash
mvn -q test -Dtest=OpenApiContractTest
```
Expected: FAIL con `falta el esquema SolicitudEstadoRequest; publicados: [...]` (hoy esos cuerpos son `Map` y no publican esquema).

- [x] **Step 2: Records nuevos y respuestas tipadas**

`src/main/java/com/reparaciones/servidor/model/ValorEntero.java`:

```java
package com.reparaciones.servidor.model;

/** Envoltorio {"value": n} de los endpoints que devuelven un único entero (contadores de la campana). */
public record ValorEntero(int value) {}
```

`src/main/java/com/reparaciones/servidor/model/ContenidoBorrador.java`:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de GET …/borrador: {"contenido": json|null}. El servidor guarda el borrador opaco. */
public record ContenidoBorrador(@Schema(nullable = true) String contenido) {}
```

`SolicitudController.java` — añadir `import com.reparaciones.servidor.model.ValorEntero;`, quitar `import java.util.Map;` y dejar `count`, `actualizarEstado` y el record así (el resto de la clase no cambia):

```java
    @GetMapping("/count")
    public ValorEntero count() {
        return new ValorEntero(dao.contarSolicitudesPendientes());
    }
```
```java
    @PatchMapping("/{idRc}/estado")
    public void actualizarEstado(@PathVariable int idRc,
                                  @RequestBody EstadoRequest body,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String estado = body.estado();
        dao.actualizarEstadoSolicitud(idRc, estado);
        String accion = "RECHAZADA".equalsIgnoreCase(estado) ? "RECHAZAR_SOLICITUD" : "GESTIONAR_SOLICITUD";
        logDao.insertar(principal.getIdUsu(), accion, "ID_RC: " + idRc + ", ESTADO: " + estado);
    }
```
y, como último miembro de la clase:
```java
    private record EstadoRequest(String estado) {}
```

`SolicitudStockController.java` — añadir `import com.reparaciones.servidor.model.ValorEntero;` e `import io.swagger.v3.oas.annotations.media.Schema;`, quitar `import java.util.Map;`, y dejar (las anotaciones `@PreAuthorize` de la Task 2 se conservan):

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/count")
    public ValorEntero count() {
        return new ValorEntero(dao.contarPendientes());
    }
```
```java
    @PreAuthorize("hasRole('SUPERTECNICO')")
    @PatchMapping("/{idSol}/estado")
    public void actualizarEstado(@PathVariable int idSol,
                                  @RequestBody EstadoRequest body,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        String estado = body.estado();
        dao.actualizarEstado(idSol, estado);
        String accion = "RECHAZADA".equalsIgnoreCase(estado)
                ? "RECHAZAR_SOLICITUD_STOCK" : "GESTIONAR_SOLICITUD_STOCK";
        logDao.insertar(principal.getIdUsu(), accion, "ID_SOL: " + idSol + ", ESTADO: " + estado);
    }
```
```java
    private record InsertarRequest(int idCom, @Schema(nullable = true) String descripcion) {}
    private record EstadoRequest(String estado) {}
```

`TelefonoController.java` — añadir `import com.reparaciones.servidor.model.ValorTexto;` (conservar `import java.util.Map;`: lo usan otros métodos) y sustituir `getModelo`:

```java
    @GetMapping("/{imei}/modelo")
    public ValorTexto getModelo(@PathVariable String imei) {
        String modelo = dao.getModelo(imei);
        if (modelo == null || modelo.isBlank()) {
            modelo = imeiLookupService.lookupModeloInterno(imei);
        }
        return new ValorTexto(modelo != null ? modelo : "");
    }
```

`ReparacionController.java` (`ValorTexto` y `ContenidoBorrador` entran por el `import com.reparaciones.servidor.model.*;` existente):

```java
    @GetMapping("/imei/{imei}/incidencia-activa")
    public ValorTexto getIncidenciaActivaPorImei(@PathVariable String imei,
            @RequestParam(defaultValue = "R") String tipo) {
        return new ValorTexto(dao.getIncidenciaActivaPorImei(imei, tipo));
    }
```

En `guardarFilaIndividual`, cambiar solo el tipo de retorno y la última línea:

```java
    public ValorTexto guardarFilaIndividual(@PathVariable String idAsignacion,
                                            @RequestBody GuardarFilaRequest req,
                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
```
```java
        return new ValorTexto(idRep);
    }
```

Y `getBorrador`:

```java
    @PreAuthorize("hasAnyRole('SUPERTECNICO','TECNICO')")
    @GetMapping("/{idRep}/borrador")
    public ContenidoBorrador getBorrador(@PathVariable String idRep,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        PropiedadAsignacion.tecnicoEfectivo(principal, dao.getIdTecDeAsignacion(idRep));
        return new ContenidoBorrador(borradorDao.get(idRep));
    }
```

El JSON no cambia (`{"value": …}`, `{"contenido": …}`): el cliente JavaFX 0.16.x sigue leyendo lo mismo. Ningún test de la suite lee esos mapas (`PropiedadAsignacionControllersTest` llama a `guardarFilaIndividual` y `getBorrador` sin mirar el valor devuelto); comprobarlo:

```bash
grep -rn "get(\"value\")\|get(\"contenido\")" src/test/java
```
Expected: sin resultados.

- [x] **Step 3: Anotar la nullabilidad**

Cada anotación se ha verificado contra su `RowMapper`/DAO: `getSolicitudesPorAsignacion` no rellena `prefijo` y lee `OBSERVACIONES`, `DESCRIPCION_SOLICITUD` y `ESTADO_SOLICITUD` tal cual; `ULTIMO_PEDIDO` e `ID_COM_MASTER` solo se rellenan si existen; `TIPO_COM` de `SolicitudResumen` sale de un `LEFT JOIN`; `FECHA_FIN` se mapea con comprobación de nulo; `OBSERVACIONES` del detalle de edición se lee con `getString`.

`model/FilaReparacion.java` (campos públicos sin getters: la anotación va sobre el campo) — fichero completo:

```java
package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class FilaReparacion {
    public int     idCom;
    public int     cantidad;
    public boolean reutilizado;
    @Schema(nullable = true) public String  observacion;
    @Schema(nullable = true) public String  prefijo;
    public boolean esSolicitud;
    @Schema(nullable = true) public String  descripcionSolicitud;
    @Schema(nullable = true) public String  estadoSolicitud;
    public boolean enCamino;
}
```

`model/Componente.java`: añadir `import io.swagger.v3.oas.annotations.media.Schema;` y cambiar solo estas dos declaraciones:

```java
    @Schema(nullable = true) private LocalDateTime ultimoPedido;
    @Schema(nullable = true) private Integer       idComMaster;
```

`model/SolicitudResumen.java`: añadir el mismo import y cambiar:

```java
    @Schema(nullable = true) private String        tipoComponente;
    @Schema(nullable = true) private String        descripcion;
```

`model/SolicitudStock.java`: import y:

```java
    @Schema(nullable = true) private String        descripcion;
```

`model/Reparacion.java`: import y:

```java
    @Schema(nullable = true) private LocalDateTime fechaFin;
```

`dao/ReparacionDAO.java`: añadir `import io.swagger.v3.oas.annotations.media.Schema;` y dejar el record así:

```java
    public record DetalleEdicion(String imei, int idTec, int idCom,
                                  boolean esReutilizado, @Schema(nullable = true) String observacion, int cantidad,
                                  LocalDateTime updatedAt) {}
```

`controller/ReparacionController.java` (ya importa `Schema`); los records quedan así — mismos campos y tipos, `int idTec` incluido:

```java
    record InsertarCompletaRequest(List<FilaReparacion> filas, String imei, int idTec,
                                   @Schema(nullable = true) String idRepAnterior,
                                   @Schema(nullable = true) String idAsignacion,
                                   @Schema(nullable = true) String categoria) {}   // package-private: lo construye el test
```
```java
    private record EditarRequest(int idComNuevo, boolean esReutilizadoNuevo,
                                 @Schema(nullable = true) String observacionNueva, int nNuevas,
                                 LocalDateTime updatedAt) {}
```
```java
    record AgotarRequest(int idCom, int cantidad, @Schema(nullable = true) String descripcion) {}   // package-private: lo construye el test
```
```java
    record GuardarFilaRequest(List<FilaReparacion> filas, String imei, int idTec,
                              @Schema(nullable = true) String idRepAnterior) {}   // package-private: lo construye el test
```

- [x] **Step 4: Suite en verde y snapshot regenerado**

```bash
mvn -q test
ls -la target/openapi.json
grep -c '"nullable" : true' target/openapi.json
grep -n '"ValorEntero"\|"ContenidoBorrador"\|"SolicitudEstadoRequest"\|"SolicitudStockEstadoRequest"' target/openapi.json | head
```
Expected: suite sin fallos; `target/openapi.json` con fecha de ahora, más `nullable` que antes y los cuatro esquemas nuevos presentes. Si una aserción de `assertNullable`/`assertNoNullable` falla, el mensaje nombra esquema y campo: corregir la anotación de ese campo, no el test.

- [x] **Step 5: Validación manual del arranque del contexto Spring (401, 403 y 200)**

La suite levanta el contexto con propiedades de test y DAOs simulados; el arranque real, con el `application.properties` local del desarrollador y MariaDB delante, se valida a mano antes de commitear (un cambio de anotaciones o de beans puede romperlo sin que la suite lo note). El plan no escribe credenciales: el usuario exporta antes, en su shell, las de un usuario con rol TECNICO de su base de datos local:

```bash
export ERP_TEC_USUARIO=...      # los pone el usuario; no se escriben en ningún fichero
export ERP_TEC_PASSWORD=...
```
Si no hay MariaDB local o esas variables no están definidas, **parar y pedírselo al usuario**: este paso no se salta.

Arrancar (puerto 8080 libre) y esperar al mensaje de arranque:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q spring-boot:run > target/arranque.log 2>&1 &
until grep -q "Started App\|APPLICATION FAILED TO START\|BUILD FAILURE" target/arranque.log; do sleep 2; done
grep "Started App\|APPLICATION FAILED TO START\|BUILD FAILURE" target/arranque.log
```
Expected: una línea `Started App in … seconds`. Si aparece `APPLICATION FAILED TO START`, leer `target/arranque.log`, corregir y repetir desde el Step 4.

Las tres comprobaciones:

```bash
BASE=http://localhost:8080

# 401 — token inválido
curl -s -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer no-es-un-jwt' "$BASE/api/reparaciones/pendientes/contadores"

# sesión de un TECNICO (credenciales solo por variables de entorno)
TOKEN_TEC=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"usuario\":\"$ERP_TEC_USUARIO\",\"password\":\"$ERP_TEC_PASSWORD\"}" \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')
test -n "$TOKEN_TEC" && echo "token obtenido"

# 403 — TECNICO sobre PUT /api/reparaciones/{idRep} (id sintético: @PreAuthorize responde antes de tocar datos)
curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$BASE/api/reparaciones/R00000000_0" \
  -H "Authorization: Bearer $TOKEN_TEC" -H 'Content-Type: application/json' \
  -d '{"idComNuevo":0,"esReutilizadoNuevo":false,"observacionNueva":null,"nNuevas":0,"updatedAt":"2026-01-01T00:00:00"}'

# 200 — lectura propia del mismo TECNICO
curl -s -w '\n%{http_code}\n' -H "Authorization: Bearer $TOKEN_TEC" "$BASE/api/reparaciones/pendientes/contadores"
```
Expected, en orden: `401`; `token obtenido`; `403`; un JSON `{"reparaciones":…,"glass":…,"pulidos":…}` seguido de `200`. Ninguna de las tres peticiones escribe datos.

Parar el servidor (en Git Bash el proceso Java es hijo de Maven: se localiza por el puerto):

```bash
netstat -ano | grep ':8080 .*LISTENING'      # última columna = PID
taskkill //PID <PID> //F
unset TOKEN_TEC
```
Anotar en el informe de la tarea los tres códigos obtenidos (sin el token ni las credenciales).

- [x] **Step 6: Commit del contrato**

```bash
git add src/main/java/com/reparaciones/servidor/model/ValorEntero.java src/main/java/com/reparaciones/servidor/model/ContenidoBorrador.java src/main/java/com/reparaciones/servidor/model/FilaReparacion.java src/main/java/com/reparaciones/servidor/model/Componente.java src/main/java/com/reparaciones/servidor/model/SolicitudResumen.java src/main/java/com/reparaciones/servidor/model/SolicitudStock.java src/main/java/com/reparaciones/servidor/model/Reparacion.java src/main/java/com/reparaciones/servidor/dao/ReparacionDAO.java src/main/java/com/reparaciones/servidor/controller/ReparacionController.java src/main/java/com/reparaciones/servidor/controller/SolicitudController.java src/main/java/com/reparaciones/servidor/controller/SolicitudStockController.java src/main/java/com/reparaciones/servidor/controller/TelefonoController.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat(servidor): contrato con nullabilidad y respuestas tipadas para el formulario y la campana"
```

- [x] **Step 7: `docs/autorizacion_endpoints.md` en versión corta**

Sustituir el fichero **entero** por el texto siguiente. Desaparecen las secciones por controlador; nada de lo retirado se copia a otro fichero del repo (la fuente de verdad de cada endpoint son las anotaciones del código y sus tests).

````markdown
# Autorización de endpoints por rol

## Por qué existe este documento

La aplicación tiene tres roles: **TECNICO**, **SUPERTECNICO** y **ADMIN**. Quién puede hacer qué lo decide el servidor en cada petición; los clientes (JavaFX y web) se limitan a reflejarlo mostrando u ocultando acciones. Este documento explica el mecanismo y el criterio. El detalle de cada endpoint no se repite aquí: está en las anotaciones de los controllers y en los tests que se citan al final.

## Cómo funciona `@PreAuthorize`

`@PreAuthorize` es una anotación de Spring Security que se coloca sobre un método de un controller, o sobre la clase entera. Antes de ejecutar el método, Spring comprueba que el usuario de la petición cumple la condición; si no la cumple, responde `403 Forbidden` sin llegar al DAO ni a la base de datos.

```java
// Solo SUPERTECNICO
@PreAuthorize("hasRole('SUPERTECNICO')")
@PutMapping("/{idRep}")
public void editarReparacion(...) { ... }

// TECNICO o SUPERTECNICO
@PreAuthorize("hasAnyRole('TECNICO','SUPERTECNICO')")
@PostMapping
public void insertar(...) { ... }
```

Una anotación a nivel de clase vale para todos sus métodos. Para que `@PreAuthorize` funcione, la configuración lleva `@EnableMethodSecurity` (en `SecurityConfig`).

## El rol viaja en el JWT

Al iniciar sesión (`POST /api/auth/login`, la única ruta pública) el servidor firma un token que incluye el usuario, su rol y, si lo tiene, su técnico (`idTec`). En cada petición, `JwtAuthFilter` valida el token y construye con esos datos el `UsuarioPrincipal`: de él salen tanto el rol que evalúa `@PreAuthorize` como el técnico que usan las reglas de más abajo. Los controllers lo reciben con `@AuthenticationPrincipal`.

- Petición sin cabecera `Authorization`: `403`.
- Token inválido o caducado: `401`.
- Token válido sin el rol o la propiedad exigidos: `403`, con el motivo en `message` cuando lo decide una regla propia.

## Criterio general

- **Lecturas (GET):** exigen sesión y, donde su controller lo anota, también rol: las de administración (usuarios y logs: ADMIN), las solicitudes de pieza (SUPERTECNICO), las solicitudes de stock (SUPERTECNICO y ADMIN) y el detalle de edición de una reparación (SUPERTECNICO). Las listas del taller se acotan además por técnico con `FiltroTecnico`.
- **Escrituras (POST, PUT, PATCH, DELETE):** exigen el rol al que los clientes ofrecen esa acción.
  - **Gestión del taller** — asignar, reasignar, editar o eliminar trabajos, catálogo y stock de componentes, compras, gestión de solicitudes: **SUPERTECNICO**.
  - **Administración** — usuarios, logs, valores de dificultad: **ADMIN**.
  - **Trabajo propio del técnico** — completar, guardar una fila, registrar un componente agotado, borrador del formulario, "por cerrar", entrega y llegada de glass, crear una solicitud de stock: **TECNICO y SUPERTECNICO**, siempre sobre sus propias asignaciones.
- Un endpoint nuevo se anota en el mismo cambio que lo crea, con un test que compruebe al menos un rol admitido y uno no admitido.

## Regla del `?tecnico=` (`FiltroTecnico`)

`FiltroTecnico.efectivo(principal, tecnico)` decide qué técnico filtra el DAO en las seis listas del taller (`GET /api/reparaciones/historial`, `/api/reparaciones/asignaciones`, `/api/glass/historial`, `/api/glass/asignaciones`, `/api/pulidos/historial`, `/api/pulidos/asignaciones`) y en `GET /api/reparaciones/pendientes/contadores`:

- **TECNICO**: el filtro es siempre su `idTec`. Sin parámetro o con su propio id recibe lo suyo; si pide otro técnico, `403` con el motivo "Solo puedes consultar tus propios trabajos" (sin llegar al DAO). Un TECNICO sin `idTec` también recibe `403`. Cualquier rol que no sea SUPERTECNICO ni ADMIN se trata como TECNICO.
- **SUPERTECNICO y ADMIN**: filtro libre (sin parámetro, todos; con parámetro, ese técnico).
- **Contadores**: sin `?tecnico=` se cuentan los del técnico del token, también para el SUPERTECNICO; un ADMIN sin técnico recibe `{0, 0, 0}` sin consultar.

## Regla de las escrituras del formulario (`PropiedadAsignacion`)

`PropiedadAsignacion` vive junto a `FiltroTecnico` y se aplica en `POST /api/reparaciones/completa`, `POST /api/reparaciones/{idAsignacion}/filas`, `POST /api/reparaciones/{idAsignacion}/agotar-componente`, `PATCH /api/reparaciones/{idRep}/completar` y `GET|PUT|DELETE /api/reparaciones/{idRep}/borrador` (la clave del borrador es el id de la asignación):

- **El técnico de cada trabajo es el del token.** `tecnicoEfectivo(principal, idTecDueno)` devuelve siempre el `idTec` del token; el `idTec` que envíe el cliente en el cuerpo no interviene (se acepta, para que los clientes anteriores sigan funcionando, y se ignora).
- **Solo se trabaja sobre asignaciones propias**, igual para TECNICO y SUPERTECNICO: si la asignación es de otro técnico, o el token no tiene técnico, `403` con el motivo "Solo puedes trabajar sobre tus propias asignaciones". Si la asignación no existe, la regla deja pasar y el DAO responde su `409` de asignación ya eliminada o completada. El borrador, además, solo admite los roles TECNICO y SUPERTECNICO.
- **La edición es del supertécnico.** `PUT /api/reparaciones/{idRep}` y `GET /api/reparaciones/{idRep}/detalle-edicion` exigen SUPERTECNICO. Al añadir filas o acciones a una reparación ya hecha, los clientes llaman a `completa` sin `idAsignacion` y con el `idTec` del técnico **original**: ese caso pasa por `exigirSupertecnico` (`403` con el motivo "Solo el supertécnico puede corregir una reparación ya hecha" para los demás roles) y conserva el `idTec` del cuerpo, para que el trabajo siga a nombre de quien lo hizo.

## Dónde están los tests

| Qué | Test |
|---|---|
| Regla del `?tecnico=` | `security/FiltroTecnicoTest`, `controller/FiltroTecnicoControllersTest`, `controller/ReparacionControllerContadoresTest` |
| Regla de propiedad de la asignación | `security/PropiedadAsignacionTest`, `controller/PropiedadAsignacionControllersTest` |
| Roles de la edición y del borrador (cadena de seguridad real, MockMvc) | `controller/RolesReparacionFormularioTest` |
| Roles de solicitudes de stock y del ajuste de stock (MockMvc) | `controller/RolesSolicitudesStockTest` |
| Dueño de la asignación en la entrega y la llegada de glass | `controller/ReparacionControllerEntregaGlassTest` |
| Sin sesión (`403`) y token inválido (`401`) | `OpenApiContractTest.elContextoArrancaYElContratoExigeSesion` |

Los tests de controller que llaman al método Java directamente (con DAOs de Mockito) comprueban las reglas propias, pero no ejercitan `@PreAuthorize`; los roles se comprueban con MockMvc y un token generado con `JwtUtil`, sin base de datos.
````

Comprobar que no queda ninguna sección por controlador y que la única tabla es la de los tests:

```bash
grep -c "Controller —" docs/autorizacion_endpoints.md      # 0
grep -n "^| " docs/autorizacion_endpoints.md | wc -l        # 8 (cabecera, separador y seis filas)
```

- [x] **Step 8: `docs/api_contract.md` — contrato del formulario y de la campana**

En la lista "Convenciones que el OpenAPI no expresa", insertar estos puntos justo **después** del punto de `GET /api/reparaciones/pendientes/contadores` y antes del de "Sin sesión":

```markdown
- Escrituras del formulario de reparación (`POST /api/reparaciones/completa`, `POST /api/reparaciones/{idAsignacion}/filas`,
  `POST /api/reparaciones/{idAsignacion}/agotar-componente`, `PATCH /api/reparaciones/{idRep}/completar` y
  `GET|PUT|DELETE /api/reparaciones/{idRep}/borrador`): pasan por `PropiedadAsignacion`. El técnico es el del token y la
  asignación debe ser suya (`403` "Solo puedes trabajar sobre tus propias asignaciones"); el `idTec` del cuerpo sigue en
  el contrato por compatibilidad y el servidor lo ignora cuando hay `idAsignacion`. `completa` sin `idAsignacion` (filas
  añadidas al editar una reparación ya hecha) exige SUPERTECNICO y conserva el `idTec` enviado, que es el del técnico
  original. `PUT /api/reparaciones/{idRep}` y `GET /api/reparaciones/{idRep}/detalle-edicion`: SUPERTECNICO. Detalle en
  `docs/autorizacion_endpoints.md`.
- En `/api/reparaciones/{idRep}/borrador` la variable de ruta es el id de la **asignación**. El contenido es un JSON opaco
  para el servidor (lo escriben y lo leen los clientes con el mismo formato); `GET` responde `ContenidoBorrador`
  `{"contenido": "<json>" | null}`.
- Campos sin valor en los cuerpos: enviar la propiedad a `null` equivale a omitirla. El contrato marca todas las
  propiedades como `required` y señala con `nullable` las que admiten `null`.
- Envoltorios de un solo valor: `ValorTexto` `{"value": texto | null}` (`…/referenciadora`, `…/incidencia-activa`,
  `POST …/filas` con el id de la reparación creada, `GET /api/telefonos/{imei}/modelo`, que responde `""` si no hay
  modelo), `ValorEntero` `{"value": n}` (`GET /api/solicitudes/count`, `GET /api/solicitudes-stock/count`) y
  `ValorBooleano` `{"value": true | false}`.
- `PATCH /api/solicitudes/{idRc}/estado` y `PATCH /api/solicitudes-stock/{idSol}/estado` reciben `{"estado": "PENDIENTE" |
  "GESTIONADA" | "RECHAZADA"}`. `/api/solicitudes` es de SUPERTECNICO; en `/api/solicitudes-stock` crean TECNICO y
  SUPERTECNICO, leen y cuentan SUPERTECNICO y ADMIN, y cambian de estado o borran SUPERTECNICO.
  `PATCH /api/componentes/{idCom}/stock`: SUPERTECNICO.
- Chasis por SKU: al completar con una pieza cuyo SKU empieza por `cha`, o al pedirla (agotado o solicitud dentro de
  `completa`), la asignación queda con `esChasis = true`. El servidor nunca lo quita por sí solo; el cambio manual
  (`PATCH /api/reparaciones/asignaciones/{idRep}/chasis`) sigue igual.
```

Comprobar que el repositorio público no recibe nada que no deba:

```bash
grep -nE "([0-9]{1,3}\.){3}[0-9]{1,3}|password|contraseñ" docs/autorizacion_endpoints.md docs/api_contract.md
```
Expected: como mucho, la mención ya existente a "contraseña actual incorrecta" de `api_contract.md` (texto de una regla de negocio); ninguna IP, ningún dominio, ninguna credencial.

- [x] **Step 9: Commit de la documentación**

```bash
mvn -q test
git add docs/autorizacion_endpoints.md docs/api_contract.md
git commit -m "docs(servidor): autorización en versión corta y contrato del formulario"
git status --short   # vacío (target/ está ignorado)
```
Expected: suite en verde antes del commit; árbol limpio después. No hacer `git push` ni merge: los pide el usuario.


### Task 4: Web — contrato regenerado, tipos exportados y fábrica de tests ampliada

**Files:**
- Modify: `api/openapi.json` (copia de `../gestion-reparaciones-servidor/target/openapi.json`), `src/shared/api/schema.d.ts` (generado con `npm run api:types:offline`), `src/shared/api/client.ts`, `src/shared/api/client.test.ts`, `src/modules/taller/test/fabrica.ts`
- Test: `src/shared/api/client.test.ts` (tipos), `src/modules/taller/test/fabrica.test.ts` (nuevo: el test de la fábrica no puede vivir en `shared`, que no importa de `modules`)

**Interfaces:**
- Consumes: S5 (esquemas `Componente`, `FilaReparacion`, `Reparacion`, `SolicitudResumen`, `SolicitudStock`, `ReparacionDAODetalleEdicion`, `ReparacionDAOAsignacionActiva`, `ReparacionInsertarCompletaRequest`, `ReparacionGuardarFilaRequest`, `ReparacionAgotarRequest`, `ReparacionEditarRequest`, `ValorEntero`, `ValorTexto`, `ContenidoBorrador`, ya con `nullable`).
- Produces (W1), exportados de `@/shared/api/client`:

```ts
export type Componente = components['schemas']['Componente']
export type ComponentesAgrupados = Record<string, Componente[]>
export type FilaReparacion = components['schemas']['FilaReparacion']
export type SolicitudAsignacion = FilaReparacion
export type Reparacion = components['schemas']['Reparacion']
export type DetalleEdicion = components['schemas']['ReparacionDAODetalleEdicion']
export type AsignacionActiva = components['schemas']['ReparacionDAOAsignacionActiva']
export type SolicitudResumen = components['schemas']['SolicitudResumen']
export type SolicitudStock = components['schemas']['SolicitudStock']
export type GuardarFilaRequest = components['schemas']['ReparacionGuardarFilaRequest']
export type InsertarCompletaRequest = components['schemas']['ReparacionInsertarCompletaRequest']
export type AgotarRequest = components['schemas']['ReparacionAgotarRequest']
export type EditarReparacionRequest = components['schemas']['ReparacionEditarRequest']
```

- Produces (W2), exportados de `src/modules/taller/test/fabrica.ts`:

```ts
export function componente(parcial?: Partial<Componente>): Componente
export function agrupados(): ComponentesAgrupados
export function solicitudAsignacion(parcial?: Partial<SolicitudAsignacion>): SolicitudAsignacion
export function detalleEdicion(parcial?: Partial<DetalleEdicion>): DetalleEdicion
export function asignacionActiva(parcial?: Partial<AsignacionActiva>): AsignacionActiva
export function solicitudUrgente(parcial?: Partial<SolicitudResumen>): SolicitudResumen
export function solicitudPreventiva(parcial?: Partial<SolicitudStock>): SolicitudStock
export function reparacion(parcial?: Partial<Reparacion>): Reparacion
export const BORRADOR_JAVAFX: string
```

**Ficha:** ninguna casilla directamente (infraestructura de tipos y datos de prueba para todas las tareas siguientes).

- [x] **Step 1: Copiar el contrato del servidor (ya con la Task 3) y regenerar los tipos**

El contrato sale del repo del servidor en la rama `feature/web-formulario`, con las Tasks 1–3 committeadas. `OpenApiContractTest` deja el documento en `target/openapi.json`; se vuelve a ejecutar para garantizar que está al día.

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git branch --show-current        # debe decir feature/web-formulario
git status --short               # debe salir vacío (Tasks 1-3 committeadas)
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q test -Dtest=OpenApiContractTest
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git branch --show-current        # debe decir feature/web-formulario
cp ../gestion-reparaciones-servidor/target/openapi.json api/openapi.json
npm run api:types:offline
grep -n "ValorEntero: {" src/shared/api/schema.d.ts
grep -n "ContenidoBorrador: {" src/shared/api/schema.d.ts
grep -n "observacion: string | null" src/shared/api/schema.d.ts | head -3
grep -n "idComMaster: number | null" src/shared/api/schema.d.ts
```
Expected: `mvn` termina sin salida (verde); `schema.d.ts` regenerado; los cuatro `grep` encuentran línea (`ValorEntero`, `ContenidoBorrador`, `observacion: string | null` en `FilaReparacion`, `idComMaster: number | null` en `Componente`). Si alguno no aparece, la Task 3 del servidor no está aplicada: parar y resolverlo allí, no aquí.

- [x] **Step 2: Test de tipos (falla en typecheck)**

En `src/shared/api/client.test.ts`, sustituir la línea de import de tipos

```ts
import type { Cliente, ContadoresPendientes, LoginResponse, ReparacionResumen, Tecnico } from './client'
```

por

```ts
import type {
  AgotarRequest, AsignacionActiva, Cliente, Componente, ComponentesAgrupados, ContadoresPendientes, DetalleEdicion,
  EditarReparacionRequest, FilaReparacion, GuardarFilaRequest, InsertarCompletaRequest, LoginResponse, Reparacion,
  ReparacionResumen, SolicitudAsignacion, SolicitudResumen, SolicitudStock, Tecnico,
} from './client'
import type { paths } from './schema'
```

y añadir al final del fichero:

```ts
describe('tipos del contrato del formulario y de la campana (spec web-formulario §5.5)', () => {
  it('lo que puede venir a null va como T | null y lo demás es obligatorio', () => {
    expectTypeOf<FilaReparacion['observacion']>().toEqualTypeOf<string | null>()
    expectTypeOf<FilaReparacion['prefijo']>().toEqualTypeOf<string | null>()
    expectTypeOf<FilaReparacion['descripcionSolicitud']>().toEqualTypeOf<string | null>()
    expectTypeOf<FilaReparacion['estadoSolicitud']>().toEqualTypeOf<string | null>()
    expectTypeOf<FilaReparacion['idCom']>().toEqualTypeOf<number>()
    expectTypeOf<SolicitudAsignacion>().toEqualTypeOf<FilaReparacion>()
    expectTypeOf<Componente['idComMaster']>().toEqualTypeOf<number | null>()
    expectTypeOf<Componente['ultimoPedido']>().toEqualTypeOf<string | null>()
    expectTypeOf<Componente['stock']>().toEqualTypeOf<number>()
    expectTypeOf<ComponentesAgrupados>().toEqualTypeOf<Record<string, Componente[]>>()
    expectTypeOf<Reparacion['fechaFin']>().toEqualTypeOf<string | null>()
    expectTypeOf<DetalleEdicion['updatedAt']>().toEqualTypeOf<string>()
    expectTypeOf<DetalleEdicion['observacion']>().toEqualTypeOf<string | null>()
    expectTypeOf<AsignacionActiva['idTec']>().toEqualTypeOf<number>()
    expectTypeOf<SolicitudResumen['descripcion']>().toEqualTypeOf<string | null>()
    expectTypeOf<SolicitudStock['descripcion']>().toEqualTypeOf<string | null>()
  })
  it('los cuerpos de las escrituras del formulario: idTec obligatorio y los opcionales como null', () => {
    expectTypeOf<InsertarCompletaRequest['idAsignacion']>().toEqualTypeOf<string | null>()
    expectTypeOf<InsertarCompletaRequest['idRepAnterior']>().toEqualTypeOf<string | null>()
    expectTypeOf<InsertarCompletaRequest['categoria']>().toEqualTypeOf<string | null>()
    expectTypeOf<InsertarCompletaRequest['idTec']>().toEqualTypeOf<number>()
    expectTypeOf<InsertarCompletaRequest['filas']>().toEqualTypeOf<FilaReparacion[]>()
    expectTypeOf<GuardarFilaRequest['idRepAnterior']>().toEqualTypeOf<string | null>()
    expectTypeOf<GuardarFilaRequest['idTec']>().toEqualTypeOf<number>()
    expectTypeOf<AgotarRequest['descripcion']>().toEqualTypeOf<string | null>()
    expectTypeOf<EditarReparacionRequest['observacionNueva']>().toEqualTypeOf<string | null>()
    expectTypeOf<EditarReparacionRequest['nNuevas']>().toEqualTypeOf<number>()
  })
  it('las respuestas que eran mapas sueltos llegan tipadas', () => {
    type ContarUrgentes = paths['/api/solicitudes/count']['get']['responses'][200]['content']['*/*']
    type ContarPreventivas = paths['/api/solicitudes-stock/count']['get']['responses'][200]['content']['*/*']
    type Borrador = paths['/api/reparaciones/{idRep}/borrador']['get']['responses'][200]['content']['*/*']
    type Incidencia = paths['/api/reparaciones/imei/{imei}/incidencia-activa']['get']['responses'][200]['content']['*/*']
    type Modelo = paths['/api/telefonos/{imei}/modelo']['get']['responses'][200]['content']['*/*']
    expectTypeOf<ContarUrgentes['value']>().toEqualTypeOf<number>()
    expectTypeOf<ContarPreventivas['value']>().toEqualTypeOf<number>()
    expectTypeOf<Borrador['contenido']>().toEqualTypeOf<string | null>()
    expectTypeOf<Incidencia['value']>().toEqualTypeOf<string | null>()
    expectTypeOf<Modelo['value']>().toEqualTypeOf<string | null>()
    type Creada = paths['/api/reparaciones/{idAsignacion}/filas']['post']['responses'][201]['content']['*/*']
    expectTypeOf<Creada['value']>().toEqualTypeOf<string | null>()
  })
})
```

```bash
npm run typecheck
```
Expected: FALLA en `client.test.ts` con `Module '"./client"' has no exported member 'AgotarRequest'` (y el resto de tipos nuevos). Las respuestas tipadas (`ContarUrgentes`, `Borrador`, `Creada`…) ya deben compilar: salen de `schema.d.ts` regenerado; si alguna falla, la Task 3 del servidor no tipó esa respuesta (los `GET` responden 200 y `POST …/filas` 201).

- [x] **Step 3: Exportar los tipos en `client.ts`**

En `src/shared/api/client.ts`, debajo de `export type ContadoresPendientes = …`, añadir:

```ts
/** Formulario de reparación y campana (sub-proyecto 2). `ComponentesAgrupados` es la respuesta de
 *  GET /api/componentes/agrupados: prefijo del tipo → sus componentes, en el orden de claves del servidor. */
export type Componente = components['schemas']['Componente']
export type ComponentesAgrupados = Record<string, Componente[]>
export type FilaReparacion = components['schemas']['FilaReparacion']
/** Lo que devuelve GET …/asignaciones/{idAsignacion}/solicitudes: mismo esquema que una fila. */
export type SolicitudAsignacion = FilaReparacion
export type Reparacion = components['schemas']['Reparacion']
export type DetalleEdicion = components['schemas']['ReparacionDAODetalleEdicion']
export type AsignacionActiva = components['schemas']['ReparacionDAOAsignacionActiva']
export type SolicitudResumen = components['schemas']['SolicitudResumen']
export type SolicitudStock = components['schemas']['SolicitudStock']
export type GuardarFilaRequest = components['schemas']['ReparacionGuardarFilaRequest']
export type InsertarCompletaRequest = components['schemas']['ReparacionInsertarCompletaRequest']
export type AgotarRequest = components['schemas']['ReparacionAgotarRequest']
export type EditarReparacionRequest = components['schemas']['ReparacionEditarRequest']
```

```bash
npm run typecheck
```
Expected: `client.test.ts` compila. Si `tsc` señala ahora código existente por los nuevos `nullable` (p. ej. un uso de `Reparacion.fechaFin` como `string`), se corrige solo esa línea con el tratamiento de `null` que ya use el fichero (`?? ''`, `formatear(x)` que ya admite `null`, etc.) y se anota en el informe de la tarea.

- [x] **Step 4: Test de la fábrica (falla)**

Crear `src/modules/taller/test/fabrica.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import {
  BORRADOR_JAVAFX, agrupados, asignacionActiva, componente, detalleEdicion, reparacion, solicitudAsignacion, solicitudPreventiva,
  solicitudUrgente,
} from './fabrica'

describe('fábrica de tests del formulario y la campana (datos sintéticos)', () => {
  it('agrupados() conserva el orden de claves del servidor y el catálogo canónico', () => {
    const a = agrupados()
    expect(Object.keys(a)).toEqual(['bat', 'cha', 'g', 'mc', 'lcd', 'cam', 'otro'])
    expect(a.bat.map((c) => [c.idCom, c.tipo, c.stock, c.stockMinimo, c.activo])).toEqual([
      [101, 'bati13', 5, 2, true], [102, 'bati14', 0, 2, true], [103, 'bati13promax', 1, 2, true], [104, 'bati12', 3, 1, false],
    ])
    expect(a.cha.map((c) => c.idCom)).toEqual([131])
    expect(a.g.map((c) => [c.idCom, c.tipo, c.stock])).toEqual([[141, 'gi13', 6], [142, 'gi14', 0]])
    expect(a.mc.map((c) => c.idCom)).toEqual([151])
    expect(a.lcd.map((c) => [c.idCom, c.tipo, c.stock, c.stockMinimo])).toEqual([[111, 'lcdi13', 1, 1], [112, 'lcdi14', 3, 1]])
    expect(a.cam.map((c) => [c.idCom, c.tipo, c.stock])).toEqual([[121, 'cami13', 4]])
    expect(a.otro.map((c) => [c.idCom, c.tipo, c.stock, c.stockMinimo])).toEqual([[161, 'otroi13', 0, 0], [162, 'otroi14', 0, 0]])
  })
  it('cada llamada devuelve objetos nuevos (un test puede mutarlos sin contaminar a otro)', () => {
    const a = agrupados()
    a.bat[0].stock = 0
    expect(agrupados().bat[0].stock).toBe(5)
  })
  it('valores por defecto y sobrescritura parcial', () => {
    expect(componente()).toMatchObject({ idCom: 101, tipo: 'bati13', stock: 5, stockMinimo: 2, activo: true, idComMaster: null, enCamino: 0, ultimoPedido: null })
    expect(componente({ stock: 0 }).stock).toBe(0)
    expect(solicitudAsignacion()).toEqual({
      idCom: 102, cantidad: 1, reutilizado: false, observacion: null, prefijo: null, esSolicitud: true, descripcionSolicitud: null,
      estadoSolicitud: 'PENDIENTE', enCamino: false,
    })
    expect(detalleEdicion()).toEqual({ imei: '355400000000111', idTec: 4, idCom: 101, esReutilizado: false, observacion: null, cantidad: 1, updatedAt: '2026-09-16T07:02:00' })
    expect(asignacionActiva()).toEqual({ idRep: 'AG20260916_2', nombreTecnico: 'Técnico H', idTec: 6 })
    expect(solicitudUrgente()).toMatchObject({ idRc: 501, idRep: 'A20260916_1', imei: '355400000000111', nombreTecnico: 'Técnico A', idCom: 102, tipoComponente: 'bati14', descripcion: null, estado: 'PENDIENTE' })
    expect(solicitudPreventiva()).toMatchObject({ idSol: 701, idCom: 111, tipoComponente: 'lcdi13', idUsu: 8, nombreUsuario: 'tecnico_n', descripcion: null, estado: 'PENDIENTE' })
    expect(reparacion()).toMatchObject({ idRep: 'R20260916_5', imei: '355400000000111', idTec: 4 })
    expect(reparacion({ idRep: 'R20260916_6' }).idRep).toBe('R20260916_6')
  })
  it('BORRADOR_JAVAFX es un JSON con la forma del cliente de escritorio: modelo, filas y otros, sin nulos', () => {
    const b = JSON.parse(BORRADOR_JAVAFX) as { modelo: string; filas: Record<string, unknown>[]; otros: Record<string, unknown>[] }
    expect(Object.keys(b)).toEqual(['modelo', 'filas', 'otros'])
    expect(b.modelo).toBe('13')
    expect(b.filas.map((f) => f.prefijo)).toEqual(['bat', 'lcd', 'cam'])
    expect(b.filas[0]).toEqual({
      prefijo: 'bat', idCom: 101, cantidad: 1, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: true,
      idRepGenerado: 'R20260916_5', fechaGuardado: '16/09 09:15',
    })
    expect(b.filas[1]).toMatchObject({ prefijo: 'lcd', idCom: 111, cantidad: 1, observacion: 'Pantalla con líneas verticales', guardada: false })
    expect(b.filas[2]).toMatchObject({ prefijo: 'cam', idCom: 121, agotadoConfirmado: true, descripcionAgotado: 'Cámara trasera completa' })
    expect(b.otros).toEqual([
      { descripcion: 'Limpieza del conector de carga', guardada: true, idRepGenerado: 'R20260916_6', fechaGuardado: '16/09 09:20' },
      { descripcion: 'Ajuste de tornillería', guardada: false },
    ])
    expect(BORRADOR_JAVAFX).not.toContain('null')
  })
})
```

```bash
npx vitest run src/modules/taller/test/fabrica.test.ts
```
Expected: FALLA (`agrupados`, `componente`, `BORRADOR_JAVAFX`… no exportados de `./fabrica`).

- [x] **Step 5: Ampliar la fábrica**

En `src/modules/taller/test/fabrica.ts`, sustituir la línea de import por:

```ts
import type {
  AsignacionActiva, Componente, ComponentesAgrupados, DetalleEdicion, Reparacion, ReparacionResumen, SolicitudAsignacion, SolicitudResumen,
  SolicitudStock, Tecnico,
} from '@/shared/api/client'
```

y añadir al final del fichero (no se toca nada de lo existente: `resumen`, `tecnico`, `normal`, `glass` quedan igual, incluido `cliente: 'AMAZON'`):

```ts
// ── Formulario de reparación y campana (sub-proyecto 2) ──────────────────────────────────────────────────────────────

export function componente(parcial: Partial<Componente> = {}): Componente {
  return {
    idCom: 101, tipo: 'bati13', fechaRegistro: '2026-09-01T08:00:00', stock: 5, stockMinimo: 2, activo: true,
    updatedAt: '2026-09-01T08:00:00', enCamino: 0, ultimoPedido: null, idComMaster: null,
    ...parcial,
  }
}

const sku = (idCom: number, tipo: string, stock: number, stockMinimo: number, activo = true) => componente({ idCom, tipo, stock, stockMinimo, activo })

/** Catálogo sintético canónico, en el orden de claves del servidor (bat, cha, g, mc, lcd, cam, otro). No hay `cam`, `cha` ni
 *  `mc` para el modelo 14: sirve para "tipo sin SKU para el modelo". `bati12` está inactivo. Cada llamada crea objetos nuevos. */
export function agrupados(): ComponentesAgrupados {
  return {
    bat: [sku(101, 'bati13', 5, 2), sku(102, 'bati14', 0, 2), sku(103, 'bati13promax', 1, 2), sku(104, 'bati12', 3, 1, false)],
    cha: [sku(131, 'chai13negro', 2, 1)],
    g: [sku(141, 'gi13', 6, 2), sku(142, 'gi14', 0, 2)],
    mc: [sku(151, 'mci13', 2, 1)],
    lcd: [sku(111, 'lcdi13', 1, 1), sku(112, 'lcdi14', 3, 1)],
    cam: [sku(121, 'cami13', 4, 1)],
    otro: [sku(161, 'otroi13', 0, 0), sku(162, 'otroi14', 0, 0)],
  }
}

/** Una solicitud de pieza tal como llega de GET …/asignaciones/{idAsignacion}/solicitudes (por defecto, pendiente de `bati14`). */
export function solicitudAsignacion(parcial: Partial<SolicitudAsignacion> = {}): SolicitudAsignacion {
  return {
    idCom: 102, cantidad: 1, reutilizado: false, observacion: null, prefijo: null, esSolicitud: true, descripcionSolicitud: null,
    estadoSolicitud: 'PENDIENTE', enCamino: false,
    ...parcial,
  }
}

export function detalleEdicion(parcial: Partial<DetalleEdicion> = {}): DetalleEdicion {
  return { imei: '355400000000111', idTec: 4, idCom: 101, esReutilizado: false, observacion: null, cantidad: 1, updatedAt: '2026-09-16T07:02:00', ...parcial }
}

export function asignacionActiva(parcial: Partial<AsignacionActiva> = {}): AsignacionActiva {
  return { idRep: 'AG20260916_2', nombreTecnico: 'Técnico H', idTec: 6, ...parcial }
}

export function solicitudUrgente(parcial: Partial<SolicitudResumen> = {}): SolicitudResumen {
  return {
    idRc: 501, idRep: 'A20260916_1', imei: '355400000000111', nombreTecnico: 'Técnico A', idCom: 102, tipoComponente: 'bati14',
    descripcion: null, estado: 'PENDIENTE', fechaSolicitud: '2026-09-16T07:02:00',
    ...parcial,
  }
}

export function solicitudPreventiva(parcial: Partial<SolicitudStock> = {}): SolicitudStock {
  return {
    idSol: 701, idCom: 111, tipoComponente: 'lcdi13', idUsu: 8, nombreUsuario: 'tecnico_n', descripcion: null, estado: 'PENDIENTE',
    fecha: '2026-09-16T09:30:00',
    ...parcial,
  }
}

export function reparacion(parcial: Partial<Reparacion> = {}): Reparacion {
  return {
    idRep: 'R20260916_5', fechaAsig: '2026-09-16T07:02:00', fechaFin: '2026-09-16T09:15:00', imei: '355400000000111', idTec: 4,
    updatedAt: '2026-09-16T09:15:00',
    ...parcial,
  }
}

/** Borrador tal cual lo escribe el cliente de escritorio (Gson: primitivos siempre, cadenas nulas omitidas, claves en el orden de
 *  declaración): modelo 13, batería guardada, pantalla normal con cantidad 1 y observación, cámara con agotado confirmado en el
 *  límite (cantidad = stock de `cami13`) y descripción, una acción guardada y otra pendiente. Es una cadena: la fábrica no
 *  importa nada de formulario/. */
export const BORRADOR_JAVAFX: string = JSON.stringify({
  modelo: '13',
  filas: [
    {
      prefijo: 'bat', idCom: 101, cantidad: 1, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: true,
      idRepGenerado: 'R20260916_5', fechaGuardado: '16/09 09:15',
    },
    {
      prefijo: 'lcd', idCom: 111, cantidad: 1, reutilizado: false, observacion: 'Pantalla con líneas verticales', solicitudNueva: false,
      agotadoConfirmado: false, guardada: false,
    },
    {
      prefijo: 'cam', idCom: 121, cantidad: 4, reutilizado: false, solicitudNueva: false, agotadoConfirmado: true,
      descripcionAgotado: 'Cámara trasera completa', guardada: false,
    },
  ],
  otros: [
    { descripcion: 'Limpieza del conector de carga', guardada: true, idRepGenerado: 'R20260916_6', fechaGuardado: '16/09 09:20' },
    { descripcion: 'Ajuste de tornillería', guardada: false },
  ],
})
```

```bash
npx vitest run src/modules/taller/test/fabrica.test.ts
```
Expected: PASS (4 tests).

- [x] **Step 6: `npm run check` en verde**

```bash
npm run check
```
Expected: lint, typecheck y toda la suite en verde (la existente + los tres tests de tipos + los cuatro de la fábrica). Si el lint protesta por `fabrica.test.ts`, es que importa algo fuera del módulo: solo debe importar de `vitest` y de `./fabrica`.

- [x] **Step 7: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/api/client.test.ts src/modules/taller/test/fabrica.ts src/modules/taller/test/fabrica.test.ts
git commit -m "chore(web): contrato regenerado para el formulario y la campana; tipos exportados y fábrica de tests con datos sintéticos"
```
(si el Step 3 obligó a tocar algún fichero más por los `nullable`, añadir su ruta concreta al `git add`).

---

### Task 5: Web — `taller/lib`: modelo de un SKU, tipos de fila y color por stock

**Files:**
- Modify: `src/modules/taller/lib/modelos.ts`, `src/modules/taller/lib/modelos.test.ts`, `src/modules/taller/lib/piezas.ts`, `src/modules/taller/lib/piezas.test.ts`, `src/modules/taller/lib/textos.ts`

**Interfaces:**
- Consumes: W1 (`Componente`, `ComponentesAgrupados` de `@/shared/api/client`), W2 (`agrupados()`, `componente()` de `../test/fabrica`).
- Produces (W3):

```ts
// modelos.ts (MODELOS_ORDENADOS y traducirModelo ya existen)
export function extraerModelo(sku: string, prefijo: string): string | null
export function modelosDisponibles(grupos: { prefijo: string; skus: Componente[] }[]): string[]

// piezas.ts (categoriaPieza ya existe)
export const PREFIJO_OTRO = 'otro'
export const PREFIJOS_GLASS: readonly string[]            // ['g', 'mc']
export function nombreTipo(prefijo: string): string
export function prefijosDeFila(agrupados: ComponentesAgrupados, glass: boolean): string[]
export type NivelStock = 'sinStock' | 'bajo' | 'normal'
export function nivelStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): NivelStock
export function claseStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): string

// textos.ts
export const TOOLTIP_ALMACEN = 'Disponible con Almacén (próxima entrega)'
```

**Ficha** (`formulario.md`): "Opciones del combo: solo los modelos para los que alguna fila…"; "Traducción del modelo…" (ya cubierta por `traducirModelo`, no se duplica); "Modelo de un SKU: en minúsculas…"; "Una fila por tipo de `GET /api/componentes/agrupados`…" (orden y nombres); "Color del SKU, en la lista y en el botón…".

- [x] **Step 1: Tests de `modelos` (fallan)**

En `src/modules/taller/lib/modelos.test.ts`, sustituir las dos primeras líneas de import por:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, componente } from '../test/fabrica'
import { MODELOS_ORDENADOS, extraerModelo, modelosDisponibles, traducirModelo } from './modelos'
```

y añadir al final del fichero:

```ts
describe('extraerModelo (modelo de un SKU)', () => {
  it('gana el código más largo que sea prefijo del resto', () => {
    expect(extraerModelo('bati13promaxneg', 'bat')).toBe('13promax')
    expect(extraerModelo('bati13pro', 'bat')).toBe('13pro')
    expect(extraerModelo('bati13', 'bat')).toBe('13')
    expect(extraerModelo('lcdixsmaxnegra', 'lcd')).toBe('xsmax')
    expect(extraerModelo('lcdixsnegra', 'lcd')).toBe('xs')
    expect(extraerModelo('lcdi6splus', 'lcd')).toBe('6splus')
    expect(extraerModelo('bati16e', 'bat')).toBe('16e')
  })
  it('quita el prefijo del tipo y la "i" inicial, y no distingue mayúsculas', () => {
    expect(extraerModelo('BATI14PLUS', 'bat')).toBe('14plus')
    expect(extraerModelo('chai13negro', 'cha')).toBe('13')
    expect(extraerModelo('cami13', 'cam')).toBe('13')
    expect(extraerModelo('batise2020', 'bat')).toBe('se2020')
    expect(extraerModelo('batair', 'bat')).toBe('air')
  })
  it('el prefijo "g" no se confunde con el resto del SKU', () => {
    expect(extraerModelo('gi13', 'g')).toBe('13')
    expect(extraerModelo('gi14promaxnegra', 'g')).toBe('14promax')
    expect(extraerModelo('mci13', 'mc')).toBe('13')
  })
  it('los componentes "otro" llevan el modelo igual que los demás', () => {
    expect(extraerModelo('otroi13', 'otro')).toBe('13')
    expect(extraerModelo('otroi14', 'otro')).toBe('14')
  })
  it('sin coincidencia devuelve null', () => {
    expect(extraerModelo('batuniversal', 'bat')).toBeNull()
    expect(extraerModelo('bat', 'bat')).toBeNull()
    expect(extraerModelo('', 'bat')).toBeNull()
  })
})

describe('modelosDisponibles (opciones del combo de modelo)', () => {
  const a = agrupados()
  it('salen en el orden de MODELOS_ORDENADOS y solo de SKU activos', () => {
    const grupos = ['bat', 'cha', 'lcd', 'cam'].map((prefijo) => ({ prefijo, skus: a[prefijo] }))
    // bati12 está inactivo: el 12 no aparece; 13promax va después de 13 y antes de 14, como en la lista
    expect(modelosDisponibles(grupos)).toEqual(['13', '13promax', '14'])
  })
  it('solo mira los grupos que recibe (quien llama decide que "otro" no aporta)', () => {
    expect(modelosDisponibles([{ prefijo: 'cam', skus: a.cam }])).toEqual(['13'])
    expect(modelosDisponibles([{ prefijo: 'g', skus: a.g }, { prefijo: 'mc', skus: a.mc }])).toEqual(['13', '14'])
    expect(modelosDisponibles([])).toEqual([])
  })
  it('un SKU sin modelo reconocible no aporta nada y los repetidos salen una vez', () => {
    const skus = [componente({ idCom: 1, tipo: 'batuniversal' }), componente({ idCom: 2, tipo: 'bati15' }), componente({ idCom: 3, tipo: 'bati15negra' })]
    expect(modelosDisponibles([{ prefijo: 'bat', skus }])).toEqual(['15'])
  })
  it('el resultado son siempre códigos de MODELOS_ORDENADOS', () => {
    const grupos = Object.keys(a).map((prefijo) => ({ prefijo, skus: a[prefijo] }))
    for (const m of modelosDisponibles(grupos)) expect(MODELOS_ORDENADOS).toContain(m)
  })
})
```

```bash
npx vitest run src/modules/taller/lib/modelos.test.ts
```
Expected: FALLA (`extraerModelo` y `modelosDisponibles` no exportados de `./modelos`).

- [x] **Step 2: Implementar en `modelos.ts`**

En `src/modules/taller/lib/modelos.ts`, añadir como primera línea del fichero:

```ts
import type { Componente } from '@/shared/api/client'
```

y al final del fichero:

```ts
/** Código de modelo de un SKU (calco de extraerModelo): en minúsculas, sin el prefijo del tipo y, si lo que queda empieza por
 *  "i", sin esa "i"; de los códigos de MODELOS_ORDENADOS que sean prefijo del resto gana el MÁS LARGO
 *  ('bati13promaxneg' → '13promax', no '13'). null si ninguno. */
export function extraerModelo(sku: string, prefijo: string): string | null {
  let resto = sku.toLowerCase().slice(prefijo.length)
  if (resto.startsWith('i')) resto = resto.slice(1)
  let mejor: string | null = null
  for (const m of MODELOS_ORDENADOS) {
    if (resto.startsWith(m) && (mejor === null || m.length > mejor.length)) mejor = m
  }
  return mejor
}

/** Modelos de MODELOS_ORDENADOS (en ese orden) presentes en algún SKU ACTIVO de los grupos indicados. Quien llama decide qué
 *  grupos pasa: el formulario pasa los tipos que son fila (nunca 'otro'). */
export function modelosDisponibles(grupos: { prefijo: string; skus: Componente[] }[]): string[] {
  const presentes = new Set<string>()
  for (const { prefijo, skus } of grupos) {
    for (const c of skus) {
      if (!c.activo) continue
      const m = extraerModelo(c.tipo, prefijo)
      if (m !== null) presentes.add(m)
    }
  }
  return MODELOS_ORDENADOS.filter((m) => presentes.has(m))
}
```

```bash
npx vitest run src/modules/taller/lib/modelos.test.ts
```
Expected: PASS (los 2 tests existentes + 9 nuevos).

- [x] **Step 3: Tests de `piezas` (fallan)**

En `src/modules/taller/lib/piezas.test.ts`, sustituir las dos primeras líneas de import por:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, componente } from '../test/fabrica'
import { PREFIJOS_GLASS, PREFIJO_OTRO, categoriaPieza, claseStock, nivelStock, nombreTipo, prefijosDeFila } from './piezas'
```

y añadir al final del fichero:

```ts
describe('nombreTipo (nombre de la fila del formulario)', () => {
  it('traduce los seis tipos conocidos', () => {
    expect(nombreTipo('bat')).toBe('Batería')
    expect(nombreTipo('cha')).toBe('Chasis')
    expect(nombreTipo('g')).toBe('Glass')
    expect(nombreTipo('cam')).toBe('Cámara')
    expect(nombreTipo('lcd')).toBe('Pantalla')
    expect(nombreTipo('mc')).toBe('Marco')
  })
  it('cualquier otro prefijo se muestra tal cual (a diferencia de categoriaPieza, que lo deja vacío)', () => {
    expect(nombreTipo('alt')).toBe('alt')
    expect(nombreTipo('otro')).toBe('otro')
    expect(categoriaPieza('alti13')).toBe('')
  })
})

describe('prefijosDeFila (una fila por tipo, en el orden del servidor)', () => {
  it('reparación: todos menos g, mc y otro', () => {
    expect(prefijosDeFila(agrupados(), false)).toEqual(['bat', 'cha', 'lcd', 'cam'])
  })
  it('glass: solo g y mc, en el orden del servidor', () => {
    expect(prefijosDeFila(agrupados(), true)).toEqual(['g', 'mc'])
    const invertido = { mc: agrupados().mc, bat: agrupados().bat, g: agrupados().g }
    expect(prefijosDeFila(invertido, true)).toEqual(['mc', 'g'])
  })
  it('salta los grupos vacíos', () => {
    const a = agrupados()
    a.cha = []
    a.mc = []
    expect(prefijosDeFila(a, false)).toEqual(['bat', 'lcd', 'cam'])
    expect(prefijosDeFila(a, true)).toEqual(['g'])
  })
  it('conserva un prefijo desconocido en su sitio', () => {
    const a = { bat: agrupados().bat, alt: [componente({ idCom: 171, tipo: 'alti13' })], g: agrupados().g, cam: agrupados().cam, otro: agrupados().otro }
    expect(prefijosDeFila(a, false)).toEqual(['bat', 'alt', 'cam'])
  })
  it('constantes', () => {
    expect(PREFIJO_OTRO).toBe('otro')
    expect(PREFIJOS_GLASS).toEqual(['g', 'mc'])
  })
})

describe('nivelStock y claseStock (color del SKU)', () => {
  it('stock 0 → sin stock, en rojo', () => {
    expect(nivelStock({ stock: 0, stockMinimo: 2 })).toBe('sinStock')
    expect(claseStock({ stock: 0, stockMinimo: 2 })).toBe('text-rojo-sin-stock')
  })
  it('0 < stock ≤ mínimo → bajo, en ámbar', () => {
    expect(nivelStock({ stock: 1, stockMinimo: 2 })).toBe('bajo')
    expect(nivelStock({ stock: 2, stockMinimo: 2 })).toBe('bajo')
    expect(claseStock({ stock: 2, stockMinimo: 2 })).toBe('text-fila-solicitud-brd')
  })
  it('por encima del mínimo → normal, sin clase', () => {
    expect(nivelStock({ stock: 3, stockMinimo: 2 })).toBe('normal')
    expect(claseStock({ stock: 3, stockMinimo: 2 })).toBe('')
  })
  it('con mínimo 0: stock 0 sigue siendo sin stock y cualquier unidad es normal', () => {
    expect(nivelStock({ stock: 0, stockMinimo: 0 })).toBe('sinStock')
    expect(nivelStock({ stock: 1, stockMinimo: 0 })).toBe('normal')
  })
  it('acepta un Componente entero', () => {
    expect(nivelStock(componente({ stock: 1, stockMinimo: 1 }))).toBe('bajo')
  })
})
```

```bash
npx vitest run src/modules/taller/lib/piezas.test.ts
```
Expected: FALLA (`nombreTipo`, `prefijosDeFila`, `nivelStock`, `claseStock`, `PREFIJO_OTRO`, `PREFIJOS_GLASS` no exportados).

- [x] **Step 4: Implementar en `piezas.ts`**

En `src/modules/taller/lib/piezas.ts`, añadir como primera línea del fichero:

```ts
import type { Componente, ComponentesAgrupados } from '@/shared/api/client'
```

y al final del fichero (`categoriaPieza` y sus constantes no se tocan):

```ts
// ── Formulario de reparación ─────────────────────────────────────────────────────────────────────────────────────────

/** El grupo 'otro' no es fila del formulario: alimenta OTRAS ACCIONES. */
export const PREFIJO_OTRO = 'otro'
/** Los dos tipos de la variante Glass. */
export const PREFIJOS_GLASS: readonly string[] = ['g', 'mc']

const NOMBRES_TIPO: Record<string, string> = { bat: 'Batería', cha: 'Chasis', g: 'Glass', cam: 'Cámara', lcd: 'Pantalla', mc: 'Marco' }

/** Nombre de la fila para un prefijo del servidor. Distinta de categoriaPieza a propósito: aquí se parte del prefijo exacto
 *  (no del SKU) y un prefijo desconocido se muestra tal cual. */
export function nombreTipo(prefijo: string): string {
  return NOMBRES_TIPO[prefijo] ?? prefijo
}

/** Prefijos que son fila, en el orden de claves del servidor, sin grupos vacíos ni 'otro'. glass=false → todos menos g/mc
 *  (formulario de reparación); glass=true → solo g/mc (variante Glass). */
export function prefijosDeFila(agrupados: ComponentesAgrupados, glass: boolean): string[] {
  return Object.keys(agrupados).filter(
    (prefijo) => prefijo !== PREFIJO_OTRO && agrupados[prefijo].length > 0 && PREFIJOS_GLASS.includes(prefijo) === glass,
  )
}

export type NivelStock = 'sinStock' | 'bajo' | 'normal'

/** stock 0 → 'sinStock'; 0 < stock ≤ mínimo → 'bajo'; resto 'normal'. */
export function nivelStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): NivelStock {
  if (c.stock === 0) return 'sinStock'
  if (c.stock > 0 && c.stock <= c.stockMinimo) return 'bajo'
  return 'normal'
}

const CLASES_STOCK: Record<NivelStock, string> = { sinStock: 'text-rojo-sin-stock', bajo: 'text-fila-solicitud-brd', normal: '' }

/** Clase Tailwind del SKU, en la lista y en el botón del combo: rojo (#B03040) sin stock, ámbar (#C07800) en stock bajo y el
 *  color normal en otro caso. Los dos tokens ya existen en tokens.css. */
export function claseStock(c: Pick<Componente, 'stock' | 'stockMinimo'>): string {
  return CLASES_STOCK[nivelStock(c)]
}
```

```bash
npx vitest run src/modules/taller/lib/piezas.test.ts
```
Expected: PASS (los 2 tests existentes + 12 nuevos).

- [x] **Step 5: `TOOLTIP_ALMACEN` en `textos.ts`**

En `src/modules/taller/lib/textos.ts`, añadir al final (la constante `TOOLTIP_FORMULARIO` se queda: la siguen usando `PendientesPage` y `MenuHistorial` hasta la Task 18):

```ts
/** Acciones de la campana que dependen del módulo de Almacén (pedidos de piezas, stock completo): visibles pero deshabilitadas,
 *  con este tooltip. */
export const TOOLTIP_ALMACEN = 'Disponible con Almacén (próxima entrega)'
```

```bash
grep -rn "TOOLTIP_FORMULARIO" src --include=*.ts --include=*.tsx | wc -l
```
Expected: el mismo número de apariciones que antes de la tarea (no se ha retirado nada).

- [x] **Step 6: `npm run check` en verde**

```bash
npm run check
```
Expected: lint (sin imports entre módulos: `lib` solo importa de `@/shared/api/client` y, en los tests, de `../test/fabrica`), typecheck y toda la suite en verde.

- [x] **Step 7: Commit**

```bash
git add src/modules/taller/lib/modelos.ts src/modules/taller/lib/modelos.test.ts src/modules/taller/lib/piezas.ts src/modules/taller/lib/piezas.test.ts src/modules/taller/lib/textos.ts
git commit -m "feat(web): lib del taller para el formulario: modelo de un SKU, tipos de fila y color por stock"
```

---



### Task 6: Web — `formulario/estado.ts` (1): tipos, estado inicial, modelo y filas

**Files:**
- Create: `src/modules/taller/formulario/estado.ts`, `src/modules/taller/formulario/estado.test.ts`

**Interfaces:**
- Consumes: W1 (`AsignacionActiva`, `Componente`, `ComponentesAgrupados`, `DetalleEdicion`, `SolicitudAsignacion` de `@/shared/api/client`), W2 (`agrupados`, `componente`, `asignacionActiva` de `../test/fabrica`), W3 (`extraerModelo`, `modelosDisponibles` de `../lib/modelos`; `PREFIJO_OTRO`, `nombreTipo`, `prefijosDeFila` de `../lib/piezas`).
- Produces: W4 entero (todos los tipos, con los campos que rellenan las Tasks 7 y 8 inicializados a su valor neutro), W5 (`DatosNuevo`, `DatosEditar`, `AccionFormulario` completa, `estadoInicial` para `DatosNuevo` sin solicitudes, `reducir` con las siete acciones de esta tarea y `default: return estado` para el resto) y los selectores "Task 6" de W6:

```ts
export function estadoInicial(datos: DatosNuevo | DatosEditar): EstadoFormulario
export function reducir(estado: EstadoFormulario, accion: AccionFormulario): EstadoFormulario
export function filasVisibles(e: EstadoFormulario): boolean
export function componenteDe(fila: FilaEstado): Componente | null
export function stockDe(fila: FilaEstado): number | null
export function filaSinSku(fila: FilaEstado): boolean
export function filaActiva(fila: FilaEstado): boolean
export function etiquetaImei(e: EstadoFormulario): string
export function tituloPestana(e: EstadoFormulario): string
export function textoConflicto(activas: AsignacionActiva[], idAsignacionPropia: string, idTecSesion: number | null): string | null
```

**Ficha** (`docs/paridad/formulario.md`): "Etiqueta IMEI…" (flujo nuevo); "Título (pestaña del navegador)…" (texto); "Modelo inicial sin solicitudes, flujo nuevo: filas ocultas…"; "Después, si el combo sigue sin valor y no es edición, se consulta…" (efecto sobre el estado: `modeloTelefono`); "Cambiar el modelo reaplica el filtro…"; "Banda de conflicto…" (`textoConflicto`); "Combo SKU…" (opciones y valor por defecto); "Stock…"; "Tipo sin SKU para el modelo…"; "Estado inicial de una fila con SKU…"; "\"+\": solo suma si cantidad < stock…"; "\"Reutilizado\"… al marcar deshabilita…"; "Cambiar de SKU…"; "Observación…" y "Diálogo \"Observación\"…" (regla: vacío no borra); "Cualquier cambio en la fila… reprograma el autoguardado" (`revision`); calco "Tras desmarcar \"Reutilizado\", \"+\" queda habilitado aunque el stock sea 0".

**Reglas de diseño de este fichero (valen para las Tasks 6, 7 y 8):**
- Fichero **puro**: sin React, sin `fetch`, sin `Date`. Todos los comandos se lanzan desde `gestion-reparaciones-web`, en la rama `feature/web-formulario`.
- `controles` se mantiene de forma imperativa, paso a paso, como la referencia; los selectores no lo derivan del stock (así salen los calcos). Cada acción de fila exige, además, que su control esté encendido: una fila guardada, con agotado confirmado, ya reparada, sin SKU o con su guardado en vuelo no admite ningún cambio; con solicitud del servidor solo admite lo que su `controles` deja encendido ("Reutilizado" si no está en camino, y la observación).
- Una acción que no procede devuelve **el mismo objeto de estado** (`toBe`): ni re-render ni reprogramación del autoguardado.
- `revision` sube solo con **cambios de datos** (filas, modelo, agotado local, otras acciones). Pedir confirmación, iniciar o fallar un guardado no son cambios de datos y no la suben; `FILA_GUARDADA` y `ACCION_GUARDADA` suben `volcados`. `EDITAR_DESCRIPCION_AGOTADO` no sube ninguna de las dos.
- `OtraAccion` lleva un campo **opcional** añadido al esqueleto, `pideOtroClic?: boolean`, imprescindible para calcar "tras un error el texto sigue en «✓ Confirmar» pero el siguiente clic vuelve a pedir confirmación" (con un solo booleano no se puede representar). Quien construya una `OtraAccion` no necesita ponerlo.

- [x] **Step 1: Test de estado inicial, modelo y cabecera (falla)**

`src/modules/taller/formulario/estado.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, asignacionActiva, componente } from '../test/fabrica'
import {
  type AccionFormulario, type DatosNuevo, type EstadoFormulario, type FilaEstado,
  componenteDe, estadoInicial, etiquetaImei, filaActiva, filaSinSku, filasVisibles, reducir, stockDe, textoConflicto, tituloPestana,
} from './estado'

const IMEI = '355400000000111'

function datosNuevo(parcial: Partial<DatosNuevo> = {}): DatosNuevo {
  return {
    modo: 'nuevo', idAsignacion: 'A20260916_1', imei: IMEI, agrupados: agrupados(), solicitudes: [], incidencia: null,
    modeloTelefono: null, ...parcial,
  }
}
/** Estado de flujo nuevo con el modelo ya elegido a mano (combo libre). */
function conModelo(modelo: string, parcial: Partial<DatosNuevo> = {}): EstadoFormulario {
  return reducir(estadoInicial(datosNuevo(parcial)), { tipo: 'CAMBIAR_MODELO', modelo })
}
function aplicar(estado: EstadoFormulario, ...acciones: AccionFormulario[]): EstadoFormulario {
  return acciones.reduce(reducir, estado)
}
function fila(estado: EstadoFormulario, prefijo: string): FilaEstado {
  const f = estado.filas.find((x) => x.prefijo === prefijo)
  if (!f) throw new Error(`no hay fila ${prefijo}`)
  return f
}

describe('estado del formulario (1): estado inicial, modelo y cabecera', () => {
  it('sin modelo las filas están ocultas', () => {
    const e = estadoInicial(datosNuevo())
    expect(e.modelo).toBeNull()
    expect(e.modeloBloqueado).toBe(false)
    expect(filasVisibles(e)).toBe(false)
    expect(filasVisibles(conModelo('13'))).toBe(true)
    // las filas existen igualmente, en el orden del servidor y sin g, mc ni otro
    expect(e.filas.map((f) => f.prefijo)).toEqual(['bat', 'cha', 'lcd', 'cam'])
    expect(e.filas.map((f) => f.nombre)).toEqual(['Batería', 'Chasis', 'Pantalla', 'Cámara'])
    expect(e.componentesOtro.map((c) => c.idCom)).toEqual([161, 162])
    expect(e.categoria).toBe('R')
    expect(e.revision).toBe(0)
    expect(e.volcados).toBe(0)
  })

  it('el modelo del teléfono se selecciona y bloquea el combo', () => {
    const e = estadoInicial(datosNuevo({ modeloTelefono: '13' }))
    expect(e.modelo).toBe('13')
    expect(e.modeloBloqueado).toBe(true)
    expect(filasVisibles(e)).toBe(true)
    expect(fila(e, 'bat').opciones.map((c) => c.idCom)).toEqual([101])
    // bloqueado: el combo ya no cambia el modelo
    expect(reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '14' })).toBe(e)
  })

  it('un modelo de teléfono fuera de la lista se ignora', () => {
    for (const modeloTelefono of ['', '12', 'desconocido']) {
      const e = estadoInicial(datosNuevo({ modeloTelefono }))
      expect(e.modelo).toBeNull()
      expect(e.modeloBloqueado).toBe(false)
    }
  })

  it('los modelos salen en orden de tienda y solo de SKU activos de filas visibles', () => {
    // bati12 está inactivo (no aporta "12"); "otro" no aporta modelos
    expect(estadoInicial(datosNuevo()).modelos).toEqual(['13', '13promax', '14'])
    // elegir un modelo que no está en el combo no hace nada
    const e = estadoInicial(datosNuevo())
    expect(reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '12' })).toBe(e)
  })

  it('elegir modelo filtra los SKU y preselecciona el primero con stock', () => {
    const catalogo = agrupados()
    catalogo.bat = [componente({ idCom: 105, tipo: 'bati13negra', stock: 0 }), ...catalogo.bat]
    const e = conModelo('13', { agrupados: catalogo })
    const bat = fila(e, 'bat')
    expect(bat.opciones.map((c) => c.idCom)).toEqual([105, 101]) // ni bati14, ni bati13promax, ni el inactivo
    expect(bat.idCom).toBe(101) // el primero CON stock, no el primero de la lista
    expect(componenteDe(bat)?.tipo).toBe('bati13')
    expect(stockDe(bat)).toBe(5)
    expect(bat.controles).toEqual({ mas: true, menos: false, reutilizado: true, sku: true, observacion: true })
    expect(bat.cantidad).toBe(0)
    expect(filaActiva(bat)).toBe(false)
  })

  it('sin ninguno con stock preselecciona el primero', () => {
    const bat = fila(conModelo('14'), 'bat')
    expect(bat.idCom).toBe(102)
    expect(stockDe(bat)).toBe(0)
    expect(bat.controles.mas).toBe(false) // "+" deshabilitado con stock ≤ 0
    expect(bat.controles.reutilizado).toBe(true)
  })

  it('tipo sin SKU para el modelo queda sin idCom', () => {
    const cam = fila(conModelo('14'), 'cam')
    expect(cam.opciones).toEqual([])
    expect(cam.idCom).toBeNull()
    expect(filaSinSku(cam)).toBe(true)
    expect(stockDe(cam)).toBeNull()
    expect(cam.controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: false })
    // la fila no desaparece y no admite nada
    const e = conModelo('14')
    expect(e.filas.map((f) => f.prefijo)).toContain('cam')
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'cam' })).toBe(e)
    expect(reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cam', valor: true })).toBe(e)
    expect(reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'cam', texto: 'rota' })).toBe(e)
  })

  it('textoConflicto agrupa por categoría, ordena Reparación·Glass·Pulido, excluye la propia y marca (tú)', () => {
    const activas = [
      asignacionActiva({ idRep: 'AP20260916_3', nombreTecnico: 'Técnico J', idTec: 7 }),
      asignacionActiva({ idRep: 'AG20260916_2', nombreTecnico: 'Técnico H', idTec: 6 }),
      asignacionActiva({ idRep: 'A20260916_1', nombreTecnico: 'Técnico A', idTec: 4 }),
      asignacionActiva({ idRep: 'A20260916_8', nombreTecnico: 'Técnico F', idTec: 3 }),
      asignacionActiva({ idRep: 'A20260916_9', nombreTecnico: 'Técnico A', idTec: 4 }),
    ]
    expect(textoConflicto(activas, 'A20260916_1', 4)).toBe(
      '⚠ Este IMEI también está asignado a — Reparación: Técnico F, Técnico A (tú) · Glass: Técnico H · Pulido: Técnico J',
    )
    // desde la glass: la propia AG queda fuera y las categorías vacías se omiten
    expect(textoConflicto(activas.slice(1, 3), 'AG20260916_2', 6)).toBe('⚠ Este IMEI también está asignado a — Reparación: Técnico A')
    expect(textoConflicto([asignacionActiva({ idRep: 'A20260916_1' })], 'A20260916_1', 4)).toBeNull()
    expect(textoConflicto([], 'A20260916_1', null)).toBeNull()
  })

  it('etiquetaImei y tituloPestana en flujo nuevo', () => {
    const e = estadoInicial(datosNuevo())
    expect(etiquetaImei(e)).toBe(`IMEI: ${IMEI}`)
    expect(tituloPestana(e)).toBe(`Nueva reparación — IMEI ${IMEI}`)
  })

  it('glass solo crea filas g y mc', () => {
    const e = estadoInicial(datosNuevo({ modo: 'glass', idAsignacion: 'AG20260916_2' }))
    expect(e.modo).toBe('glass')
    expect(e.categoria).toBe('G')
    expect(e.filas.map((f) => f.prefijo)).toEqual(['g', 'mc'])
    expect(e.filas.map((f) => f.nombre)).toEqual(['Glass', 'Marco'])
    expect(e.modelos).toEqual(['13', '14']) // solo de g y mc
    expect(e.componentesOtro).toHaveLength(2)
    expect(tituloPestana(e)).toBe(`Nueva reparación — IMEI ${IMEI}`)
  })
})
```

```bash
npm test -- src/modules/taller/formulario/estado.test.ts
```
Expected: falla la suite entera con `Error: Cannot find module './estado'` (0 tests ejecutados).

- [x] **Step 2: Tipos, estado inicial, cambio de modelo y selectores**

`src/modules/taller/formulario/estado.ts` (fichero completo en este paso):

```ts
import type { AsignacionActiva, Componente, ComponentesAgrupados, DetalleEdicion, SolicitudAsignacion } from '@/shared/api/client'
import { extraerModelo, modelosDisponibles } from '../lib/modelos'
import { PREFIJO_OTRO, nombreTipo, prefijosDeFila } from '../lib/piezas'

/** Estado del formulario de reparación: fichero PURO (sin React, sin fetch, sin Date). Los componentes solo pintan lo que
 *  dicen los selectores y despachan acciones; las reglas de docs/paridad/formulario.md viven aquí. */

// ───────────────────────────── Tipos ─────────────────────────────

export type ModoFormulario = 'nuevo' | 'glass' | 'editar'
export type Categoria = 'R' | 'G'

/** Marca "✓ Guardada": vive solo en el borrador. */
export type Guardada = { idRep: string; fecha: string } // fecha 'dd/MM HH:mm' local del navegador
/** Solicitud local confirmada en esta sesión (aún no enviada). registrado = ya se envió su agotar-componente
 *  en un intento de "Terminar asignación" y no debe reenviarse. */
export type AgotadoLocal = { descripcion: string | null; registrado: boolean }
/** Solicitud ya guardada en el servidor, aplicada a la fila al abrir. Las rechazadas y las recibidas no dejan este campo. */
export type SolicitudServidor = { estado: 'pendiente' | 'enCamino'; descripcion: string | null }
export type RolFila = 'normal' | 'editada' | 'yaReparado' // 'editada' y 'yaReparado' solo en modo editar
export type OriginalEdicion = { idCom: number; cantidad: number; reutilizado: boolean; observacion: string | null }

/** Habilitado de los controles, mantenido por el reductor paso a paso como lo hace la referencia (no se deriva: así se
 *  calcan rarezas como "+" habilitado con stock 0 tras desmarcar Reutilizado). Una fila sin SKU para el modelo
 *  (opciones vacías) se pinta siempre deshabilitada y atenuada, diga lo que diga este campo. */
export type ControlesFila = { mas: boolean; menos: boolean; reutilizado: boolean; sku: boolean; observacion: boolean }

export type FilaEstado = {
  prefijo: string
  nombre: string // nombreTipo(prefijo)
  skus: Componente[] // todos los ACTIVOS del tipo
  opciones: Componente[] // skus filtrados por el modelo (todos si no hay modelo)
  idCom: number | null // SKU elegido; null si opciones está vacío
  cantidad: number
  reutilizado: boolean
  observacion: string | null
  controles: ControlesFila
  rol: RolFila
  guardada: Guardada | null
  confirmandoGuardar: boolean // "✓ Confirmar" de "✓ Guardar fila"
  guardando: boolean // llamada en vuelo: botón deshabilitado
  agotado: AgotadoLocal | null
  solicitud: SolicitudServidor | null
  recibidoPendienteUso: boolean // "✓ Recibido" aún visible
  original: OriginalEdicion | null // solo rol 'editada'
}

export type OrigenAccion = 'nueva' | 'editada' | 'yaReparada'
export type OtraAccion = {
  id: number // identidad local estable (contador del estado)
  texto: string
  origen: OrigenAccion // 'editada' y 'yaReparada' solo en modo editar
  guardada: Guardada | null
  confirmando: boolean
  guardando: boolean
  /** Tras FALLO_GUARDAR_ACCION: el texto sigue en "✓ Confirmar" (confirmando = true) pero el siguiente clic vuelve a pedir
   *  confirmación en vez de guardar. Opcional: quien construye una OtraAccion no necesita ponerlo. */
  pideOtroClic?: boolean
}

export type EdicionEstado = {
  idRep: string
  tipo: 'pieza' | 'accion'
  idTecOriginal: number
  updatedAt: string
  textoAccionOriginal: string | null // tipo 'accion'
  idComAccion: number | null // tipo 'accion': el idCom 'otro…' de la reparación editada
}

export type GuardadoEstado = {
  clics: 0 | 1 // 1 = el siguiente clic ejecuta
  textoConfirmacion: boolean // una vez true, no vuelve a false
  enCurso: boolean
}

export type EstadoFormulario = {
  modo: ModoFormulario
  categoria: Categoria // 'G' si idAsignacion empieza por 'AG' o idRep por 'G'
  idAsignacion: string | null // nuevo y glass
  imei: string
  incidencia: string | null // idRepAnterior de todos los guardados
  modelo: string | null
  modeloBloqueado: boolean
  modelos: string[] // opciones del combo, en el orden de MODELOS_ORDENADOS
  tieneSolicitudesIniciales: boolean // hubo solicitudes (de cualquier estado): las filas nunca se ocultan
  filas: FilaEstado[]
  componentesOtro: Componente[] // grupo 'otro' completo (no se mira 'activo')
  otros: OtraAccion[]
  siguienteIdAccion: number
  edicion: EdicionEstado | null
  guardado: GuardadoEstado
  borradorRecuperado: boolean // banda azul (la pone aplicarBorrador)
  borradorDescartado: boolean // tras un guardado real: no se vuelve a escribir borrador
  /** Sube con cada cambio de datos que reprograma el autoguardado (todo menos EDITAR_DESCRIPCION_AGOTADO; pedir confirmación,
   *  iniciar o fallar un guardado no son cambios de datos y no la suben). */
  revision: number
  /** Sube cuando hay que volcar el borrador YA: FILA_GUARDADA, ACCION_GUARDADA y DESBLOQUEAR_BORRADAS con cambios. */
  volcados: number
}

// ───────────────────────────── Datos de entrada y acciones ─────────────────────────────

export type DatosNuevo = {
  modo: 'nuevo' | 'glass'
  idAsignacion: string
  imei: string
  agrupados: ComponentesAgrupados
  solicitudes: SolicitudAsignacion[] // incluye rechazadas
  incidencia: string | null
  modeloTelefono: string | null // '' o fuera de la lista = null; solo se usa si tras las solicitudes no hay modelo
}
export type DatosEditar = {
  modo: 'editar'
  idRep: string
  detalle: DetalleEdicion
  agrupados: ComponentesAgrupados
  yaReparados: number[]
  accionesYaReparadas: string[]
}

export type AccionFormulario =
  // filas y modelo
  | { tipo: 'CAMBIAR_MODELO'; modelo: string | null }
  | { tipo: 'SUMAR'; prefijo: string }
  | { tipo: 'RESTAR'; prefijo: string }
  | { tipo: 'CAMBIAR_SKU'; prefijo: string; idCom: number }
  | { tipo: 'MARCAR_REUTILIZADO'; prefijo: string; valor: boolean }
  | { tipo: 'PONER_OBSERVACION'; prefijo: string; texto: string } // recorta; vacío = sin efecto
  | { tipo: 'BORRAR_OBSERVACION'; prefijo: string }
  // guardar fila, agotado local, otras acciones y zona de guardar
  | { tipo: 'PEDIR_CONFIRMACION_FILA'; prefijo: string } // 1.er clic → "✓ Confirmar"
  | { tipo: 'INICIO_GUARDAR_FILA'; prefijo: string } // 2.º clic: guardando = true
  | { tipo: 'FILA_GUARDADA'; prefijo: string; idRep: string; fecha: string }
  | { tipo: 'FALLO_GUARDAR_FILA'; prefijo: string } // rehabilita, vuelve a "✓ Guardar fila"
  | { tipo: 'CONFIRMAR_AGOTADO'; prefijo: string; descripcion: string } // local; recorta; borra la observación
  | { tipo: 'EDITAR_DESCRIPCION_AGOTADO'; prefijo: string; descripcion: string } // NO sube revision
  | { tipo: 'CANCELAR_AGOTADO'; prefijo: string }
  | { tipo: 'AGOTADO_REGISTRADO'; prefijo: string } // agotar-componente OK en este intento
  | { tipo: 'ANADIR_ACCION' }
  | { tipo: 'ESCRIBIR_ACCION'; id: number; texto: string } // devuelve "✓ Confirmar" a "✓ Guardar"
  | { tipo: 'QUITAR_ACCION'; id: number }
  | { tipo: 'PEDIR_CONFIRMACION_ACCION'; id: number }
  | { tipo: 'INICIO_GUARDAR_ACCION'; id: number }
  | { tipo: 'ACCION_GUARDADA'; id: number; idRep: string; fecha: string }
  | { tipo: 'FALLO_GUARDAR_ACCION'; id: number } // rehabilita; el texto SIGUE en "✓ Confirmar" pero pide otro clic
  | { tipo: 'PEDIR_CONFIRMACION_GUARDAR' } // zona: 1.er clic
  | { tipo: 'INICIO_GUARDADO' } // zona: 2.º clic
  | { tipo: 'FALLO_GUARDADO' } // clics = 0, enCurso = false, el texto no vuelve
  | { tipo: 'GUARDADO_COMPLETADO' } // borradorDescartado = true
  // borrador
  | { tipo: 'REEMPLAZAR'; estado: EstadoFormulario } // resultado de aplicarBorrador; no sube revision
  | { tipo: 'DESBLOQUEAR_BORRADAS'; idsExistentes: string[] } // filas/acciones guardadas cuyo idRep ya no existe

// ───────────────────────────── Filas: construcción ─────────────────────────────

const CONTROLES_APAGADOS: ControlesFila = { mas: false, menos: false, reutilizado: false, sku: false, observacion: false }

type BaseFila = Pick<FilaEstado, 'prefijo' | 'nombre' | 'skus'>

function opcionesPara(base: BaseFila, modelo: string | null): Componente[] {
  return modelo === null ? base.skus : base.skus.filter((c) => extraerModelo(c.tipo, base.prefijo) === modelo)
}

/** Controles de una fila recién puesta sobre un SKU: "-" apagado, "+" según stock, el resto encendido. */
function controlesIniciales(c: Componente | null): ControlesFila {
  if (c === null) return CONTROLES_APAGADOS
  return { mas: c.stock > 0, menos: false, reutilizado: true, sku: true, observacion: true }
}

/** Fila en su estado inicial para un modelo: SKU por defecto = el primero con stock o, si ninguno, el primero. */
function filaLimpia(base: BaseFila, modelo: string | null): FilaEstado {
  const opciones = opcionesPara(base, modelo)
  const elegido = opciones.find((c) => c.stock > 0) ?? opciones[0] ?? null
  return {
    prefijo: base.prefijo,
    nombre: base.nombre,
    skus: base.skus,
    opciones,
    idCom: elegido ? elegido.idCom : null,
    cantidad: 0,
    reutilizado: false,
    observacion: null,
    controles: controlesIniciales(elegido),
    rol: 'normal',
    guardada: null,
    confirmandoGuardar: false,
    guardando: false,
    agotado: null,
    solicitud: null,
    recibidoPendienteUso: false,
    original: null,
  }
}

// ───────────────────────────── Estado inicial ─────────────────────────────

type DatosBase = {
  modo: ModoFormulario
  categoria: Categoria
  idAsignacion: string | null
  imei: string
  incidencia: string | null
  agrupados: ComponentesAgrupados
  glass: boolean
}

/** Estado sin modelo: una fila por tipo (orden de prefijosDeFila), todos los SKU activos como opciones. */
function estadoBase(d: DatosBase): EstadoFormulario {
  const bases: BaseFila[] = prefijosDeFila(d.agrupados, d.glass).map((prefijo) => ({
    prefijo,
    nombre: nombreTipo(prefijo),
    skus: (d.agrupados[prefijo] ?? []).filter((c) => c.activo),
  }))
  return {
    modo: d.modo,
    categoria: d.categoria,
    idAsignacion: d.idAsignacion,
    imei: d.imei,
    incidencia: d.incidencia,
    modelo: null,
    modeloBloqueado: false,
    modelos: modelosDisponibles(bases.map((b) => ({ prefijo: b.prefijo, skus: b.skus }))),
    tieneSolicitudesIniciales: false,
    filas: bases.map((b) => filaLimpia(b, null)),
    componentesOtro: d.agrupados[PREFIJO_OTRO] ?? [],
    otros: [],
    siguienteIdAccion: 1,
    edicion: null,
    guardado: { clics: 0, textoConfirmacion: false, enCurso: false },
    borradorRecuperado: false,
    borradorDescartado: false,
    revision: 0,
    volcados: 0,
  }
}

function inicialNuevo(datos: DatosNuevo): EstadoFormulario {
  const base = estadoBase({
    modo: datos.modo,
    categoria: datos.idAsignacion.startsWith('AG') ? 'G' : 'R',
    idAsignacion: datos.idAsignacion,
    imei: datos.imei,
    incidencia: datos.incidencia,
    agrupados: datos.agrupados,
    glass: datos.modo === 'glass',
  })
  const telefono = datos.modeloTelefono !== null && base.modelos.includes(datos.modeloTelefono) ? datos.modeloTelefono : null
  if (telefono === null) return base
  return { ...base, modelo: telefono, modeloBloqueado: true, filas: base.filas.map((f) => filaLimpia(f, telefono)) }
}

function inicialEditar(datos: DatosEditar): EstadoFormulario {
  const glass = datos.idRep.startsWith('G')
  return estadoBase({
    modo: 'editar',
    categoria: glass ? 'G' : 'R',
    idAsignacion: null,
    imei: datos.detalle.imei,
    incidencia: null,
    agrupados: datos.agrupados,
    glass,
  })
}

export function estadoInicial(datos: DatosNuevo | DatosEditar): EstadoFormulario {
  return datos.modo === 'editar' ? inicialEditar(datos) : inicialNuevo(datos)
}

// ───────────────────────────── Selectores de fila ─────────────────────────────

/** false = "Selecciona un modelo de iPhone para continuar". */
export function filasVisibles(e: EstadoFormulario): boolean {
  return e.modo === 'editar' || e.tieneSolicitudesIniciales || e.modelo !== null
}

/** El SKU elegido. */
export function componenteDe(fila: FilaEstado): Componente | null {
  return fila.opciones.find((c) => c.idCom === fila.idCom) ?? null
}

/** null → "—". */
export function stockDe(fila: FilaEstado): number | null {
  const c = componenteDe(fila)
  return c ? c.stock : null
}

/** Tipo sin SKU para el modelo: opacidad 0,4 y todo deshabilitado. */
export function filaSinSku(fila: FilaEstado): boolean {
  return fila.opciones.length === 0
}

export function filaActiva(fila: FilaEstado): boolean {
  return fila.cantidad > 0 || fila.reutilizado || (fila.agotado !== null && fila.solicitud === null)
}

// ───────────────────────────── Reductor ─────────────────────────────

/** Reaplica el filtro a todas las filas y RESETEA las que no están guardadas, en vuelo ni con solicitud activa. */
function cambiarModelo(estado: EstadoFormulario, modelo: string | null): EstadoFormulario {
  if (estado.modeloBloqueado || modelo === estado.modelo) return estado
  if (modelo !== null && !estado.modelos.includes(modelo)) return estado
  const filas = estado.filas.map((fila) => {
    if (fila.guardada !== null || fila.guardando || fila.solicitud !== null) return fila
    const limpia = filaLimpia(fila, modelo)
    // "✓ Recibido" sobrevive al reseteo salvo que la fila se quede sin SKU para el modelo.
    return { ...limpia, recibidoPendienteUso: fila.recibidoPendienteUso && !filaSinSku(limpia) }
  })
  return { ...estado, modelo, filas, revision: estado.revision + 1 }
}

export function reducir(estado: EstadoFormulario, accion: AccionFormulario): EstadoFormulario {
  switch (accion.tipo) {
    case 'CAMBIAR_MODELO':
      return cambiarModelo(estado, accion.modelo)
    default:
      return estado
  }
}

// ───────────────────────────── Selectores de cabecera ─────────────────────────────

export function etiquetaImei(e: EstadoFormulario): string {
  return `IMEI: ${e.imei}`
}

/** Título de la pestaña del navegador y nombre accesible del diálogo. */
export function tituloPestana(e: EstadoFormulario): string {
  return `Nueva reparación — IMEI ${e.imei}`
}

const CATEGORIAS_CONFLICTO = ['Reparación', 'Glass', 'Pulido'] as const

function categoriaConflicto(idRep: string): (typeof CATEGORIAS_CONFLICTO)[number] {
  if (idRep.startsWith('AG')) return 'Glass'
  if (idRep.startsWith('AP')) return 'Pulido'
  return 'Reparación'
}

/** Texto de la banda de conflicto, o null si el IMEI no tiene más asignaciones abiertas que la propia. */
export function textoConflicto(activas: AsignacionActiva[], idAsignacionPropia: string, idTecSesion: number | null): string | null {
  const ajenas = activas.filter((a) => a.idRep !== idAsignacionPropia)
  if (ajenas.length === 0) return null
  const grupos = CATEGORIAS_CONFLICTO.map((categoria) => {
    const nombres = ajenas
      .filter((a) => categoriaConflicto(a.idRep) === categoria)
      .map((a) => (idTecSesion !== null && a.idTec === idTecSesion ? `${a.nombreTecnico} (tú)` : a.nombreTecnico))
    return nombres.length === 0 ? null : `${categoria}: ${nombres.join(', ')}`
  }).filter((g): g is string => g !== null)
  return `⚠ Este IMEI también está asignado a — ${grupos.join(' · ')}`
}
```

```bash
npm test -- src/modules/taller/formulario/estado.test.ts
```
Expected: `Tests  10 passed (10)`.

- [x] **Step 3: Test de las filas (falla)**

Añade este bloque **al final** de `src/modules/taller/formulario/estado.test.ts` (la cabecera de imports y ayudas del Step 1 ya trae todo lo que usa):

```ts
describe('estado del formulario (1): filas', () => {
  it('sumar respeta el stock y deshabilita Reutilizado', () => {
    const uno = aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'lcd' }) // lcdi13: stock 1
    expect(fila(uno, 'lcd').cantidad).toBe(1)
    expect(fila(uno, 'lcd').controles).toMatchObject({ mas: false, menos: true, reutilizado: false })
    expect(filaActiva(fila(uno, 'lcd'))).toBe(true)
    // en el límite el clic ya no suma ni cuenta como cambio
    expect(reducir(uno, { tipo: 'SUMAR', prefijo: 'lcd' })).toBe(uno)
    // con margen, "+" sigue encendido
    const bat = fila(aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'bat' }), 'bat')
    expect(bat.controles).toMatchObject({ mas: true, menos: true, reutilizado: false })
    // cantidad > 0 y Reutilizado son excluyentes
    expect(reducir(uno, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'lcd', valor: true })).toBe(uno)
  })

  it('restar hasta cero rehabilita Reutilizado', () => {
    const dos = aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'SUMAR', prefijo: 'bat' })
    const uno = reducir(dos, { tipo: 'RESTAR', prefijo: 'bat' })
    expect(fila(uno, 'bat').cantidad).toBe(1)
    expect(fila(uno, 'bat').controles).toMatchObject({ mas: true, menos: true, reutilizado: false })
    const cero = reducir(uno, { tipo: 'RESTAR', prefijo: 'bat' })
    expect(fila(cero, 'bat').cantidad).toBe(0)
    expect(fila(cero, 'bat').controles).toMatchObject({ mas: true, menos: false, reutilizado: true })
    expect(reducir(cero, { tipo: 'RESTAR', prefijo: 'bat' })).toBe(cero)
    // restar desde el límite vuelve a encender "+"
    const lcd = aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'RESTAR', prefijo: 'lcd' })
    expect(fila(lcd, 'lcd').controles.mas).toBe(true)
  })

  it('marcar Reutilizado deshabilita + y -', () => {
    const e = aplicar(conModelo('13'), { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    const bat = fila(e, 'bat')
    expect(bat.reutilizado).toBe(true)
    expect(bat.cantidad).toBe(0)
    expect(bat.controles).toMatchObject({ mas: false, menos: false, reutilizado: true })
    expect(filaActiva(bat)).toBe(true)
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(e)
  })

  it('desmarcar Reutilizado habilita + aunque el stock sea 0 y el clic no suma', () => {
    const marcada = aplicar(conModelo('14'), { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true }) // bati14: stock 0
    const e = reducir(marcada, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: false })
    expect(fila(e, 'bat').reutilizado).toBe(false)
    expect(fila(e, 'bat').controles).toMatchObject({ mas: true, menos: false, reutilizado: true })
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(e)
    expect(fila(e, 'bat').cantidad).toBe(0)
  })

  it('cambiar de SKU pone a cero si la cantidad supera el nuevo stock', () => {
    // tres baterías del mismo modelo con stock 5, 1 y 2
    const catalogo = agrupados()
    catalogo.bat = [componente({ idCom: 101, tipo: 'bati13', stock: 5 }), componente({ idCom: 106, tipo: 'bati13alta', stock: 1 }),
      componente({ idCom: 107, tipo: 'bati13oem', stock: 2 })]
    const dos = aplicar(conModelo('13', { agrupados: catalogo }), { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'SUMAR', prefijo: 'bat' })
    // al de stock 2: la cantidad cabe, pero "+" se apaga por estar en el límite
    const justo = reducir(dos, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 107 })
    expect(fila(justo, 'bat')).toMatchObject({ idCom: 107, cantidad: 2 })
    expect(fila(justo, 'bat').controles).toMatchObject({ mas: false, menos: true, reutilizado: false })
    expect(stockDe(fila(justo, 'bat'))).toBe(2)
    // al de stock 1: la cantidad no cabe → 0 y Reutilizado vuelve
    const vacia = reducir(dos, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 106 })
    expect(fila(vacia, 'bat')).toMatchObject({ idCom: 106, cantidad: 0 })
    expect(fila(vacia, 'bat').controles).toMatchObject({ mas: true, menos: false, reutilizado: true })
    // con Reutilizado marcado, cambiar de SKU no enciende "+"
    const reutilizada = aplicar(conModelo('13', { agrupados: catalogo }),
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true }, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 107 })
    expect(fila(reutilizada, 'bat').controles.mas).toBe(false)
    // un SKU que no está entre las opciones, o el mismo, no cambia nada
    expect(reducir(dos, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 102 })).toBe(dos)
    expect(reducir(dos, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 })).toBe(dos)
  })

  it('poner observación recorta y vacío no cambia nada', () => {
    const e = aplicar(conModelo('13'), { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: '  conector dañado \n' })
    expect(fila(e, 'bat').observacion).toBe('conector dañado')
    expect(filaActiva(fila(e, 'bat'))).toBe(false) // la observación sola no activa la fila
    // "Guardar" con el texto vacío no borra la existente
    expect(reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: '   ' })).toBe(e)
    expect(reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: '' })).toBe(e)
  })

  it('borrar observación', () => {
    const con = aplicar(conModelo('13'), { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' })
    const sin = reducir(con, { tipo: 'BORRAR_OBSERVACION', prefijo: 'bat' })
    expect(fila(sin, 'bat').observacion).toBeNull()
    expect(reducir(sin, { tipo: 'BORRAR_OBSERVACION', prefijo: 'bat' })).toBe(sin)
  })

  it('cambiar modelo resetea las filas no guardadas', () => {
    const trabajado = aplicar(conModelo('13'),
      { tipo: 'SUMAR', prefijo: 'bat' },
      { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'lcd', valor: true })
    const e = reducir(trabajado, { tipo: 'CAMBIAR_MODELO', modelo: '14' })
    expect(e.modelo).toBe('14')
    expect(fila(e, 'bat')).toMatchObject({ idCom: 102, cantidad: 0, reutilizado: false, observacion: null })
    expect(fila(e, 'lcd')).toMatchObject({ idCom: 112, cantidad: 0, reutilizado: false })
    expect(fila(e, 'lcd').controles).toEqual({ mas: true, menos: false, reutilizado: true, sku: true, observacion: true })
    // vaciar el modelo vuelve a ocultar las filas
    const vacio = reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: null })
    expect(filasVisibles(vacio)).toBe(false)
    expect(fila(vacio, 'bat').opciones.map((c) => c.idCom)).toEqual([101, 102, 103])
    // elegir el mismo modelo no resetea nada
    expect(reducir(trabajado, { tipo: 'CAMBIAR_MODELO', modelo: '13' })).toBe(trabajado)
  })

  it('una fila guardada, con agotado confirmado, en vuelo o ya reparada no admite cambios', () => {
    const base = conModelo('13')
    const variantes: Partial<FilaEstado>[] = [
      { guardada: { idRep: 'R20260916_5', fecha: '16/09 09:15' } },
      { agotado: { descripcion: null, registrado: false } },
      { guardando: true },
      { rol: 'yaReparado' },
    ]
    for (const parcial of variantes) {
      const e: EstadoFormulario = { ...base, filas: base.filas.map((f) => (f.prefijo === 'bat' ? { ...f, ...parcial } : f)) }
      expect(reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(e)
      expect(reducir(e, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 })).toBe(e)
      expect(reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })).toBe(e)
      expect(reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'x' })).toBe(e)
      expect(reducir(e, { tipo: 'BORRAR_OBSERVACION', prefijo: 'bat' })).toBe(e)
    }
  })

  it('cada cambio sube revision', () => {
    let e = conModelo('13')
    expect(e.revision).toBe(1)
    const pasos: AccionFormulario[] = [
      { tipo: 'SUMAR', prefijo: 'bat' },
      { tipo: 'RESTAR', prefijo: 'bat' },
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true },
      { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
      { tipo: 'BORRAR_OBSERVACION', prefijo: 'bat' },
      { tipo: 'CAMBIAR_MODELO', modelo: '14' },
    ]
    for (const [i, paso] of pasos.entries()) {
      e = reducir(e, paso)
      expect(e.revision).toBe(i + 2)
    }
    expect(e.volcados).toBe(0)
    // lo que no cambia nada no reprograma el autoguardado; una acción de otra tarea tampoco
    expect(reducir(e, { tipo: 'RESTAR', prefijo: 'bat' })).toBe(e)
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'no-existe' })).toBe(e)
  })
})
```

```bash
npm test -- src/modules/taller/formulario/estado.test.ts
```
Expected: `Tests  7 failed | 13 passed (20)`. Fallan los que suman, restan, marcan "Reutilizado", cambian de SKU o ponen observación (p. ej. `sumar respeta el stock…` con `expected +0 to be 1`): el reductor todavía devuelve el mismo estado para esas acciones. Los nuevos que ya pasan son los que solo comprueban que algo **no** cambia.

- [x] **Step 4: Acciones de fila en el reductor**

En `estado.ts`, en la sección «Reductor», inserta este bloque **justo encima** del comentario `/** Reaplica el filtro a todas las filas y RESETEA…` (el de `cambiarModelo`):

```ts
/** Fila que no admite NINGÚN cambio: guardada, con agotado confirmado, ya reparada, sin SKU o con su guardado en vuelo. */
function bloqueada(fila: FilaEstado): boolean {
  return fila.guardada !== null || fila.agotado !== null || fila.rol === 'yaReparado' || fila.guardando || filaSinSku(fila)
}

/** Aplica `cambio` a la primera fila de ese prefijo. `cambio` devuelve null si la acción no procede (mismo estado).
 *  Todo cambio de fila anula el "✓ Confirmar" pendiente, apaga "✓ Recibido" si la fila pasa a activa y sube revision. */
function cambiarFila(estado: EstadoFormulario, prefijo: string, cambio: (fila: FilaEstado) => FilaEstado | null): EstadoFormulario {
  const indice = estado.filas.findIndex((f) => f.prefijo === prefijo)
  if (indice < 0) return estado
  const nueva = cambio(estado.filas[indice])
  if (nueva === null) return estado
  const fila: FilaEstado = {
    ...nueva,
    confirmandoGuardar: false,
    recibidoPendienteUso: nueva.recibidoPendienteUso && !filaActiva(nueva),
  }
  return { ...estado, filas: estado.filas.map((f, i) => (i === indice ? fila : f)), revision: estado.revision + 1 }
}

function sumar(fila: FilaEstado): FilaEstado | null {
  const c = componenteDe(fila)
  if (bloqueada(fila) || fila.solicitud !== null || c === null || !fila.controles.mas || fila.cantidad >= c.stock) return null
  const cantidad = fila.cantidad + 1
  return { ...fila, cantidad, controles: { ...fila.controles, mas: cantidad < c.stock, menos: true, reutilizado: false } }
}

function restar(fila: FilaEstado): FilaEstado | null {
  const c = componenteDe(fila)
  if (bloqueada(fila) || fila.solicitud !== null || c === null || !fila.controles.menos || fila.cantidad <= 0) return null
  const cantidad = fila.cantidad - 1
  return {
    ...fila,
    cantidad,
    controles: {
      ...fila.controles,
      mas: cantidad < c.stock,
      menos: cantidad > 0,
      reutilizado: cantidad === 0 ? true : fila.controles.reutilizado,
    },
  }
}

function cambiarSku(fila: FilaEstado, idCom: number): FilaEstado | null {
  const c = fila.opciones.find((o) => o.idCom === idCom)
  if (bloqueada(fila) || fila.solicitud !== null || !fila.controles.sku || c === undefined || idCom === fila.idCom) return null
  // En la fila en edición, volver al componente original ni pone a 0 ni toca "+".
  const esElOriginal = fila.original !== null && fila.original.idCom === idCom
  const seVacia = !esElOriginal && fila.cantidad > c.stock
  const cantidad = seVacia ? 0 : fila.cantidad
  const controles: ControlesFila = { ...fila.controles }
  if (seVacia) {
    controles.reutilizado = true
    controles.menos = false
  }
  if (!fila.reutilizado && !esElOriginal) controles.mas = cantidad < c.stock
  return { ...fila, idCom, cantidad, controles }
}

function marcarReutilizado(fila: FilaEstado, valor: boolean): FilaEstado | null {
  if (bloqueada(fila) || !fila.controles.reutilizado || valor === fila.reutilizado) return null
  if (valor) return { ...fila, reutilizado: true, controles: { ...fila.controles, mas: false, menos: false } }
  // Al desmarcar, "+" se enciende SIN mirar el stock (con stock 0 el clic no suma); en una fila con solicitud sigue apagado.
  return { ...fila, reutilizado: false, controles: { ...fila.controles, mas: fila.solicitud === null, menos: fila.cantidad > 0 } }
}

function ponerObservacion(fila: FilaEstado, texto: string): FilaEstado | null {
  const recortado = texto.trim()
  if (bloqueada(fila) || !fila.controles.observacion || recortado === '' || recortado === fila.observacion) return null
  return { ...fila, observacion: recortado }
}

function borrarObservacion(fila: FilaEstado): FilaEstado | null {
  if (bloqueada(fila) || !fila.controles.observacion || fila.observacion === null) return null
  return { ...fila, observacion: null }
}
```

Y en `reducir`, inserta estos `case` **entre** el de `'CAMBIAR_MODELO'` y `default:`:

```ts
    case 'SUMAR':
      return cambiarFila(estado, accion.prefijo, sumar)
    case 'RESTAR':
      return cambiarFila(estado, accion.prefijo, restar)
    case 'CAMBIAR_SKU':
      return cambiarFila(estado, accion.prefijo, (f) => cambiarSku(f, accion.idCom))
    case 'MARCAR_REUTILIZADO':
      return cambiarFila(estado, accion.prefijo, (f) => marcarReutilizado(f, accion.valor))
    case 'PONER_OBSERVACION':
      return cambiarFila(estado, accion.prefijo, (f) => ponerObservacion(f, accion.texto))
    case 'BORRAR_OBSERVACION':
      return cambiarFila(estado, accion.prefijo, borrarObservacion)
```

```bash
npm test -- src/modules/taller/formulario/estado.test.ts
```
Expected: `Tests  20 passed (20)`.

- [x] **Step 5: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/estado.ts src/modules/taller/formulario/estado.test.ts
git commit -m "feat(web): estado del formulario (1): tipos, estado inicial, filtro de modelo y filas"
```
Expected: lint, typecheck y tests en verde (con `noUnusedLocals`, el fichero no deja nada sin usar: los imports de tipos que faltan los añaden las Tasks 7 y 8 cuando los usan).

**Notas para el informe de la tarea:** `inicialEditar` queda en esta tarea como el estado base de edición (filas del tipo de trabajo, sin fila editada ni `edicion`); la Task 8 lo sustituye entero. `modeloTelefono` solo se acepta si está entre las opciones del combo (`modelos`).

---

### Task 7: Web — `formulario/estado.ts` (2): agotado local, solicitudes guardadas, fila guardada, otras acciones, dos clics y zona de guardar

**Files:**
- Modify: `src/modules/taller/formulario/estado.ts`
- Create: `src/modules/taller/formulario/estado.solicitudes.test.ts`

**Interfaces:**
- Consumes: todo lo de la Task 6 (`estadoBase`, `filaLimpia`, `controlesIniciales`, `cambiarFila`, `CONTROLES_APAGADOS`, selectores de fila); `MODELOS_ORDENADOS` (ya existente en `../lib/modelos`); W2 (`solicitudAsignacion`).
- Produces: `estadoInicial` con `solicitudes`; las acciones "Task 7" de W5 en `reducir`; los selectores "Task 7" de W6 (`zonaGuardarVisible` y `textoBotonGuardar` para nuevo/glass):

```ts
export type BotonDerecho =
  | { tipo: 'ninguno' }
  | { tipo: 'guardarFila'; texto: '✓ Guardar fila' | '✓ Confirmar'; deshabilitado: boolean }
  | { tipo: 'guardada'; texto: string }
  | { tipo: 'enCamino' }
  | { tipo: 'recibido' }
  | { tipo: 'yaReparado' }
export function botonDerecho(e: EstadoFormulario, fila: FilaEstado): BotonDerecho
export type SubFila =
  | { tipo: 'oculta' }
  | { tipo: 'sinStock' }
  | { tipo: 'limite'; stock: number }
  | { tipo: 'confirmada'; texto: string; lapizHabilitado: boolean }
export function subFila(e: EstadoFormulario, fila: FilaEstado): SubFila
export const TEXTO_SIN_STOCK: string
export const TEXTO_LIMITE: string
export function otrasAccionesVisible(e: EstadoFormulario): boolean
export function idComOtro(e: EstadoFormulario): number | null
export function contadorAcciones(e: EstadoFormulario): number
export function anadirAccionHabilitado(e: EstadoFormulario): boolean
export function zonaGuardarVisible(e: EstadoFormulario): boolean
export function textoBotonGuardar(e: EstadoFormulario): string
/** Añadido al esqueleto: true = el siguiente clic en el botón de la línea debe despachar PEDIR_CONFIRMACION_ACCION. */
export function accionPideConfirmacion(a: OtraAccion): boolean
```

**Ficha** (`docs/paridad/formulario.md`): "Modelo inicial con solicitudes cargadas…"; "\"✓ Guardar fila\"… aparece solo…"; "Dos clics: el primero cambia el texto a \"✓ Confirmar\"…"; "Fila guardada…" (estado); "En una fila guardada, la papelera…"; "Solo existe en flujo nuevo y Glass… Variante **sin stock**… Variante **límite**…"; "Confirmar es **local**…"; "Estado confirmado…" (texto de la etiqueta); "\"Guardar descripción\" actualiza… **no** reprograma el autoguardado"; "\"Cancelar solicitud\" sobre una solicitud local…"; las casillas de "Solicitudes ya guardadas (estados)" en lo que es estado; "Sección bajo las filas… Visible solo si…"; "Cabecera… badge"; "\"+ Añadir acción\"… deshabilitado mientras…"; "\"✓ Guardar\" en dos clics…" (estado); "Línea guardada…"; "Error al guardar la acción: botón rehabilitado con el texto todavía en \"✓ Confirmar\"…"; "Visible en flujo nuevo y Glass si…"; "Texto \"Terminar asignación\"… Primer clic…". Correcciones deliberadas que quedan cubiertas aquí: lápiz de una solicitud del servidor deshabilitado, papelera de la observación en fila guardada, "✓ Recibido" oculto al quedarse la fila sin SKU, y la marca `registrado` que usa la Task 8 para no repetir agotados.

**Lo que NO se porta de la referencia:** el diálogo `abrirSolicitud`, `solicitudNueva`, la marca de "solicitud cancelada" y el doble envío uso + solicitud.

- [x] **Step 1: Test de las solicitudes ya guardadas (falla)**

`src/modules/taller/formulario/estado.solicitudes.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, componente, solicitudAsignacion } from '../test/fabrica'
import {
  type AccionFormulario, type DatosNuevo, type EstadoFormulario, type FilaEstado, type OtraAccion,
  TEXTO_LIMITE, TEXTO_SIN_STOCK, accionPideConfirmacion, anadirAccionHabilitado, botonDerecho, contadorAcciones, estadoInicial,
  filaActiva, filasVisibles, idComOtro, otrasAccionesVisible, reducir, subFila, textoBotonGuardar, zonaGuardarVisible,
} from './estado'

const IMEI = '355400000000111'

function datosNuevo(parcial: Partial<DatosNuevo> = {}): DatosNuevo {
  return {
    modo: 'nuevo', idAsignacion: 'A20260916_1', imei: IMEI, agrupados: agrupados(), solicitudes: [], incidencia: null,
    modeloTelefono: null, ...parcial,
  }
}
function conModelo(modelo: string, parcial: Partial<DatosNuevo> = {}): EstadoFormulario {
  return reducir(estadoInicial(datosNuevo(parcial)), { tipo: 'CAMBIAR_MODELO', modelo })
}
function aplicar(estado: EstadoFormulario, ...acciones: AccionFormulario[]): EstadoFormulario {
  return acciones.reduce(reducir, estado)
}
function fila(estado: EstadoFormulario, prefijo: string): FilaEstado {
  const f = estado.filas.find((x) => x.prefijo === prefijo)
  if (!f) throw new Error(`no hay fila ${prefijo}`)
  return f
}
function accion(estado: EstadoFormulario, posicion: number): OtraAccion {
  const a = estado.otros[posicion - 1]
  if (!a) throw new Error(`no hay acción ${posicion}`)
  return a
}
/** Fila de batería activa (contador 1) con el primer clic de "✓ Guardar fila" ya dado. */
function batConfirmando(): EstadoFormulario {
  return aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' })
}
function batGuardada(): EstadoFormulario {
  return aplicar(batConfirmando(),
    { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' },
    { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' })
}
/** Una línea de acción escrita y, si se pide, guardada. */
function conAccion(texto: string, guardar = false): EstadoFormulario {
  const escrita = aplicar(conModelo('13'), { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto })
  if (!guardar) return escrita
  return aplicar(escrita,
    { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 },
    { tipo: 'INICIO_GUARDAR_ACCION', id: 1 },
    { tipo: 'ACCION_GUARDADA', id: 1, idRep: 'R20260916_10', fecha: '16/09 09:20' })
}

describe('estado del formulario (2): solicitudes ya guardadas', () => {
  it('solicitud pendiente bloquea la fila con Reutilizado y observación habilitados', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ descripcionSolicitud: ' batería hinchada ' })] }))
    const bat = fila(e, 'bat')
    expect(bat).toMatchObject({ idCom: 102, cantidad: 0, reutilizado: false, agotado: null, recibidoPendienteUso: false })
    expect(bat.solicitud).toEqual({ estado: 'pendiente', descripcion: 'batería hinchada' })
    expect(bat.controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: false, observacion: true })
    expect(botonDerecho(e, bat)).toEqual({ tipo: 'ninguno' })
    expect(subFila(e, bat)).toEqual({
      tipo: 'confirmada', texto: '✓  Solicitud de reposición pendiente — batería hinchada', lapizHabilitado: false,
    })
    expect(filaActiva(bat)).toBe(false)
    // ni contador ni SKU; la observación sí
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(e)
    expect(reducir(e, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 })).toBe(e)
    expect(fila(reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'avisado' }), 'bat').observacion).toBe('avisado')
    // el lápiz de una solicitud del servidor no edita ni cancela nada
    expect(reducir(e, { tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo: 'bat', descripcion: 'otra' })).toBe(e)
    expect(reducir(e, { tipo: 'CANCELAR_AGOTADO', prefijo: 'bat' })).toBe(e)
  })

  it('solicitud en camino deshabilita Reutilizado y muestra "⚠ En camino"', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ enCamino: true })] }))
    const bat = fila(e, 'bat')
    expect(bat.solicitud).toEqual({ estado: 'enCamino', descripcion: null })
    expect(bat.controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: true })
    expect(botonDerecho(e, bat)).toEqual({ tipo: 'enCamino' })
    expect(subFila(e, bat)).toEqual({ tipo: 'confirmada', texto: '✓  Solicitud de reposición pendiente', lapizHabilitado: false })
    expect(reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })).toBe(e)
  })

  it('gestionada con stock deja la fila normal con "✓ Recibido"', () => {
    // lcdi13 tiene stock 1; "recibido" se evalúa ANTES que enCamino
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ idCom: 111, estadoSolicitud: 'GESTIONADA', enCamino: true })] }))
    const lcd = fila(e, 'lcd')
    expect(lcd).toMatchObject({ idCom: 111, solicitud: null, agotado: null, recibidoPendienteUso: true })
    expect(lcd.controles).toEqual({ mas: true, menos: false, reutilizado: true, sku: true, observacion: true })
    expect(botonDerecho(e, lcd)).toEqual({ tipo: 'recibido' })
    expect(subFila(e, lcd)).toEqual({ tipo: 'oculta' })
    expect(e.modelo).toBe('13')
    expect(e.modeloBloqueado).toBe(false) // una recibida no bloquea el combo
    expect(zonaGuardarVisible(e)).toBe(false)
  })

  it('gestionada sin stock se trata como pendiente', () => {
    const pendiente = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ estadoSolicitud: 'GESTIONADA' })] })) // bati14: stock 0
    expect(fila(pendiente, 'bat').solicitud).toEqual({ estado: 'pendiente', descripcion: null })
    expect(fila(pendiente, 'bat').recibidoPendienteUso).toBe(false)
    const enCamino = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ estadoSolicitud: 'GESTIONADA', enCamino: true })] }))
    expect(fila(enCamino, 'bat').solicitud?.estado).toBe('enCamino')
  })

  it('rechazada solo preselecciona el SKU y deja el combo de modelo libre', () => {
    const catalogo = agrupados()
    catalogo.bat = [componente({ idCom: 108, tipo: 'bati14alta', stock: 3 }), ...catalogo.bat]
    const e = estadoInicial(datosNuevo({ agrupados: catalogo, solicitudes: [solicitudAsignacion({ estadoSolicitud: 'RECHAZADA' })] }))
    const bat = fila(e, 'bat')
    expect(e.modelo).toBe('14')
    expect(e.modeloBloqueado).toBe(false)
    expect(e.tieneSolicitudesIniciales).toBe(true)
    expect(bat.opciones.map((c) => c.idCom)).toEqual([108, 102])
    expect(bat.idCom).toBe(102) // por defecto habría salido 108, que tiene stock
    expect(bat.solicitud).toBeNull()
    expect(bat.controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: true, observacion: true })
    // ninguna marca de rechazo: la fila vuelve a ofrecer "Solicitar pieza"
    expect(subFila(e, bat)).toEqual({ tipo: 'sinStock' })
    expect(botonDerecho(e, bat)).toEqual({ tipo: 'ninguno' })
    expect(reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '13' }).modelo).toBe('13')
  })

  it('el modelo sale del SKU de la primera solicitud deducible y se bloquea solo con solicitud activa', () => {
    const e = estadoInicial(datosNuevo({
      modeloTelefono: '14', // no se usa: ya hay modelo por las solicitudes
      solicitudes: [solicitudAsignacion({ idCom: 9999 }), solicitudAsignacion({ idCom: 111 }), solicitudAsignacion({ idCom: 102 })],
    }))
    expect(e.modelo).toBe('13') // el de lcdi13; la 9999 no se deduce y la de bati14 llega después
    expect(e.modeloBloqueado).toBe(true)
    expect(reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '14' })).toBe(e)
    // la solicitud de otro modelo no se pierde: su SKU se añade a las opciones de su fila
    expect(fila(e, 'bat')).toMatchObject({ idCom: 102, solicitud: { estado: 'pendiente', descripcion: null } })
    expect(fila(e, 'bat').opciones.map((c) => c.idCom)).toEqual([101, 102])
  })

  it('las solicitudes se reaplican tras fijar el modelo', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ idCom: 111 })] }))
    expect(e.modelo).toBe('13')
    expect(fila(e, 'lcd').solicitud).toEqual({ estado: 'pendiente', descripcion: null }) // fijar el modelo no la borró
    expect(fila(e, 'lcd').opciones.map((c) => c.idCom)).toEqual([111])
    expect(fila(e, 'bat').opciones.map((c) => c.idCom)).toEqual([101]) // el resto, filtrado por el modelo
    expect(fila(e, 'bat').solicitud).toBeNull()
  })

  it('con solicitudes y sin modelo deducible las filas quedan visibles sin filtro', () => {
    const catalogo = agrupados()
    catalogo.bat = [...catalogo.bat, componente({ idCom: 109, tipo: 'batuniversal', stock: 0 })]
    const solicitudes = [solicitudAsignacion({ idCom: 109 })]
    const e = estadoInicial(datosNuevo({ agrupados: catalogo, solicitudes }))
    expect(e.modelo).toBeNull()
    expect(e.tieneSolicitudesIniciales).toBe(true)
    expect(filasVisibles(e)).toBe(true)
    expect(e.modeloBloqueado).toBe(true) // hay una solicitud activa
    expect(fila(e, 'bat')).toMatchObject({ idCom: 109, solicitud: { estado: 'pendiente', descripcion: null } })
    expect(fila(e, 'lcd').opciones.map((c) => c.idCom)).toEqual([111, 112]) // todos los activos
    // si el teléfono trae modelo, entra después y la solicitud sigue en su fila
    const conTelefono = estadoInicial(datosNuevo({ agrupados: catalogo, solicitudes, modeloTelefono: '13' }))
    expect(conTelefono.modelo).toBe('13')
    expect(conTelefono.modeloBloqueado).toBe(true)
    expect(fila(conTelefono, 'bat')).toMatchObject({ idCom: 109, solicitud: { estado: 'pendiente', descripcion: null } })
    expect(fila(conTelefono, 'lcd').opciones.map((c) => c.idCom)).toEqual([111])
  })

  it('"✓ Recibido" pasa a "✓ Guardar fila" al activar y no vuelve al desactivar', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ idCom: 111, estadoSolicitud: 'GESTIONADA' })] }))
    // un cambio que no activa la fila no apaga el indicador
    const conNota = reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'lcd', texto: 'llegó hoy' })
    expect(botonDerecho(conNota, fila(conNota, 'lcd'))).toEqual({ tipo: 'recibido' })
    const activa = reducir(e, { tipo: 'SUMAR', prefijo: 'lcd' })
    expect(botonDerecho(activa, fila(activa, 'lcd'))).toEqual({ tipo: 'guardarFila', texto: '✓ Guardar fila', deshabilitado: false })
    const inactiva = reducir(activa, { tipo: 'RESTAR', prefijo: 'lcd' })
    expect(botonDerecho(inactiva, fila(inactiva, 'lcd'))).toEqual({ tipo: 'ninguno' })
    expect(fila(inactiva, 'lcd').recibidoPendienteUso).toBe(false)
  })

  it('"✓ Recibido" se apaga al cambiar a un modelo sin SKU del tipo', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ idCom: 121, estadoSolicitud: 'GESTIONADA' })] })) // cami13
    expect(botonDerecho(e, fila(e, 'cam'))).toEqual({ tipo: 'recibido' })
    const sinSku = reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '14' }) // no hay cámara del 14
    expect(fila(sinSku, 'cam').recibidoPendienteUso).toBe(false)
    expect(botonDerecho(sinSku, fila(sinSku, 'cam'))).toEqual({ tipo: 'ninguno' })
    const deVuelta = reducir(sinSku, { tipo: 'CAMBIAR_MODELO', modelo: '13' })
    expect(botonDerecho(deVuelta, fila(deVuelta, 'cam'))).toEqual({ tipo: 'ninguno' }) // ya no vuelve
    // si el modelo nuevo sí tiene SKU del tipo, el indicador sobrevive al reseteo
    const lcd = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ idCom: 111, estadoSolicitud: 'GESTIONADA' })] }))
    const otroModelo = reducir(lcd, { tipo: 'CAMBIAR_MODELO', modelo: '14' })
    expect(botonDerecho(otroModelo, fila(otroModelo, 'lcd'))).toEqual({ tipo: 'recibido' })
  })

  it('marcar Reutilizado en fila con solicitud pendiente la activa', () => {
    const e = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion()] }))
    const marcada = reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    expect(fila(marcada, 'bat').reutilizado).toBe(true)
    expect(filaActiva(fila(marcada, 'bat'))).toBe(true)
    expect(zonaGuardarVisible(marcada)).toBe(true)
    expect(botonDerecho(marcada, fila(marcada, 'bat'))).toEqual({ tipo: 'ninguno' }) // nunca "✓ Guardar fila" con solicitud activa
    expect(reducir(marcada, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' })).toBe(marcada)
    // al desmarcar, "+" sigue apagado: la fila sigue siendo de solicitud
    const desmarcada = reducir(marcada, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: false })
    expect(fila(desmarcada, 'bat').controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: false, observacion: true })
    expect(zonaGuardarVisible(desmarcada)).toBe(false)
  })
})
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  11 failed | 20 passed (31)`: los 11 nuevos fallan (`estadoInicial` ignora `solicitudes`; `botonDerecho`, `subFila` y `zonaGuardarVisible` aún no existen) y los 20 de la Task 6 siguen en verde.

- [x] **Step 2: Solicitudes en el estado inicial y selectores de botón derecho, sub-fila, otras acciones y zona**

Tres cambios en `estado.ts`:

1. La línea de import de `../lib/modelos` pasa a traer también el catálogo:

```ts
import { MODELOS_ORDENADOS, extraerModelo, modelosDisponibles } from '../lib/modelos'
```

2. Sustituye la función `inicialNuevo` **entera** (desde `function inicialNuevo(datos: DatosNuevo): EstadoFormulario {` hasta su `}` de cierre; `inicialEditar` y `estadoInicial`, que van detrás, no se tocan) por este bloque, que trae sus funciones de apoyo delante:

```ts
// ───────────────────────────── Solicitudes ya guardadas ─────────────────────────────

const RECHAZADA = 'RECHAZADA'
const GESTIONADA = 'GESTIONADA'

function textoONull(texto: string | null | undefined): string | null {
  const recortado = (texto ?? '').trim()
  return recortado === '' ? null : recortado
}

/** Aplica una solicitud NO rechazada a su fila y selecciona su SKU (aunque el filtro de modelo lo hubiera dejado fuera).
 *  Orden de evaluación: recibido (GESTIONADA con stock) → en camino → pendiente (también GESTIONADA sin stock). */
function aplicarSolicitud(fila: FilaEstado, sol: SolicitudAsignacion): FilaEstado {
  const c = fila.skus.find((s) => s.idCom === sol.idCom)
  if (c === undefined) return fila
  const opciones = fila.opciones.some((o) => o.idCom === c.idCom) ? fila.opciones : [...fila.opciones, c]
  const normal: FilaEstado = {
    ...fila,
    opciones,
    idCom: c.idCom,
    cantidad: 0,
    reutilizado: false,
    solicitud: null,
    recibidoPendienteUso: false,
    controles: controlesIniciales(c),
  }
  if (sol.estadoSolicitud === GESTIONADA && c.stock > 0) return { ...normal, recibidoPendienteUso: true }
  return {
    ...normal,
    solicitud: { estado: sol.enCamino ? 'enCamino' : 'pendiente', descripcion: textoONull(sol.descripcionSolicitud) },
    // Con solicitud guardada "Añadir observación" sigue activo; "Reutilizado", solo si no está en camino.
    controles: { mas: false, menos: false, reutilizado: !sol.enCamino, sku: false, observacion: true },
  }
}

function aplicarSolicitudes(filas: FilaEstado[], solicitudes: SolicitudAsignacion[]): FilaEstado[] {
  return solicitudes.reduce(
    (acc, sol) => acc.map((f) => (f.skus.some((s) => s.idCom === sol.idCom) ? aplicarSolicitud(f, sol) : f)),
    filas,
  )
}

/** Una rechazada no deja marca: solo preselecciona su SKU si está entre las opciones del modelo. */
function preseleccionarRechazada(fila: FilaEstado, idCom: number): FilaEstado {
  const c = fila.opciones.find((o) => o.idCom === idCom)
  if (c === undefined || fila.solicitud !== null) return fila
  return { ...fila, idCom: c.idCom, controles: controlesIniciales(c) }
}

/** Primer modelo deducible del SKU de alguna solicitud (de cualquier estado, también rechazadas). */
function modeloDeSolicitudes(filas: FilaEstado[], solicitudes: SolicitudAsignacion[]): string | null {
  for (const sol of solicitudes) {
    for (const fila of filas) {
      const c = fila.skus.find((s) => s.idCom === sol.idCom)
      const modelo = c ? extraerModelo(c.tipo, fila.prefijo) : null
      if (modelo !== null) return modelo
    }
  }
  return null
}

/** El combo debe poder mostrar el modelo fijado aunque ningún SKU activo lo aporte: se inserta en su sitio del catálogo. */
function conModeloEnLista(modelos: string[], modelo: string | null): string[] {
  if (modelo === null || modelos.includes(modelo)) return modelos
  return MODELOS_ORDENADOS.filter((m) => m === modelo || modelos.includes(m))
}

/** Orden del modelo: el de las solicitudes → el del teléfono (que bloquea el combo). Fijar el modelo resetea las filas,
 *  así que las solicitudes se aplican DESPUÉS de fijarlo (las tres pasadas de la referencia: aplicar, fijar, reaplicar). */
function inicialNuevo(datos: DatosNuevo): EstadoFormulario {
  const base = estadoBase({
    modo: datos.modo,
    categoria: datos.idAsignacion.startsWith('AG') ? 'G' : 'R',
    idAsignacion: datos.idAsignacion,
    imei: datos.imei,
    incidencia: datos.incidencia,
    agrupados: datos.agrupados,
    glass: datos.modo === 'glass',
  })
  const vivas = datos.solicitudes.filter((s) => s.estadoSolicitud !== RECHAZADA)
  const rechazadas = datos.solicitudes.filter((s) => s.estadoSolicitud === RECHAZADA)
  const deSolicitud = modeloDeSolicitudes(base.filas, datos.solicitudes)
  const telefono =
    deSolicitud === null && datos.modeloTelefono !== null && base.modelos.includes(datos.modeloTelefono) ? datos.modeloTelefono : null
  const modelo = deSolicitud ?? telefono
  const filtradas = modelo === null ? base.filas : base.filas.map((f) => filaLimpia(f, modelo))
  const conSolicitudes = aplicarSolicitudes(filtradas, vivas)
  const filas = rechazadas.reduce((acc, sol) => acc.map((f) => preseleccionarRechazada(f, sol.idCom)), conSolicitudes)
  return {
    ...base,
    modelo,
    modelos: conModeloEnLista(base.modelos, modelo),
    // Solo bloquean el combo el modelo del teléfono y las solicitudes activas (pendiente o en camino).
    modeloBloqueado: telefono !== null || filas.some((f) => f.solicitud !== null),
    tieneSolicitudesIniciales: datos.solicitudes.length > 0,
    filas,
  }
}
```

3. Añade **al final** del fichero:

```ts
// ───────────────────────────── Selectores: botón derecho y sub-fila ─────────────────────────────

export type BotonDerecho =
  | { tipo: 'ninguno' }
  | { tipo: 'guardarFila'; texto: '✓ Guardar fila' | '✓ Confirmar'; deshabilitado: boolean }
  | { tipo: 'guardada'; texto: string } // '✓ Guardada dd/MM HH:mm'
  | { tipo: 'enCamino' } // '⚠ En camino'
  | { tipo: 'recibido' } // '✓ Recibido'
  | { tipo: 'yaReparado' } // '✓  Ya reparado'

export function botonDerecho(e: EstadoFormulario, fila: FilaEstado): BotonDerecho {
  if (fila.guardada !== null) return { tipo: 'guardada', texto: `✓ Guardada ${fila.guardada.fecha}` }
  // Una fila sin SKU para el modelo no enseña nada, tampoco un "✓ Recibido" anterior.
  if (filaSinSku(fila)) return { tipo: 'ninguno' }
  if (fila.solicitud !== null) return fila.solicitud.estado === 'enCamino' ? { tipo: 'enCamino' } : { tipo: 'ninguno' }
  if (fila.agotado !== null || e.modo === 'editar') return { tipo: 'ninguno' }
  if (filaActiva(fila)) {
    return { tipo: 'guardarFila', texto: fila.confirmandoGuardar ? '✓ Confirmar' : '✓ Guardar fila', deshabilitado: fila.guardando }
  }
  return fila.recibidoPendienteUso ? { tipo: 'recibido' } : { tipo: 'ninguno' }
}

export type SubFila =
  | { tipo: 'oculta' }
  | { tipo: 'sinStock' } // texto y botón "Solicitar pieza"
  | { tipo: 'limite'; stock: number } // texto y botón "Solicitar y descontar stock"
  | { tipo: 'confirmada'; texto: string; lapizHabilitado: boolean } // lapizHabilitado = solicitud local

export const TEXTO_SIN_STOCK = '⚠  Sin stock disponible. Solicita la pieza para que el admin gestione el pedido.'
export const TEXTO_LIMITE = '⚠  Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición.'
const TEXTO_SOLICITUD_PENDIENTE = '✓  Solicitud de reposición pendiente'

function conDescripcion(texto: string, descripcion: string | null): string {
  return descripcion === null ? texto : `${texto} — ${descripcion}`
}

/** Siempre 'oculta' en modo editar y en una fila guardada. En la variante límite y en su etiqueta confirmada, N es el
 *  STOCK del SKU, no el contador. El lápiz solo funciona sobre la solicitud local aún sin registrar. */
export function subFila(e: EstadoFormulario, fila: FilaEstado): SubFila {
  if (e.modo === 'editar' || fila.guardada !== null || filaSinSku(fila)) return { tipo: 'oculta' }
  if (fila.solicitud !== null) {
    return { tipo: 'confirmada', texto: conDescripcion(TEXTO_SOLICITUD_PENDIENTE, fila.solicitud.descripcion), lapizHabilitado: false }
  }
  const stock = stockDe(fila) ?? 0
  if (fila.agotado !== null) {
    const texto = stock > 0 ? `✓  ${stock} uds. se descontarán al guardar — solicitud pendiente` : TEXTO_SOLICITUD_PENDIENTE
    return { tipo: 'confirmada', texto: conDescripcion(texto, fila.agotado.descripcion), lapizHabilitado: !fila.agotado.registrado }
  }
  if (stock === 0) return { tipo: 'sinStock' }
  if (!fila.reutilizado && fila.cantidad >= stock) return { tipo: 'limite', stock }
  return { tipo: 'oculta' }
}

// ───────────────────────────── Selectores: otras acciones y zona de guardar ─────────────────────────────

/** Componente 'otro' cuyo SKU da el modelo elegido (no se mira 'activo'): el idCom de todas las acciones. */
export function idComOtro(e: EstadoFormulario): number | null {
  if (e.modelo === null) return null
  const c = e.componentesOtro.find((o) => extraerModelo(o.tipo, PREFIJO_OTRO) === e.modelo)
  return c ? c.idCom : null
}

export function otrasAccionesVisible(e: EstadoFormulario): boolean {
  return idComOtro(e) !== null
}

/** Badge: líneas guardadas, "✓ Ya reparada" o con texto. */
export function contadorAcciones(e: EstadoFormulario): number {
  return e.otros.filter((a) => a.guardada !== null || a.origen === 'yaReparada' || a.texto.trim() !== '').length
}

export function anadirAccionHabilitado(e: EstadoFormulario): boolean {
  return otrasAccionesVisible(e) && !e.otros.some((a) => a.guardada === null && a.origen !== 'yaReparada' && a.texto.trim() === '')
}

/** true = el siguiente clic en "✓ Guardar" / "✓ Confirmar" de la línea debe PEDIR confirmación (no guardar todavía). */
export function accionPideConfirmacion(a: OtraAccion): boolean {
  return !a.confirmando || a.pideOtroClic === true
}

/** Acciones nuevas con texto que se enviarán al guardar (solo cuentan si el modelo tiene componente 'otro'). */
function accionesPendientes(e: EstadoFormulario): OtraAccion[] {
  if (idComOtro(e) === null) return []
  return e.otros.filter((a) => a.origen === 'nueva' && a.guardada === null && a.texto.trim() !== '')
}

/** Se muestra u oculta la zona ENTERA. Una solicitud cargada del servidor no la muestra por sí sola. */
export function zonaGuardarVisible(e: EstadoFormulario): boolean {
  if (e.modo === 'editar') return false
  return (
    e.filas.some((f) => f.guardada !== null || filaActiva(f)) ||
    e.otros.some((a) => a.guardada !== null) ||
    accionesPendientes(e).length > 0
  )
}

export function textoBotonGuardar(e: EstadoFormulario): string {
  if (e.guardado.textoConfirmacion) return '✓  Confirmar terminar'
  return e.modo === 'editar' ? 'Guardar cambios' : 'Terminar asignación'
}
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  31 passed (31)`.

- [x] **Step 3: Test de la solicitud local y del guardado por fila (falla)**

Añade **al final** de `estado.solicitudes.test.ts`:

```ts
describe('estado del formulario (2): sub-fila de agotado y solicitud local', () => {
  it('subFila: sin stock, límite, oculta con Reutilizado, oculta en editar', () => {
    expect(TEXTO_SIN_STOCK).toBe('⚠  Sin stock disponible. Solicita la pieza para que el admin gestione el pedido.')
    expect(TEXTO_LIMITE).toBe('⚠  Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición.')
    // sin stock: se evalúa ya al pintar la fila por primera vez
    const catorce = conModelo('14')
    expect(subFila(catorce, fila(catorce, 'bat'))).toEqual({ tipo: 'sinStock' })
    // la variante sin stock no mira "Reutilizado"
    const reutilizadaSinStock = reducir(catorce, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    expect(subFila(reutilizadaSinStock, fila(reutilizadaSinStock, 'bat'))).toEqual({ tipo: 'sinStock' })
    // límite: contador = stock (lcdi13 tiene 1), con N = stock del SKU
    const trece = conModelo('13')
    expect(subFila(trece, fila(trece, 'lcd'))).toEqual({ tipo: 'oculta' })
    const enLimite = reducir(trece, { tipo: 'SUMAR', prefijo: 'lcd' })
    expect(subFila(enLimite, fila(enLimite, 'lcd'))).toEqual({ tipo: 'limite', stock: 1 })
    const conMargen = reducir(trece, { tipo: 'SUMAR', prefijo: 'bat' })
    expect(subFila(conMargen, fila(conMargen, 'bat'))).toEqual({ tipo: 'oculta' })
    // con "Reutilizado" y stock no hay sub-fila
    const reutilizada = reducir(trece, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'lcd', valor: true })
    expect(subFila(reutilizada, fila(reutilizada, 'lcd'))).toEqual({ tipo: 'oculta' })
    // fila sin SKU para el modelo
    expect(subFila(catorce, fila(catorce, 'cam'))).toEqual({ tipo: 'oculta' })
    // en modo editar no existe nunca, ni se puede confirmar
    const editando: EstadoFormulario = { ...catorce, modo: 'editar' }
    expect(subFila(editando, fila(editando, 'bat'))).toEqual({ tipo: 'oculta' })
    expect(reducir(editando, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' })).toBe(editando)
  })

  it('confirmar agotado bloquea la fila, borra la observación y conserva la cantidad en límite', () => {
    const enLimite = aplicar(conModelo('13'),
      { tipo: 'SUMAR', prefijo: 'lcd' },
      { tipo: 'PONER_OBSERVACION', prefijo: 'lcd', texto: 'pantalla con líneas' })
    const e = reducir(enLimite, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: '  negra original \n' })
    const lcd = fila(e, 'lcd')
    expect(lcd.agotado).toEqual({ descripcion: 'negra original', registrado: false })
    expect(lcd.cantidad).toBe(1) // = stock: lo que se descontará
    expect(lcd.observacion).toBeNull()
    expect(lcd.controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: false })
    expect(lcd.solicitud).toBeNull()
    expect(botonDerecho(e, lcd)).toEqual({ tipo: 'ninguno' }) // no ofrece "✓ Guardar fila"
    expect(filaActiva(lcd)).toBe(true)
    expect(e.revision).toBe(enLimite.revision + 1)
    for (const a of [
      { tipo: 'RESTAR', prefijo: 'lcd' }, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'lcd', valor: true },
      { tipo: 'PONER_OBSERVACION', prefijo: 'lcd', texto: 'x' }, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'lcd' },
      { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: 'otra vez' },
    ] satisfies AccionFormulario[]) expect(reducir(e, a)).toBe(e)
    // sin stock: contador 0 y descripción vacía = sin descripción
    const sinStock = reducir(conModelo('14'), { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '   ' })
    expect(fila(sinStock, 'bat')).toMatchObject({ cantidad: 0, agotado: { descripcion: null, registrado: false } })
    // una fila que no ofrece ninguna variante no se puede confirmar
    const normal = conModelo('13')
    expect(reducir(normal, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' })).toBe(normal)
  })

  it('texto de la etiqueta confirmada con stock 0, con stock > 0 y con descripción', () => {
    const sinStock = reducir(conModelo('14'), { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' })
    expect(subFila(sinStock, fila(sinStock, 'bat'))).toEqual({
      tipo: 'confirmada', texto: '✓  Solicitud de reposición pendiente', lapizHabilitado: true,
    })
    const limite = aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: '' })
    expect(subFila(limite, fila(limite, 'lcd'))).toEqual({
      tipo: 'confirmada', texto: '✓  1 uds. se descontarán al guardar — solicitud pendiente', lapizHabilitado: true,
    })
    const conTexto = reducir(conModelo('14'), { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: 'batería hinchada' })
    expect(subFila(conTexto, fila(conTexto, 'bat'))).toMatchObject({ texto: '✓  Solicitud de reposición pendiente — batería hinchada' })
    const limiteConTexto = aplicar(conModelo('13'),
      { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: 'negra original' })
    expect(subFila(limiteConTexto, fila(limiteConTexto, 'lcd'))).toMatchObject({
      texto: '✓  1 uds. se descontarán al guardar — solicitud pendiente — negra original',
    })
  })

  it('editar descripción no sube revision', () => {
    const confirmada = reducir(conModelo('14'), { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: 'batería hinchada' })
    const e = reducir(confirmada, { tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo: 'bat', descripcion: '  con tapa  ' })
    expect(fila(e, 'bat').agotado).toEqual({ descripcion: 'con tapa', registrado: false })
    expect(e.revision).toBe(confirmada.revision)
    expect(e.volcados).toBe(confirmada.volcados)
    const vaciada = reducir(e, { tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo: 'bat', descripcion: '' })
    expect(fila(vaciada, 'bat').agotado?.descripcion).toBeNull()
    // sobre una fila sin agotado local no hace nada
    expect(reducir(e, { tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo: 'lcd', descripcion: 'x' })).toBe(e)
    // un agotado ya registrado en el servidor no se edita ni se cancela: su lápiz queda deshabilitado
    const registrado = reducir(e, { tipo: 'AGOTADO_REGISTRADO', prefijo: 'bat' })
    expect(fila(registrado, 'bat').agotado).toEqual({ descripcion: 'con tapa', registrado: true })
    expect(registrado.revision).toBe(e.revision)
    expect(subFila(registrado, fila(registrado, 'bat'))).toMatchObject({ tipo: 'confirmada', lapizHabilitado: false })
    expect(reducir(registrado, { tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo: 'bat', descripcion: 'x' })).toBe(registrado)
    expect(reducir(registrado, { tipo: 'CANCELAR_AGOTADO', prefijo: 'bat' })).toBe(registrado)
  })

  it('cancelar agotado devuelve la fila y recalcula la variante', () => {
    const limite = aplicar(conModelo('13'), { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: 'x' })
    const e = reducir(limite, { tipo: 'CANCELAR_AGOTADO', prefijo: 'lcd' })
    expect(fila(e, 'lcd')).toMatchObject({ agotado: null, cantidad: 0, reutilizado: false })
    expect(fila(e, 'lcd').controles).toEqual({ mas: true, menos: false, reutilizado: true, sku: true, observacion: true })
    expect(subFila(e, fila(e, 'lcd'))).toEqual({ tipo: 'oculta' }) // con contador 0 ya no está en el límite
    expect(e.revision).toBe(limite.revision + 1)
    const sinStock = aplicar(conModelo('14'),
      { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' }, { tipo: 'CANCELAR_AGOTADO', prefijo: 'bat' })
    expect(fila(sinStock, 'bat').controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: true, observacion: true })
    expect(subFila(sinStock, fila(sinStock, 'bat'))).toEqual({ tipo: 'sinStock' })
    expect(zonaGuardarVisible(sinStock)).toBe(false)
  })
})

describe('estado del formulario (2): guardar fila', () => {
  it('botón derecho: reglas de visibilidad y "✓ Confirmar" revertido por cualquier cambio', () => {
    const base = conModelo('13')
    expect(botonDerecho(base, fila(base, 'bat'))).toEqual({ tipo: 'ninguno' })
    expect(reducir(base, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' })).toBe(base) // fila inactiva
    const activa = reducir(base, { tipo: 'SUMAR', prefijo: 'bat' })
    expect(botonDerecho(activa, fila(activa, 'bat'))).toEqual({ tipo: 'guardarFila', texto: '✓ Guardar fila', deshabilitado: false })
    const reutilizada = reducir(base, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cha', valor: true })
    expect(botonDerecho(reutilizada, fila(reutilizada, 'cha'))).toMatchObject({ tipo: 'guardarFila', texto: '✓ Guardar fila' })
    // convive con la sub-fila de stock agotado cuando cantidad = stock
    const enLimite = reducir(base, { tipo: 'SUMAR', prefijo: 'lcd' })
    expect(botonDerecho(enLimite, fila(enLimite, 'lcd'))).toMatchObject({ tipo: 'guardarFila' })
    expect(subFila(enLimite, fila(enLimite, 'lcd'))).toEqual({ tipo: 'limite', stock: 1 })
    // primer clic: "✓ Confirmar" (no es un cambio de datos); el segundo INICIO no procede sin el primero
    expect(reducir(activa, { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' })).toBe(activa)
    const confirmando = batConfirmando()
    expect(botonDerecho(confirmando, fila(confirmando, 'bat'))).toEqual({ tipo: 'guardarFila', texto: '✓ Confirmar', deshabilitado: false })
    expect(confirmando.revision).toBe(activa.revision)
    expect(reducir(confirmando, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' })).toBe(confirmando)
    // cualquier cambio en la fila lo devuelve a "✓ Guardar fila"
    const cambios: AccionFormulario[] = [
      { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
    ]
    for (const cambio of cambios) {
      const e = reducir(confirmando, cambio)
      expect(botonDerecho(e, fila(e, 'bat'))).toEqual({ tipo: 'guardarFila', texto: '✓ Guardar fila', deshabilitado: false })
    }
    // un cambio en OTRA fila no lo toca
    const otra = reducir(confirmando, { tipo: 'SUMAR', prefijo: 'cha' })
    expect(fila(otra, 'bat').confirmandoGuardar).toBe(true)
    // segundo clic: en vuelo, botón deshabilitado y la fila no admite cambios
    const enVuelo = reducir(confirmando, { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' })
    expect(botonDerecho(enVuelo, fila(enVuelo, 'bat'))).toEqual({ tipo: 'guardarFila', texto: '✓ Confirmar', deshabilitado: true })
    expect(reducir(enVuelo, { tipo: 'SUMAR', prefijo: 'bat' })).toBe(enVuelo)
    expect(reducir(enVuelo, { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' })).toBe(enVuelo)
    // en edición no existe nunca
    const editando: EstadoFormulario = { ...activa, modo: 'editar' }
    expect(botonDerecho(editando, fila(editando, 'bat'))).toEqual({ tipo: 'ninguno' })
    expect(reducir(editando, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' })).toBe(editando)
  })

  it('fila guardada ignora sumar, SKU, Reutilizado, observación y cambio de modelo', () => {
    const conNota = aplicar(conModelo('13'),
      { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
      { tipo: 'SUMAR', prefijo: 'cha' },
      { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' }, { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' },
      { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' })
    const bat = fila(conNota, 'bat')
    expect(bat.guardada).toEqual({ idRep: 'R20260916_9', fecha: '16/09 09:15' })
    expect(bat).toMatchObject({ guardando: false, confirmandoGuardar: false, cantidad: 1, observacion: 'conector dañado' })
    expect(bat.controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: false })
    expect(botonDerecho(conNota, bat)).toEqual({ tipo: 'guardada', texto: '✓ Guardada 16/09 09:15' })
    expect(subFila(conNota, bat)).toEqual({ tipo: 'oculta' })
    for (const a of [
      { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'RESTAR', prefijo: 'bat' }, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 },
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true }, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'x' },
      { tipo: 'BORRAR_OBSERVACION', prefijo: 'bat' }, // la papelera de una fila guardada está deshabilitada
      { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' }, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' },
      { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_99', fecha: '16/09 10:00' },
    ] satisfies AccionFormulario[]) expect(reducir(conNota, a)).toBe(conNota)
    // el cambio de modelo resetea el chasis pero no toca la guardada
    const otroModelo = reducir(conNota, { tipo: 'CAMBIAR_MODELO', modelo: '14' })
    expect(fila(otroModelo, 'bat')).toBe(bat)
    expect(fila(otroModelo, 'cha').cantidad).toBe(0)
    // una guardada con "Reutilizado": contador 0 y casilla marcada (y apagada)
    const reutilizada = aplicar(conModelo('13'),
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cha', valor: true },
      { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'cha' }, { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'cha' },
      { tipo: 'FILA_GUARDADA', prefijo: 'cha', idRep: 'R20260916_11', fecha: '16/09 09:30' })
    expect(fila(reutilizada, 'cha')).toMatchObject({ cantidad: 0, reutilizado: true, controles: { reutilizado: false } })
  })

  it('FALLO_GUARDAR_FILA rehabilita', () => {
    const enVuelo = reducir(batConfirmando(), { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' })
    const e = reducir(enVuelo, { tipo: 'FALLO_GUARDAR_FILA', prefijo: 'bat' })
    expect(fila(e, 'bat')).toMatchObject({ guardando: false, confirmandoGuardar: false, guardada: null, cantidad: 1 })
    expect(botonDerecho(e, fila(e, 'bat'))).toEqual({ tipo: 'guardarFila', texto: '✓ Guardar fila', deshabilitado: false })
    expect(fila(reducir(e, { tipo: 'SUMAR', prefijo: 'bat' }), 'bat').cantidad).toBe(2) // la fila no quedó bloqueada
    expect(e.volcados).toBe(0)
    expect(reducir(e, { tipo: 'FALLO_GUARDAR_FILA', prefijo: 'bat' })).toBe(e)
  })
})
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  6 failed | 33 passed (39)`: fallan los que confirman, editan o cancelan un agotado y los del guardado por fila (p. ej. `confirmar agotado…` con `expected null to deeply equal { descripcion: 'negra original', … }`); los nuevos que ya pasan solo leen selectores que existen desde el Step 2.

- [x] **Step 4: Reductor de "✓ Guardar fila" y del agotado local**

En `estado.ts`, inserta este bloque **justo encima** de `export function reducir(`:

```ts
// ───────────────────────────── Reductor: guardar fila y agotado local ─────────────────────────────

/** Como cambiarFila, pero para pasos que NO son cambios de datos (confirmaciones, envíos en vuelo): no toca revision. */
function sustituirFila(estado: EstadoFormulario, prefijo: string, cambio: (fila: FilaEstado) => FilaEstado | null): EstadoFormulario {
  const indice = estado.filas.findIndex((f) => f.prefijo === prefijo)
  if (indice < 0) return estado
  const nueva = cambio(estado.filas[indice])
  if (nueva === null) return estado
  return { ...estado, filas: estado.filas.map((f, i) => (i === indice ? nueva : f)) }
}

function pedirConfirmacionFila(estado: EstadoFormulario, prefijo: string): EstadoFormulario {
  return sustituirFila(estado, prefijo, (fila) => {
    const boton = botonDerecho(estado, fila)
    if (boton.tipo !== 'guardarFila' || boton.deshabilitado || fila.confirmandoGuardar) return null
    return { ...fila, confirmandoGuardar: true }
  })
}

function inicioGuardarFila(estado: EstadoFormulario, prefijo: string): EstadoFormulario {
  return sustituirFila(estado, prefijo, (fila) => (fila.confirmandoGuardar && !fila.guardando ? { ...fila, guardando: true } : null))
}

function filaGuardada(estado: EstadoFormulario, prefijo: string, guardada: Guardada): EstadoFormulario {
  const siguiente = sustituirFila(estado, prefijo, (fila) =>
    fila.guardada !== null
      ? null
      : { ...fila, guardada, guardando: false, confirmandoGuardar: false, recibidoPendienteUso: false, controles: CONTROLES_APAGADOS },
  )
  return siguiente === estado ? estado : { ...siguiente, volcados: estado.volcados + 1 }
}

function falloGuardarFila(estado: EstadoFormulario, prefijo: string): EstadoFormulario {
  return sustituirFila(estado, prefijo, (fila) => (fila.guardando ? { ...fila, guardando: false, confirmandoGuardar: false } : null))
}

/** Confirmar es LOCAL: bloquea la fila, borra su observación y deja el contador en lo que se enviará a agotar-componente
 *  (0 en "sin stock", el stock en "límite"). Solo procede si la sub-fila ofrece alguna de las dos variantes. */
function confirmarAgotado(estado: EstadoFormulario, prefijo: string, descripcion: string): EstadoFormulario {
  return cambiarFila(estado, prefijo, (fila) => {
    const variante = subFila(estado, fila)
    if (fila.guardando || (variante.tipo !== 'sinStock' && variante.tipo !== 'limite')) return null
    return {
      ...fila,
      cantidad: variante.tipo === 'limite' ? variante.stock : 0,
      observacion: null,
      agotado: { descripcion: textoONull(descripcion), registrado: false },
      controles: CONTROLES_APAGADOS,
    }
  })
}

/** Un agotado ya registrado en el servidor no se edita ni se cancela desde aquí. */
function editarDescripcionAgotado(estado: EstadoFormulario, prefijo: string, descripcion: string): EstadoFormulario {
  return sustituirFila(estado, prefijo, (fila) =>
    fila.agotado === null || fila.agotado.registrado ? null : { ...fila, agotado: { ...fila.agotado, descripcion: textoONull(descripcion) } },
  )
}

function cancelarAgotado(estado: EstadoFormulario, prefijo: string): EstadoFormulario {
  return cambiarFila(estado, prefijo, (fila) => {
    if (fila.agotado === null || fila.agotado.registrado) return null
    const stock = stockDe(fila) ?? 0
    return {
      ...fila,
      agotado: null,
      cantidad: 0,
      controles: { mas: !fila.reutilizado && stock > 0, menos: false, reutilizado: true, sku: true, observacion: true },
    }
  })
}

function agotadoRegistrado(estado: EstadoFormulario, prefijo: string): EstadoFormulario {
  return sustituirFila(estado, prefijo, (fila) =>
    fila.agotado === null || fila.agotado.registrado ? null : { ...fila, agotado: { ...fila.agotado, registrado: true } },
  )
}
```

Y en `reducir`, inserta estos `case` **justo encima** de `default:`:

```ts
    case 'PEDIR_CONFIRMACION_FILA':
      return pedirConfirmacionFila(estado, accion.prefijo)
    case 'INICIO_GUARDAR_FILA':
      return inicioGuardarFila(estado, accion.prefijo)
    case 'FILA_GUARDADA':
      return filaGuardada(estado, accion.prefijo, { idRep: accion.idRep, fecha: accion.fecha })
    case 'FALLO_GUARDAR_FILA':
      return falloGuardarFila(estado, accion.prefijo)
    case 'CONFIRMAR_AGOTADO':
      return confirmarAgotado(estado, accion.prefijo, accion.descripcion)
    case 'EDITAR_DESCRIPCION_AGOTADO':
      return editarDescripcionAgotado(estado, accion.prefijo, accion.descripcion)
    case 'CANCELAR_AGOTADO':
      return cancelarAgotado(estado, accion.prefijo)
    case 'AGOTADO_REGISTRADO':
      return agotadoRegistrado(estado, accion.prefijo)
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  39 passed (39)`.

- [x] **Step 5: Test de otras acciones y de la zona de guardar (falla)**

Añade **al final** de `estado.solicitudes.test.ts`:

```ts
describe('estado del formulario (2): otras acciones', () => {
  it('otras acciones visibles solo con componente otro del modelo', () => {
    const sinModelo = estadoInicial(datosNuevo())
    expect(otrasAccionesVisible(sinModelo)).toBe(false)
    expect(idComOtro(sinModelo)).toBeNull()
    expect(idComOtro(conModelo('13'))).toBe(161)
    expect(idComOtro(conModelo('14'))).toBe(162)
    expect(otrasAccionesVisible(conModelo('13promax'))).toBe(false) // no hay otroi13promax
    // no se mira si el componente "otro" está activo
    const catalogo = agrupados()
    catalogo.otro = [componente({ idCom: 161, tipo: 'otroi13', stock: 0, stockMinimo: 0, activo: false })]
    expect(idComOtro(conModelo('13', { agrupados: catalogo }))).toBe(161)
    // igual en glass
    expect(idComOtro(conModelo('13', { modo: 'glass', idAsignacion: 'AG20260916_2' }))).toBe(161)
  })

  it('añadir acción deshabilitado con una línea vacía', () => {
    const base = conModelo('13')
    expect(anadirAccionHabilitado(base)).toBe(true)
    const conLinea = reducir(base, { tipo: 'ANADIR_ACCION' })
    expect(conLinea.otros).toEqual([{ id: 1, texto: '', origen: 'nueva', guardada: null, confirmando: false, guardando: false }])
    expect(conLinea.siguienteIdAccion).toBe(2)
    expect(conLinea.revision).toBe(base.revision + 1)
    expect(anadirAccionHabilitado(conLinea)).toBe(false)
    expect(reducir(conLinea, { tipo: 'ANADIR_ACCION' })).toBe(conLinea)
    const soloEspacios = reducir(conLinea, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: '   ' })
    expect(anadirAccionHabilitado(soloEspacios)).toBe(false)
    const escrita = reducir(conLinea, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'Limpieza de altavoz' })
    expect(anadirAccionHabilitado(escrita)).toBe(true)
    const dos = reducir(escrita, { tipo: 'ANADIR_ACCION' })
    expect(dos.otros.map((a) => a.id)).toEqual([1, 2])
    // la papelera quita la línea sin más; los ids no se reutilizan
    const quitada = reducir(dos, { tipo: 'QUITAR_ACCION', id: 1 })
    expect(quitada.otros.map((a) => a.id)).toEqual([2])
    expect(reducir(quitada, { tipo: 'ANADIR_ACCION' })).toBe(quitada) // la 2 sigue vacía
    // sin sección (sin modelo) no se añade nada
    const sinModelo = estadoInicial(datosNuevo())
    expect(reducir(sinModelo, { tipo: 'ANADIR_ACCION' })).toBe(sinModelo)
  })

  it('badge cuenta guardadas o con texto', () => {
    expect(contadorAcciones(conModelo('13'))).toBe(0)
    expect(contadorAcciones(reducir(conModelo('13'), { tipo: 'ANADIR_ACCION' }))).toBe(0)
    const guardadaYEscrita = aplicar(conAccion('Limpieza de altavoz', true),
      { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 2, texto: 'Ajuste de botón' }, { tipo: 'ANADIR_ACCION' })
    expect(guardadaYEscrita.otros).toHaveLength(3)
    expect(contadorAcciones(guardadaYEscrita)).toBe(2)
  })

  it('escribir devuelve "✓ Confirmar" a "✓ Guardar"', () => {
    const escrita = conAccion('Limpieza')
    expect(accionPideConfirmacion(accion(escrita, 1))).toBe(true)
    expect(reducir(escrita, { tipo: 'INICIO_GUARDAR_ACCION', id: 1 })).toBe(escrita) // sin primer clic no se guarda
    const confirmando = reducir(escrita, { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 })
    expect(accion(confirmando, 1).confirmando).toBe(true)
    expect(accionPideConfirmacion(accion(confirmando, 1))).toBe(false)
    expect(confirmando.revision).toBe(escrita.revision)
    const reescrita = reducir(confirmando, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'Limpieza de altavoz' })
    expect(accion(reescrita, 1)).toMatchObject({ texto: 'Limpieza de altavoz', confirmando: false })
    expect(reescrita.revision).toBe(confirmando.revision + 1)
    // con el texto vacío no se puede pedir confirmación ("✓ Guardar" deshabilitado)
    const vacia = reducir(conModelo('13'), { tipo: 'ANADIR_ACCION' })
    expect(reducir(vacia, { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 })).toBe(vacia)
    // guardada: texto recortado, bloqueada y sin papelera
    const guardada = conAccion('  Limpieza de altavoz  ', true)
    expect(accion(guardada, 1)).toMatchObject({
      texto: 'Limpieza de altavoz', guardada: { idRep: 'R20260916_10', fecha: '16/09 09:20' }, confirmando: false, guardando: false,
    })
    expect(reducir(guardada, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'otra' })).toBe(guardada)
    expect(reducir(guardada, { tipo: 'QUITAR_ACCION', id: 1 })).toBe(guardada)
    // en edición las líneas no tienen "✓ Guardar"
    const editando: EstadoFormulario = { ...escrita, modo: 'editar' }
    expect(reducir(editando, { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 })).toBe(editando)
  })

  it('fallo al guardar acción conserva "✓ Confirmar" pero pide otro clic', () => {
    const enVuelo = aplicar(conAccion('Limpieza de altavoz'),
      { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 }, { tipo: 'INICIO_GUARDAR_ACCION', id: 1 })
    expect(accion(enVuelo, 1).guardando).toBe(true)
    expect(reducir(enVuelo, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'x' })).toBe(enVuelo)
    expect(reducir(enVuelo, { tipo: 'QUITAR_ACCION', id: 1 })).toBe(enVuelo)
    const fallida = reducir(enVuelo, { tipo: 'FALLO_GUARDAR_ACCION', id: 1 })
    expect(accion(fallida, 1)).toMatchObject({ guardando: false, confirmando: true, guardada: null }) // el texto sigue en "✓ Confirmar"
    expect(accionPideConfirmacion(accion(fallida, 1))).toBe(true)
    expect(reducir(fallida, { tipo: 'INICIO_GUARDAR_ACCION', id: 1 })).toBe(fallida) // un clic no basta
    const rearmada = reducir(fallida, { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 })
    expect(accionPideConfirmacion(accion(rearmada, 1))).toBe(false)
    expect(accion(reducir(rearmada, { tipo: 'INICIO_GUARDAR_ACCION', id: 1 }), 1).guardando).toBe(true)
    expect(fallida.volcados).toBe(0)
  })
})

describe('estado del formulario (2): zona de guardar', () => {
  it('zona visible: fila activa, agotado local, acción con texto, fila guardada, acción guardada', () => {
    expect(zonaGuardarVisible(estadoInicial(datosNuevo()))).toBe(false)
    expect(zonaGuardarVisible(conModelo('13'))).toBe(false)
    expect(zonaGuardarVisible(reducir(conModelo('13'), { tipo: 'SUMAR', prefijo: 'bat' }))).toBe(true)
    expect(zonaGuardarVisible(reducir(conModelo('13'), { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true }))).toBe(true)
    expect(zonaGuardarVisible(reducir(conModelo('14'), { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '' }))).toBe(true)
    expect(zonaGuardarVisible(reducir(conModelo('13'), { tipo: 'ANADIR_ACCION' }))).toBe(false) // línea vacía
    expect(zonaGuardarVisible(conAccion('Limpieza de altavoz'))).toBe(true)
    expect(zonaGuardarVisible(conAccion('Limpieza de altavoz', true))).toBe(true)
    expect(zonaGuardarVisible(batGuardada())).toBe(true)
    // una observación sola no cuenta
    expect(zonaGuardarVisible(reducir(conModelo('13'), { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'x' }))).toBe(false)
    // una acción escrita deja de contar si el modelo nuevo no tiene componente "otro"
    expect(zonaGuardarVisible(reducir(conAccion('Limpieza de altavoz'), { tipo: 'CAMBIAR_MODELO', modelo: '13promax' }))).toBe(false)
    expect(textoBotonGuardar(conModelo('13'))).toBe('Terminar asignación')
  })

  it('una solicitud del servidor no hace visible la zona', () => {
    const pendiente = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion()] }))
    expect(zonaGuardarVisible(pendiente)).toBe(false)
    const enCamino = estadoInicial(datosNuevo({ solicitudes: [solicitudAsignacion({ enCamino: true })] }))
    expect(zonaGuardarVisible(enCamino)).toBe(false)
    expect(reducir(pendiente, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' })).toBe(pendiente) // sin zona no hay clic
  })

  it('"✓  Confirmar terminar" no vuelve atrás y tras FALLO_GUARDADO pide dos clics', () => {
    const activa = reducir(conModelo('13'), { tipo: 'SUMAR', prefijo: 'bat' })
    expect(reducir(activa, { tipo: 'INICIO_GUARDADO' })).toBe(activa) // el primer clic nunca ejecuta
    const primerClic = reducir(activa, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' })
    expect(primerClic.guardado).toEqual({ clics: 1, textoConfirmacion: true, enCurso: false })
    expect(textoBotonGuardar(primerClic)).toBe('✓  Confirmar terminar')
    expect(primerClic.revision).toBe(activa.revision)
    // cambiar filas no restaura el texto ni desarma el segundo clic
    const cambiada = reducir(primerClic, { tipo: 'SUMAR', prefijo: 'bat' })
    expect(textoBotonGuardar(cambiada)).toBe('✓  Confirmar terminar')
    expect(cambiada.guardado.clics).toBe(1)
    const enCurso = reducir(cambiada, { tipo: 'INICIO_GUARDADO' })
    expect(enCurso.guardado).toEqual({ clics: 1, textoConfirmacion: true, enCurso: true })
    expect(reducir(enCurso, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' })).toBe(enCurso)
    expect(reducir(enCurso, { tipo: 'INICIO_GUARDADO' })).toBe(enCurso) // no se lanza dos veces
    // fallo: el texto se queda, pero hacen falta otros dos clics
    const fallido = reducir(enCurso, { tipo: 'FALLO_GUARDADO' })
    expect(fallido.guardado).toEqual({ clics: 0, textoConfirmacion: true, enCurso: false })
    expect(textoBotonGuardar(fallido)).toBe('✓  Confirmar terminar')
    expect(reducir(fallido, { tipo: 'INICIO_GUARDADO' })).toBe(fallido)
    const otraVez = aplicar(fallido, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' }, { tipo: 'INICIO_GUARDADO' })
    expect(otraVez.guardado.enCurso).toBe(true)
    expect(otraVez.borradorDescartado).toBe(false)
    // guardado real: ya no se vuelve a escribir borrador
    expect(reducir(otraVez, { tipo: 'GUARDADO_COMPLETADO' }).borradorDescartado).toBe(true)
  })

  it('FILA_GUARDADA y ACCION_GUARDADA suben volcados', () => {
    const antes = reducir(batConfirmando(), { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' })
    const fila1 = reducir(antes, { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' })
    expect(fila1.volcados).toBe(antes.volcados + 1)
    expect(fila1.revision).toBe(antes.revision)
    const accion1 = aplicar(fila1,
      { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'Limpieza de altavoz' },
      { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 }, { tipo: 'INICIO_GUARDAR_ACCION', id: 1 },
      { tipo: 'ACCION_GUARDADA', id: 1, idRep: 'R20260916_10', fecha: '16/09 09:20' })
    expect(accion1.volcados).toBe(fila1.volcados + 1)
    // repetir el aviso de guardado no vuelve a volcar
    expect(reducir(accion1, { tipo: 'ACCION_GUARDADA', id: 1, idRep: 'R20260916_10', fecha: '16/09 09:20' })).toBe(accion1)
  })
})
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  7 failed | 41 passed (48)`: fallan los que añaden, escriben o guardan acciones y los de los dos clics de la zona (p. ej. `añadir acción deshabilitado…` con `expected [] to deeply equal [ { id: 1, … } ]`).

- [x] **Step 6: Reductor de otras acciones y de la zona de guardar**

En `estado.ts`, inserta este bloque **justo encima** de `export function reducir(` (queda detrás del bloque del Step 4):

```ts
// ───────────────────────────── Reductor: otras acciones ─────────────────────────────

/** `cuenta` = es un cambio de datos (sube revision). */
function cambiarAccion(estado: EstadoFormulario, id: number, cuenta: boolean, cambio: (accion: OtraAccion) => OtraAccion | null): EstadoFormulario {
  const actual = estado.otros.find((a) => a.id === id)
  const nueva = actual ? cambio(actual) : null
  if (nueva === null) return estado
  return { ...estado, otros: estado.otros.map((a) => (a.id === id ? nueva : a)), revision: estado.revision + (cuenta ? 1 : 0) }
}

/** Línea que el usuario puede tocar: ni guardada, ni "✓ Ya reparada", ni con su guardado en vuelo. */
function accionEditable(a: OtraAccion): boolean {
  return a.guardada === null && a.origen !== 'yaReparada' && !a.guardando
}

function anadirAccion(estado: EstadoFormulario): EstadoFormulario {
  if (!anadirAccionHabilitado(estado)) return estado
  const linea: OtraAccion = { id: estado.siguienteIdAccion, texto: '', origen: 'nueva', guardada: null, confirmando: false, guardando: false }
  return { ...estado, otros: [...estado.otros, linea], siguienteIdAccion: estado.siguienteIdAccion + 1, revision: estado.revision + 1 }
}

function quitarAccion(estado: EstadoFormulario, id: number): EstadoFormulario {
  const linea = estado.otros.find((a) => a.id === id)
  if (linea === undefined || linea.origen !== 'nueva' || !accionEditable(linea)) return estado
  return { ...estado, otros: estado.otros.filter((a) => a.id !== id), revision: estado.revision + 1 }
}

function pedirConfirmacionAccion(estado: EstadoFormulario, id: number): EstadoFormulario {
  if (estado.modo === 'editar' || idComOtro(estado) === null) return estado
  return cambiarAccion(estado, id, false, (a) => {
    if (a.origen !== 'nueva' || !accionEditable(a) || a.texto.trim() === '' || !accionPideConfirmacion(a)) return null
    return { ...a, confirmando: true, pideOtroClic: false }
  })
}

// ───────────────────────────── Reductor: zona de guardar ─────────────────────────────

function pedirConfirmacionGuardar(estado: EstadoFormulario): EstadoFormulario {
  if (!zonaGuardarVisible(estado) || estado.guardado.enCurso || estado.guardado.clics === 1) return estado
  return { ...estado, guardado: { ...estado.guardado, clics: 1, textoConfirmacion: true } }
}
```

Y en `reducir`, inserta estos `case` **justo encima** de `default:` (detrás de los del Step 4):

```ts
    case 'ANADIR_ACCION':
      return anadirAccion(estado)
    case 'ESCRIBIR_ACCION':
      return cambiarAccion(estado, accion.id, true, (a) =>
        !accionEditable(a) || a.texto === accion.texto ? null : { ...a, texto: accion.texto, confirmando: false, pideOtroClic: false },
      )
    case 'QUITAR_ACCION':
      return quitarAccion(estado, accion.id)
    case 'PEDIR_CONFIRMACION_ACCION':
      return pedirConfirmacionAccion(estado, accion.id)
    case 'INICIO_GUARDAR_ACCION':
      return cambiarAccion(estado, accion.id, false, (a) =>
        a.confirmando && !accionPideConfirmacion(a) && accionEditable(a) ? { ...a, guardando: true } : null,
      )
    case 'ACCION_GUARDADA': {
      const guardada: Guardada = { idRep: accion.idRep, fecha: accion.fecha }
      const siguiente = cambiarAccion(estado, accion.id, false, (a) =>
        a.guardada !== null ? null : { ...a, texto: a.texto.trim(), guardada, confirmando: false, guardando: false, pideOtroClic: false },
      )
      return siguiente === estado ? estado : { ...siguiente, volcados: estado.volcados + 1 }
    }
    case 'FALLO_GUARDAR_ACCION':
      // El texto sigue en "✓ Confirmar" (confirmando = true), pero el siguiente clic vuelve a pedir confirmación.
      return cambiarAccion(estado, accion.id, false, (a) =>
        a.guardando ? { ...a, guardando: false, confirmando: true, pideOtroClic: true } : null,
      )
    case 'PEDIR_CONFIRMACION_GUARDAR':
      return pedirConfirmacionGuardar(estado)
    case 'INICIO_GUARDADO':
      return estado.guardado.clics === 1 && !estado.guardado.enCurso ? { ...estado, guardado: { ...estado.guardado, enCurso: true } } : estado
    case 'FALLO_GUARDADO':
      // El texto "✓  Confirmar terminar" no vuelve atrás, pero hacen falta otros dos clics.
      return estado.guardado.enCurso ? { ...estado, guardado: { ...estado.guardado, clics: 0, enCurso: false } } : estado
    case 'GUARDADO_COMPLETADO':
      return { ...estado, borradorDescartado: true }
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  48 passed (48)`.

- [x] **Step 7: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/estado.ts src/modules/taller/formulario/estado.solicitudes.test.ts
git commit -m "feat(web): estado del formulario (2): solicitudes, agotado local, filas guardadas, otras acciones y zona de guardar"
```
Expected: lint, typecheck y tests en verde.

**Notas para el informe de la tarea:**
- Las "tres pasadas" de la referencia (aplicar solicitudes, fijar el modelo, reaplicarlas) son en una función pura "decidir el modelo y aplicar después": el resultado es el mismo y es lo que comprueba `las solicitudes se reaplican tras fijar el modelo`.
- Si el SKU de una solicitud activa no pertenece al modelo fijado, se añade a las opciones de su fila para que la solicitud no se pierda; si el modelo fijado no está entre los del combo, se inserta en su posición del catálogo.
- El texto de la sub-fila de una solicitud **del servidor** es siempre "✓  Solicitud de reposición pendiente" (+ descripción), como dice la ficha; el de la solicitud **local** depende del stock del SKU.
- Precisiones sobre la ficha que conviene revisar: la sub-fila se oculta en una fila guardada; un agotado local ya `registrado` no se puede editar ni cancelar (su lápiz sale deshabilitado); al confirmar, el contador queda en 0 en "sin stock" y en el stock en "límite" aunque un borrador hubiera restaurado otra cantidad; `GUARDADO_COMPLETADO` deja `enCurso` como está para que el botón no se rearme mientras se cierra.

---

### Task 8: Web — `formulario/estado.ts` (3): modo edición y cuerpos de las llamadas

**Files:**
- Modify: `src/modules/taller/formulario/estado.ts`
- Create: `src/modules/taller/formulario/estado.edicion.test.ts`

**Interfaces:**
- Consumes: Tasks 6 y 7 (`estadoBase`, `filaLimpia`, `textoONull`, `conModeloEnLista`, `accionesPendientes`, `idComOtro`, `CONTROLES_APAGADOS`…); W1 (`AgotarRequest`, `EditarReparacionRequest`, `FilaReparacion`, `GuardarFilaRequest`, `InsertarCompletaRequest`); W2 (`detalleEdicion`).
- Produces: `estadoInicial` para `DatosEditar`; la rama `editar` de `zonaGuardarVisible`, `textoBotonGuardar`, `botonDerecho` (`yaReparado`), `etiquetaImei` y `tituloPestana`; y los selectores y constructores "Task 8" de W6:

```ts
export function hayCambioEnFilaEditada(e: EstadoFormulario): boolean
export function filaEditadaInvalida(e: EstadoFormulario): boolean
export function accionEditadaInvalida(e: EstadoFormulario): boolean
export function hayCambiosSinGuardar(e: EstadoFormulario): boolean
export type PrevisionStock = { texto: string; tendencia: 'baja' | 'sube' | 'igual' }
export function previsionStock(e: EstadoFormulario, fila: FilaEstado): PrevisionStock | null
export function filaDeCuerpo(fila: FilaEstado): FilaReparacion
export function filaDeAccion(idCom: number, texto: string): FilaReparacion
export function cuerpoGuardarFila(e: EstadoFormulario, prefijo: string, idTecSesion: number): GuardarFilaRequest
export function cuerpoGuardarAccion(e: EstadoFormulario, id: number, idTecSesion: number): GuardarFilaRequest | null
export type PlanTerminar = { agotados: { prefijo: string; cuerpo: AgotarRequest }[]; completa: InsertarCompletaRequest | null }
export function planTerminar(e: EstadoFormulario, idTecSesion: number): PlanTerminar
export type PlanGuardarCambios = {
  editarAccion: EditarReparacionRequest | null
  editarFila: EditarReparacionRequest | null
  completaFilas: InsertarCompletaRequest | null
  completaAcciones: InsertarCompletaRequest | null
}
export function planGuardarCambios(e: EstadoFormulario): PlanGuardarCambios
```

**Ficha** (`docs/paridad/formulario.md`): "Etiqueta IMEI…" (edición); "En edición: el modelo es el del SKU de la fila editada…"; todas las de "Modo edición" en lo que es estado y cuerpos ("Fila editada…", "Previsualización de stock…", "Hay cambio si varía…", "Filas de otros tipos… ya reparado…", "Resto de filas…", "Esas filas nuevas no tienen sub-fila…", "Acciones en edición…", "Editar una acción \"otro\"…", "Zona de guardar visible si…", "\"Guardar cambios\", en orden…", "Las filas y acciones nuevas… **conservan el técnico original**…", "Un cambio inválido oculta la zona…"); "Guardar: … con una fila `{idCom, cantidad, …}`" (cuerpo); "Terminar, paso 1… **no los reenvía**"; "Paso 2…"; "Paso 3…"; "Paso 4…"; "Los campos sin valor…" (viajan como `null`, decisión 1 del plan); "En flujo nuevo no se envía `categoria`…"; "(En la fila en edición, con el mismo componente original, ni se pone a 0…)". Correcciones deliberadas que quedan cubiertas aquí: sin sub-fila de agotado en edición y reintento de "Terminar asignación" sin repetir agotados.

- [x] **Step 1: Test del modo edición (falla)**

`src/modules/taller/formulario/estado.edicion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { agrupados, componente, detalleEdicion, solicitudAsignacion } from '../test/fabrica'
import {
  type AccionFormulario, type DatosEditar, type DatosNuevo, type EstadoFormulario, type FilaEstado,
  accionEditadaInvalida, anadirAccionHabilitado, botonDerecho, contadorAcciones, cuerpoGuardarAccion, cuerpoGuardarFila, estadoInicial,
  etiquetaImei, filaDeAccion, filaDeCuerpo, filaEditadaInvalida, filasVisibles, hayCambioEnFilaEditada, hayCambiosSinGuardar,
  planGuardarCambios, planTerminar, previsionStock, reducir, subFila, textoBotonGuardar, tituloPestana, zonaGuardarVisible,
} from './estado'

const IMEI = '355400000000111'

function datosNuevo(parcial: Partial<DatosNuevo> = {}): DatosNuevo {
  return {
    modo: 'nuevo', idAsignacion: 'A20260916_1', imei: IMEI, agrupados: agrupados(), solicitudes: [], incidencia: null,
    modeloTelefono: null, ...parcial,
  }
}
function datosEditar(parcial: Partial<DatosEditar> = {}): DatosEditar {
  return {
    modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [], accionesYaReparadas: [],
    ...parcial,
  }
}
/** Edición de una acción "otro" (otroi13) con su texto original. */
function datosEditarAccion(parcial: Partial<DatosEditar> = {}): DatosEditar {
  return datosEditar({ detalle: detalleEdicion({ idCom: 161, cantidad: 0, observacion: 'Limpieza de altavoz' }), ...parcial })
}
function conModelo(modelo: string, parcial: Partial<DatosNuevo> = {}): EstadoFormulario {
  return reducir(estadoInicial(datosNuevo(parcial)), { tipo: 'CAMBIAR_MODELO', modelo })
}
function aplicar(estado: EstadoFormulario, ...acciones: AccionFormulario[]): EstadoFormulario {
  return acciones.reduce(reducir, estado)
}
function fila(estado: EstadoFormulario, prefijo: string): FilaEstado {
  const f = estado.filas.find((x) => x.prefijo === prefijo)
  if (!f) throw new Error(`no hay fila ${prefijo}`)
  return f
}
const FILA_VACIA = { esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }

describe('estado del formulario (3): modo edición', () => {
  it('editar pieza: fila en rol editada con SKU, cantidad, Reutilizado y observación originales y modelo bloqueado', () => {
    const e = estadoInicial(datosEditar({ detalle: detalleEdicion({ cantidad: 2, observacion: 'conector dañado' }) }))
    expect(e).toMatchObject({ modo: 'editar', categoria: 'R', idAsignacion: null, imei: IMEI, incidencia: null, modelo: '13', modeloBloqueado: true })
    expect(e.edicion).toEqual({
      idRep: 'R20260916_5', tipo: 'pieza', idTecOriginal: 4, updatedAt: '2026-09-16T07:02:00', textoAccionOriginal: null, idComAccion: null,
    })
    expect(filasVisibles(e)).toBe(true)
    expect(reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '14' })).toBe(e)
    const bat = fila(e, 'bat')
    expect(bat).toMatchObject({ rol: 'editada', idCom: 101, cantidad: 2, reutilizado: false, observacion: 'conector dañado' })
    expect(bat.original).toEqual({ idCom: 101, cantidad: 2, reutilizado: false, observacion: 'conector dañado' })
    // cantidad > 0: "Reutilizado" apagado; "+" según stock; "-" encendido
    expect(bat.controles).toEqual({ mas: true, menos: true, reutilizado: false, sku: true, observacion: true })
    expect(e.filas.filter((f) => f.rol === 'editada')).toHaveLength(1)
    expect(fila(e, 'lcd')).toMatchObject({ rol: 'normal', idCom: 111, original: null }) // el resto, filtrado por el modelo
    expect(etiquetaImei(e)).toBe(`IMEI: ${IMEI}  ·  Editando R20260916_5`)
    expect(tituloPestana(e)).toBe('Editar reparación — R20260916_5')
    expect(hayCambioEnFilaEditada(e)).toBe(false)
    expect(zonaGuardarVisible(e)).toBe(false)
    // si era reutilizada: "+" y "-" apagados y la casilla encendida
    const reutilizada = estadoInicial(datosEditar({ detalle: detalleEdicion({ esReutilizado: true, cantidad: 0 }) }))
    expect(fila(reutilizada, 'bat')).toMatchObject({ reutilizado: true, cantidad: 0 })
    expect(fila(reutilizada, 'bat').controles).toMatchObject({ mas: false, menos: false, reutilizado: true })
    // con stock 0, "+" apagado
    const sinStock = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 102 }) }))
    expect(sinStock.modelo).toBe('14')
    expect(fila(sinStock, 'bat').controles.mas).toBe(false)
    // un SKU que ya no está activo no pierde su fila
    const inactivo = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 104 }) }))
    expect(inactivo.modelo).toBe('12')
    expect(inactivo.modelos).toEqual(['12', '13', '13promax', '14'])
    expect(fila(inactivo, 'bat')).toMatchObject({ rol: 'editada', idCom: 104 })
    expect(fila(inactivo, 'bat').opciones.map((c) => c.idCom)).toEqual([104])
    // una G… solo tiene filas de glass y categoría G
    const glass = estadoInicial(datosEditar({ idRep: 'G20260916_7', detalle: detalleEdicion({ idCom: 141 }) }))
    expect(glass.categoria).toBe('G')
    expect(glass.filas.map((f) => f.prefijo)).toEqual(['g', 'mc'])
    expect(fila(glass, 'g').rol).toBe('editada')
  })

  it('editar acción: ninguna fila editada, línea precargada sin papelera', () => {
    const e = estadoInicial(datosEditarAccion())
    expect(e.edicion).toMatchObject({ tipo: 'accion', textoAccionOriginal: 'Limpieza de altavoz', idComAccion: 161 })
    expect(e.modelo).toBe('13') // el del SKU otroi13
    expect(e.modeloBloqueado).toBe(true)
    expect(e.filas.some((f) => f.rol === 'editada')).toBe(false)
    expect(e.otros).toEqual([{ id: 1, texto: 'Limpieza de altavoz', origen: 'editada', guardada: null, confirmando: false, guardando: false }])
    expect(e.siguienteIdAccion).toBe(2)
    expect(etiquetaImei(e)).toBe(`IMEI: ${IMEI}  ·  Editando acción R20260916_5`)
    // sin papelera ni "✓ Guardar": no se puede quitar ni pedir confirmación
    expect(reducir(e, { tipo: 'QUITAR_ACCION', id: 1 })).toBe(e)
    expect(reducir(e, { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 })).toBe(e)
    expect(zonaGuardarVisible(e)).toBe(false)
    // texto distinto y no vacío = cambio; vacío = inválido (oculta la zona aunque haya otra cosa que guardar)
    const cambiada = reducir(e, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'Limpieza de altavoz y micrófono' })
    expect(zonaGuardarVisible(cambiada)).toBe(true)
    expect(accionEditadaInvalida(cambiada)).toBe(false)
    const mismoTexto = reducir(cambiada, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: ' Limpieza de altavoz ' })
    expect(zonaGuardarVisible(mismoTexto)).toBe(false)
    const vacia = aplicar(cambiada, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: '  ' }, { tipo: 'SUMAR', prefijo: 'bat' })
    expect(accionEditadaInvalida(vacia)).toBe(true)
    expect(zonaGuardarVisible(vacia)).toBe(false)
    expect(anadirAccionHabilitado(vacia)).toBe(false)
  })

  it('ya reparados: todo deshabilitado y botón yaReparado, salvo la fila editada', () => {
    // 112 (lcdi14) es de OTRO modelo y 104 (bati12) está inactivo: cuentan igual; la batería es la fila editada y manda
    const e = estadoInicial(datosEditar({ yaReparados: [112, 104] }))
    const lcd = fila(e, 'lcd')
    expect(lcd.rol).toBe('yaReparado')
    expect(lcd.controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: false })
    expect(botonDerecho(e, lcd)).toEqual({ tipo: 'yaReparado' })
    expect(reducir(e, { tipo: 'SUMAR', prefijo: 'lcd' })).toBe(e)
    expect(reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'lcd', valor: true })).toBe(e)
    expect(fila(e, 'bat').rol).toBe('editada')
    expect(botonDerecho(e, fila(e, 'bat'))).toEqual({ tipo: 'ninguno' })
    expect(fila(e, 'cha').rol).toBe('normal')
    // solo existe en edición: el flujo nuevo no marca nada
    expect(conModelo('13').filas.every((f) => f.rol === 'normal')).toBe(true)
  })

  it('acciones ya reparadas bloqueadas y contadas en el badge', () => {
    const e = estadoInicial(datosEditar({ accionesYaReparadas: ['Limpieza de altavoz', 'Ajuste de botón'] }))
    expect(e.otros.map((a) => [a.id, a.texto, a.origen])).toEqual([[1, 'Limpieza de altavoz', 'yaReparada'], [2, 'Ajuste de botón', 'yaReparada']])
    expect(contadorAcciones(e)).toBe(2)
    expect(reducir(e, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'otra' })).toBe(e)
    expect(reducir(e, { tipo: 'QUITAR_ACCION', id: 1 })).toBe(e)
    expect(zonaGuardarVisible(e)).toBe(false)
    // se pueden añadir líneas nuevas, con ids que siguen a los cargados
    const conNueva = aplicar(e, { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 3, texto: 'Cambio de tornillos' })
    expect(conNueva.otros.map((a) => a.id)).toEqual([1, 2, 3])
    expect(contadorAcciones(conNueva)).toBe(3)
    expect(zonaGuardarVisible(conNueva)).toBe(true)
    // editando una acción, las ya reparadas van delante de la línea editada
    const editandoAccion = estadoInicial(datosEditarAccion({ accionesYaReparadas: ['Ajuste de botón'] }))
    expect(editandoAccion.otros.map((a) => a.origen)).toEqual(['yaReparada', 'editada'])
    expect(editandoAccion.siguienteIdAccion).toBe(3)
  })

  it('previsión de stock: mismo SKU devuelve lo original, SKU distinto no, Reutilizado no descuenta, tendencia baja/sube/igual', () => {
    const catalogo = agrupados()
    catalogo.bat = [...catalogo.bat, componente({ idCom: 107, tipo: 'bati13oem', stock: 2 })]
    const e = estadoInicial(datosEditar({ agrupados: catalogo })) // bati13: stock 5, cantidad original 1
    expect(previsionStock(e, fila(e, 'bat'))).toEqual({ texto: '5 → 5', tendencia: 'igual' })
    const mas = reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })
    expect(previsionStock(mas, fila(mas, 'bat'))).toEqual({ texto: '5 → 4', tendencia: 'baja' })
    const menos = reducir(e, { tipo: 'RESTAR', prefijo: 'bat' })
    expect(previsionStock(menos, fila(menos, 'bat'))).toEqual({ texto: '5 → 6', tendencia: 'sube' })
    // "Reutilizado" no descuenta
    const reutilizada = reducir(menos, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    expect(previsionStock(reutilizada, fila(reutilizada, 'bat'))).toEqual({ texto: '5 → 6', tendencia: 'sube' })
    // otro SKU: no se le devuelve nada y se le descuenta la cantidad
    const otroSku = reducir(e, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 107 })
    expect(previsionStock(otroSku, fila(otroSku, 'bat'))).toEqual({ texto: '2 → 1', tendencia: 'baja' })
    // si la original era reutilizada no hay nada que devolver
    const eraReutilizada = estadoInicial(datosEditar({ detalle: detalleEdicion({ esReutilizado: true, cantidad: 0 }) }))
    expect(previsionStock(eraReutilizada, fila(eraReutilizada, 'bat'))).toEqual({ texto: '5 → 5', tendencia: 'igual' })
    // solo la fila editada tiene previsión
    expect(previsionStock(e, fila(e, 'lcd'))).toBeNull()
    const nuevo = conModelo('13')
    expect(previsionStock(nuevo, fila(nuevo, 'bat'))).toBeNull()
    // "+" sigue limitado por el stock actual: no suma las unidades que se devolverían
    const alLimite = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 111 }) })) // lcdi13: stock 1, cantidad 1
    expect(reducir(alLimite, { tipo: 'SUMAR', prefijo: 'lcd' })).toBe(alLimite)
    // volver al componente ORIGINAL ni pone a 0 ni toca "+", aunque su stock actual (2) sea menor que la cantidad (3)
    const escaso = { ...catalogo, bat: [componente({ idCom: 101, tipo: 'bati13', stock: 2 }), componente({ idCom: 107, tipo: 'bati13oem', stock: 5 })] }
    const tres = estadoInicial(datosEditar({ agrupados: escaso, detalle: detalleEdicion({ cantidad: 3 }) }))
    const ida = reducir(tres, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 107 })
    expect(fila(ida, 'bat')).toMatchObject({ idCom: 107, cantidad: 3 })
    const vuelta = reducir(ida, { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 })
    expect(fila(vuelta, 'bat')).toMatchObject({ idCom: 101, cantidad: 3 })
    expect(fila(vuelta, 'bat').controles.mas).toBe(fila(ida, 'bat').controles.mas)
    expect(hayCambioEnFilaEditada(vuelta)).toBe(false)
    // a un SKU que NO es el original y tiene menos stock que la cantidad, sí se pone a 0
    const aOtro = reducir(estadoInicial(datosEditar({ agrupados: catalogo, detalle: detalleEdicion({ cantidad: 3 }) })),
      { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 107 }) // bati13oem: stock 2 < 3
    expect(fila(aOtro, 'bat')).toMatchObject({ idCom: 107, cantidad: 0 })
  })

  it('cambio inválido oculta la zona y marca el contador', () => {
    const e = estadoInicial(datosEditar())
    expect(filaEditadaInvalida(e)).toBe(false)
    const aCero = reducir(e, { tipo: 'RESTAR', prefijo: 'bat' })
    expect(hayCambioEnFilaEditada(aCero)).toBe(true)
    expect(filaEditadaInvalida(aCero)).toBe(true)
    expect(zonaGuardarVisible(aCero)).toBe(false)
    expect(hayCambiosSinGuardar(aCero)).toBe(false) // cerrar así no pregunta: el cambio se pierde
    // ni aunque haya una fila nueva activa
    const conFilaNueva = reducir(aCero, { tipo: 'SUMAR', prefijo: 'lcd' })
    expect(zonaGuardarVisible(conFilaNueva)).toBe(false)
    // con "Reutilizado" el cambio vuelve a ser válido
    const valida = reducir(aCero, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    expect(filaEditadaInvalida(valida)).toBe(false)
    expect(zonaGuardarVisible(valida)).toBe(true)
    expect(reducir(aCero, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' })).toBe(aCero)
  })

  it('cambio válido muestra "Guardar cambios" y el primer clic "✓  Confirmar terminar"', () => {
    const e = estadoInicial(datosEditar())
    expect(textoBotonGuardar(e)).toBe('Guardar cambios')
    const cambios: AccionFormulario[] = [
      { tipo: 'SUMAR', prefijo: 'bat' },
      { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
      { tipo: 'CAMBIAR_SKU', prefijo: 'bat', idCom: 101 }, // el mismo: no es cambio
    ]
    expect(zonaGuardarVisible(reducir(e, cambios[0]))).toBe(true)
    expect(zonaGuardarVisible(reducir(e, cambios[1]))).toBe(true)
    expect(zonaGuardarVisible(reducir(e, cambios[2]))).toBe(false)
    // deshacer el cambio vuelve a ocultarla
    expect(zonaGuardarVisible(aplicar(e, { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'RESTAR', prefijo: 'bat' }))).toBe(false)
    // borrar una observación original es un cambio
    const conNota = estadoInicial(datosEditar({ detalle: detalleEdicion({ observacion: 'conector dañado' }) }))
    expect(zonaGuardarVisible(reducir(conNota, { tipo: 'BORRAR_OBSERVACION', prefijo: 'bat' }))).toBe(true)
    const primerClic = aplicar(e, { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PEDIR_CONFIRMACION_GUARDAR' })
    expect(textoBotonGuardar(primerClic)).toBe('✓  Confirmar terminar') // también en edición
    expect(reducir(primerClic, { tipo: 'INICIO_GUARDADO' }).guardado.enCurso).toBe(true)
  })

  it('fila nueva activa en edición muestra la zona y no ofrece "✓ Guardar fila" ni sub-fila', () => {
    const e = estadoInicial(datosEditar({ detalle: detalleEdicion({ idCom: 102 }) })) // modelo 14; lcdi14 tiene stock 3
    const activa = aplicar(e, { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'SUMAR', prefijo: 'lcd' })
    expect(fila(activa, 'lcd').cantidad).toBe(3) // en el límite la cantidad simplemente no pasa del stock
    expect(reducir(activa, { tipo: 'SUMAR', prefijo: 'lcd' })).toBe(activa)
    expect(zonaGuardarVisible(activa)).toBe(true)
    expect(botonDerecho(activa, fila(activa, 'lcd'))).toEqual({ tipo: 'ninguno' })
    expect(subFila(activa, fila(activa, 'lcd'))).toEqual({ tipo: 'oculta' })
    expect(reducir(activa, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'lcd' })).toBe(activa)
    expect(reducir(activa, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: '' })).toBe(activa)
    // la fila editada (bati14, stock 0) tampoco tiene sub-fila de "sin stock"
    expect(subFila(activa, fila(activa, 'bat'))).toEqual({ tipo: 'oculta' })
  })

  it('hayCambiosSinGuardar solo en editar', () => {
    const editando = reducir(estadoInicial(datosEditar()), { tipo: 'SUMAR', prefijo: 'bat' })
    expect(hayCambiosSinGuardar(editando)).toBe(true)
    expect(hayCambiosSinGuardar(estadoInicial(datosEditar()))).toBe(false)
    const nuevo = reducir(conModelo('13'), { tipo: 'SUMAR', prefijo: 'bat' })
    expect(zonaGuardarVisible(nuevo)).toBe(true)
    expect(hayCambiosSinGuardar(nuevo)).toBe(false) // el flujo nuevo nunca pregunta: lo cubre el borrador
  })
})
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  9 failed | 48 passed (57)`: fallan los 9 nuevos (el estado de edición de la Task 6 no tiene fila editada, `edicion` ni acciones; p. ej. `editar acción…` con `expected null to match object { tipo: 'accion', … }`) y siguen en verde los 48 de las Tasks 6 y 7.

- [x] **Step 2: Estado inicial de edición, cabecera, selectores de edición y sus ramas**

Cinco cambios en `estado.ts`:

1. Sustituye la función `inicialEditar` **entera** (desde `function inicialEditar(datos: DatosEditar): EstadoFormulario {` hasta su `}` de cierre; `estadoInicial`, que va detrás, no se toca) por este bloque:

```ts
/** Busca un componente en el catálogo completo (también inactivos y el grupo 'otro'): la reparación editada puede ser de un SKU
 *  que ya no está activo y no por eso se pierde su fila. */
function buscarComponente(agrupados: ComponentesAgrupados, idCom: number): { prefijo: string; componente: Componente } | null {
  for (const prefijo of Object.keys(agrupados)) {
    const componente = agrupados[prefijo].find((c) => c.idCom === idCom)
    if (componente) return { prefijo, componente }
  }
  return null
}

/** Fila en edición: SKU, cantidad, "Reutilizado" y observación originales. "+" apagado con stock ≤ 0; si era reutilizada,
 *  "+" y "-" apagados; si no y cantidad > 0, "Reutilizado" apagado. */
function filaEditada(base: BaseFila, c: Componente, modelo: string | null, detalle: DetalleEdicion): FilaEstado {
  const skus = base.skus.some((s) => s.idCom === c.idCom) ? base.skus : [...base.skus, c]
  const limpia = filaLimpia({ prefijo: base.prefijo, nombre: base.nombre, skus }, modelo)
  const opciones = limpia.opciones.some((o) => o.idCom === c.idCom) ? limpia.opciones : [...limpia.opciones, c]
  const cantidad = Math.max(0, detalle.cantidad)
  const reutilizado = detalle.esReutilizado
  const observacion = textoONull(detalle.observacion)
  return {
    ...limpia,
    skus,
    opciones,
    idCom: c.idCom,
    cantidad,
    reutilizado,
    observacion,
    controles: {
      mas: !reutilizado && c.stock > 0,
      menos: !reutilizado && cantidad > 0,
      reutilizado: reutilizado || cantidad === 0,
      sku: true,
      observacion: true,
    },
    rol: 'editada',
    original: { idCom: c.idCom, cantidad, reutilizado, observacion },
  }
}

/** Modo edición: el modelo es el del SKU editado (pieza o acción 'otro…') y el combo queda siempre bloqueado. Las filas de
 *  otros tipos con algún SKU (de cualquier modelo, activo o no) ya reparado en el IMEI salen como 'yaReparado'. */
function inicialEditar(datos: DatosEditar): EstadoFormulario {
  const glass = datos.idRep.startsWith('G')
  const base = estadoBase({
    modo: 'editar',
    categoria: glass ? 'G' : 'R',
    idAsignacion: null,
    imei: datos.detalle.imei,
    incidencia: null,
    agrupados: datos.agrupados,
    glass,
  })
  const hallado = buscarComponente(datos.agrupados, datos.detalle.idCom)
  const esAccion = hallado !== null && hallado.prefijo === PREFIJO_OTRO
  const modelo = hallado === null ? null : extraerModelo(hallado.componente.tipo, hallado.prefijo)
  const filas = base.filas.map((fila): FilaEstado => {
    if (hallado !== null && !esAccion && fila.prefijo === hallado.prefijo) return filaEditada(fila, hallado.componente, modelo, datos.detalle)
    const limpia = filaLimpia(fila, modelo)
    const yaReparado = (datos.agrupados[fila.prefijo] ?? []).some((c) => datos.yaReparados.includes(c.idCom))
    return yaReparado ? { ...limpia, rol: 'yaReparado', controles: CONTROLES_APAGADOS } : limpia
  })
  const linea = (id: number, texto: string, origen: OrigenAccion): OtraAccion => ({
    id, texto, origen, guardada: null, confirmando: false, guardando: false,
  })
  const otros = datos.accionesYaReparadas.map((texto, i) => linea(i + 1, texto, 'yaReparada'))
  if (esAccion) otros.push(linea(otros.length + 1, datos.detalle.observacion ?? '', 'editada'))
  return {
    ...base,
    modelo,
    modeloBloqueado: true,
    modelos: conModeloEnLista(base.modelos, modelo),
    filas,
    otros,
    siguienteIdAccion: otros.length + 1,
    edicion: {
      idRep: datos.idRep,
      tipo: esAccion ? 'accion' : 'pieza',
      idTecOriginal: datos.detalle.idTec,
      updatedAt: datos.detalle.updatedAt,
      textoAccionOriginal: esAccion ? (datos.detalle.observacion ?? '').trim() : null,
      idComAccion: esAccion ? datos.detalle.idCom : null,
    },
  }
}
```

2. Sustituye `etiquetaImei` y `tituloPestana` (las dos funciones, con el comentario que hay entre ellas) por:

```ts
export function etiquetaImei(e: EstadoFormulario): string {
  if (e.edicion === null) return `IMEI: ${e.imei}`
  return `IMEI: ${e.imei}  ·  Editando ${e.edicion.tipo === 'accion' ? 'acción ' : ''}${e.edicion.idRep}`
}

/** Título de la pestaña del navegador y nombre accesible del diálogo. */
export function tituloPestana(e: EstadoFormulario): string {
  return e.edicion === null ? `Nueva reparación — IMEI ${e.imei}` : `Editar reparación — ${e.edicion.idRep}`
}
```

3. En `zonaGuardarVisible`, la primera línea del cuerpo

```ts
  if (e.modo === 'editar') return false
```

pasa a ser

```ts
  if (e.modo === 'editar') return zonaVisibleEnEdicion(e)
```

4. En `botonDerecho`, añade como **primera** línea del cuerpo:

```ts
  if (fila.rol === 'yaReparado') return { tipo: 'yaReparado' }
```

5. Añade **al final** del fichero:

```ts
// ───────────────────────────── Selectores: modo edición ─────────────────────────────

function filaEnEdicion(e: EstadoFormulario): FilaEstado | null {
  return e.filas.find((f) => f.rol === 'editada') ?? null
}

function accionEnEdicion(e: EstadoFormulario): OtraAccion | null {
  return e.otros.find((a) => a.origen === 'editada') ?? null
}

/** Hay cambio si varía cantidad, SKU, "Reutilizado" u observación respecto al original. */
export function hayCambioEnFilaEditada(e: EstadoFormulario): boolean {
  const fila = filaEnEdicion(e)
  if (fila === null || fila.original === null) return false
  const o = fila.original
  return fila.cantidad !== o.cantidad || fila.idCom !== o.idCom || fila.reutilizado !== o.reutilizado || fila.observacion !== o.observacion
}

/** Cambio sin uso (cantidad 0 y sin "Reutilizado"): contador en rojo y zona de guardar oculta. */
export function filaEditadaInvalida(e: EstadoFormulario): boolean {
  const fila = filaEnEdicion(e)
  return fila !== null && hayCambioEnFilaEditada(e) && fila.cantidad === 0 && !fila.reutilizado
}

/** La acción editada con el texto vacío invalida todo el guardado. */
export function accionEditadaInvalida(e: EstadoFormulario): boolean {
  const accion = accionEnEdicion(e)
  return accion !== null && accion.texto.trim() === ''
}

function hayCambioEnAccionEditada(e: EstadoFormulario): boolean {
  const accion = accionEnEdicion(e)
  if (accion === null || e.edicion === null) return false
  const texto = accion.texto.trim()
  return texto !== '' && texto !== e.edicion.textoAccionOriginal
}

/** Filas que no son la editada ni están ya reparadas y que se han activado: se guardan como reparaciones nuevas. */
function filasNuevasEnEdicion(e: EstadoFormulario): FilaEstado[] {
  return e.filas.filter((f) => f.rol === 'normal' && !filaSinSku(f) && filaActiva(f))
}

function zonaVisibleEnEdicion(e: EstadoFormulario): boolean {
  if (filaEditadaInvalida(e) || accionEditadaInvalida(e)) return false
  return (
    hayCambioEnFilaEditada(e) || hayCambioEnAccionEditada(e) || filasNuevasEnEdicion(e).length > 0 || accionesPendientes(e).length > 0
  )
}

/** Solo en edición se pregunta al cerrar; un cambio inválido oculta la zona y por eso se pierde sin preguntar. */
export function hayCambiosSinGuardar(e: EstadoFormulario): boolean {
  return e.modo === 'editar' && zonaGuardarVisible(e)
}

export type PrevisionStock = { texto: string; tendencia: 'baja' | 'sube' | 'igual' }

/** "<stock> → <previsto>" de la fila editada: previsto = stock + devuelto − descontado. Devuelto = la cantidad original si el
 *  SKU sigue siendo el original y no era reutilizada; descontado = 0 con "Reutilizado", si no la cantidad. */
export function previsionStock(e: EstadoFormulario, fila: FilaEstado): PrevisionStock | null {
  const stock = stockDe(fila)
  if (e.modo !== 'editar' || fila.rol !== 'editada' || fila.original === null || stock === null) return null
  const devuelto = fila.idCom === fila.original.idCom && !fila.original.reutilizado ? fila.original.cantidad : 0
  const descontado = fila.reutilizado ? 0 : fila.cantidad
  const previsto = stock + devuelto - descontado
  return { texto: `${stock} → ${previsto}`, tendencia: previsto < stock ? 'baja' : previsto > stock ? 'sube' : 'igual' }
}
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  57 passed (57)`.

- [x] **Step 3: Test de los cuerpos de las llamadas (falla)**

Añade **al final** de `estado.edicion.test.ts`:

```ts
describe('estado del formulario (3): cuerpos de las llamadas', () => {
  it('cuerpoGuardarFila y cuerpoGuardarAccion con incidencia y técnico de la sesión', () => {
    const e = aplicar(conModelo('13', { incidencia: 'R20260910_2' }),
      { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
      { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: '  Limpieza de altavoz ' })
    expect(filaDeCuerpo(fila(e, 'bat'))).toEqual({
      idCom: 101, cantidad: 1, reutilizado: false, observacion: 'conector dañado', prefijo: 'bat', ...FILA_VACIA,
    })
    expect(filaDeAccion(161, ' Ajuste ')).toEqual({ idCom: 161, cantidad: 0, reutilizado: false, observacion: 'Ajuste', prefijo: 'otro', ...FILA_VACIA })
    expect(cuerpoGuardarFila(e, 'bat', 4)).toEqual({
      filas: [{ idCom: 101, cantidad: 1, reutilizado: false, observacion: 'conector dañado', prefijo: 'bat', ...FILA_VACIA }],
      imei: IMEI, idTec: 4, idRepAnterior: 'R20260910_2',
    })
    expect(cuerpoGuardarAccion(e, 1, 4)).toEqual({
      filas: [{ idCom: 161, cantidad: 0, reutilizado: false, observacion: 'Limpieza de altavoz', prefijo: 'otro', ...FILA_VACIA }],
      imei: IMEI, idTec: 4, idRepAnterior: 'R20260910_2',
    })
    // sin incidencia, sin observación: null
    const sinNada = reducir(conModelo('13'), { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cha', valor: true })
    expect(cuerpoGuardarFila(sinNada, 'cha', 4)).toEqual({
      filas: [{ idCom: 131, cantidad: 0, reutilizado: true, observacion: null, prefijo: 'cha', ...FILA_VACIA }],
      imei: IMEI, idTec: 4, idRepAnterior: null,
    })
    // acción inexistente, vacía o sin componente "otro" para el modelo
    expect(cuerpoGuardarAccion(e, 99, 4)).toBeNull()
    expect(cuerpoGuardarAccion(reducir(conModelo('13'), { tipo: 'ANADIR_ACCION' }), 1, 4)).toBeNull()
    expect(cuerpoGuardarAccion(reducir(e, { tipo: 'CAMBIAR_MODELO', modelo: '13promax' }), 1, 4)).toBeNull()
    expect(() => cuerpoGuardarFila(e, 'no-existe', 4)).toThrow()
  })

  it('planTerminar: orden de agotados, cantidad 0 en sin stock y stock en límite, descripción null', () => {
    const catalogo = agrupados()
    catalogo.cam = [componente({ idCom: 121, tipo: 'cami13', stock: 0, stockMinimo: 1 })]
    const e = aplicar(conModelo('13', { agrupados: catalogo }),
      // se confirman en orden inverso al de las filas: el plan sigue el orden de FILAS (lcd antes que cam)
      { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'cam', descripcion: 'cámara trasera' },
      { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: '' },
      { tipo: 'SUMAR', prefijo: 'bat' })
    const plan = planTerminar(e, 4)
    expect(plan.agotados).toEqual([
      { prefijo: 'lcd', cuerpo: { idCom: 111, cantidad: 1, descripcion: null } }, // límite: el stock
      { prefijo: 'cam', cuerpo: { idCom: 121, cantidad: 0, descripcion: 'cámara trasera' } }, // sin stock: 0
    ])
    // las filas con agotado nuevo no van en completa
    expect(plan.completa).toEqual({
      filas: [{ idCom: 101, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'bat', ...FILA_VACIA }],
      imei: IMEI, idTec: 4, idRepAnterior: null, idAsignacion: 'A20260916_1', categoria: null,
    })
  })

  it('planTerminar no repite agotados registrados', () => {
    const catalogo = agrupados()
    catalogo.cam = [componente({ idCom: 121, tipo: 'cami13', stock: 0, stockMinimo: 1 })]
    const e = aplicar(conModelo('13', { agrupados: catalogo }),
      { tipo: 'SUMAR', prefijo: 'lcd' }, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'lcd', descripcion: '' },
      { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'cam', descripcion: '' },
      { tipo: 'SUMAR', prefijo: 'bat' })
    expect(planTerminar(e, 4).agotados.map((a) => a.prefijo)).toEqual(['lcd', 'cam'])
    // el 1.º se registró y el 2.º falló: el reintento continúa con los que faltan
    const trasElPrimero = aplicar(e, { tipo: 'AGOTADO_REGISTRADO', prefijo: 'lcd' }, { tipo: 'FALLO_GUARDADO' })
    expect(planTerminar(trasElPrimero, 4).agotados.map((a) => a.prefijo)).toEqual(['cam'])
    // todos registrados y falló completa: el reintento solo envía completa, sin la fila agotada
    const trasTodos = reducir(trasElPrimero, { tipo: 'AGOTADO_REGISTRADO', prefijo: 'cam' })
    const plan = planTerminar(trasTodos, 4)
    expect(plan.agotados).toEqual([])
    expect(plan.completa?.filas.map((f) => f.idCom)).toEqual([101])
  })

  it('planTerminar: solo agotados → completa null, también si ya estaban registrados', () => {
    const e = reducir(conModelo('14'), { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: 'batería hinchada' })
    expect(planTerminar(e, 4)).toEqual({
      agotados: [{ prefijo: 'bat', cuerpo: { idCom: 102, cantidad: 0, descripcion: 'batería hinchada' } }],
      completa: null, // la asignación queda abierta con su solicitud
    })
    const registrado = reducir(e, { tipo: 'AGOTADO_REGISTRADO', prefijo: 'bat' })
    expect(planTerminar(registrado, 4)).toEqual({ agotados: [], completa: null })
  })

  it('planTerminar: filas vacías con filas guardadas → completa con filas []', () => {
    const e = aplicar(conModelo('13'),
      { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' }, { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' },
      { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' },
      { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'Limpieza de altavoz' },
      { tipo: 'PEDIR_CONFIRMACION_ACCION', id: 1 }, { tipo: 'INICIO_GUARDAR_ACCION', id: 1 },
      { tipo: 'ACCION_GUARDADA', id: 1, idRep: 'R20260916_10', fecha: '16/09 09:20' })
    // ni la fila guardada ni la acción guardada se reenvían; completa vacía es lo que cierra la asignación
    expect(planTerminar(e, 4)).toEqual({
      agotados: [],
      completa: { filas: [], imei: IMEI, idTec: 4, idRepAnterior: null, idAsignacion: 'A20260916_1', categoria: null },
    })
  })

  it('planTerminar incluye la fila de solicitud con Reutilizado y las acciones pendientes', () => {
    const e = aplicar(estadoInicial(datosNuevo({ incidencia: 'R20260910_2', solicitudes: [solicitudAsignacion(), solicitudAsignacion({ idCom: 112 })] })),
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true },
      { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: ' Limpieza de altavoz ' },
      { tipo: 'ANADIR_ACCION' }) // la segunda línea, vacía, no se envía
    const plan = planTerminar(e, 4)
    expect(plan.agotados).toEqual([]) // una solicitud del servidor no es un agotado nuevo
    expect(plan.completa).toEqual({
      filas: [
        { idCom: 102, cantidad: 0, reutilizado: true, observacion: null, prefijo: 'bat', ...FILA_VACIA }, // fila normal: esSolicitud false
        { idCom: 162, cantidad: 0, reutilizado: false, observacion: 'Limpieza de altavoz', prefijo: 'otro', ...FILA_VACIA },
      ],
      imei: IMEI, idTec: 4, idRepAnterior: 'R20260910_2', idAsignacion: 'A20260916_1', categoria: null,
    })
    // la solicitud de pantalla, sin tocar, no va en ninguna parte
    expect(plan.completa?.filas.some((f) => f.idCom === 112)).toBe(false)
  })

  it('planTerminar en glass no lleva categoria', () => {
    const e = aplicar(estadoInicial(datosNuevo({ modo: 'glass', idAsignacion: 'AG20260916_2' })),
      { tipo: 'CAMBIAR_MODELO', modelo: '13' }, { tipo: 'SUMAR', prefijo: 'g' })
    expect(planTerminar(e, 6).completa).toEqual({
      filas: [{ idCom: 141, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'g', ...FILA_VACIA }],
      imei: IMEI, idTec: 6, idRepAnterior: null, idAsignacion: 'AG20260916_2', categoria: null,
    })
    expect(cuerpoGuardarFila(e, 'g', 6)).not.toHaveProperty('categoria')
  })

  it('planGuardarCambios: cuatro pasos, idTec original, categoria G solo si idRep empieza por G, sin idAsignacion ni idRepAnterior', () => {
    // pieza: fila editada + fila nueva + acción nueva
    const pieza = aplicar(estadoInicial(datosEditar()),
      { tipo: 'SUMAR', prefijo: 'bat' }, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'conector dañado' },
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cam', valor: true },
      { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: ' Limpieza de altavoz ' })
    expect(planGuardarCambios(pieza)).toEqual({
      editarAccion: null,
      editarFila: { idComNuevo: 101, esReutilizadoNuevo: false, observacionNueva: 'conector dañado', nNuevas: 2, updatedAt: '2026-09-16T07:02:00' },
      completaFilas: {
        filas: [{ idCom: 121, cantidad: 0, reutilizado: true, observacion: null, prefijo: 'cam', ...FILA_VACIA }],
        imei: IMEI, idTec: 4, idRepAnterior: null, idAsignacion: null, categoria: null, // idTec = el técnico ORIGINAL (detalle.idTec)
      },
      completaAcciones: {
        filas: [{ idCom: 161, cantidad: 0, reutilizado: false, observacion: 'Limpieza de altavoz', prefijo: 'otro', ...FILA_VACIA }],
        imei: IMEI, idTec: 4, idRepAnterior: null, idAsignacion: null, categoria: null,
      },
    })
    // sin cambios no hay nada que enviar; la fila editada nunca va en completa
    expect(planGuardarCambios(estadoInicial(datosEditar()))).toEqual({ editarAccion: null, editarFila: null, completaFilas: null, completaAcciones: null })
    // acción editada: paso 0 con nNuevas 0 y sin Reutilizado; su línea no se reenvía como acción nueva
    const accion = reducir(estadoInicial(datosEditarAccion()), { tipo: 'ESCRIBIR_ACCION', id: 1, texto: ' Limpieza de altavoz y micrófono ' })
    expect(planGuardarCambios(accion)).toEqual({
      editarAccion: { idComNuevo: 161, esReutilizadoNuevo: false, observacionNueva: 'Limpieza de altavoz y micrófono', nNuevas: 0, updatedAt: '2026-09-16T07:02:00' },
      editarFila: null, completaFilas: null, completaAcciones: null,
    })
    // edición de una G…: categoria 'G' en los dos completa
    const glass = aplicar(estadoInicial(datosEditar({ idRep: 'G20260916_7', detalle: detalleEdicion({ idCom: 141, idTec: 6 }) })),
      { tipo: 'SUMAR', prefijo: 'mc' }, { tipo: 'ANADIR_ACCION' }, { tipo: 'ESCRIBIR_ACCION', id: 1, texto: 'Limpieza de marco' })
    const planGlass = planGuardarCambios(glass)
    expect(planGlass.editarFila).toBeNull()
    expect(planGlass.completaFilas).toMatchObject({ idTec: 6, categoria: 'G', idAsignacion: null, idRepAnterior: null })
    expect(planGlass.completaAcciones).toMatchObject({ idTec: 6, categoria: 'G', idAsignacion: null, idRepAnterior: null })
    // las acciones "✓ Ya reparada" no se reenvían
    const yaReparadas = estadoInicial(datosEditar({ accionesYaReparadas: ['Limpieza de altavoz'] }))
    expect(planGuardarCambios(yaReparadas).completaAcciones).toBeNull()
    // fuera de edición no hay plan
    expect(planGuardarCambios(conModelo('13'))).toEqual({ editarAccion: null, editarFila: null, completaFilas: null, completaAcciones: null })
  })
})
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  8 failed | 57 passed (65)` con `TypeError: filaDeCuerpo is not a function`, `planTerminar is not a function` y `planGuardarCambios is not a function`.

- [x] **Step 4: Constructores de cuerpos**

Dos cambios en `estado.ts`:

1. El import de tipos de la primera línea

```ts
import type { AsignacionActiva, Componente, ComponentesAgrupados, DetalleEdicion, SolicitudAsignacion } from '@/shared/api/client'
```

pasa a ser

```ts
import type {
  AgotarRequest, AsignacionActiva, Componente, ComponentesAgrupados, DetalleEdicion, EditarReparacionRequest, FilaReparacion,
  GuardarFilaRequest, InsertarCompletaRequest, SolicitudAsignacion,
} from '@/shared/api/client'
```

2. Añade **al final** del fichero:

```ts
// ───────────────────────────── Cuerpos de las llamadas ─────────────────────────────
// Los campos sin valor viajan como null (el contrato marca todas las propiedades como required).

export function filaDeCuerpo(fila: FilaEstado): FilaReparacion {
  if (fila.idCom === null) throw new Error(`La fila ${fila.prefijo} no tiene SKU: no se puede enviar`)
  return {
    idCom: fila.idCom,
    cantidad: fila.cantidad,
    reutilizado: fila.reutilizado,
    observacion: fila.observacion,
    prefijo: fila.prefijo,
    esSolicitud: false,
    descripcionSolicitud: null,
    estadoSolicitud: null,
    enCamino: false,
  }
}

export function filaDeAccion(idCom: number, texto: string): FilaReparacion {
  return {
    idCom,
    cantidad: 0,
    reutilizado: false,
    observacion: texto.trim(),
    prefijo: PREFIJO_OTRO,
    esSolicitud: false,
    descripcionSolicitud: null,
    estadoSolicitud: null,
    enCamino: false,
  }
}

/** Una fila de cuerpo por cada acción nueva con texto (ninguna si el modelo no tiene componente 'otro'). */
function filasDeAcciones(e: EstadoFormulario): FilaReparacion[] {
  const idCom = idComOtro(e)
  return idCom === null ? [] : accionesPendientes(e).map((a) => filaDeAccion(idCom, a.texto))
}

/** "✓ Guardar fila": una sola fila, con el técnico de la sesión (el servidor toma el del token) y la incidencia si la hay. */
export function cuerpoGuardarFila(e: EstadoFormulario, prefijo: string, idTecSesion: number): GuardarFilaRequest {
  const fila = e.filas.find((f) => f.prefijo === prefijo)
  if (fila === undefined) throw new Error(`No existe la fila ${prefijo}`)
  return { filas: [filaDeCuerpo(fila)], imei: e.imei, idTec: idTecSesion, idRepAnterior: e.incidencia }
}

/** "✓ Guardar" de una acción; null si el modelo no tiene componente 'otro', la línea no existe o no tiene texto. */
export function cuerpoGuardarAccion(e: EstadoFormulario, id: number, idTecSesion: number): GuardarFilaRequest | null {
  const idCom = idComOtro(e)
  const accion = e.otros.find((a) => a.id === id)
  if (idCom === null || accion === undefined || accion.texto.trim() === '') return null
  return { filas: [filaDeAccion(idCom, accion.texto)], imei: e.imei, idTec: idTecSesion, idRepAnterior: e.incidencia }
}

export type PlanTerminar = {
  agotados: { prefijo: string; cuerpo: AgotarRequest }[] // solo los NO registrados, en orden de filas
  completa: InsertarCompletaRequest | null // null = sin filas que enviar y hubo agotado nuevo (registrado o no)
}

/** "Terminar asignación": primero un agotar-componente por cada agotado local aún sin registrar (un reintento no repite los
 *  ya registrados); después `completa` con las filas no guardadas activas (también las de solicitud cargada con
 *  "Reutilizado") y las acciones pendientes. Con `filas` vacía se envía igualmente (cierra la asignación), salvo que hubiera
 *  algún agotado nuevo: entonces la asignación queda abierta con su solicitud y `completa` no se llama. Sin `categoria`. */
export function planTerminar(e: EstadoFormulario, idTecSesion: number): PlanTerminar {
  const conAgotado = e.filas.filter((f) => f.guardada === null && f.agotado !== null)
  const agotados = conAgotado.flatMap((f) =>
    f.agotado === null || f.agotado.registrado || f.idCom === null
      ? []
      : [{ prefijo: f.prefijo, cuerpo: { idCom: f.idCom, cantidad: f.cantidad, descripcion: f.agotado.descripcion } }],
  )
  const filas = [
    ...e.filas.filter((f) => f.guardada === null && f.agotado === null && !filaSinSku(f) && filaActiva(f)).map(filaDeCuerpo),
    ...filasDeAcciones(e),
  ]
  if (filas.length === 0 && conAgotado.length > 0) return { agotados, completa: null }
  return {
    agotados,
    completa: { filas, imei: e.imei, idTec: idTecSesion, idRepAnterior: e.incidencia, idAsignacion: e.idAsignacion, categoria: null },
  }
}

export type PlanGuardarCambios = {
  editarAccion: EditarReparacionRequest | null // paso 0
  editarFila: EditarReparacionRequest | null // paso 1
  completaFilas: InsertarCompletaRequest | null // paso 2
  completaAcciones: InsertarCompletaRequest | null // paso 3
}

/** "Guardar cambios", en orden. Las filas y acciones nuevas conservan el técnico ORIGINAL de la reparación editada y van
 *  sin idAsignacion ni idRepAnterior; `categoria` es 'G' solo si se edita una G…. */
export function planGuardarCambios(e: EstadoFormulario): PlanGuardarCambios {
  const edicion = e.edicion
  if (e.modo !== 'editar' || edicion === null) return { editarAccion: null, editarFila: null, completaFilas: null, completaAcciones: null }
  const completa = (filas: FilaReparacion[]): InsertarCompletaRequest | null =>
    filas.length === 0
      ? null
      : {
          filas,
          imei: e.imei,
          idTec: edicion.idTecOriginal,
          idRepAnterior: null,
          idAsignacion: null,
          categoria: edicion.idRep.startsWith('G') ? 'G' : null,
        }
  const accion = accionEnEdicion(e)
  const fila = filaEnEdicion(e)
  return {
    editarAccion:
      accion !== null && edicion.idComAccion !== null && hayCambioEnAccionEditada(e)
        ? {
            idComNuevo: edicion.idComAccion,
            esReutilizadoNuevo: false,
            observacionNueva: accion.texto.trim(),
            nNuevas: 0,
            updatedAt: edicion.updatedAt,
          }
        : null,
    editarFila:
      fila !== null && fila.idCom !== null && hayCambioEnFilaEditada(e)
        ? {
            idComNuevo: fila.idCom,
            esReutilizadoNuevo: fila.reutilizado,
            observacionNueva: fila.observacion,
            nNuevas: fila.cantidad,
            updatedAt: edicion.updatedAt,
          }
        : null,
    completaFilas: completa(filasNuevasEnEdicion(e).map(filaDeCuerpo)),
    completaAcciones: completa(filasDeAcciones(e)),
  }
}
```

```bash
npm test -- src/modules/taller/formulario
```
Expected: `Tests  65 passed (65)`.

- [x] **Step 5: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/estado.ts src/modules/taller/formulario/estado.edicion.test.ts
git commit -m "feat(web): estado del formulario (3): modo edición, previsión de stock y cuerpos de las llamadas"
```
Expected: lint, typecheck y tests en verde.

**Notas para el informe de la tarea:**
- El componente editado se busca en **todo** el catálogo (también inactivos y el grupo `otro`): si su SKU ya no está activo se añade a las opciones de su fila y su modelo al combo; `yaReparados` se compara también contra el grupo completo del tipo.
- En edición "+" sigue limitado por el stock **actual**; la previsión no cambia ese límite.
- `planTerminar` devuelve `completa: null` siempre que no haya filas que enviar y exista algún agotado local, esté o no `registrado`: así el reintento de un intento que ya registró sus agotados no cierra la asignación por error.
- Quien ejecute "Terminar asignación" (Task 15) debe despachar `AGOTADO_REGISTRADO` tras cada `agotar-componente` correcto y **recalcular** `planTerminar` con el estado nuevo antes de reintentar.
- `filaDeCuerpo` y `cuerpoGuardarFila` lanzan `Error` ante una fila sin SKU o inexistente: es un fallo de programación (la vista nunca ofrece guardar esas filas), no un caso de uso.


### Task 9: Web — `formulario/borrador.ts`: capturar, serializar, leer y aplicar con el formato del JavaFX

**Files:**
- Create: `src/modules/taller/formulario/borrador.ts`, `src/modules/taller/formulario/borrador.test.ts`
- Modify: `src/modules/taller/formulario/estado.ts` (acciones `REEMPLAZAR` y `DESBLOQUEAR_BORRADAS`), `src/modules/taller/formulario/estado.test.ts`

**Interfaces:**
- Consumes (de `./estado`, Tasks 6–8, tal cual): tipos `EstadoFormulario`, `FilaEstado`, `OtraAccion`, `ControlesFila`, `Guardada`, `AgotadoLocal`, `DatosNuevo`, `AccionFormulario`; funciones `estadoInicial`, `reducir`, `componenteDe`, `subFila`. De `../test/fabrica` (Task 4): `agrupados`, `componente`, `solicitudAsignacion`, `BORRADOR_JAVAFX`.
- Produces (W7):

```ts
export type BorradorFila = {
  prefijo: string
  idCom: number                        // -1 sin elegir
  cantidad: number
  reutilizado: boolean
  observacion?: string
  solicitudNueva: boolean              // siempre false
  descripcionSolicitud?: string
  agotadoConfirmado: boolean
  descripcionAgotado?: string
  guardada: boolean
  idRepGenerado?: string
  fechaGuardado?: string
}
export type BorradorAccion = { descripcion: string; guardada: boolean; idRepGenerado?: string; fechaGuardado?: string }
export type BorradorContenido = { modelo?: string; filas: BorradorFila[]; otros: BorradorAccion[] }

export function capturar(e: EstadoFormulario): BorradorContenido
export function borradorVacio(b: BorradorContenido): boolean
export function serializar(e: EstadoFormulario): string | null
export function leerBorrador(json: string | null): BorradorContenido | null
export function tieneGuardadas(b: BorradorContenido): boolean
export function aplicarBorrador(e: EstadoFormulario, b: BorradorContenido): EstadoFormulario
```

- Produces (W5, en `estado.ts`): las acciones `{ tipo: 'REEMPLAZAR'; estado: EstadoFormulario }` y `{ tipo: 'DESBLOQUEAR_BORRADAS'; idsExistentes: string[] }` implementadas en `reducir` (sus tipos ya están en `AccionFormulario` desde la Task 6).

**Ficha** (`formulario.md`, sección "Borrador"): "Forma exacta (mismos nombres que el JavaFX)…"; "Qué se captura…"; "Recuperación, después de cargar componentes y solicitudes…"; "JSON ilegible o fallo al leerlo…" (parte pura); "Filas guardadas borradas por otro…" (parte pura); de "Cabecera y avisos", "El borrador solo fija el modelo si el combo no está deshabilitado…"; de "Solicitudes ya guardadas", "Una fila con solicitud activa ignora lo que traiga el borrador…"; de "Guardar fila", "El estado "guardada" vive **solo en el borrador**…"; calco "El borrador restaura la cantidad sin revalidarla…".

Reglas que fija esta tarea (para quien la ejecute sin ver las demás):
- Al escribir, el objeto se construye con las claves **en el orden de declaración del cliente de escritorio** (`prefijo, idCom, cantidad, reutilizado, observacion, solicitudNueva, descripcionSolicitud, agotadoConfirmado, descripcionAgotado, guardada, idRepGenerado, fechaGuardado`; arriba `modelo, filas, otros`); booleanos y números siempre presentes; las cadenas sin valor se dejan a `undefined` y `JSON.stringify` las descarta.
- Cada fila del borrador se aplica a la **primera** fila del estado con ese `prefijo`. Se ignora si la fila tiene `solicitud` (activa en el servidor) o no tiene SKU para el modelo (`idCom === null`).
- Agotado confirmado recuperado: `agotado = { descripcion, registrado: false }`, todos los controles deshabilitados, y `cantidad` = la del borrador acotada al stock **actual** del SKU (0 si ahora no hay stock); el texto de la sub-fila lo calcula `subFila` con el stock actual.
- Fila normal recuperada: `reutilizado`, `cantidad` (mínimo 0, sin mirar el stock) y observación; de los controles solo se recalcula `menos` (`cantidad > 0`), como hace la referencia.
- `aplicarBorrador` solo usa `reducir` para `CAMBIAR_MODELO` (así hereda el filtro de SKU y el recálculo de OTRAS ACCIONES); al final restaura `revision` y `volcados` del estado de entrada y pone `borradorRecuperado = true`.

- [x] **Step 1: Tests de captura, serialización y lectura (fallan)**

Crear `src/modules/taller/formulario/borrador.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { BORRADOR_JAVAFX, agrupados, componente, solicitudAsignacion } from '../test/fabrica'
import { aplicarBorrador, borradorVacio, capturar, leerBorrador, serializar, tieneGuardadas, type BorradorContenido, type BorradorFila } from './borrador'
import { estadoInicial, reducir, subFila, type AccionFormulario, type DatosNuevo, type EstadoFormulario } from './estado'

const datos = (parcial: Partial<DatosNuevo> = {}): DatosNuevo => ({
  modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes: [], incidencia: null,
  modeloTelefono: null, ...parcial,
})
const aplicarAcciones = (e: EstadoFormulario, ...acciones: AccionFormulario[]) => acciones.reduce(reducir, e)
const conModelo13 = () => reducir(estadoInicial(datos()), { tipo: 'CAMBIAR_MODELO', modelo: '13' })
const fila = (e: EstadoFormulario, prefijo: string) => e.filas.find((f) => f.prefijo === prefijo)!
const filaBorrador = (parcial: Partial<BorradorFila> & { prefijo: string }): BorradorFila => ({
  idCom: -1, cantidad: 0, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: false, ...parcial,
})
const contenido = (parcial: Partial<BorradorContenido>): BorradorContenido => ({ filas: [], otros: [], ...parcial })

describe('borrador: capturar y serializar (mismo JSON que el cliente de escritorio)', () => {
  it('ida y vuelta: aplicar BORRADOR_JAVAFX y volver a capturar da el mismo contenido', () => {
    const leido = leerBorrador(BORRADOR_JAVAFX)!
    const e = aplicarBorrador(estadoInicial(datos()), leido)
    expect(capturar(e)).toEqual(JSON.parse(BORRADOR_JAVAFX))
    // mismas claves y en el mismo orden: la cadena es idéntica a la del cliente de escritorio
    expect(serializar(e)).toBe(BORRADOR_JAVAFX)
  })
  it('serializar omite cadenas nulas y escribe solicitudNueva false', () => {
    const e = aplicarAcciones(conModelo13(), { tipo: 'SUMAR', prefijo: 'bat' })
    const json = serializar(e)!
    expect(json).toBe('{"modelo":"13","filas":[{"prefijo":"bat","idCom":101,"cantidad":1,"reutilizado":false,"solicitudNueva":false,"agotadoConfirmado":false,"guardada":false}],"otros":[]}')
    expect(json).not.toContain('null')
    expect(json).not.toContain('observacion')
  })
  it('fila guardada se captura sin observación', () => {
    const e = aplicarAcciones(
      conModelo13(),
      { tipo: 'SUMAR', prefijo: 'bat' },
      { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'Batería hinchada' },
      { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_5', fecha: '16/09 09:15' },
    )
    expect(capturar(e).filas).toEqual([
      { prefijo: 'bat', idCom: 101, cantidad: 1, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: true, idRepGenerado: 'R20260916_5', fechaGuardado: '16/09 09:15' },
    ])
  })
  it('fila sin nada se omite; con solo observación o solo Reutilizado, no', () => {
    const e = aplicarAcciones(
      conModelo13(),
      { tipo: 'PONER_OBSERVACION', prefijo: 'lcd', texto: 'Pantalla con líneas verticales' },
      { tipo: 'MARCAR_REUTILIZADO', prefijo: 'cam', valor: true },
    )
    expect(capturar(e).filas.map((f) => f.prefijo)).toEqual(['lcd', 'cam'])
    expect(capturar(e).filas[0]).toMatchObject({ cantidad: 0, observacion: 'Pantalla con líneas verticales' })
    expect(capturar(e).filas[1]).toMatchObject({ cantidad: 0, reutilizado: true })
  })
  it('un agotado confirmado en esta sesión se captura; una solicitud del servidor no se captura como agotado', () => {
    const local = aplicarAcciones(
      reducir(estadoInicial(datos()), { tipo: 'CAMBIAR_MODELO', modelo: '14' }),
      { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: '  Batería original  ' },
    )
    expect(capturar(local).filas).toEqual([
      { prefijo: 'bat', idCom: 102, cantidad: 0, reutilizado: false, solicitudNueva: false, agotadoConfirmado: true, descripcionAgotado: 'Batería original', guardada: false },
    ])
    const delServidor = estadoInicial(datos({ solicitudes: [solicitudAsignacion({ idCom: 102, descripcionSolicitud: 'Batería original' })] }))
    expect(fila(delServidor, 'bat').solicitud).not.toBeNull()
    expect(capturar(delServidor).filas).toEqual([])
    const reutilizada = reducir(delServidor, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    expect(capturar(reutilizada).filas).toEqual([
      { prefijo: 'bat', idCom: 102, cantidad: 0, reutilizado: true, solicitudNueva: false, agotadoConfirmado: false, guardada: false },
    ])
  })
  it('acciones: con texto o guardadas, descripción recortada', () => {
    let e = aplicarAcciones(conModelo13(), { tipo: 'ANADIR_ACCION' })
    e = reducir(e, { tipo: 'ESCRIBIR_ACCION', id: e.otros[0].id, texto: '  Limpieza del conector de carga ' })
    e = reducir(e, { tipo: 'ACCION_GUARDADA', id: e.otros[0].id, idRep: 'R20260916_6', fecha: '16/09 09:20' })
    e = reducir(e, { tipo: 'ANADIR_ACCION' })
    e = reducir(e, { tipo: 'ESCRIBIR_ACCION', id: e.otros[1].id, texto: ' Ajuste de tornillería' })
    e = reducir(e, { tipo: 'ANADIR_ACCION' }) // línea vacía: no se captura
    expect(capturar(e).otros).toEqual([
      { descripcion: 'Limpieza del conector de carga', guardada: true, idRepGenerado: 'R20260916_6', fechaGuardado: '16/09 09:20' },
      { descripcion: 'Ajuste de tornillería', guardada: false },
    ])
  })
  it('borradorVacio ignora el modelo; serializar devuelve null', () => {
    const e = conModelo13()
    expect(capturar(e)).toEqual({ modelo: '13', filas: [], otros: [] })
    expect(borradorVacio(capturar(e))).toBe(true)
    expect(serializar(e)).toBeNull()
    expect(serializar(estadoInicial(datos()))).toBeNull()
    expect(borradorVacio(contenido({ otros: [{ descripcion: 'x', guardada: false }] }))).toBe(false)
  })
})

describe('leerBorrador', () => {
  it('null, cadena vacía, JSON corrupto u objeto sin filas ni otros → null (formulario limpio)', () => {
    expect(leerBorrador(null)).toBeNull()
    expect(leerBorrador('')).toBeNull()
    expect(leerBorrador('   ')).toBeNull()
    expect(leerBorrador('{"modelo":"13","filas":[{"prefijo":"bat"')).toBeNull()
    expect(leerBorrador('no es json')).toBeNull()
    expect(leerBorrador('null')).toBeNull()
    expect(leerBorrador('[]')).toBeNull()
    expect(leerBorrador('"13"')).toBeNull()
    expect(leerBorrador('{}')).toBeNull()
    expect(leerBorrador('{"modelo":"13"}')).toBeNull()
    expect(leerBorrador('{"modelo":"13","filas":[],"otros":[]}')).toBeNull()
    expect(leerBorrador('{"filas":"bat","otros":[]}')).toBeNull()
  })
  it('un borrador corrupto deja el formulario limpio: nada que aplicar', () => {
    const e = estadoInicial(datos())
    const leido = leerBorrador('{"filas":[{"prefijo":')
    expect(leido).toBeNull()
    // quien llama (useBorrador) no aplica nada: el estado sigue siendo el inicial, sin banda
    expect(e.borradorRecuperado).toBe(false)
    expect(e.filas.every((f) => f.cantidad === 0 && f.guardada === null)).toBe(true)
  })
  it('tolera claves ausentes (Gson omite nulos) y descarta lo que no tiene forma de fila o de acción', () => {
    const b = leerBorrador('{"filas":[{"prefijo":"lcd","cantidad":2},{"idCom":5},"basura",null],"otros":[{"descripcion":"Ajuste de tornillería"},{"guardada":true},7]}')!
    expect(b).toEqual({
      modelo: undefined,
      filas: [{ prefijo: 'lcd', idCom: -1, cantidad: 2, reutilizado: false, observacion: undefined, solicitudNueva: false, descripcionSolicitud: undefined, agotadoConfirmado: false, descripcionAgotado: undefined, guardada: false, idRepGenerado: undefined, fechaGuardado: undefined }],
      otros: [{ descripcion: 'Ajuste de tornillería', guardada: false, idRepGenerado: undefined, fechaGuardado: undefined }, { descripcion: '', guardada: true, idRepGenerado: undefined, fechaGuardado: undefined }],
    })
    expect(leerBorrador('{"otros":[{"descripcion":"Ajuste de tornillería","guardada":false}]}')!.filas).toEqual([])
  })
  it('tieneGuardadas mira filas y acciones', () => {
    expect(tieneGuardadas(leerBorrador(BORRADOR_JAVAFX)!)).toBe(true)
    expect(tieneGuardadas(contenido({ filas: [filaBorrador({ prefijo: 'lcd', cantidad: 1 })] }))).toBe(false)
    expect(tieneGuardadas(contenido({ otros: [{ descripcion: 'x', guardada: true }] }))).toBe(true)
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/borrador.test.ts
```
Expected: FALLA (no existe `./borrador`).

- [x] **Step 2: `borrador.ts` — tipos, captura, serialización y lectura**

Crear `src/modules/taller/formulario/borrador.ts`:

```ts
import type { EstadoFormulario, FilaEstado, OtraAccion } from './estado'

/** Borrador del formulario con el MISMO JSON que escribe el cliente de escritorio (BorradorContenido, campo a campo): los dos
 *  clientes leen y escriben el mismo borrador de una asignación. El servidor lo guarda opaco. */
export type BorradorFila = {
  prefijo: string
  idCom: number                        // -1 sin elegir
  cantidad: number
  reutilizado: boolean
  observacion?: string
  solicitudNueva: boolean              // siempre false: se conserva solo por compatibilidad
  descripcionSolicitud?: string
  agotadoConfirmado: boolean
  descripcionAgotado?: string
  guardada: boolean
  idRepGenerado?: string
  fechaGuardado?: string
}
export type BorradorAccion = { descripcion: string; guardada: boolean; idRepGenerado?: string; fechaGuardado?: string }
export type BorradorContenido = { modelo?: string; filas: BorradorFila[]; otros: BorradorAccion[] }

// Las claves van en el orden de declaración del cliente de escritorio: así la cadena serializada es idéntica a la suya.
// Booleanos y números siempre presentes; las cadenas sin valor quedan a undefined y JSON.stringify las descarta.
function capturarFila(fila: FilaEstado): BorradorFila | null {
  if (fila.guardada !== null) {
    return {
      prefijo: fila.prefijo, idCom: fila.idCom ?? -1, cantidad: fila.cantidad, reutilizado: fila.reutilizado, observacion: undefined,
      solicitudNueva: false, descripcionSolicitud: undefined, agotadoConfirmado: false, descripcionAgotado: undefined, guardada: true,
      idRepGenerado: fila.guardada.idRep, fechaGuardado: fila.guardada.fecha,
    }
  }
  const agotadoNuevo = fila.agotado !== null
  if (fila.cantidad === 0 && !fila.reutilizado && fila.observacion === null && !agotadoNuevo) return null
  return {
    prefijo: fila.prefijo, idCom: fila.idCom ?? -1, cantidad: fila.cantidad, reutilizado: fila.reutilizado,
    observacion: fila.observacion ?? undefined, solicitudNueva: false, descripcionSolicitud: undefined, agotadoConfirmado: agotadoNuevo,
    descripcionAgotado: fila.agotado?.descripcion ?? undefined, guardada: false, idRepGenerado: undefined, fechaGuardado: undefined,
  }
}

function capturarAccion(a: OtraAccion): BorradorAccion | null {
  const texto = a.texto.trim()
  if (texto === '' && a.guardada === null) return null
  return { descripcion: texto, guardada: a.guardada !== null, idRepGenerado: a.guardada?.idRep, fechaGuardado: a.guardada?.fecha }
}

export function capturar(e: EstadoFormulario): BorradorContenido {
  return {
    modelo: e.modelo ?? undefined,
    filas: e.filas.map(capturarFila).filter((f): f is BorradorFila => f !== null),
    otros: e.otros.map(capturarAccion).filter((a): a is BorradorAccion => a !== null),
  }
}

/** Vacío = sin filas y sin acciones; el modelo solo no cuenta (se autorrellena al abrir). */
export function borradorVacio(b: BorradorContenido): boolean {
  return b.filas.length === 0 && b.otros.length === 0
}

/** Cadena para PUT …/borrador, o null si el borrador está vacío (entonces toca DELETE). */
export function serializar(e: EstadoFormulario): string | null {
  const b = capturar(e)
  return borradorVacio(b) ? null : JSON.stringify(b)
}

const cadena = (v: unknown): string | undefined => (typeof v === 'string' ? v : undefined)
const entero = (v: unknown, porDefecto: number): number => (typeof v === 'number' && Number.isFinite(v) ? Math.trunc(v) : porDefecto)
const esObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)

function leerFila(v: unknown): BorradorFila | null {
  if (!esObjeto(v) || typeof v.prefijo !== 'string') return null
  return {
    prefijo: v.prefijo, idCom: entero(v.idCom, -1), cantidad: entero(v.cantidad, 0), reutilizado: v.reutilizado === true,
    observacion: cadena(v.observacion), solicitudNueva: false, descripcionSolicitud: cadena(v.descripcionSolicitud),
    agotadoConfirmado: v.agotadoConfirmado === true, descripcionAgotado: cadena(v.descripcionAgotado), guardada: v.guardada === true,
    idRepGenerado: cadena(v.idRepGenerado), fechaGuardado: cadena(v.fechaGuardado),
  }
}

function leerAccion(v: unknown): BorradorAccion | null {
  if (!esObjeto(v)) return null
  return { descripcion: cadena(v.descripcion) ?? '', guardada: v.guardada === true, idRepGenerado: cadena(v.idRepGenerado), fechaGuardado: cadena(v.fechaGuardado) }
}

/** null si json es null o vacío, no es JSON, no es un objeto con listas o el borrador está vacío: en todos esos casos el
 *  formulario abre limpio, sin error y sin banda. Tolera claves ausentes (el cliente de escritorio omite los nulos). */
export function leerBorrador(json: string | null): BorradorContenido | null {
  if (json === null || json.trim() === '') return null
  let crudo: unknown
  try {
    crudo = JSON.parse(json)
  } catch {
    return null
  }
  if (!esObjeto(crudo)) return null
  const filas = crudo.filas ?? []
  const otros = crudo.otros ?? []
  if (!Array.isArray(filas) || !Array.isArray(otros)) return null
  const b: BorradorContenido = {
    modelo: cadena(crudo.modelo),
    filas: filas.map(leerFila).filter((f): f is BorradorFila => f !== null),
    otros: otros.map(leerAccion).filter((a): a is BorradorAccion => a !== null),
  }
  return borradorVacio(b) ? null : b
}

export function tieneGuardadas(b: BorradorContenido): boolean {
  return b.filas.some((f) => f.guardada) || b.otros.some((a) => a.guardada)
}

/** Provisional: se completa en el Step 4. */
export function aplicarBorrador(e: EstadoFormulario, b: BorradorContenido): EstadoFormulario {
  void b
  return e
}
```

```bash
npx vitest run src/modules/taller/formulario/borrador.test.ts
```
Expected: pasan todos menos "ida y vuelta…" (que necesita `aplicarBorrador`).

- [x] **Step 3: Tests de `aplicarBorrador` (fallan)**

Añadir al final de `src/modules/taller/formulario/borrador.test.ts`:

```ts
describe('aplicarBorrador (recuperación, después de cargar componentes y solicitudes)', () => {
  it('aplica modelo, fila guardada, fila normal, agotado confirmado y acciones', () => {
    const e = aplicarBorrador(estadoInicial(datos()), leerBorrador(BORRADOR_JAVAFX)!)
    expect(e.modelo).toBe('13')
    expect(fila(e, 'bat')).toMatchObject({ idCom: 101, cantidad: 1, reutilizado: false, guardada: { idRep: 'R20260916_5', fecha: '16/09 09:15' } })
    expect(fila(e, 'bat').controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: false })
    expect(fila(e, 'lcd')).toMatchObject({ idCom: 111, cantidad: 1, observacion: 'Pantalla con líneas verticales', guardada: null, agotado: null })
    expect(fila(e, 'lcd').controles.menos).toBe(true)
    expect(fila(e, 'cam')).toMatchObject({ idCom: 121, cantidad: 4, agotado: { descripcion: 'Cámara trasera completa', registrado: false } })
    expect(fila(e, 'cha')).toMatchObject({ cantidad: 0, guardada: null, agotado: null })
  })
  it('el modelo no se toca si está bloqueado o no existe entre las opciones', () => {
    const bloqueado = estadoInicial(datos({ modeloTelefono: '14' }))
    expect(bloqueado.modeloBloqueado).toBe(true)
    expect(aplicarBorrador(bloqueado, contenido({ modelo: '13', filas: [filaBorrador({ prefijo: 'lcd', cantidad: 1 })] })).modelo).toBe('14')
    const libre = estadoInicial(datos())
    expect(aplicarBorrador(libre, contenido({ modelo: 'xr', filas: [filaBorrador({ prefijo: 'lcd', cantidad: 1 })] })).modelo).toBeNull()
    expect(aplicarBorrador(libre, contenido({ filas: [filaBorrador({ prefijo: 'lcd', cantidad: 1 })] })).modelo).toBeNull()
  })
  it('SKU fuera de las opciones deja el SKU por defecto; dentro, lo preselecciona y recalcula "+"', () => {
    const fuera = aplicarBorrador(estadoInicial(datos()), contenido({ modelo: '13', filas: [filaBorrador({ prefijo: 'bat', idCom: 102, cantidad: 1 })] }))
    expect(fila(fuera, 'bat')).toMatchObject({ idCom: 101, cantidad: 1 })
    // sin modelo, todas las baterías activas son opción: bati14 (stock 0) se preselecciona y "+" queda deshabilitado
    const dentro = aplicarBorrador(estadoInicial(datos()), contenido({ filas: [filaBorrador({ prefijo: 'bat', idCom: 102, reutilizado: true })] }))
    expect(dentro.modelo).toBeNull()
    expect(fila(dentro, 'bat')).toMatchObject({ idCom: 102, reutilizado: true })
    expect(fila(dentro, 'bat').controles.mas).toBe(false)
    // un SKU inactivo (bati12) nunca es opción
    const inactivo = aplicarBorrador(estadoInicial(datos()), contenido({ filas: [filaBorrador({ prefijo: 'bat', idCom: 104, cantidad: 1 })] }))
    expect(fila(inactivo, 'bat').idCom).toBe(101)
  })
  it('fila guardada con id y fecha ausentes → "?" y ""', () => {
    const e = aplicarBorrador(estadoInicial(datos()), contenido({ modelo: '13', filas: [filaBorrador({ prefijo: 'bat', idCom: 101, reutilizado: true, guardada: true })] }))
    expect(fila(e, 'bat')).toMatchObject({ cantidad: 0, reutilizado: true, guardada: { idRep: '?', fecha: '' } })
  })
  it('agotado confirmado usa el stock actual para la variante', () => {
    const b = contenido({ modelo: '13', filas: [filaBorrador({ prefijo: 'cam', idCom: 121, cantidad: 4, agotadoConfirmado: true, descripcionAgotado: 'Cámara trasera completa' })] })
    const conStock = aplicarBorrador(estadoInicial(datos()), b)
    expect(subFila(conStock, fila(conStock, 'cam'))).toEqual({ tipo: 'confirmada', texto: '✓  4 uds. se descontarán al guardar — solicitud pendiente — Cámara trasera completa', lapizHabilitado: true })
    const a = agrupados()
    a.cam = [componente({ idCom: 121, tipo: 'cami13', stock: 0, stockMinimo: 1 })]
    const sinStock = aplicarBorrador(estadoInicial(datos({ agrupados: a })), b)
    expect(fila(sinStock, 'cam').cantidad).toBe(0)
    expect(subFila(sinStock, fila(sinStock, 'cam'))).toEqual({ tipo: 'confirmada', texto: '✓  Solicitud de reposición pendiente — Cámara trasera completa', lapizHabilitado: true })
    expect(fila(sinStock, 'cam').controles).toEqual({ mas: false, menos: false, reutilizado: false, sku: false, observacion: false })
  })
  it('fila normal restaura la cantidad sin revalidar contra el stock (mínimo 0)', () => {
    const e = aplicarBorrador(estadoInicial(datos()), contenido({ modelo: '13', filas: [filaBorrador({ prefijo: 'lcd', idCom: 111, cantidad: 3 }), filaBorrador({ prefijo: 'cha', idCom: 131, cantidad: -2, observacion: 'Chasis doblado' })] }))
    expect(fila(e, 'lcd').cantidad).toBe(3) // lcdi13 solo tiene 1 en stock
    expect(fila(e, 'cha')).toMatchObject({ cantidad: 0, observacion: 'Chasis doblado' })
  })
  it('ignora la fila con solicitud activa y la fila sin SKU para el modelo', () => {
    const conSolicitud = estadoInicial(datos({ solicitudes: [solicitudAsignacion({ idCom: 102 })] }))
    const e = aplicarBorrador(conSolicitud, contenido({ filas: [filaBorrador({ prefijo: 'bat', idCom: 102, cantidad: 2, observacion: 'no debe entrar' }), filaBorrador({ prefijo: 'cam', cantidad: 1 })] }))
    expect(fila(e, 'bat')).toEqual(fila(conSolicitud, 'bat'))
    expect(fila(e, 'cam')).toEqual(fila(conSolicitud, 'cam')) // modelo 14: no hay cámara → sin SKU
    expect(e.borradorRecuperado).toBe(true)
  })
  it('un prefijo que no existe en el formulario se ignora', () => {
    const e = aplicarBorrador(estadoInicial(datos()), contenido({ modelo: '13', filas: [filaBorrador({ prefijo: 'g', idCom: 141, cantidad: 1 })] }))
    expect(e.filas.every((f) => f.cantidad === 0)).toBe(true)
  })
  it('acciones: una línea por descripción no vacía, guardadas bloqueadas', () => {
    const e = aplicarBorrador(estadoInicial(datos()), contenido({
      modelo: '13',
      otros: [
        { descripcion: '  Limpieza del conector de carga ', guardada: true, idRepGenerado: 'R20260916_6', fechaGuardado: '16/09 09:20' },
        { descripcion: '   ', guardada: false },
        { descripcion: 'Ajuste de tornillería', guardada: false },
        { descripcion: 'Soldadura de antena', guardada: true },
      ],
    }))
    expect(e.otros.map((a) => [a.texto, a.origen, a.guardada])).toEqual([
      ['Limpieza del conector de carga', 'nueva', { idRep: 'R20260916_6', fecha: '16/09 09:20' }],
      ['Ajuste de tornillería', 'nueva', null],
      ['Soldadura de antena', 'nueva', { idRep: '?', fecha: '' }],
    ])
    expect(new Set(e.otros.map((a) => a.id)).size).toBe(3)
    expect(e.siguienteIdAccion).toBeGreaterThan(Math.max(...e.otros.map((a) => a.id)))
    expect(e.otros.every((a) => !a.confirmando && !a.guardando)).toBe(true)
  })
  it('marca borradorRecuperado y no sube revision ni volcados', () => {
    const base = estadoInicial(datos())
    const e = aplicarBorrador(base, leerBorrador(BORRADOR_JAVAFX)!)
    expect(e.borradorRecuperado).toBe(true)
    expect(e.revision).toBe(base.revision)
    expect(e.volcados).toBe(base.volcados)
    expect(base.borradorRecuperado).toBe(false) // no muta la entrada
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/borrador.test.ts
```
Expected: FALLAN "ida y vuelta…" y los de `aplicarBorrador` (hoy devuelve el estado sin tocar).

- [x] **Step 4: Implementar `aplicarBorrador`**

En `src/modules/taller/formulario/borrador.ts`, sustituir la primera línea (el import de `./estado`) por:

```ts
import { componenteDe, reducir, type ControlesFila, type EstadoFormulario, type FilaEstado, type OtraAccion } from './estado'
```

y la función `aplicarBorrador` provisional (entera, con su `void b`) por:

```ts
const CONTROLES_BLOQUEADOS: ControlesFila = { mas: false, menos: false, reutilizado: false, sku: false, observacion: false }

/** Una fila del borrador sobre la fila del formulario (calco de FilaUI.aplicarBorrador). */
function aplicarFila(fila: FilaEstado, f: BorradorFila): FilaEstado {
  // Una solicitud ya guardada en el servidor manda sobre el borrador; sin SKU para el modelo no hay nada que restaurar.
  if (fila.solicitud !== null || fila.idCom === null) return fila
  // El SKU solo se preselecciona si está entre las opciones actuales; si no, queda el SKU por defecto.
  const elegido = f.idCom > 0 ? fila.opciones.find((c) => c.idCom === f.idCom) : undefined
  const base: FilaEstado = elegido ? { ...fila, idCom: elegido.idCom, controles: { ...fila.controles, mas: elegido.stock > 0 } } : fila
  if (f.guardada) {
    return {
      ...base, cantidad: f.cantidad > 0 ? f.cantidad : base.cantidad, reutilizado: f.reutilizado || base.reutilizado, controles: CONTROLES_BLOQUEADOS,
      guardada: { idRep: f.idRepGenerado ?? '?', fecha: f.fechaGuardado ?? '' }, confirmandoGuardar: false, guardando: false, agotado: null,
      recibidoPendienteUso: false,
    }
  }
  if (f.agotadoConfirmado) {
    // Variante según el stock ACTUAL: lo que se descontará nunca supera lo que hay ahora (0 si el SKU se quedó sin stock).
    const stock = componenteDe(base)?.stock ?? 0
    return {
      ...base, cantidad: Math.min(Math.max(0, f.cantidad), Math.max(0, stock)), reutilizado: false, observacion: null, controles: CONTROLES_BLOQUEADOS,
      agotado: { descripcion: f.descripcionAgotado ?? null, registrado: false }, confirmandoGuardar: false, recibidoPendienteUso: false,
    }
  }
  // Normal: "Reutilizado", cantidad (mínimo 0, SIN revalidar contra el stock) y observación. De los controles solo se
  // recalcula "-", como en la referencia.
  const cantidad = Math.max(0, f.cantidad)
  const observacion = f.observacion !== undefined && f.observacion.trim() !== '' ? f.observacion : base.observacion
  return {
    ...base, reutilizado: f.reutilizado, cantidad, observacion, controles: { ...base.controles, menos: cantidad > 0 }, confirmandoGuardar: false,
    recibidoPendienteUso: base.recibidoPendienteUso && cantidad === 0 && !f.reutilizado,
  }
}

/** Devuelve el estado con el borrador aplicado y borradorRecuperado = true; no toca revision ni volcados (aplicar un borrador
 *  recuperado no debe reprogramar el autoguardado). Orden de la referencia: modelo, filas, acciones. */
export function aplicarBorrador(e: EstadoFormulario, b: BorradorContenido): EstadoFormulario {
  let estado = e
  // El borrador solo fija el modelo si el combo no está deshabilitado y el modelo existe entre las opciones.
  if (b.modelo !== undefined && b.modelo !== estado.modelo && !estado.modeloBloqueado && estado.modelos.includes(b.modelo)) {
    estado = reducir(estado, { tipo: 'CAMBIAR_MODELO', modelo: b.modelo })
  }
  const filas = [...estado.filas]
  for (const f of b.filas) {
    const i = filas.findIndex((fila) => fila.prefijo === f.prefijo) // la PRIMERA fila de ese prefijo
    if (i >= 0) filas[i] = aplicarFila(filas[i], f)
  }
  let siguienteIdAccion = estado.siguienteIdAccion
  const recuperadas: OtraAccion[] = []
  for (const a of b.otros) {
    const descripcion = a.descripcion.trim()
    if (descripcion === '') continue
    recuperadas.push({
      id: siguienteIdAccion++, texto: descripcion, origen: 'nueva',
      guardada: a.guardada ? { idRep: a.idRepGenerado ?? '?', fecha: a.fechaGuardado ?? '' } : null, confirmando: false, guardando: false,
    })
  }
  return {
    ...estado, filas, otros: [...estado.otros, ...recuperadas], siguienteIdAccion, borradorRecuperado: true, revision: e.revision,
    volcados: e.volcados,
  }
}
```

```bash
npx vitest run src/modules/taller/formulario/borrador.test.ts
```
Expected: PASS (todos). Si "ida y vuelta…" falla solo en `serializar(e)).toBe(BORRADOR_JAVAFX)`, el orden de claves de `capturarFila`/`capturarAccion` no coincide con el de `BORRADOR_JAVAFX`: se corrige aquí (el orden de la fábrica es el del cliente de escritorio).

- [x] **Step 5: Tests de `REEMPLAZAR` y `DESBLOQUEAR_BORRADAS` (fallan)**

En `src/modules/taller/formulario/estado.test.ts`, asegurarse de que los imports del fichero incluyen `agrupados` (de `'../test/fabrica'`) y `estadoInicial`, `reducir`, `type DatosNuevo`, `type EstadoFormulario` (de `'./estado'`) —añadir los que falten a los imports ya existentes, sin duplicarlos— y añadir al final:

```ts
describe('acciones del borrador en el reductor (REEMPLAZAR y DESBLOQUEAR_BORRADAS)', () => {
  const datosBorrador: DatosNuevo = {
    modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes: [], incidencia: null, modeloTelefono: null,
  }
  /** Modelo 13; batería guardada (R20260916_5) con observación; pantalla guardada (R20260916_7) como Reutilizado; una acción
   *  guardada (R20260916_6) y otra pendiente. */
  function conGuardadas(): EstadoFormulario {
    let e = reducir(estadoInicial(datosBorrador), { tipo: 'CAMBIAR_MODELO', modelo: '13' })
    e = reducir(e, { tipo: 'SUMAR', prefijo: 'bat' })
    e = reducir(e, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'Batería hinchada' })
    e = reducir(e, { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_5', fecha: '16/09 09:15' })
    e = reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'lcd', valor: true })
    e = reducir(e, { tipo: 'FILA_GUARDADA', prefijo: 'lcd', idRep: 'R20260916_7', fecha: '16/09 09:18' })
    e = reducir(e, { tipo: 'ANADIR_ACCION' })
    e = reducir(e, { tipo: 'ESCRIBIR_ACCION', id: e.otros[0].id, texto: 'Limpieza del conector de carga' })
    e = reducir(e, { tipo: 'ACCION_GUARDADA', id: e.otros[0].id, idRep: 'R20260916_6', fecha: '16/09 09:20' })
    e = reducir(e, { tipo: 'ANADIR_ACCION' })
    e = reducir(e, { tipo: 'ESCRIBIR_ACCION', id: e.otros[1].id, texto: 'Ajuste de tornillería' })
    return e
  }
  const filaDe = (e: EstadoFormulario, prefijo: string) => e.filas.find((f) => f.prefijo === prefijo)!

  it('REEMPLAZAR devuelve el estado recibido tal cual, sin subir revision', () => {
    const actual = estadoInicial(datosBorrador)
    const nuevo = { ...conGuardadas(), borradorRecuperado: true }
    expect(reducir(actual, { tipo: 'REEMPLAZAR', estado: nuevo })).toBe(nuevo)
  })
  it('DESBLOQUEAR_BORRADAS devuelve a editable las filas cuyo id ya no existe y sube volcados', () => {
    const antes = conGuardadas()
    const e = reducir(antes, { tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: ['R20260916_7', 'R20260916_6'] })
    expect(filaDe(e, 'bat')).toMatchObject({ guardada: null, cantidad: 0, reutilizado: false, observacion: null, confirmandoGuardar: false, guardando: false })
    // bati13 tiene stock 5: "+" habilitado, "-" no (está a 0), y el resto de controles de vuelta
    expect(filaDe(e, 'bat').controles).toEqual({ mas: true, menos: false, reutilizado: true, sku: true, observacion: true })
    expect(filaDe(e, 'bat').idCom).toBe(101)
    expect(filaDe(e, 'lcd').guardada).toEqual({ idRep: 'R20260916_7', fecha: '16/09 09:18' })
    expect(e.otros[0].guardada).toEqual({ idRep: 'R20260916_6', fecha: '16/09 09:20' })
    expect(e.volcados).toBe(antes.volcados + 1)
  })
  it('DESBLOQUEAR_BORRADAS sobre una acción conserva su texto y la deja pendiente', () => {
    const antes = conGuardadas()
    const e = reducir(antes, { tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: ['R20260916_5', 'R20260916_7'] })
    expect(e.otros[0]).toMatchObject({ texto: 'Limpieza del conector de carga', guardada: null, confirmando: false, guardando: false })
    expect(e.otros[1]).toEqual(antes.otros[1])
    expect(filaDe(e, 'bat').guardada).not.toBeNull()
    expect(e.volcados).toBe(antes.volcados + 1)
  })
  it('una fila desbloqueada con el SKU sin stock deja "+" deshabilitado', () => {
    let e = reducir(estadoInicial(datosBorrador), { tipo: 'CAMBIAR_MODELO', modelo: '14' })
    e = reducir(e, { tipo: 'MARCAR_REUTILIZADO', prefijo: 'bat', valor: true })
    e = reducir(e, { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_8', fecha: '16/09 10:00' })
    e = reducir(e, { tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: [] })
    expect(filaDe(e, 'bat').controles).toEqual({ mas: false, menos: false, reutilizado: true, sku: true, observacion: true })
  })
  it('si todo sigue existiendo (o no había nada guardado) devuelve el mismo estado y no sube volcados', () => {
    const antes = conGuardadas()
    expect(reducir(antes, { tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: ['R20260916_5', 'R20260916_6', 'R20260916_7', 'R20260916_99'] })).toBe(antes)
    const limpio = estadoInicial(datosBorrador)
    expect(reducir(limpio, { tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: [] })).toBe(limpio)
  })
  it('una fila o acción recuperada sin id ("?") se desbloquea siempre', () => {
    let e = reducir(estadoInicial(datosBorrador), { tipo: 'CAMBIAR_MODELO', modelo: '13' })
    e = reducir(e, { tipo: 'SUMAR', prefijo: 'cam' })
    e = reducir(e, { tipo: 'FILA_GUARDADA', prefijo: 'cam', idRep: '?', fecha: '' })
    e = reducir(e, { tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: ['R20260916_5'] })
    expect(filaDe(e, 'cam').guardada).toBeNull()
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/estado.test.ts
```
Expected: FALLAN los seis nuevos (`reducir` devuelve hoy el mismo estado para estas dos acciones); los de las Tasks 6–8 siguen en verde.

- [x] **Step 6: Implementar las dos acciones en `estado.ts`**

En `src/modules/taller/formulario/estado.ts`, añadir esta función auxiliar justo **antes** de `export function reducir` (usa `componenteDe`, ya definido en el fichero por la Task 6; si está declarado con `function`, el orden no importa):

```ts
/** Filas y acciones guardadas cuyo idRep ya no existe (las borró otro usuario) vuelven a editables: la fila, como recién
 *  pintada (cantidad 0, sin "Reutilizado", sin observación, "+" según el stock del SKU); la acción conserva su texto. Si no
 *  cambia nada devuelve el MISMO estado; si cambia, sube `volcados` para que el borrador se reescriba al momento. */
function desbloquearBorradas(estado: EstadoFormulario, idsExistentes: string[]): EstadoFormulario {
  const existen = new Set(idsExistentes)
  let cambio = false
  const filas = estado.filas.map((fila): FilaEstado => {
    if (fila.guardada === null || existen.has(fila.guardada.idRep)) return fila
    cambio = true
    return {
      ...fila, guardada: null, cantidad: 0, reutilizado: false, observacion: null, confirmandoGuardar: false, guardando: false,
      controles: { mas: (componenteDe(fila)?.stock ?? 0) > 0, menos: false, reutilizado: true, sku: true, observacion: true },
    }
  })
  const otros = estado.otros.map((a): OtraAccion => {
    if (a.guardada === null || existen.has(a.guardada.idRep)) return a
    cambio = true
    return { ...a, guardada: null, confirmando: false, guardando: false }
  })
  return cambio ? { ...estado, filas, otros, volcados: estado.volcados + 1 } : estado
}
```

y, dentro del `switch (accion.tipo)` de `reducir`, añadir estos dos casos (si la Task 6 dejó un `default:` o una lista de casos "sin implementar" que devuelve `estado`, los dos tipos se quitan de esa lista y los casos van antes del `default:`):

```ts
    case 'REEMPLAZAR':
      // Resultado de aplicarBorrador: entra tal cual, sin subir revision (no debe reprogramar el autoguardado).
      return accion.estado
    case 'DESBLOQUEAR_BORRADAS':
      return desbloquearBorradas(estado, accion.idsExistentes)
```

(El primer parámetro de `reducir` se llama `estado` y el segundo `accion` según la firma del esqueleto; si en el fichero tienen otro nombre, se usan los del fichero.)

```bash
npx vitest run src/modules/taller/formulario/
```
Expected: PASS (`estado.test.ts`, `estado.solicitudes.test.ts`, `estado.edicion.test.ts` y `borrador.test.ts`).

- [x] **Step 7: `npm run check` en verde**

```bash
npm run check
```
Expected: lint (ningún `void` ni import sin uso en `borrador.ts`: la versión provisional del Step 2 ya no existe), typecheck y toda la suite en verde. `borrador.ts` y `estado.ts` no importan React, `Date` ni `fetch`.

- [x] **Step 8: Commit**

```bash
git add src/modules/taller/formulario/borrador.ts src/modules/taller/formulario/borrador.test.ts src/modules/taller/formulario/estado.ts src/modules/taller/formulario/estado.test.ts
git commit -m "feat(web): borrador del formulario con el mismo JSON que el cliente de escritorio"
```

---

### Task 10: Web — compartidos y deuda: `ComboNavy`, consultas silenciosas, avisos multilínea con retorno del foco, `unsubscribe`, `renderConRouter` y tokens del formulario

**Files:**
- Create: `src/shared/ui/ComboNavy.tsx`, `src/shared/ui/ComboNavy.test.tsx`, `src/test/render.test.tsx` (el test de `renderConRouter` necesita fichero propio)
- Modify: `src/shared/api/queryClient.ts`, `src/shared/api/queryClient.test.tsx`, `src/shared/session/expiracion.ts`, `src/shared/session/expiracion.test.ts`, `src/main.tsx` (solo se comprueba que sigue compilando), `src/shared/ui/AlertaProvider.tsx`, `src/shared/ui/AlertaProvider.test.tsx`, `src/test/render.tsx`, `src/shared/styles/tokens.css`

**Interfaces:**
- Consumes: `Popover`, `PopoverContent`, `PopoverTrigger` de `src/shared/ui/popover.tsx`; `cn` de `@/shared/lib/utils`; `Dialog*` de `src/shared/ui/dialog.tsx`.
- Produces (W8):

```ts
// src/shared/ui/ComboNavy.tsx
export type OpcionCombo = { valor: string; etiqueta: string; clase?: string }
export function ComboNavy(props: {
  valor: string | null
  opciones: OpcionCombo[]
  onChange: (valor: string) => void
  textoVacio: string                   // '— Selecciona modelo —' | '—'
  ancho: number                        // 180 | 170
  tamanoTexto?: 11 | 12                // por defecto 12
  visibles?: number                    // filas visibles de la lista (8 en SKU)
  disabled?: boolean
  'aria-label': string
}): JSX.Element
// Accesibilidad para tests: botón role="combobox" con el aria-label; lista role="listbox"; opciones role="option" (aria-selected).

// src/shared/api/queryClient.ts
declare module '@tanstack/react-query' { interface Register { queryMeta: { silenciarError?: boolean }; mutationMeta: { silenciarError?: boolean } } }
// QueryCache.onError: con meta.silenciarError no avisa de NADA (tampoco del corte de conexión: el banner basta).

// src/shared/session/expiracion.ts
export function onSesionExpirada(h: () => void): () => void               // devuelve unsubscribe (solo quita h si sigue siendo el actual)

// src/shared/ui/AlertaProvider.tsx
// mostrarError / mostrarAviso / mostrarTexto: el mensaje respeta '\n' (whitespace-pre-line) y, al cerrar, el foco vuelve
// al elemento que lo tenía al abrir (si sigue en el documento).

// src/test/render.tsx
export function renderConRouter(rutas: RouteObject[], opciones?: { sesion?: Sesion | null; ruta?: string }): ReturnType<typeof render> & { queryClient: QueryClient; router: ReturnType<typeof createMemoryRouter> }
```

- Produces (W9, tokens del formulario): `form-barra-bg`, `form-cabecera-bg`, `form-cabecera-brd`, `form-fila-brd`, `form-obs-bg`, `form-guardada-bg`, `form-agotado-bg`, `form-agotado-text`, `ambar`, `rojo-cancelar`, `form-otros-bg`, `form-zona-bg`, `form-zona-brd`, `aviso-conflicto-bg`, `aviso-conflicto-text`, `aviso-incidencia-text`. El negro `#000000` del contador y de la observación NO es token nuevo: ya existe `--color-texto-incidencia: #000000` y se reutiliza (`text-texto-incidencia`), como manda la decisión 5 del plan.

**Ficha** (`formulario.md`): "Combo de modelo: 180 px, estilo de combo navy…" (estilo); "Combo SKU: 170 px fijo, 8 opciones visibles, 11 px…" (estilo); "Color del SKU, en la lista y en el botón…" (soporte: `clase`). Deuda de la spec §6.4: el foco vuelve tras un aviso y `onSesionExpirada` devuelve `unsubscribe`.

- [x] **Step 1: Tokens del formulario**

En `src/shared/styles/tokens.css`, dentro del bloque `@theme { … }`, añadir justo antes de la llave de cierre (ningún token existente cambia de valor):

```css
  /* Formulario de reparación (sub-proyecto 2) */
  --color-form-barra-bg: #C8CDD6;
  --color-form-cabecera-bg: #BCC2CB;
  --color-form-cabecera-brd: #A8AEB7;
  --color-form-fila-brd: #E0E0E0;
  --color-form-obs-bg: #888888;
  --color-form-guardada-bg: #F1F8F1;
  --color-form-agotado-bg: #FFF8E0;
  --color-form-agotado-text: #7A5C00;
  --color-ambar: #E8A825;
  --color-rojo-cancelar: #C94040;
  --color-form-otros-bg: #F6F7F9;
  --color-form-zona-bg: #ECEEF1;
  --color-form-zona-brd: #CFD3DA;
  --color-aviso-conflicto-bg: #FFF3E0;
  --color-aviso-conflicto-text: #E65100;
  --color-aviso-incidencia-text: #CC4444;
```

```bash
grep -c "^  --color-" src/shared/styles/tokens.css
```
Expected: 16 más que antes de la tarea.

- [x] **Step 2: Test de `ComboNavy` (falla)**

Crear `src/shared/ui/ComboNavy.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { ComboNavy, type OpcionCombo } from './ComboNavy'

const MODELOS: OpcionCombo[] = [{ valor: '13', etiqueta: 'iPhone 13' }, { valor: '13promax', etiqueta: 'iPhone 13 Pro Max' }, { valor: '14', etiqueta: 'iPhone 14' }]
const SKUS: OpcionCombo[] = [{ valor: '101', etiqueta: 'bati13' }, { valor: '102', etiqueta: 'bati14', clase: 'text-rojo-sin-stock' }, { valor: '103', etiqueta: 'bati13promax', clase: 'text-fila-solicitud-brd' }]

function Demo({ inicial = null, alCambiar, opciones = MODELOS }: { inicial?: string | null; alCambiar?: (v: string) => void; opciones?: OpcionCombo[] }) {
  const [valor, setValor] = useState<string | null>(inicial)
  return <ComboNavy valor={valor} opciones={opciones} onChange={(v) => { setValor(v); alCambiar?.(v) }} textoVacio="— Selecciona modelo —" ancho={180} aria-label="Filtrar por modelo" />
}

describe('ComboNavy (combo navy de selección única)', () => {
  it('muestra textoVacio, abre, elige, llama a onChange y se cierra', async () => {
    const alCambiar = vi.fn()
    render(<Demo alCambiar={alCambiar} />)
    const combo = screen.getByRole('combobox', { name: 'Filtrar por modelo' })
    expect(combo).toHaveTextContent('— Selecciona modelo —')
    expect(combo).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    await userEvent.click(combo)
    const lista = screen.getByRole('listbox', { name: 'Filtrar por modelo' })
    expect(within(lista).getAllByRole('option').map((o) => o.textContent)).toEqual(['iPhone 13', 'iPhone 13 Pro Max', 'iPhone 14'])
    await userEvent.click(within(lista).getByRole('button', { name: 'iPhone 13 Pro Max' }))
    expect(alCambiar).toHaveBeenCalledTimes(1)
    expect(alCambiar).toHaveBeenCalledWith('13promax')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Filtrar por modelo' })).toHaveTextContent('iPhone 13 Pro Max')
  })
  it('la opción activa va marcada (aria-selected y fondo navy)', async () => {
    render(<Demo inicial="14" />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Filtrar por modelo' }))
    expect(screen.getByRole('option', { name: 'iPhone 14' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('option', { name: 'iPhone 13' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('button', { name: 'iPhone 14' })).toHaveClass('bg-azul-noche', 'text-superficie')
    expect(screen.getByRole('button', { name: 'iPhone 13' })).toHaveClass('text-azul-noche')
  })
  it('disabled no abre', async () => {
    render(<ComboNavy valor="13" opciones={MODELOS} onChange={() => {}} textoVacio="— Selecciona modelo —" ancho={180} disabled aria-label="Filtrar por modelo" />)
    const combo = screen.getByRole('combobox', { name: 'Filtrar por modelo' })
    expect(combo).toBeDisabled()
    await userEvent.click(combo)
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })
  it('aplica la clase de color de la opción en la lista y en el botón', async () => {
    render(<ComboNavy valor="102" opciones={SKUS} onChange={() => {}} textoVacio="—" ancho={170} tamanoTexto={11} visibles={8} aria-label="SKU de Batería" />)
    const combo = screen.getByRole('combobox', { name: 'SKU de Batería' })
    expect(combo).toHaveClass('text-rojo-sin-stock', 'text-[11px]')
    expect(combo).not.toHaveClass('text-texto-nav-activo')
    await userEvent.click(combo)
    expect(screen.getByRole('button', { name: 'bati13promax' })).toHaveClass('text-fila-solicitud-brd')
    expect(screen.getByRole('button', { name: 'bati13' })).toHaveClass('text-azul-noche')
    // la activa se pinta en blanco sobre navy aunque tenga clase de stock
    expect(screen.getByRole('button', { name: 'bati14' })).toHaveClass('bg-azul-noche', 'text-superficie')
  })
  it('es la píldora navy con el ancho pedido; la lista limita su alto a las filas visibles', async () => {
    render(<ComboNavy valor={null} opciones={SKUS} onChange={() => {}} textoVacio="—" ancho={170} tamanoTexto={11} visibles={2} aria-label="SKU de Batería" />)
    const combo = screen.getByRole('combobox', { name: 'SKU de Batería' })
    expect(combo).toHaveTextContent('—')
    expect(combo).toHaveClass('rounded-3xl', 'bg-azul-noche', 'font-bold', 'text-texto-nav-activo')
    expect(combo).toHaveStyle({ width: '170px' })
    expect(combo.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    await userEvent.click(combo)
    const lista = screen.getByRole('listbox', { name: 'SKU de Batería' })
    expect(lista).toHaveStyle({ maxHeight: '64px' })
    expect(lista.closest('[data-slot="popover-content"]')).toHaveClass('border-fila-sep', 'bg-superficie', 'rounded-lg')
  })
  it('un valor que no está entre las opciones se muestra como vacío', () => {
    render(<ComboNavy valor="99" opciones={MODELOS} onChange={() => {}} textoVacio="— Selecciona modelo —" ancho={180} aria-label="Filtrar por modelo" />)
    expect(screen.getByRole('combobox', { name: 'Filtrar por modelo' })).toHaveTextContent('— Selecciona modelo —')
  })
})
```

```bash
npx vitest run src/shared/ui/ComboNavy.test.tsx
```
Expected: FALLA (no existe `./ComboNavy`).

- [x] **Step 3: Implementar `ComboNavy`**

Crear `src/shared/ui/ComboNavy.tsx`:

```tsx
import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { Popover, PopoverContent, PopoverTrigger } from './popover'

export type OpcionCombo = { valor: string; etiqueta: string; clase?: string }

type Props = {
  valor: string | null
  opciones: OpcionCombo[]
  onChange: (valor: string) => void
  textoVacio: string
  ancho: number
  tamanoTexto?: 11 | 12
  /** Filas visibles de la lista antes de desplazar (8 en el combo de SKU). */
  visibles?: number
  disabled?: boolean
  'aria-label': string
}

const ALTO_OPCION_PX = 28
const RELLENO_LISTA_PX = 8
const CLASE_TEXTO: Record<11 | 12, string> = { 11: 'text-[11px]', 12: 'text-[12px]' }

/** Combo navy de selección única (modelo y SKU del formulario): píldora navy con la etiqueta de la opción elegida y lista
 *  blanca con la opción activa en navy. `clase` colorea el texto de una opción (SKU por stock) en la lista y en el botón. El
 *  color no se mezcla con `cn`: se elige una clase u otra, para no depender de cómo resuelva tailwind-merge dos `text-*`. */
export function ComboNavy({ valor, opciones, onChange, textoVacio, ancho, tamanoTexto = 12, visibles, disabled = false, 'aria-label': etiquetaAccesible }: Props) {
  const [abierto, setAbierto] = useState(false)
  const actual = opciones.find((o) => o.valor === valor) ?? null
  const tamano = CLASE_TEXTO[tamanoTexto]
  return (
    <Popover open={abierto} onOpenChange={(o) => setAbierto(o && !disabled)}>
      <PopoverTrigger
        role="combobox"
        aria-haspopup="listbox"
        aria-label={etiquetaAccesible}
        disabled={disabled}
        style={{ width: ancho }}
        className={cn(
          'flex h-[27px] shrink-0 items-center justify-between gap-1 rounded-3xl bg-azul-noche px-3 font-bold hover:bg-azul-noche-hover disabled:cursor-default disabled:opacity-60 disabled:hover:bg-azul-noche',
          tamano,
          actual?.clase || 'text-texto-nav-activo',
        )}
      >
        <span className="truncate">{actual ? actual.etiqueta : textoVacio}</span>
        <ChevronDown aria-hidden="true" className="size-3.5 shrink-0 text-texto-nav-activo" />
      </PopoverTrigger>
      <PopoverContent align="start" style={{ minWidth: ancho }} className="w-auto rounded-lg border border-fila-sep bg-superficie p-0 shadow-md">
        <ul
          role="listbox"
          aria-label={etiquetaAccesible}
          style={visibles !== undefined ? { maxHeight: visibles * ALTO_OPCION_PX + RELLENO_LISTA_PX } : undefined}
          className={cn('overflow-auto py-1', visibles === undefined && 'max-h-72')}
        >
          {opciones.map((o) => {
            const activa = o.valor === valor
            return (
              <li key={o.valor} role="option" aria-selected={activa}>
                <button
                  type="button"
                  onClick={() => {
                    setAbierto(false)
                    if (!activa) onChange(o.valor)
                  }}
                  className={cn(
                    'mx-1 block h-7 w-[calc(100%-8px)] truncate rounded-lg px-3 text-left font-bold',
                    tamano,
                    activa ? 'bg-azul-noche text-superficie' : cn('hover:bg-seleccion-suave', o.clase || 'text-azul-noche'),
                  )}
                >
                  {o.etiqueta}
                </button>
              </li>
            )
          })}
        </ul>
      </PopoverContent>
    </Popover>
  )
}
```

```bash
npx vitest run src/shared/ui/ComboNavy.test.tsx
```
Expected: PASS (6 tests). `visibles={2}` → `2 * 28 + 8 = 64px`.

- [x] **Step 4: Test de las consultas silenciosas (falla)**

En `src/shared/api/queryClient.test.tsx`, sustituir el componente `VistaConsulta` por esta versión (solo añade la prop `silenciar`; el resto de tests del fichero lo usan sin ella):

```tsx
function VistaConsulta({ silenciar = false }: { silenciar?: boolean }) {
  const q = useQuery({
    queryKey: ['clientes'],
    queryFn: async () => (await api.GET('/api/clientes')).data ?? [],
    meta: silenciar ? { silenciarError: true } : undefined,
  })
  return (
    <>
      <button onClick={() => void q.refetch()}>Refrescar</button>
      {q.isError && <p>FALLO</p>}
      {q.data && <p>DATOS {q.data.length}</p>}
      <p>INTENTOS {q.errorUpdateCount}</p>
    </>
  )
}
```

y añadir, dentro de `describe('crearQueryClient: errores de consultas', …)`, al final:

```tsx
  it('una consulta con meta.silenciarError no emite aviso por un 4xx (lo pone la vista)', async () => {
    server.use(http.get('*/api/clientes', () => new HttpResponse(null, { status: 403 })))
    renderConProviders(<VistaConsulta silenciar />)
    expect(await screen.findByText('FALLO')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('una consulta con meta.silenciarError tampoco abre el diálogo de conexión: basta el banner', async () => {
    server.use(http.get('*/api/clientes', () => HttpResponse.text('boom', { status: 503 })))
    renderConProviders(<VistaConsulta silenciar />)
    expect(await screen.findByText('FALLO')).toBeInTheDocument()
    expect(estaConectado()).toBe(false)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
```

```bash
npx vitest run src/shared/api/queryClient.test.tsx
```
Expected: FALLAN los dos nuevos (aparece el diálogo "Error"); además `npm run typecheck` protestaría por `meta.silenciarError` en una consulta (`queryMeta` aún sin tipar).

- [x] **Step 5: `queryMeta.silenciarError` en `queryClient.ts`**

En `src/shared/api/queryClient.ts`, sustituir el bloque `declare module` (con su comentario) por:

```ts
// Tipa `meta` de consultas y mutaciones: sin esto es `Record<string, unknown> | undefined` y `silenciarError` no está
// comprobado por tsc (un typo como `silenciarErrores` compilaría sin avisar).
declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: { silenciarError?: boolean }
    mutationMeta: { silenciarError?: boolean }
  }
}
```

y, en `QueryCache.onError`, añadir como primera línea del cuerpo (antes del comentario "Diálogo solo en el PRIMER fallo…"):

```ts
        // `meta: { silenciarError: true }`: la consulta no avisa de NADA por esta vía. O bien el aviso lo pone la vista con su
        // propio literal (cargas del formulario), o bien es un sondeo de fondo (campana). Tampoco del corte de conexión: el
        // banner ya lo cuenta (lo enciende el cliente HTTP, no este callback).
        if (query.meta?.silenciarError === true) return
```

```bash
npx vitest run src/shared/api/queryClient.test.tsx
```
Expected: PASS (los 7 existentes + 2).

- [x] **Step 6: Test del `unsubscribe` de `onSesionExpirada` (falla)**

En `src/shared/session/expiracion.test.ts`, añadir dentro del `describe('sesión expirada', …)`, al final:

```ts
  it('devuelve un unsubscribe que retira su handler', () => {
    const h = vi.fn()
    const quitar = onSesionExpirada(h)
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't' })
    quitar()
    dispararSesionExpirada()
    expect(h).not.toHaveBeenCalled()
  })
  it('el unsubscribe de un handler ya sustituido no retira el actual', () => {
    const viejo = vi.fn()
    const nuevo = vi.fn()
    const quitarViejo = onSesionExpirada(viejo)
    onSesionExpirada(nuevo)
    quitarViejo()
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't' })
    dispararSesionExpirada()
    expect(viejo).not.toHaveBeenCalled()
    expect(nuevo).toHaveBeenCalledTimes(1)
  })
  it('sin handler el disparo no se gasta: uno registrado después todavía lo recibe', () => {
    const h = vi.fn()
    onSesionExpirada(h)()
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't' })
    dispararSesionExpirada()
    const otro = vi.fn()
    onSesionExpirada(otro)
    dispararSesionExpirada()
    expect(otro).toHaveBeenCalledTimes(1)
  })
```

```bash
npx vitest run src/shared/session/expiracion.test.ts
```
Expected: FALLAN los tres nuevos (`quitar is not a function`: hoy `onSesionExpirada` devuelve `undefined`).

- [x] **Step 7: `onSesionExpirada` devuelve `unsubscribe`**

En `src/shared/session/expiracion.ts`, sustituir la función `onSesionExpirada` por:

```ts
/** Registra el handler (uno solo: el último gana) y devuelve cómo retirarlo. El unsubscribe solo quita `h` si sigue siendo
 *  el actual: el de un handler ya sustituido no deja a la app sin el nuevo. */
export function onSesionExpirada(h: () => void): () => void {
  handler = h
  return () => {
    if (handler === h) handler = null
  }
}
```

`dispararSesionExpirada` no cambia (ya sale sin gastar el disparo cuando no hay handler). `src/main.tsx` llama a `onSesionExpirada(...)` a nivel de módulo e ignora el retorno: no se toca; solo se comprueba que compila.

```bash
npx vitest run src/shared/session/expiracion.test.ts && npm run typecheck
```
Expected: PASS (3 existentes + 3) y typecheck en verde.

- [x] **Step 8: Tests de `AlertaProvider`: multilínea y retorno del foco (fallan)**

En `src/shared/ui/AlertaProvider.test.tsx`, cambiar el import de Testing Library a

```tsx
import { render, screen, waitFor, within } from '@testing-library/react'
```

y añadir dentro del `describe`, al final:

```tsx
  it('un mensaje con salto de línea se pinta en dos líneas', async () => {
    function Vista() { const { mostrarError } = useAlerta(); return <button onClick={() => mostrarError('No se pudo guardar: la asignación ya está completada.\nCierra el formulario y comprueba el estado de la asignación.')}>ir</button> }
    render(<AlertaProvider><Vista /></AlertaProvider>)
    await userEvent.click(screen.getByRole('button', { name: 'ir' }))
    const parrafo = within(screen.getByRole('dialog', { name: 'Error' })).getByText(/No se pudo guardar: la asignación ya está completada\./)
    expect(parrafo).toHaveClass('whitespace-pre-line')
    expect(parrafo.textContent).toBe('No se pudo guardar: la asignación ya está completada.\nCierra el formulario y comprueba el estado de la asignación.')
  })
  it('al cerrar el aviso el foco vuelve al elemento que lo tenía', async () => {
    render(<AlertaProvider><Demo /></AlertaProvider>)
    const boton = screen.getByRole('button', { name: 'boom' })
    await userEvent.click(boton)
    expect(boton).not.toHaveFocus() // el diálogo se lleva el foco
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    await waitFor(() => expect(boton).toHaveFocus())
  })
  it('también al cerrar con Escape y en el popup de texto', async () => {
    function Vista() { const { mostrarTexto } = useAlerta(); return <button onClick={() => mostrarTexto('Observación', 'Pantalla con líneas verticales')}>ver</button> }
    render(<AlertaProvider><Vista /></AlertaProvider>)
    const boton = screen.getByRole('button', { name: 'ver' })
    await userEvent.click(boton)
    expect(screen.getByRole('dialog', { name: 'Observación' })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(boton).toHaveFocus())
  })
  it('si el elemento que tenía el foco ya no está en el documento, cerrar no falla', async () => {
    function Vista() {
      const { mostrarError } = useAlerta()
      const [visible, setVisible] = useState(true)
      return visible ? <button onClick={() => { mostrarError('fallo'); setVisible(false) }}>efímero</button> : <p>sin botón</p>
    }
    render(<AlertaProvider><Vista /></AlertaProvider>)
    await userEvent.click(screen.getByRole('button', { name: 'efímero' }))
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('sin botón')).toBeInTheDocument()
  })
```

y añadir `import { useState } from 'react'` a los imports del fichero.

```bash
npx vitest run src/shared/ui/AlertaProvider.test.tsx
```
Expected: FALLAN "…en dos líneas" (sin la clase) y los dos del foco (el foco se queda en `body`).

- [x] **Step 9: `AlertaProvider` multilínea y con retorno del foco**

En `src/shared/ui/AlertaProvider.tsx`:

1. Cambiar el import de React a:

```tsx
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
```

2. Dentro de `AlertaProvider`, sustituir las tres definiciones `mostrarError`, `mostrarAviso` y `mostrarTexto` por:

```tsx
  // El aviso no tiene disparador de Radix (se abre desde código), así que al cerrar Radix no sabe a dónde devolver el foco
  // y se perdería en <body>. Se guarda el elemento que lo tenía al abrir (solo el primero, si se encadenan avisos) y se
  // restaura en onCloseAutoFocus, siempre que siga en el documento.
  const focoPrevio = useRef<HTMLElement | null>(null)
  const recordarFoco = useCallback(() => {
    if (focoPrevio.current === null && document.activeElement instanceof HTMLElement && document.activeElement !== document.body) {
      focoPrevio.current = document.activeElement
    }
  }, [])
  const devolverFoco = useCallback((e: Event) => {
    const el = focoPrevio.current
    focoPrevio.current = null
    if (el !== null && el.isConnected) {
      e.preventDefault()
      el.focus()
    }
  }, [])
  const mostrarError = useCallback((m: string) => { recordarFoco(); setAviso({ titulo: 'Error', msg: m }) }, [recordarFoco])
  const mostrarAviso = useCallback((titulo: string, msg: string) => { recordarFoco(); setAviso({ titulo, msg }) }, [recordarFoco])
  const mostrarTexto = useCallback((titulo: string, t: string) => {
    recordarFoco()
    setTexto({ titulo, texto: t })
    setTextoAbierto(true)
  }, [recordarFoco])
```

3. En el primer diálogo (el del aviso), sustituir `<DialogContent>` por `<DialogContent onCloseAutoFocus={devolverFoco}>` y `<DialogDescription>{aviso?.msg}</DialogDescription>` por:

```tsx
            <DialogDescription className="whitespace-pre-line">{aviso?.msg}</DialogDescription>
```

4. En el segundo diálogo (el popup de texto), añadir `onCloseAutoFocus={devolverFoco}` a su `<DialogContent aria-describedby={undefined} className="max-w-[420px] gap-2.5 bg-crema p-5">`. El `textarea` ya respeta los saltos de línea.

```bash
npx vitest run src/shared/ui/AlertaProvider.test.tsx
```
Expected: PASS (2 existentes + 4).

- [x] **Step 10: Test de `renderConRouter` (falla)**

Crear `src/test/render.test.tsx`:

```tsx
import { act, screen } from '@testing-library/react'
import { Outlet, useBlocker, useParams } from 'react-router'
import { describe, expect, it } from 'vitest'
import { useSession } from '@/shared/session/SessionProvider'
import { SESION_TEC, renderConRouter } from './render'

function Lista() {
  const { sesion } = useSession()
  return (
    <div>
      <p>Lista de {sesion?.nombreUsuario ?? 'nadie'}</p>
      <Outlet />
    </div>
  )
}
function Hijo() {
  const { idAsignacion } = useParams()
  // useBlocker exige data router: con MemoryRouter + Routes lanzaría. Que monte ya demuestra que el router es de datos.
  const bloqueo = useBlocker(false)
  return <p>Formulario {idAsignacion} ({bloqueo.state})</p>
}

describe('renderConRouter (data router en memoria)', () => {
  const rutas = [{ path: '/reparaciones/pendientes', element: <Lista />, children: [{ path: 'reparar/:idAsignacion', element: <Hijo /> }] }]

  it('monta rutas anidadas con los providers de siempre', () => {
    const { queryClient } = renderConRouter(rutas, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/reparar/A20260916_1' })
    expect(screen.getByText('Lista de tecnico_n')).toBeInTheDocument()
    expect(screen.getByText('Formulario A20260916_1 (unblocked)')).toBeInTheDocument()
    expect(queryClient.getDefaultOptions().queries?.retry).toBe(false)
  })
  it('expone router.navigate: navegar y volver atrás', async () => {
    const { router } = renderConRouter(rutas, { ruta: '/reparaciones/pendientes' })
    expect(screen.getByText('Lista de nadie')).toBeInTheDocument()
    expect(screen.queryByText(/^Formulario/)).not.toBeInTheDocument()
    await act(() => router.navigate('/reparaciones/pendientes/reparar/AG20260916_2'))
    expect(screen.getByText('Formulario AG20260916_2 (unblocked)')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/reparaciones/pendientes/reparar/AG20260916_2')
    await act(() => router.navigate(-1))
    expect(screen.queryByText(/^Formulario/)).not.toBeInTheDocument()
  })
})
```

```bash
npx vitest run src/test/render.test.tsx
```
Expected: FALLA (`renderConRouter` no exportado de `./render`).

- [x] **Step 11: `renderConRouter` en `src/test/render.tsx`**

En `src/test/render.tsx`, sustituir los dos primeros imports de librería y el de `react-router` por:

```tsx
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, RouterProvider, Routes, createMemoryRouter, type RouteObject } from 'react-router'
```

y añadir, debajo de `renderConProviders` (cuya firma no cambia):

```tsx
/** Igual que renderConProviders (mismo QueryClient de producción sin reintentos, SessionProvider y AlertaProvider) pero con
 *  un data router en memoria (createMemoryRouter + RouterProvider): hace falta para lo que exige data router, como
 *  useBlocker, y para probar rutas hijas con <Outlet />. Devuelve el `router` para navegar desde el test
 *  (`await act(() => router.navigate(-1))`). */
export function renderConRouter(
  rutas: RouteObject[],
  { sesion = null, ruta = '/' }: { sesion?: Sesion | null; ruta?: string } = {},
): ReturnType<typeof render> & { queryClient: QueryClient; router: ReturnType<typeof createMemoryRouter> } {
  if (sesion) guardarSesion(sesion)
  const qc = crearQueryClient({ retry: false })
  const router = createMemoryRouter(rutas, { initialEntries: [ruta] })
  const resultado = render(
    <QueryClientProvider client={qc}>
      <SessionProvider>
        <AlertaProvider>
          <RouterProvider router={router} />
        </AlertaProvider>
      </SessionProvider>
    </QueryClientProvider>,
  )
  return { ...resultado, queryClient: qc, router }
}
```

```bash
npx vitest run src/test/render.test.tsx
```
Expected: PASS (2 tests).

- [x] **Step 12: `npm run check` en verde**

```bash
npm run check
```
Expected: lint (`shared` sin imports de `app` ni de `modules`; `src/shared/ui/**` ya tiene desactivada la regla de fast refresh para exportar `OpcionCombo` junto al componente), typecheck y toda la suite en verde.

- [x] **Step 13: Commit**

```bash
git add src/shared/ui/ComboNavy.tsx src/shared/ui/ComboNavy.test.tsx src/shared/api/queryClient.ts src/shared/api/queryClient.test.tsx src/shared/session/expiracion.ts src/shared/session/expiracion.test.ts src/shared/ui/AlertaProvider.tsx src/shared/ui/AlertaProvider.test.tsx src/test/render.tsx src/test/render.test.tsx src/shared/styles/tokens.css
git commit -m "feat(web): combo navy compartido, consultas silenciosas, avisos multilínea que devuelven el foco y onSesionExpirada con unsubscribe"
```

---

### Task 11: Web — `formulario/api.ts`: cargas, mutaciones, borrador y handlers MSW

**Files:**
- Create: `src/modules/taller/formulario/api.ts`, `src/modules/taller/formulario/api.test.tsx`, `src/modules/taller/formulario/test/handlers.ts`

**Interfaces:**
- Consumes: W1 (`api` y los tipos `AgotarRequest`, `AsignacionActiva`, `ComponentesAgrupados`, `DetalleEdicion`, `EditarReparacionRequest`, `GuardarFilaRequest`, `InsertarCompletaRequest`, `Reparacion`, `ReparacionResumen`, `SolicitudAsignacion` de `@/shared/api/client`); W2 (`agrupados`, `resumen`, `detalleEdicion`… de `../test/fabrica`); W5 (`DatosNuevo`, `DatosEditar` de `./estado`); `queryMeta.silenciarError` (W8, Task 10); `claveAsignaciones`, `claveHistorial`, `CLAVE_CONTADORES` de `../api`; `NoEncontradoError`, `PermisoError`, `MSG_NO_ENCONTRADO` de `@/shared/api/errors`.
- Produces (W10):

```ts
export const claveCargaNuevo = (idAsignacion: string) => ['formulario', 'nuevo', idAsignacion] as const
export const claveCargaEditar = (idRep: string) => ['formulario', 'editar', idRep] as const
export type CargaNuevo = { datos: DatosNuevo; asignacionesActivas: AsignacionActiva[]; borradorJson: string | null }
export async function cargarNuevo(idAsignacion: string): Promise<CargaNuevo>
export async function cargarEditar(idRep: string): Promise<DatosEditar>
export function useCargaNuevo(idAsignacion: string): UseQueryResult<CargaNuevo>
export function useCargaEditar(idRep: string): UseQueryResult<DatosEditar>
export function useGuardarFila(): UseMutationResult<string, unknown, { idAsignacion: string; cuerpo: GuardarFilaRequest }>
export function useAgotarComponente(): UseMutationResult<void, unknown, { idAsignacion: string; cuerpo: AgotarRequest }>
export function useCompleta(): UseMutationResult<void, unknown, InsertarCompletaRequest>
export function useEditarReparacion(): UseMutationResult<void, unknown, { idRep: string; cuerpo: EditarReparacionRequest }>
export async function guardarBorrador(idAsignacion: string, contenido: string): Promise<void>
export async function borrarBorrador(idAsignacion: string): Promise<void>
export async function idsReparacionesDelImei(imei: string): Promise<string[]>
export function useRecargarAlCerrar(): () => void
export const CLAVE_NOTIF = ['notificaciones'] as const
```

`src/modules/taller/formulario/test/handlers.ts`:

```ts
export type EscenarioFormulario = {
  asignacion?: Partial<ReparacionResumen>          // por defecto resumen({ idRep: 'A20260916_1' })
  agrupados?: ComponentesAgrupados                 // por defecto agrupados()
  solicitudes?: SolicitudAsignacion[]
  incidencia?: string | null
  activas?: AsignacionActiva[]
  modeloTelefono?: string                          // '' por defecto
  borrador?: string | null
  reparacionesImei?: Reparacion[]
  detalle?: DetalleEdicion; yaReparados?: number[]; acciones?: string[]
}
export function handlersFormulario(escenario?: EscenarioFormulario): RequestHandler[]
export type LlamadaRegistrada = { metodo: string; ruta: string; cuerpo: unknown }
export function conRegistro(escenario?: EscenarioFormulario): { handlers: RequestHandler[]; llamadas: LlamadaRegistrada[] }
```

**Ficha** (`formulario.md`): "Banda de incidencia… Se consulta siempre `GET …/incidencia-activa?tipo=R` (`tipo=G` si la asignación empieza por `AG`)… Un fallo de esta consulta sí muestra error" (la carga lo propaga); "Al abrir (flujo nuevo y Glass): `GET …/solicitudes`…"; "Carga: `GET …/detalle-edicion`…"; "Solo flujo nuevo y Glass, por asignación: `GET|PUT|DELETE …/borrador`…"; "Silenciosos: asignaciones activas del IMEI, modelo del teléfono, acciones ya reparadas, lectura… del borrador…"; tabla "Llamadas a la API"; "Incidencia con `tipo=G`; acciones ya reparadas con `categoria=G`…".

Decisiones de esta tarea:
- MSW resuelve con el primer handler que coincide y cada `server.use(...)` se antepone a los anteriores: por eso los tests registran primero `handlersFormulario(...)` y, en una **segunda** llamada a `server.use`, el handler que lo sustituye.
- `cargarNuevo` conoce el IMEI con `GET /api/reparaciones/asignaciones/{idRep}` (sirve igual con F5 o acceso directo); su 404 llega como `NoEncontradoError`. El `modo` de `DatosNuevo` lo decide el prefijo del id: `AG…` → `'glass'`; el resto → `'nuevo'`.
- En el contrato la ruta del borrador es `/api/reparaciones/{idRep}/borrador` (parámetro `idRep`), aunque el valor sea el id de la asignación.
- `openapi-fetch` lanza desde el middleware `auth` (no devuelve `error`): las funciones hacen `await` y dejan propagar; una respuesta sin cuerpo da `data: undefined` y no es un error.
- Si `detalle-edicion` responde sin cuerpo (la reparación ya no existe), `cargarEditar` lanza `NoEncontradoError`.
- Ninguna mutación invalida nada: la recarga se hace al cerrar (`useRecargarAlCerrar`).

- [x] **Step 1: Handlers MSW del formulario**

Crear `src/modules/taller/formulario/test/handlers.ts`:

```ts
import { HttpResponse, http, type RequestHandler } from 'msw'
import type { AsignacionActiva, ComponentesAgrupados, DetalleEdicion, Reparacion, ReparacionResumen, SolicitudAsignacion } from '@/shared/api/client'
import { agrupados, detalleEdicion, resumen } from '../../test/fabrica'

export type EscenarioFormulario = {
  asignacion?: Partial<ReparacionResumen>
  agrupados?: ComponentesAgrupados
  solicitudes?: SolicitudAsignacion[]
  incidencia?: string | null
  activas?: AsignacionActiva[]
  modeloTelefono?: string
  borrador?: string | null
  reparacionesImei?: Reparacion[]
  detalle?: DetalleEdicion
  yaReparados?: number[]
  acciones?: string[]
}

/** Registro de las escrituras recibidas, en orden, para comprobar orden y cuerpos. */
export type LlamadaRegistrada = { metodo: string; ruta: string; cuerpo: unknown }

async function cuerpoDe(request: Request): Promise<unknown> {
  const texto = await request.text()
  if (texto === '') return null
  try {
    return JSON.parse(texto)
  } catch {
    return texto
  }
}

function construir(escenario: EscenarioFormulario, llamadas: LlamadaRegistrada[] | null): RequestHandler[] {
  const asignacion = resumen({ idRep: 'A20260916_1', ...escenario.asignacion })
  const anotar = async (request: Request) => {
    if (llamadas) llamadas.push({ metodo: request.method, ruta: new URL(request.url).pathname, cuerpo: await cuerpoDe(request) })
  }
  return [
    // ── Lecturas (las rutas más específicas, antes) ──
    http.get('*/api/componentes/agrupados', () => HttpResponse.json(escenario.agrupados ?? agrupados())),
    http.get('*/api/reparaciones/asignaciones/:idAsignacion/solicitudes', () => HttpResponse.json(escenario.solicitudes ?? [])),
    http.get('*/api/reparaciones/asignaciones/:idRep', ({ params }) => HttpResponse.json({ ...asignacion, idRep: String(params.idRep) })),
    http.get('*/api/reparaciones/imei/:imei/incidencia-activa', () => HttpResponse.json({ value: escenario.incidencia ?? null })),
    http.get('*/api/reparaciones/imei/:imei/asignaciones-activas', () => HttpResponse.json(escenario.activas ?? [])),
    http.get('*/api/reparaciones/imei/:imei/ya-reparados', () => HttpResponse.json(escenario.yaReparados ?? [])),
    http.get('*/api/reparaciones/imei/:imei/acciones', () => HttpResponse.json(escenario.acciones ?? [])),
    http.get('*/api/reparaciones/imei/:imei', () => HttpResponse.json(escenario.reparacionesImei ?? [])),
    http.get('*/api/telefonos/:imei/modelo', () => HttpResponse.json({ value: escenario.modeloTelefono ?? '' })),
    http.get('*/api/reparaciones/:idRep/borrador', () => HttpResponse.json({ contenido: escenario.borrador ?? null })),
    http.get('*/api/reparaciones/:idRep/detalle-edicion', () => HttpResponse.json(escenario.detalle ?? detalleEdicion())),
    // ── Escrituras con éxito ──
    http.post('*/api/reparaciones/completa', async ({ request }) => {
      await anotar(request)
      return new HttpResponse(null, { status: 201 })
    }),
    http.post('*/api/reparaciones/:idAsignacion/filas', async ({ request }) => {
      await anotar(request)
      return HttpResponse.json({ value: 'R20260916_9' }, { status: 201 })
    }),
    http.post('*/api/reparaciones/:idAsignacion/agotar-componente', async ({ request }) => {
      await anotar(request)
      return new HttpResponse(null, { status: 201 })
    }),
    http.put('*/api/reparaciones/:idRep/borrador', async ({ request }) => {
      await anotar(request)
      return new HttpResponse(null, { status: 200 })
    }),
    http.delete('*/api/reparaciones/:idRep/borrador', async ({ request }) => {
      await anotar(request)
      return new HttpResponse(null, { status: 200 })
    }),
    http.put('*/api/reparaciones/:idRep', async ({ request }) => {
      await anotar(request)
      return new HttpResponse(null, { status: 200 })
    }),
  ]
}

/** Handlers MSW de todas las lecturas del formulario + escrituras con éxito (`filas` → { value: 'R20260916_9' }). Un test que
 *  necesite un fallo lo declara DESPUÉS con `server.use(...)`: el último handler registrado gana. */
export function handlersFormulario(escenario: EscenarioFormulario = {}): RequestHandler[] {
  return construir(escenario, null)
}

/** Igual, pero anotando cada escritura recibida (método, ruta y cuerpo) en `llamadas`, en orden de llegada. */
export function conRegistro(escenario: EscenarioFormulario = {}): { handlers: RequestHandler[]; llamadas: LlamadaRegistrada[] } {
  const llamadas: LlamadaRegistrada[] = []
  return { handlers: construir(escenario, llamadas), llamadas }
}
```

(Este fichero no tiene test propio: lo ejercitan los de `api.test.tsx` del paso siguiente.)

- [x] **Step 2: Tests de las cargas (fallan)**

Crear `src/modules/taller/formulario/api.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, screen, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { ConexionError, NoEncontradoError, PermisoError, StaleDataError } from '@/shared/api/errors'
import { crearQueryClient } from '@/shared/api/queryClient'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { guardarSesion } from '@/shared/session/storage'
import { AlertaProvider } from '@/shared/ui/AlertaProvider'
import { SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { CLAVE_CONTADORES, claveAsignaciones, claveHistorial } from '../api'
import { BORRADOR_JAVAFX, agrupados, asignacionActiva, detalleEdicion, reparacion, solicitudAsignacion } from '../test/fabrica'
import {
  CLAVE_NOTIF, borrarBorrador, cargarEditar, cargarNuevo, claveCargaEditar, claveCargaNuevo, guardarBorrador, idsReparacionesDelImei,
  useAgotarComponente, useCargaEditar, useCargaNuevo, useCompleta, useEditarReparacion, useGuardarFila, useRecargarAlCerrar,
} from './api'
import { conRegistro, handlersFormulario } from './test/handlers'

function envoltorio(sesion: typeof SESION_TEC) {
  guardarSesion(sesion)
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}><SessionProvider><AlertaProvider>{children}</AlertaProvider></SessionProvider></QueryClientProvider>
  )
  return { wrapper, qc }
}

describe('cargarNuevo', () => {
  it('compone DatosNuevo con el imei de la asignación', async () => {
    guardarSesion(SESION_TEC)
    const solicitudes = [solicitudAsignacion({ idCom: 102 })]
    const activas = [asignacionActiva()]
    server.use(...handlersFormulario({ asignacion: { imei: '355400000000222' }, solicitudes, incidencia: 'R20260910_3', activas, modeloTelefono: '14', borrador: BORRADOR_JAVAFX }))
    const carga = await cargarNuevo('A20260916_1')
    expect(carga.datos).toEqual({
      modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000222', agrupados: agrupados(), solicitudes, incidencia: 'R20260910_3',
      modeloTelefono: '14',
    })
    expect(Object.keys(carga.datos.agrupados)).toEqual(['bat', 'cha', 'g', 'mc', 'lcd', 'cam', 'otro'])
    expect(carga.asignacionesActivas).toEqual(activas)
    expect(carga.borradorJson).toBe(BORRADOR_JAVAFX)
  })
  it('pide la incidencia con tipo=R y, para una AG, con tipo=G y modo glass', async () => {
    guardarSesion(SESION_TEC)
    const consultas: string[] = []
    server.use(...handlersFormulario())
    server.use(
      http.get('*/api/reparaciones/imei/:imei/incidencia-activa', ({ request, params }) => {
        consultas.push(`${String(params.imei)}${new URL(request.url).search}`)
        return HttpResponse.json({ value: null })
      }),
    )
    expect((await cargarNuevo('A20260916_1')).datos.modo).toBe('nuevo')
    expect((await cargarNuevo('AG20260916_2')).datos).toMatchObject({ modo: 'glass', idAsignacion: 'AG20260916_2', incidencia: null })
    expect(consultas).toEqual(['355400000000111?tipo=R', '355400000000111?tipo=G'])
  })
  it("modelo '' llega como null y sin borrador llega null", async () => {
    guardarSesion(SESION_TEC)
    server.use(...handlersFormulario())
    const carga = await cargarNuevo('A20260916_1')
    expect(carga.datos.modeloTelefono).toBeNull()
    expect(carga.borradorJson).toBeNull()
    expect(carga.asignacionesActivas).toEqual([])
  })
  it('un fallo en asignaciones-activas, en el modelo o en el borrador no rompe la carga', async () => {
    guardarSesion(SESION_TEC)
    server.use(...handlersFormulario({ incidencia: 'R20260910_3' }))
    server.use(
      http.get('*/api/reparaciones/imei/:imei/asignaciones-activas', () => HttpResponse.json({ message: 'fallo' }, { status: 422 })),
      http.get('*/api/telefonos/:imei/modelo', () => new HttpResponse(null, { status: 404 })),
      http.get('*/api/reparaciones/:idRep/borrador', () => HttpResponse.json({ message: 'fallo' }, { status: 409 })),
    )
    const carga = await cargarNuevo('A20260916_1')
    expect(carga.asignacionesActivas).toEqual([])
    expect(carga.datos.modeloTelefono).toBeNull()
    expect(carga.borradorJson).toBeNull()
    expect(carga.datos.incidencia).toBe('R20260910_3')
  })
  it('un 403 del borrador se propaga como PermisoError (la asignación es de otro técnico)', async () => {
    guardarSesion(SESION_TEC)
    server.use(...handlersFormulario())
    server.use(http.get('*/api/reparaciones/:idRep/borrador', () => new HttpResponse(null, { status: 403 })))
    await expect(cargarNuevo('A20260916_1')).rejects.toBeInstanceOf(PermisoError)
  })
  it('un fallo en la incidencia, en las solicitudes o en los componentes rechaza la carga', async () => {
    guardarSesion(SESION_TEC)
    server.use(...handlersFormulario())
    server.use(http.get('*/api/reparaciones/imei/:imei/incidencia-activa', () => HttpResponse.json({ message: 'Tipo no válido' }, { status: 422 })))
    await expect(cargarNuevo('A20260916_1')).rejects.toThrow('Tipo no válido')
    server.resetHandlers()
    server.use(...handlersFormulario())
    server.use(http.get('*/api/reparaciones/asignaciones/:idAsignacion/solicitudes', () => HttpResponse.text('boom', { status: 503 })))
    await expect(cargarNuevo('A20260916_1')).rejects.toBeInstanceOf(ConexionError)
    server.resetHandlers()
    server.use(...handlersFormulario())
    server.use(http.get('*/api/componentes/agrupados', () => HttpResponse.json({ message: 'sin catálogo' }, { status: 422 })))
    await expect(cargarNuevo('A20260916_1')).rejects.toThrow('sin catálogo')
  })
  it('una asignación que no existe rechaza con NoEncontradoError y no pide nada más', async () => {
    guardarSesion(SESION_TEC)
    let otras = 0
    server.use(
      http.get('*/api/reparaciones/asignaciones/:idRep', () => new HttpResponse(null, { status: 404 })),
      http.get('*/api/componentes/agrupados', () => { otras++; return HttpResponse.json({}) }),
    )
    await expect(cargarNuevo('A20260916_404')).rejects.toBeInstanceOf(NoEncontradoError)
    expect(otras).toBe(0)
  })
})

describe('cargarEditar', () => {
  it('encadena detalle → agrupados + ya-reparados(excluir) + acciones(categoria, excluir)', async () => {
    guardarSesion(SESION_SUPER)
    const consultas: string[] = []
    const detalle = detalleEdicion({ imei: '355400000000222', idCom: 111 })
    server.use(...handlersFormulario({ detalle }))
    server.use(
      http.get('*/api/reparaciones/imei/:imei/ya-reparados', ({ request, params }) => {
        consultas.push(`ya-reparados ${String(params.imei)}${new URL(request.url).search}`)
        return HttpResponse.json([101, 121])
      }),
      http.get('*/api/reparaciones/imei/:imei/acciones', ({ request, params }) => {
        consultas.push(`acciones ${String(params.imei)}${new URL(request.url).search}`)
        return HttpResponse.json(['Limpieza del conector de carga'])
      }),
    )
    const datos = await cargarEditar('R20260916_5')
    expect(datos).toEqual({ modo: 'editar', idRep: 'R20260916_5', detalle, agrupados: agrupados(), yaReparados: [101, 121], accionesYaReparadas: ['Limpieza del conector de carga'] })
    expect(consultas.sort()).toEqual(['acciones 355400000000222?categoria=R&excluir=R20260916_5', 'ya-reparados 355400000000222?excluir=R20260916_5'])
  })
  it('categoria=G para un idRep que empieza por G', async () => {
    guardarSesion(SESION_SUPER)
    let consulta = ''
    server.use(...handlersFormulario())
    server.use(
      http.get('*/api/reparaciones/imei/:imei/acciones', ({ request }) => { consulta = new URL(request.url).search; return HttpResponse.json([]) }),
    )
    await cargarEditar('G20260916_64')
    expect(consulta).toBe('?categoria=G&excluir=G20260916_64')
  })
  it('un fallo en las acciones ya reparadas es silencioso; en ya-reparados, no', async () => {
    guardarSesion(SESION_SUPER)
    server.use(...handlersFormulario({ yaReparados: [101] }))
    server.use(http.get('*/api/reparaciones/imei/:imei/acciones', () => HttpResponse.json({ message: 'fallo' }, { status: 422 })))
    expect((await cargarEditar('R20260916_5')).accionesYaReparadas).toEqual([])
    server.resetHandlers()
    server.use(...handlersFormulario())
    server.use(http.get('*/api/reparaciones/imei/:imei/ya-reparados', () => HttpResponse.json({ message: 'fallo al leer' }, { status: 422 })))
    await expect(cargarEditar('R20260916_5')).rejects.toThrow('fallo al leer')
  })
  it('un 403 del detalle se propaga y un detalle vacío es NoEncontradoError', async () => {
    guardarSesion(SESION_TEC)
    server.use(...handlersFormulario())
    server.use(http.get('*/api/reparaciones/:idRep/detalle-edicion', () => new HttpResponse(null, { status: 403 })))
    await expect(cargarEditar('R20260916_5')).rejects.toBeInstanceOf(PermisoError)
    server.resetHandlers()
    server.use(...handlersFormulario())
    server.use(http.get('*/api/reparaciones/:idRep/detalle-edicion', () => new HttpResponse(null, { status: 200 })))
    await expect(cargarEditar('R20260916_5')).rejects.toBeInstanceOf(NoEncontradoError)
  })
})

describe('useCargaNuevo y useCargaEditar', () => {
  it('cargan con su clave y un fallo no abre el aviso global (lo pone la vista)', async () => {
    const { wrapper, qc } = envoltorio(SESION_TEC)
    server.use(...handlersFormulario({ modeloTelefono: '13' }))
    const ok = renderHook(() => useCargaNuevo('A20260916_1'), { wrapper })
    await waitFor(() => expect(ok.result.current.isSuccess).toBe(true))
    expect(ok.result.current.data?.datos.modeloTelefono).toBe('13')
    expect(qc.getQueryData(claveCargaNuevo('A20260916_1'))).toBe(ok.result.current.data)
    server.use(http.get('*/api/reparaciones/:idRep/borrador', () => new HttpResponse(null, { status: 403 })))
    const ko = renderHook(() => useCargaNuevo('A20260916_7'), { wrapper })
    await waitFor(() => expect(ko.result.current.isError).toBe(true))
    expect(ko.result.current.error).toBeInstanceOf(PermisoError)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('useCargaEditar carga con su clave', async () => {
    const { wrapper, qc } = envoltorio(SESION_SUPER)
    server.use(...handlersFormulario({ yaReparados: [121] }))
    const h = renderHook(() => useCargaEditar('R20260916_5'), { wrapper })
    await waitFor(() => expect(h.result.current.isSuccess).toBe(true))
    expect(h.result.current.data?.yaReparados).toEqual([121])
    expect(qc.getQueryData(claveCargaEditar('R20260916_5'))).toBe(h.result.current.data)
  })
  it('no se queda en caché al desmontar: reabrir vuelve a pedirlo todo', async () => {
    const { wrapper } = envoltorio(SESION_TEC)
    let peticiones = 0
    server.use(...handlersFormulario())
    server.use(http.get('*/api/componentes/agrupados', () => { peticiones++; return HttpResponse.json(agrupados()) }))
    const primera = renderHook(() => useCargaNuevo('A20260916_1'), { wrapper })
    await waitFor(() => expect(primera.result.current.isSuccess).toBe(true))
    primera.unmount()
    const segunda = renderHook(() => useCargaNuevo('A20260916_1'), { wrapper })
    await waitFor(() => expect(segunda.result.current.isSuccess).toBe(true))
    expect(peticiones).toBe(2)
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/api.test.tsx
```
Expected: FALLA (no existe `./api`).

- [x] **Step 3: `api.ts` — claves y cargas**

Crear `src/modules/taller/formulario/api.ts`:

```ts
import { useCallback } from 'react'
import { useMutation, useQuery, useQueryClient, type UseMutationResult, type UseQueryResult } from '@tanstack/react-query'
import {
  api, type AgotarRequest, type AsignacionActiva, type EditarReparacionRequest, type GuardarFilaRequest, type InsertarCompletaRequest,
} from '@/shared/api/client'
import { MSG_NO_ENCONTRADO, NoEncontradoError, PermisoError } from '@/shared/api/errors'
import { CLAVE_CONTADORES, claveAsignaciones, claveHistorial } from '../api'
import type { DatosEditar, DatosNuevo } from './estado'

export const claveCargaNuevo = (idAsignacion: string) => ['formulario', 'nuevo', idAsignacion] as const
export const claveCargaEditar = (idRep: string) => ['formulario', 'editar', idRep] as const
/** Raíz de las consultas de la campana. Se define aquí para no importar de notificaciones/; notificaciones/api.ts la reexporta. */
export const CLAVE_NOTIF = ['notificaciones'] as const

export type CargaNuevo = { datos: DatosNuevo; asignacionesActivas: AsignacionActiva[]; borradorJson: string | null }

async function pedirAgrupados() {
  return (await api.GET('/api/componentes/agrupados')).data ?? {}
}

/** Carga del flujo nuevo y Glass. 1) La asignación, para conocer el IMEI (vale igual con F5 o acceso directo por URL; su
 *  404 llega como NoEncontradoError). 2) En paralelo: componentes, solicitudes e incidencia (un fallo rechaza la carga y la
 *  vista lo muestra) y, en silencio, asignaciones activas del IMEI (→ []), modelo del teléfono (→ null) y borrador (→ null),
 *  SALVO un 403 del borrador, que se propaga: significa que la asignación no es del técnico de la sesión. */
export async function cargarNuevo(idAsignacion: string): Promise<CargaNuevo> {
  const asignacion = (await api.GET('/api/reparaciones/asignaciones/{idRep}', { params: { path: { idRep: idAsignacion } } })).data
  if (!asignacion) throw new NoEncontradoError(404, MSG_NO_ENCONTRADO)
  const imei = asignacion.imei
  const glass = idAsignacion.startsWith('AG')
  const [agrupados, solicitudes, incidencia, asignacionesActivas, modeloTelefono, borradorJson] = await Promise.all([
    pedirAgrupados(),
    api.GET('/api/reparaciones/asignaciones/{idAsignacion}/solicitudes', { params: { path: { idAsignacion } } }).then((r) => r.data ?? []),
    api
      .GET('/api/reparaciones/imei/{imei}/incidencia-activa', { params: { path: { imei }, query: { tipo: glass ? 'G' : 'R' } } })
      .then((r) => r.data?.value ?? null),
    api
      .GET('/api/reparaciones/imei/{imei}/asignaciones-activas', { params: { path: { imei } } })
      .then((r) => r.data ?? [])
      .catch((): AsignacionActiva[] => []),
    api
      .GET('/api/telefonos/{imei}/modelo', { params: { path: { imei } } })
      .then((r) => r.data?.value || null)
      .catch(() => null),
    api
      .GET('/api/reparaciones/{idRep}/borrador', { params: { path: { idRep: idAsignacion } } })
      .then((r) => r.data?.contenido ?? null)
      .catch((e: unknown) => {
        if (e instanceof PermisoError) throw e
        return null
      }),
  ])
  return {
    datos: { modo: glass ? 'glass' : 'nuevo', idAsignacion, imei, agrupados, solicitudes, incidencia, modeloTelefono },
    asignacionesActivas,
    borradorJson,
  }
}

/** Carga del modo edición: el detalle y, después, en paralelo, componentes, piezas ya reparadas en el IMEI (sin contar la
 *  editada) y, en silencio (→ []), las acciones "otro" de otras reparaciones del IMEI en la misma categoría. */
export async function cargarEditar(idRep: string): Promise<DatosEditar> {
  const detalle = (await api.GET('/api/reparaciones/{idRep}/detalle-edicion', { params: { path: { idRep } } })).data
  if (!detalle) throw new NoEncontradoError(404, MSG_NO_ENCONTRADO)
  const imei = detalle.imei
  const categoria = idRep.startsWith('G') ? 'G' : 'R'
  const [agrupados, yaReparados, accionesYaReparadas] = await Promise.all([
    pedirAgrupados(),
    api.GET('/api/reparaciones/imei/{imei}/ya-reparados', { params: { path: { imei }, query: { excluir: idRep } } }).then((r) => r.data ?? []),
    api
      .GET('/api/reparaciones/imei/{imei}/acciones', { params: { path: { imei }, query: { categoria, excluir: idRep } } })
      .then((r) => r.data ?? [])
      .catch((): string[] => []),
  ])
  return { modo: 'editar', idRep, detalle, agrupados, yaReparados, accionesYaReparadas }
}

// Una carga por apertura del formulario: no se comparte ni se reutiliza (gcTime 0: reabrir vuelve a pedirlo todo), no se
// refresca sola (pisaría lo que el técnico está editando) y no avisa por la vía global: la vista muestra el mensaje y vuelve
// a la lista.
const OPCIONES_CARGA = { gcTime: 0, staleTime: Infinity, refetchOnWindowFocus: false, refetchOnReconnect: false, refetchInterval: false, meta: { silenciarError: true } } as const

export function useCargaNuevo(idAsignacion: string): UseQueryResult<CargaNuevo> {
  return useQuery({ queryKey: claveCargaNuevo(idAsignacion), queryFn: () => cargarNuevo(idAsignacion), ...OPCIONES_CARGA })
}

export function useCargaEditar(idRep: string): UseQueryResult<DatosEditar> {
  return useQuery({ queryKey: claveCargaEditar(idRep), queryFn: () => cargarEditar(idRep), ...OPCIONES_CARGA })
}
```

(Los imports `useCallback`, `useMutation`, `useQueryClient`, `UseMutationResult` y los tipos de cuerpo se usan en el Step 5; hasta entonces `vitest` no los echa en falta, y `npm run check` no se ejecuta hasta el Step 6.)

```bash
npx vitest run src/modules/taller/formulario/api.test.tsx
```
Expected: FALLA todavía al importar (`borrarBorrador`, `useGuardarFila`… no exportados). Es lo esperado: los tests de las cargas se ven en verde tras el Step 5.

- [x] **Step 4: Tests de mutaciones, borrador, verificación y recarga (fallan)**

Añadir al final de `src/modules/taller/formulario/api.test.tsx`:

```tsx
describe('mutaciones del formulario', () => {
  const fila = { idCom: 101, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'bat', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }

  it('useGuardarFila envía el cuerpo tal cual y devuelve el idRep', async () => {
    const { wrapper } = envoltorio(SESION_TEC)
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    const cuerpo = { filas: [fila], imei: '355400000000111', idTec: 4, idRepAnterior: 'R20260910_3' }
    const m = renderHook(() => useGuardarFila(), { wrapper })
    await expect(m.result.current.mutateAsync({ idAsignacion: 'A20260916_1', cuerpo })).resolves.toBe('R20260916_9')
    expect(llamadas).toEqual([{ metodo: 'POST', ruta: '/api/reparaciones/A20260916_1/filas', cuerpo }])
  })
  it('useGuardarFila devuelve "?" si el servidor no da id', async () => {
    const { wrapper } = envoltorio(SESION_TEC)
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', () => HttpResponse.json({ value: null }, { status: 201 })))
    const m = renderHook(() => useGuardarFila(), { wrapper })
    await expect(m.result.current.mutateAsync({ idAsignacion: 'A20260916_1', cuerpo: { filas: [fila], imei: '355400000000111', idTec: 4, idRepAnterior: null } })).resolves.toBe('?')
  })
  it('useCompleta, useAgotarComponente y useEditarReparacion envían el cuerpo tal cual, en el orden en que se llaman', async () => {
    const { wrapper } = envoltorio(SESION_SUPER)
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    const agotar = { idCom: 121, cantidad: 4, descripcion: null }
    const completa = { filas: [fila], imei: '355400000000111', idTec: 3, idRepAnterior: null, idAsignacion: 'A20260916_1', categoria: null }
    const editar = { idComNuevo: 101, esReutilizadoNuevo: false, observacionNueva: null, nNuevas: 2, updatedAt: '2026-09-16T07:02:00' }
    const a = renderHook(() => useAgotarComponente(), { wrapper })
    const c = renderHook(() => useCompleta(), { wrapper })
    const e = renderHook(() => useEditarReparacion(), { wrapper })
    await a.result.current.mutateAsync({ idAsignacion: 'A20260916_1', cuerpo: agotar })
    await c.result.current.mutateAsync(completa)
    await e.result.current.mutateAsync({ idRep: 'R20260916_5', cuerpo: editar })
    expect(llamadas).toEqual([
      { metodo: 'POST', ruta: '/api/reparaciones/A20260916_1/agotar-componente', cuerpo: agotar },
      { metodo: 'POST', ruta: '/api/reparaciones/completa', cuerpo: completa },
      { metodo: 'PUT', ruta: '/api/reparaciones/R20260916_5', cuerpo: editar },
    ])
  })
  it('ninguna mutación abre el aviso global ante un 409 (meta.silenciarError): el literal lo pone la vista', async () => {
    const { wrapper } = envoltorio(SESION_SUPER)
    const conflicto = () => HttpResponse.json({ message: 'La asignación ya fue completada' }, { status: 409 })
    server.use(
      http.post('*/api/reparaciones/completa', conflicto),
      http.post('*/api/reparaciones/:idAsignacion/filas', conflicto),
      http.post('*/api/reparaciones/:idAsignacion/agotar-componente', conflicto),
      http.put('*/api/reparaciones/:idRep', conflicto),
    )
    const g = renderHook(() => useGuardarFila(), { wrapper })
    const a = renderHook(() => useAgotarComponente(), { wrapper })
    const c = renderHook(() => useCompleta(), { wrapper })
    const e = renderHook(() => useEditarReparacion(), { wrapper })
    const errores = await Promise.all([
      g.result.current.mutateAsync({ idAsignacion: 'A20260916_1', cuerpo: { filas: [fila], imei: '355400000000111', idTec: 4, idRepAnterior: null } }).catch((x: unknown) => x),
      a.result.current.mutateAsync({ idAsignacion: 'A20260916_1', cuerpo: { idCom: 121, cantidad: 0, descripcion: null } }).catch((x: unknown) => x),
      c.result.current.mutateAsync({ filas: [], imei: '355400000000111', idTec: 4, idRepAnterior: null, idAsignacion: 'A20260916_1', categoria: null }).catch((x: unknown) => x),
      e.result.current.mutateAsync({ idRep: 'R20260916_5', cuerpo: { idComNuevo: 101, esReutilizadoNuevo: false, observacionNueva: null, nNuevas: 1, updatedAt: '2026-09-16T07:02:00' } }).catch((x: unknown) => x),
    ])
    for (const err of errores) {
      expect(err).toBeInstanceOf(StaleDataError)
      expect((err as Error).message).toBe('La asignación ya fue completada')
    }
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('borrador y verificación de filas guardadas', () => {
  it('guardarBorrador envía { contenido } y borrarBorrador hace DELETE, sobre la ruta de la asignación', async () => {
    guardarSesion(SESION_TEC)
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    await guardarBorrador('A20260916_1', BORRADOR_JAVAFX)
    await borrarBorrador('A20260916_1')
    expect(llamadas).toEqual([
      { metodo: 'PUT', ruta: '/api/reparaciones/A20260916_1/borrador', cuerpo: { contenido: BORRADOR_JAVAFX } },
      { metodo: 'DELETE', ruta: '/api/reparaciones/A20260916_1/borrador', cuerpo: null },
    ])
  })
  it('las dos LANZAN si fallan: el silencio lo decide quien llama', async () => {
    guardarSesion(SESION_TEC)
    server.use(
      http.put('*/api/reparaciones/:idRep/borrador', () => new HttpResponse(null, { status: 403 })),
      http.delete('*/api/reparaciones/:idRep/borrador', () => HttpResponse.text('boom', { status: 503 })),
    )
    await expect(guardarBorrador('A20260916_1', '{}')).rejects.toBeInstanceOf(PermisoError)
    await expect(borrarBorrador('A20260916_1')).rejects.toBeInstanceOf(ConexionError)
  })
  it('idsReparacionesDelImei devuelve los idRep', async () => {
    guardarSesion(SESION_TEC)
    server.use(...handlersFormulario({ reparacionesImei: [reparacion(), reparacion({ idRep: 'R20260916_6' })] }))
    await expect(idsReparacionesDelImei('355400000000111')).resolves.toEqual(['R20260916_5', 'R20260916_6'])
    server.use(http.get('*/api/reparaciones/imei/:imei', () => new HttpResponse(null, { status: 404 })))
    await expect(idsReparacionesDelImei('355400000000111')).rejects.toBeInstanceOf(NoEncontradoError)
  })
})

describe('useRecargarAlCerrar', () => {
  it('invalida asignaciones, contadores, historiales y notificaciones, y nada más', () => {
    const { wrapper, qc } = envoltorio(SESION_TEC)
    const claves = [
      [...claveAsignaciones('REPARACION'), 'propio'], [...claveAsignaciones('GLASS'), 'propio'], [...claveAsignaciones('PULIDO'), 'propio'],
      [...CLAVE_CONTADORES, 'propio'], claveHistorial('REPARACION'), claveHistorial('GLASS'), claveHistorial('PULIDO'),
      [...CLAVE_NOTIF, 'contador'], [...CLAVE_NOTIF, 'solicitudes'],
    ]
    for (const clave of claves) qc.setQueryData(clave, [])
    qc.setQueryData(['clientes', 'activos'], [])
    const h = renderHook(() => useRecargarAlCerrar(), { wrapper })
    h.result.current()
    for (const clave of claves) expect(qc.getQueryState(clave)?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['clientes', 'activos'])?.isInvalidated).toBe(false)
  })
  it('la función es estable entre renders (sirve de dependencia de un efecto)', () => {
    const { wrapper } = envoltorio(SESION_TEC)
    const h = renderHook(() => useRecargarAlCerrar(), { wrapper })
    const primera = h.result.current
    h.rerender()
    expect(h.result.current).toBe(primera)
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/api.test.tsx
```
Expected: FALLA al importar (faltan las exportaciones del Step 5).

- [x] **Step 5: `api.ts` — mutaciones, borrador, verificación y recarga**

Añadir al final de `src/modules/taller/formulario/api.ts`:

```ts
// ── Mutaciones ───────────────────────────────────────────────────────────────────────────────────────────────────────
// Todas con meta.silenciarError: la vista pone el literal de la ficha ("No se pudo guardar la fila: …", etc.). Un corte de
// conexión lo avisa igualmente el mecanismo global. NINGUNA invalida nada: mientras el formulario está abierto la lista de
// debajo no se toca; se recarga al cerrar (useRecargarAlCerrar).

/** "✓ Guardar fila" y "✓ Guardar" de una acción. Devuelve el idRep generado ('?' si el servidor no lo da). */
export function useGuardarFila(): UseMutationResult<string, unknown, { idAsignacion: string; cuerpo: GuardarFilaRequest }> {
  return useMutation<string, unknown, { idAsignacion: string; cuerpo: GuardarFilaRequest }>({
    mutationFn: async ({ idAsignacion, cuerpo }) => {
      const { data } = await api.POST('/api/reparaciones/{idAsignacion}/filas', { params: { path: { idAsignacion } }, body: cuerpo })
      return data?.value ?? '?'
    },
    meta: { silenciarError: true },
  })
}

export function useAgotarComponente(): UseMutationResult<void, unknown, { idAsignacion: string; cuerpo: AgotarRequest }> {
  return useMutation<void, unknown, { idAsignacion: string; cuerpo: AgotarRequest }>({
    mutationFn: async ({ idAsignacion, cuerpo }) => {
      await api.POST('/api/reparaciones/{idAsignacion}/agotar-componente', { params: { path: { idAsignacion } }, body: cuerpo })
    },
    meta: { silenciarError: true },
  })
}

export function useCompleta(): UseMutationResult<void, unknown, InsertarCompletaRequest> {
  return useMutation<void, unknown, InsertarCompletaRequest>({
    mutationFn: async (cuerpo) => {
      await api.POST('/api/reparaciones/completa', { body: cuerpo })
    },
    meta: { silenciarError: true },
  })
}

export function useEditarReparacion(): UseMutationResult<void, unknown, { idRep: string; cuerpo: EditarReparacionRequest }> {
  return useMutation<void, unknown, { idRep: string; cuerpo: EditarReparacionRequest }>({
    mutationFn: async ({ idRep, cuerpo }) => {
      await api.PUT('/api/reparaciones/{idRep}', { params: { path: { idRep } }, body: cuerpo })
    },
    meta: { silenciarError: true },
  })
}

// ── Borrador y verificación ──────────────────────────────────────────────────────────────────────────────────────────
// Funciones sueltas (no pasan por TanStack Query, así que no hay aviso global) que LANZAN: quien llama decide el silencio.
// En el contrato el parámetro de ruta se llama idRep, aunque el valor es el id de la asignación.

export async function guardarBorrador(idAsignacion: string, contenido: string): Promise<void> {
  await api.PUT('/api/reparaciones/{idRep}/borrador', { params: { path: { idRep: idAsignacion } }, body: { contenido } })
}

export async function borrarBorrador(idAsignacion: string): Promise<void> {
  await api.DELETE('/api/reparaciones/{idRep}/borrador', { params: { path: { idRep: idAsignacion } } })
}

/** Ids de las reparaciones que existen hoy para el IMEI: con ellos se detectan las filas guardadas que otro usuario borró. */
export async function idsReparacionesDelImei(imei: string): Promise<string[]> {
  const { data } = await api.GET('/api/reparaciones/imei/{imei}', { params: { path: { imei } } })
  return (data ?? []).map((r) => r.idRep)
}

// ── Recarga al cerrar ────────────────────────────────────────────────────────────────────────────────────────────────

const CLAVES_AL_CERRAR: readonly (readonly unknown[])[] = [
  claveAsignaciones('REPARACION'), claveAsignaciones('GLASS'), claveAsignaciones('PULIDO'), CLAVE_CONTADORES,
  claveHistorial('REPARACION'), claveHistorial('GLASS'), claveHistorial('PULIDO'), CLAVE_NOTIF,
]

/** Al cerrar el formulario, se haya guardado o no: recarga la lista de debajo (pendientes e historiales), los contadores de
 *  Pendientes y la campana. Lanza las recargas y no las espera: cerrar no debe quedarse colgado de la red. */
export function useRecargarAlCerrar(): () => void {
  const qc = useQueryClient()
  return useCallback(() => {
    for (const queryKey of CLAVES_AL_CERRAR) void qc.invalidateQueries({ queryKey })
  }, [qc])
}
```

```bash
npx vitest run src/modules/taller/formulario/api.test.tsx
```
Expected: PASS (todos: cargas, hooks, mutaciones, borrador, verificación y recarga).

- [x] **Step 6: `npm run check` en verde**

```bash
npm run check
```
Expected: lint (imports relativos dentro del módulo: `../api`, `./estado`, `../../test/fabrica`; ninguno a `@/modules/*` ni a `@/app/*`), typecheck y toda la suite en verde. Si `tsc` protesta porque `useQuery` no infiere `UseQueryResult<CargaNuevo>` con `OPCIONES_CARGA`, se tipa el genérico en la llamada (`useQuery<CargaNuevo>({ … })`), sin tocar las opciones.

- [x] **Step 7: Commit**

```bash
git add src/modules/taller/formulario/api.ts src/modules/taller/formulario/api.test.tsx src/modules/taller/formulario/test/handlers.ts
git commit -m "feat(web): llamadas del formulario (carga, guardado por fila, agotar, completar, editar y borrador) con handlers de prueba"
```

---



### Task 12: Web — el formulario como diálogo gobernado por la URL: rutas, apertura desde Pendientes, cabecera, avisos, carga y cierre

**Files:**
- Create: `src/modules/taller/formulario/FormularioReparacion.tsx`, `src/modules/taller/formulario/FormularioReparacion.test.tsx`, `src/modules/taller/formulario/CabeceraFormulario.tsx`, `src/modules/taller/formulario/rutas.tsx`, `src/modules/taller/formulario/rutas.test.tsx`
- Modify: `src/app/router.tsx`, `src/modules/taller/pendientes/PendientesPage.tsx`, `src/modules/taller/pendientes/PendientesPage.test.tsx`

**Interfaces:**
- Consumes: `estadoInicial`, `reducir`, `AccionFormulario`, `EstadoFormulario` (W5); `filasVisibles`, `etiquetaImei`, `tituloPestana`, `textoConflicto` (W6); `ComboNavy` y `renderConRouter` (W8); `useCargaNuevo`, `useRecargarAlCerrar`, `CargaNuevo` (W10); `handlersFormulario` y `EscenarioFormulario` de `formulario/test/handlers.ts` (W10); `asignacionActiva`, `resumen` de la fábrica (W2); `traducirModelo` (`lib/modelos`); tokens del formulario (W9, ya añadidos por la Task 10); nombres accesibles y `data-testid` de W13.
- Produces:
  ```ts
  export function FormularioReparacion(props:
    | { modo: 'nuevo' | 'glass'; idAsignacion: string; onCerrar: () => void }
    | { modo: 'editar'; idRep: string; onCerrar: () => void }): JSX.Element
  export function CabeceraFormulario(props: { estado: EstadoFormulario; conflicto: string | null; dispatch: Dispatch<AccionFormulario>; onCerrar: () => void }): JSX.Element
  // formulario/rutas.tsx
  export function FormularioNuevoRuta(props: { glass: boolean }): JSX.Element   // useParams idAsignacion; cierra a /reparaciones/pendientes[/glass]
  ```
  Ruta `reparar/:idAsignacion` hija de `/reparaciones/pendientes` (W12) y `<Outlet />` al final de `PendientesPage`. En esta tarea `FormularioReparacion` pinta la barra superior, las bandas, la cabecera de columnas, el texto "Selecciona un modelo de iPhone para continuar" y, por cada `estado.filas`, un hueco `data-testid="fila-<prefijo>"` con el nombre del tipo (las Tasks 13–15 lo sustituyen por la fila real, la sub-fila, OTRAS ACCIONES y la zona de guardar).

**Ficha:** `formulario.md` — "Flujo nuevo: el botón "Añadir reparación" de Pendientes navega a…" (solo reparación); "Título (pestaña del navegador)…"; "Diálogo modal sobre la página…"; "Cerrar en flujo nuevo y Glass (✕, Atrás, Escape…): **nunca pregunta**…" (el volcado del borrador lo engancha la Task 16); "Al cerrar, con o sin guardar, se recarga la lista de debajo…"; "F5 o acceso directo por URL reabre el formulario…"; "Si falla la carga inicial…"; "Barra superior…"; "Etiqueta IMEI…" (flujo nuevo); "Combo de modelo…" (uso); "Modelo inicial sin solicitudes, flujo nuevo: filas ocultas y texto…"; "Banda de conflicto…"; "Banda de incidencia…"; "Cabecera de columnas…"; "403 al abrir…"; "Sin conexión: el banner existente del shell". `pendientes.md` — "(sub-proyecto 2) El botón abre el formulario de reparación de esa asignación…" (pestaña Reparaciones; la de Glass, en la Task 17).

Todos los comandos se ejecutan desde `gestion-reparaciones-web`, en la rama `feature/web-formulario`.

- [x] **Step 1: Tests de la cabecera, los avisos y el cierre (fallan)**

`src/modules/taller/formulario/FormularioReparacion.test.tsx`:

```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderConRouter, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { asignacionActiva } from '../test/fabrica'
import { FormularioReparacion } from './FormularioReparacion'
import { handlersFormulario, type EscenarioFormulario } from './test/handlers'

const TITULO = 'Nueva reparación — IMEI 355400000000111'

/** Monta el formulario de la asignación A20260916_1 (IMEI 355400000000111, del técnico de SESION_TEC) sobre un data router. */
function abrir(escenario: EscenarioFormulario = {}) {
  const onCerrar = vi.fn()
  server.use(...handlersFormulario(escenario))
  const r = renderConRouter([{ path: '/', element: <FormularioReparacion modo="nuevo" idAsignacion="A20260916_1" onCerrar={onCerrar} /> }], { sesion: SESION_TEC, ruta: '/' })
  return { ...r, onCerrar }
}

async function elegirModelo(nombre: string) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Filtrar por modelo' }))
  await userEvent.click(await screen.findByRole('option', { name: nombre }))
}

describe('FormularioReparacion · cabecera, avisos y cierre (ficha docs/paridad/formulario.md)', () => {
  it('sin modelo: cabecera de columnas visible y "Selecciona un modelo de iPhone para continuar"', async () => {
    abrir()
    expect(await screen.findByRole('dialog', { name: TITULO })).toBeInTheDocument()
    expect(screen.getByText('IMEI: 355400000000111')).toBeInTheDocument()
    expect(screen.getByText('Filtrar por modelo:')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Filtrar por modelo' })).toHaveTextContent('— Selecciona modelo —')
    for (const columna of ['Unit (+/-)', 'Componente', 'SKU', 'Stock', '¿Reutilizado?', 'Observación']) expect(screen.getByText(columna)).toBeInTheDocument()
    expect(screen.getByText('Selecciona un modelo de iPhone para continuar')).toBeInTheDocument()
    expect(screen.queryByTestId('fila-bat')).not.toBeInTheDocument()
    expect(screen.queryByTestId('banda-conflicto')).not.toBeInTheDocument()
    expect(screen.queryByTestId('banda-incidencia')).not.toBeInTheDocument()
  })

  it('elegir modelo muestra las filas (data-testid fila-*) en el orden del servidor, sin glass, marco ni otro', async () => {
    abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    expect(screen.getAllByTestId(/^fila-/).map((f) => f.getAttribute('data-testid'))).toEqual(['fila-bat', 'fila-cha', 'fila-lcd', 'fila-cam'])
    expect(screen.queryByText('Selecciona un modelo de iPhone para continuar')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Filtrar por modelo' })).toHaveTextContent('iPhone 13')
  })

  it('las opciones del combo son los modelos con SKU activo, traducidos y en orden de tienda', async () => {
    abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await userEvent.click(screen.getByRole('combobox', { name: 'Filtrar por modelo' }))
    expect((await screen.findAllByRole('option')).map((o) => o.textContent)).toEqual(['iPhone 13', 'iPhone 13 Pro Max', 'iPhone 14'])
  })

  it('modelo autodetectado: combo deshabilitado y filas a la vista', async () => {
    abrir({ modeloTelefono: '13' })
    await screen.findByRole('dialog', { name: TITULO })
    const combo = screen.getByRole('combobox', { name: 'Filtrar por modelo' })
    expect(combo).toBeDisabled()
    expect(combo).toHaveTextContent('iPhone 13')
    expect(screen.getByTestId('fila-bat')).toBeInTheDocument()
  })

  it('banda de conflicto con el literal, sin la asignación propia y con "(tú)"', async () => {
    abrir({
      activas: [
        asignacionActiva(),
        asignacionActiva({ idRep: 'A20260916_1', nombreTecnico: 'Técnico A', idTec: 4 }),
        asignacionActiva({ idRep: 'AP20260916_3', nombreTecnico: 'Técnico A', idTec: 4 }),
      ],
    })
    const banda = await screen.findByTestId('banda-conflicto')
    expect(banda.textContent).toBe('⚠ Este IMEI también está asignado a — Glass: Técnico H · Pulido: Técnico A (tú)')
    expect(banda).toHaveClass('bg-aviso-conflicto-bg', 'text-aviso-conflicto-text')
  })

  it('banda de incidencia con el idRep', async () => {
    abrir({ incidencia: 'R20260910_3' })
    const banda = await screen.findByTestId('banda-incidencia')
    expect(banda.textContent).toBe('⚠ Resuelve incidencia: R20260910_3')
    expect(banda).toHaveClass('text-aviso-incidencia-text')
  })

  it('✕ y Escape piden cerrar sin preguntar', async () => {
    const { onCerrar } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(2))
    expect(screen.queryByText('Salir sin guardar')).not.toBeInTheDocument()
  })
})
```

```bash
npm test -- FormularioReparacion
```
Expected: falla — `Failed to resolve import "./FormularioReparacion"`.

- [x] **Step 2: `CabeceraFormulario`**

`src/modules/taller/formulario/CabeceraFormulario.tsx`:

```tsx
import type { Dispatch } from 'react'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { traducirModelo } from '../lib/modelos'
import { etiquetaImei, type AccionFormulario, type EstadoFormulario } from './estado'

type Props = { estado: EstadoFormulario; conflicto: string | null; dispatch: Dispatch<AccionFormulario>; onCerrar: () => void }

/** Barra superior (etiqueta IMEI, "Filtrar por modelo:", combo y ✕) y, debajo, las bandas de aviso. No decide nada: el texto
 *  del conflicto llega calculado (`textoConflicto`) y la incidencia es la del estado. whitespace-pre conserva los dobles
 *  espacios de la etiqueta en edición. */
export function CabeceraFormulario({ estado, conflicto, dispatch, onCerrar }: Props) {
  return (
    <>
      <div className="flex items-center gap-4 bg-form-barra-bg px-4 py-2.5">
        <span className="text-[13px] font-bold whitespace-pre text-azul-medio">{etiquetaImei(estado)}</span>
        <span className="flex-1" />
        <span className="text-[12px] text-azul-gris">Filtrar por modelo:</span>
        <ComboNavy
          aria-label="Filtrar por modelo"
          valor={estado.modelo}
          opciones={estado.modelos.map((m) => ({ valor: m, etiqueta: traducirModelo(m) }))}
          onChange={(modelo) => dispatch({ tipo: 'CAMBIAR_MODELO', modelo })}
          textoVacio="— Selecciona modelo —"
          ancho={180}
          disabled={estado.modeloBloqueado}
        />
        <button type="button" aria-label="Cerrar formulario" onClick={onCerrar} className="cursor-pointer px-1 text-[16px] leading-none font-bold text-azul-gris">
          ✕
        </button>
      </div>
      {conflicto !== null && (
        <div data-testid="banda-conflicto" className="bg-aviso-conflicto-bg px-4 py-[5px] text-[12px] font-bold text-aviso-conflicto-text">
          {conflicto}
        </div>
      )}
      {estado.incidencia !== null && (
        <div data-testid="banda-incidencia" className="px-4 py-[5px] text-[12px] text-aviso-incidencia-text">
          {`⚠ Resuelve incidencia: ${estado.incidencia}`}
        </div>
      )}
    </>
  )
}
```

- [x] **Step 3: `FormularioReparacion` (carga, diálogo, título de la pestaña, recarga al cerrar)**

`src/modules/taller/formulario/FormularioReparacion.tsx`:

```tsx
import { useEffect, useReducer, useRef } from 'react'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { useCargaNuevo, useRecargarAlCerrar, type CargaNuevo } from './api'
import { CabeceraFormulario } from './CabeceraFormulario'
import { estadoInicial, filasVisibles, reducir, textoConflicto, tituloPestana } from './estado'

type Props =
  | { modo: 'nuevo' | 'glass'; idAsignacion: string; onCerrar: () => void }
  | { modo: 'editar'; idRep: string; onCerrar: () => void }

/** Cabecera de columnas: mismos anchos que las celdas de cada fila (la observación, 280 fijo). El botón derecho no tiene columna. */
const COLUMNAS = [
  { texto: 'Unit (+/-)', clase: 'w-[70px]' },
  { texto: 'Componente', clase: 'w-[100px]' },
  { texto: 'SKU', clase: 'w-[170px]' },
  { texto: 'Stock', clase: 'w-[70px] text-center' },
  { texto: '¿Reutilizado?', clase: 'w-[110px]' },
  { texto: 'Observación', clase: 'w-[280px]' },
] as const

/** El formulario de reparación como diálogo modal sobre la lista. La URL lo gobierna: quien lo monta (formulario/rutas.tsx)
 *  decide a dónde se vuelve en `onCerrar`. El flujo nuevo y el de glass comparten carga (`cargarNuevo` deduce la categoría
 *  del prefijo de la asignación); la edición tiene su propia carga y su propia ruta, y hasta que exista no pinta nada. */
export function FormularioReparacion(props: Props) {
  if (props.modo === 'editar') return null
  return <FormularioNuevo idAsignacion={props.idAsignacion} onCerrar={props.onCerrar} />
}

function FormularioNuevo({ idAsignacion, onCerrar }: { idAsignacion: string; onCerrar: () => void }) {
  const carga = useCargaNuevo(idAsignacion)
  const { mostrarError } = useAlerta()

  // Al cerrar —✕, Escape, Atrás o tras guardar— se recarga la lista de debajo: se hace al desmontar, que es lo único que
  // tienen en común las cuatro salidas. La ref evita depender de la identidad de la función en cada render.
  const recargar = useRecargarAlCerrar()
  const recargarRef = useRef(recargar)
  const onCerrarRef = useRef(onCerrar)
  useEffect(() => {
    recargarRef.current = recargar
    onCerrarRef.current = onCerrar
  })
  useEffect(() => () => recargarRef.current(), [])

  // La carga va con meta.silenciarError: el aviso lo da esta vista y después vuelve a la lista. 401 y corte de conexión ya
  // los gestiona el shell (redirección a login y banner): ahí solo se cierra.
  useEffect(() => {
    if (!carga.isError) return
    if (!esErrorGestionadoGlobalmente(carga.error)) mostrarError(mensajeDeError(carga.error))
    onCerrarRef.current()
  }, [carga.isError, carga.error, mostrarError])

  if (!carga.data) return null
  // El reductor se monta solo con la carga terminada: `key` evita reinicializarlo con datos de otra asignación.
  return <FormularioCargado key={idAsignacion} carga={carga.data} onCerrar={onCerrar} />
}

function FormularioCargado({ carga, onCerrar }: { carga: CargaNuevo; onCerrar: () => void }) {
  const { sesion } = useSession()
  const [estado, dispatch] = useReducer(reducir, carga.datos, estadoInicial)
  const titulo = tituloPestana(estado)
  const conflicto = textoConflicto(carga.asignacionesActivas, carga.datos.idAsignacion, sesion?.idTec ?? null)

  // El título de la ventana del JavaFX pasa a ser el de la pestaña; al cerrar vuelve el que había.
  useEffect(() => {
    const anterior = document.title
    document.title = titulo
    return () => {
      document.title = anterior
    }
  }, [titulo])

  return (
    <Dialog open onOpenChange={(abierto) => { if (!abierto) onCerrar() }}>
      {/* max-w-none y sm:max-w-none anulan el max-w y el sm:max-w-lg de DialogContent (tailwind-merge no descarta la variante
          con modificador si no se repite). Pulsar fuera no cierra: la ventana del JavaFX solo se cerraba con su ✕. */}
      <DialogContent
        aria-label={titulo}
        aria-describedby={undefined}
        showCloseButton={false}
        onInteractOutside={(e) => e.preventDefault()}
        className="flex h-[calc(100vh-48px)] min-h-[700px] w-[calc(100vw-48px)] max-w-none min-w-[960px] flex-col gap-0 overflow-hidden rounded-none border-0 bg-fondo-vista p-0 sm:max-w-none"
      >
        <DialogTitle className="sr-only">{titulo}</DialogTitle>
        <CabeceraFormulario estado={estado} conflicto={conflicto} dispatch={dispatch} onCerrar={onCerrar} />
        <div className="flex border-b border-form-cabecera-brd bg-form-cabecera-bg">
          {COLUMNAS.map((c) => (
            <span key={c.texto} className={`${c.clase} shrink-0 px-2.5 py-1.5 text-[12px] text-azul-gris`}>
              {c.texto}
            </span>
          ))}
        </div>
        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
          {filasVisibles(estado) ? (
            estado.filas.map((fila) => (
              <div key={fila.prefijo} data-testid={`fila-${fila.prefijo}`} className="flex min-h-[37px] items-center border-b border-form-fila-brd bg-fondo-input px-2.5 text-[12px]">
                {fila.nombre}
              </div>
            ))
          ) : (
            <p className="py-10 text-center text-[13px] text-azul-gris">Selecciona un modelo de iPhone para continuar</p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
```

```bash
npm test -- FormularioReparacion
```
Expected: pasan los 7 tests.

- [x] **Step 4: Tests de las rutas (fallan)**

`src/modules/taller/formulario/rutas.test.tsx`:

```tsx
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import type { RouteObject } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { renderConRouter, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { PendientesPage } from '../pendientes/PendientesPage'
import { resumen } from '../test/fabrica'
import { FormularioNuevoRuta } from './rutas'
import { handlersFormulario } from './test/handlers'

const TITULO = 'Nueva reparación — IMEI 355400000000111'
const LISTA = '/reparaciones/pendientes'
const FORMULARIO = '/reparaciones/pendientes/reparar/A20260916_1'

/** El mismo anidamiento que app/router.tsx: el formulario es ruta hija de la lista, que sigue montada debajo. */
const rutas: RouteObject[] = [
  { path: LISTA, element: <PendientesPage tipo="REPARACION" />, children: [{ path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass={false} /> }] },
]

let listasPedidas = 0
let contadoresPedidos = 0

beforeEach(() => {
  listasPedidas = 0
  contadoresPedidos = 0
  server.use(...handlersFormulario())
  // Registrados después: tienen prioridad sobre cualquier handler anterior con el mismo patrón.
  server.use(
    http.get('*/api/reparaciones/asignaciones', () => { listasPedidas++; return HttpResponse.json([resumen()]) }),
    http.get('*/api/glass/asignaciones', () => HttpResponse.json([])),
    http.get('*/api/pulidos/asignaciones', () => HttpResponse.json([])),
    http.get('*/api/reparaciones/pendientes/contadores', () => { contadoresPedidos++; return HttpResponse.json({ reparaciones: 1, glass: 0, pulidos: 0 }) }),
  )
})

const abrirEn = (ruta: string) => renderConRouter(rutas, { sesion: SESION_TEC, ruta })

describe('rutas del formulario (ficha docs/paridad/formulario.md · Apertura, rutas y cierre)', () => {
  it('el botón "Añadir reparación" navega a /reparaciones/pendientes/reparar/<id> y la lista sigue montada', async () => {
    const { router } = abrirEn(LISTA)
    await userEvent.click(await screen.findByRole('button', { name: 'Añadir reparación' }))
    expect(router.state.location.pathname).toBe(FORMULARIO)
    expect(await screen.findByRole('dialog', { name: TITULO })).toBeInTheDocument()
    // La lista queda debajo del modal (fuera del árbol accesible, pero montada).
    expect(screen.getByText('Mis asignaciones pendientes')).toBeInTheDocument()
    expect(screen.getByText('A20260916_1')).toBeInTheDocument()
  })

  it('acceso directo por URL abre el formulario con el IMEI', async () => {
    abrirEn(FORMULARIO)
    expect(await screen.findByRole('dialog', { name: TITULO })).toBeInTheDocument()
    expect(screen.getByText('IMEI: 355400000000111')).toBeInTheDocument()
  })

  it('title de la pestaña y restauración al cerrar', async () => {
    document.title = 'FSGR'
    abrirEn(FORMULARIO)
    await screen.findByRole('dialog', { name: TITULO })
    expect(document.title).toBe(TITULO)
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(document.title).toBe('FSGR'))
  })

  it('✕ cierra y vuelve a la lista sin preguntar; Escape también', async () => {
    const { router } = abrirEn(FORMULARIO)
    await screen.findByRole('dialog', { name: TITULO })
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(LISTA))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: 'Añadir reparación' }))
    await screen.findByRole('dialog', { name: TITULO })
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(router.state.location.pathname).toBe(LISTA))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('Atrás del navegador cierra (router.navigate(-1))', async () => {
    const { router } = abrirEn(LISTA)
    await userEvent.click(await screen.findByRole('button', { name: 'Añadir reparación' }))
    await screen.findByRole('dialog', { name: TITULO })
    await act(async () => { await router.navigate(-1) })
    expect(router.state.location.pathname).toBe(LISTA)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('al cerrar se vuelven a pedir asignaciones y contadores', async () => {
    abrirEn(FORMULARIO)
    await screen.findByRole('dialog', { name: TITULO })
    await waitFor(() => expect(listasPedidas).toBeGreaterThan(0))
    const listasAntes = listasPedidas
    const contadoresAntes = contadoresPedidos
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(listasPedidas).toBeGreaterThan(listasAntes))
    await waitFor(() => expect(contadoresPedidos).toBeGreaterThan(contadoresAntes))
  })

  it('fallo de carga: aviso con el mensaje y vuelta a la lista', async () => {
    server.use(http.get('*/api/componentes/agrupados', () => HttpResponse.json({ message: 'Catálogo de componentes no disponible' }, { status: 422 })))
    const { router } = abrirEn(FORMULARIO)
    expect(await screen.findByText('Catálogo de componentes no disponible')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe(LISTA))
    expect(screen.queryByRole('dialog', { name: TITULO })).not.toBeInTheDocument()
  })

  it('403 del borrador: "No tienes permisos para realizar esta acción." y vuelta a la lista', async () => {
    server.use(http.get('*/api/reparaciones/:idRep/borrador', () => new HttpResponse(null, { status: 403 })))
    const { router } = abrirEn(FORMULARIO)
    expect(await screen.findByText('No tienes permisos para realizar esta acción.')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe(LISTA))
    expect(screen.queryByRole('dialog', { name: TITULO })).not.toBeInTheDocument()
  })

  it('404 de la asignación: aviso y vuelta a la lista', async () => {
    server.use(http.get('*/api/reparaciones/asignaciones/:idRep', () => new HttpResponse(null, { status: 404 })))
    const { router } = abrirEn(FORMULARIO)
    expect(await screen.findByText('Recurso no encontrado.')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe(LISTA))
  })
})
```

```bash
npm test -- formulario/rutas
```
Expected: falla — `Failed to resolve import "./rutas"`.

- [x] **Step 5: `formulario/rutas.tsx`**

`src/modules/taller/formulario/rutas.tsx`:

```tsx
import { useNavigate, useParams } from 'react-router'
import { FormularioReparacion } from './FormularioReparacion'

/** Ruta hija de Pendientes (`reparar/:idAsignacion`): el formulario se pinta sobre la lista, que sigue montada debajo. Cerrar
 *  navega a la lista con `replace`: con F5 o acceso directo no hay entrada previa a la que volver. El Atrás del navegador sale
 *  de la ruta por sí solo y desmonta el formulario, que es lo mismo que cerrarlo. */
export function FormularioNuevoRuta({ glass }: { glass: boolean }) {
  const { idAsignacion = '' } = useParams()
  const navigate = useNavigate()
  const lista = glass ? '/reparaciones/pendientes/glass' : '/reparaciones/pendientes'
  return <FormularioReparacion key={idAsignacion} modo={glass ? 'glass' : 'nuevo'} idAsignacion={idAsignacion} onCerrar={() => navigate(lista, { replace: true })} />
}
```

```bash
npm test -- formulario/rutas
```
Expected: siguen fallando los tests que pasan por la lista ("Añadir reparación" está deshabilitado y `PendientesPage` no pinta `<Outlet />`: el diálogo no aparece); no quedan errores de import.

- [x] **Step 6: Tests de Pendientes (fallan)**

En `src/modules/taller/pendientes/PendientesPage.test.tsx`:

1. Añadir a los imports:

```tsx
import type { RouteObject } from 'react-router'
import { renderConProviders, renderConRouter, SESION_SUPER, SESION_TEC } from '@/test/render'
```
(la segunda línea sustituye al import actual de `@/test/render`).

2. Sustituir el test `el botón "Añadir reparación" está deshabilitado con el tooltip del formulario` por estos tres:

```tsx
  it('el botón "Añadir reparación" abre la ruta del formulario de su asignación y la lista sigue montada', async () => {
    const rutas: RouteObject[] = [
      { path: '/reparaciones/pendientes', element: <PendientesPage tipo="REPARACION" />, children: [{ path: 'reparar/:idAsignacion', element: <p>FORMULARIO ABIERTO</p> }] },
    ]
    const { router } = renderConRouter(rutas, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes' })
    const botones = await screen.findAllByRole('button', { name: 'Añadir reparación' })
    expect(botones).toHaveLength(4)
    expect(botones[0]).toBeEnabled()
    await userEvent.click(botones[0])
    expect(router.state.location.pathname).toBe('/reparaciones/pendientes/reparar/A20260915_29')
    expect(await screen.findByText('FORMULARIO ABIERTO')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Mis asignaciones pendientes' })).toBeInTheDocument()
  })
  it('"Añadir glass" sigue reservado, con el tooltip del formulario', async () => {
    server.use(http.get('*/api/glass/asignaciones', () => HttpResponse.json([{ ...glass(null), idRep: 'AG20260916_2', imei: '351111111111112' }])))
    renderConProviders(<PendientesPage tipo="GLASS" />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' })
    const boton = await screen.findByRole('button', { name: 'Añadir glass' })
    expect(boton).toBeDisabled()
    expect(boton.parentElement).toHaveAttribute('title', 'Disponible con el formulario de reparación (siguiente entrega)')
  })
  it('una asignación con solicitud pendiente conserva la franja naranja, el badge "Solicitud" y el SKU bajo el estado', async () => {
    server.use(http.get('*/api/reparaciones/asignaciones', () => HttpResponse.json([
      resumen({ idRep: 'A20260916_7', imei: '355400000000222', esSolicitud: 1, estadoSolicitud: 'PENDIENTE', tipoSolicitud: 'bati14', tiposSolicitud: 'bati14' }),
    ])))
    abrir()
    const fila = await screen.findByRole('row', { name: /A20260916_7 / })
    expect(fila).toHaveClass('border-l-8', 'border-l-fila-solicitud-brd')
    expect(within(fila).getByText('Solicitud')).toHaveClass('bg-fila-solicitud-bg', 'text-fila-solicitud-brd')
    expect(within(fila).getByText('bati14')).toBeInTheDocument()
  })
```

```bash
npm test -- PendientesPage
```
Expected: falla el primero de los tres (`expect(botones[0]).toBeEnabled()`: el botón sigue deshabilitado). Los otros dos ya pasan: verifican lo que el sub-proyecto 1 dejó hecho.

- [x] **Step 7: `PendientesPage` navega y pinta `<Outlet />`; ruta hija en `router.tsx`**

En `src/modules/taller/pendientes/PendientesPage.tsx`:

1. Añadir el import de React Router (debajo del de `react`):

```tsx
import { Outlet, useNavigate } from 'react-router'
```

2. Sustituir la función `BotonAnadir` completa (con su comentario) por:

```tsx
/** "Añadir reparación" abre el formulario de esa asignación como ruta hija (se pinta sobre esta lista). "Añadir glass" sigue
 *  reservado, con su tooltip, hasta que exista la ruta de glass; en una glass bloqueada no se pinta. */
function BotonAnadir({ rep, glass }: { rep: ReparacionResumen; glass: boolean }) {
  const navigate = useNavigate()
  if (glass && ocultarAnadirGlass(rep)) return null
  if (glass) {
    return (
      <span title={TOOLTIP_FORMULARIO} className="inline-block">
        <BotonPrimario disabled className="pointer-events-none">Añadir glass</BotonPrimario>
      </span>
    )
  }
  return <BotonPrimario onClick={() => navigate(`/reparaciones/pendientes/reparar/${rep.idRep}`)}>Añadir reparación</BotonPrimario>
}
```

3. En el JSX de `PendientesPage`, justo después del `<ConfirmDialog … />` y antes del `</div>` de cierre, añadir:

```tsx
      {/* El formulario de reparación es una ruta hija: se pinta aquí, como diálogo modal sobre la lista. */}
      <Outlet />
```

En `src/app/router.tsx`:

1. Añadir el import (orden alfabético, antes del de `HistorialPage`):

```tsx
import { FormularioNuevoRuta } from '@/modules/taller/formulario/rutas'
```

2. Sustituir la línea de `/reparaciones/pendientes` por:

```tsx
              {
                path: '/reparaciones/pendientes',
                element: <PendientesPage tipo="REPARACION" />,
                children: [{ path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass={false} /> }],
              },
```
(`/reparaciones/pendientes/glass` y `/reparaciones/pendientes/pulidos` son rutas hermanas, no hijas: no chocan con `reparar/:idAsignacion`.)

```bash
npm test -- PendientesPage formulario/rutas FormularioReparacion
```
Expected: todo en verde (los tests anteriores de `PendientesPage`, montados sin rutas hijas, siguen valiendo: `<Outlet />` no pinta nada).

- [x] **Step 8: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx src/modules/taller/formulario/CabeceraFormulario.tsx src/modules/taller/formulario/rutas.tsx src/modules/taller/formulario/rutas.test.tsx src/modules/taller/pendientes/PendientesPage.tsx src/modules/taller/pendientes/PendientesPage.test.tsx src/app/router.tsx
git commit -m "feat(web): formulario de reparación como diálogo gobernado por la URL: rutas, cabecera, avisos, carga y cierre"
```

---

### Task 13: Web — `FilaComponente`, diálogo "Observación" y "✓ Guardar fila"

**Files:**
- Create: `src/modules/taller/formulario/FilaComponente.tsx`, `src/modules/taller/formulario/FilaComponente.test.tsx`, `src/modules/taller/formulario/DialogoObservacionFila.tsx`, `src/modules/taller/formulario/useGuardado.ts`, `src/modules/taller/formulario/useGuardado.test.tsx`
- Modify: `src/modules/taller/formulario/FormularioReparacion.tsx`, `src/modules/taller/formulario/FormularioReparacion.test.tsx`

**Interfaces:**
- Consumes: `FilaEstado`, `EstadoFormulario`, `AccionFormulario`, `DatosNuevo`, `estadoInicial`, `reducir` (W4–W5); `botonDerecho`, `BotonDerecho`, `stockDe`, `filaSinSku`, `cuerpoGuardarFila` (W6; el SKU elegido lo pinta el propio combo, así que `componenteDe` no hace falta aquí); acciones `SUMAR`, `RESTAR`, `CAMBIAR_SKU`, `MARCAR_REUTILIZADO`, `PONER_OBSERVACION`, `BORRAR_OBSERVACION`, `PEDIR_CONFIRMACION_FILA`, `INICIO_GUARDAR_FILA`, `FILA_GUARDADA`, `FALLO_GUARDAR_FILA`; `claseStock` (W3); `ComboNavy` (W8); `useGuardarFila` (W10); `conRegistro`, `LlamadaRegistrada` (W10); `agrupados()` (W2); `Checkbox` y `Dialog` de `shared/ui`; `useAlerta`, `useSession`, `esErrorGestionadoGlobalmente`, `mensajeDeError`.
- Produces:
  ```ts
  export function FilaComponente(props: { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario>; onGuardarFila: (prefijo: string) => void; children?: ReactNode /* la sub-fila */ }): JSX.Element
  export function DialogoObservacionFila(props: { abierto: boolean; tipo: string; inicial: string; onGuardar: (texto: string) => void; onCancelar: () => void }): JSX.Element
  // formulario/useGuardado.ts
  export function useGuardado(args: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardado: () => void; antesDeCerrar?: () => Promise<void> }): {
    pulsarGuardar: () => void
    guardarFila: (prefijo: string) => void         // dos clics sobre la fila: PEDIR_CONFIRMACION_FILA y, al segundo, POST …/filas
    guardarAccion: (id: number) => void
  }
  export function fechaGuardado(d: Date): string   // 'dd/MM HH:mm' en hora LOCAL del navegador (auxiliar de esta tarea; la reutiliza la Task 15)
  ```
  En esta tarea `pulsarGuardar` y `guardarAccion` no hacen nada todavía (los completa la Task 15). Selectores de test de W13: `fila-<prefijo>` con `data-estado`, `contador-<prefijo>`, `stock-<prefijo>`, `boton-derecho-<prefijo>`, botones `"Sumar <tipo>"` / `"Restar <tipo>"`, casilla `"Reutilizado <tipo>"`, combo `"SKU de <tipo>"`, papelera `"Borrar observación de <tipo>"`.

**Ficha:** `formulario.md` — "Fila: fondo #F3F3F3, borde inferior…"; "Contador (70 px)…"; "Nombre del tipo…"; "Combo SKU: 170 px fijo…" (pintado); "Color del SKU, en la lista y en el botón…" (pintado); "Stock: 70 px centrado…"; "Tipo sin SKU para el modelo…" (opacidad 0,4 y todo deshabilitado); ""Reutilizado" (casilla con ese texto…)"; "Observación (ancho máximo 280)…"; "Diálogo "Observación" (440 px)…"; ""✓ Guardar fila" (botón derecho, alto 27…)"; "Dos clics: el primero cambia el texto a "✓ Confirmar"…"; "Guardar: botón deshabilitado mientras dura; `POST …/filas`…"; "Fila guardada: fondo #F1F8F1…"; "En una fila guardada, la papelera…"; "Error al guardar: el botón se rehabilita…"; de "Errores": "Guardar fila: "No se pudo guardar la fila: <mensaje>"" y "`<mensaje>`: el del servidor en 409 y 422…".

Todos los comandos se ejecutan desde `gestion-reparaciones-web`. La fila **no decide nada**: pinta `fila.controles`, los selectores de `estado.ts` y despacha.

- [x] **Step 1: Tests de la fila (fallan)**

`src/modules/taller/formulario/FilaComponente.test.tsx`:

```tsx
import { useReducer } from 'react'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders } from '@/test/render'
import { agrupados } from '../test/fabrica'
import { estadoInicial, reducir, type AccionFormulario, type DatosNuevo } from './estado'
import { FilaComponente } from './FilaComponente'

const DATOS: DatosNuevo = { modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes: [], incidencia: null, modeloTelefono: null }

/** Arnés: el reductor real con las filas pintadas. "Guardar fila" aquí solo pide la confirmación (el POST es de useGuardado). */
function Arnes({ acciones, alGuardar }: { acciones: AccionFormulario[]; alGuardar: (prefijo: string) => void }) {
  const [estado, dispatch] = useReducer(reducir, DATOS, (d) => acciones.reduce(reducir, estadoInicial(d)))
  return (
    <div>
      {estado.filas.map((fila) => (
        <FilaComponente
          key={fila.prefijo}
          estado={estado}
          fila={fila}
          dispatch={dispatch}
          onGuardarFila={(prefijo) => { alGuardar(prefijo); dispatch({ tipo: 'PEDIR_CONFIRMACION_FILA', prefijo }) }}
        />
      ))}
    </div>
  )
}

function montar(acciones: AccionFormulario[]) {
  const alGuardar = vi.fn()
  renderConProviders(<Arnes acciones={acciones} alGuardar={alGuardar} />)
  return { alGuardar }
}
const MODELO_13: AccionFormulario = { tipo: 'CAMBIAR_MODELO', modelo: '13' }
const MODELO_14: AccionFormulario = { tipo: 'CAMBIAR_MODELO', modelo: '14' }

describe('FilaComponente (ficha docs/paridad/formulario.md · Filas de componente)', () => {
  it('pinta contador, nombre, SKU, stock, Reutilizado y "Añadir observación"', () => {
    montar([MODELO_13])
    const fila = screen.getByTestId('fila-bat')
    expect(fila).toHaveAttribute('data-estado', 'normal')
    expect(fila).toHaveClass('bg-fondo-input', 'border-form-fila-brd')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
    expect(screen.getByTestId('contador-bat')).toHaveClass('text-gris-borde')
    expect(within(fila).getByText('Batería')).toBeInTheDocument()
    expect(within(fila).getByRole('combobox', { name: 'SKU de Batería' })).toHaveTextContent('bati13')
    expect(screen.getByTestId('stock-bat')).toHaveTextContent('5')
    expect(within(fila).getByText('Reutilizado')).toBeInTheDocument()
    expect(within(fila).getByRole('checkbox', { name: 'Reutilizado Batería' })).not.toBeChecked()
    expect(within(fila).getByRole('button', { name: 'Añadir observación' })).toBeEnabled()
    expect(within(fila).getByRole('button', { name: 'Restar Batería' })).toBeDisabled()
    expect(screen.queryByTestId('boton-derecho-bat')).not.toBeInTheDocument()
  })

  it('el SKU sin stock sale en rojo y el bajo en ámbar, en la lista y en el botón', async () => {
    montar([])
    const combo = within(screen.getByTestId('fila-bat')).getByRole('combobox', { name: 'SKU de Batería' })
    await userEvent.click(combo)
    const opciones = await screen.findAllByRole('option')
    expect(opciones.map((o) => o.textContent)).toEqual(['bati13', 'bati14', 'bati13promax'])
    expect(opciones[1].outerHTML).toContain('text-rojo-sin-stock')
    expect(opciones[2].outerHTML).toContain('text-fila-solicitud-brd')
    await userEvent.click(opciones[1])
    expect(screen.getByTestId('stock-bat')).toHaveTextContent('0')
    expect(combo).toHaveTextContent('bati14')
    expect(combo.outerHTML).toContain('text-rojo-sin-stock')
  })

  it('fila sin SKU: atenuada y todo deshabilitado, stock "—"', () => {
    montar([MODELO_14])
    const fila = screen.getByTestId('fila-cha')
    expect(fila).toHaveAttribute('data-estado', 'sinSku')
    expect(fila).toHaveClass('opacity-40')
    expect(screen.getByTestId('stock-cha')).toHaveTextContent('—')
    const combo = within(fila).getByRole('combobox', { name: 'SKU de Chasis' })
    expect(combo).toBeDisabled()
    expect(combo).toHaveTextContent('—')
    expect(within(fila).getByRole('button', { name: 'Sumar Chasis' })).toBeDisabled()
    expect(within(fila).getByRole('button', { name: 'Restar Chasis' })).toBeDisabled()
    expect(within(fila).getByRole('checkbox', { name: 'Reutilizado Chasis' })).toBeDisabled()
    expect(within(fila).getByRole('button', { name: 'Añadir observación' })).toBeDisabled()
  })

  it('+ y - despachan y respetan el deshabilitado', async () => {
    montar([MODELO_13])
    const bat = within(screen.getByTestId('fila-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Sumar Batería' }))
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('1')
    expect(screen.getByTestId('contador-bat')).toHaveClass('text-texto-incidencia')
    expect(bat.getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeDisabled()
    await userEvent.click(bat.getByRole('button', { name: 'Restar Batería' }))
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
    expect(bat.getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeEnabled()
    // Pantalla: stock 1 → tras sumar una, "+" se deshabilita.
    const lcd = within(screen.getByTestId('fila-lcd'))
    await userEvent.click(lcd.getByRole('button', { name: 'Sumar Pantalla' }))
    expect(lcd.getByRole('button', { name: 'Sumar Pantalla' })).toBeDisabled()
    // Reutilizado deshabilita + y -.
    const cam = within(screen.getByTestId('fila-cam'))
    await userEvent.click(cam.getByRole('checkbox', { name: 'Reutilizado Cámara' }))
    expect(cam.getByRole('checkbox', { name: 'Reutilizado Cámara' })).toBeChecked()
    expect(cam.getByRole('button', { name: 'Sumar Cámara' })).toBeDisabled()
    expect(cam.getByRole('button', { name: 'Restar Cámara' })).toBeDisabled()
  })

  it('diálogo Observación: cabecera "Observación para: <tipo>", guarda recortado, vacío no cambia, Cancelar', async () => {
    montar([MODELO_13])
    const bat = within(screen.getByTestId('fila-bat'))
    // Vacío: cierra sin cambiar nada.
    await userEvent.click(bat.getByRole('button', { name: 'Añadir observación' }))
    let dlg = within(await screen.findByRole('dialog', { name: 'Observación para: Batería' }))
    expect(dlg.getByRole('textbox')).toHaveValue('')
    expect(dlg.getByRole('textbox')).toHaveAttribute('rows', '5')
    await userEvent.click(dlg.getByRole('button', { name: 'Guardar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(bat.getByRole('button', { name: 'Añadir observación' })).toBeInTheDocument()
    // Cancelar: tampoco cambia.
    await userEvent.click(bat.getByRole('button', { name: 'Añadir observación' }))
    dlg = within(await screen.findByRole('dialog', { name: 'Observación para: Batería' }))
    await userEvent.type(dlg.getByRole('textbox'), 'No se guarda')
    await userEvent.click(dlg.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(bat.queryByText('No se guarda')).not.toBeInTheDocument()
    // Con texto: lo guarda recortado.
    await userEvent.click(bat.getByRole('button', { name: 'Añadir observación' }))
    dlg = within(await screen.findByRole('dialog', { name: 'Observación para: Batería' }))
    expect(dlg.getByRole('textbox')).toHaveValue('')
    await userEvent.type(dlg.getByRole('textbox'), '  Conector sucio  ')
    await userEvent.click(dlg.getByRole('button', { name: 'Guardar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(bat.getByText('Conector sucio').textContent).toBe('Conector sucio')
  })

  it('con observación: texto, papelera y sin botón de añadir; la papelera la borra sin confirmación', async () => {
    montar([MODELO_13, { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'Conector sucio' }])
    const bat = within(screen.getByTestId('fila-bat'))
    expect(bat.getByText('Conector sucio')).toHaveClass('truncate', 'text-texto-incidencia')
    expect(bat.queryByRole('button', { name: 'Añadir observación' })).not.toBeInTheDocument()
    await userEvent.click(bat.getByRole('button', { name: 'Borrar observación de Batería' }))
    expect(bat.queryByText('Conector sucio')).not.toBeInTheDocument()
    expect(bat.getByRole('button', { name: 'Añadir observación' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('"✓ Guardar fila" aparece con la fila activa, pasa a "✓ Confirmar" y cualquier cambio lo devuelve', async () => {
    const { alGuardar } = montar([MODELO_13])
    const bat = within(screen.getByTestId('fila-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Sumar Batería' }))
    const boton = screen.getByTestId('boton-derecho-bat')
    expect(boton).toHaveTextContent('✓ Guardar fila')
    expect(boton).toHaveClass('bg-azul-noche', 'h-[27px]')
    await userEvent.click(boton)
    expect(alGuardar).toHaveBeenCalledWith('bat')
    expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Confirmar')
    await userEvent.click(bat.getByRole('button', { name: 'Sumar Batería' }))
    expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Guardar fila')
    await userEvent.click(bat.getByRole('button', { name: 'Restar Batería' }))
    await userEvent.click(bat.getByRole('button', { name: 'Restar Batería' }))
    expect(screen.queryByTestId('boton-derecho-bat')).not.toBeInTheDocument()
    // Con "Reutilizado" también se ofrece.
    await userEvent.click(within(screen.getByTestId('fila-lcd')).getByRole('checkbox', { name: 'Reutilizado Pantalla' }))
    expect(screen.getByTestId('boton-derecho-lcd')).toHaveTextContent('✓ Guardar fila')
  })

  it('fila guardada: verde, "✓ Guardada dd/MM HH:mm" y todo deshabilitado, también la papelera', () => {
    montar([
      MODELO_13,
      { tipo: 'SUMAR', prefijo: 'bat' },
      { tipo: 'PONER_OBSERVACION', prefijo: 'bat', texto: 'Conector sucio' },
      { tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' },
      { tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' },
      { tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_5', fecha: '16/09 09:15' },
    ])
    const fila = screen.getByTestId('fila-bat')
    expect(fila).toHaveAttribute('data-estado', 'guardada')
    expect(fila).toHaveClass('bg-form-guardada-bg', 'border-fila-reparado-brd')
    const boton = screen.getByTestId('boton-derecho-bat')
    expect(boton).toHaveTextContent('✓ Guardada 16/09 09:15')
    expect(boton).toBeDisabled()
    expect(boton).toHaveClass('bg-recibido-bg', 'text-recibido-text')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('1')
    const bat = within(fila)
    expect(bat.getByRole('combobox', { name: 'SKU de Batería' })).toBeDisabled()
    expect(bat.getByRole('button', { name: 'Sumar Batería' })).toBeDisabled()
    expect(bat.getByRole('button', { name: 'Restar Batería' })).toBeDisabled()
    expect(bat.getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeDisabled()
    expect(bat.getByRole('button', { name: 'Borrar observación de Batería' })).toBeDisabled()
  })
})
```

```bash
npm test -- FilaComponente
```
Expected: falla — `Failed to resolve import "./FilaComponente"`.

- [x] **Step 2: `DialogoObservacionFila`**

`src/modules/taller/formulario/DialogoObservacionFila.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

type Props = { abierto: boolean; tipo: string; inicial: string; onGuardar: (texto: string) => void; onCancelar: () => void }

/** Diálogo "Observación" de una fila (440 px): cabecera "Observación para: <tipo>", área de 5 líneas precargada, "Guardar" verde
 *  a ancho completo y "Cancelar". Entrega el texto tal cual: recortar, y no cambiar nada si queda vacío, es regla del reductor
 *  (PONER_OBSERVACION). El ancho repite el sm: por el sm:max-w-lg de DialogContent (mismo truco que ConfirmDialog). */
export function DialogoObservacionFila({ abierto, tipo, inicial, onGuardar, onCancelar }: Props) {
  const [texto, setTexto] = useState(inicial)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga la observación actual cada vez que se abre
    if (abierto) setTexto(inicial)
  }, [abierto, inicial])
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent aria-describedby={undefined} className="max-w-[min(440px,calc(100%-2rem))] gap-2 bg-fondo-input p-4 sm:max-w-[min(440px,calc(100%-2rem))]">
        <DialogHeader>
          <DialogTitle className="text-[14px] font-bold text-azul-medio">{`Observación para: ${tipo}`}</DialogTitle>
        </DialogHeader>
        <textarea
          aria-label="Observación"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          rows={5}
          className="w-full resize-none rounded border border-gris-borde bg-superficie p-2 text-[13px] whitespace-pre-wrap"
        />
        <Button onClick={() => onGuardar(texto)} className="h-auto w-full rounded bg-fila-reparado-ico py-2 text-[12px] text-superficie hover:bg-fila-reparado-ico/90">
          Guardar
        </Button>
        <DialogFooter>
          <Button variant="outline" onClick={onCancelar}>Cancelar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [x] **Step 3: `FilaComponente`**

`src/modules/taller/formulario/FilaComponente.tsx`:

```tsx
import { useState, type Dispatch, type ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'
import { Checkbox } from '@/shared/ui/checkbox'
import { ComboNavy } from '@/shared/ui/ComboNavy'
import { claseStock } from '../lib/piezas'
import { DialogoObservacionFila } from './DialogoObservacionFila'
import { botonDerecho, filaSinSku, stockDe, type AccionFormulario, type BotonDerecho, type EstadoFormulario, type FilaEstado } from './estado'

type Props = { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario>; onGuardarFila: (prefijo: string) => void; children?: ReactNode }

type EstadoFila = 'normal' | 'guardada' | 'editada' | 'yaReparado' | 'sinSku'

/** Fondo y borde inferior por estado (valor de data-estado). */
const CLASES_ESTADO: Record<EstadoFila, string> = {
  normal: 'bg-fondo-input border-form-fila-brd',
  sinSku: 'bg-fondo-input border-form-fila-brd opacity-40',
  guardada: 'bg-form-guardada-bg border-fila-reparado-brd',
  editada: 'bg-fila-edicion-bg border-fila-edicion-brd',
  yaReparado: 'bg-fila-reparado-bg border-fila-reparado-brd',
}

function estadoDeFila(fila: FilaEstado): EstadoFila {
  if (fila.guardada !== null) return 'guardada'
  if (filaSinSku(fila)) return 'sinSku'
  if (fila.rol === 'editada') return 'editada'
  if (fila.rol === 'yaReparado') return 'yaReparado'
  return 'normal'
}

/** Etiqueta deshabilitada del botón derecho ("✓ Guardada …"): 11 px, radio 0, padding 4 10, con sus colores aunque esté disabled. */
const CLASE_ETIQUETA = 'h-[27px] rounded-none px-2.5 py-1 text-[11px]'

function BotonDerechoFila({ boton, prefijo, onGuardarFila }: { boton: BotonDerecho; prefijo: string; onGuardarFila: (prefijo: string) => void }) {
  const testid = `boton-derecho-${prefijo}`
  switch (boton.tipo) {
    case 'guardarFila':
      return (
        <button
          type="button"
          data-testid={testid}
          disabled={boton.deshabilitado}
          onClick={() => onGuardarFila(prefijo)}
          className="h-[27px] cursor-pointer rounded bg-azul-noche px-2.5 py-1 text-[11px] font-bold text-superficie disabled:cursor-default disabled:opacity-50"
        >
          {boton.texto}
        </button>
      )
    case 'guardada':
      return (
        <button type="button" data-testid={testid} disabled className={cn(CLASE_ETIQUETA, 'bg-recibido-bg text-recibido-text')}>
          {boton.texto}
        </button>
      )
    default:
      return null
  }
}

/** Una fila de componente: contador con "+" y "-", nombre, combo SKU, stock, "Reutilizado", observación y botón derecho; debajo,
 *  `children` (la sub-fila de agotado). No decide nada: el habilitado sale de `fila.controles` (una fila sin SKU para el modelo o
 *  ya guardada se pinta siempre deshabilitada), el botón derecho de `botonDerecho()` y cada gesto es un `dispatch`. */
export function FilaComponente({ estado, fila, dispatch, onGuardarFila, children }: Props) {
  const [dialogoObservacion, setDialogoObservacion] = useState(false)
  const { prefijo, nombre: tipo } = fila
  const estadoFila = estadoDeFila(fila)
  const inerte = estadoFila === 'sinSku' || estadoFila === 'guardada'
  const stock = stockDe(fila)
  const clasesMasMenos = 'h-[18px] w-[35px] cursor-pointer rounded-none bg-gris-borde p-0 text-[14px] leading-none font-bold text-gris-disabled disabled:cursor-default disabled:opacity-40'

  return (
    <div data-testid={`fila-${prefijo}`} data-estado={estadoFila} className={cn('border-b', CLASES_ESTADO[estadoFila])}>
      <div className="flex min-h-[37px] items-center">
        <div className="flex w-[70px] shrink-0 items-center">
          <span data-testid={`contador-${prefijo}`} className={cn('w-[34px] text-center font-[family-name:Inter,system-ui,sans-serif] text-[20px] font-normal', fila.cantidad > 0 ? 'text-texto-incidencia' : 'text-gris-borde')}>
            {fila.cantidad}
          </span>
          <div className="flex w-[35px] flex-col">
            <button type="button" aria-label={`Sumar ${tipo}`} disabled={inerte || !fila.controles.mas} onClick={() => dispatch({ tipo: 'SUMAR', prefijo })} className={clasesMasMenos}>+</button>
            <button type="button" aria-label={`Restar ${tipo}`} disabled={inerte || !fila.controles.menos} onClick={() => dispatch({ tipo: 'RESTAR', prefijo })} className={clasesMasMenos}>-</button>
          </div>
        </div>
        <span className="w-[100px] shrink-0 px-2.5 text-[12px]">{tipo}</span>
        <div className="w-[170px] shrink-0">
          <ComboNavy
            aria-label={`SKU de ${tipo}`}
            valor={fila.idCom === null ? null : String(fila.idCom)}
            opciones={fila.opciones.map((c) => ({ valor: String(c.idCom), etiqueta: c.tipo, clase: claseStock(c) }))}
            onChange={(valor) => dispatch({ tipo: 'CAMBIAR_SKU', prefijo, idCom: Number(valor) })}
            textoVacio="—"
            ancho={170}
            tamanoTexto={11}
            visibles={8}
            disabled={inerte || !fila.controles.sku}
          />
        </div>
        <span data-testid={`stock-${prefijo}`} className="w-[70px] shrink-0 px-2.5 text-center text-[12px]">{stock ?? '—'}</span>
        <label className="flex w-[110px] shrink-0 items-center gap-1.5 px-2.5 text-[12px]">
          <Checkbox
            aria-label={`Reutilizado ${tipo}`}
            checked={fila.reutilizado}
            disabled={inerte || !fila.controles.reutilizado}
            onCheckedChange={(valor) => dispatch({ tipo: 'MARCAR_REUTILIZADO', prefijo, valor: valor === true })}
          />
          Reutilizado
        </label>
        <div className="flex w-[280px] max-w-[280px] shrink-0 items-center gap-1 px-2.5">
          {fila.observacion === null ? (
            <button
              type="button"
              disabled={inerte || !fila.controles.observacion}
              onClick={() => setDialogoObservacion(true)}
              className="flex h-[27px] cursor-pointer items-center gap-1.5 rounded-none bg-form-obs-bg px-2.5 py-1 text-[11px] text-gris-disabled disabled:cursor-default disabled:opacity-40"
            >
              <img src="/editar.png" alt="" className="h-3.5 w-3.5" />
              Añadir observación
            </button>
          ) : (
            <>
              <span title={fila.observacion} className="min-w-0 truncate text-[12px] text-texto-incidencia">{fila.observacion}</span>
              <button
                type="button"
                aria-label={`Borrar observación de ${tipo}`}
                disabled={inerte || !fila.controles.observacion}
                onClick={() => dispatch({ tipo: 'BORRAR_OBSERVACION', prefijo })}
                className="shrink-0 cursor-pointer bg-transparent px-1 py-0.5 disabled:cursor-default disabled:opacity-40"
              >
                <img src="/borrar.png" alt="" className="h-5 w-5" />
              </button>
            </>
          )}
        </div>
        <div className="ml-auto shrink-0 pr-2.5">
          <BotonDerechoFila boton={botonDerecho(estado, fila)} prefijo={prefijo} onGuardarFila={onGuardarFila} />
        </div>
      </div>
      {children}
      <DialogoObservacionFila
        abierto={dialogoObservacion}
        tipo={tipo}
        inicial={fila.observacion ?? ''}
        onGuardar={(texto) => { dispatch({ tipo: 'PONER_OBSERVACION', prefijo, texto }); setDialogoObservacion(false) }}
        onCancelar={() => setDialogoObservacion(false)}
      />
    </div>
  )
}
```

```bash
npm test -- FilaComponente
```
Expected: pasan los 8 tests.

- [x] **Step 4: Tests de `useGuardado` (fallan)**

`src/modules/taller/formulario/useGuardado.test.tsx`:

```tsx
import { useReducer } from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { agrupados } from '../test/fabrica'
import { estadoInicial, reducir, type DatosNuevo } from './estado'
import { conRegistro, type LlamadaRegistrada } from './test/handlers'
import { fechaGuardado, useGuardado } from './useGuardado'

const DATOS: DatosNuevo = { modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes: [], incidencia: 'R20260910_3', modeloTelefono: '13' }

/** Escrituras en orden, sin las del borrador. `conRegistro` anota `metodo` en mayúsculas, `ruta` = pathname y `cuerpo` = JSON recibido. */
const escrituras = (llamadas: LlamadaRegistrada[]) =>
  llamadas.filter((l) => !l.ruta.endsWith('/borrador'))

/** Arnés del hook para la fila de batería (modelo 13: bati13, idCom 101, stock 5). */
function ArnesFila() {
  const [estado, dispatch] = useReducer(reducir, DATOS, estadoInicial)
  const { guardarFila } = useGuardado({ estado, dispatch, onGuardado: () => {} })
  const bat = estado.filas.find((f) => f.prefijo === 'bat')
  return (
    <div>
      <button onClick={() => dispatch({ tipo: 'SUMAR', prefijo: 'bat' })}>sumar</button>
      <button onClick={() => guardarFila('bat')}>guardar fila</button>
      <output data-testid="bat">{JSON.stringify({ confirmando: bat?.confirmandoGuardar, guardando: bat?.guardando, guardada: bat?.guardada })}</output>
    </div>
  )
}
const leer = () => JSON.parse(screen.getByTestId('bat').textContent ?? '{}') as { confirmando: boolean; guardando: boolean; guardada: { idRep: string; fecha: string } | null }

beforeEach(() => {
  // Solo se falsea Date (la fecha de "✓ Guardada" es la hora local): los temporizadores reales siguen sirviendo a MSW y a user-event.
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 8, 16, 9, 15))
})
afterEach(() => vi.useRealTimers())

describe('useGuardado · guardar fila', () => {
  it('fechaGuardado usa la hora local del navegador con ceros a la izquierda', () => {
    expect(fechaGuardado(new Date(2026, 8, 6, 9, 5))).toBe('06/09 09:05')
    expect(fechaGuardado(new Date(2026, 11, 24, 18, 40))).toBe('24/12 18:40')
  })

  it('el primer clic pide confirmación sin llamar; el segundo hace el POST con el cuerpo exacto y marca la fila guardada', async () => {
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    renderConProviders(<ArnesFila />, { sesion: SESION_TEC })
    await userEvent.click(screen.getByRole('button', { name: 'sumar' }))
    await userEvent.click(screen.getByRole('button', { name: 'guardar fila' }))
    expect(leer().confirmando).toBe(true)
    expect(escrituras(llamadas)).toEqual([])
    await userEvent.click(screen.getByRole('button', { name: 'guardar fila' }))
    await waitFor(() => expect(leer().guardada).toEqual({ idRep: 'R20260916_9', fecha: '16/09 09:15' }))
    expect(escrituras(llamadas)).toEqual([
      {
        metodo: 'POST',
        ruta: '/api/reparaciones/A20260916_1/filas',
        cuerpo: {
          filas: [{ idCom: 101, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'bat', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }],
          imei: '355400000000111',
          idTec: 4,
          idRepAnterior: 'R20260910_3',
        },
      },
    ])
  })

  it('error 409: aviso "No se pudo guardar la fila: <mensaje del servidor>" y la fila vuelve a estar editable', async () => {
    server.use(...conRegistro().handlers)
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', () => HttpResponse.json({ message: 'La asignación ya fue eliminada o completada' }, { status: 409 })))
    renderConProviders(<ArnesFila />, { sesion: SESION_TEC })
    await userEvent.click(screen.getByRole('button', { name: 'sumar' }))
    await userEvent.click(screen.getByRole('button', { name: 'guardar fila' }))
    await userEvent.click(screen.getByRole('button', { name: 'guardar fila' }))
    expect(await screen.findByText('No se pudo guardar la fila: La asignación ya fue eliminada o completada')).toBeInTheDocument()
    expect(leer()).toEqual({ confirmando: false, guardando: false, guardada: null })
  })

  it('un corte de conexión no añade el literal de la fila (ya avisa el mecanismo global) pero rehabilita', async () => {
    server.use(...conRegistro().handlers)
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', () => new HttpResponse(null, { status: 503 })))
    renderConProviders(<ArnesFila />, { sesion: SESION_TEC })
    await userEvent.click(screen.getByRole('button', { name: 'sumar' }))
    await userEvent.click(screen.getByRole('button', { name: 'guardar fila' }))
    await userEvent.click(screen.getByRole('button', { name: 'guardar fila' }))
    expect(await screen.findByText('Sin conexión con el servidor: HTTP 503')).toBeInTheDocument()
    expect(screen.queryByText(/No se pudo guardar la fila/)).not.toBeInTheDocument()
    expect(leer()).toEqual({ confirmando: false, guardando: false, guardada: null })
  })
})
```

```bash
npm test -- useGuardado
```
Expected: falla — `Failed to resolve import "./useGuardado"`.

- [x] **Step 5: `useGuardado` con `guardarFila`**

`src/modules/taller/formulario/useGuardado.ts`:

```ts
import type { Dispatch } from 'react'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { useGuardarFila } from './api'
import { cuerpoGuardarFila, type AccionFormulario, type EstadoFormulario } from './estado'

type Args = { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardado: () => void; antesDeCerrar?: () => Promise<void> }
type Guardado = { pulsarGuardar: () => void; guardarFila: (prefijo: string) => void; guardarAccion: (id: number) => void }

/** Fecha de "✓ Guardada": hora LOCAL del navegador como texto dd/MM HH:mm, sin año (no la de Madrid de shared/lib/fechas). */
export function fechaGuardado(d: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0')
  return `${dos(d.getDate())}/${dos(d.getMonth() + 1)} ${dos(d.getHours())}:${dos(d.getMinutes())}`
}

/** Orquesta los guardados del formulario. Las mutaciones van con meta.silenciarError: el literal de la ficha lo pone este hook,
 *  salvo que el error ya lo gestione el shell (401 → login; corte de conexión → banner y su aviso). Se usa mutateAsync: los
 *  callbacks por llamada de `mutate` solo se disparan para la última, y dos filas pueden guardarse a la vez. */
export function useGuardado(args: Args): Guardado {
  // onGuardado y antesDeCerrar son de "Terminar asignación": aquí todavía no se usan.
  const { estado, dispatch } = args
  const { sesion } = useSession()
  const { mostrarError } = useAlerta()
  const guardarFilaMut = useGuardarFila()
  // En flujo nuevo y glass el servidor toma el técnico del token; el contrato exige el campo y se envía el de la sesión.
  const idTecSesion = sesion?.idTec ?? 0

  function avisar(e: unknown, literal: string) {
    if (!esErrorGestionadoGlobalmente(e)) mostrarError(literal)
  }

  async function ejecutarGuardarFila(idAsignacion: string, prefijo: string) {
    dispatch({ tipo: 'INICIO_GUARDAR_FILA', prefijo })
    try {
      const idRep = await guardarFilaMut.mutateAsync({ idAsignacion, cuerpo: cuerpoGuardarFila(estado, prefijo, idTecSesion) })
      dispatch({ tipo: 'FILA_GUARDADA', prefijo, idRep, fecha: fechaGuardado(new Date()) })
    } catch (e) {
      dispatch({ tipo: 'FALLO_GUARDAR_FILA', prefijo })
      avisar(e, `No se pudo guardar la fila: ${mensajeDeError(e)}`)
    }
  }

  function guardarFila(prefijo: string) {
    const fila = estado.filas.find((f) => f.prefijo === prefijo)
    if (!fila || fila.guardando || fila.guardada !== null || estado.idAsignacion === null) return
    if (!fila.confirmandoGuardar) {
      dispatch({ tipo: 'PEDIR_CONFIRMACION_FILA', prefijo })
      return
    }
    void ejecutarGuardarFila(estado.idAsignacion, prefijo)
  }

  // pulsarGuardar y guardarAccion llegan con la zona de guardar y OTRAS ACCIONES; hasta entonces no hacen nada.
  return { pulsarGuardar: () => {}, guardarFila, guardarAccion: () => {} }
}
```

```bash
npm test -- useGuardado
```
Expected: pasan los 4 tests.

- [x] **Step 6: Tests de integración en el formulario (fallan)**

En `src/modules/taller/formulario/FormularioReparacion.test.tsx`:

1. Sustituir todo lo que hay antes de `async function elegirModelo` (los imports, `TITULO` y `abrir`) por:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderConRouter, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { asignacionActiva } from '../test/fabrica'
import { FormularioReparacion } from './FormularioReparacion'
import { conRegistro, type EscenarioFormulario, type LlamadaRegistrada } from './test/handlers'

const TITULO = 'Nueva reparación — IMEI 355400000000111'

/** Monta el formulario de la asignación A20260916_1 (IMEI 355400000000111, del técnico de SESION_TEC) sobre un data router. */
function abrir(escenario: EscenarioFormulario = {}) {
  const onCerrar = vi.fn()
  const { handlers, llamadas } = conRegistro(escenario)
  server.use(...handlers)
  const r = renderConRouter([{ path: '/', element: <FormularioReparacion modo="nuevo" idAsignacion="A20260916_1" onCerrar={onCerrar} /> }], { sesion: SESION_TEC, ruta: '/' })
  return { ...r, onCerrar, llamadas }
}

/** Escrituras del formulario en orden, sin las del borrador. `conRegistro` anota `metodo` en mayúsculas (`request.method`), `ruta` =
 *  pathname (`/api/reparaciones/...`) y `cuerpo` = JSON recibido (null si no hay): se comparan tal cual. */
const escrituras = (llamadas: LlamadaRegistrada[]) =>
  llamadas.filter((l) => !l.ruta.endsWith('/borrador'))
```

2. Añadir al final del fichero este segundo `describe`:

```tsx
describe('FormularioReparacion · filas y "✓ Guardar fila"', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 16, 9, 15))
  })
  afterEach(() => vi.useRealTimers())

  it('"✓ Guardar fila" → "✓ Confirmar" → POST con el cuerpo exacto; éxito: fila verde con "✓ Guardada dd/MM HH:mm" y controles deshabilitados', async () => {
    const { llamadas } = abrir({ incidencia: 'R20260910_3' })
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    const bat = within(screen.getByTestId('fila-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Confirmar')
    expect(escrituras(llamadas)).toEqual([])
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await waitFor(() => expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Guardada 16/09 09:15'))
    expect(screen.getByTestId('fila-bat')).toHaveAttribute('data-estado', 'guardada')
    expect(bat.getByRole('button', { name: 'Sumar Batería' })).toBeDisabled()
    expect(bat.getByRole('combobox', { name: 'SKU de Batería' })).toBeDisabled()
    expect(escrituras(llamadas)).toEqual([
      {
        metodo: 'POST',
        ruta: '/api/reparaciones/A20260916_1/filas',
        cuerpo: {
          filas: [{ idCom: 101, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'bat', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }],
          imei: '355400000000111',
          idTec: 4,
          idRepAnterior: 'R20260910_3',
        },
      },
    ])
  })

  it('cambiar algo tras el primer clic vuelve a "✓ Guardar fila" y no se llama al servidor', async () => {
    const { llamadas } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    const bat = within(screen.getByTestId('fila-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await userEvent.click(bat.getByRole('button', { name: 'Sumar Batería' }))
    expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Guardar fila')
    expect(escrituras(llamadas)).toEqual([])
  })

  it('error 409: aviso "No se pudo guardar la fila: <mensaje del servidor>" y la fila sigue editable', async () => {
    abrir()
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', () => HttpResponse.json({ message: 'La asignación ya fue eliminada o completada' }, { status: 409 })))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await userEvent.click(within(screen.getByTestId('fila-bat')).getByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    expect(await screen.findByText('No se pudo guardar la fila: La asignación ya fue eliminada o completada')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    expect(screen.getByTestId('fila-bat')).toHaveAttribute('data-estado', 'normal')
    expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Guardar fila')
    expect(screen.getByTestId('boton-derecho-bat')).toBeEnabled()
    expect(within(screen.getByTestId('fila-bat')).getByRole('button', { name: 'Sumar Batería' })).toBeEnabled()
  })

  it('mientras guarda el botón está deshabilitado', async () => {
    abrir()
    let soltar: () => void = () => {}
    const puerta = new Promise<void>((resolve) => { soltar = resolve })
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', async () => { await puerta; return HttpResponse.json({ value: 'R20260916_9' }) }))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await userEvent.click(within(screen.getByTestId('fila-bat')).getByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await waitFor(() => expect(screen.getByTestId('boton-derecho-bat')).toBeDisabled())
    expect(screen.getByTestId('boton-derecho-bat')).not.toHaveTextContent('Guardada')
    soltar()
    await waitFor(() => expect(screen.getByTestId('boton-derecho-bat')).toHaveTextContent('✓ Guardada 16/09 09:15'))
  })
})
```

```bash
npm test -- FormularioReparacion
```
Expected: fallan los 4 tests nuevos (`Unable to find a label/role … "Sumar Batería"`: el formulario aún pinta el hueco, no `FilaComponente`). Los 7 de la Task 12 siguen en verde.

- [x] **Step 7: `FormularioReparacion` pinta `FilaComponente` y usa `useGuardado`**

En `src/modules/taller/formulario/FormularioReparacion.tsx`:

1. Añadir los imports (orden alfabético dentro del bloque de imports relativos):

```tsx
import { FilaComponente } from './FilaComponente'
import { useGuardado } from './useGuardado'
```

2. En `FormularioCargado`, debajo de la línea `const conflicto = …`, añadir:

```tsx
  // Guardar (fila a fila, o todo) cierra el formulario igual que ✕: la recarga de la lista la hace el desmontaje.
  const guardado = useGuardado({ estado, dispatch, onGuardado: onCerrar })
```

3. Sustituir el bloque `estado.filas.map(…)` (el `<div data-testid={`fila-…`}>` provisional) por:

```tsx
            estado.filas.map((fila) => <FilaComponente key={fila.prefijo} estado={estado} fila={fila} dispatch={dispatch} onGuardarFila={guardado.guardarFila} />)
```

```bash
npm test -- FormularioReparacion FilaComponente useGuardado
```
Expected: todo en verde (11 + 8 + 4).

- [x] **Step 8: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/FilaComponente.tsx src/modules/taller/formulario/FilaComponente.test.tsx src/modules/taller/formulario/DialogoObservacionFila.tsx src/modules/taller/formulario/useGuardado.ts src/modules/taller/formulario/useGuardado.test.tsx src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx
git commit -m "feat(web): filas de componente del formulario con observación y guardado por fila"
```

---

### Task 14: Web — `SubFilaAgotado`, diálogos "Solicitar pieza" y "Editar descripción de solicitud", indicadores de solicitudes guardadas

**Files:**
- Create: `src/modules/taller/formulario/SubFilaAgotado.tsx`, `src/modules/taller/formulario/SubFilaAgotado.test.tsx`, `src/modules/taller/formulario/DialogoSolicitarPieza.tsx`, `src/modules/taller/formulario/DialogoDescripcionSolicitud.tsx`
- Modify: `src/modules/taller/formulario/FormularioReparacion.tsx`, `src/modules/taller/formulario/FormularioReparacion.test.tsx`, `src/modules/taller/formulario/FilaComponente.tsx` (botón derecho `enCamino` / `recibido`), `src/modules/taller/formulario/FilaComponente.test.tsx`

**Interfaces:**
- Consumes: `subFila`, `SubFila`, `botonDerecho`, `TEXTO_SIN_STOCK`, `TEXTO_LIMITE` (W6); acciones `CONFIRMAR_AGOTADO`, `EDITAR_DESCRIPCION_AGOTADO`, `CANCELAR_AGOTADO` (W5); `FilaEstado.agotado` (W4); `FilaComponente` (Task 13); `solicitudAsignacion()` y `agrupados()` (W2); `Dialog` de `shared/ui`; tokens `form-agotado-bg`, `form-agotado-text`, `ambar`, `rojo-cancelar`, `recibido-bg/text`, `tipo-reparacion-bg/text` (W9).
- Produces:
  ```ts
  export function SubFilaAgotado(props: { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario> }): JSX.Element | null
  export function DialogoSolicitarPieza(props: { abierto: boolean; tipo: string; variante: 'sinStock' | 'limite'; stock: number; inicial: string; onConfirmar: (descripcion: string) => void; onCancelar: () => void }): JSX.Element
  export function DialogoDescripcionSolicitud(props: { abierto: boolean; inicial: string; onGuardar: (descripcion: string) => void; onCancelarSolicitud: () => void; onCancelar: () => void }): JSX.Element
  ```
  Selectores de W13: `subfila-<prefijo>` con `data-variante="sinStock|limite|confirmada"`; lápiz `"Editar descripción de solicitud de <tipo>"`. `FormularioReparacion` pasa `<SubFilaAgotado />` como `children` de cada `FilaComponente`.

**Ficha:** `formulario.md` — toda la sección "Sub-fila de agotado y solicitud de pieza" (pintado y diálogos: "Sub-fila bajo la fila…", "Solo existe en flujo nuevo y Glass…", "Diálogo "Solicitar pieza — <tipo>" (460 px)…", "Confirmar es **local**…", "Estado confirmado…", "Lápiz → diálogo "Editar descripción de solicitud"…", ""Guardar descripción" actualiza…", ""Cancelar solicitud" sobre una solicitud local…") y, de "Solicitudes ya guardadas (estados)", el pintado de "**Pendiente**…", "**En camino**…", "**Recibido**…", "**Rechazada**…" y "El lápiz solo funciona sobre la solicitud local…".

Todos los comandos se ejecutan desde `gestion-reparaciones-web`. La variante, el texto de la etiqueta y si el lápiz funciona los decide `subFila()`; los componentes solo pintan. Los dobles espacios tras "⚠" y "✓" son literales: las etiquetas llevan `whitespace-pre-wrap` y los tests comparan `textContent`.

- [x] **Step 1: Tests de la sub-fila y sus diálogos (fallan)**

`src/modules/taller/formulario/SubFilaAgotado.test.tsx`:

```tsx
import { useReducer } from 'react'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { SolicitudAsignacion } from '@/shared/api/client'
import { renderConProviders } from '@/test/render'
import { agrupados, solicitudAsignacion } from '../test/fabrica'
import { estadoInicial, reducir, type AccionFormulario, type DatosNuevo } from './estado'
import { FilaComponente } from './FilaComponente'
import { SubFilaAgotado } from './SubFilaAgotado'

const datos = (solicitudes: SolicitudAsignacion[] = []): DatosNuevo => ({ modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes, incidencia: null, modeloTelefono: null })

function Arnes({ solicitudes, acciones }: { solicitudes: SolicitudAsignacion[]; acciones: AccionFormulario[] }) {
  const [estado, dispatch] = useReducer(reducir, datos(solicitudes), (d) => acciones.reduce(reducir, estadoInicial(d)))
  return (
    <div>
      {estado.filas.map((fila) => (
        <FilaComponente key={fila.prefijo} estado={estado} fila={fila} dispatch={dispatch} onGuardarFila={() => {}}>
          <SubFilaAgotado estado={estado} fila={fila} dispatch={dispatch} />
        </FilaComponente>
      ))}
      <output data-testid="revision">{estado.revision}</output>
    </div>
  )
}
const montar = (acciones: AccionFormulario[], solicitudes: SolicitudAsignacion[] = []) => renderConProviders(<Arnes solicitudes={solicitudes} acciones={acciones} />)

// Modelo 14: batería bati14 (idCom 102) con stock 0. Modelo 13: chasis chai13negro (idCom 131) con stock 2.
const MODELO_13: AccionFormulario = { tipo: 'CAMBIAR_MODELO', modelo: '13' }
const MODELO_14: AccionFormulario = { tipo: 'CAMBIAR_MODELO', modelo: '14' }
const CHASIS_AL_LIMITE: AccionFormulario[] = [MODELO_13, { tipo: 'SUMAR', prefijo: 'cha' }, { tipo: 'SUMAR', prefijo: 'cha' }]

describe('SubFilaAgotado (ficha docs/paridad/formulario.md · Sub-fila de agotado y solicitud de pieza)', () => {
  it('variante sin stock: texto literal y botón "Solicitar pieza"', () => {
    montar([MODELO_14])
    const sub = screen.getByTestId('subfila-bat')
    expect(sub).toHaveAttribute('data-variante', 'sinStock')
    expect(sub).toHaveClass('bg-form-agotado-bg', 'pl-[70px]')
    const etiqueta = within(sub).getByText(/Sin stock disponible/)
    expect(etiqueta.textContent).toBe('⚠  Sin stock disponible. Solicita la pieza para que el admin gestione el pedido.')
    expect(etiqueta).toHaveClass('text-form-agotado-text', 'whitespace-pre-wrap')
    expect(within(sub).getByRole('button', { name: 'Solicitar pieza' })).toHaveClass('bg-ambar')
    expect(within(sub).queryByRole('button', { name: 'Editar descripción de solicitud de Batería' })).not.toBeInTheDocument()
    // Con stock de sobra no hay sub-fila.
    expect(screen.queryByTestId('subfila-lcd')).not.toBeInTheDocument()
  })

  it('variante límite: texto literal y "Solicitar y descontar stock"; convive con "✓ Guardar fila"', () => {
    montar(CHASIS_AL_LIMITE)
    const sub = screen.getByTestId('subfila-cha')
    expect(sub).toHaveAttribute('data-variante', 'limite')
    expect(within(sub).getByText(/Stock agotado/).textContent).toBe('⚠  Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición.')
    expect(within(sub).getByRole('button', { name: 'Solicitar y descontar stock' })).toBeInTheDocument()
    expect(screen.getByTestId('boton-derecho-cha')).toHaveTextContent('✓ Guardar fila')
    expect(screen.queryByTestId('subfila-bat')).not.toBeInTheDocument()
  })

  it('diálogo sin stock: título "Solicitar pieza — <tipo>", dos líneas de cabecera, placeholder, "Confirmar: solicitar pieza"; Cancelar no cambia nada', async () => {
    montar([MODELO_14])
    await userEvent.click(within(screen.getByTestId('subfila-bat')).getByRole('button', { name: 'Solicitar pieza' }))
    const dlg = within(await screen.findByRole('dialog', { name: 'Solicitar pieza — Batería' }))
    expect(dlg.getByText('Sin stock disponible.')).toBeInTheDocument()
    expect(dlg.getByText('Se creará una solicitud PENDIENTE para que el admin gestione el pedido.')).toBeInTheDocument()
    const area = dlg.getByPlaceholderText('Describe la pieza que necesitas (opcional)...')
    expect(area).toHaveAttribute('rows', '4')
    expect(area).toHaveValue('')
    expect(dlg.getByRole('button', { name: 'Confirmar: solicitar pieza' })).toHaveClass('bg-ambar')
    await userEvent.click(dlg.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('subfila-bat')).toHaveAttribute('data-variante', 'sinStock')
  })

  it('diálogo límite: "Se descontarán <N> unidades…" y "Confirmar: descontar <N> ud. de stock y solicitar" con N = stock', async () => {
    montar(CHASIS_AL_LIMITE)
    await userEvent.click(within(screen.getByTestId('subfila-cha')).getByRole('button', { name: 'Solicitar y descontar stock' }))
    const dlg = within(await screen.findByRole('dialog', { name: 'Solicitar pieza — Chasis' }))
    expect(dlg.getByText('Se descontarán 2 unidades de stock y quedará una solicitud PENDIENTE.')).toBeInTheDocument()
    expect(dlg.getByText('La asignación permanecerá abierta hasta recibir la pieza.')).toBeInTheDocument()
    expect(dlg.getByRole('button', { name: 'Confirmar: descontar 2 ud. de stock y solicitar' })).toBeInTheDocument()
  })

  it('confirmar deja la sub-fila verde con lápiz y sin botón ámbar, bloquea la fila y oculta el botón derecho', async () => {
    montar([MODELO_14])
    await userEvent.click(within(screen.getByTestId('subfila-bat')).getByRole('button', { name: 'Solicitar pieza' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar: solicitar pieza' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const sub = screen.getByTestId('subfila-bat')
    expect(sub).toHaveAttribute('data-variante', 'confirmada')
    expect(sub).toHaveClass('bg-recibido-bg')
    const etiqueta = within(sub).getByText(/Solicitud de reposición pendiente/)
    expect(etiqueta.textContent).toBe('✓  Solicitud de reposición pendiente')
    expect(etiqueta).toHaveClass('font-bold', 'text-recibido-text')
    expect(within(sub).queryByRole('button', { name: 'Solicitar pieza' })).not.toBeInTheDocument()
    expect(within(sub).getByRole('button', { name: 'Editar descripción de solicitud de Batería' })).toBeEnabled()
    const bat = within(screen.getByTestId('fila-bat'))
    expect(bat.getByRole('combobox', { name: 'SKU de Batería' })).toBeDisabled()
    expect(bat.getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeDisabled()
    expect(bat.getByRole('button', { name: 'Añadir observación' })).toBeDisabled()
    expect(screen.queryByTestId('boton-derecho-bat')).not.toBeInTheDocument()
  })

  it('etiqueta con " — <descripción>"; en límite conserva la cantidad y el número es el stock', async () => {
    montar(CHASIS_AL_LIMITE)
    await userEvent.click(within(screen.getByTestId('subfila-cha')).getByRole('button', { name: 'Solicitar y descontar stock' }))
    const dlg = within(await screen.findByRole('dialog'))
    await userEvent.type(dlg.getByRole('textbox'), '  Marco doblado  ')
    await userEvent.click(dlg.getByRole('button', { name: 'Confirmar: descontar 2 ud. de stock y solicitar' }))
    expect(within(screen.getByTestId('subfila-cha')).getByText(/se descontarán al guardar/).textContent).toBe('✓  2 uds. se descontarán al guardar — solicitud pendiente — Marco doblado')
    expect(screen.getByTestId('contador-cha')).toHaveTextContent('2')
    expect(screen.queryByTestId('boton-derecho-cha')).not.toBeInTheDocument()
  })

  it('lápiz abre "Editar descripción de solicitud" con tres botones; "Guardar descripción" actualiza sin subir revision; "Cancelar" no cambia; "Cancelar solicitud" devuelve la sub-fila amarilla', async () => {
    montar([MODELO_14, { tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: 'Batería hinchada' }])
    const lapiz = () => within(screen.getByTestId('subfila-bat')).getByRole('button', { name: 'Editar descripción de solicitud de Batería' })
    const revision = screen.getByTestId('revision').textContent
    await userEvent.click(lapiz())
    let dlg = within(await screen.findByRole('dialog', { name: 'Editar descripción de solicitud' }))
    expect(dlg.getByRole('textbox')).toHaveValue('Batería hinchada')
    expect(dlg.getAllByRole('button').map((b) => b.textContent)).toEqual(['Guardar descripción', 'Cancelar solicitud', 'Cancelar'])
    expect(dlg.getByRole('button', { name: 'Guardar descripción' })).toHaveClass('bg-ambar')
    expect(dlg.getByRole('button', { name: 'Cancelar solicitud' })).toHaveClass('bg-rojo-cancelar')
    await userEvent.clear(dlg.getByRole('textbox'))
    await userEvent.type(dlg.getByRole('textbox'), 'Batería hinchada, urgente')
    await userEvent.click(dlg.getByRole('button', { name: 'Guardar descripción' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(within(screen.getByTestId('subfila-bat')).getByText(/Solicitud de reposición/).textContent).toBe('✓  Solicitud de reposición pendiente — Batería hinchada, urgente')
    expect(screen.getByTestId('revision').textContent).toBe(revision)
    // "Cancelar" cierra sin cambios.
    await userEvent.click(lapiz())
    dlg = within(await screen.findByRole('dialog', { name: 'Editar descripción de solicitud' }))
    await userEvent.type(dlg.getByRole('textbox'), ' y algo más')
    await userEvent.click(dlg.getByRole('button', { name: 'Cancelar' }))
    expect(within(screen.getByTestId('subfila-bat')).getByText(/Solicitud de reposición/).textContent).toBe('✓  Solicitud de reposición pendiente — Batería hinchada, urgente')
    // "Cancelar solicitud" vuelve al estado anterior a confirmar.
    await userEvent.click(lapiz())
    dlg = within(await screen.findByRole('dialog', { name: 'Editar descripción de solicitud' }))
    await userEvent.click(dlg.getByRole('button', { name: 'Cancelar solicitud' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const sub = screen.getByTestId('subfila-bat')
    expect(sub).toHaveAttribute('data-variante', 'sinStock')
    expect(sub).toHaveClass('bg-form-agotado-bg')
    expect(within(sub).getByRole('button', { name: 'Solicitar pieza' })).toBeInTheDocument()
    expect(within(screen.getByTestId('fila-bat')).getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeEnabled()
  })

  it('solicitud pendiente del servidor: sub-fila verde con lápiz deshabilitado que no abre nada; Reutilizado y observación siguen activos', async () => {
    montar([], [solicitudAsignacion({ descripcionSolicitud: 'Urgente para cliente' })])
    const sub = screen.getByTestId('subfila-bat')
    expect(sub).toHaveAttribute('data-variante', 'confirmada')
    expect(within(sub).getByText(/Solicitud de reposición/).textContent).toBe('✓  Solicitud de reposición pendiente — Urgente para cliente')
    const lapiz = within(sub).getByRole('button', { name: 'Editar descripción de solicitud de Batería' })
    expect(lapiz).toBeDisabled()
    expect(lapiz).not.toHaveClass('cursor-pointer')
    await userEvent.click(lapiz)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const bat = within(screen.getByTestId('fila-bat'))
    expect(bat.getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeEnabled()
    expect(bat.getByRole('button', { name: 'Añadir observación' })).toBeEnabled()
    expect(bat.getByRole('button', { name: 'Sumar Batería' })).toBeDisabled()
    expect(bat.getByRole('combobox', { name: 'SKU de Batería' })).toBeDisabled()
    expect(screen.queryByTestId('boton-derecho-bat')).not.toBeInTheDocument()
  })

  it('rechazada: SKU en rojo y sub-fila amarilla "Solicitar pieza", sin ninguna marca', () => {
    montar([], [solicitudAsignacion({ estadoSolicitud: 'RECHAZADA' })])
    const combo = within(screen.getByTestId('fila-bat')).getByRole('combobox', { name: 'SKU de Batería' })
    expect(combo).toHaveTextContent('bati14')
    expect(combo.outerHTML).toContain('text-rojo-sin-stock')
    expect(combo).toBeEnabled()
    const sub = screen.getByTestId('subfila-bat')
    expect(sub).toHaveAttribute('data-variante', 'sinStock')
    expect(within(sub).getByRole('button', { name: 'Solicitar pieza' })).toBeInTheDocument()
    expect(screen.queryByTestId('boton-derecho-bat')).not.toBeInTheDocument()
  })
})
```

```bash
npm test -- SubFilaAgotado
```
Expected: falla — `Failed to resolve import "./SubFilaAgotado"`.

- [x] **Step 2: Los dos diálogos**

`src/modules/taller/formulario/DialogoSolicitarPieza.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

type Props = { abierto: boolean; tipo: string; variante: 'sinStock' | 'limite'; stock: number; inicial: string; onConfirmar: (descripcion: string) => void; onCancelar: () => void }

/** Diálogo "Solicitar pieza — <tipo>" (460 px). La cabecera y el botón cambian con la variante; N es el stock del SKU, no el
 *  contador. Confirmar es local: quien lo usa despacha CONFIRMAR_AGOTADO; el servidor no se entera hasta "Terminar asignación". */
export function DialogoSolicitarPieza({ abierto, tipo, variante, stock, inicial, onConfirmar, onCancelar }: Props) {
  const [texto, setTexto] = useState(inicial)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga la descripción que hubiera cada vez que se abre
    if (abierto) setTexto(inicial)
  }, [abierto, inicial])
  const lineas =
    variante === 'sinStock'
      ? ['Sin stock disponible.', 'Se creará una solicitud PENDIENTE para que el admin gestione el pedido.']
      : [`Se descontarán ${stock} unidades de stock y quedará una solicitud PENDIENTE.`, 'La asignación permanecerá abierta hasta recibir la pieza.']
  const textoBoton = variante === 'sinStock' ? 'Confirmar: solicitar pieza' : `Confirmar: descontar ${stock} ud. de stock y solicitar`
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent aria-describedby={undefined} className="max-w-[min(460px,calc(100%-2rem))] gap-2 bg-fondo-input p-4 sm:max-w-[min(460px,calc(100%-2rem))]">
        <DialogHeader>
          <DialogTitle className="text-[14px] font-bold text-azul-medio">{`Solicitar pieza — ${tipo}`}</DialogTitle>
        </DialogHeader>
        <div className="text-[13px] text-azul-medio">
          <p>{lineas[0]}</p>
          <p>{lineas[1]}</p>
        </div>
        <textarea
          aria-label="Descripción de la pieza"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder="Describe la pieza que necesitas (opcional)..."
          rows={4}
          className="w-full resize-none rounded border border-gris-borde bg-superficie p-2 text-[13px]"
        />
        <Button onClick={() => onConfirmar(texto.trim())} className="h-auto w-full rounded bg-ambar py-2 text-[12px] text-superficie hover:bg-ambar/90">
          {textoBoton}
        </Button>
        <DialogFooter>
          <Button variant="outline" onClick={onCancelar}>Cancelar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

`src/modules/taller/formulario/DialogoDescripcionSolicitud.tsx`:

```tsx
import { useLayoutEffect, useState } from 'react'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogTitle } from '@/shared/ui/dialog'

type Props = { abierto: boolean; inicial: string; onGuardar: (descripcion: string) => void; onCancelarSolicitud: () => void; onCancelar: () => void }

/** Diálogo "Editar descripción de solicitud" (440 px) de una solicitud LOCAL aún sin enviar. No tiene cabecera visible: el título
 *  va en sr-only para que el diálogo tenga nombre accesible. Tres botones, en este orden: "Guardar descripción" (ámbar),
 *  "Cancelar solicitud" (rojo) y "Cancelar" (cierra sin cambios). */
export function DialogoDescripcionSolicitud({ abierto, inicial, onGuardar, onCancelarSolicitud, onCancelar }: Props) {
  const [texto, setTexto] = useState(inicial)
  useLayoutEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- precarga la descripción actual cada vez que se abre
    if (abierto) setTexto(inicial)
  }, [abierto, inicial])
  const claseBoton = 'h-auto w-full rounded py-2 text-[12px] text-superficie'
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent aria-describedby={undefined} showCloseButton={false} className="max-w-[min(440px,calc(100%-2rem))] gap-2 bg-fondo-input p-4 sm:max-w-[min(440px,calc(100%-2rem))]">
        <DialogTitle className="sr-only">Editar descripción de solicitud</DialogTitle>
        <textarea
          aria-label="Descripción de la pieza"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder="Describe la pieza que necesitas (opcional)..."
          rows={4}
          className="w-full resize-none rounded border border-gris-borde bg-superficie p-2 text-[13px]"
        />
        <Button onClick={() => onGuardar(texto.trim())} className={`${claseBoton} bg-ambar hover:bg-ambar/90`}>Guardar descripción</Button>
        <Button onClick={onCancelarSolicitud} className={`${claseBoton} bg-rojo-cancelar hover:bg-rojo-cancelar/90`}>Cancelar solicitud</Button>
        <DialogFooter>
          <Button variant="outline" onClick={onCancelar}>Cancelar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```
(`showCloseButton={false}`: sin cabecera no hay ✕ —el test comprueba que los únicos botones son los tres de la ficha—; Escape y "Cancelar" cierran.)

- [x] **Step 3: `SubFilaAgotado`**

`src/modules/taller/formulario/SubFilaAgotado.tsx`:

```tsx
import { useState, type Dispatch } from 'react'
import { cn } from '@/shared/lib/utils'
import { DialogoDescripcionSolicitud } from './DialogoDescripcionSolicitud'
import { DialogoSolicitarPieza } from './DialogoSolicitarPieza'
import { subFila, TEXTO_LIMITE, TEXTO_SIN_STOCK, type AccionFormulario, type EstadoFormulario, type FilaEstado } from './estado'

type Props = { estado: EstadoFormulario; fila: FilaEstado; dispatch: Dispatch<AccionFormulario> }

/** Sub-fila de agotado bajo una fila (sangría de 70 px). `subFila()` decide si existe, la variante, el texto confirmado y si el
 *  lápiz funciona (solo sobre la solicitud local; la ya guardada en el servidor lo muestra deshabilitado). Confirmar, editar la
 *  descripción y cancelar son acciones locales del reductor: aquí no se llama al servidor. */
export function SubFilaAgotado({ estado, fila, dispatch }: Props) {
  const [dialogo, setDialogo] = useState<'ninguno' | 'solicitar' | 'descripcion'>('ninguno')
  const sub = subFila(estado, fila)
  if (sub.tipo === 'oculta') return null
  const { prefijo, nombre: tipo } = fila
  const cerrar = () => setDialogo('ninguno')

  return (
    <div
      data-testid={`subfila-${prefijo}`}
      data-variante={sub.tipo}
      className={cn('flex items-center gap-2.5 py-1 pr-2 pl-[70px]', sub.tipo === 'confirmada' ? 'bg-recibido-bg' : 'bg-form-agotado-bg')}
    >
      <span className={cn('text-[11px] whitespace-pre-wrap', sub.tipo === 'confirmada' ? 'font-bold text-recibido-text' : 'text-form-agotado-text')}>
        {sub.tipo === 'confirmada' ? sub.texto : sub.tipo === 'limite' ? TEXTO_LIMITE : TEXTO_SIN_STOCK}
      </span>
      {sub.tipo === 'confirmada' ? (
        <button
          type="button"
          aria-label={`Editar descripción de solicitud de ${tipo}`}
          disabled={!sub.lapizHabilitado}
          onClick={() => setDialogo('descripcion')}
          className={cn('bg-transparent px-1 py-0.5', sub.lapizHabilitado ? 'cursor-pointer' : 'cursor-default')}
        >
          <img src="/editar.png" alt="" className="h-4 w-4" />
        </button>
      ) : (
        <button type="button" onClick={() => setDialogo('solicitar')} className="cursor-pointer rounded bg-ambar px-2.5 py-1 text-[11px] text-superficie">
          {sub.tipo === 'limite' ? 'Solicitar y descontar stock' : 'Solicitar pieza'}
        </button>
      )}
      <DialogoSolicitarPieza
        abierto={dialogo === 'solicitar'}
        tipo={tipo}
        variante={sub.tipo === 'limite' ? 'limite' : 'sinStock'}
        stock={sub.tipo === 'limite' ? sub.stock : 0}
        inicial={fila.agotado?.descripcion ?? ''}
        onConfirmar={(descripcion) => { dispatch({ tipo: 'CONFIRMAR_AGOTADO', prefijo, descripcion }); cerrar() }}
        onCancelar={cerrar}
      />
      <DialogoDescripcionSolicitud
        abierto={dialogo === 'descripcion'}
        inicial={fila.agotado?.descripcion ?? ''}
        onGuardar={(descripcion) => { dispatch({ tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo, descripcion }); cerrar() }}
        onCancelarSolicitud={() => { dispatch({ tipo: 'CANCELAR_AGOTADO', prefijo }); cerrar() }}
        onCancelar={cerrar}
      />
    </div>
  )
}
```

```bash
npm test -- SubFilaAgotado
```
Expected: pasan los 9 tests.

- [x] **Step 4: Tests de "⚠ En camino" y "✓ Recibido" en la fila (fallan)**

En `src/modules/taller/formulario/FilaComponente.test.tsx`:

1. Añadir a los imports:

```tsx
import type { SolicitudAsignacion } from '@/shared/api/client'
import { agrupados, solicitudAsignacion } from '../test/fabrica'
```
(la segunda línea sustituye al import actual de `../test/fabrica`).

2. Sustituir `DATOS`, `Arnes` y `montar` por esta versión, que admite solicitudes ya guardadas:

```tsx
const datos = (solicitudes: SolicitudAsignacion[]): DatosNuevo => ({ modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes, incidencia: null, modeloTelefono: null })

/** Arnés: el reductor real con las filas pintadas. "Guardar fila" aquí solo pide la confirmación (el POST es de useGuardado). */
function Arnes({ acciones, solicitudes, alGuardar }: { acciones: AccionFormulario[]; solicitudes: SolicitudAsignacion[]; alGuardar: (prefijo: string) => void }) {
  const [estado, dispatch] = useReducer(reducir, datos(solicitudes), (d) => acciones.reduce(reducir, estadoInicial(d)))
  return (
    <div>
      {estado.filas.map((fila) => (
        <FilaComponente
          key={fila.prefijo}
          estado={estado}
          fila={fila}
          dispatch={dispatch}
          onGuardarFila={(prefijo) => { alGuardar(prefijo); dispatch({ tipo: 'PEDIR_CONFIRMACION_FILA', prefijo }) }}
        />
      ))}
    </div>
  )
}

function montar(acciones: AccionFormulario[], solicitudes: SolicitudAsignacion[] = []) {
  const alGuardar = vi.fn()
  renderConProviders(<Arnes acciones={acciones} solicitudes={solicitudes} alGuardar={alGuardar} />)
  return { alGuardar }
}
```

3. Añadir dentro del `describe`, al final:

```tsx
  it('en camino: botón derecho "⚠ En camino" deshabilitado y Reutilizado deshabilitado', () => {
    montar([], [solicitudAsignacion({ enCamino: true })])
    const boton = screen.getByTestId('boton-derecho-bat')
    expect(boton).toHaveTextContent('⚠ En camino')
    expect(boton).toBeDisabled()
    expect(boton).toHaveClass('bg-tipo-reparacion-bg', 'text-tipo-reparacion-text', 'rounded-none')
    const casilla = within(screen.getByTestId('fila-bat')).getByRole('checkbox', { name: 'Reutilizado Batería' })
    expect(casilla).toBeDisabled()
    expect(casilla).not.toBeChecked()
  })

  it('recibido: "✓ Recibido" deshabilitado en una fila editable; al activarla pasa a "✓ Guardar fila" y ya no vuelve', async () => {
    // lcdi14 (idCom 112) tiene stock 3: GESTIONADA con stock = recibido.
    montar([], [solicitudAsignacion({ idCom: 112, estadoSolicitud: 'GESTIONADA' })])
    const boton = screen.getByTestId('boton-derecho-lcd')
    expect(boton).toHaveTextContent('✓ Recibido')
    expect(boton).toBeDisabled()
    expect(boton).toHaveClass('bg-recibido-bg', 'text-recibido-text')
    const lcd = within(screen.getByTestId('fila-lcd'))
    expect(screen.getByTestId('fila-lcd')).toHaveAttribute('data-estado', 'normal')
    await userEvent.click(lcd.getByRole('button', { name: 'Sumar Pantalla' }))
    expect(screen.getByTestId('boton-derecho-lcd')).toHaveTextContent('✓ Guardar fila')
    await userEvent.click(lcd.getByRole('button', { name: 'Restar Pantalla' }))
    expect(screen.queryByTestId('boton-derecho-lcd')).not.toBeInTheDocument()
  })
```

```bash
npm test -- FilaComponente
```
Expected: fallan los 2 nuevos (`Unable to find an element by: [data-testid="boton-derecho-bat"]` y `…-lcd`: `BotonDerechoFila` aún devuelve `null` para esos tipos); los 8 anteriores siguen en verde.

- [x] **Step 5: `FilaComponente` pinta los dos indicadores**

En `src/modules/taller/formulario/FilaComponente.tsx`, dentro del `switch` de `BotonDerechoFila`, añadir entre el `case 'guardada'` y el `default`:

```tsx
    case 'enCamino':
      return (
        <button type="button" data-testid={testid} disabled className={cn(CLASE_ETIQUETA, 'bg-tipo-reparacion-bg text-tipo-reparacion-text')}>
          ⚠ En camino
        </button>
      )
    case 'recibido':
      return (
        <button type="button" data-testid={testid} disabled className={cn(CLASE_ETIQUETA, 'bg-recibido-bg text-recibido-text')}>
          ✓ Recibido
        </button>
      )
```
y actualizar el comentario de `CLASE_ETIQUETA`:

```tsx
/** Etiqueta deshabilitada del botón derecho ("✓ Guardada …", "⚠ En camino", "✓ Recibido"): 11 px, radio 0, padding 4 10, con sus
 *  colores aunque esté disabled. */
```

```bash
npm test -- FilaComponente
```
Expected: pasan los 10 tests.

- [x] **Step 6: Test de integración en el formulario (falla)**

En `src/modules/taller/formulario/FormularioReparacion.test.tsx`:

1. Sustituir el import de la fábrica por:

```tsx
import { asignacionActiva, solicitudAsignacion } from '../test/fabrica'
```

2. Añadir al final del fichero:

```tsx
describe('FormularioReparacion · solicitud de pieza y solicitudes ya guardadas', () => {
  it('confirmar no llama al servidor y deja la sub-fila verde con lápiz y sin botón ámbar', async () => {
    const { llamadas } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 14')
    await userEvent.click(within(screen.getByTestId('subfila-bat')).getByRole('button', { name: 'Solicitar pieza' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Solicitar pieza — Batería' })).getByRole('button', { name: 'Confirmar: solicitar pieza' }))
    const sub = screen.getByTestId('subfila-bat')
    expect(sub).toHaveAttribute('data-variante', 'confirmada')
    expect(within(sub).getByRole('button', { name: 'Editar descripción de solicitud de Batería' })).toBeEnabled()
    expect(within(sub).queryByRole('button', { name: 'Solicitar pieza' })).not.toBeInTheDocument()
    expect(escrituras(llamadas)).toEqual([])
  })

  it('solicitud pendiente cargada del servidor: modelo deducido y bloqueado, fila con la sub-fila verde', async () => {
    abrir({ solicitudes: [solicitudAsignacion()] })
    await screen.findByRole('dialog', { name: TITULO })
    const combo = screen.getByRole('combobox', { name: 'Filtrar por modelo' })
    expect(combo).toHaveTextContent('iPhone 14')
    expect(combo).toBeDisabled()
    expect(screen.getByTestId('subfila-bat')).toHaveAttribute('data-variante', 'confirmada')
  })
})
```

```bash
npm test -- FormularioReparacion
```
Expected: fallan los 2 nuevos (`Unable to find an element by: [data-testid="subfila-bat"]`).

- [x] **Step 7: `FormularioReparacion` pasa la sub-fila a cada fila**

En `src/modules/taller/formulario/FormularioReparacion.tsx`:

1. Añadir el import:

```tsx
import { SubFilaAgotado } from './SubFilaAgotado'
```

2. Sustituir la línea `estado.filas.map(…)` por:

```tsx
            estado.filas.map((fila) => (
              <FilaComponente key={fila.prefijo} estado={estado} fila={fila} dispatch={dispatch} onGuardarFila={guardado.guardarFila}>
                <SubFilaAgotado estado={estado} fila={fila} dispatch={dispatch} />
              </FilaComponente>
            ))
```

```bash
npm test -- FormularioReparacion SubFilaAgotado FilaComponente
```
Expected: todo en verde (13 + 9 + 10).

- [x] **Step 8: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/SubFilaAgotado.tsx src/modules/taller/formulario/SubFilaAgotado.test.tsx src/modules/taller/formulario/DialogoSolicitarPieza.tsx src/modules/taller/formulario/DialogoDescripcionSolicitud.tsx src/modules/taller/formulario/FilaComponente.tsx src/modules/taller/formulario/FilaComponente.test.tsx src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx
git commit -m "feat(web): solicitud de pieza y componente agotado en el formulario, con los indicadores de solicitudes ya guardadas"
```

---

### Task 15: Web — `OtrasAcciones`, `ZonaGuardar` y "Terminar asignación"

**Files:**
- Create: `src/modules/taller/formulario/OtrasAcciones.tsx`, `src/modules/taller/formulario/OtrasAcciones.test.tsx`, `src/modules/taller/formulario/ZonaGuardar.tsx`
- Modify: `src/modules/taller/formulario/useGuardado.ts`, `src/modules/taller/formulario/useGuardado.test.tsx`, `src/modules/taller/formulario/FormularioReparacion.tsx`, `src/modules/taller/formulario/FormularioReparacion.test.tsx`

**Interfaces:**
- Consumes: `otrasAccionesVisible`, `contadorAcciones`, `anadirAccionHabilitado`, `accionPideConfirmacion`, `zonaGuardarVisible`, `textoBotonGuardar`, `cuerpoGuardarAccion`, `planTerminar`, `PlanTerminar` (W6); `reducir` (W5); acciones `ANADIR_ACCION`, `ESCRIBIR_ACCION`, `QUITAR_ACCION`, `PEDIR_CONFIRMACION_ACCION`, `INICIO_GUARDAR_ACCION`, `ACCION_GUARDADA`, `FALLO_GUARDAR_ACCION`, `PEDIR_CONFIRMACION_GUARDAR`, `INICIO_GUARDADO`, `FALLO_GUARDADO`, `GUARDADO_COMPLETADO`, `AGOTADO_REGISTRADO` (W5); `OtraAccion`, `GuardadoEstado` (W4); `useGuardarFila`, `useAgotarComponente`, `useCompleta` (W10); `conRegistro` (W10); `fechaGuardado` y `useGuardado` de la Task 13; `BotonPrimario` (`shared/ui/Botones`); `StaleDataError`, `esErrorGestionadoGlobalmente`, `mensajeDeError`; avisos multilínea del `AlertaProvider` (W8).
- Produces:
  ```ts
  export function OtrasAcciones(props: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardarAccion: (id: number) => void }): JSX.Element | null
  export function ZonaGuardar(props: { estado: EstadoFormulario; onPulsar: () => void }): JSX.Element | null
  // formulario/useGuardado.ts — completo para nuevo y glass
  export function useGuardado(args: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardado: () => void; antesDeCerrar?: () => Promise<void> }): {
    pulsarGuardar: () => void                      // 1.er clic PEDIR_CONFIRMACION_GUARDAR; 2.º INICIO_GUARDADO + ejecuta planTerminar
    guardarFila: (prefijo: string) => void
    guardarAccion: (id: number) => void            // dos clics sobre la línea; decide con accionPideConfirmacion(accion) (tras un fallo, el reductor deja pideOtroClic)
  }
  ```
  Selectores de W13: sección `otras-acciones`, badge `otras-acciones-badge`, línea `accion-<n>` (n = posición desde 1), papelera `"Quitar acción"`, zona `zona-guardar`. `antesDeCerrar` se espera tras `GUARDADO_COMPLETADO` y antes de `onGuardado` (su fallo se ignora); en modo `editar` el segundo clic de `pulsarGuardar` todavía no ejecuta nada (la rama de edición llega con su ruta).

**Ficha:** `formulario.md` — sección "Otras acciones" salvo "En edición las líneas no tienen "✓ Guardar"…"; sección "Zona de guardar y terminar" completa ("Zona pegada al fondo…", "Visible en flujo nuevo y Glass si…", "Texto "Terminar asignación"… Primer clic…", "Terminar, paso 1…", "Paso 2…", "Paso 3…", "Paso 4…", "Los campos sin valor…" con la precisión de que viajan como `null`); de "Errores": "Guardar acción: "No se pudo guardar la acción: <mensaje>"", "Agotar componente: con 409…", "Terminar: con 409…"; de "Correcciones deliberadas": "Reintento de "Terminar asignación" tras un fallo parcial".

Todos los comandos se ejecutan desde `gestion-reparaciones-web`. Qué se envía y en qué orden lo decide `planTerminar()`; el hook solo lo ejecuta, despacha y pone los literales de error. Dos reglas que vienen del reductor (Tasks 7–8) y que el hook respeta sin estado propio: (1) si el clic sobre una acción pide confirmación o guarda lo dice `accionPideConfirmacion(accion)` —tras `FALLO_GUARDAR_ACCION` la línea queda con `confirmando = true` y `pideOtroClic = true`: el botón sigue en "✓ Confirmar" pero el siguiente clic vuelve a confirmar—; (2) `planTerminar` se recalcula con el estado resultante tras cada `AGOTADO_REGISTRADO` (devuelve `completa: null` si no hay filas que enviar y existe algún agotado local, registrado o no).

- [x] **Step 1: Tests de `OtrasAcciones` (fallan)**

`src/modules/taller/formulario/OtrasAcciones.test.tsx`:

```tsx
import { useReducer } from 'react'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders } from '@/test/render'
import { agrupados } from '../test/fabrica'
import { estadoInicial, reducir, type DatosNuevo, type EstadoFormulario } from './estado'
import { OtrasAcciones } from './OtrasAcciones'

const DATOS: DatosNuevo = { modo: 'nuevo', idAsignacion: 'A20260916_1', imei: '355400000000111', agrupados: agrupados(), solicitudes: [], incidencia: null, modeloTelefono: null }

/** Arnés: el reductor real. "✓ Guardar" aquí solo pide la confirmación (el POST es de useGuardado). */
function Arnes({ preparar, alGuardar }: { preparar: (e: EstadoFormulario) => EstadoFormulario; alGuardar: (id: number) => void }) {
  const [estado, dispatch] = useReducer(reducir, DATOS, (d) => preparar(estadoInicial(d)))
  return <OtrasAcciones estado={estado} dispatch={dispatch} onGuardarAccion={(id) => { alGuardar(id); dispatch({ tipo: 'PEDIR_CONFIRMACION_ACCION', id }) }} />
}
function montar(preparar: (e: EstadoFormulario) => EstadoFormulario) {
  const alGuardar = vi.fn()
  renderConProviders(<Arnes preparar={preparar} alGuardar={alGuardar} />)
  return { alGuardar }
}
const conModelo = (modelo: string) => (e: EstadoFormulario) => reducir(e, { tipo: 'CAMBIAR_MODELO', modelo })
/** Modelo 13 con una acción ya guardada ("Limpieza de conector") y otra pendiente con texto. */
function conUnaGuardadaYUnaPendiente(e: EstadoFormulario): EstadoFormulario {
  let s = reducir(conModelo('13')(e), { tipo: 'ANADIR_ACCION' })
  const id = s.otros[0].id
  s = reducir(s, { tipo: 'ESCRIBIR_ACCION', id, texto: 'Limpieza de conector' })
  s = reducir(s, { tipo: 'PEDIR_CONFIRMACION_ACCION', id })
  s = reducir(s, { tipo: 'INICIO_GUARDAR_ACCION', id })
  s = reducir(s, { tipo: 'ACCION_GUARDADA', id, idRep: 'R20260916_7', fecha: '16/09 09:15' })
  s = reducir(s, { tipo: 'ANADIR_ACCION' })
  return reducir(s, { tipo: 'ESCRIBIR_ACCION', id: s.otros[1].id, texto: 'Cambio de tornillos' })
}

describe('OtrasAcciones (ficha docs/paridad/formulario.md · Otras acciones)', () => {
  it('sección oculta sin modelo o sin componente otro del modelo', () => {
    montar((e) => e)
    expect(screen.queryByTestId('otras-acciones')).not.toBeInTheDocument()
  })
  it('sección oculta con un modelo sin componente otro (13 Pro Max) y visible con el 13', () => {
    const { unmount } = renderConProviders(<Arnes preparar={conModelo('13promax')} alGuardar={() => {}} />)
    expect(screen.queryByTestId('otras-acciones')).not.toBeInTheDocument()
    unmount()
    montar(conModelo('13'))
    const seccion = screen.getByTestId('otras-acciones')
    expect(seccion).toHaveClass('bg-form-otros-bg', 'border-l-4', 'border-l-azul-medio')
    expect(within(seccion).getByText('OTRAS ACCIONES')).toBeInTheDocument()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('0')
    expect(screen.queryByTestId('accion-1')).not.toBeInTheDocument()
  })

  it('"+ Añadir acción" añade una línea con foco y se deshabilita con una línea vacía', async () => {
    montar(conModelo('13'))
    const anadir = screen.getByRole('button', { name: '+ Añadir acción' })
    expect(anadir).toBeEnabled()
    await userEvent.click(anadir)
    const campo = within(screen.getByTestId('accion-1')).getByPlaceholderText('Describe la acción')
    expect(campo).toHaveFocus()
    expect(anadir).toBeDisabled()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('0')
    await userEvent.type(campo, 'Limpieza de conector')
    expect(anadir).toBeEnabled()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('1')
    await userEvent.click(anadir)
    expect(within(screen.getByTestId('accion-2')).getByPlaceholderText('Describe la acción')).toHaveFocus()
  })

  it('"✓ Guardar" deshabilitado sin texto; el primer clic pasa a "✓ Confirmar" y escribir lo devuelve a "✓ Guardar"', async () => {
    const { alGuardar } = montar(conModelo('13'))
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir acción' }))
    const linea = within(screen.getByTestId('accion-1'))
    expect(linea.getByRole('button', { name: '✓ Guardar' })).toBeDisabled()
    await userEvent.type(linea.getByPlaceholderText('Describe la acción'), 'Limpieza')
    expect(linea.getByRole('button', { name: '✓ Guardar' })).toBeEnabled()
    expect(linea.getByRole('button', { name: '✓ Guardar' })).toHaveClass('bg-azul-noche')
    await userEvent.click(linea.getByRole('button', { name: '✓ Guardar' }))
    expect(alGuardar).toHaveBeenCalledTimes(1)
    expect(linea.getByRole('button', { name: '✓ Confirmar' })).toBeInTheDocument()
    await userEvent.type(linea.getByPlaceholderText('Describe la acción'), ' de conector')
    expect(linea.getByRole('button', { name: '✓ Guardar' })).toBeInTheDocument()
    expect(linea.queryByRole('button', { name: '✓ Confirmar' })).not.toBeInTheDocument()
  })

  it('papelera quita la línea sin confirmación', async () => {
    montar(conModelo('13'))
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir acción' }))
    await userEvent.type(within(screen.getByTestId('accion-1')).getByPlaceholderText('Describe la acción'), 'Limpieza')
    await userEvent.click(within(screen.getByTestId('accion-1')).getByRole('button', { name: 'Quitar acción' }))
    expect(screen.queryByTestId('accion-1')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('0')
  })

  it('línea guardada: campo deshabilitado y "✓ Guardada dd/MM HH:mm", sin "✓ Guardar" ni papelera; el badge cuenta guardadas y con texto', () => {
    montar(conUnaGuardadaYUnaPendiente)
    const guardada = within(screen.getByTestId('accion-1'))
    expect(guardada.getByPlaceholderText('Describe la acción')).toBeDisabled()
    expect(guardada.getByPlaceholderText('Describe la acción')).toHaveValue('Limpieza de conector')
    expect(guardada.getByText('✓ Guardada 16/09 09:15')).toHaveClass('font-bold', 'text-recibido-text')
    expect(guardada.queryByRole('button')).not.toBeInTheDocument()
    const pendiente = within(screen.getByTestId('accion-2'))
    expect(pendiente.getByRole('button', { name: '✓ Guardar' })).toBeEnabled()
    expect(pendiente.getByRole('button', { name: 'Quitar acción' })).toBeInTheDocument()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('2')
  })
})
```

```bash
npm test -- OtrasAcciones
```
Expected: falla — `Failed to resolve import "./OtrasAcciones"`.

- [x] **Step 2: `OtrasAcciones`**

`src/modules/taller/formulario/OtrasAcciones.tsx`:

```tsx
import { useEffect, useRef, type Dispatch } from 'react'
import { anadirAccionHabilitado, contadorAcciones, otrasAccionesVisible, type AccionFormulario, type EstadoFormulario } from './estado'

type Props = { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardarAccion: (id: number) => void }

/** Sección OTRAS ACCIONES (igual en reparación y en glass): cabecera con badge, caja blanca con las líneas y "+ Añadir acción".
 *  La visibilidad, el badge y el habilitado de "+ Añadir acción" son selectores del estado; cada línea despacha lo que se teclea. */
export function OtrasAcciones({ estado, dispatch, onGuardarAccion }: Props) {
  // "+ Añadir acción" da el foco a la línea nueva: se marca en el clic y se enfoca cuando la línea ya está pintada.
  const ultimoCampo = useRef<HTMLInputElement | null>(null)
  const enfocarNueva = useRef(false)
  const total = estado.otros.length
  useEffect(() => {
    if (!enfocarNueva.current) return
    enfocarNueva.current = false
    ultimoCampo.current?.focus()
  }, [total])

  if (!otrasAccionesVisible(estado)) return null
  return (
    <section data-testid="otras-acciones" className="border-l-4 border-l-azul-medio bg-form-otros-bg">
      <div className="flex items-center gap-2 px-3.5 pt-2 pb-0.5">
        <span className="text-[11.5px] font-bold text-azul-medio">OTRAS ACCIONES</span>
        <span data-testid="otras-acciones-badge" className="rounded-[10px] bg-azul-medio px-2 py-px text-[10px] font-bold text-superficie">
          {contadorAcciones(estado)}
        </span>
      </div>
      <div className="flex flex-col items-start gap-2 px-3.5 pt-0.5 pb-3">
        <div className="flex max-h-[150px] min-h-[36px] w-full flex-col gap-[5px] overflow-y-auto rounded-[6px] border border-fila-sep bg-superficie p-[5px]">
          {estado.otros.map((accion, i) => (
            <div key={accion.id} data-testid={`accion-${i + 1}`} className="flex items-center gap-[5px]">
              <input
                ref={i === total - 1 ? ultimoCampo : undefined}
                aria-label={`Descripción de la acción ${i + 1}`}
                value={accion.texto}
                // Mientras se guarda tampoco se escribe: lo enviado y lo que queda en pantalla deben ser lo mismo.
                disabled={accion.guardada !== null || accion.guardando}
                onChange={(e) => dispatch({ tipo: 'ESCRIBIR_ACCION', id: accion.id, texto: e.target.value })}
                placeholder="Describe la acción"
                className="min-w-0 flex-1 rounded border border-fila-sep bg-superficie px-2 py-1 text-[12px] disabled:opacity-60"
              />
              {accion.guardada !== null ? (
                <span className="shrink-0 px-1 text-[11px] font-bold text-recibido-text">{`✓ Guardada ${accion.guardada.fecha}`}</span>
              ) : (
                <>
                  <button
                    type="button"
                    disabled={accion.texto.trim() === '' || accion.guardando}
                    onClick={() => onGuardarAccion(accion.id)}
                    className="shrink-0 cursor-pointer rounded bg-azul-noche px-2.5 py-1 text-[11px] font-bold text-superficie disabled:cursor-default disabled:opacity-50"
                  >
                    {accion.confirmando ? '✓ Confirmar' : '✓ Guardar'}
                  </button>
                  <button type="button" aria-label="Quitar acción" onClick={() => dispatch({ tipo: 'QUITAR_ACCION', id: accion.id })} className="shrink-0 cursor-pointer bg-transparent px-1 py-0.5">
                    <img src="/borrar.png" alt="" className="h-[18px] w-[18px]" />
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
        <button
          type="button"
          disabled={!anadirAccionHabilitado(estado)}
          onClick={() => { enfocarNueva.current = true; dispatch({ tipo: 'ANADIR_ACCION' }) }}
          className="cursor-pointer rounded-[6px] bg-azul-medio px-3 py-1.5 text-[11.5px] font-bold text-superficie disabled:cursor-default disabled:opacity-50"
        >
          + Añadir acción
        </button>
      </div>
    </section>
  )
}
```

```bash
npm test -- OtrasAcciones
```
Expected: pasan los 6 tests.

- [x] **Step 3: `ZonaGuardar`**

`src/modules/taller/formulario/ZonaGuardar.tsx`:

```tsx
import { BotonPrimario } from '@/shared/ui/Botones'
import { textoBotonGuardar, zonaGuardarVisible, type EstadoFormulario } from './estado'

/** Zona pegada al fondo con el único botón navy. Se muestra u oculta ENTERA (`zonaGuardarVisible`); el botón nunca se deshabilita:
 *  mientras un guardado está en curso, quien recibe `onPulsar` ignora el clic. whitespace-pre conserva el doble espacio de
 *  "✓  Confirmar terminar". */
export function ZonaGuardar({ estado, onPulsar }: { estado: EstadoFormulario; onPulsar: () => void }) {
  if (!zonaGuardarVisible(estado)) return null
  return (
    <div data-testid="zona-guardar" className="flex justify-end border-t border-form-zona-brd bg-form-zona-bg px-4 py-3">
      <BotonPrimario onClick={onPulsar} className="whitespace-pre">{textoBotonGuardar(estado)}</BotonPrimario>
    </div>
  )
}
```
(No lleva fichero de tests propio: la cubren los tests del formulario del Step 6.)

- [x] **Step 4: Tests de `useGuardado` para acciones y "Terminar" (fallan)**

En `src/modules/taller/formulario/useGuardado.test.tsx`, añadir al final:

```tsx
/** Arnés para la primera acción "otro" y para la zona de guardar (modelo 13: componente otroi13, idCom 161). */
function ArnesAccion({ onGuardado, antesDeCerrar }: { onGuardado: () => void; antesDeCerrar?: () => Promise<void> }) {
  const [estado, dispatch] = useReducer(reducir, DATOS, estadoInicial)
  const { guardarAccion, pulsarGuardar } = useGuardado({ estado, dispatch, onGuardado, antesDeCerrar })
  const accion = estado.otros[0]
  return (
    <div>
      <button onClick={() => dispatch({ tipo: 'ANADIR_ACCION' })}>añadir</button>
      <button onClick={() => accion && dispatch({ tipo: 'ESCRIBIR_ACCION', id: accion.id, texto: '  Limpieza de conector  ' })}>escribir</button>
      <button onClick={() => accion && guardarAccion(accion.id)}>guardar acción</button>
      <button onClick={pulsarGuardar}>terminar</button>
      <output data-testid="accion">{JSON.stringify(accion ? { confirmando: accion.confirmando, guardando: accion.guardando, guardada: accion.guardada } : null)}</output>
      <output data-testid="guardado">{JSON.stringify(estado.guardado)}</output>
    </div>
  )
}
const leerAccion = () => JSON.parse(screen.getByTestId('accion').textContent ?? 'null') as { confirmando: boolean; guardando: boolean; guardada: { idRep: string; fecha: string } | null }
const leerGuardado = () => JSON.parse(screen.getByTestId('guardado').textContent ?? '{}') as { clics: number; textoConfirmacion: boolean; enCurso: boolean }
const pulsar = (nombre: string) => userEvent.click(screen.getByRole('button', { name: nombre }))

const FILA_ACCION = { idCom: 161, cantidad: 0, reutilizado: false, observacion: 'Limpieza de conector', prefijo: 'otro', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }

describe('useGuardado · guardar acción y terminar', () => {
  it('acción: el primer clic pide confirmación; el segundo hace el POST con la fila "otro" recortada y la marca guardada', async () => {
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    renderConProviders(<ArnesAccion onGuardado={() => {}} />, { sesion: SESION_TEC })
    await pulsar('añadir')
    await pulsar('escribir')
    await pulsar('guardar acción')
    expect(leerAccion().confirmando).toBe(true)
    expect(escrituras(llamadas)).toEqual([])
    await pulsar('guardar acción')
    await waitFor(() => expect(leerAccion().guardada).toEqual({ idRep: 'R20260916_9', fecha: '16/09 09:15' }))
    expect(escrituras(llamadas)).toEqual([
      { metodo: 'POST', ruta: '/api/reparaciones/A20260916_1/filas', cuerpo: { filas: [FILA_ACCION], imei: '355400000000111', idTec: 4, idRepAnterior: 'R20260910_3' } },
    ])
  })

  it('acción con error: aviso "No se pudo guardar la acción: <mensaje>" y el siguiente clic vuelve a pedir confirmación', async () => {
    server.use(...conRegistro().handlers)
    let intentos = 0
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', () => { intentos++; return HttpResponse.json({ message: 'Componente no válido' }, { status: 422 }) }))
    renderConProviders(<ArnesAccion onGuardado={() => {}} />, { sesion: SESION_TEC })
    await pulsar('añadir')
    await pulsar('escribir')
    await pulsar('guardar acción')
    await pulsar('guardar acción')
    expect(await screen.findByText('No se pudo guardar la acción: Componente no válido')).toBeInTheDocument()
    expect(intentos).toBe(1)
    expect(leerAccion().guardando).toBe(false)
    expect(leerAccion().guardada).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    await pulsar('guardar acción')
    expect(intentos).toBe(1)
    await pulsar('guardar acción')
    await waitFor(() => expect(intentos).toBe(2))
  })

  it('terminar: primer clic confirma, segundo ejecuta; antesDeCerrar se espera tras completa y antes de onGuardado', async () => {
    const { handlers, llamadas } = conRegistro()
    server.use(...handlers)
    const orden: string[] = []
    renderConProviders(<ArnesAccion onGuardado={() => orden.push('guardado')} antesDeCerrar={async () => { orden.push(`antes:${escrituras(llamadas).length}`) }} />, { sesion: SESION_TEC })
    await pulsar('añadir')
    await pulsar('escribir')
    await pulsar('terminar')
    expect(leerGuardado()).toEqual({ clics: 1, textoConfirmacion: true, enCurso: false })
    expect(escrituras(llamadas)).toEqual([])
    await pulsar('terminar')
    await waitFor(() => expect(orden).toEqual(['antes:1', 'guardado']))
    expect(escrituras(llamadas)).toEqual([
      { metodo: 'POST', ruta: '/api/reparaciones/completa', cuerpo: { filas: [FILA_ACCION], imei: '355400000000111', idTec: 4, idRepAnterior: 'R20260910_3', idAsignacion: 'A20260916_1', categoria: null } },
    ])
  })

  it('un fallo de antesDeCerrar no impide cerrar', async () => {
    server.use(...conRegistro().handlers)
    const onGuardado = vi.fn()
    renderConProviders(<ArnesAccion onGuardado={onGuardado} antesDeCerrar={() => Promise.reject(new Error('sin borrador'))} />, { sesion: SESION_TEC })
    await pulsar('añadir')
    await pulsar('escribir')
    await pulsar('terminar')
    await pulsar('terminar')
    await waitFor(() => expect(onGuardado).toHaveBeenCalledTimes(1))
  })

  it('terminar con corte de conexión: no pone el literal, pero deja el guardado listo para reintentar', async () => {
    server.use(...conRegistro().handlers)
    server.use(http.post('*/api/reparaciones/completa', () => new HttpResponse(null, { status: 503 })))
    const onGuardado = vi.fn()
    renderConProviders(<ArnesAccion onGuardado={onGuardado} />, { sesion: SESION_TEC })
    await pulsar('añadir')
    await pulsar('escribir')
    await pulsar('terminar')
    await pulsar('terminar')
    expect(await screen.findByText('Sin conexión con el servidor: HTTP 503')).toBeInTheDocument()
    expect(screen.queryByText(/No se pudo guardar/)).not.toBeInTheDocument()
    expect(leerGuardado()).toEqual({ clics: 0, textoConfirmacion: true, enCurso: false })
    expect(onGuardado).not.toHaveBeenCalled()
  })
})
```

```bash
npm test -- useGuardado
```
Expected: fallan los 5 nuevos (`guardarAccion` y `pulsarGuardar` aún no hacen nada: `leerAccion().confirmando` es `false`, `leerGuardado().clics` es `0`); los 4 de la Task 13 siguen en verde.

- [x] **Step 5: `useGuardado` completo para nuevo y glass**

Sustituir `src/modules/taller/formulario/useGuardado.ts` entero por:

```ts
import type { Dispatch } from 'react'
import { esErrorGestionadoGlobalmente, mensajeDeError, StaleDataError } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { useAgotarComponente, useCompleta, useGuardarFila } from './api'
import { accionPideConfirmacion, cuerpoGuardarAccion, cuerpoGuardarFila, planTerminar, reducir, type AccionFormulario, type EstadoFormulario } from './estado'

type Args = { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; onGuardado: () => void; antesDeCerrar?: () => Promise<void> }
type Guardado = { pulsarGuardar: () => void; guardarFila: (prefijo: string) => void; guardarAccion: (id: number) => void }

/** Fecha de "✓ Guardada": hora LOCAL del navegador como texto dd/MM HH:mm, sin año (no la de Madrid de shared/lib/fechas). */
export function fechaGuardado(d: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0')
  return `${dos(d.getDate())}/${dos(d.getMonth() + 1)} ${dos(d.getHours())}:${dos(d.getMinutes())}`
}

/** Orquesta los guardados del formulario: fila a fila, acción a acción y "Terminar asignación". Qué se envía lo deciden los
 *  constructores de estado.ts; aquí se ejecuta, se despacha el resultado y se pone el literal de error de la ficha. Las mutaciones
 *  van con meta.silenciarError: si el error ya lo gestiona el shell (401 → login; corte de conexión → banner y su aviso) no se
 *  añade literal, pero el estado se rehabilita igual. Se usa mutateAsync: los callbacks por llamada de `mutate` solo se disparan
 *  para la última, y dos filas pueden guardarse a la vez. */
export function useGuardado({ estado, dispatch, onGuardado, antesDeCerrar }: Args): Guardado {
  const { sesion } = useSession()
  const { mostrarError } = useAlerta()
  const guardarFilaMut = useGuardarFila()
  const agotarMut = useAgotarComponente()
  const completaMut = useCompleta()
  // En flujo nuevo y glass el servidor toma el técnico del token; el contrato exige el campo y se envía el de la sesión.
  const idTecSesion = sesion?.idTec ?? 0

  function avisar(e: unknown, literal: string) {
    if (!esErrorGestionadoGlobalmente(e)) mostrarError(literal)
  }

  async function ejecutarGuardarFila(idAsignacion: string, prefijo: string) {
    dispatch({ tipo: 'INICIO_GUARDAR_FILA', prefijo })
    try {
      const idRep = await guardarFilaMut.mutateAsync({ idAsignacion, cuerpo: cuerpoGuardarFila(estado, prefijo, idTecSesion) })
      dispatch({ tipo: 'FILA_GUARDADA', prefijo, idRep, fecha: fechaGuardado(new Date()) })
    } catch (e) {
      dispatch({ tipo: 'FALLO_GUARDAR_FILA', prefijo })
      avisar(e, `No se pudo guardar la fila: ${mensajeDeError(e)}`)
    }
  }

  function guardarFila(prefijo: string) {
    const fila = estado.filas.find((f) => f.prefijo === prefijo)
    if (!fila || fila.guardando || fila.guardada !== null || estado.idAsignacion === null) return
    if (!fila.confirmandoGuardar) {
      dispatch({ tipo: 'PEDIR_CONFIRMACION_FILA', prefijo })
      return
    }
    void ejecutarGuardarFila(estado.idAsignacion, prefijo)
  }

  async function ejecutarGuardarAccion(idAsignacion: string, id: number) {
    const cuerpo = cuerpoGuardarAccion(estado, id, idTecSesion)
    if (cuerpo === null) return
    dispatch({ tipo: 'INICIO_GUARDAR_ACCION', id })
    try {
      const idRep = await guardarFilaMut.mutateAsync({ idAsignacion, cuerpo })
      dispatch({ tipo: 'ACCION_GUARDADA', id, idRep, fecha: fechaGuardado(new Date()) })
    } catch (e) {
      dispatch({ tipo: 'FALLO_GUARDAR_ACCION', id })
      avisar(e, `No se pudo guardar la acción: ${mensajeDeError(e)}`)
    }
  }

  function guardarAccion(id: number) {
    const accion = estado.otros.find((a) => a.id === id)
    if (!accion || accion.guardando || accion.guardada !== null || estado.idAsignacion === null) return
    // Lo decide el estado: primer clic, o primer clic tras un fallo (el texto sigue en "✓ Confirmar" pero `pideOtroClic`
    // obliga a confirmar otra vez). FALLO_GUARDAR_ACCION lo enciende; PEDIR_CONFIRMACION_ACCION y ESCRIBIR_ACCION lo apagan.
    if (accionPideConfirmacion(accion)) {
      dispatch({ tipo: 'PEDIR_CONFIRMACION_ACCION', id })
      return
    }
    void ejecutarGuardarAccion(estado.idAsignacion, id)
  }

  /** "Terminar asignación": primero un agotar-componente por cada agotado local AÚN NO registrado, en orden de filas y uno a uno;
   *  cada éxito se anota con AGOTADO_REGISTRADO y el plan se RECALCULA con el estado resultante (no se guarda una copia del plan
   *  del clic): `actual` avanza con el mismo reductor que el estado de la vista, así que un fallo a mitad y su reintento parten
   *  siempre de lo ya registrado. Después `completa`, salvo que el plan diga que no hay nada que completar (solo agotados:
   *  `completa: null`, estén registrados o no). Lo ya hecho no se deshace. */
  async function terminar(idAsignacion: string) {
    dispatch({ tipo: 'INICIO_GUARDADO' })
    let actual = estado
    for (;;) {
      const agotado = planTerminar(actual, idTecSesion).agotados[0]
      if (agotado === undefined) break
      try {
        await agotarMut.mutateAsync({ idAsignacion, cuerpo: agotado.cuerpo })
      } catch (e) {
        dispatch({ tipo: 'FALLO_GUARDADO' })
        const inicio = e instanceof StaleDataError ? 'No se pudo registrar componente agotado' : 'Error al registrar componente agotado'
        avisar(e, `${inicio}: ${mensajeDeError(e)}`)
        return
      }
      const registrado = { tipo: 'AGOTADO_REGISTRADO', prefijo: agotado.prefijo } as const
      dispatch(registrado)
      const siguiente = reducir(actual, registrado)
      if (siguiente === actual) break // no debería ocurrir: evita repetir la misma llamada sin fin
      actual = siguiente
    }
    const completa = planTerminar(actual, idTecSesion).completa
    if (completa !== null) {
      try {
        await completaMut.mutateAsync(completa)
      } catch (e) {
        dispatch({ tipo: 'FALLO_GUARDADO' })
        const base = `No se pudo guardar: ${mensajeDeError(e)}`
        avisar(e, e instanceof StaleDataError ? `${base}\nCierra el formulario y comprueba el estado de la asignación.` : base)
        return
      }
    }
    dispatch({ tipo: 'GUARDADO_COMPLETADO' })
    try {
      await antesDeCerrar?.()
    } catch {
      // Descartar el borrador es silencioso: su fallo no impide cerrar.
    }
    onGuardado()
  }

  function pulsarGuardar() {
    if (estado.guardado.enCurso) return
    if (estado.guardado.clics === 0) {
      dispatch({ tipo: 'PEDIR_CONFIRMACION_GUARDAR' })
      return
    }
    // "Guardar cambios" (modo editar) se ejecuta con planGuardarCambios cuando exista la ruta de edición.
    if (estado.modo === 'editar' || estado.idAsignacion === null) return
    void terminar(estado.idAsignacion)
  }

  return { pulsarGuardar, guardarFila, guardarAccion }
}
```

```bash
npm test -- useGuardado
```
Expected: pasan los 9 tests.

- [x] **Step 6: Tests de integración en el formulario (fallan)**

En `src/modules/taller/formulario/FormularioReparacion.test.tsx`, añadir al final:

```tsx
const FILA_BAT = { idCom: 101, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'bat', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }
const FILA_LCD_14 = { idCom: 112, cantidad: 1, reutilizado: false, observacion: null, prefijo: 'lcd', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }
const FILA_ACCION = { idCom: 161, cantidad: 0, reutilizado: false, observacion: 'Limpieza de conector', prefijo: 'otro', esSolicitud: false, descripcionSolicitud: null, estadoSolicitud: null, enCamino: false }
const completa = (filas: unknown[]) => ({ metodo: 'POST', ruta: '/api/reparaciones/completa', cuerpo: { filas, imei: '355400000000111', idTec: 4, idRepAnterior: null, idAsignacion: 'A20260916_1', categoria: null } })
const agotar = (idCom: number, cantidad: number, descripcion: string | null) => ({ metodo: 'POST', ruta: '/api/reparaciones/A20260916_1/agotar-componente', cuerpo: { idCom, cantidad, descripcion } })

const botonZona = () => within(screen.getByTestId('zona-guardar')).getByRole('button')
/** Los dos clics de "Terminar asignación". */
async function terminar() {
  await userEvent.click(botonZona())
  await userEvent.click(botonZona())
}
async function sumar(prefijo: string, tipo: string, veces = 1) {
  for (let i = 0; i < veces; i++) await userEvent.click(within(screen.getByTestId(`fila-${prefijo}`)).getByRole('button', { name: `Sumar ${tipo}` }))
}
/** Abre el diálogo de la sub-fila, escribe la descripción (si la hay) y confirma. */
async function solicitar(prefijo: string, boton: 'Solicitar pieza' | 'Solicitar y descontar stock', descripcion?: string) {
  await userEvent.click(within(screen.getByTestId(`subfila-${prefijo}`)).getByRole('button', { name: boton }))
  const dlg = within(await screen.findByRole('dialog', { name: /^Solicitar pieza — / }))
  if (descripcion) await userEvent.type(dlg.getByRole('textbox'), descripcion)
  await userEvent.click(dlg.getByRole('button', { name: /^Confirmar: / }))
}
async function escribirAccion(texto: string) {
  await userEvent.click(screen.getByRole('button', { name: '+ Añadir acción' }))
  await userEvent.type(within(screen.getByTestId('accion-1')).getByPlaceholderText('Describe la acción'), texto)
}
const cerrarAviso = () => userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))

describe('FormularioReparacion · otras acciones', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 16, 9, 15))
  })
  afterEach(() => vi.useRealTimers())

  it('"✓ Guardar" en dos clics: POST con { idCom otro, cantidad 0, prefijo "otro", observacion recortada }; la línea queda guardada', async () => {
    const { llamadas } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await escribirAccion('  Limpieza de conector  ')
    const linea = within(screen.getByTestId('accion-1'))
    await userEvent.click(linea.getByRole('button', { name: '✓ Guardar' }))
    expect(escrituras(llamadas)).toEqual([])
    await userEvent.click(linea.getByRole('button', { name: '✓ Confirmar' }))
    expect(await linea.findByText('✓ Guardada 16/09 09:15')).toBeInTheDocument()
    expect(linea.getByPlaceholderText('Describe la acción')).toBeDisabled()
    expect(escrituras(llamadas)).toEqual([
      { metodo: 'POST', ruta: '/api/reparaciones/A20260916_1/filas', cuerpo: { filas: [FILA_ACCION], imei: '355400000000111', idTec: 4, idRepAnterior: null } },
    ])
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('1')
  })

  it('error: "No se pudo guardar la acción: <mensaje>" y el botón sigue en "✓ Confirmar", rehabilitado', async () => {
    abrir()
    server.use(http.post('*/api/reparaciones/:idAsignacion/filas', () => HttpResponse.json({ message: 'Componente no válido' }, { status: 422 })))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await escribirAccion('Limpieza de conector')
    const linea = within(screen.getByTestId('accion-1'))
    await userEvent.click(linea.getByRole('button', { name: '✓ Guardar' }))
    await userEvent.click(linea.getByRole('button', { name: '✓ Confirmar' }))
    expect(await screen.findByText('No se pudo guardar la acción: Componente no válido')).toBeInTheDocument()
    await cerrarAviso()
    expect(linea.getByRole('button', { name: '✓ Confirmar' })).toBeEnabled()
    expect(linea.getByPlaceholderText('Describe la acción')).toBeEnabled()
  })
})

describe('FormularioReparacion · zona de guardar y "Terminar asignación"', () => {
  it('zona oculta sin nada activo y visible con fila activa; "Terminar asignación" → "✓  Confirmar terminar" sin vuelta atrás', async () => {
    const { llamadas } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    expect(screen.queryByTestId('zona-guardar')).not.toBeInTheDocument()
    await sumar('bat', 'Batería')
    expect(screen.getByTestId('zona-guardar')).toHaveClass('bg-form-zona-bg', 'border-form-zona-brd')
    expect(botonZona().textContent).toBe('Terminar asignación')
    expect(botonZona()).toBeEnabled()
    await userEvent.click(botonZona())
    expect(botonZona().textContent).toBe('✓  Confirmar terminar')
    expect(escrituras(llamadas)).toEqual([])
    // Desactivar la fila oculta la zona entera; al reactivarla el texto no ha vuelto atrás.
    await userEvent.click(within(screen.getByTestId('fila-bat')).getByRole('button', { name: 'Restar Batería' }))
    expect(screen.queryByTestId('zona-guardar')).not.toBeInTheDocument()
    await sumar('bat', 'Batería')
    expect(botonZona().textContent).toBe('✓  Confirmar terminar')
  })

  it('orden de llamadas: agotar ×N en orden de filas, luego completa con las filas activas y las acciones pendientes; éxito: cierra', async () => {
    const { llamadas, onCerrar } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    // Se preparan al revés (pantalla antes que chasis) para comprobar que manda el orden de filas.
    await sumar('lcd', 'Pantalla')
    await solicitar('lcd', 'Solicitar y descontar stock')
    await sumar('cha', 'Chasis', 2)
    await solicitar('cha', 'Solicitar y descontar stock', 'Marco doblado')
    await sumar('bat', 'Batería')
    await escribirAccion('Limpieza de conector')
    await terminar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(escrituras(llamadas)).toEqual([agotar(131, 2, 'Marco doblado'), agotar(111, 1, null), completa([FILA_BAT, FILA_ACCION])])
  })

  it('solo agotados: no se llama a completa y se cierra (cantidad 0 en la variante sin stock)', async () => {
    const { llamadas, onCerrar } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 14')
    await solicitar('bat', 'Solicitar pieza')
    await terminar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(escrituras(llamadas)).toEqual([agotar(102, 0, null)])
  })

  it('solo filas guardadas: completa con filas []', async () => {
    const { llamadas, onCerrar } = abrir()
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await sumar('bat', 'Batería')
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await waitFor(() => expect(screen.getByTestId('fila-bat')).toHaveAttribute('data-estado', 'guardada'))
    expect(botonZona().textContent).toBe('Terminar asignación')
    await terminar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(escrituras(llamadas).map((l) => l.ruta)).toEqual(['/api/reparaciones/A20260916_1/filas', '/api/reparaciones/completa'])
    expect(escrituras(llamadas)[1]).toEqual(completa([]))
  })

  it('fallo en el 2.º agotado con 409: aviso "No se pudo registrar componente agotado: …", se detiene, y el reintento no repite el 1.º', async () => {
    const { llamadas, onCerrar } = abrir()
    const agotados: number[] = []
    let fallar = true
    server.use(http.post('*/api/reparaciones/:idAsignacion/agotar-componente', async ({ request }) => {
      const cuerpo = (await request.json()) as { idCom: number }
      agotados.push(cuerpo.idCom)
      if (cuerpo.idCom === 111 && fallar) { fallar = false; return HttpResponse.json({ message: 'Stock insuficiente' }, { status: 409 }) }
      return new HttpResponse(null, { status: 200 })
    }))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 13')
    await sumar('cha', 'Chasis', 2)
    await solicitar('cha', 'Solicitar y descontar stock')
    await sumar('lcd', 'Pantalla')
    await solicitar('lcd', 'Solicitar y descontar stock')
    await terminar()
    expect(await screen.findByText('No se pudo registrar componente agotado: Stock insuficiente')).toBeInTheDocument()
    expect(agotados).toEqual([131, 111])
    expect(onCerrar).not.toHaveBeenCalled()
    await cerrarAviso()
    expect(botonZona().textContent).toBe('✓  Confirmar terminar')
    await terminar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(agotados).toEqual([131, 111, 111])
    // Solo había agotados: completa no se llama en ningún intento.
    expect(escrituras(llamadas)).toEqual([])
  })

  it('fallo de agotar no 409: "Error al registrar componente agotado: …" y el formulario sigue abierto', async () => {
    const { onCerrar } = abrir()
    server.use(http.post('*/api/reparaciones/:idAsignacion/agotar-componente', () => HttpResponse.json({ message: 'Componente inactivo' }, { status: 422 })))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 14')
    await solicitar('bat', 'Solicitar pieza')
    await terminar()
    expect(await screen.findByText('Error al registrar componente agotado: Componente inactivo')).toBeInTheDocument()
    await cerrarAviso()
    expect(onCerrar).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog', { name: TITULO })).toBeInTheDocument()
  })

  it('fallo de completa con 409: mensaje en dos líneas; el formulario sigue abierto, hacen falta otros dos clics y el reintento no repite agotados', async () => {
    const { llamadas, onCerrar } = abrir()
    let completas = 0
    let ultimoCuerpo: unknown = null
    server.use(http.post('*/api/reparaciones/completa', async ({ request }) => {
      completas++
      ultimoCuerpo = await request.json()
      return completas === 1 ? HttpResponse.json({ message: 'La asignación ya fue eliminada o completada' }, { status: 409 }) : new HttpResponse(null, { status: 200 })
    }))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 14')
    await solicitar('bat', 'Solicitar pieza')
    await sumar('lcd', 'Pantalla')
    await terminar()
    const aviso = await screen.findByText(/No se pudo guardar: La asignación ya fue eliminada o completada/)
    expect(aviso.textContent).toBe('No se pudo guardar: La asignación ya fue eliminada o completada\nCierra el formulario y comprueba el estado de la asignación.')
    expect(aviso).toHaveClass('whitespace-pre-line')
    await cerrarAviso()
    expect(onCerrar).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog', { name: TITULO })).toBeInTheDocument()
    // El texto sigue en "✓  Confirmar terminar", pero un solo clic no ejecuta.
    expect(botonZona().textContent).toBe('✓  Confirmar terminar')
    await userEvent.click(botonZona())
    expect(completas).toBe(1)
    await userEvent.click(botonZona())
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(completas).toBe(2)
    expect(ultimoCuerpo).toEqual(completa([FILA_LCD_14]).cuerpo)
    expect(escrituras(llamadas)).toEqual([agotar(102, 0, null)])
  })

  it('fallo de completa con otro error: una sola línea', async () => {
    const { onCerrar } = abrir()
    server.use(http.post('*/api/reparaciones/completa', () => HttpResponse.json({ message: 'Stock insuficiente para lcdi14' }, { status: 422 })))
    await screen.findByRole('dialog', { name: TITULO })
    await elegirModelo('iPhone 14')
    await sumar('lcd', 'Pantalla')
    await terminar()
    const aviso = await screen.findByText(/No se pudo guardar/)
    expect(aviso.textContent).toBe('No se pudo guardar: Stock insuficiente para lcdi14')
    await cerrarAviso()
    expect(onCerrar).not.toHaveBeenCalled()
  })
})
```

```bash
npm test -- FormularioReparacion
```
Expected: fallan los 10 nuevos (`Unable to find … "+ Añadir acción"` / `[data-testid="zona-guardar"]`); los 13 anteriores siguen en verde.

- [x] **Step 7: `FormularioReparacion` con OTRAS ACCIONES y la zona de guardar**

`src/modules/taller/formulario/FormularioReparacion.tsx` queda así (fichero completo):

```tsx
import { useEffect, useReducer, useRef } from 'react'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { useCargaNuevo, useRecargarAlCerrar, type CargaNuevo } from './api'
import { CabeceraFormulario } from './CabeceraFormulario'
import { estadoInicial, filasVisibles, reducir, textoConflicto, tituloPestana } from './estado'
import { FilaComponente } from './FilaComponente'
import { OtrasAcciones } from './OtrasAcciones'
import { SubFilaAgotado } from './SubFilaAgotado'
import { useGuardado } from './useGuardado'
import { ZonaGuardar } from './ZonaGuardar'

type Props =
  | { modo: 'nuevo' | 'glass'; idAsignacion: string; onCerrar: () => void }
  | { modo: 'editar'; idRep: string; onCerrar: () => void }

/** Cabecera de columnas: mismos anchos que las celdas de cada fila (la observación, 280 fijo). El botón derecho no tiene columna. */
const COLUMNAS = [
  { texto: 'Unit (+/-)', clase: 'w-[70px]' },
  { texto: 'Componente', clase: 'w-[100px]' },
  { texto: 'SKU', clase: 'w-[170px]' },
  { texto: 'Stock', clase: 'w-[70px] text-center' },
  { texto: '¿Reutilizado?', clase: 'w-[110px]' },
  { texto: 'Observación', clase: 'w-[280px]' },
] as const

/** El formulario de reparación como diálogo modal sobre la lista. La URL lo gobierna: quien lo monta (formulario/rutas.tsx)
 *  decide a dónde se vuelve en `onCerrar`. El flujo nuevo y el de glass comparten carga (`cargarNuevo` deduce la categoría
 *  del prefijo de la asignación); la edición tiene su propia carga y su propia ruta, y hasta que exista no pinta nada. */
export function FormularioReparacion(props: Props) {
  if (props.modo === 'editar') return null
  return <FormularioNuevo idAsignacion={props.idAsignacion} onCerrar={props.onCerrar} />
}

function FormularioNuevo({ idAsignacion, onCerrar }: { idAsignacion: string; onCerrar: () => void }) {
  const carga = useCargaNuevo(idAsignacion)
  const { mostrarError } = useAlerta()

  // Al cerrar —✕, Escape, Atrás o tras guardar— se recarga la lista de debajo: se hace al desmontar, que es lo único que
  // tienen en común las cuatro salidas. La ref evita depender de la identidad de la función en cada render.
  const recargar = useRecargarAlCerrar()
  const recargarRef = useRef(recargar)
  const onCerrarRef = useRef(onCerrar)
  useEffect(() => {
    recargarRef.current = recargar
    onCerrarRef.current = onCerrar
  })
  useEffect(() => () => recargarRef.current(), [])

  // La carga va con meta.silenciarError: el aviso lo da esta vista y después vuelve a la lista. 401 y corte de conexión ya
  // los gestiona el shell (redirección a login y banner): ahí solo se cierra.
  useEffect(() => {
    if (!carga.isError) return
    if (!esErrorGestionadoGlobalmente(carga.error)) mostrarError(mensajeDeError(carga.error))
    onCerrarRef.current()
  }, [carga.isError, carga.error, mostrarError])

  if (!carga.data) return null
  // El reductor se monta solo con la carga terminada: `key` evita reinicializarlo con datos de otra asignación.
  return <FormularioCargado key={idAsignacion} carga={carga.data} onCerrar={onCerrar} />
}

function FormularioCargado({ carga, onCerrar }: { carga: CargaNuevo; onCerrar: () => void }) {
  const { sesion } = useSession()
  const [estado, dispatch] = useReducer(reducir, carga.datos, estadoInicial)
  const titulo = tituloPestana(estado)
  const conflicto = textoConflicto(carga.asignacionesActivas, carga.datos.idAsignacion, sesion?.idTec ?? null)
  // Guardar (fila a fila, o todo) cierra el formulario igual que ✕: la recarga de la lista la hace el desmontaje.
  const guardado = useGuardado({ estado, dispatch, onGuardado: onCerrar })

  // El título de la ventana del JavaFX pasa a ser el de la pestaña; al cerrar vuelve el que había.
  useEffect(() => {
    const anterior = document.title
    document.title = titulo
    return () => {
      document.title = anterior
    }
  }, [titulo])

  return (
    <Dialog open onOpenChange={(abierto) => { if (!abierto) onCerrar() }}>
      {/* max-w-none y sm:max-w-none anulan el max-w y el sm:max-w-lg de DialogContent (tailwind-merge no descarta la variante
          con modificador si no se repite). Pulsar fuera no cierra: la ventana del JavaFX solo se cerraba con su ✕. */}
      <DialogContent
        aria-label={titulo}
        aria-describedby={undefined}
        showCloseButton={false}
        onInteractOutside={(e) => e.preventDefault()}
        className="flex h-[calc(100vh-48px)] min-h-[700px] w-[calc(100vw-48px)] max-w-none min-w-[960px] flex-col gap-0 overflow-hidden rounded-none border-0 bg-fondo-vista p-0 sm:max-w-none"
      >
        <DialogTitle className="sr-only">{titulo}</DialogTitle>
        <CabeceraFormulario estado={estado} conflicto={conflicto} dispatch={dispatch} onCerrar={onCerrar} />
        <div className="flex border-b border-form-cabecera-brd bg-form-cabecera-bg">
          {COLUMNAS.map((c) => (
            <span key={c.texto} className={`${c.clase} shrink-0 px-2.5 py-1.5 text-[12px] text-azul-gris`}>
              {c.texto}
            </span>
          ))}
        </div>
        {/* Filas y OTRAS ACCIONES desplazan juntas; el hueco flexible empuja la zona de guardar al fondo. */}
        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
          {filasVisibles(estado) ? (
            estado.filas.map((fila) => (
              <FilaComponente key={fila.prefijo} estado={estado} fila={fila} dispatch={dispatch} onGuardarFila={guardado.guardarFila}>
                <SubFilaAgotado estado={estado} fila={fila} dispatch={dispatch} />
              </FilaComponente>
            ))
          ) : (
            <p className="py-10 text-center text-[13px] text-azul-gris">Selecciona un modelo de iPhone para continuar</p>
          )}
          <OtrasAcciones estado={estado} dispatch={dispatch} onGuardarAccion={guardado.guardarAccion} />
        </div>
        <ZonaGuardar estado={estado} onPulsar={guardado.pulsarGuardar} />
      </DialogContent>
    </Dialog>
  )
}
```

```bash
npm test -- FormularioReparacion OtrasAcciones useGuardado
```
Expected: todo en verde (23 + 6 + 9).

- [x] **Step 8: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/OtrasAcciones.tsx src/modules/taller/formulario/OtrasAcciones.test.tsx src/modules/taller/formulario/ZonaGuardar.tsx src/modules/taller/formulario/useGuardado.ts src/modules/taller/formulario/useGuardado.test.tsx src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx
git commit -m "feat(web): otras acciones, zona de guardar y \"Terminar asignación\" con el orden de llamadas de la referencia"
```


### Task 16: Web — `useBorrador` e integración: recuperación, verificación de filas guardadas, borrado tras guardar y volcado al cerrar

**Files:**
- Create: `src/modules/taller/formulario/useBorrador.ts`, `src/modules/taller/formulario/useBorrador.test.tsx`
- Modify: `src/modules/taller/formulario/FormularioReparacion.tsx`, `src/modules/taller/formulario/FormularioReparacion.test.tsx`, `src/modules/taller/formulario/CabeceraFormulario.tsx` (banda)
- Modify (red de seguridad del Step 7c: TODOS los ficheros de test que montan el formulario real): `src/modules/taller/formulario/FormularioReparacion.test.tsx` (el `afterEach` va en el bloque del Step 5, a nivel de fichero: cubre también los `describe` de las Tasks 12–15) y `src/modules/taller/formulario/rutas.test.tsx` (monta `FormularioNuevoRuta` real). `src/modules/taller/pendientes/PendientesPage.test.tsx` NO se toca: sus rutas hijas son un `<p>` de relleno, no el formulario. `useGuardado.test.tsx`, `FilaComponente.test.tsx`, `SubFilaAgotado.test.tsx` y `OtrasAcciones.test.tsx` tampoco: montan arneses sin `useBorrador`.

**Interfaces:**
- Consumes: W7 (`leerBorrador`, `aplicarBorrador`, `serializar`, `tieneGuardadas`), W10 (`guardarBorrador`, `borrarBorrador`, `idsReparacionesDelImei`), `estado.revision`, `estado.volcados`, `estado.borradorDescartado`, `estado.borradorRecuperado`, `estado.idAsignacion`, `estado.imei`, acciones `REEMPLAZAR` y `DESBLOQUEAR_BORRADAS` (W5), `useGuardado({ antesDeCerrar })` (W11, Task 15), `handlersFormulario` / `conRegistro` (W10), `BORRADOR_JAVAFX` (W2).
- Produces:

```ts
// src/modules/taller/formulario/useBorrador.ts
export const RETARDO_BORRADOR_MS = 2000
export function useBorrador(args: { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; borradorJson: string | null; activo: boolean }): {
  listo: boolean                                 // borrador aplicado (o no había): hasta entonces no se escribe
  volcarAhora: () => Promise<void>               // cancela el temporizador y PUT/DELETE ya (silencioso)
  descartar: () => Promise<void>                 // tras guardado real: cancela, DELETE, y ya no vuelve a escribir
}
/** Añadido de esta tarea (no está en el esqueleto): promesa que se resuelve cuando no quedan escrituras de borrador en vuelo.
 *  La usan los tests que montan el formulario para esperar el volcado del desmontaje antes de que MSW retire sus handlers. */
export function borradorEnReposo(): Promise<void>
```
  `FormularioCargado` (el componente interior de `FormularioReparacion.tsx`, el del `useReducer`; lo dejó así la Task 12: `FormularioReparacion` → `FormularioNuevo` (carga, aviso de error, recarga al desmontar) → `FormularioCargado({ carga: CargaNuevo, onCerrar })`) enlaza `useGuardado({ antesDeCerrar: descartar })` y su cierre pasa a ser `volcarAhora()` + `onCerrar()`. `CabeceraFormulario` pinta `data-testid="banda-borrador"`.

Semántica de los dos contadores del reductor que este hook escucha (Tasks 6–9): `estado.revision` sube SOLO con cambios de datos (modelo, `SUMAR`/`RESTAR`, SKU, "Reutilizado", observación, `CONFIRMAR_AGOTADO`/`CANCELAR_AGOTADO`, añadir/escribir/quitar acción); NO la suben `PEDIR_CONFIRMACION_*`, `INICIO_*`, `FALLO_*`, `AGOTADO_REGISTRADO`, `GUARDADO_COMPLETADO`, `EDITAR_DESCRIPCION_AGOTADO` ni `REEMPLAZAR`. `estado.volcados` sube con `FILA_GUARDADA`, `ACCION_GUARDADA` y `DESBLOQUEAR_BORRADAS` cuando cambia algo (y estas tres no suben `revision`). Por eso el autoguardado cuelga de `revision` y el volcado inmediato de `volcados`.

**Ficha** (`docs/paridad/formulario.md`): "Banda \"✓ Borrador recuperado\": justo debajo de la de incidencia…"; de "Borrador": "Autoguardado 2 s después del último cambio…", "No se guarda mientras se está aplicando un borrador recuperado ni después de un guardado real…", "JSON ilegible o fallo al leerlo: formulario limpio, sin error y sin banda", "Filas guardadas borradas por otro…"; "Cerrar en flujo nuevo y Glass (✕, Atrás, Escape…): **nunca pregunta**; cancela el temporizador del borrador, vuelca…"; "…borrador volcado al momento (sin esperar los 2 s)…" (fila y acción); "\"Guardar descripción\" actualiza… pero **no** reprograma el autoguardado"; "F5 o acceso directo por URL… en flujo nuevo recupera el borrador"; "Silenciosos: … lectura y escritura del borrador, comprobación de filas guardadas".

**Decisiones de diseño que fija esta tarea** (quien la ejecute no ve las demás):
- `listo` **no** es un `useState`: se deriva (`no había borrador legible` o `estado.borradorRecuperado`). Así no hay `setState` dentro de un efecto y la bandera cambia en el mismo commit que el `REEMPLAZAR`.
- Todas las escrituras pasan por una **cola serie de módulo**: un `DELETE` de `descartar` nunca adelanta a un `PUT` que siguiera en vuelo, y dos volcados seguidos con el mismo contenido (✕ y, acto seguido, el desmontaje) no repiten la llamada. Si una escritura **falla**, no se anota como escrita: el siguiente volcado (el de cerrar) la reintenta una vez. Nunca se muestra nada.
- El volcado del desmontaje solo se registra cuando `listo` es `true`: con React 19 StrictMode (montar → desmontar → montar en desarrollo) no se escribe un borrador "vacío" encima del que todavía no se ha aplicado.
- El último estado se guarda en una ref actualizada en `useLayoutEffect` (no durante el render: regla `react-hooks/refs`, mismo patrón que `shared/ui/exportable.tsx`).
- El test del hook sustituye `./api` con `vi.mock` (temporizadores falsos + red real es frágil); que `guardarBorrador` hace `PUT { contenido }`, `borrarBorrador` `DELETE` e `idsReparacionesDelImei` `GET /api/reparaciones/imei/{imei}` ya lo prueba `api.test.tsx` (Task 11), y los tests de integración de esta tarea lo recorren de punta a punta con MSW y temporizadores reales.

- [x] **Step 1: Test del hook (falla)**

`src/modules/taller/formulario/useBorrador.test.tsx`:

```tsx
import { StrictMode, useReducer } from 'react'
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { agrupados, BORRADOR_JAVAFX, detalleEdicion } from '../test/fabrica'
import { estadoInicial, reducir, type DatosEditar, type DatosNuevo, type EstadoFormulario } from './estado'
import { borradorEnReposo, RETARDO_BORRADOR_MS, useBorrador } from './useBorrador'

// Temporizadores falsos + red real es frágil: el hook se prueba contra un ./api sustituido. El tráfico HTTP de estas tres
// funciones lo cubre api.test.tsx y, de punta a punta, FormularioReparacion.test.tsx.
const api = vi.hoisted(() => ({
  guardarBorrador: vi.fn<(idAsignacion: string, contenido: string) => Promise<void>>(),
  borrarBorrador: vi.fn<(idAsignacion: string) => Promise<void>>(),
  idsReparacionesDelImei: vi.fn<(imei: string) => Promise<string[]>>(),
}))
vi.mock('./api', () => api)

const IMEI = '355400000000111'
const ID = 'A20260916_1'

function datosNuevo(parcial: Partial<DatosNuevo> = {}): DatosNuevo {
  return { modo: 'nuevo', idAsignacion: ID, imei: IMEI, agrupados: agrupados(), solicitudes: [], incidencia: null, modeloTelefono: '13', ...parcial }
}
const DATOS_EDITAR: DatosEditar = { modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [], accionesYaReparadas: [] }

/** Un borrador mínimo con el formato del cliente de escritorio: batería con dos unidades. */
const BORRADOR_SIMPLE = JSON.stringify({
  modelo: '13',
  filas: [{ prefijo: 'bat', idCom: 101, cantidad: 2, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: false }],
  otros: [],
})
type Guardable = { guardada: boolean; idRepGenerado?: string }
const crudo = JSON.parse(BORRADOR_JAVAFX) as { filas: Guardable[]; otros: Guardable[] }
/** Los idRep que BORRADOR_JAVAFX da por guardados (una fila y una acción). */
const IDS_GUARDADOS = [...crudo.filas, ...crudo.otros].filter((x) => x.guardada).map((x) => x.idRepGenerado ?? '?')

function montar(borradorJson: string | null, opciones: { datos?: DatosNuevo | DatosEditar; activo?: boolean; estricto?: boolean } = {}) {
  return renderHook(
    () => {
      const [estado, dispatch] = useReducer(reducir, opciones.datos ?? datosNuevo(), estadoInicial)
      const borrador = useBorrador({ estado, dispatch, borradorJson, activo: opciones.activo ?? true })
      return { estado, dispatch, borrador }
    },
    opciones.estricto ? { wrapper: StrictMode } : undefined,
  )
}
const avanzar = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })
/** Deja correr promesas, efectos y la cola de escrituras sin mover el reloj. */
async function asentar() {
  for (let i = 0; i < 3; i++) await act(async () => { await borradorEnReposo() })
}
const fila = (e: EstadoFormulario, prefijo: string) => e.filas.find((f) => f.prefijo === prefijo)!
const contenidoDe = (n: number) => JSON.parse(api.guardarBorrador.mock.calls[n][1]) as { modelo?: string; filas: Record<string, unknown>[]; otros: Record<string, unknown>[] }

beforeEach(() => {
  vi.useFakeTimers()
  api.guardarBorrador.mockReset().mockResolvedValue(undefined)
  api.borrarBorrador.mockReset().mockResolvedValue(undefined)
  api.idsReparacionesDelImei.mockReset().mockResolvedValue([])
})
afterEach(() => {
  vi.useRealTimers()
})

describe('useBorrador (ficha docs/paridad/formulario.md, sección Borrador)', () => {
  it('RETARDO_BORRADOR_MS son 2 s', () => {
    expect(RETARDO_BORRADOR_MS).toBe(2000)
  })

  it('no escribe nada hasta 2 s después del último cambio y reinicia con cada cambio', async () => {
    const { result } = montar(null)
    expect(result.current.borrador.listo).toBe(true)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await avanzar(1500)
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await avanzar(1999)
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    await avanzar(1)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    expect(api.guardarBorrador.mock.calls[0][0]).toBe(ID)
    expect(contenidoDe(0).filas).toEqual([expect.objectContaining({ prefijo: 'bat', idCom: 101, cantidad: 2 })])
  })

  it('borrador vacío → DELETE; con contenido → PUT { contenido }', async () => {
    const { result } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await avanzar(RETARDO_BORRADOR_MS)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    expect(contenidoDe(0)).toMatchObject({
      modelo: '13',
      filas: [{ prefijo: 'bat', idCom: 101, cantidad: 1, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: false }],
      otros: [],
    })
    expect(api.borrarBorrador).not.toHaveBeenCalled()
    act(() => result.current.dispatch({ tipo: 'RESTAR', prefijo: 'bat' }))
    await avanzar(RETARDO_BORRADOR_MS)
    expect(api.borrarBorrador).toHaveBeenCalledTimes(1)
    expect(api.borrarBorrador).toHaveBeenCalledWith(ID)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
  })

  it('volcados dispara un PUT inmediato (fila guardada) y el temporizador pendiente no lo repite', async () => {
    const { result } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    act(() => result.current.dispatch({ tipo: 'PEDIR_CONFIRMACION_FILA', prefijo: 'bat' }))
    act(() => result.current.dispatch({ tipo: 'INICIO_GUARDAR_FILA', prefijo: 'bat' }))
    act(() => result.current.dispatch({ tipo: 'FILA_GUARDADA', prefijo: 'bat', idRep: 'R20260916_9', fecha: '16/09 09:15' }))
    await asentar()
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    expect(contenidoDe(0).filas).toEqual([expect.objectContaining({ prefijo: 'bat', guardada: true, idRepGenerado: 'R20260916_9', fechaGuardado: '16/09 09:15' })])
    await avanzar(5000)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
  })

  it('aplicar el borrador recuperado no dispara escritura', async () => {
    const { result } = montar(BORRADOR_SIMPLE)
    await asentar()
    expect(result.current.borrador.listo).toBe(true)
    expect(result.current.estado.borradorRecuperado).toBe(true)
    expect(fila(result.current.estado, 'bat').cantidad).toBe(2)
    await avanzar(5000)
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    expect(api.borrarBorrador).not.toHaveBeenCalled()
    expect(api.idsReparacionesDelImei).not.toHaveBeenCalled()
  })

  it('StrictMode: el montaje doble no vuelca un borrador vacío encima del que aún no se ha aplicado', async () => {
    const { result } = montar(BORRADOR_SIMPLE, { estricto: true })
    await asentar()
    await avanzar(5000)
    expect(api.borrarBorrador).not.toHaveBeenCalled()
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    expect(fila(result.current.estado, 'bat').cantidad).toBe(2)
  })

  it('borrador ilegible: formulario limpio, sin banda y sin error', async () => {
    const { result } = montar('{esto no es json')
    await asentar()
    expect(result.current.borrador.listo).toBe(true)
    expect(result.current.estado.borradorRecuperado).toBe(false)
    expect(fila(result.current.estado, 'bat').cantidad).toBe(0)
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    expect(api.borrarBorrador).not.toHaveBeenCalled()
  })

  it('banda "✓ Borrador recuperado" solo con borrador no vacío: un borrador con solo el modelo no cuenta', async () => {
    const { result } = montar(JSON.stringify({ modelo: '13', filas: [], otros: [] }))
    await asentar()
    expect(result.current.estado.borradorRecuperado).toBe(false)
    expect(result.current.borrador.listo).toBe(true)
  })

  it('con filas guardadas pide las reparaciones del IMEI; las que faltan vuelven a editables y se reescribe el borrador al momento', async () => {
    api.idsReparacionesDelImei.mockResolvedValue([])
    const { result } = montar(BORRADOR_JAVAFX)
    await asentar()
    expect(api.idsReparacionesDelImei).toHaveBeenCalledTimes(1)
    expect(api.idsReparacionesDelImei).toHaveBeenCalledWith(IMEI)
    expect(fila(result.current.estado, 'bat').guardada).toBeNull()
    expect(result.current.estado.otros.every((o) => o.guardada === null)).toBe(true)
    // Sin mover el reloj: el desbloqueo sube `volcados`.
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    expect(contenidoDe(0).filas.every((f) => f.guardada === false)).toBe(true)
    expect(contenidoDe(0).otros.every((o) => o.guardada === false)).toBe(true)
  })

  it('si las guardadas siguen existiendo, quedan bloqueadas y no se reescribe nada', async () => {
    api.idsReparacionesDelImei.mockResolvedValue(IDS_GUARDADOS)
    const { result } = montar(BORRADOR_JAVAFX)
    await asentar()
    expect(fila(result.current.estado, 'bat').guardada).toEqual({ idRep: 'R20260916_5', fecha: '16/09 09:15' })
    await avanzar(5000)
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    expect(api.borrarBorrador).not.toHaveBeenCalled()
  })

  it('si esa consulta falla, quedan bloqueadas y no hay error', async () => {
    api.idsReparacionesDelImei.mockRejectedValue(new Error('sin red'))
    const { result } = montar(BORRADOR_JAVAFX)
    await asentar()
    expect(fila(result.current.estado, 'bat').guardada).not.toBeNull()
    await avanzar(5000)
    expect(api.guardarBorrador).not.toHaveBeenCalled()
  })

  it('volcarAhora cancela el temporizador', async () => {
    const { result } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await avanzar(500)
    await act(async () => { await result.current.borrador.volcarAhora() })
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    await avanzar(5000)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
  })

  it('descartar hace DELETE y después ningún cambio vuelve a escribir, tampoco al desmontar', async () => {
    const { result, unmount } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await act(async () => { await result.current.borrador.descartar() })
    expect(api.borrarBorrador).toHaveBeenCalledTimes(1)
    expect(api.borrarBorrador).toHaveBeenCalledWith(ID)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await avanzar(5000)
    unmount()
    await asentar()
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    expect(api.borrarBorrador).toHaveBeenCalledTimes(1)
  })

  it('tras GUARDADO_COMPLETADO (borradorDescartado) tampoco escribe', async () => {
    const { result } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    act(() => result.current.dispatch({ tipo: 'GUARDADO_COMPLETADO' }))
    await avanzar(5000)
    await act(async () => { await result.current.borrador.volcarAhora() })
    expect(api.guardarBorrador).not.toHaveBeenCalled()
  })

  it('desmontar (Atrás) vuelca sin esperar los 2 s', async () => {
    const { result, unmount } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    unmount()
    await asentar()
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    expect(contenidoDe(0).filas).toEqual([expect.objectContaining({ prefijo: 'bat', cantidad: 1 })])
  })

  it('✕ y desmontaje seguidos con el mismo contenido escriben una sola vez', async () => {
    const { result, unmount } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await act(async () => { await result.current.borrador.volcarAhora() })
    unmount()
    await asentar()
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
  })

  it('un fallo del PUT no muestra nada y el volcado de cerrar lo reintenta una vez', async () => {
    api.guardarBorrador.mockRejectedValueOnce(new Error('sin red'))
    const { result } = montar(null)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'bat' }))
    await avanzar(RETARDO_BORRADOR_MS)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    await act(async () => { await result.current.borrador.volcarAhora() })
    expect(api.guardarBorrador).toHaveBeenCalledTimes(2)
    // Ya escrito: otro volcado con el mismo contenido no repite.
    await act(async () => { await result.current.borrador.volcarAhora() })
    expect(api.guardarBorrador).toHaveBeenCalledTimes(2)
  })

  it('"Guardar descripción" no reprograma el autoguardado; el cambio entra con el volcado de cerrar', async () => {
    // Modelo 14: la batería (bati14) está a 0 → variante sin stock.
    const { result } = montar(null, { datos: datosNuevo({ modeloTelefono: '14' }) })
    act(() => result.current.dispatch({ tipo: 'CONFIRMAR_AGOTADO', prefijo: 'bat', descripcion: 'batería original' }))
    await avanzar(RETARDO_BORRADOR_MS)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    act(() => result.current.dispatch({ tipo: 'EDITAR_DESCRIPCION_AGOTADO', prefijo: 'bat', descripcion: 'batería compatible' }))
    await avanzar(5000)
    expect(api.guardarBorrador).toHaveBeenCalledTimes(1)
    await act(async () => { await result.current.borrador.volcarAhora() })
    expect(api.guardarBorrador).toHaveBeenCalledTimes(2)
    expect(contenidoDe(1).filas).toEqual([expect.objectContaining({ prefijo: 'bat', agotadoConfirmado: true, descripcionAgotado: 'batería compatible' })])
  })

  it('modo editar: el hook inactivo no llama a nada', async () => {
    const { result, unmount } = montar(null, { datos: DATOS_EDITAR, activo: false })
    expect(result.current.borrador.listo).toBe(true)
    act(() => result.current.dispatch({ tipo: 'SUMAR', prefijo: 'cam' }))
    await avanzar(5000)
    await act(async () => { await result.current.borrador.volcarAhora() })
    await act(async () => { await result.current.borrador.descartar() })
    unmount()
    await asentar()
    expect(api.guardarBorrador).not.toHaveBeenCalled()
    expect(api.borrarBorrador).not.toHaveBeenCalled()
    expect(api.idsReparacionesDelImei).not.toHaveBeenCalled()
  })
})
```

- [x] **Step 2: Ejecutar y ver que falla**

```bash
npx vitest run src/modules/taller/formulario/useBorrador.test.tsx
```
Esperado: FAIL — `Failed to resolve import "./useBorrador"` (el fichero no existe).

- [x] **Step 3: Implementar el hook**

`src/modules/taller/formulario/useBorrador.ts`:

```ts
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, type Dispatch } from 'react'
import { borrarBorrador, guardarBorrador, idsReparacionesDelImei } from './api'
import { aplicarBorrador, leerBorrador, serializar, tieneGuardadas } from './borrador'
import type { AccionFormulario, EstadoFormulario } from './estado'

export const RETARDO_BORRADOR_MS = 2000

/** Cola serie de módulo: las escrituras del borrador salen de una en una y en orden, de modo que el DELETE de `descartar`
 *  nunca adelanta a un PUT que siguiera en vuelo. Las tareas nunca rechazan (capturan su propio error). */
let cola: Promise<void> = Promise.resolve()
function encolar(tarea: () => Promise<void>): Promise<void> {
  cola = cola.then(tarea, tarea)
  return cola
}
/** Se resuelve cuando no quedan escrituras de borrador en vuelo (para los tests que montan el formulario). */
export function borradorEnReposo(): Promise<void> {
  return cola
}

type Args = { estado: EstadoFormulario; dispatch: Dispatch<AccionFormulario>; borradorJson: string | null; activo: boolean }

/** Borrador persistente del flujo nuevo y Glass: aplica el recuperado una sola vez, comprueba que sus filas guardadas
 *  sigan existiendo, autoguarda 2 s después del último cambio, vuelca al momento cuando sube `estado.volcados` y al
 *  desmontar, y deja de escribir tras un guardado real. Todos los fallos son silenciosos. */
export function useBorrador({ estado, dispatch, borradorJson, activo }: Args): { listo: boolean; volcarAhora: () => Promise<void>; descartar: () => Promise<void> } {
  // Ilegible, vacío o inactivo → null: formulario limpio, sin banda y sin error.
  const recuperado = useMemo(() => (activo ? leerBorrador(borradorJson) : null), [activo, borradorJson])
  // Derivado, no estado: pasa a true en el mismo commit que el REEMPLAZAR (aplicarBorrador pone borradorRecuperado).
  const listo = recuperado === null || estado.borradorRecuperado

  // Último estado para los volcados que ocurren fuera del render (temporizador, cierre, desmontaje). Se actualiza en un
  // layout effect, no durante el render (regla react-hooks/refs; mismo patrón que shared/ui/exportable.tsx).
  const vivo = useRef({ estado, listo, activo })
  useLayoutEffect(() => {
    vivo.current = { estado, listo, activo }
  })
  /** Último contenido escrito con éxito (null = DELETE); undefined = aún nada en esta apertura. */
  const escrito = useRef<string | null | undefined>(undefined)
  const descartado = useRef(false)
  const aplicado = useRef(false)
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null)
  const revisionInicial = useRef(estado.revision)
  const volcadosVistos = useRef(estado.volcados)

  const cancelar = useCallback(() => {
    if (temporizador.current !== null) {
      clearTimeout(temporizador.current)
      temporizador.current = null
    }
  }, [])

  const escribir = useCallback(
    (): Promise<void> =>
      encolar(async () => {
        const { estado: e, listo: aplicadoYa, activo: enUso } = vivo.current
        if (!enUso || !aplicadoYa || descartado.current || e.borradorDescartado || e.idAsignacion === null) return
        const contenido = serializar(e)
        // Mismo contenido que la última escritura buena (✕ y, acto seguido, el desmontaje): no se repite.
        if (escrito.current !== undefined && contenido === escrito.current) return
        try {
          if (contenido === null) await borrarBorrador(e.idAsignacion)
          else await guardarBorrador(e.idAsignacion, contenido)
          escrito.current = contenido
        } catch {
          // Silencioso. No se anota como escrito: el siguiente volcado (el de cerrar) lo reintenta.
        }
      }),
    [],
  )

  // 1) Recuperación, una sola vez (la ref sobrevive al montaje doble de StrictMode), y verificación de las guardadas.
  useEffect(() => {
    if (recuperado === null || aplicado.current) return
    aplicado.current = true
    const base = vivo.current.estado
    dispatch({ tipo: 'REEMPLAZAR', estado: aplicarBorrador(base, recuperado) })
    if (!tieneGuardadas(recuperado)) return
    idsReparacionesDelImei(base.imei).then(
      (ids) => dispatch({ tipo: 'DESBLOQUEAR_BORRADAS', idsExistentes: ids }),
      () => {
        // Silencioso: siguen bloqueadas hasta la siguiente apertura.
      },
    )
  }, [recuperado, dispatch])

  // 2) Autoguardado: 2 s después del último cambio. REEMPLAZAR no sube `revision`, así que aplicar el borrador no escribe.
  useEffect(() => {
    if (!activo || !listo || estado.borradorDescartado || estado.revision === revisionInicial.current) return
    const t = setTimeout(() => {
      temporizador.current = null
      void escribir()
    }, RETARDO_BORRADOR_MS)
    temporizador.current = t
    return () => clearTimeout(t)
  }, [activo, listo, estado.revision, estado.borradorDescartado, escribir])

  // 3) Volcado inmediato: fila guardada, acción guardada y filas desbloqueadas. Va DESPUÉS del efecto 2 para cancelar
  //    el temporizador que ese mismo commit acaba de programar.
  useEffect(() => {
    if (!activo || !listo || estado.volcados === volcadosVistos.current) return
    volcadosVistos.current = estado.volcados
    cancelar()
    void escribir()
  }, [activo, listo, estado.volcados, cancelar, escribir])

  // 4) Volcado al desmontar (Atrás del navegador, cambio de ruta). Solo se registra con `listo`: el desmontaje simulado
  //    de StrictMode, que ocurre antes de aplicar el borrador recuperado, no escribe nada.
  useEffect(() => {
    if (!activo || !listo) return
    return () => {
      cancelar()
      void escribir()
    }
  }, [activo, listo, cancelar, escribir])

  const volcarAhora = useCallback((): Promise<void> => {
    cancelar()
    return escribir()
  }, [cancelar, escribir])

  const descartar = useCallback((): Promise<void> => {
    cancelar()
    descartado.current = true
    return encolar(async () => {
      const { estado: e, activo: enUso } = vivo.current
      if (!enUso || e.idAsignacion === null) return
      try {
        await borrarBorrador(e.idAsignacion)
      } catch {
        // Se ignora: el guardado real ya está hecho y el formulario se cierra igualmente.
      }
    })
  }, [cancelar])

  return { listo, volcarAhora, descartar }
}
```

- [x] **Step 4: Ejecutar y ver que pasa**

```bash
npx vitest run src/modules/taller/formulario/useBorrador.test.tsx
```
Esperado: PASS (19 tests). Si `'volcados dispara un PUT inmediato…'` fallara porque el reductor exige otro orden de acciones para llegar a `FILA_GUARDADA`, se ajusta la secuencia de `dispatch` del test, no el hook.

- [x] **Step 5: Tests de integración en el formulario (fallan)**

Añadir **al final** de `src/modules/taller/formulario/FormularioReparacion.test.tsx` este bloque (los imports que el fichero ya tenga se fusionan, no se duplican):

```tsx
import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_TEC } from '@/test/render'
import { reparacion } from '../test/fabrica'
import { FormularioReparacion } from './FormularioReparacion'
import { conRegistro, type EscenarioFormulario, type LlamadaRegistrada } from './test/handlers'
import { borradorEnReposo } from './useBorrador'

// Red de seguridad: se desmonta AQUÍ (con los handlers de MSW todavía activos) y se espera el volcado del desmontaje.
// Este afterEach corre antes que el de src/test/setup.ts (los hooks "after" van en orden inverso al de registro).
afterEach(async () => {
  cleanup()
  await borradorEnReposo()
})

const BORRADOR_BAT_2 = JSON.stringify({
  modelo: '13',
  filas: [{ prefijo: 'bat', idCom: 101, cantidad: 2, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: false }],
  otros: [],
})
const BORRADOR_BAT_GUARDADA = JSON.stringify({
  modelo: '13',
  filas: [{ prefijo: 'bat', idCom: 101, cantidad: 1, reutilizado: false, solicitudNueva: false, agotadoConfirmado: false, guardada: true, idRepGenerado: 'R20260916_5', fechaGuardado: '16/09 09:15' }],
  otros: [],
})

function abrirNuevo(escenario: EscenarioFormulario = {}) {
  const registro = conRegistro({ modeloTelefono: '13', ...escenario })
  server.use(...registro.handlers)
  const onCerrar = vi.fn()
  const vista = renderConProviders(<FormularioReparacion modo="nuevo" idAsignacion="A20260916_1" onCerrar={onCerrar} />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/reparar/A20260916_1' })
  return { ...vista, ...registro, onCerrar }
}
/** `conRegistro` anota `metodo` en mayúsculas (`request.method`), `ruta` = pathname y `cuerpo` = JSON recibido (null en un DELETE). */
const delBorrador = (llamadas: LlamadaRegistrada[]) => llamadas.filter((l) => l.ruta.endsWith('/borrador'))
const contenidoDe = (l: LlamadaRegistrada) => JSON.parse((l.cuerpo as { contenido: string }).contenido) as { filas: Record<string, unknown>[]; otros: Record<string, unknown>[] }
// `botonZona()` ya existe en este fichero desde la Task 15 (el botón de data-testid="zona-guardar"): se reutiliza, no se redeclara.

describe('FormularioReparacion — borrador persistente', () => {
  it('F5 o acceso directo recupera el borrador: banda "✓ Borrador recuperado" bajo los avisos y la fila con su cantidad', async () => {
    abrirNuevo({ borrador: BORRADOR_BAT_2, incidencia: 'R20260910_1' })
    const banda = await screen.findByTestId('banda-borrador')
    expect(banda.textContent).toBe('✓ Borrador recuperado')
    expect(banda).toHaveClass('bg-tipo-reparacion-bg', 'text-tipo-reparacion-text', 'text-[11px]', 'font-bold', 'w-full')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('2')
    // Justo debajo de la banda de incidencia.
    expect(screen.getByTestId('banda-incidencia').compareDocumentPosition(banda) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('sin borrador, o con un borrador ilegible, no hay banda ni aviso', async () => {
    const { unmount } = abrirNuevo({ borrador: null })
    await screen.findByTestId('fila-bat')
    expect(screen.queryByTestId('banda-borrador')).not.toBeInTheDocument()
    unmount()
    abrirNuevo({ borrador: '{esto no es json' })
    await screen.findByTestId('fila-bat')
    expect(screen.queryByTestId('banda-borrador')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Error' })).not.toBeInTheDocument()
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
  })

  it('✕ vuelca el borrador en ese momento (PUT) y cierra sin preguntar', async () => {
    const { llamadas, onCerrar } = abrirNuevo()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(delBorrador(llamadas)).toHaveLength(1))
    expect(delBorrador(llamadas)[0]).toMatchObject({ metodo: 'PUT', ruta: '/api/reparaciones/A20260916_1/borrador' })
    expect(contenidoDe(delBorrador(llamadas)[0]).filas).toEqual([expect.objectContaining({ prefijo: 'bat', idCom: 101, cantidad: 1 })])
  })

  it('✕ con el formulario vacío borra el borrador (DELETE)', async () => {
    const { llamadas } = abrirNuevo()
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(delBorrador(llamadas)).toHaveLength(1))
    expect(delBorrador(llamadas)[0]).toMatchObject({ metodo: 'DELETE', ruta: '/api/reparaciones/A20260916_1/borrador', cuerpo: null })
  })

  it('guardar una fila vuelca el borrador al momento, sin esperar los 2 s', async () => {
    const { llamadas } = abrirNuevo()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    await userEvent.click(screen.getByTestId('boton-derecho-bat'))
    // waitFor espera 1 s como mucho: si el volcado dependiera del retardo de 2 s, no llegaría.
    await waitFor(() => expect(delBorrador(llamadas)).toHaveLength(1))
    expect(contenidoDe(delBorrador(llamadas)[0]).filas).toEqual([expect.objectContaining({ prefijo: 'bat', guardada: true, idRepGenerado: 'R20260916_9' })])
  })

  it('"Terminar asignación": el DELETE del borrador va después de completa y antes de cerrar, y al desmontar no se reescribe', async () => {
    const { llamadas, onCerrar, unmount } = abrirNuevo()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(botonZona())
    await userEvent.click(botonZona())
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    const orden = llamadas.map((l) => `${l.metodo} ${l.ruta}`)
    const iCompleta = orden.indexOf('POST /api/reparaciones/completa')
    expect(iCompleta).toBeGreaterThanOrEqual(0)
    expect(orden.indexOf('DELETE /api/reparaciones/A20260916_1/borrador')).toBeGreaterThan(iCompleta)
    unmount()
    await borradorEnReposo()
    // Lo último que se hizo con el borrador fue borrarlo: el desmontaje no lo reescribe.
    expect(delBorrador(llamadas).at(-1)?.metodo).toBe('DELETE')
  })

  it('fila guardada que otro borró vuelve a editable y el borrador se reescribe; si sigue existiendo, queda bloqueada', async () => {
    const primera = abrirNuevo({ borrador: BORRADOR_BAT_GUARDADA, reparacionesImei: [] })
    await waitFor(() => expect(screen.getByTestId('fila-bat')).toHaveAttribute('data-estado', 'normal'))
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
    // El borrador queda vacío tras el desbloqueo → DELETE inmediato.
    await waitFor(() => expect(delBorrador(primera.llamadas)).toHaveLength(1))
    expect(delBorrador(primera.llamadas)[0].metodo).toBe('DELETE')
    primera.unmount()
    await borradorEnReposo()
    abrirNuevo({ borrador: BORRADOR_BAT_GUARDADA, reparacionesImei: [reparacion({ idRep: 'R20260916_5' })] })
    await waitFor(() => expect(screen.getByTestId('fila-bat')).toHaveAttribute('data-estado', 'guardada'))
    expect(screen.getByTestId('boton-derecho-bat').textContent).toBe('✓ Guardada 16/09 09:15')
  })
})
```

- [x] **Step 6: Ejecutar y ver que falla**

```bash
npx vitest run src/modules/taller/formulario/FormularioReparacion.test.tsx
```
Esperado: FAIL — no existe `banda-borrador`, ✕ no produce ninguna llamada a `/borrador`, y tras "Terminar asignación" no hay `DELETE …/borrador`.

- [x] **Step 7: Integrar en la vista**

**7a. `CabeceraFormulario.tsx` — la banda.** Como **último hijo** del contenedor que devuelve `CabeceraFormulario` (después de la banda `data-testid="banda-incidencia"`; la cabecera de columnas la pinta `FormularioReparacion` a continuación, así que queda entre ambas), añadir:

```tsx
      {estado.borradorRecuperado && (
        // #E3F2FD / #1565C0: mismos hexadecimales que los tokens tipo-reparacion-* (decisión 5 del plan: se reutilizan).
        <div data-testid="banda-borrador" className="w-full bg-tipo-reparacion-bg px-4 py-1 text-[11px] font-bold text-tipo-reparacion-text">
          ✓ Borrador recuperado
        </div>
      )}
```
No se oculta nunca mientras el formulario siga abierto: `borradorRecuperado` no vuelve a `false`.

**7b. `FormularioReparacion.tsx` — `FormularioCargado`.** El fichero tiene tres funciones desde la Task 12: `FormularioReparacion` (exportada; elige por modo), `FormularioNuevo` (llama a `useCargaNuevo`, avisa del error de carga, recarga la lista al desmontar y monta el interior con `key`) y `FormularioCargado({ carga, onCerrar }: { carga: CargaNuevo; onCerrar: () => void })`, que es quien tiene el `useReducer`. `carga.borradorJson` ya le llega dentro de `carga` (W10): **no hace falta ninguna prop nueva** y `FormularioNuevo` no se toca. Cuatro cambios, todos en `FormularioCargado`:

1. Imports: la línea de React pasa a `import { useCallback, useEffect, useReducer, useRef } from 'react'` y se añade, en orden alfabético entre los relativos (detrás de `./SubFilaAgotado`), `import { useBorrador } from './useBorrador'`.

2. Sustituir estas dos líneas (las dejó la Task 13):

```tsx
  // Guardar (fila a fila, o todo) cierra el formulario igual que ✕: la recarga de la lista la hace el desmontaje.
  const guardado = useGuardado({ estado, dispatch, onGuardado: onCerrar })
```
por:

```tsx
  // Borrador: solo flujo nuevo y Glass (los únicos que llegan hoy a este componente; en edición el hook queda inactivo).
  const { volcarAhora, descartar } = useBorrador({ estado, dispatch, borradorJson: carga.borradorJson, activo: estado.modo !== 'editar' })
  /** ✕ y Escape: nunca pregunta. Vuelca el borrador YA (PUT, o DELETE si está vacío) y cierra sin esperar a la red; si esa
   *  escritura falla, el volcado del desmontaje la reintenta una vez. Atrás del navegador solo desmonta. */
  const cerrar = useCallback(() => {
    void volcarAhora()
    onCerrar()
  }, [volcarAhora, onCerrar])
  // Guardar de verdad ("Terminar asignación") no vuelca: BORRA el borrador (antesDeCerrar) y después cierra con el onCerrar
  // de las props, no con `cerrar`. La recarga de la lista la sigue haciendo el desmontaje de FormularioNuevo.
  const guardado = useGuardado({ estado, dispatch, onGuardado: onCerrar, antesDeCerrar: descartar })
```

3. En el `<Dialog open onOpenChange=…>` del formulario: `onOpenChange={(abierto) => { if (!abierto) cerrar() }}` (antes llamaba a `onCerrar`).

4. En `<CabeceraFormulario … />`: `onCerrar={cerrar}` (antes `onCerrar={onCerrar}`).

El resto del JSX (`guardado.guardarFila`, `guardado.guardarAccion`, `guardado.pulsarGuardar`) no cambia. `useGuardado.ts` tampoco: desde la Task 15 ya espera `antesDeCerrar` (`await antesDeCerrar?.()` dentro de un `try`) **después** de despachar `GUARDADO_COMPLETADO` y **antes** de `onGuardado()`, y `terminar` tiene una única salida con éxito (con `completa` o sin ella), así que el `DELETE` sale en los dos casos. `descartar` nunca rechaza.

**7c. Red de seguridad en los demás tests que montan el formulario real.** A partir de esta tarea, todo fichero de test que monte `FormularioReparacion` de verdad necesita desmontar ANTES de que `src/test/setup.ts` retire los handlers de MSW, y esperar a la cola de escrituras. Hoy son dos: `FormularioReparacion.test.tsx` (ya lo lleva: el `afterEach` del Step 5 está a nivel de fichero y vale para todos sus `describe`, también los de las Tasks 12–15) y `formulario/rutas.test.tsx`. Se comprueba con:

```bash
grep -rlE "FormularioReparacion|FormularioNuevoRuta|FormularioEditarRuta" src --include=*.test.tsx
```
Esperado: exactamente esos dos ficheros (`pendientes/PendientesPage.test.tsx` usa un `<p>` de relleno como ruta hija y no aparece). En `src/modules/taller/formulario/rutas.test.tsx`, fusionar en los imports `cleanup` (de `@testing-library/react`) y `afterEach` (de `vitest`), añadir `import { borradorEnReposo } from './useBorrador'` y, tras los imports:

```tsx
// El formulario vuelca su borrador al desmontar: se desmonta aquí, con los handlers de MSW aún activos, y se espera.
// Este afterEach corre antes que el de src/test/setup.ts (los hooks "after" van en orden inverso al de registro).
afterEach(async () => {
  cleanup()
  await borradorEnReposo()
})
```
Sin esto el `PUT`/`DELETE` del desmontaje llegaría después de `server.resetHandlers()` y MSW lo denunciaría como petición sin handler (y marcaría la conexión como caída para el test siguiente). Las tareas posteriores que añadan otro fichero de test que monte el formulario real (p. ej. una página con la ruta hija real) deben copiar este mismo `afterEach`.

- [x] **Step 8: Ejecutar y ver que pasa**

```bash
npx vitest run src/modules/taller/formulario src/modules/taller/pendientes
```
Esperado: PASS, sin avisos `[MSW] … without a matching request handler` en la salida.

- [x] **Step 9: Verde y commit**

```bash
npm run check
git add src/modules/taller/formulario/useBorrador.ts src/modules/taller/formulario/useBorrador.test.tsx src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx src/modules/taller/formulario/CabeceraFormulario.tsx src/modules/taller/formulario/rutas.test.tsx
git commit -m "feat(web): borrador persistente del formulario: autoguardado, recuperación, verificación y volcado al cerrar"
```

---

### Task 17: Web — variante Glass y su ruta

**Files:**
- Modify: `src/app/router.tsx`, `src/modules/taller/pendientes/PendientesPage.tsx`, `src/modules/taller/pendientes/PendientesPage.test.tsx`, `src/modules/taller/formulario/rutas.test.tsx`, `src/modules/taller/formulario/FormularioReparacion.test.tsx`
- Modify (una línea): `src/modules/taller/formulario/rutas.tsx`

**Interfaces:**
- Consumes: `FormularioNuevoRuta(props: { glass: boolean })` (W11), modo `glass` de `estadoInicial` (W5: solo filas `g` y `mc`, `categoria: 'G'`), `cargarNuevo` (W10: `tipo=G` para una `AG…`), `ocultarAnadirGlass` (`lib/entregaGlass`), `renderConRouter` (W8), `handlersFormulario` / `conRegistro` (W10), `asignacionActiva` (W2).
- Produces: ruta `/reparaciones/pendientes/glass/reparar/:idAsignacion` (W12); "Añadir glass" navega a ella; en `FormularioNuevoRuta`, el modo lo decide el **prefijo del id** (`AG…` → `glass`) y la lista a la que se vuelve, la **ruta** (`glass`).

**Ficha:** `formulario.md` — sección "Variante Glass" completa ("Asignación `AG…`… solo filas \"Glass\" y \"Marco\"…", "Ningún texto del formulario cambia…", "Incidencia con `tipo=G`… la banda de conflicto agrupa igual, excluida la propia `AG…`", "En flujo nuevo no se envía `categoria`…"); "Flujo nuevo: … \"Añadir glass\" a `/reparaciones/pendientes/glass/reparar/<idAsignacion>`…". `pendientes.md` — "(sub-proyecto 2) El botón abre el formulario…" (pestaña Glass).

- [x] **Step 1: Tests (fallan)**

**1a. `src/modules/taller/pendientes/PendientesPage.test.tsx`** — **sustituir** el test `'"Añadir glass" sigue reservado, con el tooltip del formulario'` (lo dejó la Task 12 y deja de ser cierto) por este (`renderConRouter` ya se importa de `@/test/render` desde la Task 12; `glass`, de `../test/fabrica`):

```tsx
  it('"Añadir glass" navega a /reparaciones/pendientes/glass/reparar/<id>, la lista sigue montada y no se pinta si ocultarAnadirGlass', async () => {
    const bloqueada = { ...glass(null), idRep: 'AG20260916_1', imei: '351111111111111', normalAbierta: true, normalTecnicoNombre: 'Técnico J' }
    const libre = { ...glass(null), idRep: 'AG20260916_2', imei: '352222222222222' }
    server.use(http.get('*/api/glass/asignaciones', () => HttpResponse.json([bloqueada, libre])))
    const { router } = renderConRouter(
      [{ path: '/reparaciones/pendientes/glass', element: <PendientesPage tipo="GLASS" />, children: [{ path: 'reparar/:idAsignacion', element: <p>FORMULARIO GLASS</p> }] }],
      { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass' },
    )
    await screen.findByText('AG20260916_2')
    expect(within(screen.getByRole('row', { name: /AG20260916_1/ })).queryByRole('button', { name: 'Añadir glass' })).not.toBeInTheDocument()
    const boton = within(screen.getByRole('row', { name: /AG20260916_2/ })).getByRole('button', { name: 'Añadir glass' })
    expect(boton).toBeEnabled()
    expect(boton.parentElement).not.toHaveAttribute('title')
    await userEvent.click(boton)
    expect(router.state.location.pathname).toBe('/reparaciones/pendientes/glass/reparar/AG20260916_2')
    expect(await screen.findByText('FORMULARIO GLASS')).toBeInTheDocument()
    expect(screen.getByText('AG20260916_2')).toBeInTheDocument()
  })
```

**1b. `src/modules/taller/formulario/rutas.test.tsx`** — añadir al final (el `beforeEach` de la Task 12 ya registró `handlersFormulario()`; los `server.use(...)` de estos tests van después y por eso ganan; imports a fusionar: `Outlet` de `react-router`; `screen`, `waitFor`, `within` de `@testing-library/react`; `userEvent`; `server`; `renderConRouter`, `SESION_TEC`; `handlersFormulario` de `./test/handlers`; `FormularioNuevoRuta` de `./rutas`):

```tsx
const LISTAS_PENDIENTES = [
  { path: '/reparaciones/pendientes', element: <><p>LISTA REPARACIONES</p><Outlet /></>, children: [{ path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass={false} /> }] },
  { path: '/reparaciones/pendientes/glass', element: <><p>LISTA GLASS</p><Outlet /></>, children: [{ path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass /> }] },
]
const idsDeFilas = () => screen.getAllByTestId(/^fila-/).map((f) => f.getAttribute('data-testid'))

describe('FormularioNuevoRuta — variante glass', () => {
  it('la ruta glass abre el formulario de la AG y cerrar vuelve a /reparaciones/pendientes/glass', async () => {
    server.use(...handlersFormulario({ asignacion: { idRep: 'AG20260916_2', imei: '355400000000222' }, modeloTelefono: '13' }))
    const { router } = renderConRouter(LISTAS_PENDIENTES, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass/reparar/AG20260916_2' })
    expect(await screen.findByRole('dialog', { name: 'Nueva reparación — IMEI 355400000000222' })).toBeInTheDocument()
    expect(idsDeFilas()).toEqual(['fila-g', 'fila-mc'])
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/pendientes/glass'))
    expect(screen.getByText('LISTA GLASS')).toBeInTheDocument()
  })

  it('manda el prefijo del id, no la ruta: una AG… bajo la ruta de reparaciones es glass, y una A… bajo la ruta glass es reparación; se vuelve a la lista de la ruta', async () => {
    server.use(...handlersFormulario({ asignacion: { idRep: 'AG20260916_2', imei: '355400000000222' }, modeloTelefono: '13' }))
    const primera = renderConRouter(LISTAS_PENDIENTES, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/reparar/AG20260916_2' })
    await screen.findByTestId('fila-g')
    expect(idsDeFilas()).toEqual(['fila-g', 'fila-mc'])
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(primera.router.state.location.pathname).toBe('/reparaciones/pendientes'))
    primera.unmount()

    server.use(...handlersFormulario({ asignacion: { idRep: 'A20260916_1' }, modeloTelefono: '13' }))
    const segunda = renderConRouter(LISTAS_PENDIENTES, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass/reparar/A20260916_1' })
    await screen.findByTestId('fila-bat')
    expect(idsDeFilas()).toEqual(['fila-bat', 'fila-cha', 'fila-lcd', 'fila-cam'])
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(segunda.router.state.location.pathname).toBe('/reparaciones/pendientes/glass'))
  })
})
```

**1c. `src/modules/taller/formulario/FormularioReparacion.test.tsx`** — añadir al final (imports a fusionar: `HttpResponse`, `http` de `msw`; `asignacionActiva` de `../test/fabrica`; el resto ya está por la Task 16):

```tsx
function abrirGlass(escenario: EscenarioFormulario = {}) {
  const registro = conRegistro({ asignacion: { idRep: 'AG20260916_2', imei: '355400000000222' }, modeloTelefono: '13', ...escenario })
  server.use(...registro.handlers)
  const onCerrar = vi.fn()
  const vista = renderConProviders(<FormularioReparacion modo="glass" idAsignacion="AG20260916_2" onCerrar={onCerrar} />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass/reparar/AG20260916_2' })
  return { ...vista, ...registro, onCerrar }
}

describe('FormularioReparacion — variante glass', () => {
  it('solo tiene las filas "Glass" y "Marco" (en el orden del servidor) más OTRAS ACCIONES', async () => {
    abrirGlass()
    await screen.findByTestId('fila-g')
    expect(screen.getAllByTestId(/^fila-/).map((f) => f.getAttribute('data-testid'))).toEqual(['fila-g', 'fila-mc'])
    expect(within(screen.getByTestId('fila-g')).getByText('Glass')).toBeInTheDocument()
    expect(within(screen.getByTestId('fila-mc')).getByText('Marco')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'SKU de Glass' })).toHaveTextContent('gi13')
    expect(screen.getByTestId('otras-acciones')).toBeInTheDocument()
  })

  it('los textos no cambian: título "Nueva reparación — IMEI …", etiqueta IMEI y "Terminar asignación"', async () => {
    abrirGlass()
    expect(await screen.findByRole('dialog', { name: 'Nueva reparación — IMEI 355400000000222' })).toBeInTheDocument()
    expect(document.title).toBe('Nueva reparación — IMEI 355400000000222')
    expect(screen.getByText('IMEI: 355400000000222')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Glass' }))
    expect(within(screen.getByTestId('zona-guardar')).getByRole('button').textContent).toBe('Terminar asignación')
  })

  it('la incidencia se pide con tipo=G y se muestra igual', async () => {
    let tipo: string | null = null
    const { handlers } = conRegistro({ asignacion: { idRep: 'AG20260916_2', imei: '355400000000222' }, modeloTelefono: '13' })
    server.use(...handlers)
    server.use(http.get('*/api/reparaciones/imei/:imei/incidencia-activa', ({ request }) => {
      tipo = new URL(request.url).searchParams.get('tipo')
      return HttpResponse.json({ value: 'G20260910_4' })
    }))
    renderConProviders(<FormularioReparacion modo="glass" idAsignacion="AG20260916_2" onCerrar={vi.fn()} />, { sesion: SESION_TEC, ruta: '/reparaciones/pendientes/glass/reparar/AG20260916_2' })
    expect((await screen.findByTestId('banda-incidencia')).textContent).toBe('⚠ Resuelve incidencia: G20260910_4')
    expect(tipo).toBe('G')
  })

  it('"✓ Guardar fila" y "Terminar asignación" no llevan categoria (el servidor la deduce del prefijo AG)', async () => {
    const { llamadas, onCerrar } = abrirGlass()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Glass' }))
    await userEvent.click(screen.getByTestId('boton-derecho-g'))
    await userEvent.click(screen.getByTestId('boton-derecho-g'))
    await waitFor(() => expect(screen.getByTestId('fila-g')).toHaveAttribute('data-estado', 'guardada'))
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Marco' }))
    const zona = () => within(screen.getByTestId('zona-guardar')).getByRole('button')
    await userEvent.click(zona())
    await userEvent.click(zona())
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    const filas = llamadas.find((l) => l.ruta.endsWith('/AG20260916_2/filas'))!
    expect(filas.cuerpo).not.toHaveProperty('categoria')
    expect(filas.cuerpo).toMatchObject({ imei: '355400000000222', filas: [{ idCom: 141, cantidad: 1, prefijo: 'g', esSolicitud: false }] })
    const completa = llamadas.find((l) => l.ruta.endsWith('/completa'))!
    // El contrato marca todas las propiedades como required: "no se envía" = viaja a null.
    expect(completa.cuerpo).toMatchObject({ idAsignacion: 'AG20260916_2', categoria: null, imei: '355400000000222', filas: [{ idCom: 151, cantidad: 1, prefijo: 'mc' }] })
  })

  it('la banda de conflicto agrupa igual y excluye la propia AG', async () => {
    abrirGlass({
      activas: [
        asignacionActiva({ idRep: 'AG20260916_2', nombreTecnico: 'Técnico A', idTec: 4 }),
        asignacionActiva({ idRep: 'A20260916_7', nombreTecnico: 'Técnico H', idTec: 6 }),
        asignacionActiva({ idRep: 'AP20260916_3', nombreTecnico: 'Técnico A', idTec: 4 }),
      ],
    })
    expect((await screen.findByTestId('banda-conflicto')).textContent).toBe('⚠ Este IMEI también está asignado a — Reparación: Técnico H · Pulido: Técnico A (tú)')
  })
})
```

- [x] **Step 2: Ejecutar y ver que falla**

```bash
npx vitest run src/modules/taller/pendientes/PendientesPage.test.tsx src/modules/taller/formulario/rutas.test.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx
```
Esperado: FAIL en 1a (`Añadir glass` sigue deshabilitado: `toBeEnabled` falla). Los de 1b y 1c pasan ya: `FormularioNuevo` no mira la prop `modo` (la categoría la deduce `cargarNuevo` del prefijo `AG` del id y el estado nace en modo `glass`), así que documentan y blindan un comportamiento que las Tasks 6–15 ya dan.

- [x] **Step 3: Implementación**

**3a. `src/app/router.tsx`** — la ruta de la pestaña Glass gana su hija (la de reparaciones ya la tiene desde la Task 12). Sustituir la línea

```tsx
              { path: '/reparaciones/pendientes/glass', element: <PendientesPage tipo="GLASS" /> },
```
por

```tsx
              {
                path: '/reparaciones/pendientes/glass',
                element: <PendientesPage tipo="GLASS" />,
                children: [{ path: 'reparar/:idAsignacion', element: <FormularioNuevoRuta glass /> }],
              },
```
(`FormularioNuevoRuta` ya está importado de `@/modules/taller/formulario/rutas`. `/reparaciones/pendientes/glass` es hermana de `/reparaciones/pendientes`, no hija: `reparar/:idAsignacion` no choca.)

**3b. `src/modules/taller/pendientes/PendientesPage.tsx`** — `BotonAnadir` queda así, **entero** (sustituye a lo que dejó la Task 12, que mantenía "Añadir glass" deshabilitado con tooltip):

```tsx
/** "Añadir reparación" / "Añadir glass": abre el formulario de esa asignación como ruta hija (diálogo sobre la lista),
 *  directamente y sin confirmación previa. En una glass bloqueada (ocultarAnadirGlass) no se pinta. */
function BotonAnadir({ rep, glass }: { rep: ReparacionResumen; glass: boolean }) {
  const navigate = useNavigate()
  if (glass && ocultarAnadirGlass(rep)) return null
  const lista = glass ? '/reparaciones/pendientes/glass' : '/reparaciones/pendientes'
  return <BotonPrimario onClick={() => navigate(`${lista}/reparar/${rep.idRep}`)}>{glass ? 'Añadir glass' : 'Añadir reparación'}</BotonPrimario>
}
```
Imports: `import { Outlet, useNavigate } from 'react-router'` (fusionado con el que ya haya) y **quitar** `import { TOOLTIP_FORMULARIO } from '../lib/textos'`, que queda sin uso. El `<Outlet />` del final del JSX lo puso la Task 12 y sirve igual para la pestaña Glass.

**3c. `src/modules/taller/formulario/rutas.tsx`** — dentro de `FormularioNuevoRuta` (Task 12), la prop `modo={glass ? 'glass' : 'nuevo'}` no tiene efecto (manda el prefijo del id), pero miente al lector: se sustituye por un `modo` calculado del id. Añadir, debajo de `const lista = …`:

```tsx
  // Manda el prefijo del id, no la ruta: una AG… abierta bajo /pendientes/reparar/ sigue siendo glass, y al revés.
  // La prop `glass` (la ruta) solo decide a qué lista se vuelve al cerrar.
  const modo = idAsignacion.startsWith('AG') ? 'glass' : 'nuevo'
```
y en el JSX, `modo={glass ? 'glass' : 'nuevo'}` pasa a `modo={modo}`. La lista de cierre no cambia: `glass ? '/reparaciones/pendientes/glass' : '/reparaciones/pendientes'`.

- [x] **Step 4: Ejecutar y ver que pasa**

```bash
npx vitest run src/modules/taller/pendientes src/modules/taller/formulario
```
Esperado: PASS.

- [x] **Step 5: Verde y commit**

```bash
npm run check
git add src/app/router.tsx src/modules/taller/pendientes/PendientesPage.tsx src/modules/taller/pendientes/PendientesPage.test.tsx src/modules/taller/formulario/rutas.tsx src/modules/taller/formulario/rutas.test.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx
git commit -m "feat(web): variante glass del formulario y su ruta desde Pendientes"
```

---

### Task 18: Web — modo edición: rutas de Historial e IMEIs, "Editar" habilitado, "Salir sin guardar" y 409

**Files:**
- Create: `src/modules/taller/formulario/DialogoSalirSinGuardar.tsx`
- Modify: `src/app/router.tsx`, `src/modules/taller/rutas.tsx`, `src/modules/taller/rutas.test.tsx`, `src/modules/taller/formulario/rutas.tsx`, `src/modules/taller/formulario/rutas.test.tsx`, `src/modules/taller/formulario/FormularioReparacion.tsx` (+ `FormularioReparacion.test.tsx`), `src/modules/taller/formulario/FilaComponente.tsx` (+ `FilaComponente.test.tsx`), `src/modules/taller/formulario/OtrasAcciones.tsx` (+ `OtrasAcciones.test.tsx`), `src/modules/taller/formulario/useGuardado.ts` (+ `useGuardado.test.tsx`), `src/modules/taller/componentes/MenuHistorial.tsx`, `src/modules/taller/componentes/useAccionesTrabajo.tsx` (solo el tipo de `acciones`), `src/modules/taller/historial/HistorialPage.tsx` (+ `HistorialPage.test.tsx`), `src/modules/taller/imeis/ImeiDetallePage.tsx` (+ `ImeiDetallePage.test.tsx`), `src/modules/taller/pendientes/PendientesPage.tsx` (solo comprobar que ya no importa `TOOLTIP_FORMULARIO`), `src/modules/taller/lib/textos.ts`

**Interfaces:**
- Consumes: `useCargaEditar`, `useEditarReparacion`, `useCompleta`, `useRecargarAlCerrar` (W10); `planGuardarCambios`, `hayCambiosSinGuardar`, `previsionStock`, `filaEditadaInvalida`, `botonDerecho` (`yaReparado`), `subFila`, `textoBotonGuardar`, `filaSinSku` (W6); `ConfirmDialog` (`shared/ui`); `renderConRouter` (W8); `StaleDataError`, `mensajeDeError`, `esErrorGestionadoGlobalmente`, `MSG_SIN_PERMISOS` (`shared/api/errors`); `useBorrador({ activo: false })` (Task 16).
- Produces:

```tsx
// formulario/rutas.tsx
export function FormularioEditarRuta(props: { origen: 'historial' | 'historial-glass' | 'imei' }): JSX.Element
// taller/rutas.tsx
export function RequiereSupertecnico(): JSX.Element     // no SUPERTECNICO → aviso MSG_SIN_PERMISOS y Navigate a la lista
// formulario/DialogoSalirSinGuardar.tsx
export function DialogoSalirSinGuardar(props: { abierto: boolean; onSalir: () => void; onCancelar: () => void }): JSX.Element
// componentes/MenuHistorial.tsx
export type AccionesHistorial = {
  editar: (rep: ReparacionResumen) => void
  borrar: (rep: ReparacionResumen) => void
  anadirIncidencia: (rep: ReparacionResumen) => void
  cancelarIncidencia: (rep: ReparacionResumen) => void
}
```
  `useGuardado.pulsarGuardar` ejecuta `planGuardarCambios` en modo `editar`; `FormularioReparacion` atiende `modo: 'editar'`; rutas `editar/:idRep` de W12; se elimina `TOOLTIP_FORMULARIO`.

**Ficha:** `formulario.md` — "Edición: \"Editar\" del menú contextual del Historial navega a…"; "Roles: … edición, solo SUPERTECNICO; ADMIN no tiene ninguna entrada…"; "Título (pestaña del navegador)…" (edición); "Cerrar en edición…"; "Diálogo \"Salir sin guardar\"…"; "Etiqueta IMEI…" (edición); sección "Modo edición" en lo que es pintado ("Fila editada: fondo #EBF4FF…", "Previsualización de stock…", "…contador en #C94040 negrita y zona de guardar oculta", "Filas de otros tipos… \"✓  Ya reparado\"…", "Resto de filas: normales… sin \"✓ Guardar fila\"", "Esas filas nuevas no tienen sub-fila de agotado…", "Acciones en edición… \"✓ Ya reparada\"…", "Editar una acción \"otro\"…", "\"Guardar cambios\", en orden…", "Las filas y acciones nuevas… **conservan el técnico original**…", "Un cambio inválido oculta la zona… cerrar en ese estado **no pregunta**"); "En edición las líneas no tienen \"✓ Guardar\"…"; "Texto … \"Guardar cambios\" (edición). Primer clic: \"✓  Confirmar terminar\" — **también en edición**"; "Guardar cambios: con 409…"; "Edición de una `G…`: solo filas Glass y Marco" y "solo en edición de una `G…` se envía `categoria: \"G\"`"; "403 al abrir (… edición sin ser SUPERTECNICO)…"; "Al cerrar, con o sin guardar, se recarga la lista de debajo". `historial.md` e `imeis.md` — "(sub-proyecto 2) \"Editar\" abre el formulario en modo edición…".

**Decisiones de la ficha que esta tarea aplica** (no se reabren):
- La edición es **solo de SUPERTECNICO**: "Editar" solo lo ve el supertécnico y la ruta va tras `RequiereSupertecnico`; el servidor exige el mismo rol.
- Las filas y acciones **nuevas** añadidas en edición conservan el técnico **original**: `completa` va con `idTec = detalle.idTec`, `idAsignacion: null` e `idRepAnterior: null` (lo construye `planGuardarCambios`; aquí solo se envía tal cual).
- En edición **no hay sub-fila de agotado** ni "✓ Guardar fila" ni "✓ Guardar" de acción.
- El primer clic en "Guardar cambios" muestra **"✓  Confirmar terminar"** (dos espacios), igual que en el flujo nuevo.
- Un **409** muestra el aviso literal de dos líneas y **no recarga** el formulario.
- En edición no hay borrador: el `useBorrador` de la Task 16 ya queda inactivo con `estado.modo === 'editar'`.

**Nota sobre los textos con dos espacios en los tests:** Testing Library colapsa los espacios del DOM al buscar por texto o por nombre accesible, así que los literales con doble espacio se comprueban con `.textContent` sobre un elemento localizado por `data-testid` o por rol sin nombre.

- [x] **Step 1: `RequiereSupertecnico` — test (falla)**

`src/modules/taller/rutas.test.tsx` — ampliar el import de `./rutas` con `RequiereSupertecnico`, el de `react-router` con `Outlet`, el de `@/test/render` con `renderConRouter`, y añadir dentro del `describe`:

```tsx
  it('RequiereSupertecnico: TECNICO y ADMIN por URL reciben el aviso genérico y vuelven a la lista; el supertécnico pasa', async () => {
    const rutas = [
      { path: '/reparaciones/historial', element: <><p>HISTORIAL</p><Outlet /></>, children: [{ element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <p>FORMULARIO</p> }] }] },
      { path: '/reparaciones/historial/glass', element: <><p>HISTORIAL GLASS</p><Outlet /></>, children: [{ element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <p>FORMULARIO</p> }] }] },
      { path: '/reparaciones/imeis/:imei', element: <><p>DETALLE IMEI</p><Outlet /></>, children: [{ element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <p>FORMULARIO</p> }] }] },
    ]
    const tec = renderConRouter(rutas, { sesion: SESION_TEC, ruta: '/reparaciones/historial/editar/R20260916_5' })
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
    expect(tec.router.state.location.pathname).toBe('/reparaciones/historial')
    expect(screen.queryByText('FORMULARIO')).not.toBeInTheDocument()
    tec.unmount()

    const admin = renderConRouter(rutas, { sesion: SESION_ADMIN, ruta: '/reparaciones/imeis/351900000000041/editar/G20260912_1' })
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
    expect(admin.router.state.location.pathname).toBe('/reparaciones/imeis/351900000000041')
    admin.unmount()

    const glass = renderConRouter(rutas, { sesion: SESION_TEC, ruta: '/reparaciones/historial/glass/editar/G20260912_1' })
    await screen.findByRole('dialog', { name: 'Error' })
    expect(glass.router.state.location.pathname).toBe('/reparaciones/historial/glass')
    glass.unmount()

    renderConRouter(rutas, { sesion: SESION_SUPER, ruta: '/reparaciones/historial/editar/R20260916_5' })
    expect(await screen.findByText('FORMULARIO')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Error' })).not.toBeInTheDocument()
  })
```

```bash
npx vitest run src/modules/taller/rutas.test.tsx
```
Esperado: FAIL — `RequiereSupertecnico` no se exporta de `./rutas`.

- [x] **Step 2: `RequiereSupertecnico` — implementación**

`src/modules/taller/rutas.tsx` — imports:

```tsx
import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { MSG_SIN_PERMISOS } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin, esAdminOSuperTecnico, esSuperTecnico, type Sesion } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'
```
y, al final del fichero:

```tsx
/** La edición de una reparación ya hecha es solo del supertécnico (el servidor exige el mismo rol). Quien llegue por URL
 *  sin serlo recibe el aviso genérico y vuelve a la lista de la que cuelga la ruta: se quita el tramo "/editar/<idRep>". */
export function RequiereSupertecnico() {
  const { sesion } = useSession()
  const { mostrarError } = useAlerta()
  const { pathname } = useLocation()
  const permitido = esSuperTecnico(sesion)
  useEffect(() => {
    if (!permitido) mostrarError(MSG_SIN_PERMISOS)
  }, [permitido, mostrarError])
  if (!permitido) return <Navigate to={pathname.replace(/\/editar\/[^/]+\/?$/, '')} replace />
  return <Outlet />
}
```

```bash
npx vitest run src/modules/taller/rutas.test.tsx
```
Esperado: PASS.

- [x] **Step 3: `useGuardado` en edición — test (falla)**

Añadir al final de `src/modules/taller/formulario/useGuardado.test.tsx` (imports a fusionar con los existentes):

```tsx
import { useReducer } from 'react'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { agrupados, detalleEdicion } from '../test/fabrica'
import { estadoInicial, reducir, textoBotonGuardar, zonaGuardarVisible, type DatosEditar } from './estado'
import { conRegistro } from './test/handlers'
import { useGuardado } from './useGuardado'

const DATOS_EDITAR: DatosEditar = { modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [], accionesYaReparadas: [] }

/** Arnés mínimo: el reductor real, el hook y botones que despachan lo que en la vista hacen las filas. */
function ArnesEdicion({ datos, onGuardado }: { datos: DatosEditar; onGuardado: () => void }) {
  const [estado, dispatch] = useReducer(reducir, datos, estadoInicial)
  const { pulsarGuardar } = useGuardado({ estado, dispatch, onGuardado })
  const ultima = estado.otros[estado.otros.length - 1]
  return (
    <>
      <button onClick={() => dispatch({ tipo: 'SUMAR', prefijo: 'bat' })}>sumar bat</button>
      <button onClick={() => dispatch({ tipo: 'SUMAR', prefijo: 'cam' })}>sumar cam</button>
      <button onClick={() => dispatch({ tipo: 'SUMAR', prefijo: 'mc' })}>sumar mc</button>
      <button onClick={() => dispatch({ tipo: 'ANADIR_ACCION' })}>añadir acción</button>
      <button onClick={() => ultima && dispatch({ tipo: 'ESCRIBIR_ACCION', id: ultima.id, texto: 'Limpieza interna' })}>escribir acción</button>
      {zonaGuardarVisible(estado) && <button data-testid="guardar" onClick={pulsarGuardar}>{textoBotonGuardar(estado)}</button>}
    </>
  )
}
function montarEdicion(datos: DatosEditar = DATOS_EDITAR) {
  const registro = conRegistro()
  server.use(...registro.handlers)
  const onGuardado = vi.fn()
  renderConProviders(<ArnesEdicion datos={datos} onGuardado={onGuardado} />, { sesion: SESION_SUPER })
  return { ...registro, onGuardado }
}
// `pulsar(nombre)` ya existe en este fichero desde la Task 15 (clic en el botón con ese nombre): se reutiliza, no se redeclara.

describe('useGuardado — "Guardar cambios" (modo edición)', () => {
  it('"Guardar cambios" → "✓  Confirmar terminar" → PUT y, con filas y acciones nuevas, dos POST completa en ese orden con el idTec original', async () => {
    const { llamadas, onGuardado } = montarEdicion()
    await pulsar('sumar bat')
    await pulsar('sumar cam')
    await pulsar('añadir acción')
    await pulsar('escribir acción')
    expect(screen.getByTestId('guardar').textContent).toBe('Guardar cambios')
    await userEvent.click(screen.getByTestId('guardar'))
    expect(screen.getByTestId('guardar').textContent).toBe('✓  Confirmar terminar')
    expect(llamadas).toHaveLength(0)
    await userEvent.click(screen.getByTestId('guardar'))
    await waitFor(() => expect(onGuardado).toHaveBeenCalledTimes(1))
    // `conRegistro` anota `metodo` en mayúsculas y `ruta` = pathname.
    expect(llamadas.map((l) => `${l.metodo} ${l.ruta}`)).toEqual([
      'PUT /api/reparaciones/R20260916_5',
      'POST /api/reparaciones/completa',
      'POST /api/reparaciones/completa',
    ])
    expect(llamadas[0].cuerpo).toEqual({ idComNuevo: 101, esReutilizadoNuevo: false, observacionNueva: null, nNuevas: 2, updatedAt: '2026-09-16T07:02:00' })
    // Sesión: supertécnico con idTec 3. El trabajo nuevo va a nombre del técnico ORIGINAL (4), sin asignación ni incidencia.
    expect(llamadas[1].cuerpo).toMatchObject({ imei: '355400000000111', idTec: 4, idAsignacion: null, idRepAnterior: null, categoria: null, filas: [{ idCom: 121, cantidad: 1, reutilizado: false, prefijo: 'cam', esSolicitud: false }] })
    expect(llamadas[2].cuerpo).toMatchObject({ imei: '355400000000111', idTec: 4, idAsignacion: null, idRepAnterior: null, categoria: null, filas: [{ idCom: 161, cantidad: 0, prefijo: 'otro', observacion: 'Limpieza interna' }] })
  })

  it('edición de una G…: completa lleva categoria "G"', async () => {
    const { llamadas, onGuardado } = montarEdicion({ ...DATOS_EDITAR, idRep: 'G20260916_3', detalle: detalleEdicion({ idCom: 141 }) })
    await pulsar('sumar mc')
    await userEvent.click(screen.getByTestId('guardar'))
    await userEvent.click(screen.getByTestId('guardar'))
    await waitFor(() => expect(onGuardado).toHaveBeenCalledTimes(1))
    expect(llamadas).toHaveLength(1)
    expect(llamadas[0].cuerpo).toMatchObject({ categoria: 'G', idTec: 4, idAsignacion: null, filas: [{ idCom: 151, prefijo: 'mc' }] })
  })

  it('409: aviso literal de dos líneas, no se cierra, y hacen falta otros dos clics con el texto aún en "✓  Confirmar terminar"', async () => {
    const { llamadas, onGuardado } = montarEdicion()
    server.use(http.put('*/api/reparaciones/R20260916_5', () => HttpResponse.json({ message: 'El registro fue modificado por otro usuario' }, { status: 409 })))
    await pulsar('sumar bat')
    await userEvent.click(screen.getByTestId('guardar'))
    await userEvent.click(screen.getByTestId('guardar'))
    const aviso = await screen.findByRole('dialog', { name: 'Error' })
    expect(within(aviso).getByText(/otro usuario modificó/).textContent).toBe('No se pudo guardar: otro usuario modificó esta reparación.\nCierra y vuelve a abrir el formulario para ver los cambios actuales.')
    expect(onGuardado).not.toHaveBeenCalled()
    expect(llamadas).toHaveLength(0)
    await userEvent.click(within(aviso).getByRole('button', { name: 'Aceptar' }))
    expect(screen.getByTestId('guardar').textContent).toBe('✓  Confirmar terminar')
    server.use(http.put('*/api/reparaciones/R20260916_5', () => new HttpResponse(null, { status: 204 })))
    await userEvent.click(screen.getByTestId('guardar'))
    expect(onGuardado).not.toHaveBeenCalled()
    await userEvent.click(screen.getByTestId('guardar'))
    await waitFor(() => expect(onGuardado).toHaveBeenCalledTimes(1))
  })

  it('otro error: "No se pudo guardar: <mensaje>"; lo ya hecho no se deshace y los pasos siguientes no se ejecutan', async () => {
    const { llamadas, onGuardado } = montarEdicion()
    server.use(http.post('*/api/reparaciones/completa', () => HttpResponse.json({ message: 'Stock insuficiente para cami13' }, { status: 422 })))
    await pulsar('sumar bat')
    await pulsar('sumar cam')
    await pulsar('añadir acción')
    await pulsar('escribir acción')
    await userEvent.click(screen.getByTestId('guardar'))
    await userEvent.click(screen.getByTestId('guardar'))
    const aviso = await screen.findByRole('dialog', { name: 'Error' })
    expect(within(aviso).getByText(/No se pudo guardar/).textContent).toBe('No se pudo guardar: Stock insuficiente para cami13')
    // El PUT (paso 1) llegó al registro; el completa que falló lo atendió el handler de arriba y el de acciones no salió.
    expect(llamadas.map((l) => `${l.metodo} ${l.ruta}`)).toEqual(['PUT /api/reparaciones/R20260916_5'])
    expect(onGuardado).not.toHaveBeenCalled()
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/useGuardado.test.tsx
```
Esperado: FAIL — en modo `editar` el segundo clic no envía ningún `PUT` (la rama de edición no existe).

- [x] **Step 4: `useGuardado` — rama de edición**

`src/modules/taller/formulario/useGuardado.ts` (tal como lo dejó la Task 15: mutaciones `guardarFilaMut`, `agotarMut`, `completaMut`; función `avisar(e, literal)`, que calla si el error ya lo gestiona el shell; `terminar(idAsignacion)`, que despacha ella misma `INICIO_GUARDADO`; y `pulsarGuardar`, cuyo segundo clic en modo `editar` aún no hace nada):

1. Imports: `useEditarReparacion` se añade a los de `./api` (`import { useAgotarComponente, useCompleta, useEditarReparacion, useGuardarFila } from './api'`) y `planGuardarCambios` a los de `./estado`. `StaleDataError`, `esErrorGestionadoGlobalmente` y `mensajeDeError` ya están importados.

2. Constante de módulo (debajo de `fechaGuardado`):

```ts
/** Literal del cliente de referencia para el bloqueo optimista en edición. El formulario NO se recarga: solo avisa. */
const MSG_409_EDICION = 'No se pudo guardar: otro usuario modificó esta reparación.\nCierra y vuelve a abrir el formulario para ver los cambios actuales.'
```

3. Dentro del hook, junto a las demás mutaciones: `const editarMut = useEditarReparacion()`. Y esta función, **completa**, debajo de `terminar`:

```ts
  /** "Guardar cambios", en el orden de la referencia: (0) acción editada, (1) fila editada, (2) filas nuevas, (3) acciones
   *  nuevas, (4) cerrar. Lo ya hecho no se deshace si un paso posterior falla. Los cuerpos salen de planGuardarCambios:
   *  las filas y acciones nuevas conservan el técnico ORIGINAL (idTec del detalle) y van sin idAsignacion. */
  async function guardarCambios(idRep: string) {
    dispatch({ tipo: 'INICIO_GUARDADO' })
    const plan = planGuardarCambios(estado)
    try {
      if (plan.editarAccion) await editarMut.mutateAsync({ idRep, cuerpo: plan.editarAccion })
      if (plan.editarFila) await editarMut.mutateAsync({ idRep, cuerpo: plan.editarFila })
      if (plan.completaFilas) await completaMut.mutateAsync(plan.completaFilas)
      if (plan.completaAcciones) await completaMut.mutateAsync(plan.completaAcciones)
    } catch (e) {
      // clics = 0 y enCurso = false; el texto sigue en "✓  Confirmar terminar" y hacen falta otros dos clics.
      dispatch({ tipo: 'FALLO_GUARDADO' })
      avisar(e, e instanceof StaleDataError ? MSG_409_EDICION : `No se pudo guardar: ${mensajeDeError(e)}`)
      return
    }
    dispatch({ tipo: 'GUARDADO_COMPLETADO' })
    try {
      await antesDeCerrar?.()
    } catch {
      // En edición no hay borrador que descartar; se mantiene la misma tolerancia que en "Terminar asignación".
    }
    onGuardado()
  }
```

4. En `pulsarGuardar`, sustituir las tres últimas líneas del cuerpo (el comentario `// "Guardar cambios" (modo editar) se ejecuta…`, el `if (estado.modo === 'editar' || estado.idAsignacion === null) return` y el `void terminar(estado.idAsignacion)`) por:

```ts
    if (estado.modo === 'editar') {
      if (estado.edicion !== null) void guardarCambios(estado.edicion.idRep)
      return
    }
    if (estado.idAsignacion === null) return
    void terminar(estado.idAsignacion)
```
El primer clic (`PEDIR_CONFIRMACION_GUARDAR`) y la guarda `if (estado.guardado.enCurso) return` no cambian.

```bash
npx vitest run src/modules/taller/formulario/useGuardado.test.tsx
```
Esperado: PASS.

- [x] **Step 5: `FilaComponente` en edición — test (falla)**

Añadir al final de `src/modules/taller/formulario/FilaComponente.test.tsx` (imports a fusionar):

```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders } from '@/test/render'
import { agrupados, componente, detalleEdicion } from '../test/fabrica'
import { estadoInicial, reducir, type DatosEditar, type EstadoFormulario } from './estado'
import { FilaComponente } from './FilaComponente'

function estadoEditar(parcial: Partial<DatosEditar> = {}): EstadoFormulario {
  return estadoInicial({ modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [111], accionesYaReparadas: [], ...parcial })
}
function pintarFila(estado: EstadoFormulario, prefijo: string) {
  const fila = estado.filas.find((f) => f.prefijo === prefijo)!
  return renderConProviders(<FilaComponente estado={estado} fila={fila} dispatch={vi.fn()} onGuardarFila={vi.fn()} />)
}

describe('FilaComponente — modo edición', () => {
  it('fila editada en azul, con SKU, cantidad y previsión "5 → 5" en gris; sin botón derecho', () => {
    pintarFila(estadoEditar(), 'bat')
    const fila = screen.getByTestId('fila-bat')
    expect(fila).toHaveAttribute('data-estado', 'editada')
    expect(fila).toHaveClass('border-b', 'bg-fila-edicion-bg', 'border-fila-edicion-brd')
    expect(screen.getByRole('combobox', { name: 'SKU de Batería' })).toHaveTextContent('bati13')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('1')
    expect(screen.getByTestId('stock-bat').textContent).toBe('5 → 5')
    expect(screen.getByTestId('stock-bat')).toHaveClass('font-bold', 'text-azul-gris')
    expect(screen.getByRole('checkbox', { name: 'Reutilizado Batería' })).toBeDisabled()
    expect(screen.queryByText('✓ Guardar fila')).not.toBeInTheDocument()
  })

  it('previsión "5 → 4" en rojo al sumar y "5 → 6" en verde al restar', () => {
    const mas = reducir(estadoEditar(), { tipo: 'SUMAR', prefijo: 'bat' })
    const primera = pintarFila(mas, 'bat')
    expect(screen.getByTestId('stock-bat').textContent).toBe('5 → 4')
    expect(screen.getByTestId('stock-bat')).toHaveClass('text-rojo-cancelar')
    primera.unmount()
    pintarFila(reducir(estadoEditar(), { tipo: 'RESTAR', prefijo: 'bat' }), 'bat')
    expect(screen.getByTestId('stock-bat').textContent).toBe('5 → 6')
    expect(screen.getByTestId('stock-bat')).toHaveClass('text-verde-ok')
  })

  it('cambio inválido (cantidad 0 sin Reutilizado): contador en rojo y negrita', () => {
    pintarFila(reducir(estadoEditar(), { tipo: 'RESTAR', prefijo: 'bat' }), 'bat')
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('0')
    expect(screen.getByTestId('contador-bat')).toHaveClass('text-rojo-cancelar', 'font-bold')
  })

  it('fila ya reparada: verde, todo deshabilitado y "✓  Ya reparado" al final', () => {
    pintarFila(estadoEditar(), 'lcd')
    const fila = screen.getByTestId('fila-lcd')
    expect(fila).toHaveAttribute('data-estado', 'yaReparado')
    expect(fila).toHaveClass('border-b', 'bg-fila-reparado-bg', 'border-fila-reparado-brd')
    expect(screen.getByTestId('boton-derecho-lcd').textContent).toBe('✓  Ya reparado')
    expect(screen.getByTestId('boton-derecho-lcd')).toHaveClass('text-[11px]', 'font-bold', 'text-verde-ok')
    expect(screen.getByRole('button', { name: 'Sumar Pantalla' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Restar Pantalla' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: 'SKU de Pantalla' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Reutilizado Pantalla' })).toBeDisabled()
  })

  it('fila nueva activa en edición: fondo normal, sin "✓ Guardar fila"; con el SKU a 0, "+" deshabilitado', () => {
    const activa = pintarFila(reducir(estadoEditar(), { tipo: 'SUMAR', prefijo: 'cam' }), 'cam')
    expect(screen.getByTestId('fila-cam')).toHaveAttribute('data-estado', 'normal')
    expect(screen.getByTestId('contador-cam')).toHaveTextContent('1')
    expect(screen.queryByText('✓ Guardar fila')).not.toBeInTheDocument()
    expect(screen.getByTestId('stock-cam').textContent).toBe('4')
    activa.unmount()
    const sinStock = { ...agrupados(), cam: [componente({ idCom: 121, tipo: 'cami13', stock: 0, stockMinimo: 1 })] }
    pintarFila(estadoEditar({ agrupados: sinStock }), 'cam')
    expect(screen.getByRole('button', { name: 'Sumar Cámara' })).toBeDisabled()
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/FilaComponente.test.tsx
```
Esperado: FAIL — el stock pinta `5` en vez de `5 → 5`, el contador inválido no sale en rojo y no existe `boton-derecho-lcd` con "✓  Ya reparado" (el `data-estado` y el fondo de `editada`/`yaReparado` ya los pinta la Task 13: esas aserciones pasan).

- [x] **Step 6: `FilaComponente` — pintado de edición**

`src/modules/taller/formulario/FilaComponente.tsx` (el de las Tasks 13–14). El `data-estado` y el fondo por estado **ya están**: `estadoDeFila()` devuelve `'editada'` / `'yaReparado'` según `fila.rol` y `CLASES_ESTADO` tiene sus clases (`bg-fila-edicion-bg border-fila-edicion-brd`, `bg-fila-reparado-bg border-fila-reparado-brd`); no se tocan. Imports a añadir a los de `./estado`: `filaEditadaInvalida`, `previsionStock`, `type PrevisionStock`. Tres cambios:

1. **Contador.** Dentro de `FilaComponente`, debajo de `const stock = stockDe(fila)`, añadir:

```tsx
  // Edición: un cambio que deja la fila editada a 0 sin "Reutilizado" no se puede guardar → contador en rojo y negrita.
  const invalida = fila.rol === 'editada' && filaEditadaInvalida(estado)
  const prevision = previsionStock(estado, fila)
```
y en el `className` del `<span data-testid={`contador-${prefijo}`}>` sustituir la expresión `fila.cantidad > 0 ? 'text-texto-incidencia' : 'text-gris-borde'` por:

```tsx
invalida ? 'font-bold text-rojo-cancelar' : fila.cantidad > 0 ? 'text-texto-incidencia' : 'text-gris-borde'
```
(`cn` usa tailwind-merge: `font-bold` gana al `font-normal` de las clases comunes.)

2. **Stock.** A nivel de módulo, debajo de `CLASE_ETIQUETA`:

```tsx
/** Previsión de stock de la fila editada ("5 → 4"): rojo si baja, verde si sube, gris si queda igual. */
const COLOR_TENDENCIA: Record<PrevisionStock['tendencia'], string> = { baja: 'text-rojo-cancelar', sube: 'text-verde-ok', igual: 'text-azul-gris' }
```
y la celda `<span data-testid={`stock-${prefijo}`} …>{stock ?? '—'}</span>` pasa a ser:

```tsx
        <span data-testid={`stock-${prefijo}`} className={cn('w-[70px] shrink-0 px-2.5 text-center text-[12px]', prevision !== null && ['font-bold', COLOR_TENDENCIA[prevision.tendencia]])}>
          {prevision !== null ? prevision.texto : (stock ?? '—')}
        </span>
```

3. **Botón derecho.** En el `switch` de `BotonDerechoFila`, entre el `case 'recibido'` (Task 14) y el `default`, añadir la rama `yaReparado` (una etiqueta, no un botón; hasta esta tarea caía en el `default` y no pintaba nada):

```tsx
    case 'yaReparado':
      // whitespace-pre conserva los dos espacios del literal; entre llaves para que el formateador no los toque.
      return <span data-testid={testid} className="px-2.5 text-[11px] font-bold whitespace-pre text-verde-ok">{'✓  Ya reparado'}</span>
```

Lo demás de la edición ya lo decide el estado (Task 8) y la vista no hace nada: `botonDerecho` devuelve `ninguno` en la fila editada y en las filas nuevas (no hay "✓ Guardar fila"), `fila.controles` trae deshabilitado lo que toca en `yaReparado`, y `subFila` es siempre `oculta`.

```bash
npx vitest run src/modules/taller/formulario/FilaComponente.test.tsx
```
Esperado: PASS.

- [x] **Step 7: `OtrasAcciones` en edición — test (falla)**

Añadir al final de `src/modules/taller/formulario/OtrasAcciones.test.tsx` (imports a fusionar):

```tsx
import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders } from '@/test/render'
import { agrupados, detalleEdicion } from '../test/fabrica'
import { estadoInicial, reducir, type DatosEditar, type EstadoFormulario } from './estado'
import { OtrasAcciones } from './OtrasAcciones'

function estadoEdicion(parcial: Partial<DatosEditar> = {}): EstadoFormulario {
  return estadoInicial({ modo: 'editar', idRep: 'R20260916_5', detalle: detalleEdicion(), agrupados: agrupados(), yaReparados: [], accionesYaReparadas: ['Limpieza de conector'], ...parcial })
}
const pintarAcciones = (estado: EstadoFormulario) => renderConProviders(<OtrasAcciones estado={estado} dispatch={vi.fn()} onGuardarAccion={vi.fn()} />)
const lineaDe = (valor: string) => screen.getByDisplayValue(valor).closest('[data-testid^="accion-"]') as HTMLElement

describe('OtrasAcciones — modo edición', () => {
  it('acción ya reparada: campo deshabilitado, "✓ Ya reparada", sin papelera ni "✓ Guardar", y cuenta en el badge', () => {
    pintarAcciones(estadoEdicion())
    const linea = lineaDe('Limpieza de conector')
    expect(within(linea).getByRole('textbox')).toBeDisabled()
    expect(within(linea).getByText('✓ Ya reparada')).toHaveClass('text-[11px]', 'font-bold', 'text-recibido-text')
    expect(within(linea).queryByRole('button', { name: 'Quitar acción' })).not.toBeInTheDocument()
    expect(within(linea).queryByRole('button', { name: '✓ Guardar' })).not.toBeInTheDocument()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('1')
  })

  it('editar una acción "otro": línea precargada y editable, sin papelera ni "✓ Guardar"; ninguna fila está en edición', () => {
    const estado = estadoEdicion({ detalle: detalleEdicion({ idCom: 161, cantidad: 0, observacion: 'Cambio de tornillos' }), accionesYaReparadas: [] })
    expect(estado.filas.some((f) => f.rol === 'editada')).toBe(false)
    pintarAcciones(estado)
    const linea = lineaDe('Cambio de tornillos')
    expect(within(linea).getByRole('textbox')).toBeEnabled()
    expect(within(linea).queryByRole('button', { name: 'Quitar acción' })).not.toBeInTheDocument()
    expect(within(linea).queryByRole('button', { name: '✓ Guardar' })).not.toBeInTheDocument()
  })

  it('acción nueva en edición: con papelera y sin "✓ Guardar" (se guarda con "Guardar cambios")', () => {
    let estado = reducir(estadoEdicion({ accionesYaReparadas: [] }), { tipo: 'ANADIR_ACCION' })
    estado = reducir(estado, { tipo: 'ESCRIBIR_ACCION', id: estado.otros[estado.otros.length - 1].id, texto: 'Limpieza interna' })
    pintarAcciones(estado)
    const linea = lineaDe('Limpieza interna')
    expect(within(linea).getByRole('button', { name: 'Quitar acción' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '✓ Guardar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '✓ Confirmar' })).not.toBeInTheDocument()
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/OtrasAcciones.test.tsx
```
Esperado: FAIL — la línea `yaReparada` no muestra "✓ Ya reparada" y las líneas de edición siguen pintando "✓ Guardar".

- [x] **Step 8: `OtrasAcciones` — reglas de la línea en edición**

`src/modules/taller/formulario/OtrasAcciones.tsx` (el de la Task 15). Dentro de `OtrasAcciones`, debajo de `const total = estado.otros.length`, añadir:

```tsx
  // En edición las líneas no tienen "✓ Guardar": las acciones se guardan con "Guardar cambios".
  const enEdicion = estado.modo === 'editar'
```
y sustituir el cuerpo del `estado.otros.map((accion, i) => ( … ))` (el `<div data-testid={`accion-${i + 1}`}>` entero) por:

```tsx
          {estado.otros.map((accion, i) => {
            const yaReparada = accion.origen === 'yaReparada'
            const bloqueada = accion.guardada !== null || yaReparada
            return (
              <div key={accion.id} data-testid={`accion-${i + 1}`} className="flex items-center gap-[5px]">
                <input
                  ref={i === total - 1 ? ultimoCampo : undefined}
                  aria-label={`Descripción de la acción ${i + 1}`}
                  value={accion.texto}
                  // Guardada o ya reparada: solo lectura. Mientras se guarda tampoco se escribe. La acción EDITADA sí es editable.
                  disabled={bloqueada || accion.guardando}
                  onChange={(e) => dispatch({ tipo: 'ESCRIBIR_ACCION', id: accion.id, texto: e.target.value })}
                  placeholder="Describe la acción"
                  className="min-w-0 flex-1 rounded border border-fila-sep bg-superficie px-2 py-1 text-[12px] disabled:opacity-60"
                />
                {accion.guardada !== null && <span className="shrink-0 px-1 text-[11px] font-bold text-recibido-text">{`✓ Guardada ${accion.guardada.fecha}`}</span>}
                {yaReparada && <span className="shrink-0 px-1 text-[11px] font-bold text-recibido-text">✓ Ya reparada</span>}
                {!bloqueada && !enEdicion && (
                  <button
                    type="button"
                    disabled={accion.texto.trim() === '' || accion.guardando}
                    onClick={() => onGuardarAccion(accion.id)}
                    className="shrink-0 cursor-pointer rounded bg-azul-noche px-2.5 py-1 text-[11px] font-bold text-superficie disabled:cursor-default disabled:opacity-50"
                  >
                    {accion.confirmando ? '✓ Confirmar' : '✓ Guardar'}
                  </button>
                )}
                {/* La papelera solo existe en las líneas nuevas sin guardar: la acción editada no se puede quitar. */}
                {!bloqueada && accion.origen === 'nueva' && (
                  <button type="button" aria-label="Quitar acción" onClick={() => dispatch({ tipo: 'QUITAR_ACCION', id: accion.id })} className="shrink-0 cursor-pointer bg-transparent px-1 py-0.5">
                    <img src="/borrar.png" alt="" className="h-[18px] w-[18px]" />
                  </button>
                )}
              </div>
            )
          })}
```
El texto del botón sigue saliendo del estado (`accion.confirmando`): tras un fallo el reductor deja `confirmando = true` y `pideOtroClic = true`, el botón sigue diciendo "✓ Confirmar" y es `useGuardado.guardarAccion` quien, con `accionPideConfirmacion(accion)`, vuelve a pedir la confirmación.

El badge no se toca: `contadorAcciones` ya cuenta las `yaReparada`.

```bash
npx vitest run src/modules/taller/formulario/OtrasAcciones.test.tsx
```
Esperado: PASS.

- [x] **Step 9: Formulario en modo edición y "Salir sin guardar" — tests (fallan)**

**9a.** Añadir al final de `src/modules/taller/formulario/FormularioReparacion.test.tsx` (imports a fusionar: `type RequestHandler` de `msw`; `renderConRouter`, `SESION_SUPER` de `@/test/render`; `componente`, `detalleEdicion`, `agrupados` de `../test/fabrica`). El modo edición usa `useBlocker` (dentro de `GuardiaSalida`, que solo se monta en edición), que exige data router: estos tests montan con `renderConRouter`; los del flujo nuevo y glass pueden seguir con `renderConProviders`. `botonZona()` y `borradorEnReposo` ya están en el fichero (Tasks 15 y 16).

```tsx
/** `extra`: handlers que deben ganar a los del escenario. Van en una SEGUNDA llamada a `server.use` (en una misma llamada gana
 *  el primero de la lista; cada llamada nueva se antepone a las anteriores) y ANTES de montar (la carga sale en el primer efecto). */
function abrirEditar(escenario: EscenarioFormulario = {}, idRep = 'R20260916_5', extra: RequestHandler[] = []) {
  const registro = conRegistro({ detalle: detalleEdicion(), yaReparados: [111], acciones: ['Limpieza de conector'], ...escenario })
  server.use(...registro.handlers)
  if (extra.length > 0) server.use(...extra)
  const onCerrar = vi.fn()
  const vista = renderConRouter([{ path: '/editar', element: <FormularioReparacion modo="editar" idRep={idRep} onCerrar={onCerrar} /> }], { sesion: SESION_SUPER, ruta: '/editar' })
  return { ...vista, ...registro, onCerrar }
}

describe('FormularioReparacion — modo edición', () => {
  it('title "Editar reparación — <idRep>", etiqueta "IMEI: …  ·  Editando <idRep>", modelo bloqueado y sin bandas ni borrador', async () => {
    const { llamadas } = abrirEditar()
    expect(await screen.findByRole('dialog', { name: 'Editar reparación — R20260916_5' })).toBeInTheDocument()
    expect(document.title).toBe('Editar reparación — R20260916_5')
    expect(screen.getByText(/^IMEI:/).textContent).toBe('IMEI: 355400000000111  ·  Editando R20260916_5')
    expect(screen.getByRole('combobox', { name: 'Filtrar por modelo' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: 'Filtrar por modelo' })).toHaveTextContent('iPhone 13')
    expect(screen.queryByTestId('banda-conflicto')).not.toBeInTheDocument()
    expect(screen.queryByTestId('banda-incidencia')).not.toBeInTheDocument()
    expect(screen.queryByTestId('banda-borrador')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await borradorEnReposo()
    expect(llamadas.filter((l) => l.ruta.endsWith('/borrador'))).toHaveLength(0)
  })

  it('fila editada en azul con previsión "5 → 4" en rojo; fila ya reparada con "✓  Ya reparado"; acción ya reparada con "✓ Ya reparada"', async () => {
    abrirEditar()
    expect(await screen.findByTestId('fila-bat')).toHaveAttribute('data-estado', 'editada')
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Batería' }))
    expect(screen.getByTestId('stock-bat').textContent).toBe('5 → 4')
    expect(screen.getByTestId('stock-bat')).toHaveClass('text-rojo-cancelar')
    expect(screen.getByTestId('fila-lcd')).toHaveAttribute('data-estado', 'yaReparado')
    expect(screen.getByTestId('boton-derecho-lcd').textContent).toBe('✓  Ya reparado')
    expect(within(screen.getByTestId('otras-acciones')).getByText('✓ Ya reparada')).toBeInTheDocument()
    expect(screen.getByTestId('otras-acciones-badge')).toHaveTextContent('1')
  })

  it('cambio inválido: contador en rojo y sin zona de guardar; cambio válido: "Guardar cambios"', async () => {
    abrirEditar()
    await screen.findByTestId('fila-bat')
    expect(screen.queryByTestId('zona-guardar')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Restar Batería' }))
    expect(screen.getByTestId('contador-bat')).toHaveClass('text-rojo-cancelar', 'font-bold')
    expect(screen.queryByTestId('zona-guardar')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Batería' }))
    expect(botonZona().textContent).toBe('Guardar cambios')
  })

  it('sin sub-fila de agotado aunque el SKU esté a 0, y sin "✓ Guardar fila" en una fila nueva activa', async () => {
    abrirEditar({ agrupados: { ...agrupados(), cam: [componente({ idCom: 121, tipo: 'cami13', stock: 0, stockMinimo: 1 })] } })
    await screen.findByTestId('fila-cam')
    expect(screen.queryByTestId('subfila-cam')).not.toBeInTheDocument()
    expect(screen.queryByText('Solicitar pieza')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sumar Cámara' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Chasis' }))
    expect(screen.queryByText('✓ Guardar fila')).not.toBeInTheDocument()
    expect(screen.queryByTestId('subfila-cha')).not.toBeInTheDocument()
  })

  it('editar acción: "Editando acción <idRep>", línea precargada sin papelera ni "✓ Guardar"; texto vacío oculta la zona', async () => {
    abrirEditar({ detalle: detalleEdicion({ idCom: 161, cantidad: 0, observacion: 'Cambio de tornillos' }), yaReparados: [], acciones: [] })
    const campo = await screen.findByDisplayValue('Cambio de tornillos')
    expect(screen.getByText(/^IMEI:/).textContent).toBe('IMEI: 355400000000111  ·  Editando acción R20260916_5')
    expect(screen.getAllByTestId(/^fila-/).every((f) => f.getAttribute('data-estado') !== 'editada')).toBe(true)
    expect(screen.queryByRole('button', { name: 'Quitar acción' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '✓ Guardar' })).not.toBeInTheDocument()
    await userEvent.type(campo, ' nuevos')
    expect(botonZona().textContent).toBe('Guardar cambios')
    await userEvent.clear(campo)
    expect(screen.queryByTestId('zona-guardar')).not.toBeInTheDocument()
  })

  it('"Guardar cambios" → "✓  Confirmar terminar" → PUT, completa de filas y completa de acciones con el idTec original; cierra', async () => {
    const { llamadas, onCerrar } = abrirEditar()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sumar Cámara' }))
    await userEvent.click(screen.getByRole('button', { name: '+ Añadir acción' }))
    // "+ Añadir acción" deja el foco en la línea nueva: se escribe directamente.
    await userEvent.keyboard('Limpieza interna')
    await userEvent.click(botonZona())
    expect(botonZona().textContent).toBe('✓  Confirmar terminar')
    await userEvent.click(botonZona())
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(llamadas.map((l) => `${l.metodo} ${l.ruta}`)).toEqual(['PUT /api/reparaciones/R20260916_5', 'POST /api/reparaciones/completa', 'POST /api/reparaciones/completa'])
    expect(llamadas[0].cuerpo).toMatchObject({ idComNuevo: 101, nNuevas: 2, esReutilizadoNuevo: false, updatedAt: '2026-09-16T07:02:00' })
    expect(llamadas[1].cuerpo).toMatchObject({ idTec: 4, idAsignacion: null, idRepAnterior: null, filas: [{ idCom: 121, cantidad: 1 }] })
    expect(llamadas[2].cuerpo).toMatchObject({ idTec: 4, idAsignacion: null, filas: [{ idCom: 161, cantidad: 0, prefijo: 'otro', observacion: 'Limpieza interna' }] })
  })

  it('409: aviso de dos líneas y el formulario sigue abierto sin recargar', async () => {
    let cargasDetalle = 0
    const { onCerrar } = abrirEditar({}, 'R20260916_5', [
      http.get('*/api/reparaciones/R20260916_5/detalle-edicion', () => { cargasDetalle++; return HttpResponse.json(detalleEdicion()) }),
      http.put('*/api/reparaciones/R20260916_5', () => HttpResponse.json({ message: 'El registro fue modificado por otro usuario' }, { status: 409 })),
    ])
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await userEvent.click(botonZona())
    await userEvent.click(botonZona())
    const aviso = await screen.findByRole('dialog', { name: 'Error' })
    expect(within(aviso).getByText(/otro usuario modificó/).textContent).toBe('No se pudo guardar: otro usuario modificó esta reparación.\nCierra y vuelve a abrir el formulario para ver los cambios actuales.')
    await userEvent.click(within(aviso).getByRole('button', { name: 'Aceptar' }))
    expect(screen.getByRole('dialog', { name: 'Editar reparación — R20260916_5' })).toBeInTheDocument()
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('2')
    expect(botonZona().textContent).toBe('✓  Confirmar terminar')
    expect(cargasDetalle).toBe(1)
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('edición de una G…: solo filas Glass y Marco', async () => {
    abrirEditar({ detalle: detalleEdicion({ idCom: 141 }), yaReparados: [], acciones: [] }, 'G20260916_3')
    expect(await screen.findByTestId('fila-g')).toHaveAttribute('data-estado', 'editada')
    expect(screen.getAllByTestId(/^fila-/).map((f) => f.getAttribute('data-testid'))).toEqual(['fila-g', 'fila-mc'])
  })

  it('fallo de carga (403 del detalle): aviso genérico y cierre', async () => {
    const { onCerrar } = abrirEditar({}, 'R20260916_5', [http.get('*/api/reparaciones/R20260916_5/detalle-edicion', () => new HttpResponse(null, { status: 403 }))])
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
    await waitFor(() => expect(onCerrar).toHaveBeenCalled())
  })

  it('el detalle llega vacío (la reparación ya no existe): el formulario queda sin contenido, aviso del shell y cierre', async () => {
    // `cargarEditar` convierte un detalle-edicion sin cuerpo en NoEncontradoError (Task 11): se trata como cualquier fallo de carga.
    const { onCerrar } = abrirEditar({}, 'R20260916_5', [http.get('*/api/reparaciones/R20260916_5/detalle-edicion', () => new HttpResponse(null, { status: 200 }))])
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('Recurso no encontrado.')
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('dialog', { name: /^Editar reparación/ })).not.toBeInTheDocument()
    expect(screen.queryByTestId('fila-bat')).not.toBeInTheDocument()
  })
})
```

**9b.** Añadir al final de `src/modules/taller/formulario/rutas.test.tsx` (imports a fusionar: `act` de `@testing-library/react`; `SESION_SUPER`; `detalleEdicion` de `../test/fabrica`; `FormularioEditarRuta` de `./rutas`):

```tsx
const LISTAS_EDICION = [
  { path: '/reparaciones/historial', element: <><p>LISTA HISTORIAL</p><Outlet /></>, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="historial" /> }] },
  { path: '/reparaciones/historial/glass', element: <><p>LISTA HISTORIAL GLASS</p><Outlet /></>, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="historial-glass" /> }] },
  { path: '/reparaciones/imeis/:imei', element: <><p>DETALLE IMEI</p><Outlet /></>, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="imei" /> }] },
]
const RUTA_EDITAR = '/reparaciones/historial/editar/R20260916_5'
function abrirRutaEditar(ruta = RUTA_EDITAR) {
  server.use(...handlersFormulario({ detalle: detalleEdicion() }))
  return renderConRouter(LISTAS_EDICION, { sesion: SESION_SUPER, ruta })
}
const cerrar = () => userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))

describe('FormularioEditarRuta — cierre y "Salir sin guardar"', () => {
  it('cada origen vuelve a su lista; el de IMEIs usa el :imei de la URL, no el de la reparación', async () => {
    const historial = abrirRutaEditar()
    await screen.findByRole('dialog', { name: 'Editar reparación — R20260916_5' })
    await cerrar()
    await waitFor(() => expect(historial.router.state.location.pathname).toBe('/reparaciones/historial'))
    historial.unmount()
    const glass = abrirRutaEditar('/reparaciones/historial/glass/editar/R20260916_5')
    await screen.findByRole('dialog', { name: 'Editar reparación — R20260916_5' })
    await cerrar()
    await waitFor(() => expect(glass.router.state.location.pathname).toBe('/reparaciones/historial/glass'))
    glass.unmount()
    const imei = abrirRutaEditar('/reparaciones/imeis/359900000000999/editar/R20260916_5')
    await screen.findByRole('dialog', { name: 'Editar reparación — R20260916_5' })
    await cerrar()
    await waitFor(() => expect(imei.router.state.location.pathname).toBe('/reparaciones/imeis/359900000000999'))
  })

  it('cerrar sin cambios cierra sin preguntar y restaura el title', async () => {
    const { router } = abrirRutaEditar()
    await screen.findByTestId('fila-bat')
    await cerrar()
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/historial'))
    expect(screen.queryByRole('dialog', { name: 'Salir sin guardar' })).not.toBeInTheDocument()
    expect(document.title).not.toBe('Editar reparación — R20260916_5')
  })

  it('con cambios abre "Salir sin guardar"; "Cancelar" vuelve al formulario y "Salir sin guardar" cierra', async () => {
    const { router } = abrirRutaEditar()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await cerrar()
    const dlg = await screen.findByRole('dialog', { name: 'Salir sin guardar' })
    expect(within(dlg).getByText('Tienes cambios sin guardar que se perderán si cierras el formulario.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(RUTA_EDITAR)
    await userEvent.click(within(dlg).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog', { name: 'Salir sin guardar' })).not.toBeInTheDocument()
    expect(screen.getByTestId('contador-bat')).toHaveTextContent('2')
    await cerrar()
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Salir sin guardar' })).getByRole('button', { name: 'Salir sin guardar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/historial'))
    expect(screen.queryByTestId('fila-bat')).not.toBeInTheDocument()
  })

  it('Escape con cambios también pregunta', async () => {
    abrirRutaEditar()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await userEvent.keyboard('{Escape}')
    expect(await screen.findByRole('dialog', { name: 'Salir sin guardar' })).toBeInTheDocument()
  })

  it('Atrás con cambios también abre el diálogo (useBlocker) y la URL no cambia hasta confirmar', async () => {
    server.use(...handlersFormulario({ detalle: detalleEdicion() }))
    const { router } = renderConRouter(LISTAS_EDICION, { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    await act(async () => { await router.navigate(RUTA_EDITAR) })
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    await act(async () => { await router.navigate(-1) })
    const dlg = await screen.findByRole('dialog', { name: 'Salir sin guardar' })
    expect(router.state.location.pathname).toBe(RUTA_EDITAR)
    await userEvent.click(within(dlg).getByRole('button', { name: 'Salir sin guardar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/historial'))
  })

  it('cerrar con cambio inválido no pregunta y el cambio se pierde', async () => {
    const { router } = abrirRutaEditar()
    await userEvent.click(await screen.findByRole('button', { name: 'Restar Batería' }))
    expect(screen.queryByTestId('zona-guardar')).not.toBeInTheDocument()
    await cerrar()
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/historial'))
    expect(screen.queryByRole('dialog', { name: 'Salir sin guardar' })).not.toBeInTheDocument()
  })

  it('tras "Guardar cambios" con éxito se cierra sin preguntar', async () => {
    const { router } = abrirRutaEditar()
    await userEvent.click(await screen.findByRole('button', { name: 'Sumar Batería' }))
    const zona = () => within(screen.getByTestId('zona-guardar')).getByRole('button')
    await userEvent.click(zona())
    await userEvent.click(zona())
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/historial'))
    expect(screen.queryByRole('dialog', { name: 'Salir sin guardar' })).not.toBeInTheDocument()
  })
})
```

```bash
npx vitest run src/modules/taller/formulario/FormularioReparacion.test.tsx src/modules/taller/formulario/rutas.test.tsx
```
Esperado: FAIL — `FormularioEditarRuta` no se exporta y `FormularioReparacion` con `modo="editar"` no carga nada.

- [x] **Step 10: `DialogoSalirSinGuardar`, rama de edición del formulario y `FormularioEditarRuta`**

**10a. `src/modules/taller/formulario/DialogoSalirSinGuardar.tsx`** (nuevo, completo):

```tsx
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'

/** Calco del ConfirmDialog que la referencia abre al cerrar la edición con cambios: caja de 400 px, título rojo con ✕
 *  (equivale a "Cancelar"), acción destructiva arriba y "Cancelar" debajo, sin cuenta atrás. ConfirmDialog ya cumple
 *  medidas y colores de la ficha. */
export function DialogoSalirSinGuardar({ abierto, onSalir, onCancelar }: { abierto: boolean; onSalir: () => void; onCancelar: () => void }) {
  return (
    <ConfirmDialog
      abierto={abierto}
      titulo="Salir sin guardar"
      descripcion="Tienes cambios sin guardar que se perderán si cierras el formulario."
      textoAccion="Salir sin guardar"
      onConfirmar={onSalir}
      onCancelar={onCancelar}
    />
  )
}
```

**10b. `src/modules/taller/formulario/FormularioReparacion.tsx`.** Se conserva la partición de la Task 12 (`FormularioReparacion` elige por modo → un cargador por flujo → `FormularioCargado` con el `useReducer`) y se le añade lo que pide la edición: el cargador `FormularioEditar` (hermano de `FormularioNuevo`), la guardia `GuardiaSalida` y un `FormularioCargado` que acepta las dos cargas. Lo común a los dos cargadores (recargar la lista al desmontar; avisar y cerrar si la carga falla, incluido el `NoEncontradoError` de un detalle vacío) pasa al hook local `useCierreDeCarga`. El fichero queda así, **entero** (el JSX del diálogo no cambia respecto a las Tasks 15–16 salvo la última línea de `<DialogContent>`):

```tsx
import { useCallback, useEffect, useReducer, useRef, type RefObject } from 'react'
import { useBlocker } from 'react-router'
import type { AsignacionActiva } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { useCargaEditar, useCargaNuevo, useRecargarAlCerrar } from './api'
import { CabeceraFormulario } from './CabeceraFormulario'
import { DialogoSalirSinGuardar } from './DialogoSalirSinGuardar'
import { estadoInicial, filasVisibles, hayCambiosSinGuardar, reducir, textoConflicto, tituloPestana, type DatosEditar, type DatosNuevo } from './estado'
import { FilaComponente } from './FilaComponente'
import { OtrasAcciones } from './OtrasAcciones'
import { SubFilaAgotado } from './SubFilaAgotado'
import { useBorrador } from './useBorrador'
import { useGuardado } from './useGuardado'
import { ZonaGuardar } from './ZonaGuardar'

type Props =
  | { modo: 'nuevo' | 'glass'; idAsignacion: string; onCerrar: () => void }
  | { modo: 'editar'; idRep: string; onCerrar: () => void }

/** Lo que necesita el formulario ya cargado. `CargaNuevo` (W10) tiene esta misma forma; la edición la compone sin asignaciones
 *  activas (no hay banda de conflicto) ni borrador. */
type CargaFormulario = { datos: DatosNuevo | DatosEditar; asignacionesActivas: AsignacionActiva[]; borradorJson: string | null }

/** Cabecera de columnas: mismos anchos que las celdas de cada fila (la observación, 280 fijo). El botón derecho no tiene columna. */
const COLUMNAS = [
  { texto: 'Unit (+/-)', clase: 'w-[70px]' },
  { texto: 'Componente', clase: 'w-[100px]' },
  { texto: 'SKU', clase: 'w-[170px]' },
  { texto: 'Stock', clase: 'w-[70px] text-center' },
  { texto: '¿Reutilizado?', clase: 'w-[110px]' },
  { texto: 'Observación', clase: 'w-[280px]' },
] as const

/** El formulario de reparación como diálogo modal sobre la lista. La URL lo gobierna: quien lo monta (formulario/rutas.tsx)
 *  decide a dónde se vuelve en `onCerrar`. Sin hooks aquí: cada modo tiene su cargador (el flujo nuevo y el de glass comparten
 *  el suyo: `cargarNuevo` deduce la categoría del prefijo de la asignación). */
export function FormularioReparacion(props: Props) {
  if (props.modo === 'editar') return <FormularioEditar idRep={props.idRep} onCerrar={props.onCerrar} />
  return <FormularioNuevo idAsignacion={props.idAsignacion} onCerrar={props.onCerrar} />
}

/** Común a las dos cargas. (1) Al cerrar —✕, Escape, Atrás, "Salir sin guardar" o tras guardar— se recarga la lista de debajo:
 *  se hace al desmontar, que es lo único que tienen en común todas las salidas. (2) La carga va con meta.silenciarError: el
 *  aviso lo da esta vista y después vuelve a la lista (403 → mensaje genérico de permisos; 404 o detalle vacío → "Recurso no
 *  encontrado."); 401 y corte de conexión ya los gestiona el shell: ahí solo se cierra. Las refs evitan depender de la
 *  identidad de las funciones en cada render. */
function useCierreDeCarga(falla: boolean, error: unknown, onCerrar: () => void) {
  const { mostrarError } = useAlerta()
  const recargar = useRecargarAlCerrar()
  const recargarRef = useRef(recargar)
  const onCerrarRef = useRef(onCerrar)
  useEffect(() => {
    recargarRef.current = recargar
    onCerrarRef.current = onCerrar
  })
  useEffect(() => () => recargarRef.current(), [])
  useEffect(() => {
    if (!falla) return
    if (!esErrorGestionadoGlobalmente(error)) mostrarError(mensajeDeError(error))
    onCerrarRef.current()
  }, [falla, error, mostrarError])
}

function FormularioNuevo({ idAsignacion, onCerrar }: { idAsignacion: string; onCerrar: () => void }) {
  const carga = useCargaNuevo(idAsignacion)
  useCierreDeCarga(carga.isError, carga.error, onCerrar)
  if (!carga.data) return null
  // El reductor se monta solo con la carga terminada: `key` evita reinicializarlo con datos de otra asignación.
  return <FormularioCargado key={idAsignacion} carga={carga.data} onCerrar={onCerrar} />
}

/** Carga de la edición: detalle → agrupados + ya reparados + acciones ya reparadas. Sin banda de conflicto, incidencia,
 *  solicitudes, borrador ni consulta del modelo del teléfono. Si el detalle llega vacío (la reparación ya no existe) la carga
 *  rechaza con NoEncontradoError: el formulario no llega a pintarse, sale el aviso y se vuelve a la lista. */
function FormularioEditar({ idRep, onCerrar }: { idRep: string; onCerrar: () => void }) {
  const carga = useCargaEditar(idRep)
  useCierreDeCarga(carga.isError, carga.error, onCerrar)
  if (!carga.data) return null
  return <FormularioCargado key={idRep} carga={{ datos: carga.data, asignacionesActivas: [], borradorJson: null }} onCerrar={onCerrar} />
}

/** Todos los cierres de la edición son navegaciones (✕ y Escape navegan a la lista con replace; Atrás es un POP): un único
 *  bloqueo las cubre. Bloquea solo si la zona de guardar está visible ("hay cambios") y no se acaba de guardar. Un cambio
 *  inválido oculta la zona, así que cerrar en ese estado no pregunta y el cambio se pierde (calco de la referencia). Vive en
 *  un componente aparte que solo se monta en edición: `useBlocker` exige data router y el flujo nuevo no debe depender de él. */
function GuardiaSalida({ hayCambios, salidaLibre }: { hayCambios: boolean; salidaLibre: RefObject<boolean> }) {
  const blocker = useBlocker(() => hayCambios && !salidaLibre.current)
  return <DialogoSalirSinGuardar abierto={blocker.state === 'blocked'} onSalir={() => blocker.proceed?.()} onCancelar={() => blocker.reset?.()} />
}

function FormularioCargado({ carga, onCerrar }: { carga: CargaFormulario; onCerrar: () => void }) {
  const { sesion } = useSession()
  const [estado, dispatch] = useReducer(reducir, carga.datos, estadoInicial)
  const titulo = tituloPestana(estado)
  // En edición no hay asignación propia ni activas: textoConflicto([], …) da null y la banda no se pinta.
  const conflicto = textoConflicto(carga.asignacionesActivas, estado.idAsignacion ?? '', sesion?.idTec ?? null)
  // Borrador: solo flujo nuevo y Glass; en edición el hook queda inactivo y no llama a nada.
  const { volcarAhora, descartar } = useBorrador({ estado, dispatch, borradorJson: carga.borradorJson, activo: estado.modo !== 'editar' })
  /** ✕ y Escape. Flujo nuevo y Glass: nunca pregunta; vuelca el borrador YA y cierra sin esperar a la red. Edición: `volcarAhora`
   *  no hace nada y `onCerrar` navega; si hay cambios, GuardiaSalida intercepta esa navegación y abre "Salir sin guardar". */
  const cerrar = useCallback(() => {
    void volcarAhora()
    onCerrar()
  }, [volcarAhora, onCerrar])
  // Tras un guardado con éxito se sale sin preguntar aunque el estado siga diciendo "hay cambios".
  const salidaLibre = useRef(false)
  const alGuardar = useCallback(() => {
    salidaLibre.current = true
    onCerrar()
  }, [onCerrar])
  // Guardar de verdad no vuelca: borra el borrador (antesDeCerrar; en edición no llama a nada) y cierra con el onCerrar de las props.
  const guardado = useGuardado({ estado, dispatch, onGuardado: alGuardar, antesDeCerrar: descartar })

  // El título de la ventana del JavaFX pasa a ser el de la pestaña; al cerrar vuelve el que había.
  useEffect(() => {
    const anterior = document.title
    document.title = titulo
    return () => {
      document.title = anterior
    }
  }, [titulo])

  return (
    <Dialog open onOpenChange={(abierto) => { if (!abierto) cerrar() }}>
      {/* max-w-none y sm:max-w-none anulan el max-w y el sm:max-w-lg de DialogContent (tailwind-merge no descarta la variante
          con modificador si no se repite). Pulsar fuera no cierra: la ventana del JavaFX solo se cerraba con su ✕. */}
      <DialogContent
        aria-label={titulo}
        aria-describedby={undefined}
        showCloseButton={false}
        onInteractOutside={(e) => e.preventDefault()}
        className="flex h-[calc(100vh-48px)] min-h-[700px] w-[calc(100vw-48px)] max-w-none min-w-[960px] flex-col gap-0 overflow-hidden rounded-none border-0 bg-fondo-vista p-0 sm:max-w-none"
      >
        <DialogTitle className="sr-only">{titulo}</DialogTitle>
        <CabeceraFormulario estado={estado} conflicto={conflicto} dispatch={dispatch} onCerrar={cerrar} />
        <div className="flex border-b border-form-cabecera-brd bg-form-cabecera-bg">
          {COLUMNAS.map((c) => (
            <span key={c.texto} className={`${c.clase} shrink-0 px-2.5 py-1.5 text-[12px] text-azul-gris`}>
              {c.texto}
            </span>
          ))}
        </div>
        {/* Filas y OTRAS ACCIONES desplazan juntas; el hueco flexible empuja la zona de guardar al fondo. */}
        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
          {filasVisibles(estado) ? (
            estado.filas.map((fila) => (
              <FilaComponente key={fila.prefijo} estado={estado} fila={fila} dispatch={dispatch} onGuardarFila={guardado.guardarFila}>
                <SubFilaAgotado estado={estado} fila={fila} dispatch={dispatch} />
              </FilaComponente>
            ))
          ) : (
            <p className="py-10 text-center text-[13px] text-azul-gris">Selecciona un modelo de iPhone para continuar</p>
          )}
          <OtrasAcciones estado={estado} dispatch={dispatch} onGuardarAccion={guardado.guardarAccion} />
        </div>
        <ZonaGuardar estado={estado} onPulsar={guardado.pulsarGuardar} />
        {estado.modo === 'editar' && <GuardiaSalida hayCambios={hayCambiosSinGuardar(estado)} salidaLibre={salidaLibre} />}
      </DialogContent>
    </Dialog>
  )
}
```
`ZonaGuardar` no cambia: en edición la zona solo se ve cuando `hayCambiosSinGuardar(estado)` (Task 8) y `textoBotonGuardar` ya devuelve "Guardar cambios". `SubFilaAgotado` tampoco: `subFila` es siempre `oculta` en edición.

**10c. `src/modules/taller/formulario/rutas.tsx`** — añadir debajo de `FormularioNuevoRuta` (`useNavigate` y `useParams` ya están importados; no hace falta ningún import más):

```tsx
const LISTA_DE_ORIGEN = { historial: '/reparaciones/historial', 'historial-glass': '/reparaciones/historial/glass' } as const

/** Ruta hija `editar/:idRep` del Historial (dos pestañas) y del detalle de IMEIs. Cierra a la lista de la que cuelga, con
 *  replace (con F5 no hay entrada previa en el historial); en el detalle de IMEIs usa el :imei de la URL, no el de la
 *  reparación. La recarga de la lista de debajo la hace el propio formulario al desmontarse —✕, Escape, guardado, "Salir sin
 *  guardar" o Atrás—, igual que en el flujo nuevo (`useCierreDeCarga` en FormularioReparacion.tsx). */
export function FormularioEditarRuta({ origen }: { origen: 'historial' | 'historial-glass' | 'imei' }) {
  const { idRep = '', imei = '' } = useParams()
  const navigate = useNavigate()
  const lista = origen === 'imei' ? `/reparaciones/imeis/${imei}` : LISTA_DE_ORIGEN[origen]
  return <FormularioReparacion key={idRep} modo="editar" idRep={idRep} onCerrar={() => navigate(lista, { replace: true })} />
}
```

**10d. `src/app/router.tsx`** — imports: `FormularioEditarRuta` junto a `FormularioNuevoRuta` (`@/modules/taller/formulario/rutas`) y `RequiereSupertecnico` junto a `InicioReparaciones, RequiereTecnico` (`@/modules/taller/rutas`). Sustituir estas tres líneas

```tsx
          { path: '/reparaciones/historial', element: <HistorialPage tipo="REPARACION" /> },
          { path: '/reparaciones/historial/glass', element: <HistorialPage tipo="GLASS" /> },
          …
          { path: '/reparaciones/imeis/:imei', element: <ImeiDetallePage /> },
```
por (la de `/reparaciones/historial/pulidos` y la de `/reparaciones/imeis` no cambian):

```tsx
          {
            path: '/reparaciones/historial',
            element: <HistorialPage tipo="REPARACION" />,
            children: [{ element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="historial" /> }] }],
          },
          {
            path: '/reparaciones/historial/glass',
            element: <HistorialPage tipo="GLASS" />,
            children: [{ element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="historial-glass" /> }] }],
          },
          {
            path: '/reparaciones/imeis/:imei',
            element: <ImeiDetallePage />,
            children: [{ element: <RequiereSupertecnico />, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="imei" /> }] }],
          },
```

```bash
npx vitest run src/modules/taller/formulario
```
Esperado: PASS (los tests de 9a, 9b y todos los anteriores del directorio).

- [x] **Step 11: "Editar" habilitado en Historial e IMEIs — tests (fallan)**

**11a. `src/modules/taller/historial/HistorialPage.test.tsx`:**
- En `'menú del supertécnico y diálogo "Borrar reparación"…'`, **borrar** la línea `expect(screen.getByRole('menuitem', { name: 'Editar' })).toHaveAttribute('aria-disabled', 'true')`.
- **Borrar entero** el test `'el tooltip de "Editar" deshabilitado va en un envoltorio…'`.
- Añadir (imports a fusionar: `cleanup` de `@testing-library/react`; `renderConRouter`; `detalleEdicion` de `../test/fabrica`; `FormularioEditarRuta` de `../formulario/rutas`; `handlersFormulario` de `../formulario/test/handlers`; `borradorEnReposo` de `../formulario/useBorrador`) y, tras los imports, la red de seguridad de la Task 16:

```tsx
afterEach(async () => {
  cleanup()
  await borradorEnReposo()
})
```
```tsx
  it('"Editar" navega a /reparaciones/historial/editar/<idRep> (y /glass/ en la pestaña Glass), sin confirmación previa y sin tooltip', async () => {
    const hija = [{ path: 'editar/:idRep', element: <p>FORMULARIO EDITAR</p> }]
    const rep = renderConRouter([{ path: '/reparaciones/historial', element: <HistorialPage tipo="REPARACION" />, children: hija }], { sesion: SESION_SUPER, ruta: '/reparaciones/historial' })
    await userEvent.pointer({ keys: '[MouseRight]', target: await screen.findByText('R20260915_133') })
    const editar = await screen.findByRole('menuitem', { name: 'Editar' })
    expect(editar).not.toHaveAttribute('aria-disabled', 'true')
    expect(screen.queryByTitle('Disponible con el formulario de reparación (siguiente entrega)')).not.toBeInTheDocument()
    await userEvent.click(editar)
    expect(rep.router.state.location.pathname).toBe('/reparaciones/historial/editar/R20260915_133')
    expect(await screen.findByText('FORMULARIO EDITAR')).toBeInTheDocument()
    expect(screen.getByText('R20260915_133')).toBeInTheDocument()
    rep.unmount()

    server.use(http.get('*/api/glass/historial', () => HttpResponse.json([resumen({ idRep: 'G20260916_3', tipoComponente: 'gi13', fechaFin: '2026-09-16T09:00:00' })])))
    const gl = renderConRouter([{ path: '/reparaciones/historial/glass', element: <HistorialPage tipo="GLASS" />, children: hija }], { sesion: SESION_SUPER, ruta: '/reparaciones/historial/glass' })
    await userEvent.pointer({ keys: '[MouseRight]', target: await screen.findByText('G20260916_3') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar' }))
    expect(gl.router.state.location.pathname).toBe('/reparaciones/historial/glass/editar/G20260916_3')
  })

  it('"Editar" abre el formulario sobre la tabla y al cerrarlo se recarga el historial', async () => {
    let cargas = 0
    server.use(...handlersFormulario({ detalle: detalleEdicion({ imei: '351900000000041' }) }))
    server.use(http.get('*/api/reparaciones/historial', () => { cargas++; return HttpResponse.json(filas) }))
    const { router } = renderConRouter(
      [{ path: '/reparaciones/historial', element: <HistorialPage tipo="REPARACION" />, children: [{ path: 'editar/:idRep', element: <FormularioEditarRuta origen="historial" /> }] }],
      { sesion: SESION_SUPER, ruta: '/reparaciones/historial' },
    )
    await userEvent.pointer({ keys: '[MouseRight]', target: await screen.findByText('R20260915_133') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar' }))
    expect(await screen.findByRole('dialog', { name: 'Editar reparación — R20260915_133' })).toBeInTheDocument()
    // La tabla sigue montada debajo del diálogo.
    expect(screen.getAllByText('R20260916_6').length).toBeGreaterThan(0)
    const antes = cargas
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar formulario' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/reparaciones/historial'))
    await waitFor(() => expect(cargas).toBeGreaterThan(antes))
  })
```
**11b. `src/modules/taller/imeis/ImeiDetallePage.test.tsx`** — añadir (import a fusionar: `renderConRouter`):

```tsx
  it('"Editar" navega a /reparaciones/imeis/<imei>/editar/<idRep>; solo lo tienen las filas R y G', async () => {
    const { router } = renderConRouter(
      [{ path: '/reparaciones/imeis/:imei', element: <ImeiDetallePage />, children: [{ path: 'editar/:idRep', element: <p>FORMULARIO EDITAR</p> }] }],
      { sesion: SESION_SUPER, ruta: `/reparaciones/imeis/${A}` },
    )
    await screen.findByText('P20260913_1')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('P20260913_1') })
    expect((await screen.findAllByRole('menuitem')).map((m) => m.textContent)).not.toContain('Editar')
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('G20260912_1') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar' }))
    expect(router.state.location.pathname).toBe(`/reparaciones/imeis/${A}/editar/G20260912_1`)
    expect(await screen.findByText('FORMULARIO EDITAR')).toBeInTheDocument()
    expect(screen.getByText(`IMEI: ${A}`)).toBeInTheDocument()
  })
```

```bash
npx vitest run src/modules/taller/historial/HistorialPage.test.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx
```
Esperado: FAIL — "Editar" sigue con `aria-disabled="true"` y el clic no navega.

- [x] **Step 12: `MenuHistorial`, páginas y retirada de `TOOLTIP_FORMULARIO`**

**12a. `src/modules/taller/componentes/MenuHistorial.tsx`** queda así, entero:

```tsx
import type { ReparacionResumen } from '@/shared/api/client'
import { tipoDe } from '@/shared/lib/tipoTrabajo'
import { ContextMenuItem, ContextMenuSeparator } from '@/shared/ui/context-menu'
import type { CeldaPulsada } from '@/shared/ui/DataTable'
import { MenuCopiarCelda } from '@/shared/ui/MenuCopiarCelda'

export type AccionesHistorial = {
  editar: (rep: ReparacionResumen) => void
  borrar: (rep: ReparacionResumen) => void
  anadirIncidencia: (rep: ReparacionResumen) => void
  cancelarIncidencia: (rep: ReparacionResumen) => void
}

/** Menú contextual del Historial y del detalle de IMEIs: Editar (solo filas R y G; abre el formulario en modo edición,
 *  sin confirmación previa), Borrar, Copiar celda, Añadir incidencia (si no tiene), Cancelar incidencia (si está abierta).
 *  Sin permisos de edición (todo el que no sea supertécnico): solo Copiar celda. */
export function MenuHistorial({ rep, celda, texto, puedeEditar, acciones }: { rep: ReparacionResumen; celda: CeldaPulsada; texto: string | null; puedeEditar: boolean; acciones: AccionesHistorial }) {
  if (!puedeEditar) return <MenuCopiarCelda texto={texto} celda={celda} />
  const tipo = tipoDe(rep.idRep)
  const editable = tipo === 'REPARACION' || tipo === 'GLASS'
  const abierta = rep.esIncidencia && !rep.esResuelto
  return (
    <>
      {editable && <ContextMenuItem onSelect={() => acciones.editar(rep)}>Editar</ContextMenuItem>}
      <ContextMenuItem onSelect={() => acciones.borrar(rep)}>Borrar</ContextMenuItem>
      <ContextMenuSeparator />
      <MenuCopiarCelda texto={texto} celda={celda} />
      {(!rep.esIncidencia || abierta) && <ContextMenuSeparator />}
      {!rep.esIncidencia && <ContextMenuItem onSelect={() => acciones.anadirIncidencia(rep)}>Añadir incidencia</ContextMenuItem>}
      {abierta && <ContextMenuItem onSelect={() => acciones.cancelarIncidencia(rep)}>Cancelar incidencia</ContextMenuItem>}
    </>
  )
}
```

**12b. `src/modules/taller/componentes/useAccionesTrabajo.tsx`** — el hook no navega: devuelve las tres acciones con diálogo y cada página añade `editar`. Dos cambios de tipo, nada más:

```tsx
export function useAccionesTrabajo({ tituloBorrar, avisoReferencia }: Opciones): { acciones: Omit<AccionesHistorial, 'editar'>; dialogos: ReactNode } {
```
```tsx
  const acciones: Omit<AccionesHistorial, 'editar'> = { borrar: (r) => void pedirBorrado(r), anadirIncidencia: setConIncidencia, cancelarIncidencia: setACancelar }
```

**12c. `src/modules/taller/historial/HistorialPage.tsx`:**
- Imports: `import { Outlet, useNavigate } from 'react-router'` e `import { MenuHistorial, type AccionesHistorial } from '../componentes/MenuHistorial'` (sustituye al import actual de `MenuHistorial`).
- Tras la línea de `useAccionesTrabajo(...)`:
  ```tsx
    const navigate = useNavigate()
    const listaBase = tipo === 'GLASS' ? '/reparaciones/historial/glass' : '/reparaciones/historial'
    const accionesMenu: AccionesHistorial = { ...acciones, editar: (rep) => void navigate(`${listaBase}/editar/${rep.idRep}`) }
  ```
- En `menuFila`, `acciones={accionesMenu}` (antes `acciones={acciones}`).
- `<Outlet />` como último hijo del `<div className="p-10">`, después de `{dialogos}`.

**12d. `src/modules/taller/imeis/ImeiDetallePage.tsx`:**
- Imports: `import { Outlet, useNavigate, useParams } from 'react-router'` e `import { MenuHistorial, type AccionesHistorial } from '../componentes/MenuHistorial'`.
- Tras la línea de `useAccionesTrabajo(...)` (`navigate` e `imei` ya existen en el componente):
  ```tsx
    // El :imei de la URL, no el de la reparación: al cerrar se vuelve exactamente a este detalle.
    const accionesMenu: AccionesHistorial = { ...acciones, editar: (rep) => void navigate(`/reparaciones/imeis/${imei}/editar/${rep.idRep}`) }
  ```
- En `menuFila`, `acciones={accionesMenu}`.
- `<Outlet />` como último hijo del `<div className="p-10">`, después de `{dialogos}`.

**12e. `src/modules/taller/lib/textos.ts`** — borrar la constante `TOOLTIP_FORMULARIO` y su comentario; el fichero se queda solo con `TOOLTIP_ALMACEN` (Task 5). Comprobar que no queda ningún uso (incluido `pendientes/PendientesPage.tsx`, que dejó de importarla al habilitar "Añadir glass"):

```bash
grep -rn "TOOLTIP_FORMULARIO\|siguiente entrega" src
```
Esperado: sin resultados. Si `PendientesPage.tsx` o su test aún la nombran, se quita ese import/aserción ahora.

```bash
npx vitest run src/modules/taller
```
Esperado: PASS.

- [x] **Step 13: Verde y commit**

```bash
npm run check
git add src/app/router.tsx src/modules/taller/rutas.tsx src/modules/taller/rutas.test.tsx src/modules/taller/formulario/DialogoSalirSinGuardar.tsx src/modules/taller/formulario/rutas.tsx src/modules/taller/formulario/rutas.test.tsx src/modules/taller/formulario/FormularioReparacion.tsx src/modules/taller/formulario/FormularioReparacion.test.tsx src/modules/taller/formulario/FilaComponente.tsx src/modules/taller/formulario/FilaComponente.test.tsx src/modules/taller/formulario/OtrasAcciones.tsx src/modules/taller/formulario/OtrasAcciones.test.tsx src/modules/taller/formulario/useGuardado.ts src/modules/taller/formulario/useGuardado.test.tsx src/modules/taller/componentes/MenuHistorial.tsx src/modules/taller/componentes/useAccionesTrabajo.tsx src/modules/taller/historial/HistorialPage.tsx src/modules/taller/historial/HistorialPage.test.tsx src/modules/taller/imeis/ImeiDetallePage.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx src/modules/taller/pendientes/PendientesPage.tsx src/modules/taller/lib/textos.ts
git commit -m "feat(web): edición de reparaciones desde Historial e IMEIs con \"Salir sin guardar\" y aviso de modificación concurrente"
```
(`PendientesPage.tsx` solo entra en el `git add` si el Step 12e tuvo que tocarlo.)


### Task 19: Web — notificaciones (1): datos, alertas y `Campana` en la barra

**Files:**
- Create: `src/modules/taller/notificaciones/api.ts`, `api.test.tsx`, `alertas.ts`, `alertas.test.ts`, `solicitudes.ts`, `solicitudes.test.ts`, `Campana.tsx`, `Campana.test.tsx`, `test/handlers.ts`, `public/NotfON.png`, `public/NotifOFF.png`, `public/Badge.png`
- Modify: `src/app/shell/TopBar.tsx`, `src/app/shell/TopBar.test.tsx`, `src/shared/styles/tokens.css`, `src/shared/styles/globals.css` (animación del pulso)
- Modify (solo tests, para que sigan en verde): `src/modules/taller/pendientes/PendientesPage.test.tsx`, `src/modules/taller/historial/HistorialPage.test.tsx`, `src/modules/taller/imeis/ImeisPage.test.tsx`, `src/modules/taller/imeis/ImeiDetallePage.test.tsx` (montan `<AppLayout />` con sesión de supertécnico: desde esta tarea la barra pide los datos de la campana)

**Interfaces:**
- Consumes: tipos `Componente`, `SolicitudResumen`, `SolicitudStock` de `@/shared/api/client` (W1); `componente()`, `solicitudUrgente()`, `solicitudPreventiva()` de `src/modules/taller/test/fabrica.ts` (W2); `queryMeta.silenciarError` de `src/shared/api/queryClient.ts` (W8: una consulta con `meta: { silenciarError: true }` no avisa de nada, tampoco del corte de conexión); `useIntervaloRefresco` (`@/shared/api/refresco`); `CLAVE_NOTIF = ['notificaciones']` de `../formulario/api` (W10); `LlamadaRegistrada` de `../formulario/test/handlers` (W10); `useSession` / `esSuperTecnico`.
- Produces (W14, firmas exactas):

```ts
// alertas.ts (pura)
export function esAlerta(c: Componente): boolean
export type AlertaStock = { componente: Componente; nivel: 'sinStock' | 'stockBajo' }
export function alertasOrdenadas(componentes: Componente[]): AlertaStock[]
export function hayAlertas(componentes: Componente[]): boolean

// solicitudes.ts (pura)
export type TarjetaDatos =
  | { clase: 'U'; id: number; grupo: 'pendiente' | 'rechazada'; sol: SolicitudResumen }
  | { clase: 'P'; id: number; grupo: 'pendiente' | 'rechazada'; sol: SolicitudStock }
export type ListasSolicitudes = { pendientes: TarjetaDatos[]; rechazadas: TarjetaDatos[] }
export function componerListas(d: { urgPend: SolicitudResumen[]; prevPend: SolicitudStock[]; urgRech: SolicitudResumen[]; prevRech: SolicitudStock[] }): ListasSolicitudes
export function firma(l: ListasSolicitudes): string
export function lineaInfo(t: TarjetaDatos): string

// api.ts
export { CLAVE_NOTIF } from '../formulario/api'
export const CLAVE_NOTIF_CONTADOR = ['notificaciones', 'contador'] as const
export const CLAVE_NOTIF_SOLICITUDES = ['notificaciones', 'solicitudes'] as const
export const CLAVE_NOTIF_COMPONENTES = ['notificaciones', 'componentes'] as const
export function useContadorNotificaciones(activo: boolean): UseQueryResult<number>
export function useSolicitudesPanel(abierto: boolean): UseQueryResult<ListasSolicitudes>
export function useComponentesGestionados(sondea: boolean): UseQueryResult<Componente[]>
export function useCambiarEstadoSolicitud(): UseMutationResult<void, unknown, { clase: 'U' | 'P'; id: number; estado: 'PENDIENTE' | 'RECHAZADA' }>
export function useQuitarSolicitud(): UseMutationResult<void, unknown, { clase: 'U' | 'P'; id: number }>
export function useRechazarTodo(): UseMutationResult<void, unknown, void>

// Campana.tsx
export function Campana(): JSX.Element | null            // null si no es SUPERTECNICO

// test/handlers.ts
export type EscenarioNotificaciones = { urgPend?: SolicitudResumen[]; prevPend?: SolicitudStock[]; urgRech?: SolicitudResumen[]; prevRech?: SolicitudStock[]; gestionados?: Componente[] }
export function handlersNotificaciones(e?: EscenarioNotificaciones): RequestHandler[]
export function conRegistroNotificaciones(e?: EscenarioNotificaciones): { handlers: RequestHandler[]; llamadas: LlamadaRegistrada[] }
```
  Tokens "Task 19" de W9. Selectores de W13: botón `"Notificaciones"` con `data-testid="campana"`, `data-pulso="true|false"`, `data-encendida="true|false"`; badge `data-testid="campana-badge"`. Hasta la Task 20, `Campana` pinta un panel vacío `data-testid="panel-notificaciones"` con `data-pestana="solicitudes|alertas"`.

**Ficha** (`docs/paridad/notificaciones.md`): sección "Campana en la barra" completa ("Roles: solo SUPERTECNICO…", "Posición: extremo derecho…", "Botón transparente sin borde…", "Badge: arriba a la derecha…", "Contador = solicitudes urgentes…", "La imagen encendida también se fuerza…", "El contador se recalcula…"); sección "Pulso de alertas" completa ("Alerta de stock = componente master…", "Al iniciar sesión, si hay alguna alerta…", "El pulso se para con el primer clic…", "No hay diálogo automático…"); de "Panel": "Se abre con un clic en la campana y se cierra con otro…" (solo el conmutador) y "Pestaña inicial…"; de "Pestaña Alertas": "Orden…" y "Un componente con stock negativo…" (parte pura); de "Pestaña Solicitudes": "Orden…" y "Fechas…" (parte pura).

- [x] **Step 1: Imágenes de la campana**

Se copian tal cual del cliente de referencia (rama `hotfix/0.16.3` del repo raíz), con sus nombres originales (incluida la errata `NotfON`). Comando de solo lectura sobre el repo raíz; ejecutarlo **en Bash** (la redirección `>` de PowerShell estropea los binarios). `borrar.png` ya está en `public/`.

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
for f in NotfON.png NotifOFF.png Badge.png; do
  git show "hotfix/0.16.3:gestion-reparaciones-cliente/src/main/resources/images/$f" > "gestion-reparaciones-web/public/$f"
done
git hash-object gestion-reparaciones-web/public/NotfON.png gestion-reparaciones-web/public/NotifOFF.png gestion-reparaciones-web/public/Badge.png
```
Expected (los identificadores de blob del cliente; si no coinciden, la copia se ha estropeado):

```
e012f409cb995a76784014d75bd4e2706256a3b5
33b9dbfa4090b927bf2dc048ffb210693fbbc33e
3e2cba5baa346921a81152b857d90ebf8a3e9273
```

- [x] **Step 2: Tokens de la campana y animación del pulso**

`src/shared/styles/tokens.css`, dentro de `@theme`, después de `--color-seleccion-suave` (o de la última línea que haya dejado la Task 10):

```css
  --color-notif-panel-brd: #C4C9D4;
  --color-notif-segmento-brd: #D0D4DC;
  --color-notif-tarjeta-alt: #F5F6F8;
  --color-notif-urgente-text: #D97B00;
  --color-notif-rechazar-bg: #F5A0A0;
  --color-notif-rechazar-text: #7A2020;
  --color-notif-rechazada-bg: #F0F1F3;
  --color-notif-rechazada-alt: #E9EAEC;
  --color-notif-avatar-apagado: #EDEEF0;
  --color-notif-texto-apagado: #B0B5BF;
  --color-notif-urgente-apagado-text: #C8A060;
  --color-notif-urgente-apagado-bg: #FFF8ED;
  --color-notif-sin-stock: #E8504A;
  --color-notif-stock-bajo: #E8903A;
```
Reutilizados sin crear nada: `amarillo` (#F1E356), `badge-neutro-bg` (#E8EAF0), `texto-fecha-inicio` (#9AA0AA), `aviso-conflicto-bg` (#FFF3E0, de la Task 10), `azul-medio`, `azul-gris`, `azul-noche`, `superficie` (blanco), `fondo-vista`.

`src/shared/styles/globals.css`, al final del fichero:

```css
/* Pulso de alertas de la campana (calco del ScaleTransition + DropShadow del JavaFX): escala 1 → 1,25 → 1 y halo
   amarillo de 0 → 40 px → 0, ciclo de 1,2 s, lineal, sin fin. La lógica (cuándo late) vive en Campana.tsx. */
@keyframes pulso-campana {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 var(--color-amarillo);
  }
  50% {
    transform: scale(1.25);
    box-shadow: 0 0 40px 0 var(--color-amarillo);
  }
}
.campana-pulso {
  border-radius: 9999px;
  animation: pulso-campana 1.2s linear infinite;
}
/* Con "reducir movimiento" no hay animación, pero sí un halo fijo: el estado (data-pulso) no cambia. */
@media (prefers-reduced-motion: reduce) {
  .campana-pulso {
    animation: none;
    box-shadow: 0 0 12px 0 var(--color-amarillo);
  }
}
```

```bash
npm run build
```
Expected: build correcto (Tailwind compila los tokens y el `@keyframes`). El CSS no tiene test propio: la lógica del pulso se comprueba por `data-pulso` en el Step 11.

- [x] **Step 3: `alertas.ts` — test que falla**

`src/modules/taller/notificaciones/alertas.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { componente } from '../test/fabrica'
import { alertasOrdenadas, esAlerta, hayAlertas } from './alertas'

describe('alertas de stock (ficha notificaciones.md, "Pulso de alertas" y "Pestaña Alertas")', () => {
  it('alertas: master, activo y stock <= mínimo (0 con mínimo 0 cuenta; slave e inactivo no)', () => {
    expect(esAlerta(componente({ stock: 2, stockMinimo: 2 }))).toBe(true)
    expect(esAlerta(componente({ stock: 0, stockMinimo: 0 }))).toBe(true)
    expect(esAlerta(componente({ stock: 3, stockMinimo: 2 }))).toBe(false)
    expect(esAlerta(componente({ stock: 0, idComMaster: 101 }))).toBe(false)
    expect(esAlerta(componente({ stock: 0, activo: false }))).toBe(false)
  })
  it('orden sin stock → stock bajo, conservando el orden recibido', () => {
    const lista = [
      componente({ idCom: 101, tipo: 'bati13', stock: 1, stockMinimo: 2 }),
      componente({ idCom: 102, tipo: 'bati14', stock: 0, stockMinimo: 2 }),
      componente({ idCom: 111, tipo: 'lcdi13', stock: 1, stockMinimo: 1 }),
      componente({ idCom: 112, tipo: 'lcdi14', stock: 3, stockMinimo: 1 }),
      componente({ idCom: 142, tipo: 'gi14', stock: 0, stockMinimo: 2 }),
    ]
    expect(alertasOrdenadas(lista).map((a) => [a.componente.idCom, a.nivel])).toEqual([
      [102, 'sinStock'],
      [142, 'sinStock'],
      [101, 'stockBajo'],
      [111, 'stockBajo'],
    ])
  })
  it('stock negativo cuenta en hayAlertas y no tiene tarjeta', () => {
    const negativo = [componente({ idCom: 121, tipo: 'cami13', stock: -1, stockMinimo: 1 })]
    expect(hayAlertas(negativo)).toBe(true)
    expect(alertasOrdenadas(negativo)).toEqual([])
    expect(hayAlertas([componente({ stock: 5, stockMinimo: 2 })])).toBe(false)
    expect(hayAlertas([])).toBe(false)
  })
})
```

```bash
npm test -- notificaciones/alertas
```
Expected: FAIL — `Failed to resolve import "./alertas"`.

- [x] **Step 4: `alertas.ts` — implementación**

`src/modules/taller/notificaciones/alertas.ts`:

```ts
import type { Componente } from '@/shared/api/client'

/** Alerta de stock: componente master (sin `idComMaster`), activo y con stock <= mínimo (0 con mínimo 0 también). Se calcula
 *  en la web sobre GET /api/componentes/gestionados, como en el cliente de referencia (no se usa /stock-bajo). */
export function esAlerta(c: Componente): boolean {
  return c.idComMaster == null && c.activo && c.stock <= c.stockMinimo
}

export type AlertaStock = { componente: Componente; nivel: 'sinStock' | 'stockBajo' }

/** Primero stock === 0, después stock > 0, cada grupo en el orden recibido (el del servidor, por SKU). Los de stock
 *  negativo cuentan en hayAlertas pero no salen: los grupos son `== 0` y `> 0`, como en la referencia. */
export function alertasOrdenadas(componentes: Componente[]): AlertaStock[] {
  const alertas = componentes.filter(esAlerta)
  return [
    ...alertas.filter((c) => c.stock === 0).map((c): AlertaStock => ({ componente: c, nivel: 'sinStock' })),
    ...alertas.filter((c) => c.stock > 0).map((c): AlertaStock => ({ componente: c, nivel: 'stockBajo' })),
  ]
}

export function hayAlertas(componentes: Componente[]): boolean {
  return componentes.some(esAlerta)
}
```

```bash
npm test -- notificaciones/alertas
```
Expected: PASS (3 tests).

- [x] **Step 5: `solicitudes.ts` — test que falla**

`src/modules/taller/notificaciones/solicitudes.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { solicitudPreventiva, solicitudUrgente } from '../test/fabrica'
import { componerListas, firma, lineaInfo } from './solicitudes'

const datos = () => ({
  urgPend: [solicitudUrgente({ idRc: 501 }), solicitudUrgente({ idRc: 502, idRep: 'AG20260916_2' })],
  prevPend: [solicitudPreventiva({ idSol: 701 })],
  urgRech: [solicitudUrgente({ idRc: 503, estado: 'RECHAZADA' })],
  prevRech: [solicitudPreventiva({ idSol: 702, estado: 'RECHAZADA' })],
})

describe('solicitudes del panel (ficha notificaciones.md, "Pestaña Solicitudes")', () => {
  it('componerListas: urgentes antes que preventivas en cada grupo', () => {
    const l = componerListas(datos())
    expect(l.pendientes.map((t) => `${t.clase}${t.id}:${t.grupo}`)).toEqual(['U501:pendiente', 'U502:pendiente', 'P701:pendiente'])
    expect(l.rechazadas.map((t) => `${t.clase}${t.id}:${t.grupo}`)).toEqual(['U503:rechazada', 'P702:rechazada'])
  })
  it('firma cambia con ids/grupo/clase y no con descripción, técnico o fecha', () => {
    const base = firma(componerListas(datos()))
    const retocada = datos()
    retocada.urgPend[0] = solicitudUrgente({ idRc: 501, descripcion: 'otra', nombreTecnico: 'Técnico H', fechaSolicitud: '2026-09-17T10:00:00' })
    expect(firma(componerListas(retocada))).toBe(base)
    const conNueva = datos()
    conNueva.urgPend.push(solicitudUrgente({ idRc: 504 }))
    expect(firma(componerListas(conNueva))).not.toBe(base)
    const cambiadaDeGrupo = datos()
    cambiadaDeGrupo.urgRech.push(cambiadaDeGrupo.urgPend.pop()!)
    expect(firma(componerListas(cambiadaDeGrupo))).not.toBe(base)
    // Misma cifra en otra clase: U701 no es P701
    const otraClase = datos()
    otraClase.prevPend = []
    otraClase.urgPend.push(solicitudUrgente({ idRc: 701 }))
    expect(firma(componerListas(otraClase))).not.toBe(base)
  })
  it('lineaInfo de las cuatro tarjetas (urgente rechazada sin fecha; preventiva rechazada con fecha)', () => {
    const l = componerListas(datos())
    // 07:02 UTC = 09:02 en Madrid (septiembre); 09:30 UTC = 11:30
    expect(lineaInfo(l.pendientes[0])).toBe('Técnico A  ·  16/09/2026 09:02  ·  A20260916_1')
    expect(lineaInfo(l.pendientes[2])).toBe('tecnico_n  ·  16/09/2026 11:30')
    expect(lineaInfo(l.rechazadas[0])).toBe('Técnico A  ·  A20260916_1')
    expect(lineaInfo(l.rechazadas[1])).toBe('tecnico_n  ·  16/09/2026 11:30')
  })
})
```

```bash
npm test -- notificaciones/solicitudes
```
Expected: FAIL — `Failed to resolve import "./solicitudes"`.

- [x] **Step 6: `solicitudes.ts` — implementación**

`src/modules/taller/notificaciones/solicitudes.ts`:

```ts
import type { SolicitudResumen, SolicitudStock } from '@/shared/api/client'
import { formatear } from '@/shared/lib/fechas'

/** U = urgente (solicitud de pieza de una asignación, id = idRc); P = preventiva (solicitud de stock, id = idSol). */
export type TarjetaDatos =
  | { clase: 'U'; id: number; grupo: 'pendiente' | 'rechazada'; sol: SolicitudResumen }
  | { clase: 'P'; id: number; grupo: 'pendiente' | 'rechazada'; sol: SolicitudStock }

export type ListasSolicitudes = { pendientes: TarjetaDatos[]; rechazadas: TarjetaDatos[] }

const urgente = (grupo: 'pendiente' | 'rechazada') => (sol: SolicitudResumen): TarjetaDatos => ({ clase: 'U', id: sol.idRc, grupo, sol })
const preventiva = (grupo: 'pendiente' | 'rechazada') => (sol: SolicitudStock): TarjetaDatos => ({ clase: 'P', id: sol.idSol, grupo, sol })

/** En cada lista, primero todas las urgentes (orden del servidor) y después todas las preventivas; no se intercalan por fecha. */
export function componerListas(d: { urgPend: SolicitudResumen[]; prevPend: SolicitudStock[]; urgRech: SolicitudResumen[]; prevRech: SolicitudStock[] }): ListasSolicitudes {
  return {
    pendientes: [...d.urgPend.map(urgente('pendiente')), ...d.prevPend.map(preventiva('pendiente'))],
    rechazadas: [...d.urgRech.map(urgente('rechazada')), ...d.prevRech.map(preventiva('rechazada'))],
  }
}

const idsDe = (tarjetas: TarjetaDatos[]) => tarjetas.map((t) => `${t.clase}${t.id}`).sort().join(',')

/** Conjunto de identificadores con su grupo y clase: si no cambia, el panel conserva las tarjetas que ya pinta (así un
 *  sondeo no parpadea ni pierde el desplazamiento). Descripción, técnico y fecha no entran. */
export function firma(l: ListasSolicitudes): string {
  return `pendiente:${idsDe(l.pendientes)}|rechazada:${idsDe(l.rechazadas)}`
}

const SEPARADOR = '  ·  '
const FMT = 'dd/MM/yyyy HH:mm'

/** Línea de información de la tarjeta (calcos de la referencia): la urgente muestra `fechaSolicitud` (que el servidor rellena
 *  con la fecha de la asignación) y pierde la fecha al rechazarse; la preventiva la conserva y nunca lleva id de asignación. */
export function lineaInfo(t: TarjetaDatos): string {
  const partes =
    t.clase === 'U'
      ? t.grupo === 'pendiente'
        ? [t.sol.nombreTecnico, formatear(t.sol.fechaSolicitud, FMT), t.sol.idRep]
        : [t.sol.nombreTecnico, t.sol.idRep]
      : [t.sol.nombreUsuario, formatear(t.sol.fecha, FMT)]
  return partes.filter((p) => p !== '').join(SEPARADOR)
}
```

```bash
npm test -- notificaciones/solicitudes
```
Expected: PASS (3 tests).

- [x] **Step 7: Handlers MSW de la campana**

`src/modules/taller/notificaciones/test/handlers.ts` (con estado: un cambio de estado mueve la solicitud de lista, "limpiar" y el borrado la quitan; así los tests de vista ven el resultado de la recarga). Las escrituras responden 204, como el resto de handlers del proyecto.

```ts
import { HttpResponse, http, type RequestHandler } from 'msw'
import type { Componente, SolicitudResumen, SolicitudStock } from '@/shared/api/client'
import type { LlamadaRegistrada } from '../../formulario/test/handlers'

export type EscenarioNotificaciones = {
  urgPend?: SolicitudResumen[]
  prevPend?: SolicitudStock[]
  urgRech?: SolicitudResumen[]
  prevRech?: SolicitudStock[]
  gestionados?: Componente[]
}

async function cuerpoDe(request: Request): Promise<unknown> {
  const texto = await request.clone().text()
  return texto === '' ? null : JSON.parse(texto)
}

function construir(e: EscenarioNotificaciones, llamadas: LlamadaRegistrada[] | null): RequestHandler[] {
  // El estado de cada solicitud manda sobre la lista en la que llegó: así un PATCH de estado la cambia de grupo.
  let urgentes: SolicitudResumen[] = [
    ...(e.urgPend ?? []).map((s) => ({ ...s, estado: 'PENDIENTE' })),
    ...(e.urgRech ?? []).map((s) => ({ ...s, estado: 'RECHAZADA' })),
  ]
  let preventivas: SolicitudStock[] = [
    ...(e.prevPend ?? []).map((s) => ({ ...s, estado: 'PENDIENTE' })),
    ...(e.prevRech ?? []).map((s) => ({ ...s, estado: 'RECHAZADA' })),
  ]
  const gestionados = e.gestionados ?? []
  const filtrar = <T extends { estado: string }>(lista: T[], request: Request) => {
    const estado = new URL(request.url).searchParams.get('estado')
    return estado ? lista.filter((s) => s.estado === estado) : lista
  }
  const registrar = async (request: Request) => {
    llamadas?.push({ metodo: request.method, ruta: new URL(request.url).pathname, cuerpo: await cuerpoDe(request) })
  }
  const hecho = () => new HttpResponse(null, { status: 204 })
  return [
    http.get('*/api/solicitudes/count', () => HttpResponse.json({ value: urgentes.filter((s) => s.estado === 'PENDIENTE').length })),
    http.get('*/api/solicitudes-stock/count', () => HttpResponse.json({ value: preventivas.filter((s) => s.estado === 'PENDIENTE').length })),
    http.get('*/api/solicitudes', ({ request }) => HttpResponse.json(filtrar(urgentes, request))),
    http.get('*/api/solicitudes-stock', ({ request }) => HttpResponse.json(filtrar(preventivas, request))),
    http.get('*/api/componentes/gestionados', () => HttpResponse.json(gestionados)),
    http.patch('*/api/solicitudes/:idRc/estado', async ({ request, params }) => {
      await registrar(request)
      const { estado } = (await request.clone().json()) as { estado: string }
      urgentes = urgentes.map((s) => (s.idRc === Number(params.idRc) ? { ...s, estado } : s))
      return hecho()
    }),
    http.patch('*/api/solicitudes/:idRc/limpiar', async ({ request, params }) => {
      await registrar(request)
      urgentes = urgentes.filter((s) => s.idRc !== Number(params.idRc))
      return hecho()
    }),
    http.patch('*/api/solicitudes-stock/:idSol/estado', async ({ request, params }) => {
      await registrar(request)
      const { estado } = (await request.clone().json()) as { estado: string }
      preventivas = preventivas.map((s) => (s.idSol === Number(params.idSol) ? { ...s, estado } : s))
      return hecho()
    }),
    http.delete('*/api/solicitudes-stock/:idSol', async ({ request, params }) => {
      await registrar(request)
      preventivas = preventivas.filter((s) => s.idSol !== Number(params.idSol))
      return hecho()
    }),
  ]
}

/** Lecturas y escrituras de la campana con éxito. Los `count` se derivan de las listas pendientes. */
export function handlersNotificaciones(e: EscenarioNotificaciones = {}): RequestHandler[] {
  return construir(e, null)
}

/** Igual, registrando las escrituras recibidas, en orden, para comprobar rutas y cuerpos. */
export function conRegistroNotificaciones(e: EscenarioNotificaciones = {}): { handlers: RequestHandler[]; llamadas: LlamadaRegistrada[] } {
  const llamadas: LlamadaRegistrada[] = []
  return { handlers: construir(e, llamadas), llamadas }
}
```
No tiene test propio: lo ejercitan los de `api.test.tsx` y `Campana.test.tsx`.

- [x] **Step 8: `api.ts` — tests que fallan**

`src/modules/taller/notificaciones/api.test.tsx`:

```tsx
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { crearQueryClient } from '@/shared/api/queryClient'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { guardarSesion } from '@/shared/session/storage'
import { onError } from '@/shared/ui/alertas'
import { SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { componente, solicitudPreventiva, solicitudUrgente } from '../test/fabrica'
import {
  CLAVE_NOTIF, CLAVE_NOTIF_COMPONENTES, CLAVE_NOTIF_CONTADOR, CLAVE_NOTIF_SOLICITUDES, useCambiarEstadoSolicitud,
  useComponentesGestionados, useContadorNotificaciones, useQuitarSolicitud, useRechazarTodo, useSolicitudesPanel,
} from './api'
import { conRegistroNotificaciones, handlersNotificaciones } from './test/handlers'

function envoltorio() {
  guardarSesion(SESION_SUPER)
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}><SessionProvider>{children}</SessionProvider></QueryClientProvider>
  )
  return { qc, wrapper }
}

/** GET recibidos por el servidor simulado, como "ruta?consulta". */
function espiarGets(): string[] {
  const gets: string[] = []
  server.events.on('request:start', ({ request }) => {
    if (request.method !== 'GET') return
    const url = new URL(request.url)
    gets.push(url.pathname + url.search)
  })
  return gets
}
afterEach(() => server.events.removeAllListeners())

const TRES_PENDIENTES = {
  urgPend: [solicitudUrgente({ idRc: 501 }), solicitudUrgente({ idRc: 502 })],
  prevPend: [solicitudPreventiva({ idSol: 701 })],
}

describe('api de notificaciones', () => {
  it('las claves cuelgan de CLAVE_NOTIF (invalidar el prefijo las recarga todas)', () => {
    for (const clave of [CLAVE_NOTIF_CONTADOR, CLAVE_NOTIF_SOLICITUDES, CLAVE_NOTIF_COMPONENTES]) expect(clave[0]).toBe(CLAVE_NOTIF[0])
  })
  it('contador = suma de los dos count; inactivo no pide nada', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones(TRES_PENDIENTES))
    const { wrapper } = envoltorio()
    const inactivo = renderHook(() => useContadorNotificaciones(false), { wrapper })
    expect(inactivo.result.current.fetchStatus).toBe('idle')
    expect(gets).toEqual([])
    const activo = renderHook(() => useContadorNotificaciones(true), { wrapper })
    await waitFor(() => expect(activo.result.current.data).toBe(3))
  })
  it('fallo al contar: sin aviso y el dato anterior se conserva', async () => {
    const avisos: string[] = []
    const quitar = onError((m) => avisos.push(m))
    server.use(...handlersNotificaciones(TRES_PENDIENTES))
    const { wrapper } = envoltorio()
    const h = renderHook(() => useContadorNotificaciones(true), { wrapper })
    await waitFor(() => expect(h.result.current.data).toBe(3))
    server.use(http.get('*/api/solicitudes/count', () => HttpResponse.json({ message: 'no' }, { status: 403 })))
    await act(async () => { await h.result.current.refetch() })
    expect(h.result.current.isError).toBe(true)
    expect(h.result.current.data).toBe(3)
    expect(avisos).toEqual([])
    quitar()
  })
  it('useSolicitudesPanel pide las cuatro listas por estado y las compone; cerrado no pide', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones({ ...TRES_PENDIENTES, urgRech: [solicitudUrgente({ idRc: 503 })], prevRech: [solicitudPreventiva({ idSol: 702 })] }))
    const { wrapper } = envoltorio()
    const cerrado = renderHook(() => useSolicitudesPanel(false), { wrapper })
    expect(cerrado.result.current.fetchStatus).toBe('idle')
    const abierto = renderHook(() => useSolicitudesPanel(true), { wrapper })
    await waitFor(() => expect(abierto.result.current.isSuccess).toBe(true))
    expect([...gets].sort()).toEqual([
      '/api/solicitudes-stock?estado=PENDIENTE', '/api/solicitudes-stock?estado=RECHAZADA',
      '/api/solicitudes?estado=PENDIENTE', '/api/solicitudes?estado=RECHAZADA',
    ])
    expect(abierto.result.current.data?.pendientes.map((t) => `${t.clase}${t.id}`)).toEqual(['U501', 'U502', 'P701'])
    expect(abierto.result.current.data?.rechazadas.map((t) => `${t.clase}${t.id}`)).toEqual(['U503', 'P702'])
  })
  it('componentes: el primer fallo sin datos avisa una sola vez; los siguientes, no', async () => {
    const avisos: string[] = []
    const quitar = onError((m) => avisos.push(m))
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json({ message: 'no' }, { status: 403 })))
    const { wrapper } = envoltorio()
    const h = renderHook(() => useComponentesGestionados(false), { wrapper })
    await waitFor(() => expect(h.result.current.isError).toBe(true))
    expect(avisos).toEqual(['No tienes permisos para realizar esta acción.'])
    await act(async () => { await h.result.current.refetch() })
    expect(avisos).toHaveLength(1)
    quitar()
  })
  it('componentes: un fallo con datos ya cargados es silencioso y conserva la última lista buena', async () => {
    const avisos: string[] = []
    const quitar = onError((m) => avisos.push(m))
    server.use(...handlersNotificaciones({ gestionados: [componente({ idCom: 102, tipo: 'bati14', stock: 0 })] }))
    const { wrapper } = envoltorio()
    const h = renderHook(() => useComponentesGestionados(true), { wrapper })
    await waitFor(() => expect(h.result.current.data).toHaveLength(1))
    server.use(http.get('*/api/componentes/gestionados', () => new HttpResponse(null, { status: 500 })))
    await act(async () => { await h.result.current.refetch() })
    expect(h.result.current.data).toHaveLength(1)
    expect(avisos).toEqual([])
    quitar()
  })
  it('cambiar estado: PATCH …/estado de la clase que toca y recarga de contador, solicitudes y alertas', async () => {
    const gets = espiarGets()
    const { handlers, llamadas } = conRegistroNotificaciones(TRES_PENDIENTES)
    server.use(...handlers)
    const { wrapper } = envoltorio()
    const contador = renderHook(() => useContadorNotificaciones(true), { wrapper })
    const listas = renderHook(() => useSolicitudesPanel(true), { wrapper })
    const componentes = renderHook(() => useComponentesGestionados(true), { wrapper })
    await waitFor(() => expect(contador.result.current.data).toBe(3))
    await waitFor(() => expect(listas.result.current.isSuccess && componentes.result.current.isSuccess).toBe(true))
    const m = renderHook(() => useCambiarEstadoSolicitud(), { wrapper })
    await act(async () => { await m.result.current.mutateAsync({ clase: 'U', id: 501, estado: 'RECHAZADA' }) })
    await act(async () => { await m.result.current.mutateAsync({ clase: 'P', id: 701, estado: 'RECHAZADA' }) })
    expect(llamadas).toEqual([
      { metodo: 'PATCH', ruta: '/api/solicitudes/501/estado', cuerpo: { estado: 'RECHAZADA' } },
      { metodo: 'PATCH', ruta: '/api/solicitudes-stock/701/estado', cuerpo: { estado: 'RECHAZADA' } },
    ])
    await waitFor(() => expect(contador.result.current.data).toBe(1))
    await waitFor(() => expect(listas.result.current.data?.rechazadas.map((t) => `${t.clase}${t.id}`)).toEqual(['U501', 'P701']))
    expect(gets.filter((g) => g === '/api/componentes/gestionados').length).toBeGreaterThanOrEqual(2)
  })
  it('quitar: urgente → PATCH limpiar sin cuerpo; preventiva → DELETE', async () => {
    const { handlers, llamadas } = conRegistroNotificaciones({ urgRech: [solicitudUrgente({ idRc: 503 })], prevRech: [solicitudPreventiva({ idSol: 702 })] })
    server.use(...handlers)
    const { wrapper } = envoltorio()
    const m = renderHook(() => useQuitarSolicitud(), { wrapper })
    await act(async () => { await m.result.current.mutateAsync({ clase: 'U', id: 503 }) })
    await act(async () => { await m.result.current.mutateAsync({ clase: 'P', id: 702 }) })
    expect(llamadas).toEqual([
      { metodo: 'PATCH', ruta: '/api/solicitudes/503/limpiar', cuerpo: null },
      { metodo: 'DELETE', ruta: '/api/solicitudes-stock/702', cuerpo: null },
    ])
  })
  it('rechazar todo: pide las pendientes y las rechaza una a una, urgentes y luego preventivas; no recarga las alertas', async () => {
    const gets = espiarGets()
    const { handlers, llamadas } = conRegistroNotificaciones(TRES_PENDIENTES)
    server.use(...handlers)
    const { wrapper } = envoltorio()
    const componentes = renderHook(() => useComponentesGestionados(true), { wrapper })
    await waitFor(() => expect(componentes.result.current.isSuccess).toBe(true))
    const m = renderHook(() => useRechazarTodo(), { wrapper })
    await act(async () => { await m.result.current.mutateAsync() })
    expect(llamadas.map((l) => l.ruta)).toEqual(['/api/solicitudes/501/estado', '/api/solicitudes/502/estado', '/api/solicitudes-stock/701/estado'])
    expect(llamadas.every((l) => JSON.stringify(l.cuerpo) === '{"estado":"RECHAZADA"}')).toBe(true)
    expect(gets.filter((g) => g === '/api/componentes/gestionados')).toHaveLength(1)
  })
  it('rechazar todo se detiene en el primer error y lanza; con la lista vacía no escribe nada', async () => {
    const { handlers, llamadas } = conRegistroNotificaciones(TRES_PENDIENTES)
    server.use(http.patch('*/api/solicitudes/502/estado', () => HttpResponse.json({ message: 'La solicitud ya no está pendiente' }, { status: 409 })), ...handlers)
    const { wrapper } = envoltorio()
    const m = renderHook(() => useRechazarTodo(), { wrapper })
    await act(async () => { await expect(m.result.current.mutateAsync()).rejects.toThrow('La solicitud ya no está pendiente') })
    expect(llamadas.map((l) => l.ruta)).toEqual(['/api/solicitudes/501/estado'])

    server.resetHandlers()
    const vacio = conRegistroNotificaciones()
    server.use(...vacio.handlers)
    const otro = envoltorio()
    const m2 = renderHook(() => useRechazarTodo(), { wrapper: otro.wrapper })
    await act(async () => { await m2.result.current.mutateAsync() })
    expect(vacio.llamadas).toEqual([])
  })
})
```

```bash
npm test -- notificaciones/api
```
Expected: FAIL — `Failed to resolve import "./api"`.

- [x] **Step 9: `api.ts` — implementación**

`src/modules/taller/notificaciones/api.ts`:

```ts
import { useMutation, useQuery, useQueryClient, type UseMutationResult, type UseQueryResult } from '@tanstack/react-query'
import { api, type Componente } from '@/shared/api/client'
import { ConexionError, SesionExpiradaError, mensajeDeError, mensajeSinConexion } from '@/shared/api/errors'
import { useIntervaloRefresco } from '@/shared/api/refresco'
import { emitirError } from '@/shared/ui/alertas'
import { CLAVE_NOTIF } from '../formulario/api'
import { componerListas, type ListasSolicitudes } from './solicitudes'

export { CLAVE_NOTIF } from '../formulario/api'
export const CLAVE_NOTIF_CONTADOR = ['notificaciones', 'contador'] as const
export const CLAVE_NOTIF_SOLICITUDES = ['notificaciones', 'solicitudes'] as const
export const CLAVE_NOTIF_COMPONENTES = ['notificaciones', 'componentes'] as const

type Estado = 'PENDIENTE' | 'RECHAZADA'

async function contar(): Promise<number> {
  const [urgentes, preventivas] = await Promise.all([api.GET('/api/solicitudes/count'), api.GET('/api/solicitudes-stock/count')])
  return (urgentes.data?.value ?? 0) + (preventivas.data?.value ?? 0)
}

const pedirUrgentes = async (estado: Estado) => (await api.GET('/api/solicitudes', { params: { query: { estado } } })).data ?? []
const pedirPreventivas = async (estado: Estado) => (await api.GET('/api/solicitudes-stock', { params: { query: { estado } } })).data ?? []

async function pedirListas(): Promise<ListasSolicitudes> {
  const [urgPend, prevPend, urgRech, prevRech] = await Promise.all([
    pedirUrgentes('PENDIENTE'), pedirPreventivas('PENDIENTE'), pedirUrgentes('RECHAZADA'), pedirPreventivas('RECHAZADA'),
  ])
  return componerListas({ urgPend, prevPend, urgRech, prevRech })
}

/** Total del badge: urgentes PENDIENTE + preventivas PENDIENTE. Sondea con el intervalo general (60 s / 5 s) y al volver el
 *  foco (refetchOnWindowFocus del QueryClient). Un fallo se ignora en silencio y el badge se queda como estaba. */
export function useContadorNotificaciones(activo: boolean): UseQueryResult<number> {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({ queryKey: CLAVE_NOTIF_CONTADOR, queryFn: contar, enabled: activo, refetchInterval: intervalo, meta: { silenciarError: true } })
}

/** Las cuatro listas del panel. Solo con el panel abierto; sondea con el intervalo general. Errores: política general de
 *  las consultas (un corte de conexión con datos en pantalla deja solo el banner y se conserva la última lista). */
export function useSolicitudesPanel(abierto: boolean): UseQueryResult<ListasSolicitudes> {
  const intervalo = useIntervaloRefresco(abierto)
  return useQuery({ queryKey: CLAVE_NOTIF_SOLICITUDES, queryFn: pedirListas, enabled: abierto, refetchInterval: intervalo })
}

/** Componentes gestionados, de los que salen las alertas (alertas.ts). `sondea` = panel abierto. La primera carga (la del
 *  pulso) SÍ avisa del error; los sondeos y recargas posteriores, no. Como `meta` es fijo por consulta, la consulta va
 *  silenciada y el aviso se da a mano en el primer fallo de una consulta que nunca tuvo datos. */
export function useComponentesGestionados(sondea: boolean): UseQueryResult<Componente[]> {
  const qc = useQueryClient()
  const intervalo = useIntervaloRefresco(sondea)
  return useQuery({
    queryKey: CLAVE_NOTIF_COMPONENTES,
    queryFn: async (): Promise<Componente[]> => {
      try {
        return (await api.GET('/api/componentes/gestionados')).data ?? []
      } catch (e) {
        const estado = qc.getQueryState(CLAVE_NOTIF_COMPONENTES)
        const primerFallo = estado?.data === undefined && (estado?.errorUpdateCount ?? 0) === 0
        if (primerFallo && !(e instanceof SesionExpiradaError)) emitirError(e instanceof ConexionError ? mensajeSinConexion(e) : mensajeDeError(e))
        throw e
      }
    },
    refetchInterval: intervalo,
    meta: { silenciarError: true },
  })
}

/** "Rechazar" y "Recuperar" de una tarjeta. Sin confirmación. Después recarga todo lo de la campana (contador, solicitudes y
 *  alertas), también si falla. El error lo muestra el aviso global. */
export function useCambiarEstadoSolicitud(): UseMutationResult<void, unknown, { clase: 'U' | 'P'; id: number; estado: Estado }> {
  const qc = useQueryClient()
  return useMutation<void, unknown, { clase: 'U' | 'P'; id: number; estado: Estado }>({
    mutationFn: async ({ clase, id, estado }) => {
      if (clase === 'U') await api.PATCH('/api/solicitudes/{idRc}/estado', { params: { path: { idRc: id } }, body: { estado } })
      else await api.PATCH('/api/solicitudes-stock/{idSol}/estado', { params: { path: { idSol: id } }, body: { estado } })
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: CLAVE_NOTIF })
    },
  })
}

/** Papelera de una rechazada: la urgente se oculta para siempre (limpiar); la preventiva se borra. */
export function useQuitarSolicitud(): UseMutationResult<void, unknown, { clase: 'U' | 'P'; id: number }> {
  const qc = useQueryClient()
  return useMutation<void, unknown, { clase: 'U' | 'P'; id: number }>({
    mutationFn: async ({ clase, id }) => {
      if (clase === 'U') await api.PATCH('/api/solicitudes/{idRc}/limpiar', { params: { path: { idRc: id } } })
      else await api.DELETE('/api/solicitudes-stock/{idSol}', { params: { path: { idSol: id } } })
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: CLAVE_NOTIF })
    },
  })
}

/** Pide las PENDIENTE en ese momento y las rechaza una a una (urgentes, luego preventivas); al primer error se detiene y
 *  lanza (las ya rechazadas quedan así; no recarga). Con éxito recarga solicitudes y contador, no las alertas. */
export function useRechazarTodo(): UseMutationResult<void, unknown, void> {
  const qc = useQueryClient()
  return useMutation<void, unknown, void>({
    mutationFn: async () => {
      const [urgentes, preventivas] = await Promise.all([pedirUrgentes('PENDIENTE'), pedirPreventivas('PENDIENTE')])
      for (const s of urgentes) await api.PATCH('/api/solicitudes/{idRc}/estado', { params: { path: { idRc: s.idRc } }, body: { estado: 'RECHAZADA' } })
      for (const s of preventivas) await api.PATCH('/api/solicitudes-stock/{idSol}/estado', { params: { path: { idSol: s.idSol } }, body: { estado: 'RECHAZADA' } })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: CLAVE_NOTIF_SOLICITUDES })
      void qc.invalidateQueries({ queryKey: CLAVE_NOTIF_CONTADOR })
    },
  })
}
```

```bash
npm test -- notificaciones/api
```
Expected: PASS (10 tests). Si `tsc` protesta en `body: { estado }` es que el contrato de la Task 4 no trae `SolicitudEstadoRequest` / `SolicitudStockEstadoRequest`: se revisa `api/openapi.json`, no se añade ningún cast.

- [x] **Step 10: `Campana` — tests que fallan**

`src/modules/taller/notificaciones/Campana.test.tsx`. El intervalo de sondeo se acorta solo en el test que lo necesita, con un doble de `useIntervaloRefresco` que por defecto delega en el real; el foco de la ventana se simula con `focusManager` de TanStack Query.

```tsx
import { focusManager } from '@tanstack/react-query'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { componente, solicitudPreventiva, solicitudUrgente } from '../test/fabrica'
import { CLAVE_NOTIF_COMPONENTES, CLAVE_NOTIF_CONTADOR } from './api'
import { Campana } from './Campana'
import { handlersNotificaciones } from './test/handlers'

const control = vi.hoisted(() => ({ intervalo: null as number | null }))
vi.mock('@/shared/api/refresco', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/shared/api/refresco')>()
  function useIntervaloRefresco(activo = true): number | false {
    const normal = real.useIntervaloRefresco(activo)
    return control.intervalo !== null && activo ? control.intervalo : normal
  }
  return { ...real, useIntervaloRefresco }
})

function espiarGets(): string[] {
  const gets: string[] = []
  server.events.on('request:start', ({ request }) => {
    if (request.method !== 'GET') return
    const url = new URL(request.url)
    gets.push(url.pathname + url.search)
  })
  return gets
}
const cuantas = (gets: string[], ruta: string) => gets.filter((g) => g === ruta).length

afterEach(() => {
  control.intervalo = null
  focusManager.setFocused(undefined)
  server.events.removeAllListeners()
})

const SIN_STOCK = componente({ idCom: 102, tipo: 'bati14', stock: 0, stockMinimo: 2 })
const campana = () => screen.getByTestId('campana')
const imagen = () => screen.getByTestId('campana-imagen')
/** Pestaña con la que se ha abierto el panel. */
const pestanaAbierta = () => screen.getByTestId('panel-notificaciones').dataset.pestana

describe('Campana (ficha docs/paridad/notificaciones.md)', () => {
  it('TECNICO y ADMIN no la ven ni ocupa hueco (y no piden nada)', () => {
    const gets = espiarGets()
    for (const sesion of [SESION_TEC, SESION_ADMIN]) {
      const { container, unmount } = renderConProviders(<Campana />, { sesion })
      expect(screen.queryByRole('button', { name: 'Notificaciones' })).not.toBeInTheDocument()
      expect(container.querySelector('button, img')).toBeNull()
      unmount()
      sessionStorage.clear()
    }
    expect(gets).toEqual([])
  })
  it('SUPERTECNICO con 0 pendientes: imagen apagada de 30×30, sin badge; botón transparente con hover', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones())
    renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes-stock/count')).toBe(1))
    expect(screen.getByRole('button', { name: 'Notificaciones' })).toBe(campana())
    expect(campana()).toHaveClass('cursor-pointer', 'rounded-lg', 'p-0.5', 'hover:bg-white/8')
    expect(campana()).toHaveAttribute('data-encendida', 'false')
    expect(imagen()).toHaveAttribute('src', '/NotifOFF.png')
    expect(imagen()).toHaveClass('h-[30px]', 'w-[30px]')
    expect(screen.queryByTestId('campana-badge')).not.toBeInTheDocument()
  })
  it('encendida y badge con el número, sin tope (120)', async () => {
    server.use(
      http.get('*/api/solicitudes/count', () => HttpResponse.json({ value: 100 })),
      http.get('*/api/solicitudes-stock/count', () => HttpResponse.json({ value: 20 })),
      ...handlersNotificaciones(),
    )
    renderConProviders(<Campana />, { sesion: SESION_SUPER })
    const badge = await screen.findByTestId('campana-badge')
    expect(badge).toHaveTextContent('120')
    expect(badge).toHaveClass('pointer-events-none', 'translate-x-[10px]', '-translate-y-[7px]')
    expect(badge.querySelector('img')).toHaveAttribute('src', '/Badge.png')
    expect(screen.getByText('120')).toHaveClass('text-[10px]', 'font-bold', 'text-amarillo')
    expect(imagen()).toHaveAttribute('src', '/NotfON.png')
    expect(campana()).toHaveAttribute('data-encendida', 'true')
  })
  it('fallo al contar: sin aviso y badge como estaba', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones({ urgPend: [solicitudUrgente({ idRc: 501 })], prevPend: [solicitudPreventiva({ idSol: 701 })] }))
    const { queryClient } = renderConProviders(<Campana />, { sesion: SESION_SUPER })
    expect(await screen.findByTestId('campana-badge')).toHaveTextContent('2')
    server.use(http.get('*/api/solicitudes/count', () => HttpResponse.json({ message: 'no' }, { status: 403 })))
    await act(async () => { await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_CONTADOR }) })
    expect(cuantas(gets, '/api/solicitudes/count')).toBe(2)
    expect(screen.getByTestId('campana-badge')).toHaveTextContent('2')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('pulso si hay alertas al cargar; el primer clic lo para, abre en Alertas y no vuelve aunque lleguen alertas', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones({ gestionados: [SIN_STOCK] }))
    const { queryClient } = renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await waitFor(() => expect(campana()).toHaveAttribute('data-pulso', 'true'))
    expect(campana().querySelector('.campana-pulso')).not.toBeNull()
    // Late con total 0: la imagen encendida se fuerza mientras dura el pulso, sin badge
    expect(imagen()).toHaveAttribute('src', '/NotfON.png')
    expect(screen.queryByTestId('campana-badge')).not.toBeInTheDocument()

    await userEvent.click(campana())
    expect(campana()).toHaveAttribute('data-pulso', 'false')
    expect(campana().querySelector('.campana-pulso')).toBeNull()
    expect(pestanaAbierta()).toBe('alertas')

    await userEvent.click(campana())
    expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument()
    await act(async () => { await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_COMPONENTES }) })
    expect(cuantas(gets, '/api/componentes/gestionados')).toBe(2)
    expect(campana()).toHaveAttribute('data-pulso', 'false')
    // La segunda apertura ya no es la del pulso
    await userEvent.click(campana())
    expect(pestanaAbierta()).toBe('solicitudes')
  })
  it('sin alertas al cargar no late nunca, aunque aparezcan después; abre en Solicitudes', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones())
    const { queryClient } = renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cuantas(gets, '/api/componentes/gestionados')).toBe(1))
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json([SIN_STOCK])))
    await act(async () => { await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_COMPONENTES }) })
    expect(cuantas(gets, '/api/componentes/gestionados')).toBe(2)
    expect(campana()).toHaveAttribute('data-pulso', 'false')
    await userEvent.click(campana())
    expect(pestanaAbierta()).toBe('solicitudes')
  })
  it('un fallo al cargar los componentes muestra el error y no hay pulso', async () => {
    server.use(http.get('*/api/componentes/gestionados', () => HttpResponse.json({ message: 'no' }, { status: 403 })), ...handlersNotificaciones())
    renderConProviders(<Campana />, { sesion: SESION_SUPER })
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
    expect(campana()).toHaveAttribute('data-pulso', 'false')
  })
  it('conmutador: un clic abre y otro cierra; imagen encendida forzada con el panel abierto aunque el total sea 0', async () => {
    server.use(...handlersNotificaciones())
    renderConProviders(<Campana />, { sesion: SESION_SUPER })
    expect(campana()).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(campana())
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
    expect(campana()).toHaveAttribute('aria-expanded', 'true')
    expect(imagen()).toHaveAttribute('src', '/NotfON.png')
    expect(screen.queryByTestId('campana-badge')).not.toBeInTheDocument()
    await userEvent.click(campana())
    expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument()
    expect(imagen()).toHaveAttribute('src', '/NotifOFF.png')
  })
  it('recuento al abrir y al cerrar el panel', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones())
    const { queryClient } = renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBe(1))
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    await userEvent.click(campana())
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBe(2))
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    await userEvent.click(campana())
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBe(3))
    expect(cuantas(gets, '/api/solicitudes-stock/count')).toBe(3)
  })
  it('recuento al volver el foco a la ventana', async () => {
    const gets = espiarGets()
    server.use(...handlersNotificaciones())
    const { queryClient } = renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBe(1))
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
    })
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBe(2))
  })
  it('recuento por intervalo con el panel cerrado; los componentes no se sondean con el panel cerrado', async () => {
    control.intervalo = 40
    const gets = espiarGets()
    server.use(...handlersNotificaciones())
    renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBeGreaterThanOrEqual(3))
    expect(cuantas(gets, '/api/componentes/gestionados')).toBe(1)
  })
})
```

```bash
npm test -- notificaciones/Campana
```
Expected: FAIL — `Failed to resolve import "./Campana"`.

- [x] **Step 11: `Campana` — implementación**

`src/modules/taller/notificaciones/Campana.tsx`:

```tsx
import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { cn } from '@/shared/lib/utils'
import { useSession } from '@/shared/session/SessionProvider'
import { esSuperTecnico } from '@/shared/session/storage'
import { hayAlertas } from './alertas'
import { CLAVE_NOTIF_CONTADOR, useComponentesGestionados, useContadorNotificaciones } from './api'

/** 'pendiente' = aún no ha llegado la primera respuesta de componentes; se decide una sola vez por montaje ("inicio de
 *  sesión" = carga de la aplicación con sesión) y, una vez parado, no vuelve a arrancar. */
type Pulso = 'pendiente' | 'latiendo' | 'parado'

/** Campana de la barra superior: solo SUPERTECNICO. Para el resto no pinta nada ni pide datos. */
export function Campana() {
  const { sesion } = useSession()
  if (!esSuperTecnico(sesion)) return null
  return <CampanaSupertecnico />
}

function CampanaSupertecnico() {
  const qc = useQueryClient()
  const anclaRef = useRef<HTMLButtonElement>(null)
  const [abierto, setAbierto] = useState(false)
  const [pestanaInicial, setPestanaInicial] = useState<'solicitudes' | 'alertas'>('solicitudes')
  const [pulso, setPulso] = useState<Pulso>('pendiente')
  const contador = useContadorNotificaciones(true)
  const componentes = useComponentesGestionados(abierto)

  // Ajuste de estado durante el render (patrón de React para "derivar una vez"): la PRIMERA respuesta decide el pulso.
  if (pulso === 'pendiente' && (componentes.data !== undefined || componentes.isError)) {
    setPulso(hayAlertas(componentes.data ?? []) ? 'latiendo' : 'parado')
  }

  const total = contador.data ?? 0
  const latiendo = pulso === 'latiendo'
  // Calco: la imagen encendida se fuerza mientras late y con el panel abierto, aunque el total sea 0.
  const encendida = total > 0 || latiendo || abierto

  function alternar() {
    if (!abierto) setPestanaInicial(latiendo ? 'alertas' : 'solicitudes')
    setPulso('parado')
    setAbierto(!abierto)
    // El contador se recalcula al abrir y al cerrar el panel.
    void qc.invalidateQueries({ queryKey: CLAVE_NOTIF_CONTADOR })
  }

  return (
    <>
      <button
        ref={anclaRef}
        type="button"
        aria-label="Notificaciones"
        aria-expanded={abierto}
        data-testid="campana"
        data-pulso={latiendo ? 'true' : 'false'}
        data-encendida={encendida ? 'true' : 'false'}
        onClick={alternar}
        className="cursor-pointer rounded-lg p-0.5 hover:bg-white/8"
      >
        {/* El pulso envuelve imagen y badge: laten juntos. */}
        <span className={cn('relative block', latiendo && 'campana-pulso')}>
          <img data-testid="campana-imagen" src={encendida ? '/NotfON.png' : '/NotifOFF.png'} alt="" className="h-[30px] w-[30px]" />
          {total > 0 && (
            <span data-testid="campana-badge" className="pointer-events-none absolute top-0 right-0 flex h-4 w-4 translate-x-[10px] -translate-y-[7px] items-center justify-center">
              <img src="/Badge.png" alt="" className="absolute inset-0 h-4 w-4" />
              <span className="relative text-[10px] leading-none font-bold text-amarillo">{total}</span>
            </span>
          )}
        </span>
      </button>
      {/* El panel real llega con la tarea siguiente; de momento, un contenedor vacío que recuerda la pestaña inicial. */}
      {abierto && <div data-testid="panel-notificaciones" data-pestana={pestanaInicial} />}
    </>
  )
}
```

```bash
npm test -- notificaciones/Campana
```
Expected: PASS (11 tests).

- [x] **Step 12: La campana en `TopBar` — test que falla**

En `src/app/shell/TopBar.test.tsx`, añadir el import y sustituir el test `'la campana no se muestra en este sub-proyecto (ni al supertécnico)'` por estos dos:

```tsx
import { handlersNotificaciones } from '@/modules/taller/notificaciones/test/handlers'
import { server } from '@/test/server'
```

```tsx
  it('TopBar: la campana va a la izquierda del usuario, solo para el supertécnico', async () => {
    server.use(...handlersNotificaciones())
    renderConProviders(<AppLayout />, { sesion: SESION_SUPER })
    const campana = await screen.findByRole('button', { name: 'Notificaciones' })
    const usuario = screen.getByRole('button', { name: /Hola, tecnico_f/ })
    expect(campana.compareDocumentPosition(usuario) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(campana.parentElement).toBe(usuario.parentElement)
    // Separación de 10 px: el gap de la barra
    expect(campana.parentElement).toHaveClass('gap-2.5')
  })
  it('técnico y admin no tienen campana', () => {
    for (const sesion of [SESION_TEC, SESION_ADMIN]) {
      const { unmount } = renderConProviders(<AppLayout />, { sesion })
      expect(screen.queryByRole('button', { name: 'Notificaciones' })).not.toBeInTheDocument()
      unmount()
      sessionStorage.clear()
    }
  })
```

```bash
npm test -- TopBar
```
Expected: FAIL — `Unable to find role="button" and name "Notificaciones"`.

- [x] **Step 13: La campana en `TopBar` — implementación**

`src/app/shell/TopBar.tsx`: añadir el import y sustituir el comentario-hueco por el componente (el `gap-2.5` de la barra ya da los 10 px).

```tsx
import { Campana } from '@/modules/taller/notificaciones/Campana'
```

```tsx
      <div className="flex-1" />
      <Campana />
      <UserMenu />
```
(se elimina la línea `{/* Campana de solicitudes (solo SUPERTECNICO): llega con el sub-proyecto 2 */}`).

```bash
npm test -- TopBar
```
Expected: PASS.

- [x] **Step 14: Tests de página que montan `<AppLayout />` con supertécnico**

Desde ahora la barra del supertécnico pide los datos de la campana y el servidor simulado rechaza lo que no tiene handler. En `src/modules/taller/pendientes/PendientesPage.test.tsx`, `src/modules/taller/historial/HistorialPage.test.tsx`, `src/modules/taller/imeis/ImeisPage.test.tsx` y `src/modules/taller/imeis/ImeiDetallePage.test.tsx`, añadir el import y una línea al principio del `beforeEach` de nivel de fichero (el que ya registra los handlers de la página):

```tsx
import { handlersNotificaciones } from '../notificaciones/test/handlers'
```

```tsx
beforeEach(() => {
  // <AppLayout/> con supertécnico monta la campana de la barra, que pide sus contadores y los componentes gestionados.
  server.use(...handlersNotificaciones())
  // …lo que ya hubiera
```

```bash
npm test
```
Expected: toda la suite en verde. Si otra prueba que monte `<AppLayout />` (o el router completo) con `SESION_SUPER` falla con una petición sin handler a `/api/solicitudes/count`, `/api/solicitudes-stock/count` o `/api/componentes/gestionados`, se le añade la misma línea.

- [x] **Step 15: Verde y commit**

```bash
npm run check
git add public/NotfON.png public/NotifOFF.png public/Badge.png src/shared/styles/tokens.css src/shared/styles/globals.css src/modules/taller/notificaciones src/app/shell/TopBar.tsx src/app/shell/TopBar.test.tsx src/modules/taller/pendientes/PendientesPage.test.tsx src/modules/taller/historial/HistorialPage.test.tsx src/modules/taller/imeis/ImeisPage.test.tsx src/modules/taller/imeis/ImeiDetallePage.test.tsx
git commit -m "feat(web): campana de notificaciones del supertécnico: contador, pulso de alertas y datos del panel"
```

---

### Task 20: Web — notificaciones (2): panel, tarjetas, acciones, menús contextuales, botones de Almacén y refresco con snapshot

**Files:**
- Create: `src/modules/taller/notificaciones/PanelNotificaciones.tsx`, `PanelNotificaciones.test.tsx`, `TarjetaSolicitud.tsx`, `TarjetaAlerta.tsx`
- Modify: `src/modules/taller/notificaciones/Campana.tsx`, `Campana.test.tsx`

**Interfaces:**
- Consumes (de la Task 19, en `src/modules/taller/notificaciones/`): `useSolicitudesPanel(abierto)`, `useComponentesGestionados(sondea)`, `useCambiarEstadoSolicitud()`, `useQuitarSolicitud()`, `useRechazarTodo()`, `CLAVE_NOTIF_SOLICITUDES`, `CLAVE_NOTIF_COMPONENTES`, `CLAVE_NOTIF_CONTADOR` (`api.ts`); `firma`, `lineaInfo`, `TarjetaDatos`, `ListasSolicitudes` (`solicitudes.ts`); `alertasOrdenadas`, `AlertaStock` (`alertas.ts`); `conRegistroNotificaciones`, `handlersNotificaciones` (`test/handlers.ts`). De otras tareas: `TOOLTIP_ALMACEN = 'Disponible con Almacén (próxima entrega)'` (`../lib/textos`, W3); `ContextMenu`, `ContextMenuTrigger`, `ContextMenuContent`, `ContextMenuItem` (`@/shared/ui/context-menu`); tokens "Task 19" de W9 y `aviso-conflicto-bg` (Task 10). Los errores de las acciones los muestra el aviso global del `QueryClient` (las mutaciones no llevan `silenciarError`), por lo que el panel no necesita llamar a `useAlerta`.
- Produces:

```ts
export function PanelNotificaciones(props: { pestanaInicial: 'solicitudes' | 'alertas'; anclaRef: RefObject<HTMLElement | null>; onCerrar: () => void }): JSX.Element
export function TarjetaSolicitud(props: { datos: TarjetaDatos; alterna: boolean; onRechazar: () => void; onRecuperar: () => void; onQuitar: () => void }): JSX.Element
export function TarjetaAlerta(props: { alerta: AlertaStock; alterna: boolean }): JSX.Element
```
  Selectores (W13): `data-testid="panel-notificaciones"`; pestañas `role="tab"` "Solicitudes" / "Alertas"; `data-testid="tarjeta-solicitud-U-<idRc>"` / `"tarjeta-solicitud-P-<idSol>"` con `data-grupo="pendiente|rechazada"`; `data-testid="tarjeta-alerta-<idCom>"`; papelera `"Borrar solicitud"`.

**Ficha** (`docs/paridad/notificaciones.md`): sección "Panel" ("Se abre con un clic…" —tamaño, posición y recolocación—, "También se cierra al pulsar en cualquier punto…", "Control segmentado…", "A la derecha de esa fila, enlace \"→ Ir a pedidos\"…"); sección "Pestaña Solicitudes" completa ("Zona con desplazamiento vertical de 370 px…", "Orden…", "Fondo alterno…", "Fechas…", "**Urgente pendiente**…", "\"Rechazar\" (urgente)…", "**Urgente rechazada**…", "\"Recuperar\" (urgente)…", "**Preventiva pendiente**…", "**Preventiva rechazada**…", "Errores de cualquier acción…", "Botones inferiores…", "\"Rechazar todo\"…", "\"Pedir piezas\"…"); sección "Pestaña Alertas" completa; sección "Refresco" completa; de "Diferencias aceptadas": "**Escape cierra el panel**…".

- [x] **Step 1: Tarjetas — tests que fallan**

`src/modules/taller/notificaciones/PanelNotificaciones.test.tsx` (primer bloque; los siguientes pasos añaden más `describe` al mismo fichero). Todo se prueba a través de `<Campana />`, que es quien monta el panel en producción.

```tsx
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_SUPER } from '@/test/render'
import { server } from '@/test/server'
import { TOOLTIP_ALMACEN } from '../lib/textos'
import { componente, solicitudPreventiva, solicitudUrgente } from '../test/fabrica'
import { CLAVE_NOTIF_COMPONENTES, CLAVE_NOTIF_SOLICITUDES } from './api'
import { Campana } from './Campana'
import { conRegistroNotificaciones, type EscenarioNotificaciones } from './test/handlers'

const control = vi.hoisted(() => ({ intervalo: null as number | null }))
vi.mock('@/shared/api/refresco', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/shared/api/refresco')>()
  function useIntervaloRefresco(activo = true): number | false {
    const normal = real.useIntervaloRefresco(activo)
    return control.intervalo !== null && activo ? control.intervalo : normal
  }
  return { ...real, useIntervaloRefresco }
})

function espiarGets(): string[] {
  const gets: string[] = []
  server.events.on('request:start', ({ request }) => {
    if (request.method !== 'GET') return
    const url = new URL(request.url)
    gets.push(url.pathname + url.search)
  })
  return gets
}
const cuantas = (gets: string[], ruta: string) => gets.filter((g) => g === ruta).length

afterEach(() => {
  control.intervalo = null
  server.events.removeAllListeners()
  vi.restoreAllMocks()
})

const ESCENARIO: EscenarioNotificaciones = {
  urgPend: [solicitudUrgente({ idRc: 501, descripcion: 'Batería hinchada' }), solicitudUrgente({ idRc: 502, tipoComponente: null })],
  prevPend: [solicitudPreventiva({ idSol: 701, descripcion: 'Quedan pocas' })],
  urgRech: [solicitudUrgente({ idRc: 503, descripcion: 'No se debe ver' })],
  prevRech: [solicitudPreventiva({ idSol: 702, descripcion: 'Tampoco se ve' })],
  gestionados: [
    componente({ idCom: 101, tipo: 'bati13', stock: 1, stockMinimo: 2 }),
    componente({ idCom: 102, tipo: 'bati14', stock: 0, stockMinimo: 2 }),
    componente({ idCom: 112, tipo: 'lcdi14', stock: 3, stockMinimo: 1 }),
  ],
}

/** Monta la campana del supertécnico y abre el panel. Con alertas en el escenario el pulso late y la primera apertura cae
 *  en "Alertas"; `pestana` deja el panel en la pestaña pedida. */
async function abrirPanel(escenario: EscenarioNotificaciones = ESCENARIO, pestana: 'Solicitudes' | 'Alertas' = 'Solicitudes') {
  const registro = conRegistroNotificaciones(escenario)
  server.use(...registro.handlers)
  const resultado = renderConProviders(<Campana />, { sesion: SESION_SUPER })
  await userEvent.click(screen.getByTestId('campana'))
  await userEvent.click(screen.getByRole('tab', { name: pestana }))
  return { ...resultado, llamadas: registro.llamadas }
}
const tarjeta = (id: string) => screen.findByTestId(`tarjeta-solicitud-${id}`)

describe('tarjetas de solicitudes (ficha notificaciones.md, "Pestaña Solicitudes")', () => {
  it('urgente pendiente: avatar con inicial, SKU, "⚠", línea "<técnico>  ·  <fecha>  ·  <idRep>", descripción y "Rechazar"', async () => {
    await abrirPanel()
    const t = await tarjeta('U-501')
    expect(t).toHaveAttribute('data-grupo', 'pendiente')
    expect(t).toHaveClass('bg-superficie', 'rounded-md', 'p-2.5', 'gap-2.5')
    expect(within(t).getByText('B')).toHaveClass('h-9', 'w-9', 'rounded-full', 'bg-badge-neutro-bg', 'text-[13px]', 'font-bold', 'text-azul-gris')
    expect(within(t).getByText('bati14')).toHaveClass('text-[13px]', 'font-bold', 'text-azul-medio')
    expect(within(t).getByText('⚠')).toHaveClass('bg-aviso-conflicto-bg', 'text-notif-urgente-text', 'text-[10px]', 'font-bold')
    const info = within(t).getByText(/Técnico A/)
    expect(info.textContent).toBe('Técnico A  ·  16/09/2026 09:02  ·  A20260916_1')
    expect(info).toHaveClass('whitespace-pre-wrap', 'text-[11px]', 'text-texto-fecha-inicio')
    expect(within(t).getByText('Batería hinchada')).toHaveClass('whitespace-pre-line', 'text-[11px]', 'text-azul-gris')
    expect(within(t).queryByText(/355400000000111/)).not.toBeInTheDocument()
    expect(within(t).getByRole('button', { name: 'Rechazar' })).toHaveClass('bg-notif-rechazar-bg', 'text-notif-rechazar-text', 'rounded-[20px]', 'text-[11px]')
    expect(within(t).queryByRole('button', { name: 'Borrar solicitud' })).not.toBeInTheDocument()
    // Sin SKU: inicial "?"
    expect(within(await tarjeta('U-502')).getByText('?')).toBeInTheDocument()
  })
  it('urgente rechazada: sin fecha ni descripción, "Recuperar" y papelera', async () => {
    await abrirPanel()
    const t = await tarjeta('U-503')
    expect(t).toHaveAttribute('data-grupo', 'rechazada')
    expect(t).toHaveClass('bg-notif-rechazada-bg', 'p-2')
    expect(within(t).getByText('B')).toHaveClass('bg-notif-avatar-apagado', 'text-notif-texto-apagado')
    expect(within(t).getByText('bati14')).toHaveClass('font-normal', 'text-texto-fecha-inicio')
    expect(within(t).getByText('⚠')).toHaveClass('bg-notif-urgente-apagado-bg', 'text-notif-urgente-apagado-text')
    expect(within(t).getByText(/Técnico A/).textContent).toBe('Técnico A  ·  A20260916_1')
    expect(within(t).getByText(/Técnico A/)).toHaveClass('text-notif-texto-apagado')
    expect(within(t).queryByText('No se debe ver')).not.toBeInTheDocument()
    expect(within(t).getByRole('button', { name: 'Recuperar' })).toHaveClass('bg-azul-medio', 'text-superficie', 'rounded-[20px]', 'text-[11px]')
    const papelera = within(t).getByRole('button', { name: 'Borrar solicitud' })
    expect(papelera.querySelector('img')).toHaveAttribute('src', '/borrar.png')
    expect(papelera.querySelector('img')).toHaveClass('h-[18px]', 'w-[18px]', 'opacity-50')
  })
  it('preventiva pendiente y rechazada según la ficha: sin "⚠", "<usuario>  ·  <fecha>"; la rechazada conserva la fecha y pierde la descripción', async () => {
    await abrirPanel()
    const pendiente = await tarjeta('P-701')
    expect(within(pendiente).queryByText('⚠')).not.toBeInTheDocument()
    expect(within(pendiente).getByText('L')).toBeInTheDocument()
    expect(within(pendiente).getByText('lcdi13')).toBeInTheDocument()
    expect(within(pendiente).getByText(/tecnico_n/).textContent).toBe('tecnico_n  ·  16/09/2026 11:30')
    expect(within(pendiente).getByText('Quedan pocas')).toBeInTheDocument()
    expect(within(pendiente).getByRole('button', { name: 'Rechazar' })).toBeInTheDocument()
    const rechazada = await tarjeta('P-702')
    expect(within(rechazada).queryByText('⚠')).not.toBeInTheDocument()
    expect(within(rechazada).getByText(/tecnico_n/).textContent).toBe('tecnico_n  ·  16/09/2026 11:30')
    expect(within(rechazada).queryByText('Tampoco se ve')).not.toBeInTheDocument()
    expect(within(rechazada).getByRole('button', { name: 'Recuperar' })).toBeInTheDocument()
    expect(within(rechazada).getByRole('button', { name: 'Borrar solicitud' })).toBeInTheDocument()
  })
  it('fondo alterno con un único contador por lista (compartido entre urgentes y preventivas)', async () => {
    await abrirPanel()
    expect(await tarjeta('U-501')).toHaveClass('bg-superficie')
    expect(await tarjeta('U-502')).toHaveClass('bg-notif-tarjeta-alt')
    expect(await tarjeta('P-701')).toHaveClass('bg-superficie')
    expect(await tarjeta('U-503')).toHaveClass('bg-notif-rechazada-bg')
    expect(await tarjeta('P-702')).toHaveClass('bg-notif-rechazada-alt')
  })
})
```

```bash
npm test -- PanelNotificaciones
```
Expected: FAIL — no existe `role="tab"` (el panel de la Task 19 es un contenedor vacío).

- [x] **Step 2: `TarjetaSolicitud` y `TarjetaAlerta`**

`src/modules/taller/notificaciones/TarjetaSolicitud.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuTrigger } from '@/shared/ui/context-menu'
import { lineaInfo, type TarjetaDatos } from './solicitudes'

type Props = { datos: TarjetaDatos; alterna: boolean; onRechazar: () => void; onRecuperar: () => void; onQuitar: () => void }

const PILDORA = 'cursor-pointer rounded-[20px] px-3.5 py-1.5 text-[11px]'

/** Tarjeta de una solicitud (urgente o preventiva, pendiente o rechazada). Ninguna acción pide confirmación. El menú
 *  contextual repite la acción principal; la papelera no está en el menú. Las rechazadas nunca muestran la descripción. */
export function TarjetaSolicitud({ datos, alterna, onRechazar, onRecuperar, onQuitar }: Props) {
  const pendiente = datos.grupo === 'pendiente'
  const sku = datos.sol.tipoComponente ?? ''
  const inicial = sku.trim() === '' ? '?' : sku.trim()[0].toUpperCase()
  const descripcion = pendiente ? datos.sol.descripcion : null
  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>
        <div
          data-testid={`tarjeta-solicitud-${datos.clase}-${datos.id}`}
          data-grupo={datos.grupo}
          className={cn(
            'flex items-center gap-2.5 rounded-md',
            pendiente ? 'p-2.5' : 'p-2',
            pendiente ? (alterna ? 'bg-notif-tarjeta-alt' : 'bg-superficie') : alterna ? 'bg-notif-rechazada-alt' : 'bg-notif-rechazada-bg',
          )}
        >
          <span
            className={cn(
              'flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[13px] font-bold',
              pendiente ? 'bg-badge-neutro-bg text-azul-gris' : 'bg-notif-avatar-apagado text-notif-texto-apagado',
            )}
          >
            {inicial}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <span className={cn('text-[13px]', pendiente ? 'font-bold text-azul-medio' : 'font-normal text-texto-fecha-inicio')}>{sku}</span>
              {datos.clase === 'U' && (
                <span
                  className={cn(
                    'rounded px-[5px] py-px text-[10px] font-bold',
                    pendiente ? 'bg-aviso-conflicto-bg text-notif-urgente-text' : 'bg-notif-urgente-apagado-bg text-notif-urgente-apagado-text',
                  )}
                >
                  ⚠
                </span>
              )}
            </div>
            {/* pre-wrap: el separador "  ·  " lleva dobles espacios que HTML colapsaría */}
            <p className={cn('text-[11px] whitespace-pre-wrap', pendiente ? 'text-texto-fecha-inicio' : 'text-notif-texto-apagado')}>{lineaInfo(datos)}</p>
            {descripcion && <p className="text-[11px] whitespace-pre-line text-azul-gris">{descripcion}</p>}
          </div>
          {pendiente ? (
            <button type="button" onClick={onRechazar} className={cn(PILDORA, 'bg-notif-rechazar-bg text-notif-rechazar-text')}>
              Rechazar
            </button>
          ) : (
            <>
              <button type="button" onClick={onRecuperar} className={cn(PILDORA, 'bg-azul-medio text-superficie')}>
                Recuperar
              </button>
              <button type="button" aria-label="Borrar solicitud" onClick={onQuitar} className="cursor-pointer">
                <img src="/borrar.png" alt="" className="h-[18px] w-[18px] opacity-50" />
              </button>
            </>
          )}
        </div>
      </ContextMenuTrigger>
      <ContextMenuContent>
        {pendiente ? (
          <ContextMenuItem onSelect={onRechazar}>Rechazar solicitud</ContextMenuItem>
        ) : (
          <ContextMenuItem onSelect={onRecuperar}>Recuperar solicitud</ContextMenuItem>
        )}
      </ContextMenuContent>
    </ContextMenu>
  )
}
```

`src/modules/taller/notificaciones/TarjetaAlerta.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'
import { TOOLTIP_ALMACEN } from '../lib/textos'
import type { AlertaStock } from './alertas'

/** Tarjeta de una alerta de stock. El mínimo no se muestra; sin forma singular ("1 unid. restantes"); sin menú contextual.
 *  "Pedir" queda deshabilitado con tooltip hasta que exista Almacén. */
export function TarjetaAlerta({ alerta, alterna }: { alerta: AlertaStock; alterna: boolean }) {
  const sinStock = alerta.nivel === 'sinStock'
  return (
    <div data-testid={`tarjeta-alerta-${alerta.componente.idCom}`} className={cn('flex items-center gap-3 rounded-md p-3', alterna ? 'bg-notif-tarjeta-alt' : 'bg-superficie')}>
      <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[14px] font-bold text-superficie', sinStock ? 'bg-notif-sin-stock' : 'bg-notif-stock-bajo')}>
        {sinStock ? '✕' : '!'}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[14px] font-bold text-azul-medio">{alerta.componente.tipo}</p>
        <p className="flex items-center gap-1.5 text-[11px]">
          <span className={cn('font-bold', sinStock ? 'text-notif-sin-stock' : 'text-notif-stock-bajo')}>{sinStock ? 'Sin Stock' : 'Stock Bajo'}</span>
          <span className="text-texto-fecha-inicio">{sinStock ? 'Sin unidades' : `${alerta.componente.stock} unid. restantes`}</span>
        </p>
      </div>
      {/* El title va en el envoltorio: el botón deshabilitado lleva pointer-events-none y no recibiría el hover. */}
      <span title={TOOLTIP_ALMACEN} className="inline-block">
        <button type="button" disabled className="pointer-events-none rounded-[20px] bg-azul-medio px-4 py-1.5 text-[11px] text-superficie disabled:opacity-50">
          Pedir
        </button>
      </span>
    </div>
  )
}
```
Los tests del Step 1 siguen en rojo hasta el Step 3 (falta el panel que pinta las tarjetas).

- [x] **Step 3: `PanelNotificaciones` y su montaje en `Campana`**

`src/modules/taller/notificaciones/PanelNotificaciones.tsx`:

```tsx
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react'
import { cn } from '@/shared/lib/utils'
import { TOOLTIP_ALMACEN } from '../lib/textos'
import { alertasOrdenadas } from './alertas'
import { useCambiarEstadoSolicitud, useComponentesGestionados, useQuitarSolicitud, useRechazarTodo, useSolicitudesPanel } from './api'
import { firma, type ListasSolicitudes, type TarjetaDatos } from './solicitudes'
import { TarjetaAlerta } from './TarjetaAlerta'
import { TarjetaSolicitud } from './TarjetaSolicitud'

type Pestana = 'solicitudes' | 'alertas'
type Props = { pestanaInicial: Pestana; anclaRef: RefObject<HTMLElement | null>; onCerrar: () => void }

const ANCHO = 480
const SEPARACION = 6
const VACIAS: ListasSolicitudes = { pendientes: [], rechazadas: [] }
/** Capas que viven en un portal fuera del panel: un clic o un Escape dentro de ellas no es "fuera del panel". */
const CAPAS = '[data-slot="context-menu-content"], [role="dialog"], [data-slot="dialog-overlay"]'
const PESTANAS: { clave: Pestana; texto: string }[] = [
  { clave: 'solicitudes', texto: 'Solicitudes' },
  { clave: 'alertas', texto: 'Alertas' },
]
const BOTON_INFERIOR = 'w-full rounded-[20px] p-[11px] text-[13px] font-bold'

/** Botón reservado para Almacén: visible, deshabilitado y con tooltip en un envoltorio que sí recibe el hover. */
function BotonAlmacen({ texto, className, envoltorio }: { texto: string; className: string; envoltorio?: string }) {
  return (
    <span title={TOOLTIP_ALMACEN} className={envoltorio ?? 'inline-block'}>
      <button type="button" disabled className={cn('pointer-events-none disabled:opacity-50', className)}>
        {texto}
      </button>
    </span>
  )
}

/** Panel flotante de la campana: 480 px, sin cabecera ni botón de cerrar, no modal (sin overlay ni trampa de foco). Borde
 *  derecho alineado con el de la campana y 6 px por debajo; se recoloca al cambiar el tamaño de la ventana. Se cierra al
 *  pulsar fuera del panel y de la campana, y con Escape. */
export function PanelNotificaciones({ pestanaInicial, anclaRef, onCerrar }: Props) {
  const panelRef = useRef<HTMLDivElement>(null)
  const [pestana, setPestana] = useState<Pestana>(pestanaInicial)
  // Copia que pintan las tarjetas: solo se sustituye cuando cambia el conjunto de identificadores con su grupo y clase.
  const [pintadas, setPintadas] = useState<ListasSolicitudes>(VACIAS)
  const solicitudes = useSolicitudesPanel(true)
  const componentes = useComponentesGestionados(true)
  const cambiarEstado = useCambiarEstadoSolicitud()
  const quitar = useQuitarSolicitud()
  const rechazarTodo = useRechazarTodo()

  // Ajuste de estado durante el render: un sondeo que solo trae otra descripción, técnico o fecha no repinta.
  if (solicitudes.data && firma(solicitudes.data) !== firma(pintadas)) setPintadas(solicitudes.data)
  // Las alertas se repintan siempre; si un sondeo falla, `data` conserva la última lista buena.
  const alertas = alertasOrdenadas(componentes.data ?? [])

  useLayoutEffect(() => {
    function colocar() {
      const ancla = anclaRef.current
      const panel = panelRef.current
      if (!ancla || !panel) return
      const r = ancla.getBoundingClientRect()
      panel.style.top = `${r.bottom + SEPARACION}px`
      panel.style.left = `${r.right - ANCHO}px`
    }
    colocar()
    window.addEventListener('resize', colocar)
    return () => window.removeEventListener('resize', colocar)
  }, [anclaRef])

  useEffect(() => {
    function alPulsar(e: PointerEvent) {
      const destino = e.target
      if (!(destino instanceof Element)) return
      if (panelRef.current?.contains(destino) || anclaRef.current?.contains(destino)) return
      if (destino.closest(CAPAS)) return
      onCerrar()
    }
    function alTeclear(e: KeyboardEvent) {
      if (e.key !== 'Escape') return
      // Con un menú contextual o un aviso encima, Escape es suyo.
      if (document.querySelector(CAPAS)) return
      onCerrar()
    }
    document.addEventListener('pointerdown', alPulsar)
    document.addEventListener('keydown', alTeclear)
    return () => {
      document.removeEventListener('pointerdown', alPulsar)
      document.removeEventListener('keydown', alTeclear)
    }
  }, [anclaRef, onCerrar])

  const tarjeta = (t: TarjetaDatos, i: number) => (
    <TarjetaSolicitud
      key={`${t.clase}-${t.id}`}
      datos={t}
      alterna={i % 2 === 1}
      onRechazar={() => cambiarEstado.mutate({ clase: t.clase, id: t.id, estado: 'RECHAZADA' })}
      onRecuperar={() => cambiarEstado.mutate({ clase: t.clase, id: t.id, estado: 'PENDIENTE' })}
      onQuitar={() => quitar.mutate({ clase: t.clase, id: t.id })}
    />
  )

  return (
    <div ref={panelRef} data-testid="panel-notificaciones" className="fixed z-40 flex w-[480px] flex-col gap-3 border border-notif-panel-brd bg-fondo-vista p-5">
      <div className="flex items-center">
        <div role="tablist" className="flex rounded-[20px] border border-notif-segmento-brd bg-superficie p-[3px]">
          {PESTANAS.map((p) => (
            <button
              key={p.clave}
              type="button"
              role="tab"
              aria-selected={pestana === p.clave}
              onClick={() => setPestana(p.clave)}
              className={cn('cursor-pointer px-[18px] py-[7px] text-[12px]', pestana === p.clave ? 'rounded-[17px] bg-azul-medio font-bold text-superficie' : 'text-azul-gris')}
            >
              {p.texto}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <BotonAlmacen texto="→ Ir a pedidos" className="text-[12px] font-bold text-azul-noche" />
      </div>

      {pestana === 'solicitudes' ? (
        <>
          <div role="tabpanel" aria-label="Solicitudes" className="h-[370px] overflow-x-hidden overflow-y-auto bg-fondo-vista">
            <h2 className="mb-1.5 text-[14px] font-bold text-azul-medio">Solicitudes de pieza</h2>
            <div className="flex flex-col gap-1.5">{pintadas.pendientes.map(tarjeta)}</div>
            <h3 className="mt-3 mb-1.5 text-[12px] font-bold text-texto-fecha-inicio">Rechazadas</h3>
            <div className="flex flex-col gap-1.5">{pintadas.rechazadas.map(tarjeta)}</div>
          </div>
          <div className="flex gap-2">
            <BotonAlmacen texto="Pedir piezas" envoltorio="flex-1" className={cn(BOTON_INFERIOR, 'bg-azul-medio text-superficie')} />
            <button type="button" onClick={() => rechazarTodo.mutate()} className={cn(BOTON_INFERIOR, 'flex-1 cursor-pointer bg-notif-rechazar-bg text-notif-rechazar-text')}>
              Rechazar todo
            </button>
          </div>
        </>
      ) : (
        <>
          <div role="tabpanel" aria-label="Alertas" className="h-[320px] overflow-x-hidden overflow-y-auto bg-fondo-vista">
            <h2 className="mb-1.5 text-[16px] font-bold text-azul-medio">Alertas de Stock</h2>
            {alertas.length === 0 ? (
              <p className="text-[13px] text-texto-fecha-inicio">Sin alertas de stock</p>
            ) : (
              <div className="flex flex-col gap-1.5">
                {alertas.map((a, i) => (
                  <TarjetaAlerta key={a.componente.idCom} alerta={a} alterna={i % 2 === 1} />
                ))}
              </div>
            )}
          </div>
          <div className="flex gap-2">
            <BotonAlmacen texto="Pedir todas las piezas" envoltorio="flex-1" className={cn(BOTON_INFERIOR, 'bg-azul-medio text-superficie')} />
            <BotonAlmacen texto="Ver Stock Completo" envoltorio="flex-1" className={cn(BOTON_INFERIOR, 'bg-azul-medio text-superficie')} />
          </div>
        </>
      )}
    </div>
  )
}
```

`src/modules/taller/notificaciones/Campana.tsx` — tres cambios:

1. Imports: `useCallback` junto a `useRef, useState`, y `import { PanelNotificaciones } from './PanelNotificaciones'`.
2. Debajo de `alternar`, el cierre que usa el panel (estable, para que el panel no vuelva a suscribir sus escuchas en cada render):

```tsx
  // Cierre desde el panel (clic fuera o Escape): también recalcula el contador.
  const cerrar = useCallback(() => {
    setAbierto(false)
    void qc.invalidateQueries({ queryKey: CLAVE_NOTIF_CONTADOR })
  }, [qc])
```
3. El contenedor vacío se sustituye por el panel (y se borra su comentario):

```tsx
      {abierto && <PanelNotificaciones pestanaInicial={pestanaInicial} anclaRef={anclaRef} onCerrar={cerrar} />}
```

`src/modules/taller/notificaciones/Campana.test.tsx` — el panel ya no lleva `data-pestana`: la pestaña de apertura se lee de la pestaña seleccionada. Sustituir el ayudante:

```tsx
/** Pestaña con la que se ha abierto el panel. */
const pestanaAbierta = () => screen.getByRole('tab', { selected: true }).textContent?.toLowerCase()
```
Ningún otro cambio: `handlersNotificaciones()` ya responde a las cuatro listas que pide el panel al abrirse.

```bash
npm test -- notificaciones
```
Expected: PASS — los 4 tests de tarjetas del Step 1 y los de la Task 19 (`Campana.test.tsx` incluido).

- [x] **Step 4: Panel (posición, cierre, segmentado, títulos y botones de Almacén) — tests**

Añadir a `PanelNotificaciones.test.tsx`:

```tsx
describe('panel (ficha notificaciones.md, "Panel")', () => {
  const rect = (right: number): DOMRect => ({ x: right - 34, y: 15, left: right - 34, top: 15, right, bottom: 49, width: 34, height: 34, toJSON: () => ({}) })

  it('panel de 480 px anclado a la campana; se recoloca con la ventana', async () => {
    const espia = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(rect(900))
    await abrirPanel({})
    const panel = screen.getByTestId('panel-notificaciones')
    expect(panel).toHaveClass('fixed', 'w-[480px]', 'bg-fondo-vista', 'border', 'border-notif-panel-brd', 'p-5', 'gap-3')
    expect(panel.style.top).toBe('55px')
    expect(panel.style.left).toBe('420px')
    espia.mockReturnValue(rect(700))
    fireEvent(window, new Event('resize'))
    expect(panel.style.left).toBe('220px')
  })
  it('clic fuera y Escape lo cierran; clic dentro no', async () => {
    await abrirPanel({})
    await userEvent.click(screen.getByRole('heading', { name: 'Solicitudes de pieza' }))
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
    await userEvent.click(document.body)
    expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument()
    await userEvent.click(screen.getByTestId('campana'))
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByTestId('panel-notificaciones')).not.toBeInTheDocument()
  })
  it('segmentado: "Solicitudes" activo por defecto; cambiar de pestaña no vuelve a pedir datos', async () => {
    const gets = espiarGets()
    const registro = conRegistroNotificaciones({})
    server.use(...registro.handlers)
    const { queryClient } = renderConProviders(<Campana />, { sesion: SESION_SUPER })
    await userEvent.click(screen.getByTestId('campana'))
    const solicitudes = screen.getByRole('tab', { name: 'Solicitudes' })
    const alertas = screen.getByRole('tab', { name: 'Alertas' })
    expect(solicitudes).toHaveAttribute('aria-selected', 'true')
    expect(solicitudes).toHaveClass('bg-azul-medio', 'text-superficie', 'font-bold', 'rounded-[17px]', 'text-[12px]')
    expect(alertas).toHaveClass('text-azul-gris')
    expect(alertas).not.toHaveClass('font-bold')
    expect(solicitudes.parentElement).toHaveClass('rounded-[20px]', 'border-notif-segmento-brd', 'bg-superficie', 'p-[3px]')
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes?estado=PENDIENTE')).toBe(1))
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    const antes = gets.length
    await userEvent.click(alertas)
    expect(await screen.findByRole('heading', { name: 'Alertas de Stock' })).toBeInTheDocument()
    await userEvent.click(solicitudes)
    expect(screen.getByRole('heading', { name: 'Solicitudes de pieza' })).toBeInTheDocument()
    expect(gets.length).toBe(antes)
  })
  it('títulos "Solicitudes de pieza" y "Rechazadas" siempre visibles, sin texto de lista vacía', async () => {
    await abrirPanel({})
    expect(screen.getByRole('heading', { name: 'Solicitudes de pieza' })).toHaveClass('text-[14px]', 'font-bold', 'text-azul-medio')
    expect(screen.getByRole('heading', { name: 'Rechazadas' })).toHaveClass('text-[12px]', 'font-bold', 'text-texto-fecha-inicio')
    expect(screen.getByRole('tabpanel', { name: 'Solicitudes' })).toHaveClass('h-[370px]', 'overflow-y-auto', 'overflow-x-hidden')
    expect(screen.queryByText(/sin solicitudes/i)).not.toBeInTheDocument()
  })
  it('"→ Ir a pedidos" deshabilitado con el tooltip de Almacén en las dos pestañas', async () => {
    await abrirPanel({})
    for (const pestana of ['Solicitudes', 'Alertas']) {
      await userEvent.click(screen.getByRole('tab', { name: pestana }))
      const enlace = screen.getByRole('button', { name: '→ Ir a pedidos' })
      expect(enlace).toBeDisabled()
      expect(enlace).toHaveClass('text-[12px]', 'font-bold', 'text-azul-noche')
      expect(enlace.parentElement).toHaveAttribute('title', TOOLTIP_ALMACEN)
      expect(enlace.parentElement).not.toHaveClass('pointer-events-none')
    }
  })
  it('"Pedir piezas", "Pedir", "Pedir todas las piezas" y "Ver Stock Completo" deshabilitados con tooltip; "Rechazar todo" habilitado', async () => {
    await abrirPanel()
    expect(screen.getByRole('button', { name: 'Rechazar todo' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Rechazar todo' })).toHaveClass('bg-notif-rechazar-bg', 'text-notif-rechazar-text', 'text-[13px]', 'font-bold', 'rounded-[20px]', 'p-[11px]')
    const pedirPiezas = screen.getByRole('button', { name: 'Pedir piezas' })
    expect(pedirPiezas).toHaveClass('bg-azul-medio', 'text-superficie', 'text-[13px]', 'font-bold')
    expect(pedirPiezas).toBeDisabled()
    expect(pedirPiezas.parentElement).toHaveAttribute('title', TOOLTIP_ALMACEN)
    await userEvent.click(screen.getByRole('tab', { name: 'Alertas' }))
    const reservados = [...screen.getAllByRole('button', { name: 'Pedir' }), screen.getByRole('button', { name: 'Pedir todas las piezas' }), screen.getByRole('button', { name: 'Ver Stock Completo' })]
    expect(reservados).toHaveLength(4)
    for (const boton of reservados) {
      expect(boton).toBeDisabled()
      expect(boton.parentElement).toHaveAttribute('title', TOOLTIP_ALMACEN)
    }
  })
})
```
```bash
npm test -- PanelNotificaciones
```
Expected: PASS (la implementación del Step 3 ya lo cubre).

- [x] **Step 5: Acciones de tarjeta, menús contextuales, errores y "Rechazar todo" — tests**

Añadir a `PanelNotificaciones.test.tsx`:

```tsx
describe('acciones (sin confirmación)', () => {
  it('"Rechazar" → PATCH { estado: "RECHAZADA" } sin confirmación y recarga solicitudes, contador y alertas', async () => {
    const gets = espiarGets()
    const { llamadas } = await abrirPanel()
    const t = await tarjeta('U-501')
    await waitFor(() => expect(cuantas(gets, '/api/componentes/gestionados')).toBeGreaterThanOrEqual(1))
    const antes = { componentes: cuantas(gets, '/api/componentes/gestionados'), contador: cuantas(gets, '/api/solicitudes/count') }
    await userEvent.click(within(t).getByRole('button', { name: 'Rechazar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('tarjeta-solicitud-U-501')).toHaveAttribute('data-grupo', 'rechazada'))
    expect(llamadas).toEqual([{ metodo: 'PATCH', ruta: '/api/solicitudes/501/estado', cuerpo: { estado: 'RECHAZADA' } }])
    expect(cuantas(gets, '/api/componentes/gestionados')).toBeGreaterThan(antes.componentes)
    expect(cuantas(gets, '/api/solicitudes/count')).toBeGreaterThan(antes.contador)
    // El badge baja de 3 a 2
    await waitFor(() => expect(screen.getByTestId('campana-badge')).toHaveTextContent('2'))
  })
  it('"Recuperar" → PATCH { estado: "PENDIENTE" }, urgente y preventiva', async () => {
    const { llamadas } = await abrirPanel()
    await userEvent.click(within(await tarjeta('U-503')).getByRole('button', { name: 'Recuperar' }))
    await waitFor(() => expect(screen.getByTestId('tarjeta-solicitud-U-503')).toHaveAttribute('data-grupo', 'pendiente'))
    await userEvent.click(within(await tarjeta('P-702')).getByRole('button', { name: 'Recuperar' }))
    await waitFor(() => expect(screen.getByTestId('tarjeta-solicitud-P-702')).toHaveAttribute('data-grupo', 'pendiente'))
    expect(llamadas).toEqual([
      { metodo: 'PATCH', ruta: '/api/solicitudes/503/estado', cuerpo: { estado: 'PENDIENTE' } },
      { metodo: 'PATCH', ruta: '/api/solicitudes-stock/702/estado', cuerpo: { estado: 'PENDIENTE' } },
    ])
  })
  it('papelera urgente → PATCH limpiar; preventiva → DELETE; la solicitud deja de aparecer', async () => {
    const { llamadas } = await abrirPanel()
    await userEvent.click(within(await tarjeta('U-503')).getByRole('button', { name: 'Borrar solicitud' }))
    await waitFor(() => expect(screen.queryByTestId('tarjeta-solicitud-U-503')).not.toBeInTheDocument())
    await userEvent.click(within(await tarjeta('P-702')).getByRole('button', { name: 'Borrar solicitud' }))
    await waitFor(() => expect(screen.queryByTestId('tarjeta-solicitud-P-702')).not.toBeInTheDocument())
    expect(llamadas).toEqual([
      { metodo: 'PATCH', ruta: '/api/solicitudes/503/limpiar', cuerpo: null },
      { metodo: 'DELETE', ruta: '/api/solicitudes-stock/702', cuerpo: null },
    ])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('menú contextual: "Rechazar solicitud" / "Recuperar solicitud", sin papelera; usarlo no cierra el panel', async () => {
    const { llamadas } = await abrirPanel()
    await userEvent.pointer({ keys: '[MouseRight]', target: await tarjeta('P-701') })
    expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual(['Rechazar solicitud'])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Rechazar solicitud' }))
    await waitFor(() => expect(screen.getByTestId('tarjeta-solicitud-P-701')).toHaveAttribute('data-grupo', 'rechazada'))
    await userEvent.pointer({ keys: '[MouseRight]', target: await tarjeta('U-503') })
    expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual(['Recuperar solicitud'])
    await userEvent.click(screen.getByRole('menuitem', { name: 'Recuperar solicitud' }))
    await waitFor(() => expect(screen.getByTestId('tarjeta-solicitud-U-503')).toHaveAttribute('data-grupo', 'pendiente'))
    expect(llamadas.map((l) => `${l.ruta} ${JSON.stringify(l.cuerpo)}`)).toEqual([
      '/api/solicitudes-stock/701/estado {"estado":"RECHAZADA"}',
      '/api/solicitudes/503/estado {"estado":"PENDIENTE"}',
    ])
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
  })
  it('error de una acción: aviso con el mensaje y la lista no cambia; aceptar el aviso no cierra el panel', async () => {
    await abrirPanel()
    // Después de abrirPanel: MSW antepone cada server.use, así que este 409 gana a los handlers del escenario
    server.use(http.patch('*/api/solicitudes/501/estado', () => HttpResponse.json({ message: 'La solicitud ya fue gestionada' }, { status: 409 })))
    await userEvent.click(within(await tarjeta('U-501')).getByRole('button', { name: 'Rechazar' }))
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('La solicitud ya fue gestionada')
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    expect(screen.getByTestId('panel-notificaciones')).toBeInTheDocument()
    expect(screen.getByTestId('tarjeta-solicitud-U-501')).toHaveAttribute('data-grupo', 'pendiente')
  })
  it('"Rechazar todo": pide las pendientes, rechaza urgentes y luego preventivas una a una, y no recarga las alertas', async () => {
    const gets = espiarGets()
    const { llamadas, queryClient } = await abrirPanel()
    await tarjeta('U-501')
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    const componentesAntes = cuantas(gets, '/api/componentes/gestionados')
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar todo' }))
    await waitFor(() => expect(screen.getByTestId('tarjeta-solicitud-P-701')).toHaveAttribute('data-grupo', 'rechazada'))
    expect(llamadas.map((l) => l.ruta)).toEqual(['/api/solicitudes/501/estado', '/api/solicitudes/502/estado', '/api/solicitudes-stock/701/estado'])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(cuantas(gets, '/api/componentes/gestionados')).toBe(componentesAntes)
    await waitFor(() => expect(screen.queryByTestId('campana-badge')).not.toBeInTheDocument())
  })
  it('"Rechazar todo" se detiene en el primer error, lo muestra y no recarga', async () => {
    const { llamadas } = await abrirPanel()
    await tarjeta('U-501')
    server.use(http.patch('*/api/solicitudes/502/estado', () => HttpResponse.json({ message: 'La solicitud ya fue gestionada' }, { status: 409 })))
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar todo' }))
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('La solicitud ya fue gestionada')
    expect(llamadas.map((l) => l.ruta)).toEqual(['/api/solicitudes/501/estado'])
    // No recarga: la 501 ya está rechazada en el servidor pero el panel la sigue pintando como pendiente
    expect(screen.getByTestId('tarjeta-solicitud-U-501')).toHaveAttribute('data-grupo', 'pendiente')
  })
  it('"Rechazar todo" con la lista vacía no escribe nada', async () => {
    const { llamadas, queryClient } = await abrirPanel({})
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar todo' }))
    await waitFor(() => expect(queryClient.isMutating()).toBe(0))
    expect(llamadas).toEqual([])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
```
```bash
npm test -- PanelNotificaciones
```
Expected: PASS (la implementación del Step 3 ya cubre estas acciones). Si algún test falla, se corrige la implementación, no el test: los textos, rutas y cuerpos son los de la ficha.

- [x] **Step 6: Pestaña Alertas y refresco con snapshot — tests**

Añadir a `PanelNotificaciones.test.tsx`:

```tsx
describe('pestaña Alertas', () => {
  it('alertas: "Sin alertas de stock" cuando no hay', async () => {
    await abrirPanel({ gestionados: [componente({ stock: 5, stockMinimo: 2 })] }, 'Alertas')
    expect(screen.getByRole('heading', { name: 'Alertas de Stock' })).toHaveClass('text-[16px]', 'font-bold', 'text-azul-medio')
    expect(await screen.findByText('Sin alertas de stock')).toHaveClass('text-[13px]', 'text-texto-fecha-inicio')
    expect(screen.getByRole('tabpanel', { name: 'Alertas' })).toHaveClass('h-[320px]', 'overflow-y-auto')
  })
  it('tarjeta "Sin Stock / Sin unidades" y "Stock Bajo / N unid. restantes", sin stock primero, fondo alterno y sin menú contextual', async () => {
    await abrirPanel(ESCENARIO, 'Alertas')
    const sinStock = await screen.findByTestId('tarjeta-alerta-102')
    const bajo = screen.getByTestId('tarjeta-alerta-101')
    expect(screen.queryByTestId('tarjeta-alerta-112')).not.toBeInTheDocument()
    expect(sinStock.compareDocumentPosition(bajo) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(sinStock).toHaveClass('bg-superficie', 'rounded-md', 'p-3', 'gap-3')
    expect(bajo).toHaveClass('bg-notif-tarjeta-alt')
    expect(within(sinStock).getByText('✕')).toHaveClass('h-10', 'w-10', 'rounded-full', 'bg-notif-sin-stock', 'text-[14px]', 'font-bold', 'text-superficie')
    expect(within(sinStock).getByText('bati14')).toHaveClass('text-[14px]', 'font-bold', 'text-azul-medio')
    expect(within(sinStock).getByText('Sin Stock')).toHaveClass('font-bold', 'text-notif-sin-stock')
    expect(within(sinStock).getByText('Sin unidades')).toHaveClass('text-texto-fecha-inicio')
    expect(within(bajo).getByText('!')).toHaveClass('bg-notif-stock-bajo')
    expect(within(bajo).getByText('Stock Bajo')).toHaveClass('text-notif-stock-bajo')
    expect(within(bajo).getByText('1 unid. restantes')).toBeInTheDocument()
    expect(within(bajo).queryByText(/mínimo/i)).not.toBeInTheDocument()
    await userEvent.pointer({ keys: '[MouseRight]', target: sinStock })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})

describe('refresco (ficha notificaciones.md, "Refresco")', () => {
  it('sondeo con el panel abierto: solicitudes, contadores y alertas', async () => {
    control.intervalo = 40
    const gets = espiarGets()
    await abrirPanel()
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes?estado=PENDIENTE')).toBeGreaterThanOrEqual(3))
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes-stock?estado=RECHAZADA')).toBeGreaterThanOrEqual(3))
    await waitFor(() => expect(cuantas(gets, '/api/componentes/gestionados')).toBeGreaterThanOrEqual(3))
    await waitFor(() => expect(cuantas(gets, '/api/solicitudes/count')).toBeGreaterThanOrEqual(3))
  })
  it('un cambio de descripción no repinta las tarjetas, un id nuevo sí; las alertas se repintan siempre', async () => {
    const { queryClient } = await abrirPanel({ urgPend: [solicitudUrgente({ idRc: 501, descripcion: 'Texto inicial' })], gestionados: [componente({ idCom: 102, tipo: 'bati14', stock: 0 })] })
    expect(await screen.findByText('Texto inicial')).toBeInTheDocument()
    const recargar = async () => {
      await act(async () => {
        await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_SOLICITUDES })
        await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_COMPONENTES })
      })
    }
    let urgentes = [solicitudUrgente({ idRc: 501, descripcion: 'Texto cambiado', nombreTecnico: 'Técnico H' })]
    server.use(
      http.get('*/api/solicitudes', ({ request }) => HttpResponse.json(new URL(request.url).searchParams.get('estado') === 'PENDIENTE' ? urgentes : [])),
      http.get('*/api/componentes/gestionados', () => HttpResponse.json([componente({ idCom: 102, tipo: 'bati14', stock: 1, stockMinimo: 2 })])),
    )
    await recargar()
    expect(screen.getByText('Texto inicial')).toBeInTheDocument()
    expect(screen.queryByText('Texto cambiado')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Alertas' }))
    expect(screen.getByText('1 unid. restantes')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Solicitudes' }))

    urgentes = [...urgentes, solicitudUrgente({ idRc: 504 })]
    await recargar()
    expect(await screen.findByTestId('tarjeta-solicitud-U-504')).toBeInTheDocument()
    expect(screen.getByText('Texto cambiado')).toBeInTheDocument()
  })
  it('fallo del sondeo: sin aviso y se conserva la última lista (solicitudes y alertas)', async () => {
    const { queryClient } = await abrirPanel()
    await tarjeta('U-501')
    await waitFor(() => expect(queryClient.isFetching()).toBe(0))
    const caido = () => new HttpResponse(null, { status: 500 })
    server.use(http.get('*/api/solicitudes', caido), http.get('*/api/solicitudes-stock', caido), http.get('*/api/componentes/gestionados', caido))
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_SOLICITUDES })
      await queryClient.invalidateQueries({ queryKey: CLAVE_NOTIF_COMPONENTES })
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('tarjeta-solicitud-U-501')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Alertas' }))
    expect(screen.getByTestId('tarjeta-alerta-102')).toBeInTheDocument()
  })
})
```

```bash
npm test -- PanelNotificaciones
```
Expected: PASS. El snapshot lo da `pintadas` (Step 3): si el test del cambio de descripción falla es que las tarjetas pintan `solicitudes.data` directamente.

- [x] **Step 7: Verde y commit**

```bash
npm run check
git add src/modules/taller/notificaciones
git commit -m "feat(web): panel de notificaciones: solicitudes y alertas con sus acciones, botones de Almacén reservados y refresco sin parpadeo"
```

---

### Task 21: Cierre — smoke e2e, capturas web, fichas marcadas, CHANGELOG, versión 0.3.0 y verificación final

**Files:**
- Create: `tests/e2e/formulario.spec.ts`
- Modify (web): `.env.e2e.example`, `README.md`, `CHANGELOG.md`, `package.json` + `package-lock.json` (0.3.0), `docs/paridad/{formulario,notificaciones,pendientes,historial,imeis}.md`
- Modify (documentación privada, fuera de los repos): el script de capturas de la documentación privada, el esqueleto de directorios de la web y el plan maestro
- Modify (raíz, al final y solo tras los merges): gitlinks + este plan con las casillas marcadas

**Interfaces:**
- Consumes: todo lo anterior; `credenciales(variableUsuario, variableClave)` de `tests/e2e/credenciales.ts` (salta el test si faltan las variables); variables de entorno `E2E_BASE_URL`, `E2E_USER`/`E2E_PASS` (supertécnico) y `TEC_USER`/`TEC_PASS` (técnico con pendientes sobre IMEI de prueba); selectores de W13 (`role="dialog"` con el título de la pestaña, botón `"Cerrar formulario"`, combo `"Filtrar por modelo"`, `data-testid="fila-<prefijo>"`, botones `"Sumar <tipo>"`, `contador-<prefijo>`, `boton-derecho-<prefijo>`, `subfila-<prefijo>` con `data-variante`, fila `data-estado="editada"`, botones `"Añadir observación"` / `"Borrar observación de <tipo>"` y diálogo `"Observación para: <tipo>"` con su cuadro `"Observación"`, `banda-borrador`, `zona-guardar`, `campana`, `campana-badge`, `panel-notificaciones`, `tarjeta-solicitud-…` con `data-grupo`).
- Produces: nada que consuman otras tareas.

**Ficha:** marcado de todas las casillas de `formulario.md` y `notificaciones.md` y de las "(sub-proyecto 2)" de `pendientes.md`, `historial.md` e `imeis.md`. Se añaden a `formulario.md`: la lectura `GET /api/reparaciones/asignaciones/{idRep}` en la tabla de llamadas; en «Diferencias aceptadas», los campos sin valor como `null`, la política global de 5xx, esa llamada extra y que `GET /api/telefonos/{imei}/modelo` se pide siempre; y en «Correcciones deliberadas respecto al JavaFX», una sexta (el agotado confirmado en la variante «límite» recuperado del borrador conserva la cantidad acotada al stock actual). A `notificaciones.md`, la precisión de que el error de carga de componentes solo se muestra en la primera carga.

**Regla de esta tarea:** el agente **no** hace `git push`, ni merges, ni tags, ni despliegues, ni abre SSH. Todo eso está en el Step 7 como «pasos del usuario, uno a uno y con su OK explícito»; el agente prepara los comandos y espera.

- [x] **Step 1: Smoke Playwright del formulario y de la campana**

`.env.e2e.example` — no cambian las variables; se añaden al final dos líneas de comentario (el resto del fichero se deja como está):

```
# formulario.spec.ts ESCRIBE (consume stock de prueba y crea una solicitud): TEC_USER debe tener una asignación de
# reparación pendiente sobre un IMEI de prueba. Ver "Smoke e2e" en el README.
```

`tests/e2e/formulario.spec.ts` (sin IMEI, usuarios ni direcciones: todo llega por entorno; sin credenciales, cada test se salta):

```ts
import { expect, test, type Page } from '@playwright/test'
import { credenciales } from './credenciales.ts'

// El test del supertécnico rechaza y recupera la solicitud que deja el del técnico: van en orden y en el mismo worker.
test.describe.configure({ mode: 'serial' })

async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
}

const esBorrador = (metodo: string) => (r: { url(): string; request(): { method(): string }; ok(): boolean }) =>
  r.request().method() === metodo && new URL(r.url()).pathname.endsWith('/borrador') && r.ok()

/**
 * Guion del técnico (spec §11). ESCRIBE: guarda una fila (consume una unidad de stock de prueba), registra una solicitud
 * de pieza y termina. Datos necesarios, creados antes con el cliente de escritorio: una asignación de reparación pendiente
 * del técnico de TEC_USER sobre un IMEI de prueba, de un modelo con al menos un tipo con stock y otro tipo con el SKU a 0.
 */
test('técnico: borrador recuperado, guardar fila, solicitar pieza y terminar', async ({ page }) => {
  const { usuario, clave } = credenciales('TEC_USER', 'TEC_PASS')
  await entrar(page, usuario, clave)
  await expect(page).toHaveURL(/\/reparaciones\/pendientes$/)

  // Abrir "Añadir reparación" de la primera asignación
  await page.getByRole('button', { name: 'Añadir reparación' }).first().click()
  await expect(page).toHaveURL(/\/reparaciones\/pendientes\/reparar\/[^/]+$/)
  const urlFormulario = page.url()
  const formulario = page.getByRole('dialog', { name: /^Nueva reparación — IMEI / })
  await expect(formulario).toBeVisible()

  // Elegir modelo (si el teléfono ya lo trae, el combo llega bloqueado y con valor)
  const modelo = formulario.getByRole('combobox', { name: 'Filtrar por modelo' })
  if (await modelo.isEnabled()) {
    await modelo.click()
    await page.getByRole('option').first().click()
  }

  // Activar la primera fila que tenga stock
  const fila = formulario
    .locator('[data-testid^="fila-"]')
    .filter({ has: page.getByRole('button', { name: /^Sumar /, disabled: false }) })
    .first()
  await expect(fila).toBeVisible()
  const prefijo = ((await fila.getAttribute('data-testid')) ?? '').replace('fila-', '')
  await fila.getByRole('button', { name: /^Sumar / }).click()
  await expect(formulario.getByTestId(`contador-${prefijo}`)).toHaveText('1')

  // Cerrar: no pregunta y vuelca el borrador en ese momento
  const volcado = page.waitForResponse(esBorrador('PUT'))
  await formulario.getByRole('button', { name: 'Cerrar formulario' }).click()
  await volcado
  await expect(page).toHaveURL(/\/reparaciones\/pendientes$/)

  // Reabrir por URL (mismo camino que F5): el borrador se recupera
  await page.goto(urlFormulario)
  await expect(formulario).toBeVisible()
  await expect(formulario.getByTestId('banda-borrador')).toContainText('✓ Borrador recuperado')
  await expect(formulario.getByTestId(`contador-${prefijo}`)).toHaveText('1')

  // "✓ Guardar fila" en dos clics
  const botonDerecho = formulario.getByTestId(`boton-derecho-${prefijo}`)
  await expect(botonDerecho).toHaveText('✓ Guardar fila')
  await botonDerecho.click()
  await expect(botonDerecho).toHaveText('✓ Confirmar')
  await botonDerecho.click()
  await expect(botonDerecho).toHaveText(/^✓ Guardada [0-9]{2}\/[0-9]{2} [0-9]{2}:[0-9]{2}$/)

  // Solicitar pieza (local) en la fila sin stock. Al confirmar, data-variante pasa a "confirmada": la sub-fila se vuelve a
  // localizar por su data-testid, porque un localizador que filtre por "sinStock" dejaría de encontrarla.
  const sinStock = formulario.locator('[data-testid^="subfila-"][data-variante="sinStock"]').first()
  await expect(sinStock).toBeVisible()
  const subfila = formulario.getByTestId((await sinStock.getAttribute('data-testid')) ?? '')
  await subfila.getByRole('button', { name: 'Solicitar pieza' }).click()
  const solicitar = page.getByRole('dialog', { name: /^Solicitar pieza — / })
  await solicitar.getByPlaceholder('Describe la pieza que necesitas (opcional)...').fill('Smoke e2e')
  await solicitar.getByRole('button', { name: 'Confirmar: solicitar pieza' }).click()
  await expect(subfila).toHaveAttribute('data-variante', 'confirmada')
  await expect(subfila).toContainText('✓  Solicitud de reposición pendiente — Smoke e2e')

  // "Terminar asignación" en dos clics y vuelta a la lista
  const terminar = formulario.getByTestId('zona-guardar').getByRole('button')
  await expect(terminar).toHaveText('Terminar asignación')
  await terminar.click()
  await expect(terminar).toHaveText('✓  Confirmar terminar')
  await terminar.click()
  await expect(page).toHaveURL(/\/reparaciones\/pendientes$/)
  await expect(formulario).toBeHidden()
})

/** Guion del supertécnico (spec §11): campana con badge, "Rechazar", "Recuperar", "Editar" desde Historial y salir sin guardar. */
test('supertécnico: campana con badge, rechazar y recuperar, editar y salir sin guardar', async ({ page }) => {
  const { usuario, clave } = credenciales('E2E_USER', 'E2E_PASS')
  await entrar(page, usuario, clave)
  await expect(page).toHaveURL(/\/reparaciones\/historial$/)

  // La solicitud que dejó el técnico enciende el badge
  await expect(page.getByTestId('campana-badge')).toHaveText(/^[1-9][0-9]*$/)
  await page.getByTestId('campana').click()
  const panel = page.getByTestId('panel-notificaciones')
  await expect(panel).toBeVisible()
  await panel.getByRole('tab', { name: 'Solicitudes' }).click()

  const pendiente = panel.locator('[data-testid^="tarjeta-solicitud-"][data-grupo="pendiente"]').first()
  await expect(pendiente).toBeVisible()
  const idTarjeta = (await pendiente.getAttribute('data-testid')) ?? ''
  const tarjeta = panel.getByTestId(idTarjeta)
  await tarjeta.getByRole('button', { name: 'Rechazar' }).click()
  await expect(tarjeta).toHaveAttribute('data-grupo', 'rechazada')
  await tarjeta.getByRole('button', { name: 'Recuperar' }).click()
  await expect(tarjeta).toHaveAttribute('data-grupo', 'pendiente')
  await page.keyboard.press('Escape')
  await expect(panel).toBeHidden()

  // "Editar" desde el Historial
  await expect(page.getByRole('heading', { name: 'Historial de reparaciones' })).toBeVisible()
  const primeraFila = page.getByRole('table').locator('tr[aria-selected]').first()
  await expect(primeraFila).toBeVisible()
  await primeraFila.getByRole('cell').first().click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Editar' }).click()
  await expect(page).toHaveURL(/\/reparaciones\/historial\/editar\/[^/]+$/)
  const edicion = page.getByRole('dialog', { name: /^Editar reparación — / })
  await expect(edicion).toBeVisible()

  // Con una pieza editada se provoca un cambio y se sale sin guardar; una acción "otro" no tiene fila editada y cierra sin más.
  // El cambio es la observación, el único control de la fila editada que está siempre habilitado ("Reutilizado" va
  // deshabilitado con cantidad > 0 y "+" con el stock a 0) y que nunca deja el cambio como inválido.
  const editada = edicion.locator('[data-estado="editada"]')
  if ((await editada.count()) > 0) {
    const papelera = editada.getByRole('button', { name: /^Borrar observación de / })
    if ((await papelera.count()) > 0) {
      await papelera.click()
    } else {
      await editada.getByRole('button', { name: 'Añadir observación' }).click()
      const observacion = page.getByRole('dialog', { name: /^Observación para: / })
      await observacion.getByRole('textbox', { name: 'Observación' }).fill('Smoke e2e')
      await observacion.getByRole('button', { name: 'Guardar', exact: true }).click()
    }
    await expect(edicion.getByTestId('zona-guardar')).toBeVisible()
    await edicion.getByRole('button', { name: 'Cerrar formulario' }).click()
    await page.getByRole('button', { name: 'Salir sin guardar' }).click()
  } else {
    await edicion.getByRole('button', { name: 'Cerrar formulario' }).click()
  }
  await expect(page).toHaveURL(/\/reparaciones\/historial$/)
  await expect(edicion).toBeHidden()
})
```
Se ejecuta en el Step 7 contra la web desplegada (necesita el servidor de esta rama), no ahora. Ahora solo se comprueba que compila y que sin credenciales se salta:

```bash
npx playwright test tests/e2e/formulario.spec.ts --list
env -u TEC_USER -u TEC_PASS -u E2E_USER -u E2E_PASS npx playwright test tests/e2e/formulario.spec.ts
```
Expected: la lista muestra los 2 tests; la segunda orden termina con `2 skipped`.

- [x] **Step 2: CHANGELOG, README y versión 0.3.0**

`CHANGELOG.md`, encima de `## [0.2.0]` (la fecha se pone el día del tag):

```markdown
## [0.3.0] - <fecha del tag> — Formulario de reparación y campana

- Formulario de reparación como diálogo gobernado por la URL, en tres modos: nueva reparación y glass desde Pendientes ("Añadir reparación" / "Añadir glass") y edición desde Historial e IMEIs ("Editar", solo supertécnico).
- Filas por tipo de componente con filtro por modelo, SKU coloreado por stock, cantidad, "Reutilizado" y observación; "✓ Guardar fila" en dos clics.
- Solicitud de pieza desde la fila (sin stock y stock al límite), solicitudes ya guardadas con sus estados (pendiente, en camino, recibido, rechazada) y "Otras acciones".
- "Terminar asignación" y "Guardar cambios" en dos clics; en edición, previsión de stock, "Salir sin guardar" y aviso de modificación concurrente sin recarga automática.
- Borrador persistente por asignación, compatible con el del cliente de escritorio: autoguardado a los 2 s, volcado al cerrar y banda "✓ Borrador recuperado".
- Campana de notificaciones del supertécnico: contador de solicitudes pendientes, pulso de alertas de stock y panel con Solicitudes (rechazar, recuperar, quitar, "Rechazar todo") y Alertas; refresco cada 60 s sin parpadeo. Las acciones de Almacén quedan visibles y deshabilitadas hasta esa entrega.
- Compartidos: `ComboNavy`, avisos multilínea que devuelven el foco al cerrar, `renderConRouter` para tests con data router y consultas con `meta.silenciarError`.
- Servidor: el técnico de las escrituras del formulario se toma del token y la asignación debe ser propia; la edición de reparaciones y el ajuste de stock son del supertécnico; roles por método en las solicitudes de stock; la asignación pasa a chasis al usar o pedir una pieza `cha`; contrato con nullabilidad y respuestas tipadas para lo que la web consume.
- Diferencias aceptadas, correcciones deliberadas y comportamientos calcados: secciones del mismo nombre en `docs/paridad/formulario.md` y `docs/paridad/notificaciones.md`.
```

`README.md`:
- En "Smoke e2e (Playwright)", a continuación de la frase de `taller.spec.ts`, añadir: "`formulario.spec.ts` **escribe**: con `TEC_USER` abre una asignación de reparación pendiente, guarda una fila (consume una unidad de stock), registra una solicitud de pieza y termina; con `E2E_USER` rechaza y recupera esa solicitud desde la campana y abre "Editar" del Historial sin guardar. Solo se ejecuta contra un entorno con usuarios y datos de prueba, con una asignación pendiente recién creada sobre un IMEI de prueba, de un modelo que tenga un tipo con stock y otro con el SKU a 0. Sin credenciales en el entorno, los tests se saltan."
- En "Documentación": añadir la línea "- Spec del formulario de reparación y la campana: repo raíz, `docs/superpowers/specs/2026-09-19-web-formulario-design.md`." y cambiar el paréntesis de las fichas a "(taller: `docs/paridad/{pendientes,historial,imeis,formulario,notificaciones}.md`)".

Versión (mismo mecanismo que en 0.2.0: `package.json` y `package-lock.json`; la barra superior la toma de `package.json` en el build):

```bash
npm version 0.3.0 --no-git-tag-version
git diff --stat package.json package-lock.json
```
Expected: `package.json` con `"version": "0.3.0"` y `package-lock.json` con sus dos apariciones de la versión del paquete raíz actualizadas.

- [x] **Step 3: Fichas de paridad y commit**

1. Recorrer `docs/paridad/formulario.md` y `docs/paridad/notificaciones.md` casilla a casilla, y las tres casillas "(sub-proyecto 2)" de `pendientes.md`, `historial.md` e `imeis.md`: `[x]` si la cubre un test o la implementación (cada tarea del plan dice qué casillas cubre). Lo que no se cumpla se corrige antes del commit o se lleva a «Diferencias aceptadas» con el visto bueno del usuario; no se deja `[ ]` sin explicación.
2. `docs/paridad/formulario.md`, tabla "Llamadas a la API": nueva primera fila.

```markdown
| `GET /api/reparaciones/asignaciones/{idRep}` | → resumen de la asignación (se usa `imei`) | Al abrir, flujo nuevo y Glass, antes que las demás: la URL solo trae el id de la asignación (F5 y acceso directo). El JavaFX no la necesitaba porque recibía la fila de la lista |
```
   En la misma tabla, la fila de `GET /api/telefonos/{imei}/modelo` cambia su tercera columna de "Al abrir, solo si el modelo sigue sin valor (fallo silencioso)" a "Al abrir, siempre, en paralelo con las demás lecturas (fallo silencioso); el resultado solo se usa si el modelo sigue sin valor".
3. `docs/paridad/formulario.md`, «Diferencias aceptadas»: cuatro viñetas nuevas al final.

```markdown
- Los campos sin valor viajan como `null` en vez de omitirse del JSON: el contrato marca todas las propiedades como obligatorias y, para el servidor, un `null` equivale a la ausencia del campo.
- Errores 500 o superiores: se mantiene la política de conexión del shell (banner y, si el corte interrumpe una acción del usuario, aviso "Sin conexión con el servidor: <detalle>"); el texto "El servidor no está disponible. Inténtalo de nuevo en unos segundos." del JavaFX no se reproduce.
- Una llamada más al abrir el flujo nuevo y Glass: `GET /api/reparaciones/asignaciones/{idRep}`, antes que las demás, para obtener el IMEI. La URL solo trae el id de la asignación (F5 y acceso directo); el JavaFX recibía la fila de la lista y no la necesitaba.
- `GET /api/telefonos/{imei}/modelo` se pide siempre al abrir, en paralelo con las demás lecturas, en vez de solo cuando el modelo sigue sin valor; el resultado se usa igual (solo si el combo sigue sin valor) y un fallo se ignora en silencio.
```
   Y en las tres casillas afectadas, al marcarlas, se añade la remisión: en "Los campos sin valor se omiten del JSON…", al final, " (en la web viajan como `null`: ver «Diferencias aceptadas»)"; en "`<mensaje>`: el del servidor en 409 y 422…", al final, " (500 o más: ver «Diferencias aceptadas»)"; en "Después, si el combo sigue sin valor y no es edición, se consulta `GET /api/telefonos/{imei}/modelo`…", al final, " (la web lo pide siempre y lo usa solo en ese caso: ver «Diferencias aceptadas»)".
4. `docs/paridad/formulario.md`, «Correcciones deliberadas respecto al JavaFX»: una sexta viñeta al final.

```markdown
- Agotado confirmado en la variante «límite» recuperado del borrador: el JavaFX dejaba la cantidad a 0 y el descuento de esas unidades se perdía al terminar. La web conserva la cantidad del borrador, acotada al stock actual del SKU (0 si ya no queda stock).
```
   En «Comportamientos del JavaFX calcados a propósito», la viñeta "El borrador restaura la cantidad sin revalidarla contra el stock actual…" se precisa añadiendo tras "stock actual": " (salvo en un agotado confirmado: ver «Correcciones deliberadas»)".
5. `docs/paridad/notificaciones.md`, «Diferencias aceptadas»: una viñeta nueva, y la remisión " (solo en la primera carga: ver «Diferencias aceptadas»)" al final de la casilla "Al iniciar sesión, si hay alguna alerta…".

```markdown
- Un fallo al cargar los componentes muestra el error solo en la primera carga (la que decide el pulso); los sondeos y recargas posteriores son silenciosos y conservan la última lista buena.
```

```bash
npm run check
git add .env.e2e.example tests/e2e/formulario.spec.ts README.md CHANGELOG.md package.json package-lock.json docs/paridad
git commit -m "chore(web): 0.3.0: smoke e2e del formulario y la campana, changelog, README y fichas de paridad marcadas"
```

- [x] **Step 4: Documentación privada (fuera de los repos; nada de esto se commitea en ellos)**

**El script de capturas de la documentación privada** se amplía a los estados de las fichas. Sigue su patrón actual, `paso(nombre, fn)` + `shot(page, nombre)`, y reutiliza los nombres base de las capturas de referencia para poder comparar lado a lado. Ningún paso pulsa el segundo clic de "✓ Confirmar" ni de "✓  Confirmar terminar", ni "Rechazar", "Recuperar", papeleras o "Rechazar todo": las capturas no escriben (lo único que dejan es el borrador de la asignación de prueba, que el último paso del técnico vacía).

Bloque del TECNICO, después de los pasos de Pendientes:

```js
// ---------- TECNICO: formulario ----------
const formulario = () => page.getByRole('dialog', { name: /^Nueva reparación — IMEI / })
await paso('form-nuevo-inicial', async () => {
  await page.getByRole('link', { name: /^Reparaciones \(/ }).click()
  await page.getByRole('button', { name: 'Añadir reparación' }).first().click()
  await formulario().waitFor()
  await page.waitForTimeout(500)
  await shot(page, 'form-nuevo-inicial')
})
await paso('form-filtro-modelo', async () => {
  const modelo = formulario().getByRole('combobox', { name: 'Filtrar por modelo' })
  if (await modelo.isEnabled()) {
    await modelo.click()
    await page.getByRole('option').first().waitFor()
    await shot(page, 'form-filtro-modelo')
    await page.getByRole('option').first().click()
  }
})
await paso('form-combo-sku', async () => {
  await formulario().getByRole('combobox', { name: /^SKU de / }).first().click()
  await page.getByRole('option').first().waitFor()
  await shot(page, 'form-combo-sku')
  await page.keyboard.press('Escape')
})
await paso('form-fila-activa', async () => {
  await formulario().getByRole('button', { name: /^Sumar /, disabled: false }).first().click()
  await shot(page, 'form-fila-activa')
})
await paso('form-confirmacion-fila', async () => {
  await formulario().getByRole('button', { name: '✓ Guardar fila' }).first().click()
  await shot(page, 'form-confirmacion-fila')
})
await paso('form-dialogo-observacion', async () => {
  await formulario().getByRole('button', { name: 'Añadir observación' }).first().click()
  await page.getByRole('dialog', { name: 'Observación' }).waitFor()
  await shot(page, 'form-dialogo-observacion')
  await page.getByRole('button', { name: 'Cancelar' }).click()
})
await paso('form-subfila-sin-stock', async () => {
  const subfila = formulario().locator('[data-testid^="subfila-"][data-variante="sinStock"]').first()
  await subfila.scrollIntoViewIfNeeded()
  await shot(page, 'form-subfila-sin-stock')
  await subfila.getByRole('button', { name: 'Solicitar pieza' }).click()
  await page.getByRole('dialog', { name: /^Solicitar pieza — / }).waitFor()
  await shot(page, 'form-dialogo-solicitar-pieza-sin-stock')
  await page.getByRole('button', { name: 'Confirmar: solicitar pieza' }).click()
  await shot(page, 'form-subfila-solicitud-confirmada')
})
await paso('form-dialogo-editar-descripcion', async () => {
  await formulario().getByRole('button', { name: /^Editar descripción de solicitud de / }).first().click()
  await page.getByRole('button', { name: 'Guardar descripción' }).waitFor()
  await shot(page, 'form-dialogo-editar-descripcion')
  await page.getByRole('button', { name: 'Cancelar solicitud' }).click()
})
await paso('form-otras-acciones-linea', async () => {
  const otras = formulario().getByTestId('otras-acciones')
  if (await otras.isVisible()) {
    await otras.getByRole('button', { name: '+ Añadir acción' }).click()
    await otras.getByPlaceholder('Describe la acción').fill('Limpieza de conector')
    await shot(page, 'form-otras-acciones-linea')
    await otras.getByRole('button', { name: 'Quitar acción' }).click()
  }
})
await paso('form-confirmacion-terminar', async () => {
  await formulario().getByTestId('zona-guardar').getByRole('button').click()
  await shot(page, 'form-confirmacion-terminar')
})
await paso('form-borrador-recuperado', async () => {
  const url = page.url()
  await formulario().getByRole('button', { name: 'Cerrar formulario' }).click()
  await page.goto(url)
  await formulario().getByTestId('banda-borrador').waitFor()
  await shot(page, 'form-borrador-recuperado')
})
await paso('form-vaciar-borrador', async () => {
  // Deja la asignación de prueba como estaba: sin filas activas el cierre borra el borrador.
  await formulario().getByRole('button', { name: /^Restar /, disabled: false }).first().click()
  await formulario().getByRole('button', { name: 'Cerrar formulario' }).click()
})
await paso('form-glass-nuevo', async () => {
  await page.getByRole('link', { name: /^Glass \(/ }).click()
  const anadir = page.getByRole('button', { name: 'Añadir glass' }).first()
  if (await anadir.isVisible()) {
    await anadir.click()
    await formulario().waitFor()
    await shot(page, 'form-glass-nuevo')
    await formulario().getByRole('button', { name: 'Cerrar formulario' }).click()
  }
})
```

Bloque del SUPERTECNICO, después de los pasos del Historial:

```js
// ---------- SUPERTECNICO: campana y edición ----------
await paso('campana-on-badge', async () => {
  await page.getByTestId('campana').waitFor()
  await page.waitForTimeout(800)
  await shot(page, 'campana-on-badge-pulso')
})
await paso('notif-alertas-y-solicitudes', async () => {
  await page.getByTestId('campana').click()
  await page.getByTestId('panel-notificaciones').waitFor()
  await page.getByRole('tab', { name: 'Alertas' }).click()
  await shot(page, 'notif-alertas')
  await page.getByRole('tab', { name: 'Solicitudes' }).click()
  await page.waitForTimeout(500)
  await shot(page, 'notif-solicitudes')
  await page.keyboard.press('Escape')
})
await paso('form-editar', async () => {
  await page.getByRole('link', { name: 'Historial' }).click()
  await page.getByRole('table').locator('tr[aria-selected]').first().getByRole('cell').first().click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Editar' }).waitFor()
  await shot(page, 'historial-menu-editar')
  await page.getByRole('menuitem', { name: 'Editar' }).click()
  const edicion = page.getByRole('dialog', { name: /^Editar reparación — / })
  await edicion.waitFor()
  await shot(page, 'form-editar')
  const editada = edicion.locator('[data-estado="editada"]')
  if ((await editada.count()) > 0) {
    // El cambio es la observación: en la fila editada "Reutilizado" va deshabilitado con cantidad > 0 y "+" con stock 0.
    const papelera = editada.getByRole('button', { name: /^Borrar observación de / })
    if ((await papelera.count()) > 0) {
      await papelera.click()
    } else {
      await editada.getByRole('button', { name: 'Añadir observación' }).click()
      const observacion = page.getByRole('dialog', { name: /^Observación para: / })
      await observacion.getByRole('textbox', { name: 'Observación' }).fill('Captura')
      await observacion.getByRole('button', { name: 'Guardar', exact: true }).click()
    }
    await shot(page, 'form-editar-cambio-valido')
    await edicion.getByRole('button', { name: 'Cerrar formulario' }).click()
    await page.getByRole('button', { name: 'Salir sin guardar' }).first().waitFor()
    await shot(page, 'form-editar-cerrar-con-cambios')
    await page.getByRole('button', { name: 'Salir sin guardar' }).click()
  } else {
    await edicion.getByRole('button', { name: 'Cerrar formulario' }).click()
  }
})
```

**Esqueleto de directorios de la web** (documentación privada): añadir `taller/formulario/` y `taller/notificaciones/` con una frase por fichero, `shared/ui/ComboNavy.tsx`, y en la tabla de equivalencias el controlador del formulario → `formulario/{estado,borrador,api}.ts` + vista, y la campana de la ventana principal → `notificaciones/`.

**Plan maestro** (documentación privada): la casilla del sub-proyecto 2 se marca en el Step 8, con fecha y commits, cuando existan los merges.

- [x] **Step 5: Verificación final en local (la ejecuta el agente; no modifica nada)**

Suites, en este orden. El cliente JavaFX **no se toca**: su suite es el control de compatibilidad.

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && mvn -q test && cmp target/openapi.json ../gestion-reparaciones-web/api/openapi.json && echo CONTRATO_IDENTICO
cd ../gestion-reparaciones-web && npm run check && npm run build
cd ../gestion-reparaciones-cliente && git status --short . && mvn -q test
```
Expected: servidor en verde y `CONTRATO_IDENTICO`; web en verde y build correcto; `git status` del cliente sin salida (ni un fichero tocado) y su suite en verde.

Arranque del servidor en local (la suite no levanta el contexto completo contra la base de datos: un cambio de anotaciones o de beans puede romper el arranque sin que ningún test lo note). Usa la configuración local del desarrollador; el plan no escribe credenciales: las variables las carga el usuario en su terminal.

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && mvn -q spring-boot:run
```
En otra terminal, con `TEC_USER/TEC_PASS` y `E2E_USER/E2E_PASS` ya en el entorno:

```bash
token() { curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d "{\"usuario\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/'; }
TEC=$(token "$TEC_USER" "$TEC_PASS"); SUP=$(token "$E2E_USER" "$E2E_PASS")
curl -s -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer no-es-un-token' localhost:8080/api/solicitudes/count
curl -s -o /dev/null -w '%{http_code}\n' -X PUT -H "Authorization: Bearer $TEC" -H 'Content-Type: application/json' -d '{"idComNuevo":1,"esReutilizadoNuevo":false,"observacionNueva":null,"nNuevas":1,"updatedAt":"2026-01-01T00:00:00"}' localhost:8080/api/reparaciones/R00000000_0
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $SUP" localhost:8080/api/solicitudes/count
```
Expected: `401`, `403`, `200`. Se para el servidor al terminar.

- [x] **Step 6: Revisión final de la rama (multi-lente) y correcciones**

Revisión del diff completo `main...feature/web-formulario` de web y servidor con tres lentes, cada una un subagente revisor con esfuerzo alto:
1. **Paridad** contra `docs/paridad/formulario.md` y `notificaciones.md` y contra las capturas de referencia (textos literales con sus dobles espacios, colores por token, casillas marcadas que de verdad tienen test).
2. **Capas, tipos y tests**: fronteras de `eslint.config.js` (el módulo no importa de `app`; `shared` no importa de `modules`), ningún cast sobre tipos del contrato, contrato idéntico, tests con datos sintéticos.
3. **Permisos y errores**: el técnico de cada escritura sale del token; la edición y el ajuste de stock exigen SUPERTECNICO; roles de `/api/solicitudes-stock`; 403 → aviso genérico y vuelta a la lista; 409 con sus literales; y un repaso de que en los dos repos no hay datos reales (IMEI, nombres, direcciones, rutas de la documentación privada).

Cada hallazgo se corrige en un commit propio (`fix(web): …` / `fix(servidor): …`) con su test, y se repite el Step 5. La rama se da por "lista para merge" con cero hallazgos críticos.

- [x] **Step 7: Pasos del USUARIO, uno a uno y con su OK explícito**

El agente **no ejecuta nada de este paso por su cuenta**: ni `git push`, ni merge, ni tag, ni despliegue, ni SSH. Presenta cada punto, deja escritos los comandos, espera el OK explícito del usuario para ese punto y no pasa al siguiente hasta que el usuario confirma el resultado. Las credenciales y la dirección del entorno llegan solo por variables de entorno que el usuario carga en su terminal (`E2E_BASE_URL`, `E2E_USER`/`E2E_PASS`, `TEC_USER`/`TEC_PASS`, `API_USER`/`API_PASS`); nunca por chat ni escritas en un fichero del repo. El servidor va siempre antes que la web.

1. **Servidor: merge `--no-ff` a `main` + push → despliegue → comparación del contrato.**

   Merge y push (los lanza el usuario, o el agente solo si el usuario lo pide expresamente para este punto):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git checkout main && git merge --no-ff feature/web-formulario -m "Merge branch 'feature/web-formulario' (técnico del token y asignación propia, roles de solicitudes de stock y ajuste de stock, chasis por SKU, contrato con nullabilidad)"
git push origin main
```
   Despliegue del servidor (usuario): el agente redacta en el chat los comandos de la guía operativa privada (actualizar el clon a `main` y reconstruir el contenedor del backend) y el usuario los ejecuta en su terminal. Después, contrato desplegado comparado con el snapshot de la web:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && API_URL="$E2E_BASE_URL" node scripts/fetch-openapi.mjs && git diff --exit-code api/openapi.json && echo CONTRATO_DESPLEGADO_IDENTICO
```
   Expected: `CONTRATO_DESPLEGADO_IDENTICO`. El script sobrescribe `api/openapi.json`: si hay diferencias de rutas o esquemas se para aquí y se investiga; en cualquier caso el fichero se deja como estaba con `git checkout api/openapi.json`.
2. **Web: merge `--no-ff` a `main` + push → despliegue.**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git checkout main && git merge --no-ff feature/web-formulario -m "Merge branch 'feature/web-formulario' (formulario de reparación, solicitudes, borrador y campana)"
git push origin main
```
   Despliegue de la web (usuario), igual que en el punto 1 con el contenedor de la web.
3. **Datos de prueba** (usuario, con el cliente JavaFX de referencia 0.16.x apuntando a producción): dos asignaciones de reparación para el técnico de `TEC_USER` sobre IMEI de prueba, de un modelo con un tipo con stock y otro con el SKU a 0 (una la usan las capturas sin modificarla y el smoke la termina; la otra queda de reserva por si hay que repetir); opcionalmente una de Glass para las capturas. En la misma sesión, control de compatibilidad en vivo: con ese cliente, abrir el formulario desde "Mis pendientes" sobre una asignación de prueba, guardar una fila y cerrar, contra el servidor ya desplegado. El guion de limpieza posterior vive en la documentación privada.
4. **Smoke e2e + lista manual del ADMIN + capturas**, contra lo desplegado (el usuario carga las variables).

   a. Capturas de la web. No escriben (lo único que dejan es el borrador, que el propio script vacía), así que van antes del smoke, que termina la asignación:

```bash
OUT_DIR="<carpeta de capturas de la documentación privada>" node "<el script de capturas de la documentación privada>"
```
   El usuario compara lado a lado con las capturas de referencia y anota en las fichas lo que difiera. Las dos capturas de la campana con badge (`campana-on-badge-pulso`, `notif-solicitudes`) se repiten después del smoke, que deja una solicitud pendiente.

   b. Smoke (escribe sobre los datos del punto 3):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && npm run e2e
```
   Expected: `clientes.spec.ts`, `taller.spec.ts` **completo** (incluido el test del técnico, pendiente desde el sub-proyecto 1) y `formulario.spec.ts` en verde, sin ningún `skipped`. Si algo falla se corrige en una rama `fix/…`, que vuelve a pasar por los puntos 1 o 2 según el repo, y se repite; no se tapa con un reintento.

   c. Lista de comprobación manual del rol ADMIN (usuario, en el navegador):
   - [x] entra por Historial de reparaciones;
   - [x] no tiene "Pendientes" en la columna lateral;
   - [x] no tiene campana en la barra superior;
   - [x] no hay refresco periódico (la etiqueta "Actualizado HH:mm" solo cambia al volver el foco a la ventana o al recargar a mano);
   - [x] los menús contextuales solo ofrecen "Copiar celda" (sin "Editar");
   - [x] el CSV de pulidos incluye la columna "Técnico".

   d. Limpieza de los datos de prueba (usuario, con el guion de la documentación privada).
5. **Tag `v0.3.0` + push del tag** — solo con el punto 4 en verde. La fecha del CHANGELOG se fija ese día: el agente prepara en `main` de la web el cambio de `<fecha del tag>` por la fecha real y, con el OK del usuario, lo commitea (`docs(web): CHANGELOG 0.3.0 con la fecha del tag`). Después, el usuario:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git tag -a v0.3.0 -m "Formulario de reparación, solicitudes, borrador y campana"
git push origin main v0.3.0
```
6. **Gitlinks en el repo raíz + push.** El agente marca las casillas de este plan y deja preparado el commit en el repo raíz (`main`); commit y push, con el OK del usuario:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-web gestion-reparaciones-servidor docs/superpowers/plans
git commit -m "chore: gitlinks servidor y web tras el sub-proyecto 2 Formulario de reparación (web v0.3.0) y plan cerrado"
git push origin main
```

- [x] **Step 8: Plan maestro y memoria (sin git)**

Con el Step 7 completo: se marca el sub-proyecto 2 en el plan maestro de la documentación privada (fecha, commits de merge y tag) y se actualiza la memoria del proyecto: sub-proyecto 2 CERRADO, qué queda de deuda (spec §14) y cuál es el siguiente sub-proyecto. Este paso no toca ningún repositorio.


---

## Cobertura de la spec

| Spec | Tasks |
|---|---|
| §4 rutas, navegación, título, cierre, 403 por URL | 12, 17, 18 |
| §4 refresco de la campana (intervalo, foco, snapshot) | 19, 20 |
| §5.1 técnico del token, propiedad, excepción de la edición | 1 |
| §5.2 `PUT /{idRep}` SUPERTECNICO | 1 |
| §5.3 roles de solicitudes-stock y `PATCH …/stock` | 2 |
| §5.4 autodetección de chasis | 2 |
| §5.5 contrato · §5.7 arranque · §5.8 documentación corta | 3 |
| §5.6 sin cambios de esquema | 1–3 (restricción) |
| §6.1 `estado.ts` · `borrador.ts` · `api.ts` · `useBorrador` · vista y diálogos | 6–8 · 9 · 11 · 16 · 12–15, 18 |
| §6.1 ampliación de `taller/lib` | 5 |
| §6.2 notificaciones | 19, 20 |
| §6.3 llamadas | 11, 19 |
| §6.4 cambios en lo existente (botón, menú, shell, deuda) | 10, 12, 17, 18, 19 |
| §6.4 franja naranja, badge "Solicitud" y SKU en Pendientes | ya cubierto por el sub-proyecto 1 (`filaClase` y `badgesEstado`); la Task 12 lo verifica con un test de `PendientesPage` |
| §7 cabecera · filas · otras acciones · guardar · glass · editar | 6, 12 · 6, 7, 13, 14 · 7, 15 · 8, 15 · 17 · 8, 18 |
| §8 borrador | 9, 16 |
| §9.1–9.3 diferencias, calcos y correcciones | 7, 8, 14, 15, 18, 20; fichas en 21 |
| §10 errores | 12, 13, 15, 18 |
| §11 fichas, tests, e2e, capturas | cada tarea; 21 |
| §12 criterios de cierre | 21 |

**Casillas de las fichas sin cubrir:** ninguna. Tres casillas quedan cubiertas con una precisión que el usuario debe confirmar antes de redactar los cuerpos (ver el mensaje de entrega): "Los campos sin valor se omiten del JSON" (se envían como `null`), "`<mensaje>`: … 500 o más \"El servidor no está disponible…\"" (la web mantiene su política global de conexión) y "Un fallo al cargar los componentes muestra el error" (solo en la primera carga).

## Decisiones resueltas antes de redactar los cuerpos (2026-09-19)

1. **Campos sin valor:** la web envía `null` (el contrato marca todas las propiedades como `required`; para Jackson es lo mismo que omitirlos). Se anota en «Diferencias aceptadas» de la ficha en la Task 21. Sin casts.
2. **Errores 5xx:** se mantiene la política global de la web desde el sub-proyecto 0 (banner y diálogo de conexión); el texto específico del JavaFX para 5xx no se reproduce. Se anota en «Diferencias aceptadas» en la Task 21.
3. **Asignación inexistente en la regla de propiedad:** no lanza 403; deja que el DAO responda su 409 de «ya eliminada o completada», como hoy. En `borrador` sigue como hoy.
4. **Fallo al cargar componentes en la campana:** se muestra el error solo en la primera carga (la del pulso); los sondeos y recargas posteriores son silenciosos y conservan la última lista buena.
5. **Tokens de color:** se reutiliza un token existente cuando el hexadecimal coincide exactamente, aunque su nombre semántico sea otro; solo se crean tokens nuevos para hexadecimales que no existan (la lista cerrada está en W9).
6. **Coste de la suite del servidor:** se aceptan las dos clases MockMvc con contextos propios.
7. **Agotado confirmado recuperado del borrador (variante «límite»):** la cantidad se conserva acotada al stock actual del SKU (0 si ya no hay stock), para no perder el descuento; el JavaFX la dejaba a 0. Lo aplica `aplicarBorrador` (Task 9, W7) y es la sexta «corrección deliberada» que la Task 21 anota en la ficha `formulario.md`.

## Ejecución y cierre (2026-09-19 → 2026-09-21)

Ejecutado con un implementador y un revisor por tarea, revisión final de cada rama y verificación final (servidor 218 tests, web 683 tests y build, cliente de escritorio 284 tests sin tocar, contrato idéntico entre servidor y web). Entrega: servidor `main` `ecd69f6`, web `main` `41538a2` = tag `v0.3.0`.

Tareas añadidas durante el cierre, por decisión del usuario:

- **Task 22 (servidor) — reintentos seguros.** Cabecera opcional `Idempotency-Key` en `POST /api/reparaciones/completa`, `POST …/{idAsignacion}/filas`, `POST …/{idAsignacion}/agotar-componente` y `PUT /api/reparaciones/{idRep}`: con la misma clave y la misma petición, el servidor devuelve el resultado de la primera ejecución sin repetirla (409 si sigue en curso, 422 si la clave se reutiliza con otra petición). Registro en memoria con caducidad de 24 h; sin cambios de esquema; sin cabecera, el comportamiento es el de siempre. La autorización se evalúa antes que el registro y el log de actividad se escribe una sola vez, después de la escritura.
- **Task 23 (web) — claves por operación.** Cada guardado del formulario (fila, acción, agotado, terminar y cada paso de "Guardar cambios") viaja con su clave y la reutiliza al reintentar la misma petición; en "Guardar cambios" las claves se dan por hechas al terminar bien la llamada entera. `formulario/estado.ts` y `formulario/borrador.ts` no cambian.
- Las cuatro escrituras del formulario llevan además el rol de técnico o supertécnico, igual que el borrador; las alertas de stock se recargan también al abrir el panel de notificaciones.

Ajustes respecto al texto de este plan que conviene conocer al leerlo:

- Guardados solapados: mientras "Terminar asignación" o "Guardar cambios" está en curso no se guarda una fila ni una acción por separado, y al revés (octava corrección deliberada de la ficha).
- El volcado del borrador al desmontar solo escribe si hubo cambios en esa apertura; ✕ y Escape vuelcan siempre.
- `PanelNotificaciones` recibe las alertas por props desde `Campana`, que mantiene la única suscripción a los componentes.
- Para elegir una opción de `ComboNavy` en un test se pulsa el botón interior de la opción.
- El arranque del servidor con 401/403/200 se comprobó contra el entorno desplegado, y los datos de prueba del smoke se crearon por la API (teléfonos sintéticos con modelo y sus asignaciones) en vez de con el cliente de escritorio.
- El smoke se ejecuta en serie (`workers: 1`): el entorno desplegado limita los inicios de sesión por minuto. La comparación del contrato desplegado se hace con el JSON normalizado (el script de descarga reescribe el formato).
