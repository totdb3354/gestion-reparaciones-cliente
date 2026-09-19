# Sub-proyecto 2 — Formulario de reparación, solicitudes de pieza, borrador y campana

**Fecha:** 2026-09-19
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra; las decisiones generales están allí y no se repiten aquí). Antecesor: [Taller técnico](2026-09-16-web-taller-design.md).
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-formulario`, creadas desde `main`), repo raíz (gitlinks, plan y specs; se queda en `main`)

## 1. Objetivo

Migrar a la web lo único que las vistas del taller aún no pueden abrir: el
**formulario de reparación**, en sus dos modos (completar una asignación y editar
una reparación hecha), con todo lo que cuelga de él — solicitud de pieza,
componente agotado, acciones "otro", guardado por fila y **borrador
persistente** — y la **campana de notificaciones** del supertécnico, donde esas
solicitudes se criban. La referencia es la línea hotfix que usa la tienda
(`hotfix/0.16.3`, solo lectura), con paridad verificada contra 36 capturas.

En el servidor: el técnico de cada escritura del formulario sale del token y se
verifica la propiedad de la asignación; roles en solicitudes de stock y en el
ajuste de stock; autodetección de chasis por SKU; contrato con nullabilidad para
lo que la web empieza a consumir.

Al cerrar, un técnico puede hacer su jornada completa en la web (ver pendientes,
completar, pedir piezas) y el supertécnico puede corregir reparaciones y cribar
solicitudes. Entrega: web **v0.3.0**.

## 2. Fuera de alcance

- Alta de la **solicitud preventiva** (Stock → "Solicitar pieza") y todo Stock, Pedidos y Proveedores: sub-proyecto 4. La campana muestra y gestiona las preventivas que existan, pero no las crea.
- Las acciones de la campana que terminan en Almacén ("Pedir piezas", "Pedir", "Pedir todas las piezas", "Ver Stock Completo", "→ Ir a pedidos"): existen y están deshabilitadas con tooltip hasta el sub-proyecto 4 (§9).
- Apartado Asignaciones del supertécnico: sub-proyecto 3. Los datos para probar se crean con el JavaFX (§11).
- Mejoras de comportamiento sobre el JavaFX: el programa es calco fiel; las detectadas se anotan para después del corte (§14).
- El rol ADMIN no tiene nada en este sub-proyecto (sin campana, sin Pendientes, sin "Editar").
- El resto de la autorización del servidor que este sub-proyecto no toca: sub-proyecto 7.
- Separar la SPA en su contenedor `web`: aplazado (decisión del 2026-09-19).

## 3. Decisiones de este sub-proyecto

| # | Decisión | Motivo |
|---|---|---|
| D1 | Campana completa; los botones que llevan a Almacén, deshabilitados con tooltip "Disponible con Almacén (próxima entrega)" | Mismo patrón que "Añadir"/"Editar" en el sub-proyecto 1; el supertécnico ya criba solicitudes; el 4 solo habilita |
| D2 | "✓ Ya reparado" solo en modo Editar, como el JavaFX; el flujo nuevo no avisa de piezas ya cambiadas en el IMEI | Calco fiel. El javadoc del controlador está desfasado: el código solo lo aplica en `initEditar` |
| D3 | El formulario es un **diálogo modal grande gobernado por la URL** | Calco visual de la ventana modal; Atrás cierra igual que ✕; F5 reabre y recupera el borrador |
| D4 | Servidor: lo de la spec maestra **más** propiedad verificada en los endpoints que el formulario usa | Ya se abren esos métodos y sus tests; el servidor es la fuente de verdad de quién hace cada trabajo |
| D5 | El borrador usa **el mismo JSON que el JavaFX** (`BorradorContenido`); el servidor lo sigue guardando opaco | Un borrador empezado en un cliente se abre en el otro el día del corte; un JSON ilegible = formulario limpio |
| D6 | Lógica del formulario en un **reductor puro** (`estado.ts`) con selectores; los componentes solo pintan y despachan | Las reglas son muchas y se cruzan; se prueban una a una sin DOM; es el estilo de `taller/lib` |
| D7 | Rarezas del JavaFX **calcadas a propósito** (lista en §9.2) | Paridad total; se revisan después del corte |
| D8 | El diálogo `abrirSolicitud` ("Pieza pendiente: …") no se migra | Código muerto: su botón solo es visible deshabilitado, como indicador "⚠ En camino" / "✓ Recibido" |
| D9 | Los datos de prueba en producción se crean con el JavaFX de referencia apuntando a producción | Sin SQL manual; camino real; producción solo tiene usuarios y datos de prueba |
| D10 | La tabla de autorización por endpoint deja el repo público del servidor; en el repo queda una versión corta sin tabla | Los repos son públicos; la tabla completa vive en la documentación privada del proyecto |

## 4. Rutas, navegación y refresco

Rutas hijas de las páginas existentes (el diálogo se pinta sobre la página, que sigue montada debajo):

| Ruta | Modo | Quién |
|---|---|---|
| `/reparaciones/pendientes/reparar/:idAsignacion` | nuevo | TECNICO y SUPERTECNICO, sobre asignaciones propias |
| `/reparaciones/pendientes/glass/reparar/:idAsignacion` | glass (asignación `AG…`) | TECNICO y SUPERTECNICO, sobre asignaciones propias |
| `/reparaciones/historial/editar/:idRep` y `/reparaciones/historial/glass/editar/:idRep` | editar | SUPERTECNICO |
| `/reparaciones/imeis/:imei/editar/:idRep` | editar | SUPERTECNICO |

- "Añadir reparación" / "Añadir glass" (Pendientes) y "Editar" (menú contextual de Historial y del detalle de IMEIs, solo filas `R`/`G`) dejan de estar deshabilitados y navegan a la ruta.
- ✕, Atrás del navegador y Escape (sin otro diálogo encima) hacen lo mismo. En modo nuevo: volcado del borrador y cierre **sin preguntar**. En modo editar: si hay cambios, diálogo "Salir sin guardar".
- Al cerrar (guardado o no) se invalida la lista de debajo y los contadores de pendientes.
- Acceso directo por URL a algo ajeno o con rol insuficiente: el servidor responde 403 y la web muestra el mensaje genérico ya pactado y vuelve a la lista.
- Título de la pestaña del navegador: "Nueva reparación — IMEI <imei>" / "Editar reparación — <idRep>".
- La campana refresca con `useIntervaloRefresco` (5 s) y al recuperar el foco la ventana; el panel abierto refresca al mismo ritmo y no redibuja si nada cambió.

## 5. Servidor (rama `feature/web-formulario`; el JavaFX 0.16.x sigue funcionando)

1. **Técnico del token y propiedad de la asignación** en `POST /api/reparaciones/completa`, `POST /api/reparaciones/{idAsignacion}/filas`, `POST /api/reparaciones/{idAsignacion}/agotar-componente` y `GET|PUT|DELETE /api/reparaciones/{idRep}/borrador`: el técnico efectivo es el del token; el `idTec` del cuerpo se **ignora** (no se rechaza: el JavaFX lo sigue enviando); la asignación debe ser de ese técnico o la respuesta es 403. La regla es la misma para TECNICO y SUPERTECNICO: verificado en el cliente de referencia, el formulario solo se abre desde "Mis pendientes", que carga las asignaciones del técnico de la sesión, y siempre envía ese técnico; ningún flujo completa asignaciones de otro. `PATCH /api/reparaciones/{idRep}/completar` (sin llamadores en los clientes) recibe la misma regla. Vive en un componente único junto a `FiltroTecnico`, con tests propios.
2. `PUT /api/reparaciones/{idRep}` (editar): exige SUPERTECNICO, único rol que ofrece "Editar" en los clientes. Mantiene el bloqueo optimista (`updatedAt`, 409).
3. **Roles**: `/api/solicitudes-stock` — crear: TECNICO y SUPERTECNICO; listar, contar, cambiar estado y borrar: SUPERTECNICO, con lectura para ADMIN como fija la spec maestra. `PATCH /api/componentes/{id}/stock`: SUPERTECNICO. Verificado: ningún cliente lo llama (ni la línea hotfix ni `main`); el stock se mueve dentro de las transacciones del servidor (completar, editar, agotar, recibir pedidos), así que restringirlo no afecta a la tienda.
4. **Autodetección de chasis por SKU** (decisión de producto de julio): la asignación pasa a `ES_CHASIS = TRUE` al completar con una pieza cuyo SKU empieza por `cha` (`insertarCompleta` y `guardarFilaIndividual`) y al crear una solicitud de pieza `cha…` (`agotarComponente` y solicitud en `insertarCompleta`). Nunca se quita automáticamente; el toggle manual sigue igual.
5. **Contrato**: `required` y nullabilidad en los DTO que la web empieza a usar (componente, solicitud resumen, solicitud de stock, solicitud por asignación, detalle de edición, asignación activa, peticiones de completar, fila, agotar y editar). Snapshot OpenAPI regenerado.
6. Sin cambios de esquema de base de datos.
7. **Arranque**: la suite no levanta el contexto Spring; antes del merge se arranca el servidor en local y se comprueba un 401, un 403 y un 200.
8. **Documentación**: `docs/autorizacion_endpoints.md` queda en versión corta (mecanismo, criterio general, dónde están los tests), sin tabla por endpoint (D10).

## 6. Web

Todo dentro de `src/modules/taller/`. Sin dependencias nuevas.

### 6.1 `taller/formulario/`

| Unidad | Responsabilidad | Depende de |
|---|---|---|
| `estado.ts` (pura) | Tipos del estado, reductor y selectores. Modos `nuevo`, `glass`, `editar`. Todas las reglas de §7 | tipos del contrato |
| `borrador.ts` (pura) | `serializar`, `aplicarBorrador`, `borradorVacio`, con el formato de §8 | `estado.ts` |
| `api.ts` | Consultas y mutaciones de §6.3 | cliente tipado OpenAPI |
| `useBorrador.ts` | Retardo de 2 s, volcado al cerrar/desmontar, bandera "recuperando", borrado tras guardar | `borrador.ts`, `api.ts` |
| `FormularioReparacion.tsx` | Orquesta carga, reductor, borrador, guardado y cierre | todo lo anterior |
| `CabeceraFormulario`, `FilaComponente`, `SubFilaAgotado`, `OtrasAcciones`, `ZonaGuardar` | Pintan estado y despachan acciones; sin lógica propia | `estado.ts` |
| `DialogoObservacionFila`, `DialogoSolicitarPieza`, `DialogoDescripcionSolicitud`, `DialogoSalirSinGuardar` | Diálogos sobre el `Dialog` compartido | `shared/ui` |

`taller/lib/modelos.ts` y `piezas.ts` se amplían con `extraerModelo(sku, prefijo)`, la traducción de modelos y el orden y nombre de los tipos (Batería, Chasis, Pantalla, Cámara; Glass y Marco en glass).

### 6.2 `taller/notificaciones/`

| Unidad | Responsabilidad |
|---|---|
| `api.ts` | Contadores, listas `PENDIENTE` y `RECHAZADA` de solicitudes urgentes y preventivas, componentes con stock bajo, cambio de estado, limpiar y borrar |
| `alertas.ts` (pura) | Predicado de alerta (componente master, activo, stock ≤ mínimo) y orden: "Sin stock" primero, "Bajo mínimo" después |
| `Campana.tsx` | Icono apagado/encendido, badge con el total de pendientes, pulso cuando hay alertas. Solo se monta para SUPERTECNICO, en la barra superior |
| `PanelNotificaciones.tsx`, `TarjetaSolicitud.tsx`, `TarjetaAlerta.tsx` | Panel flotante anclado a la campana; pestañas Solicitudes y Alertas; se abre en Alertas si hay pulso; tarjetas de fondo alterno |

### 6.3 Llamadas

Lecturas: `GET /api/componentes/agrupados`, `GET /api/reparaciones/asignaciones/{idAsignacion}/solicitudes`, `GET /api/reparaciones/imei/{imei}/asignaciones-activas`, incidencia activa del IMEI, `GET /api/reparaciones/{idRep}/detalle-edicion`, `GET /api/reparaciones/imei/{imei}/ya-reparados`, `GET /api/reparaciones/{idRep}/borrador`, `GET /api/solicitudes` y `/count`, `GET /api/solicitudes-stock` y `/count`, `GET /api/componentes/stock-bajo`.
Escrituras: `POST …/{idAsignacion}/filas`, `POST …/{idAsignacion}/agotar-componente`, `POST …/completa`, `PUT …/{idRep}`, `PUT|DELETE …/{idRep}/borrador`, `PATCH /api/solicitudes/{idRc}/estado`, `PATCH /api/solicitudes/{idRc}/limpiar`, `PATCH /api/solicitudes-stock/{idSol}/estado`, `DELETE /api/solicitudes-stock/{idSol}`.
El plan fija la lista exacta contra el contrato; ninguna escritura nueva en el servidor.

### 6.4 Cambios en lo existente

- Botón de Pendientes y `MenuHistorial`: de deshabilitado con tooltip a navegar.
- `app/shell`: hueco para la campana a la izquierda del usuario.
- Pendientes: franja naranja, badge "Solicitud" y SKU bajo el estado cuando la asignación tiene solicitud pendiente (verificar lo que ya pinta el sub-proyecto 1 y completar).
- Dos retoques de deuda que afectan a los diálogos nuevos: el foco vuelve a la tabla al cerrar un diálogo; `onSesionExpirada` devuelve su `unsubscribe`.

## 7. Comportamiento del formulario (resumen; las fichas de paridad son el criterio de aceptación)

**Cabecera.** "IMEI: <imei>" (en edición, "· Editando <idRep>"); "Filtrar por modelo" con los modelos traducidos. Sin modelo elegido se ocultan las filas y se lee "Selecciona modelo"; con el modelo de la asignación viene precargado. El combo se bloquea en edición y mientras haya solicitudes pendientes. Avisos: naranja "⚠ Este IMEI también está asignado a — Reparación: … · Glass: … · Pulido: …" (con "(tú)"), "⚠ Resuelve incidencia: <idRep>", azul "✓ Borrador recuperado".

**Filas** (una por tipo; el modelo filtra los SKU). Contador con +/−, SKU (sin stock en rojo), stock, "Reutilizado", observación.
- Contador ≥ 1 deshabilita "Reutilizado"; "Reutilizado" marcado deja el contador en 0 y deshabilita +/−.
- Fila activa (contador ≥ 1 o reutilizado): aparece "✓ Guardar fila", que guarda esa fila en el servidor al momento y la bloquea en verde con "✓ Guardada dd/MM HH:mm".
- SKU con stock 0: franja amarilla "⚠ Sin stock disponible. Solicita la pieza para que el admin gestione el pedido." + "Solicitar pieza". Contador igual al stock (sin reutilizado): "⚠ Stock agotado. Puedes descontar los componentes fallidos y solicitar reposición." + "Solicitar y descontar stock".
- Diálogo "Solicitar pieza — <tipo>": texto según el caso, descripción opcional, "Confirmar: solicitar pieza" o "Confirmar: descontar N ud. de stock y solicitar", "Cancelar". Confirmar es **local**: bloquea la fila y muestra la franja verde "✓ Solicitud de reposición pendiente — <descripción>" con lápiz; se guarda al terminar.
- Lápiz: "Editar descripción de solicitud" con "Guardar descripción", "Cancelar solicitud" y "Cancelar".
- Solicitud ya guardada (al reabrir): misma franja verde; SKU y +/− bloqueados; "Reutilizado" disponible salvo "en camino" (marcarlo y terminar resuelve la solicitud con pieza reutilizada). Indicadores: "⚠ En camino" y, con la solicitud gestionada y stock disponible, "✓ Recibido" con la fila desbloqueada. Las rechazadas no se muestran: la fila vuelve a "Sin stock" con el SKU preseleccionado.

**Otras acciones.** Sección "OTRAS ACCIONES" con contador; "+ Añadir acción" añade una línea (campo, "✓ Guardar" activo con texto, papelera) y se deshabilita mientras haya una línea vacía; guardar bloquea la línea con "✓ Guardada <fecha>".

**Guardar.** La zona inferior aparece cuando hay algo activo, una solicitud cancelada o algo ya guardado. "Terminar asignación" → primer clic "✓ Confirmar terminar" → segundo clic guarda todo (agotados, solicitudes, filas) en el orden del JavaFX, borra el borrador y cierra. Con solicitud pendiente la asignación queda abierta.

**Glass.** Asignación `AG…`: solo filas Glass y Marco más otras acciones; genera `G`.

**Editar.** Fila editada en azul, modelo bloqueado, resto de tipos ya reparados en el IMEI marcados "✓ Ya reparado" y deshabilitados; previsualización de stock "155 → 154" en rojo cuando cambia; sin reutilizado y con contador 0, contador en rojo y sin botón; con cambio válido, "Guardar cambios". También edita una acción "otro". 409 → aviso de modificación concurrente y recarga.

## 8. Borrador

JSON con los campos exactos del JavaFX: `modelo`; `filas[]` con `prefijo`, `idCom` (−1 sin elegir), `cantidad`, `reutilizado`, `observacion`, `solicitudNueva`, `descripcionSolicitud`, `agotadoConfirmado`, `descripcionAgotado`, `guardada`, `idRepGenerado`, `fechaGuardado`; `otros[]` con `descripcion`, `guardada`, `idRepGenerado`, `fechaGuardado`.

- Solo en modo nuevo/glass. Autoguardado 2 s después del último cambio; volcado al cerrar. Borrador vacío (sin filas ni acciones; el modelo solo no cuenta) → se borra en vez de guardarse.
- Al abrir: si hay borrador legible se aplica sin disparar autoguardado y se muestra "✓ Borrador recuperado". Las filas "guardadas" viven en el borrador; al aplicarlo se comprueba contra el servidor que sigan existiendo y, si alguien las borró, se desmarcan y se reescribe el borrador.
- Fallo al autoguardar: silencioso. Dos pestañas sobre la misma asignación: gana la última escritura, como hoy.

## 9. Diferencias y calcos

### 9.1 Diferencias aceptadas respecto al JavaFX
- Diálogo modal dentro de la página en vez de ventana aparte; no es redimensionable ni movible; ocupa casi toda la ventana con un mínimo equivalente a 960×700.
- La URL refleja el formulario abierto; Atrás lo cierra; F5 lo reabre.
- Botones de la campana que llevan a Almacén: deshabilitados con tooltip hasta el sub-proyecto 4.
- La campana refresca por intervalo en vez de por hilo sondeador; mismo efecto visible.

### 9.2 Comportamientos del JavaFX calcados a propósito
- El flujo nuevo no avisa de piezas ya cambiadas en el IMEI.
- El técnico no recibe señal de que su solicitud fue rechazada (la fila vuelve a "Normal").
- "Rechazar", "Recuperar" y la papelera de la campana actúan sin confirmación.
- La tarjeta de solicitud urgente muestra la fecha de la asignación.
- "✓ Confirmar terminar" no vuelve a "Terminar asignación" aunque cambien las filas.
- Cerrar el formulario nuevo nunca pregunta (el borrador lo cubre).

## 10. Errores

- Guardar fila o acción: "No se pudo guardar la fila: <mensaje>" / "No se pudo guardar la acción: <mensaje>"; la fila no se bloquea.
- Terminar o guardar cambios: "No se pudo guardar: <mensaje>"; el formulario sigue abierto y el borrador intacto.
- Agotar componente: "No se pudo registrar componente agotado: <mensaje>".
- 409 en edición: aviso y recarga. 403: mensaje genérico y vuelta a la lista.
- Fallo al leer asignaciones activas del IMEI: no crítico, el formulario abre sin aviso.
- Sin conexión: el banner existente.

## 11. Paridad, tests y verificación

- **Capturas de referencia**: 36, fuera de los repos (documentación privada), con índice y hallazgos. Usuarios y IMEI de prueba; ninguna entra en `docs/`.
- **Fichas** en `docs/paridad/` de la web, cerradas con el usuario antes del plan: `formulario.md`, `notificaciones.md` y actualización de `pendientes.md`, `historial.md`, `imeis.md`. Lo no capturable (gestionada, en camino, "Selecciona modelo", incidencia en cabecera) se documenta desde el código y se marca como tal.
- **Web (Vitest + Testing Library)**, datos sintéticos desde el primer commit: `estado.test.ts` (una prueba por regla de §7), `borrador.test.ts` (ida y vuelta con un JSON del JavaFX anonimizado, corrupto, vacío, filas guardadas borradas), `alertas.test.ts`, componentes del formulario en sus tres modos, `useBorrador` con temporizadores falsos, campana y panel (incluido que TECNICO y ADMIN no la ven), rutas (Atrás cierra, acceso directo, 403).
- **Servidor (JUnit + Mockito)**: propiedad por endpoint (dueño 2xx, ajeno 403, supertécnico), `idTec` del cuerpo ignorado, roles, autodetección de chasis; más el arranque manual.
- **E2E (Playwright, credenciales solo por entorno)**: `formulario.spec.ts` — técnico: abrir, activar fila, cerrar, reabrir con "Borrador recuperado", guardar fila, solicitar pieza, terminar; supertécnico: campana con badge, rechazar, recuperar, abrir "Editar" y salir sin guardar. Se ejecuta también el test del técnico de `taller.spec.ts` pendiente del sub-proyecto 1 y la comprobación manual del rol ADMIN.
- **Datos de prueba en producción** (D9): asignaciones sobre IMEI sintéticos creadas con el JavaFX de referencia apuntando a producción. El smoke **escribe** (consume stock de prueba, crea solicitudes); producción solo tiene usuarios y datos de prueba. El guion de limpieza queda en la documentación privada.
- **Capturas de la web**: `capturas-web.mjs` ampliado a los mismos estados; informe visual comparado.

## 12. Criterios de cierre

1. Suites en verde: web, servidor y cliente JavaFX (sin tocar, como control de compatibilidad).
2. Servidor arrancado en local con 401/403/200 comprobados.
3. Fichas de paridad con todas las casillas marcadas o con diferencia aceptada por el usuario.
4. Revisión final de la rama "ready to merge"; revisión visual con cero críticos.
5. Desplegado en producción (servidor, contrato comparado, web) y smoke en verde, incluido el pendiente del técnico y la comprobación del ADMIN.
6. Merges `--no-ff`, tag `v0.3.0` y push, cada uno con el OK explícito del usuario; gitlinks en el raíz.

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Lógica densa (el controlador de referencia pasa de 2.000 líneas) | Reductor puro con tests derivados de las capturas; fichas cerradas antes del código |
| Romper al JavaFX de la tienda con los cambios del servidor | `idTec` del cuerpo ignorado, no rechazado; verificar en el hotfix quién llama a cada endpoint antes de cerrar roles; suite del cliente como control |
| Borrador compartido entre clientes | Test de ida y vuelta con JSON real anonimizado; JSON desconocido = formulario limpio |
| Escrituras reales en el smoke | Solo datos de prueba; guion de limpieza |
| Arranque de Spring no cubierto por la suite | Validación manual antes del merge |
| Datos reales en repos públicos | Sintéticos desde el primer commit; repaso contra el mapa privado antes de cada push |

## 14. Deuda y mejoras que quedan para más adelante

Después del corte (se anotan en el plan maestro): avisar en el flujo nuevo de piezas ya cambiadas en el IMEI; señal al técnico de solicitud rechazada; confirmación en "Rechazar todo"; fecha real de la solicitud en la tarjeta; "Confirmar terminar" reversible. Sub-proyecto 4: habilitar los botones de Almacén de la campana y el alta de la solicitud preventiva.
