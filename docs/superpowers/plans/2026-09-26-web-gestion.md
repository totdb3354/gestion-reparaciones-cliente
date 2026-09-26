# Sub-proyecto 6 — Gestión: usuarios y técnicos, logs, cambiar contraseña y menú de usuario: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir los tres "Pendiente de migrar" del menú de usuario por la página "Gestionar técnicos" (`/gestion/tecnicos`: alta, tabla, activar/desactivar, eliminar), la página "Ver logs" (`/gestion/logs`: filtros, buscador, detalle) y el diálogo "Cambiar contraseña", subiendo al servidor las validaciones del alta y de la contraseña (422 con los textos del cliente), la comprobación de referencias al borrar (409), el 404 de ids inexistentes, el `limite` y el filtro de fechas en hora de Madrid del log y la lista real de acciones; y añadir el CSV de Asignaciones de la línea `hotfix/0.16.3`.

**Architecture:** El servidor gana `ValidacionUsuarios` (patrón de `ValidacionPedidos`) y lecturas nuevas en `UsuarioDAO` (existencia, `idUsu` por técnico, referencias en nueve columnas), una guarda de rol en `TecnicoController`, dos 422 en `AuthController`, `limite`/fechas Madrid/`acciones` en `LogDAO` y `LogController`, y nullables en el contrato. La web gana el módulo `modules/gestion/` (guarda `RequiereAdmin`, consulta compartida de usuarios, subcarpetas `tecnicos/`, `logs/` y `cuenta/` con helpers puros testeados, `api.ts` y una página o diálogo cada una) sobre `DataTable`, `CampoAutocompletar`, `RangoFechas`, `ConfirmDialog`, `AlertaProvider` y `ComboNavy`; `CampoPassword` pasa a `shared/ui` y lo comparten el login y el diálogo; el CSV de Asignaciones se añade en `modules/taller/asignaciones/csv.ts`.

**Tech Stack:** Servidor Spring Boot 3.3 + JdbcTemplate + JUnit 5 + Mockito + MockMvc. Web React 19 + TypeScript + TanStack Query + openapi-fetch + Tailwind + shadcn/ui + Vitest + Testing Library + MSW + Playwright.

**Spec:** [`docs/superpowers/specs/2026-09-26-web-gestion-design.md`](../specs/2026-09-26-web-gestion-design.md) (decisiones G1-G11 vinculantes) y la spec maestra [`2026-09-13-migracion-web-programa-design.md`](../specs/2026-09-13-migracion-web-programa-design.md). **Referencia de detalle:** los inventarios `inventario-tecnicos.md`, `inventario-logs.md` e `inventario-password.md`, guardados fuera del repo (los tiene el controlador de la sesión; si una regla de este plan no cuadra con el JavaFX, manda el JavaFX y se consulta).

## Global Constraints

- **Ramas:** `feature/web-gestion` en `gestion-reparaciones-servidor` (T1) y en `gestion-reparaciones-web` (T6), creadas desde `main`. El repo raíz se queda en `main`.
- **Nunca** `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno. Claude no hace SSH a las VMs: los comandos de la VDC se preparan y los ejecuta el usuario.
- **Commits sin `Co-Authored-By`.** Mensajes en español, en minúscula tras el prefijo. En cada dispatch, antes de dar la tarea por hecha, `git cat-file -p HEAD | tail -1` no puede contener `Co-Authored-By` (lección del 4a).
- **Los tres repos son públicos:** ningún dato real (nombres de personas, usuarios reales, contraseñas, dominios, IPs, ids de la BD de pruebas) en código, tests, comentarios, fixtures ni documentación. Usuarios sintéticos `tecnico-a`, `usuario-a`, `admin-prueba`, `e2e-tecnico-<marca>`; contraseñas `secreta1`, `nueva123`; IMEIs `000000000000000`. Los huecos de seguridad no se describen en `docs/`: van a `Apuntes/plan-futuro.md` (SP7).
- **El cliente JavaFX NO se toca.** Se consulta en solo lectura con `git show hotfix/0.16.3:gestion-reparaciones-cliente/<ruta>` desde el raíz; el disco del cliente está en `main` (otra versión) y no vale como referencia.
- **Todo el trabajo de servidor es aditivo:** ninguna respuesta que el JavaFX consuma cambia de forma; los 422 nuevos coinciden con lo que el cliente bloquea antes de llamar; `limite` es opcional y sin él el log se devuelve entero; `idUsu` del `DELETE` sigue en el contrato.
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`. Comandos por Bash antes que PowerShell. `mvn -q` no imprime el resumen: el recuento se suma de `target/surefire-reports/*.txt`.
- **Antes de cada tarea, buscar en el repo lo que va a crear** (lección del 3a, 3b, 4a y 4b): si ya existe, se reutiliza y se anota la desviación.
- **Cada petición se verifica contra el contrato OpenAPI real** (`src/shared/api/schema.d.ts`, regenerado en T6) antes de escribir el código que la usa. Si un nombre de ruta, parámetro o esquema no coincide con este plan, manda el contrato.
- **Textos visibles exactos** (se copian tal cual de la spec §6): ninguno se reescribe "mejorado". Los que dependen de la JVM del JavaFX (placeholder de tabla vacía, título del aviso de éxito) se fijan con la captura antes de la comparación.
- **Un módulo no importa de otro módulo** (regla de lint del repo): `gestion` no importa de `taller` ni de `almacen` (la clave `['tecnicos']` se invalida por literal); lo compartido va a `src/shared` (`CampoPassword`, `FMT_FECHA_LOG`, tokens).
- **Un fichero que exporta un componente no exporta helpers** (react-refresh): helpers a `.ts` propios (`validacion.ts`, `textos.ts`, `filtros.ts`, `navegacion.ts`, `csv.ts`).
- **Los revisores comprueban `npm run lint`** en cada tarea de web; un warning "preexistente" se demuestra con `git stash`, no se afirma.
- **Testing Library no normaliza el texto buscado:** para textos con saltos de línea o espacios múltiples usar `getByText(texto, { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) })` o buscar por fragmento.
- **Datos de prueba por la API y apuntados para la limpieza:** el smoke y las capturas crean sus usuarios con marca `e2e-` y los borran; lo que quede se anota en `Apuntes/plan-futuro.md` (casilla 6).
- **Smoke con `E2E_BASE_URL=http://localhost:5173` exportado** en la línea de comandos (el valor de `~/.env.e2e` es la web desplegada; lección del 4b) y **el puerto 5173 libre** de dev servers viejos (lección del 4a).
- **Capturas de la web lado a lado con las del JavaFX ANTES del merge** de la rama web (lección del 4a), con `capturas-gestion.mjs` construido sobre `Apuntes/herramientas/paridad-capturas/capturas-pedidos.mjs`.
- **Tests con timeout intermitente (>5 s) cuando hay varios procesos node:** relanzar el fichero aislado antes de diagnosticar.
- **Este plan lo redactaron cuatro subagentes sobre un documento de interfaces común y NO está compilado ni ejecutado:** antes de la Task 1 se revisa aplicando el código en copias (sección "Revisión previa").

---

## Estructura de ficheros

**Servidor** (`gestion-reparaciones-servidor`, rutas bajo `src/main/java/com/reparaciones/servidor/` y `src/test/java/com/reparaciones/servidor/`)

| Fichero | Responsabilidad |
|---|---|
| `controller/ValidacionUsuarios.java` | *Crear.* Textos y reglas de los 422 del alta y del cambio de contraseña; texto del 404 y del 409 (T1, T2, T3) |
| `controller/UsuarioController.java` | *Modificar.* Trim + 422 en el alta, `RegistrarTecnicoRequest` package-private (T1); 404, `tieneReferencias`, `DELETE` con 409 e `idUsu` resuelto (T2) |
| `dao/UsuarioDAO.java` | *Modificar.* `existeTecnico`, `getIdUsuByIdTec`, `tieneReferencias`; `tieneReparaciones` delega (T2) |
| `controller/TecnicoController.java` | *Modificar.* `hasRole('ADMIN')` en `POST` y `DELETE` (T3) |
| `controller/AuthController.java` | *Modificar.* 422 de vacíos y de longitud en `cambiar-password` (T3) |
| `dao/LogDAO.java` | *Modificar.* `limite`, límites de día en Madrid → UTC, desempate por `ID_LOG`, `getAcciones` (T4) |
| `controller/LogController.java` | *Modificar.* Parámetro `limite` con 422 y `GET /api/logs/acciones` (T4) |
| `model/LogActividad.java` | *Modificar.* `@Schema(nullable = true)` en `detalle` y `motivo` (T5) |
| `OpenApiContractTest.java` | *Modificar.* Ruta `/api/logs/acciones`, parámetro `limite`, `idUsu` opcional, nullables (T5) |
| Tests nuevos | `controller/UsuarioControllerTest` (T1, T2); `dao/UsuarioDAOReferenciasTest` (T2); `controller/RolesUsuarioTecnicoTest`, `controller/AuthControllerCambiarPasswordTest` (T3); `dao/LogDAOFiltroTest`, `controller/LogControllerTest` (T4) |

**Web** (`gestion-reparaciones-web`)

| Fichero | Responsabilidad |
|---|---|
| `api/openapi.json`, `src/shared/api/schema.d.ts` | *Regenerar* con el contrato de la rama del servidor (T6) |
| `src/shared/api/client.ts` | *Modificar.* Alias `Usuario`, `LogActividad` (T6) |
| `src/shared/lib/fechas.ts` (+ test) | *Modificar.* Patrón `dd/MM/yyyy HH:mm:ss` y `FMT_FECHA_LOG` (T6) |
| `src/shared/styles/tokens.css` | *Modificar.* `fondo-gestion`, `badge-usuario-*`, `error-password`, `etiqueta-password` (T6) |
| `public/Lock.png`, `public/Unlock.png` | *Crear.* Copiados del cliente (T6) |
| `src/shared/ui/CampoPassword.tsx` (+ test) | *Crear.* Campo de contraseña con ojo, compartido por login y diálogo (T6) |
| `src/app/login/LoginPage.tsx` (+ test) | *Modificar.* Usa `CampoPassword`; "Rellena usuario y contraseña." (T6) |
| `src/modules/gestion/rutas.tsx` (+ test), `navegacion.ts` (+ test) | *Crear.* `RequiereAdmin`; `rutaVolverA` (T7) |
| `src/modules/gestion/api.ts` (+ test) | *Crear.* Claves y `useUsuariosTecnicos` (T7) |
| `src/app/router.tsx` | *Modificar.* Rutas de gestión bajo `RequiereAdmin`; `/cuenta/cambiar-password` eliminada (T7); páginas reales (T9, T11) |
| `src/app/shell/UserMenu.tsx` (+ `TopBar.test.tsx`) | *Modificar.* `volverA` en los ítems de ADMIN (T7); "Cambiar contraseña" abre el diálogo (T12) |
| `src/modules/gestion/tecnicos/validacion.ts`, `textos.ts`, `BadgeEstadoUsuario.tsx`, `columnas.tsx`, `api.ts` (+ tests) | *Crear.* Helpers puros, badge, columnas y mutaciones de técnicos (T8) |
| `src/modules/gestion/tecnicos/TecnicosPage.tsx` (+ test) | *Crear.* La página "Gestión de usuarios" (T9) |
| `.gitignore` | *Modificar.* `logs` → `/logs`, para que Git no ignore `src/modules/gestion/logs/` (T10) |
| `src/modules/gestion/logs/filtros.ts`, `columnas.tsx`, `api.ts` (+ tests) | *Crear.* Filtros, buscador, columnas y consultas del log (T10) |
| `src/modules/gestion/logs/LogsPage.tsx` (+ test) | *Crear.* La página "Log de actividad" (T11) |
| `src/modules/gestion/cuenta/validacion.ts`, `api.ts`, `CambiarPasswordDialog.tsx` (+ tests) | *Crear.* Validación, mutación y diálogo de contraseña (T12) |
| `src/modules/taller/asignaciones/csv.ts` (+ test), `AsignacionesPage.tsx` (+ test), `docs/paridad/asignaciones.md` | *Crear / modificar.* CSV de 14 columnas (T13) |
| `tests/e2e/gestion.spec.ts`, `.env.e2e.example`, `README.md` | *Crear / modificar.* Smoke contra producción (T14) |
| `docs/paridad/tecnicos.md`, `logs.md`, `cuenta.md`, `shell.md`, `CHANGELOG.md`, `package.json`, `package-lock.json` | *Crear / modificar.* Fichas de paridad, 0.8.0 (T15) |

---

## Task 0: Arranque — limpieza pendiente de la BD de pruebas (4a y 4b) por la API

> **NO SE EJECUTA (decisión del usuario, 2026-09-26).** La BD de la VM de producción es hoy una copia de pruebas que se vacía y se recarga con el dump definitivo en el corte (spec maestra §2), así que esta limpieza no aporta nada. La ejecución del plan empieza en la **Task 1**. Se conserva el procedimiento por si se quisiera usar antes del piloto; la lista de datos sigue en `Apuntes/plan-futuro.md`.

Se ejecuta **antes de la Task 1**, contra producción (que hoy es la BD de pruebas, `project_preproduccion`), con el servidor desplegado actual (`main` 3dccc4a): todas las rutas que usa ya existen. **El plan es público: aquí no hay ids, nombres de proveedor ni valores de stock reales.** La lista concreta vive solo en los apuntes privados y el ejecutor la lee de allí. Esta tarea no toca ningún repo ni hace commits.

**Files:**
- Modify (fuera de los repos, privado, sin commit): `C:\Users\dev\Documents\Apuntes\plan-futuro.md` (casillas "Limpieza en la VDC del 4a" y "Limpieza en la VDC del 4b", §9, bloque 4)
- Read (privado): `C:\Users\dev\Documents\Apuntes\paridad-capturas\almacen\CAPTURAS-4b.md` (sección "Datos creados para las capturas (ids)")
- Temporal (fuera de los repos, se borra en el Step 9): `$TMPDIR/sp6-limpieza/` (token de sesión y `api.sh`)

**Interfaces:**
- Consumes (contrato actual de la web, `gestion-reparaciones-web/src/shared/api/schema.d.ts` en `cd6853b`; se cita la línea de la ruta y del cuerpo):
  - `POST /api/auth/login` (`:631`), cuerpo `AuthLoginRequest { usuario, password }` (`:2568-2571`), respuesta `LoginResponse { …, token }` (`:2572-2580`).
  - `GET /api/compras` (`:535`, `getAll_7` → `CompraComponente[]`, `:4451`); `CompraComponente { idCompra, idCom, tipoComponente, idProv, cantidad, cantidadRecibida, estado, updatedAt, … }` (`:3120-3146`).
  - `PATCH /api/compras/{idCompra}/desrecibir` (`:1047`), `…/confirmar-alterado` (`:1111`, es "Cerrar sin resto": `modules/almacen/pedidos/PedidosPage.tsx:48`), `…/cancelar` (`:1127`), los tres con `CompraUpdatedAtRequest { updatedAt }` (`:2705-2708`) y respuesta 200.
  - `DELETE /api/compras/{idCompra}` (`:103`, `borrar_2`, respuesta **200** sin cuerpo; solo pendientes).
  - `GET /api/compras-otros` (`:567`), `CompraOtro { idCompraOtro, concepto, estado, cantidadRecibida, updatedAt, … }` (`:3147-3171`); `PATCH /api/compras-otros/{id}/desrecibir` (`:1159`), `…/confirmar-alterado` (`:1223`), `…/cancelar` (`:1239`) con `CompraOtroUpdatedAtRequest { updatedAt }` (`:2721-2724`); `DELETE /api/compras-otros/{id}` (`:119`, `borrar_3`, 200).
  - `GET /api/proveedores` (`:439`), `Proveedor { idProv, nombre, activo, … }` (`:3086-3094`); `GET /api/proveedores/{idProv}/tiene-pedidos` (`:1847`); `PATCH /api/proveedores/{idProv}/activo` (`:1015`) con `ProveedorActivoRequest { activo }` (`:2696-2698`), 200; `DELETE /api/proveedores/{idProv}` (`:71`, `borrar_1`, **204**, y 409 "tiene pedidos" en `ProveedorController.java:81-86` del servidor).
  - `GET /api/componentes/gestionados` (`:1975`, `Componente { idCom, tipo, stock, … }` `:3172-3191`); `PATCH /api/componentes/{idCom}/stock` (`:1255`) con **`ComponenteDeltaRequest { delta }`** (`:2731-2734`): es un incremento, no el valor final (`ComponenteController.java:107-111`).
- Produces: casillas 4a y 4b de `plan-futuro.md` marcadas con el resultado de cada llamada.

**Supuestos:** `~/.env.e2e` tiene `E2E_BASE_URL` (la web de producción; nginx pasa `/api` al backend), `E2E_USER`/`E2E_PASS` (SUPERTECNICO: todas las escrituras de compras, proveedores y stock exigen ese rol, `CompraController.java:58-163`) y `E2E_SKU_PRUEBA`. `desrecibir` resta del stock la cantidad recibida y responde 409 si el stock no llega (`CompraComponenteDAO.java:202-218`). Un pedido cancelado sigue contando como pedido del proveedor (por eso su `DELETE` da 409 y se desactiva en su lugar).

**Regla de la tarea:** cada llamada de **escritura** (`PATCH`, `DELETE`) se presenta al usuario con el elemento, el estado leído justo antes y el comando exacto, y **solo se ejecuta tras su OK explícito, una a una**. Las lecturas (`GET`) no necesitan OK. Tras cada escritura se relee el elemento y se apunta el resultado. Si una respuesta no es la esperada (409, 404, 422), no se reintenta ni se improvisa otra llamada: se enseña al usuario y se decide con él.

- [ ] **Step 1: Leer la lista privada y preparar el guion**

```bash
grep -n "Limpieza en la VDC del 4a\|Limpieza en la VDC del 4b" /c/Users/dev/Documents/Apuntes/plan-futuro.md
sed -n '/^## Datos creados para las capturas/,/^## Pestaña Pedidos/p' /c/Users/dev/Documents/Apuntes/paridad-capturas/almacen/CAPTURAS-4b.md
```

Expected: las dos casillas `- [ ]` (4a y 4b) y la sección "Datos creados" con los proveedores, los pedidos de componentes y de otros con su estado, y el stock del SKU de prueba antes y después de las capturas. Con eso, el ejecutor escribe **en el chat, no en ningún fichero de los repos**, una tabla con una fila por elemento: `elemento (id privado) · estado esperado · acción (desrecibir / cerrar sin resto + desrecibir / borrar / cancelar / desactivar o borrar proveedor / corregir stock / sin receta) · ruta`. Las filas "sin receta" (lo que no es un pedido, un proveedor ni el stock del SKU de prueba; p. ej. la fila que un smoke guardó en una asignación o los logs) se anotan como tales y se deciden con el usuario al final (Step 8): esta tarea no inventa rutas para ellas.

- [ ] **Step 2: Comprobar en el contrato cada ruta y cuerpo que se va a usar**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
grep -n '^    "/api/auth/login"\|^    "/api/compras": \|^    "/api/compras/{idCompra}"\|^    "/api/compras/{idCompra}/desrecibir"\|^    "/api/compras/{idCompra}/confirmar-alterado"\|^    "/api/compras/{idCompra}/cancelar"\|^    "/api/compras-otros": \|^    "/api/compras-otros/{id}"\|^    "/api/compras-otros/{id}/desrecibir"\|^    "/api/compras-otros/{id}/confirmar-alterado"\|^    "/api/compras-otros/{id}/cancelar"\|^    "/api/proveedores": \|^    "/api/proveedores/{idProv}"\|^    "/api/proveedores/{idProv}/activo"\|^    "/api/proveedores/{idProv}/tiene-pedidos"\|^    "/api/componentes/gestionados"\|^    "/api/componentes/{idCom}/stock"' src/shared/api/schema.d.ts
grep -n '^        CompraUpdatedAtRequest: {\|^        CompraOtroUpdatedAtRequest: {\|^        ProveedorActivoRequest: {\|^        ComponenteDeltaRequest: {\|^        AuthLoginRequest: {' -A3 src/shared/api/schema.d.ts
```

Expected: las 17 rutas en las líneas citadas en "Interfaces" (`:631`, `:535`, `:103`, `:1047`, `:1111`, `:1127`, `:567`, `:119`, `:1159`, `:1223`, `:1239`, `:439`, `:71`, `:1015`, `:1847`, `:1975`, `:1255`) y los cinco cuerpos: `updatedAt: string` (dos veces), `activo: boolean`, `delta: number`, `usuario`/`password`. Si alguna no aparece, se para y se consulta.

- [ ] **Step 3: Token de sesión sin escribir la contraseña en la línea de comandos**

```bash
L="$TMPDIR/sp6-limpieza"; mkdir -p "$L"; chmod 700 "$L"
set -a; . ~/.env.e2e; set +a
node -e 'process.stdout.write(JSON.stringify({usuario:process.env.E2E_USER,password:process.env.E2E_PASS}))' \
  | curl -sS -X POST -H 'Content-Type: application/json' --data-binary @- "$E2E_BASE_URL/api/auth/login" \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{let t;try{t=JSON.parse(s).token}catch{}if(!t){console.error("login sin token");process.exit(1)}process.stdout.write(t)})' > "$L/token"
test -s "$L/token" && echo "token OK"
```

Expected: `token OK` (el login es de SUPERTECNICO; un 401 o el 503 del límite de nginx de 5 inicios por minuto darían "login sin token": esperar un minuto y repetir). El token no se imprime nunca.

- [ ] **Step 4: Ayudantes de lectura y escritura**

`$TMPDIR/sp6-limpieza/api.sh` (se carga al principio de cada comando de los Steps 5-8, porque cada llamada de Bash es una shell nueva):

```bash
cat > "$TMPDIR/sp6-limpieza/api.sh" <<'EOF'
# Ayudantes de la limpieza de la Task 0 del SP6. Se carga con: . "$TMPDIR/sp6-limpieza/api.sh"
L="$TMPDIR/sp6-limpieza"
set -a; . ~/.env.e2e; set +a
TOKEN="$(cat "$L/token")"
api_get() { curl -sS -H "Authorization: Bearer $TOKEN" "$E2E_BASE_URL$1"; }
# api_escribir MÉTODO RUTA [CUERPO_JSON]: imprime el cuerpo de la respuesta y "HTTP <código>".
api_escribir() {
  if [ -n "${3:-}" ]; then
    curl -sS -X "$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' --data "$3" -w '\nHTTP %{http_code}\n' "$E2E_BASE_URL$2"
  else
    curl -sS -X "$1" -H "Authorization: Bearer $TOKEN" -w '\nHTTP %{http_code}\n' "$E2E_BASE_URL$2"
  fi
}
_buscar() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const [campo,valor,...claves]=process.argv.slice(1);const x=JSON.parse(s).find(e=>String(e[campo])===valor);console.log(x?JSON.stringify(Object.fromEntries(claves.map(k=>[k,x[k]]))):"NO EXISTE")})' "$@"; }
compra()    { api_get /api/compras        | _buscar idCompra "$1" idCompra estado cantidad cantidadRecibida tipoComponente idProv updatedAt; }
otro()      { api_get /api/compras-otros  | _buscar idCompraOtro "$1" idCompraOtro estado cantidad cantidadRecibida concepto idProv updatedAt; }
proveedor() { api_get /api/proveedores    | _buscar idProv "$1" idProv nombre activo; }
stock_sku() { api_get /api/componentes/gestionados | _buscar tipo "$E2E_SKU_PRUEBA" idCom tipo stock activo; }
EOF
. "$TMPDIR/sp6-limpieza/api.sh"; stock_sku
```

Expected: una línea JSON `{"idCom":…,"tipo":"<E2E_SKU_PRUEBA>","stock":…,"activo":true}`. Se compara `stock` con el que la lista privada da como "antes de las capturas" y se apunta la diferencia (la esperada es la suma de las recepciones que la lista enumera). Si `idCom` no coincide con el de la lista privada, se para y se consulta.

- [ ] **Step 5: Pedidos de componentes, uno a uno (con OK por cada escritura)**

Para cada pedido de la tabla del Step 1, primero la lectura:

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; compra <id>
```

Expected: `{"idCompra":<id>,"estado":"…","cantidad":…,"cantidadRecibida":…,"tipoComponente":"…","idProv":…,"updatedAt":"…"}`; el estado tiene que ser el de la lista (si no, se enseña y se decide). Según la acción, **se presenta al usuario el comando con el `updatedAt` recién leído y se espera su OK**:

1. **Recibido → desrecibir** (resta del stock su `cantidadRecibida` y lo deja `en_camino`):

   ```bash
   . "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras/<id>/desrecibir '{"updatedAt":"<updatedAt>"}'; compra <id>; stock_sku
   ```

   Expected: `HTTP 200`, el pedido en `"estado":"en_camino"` y el `stock` bajado en su `cantidadRecibida`. 409 = el pedido cambió o el stock no llega: se relee y se consulta. Si la lista dice además "cancelar", queda en `en_camino` y pasa al punto 4 (otra escritura, otro OK).

2. **Parcial → cerrar sin resto y desrecibir** (dos escrituras, dos OK): primero `confirmar-alterado` (parcial → recibido con la recibida, **sin tocar stock**), después se relee el `updatedAt` nuevo y se desrecibe:

   ```bash
   . "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras/<id>/confirmar-alterado '{"updatedAt":"<updatedAt>"}'; compra <id>; stock_sku
   ```

   Expected: `HTTP 200`, `"estado":"recibido"`, `stock` sin cambios. Después, con el `updatedAt` que acaba de imprimir `compra <id>`:

   ```bash
   . "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras/<id>/desrecibir '{"updatedAt":"<updatedAt nuevo>"}'; compra <id>; stock_sku
   ```

   Expected: `HTTP 200`, `en_camino` y el `stock` bajado en la recibida del parcial.

3. **Pendiente → borrar**:

   ```bash
   . "$TMPDIR/sp6-limpieza/api.sh"; api_escribir DELETE /api/compras/<id>; compra <id>
   ```

   Expected: `HTTP 200` y después `NO EXISTE`. 409 = ya no está pendiente: se consulta.

4. **En camino → cancelar**:

   ```bash
   . "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras/<id>/cancelar '{"updatedAt":"<updatedAt>"}'; compra <id>
   ```

   Expected: `HTTP 200` y `"estado":"cancelado"`.

Los que la lista deja "como están" no se tocan. Cada resultado (código y estado final) se apunta en el chat en la tabla del Step 1.

- [ ] **Step 6: Otros pedidos, uno a uno (con OK por cada escritura)**

Igual que el Step 5 con `otro <id>` y las rutas de `/api/compras-otros/<id>/…` (no tocan stock):

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; otro <id>
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras-otros/<id>/cancelar '{"updatedAt":"<updatedAt>"}'; otro <id>
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras-otros/<id>/desrecibir '{"updatedAt":"<updatedAt>"}'; otro <id>
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/compras-otros/<id>/confirmar-alterado '{"updatedAt":"<updatedAt>"}'; otro <id>
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir DELETE /api/compras-otros/<id>; otro <id>
```

Expected: la lectura con el estado de la lista; cada escritura `HTTP 200` y el estado siguiente (`cancelado`, `en_camino`, `recibido`) o `NO EXISTE` tras el `DELETE` (solo pendientes). Solo se ejecutan las que la lista pide, una a una con OK.

- [ ] **Step 7: Proveedores de prueba (con OK por cada escritura)**

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; proveedor <idProv>; api_get /api/proveedores/<idProv>/tiene-pedidos; echo
```

Expected: `{"idProv":<idProv>,"nombre":"…","activo":…}` con el nombre de prueba de la lista (si el nombre no es el de la lista, se para) y `{"value":true|false}`. Con `false` (sin pedidos, ni siquiera cancelados), borrar:

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir DELETE /api/proveedores/<idProv>; proveedor <idProv>
```

Expected: `HTTP 204` y `NO EXISTE`. Con `true` (el `DELETE` daría 409 "tiene pedidos"), desactivar si aún está activo:

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/proveedores/<idProv>/activo '{"activo":false}'; proveedor <idProv>
```

Expected: `HTTP 200` y `"activo":false`.

- [ ] **Step 8: Stock del SKU de prueba y elementos sin receta**

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; stock_sku
```

Expected: `stock` igual al "antes de las capturas" de la lista privada. Si no cuadra (p. ej. un parcial que no se pudo cerrar), se presenta al usuario la corrección con `delta = esperado − actual` (negativo para restar) y, con su OK:

```bash
. "$TMPDIR/sp6-limpieza/api.sh"; api_escribir PATCH /api/componentes/<idCom>/stock '{"delta":<esperado − actual>}'; stock_sku
```

Expected: `HTTP 200` y el `stock` esperado.

Elementos sin receta (Step 1): se enseñan al usuario con lo que dice la lista y se anotan tal cual en la casilla ("pendiente, sin endpoint en esta tarea" o lo que él decida). **Los logs del smoke y de las capturas no se borran**: no hay endpoint de borrado de `Log_Actividad`; se anota.

- [ ] **Step 9: Cerrar la sesión de limpieza y apuntar el resultado**

```bash
rm -rf "$TMPDIR/sp6-limpieza"; ls "$TMPDIR/sp6-limpieza" 2>&1 | head -1
```

Expected: `ls: cannot access …: No such file or directory` (token borrado).

En `C:\Users\dev\Documents\Apuntes\plan-futuro.md` (privado, sin commit): marcar `[x]` las casillas "Limpieza en la VDC del 4a" y "del 4b" **solo** si todos sus elementos quedaron resueltos, añadiendo al final de cada una `— HECHA 2026-09-XX en la Task 0 del SP6 por la API: <resumen por elemento con su código HTTP y estado final; stock del SKU de prueba final>; logs no borrables (sin endpoint)`. Si queda algo (sin receta o un 409 que el usuario decide dejar), la casilla sigue `[ ]` con lo hecho y lo que falta. El texto de esa casilla es privado: puede llevar ids; el plan y los commits no.

Recuento: **+0 tests** (tarea operativa, sin código).

---

---

## Task 1: Servidor — 422 del alta de usuarios con los textos del cliente y nombres guardados recortados

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/UsuarioController.java:35-59` (`registrarTecnico`) y `:122` (`RegistrarTecnicoRequest` pasa a package-private)
- Test: `src/test/java/com/reparaciones/servidor/controller/UsuarioControllerTest.java` (nuevo)

**Interfaces:**
- Consumes: `UsuarioDAO.existeNombreTecnico(String)` (`UsuarioDAO.java:123-128`), `UsuarioDAO.existeNombreUsuario(String)` (`:130-135`), `UsuarioDAO.registrarTecnico(String, String, String, String)` (`:51-68`, misma firma y cuerpo), `LogDAO.insertar(int, String, String)` (`LogDAO.java:30-32`), `UsuarioPrincipal(int idUsu, String nombreUsuario, String password, String rol, Integer idTec)`.
- Produces:
  - `controller.ValidacionUsuarios` (`final class`, package-private): `ROLES = Set.of("TECNICO", "SUPERTECNICO")`, `MSG_CAMPOS`, `MSG_PASSWORD_CORTA`, `MSG_USUARIO_LARGO`, `MSG_TECNICO_LARGO`, `MSG_ROL`, `static void validarAlta(String nombreTecnico, String nombreUsuario, String password, String rol)` (lanza `ResponseStatusException(UNPROCESSABLE_ENTITY, msg)`; orden campos → 6 → 50 → 100 → rol; `rol` null no falla), `static ResponseStatusException regla(String)`. La Task 2 añade `MSG_NO_ENCONTRADO` y `msgTieneReferencias`; la Task 3, `MSG_RELLENA` y `validarCambioPassword`.
  - `POST /api/usuarios/tecnicos`: 422 `{message}` con los cinco textos exactos de la spec §4.1 antes de mirar duplicados; nombres recortados en la comprobación de duplicados, en el `INSERT` y en el log `CREAR_USUARIO`; el 400 en texto plano del rol desaparece (el `IllegalArgumentException` del DAO ya no se alcanza); los dos 409 y el 409 de `DataIntegrityViolationException` se conservan con su cuerpo `Map.of("message", …)`; éxito 201 como hoy.
  - `UsuarioController.RegistrarTecnicoRequest` package-private (springdoc lo sigue publicando como `UsuarioRegistrarTecnicoRequest`).

**Supuestos:** ninguno sin verificar: el controlador y los tests se han comprobado contra `main` 3dccc4a. La contraseña "vacía" es `isEmpty()` sin trim (calco de `RegisterController :262-265`: solo se recortan los nombres).

Todos los comandos de las Tasks 1-5 se ejecutan desde la raíz del repo del servidor (`C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor`) en Bash.

- [ ] **Step 1: Crear la rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git log --oneline -1
git checkout -b feature/web-gestion
```

Expected: `3dccc4a Merge branch 'feature/web-pedidos'` y `Switched to a new branch 'feature/web-gestion'`.

- [ ] **Step 2: Test del alta (falla)**

`src/test/java/com/reparaciones/servidor/controller/UsuarioControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** UsuarioController (spec 6 §4.1 y §4.2): 422 del alta en orden, trim guardado, 409 de duplicados, 404 de ids
 *  inexistentes y 409 del borrado con referencias. Mockito sin Spring. */
class UsuarioControllerTest {

    private final UsuarioDAO dao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioController ctl = new UsuarioController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin-prueba", "", "ADMIN", null);

    private static UsuarioController.RegistrarTecnicoRequest alta(String tecnico, String usuario, String password, String rol) {
        return new UsuarioController.RegistrarTecnicoRequest(tecnico, usuario, password, rol);
    }

    /** Un 422 nunca escribe ni registra log (ni siquiera consulta duplicados). */
    private String falla422(UsuarioController.RegistrarTecnicoRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.registrarTecnico(req, admin));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verifyNoInteractions(dao, logDao);
        return e.getReason();
    }

    // ── alta: los cinco 422 en orden ──
    @Test void altaConCampoVacioEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", "usuario-a", "secreta1", "TECNICO")));
    }

    @Test void altaConNombresEnBlancoONulosEs422() {
        assertEquals("Todos los campos son obligatorios.", falla422(alta("   ", "usuario-a", "secreta1", "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", null, "secreta1", "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", "usuario-a", null, "TECNICO")));
        assertEquals("Todos los campos son obligatorios.", falla422(alta("tecnico-a", "usuario-a", "", "TECNICO")));
    }

    @Test void altaConPasswordCortaEs422() {
        assertEquals("La contraseña debe tener al menos 6 caracteres.",
                falla422(alta("tecnico-a", "usuario-a", "12345", "TECNICO")));
    }

    @Test void altaConUsuarioDeMasDe50Es422() {
        assertEquals("El nombre de usuario no puede superar 50 caracteres.",
                falla422(alta("tecnico-a", "u".repeat(51), "secreta1", "TECNICO")));
    }

    @Test void altaConTecnicoDeMasDe100Es422() {
        assertEquals("El nombre del técnico no puede superar 100 caracteres.",
                falla422(alta("t".repeat(101), "usuario-a", "secreta1", "TECNICO")));
    }

    @Test void altaConRolNoPermitidoEs422EnVezDe400() {
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "secreta1", "ADMIN")));
        assertEquals("Rol no permitido.", falla422(alta("tecnico-a", "usuario-a", "secreta1", "")));
    }

    /** Parando en la primera: con todo mal a la vez sale el primero de la lista, y así sucesivamente. */
    @Test void elOrdenEsCamposSeisCincuentaCienRol() {
        String largo51 = "u".repeat(51);
        String largo101 = "t".repeat(101);
        assertEquals("Todos los campos son obligatorios.", falla422(alta("", largo51, "1", "ADMIN")));
        assertEquals("La contraseña debe tener al menos 6 caracteres.", falla422(alta(largo101, largo51, "1", "ADMIN")));
        assertEquals("El nombre de usuario no puede superar 50 caracteres.",
                falla422(alta(largo101, largo51, "secreta1", "ADMIN")));
        assertEquals("El nombre del técnico no puede superar 100 caracteres.",
                falla422(alta(largo101, "usuario-a", "secreta1", "ADMIN")));
    }

    /** Los límites exactos pasan: 6 caracteres, 50 y 100 (tras el trim). */
    @Test void losLimitesExactosSonValidos() {
        String usuario50 = "u".repeat(50);
        String tecnico100 = "t".repeat(100);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" " + tecnico100 + " ", " " + usuario50 + " ", "123456", "TECNICO"), admin);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico(tecnico100, usuario50, "123456", "TECNICO");
    }

    // ── alta: trim guardado, rol por defecto, 201 con log ──
    @Test void altaValidaGuardaLosNombresRecortadosYRegistraLog() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("  tecnico-a ", " usuario-a  ", " secreta1 ", "SUPERTECNICO"), admin);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).existeNombreTecnico("tecnico-a");
        verify(dao).existeNombreUsuario("usuario-a");
        // la contraseña no se recorta (calco del cliente)
        verify(dao).registrarTecnico("tecnico-a", "usuario-a", " secreta1 ", "SUPERTECNICO");
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: SUPERTECNICO, TECNICO: tecnico-a");
    }

    @Test void altaSinRolGuardaTecnico() {
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "secreta1", null), admin);
        assertEquals(201, resp.getStatusCode().value());
        verify(dao).registrarTecnico("tecnico-a", "usuario-a", "secreta1", "TECNICO");
        verify(logDao).insertar(1, "CREAR_USUARIO", "NOMBRE_USUARIO: usuario-a, ROL: TECNICO, TECNICO: tecnico-a");
    }

    // ── alta: los dos 409 de siempre ──
    @Test void tecnicoDuplicadoEs409SinEscribir() {
        when(dao.existeNombreTecnico("tecnico-a")).thenReturn(true);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta(" tecnico-a ", "usuario-a", "secreta1", "TECNICO"), admin);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ya existe un técnico con ese nombre."), resp.getBody());
        verify(dao, never()).registrarTecnico(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(logDao);
    }

    @Test void usuarioDuplicadoEs409SinEscribir() {
        when(dao.existeNombreUsuario("usuario-a")).thenReturn(true);
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a ", "secreta1", "TECNICO"), admin);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ese nombre de usuario ya existe."), resp.getBody());
        verify(dao, never()).registrarTecnico(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(logDao);
    }

    @Test void violacionDeIntegridadSigueSiendo409SinLog() {
        doThrow(new DataIntegrityViolationException("duplicado"))
                .when(dao).registrarTecnico("tecnico-a", "usuario-a", "secreta1", "TECNICO");
        ResponseEntity<?> resp = ctl.registrarTecnico(alta("tecnico-a", "usuario-a", "secreta1", "TECNICO"), admin);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Ese nombre de usuario ya existe."), resp.getBody());
        verifyNoInteractions(logDao);
    }
}
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=UsuarioControllerTest test`
Expected: FAIL de compilación (`RegistrarTecnicoRequest has private access in UsuarioController`).

- [ ] **Step 4: `ValidacionUsuarios` (alta)**

`src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java`:

```java
package com.reparaciones.servidor.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/** Reglas del alta de usuarios y técnicos y del cambio de contraseña que hasta el sub-proyecto 6 solo aplicaba el
 *  cliente JavaFX (spec 6 §4.1, §4.2 y §4.4). Mismos textos que el cliente cuando existen. Un 422 se lanza siempre
 *  antes de escribir y de registrar log. La usan UsuarioController y AuthController. */
final class ValidacionUsuarios {

    private ValidacionUsuarios() {}

    /** Los dos roles del combo del alta (RegisterController :53); ADMIN no se crea desde la aplicación. */
    static final Set<String> ROLES = Set.of("TECNICO", "SUPERTECNICO");

    static final String MSG_CAMPOS         = "Todos los campos son obligatorios.";
    static final String MSG_PASSWORD_CORTA = "La contraseña debe tener al menos 6 caracteres.";
    static final String MSG_USUARIO_LARGO  = "El nombre de usuario no puede superar 50 caracteres.";
    static final String MSG_TECNICO_LARGO  = "El nombre del técnico no puede superar 100 caracteres.";
    static final String MSG_ROL            = "Rol no permitido.";

    /** Orden del cliente y de la spec: campos → 6 → 50 → 100 → rol; para en el primero. Los nombres llegan ya
     *  recortados (null si venían null); la contraseña no se recorta (calco). {@code rol} null vale TECNICO. */
    static void validarAlta(String nombreTecnico, String nombreUsuario, String password, String rol) {
        if (vacio(nombreTecnico) || vacio(nombreUsuario) || vacio(password)) throw regla(MSG_CAMPOS);
        if (password.length() < 6) throw regla(MSG_PASSWORD_CORTA);
        if (nombreUsuario.length() > 50) throw regla(MSG_USUARIO_LARGO);
        if (nombreTecnico.length() > 100) throw regla(MSG_TECNICO_LARGO);
        if (rol != null && !ROLES.contains(rol)) throw regla(MSG_ROL);
    }

    private static boolean vacio(String s) {
        return s == null || s.isEmpty();
    }

    static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
```

- [ ] **Step 5: `registrarTecnico` validado y record package-private**

En `UsuarioController.java`, sustituir `registrarTecnico` (:35-59) por:

```java
    /** Alta de usuario y técnico (spec 6 §4.1): los nombres se recortan antes de validar y se guardan recortados;
     *  los cinco 422 de {@link ValidacionUsuarios#validarAlta} van antes que los dos 409 de duplicado de siempre.
     *  Un 422 o un 409 no escriben ni registran log. */
    @PostMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombreTecnico = recortar(req.nombreTecnico());
        String nombreUsuario = recortar(req.nombreUsuario());
        ValidacionUsuarios.validarAlta(nombreTecnico, nombreUsuario, req.password(), req.rol());
        if (dao.existeNombreTecnico(nombreTecnico)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ya existe un técnico con ese nombre."));
        }
        if (dao.existeNombreUsuario(nombreUsuario)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        String rol = req.rol() != null ? req.rol() : "TECNICO";
        try {
            dao.registrarTecnico(nombreTecnico, nombreUsuario, req.password(), rol);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                "NOMBRE_USUARIO: " + nombreUsuario + ", ROL: " + rol + ", TECNICO: " + nombreTecnico);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static String recortar(String s) {
        return s == null ? null : s.trim();
    }
```

(El `catch (IllegalArgumentException e) → 400 texto plano` desaparece: el rol ya lo corta `validarAlta` con 422. El log sale del `try`: solo el `INSERT` puede dar el 409 de integridad.)

Y el record (:122):

```java
    /** Package-private (no private) para que los tests lo construyan; springdoc lo publica con el mismo nombre. */
    record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String password, String rol) {}
```

- [ ] **Step 6: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=UsuarioControllerTest test; grep -h "Tests run" target/surefire-reports/*UsuarioControllerTest.txt`
Expected: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 … in com.reparaciones.servidor.controller.UsuarioControllerTest`.

- [ ] **Step 7: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'`
Expected: `mvn` sin fallos y `434` (421 + 13). `OpenApiContractTest` sigue en verde: `UsuarioRegistrarTecnicoRequest` conserva su nombre. (El recuento solo vale tras una ejecución completa: un `-Dtest=` deja los informes viejos del resto.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java src/main/java/com/reparaciones/servidor/controller/UsuarioController.java src/test/java/com/reparaciones/servidor/controller/UsuarioControllerTest.java
git commit -m "feat(usuarios): 422 del alta de usuarios con los textos del cliente y nombres guardados sin espacios"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 13.

---

## Task 2: Servidor — 404 de técnico inexistente, todas las referencias en `tiene-reparaciones` y 409 del borrado

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java:88-93` (`tieneReparaciones` → bloque con `REFERENCIAS_TECNICO`, `REFERENCIAS_USUARIO`, `existeTecnico`, `getIdUsuByIdTec`, `tieneReferencias`, `existe` y `tieneReparaciones` delegando)
- Modify: `src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java` (tras `MSG_ROL`: `MSG_NO_ENCONTRADO` y `msgTieneReferencias`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/UsuarioController.java` (clase completa: import de `ResponseStatusException`, `activarTecnico` :61-70, `desactivarTecnico` :72-81, `tieneReparaciones` :105-109, `eliminarTecnico` :111-120, `exigirTecnico` nuevo)
- Create: `src/test/java/com/reparaciones/servidor/dao/UsuarioDAOReferenciasTest.java`
- Modify: `src/test/java/com/reparaciones/servidor/controller/UsuarioControllerTest.java` (10 tests nuevos al final, import `anyInt`)

**Interfaces:**
- Consumes: `ValidacionUsuarios` (Task 1); `UsuarioDAO.getNombreByIdTec(int)` (`:118-121`), `UsuarioDAO.eliminarTecnico(int idTec, int idUsu)` (`:95-100`, misma firma y cuerpo: `Log_Actividad`, `Usuario`, `Tecnico` en una transacción), `activarTecnico`/`desactivarTecnico` (`:71-77`).
- Produces:
  - `UsuarioDAO`: `public boolean existeTecnico(int idTec)` (`SELECT COUNT(*) FROM Tecnico WHERE ID_TEC = ?`), `public Integer getIdUsuByIdTec(int idTec)` (`queryForList("SELECT ID_USU FROM Usuario WHERE ID_TEC = ?", Integer.class, idTec)`, null si no hay fila), `public boolean tieneReferencias(int idTec)`, `public boolean tieneReparaciones(int idTec)` (misma firma, delega). Constantes package-private `REFERENCIAS_TECNICO` (3 SQL) y `REFERENCIAS_USUARIO` (6 SQL).
  - `ValidacionUsuarios.MSG_NO_ENCONTRADO = "Técnico no encontrado."` y `static String msgTieneReferencias(String nombreTecnico)` → `"\"" + nombre + "\" tiene reparaciones asociadas."`.
  - `PATCH /api/usuarios/tecnicos/{idTec}/activar` y `…/desactivar`, `GET …/{idTec}/tiene-reparaciones` y `DELETE …/{idTec}`: 404 `{message: "Técnico no encontrado."}` si el técnico no existe (antes 500, y `{value:false}` en `tiene-reparaciones`).
  - `GET …/tiene-reparaciones` → `{value: tieneReferencias(idTec)}` (misma forma `Map<String, Boolean>`).
  - `DELETE …/{idTec}?idUsu=`: `idUsu` pasa a `@RequestParam(required = false) Integer` y se ignora; orden: 404 → nombre → 409 `"\"{nombre}\" tiene reparaciones asociadas."` sin borrar ni registrar → `idUsu` resuelto con `getIdUsuByIdTec` (null → 404) → `eliminarTecnico(idTec, idUsuReal)` → log `ELIMINAR_USUARIO` `"ID_TEC: {idTec}, ID_USU: {idUsuReal}, NOMBRE: {nombre}"`.
  - Excluir/incluir de estadísticas: sin cambios (`UsuarioControllerExclusionTest` sigue igual).

**Decisión (una consulta o varias):** nueve consultas `SELECT EXISTS(SELECT 1 FROM <tabla> WHERE <columna> = ?)`, una por referencia, recorridas en orden y parando en la primera que exista. Motivos: (1) la spec §9 pide que **cada** referencia por separado dé `true`; con una consulta única de nueve `EXISTS` sumados, el test con `JdbcTemplate` mockeado solo comprobaría que se devuelve lo que devuelve el mock, mientras que con una consulta por referencia cada test fija el SQL exacto (tabla y columna) de su referencia; (2) todas las columnas tienen índice (el que crea su FK) y `EXISTS` corta en la primera fila; (3) se ejecuta solo al pulsar la papelera, una acción rara de ADMIN; en el caso normal (técnico con historial) basta la primera consulta.

**Supuestos:** las tablas `Revision`, `Envio`, `Envio_Telefono` y `Movimiento_telefono` existen en las dos BD donde corre este servidor (están en `sql/crear_bd.sql:102-160, 334-349` y en las migraciones `migracion-f2b-revision.sql`, `migracion-f2c-envios.sql` y `migracion-f2a-lotes.sql`). Si faltara alguna, `tiene-reparaciones` y el `DELETE` darían 500. **Antes del despliegue** (paso del usuario en el cierre) conviene comprobarlo en cada BD con `SHOW TABLES LIKE 'Revision'; SHOW TABLES LIKE 'Envio%'; SHOW TABLES LIKE 'Movimiento_telefono';`.

- [ ] **Step 1: Test de las referencias en `UsuarioDAO` (falla)**

`src/test/java/com/reparaciones/servidor/dao/UsuarioDAOReferenciasTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Comprobación de datos asociados antes de borrar un técnico (spec 6 §4.2, G8): cada una de las nueve columnas con FK
 *  hacia el técnico o su usuario basta para responder true; sin ninguna, false. Un queryForObject sin stub devuelve
 *  null (= no existe). JdbcTemplate mockeado, sin Spring. */
class UsuarioDAOReferenciasTest {

    private static final int ID_TEC = 7;
    private static final int ID_USU = 20;
    private static final String SQL_ID_USU = "SELECT ID_USU FROM Usuario WHERE ID_TEC = ?";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioDAO dao = new UsuarioDAO(jdbc, mock(PasswordEncoder.class));

    UsuarioDAOReferenciasTest() {
        when(jdbc.queryForList(SQL_ID_USU, Integer.class, ID_TEC)).thenReturn(List.of(ID_USU));
    }

    private void existe(String sql, int id) {
        when(jdbc.queryForObject(sql, Integer.class, id)).thenReturn(1);
    }

    // ── las tres columnas hacia Tecnico.ID_TEC ──
    @Test void reparacionPropiaCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC = ?)", ID_TEC);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void haberAsignadoCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC_ASIGNA = ?)", ID_TEC);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void haberEntregadoGlassCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ENTREGADO_POR = ?)", ID_TEC);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    // ── las seis columnas hacia Usuario.ID_USU (con el idUsu resuelto desde idTec) ──
    @Test void revisionEsteticaCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Revision WHERE EST_ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void revisionFuncionalCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Revision WHERE FUN_ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void envioCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Envio WHERE ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void devolucionDeEnvioCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Envio_Telefono WHERE ID_USU_DEVOLUCION = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void solicitudDeStockCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Solicitud_Stock WHERE ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void movimientoDeTelefonoCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Movimiento_telefono WHERE ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    // ── ninguna ──
    @Test void sinNingunaReferenciaEsFalseYMiraLasNueve() {
        assertFalse(dao.tieneReferencias(ID_TEC));
        for (String sql : UsuarioDAO.REFERENCIAS_TECNICO) verify(jdbc).queryForObject(sql, Integer.class, ID_TEC);
        for (String sql : UsuarioDAO.REFERENCIAS_USUARIO) verify(jdbc).queryForObject(sql, Integer.class, ID_USU);
        assertEquals(3, UsuarioDAO.REFERENCIAS_TECNICO.size());
        assertEquals(6, UsuarioDAO.REFERENCIAS_USUARIO.size());
    }

    /** Un técnico sin fila en Usuario solo se mira por sus columnas de Tecnico. */
    @Test void tecnicoSinUsuarioSoloMiraReparacion() {
        when(jdbc.queryForList(SQL_ID_USU, Integer.class, ID_TEC)).thenReturn(List.of());
        assertFalse(dao.tieneReferencias(ID_TEC));
        verify(jdbc, times(3)).queryForObject(anyString(), eq(Integer.class), anyInt());
    }

    /** tieneReparaciones (lo que llama el JavaFX) delega: un supertécnico que solo ha asignado ya no pasa. */
    @Test void tieneReparacionesDelegaEnTieneReferencias() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC_ASIGNA = ?)", ID_TEC);
        assertTrue(dao.tieneReparaciones(ID_TEC));
    }

    // ── lecturas auxiliares ──
    @Test void existeTecnicoCuentaPorId() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM Tecnico WHERE ID_TEC = ?", Integer.class, ID_TEC)).thenReturn(1);
        assertTrue(dao.existeTecnico(ID_TEC));
        assertFalse(dao.existeTecnico(99));
    }

    @Test void getIdUsuByIdTecDevuelveElUsuarioONull() {
        assertEquals(Integer.valueOf(ID_USU), dao.getIdUsuByIdTec(ID_TEC));
        assertNull(dao.getIdUsuByIdTec(99));
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=UsuarioDAOReferenciasTest test`
Expected: FAIL de compilación (`cannot find symbol: method tieneReferencias(int)`, `REFERENCIAS_TECNICO`, `existeTecnico`, `getIdUsuByIdTec`).

- [ ] **Step 3: `UsuarioDAO` con las referencias**

En `UsuarioDAO.java`, sustituir `tieneReparaciones` (:88-93) por:

```java
    /** Las columnas que apuntan a Tecnico.ID_TEC con FK (sql/crear_bd.sql :180-183). */
    static final List<String> REFERENCIAS_TECNICO = List.of(
            "SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC = ?)",
            "SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC_ASIGNA = ?)",
            "SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ENTREGADO_POR = ?)");

    /** Las columnas que apuntan a Usuario.ID_USU con FK (sql/crear_bd.sql :130-131, :144, :160, :316, :346), salvo
     *  Log_Actividad, que eliminarTecnico borra a propósito (calco, spec 6 G8). */
    static final List<String> REFERENCIAS_USUARIO = List.of(
            "SELECT EXISTS(SELECT 1 FROM Revision WHERE EST_ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Revision WHERE FUN_ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Envio WHERE ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Envio_Telefono WHERE ID_USU_DEVOLUCION = ?)",
            "SELECT EXISTS(SELECT 1 FROM Solicitud_Stock WHERE ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Movimiento_telefono WHERE ID_USU = ?)");

    public boolean existeTecnico(int idTec) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM Tecnico WHERE ID_TEC = ?", Integer.class, idTec);
        return n != null && n > 0;
    }

    /** El usuario de un técnico, o null si el técnico no tiene fila en Usuario. */
    public Integer getIdUsuByIdTec(int idTec) {
        List<Integer> ids = jdbc.queryForList("SELECT ID_USU FROM Usuario WHERE ID_TEC = ?", Integer.class, idTec);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** true si el técnico o su usuario aparecen en cualquiera de las nueve columnas con FK hacia ellos (spec 6 §4.2):
     *  lo que haría fallar el borrado por integridad. Una consulta por referencia, parando en la primera que exista. */
    public boolean tieneReferencias(int idTec) {
        for (String sql : REFERENCIAS_TECNICO) {
            if (existe(sql, idTec)) return true;
        }
        Integer idUsu = getIdUsuByIdTec(idTec);
        if (idUsu == null) return false;
        for (String sql : REFERENCIAS_USUARIO) {
            if (existe(sql, idUsu)) return true;
        }
        return false;
    }

    private boolean existe(String sql, int id) {
        Integer n = jdbc.queryForObject(sql, Integer.class, id);
        return n != null && n > 0;
    }

    /** Se conserva por compatibilidad: desde el sub-proyecto 6 mira todas las referencias, no solo Reparacion.ID_TEC. */
    public boolean tieneReparaciones(int idTec) {
        return tieneReferencias(idTec);
    }
```

(`java.util.List` ya está importado en `:14`.)

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=UsuarioDAOReferenciasTest test; grep -h "Tests run" target/surefire-reports/*UsuarioDAOReferenciasTest.txt`
Expected: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Tests de 404, `tiene-reparaciones` y borrado en el controlador (fallan)**

En `UsuarioControllerTest.java`, añadir el import:

```java
import static org.mockito.ArgumentMatchers.anyInt;
```

y, antes de la llave de cierre de la clase, estos tests:

```java
    // ── 404 de idTec inexistente (spec 6 §4.2) ──
    private static void noEncontrado(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Técnico no encontrado.", e.getReason());
    }

    @Test void activarUnInexistenteEs404SinEscribir() {
        noEncontrado(() -> ctl.activarTecnico(99, admin));
        verify(dao, never()).activarTecnico(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void desactivarUnInexistenteEs404SinEscribir() {
        noEncontrado(() -> ctl.desactivarTecnico(99, admin));
        verify(dao, never()).desactivarTecnico(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void tieneReparacionesDeUnInexistenteEs404() {
        noEncontrado(() -> ctl.tieneReparaciones(99));
        verify(dao, never()).tieneReferencias(anyInt());
    }

    @Test void eliminarUnInexistenteEs404SinBorrar() {
        noEncontrado(() -> ctl.eliminarTecnico(99, 20, admin));
        verify(dao, never()).eliminarTecnico(anyInt(), anyInt());
        verifyNoInteractions(logDao);
    }

    // ── activar / desactivar como hoy ──
    @Test void activarYDesactivarEscribenYRegistranLog() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        ctl.desactivarTecnico(7, admin);
        ctl.activarTecnico(7, admin);
        verify(dao).desactivarTecnico(7);
        verify(dao).activarTecnico(7);
        verify(logDao).insertar(1, "DESACTIVAR_USUARIO", "ID_TEC: 7, NOMBRE: tecnico-a");
        verify(logDao).insertar(1, "ACTIVAR_USUARIO", "ID_TEC: 7, NOMBRE: tecnico-a");
    }

    // ── tiene-reparaciones mira todas las referencias ──
    @Test void tieneReparacionesDevuelveTieneReferencias() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.tieneReferencias(7)).thenReturn(true);
        assertEquals(Map.of("value", true), ctl.tieneReparaciones(7));
        when(dao.tieneReferencias(7)).thenReturn(false);
        assertEquals(Map.of("value", false), ctl.tieneReparaciones(7));
    }

    // ── eliminar: 409 con referencias, idUsu resuelto e ignorado ──
    @Test void eliminarConReferenciasEs409SinBorrarNiRegistrar() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.tieneReferencias(7)).thenReturn(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.eliminarTecnico(7, 20, admin));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("\"tecnico-a\" tiene reparaciones asociadas.", e.getReason());
        verify(dao, never()).eliminarTecnico(anyInt(), anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void eliminarResuelveElUsuarioDesdeElTecnicoEIgnoraElDeLaQuery() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.eliminarTecnico(7, 999, admin);
        verify(dao).eliminarTecnico(7, 20);
        verify(logDao).insertar(1, "ELIMINAR_USUARIO", "ID_TEC: 7, ID_USU: 20, NOMBRE: tecnico-a");
    }

    @Test void eliminarSinIdUsuEnLaQueryTambienBorra() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.getIdUsuByIdTec(7)).thenReturn(20);
        ctl.eliminarTecnico(7, null, admin);
        verify(dao).eliminarTecnico(7, 20);
    }

    /** Un Tecnico sin fila en Usuario no sale en la tabla (JOIN) y no se borra por aquí: 404. */
    @Test void eliminarUnTecnicoSinUsuarioEs404() {
        when(dao.existeTecnico(7)).thenReturn(true);
        when(dao.getNombreByIdTec(7)).thenReturn("tecnico-a");
        when(dao.getIdUsuByIdTec(7)).thenReturn(null);   // un mock devuelve 0 para Integer si no se dice
        noEncontrado(() -> ctl.eliminarTecnico(7, 20, admin));
        verify(dao, never()).eliminarTecnico(anyInt(), anyInt());
        verifyNoInteractions(logDao);
    }
```

- [ ] **Step 6: Ejecutar y ver que fallan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=UsuarioControllerTest test`
Expected: FAIL de compilación en `eliminarSinIdUsuEnLaQueryTambienBorra` (`incompatible types: <null> cannot be converted to int`: `idUsu` todavía es `int` obligatorio).

- [ ] **Step 7: Textos de 404 y 409 en `ValidacionUsuarios`**

En `ValidacionUsuarios.java`, justo después de `static final String MSG_ROL = "Rol no permitido.";`, añadir:

```java
    static final String MSG_NO_ENCONTRADO  = "Técnico no encontrado.";

    /** 409 del borrado con datos asociados: el mismo texto que la cabecera del aviso del cliente (RegisterController :224). */
    static String msgTieneReferencias(String nombreTecnico) {
        return "\"" + nombreTecnico + "\" tiene reparaciones asociadas.";
    }
```

- [ ] **Step 8: `UsuarioController` con 404, referencias y borrado seguro**

`src/main/java/com/reparaciones/servidor/controller/UsuarioController.java` completo:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.Usuario;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioDAO dao;
    private final LogDAO     logDao;

    public UsuarioController(UsuarioDAO dao, LogDAO logDao) {
        this.dao    = dao;
        this.logDao = logDao;
    }

    @GetMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> getUsuariosTecnicos() {
        return dao.getUsuariosTecnicos();
    }

    /** Alta de usuario y técnico (spec 6 §4.1): los nombres se recortan antes de validar y se guardan recortados;
     *  los cinco 422 de {@link ValidacionUsuarios#validarAlta} van antes que los dos 409 de duplicado de siempre.
     *  Un 422 o un 409 no escriben ni registran log. */
    @PostMapping("/tecnicos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registrarTecnico(@RequestBody RegistrarTecnicoRequest req,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombreTecnico = recortar(req.nombreTecnico());
        String nombreUsuario = recortar(req.nombreUsuario());
        ValidacionUsuarios.validarAlta(nombreTecnico, nombreUsuario, req.password(), req.rol());
        if (dao.existeNombreTecnico(nombreTecnico)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ya existe un técnico con ese nombre."));
        }
        if (dao.existeNombreUsuario(nombreUsuario)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        String rol = req.rol() != null ? req.rol() : "TECNICO";
        try {
            dao.registrarTecnico(nombreTecnico, nombreUsuario, req.password(), rol);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ese nombre de usuario ya existe."));
        }
        logDao.insertar(principal.getIdUsu(), "CREAR_USUARIO",
                "NOMBRE_USUARIO: " + nombreUsuario + ", ROL: " + rol + ", TECNICO: " + nombreTecnico);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static String recortar(String s) {
        return s == null ? null : s.trim();
    }

    @PatchMapping("/tecnicos/{idTec}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activarTecnico(@PathVariable int idTec,
                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirTecnico(idTec);
        String nombre = dao.getNombreByIdTec(idTec);
        dao.activarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "ACTIVAR_USUARIO",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarTecnico(@PathVariable int idTec,
                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirTecnico(idTec);
        String nombre = dao.getNombreByIdTec(idTec);
        dao.desactivarTecnico(idTec);
        logDao.insertar(principal.getIdUsu(), "DESACTIVAR_USUARIO",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/excluir-estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirEstadisticas(@PathVariable int idTec,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.excluirEstadisticas(idTec);
        logDao.insertar(principal.getIdUsu(), "EXCLUIR_ESTADISTICAS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @PatchMapping("/tecnicos/{idTec}/incluir-estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void incluirEstadisticas(@PathVariable int idTec,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = dao.getNombreByIdTec(idTec);
        dao.incluirEstadisticas(idTec);
        logDao.insertar(principal.getIdUsu(), "INCLUIR_ESTADISTICAS",
                "ID_TEC: " + idTec + ", NOMBRE: " + nombre);
    }

    @GetMapping("/tecnicos/{idTec}/tiene-reparaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> tieneReparaciones(@PathVariable int idTec) {
        exigirTecnico(idTec);
        return Map.of("value", dao.tieneReferencias(idTec));
    }

    /** Spec 6 §4.2 (G8): el servidor vuelve a comprobar todas las referencias (409 sin borrar nada) y resuelve el
     *  usuario desde idTec; el idUsu de la query se conserva en el contrato (opcional) para el JavaFX y se ignora. */
    @DeleteMapping("/tecnicos/{idTec}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTecnico(@PathVariable int idTec,
                                @io.swagger.v3.oas.annotations.Parameter(
                                        description = "Ignorado: el servidor lo resuelve desde idTec")
                                @RequestParam(required = false) Integer idUsu,
                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        exigirTecnico(idTec);
        String nombre = dao.getNombreByIdTec(idTec);
        if (dao.tieneReferencias(idTec)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ValidacionUsuarios.msgTieneReferencias(nombre));
        }
        Integer idUsuReal = dao.getIdUsuByIdTec(idTec);
        if (idUsuReal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ValidacionUsuarios.MSG_NO_ENCONTRADO);
        }
        dao.eliminarTecnico(idTec, idUsuReal);
        logDao.insertar(principal.getIdUsu(), "ELIMINAR_USUARIO",
                "ID_TEC: " + idTec + ", ID_USU: " + idUsuReal + ", NOMBRE: " + nombre);
    }

    /** 404 {message: "Técnico no encontrado."} en vez del 500 de getNombreByIdTec (spec 6 §4.2). */
    private void exigirTecnico(int idTec) {
        if (!dao.existeTecnico(idTec)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ValidacionUsuarios.MSG_NO_ENCONTRADO);
        }
    }

    /** Package-private (no private) para que los tests lo construyan; springdoc lo publica con el mismo nombre. */
    record RegistrarTecnicoRequest(String nombreTecnico, String nombreUsuario, String password, String rol) {}
}
```

- [ ] **Step 9: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest='UsuarioControllerTest,UsuarioDAOReferenciasTest,UsuarioControllerExclusionTest' test; grep -h "Tests run" target/surefire-reports/*Usuario*.txt`
Expected: PASS: `UsuarioControllerTest` 23, `UsuarioDAOReferenciasTest` 14, `UsuarioControllerExclusionTest` 2.

- [ ] **Step 10: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'`
Expected: sin fallos y `458` (434 + 24: 14 del DAO y 10 del controlador). `OpenApiContractTest` sigue en verde (el parámetro `idUsu` pasa a `required: false`; lo comprueba la Task 5).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/UsuarioDAO.java src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java src/main/java/com/reparaciones/servidor/controller/UsuarioController.java src/test/java/com/reparaciones/servidor/dao/UsuarioDAOReferenciasTest.java src/test/java/com/reparaciones/servidor/controller/UsuarioControllerTest.java
git commit -m "feat(usuarios): 404 de técnico inexistente y 409 al borrar con datos asociados mirando todas las referencias"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 24 (14 + 10).

---

## Task 3: Servidor — guarda de ADMIN en `/api/tecnicos` y 422 de cambiar contraseña

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/controller/TecnicoController.java:37` (`insertar`) y `:45` (`eliminar`): `@PreAuthorize("hasRole('ADMIN')")` (`PreAuthorize` ya importado en `:8`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java` (`MSG_RELLENA` y `validarCambioPassword`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/AuthController.java:62-63` (el `badRequest()` sale; entra `validarCambioPassword`)
- Create: `src/test/java/com/reparaciones/servidor/controller/RolesUsuarioTecnicoTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/AuthControllerCambiarPasswordTest.java`

**Interfaces:**
- Consumes: `TecnicoDAO.insertar(String)` (`TecnicoDAO.java:45`), `TecnicoDAO.eliminar(int)` (`:49`), `TecnicoDAO.getNombreById(int)` (`:59`); `JwtUtil.generateToken(UsuarioPrincipal)`; `UsuarioDAO.cambiarPassword(int, String, String)` (`UsuarioDAO.java:102-116`, sin cambios: sigue lanzando `IllegalArgumentException("Contraseña actual incorrecta.")`); `AuthController.CambiarPasswordRequest` (ya package-private, `:74`); `ValidacionUsuarios.regla`/`vacio`/`MSG_PASSWORD_CORTA` (Task 1).
- Produces:
  - `POST /api/tecnicos` y `DELETE /api/tecnicos/{idTec}`: 403 para TECNICO y SUPERTECNICO; ADMIN como hoy (201 / 204).
  - `ValidacionUsuarios.MSG_RELLENA = "Rellena todos los campos."` y `static void validarCambioPassword(String passwordActual, String passwordNueva)`: null o `isEmpty()` (sin trim) en cualquiera → 422 `MSG_RELLENA`; `passwordNueva.length() < 6` → 422 `MSG_PASSWORD_CORTA`.
  - `PATCH /api/auth/cambiar-password`: 422 `{message}` "Rellena todos los campos." / "La contraseña debe tener al menos 6 caracteres." antes del DAO (sustituyen al 400 sin cuerpo y al 422 "rawPassword cannot be null"); después, el 422 "Contraseña actual incorrecta." de siempre (`Map.of("message", …)`); éxito 204 y log `CAMBIAR_PASSWORD` con detalle `""`, como hoy.

**Supuestos:** el encargo menciona `@WithMockUser`, pero `RolesCompraLoteTest` (el patrón que manda la spec) monta `@SpringBootTest` + `@AutoConfigureMockMvc` con tokens JWT reales de `JwtUtil`; se copia ese patrón exacto (ver Desviaciones).

- [ ] **Step 1: Test de roles de `/api/tecnicos` (falla)**

`src/test/java/com/reparaciones/servidor/controller/RolesUsuarioTecnicoTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST y DELETE /api/tecnicos (spec 6 §4.3): solo ADMIN, con la cadena de seguridad real. Hasta el sub-proyecto 6
 *  no tenían @PreAuthorize y cualquier sesión podía crear o borrar un Tecnico. */
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
class RolesUsuarioTecnicoTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;

    @MockBean TecnicoDAO tecnicoDao;
    @MockBean LogDAO logDao;

    private String tecnico()      { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4)); }
    private String supertecnico() { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(7, "usuario-b", "", "SUPERTECNICO", 3)); }
    private String admin()        { return "Bearer " + jwtUtil.generateToken(new UsuarioPrincipal(1, "admin-prueba", "", "ADMIN", null)); }

    private static final String CUERPO = """
            {"nombre":"tecnico-a"}""";

    @Test void crearTecnicoYSupertecnicoReciben403() throws Exception {
        for (String token : new String[] { tecnico(), supertecnico() }) {
            mvc.perform(post("/api/tecnicos").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
        }
        verify(tecnicoDao, never()).insertar(anyString());
    }

    @Test void crearAdminEs201() throws Exception {
        mvc.perform(post("/api/tecnicos").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated());
        verify(tecnicoDao).insertar("tecnico-a");
    }

    @Test void borrarTecnicoYSupertecnicoReciben403() throws Exception {
        for (String token : new String[] { tecnico(), supertecnico() }) {
            mvc.perform(delete("/api/tecnicos/9").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        verify(tecnicoDao, never()).eliminar(anyInt());
    }

    @Test void borrarAdminEs204() throws Exception {
        when(tecnicoDao.getNombreById(9)).thenReturn("tecnico-a");
        mvc.perform(delete("/api/tecnicos/9").header("Authorization", admin()))
                .andExpect(status().isNoContent());
        verify(tecnicoDao).eliminar(9);
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=RolesUsuarioTecnicoTest test`
Expected: FAIL 2 de 4: `crearTecnicoYSupertecnicoReciben403` (`Status expected:<403> but was:<201>`) y `borrarTecnicoYSupertecnicoReciben403` (`Status expected:<403> but was:<204>`); los dos de ADMIN pasan.

- [ ] **Step 3: Guarda de ADMIN en `TecnicoController`**

En `TecnicoController.java`, sustituir la cabecera de `insertar` (:37-38) por:

```java
    /** Solo ADMIN desde el sub-proyecto 6 (spec §4.3): hasta entonces cualquier sesión podía crear un Tecnico
     *  huérfano. Sin consumidor en el JavaFX 0.16.x ni en la web. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
```

y la de `eliminar` (:45-46) por:

```java
    /** Solo ADMIN desde el sub-proyecto 6 (spec §4.3); sin consumidor en el JavaFX 0.16.x ni en la web. */
    @DeleteMapping("/{idTec}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest='RolesUsuarioTecnicoTest,TecnicoControllerGlassTest' test; grep -h "Tests run" target/surefire-reports/*RolesUsuarioTecnicoTest.txt target/surefire-reports/*TecnicoControllerGlassTest.txt`
Expected: PASS, 4 + 3.

- [ ] **Step 5: Test de cambiar contraseña (falla)**

`src/test/java/com/reparaciones/servidor/controller/AuthControllerCambiarPasswordTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** PATCH /api/auth/cambiar-password (spec 6 §4.4): 422 "Rellena todos los campos." y 422 de longitud antes de tocar
 *  la BD (sustituyen al 400 sin cuerpo), el 422 "Contraseña actual incorrecta." de siempre y 204 con log. */
class AuthControllerCambiarPasswordTest {

    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final AuthController ctl = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class),
            logDao, usuarioDao);
    private final UsuarioPrincipal usuario = new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4);

    private static AuthController.CambiarPasswordRequest cambio(String actual, String nueva) {
        return new AuthController.CambiarPasswordRequest(actual, nueva);
    }

    /** Un 422 de validación no llega al DAO ni registra log. */
    private String falla422(AuthController.CambiarPasswordRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.cambiarPassword(usuario, req));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verifyNoInteractions(usuarioDao, logDao);
        return e.getReason();
    }

    @Test void cambioValidoEs204YRegistraLog() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "nueva123"));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "nueva123");
        verify(logDao).insertar(8, "CAMBIAR_PASSWORD", "");
    }

    @Test void camposVaciosOAusentesSon422() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "nueva123")));
        assertEquals("Rellena todos los campos.", falla422(cambio("secreta1", "")));
        assertEquals("Rellena todos los campos.", falla422(cambio(null, "nueva123")));
        assertEquals("Rellena todos los campos.", falla422(cambio("secreta1", null)));
    }

    @Test void nuevaCortaEs422EnVezDe400() {
        assertEquals("La contraseña debe tener al menos 6 caracteres.", falla422(cambio("secreta1", "12345")));
    }

    /** Parando en la primera: con la actual vacía y la nueva corta sale el de campos. */
    @Test void elOrdenEsCamposYDespuesLongitud() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "123")));
    }

    /** Sin trim (calco): seis espacios son una contraseña de 6 caracteres. */
    @Test void losEspaciosCuentanComoCaracteres() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "      "));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "      ");
    }

    @Test void actualIncorrectaSigueSiendo422ConSuTextoYSinLog() {
        doThrow(new IllegalArgumentException("Contraseña actual incorrecta."))
                .when(usuarioDao).cambiarPassword(8, "otra-cosa", "nueva123");
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("otra-cosa", "nueva123"));
        assertEquals(422, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Contraseña actual incorrecta."), resp.getBody());
        verifyNoInteractions(logDao);
    }
}
```

- [ ] **Step 6: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=AuthControllerCambiarPasswordTest test`
Expected: FAIL 3 de 6: `camposVaciosOAusentesSon422`, `nuevaCortaEs422EnVezDe400` y `elOrdenEsCamposYDespuesLongitud` con `Expected org.springframework.web.server.ResponseStatusException to be thrown, but nothing was thrown` (hoy devuelve un `ResponseEntity` 400 o llega al DAO); los otros tres ya pasan.

- [ ] **Step 7: `validarCambioPassword` en `ValidacionUsuarios`**

En `ValidacionUsuarios.java`, tras `static final String MSG_NO_ENCONTRADO  = "Técnico no encontrado.";` añadir:

```java
    static final String MSG_RELLENA        = "Rellena todos los campos.";
```

y, justo antes de `private static boolean vacio(String s) {`, añadir:

```java
    /** Cambiar contraseña (spec 6 §4.4), mismo orden que CambiarPasswordController :84-91 y sin trim: alguna vacía o
     *  ausente → "Rellena todos los campos."; nueva de menos de 6 → la misma regla del alta. La confirmación no llega
     *  al servidor (se queda en el cliente). */
    static void validarCambioPassword(String passwordActual, String passwordNueva) {
        if (vacio(passwordActual) || vacio(passwordNueva)) throw regla(MSG_RELLENA);
        if (passwordNueva.length() < 6) throw regla(MSG_PASSWORD_CORTA);
    }
```

- [ ] **Step 8: `AuthController.cambiarPassword` validado**

En `AuthController.java`, sustituir las líneas :62-63:

```java
        if (req.passwordNueva() == null || req.passwordNueva().length() < 6)
            return ResponseEntity.badRequest().build();
```

por:

```java
        // 422 con los textos del cliente antes de BCrypt (spec 6 §4.4): sustituye al 400 sin cuerpo y evita el
        // "rawPassword cannot be null" de matches(null, …). Lanza ResponseStatusException: el catch de abajo no la ve.
        ValidacionUsuarios.validarCambioPassword(req.passwordActual(), req.passwordNueva());
```

El resto del método (:64-70) no cambia.

- [ ] **Step 9: Ejecutar y ver que pasan**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest='AuthControllerCambiarPasswordTest,AuthControllerTest' test; grep -h "Tests run" target/surefire-reports/*AuthController*.txt`
Expected: PASS, 6 + 2.

- [ ] **Step 10: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'`
Expected: sin fallos y `468` (458 + 10: 4 de roles y 6 de contraseña).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/controller/TecnicoController.java src/main/java/com/reparaciones/servidor/controller/ValidacionUsuarios.java src/main/java/com/reparaciones/servidor/controller/AuthController.java src/test/java/com/reparaciones/servidor/controller/RolesUsuarioTecnicoTest.java src/test/java/com/reparaciones/servidor/controller/AuthControllerCambiarPasswordTest.java
git commit -m "feat(seguridad): solo admin crea o borra en /api/tecnicos y 422 al cambiar la contraseña con los textos del cliente"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 10 (4 + 6).

---

## Task 4: Servidor — log con `limite`, días de Madrid, orden estable y `GET /api/logs/acciones`

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/dao/LogDAO.java` (clase completa: imports :8-10, `getFiltered` :40-65; nuevos `MADRID`, `LIMITE_MAX`, `inicioDiaUtc`, sobrecarga con `limite` y `getAcciones`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/LogController.java` (clase completa, 31 líneas: `getAll` :22-30 gana `limite`; `MSG_LIMITE` y `getAcciones` nuevos)
- Create: `src/test/java/com/reparaciones/servidor/dao/LogDAOFiltroTest.java`
- Create: `src/test/java/com/reparaciones/servidor/controller/LogControllerTest.java`

**Interfaces:**
- Consumes: `LogDAO.MAPPER` (`LogDAO.java:17-24`, sin cambios); el criterio de `UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid` (`job/UrgenteAutomaticoJob.java:35-37`: `LocalDate.atStartOfDay(Europe/Madrid)` → instante UTC) y de `ReparacionDAO.getEstadisticasPuntos` (`ReparacionDAO.java:433-434`: `desde` inclusivo, `hasta + 1` exclusivo, `FECHA >= ? AND FECHA < ?`).
- Produces:
  - `LogDAO`: `static final ZoneId MADRID`, `public static final int LIMITE_MAX = 5000`, `static LocalDateTime inicioDiaUtc(LocalDate dia)`, `public List<LogActividad> getFiltered(String accion, String tecnico, LocalDate desde, LocalDate hasta)` (delega con `limite = null`), `public List<LogActividad> getFiltered(String accion, String tecnico, LocalDate desde, LocalDate hasta, Integer limite)`, `public List<String> getAcciones()`.
  - SQL: `… WHERE 1=1 [AND l.ACCION = ?] [AND u.NOMBRE_USUARIO = ?] [AND l.FECHA >= ?] [AND l.FECHA < ?] ORDER BY l.FECHA DESC, l.ID_LOG DESC [LIMIT ?]`; los límites de fecha van como `LocalDateTime` en UTC; desaparece `DATE(l.FECHA)`.
  - `LogController`: `static final String MSG_LIMITE = "Límite no válido (debe estar entre 1 y 5000)."`; `GET /api/logs?…&limite=` (`Integer`, opcional; fuera de 1..5000 → 422 `{message: MSG_LIMITE}` sin consultar; sin él → `null` → todo); `GET /api/logs/acciones` (`hasRole('ADMIN')`) → `List<String>` (`SELECT DISTINCT ACCION FROM Log_Actividad ORDER BY ACCION`).

**Supuestos:** la sesión JDBC y la JVM del contenedor van en UTC (premisa documentada en `ReparacionDAO.java:425-432`), así que un `LocalDateTime` en UTC se compara igual que el `Timestamp.from(instant)` de los precedentes; lo confirma el smoke de la Task 14 filtrando por el día de hoy (riesgo de la spec §12). `LIMITE_MAX` es `public` porque `LogController` está en otro paquete (ver Desviaciones).

- [ ] **Step 1: Test del filtro del log (falla)**

`src/test/java/com/reparaciones/servidor/dao/LogDAOFiltroTest.java`:

```java
package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lectura del log (spec 6 §4.5, G3 y G5): días de Madrid convertidos a UTC con "desde" inclusivo y "hasta" exclusivo
 *  al día siguiente, orden con desempate por ID_LOG y LIMIT solo cuando llega. SQL y parámetros capturados con
 *  ArgumentCaptor (un captor por parámetro de la lista variable, en orden); JdbcTemplate mockeado, sin Spring. */
@SuppressWarnings("unchecked")
class LogDAOFiltroTest {

    private static final String BASE = "SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, l.ACCION, l.DETALLE, l.MOTIVO "
            + "FROM Log_Actividad l JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1=1";
    private static final String ORDEN = " ORDER BY l.FECHA DESC, l.ID_LOG DESC";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LogDAO dao = new LogDAO(jdbc);
    private final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<Object> p = ArgumentCaptor.forClass(Object.class);

    // ── límites de día: Madrid → UTC ──
    @Test void inicioDeUnDiaDeVeranoEsLas22DelDiaAnteriorEnUtc() {
        assertEquals(LocalDateTime.of(2026, 6, 9, 22, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 6, 10)));
    }

    @Test void inicioDeUnDiaDeInviernoEsLas23DelDiaAnteriorEnUtc() {
        assertEquals(LocalDateTime.of(2026, 1, 14, 23, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 1, 15)));
    }

    /** El 29/03/2026 cambia la hora: empieza en invierno (UTC+1) y el siguiente ya en verano (UTC+2). */
    @Test void elDiaDelCambioDeHoraSeCalculaConLaZonaDeCadaExtremo() {
        assertEquals(LocalDateTime.of(2026, 3, 28, 23, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 3, 29)));
        assertEquals(LocalDateTime.of(2026, 3, 29, 22, 0), LogDAO.inicioDiaUtc(LocalDate.of(2026, 3, 30)));
    }

    // ── filtros de fecha ──
    /** Un LOGIN a las 01:30 del 10/06 en Madrid (23:30 UTC del 09/06) sale filtrando por el 10/06: desde 22:00 UTC
     *  del 09/06 hasta antes de las 22:00 UTC del 10/06. */
    @Test void desdeYHastaEnVeranoSonLosLimitesDelDiaDeMadridEnUtc() {
        dao.getFiltered(null, null, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10));
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture(), p.capture());
        assertEquals(BASE + " AND l.FECHA >= ? AND l.FECHA < ?" + ORDEN, sql.getValue());
        assertEquals(List.of(LocalDateTime.of(2026, 6, 9, 22, 0), LocalDateTime.of(2026, 6, 10, 22, 0)), p.getAllValues());
    }

    @Test void desdeYHastaEnInviernoSonLosLimitesDelDiaDeMadridEnUtc() {
        dao.getFiltered(null, null, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20));
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture(), p.capture());
        assertEquals(List.of(LocalDateTime.of(2026, 1, 14, 23, 0), LocalDateTime.of(2026, 1, 20, 23, 0)), p.getAllValues());
    }

    @Test void hastaSoloEsExclusivoAlInicioDelDiaSiguiente() {
        dao.getFiltered(null, null, null, LocalDate.of(2026, 1, 15));
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture());
        assertEquals(BASE + " AND l.FECHA < ?" + ORDEN, sql.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 15, 23, 0), p.getValue());
        assertFalse(sql.getValue().contains("DATE("), "ya no se usa DATE(FECHA) en UTC");
    }

    // ── orden, acción, usuario y límite ──
    @Test void sinFiltrosNiLimiteNoLlevaParametrosYOrdenaConDesempate() {
        dao.getFiltered(null, null, null, null);
        // sin parámetros la llamada es query(sql, mapper, new Object[0]): any(Object[].class) casa el array vacío
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertEquals(BASE + ORDEN, sql.getValue());
        assertFalse(sql.getValue().contains("LIMIT"));
    }

    @Test void laSobrecargaDeCuatroEsSinLimite() {
        LogDAO espia = spy(dao);
        espia.getFiltered("LOGIN", "usuario-a", null, null);
        verify(espia).getFiltered("LOGIN", "usuario-a", null, null, null);
    }

    @Test void accionYUsuarioFiltranPorIgualdadYElLimiteVaAlFinal() {
        dao.getFiltered("CREAR_USUARIO", "admin-prueba", LocalDate.of(2026, 6, 10), null, 1000);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture(), p.capture(), p.capture(), p.capture());
        assertEquals(BASE + " AND l.ACCION = ? AND u.NOMBRE_USUARIO = ? AND l.FECHA >= ?" + ORDEN + " LIMIT ?",
                sql.getValue());
        assertEquals(List.of("CREAR_USUARIO", "admin-prueba", LocalDateTime.of(2026, 6, 9, 22, 0), 1000),
                p.getAllValues());
    }

    @Test void accionYUsuarioEnBlancoNoFiltran() {
        dao.getFiltered("  ", "", null, null, 5);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), p.capture());
        assertEquals(BASE + ORDEN + " LIMIT ?", sql.getValue());
        assertEquals(5, p.getValue());
    }

    // ── acciones ──
    @Test void accionesSonLasDistintasEnOrdenAlfabetico() {
        when(jdbc.queryForList("SELECT DISTINCT ACCION FROM Log_Actividad ORDER BY ACCION", String.class))
                .thenReturn(List.of("CREAR_USUARIO", "LOGIN"));
        assertEquals(List.of("CREAR_USUARIO", "LOGIN"), dao.getAcciones());
    }
}
```

(Ojo con Mockito 5: con cero parámetros, `verify(jdbc).query(sql.capture(), any(RowMapper.class))` casaría con la sobrecarga `query(String, RowMapper)`, que el DAO no llama; por eso ese test usa `any(Object[].class)`. Con parámetros, un captor por elemento de la lista variable, en orden.)

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=LogDAOFiltroTest test`
Expected: FAIL de compilación (`cannot find symbol: method inicioDiaUtc(LocalDate)`, `getFiltered(…, int)`, `getAcciones()`).

- [ ] **Step 3: `LogDAO` con límite, días de Madrid y acciones**

`src/main/java/com/reparaciones/servidor/dao/LogDAO.java` completo:

```java
package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.LogActividad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LogDAO {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Tope del parámetro {@code limite} de GET /api/logs (spec 6 §4.5); lo comprueba LogController. */
    public static final int LIMITE_MAX = 5000;

    private final JdbcTemplate jdbc;

    private static final RowMapper<LogActividad> MAPPER = (rs, row) -> new LogActividad(
            rs.getInt("ID_LOG"),
            rs.getTimestamp("FECHA").toLocalDateTime(),
            rs.getString("NOMBRE_USUARIO"),
            rs.getString("ACCION"),
            rs.getString("DETALLE"),
            rs.getString("MOTIVO")
    );

    public LogDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(int idUsu, String accion, String detalle) {
        insertar(idUsu, accion, detalle, null);
    }

    public void insertar(int idUsu, String accion, String detalle, String motivo) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, ACCION, DETALLE, MOTIVO) VALUES (?, ?, ?, ?)",
                idUsu, accion, detalle, motivo);
    }

    /** Inicio del día {@code dia} en Madrid expresado en UTC, sin zona: así guarda FECHA la BD (sesión y JVM en UTC;
     *  mismo criterio que UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid y ReparacionDAO.getEstadisticasPuntos). */
    static LocalDateTime inicioDiaUtc(LocalDate dia) {
        return dia.atStartOfDay(MADRID).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Sin límite: lo que llama el JavaFX (todo el log que cumpla los filtros). */
    public List<LogActividad> getFiltered(String accion, String tecnico,
                                          LocalDate desde, LocalDate hasta) {
        return getFiltered(accion, tecnico, desde, hasta, null);
    }

    /** Filtros de igualdad de acción y usuario, días de Madrid completos (spec 6 G5: antes DATE(FECHA) en UTC dejaba
     *  lo ocurrido entre las 00:00 y las 01:59 de Madrid en el día anterior), orden estable por fecha e id y, si llega,
     *  un LIMIT (el rango 1..LIMITE_MAX lo valida el controlador). Con desde > hasta devuelve vacío. */
    public List<LogActividad> getFiltered(String accion, String tecnico,
                                          LocalDate desde, LocalDate hasta, Integer limite) {
        StringBuilder sql = new StringBuilder(
                "SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, l.ACCION, l.DETALLE, l.MOTIVO " +
                "FROM Log_Actividad l JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (accion != null && !accion.isBlank()) {
            sql.append(" AND l.ACCION = ?");
            params.add(accion);
        }
        if (tecnico != null && !tecnico.isBlank()) {
            sql.append(" AND u.NOMBRE_USUARIO = ?");
            params.add(tecnico);
        }
        if (desde != null) {
            sql.append(" AND l.FECHA >= ?");
            params.add(inicioDiaUtc(desde));
        }
        if (hasta != null) {
            sql.append(" AND l.FECHA < ?");
            params.add(inicioDiaUtc(hasta.plusDays(1)));
        }
        sql.append(" ORDER BY l.FECHA DESC, l.ID_LOG DESC");
        if (limite != null) {
            sql.append(" LIMIT ?");
            params.add(limite);
        }
        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }

    /** Las acciones que hay de verdad en el log, en orden alfabético (spec 6 G4): alimenta el filtro "Acción...". */
    public List<String> getAcciones() {
        return jdbc.queryForList("SELECT DISTINCT ACCION FROM Log_Actividad ORDER BY ACCION", String.class);
    }
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=LogDAOFiltroTest test; grep -h "Tests run" target/surefire-reports/*LogDAOFiltroTest.txt`
Expected: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Test del controlador del log (falla)**

`src/test/java/com/reparaciones/servidor/controller/LogControllerTest.java`:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** GET /api/logs con limite y GET /api/logs/acciones (spec 6 §4.5). Mockito sin Spring; la guarda de rol se comprueba
 *  sobre la anotación (el resto de GET /api/logs ya era hasRole('ADMIN')). */
class LogControllerTest {

    private final LogDAO logDao = mock(LogDAO.class);
    private final LogController ctl = new LogController(logDao);

    private void limiteNoValido(Integer limite) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.getAll(null, null, null, null, limite));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Límite no válido (debe estar entre 1 y 5000).", e.getReason());
    }

    @Test void limiteFueraDeRangoEs422SinConsultar() {
        limiteNoValido(0);
        limiteNoValido(-1);
        limiteNoValido(5001);
        verifyNoInteractions(logDao);
    }

    @Test void losExtremosDelRangoSonValidos() {
        ctl.getAll(null, null, null, null, 1);
        ctl.getAll(null, null, null, null, 5000);
        verify(logDao).getFiltered(null, null, null, null, 1);
        verify(logDao).getFiltered(null, null, null, null, 5000);
    }

    /** Sin limite (el JavaFX) el DAO recibe null: todo el log, como antes. */
    @Test void sinLimitePasaNullYLosFiltrosTalCual() {
        LocalDate desde = LocalDate.of(2026, 6, 10);
        LocalDate hasta = LocalDate.of(2026, 6, 12);
        ctl.getAll("LOGIN", "usuario-a", desde, hasta, null);
        verify(logDao).getFiltered("LOGIN", "usuario-a", desde, hasta, null);
    }

    @Test void accionesDevuelveLaListaDelDao() {
        when(logDao.getAcciones()).thenReturn(List.of("CREAR_USUARIO", "LOGIN"));
        assertEquals(List.of("CREAR_USUARIO", "LOGIN"), ctl.getAcciones());
    }

    @Test void accionesEsSoloAdmin() throws Exception {
        PreAuthorize guarda = LogController.class.getMethod("getAcciones").getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('ADMIN')", guarda.value());
    }
}
```

- [ ] **Step 6: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=LogControllerTest test`
Expected: FAIL de compilación (`method getAll … cannot be applied to given types` con cinco argumentos; `cannot find symbol: method getAcciones()`).

- [ ] **Step 7: `LogController` con `limite` y `/acciones`**

`src/main/java/com/reparaciones/servidor/controller/LogController.java` completo:

```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.LogActividad;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    static final String MSG_LIMITE = "Límite no válido (debe estar entre 1 y 5000).";

    private final LogDAO logDao;

    public LogController(LogDAO logDao) {
        this.logDao = logDao;
    }

    /** {@code limite} es opcional y aditivo (spec 6 G3): sin él, todo el log que cumpla los filtros, como pide el
     *  JavaFX; la web pide las 1.000 filas más recientes. Fuera de 1..5000 → 422 sin consultar. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LogActividad> getAll(
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer limite) {
        if (limite != null && (limite < 1 || limite > LogDAO.LIMITE_MAX)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_LIMITE);
        }
        return logDao.getFiltered(accion, tecnico, desde, hasta, limite);
    }

    /** Lista real de acciones para el filtro "Acción..." (spec 6 G4). */
    @GetMapping("/acciones")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> getAcciones() {
        return logDao.getAcciones();
    }
}
```

- [ ] **Step 8: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest='LogControllerTest,LogDAOFiltroTest' test; grep -h "Tests run" target/surefire-reports/*Log*Test.txt`
Expected: PASS, 5 + 11.

- [ ] **Step 9: Ejecutar la suite completa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'`
Expected: sin fallos y `484` (468 + 16: 11 del DAO y 5 del controlador).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/dao/LogDAO.java src/main/java/com/reparaciones/servidor/controller/LogController.java src/test/java/com/reparaciones/servidor/dao/LogDAOFiltroTest.java src/test/java/com/reparaciones/servidor/controller/LogControllerTest.java
git commit -m "feat(logs): límite opcional, días de madrid en el filtro de fechas, orden estable y lista de acciones"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 16 (11 + 5).

---

## Task 5: Servidor — contrato: nulos del log, `/api/logs/acciones`, `limite`, `idUsu` opcional y códigos reales

**Files:**
- Modify: `src/main/java/com/reparaciones/servidor/model/LogActividad.java:3` (import de `Schema`) y `:10-11` (`detalle`, `motivo` con `@Schema(nullable = true)`)
- Modify: `src/main/java/com/reparaciones/servidor/controller/UsuarioController.java` (imports; `@ApiResponses` en `registrarTecnico`, `activarTecnico`, `desactivarTecnico` y `eliminarTecnico`, sobre la clase de la Task 2)
- Modify: `src/main/java/com/reparaciones/servidor/controller/AuthController.java:58` (`@ApiResponses` de `cambiarPassword`)
- Modify: `src/test/java/com/reparaciones/servidor/OpenApiContractTest.java` (test nuevo `elContratoPublicaLaGestion` y helpers `parametro` y `assertCodigos`, insertados entre `:308` y el javadoc de `:310`)

**Interfaces:**
- Consumes: todo lo de las Tasks 1-4; los helpers del test `assertNullable` (`OpenApiContractTest.java:411-417`), `assertNoNullable` (`:419-427`), `nombres` (`:429-433`); `OpenApiConfig` (todas las propiedades `required`, la nulabilidad solo con `@Schema(nullable = true)`); el volcado de `target/openapi.json` que hace `elContratoPublicaLosEsquemasDeLaWeb` (`:253-256`).
- Produces (lo que regenera la web en la Task 6, en `schema.d.ts`):
  - `components.schemas.LogActividad`: `detalle` y `motivo` `nullable: true` (→ `string | null`); `idLog`, `fecha`, `nombreUsuario`, `accion` siguen no nulos y requeridos.
  - `paths["/api/logs/acciones"].get` → 200 `string[]` (media `*/*`, como el resto del contrato).
  - `paths["/api/logs"].get.parameters`: `limite` (query, `required: false`, `integer int32`).
  - `paths["/api/usuarios/tecnicos/{idTec}"].delete.parameters`: `idUsu` `required: false` (con descripción "Ignorado: el servidor lo resuelve desde idTec").
  - Respuestas: `POST /api/usuarios/tecnicos` → `201`, `409`, `422` sin cuerpo declarado (desaparece el `200 Record<string, never>`); `DELETE /api/usuarios/tecnicos/{idTec}` → `204`, `404`, `409`; `PATCH …/activar` y `…/desactivar` → `204`, `404`; `PATCH /api/auth/cambiar-password` → `204`, `422` (desaparece el `200 Record<string, never>`).

**Supuestos:** springdoc, cuando un método lleva `@ApiResponses`, publica solo los códigos declarados (verificado: `/api/auth/login` publica únicamente `200` y `401`), así que el `200` genérico de `ResponseEntity<?>` desaparece. `GET …/tiene-reparaciones` y `GET /api/logs` no se anotan: declarar su 404/422 obligaría a redescribir el esquema del 200 (el `Map<String, Boolean>` y la lista), y la web no necesita esos códigos en el tipo (lee `message` del cuerpo del error).

- [ ] **Step 1: Test del contrato de gestión (falla)**

En `OpenApiContractTest.java`, entre el cierre de `elContratoPublicaLosLotesYLosNulosDePedidos` (`:308`) y el javadoc de `lasEscriturasDelFormularioAdmitenClaveDeIdempotencia` (`:310`), insertar:

```java
    /**
     * Sub-proyecto 6 (spec §4.6): la lista de acciones y el límite del log, el idUsu opcional del borrado de técnicos,
     * los nulos de LogActividad y los códigos reales del alta, el borrado, activar/desactivar y cambiar contraseña
     * (antes el contrato decía 200 con un objeto vacío donde el servidor responde 201/204).
     */
    @Test void elContratoPublicaLaGestion() throws Exception {
        String token = jwtUtil.generateToken(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        var res = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
        assertEquals(200, res.getStatus());

        JsonNode doc = JSON.readTree(res.getContentAsString());
        JsonNode paths = doc.get("paths");
        JsonNode esquemas = doc.path("components").path("schemas");

        for (String ruta : List.of("/api/logs", "/api/logs/acciones", "/api/usuarios/tecnicos",
                "/api/usuarios/tecnicos/{idTec}", "/api/usuarios/tecnicos/{idTec}/tiene-reparaciones",
                "/api/auth/cambiar-password")) {
            assertTrue(paths.has(ruta), () -> "falta la ruta " + ruta + " en el contrato");
        }

        JsonNode acciones = paths.path("/api/logs/acciones").path("get").path("responses").path("200")
                .path("content").elements().next().path("schema");
        assertEquals("array", acciones.path("type").asText(), "GET /api/logs/acciones devuelve una lista");
        assertEquals("string", acciones.path("items").path("type").asText(), "…de textos");

        JsonNode limite = parametro(paths, "/api/logs", "get", "limite");
        assertEquals("query", limite.path("in").asText());
        assertFalse(limite.path("required").asBoolean(true), "limite es opcional (el JavaFX no lo manda)");
        assertEquals("integer", limite.path("schema").path("type").asText());

        JsonNode idUsu = parametro(paths, "/api/usuarios/tecnicos/{idTec}", "delete", "idUsu");
        assertFalse(idUsu.path("required").asBoolean(true), "idUsu del DELETE pasa a opcional (se ignora)");

        assertNullable(esquemas, "LogActividad", "detalle", "motivo");
        assertNoNullable(esquemas, "LogActividad", "idLog", "fecha", "nombreUsuario", "accion");

        assertCodigos(paths, "/api/usuarios/tecnicos", "post", "201", "409", "422");
        assertCodigos(paths, "/api/usuarios/tecnicos/{idTec}", "delete", "204", "404", "409");
        assertCodigos(paths, "/api/usuarios/tecnicos/{idTec}/activar", "patch", "204", "404");
        assertCodigos(paths, "/api/usuarios/tecnicos/{idTec}/desactivar", "patch", "204", "404");
        assertCodigos(paths, "/api/auth/cambiar-password", "patch", "204", "422");
    }

    /** Un parámetro (query o path) de una operación, por nombre. */
    private static JsonNode parametro(JsonNode paths, String ruta, String metodo, String nombre) {
        for (JsonNode p : paths.path(ruta).path(metodo).path("parameters")) {
            if (nombre.equals(p.path("name").asText())) return p;
        }
        return fail(metodo + " " + ruta + " no declara el parámetro " + nombre);
    }

    /** Los códigos de respuesta de una operación son exactamente estos (sin el 200 genérico de ResponseEntity<?>). */
    private static void assertCodigos(JsonNode paths, String ruta, String metodo, String... codigos) {
        List<String> declarados = nombres(paths.path(ruta).path(metodo).path("responses"));
        assertEquals(Set.of(codigos), Set.copyOf(declarados), () -> metodo + " " + ruta + " declara " + declarados);
    }
```

(`fail`, `Set` y `List` ya están importados: `Assertions.*` en `:23`, `java.util.Set` en `:20`.)

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=OpenApiContractTest test`
Expected: FAIL 1 de 5: `elContratoPublicaLaGestion` con `LogActividad.detalle debe ser nullable` (la ruta `/acciones`, `limite` e `idUsu` opcional ya los dejaron las Tasks 2 y 4).

- [ ] **Step 3: Nulos de `LogActividad`**

En `LogActividad.java`, sustituir `import java.time.LocalDateTime;` (:3) por:

```java
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
```

y los dos campos (:10-11) por:

```java
    /** TEXT nulable: la mayoría de filas traen motivo null y algunas detalle null (spec 6 §4.6). */
    @Schema(nullable = true) private String detalle;
    @Schema(nullable = true) private String motivo;
```

- [ ] **Step 4: Ejecutar y ver que falla en los códigos**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=OpenApiContractTest test`
Expected: FAIL 1 de 5: `post /api/usuarios/tecnicos declara [200] ==> expected: <[201, 409, 422]> but was: <[200]>` (el orden dentro de los corchetes puede variar: son `Set`).

- [ ] **Step 5: Códigos de respuesta en `UsuarioController` y `AuthController`**

En `UsuarioController.java`, tras `import com.reparaciones.servidor.security.UsuarioPrincipal;` añadir:

```java
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
```

En `registrarTecnico`, entre `@PreAuthorize("hasRole('ADMIN')")` y `public ResponseEntity<?> registrarTecnico(`, añadir:

```java
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content),
        @ApiResponse(responseCode = "422", content = @Content)
    })
```

En `activarTecnico` y en `desactivarTecnico`, entre `@ResponseStatus(HttpStatus.NO_CONTENT)` y `public void …`, añadir en los dos:

```java
    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content)
    })
```

En `eliminarTecnico`, entre `@ResponseStatus(HttpStatus.NO_CONTENT)` y `public void eliminarTecnico(`, añadir:

```java
    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "404", content = @Content),
        @ApiResponse(responseCode = "409", content = @Content)
    })
```

En `AuthController.java`, entre `@PatchMapping("/cambiar-password")` (:58) y `public ResponseEntity<?> cambiarPassword(`, añadir (nombres completos, como el `login` de `:35-40`):

```java
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", content = @io.swagger.v3.oas.annotations.media.Content)
    })
```

- [ ] **Step 6: Ejecutar y ver que pasa**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q -Dtest=OpenApiContractTest test; grep -h "Tests run" target/surefire-reports/*OpenApiContractTest.txt`
Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 7: Suite completa y contrato regenerado**

Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test; grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'`
Expected: sin fallos y `485` (484 + 1).

Run: `ls -la target/openapi.json; grep -c '"/api/logs/acciones"' target/openapi.json; grep -n '"name" : "limite"' target/openapi.json; grep -n '"description" : "Ignorado: el servidor lo resuelve desde idTec"' target/openapi.json; node -e "const d=require('./target/openapi.json');console.log(JSON.stringify(d.components.schemas.LogActividad.properties.motivo), Object.keys(d.paths['/api/usuarios/tecnicos'].post.responses).join(','), Object.keys(d.paths['/api/auth/cambiar-password'].patch.responses).join(','))"`
Expected: `target/openapi.json` con fecha de hoy; `1`; una línea con `"name" : "limite"`; una línea con la descripción de `idUsu`; y `{"type":"string","nullable":true} 201,409,422 204,422`. (`target/` no está en git: este fichero es el que la web copia en la Task 6 para regenerar `schema.d.ts`.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/model/LogActividad.java src/main/java/com/reparaciones/servidor/controller/UsuarioController.java src/main/java/com/reparaciones/servidor/controller/AuthController.java src/test/java/com/reparaciones/servidor/OpenApiContractTest.java
git commit -m "feat(contrato): nulos del log, lista de acciones, límite, idusu opcional y códigos reales de usuarios y contraseña"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 1 (`elContratoPublicaLaGestion`). Total del servidor tras el bloque A: **485** (421 + 64).

---

---

## Task 6: Web — rama, contrato regenerado, alias `Usuario` y `LogActividad`, fecha con segundos, tokens de gestión, candados y `CampoPassword` en el login

**Files:**
- Modify: `api/openapi.json`, `src/shared/api/schema.d.ts` (regenerados por el flujo offline del README, l.16-21)
- Modify: `src/shared/api/client.ts:20-23` (alias nuevos tras `CompraOtro`, l.23)
- Modify: `src/shared/lib/fechas.ts:4` (`Patron`), `:10-11` (constante tras `FMT_FECHA_PEDIDO`), `:13-21` (`FMT` con segundos), `:23-31` (`Partes` y `partesMadrid`), `:45` (regex de `formatear`)
- Modify: `src/shared/lib/fechas.test.ts:2` (import) y `:37` (caso nuevo antes del `})` final, l.38)
- Modify: `src/shared/styles/tokens.css:144` (al final del bloque `@theme`, tras `--color-badge-pendiente-text`)
- Create: `public/Lock.png`, `public/Unlock.png` (binarios del cliente, `hotfix/0.16.3`)
- Create: `src/shared/ui/CampoPassword.tsx`, `src/shared/ui/CampoPassword.test.tsx`
- Modify: `src/app/login/LoginPage.tsx:1-9` (imports y `inputCls`), `:17` (estado `verPassword`, se va), `:31` (texto de vacíos), `:59-76` (campo de contraseña)
- Modify: `src/app/login/LoginPage.test.tsx:36-43` (texto nuevo), `:94-100` (ojo), caso nuevo de usuario con espacios

**Interfaces:**
- Consumes: `target/openapi.json` de `gestion-reparaciones-servidor` en la rama `feature/web-gestion` con las Tasks 1-5 hechas (T5 vuelca el contrato en `OpenApiContractTest`).
- Produces:
  - `export type Usuario = components['schemas']['Usuario']` y `export type LogActividad = components['schemas']['LogActividad']` en `@/shared/api/client`.
  - `@/shared/lib/fechas`: `Patron` gana `'dd/MM/yyyy HH:mm:ss'`; `export const FMT_FECHA_LOG: Patron = 'dd/MM/yyyy HH:mm:ss'`; `formatear` sustituye `ss`.
  - Tokens (utilidades Tailwind v4 desde `@theme`): `bg-fondo-gestion`, `bg-badge-usuario-activo-bg`, `text-badge-usuario-activo-text`, `bg-badge-usuario-inactivo-bg`, `text-badge-usuario-inactivo-text`, `text-error-password`, `text-etiqueta-password`.
  - `/Lock.png` y `/Unlock.png` servidos desde `public/`.
  - `@/shared/ui/CampoPassword`: `export function CampoPassword(props: { valor: string; onChange: (v: string) => void; placeholder: string; 'aria-label': string; autoComplete?: string; autoFocus?: boolean; className?: string; id?: string })`. `className` va al `<input>`; el hueco del ojo (`pr-11`) se aplica después para que `tailwind-merge` no lo pise con un `px-*`.

**Supuestos:** la rama del servidor `feature/web-gestion` existe con T1-T5 commiteadas y `mvn -q -Dtest=OpenApiContractTest test` deja `target/openapi.json` (así lo hace hoy, README de la web l.16-21). T5 marca `detalle` y `motivo` de `LogActividad` como `nullable`, añade `/api/logs/acciones`, el parámetro `limite` de `/api/logs` y deja `idUsu` del `DELETE /api/usuarios/tecnicos/{idTec}` como opcional. El nombre de la operación de `GET /api/logs` (`getAll_6` hoy, `schema.d.ts:1902`) puede cambiar al regenerar: nadie en la web lo usa por nombre.

> Búsqueda previa: `grep -rn "FMT_FECHA_LOG\|HH:mm:ss" src` sale vacío; `export type Usuario` no existe en `client.ts` (solo `Tecnico`, l.14); `ls public` no tiene `Lock.png` ni `Unlock.png` (lista completa: `Badge.png`, `Historial.png`, `NotfON.png`, `NotifOFF.png`, `borrar.png`, `editar.png`, `icono_programa.png`, `logoNavBar.png`, `logo_inicio_sesion.png`, `ojo_activar.png`, `ojo_desactivar.png`, `user.png`); `src/shared/ui/CampoPassword.tsx` no existe; el ojo solo vive en `LoginPage.tsx:59-76`. `git -C /c/Users/dev/Documents/ProgramaReparaciones ls-tree hotfix/0.16.3 gestion-reparaciones-cliente/src/main/resources/images/` lista `Lock.png` (blob `7559daf1957e56798b09e17dbbc55590cafa7777`) y `Unlock.png` (blob `36792e5bc222445b199c29ac602921f0904caad9`), con mayúscula inicial. El texto del login del JavaFX es `"Rellena usuario y contraseña."` (`LoginController.java:98` en `hotfix/0.16.3`, con la misma condición `usuario.trim().isEmpty() || password.isEmpty()`).

- [ ] **Step 1: Rama**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git switch main && git pull --ff-only
git log --oneline -1
git switch -c feature/web-gestion
```

Expected: `cd6853b docs(web): fecha de la 0.7.0` y `Switched to a new branch 'feature/web-gestion'`.

- [ ] **Step 2: Contrato de la rama del servidor**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git switch feature/web-gestion
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q -Dtest=OpenApiContractTest test
cp target/openapi.json ../gestion-reparaciones-web/api/openapi.json
cd ../gestion-reparaciones-web
npm run api:types:offline
git diff --stat api/openapi.json src/shared/api/schema.d.ts
grep -n '"/api/logs/acciones"' src/shared/api/schema.d.ts
grep -n -A10 '^        LogActividad: {' src/shared/api/schema.d.ts | grep -E 'detalle|motivo'
grep -n 'limite?: number' src/shared/api/schema.d.ts
grep -n -A4 '^    eliminarTecnico: {' src/shared/api/schema.d.ts
grep -n -A9 '^        Usuario: {' src/shared/api/schema.d.ts
```

Expected:
- `mvn` termina sin salida (verde).
- El diff solo toca los dos ficheros.
- Una línea con `"/api/logs/acciones": {`.
- `detalle: string | null;` y `motivo: string | null;` (antes `string`, `schema.d.ts:3117-3118`).
- Una línea con `limite?: number;` dentro de la operación de `GET /api/logs`.
- `eliminarTecnico` con `query?: {` y `idUsu?: number;` (antes `query: { idUsu: number }`, `schema.d.ts:6846-6848`). Si sigue obligatorio, la Task 8 lo manda igual (siempre lo manda) y se anota.
- `Usuario` sin cambios: `idUsu: number; nombreUsuario: string; rol: string; idTec: number; nombreTecnico: string; activo: boolean;` (`schema.d.ts:2751-2760`).

Cualquier otro cambio del diff viene de `main` del servidor posterior al último `api:types` (3dccc4a) y se revisa antes de seguir.

- [ ] **Step 3: Alias del contrato**

En `src/shared/api/client.ts`, tras `export type CompraOtro = …` (l.23):

```ts

/** Gestión (sub-proyecto 6): usuarios TECNICO/SUPERTECNICO de GET /api/usuarios/tecnicos (sin ADMIN; `activo` es el de
 *  Tecnico) y filas del log de actividad (`detalle` y `motivo` nullables desde la Task 5 del servidor). */
export type Usuario = components['schemas']['Usuario']
export type LogActividad = components['schemas']['LogActividad']
```

Run: `npx tsc -b`
Expected: sin errores (nadie usa aún `LogActividad`; el cambio a nullable no rompe nada existente).

- [ ] **Step 4: Test del patrón con segundos (falla)**

En `src/shared/lib/fechas.test.ts`, sustituir la l.2 por:

```ts
import { FMT_FECHA_LOG, FMT_FECHA_PEDIDO, fechaLocal, formatear, horaLocal, hoyMadrid, marcaFichero, parsearUtc } from './fechas'
```

y añadir antes del `})` final (l.38):

```ts
  it('patrón de la columna Fecha del log (dd/MM/yyyy HH:mm:ss, LogController FMT :36): segundos en Madrid, sin romper HH:mm', () => {
    expect(FMT_FECHA_LOG).toBe('dd/MM/yyyy HH:mm:ss')
    expect(formatear('2026-08-28T08:42:05', FMT_FECHA_LOG)).toBe('28/08/2026 10:42:05')
    // 23:59:59,987 UTC del 15/01 = 00:59:59 del 16/01 en Madrid (CET): los milisegundos se truncan, no redondean.
    expect(formatear('2026-01-15T23:59:59.987', FMT_FECHA_LOG)).toBe('16/01/2026 00:59:59')
    expect(formatear('2026-08-28T08:42:05', 'HH:mm')).toBe('10:42')
    expect(formatear('2026-08-28T08:42:05', FMT_FECHA_PEDIDO)).toBe('28/08/26 10:42')
  })
```

Run: `npx vitest run src/shared/lib/fechas.test.ts`
Expected: FAIL: `FMT_FECHA_LOG` es `undefined` y `formatear(…, 'dd/MM/yyyy HH:mm:ss')` deja `ss` sin sustituir (`'28/08/2026 10:42:ss'`).

- [ ] **Step 5: Implementar**

En `src/shared/lib/fechas.ts`, la l.4 queda:

```ts
export type Patron =
  | 'yyyy/MM/dd HH:mm'
  | 'yyyy/MM/dd'
  | 'dd/MM HH:mm'
  | 'dd/MM'
  | 'HH:mm'
  | 'dd/MM/yyyy'
  | 'dd/MM/yyyy HH:mm'
  | 'dd/MM/yy HH:mm'
  | 'dd/MM/yyyy HH:mm:ss'
```

Tras `FMT_FECHA_PEDIDO` (l.11):

```ts

/** Patrón de la columna "Fecha" del visor de logs (LogController `FMT` :36): el único del cliente con segundos. */
export const FMT_FECHA_LOG: Patron = 'dd/MM/yyyy HH:mm:ss'
```

`FMT` (l.13-21) gana los segundos:

```ts
const FMT = new Intl.DateTimeFormat('es-ES', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})
```

`Partes` y `partesMadrid` (l.23-31):

```ts
type Partes = Record<'yyyy' | 'yy' | 'MM' | 'dd' | 'HH' | 'mm' | 'ss', string>

function partesMadrid(d: Date): Partes {
  const p: Record<string, string> = {}
  for (const parte of FMT.formatToParts(d)) p[parte.type] = parte.value
  // Algunos motores devuelven "24" a medianoche con hour12: false
  const HH = p.hour === '24' ? '00' : p.hour
  return { yyyy: p.year, yy: p.year.slice(-2), MM: p.month, dd: p.day, HH, mm: p.minute, ss: p.second }
}
```

Y la regex de `formatear` (l.45):

```ts
  return patron.replace(/yyyy|yy|MM|dd|HH|mm|ss/g, (t) => p[t as keyof Partes])
```

Run: `npx vitest run src/shared/lib/fechas.test.ts`
Expected: PASS, 6 tests.

- [ ] **Step 6: Tokens**

En `src/shared/styles/tokens.css`, al final del bloque `@theme` (tras `--color-badge-pendiente-text: #B26A00;`, l.144):

```css

  /* Gestión (sub-proyecto 6). Fondo de RegisterView.fxml (#EFEFEF, el mismo valor que fondo-login: token propio para no
     atar la página de técnicos al login), badge de estado de RegisterController :106-131 (colores a mano, sin constante en
     Colores.java) y el error y las etiquetas de CambiarPasswordView.fxml. */
  --color-fondo-gestion: #EFEFEF;
  --color-badge-usuario-activo-bg: #D4EDDA;
  --color-badge-usuario-activo-text: #2E7D32;
  --color-badge-usuario-inactivo-bg: #F5E6E6;
  --color-badge-usuario-inactivo-text: #B03040;
  --color-error-password: #CC0000;
  --color-etiqueta-password: #555555;
```

- [ ] **Step 7: Candados del cliente**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git ls-tree hotfix/0.16.3 gestion-reparaciones-cliente/src/main/resources/images/ | grep -i 'lock'
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/resources/images/Lock.png > gestion-reparaciones-web/public/Lock.png
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/resources/images/Unlock.png > gestion-reparaciones-web/public/Unlock.png
git -C gestion-reparaciones-web hash-object public/Lock.png public/Unlock.png
```

Expected: el `ls-tree` lista `…/images/Lock.png` y `…/images/Unlock.png`; `hash-object` imprime `7559daf1957e56798b09e17dbbc55590cafa7777` y `36792e5bc222445b199c29ac602921f0904caad9` (los blobs del cliente: la copia es exacta). El nombre lleva mayúscula inicial: en el nginx de producción (Linux) `/lock.png` daría 404.

- [ ] **Step 8: Test de `CampoPassword` (falla)**

`src/shared/ui/CampoPassword.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { CampoPassword } from './CampoPassword'

function Demo({ alCambiar, className }: { alCambiar?: (v: string) => void; className?: string }) {
  const [valor, setValor] = useState('')
  return (
    <CampoPassword
      valor={valor}
      onChange={(v) => { setValor(v); alCambiar?.(v) }}
      placeholder="Nueva contraseña"
      aria-label="Nueva contraseña"
      className={className}
    />
  )
}

/** Calco del par PasswordField/TextField con el botón del ojo de LoginController :70-85 y CambiarPasswordController :41-72. */
describe('CampoPassword', () => {
  it('empieza oculto, con el placeholder y el ojo de mostrar (ojo_activar.png, 18 px)', () => {
    render(<Demo />)
    const campo = screen.getByLabelText('Nueva contraseña')
    expect(campo).toHaveAttribute('type', 'password')
    expect(campo).toHaveAttribute('placeholder', 'Nueva contraseña')
    const ojo = screen.getByRole('button', { name: 'Mostrar contraseña' })
    expect(ojo).toHaveAttribute('type', 'button')
    expect(ojo.querySelector('img')).toHaveAttribute('src', '/ojo_activar.png')
    expect(ojo.querySelector('img')).toHaveClass('h-[18px]', 'w-[18px]')
  })
  it('el ojo alterna type, aria-label e icono y conserva el texto escrito', async () => {
    render(<Demo />)
    const campo = screen.getByLabelText('Nueva contraseña')
    await userEvent.type(campo, 'secreta1')
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(campo).toHaveAttribute('type', 'text')
    expect(campo).toHaveValue('secreta1')
    const ocultar = screen.getByRole('button', { name: 'Ocultar contraseña' })
    expect(ocultar.querySelector('img')).toHaveAttribute('src', '/ojo_desactivar.png')
    await userEvent.click(ocultar)
    expect(campo).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Mostrar contraseña' })).toBeInTheDocument()
  })
  it('llama a onChange con cada tecla y el className del input no se come el hueco del ojo', async () => {
    const alCambiar = vi.fn()
    render(<Demo alCambiar={alCambiar} className="px-3.5 clase-extra" />)
    await userEvent.type(screen.getByLabelText('Nueva contraseña'), 'abc')
    expect(alCambiar).toHaveBeenCalledTimes(3)
    expect(alCambiar).toHaveBeenLastCalledWith('abc')
    expect(screen.getByLabelText('Nueva contraseña')).toHaveClass('clase-extra', 'px-3.5', 'pr-11')
  })
  it('id, autoComplete y autoFocus llegan al input', () => {
    render(<CampoPassword valor="" onChange={() => {}} placeholder="Contraseña actual" aria-label="Contraseña actual" id="campo-actual" autoComplete="current-password" autoFocus />)
    const campo = screen.getByLabelText('Contraseña actual')
    expect(campo).toHaveAttribute('id', 'campo-actual')
    expect(campo).toHaveAttribute('autocomplete', 'current-password')
    expect(campo).toHaveFocus()
  })
})
```

Run: `npx vitest run src/shared/ui/CampoPassword.test.tsx`
Expected: FAIL, `Failed to resolve import "./CampoPassword"`.

- [ ] **Step 9: Implementar `CampoPassword`**

`src/shared/ui/CampoPassword.tsx`:

```tsx
import { useState } from 'react'
import { cn } from '@/shared/lib/utils'
import { Input } from './input'

type Props = {
  valor: string
  onChange: (v: string) => void
  placeholder: string
  'aria-label': string
  autoComplete?: string
  autoFocus?: boolean
  /** Clases del <input> (borde, radio, padding, colores de cada pantalla). */
  className?: string
  id?: string
}

/** Campo de contraseña con el botón del ojo (spec 6, G11): calco del par PasswordField/TextField del login
 *  (LoginController :70-85) y de los tres campos de CambiarPasswordView.fxml. Cada instancia guarda su propia
 *  visibilidad. `pr-11` va detrás de `className` para que un `px-*` de la pantalla no lo pise en tailwind-merge (el hueco
 *  del ojo, padding derecho 44 del FXML). */
export function CampoPassword({ valor, onChange, placeholder, 'aria-label': etiqueta, autoComplete, autoFocus, className, id }: Props) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative w-full">
      <Input
        id={id}
        type={visible ? 'text' : 'password'}
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={etiqueta}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        className={cn(className, 'pr-11')}
      />
      <button
        type="button"
        aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        onClick={() => setVisible((v) => !v)}
        className="absolute top-1/2 right-3 -translate-y-1/2 cursor-pointer"
      >
        <img src={visible ? '/ojo_desactivar.png' : '/ojo_activar.png'} alt="" className="h-[18px] w-[18px]" />
      </button>
    </div>
  )
}
```

Run: `npx vitest run src/shared/ui/CampoPassword.test.tsx`
Expected: PASS, 4 tests.

- [ ] **Step 10: Test del login con el texto del JavaFX (falla)**

En `src/app/login/LoginPage.test.tsx`, el caso de la l.36-43 queda:

```tsx
  it('con campos vacíos no llama a la API y avisa con el texto del JavaFX', async () => {
    let llamadas = 0
    server.use(http.post('*/api/auth/login', () => { llamadas++; return HttpResponse.json(respuestaLogin) }))
    montar()
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar Sesión' }))
    expect(llamadas).toBe(0)
    expect(screen.getByText('Rellena usuario y contraseña.')).toBeInTheDocument()
  })
  it('un usuario de solo espacios cuenta como vacío (trim) y no llama a la API', async () => {
    let llamadas = 0
    server.use(http.post('*/api/auth/login', () => { llamadas++; return HttpResponse.json(respuestaLogin) }))
    montar()
    await userEvent.type(screen.getByPlaceholderText('Usuario'), '   ')
    await userEvent.type(screen.getByPlaceholderText('Contraseña'), 'secreta1{enter}')
    expect(llamadas).toBe(0)
    expect(screen.getByText('Rellena usuario y contraseña.')).toBeInTheDocument()
  })
```

Y el caso del ojo (l.94-100):

```tsx
  it('el ojo (CampoPassword) alterna la visibilidad de la contraseña y su icono', async () => {
    montar()
    const pass = screen.getByPlaceholderText('Contraseña')
    expect(pass).toHaveAttribute('type', 'password')
    expect(pass).toHaveClass('pr-11')
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(pass).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' }).querySelector('img')).toHaveAttribute('src', '/ojo_desactivar.png')
  })
```

Run: `npx vitest run src/app/login/LoginPage.test.tsx`
Expected: FAIL en los dos primeros (`Unable to find an element with the text: Rellena usuario y contraseña.`: hoy dice "Introduce usuario y contraseña.") y en el del ojo (`pr-11` no está: `tailwind-merge` lo quita al venir antes que el `px-3.5` de `inputCls`).

- [ ] **Step 11: Implementar en el login**

En `src/app/login/LoginPage.tsx`:

Imports (l.1-6), añadiendo `CampoPassword`:

```tsx
import { useEffect, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '@/shared/ui/button'
import { CampoPassword } from '@/shared/ui/CampoPassword'
import { Input } from '@/shared/ui/input'
import { APP_VERSION } from '@/shared/lib/version'
import { useSession } from '@/shared/session/SessionProvider'
```

Borrar la l.17 (`const [verPassword, setVerPassword] = useState(false)`).

La l.31 queda:

```tsx
      setError('Rellena usuario y contraseña.')
```

Y el bloque de la contraseña (l.59-76) queda:

```tsx
        <div className="mb-1.5 w-full">
          <CampoPassword
            valor={password}
            onChange={setPassword}
            placeholder="Contraseña"
            aria-label="Contraseña"
            autoComplete="current-password"
            className={inputCls}
          />
        </div>
```

Run: `npx vitest run src/app/login/LoginPage.test.tsx src/shared/ui/CampoPassword.test.tsx`
Expected: PASS, 8 + 4 tests.

- [ ] **Step 12: Comprobar**

```bash
npm run check
```

Expected: lint y typecheck en verde; **1339 tests** (1333 de v0.7.0 + 1 de `fechas` + 4 de `CampoPassword` + 1 de `LoginPage`).

- [ ] **Step 13: Commit**

```bash
git add api/openapi.json src/shared/api/schema.d.ts src/shared/api/client.ts src/shared/lib/fechas.ts src/shared/lib/fechas.test.ts src/shared/styles/tokens.css public/Lock.png public/Unlock.png src/shared/ui/CampoPassword.tsx src/shared/ui/CampoPassword.test.tsx src/app/login/LoginPage.tsx src/app/login/LoginPage.test.tsx
git commit -m "chore(web): contrato de gestion, alias de usuario y log, fecha con segundos, tokens y candados de gestion, y campo de contraseña compartido con el texto del login del javafx"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

---

## Task 7: Web — `RequiereAdmin`, `rutaVolverA`, `useUsuariosTecnicos`, rutas de gestión bajo la guarda y `volverA` en el menú de usuario

**Files:**
- Create: `src/modules/gestion/rutas.tsx`, `src/modules/gestion/rutas.test.tsx`
- Create: `src/modules/gestion/navegacion.ts`, `src/modules/gestion/navegacion.test.ts`
- Create: `src/modules/gestion/api.ts`, `src/modules/gestion/api.test.tsx`
- Modify: `src/app/router.tsx:14` (import) y `:77-79` (rutas de gestión y `/cuenta/cambiar-password`)
- Modify: `src/app/shell/UserMenu.tsx:1` (import), `:8-9` (`useLocation`), `:21-22` (navegaciones con `volverA`), `:27` ("Cambiar contraseña" sin navegación)
- Modify: `src/app/shell/TopBar.test.tsx:4` (import de `useLocation`), `Destino` tras los imports y casos nuevos tras el caso `'el admin ve además "Gestionar técnicos" y "Ver logs"'`

**Interfaces:**
- Consumes: `Usuario` de `@/shared/api/client` (Task 6); `MSG_SIN_PERMISOS` de `@/shared/api/errors` (`errors.ts:29`); `esAdmin` de `@/shared/session/storage`; `useAlerta` de `@/shared/ui/AlertaProvider`; `PendienteDeMigrar` de `app/shell/PendienteDeMigrar.tsx`.
- Produces:
  - `@/modules/gestion/rutas`: `export function RequiereAdmin(): JSX.Element` (ADMIN → `<Outlet />`; resto → `mostrarError(MSG_SIN_PERMISOS)` en efecto + `<Navigate to="/reparaciones" replace />`).
  - `modules/gestion/navegacion.ts`: `export const RUTA_VOLVER_POR_DEFECTO = '/reparaciones'`; `export function rutaVolverA(state: unknown): string`.
  - `modules/gestion/api.ts`: `CLAVE_USUARIOS = ['usuarios'] as const`, `CLAVE_USUARIOS_TECNICOS = ['usuarios', 'tecnicos'] as const`, `CLAVE_TECNICOS_LITERAL = ['tecnicos'] as const`, `useUsuariosTecnicos(opciones?: { habilitada?: boolean }): UseQueryResult<Usuario[]>` (siempre `meta: { silenciarError: true }` y `refetchOnWindowFocus: false`: la página de técnicos pinta su error inline y el filtro de logs lo calla, calco).
  - Router: `/gestion/tecnicos` y `/gestion/logs` bajo `<RequiereAdmin />`, todavía con `PendienteDeMigrar` (T9 y T11 los sustituyen); `/cuenta/cambiar-password` desaparece.
  - `UserMenu`: "Gestionar técnicos" y "Ver logs" navegan con `state: { volverA: pathname }`; "Cambiar contraseña" queda con `onSelect` vacío (T12 lo conecta a `CambiarPasswordDialog` y actualiza el test "no navega" de `TopBar.test.tsx` para comprobar que abre el diálogo).

**Supuestos:** `renderConRouter` (`src/test/render.tsx:53-70`) monta un data router en memoria y devuelve `router`; `renderConProviders` acepta `rutas` hermanas (l.10, l.39). `AppLayout` con `SESION_ADMIN` en `/stock/proveedores` no hace peticiones (el test de la l.30-37 lo monta así con `SESION_TEC` sin handlers; la campana y el badge de Asignaciones son solo del supertécnico).

> Búsqueda previa: `grep -rn "RequiereAdmin\|volverA\|useUsuariosTecnicos\|'usuarios'" src` sale vacío. El patrón de guarda es `RequiereSupertecnicoOAdmin` (`modules/taller/rutas.tsx:46-55`) y su test (`rutas.test.tsx:160-179`), que no se pueden importar desde `gestion` (regla de módulos, `eslint.config.js`): se copian. `CLAVE_TECNICOS = ['tecnicos']` vive en `modules/taller/api.ts:18` y cubre `['tecnicos','activos']` (l.21) por prefijo: aquí va como literal. `TopBar.test.tsx` no comprueba hoy ninguna navegación del menú (l.38-52 solo mira qué ítems salen).

- [ ] **Step 1: Test de `RequiereAdmin` (falla)**

`src/modules/gestion/rutas.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderConRouter, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { RequiereAdmin } from './rutas'

const rutas = [
  {
    element: <RequiereAdmin />,
    children: [
      { path: '/gestion/tecnicos', element: <p>TECNICOS</p> },
      { path: '/gestion/logs', element: <p>LOGS</p> },
    ],
  },
  { path: '/reparaciones', element: <p>INICIO POR ROL</p> },
]

/** "Gestionar técnicos" y "Ver logs" son solo del ADMIN (MainController :826-832; el servidor responde 403 al resto). */
describe('RequiereAdmin', () => {
  it('el ADMIN entra en /gestion/tecnicos y en /gestion/logs sin aviso', async () => {
    const tecnicos = renderConRouter(rutas, { sesion: SESION_ADMIN, ruta: '/gestion/tecnicos' })
    expect(await screen.findByText('TECNICOS')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Error' })).not.toBeInTheDocument()
    tecnicos.unmount()

    renderConRouter(rutas, { sesion: SESION_ADMIN, ruta: '/gestion/logs' })
    expect(await screen.findByText('LOGS')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Error' })).not.toBeInTheDocument()
  })
  it.each([
    ['TECNICO', SESION_TEC, '/gestion/tecnicos'],
    ['SUPERTECNICO', SESION_SUPER, '/gestion/logs'],
  ])('%s por URL recibe el aviso genérico de permisos y sale a /reparaciones', async (_rol, sesion, ruta) => {
    const { router } = renderConRouter(rutas, { sesion, ruta })
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('No tienes permisos para realizar esta acción.')
    expect(router.state.location.pathname).toBe('/reparaciones')
    expect(screen.queryByText('TECNICOS')).not.toBeInTheDocument()
    expect(screen.queryByText('LOGS')).not.toBeInTheDocument()
  })
})
```

Run: `npx vitest run src/modules/gestion/rutas.test.tsx`
Expected: FAIL, `Failed to resolve import "./rutas"`.

- [ ] **Step 2: Implementar `RequiereAdmin`**

`src/modules/gestion/rutas.tsx`:

```tsx
import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router'
import { MSG_SIN_PERMISOS } from '@/shared/api/errors'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin } from '@/shared/session/storage'
import { useAlerta } from '@/shared/ui/AlertaProvider'

/** "Gestionar técnicos" y "Ver logs" (spec 6, §6.4 y Roles): solo ADMIN. TECNICO y SUPERTECNICO no tienen las entradas del
 *  menú, así que solo llegan por URL: reciben el aviso genérico y salen a /reparaciones, que ya reparte por rol. Mismo
 *  patrón que RequiereSupertecnicoOAdmin (modules/taller/rutas.tsx), copiado porque un módulo no importa de otro. */
export function RequiereAdmin() {
  const { sesion } = useSession()
  const { mostrarError } = useAlerta()
  const permitido = esAdmin(sesion)
  useEffect(() => {
    if (!permitido) mostrarError(MSG_SIN_PERMISOS)
  }, [permitido, mostrarError])
  if (!permitido) return <Navigate to="/reparaciones" replace />
  return <Outlet />
}
```

Run: `npx vitest run src/modules/gestion/rutas.test.tsx`
Expected: PASS, 3 tests.

- [ ] **Step 3: Test de `rutaVolverA` (falla)**

`src/modules/gestion/navegacion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { RUTA_VOLVER_POR_DEFECTO, rutaVolverA } from './navegacion'

/** "Cerrar" de técnicos y logs vuelve a la vista desde la que el menú abrió la página (spec 6, §6.1 Pie). */
describe('rutaVolverA', () => {
  it('devuelve el volverA del state cuando es una ruta de la app', () => {
    expect(rutaVolverA({ volverA: '/stock/pedidos' })).toBe('/stock/pedidos')
    expect(rutaVolverA({ volverA: '/reparaciones/historial', otra: 1 })).toBe('/reparaciones/historial')
  })
  it('sin state, sin volverA o con algo que no es una ruta interna → /reparaciones', () => {
    expect(RUTA_VOLVER_POR_DEFECTO).toBe('/reparaciones')
    for (const state of [null, undefined, 'x', 7, {}, { volverA: 3 }, { volverA: '' }, { volverA: 'stock' }, { volverA: 'https://ejemplo.invalid/' }, { volverA: '//ejemplo.invalid' }]) {
      expect(rutaVolverA(state)).toBe('/reparaciones')
    }
  })
})
```

Run: `npx vitest run src/modules/gestion/navegacion.test.ts`
Expected: FAIL, `Failed to resolve import "./navegacion"`.

- [ ] **Step 4: Implementar `rutaVolverA`**

`src/modules/gestion/navegacion.ts`:

```ts
/** Destino de "Cerrar" cuando la página no se abrió desde el menú (URL tecleada, recarga): el inicio por rol. */
export const RUTA_VOLVER_POR_DEFECTO = '/reparaciones'

/** Ruta a la que vuelve "Cerrar" en /gestion/tecnicos y /gestion/logs: el `volverA` que el menú de usuario deja en el
 *  state de la navegación. Solo rutas internas ('/…', no '//…'): el state viene del historial del navegador. */
export function rutaVolverA(state: unknown): string {
  if (state !== null && typeof state === 'object' && 'volverA' in state) {
    const v = (state as { volverA?: unknown }).volverA
    if (typeof v === 'string' && v.startsWith('/') && !v.startsWith('//')) return v
  }
  return RUTA_VOLVER_POR_DEFECTO
}
```

Run: `npx vitest run src/modules/gestion/navegacion.test.ts`
Expected: PASS, 2 tests.

- [ ] **Step 5: Test de `useUsuariosTecnicos` (falla)**

`src/modules/gestion/api.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import type { Usuario } from '@/shared/api/client'
import { crearQueryClient } from '@/shared/api/queryClient'
import { onError } from '@/shared/ui/alertas'
import { server } from '@/test/server'
import { CLAVE_TECNICOS_LITERAL, CLAVE_USUARIOS, CLAVE_USUARIOS_TECNICOS, useUsuariosTecnicos } from './api'

const usuarios: Usuario[] = [
  { idUsu: 11, nombreUsuario: 'usuario-a', rol: 'TECNICO', idTec: 21, nombreTecnico: 'tecnico-a', activo: true },
  { idUsu: 12, nombreUsuario: 'usuario-b', rol: 'SUPERTECNICO', idTec: 22, nombreTecnico: 'tecnico-b', activo: false },
]

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}

describe('api de gestión', () => {
  it('claves: usuarios, usuarios/tecnicos (colgada de usuarios) y el literal de técnicos del taller', () => {
    expect(CLAVE_USUARIOS).toEqual(['usuarios'])
    expect(CLAVE_USUARIOS_TECNICOS).toEqual(['usuarios', 'tecnicos'])
    expect(CLAVE_TECNICOS_LITERAL).toEqual(['tecnicos'])
  })
  it('useUsuariosTecnicos lee GET /api/usuarios/tecnicos en el orden del servidor y no recarga por foco', async () => {
    let peticiones = 0
    server.use(http.get('*/api/usuarios/tecnicos', () => { peticiones += 1; return HttpResponse.json(usuarios) }))
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useUsuariosTecnicos(), { wrapper })
    await waitFor(() => expect(result.current.data).toEqual(usuarios))
    expect(peticiones).toBe(1)
    // `Query.options` es `QueryOptions` (sin las opciones de observer: tsc -b fallaría con TS2339); se lee del observer.
    expect(qc.getQueryCache().find({ queryKey: CLAVE_USUARIOS_TECNICOS })?.observers[0]?.options.refetchOnWindowFocus).toBe(false)
  })
  it('con habilitada: false no pide nada', () => {
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useUsuariosTecnicos({ habilitada: false }), { wrapper })
    expect(result.current.fetchStatus).toBe('idle')
    expect(result.current.data).toBeUndefined()
  })
  it('un fallo de carga no abre el diálogo global: lo pinta la página de técnicos o lo calla el filtro de logs', async () => {
    const avisos = vi.fn()
    const quitar = onError(avisos)
    server.use(http.get('*/api/usuarios/tecnicos', () => HttpResponse.json({ message: 'Fallo de prueba' }, { status: 400 })))
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useUsuariosTecnicos(), { wrapper })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(avisos).not.toHaveBeenCalled()
    quitar()
  })
})
```

Run: `npx vitest run src/modules/gestion/api.test.tsx`
Expected: FAIL, `Failed to resolve import "./api"`.

- [ ] **Step 6: Implementar `api.ts` de gestión**

`src/modules/gestion/api.ts`:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type Usuario } from '@/shared/api/client'

/** Prefijo de todo lo de usuarios: invalidarlo recarga la lista de técnicos de esta página y el filtro "Técnico..." de logs. */
export const CLAVE_USUARIOS = ['usuarios'] as const
export const CLAVE_USUARIOS_TECNICOS = ['usuarios', 'tecnicos'] as const
/** La CLAVE_TECNICOS de modules/taller/api.ts (combos y listas de técnicos del resto de la web; cubre ['tecnicos','activos']
 *  por prefijo). Literal porque un módulo no importa de otro, como ['notificaciones'] en el 4b. */
export const CLAVE_TECNICOS_LITERAL = ['tecnicos'] as const

/** GET /api/usuarios/tecnicos (ADMIN): TECNICO y SUPERTECNICO, sin ADMIN, ordenados por nombre de técnico en el servidor.
 *  Sin diálogo global de error (la página de técnicos pinta "Error al cargar los usuarios." en su línea y el filtro de logs
 *  falla en silencio, calco) y sin recarga por foco ni sondeo (spec 6, §7). */
export function useUsuariosTecnicos(opciones: { habilitada?: boolean } = {}): UseQueryResult<Usuario[]> {
  return useQuery({
    queryKey: CLAVE_USUARIOS_TECNICOS,
    queryFn: async () => (await api.GET('/api/usuarios/tecnicos')).data ?? [],
    enabled: opciones.habilitada ?? true,
    meta: { silenciarError: true },
    refetchOnWindowFocus: false,
  })
}
```

Run: `npx vitest run src/modules/gestion/api.test.tsx`
Expected: PASS, 4 tests.

- [ ] **Step 7: Tests del menú de usuario (fallan)**

En `src/app/shell/TopBar.test.tsx`, la l.4 queda:

```tsx
import { Route, useLocation } from 'react-router'
```

Tras la última importación (hoy la l.10) se añade:

```tsx

/** Destino de prueba que enseña el state con el que se llegó (el `volverA` del menú de usuario). */
function Destino({ texto }: { texto: string }) {
  const { state } = useLocation()
  return <p>{`${texto} ${JSON.stringify(state)}`}</p>
}
```

Y tras el cierre (`})`) del caso `'el admin ve además "Gestionar técnicos" y "Ver logs"'` (se busca por el texto: tras añadir `Destino` ya no está en la l.47-52, y con el número de línea el bloque cae dentro del test del técnico):

```tsx
  it.each([
    ['Gestionar técnicos', '/gestion/tecnicos', 'TECNICOS'],
    ['Ver logs', '/gestion/logs', 'LOGS'],
  ])('"%s" navega a %s con volverA = la ruta desde la que se abrió', async (item, ruta, texto) => {
    renderConProviders(<AppLayout />, { sesion: SESION_ADMIN, ruta: '/stock/proveedores', rutas: <Route path={ruta} element={<Destino texto={texto} />} /> })
    await userEvent.click(screen.getByRole('button', { name: /Hola, admin/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: item }))
    expect(await screen.findByText(`${texto} {"volverA":"/stock/proveedores"}`)).toBeInTheDocument()
  })
  it('"Cambiar contraseña" ya no navega a /cuenta/cambiar-password (la ruta desaparece; el diálogo lo abre en el sitio)', async () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC, ruta: '/stock/proveedores', rutas: <Route path="/cuenta/cambiar-password" element={<p>CUENTA</p>} /> })
    await userEvent.click(screen.getByRole('button', { name: /Hola, tecnico_n/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar contraseña' }))
    expect(screen.queryByText('CUENTA')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Hola, tecnico_n/ })).toBeInTheDocument()
  })
```

(La Task 12, Step 13, sustituye la última aserción de este tercer caso por la del diálogo abierto: con el diálogo modal la barra queda `aria-hidden` y `getByRole('button', { name: /Hola, tecnico_n/ })` ya no la encuentra.)

Run: `npx vitest run src/app/shell/TopBar.test.tsx`
Expected: FAIL en los tres nuevos: los dos primeros encuentran `TECNICOS null` / `LOGS null` (se navega sin state) y el tercero encuentra `CUENTA`.

- [ ] **Step 8: Implementar en `UserMenu` y en el router**

`src/app/shell/UserMenu.tsx`, l.1:

```tsx
import { useLocation, useNavigate } from 'react-router'
```

Tras `const navigate = useNavigate()` (l.9):

```tsx
  const { pathname } = useLocation()
```

Las l.21-22:

```tsx
            <DropdownMenuItem onSelect={() => navigate('/gestion/tecnicos', { state: { volverA: pathname } })}>Gestionar técnicos</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => navigate('/gestion/logs', { state: { volverA: pathname } })}>Ver logs</DropdownMenuItem>
```

La l.27:

```tsx
        <DropdownMenuItem onSelect={() => { /* Task 12: abre CambiarPasswordDialog en el sitio (spec 6, G6) */ }}>Cambiar contraseña</DropdownMenuItem>
```

`src/app/router.tsx`, tras `import { ClientesPage } from '@/modules/gestion/clientes/ClientesPage'` (l.5, junto a los demás imports de `@/modules/gestion`):

```tsx
import { RequiereAdmin } from '@/modules/gestion/rutas'
```

Las tres rutas `PendienteDeMigrar` de gestión y cuenta (`/gestion/tecnicos`, `/gestion/logs` y `/cuenta/cambiar-password`; hoy l.77-79, l.78-80 tras el import de arriba) se sustituyen por:

```tsx
          {
            // Solo ADMIN (spec 6): TECNICO y SUPERTECNICO por URL reciben el aviso genérico y vuelven a /reparaciones.
            element: <RequiereAdmin />,
            children: [
              { path: '/gestion/tecnicos', element: <PendienteDeMigrar nombre="Gestionar técnicos" /> },
              { path: '/gestion/logs', element: <PendienteDeMigrar nombre="Ver logs" /> },
            ],
          },
```

(`/cuenta/cambiar-password` desaparece: "Cambiar contraseña" será un diálogo sobre la vista actual, G6; la URL vieja cae en el `*` de la l.81 y vuelve a `/`.)

Run: `npx vitest run src/app/shell src/modules/gestion`
Expected: PASS (los 3 nuevos de `TopBar.test.tsx` y los 9 de `modules/gestion`; ninguno de los anteriores roto).

- [ ] **Step 9: Comprobar**

```bash
npm run check
```

Expected: lint y typecheck en verde; **1351 tests** (1339 + 3 de `rutas` + 2 de `navegacion` + 4 de `api` + 3 de `TopBar`).

- [ ] **Step 10: Commit**

```bash
git add src/modules/gestion/rutas.tsx src/modules/gestion/rutas.test.tsx src/modules/gestion/navegacion.ts src/modules/gestion/navegacion.test.ts src/modules/gestion/api.ts src/modules/gestion/api.test.tsx src/app/router.tsx src/app/shell/UserMenu.tsx src/app/shell/TopBar.test.tsx
git commit -m "feat(gestion): guarda solo admin en tecnicos y logs, vuelta a la vista de origen, lista de usuarios tecnicos y fuera la ruta de cambiar contraseña"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

---

## Task 8: Web — validación, textos, errores inline, badge, columnas y `api.ts` de técnicos

**Files:**
- Create: `src/modules/gestion/tecnicos/validacion.ts`, `src/modules/gestion/tecnicos/validacion.test.ts`
- Create: `src/modules/gestion/tecnicos/textos.ts`
- Create: `src/modules/gestion/tecnicos/errores.ts`, `src/modules/gestion/tecnicos/errores.test.ts`
- Create: `src/modules/gestion/tecnicos/BadgeEstadoUsuario.tsx`
- Create: `src/modules/gestion/tecnicos/columnas.tsx`, `src/modules/gestion/tecnicos/columnas.test.tsx`
- Create: `src/modules/gestion/tecnicos/api.ts`, `src/modules/gestion/tecnicos/api.test.tsx`

**Interfaces:**
- Consumes: `Usuario`, `api` de `@/shared/api/client` (Task 6); `CLAVE_USUARIOS`, `CLAVE_TECNICOS_LITERAL` de `../api` (Task 7); `NoEncontradoError`, `StaleDataError`, `ReglaNegocioError` de `@/shared/api/errors` (`errors.ts:12-16`); `DataTable` (solo en el test); tokens `badge-usuario-*` (Task 6); `/Lock.png`, `/Unlock.png` (Task 6), `/borrar.png` (ya en `public/`).
- Produces (firmas del documento de interfaces del reparto, con los añadidos marcados en Desviaciones):

```ts
// validacion.ts
export const ROLES = ['TECNICO', 'SUPERTECNICO'] as const
export type Rol = (typeof ROLES)[number]
export function rolDe(valor: string): Rol                          // añadido: el valor del ComboNavy es string
export type DatosAlta = { nombreTecnico: string; nombreUsuario: string; password: string; confirmar: string; rol: Rol }
export type CuerpoAlta = { nombreTecnico: string; nombreUsuario: string; password: string; rol: Rol }   // = ReturnType<typeof cuerpoAlta>
export const MSG_CAMPOS, MSG_NO_COINCIDEN, MSG_PASSWORD_CORTA, MSG_TECNICO_DUPLICADO, MSG_USUARIO_DUPLICADO
export function validarAlta(d: DatosAlta): string | null
export function cuerpoAlta(d: DatosAlta): CuerpoAlta
export function duplicadosEnVivo(usuarios: Usuario[], nombreTecnico: string, nombreUsuario: string): { tecnico: string | null; usuario: string | null }
// textos.ts
export const MSG_ERROR_CARGA, MSG_ERROR_REGISTRO, MSG_ERROR_ESTADO, MSG_ERROR_ELIMINAR, MSG_ERROR_COMPROBAR, TITULO_NO_ELIMINAR,
  TITULO_ELIMINAR, TEXTO_VACIO_TABLA, TOOLTIP_DESACTIVAR, TOOLTIP_ACTIVAR
export const MSG_TECNICO_NO_ENCONTRADO = 'Técnico no encontrado.'   // añadido: el 404 del servidor (ver errores.ts)
export const textoNoEliminar: (n: string) => string
export const textoEliminar: (n: string) => string
// errores.ts (añadido)
export type CodigoConMensaje = 404 | 409 | 422
export function mensajeInline(e: unknown, fijo: string, conMensaje: readonly CodigoConMensaje[]): string
// BadgeEstadoUsuario.tsx
export function BadgeEstadoUsuario({ activo }: { activo: boolean }): JSX.Element
// columnas.tsx
export const ANCHOS_TECNICOS = { tecnico: 160, usuario: 130, rol: 110, estado: 90, acciones: 80 } as const
export function columnasTecnicos(acciones: { onToggle: (u: Usuario) => void; onEliminar: (u: Usuario) => void }): ColumnDef<Usuario>[]
// api.ts
export function useRegistrar(): UseMutationResult<void, Error, CuerpoAlta>
export function useCambiarActivo(): UseMutationResult<void, Error, { idTec: number; activar: boolean }>
export function useEliminar(): UseMutationResult<void, Error, { idTec: number; idUsu: number }>
export async function consultarTieneReparaciones(idTec: number): Promise<boolean>
```

**Supuestos:** tras la Task 6 el contrato tiene `POST /api/usuarios/tecnicos` con cuerpo `UsuarioRegistrarTecnicoRequest` (`nombreTecnico, nombreUsuario, password, rol`, `schema.d.ts:2272`), `PATCH …/{idTec}/activar` y `…/desactivar` (l.711 y l.695), `GET …/{idTec}/tiene-reparaciones` con respuesta `{[key: string]: boolean}` (l.1335, operación l.5801) y `DELETE …/{idTec}` con `idUsu` en query (l.2071, operación l.6844; opcional desde T2). `clasificar` (`errors.ts:52-53`) descarta el `message` de un 404 (siempre "Recurso no encontrado."): por eso el 404 se traduce aquí al texto del servidor (`ValidacionUsuarios.MSG_NO_ENCONTRADO` de la Task 1-2, "Técnico no encontrado."). `DataTable` pone `data-columna={column.id}` en cada celda (`DataTable.tsx:343`) y un `<col>` por columna con su `size` (l.391).

> Búsqueda previa: `src/modules/gestion/tecnicos/` no existe. No hay `BotonPapelera` en `shared/ui/Botones.tsx` (solo `BotonPrimario`/`BotonSecundario`); el de `modules/taller/componentes/BotonPapelera.tsx` es de otro módulo y mide 25 px: la papelera de 22 px va en la propia columna. `StatusBadge` (`shared/ui/StatusBadge.tsx`) tiene los colores de Clientes, no los de esta tabla: badge propio. Textos del JavaFX: `RegisterController.java` (`hotfix/0.16.3`) :193-195, :209-211, :223-247, :262-275, :281-284; en vivo :67-81.

- [ ] **Step 1: Test de la validación (falla)**

`src/modules/gestion/tecnicos/validacion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { Usuario } from '@/shared/api/client'
import {
  cuerpoAlta, duplicadosEnVivo, MSG_CAMPOS, MSG_NO_COINCIDEN, MSG_PASSWORD_CORTA, MSG_TECNICO_DUPLICADO, MSG_USUARIO_DUPLICADO, rolDe, ROLES,
  validarAlta, type DatosAlta,
} from './validacion'

const datos = (o: Partial<DatosAlta> = {}): DatosAlta => ({
  nombreTecnico: 'tecnico-c', nombreUsuario: 'usuario-c', password: 'secreta1', confirmar: 'secreta1', rol: 'TECNICO', ...o,
})
const lista: Usuario[] = [
  { idUsu: 11, nombreUsuario: 'usuario-a', rol: 'TECNICO', idTec: 21, nombreTecnico: 'tecnico-a', activo: true },
  { idUsu: 12, nombreUsuario: 'usuario-b', rol: 'SUPERTECNICO', idTec: 22, nombreTecnico: 'tecnico-b', activo: false },
]

/** Calco de RegisterController.registrar (:255-285) y validarNombresEnVivo (:67-81) de hotfix/0.16.3. */
describe('validación del alta de técnicos', () => {
  it('textos exactos del JavaFX, roles del combo en mayúsculas y sin ADMIN', () => {
    expect(MSG_CAMPOS).toBe('Todos los campos son obligatorios.')
    expect(MSG_NO_COINCIDEN).toBe('Las contraseñas no coinciden.')
    expect(MSG_PASSWORD_CORTA).toBe('La contraseña debe tener al menos 6 caracteres.')
    expect(MSG_TECNICO_DUPLICADO).toBe('Ya existe un técnico con ese nombre.')
    expect(MSG_USUARIO_DUPLICADO).toBe('Ese nombre de usuario ya existe.')
    expect(ROLES).toEqual(['TECNICO', 'SUPERTECNICO'])
    expect(rolDe('SUPERTECNICO')).toBe('SUPERTECNICO')
    expect(rolDe('TECNICO')).toBe('TECNICO')
    expect(rolDe('ADMIN')).toBe('TECNICO')
  })
  it('vacíos (nombres con trim, contraseña sin trim) → "Todos los campos son obligatorios.", antes que el resto', () => {
    expect(validarAlta(datos({ nombreTecnico: '' }))).toBe(MSG_CAMPOS)
    expect(validarAlta(datos({ nombreTecnico: '   ' }))).toBe(MSG_CAMPOS)
    expect(validarAlta(datos({ nombreUsuario: ' ' }))).toBe(MSG_CAMPOS)
    expect(validarAlta(datos({ password: '', confirmar: '' }))).toBe(MSG_CAMPOS)
    expect(validarAlta(datos({ nombreTecnico: '', password: 'abc', confirmar: 'xyz' }))).toBe(MSG_CAMPOS)
  })
  it('la confirmación vacía no cuenta como campo obligatorio: cae en "no coinciden"', () => {
    expect(validarAlta(datos({ confirmar: '' }))).toBe(MSG_NO_COINCIDEN)
  })
  it('"no coinciden" va antes que "menos de 6" y compara sin trim', () => {
    expect(validarAlta(datos({ password: 'abc', confirmar: 'abd' }))).toBe(MSG_NO_COINCIDEN)
    expect(validarAlta(datos({ password: 'secreta1', confirmar: 'secreta1 ' }))).toBe(MSG_NO_COINCIDEN)
  })
  it('menos de 6 caracteres, contando los espacios; con todo bien → null', () => {
    expect(validarAlta(datos({ password: '12345', confirmar: '12345' }))).toBe(MSG_PASSWORD_CORTA)
    expect(validarAlta(datos({ password: '   ', confirmar: '   ' }))).toBe(MSG_PASSWORD_CORTA)
    expect(validarAlta(datos({ password: '12345 ', confirmar: '12345 ' }))).toBeNull()
    expect(validarAlta(datos())).toBeNull()
  })
  it('cuerpoAlta recorta los dos nombres, deja contraseña y rol tal cual y no manda la confirmación', () => {
    expect(cuerpoAlta(datos({ nombreTecnico: '  tecnico-c ', nombreUsuario: ' usuario-c  ', password: ' secreta1 ', confirmar: ' secreta1 ', rol: 'SUPERTECNICO' }))).toEqual({
      nombreTecnico: 'tecnico-c', nombreUsuario: 'usuario-c', password: ' secreta1 ', rol: 'SUPERTECNICO',
    })
  })
  it('duplicadosEnVivo: sin mayúsculas y con trim a los dos lados, cada campo contra su columna; vacío o ADMIN → null', () => {
    expect(duplicadosEnVivo(lista, '', '')).toEqual({ tecnico: null, usuario: null })
    expect(duplicadosEnVivo(lista, '   ', '  ')).toEqual({ tecnico: null, usuario: null })
    expect(duplicadosEnVivo(lista, ' TECNICO-A ', '')).toEqual({ tecnico: MSG_TECNICO_DUPLICADO, usuario: null })
    expect(duplicadosEnVivo(lista, '', 'Usuario-B')).toEqual({ tecnico: null, usuario: MSG_USUARIO_DUPLICADO })
    expect(duplicadosEnVivo(lista, 'tecnico-b', 'usuario-a')).toEqual({ tecnico: MSG_TECNICO_DUPLICADO, usuario: MSG_USUARIO_DUPLICADO })
    // Un nombre de usuario igual a un nombre de técnico no cuenta: cada campo se compara con su propia columna.
    expect(duplicadosEnVivo(lista, 'usuario-a', 'tecnico-a')).toEqual({ tecnico: null, usuario: null })
    // Los nombres de la lista también se recortan (u.getNombreTecnico().trim()).
    expect(duplicadosEnVivo([{ ...lista[0], nombreTecnico: ' tecnico-z ' }], 'tecnico-z', '').tecnico).toBe(MSG_TECNICO_DUPLICADO)
    // La lista no trae ADMIN: "admin" pasa en vivo y lo frena el 409 del servidor (calco).
    expect(duplicadosEnVivo(lista, '', 'admin')).toEqual({ tecnico: null, usuario: null })
  })
})
```

Run: `npx vitest run src/modules/gestion/tecnicos/validacion.test.ts`
Expected: FAIL, `Failed to resolve import "./validacion"`.

- [ ] **Step 2: Implementar la validación**

`src/modules/gestion/tecnicos/validacion.ts`:

```ts
import type { Usuario } from '@/shared/api/client'

/** Opciones del combo de rol (RegisterController :53): literales en mayúsculas, sin ADMIN; TECNICO por defecto. */
export const ROLES = ['TECNICO', 'SUPERTECNICO'] as const
export type Rol = (typeof ROLES)[number]

/** El ComboNavy devuelve un string: cualquier cosa que no sea SUPERTECNICO es el rol por defecto. */
export function rolDe(valor: string): Rol {
  return valor === 'SUPERTECNICO' ? 'SUPERTECNICO' : 'TECNICO'
}

export type DatosAlta = { nombreTecnico: string; nombreUsuario: string; password: string; confirmar: string; rol: Rol }
export type CuerpoAlta = { nombreTecnico: string; nombreUsuario: string; password: string; rol: Rol }

export const MSG_CAMPOS = 'Todos los campos son obligatorios.'
export const MSG_NO_COINCIDEN = 'Las contraseñas no coinciden.'
export const MSG_PASSWORD_CORTA = 'La contraseña debe tener al menos 6 caracteres.'
export const MSG_TECNICO_DUPLICADO = 'Ya existe un técnico con ese nombre.'
export const MSG_USUARIO_DUPLICADO = 'Ese nombre de usuario ya existe.'

/** Calco de RegisterController.registrar (:257-275): nombres con trim, contraseña y confirmación sin trim; para en el primer
 *  fallo. La confirmación vacía no cuenta como obligatoria: cae en "no coinciden". */
export function validarAlta(d: DatosAlta): string | null {
  if (d.nombreTecnico.trim() === '' || d.nombreUsuario.trim() === '' || d.password === '') return MSG_CAMPOS
  if (d.password !== d.confirmar) return MSG_NO_COINCIDEN
  if (d.password.length < 6) return MSG_PASSWORD_CORTA
  return null
}

/** Cuerpo de POST /api/usuarios/tecnicos con los nombres recortados (el servidor también recorta desde la Task 1). */
export function cuerpoAlta(d: DatosAlta): CuerpoAlta {
  return { nombreTecnico: d.nombreTecnico.trim(), nombreUsuario: d.nombreUsuario.trim(), password: d.password, rol: d.rol }
}

const normalizar = (s: string) => s.trim().toLowerCase()

/** Calco de validarNombresEnVivo (:67-81): cada nombre contra su columna de la lista cargada, sin mayúsculas y con trim a
 *  los dos lados; un campo vacío no avisa. La lista no trae ADMIN, así que su nombre pasa aquí y lo frena el 409. */
export function duplicadosEnVivo(usuarios: Usuario[], nombreTecnico: string, nombreUsuario: string): { tecnico: string | null; usuario: string | null } {
  const tecnico = normalizar(nombreTecnico)
  const usuario = normalizar(nombreUsuario)
  const tecnicoDup = tecnico !== '' && usuarios.some((u) => normalizar(u.nombreTecnico) === tecnico)
  const usuarioDup = usuario !== '' && usuarios.some((u) => normalizar(u.nombreUsuario) === usuario)
  return { tecnico: tecnicoDup ? MSG_TECNICO_DUPLICADO : null, usuario: usuarioDup ? MSG_USUARIO_DUPLICADO : null }
}
```

Run: `npx vitest run src/modules/gestion/tecnicos/validacion.test.ts`
Expected: PASS, 7 tests.

- [ ] **Step 3: Textos**

`src/modules/gestion/tecnicos/textos.ts`:

```ts
/** Textos fijos de la página de técnicos (RegisterController de hotfix/0.16.3). */
export const MSG_ERROR_CARGA = 'Error al cargar los usuarios.'
export const MSG_ERROR_REGISTRO = 'Error al registrar. Inténtalo de nuevo.'
export const MSG_ERROR_ESTADO = 'Error al cambiar el estado del técnico.'
export const MSG_ERROR_ELIMINAR = 'Error al eliminar el técnico.'
export const MSG_ERROR_COMPROBAR = 'Error al comprobar las reparaciones del técnico.'
/** 404 del servidor (ValidacionUsuarios.MSG_NO_ENCONTRADO): clasificar() no conserva el mensaje de un 404. */
export const MSG_TECNICO_NO_ENCONTRADO = 'Técnico no encontrado.'

/** Alert INFORMATION (:223-228): cabecera y contenido del JavaFX unidos con saltos de línea (el diálogo usa whitespace-pre-line). */
export const TITULO_NO_ELIMINAR = 'No se puede eliminar'
export const textoNoEliminar = (n: string) =>
  `"${n}" tiene reparaciones asociadas.\nNo es posible eliminarlo para conservar el historial.\nPuedes desactivarlo para bloquear su acceso.`

/** Alert CONFIRMATION (:231-234), ahora ConfirmDialog con botón "Eliminar" (spec 6, G9). */
export const TITULO_ELIMINAR = 'Eliminar técnico'
export const textoEliminar = (n: string) => `¿Eliminar a "${n}" definitivamente?\nSe borrarán sus credenciales de acceso y su registro de técnico.`

/** Placeholder por defecto del TableView (RegisterView.fxml no define uno); se confirma con la captura (spec 6, §12). */
export const TEXTO_VACIO_TABLA = 'No hay contenido en la tabla'

/** Tooltip del candado (:179); la papelera no tiene tooltip. */
export const TOOLTIP_DESACTIVAR = 'Desactivar acceso'
export const TOOLTIP_ACTIVAR = 'Activar acceso'
```

- [ ] **Step 4: Test de los errores inline (falla)**

`src/modules/gestion/tecnicos/errores.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { ApiError, ConexionError, NoEncontradoError, ReglaNegocioError, StaleDataError } from '@/shared/api/errors'
import { mensajeInline } from './errores'

/** Spec 6, G9 y §8: la línea inline enseña el message del servidor en los códigos que cada acción espera y el texto fijo
 *  del JavaFX en el resto. */
describe('mensajeInline', () => {
  it('409 y 422 → el message del servidor si la acción los espera', () => {
    expect(mensajeInline(new StaleDataError(409, 'Ese nombre de usuario ya existe.'), 'fijo', [409, 422])).toBe('Ese nombre de usuario ya existe.')
    expect(mensajeInline(new ReglaNegocioError(422, 'Rol no permitido.'), 'fijo', [409, 422])).toBe('Rol no permitido.')
  })
  it('404 → "Técnico no encontrado." (clasificar descarta el message de los 404)', () => {
    expect(mensajeInline(new NoEncontradoError(404, 'Recurso no encontrado.'), 'fijo', [404])).toBe('Técnico no encontrado.')
  })
  it('un código que la acción no espera → el texto fijo', () => {
    expect(mensajeInline(new NoEncontradoError(404, 'Recurso no encontrado.'), 'fijo', [409, 422])).toBe('fijo')
    expect(mensajeInline(new StaleDataError(409, 'x'), 'fijo', [404])).toBe('fijo')
    // Un 503 con mensaje es ReglaNegocioError (4b) pero no es un 422: texto fijo.
    expect(mensajeInline(new ReglaNegocioError(503, 'x'), 'fijo', [422])).toBe('fijo')
  })
  it('400, conexión y cualquier otra cosa → el texto fijo', () => {
    expect(mensajeInline(new ApiError(400, 'Rol no permitido: ADMIN'), 'fijo', [404, 409, 422])).toBe('fijo')
    expect(mensajeInline(new ConexionError(503, 'Sin conexión con el servidor.', 'HTTP 503'), 'fijo', [404, 409, 422])).toBe('fijo')
    expect(mensajeInline(new Error('boom'), 'fijo', [404, 409, 422])).toBe('fijo')
    expect(mensajeInline('boom', 'fijo', [404, 409, 422])).toBe('fijo')
  })
})
```

Run: `npx vitest run src/modules/gestion/tecnicos/errores.test.ts`
Expected: FAIL, `Failed to resolve import "./errores"`.

- [ ] **Step 5: Implementar los errores inline**

`src/modules/gestion/tecnicos/errores.ts`:

```ts
import { NoEncontradoError, ReglaNegocioError, StaleDataError } from '@/shared/api/errors'
import { MSG_TECNICO_NO_ENCONTRADO } from './textos'

export type CodigoConMensaje = 404 | 409 | 422

/** Texto de la línea inline tras un fallo (spec 6, G9): el message del servidor en los códigos que la acción espera (alta:
 *  409/422; candado y comprobación: 404; borrado: 404/409) y el texto fijo del JavaFX en el resto. El 404 no trae su
 *  message hasta aquí (clasificar lo cambia por "Recurso no encontrado."): se usa el texto del servidor para esta ruta. */
export function mensajeInline(e: unknown, fijo: string, conMensaje: readonly CodigoConMensaje[]): string {
  if (e instanceof NoEncontradoError && conMensaje.includes(404)) return MSG_TECNICO_NO_ENCONTRADO
  if (e instanceof StaleDataError && conMensaje.includes(409)) return e.message
  if (e instanceof ReglaNegocioError && e.status === 422 && conMensaje.includes(422)) return e.message
  return fijo
}
```

Run: `npx vitest run src/modules/gestion/tecnicos/errores.test.ts`
Expected: PASS, 4 tests.

- [ ] **Step 6: Test de las columnas (falla)**

`src/modules/gestion/tecnicos/columnas.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Usuario } from '@/shared/api/client'
import { DataTable } from '@/shared/ui/DataTable'
import { columnasTecnicos } from './columnas'

const usuarios: Usuario[] = [
  { idUsu: 11, nombreUsuario: 'usuario-a', rol: 'TECNICO', idTec: 21, nombreTecnico: 'tecnico-a', activo: true },
  { idUsu: 12, nombreUsuario: 'usuario-b', rol: 'SUPERTECNICO', idTec: 22, nombreTecnico: 'tecnico-b', activo: false },
]

function montar() {
  const onToggle = vi.fn()
  const onEliminar = vi.fn()
  const r = render(<DataTable columns={columnasTecnicos({ onToggle, onEliminar })} data={usuarios} vacio="No hay contenido en la tabla" getRowId={(u) => String(u.idTec)} />)
  return { ...r, onToggle, onEliminar }
}
const celdas = (container: HTMLElement, columna: string) => Array.from(container.querySelectorAll(`[data-columna="${columna}"]`)).map((c) => c.textContent)
const fila = (nombre: string) => screen.getByRole('row', { name: new RegExp(`^${nombre}`) })

/** Calco de RegisterController.configurarTabla (:97-186) y RegisterView.fxml :105-109. */
describe('columnas de técnicos', () => {
  it('Técnico, Usuario, Rol, Estado y una de acciones sin cabecera, con los prefWidth del FXML', () => {
    const { container } = montar()
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Técnico', 'Usuario', 'Rol', 'Estado', ''])
    const cols = container.querySelectorAll('col')
    expect(cols[0]).toHaveStyle({ width: '160px' })
    expect(cols[1]).toHaveStyle({ width: '130px' })
    expect(cols[2]).toHaveStyle({ width: '110px' })
    expect(cols[3]).toHaveStyle({ width: '90px' })
    expect(cols[4]).toHaveStyle({ width: '80px' })
  })
  it('nombre de técnico, de usuario y el rol en mayúsculas tal cual, en el orden recibido', () => {
    const { container } = montar()
    expect(celdas(container, 'tecnico')).toEqual(['tecnico-a', 'tecnico-b'])
    expect(celdas(container, 'usuario')).toEqual(['usuario-a', 'usuario-b'])
    expect(celdas(container, 'rol')).toEqual(['TECNICO', 'SUPERTECNICO'])
  })
  it('badge "Activo" (#2E7D32 sobre #D4EDDA) e "Inactivo" (#B03040 sobre #F5E6E6), radio 10, 11 px negrita', () => {
    montar()
    expect(screen.getByText('Activo')).toHaveClass('bg-badge-usuario-activo-bg', 'text-badge-usuario-activo-text', 'rounded-[10px]', 'px-2.5', 'py-[3px]', 'text-[11px]', 'font-bold')
    expect(screen.getByText('Inactivo')).toHaveClass('bg-badge-usuario-inactivo-bg', 'text-badge-usuario-inactivo-text')
  })
  it('candado: abierto (Unlock.png) con "Desactivar acceso" si activo, cerrado (Lock.png) con "Activar acceso" si no; papelera sin tooltip en todas las filas', () => {
    montar()
    const desactivar = within(fila('tecnico-a')).getByRole('button', { name: 'Desactivar acceso' })
    expect(desactivar).toHaveAttribute('title', 'Desactivar acceso')
    expect(desactivar.querySelector('img')).toHaveAttribute('src', '/Unlock.png')
    expect(desactivar.querySelector('img')).toHaveClass('h-[18px]', 'w-[18px]')
    const activar = within(fila('tecnico-b')).getByRole('button', { name: 'Activar acceso' })
    expect(activar).toHaveAttribute('title', 'Activar acceso')
    expect(activar.querySelector('img')).toHaveAttribute('src', '/Lock.png')
    const papeleras = screen.getAllByRole('button', { name: 'Eliminar' })
    expect(papeleras).toHaveLength(2)
    for (const p of papeleras) {
      expect(p).not.toHaveAttribute('title')
      expect(p.querySelector('img')).toHaveAttribute('src', '/borrar.png')
      expect(p.querySelector('img')).toHaveClass('h-[22px]', 'w-[22px]')
    }
  })
  it('el candado llama a onToggle y la papelera a onEliminar con su fila', async () => {
    const { onToggle, onEliminar } = montar()
    await userEvent.click(within(fila('tecnico-b')).getByRole('button', { name: 'Activar acceso' }))
    expect(onToggle).toHaveBeenCalledWith(usuarios[1])
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Eliminar' }))
    expect(onEliminar).toHaveBeenCalledWith(usuarios[0])
    expect(onToggle).toHaveBeenCalledTimes(1)
    expect(onEliminar).toHaveBeenCalledTimes(1)
  })
})
```

Run: `npx vitest run src/modules/gestion/tecnicos/columnas.test.tsx`
Expected: FAIL, `Failed to resolve import "./columnas"`.

- [ ] **Step 7: Implementar badge y columnas**

`src/modules/gestion/tecnicos/BadgeEstadoUsuario.tsx`:

```tsx
import { cn } from '@/shared/lib/utils'

/** Badge de la columna Estado (RegisterController :106-131): "Activo" #2E7D32 sobre #D4EDDA, "Inactivo" #B03040 sobre
 *  #F5E6E6, radio 10, padding 3 10, 11 px negrita. Conserva sus colores en la fila seleccionada, como en el JavaFX. */
export function BadgeEstadoUsuario({ activo }: { activo: boolean }) {
  return (
    <span
      className={cn(
        'inline-block rounded-[10px] px-2.5 py-[3px] text-[11px] font-bold',
        activo ? 'bg-badge-usuario-activo-bg text-badge-usuario-activo-text' : 'bg-badge-usuario-inactivo-bg text-badge-usuario-inactivo-text',
      )}
    >
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}
```

`src/modules/gestion/tecnicos/columnas.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { Usuario } from '@/shared/api/client'
import { BadgeEstadoUsuario } from './BadgeEstadoUsuario'
import { TOOLTIP_ACTIVAR, TOOLTIP_DESACTIVAR } from './textos'

/** prefWidth de RegisterView.fxml :105-109. La de acciones es la última (80): en el JavaFX absorbe el sobrante
 *  (CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); aquí lo que sobra queda en la columna de relleno del DataTable. */
export const ANCHOS_TECNICOS = { tecnico: 160, usuario: 130, rol: 110, estado: 90, acciones: 80 } as const

type Acciones = { onToggle: (u: Usuario) => void; onEliminar: (u: Usuario) => void }

/** Columnas de "Técnicos registrados" (RegisterController.configurarTabla :97-186). El rol va tal cual (TECNICO /
 *  SUPERTECNICO). Acciones (:138-182): candado transparente de 18 px (abierto = tiene acceso) con tooltip, 4 px y la
 *  papelera de 22 px sin tooltip, en todas las filas (el filtro "tiene reparaciones" se hace al pulsar). El clic también
 *  selecciona la fila, como en el JavaFX. */
export function columnasTecnicos({ onToggle, onEliminar }: Acciones): ColumnDef<Usuario>[] {
  return [
    { id: 'tecnico', header: 'Técnico', size: ANCHOS_TECNICOS.tecnico, accessorFn: (u) => u.nombreTecnico },
    { id: 'usuario', header: 'Usuario', size: ANCHOS_TECNICOS.usuario, accessorFn: (u) => u.nombreUsuario },
    { id: 'rol', header: 'Rol', size: ANCHOS_TECNICOS.rol, accessorFn: (u) => u.rol },
    { id: 'estado', header: 'Estado', size: ANCHOS_TECNICOS.estado, cell: ({ row }) => <BadgeEstadoUsuario activo={row.original.activo} /> },
    {
      id: 'acciones',
      header: '',
      size: ANCHOS_TECNICOS.acciones,
      cell: ({ row }) => {
        const u = row.original
        const tooltip = u.activo ? TOOLTIP_DESACTIVAR : TOOLTIP_ACTIVAR
        return (
          <div className="flex items-center justify-center gap-1">
            <button type="button" aria-label={tooltip} title={tooltip} onClick={() => onToggle(u)} className="cursor-pointer px-1.5 py-1">
              <img src={u.activo ? '/Unlock.png' : '/Lock.png'} alt="" className="h-[18px] w-[18px]" />
            </button>
            <button type="button" aria-label="Eliminar" onClick={() => onEliminar(u)} className="cursor-pointer">
              <img src="/borrar.png" alt="" className="h-[22px] w-[22px]" />
            </button>
          </div>
        )
      },
    },
  ]
}
```

Run: `npx vitest run src/modules/gestion/tecnicos/columnas.test.tsx`
Expected: PASS, 5 tests.

- [ ] **Step 8: Test del `api.ts` de técnicos (falla)**

`src/modules/gestion/tecnicos/api.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { NoEncontradoError, StaleDataError } from '@/shared/api/errors'
import { crearQueryClient } from '@/shared/api/queryClient'
import { onError } from '@/shared/ui/alertas'
import { server } from '@/test/server'
import { consultarTieneReparaciones, useCambiarActivo, useEliminar, useRegistrar } from './api'

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  // Datos en caché de las consultas que una escritura debe marcar como caducadas (y una ajena que no).
  qc.setQueryData(['usuarios', 'tecnicos'], [])
  qc.setQueryData(['tecnicos'], [])
  qc.setQueryData(['tecnicos', 'activos'], [])
  qc.setQueryData(['clientes'], [])
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}
function caducadas(qc: ReturnType<typeof crearQueryClient>) {
  return {
    usuarios: qc.getQueryState(['usuarios', 'tecnicos'])?.isInvalidated,
    tecnicos: qc.getQueryState(['tecnicos'])?.isInvalidated,
    activos: qc.getQueryState(['tecnicos', 'activos'])?.isInvalidated,
    clientes: qc.getQueryState(['clientes'])?.isInvalidated,
  }
}
const RECARGADAS = { usuarios: true, tecnicos: true, activos: true, clientes: false }
const INTACTAS = { usuarios: false, tecnicos: false, activos: false, clientes: false }

type Peticion = { metodo: string; ruta: string; query: string; cuerpo: unknown }
function registrar(patron: string, respuesta: () => Response = () => new HttpResponse(null, { status: 204 })) {
  const peticiones: Peticion[] = []
  server.use(
    http.all(patron, async ({ request }) => {
      const texto = await request.text()
      const url = new URL(request.url)
      peticiones.push({ metodo: request.method, ruta: url.pathname, query: url.search, cuerpo: texto ? JSON.parse(texto) : null })
      return respuesta()
    }),
  )
  return peticiones
}

describe('api de técnicos', () => {
  it('useRegistrar: POST /api/usuarios/tecnicos con el cuerpo tal cual y, si sale bien, recarga usuarios y técnicos', async () => {
    const peticiones = registrar('*/api/usuarios/tecnicos', () => new HttpResponse(null, { status: 201 }))
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useRegistrar(), { wrapper })
    await result.current.mutateAsync({ nombreTecnico: 'tecnico-c', nombreUsuario: 'usuario-c', password: 'secreta1', rol: 'SUPERTECNICO' })
    expect(peticiones).toEqual([
      { metodo: 'POST', ruta: '/api/usuarios/tecnicos', query: '', cuerpo: { nombreTecnico: 'tecnico-c', nombreUsuario: 'usuario-c', password: 'secreta1', rol: 'SUPERTECNICO' } },
    ])
    expect(caducadas(qc)).toEqual(RECARGADAS)
  })
  it('useRegistrar con 409: rechaza con el message del servidor, sin diálogo global y sin recargar', async () => {
    const avisos = vi.fn()
    const quitar = onError(avisos)
    registrar('*/api/usuarios/tecnicos', () => HttpResponse.json({ message: 'Ese nombre de usuario ya existe.' }, { status: 409 }))
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useRegistrar(), { wrapper })
    const error = await result.current.mutateAsync({ nombreTecnico: 'tecnico-c', nombreUsuario: 'usuario-a', password: 'secreta1', rol: 'TECNICO' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(StaleDataError)
    expect((error as StaleDataError).message).toBe('Ese nombre de usuario ya existe.')
    expect(avisos).not.toHaveBeenCalled()
    expect(caducadas(qc)).toEqual(INTACTAS)
    quitar()
  })
  it.each([
    [true, '/api/usuarios/tecnicos/21/activar'],
    [false, '/api/usuarios/tecnicos/21/desactivar'],
  ])('useCambiarActivo(activar: %s) → PATCH %s sin cuerpo y recarga', async (activar, ruta) => {
    const peticiones = registrar('*/api/usuarios/tecnicos/21/*')
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useCambiarActivo(), { wrapper })
    await result.current.mutateAsync({ idTec: 21, activar })
    expect(peticiones).toEqual([{ metodo: 'PATCH', ruta, query: '', cuerpo: null }])
    expect(caducadas(qc)).toEqual(RECARGADAS)
  })
  it('useEliminar → DELETE /api/usuarios/tecnicos/21?idUsu=11 y recarga', async () => {
    const peticiones = registrar('*/api/usuarios/tecnicos/21')
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useEliminar(), { wrapper })
    await result.current.mutateAsync({ idTec: 21, idUsu: 11 })
    expect(peticiones).toEqual([{ metodo: 'DELETE', ruta: '/api/usuarios/tecnicos/21', query: '?idUsu=11', cuerpo: null }])
    expect(caducadas(qc)).toEqual(RECARGADAS)
  })
  it('consultarTieneReparaciones lee value de GET …/{idTec}/tiene-reparaciones', async () => {
    server.use(http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', ({ params }) => HttpResponse.json({ value: params.idTec === '21' })))
    expect(await consultarTieneReparaciones(21)).toBe(true)
    expect(await consultarTieneReparaciones(22)).toBe(false)
  })
  it('consultarTieneReparaciones con 404 rechaza con NoEncontradoError', async () => {
    server.use(http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', () => HttpResponse.json({ message: 'Técnico no encontrado.' }, { status: 404 })))
    await expect(consultarTieneReparaciones(99)).rejects.toBeInstanceOf(NoEncontradoError)
  })
})
```

Run: `npx vitest run src/modules/gestion/tecnicos/api.test.tsx`
Expected: FAIL, `Failed to resolve import "./api"`.

- [ ] **Step 9: Implementar el `api.ts` de técnicos**

`src/modules/gestion/tecnicos/api.ts`:

```ts
import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import { useCallback } from 'react'
import { api } from '@/shared/api/client'
import { CLAVE_TECNICOS_LITERAL, CLAVE_USUARIOS } from '../api'
import type { CuerpoAlta } from './validacion'

/** Tras una escritura con éxito (spec 6, §7): la tabla de esta página (['usuarios', …]) y los combos y listas de técnicos
 *  del resto de la web (['tecnicos', …]); equivale a la recarga de la vista al cerrar el modal del JavaFX. */
function useRecargaUsuarios(): () => void {
  const qc = useQueryClient()
  return useCallback(() => {
    void qc.invalidateQueries({ queryKey: CLAVE_USUARIOS })
    void qc.invalidateQueries({ queryKey: CLAVE_TECNICOS_LITERAL })
  }, [qc])
}

/** Las tres escrituras silencian el diálogo global: el error va a la línea inline de la página (spec 6, G9). El corte de
 *  conexión lo sigue avisando el MutationCache. Recargan solo si salen bien (calco: el JavaFX recarga tras el éxito). */
export function useRegistrar(): UseMutationResult<void, Error, CuerpoAlta> {
  const recargar = useRecargaUsuarios()
  return useMutation({
    mutationFn: async (cuerpo: CuerpoAlta) => {
      await api.POST('/api/usuarios/tecnicos', { body: cuerpo })
    },
    meta: { silenciarError: true },
    onSuccess: recargar,
  })
}

export function useCambiarActivo(): UseMutationResult<void, Error, { idTec: number; activar: boolean }> {
  const recargar = useRecargaUsuarios()
  return useMutation({
    mutationFn: async ({ idTec, activar }: { idTec: number; activar: boolean }) => {
      const params = { path: { idTec } }
      if (activar) await api.PATCH('/api/usuarios/tecnicos/{idTec}/activar', { params })
      else await api.PATCH('/api/usuarios/tecnicos/{idTec}/desactivar', { params })
    },
    meta: { silenciarError: true },
    onSuccess: recargar,
  })
}

/** DELETE con el `idUsu` de la fila, como el JavaFX (el servidor lo resuelve desde idTec desde la Task 2 y lo ignora). */
export function useEliminar(): UseMutationResult<void, Error, { idTec: number; idUsu: number }> {
  const recargar = useRecargaUsuarios()
  return useMutation({
    mutationFn: async ({ idTec, idUsu }: { idTec: number; idUsu: number }) => {
      await api.DELETE('/api/usuarios/tecnicos/{idTec}', { params: { path: { idTec }, query: { idUsu } } })
    },
    meta: { silenciarError: true },
    onSuccess: recargar,
  })
}

/** Lectura bajo demanda antes de borrar (no es una consulta de caché: la pide la papelera en cada clic, como el JavaFX). */
export async function consultarTieneReparaciones(idTec: number): Promise<boolean> {
  const { data } = await api.GET('/api/usuarios/tecnicos/{idTec}/tiene-reparaciones', { params: { path: { idTec } } })
  return data?.value === true
}
```

Run: `npx vitest run src/modules/gestion/tecnicos/api.test.tsx`
Expected: PASS, 7 tests.

- [ ] **Step 10: Lint y suite**

```bash
npm run lint && npx tsc -b && npx vitest run src/modules/gestion
```

Expected: lint y typecheck sin errores; PASS, 46 tests en 8 ficheros de `src/modules/gestion` (32 nuevos: 9 de la Task 7 + 7 de `validacion` + 4 de `errores` + 5 de `columnas` + 7 de `api`; más los 14 de `clientes/`, que la carpeta incluye). Acumulado: **1374**.

- [ ] **Step 11: Commit**

```bash
git add src/modules/gestion/tecnicos/validacion.ts src/modules/gestion/tecnicos/validacion.test.ts src/modules/gestion/tecnicos/textos.ts src/modules/gestion/tecnicos/errores.ts src/modules/gestion/tecnicos/errores.test.ts src/modules/gestion/tecnicos/BadgeEstadoUsuario.tsx src/modules/gestion/tecnicos/columnas.tsx src/modules/gestion/tecnicos/columnas.test.tsx src/modules/gestion/tecnicos/api.ts src/modules/gestion/tecnicos/api.test.tsx
git commit -m "feat(gestion): validacion del alta y duplicados en vivo, textos, errores inline, badge de estado, columnas con candado y papelera, y escrituras de tecnicos"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

---

## Task 9: Web — `TecnicosPage` y ruta `/gestion/tecnicos`

**Files:**
- Create: `src/modules/gestion/tecnicos/TecnicosPage.tsx`, `src/modules/gestion/tecnicos/TecnicosPage.test.tsx`
- Modify: `src/app/router.tsx` (import de `TecnicosPage` tras el de `RequiereAdmin`; la ruta `/gestion/tecnicos` que la Task 7 dejó bajo `<RequiereAdmin />`)

**Interfaces:**
- Consumes: `useUsuariosTecnicos` de `../api` y `rutaVolverA` de `../navegacion` (Task 7); `validarAlta`, `cuerpoAlta`, `duplicadosEnVivo`, `ROLES`, `rolDe`, `DatosAlta` de `./validacion`, textos de `./textos`, `mensajeInline` de `./errores`, `columnasTecnicos` de `./columnas`, `useRegistrar`, `useCambiarActivo`, `useEliminar`, `consultarTieneReparaciones` de `./api` (Task 8); `useAlerta().mostrarAviso` (`AlertaProvider.tsx:114`), `ConfirmDialog` (`ConfirmDialog.tsx:20`, descripción con `whitespace-pre-line`, l.45), `DataTable` (`ordenacion`, `vacio`, `altoFila`, `seleccionada`/`onSeleccionar`), `ComboNavy` (`ComboNavy.tsx:41`), `Input`, `Button`.
- Produces: `export function TecnicosPage(): JSX.Element` en `@/modules/gestion/tecnicos/TecnicosPage`; `/gestion/tecnicos` pinta la página (bajo `RequiereAdmin`).

**Supuestos:** `renderConProviders` acepta `queryClient` precargado (`src/test/render.tsx:10`, l.24) y `renderConRouter` devuelve `router` para navegar con state (l.53-70). El aviso de `mostrarAviso` es un `Dialog` con el título como nombre accesible y botón "Aceptar" (`AlertaProvider.tsx:126-136`). El `ConfirmDialog` pone la acción antes de "Cancelar" y lleva el foco a "Cancelar" (l.38-41), así que Enter no borra por accidente. La página no se envuelve en `<form>`: Enter no registra (calco, `btnRegistrar` no es `defaultButton`).

> Búsqueda previa: `TecnicosPage` no existe; `/gestion/tecnicos` es `PendienteDeMigrar` bajo `RequiereAdmin` desde la Task 7. `useErrorServidor` vive en `modules/almacen/ui/useErrorServidor.ts` y no se puede importar desde `gestion` (regla de módulos); tampoco hace falta: aquí la línea inline no se oculta al teclear (calco: `lblError` queda hasta la siguiente acción) y se vacía al empezar cada acción (G9). `ClientesPage` (`modules/gestion/clientes/ClientesPage.tsx:78-81`) usa `mensajeDeError`/`esErrorGestionadoGlobalmente` para el diálogo; aquí el texto va inline con `mensajeInline` (Task 8). El error de carga sigue el patrón "ajustar estado al cambiar una prop" de `useErrorServidor.ts:10-13`, con `errorUpdatedAt` como marca de cada fallo nuevo.

- [ ] **Step 1: Test de la página (falla)**

`src/modules/gestion/tecnicos/TecnicosPage.test.tsx`:

```tsx
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Usuario } from '@/shared/api/client'
import { crearQueryClient } from '@/shared/api/queryClient'
import { ExportableProvider, useExportable } from '@/shared/ui/exportable'
import { renderConProviders, renderConRouter, SESION_ADMIN } from '@/test/render'
import { server } from '@/test/server'
import { TecnicosPage } from './TecnicosPage'

const usuarios: Usuario[] = [
  { idUsu: 11, nombreUsuario: 'usuario-a', rol: 'TECNICO', idTec: 21, nombreTecnico: 'tecnico-a', activo: true },
  { idUsu: 12, nombreUsuario: 'usuario-b', rol: 'SUPERTECNICO', idTec: 22, nombreTecnico: 'tecnico-b', activo: false },
]
const cargas = { n: 0 }

beforeEach(() => {
  cargas.n = 0
  server.use(http.get('*/api/usuarios/tecnicos', () => { cargas.n += 1; return HttpResponse.json(usuarios) }))
})

const montar = (opciones: { queryClient?: ReturnType<typeof crearQueryClient> } = {}) =>
  renderConProviders(<TecnicosPage />, { sesion: SESION_ADMIN, ruta: '/gestion/tecnicos', ...opciones })
const fila = (nombre: string) => screen.getByRole('row', { name: new RegExp(`^${nombre}`) })
const linea = () => screen.getByRole('alert')
const botonRegistrar = () => screen.getByRole('button', { name: 'Registrar técnico' })

async function rellenar({ tecnico = '', usuario = '', password = '', confirmar = '' }) {
  if (tecnico) await userEvent.type(screen.getByLabelText('Nombre del técnico'), tecnico)
  if (usuario) await userEvent.type(screen.getByLabelText('Nombre de usuario'), usuario)
  if (password) await userEvent.type(screen.getByLabelText('Contraseña'), password)
  if (confirmar) await userEvent.type(screen.getByLabelText('Confirmar'), confirmar)
}

/** Sonda del menú de usuario: "Descargar CSV" se habilita solo si la vista registra un exportable. */
function SondaCsv() {
  return <p>{useExportable() === null ? 'SIN CSV' : 'CON CSV'}</p>
}

/** Calco de RegisterView.fxml y RegisterController (hotfix/0.16.3) como página del shell (spec 6, §6.1). */
describe('TecnicosPage', () => {
  it('cabecera con logo, cuatro campos con sus etiquetas y placeholders, fila de acción, tabla y "Cerrar"', async () => {
    const { container } = montar()
    expect(screen.getByRole('heading', { name: 'Gestión de usuarios' })).toHaveClass('text-[18px]', 'font-bold', 'text-azul-medio')
    expect(screen.getByText('Registra o elimina accesos al sistema')).toHaveClass('text-[12px]', 'text-azul-gris')
    expect(container.querySelector('img[src="/logo_inicio_sesion.png"]')).toHaveClass('h-[46px]', 'w-[46px]')
    const campos = [
      ['Nombre del técnico', 'Nombre visible en reparaciones', 'text'],
      ['Nombre de usuario', 'Credencial de login', 'text'],
      ['Contraseña', 'Contraseña', 'password'],
      ['Confirmar', 'Repite la contraseña', 'password'],
    ] as const
    for (const [etiqueta, placeholder, tipo] of campos) {
      const campo = screen.getByLabelText(etiqueta)
      expect(campo).toHaveAttribute('placeholder', placeholder)
      expect(campo).toHaveAttribute('type', tipo)
      expect(campo).toHaveValue('')
    }
    expect(screen.getByText('Nombre del técnico')).toHaveClass('text-[11px]', 'font-bold', 'text-azul-gris')
    // Sin ojo en los campos de contraseña del alta (calco: PasswordField simple).
    expect(screen.queryByRole('button', { name: 'Mostrar contraseña' })).not.toBeInTheDocument()
    const combo = screen.getByRole('combobox', { name: 'Rol' })
    expect(combo).toHaveTextContent(/^TECNICO$/)
    expect(combo).toHaveStyle({ width: '130px' })
    await userEvent.click(combo)
    expect(within(screen.getByRole('listbox', { name: 'Rol' })).getAllByRole('option').map((o) => o.textContent)).toEqual(['TECNICO', 'SUPERTECNICO'])
    await userEvent.keyboard('{Escape}')
    expect(botonRegistrar()).toBeEnabled()
    expect(linea()).toBeEmptyDOMElement()
    expect(screen.getByRole('heading', { name: 'Técnicos registrados' })).toHaveClass('text-[13px]', 'font-bold')
    expect(screen.getByRole('button', { name: 'Cerrar' })).toHaveClass('text-[12px]', 'text-azul-gris')
    expect(container.firstChild).toHaveClass('bg-fondo-gestion')
    expect(await screen.findByText('tecnico-a')).toBeInTheDocument()
  })
  it('pinta la lista del servidor en su orden, sin ordenación por cabecera', async () => {
    const { container } = montar()
    await screen.findByText('tecnico-a')
    expect(Array.from(container.querySelectorAll('[data-columna="tecnico"]')).map((c) => c.textContent)).toEqual(['tecnico-a', 'tecnico-b'])
    expect(Array.from(container.querySelectorAll('[data-columna="rol"]')).map((c) => c.textContent)).toEqual(['TECNICO', 'SUPERTECNICO'])
    expect(screen.getByRole('columnheader', { name: 'Técnico' })).not.toHaveAttribute('aria-sort')
    expect(within(screen.getByRole('columnheader', { name: 'Técnico' })).queryByRole('button')).not.toBeInTheDocument()
    expect(cargas.n).toBe(1)
  })
  it('no registra exportable: "Descargar CSV" queda deshabilitado en esta ruta (calco)', async () => {
    renderConProviders(
      <ExportableProvider>
        <TecnicosPage />
        <SondaCsv />
      </ExportableProvider>,
      { sesion: SESION_ADMIN, ruta: '/gestion/tecnicos' },
    )
    await screen.findByText('tecnico-a')
    expect(screen.getByText('SIN CSV')).toBeInTheDocument()
  })
  it('duplicados en vivo (sin mayúsculas y con trim) bajo cada campo y "Registrar técnico" deshabilitado; "admin" pasa', async () => {
    montar()
    await screen.findByText('tecnico-a')
    const tecnico = screen.getByLabelText('Nombre del técnico')
    const usuario = screen.getByLabelText('Nombre de usuario')
    await userEvent.type(tecnico, ' TECNICO-A ')
    expect(screen.getByText('Ya existe un técnico con ese nombre.')).toHaveClass('text-[10px]', 'text-texto-error')
    expect(botonRegistrar()).toBeDisabled()
    await userEvent.type(usuario, 'Usuario-B')
    expect(screen.getByText('Ese nombre de usuario ya existe.')).toBeInTheDocument()
    await userEvent.clear(tecnico)
    expect(screen.queryByText('Ya existe un técnico con ese nombre.')).not.toBeInTheDocument()
    expect(botonRegistrar()).toBeDisabled()
    await userEvent.clear(usuario)
    expect(botonRegistrar()).toBeEnabled()
    await userEvent.type(usuario, 'admin')
    expect(screen.queryByText('Ese nombre de usuario ya existe.')).not.toBeInTheDocument()
    expect(botonRegistrar()).toBeEnabled()
    // Los avisos en vivo no van a la línea inline.
    expect(linea()).toBeEmptyDOMElement()
  })
  it('valida en el orden del JavaFX sin llamar al servidor, y Enter no registra', async () => {
    let posts = 0
    server.use(http.post('*/api/usuarios/tecnicos', () => { posts += 1; return new HttpResponse(null, { status: 201 }) }))
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.type(screen.getByLabelText('Confirmar'), 'x{enter}')
    expect(linea()).toBeEmptyDOMElement()
    await userEvent.click(botonRegistrar())
    expect(linea()).toHaveTextContent('Todos los campos son obligatorios.')
    expect(linea()).toHaveClass('text-[11px]', 'text-texto-error')
    await rellenar({ tecnico: 'tecnico-c', usuario: 'usuario-c', password: 'secreta1' })
    await userEvent.click(botonRegistrar())
    expect(linea()).toHaveTextContent('Las contraseñas no coinciden.')
    await userEvent.clear(screen.getByLabelText('Contraseña'))
    await userEvent.clear(screen.getByLabelText('Confirmar'))
    await rellenar({ password: '12345', confirmar: '12345' })
    await userEvent.click(botonRegistrar())
    expect(linea()).toHaveTextContent('La contraseña debe tener al menos 6 caracteres.')
    expect(posts).toBe(0)
  })
  it('alta: POST con nombres recortados y el rol del combo; vacía el formulario, combo a TECNICO y recarga, sin mensaje de éxito', async () => {
    let cuerpo: unknown = null
    server.use(http.post('*/api/usuarios/tecnicos', async ({ request }) => { cuerpo = await request.json(); return new HttpResponse(null, { status: 201 }) }))
    montar()
    await screen.findByText('tecnico-a')
    await rellenar({ tecnico: '  tecnico-c ', usuario: ' usuario-c ', password: 'secreta1', confirmar: 'secreta1' })
    await userEvent.click(screen.getByRole('combobox', { name: 'Rol' }))
    await userEvent.click(within(screen.getByRole('listbox', { name: 'Rol' })).getByRole('button', { name: 'SUPERTECNICO' }))
    await userEvent.click(botonRegistrar())
    await waitFor(() => expect(cargas.n).toBe(2))
    expect(cuerpo).toEqual({ nombreTecnico: 'tecnico-c', nombreUsuario: 'usuario-c', password: 'secreta1', rol: 'SUPERTECNICO' })
    for (const etiqueta of ['Nombre del técnico', 'Nombre de usuario', 'Contraseña', 'Confirmar']) {
      await waitFor(() => expect(screen.getByLabelText(etiqueta)).toHaveValue(''))
    }
    expect(screen.getByRole('combobox', { name: 'Rol' })).toHaveTextContent(/^TECNICO$/)
    expect(linea()).toBeEmptyDOMElement()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it.each([
    [409, () => HttpResponse.json({ message: 'Ese nombre de usuario ya existe.' }, { status: 409 }), 'Ese nombre de usuario ya existe.'],
    [422, () => HttpResponse.json({ message: 'El nombre de usuario no puede superar 50 caracteres.' }, { status: 422 }), 'El nombre de usuario no puede superar 50 caracteres.'],
    [400, () => new HttpResponse('Rol no permitido: ADMIN', { status: 400 }), 'Error al registrar. Inténtalo de nuevo.'],
  ])('alta con %s: la línea inline enseña el texto que toca y el formulario conserva lo escrito', async (_codigo, respuesta, texto) => {
    server.use(http.post('*/api/usuarios/tecnicos', respuesta))
    montar()
    await screen.findByText('tecnico-a')
    await rellenar({ tecnico: 'tecnico-c', usuario: 'usuario-c', password: 'secreta1', confirmar: 'secreta1' })
    await userEvent.click(botonRegistrar())
    expect(await screen.findByText(texto)).toBe(linea())
    expect(screen.getByLabelText('Nombre del técnico')).toHaveValue('tecnico-c')
    expect(cargas.n).toBe(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('candado sin confirmación: desactiva al activo, activa al inactivo y recarga tras cada uno', async () => {
    const rutas: string[] = []
    server.use(http.patch('*/api/usuarios/tecnicos/:idTec/:accion', ({ request }) => { rutas.push(new URL(request.url).pathname); return new HttpResponse(null, { status: 204 }) }))
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Desactivar acceso' }))
    await waitFor(() => expect(cargas.n).toBe(2))
    await userEvent.click(within(fila('tecnico-b')).getByRole('button', { name: 'Activar acceso' }))
    await waitFor(() => expect(cargas.n).toBe(3))
    expect(rutas).toEqual(['/api/usuarios/tecnicos/21/desactivar', '/api/usuarios/tecnicos/22/activar'])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(linea()).toBeEmptyDOMElement()
  })
  it.each([
    [404, () => HttpResponse.json({ message: 'Técnico no encontrado.' }, { status: 404 }), 'Técnico no encontrado.'],
    [400, () => new HttpResponse('boom', { status: 400 }), 'Error al cambiar el estado del técnico.'],
  ])('candado con %s: texto inline y sin recarga', async (_codigo, respuesta, texto) => {
    server.use(http.patch('*/api/usuarios/tecnicos/:idTec/:accion', respuesta))
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Desactivar acceso' }))
    expect(await screen.findByText(texto)).toBe(linea())
    expect(cargas.n).toBe(1)
  })
  it('papelera de un técnico con reparaciones: aviso "No se puede eliminar" con sus tres líneas y no borra', async () => {
    let borrados = 0
    server.use(
      http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', () => HttpResponse.json({ value: true })),
      http.delete('*/api/usuarios/tecnicos/:idTec', () => { borrados += 1; return new HttpResponse(null, { status: 204 }) }),
    )
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Eliminar' }))
    const aviso = await screen.findByRole('dialog', { name: 'No se puede eliminar' })
    expect(aviso).toHaveTextContent('"tecnico-a" tiene reparaciones asociadas. No es posible eliminarlo para conservar el historial. Puedes desactivarlo para bloquear su acceso.')
    await userEvent.click(within(aviso).getByRole('button', { name: 'Aceptar' }))
    expect(borrados).toBe(0)
    expect(cargas.n).toBe(1)
  })
  it('papelera sin reparaciones: confirmación "Eliminar técnico" → DELETE con idUsu → recarga', async () => {
    const borrados: string[] = []
    server.use(
      http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', () => HttpResponse.json({ value: false })),
      http.delete('*/api/usuarios/tecnicos/:idTec', ({ request }) => { const u = new URL(request.url); borrados.push(u.pathname + u.search); return new HttpResponse(null, { status: 204 }) }),
    )
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Eliminar' }))
    const confirmacion = await screen.findByRole('dialog', { name: 'Eliminar técnico' })
    expect(confirmacion).toHaveTextContent('¿Eliminar a "tecnico-a" definitivamente? Se borrarán sus credenciales de acceso y su registro de técnico.')
    expect(within(confirmacion).getByRole('button', { name: 'Cancelar' })).toBeInTheDocument()
    await userEvent.click(within(confirmacion).getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(cargas.n).toBe(2))
    expect(borrados).toEqual(['/api/usuarios/tecnicos/21?idUsu=11'])
    expect(screen.queryByRole('dialog', { name: 'Eliminar técnico' })).not.toBeInTheDocument()
  })
  it('"Cancelar" en la confirmación no borra', async () => {
    let borrados = 0
    server.use(
      http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', () => HttpResponse.json({ value: false })),
      http.delete('*/api/usuarios/tecnicos/:idTec', () => { borrados += 1; return new HttpResponse(null, { status: 204 }) }),
    )
    montar()
    await screen.findByText('tecnico-b')
    await userEvent.click(within(fila('tecnico-b')).getByRole('button', { name: 'Eliminar' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Eliminar técnico' })).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Eliminar técnico' })).not.toBeInTheDocument())
    expect(borrados).toBe(0)
    expect(cargas.n).toBe(1)
  })
  it.each([
    [409, () => HttpResponse.json({ message: '"tecnico-a" tiene reparaciones asociadas.' }, { status: 409 }), '"tecnico-a" tiene reparaciones asociadas.'],
    [404, () => HttpResponse.json({ message: 'Técnico no encontrado.' }, { status: 404 }), 'Técnico no encontrado.'],
    [400, () => new HttpResponse('boom', { status: 400 }), 'Error al eliminar el técnico.'],
  ])('DELETE con %s (p. ej. la carrera entre la comprobación y el borrado): texto inline y sin recarga', async (_codigo, respuesta, texto) => {
    server.use(
      http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', () => HttpResponse.json({ value: false })),
      http.delete('*/api/usuarios/tecnicos/:idTec', respuesta),
    )
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Eliminar' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Eliminar técnico' })).getByRole('button', { name: 'Eliminar' }))
    expect(await screen.findByText(texto)).toBe(linea())
    expect(cargas.n).toBe(1)
  })
  it.each([
    [404, () => HttpResponse.json({ message: 'Técnico no encontrado.' }, { status: 404 }), 'Técnico no encontrado.'],
    [400, () => new HttpResponse('boom', { status: 400 }), 'Error al comprobar las reparaciones del técnico.'],
  ])('fallo de la comprobación con %s: texto inline, sin aviso ni confirmación', async (_codigo, respuesta, texto) => {
    server.use(http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', respuesta))
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Eliminar' }))
    expect(await screen.findByText(texto)).toBe(linea())
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('la línea inline se vacía al empezar cualquier acción (G9)', async () => {
    server.use(
      http.patch('*/api/usuarios/tecnicos/:idTec/:accion', () => new HttpResponse('boom', { status: 400 })),
      // La comprobación no responde: se ve que la línea ya se ha vaciado antes de saber el resultado.
      http.get('*/api/usuarios/tecnicos/:idTec/tiene-reparaciones', async () => { await delay('infinite'); return HttpResponse.json({ value: false }) }),
    )
    montar()
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Desactivar acceso' }))
    expect(await screen.findByText('Error al cambiar el estado del técnico.')).toBe(linea())
    await userEvent.click(within(fila('tecnico-b')).getByRole('button', { name: 'Eliminar' }))
    expect(linea()).toBeEmptyDOMElement()
    await userEvent.click(botonRegistrar())
    expect(linea()).toHaveTextContent('Todos los campos son obligatorios.')
  })
  it('error de carga: "Error al cargar los usuarios." en la línea, tabla vacía con su placeholder y sin diálogo', async () => {
    server.use(http.get('*/api/usuarios/tecnicos', () => new HttpResponse('boom', { status: 500 })))
    montar()
    expect(await screen.findByText('Error al cargar los usuarios.')).toBe(linea())
    expect(screen.getByText('No hay contenido en la tabla')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('una escritura con éxito marca como caducados los técnicos del resto de la web', async () => {
    server.use(http.patch('*/api/usuarios/tecnicos/:idTec/:accion', () => new HttpResponse(null, { status: 204 })))
    const qc = crearQueryClient({ retry: false })
    qc.setQueryData(['tecnicos'], [])
    qc.setQueryData(['tecnicos', 'activos'], [])
    qc.setQueryData(['clientes'], [])
    montar({ queryClient: qc })
    await screen.findByText('tecnico-a')
    await userEvent.click(within(fila('tecnico-a')).getByRole('button', { name: 'Desactivar acceso' }))
    await waitFor(() => expect(qc.getQueryState(['tecnicos'])?.isInvalidated).toBe(true))
    expect(qc.getQueryState(['tecnicos', 'activos'])?.isInvalidated).toBe(true)
    expect(qc.getQueryState(['clientes'])?.isInvalidated).toBe(false)
  })
  it('"Cerrar" vuelve a la ruta de volverA sin preguntar aunque haya texto', async () => {
    const { router } = renderConRouter(
      [
        { path: '/gestion/tecnicos', element: <TecnicosPage /> },
        { path: '/stock/pedidos', element: <p>PEDIDOS</p> },
        { path: '/reparaciones', element: <p>REPARACIONES</p> },
      ],
      { sesion: SESION_ADMIN, ruta: '/reparaciones' },
    )
    await act(() => router.navigate('/gestion/tecnicos', { state: { volverA: '/stock/pedidos' } }))
    await screen.findByText('tecnico-a')
    await userEvent.type(screen.getByLabelText('Nombre del técnico'), 'tecnico-c')
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(await screen.findByText('PEDIDOS')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/stock/pedidos')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('"Cerrar" sin volverA (URL tecleada) va a /reparaciones', async () => {
    const { router } = renderConRouter(
      [
        { path: '/gestion/tecnicos', element: <TecnicosPage /> },
        { path: '/reparaciones', element: <p>REPARACIONES</p> },
      ],
      { sesion: SESION_ADMIN, ruta: '/gestion/tecnicos' },
    )
    await screen.findByText('tecnico-a')
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(await screen.findByText('REPARACIONES')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/reparaciones')
  })
})
```

Run: `npx vitest run src/modules/gestion/tecnicos/TecnicosPage.test.tsx`
Expected: FAIL, `Failed to resolve import "./TecnicosPage"`.

- [ ] **Step 2: Implementar la página**

`src/modules/gestion/tecnicos/TecnicosPage.tsx`:

```tsx
import { useCallback, useId, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { Usuario } from '@/shared/api/client'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Button } from '@/shared/ui/button'
import { ComboNavy, type OpcionCombo } from '@/shared/ui/ComboNavy'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { DataTable } from '@/shared/ui/DataTable'
import { Input } from '@/shared/ui/input'
import { useUsuariosTecnicos } from '../api'
import { rutaVolverA } from '../navegacion'
import { consultarTieneReparaciones, useCambiarActivo, useEliminar, useRegistrar } from './api'
import { columnasTecnicos } from './columnas'
import { mensajeInline } from './errores'
import {
  MSG_ERROR_CARGA, MSG_ERROR_COMPROBAR, MSG_ERROR_ELIMINAR, MSG_ERROR_ESTADO, MSG_ERROR_REGISTRO, TEXTO_VACIO_TABLA, TITULO_ELIMINAR,
  TITULO_NO_ELIMINAR, textoEliminar, textoNoEliminar,
} from './textos'
import { cuerpoAlta, duplicadosEnVivo, rolDe, ROLES, validarAlta, type DatosAlta } from './validacion'

const FORMULARIO_VACIO: DatosAlta = { nombreTecnico: '', nombreUsuario: '', password: '', confirmar: '', rol: 'TECNICO' }
const OPCIONES_ROL: OpcionCombo[] = ROLES.map((r) => ({ valor: r, etiqueta: r }))
const SIN_USUARIOS: Usuario[] = []

type CampoTexto = 'nombreTecnico' | 'nombreUsuario' | 'password' | 'confirmar'
/** Fila de campos de RegisterView.fxml :32-77 (etiqueta, prompt y tipo). Las contraseñas no llevan ojo (PasswordField). */
const CAMPOS: { clave: CampoTexto; etiqueta: string; placeholder: string; tipo: 'text' | 'password'; autoComplete: string }[] = [
  { clave: 'nombreTecnico', etiqueta: 'Nombre del técnico', placeholder: 'Nombre visible en reparaciones', tipo: 'text', autoComplete: 'off' },
  { clave: 'nombreUsuario', etiqueta: 'Nombre de usuario', placeholder: 'Credencial de login', tipo: 'text', autoComplete: 'off' },
  { clave: 'password', etiqueta: 'Contraseña', placeholder: 'Contraseña', tipo: 'password', autoComplete: 'new-password' },
  { clave: 'confirmar', etiqueta: 'Confirmar', placeholder: 'Repite la contraseña', tipo: 'password', autoComplete: 'new-password' },
]
/** Estilo inline común de los cuatro campos: blanco, borde #D4D8DE, radio 8, padding 10 12, 13 px, texto #2C3B54,
 *  prompt #A0A8B4. El `md:text-[13px]` pisa el `md:text-sm` del Input de shadcn. */
const CLASE_CAMPO = 'h-auto rounded-lg border-borde-input bg-superficie px-3 py-2.5 text-[13px] md:text-[13px] text-azul-medio placeholder:text-texto-suave'

/** "Gestionar técnicos" (spec 6, §6.1): calco de RegisterView.fxml y RegisterController de hotfix/0.16.3 como página del
 *  shell (G2). La línea de error inline recoge la validación del alta y los fallos de carga, candado y borrado; se vacía al
 *  empezar cada acción y enseña el message del servidor en 404/409/422 (G9). Sin sondeo, sin CSV, sin ordenación. */
export function TecnicosPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { mostrarAviso } = useAlerta()
  const idBase = useId()
  const { data: usuarios = SIN_USUARIOS, isError, errorUpdatedAt } = useUsuariosTecnicos()
  const registrar = useRegistrar()
  const { mutate: mutarActivo } = useCambiarActivo()
  const { mutate: mutarEliminar } = useEliminar()
  const [form, setForm] = useState<DatosAlta>(FORMULARIO_VACIO)
  const [error, setError] = useState<string | null>(null)
  const [errorCargaVisto, setErrorCargaVisto] = useState(0)
  const [seleccionada, setSeleccionada] = useState<string | null>(null)
  const [aEliminar, setAEliminar] = useState<Usuario | null>(null)

  // Cada fallo de carga (el inicial o la recarga tras una escritura) trae un errorUpdatedAt nuevo: se pinta
  // "Error al cargar los usuarios." en la línea aunque una acción la hubiera vaciado. Patrón "ajustar estado al cambiar
  // una prop" durante el render (como useErrorServidor), sin useEffect + setState.
  if (isError && errorUpdatedAt !== errorCargaVisto) {
    setErrorCargaVisto(errorUpdatedAt)
    setError(MSG_ERROR_CARGA)
  }

  const dup = duplicadosEnVivo(usuarios, form.nombreTecnico, form.nombreUsuario)
  const hayDuplicado = dup.tecnico !== null || dup.usuario !== null

  const alternarActivo = useCallback(
    (u: Usuario) => {
      setError(null)
      mutarActivo({ idTec: u.idTec, activar: !u.activo }, { onError: (e) => setError(mensajeInline(e, MSG_ERROR_ESTADO, [404])) })
    },
    [mutarActivo],
  )

  /** Papelera (RegisterController :220-248): comprueba en cada clic; con datos asociados solo informa, sin ellos confirma. */
  const pedirEliminar = useCallback(
    async (u: Usuario) => {
      setError(null)
      let tiene: boolean
      try {
        tiene = await consultarTieneReparaciones(u.idTec)
      } catch (e) {
        setError(mensajeInline(e, MSG_ERROR_COMPROBAR, [404]))
        return
      }
      if (tiene) mostrarAviso(TITULO_NO_ELIMINAR, textoNoEliminar(u.nombreTecnico))
      else setAEliminar(u)
    },
    [mostrarAviso],
  )

  const columnas = useMemo(
    () => columnasTecnicos({ onToggle: alternarActivo, onEliminar: (u) => { void pedirEliminar(u) } }),
    [alternarActivo, pedirEliminar],
  )

  function cambiar(clave: CampoTexto, valor: string) {
    setForm((f) => ({ ...f, [clave]: valor }))
  }

  function onRegistrar() {
    setError(null)
    const fallo = validarAlta(form)
    if (fallo !== null) {
      setError(fallo)
      return
    }
    registrar.mutate(cuerpoAlta(form), {
      onSuccess: () => setForm(FORMULARIO_VACIO),
      onError: (e) => setError(mensajeInline(e, MSG_ERROR_REGISTRO, [409, 422])),
    })
  }

  function confirmarEliminar() {
    const u = aEliminar
    setAEliminar(null)
    if (u === null) return
    mutarEliminar({ idTec: u.idTec, idUsu: u.idUsu }, { onError: (e) => setError(mensajeInline(e, MSG_ERROR_ELIMINAR, [404, 409])) })
  }

  return (
    <div className="flex min-h-full flex-col bg-fondo-gestion">
      <div className="flex flex-col gap-4 px-12 pt-7 pb-5">
        <div className="flex items-center gap-3.5">
          <img src="/logo_inicio_sesion.png" alt="" className="h-[46px] w-[46px] object-contain" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-[18px] font-bold text-azul-medio">Gestión de usuarios</h1>
            <p className="text-[12px] text-azul-gris">Registra o elimina accesos al sistema</p>
          </div>
        </div>

        <div className="flex gap-3">
          {CAMPOS.map((c) => {
            const id = `${idBase}-${c.clave}`
            const aviso = c.clave === 'nombreTecnico' ? dup.tecnico : c.clave === 'nombreUsuario' ? dup.usuario : null
            return (
              <div key={c.clave} className="flex min-w-0 flex-1 flex-col gap-[5px]">
                <label htmlFor={id} className="text-[11px] font-bold text-azul-gris">
                  {c.etiqueta}
                </label>
                <Input
                  id={id}
                  type={c.tipo}
                  autoComplete={c.autoComplete}
                  value={form[c.clave]}
                  onChange={(e) => cambiar(c.clave, e.target.value)}
                  placeholder={c.placeholder}
                  className={CLASE_CAMPO}
                />
                {aviso !== null && <p className="text-[10px] text-texto-error">{aviso}</p>}
              </div>
            )
          })}
        </div>

        <div className="flex items-center gap-3">
          {/* Ocupa su hueco aunque esté vacía (lblError visible=false pero managed) y empuja el combo y el botón a la derecha. */}
          <p role="alert" className="min-h-4 min-w-0 flex-1 text-[11px] text-texto-error">
            {error ?? ''}
          </p>
          <ComboNavy
            valor={form.rol}
            opciones={OPCIONES_ROL}
            onChange={(v) => setForm((f) => ({ ...f, rol: rolDe(v) }))}
            textoVacio="TECNICO"
            ancho={130}
            aria-label="Rol"
          />
          <Button
            type="button"
            disabled={hayDuplicado || registrar.isPending}
            onClick={onRegistrar}
            className="h-auto rounded-3xl bg-azul-noche px-6 py-2.5 text-[13px] font-bold text-crema hover:bg-azul-noche-hover"
          >
            Registrar técnico
          </Button>
        </div>
        <hr className="border-borde-input" />
      </div>

      <div className="flex flex-1 flex-col gap-2.5 px-12 pt-4">
        <h2 className="text-[13px] font-bold text-azul-medio">Técnicos registrados</h2>
        <DataTable
          columns={columnas}
          data={usuarios}
          vacio={TEXTO_VACIO_TABLA}
          getRowId={(u) => String(u.idTec)}
          seleccionada={seleccionada}
          onSeleccionar={setSeleccionada}
          ordenacion={false}
          altoFila={35}
        />
      </div>

      <div className="flex justify-end px-12 pt-3 pb-5">
        <button type="button" onClick={() => navigate(rutaVolverA(location.state))} className="cursor-pointer text-[12px] text-azul-gris">
          Cerrar
        </button>
      </div>

      <ConfirmDialog
        abierto={aEliminar !== null}
        titulo={TITULO_ELIMINAR}
        descripcion={aEliminar !== null ? textoEliminar(aEliminar.nombreTecnico) : ''}
        textoAccion="Eliminar"
        onCancelar={() => setAEliminar(null)}
        onConfirmar={confirmarEliminar}
      />
    </div>
  )
}
```

Run: `npx vitest run src/modules/gestion/tecnicos/TecnicosPage.test.tsx`
Expected: PASS, 25 tests.

- [ ] **Step 3: Ruta**

En `src/app/router.tsx`, tras `import { RequiereAdmin } from '@/modules/gestion/rutas'` (lo añadió la Task 7 tras el de `ClientesPage`; así los imports de `@/modules/gestion` siguen en orden alfabético):

```tsx
import { TecnicosPage } from '@/modules/gestion/tecnicos/TecnicosPage'
```

Y dentro del bloque `<RequiereAdmin />` que dejó la Task 7:

```tsx
              { path: '/gestion/tecnicos', element: <TecnicosPage /> },
```

(la de `/gestion/logs` sigue con `PendienteDeMigrar` hasta la Task 11).

- [ ] **Step 4: Comprobar**

```bash
npm run check && npm run build
```

Expected: lint, typecheck y tests en verde, **1399 tests** (1374 + 25 de `TecnicosPage`); `vite build` sin errores.

- [ ] **Step 5: Commit**

```bash
git add src/modules/gestion/tecnicos/TecnicosPage.tsx src/modules/gestion/tecnicos/TecnicosPage.test.tsx src/app/router.tsx
git commit -m "feat(gestion): pagina de tecnicos con alta y duplicados en vivo, candado, borrado con comprobacion y confirmacion, errores inline y cerrar a la vista de origen"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

---

---

## Task 10: Web — filtros, columnas y consultas del log de actividad

**Files:**
- Create: `src/modules/gestion/logs/filtros.ts`, `src/modules/gestion/logs/filtros.test.ts`
- Create: `src/modules/gestion/logs/columnas.tsx`, `src/modules/gestion/logs/columnas.test.tsx`
- Create: `src/modules/gestion/logs/api.ts`, `src/modules/gestion/logs/api.test.tsx`
- Modify: `.gitignore:2` (`logs` → `/logs`, Step 15)

**Interfaces:**
- Consumes: `api`, `LogActividad` de `@/shared/api/client` (alias de la Task 6: `{ idLog, fecha, nombreUsuario, accion, detalle: string | null, motivo: string | null }`); `formatear`, `FMT_FECHA_LOG` de `@/shared/lib/fechas` (Task 6: patrón `'dd/MM/yyyy HH:mm:ss'` con segundos); `DataTable` de `@/shared/ui/DataTable` (`shared/ui/DataTable.tsx:129`); `crearQueryClient` de `@/shared/api/queryClient` (`queryClient.ts:30`); `server` de `@/test/server`. Contrato: `GET /api/logs` = `getAll_6` (`schema.d.ts:1895-1910` y `:6605-6629` en `main`; tras la regeneración de la Task 6 su `query` gana `limite?: number`), `GET /api/logs/acciones` (ruta nueva del servidor, Task 4; respuesta `string[]`).
- Produces:

```ts
// filtros.ts
export type FiltrosLogs = { texto: string; accion: string | null; usuario: string | null; desde: string; hasta: string }
export const FILTROS_LOGS_VACIOS: FiltrosLogs
export const LIMITE_LOGS = 1000
export const MSG_TOPE = 'Mostrando los 1.000 registros más recientes; acota con los filtros.'
export const TEXTO_VACIO_LOGS = 'No hay contenido en la tabla'
export type QueryLogs = { accion?: string; tecnico?: string; desde?: string; hasta?: string; limite: number }
export function queryLogs(f: FiltrosLogs): QueryLogs
export function coincideTexto(log: LogActividad, texto: string | null | undefined): boolean
export function aplicarBuscador(logs: LogActividad[], texto: string): LogActividad[]
// columnas.tsx
export const COLUMNAS_LOGS: ColumnDef<LogActividad>[]
export function textoDetalle(log: LogActividad): string
// api.ts
export const CLAVE_LOGS = ['logs'] as const
export const CLAVE_ACCIONES_LOG = ['logs', 'acciones'] as const
export function useLogs(q: QueryLogs): UseQueryResult<LogActividad[]>
export function useAccionesLog(): UseQueryResult<string[]>
```

**Supuestos:** la Task 6 ya dejó en la rama `LogActividad` en `client.ts`, `FMT_FECHA_LOG` en `fechas.ts` y el contrato regenerado con `limite` en `getAll_6` y la ruta `"/api/logs/acciones"` con `get` → `string[]`, y con `detalle`/`motivo` como `string | null` (`@Schema(nullable = true)` de la Task 5). Si el nombre de la operación o de la ruta nueva difiere, manda el contrato y se anota. Las cadenas de fecha del servidor son `LocalDateTime` ISO sin zona (UTC), como en el resto de la web.

> Búsqueda previa: `src/modules/gestion/logs/` no existe (`ls src/modules/gestion` → solo `clientes/` en `main`, más lo que creen las Tasks 7-9) y no hay ninguna llamada a `/api/logs` fuera de `schema.d.ts` (`grep -rn "/api/logs" src --include=*.ts --include=*.tsx | grep -v schema.d.ts` → vacío). `coincideTexto` es el calco de `LogController.coincideTexto` (`hotfix/0.16.3`, `controllers/LogController.java:245-255`): `isBlank` → coincide; `toLowerCase().trim()` del texto; `contains` sobre usuario, acción y detalle (no motivo ni fecha), con `null` que no coincide. Los nueve casos son los de `LogControllerTest` (inventario-logs §16), con datos sintéticos.

- [ ] **Step 1: Comprobar rama y punto de partida**

```bash
cd C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git branch --show-current
ls src/modules/gestion/logs 2>/dev/null || echo "no existe"
grep -n "FMT_FECHA_LOG" src/shared/lib/fechas.ts
grep -n "export type LogActividad" src/shared/api/client.ts
grep -n '"/api/logs/acciones"\|limite?: number' src/shared/api/schema.d.ts
```

Expected: `feature/web-gestion`; `no existe`; una línea con `export const FMT_FECHA_LOG: Patron = 'dd/MM/yyyy HH:mm:ss'`; una línea con el alias `LogActividad`; la ruta `"/api/logs/acciones": {` y al menos una línea `limite?: number;` (la de `getAll_6`). Si falta alguna, la Task 6 no está hecha: parar y avisar.

- [ ] **Step 2: Test de los filtros (falla)**

`src/modules/gestion/logs/filtros.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { LogActividad } from '@/shared/api/client'
import {
  aplicarBuscador, coincideTexto, FILTROS_LOGS_VACIOS, LIMITE_LOGS, MSG_TOPE, queryLogs, TEXTO_VACIO_LOGS, type FiltrosLogs,
} from './filtros'

/** Mismo log de ejemplo que `LogControllerTest.logDeEjemplo()` del JavaFX, con datos sintéticos. */
const log = (o: Partial<LogActividad> = {}): LogActividad => ({
  idLog: 1,
  fecha: '2026-06-10T10:30:00',
  nombreUsuario: 'usuario-a',
  accion: 'CREAR_REPARACION',
  detalle: 'ID_REP: 5, IMEI: 000000000000000, ID_TEC: 2',
  motivo: null,
  ...o,
})

describe('constantes', () => {
  it('filtros vacíos, límite de 1.000 filas y textos exactos (spec §6.2)', () => {
    expect(FILTROS_LOGS_VACIOS).toEqual({ texto: '', accion: null, usuario: null, desde: '', hasta: '' })
    expect(LIMITE_LOGS).toBe(1000)
    expect(MSG_TOPE).toBe('Mostrando los 1.000 registros más recientes; acota con los filtros.')
    expect(TEXTO_VACIO_LOGS).toBe('No hay contenido en la tabla')
  })
})

describe('queryLogs (parámetros de GET /api/logs)', () => {
  it('sin filtros solo lleva el límite', () => {
    expect(queryLogs(FILTROS_LOGS_VACIOS)).toEqual({ limite: 1000 })
  })
  it('lleva acción, el usuario como `tecnico` y las dos fechas; el buscador no viaja', () => {
    const f: FiltrosLogs = { texto: 'imei', accion: 'LOGIN', usuario: 'usuario-a', desde: '2026-09-01', hasta: '2026-09-26' }
    expect(queryLogs(f)).toEqual({ limite: 1000, accion: 'LOGIN', tecnico: 'usuario-a', desde: '2026-09-01', hasta: '2026-09-26' })
  })
  it('omite cada vacío por separado (null o cadena vacía)', () => {
    expect(queryLogs({ ...FILTROS_LOGS_VACIOS, accion: 'LOGIN' })).toEqual({ limite: 1000, accion: 'LOGIN' })
    expect(queryLogs({ ...FILTROS_LOGS_VACIOS, accion: '' })).toEqual({ limite: 1000 })
    expect(queryLogs({ ...FILTROS_LOGS_VACIOS, usuario: 'usuario-b' })).toEqual({ limite: 1000, tecnico: 'usuario-b' })
    expect(queryLogs({ ...FILTROS_LOGS_VACIOS, hasta: '2026-09-26' })).toEqual({ limite: 1000, hasta: '2026-09-26' })
  })
})

describe('coincideTexto (los 9 casos de LogControllerTest del JavaFX)', () => {
  it('texto vacío: coincide siempre', () => {
    expect(coincideTexto(log(), '')).toBe(true)
  })
  it('texto null: coincide siempre', () => {
    expect(coincideTexto(log(), null)).toBe(true)
  })
  it('texto solo espacios: coincide siempre', () => {
    expect(coincideTexto(log(), '   ')).toBe(true)
  })
  it('coincide en el usuario ignorando mayúsculas', () => {
    expect(coincideTexto(log(), 'USUARIO-A')).toBe(true)
  })
  it('coincide en la acción ignorando mayúsculas', () => {
    expect(coincideTexto(log(), 'crear_reparacion')).toBe(true)
  })
  it('coincide en el detalle por IMEI', () => {
    expect(coincideTexto(log(), '000000000000000')).toBe(true)
  })
  it('coincide con una parte del detalle ("ID_REP")', () => {
    expect(coincideTexto(log(), 'ID_REP')).toBe(true)
  })
  it('"no_existe" no coincide en ningún campo', () => {
    expect(coincideTexto(log(), 'no_existe')).toBe(false)
  })
  it('campos null no lanzan y no coinciden', () => {
    const sinCampos = { ...log(), nombreUsuario: null, accion: null, detalle: null } as unknown as LogActividad
    expect(() => coincideTexto(sinCampos, 'algo')).not.toThrow()
    expect(coincideTexto(sinCampos, 'algo')).toBe(false)
  })
})

describe('coincideTexto: lo que no mira (calco, inventario-logs §5)', () => {
  it('no busca en el motivo ni en la fecha', () => {
    const conMotivo = log({ motivo: 'Duplicada' })
    expect(coincideTexto(conMotivo, 'duplicada')).toBe(false)
    expect(coincideTexto(conMotivo, '2026-06-10')).toBe(false)
    expect(coincideTexto(conMotivo, '10/06/2026')).toBe(false)
  })
  it('recorta el texto buscado antes de comparar', () => {
    expect(coincideTexto(log(), '  usuario-a  ')).toBe(true)
  })
})

describe('aplicarBuscador', () => {
  it('con texto en blanco devuelve la misma lista', () => {
    const lista = [log(), log({ idLog: 2 })]
    expect(aplicarBuscador(lista, '  ')).toBe(lista)
  })
  it('filtra con coincideTexto y conserva el orden del servidor', () => {
    const a = log({ idLog: 3, nombreUsuario: 'usuario-a' })
    const b = log({ idLog: 2, nombreUsuario: 'usuario-b', detalle: 'ID_REP: 7' })
    const c = log({ idLog: 1, nombreUsuario: 'usuario-b', accion: 'LOGIN', detalle: '' })
    expect(aplicarBuscador([a, b, c], 'usuario-b').map((l) => l.idLog)).toEqual([2, 1])
  })
})
```

- [ ] **Step 3: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/logs/filtros.test.ts`
Expected: FAIL, `Failed to resolve import "./filtros"`.

- [ ] **Step 4: Implementar los filtros**

`src/modules/gestion/logs/filtros.ts`:

```ts
import type { LogActividad } from '@/shared/api/client'

/** Filtros del visor "Log de actividad" (LogController :23-40, :79-90 de `hotfix/0.16.3`). `texto` es el buscador en
 *  memoria; `accion` y `usuario`, lo elegido en "Acción..." y "Técnico..." (null = sin filtro); `desde`/`hasta`, los
 *  valores de RangoFechas ('yyyy-MM-dd' o ''). */
export type FiltrosLogs = { texto: string; accion: string | null; usuario: string | null; desde: string; hasta: string }

/** Al abrir y tras "Limpiar filtros" (:229-238): todo vacío. */
export const FILTROS_LOGS_VACIOS: FiltrosLogs = { texto: '', accion: null, usuario: null, desde: '', hasta: '' }

/** G3: la web pide las 1.000 filas más recientes (el servidor admite 1..5000 en `limite`; sin él, todo, como el JavaFX). */
export const LIMITE_LOGS = 1000
export const MSG_TOPE = 'Mostrando los 1.000 registros más recientes; acota con los filtros.'
/** Placeholder por defecto del TableView (no lo fija LogView.fxml); a confirmar con la captura `gestion-logs-vacio.png`. */
export const TEXTO_VACIO_LOGS = 'No hay contenido en la tabla'

/** Parámetros de GET /api/logs (`getAll_6`): los nombres del servidor. El usuario viaja como `tecnico`, igual que en el
 *  LogDAO del JavaFX (:14-18), porque el servidor filtra por `NOMBRE_USUARIO` con ese nombre de parámetro. */
export type QueryLogs = { accion?: string; tecnico?: string; desde?: string; hasta?: string; limite: number }

/** Omite lo vacío (el JavaFX solo concatena los que no son null) y lleva siempre el límite. El buscador no viaja. */
export function queryLogs(f: FiltrosLogs): QueryLogs {
  const q: QueryLogs = { limite: LIMITE_LOGS }
  if (f.accion) q.accion = f.accion
  if (f.usuario) q.tecnico = f.usuario
  if (f.desde) q.desde = f.desde
  if (f.hasta) q.hasta = f.hasta
  return q
}

function contiene(campo: string | null | undefined, texto: string): boolean {
  return campo != null && campo.toLowerCase().includes(texto)
}

/** Calco de `LogController.coincideTexto` (:245-255): texto null o en blanco coincide siempre; si no, `contains` sin
 *  mayúsculas del texto recortado sobre usuario, acción o detalle (ni motivo ni fecha). Un campo null no coincide. */
export function coincideTexto(log: LogActividad, texto: string | null | undefined): boolean {
  if (texto == null || texto.trim() === '') return true
  const t = texto.toLowerCase().trim()
  return contiene(log.nombreUsuario, t) || contiene(log.accion, t) || contiene(log.detalle, t)
}

/** La FilteredList del JavaFX (:79-82): en memoria, sobre lo cargado, sin reordenar. En blanco devuelve la misma lista. */
export function aplicarBuscador(logs: LogActividad[], texto: string): LogActividad[] {
  return texto.trim() === '' ? logs : logs.filter((l) => coincideTexto(l, texto))
}
```

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/logs/filtros.test.ts`
Expected: PASS, 17 tests.

- [ ] **Step 6: Test de las columnas y del detalle (falla)**

`src/modules/gestion/logs/columnas.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { LogActividad } from '@/shared/api/client'
import { DataTable } from '@/shared/ui/DataTable'
import { COLUMNAS_LOGS, textoDetalle } from './columnas'

const log = (o: Partial<LogActividad> = {}): LogActividad => ({
  idLog: 1,
  fecha: '2026-06-09T23:30:05',
  nombreUsuario: 'usuario-a',
  accion: 'CREAR_ASIGNACION',
  detalle: 'ID_REP: R1, IMEI: 000000000000000, MODELO: X, TECNICO: tecnico-a',
  motivo: null,
  ...o,
})

function celdas(l: LogActividad): (string | null)[] {
  render(<DataTable columns={COLUMNAS_LOGS} data={[l]} vacio="vacío" />)
  const fila = screen.getAllByRole('row')[1]
  return within(fila).getAllByRole('cell').map((c) => c.textContent)
}

describe('COLUMNAS_LOGS (LogView.fxml :48-59)', () => {
  it('Fecha, Usuario, Acción y Detalle con los prefWidth del JavaFX (Detalle es la columna flexible)', () => {
    expect(COLUMNAS_LOGS.map((c) => c.header)).toEqual(['Fecha', 'Usuario', 'Acción', 'Detalle'])
    expect(COLUMNAS_LOGS.map((c) => c.size)).toEqual([150, 80, 180, 400])
  })
  it('fecha "dd/MM/yyyy HH:mm:ss" en hora de Madrid (verano, +2 h); usuario, acción y detalle tal cual', () => {
    expect(celdas(log())).toEqual(['10/06/2026 01:30:05', 'usuario-a', 'CREAR_ASIGNACION', 'ID_REP: R1, IMEI: 000000000000000, MODELO: X, TECNICO: tecnico-a'])
  })
  it('en invierno la diferencia es de 1 h y los segundos se conservan', () => {
    expect(celdas(log({ fecha: '2026-01-15T23:00:09' }))[0]).toBe('16/01/2026 00:00:09')
  })
  it('detalle null: celda vacía; el detalle va en una sola línea con elipsis', () => {
    expect(celdas(log({ detalle: null }))[3]).toBe('')
    const { container } = render(<DataTable columns={COLUMNAS_LOGS} data={[log()]} vacio="vacío" />)
    const detalle = container.querySelectorAll('tbody tr')[0].querySelectorAll('td')[3].firstElementChild
    expect(detalle).toHaveClass('block', 'truncate')
  })
})

describe('textoDetalle (doble clic, LogController :93-103)', () => {
  it('sin motivo: el detalle tal cual', () => {
    expect(textoDetalle(log())).toBe('ID_REP: R1, IMEI: 000000000000000, MODELO: X, TECNICO: tecnico-a')
  })
  it('con motivo: línea en blanco y "MOTIVO: …"', () => {
    expect(textoDetalle(log({ detalle: 'ID_REP: R2', motivo: 'Duplicada' }))).toBe('ID_REP: R2\n\nMOTIVO: Duplicada')
  })
  it('motivo en blanco no añade nada; detalle null cuenta como ""', () => {
    expect(textoDetalle(log({ detalle: 'ID_REP: R3', motivo: '   ' }))).toBe('ID_REP: R3')
    expect(textoDetalle(log({ detalle: null, motivo: null }))).toBe('')
    expect(textoDetalle(log({ detalle: null, motivo: 'Error de alta' }))).toBe('\n\nMOTIVO: Error de alta')
  })
})
```

- [ ] **Step 7: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/logs/columnas.test.tsx`
Expected: FAIL, `Failed to resolve import "./columnas"`.

- [ ] **Step 8: Implementar las columnas**

`src/modules/gestion/logs/columnas.tsx`:

```tsx
import type { ColumnDef } from '@tanstack/react-table'
import type { LogActividad } from '@/shared/api/client'
import { FMT_FECHA_LOG, formatear } from '@/shared/lib/fechas'

/** Columnas de `tablaLogs` (LogView.fxml :48-59) con sus prefWidth (150/80/180/400). La página las pinta con el ajuste
 *  'estirar' de DataTable, como las otras tablas con CONSTRAINED_RESIZE_POLICY (Asignaciones, Historial): `size` es el
 *  mínimo y el peso, y Detalle, la más ancha, se lleva el grueso del sobrante. Sin ordenación por cabecera (G10): la
 *  página no la activa y el orden es el del servidor (fecha desc, id desc). */
export const COLUMNAS_LOGS: ColumnDef<LogActividad>[] = [
  { id: 'fecha', header: 'Fecha', size: 150, accessorFn: (l) => formatear(l.fecha, FMT_FECHA_LOG) },
  { id: 'usuario', header: 'Usuario', size: 80, accessorFn: (l) => l.nombreUsuario },
  { id: 'accion', header: 'Acción', size: 180, accessorFn: (l) => l.accion },
  {
    id: 'detalle', header: 'Detalle', size: 400, accessorFn: (l) => l.detalle ?? '',
    // Una línea con elipsis, como la celda de texto del TableView; el texto entero sale con doble clic.
    cell: ({ row }) => <span className="block truncate">{row.original.detalle ?? ''}</span>,
  },
]

/** Texto del popup "Detalle del log" (LogController :96-100): detalle (o "") y, si el motivo no está en blanco,
 *  "\n\nMOTIVO: " + motivo. */
export function textoDetalle(log: LogActividad): string {
  const texto = log.detalle ?? ''
  return log.motivo != null && log.motivo.trim() !== '' ? `${texto}\n\nMOTIVO: ${log.motivo}` : texto
}
```

- [ ] **Step 9: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/logs/columnas.test.tsx`
Expected: PASS, 7 tests.

- [ ] **Step 10: Test de las consultas (falla)**

`src/modules/gestion/logs/api.test.tsx`:

```tsx
import { focusManager, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import type { LogActividad } from '@/shared/api/client'
import { PermisoError } from '@/shared/api/errors'
import { crearQueryClient } from '@/shared/api/queryClient'
import { server } from '@/test/server'
import { CLAVE_ACCIONES_LOG, CLAVE_LOGS, useAccionesLog, useLogs } from './api'
import type { QueryLogs } from './filtros'

const LOG: LogActividad = {
  idLog: 1, fecha: '2026-09-25T08:15:30', nombreUsuario: 'usuario-a', accion: 'LOGIN', detalle: '', motivo: null,
}

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}

/** Parámetros de cada GET /api/logs, como objeto plano (en el orden en que llegan). */
function registrarLogs(respuesta: () => Response = () => HttpResponse.json([LOG])) {
  const peticiones: Record<string, string>[] = []
  server.use(
    http.get('*/api/logs', ({ request }) => {
      // Sin `Object.fromEntries(searchParams)`: el `lib` de tsconfig.app.json no trae DOM.Iterable (tsc -b daría TS2769).
      const params: Record<string, string> = {}
      new URL(request.url).searchParams.forEach((valor, clave) => { params[clave] = valor })
      peticiones.push(params)
      return respuesta()
    }),
  )
  return peticiones
}

afterEach(() => {
  focusManager.setFocused(undefined)
})

describe('useLogs (GET /api/logs, contrato getAll_6)', () => {
  it('manda limite, accion, tecnico, desde y hasta con los nombres del contrato', async () => {
    const peticiones = registrarLogs()
    const { wrapper } = envoltorio()
    const q: QueryLogs = { limite: 1000, accion: 'LOGIN', tecnico: 'usuario-a', desde: '2026-09-01', hasta: '2026-09-26' }
    const { result } = renderHook(() => useLogs(q), { wrapper })
    await waitFor(() => expect(result.current.data).toEqual([LOG]))
    expect(peticiones).toEqual([{ limite: '1000', accion: 'LOGIN', tecnico: 'usuario-a', desde: '2026-09-01', hasta: '2026-09-26' }])
  })
  it('sin filtros solo manda el límite; cambiar un filtro cambia la clave y relanza la carga', async () => {
    const peticiones = registrarLogs()
    const { qc, wrapper } = envoltorio()
    const { result, rerender } = renderHook(({ q }: { q: QueryLogs }) => useLogs(q), { wrapper, initialProps: { q: { limite: 1000 } as QueryLogs } })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    rerender({ q: { limite: 1000, accion: 'LOGIN' } })
    await waitFor(() => expect(peticiones).toHaveLength(2))
    expect(peticiones).toEqual([{ limite: '1000' }, { limite: '1000', accion: 'LOGIN' }])
    expect(qc.getQueryCache().find({ queryKey: [...CLAVE_LOGS, { limite: 1000, accion: 'LOGIN' }] })).toBeDefined()
  })
  it('un error queda en la consulta sin diálogo genérico (la página pone su propio texto)', async () => {
    registrarLogs(() => new HttpResponse(null, { status: 403 }))
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useLogs({ limite: 1000 }), { wrapper })
    await waitFor(() => expect(result.current.error).toBeInstanceOf(PermisoError))
    expect(qc.getQueryCache().find({ queryKey: [...CLAVE_LOGS, { limite: 1000 }] })?.meta).toEqual({ silenciarError: true })
  })
  it('no recarga al volver a la pestaña (sin refresco automático, spec §7)', async () => {
    const peticiones = registrarLogs()
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useLogs({ limite: 1000 }), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
    })
    await new Promise((r) => setTimeout(r, 30))
    expect(peticiones).toHaveLength(1)
  })
})

describe('useAccionesLog (GET /api/logs/acciones, G4)', () => {
  it('lee la lista de acciones del servidor tal cual, bajo su propia clave y silenciada', async () => {
    let pedidas = 0
    server.use(http.get('*/api/logs/acciones', () => { pedidas++; return HttpResponse.json(['CREAR_ASIGNACION', 'LOGIN']) }))
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useAccionesLog(), { wrapper })
    await waitFor(() => expect(result.current.data).toEqual(['CREAR_ASIGNACION', 'LOGIN']))
    expect(pedidas).toBe(1)
    expect(qc.getQueryCache().find({ queryKey: CLAVE_ACCIONES_LOG })?.meta).toEqual({ silenciarError: true })
  })
})
```

- [ ] **Step 11: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/logs/api.test.tsx`
Expected: FAIL, `Failed to resolve import "./api"`.

- [ ] **Step 12: Implementar las consultas**

`src/modules/gestion/logs/api.ts`:

```ts
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { api, type LogActividad } from '@/shared/api/client'
import type { QueryLogs } from './filtros'

export const CLAVE_LOGS = ['logs'] as const
export const CLAVE_ACCIONES_LOG = ['logs', 'acciones'] as const

/** Sin sondeo ni recarga al volver a la pestaña o a la red (calco: el visor del JavaFX solo recarga con "Actualizar" o al
 *  tocar un filtro, spec §7). */
const SIN_REFRESCO = { refetchOnWindowFocus: false, refetchOnReconnect: false } as const

/** GET /api/logs con los filtros de servidor y `limite` (G3). La clave lleva la query entera: cambiar un filtro cambia la
 *  clave y dispara una carga; "Limpiar filtros" la cambia una sola vez. `silenciarError`: el fallo lo enseña la página con
 *  "Error al cargar los logs: …" (LogController :223-225) y no debe salir además el diálogo genérico. */
export function useLogs(q: QueryLogs): UseQueryResult<LogActividad[]> {
  return useQuery({
    queryKey: [...CLAVE_LOGS, q],
    queryFn: async () => (await api.GET('/api/logs', { params: { query: q } })).data ?? [],
    ...SIN_REFRESCO,
    meta: { silenciarError: true },
  })
}

/** Lista del filtro "Acción..." (G4: `SELECT DISTINCT ACCION ORDER BY ACCION` en el servidor). Un fallo es silencioso: el
 *  autocompletar queda vacío, como el desplegable de usuarios del JavaFX (:209-211). */
export function useAccionesLog(): UseQueryResult<string[]> {
  return useQuery({
    queryKey: CLAVE_ACCIONES_LOG,
    queryFn: async () => (await api.GET('/api/logs/acciones')).data ?? [],
    ...SIN_REFRESCO,
    meta: { silenciarError: true },
  })
}
```

- [ ] **Step 13: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/logs`
Expected: PASS, 3 ficheros, 29 tests (17 + 7 + 5).

- [ ] **Step 14: Lint y tipos**

```bash
npm run lint && npx tsc -b
```

Expected: lint sin errores ni warnings nuevos; `tsc -b` sin salida. Si `tsc` marca `params.query.limite` o la ruta `/api/logs/acciones` como inexistentes, el contrato no está regenerado (Task 6): parar.

- [ ] **Step 15: Anclar `logs` en `.gitignore` y commit**

La l.2 de `.gitignore` es `logs` sin anclar: Git ignora `src/modules/gestion/logs/` entero y el `git add` de abajo falla con "The following paths are ignored by one of your .gitignore files" (vitest, lint y tsc no se enteran; un `git add -A` lo dejaría fuera sin avisar). Se ancla a la raíz (no afecta a los `*.log`):

```bash
grep -n "^logs$" .gitignore
sed -i 's/^logs$/\/logs/' .gitignore
grep -n "logs" .gitignore
git check-ignore src/modules/gestion/logs/api.ts || echo "no ignorado"
```

Expected: el primer `grep` da `2:logs`; el segundo, `2:/logs` (y la línea `*.log` sin cambios); `git check-ignore` no imprime la ruta y sale `no ignorado`.

```bash
git add .gitignore src/modules/gestion/logs/filtros.ts src/modules/gestion/logs/filtros.test.ts src/modules/gestion/logs/columnas.tsx src/modules/gestion/logs/columnas.test.tsx src/modules/gestion/logs/api.ts src/modules/gestion/logs/api.test.tsx
git commit -m "feat(gestion): filtros, columnas y consultas del log de actividad"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 29.

---

## Task 11: Web — página "Ver logs" (`/gestion/logs`)

**Files:**
- Create: `src/modules/gestion/logs/LogsPage.tsx`, `src/modules/gestion/logs/LogsPage.test.tsx`
- Modify: `src/app/router.tsx` — la entrada `{ path: '/gestion/logs', element: <PendienteDeMigrar nombre="Ver logs" /> }` (hoy `router.tsx:78`; tras la Task 7 vive dentro del bloque `{ element: <RequiereAdmin />, children: [...] }`) y el bloque de imports (`router.tsx:1-19`).

**Interfaces:**
- Consumes: de la Task 10, `useLogs`, `useAccionesLog` (`./api`), `COLUMNAS_LOGS`, `textoDetalle` (`./columnas`), `FILTROS_LOGS_VACIOS`, `LIMITE_LOGS`, `MSG_TOPE`, `TEXTO_VACIO_LOGS`, `queryLogs`, `aplicarBuscador`, `FiltrosLogs` (`./filtros`); de la Task 7, `useUsuariosTecnicos(opciones?: { habilitada?: boolean }): UseQueryResult<Usuario[]>` (`../api`) y `rutaVolverA(state: unknown): string` (`../navegacion`); de la Task 6, el token `bg-fondo-gestion`; compartidos: `CampoAutocompletar` (`shared/ui/CampoAutocompletar.tsx:20`: `valor`, `opciones {clave, etiqueta}`, `onElegir`, `onTextoCambiado`, `placeholder`, `aria-label`), `RangoFechas` (`shared/ui/RangoFechas.tsx:8`), `DataTable` (`vacio`, `ordenacion`, `ajuste`, `onAbrir`, `seleccionada`/`onSeleccionar`), `BotonPrimario`/`BotonSecundario` (`shared/ui/Botones.tsx`), `Input` (`shared/ui/input.tsx`), `useAlerta` (`mostrarError`, `mostrarTexto`; `shared/ui/AlertaProvider.tsx:10-12`), `esErrorGestionadoGlobalmente`, `mensajeDeError` (`shared/api/errors.ts:79-89`).
- Produces: `export function LogsPage()`; ruta `/gestion/logs` → `<LogsPage />` bajo `RequiereAdmin`.

**Supuestos:**
- `useUsuariosTecnicos` (Task 7) lleva siempre `meta: { silenciarError: true }` y `refetchOnWindowFocus: false` (lo fija la Task 7; TecnicosPage pinta su propio "Error al cargar los usuarios."): la lista del filtro falla en silencio como pide la spec §6.2. El Step 4 solo lo comprueba con `grep` (no añade nada).
- La Task 7 dejó en el router el bloque `RequiereAdmin` con `/gestion/logs` → `<PendienteDeMigrar nombre="Ver logs" />` y eliminó `/cuenta/cambiar-password`; la Task 9 ya sustituyó `/gestion/tecnicos`. `PendienteDeMigrar` sigue importado por `/estadisticas/*`.
- `CampoAutocompletar` no vuelve a `''` cuando `valor` pasa a `null` (`CampoAutocompletar.tsx:126-134`: "los padres remontan el campo con `key`"): "Limpiar filtros" remonta los dos campos con un contador.
- `useErrorServidor` vive en `modules/almacen/ui` y no se puede importar desde `gestion`; aquí no hace falta (la página no tiene formulario).

> Búsqueda previa: `grep -rn "LogsPage" src` → vacío. Política de errores comprobada en el código: las consultas con `meta.silenciarError` no avisan por el `QueryCache` (`queryClient.ts:39-42`), y `client.ts:84-86` ya enciende el banner (`ConexionError`) y el flujo de sesión caducada (`SesionExpiradaError`) antes de lanzar; por eso la página solo avisa si `!esErrorGestionadoGlobalmente(e)` (mismo criterio que `StockPage.tsx:109`) y no duplica avisos. Para conservar la tabla cuando falla una carga con otra clave (cambio de filtro), la página guarda las últimas filas buenas con el patrón "ajustar el estado durante el render" (el de `CampoAutocompletar.tsx:130-134`), que la regla `react-hooks` 7 admite (un `ref` escrito en render o un `setState` en efecto no).

- [ ] **Step 1: Test de la página (falla)**

`src/modules/gestion/logs/LogsPage.test.tsx`:

```tsx
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Outlet } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import type { LogActividad, Usuario } from '@/shared/api/client'
import { estaConectado } from '@/shared/api/conexion'
import { ExportableProvider, useExportable } from '@/shared/ui/exportable'
import { renderConProviders, renderConRouter, SESION_ADMIN } from '@/test/render'
import { server } from '@/test/server'
import { LogsPage } from './LogsPage'

const log = (o: Partial<LogActividad>): LogActividad => ({
  idLog: 1, fecha: '2026-09-25T08:00:00', nombreUsuario: 'usuario-a', accion: 'LOGIN', detalle: '', motivo: null, ...o,
})
// Orden del servidor: fecha desc, id desc. 08:15:30 UTC en septiembre = 10:15:30 en Madrid; 22:30 del 24 = 00:30 del 25.
const L1 = log({ idLog: 3, fecha: '2026-09-25T08:15:30', nombreUsuario: 'usuario-a', accion: 'CREAR_ASIGNACION', detalle: 'ID_REP: R1, IMEI: 000000000000000, TECNICO: tecnico-a' })
const L2 = log({ idLog: 2, fecha: '2026-09-25T08:10:00', nombreUsuario: 'usuario-b', accion: 'ELIMINAR_ASIGNACION', detalle: 'ID_REP: R2, IMEI: 000000000000001', motivo: 'Duplicada' })
const L3 = log({ idLog: 1, fecha: '2026-09-24T22:30:00', nombreUsuario: 'admin-prueba', accion: 'LOGIN', detalle: '' })
const LOGS = [L1, L2, L3]

const usuario = (idUsu: number, nombreUsuario: string): Usuario => ({
  idUsu, nombreUsuario, rol: 'TECNICO', idTec: idUsu + 10, nombreTecnico: `tecnico-${idUsu}`, activo: idUsu !== 3,
})
// Desordenados a propósito: la página los ordena con el orden natural de String (mayúsculas antes que minúsculas).
const USUARIOS = [usuario(1, 'usuario-b'), usuario(2, 'Usuario-C'), usuario(3, 'usuario-a')]

type Peticion = Record<string, string>
/** Registra los parámetros de cada GET /api/logs; `respuesta` recibe el número de petición (1, 2, …). */
function registrarLogs(respuesta: (n: number) => Response = () => HttpResponse.json(LOGS)) {
  const peticiones: Peticion[] = []
  server.use(
    http.get('*/api/logs', ({ request }) => {
      // `forEach` y no `Object.fromEntries(searchParams)`: sin DOM.Iterable en el `lib`, tsc -b da TS2769.
      const params: Peticion = {}
      new URL(request.url).searchParams.forEach((valor, clave) => { params[clave] = valor })
      peticiones.push(params)
      return respuesta(peticiones.length)
    }),
  )
  return peticiones
}
const ultima = (p: Peticion[]) => p[p.length - 1]

beforeEach(() => {
  server.use(
    http.get('*/api/logs/acciones', () => HttpResponse.json(['CREAR_ASIGNACION', 'ELIMINAR_ASIGNACION', 'LOGIN'])),
    http.get('*/api/usuarios/tecnicos', () => HttpResponse.json(USUARIOS)),
  )
})

function abrir() {
  return renderConProviders(<LogsPage />, { sesion: SESION_ADMIN, ruta: '/gestion/logs' })
}

describe('LogsPage: estructura (LogView.fxml)', () => {
  it('cabecera, barra de filtros en su orden, pie y carga inicial con limite=1000 y sin filtros', async () => {
    const peticiones = registrarLogs()
    const { container } = abrir()
    expect(screen.getByRole('heading', { name: 'Log de actividad' })).toHaveClass('text-[18px]', 'font-bold', 'text-azul-medio')
    expect(screen.getByText('Registro de acciones realizadas en el sistema')).toHaveClass('text-[12px]', 'text-azul-gris')
    expect(container.querySelector('img[src="/logo_inicio_sesion.png"]')).toHaveClass('h-[46px]', 'w-[46px]')
    const buscar = screen.getByPlaceholderText('Buscar...')
    const accion = screen.getByRole('combobox', { name: 'Acción' })
    const tecnico = screen.getByRole('combobox', { name: 'Técnico' })
    expect(buscar).toHaveClass('w-[220px]')
    expect(accion).toHaveAttribute('placeholder', 'Acción...')
    expect(tecnico).toHaveAttribute('placeholder', 'Técnico...')
    expect(accion.parentElement?.parentElement).toHaveClass('w-[150px]')
    expect(tecnico.parentElement?.parentElement).toHaveClass('w-[150px]')
    expect(buscar.parentElement).toHaveClass('flex-wrap')
    const orden = [buscar, accion, tecnico, screen.getByLabelText('Desde:'), screen.getByLabelText('Hasta:'), screen.getByRole('button', { name: 'Limpiar filtros' })]
    for (let i = 1; i < orden.length; i++) {
      expect(orden[i - 1].compareDocumentPosition(orden[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    }
    const actualizar = screen.getByRole('button', { name: 'Actualizar' })
    const cerrar = screen.getByRole('button', { name: 'Cerrar' })
    expect(actualizar.compareDocumentPosition(cerrar) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(cerrar).toHaveClass('text-[12px]', 'text-azul-gris')
    await screen.findByText('CREAR_ASIGNACION')
    expect(peticiones).toEqual([{ limite: '1000' }])
  })
  it('tabla: Fecha con segundos en Madrid, en el orden del servidor, sin ordenación por cabecera', async () => {
    registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Fecha', 'Usuario', 'Acción', 'Detalle'])
    expect(within(screen.getAllByRole('columnheader')[0]).queryByRole('button')).not.toBeInTheDocument()
    const filas = screen.getAllByRole('row').slice(1)
    expect(filas.map((f) => within(f).getAllByRole('cell')[0].textContent)).toEqual(['25/09/2026 10:15:30', '25/09/2026 10:10:00', '25/09/2026 00:30:00'])
    expect(within(filas[0]).getAllByRole('cell').map((c) => c.textContent)).toEqual(['25/09/2026 10:15:30', 'usuario-a', 'CREAR_ASIGNACION', 'ID_REP: R1, IMEI: 000000000000000, TECNICO: tecnico-a'])
  })
  it('sin filas: "No hay contenido en la tabla" y sin aviso de tope', async () => {
    registrarLogs(() => HttpResponse.json([]))
    abrir()
    expect(await screen.findByText('No hay contenido en la tabla')).toBeInTheDocument()
    expect(screen.queryByText(/Mostrando los 1\.000/)).not.toBeInTheDocument()
  })
  it('no registra exportable: "Descargar CSV" queda deshabilitado (calco, sin CSV de logs)', async () => {
    registrarLogs()
    function SondaCsv() {
      const exportar = useExportable()
      return <p>{exportar ? 'CSV ACTIVO' : 'CSV DESHABILITADO'}</p>
    }
    renderConProviders(<LogsPage />, {
      sesion: SESION_ADMIN, ruta: '/gestion/logs',
      layout: <ExportableProvider><Outlet /><SondaCsv /></ExportableProvider>,
    })
    await screen.findByText('CREAR_ASIGNACION')
    expect(screen.getByText('CSV DESHABILITADO')).toBeInTheDocument()
  })
})

describe('LogsPage: buscador en memoria', () => {
  it('filtra por usuario, acción o detalle sin pedir otra vez; no mira el motivo', async () => {
    const peticiones = registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    const buscar = screen.getByPlaceholderText('Buscar...')
    await userEvent.type(buscar, '000000000000001')
    expect(screen.queryByText('CREAR_ASIGNACION')).not.toBeInTheDocument()
    expect(screen.getByText('ELIMINAR_ASIGNACION')).toBeInTheDocument()
    await userEvent.clear(buscar)
    await userEvent.type(buscar, 'duplicada')
    expect(screen.getByText('No hay contenido en la tabla')).toBeInTheDocument()
    expect(peticiones).toHaveLength(1)
  })
  it('"Actualizar" recarga con los filtros actuales y el buscador se conserva', async () => {
    const peticiones = registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    await userEvent.type(screen.getByPlaceholderText('Buscar...'), 'usuario-b')
    await userEvent.click(screen.getByRole('button', { name: 'Actualizar' }))
    await waitFor(() => expect(peticiones).toHaveLength(2))
    expect(peticiones[1]).toEqual({ limite: '1000' })
    expect(screen.getByPlaceholderText('Buscar...')).toHaveValue('usuario-b')
    expect(screen.queryByText('CREAR_ASIGNACION')).not.toBeInTheDocument()
    expect(screen.getByText('ELIMINAR_ASIGNACION')).toBeInTheDocument()
  })
})

describe('LogsPage: filtros de servidor', () => {
  it('"Acción...": opciones de /api/logs/acciones; elegir filtra; teclear otra cosa no lo quita; vaciarlo sí', async () => {
    const peticiones = registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    const campo = screen.getByRole('combobox', { name: 'Acción' })
    await userEvent.type(campo, 'asig')
    expect((await screen.findAllByRole('option')).map((o) => o.textContent)).toEqual(['CREAR_ASIGNACION', 'ELIMINAR_ASIGNACION'])
    await userEvent.click(screen.getByRole('option', { name: 'ELIMINAR_ASIGNACION' }))
    await waitFor(() => expect(ultima(peticiones)).toEqual({ limite: '1000', accion: 'ELIMINAR_ASIGNACION' }))
    // Calco de LogController :137-144: editar el texto a otra cosa no vacía no quita la acción elegida.
    await userEvent.type(campo, 'X')
    expect(peticiones).toHaveLength(2)
    await userEvent.clear(campo)
    await waitFor(() => expect(peticiones).toHaveLength(3))
    expect(ultima(peticiones)).toEqual({ limite: '1000' })
  })
  it('"Técnico...": nombres de usuario en orden natural de cadena; elegir manda `tecnico`', async () => {
    const peticiones = registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    await userEvent.type(screen.getByRole('combobox', { name: 'Técnico' }), 'suario')
    expect((await screen.findAllByRole('option')).map((o) => o.textContent)).toEqual(['Usuario-C', 'usuario-a', 'usuario-b'])
    await userEvent.click(screen.getByRole('option', { name: 'usuario-a' }))
    await waitFor(() => expect(ultima(peticiones)).toEqual({ limite: '1000', tecnico: 'usuario-a' }))
  })
  it('"Desde:" y "Hasta:" mandan las fechas yyyy-MM-dd', async () => {
    const peticiones = registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    fireEvent.change(screen.getByLabelText('Desde:'), { target: { value: '2026-09-01' } })
    await waitFor(() => expect(ultima(peticiones)).toEqual({ limite: '1000', desde: '2026-09-01' }))
    fireEvent.change(screen.getByLabelText('Hasta:'), { target: { value: '2026-09-26' } })
    await waitFor(() => expect(ultima(peticiones)).toEqual({ limite: '1000', desde: '2026-09-01', hasta: '2026-09-26' }))
  })
  it('"Limpiar filtros" vacía los cinco y recarga una sola vez', async () => {
    const peticiones = registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    await userEvent.type(screen.getByPlaceholderText('Buscar...'), 'usuario-a')
    fireEvent.change(screen.getByLabelText('Desde:'), { target: { value: '2026-09-01' } })
    await waitFor(() => expect(peticiones).toHaveLength(2))
    await userEvent.type(screen.getByRole('combobox', { name: 'Acción' }), 'LOG')
    await userEvent.click(await screen.findByRole('option', { name: 'LOGIN' }))
    await waitFor(() => expect(peticiones).toHaveLength(3))
    await userEvent.type(screen.getByRole('combobox', { name: 'Técnico' }), 'usuario-b')
    await userEvent.click(await screen.findByRole('option', { name: 'usuario-b' }))
    await waitFor(() => expect(peticiones).toHaveLength(4))
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await waitFor(() => expect(peticiones).toHaveLength(5))
    expect(peticiones[4]).toEqual({ limite: '1000' })
    expect(screen.getByPlaceholderText('Buscar...')).toHaveValue('')
    expect(screen.getByRole('combobox', { name: 'Acción' })).toHaveValue('')
    expect(screen.getByRole('combobox', { name: 'Técnico' })).toHaveValue('')
    expect(screen.getByLabelText('Desde:')).toHaveValue('')
    expect(screen.getByLabelText('Hasta:')).toHaveValue('')
    await new Promise((r) => setTimeout(r, 50))
    expect(peticiones).toHaveLength(5)
  })
})

describe('LogsPage: aviso de tope (G3)', () => {
  it('con exactamente 1.000 filas avisa bajo la barra; con 999, no', async () => {
    const lote = (n: number) => Array.from({ length: n }, (_, i) => log({ idLog: n - i, detalle: `ID_REP: R${i}` }))
    registrarLogs(() => HttpResponse.json(lote(1000)))
    const { unmount } = abrir()
    expect(await screen.findByText('Mostrando los 1.000 registros más recientes; acota con los filtros.')).toBeInTheDocument()
    unmount()
    sessionStorage.clear()
    registrarLogs(() => HttpResponse.json(lote(999)))
    abrir()
    await screen.findByText('ID_REP: R0')
    expect(screen.queryByText('Mostrando los 1.000 registros más recientes; acota con los filtros.')).not.toBeInTheDocument()
  })
})

describe('LogsPage: doble clic (LogController :93-103)', () => {
  it('abre "Detalle del log" con el motivo tras una línea en blanco, o solo el detalle', async () => {
    registrarLogs()
    abrir()
    await userEvent.dblClick(await screen.findByText('ELIMINAR_ASIGNACION'))
    const dlg = await screen.findByRole('dialog', { name: 'Detalle del log' })
    expect(within(dlg).getByRole('textbox')).toHaveValue('ID_REP: R2, IMEI: 000000000000001\n\nMOTIVO: Duplicada')
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Detalle del log' })).not.toBeInTheDocument())
    await userEvent.dblClick(screen.getByText('CREAR_ASIGNACION'))
    const otro = await screen.findByRole('dialog', { name: 'Detalle del log' })
    expect(within(otro).getByRole('textbox')).toHaveValue('ID_REP: R1, IMEI: 000000000000000, TECNICO: tecnico-a')
  })
})

describe('LogsPage: errores', () => {
  it('un fallo al cambiar un filtro avisa "Error al cargar los logs: …" y la tabla conserva lo que tenía', async () => {
    registrarLogs((n) => (n === 1 ? HttpResponse.json(LOGS) : new HttpResponse(null, { status: 403 })))
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    fireEvent.change(screen.getByLabelText('Desde:'), { target: { value: '2026-09-01' } })
    const dlg = await screen.findByRole('dialog', { name: 'Error' })
    expect(dlg).toHaveTextContent('Error al cargar los logs: No tienes permisos para realizar esta acción.')
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.getByText('CREAR_ASIGNACION')).toBeInTheDocument()
    expect(screen.getByText('ELIMINAR_ASIGNACION')).toBeInTheDocument()
  })
  it('un fallo de "Actualizar" avisa con el mensaje del servidor y conserva las filas', async () => {
    registrarLogs((n) => (n === 1 ? HttpResponse.json(LOGS) : HttpResponse.json({ message: 'Fallo de prueba' }, { status: 400 })))
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    await userEvent.click(screen.getByRole('button', { name: 'Actualizar' }))
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('Error al cargar los logs: Fallo de prueba')
    expect(screen.getByText('CREAR_ASIGNACION')).toBeInTheDocument()
  })
  it('sin conexión: solo el banner de la política global, sin "Error al cargar los logs"', async () => {
    // Las dos listas también fallan (sin conexión real caerían todas): si respondieran 200, `client.ts` llamaría a
    // `reportarExito()` y el banner se apagaría solo. Mismo recurso que `ClientesPage.test.tsx:148`.
    server.use(
      http.get('*/api/logs/acciones', () => new HttpResponse(null, { status: 500 })),
      http.get('*/api/usuarios/tecnicos', () => new HttpResponse(null, { status: 500 })),
    )
    registrarLogs(() => new HttpResponse(null, { status: 500 }))
    abrir()
    await waitFor(() => expect(estaConectado()).toBe(false))
    expect(screen.queryByText(/Error al cargar los logs/)).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
  it('un fallo de las listas de acciones o de usuarios es silencioso: los autocompletar quedan vacíos', async () => {
    server.use(
      http.get('*/api/logs/acciones', () => new HttpResponse(null, { status: 403 })),
      http.get('*/api/usuarios/tecnicos', () => new HttpResponse(null, { status: 403 })),
    )
    registrarLogs()
    abrir()
    await screen.findByText('CREAR_ASIGNACION')
    await userEvent.type(screen.getByRole('combobox', { name: 'Acción' }), 'a')
    await userEvent.type(screen.getByRole('combobox', { name: 'Técnico' }), 'a')
    expect(screen.queryAllByRole('option')).toHaveLength(0)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('LogsPage: "Cerrar"', () => {
  it('vuelve a la vista desde la que se abrió (state.volverA); al reabrir, los filtros están vacíos', async () => {
    registrarLogs()
    const { router } = renderConRouter(
      [
        { path: '/stock', element: <p>STOCK</p> },
        { path: '/reparaciones', element: <p>REPARACIONES</p> },
        { path: '/gestion/logs', element: <LogsPage /> },
      ],
      { sesion: SESION_ADMIN, ruta: '/stock' },
    )
    await act(() => router.navigate('/gestion/logs', { state: { volverA: '/stock' } }))
    await userEvent.type(await screen.findByPlaceholderText('Buscar...'), 'usuario-a')
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(await screen.findByText('STOCK')).toBeInTheDocument()
    await act(() => router.navigate('/gestion/logs'))
    expect(await screen.findByPlaceholderText('Buscar...')).toHaveValue('')
  })
  it('sin volverA vuelve a /reparaciones', async () => {
    registrarLogs()
    renderConRouter(
      [
        { path: '/reparaciones', element: <p>REPARACIONES</p> },
        { path: '/gestion/logs', element: <LogsPage /> },
      ],
      { sesion: SESION_ADMIN, ruta: '/gestion/logs' },
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar' }))
    expect(await screen.findByText('REPARACIONES')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/logs/LogsPage.test.tsx`
Expected: FAIL, `Failed to resolve import "./LogsPage"`.

- [ ] **Step 3: Implementar la página**

`src/modules/gestion/logs/LogsPage.tsx`:

```tsx
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { LogActividad } from '@/shared/api/client'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { BotonPrimario, BotonSecundario } from '@/shared/ui/Botones'
import { CampoAutocompletar } from '@/shared/ui/CampoAutocompletar'
import { DataTable } from '@/shared/ui/DataTable'
import { Input } from '@/shared/ui/input'
import { RangoFechas } from '@/shared/ui/RangoFechas'
import { useUsuariosTecnicos } from '../api'
import { rutaVolverA } from '../navegacion'
import { useAccionesLog, useLogs } from './api'
import { COLUMNAS_LOGS, textoDetalle } from './columnas'
import { aplicarBuscador, FILTROS_LOGS_VACIOS, LIMITE_LOGS, MSG_TOPE, queryLogs, TEXTO_VACIO_LOGS, type FiltrosLogs } from './filtros'

const PREFIJO_ERROR = 'Error al cargar los logs: '

/** "Ver logs" (LogView.fxml + LogController de `hotfix/0.16.3`), como página del shell (G2). Solo lectura. */
export function LogsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { mostrarError, mostrarTexto } = useAlerta()
  // Estado local: los filtros no sobreviven a salir de la página (calco: la ventana del JavaFX se abre siempre vacía).
  const [filtros, setFiltros] = useState<FiltrosLogs>(FILTROS_LOGS_VACIOS)
  // CampoAutocompletar no vuelve a '' solo porque `valor` pase a null: "Limpiar filtros" remonta los dos campos.
  const [limpiezas, setLimpiezas] = useState(0)
  const [seleccionada, setSeleccionada] = useState<string | null>(null)

  const q = useMemo(() => queryLogs(filtros), [filtros])
  const { data, error, errorUpdatedAt, isFetching, refetch } = useLogs(q)
  const { data: acciones } = useAccionesLog()
  const { data: usuarios } = useUsuariosTecnicos()

  // La tabla conserva lo último que llegó bien (LogController :223-225: un fallo no toca `logsMaster`). Con otra clave
  // (cambio de filtro) `data` pasa a undefined mientras carga o si falla; aquí se ignora ese undefined. Patrón de
  // "ajustar el estado durante el render" en vez de un efecto.
  const [filas, setFilas] = useState<LogActividad[]>([])
  const [datosVistos, setDatosVistos] = useState<LogActividad[] | undefined>(undefined)
  if (data !== datosVistos) {
    setDatosVistos(data)
    if (data !== undefined) setFilas(data)
  }

  // Un aviso por fallo de carga (inicial, "Actualizar" o cambio de filtro), salvo 401 y conexión: esos los lleva la
  // política global (flujo de sesión caducada y banner), que ya se disparó en el cliente HTTP. `isFetching` evita repetir
  // el error que una clave ya fallida trae de la caché mientras se vuelve a pedir.
  useEffect(() => {
    if (!error || isFetching || esErrorGestionadoGlobalmente(error)) return
    mostrarError(PREFIJO_ERROR + mensajeDeError(error))
  }, [error, errorUpdatedAt, isFetching, mostrarError])

  const opcionesAccion = useMemo(() => (acciones ?? []).map((a) => ({ clave: a, etiqueta: a })), [acciones])
  // Calco de `sorted()` sobre `nombreUsuario` (:203-208): orden natural de String (por unidades UTF-16, sin locale), que
  // es exactamente el de `Array.prototype.sort()` sin comparador. Activos e inactivos; sin ADMIN (lo excluye el endpoint).
  const opcionesUsuario = useMemo(
    () => (usuarios ?? []).map((u) => u.nombreUsuario).sort().map((n) => ({ clave: n, etiqueta: n })),
    [usuarios],
  )
  const visibles = useMemo(() => aplicarBuscador(filas, filtros.texto), [filas, filtros.texto])

  const limpiar = () => {
    // Un solo cambio de estado = una sola clave nueva = una sola recarga (el JavaFX lanzaba hasta cinco).
    setFiltros(FILTROS_LOGS_VACIOS)
    setLimpiezas((n) => n + 1)
  }

  return (
    <div className="flex min-h-full flex-col bg-fondo-gestion">
      <header className="flex flex-col gap-4 px-12 pt-7 pb-5">
        <div className="flex items-center gap-3.5">
          <img src="/logo_inicio_sesion.png" alt="" className="h-[46px] w-[46px] object-contain" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-[18px] font-bold text-azul-medio">Log de actividad</h1>
            <p className="text-[12px] text-azul-gris">Registro de acciones realizadas en el sistema</p>
          </div>
        </div>
        <hr className="border-borde-input" />
      </header>

      <section className="flex flex-1 flex-col gap-2.5 px-12 pt-2">
        <div className="flex flex-wrap items-center gap-2.5">
          <Input
            aria-label="Buscar"
            value={filtros.texto}
            onChange={(e) => setFiltros((f) => ({ ...f, texto: e.target.value }))}
            placeholder="Buscar..."
            className="w-[220px] bg-superficie"
          />
          <div className="w-[150px]">
            <CampoAutocompletar
              key={`accion-${limpiezas}`}
              valor={filtros.accion}
              opciones={opcionesAccion}
              onElegir={(accion) => setFiltros((f) => ({ ...f, accion }))}
              // Calco de :139-143: solo vaciar el texto quita la acción elegida.
              onTextoCambiado={(t) => { if (t.trim() === '') setFiltros((f) => (f.accion === null ? f : { ...f, accion: null })) }}
              placeholder="Acción..."
              aria-label="Acción"
            />
          </div>
          <div className="w-[150px]">
            <CampoAutocompletar
              key={`usuario-${limpiezas}`}
              valor={filtros.usuario}
              opciones={opcionesUsuario}
              onElegir={(usuario) => setFiltros((f) => ({ ...f, usuario }))}
              onTextoCambiado={(t) => { if (t.trim() === '') setFiltros((f) => (f.usuario === null ? f : { ...f, usuario: null })) }}
              placeholder="Técnico..."
              aria-label="Técnico"
            />
          </div>
          <RangoFechas desde={filtros.desde} hasta={filtros.hasta} onChange={(desde, hasta) => setFiltros((f) => ({ ...f, desde, hasta }))} />
          <BotonSecundario onClick={limpiar}>Limpiar filtros</BotonSecundario>
        </div>
        {filas.length === LIMITE_LOGS && <p className="text-[12px] text-azul-gris">{MSG_TOPE}</p>}
        <DataTable
          columns={COLUMNAS_LOGS}
          data={visibles}
          vacio={TEXTO_VACIO_LOGS}
          ajuste="estirar"
          ordenacion={false}
          getRowId={(l) => String(l.idLog)}
          seleccionada={seleccionada}
          onSeleccionar={setSeleccionada}
          onAbrir={(l) => mostrarTexto('Detalle del log', textoDetalle(l))}
        />
      </section>

      <footer className="flex items-center justify-end gap-3 px-12 pt-3 pb-5">
        <BotonPrimario onClick={() => void refetch()}>Actualizar</BotonPrimario>
        <button type="button" onClick={() => navigate(rutaVolverA(location.state))} className="cursor-pointer bg-transparent text-[12px] text-azul-gris">
          Cerrar
        </button>
      </footer>
    </div>
  )
}
```

- [ ] **Step 4: Comprobar el silencio de `useUsuariosTecnicos`**

Run: `grep -n "silenciarError" src/modules/gestion/api.ts`
Expected: una línea dentro de `useUsuariosTecnicos` (`meta: { silenciarError: true },`). Si no sale, añadir esa línea al objeto de `useQuery` de `useUsuariosTecnicos` en `src/modules/gestion/api.ts` y anotarlo en el commit (lo necesita el test "un fallo de las listas… es silencioso"; TecnicosPage ya enseña su error inline y tampoco quiere el diálogo genérico).

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/logs/LogsPage.test.tsx`
Expected: PASS, 18 tests. (Si un test cae por timeout con otros procesos node vivos, relanzar el fichero aislado antes de diagnosticar.)

- [ ] **Step 6: Router**

En `src/app/router.tsx`, añadir el import tras el de `ClientesPage` (hoy `router.tsx:5`):

```tsx
import { LogsPage } from '@/modules/gestion/logs/LogsPage'
```

y, dentro del bloque `RequiereAdmin` que dejó la Task 7, sustituir:

```tsx
{ path: '/gestion/logs', element: <PendienteDeMigrar nombre="Ver logs" /> },
```

por:

```tsx
{ path: '/gestion/logs', element: <LogsPage /> },
```

Run: `grep -n "gestion/logs\|PendienteDeMigrar\|cambiar-password" src/app/router.tsx`
Expected: `/gestion/logs` → `<LogsPage />` dentro del bloque de `RequiereAdmin`; `PendienteDeMigrar` solo en su import y en `/estadisticas/*`; ninguna línea con `cambiar-password` (la quitó la Task 7).

- [ ] **Step 7: Lint, tipos y suite del módulo y del shell**

```bash
npm run lint && npx tsc -b && npx vitest run src/modules/gestion src/app
```

Expected: lint sin errores ni warnings nuevos; `tsc -b` sin salida; PASS de todos los ficheros (los de la Task 7 en `src/app` y `rutas.test.tsx` siguen verdes: la guarda no cambia).

- [ ] **Step 8: Commit**

```bash
git add src/modules/gestion/logs/LogsPage.tsx src/modules/gestion/logs/LogsPage.test.tsx src/app/router.tsx
git commit -m "feat(gestion): página ver logs con filtros, aviso de tope y detalle por doble clic"
git cat-file -p HEAD | tail -1
```

(Si el Step 4 tocó `src/modules/gestion/api.ts`, añadirlo al `git add`.)

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 18.

---

## Task 12: Web — "Cambiar contraseña": validación, api, diálogo y apertura desde el menú de usuario

**Files:**
- Create: `src/modules/gestion/cuenta/validacion.ts`, `src/modules/gestion/cuenta/validacion.test.ts`
- Create: `src/modules/gestion/cuenta/api.ts`, `src/modules/gestion/cuenta/api.test.tsx`
- Create: `src/modules/gestion/cuenta/CambiarPasswordDialog.tsx`, `src/modules/gestion/cuenta/CambiarPasswordDialog.test.tsx`
- Modify: `src/app/shell/UserMenu.tsx:1-33` (hoy en `main`; la Task 7 ya lo tocó: `useLocation`, `volverA` en los dos ítems de ADMIN y "Cambiar contraseña" con `onSelect` vacío)
- Modify (tests): `src/app/shell/TopBar.test.tsx:1-10` (imports) y al final del fichero (tras `:78`, `describe` nuevo)

**Interfaces:**
- Consumes: `api` de `@/shared/api/client`; contrato `PATCH /api/auth/cambiar-password` (`schema.d.ts:1319-1334`, operación `cambiarPassword` `:5777-5800`, cuerpo `AuthCambiarPasswordRequest` `{ passwordActual: string; passwordNueva: string }` `:2747-2750`); `CampoPassword` de `@/shared/ui/CampoPassword` (Task 6: `{ valor, onChange, placeholder, 'aria-label', autoComplete?, autoFocus?, className? }`, botón del ojo con `aria-label` "Mostrar contraseña"/"Ocultar contraseña"); `Dialog`, `DialogContent`, `DialogTitle` de `@/shared/ui/dialog` (`dialog.tsx:8-80`, `DialogContent` con `showCloseButton` y `sm:max-w-lg` por defecto, `:62`); `Button` de `@/shared/ui/button`; `useAlerta().mostrarAviso` (`AlertaProvider.tsx:42`); `esErrorGestionadoGlobalmente`, `mensajeDeError` (`errors.ts:79-89`); tokens `bg-azul-noche`, `text-error-password`, `text-etiqueta-password` (Task 6), `border-fila-sep`, `border-borde-input`.
- Produces:

```ts
// cuenta/validacion.ts
export const MSG_RELLENA = 'Rellena todos los campos.'
export const MSG_PASSWORD_CORTA = 'La contraseña debe tener al menos 6 caracteres.'
export const MSG_NUEVAS_NO_COINCIDEN = 'Las contraseñas nuevas no coinciden.'
export const TITULO_EXITO = 'Información'
export const MSG_EXITO = 'Contraseña cambiada correctamente.'
export function validarCambioPassword(actual: string, nueva: string, confirmar: string): string | null
// cuenta/api.ts
export type CuerpoCambiarPassword = { passwordActual: string; passwordNueva: string }
export function useCambiarPassword(): UseMutationResult<void, Error, CuerpoCambiarPassword>
// cuenta/CambiarPasswordDialog.tsx
export function CambiarPasswordDialog({ abierto, onCerrar }: { abierto: boolean; onCerrar: () => void })
```

**Supuestos:**
- `CampoPassword` (Task 6) pasa `className` al `Input` con `cn(...)` (lo que llega después gana, `tailwind-merge`) y su contenedor ocupa todo el ancho; el ojo es un `<button type="button">` que no envía el formulario.
- Estado de `UserMenu.tsx` tras la Task 7: el de abajo en "Paso 7" menos el `useState`, el import del diálogo y el `onSelect` de "Cambiar contraseña" (que la Task 7 dejó en `onSelect={() => {}}`). Si difiere en algo más, se conserva lo de la Task 7 y solo se aplican esas tres piezas.
- El título del aviso de éxito "Información" es el previsto (spec §6.3, "a confirmar con la captura" `gestion-password-exito.png`).

> Búsqueda previa: `grep -rn "cambiar-password\|passwordActual" src --include=*.ts --include=*.tsx | grep -v schema.d.ts` → solo `UserMenu.tsx:27` en `main` (la navegación que quitó la Task 7) y nada tras la Task 7; `src/modules/gestion/cuenta/` no existe. `DialogoAlmacen` (`modules/almacen/ui/DialogoAlmacen.tsx:22-23, :35`) fija el ancho con `w-[360px] max-w-[min(360px,calc(100%-2rem))] sm:max-w-[min(360px,calc(100%-2rem))]`: la variante `sm:` del mismo grupo pisa el `sm:max-w-lg` de `DialogContent` vía `cn`/`tailwind-merge`; aquí se hace igual con 380. `useErrorServidor` está en `modules/almacen/ui` (no importable desde `gestion`): el diálogo lleva su propio estado de error. El formulario vive en un componente hijo de `DialogContent`, que Radix desmonta al cerrar: al reabrir, campos y error vuelven a estar vacíos (el JavaFX crea la ventana de nuevo cada vez).

- [ ] **Step 1: Test de la validación (falla)**

`src/modules/gestion/cuenta/validacion.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { MSG_EXITO, MSG_NUEVAS_NO_COINCIDEN, MSG_PASSWORD_CORTA, MSG_RELLENA, TITULO_EXITO, validarCambioPassword } from './validacion'

describe('textos (CambiarPasswordController :84-105)', () => {
  it('son exactos', () => {
    expect(MSG_RELLENA).toBe('Rellena todos los campos.')
    expect(MSG_PASSWORD_CORTA).toBe('La contraseña debe tener al menos 6 caracteres.')
    expect(MSG_NUEVAS_NO_COINCIDEN).toBe('Las contraseñas nuevas no coinciden.')
    expect(TITULO_EXITO).toBe('Información')
    expect(MSG_EXITO).toBe('Contraseña cambiada correctamente.')
  })
})

describe('validarCambioPassword (orden: vacíos → < 6 → no coinciden; sin trim)', () => {
  it.each([
    ['', 'nueva123', 'nueva123'],
    ['secreta1', '', 'nueva123'],
    ['secreta1', 'nueva123', ''],
    ['', '', ''],
  ])('algún campo vacío ("%s", "%s", "%s") → "Rellena todos los campos."', (actual, nueva, confirmar) => {
    expect(validarCambioPassword(actual, nueva, confirmar)).toBe(MSG_RELLENA)
  })
  it('vacío gana a corta y a no coinciden', () => {
    expect(validarCambioPassword('', 'abc', 'xyz')).toBe(MSG_RELLENA)
  })
  it('nueva de menos de 6 caracteres → corta, antes que no coinciden', () => {
    expect(validarCambioPassword('secreta1', 'nue12', 'nue12')).toBe(MSG_PASSWORD_CORTA)
    expect(validarCambioPassword('secreta1', 'abc', 'xyz')).toBe(MSG_PASSWORD_CORTA)
  })
  it('nueva distinta de la confirmación (distingue mayúsculas) → no coinciden', () => {
    expect(validarCambioPassword('secreta1', 'nueva123', 'NUEVA123')).toBe(MSG_NUEVAS_NO_COINCIDEN)
  })
  it('sin trim: los espacios cuentan como relleno y como caracteres', () => {
    expect(validarCambioPassword('   ', '      ', '      ')).toBeNull()
    expect(validarCambioPassword('secreta1', ' nue1 ', ' nue1 ')).toBeNull()
    expect(validarCambioPassword('secreta1', 'nue12 ', 'nue12')).toBe(MSG_NUEVAS_NO_COINCIDEN)
  })
  it('6 caracteres exactos e iguales → válido', () => {
    expect(validarCambioPassword('secreta1', 'nueva1', 'nueva1')).toBeNull()
  })
})
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/cuenta/validacion.test.ts`
Expected: FAIL, `Failed to resolve import "./validacion"`.

- [ ] **Step 3: Implementar la validación**

`src/modules/gestion/cuenta/validacion.ts`:

```ts
/** Textos de CambiarPasswordController (`hotfix/0.16.3`, :84-95) y del Alert de éxito (:100-105). */
export const MSG_RELLENA = 'Rellena todos los campos.'
export const MSG_PASSWORD_CORTA = 'La contraseña debe tener al menos 6 caracteres.'
export const MSG_NUEVAS_NO_COINCIDEN = 'Las contraseñas nuevas no coinciden.'
/** Título por defecto del Alert INFORMATION con locale español; a confirmar con `gestion-password-exito.png`. */
export const TITULO_EXITO = 'Información'
export const MSG_EXITO = 'Contraseña cambiada correctamente.'

/** Calco de `guardar` (:84-95): sin trim ("   " cuenta como relleno), longitud en unidades UTF-16 como `String.length()`,
 *  comparación exacta. Para en el primer fallo; null = válido. La confirmación no viaja al servidor. */
export function validarCambioPassword(actual: string, nueva: string, confirmar: string): string | null {
  if (actual === '' || nueva === '' || confirmar === '') return MSG_RELLENA
  if (nueva.length < 6) return MSG_PASSWORD_CORTA
  if (nueva !== confirmar) return MSG_NUEVAS_NO_COINCIDEN
  return null
}
```

- [ ] **Step 4: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/cuenta/validacion.test.ts`
Expected: PASS, 10 tests.

- [ ] **Step 5: Test de la api (falla)**

`src/modules/gestion/cuenta/api.test.tsx`:

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { ReglaNegocioError } from '@/shared/api/errors'
import { crearQueryClient } from '@/shared/api/queryClient'
import { server } from '@/test/server'
import { useCambiarPassword } from './api'

function envoltorio() {
  const qc = crearQueryClient({ retry: false })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  return { qc, wrapper }
}

describe('useCambiarPassword (PATCH /api/auth/cambiar-password)', () => {
  it('manda {passwordActual, passwordNueva} y un 204 es éxito', async () => {
    const peticiones: { metodo: string; cuerpo: unknown }[] = []
    server.use(
      http.patch('*/api/auth/cambiar-password', async ({ request }) => {
        peticiones.push({ metodo: request.method, cuerpo: await request.json() })
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { wrapper } = envoltorio()
    const { result } = renderHook(() => useCambiarPassword(), { wrapper })
    result.current.mutate({ passwordActual: 'secreta1', passwordNueva: 'nueva123' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(peticiones).toEqual([{ metodo: 'PATCH', cuerpo: { passwordActual: 'secreta1', passwordNueva: 'nueva123' } }])
  })
  it('un 422 llega como ReglaNegocioError con el message del servidor y la mutación va silenciada', async () => {
    server.use(http.patch('*/api/auth/cambiar-password', () => HttpResponse.json({ message: 'Contraseña actual incorrecta.' }, { status: 422 })))
    const { qc, wrapper } = envoltorio()
    const { result } = renderHook(() => useCambiarPassword(), { wrapper })
    result.current.mutate({ passwordActual: 'mala123', passwordNueva: 'nueva123' })
    await waitFor(() => expect(result.current.error).toBeInstanceOf(ReglaNegocioError))
    expect(result.current.error?.message).toBe('Contraseña actual incorrecta.')
    expect(qc.getMutationCache().getAll()[0].meta).toEqual({ silenciarError: true })
  })
})
```

- [ ] **Step 6: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/cuenta/api.test.tsx`
Expected: FAIL, `Failed to resolve import "./api"`.

- [ ] **Step 7: Implementar la api**

`src/modules/gestion/cuenta/api.ts`:

```ts
import { useMutation, type UseMutationResult } from '@tanstack/react-query'
import { api } from '@/shared/api/client'

export type CuerpoCambiarPassword = { passwordActual: string; passwordNueva: string }

/** PATCH /api/auth/cambiar-password (AuthCambiarPasswordRequest). Sin invalidaciones (spec §7) ni Idempotency-Key
 *  (inventario-password §17.6). `silenciarError`: el 422 y el resto los pinta el diálogo en su línea; el corte de conexión
 *  lo sigue avisando el MutationCache (queryClient.ts:59-69) y el 401, el flujo global de sesión caducada. */
export function useCambiarPassword(): UseMutationResult<void, Error, CuerpoCambiarPassword> {
  return useMutation({
    mutationFn: async (body: CuerpoCambiarPassword) => {
      await api.PATCH('/api/auth/cambiar-password', { body })
    },
    meta: { silenciarError: true },
  })
}
```

- [ ] **Step 8: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/cuenta/api.test.tsx`
Expected: PASS, 2 tests.

- [ ] **Step 9: Test del diálogo (falla)**

`src/modules/gestion/cuenta/CambiarPasswordDialog.test.tsx`:

```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderConProviders, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { CambiarPasswordDialog } from './CambiarPasswordDialog'

/** Registra los cuerpos de cada PATCH; por defecto responde 204. */
function registrarCambio(respuesta: () => Response | Promise<Response> = () => new HttpResponse(null, { status: 204 })) {
  const cuerpos: unknown[] = []
  server.use(
    http.patch('*/api/auth/cambiar-password', async ({ request }) => {
      cuerpos.push(await request.json())
      return respuesta()
    }),
  )
  return cuerpos
}

function abrir(onCerrar = vi.fn()) {
  renderConProviders(<CambiarPasswordDialog abierto onCerrar={onCerrar} />, { sesion: SESION_TEC })
  return onCerrar
}

async function rellenar(actual: string, nueva: string, confirmar: string) {
  if (actual) await userEvent.type(screen.getByLabelText('Contraseña actual'), actual)
  if (nueva) await userEvent.type(screen.getByLabelText('Nueva contraseña'), nueva)
  if (confirmar) await userEvent.type(screen.getByLabelText('Confirmar nueva contraseña'), confirmar)
}

describe('CambiarPasswordDialog: estructura (CambiarPasswordView.fxml)', () => {
  it('barra navy con el título, tres campos con sus etiquetas y placeholders, botones y 380 px', () => {
    abrir()
    const dlg = screen.getByRole('dialog', { name: 'Cambiar contraseña' })
    expect(dlg).toHaveClass('w-[380px]', 'p-0', 'sm:max-w-[min(380px,calc(100%-2rem))]')
    expect(dlg).not.toHaveClass('sm:max-w-lg')
    const titulo = within(dlg).getByText('Cambiar contraseña')
    expect(titulo).toHaveClass('text-[15px]', 'font-bold', 'text-white')
    expect(titulo.parentElement).toHaveClass('bg-azul-noche', 'px-5', 'py-4')
    for (const etiqueta of ['Contraseña actual', 'Nueva contraseña', 'Confirmar nueva contraseña']) {
      expect(within(dlg).getByText(etiqueta)).toHaveClass('text-[11px]', 'font-bold', 'text-etiqueta-password')
    }
    expect(within(dlg).getByLabelText('Contraseña actual')).toHaveAttribute('placeholder', 'Contraseña actual')
    expect(within(dlg).getByLabelText('Nueva contraseña')).toHaveAttribute('placeholder', 'Nueva contraseña')
    expect(within(dlg).getByLabelText('Confirmar nueva contraseña')).toHaveAttribute('placeholder', 'Confirmar contraseña')
    for (const nombre of ['Contraseña actual', 'Nueva contraseña', 'Confirmar nueva contraseña']) {
      expect(within(dlg).getByLabelText(nombre)).toHaveAttribute('type', 'password')
    }
    const cancelar = within(dlg).getByRole('button', { name: 'Cancelar' })
    const guardar = within(dlg).getByRole('button', { name: 'Guardar' })
    expect(cancelar.compareDocumentPosition(guardar) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(guardar).toHaveClass('bg-azul-noche', 'font-bold', 'rounded-md')
    expect(cancelar).toHaveClass('border-fila-sep', 'rounded-md')
    expect(within(dlg).getByText('Contraseña actual').closest('form')).toHaveClass('p-6', 'gap-4')
    // Línea de error oculta hasta que hay error; sin la ✕ de shadcn (el JavaFX no la tiene).
    expect(within(dlg).queryByRole('alert')).not.toBeInTheDocument()
    expect(within(dlg).queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
  })
  it('el foco inicial está en la contraseña actual', async () => {
    abrir()
    await waitFor(() => expect(screen.getByLabelText('Contraseña actual')).toHaveFocus())
  })
  it('cada campo tiene su ojo independiente', async () => {
    abrir()
    const ojos = screen.getAllByRole('button', { name: 'Mostrar contraseña' })
    expect(ojos).toHaveLength(3)
    await userEvent.click(ojos[0])
    expect(screen.getByLabelText('Contraseña actual')).toHaveAttribute('type', 'text')
    expect(screen.getByLabelText('Nueva contraseña')).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText('Confirmar nueva contraseña')).toHaveAttribute('type', 'password')
    expect(screen.getAllByRole('button', { name: 'Ocultar contraseña' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Mostrar contraseña' })).toHaveLength(2)
  })
})

describe('CambiarPasswordDialog: validación en la línea de error', () => {
  it.each([
    ['', '', '', 'Rellena todos los campos.'],
    ['secreta1', 'nue12', 'nue12', 'La contraseña debe tener al menos 6 caracteres.'],
    ['secreta1', 'nueva123', 'nueva124', 'Las contraseñas nuevas no coinciden.'],
  ])('("%s", "%s", "%s") → "%s" sin llamar al servidor', async (actual, nueva, confirmar, mensaje) => {
    const cuerpos = registrarCambio()
    const onCerrar = abrir()
    await rellenar(actual, nueva, confirmar)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    const error = screen.getByRole('alert')
    expect(error).toHaveTextContent(mensaje)
    expect(error).toHaveClass('text-[12px]', 'text-error-password')
    expect(cuerpos).toHaveLength(0)
    expect(onCerrar).not.toHaveBeenCalled()
  })
})

describe('CambiarPasswordDialog: guardado', () => {
  it('éxito: manda actual y nueva (sin la confirmación), cierra y avisa "Contraseña cambiada correctamente."; la sesión sigue', async () => {
    const cuerpos = registrarCambio()
    const onCerrar = abrir()
    await rellenar('secreta1', 'nueva123', 'nueva123')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    const aviso = await screen.findByRole('dialog', { name: 'Información' })
    expect(aviso).toHaveTextContent('Contraseña cambiada correctamente.')
    expect(cuerpos).toEqual([{ passwordActual: 'secreta1', passwordNueva: 'nueva123' }])
    expect(onCerrar).toHaveBeenCalledTimes(1)
    expect(sessionStorage.getItem('fsgr.sesion')).not.toBeNull()
  })
  it('Enter en un campo guarda', async () => {
    const cuerpos = registrarCambio()
    abrir()
    await rellenar('secreta1', 'nueva123', '')
    await userEvent.type(screen.getByLabelText('Confirmar nueva contraseña'), 'nueva123{Enter}')
    await waitFor(() => expect(cuerpos).toHaveLength(1))
  })
  it('"Guardar" y "Cancelar" quedan deshabilitados mientras responde y Esc no cierra', async () => {
    let soltar!: () => void
    const espera = new Promise<void>((r) => { soltar = r })
    registrarCambio(async () => { await espera; return new HttpResponse(null, { status: 204 }) })
    const onCerrar = abrir()
    await rellenar('secreta1', 'nueva123', 'nueva123')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled())
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    expect(onCerrar).not.toHaveBeenCalled()
    soltar()
    await waitFor(() => expect(onCerrar).toHaveBeenCalledTimes(1))
  })
  it('422: el message del servidor en la línea, los campos se conservan y no hay diálogo genérico', async () => {
    registrarCambio(() => HttpResponse.json({ message: 'Contraseña actual incorrecta.' }, { status: 422 }))
    const onCerrar = abrir()
    await rellenar('mala1234', 'nueva123', 'nueva123')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Contraseña actual incorrecta.')
    expect(screen.getByLabelText('Contraseña actual')).toHaveValue('mala1234')
    expect(screen.getByLabelText('Nueva contraseña')).toHaveValue('nueva123')
    expect(screen.getByLabelText('Confirmar nueva contraseña')).toHaveValue('nueva123')
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeEnabled()
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(onCerrar).not.toHaveBeenCalled()
  })
  it('un nuevo intento limpia el error anterior antes de validar', async () => {
    registrarCambio(() => HttpResponse.json({ message: 'Contraseña actual incorrecta.' }, { status: 422 }))
    abrir()
    await rellenar('mala1234', 'nueva123', 'nueva123')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await screen.findByText('Contraseña actual incorrecta.')
    await userEvent.clear(screen.getByLabelText('Confirmar nueva contraseña'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Rellena todos los campos.')
    expect(screen.queryByText('Contraseña actual incorrecta.')).not.toBeInTheDocument()
  })
  it('otro error (403): el texto de la web en la línea', async () => {
    registrarCambio(() => new HttpResponse(null, { status: 403 }))
    abrir()
    await rellenar('secreta1', 'nueva123', 'nueva123')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permisos para realizar esta acción.')
  })
  it('sin conexión: sin texto en la línea; lo avisa el diálogo global de conexión', async () => {
    registrarCambio(() => new HttpResponse(null, { status: 500 }))
    abrir()
    await rellenar('secreta1', 'nueva123', 'nueva123')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('dialog', { name: 'Error' })).toHaveTextContent('Sin conexión con el servidor: HTTP 500')
    // El diálogo de error deja el de contraseña con aria-hidden (fuera del árbol accesible): se mira el formulario por el DOM.
    expect(screen.getByLabelText('Contraseña actual').closest('form')?.querySelector('[role="alert"]')).toBeNull()
  })
})

describe('CambiarPasswordDialog: cerrar', () => {
  it('"Cancelar" cierra sin preguntar aunque haya texto, sin llamar al servidor', async () => {
    const cuerpos = registrarCambio()
    const onCerrar = abrir()
    await rellenar('secreta1', 'nueva123', '')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog', { name: /Eliminar|Descartar/ })).not.toBeInTheDocument()
    expect(cuerpos).toHaveLength(0)
  })
  it('Esc cierra', async () => {
    const onCerrar = abrir()
    await userEvent.keyboard('{Escape}')
    expect(onCerrar).toHaveBeenCalledTimes(1)
  })
  it('al reabrir, los campos y el error vuelven vacíos (la ventana del JavaFX se crea de nuevo)', async () => {
    function Arnes() {
      const [abierto, setAbierto] = useState(true)
      return (
        <>
          <button type="button" onClick={() => setAbierto(true)}>Abrir</button>
          <CambiarPasswordDialog abierto={abierto} onCerrar={() => setAbierto(false)} />
        </>
      )
    }
    renderConProviders(<Arnes />, { sesion: SESION_TEC })
    await rellenar('secreta1', 'abc', 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('La contraseña debe tener al menos 6 caracteres.')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Cambiar contraseña' })).not.toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }))
    const dlg = await screen.findByRole('dialog', { name: 'Cambiar contraseña' })
    expect(within(dlg).getByLabelText('Contraseña actual')).toHaveValue('')
    expect(within(dlg).getByLabelText('Nueva contraseña')).toHaveValue('')
    expect(within(dlg).queryByRole('alert')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 10: Ejecutar y ver que falla**

Run: `npx vitest run src/modules/gestion/cuenta/CambiarPasswordDialog.test.tsx`
Expected: FAIL, `Failed to resolve import "./CambiarPasswordDialog"`.

- [ ] **Step 11: Implementar el diálogo**

`src/modules/gestion/cuenta/CambiarPasswordDialog.tsx`:

```tsx
import { useState, type ReactNode } from 'react'
import { esErrorGestionadoGlobalmente, mensajeDeError } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Button } from '@/shared/ui/button'
import { CampoPassword } from '@/shared/ui/CampoPassword'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { useCambiarPassword } from './api'
import { MSG_EXITO, TITULO_EXITO, validarCambioPassword } from './validacion'

type Props = { abierto: boolean; onCerrar: () => void }

/** Campo de CambiarPasswordView.fxml (:24-105): fondo blanco, borde #D4D8DE, radio 8, padding 13 44 13 14 (44 a la derecha
 *  para el ojo), 13 px, texto #2C3B54, prompt #A0A8B4: los del login, que CampoPassword ya pinta con el ojo a la derecha. */
const CLASE_CAMPO =
  'h-auto rounded-lg border-borde-input bg-superficie py-[13px] pr-11 pl-3.5 text-[13px] md:text-[13px] text-azul-medio placeholder:text-texto-suave'

function Campo({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[11px] font-bold text-etiqueta-password">{etiqueta}</span>
      {children}
    </div>
  )
}

/** "Cambiar contraseña" (spec §6.3, G6): diálogo de 380 px sobre la vista actual, sin el marco de DialogoAlmacen: barra
 *  navy con el título y cuerpo blanco. No se cierra mientras responde (el JavaFX congela la ventana en la llamada). */
export function CambiarPasswordDialog({ abierto, onCerrar }: Props) {
  const cambiar = useCambiarPassword()
  return (
    <Dialog open={abierto} onOpenChange={(o) => { if (!o && !cambiar.isPending) onCerrar() }}>
      <DialogContent
        aria-describedby={undefined}
        showCloseButton={false}
        className="w-[380px] max-w-[min(380px,calc(100%-2rem))] gap-0 overflow-hidden border-0 bg-superficie p-0 sm:max-w-[min(380px,calc(100%-2rem))]"
      >
        <div className="bg-azul-noche px-5 py-4">
          <DialogTitle className="text-[15px] leading-normal font-bold text-white">Cambiar contraseña</DialogTitle>
        </div>
        {/* Hijo de DialogContent: Radix lo desmonta al cerrar, así que cada apertura empieza con los campos vacíos. */}
        <Cuerpo cambiar={cambiar} onCerrar={onCerrar} />
      </DialogContent>
    </Dialog>
  )
}

function Cuerpo({ cambiar, onCerrar }: { cambiar: ReturnType<typeof useCambiarPassword>; onCerrar: () => void }) {
  const { mostrarAviso } = useAlerta()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [confirmar, setConfirmar] = useState('')
  const [error, setError] = useState<string | null>(null)
  const enviando = cambiar.isPending

  /** Calco de `guardar` (:78-110): oculta el error, valida sin trim, envía y, con éxito, cierra y avisa. Un error deja
   *  los campos como estaban. 401 y conexión los lleva la política global (sesión caducada, banner y diálogo de
   *  conexión del MutationCache): no se repiten en la línea. */
  const guardar = () => {
    if (enviando) return
    setError(null)
    const fallo = validarCambioPassword(actual, nueva, confirmar)
    if (fallo !== null) {
      setError(fallo)
      return
    }
    cambiar.mutate(
      { passwordActual: actual, passwordNueva: nueva },
      {
        onSuccess: () => {
          onCerrar()
          mostrarAviso(TITULO_EXITO, MSG_EXITO)
        },
        onError: (e) => {
          if (!esErrorGestionadoGlobalmente(e)) setError(mensajeDeError(e))
        },
      },
    )
  }

  return (
    <form noValidate onSubmit={(e) => { e.preventDefault(); guardar() }} className="flex flex-col gap-4 bg-superficie p-6">
      <Campo etiqueta="Contraseña actual">
        <CampoPassword valor={actual} onChange={setActual} placeholder="Contraseña actual" aria-label="Contraseña actual" autoComplete="current-password" autoFocus className={CLASE_CAMPO} />
      </Campo>
      <Campo etiqueta="Nueva contraseña">
        <CampoPassword valor={nueva} onChange={setNueva} placeholder="Nueva contraseña" aria-label="Nueva contraseña" autoComplete="new-password" className={CLASE_CAMPO} />
      </Campo>
      <Campo etiqueta="Confirmar nueva contraseña">
        {/* Placeholder "Confirmar contraseña", distinto de su etiqueta: calco (CambiarPasswordView.fxml :84, :89). */}
        <CampoPassword valor={confirmar} onChange={setConfirmar} placeholder="Confirmar contraseña" aria-label="Confirmar nueva contraseña" autoComplete="new-password" className={CLASE_CAMPO} />
      </Campo>
      {error !== null && <p role="alert" className="text-[12px] text-error-password">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          disabled={enviando}
          onClick={onCerrar}
          className="h-auto rounded-md border-fila-sep bg-superficie px-[18px] py-2 text-[13px] font-normal text-azul-medio shadow-none hover:bg-superficie"
        >
          Cancelar
        </Button>
        <Button type="submit" disabled={enviando} className="h-auto rounded-md bg-azul-noche px-[18px] py-2 text-[13px] font-bold text-white hover:bg-azul-noche-hover">
          Guardar
        </Button>
      </div>
    </form>
  )
}
```

- [ ] **Step 12: Ejecutar y ver que pasa**

Run: `npx vitest run src/modules/gestion/cuenta`
Expected: PASS, 3 ficheros, 28 tests (10 + 2 + 16).

- [ ] **Step 13: Tests del menú de usuario (fallan)**

En `src/app/shell/TopBar.test.tsx`, sustituir los imports (`:1-10`) por:

```tsx
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { Route, useLocation } from 'react-router'
import { ultimaRutaStock } from '@/modules/almacen/estado'
import { handlersNotificaciones } from '@/modules/taller/notificaciones/test/handlers'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { server } from '@/test/server'
import { AppLayout } from './AppLayout'
```

(`useLocation` se conserva: lo añadió la Task 7 para `Destino` y los dos tests de `volverA` lo usan; si falta, sale TS2552 y "ReferenceError: useLocation is not defined". Si la Task 7 ya añadió alguno de estos nombres, no se duplica.)

En el tercer caso que añadió la Task 7 (`'"Cambiar contraseña" ya no navega a /cuenta/cambiar-password …'`), la última línea

```tsx
    expect(screen.getByRole('button', { name: /Hola, tecnico_n/ })).toBeInTheDocument()
```

pasa a ser (con el diálogo modal abierto la barra queda `aria-hidden` y el botón ya no está en el árbol accesible):

```tsx
    expect(await screen.findByRole('dialog', { name: 'Cambiar contraseña' })).toBeInTheDocument()
```

Y añadir al final del fichero:

```tsx
describe('"Cambiar contraseña" desde el menú de usuario (spec §6.3, G6)', () => {
  // En /reparaciones el lateral de TECNICO y SUPERTECNICO pide los contadores: sin handler, MSW corta la petición,
  // sale el diálogo modal de conexión y el menú queda con pointer-events: none. Mismo handler que la Task 13.
  beforeEach(() => {
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 0 })))
  })
  it.each([
    ['técnico', SESION_TEC, /Hola, tecnico_n/],
    ['supertécnico', SESION_SUPER, /Hola, tecnico_f/],
    ['admin', SESION_ADMIN, /Hola, admin/],
  ])('%s: abre el diálogo sobre la vista actual, sin navegar', async (_rol, sesion, saludo) => {
    server.use(...handlersNotificaciones())
    renderConProviders(<AppLayout />, {
      sesion, ruta: '/reparaciones',
      rutas: <Route path="/cuenta/cambiar-password" element={<p>RUTA VIEJA</p>} />,
    })
    await userEvent.click(screen.getByRole('button', { name: saludo }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar contraseña' }))
    const dlg = await screen.findByRole('dialog', { name: 'Cambiar contraseña' })
    expect(within(dlg).getByLabelText('Contraseña actual')).toBeInTheDocument()
    expect(screen.queryByText('RUTA VIEJA')).not.toBeInTheDocument()
    expect(screen.getByText('FSGR:')).toBeInTheDocument()
  })
  it('"Cancelar" lo cierra y el menú se puede volver a abrir (sin pointer-events colgados)', async () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC, ruta: '/reparaciones' })
    await userEvent.click(screen.getByRole('button', { name: /Hola, tecnico_n/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar contraseña' }))
    const dlg = await screen.findByRole('dialog', { name: 'Cambiar contraseña' })
    await userEvent.click(within(dlg).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Cambiar contraseña' })).not.toBeInTheDocument())
    expect(document.body.style.pointerEvents).not.toBe('none')
    await userEvent.click(screen.getByRole('button', { name: /Hola, tecnico_n/ }))
    expect(await screen.findByRole('menuitem', { name: 'Cambiar contraseña' })).toBeInTheDocument()
  })
  it('guardar con éxito cierra el diálogo, avisa y deja al usuario en la misma vista', async () => {
    server.use(http.patch('*/api/auth/cambiar-password', () => new HttpResponse(null, { status: 204 })))
    renderConProviders(<AppLayout />, { sesion: SESION_TEC, ruta: '/reparaciones' })
    await userEvent.click(screen.getByRole('button', { name: /Hola, tecnico_n/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar contraseña' }))
    const dlg = await screen.findByRole('dialog', { name: 'Cambiar contraseña' })
    await userEvent.type(within(dlg).getByLabelText('Contraseña actual'), 'secreta1')
    await userEvent.type(within(dlg).getByLabelText('Nueva contraseña'), 'nueva123')
    await userEvent.type(within(dlg).getByLabelText('Confirmar nueva contraseña'), 'nueva123')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('dialog', { name: 'Información' })).toHaveTextContent('Contraseña cambiada correctamente.')
    expect(screen.queryByRole('dialog', { name: 'Cambiar contraseña' })).not.toBeInTheDocument()
    expect(screen.getByText('Hola, tecnico_n')).toBeInTheDocument()
  })
})
```

(`act` ya se importaba en `main` desde otra línea, `TopBar.test.tsx:5`; queda en la primera.)

Run: `npx vitest run src/app/shell/TopBar.test.tsx`
Expected: FAIL en los 5 tests nuevos y en el de la Task 7 recién cambiado (el ítem no abre nada: `Unable to find role="dialog" and name "Cambiar contraseña"`); los anteriores siguen en verde.

- [ ] **Step 14: Abrir el diálogo desde `UserMenu`**

`src/app/shell/UserMenu.tsx` queda así (lo de la Task 7 más el estado, el import y el diálogo):

```tsx
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { CambiarPasswordDialog } from '@/modules/gestion/cuenta/CambiarPasswordDialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import { useSession } from '@/shared/session/SessionProvider'
import { esAdmin } from '@/shared/session/storage'
import { useExportable } from '@/shared/ui/exportable'

export function UserMenu() {
  const { sesion, logout } = useSession()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const exportar = useExportable()
  // "Cambiar contraseña" es un diálogo sobre la vista actual (G6, calco del Stage modal): estado local, sin ruta.
  const [passwordAbierto, setPasswordAbierto] = useState(false)
  if (!sesion) return null
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger className="flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1 hover:bg-white/8">
          <img src="/user.png" alt="" className="h-7 w-7" />
          <span className="text-[12px] font-bold text-crema">Hola, {sesion.nombreUsuario}</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {esAdmin(sesion) && (
            <>
              <DropdownMenuItem onSelect={() => navigate('/gestion/tecnicos', { state: { volverA: pathname } })}>Gestionar técnicos</DropdownMenuItem>
              <DropdownMenuItem onSelect={() => navigate('/gestion/logs', { state: { volverA: pathname } })}>Ver logs</DropdownMenuItem>
              <DropdownMenuSeparator />
            </>
          )}
          <DropdownMenuItem disabled={!exportar} onSelect={() => exportar?.()}>Descargar CSV</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => setPasswordAbierto(true)}>Cambiar contraseña</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => { logout(); navigate('/login', { replace: true }) }}>Cerrar Sesión</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      {/* Fuera del DropdownMenu: el menú se cierra al elegir y el diálogo sigue montado. Portal de Radix: no ocupa sitio en
          la barra (la campana y el botón siguen siendo hermanos directos, TopBar.test). */}
      <CambiarPasswordDialog abierto={passwordAbierto} onCerrar={() => setPasswordAbierto(false)} />
    </>
  )
}
```

- [ ] **Step 15: Ejecutar y ver que pasa**

Run: `npx vitest run src/app/shell/TopBar.test.tsx`
Expected: PASS, todos (los de `main`, los de la Task 7 y los 5 nuevos).

- [ ] **Step 16: Lint, tipos y suite completa**

```bash
npm run check
```

Expected: lint sin errores ni warnings nuevos (`UserMenu` en `app` sí puede importar de `modules/gestion`; `cuenta/` no importa de otros módulos), `tsc -b` sin salida y toda la suite en verde (recuento previo + 33 de esta tarea).

- [ ] **Step 17: Commit**

```bash
git add src/modules/gestion/cuenta/validacion.ts src/modules/gestion/cuenta/validacion.test.ts src/modules/gestion/cuenta/api.ts src/modules/gestion/cuenta/api.test.tsx src/modules/gestion/cuenta/CambiarPasswordDialog.tsx src/modules/gestion/cuenta/CambiarPasswordDialog.test.tsx src/app/shell/UserMenu.tsx src/app/shell/TopBar.test.tsx
git commit -m "feat(gestion): diálogo de cambiar contraseña abierto desde el menú de usuario"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Tests añadidos: 33 (10 validación + 2 api + 16 diálogo + 5 menú).

---

---

## Task 13: Web — CSV de Asignaciones (14 columnas) y exportable de la vista

**Files:**
- Create: `src/modules/taller/asignaciones/csv.ts`
- Test: `src/modules/taller/asignaciones/csv.test.ts`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx:1-25` (imports) y `:80` (registro del exportable tras `visibles`)
- Test (modify): `src/modules/taller/asignaciones/AsignacionesPage.test.tsx:1-14` (imports) y final del fichero (`:371`, describe nuevo)
- Modify: `docs/paridad/asignaciones.md:191` (sección "## CSV" nueva, antes de "## Errores" en `:192`)

**Interfaces:**
- Consumes (todo existe en `main` cd6853b):
  - `ReparacionResumen` de `@/shared/api/client`; en `schema.d.ts:2957-2996`: `idRep: string` (`:2958`), `imei: string` (`:2959`), `nombreTecnico: string` (`:2960`, no nulo en el contrato; el JavaFX lo protege igualmente), `fechaAsig: string` (`:2962`), `esSolicitud: number` (`:2975`), `estadoSolicitud: string | null` (`:2977`), `stockSolicitud: number` (`:2980`), `modelo: string | null` (`:2985`), `comentarioAsignacion: string | null` (`:2986`), `urgente: boolean` (`:2988`), `esChasis: boolean` (`:2989`), `porCerrar: boolean` (`:2990`), `nombreTecnicoAsigna: string | null` (`:2992`), `cliente: string | null` (`:2995`).
  - `textoForzado(valor)` y `descargarCsv(nombreBase, cabeceras: string[], filas: string[][])` de `src/shared/lib/csv.ts:10-13,21-31`.
  - `formatear(iso, 'dd/MM/yyyy HH:mm')` de `src/shared/lib/fechas.ts:41-46` (hora de Madrid).
  - `tipoDe(idRep)` y `TIPO_TRABAJO[tipo].etiqueta` ("Reparación" / "Glass" / "Pulido") de `src/shared/lib/tipoTrabajo.ts:5-16`.
  - `textoCsvEntrega(rep)` de `src/modules/taller/lib/entregaGlass.ts:67-72` (A: `glassEntregadoAt`; AG: `entregadoAt`; pulido: `''`).
  - `traducirModelo(codigo)` de `src/modules/taller/lib/modelos.ts:23-30` (`null`/`''` → `''`).
  - `useRegistrarExportable(fn)` de `src/shared/ui/exportable.tsx:16-35`.
  - Fábricas de test `resumen`, `normal`, `glass` de `src/modules/taller/test/fabrica.ts:7-37`.
- Produces: `NOMBRE_CSV_ASIGNACIONES`, `CABECERAS_ASIGNACIONES`, `filaAsignacionCsv(r: ReparacionResumen): string[]` (firmas de INTERFACES.md).

**Supuestos:** ninguno sobre otras tareas: T13 solo toca el módulo `taller` y no depende de T6-T12. La referencia es el hotfix, `ReparacionControllerSuperTecnico.exportarCSV` (`:1170-1186`) y `filaAsignacion` (`:1242-1266`), leídos con `git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/ReparacionControllerSuperTecnico.java`: mismo orden de columnas, `"—"` para "Asignado por" nulo y el criterio de "En espera de pieza" de `CargaTecnicos.enEsperaDePieza`.

- [ ] **Step 1: Comprobar que no existe**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git branch --show-current
ls src/modules/taller/asignaciones/csv.ts 2>&1
grep -rn "useRegistrarExportable\|reparaciones_pendientes" src/modules/taller/asignaciones/
```

Expected: `feature/web-gestion`; `ls: cannot access …: No such file or directory`; el `grep` no imprime nada (la vista no registra exportable hoy y "Descargar CSV" sale deshabilitado en Asignaciones).

- [ ] **Step 2: El test del helper (falla)**

`src/modules/taller/asignaciones/csv.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { glass, normal, resumen } from '../test/fabrica'
import { CABECERAS_ASIGNACIONES, filaAsignacionCsv, NOMBRE_CSV_ASIGNACIONES } from './csv'

// Índices de las columnas, en el orden de CABECERAS_ASIGNACIONES.
const TIPO = 1
const TECNICO = 2
const MODELO = 4
const FECHA = 5
const COMENTARIO = 6
const CLIENTE = 7
const ASIGNADO_POR = 8
const URGENTE = 9
const CHASIS = 10
const POR_CERRAR = 11
const ENTREGADO = 12
const EN_ESPERA = 13

describe('CSV de Asignaciones (calco de filaAsignacion del hotfix 0.16.3)', () => {
  it('nombre del fichero y las 14 cabeceras exactas en orden', () => {
    expect(NOMBRE_CSV_ASIGNACIONES).toBe('reparaciones_pendientes')
    expect(CABECERAS_ASIGNACIONES).toEqual([
      'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por', 'Urgente',
      'Chasis', 'Por cerrar', 'Entregado', 'En espera de pieza',
    ])
  })

  it('fila completa de una reparación: todos los campos, fecha en hora de Madrid y "Sí" en las marcas', () => {
    const r = resumen({
      idRep: 'A20260916_1', imei: '000000000000000', nombreTecnico: 'tecnico-a', modelo: '14', fechaAsig: '2026-09-16T07:02:00',
      comentarioAsignacion: 'Pantalla rota', cliente: 'Cliente A', nombreTecnicoAsigna: 'tecnico-b', urgente: true, esChasis: true,
      porCerrar: true, glassAbierta: true, glassEntregadoAt: '2026-09-16T08:42:00', glassTecnicoNombre: 'tecnico-c',
      esSolicitud: 1, estadoSolicitud: 'PENDIENTE', stockSolicitud: 0,
    })
    expect(filaAsignacionCsv(r)).toEqual([
      'A20260916_1', 'Reparación', 'tecnico-a', '="000000000000000"', 'iPhone 14', '16/09/2026 09:02', 'Pantalla rota', 'Cliente A',
      'tecnico-b', 'Sí', 'Sí', 'Sí', '16/09/2026 10:42', 'Sí',
    ])
  })

  it('fecha de asignación en invierno: UTC+1', () => {
    expect(filaAsignacionCsv(resumen({ fechaAsig: '2026-01-15T07:02:00' }))[FECHA]).toBe('15/01/2026 08:02')
  })

  it('tipo por prefijo: A → Reparación, AG → Glass, AP → Pulido', () => {
    expect(filaAsignacionCsv(resumen({ idRep: 'A20260916_1' }))[TIPO]).toBe('Reparación')
    expect(filaAsignacionCsv(resumen({ idRep: 'AG20260916_2' }))[TIPO]).toBe('Glass')
    expect(filaAsignacionCsv(resumen({ idRep: 'AP20260916_3' }))[TIPO]).toBe('Pulido')
  })

  it('nulos: técnico, modelo, comentario y cliente vacíos; "Asignado por" nulo es "—"', () => {
    // El contrato declara nombreTecnico no nulo; el JavaFX lo protege igual (`!= null ? … : ""`) y aquí también.
    const fila = filaAsignacionCsv(resumen({
      nombreTecnico: null as unknown as string, modelo: null, comentarioAsignacion: null, cliente: null, nombreTecnicoAsigna: null,
    }))
    expect(fila[TECNICO]).toBe('')
    expect(fila[MODELO]).toBe('')
    expect(fila[COMENTARIO]).toBe('')
    expect(fila[CLIENTE]).toBe('')
    expect(fila[ASIGNADO_POR]).toBe('—')
    expect(filaAsignacionCsv(resumen({ modelo: '' }))[MODELO]).toBe('')
  })

  it('marcas a false son "No"', () => {
    const fila = filaAsignacionCsv(resumen({ urgente: false, esChasis: false, porCerrar: false }))
    expect([fila[URGENTE], fila[CHASIS], fila[POR_CERRAR]]).toEqual(['No', 'No', 'No'])
  })

  it('Entregado: fecha de la entrega en A y AG, vacío sin entrega y en pulidos', () => {
    expect(filaAsignacionCsv(normal(true, '2026-08-28T08:42:00'))[ENTREGADO]).toBe('28/08/2026 10:42')
    expect(filaAsignacionCsv(glass('2026-08-28T08:42:00'))[ENTREGADO]).toBe('28/08/2026 10:42')
    expect(filaAsignacionCsv(normal(true, null))[ENTREGADO]).toBe('')
    expect(filaAsignacionCsv(resumen({ idRep: 'AP20260828_1', entregadoAt: '2026-08-28T08:42:00' }))[ENTREGADO]).toBe('')
  })

  it('en espera de pieza: sin solicitud No; solicitud sin gestionar Sí; gestionada sin stock Sí; gestionada con stock No', () => {
    const espera = (p: Parameters<typeof resumen>[0]) => filaAsignacionCsv(resumen(p))[EN_ESPERA]
    expect(espera({ esSolicitud: 0, estadoSolicitud: null, stockSolicitud: 0 })).toBe('No')
    expect(espera({ esSolicitud: 2, estadoSolicitud: 'PENDIENTE', stockSolicitud: 5 })).toBe('Sí')
    expect(espera({ esSolicitud: 1, estadoSolicitud: 'GESTIONADA', stockSolicitud: 0 })).toBe('Sí')
    expect(espera({ esSolicitud: 1, estadoSolicitud: 'GESTIONADA', stockSolicitud: 3 })).toBe('No')
  })
})
```

- [ ] **Step 3: Ejecutarlo**

```bash
npx vitest run src/modules/taller/asignaciones/csv.test.ts
```

Expected: FAIL, `Failed to resolve import "./csv"` (el módulo no existe).

- [ ] **Step 4: El helper**

`src/modules/taller/asignaciones/csv.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'
import { textoForzado } from '@/shared/lib/csv'
import { formatear } from '@/shared/lib/fechas'
import { TIPO_TRABAJO, tipoDe } from '@/shared/lib/tipoTrabajo'
import { textoCsvEntrega } from '../lib/entregaGlass'
import { traducirModelo } from '../lib/modelos'

/** Calco de ReparacionControllerSuperTecnico.exportarCSV con la tabla de asignaciones visible (hotfix 0.16.3, :1170-1186):
 *  fichero `reparaciones_pendientes`, las filas visibles tras los filtros y estas 14 columnas (spec SP6 §6.5, G7). */
export const NOMBRE_CSV_ASIGNACIONES = 'reparaciones_pendientes'

export const CABECERAS_ASIGNACIONES = [
  'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por', 'Urgente', 'Chasis',
  'Por cerrar', 'Entregado', 'En espera de pieza',
]

const siNo = (valor: boolean) => (valor ? 'Sí' : 'No')

/** Mismo criterio que CargaTecnicos.enEsperaDePieza: solicitud activa y aún no recibida (gestionada y con stock). */
function enEsperaDePieza(r: ReparacionResumen): boolean {
  return r.esSolicitud > 0 && !(r.estadoSolicitud === 'GESTIONADA' && r.stockSolicitud > 0)
}

/** Calco de filaAsignacion (hotfix 0.16.3, :1242-1266). */
export function filaAsignacionCsv(r: ReparacionResumen): string[] {
  return [
    r.idRep,
    TIPO_TRABAJO[tipoDe(r.idRep)].etiqueta,
    r.nombreTecnico ?? '',
    textoForzado(r.imei),
    traducirModelo(r.modelo),
    formatear(r.fechaAsig, 'dd/MM/yyyy HH:mm'),
    r.comentarioAsignacion ?? '',
    r.cliente ?? '',
    r.nombreTecnicoAsigna ?? '—',
    siNo(r.urgente),
    siNo(r.esChasis),
    siNo(r.porCerrar),
    textoCsvEntrega(r),
    siNo(enEsperaDePieza(r)),
  ]
}
```

- [ ] **Step 5: Ejecutarlo**

```bash
npx vitest run src/modules/taller/asignaciones/csv.test.ts
```

Expected: PASS, `Tests  8 passed (8)`.

- [ ] **Step 6: El test del exportable en la vista (falla)**

En `src/modules/taller/asignaciones/AsignacionesPage.test.tsx`, junto a los imports de `:8-11` (tras `import { server } from '@/test/server'`):

```ts
import * as csv from '@/shared/lib/csv'
```

y al final del fichero (tras el `})` de `:371`):

```tsx
/**
 * "Descargar CSV" vive en el menú de usuario del AppLayout real (TopBar), como en PendientesPage.test.tsx: la vista solo
 * registra el exportador. Se filtra por IMEI para comprobar que exporta las filas VISIBLES, no la lista completa.
 */
describe('AsignacionesPage · CSV (spec SP6 §6.5)', () => {
  beforeEach(() => {
    // AppLayout con supertécnico monta la campana de la barra y el badge de Pendientes del lateral.
    server.use(...handlersNotificaciones())
    server.use(http.get('*/api/reparaciones/pendientes/contadores', () => HttpResponse.json({ reparaciones: 0, glass: 0, pulidos: 0 })))
  })

  it('"Descargar CSV" exporta reparaciones_pendientes con las 14 columnas y solo las filas visibles', async () => {
    const descargar = vi.spyOn(csv, 'descargarCsv').mockImplementation(() => {})
    const usuario = userEvent.setup()
    renderConProviders(<AsignacionesPage />, { sesion: SESION_SUPER, ruta: '/reparaciones/asignaciones', layout: <AppLayout /> })
    await screen.findByText('A20260916_1')
    await usuario.type(screen.getByPlaceholderText('Filtrar por IMEI'), '000000000000003')
    await waitFor(() => expect(screen.queryByText('A20260916_1')).not.toBeInTheDocument())
    await usuario.click(screen.getByRole('button', { name: /Hola,/ }))
    await usuario.click(await screen.findByRole('menuitem', { name: 'Descargar CSV' }))
    expect(descargar).toHaveBeenCalledTimes(1)
    const [base, cabeceras, filas] = descargar.mock.calls[0]
    expect(base).toBe('reparaciones_pendientes')
    expect(cabeceras).toEqual([
      'ID', 'Tipo', 'Técnico', 'IMEI', 'Modelo', 'Fecha asignación', 'Comentario', 'Cliente', 'Asignado por', 'Urgente',
      'Chasis', 'Por cerrar', 'Entregado', 'En espera de pieza',
    ])
    // La fila de pulido del beforeEach de arriba: resumen() con idRep AP…, IMEI …003 y cliente "CLIENTE A".
    expect(filas).toEqual([
      ['AP20260916_3', 'Pulido', 'Técnico A', '="000000000000003"', 'iPhone 14', '16/09/2026 09:02', '', 'CLIENTE A', 'Técnico F', 'No', 'No', 'No', '', 'No'],
    ])
    descargar.mockRestore()
  })
})
```

(`waitFor`, `userEvent`, `http`, `HttpResponse`, `vi`, `AppLayout`, `renderConProviders`, `SESION_SUPER` y `handlersNotificaciones` ya están importados en `:1-12`; los nombres "Técnico A"/"Técnico F"/"CLIENTE A" son los sintéticos de la fábrica y del `beforeEach` del fichero.)

```bash
npx vitest run src/modules/taller/asignaciones/AsignacionesPage.test.tsx -t "Descargar CSV"
```

Expected: FAIL: `expected "spy" to be called 1 times, but got 0 times` (el ítem "Descargar CSV" sale deshabilitado porque la vista no registra exportador).

- [ ] **Step 7: Registrar el exportable en la vista**

En `src/modules/taller/asignaciones/AsignacionesPage.tsx`, imports (orden del fichero): antes de `import { hoyMadrid } from '@/shared/lib/fechas'` (`:3`):

```ts
import { descargarCsv } from '@/shared/lib/csv'
```

tras `import { EtiquetaActualizado } from '@/shared/ui/EtiquetaActualizado'` (`:9`):

```ts
import { useRegistrarExportable } from '@/shared/ui/exportable'
```

tras `import { contarTecnicosPorImei } from './conteoTecnicos'` (`:18`):

```ts
import { CABECERAS_ASIGNACIONES, filaAsignacionCsv, NOMBRE_CSV_ASIGNACIONES } from './csv'
```

y tras `const visibles = useMemo(() => aplicarFiltros(data, filtros), [data, filtros])` (`:80`):

```tsx
  // "Descargar CSV" del menú de usuario (spec SP6 §6.5, G7): las filas visibles tras los filtros, en el orden de la tabla,
  // como el exportarCSV del hotfix con la tabla de asignaciones a la vista. Lo tienen quienes ven la vista
  // (SUPERTECNICO y ADMIN); el ADMIN también exporta aunque la vista sea de solo lectura.
  useRegistrarExportable(() => descargarCsv(NOMBRE_CSV_ASIGNACIONES, CABECERAS_ASIGNACIONES, visibles.map(filaAsignacionCsv)))
```

- [ ] **Step 8: Ejecutar, lint y tipos**

```bash
npx vitest run src/modules/taller/asignaciones/
npm run lint
npx tsc -b
```

Expected: toda la carpeta en verde, `AsignacionesPage.test.tsx` con **18** tests (17 + 1) y `csv.test.ts` con 8; lint y `tsc -b` limpios (el `.ts` de helpers no exporta componentes; `taller` no importa de otro módulo).

- [ ] **Step 9: Ficha de Asignaciones**

En `docs/paridad/asignaciones.md`, antes de `## Errores` (`:192`), sección nueva:

```md
## CSV

- [ ] "Descargar CSV" del menú de usuario (spec SP6 §6.5, G7; antes deshabilitado en esta vista) descarga `reparaciones_pendientes_<fecha>_<hora>.csv` con las filas visibles tras los filtros, en el orden de la tabla, y las 14 columnas `ID;Tipo;Técnico;IMEI;Modelo;Fecha asignación;Comentario;Cliente;Asignado por;Urgente;Chasis;Por cerrar;Entregado;En espera de pieza` (calco de `filaAsignacion` del hotfix): Tipo "Reparación"/"Glass"/"Pulido" por el prefijo; IMEI como `="…"`; modelo traducido o vacío; fecha `dd/MM/yyyy HH:mm` en hora de Madrid; técnico, comentario y cliente vacíos si faltan; "Asignado por" vacío como "—"; "Sí"/"No" en Urgente, Chasis y Por cerrar; "Entregado" con la fecha de la entrega a glass (fila A: la de su glass; fila AG: la suya; vacío sin entrega y en pulidos); "En espera de pieza" "Sí" con solicitud activa salvo gestionada con stock. Para SUPERTECNICO y ADMIN (`gestion/asig-csv`, captura nueva del SP6).
```

- [ ] **Step 10: Commit**

```bash
git add src/modules/taller/asignaciones/csv.ts src/modules/taller/asignaciones/csv.test.ts src/modules/taller/asignaciones/AsignacionesPage.tsx src/modules/taller/asignaciones/AsignacionesPage.test.tsx docs/paridad/asignaciones.md
git commit -m "feat(asignaciones): csv de la vista con las 14 columnas del hotfix y exportable registrado"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Recuento: **+9 tests** (8 de `csv.test.ts` + 1 de `AsignacionesPage.test.tsx`).

---

## Task 14: Web — smoke Playwright de Gestión (`gestion.spec.ts`)

**Files:**
- Create: `tests/e2e/gestion.spec.ts`
- Modify: `.env.e2e.example` (hoy sin `ADMIN_USER`/`ADMIN_PASS`: bloque nuevo al final del fichero, que tiene 22 líneas: tras `:22`), `README.md` (sección "Smoke e2e", `:28-54`: párrafo nuevo tras el de `pedidos.spec.ts` en `:48-52` y frase final `:53-54`)

**Interfaces:**
- Consumes: `credenciales(variableUsuario, variableClave)` de `tests/e2e/credenciales.ts:5-10`; el patrón de login de `tests/e2e/stock.spec.ts:22-29` y de llamada a la API con el token de `sessionStorage['fsgr.sesion']` de `tests/e2e/pedidos.spec.ts` (`llamarApi`; clave en `src/shared/session/storage.ts:9`); `workers: 1` de `playwright.config.ts:7`. La forma `Usuario { idUsu, nombreUsuario, rol, idTec, nombreTecnico, activo }` del contrato (`schema.d.ts:2751-2760`) se repite a mano (el e2e no importa código de la app, `asignar.spec.ts:4-9`).
- Rutas (contrato de `cd6853b`; T5/T6 no las cambian de forma): `POST`/`GET /api/usuarios/tecnicos` (`:183`), `PATCH …/{idTec}/desactivar` (`:695`) y `…/activar` (`:711`) con 204 (`UsuarioController.java:61-80`), `GET …/{idTec}/tiene-reparaciones` (`:1335`), `DELETE …/{idTec}?idUsu=` (`:2071`, 204), `PATCH /api/auth/cambiar-password` (`:1319`, 204), `GET /api/logs` (`:1895`) con `accion`, `desde`, `hasta` y el `limite` que añade T4/T6, `POST /api/auth/login` (`:631`).
- Produces: el smoke que U4 (Task 15) ejecuta con OK del usuario.

**Supuestos** (se comprueban contra el código real de T6-T12 antes de escribir el test; si no cuadran, manda el código y se anota en la ejecución):
- Login (T6): placeholders "Usuario" y "Contraseña" (el `CampoPassword` recibe `placeholder="Contraseña"`), botón "Iniciar Sesión", y tras entrar se ve "FSGR:".
- Menú de usuario: disparador `button` con nombre `/Hola,/`; ítems `menuitem` "Gestionar técnicos", "Ver logs", "Cambiar contraseña", "Cerrar Sesión".
- Técnicos (T9): texto "Gestión de usuarios"; inputs con placeholders "Nombre visible en reparaciones", "Credencial de login", "Contraseña" (exacto) y "Repite la contraseña"; combo de rol con "TECNICO" por defecto; botón "Registrar técnico"; en la fila, candado `button` con nombre accesible "Desactivar acceso"/"Activar acceso" (`aria-label` de INTERFACES) y badge "Activo"/"Inactivo"; papelera = `button` que contiene `img[src="/borrar.png"]` (se localiza por la imagen porque la spec no le da tooltip); `ConfirmDialog` `dialog` "Eliminar técnico" con el botón "Eliminar"; pie "Cerrar" (se localiza por texto exacto, sea botón o enlace).
- Logs (T11): texto "Log de actividad"; buscador placeholder "Buscar..."; `CampoAutocompletar` de acción con placeholder "Acción..." y opciones `role="option"` (`CampoAutocompletar.tsx:76-116`); `RangoFechas` con etiquetas "Desde:"/"Hasta:" asociadas a `input type="date"` (`RangoFechas.tsx:12-15`); columnas Fecha · Usuario · Acción · Detalle (la celda de Usuario es la segunda, índice 1); el doble clic abre el `dialog` "Detalle del log" de `mostrarTexto` (`AlertaProvider.tsx:65-83`), que se cierra con Escape.
- Contraseña (T12): `dialog` con nombre "Cambiar contraseña" (su `DialogTitle`); placeholders "Contraseña actual", "Nueva contraseña", "Confirmar contraseña"; botón "Guardar"; éxito → `dialog` "Información" con "Contraseña cambiada correctamente." y "Aceptar" (`mostrarAviso`, `AlertaProvider.tsx:42,57-61`).

Variables: `ADMIN_USER`/`ADMIN_PASS` (ya están en `~/.env.e2e`; faltan en `.env.e2e.example`, se añaden en el Step 2). Sin ellas el test se salta. **Límite de inicios de sesión**: nginx permite `rate=5r/m` con `burst=3 nodelay` (`deploy/nginx/default.conf:2,28`): cuatro inicios seguidos pasan (1 + ráfaga de 3) y cada 12 s se recupera uno. El test hace exactamente **cuatro** (ADMIN, usuario de prueba, usuario de prueba con la contraseña nueva, ADMIN) y no espera entre ellos; la limpieza de `afterAll`, que solo corre si el borrado por la interfaz no llegó a hacerse, sería el quinto: **espera 12 s antes** de su login (con `setTimeout`, porque en `afterAll` no hay página). Los nombres creados se registran (anotación del test y variable `pendiente`) **antes** de pulsar "Registrar técnico".

Logs que deja: `CREAR_USUARIO`, `DESACTIVAR_USUARIO`, `ACTIVAR_USUARIO`, `ELIMINAR_USUARIO` y dos `LOGIN` del ADMIN (no se pueden borrar: van a la limpieza anotada en U12). Los `LOGIN` y `CAMBIAR_PASSWORD` del usuario de prueba se borran con él (el `DELETE` borra su `Log_Actividad`, G8).

- [ ] **Step 1: El test**

`tests/e2e/gestion.spec.ts`:

```ts
import { expect, test, type Page, type Response } from '@playwright/test'
import { credenciales } from './credenciales.ts'

/** Forma de GET /api/usuarios/tecnicos (schema.d.ts: Usuario). Se repite a mano porque el e2e no importa código de la app. */
type UsuarioApi = { idUsu: number; nombreUsuario: string; rol: string; idTec: number; nombreTecnico: string; activo: boolean }

/** Usuario de prueba que puede seguir vivo si el test falla antes del borrado por la interfaz; afterAll lo borra por la API. */
let pendiente: { nombreUsuario: string } | null = null

const escaparRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const esRespuesta = (ruta: string, metodo: string) => (r: Response) => new URL(r.url()).pathname === ruta && r.request().method() === metodo

/** Fecha civil de hoy en Madrid ('yyyy-MM-dd'), el formato de los <input type="date"> de RangoFechas. */
const hoyMadrid = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())

async function entrarCon(page: Page, usuario: string, clave: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(usuario)
  await page.getByPlaceholder('Contraseña').fill(clave)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()
  // La barra se pinta antes de que `<Navigate>` lleve de /reparaciones a /historial (ADMIN) o /pendientes (TECNICO):
  // se espera la URL final porque `origen` y la `ruta` de `cambiarPassword` leen `page.url()` después de la redirección.
  await expect(page).toHaveURL(/\/reparaciones\/(historial|pendientes)$/)
}

async function menuUsuario(page: Page, item: string) {
  await page.getByRole('button', { name: /Hola,/ }).click()
  await page.getByRole('menuitem', { name: item, exact: true }).click()
}

async function cerrarSesion(page: Page) {
  await menuUsuario(page, 'Cerrar Sesión')
  await expect(page).toHaveURL(/\/login$/)
}

/** GET /api/usuarios/tecnicos con el token de la sesión de la página (misma origin que la app; patrón de pedidos.spec.ts). */
async function usuariosTecnicos(page: Page): Promise<UsuarioApi[]> {
  return page.evaluate(async () => {
    const sesion = JSON.parse(sessionStorage.getItem('fsgr.sesion') ?? '{}') as { token?: string }
    const r = await fetch('/api/usuarios/tecnicos', { headers: { Authorization: `Bearer ${sesion.token}` } })
    if (!r.ok) throw new Error(`GET /api/usuarios/tecnicos: ${r.status}`)
    return (await r.json()) as UsuarioApi[]
  })
}

/** Abre "Cambiar contraseña" desde el menú, comprueba que no navega y cambia `actual` por `nueva`. */
async function cambiarPassword(page: Page, actual: string, nueva: string) {
  const ruta = new URL(page.url()).pathname
  await menuUsuario(page, 'Cambiar contraseña')
  const dlg = page.getByRole('dialog', { name: 'Cambiar contraseña' })
  await expect(dlg).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`${escaparRegex(ruta)}$`))
  await dlg.getByPlaceholder('Contraseña actual', { exact: true }).fill(actual)
  await dlg.getByPlaceholder('Nueva contraseña', { exact: true }).fill(nueva)
  await dlg.getByPlaceholder('Confirmar contraseña', { exact: true }).fill(nueva)
  const respuesta = page.waitForResponse(esRespuesta('/api/auth/cambiar-password', 'PATCH'))
  await dlg.getByRole('button', { name: 'Guardar' }).click()
  expect((await respuesta).status(), 'PATCH /api/auth/cambiar-password').toBe(204)
  await expect(dlg).toBeHidden()
  const aviso = page.getByRole('dialog', { name: 'Información' })
  await expect(aviso.getByText('Contraseña cambiada correctamente.')).toBeVisible()
  await aviso.getByRole('button', { name: 'Aceptar' }).click()
  await expect(page).toHaveURL(new RegExp(`${escaparRegex(ruta)}$`))
}

/**
 * ESCRIBE en el entorno de destino: con ADMIN_USER registra un técnico de prueba `e2e-tecnico-<marca>` (usuario
 * `e2e-usuario-<marca>`, rol TECNICO, contraseña sintética), lo desactiva y lo activa, busca su CREAR_USUARIO en el visor de
 * logs, entra con él y cambia su contraseña por el diálogo (y la restaura), y por último lo borra como ADMIN desde la
 * papelera. Solo toca ese usuario: las escrituras de /api/usuarios/tecnicos/{id} a otro id se abortan.
 */
test('admin: técnico de prueba registrado, bloqueado y desbloqueado, visto en el log, con contraseña cambiada y borrado', async ({ page }) => {
  test.setTimeout(180_000)
  const admin = credenciales('ADMIN_USER', 'ADMIN_PASS')
  const marca = Date.now()
  const nombreTecnico = `e2e-tecnico-${marca}`
  const nombreUsuario = `e2e-usuario-${marca}`
  const clave = `e2e-clave-${marca}`
  const claveNueva = `e2e-nueva-${marca}`

  await entrarCon(page, admin.usuario, admin.clave) // inicio de sesión 1/4
  const origen = new URL(page.url()).pathname

  let idTec = 0
  let idUsu = 0
  await test.step('alta desde "Gestionar técnicos"', async () => {
    const lista = page.waitForResponse(esRespuesta('/api/usuarios/tecnicos', 'GET'))
    await menuUsuario(page, 'Gestionar técnicos')
    await expect(page).toHaveURL(/\/gestion\/tecnicos$/)
    await expect(page.getByText('Gestión de usuarios', { exact: true })).toBeVisible()
    expect((await lista).ok()).toBe(true)

    await page.getByPlaceholder('Nombre visible en reparaciones').fill(nombreTecnico)
    await page.getByPlaceholder('Credencial de login').fill(nombreUsuario)
    await page.getByPlaceholder('Contraseña', { exact: true }).fill(clave)
    await page.getByPlaceholder('Repite la contraseña').fill(clave)

    // Se registra ANTES del clic: si el alta llega a escribir y el test cae después, afterAll sabe qué borrar.
    pendiente = { nombreUsuario }
    test.info().annotations.push({ type: 'e2e-creado', description: `técnico ${nombreTecnico} / usuario ${nombreUsuario}` })
    const alta = page.waitForResponse(esRespuesta('/api/usuarios/tecnicos', 'POST'))
    await page.getByRole('button', { name: 'Registrar técnico' }).click()
    const respuesta = await alta
    expect(respuesta.status(), 'POST /api/usuarios/tecnicos').toBe(201)
    expect(respuesta.request().postDataJSON()).toEqual({ nombreTecnico, nombreUsuario, password: clave, rol: 'TECNICO' })
    // Sin mensaje de éxito (calco): el formulario se vacía.
    await expect(page.getByPlaceholder('Nombre visible en reparaciones')).toHaveValue('')

    const creados = (await usuariosTecnicos(page)).filter((u) => u.nombreUsuario === nombreUsuario)
    expect(creados).toHaveLength(1)
    expect(creados[0]).toMatchObject({ nombreTecnico, rol: 'TECNICO', activo: true })
    idTec = creados[0].idTec
    idUsu = creados[0].idUsu
  })

  // A partir de aquí solo se deja escribir sobre el técnico de prueba (patrón soloBorrar de pedidos.spec.ts).
  const propias = new Set([
    `/api/usuarios/tecnicos/${idTec}`, `/api/usuarios/tecnicos/${idTec}/activar`, `/api/usuarios/tecnicos/${idTec}/desactivar`,
  ])
  await page.route('**/api/usuarios/tecnicos/**', (route) => {
    const req = route.request()
    if (req.method() !== 'GET' && !propias.has(new URL(req.url()).pathname)) return route.abort()
    return route.continue()
  })
  const fila = page.getByRole('row').filter({ hasText: nombreTecnico })

  await test.step('fila nueva, desactivar y activar con el candado', async () => {
    await expect(fila).toHaveCount(1)
    await expect(fila).toContainText(nombreUsuario)
    await expect(fila).toContainText('TECNICO')
    await expect(fila.getByText('Activo', { exact: true })).toBeVisible()

    const desactivar = page.waitForResponse(esRespuesta(`/api/usuarios/tecnicos/${idTec}/desactivar`, 'PATCH'))
    await fila.getByRole('button', { name: 'Desactivar acceso' }).click()
    expect((await desactivar).status(), `PATCH /api/usuarios/tecnicos/${idTec}/desactivar`).toBe(204)
    await expect(fila.getByText('Inactivo', { exact: true })).toBeVisible()

    const activar = page.waitForResponse(esRespuesta(`/api/usuarios/tecnicos/${idTec}/activar`, 'PATCH'))
    await fila.getByRole('button', { name: 'Activar acceso' }).click()
    expect((await activar).status(), `PATCH /api/usuarios/tecnicos/${idTec}/activar`).toBe(204)
    await expect(fila.getByText('Activo', { exact: true })).toBeVisible()
    await expect(fila.getByRole('button', { name: 'Desactivar acceso' })).toBeVisible()

    // "Cerrar" vuelve a la vista desde la que el menú abrió la página (volverA).
    await page.getByText('Cerrar', { exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`${escaparRegex(origen)}$`))
  })

  await test.step('CREAR_USUARIO en "Ver logs" filtrando por acción y por el día de hoy', async () => {
    const hoy = hoyMadrid()
    await menuUsuario(page, 'Ver logs')
    await expect(page).toHaveURL(/\/gestion\/logs$/)
    await expect(page.getByText('Log de actividad', { exact: true })).toBeVisible()
    await page.getByLabel('Desde:').fill(hoy)
    await page.getByLabel('Hasta:').fill(hoy)

    // La espera se registra ANTES de elegir la acción y exige los tres filtros y el tope de 1.000 en la query.
    const filtrada = page.waitForResponse((r) => {
      const u = new URL(r.url())
      return u.pathname === '/api/logs' && r.request().method() === 'GET' && u.searchParams.get('accion') === 'CREAR_USUARIO'
        && u.searchParams.get('desde') === hoy && u.searchParams.get('hasta') === hoy && u.searchParams.get('limite') === '1000'
    })
    await page.getByPlaceholder('Acción...').fill('CREAR_USUARIO')
    await page.getByRole('option', { name: 'CREAR_USUARIO', exact: true }).click()
    expect((await filtrada).ok(), 'GET /api/logs?accion=CREAR_USUARIO').toBe(true)

    // El filtro "Técnico..." no lista ADMIN (calco), así que el autor se comprueba en la celda Usuario.
    const detalle = `NOMBRE_USUARIO: ${nombreUsuario}, ROL: TECNICO, TECNICO: ${nombreTecnico}`
    const filaLog = page.getByRole('row').filter({ hasText: detalle })
    await expect(filaLog).toHaveCount(1)
    await expect(filaLog.getByRole('cell').nth(1)).toHaveText(admin.usuario)
    await expect(filaLog).toContainText('CREAR_USUARIO')

    // Buscador en memoria (sin volver al servidor) y detalle por doble clic.
    await page.getByPlaceholder('Buscar...').fill(nombreUsuario)
    await expect(filaLog).toHaveCount(1)
    await filaLog.dblclick()
    const popup = page.getByRole('dialog', { name: 'Detalle del log' })
    await expect(popup).toContainText(detalle)
    await page.keyboard.press('Escape')
    await expect(popup).toBeHidden()
  })

  await test.step('el usuario de prueba cambia su contraseña desde el menú y vuelve a entrar con la nueva', async () => {
    await cerrarSesion(page)
    await entrarCon(page, nombreUsuario, clave) // inicio de sesión 2/4
    await cambiarPassword(page, clave, claveNueva)
    await cerrarSesion(page)
    await entrarCon(page, nombreUsuario, claveNueva) // inicio de sesión 3/4
    await cambiarPassword(page, claveNueva, clave) // restaurada: si el borrado fallase, el usuario queda con la original
    await cerrarSesion(page)
  })

  await test.step('el ADMIN lo borra desde la papelera', async () => {
    await entrarCon(page, admin.usuario, admin.clave) // inicio de sesión 4/4
    await menuUsuario(page, 'Gestionar técnicos')
    await expect(fila).toHaveCount(1)
    const comprobacion = page.waitForResponse(esRespuesta(`/api/usuarios/tecnicos/${idTec}/tiene-reparaciones`, 'GET'))
    await fila.locator('button:has(img[src="/borrar.png"])').click()
    const tiene = await comprobacion
    expect(tiene.ok()).toBe(true)
    expect(await tiene.json()).toEqual({ value: false })

    const confirmar = page.getByRole('dialog', { name: 'Eliminar técnico' })
    await expect(confirmar).toContainText(`¿Eliminar a "${nombreTecnico}" definitivamente?`)
    const borrado = page.waitForResponse(esRespuesta(`/api/usuarios/tecnicos/${idTec}`, 'DELETE'))
    await confirmar.getByRole('button', { name: 'Eliminar', exact: true }).click()
    const respuesta = await borrado
    expect(respuesta.status(), `DELETE /api/usuarios/tecnicos/${idTec}`).toBe(204)
    expect(new URL(respuesta.url()).searchParams.get('idUsu')).toBe(String(idUsu))
    pendiente = null
    await expect(fila).toHaveCount(0)
  })
})

/**
 * Limpieza por la API si el test cayó con el usuario de prueba creado. Busca por el nombre de usuario EXACTO y borra solo
 * esa fila; los fallos se señalan con expect.soft nombrando la ruta, sin tapar el fallo original.
 */
test.afterAll(async ({ playwright }, testInfo) => {
  const restante = pendiente
  const usuario = process.env.ADMIN_USER
  const clave = process.env.ADMIN_PASS
  if (!restante || !usuario || !clave) return
  // El test ya ha gastado hasta cuatro inicios de sesión; nginx admite 5 por minuto con ráfaga de 3 (uno nuevo cada 12 s).
  await new Promise((r) => setTimeout(r, 12_000))
  const ctx = await playwright.request.newContext({ baseURL: testInfo.project.use.baseURL })
  try {
    const login = await ctx.post('/api/auth/login', { data: { usuario, password: clave } })
    expect.soft(login.status(), 'limpieza POST /api/auth/login').toBe(200)
    if (!login.ok()) return
    const { token } = (await login.json()) as { token: string }
    const headers = { Authorization: `Bearer ${token}` }
    const lista = await ctx.get('/api/usuarios/tecnicos', { headers })
    expect.soft(lista.status(), 'limpieza GET /api/usuarios/tecnicos').toBe(200)
    if (!lista.ok()) return
    const quedan = ((await lista.json()) as UsuarioApi[]).filter((u) => u.nombreUsuario === restante.nombreUsuario)
    for (const u of quedan) {
      const ruta = `/api/usuarios/tecnicos/${u.idTec}?idUsu=${u.idUsu}`
      const r = await ctx.delete(ruta, { headers })
      expect.soft(r.status(), `limpieza DELETE ${ruta}`).toBe(204)
    }
  } finally {
    await ctx.dispose()
  }
})
```

- [ ] **Step 2: `.env.e2e.example` y README**

Al final de `.env.e2e.example` (el fichero tiene 22 líneas: el bloque va tras la l.22):

```
# gestion.spec.ts ESCRIBE: con ADMIN_USER registra un técnico de prueba "e2e-tecnico-<marca>" (usuario
# "e2e-usuario-<marca>", rol TECNICO, contraseña sintética), lo desactiva y lo activa, busca su CREAR_USUARIO en "Ver logs",
# entra con él, cambia su contraseña por el diálogo y la restaura, y lo borra como ADMIN. Si algo falla, lo borra por la API.
ADMIN_USER=<admin>
ADMIN_PASS=<contraseña>
```

En `README.md`, sección "Smoke e2e", tras el párrafo de `pedidos.spec.ts` (`:48-52`, termina en "así que no toca stock."):

```
`gestion.spec.ts` también **escribe**: con `ADMIN_USER`/`ADMIN_PASS` (un administrador) registra desde "Gestionar técnicos"
un técnico de prueba `e2e-tecnico-<marca>` con usuario `e2e-usuario-<marca>` y una contraseña sintética, lo desactiva y lo
activa con el candado, comprueba su `CREAR_USUARIO` en "Ver logs" (filtro de acción y fechas de hoy, buscador y detalle),
entra con él, cambia su contraseña desde el menú y la restaura, y lo borra como administrador desde la papelera. Solo deja
escribir sobre ese usuario; si el test cae antes del borrado, lo borra por la API buscándolo por su nombre exacto. Hace
cuatro inicios de sesión (el límite es de 5 por minuto): no lo lances pegado a otra ejecución.
```

y la frase final (`:53-54`) queda:

```
Sin credenciales (o sin `E2E_IMEI_PRUEBA`/`E2E_TEC_PRUEBA` en el caso
de `asignar.spec.ts`, sin `E2E_SKU_PRUEBA` en el de `stock.spec.ts` y `pedidos.spec.ts`, o sin `ADMIN_USER`/`ADMIN_PASS` en el de
`gestion.spec.ts`) en el entorno, los tests se saltan.
```

- [ ] **Step 3: Comprobaciones locales sin escribir**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
npm run lint
npx tsc -b
npx playwright test --list
```

Expected: lint limpio (`eslint .` cubre `tests/e2e`) y `tsc -b` limpio; `--list` compila el fichero y enumera **9 tests en 7 ficheros** (los 8 anteriores: `asignar` 1, `clientes` 1, `formulario` 2, `pedidos` 1, `stock` 1, `taller` 2; más el nuevo). **El smoke no se ejecuta aquí**: escribe en producción y necesita el servidor de la rama desplegado (el `limite` de `/api/logs` y los 404/409 nuevos no existen antes). Se lanza en la Task 15, paso del usuario U4, con su OK.

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/gestion.spec.ts .env.e2e.example README.md
git commit -m "test(e2e): smoke de gestion con alta, candado, log, cambio de contraseña y borrado de un tecnico de prueba"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

Recuento: **+0 tests unitarios** (+1 test e2e).

---

## Task 15: Fichas de paridad, versión 0.8.0, verificación final y cierre

**Files:**
- Create (web): `docs/paridad/tecnicos.md`, `docs/paridad/logs.md`, `docs/paridad/cuenta.md`
- Modify (web): `docs/paridad/shell.md:20` (bloque nuevo tras la última casilla, antes de "Diferencias aceptadas" en `:21`), `README.md:59-63` (sección "Documentación", línea nueva al final), `CHANGELOG.md:7` (entrada nueva antes de `## [0.7.0]`), `package.json:4` y `package-lock.json:3,9` (versión `0.8.0`; la versión visible sale solo de `package.json` vía `__APP_VERSION__`, `vite.config.ts:8,14`)
- Modify (raíz): el plan `docs/superpowers/plans/2026-09-26-web-gestion.md` (sección "Ejecución y cierre")
- Fuera de los repos (privados, sin commit): `C:\Users\dev\Documents\Apuntes\paridad-capturas\gestion\CAPTURAS-6.md`, `…\gestion\COMPARACION-6.md`, `C:\Users\dev\Documents\Apuntes\herramientas\paridad-capturas\capturas-gestion.mjs`, `Apuntes/plan-futuro.md`, memoria del proyecto

**Interfaces:** consume todo lo anterior (T0-T14); no produce código de la app.

**Supuestos:** las rutas, textos y nombres de test de T1-T12 son los de INTERFACES.md y la spec §9; la sección "Comprobado por tests" de cada ficha cita ficheros de test (no `it`), así que un nombre de `it` distinto no la invalida. Recuentos: servidor 421 + los de T1-T5 (INTERFACES: ~470); web 1333 + la suma de lo que anota cada tarea T6-T13 (T13: +9).

- [ ] **Step 1: Ficha de técnicos**

`docs/paridad/tecnicos.md` (web), completa:

```md
# Ficha de paridad — Gestionar técnicos (RegisterView.fxml + RegisterController)

Referencia: línea hotfix del cliente JavaFX (`hotfix/0.16.3`, la que usa la tienda), la ventana "Gestión de técnicos" que abre "Gestionar técnicos" del menú de usuario (`MainController`), con `RegisterView.fxml` y `RegisterController`. Spec de este sub-proyecto: raíz `docs/superpowers/specs/2026-09-26-web-gestion-design.md` (§6.1 y §10).

Capturas de referencia (documentación privada, fuera del repo; llevan datos reales del taller y se citan solo por nombre): `gestion/gestion-tecnicos-{vacio-formulario,tabla,fila-seleccionada,tooltip-desactivar,tooltip-activar,combo-rol,dup-tecnico,dup-usuario,error-vacios,error-no-coinciden,error-corta,error-409-admin,alta-ok,desactivado,no-se-puede-eliminar,confirmar-eliminar,error-eliminar,tabla-vacia,login-inactivo,tras-cerrar}.png` y `gestion/gestion-menu-usuario-admin.png`. Las de la web llevan el prefijo `web-`. Las situaciones que no se puedan reproducir en la toma se anotan como tales y se comprueban con la web o por test.

Los ejemplos de técnico ("tecnico-a") y de usuario ("usuario-a") son sintéticos.

## Diferencias deliberadas respecto al JavaFX

Las de la spec §10:

- **Página del shell** `/gestion/tecnicos` con la guarda `RequiereAdmin` (G2), en vez de la ventana modal "Gestión de técnicos"; sin título de ventana. El resto de roles recibe el aviso de permisos y vuelve a `/reparaciones`.
- **"Cerrar" vuelve a la vista desde la que el menú abrió la página** (`volverA`), o a `/reparaciones` si se entró por URL; equivale a cerrar la ventana.
- **Validaciones también en el servidor** (G1): el alta responde 422 "Todos los campos son obligatorios.", "La contraseña debe tener al menos 6 caracteres.", "El nombre de usuario no puede superar 50 caracteres.", "El nombre del técnico no puede superar 100 caracteres." y "Rol no permitido."; los duplicados siguen siendo 409; un `idTec` inexistente es 404 "Técnico no encontrado.". El JavaFX no los ve porque valida antes.
- **Borrar un técnico con cualquier referencia** (reparaciones propias, asignadas por él, entregas de glass, revisiones, envíos, devoluciones, solicitudes de stock o movimientos de teléfonos) da el aviso "No se puede eliminar", y si la comprobación y el borrado se cruzan, el `DELETE` responde 409 `"{nombre}" tiene reparaciones asociadas.` sin borrar nada (G8). En el JavaFX solo miraba `Reparacion.ID_TEC` y el borrado acababa en "Error al eliminar el técnico." (500 por integridad).
- **Diálogos nativos → propios** (G9): "No se puede eliminar" es un `mostrarAviso` y "Eliminar técnico" un `ConfirmDialog` con los botones "Eliminar" y "Cancelar".
- **Línea de error inline que se vacía al empezar cada acción** (registrar, candado, papelera) y que muestra el `message` del servidor en 404/409/422 (G9); el JavaFX deja el último error hasta un alta correcta y usa textos fijos.
- **Sin ordenación por cabecera** (G10).
- **Toda escritura con éxito invalida las listas de usuarios y de técnicos** de toda la web: los combos de técnicos del resto de vistas ven altas y bajas (equivale a la recarga de la vista de fondo al cerrar el modal del JavaFX).

Decididas durante la ejecución y la comparación de capturas: se añaden aquí, cada una con la decisión del usuario.

## Calcos

- Tres nombres para la misma pantalla: "Gestión de usuarios" en la cabecera y "Gestionar técnicos" en el menú (el título de ventana "Gestión de técnicos" no existe en la web).
- "Registrar técnico" aunque el rol elegido sea SUPERTECNICO; roles en mayúsculas; sin ADMIN en el combo.
- Papelera en todas las filas, sin tooltip.
- Sin mensaje de éxito al registrar.
- El error en vivo de duplicados se calcula contra la lista cargada, que no incluye ADMIN: "admin" pasa en vivo y lo frena el 409 del servidor.
- La confirmación vacía cae en "Las contraseñas no coinciden."; las contraseñas no se recortan.
- Enter no registra; "Cerrar" cierra sin preguntar aunque haya texto.
- El log de actividad del usuario eliminado se borra con él (G8).
- Sin CSV: "Descargar CSV" queda deshabilitado en esta ruta (`RegisterController` no implementa `Exportable`).
- Sin filtros, sin sondeo y sin "Actualizado".

## Pendiente de decidir

- Placeholder de la tabla vacía: previsto "No hay contenido en la tabla" (texto por defecto del `TableView`); se fija con `gestion-tecnicos-tabla-vacia` o, si no es reproducible, con la JVM del JavaFX.
- Columna de acciones: la web la deja en 80 px y el sobrante va a la columna de relleno de `DataTable`; en el JavaFX la última columna absorbe el resto (`FLEX_LAST_COLUMN`, spec §6.1). Se acepta como diferencia o se le da el sobrante, según `gestion-tecnicos-tabla`.

## Ruta y acceso

- [ ] "Gestionar técnicos" del menú (solo ADMIN) abre `/gestion/tecnicos` (`gestion-menu-usuario-admin`).
- [ ] TECNICO y SUPERTECNICO por URL: aviso de permisos y vuelta a `/reparaciones`.

## Cabecera

- [ ] Logo `logo_inicio_sesion.png` de 46 px, "Gestión de usuarios" (18 px negrita azul medio) y "Registra o elimina accesos al sistema" (12 px azul gris); fondo de página `#EFEFEF`; padding lateral 48 px (`gestion-tecnicos-vacio-formulario`).

## Formulario de alta

- [ ] Fila de cuatro campos a partes iguales, etiqueta 11 px negrita azul gris encima, campo blanco con borde `#D4D8DE`, radio 8, padding 10/12: "Nombre del técnico" ("Nombre visible en reparaciones"), "Nombre de usuario" ("Credencial de login"), "Contraseña" ("Contraseña", sin ojo), "Confirmar" ("Repite la contraseña") (`gestion-tecnicos-vacio-formulario`).
- [ ] Fila de acción: línea de error (11 px rojo, ocupa su hueco vacía) · combo de 130 px "TECNICO" / "SUPERTECNICO" con TECNICO por defecto · botón navy "Registrar técnico" en píldora de radio 24 (`gestion-tecnicos-combo-rol`).
- [ ] Separador `#D4D8DE` bajo el formulario.

## Duplicados en vivo

- [ ] "Ya existe un técnico con ese nombre." bajo el primer campo (10 px rojo) al escribir un nombre existente con otras mayúsculas o espacios; "Registrar técnico" deshabilitado (`gestion-tecnicos-dup-tecnico`).
- [ ] "Ese nombre de usuario ya existe." bajo el segundo campo, igual (`gestion-tecnicos-dup-usuario`).

## Validación y guardado

- [ ] Nombres o contraseña vacíos (nombres con trim): "Todos los campos son obligatorios." (`gestion-tecnicos-error-vacios`).
- [ ] Contraseña distinta de la confirmación: "Las contraseñas no coinciden." (`gestion-tecnicos-error-no-coinciden`).
- [ ] Menos de 6 caracteres: "La contraseña debe tener al menos 6 caracteres." (`gestion-tecnicos-error-corta`).
- [ ] Un 409 o 422 del servidor pinta su mensaje en la línea; p. ej. usuario "admin": "Ese nombre de usuario ya existe." (`gestion-tecnicos-error-409-admin`).
- [ ] Otro error: "Error al registrar. Inténtalo de nuevo.".
- [ ] Éxito: los cuatro campos vacíos, combo en TECNICO, línea vacía y la fila nueva en su sitio alfabético, sin mensaje (`gestion-tecnicos-alta-ok`).

## Tabla "Técnicos registrados"

- [ ] Título "Técnicos registrados" (13 px negrita); columnas Técnico (160), Usuario (130), Rol (110, tal cual), Estado (90) y acciones sin cabecera que absorbe el resto; orden del servidor (nombre de técnico); filas de 35 px (`gestion-tecnicos-tabla`).
- [ ] Badge "Activo" `#2E7D32` sobre `#D4EDDA` e "Inactivo" `#B03040` sobre `#F5E6E6`, radio 10, padding 3/10, 11 px negrita (`gestion-tecnicos-tabla`).
- [ ] Candado `Unlock.png` 18 px en activos con tooltip "Desactivar acceso" y `Lock.png` en inactivos con "Activar acceso" (`gestion-tecnicos-tooltip-desactivar`, `gestion-tecnicos-tooltip-activar`).
- [ ] Papelera `borrar.png` 22 px en todas las filas.
- [ ] Fila seleccionada navy con texto claro; badge e iconos sin cambiar (`gestion-tecnicos-fila-seleccionada`).
- [ ] Tabla vacía: el placeholder que se decida arriba (`gestion-tecnicos-tabla-vacia`).
- [ ] Error de carga: "Error al cargar los usuarios." en la línea inline y la tabla vacía.

## Activar y desactivar

- [ ] Clic en el candado, sin confirmación: cambia el badge y el candado y recarga (`gestion-tecnicos-desactivado`).
- [ ] Error: "Error al cambiar el estado del técnico." en la línea, o el mensaje del servidor si es 404.
- [ ] Un técnico desactivado no puede iniciar sesión (`gestion-tecnicos-login-inactivo`; mensaje del login, sin cambios en este sub-proyecto).

## Eliminar

- [ ] Con referencias: aviso "No se puede eliminar" con `"{nombre}" tiene reparaciones asociadas.`, "No es posible eliminarlo para conservar el historial." y "Puedes desactivarlo para bloquear su acceso." en tres líneas (`gestion-tecnicos-no-se-puede-eliminar`).
- [ ] Sin referencias: confirmación "Eliminar técnico" con `¿Eliminar a "{nombre}" definitivamente?` y "Se borrarán sus credenciales de acceso y su registro de técnico.", botones "Eliminar" y "Cancelar" (`gestion-tecnicos-confirmar-eliminar`).
- [ ] Error al comprobar: "Error al comprobar las reparaciones del técnico.".
- [ ] Error al borrar: el mensaje del servidor si es 404 o 409, si no "Error al eliminar el técnico." (`gestion-tecnicos-error-eliminar`: en la web el caso de la captura sale como aviso "No se puede eliminar", ver diferencias).

## Pie

- [ ] "Cerrar" (estilo enlace, 12 px azul gris) vuelve a la vista de origen, que muestra ya el técnico nuevo en sus combos (`gestion-tecnicos-tras-cerrar`).

## Comprobado por tests

Lo que no se ve en una captura o no se puede provocar en la toma:

- [ ] Los cinco 422 del alta en su orden, los nombres guardados recortados, los dos 409 y el 201 con log `CREAR_USUARIO` (`UsuarioControllerTest`).
- [ ] 404 de activar, desactivar, comprobar y eliminar; 409 del borrado con referencias sin borrar; `idUsu` resuelto desde `idTec` e ignorado (`UsuarioControllerTest`).
- [ ] Cada una de las nueve referencias hace `true` y ninguna `false` (`UsuarioDAOReferenciasTest`).
- [ ] `POST`/`DELETE /api/tecnicos` responden 403 a TECNICO y SUPERTECNICO (`RolesUsuarioTecnicoTest`).
- [ ] Orden y textos de la validación de la web y duplicados en vivo (`modules/gestion/tecnicos/validacion.test.ts`).
- [ ] Solo ADMIN (`modules/gestion/rutas.test.tsx`); formulario, alta con limpieza y recarga, 409/422 inline, candado, aviso, confirmación, errores del borrado, textos fijos, invalidaciones y "Cerrar" con `volverA` (`modules/gestion/tecnicos/TecnicosPage.test.tsx`, `columnas.test.tsx`, `api.test.ts`).
- [ ] Recorrido completo contra producción: alta, candado, log, contraseña y borrado (`tests/e2e/gestion.spec.ts`).
```

- [ ] **Step 2: Ficha de logs**

`docs/paridad/logs.md` (web), completa:

```md
# Ficha de paridad — Ver logs (LogView.fxml + LogController)

Referencia: línea hotfix del cliente JavaFX (`hotfix/0.16.3`, la que usa la tienda), la ventana "Log de actividad" que abre "Ver logs" del menú de usuario (`MainController`), con `LogView.fxml` y `LogController`. Spec de este sub-proyecto: raíz `docs/superpowers/specs/2026-09-26-web-gestion-design.md` (§6.2 y §10).

Capturas de referencia (documentación privada, fuera del repo; llevan datos reales del taller y se citan solo por nombre): `gestion/gestion-logs-{inicial,maximizada,fila-seleccionada,popup-accion,popup-accion-filtrado,filtro-accion,popup-tecnico,fechas,fechas-madrugada,buscador,vacio,detalle,detalle-motivo,detalle-largo,orden-cabecera,error}.png` y `gestion/gestion-menu-usuario-{admin,supertecnico}.png`. Las de la web llevan el prefijo `web-`. Las situaciones que no se puedan reproducir en la toma se anotan como tales y se comprueban con la web o por test.

Los ejemplos de usuario ("usuario-a") y de IMEI (`000000000000000`) son sintéticos.

## Diferencias deliberadas respecto al JavaFX

Las de la spec §10:

- **Página del shell** `/gestion/logs` con la guarda `RequiereAdmin` (G2), en vez de una ventana independiente no modal; para tenerla al lado de otra vista se abre en otra pestaña del navegador.
- **"Cerrar" vuelve a la vista desde la que el menú abrió la página** (`volverA`), o a `/reparaciones` si se entró por URL.
- **Las 1.000 entradas más recientes** (G3): la web pide `limite=1000` y, si llegan exactamente 1.000, avisa "Mostrando los 1.000 registros más recientes; acota con los filtros."; el JavaFX descarga el log entero en cada cambio de filtro.
- **Lista de acciones real y alfabética** (G4), de `GET /api/logs/acciones`; el JavaFX usa 56 códigos agrupados a mano (le faltan unos 40 de los que escribe el servidor y sobran 3 que ya no se generan).
- **Filtro de fechas en hora de Madrid** (G5): lo ocurrido entre las 00:00 y las 01:59 ya no cae en el día anterior; corrige también al JavaFX.
- **Barra de filtros con salto de línea y el estilo estándar de la web** (el JavaFX no carga `app.css` en esa ventana).
- **"Acción..." y "Técnico..." como `CampoAutocompletar`** (teclado y Enter), en vez del `Popup` con `ListView` (G10).
- **Sin ordenación por cabecera** (G10); el JavaFX ordenaba la fecha como texto `dd/MM/yyyy`.
- **Fallo de carga** con "Error al cargar los logs: …" en el diálogo de error y la tabla conservando lo que tenía, salvo sesión caducada y sin conexión, que siguen la política general (banner y login).

Decididas durante la ejecución y la comparación de capturas: se añaden aquí, cada una con la decisión del usuario.

## Calcos

- El buscador no mira el motivo ni la fecha; el motivo solo se ve con el doble clic.
- "Técnico..." lista los nombres de usuario de TECNICO y SUPERTECNICO, activos e inactivos, sin ADMIN.
- Los filtros no sobreviven a salir de la página: al volver, vacíos.
- Con "Desde" posterior a "Hasta" la tabla queda vacía sin aviso.
- Sin colores por acción, sin menú contextual, sin sondeo, sin "Actualizado" y sin CSV ("Descargar CSV" deshabilitado en esta ruta).
- Un fallo al cargar la lista de acciones o de usuarios deja el autocompletar vacío, sin aviso.

## Pendiente de decidir

- Placeholder de la tabla vacía: previsto "No hay contenido en la tabla", el mismo que en técnicos; se fija con `gestion-logs-vacio`.

## Ruta y acceso

- [ ] "Ver logs" del menú (solo ADMIN) abre `/gestion/logs`; SUPERTECNICO y TECNICO no tienen el ítem (`gestion-menu-usuario-admin`, `gestion-menu-usuario-supertecnico`) y por URL reciben el aviso de permisos.

## Cabecera

- [ ] Logo de 46 px, "Log de actividad" y "Registro de acciones realizadas en el sistema"; separador (`gestion-logs-inicial`).

## Barra de filtros

- [ ] En este orden: "Buscar..." (220 px), "Acción..." (150 px), "Técnico..." (150 px), "Desde:" / "Hasta:" y "Limpiar filtros" (`gestion-logs-inicial`).
- [ ] "Buscar...": contiene, sin mayúsculas y con trim, sobre usuario, acción y detalle; se conserva al recargar (`gestion-logs-buscador`).
- [ ] "Acción...": lista completa y filtrada al teclear; elegir filtra en el servidor por igualdad; borrar el texto quita el filtro (`gestion-logs-popup-accion`, `gestion-logs-popup-accion-filtrado`, `gestion-logs-filtro-accion`).
- [ ] "Técnico...": nombres de usuario en orden natural, sin ADMIN (`gestion-logs-popup-tecnico`).
- [ ] "Desde:" / "Hasta:" inclusivos en hora de Madrid (`gestion-logs-fechas`, `gestion-logs-fechas-madrugada`: diferencia, ver arriba).
- [ ] "Limpiar filtros" vacía los cinco con una sola recarga.

## Aviso de tope

- [ ] Con 1.000 filas exactas, bajo la barra: "Mostrando los 1.000 registros más recientes; acota con los filtros." (sin captura del JavaFX: diferencia).

## Tabla

- [ ] Fecha (150; `dd/MM/yyyy HH:mm:ss` en hora de Madrid), Usuario (80; nombre de login), Acción (180; el código tal cual), Detalle (el resto, una línea con elipsis); orden del servidor, fecha y desempate por id descendentes (`gestion-logs-inicial`, `gestion-logs-maximizada`).
- [ ] Fila seleccionada navy con texto claro (`gestion-logs-fila-seleccionada`).
- [ ] Sin ordenación por cabecera (`gestion-logs-orden-cabecera`: diferencia, ver arriba).
- [ ] Tabla vacía: el placeholder que se decida arriba (`gestion-logs-vacio`).

## Detalle

- [ ] Doble clic en una fila: "Detalle del log" con el detalle de solo lectura y "Copiar" (`gestion-logs-detalle`).
- [ ] Con motivo: línea en blanco y "MOTIVO: …" debajo del detalle (`gestion-logs-detalle-motivo`).
- [ ] Detalle largo con ajuste de línea y scroll (`gestion-logs-detalle-largo`).

## Pie y errores

- [ ] "Actualizar" (navy) recarga con los filtros actuales; "Cerrar" vuelve a la vista de origen.
- [ ] Fallo de carga: "Error al cargar los logs: …" conservando la tabla (`gestion-logs-error`: diferencia de presentación, ver arriba).

## Comprobado por tests

Lo que no se ve en una captura o no se puede provocar en la toma:

- [ ] `limite` opcional, 422 "Límite no válido (debe estar entre 1 y 5000)." fuera de rango, `GET /api/logs/acciones` solo ADMIN (`LogControllerTest`).
- [ ] Límites de día en Madrid convertidos a UTC en verano e invierno y orden con desempate (`LogDAOFiltroTest`).
- [ ] Ruta nueva, `limite` y los nulos de `LogActividad` en el contrato (`OpenApiContractTest`).
- [ ] Los nueve casos de `coincideTexto` portados del cliente y `queryLogs` sin vacíos (`modules/gestion/logs/filtros.test.ts`).
- [ ] Filtros de servidor y de memoria, aviso de tope, doble clic con y sin motivo, "Actualizar", "Limpiar filtros" con una sola carga y error conservando los datos (`modules/gestion/logs/LogsPage.test.tsx`, `columnas.test.tsx`, `api.test.ts`).
- [ ] `CREAR_USUARIO` del día filtrando por acción y fechas contra producción, buscador y detalle (`tests/e2e/gestion.spec.ts`).
```

- [ ] **Step 3: Ficha de cuenta**

`docs/paridad/cuenta.md` (web), completa:

```md
# Ficha de paridad — Cambiar contraseña (CambiarPasswordView.fxml + CambiarPasswordController)

Referencia: línea hotfix del cliente JavaFX (`hotfix/0.16.3`, la que usa la tienda), la ventana modal "Cambiar contraseña" que abre el ítem del mismo nombre del menú de usuario (`MainController`), con `CambiarPasswordView.fxml` y `CambiarPasswordController`. El menú de usuario y el login se siguen en `shell.md`. Spec de este sub-proyecto: raíz `docs/superpowers/specs/2026-09-26-web-gestion-design.md` (§6.3, §6.4 y §10).

Capturas de referencia (documentación privada, fuera del repo; llevan datos reales del taller y se citan solo por nombre): `gestion/gestion-password-{vacia,ojo,error-vacios,error-corta,error-no-coinciden,error-actual,exito,sin-conexion,enter-esc,sesion-caducada}.png` y `gestion/gestion-menu-usuario-{admin,tecnico,supertecnico}.png`. Se toman con un usuario de prueba creado para ello, nunca uno real. Las de la web llevan el prefijo `web-`.

Las contraseñas de los ejemplos ("secreta1", "nueva123") son sintéticas.

## Diferencias deliberadas respecto al JavaFX

Las de la spec §10:

- **Enter guarda y Esc cierra** (inocua); en el JavaFX no hacen nada (`gestion-password-enter-esc`).
- **Validación también en el servidor** (G1): `PATCH /api/auth/cambiar-password` responde 422 "Rellena todos los campos." y "La contraseña debe tener al menos 6 caracteres." (antes, 400 sin cuerpo); el JavaFX no los ve porque valida antes.
- **Diálogo propio de la web** (sin el marco de ventana del sistema) y aviso de éxito con `mostrarAviso`.

Decididas durante la ejecución y la comparación de capturas: se añaden aquí, cada una con la decisión del usuario.

## Calcos

- Diálogo encima de la vista actual, sin navegar (G6): la vista de fondo se conserva.
- Placeholder "Confirmar contraseña" distinto de su etiqueta "Confirmar nueva contraseña".
- Sin trim en las contraseñas.
- "Cancelar" cierra sin preguntar.
- Tras el cambio, la sesión sigue igual (no se cierra ni se renueva).
- Sin CSV.

## Pendiente de decidir

- Título del aviso de éxito: previsto "Información"; se fija con `gestion-password-exito`.

## Menú

- [ ] "Cambiar contraseña" en el menú de los tres roles, entre "Descargar CSV" y el separador de "Cerrar Sesión" (`gestion-menu-usuario-admin`, `gestion-menu-usuario-tecnico`, `gestion-menu-usuario-supertecnico`).
- [ ] Abre el diálogo sin cambiar de ruta.

## Diálogo

- [ ] 380 px; barra superior navy `#001232` con "Cambiar contraseña" (15 px negrita blanco, padding 16/20); cuerpo blanco con padding 24 y separación 16 (`gestion-password-vacia`).
- [ ] Tres campos con etiqueta 11 px negrita `#555`: "Contraseña actual" (placeholder "Contraseña actual"), "Nueva contraseña" ("Nueva contraseña") y "Confirmar nueva contraseña" ("Confirmar contraseña"); foco inicial en la actual (`gestion-password-vacia`).
- [ ] Ojo independiente por campo (`ojo_activar` / `ojo_desactivar`, 18 px) que muestra y oculta el texto (`gestion-password-ojo`).
- [ ] Botones a la derecha: "Cancelar" (blanco, borde `#C2C8D0`, radio 6) y "Guardar" (navy, negrita, radio 6).

## Validación

- [ ] Línea de error `#CC0000` 12 px, oculta hasta que hay error.
- [ ] Alguno vacío: "Rellena todos los campos." (`gestion-password-error-vacios`).
- [ ] Nueva de menos de 6: "La contraseña debe tener al menos 6 caracteres." (`gestion-password-error-corta`).
- [ ] Nueva distinta de la confirmación: "Las contraseñas nuevas no coinciden." (`gestion-password-error-no-coinciden`).

## Guardado

- [ ] "Guardar" deshabilitado mientras responde el servidor.
- [ ] Actual incorrecta: "Contraseña actual incorrecta." (422 del servidor) en la línea, sin vaciar los campos (`gestion-password-error-actual`).
- [ ] Éxito: se cierra el diálogo y sale el aviso "Contraseña cambiada correctamente." (`gestion-password-exito`).
- [ ] Sin conexión: banner y el mensaje de la web para ese error (`gestion-password-sin-conexion`).
- [ ] Sesión caducada: flujo global de sesión caducada, vuelta al login (`gestion-password-sesion-caducada`).

## Comprobado por tests

- [ ] 204 con log `CAMBIAR_PASSWORD`, 422 con campos vacíos, 422 con la nueva corta y 422 con la actual incorrecta (`AuthControllerCambiarPasswordTest`).
- [ ] Orden y textos de la validación sin trim (`modules/gestion/cuenta/validacion.test.ts`).
- [ ] Validación, ojo por campo, envío, éxito con aviso y cierre, 422 inline y botón deshabilitado (`modules/gestion/cuenta/CambiarPasswordDialog.test.tsx`, `api.test.ts`).
- [ ] El diálogo se abre desde el menú sin navegar (`app/shell/TopBar.test.tsx`).
- [ ] Cambio real y vuelta a la contraseña original de un usuario de prueba contra producción (`tests/e2e/gestion.spec.ts`).
```

- [ ] **Step 4: Líneas del shell y README**

En `docs/paridad/shell.md`, tras la última casilla (`:20`) y antes de "- Diferencias aceptadas" (`:21`):

```md
- [ ] (Sub-proyecto 6) "Cambiar contraseña" abre el diálogo encima de la vista actual, sin navegar; la ruta `/cuenta/cambiar-password` ya no existe (calco del `Stage` modal; ficha `cuenta.md`) (`gestion-password-vacia`).
- [ ] (Sub-proyecto 6) "Gestionar técnicos" y "Ver logs" (solo ADMIN) abren `/gestion/tecnicos` y `/gestion/logs` recordando la vista de origen (`volverA`); su "Cerrar" vuelve a ella, o a `/reparaciones` si se entró por URL (`gestion-tecnicos-tras-cerrar`).
- [ ] (Sub-proyecto 6) Las tres pantallas de Gestión no registran exportable: "Descargar CSV" queda deshabilitado en `/gestion/tecnicos` y `/gestion/logs`, y el diálogo de contraseña tampoco exporta (calco: ni `RegisterController`, ni `LogController`, ni `CambiarPasswordController` implementan `Exportable`) (`gestion-csv-sin-exportable`).
- [ ] (Sub-proyecto 6) Login: con usuario o contraseña vacíos dice "Rellena usuario y contraseña." (texto del JavaFX; corrige al sub-proyecto 0) y la contraseña usa `CampoPassword` con el ojo (`gestion-cerrar-sesion-login`).
- [ ] (Sub-proyecto 6) Menú de usuario completo para los tres roles, sin ningún "Pendiente de migrar" (`gestion-menu-usuario-admin`, `gestion-menu-usuario-tecnico`, `gestion-menu-usuario-supertecnico`, `gestion-menu-usuario-posicion`, `gestion-barra-version`).
```

En `README.md`, sección "Documentación", al final (tras `:63`):

```md
- Spec de Gestión (técnicos, logs y contraseña): repo raíz, `docs/superpowers/specs/2026-09-26-web-gestion-design.md`; fichas `docs/paridad/{tecnicos,logs,cuenta}.md`.
```

- [ ] **Step 5: Versión y CHANGELOG**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
npm version 0.8.0 --no-git-tag-version
node -p "require('./package.json').version"
git diff --stat
```

Expected: `v0.8.0` y después `0.8.0`; `git diff --stat` muestra `package.json` y `package-lock.json` (las dos líneas `"version"` de `package-lock.json:3,9`; la de `:591` es de una dependencia y no cambia).

`CHANGELOG.md`, entrada nueva encima de `## [0.7.0]` (`:7`):

```md
## [0.8.0] - 2026-09-XX — Gestión

- "Gestionar técnicos" en `/gestion/tecnicos` (solo administrador): alta de usuario y técnico con rol TECNICO o SUPERTECNICO, aviso mientras se escribe si el nombre ya existe, tabla de técnicos registrados con su estado, candado para activar o desactivar el acceso y papelera con confirmación, o con aviso si el técnico tiene historial.
- "Ver logs" en `/gestion/logs` (solo administrador): registro de actividad con buscador, filtros de acción, técnico y fechas, las 1.000 entradas más recientes con aviso al llegar al tope y el detalle completo con doble clic.
- "Cambiar contraseña" se abre encima de la vista en la que estás, con un ojo en cada campo para ver lo escrito.
- El inicio de sesión dice "Rellena usuario y contraseña." con los campos vacíos, como el programa de escritorio.
- Asignaciones: "Descargar CSV" exporta las asignaciones visibles con 14 columnas, incluida "Entregado".
- Servidor: el alta de usuarios y el cambio de contraseña se validan también en el servidor; un técnico con historial (reparaciones, asignaciones, entregas, solicitudes o movimientos) no se puede borrar y se explica por qué; un técnico inexistente responde "no encontrado"; el filtro de fechas del registro de actividad usa la hora de Madrid, también para el programa de escritorio; solo el administrador puede crear o borrar técnicos.
- Diferencias aceptadas respecto al programa de escritorio: `docs/paridad/tecnicos.md`, `docs/paridad/logs.md` y `docs/paridad/cuenta.md`.
```

La fecha `XX` se fija el día del tag (U10).

- [ ] **Step 6: Verificación completa de los tres repos**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git branch --show-current && mvn -q test
grep -h "Tests run" target/surefire-reports/*.txt | awk -F'[:,]' '{s+=$2} END {print s}'
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web && git branch --show-current && npm run check && npm run build && npx playwright test --list
node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/comparar-openapi.mjs \
  /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor/target/openapi.json \
  /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/api/openapi.json
grep -l '0\.8\.0' /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/dist/assets/*.js
git -C /c/Users/dev/Documents/ProgramaReparaciones status --short gestion-reparaciones-cliente
```

Expected: las dos ramas `feature/web-gestion`; servidor en verde y el recuento de surefire igual a 421 + los tests de T1-T5 (~470); web: `npm run check` (lint, typecheck y Vitest) en verde con 1333 + la suma de T6-T13, build correcto y `--list` con **9 tests en 7 ficheros**; `comparar-openapi.mjs` imprime `rutas A/B: 137 / 137` (136 del 4b + `/api/logs/acciones`) y ninguna diferencia salvo `servers`; el bundle contiene `0.8.0`; `git status` del cliente JavaFX **vacío** (no se ha tocado). `comparar-openapi.mjs` existe en `Apuntes/herramientas/paridad-capturas/` (ordena las claves e ignora `servers`); un `diff` directo de los dos JSON no sirve porque el orden de claves puede diferir.

- [ ] **Step 7: Commit de cierre en la web**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git add docs/paridad/tecnicos.md docs/paridad/logs.md docs/paridad/cuenta.md docs/paridad/shell.md README.md CHANGELOG.md package.json package-lock.json
git commit -m "docs(web): fichas de tecnicos, logs y cuenta sin marcar, lineas del shell, CHANGELOG y version 0.8.0"
git cat-file -p HEAD | tail -1
```

Expected: la última línea del commit no contiene `Co-Authored-By`.

- [ ] **Step 8: "Ejecución y cierre" en el raíz**

Añadir al final del plan (`docs/superpowers/plans/2026-09-26-web-gestion.md`) la sección **"## Ejecución y cierre (2026-09-XX)"** con el esquema de la del 4b (`docs/superpowers/plans/2026-09-25-web-almacen-pedidos.md:11563-11624`): estado ("código terminado; pendiente de smoke, capturas y OK del usuario"), resultado de la Task 0 (sin ids: "N pedidos, N proveedores y el stock del SKU de prueba resueltos; pendiente: …"), rama y head de cada repo con la lista de commits, suites (Step 6), desviaciones respecto al plan, decisiones del usuario, backlog menor de las revisiones y, debajo, la subsección **"### Pendiente, del usuario y uno a uno"** con los pasos U1-U13 del Step 10, copiados tal cual. Commit en `main` del raíz:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add docs/superpowers/plans/2026-09-26-web-gestion.md
git commit -m "docs(plan): ejecucion del sub-proyecto 6 (pendiente de smoke, capturas y ok del usuario)"
git cat-file -p HEAD | tail -1
```

- [ ] **Step 9: Lista privada de capturas del JavaFX**

Crear `C:\Users\dev\Documents\Apuntes\paridad-capturas\gestion\CAPTURAS-6.md` (fuera del repo; formato de `almacen/CAPTURAS-4b.md`), con las tres tablas §18 de los inventarios. Las tres del menú de ADMIN de los inventarios (`gestion-tecnicos-menu`, `gestion-logs-menu-admin`, `gestion-menu-usuario-admin`) son la misma situación: se toma una vez con el nombre canónico `gestion-menu-usuario-admin.png`, y lo mismo con el menú de TECNICO (`gestion-tecnicos-menu-tecnico` = `gestion-menu-usuario-tecnico`) y el de SUPERTECNICO (`gestion-logs-menu-supertecnico` = `gestion-menu-usuario-supertecnico`).

```md
# Capturas del JavaFX para el sub-proyecto 6 — Gestión (técnicos, logs, contraseña y menú de usuario)

Tomar en el JavaFX **`hotfix/0.16.3`** lanzado desde el worktree `Documents/_ref-hotfix-0163`, con `config.properties`
apuntando a **producción** (BD de pruebas; con el servidor del SP6 ya desplegado), sesión ADMIN salvo que se indique.
El login lo teclea siempre el usuario. Guardar en esta carpeta con el nombre exacto. Las que no sean reproducibles se
anotan como "no reproducible", no se inventan.

**Usuario de prueba** (nunca uno real): lo registra el usuario desde el propio formulario del JavaFX con técnico
`captura-tecnico-<fecha>`, usuario `captura-usuario-<fecha>`, rol TECNICO y una contraseña sintética que solo se apunta
aquí; sirve para `gestion-tecnicos-alta-ok`, el candado, `gestion-tecnicos-confirmar-eliminar` (Cancelar) y todas las de
contraseña (sesión con ese usuario; al terminar se restaura la contraseña). Al final se borra desde la papelera.

Marcar `[x]` al tomar cada una.

## Datos creados para las capturas

- Usuario de prueba (técnico / usuario / idTec, borrado sí o no): …
- Contraseñas cambiadas y restauradas: …
- Logs que quedan (`CREAR_USUARIO`, `ACTIVAR_USUARIO`, `DESACTIVAR_USUARIO`, `ELIMINAR_USUARIO`, `LOGIN`; los `CAMBIAR_PASSWORD` y `LOGIN` del usuario de prueba se borran con él): van a la limpieza del 6.

## Menú de usuario y barra

- [ ] `gestion-menu-usuario-admin.png` — Menú desplegado: "Gestionar técnicos", "Ver logs", separador, "Descargar CSV", "Cambiar contraseña", separador, "Cerrar Sesión", con un ítem en hover navy. (ADMIN; sirve también como `gestion-tecnicos-menu` y `gestion-logs-menu-admin`.)
- [ ] `gestion-menu-usuario-tecnico.png` — Menú sin las entradas de ADMIN. (TECNICO: el usuario de prueba; sirve como `gestion-tecnicos-menu-tecnico`.)
- [ ] `gestion-menu-usuario-supertecnico.png` — Menú con la campana visible a la izquierda del botón. (SUPERTECNICO; sirve como `gestion-logs-menu-supertecnico`.)
- [ ] `gestion-menu-usuario-posicion.png` — Plano amplio: alineación del menú respecto al botón (borde izquierdo, 4 px debajo).
- [ ] `gestion-barra-version.png` — Barra superior completa con "V.0.16.2" en el hotfix 0.16.3.
- [ ] `gestion-cerrar-sesion-login.png` — Login tras "Cerrar Sesión" (título "Gestión de Reparaciones — Login").
- [ ] `gestion-logs-tras-logout.png` — Abrir "Ver logs" y cerrar sesión: ¿la ventana de logs sigue abierta?
- [ ] `gestion-csv-sin-exportable.png` — En Estadísticas o Clientes, "Descargar CSV" activo y sin efecto.
- [ ] `asig-csv.png` — Asignaciones del supertécnico, "Descargar CSV": nombre `reparaciones_pendientes_…csv` y el CSV abierto (14 columnas). (SUPERTECNICO; referencia de la Task 13.)

## Gestionar técnicos (ADMIN)

- [ ] `gestion-tecnicos-vacio-formulario.png` — Ventana recién abierta: título "Gestión de técnicos", cabecera con logo, 4 campos con prompts, combo "TECNICO", botón, tabla con datos.
- [ ] `gestion-tecnicos-tabla.png` — Tabla con técnicos activos e inactivos, TECNICO y SUPERTECNICO: badges, candado abierto/cerrado, papelera.
- [ ] `gestion-tecnicos-fila-seleccionada.png` — Una fila seleccionada (fondo navy): badge e iconos.
- [ ] `gestion-tecnicos-tooltip-desactivar.png` / `gestion-tecnicos-tooltip-activar.png` — Tooltip del candado sobre un activo ("Desactivar acceso") y sobre un inactivo ("Activar acceso").
- [ ] `gestion-tecnicos-combo-rol.png` — Combo de rol desplegado: "TECNICO", "SUPERTECNICO".
- [ ] `gestion-tecnicos-dup-tecnico.png` — Nombre de técnico existente con otra capitalización o espacios: aviso rojo bajo el campo y botón deshabilitado.
- [ ] `gestion-tecnicos-dup-usuario.png` — Ídem con el nombre de usuario.
- [ ] `gestion-tecnicos-error-vacios.png` — "Todos los campos son obligatorios."
- [ ] `gestion-tecnicos-error-no-coinciden.png` — "Las contraseñas no coinciden."
- [ ] `gestion-tecnicos-error-corta.png` — "La contraseña debe tener al menos 6 caracteres."
- [ ] `gestion-tecnicos-error-409-admin.png` — Usuario igual al del ADMIN (no está en la lista, pasa en vivo) → "Ese nombre de usuario ya existe." del servidor.
- [ ] `gestion-tecnicos-alta-ok.png` — Tras registrar el usuario de prueba: formulario vacío, combo en "TECNICO", fila nueva en su sitio alfabético.
- [ ] `gestion-tecnicos-desactivado.png` — Tras el candado del usuario de prueba: badge "Inactivo" y candado cerrado.
- [ ] `gestion-tecnicos-login-inactivo.png` — Login con el usuario de prueba desactivado: el error que ve. (Después, reactivarlo.)
- [ ] `gestion-tecnicos-no-se-puede-eliminar.png` — Papelera sobre un técnico con reparaciones: "No se puede eliminar" (cabecera y dos líneas). Solo lee; no se confirma nada.
- [ ] `gestion-tecnicos-confirmar-eliminar.png` — Papelera sobre el usuario de prueba: "Eliminar técnico" con sus botones reales; **Cancelar** (se borra al final).
- [ ] `gestion-tecnicos-error-eliminar.png` — Supertécnico que asignó trabajos sin reparaciones propias: con el servidor del SP6 el JavaFX ve ahora "No se puede eliminar" (la comprobación mira todas las referencias); anotar lo que salga. Nunca confirmar el borrado de un técnico real.
- [ ] `gestion-tecnicos-tabla-vacia.png` — Solo si se puede en una BD de pruebas; si no, "no reproducible" (el placeholder se fija con la JVM).
- [ ] `gestion-tecnicos-tras-cerrar.png` — Vista de fondo recargada tras cerrar (p. ej. Asignaciones con el técnico nuevo en el combo).

## Ver logs (ADMIN)

- [ ] `gestion-logs-inicial.png` — Ventana recién abierta (860×520): cabecera, barra de filtros (¿cortada?), tabla, pie "Actualizar"/"Cerrar"; aspecto de "Buscar..." y "Limpiar filtros".
- [ ] `gestion-logs-maximizada.png` — Maximizada: reparto de anchos con Detalle flexible.
- [ ] `gestion-logs-fila-seleccionada.png` — Una fila seleccionada (fondo `#2C3B54`, texto claro).
- [ ] `gestion-logs-popup-accion.png` — Popup de "Acción..." con la lista completa (8 filas y scroll).
- [ ] `gestion-logs-popup-accion-filtrado.png` — Popup de "Acción..." con "pedido" tecleado.
- [ ] `gestion-logs-filtro-accion.png` — Tabla filtrada por `LOGIN`.
- [ ] `gestion-logs-popup-tecnico.png` — Popup de "Técnico..." (confirmar que no sale el ADMIN).
- [ ] `gestion-logs-fechas.png` — "Desde" con el calendario abierto y el editor de texto deshabilitado.
- [ ] `gestion-logs-fechas-madrugada.png` — "Desde" = un día con actividad entre 00:00 y 01:59 (Madrid). Con el servidor del SP6 el filtro ya va en hora de Madrid: anotar lo que salga.
- [ ] `gestion-logs-buscador.png` — "Buscar..." con un IMEI: solo filas cuyo detalle lo contiene.
- [ ] `gestion-logs-vacio.png` — Filtros sin resultados: placeholder por defecto del JavaFX.
- [ ] `gestion-logs-detalle.png` — Doble clic sobre un `CREAR_ASIGNACION`: "Detalle del log" con "Copiar".
- [ ] `gestion-logs-detalle-motivo.png` — Doble clic sobre un `ELIMINAR_ASIGNACION` con motivo: línea en blanco y "MOTIVO: …".
- [ ] `gestion-logs-detalle-largo.png` — Doble clic sobre un `COMPLETAR_PULIDO_LOTE` o `EDITAR_REPARACION` largo.
- [ ] `gestion-logs-orden-cabecera.png` — Tras pulsar la cabecera "Fecha" (orden alfabético de `dd/MM/yyyy`).
- [ ] `gestion-logs-error.png` — "Error al cargar los logs: …" con el servidor parado: "no reproducible" en producción (no se para el servidor); se comprueba por test.

## Cambiar contraseña (sesión del usuario de prueba, TECNICO)

- [ ] `gestion-password-vacia.png` — Recién abierta: barra navy, tres campos con placeholders, botones; título de la ventana.
- [ ] `gestion-password-ojo.png` — Texto en los tres campos y el ojo activado en uno (`ojo_desactivar`).
- [ ] `gestion-password-error-vacios.png` — "Rellena todos los campos." (¿crece la ventana?).
- [ ] `gestion-password-error-corta.png` — "La contraseña debe tener al menos 6 caracteres."
- [ ] `gestion-password-error-no-coinciden.png` — "Las contraseñas nuevas no coinciden."
- [ ] `gestion-password-error-actual.png` — Actual incorrecta: "Contraseña actual incorrecta." (422).
- [ ] `gestion-password-exito.png` — `Alert` "Contraseña cambiada correctamente." (título, cabecera, icono y botón exactos). Después, restaurar la contraseña.
- [ ] `gestion-password-sin-conexion.png` — Con el servidor parado: "no reproducible" en producción; se comprueba por test.
- [ ] `gestion-password-enter-esc.png` — Enter en "Confirmar" y Esc: confirmar que no hacen nada.
- [ ] `gestion-password-sesion-caducada.png` — Guardar con el token caducado: solo si se puede sin tocar el servidor; si no, "no reproducible".
```

- [ ] **Step 10: Parar y pedir OK al usuario**

**No hacer push, merge, tag ni despliegue.** Presentar qué se ha hecho, el estado de los tres repos y la lista de pasos que requieren su OK **uno a uno**. Claude no hace SSH a las VMs: los comandos de la VDC se le preparan y los ejecuta el usuario. Las capturas de la web se comparan **antes del merge de la web**. Esta lista se copia tal cual como "### Pendiente, del usuario y uno a uno" en la sección del Step 8:

### Pendiente, del usuario y uno a uno

No hacer push, merge, tag ni despliegue sin OK. Claude no hace SSH a las VMs: los comandos de la VDC se preparan y los ejecuta el usuario. Las capturas de la web se comparan **antes del merge de la web** (spec §9).

**U1. Push de las dos ramas** (copia de seguridad; con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git -C gestion-reparaciones-servidor push -u origin feature/web-gestion
git -C gestion-reparaciones-web push -u origin feature/web-gestion
```

**U2. Merge del servidor en `main` y push** (con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git merge --no-ff --no-edit feature/web-gestion
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q test
git push origin main
```

Expected: suite en verde en `main` antes del push.

**U3. Despliegue del servidor en la VDC y contrato** (lo ejecuta el usuario; guía privada `Apuntes/despliegue_vdc_produccion.md` §P8). Solo el backend: la web 0.7.0 desplegada sigue funcionando con el servidor nuevo porque todo es aditivo (su `/cuenta/cambiar-password` es un "Pendiente de migrar" y no llama a nada).

```bash
ssh prod
cd /opt/reparaciones && git -C gestion-reparaciones-servidor pull && git -C gestion-reparaciones-servidor log --oneline -1
docker compose up -d --build backend
docker compose logs --tail=80 backend | grep -E "Started|ERROR"
exit
```

Expected: el `log -1` muestra el merge de U2 y los logs, `Started App`. Después, en el PC (Git Bash, desde la web, credenciales de `~/.env.e2e` exportadas y sin escribirlas en la línea de comandos):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
set -a; . ~/.env.e2e; set +a
cp api/openapi.json "$TMPDIR/openapi-rama.json"
API_URL="$E2E_BASE_URL" API_USER="$E2E_USER" API_PASS="$E2E_PASS" node scripts/fetch-openapi.mjs
node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/comparar-openapi.mjs "$TMPDIR/openapi-rama.json" api/openapi.json
git checkout api/openapi.json
```

Expected: `rutas A/B: 137 / 137` y sin diferencias salvo `servers`; `git checkout` devuelve el snapshot determinista. `node scripts/fetch-openapi.mjs` (no `npm run api:types`) para no regenerar `schema.d.ts`.

**U4. Smoke contra producción con la web de la rama en local** (escribe en la BD de pruebas: crea y borra un usuario de prueba; con OK). `.env.local` con `VITE_API_PROXY_TARGET` apuntando a la API de producción (lección del 3b) y el puerto 5173 libre. **`E2E_BASE_URL=http://localhost:5173` explícito** (lección del 4b: con el valor de `~/.env.e2e` correría contra la web desplegada, que es la 0.7.0):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout feature/web-gestion
npm run dev   # en otra terminal
set -a; . ~/.env.e2e; set +a
E2E_BASE_URL=http://localhost:5173 npx playwright test tests/e2e/gestion.spec.ts
```

Expected: `gestion.spec.ts` en verde. **Esperar al menos un minuto** (límite de 5 inicios de sesión por minuto; el smoke hace cuatro) y después la suite completa, en serie (`workers: 1`):

```bash
E2E_BASE_URL=http://localhost:5173 npx playwright test
```

Expected: `stock`, `asignar`, `clientes`, `taller`, `pedidos` y `gestion` en verde (`formulario.spec.ts` depende de su precondición de datos, como en 4a y 4b). Si `gestion.spec.ts` falla, su anotación `e2e-creado` y los `expect.soft` de `afterAll` nombran el usuario y la ruta que quedaron: se anota en la limpieza del 6 (U12).

**U5. Capturas del JavaFX** (el usuario; Claude maneja los scripts de `Apuntes/herramientas/paridad-capturas/` para capturar y navegar, **sin pulsar nada que escriba**: el alta, el candado, el cambio de contraseña y el borrado del usuario de prueba los pulsa el usuario). Worktree recreado desde el raíz, que se queda en `main`:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git worktree add ../_ref-hotfix-0163 hotfix/0.16.3
cp gestion-reparaciones-cliente/src/main/resources/config.properties ../_ref-hotfix-0163/gestion-reparaciones-cliente/src/main/resources/config.properties
```

Editar esa copia para que apunte a producción (como en 4a y 4b; el fichero está ignorado por git), y lanzar:

```bash
cd /c/Users/dev/Documents/_ref-hotfix-0163/gestion-reparaciones-cliente
export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
mvn -q javafx:run
```

Comprobar con `Get-NetTCPConnection -OwningProcess <pid>` que solo conecta con producción. Recorrer `Apuntes/paridad-capturas/gestion/CAPTURAS-6.md` una a una con el usuario de prueba que se describe allí (nunca uno real) y apuntar en "Datos creados" lo que quede. Al terminar: `git worktree remove ../_ref-hotfix-0163` desde el raíz.

**U6. Capturas de la web y comparación lado a lado** (antes del merge de la web). Con la web de la rama en local contra producción (`npm run dev`, puerto 5173), crear `C:\Users\dev\Documents\Apuntes\herramientas\paridad-capturas\capturas-gestion.mjs` a partir de `capturas-pedidos.mjs` (misma cabecera, `paso`, `shot` con prefijo `web-`, `login`, contextos 1920×1080). Su guardia bloquea toda escritura **salvo las del flujo de prueba**: el alta de un técnico cuyo nombre empiece por `captura-` (incluido el 409 provocado con el usuario del ADMIN, que no escribe), el candado y el borrado del idTec del usuario de prueba que el propio script creó, y el cambio de contraseña **solo en el contexto de ese usuario**:

```js
// Capturas de paridad de la web, sub-proyecto 6 (Gestión), contra E2E_BASE_URL (web de la rama en local).
// Credenciales: ADMIN_USER/ADMIN_PASS y E2E_USER/E2E_PASS (SUPERTECNICO). No se imprimen.
// Escrituras: SOLO el flujo del usuario de prueba "captura-…" que crea el propio script (alta, candado, cambio de su
// contraseña y restauración, borrado final) y el POST del 409 con el usuario del ADMIN (el servidor no escribe).
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
const require = createRequire('C:/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/package.json')
const { chromium } = require('@playwright/test')

const OUT = process.env.OUT_DIR
if (!OUT) { console.error('Falta OUT_DIR'); process.exit(1) }
mkdirSync(OUT, { recursive: true })
const base = process.env.E2E_BASE_URL.replace(/\/$/, '')
const marca = Date.now()
const P = { tecnico: `captura-tecnico-${marca}`, usuario: `captura-usuario-${marca}`, clave: `captura-${marca}`, nueva: `captura-n-${marca}` }
let prueba = null // { idTec, idUsu } del usuario de prueba, cuando exista

const paso = async (nombre, fn) => {
  try { await fn(); console.log('ok  ', nombre) } catch (e) { console.log('FAIL', nombre, '->', String(e).split('\n')[0]) }
}
const shot = async (page, n) => { await page.waitForTimeout(350); await page.screenshot({ path: `${OUT}/web-${n}.png` }); console.log('     shot web-' + n) }
const espera = (ms) => new Promise((r) => setTimeout(r, ms))

async function guardia(ctx, { esUsuarioPrueba = false } = {}) {
  await ctx.route('**/api/**', (route) => {
    const req = route.request()
    const ruta = new URL(req.url()).pathname
    const m = req.method()
    if (m === 'GET' || ruta === '/api/auth/login') return route.continue()
    let cuerpo = null
    try { cuerpo = req.postDataJSON() } catch { cuerpo = null }
    const propias = prueba ? [`/api/usuarios/tecnicos/${prueba.idTec}/activar`, `/api/usuarios/tecnicos/${prueba.idTec}/desactivar`] : []
    const ok =
      (m === 'POST' && ruta === '/api/usuarios/tecnicos' && typeof cuerpo?.nombreTecnico === 'string' && cuerpo.nombreTecnico.startsWith('captura-')) ||
      (m === 'PATCH' && propias.includes(ruta)) ||
      (m === 'DELETE' && prueba !== null && ruta === `/api/usuarios/tecnicos/${prueba.idTec}`) ||
      (esUsuarioPrueba && m === 'PATCH' && ruta === '/api/auth/cambiar-password')
    if (ok) { console.log('     PERMITIDA', m, ruta); return route.continue() }
    console.log('     BLOQUEADA', m, ruta)
    return route.abort()
  })
}

async function login(page, user, pass) {
  await page.goto(base + '/login')
  await page.getByPlaceholder('Usuario').fill(user)
  await page.getByPlaceholder('Contraseña').fill(pass)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await page.getByText('FSGR:').waitFor()
}
const menu = async (page, item) => {
  await page.getByRole('button', { name: /Hola,/ }).click()
  if (item) await page.getByRole('menuitem', { name: item, exact: true }).click()
}
const esc = async (page) => { await page.keyboard.press('Escape'); await page.waitForTimeout(250) }

const browser = await chromium.launch()
const nuevoContexto = async (opciones) => {
  const c = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 })
  await guardia(c, opciones)
  return c
}

// ================= ADMIN (inicio de sesión 1) =================
const ctxAdmin = await nuevoContexto()
const a = await ctxAdmin.newPage()
await paso('login admin', () => login(a, process.env.ADMIN_USER, process.env.ADMIN_PASS))
await paso('gestion-menu-usuario-admin', async () => { await menu(a); await shot(a, 'gestion-menu-usuario-admin'); await esc(a) })
await paso('gestion-menu-usuario-posicion + barra', async () => {
  await menu(a); await shot(a, 'gestion-menu-usuario-posicion'); await esc(a); await shot(a, 'gestion-barra-version')
})
await paso('gestion-csv-sin-exportable', async () => {
  await a.goto(base + '/clientes'); await a.waitForTimeout(800); await menu(a); await shot(a, 'gestion-csv-sin-exportable'); await esc(a)
})

const filaT = (texto) => a.getByRole('row').filter({ hasText: texto })
await paso('gestion-tecnicos-vacio-formulario', async () => {
  await a.goto(base + '/reparaciones'); await menu(a, 'Gestionar técnicos')
  await a.getByText('Gestión de usuarios', { exact: true }).waitFor(); await a.waitForTimeout(800)
  await shot(a, 'gestion-tecnicos-vacio-formulario'); await shot(a, 'gestion-tecnicos-tabla')
})
await paso('gestion-tecnicos-fila-seleccionada', async () => { await a.getByRole('row').nth(1).click(); await shot(a, 'gestion-tecnicos-fila-seleccionada') })
await paso('gestion-tecnicos-combo-rol', async () => {
  await a.getByRole('combobox').filter({ hasText: 'TECNICO' }).first().click(); await shot(a, 'gestion-tecnicos-combo-rol'); await esc(a)
})
const campos = {
  tecnico: () => a.getByPlaceholder('Nombre visible en reparaciones'), usuario: () => a.getByPlaceholder('Credencial de login'),
  clave: () => a.getByPlaceholder('Contraseña', { exact: true }), confirmar: () => a.getByPlaceholder('Repite la contraseña'),
}
const vaciar = async () => { for (const c of Object.values(campos)) await c().fill('') }
await paso('gestion-tecnicos-dup-tecnico / dup-usuario', async () => {
  const primera = (await a.getByRole('row').nth(1).getByRole('cell').allTextContents()).map((t) => t.trim())
  await campos.tecnico().fill(`  ${primera[0].toUpperCase()} `); await shot(a, 'gestion-tecnicos-dup-tecnico'); await vaciar()
  await campos.usuario().fill(primera[1].toUpperCase()); await shot(a, 'gestion-tecnicos-dup-usuario'); await vaciar()
})
const registrar = () => a.getByRole('button', { name: 'Registrar técnico' }).click()
await paso('errores de validación', async () => {
  await registrar(); await shot(a, 'gestion-tecnicos-error-vacios')
  await campos.tecnico().fill(P.tecnico); await campos.usuario().fill(P.usuario); await campos.clave().fill('secreta1'); await campos.confirmar().fill('otra123')
  await registrar(); await shot(a, 'gestion-tecnicos-error-no-coinciden')
  await campos.clave().fill('abc'); await campos.confirmar().fill('abc'); await registrar(); await shot(a, 'gestion-tecnicos-error-corta')
})
await paso('gestion-tecnicos-error-409-admin', async () => {
  await campos.tecnico().fill(`captura-409-${marca}`); await campos.usuario().fill(process.env.ADMIN_USER)
  await campos.clave().fill(P.clave); await campos.confirmar().fill(P.clave)
  await registrar(); await a.waitForTimeout(800); await shot(a, 'gestion-tecnicos-error-409-admin'); await vaciar()
})
await paso('gestion-tecnicos-alta-ok', async () => {
  await campos.tecnico().fill(P.tecnico); await campos.usuario().fill(P.usuario); await campos.clave().fill(P.clave); await campos.confirmar().fill(P.clave)
  const alta = a.waitForResponse((r) => new URL(r.url()).pathname === '/api/usuarios/tecnicos' && r.request().method() === 'POST')
  await registrar(); console.log('     POST ->', (await alta).status())
  await filaT(P.tecnico).waitFor(); await filaT(P.tecnico).scrollIntoViewIfNeeded(); await shot(a, 'gestion-tecnicos-alta-ok')
  prueba = await a.evaluate(async (usuario) => {
    const s = JSON.parse(sessionStorage.getItem('fsgr.sesion') ?? '{}')
    const l = await (await fetch('/api/usuarios/tecnicos', { headers: { Authorization: `Bearer ${s.token}` } })).json()
    const u = l.find((x) => x.nombreUsuario === usuario)
    return u ? { idTec: u.idTec, idUsu: u.idUsu } : null
  }, P.usuario)
  console.log('     usuario de prueba:', P.usuario, JSON.stringify(prueba))
})
await paso('tooltips y desactivado', async () => {
  await filaT(P.tecnico).getByRole('button', { name: 'Desactivar acceso' }).hover(); await a.waitForTimeout(900); await shot(a, 'gestion-tecnicos-tooltip-desactivar')
  await filaT(P.tecnico).getByRole('button', { name: 'Desactivar acceso' }).click(); await filaT(P.tecnico).getByText('Inactivo', { exact: true }).waitFor()
  await shot(a, 'gestion-tecnicos-desactivado')
  await filaT(P.tecnico).getByRole('button', { name: 'Activar acceso' }).hover(); await a.waitForTimeout(900); await shot(a, 'gestion-tecnicos-tooltip-activar')
})
// Login con el usuario inactivo (inicio de sesión 2): contexto aparte, falla sin escribir.
await paso('gestion-tecnicos-login-inactivo', async () => {
  const c = await nuevoContexto(); const p = await c.newPage()
  await p.goto(base + '/login'); await p.getByPlaceholder('Usuario').fill(P.usuario); await p.getByPlaceholder('Contraseña').fill(P.clave)
  await p.getByRole('button', { name: 'Iniciar Sesión' }).click(); await p.waitForTimeout(1500); await shot(p, 'gestion-tecnicos-login-inactivo'); await c.close()
})
await paso('reactivar', async () => {
  await filaT(P.tecnico).getByRole('button', { name: 'Activar acceso' }).click(); await filaT(P.tecnico).getByText('Activo', { exact: true }).waitFor()
})
await paso('gestion-tecnicos-no-se-puede-eliminar', async () => {
  // Solo lee (GET tiene-reparaciones): la primera fila que no sea la de prueba y dé "No se puede eliminar".
  for (let i = 1; i <= 5; i++) {
    const f = a.getByRole('row').nth(i)
    if ((await f.textContent()).includes(P.tecnico)) continue
    await f.locator('button:has(img[src="/borrar.png"])').click(); await a.waitForTimeout(800)
    const aviso = a.getByRole('dialog', { name: 'No se puede eliminar' })
    if (await aviso.count()) { await shot(a, 'gestion-tecnicos-no-se-puede-eliminar'); await aviso.getByRole('button', { name: 'Aceptar' }).click(); return }
    await a.getByRole('dialog', { name: 'Eliminar técnico' }).getByRole('button', { name: 'Cancelar' }).click()
  }
})
await paso('gestion-tecnicos-confirmar-eliminar', async () => {
  await filaT(P.tecnico).locator('button:has(img[src="/borrar.png"])').click()
  const d = a.getByRole('dialog', { name: 'Eliminar técnico' }); await d.waitFor(); await shot(a, 'gestion-tecnicos-confirmar-eliminar')
  await d.getByRole('button', { name: 'Cancelar' }).click()
})
await paso('gestion-tecnicos-tras-cerrar', async () => { await a.getByText('Cerrar', { exact: true }).click(); await a.waitForTimeout(1200); await shot(a, 'gestion-tecnicos-tras-cerrar') })

// Logs
const accion = async (texto, opcion) => {
  await a.getByPlaceholder('Acción...').fill(texto)
  if (opcion) { await a.getByRole('option', { name: opcion, exact: true }).click(); await a.waitForTimeout(1200) }
}
await paso('gestion-logs-inicial', async () => {
  await menu(a, 'Ver logs'); await a.getByText('Log de actividad', { exact: true }).waitFor(); await a.waitForTimeout(1500)
  await shot(a, 'gestion-logs-inicial'); await shot(a, 'gestion-logs-maximizada')
  await a.getByRole('row').nth(1).click(); await shot(a, 'gestion-logs-fila-seleccionada')
  await a.getByRole('columnheader', { name: 'Fecha' }).click(); await shot(a, 'gestion-logs-orden-cabecera')
})
await paso('popups de acción y técnico', async () => {
  await a.getByPlaceholder('Acción...').click(); await shot(a, 'gestion-logs-popup-accion')
  await accion('pedido'); await shot(a, 'gestion-logs-popup-accion-filtrado')
  await accion('LOGIN', 'LOGIN'); await shot(a, 'gestion-logs-filtro-accion')
  await a.getByRole('button', { name: 'Limpiar filtros' }).click(); await a.waitForTimeout(1200)
  await a.getByPlaceholder('Técnico...').click(); await shot(a, 'gestion-logs-popup-tecnico'); await esc(a)
})
await paso('fechas, buscador y vacío', async () => {
  await a.getByLabel('Desde:').click(); await shot(a, 'gestion-logs-fechas'); await esc(a)
  if (process.env.FECHA_MADRUGADA) { await a.getByLabel('Desde:').fill(process.env.FECHA_MADRUGADA); await a.getByLabel('Hasta:').fill(process.env.FECHA_MADRUGADA); await a.waitForTimeout(1500); await shot(a, 'gestion-logs-fechas-madrugada') }
  await a.getByRole('button', { name: 'Limpiar filtros' }).click(); await a.waitForTimeout(1200)
  if (process.env.IMEI_BUSCADOR) { await a.getByPlaceholder('Buscar...').fill(process.env.IMEI_BUSCADOR); await shot(a, 'gestion-logs-buscador') }
  await a.getByPlaceholder('Buscar...').fill('zzzz-sin-resultados'); await shot(a, 'gestion-logs-vacio'); await a.getByPlaceholder('Buscar...').fill('')
})
for (const [nombre, codigo] of [['gestion-logs-detalle', 'CREAR_ASIGNACION'], ['gestion-logs-detalle-motivo', 'ELIMINAR_ASIGNACION'], ['gestion-logs-detalle-largo', 'EDITAR_REPARACION']]) {
  await paso(nombre, async () => {
    await accion(codigo, codigo); await a.getByRole('row').nth(1).dblclick()
    await a.getByRole('dialog', { name: 'Detalle del log' }).waitFor(); await shot(a, nombre); await esc(a)
    await a.getByRole('button', { name: 'Limpiar filtros' }).click(); await a.waitForTimeout(1000)
  })
}

// ================= Usuario de prueba, TECNICO (inicio de sesión 3) =================
await espera(13_000) // límite de nginx: 5 inicios por minuto con ráfaga de 3
const ctxP = await nuevoContexto({ esUsuarioPrueba: true })
const p = await ctxP.newPage()
await paso('login usuario de prueba', () => login(p, P.usuario, P.clave))
await paso('gestion-menu-usuario-tecnico', async () => { await menu(p); await shot(p, 'gestion-menu-usuario-tecnico'); await esc(p) })
const dlg = () => p.getByRole('dialog', { name: 'Cambiar contraseña' })
const rellenar = async (actual, nueva, confirmar) => {
  await dlg().getByPlaceholder('Contraseña actual', { exact: true }).fill(actual)
  await dlg().getByPlaceholder('Nueva contraseña', { exact: true }).fill(nueva)
  await dlg().getByPlaceholder('Confirmar contraseña', { exact: true }).fill(confirmar)
}
const guardar = async () => { await dlg().getByRole('button', { name: 'Guardar' }).click(); await p.waitForTimeout(900) }
await paso('contraseña: vacía, ojo y errores', async () => {
  await menu(p, 'Cambiar contraseña'); await dlg().waitFor(); await shot(p, 'gestion-password-vacia')
  await guardar(); await shot(p, 'gestion-password-error-vacios')
  await rellenar(P.clave, 'abc', 'abc'); await guardar(); await shot(p, 'gestion-password-error-corta')
  await rellenar(P.clave, 'nueva123', 'otra1234'); await guardar(); await shot(p, 'gestion-password-error-no-coinciden')
  await rellenar(P.clave, 'nueva123', 'nueva123'); await dlg().getByRole('button', { name: 'Mostrar contraseña' }).first().click(); await shot(p, 'gestion-password-ojo')
  await rellenar('incorrecta1', 'nueva123', 'nueva123'); await guardar(); await shot(p, 'gestion-password-error-actual')
  await p.keyboard.press('Escape'); await p.waitForTimeout(300); await shot(p, 'gestion-password-enter-esc')
})
await paso('gestion-password-exito y restaurar', async () => {
  await menu(p, 'Cambiar contraseña'); await rellenar(P.clave, P.nueva, P.nueva); await guardar()
  const aviso = p.getByRole('dialog', { name: 'Información' }); await aviso.waitFor(); await shot(p, 'gestion-password-exito')
  await aviso.getByRole('button', { name: 'Aceptar' }).click()
  await menu(p, 'Cambiar contraseña'); await rellenar(P.nueva, P.clave, P.clave); await guardar()
  await p.getByRole('dialog', { name: 'Información' }).getByRole('button', { name: 'Aceptar' }).click()
})
await paso('gestion-cerrar-sesion-login', async () => { await menu(p, 'Cerrar Sesión'); await p.waitForTimeout(600); await shot(p, 'gestion-cerrar-sesion-login') })
await ctxP.close()

// ================= SUPERTECNICO (inicio de sesión 4) =================
await espera(13_000)
const ctxS = await nuevoContexto()
const s = await ctxS.newPage()
await paso('gestion-menu-usuario-supertecnico', async () => { await login(s, process.env.E2E_USER, process.env.E2E_PASS); await menu(s); await shot(s, 'gestion-menu-usuario-supertecnico'); await esc(s) })
await paso('asig-csv', async () => {
  await s.goto(base + '/reparaciones/asignaciones'); await s.waitForTimeout(1500); await menu(s); await shot(s, 'asig-csv'); await esc(s)
})
await ctxS.close()

// ================= Borrado del usuario de prueba (sesión ADMIN abierta) =================
await paso('borrar usuario de prueba', async () => {
  await a.goto(base + '/gestion/tecnicos'); await filaT(P.tecnico).waitFor()
  await filaT(P.tecnico).locator('button:has(img[src="/borrar.png"])').click()
  const d = a.getByRole('dialog', { name: 'Eliminar técnico' }); await d.waitFor()
  const borrado = a.waitForResponse((r) => r.request().method() === 'DELETE')
  await d.getByRole('button', { name: 'Eliminar', exact: true }).click()
  console.log('     DELETE ->', (await borrado).status(), '(si no es 204, borrar a mano y anotar)', P.usuario)
})

await browser.close()
console.log('FIN')
```

Uso (credenciales de `~/.env.e2e` exportadas; `IMEI_BUSCADOR` y `FECHA_MADRUGADA` opcionales, sacados de la BD de pruebas, nunca escritos en el script):

```bash
set -a; . ~/.env.e2e; set +a
E2E_BASE_URL=http://localhost:5173 OUT_DIR=/c/Users/dev/Documents/Apuntes/paridad-capturas/gestion node /c/Users/dev/Documents/Apuntes/herramientas/paridad-capturas/capturas-gestion.mjs
```

Anotar pareja a pareja en `Apuntes/paridad-capturas/gestion/COMPARACION-6.md` (formato de `almacen/COMPARACION-4b.md`): diferencia deliberada confirmada, calco, no comparable por datos o diferencia nueva. **Cada diferencia nueva se decide con el usuario**, no sobre la marcha; las que se corrijan van a `feature/web-gestion` con su test y su commit, y las aceptadas a la ficha correspondiente ("Decididas durante la ejecución y la comparación de capturas"). Se cierran también los "Pendiente de decidir" de las fichas (placeholder de tabla vacía, título del aviso de éxito y columna de acciones de técnicos a 80 px).

**U7. Fichas marcadas** contra las capturas y los tests; commit en la rama:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git add docs/paridad/tecnicos.md docs/paridad/logs.md docs/paridad/cuenta.md docs/paridad/shell.md docs/paridad/asignaciones.md
git commit -m "docs(web): fichas de gestion marcadas tras comparar capturas"
git cat-file -p HEAD | tail -1
git push origin feature/web-gestion
```

**U8. Merge de la web en `main` y push** (con OK):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout main && git pull --ff-only
git merge --no-ff --no-edit feature/web-gestion
npm run check && npm run build
git push origin main
```

**U9. Despliegue de la web en la VDC** (lo ejecuta el usuario):

```bash
ssh prod
cd /opt/reparaciones && git -C gestion-reparaciones-web pull && git -C gestion-reparaciones-web log --oneline -1
docker compose up -d --build nginx
exit
```

Verificación del bundle desde el PC: el `index-*.js` que sirve producción es el del build local de U8.

```bash
set -a; . ~/.env.e2e; set +a
curl -s "$E2E_BASE_URL/" | grep -o 'assets/index-[^"]*\.js'
grep -o 'assets/index-[^"]*\.js' /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web/dist/index.html
```

Expected: el mismo nombre en las dos líneas. Si difiere con commits nuevos, la variante `--no-cache` de §P8 de la guía.

**U10. Tag `v0.8.0`** (con OK): fijar antes la fecha de la entrada del CHANGELOG (commit `docs(web): fecha de la 0.8.0` en `main` y push) y después:

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git tag -a v0.8.0 -m "v0.8.0: gestion de tecnicos, logs, cambiar contraseña y csv de asignaciones"
git push origin v0.8.0
```

**U11. Gitlinks en el raíz** (con OK para el push):

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones
git add gestion-reparaciones-servidor gestion-reparaciones-web docs/superpowers/plans/2026-09-26-web-gestion.md
git commit -m "chore: gitlinks servidor y web tras el sub-proyecto 6 (web v0.8.0) y cierre del plan"
git push origin main
```

Antes, completar en este plan un "## Cierre final" con el esquema del 4b (`docs/superpowers/plans/2026-09-25-web-almacen-pedidos.md:11770-11778`): merges, despliegues, smoke, comparación, tag y suites finales.

**U12. `Apuntes/plan-futuro.md`, §9** (privado, sin commit): marcar `[x]` la casilla **6** con el mismo nivel de detalle que la del 4b (commits de `main`, tag, tests, despliegue, smoke, capturas comparadas, resultado de la Task 0), y añadir debajo:

- `[ ] Limpieza en la VDC del 6 (se suma a las anteriores)`: el usuario de prueba de las capturas si no se borró (nombre en `CAPTURAS-6.md`, "Datos creados") y el del smoke si falló su limpieza (anotación `e2e-creado` y `expect.soft` de `afterAll`); los logs `CREAR_USUARIO`, `ACTIVAR_USUARIO`, `DESACTIVAR_USUARIO`, `ELIMINAR_USUARIO` y `LOGIN` del ADMIN que dejan el smoke y las capturas (no hay endpoint para borrarlos); los `CAMBIAR_PASSWORD` y `LOGIN` del usuario de prueba solo quedan si el usuario no llegó a borrarse (el `DELETE` borra su log, G8).
- `[ ] Backlog 6 → web / servidor`: lo triado como backlog en las revisiones de las tareas y en la revisión final.

**U13. Memoria** (Claude, con OK): actualizar `project_migracion_web_programa.md` (SP6 cerrado, web v0.8.0, siguiente el 7 en paralelo y después el 8, piloto y corte) y su línea en `MEMORY.md`.

Recuento: **+0 tests** (documentación, versión y verificación).

---

---

## Trazabilidad: reglas de los inventarios → test

### Servidor (T1-T5)

| Regla (inventario §) | Test |
|---|---|
| Técnicos §7.1 / §15: técnico, usuario y contraseña obligatorios (nombres tras trim) | `UsuarioControllerTest.altaConCampoVacioEs422`, `altaConNombresEnBlancoONulosEs422` |
| Técnicos §7.1 / §15: contraseña de al menos 6 en el alta | `UsuarioControllerTest.altaConPasswordCortaEs422`, `losLimitesExactosSonValidos` |
| Técnicos §7.1 / §15 / §17.2: longitudes 50 y 100 (texto nuevo) | `UsuarioControllerTest.altaConUsuarioDeMasDe50Es422`, `altaConTecnicoDeMasDe100Es422`, `losLimitesExactosSonValidos` |
| Técnicos §7.1 / §15 / §12: rol permitido como 422 `{message}` (antes 400 texto plano); rol nulo = TECNICO | `UsuarioControllerTest.altaConRolNoPermitidoEs422EnVezDe400`, `altaSinRolGuardaTecnico` |
| Spec §4.1: orden campos → 6 → 50 → 100 → rol | `UsuarioControllerTest.elOrdenEsCamposSeisCincuentaCienRol` |
| Técnicos §7.1 / §15: trim de los nombres al guardar (contraseña sin trim) | `UsuarioControllerTest.altaValidaGuardaLosNombresRecortadosYRegistraLog` |
| Técnicos §7.1 / §7.2: duplicados 409 con los textos del cliente | `UsuarioControllerTest.tecnicoDuplicadoEs409SinEscribir`, `usuarioDuplicadoEs409SinEscribir` |
| Técnicos §12: `DataIntegrityViolationException` → 409 conservado | `UsuarioControllerTest.violacionDeIntegridadSigueSiendo409SinLog` |
| Técnicos §6.1: éxito 201 y log `CREAR_USUARIO` | `UsuarioControllerTest.altaValidaGuardaLosNombresRecortadosYRegistraLog`, `altaSinRolGuardaTecnico` |
| Técnicos §6.2: activar/desactivar escriben y registran `ACTIVAR_USUARIO`/`DESACTIVAR_USUARIO` | `UsuarioControllerTest.activarYDesactivarEscribenYRegistranLog` |
| Técnicos §6.5 / §15: `idTec` inexistente → 404 (antes 500 y `{value:false}`) | `UsuarioControllerTest.activarUnInexistenteEs404SinEscribir`, `desactivarUnInexistenteEs404SinEscribir`, `tieneReparacionesDeUnInexistenteEs404`, `eliminarUnInexistenteEs404SinBorrar` |
| Técnicos §6.3 / §15: `tiene-reparaciones` mira las nueve FK (no solo `Reparacion.ID_TEC`) | `UsuarioDAOReferenciasTest.reparacionPropiaCuenta`, `haberAsignadoCuenta`, `haberEntregadoGlassCuenta`, `revisionEsteticaCuenta`, `revisionFuncionalCuenta`, `envioCuenta`, `devolucionDeEnvioCuenta`, `solicitudDeStockCuenta`, `movimientoDeTelefonoCuenta`, `sinNingunaReferenciaEsFalseYMiraLasNueve`, `tecnicoSinUsuarioSoloMiraReparacion`, `tieneReparacionesDelegaEnTieneReferencias`; `UsuarioControllerTest.tieneReparacionesDevuelveTieneReferencias` |
| Técnicos §6.3 / §15: el DELETE comprueba las referencias → 409 `"\"{nombre}\" tiene reparaciones asociadas."` sin borrar | `UsuarioControllerTest.eliminarConReferenciasEs409SinBorrarNiRegistrar` |
| Técnicos §8 / §15: `idUsu` resuelto desde `idTec`, el de la query se ignora (y es opcional) | `UsuarioControllerTest.eliminarResuelveElUsuarioDesdeElTecnicoEIgnoraElDeLaQuery`, `eliminarSinIdUsuEnLaQueryTambienBorra`, `eliminarUnTecnicoSinUsuarioEs404`; `OpenApiContractTest.elContratoPublicaLaGestion` |
| Técnicos §6.3: log `ELIMINAR_USUARIO` con `ID_TEC`, `ID_USU` y `NOMBRE` | `UsuarioControllerTest.eliminarResuelveElUsuarioDesdeElTecnicoEIgnoraElDeLaQuery` |
| Técnicos §8 / §15: `POST`/`DELETE /api/tecnicos` solo ADMIN | `RolesUsuarioTecnicoTest.crearTecnicoYSupertecnicoReciben403`, `crearAdminEs201`, `borrarTecnicoYSupertecnicoReciben403`, `borrarAdminEs204` |
| Técnicos §12 / §14: el contrato decía 200 `Record<string, never>` en el alta (real: 201/409/422) | `OpenApiContractTest.elContratoPublicaLaGestion` |
| Contraseña §7.1 #1 / §15: campos vacíos o ausentes → 422 "Rellena todos los campos." (sin el "rawPassword cannot be null") | `AuthControllerCambiarPasswordTest.camposVaciosOAusentesSon422` |
| Contraseña §7.1 #2 / §15: nueva de menos de 6 → 422 (antes 400 sin cuerpo) | `AuthControllerCambiarPasswordTest.nuevaCortaEs422EnVezDe400`, `elOrdenEsCamposYDespuesLongitud` |
| Contraseña §7.1: sin trim | `AuthControllerCambiarPasswordTest.losEspaciosCuentanComoCaracteres` |
| Contraseña §7.2: actual incorrecta → 422 con su texto, sin log | `AuthControllerCambiarPasswordTest.actualIncorrectaSigueSiendo422ConSuTextoYSinLog` |
| Contraseña §7.2: éxito 204 y log `CAMBIAR_PASSWORD` | `AuthControllerCambiarPasswordTest.cambioValidoEs204YRegistraLog` |
| Contraseña §12 / §14: el contrato decía 200 (real: 204/422) | `OpenApiContractTest.elContratoPublicaLaGestion` |
| Logs §15 / spec G3: `limite` opcional 1..5000, 422 fuera de rango, sin él todo (JavaFX) | `LogControllerTest.limiteFueraDeRangoEs422SinConsultar`, `losExtremosDelRangoSonValidos`, `sinLimitePasaNullYLosFiltrosTalCual`; `LogDAOFiltroTest.accionYUsuarioFiltranPorIgualdadYElLimiteVaAlFinal`, `sinFiltrosNiLimiteNoLlevaParametrosYOrdenaConDesempate`, `laSobrecargaDeCuatroEsSinLimite` |
| Logs §11 / §15 / spec G5: fechas en días de Madrid convertidos a UTC, `hasta` exclusivo al día siguiente, sin `DATE()` | `LogDAOFiltroTest.inicioDeUnDiaDeVeranoEsLas22DelDiaAnteriorEnUtc`, `inicioDeUnDiaDeInviernoEsLas23DelDiaAnteriorEnUtc`, `elDiaDelCambioDeHoraSeCalculaConLaZonaDeCadaExtremo`, `desdeYHastaEnVeranoSonLosLimitesDelDiaDeMadridEnUtc`, `desdeYHastaEnInviernoSonLosLimitesDelDiaDeMadridEnUtc`, `hastaSoloEsExclusivoAlInicioDelDiaSiguiente` |
| Logs §3: orden sin desempate por `ID_LOG` | `LogDAOFiltroTest.sinFiltrosNiLimiteNoLlevaParametrosYOrdenaConDesempate` |
| Logs §3 / §7: acción y usuario por igualdad; en blanco no filtran | `LogDAOFiltroTest.accionYUsuarioFiltranPorIgualdadYElLimiteVaAlFinal`, `accionYUsuarioEnBlancoNoFiltran` |
| Logs §12.1 / §15 / spec G4: lista real de acciones, alfabética, solo ADMIN | `LogDAOFiltroTest.accionesSonLasDistintasEnOrdenAlfabetico`; `LogControllerTest.accionesDevuelveLaListaDelDao`, `accionesEsSoloAdmin`; `OpenApiContractTest.elContratoPublicaLaGestion` |
| Logs §14: `detalle`/`motivo` nulables en el contrato | `OpenApiContractTest.elContratoPublicaLaGestion` |

No se cubren con test propio (se conservan sin cambios): el borrado de `Log_Actividad` del usuario eliminado (G8, cuerpo de `UsuarioDAO.eliminarTecnico` intacto) y `desde > hasta` → lista vacía (lo da el propio SQL; logs §7).

### Web: cimientos y Gestionar técnicos (T6-T9)

| Regla (inventario §) | Test |
|---|---|
| tecnicos §1 cabecera: logo 46 px, "Gestión de usuarios" 18 px negrita, "Registra o elimina accesos al sistema" 12 px | `TecnicosPage.test.tsx` › `it('cabecera con logo, cuatro campos con sus etiquetas y placeholders, fila de acción, tabla y "Cerrar"')` |
| tecnicos §1 cuatro campos: etiquetas 11 px negrita, placeholders, contraseñas sin ojo | ídem |
| tecnicos §1 combo de rol 130 px, "TECNICO"/"SUPERTECNICO", TECNICO por defecto, sin ADMIN | ídem + `validacion.test.ts` › `it('textos exactos del JavaFX, roles del combo en mayúsculas y sin ADMIN')` |
| tecnicos §1 "Registrar técnico" no es `defaultButton` (Enter no registra) | `TecnicosPage.test.tsx` › `it('valida en el orden del JavaFX sin llamar al servidor, y Enter no registra')` |
| tecnicos §1 "Técnicos registrados" 13 px negrita; fondo `#EFEFEF` | `TecnicosPage.test.tsx` › `it('cabecera con logo, …')` |
| tecnicos §1 y §6.4 "Cerrar" estilo enlace, cierra sin preguntar | `TecnicosPage.test.tsx` › `it('"Cerrar" vuelve a la ruta de volverA sin preguntar aunque haya texto')` y `it('"Cerrar" sin volverA (URL tecleada) va a /reparaciones')` |
| tecnicos §2 al cerrar, las listas de técnicos del resto de la vista ven altas y bajas | `TecnicosPage.test.tsx` › `it('una escritura con éxito marca como caducados los técnicos del resto de la web')`; `tecnicos/api.test.tsx` › los tres `RECARGADAS` |
| tecnicos §3 carga `GET /api/usuarios/tecnicos`, orden del servidor, sin poller ni recarga por foco | `gestion/api.test.tsx` › `it('useUsuariosTecnicos lee GET /api/usuarios/tecnicos en el orden del servidor y no recarga por foco')`; `TecnicosPage.test.tsx` › `it('pinta la lista del servidor en su orden, sin ordenación por cabecera')` |
| tecnicos §3 error de carga "Error al cargar los usuarios." inline, tabla vacía | `TecnicosPage.test.tsx` › `it('error de carga: "Error al cargar los usuarios." en la línea, tabla vacía con su placeholder y sin diálogo')` |
| tecnicos §4 columnas Técnico 160, Usuario 130, Rol 110, Estado 90, acciones sin cabecera | `columnas.test.tsx` › `it('Técnico, Usuario, Rol, Estado y una de acciones sin cabecera, con los prefWidth del FXML')` |
| tecnicos §4 rol en mayúsculas tal cual | `columnas.test.tsx` › `it('nombre de técnico, de usuario y el rol en mayúsculas tal cual, en el orden recibido')` |
| tecnicos §4 badge "Activo" `#2E7D32`/`#D4EDDA`, "Inactivo" `#B03040`/`#F5E6E6`, radio 10, 11 px negrita | `columnas.test.tsx` › `it('badge "Activo" (#2E7D32 sobre #D4EDDA) e "Inactivo" …')` |
| tecnicos §4 candado `Unlock.png`/`Lock.png` 18 px con tooltip "Desactivar acceso"/"Activar acceso"; papelera 22 px sin tooltip en todas las filas | `columnas.test.tsx` › `it('candado: abierto (Unlock.png) …; papelera sin tooltip en todas las filas')` |
| tecnicos §4 sin ordenación por cabecera (G10) | `TecnicosPage.test.tsx` › `it('pinta la lista del servidor en su orden, sin ordenación por cabecera')` |
| tecnicos §6.1 vacíos (nombres con trim) → "Todos los campos son obligatorios." | `validacion.test.ts` › `it('vacíos (nombres con trim, contraseña sin trim) → …')` |
| tecnicos §6.1 confirmación vacía cae en "Las contraseñas no coinciden." | `validacion.test.ts` › `it('la confirmación vacía no cuenta como campo obligatorio: cae en "no coinciden"')` |
| tecnicos §6.1 orden "no coinciden" → "menos de 6", contraseñas sin trim | `validacion.test.ts` › `it('"no coinciden" va antes que "menos de 6" y compara sin trim')` y `it('menos de 6 caracteres, contando los espacios; con todo bien → null')`; `TecnicosPage.test.tsx` › `it('valida en el orden del JavaFX …')` |
| tecnicos §6.1 POST con `{nombreTecnico, nombreUsuario, password, rol}`, nombres con trim | `validacion.test.ts` › `it('cuerpoAlta recorta los dos nombres, …')`; `tecnicos/api.test.tsx` › `it('useRegistrar: POST /api/usuarios/tecnicos …')`; `TecnicosPage.test.tsx` › `it('alta: POST con nombres recortados y el rol del combo; …')` |
| tecnicos §6.1 éxito: vacía los cuatro campos, combo a TECNICO, línea vacía, recarga, sin mensaje de éxito | `TecnicosPage.test.tsx` › `it('alta: POST con nombres recortados …, sin mensaje de éxito')` |
| tecnicos §6.1 y §6.5 error del alta: 409/422 → `message`; otro → "Error al registrar. Inténtalo de nuevo." | `TecnicosPage.test.tsx` › `it.each(…)('alta con %s: …')`; `errores.test.ts` › `it('409 y 422 → el message del servidor si la acción los espera')` y `it('400, conexión y cualquier otra cosa → el texto fijo')` |
| tecnicos §6.2 candado sin confirmación, `PATCH …/desactivar`/`…/activar`, recarga | `TecnicosPage.test.tsx` › `it('candado sin confirmación: …')`; `tecnicos/api.test.tsx` › `it.each(…)('useCambiarActivo(activar: %s) → PATCH %s sin cuerpo y recarga')` |
| tecnicos §6.2 error → "Error al cambiar el estado del técnico." (404 → `message`, G9) | `TecnicosPage.test.tsx` › `it.each(…)('candado con %s: texto inline y sin recarga')` |
| tecnicos §6.3 comprobación previa `GET …/tiene-reparaciones` → `value` | `tecnicos/api.test.tsx` › `it('consultarTieneReparaciones lee value de GET …/{idTec}/tiene-reparaciones')` |
| tecnicos §6.3 con reparaciones: "No se puede eliminar" + tres líneas, sin borrar | `TecnicosPage.test.tsx` › `it('papelera de un técnico con reparaciones: …')` |
| tecnicos §6.3 sin reparaciones: "Eliminar técnico", "¿Eliminar a \"…\" definitivamente?" + "Se borrarán…"; `DELETE …?idUsu=` y recarga | `TecnicosPage.test.tsx` › `it('papelera sin reparaciones: confirmación "Eliminar técnico" → DELETE con idUsu → recarga')`; `tecnicos/api.test.tsx` › `it('useEliminar → DELETE /api/usuarios/tecnicos/21?idUsu=11 y recarga')` |
| tecnicos §6.3 cancelar no borra | `TecnicosPage.test.tsx` › `it('"Cancelar" en la confirmación no borra')` |
| tecnicos §6.3 error del borrado "Error al eliminar el técnico." (404/409 → `message`) | `TecnicosPage.test.tsx` › `it.each(…)('DELETE con %s …: texto inline y sin recarga')` |
| tecnicos §6.3 error de la comprobación "Error al comprobar las reparaciones del técnico." | `TecnicosPage.test.tsx` › `it.each(…)('fallo de la comprobación con %s: …')` |
| tecnicos §7.1 duplicados en vivo sin mayúsculas y con trim, contra la lista cargada, cada campo con su columna | `validacion.test.ts` › `it('duplicadosEnVivo: …')` |
| tecnicos §7.1 aviso bajo el campo (10 px rojo) y "Registrar técnico" deshabilitado; "admin" pasa en vivo | `TecnicosPage.test.tsx` › `it('duplicados en vivo (sin mayúsculas y con trim) bajo cada campo …; "admin" pasa')` |
| tecnicos §8 solo ADMIN (ítem y ventana) | `gestion/rutas.test.tsx` › `it('el ADMIN entra en /gestion/tecnicos y en /gestion/logs sin aviso')` y `it.each(…)('%s por URL recibe el aviso genérico de permisos y sale a /reparaciones')` |
| tecnicos §10 sin CSV ("Descargar CSV" deshabilitado) | `TecnicosPage.test.tsx` › `it('no registra exportable: "Descargar CSV" queda deshabilitado en esta ruta (calco)')` |
| tecnicos §17.10 (G9) la línea inline se vacía al empezar cada acción | `TecnicosPage.test.tsx` › `it('la línea inline se vacía al empezar cualquier acción (G9)')` |
| password §1.1 ojo por campo `ojo_activar`/`ojo_desactivar` 18 px, padding derecho para el ojo | `CampoPassword.test.tsx` › `it('empieza oculto, …')`, `it('el ojo alterna type, aria-label e icono …')`, `it('llama a onChange con cada tecla …')` |
| password §1.3 "Gestionar técnicos"/"Ver logs" solo ADMIN, mismo orden (se conserva) y navegan con `volverA` | `TopBar.test.tsx` › `it('el admin ve además "Gestionar técnicos" y "Ver logs"')` (existente) y `it.each(…)('"%s" navega a %s con volverA = la ruta desde la que se abrió')` |
| password §6.1 (G6) "Cambiar contraseña" no navega (la ruta desaparece) | `TopBar.test.tsx` › `it('"Cambiar contraseña" ya no navega a /cuenta/cambiar-password …')` (la Task 12 lo cambia por "abre el diálogo") |
| password §13 / G11 texto del login "Rellena usuario y contraseña." con usuario (trim) o contraseña vacíos | `LoginPage.test.tsx` › `it('con campos vacíos no llama a la API y avisa con el texto del JavaFX')` y `it('un usuario de solo espacios cuenta como vacío (trim) y no llama a la API')` |
| logs §(columna Fecha) `dd/MM/yyyy HH:mm:ss` en Madrid (lo consume la Task 10) | `fechas.test.ts` › `it('patrón de la columna Fecha del log (dd/MM/yyyy HH:mm:ss, LogController FMT :36): …')` |

### Web: Ver logs y Cambiar contraseña (T10-T12)

| Regla (inventario §) | Test |
|---|---|
| Logs §1: cabecera (logo 46 px, "Log de actividad" 18 px, subtítulo 12 px, separador) | `LogsPage.test.tsx` › `it('cabecera, barra de filtros en su orden, pie y carga inicial con limite=1000 y sin filtros')` |
| Logs §1: barra en orden "Buscar..." 220 · "Acción..." 150 · "Técnico..." 150 · Desde/Hasta · "Limpiar filtros" (con `flex-wrap`, §17.9) | ídem |
| Logs §1: pie "Actualizar" / "Cerrar" (12 px azul gris) | ídem; `it('"Actualizar" recarga con los filtros actuales y el buscador se conserva')` |
| Logs §3 + G3: carga inicial sin filtros con `limite=1000` | `LogsPage.test.tsx` › cabecera (peticiones `[{ limite: '1000' }]`); `api.test.tsx` › `it('sin filtros solo manda el límite; cambiar un filtro cambia la clave y relanza la carga')` |
| Logs §3: parámetros `accion`, `tecnico`, `desde`, `hasta` del contrato | `api.test.tsx` › `it('manda limite, accion, tecnico, desde y hasta con los nombres del contrato')`; `filtros.test.ts` › `describe('queryLogs …')` (3) |
| Logs §3: error de carga "Error al cargar los logs: " + mensaje; la tabla conserva los datos | `LogsPage.test.tsx` › `it('un fallo al cambiar un filtro avisa …')`, `it('un fallo de "Actualizar" avisa con el mensaje del servidor …')` |
| Logs §3 / spec §6.2: 401 y conexión siguen la política global | `LogsPage.test.tsx` › `it('sin conexión: solo el banner de la política global …')`; `api.test.tsx` › `it('un error queda en la consulta sin diálogo genérico …')` |
| Logs §3: fallo de la lista de usuarios silencioso (y de acciones, G4) | `LogsPage.test.tsx` › `it('un fallo de las listas de acciones o de usuarios es silencioso …')` |
| Logs §4: columnas Fecha/Usuario/Acción/Detalle con 150/80/180 (Detalle flexible) | `columnas.test.tsx` › `it('Fecha, Usuario, Acción y Detalle con los prefWidth del JavaFX …')` |
| Logs §4 / §11: fecha `dd/MM/yyyy HH:mm:ss` en Madrid, con segundos | `columnas.test.tsx` › `it('fecha "dd/MM/yyyy HH:mm:ss" en hora de Madrid …')`, `it('en invierno la diferencia es de 1 h …')`; `LogsPage.test.tsx` › `it('tabla: Fecha con segundos en Madrid …')` |
| Logs §4: detalle en una línea con elipsis; null → vacío | `columnas.test.tsx` › `it('detalle null: celda vacía; el detalle va en una sola línea con elipsis')` |
| Logs §4: placeholder "No hay contenido en la tabla" | `LogsPage.test.tsx` › `it('sin filas: "No hay contenido en la tabla" y sin aviso de tope')` |
| Logs §4 / §17.7 (G10): sin ordenación por cabecera, orden del servidor | `LogsPage.test.tsx` › `it('tabla: … sin ordenación por cabecera')` |
| Logs §5: buscador en memoria sobre usuario/acción/detalle, sin mayúsculas, con trim; sin motivo ni fecha | `filtros.test.ts` › `describe('coincideTexto: lo que no mira …')` (2), `describe('aplicarBuscador')` (2); `LogsPage.test.tsx` › `it('filtra por usuario, acción o detalle sin pedir otra vez; no mira el motivo')` |
| Logs §5: el buscador se conserva al recargar | `LogsPage.test.tsx` › `it('"Actualizar" recarga con los filtros actuales y el buscador se conserva')` |
| Logs §5: "Acción..." elegir filtra; vaciar quita; teclear otra cosa no quita (estado incoherente calcado) | `LogsPage.test.tsx` › `it('"Acción...": opciones de /api/logs/acciones; elegir filtra; …')` |
| Logs §5 / §3: "Técnico..." con `nombreUsuario` en orden natural (`sorted()`) | `LogsPage.test.tsx` › `it('"Técnico...": nombres de usuario en orden natural de cadena; …')` |
| Logs §5: Desde/Hasta `yyyy-MM-dd` | `LogsPage.test.tsx` › `it('"Desde:" y "Hasta:" mandan las fechas yyyy-MM-dd')` |
| Logs §5 (spec §6.2): "Limpiar filtros" vacía los cinco con una sola recarga | `LogsPage.test.tsx` › `it('"Limpiar filtros" vacía los cinco y recarga una sola vez')` |
| G3: aviso con exactamente 1.000 filas | `LogsPage.test.tsx` › `it('con exactamente 1.000 filas avisa bajo la barra; con 999, no')`; `filtros.test.ts` › `it('filtros vacíos, límite de 1.000 filas y textos exactos …')` |
| G4: acciones de `GET /api/logs/acciones` | `api.test.tsx` › `it('lee la lista de acciones del servidor tal cual, …')` |
| Logs §6: doble clic → "Detalle del log" con `detalle ?? ""` + "\n\nMOTIVO: " si hay motivo | `columnas.test.tsx` › `describe('textoDetalle …')` (3); `LogsPage.test.tsx` › `it('abre "Detalle del log" con el motivo tras una línea en blanco, o solo el detalle')` |
| Logs §6 / spec §6.1: "Cerrar" vuelve a `volverA` o a `/reparaciones` | `LogsPage.test.tsx` › `it('vuelve a la vista desde la que se abrió …')`, `it('sin volverA vuelve a /reparaciones')` |
| Logs §9: sin poller ni recarga al foco | `api.test.tsx` › `it('no recarga al volver a la pestaña (sin refresco automático, spec §7)')` |
| Logs §9: filtros vacíos al reabrir | `LogsPage.test.tsx` › `it('vuelve a la vista desde la que se abrió (state.volverA); al reabrir, los filtros están vacíos')` |
| Logs §10: sin CSV ("Descargar CSV" deshabilitado) | `LogsPage.test.tsx` › `it('no registra exportable: "Descargar CSV" queda deshabilitado …')` |
| Logs §16: 9 casos de `LogControllerTest.coincideTexto` | `filtros.test.ts` › `describe('coincideTexto (los 9 casos de LogControllerTest del JavaFX)')`: `texto vacío`, `texto null`, `texto solo espacios`, `usuario ignorando mayúsculas`, `acción ignorando mayúsculas`, `detalle por IMEI`, `una parte del detalle ("ID_REP")`, `"no_existe" no coincide`, `campos null no lanzan y no coinciden` |
| Password §1.1: barra navy 15 px, cuerpo blanco padding 24/gap 16, 380 px, etiquetas 11 px `#555`, placeholders (con "Confirmar contraseña"), botones | `CambiarPasswordDialog.test.tsx` › `it('barra navy con el título, tres campos con sus etiquetas y placeholders, botones y 380 px')` |
| Password §1.1: ojo independiente por campo | `CambiarPasswordDialog.test.tsx` › `it('cada campo tiene su ojo independiente')` |
| Password §1.1: foco inicial en la actual | `CambiarPasswordDialog.test.tsx` › `it('el foco inicial está en la contraseña actual')` |
| Password §1.1: error `#CC0000` 12 px oculto hasta que hay error | `CambiarPasswordDialog.test.tsx` › estructura y `describe('… validación en la línea de error')` |
| Password §7.1: vacíos → corta → no coinciden, sin trim, textos exactos | `validacion.test.ts` (10); `CambiarPasswordDialog.test.tsx` › `it.each(… sin llamar al servidor)` (3) |
| Password §3: `PATCH` con `{passwordActual, passwordNueva}`, la confirmación no viaja | `api.test.tsx` (cuenta) › `it('manda {passwordActual, passwordNueva} y un 204 es éxito')`; `CambiarPasswordDialog.test.tsx` › `it('éxito: manda actual y nueva (sin la confirmación), …')` |
| Password §2 / §6.2: éxito → cierra y `mostrarAviso("Información", "Contraseña cambiada correctamente.")`; la sesión no cambia | `CambiarPasswordDialog.test.tsx` › `it('éxito: …; la sesión sigue')`; `TopBar.test.tsx` › `it('guardar con éxito cierra el diálogo, avisa y deja al usuario en la misma vista')` |
| Password §6.2: "Guardar" deshabilitado durante la llamada | `CambiarPasswordDialog.test.tsx` › `it('"Guardar" y "Cancelar" quedan deshabilitados mientras responde y Esc no cierra')` |
| Password §6.2 / §6.3: 422 → `message` en la línea, campos intactos | `CambiarPasswordDialog.test.tsx` › `it('422: el message del servidor en la línea, …')` |
| Password §6.2: cada guardar oculta primero el error anterior | `CambiarPasswordDialog.test.tsx` › `it('un nuevo intento limpia el error anterior antes de validar')` |
| Password §6.3: 403 → texto de la web; 5xx/red → política global | `CambiarPasswordDialog.test.tsx` › `it('otro error (403): …')`, `it('sin conexión: sin texto en la línea; …')` |
| Password §6.2: "Cancelar" cierra sin preguntar | `CambiarPasswordDialog.test.tsx` › `it('"Cancelar" cierra sin preguntar aunque haya texto, …')` |
| Password §17.7: Enter guarda y Esc cierra (diferencia aceptada) | `CambiarPasswordDialog.test.tsx` › `it('Enter en un campo guarda')`, `it('Esc cierra')` |
| Password §2: ventana nueva en cada apertura | `CambiarPasswordDialog.test.tsx` › `it('al reabrir, los campos y el error vuelven vacíos …')` |
| Password §1.3 / §8 / §17.1 (G6): "Cambiar contraseña" para los tres roles abre un modal sobre la vista sin navegar | `TopBar.test.tsx` › `it.each(…)('%s: abre el diálogo sobre la vista actual, sin navegar')` (3) |
| Password §2: al cerrar, se vuelve a la vista (el menú sigue operativo) | `TopBar.test.tsx` › `it('"Cancelar" lo cierra y el menú se puede volver a abrir (sin pointer-events colgados)')` |

### Web: CSV de Asignaciones, smoke y cierre (T0, T13-T15)

| Regla (inventario §) | Test |
|---|---|
| CSV de Asignaciones: fichero `reparaciones_pendientes` y 14 cabeceras en orden (hotfix `ReparacionControllerSuperTecnico.exportarCSV :1170-1186`; spec §6.5) | `src/modules/taller/asignaciones/csv.test.ts` › `it('nombre del fichero y las 14 cabeceras exactas en orden')` |
| CSV: fila completa (IMEI `="…"`, modelo traducido, fecha `dd/MM/yyyy HH:mm` en Madrid, "Sí" en urgente/chasis/por cerrar, `textoCsvEntrega`) (hotfix `filaAsignacion :1242-1257`) | `csv.test.ts` › `it('fila completa de una reparación: todos los campos, fecha en hora de Madrid y "Sí" en las marcas')` y `it('fecha de asignación en invierno: UTC+1')` |
| CSV: tipo por prefijo `A`/`AG`/`AP` (`TipoTrabajo.desde(...).etiqueta`, `:1245`) | `csv.test.ts` › `it('tipo por prefijo: A → Reparación, AG → Glass, AP → Pulido')` |
| CSV: técnico, modelo, comentario y cliente nulos → `''`; "Asignado por" nulo → "—" (`:1246-1253`) | `csv.test.ts` › `it('nulos: técnico, modelo, comentario y cliente vacíos; "Asignado por" nulo es "—"')` |
| CSV: "No" en las marcas a false (`:1254-1256`) | `csv.test.ts` › `it('marcas a false son "No"')` |
| CSV: "Entregado" A derivada / AG real / pulido vacío (`EntregaGlass.textoCsv`, `:1257`) | `csv.test.ts` › `it('Entregado: fecha de la entrega en A y AG, vacío sin entrega y en pulidos')` |
| CSV: "En espera de pieza" = `esSolicitud > 0 && !(GESTIONADA && stockSolicitud > 0)` (`:1259-1263`) | `csv.test.ts` › `it('en espera de pieza: sin solicitud No; solicitud sin gestionar Sí; gestionada sin stock Sí; gestionada con stock No')` |
| "Descargar CSV" delega en la vista activa y exporta lo visible (inventario-password §6.1; hotfix `getItemsVisibles`, `:1177`) | `AsignacionesPage.test.tsx` › `it('"Descargar CSV" exporta reparaciones_pendientes con las 14 columnas y solo las filas visibles')` |
| Menú de ADMIN: "Gestionar técnicos" y "Ver logs" (inventario-password §1.3) | `tests/e2e/gestion.spec.ts` › `test('admin: técnico de prueba registrado, bloqueado y desbloqueado, visto en el log, con contraseña cambiada y borrado')` |
| Registrar: cuerpo con nombres y rol TECNICO por defecto, 201, formulario vacío sin mensaje de éxito, fila nueva con badge "Activo" (inventario-tecnicos §6.1, §4) | `gestion.spec.ts` › mismo test, step `'alta desde "Gestionar técnicos"'` y `'fila nueva, desactivar y activar con el candado'` |
| Activar/desactivar sin confirmación, tooltips "Desactivar acceso"/"Activar acceso", badge "Inactivo"/"Activo" (inventario-tecnicos §6.2) | `gestion.spec.ts` › step `'fila nueva, desactivar y activar con el candado'` |
| "Cerrar" vuelve a la vista de origen (inventario-tecnicos §6.4; spec §6.1 `volverA`) | `gestion.spec.ts` › step `'fila nueva, desactivar y activar con el candado'` (final) |
| Filtro de acción por igualdad en el servidor y fechas del día en Madrid, con `limite=1000` (inventario-logs §5, §11; spec G3, G5) | `gestion.spec.ts` › step `'CREAR_USUARIO en "Ver logs" filtrando por acción y por el día de hoy'` |
| Formato del detalle `NOMBRE_USUARIO: …, ROL: …, TECNICO: …` y autor en la columna Usuario (inventario-logs §12.3, §4) | `gestion.spec.ts` › mismo step |
| Buscador en memoria sobre el detalle; doble clic → "Detalle del log" (inventario-logs §5, §6) | `gestion.spec.ts` › mismo step |
| "Cambiar contraseña" desde el menú sin navegar; guardar → aviso "Contraseña cambiada correctamente." y la sesión sigue; la contraseña nueva vale para entrar (inventario-password §6.1, §6.2) | `gestion.spec.ts` › step `'el usuario de prueba cambia su contraseña desde el menú y vuelve a entrar con la nueva'` |
| Eliminar: `tiene-reparaciones` → confirmación "Eliminar técnico" con `¿Eliminar a "{n}" definitivamente?` → `DELETE ?idUsu=` 204 → fila fuera (inventario-tecnicos §6.3) | `gestion.spec.ts` › step `'el ADMIN lo borra desde la papelera'` |

## Autorrevisión del plan

Contrastado contra la spec §1-§12 y el código real; **el bloque servidor (T1-T5) sí se ejecutó** (el redactor aplicó las cinco tareas en un worktree desechable de `main` 3dccc4a con `mvn -q test`: 421 → 485), **la web (T6-T15) no está compilada ni ejecutada**. Cada bloque lo redactó un subagente distinto sobre un documento de interfaces común; estos son los puntos cruzados que se reconciliaron al ensamblar (las notas originales de cada bloque están al final, en "Desviaciones respecto al reparto inicial de interfaces"):

1. **Contrato sin respuesta 200 en `registrarTecnico` (201/409/422) y `cambiarPassword` (204/422)** (desviación 4 del servidor): las mutaciones de la web (`useRegistrar`, T8; `useCambiarPassword`, T12) hacen `await api.POST/PATCH(...)` sin leer `data`, así que el tipo generado no las afecta; los mocks MSW de sus tests responden 201/204 sin cuerpo.
2. **`silenciarError` en `useUsuariosTecnicos`:** lo fija la Task 7 (junto a `refetchOnWindowFocus: false`); la nota condicional del Step 4 de la Task 11 se ha dejado en una comprobación por `grep`, sin cambios de código.
3. **`UserMenu.tsx`:** la Task 7 añade `volverA` a los dos ítems de ADMIN y deja "Cambiar contraseña" con `onSelect` vacío (con test de que no navega); la Task 12 da el fichero completo, que es un superconjunto exacto de lo que deja la Task 7 (import, `useState` y el `onSelect` que abre el diálogo), y sustituye ese test por el de apertura.
4. **404 "Técnico no encontrado.":** `clasificar` (`errors.ts:52-53`) descarta el `message` de cualquier 404, así que la web no puede leerlo; la Task 8 lo fija como constante `MSG_TECNICO_NO_ENCONTRADO` en `tecnicos/textos.ts` con el helper `mensajeInline` de `tecnicos/errores.ts`. Es el mismo texto que `ValidacionUsuarios.MSG_NO_ENCONTRADO` (T1-T2): si uno cambia, cambia el otro. También aplica al 404 del `GET tiene-reparaciones` (regla G9), que la spec §6.1 dejaba con texto fijo: prevalece G9 (`message` del servidor).
5. **Nombres accesibles que supone el smoke (T14) contra lo que producen T6-T12:** candado `aria-label`/`title` "Desactivar acceso" / "Activar acceso" (T8), papelera `aria-label="Eliminar"` (T8; el smoke la localiza por la imagen `borrar.png`, que también vale), `ConfirmDialog` "Eliminar técnico" con botón "Eliminar" (T9), placeholders exactos de los cuatro campos del alta (T9) y de los tres del diálogo (T12), `dialog` "Cambiar contraseña" (`DialogTitle`, T12), aviso "Información" (T12), `CampoAutocompletar` con `role="option"` (existente), `RangoFechas` con etiquetas "Desde:"/"Hasta:" (existente). Coinciden.
6. **Anchos de columna con sobrante:** la web deja el sobrante en la columna de relleno de `DataTable`, no en la última como el JavaFX (`FLEX_LAST_COLUMN`): acciones de técnicos a 80 px (T8) y Detalle de logs con `size: 400` y `ajuste="estirar"` (T10-T11). Diferencia a revisar en la comparación de capturas (U6); si se corrige, es un ajuste de `DataTable`, no de las páginas. La columna de acciones de técnicos a 80 px (la spec §6.1 dice que absorbe el resto) queda como diferencia a decidir con la captura `gestion-tecnicos-tabla`, y la ficha `tecnicos.md` (Task 15) la lista en "Pendiente de decidir".
7. **Diálogo de contraseña (T12):** mientras responde el servidor se deshabilitan "Guardar" y "Cancelar" y se ignora Esc; un 401 o un fallo de conexión no se pintan en la línea roja (los avisa la política global: login o banner), a diferencia del JavaFX. Van a la ficha `cuenta.md` (T15) como diferencias inocuas.
8. **Logs sin conexión (T11):** solo banner, sin "Error al cargar los logs: …" ni diálogo de conexión (spec §6.2); la tabla conserva las últimas filas buenas cuando falla un cambio de filtro (estado ajustado durante el render, patrón de `CampoAutocompletar`), y "Limpiar filtros" remonta los dos autocompletar con `key`.
9. **Task 0 (U0):** `PATCH /api/componentes/{idCom}/stock` recibe un incremento (`{delta}`), "cerrar sin resto" es `confirmar-alterado`, `DELETE /api/proveedores/{id}` da 409 con pedidos aunque estén cancelados (se desactiva en su lugar). Cada escritura con OK del usuario, una a una; los ids y nombres reales viven solo en `Apuntes`.
10. **Recuentos:** servidor 421 → 485 (ejecutado); web 1333 → ~1488 orientativo (T6 +6, T7 +12, T8 +23, T9 +25, T10 +29, T11 +18, T12 +33, T13 +9) y el smoke pasa de 8 a 9 tests en `--list`. Se comprueban en ejecución.
11. **Supuesto de esquema (T2):** `tieneReferencias` consulta `Revision`, `Envio`, `Envio_Telefono` y `Movimiento_telefono`, que existen en `sql/crear_bd.sql` y en las migraciones F2; antes de desplegar (U3) el usuario comprueba con `SHOW TABLES` en la BD de producción que las cuatro existen (si faltara alguna, `tiene-reparaciones` y el `DELETE` darían 500).
12. **Orden de ejecución:** T0 no se ejecuta (decisión del usuario 2026-09-26) → T1-T5 servidor → T6-T13 web → T14 smoke (se escribe; se ejecuta en U4 contra producción con la web en local) → T15 cierre; U1-U13 en el orden del 4b (capturas comparadas antes del merge de la web).
13. **Puntos a vigilar en ejecución** (marcados en cada tarea): `tailwind-merge` y el `pr-11` de `CampoPassword` en el login (T6, desviación 7 de B); la regla `react-hooks` con el estado ajustado durante el render (T11); `any(Object[].class)` en los `verify(...never())` de Mockito 5 (T1-T4); el límite de 5 inicios de sesión por minuto en el smoke (T14: cuatro seguidos caben, la limpieza espera 12 s); los recuentos de surefire solo tras una suite completa.

**Revisión previa antes de la Task 1** (lección del 4a): el servidor ya está aplicado y ejecutado por su redactor; dos subagentes aplican el código de la web en copias (T6-T9; T6-T13 con foco en T10-T13 y el `--list` de T14), ejecutan `npm run check` y devuelven los desajustes; se corrige el plan antes de despachar nada.

## Revisión previa (2026-09-26)

Servidor: las Tasks 1-5 las aplicó y ejecutó su redactor en un worktree desechable de `main` 3dccc4a (421 → 485 tests). Web: dos subagentes aplicaron el código del plan en copias desde `cd6853b` (W1, Tasks 6-9 → 1399 tests; W2, Tasks 6-13 → 1488 tests, y `npx playwright test --list` de la Task 14 con 9 tests en 7 ficheros, sin ejecutar); `npm run lint`, `npx tsc -b` y `npm run build` limpios tras las correcciones de abajo. El contrato (`schema.d.ts`) se editó a mano en las copias porque la rama del servidor con las Tasks 1-5 no existía (`api/openapi.json` sin tocar). Con varios procesos node en paralelo hubo 3-4 timeouts intermitentes de 5 s en ficheros ajenos al plan (`StockPage`, `FormularioReparacion`, `NuevoOtroPedidoDialog`), que pasan relanzados aislados.

Decisiones del controlador (rutinarias; el usuario puede cambiarlas): 1. `.gitignore` `/logs` (no renombrar el módulo). 2. `forEach` en los dos tests, sin tocar `tsconfig`. 3. Handler de contadores en los tests del menú (siguen montados en `/reparaciones`). 4. Columna de acciones a 80 px → diferencia a decidir con la captura.

Correcciones aplicadas:
1. Task 10, Files + Step 15 (y tabla "Estructura de ficheros"): `.gitignore:2` `logs` → `/logs` con `grep`/`sed`/`git check-ignore` y `.gitignore` en el `git add` (sin esto Git ignora `src/modules/gestion/logs/` y el `git add` falla).
2. Task 10, Step 10 (`logs/api.test.tsx`) y Task 11, Step 1 (`LogsPage.test.tsx`, `registrarLogs`): `searchParams.forEach` sobre un `Record<string, string>` / `Peticion` en vez de `Object.fromEntries` (TS2769 sin DOM.Iterable); en `api.test.tsx`, `initialProps: { q: { limite: 1000 } as QueryLogs }` (TS2353).
3. Task 11, Step 1, test "sin conexión: solo el banner…": `/api/logs/acciones` y `/api/usuarios/tecnicos` responden 500 antes de montar (precedente `ClientesPage.test.tsx:148`).
4. Task 12, Step 9 (`CambiarPasswordDialog.test.tsx`, "sin conexión"): la aserción mira el `form` por el DOM (`closest('form')?.querySelector('[role="alert"]')` → `null`), porque el diálogo de error deja el de contraseña `aria-hidden`.
5. Task 12, Step 13: el import de `react-router` de `TopBar.test.tsx` conserva `useLocation` (`import { Route, useLocation } from 'react-router'`).
6. Task 12, Step 13: `beforeEach` con el handler de `/api/reparaciones/pendientes/contadores` en el `describe` nuevo y `beforeEach` en el import de `vitest`.
7. Task 12, Step 13 (y aviso en la Task 7, Step 7): la última aserción del caso de la Task 7 "Cambiar contraseña ya no navega…" pasa a `expect(await screen.findByRole('dialog', { name: 'Cambiar contraseña' })).toBeInTheDocument()`.
8. Task 12, Step 14 (`UserMenu.tsx`): `const { pathname } = useLocation()` y `volverA: pathname`, como la Task 7.
9. Task 14, Files + Step 2: `.env.e2e.example` tiene 22 líneas; el bloque va al final (tras la l.22, no la `:26`).
10. Task 14, `gestion.spec.ts`: `entrarCon` termina con `await expect(page).toHaveURL(/\/reparaciones\/(historial|pendientes)$/)` (comentario: `origen` y `ruta` leen `page.url()` tras la redirección).
11. Task 7, Step 5 (`api.test.tsx`): `refetchOnWindowFocus` se lee de `observers[0]?.options` (TS2339 con `Query.options`).
12. Task 7, Steps 7-8 y Task 9, Files + Step 3: anclas por texto (caso `'el admin ve además "Gestionar técnicos" y "Ver logs"'`; las tres rutas `PendienteDeMigrar`; import de `TecnicosPage` tras el de `RequiereAdmin`).
13. Task 8, Step 10: `npx vitest run src/modules/gestion` → 46 tests en 8 ficheros (32 nuevos + 14 de `clientes/`), acumulado 1374.
14. Task 6, "Búsqueda previa": lista completa de `ls public`.
15. Autorrevisión, punto 6, ficha `tecnicos.md` ("Pendiente de decidir") y comparación de capturas de la Task 15: columna de acciones a 80 px como diferencia a decidir con `gestion-tecnicos-tabla`.

## Desviaciones respecto al reparto inicial de interfaces

Cada bloque del plan lo redactó un subagente contra el código real a partir de un documento de interfaces común (fuera del repo). Lo que no cuadró con el código se adaptó y se anotó aquí; la "Autorrevisión del plan" de arriba dice cómo queda cada punto cruzado.

### Bloque servidor (T1-T5)

1. **`LogDAO.LIMITE_MAX` es `public`** (INTERFACES lo daba package-private): `LogController` está en `controller` y `LogDAO` en `dao`. `MADRID` e `inicioDiaUtc` sí quedan package-private (los usa el test del mismo paquete).
2. **`RolesUsuarioTecnicoTest` usa tokens JWT reales**, no `@WithMockUser`: es el patrón exacto de `RolesCompraLoteTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `JwtUtil.generateToken`), que es el que manda la spec. Con ADMIN se comprueba 201/204 (no 404: `TecnicoDAO` va mockeado).
3. **La guarda de `GET /api/logs/acciones` se prueba por reflexión** (`LogControllerTest.accionesEsSoloAdmin`) para no crear un sexto test `Roles*` fuera de la lista de nombres fijos.
4. **`tieneReferencias` = nueve consultas `SELECT EXISTS(…)`**, una por referencia y parando en la primera (INTERFACES dejaba elegir): cada test fija el SQL de su referencia; justificación en la Task 2. Las listas `REFERENCIAS_TECNICO` (3) y `REFERENCIAS_USUARIO` (6) son package-private para el test. Un técnico sin fila en `Usuario` solo se mira por sus tres columnas de `Reparacion`, y en el `DELETE` da 404 (INTERFACES: "si null → 404 también").
5. **Contrato: se documentan códigos reales con `@ApiResponses`** en el alta (201/409/422), el borrado (204/404/409), activar/desactivar (204/404) y cambiar contraseña (204/422). Consecuencia para la web (Task 6 en adelante): en `schema.d.ts`, `registrarTecnico` y `cambiarPassword` **dejan de tener respuesta 200** (`Record<string, never>`); las mutaciones no deben leer `data`. `tiene-reparaciones` y `GET /api/logs` no se anotan (habría que redescribir el esquema del 200); sus 404/422 llegan igual con `{message}`.
6. **`idUsu` del `DELETE` lleva descripción** "Ignorado: el servidor lo resuelve desde idTec" en el contrato (aditivo).
7. **Contraseña "vacía" = `isEmpty()` sin trim**, en el alta y en cambiar contraseña: la spec §4.1 dice "vacíos tras el trim" en la misma frase que los nombres, pero el cliente (`RegisterController :262-265`, `CambiarPasswordController :84-87`) no recorta contraseñas y la §6.3 dice "sin trim". Se calca el cliente.
8. **El log `CREAR_USUARIO` sale del `try`**: solo el `INSERT` puede producir el 409 de integridad. Sin cambio observable.
9. **Recuento**: 421 → **485** (+64: T1 13, T2 24, T3 10, T4 16, T5 1), frente a los ~470 estimados en INTERFACES. Código y cifras verificados ejecutando las cinco tareas en un worktree desechable de `main` 3dccc4a (ya eliminado).
10. **Supuesto a comprobar antes de desplegar**: las tablas `Revision`, `Envio`, `Envio_Telefono` y `Movimiento_telefono` tienen que existir en las dos BD donde corre el servidor; si falta alguna, `tiene-reparaciones` y el `DELETE` darían 500 (Task 2, Supuestos).
11. **Mockito 5 y las listas variables vacías**: `verify(jdbc).query(captor, any(RowMapper.class))` casa con la sobrecarga sin varargs; para la llamada sin parámetros el test usa `any(Object[].class)` (nota en la Task 4).

### Bloque web: cimientos y Gestionar técnicos (T6-T9)

1. **404 con el texto del servidor.** `clasificar` (`errors.ts:52-53`) cambia el `message` de cualquier 404 por "Recurso no encontrado.", así que la web no ve el "Técnico no encontrado." del servidor. No se toca `clasificar` (cambiaría todos los 404 de la web). En su lugar, `textos.ts` añade `MSG_TECNICO_NO_ENCONTRADO = 'Técnico no encontrado.'` y un helper nuevo, `errores.ts` › `mensajeInline(e, fijo, conMensaje)`, lo usa para los 404. Si la Task 1-2 del servidor cambia ese texto, hay que cambiar también esta constante.
2. **Añadidos a INTERFACES.md** (ningún nombre cambia): `rolDe(valor)` y `type CuerpoAlta` en `validacion.ts`, porque `ComboNavy` devuelve un `string` y `CuerpoAlta` es el mismo tipo que `ReturnType<typeof cuerpoAlta>`. También `errores.ts` (`mensajeInline`, `CodigoConMensaje`), `ANCHOS_TECNICOS` en `columnas.tsx`, `RUTA_VOLVER_POR_DEFECTO` en `navegacion.ts` y un prop opcional `id` en `CampoPassword`, para que el diálogo de la Task 12 pueda enlazar sus etiquetas con `htmlFor`.
3. **`rutaVolverA` es más estricta que en INTERFACES:** solo acepta cadenas que empiecen por `/` y no por `//`, porque el state viene del historial del navegador. En los demás casos devuelve `/reparaciones`.
4. **Comprobación de la papelera con 404:** la spec §6.1 pone texto fijo para el `GET`, pero G9 dice que se muestre el `message` en 404. Se ha seguido G9: 404 → "Técnico no encontrado."; en los demás casos, "Error al comprobar las reparaciones del técnico.".
5. **Columna de acciones:** en el JavaFX, la última columna absorbe el ancho que sobra (`FLEX_LAST_COLUMN`). `DataTable` en modo `'fijo'` deja ese sobrante en su columna de relleno, así que las acciones miden 80 px y los iconos se centran ahí. Hay que revisarlo al comparar capturas (Task 15).
6. **`useUsuariosTecnicos` siempre con `meta: { silenciarError: true }` y `refetchOnWindowFocus: false`.** La página de técnicos pinta el error inline y el filtro de logs (Task 11) lo calla, igual que el JavaFX. Así la Task 11 no necesita ninguna opción extra.
7. **Hueco del ojo en el login.** Hoy `LoginPage` pasa `pr-11` antes del `px-3.5` de `inputCls`, y `tailwind-merge` lo descarta, así que el texto queda bajo el ojo. `CampoPassword` aplica `pr-11` al final y lo arregla (lo comprueba el test del ojo del login). Es un cambio visual mínimo, a favor del calco (padding derecho 44 del FXML).
8. **"Registrar técnico" también se deshabilita mientras el POST responde** (`registrar.isPending`), para evitar un doble alta. El JavaFX es síncrono y no lo necesita. Con duplicados en vivo se deshabilita igual que en el JavaFX.
9. **"Cambiar contraseña" queda sin acción entre la Task 7 y la Task 12** (`onSelect` vacío con comentario). El test nuevo de `TopBar.test.tsx` comprueba que no navega; la Task 12 debe cambiarlo para comprobar que abre `CambiarPasswordDialog`.
10. **Recuentos:** se parte de 1333 tests (v0.7.0) y del orden T6 → T9 con la web en `main` cd6853b. Si otro bloque añade tests antes, los acumulados (1339 / 1351 / 1374 / 1399) se desplazan en la misma cantidad.

### Bloque web: Ver logs y Cambiar contraseña (T10-T12)

1. `coincideTexto(log, texto: string | null | undefined)` en vez de `texto: string` (INTERFACES): hace falta para portar el caso `null` de `LogControllerTest`. Es un supertipo: quien llame con `string` no cambia.
2. `COLUMNAS_LOGS`: Detalle lleva `size: 400` (su prefWidth) en vez de "sin size fijo". En `DataTable` una columna sin `size` mide 150 fijos (`DataTable.tsx:71, :165`). La página usa `ajuste="estirar"`, como Asignaciones e Historial: reparte el ancho en proporción a los `size`, así que Detalle se lleva la mayor parte del sobrante sin absorberlo todo, como sí hacía el `FLEX_LAST_COLUMN` del JavaFX. Se anota en la ficha `logs.md` (Task 15).
3. Exportaciones de más, sin cambiar ninguna de INTERFACES: `CLAVE_ACCIONES_LOG` (`logs/api.ts`); `TITULO_EXITO`, `MSG_EXITO` (`cuenta/validacion.ts`); `CuerpoCambiarPassword` (`cuenta/api.ts`). Además, `useLogs`/`useAccionesLog` ponen `refetchOnReconnect: false`, además de `refetchOnWindowFocus: false`, para no refrescar solos (spec §7).
4. Sin conexión, en Logs solo sale el banner: la consulta va silenciada y la página solo avisa de lo que no es `ConexionError` ni `SesionExpiradaError`. Así no aparece el diálogo "Sin conexión…" del primer fallo que sí saca `QueryCache`, ni el "Error al cargar los logs: Sin conexión…" del JavaFX. Es lo que pide la spec §6.2 ("salvo 401 y conexión, que siguen la política global (banner y login)").
5. La tabla de Logs conserva los datos sin `placeholderData`: guarda las últimas filas buenas con "ajustar el estado durante el render", porque con otra clave `data` pasa a `undefined` si la carga falla. El aviso de error se protege con `isFetching` para no repetir el error que una clave fallida trae de la caché. Los `CampoAutocompletar` se remontan con `key` al limpiar, porque no vuelven a `''` solos. Llevan `aria-label` "Acción"/"Técnico" y `placeholder` "Acción..."/"Técnico...".
6. Si la Task 7 no puso `meta: { silenciarError: true }` en `useUsuariosTecnicos`, lo añade la Task 11 (Step 4). La spec exige que el filtro "Técnico..." falle en silencio.
7. En el diálogo de contraseña, un `ConexionError` no se pinta en la línea roja: ya lo avisa el diálogo global del `MutationCache`, y así no sale dos veces. El JavaFX lo pintaba en la línea. El 401 tampoco sale en la línea: va al flujo de sesión caducada. Diferencia para la ficha `cuenta.md`.
8. Mientras responde el servidor, el diálogo deshabilita también "Cancelar" e ignora Esc (el JavaFX se congelaba; `DialogoAlmacen` hace lo mismo con `enviando`). `useErrorServidor` no se usa porque vive en `modules/almacen/ui`: el diálogo lleva su propio estado de error en un hijo de `DialogContent`, que se desmonta al cerrar y así se reinicia en cada apertura.
9. La Task 12 da `UserMenu.tsx` entero, con el estado que se espera tras la Task 7 (`useLocation` + `volverA`). Si la Task 7 lo dejó distinto, solo se aplican las tres piezas nuevas: import, `useState` y `onSelect` + `<CambiarPasswordDialog>`.

### Bloque web: arranque, CSV de Asignaciones, smoke y cierre (T0, T13-T15)

1. **`PATCH /api/componentes/{idCom}/stock` recibe un incremento** (`ComponenteDeltaRequest { delta }`, `schema.d.ts:2731-2734`; `ComponenteController.java:107-111`), no el stock final: la Task 0 calcula `delta = esperado − actual` y lo presenta así.
2. **"Cerrar sin resto" es `PATCH …/confirmar-alterado`** (`PedidosPage.tsx:48`): la Task 0 lo usa para el parcial antes de desrecibirlo. El `DELETE` de un pedido responde **200** (no 204) y el de un proveedor **204** con 409 si tiene pedidos, incluidos los cancelados: por eso la receta mira antes `tiene-pedidos` y desactiva en lugar de borrar.
3. **Task 0 sin ids en el plan**: el guion con ids se arma en el chat a partir de los apuntes privados; lo que no es un pedido, un proveedor ni el stock del SKU de prueba (p. ej. la fila guardada por un smoke en una asignación, recogida en la casilla del 4a) queda "sin receta" y se decide con el usuario, sin inventar rutas.
4. **Logs del smoke y de las capturas**: además de `CREAR_USUARIO`/`ELIMINAR_USUARIO`, quedan `ACTIVAR_USUARIO`, `DESACTIVAR_USUARIO` y los `LOGIN` del ADMIN; los `CAMBIAR_PASSWORD` (y `LOGIN`) del usuario de prueba **no quedan** si se borra, porque el `DELETE` borra su `Log_Actividad` (G8). U12 lo anota así, en lugar de listar `CAMBIAR_PASSWORD` como log que limpiar.
5. **Smoke, papelera por imagen**: la spec no le da tooltip ni INTERFACES fija su nombre accesible (`BotonPapelera` vive en `taller` y `gestion` no puede importarlo), así que el test la localiza con `button:has(img[src="/borrar.png"])`. "Cerrar" se busca por texto exacto (puede ser botón o enlace). Los demás nombres accesibles del test son supuestos contra T6-T12, listados en "Supuestos" de la Task 14.
6. **Smoke, `afterAll` con `setTimeout` de 12 s** (no `waitForTimeout`: en `afterAll` no hay página) antes del quinto login, solo si el borrado por la interfaz no llegó a hacerse; el test en sí hace cuatro inicios de sesión sin esperas (ráfaga de 3 + 1 de nginx). Además del filtro por acción y fechas, comprueba el autor en la celda Usuario (el filtro "Técnico..." no lista ADMIN), el buscador y el doble clic, y que "Cerrar" vuelve al origen.
7. **Capturas de menú duplicadas en los inventarios** (`gestion-tecnicos-menu`, `gestion-logs-menu-admin` y `gestion-menu-usuario-admin`; ídem para TECNICO y SUPERTECNICO): `CAPTURAS-6.md` y las fichas usan un solo nombre canónico `gestion-menu-usuario-{admin,tecnico,supertecnico}`. Se añade `asig-csv.png` (captura nueva para la Task 13, que no estaba en ningún §18). Las que exigen parar el servidor (`gestion-logs-error`, `gestion-password-sin-conexion`) se marcan "no reproducible" en producción.
8. **"Descargar CSV" en el diálogo de contraseña**: la spec §6.4 dice "deshabilitado en las tres pantallas"; el diálogo se abre sobre la vista actual, así que la línea de `shell.md` dice que las tres pantallas no registran exportable y que el ítem queda deshabilitado en `/gestion/tecnicos` y `/gestion/logs` (con el diálogo abierto el menú no es accesible).
9. **`.env.e2e.example` no tenía `ADMIN_USER`/`ADMIN_PASS`** (sí `~/.env.e2e`): la Task 14 los añade con su comentario.
10. **Verificación del cliente JavaFX**: solo `git status --short gestion-reparaciones-cliente` vacío (como pide el encargo), sin `mvn test` del cliente, que el 4b sí lanzaba; el recuento de la web se expresa como 1333 + los recuentos de T6-T13 porque los de T6-T12 los fija otro bloque.
