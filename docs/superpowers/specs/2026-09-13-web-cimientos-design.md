# Sub-proyecto 0 — Cimientos de la app web

**Fecha:** 2026-09-13
**Estado:** Aprobado en brainstorming; pendiente de revisión escrita del usuario
**Programa:** [Migración del cliente JavaFX a app web](2026-09-13-migracion-web-programa-design.md) (spec maestra; las decisiones generales están allí y no se repiten aquí)
**Repos que toca:** `gestion-reparaciones-web` (nuevo), `gestion-reparaciones-servidor`, repo raíz (submódulo y docs), VM de producción

## 1. Objetivo

Dejar montada la base sobre la que se construyen los ocho sub-proyectos
siguientes, y demostrarla con un módulo real de punta a punta. Al cerrar este
sub-proyecto:

- Existe el repo web con el stack, la estructura por módulos, los tokens de estilo, el shell, el login, la sesión, el cliente API tipado y la CI.
- El servidor publica su contrato OpenAPI y la web genera sus tipos de él.
- La VM de producción sirve la web en `https://erp.fonestore.es` junto a la API, con una copia de la BD de preprod.
- La vista **Clientes** funciona en la web con paridad verificada contra su ficha, incluido el bloqueo optimista (409).

Se elige Clientes como primer módulo porque es pequeña (215 líneas de controller, 4 endpoints), la ven los tres roles con permisos distintos (solo SUPERTECNICO edita), tiene CRUD completo, bloqueo optimista, confirmación con `ConfirmDialog` y un filtro multiselección: ejercita todos los patrones transversales sin lógica de negocio compleja.

## 2. Fuera de alcance

- Cualquier otra vista. El botón Reparaciones, Stock y Estadísticas del shell existen pero llevan a una pantalla "Pendiente de migrar" con el nombre de la vista.
- Campana de notificaciones (sub-proyecto 2): el hueco existe en la barra pero no se muestra.
- VPN y hardening completo (sub-proyecto 7). Aquí solo lo mínimo para no exponer nada nuevo: backend sin puerto, SSH por clave, secretos nuevos.
- Autorización en servidor de otros módulos (viaja con cada sub-proyecto).
- Despliegue automático a la VM.

## 3. Repo `gestion-reparaciones-web`

**3.1 Creación.** Repo en GitHub (`totdb3354/gestion-reparaciones-web`), **público** como los otros dos (los secretos viven solo en la VM y en `.env.local`, ignorado; las capturas de paridad con datos reales van fuera del repo), rama `main`, enganchado al repo raíz como gitlink en `gestion-reparaciones-web/` con la misma convención que el servidor (sin `.gitmodules`). **Hecho el 2026-09-14** (commit inicial `7e0ea34`, gitlink en el raíz `8a1f75a`); el cambio a público lo hace el usuario en la web de GitHub. Flujo git del proyecto: ramas por área, merge `--no-ff`, tags semánticos, sin `Co-Authored-By` en los commits (regla del usuario).

**3.2 Stack y versiones (pinneadas en `package.json`).**

| Pieza | Elección |
|---|---|
| Runtime de build | Node 24 LTS en CI y Docker (en local hay Node 26), npm |
| Framework | React 19 + TypeScript 5 (modo `strict`) + Vite 6 |
| Rutas | React Router 7 (modo data router) |
| Estado de servidor | TanStack Query 5 |
| Tablas | TanStack Table 8 |
| Estilos | Tailwind 4 + shadcn/ui (componentes copiados en `src/shared/ui`) |
| Gráficas | Recharts (no se usa aquí; se instala en el sub-proyecto 5) |
| Formularios | react-hook-form + zod |
| Tipos de API | openapi-typescript (genera `src/shared/api/schema.d.ts` desde `/v3/api-docs`) + openapi-fetch |
| Tests | Vitest + @testing-library/react + MSW; Playwright para el smoke |
| Calidad | ESLint (config de Vite + react-hooks) + Prettier; `npm run check` = lint + typecheck + test |

**3.3 Estructura.**

```
gestion-reparaciones-web/
  docs/paridad/                 fichas de paridad y capturas (una carpeta por vista)
  public/                       favicon, logo
  src/
    app/
      main.tsx                  arranque: QueryClient, Router, proveedores
      router.tsx                árbol de rutas con guards
      shell/                    TopBar, UserMenu, ConnectionBanner, AppLayout, PendienteDeMigrar
      session/                  SessionProvider, useSession, guards por rol, storage del token
    modules/
      taller/                   (vacío salvo placeholders de ruta)
      almacen/                  (ídem)
      gestion/
        clientes/               ClientesPage, ClienteDialog, hooks de query, tests
    shared/
      api/                      client.ts (openapi-fetch + interceptores), errors.ts, schema.d.ts (generado)
      ui/                       componentes shadcn + DataTable, MultiSelect, StatusBadge, ConfirmDialog, TogglePill
      lib/                      fechas.ts, numeros.ts, imei.ts, csv.ts
      styles/                   tokens.css, globals.css
  tests/e2e/                    smoke Playwright
  Dockerfile                    build en dos fases (ver §9); la conf de nginx vive en la VM (/opt/reparaciones/nginx)
  .github/workflows/ci.yml
```

Reglas: un módulo no importa de otro módulo; todo lo compartido pasa por `shared`. Los componentes de `shared/ui` no conocen la API. Los hooks de query viven en el módulo que los usa.

**3.4 Tokens de estilo (`shared/styles/tokens.css`).** Variables CSS con los valores de `Colores.java` y `app.css`, consumidas por Tailwind como colores con nombre. Ningún componente escribe un color a mano. Mínimo inicial:

| Token | Valor | Origen |
|---|---|---|
| `--azul-noche` | `#001232` | navbar, títulos |
| `--azul-medio` | `#2C3B54` | botones primarios |
| `--azul-gris` | `#586376` | texto secundario |
| `--crema` | `#F6F6F6` | fondo de vistas |
| `--amarillo` | `#F1E356` | acentos, badge de campana |
| `--fondo-input` | `#F3F3F3` | inputs |
| `--gris-borde` / `--gris-disabled` | `#A9A9A9` / `#E7E7E7` | bordes, deshabilitado |
| `--texto-error` / `--texto-accion` / `--verde-ok` | `#B03040` / `#4A6FA5` / `#4CAF50` | mensajes y acciones |
| `--fila-*-bg` y `--fila-*-brd` | edición, reparado, recibido, parcial, cancelado, incidencia, solicitud, urgente, modificada, seleccionada | bordes izquierdos de 4 px y fondos de fila |

La tipografía, tamaños de la barra (64 px), radios y sombras se miden en la app real al escribir la ficha del shell.

## 4. Shell y navegación

Calco de `MainView.fxml` y `MainController`:

- **Barra superior** navy de 64 px: logo (clic vuelve al panel inicial del rol), título "FSGR: Gestión de Stock y Reparaciones" con la versión (de `package.json`), cuatro botones de navegación **Reparaciones, Stock, Estadísticas, Clientes** visibles para los tres roles, hueco de la campana (solo SUPERTECNICO, oculto en este sub-proyecto), botón de usuario "Hola, <usuario>" con menú.
- **Menú de usuario**: "Descargar CSV" (delegado a la vista activa si es exportable; deshabilitado si no), "Cambiar contraseña" (placeholder hasta el sub-proyecto 6), separador, "Cerrar Sesión". ADMIN ve además, arriba, "Gestionar técnicos" y "Ver logs" (placeholders).
- **Banner de conexión** bajo la barra: "⚠ Sin conexión con el servidor. Reintentando…", aparece cuando una petición falla por red o 5xx y desaparece con la primera que vuelve a funcionar. Mientras está activo, las queries con refetch pasan de 60 s a 5 s.
- **Rutas**: `/login`; `/` redirige al panel inicial del rol; `/reparaciones/*`, `/stock/*`, `/estadisticas/*`, `/clientes`. Las tres primeras muestran `PendienteDeMigrar` con el nombre de la vista JavaFX que sustituirán. Toda ruta salvo `/login` exige sesión; `/clientes` la ven los tres roles.
- **Refresco**: al volver a la pestaña (`visibilitychange`) se invalidan las queries de la vista activa, equivalente a la recarga por foco del JavaFX. Clientes no tiene poller, como hoy.
- **Responsive base**: la barra colapsa los botones en un menú a menos de 900 px; el contenedor usa rejilla fluida; las tablas van dentro de un contenedor con `overflow-x: auto`.

## 5. Login y sesión

- **Pantalla** calcada de `LoginView.fxml`: logo, "FSGR", "Gestión de Stock y Reparaciones", versión, campos Usuario y Contraseña (con ojo para mostrarla), etiqueta de error, botón "Iniciar Sesión". Ambos campos obligatorios; Enter envía.
- **Llamada**: `POST /api/auth/login` con `{usuario, password}`; respuesta `{idUsu, nombreUsuario, rol, idTec, token}`. 401 en login = "Usuario o contraseña incorrectos" (mismo texto que hoy). Fallo de red = un único reintento y después el mensaje de conexión, como el parche de login del JavaFX.
- **Sesión** (`SessionProvider`): guarda `{idUsu, nombreUsuario, rol, idTec, token}` en `sessionStorage` (sobrevive a recargar la página, muere al cerrar la pestaña; no se comparte entre pestañas, igual que el JavaFX no comparte entre ventanas). Helpers `esAdmin`, `esSuperTecnico`, `esAdminOSuperTecnico`, `idTec`, calcados de `Sesion.java`. Rol = string `ADMIN` | `SUPERTECNICO` | otro = TECNICO.
- **Expiración**: cualquier 401 fuera del login cierra la sesión una sola vez (aunque fallen varias peticiones a la vez), navega a `/login` y muestra "Tu sesión ha expirado. Inicia sesión de nuevo." El JWT dura 24 h; no hay refresh.
- **Cerrar sesión**: borra `sessionStorage`, limpia la caché de queries y vuelve a `/login`.

## 6. Cliente API y errores

- `openapi-fetch` con el `schema.d.ts` generado. Base `/api` relativa (misma origin); en desarrollo el proxy de Vite manda `/api` a la VM de producción (`https://erp.fonestore.es`, o la IP mientras no haya DNS), configurable por `.env.local`.
- Interceptor de petición: cabecera `Authorization: Bearer <token>` si hay sesión; `Content-Type: application/json`.
- Tiempo máximo por petición 15 s (`AbortSignal.timeout`), como el JavaFX. Sin reintentos automáticos salvo el del login.
- **Mapeo de errores** (`shared/api/errors.ts`, port de `ApiClient.clasificar`, con los mismos 8 casos de test):

| Estado | Efecto |
|---|---|
| 401 | `SesionExpirada` → cierre de sesión único + mensaje |
| 403 | "No tienes permisos para realizar esta acción." |
| 404 | "Recurso no encontrado." |
| 409 | `StaleData` con el mensaje del servidor; el módulo decide (Clientes: "El cliente fue modificado por otro usuario. Se recargan los datos." y recarga) |
| 422 | Mensaje del servidor tal cual (regla de negocio) |
| 5xx, red, timeout | `Conexion` → banner activo; el mensaje al usuario es genérico |

- Fechas: el servidor manda ISO; `shared/lib/fechas.ts` formatea en Europe/Madrid con `dd/MM HH:mm` (y `dd/MM/yyyy` donde el JavaFX lo haga). Números con coma decimal (`es-ES`).
- Los errores se muestran con un componente `Alerta` equivalente a `Alertas.mostrarError` (diálogo modal con Aceptar), no con toasts, para calcar el comportamiento.

## 7. Módulo Clientes (vertical slice)

Calco de `ClientesView.fxml` + `ClientesController` (spec de origen: `2026-06-23-clientes-design.md`). La ficha de paridad `docs/paridad/clientes.md` se escribe primero y se cierra con el usuario antes de codificar; este es su contenido esperado:

- **Roles**: los tres la ven. `soloLectura = !esSuperTecnico` oculta "Nuevo cliente", las acciones de fila y los menús de edición. El servidor ya protege las escrituras con `hasRole('SUPERTECNICO')`.
- **Cabecera**: título "Clientes", filtro multiselección **por nombre de cliente** (lista solo los activos; etiqueta "Cliente", el nombre si hay uno seleccionado, "N clientes" si varios; sin selección se ven todos, activos e inactivos), botón "Nuevo cliente".
- **Tabla**: columnas Nombre y Estado (badge "Activo" verde sobre `--fila-reparado-bg` / "Inactivo" gris sobre `--fila-cancelado-bg`), en el orden que devuelve el servidor; filas activas con borde izquierdo verde de 8 px, fila seleccionada con fondo azul medio; placeholder "Sin clientes".
- **Acciones** (SUPERTECNICO): menú contextual sobre la fila con "Desactivar" o "Activar" según estado, "Editar" (diálogo "Editar cliente", campo "Nombre:", precargado; sin cambio si el nombre queda igual) y "Borrar", que solo aparece si `GET /clientes/{id}/tiene-telefonos` es falso.
- **Nuevo cliente**: diálogo "Nuevo cliente" con "Nombre del cliente:", nombre obligatorio y recortado de espacios.
- **Borrar**: `ConfirmDialog` "Borrar cliente" / "¿Seguro que quieres borrar el cliente "<nombre>"? Esta acción no se puede deshacer.", botones "Borrar" y "Cancelar". Sin motivo. Si el servidor responde 409 por teléfonos asociados, se muestra su mensaje y se recarga.
- **Concurrencia**: PUT y PATCH envían `updatedAt`; 409 → "El cliente fue modificado por otro usuario. Se recargan los datos." y recarga.
- **Endpoints**: `GET /api/clientes`, `GET /api/clientes/activos`, `GET /api/clientes/{id}/tiene-telefonos`, `POST`, `PUT /{id}`, `PATCH /{id}/activo`, `DELETE /{id}`.
- **CSV**: la vista no es exportable en el JavaFX; "Descargar CSV" del menú queda deshabilitado en ella.
- **Sin poller**; se recarga tras cada escritura y al volver a la pestaña.
- **Tests**: Testing Library + MSW para: lista y filtro, solo-lectura por rol, alta, edición, 409 con recarga, borrado bloqueado por teléfonos, confirmación de borrado. Smoke Playwright: login como SUPERTECNICO, crear, editar, desactivar, borrar.

## 8. Servidor

- **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui` compatible con Spring Boot 3.3): `/v3/api-docs` y `/swagger-ui` solo accesibles con token (o solo en perfil de desarrollo; se decide en el plan). Anotar con `@Schema`/`@ApiResponse` donde el tipo no sea inferible: la respuesta del login (`Map`) y las respuestas `{value}`.
- Un script `npm run api:types` en la web descarga `/v3/api-docs` de la VM (o de un servidor local) y regenera `schema.d.ts`; el fichero generado se commitea para que la CI no dependa de la VM.
- `docs/schema.md` del servidor regenerado desde `sql/crear_bd.sql`; `docs/api_contract.md` de ambos repos sustituidos por una nota que apunta al OpenAPI.
- Ningún cambio funcional ni de autorización en este sub-proyecto (Clientes ya está protegido).
- Validar el arranque del contexto Spring tras añadir springdoc (regla del proyecto: no hay test de contexto).

## 9. VM de producción y despliegue

Sigue la receta de preprod y de la web Fonestore (informe de infraestructura en Apuntes, secciones 1 y 2), con las correcciones ya decididas. Claude prepara los comandos y el usuario los ejecuta; cada sesión se documenta en `Apuntes/despliegue_vdc.md`, sección nueva "Producción y web".

1. Base: `apt upgrade`, reboot, clave SSH pública de este PC en `authorized_keys`, `PasswordAuthentication no`, fail2ban del host activo.
2. `git clone -b main` del servidor en `/opt/reparaciones/servidor` y del repo web en `/opt/reparaciones/web` (ambos públicos: sin token).
3. `init.sql` = dump de preprod de usar y tirar (`mariadb-dump --databases gestion_reparaciones --single-transaction --routines --triggers` desde Git Bash).
4. `docker-compose.yml`: mariadb (`127.0.0.1:3306`, contraseña nueva, usuario dedicado `erp` con permisos sobre la BD), backend (sin `ports`, `JWT_SECRET` nuevo de 64 caracteres, `SERVER_ERROR_INCLUDE_MESSAGE: never`), nginx (80/443, volúmenes `nginx/default.conf`, `certbot`, `/etc/letsencrypt:ro`, `logs-nginx` como bind mount para fail2ban).
5. **Dockerfile de la web** en dos fases: `node:22-alpine` (`npm ci`, `npm run build`) → `nginx:alpine` copiando `dist/` a `/usr/share/nginx/html`. Ese contenedor es el nginx del stack: sirve la SPA con `try_files $uri /index.html` y proxy `/api/` al backend.
6. DNS: `erp.fonestore.es` → IP pública de la VM de producción (en Apuntes) en Webempresa. Certificado: `certbot certonly --webroot` con hook de recarga en `renewal-hooks/deploy` (patrón de la web Fonestore, unificado). 80 solo challenge + 301.
7. Firewall del proveedor de la VM: 22, 80, 443 y aprovisionar. En este sub-proyecto el 443 queda abierto con HTTPS + login; la restricción al túnel llega con el sub-proyecto 7.
8. Prueba: login desde el navegador con un usuario de la copia, alta y borrado de un cliente, 409 provocado desde dos pestañas.

Actualización: `git pull` en los dos repos y `docker compose up -d --build`. La web se reconstruye en la VM; no se sube `dist/` por scp.

## 10. CI

`.github/workflows/ci.yml` en el repo web: en cada push y pull request, Node 22, `npm ci`, `npm run check` (lint, typecheck, Vitest). Playwright no corre en CI en este sub-proyecto (necesita API); se ejecuta en local contra la VM. El workflow de la raíz que construye los instaladores JavaFX no se toca.

## 11. Criterios de cierre

- `npm run check` verde en CI; tests de Clientes y del mapeo de errores pasando.
- Contexto Spring arranca con springdoc; `/v3/api-docs` responde; `schema.d.ts` generado y commiteado.
- `https://erp.fonestore.es` sirve la web con certificado válido; login con los tres roles; Clientes con paridad verificada contra su ficha y capturas, smoke Playwright verde.
- Ficha `docs/paridad/clientes.md` y `docs/paridad/shell.md` cerradas con el usuario.
- Documentación: sección "Producción y web" en `Apuntes/despliegue_vdc.md`, `schema.md` regenerado, `README` del repo web con arranque en local (`npm install`, `.env.local` con la URL de la API, `npm run dev`).
- Merge `--no-ff` a `main` en los repos tocados, gitlinks actualizados en el raíz, tag `v0.1.0` en el repo web.

## 12. Riesgos propios de este sub-proyecto

| Riesgo | Mitigación |
|---|---|
| Tailwind 4 o shadcn cambian de API entre la spec y la ejecución | Pinnear versiones en el plan al día de ejecución; si Tailwind 4 da problemas con shadcn, usar Tailwind 3.4 |
| springdoc no infiere las respuestas `Map<String,Object>` | Anotar a mano las que use Clientes y el login; el resto se anota módulo a módulo |
| El proxy de Vite contra HTTPS con certificado real y la cookie/token | Solo cabecera bearer, sin cookies; el proxy no necesita nada especial |
| El usuario ejecuta los comandos en la VM y algo difiere de la guía | Corregir la guía en el momento (regla acordada para las VDC) |
