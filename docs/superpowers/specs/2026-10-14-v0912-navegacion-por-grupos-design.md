# 0.9.12 — Navegación por grupos: todo en la columna lateral

> **Sale como 0.9.14** (2026-10-14): la 0.9.13 (casillas en las acciones de bloque y «Exportar bloque en CSV») entró antes en `main`. Los nombres de fichero conservan `v0912`; en el código, la web y los documentos, la versión es 0.9.14.

Fecha: 2026-10-14. Estado: diseño aprobado en el brainstorming, pendiente del plan.

- **Versión:** la siguiente de la web tras la 0.9.11 («abrir» automático al entregar a Glass). Si la 0.9.11 sale después, se renumera; el contenido no cambia.
- **Alcance:** solo web, sin cambios en el servidor ni en la base. Parte de la 0.9.10 (columna plegable con iconos, en producción desde el 2026-10-14).
- **Prototipo aprobado:** `assets/2026-10-14-v0912-prototipo-navegacion-grupos.html`, modo «Nueva». Estimación: un día de trabajo con tests.

## 1. Qué cambia

**Hoy** la navegación va en dos sitios:
- Las pastillas de la barra navy eligen la sección: Reparaciones, Stock, Estadísticas y Clientes.
- La columna lateral solo enseña las pestañas de esa sección.
- Gestión (Técnicos y Logs) está en el menú de usuario del admin.

Consecuencias:
- Para ir de una pestaña de Stock a una de Reparaciones hacen falta dos clics.
- Los números de Pendientes y Asignaciones solo se ven estando en Reparaciones.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| Dónde se navega | Todo en la columna lateral. La barra navy se queda con el logo, el título, la campana y el usuario. |
| Grupos | Cada sección es un grupo con una cabecera plegable, también las de una sola pestaña, para que todo sea igual. |
| Nombres | La pestaña no repite el nombre del grupo: **Estadísticas › Rendimiento** (lo que medía el JavaFX por técnico) y **Clientes › Listado**. |
| Gestión | Pasa a ser un grupo de la columna, solo para el admin. Sale del menú de usuario. |
| Columna plegada | Se ven todos los iconos, con una línea fina entre grupos. El nombre que sale al pasar el ratón lleva el grupo: «Clientes · Listado», «Stock · Pedidos». |
| Recordar | Los grupos plegados se recuerdan en el navegador, igual que la columna plegada de la 0.9.10. |

## 2. Grupos y pestañas

En este orden. Las pestañas de Reparaciones dependen del rol, como hoy.

| Grupo (cabecera) | Pestaña | Ruta | Icono (lucide-react) | Quién |
|---|---|---|---|---|
| REPARACIONES | Asignaciones (+ número) | `/reparaciones/asignaciones` | `ClipboardList` | supertécnico (con número) y admin (sin número) |
| | Pendientes (+ número) | `/reparaciones/pendientes` | `Wrench` | técnico y supertécnico |
| | Historial | `/reparaciones/historial` | `History` | todos |
| | IMEIs | `/reparaciones/imeis` | `Smartphone` | todos |
| STOCK | Stock actual | `/stock` (`end`) | `Package` | todos |
| | Pedidos | `/stock/pedidos` | `ShoppingCart` | todos |
| | Proveedores | `/stock/proveedores` | `Building2` | todos |
| ESTADÍSTICAS | Rendimiento | `/estadisticas` | `ChartColumn` | todos |
| CLIENTES | Listado | `/clientes` | `Users` | todos |
| GESTIÓN | Técnicos | `/gestion/tecnicos` | `UserCog` | admin |
| | Logs | `/gestion/logs` | `ScrollText` | admin |

- Las pestañas activas se marcan igual que hoy:
  - Historial también en `/reparaciones/historial/glass` y `/pulidos`; IMEIs también en el detalle de un IMEI;
  - Pendientes también en `/glass` y `/pulidos`; Pedidos también en `/stock/pedidos/otros`.
- **Técnicos y Logs pierden el botón «Cerrar»** (decisión del usuario del 2026-10-14, tras la revisión final). «Cerrar» imitaba las ventanas aparte del JavaFX y volvía a la página de origen (`volverA`). Ahora son pestañas como las demás: para ir a otro sitio se pulsa otra pestaña, y para volver, el botón Atrás del navegador. Desaparecen `volverA`, `rutaVolverA` (`modules/gestion/navegacion.ts`) y el `state` de los enlaces.
  - Motivo: con la pestaña siempre a la vista, pulsar la activa dejaba `volverA` apuntando a la propia página, y «Cerrar» no hacía nada.
- La página de Estadísticas sigue siendo «pendiente de migrar». Solo cambia el nombre de su pestaña.

## 3. Comportamiento

**Columna abierta (200 px):**
- Cada grupo tiene una cabecera y, debajo, sus pestañas con icono y nombre, como en la 0.9.10.
- **La cabecera** es un botón:
  - va en mayúsculas pequeñas: 10,5 px, negrita, `tracking-wide`, `text-azul-gris`;
  - a la derecha lleva un `ChevronDown` de 14 px, que gira −90° cuando el grupo está plegado;
  - lleva `aria-expanded` y `aria-controls` apuntando a la lista de su grupo;
  - al pulsarla, el grupo se pliega o se despliega, pero no navega.
- **Con el grupo plegado:**
  - sus pestañas no se pintan;
  - la cabecera enseña la suma de los números de sus pestañas (con el mismo `BadgeLateral`, tope 99+), si es mayor que 0;
  - si la página actual está dentro del grupo, la cabecera va en `text-azul-noche` para que se sepa dónde se está.
- **Al empezar** todos los grupos están desplegados.
- **Si no cabe:** la columna crece con la página y se usa el scroll de la página, como hoy. La columna no tiene scroll propio.
  - El peor caso, el admin a 1366 × 768, ocupa unos 538 px de 593 y cabe.

**Columna plegada (56 px):**
- No hay cabeceras: se ven todos los iconos de todos los grupos, aunque haya grupos plegados.
- Entre un grupo y el siguiente hay una línea de 1 px (`bg-fila-sep`), con 6 px de margen vertical.
- **Al pasar el ratón o al llegar con el teclado** sale el nombre «Grupo · Pestaña», con el mismo tooltip de la 0.9.10 (`disableHoverableContent`).
  - El grupo va escrito tal cual («Reparaciones · Pendientes»), no en mayúsculas.
  - El `aria-label` del enlace es ese mismo texto.
- Los números siguen compactos en la esquina de su icono.

**Preferencias del navegador:**
- `columnaLateral.plegada` sigue como en la 0.9.10.
- Nueva clave `columnaLateral.gruposPlegados`: un JSON con la lista de los grupos plegados (por ejemplo `["stock"]`).
  - Si no se puede leer, o el valor no es una lista, todos salen desplegados. Los ids que no son de un grupo conocido se ignoran.
  - Si no se puede guardar, dura lo que la página. Mismo patrón `try/catch` que `preferencias.ts`.
- Es del navegador, no del usuario, y `reiniciarStores` no la toca.

**La columna siempre está:** todas las rutas tienen su grupo. Desaparece la regla de la 0.9.10 de no pintar la columna en las secciones sin enlaces.

## 4. Barra navy y menú de usuario

- **Barra navy:**
  - Se quitan las pastillas de sección (`NAV` y su `<nav>`). Quedan el logo (enlace a inicio), «FSGR:», el título con la versión, la campana y el usuario.
  - Desaparece la «última pestaña de Stock»: el store `ultimaRutaStock` (`modules/almacen/estado.ts`) y los tres `useEffect` que lo guardan (Stock, Pedidos, Proveedores). Solo lo usaba la pastilla «Stock».
- **Menú de usuario:**
  - El del admin pierde «Gestionar técnicos» y «Ver logs», con su separador.
  - Quedan «Descargar CSV», «Cambiar contraseña» y «Cerrar Sesión».

## 5. Los números se ven siempre

- Hoy `BadgeAsignaciones` y `BadgePendientes` solo están montados cuando se está en Reparaciones. Con los grupos lo están siempre.
- **Pendientes:** pide `/api/reparaciones/pendientes/contadores`, una consulta ligera, cada 60 s.
- **Asignaciones (solo el supertécnico):**
  - calcula el total pidiendo las 3 listas de asignaciones; ahora lo hará cada 60 s desde cualquier pantalla;
  - en Asignaciones sigue sin sondear por su cuenta (`sondea: !activo`), como hoy;
  - va en segundo plano y sin la capa de carga;
  - si algún día pesa, la solución es que el servidor dé solo el recuento, en otra versión.

## 6. Cómo afecta al resto de la web

- **Anchos:** son los mismos de la 0.9.10 (200 y 56 px), así que las tablas no cambian.
- **Altura:** la barra navy no cambia de alto.
- **E2E que cambian:**
  - las que pulsan «Stock» o «Clientes» en la barra navy (3 sitios) pasan a pulsar «Stock actual» o «Listado» en la columna;
  - las que entran en Gestión por el menú de usuario (`gestion.spec.ts` y `cliente-historial.spec.ts`) pasan a pulsar «Técnicos» o «Logs» en la columna;
  - `columna-lateral.spec.ts`: el nombre de Historial plegada pasa a ser «Reparaciones · Historial»;
  - la de «Descargar CSV» de `bloques.spec.ts` no cambia, porque sigue en el menú.

## 7. Código

- **`shared/lib/enlaces.ts`:** nuevos tipos `IdGrupoNav = 'reparaciones' | 'stock' | 'estadisticas' | 'clientes' | 'gestion'` y `GrupoNav = { id: IdGrupoNav; titulo: string; enlaces: Enlace[] }`. (El `state` opcional de `Enlace` para `volverA` se quitó tras la revisión final, con «Cerrar»: ver §2.)
- **`modules/taller/componentes/BadgeSumaLateral.tsx` (nuevo):** la suma de la cabecera de un grupo plegado (Pendientes, Asignaciones o los dos), con los mismos hooks que los badges de cada pestaña.
- **`test/server.ts`:** respuesta por defecto (a cero) de los contadores de Pendientes, porque su badge se monta ahora en todas las vistas.
- **`app/shell/navegacion.ts` (nuevo):** `gruposNav(sesion, pathname)` devuelve los grupos del rol en el orden de §2.
  - Usa `enlacesReparaciones` y `enlacesStock`, que ya existen, y añade Estadísticas, Clientes y Gestión.
  - Es una función pura, con sus tests.
- **`app/shell/preferencias.ts`:** `leerGruposPlegados(): Set<GrupoNav['id']>` y `guardarGruposPlegados(ids)`.
- **`app/shell/SubNav.tsx`:**
  - pinta los grupos: abierta, con cabeceras; plegada, con separadores;
  - recuerda los grupos plegados;
  - pinta el tooltip «Grupo · Pestaña»;
  - deja de mirar la sección de la URL.
  - `EnlaceLateral` mantiene el mismo árbol en los dos estados, como tras el arreglo de la 0.9.10.
- **`app/shell/TopBar.tsx`:** sin las pastillas ni `ultimaRutaStock`.
- **`app/shell/UserMenu.tsx`:** sin las entradas de Gestión.
- **`modules/almacen/estado.ts` y las páginas Stock, Pedidos y Proveedores:** se quita `ultimaRutaStock`.
- **Sin cambios:** las vistas, las rutas del router, `BadgeLateral` y las páginas de Gestión.

Es una adaptación web, sin calco en el JavaFX.

## 8. Pruebas

**Vitest y Testing Library:**
- **`navegacion.test.ts`:**
  - grupos y pestañas por rol (técnico, supertécnico y admin), en el orden de §2;
  - Gestión solo para el admin;
  - Técnicos y Logs sin «Cerrar» (ver §2).
- **`preferencias.test.ts`:** leer y guardar los grupos plegados; con JSON roto, con grupos desconocidos y sin almacenamiento.
- **`SubNav.test.tsx`** (se reescriben los tests que dependían de «una columna por sección»):
  - en cualquier ruta, incluidas `/estadisticas` y `/gestion/logs`, salen todos los grupos del rol;
  - plegar un grupo esconde sus pestañas, pone `aria-expanded="false"`, enseña la suma de números y se recuerda;
  - un grupo plegado con la página actual dentro marca su cabecera;
  - con la columna plegada salen todos los iconos y los separadores, y el tooltip dice «Grupo · Pestaña»;
  - los números de Pendientes y Asignaciones se ven estando en Stock;
  - se mantienen los tests de la 0.9.10 que siguen valiendo: plegar la columna, no remontar al plegar, el tooltip que no tapa el botón y los números compactos.
- **`TopBar.test.tsx`:** sin pastillas de sección; el menú del admin sin Gestión; el resto igual.

**E2E en Chromium contra preprod:** las de §6 adaptadas y una pasada completa. En `columna-lateral.spec.ts` se añade un paso: ir de Pedidos a Historial en un clic.

**Revisión visual:** capturas a 1366 y 1920 con los tres roles, abierta y plegada, con algún grupo plegado.

## 9. Despliegue y documentación

- **Solo web, en horario:** el corte es despreciable, no hay migración y la vuelta atrás es solo de código.
- **Aviso al taller (importante, porque cambia la costumbre):** «Ahora todo se navega desde la columna de la izquierda: cada sección es un grupo que se puede plegar, y la barra de arriba ya no tiene botones de sección. Gestión (admin) también está en la columna.»
- **Documentación:**
  - CHANGELOG `[0.9.12]` y `NOVEDADES-v0.9.12.md` en la raíz;
  - ficha `docs/paridad/shell.md`: la barra superior sin botones y la columna por grupos;
  - fichas de Técnicos y Logs: se entra desde la columna.

## 10. Fuera de alcance

- Un buscador de IMEI en la barra navy, o cualquier uso nuevo del hueco que dejan las pastillas.
- Un recuento de asignaciones en el servidor (§5).
- Más pestañas en Estadísticas o Clientes.
- El orden de los grupos por rol, o grupos favoritos.
- La web en pantallas táctiles o estrechas.
