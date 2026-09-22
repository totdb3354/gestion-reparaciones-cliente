# Sub-proyecto 3a — Asignaciones del supertécnico y la carga de técnicos en el servidor

**Fecha:** 2026-09-22
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra; las decisiones generales están allí y no se repiten aquí). Antecesores: [Taller técnico](2026-09-16-web-taller-design.md) y [Formulario de reparación](2026-09-19-web-formulario-design.md).
**Repos que toca:** `gestion-reparaciones-web` y `gestion-reparaciones-servidor` (ramas `feature/web-asignaciones`, creadas desde `main`), repo raíz (gitlinks, plan y specs; se queda en `main`)

## 1. Objetivo

Sustituir el placeholder de `/reparaciones/asignaciones` por la vista real: la
**tabla unificada de asignaciones pendientes** del supertécnico — reparación,
glass y pulido en una sola lista —, sus cinco filtros, el menú contextual con
sus editores, el borrado, las dos ventanas de la cabecera (**Carga de técnicos**
y **Técnicos de glass**) y el modo **solo lectura del ADMIN**.

En el servidor sube el primero de los cálculos que hoy hace el cliente: la
**carga diaria por técnico**, con su suite de tests portada tal cual.

Al cerrar, el supertécnico puede ver y gobernar el trabajo repartido desde la
web: reasignar, marcar urgente y chasis, editar comentario, modelo y cliente,
borrar asignaciones y consultar la carga del equipo. Lo único que seguirá
haciendo en el JavaFX es **crear** asignaciones, que es el sub-proyecto 3b.
Entrega: web **v0.4.0**.

La referencia es la línea hotfix que usa la tienda (`hotfix/0.16.3`, solo
lectura), con paridad verificada contra 46 capturas y el inventario del código,
ambos guardados fuera del repo (llevan datos
reales del taller).

## 2. Fuera de alcance

- **El modal "Asignar trabajos"**: es el sub-proyecto 3b. El botón "Asignar"
  existe en su sitio, **deshabilitado y con tooltip**, igual que se hizo en el
  sub-proyecto 2 con las acciones que terminaban en Almacén.
- **La predicción de glass** (`PrediccionGlass`): solo la necesita el modal, así
  que viaja con el 3b.
- **La entrega de glass**: ya está en la web desde el sub-proyecto 1
  (`modules/taller/lib/entregaGlass.ts` y el menú de Pendientes del técnico).
  Aquí solo se **consume** para pintar los badges y las píldoras de esta tabla.
- **El panel de Revisión**: decisión del 2026-09-22, pasa al sub-proyecto 4 con
  Inventario y lotes, que es donde encaja funcionalmente.
- Estadísticas, Almacén y Gestión: sus sub-proyectos.

## 3. Decisiones de este sub-proyecto

| # | Decisión | Por qué |
|---|---|---|
| D1 | El sub-proyecto 3 se parte en **3a (esta vista)** y **3b (el modal)** | Es el bloque más grande del programa. Partido, 3a se mergea, despliega y usa sin 3b, y el trozo de más riesgo queda aislado |
| D2 | Reasignar, urgente y chasis se **calcan**: escriben al instante desde la tabla y el menú, sin confirmación, **pero con un aviso "Deshacer"** unos segundos | En un navegador un clic o una tecla de más cambia un `select` sin querer, y eso mueve trabajo de un técnico a otro en silencio. El deshacer no cambia el gesto y evita el daño |
| D3 | La carga de técnicos se calcula **en el servidor**, con los dos alcances en la misma respuesta | Es la regla de negocio que la spec maestra manda subir. Devolver Pedidos y Total juntos mantiene el toggle instantáneo, como hoy |
| D4 | El refresco periódico se **congela mientras haya un menú, un desplegable, un diálogo o el modal abiertos** | Sin eso, la fila se mueve bajo el cursor y la acción se pierde o cae en otra fila. Verificado en el JavaFX durante las capturas |
| D5 | La tabla **no se ordena por columna** | El orden (urgente → con cliente → resto) es funcional: lo que corre va arriba. Ordenar por otra cosa entierra lo urgente sin avisar. Diferencia deliberada, anotada en la ficha y en el backlog de UI |
| D6 | La ruta la abren **SUPERTECNICO y ADMIN**; el ADMIN entra en solo lectura | Calco del JavaFX: el técnico no tiene esa entrada en el lateral |
| D7 | El filtrado sigue siendo **en memoria**, sobre lo ya cargado | Calco. La lista completa cabe de sobra y evita un viaje por tecla |

## 4. Rutas, navegación y refresco

`/reparaciones/asignaciones` deja de ser `<PendienteDeMigrar>` y pasa a
`<AsignacionesPage />`, dentro de una guarda que admite **SUPERTECNICO y ADMIN**;
el TECNICO recibe el mismo 403 de la web que en el resto de rutas guardadas y no
ve la entrada en el lateral.

Refresco con el `useIntervaloRefresco` del resto de la web, con una diferencia
(D4): un contador de "interacciones abiertas" lo suspende mientras haya un menú
contextual, un desplegable de filtro, un editor o una de las dos ventanas
abiertas, y lo reanuda al cerrarse la última. El pie mantiene el
`Actualizado HH:mm` del JavaFX.

## 5. Servidor (rama `feature/web-asignaciones`; el JavaFX 0.16.x sigue funcionando)

Todo lo de este sub-proyecto es **aditivo**: no cambia ninguna respuesta que el
JavaFX ya consuma.

**Endpoint nuevo — carga de técnicos**

`GET /api/reparaciones/carga-tecnicos` → `{ pedidos: [...], total: [...] }`

Cada elemento lleva `idTec`, `nombre`, `pctHecho`, `pctPendiente`, los dos
desgloses (`normales`, `chasis`, `porCerrar`, `glass`, `enEsperaPieza`) y
`sinJornada`. Exige rol **SUPERTECNICO o ADMIN**.

Devuelve los **dos alcances en una sola respuesta** en vez de un `?alcance=`
porque el toggle Pedidos|Total de la ventana es instantáneo hoy: el cliente
calcula los dos mapas a la vez. Pedirlos por separado metería latencia donde
ahora no la hay.

**Lógica portada:** `CargaTecnicos` pasa del cliente al servidor sin cambios de
comportamiento, y con ella sus 7 tests, que ya cubren el escalado por jornada,
el fin de semana, el alcance Pedidos y las reglas de peso.

**Zona horaria:** el día de la semana se resuelve en **Europe/Madrid**, no en la
zona del proceso. Con UTC, entre medianoche y las 02:00 el fin de semana se
desplaza y la carga saldría a cero un día entero. Hay test para esto.

**Tolerancia que se mantiene:** el tramo "hecho hoy" sale de las asignaciones
completadas hoy. Si esa consulta falla, la respuesta degrada a **solo
pendiente** en vez de dar error, igual que hace hoy el cliente.

## 6. Web

```
modules/taller/asignaciones/
├── AsignacionesPage.tsx        vista, barra de filtros y tabla
├── api.ts                      queries y mutaciones, incluida carga-tecnicos
├── columnas.tsx                las once columnas y sus celdas derivadas
├── filtros.ts                  estado y predicado de los cinco filtros (puro)
├── orden.ts                    el orden de prioridad (puro)
├── MenuAsignacion.tsx          menú contextual, con sus tres variantes
├── CargaTecnicosDialog.tsx     ventana de carga
├── TecnicosGlassDialog.tsx     diálogo de técnicos de glass
└── editores/                   comentario · modelo · cliente
```

Lo puro (`filtros.ts`, `orden.ts`, el conteo de técnicos por IMEI) vive separado
de los componentes: es lo que hizo útiles los tests del sub-proyecto 2. Se
reutilizan `DataTable`, `ComboNavy`, `ConfirmDialog`, `AlertaProvider` y el
selector de cliente ya existentes.

El modal del 3b colgará de esta misma carpeta, en su propia subcarpeta, para no
repetir la deuda del `estado.ts` de 1.097 líneas del sub-proyecto 2.

## 7. La tabla

Una sola lista con las tres categorías. El tipo **se deriva del prefijo del
identificador** (`A…` reparación, `AG…` glass, `AP…` pulido), como en el cliente.

Orden fijo, estable dentro de cada grupo: **urgente → con cliente → resto**.

| Columna | Contenido |
|---|---|
| Id Asignación | El identificador |
| Tipo | Badge de color del tipo, con la palabra **Chasis** debajo cuando corresponde |
| Técnico | Desplegable de técnicos **dentro de la celda**; cambiarlo reasigna (D2) |
| IMEI | El IMEI y, debajo, la píldora `Glass: <técnico>` o `Rep: <técnico>` según el lado |
| Modelo | Nombre traducido del modelo |
| Fecha asignación | `yyyy/MM/dd HH:mm` |
| Comentario | Texto; al pulsar sobre él se abre el popup de lectura |
| Cliente | Nombre, o vacío |
| Asignado por | Quién la creó, o vacío |
| Estado | Hasta cuatro badges apilados (§8) |
| — | Papelera de borrar; oculta para el ADMIN |

La fila lleva una **franja de 8 px** a la izquierda: ámbar si tiene solicitud de
pieza, roja si es incidencia, transparente si no.

## 8. La columna Estado

Apila, en este orden, los que apliquen:

1. **Urgente**
2. **Por cerrar**
3. El badge de **entrega de glass** (`→ <técnico>` arriba, `Llegó hh:mm` o
   `Llegó dd/MM` en la glass), con su tooltip
4. Y **uno solo** de estos, en cascada excluyente: **Incidencia**; si no,
   **Recibido** / **En camino** / **Solicitud** según el estado de la solicitud
   de pieza; si no, y la fila no es urgente, **Normal**

## 9. Filtros

| Filtro | Comportamiento |
|---|---|
| IMEI | Admite **varios separados por comas** y exige los **15 dígitos**: un IMEI incompleto se marca en rojo y **no filtra**. Al completar los 15 se añade una coma para seguir escribiendo |
| Técnico | Selección múltiple. **Vacío significa "no filtrar"**, no "ninguno" |
| Cliente | Selección múltiple con el sentinel **(Sin cliente)**. Arranca con **todos marcados** y la etiqueta "Todos"; se repuebla en cada carga con los clientes presentes |
| Tipo | Reparación / Glass / Pulido. Vacío = todos |
| Estado | Solicitudes pieza / Incidencias / Asignaciones, combinados con **O**. "Asignaciones" son las que no tienen ni solicitud ni incidencia |
| Limpiar filtros | Resetea los cinco, técnico y cliente incluidos |

El contador de la cabecera muestra las filas **tras los filtros**; el badge del
lateral sigue siendo el total.

## 10. Menú contextual, editores y borrado

Los ítems dependen de la categoría de la fila y del rol:

| Ítem | Reparación | Glass | Pulido | ADMIN |
|---|---|---|---|---|
| Copiar celda | sí | sí | sí | sí |
| Editar comentario | sí | sí | sí | no |
| Editar modelo | no | no | sí | no |
| Editar cliente | sí | sí | sí | no |
| Marcar/Quitar urgente | sí | sí | no | no |
| Marcar/Quitar chasis | sí | no | no | no |

El texto de urgente y chasis alterna según el estado de la fila. "Copiar celda"
copia la celda sobre la que se abrió el menú.

**Editores:** comentario (área de texto), modelo (solo pulido: buscador y lista
con el actual preseleccionado) y cliente (buscador, opción explícita
**— Sin cliente —**, y bloqueo optimista: si otro lo cambió mientras tanto, aviso
y recarga).

**Borrado:** confirmación con el identificador en el título y el texto *"El
técnico dejará de verla en su lista de pendientes"*; si la fila es una
incidencia, el texto añade que la incidencia se marcará como no activa. **No
pide motivo.**

## 11. Carga de técnicos y técnicos de glass

**Carga de técnicos.** Toggle **Pedidos | Total** (Pedidos por defecto), una fila
por técnico activo ordenada de mayor a menor. Cada fila lleva el nombre, dos
barras en la misma escala —arriba el total del día con color por nivel, debajo y
más fina el progreso de lo completado hoy— las cifras correspondientes y un
tooltip con el desglose (*"Pendiente: … — Hecho hoy: …"*, omitiendo los ceros).
Sin jornada, las cifras son un guion: no hay porcentaje sin jornada. Al pasar el
ratón, el resto de filas se atenúa. **Al pulsar una fila, la ventana se cierra y
la tabla queda filtrada por ese técnico.**

**Técnicos de glass.** Un check por técnico activo y la nota que explica la
regla. Al aceptar **solo se mandan los cambios**, y si alguno falla el diálogo no
se cierra. Para el ADMIN, los checks están deshabilitados y solo hay un botón de
cerrar.

## 12. Modo solo lectura del ADMIN

Desaparecen el botón "Asignar", la columna de la papelera y el contador del
lateral; el menú contextual se queda en "Copiar celda"; el desplegable de técnico
se muestra como texto plano, y los checks de "Técnicos de glass" quedan
deshabilitados. Los filtros y la ventana de carga funcionan con normalidad.

Esto es la capa visible. Las escrituras que la vista usa ya exigen el rol en el
servidor, de modo que ocultar los controles es comodidad, no la protección.

## 13. Diferencias y calcos

| Asunto | Decisión |
|---|---|
| Orden por columna | **Diferencia deliberada**: la web podría ordenar y no lo hace (D5) |
| Reasignar / urgente / chasis | **Calco** del gesto, **más** un aviso con "Deshacer" (D2) |
| Refresco | **Calco** del intervalo, **más** la congelación mientras se interactúa (D4) |
| Carga de técnicos | **Calco** de la presentación; el cálculo cambia de sitio, no de resultado (D3) |
| Entrega de glass | **Calco**; ya existía en la web y aquí solo se consume |
| Filtrado en memoria | **Calco** (D7) |

Cualquier diferencia nueva que aparezca al comparar las capturas se decide con
el usuario al cerrar las fichas, no sobre la marcha.

## 14. Errores

Se reutiliza el mapeo de errores del sub-proyecto 0: sin conexión y timeout
muestran el banner; 401 lleva al login; 403 muestra la pantalla de permiso; 409
en el editor de cliente avisa y recarga. Si la carga de técnicos falla, la
ventana se abre con un mensaje en vez de la lista, y **la tabla no se ve
afectada**: son dos consultas independientes.

## 15. Paridad, tests y verificación

- **Fichas de paridad** en `docs/paridad/asignaciones.md`, marcadas contra las 46
  capturas y el inventario del código. Son el criterio de aceptación.
- **Web:** tests unitarios de lo puro (filtros, orden, conteo) y de componentes
  con Testing Library, siguiendo lo hecho en los sub-proyectos 1 y 2.
- **Servidor:** los 7 tests de `CargaTecnicos` portados, más los del endpoint
  (roles, los dos alcances, la degradación a solo-pendiente y la zona horaria).
- **Smoke** Playwright con el supertécnico contra producción, en serie.
- **Capturas** de la web al cerrar, para el archivo. La comparación fina lado a
  lado sigue aplazada al final del programa, cuando estén todas las vistas.

## 16. Criterios de cierre

1. La ruta muestra la tabla real, con las once columnas y los badges correctos.
2. Los cinco filtros y "Limpiar filtros" se comportan como la ficha describe.
3. Reasignar, urgente, chasis, los tres editores y el borrado funcionan, cada uno
   con su aviso de deshacer o su confirmación.
4. Las dos ventanas de la cabecera funcionan, incluido el filtrado al pulsar una
   fila de carga.
5. El ADMIN ve la vista en solo lectura y el TECNICO no puede abrirla.
6. Suites en verde en los tres repos y smoke en verde.
7. Desplegado en la VM, servidor antes que web, con el contrato publicado
   idéntico al que consume la web.

## 17. Riesgos

| Riesgo | Mitigación |
|---|---|
| El desplegable dentro de la celda es incómodo con teclado y lector de pantalla | Se reutiliza el componente de selección ya existente en la web, que ya resolvió esto; y el aviso "Deshacer" cubre el cambio accidental |
| La congelación del refresco se queda pegada si algo no cierra bien | El contador de interacciones se libera también al desmontar; hay test |
| La carga de técnicos cambia de resultado al mover el cálculo | Los 7 tests viajan con el código y se ejecutan en el servidor sin tocar sus expectativas |
| La tabla crece y el filtrado en memoria se nota | Hoy son decenas de filas. Si algún día molesta, el filtro sube al servidor sin cambiar la interfaz |

## 18. Deuda y mejoras que quedan para más adelante

- Ordenar por columna, si el uso lo pide (D5).
- Topes de carga configurables y por técnico, que ya estaban apuntados en la
  spec original de capacidad diaria.
- La franja de carga deja fuera del denominador a los técnicos desactivados que
  aún tengan trabajo abierto: los porcentajes pueden sumar menos de 100 sin
  explicación. Viene del cliente y se arrastra tal cual.
