# Mapa de dominio y producto — ERP gestion-reparaciones (Fonestore)

> Extraído exclusivamente de la documentación indicada (2026-09-13). Cada sección cita su fuente. Aviso general: `api_contract.md` (cliente y servidor), `autorizacion_endpoints.md` y `schema.md` están **desactualizados** respecto a `CHANGELOG.md` y `plan-futuro.md` (no recogen lotes, revisión, envíos, clientes, compras-otros, entrega glass, por-cerrar, chasis…). `bgc3,.md` es una guía de configuración de Claude Code, sin contenido de dominio.

## 1. Alcance del ERP y organigrama

**Alcance** (`Apuntes/cobertura-erp.md`, a v0.13.0; `plan-futuro.md` §1 y §8):
- Solución vertical de **SAT / taller de reparación de iPhones** con Inventario y Compras alrededor; aspira a ERP más completo.
- Núcleo maduro: **SAT** (órdenes por IMEI, 3 tipos de trabajo Reparación/Glass/Pulido con colas, historiales e incidencias propias, urgencias, borrador persistente, vista IMEIs), **Inventario** (SKU, stock + mínimo, master/slave, consumo desde reparación, solicitudes de pieza) y **Compras** (proveedor, pedidos con recepción parcial, multidivisa vía `TipoCambio`, pedidos "Otros" sin stock). Desde F2 (`plan-futuro.md` §2) se añade el **ciclo de vida del teléfono** (lotes, revisión, envíos).
- Parcial: CRM (cliente = solo nombre + activo), BI básico (estadísticas, log de auditoría, CSV), RR.HH. lateral (identidad técnicos), multidivisa, control de accesos JWT.
- **Fuera de la app, en sistemas externos**: ventas/TPV, facturación + contabilidad/Verifactu, logística/envíos físicos (la app solo registra el envío). Compras "solo se apunta": sin integración fiscal.
- Ausente: MRP, proyectos, activos fijos, QM formal, CMMS, marketing.

**Organigrama real** (`Apuntes/organigrama_fonestore.md`, privado; aquí solo por función, sin nombres): Administración (3 personas); Logística (2: pedidos, stock, etiquetas y empaquetado; pedidos externos, grading y organización de reparaciones); Comercial (1: clientes, precios, certificados); Taller (8: grading, revisión de pedidos, pulido, revisión de funcionalidad y reparación; sub-departamento Glass de 2); Incidencias (3: incidencias externas, reparar, organizar reparaciones); IT (2: web/automatización; ERP). El doc no asigna roles de la app a personas; por función: taller = TECNICO, quienes organizan reparaciones = SUPERTECNICO, logística/administración = futuro rol LOGISTICA (`plan-futuro.md` §3; `metricas-estadisticas.md` §8 menciona "logística, supertécnicos con otras tareas").

## 2. Módulos funcionales y ciclo de vida

| Módulo | Contenido documentado | Fuente |
|---|---|---|
| Autenticación/cuenta | Login JWT (24 h según servidor), cambiar contraseña, roles TECNICO/SUPERTECNICO/ADMIN | `api_contract` servidor/cliente |
| Asignaciones (SuperTécnico) | Modal 3 colas (Reparación/Glass/Pulido), mismo IMEI en varias, cliente por IMEI con "— Sin cliente —", aviso de asignación cruzada, pegado masivo de IMEIs (15 dígitos), urgente por IMEI, chasis flag, "Lleva glass" → glass automática al técnico habilitado con menos carga, técnico y cliente persistentes, Carga de técnicos (capacidad diaria) | `cobertura-erp.md`; CHANGELOG 0.13–0.16.2; `plan-futuro.md` §1 |
| Mis pendientes (3 roles) | Pestañas Reparación/Glass/Pulido, completar (formulario con piezas, reutilizado, acciones "otro", solicitud de pieza, agotar componente), "Por cerrar", entrega del teléfono al técnico de glass ("→ técnico", "Llegó"), badges 99+ | CHANGELOG 0.15.0, 0.16.1; `permisos_roles.md` |
| Historial | Selector Reparaciones/Glass/Pulidos, filtros (técnico, cliente, pieza, IMEI multi), editar reparación/glass, incidencias, CSV | CHANGELOG 0.11–0.13 |
| IMEIs (Reparaciones) | Vista simple por dispositivo con contadores por tipo, drill-down; TECNICO solo los suyos | `plan-futuro.md` §2 Reubicación |
| Inventario (menú propio) | Vista completa 28 campos: importar xlsx (vista previa, conflictos, modelos/colores sin mapear), alta manual con lookup IMEI, editar atributos (lock optimista), filtros Estado/Ubicación/Lote/Modelo, CSV 19 col; SUPERTECNICO+ADMIN (ADMIN sin importar) | `plan-futuro.md` §2 F2a/Reubicación |
| Lotes / Suppliers | Vista de lotes; suppliers de terminales = `Proveedor.TIPO ENUM(COMPONENTES,TELEFONOS)` | `plan-futuro.md` §2 |
| Revisión | Panel con cola y escáner; ficha en 2 partes con veredicto OK / bloqueo / desguace / enlace a asignar | `plan-futuro.md` §2 F2b |
| Envíos | Enviar + devolución (`Envio`, `Envio_Telefono`, `ES_DEVOLUCION`); trazabilidad `Movimiento_telefono` en UI | `plan-futuro.md` §2 F2c |
| Stock / Pedidos / Proveedores | Componentes (SKU, stock, mínimo, activo, compartidos), pedidos componentes y "Otros", recepción total/parcial/resto/alterado, cancelar, tipos de cambio, CSV | `permisos_roles.md`; `schema.md`; CHANGELOG 0.12–0.14 |
| Solicitudes de stock | Peticiones de pieza del técnico (PENDIENTE/GESTIONADA/RECHAZADA), contador | `api_contract` servidor; `schema.md` |
| Clientes | Catálogo vinculado a IMEI; SUPERTECNICO gestiona, resto lectura | CHANGELOG 0.10.0 |
| Estadísticas | Técnicos (puntos), Stock (evolución), ⚙ Valores y 👥 Técnicos (admin) | `metricas-estadisticas.md` |
| Administración | Usuarios/técnicos (crear, activar, eliminar, excluir estadísticas, técnicos de glass) | `api_contract` servidor |
| Log de actividad | Filtros en servidor, por acción, detalle, motivo obligatorio al borrar | CHANGELOG 0.10.0 |
| Notificaciones | Campana de alertas de stock, "Pedir todas las piezas", "Ir a pedidos" | CHANGELOG 0.16.0/0.16.1 |

**Ciclo de vida del teléfono** (`plan-futuro.md` §2; spec F2 canónica no incluida aquí):
- Entrada por **lote** (xlsx del supplier o alta manual; batch+supplier existentes → append al lote). Atributos: modelo (equivalencias `Modelo_equivalencia`), color oficial (`Color_equivalencia`, matriz `CatalogoAtributos`), storage, `ES_ESIM`, `GRADO_PROPIO ENUM(C,B,A-,A)`; el SKU de teléfono se **deriva** (pendiente `SkuTelefono.derivar()`).
- Estados explícitos `RECIBIDO → EN_REVISION → BLOQUEADO | OK → ENVIADO | DESGUACE` (+ derivados `REVISADO`/`REPARADO`); la **ubicación siempre se deriva** (`UbicacionDerivador` en servidor, `UbicacionTexto` en cliente); trazabilidad append-only `Movimiento_telefono`.
- Revisión (F2b): tabla `Revision`, veredicto; bloqueo con motivo; "Enviar a externo…" pendiente.
- Envío/devolución (F2c); el check antiguo `REVISION_LOGISTICA` está retirado.
- **Trabajos**: asignaciones `A`/`AG`/`AP` → hechos `R`/`G`/`P` (`Reparacion.ID_REP` con prefijo + fecha + contador, generado en servidor); incidencias encadenadas por `ID_REP_ANTERIOR`; urgente por IMEI (propagación server-side, job nocturno por cliente vencido); por cerrar; chasis; entrega glass (`ENTREGADO_AT/POR`) (`schema.md`, `cobertura-erp.md`, CHANGELOG).
- Pedidos: `pendiente → en_camino → parcial/recibido/cancelado` (`cobertura-erp.md`; `schema.md` lista solo 4 valores sin `en_camino`).
- `Apuntes/Modulo Ubicaciones - Spec.md` (v0.1 sobre diagrama v14) describe el modelo conceptual de **cajas** (ALMACÉN → PARA REVISAR → REPARACIONES pulir→glass→normal → LISTOS → PEDIDOS → ENVIADOS / DESGUACE) con transiciones numeradas y flags; es un borrador anterior a F2 con puntos "pendiente de aclarar" (`es_F`, override admin, caducidad). El diagrama vivo es `FLUJO_UBICACIONES_v17.drawio` (`plan-futuro.md` §8).

**Lógica de negocio que hoy vive en el cliente** (a replicar o mover a servidor en la web): `LoteXlsxParser` (Apache POI), `ClasificadorImportacion`, `ModeloMapper`, `ColorMapper`, `CatalogoAtributos`, `LookupModelosImeis` (cola con pacing 2 s, rate limit 30 req/min), `UbicacionTexto`, `ConfigVistaAgrupado`, `CargaTecnicos` (pesos 1/8, 1/17, 1/25; jornadas 9/8/6 h), `PuntosEstadistica`, balanceo de glass automática, `CsvExporter`, "N asignados" derivado en cliente (`plan-futuro.md` §2; CHANGELOG 0.12–0.16.2; `metricas-estadisticas.md` §10).

## 3. Matriz de permisos por rol

Fuente cliente: `permisos_roles.md`. Fuente servidor: `autorizacion_endpoints.md` (criterio: GET abierto a cualquier autenticado; mutaciones por rol; `hasRole('SUPERTECNICO')` **excluye a ADMIN**, que recibe 403).

| Área | TECNICO | SUPERTECNICO | ADMIN | Servidor |
|---|---|---|---|---|
| Asignaciones propias / completar / editar propia / agotar componente | Sí | Sí | No (lectura) | `POST /completa`, `PATCH completar`, `PUT /{idRep}`, `POST agotar-componente`, `POST /api/reparaciones`: **cualquiera** |
| Asignar, reasignar, editar asignación, urgente, borrar, incidencia, borrar incidencia | No | Sí | No (lectura) | SUPERTECNICO (`POST asignaciones`, `PATCH tecnico`, `POST incidencia`, `DELETE`) |
| Historial / IMEIs | Propio | Todos | Todos (lectura) | GET **cualquiera**, `?tecnico=` opcional |
| Pulidos | Historial propio | Gestión completa | — | Mismos endpoints de reparaciones |
| Stock componentes | Lectura en formulario | CRUD, mínimo, activo | Lectura (`cantidad-pendiente`) | GET cualquiera; POST/PUT/PATCH/DELETE SUPERTECNICO; **`PATCH stock` cualquiera** |
| Pedidos / Proveedores / Solicitudes | No | Sí | No | SUPERTECNICO (ADMIN solo `GET cantidad-pendiente`) |
| Inventario / importar / lotes | Ve inventario según spec F2a; tras reubicación solo sus IMEIs | Sí | Ve, sin importar | No documentado (`TelefonoController` sin rol) |
| Clientes | Lectura | Gestiona | Lectura | No documentado |
| Estadísticas | Solo propias | Solo propias (0.16.2) | Todas + ⚙ Valores + 👥 Técnicos | GET cualquiera; `PUT valores-dificultad` y `PATCH excluir/incluir` ADMIN |
| Usuarios / Log | No | No | Sí | ADMIN (`UsuarioController`, `LogController`) |
| Técnicos de glass | — | Edita | Ve | `PATCH /api/tecnicos/{id}/glass` SUPERTECNICO |
| Cambiar contraseña | Sí | Sí | Sí | `PATCH /api/auth/cambiar-password` (solo doc cliente) |

**Restricciones SOLO en cliente** (según los docs citados):
- Filtro "solo lo propio" en historial, asignaciones y estadísticas de TECNICO/SUPERTECNICO: el servidor sirve todo a cualquier rol (`autorizacion_endpoints.md`: `/historial`, `/asignaciones`, `/estadisticas`, `/estadisticas/puntos` "cualquiera"; `metricas-estadisticas.md` §9-10; `plan-futuro.md` §7 Seguridad).
- Propiedad de la reparación al editar/completar (PUT/PATCH "cualquiera"; solo por-cerrar valida propiedad, y aun así "sin exigir rol TECNICO en la rama de propiedad", `plan-futuro.md` §3).
- `PATCH /api/telefonos/{imei}/observacion` "solo SUPERTECNICO" en `api_contract` cliente; `TelefonoController` sin restricción en servidor. `DELETE /api/telefonos/{imei}` sin rol; `POST /api/reparaciones` sin `@PreAuthorize`; `PATCH chasis` sin validar prefijo (`plan-futuro.md` §3, §6).
- ADMIN "solo lectura" en asignaciones y stock: coherente en servidor (403 por `hasRole('SUPERTECNICO')`), pero ADMIN tampoco puede leer proveedores/compras/solicitudes por API.
- Visibilidad de Inventario por rol y "guard defensivo por rol en `mostrarInventario()`" pendiente (`plan-futuro.md` §2 Reubicación).
- Exclusión de técnicos en estadísticas: el filtrado es del cliente (`metricas-estadisticas.md` §10).

## 4. Multiusuario (`multiusuario.md`, + `plan_migracion_backend.md`, `cobertura-erp.md`, CHANGELOG 0.12.0)

Comportamiento que la web debe replicar:
- **Optimistic locking**: `UPDATED_AT` en `Componente`, `Reparacion`, `Compra_componente`, `Reparacion_componente` (y `Dificultad_puntos`, `schema.md`). El cliente guarda el timestamp al cargar, lo envía en el body; el servidor hace `UPDATE … AND UPDATED_AT = ?` y devuelve **409** si 0 filas (`StaleDataException`). Truncado a segundos en ambos lados.
- **Mensajes de conflicto específicos** al confirmar cambios de técnico: tras el 409 se reconsulta `GET asignaciones/{idRep}` y se lista por asignación: "ya no está pendiente (fue completada por otro usuario)" / "fue reasignada a X por otro usuario" / "fue modificada por otro usuario"; los guardados correctos no se mencionan; luego recarga. Pedido: "El pedido fue modificado por otro usuario. Cierra y recarga los datos." + cierre del formulario. Componente: alert + recarga. Lenguaje de usuario, sin tecnicismos.
- **Vértices cubiertos en servidor** (§8): `nextId()` con `FOR UPDATE`; `insertarCompleta` sobre A* borrada → 409; `completar` con `AND FECHA_FIN IS NULL` → 409; `borrarIncidenciaPorImei` recheck; recepción de pedido en transacción única; stock con aritmética atómica. Pendiente: `CHECK (STOCK >= 0)`, SSE/WebSockets.
- **Frescura**: polling cada **60 s** por vista activa (propuesta 30 s para stock/asignaciones), **recarga al recuperar el foco** de la ventana, label **"Actualizado HH:mm"** clicable para recargar a mano (`fixes post 1er sesion UAT.md`), interfaz `Recargable` que recarga solo el panel visible, borrado → `cargar()` inmediato, formularios cargan combos frescos al abrir.
- **Sesión expirada**: interceptor central en `ApiClient`: cualquier **401** → cerrar sesión, navegar a login, "Tu sesión ha expirado. Inicia sesión de nuevo." Token en memoria (nunca en disco); validez 24 h (`api_contract` servidor).
- **Conexión**: banner no bloqueante "sin conexión" que se autocura; diálogos con owner (CHANGELOG 0.12.0). Parche login: `connectTimeout` 5 s, timeout 15 s por petición, un único reintento solo en login, mensaje veraz (`plan-futuro.md` §6; el keepalive del `HttpClient` es específico de JavaFX).
- **Borrador persistente** del modal de asignación (`Reparacion_borrador`, `cobertura-erp.md` §1; `plan-futuro.md` §1 "borrador persistente") — no detallado en `multiusuario.md`.
- Confirmar al cerrar formulario con cambios sin guardar; modal de asignación no se cierra al confirmar (`fixes post 1er sesion UAT.md`).

## 5. Estadísticas (`metricas-estadisticas.md`)

Tabla `Dificultad_puntos` (seed bateria 1, camara 0,7, chasis 2, marco 0,5, pantalla 1, glass 0,5, otro 0,5 → vigente 0,25, pulido 0,25); mapeo prefijo SKU→clave en orden `bat, cha, cam, lcd, mc, g`; siempre con la tabla vigente (revalora el pasado).

| Cálculo | Dónde |
|---|---|
| Puntos por trabajo terminado (suma de filas de pieza, cantidad NO multiplica; sin filas → `otro`; pulido fijo; reutilizadas y acciones "otro" cuentan; solicitudes puras no) | **Servidor** `PuntosCalculo` |
| Horario de jornada (L-M 8:30-18:00, X-J 8:30-17:00, V 8:30-14:30, margen 8:00 / +15 min; finde todo extra), fecha de cierre en hora Madrid, `puntosJornada`, `nImeisJornada`, `nImeis` por periodo, desglose normales/glass/pulidos/sinPiezas | **Servidor** `Jornada`, endpoint `GET /api/reparaciones/estadisticas/puntos` |
| Días laborables (L-V, periodo en curso hasta hoy, divisor mínimo 1), **Puntos/día**, **Promedio** (media por técnico-periodo trabajado, solo jornada), x̄ por serie, Por encima/Por debajo, tarjetas (mes cerrado vs anterior con signo verde/rojo; hoy vs media del mismo día de semana del mes anterior, % truncado, gris/verde nunca rojo), IMEIs típicos, chips "N IMEIs", ventana estándar 30 d/16 sem/12 m/5 a con actividad, eje X solo periodos con actividad, filtrado de excluidos (`ES_ESTADISTICA`), textos y tooltips | **Cliente** `PuntosEstadistica` + `EstadisticasController` |
| Evolución de stock, conteos por técnico | Servidor `GET /api/componentes/evolucion-stock`, `GET /api/reparaciones/estadisticas` |
| Valores y exclusión | `GET/PUT /api/valores-dificultad` (PUT ADMIN, 422 fuera de rango), `PATCH .../excluir-estadisticas|incluir-estadisticas` (ADMIN), log `EDITAR_PUNTOS` |

Backlog explícito para la web (F4): tarjeta de progreso con flechas de meses, serie de IMEIs, calendario de festivos/horario por técnico, tabla de horario configurable, filtrado "solo propias" en servidor.

## 6. Contrato API (inventario compacto)

Base `/api`, JWT Bearer salvo login. Fuentes: `api_contract.md` cliente (83 endpoints) y servidor.

- **AuthController**: `POST /auth/login` → `{idUsu, nombreUsuario, rol, idTec, token}`; `PATCH /auth/cambiar-password` (solo doc cliente; 400/422).
- **UsuarioController** (ADMIN): `GET/POST /usuarios/tecnicos`; `PATCH …/{idTec}/activar|desactivar|excluir-estadisticas|incluir-estadisticas`; `GET …/{idTec}/tiene-reparaciones`; `DELETE …/{idTec}?idUsu=`. (`autorizacion_endpoints.md` lista rutas distintas: `/registrar`, `/activar/{id}`, `/rol/{id}`…)
- **TecnicoController**: `GET /tecnicos`, `GET /tecnicos/activos`; solo servidor: `POST /tecnicos`, `DELETE /tecnicos/{id}`, `PATCH /tecnicos/{id}/glass` (SUPERTECNICO).
- **ComponenteController**: `GET /componentes`, `/gestionados`, `/stock-bajo`, `/agrupados`, `/chasis?color=`, `/evolucion-stock?granularidad&desde&hasta`; `POST /componentes`; `PUT /{idCom}` (optimista); `PATCH /{idCom}/stock-minimo|stock (delta)|activo`; `DELETE /{idCom}`.
- **ProveedorController** (SUPERTECNICO): `GET /proveedores`, `/activos`, `/{id}/tiene-pedidos`; `POST`; `PATCH /{id}/activo|divisa`; `DELETE /{id}`. (Filtro `?tipo=` por suppliers de terminales: `plan-futuro.md`.)
- **TelefonoController**: `GET /telefonos`, `/{imei}/exists`, `/{imei}/modelo` (BD → imeicheck, solo doc cliente); `POST /telefonos` (upsert modelo); `PATCH /{imei}/observacion` (solo doc cliente); `DELETE /{imei}`. No documentados en contrato: `GET /telefonos/inventario`, `PATCH /{imei}/atributos` (`plan-futuro.md`).
- **TipoCambioController**: `GET /tipo-cambio/{divisa}` (servidor llama a Frankfurter y cachea).
- **CompraController** (SUPERTECNICO): `GET /compras`, `/pendientes`, `/cantidad-pendiente/{idCom}`; `POST`; `PUT /{id}` (optimista); `PATCH /{id}/confirmar-recibido|confirmar-parcial|recibir-resto|confirmar-alterado|cancelar` (+ `desrecibir` solo en autorización). `/compras-otros` (CHANGELOG 0.12.0) sin contrato.
- **ReparacionController**: lecturas `GET /reparaciones` (raw, solo servidor), `/historial?tecnico=`, `/historial/imei/{imei}`, `/asignaciones?tecnico=`, `/asignaciones/{idRep}`, `/asignaciones/{idRep}/solicitudes`, `/{idRep}/detalle-edicion`, `/{idRep}/referenciadora`, `/imei/{imei}`, `/imei/{imei}/count|incidencia-activa|tiene-asignacion?tecnico=|tecnicos-asignados|ya-reparados?excluir=`, `/estadisticas`, `/estadisticas/puntos`; escrituras `POST /reparaciones` (solo servidor), `POST /asignaciones` (SUPERTECNICO), `POST /completa` (transacción reparación+piezas), `POST /{idAsig}/agotar-componente`, `POST /{idRep}/incidencia` (SUPERTECNICO), `PATCH /{idRep}/completar`, `PATCH /asignaciones/{idRep}/tecnico` (optimista), `PATCH /asignaciones/{idRep}/urgente` y `PATCH /asignaciones/{idRep}` (solo doc cliente), `PUT /{idRep}` (editar piezas), `DELETE /imei/{imei}/incidencia-activa`, `DELETE /asignaciones/{idAsig}`, `DELETE /{idRep}`. Sin contrato: `asignaciones/completadas-hoy`, por-cerrar, chasis, entrega-glass, asignaciones-activas/acciones (CHANGELOG 0.13–0.16.1).
- **ReparacionComponenteController**: `GET /reparacion-componentes/{idRep}`; `POST`; `DELETE /{idRep}/{idCom}`; `PATCH /{idRep}/incidencia`; `DELETE /{idRep}/incidencia` (SUPERTECNICO los dos últimos).
- **SolicitudController** (SUPERTECNICO): `GET /solicitudes/count`, `GET /solicitudes?estado=`, `PATCH /{idRc}/estado|limpiar`.
- **DificultadController**: `GET /valores-dificultad`; `PUT` (ADMIN).
- **LogController** (ADMIN): `GET /logs` (filtros en servidor, CHANGELOG 0.10.0).
- Códigos: 200/201/204, 400, 401, 403, 404, 409 (lock o usuario duplicado), 422, 500.
- Endpoints de F2 sin contrato escrito: `GET /lotes`, `POST /lotes/verificar`, `POST /lotes/importar`, revisión, envíos/devoluciones, `PUT revision-logistica` (no-op a eliminar) (`plan-futuro.md`).

**Discrepancias cliente vs servidor**:
1. Forma de respuestas escalares: cliente `{tasa}`, `{result}`, `{cantidad}`, `{count}`, `{idRep}`; servidor unifica en `{value}` (tipo-cambio, tiene-reparaciones, tiene-pedidos, cantidad-pendiente, solicitudes/count, referenciadora, incidencia-activa, tiene-asignacion).
2. `PUT /reparaciones/{idRep}`: cliente envía `updatedAt`; servidor documenta `piezaViejaRota` y no `updatedAt`.
3. Solo en cliente: `cambiar-password`, `telefonos/{imei}/modelo`, `telefonos/{imei}/observacion`, `tecnicos-asignados`, `PATCH asignaciones/{idRep}/urgente`, `PATCH asignaciones/{idRep}`. Solo en servidor: `GET/POST /reparaciones`, `POST/DELETE /tecnicos`, `PATCH tecnicos/{id}/glass`, `excluir/incluir-estadisticas`, `estadisticas/puntos`, `valores-dificultad`. Solo en autorización: `desrecibir`, `PATCH usuarios/rol/{id}`.
4. `schema.md`: `Usuario.ROL` solo `ADMIN|TECNICO` (falta SUPERTECNICO), `Telefono` solo IMEI, `Compra_componente.ESTADO` sin `en_camino`, faltan `Cliente`, `Compra_otro`, `Reparacion_borrador`, `Lote`, `Movimiento_telefono`, `Modelo_equivalencia`, `Color_equivalencia`, `Revision`, `Envio*`, `Log_Actividad`, `PuntoEstadistica/PuntoStock`, columnas `ES_CHASIS`, `POR_CERRAR`, `ENTREGADO_*`, `TIPO` en Proveedor, `ID_COM_MASTER`.

## 7. Backlog pendiente de `plan-futuro.md` (checkboxes `[ ]`)

**(a) Afecta a la web / conviene hacerlo en el servidor antes o durante la migración**
- §3 Fase 3 completa: rol LOGISTICA; división limpia de roles; `@PreAuthorize` en todo lo que muta; `PATCH` chasis sin validar prefijo; `DELETE /telefonos/{imei}` sin rol; por-cerrar sin rol en rama de propiedad; body null → 400 en envíos/devoluciones; dedupe server-side devoluciones; eliminar `PUT revision-logistica`.
- §7 Seguridad: autorización por rol en servidor **incluidas estadísticas** (no-ADMIN solo sus filas + agregados de equipo); rate limiting en login; usuario MariaDB no root.
- §2 F2b hardening: IMEI "activo" con el servidor como autoridad; lote todo-conflictos no crea `Lote` vacío; no resolver modelo sin mapear con texto vacío; tests `LoteImportService`/reentrada.
- §2 Atributos SKU: `SkuTelefono.derivar()` + columna SKU; consistencia color teléfono↔chasis (aviso + confirmación al cerrar reparación); regla chasis nuevo ⇒ grado A; **mini-fase auto-revisión al reparar** (batería 100 % auto, chasis ⇒ grado A) antes de v0.17.0; agrupación/conteo por SKU y export (a valorar).
- §2 F2c: "Enviar a externo…" (acción compuesta en servidor); test del hook `marcarIncidenciaYAsignar`.
- §6 Asignaciones: `POST /api/reparaciones` sin `@PreAuthorize` + `insertar` legacy a borrar; test de herencia de urgente; incidencias por IMEI; cambiar TIPO rep↔glass (transacción en servidor); verificar job nocturno de urgentes.
- §6 Carga v2: autodetección de chasis por SKU `cha%` en servidor (`ES_CHASIS = TRUE` en `insertarCompleta` y al crear solicitud); bajar `TOPE_NORMALES_9H` 25→20 (F2b dice "de regalo 25→20" ya hecho; checkbox sin marcar).
- §4 Fase 4: sección de estadísticas de IMEIs (endpoint nuevo); analítica tipo NSYS (calidad por lote/proveedor, tiempos de revisión, modelos rentables). §5: stock mínimo por consumo mensual; unificar `STOCK_MINIMO` en SKU compartidos.
- §6 NSYS: grading con fotos (adjuntos en servidor); certificados imprimibles (mejor generados en servidor/web).
- §7 Testing nivel 2: Spring Boot Test + test de contexto.
- Lógica hoy en cliente (ver §2 de este informe): importador xlsx, mapeos, carga de técnicos, puntos/promedios, balanceo glass automática — decidir si pasa al servidor antes de la web.

**(b) Puramente JavaFX, queda superado por la web**
- Backlogs menores de reviews: TOCTOU del worker de lookup, flash "—", carrera quitar/re-añadir; supplier creado inline no auto-seleccionado; fetches de tasa duplicados; `buscarProveedorPorNombre` duplicado; helper de combos repoblables; anchos de columna; listeners acumulados `colGrado/colLote`; test cabeceras CSV del descriptor; `adaptarFiltrosMaestro`; `EnvioDialog` editable en vuelo; placeholder "Cargando…"; helpers `imeiDeFila`; `cellFactoryConCarga()` duplicada; cachear mapa de porcentajes; javadoc `setIdRep`; hover "N uds" sin cliente; login en hilo de fondo con spinner; loggear reintento del login; guard de solapamiento "Elegir modelo…"; texto botón "Importar N teléfonos"; alert "todos activos"; renombrar CSV maestro (CHANGELOG 0.13.0 ya lo hizo); fix scroll `fixedCellSize` en Estadísticas.
- §7 UI responsive completa (la web lo resuelve por diseño); Nivel 3 TestFX (sustituir por e2e web).
- "Stock: indicar en ambas filas con qué SKU se comparte" y "selector de color oficial en chasis": solo cliente → rehacer directamente en la web.
- §2 Reubicación: guard por rol en `mostrarInventario()` (cliente) — su versión real es (a) autorización servidor.
- Hotfix 0.16.1 "propagar MODELO entre colas" — CHANGELOG 0.16.2 ya lo recoge como hecho; "preselección de modelo solo con piezas activas" = regla de diseño a decidir en la web.

**(c) Independiente de la migración**
- Ejecutar `renombrar-chasis-colores.sql`; script de limpieza de huérfanos; (opcional) cruzar `MODELO_SIN_MAPEAR` con la API; UI para cambiar `TIPO` de proveedor (hoy SQL); `?tipo=` con string vacío (latente).
- Release 0.16.1 checkbox (CHANGELOG ya publica 0.16.1 el 2026-08-31); super smoke pre-v0.17.0; verificar que "Añadir incidencia" etiqueta G en glass.
- Infra: backup diario automático de BD; cerrar SSH por IP pública y rotar root SQL; paso de preproducción a producción; manual de usuario (con 1.0.0).
- Por-cerrar: técnicos desactivados fuera del denominador; mensaje amable del 404 mid-air; desempate por unidades en finde; priorizar cola del pulidor (si algún día se quiere).
- Descartados: sub-ubicaciones finas (chips), diagnóstico automático en dispositivo.

## 8. Versión actual y últimos cambios (`CHANGELOG.md`)

- **Última publicada: 0.16.2 (2026-09-07)**; `[Unreleased]` en curso. `main` lleva F2a/F2b/F2c, suppliers, atributos SKU, urgente por IMEI **sin tag** (v0.17.0 pendiente, `plan-futuro.md` §2); la tienda va por la línea hotfix 0.16.x.
- **Unreleased**: tarjeta del mes compara los dos últimos meses cerrados (variación con signo, verde/rojo); lo de fuera de horario suma pero no promedia (horario del taller + márgenes; requiere servidor con `puntosJornada`); Por encima/Por debajo alineado con las x̄; fecha de cierre en hora de Madrid (antes UTC).
- **0.16.2**: estadísticas por puntos de dificultad (⚙ Valores, tarjetas, promedio, IMEIs típicos, popover); 👥 exclusión de técnicos; glass automática al asignar ("Lleva glass", técnico habilitado con menos carga, pastilla "auto"); Técnicos de glass (`ES_GLASS`); modelo compartido por IMEI y técnico propuesto por cola en el modal; "cada uno ve solo las suyas" (restricción de cliente); finde suma no promedia; fixes de etiquetas SKU, Enter en modelo y modal en pantallas pequeñas.
- **0.16.1 (2026-08-31)**: entrega del teléfono al técnico de glass (`ENTREGADO_AT/POR`, "→ técnico", "Llegó", deshacer), "sin teléfono no hay glass", píldoras "Glass:"/"Rep:" bidireccionales; 422 con mensaje real; campana sin componentes desactivados. Migración `migracion-entrega-glass.sql` (orden ALTER → servidor → cliente).
- **0.16.0 (2026-07-10)**: carga por capacidad diaria v2 (chasis 1/8, glass 1/17, normal 1/25; jornadas 9/8/6 h), endpoint `completadas-hoy`, ventana de carga con toggle Pedidos|Total y doble barra, CSV de Asignaciones espejo.
