# 0.9.10 — Columna lateral plegable con iconos

Fecha: 2026-10-13. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va en la **versión siguiente de la web tras la 0.9.9**. **Solo web**: sin cambios en el servidor ni en la base.
Prototipo aprobado: `assets/2026-10-13-v0910-prototipo-columna-lateral.html`, variante «A + E · Iconos con el botón del
borde». Estimación: unas 5–7 h con tests.

## 1. Qué cambia

**Hoy** la columna lateral (`app/shell/SubNav.tsx`) mide siempre 200 px y solo lleva texto. En Estadísticas, en
Gestionar técnicos y en Ver logs no tiene enlaces, pero ocupa igual sus 200 px, en blanco.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| Iconos | Cada enlace lleva un icono delante del nombre, con la columna abierta y con la columna plegada. |
| Plegar | Un botón redondo sobre el borde derecho de la columna, arriba: «‹» con la columna abierta y «›» con la columna plegada. Está en el mismo sitio en los dos estados. |
| Plegada | Queda una franja de 56 px con solo los iconos. El nombre sale al pasar el ratón por un icono. El contenido gana 144 px. |
| Números | Los de Pendientes y Asignaciones siguen cada uno en su icono, con el tope 99+ y los colores de hoy. |
| Sin enlaces | En las secciones sin enlaces no se pinta ni la columna ni el botón: el contenido usa todo el ancho. |
| Recordar | Plegada o abierta es una preferencia del navegador, como «Agrupar por bloque» en Pedidos. |

**Iconos** (lucide-react, que la web ya usa):

| Enlace | Icono |
|---|---|
| Asignaciones | `ClipboardList` |
| Pendientes | `Wrench` |
| Historial | `History` |
| IMEIs | `Smartphone` |
| Stock actual | `Package` |
| Pedidos | `ShoppingCart` |
| Proveedores | `Building2` |
| Clientes | `Users` |

## 2. Comportamiento

**Abierta:**
- Mide 200 px, como hoy.
- Cada enlace lleva el icono a 18 px, 10 px de separación, el nombre y, si lo tiene, el número, igual que ahora.
- El botón mide 24 px. Es blanco, con borde `pill-borde` y una sombra suave. Va centrado sobre el borde derecho, con la mitad fuera de la columna, y a 14 px de arriba.
- Para que el botón no pise las píldoras, la columna deja 18 px de relleno a la derecha. Cada píldora mide 174 px en vez de 184.

**Plegada:**
- Mide 56 px. Cada icono va en su píldora, con 6 px de relleno a la izquierda y 14 px a la derecha, para dejar sitio al botón.
- El enlace activo es la píldora navy con el icono en blanco, como el activo de hoy.
- El número va en la esquina superior derecha de la píldora. Es el mismo `BadgeLateral`, en pequeño, con los mismos colores (blanco sobre el enlace activo, navy en los demás).
- Al pasar el ratón por un icono, o al llegar a él con el teclado, sale su nombre a la derecha, con el `Tooltip` de `shared/ui/tooltip.tsx`.
- Cada enlace lleva `aria-label` con su nombre, porque el texto ya no se ve.

**El botón:**
- `aria-label` y `title` dicen «Ocultar menú lateral» o «Mostrar menú lateral».
- Lleva `aria-expanded` y `aria-controls` apuntando a la columna.
- El ancho cambia con una transición de 180 ms. Con «reducir movimiento» del sistema no hay animación (`motion-reduce:transition-none`).

**Preferencia:**
- Se guarda en `localStorage` con la clave `columnaLateral.plegada` (`si` / `no`).
- La lectura y la escritura van con `try/catch`, como en `almacen/pedidos/preferencias.ts`. Si no se puede leer (modo privado, almacenamiento bloqueado), la columna sale abierta. Si no se puede guardar, la elección dura lo que la página.
- Es **del navegador, no del usuario**. En el taller se comparten PCs, así que quien entre la verá como la dejó el anterior.
- No se borra al cerrar sesión, porque no es un filtro: `reiniciarStores` no la toca.
- Las otras pestañas abiertas la toman al recargar.

**Sin enlaces:** `SubNav` no pinta nada cuando la sección no tiene enlaces: Estadísticas, `/gestion/tecnicos` y `/gestion/logs`.

## 3. Cómo afecta al resto de la web

No hay que tocar ninguna vista. Todas las tablas usan `DataTable`, que mide su hueco con un `ResizeObserver` y recalcula los anchos solo, también durante la animación. Lo que gana cada tabla depende de cómo reparte el ancho:

| Tablas | Al plegar |
|---|---|
| Asignaciones, Historial, Detalle de IMEI y Logs (ajuste `estirar`) | Crecen todas las columnas en proporción. |
| Pendientes, Pulidos, IMEIs, Stock, Pedidos, Proveedores y Clientes (`fluido`) | Crecen solo las columnas sin tope: componente, cliente, comentario… |
| Técnicos (`ultima`) | Crece solo la última columna. |

- Las barras de filtros bajan a una segunda línea más tarde.
- A 1366 px el Historial ya tiene scroll horizontal. Al plegar, lo que falta para que quepa baja de 502 a 358 px. En Asignaciones, de 227 a 83 px. A 1920 px las dos caben con la columna abierta.
- **No cambian:** la barra navy, la campana, el menú de usuario, el formulario de reparación (ya ocupa toda la pantalla), los diálogos, la capa de carga, el aviso de versión nueva y el letrero de entorno.

## 4. Código

- **`shared/lib/enlaces.ts`:** el tipo `Enlace` gana `icono: LucideIcon`, obligatorio.
- **`modules/taller/rutas.tsx`, `modules/almacen/rutas.ts` y el enlace de Clientes en `SubNav.tsx`:** cada enlace lleva su icono.
- **`app/shell/preferencias.ts` (nuevo):** `leerPlegada()` y `guardarPlegada(plegada)`.
- **`app/shell/SubNav.tsx`:**
  - el estado `plegada`, que arranca de `leerPlegada()` y se guarda al cambiar;
  - el botón;
  - los dos modos de pintar los enlaces y el tooltip en el modo plegado;
  - no pintar nada si no hay enlaces.
  - Vive en `AppLayout`, así que el estado se mantiene al cambiar de ruta.
- **`modules/taller/componentes/BadgeLateral.tsx`:** nueva prop `compacto`, para el número en la esquina del icono. `BadgePendientes` y `BadgeAsignaciones` la reciben y la pasan.
- `AppLayout.tsx` y las vistas no cambian.

Es una adaptación a web, sin calco en el JavaFX.

## 5. Pruebas

**Vitest y Testing Library:**
- En `SubNav.test.tsx`:
  - abierta, cada enlace tiene su icono y su nombre;
  - el botón pliega: no se ven los nombres, los enlaces tienen `aria-label`, `aria-expanded` pasa a `false` y la preferencia queda guardada; al volver a pulsarlo, se despliega;
  - arranca plegada si la preferencia lo dice, y abierta si el almacenamiento falla;
  - plegada, el enlace activo sigue marcado y los números de Pendientes y Asignaciones siguen saliendo, compactos, con el tope 99+;
  - al pasar el ratón por un icono, o enfocarlo con el teclado, sale su nombre;
  - sin enlaces no hay ni columna ni botón. Sustituye al test actual «en una sección todavía sin enlaces mantiene la columna vacía».
- En `preferencias.test.ts`: leer y guardar, con almacenamiento y sin él.

**E2E en Chromium contra preprod** (`tests/e2e/columna-lateral.spec.ts`, nueva). Solo lee datos, no escribe nada:
- plegar la columna;
- recargar y comprobar que sigue plegada;
- ir a otra pestaña pulsando su icono;
- desplegarla.

Se ejecuta con las demás, de una en una.

**Revisión visual:** capturas en preprod a 1366 y a 1920 px, con los tres roles.

## 6. Despliegue y documentación

- **Versión de la web: 0.9.10.** Se cambia en `package.json`, y se añaden la entrada `[0.9.10]` del CHANGELOG y `docs/novedades/NOVEDADES-v0.9.10.md` en la raíz.
- La ficha `docs/paridad/shell.md` se actualiza: la columna es una adaptación web.
- **Desarrollo en paralelo con la 0.9.9.** Se hace en la rama `feature/columna-lateral-plegable`, en un worktree propio (`wt/web-0910`), para no tocar el de la 0.9.9 (`wt/web-099`). No comparten ficheros de código: la 0.9.9 solo toca `modules/almacen/pedidos/`.
- **Al mergear,** el único choque esperable es la cabecera del CHANGELOG, y se resuelve a mano. Antes de mergear se trae `main` a la rama y se repasan los tests.
- **Orden:** preprod y producción después de la 0.9.9, porque preprod es un único entorno.
- **Preproducción:** con las E2E. **Producción:** en horario, porque el corte es despreciable: solo la web y sin migración.

## 7. Fuera de alcance

- Un atajo de teclado para plegar.
- Iconos en la barra navy o en otras partes de la web.
- Guardar la preferencia por usuario en el servidor, o sincronizarla entre pestañas abiertas.
- Llevar Gestionar técnicos y Ver logs a la columna lateral.
- La web en pantallas táctiles o estrechas: es el trabajo de hacer responsive toda la web.
- El scroll horizontal del Historial a 1366 y 1600 px. Plegar lo reduce pero no lo quita; quitarlo es otro trabajo (revisar los anchos mínimos).
