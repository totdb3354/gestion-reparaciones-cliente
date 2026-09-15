# Web Cimientos (sub-proyecto 0) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar montada la base de la app web (repo, stack, shell, login, sesión, API tipada, CI, VM de producción) y demostrarla con la vista Clientes funcionando de punta a punta con paridad verificada.

**Architecture:** SPA React servida como ficheros estáticos por el nginx de la VM de producción, misma origin que la API Spring Boot (`/api/`). El servidor publica su contrato con springdoc y la web genera tipos TypeScript de él. Código por módulos (`app`, `modules/{taller,almacen,gestion}`, `shared`), tokens de color como variables CSS, componentes shadcn copiados al repo.

**Tech Stack:** React 19, TypeScript 5 strict, Vite, React Router 7, TanStack Query 5, TanStack Table 8, Tailwind 4, shadcn/ui, openapi-fetch + openapi-typescript, Vitest + Testing Library + MSW, Playwright, springdoc-openapi 2.6, Docker (nginx:alpine, node:24-alpine), certbot.

Spec: `docs/superpowers/specs/2026-09-13-web-cimientos-design.md`. Spec maestra: `docs/superpowers/specs/2026-09-13-migracion-web-programa-design.md`.

## Global Constraints

- Repos: web = `C:\Users\dev\Documents\ProgramaReparaciones\gestion-reparaciones-web` (clonado, gitlink en el raíz); servidor = `...\gestion-reparaciones-servidor`; raíz = `...\ProgramaReparaciones`. Rama de trabajo en web y servidor: `feature/web-cimientos` (crear desde `main` en el servidor; en la web desde `main`, que solo tiene el README).
- Git: commits sin `Co-Authored-By`; **nunca `git push`, merge ni tag sin OK explícito del usuario** (el usuario avisa). Mensajes en español, prefijo `feat(web):`, `chore(web):`, `test(web):`, `docs(web):`, `feat(servidor):`.
- Bash en Windows: para Maven exportar antes `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`. Node local es v26.7 y npm 11 (vale para todo); en CI y Docker se usa Node 24 LTS. No hay Docker local: los ficheros Docker se validan en la VM (Task 10).
- TypeScript `strict: true`. Ningún color escrito a mano en componentes: siempre tokens (`bg-azul-noche`, `text-crema`, ...). Textos de interfaz **idénticos** a los del JavaFX (tildes incluidas).
- Sesión: JWT bearer en `sessionStorage` bajo la clave `fsgr.sesion`. Roles: `ADMIN`, `SUPERTECNICO`, cualquier otro = TECNICO.
- Mapeo de errores HTTP (port de `ApiClient.clasificar`): 401 sesión expirada (una sola vez), 403 "No tienes permisos para realizar esta acción.", 404 "Recurso no encontrado.", 409 conflicto con mensaje del servidor, 422 mensaje del servidor, 5xx/red/timeout conexión + banner. Timeout por petición 15 s. Sin reintentos salvo uno en el login.
- Claude no hace SSH a las VMs: prepara los comandos y el usuario los ejecuta (Task 10). Cada sesión sobre la VM se documenta en `Apuntes/despliegue_vdc.md`.
- Las capturas del JavaFX con datos reales van fuera del repo (público): `Apuntes/paridad-capturas/<vista>/`.

---

## Mapa de ficheros

**Repo web (`gestion-reparaciones-web/`)**

| Fichero | Responsabilidad |
|---|---|
| `package.json`, `vite.config.ts`, `tsconfig*.json`, `eslint.config.js`, `.prettierrc`, `.env.example`, `.gitignore` | Tooling |
| `.github/workflows/ci.yml` | CI: lint + typecheck + tests |
| `src/main.tsx` | Arranque: QueryClient, SessionProvider, AlertaProvider, RouterProvider |
| `src/app/router.tsx` | Árbol de rutas y guard de sesión |
| `src/app/session/storage.ts` | Leer/guardar/borrar sesión en `sessionStorage`; helpers de rol |
| `src/app/session/expiracion.ts` | Hook de sesión expirada (disparo único) |
| `src/app/session/SessionProvider.tsx` | Contexto de sesión: `login`, `logout`, `sesion` |
| `src/app/session/RequireSesion.tsx` | Guard: sin sesión → `/login` |
| `src/app/login/LoginPage.tsx` | Pantalla de login calcada |
| `src/app/shell/AppLayout.tsx` | Barra + banner + `<Outlet/>` + refresco al volver |
| `src/app/shell/TopBar.tsx`, `UserMenu.tsx`, `ConnectionBanner.tsx`, `PendienteDeMigrar.tsx` | Shell |
| `src/app/shell/exportable.tsx` | Contexto "Descargar CSV" (la vista activa registra su exportador) |
| `src/shared/api/schema.d.ts` | Generado por openapi-typescript (se commitea) |
| `src/shared/api/errors.ts` | Clases de error y `clasificar()` |
| `src/shared/api/conexion.ts` | Estado conectado/desconectado (store externo) |
| `src/shared/api/client.ts` | `api` (openapi-fetch) con bearer, timeout y mapeo de errores |
| `src/shared/ui/*` | shadcn (button, dialog, dropdown-menu, input, label, badge, table, checkbox, popover, command, context-menu, tooltip) + propios: `DataTable.tsx`, `MultiSelect.tsx`, `StatusBadge.tsx`, `ConfirmDialog.tsx`, `AlertaProvider.tsx` |
| `src/shared/lib/utils.ts` | `cn()` de shadcn |
| `src/shared/lib/version.ts` | Versión de la app (de `package.json`) |
| `src/shared/styles/tokens.css`, `globals.css` | Tokens y base |
| `src/modules/gestion/clientes/api.ts` | Hooks de query/mutation de Clientes |
| `src/modules/gestion/clientes/ClientesPage.tsx`, `ClienteDialog.tsx` | Vista Clientes |
| `src/test/setup.ts`, `src/test/server.ts`, `src/test/render.tsx` | Infra de tests (MSW, render con providers) |
| `api/openapi.json` | Snapshot del contrato del servidor |
| `scripts/fetch-openapi.mjs` | Descarga el contrato con login |
| `docs/paridad/shell.md`, `docs/paridad/clientes.md` | Fichas de paridad |
| `Dockerfile`, `deploy/nginx/default.conf`, `deploy/nginx/bootstrap.conf`, `deploy/docker-compose.prod.yml` | Despliegue |
| `tests/e2e/clientes.spec.ts`, `playwright.config.ts` | Smoke e2e |

**Repo servidor**

| Fichero | Cambio |
|---|---|
| `pom.xml` | + springdoc |
| `src/main/java/com/reparaciones/servidor/controller/AuthController.java` | `LoginResponse` tipado, records no privados |
| `src/main/java/com/reparaciones/servidor/model/ValorBooleano.java` | Nuevo record `{value}` |
| `src/main/java/com/reparaciones/servidor/controller/ClienteController.java` | `tieneTelefonos` devuelve `ValorBooleano`; records no privados |
| `src/test/java/com/reparaciones/servidor/controller/AuthControllerTest.java`, `ClienteControllerTest.java` | Nuevos tests |
| `Dockerfile` | Nuevo (copia del que vive en la VM de preprod) |
| `docs/schema.md`, `docs/api_contract.md` | Regenerado / sustituido por nota |

**Repo raíz**: `gestion-reparaciones-cliente/docs/api_contract.md` (nota), gitlinks.

---

### Task 1: Scaffold del repo web, calidad y CI

**Files:**
- Create: todo el scaffold de Vite en `gestion-reparaciones-web/`, `vite.config.ts`, `src/test/setup.ts`, `src/shared/lib/version.ts`, `src/shared/lib/version.test.ts`, `.prettierrc`, `.env.example`, `.github/workflows/ci.yml`, `README.md`
- Modify: `package.json` (scripts), `tsconfig.app.json` (alias), `.gitignore`

**Interfaces:**
- Produces: alias `@/` → `src/`; `npm run check`; constante `APP_VERSION: string` en `src/shared/lib/version.ts`; variable de entorno `VITE_API_PROXY_TARGET` para el proxy de desarrollo.

- [ ] **Step 1: Crear la rama y el scaffold de Vite (en carpeta temporal, porque el repo ya tiene README)**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
git checkout -b feature/web-cimientos
cd .. && npm create vite@latest tmp-vite-scaffold -- --template react-ts
cp -r tmp-vite-scaffold/. gestion-reparaciones-web/ && rm -rf tmp-vite-scaffold
cd gestion-reparaciones-web && git checkout -- README.md && ls
```
Expected: `index.html package.json src/ tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts eslint.config.js public/` y el README original intacto.

- [ ] **Step 2: Instalar dependencias base y de test**

```bash
npm install
npm install -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event msw prettier eslint-config-prettier @types/node
```

- [ ] **Step 3: Configurar Vite (alias, proxy, vitest, versión)**

`vite.config.ts`:
```ts
/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')) as { version: string }

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    define: { __APP_VERSION__: JSON.stringify(pkg.version) },
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
          secure: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: false,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  }
})
```

`src/vite-env.d.ts` (añadir al final):
```ts
declare const __APP_VERSION__: string
```

`tsconfig.app.json`: dentro de `compilerOptions` añadir
```json
"baseUrl": ".",
"paths": { "@/*": ["./src/*"] },
"types": ["vite/client", "@testing-library/jest-dom"]
```

`src/test/setup.ts`:
```ts
import '@testing-library/jest-dom/vitest'
```

`.env.example`:
```
# URL a la que el proxy de Vite manda /api en desarrollo (VM de producción o servidor local)
VITE_API_PROXY_TARGET=http://localhost:8080
```
`.gitignore`: añadir la línea `.env.local`.

`.prettierrc`:
```json
{ "semi": false, "singleQuote": true, "printWidth": 110, "trailingComma": "all" }
```
`eslint.config.js`: añadir `import prettier from 'eslint-config-prettier'` y `prettier` como último elemento del array exportado.

`package.json` scripts (sustituir el bloque):
```json
"scripts": {
  "dev": "vite",
  "build": "tsc -b && vite build",
  "preview": "vite preview",
  "lint": "eslint .",
  "typecheck": "tsc -b",
  "test": "vitest run",
  "test:watch": "vitest",
  "format": "prettier --write .",
  "check": "npm run lint && npm run typecheck && npm run test"
}
```

- [ ] **Step 4: Test de versión (falla porque no existe el módulo)**

`src/shared/lib/version.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { APP_VERSION } from './version'

describe('APP_VERSION', () => {
  it('es un semver tomado de package.json', () => {
    expect(APP_VERSION).toMatch(/^\d+\.\d+\.\d+$/)
  })
})
```
Run: `npm test` → FAIL: `Cannot find module './version'`.

- [ ] **Step 5: Implementar y poner versión 0.1.0**

`src/shared/lib/version.ts`:
```ts
export const APP_VERSION: string = __APP_VERSION__
```
En `package.json`: `"version": "0.1.0"`.
Run: `npm test` → PASS (1 test).

- [ ] **Step 6: CI**

`.github/workflows/ci.yml`:
```yaml
name: CI
on:
  push:
  pull_request:
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 24
          cache: npm
      - run: npm ci
      - run: npm run check
```

- [ ] **Step 7: README de arranque**

Sustituir `README.md` por:
```markdown
# gestion-reparaciones-web

Cliente web del ERP de reparaciones de Fonestore (React + TypeScript). Sustituye al cliente JavaFX
(`gestion-reparaciones-cliente`) contra el mismo servidor Spring Boot (`gestion-reparaciones-servidor`).

## Arranque en local
1. `npm install`
2. Copia `.env.example` a `.env.local` y pon en `VITE_API_PROXY_TARGET` la URL de la API
   (VM de producción `https://erp.fonestore.es` o un servidor local `http://localhost:8080`).
3. `npm run dev` → http://localhost:5173. Las llamadas a `/api` van por proxy a esa URL, sin CORS.

## Calidad
`npm run check` = lint + typecheck + tests (lo mismo que ejecuta la CI). `npm run test:watch` en desarrollo.

## Contrato de la API
`npm run api:types` descarga el OpenAPI del servidor y regenera `src/shared/api/schema.d.ts` (ver `scripts/`).

## Documentación
- Spec maestra y de cimientos: repo raíz, `docs/superpowers/specs/2026-09-13-*`.
- Fichas de paridad por vista: `docs/paridad/`.
```

- [ ] **Step 8: Comprobar todo y commitear**

Run: `npm run check` → lint sin errores, `tsc -b` sin errores, 1 test PASS. `npm run build` → `dist/index.html` existe.
```bash
git add -A && git commit -m "chore(web): scaffold Vite + React + TS, vitest, prettier, CI y README"
```

---

### Task 2: Tailwind 4, shadcn/ui y tokens de estilo

**Files:**
- Create: `src/shared/styles/tokens.css`, `src/shared/styles/globals.css`, `src/shared/lib/utils.ts`, `components.json`, `src/shared/ui/*.tsx` (generados por shadcn), `src/shared/ui/button.test.tsx`
- Modify: `vite.config.ts` (plugin Tailwind), `src/main.tsx` (importar `globals.css`), borrar `src/App.css` e `src/index.css`

**Interfaces:**
- Produces: utilidades Tailwind con nombre de token (`bg-azul-noche`, `text-crema`, `bg-fila-reparado-bg`, `border-fila-reparado-brd`, ...); componentes shadcn importables desde `@/shared/ui/<nombre>`; `cn()` en `@/shared/lib/utils`.

- [ ] **Step 1: Instalar Tailwind 4 y dependencias de shadcn**

```bash
npm install tailwindcss @tailwindcss/vite tw-animate-css class-variance-authority clsx tailwind-merge lucide-react
```
`vite.config.ts`: `import tailwindcss from '@tailwindcss/vite'` y `plugins: [react(), tailwindcss()]`.

- [ ] **Step 2: Tokens (valores de `Colores.java` y `app.css` del JavaFX)**

`src/shared/styles/tokens.css`:
```css
/* Tokens de color del ERP. Fuente: gestion-reparaciones-cliente utils/Colores.java y styles/app.css.
   Ningún componente escribe un color a mano: usa estas utilidades (bg-azul-noche, text-crema, ...). */
@theme {
  --color-azul-noche: #001232;
  --color-azul-noche-hover: #0A2040;
  --color-azul-medio: #2C3B54;
  --color-azul-gris: #586376;
  --color-crema: #F6F6F6;
  --color-amarillo: #F1E356;
  --color-fondo-vista: #DDE1E7;
  --color-fondo-login: #EFEFEF;
  --color-fondo-input: #F3F3F3;
  --color-nav-switch: #E8EAF0;
  --color-borde-input: #D4D8DE;
  --color-gris-borde: #A9A9A9;
  --color-gris-disabled: #E7E7E7;
  --color-texto-suave: #A0A8B4;
  --color-pill-bg: #F0F2F5;
  --color-pill-borde: #C8CDD5;
  --color-texto-error: #B03040;
  --color-texto-accion: #4A6FA5;
  --color-verde-ok: #4CAF50;
  --color-rojo-accion: #A84040;
  --color-rojo-sin-stock: #B03040;
  --color-fila-edicion-bg: #EBF4FF;
  --color-fila-edicion-brd: #B3D4F5;
  --color-fila-reparado-bg: #EBF5EB;
  --color-fila-reparado-brd: #C5E1C5;
  --color-fila-reparado-ico: #8AC7AF;
  --color-fila-recibido-bg: #C8E6C9;
  --color-fila-recibido-brd: #3A7D44;
  --color-fila-parcial-bg: #E8E0F7;
  --color-fila-parcial-brd: #7B5EA7;
  --color-fila-cancelado-bg: #E0E0E0;
  --color-fila-cancelado-brd: #9E9E9E;
  --color-fila-cancelado-text: #9E9E9E;
  --color-fila-incidencia-bg: #E8C8CE;
  --color-fila-incidencia-brd: #B83746;
  --color-fila-solicitud-bg: #FDEBC8;
  --color-fila-solicitud-brd: #C07800;
  --color-fila-urgente-brd: #C62828;
  --color-fila-modificada-bg: #E0F7FA;
  --color-fila-modificada-brd: #00838F;
  --color-fila-selected-brd: #3D5070;
  --color-fila-sep: #C2C8D0;
  --color-texto-nav-activo: #FAFAFA;
  --spacing-navbar: 64px;
}
```

`src/shared/styles/globals.css` (variables que esperan los componentes shadcn, mapeadas a nuestros tokens):
```css
@import 'tailwindcss';
@import 'tw-animate-css';
@import './tokens.css';

@custom-variant dark (&:is(.dark *));

:root {
  --radius: 0.625rem;
  --background: #f6f6f6;
  --foreground: #2c3b54;
  --card: #ffffff;
  --card-foreground: #2c3b54;
  --popover: #ffffff;
  --popover-foreground: #2c3b54;
  --primary: #001232;
  --primary-foreground: #f6f6f6;
  --secondary: #e8eaf0;
  --secondary-foreground: #2c3b54;
  --muted: #f0f2f5;
  --muted-foreground: #586376;
  --accent: #e8eaf0;
  --accent-foreground: #001232;
  --destructive: #a84040;
  --border: #d4d8de;
  --input: #d4d8de;
  --ring: #3d5070;
}

@theme inline {
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
}

@layer base {
  * {
    @apply border-border outline-ring/50;
  }
  body {
    @apply bg-background text-foreground;
    font-family: system-ui, 'Segoe UI', sans-serif;
  }
}
```
Borrar `src/App.css` y `src/index.css`; en `src/main.tsx` importar `'@/shared/styles/globals.css'` en vez de `./index.css`; en `src/App.tsx` quitar `import './App.css'` (App.tsx se sustituye en la Task 5; por ahora dejarlo mínimo: `export default function App() { return <h1>FSGR</h1> }`).

- [ ] **Step 3: shadcn sin asistente (components.json a mano) y añadir componentes**

`src/shared/lib/utils.ts`:
```ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```
`components.json`:
```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": { "config": "", "css": "src/shared/styles/globals.css", "baseColor": "neutral", "cssVariables": true, "prefix": "" },
  "iconLibrary": "lucide",
  "aliases": { "components": "@/shared", "utils": "@/shared/lib/utils", "ui": "@/shared/ui", "lib": "@/shared/lib", "hooks": "@/shared/hooks" }
}
```
```bash
npx shadcn@latest add --yes --overwrite button dialog dropdown-menu input label badge table checkbox popover command context-menu tooltip
ls src/shared/ui
```
Expected: `button.tsx dialog.tsx dropdown-menu.tsx input.tsx label.tsx badge.tsx table.tsx checkbox.tsx popover.tsx command.tsx context-menu.tsx tooltip.tsx`. Si `add` deja algún import a `@/components/...`, corregirlo a `@/shared/...` (grep `@/components`).

- [ ] **Step 4: Test de humo de shadcn + tokens**

`src/shared/ui/button.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Button } from './button'

describe('Button (shadcn)', () => {
  it('renderiza y acepta utilidades de token', () => {
    render(<Button className="bg-azul-noche text-crema">Iniciar Sesión</Button>)
    const btn = screen.getByRole('button', { name: 'Iniciar Sesión' })
    expect(btn).toHaveClass('bg-azul-noche')
    expect(btn).toHaveClass('text-crema')
  })
})
```
Run: `npm test` → PASS (2 tests). `npm run build` → sin errores (Tailwind compila `tokens.css`; si una utilidad de token no existiera, la clase no se generaría pero el test de clase seguiría pasando: por eso además abrir `npm run dev` y comprobar que el `<h1>` con `className="bg-azul-noche text-crema"` se ve navy con texto crema).

- [ ] **Step 5: Commit**

```bash
npm run check && git add -A && git commit -m "feat(web): Tailwind 4, shadcn/ui y tokens de color del JavaFX"
```

---

### Task 3: Servidor — springdoc, respuestas tipadas, Dockerfile y docs

**Files:**
- Modify: `gestion-reparaciones-servidor/pom.xml`, `.../controller/AuthController.java`, `.../controller/ClienteController.java`, `docs/schema.md`, `docs/api_contract.md`
- Create: `.../model/ValorBooleano.java`, `src/test/java/com/reparaciones/servidor/controller/AuthControllerTest.java`, `.../ClienteControllerTest.java`, `Dockerfile`
- Root repo: `gestion-reparaciones-cliente/docs/api_contract.md` (nota)

**Interfaces:**
- Produces: `GET /v3/api-docs` (autenticado) con esquemas `LoginResponse {idUsu:int, nombreUsuario, rol, idTec:int|null, token}`, `Cliente {idCli, nombre, activo, updatedAt}`, `ValorBooleano {value:boolean}`, `NombreRequest {nombre}`, `EditarRequest {nombre, updatedAt}`, `ActivoRequest {activo, updatedAt}`, `LoginRequest {usuario, password}`. La Task 4 genera tipos de aquí.

- [ ] **Step 1: Rama y dependencia**

```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-servidor && git checkout main && git pull && git checkout -b feature/web-cimientos
```
En `pom.xml`, dentro de `<dependencies>`:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

- [ ] **Step 2: Tests de controller (fallan: no compila)**

`src/test/java/com/reparaciones/servidor/controller/AuthControllerTest.java`:
```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final AuthController ctl = new AuthController(authManager, jwtUtil, logDao, usuarioDao);

    @Test void loginDevuelveRespuestaTipadaConLosCincoCampos() {
        var principal = new UsuarioPrincipal(7, "fati", "x", "SUPERTECNICO", 3);
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authManager.authenticate(any())).thenReturn(auth);
        when(jwtUtil.generateToken(principal)).thenReturn("jwt-123");

        var resp = ctl.login(new AuthController.LoginRequest("fati", "secreta"));

        assertEquals(200, resp.getStatusCode().value());
        var body = (AuthController.LoginResponse) resp.getBody();
        assertNotNull(body);
        assertEquals(7, body.idUsu());
        assertEquals("fati", body.nombreUsuario());
        assertEquals("SUPERTECNICO", body.rol());
        assertEquals(3, body.idTec());
        assertEquals("jwt-123", body.token());
        verify(logDao).insertar(7, "LOGIN", "");
    }

    @Test void loginConCredencialesMalasDevuelve401SinCuerpo() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        var resp = ctl.login(new AuthController.LoginRequest("x", "y"));
        assertEquals(401, resp.getStatusCode().value());
        assertNull(resp.getBody());
    }
}
```

`src/test/java/com/reparaciones/servidor/controller/ClienteControllerTest.java`:
```java
package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ClienteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClienteControllerTest {

    private final ClienteDAO dao = mock(ClienteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ClienteController ctl = new ClienteController(dao, logDao);

    @Test void tieneTelefonosDevuelveValorBooleanoTipado() {
        when(dao.tieneTelefonos(5)).thenReturn(true);
        assertTrue(ctl.tieneTelefonos(5).value());
        when(dao.tieneTelefonos(6)).thenReturn(false);
        assertFalse(ctl.tieneTelefonos(6).value());
    }
}
```
Run: `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH; mvn -q test -Dtest='AuthControllerTest,ClienteControllerTest'` → FAIL de compilación (`LoginRequest` privado, `LoginResponse` y `ValorBooleano` no existen).

- [ ] **Step 3: Implementar**

`src/main/java/com/reparaciones/servidor/model/ValorBooleano.java`:
```java
package com.reparaciones.servidor.model;

/** Envoltorio {"value": true|false} de los endpoints que devuelven un único booleano. */
public record ValorBooleano(boolean value) {}
```

`AuthController.java`: sustituir el método `login` y los records del final por:
```java
    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            content = @io.swagger.v3.oas.annotations.media.Content(
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.usuario(), req.password()));
            var principal = (UsuarioPrincipal) auth.getPrincipal();
            String token  = jwtUtil.generateToken(principal);

            logDao.insertar(principal.getIdUsu(), "LOGIN", "");

            return ResponseEntity.ok(new LoginResponse(
                    principal.getIdUsu(), principal.getUsername(), principal.getRol(),
                    principal.getIdTec(), token));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
    }
    // ... cambiarPassword sin cambios ...

    /** Respuesta del login. Mismo JSON que el Map anterior; tipada para el contrato OpenAPI. */
    public record LoginResponse(int idUsu, String nombreUsuario, String rol, Integer idTec, String token) {}
    record LoginRequest(String usuario, String password) {}
    record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
```
Quitar los imports de `HashMap`/`Map` si quedan sin uso (`Map` sigue usándose en `cambiarPassword`).

`ClienteController.java`: `tieneTelefonos` pasa a
```java
    @GetMapping("/{idCli}/tiene-telefonos")
    public ValorBooleano tieneTelefonos(@PathVariable int idCli) {
        return new ValorBooleano(dao.tieneTelefonos(idCli));
    }
```
con `import com.reparaciones.servidor.model.ValorBooleano;` (quitar `java.util.Map` si queda sin uso), y los tres records del final pasan de `private record` a `record` (package-private) para que springdoc los introspeccione.

Run: `mvn -q test` (suite completa) → todos PASS, incluidos los 3 nuevos.

- [ ] **Step 4: Validar el arranque del contexto y el contrato (regla del proyecto: no hay test de contexto)**

Arrancar XAMPP (MariaDB local con la BD `reparaciones`), luego:
```bash
mvn -q spring-boot:run > /tmp/server.log 2>&1 &
sleep 40 && grep -c "Started App" /tmp/server.log
T=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"usuario":"admin","password":"<la del admin local>"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s http://localhost:8080/v3/api-docs -H "Authorization: Bearer $T" -o /tmp/openapi.json && grep -o '"/api/clientes[^"]*"' /tmp/openapi.json | sort -u
grep -o '"LoginResponse"\|"ValorBooleano"\|"EditarRequest"' /tmp/openapi.json | sort -u
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
```
Expected: `1` (arrancó); las 4 rutas de clientes; los 3 esquemas; y `401` sin token (el contrato exige sesión). Parar el servidor (`kill %1`).

- [ ] **Step 5: Dockerfile del servidor en git (hoy vive solo en la VM de preprod, copiado literal de `Apuntes/despliegue_vdc.md` Paso 6)**

`gestion-reparaciones-servidor/Dockerfile`:
```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && mvn package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Documentación de contrato y esquema**

`docs/api_contract.md` del servidor → sustituir todo el contenido por:
```markdown
# Contrato de la API

Desde 2026-09 el contrato se **genera** con springdoc: `GET /v3/api-docs` (requiere JWT) devuelve el
OpenAPI de todos los controllers. La app web guarda un snapshot en `gestion-reparaciones-web/api/openapi.json`
y genera sus tipos TypeScript de él (`npm run api:types`). Este fichero ya no se mantiene a mano.
```
Mismo texto en `gestion-reparaciones-cliente/docs/api_contract.md` (repo raíz).

`docs/schema.md` → regenerar: cabecera + lista de tablas extraída del SQL:
```bash
{ printf '# Esquema de la base de datos\n\nFuente de verdad: `sql/crear_bd.sql` (y las migraciones de `sql/` para entornos existentes).\nTablas (generado con grep el %s):\n\n' "$(date +%F)"; grep -oE 'CREATE TABLE (IF NOT EXISTS )?`?[A-Za-z_]+' sql/crear_bd.sql | sed -E 's/.*`?([A-Za-z_]+)$/- `\1`/'; } > docs/schema.md && cat docs/schema.md
```

- [ ] **Step 7: Commits**

```bash
git add pom.xml src/main src/test && git commit -m "feat(servidor): springdoc-openapi, LoginResponse y ValorBooleano tipados para el contrato de la web"
git add Dockerfile docs && git commit -m "chore(servidor): Dockerfile en git y docs de contrato/esquema regenerados"
cd .. && git add gestion-reparaciones-cliente/docs/api_contract.md && git commit -m "docs: api_contract del cliente apunta al OpenAPI generado"
```

---

### Task 4: Web — tipos OpenAPI, cliente API, errores, sesión y estado de conexión

**Files:**
- Create: `scripts/fetch-openapi.mjs`, `api/openapi.json`, `src/shared/api/schema.d.ts` (generado), `src/shared/api/errors.ts` + `errors.test.ts`, `src/shared/api/conexion.ts` + `conexion.test.ts`, `src/app/session/storage.ts` + `storage.test.ts`, `src/app/session/expiracion.ts` + `expiracion.test.ts`, `src/shared/api/client.ts` + `client.test.ts`, `src/test/server.ts`
- Modify: `package.json` (scripts `api:types`, `api:types:offline`), `src/test/setup.ts`

**Interfaces:**
- Produces:
  - `api` (openapi-fetch tipado con `paths`), `type Cliente = components['schemas']['Cliente']`, `type LoginResponse = components['schemas']['LoginResponse']`.
  - `errors.ts`: `class ApiError extends Error { status: number }`, `SesionExpiradaError`, `PermisoError`, `NoEncontradoError`, `StaleDataError`, `ReglaNegocioError`, `ConexionError`; `clasificar(status: number, msg: string | null): ApiError`; `extraerMensaje(body: unknown): string | null`.
  - `conexion.ts`: `reportarFallo()`, `reportarExito()`, `estaConectado(): boolean`, `useConexion(): boolean`, `intervaloRefresco(): number` (60000 conectado / 5000 desconectado).
  - `storage.ts`: `type Sesion = { idUsu: number; nombreUsuario: string; rol: string; idTec: number | null; token: string }`, `leerSesion(): Sesion | null`, `guardarSesion(s)`, `borrarSesion()`, `esAdmin(s)`, `esSuperTecnico(s)`, `esAdminOSuperTecnico(s)`.
  - `expiracion.ts`: `onSesionExpirada(handler: () => void)`, `dispararSesionExpirada()`, `rearmarSesionExpirada()`.

- [ ] **Step 1: Snapshot del contrato y generación de tipos**

```bash
npm install openapi-fetch
npm install -D openapi-typescript
```
`scripts/fetch-openapi.mjs`:
```js
// Descarga /v3/api-docs del servidor (requiere login) y lo guarda en api/openapi.json.
// Uso: API_URL=http://localhost:8080 API_USER=admin API_PASS=xxx node scripts/fetch-openapi.mjs
import { writeFileSync } from 'node:fs'

const base = process.env.API_URL ?? 'http://localhost:8080'
const login = await fetch(`${base}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: process.env.API_USER, password: process.env.API_PASS }),
})
if (!login.ok) throw new Error(`Login ${login.status}: revisa API_USER/API_PASS`)
const { token } = await login.json()
const docs = await fetch(`${base}/v3/api-docs`, { headers: { Authorization: `Bearer ${token}` } })
if (!docs.ok) throw new Error(`api-docs ${docs.status}`)
const json = await docs.json()
writeFileSync('api/openapi.json', JSON.stringify(json, null, 2) + '\n')
console.log(`api/openapi.json actualizado: ${Object.keys(json.paths).length} rutas`)
```
`package.json` scripts: añadir
```json
"api:types": "node scripts/fetch-openapi.mjs && npm run api:types:offline",
"api:types:offline": "openapi-typescript api/openapi.json -o src/shared/api/schema.d.ts"
```
Con el servidor de la Task 3 arrancado en local (XAMPP + `mvn spring-boot:run`):
```bash
mkdir -p api && API_URL=http://localhost:8080 API_USER=admin API_PASS='<la del admin local>' npm run api:types
grep -n "LoginResponse\|ValorBooleano\|'/api/clientes'" src/shared/api/schema.d.ts | head
```
Expected: fichero generado con `paths['/api/clientes']` y `components['schemas']['LoginResponse']`.

- [ ] **Step 2: Tests de errores (fallan)**

`src/shared/api/errors.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import {
  ConexionError, NoEncontradoError, PermisoError, ReglaNegocioError, SesionExpiradaError, StaleDataError,
  clasificar, extraerMensaje,
} from './errors'

describe('clasificar (port de ApiClient.clasificar)', () => {
  it('401 → sesión expirada con el texto fijo', () => {
    const e = clasificar(401, null)
    expect(e).toBeInstanceOf(SesionExpiradaError)
    expect(e.message).toBe('Sesión expirada. Vuelve a iniciar sesión.')
  })
  it('403 → sin permisos', () => {
    expect(clasificar(403, 'lo que sea')).toBeInstanceOf(PermisoError)
    expect(clasificar(403, null).message).toBe('No tienes permisos para realizar esta acción.')
  })
  it('404 → recurso no encontrado', () => {
    expect(clasificar(404, null)).toBeInstanceOf(NoEncontradoError)
    expect(clasificar(404, null).message).toBe('Recurso no encontrado.')
  })
  it('409 → StaleData con el mensaje del servidor', () => {
    const e = clasificar(409, 'El cliente tiene teléfonos asociados')
    expect(e).toBeInstanceOf(StaleDataError)
    expect(e.message).toBe('El cliente tiene teléfonos asociados')
  })
  it('422 conserva el mensaje de negocio del servidor', () => {
    expect(clasificar(422, 'Contraseña actual incorrecta.').message).toBe('Contraseña actual incorrecta.')
    expect(clasificar(422, 'x')).toBeInstanceOf(ReglaNegocioError)
  })
  it('422 sin mensaje usa un texto genérico', () => {
    expect(clasificar(422, null).message).toBe('El servidor ha rechazado la operación.')
  })
  it('5xx → error de conexión', () => {
    expect(clasificar(500, null)).toBeInstanceOf(ConexionError)
    expect(clasificar(503, null)).toBeInstanceOf(ConexionError)
  })
  it('otros códigos → ApiError genérico con el mensaje o el código', () => {
    expect(clasificar(400, 'Body inválido').message).toBe('Body inválido')
    expect(clasificar(418, null).message).toBe('Error del servidor (418).')
  })
})

describe('extraerMensaje', () => {
  it('lee message de un JSON de Spring', () => {
    expect(extraerMensaje({ message: 'hola', status: 422 })).toBe('hola')
  })
  it('devuelve null si no hay message', () => {
    expect(extraerMensaje({})).toBeNull()
    expect(extraerMensaje(null)).toBeNull()
    expect(extraerMensaje('texto plano')).toBe('texto plano')
    expect(extraerMensaje('')).toBeNull()
  })
})
```
Run: `npm test -- errors` → FAIL (módulo inexistente).

- [ ] **Step 3: Implementar errores**

`src/shared/api/errors.ts`:
```ts
/** Port de ApiClient.clasificar del cliente JavaFX: un tipo de error por familia de respuesta. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = new.target.name
  }
}
export class SesionExpiradaError extends ApiError {}
export class PermisoError extends ApiError {}
export class NoEncontradoError extends ApiError {}
/** 409: bloqueo optimista (updatedAt) o conflicto de negocio; el mensaje del servidor es el bueno. */
export class StaleDataError extends ApiError {}
/** 422: regla de negocio del servidor; su mensaje se muestra tal cual. */
export class ReglaNegocioError extends ApiError {}
/** 5xx, fallo de red o timeout: activa el banner de sin conexión. */
export class ConexionError extends ApiError {}

export const MSG_SESION_EXPIRADA = 'Sesión expirada. Vuelve a iniciar sesión.'
export const MSG_SIN_PERMISOS = 'No tienes permisos para realizar esta acción.'
export const MSG_NO_ENCONTRADO = 'Recurso no encontrado.'
export const MSG_SIN_CONEXION = 'Sin conexión con el servidor.'

export function clasificar(status: number, msg: string | null): ApiError {
  switch (status) {
    case 401:
      return new SesionExpiradaError(401, MSG_SESION_EXPIRADA)
    case 403:
      return new PermisoError(403, MSG_SIN_PERMISOS)
    case 404:
      return new NoEncontradoError(404, MSG_NO_ENCONTRADO)
    case 409:
      return new StaleDataError(409, msg ?? 'El registro fue modificado por otro usuario.')
    case 422:
      return new ReglaNegocioError(422, msg ?? 'El servidor ha rechazado la operación.')
    default:
      if (status >= 500) return new ConexionError(status, MSG_SIN_CONEXION)
      return new ApiError(status, msg ?? `Error del servidor (${status}).`)
  }
}

export function extraerMensaje(body: unknown): string | null {
  if (typeof body === 'string') return body.trim() === '' ? null : body
  if (body && typeof body === 'object' && 'message' in body) {
    const m = (body as { message?: unknown }).message
    return typeof m === 'string' && m.trim() !== '' ? m : null
  }
  return null
}
```
Run: `npm test -- errors` → PASS.

- [ ] **Step 4: Estado de conexión (test, luego código)**

`src/shared/api/conexion.test.ts`:
```ts
import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { estaConectado, intervaloRefresco, reportarExito, reportarFallo, useConexion } from './conexion'

describe('estado de conexión', () => {
  beforeEach(() => reportarExito())
  it('empieza conectado y el intervalo es de 60 s', () => {
    expect(estaConectado()).toBe(true)
    expect(intervaloRefresco()).toBe(60_000)
  })
  it('un fallo lo pone desconectado (5 s) y un éxito lo autocura', () => {
    reportarFallo()
    expect(estaConectado()).toBe(false)
    expect(intervaloRefresco()).toBe(5_000)
    reportarExito()
    expect(estaConectado()).toBe(true)
  })
  it('useConexion se actualiza al cambiar el estado', () => {
    const { result } = renderHook(() => useConexion())
    expect(result.current).toBe(true)
    act(() => reportarFallo())
    expect(result.current).toBe(false)
  })
})
```
`src/shared/api/conexion.ts`:
```ts
import { useSyncExternalStore } from 'react'

/** Equivalente a ConexionEstado del JavaFX: un flag global que alimenta el banner y el ritmo de refresco. */
let conectado = true
const listeners = new Set<() => void>()

function emitir() {
  listeners.forEach((l) => l())
}
export function reportarFallo() {
  if (conectado) {
    conectado = false
    emitir()
  }
}
export function reportarExito() {
  if (!conectado) {
    conectado = true
    emitir()
  }
}
export function estaConectado() {
  return conectado
}
/** 60 s conectado, 5 s mientras el banner está activo (mismos valores que Poller.java). */
export function intervaloRefresco() {
  return conectado ? 60_000 : 5_000
}
export function useConexion(): boolean {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    () => conectado,
  )
}
```
Run: `npm test -- conexion` → PASS.

- [ ] **Step 5: Storage de sesión (test, luego código)**

`src/app/session/storage.test.ts`:
```ts
import { beforeEach, describe, expect, it } from 'vitest'
import { borrarSesion, esAdmin, esAdminOSuperTecnico, esSuperTecnico, guardarSesion, leerSesion, type Sesion } from './storage'

const fati: Sesion = { idUsu: 7, nombreUsuario: 'fati', rol: 'SUPERTECNICO', idTec: 3, token: 'jwt' }

describe('storage de sesión', () => {
  beforeEach(() => sessionStorage.clear())
  it('sin sesión devuelve null', () => {
    expect(leerSesion()).toBeNull()
  })
  it('guarda y lee la sesión en sessionStorage', () => {
    guardarSesion(fati)
    expect(leerSesion()).toEqual(fati)
    expect(sessionStorage.getItem('fsgr.sesion')).toContain('"fati"')
  })
  it('borrar la deja en null', () => {
    guardarSesion(fati)
    borrarSesion()
    expect(leerSesion()).toBeNull()
  })
  it('un valor corrupto se trata como sin sesión', () => {
    sessionStorage.setItem('fsgr.sesion', '{no-json')
    expect(leerSesion()).toBeNull()
  })
  it('helpers de rol calcados de Sesion.java', () => {
    expect(esSuperTecnico(fati)).toBe(true)
    expect(esAdmin(fati)).toBe(false)
    expect(esAdminOSuperTecnico(fati)).toBe(true)
    const admin = { ...fati, rol: 'ADMIN', idTec: null }
    expect(esAdmin(admin)).toBe(true)
    expect(esSuperTecnico(admin)).toBe(false)
    const tec = { ...fati, rol: 'TECNICO' }
    expect(esAdminOSuperTecnico(tec)).toBe(false)
    expect(esAdmin(null)).toBe(false)
  })
})
```
`src/app/session/storage.ts`:
```ts
export type Sesion = {
  idUsu: number
  nombreUsuario: string
  rol: string
  idTec: number | null
  token: string
}

const CLAVE = 'fsgr.sesion'

export function leerSesion(): Sesion | null {
  try {
    const raw = sessionStorage.getItem(CLAVE)
    if (!raw) return null
    const s = JSON.parse(raw) as Partial<Sesion>
    if (typeof s.token !== 'string' || typeof s.nombreUsuario !== 'string') return null
    return s as Sesion
  } catch {
    return null
  }
}
export function guardarSesion(s: Sesion) {
  sessionStorage.setItem(CLAVE, JSON.stringify(s))
}
export function borrarSesion() {
  sessionStorage.removeItem(CLAVE)
}
export const esAdmin = (s: Sesion | null) => s?.rol === 'ADMIN'
export const esSuperTecnico = (s: Sesion | null) => s?.rol === 'SUPERTECNICO'
export const esAdminOSuperTecnico = (s: Sesion | null) => esAdmin(s) || esSuperTecnico(s)
```
Run: `npm test -- storage` → PASS.

- [ ] **Step 6: Hook de sesión expirada (test, luego código; port de SesionExpiradaHookTest)**

`src/app/session/expiracion.test.ts`:
```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { dispararSesionExpirada, onSesionExpirada, rearmarSesionExpirada } from './expiracion'
import { borrarSesion, guardarSesion } from './storage'

describe('sesión expirada', () => {
  beforeEach(() => {
    sessionStorage.clear()
    rearmarSesionExpirada()
  })
  it('sin sesión no dispara (p. ej. 401 del propio login)', () => {
    const h = vi.fn()
    onSesionExpirada(h)
    dispararSesionExpirada()
    expect(h).not.toHaveBeenCalled()
  })
  it('con sesión dispara una sola vez aunque fallen varias peticiones a la vez', () => {
    const h = vi.fn()
    onSesionExpirada(h)
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't' })
    dispararSesionExpirada()
    dispararSesionExpirada()
    expect(h).toHaveBeenCalledTimes(1)
  })
  it('tras rearmar (nuevo login) vuelve a disparar', () => {
    const h = vi.fn()
    onSesionExpirada(h)
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't' })
    dispararSesionExpirada()
    borrarSesion()
    rearmarSesionExpirada()
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'TECNICO', idTec: 1, token: 't2' })
    dispararSesionExpirada()
    expect(h).toHaveBeenCalledTimes(2)
  })
})
```
`src/app/session/expiracion.ts`:
```ts
import { leerSesion } from './storage'

/** Un 401 con sesión activa expulsa al usuario una sola vez (varias peticiones pueden fallar a la vez). */
let handler: (() => void) | null = null
let disparado = false

export function onSesionExpirada(h: () => void) {
  handler = h
}
export function dispararSesionExpirada() {
  if (disparado || !leerSesion() || !handler) return
  disparado = true
  handler()
}
/** Llamar tras un login correcto. */
export function rearmarSesionExpirada() {
  disparado = false
}
```
Run: `npm test -- expiracion` → PASS.

- [ ] **Step 7: Infra MSW y cliente API (test, luego código)**

`src/test/server.ts`:
```ts
import { setupServer } from 'msw/node'

export const server = setupServer()
```
`src/test/setup.ts` (completo):
```ts
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// Polyfills que Radix (popover, dropdown, context-menu) necesita y jsdom no trae.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.setPointerCapture ??= () => {}
Element.prototype.releasePointerCapture ??= () => {}
Element.prototype.scrollIntoView ??= () => {}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  sessionStorage.clear()
})
afterAll(() => server.close())
```
`src/shared/api/client.test.ts`:
```ts
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { guardarSesion } from '@/app/session/storage'
import { onSesionExpirada, rearmarSesionExpirada } from '@/app/session/expiracion'
import { estaConectado, reportarExito } from './conexion'
import { api } from './client'
import { ConexionError, ReglaNegocioError, SesionExpiradaError, StaleDataError } from './errors'

describe('cliente API', () => {
  beforeEach(() => {
    reportarExito()
    rearmarSesionExpirada()
  })
  it('añade el bearer de la sesión y devuelve data tipada', async () => {
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'ADMIN', idTec: null, token: 'jwt-1' })
    let auth = ''
    server.use(
      http.get('*/api/clientes', ({ request }) => {
        auth = request.headers.get('authorization') ?? ''
        return HttpResponse.json([{ idCli: 1, nombre: 'WEB', activo: true, updatedAt: '2026-09-01T10:00:00' }])
      }),
    )
    const { data } = await api.GET('/api/clientes')
    expect(auth).toBe('Bearer jwt-1')
    expect(data?.[0]?.nombre).toBe('WEB')
  })
  it('409 lanza StaleDataError con el mensaje del servidor', async () => {
    server.use(http.delete('*/api/clientes/5', () => HttpResponse.json({ message: 'Tiene teléfonos' }, { status: 409 })))
    await expect(api.DELETE('/api/clientes/{idCli}', { params: { path: { idCli: 5 } } })).rejects.toBeInstanceOf(StaleDataError)
    await expect(api.DELETE('/api/clientes/{idCli}', { params: { path: { idCli: 5 } } })).rejects.toThrow('Tiene teléfonos')
  })
  it('422 lanza ReglaNegocioError con el mensaje', async () => {
    server.use(http.post('*/api/clientes', () => HttpResponse.json({ message: 'Nombre duplicado' }, { status: 422 })))
    await expect(api.POST('/api/clientes', { body: { nombre: 'x' } })).rejects.toBeInstanceOf(ReglaNegocioError)
  })
  it('401 con sesión dispara el hook de sesión expirada', async () => {
    guardarSesion({ idUsu: 1, nombreUsuario: 'a', rol: 'ADMIN', idTec: null, token: 'jwt-1' })
    const h = vi.fn()
    onSesionExpirada(h)
    server.use(http.get('*/api/clientes', () => new HttpResponse(null, { status: 401 })))
    await expect(api.GET('/api/clientes')).rejects.toBeInstanceOf(SesionExpiradaError)
    expect(h).toHaveBeenCalledTimes(1)
  })
  it('fallo de red lanza ConexionError y marca desconectado; un éxito posterior autocura', async () => {
    server.use(http.get('*/api/clientes', () => HttpResponse.error()))
    await expect(api.GET('/api/clientes')).rejects.toBeInstanceOf(ConexionError)
    expect(estaConectado()).toBe(false)
    server.use(http.get('*/api/clientes', () => HttpResponse.json([])))
    await api.GET('/api/clientes')
    expect(estaConectado()).toBe(true)
  })
})
```
`src/shared/api/client.ts`:
```ts
import createClient, { type Middleware } from 'openapi-fetch'
import type { components, paths } from './schema'
import { leerSesion } from '@/app/session/storage'
import { dispararSesionExpirada } from '@/app/session/expiracion'
import { ConexionError, MSG_SIN_CONEXION, SesionExpiradaError, clasificar, extraerMensaje } from './errors'
import { reportarExito, reportarFallo } from './conexion'

export type Cliente = components['schemas']['Cliente']
export type LoginResponse = components['schemas']['LoginResponse']

/** Las rutas del contrato ya llevan /api/...; baseUrl es la origin: vacía en producción (misma origin),
 *  absoluta en tests porque el fetch de jsdom no admite URLs relativas. */
const baseUrl = import.meta.env.MODE === 'test' ? 'http://localhost' : ''
export const TIMEOUT_MS = 15_000

const auth: Middleware = {
  onRequest({ request }) {
    const s = leerSesion()
    if (s) request.headers.set('Authorization', `Bearer ${s.token}`)
    return request
  },
  async onResponse({ response }) {
    if (response.ok) {
      reportarExito()
      return response
    }
    if (response.status >= 500) reportarFallo()
    const texto = await response.clone().text()
    let body: unknown = texto
    try {
      body = JSON.parse(texto)
    } catch {
      /* texto plano */
    }
    const err = clasificar(response.status, extraerMensaje(body))
    if (err instanceof SesionExpiradaError) dispararSesionExpirada()
    throw err
  },
}

export const api = createClient<paths>({
  baseUrl,
  fetch: async (request) => {
    try {
      return await fetch(request, { signal: AbortSignal.timeout(TIMEOUT_MS) })
    } catch (e) {
      reportarFallo()
      throw new ConexionError(0, MSG_SIN_CONEXION + (e instanceof Error && e.name === 'TimeoutError' ? ' (tiempo de espera agotado)' : ''))
    }
  },
})
api.use(auth)
```

Run: `npm test -- client` → PASS (5 tests). `npm run check` → verde.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(web): contrato OpenAPI tipado, cliente API con bearer/timeout, mapeo de errores, sesión y estado de conexión"
```

---

### Task 5: SessionProvider, LoginPage y rutas con guard

**Files:**
- Create: `src/app/session/SessionProvider.tsx`, `src/app/session/RequireSesion.tsx`, `src/app/login/LoginPage.tsx`, `src/app/login/LoginPage.test.tsx`, `src/app/router.tsx`, `src/test/render.tsx`, `public/logo_inicio_sesion.png`, `public/logoNavBar.png`, `public/user.png`, `public/icono_programa.png`, `public/ojo_activar.png`, `public/ojo_desactivar.png`
- Modify: `src/main.tsx`; borrar `src/App.tsx`, `src/assets/`

**Interfaces:**
- Consumes: `api`, `LoginResponse`, `storage.ts`, `expiracion.ts`, `errors.ts` (Task 4), `Button`/`Input` shadcn (Task 2), `APP_VERSION` (Task 1).
- Produces: `useSession(): { sesion: Sesion | null; login(usuario: string, password: string): Promise<void>; logout(): void }`; `<RequireSesion/>` (layout route); `router` (createBrowserRouter) con rutas `/login`, `/`, `/reparaciones/*`, `/stock/*`, `/estadisticas/*`, `/clientes` (placeholders `PendienteDeMigrar` hasta las Tasks 6 y 8); `renderConProviders(ui, { sesion?, ruta? })` para tests; `MSG_CREDENCIALES = 'Usuario o contraseña incorrectos.'`.

- [ ] **Step 1: Dependencias e imágenes**

```bash
npm install react-router @tanstack/react-query
cp ../gestion-reparaciones-cliente/src/main/resources/images/{logo_inicio_sesion,logoNavBar,user,icono_programa,ojo_activar,ojo_desactivar}.png public/
rm -rf src/assets src/App.tsx && sed -i 's#<link rel="icon" type="image/svg+xml" href="/vite.svg" />#<link rel="icon" type="image/png" href="/icono_programa.png" />#; s#<title>.*</title>#<title>FSGR</title>#; s#<html lang="en">#<html lang="es">#' index.html && rm -f public/vite.svg
```

- [ ] **Step 2: Test de LoginPage y SessionProvider (falla)**

`src/test/render.tsx`:
```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { SessionProvider } from '@/app/session/SessionProvider'
import { guardarSesion, type Sesion } from '@/app/session/storage'

type Opciones = { sesion?: Sesion | null; ruta?: string; rutas?: ReactElement }

/** Render con QueryClient (sin reintentos), SessionProvider y MemoryRouter. `rutas` permite añadir <Route>s auxiliares. */
export function renderConProviders(ui: ReactElement, { sesion = null, ruta = '/', rutas }: Opciones = {}) {
  if (sesion) guardarSesion(sesion)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <SessionProvider>
        <MemoryRouter initialEntries={[ruta]}>
          <Routes>
            <Route path={ruta} element={ui} />
            {rutas}
          </Routes>
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

export const SESION_SUPER: Sesion = { idUsu: 7, nombreUsuario: 'fati', rol: 'SUPERTECNICO', idTec: 3, token: 'jwt-super' }
export const SESION_TEC: Sesion = { idUsu: 8, nombreUsuario: 'zara', rol: 'TECNICO', idTec: 4, token: 'jwt-tec' }
export const SESION_ADMIN: Sesion = { idUsu: 1, nombreUsuario: 'admin', rol: 'ADMIN', idTec: null, token: 'jwt-admin' }
```
`src/app/login/LoginPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { Route } from 'react-router'
import { describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders } from '@/test/render'
import { leerSesion } from '@/app/session/storage'
import { LoginPage } from './LoginPage'

const respuestaLogin = { idUsu: 7, nombreUsuario: 'fati', rol: 'SUPERTECNICO', idTec: 3, token: 'jwt-super' }

function montar() {
  return renderConProviders(<LoginPage />, { ruta: '/login', rutas: <Route path="/" element={<p>INICIO</p>} /> })
}

describe('LoginPage', () => {
  it('calca la pantalla: título, subtítulo, campos y botón', () => {
    montar()
    expect(screen.getByText('FSGR')).toBeInTheDocument()
    expect(screen.getByText('Gestión de Stock y')).toBeInTheDocument()
    expect(screen.getByText('Reparaciones')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Usuario')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Contraseña')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Iniciar Sesión' })).toBeInTheDocument()
  })
  it('con campos vacíos no llama a la API y avisa', async () => {
    let llamadas = 0
    server.use(http.post('*/api/auth/login', () => { llamadas++; return HttpResponse.json(respuestaLogin) }))
    montar()
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar Sesión' }))
    expect(llamadas).toBe(0)
    expect(screen.getByText('Introduce usuario y contraseña.')).toBeInTheDocument()
  })
  it('login correcto guarda la sesión y navega a /', async () => {
    server.use(http.post('*/api/auth/login', () => HttpResponse.json(respuestaLogin)))
    montar()
    await userEvent.type(screen.getByPlaceholderText('Usuario'), 'fati')
    await userEvent.type(screen.getByPlaceholderText('Contraseña'), 'secreta{enter}')
    await waitFor(() => expect(screen.getByText('INICIO')).toBeInTheDocument())
    expect(leerSesion()?.token).toBe('jwt-super')
  })
  it('401 muestra "Usuario o contraseña incorrectos." y no guarda sesión', async () => {
    server.use(http.post('*/api/auth/login', () => new HttpResponse(null, { status: 401 })))
    montar()
    await userEvent.type(screen.getByPlaceholderText('Usuario'), 'fati')
    await userEvent.type(screen.getByPlaceholderText('Contraseña'), 'mala')
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar Sesión' }))
    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument()
    expect(leerSesion()).toBeNull()
  })
  it('un fallo de red reintenta una vez y si vuelve a fallar muestra el mensaje de conexión', async () => {
    let intentos = 0
    server.use(http.post('*/api/auth/login', () => { intentos++; return HttpResponse.error() }))
    montar()
    await userEvent.type(screen.getByPlaceholderText('Usuario'), 'fati')
    await userEvent.type(screen.getByPlaceholderText('Contraseña'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar Sesión' }))
    expect(await screen.findByText(/Sin conexión con el servidor/)).toBeInTheDocument()
    expect(intentos).toBe(2)
  })
  it('el ojo alterna la visibilidad de la contraseña', async () => {
    montar()
    const pass = screen.getByPlaceholderText('Contraseña')
    expect(pass).toHaveAttribute('type', 'password')
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(pass).toHaveAttribute('type', 'text')
  })
})
```
Run: `npm test -- LoginPage` → FAIL (módulos inexistentes).

- [ ] **Step 3: SessionProvider**

`src/app/session/SessionProvider.tsx`:
```tsx
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { api } from '@/shared/api/client'
import { ConexionError, SesionExpiradaError } from '@/shared/api/errors'
import { rearmarSesionExpirada } from './expiracion'
import { borrarSesion, guardarSesion, leerSesion, type Sesion } from './storage'

export const MSG_CREDENCIALES = 'Usuario o contraseña incorrectos.'

type Ctx = {
  sesion: Sesion | null
  login: (usuario: string, password: string) => Promise<void>
  logout: () => void
}
const SessionContext = createContext<Ctx | null>(null)

async function pedirLogin(usuario: string, password: string) {
  const { data } = await api.POST('/api/auth/login', { body: { usuario, password } })
  if (!data) throw new ConexionError(0, 'Respuesta vacía del servidor.')
  return data
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(() => leerSesion())

  const login = useCallback(async (usuario: string, password: string) => {
    let data
    try {
      data = await pedirLogin(usuario, password)
    } catch (e) {
      // Un único reintento solo en el login, como el parche de conexión del JavaFX.
      if (!(e instanceof ConexionError)) throw traducir(e)
      try {
        data = await pedirLogin(usuario, password)
      } catch (e2) {
        throw traducir(e2)
      }
    }
    const s: Sesion = { idUsu: data.idUsu, nombreUsuario: data.nombreUsuario, rol: data.rol, idTec: data.idTec ?? null, token: data.token }
    guardarSesion(s)
    rearmarSesionExpirada()
    setSesion(s)
  }, [])

  const logout = useCallback(() => {
    borrarSesion()
    setSesion(null)
  }, [])

  const value = useMemo(() => ({ sesion, login, logout }), [sesion, login, logout])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

/** En el login, un 401 no es "sesión expirada" sino credenciales incorrectas. */
function traducir(e: unknown): Error {
  if (e instanceof SesionExpiradaError) return new Error(MSG_CREDENCIALES)
  return e instanceof Error ? e : new Error(String(e))
}

export function useSession(): Ctx {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession fuera de SessionProvider')
  return ctx
}
```
Nota: el 401 del login no dispara el hook de sesión expirada porque `dispararSesionExpirada` exige sesión guardada (test de la Task 4).

- [ ] **Step 4: LoginPage (calco de LoginView.fxml)**

`src/app/login/LoginPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { APP_VERSION } from '@/shared/lib/version'
import { useSession } from '@/app/session/SessionProvider'

const inputCls =
  'h-auto rounded-lg border-borde-input bg-white px-3.5 py-3 text-[13px] text-azul-medio placeholder:text-texto-suave'

export function LoginPage() {
  const { login } = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const [usuario, setUsuario] = useState('')
  const [password, setPassword] = useState('')
  const [verPassword, setVerPassword] = useState(false)
  // Mensaje pendiente (sesión expirada, dejado por main.tsx antes de recargar) o de una navegación interna.
  const [error, setError] = useState<string | null>(() => {
    const pendiente = sessionStorage.getItem('fsgr.mensajeLogin')
    if (pendiente) sessionStorage.removeItem('fsgr.mensajeLogin')
    return pendiente ?? (location.state as { mensaje?: string } | null)?.mensaje ?? null
  })
  const [enviando, setEnviando] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (usuario.trim() === '' || password === '') {
      setError('Introduce usuario y contraseña.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      await login(usuario.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error desconocido.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-fondo-login px-4">
      <form onSubmit={onSubmit} className="flex w-full max-w-[340px] flex-col items-center" noValidate>
        <img src="/logo_inicio_sesion.png" alt="" className="mb-3.5 h-[58px] w-[58px]" />
        <div className="mb-1 flex items-center gap-1.5">
          <span className="text-[22px] font-bold text-azul-medio">FSGR</span>
          <span className="pt-1 text-[11px] text-texto-suave">v{APP_VERSION}</span>
        </div>
        <div className="mb-8 text-center text-[20px] leading-tight text-azul-medio">
          <div>Gestión de Stock y</div>
          <div>Reparaciones</div>
        </div>
        <Input className={`mb-2.5 ${inputCls}`} placeholder="Usuario" value={usuario} onChange={(e) => setUsuario(e.target.value)} autoFocus autoComplete="username" />
        <div className="relative mb-1.5 w-full">
          <Input
            className={`pr-11 ${inputCls}`}
            placeholder="Contraseña"
            type={verPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
          <button
            type="button"
            aria-label={verPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            onClick={() => setVerPassword((v) => !v)}
            className="absolute top-1/2 right-3 -translate-y-1/2 cursor-pointer"
          >
            <img src={verPassword ? '/ojo_desactivar.png' : '/ojo_activar.png'} alt="" className="h-[18px] w-[18px]" />
          </button>
        </div>
        <p className={`mb-2.5 w-full text-[11px] text-texto-error ${error ? '' : 'invisible'}`} role="alert">
          {error ?? ' '}
        </p>
        <Button
          type="submit"
          disabled={enviando}
          className="mt-4.5 mb-3.5 h-auto w-full rounded-3xl bg-azul-noche py-3.5 text-[13px] font-bold text-crema hover:bg-azul-noche-hover"
        >
          Iniciar Sesión
        </Button>
      </form>
    </div>
  )
}
```

- [ ] **Step 5: Guard, rutas y arranque**

`src/app/session/RequireSesion.tsx`:
```tsx
import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from './SessionProvider'

export function RequireSesion() {
  const { sesion } = useSession()
  const location = useLocation()
  if (!sesion) return <Navigate to="/login" replace state={{ desde: location.pathname }} />
  return <Outlet />
}
```
`src/app/router.tsx` (versión provisional; la Task 6 mete `AppLayout`, la Task 8 `ClientesPage`):
```tsx
import { createBrowserRouter, Navigate } from 'react-router'
import { LoginPage } from './login/LoginPage'
import { RequireSesion } from './session/RequireSesion'

const Placeholder = ({ nombre }: { nombre: string }) => <p className="p-6">Pendiente de migrar: {nombre}</p>

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireSesion />,
    children: [
      { path: '/', element: <Navigate to="/reparaciones" replace /> },
      { path: '/reparaciones/*', element: <Placeholder nombre="Reparaciones" /> },
      { path: '/stock/*', element: <Placeholder nombre="Stock" /> },
      { path: '/estadisticas/*', element: <Placeholder nombre="Estadísticas" /> },
      { path: '/clientes', element: <Placeholder nombre="Clientes" /> },
    ],
  },
])
```
`src/main.tsx`:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import '@/shared/styles/globals.css'
import { router } from '@/app/router'
import { SessionProvider } from '@/app/session/SessionProvider'
import { onSesionExpirada } from '@/app/session/expiracion'
import { borrarSesion } from '@/app/session/storage'
import { MSG_SESION_EXPIRADA_UI } from '@/app/session/mensajes'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: true, staleTime: 0 } },
})

// 401 con sesión: se borra la sesión y se recarga la app en /login con el mensaje (equivale a volver al
// login del JavaFX, que descarta las vistas cacheadas). LoginPage lee y borra 'fsgr.mensajeLogin'.
onSesionExpirada(() => {
  borrarSesion()
  sessionStorage.setItem('fsgr.mensajeLogin', MSG_SESION_EXPIRADA_UI)
  window.location.assign('/login')
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </QueryClientProvider>
  </StrictMode>,
)
```
`src/app/session/mensajes.ts`:
```ts
export const MSG_SESION_EXPIRADA_UI = 'Tu sesión ha expirado. Inicia sesión de nuevo.'
```
Run: `npm test -- LoginPage` → PASS (6 tests). `npm run check` → verde. `npm run dev` con `.env.local` apuntando al servidor local: login real funciona y muestra "Pendiente de migrar: Reparaciones".

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(web): SessionProvider, LoginPage calcada del JavaFX y rutas con guard de sesión"
```

---

### Task 6: Shell — barra superior, menú de usuario, banner de conexión, refresco al volver

**Files:**
- Create: `src/app/shell/AppLayout.tsx`, `TopBar.tsx`, `UserMenu.tsx`, `ConnectionBanner.tsx`, `PendienteDeMigrar.tsx`, `exportable.tsx`, `src/app/shell/TopBar.test.tsx`, `src/app/shell/ConnectionBanner.test.tsx`
- Modify: `src/app/router.tsx`

**Interfaces:**
- Consumes: `useSession`, `esAdmin`, `esSuperTecnico`, `useConexion`, `APP_VERSION`, shadcn `dropdown-menu`.
- Produces: `<AppLayout/>` (layout route con `<Outlet/>`); `ExportableProvider` + `useRegistrarExportable(fn: (() => void) | null)` + `useExportable(): (() => void) | null`; `<PendienteDeMigrar nombre="..."/>`; helper `inicioDelRol(sesion): string` (`/reparaciones` para todos en este sub-proyecto).

- [ ] **Step 1: Tests del shell (fallan)**

`src/app/shell/TopBar.test.tsx`:
```tsx
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderConProviders, SESION_ADMIN, SESION_SUPER, SESION_TEC } from '@/test/render'
import { AppLayout } from './AppLayout'

describe('barra superior (calco de MainView)', () => {
  it('muestra título, versión, los 4 botones y el saludo para cualquier rol', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    expect(screen.getByText('FSGR:')).toBeInTheDocument()
    expect(screen.getByText(/Gestión de Stock y Reparaciones V\.\d+\.\d+\.\d+/)).toBeInTheDocument()
    for (const b of ['Reparaciones', 'Stock', 'Estadísticas', 'Clientes']) {
      expect(screen.getByRole('link', { name: b })).toBeInTheDocument()
    }
    expect(screen.getByText('Hola, zara')).toBeInTheDocument()
  })
  it('el menú de usuario de un técnico no tiene opciones de admin y "Descargar CSV" va deshabilitado', async () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    await userEvent.click(screen.getByRole('button', { name: /Hola, zara/ }))
    expect(screen.queryByText('Gestionar técnicos')).not.toBeInTheDocument()
    expect(screen.queryByText('Ver logs')).not.toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Descargar CSV' })).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('menuitem', { name: 'Cambiar contraseña' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Cerrar Sesión' })).toBeInTheDocument()
  })
  it('el admin ve además "Gestionar técnicos" y "Ver logs"', async () => {
    renderConProviders(<AppLayout />, { sesion: SESION_ADMIN })
    await userEvent.click(screen.getByRole('button', { name: /Hola, admin/ }))
    expect(screen.getByRole('menuitem', { name: 'Gestionar técnicos' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Ver logs' })).toBeInTheDocument()
  })
  it('la campana no se muestra en este sub-proyecto (ni al supertécnico)', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_SUPER })
    expect(screen.queryByRole('button', { name: /solicitudes/i })).not.toBeInTheDocument()
  })
  it('Cerrar Sesión borra la sesión y lleva al login', async () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC, rutas: <Route path="/login" element={<p>LOGIN</p>} /> })
    await userEvent.click(screen.getByRole('button', { name: /Hola, zara/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cerrar Sesión' }))
    expect(await screen.findByText('LOGIN')).toBeInTheDocument()
    expect(sessionStorage.getItem('fsgr.sesion')).toBeNull()
  })
})
```
(añadir `import { Route } from 'react-router'` arriba).

`src/app/shell/ConnectionBanner.test.tsx`:
```tsx
import { act, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { reportarExito, reportarFallo } from '@/shared/api/conexion'
import { renderConProviders, SESION_TEC } from '@/test/render'
import { AppLayout } from './AppLayout'

describe('banner de conexión', () => {
  beforeEach(() => reportarExito())
  it('oculto conectado; aparece al fallar y desaparece al recuperarse', () => {
    renderConProviders(<AppLayout />, { sesion: SESION_TEC })
    expect(screen.queryByText(/Sin conexión con el servidor/)).not.toBeInTheDocument()
    act(() => reportarFallo())
    expect(screen.getByText('⚠ Sin conexión con el servidor. Reintentando…')).toBeInTheDocument()
    act(() => reportarExito())
    expect(screen.queryByText(/Sin conexión con el servidor/)).not.toBeInTheDocument()
  })
})
```
Run: `npm test -- shell` → FAIL.

- [ ] **Step 2: Implementar el shell**

`src/app/shell/exportable.tsx`:
```tsx
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

type Exportador = (() => void) | null
const Ctx = createContext<{ exportador: Exportador; registrar: (e: Exportador) => void } | null>(null)

/** "Descargar CSV" del menú de usuario delega en la vista activa, como Exportable en el JavaFX. */
export function ExportableProvider({ children }: { children: ReactNode }) {
  const [exportador, registrar] = useState<Exportador>(null)
  return <Ctx.Provider value={{ exportador, registrar }}>{children}</Ctx.Provider>
}
export function useExportable(): Exportador {
  return useContext(Ctx)?.exportador ?? null
}
/** La vista que exporta llama a esto con su función; al desmontarse se desregistra. */
export function useRegistrarExportable(fn: Exportador) {
  const ctx = useContext(Ctx)
  useEffect(() => {
    ctx?.registrar(() => fn)
    return () => ctx?.registrar(null)
  }, [ctx, fn])
}
```
`src/app/shell/ConnectionBanner.tsx`:
```tsx
import { useConexion } from '@/shared/api/conexion'

export function ConnectionBanner() {
  const conectado = useConexion()
  if (conectado) return null
  return (
    <div role="status" className="bg-fila-solicitud-bg px-4 py-1.5 text-center text-[12px] font-bold text-fila-solicitud-brd">
      ⚠ Sin conexión con el servidor. Reintentando…
    </div>
  )
}
```
`src/app/shell/PendienteDeMigrar.tsx`:
```tsx
export function PendienteDeMigrar({ nombre }: { nombre: string }) {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-azul-medio">{nombre}</h1>
      <p className="mt-2 text-azul-gris">Pendiente de migrar desde el cliente JavaFX.</p>
    </div>
  )
}
```
`src/app/shell/UserMenu.tsx`:
```tsx
import { useNavigate } from 'react-router'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu'
import { useSession } from '@/app/session/SessionProvider'
import { esAdmin } from '@/app/session/storage'
import { useExportable } from './exportable'

export function UserMenu() {
  const { sesion, logout } = useSession()
  const navigate = useNavigate()
  const exportar = useExportable()
  if (!sesion) return null
  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1 hover:bg-white/8">
        <img src="/user.png" alt="" className="h-7 w-7" />
        <span className="text-[12px] font-bold text-crema">Hola, {sesion.nombreUsuario}</span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {esAdmin(sesion) && (
          <>
            <DropdownMenuItem onSelect={() => navigate('/gestion/tecnicos')}>Gestionar técnicos</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => navigate('/gestion/logs')}>Ver logs</DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem disabled={!exportar} onSelect={() => exportar?.()}>Descargar CSV</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => navigate('/cuenta/cambiar-password')}>Cambiar contraseña</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => { logout(); navigate('/login', { replace: true }) }}>Cerrar Sesión</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
```
`src/app/shell/TopBar.tsx`:
```tsx
import { NavLink } from 'react-router'
import { APP_VERSION } from '@/shared/lib/version'
import { cn } from '@/shared/lib/utils'
import { UserMenu } from './UserMenu'

const NAV = [
  { to: '/reparaciones', label: 'Reparaciones' },
  { to: '/stock', label: 'Stock' },
  { to: '/estadisticas', label: 'Estadísticas' },
  { to: '/clientes', label: 'Clientes' },
]

/** Calco de la navbar de MainView.fxml: navy 64 px, logo, título, 4 botones tipo píldora, usuario. */
export function TopBar() {
  return (
    <header className="flex h-navbar items-center gap-2.5 bg-azul-noche px-5">
      <NavLink to="/" className="rounded-lg px-2 py-1.5 hover:bg-white/8">
        <img src="/logoNavBar.png" alt="Inicio" className="h-[38px]" />
      </NavLink>
      <span className="text-[13px] font-bold text-crema">FSGR:</span>
      <span className="hidden text-[13px] text-crema md:inline">Gestión de Stock y Reparaciones V.{APP_VERSION}</span>
      <nav className="ml-2 flex items-center gap-0.5 rounded-3xl bg-nav-switch p-[3px]">
        {NAV.map((n) => (
          <NavLink
            key={n.to}
            to={n.to}
            className={({ isActive }) =>
              cn(
                'rounded-[20px] px-4 py-1.5 text-[12px] font-bold',
                isActive ? 'bg-azul-noche text-texto-nav-activo' : 'text-azul-medio hover:bg-azul-medio/8',
              )
            }
          >
            {n.label}
          </NavLink>
        ))}
      </nav>
      <div className="flex-1" />
      {/* Campana de solicitudes (solo SUPERTECNICO): llega con el sub-proyecto 2 */}
      <UserMenu />
    </header>
  )
}
```
`src/app/shell/AppLayout.tsx`:
```tsx
import { Outlet } from 'react-router'
import { ExportableProvider } from './exportable'
import { TopBar } from './TopBar'
import { ConnectionBanner } from './ConnectionBanner'

export function AppLayout() {
  return (
    <ExportableProvider>
      <div className="flex min-h-screen flex-col bg-fondo-vista">
        <TopBar />
        <ConnectionBanner />
        <main className="flex-1">
          <Outlet />
        </main>
      </div>
    </ExportableProvider>
  )
}
```
`src/app/router.tsx`: el hijo de `RequireSesion` pasa a ser `AppLayout` con las rutas dentro:
```tsx
import { createBrowserRouter, Navigate } from 'react-router'
import { LoginPage } from './login/LoginPage'
import { RequireSesion } from './session/RequireSesion'
import { AppLayout } from './shell/AppLayout'
import { PendienteDeMigrar } from './shell/PendienteDeMigrar'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireSesion />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <Navigate to="/reparaciones" replace /> },
          { path: '/reparaciones/*', element: <PendienteDeMigrar nombre="Reparaciones" /> },
          { path: '/stock/*', element: <PendienteDeMigrar nombre="Stock" /> },
          { path: '/estadisticas/*', element: <PendienteDeMigrar nombre="Estadísticas" /> },
          { path: '/clientes', element: <PendienteDeMigrar nombre="Clientes" /> },
          { path: '/gestion/tecnicos', element: <PendienteDeMigrar nombre="Gestionar técnicos" /> },
          { path: '/gestion/logs', element: <PendienteDeMigrar nombre="Ver logs" /> },
          { path: '/cuenta/cambiar-password', element: <PendienteDeMigrar nombre="Cambiar contraseña" /> },
        ],
      },
    ],
  },
])
```
Refresco al volver a la pestaña: lo da TanStack Query (`refetchOnWindowFocus: true` en `main.tsx`, que escucha `visibilitychange` y `focus`). No hace falta código propio.

Run: `npm test -- shell` → PASS (6 tests). `npm run check` verde. `npm run dev`: la barra se ve navy de 64 px con las píldoras; al cortar el servidor y navegar, aparece el banner.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(web): shell calcado de MainView (barra, menú de usuario por rol, banner de conexión) y rutas placeholder"
```

---

### Task 7: Componentes compartidos — DataTable, MultiSelect, StatusBadge, ConfirmDialog, AlertaProvider

**Files:**
- Create: `src/shared/ui/DataTable.tsx`, `MultiSelect.tsx` + `MultiSelect.test.tsx`, `StatusBadge.tsx`, `ConfirmDialog.tsx` + `ConfirmDialog.test.tsx`, `AlertaProvider.tsx` + `AlertaProvider.test.tsx`
- Modify: `src/main.tsx` (envolver con `AlertaProvider`), `src/test/render.tsx` (ídem)

**Interfaces:**
- Produces:
  - `DataTable<T>({ columns: ColumnDef<T, any>[]; data: T[]; vacio: string; filaClase?: (row: T) => string; menuFila?: (row: T) => ReactNode; getRowId?: (row: T) => string })` sobre TanStack Table; el menú contextual usa `context-menu` de shadcn.
  - `MultiSelect<T>({ opciones: T[]; clave: (o: T) => string; etiqueta: (o: T) => string; seleccion: Set<string>; onChange: (s: Set<string>) => void; textoVacio: string; textoPlural: (n: number) => string })` y helper puro `textoMultiSelect(seleccion: string[], textoVacio, textoPlural): string`.
  - `StatusBadge({ activo: boolean })` → "Activo" / "Inactivo".
  - `ConfirmDialog({ abierto, titulo, descripcion, textoAccion, textoCancelar?, onConfirmar, onCancelar })`.
  - `AlertaProvider` + `useAlerta(): { mostrarError(msg: string): void }` (diálogo modal con "Aceptar", equivalente a `Alertas.mostrarError`).

- [ ] **Step 1: Tests (fallan)**

`src/shared/ui/MultiSelect.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { MultiSelect, textoMultiSelect } from './MultiSelect'

describe('textoMultiSelect (calco de actualizarTextoFiltro)', () => {
  it('vacío, uno y varios', () => {
    expect(textoMultiSelect([], 'Cliente', (n) => `${n} clientes`)).toBe('Cliente')
    expect(textoMultiSelect(['WEB'], 'Cliente', (n) => `${n} clientes`)).toBe('WEB')
    expect(textoMultiSelect(['WEB', 'OTRO'], 'Cliente', (n) => `${n} clientes`)).toBe('2 clientes')
  })
})

function Demo() {
  const [sel, setSel] = useState<Set<string>>(new Set())
  return (
    <MultiSelect
      opciones={[{ n: 'WEB' }, { n: 'OTRO' }]}
      clave={(o) => o.n}
      etiqueta={(o) => o.n}
      seleccion={sel}
      onChange={setSel}
      textoVacio="Cliente"
      textoPlural={(k) => `${k} clientes`}
    />
  )
}

describe('MultiSelect', () => {
  it('marca y desmarca con checkboxes y actualiza la etiqueta', async () => {
    render(<Demo />)
    await userEvent.click(screen.getByRole('button', { name: 'Cliente' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'WEB' }))
    expect(screen.getByRole('button', { name: 'WEB' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'OTRO' }))
    expect(screen.getByRole('button', { name: '2 clientes' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'WEB' }))
    expect(screen.getByRole('button', { name: 'OTRO' })).toBeInTheDocument()
  })
})
```
`src/shared/ui/ConfirmDialog.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog (calco de ConfirmDialog.mostrar)', () => {
  it('muestra título, descripción y botones; confirma y cancela', async () => {
    const ok = vi.fn()
    const no = vi.fn()
    render(
      <ConfirmDialog abierto titulo="Borrar cliente" descripcion='¿Seguro que quieres borrar el cliente "WEB"? Esta acción no se puede deshacer.' textoAccion="Borrar" onConfirmar={ok} onCancelar={no} />,
    )
    expect(screen.getByRole('dialog', { name: 'Borrar cliente' })).toBeInTheDocument()
    expect(screen.getByText(/no se puede deshacer/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(no).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Borrar' }))
    expect(ok).toHaveBeenCalledTimes(1)
  })
})
```
`src/shared/ui/AlertaProvider.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AlertaProvider, useAlerta } from './AlertaProvider'

function Demo() {
  const { mostrarError } = useAlerta()
  return <button onClick={() => mostrarError('El cliente fue modificado por otro usuario. Se recargan los datos.')}>boom</button>
}

describe('AlertaProvider (calco de Alertas.mostrarError)', () => {
  it('abre un diálogo con el mensaje y se cierra con Aceptar', async () => {
    render(<AlertaProvider><Demo /></AlertaProvider>)
    await userEvent.click(screen.getByText('boom'))
    expect(screen.getByRole('dialog', { name: 'Error' })).toBeInTheDocument()
    expect(screen.getByText(/modificado por otro usuario/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
```
Run: `npm test -- ui` → FAIL.

- [ ] **Step 2: Implementar**

```bash
npm install @tanstack/react-table
```
`src/shared/ui/StatusBadge.tsx`:
```tsx
import { cn } from '@/shared/lib/utils'

/** Badge "Activo"/"Inactivo" con los colores de ClientesController.configurarTabla. */
export function StatusBadge({ activo }: { activo: boolean }) {
  return (
    <span
      className={cn(
        'inline-block rounded-[10px] px-2.5 py-0.5 text-[11px] font-bold',
        activo ? 'bg-fila-reparado-bg text-fila-reparado-ico' : 'bg-fila-cancelado-bg text-fila-cancelado-text',
      )}
    >
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}
```
`src/shared/ui/MultiSelect.tsx`:
```tsx
import { Popover, PopoverContent, PopoverTrigger } from './popover'
import { Checkbox } from './checkbox'
import { cn } from '@/shared/lib/utils'

export function textoMultiSelect(seleccion: string[], textoVacio: string, textoPlural: (n: number) => string) {
  if (seleccion.length === 0) return textoVacio
  if (seleccion.length === 1) return seleccion[0]
  return textoPlural(seleccion.length)
}

type Props<T> = {
  opciones: T[]
  clave: (o: T) => string
  etiqueta: (o: T) => string
  seleccion: Set<string>
  onChange: (s: Set<string>) => void
  textoVacio: string
  textoPlural: (n: number) => string
  className?: string
}

/** Desplegable con checkboxes, calco de MultiSelectDropdown: la etiqueta del botón resume la selección. */
export function MultiSelect<T>({ opciones, clave, etiqueta, seleccion, onChange, textoVacio, textoPlural, className }: Props<T>) {
  const texto = textoMultiSelect([...seleccion], textoVacio, textoPlural)
  function toggle(k: string, marcado: boolean) {
    const s = new Set(seleccion)
    if (marcado) s.add(k)
    else s.delete(k)
    onChange(s)
  }
  return (
    <Popover>
      <PopoverTrigger
        className={cn('h-8 min-w-36 rounded border border-azul-gris bg-white px-3 text-left text-[12px] text-azul-medio', className)}
      >
        {texto}
      </PopoverTrigger>
      <PopoverContent align="start" className="max-h-72 w-56 overflow-auto p-2">
        {opciones.map((o) => {
          const k = clave(o)
          const id = `ms-${k}`
          return (
            <label key={k} htmlFor={id} className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-[12px] hover:bg-pill-bg">
              <Checkbox id={id} checked={seleccion.has(k)} onCheckedChange={(v) => toggle(k, v === true)} aria-label={etiqueta(o)} />
              {etiqueta(o)}
            </label>
          )
        })}
      </PopoverContent>
    </Popover>
  )
}
```
`src/shared/ui/ConfirmDialog.tsx`:
```tsx
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './dialog'
import { Button } from './button'

type Props = {
  abierto: boolean
  titulo: string
  descripcion: string
  textoAccion: string
  textoCancelar?: string
  onConfirmar: () => void
  onCancelar: () => void
}

/** Calco de ConfirmDialog.mostrar(titulo, descripcion, textoAccion, "Cancelar", onConfirm). */
export function ConfirmDialog({ abierto, titulo, descripcion, textoAccion, textoCancelar = 'Cancelar', onConfirmar, onCancelar }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>{descripcion}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onCancelar}>{textoCancelar}</Button>
          <Button className="bg-rojo-accion text-white hover:bg-rojo-accion/90" onClick={onConfirmar}>{textoAccion}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```
`src/shared/ui/AlertaProvider.tsx`:
```tsx
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './dialog'
import { Button } from './button'

const Ctx = createContext<{ mostrarError: (msg: string) => void } | null>(null)

/** Equivalente a Alertas.mostrarError: diálogo modal con el mensaje y "Aceptar". */
export function AlertaProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null)
  const mostrarError = useCallback((m: string) => setMsg(m), [])
  const value = useMemo(() => ({ mostrarError }), [mostrarError])
  return (
    <Ctx.Provider value={value}>
      {children}
      <Dialog open={msg !== null} onOpenChange={(o) => !o && setMsg(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Error</DialogTitle>
            <DialogDescription>{msg}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button onClick={() => setMsg(null)}>Aceptar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Ctx.Provider>
  )
}
export function useAlerta() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useAlerta fuera de AlertaProvider')
  return ctx
}
```
`src/shared/ui/DataTable.tsx`:
```tsx
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from '@tanstack/react-table'
import type { ReactNode } from 'react'
import { ContextMenu, ContextMenuContent, ContextMenuTrigger } from './context-menu'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './table'
import { cn } from '@/shared/lib/utils'

type Props<T> = {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  columns: ColumnDef<T, any>[]
  data: T[]
  vacio: string
  filaClase?: (row: T) => string
  /** Si se da, cada fila abre este menú con clic derecho (equivalente al ContextMenu de TableView). */
  menuFila?: (row: T) => ReactNode
  getRowId?: (row: T) => string
}

export function DataTable<T>({ columns, data, vacio, filaClase, menuFila, getRowId }: Props<T>) {
  const table = useReactTable({ data, columns, getCoreRowModel: getCoreRowModel(), getRowId })
  return (
    <div className="overflow-x-auto rounded-md bg-white">
      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((hg) => (
            <TableRow key={hg.id}>
              {hg.headers.map((h) => (
                <TableHead key={h.id} className="text-[12px] font-bold text-azul-medio">
                  {h.isPlaceholder ? null : flexRender(h.column.columnDef.header, h.getContext())}
                </TableHead>
              ))}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={columns.length} className="py-8 text-center text-azul-gris">{vacio}</TableCell>
            </TableRow>
          )}
          {table.getRowModel().rows.map((row) => {
            const tr = (
              <TableRow key={row.id} className={cn('border-b border-fila-sep', filaClase?.(row.original))}>
                {row.getVisibleCells().map((c) => (
                  <TableCell key={c.id} className="text-[12px]">{flexRender(c.column.columnDef.cell, c.getContext())}</TableCell>
                ))}
              </TableRow>
            )
            if (!menuFila) return tr
            return (
              <ContextMenu key={row.id}>
                <ContextMenuTrigger asChild>{tr}</ContextMenuTrigger>
                <ContextMenuContent>{menuFila(row.original)}</ContextMenuContent>
              </ContextMenu>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}
```
`src/main.tsx` y `src/test/render.tsx`: envolver el contenido con `<AlertaProvider>` (dentro de `SessionProvider`).

Run: `npm test -- ui` → PASS (4 tests). `npm run check` verde.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(web): DataTable, MultiSelect, StatusBadge, ConfirmDialog y AlertaProvider calcados de los utils del JavaFX"
```

---

### Task 8: Fichas de paridad (shell y Clientes) y módulo Clientes

**Files:**
- Create: `docs/paridad/README.md`, `docs/paridad/shell.md`, `docs/paridad/clientes.md`, `src/modules/gestion/clientes/api.ts`, `ClientesPage.tsx`, `ClienteDialog.tsx`, `ClientesPage.test.tsx`
- Modify: `src/app/router.tsx`

**Interfaces:**
- Consumes: `api`, `Cliente`, `useAlerta`, `useSession`, `esSuperTecnico`, `DataTable`, `MultiSelect`, `StatusBadge`, `ConfirmDialog`, `Button`, `Dialog`, `Input`, `Label`, `ContextMenuItem`.
- Produces: `useClientes()`, `useCrearCliente()`, `useEditarCliente()`, `useSetActivoCliente()`, `useBorrarCliente()`, `tieneTelefonos(idCli): Promise<boolean>`; ruta `/clientes` → `ClientesPage`.

- [ ] **Step 1: Fichas de paridad (se escriben ANTES del código y se cierran con el usuario)**

`docs/paridad/README.md`:
```markdown
# Fichas de paridad

Una ficha por vista del cliente JavaFX. Se escribe antes de construir la vista web y es su criterio de aceptación:
la vista no se da por hecha hasta cumplir cada punto. Las capturas de referencia del JavaFX (con datos reales)
viven fuera del repo en `Apuntes/paridad-capturas/<vista>/` y se citan por nombre.
Plantilla: roles · sub-vistas · columnas · filtros · acciones y confirmaciones · badges y colores (token) ·
textos · CSV · refresco · reglas de negocio (spec de origen y dónde viven) · diferencias inevitables aceptadas.
```
`docs/paridad/shell.md`:
```markdown
# Ficha de paridad — Shell (MainView.fxml / MainController)

Capturas: `Apuntes/paridad-capturas/shell/{navbar-tecnico,navbar-supertecnico,menu-usuario-admin,banner-conexion}.png`.

- [ ] Barra superior navy `--color-azul-noche`, 64 px, padding lateral 20 px.
- [ ] Logo (`logoNavBar.png`, 38 px de alto) clicable → panel inicial del rol (`/reparaciones`).
- [ ] "FSGR:" en negrita 13 px crema + "Gestión de Stock y Reparaciones V.<versión>" normal 13 px crema.
- [ ] Píldora de navegación `--color-nav-switch` radio 24, con 4 botones: Reparaciones, Stock, Estadísticas, Clientes; botón activo navy con texto `#FAFAFA`; 12 px negrita; hover azul medio al 8 %.
- [ ] Los 4 botones visibles para los 3 roles.
- [ ] Campana con badge: solo SUPERTECNICO. **No en este sub-proyecto** (llega con Formulario/notificaciones).
- [ ] Botón de usuario: icono `user.png` 28 px + "Hola, <usuario>" 12 px negrita crema; hover blanco al 8 %.
- [ ] Menú de usuario: [ADMIN: "Gestionar técnicos", "Ver logs", separador] "Descargar CSV" (deshabilitado si la vista no exporta), "Cambiar contraseña", separador, "Cerrar Sesión".
- [ ] "Cerrar Sesión": borra la sesión, limpia caché de datos, vuelve a `/login`.
- [ ] Banner bajo la barra "⚠ Sin conexión con el servidor. Reintentando…" cuando falla la red o hay 5xx; se autocura; mientras está, el refresco pasa a 5 s.
- [ ] 401 con sesión → `/login` con "Tu sesión ha expirado. Inicia sesión de nuevo." (una sola vez).
- [ ] Fondo del área de contenido `--color-fondo-vista`.
- [ ] Refresco al volver a la pestaña (equivale a la recarga al recuperar foco).
- Diferencias aceptadas: la campana pulsante y las ventanas secundarias (logs, gestionar técnicos) pasan a rutas/modales; el título de ventana es la pestaña del navegador ("FSGR").
```
`docs/paridad/clientes.md`:
```markdown
# Ficha de paridad — Clientes (ClientesView.fxml / ClientesController, spec 2026-06-23-clientes-design.md)

Capturas: `Apuntes/paridad-capturas/clientes/{lista-supertecnico,lista-tecnico,menu-contextual,nuevo-cliente,editar-cliente,borrar-cliente}.png`.

- [ ] Roles: la ven los tres. Solo SUPERTECNICO escribe (`soloLectura = !esSuperTecnico`): sin "Nuevo cliente" ni menú contextual para TECNICO y ADMIN. El servidor ya protege con `hasRole('SUPERTECNICO')`.
- [ ] Título "Clientes" (24 px negrita azul medio) en la cabecera de la vista.
- [ ] Filtro multiselección por nombre: lista SOLO clientes activos; etiqueta "Cliente" / nombre único / "N clientes"; sin selección se muestran todos los clientes (activos e inactivos); con selección, solo los nombres marcados.
- [ ] Botón "Nuevo cliente" (`btn-primary`: navy, radio 24, 12 px negrita) solo SUPERTECNICO.
- [ ] Tabla: columnas "Nombre" y "Estado"; orden = el que devuelve `GET /api/clientes`; sin ordenación por clic; columnas no reordenables.
- [ ] Estado como badge: "Activo" (`--color-fila-reparado-bg` / texto `--color-fila-reparado-ico`) o "Inactivo" (`--color-fila-cancelado-bg` / texto `--color-fila-cancelado-text`); radio 10, 11 px negrita.
- [ ] Filas: borde izquierdo de 8 px verde `--color-fila-reparado-brd` si activo, transparente si inactivo; separador inferior `--color-fila-sep`; fila seleccionada fondo `--color-azul-medio`.
- [ ] Placeholder "Sin clientes".
- [ ] Menú contextual (clic derecho, SUPERTECNICO): "Desactivar"/"Activar" según estado, "Editar", y "Borrar" solo si `GET /api/clientes/{id}/tiene-telefonos` devuelve false (se consulta al abrir el menú).
- [ ] "Nuevo cliente": diálogo título "Nuevo cliente", campo "Nombre del cliente:"; nombre recortado; vacío = no hace nada; `POST /api/clientes {nombre}` y recarga.
- [ ] "Editar": diálogo título "Editar cliente", campo "Nombre:" precargado; si queda igual o vacío no llama; `PUT /api/clientes/{id} {nombre, updatedAt}` y recarga.
- [ ] "Activar"/"Desactivar": `PATCH /api/clientes/{id}/activo {activo, updatedAt}` directo, sin confirmación, y recarga.
- [ ] "Borrar": ConfirmDialog "Borrar cliente" / "¿Seguro que quieres borrar el cliente "<nombre>"? Esta acción no se puede deshacer." / botones "Borrar" y "Cancelar"; `DELETE /api/clientes/{id}` y recarga.
- [ ] 409 en editar o activar: alerta "El cliente fue modificado por otro usuario. Se recargan los datos." y recarga. 409 en borrar (teléfonos asociados): alerta con el mensaje del servidor y recarga. Cualquier otro error: alerta con su mensaje.
- [ ] Sin poller. Recarga tras cada escritura y al volver a la pestaña.
- [ ] No exportable: "Descargar CSV" deshabilitado en esta vista.
- Diferencias aceptadas: el menú contextual del JavaFX se abre al seleccionar fila; en web se abre con clic derecho sobre la fila (misma lista de opciones). Los `TextInputDialog` pasan a un diálogo modal con el mismo título y etiqueta.
```
**Checkpoint con el usuario:** enviar las dos fichas, pedir las capturas (se guardan en `Apuntes/paridad-capturas/`) y no seguir hasta su OK.

- [ ] **Step 2: Test de ClientesPage (falla)**

`src/modules/gestion/clientes/ClientesPage.test.tsx`:
```tsx
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/test/server'
import { renderConProviders, SESION_SUPER, SESION_TEC } from '@/test/render'
import { ClientesPage } from './ClientesPage'

const clientes = [
  { idCli: 1, nombre: 'WEB', activo: true, updatedAt: '2026-09-01T10:00:00' },
  { idCli: 2, nombre: 'OTRO', activo: true, updatedAt: '2026-09-01T10:00:00' },
  { idCli: 3, nombre: 'Antiguo', activo: false, updatedAt: '2026-09-01T10:00:00' },
]

beforeEach(() => {
  server.use(
    http.get('*/api/clientes', () => HttpResponse.json(clientes)),
    http.get('*/api/clientes/:id/tiene-telefonos', ({ params }) => HttpResponse.json({ value: params.id === '1' })),
  )
})

describe('ClientesPage', () => {
  it('lista nombre y estado, y el filtro solo ofrece activos', async () => {
    renderConProviders(<ClientesPage />, { sesion: SESION_SUPER })
    expect(await screen.findByText('Antiguo')).toBeInTheDocument()
    expect(screen.getAllByText('Activo')).toHaveLength(2)
    expect(screen.getByText('Inactivo')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cliente' }))
    expect(screen.getByRole('checkbox', { name: 'WEB' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Antiguo' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'WEB' }))
    await userEvent.keyboard('{Escape}')
    const tabla = within(screen.getByRole('table'))
    expect(tabla.queryByText('OTRO')).not.toBeInTheDocument()
    expect(tabla.queryByText('Antiguo')).not.toBeInTheDocument()
    expect(tabla.getByText('WEB')).toBeInTheDocument()
  })
  it('un técnico no ve "Nuevo cliente" ni menú contextual', async () => {
    renderConProviders(<ClientesPage />, { sesion: SESION_TEC })
    await screen.findByText('WEB')
    expect(screen.queryByRole('button', { name: 'Nuevo cliente' })).not.toBeInTheDocument()
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('WEB') })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
  it('crea un cliente con el diálogo "Nuevo cliente" y recarga', async () => {
    let creado: unknown = null
    server.use(http.post('*/api/clientes', async ({ request }) => { creado = await request.json(); return new HttpResponse(null, { status: 201 }) }))
    renderConProviders(<ClientesPage />, { sesion: SESION_SUPER })
    await screen.findByText('WEB')
    await userEvent.click(screen.getByRole('button', { name: 'Nuevo cliente' }))
    const dlg = screen.getByRole('dialog', { name: 'Nuevo cliente' })
    await userEvent.type(within(dlg).getByLabelText('Nombre del cliente:'), '  Amazon  ')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Aceptar' }))
    await waitFor(() => expect(creado).toEqual({ nombre: 'Amazon' }))
  })
  it('el menú contextual ofrece Desactivar/Editar y Borrar solo si no tiene teléfonos', async () => {
    renderConProviders(<ClientesPage />, { sesion: SESION_SUPER })
    await screen.findByText('WEB')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('WEB') })
    expect(await screen.findByRole('menuitem', { name: 'Desactivar' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Editar' })).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('menuitem', { name: 'Borrar' })).not.toBeInTheDocument())
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('OTRO') })
    expect(await screen.findByRole('menuitem', { name: 'Borrar' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Desactivar' })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('Antiguo') })
    expect(await screen.findByRole('menuitem', { name: 'Activar' })).toBeInTheDocument()
  })
  it('editar envía nombre y updatedAt; un 409 muestra el aviso y recarga', async () => {
    let body: unknown = null
    server.use(http.put('*/api/clientes/2', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 409 }) }))
    renderConProviders(<ClientesPage />, { sesion: SESION_SUPER })
    await screen.findByText('OTRO')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('OTRO') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Editar' }))
    const dlg = screen.getByRole('dialog', { name: 'Editar cliente' })
    const input = within(dlg).getByLabelText('Nombre:')
    expect(input).toHaveValue('OTRO')
    await userEvent.clear(input)
    await userEvent.type(input, 'Otros')
    await userEvent.click(within(dlg).getByRole('button', { name: 'Aceptar' }))
    expect(await screen.findByText('El cliente fue modificado por otro usuario. Se recargan los datos.')).toBeInTheDocument()
    expect(body).toEqual({ nombre: 'Otros', updatedAt: '2026-09-01T10:00:00' })
  })
  it('borrar pide confirmación con el texto exacto y llama a DELETE', async () => {
    let borrado = false
    server.use(http.delete('*/api/clientes/2', () => { borrado = true; return new HttpResponse(null, { status: 204 }) }))
    renderConProviders(<ClientesPage />, { sesion: SESION_SUPER })
    await screen.findByText('OTRO')
    await userEvent.pointer({ keys: '[MouseRight]', target: screen.getByText('OTRO') })
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Borrar' }))
    expect(screen.getByRole('dialog', { name: 'Borrar cliente' })).toBeInTheDocument()
    expect(screen.getByText('¿Seguro que quieres borrar el cliente "OTRO"? Esta acción no se puede deshacer.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(borrado).toBe(true))
  })
  it('sin clientes muestra el placeholder', async () => {
    server.use(http.get('*/api/clientes', () => HttpResponse.json([])))
    renderConProviders(<ClientesPage />, { sesion: SESION_TEC })
    expect(await screen.findByText('Sin clientes')).toBeInTheDocument()
  })
})
```
Run: `npm test -- Clientes` → FAIL.

- [ ] **Step 3: Hooks de API**

`src/modules/gestion/clientes/api.ts`:
```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Cliente } from '@/shared/api/client'

export const CLAVE_CLIENTES = ['clientes'] as const

export function useClientes() {
  return useQuery({
    queryKey: CLAVE_CLIENTES,
    queryFn: async () => (await api.GET('/api/clientes')).data ?? [],
  })
}

export async function tieneTelefonos(idCli: number): Promise<boolean> {
  const { data } = await api.GET('/api/clientes/{idCli}/tiene-telefonos', { params: { path: { idCli } } })
  return data?.value ?? false
}

function useRecarga() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: CLAVE_CLIENTES })
}

export function useCrearCliente() {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: (nombre: string) => api.POST('/api/clientes', { body: { nombre } }),
    onSettled: recargar,
  })
}
export function useEditarCliente() {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: (c: Pick<Cliente, 'idCli' | 'nombre' | 'updatedAt'>) =>
      api.PUT('/api/clientes/{idCli}', { params: { path: { idCli: c.idCli } }, body: { nombre: c.nombre, updatedAt: c.updatedAt } }),
    onSettled: recargar,
  })
}
export function useSetActivoCliente() {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: (c: Pick<Cliente, 'idCli' | 'activo' | 'updatedAt'>) =>
      api.PATCH('/api/clientes/{idCli}/activo', { params: { path: { idCli: c.idCli } }, body: { activo: c.activo, updatedAt: c.updatedAt } }),
    onSettled: recargar,
  })
}
export function useBorrarCliente() {
  const recargar = useRecarga()
  return useMutation({
    mutationFn: (idCli: number) => api.DELETE('/api/clientes/{idCli}', { params: { path: { idCli } } }),
    onSettled: recargar,
  })
}
```
Si `schema.d.ts` tipa `idCli`, `nombre`, `activo` o `updatedAt` como opcionales (springdoc no marca `required` en records sin `@Schema(requiredMode=REQUIRED)`), definir en `client.ts` `export type Cliente = Required<components['schemas']['Cliente']>` y en el servidor, en el sub-proyecto siguiente, anotar los modelos; para Clientes basta el `Required<>`.

- [ ] **Step 4: Diálogo y página**

`src/modules/gestion/clientes/ClienteDialog.tsx`:
```tsx
import { useEffect, useState } from 'react'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

type Props = {
  abierto: boolean
  titulo: 'Nuevo cliente' | 'Editar cliente'
  etiqueta: 'Nombre del cliente:' | 'Nombre:'
  valorInicial?: string
  onAceptar: (nombre: string) => void
  onCancelar: () => void
}

/** Calco de los TextInputDialog de ClientesController. Acepta con Enter; nombre recortado; vacío no hace nada. */
export function ClienteDialog({ abierto, titulo, etiqueta, valorInicial = '', onAceptar, onCancelar }: Props) {
  const [nombre, setNombre] = useState(valorInicial)
  useEffect(() => {
    if (abierto) setNombre(valorInicial)
  }, [abierto, valorInicial])
  function aceptar() {
    const n = nombre.trim()
    if (n === '') return
    onAceptar(n)
  }
  return (
    <Dialog open={abierto} onOpenChange={(o) => !o && onCancelar()}>
      <DialogContent>
        <form onSubmit={(e) => { e.preventDefault(); aceptar() }}>
          <DialogHeader>
            <DialogTitle>{titulo}</DialogTitle>
          </DialogHeader>
          <div className="my-4 flex items-center gap-3">
            <Label htmlFor="cliente-nombre">{etiqueta}</Label>
            <Input id="cliente-nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} autoFocus />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onCancelar}>Cancelar</Button>
            <Button type="submit">Aceptar</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
```
`src/modules/gestion/clientes/ClientesPage.tsx`:
```tsx
import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { useSession } from '@/app/session/SessionProvider'
import { esSuperTecnico } from '@/app/session/storage'
import type { Cliente } from '@/shared/api/client'
import { StaleDataError } from '@/shared/api/errors'
import { useAlerta } from '@/shared/ui/AlertaProvider'
import { Button } from '@/shared/ui/button'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { ContextMenuItem } from '@/shared/ui/context-menu'
import { DataTable } from '@/shared/ui/DataTable'
import { MultiSelect } from '@/shared/ui/MultiSelect'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { ClienteDialog } from './ClienteDialog'
import { tieneTelefonos, useBorrarCliente, useClientes, useCrearCliente, useEditarCliente, useSetActivoCliente } from './api'

const MSG_MODIFICADO = 'El cliente fue modificado por otro usuario. Se recargan los datos.'

const columnas: ColumnDef<Cliente>[] = [
  { accessorKey: 'nombre', header: 'Nombre' },
  { accessorKey: 'activo', header: 'Estado', cell: ({ row }) => <StatusBadge activo={row.original.activo} /> },
]

type Dialogo = { tipo: 'nuevo' } | { tipo: 'editar'; cliente: Cliente } | { tipo: 'borrar'; cliente: Cliente } | null

/** Contenido del menú contextual. Radix solo lo monta al abrirse, así que el useEffect equivale a la consulta
 *  tiene-telefonos que el JavaFX hace al seleccionar la fila: "Borrar" aparece solo si no tiene teléfonos. */
function MenuCliente({ c, onToggle, onEditar, onBorrar }: { c: Cliente; onToggle: () => void; onEditar: () => void; onBorrar: () => void }) {
  const { mostrarError } = useAlerta()
  const [borrable, setBorrable] = useState(false)
  useEffect(() => {
    let vivo = true
    tieneTelefonos(c.idCli)
      .then((tiene) => { if (vivo) setBorrable(!tiene) })
      .catch((e: unknown) => mostrarError(e instanceof Error ? e.message : String(e)))
    return () => { vivo = false }
  }, [c.idCli, mostrarError])
  return (
    <>
      <ContextMenuItem onSelect={onToggle}>{c.activo ? 'Desactivar' : 'Activar'}</ContextMenuItem>
      <ContextMenuItem onSelect={onEditar}>Editar</ContextMenuItem>
      {borrable && <ContextMenuItem onSelect={onBorrar}>Borrar</ContextMenuItem>}
    </>
  )
}

export function ClientesPage() {
  const { sesion } = useSession()
  const puedeEditar = esSuperTecnico(sesion)
  const { mostrarError } = useAlerta()
  const { data: clientes = [] } = useClientes()
  const crear = useCrearCliente()
  const editar = useEditarCliente()
  const setActivo = useSetActivoCliente()
  const borrar = useBorrarCliente()
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set())
  const [dialogo, setDialogo] = useState<Dialogo>(null)

  const activos = useMemo(() => clientes.filter((c) => c.activo), [clientes])
  const visibles = useMemo(
    () => (seleccion.size === 0 ? clientes : clientes.filter((c) => seleccion.has(c.nombre))),
    [clientes, seleccion],
  )

  /** Crear y borrar muestran el mensaje recibido (p. ej. el 409 "tiene teléfonos asociados" del servidor). */
  function tratarError(e: unknown) {
    mostrarError(e instanceof Error ? e.message : String(e))
  }
  /** Editar y activar: cualquier 409 es el aviso de modificado por otro usuario, como en el JavaFX. */
  function tratarErrorEdicion(e: unknown) {
    mostrarError(e instanceof StaleDataError ? MSG_MODIFICADO : e instanceof Error ? e.message : String(e))
  }

  return (
    <div className="p-6">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-azul-medio">Clientes</h1>
        <MultiSelect
          opciones={activos}
          clave={(c) => c.nombre}
          etiqueta={(c) => c.nombre}
          seleccion={seleccion}
          onChange={setSeleccion}
          textoVacio="Cliente"
          textoPlural={(n) => `${n} clientes`}
        />
        <div className="flex-1" />
        {puedeEditar && (
          <Button className="rounded-3xl bg-azul-noche px-4 text-[12px] font-bold text-white hover:bg-azul-noche-hover" onClick={() => setDialogo({ tipo: 'nuevo' })}>
            Nuevo cliente
          </Button>
        )}
      </div>

      <DataTable
        columns={columnas}
        data={visibles}
        vacio="Sin clientes"
        getRowId={(c) => String(c.idCli)}
        filaClase={(c) => (c.activo ? 'border-l-8 border-l-fila-reparado-brd' : 'border-l-8 border-l-transparent')}
        menuFila={
          puedeEditar
            ? (c) => (
                <MenuCliente
                  c={c}
                  onToggle={() => setActivo.mutate({ idCli: c.idCli, activo: !c.activo, updatedAt: c.updatedAt }, { onError: tratarErrorEdicion })}
                  onEditar={() => setDialogo({ tipo: 'editar', cliente: c })}
                  onBorrar={() => setDialogo({ tipo: 'borrar', cliente: c })}
                />
              )
            : undefined
        }
      />

      <ClienteDialog
        abierto={dialogo?.tipo === 'nuevo'}
        titulo="Nuevo cliente"
        etiqueta="Nombre del cliente:"
        onCancelar={() => setDialogo(null)}
        onAceptar={(nombre) => { setDialogo(null); crear.mutate(nombre, { onError: tratarError }) }}
      />
      <ClienteDialog
        abierto={dialogo?.tipo === 'editar'}
        titulo="Editar cliente"
        etiqueta="Nombre:"
        valorInicial={dialogo?.tipo === 'editar' ? dialogo.cliente.nombre : ''}
        onCancelar={() => setDialogo(null)}
        onAceptar={(nombre) => {
          if (dialogo?.tipo !== 'editar') return
          const c = dialogo.cliente
          setDialogo(null)
          if (nombre === c.nombre) return
          editar.mutate({ idCli: c.idCli, nombre, updatedAt: c.updatedAt }, { onError: tratarErrorEdicion })
        }}
      />
      <ConfirmDialog
        abierto={dialogo?.tipo === 'borrar'}
        titulo="Borrar cliente"
        descripcion={dialogo?.tipo === 'borrar' ? `¿Seguro que quieres borrar el cliente "${dialogo.cliente.nombre}"? Esta acción no se puede deshacer.` : ''}
        textoAccion="Borrar"
        onCancelar={() => setDialogo(null)}
        onConfirmar={() => {
          if (dialogo?.tipo !== 'borrar') return
          const id = dialogo.cliente.idCli
          setDialogo(null)
          borrar.mutate(id, { onError: tratarError })
        }}
      />
    </div>
  )
}
```
Imports de `ClientesPage.tsx`: añadir `useEffect` al import de React.

`src/app/router.tsx`: sustituir `{ path: '/clientes', element: <PendienteDeMigrar nombre="Clientes" /> }` por `{ path: '/clientes', element: <ClientesPage /> }` con su import.

Run: `npm test -- Clientes` → PASS (7 tests). `npm run check` verde. `npm run dev` contra el servidor local: crear, editar, activar/desactivar, borrar; abrir dos pestañas y editar el mismo cliente en las dos → la segunda recibe el aviso de modificado.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(web): modulo Clientes con paridad (filtro por nombre, menu contextual, dialogos, 409) y fichas de paridad shell/clientes"
```

---

### Task 9: Dockerfile de la web, nginx y compose de producción (ficheros versionados)

**Files:**
- Create (repo web): `Dockerfile`, `.dockerignore`, `deploy/nginx/bootstrap.conf`, `deploy/nginx/default.conf`, `deploy/docker-compose.prod.yml`, `deploy/README.md`

**Interfaces:**
- Produces: imagen `nginx` con la SPA en `/usr/share/nginx/html` y proxy `/api/` → `backend:8080`; compose de 3 servicios (`mariadb`, `backend`, `nginx`) con marcadores `CAMBIAR_PASSWORD_SQL` y `CAMBIAR_JWT_SECRET`.

- [ ] **Step 1: Dockerfile en dos fases y .dockerignore**

`Dockerfile`:
```dockerfile
# Fase 1: compilar la SPA
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Fase 2: servir con nginx (la conf se monta desde /opt/reparaciones/nginx en la VM)
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80 443
```
`.dockerignore`:
```
node_modules
dist
.git
.env*
tests
docs
```

- [ ] **Step 2: nginx de arranque (solo 80, para emitir el certificado) y definitivo**

`deploy/nginx/bootstrap.conf`:
```nginx
server {
    listen 80;
    server_name erp.fonestore.es;

    location /.well-known/acme-challenge/ { root /var/www/certbot; }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location / {
        root /usr/share/nginx/html;
        try_files $uri /index.html;
    }
}
```
`deploy/nginx/default.conf`:
```nginx
# Rate limiting del login (guía de hardening de la VM): 5 intentos/minuto por IP, ráfaga de 3.
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;

server {
    listen 80;
    server_name erp.fonestore.es;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl;
    http2 on;
    server_name erp.fonestore.es;

    ssl_certificate     /etc/letsencrypt/live/erp.fonestore.es/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/erp.fonestore.es/privkey.pem;

    add_header X-Frame-Options DENY always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy same-origin always;

    access_log /var/log/nginx/erp.access.log;
    error_log  /var/log/nginx/erp.error.log;

    location /api/auth/login {
        limit_req zone=login burst=3 nodelay;
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /v3/api-docs { proxy_pass http://backend:8080; }
    location / {
        root /usr/share/nginx/html;
        try_files $uri /index.html;
        location ~* \.(js|css|png|ico)$ { expires 7d; add_header Cache-Control "public"; }
    }
}
```

- [ ] **Step 3: Compose de producción (plantilla)**

`deploy/docker-compose.prod.yml`:
```yaml
# Copiar a /opt/reparaciones/docker-compose.yml en la VM y sustituir los marcadores (ver deploy/README.md).
services:
  mariadb:
    image: mariadb:11
    environment:
      MARIADB_ROOT_PASSWORD: CAMBIAR_PASSWORD_ROOT
      MARIADB_DATABASE: gestion_reparaciones
      MARIADB_USER: erp
      MARIADB_PASSWORD: CAMBIAR_PASSWORD_SQL
    volumes:
      - db_data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql
    ports:
      - "127.0.0.1:3306:3306"
    restart: unless-stopped

  backend:
    build: ./gestion-reparaciones-servidor
    environment:
      SPRING_DATASOURCE_URL: jdbc:mariadb://mariadb:3306/gestion_reparaciones
      SPRING_DATASOURCE_USERNAME: erp
      SPRING_DATASOURCE_PASSWORD: CAMBIAR_PASSWORD_SQL
      JWT_SECRET: CAMBIAR_JWT_SECRET
      JWT_EXPIRATION: "86400000"
      SERVER_ERROR_INCLUDE_MESSAGE: never
      SPRINGDOC_SWAGGER_UI_ENABLED: "false"
    depends_on:
      - mariadb
    restart: unless-stopped

  nginx:
    build: ./gestion-reparaciones-web
    ports:
      - "0.0.0.0:80:80"
      - "0.0.0.0:443:443"
    volumes:
      - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
      - ./certbot:/var/www/certbot
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - ./logs-nginx:/var/log/nginx
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  db_data:
```
`deploy/README.md`:
```markdown
# Despliegue en la VM de producción

Los ficheros de esta carpeta son la referencia versionada de lo que vive en `/opt/reparaciones` de la VM:
- `docker-compose.prod.yml` → `/opt/reparaciones/docker-compose.yml` (sustituir `CAMBIAR_PASSWORD_ROOT`,
  `CAMBIAR_PASSWORD_SQL` y `CAMBIAR_JWT_SECRET`; los valores reales solo existen en la VM).
- `nginx/bootstrap.conf` → `/opt/reparaciones/nginx/default.conf` SOLO hasta emitir el certificado.
- `nginx/default.conf` → `/opt/reparaciones/nginx/default.conf` definitivo (80 → 301 → 443).
El backend no expone puertos al host; `SERVER_ERROR_INCLUDE_MESSAGE=never` en producción.
Actualizar: `cd /opt/reparaciones && git -C gestion-reparaciones-servidor pull && git -C gestion-reparaciones-web pull && docker compose up -d --build`.
El paso a paso de la primera instalación está en `Apuntes/despliegue_vdc.md`, sección "Producción y web".
```

- [ ] **Step 4: Comprobación local (sin Docker) y commit**

Run: `npm run build && ls dist/index.html dist/assets | head -3` → existe `dist/index.html`. `grep -c "proxy_pass http://backend:8080" deploy/nginx/default.conf` → `3`.
```bash
git add -A && git commit -m "chore(web): Dockerfile en dos fases, nginx (bootstrap y definitivo con rate limiting) y compose de produccion"
```

---

### Task 10: Montaje de la VM de producción y despliegue (runbook; el usuario ejecuta)

**Files:**
- Modify: `C:\Users\dev\Documents\Apuntes\despliegue_vdc.md` (sección nueva "Producción y web", con el registro de la sesión)
- VM de producción (IP en Apuntes; alias SSH `prod`): `/opt/reparaciones/{docker-compose.yml, nginx/default.conf, sql/init.sql, certbot/, logs-nginx/, gestion-reparaciones-servidor/, gestion-reparaciones-web/}`

**Interfaces:**
- Consumes: Task 3 (Dockerfile del servidor en `main`... ver nota de ramas), Task 9 (ficheros `deploy/`).
- Produces: `https://erp.fonestore.es` sirviendo la web y `/api`.

Nota de ramas: la VM clona `main`, pero el trabajo está en `feature/web-cimientos` de ambos repos. Para esta tarea, clonar esa rama (`git clone -b feature/web-cimientos ...`) y, tras el merge final (Task 11), cambiar a `main` con `git checkout main && git pull`.

**Reglas:** Claude prepara los comandos; el usuario los ejecuta uno a uno por SSH (`ssh prod`) y pega la salida; si algo difiere del runbook, se corrige el runbook en el momento. Nada de ráfagas de conexiones (fail2ban).

- [x] **Step 1: Preparar el runbook en `Apuntes/despliegue_vdc.md`** — añadir al final la sección:
El texto completo del runbook (P1 a P8: base, repos, secretos, dump de preprod, DNS y firewall, primer arranque y certificado, comprobaciones, actualización) vive **fuera del repo público**, en `Apuntes/despliegue_vdc.md`, sección "Producción y web (VM de producción, alias `prod`)". Ya está escrito (2026-09-14); aquí solo se referencia porque contiene alias SSH, nombres de contenedores y detalles de red.

- [x] **Step 2: Ejecutar P1–P7 con el usuario**, comando a comando. Cada desviación se corrige en el runbook. Al terminar, rellenar el "Registro de sesiones".

Expected al final: `https://erp.fonestore.es` con candado válido, login de los tres roles, Clientes operativa, 8080 cerrado al exterior, certificado con renovación probada en seco.

- [x] **Step 3: Confirmar la generación de tipos contra producción**

En el PC:
```bash
cd /c/Users/dev/Documents/ProgramaReparaciones/gestion-reparaciones-web
API_URL=https://erp.fonestore.es API_USER=admin API_PASS='...' npm run api:types && git status --short src/shared/api/schema.d.ts api/openapi.json
```
Expected: sin cambios (mismo contrato que en local) o solo diferencias de orden; si cambia, commitear `chore(web): snapshot OpenAPI de produccion`.

---

### Task 11: Smoke Playwright, cierre, merges y tag

**Files:**
- Create (repo web): `playwright.config.ts`, `tests/e2e/clientes.spec.ts`, `.env.e2e.example`
- Modify: `package.json` (script `e2e`), `.gitignore` (`.env.e2e`, `test-results/`, `playwright-report/`), `README.md` (sección e2e)
- Repo raíz: gitlinks; `Apuntes/plan-futuro.md` (§9: marcar el sub-proyecto 0)

- [x] **Step 1: Playwright**

```bash
npm install -D @playwright/test && npx playwright install chromium
```
`playwright.config.ts`:
```ts
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173', trace: 'retain-on-failure' },
  reporter: 'list',
})
```
`.env.e2e.example` (documentación de las variables; se exportan en la shell, no se cargan de fichero):
```
E2E_BASE_URL=https://erp.fonestore.es
E2E_USER=<supertecnico>
E2E_PASS=<contraseña>
```
`tests/e2e/clientes.spec.ts`:
```ts
import { expect, test } from '@playwright/test'

const nombre = `E2E ${Date.now()}`

test('login, crear, editar, desactivar y borrar un cliente', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('Usuario').fill(process.env.E2E_USER!)
  await page.getByPlaceholder('Contraseña').fill(process.env.E2E_PASS!)
  await page.getByRole('button', { name: 'Iniciar Sesión' }).click()
  await expect(page.getByText('FSGR:')).toBeVisible()

  await page.getByRole('link', { name: 'Clientes' }).click()
  await page.getByRole('button', { name: 'Nuevo cliente' }).click()
  await page.getByLabel('Nombre del cliente:').fill(nombre)
  await page.getByRole('button', { name: 'Aceptar' }).click()
  const fila = page.getByRole('row', { name: new RegExp(nombre) })
  await expect(fila).toBeVisible()

  await fila.click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Editar' }).click()
  await page.getByLabel('Nombre:').fill(`${nombre} bis`)
  await page.getByRole('button', { name: 'Aceptar' }).click()
  const fila2 = page.getByRole('row', { name: new RegExp(`${nombre} bis`) })
  await expect(fila2).toBeVisible()

  await fila2.click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Desactivar' }).click()
  await expect(fila2.getByText('Inactivo')).toBeVisible()

  await fila2.click({ button: 'right' })
  await page.getByRole('menuitem', { name: 'Borrar' }).click()
  await expect(page.getByRole('dialog', { name: 'Borrar cliente' })).toBeVisible()
  await page.getByRole('button', { name: 'Borrar' }).click()
  await expect(fila2).toHaveCount(0)
})
```
`package.json`: `"e2e": "playwright test"`. Exportando las variables en la shell:
Run: `E2E_BASE_URL=https://erp.fonestore.es E2E_USER=... E2E_PASS=... npm run e2e` → 1 passed.
```bash
git add -A && git commit -m "test(web): smoke Playwright de Clientes contra produccion"
```

- [x] **Step 2: Checklist de cierre (spec §11)** — comprobar y anotar en el mensaje final:
  - `npm run check` verde en local y en CI (tras el push que autorice el usuario).
  - Servidor: suite Maven verde; contexto arranca con springdoc; `schema.d.ts` commiteado.
  - `https://erp.fonestore.es` con certificado válido; login 3 roles; Clientes con paridad verificada contra `docs/paridad/clientes.md` y las capturas; e2e verde.
  - Fichas `shell.md` y `clientes.md` cerradas con el usuario (checkboxes marcados).
  - `Apuntes/despliegue_vdc.md` con la sección "Producción y web" y su registro; `schema.md` regenerado; README del repo web.

- [x] **Step 3: Integración (solo con OK explícito del usuario, uno a uno)**
  1. Servidor: `git checkout main && git merge --no-ff feature/web-cimientos -m "Merge branch 'feature/web-cimientos' (springdoc, respuestas tipadas, Dockerfile)"` → push.
  2. Web: `git checkout main && git merge --no-ff feature/web-cimientos -m "Merge branch 'feature/web-cimientos' (cimientos: shell, login, API tipada, Clientes)"` → push → `git tag -a v0.1.0 -m "Cimientos: shell, login, API tipada, Clientes"` → push del tag.
  3. Raíz: `git add gestion-reparaciones-servidor gestion-reparaciones-web gestion-reparaciones-cliente/docs/api_contract.md docs/superpowers/plans/2026-09-14-web-cimientos.md && git commit -m "chore: gitlinks servidor y web tras cimientos (web v0.1.0)"`; merge de `docs/migracion-web` donde el usuario decida (main o la hotfix en curso).
  4. VM: `cd /opt/reparaciones && git -C gestion-reparaciones-servidor checkout main && git -C gestion-reparaciones-servidor pull && git -C gestion-reparaciones-web checkout main && git -C gestion-reparaciones-web pull && docker compose up -d --build`.
  5. `Apuntes/plan-futuro.md` §9: marcar `[x] 0 Cimientos`; memoria del proyecto actualizada con el estado.

---

## Self-review (hecho al escribir el plan)

- **Cobertura de la spec:** §3 repo/stack/estructura/tokens → Tasks 1, 2; §4 shell → Task 6; §5 login y sesión → Tasks 4, 5; §6 cliente API y errores → Task 4; §7 Clientes y su ficha → Tasks 7, 8; §8 servidor (springdoc, tipos, schema.md, api_contract, arranque validado) → Task 3; §9 VM y despliegue → Tasks 9, 10; §10 CI → Task 1; §11 criterios de cierre → Task 11. Bases responsive (spec §4): la barra oculta el título largo bajo `md` y las tablas van en `overflow-x-auto` (Task 6 y 7); el colapso de los botones en menú a <900 px queda para cuando haya más de una vista real (anotado en `docs/paridad/shell.md` como diferencia aceptada de este sub-proyecto).
- **Placeholders:** ninguno; las contraseñas del admin local y de e2e se pasan por variable de entorno y no se escriben.
- **Consistencia de nombres:** `api`, `Cliente`, `clasificar`, `extraerMensaje`, `reportarFallo/Exito`, `useConexion`, `leerSesion/guardarSesion/borrarSesion`, `esAdmin/esSuperTecnico/esAdminOSuperTecnico`, `onSesionExpirada/dispararSesionExpirada/rearmarSesionExpirada`, `useSession`, `renderConProviders`, `SESION_*`, `DataTable/MultiSelect/StatusBadge/ConfirmDialog/AlertaProvider/useAlerta`, `useClientes/useCrearCliente/useEditarCliente/useSetActivoCliente/useBorrarCliente/tieneTelefonos` se usan con la misma firma en todas las tareas. `baseUrl` del cliente: origin vacía en producción y `http://localhost` en tests; los handlers MSW usan `*/api/...`.
