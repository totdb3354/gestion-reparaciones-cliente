# Programa de migración del cliente JavaFX a app web (spec maestra)

**Fecha:** 2026-09-13
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Ámbito:** todo el programa de migración. No se planifica directamente: cada
sub-proyecto de la sección 7 tiene su propia spec y su propio plan.

Documentos de investigación en los que se apoya (leídos y resumidos el
2026-09-13 a partir de toda la documentación del proyecto; los repos son públicos, así que
IPs, secretos, topología de red y nombres de personas se quedan en `Apuntes/`, fuera de git):

- Infraestructura, despliegue y seguridad: `Apuntes/migracion-web-infra-seguridad.md` (PRIVADO, fuera del repo: resume IPs, red y estado del hardening)
- [Dominio y producto](../research/2026-09-13-migracion-web-dominio-producto.md)
- [Specs de diseño por módulo (47 specs)](../research/2026-09-13-migracion-web-specs-por-modulo.md)
- [Inventario del cliente JavaFX (las 23 vistas de la línea 0.16.x, código)](../research/2026-09-13-migracion-web-inventario-cliente-javafx.md)

Toda sesión futura de este programa empieza leyendo esta spec y, si va a
tocar un módulo concreto, el informe de specs por módulo y el inventario del
cliente en la parte de ese módulo.

---

## 1. Objetivo

Sustituir el cliente de escritorio JavaFX (`gestion-reparaciones-cliente`,
25.800 líneas, 25 vistas en `main`) por una aplicación web que ataque el mismo servidor
Spring Boot (`gestion-reparaciones-servidor`) y la misma base de datos
MariaDB, sin que los usuarios de la tienda tengan que reaprender nada.

Motivación (decisión 2026-08-28): el JavaFX tiene los días contados; no se
invierte más en él salvo hotfixes funcionales. La web elimina la instalación
por PC, el reparto por USB, los builds por sistema operativo y abre la puerta
a otros dispositivos.

## 2. Decisiones cerradas

| Tema | Decisión | Motivo |
|---|---|---|
| Estrategia | **Big bang con piloto.** El JavaFX sigue en producción sin cambios salvo hotfixes. Cuando la web tenga paridad total, uno o dos usuarios la usan en paralelo unas semanas con datos reales. Después, corte único y retirada del JavaFX | Usuarios conocidos, un solo corte de adaptación, ambos clientes comparten API y BD así que el piloto no molesta a nadie |
| Paridad | **Total: las 25 vistas de `main`** (la vista Pulido supertécnico y `SelectorModeloDialog` son código muerto y no se migran). **Versión de referencia: `main` con la línea `hotfix/0.16.x` mergeada** (lo que será v0.17.0): la tienda usa hoy 0.16.x (23 vistas) y `main` añade Inventario y Panel de Revisión con lotes, importador y envíos de la Fase 2, ya desplegados en el servidor. El inventario del cliente (informe) cubre la línea 0.16.x; Inventario y Revisión se inventarían al diseñar los sub-proyectos 3 y 4. Las de uso raro (logs, registro, cambiar contraseña, historial de pulido) se hacen las últimas; si aprieta la fecha, el admin conserva el JavaFX unas semanas solo para ellas | Apagar el JavaFX de golpe era el objetivo |
| Aspecto | **Calco fiel del JavaFX actual** en flujos, nombres, menús, columnas, filtros, colores de fila, badges, modales y textos. Sin rediseño visual. El rediseño ERP (sidebar con módulos, topbar, dashboard; spec de julio 2026 y ejemplos en `Apuntes/EjemplosDiseñoERP`) es un **proyecto posterior al corte** | Cero readaptación de la tienda; mezclar rediseño y calco alarga el big bang y rompe el "sin readaptación" |
| Estructura interna | Aunque por fuera calque, **por dentro nace como ERP**: código por módulos (taller, almacén, gestión), rutas por vista, tokens de color y espaciado como variables CSS, y el shell (navegación + contenedor) separado de las pantallas | Hacerlo ahora es gratis; hacerlo después es caro. El rediseño posterior será un proyecto de navegación y piel, no de reescribir vistas |
| Dispositivos | **PC ahora, bases responsive puestas**: rejilla fluida, filtros que envuelven, tablas en contenedor con scroll horizontal, sin `min-width` mayores que la pantalla. No se diseñan vistas móviles | Puerta abierta a tablets y móviles sin rehacer vistas |
| Fuente de verdad | **El servidor.** La autorización por rol y los cálculos de negocio que hoy viven en el JavaFX suben al servidor durante la migración, módulo a módulo, con endpoints aditivos que el JavaFX no necesita adoptar | En una web la API está a un F12 de distancia; y una regla en dos lenguajes diverge |
| Repos | Web: repo nuevo `gestion-reparaciones-web`, **público** como los otros dos (decisión 2026-09-14: los secretos viven solo en la VM y en ficheros ignorados; las capturas de paridad con datos reales se guardan fuera del repo), enganchado al raíz como gitlink. Servidor: **el existente** evoluciona con ramas por sub-proyecto. Cliente JavaFX: congelado y archivado tras el corte | Dos servidores contra la misma BD duplicarían cada hotfix; público evita el token en la VM y no consume cuota de CI |
| Stack | React 19 + TypeScript + Vite; React Router; TanStack Query; TanStack Table; **Tailwind + shadcn/ui**; Recharts; Vitest + Testing Library + MSW; Playwright para smoke por módulo | shadcn copia los componentes al repo y deja el aspecto al 100 % en nuestras manos, lo que hace directo calcar `app.css` |
| Contrato API | springdoc-openapi en el servidor genera OpenAPI desde los controllers; la web genera sus tipos TypeScript de ahí. Los `api_contract.md` de ambos repos y `schema.md` se dan por obsoletos | La revisión documental demostró que están desactualizados (faltan lotes, revisión, envíos, clientes, pedidos-otros, entrega glass) |
| Sesión | JWT bearer de 24 h como hoy, guardado en el navegador (`sessionStorage`); 401 lleva al login con mensaje; sin refresh tokens en v1 | Compatibilidad con el JavaFX durante la convivencia; el acceso está detrás de VPN |
| Infra | La web vive en la **VM de producción** (IP en Apuntes), hoy sin uso. Se monta ahora con una copia de usar y tirar de la BD de preprod. Al corte se limpia y se carga el dump definitivo. Preprod (`api.fonestore.es`) sigue sirviendo al JavaFX hasta el corte y después queda como staging | La VM está lista salvo el clone y el compose; desarrollar ahí evita servidor local |
| Dominio | Subdominio propio del ERP, propuesta `erp.fonestore.es`, DNS en Webempresa, certificado Let's Encrypt por webroot con hook de recarga (mismo patrón que `api.fonestore.es`) | Misma origin web + API, sin CORS |
| Red | **VPN WireGuard sitio a sitio** desde el router de la tienda a la VM: en la tienda nadie configura nada. **WireGuard por dispositivo** para quien trabaje desde fuera y como acceso de emergencia si cae el túnel. **Sin lista blanca por IP** (la tienda tiene IP dinámica). Nginx solo sirve el ERP al tráfico del túnel | Comodidad en tienda, acceso remoto controlado, sin dependencia de IP fija |
| Hardening | Backend sin puerto al host; SSH solo por clave; fail2ban leyendo logs de nginx por bind mount; rate limiting en `/api/auth/login`; cabeceras de seguridad; contraseña SQL y secreto JWT nuevos; usuario MariaDB dedicado; `include-message` de errores desactivado; backup diario | Recomendaciones de la guía de seguridad del proveedor de la VM y lecciones del incidente de fonestockroom, todas pendientes hoy |
| CI/CD | GitHub Actions en el repo web: compila y pasa tests en cada push. Despliegue **manual** en la VM (git pull + rebuild), como el servidor hoy | Patrón vigente en las dos VMs; no hay despliegue automático documentado |

### 2.1 Diferencias inevitables respecto al JavaFX (pactadas)

Se aceptan de antemano para que no aparezcan como sorpresas en el smoke:

- Guardar CSV pasa a **descarga del navegador** (sin diálogo de carpeta).
- Las ventanas secundarias (Stage) pasan a **modales o pestañas del navegador**.
- La recarga al recuperar el foco de ventana pasa a **recarga al volver a la pestaña** (`visibilitychange`).
- Los atajos de teclado del escritorio se revisan uno a uno en la ficha de paridad de cada vista.
- El lector de códigos de barras (IMEI) funciona igual: es un teclado.
- No hay "instalación": la versión se ve en el pie o en el menú de usuario, y el despliegue actualiza a todos a la vez.

## 3. Arquitectura de la web

```
Navegador ──HTTPS (túnel VPN)──► nginx (VM producción)
                                  ├── /            → ficheros estáticos de la SPA (dist/)
                                  └── /api/        → backend Spring Boot :8080 (red interna Docker)
                                                        └── MariaDB :3306 (red interna Docker)
```

**Repo `gestion-reparaciones-web`**

```
src/
  app/            shell (barra superior, contenedor), rutas, sesión, guards por rol, banner de conexión
  modules/
    taller/       pendientes técnico, historial, IMEIs, formulario, asignaciones, pulido, glass, revisión
    almacen/      stock, pedidos (componentes y otros), proveedores, lotes e importador
    gestion/      clientes, estadísticas, usuarios y técnicos, logs, cuenta
  shared/
    api/          cliente HTTP, tipos generados de OpenAPI, mapeo de errores, hooks de TanStack Query
    ui/           componentes shadcn adaptados + componentes propios (DataTable, Badge, TogglePill, MultiSelect, ConfirmDialog, banner)
    lib/          fechas Madrid, formato de números con coma, IMEI (troceado, validación), CSV
    styles/       tokens CSS (de app.css y Colores.java), tailwind.config
docs/
  paridad/        una ficha por vista (las capturas del JavaFX, con datos reales, van en Apuntes/paridad-capturas/)
```

Reglas:

- Cada módulo expone sus rutas y sus pantallas; nunca importa de otro módulo salvo a través de `shared`.
- La lógica que se queda en cliente (troceado de IMEIs, agrupación por IMEI, filtro IMEI, estado del modal de asignación) vive en funciones puras de `shared/lib` o del módulo, con tests Vitest portados de los JUnit actuales.
- Los tokens de color salen de `Colores.java` (azul noche `#001232`, azul medio `#2C3B54`, crema `#F6F6F6`, amarillo `#F1E356`, fondos y bordes de fila por estado, etc.) y de `app.css`. Ningún color se escribe a mano en un componente.
- Estado de servidor con TanStack Query: refetch cada 60 s en las vistas que hoy tienen poller (técnico, supertécnico, stock, notificaciones), 5 s cuando el banner de sin conexión está activo, y al volver a la pestaña. Admin, estadísticas y clientes sin poller, como hoy.
- Mapeo de errores único (equivalente a `ApiClient.clasificar`): 401 cierra sesión y lleva al login con "Tu sesión ha expirado. Inicia sesión de nuevo."; 403 "No tienes permisos para realizar esta acción."; 404 "Recurso no encontrado."; 409 conflicto de edición con el mensaje del servidor y recarga; 422 el mensaje de negocio del servidor; 5xx o fallo de red activan el banner "Sin conexión con el servidor. Reintentando…" que se autocura.

## 4. Servidor: cambios que acompañan a la migración

Todos son aditivos o endurecen permisos; ninguno rompe al JavaFX en producción.

**4.1 Contrato (sub-proyecto 0).** springdoc-openapi (`/v3/api-docs`) y anotaciones mínimas donde el tipo de respuesta no sea inferible (`Map<String,Object>` de login y respuestas `{value}`). Se regenera `schema.md` desde `crear_bd.sql`. Los `api_contract.md` se sustituyen por un enlace al OpenAPI.

**4.2 Autorización en servidor (bloque de seguridad de la Fase 3), obligatorio antes del piloto.** Detalle con fichero y línea en el inventario del cliente, sección 3, y en el informe de dominio, sección 3:

- Todo `?tecnico=` en `/reparaciones/asignaciones`, `/historial`, `/glass/*`, `/pulidos/*` se verifica contra el principal: un no-admin solo recibe lo suyo, y `ReparacionControllerTecnico` deja de descargar el historial completo para filtrarlo en cliente.
- Estadísticas (`/estadisticas`, `/estadisticas/puntos`): un no-admin recibe solo sus filas más los agregados de equipo por periodo calculados en servidor (compromiso de la spec de estadísticas solo-propias de 2026-09-07). La exclusión `ES_ESTADISTICA` se aplica en servidor.
- Huecos concretos: `PATCH /api/componentes/{id}/stock` y `/api/solicitudes-stock` sin rol; `POST /api/reparaciones` sin `@PreAuthorize`; `DELETE /api/telefonos/{imei}` y `PATCH observacion` sin rol; `idTec` del autor deja de viajar en el body de `/reparaciones/completa` y se toma del token; propiedad de la reparación al editar y completar; modo solo-lectura del admin aplicado en servidor (hoy ADMIN recibe 403 en mutaciones pero tampoco puede leer proveedores, compras ni solicitudes: se le da lectura).

**4.3 Cálculos que suben al servidor**, cada uno en el sub-proyecto que lo necesita, con test JUnit y portando las suites del cliente (`CargaTecnicosTest`, `PrediccionGlassTest`, `PuntosEstadisticaTest`, `EntregaGlassTest`):

| Cálculo | Hoy en | Sub-proyecto |
|---|---|---|
| Carga de técnicos: topes 8/17/25, pesos, jornadas 9/9/8/8/6, colores 70/90, alcances Pedidos/Total | `utils/CargaTecnicos` | 3 |
| Predicción y balanceo de glass automática | `utils/PrediccionGlass` | 3 |
| Contadores de pendientes (3 categorías, cap 99+), "N asignados", dedup de asignaciones | controllers | 1 y 3 |
| Textos y visibilidad de entrega glass | `utils/EntregaGlass` | 3 |
| Transiciones de estado de pedidos, rangos de recepción parcial y resto, `precioEur = precio × tasa` | `StockController` | 4 |
| Importador xlsx de lotes (POI), clasificador, mapeos de modelo y color, lookup de modelos con pacing | `utils/*` del importador | 4 |
| Puntos por día, promedio por periodo trabajado, días laborables, tarjetas, finde y fuera de horario que suman sin promediar | `utils/PuntosEstadistica` | 5 |
| Umbral de batería 85 para revisión obligatoria, catálogo `MODELOS_ORDENADOS`, tipo por prefijo, rango 0–99,99 de dificultad, contraseña ≥ 6 en registro | duplicados o solo cliente | 3, 4, 6 |

**4.4 Lo que no cambia:** JWT bearer de 24 h, bloqueo optimista con `updatedAt` y 409 donde ya existe (y toggles sin lock donde no lo hay, como urgente, chasis, por cerrar, entrega y revisión), JdbcTemplate, estructura de controllers, generación de `ID_REP` en servidor. Sin CORS. Rate limiting en nginx, no en Spring.

## 5. Infraestructura

**5.1 VM de producción.** Tres contenedores como en preprod (MariaDB 11, backend Temurin 17 en dos fases, nginx alpine) más la web como ficheros estáticos dentro de la imagen de nginx, construida en dos fases (Node compila `dist/`, nginx lo sirve con `try_files $uri /index.html`). Backend y MariaDB sin `ports` al host. Estructura `/opt/reparaciones/{sql,nginx,certbot,logs-nginx}`. Compose con contraseña SQL y `JWT_SECRET` nuevos, `SERVER_ERROR_INCLUDE_MESSAGE` desactivado, usuario MariaDB dedicado con permisos solo sobre `gestion_reparaciones`.

**5.2 Dominio y certificado.** `erp.fonestore.es` en Webempresa apuntando a la IP pública de la VM. Certificado por webroot; el puerto 80 queda abierto al exterior solo para el challenge ACME y la redirección 301. El bloque 443 de nginx solo acepta orígenes del túnel (`allow` de la red VPN y de la red local de la tienda, `deny all`). `api.fonestore.es` se queda en preprod hasta el corte; después apunta a producción o se retira, a decidir en el sub-proyecto 8.

**5.3 Red (sub-proyecto 7).** WireGuard con la VM como hub. Peer 1: el router de la tienda (túnel sitio a sitio; keepalive porque la IP de la tienda es dinámica). Peers N: dispositivos remotos con su clave. Prerrequisito a comprobar: si el router de la tienda soporta WireGuard o hace falta una pasarela (mini PC o Raspberry con ruta estática). Los dos ISP y los routers en bridge del documento de infraestructura son para rendimiento y redundancia, no para la primera versión. Firewall del proveedor de la VM: 22, 80 y el UDP de WireGuard; el 443 puede quedar abierto porque nginx filtra por origen, o cerrado si se decide que todo entre por el túnel (se decide en el sub-proyecto 7 tras probar el enrutado). Corrección al documento de infraestructura: el túnel termina en la VM, así que el firewall del proveedor de la VM ve la IP pública del cliente; la restricción por VPN se hace en la VM, no en el panel.

**5.4 Hardening (sub-proyecto 7, con lo mínimo en el 0).** Tabla de estado en el informe de infraestructura (Apuntes), sección 6. Bloqueante para el piloto: backend sin puerto, SSH solo clave, secretos nuevos, rate limiting del login, fail2ban sobre nginx, backup diario. Deseable: cabeceras de seguridad, geo-bloqueo del login, 2FA para admin (post-corte).

**5.5 Piloto y corte (sub-proyecto 8).** Para el piloto, el mismo bundle se despliega también en el nginx de preprod, con lo que uno o dos usuarios trabajan con datos reales mientras el resto sigue en JavaFX. El corte: fin de jornada, dump completo de preprod (`mariadb-dump --single-transaction --routines --triggers`), carga en producción con volumen limpio, router de la tienda al túnel, tag del servidor y de la web, retirada del JavaFX de los PCs. Preprod queda como staging y recibe los mismos despliegues antes que producción.

## 6. Paridad: cómo se verifica

"Calcar" se convierte en algo comprobable con cinco mecanismos:

1. **Ficha de paridad por vista** (`docs/paridad/<vista>.md` en el repo web), escrita **antes** de construir la vista a partir del FXML, del controller, del informe de specs por módulo y del inventario. Es el criterio de aceptación de la tarea. Plantilla:
   - Roles que la ven y diferencias por rol.
   - Sub-vistas o pestañas y su orden.
   - Columnas de cada tabla con su orden, formato y ordenación por defecto.
   - Filtros (tipo, valores, comportamiento del "Limpiar").
   - Acciones: botones, menús contextuales, dobles clics, atajos; qué confirma con `ConfirmDialog` y con qué texto, qué pide motivo.
   - Badges, píldoras y colores de fila con su token.
   - Textos de aviso, error y vacío.
   - Qué exporta el CSV y con qué cabeceras.
   - Qué se refresca y cuándo (poller, al volver, tras guardar).
   - Reglas de negocio que aplica (con la spec de origen) y dónde viven ahora (servidor o cliente).
   - Diferencias inevitables aceptadas para esa vista.
2. **Capturas del JavaFX** de cada vista y estado importante, tomadas por el usuario de la app real. Como el repo es público, se guardan fuera de git, en `Apuntes/paridad-capturas/<vista>/`, y la ficha las referencia por nombre. La revisión se hace lado a lado.
3. **Tests:** los JUnit de la lógica que se queda en cliente se portan a Vitest uno a uno; los comportamientos de la ficha se prueban con Testing Library y MSW.
4. **Smoke por módulo con el usuario**, con la misma copia de datos en JavaFX y web, haciendo el mismo flujo en los dos.
5. **Piloto** con usuarios reales antes del corte.

## 7. Descomposición y orden

Cada sub-proyecto lleva su spec, su plan y sus ramas (`feature/web-<nombre>` en el repo web y en el servidor cuando toque). Los cambios de servidor viajan dentro del sub-proyecto que los necesita. Antes de cada uno, un brainstorming corto: las decisiones grandes ya están aquí.

| # | Sub-proyecto | Web | Servidor | Infra |
|---|---|---|---|---|
| 0 | **Cimientos** ([spec](2026-09-13-web-cimientos-design.md)) | Repo, stack, shell con barra superior y roles, login y sesión, banner de conexión, refresco, mapeo de errores, tokens CSS, DataTable con filtros, CI. Primer módulo real: **Clientes** (CRUD con bloqueo optimista) de punta a punta | springdoc y OpenAPI; tipos generados; `schema.md` regenerado | Terminar VM de producción, subdominio y certificado, despliegue de la web |
| 1 | **Taller técnico** | Pendientes técnico (reparaciones, glass, pulidos), Historial, IMEIs (vista agrupada) | `?tecnico=` verificado contra el token; filtros por rol; contadores de pendientes | |
| 2 | **Formulario de reparación** | Formulario de completar con piezas, reutilizado, acciones "otro", solicitud de pieza, agotar componente, borrador persistente; campana y notificaciones | `idTec` del token; `/solicitudes-stock` y `PATCH stock` con rol; autodetección de chasis por SKU | |
| 3 | **Asignaciones supertécnico** | Vista de asignaciones con el modal de colas (pegado de IMEIs, cliente por IMEI, urgente, chasis, "Lleva glass"), carga de técnicos, predicción y entrega de glass, revisión y ficha de veredicto; admin en solo lectura | Carga, predicción, badges, dedup, entrega glass, solo-lectura admin, umbral 85 | |
| 4 | **Almacén** | Stock (agrupado por SKU, compartidos, mínimo, activo), pedidos de componentes y otros, proveedores, tipos de cambio, inventario de teléfonos, lotes e importador xlsx, envíos y devoluciones | Transiciones de pedidos, recepción parcial, precio por tasa, importador en servidor, `DELETE telefonos` con rol | |
| 5 | **Estadísticas** | Técnicos (puntos, tarjetas, promedio, IMEIs típicos, ⚙ Valores, 👥 Técnicos) y Stock (evolución) | Puntos por día, promedios, días laborables, filtrado por rol y agregados de equipo | |
| 6 | **Gestión** | Usuarios y técnicos, logs, registro, cambiar contraseña, historial de pulido, menú de usuario | Validaciones que hoy están en cliente | |
| 7 | **Red y hardening** | | | VPN sitio a sitio, WireGuard por dispositivo, fail2ban, rate limiting, cabeceras, backup. En paralelo; listo antes del piloto |
| 8 | **Piloto y corte** | Despliegue en preprod para uno o dos usuarios, checklist de paridad completa, correcciones, versión 1.0 | Tag del servidor | Dump, carga en producción, router al túnel, retirada del JavaFX, preprod como staging |

Orden: 0 → 1 → 2 → 3 → 4 → 5 → 6 → 8, con 7 en paralelo. Se empieza por el técnico porque sus vistas son las más simples y más usadas, y así los patrones (tabla, filtros, badges, refresco) se asientan antes del modal de asignaciones, que es lo más complejo del cliente.

## 8. Fuera de alcance

- Rediseño visual ERP (sidebar con módulos, topbar, dashboard, modo oscuro): proyecto posterior al corte.
- Vistas móviles diseñadas: solo las bases responsive.
- Rol LOGISTICA y el resto de la Fase 3 funcional: siguen en el plan-futuro, no entran aquí salvo el bloque de seguridad.
- Refresh tokens, 2FA, SSE o WebSockets: post-corte.
- Despliegue automático desde GitHub Actions a la VM.
- Integraciones (NSYS, web pública): fuera.

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| El modal de asignaciones (2.900 líneas de controller) es más complejo de lo que parece | Es el sub-proyecto 3, con los patrones ya asentados; se sube al servidor todo lo que sea cálculo y se deja en cliente solo el estado de colas, con tests |
| Documentación de contrato y esquema desactualizada | Sub-proyecto 0 la regenera desde el código; a partir de ahí el contrato se genera, no se escribe |
| El router de la tienda no soporta WireGuard | Pasarela con mini PC o Raspberry; se comprueba al inicio del sub-proyecto 7, no bloquea el desarrollo |
| Deriva entre JavaFX y web durante meses (hotfixes al JavaFX) | Cada hotfix funcional del JavaFX añade una línea a la ficha de paridad de la vista afectada |
| Un solo desarrollador | Cada sub-proyecto es un bloque cerrado con smoke y merge; el programa puede pausarse entre bloques sin dejar nada a medias |

## 10. Documentación y memoria

- Esta spec y tres de los informes viven en `docs/superpowers/` del repo raíz; el de infraestructura, en `Apuntes/` (privado).
- `Apuntes/plan-futuro.md` recibe una sección "Migración web" con los 9 sub-proyectos como checkboxes.
- La memoria de sesión apunta a esta spec y al sub-proyecto en curso; se actualiza al cerrar cada bloque.
- Las sesiones de infraestructura sobre la VM de producción y la red se documentan por sesión en `Apuntes/despliegue_vdc.md` (sección nueva "Producción y web"), siguiendo la regla de documentación externa acordada para las VDC.
