# SP7 parte 1 (7b) — Autorización en servidor, sesiones y dos mejoras de la web

Fecha: 2026-09-28. Sub-proyecto 7 de la migración del ERP a web, primera parte.
Versión del producto: **0.9.0** (servidor y web etiquetados juntos).

Spec maestra: `docs/superpowers/specs/2026-09-13-migracion-web-programa-design.md` (§4.2, §7 fila 7).

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe **el comportamiento
objetivo**: qué rol puede hacer qué, y qué respuesta da el servidor en cada caso.
No enumera qué endpoints están hoy sin comprobación ni describe cómo se
aprovecharía. El detalle operativo —la tabla de endpoints, el reparto por entrega y
quién llama a cada ruta desde cada cliente— vive fuera de git, en
`Apuntes/sp7/`: `inventario-a-autorizacion-servidor.md` (estado real del servidor),
`inventario-b-llamantes-endpoints.md` (qué rol llama a cada endpoint desde la web y
desde el JavaFX) y `sp7b-endpoints-por-entrega.md` (el reparto). La misma regla se
aplica al plan de implementación y a los mensajes de commit: se describe el
comportamiento nuevo, nunca el hueco anterior.

## 1. Objetivo

Que el servidor sea la fuente de verdad de los permisos. Hoy la separación de roles
vive en buena parte en la pantalla de cada cliente; al terminar esta parte, cada
endpoint exige por sí mismo el rol que le corresponde y, donde el dato pertenece a
una persona, comprueba que quien llama es su dueño.

Además: que desactivar o borrar a un usuario surta efecto de inmediato, que los
intentos fallidos de contraseña dejen rastro, que el administrador pueda
restablecer contraseñas ajenas, que el registro de auditoría sobreviva al borrado
del usuario, y que la web cierre la sesión por inactividad y avise cuando hay una
versión nueva desplegada.

## 2. Restricción que manda sobre todo lo demás

**El mismo servidor lo usa el cliente JavaFX del taller, y ese cliente no se puede
modificar.** Cerrar un permiso no puede romper nada que el JavaFX haga hoy de forma
legítima. La referencia de lectura es la rama `hotfix/0.16.3` del repositorio raíz,
consultada con `git show` y `git grep`, nunca con `checkout`.

De ahí salen tres reglas de trabajo:

1. Para cada endpoint que se cierre, el inventario privado dice qué rol lo llama
   desde la web **y** desde el JavaFX, con fichero y línea de los dos.
2. Que una ruta aparezca en un DAO del cliente no prueba que el taller la use: hay
   métodos de DAO y vistas enteras que ningún sitio invoca. La prueba es el camino
   completo hasta una vista viva.
3. Los tests con mocks no bastan. Cada entrega se prueba con el JavaFX real antes de
   llegar a la máquina del taller (§8).

Lección que origina la regla 3 (2026-09-27): un arreglo que decidía la existencia de
una fila consultando una vista filtrada rompió el borrado de pulidos desde la vista
Agrupado del JavaFX, y la suite en verde no lo detectó.

## 3. Decisiones

| # | Decisión | Motivo |
|---|---|---|
| D1 | **Dos specs.** Esta es la de 7b. La de 7c (backup, fail2ban, `--no-deps nginx`) se escribe aparte | 7c depende de una decisión pendiente del usuario: dónde se guardan las copias fuera de la máquina |
| D2 | **La invalidación de sesión se hace comprobando el usuario en cada petición**, sin cambio de esquema y con caché corta | Cubre desactivar, borrar y cambiar el rol sin migración. La alternativa con versión de credenciales cubriría además el cambio de contraseña, a cambio de una migración y cinco ficheros más |
| D3 | **Límite aceptado: cambiar la contraseña no invalida las sesiones ya abiertas.** Queda anotado; la versión de credenciales se valora después de medir D2 en producción | El caso que D2 no cubre es el menos probable en una red interna |
| D4 | **La respuesta ante una sesión que ya no vale es 401, nunca 403** | Solo el 401 expulsa al usuario en los dos clientes. Un 403 deja a la persona dentro con un diálogo y la sesión viva |
| D5 | **Tres entregas más un cierre**, cada una desplegable y probable por separado | Separa lo que no puede romper al taller de lo que sí. Si algo falla, se sabe cuál de los cambios fue |
| D6 | **Estadísticas: se cierra el acceso, no se toca el cálculo.** Un no-administrador recibe solo sus filas | Los agregados de equipo calculados en servidor son el trabajo de Estadísticas (SP5), aplazado a después del corte |
| D7 | **La API de primera generación no se borra de golpe:** primero responde 403 y anota cada intento; se borra cuando pase el periodo de observación sin ninguno | El usuario quiere retirarla, pero con prueba real de que nadie la usa, no con un `grep` |
| D8 | **El registro de auditoría guarda el nombre del usuario** y su clave admite nulo, así que borrar al usuario ya no borra sus líneas | Una auditoría que desaparece con el auditado no sirve. Revisa la decisión G8 del SP6 |
| D9 | **Los fallos de contraseña se registran y el freno por cuenta es una espera creciente**, no un bloqueo | Bloquear permitiría dejar fuera a un compañero a propósito, y en un taller con equipos compartidos obliga al administrador a desbloquear |
| D10 | **El administrador restablece con una contraseña temporal de un solo uso**, y el sistema obliga a cambiarla al entrar | El administrador no debe conocer la contraseña con la que otra persona firma sus acciones en el registro |
| D11 | **Las dos mejoras de la web entran en la misma 0.9.0** que el servidor | Un solo cierre, una sola tanda de pruebas en producción, y la lista del piloto queda completa de una vez |
| D12 | **El cierre por inactividad usa una marca nueva**, no la marca de sesión de la 0.8.5 | La marca actual mide que la pestaña está viva, no que haya alguien delante, y se renueva al recuperar el foco (§6.1) |
| D13 | **Listas de catálogo abiertas a propósito**: la de técnicos, la de clientes y los catálogos de componentes siguen accesibles a cualquier sesión válida | El técnico las usa hoy de forma legítima desde el JavaFX, y son catálogo, no el trabajo de otras personas. Se documenta como decisión, no como olvido |

## 4. Estado objetivo del servidor

Cinco grupos. El reparto endpoint por endpoint está en `Apuntes/sp7/`.

**4.1 Rutas de primera generación.** Son rutas que las pantallas actuales ya no
usan, sustituidas por otras y nunca retiradas. Responden 403 con mensaje y dejan
una línea en el registro de intentos con el usuario, la hora y la dirección de
origen. No se borran en esta parte (D7).

**4.2 Rutas de módulos que el taller no usa** (lotes, inventario, revisión, envíos,
atributos de teléfono, devoluciones). Exigen SUPERTECNICO. Solo existen en la rama
`main` del cliente, que la tienda no ejecuta, así que cerrarlas no la afecta.

**4.3 Rutas que en pantalla solo alcanza un rol concreto.** Exigen ese rol en el
servidor: la campana de solicitudes con su contador, su cambio de estado y su
limpieza; el catálogo de valores de dificultad; la consulta del tipo de cambio; y
las consultas auxiliares que solo usan las vistas de supertécnico y administrador.

**4.4 Rutas que el técnico usa y se quedan accesibles a su rol, con dueño
comprobado.** Este es el grupo delicado:

- **Completar pulidos en lote**: el servidor comprueba que las asignaciones
  pertenecen a quien llama y que son asignaciones de pulido. Sigue siendo una
  operación del técnico.
- **Listas de asignaciones e historiales** (reparaciones, glass y pulidos): el
  técnico indicado en la consulta se verifica contra el token, extendiendo el filtro
  que ya existe para las seis listas del taller. Un no-administrador recibe lo suyo.
- **Estadísticas y puntos**: un no-administrador recibe solo sus filas (D6).
- **Escrituras del formulario de reparación**: se comprueba que la reparación es de
  quien la edita o la completa.
- **Rutas que la web abre al técnico y el JavaFX no usa**: se filtran por dueño.
- **Editar el modelo de un teléfono**: exige SUPERTECNICO, previa confirmación con
  el código del cliente de que ese rol es el único que lo ofrece en el JavaFX. Si
  resulta que el técnico también puede, se queda abierto y se anota.
- **Borrar un teléfono**: exige SUPERTECNICO. Cuando el teléfono está referenciado
  por una asignación de pulido ya cerrada, el servidor responde con conflicto y un
  mensaje que dice qué lo impide, en lugar de un error interno.
- **Consulta del modelo por IMEI**: se queda accesible al técnico, porque la usa en
  el formulario, y el servidor le pone un freno de ritmo. Dispara una consulta a un
  servicio externo **gratuito**, de modo que el
  freno no evita un coste: evita que una llamada repetida sin control lleve al
  proveedor a limitar el acceso, y que la latencia acumulada de muchas llamadas
  seguidas empuje una respuesta por encima del tiempo máximo aceptado. El ritmo
  permitido son treinta consultas cada diez segundos **por usuario**, y solo gasta
  cupo la consulta que de verdad sale fuera: un IMEI que ya está en la base no cuenta.
- **Los cuatro toggles del menú contextual de pendientes** ya comprueban el dueño;
  se confirma en el código y se les añade cobertura de test.

**4.5 Robustez del filtro de sesión.** Dos arreglos defensivos que no cambian el
comportamiento de un token válido: un identificador ausente en el token no debe
provocar un error interno, y un token sin el dato del rol no debe producir un rol
con nombre nulo. En los dos casos, 401.

## 5. Sesiones, contraseñas y auditoría

**5.1 El usuario se comprueba en cada petición.** El filtro consulta si el usuario
existe y está activo, con la misma condición que ya aplica el inicio de sesión. Si
no lo está, 401 (D4). El resultado se cachea treinta segundos, porque la web sondea de forma
periódica y cada pestaña abierta multiplica las peticiones. Treinta segundos es el
techo de retardo aceptado entre desactivar a alguien y que deje de poder operar. Efecto:
desactivar, borrar o cambiar el rol de alguien surte efecto casi de inmediato, sin
esperar a que caduque su token.

Consecuencia que hay que contar en el manual: quien esté trabajando con el JavaFX en
ese momento verá el aviso de sesión caducada del propio cliente y volverá a la
pantalla de entrada; perderá lo que tenga escrito, salvo el borrador del formulario
de reparación, que vive en el servidor.

**5.2 Intentos fallidos registrados.** Los fallos de inicio de sesión y los de
cambio de contraseña dejan una línea con el nombre intentado, la hora y la
dirección de origen. Hoy no se anota ninguno.

**5.3 Freno por cuenta.** A partir del quinto fallo consecutivo de una misma
cuenta, el servidor responde con una espera que crece con cada fallo hasta un tope
de un minuto. Un inicio correcto reinicia el contador. No bloquea la cuenta (D9). Es
independiente del límite que aplica nginx por dirección, que no se toca: el taller
comparte dirección y el cálculo hecho el 2026-09-27 dice que no hace falta subirlo.

**5.4 Restablecer contraseñas ajenas.** El administrador genera una contraseña
temporal para otro usuario. Al entrar con ella, el sistema le exige cambiarla antes
de poder hacer nada más (D10). El administrador no puede fijar una contraseña
definitiva ajena.

**5.5 Cambiar la propia contraseña** mantiene el mínimo de longitud y el mensaje
actuales, y pasa a tener registro de fallos y freno (5.2, 5.3).

**5.6 El registro de auditoría sobrevive al borrado del usuario.** Cada línea guarda
el nombre además del identificador; al borrar al usuario, el identificador queda a
nulo y la línea sigue diciendo quién hizo qué (D8).

**5.7 Escrituras que hoy no dejan rastro** pasan a registrarse, para que la
auditoría sea completa: entre otras, el borrado de teléfono y las operaciones sobre
piezas de una reparación.

## 6. Web

**6.1 Cierre por inactividad a las dos horas.**

Cuenta como actividad el ratón y el teclado dentro del ERP. **No** cuentan el
refresco automático de las tablas ni el hecho de volver a la ventana. Esto obliga a
una marca nueva: la marca de la 0.8.5 se renueva cada treinta segundos mientras la
pestaña vive y también al recuperar el foco, así que mide presencia de pestaña, no
de persona; reutilizarla dejaría una pestaña del taller sin caducar nunca. Lo que sí
se reutiliza es la forma de escribir y leer marcas, la sincronización entre pestañas
y el camino de expulsión al login con mensaje.

Con la sesión compartida entre pestañas, la actividad de cualquiera de ellas cuenta
para todas.

Un minuto antes de cerrar, aviso con botón para seguir trabajando. El aviso dice qué
se va a perder: el formulario de reparación tiene borrador en el servidor, pero el
modal de asignar trabajos y las líneas de un pedido viven solo en memoria, y hoy el
aviso de salida se silencia justo cuando la sesión desaparece.

**6.2 Aviso de versión nueva tras un despliegue.**

La compilación genera un fichero con la versión, que nginx sirve sin caché. La web
lo consulta cada cinco minutos y, si no coincide con la versión que tiene cargada,
muestra un aviso con un botón para recargar. **Nunca recarga sola**: recargar pierde
lo que la persona tenga escrito.

## 7. Cambios de esquema

Tres, todos aditivos, en `Log_Actividad` y `Usuario`: la columna del nombre en el
registro, el identificador de usuario del registro pasando a admitir nulo con
borrado en cascada a nulo, y la marca de contraseña temporal en usuarios. Se aplican
a las dos bases de datos, porque las dos máquinas ejecutan el mismo servidor, y
siempre antes de desplegar el servidor que los usa: al ser aditivos, el servidor
anterior sigue funcionando sobre el esquema nuevo.

El SQL lo prepara el plan y lo ejecuta el usuario. Antes de escribirlo hay que
comprobar, con el código del cliente, que la vista de registro de actividad del
JavaFX no se rompe al encontrar un identificador nulo. Si se rompe, la parte de 5.6
se replantea (por ejemplo, impidiendo borrar usuarios con historial) y el resto de
la entrega sigue igual.

## 8. Entregas, despliegue y vuelta atrás

**E1 — lo que no puede romper al taller**: §4.1, §4.2, §4.3, §4.5, el borrado de
teléfono y el freno del lookup de IMEI de §4.4.
**E2 — lo delicado**: el resto de §4.4.
**E3 — sesiones**: §5 completa, con sus cambios de esquema.
**Cierre** — borrado de las rutas de primera generación, solo si el periodo de
observación acaba sin un solo intento registrado (D7).

**La web (§6)** se desarrolla en paralelo y no depende del servidor. Se despliega en
el mismo hueco que E3 o justo después, y en todo caso antes del tag 0.9.0 (D11).

**Orden de cada entrega.** Siempre el mismo:

1. **Producción** primero: es la máquina donde hoy no trabaja nadie. Se despliega el
   servidor y se prueba allí.
2. **El JavaFX real contra ese servidor**, antes de tocar la máquina del taller,
   con el cliente de la rama `hotfix/0.16.3` apuntado a producción. Este paso es
   obligatorio y es el que sustituye a la confianza en los mocks.
3. **Preproducción**, fuera de horario, cuando los dos pasos anteriores están en
   verde. Es la máquina donde trabaja el taller.

**Vuelta atrás**: el procedimiento de la guía de la máquina —volver los clones al
commit anterior y reconstruir—. Los cambios de esquema son aditivos, así que un
servidor anterior sigue funcionando sobre el esquema nuevo.

**Periodo de observación** (D7): empieza cuando E1 está en preproducción, porque es
donde el taller genera tráfico real. Dos o tres semanas sin ningún intento
registrado habilitan el borrado. Si el tag 0.9.0 se pone antes de que acabe, el
borrado sale como 0.9.1.

## 9. Verificación

- Suites de servidor y web en verde, con tests nuevos por cada permiso añadido: uno
  que comprueba que el rol correcto pasa y otro que el incorrecto recibe 403, y en
  las rutas con dueño, que el dueño pasa y un tercero no.
- **Dos pruebas manuales obligatorias con el JavaFX** en cada entrega que toque
  pulidos o reparaciones: borrar un pulido desde la vista Agrupado, y completar
  pulidos en lote desde el botón del técnico. La primera existe porque ese borrado
  reutiliza la ruta de reparaciones para filas de pulido y es lo que se rompió el
  2026-09-27.
- Recorrido de los tres roles en la web contra producción tras cada entrega.
- Contrato publicado idéntico al de la rama, como en las entregas anteriores.
- Para la inactividad: prueba en navegador real con la restauración de pestañas
  activada, comprobando que el refresco de tablas y el foco no la impiden, y que el
  aviso previo aparece.

## 10. Fuera de alcance

- **7a, la VPN**: sesión aparte. Notas de partida en `Apuntes/sp7/notas-7a-vpn.md`.
- **7c, backup y fail2ban**: spec propia, pendiente de que se decida dónde van las
  copias fuera de la máquina. Arrastra dos prerrequisitos que salieron del
  inventario: calibrar el límite de peticiones del inicio de sesión, que hoy
  responde con un código distinto del que la documentación supone, y la rotación de
  los registros de nginx, que hoy no existe en ninguna máquina.
- **Limpieza de los datos y usuarios de prueba de producción**: va en el corte (SP8).
- **Agregados de equipo de Estadísticas en servidor**: SP5, después del corte (D6).
- **Aceptado sin arreglo**: el registro de claves de reintento vive en memoria y se
  pierde al reiniciar; lo escrito en los dos segundos previos a una sesión caducada;
  editar el modelo sin control de concurrencia, igual que el JavaFX; el límite de
  quince segundos por petición no se sube.
- **No entran**: el contenedor de web separado, la paginación numerada de los
  historiales y los backlogs menores del SP6.

## 11. Versionado y requisitos de la 1.0.0

Esta parte es la **0.9.0**: servidor y web etiquetados juntos al cerrarla.

Del criterio de salida de la 1.0.0, esta parte cubre **los huecos de autorización
del servidor**. Siguen pendientes: el acceso por VPN (7a), el backup automático con
restauración probada (7c), el piloto, el corte, la retirada del JavaFX, el manual de
usuario y las semanas de uso sin incidencias.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un permiso cerrado rompe un camino legítimo del JavaFX | El inventario privado cita el código de los dos clientes para cada endpoint; tres entregas separadas; prueba con el JavaFX real contra producción antes de tocar la máquina del taller |
| La lista de rutas sin llamante tiene un falso positivo | No se borran en esta parte: primero 403 y registro de intentos, con periodo de observación en la máquina del taller (D7) |
| La comprobación del usuario en cada petición degrada el rendimiento | Caché corta; el criterio de quince segundos por respuesta sigue siendo el juez |
| Una tarjeta de promedio de equipo queda a cero al filtrar estadísticas | Se comprueba en pantalla en la E2; si ocurre, entra el agregado mínimo que la sostenga |
| La vista de registro del JavaFX no admite un identificador nulo | Se comprueba en el código antes de escribir el SQL; si no lo admite, 5.6 se replantea sin bloquear el resto |
| El cierre por inactividad hace perder trabajo | Aviso un minuto antes con botón para seguir, y el aviso dice qué no está guardado |
| El aviso de versión recarga y se pierde lo escrito | El botón lo pulsa la persona; nunca hay recarga automática |

## 13. Documentación

- Este documento y el plan, en el repositorio raíz.
- Reparto por entrega y tablas de endpoints, en `Apuntes/sp7/` (privado).
- El mapa privado de autorización por endpoint se regenera desde el inventario: el
  actual tiene rutas que ya no existen y le faltan controladores. En el repositorio
  del servidor se mantiene la versión corta, sin tabla.
- Ledger en `.superpowers/sdd/progress.md`, sección `# SP7 parte 1`.
- Casilla 7 de `Apuntes/plan-futuro.md` §9 y memoria, al cerrar cada bloque.
