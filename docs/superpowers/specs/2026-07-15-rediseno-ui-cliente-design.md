# Rediseño visual del cliente — sidebar, tokens y dashboard de inicio

**Fecha:** 2026-07-15
**Estado:** Aprobado (brainstorming con mockups en `.superpowers/brainstorm/5410-1784102431/content/`)
**Rama prevista:** `feature/ui-rediseno` (nueva, desde `main`)
**Prerrequisito:** ejecutar y mergear `feature/atributos-sku` antes de empezar (toca las vistas de inventario/IMEIs que aquí se reubican).

## Contexto y objetivo

El usuario quiere acercar el aspecto del ERP a los dashboards ERP web modernos de referencia (carpeta "ideas" del escritorio: GemMatrix, Vantus, Pakun, Property). Rasgos comunes de esas referencias: sidebar lateral de navegación con iconos, tarjetas KPI, tarjetas blancas con sombra sobre lienzo gris, chips de estado en tablas y jerarquía tipográfica fuerte.

Aunque a futuro la UI migrará a web, el sistema de diseño que se define aquí (paleta, chips, tarjetas, jerarquía) servirá de spec para esa web; solo el FXML/CSS concreto es específico de JavaFX.

**Alcance:** solo cliente JavaFX, solo presentación. Sin cambios de lógica de negocio, DAOs, servidor, BD ni permisos.

## Decisiones tomadas (con mockups)

1. **Navegación**: sidebar lateral izquierdo **oscuro** (navy de marca `#001232`), fijo ~220px, con secciones y usuario abajo. Elegido frente a mantener navbar superior o sidebar claro.
2. **Modo oscuro**: NO en v1. El CSS se tokeniza para poder añadirlo después sin repintar. El sidebar navy es permanente (marca).
3. **Dashboard de inicio**: layout "carga protagonista" (hero de carga del día + KPIs 2×2 + listas), adaptado por rol.
4. **Barra superior fina** blanca con título de vista + campana de solicitudes (badge) + menú de usuario. Elegido frente a meter la campana en el sidebar.
5. El dashboard es **solo una capa de resumen y atajos**: no posee información ni sustituye módulos; todo lo que muestra existe con más detalle en su módulo, y cada tarjeta hace deep-link filtrado.
6. Habrá un futuro usuario/rol de **logística** (F3 del roadmap): el sidebar reserva el hueco (item oculto hasta que exista).

## 1. Sistema de diseño (tokens)

Reescritura de `app.css` sobre looked-up colors de JavaFX definidos en `.root`:

- Tokens de color: `-color-bg` (lienzo), `-color-card`, `-color-accent` (navy), `-color-text`, `-color-text-muted`, `-color-border`, y semánticos `-color-ok`, `-color-warn`, `-color-danger`, `-color-info` (cada uno con variante de fondo suave para chips).
- Paleta base: la actual (navy `#001232`, lienzo gris claro, blanco), afinada.
- **Tarjetas**: `-fx-background-radius` 10–12, sombra suave (`dropshadow`), sin borde gris duro. Sustituyen el patrón actual borde `#C2C8D0`.
- **Chips de estado**: clase reutilizable (label con fondo suave + texto semántico + radio completo). Mapeo: verde OK / ámbar bajo-pendiente / rojo sin stock-error / azul en curso-en camino. Sustituyen texto plano en columnas Estado de todas las tablas.
- **Iconos**: dependencia **Ikonli** (core + un pack, decidir en plan entre Material2/Feather/Boxicons) para sidebar, topbar, botones principales y tarjetas KPI. Hoy la app no tiene iconos.
- **Tipografía**: escala definida (título vista, número KPI grande, label pequeño apagado, cuerpo tabla). Sin fuentes externas nuevas salvo que el plan lo justifique.
- Los estilos inline dispersos en controladores (p. ej. tarjetas de NotificacionesModal, `estiloTabActivo()`) migran a clases CSS con tokens de forma oportunista al tocar cada zona; no es objetivo barrer el 100 % en v1.

## 2. Estructura de navegación

`MainView.fxml` pasa de navbar superior a **BorderPane: left = sidebar, top = topbar fina, center = contenedor** (se conserva `StackPane` + `vistaCache` + `Recargable` + deep-links `irA…` tal cual).

### Sidebar (navy, fijo ~220px)

| Sección | Item | Vista destino (origen actual) |
|---|---|---|
| — | Inicio | Dashboard nuevo (por rol) |
| Taller | Reparaciones | Historial (ReparacionView por rol) |
| Taller | Asignaciones | tab Asignaciones (super/admin; modal pendientes en super) |
| Taller | Pendientes | tab Pendientes (técnico/supertécnico) |
| Taller | Pulidos | vistas Pulido por rol |
| Almacén | Stock | StockView → tab Stock actual |
| Almacén | Pedidos | StockView → tab Pedidos |
| Almacén | Proveedores | StockView → tab Proveedores |
| Almacén | Inventario | tab IMEIs actual (AgrupadoView) |
| Gestión | Estadísticas | EstadisticasView (sus 2 tabs internas se mantienen dentro) |
| Gestión | Clientes | ClientesView |
| *(futuro)* | Logística | oculto hasta que exista el rol |
| abajo | Usuario (nombre + rol) | ancla del menú de usuario actual |

- Los **sub-sidebars blancos internos de Taller y Almacén desaparecen**: su navegación sube al sidebar global. **Excepción: Estadísticas** conserva sus dos pestañas (Reparaciones/Stock) como toggle interno — dos entradas de sidebar para una vista de gráficos sería ruido. Los toggles pill internos con sentido funcional se conservan.
- **Visibilidad por rol: la misma que hoy.** Cada rol ve los items que corresponden a las vistas/tabs que ya ve; nada se abre ni se cierra de permisos.
- Item activo resaltado (fondo más claro + texto blanco), iconos en todos los items, badges de contador donde ya existen (patrón `sidebar-badge` actual).
- Ancho fijo en v1; colapsable a solo-iconos queda como mejora futura.

### Topbar (blanca, fina)

- Izquierda: título de la vista activa + fecha (con sitio para migas si algún día hay jerarquía).
- Derecha: **campana de solicitudes** con badge y pulso/glow actuales (solo supertécnico, como hoy; el anclaje del panel `NotificacionesModal` pasa a colgar de la topbar) + **avatar/usuario** con el menú actual íntegro (Gestionar técnicos y Ver logs solo admin; Descargar CSV; Cambiar contraseña; Cerrar sesión).
- Hueco natural para un buscador global futuro (no en v1).

## 3. Dashboard "Inicio"

Nueva vista por defecto tras login para todos los roles (sustituye a abrir Reparaciones directamente).

### Admin / Supertécnico

- **Hero "Carga del día"**: porcentaje grande + las dos barras reales de capacidad con topes 8/17/25 (mismos datos y cálculo que el modal de carga actual, que no cambia y se abre al hacer clic).
- **KPIs 2×2**: completadas hoy · pendientes · pulidos pendientes · componentes bajo mínimo.
- **Listas inferiores**: alertas de stock (chips ámbar/rojo, clic → Stock filtrado), pedidos en camino (clic → Pedidos), inventario reciente (clic → Inventario).

### Técnico

Versión reducida: su cola del día (asignaciones propias), la carga del día del taller (misma tarjeta, formato compacto — la carga es global, no por técnico) y sus completadas hoy. Sin datos de almacén.

### Datos y navegación

- Todos los datos salen de **DAOs existentes** en el cliente; sin endpoints nuevos en v1. Si el número de llamadas al abrir resultara pesado, se valoraría un endpoint agregado más adelante (fuera de alcance).
- Deep-links reutilizando el mecanismo existente (`vistaCache` + métodos tipo `irAPedidos`/`irAStockActual`).
- Se recarga con el patrón `Recargable` (foco de ventana) como el resto de vistas.

## 4. Vistas interiores

Solo restyle, cero reestructura funcional:

- Tablas: chips de estado, cabeceras y filas con la nueva tipografía/aire, tarjeta contenedora con sombra.
- Filtros: iguales (los FlowPane responsive se conservan).
- Formularios y modales: heredan tokens vía CSS; sin rediseño individual en v1.

## 5. Qué NO cambia

Lógica de negocio, DAOs/HTTP, servidor, BD, roles y permisos, modal de carga, contenido del panel de notificaciones (solo su ancla), login (solo hereda tokens), exportaciones CSV.

## 6. Fases de implementación

Cada fase deja la app compilando, usable y mergeable por separado:

1. **F1 — Tokens + facelift**: reescritura de `app.css` con tokens, tarjetas, chips, tipografía, Ikonli. La estructura de navegación aún es la actual.
2. **F2 — Sidebar + topbar**: reestructura de `MainView` y `MainController`, desaparición de sub-sidebars, recolocación de campana y menú usuario.
3. **F3 — Dashboard**: vista Inicio por rol con deep-links.

## 7. Verificación

- Compilación + suite de tests actual en cada fase.
- Smoke manual con los 3 roles (patrón usado en Separar Glass): navegación completa, campana/solicitudes, deep-links del dashboard, CSV, cambiar contraseña, logout/login.
- Atención especial: el servidor no tiene test de contexto Spring, pero aquí no se toca servidor; el riesgo es solo cliente.

## 8. Riesgos y mitigaciones

- **Conflicto con `feature/atributos-sku`** (toca inventario/IMEIs): mitigado con el prerrequisito de mergearlo antes.
- **CSS de TableView en JavaFX es quisquilloso** (selected/hover/scrollbars): F1 incluye pasada explícita por todos los estados; ya hay base en el CSS actual.
- **Rendimiento del dashboard** (varias llamadas al abrir): aceptado en v1; medir y, si molesta, endpoint agregado después.
- **Regresiones visuales en vistas no revisadas**: el smoke de 3 roles recorre todas las vistas.

## Futuro (fuera de alcance, anotado)

- Modo oscuro (toggle) sobre los tokens.
- Sidebar colapsable a solo-iconos.
- Buscador global en topbar.
- Item Logística cuando exista el rol (F3 roadmap).
- KPIs económicos (requieren ventas/facturación, que van por sistemas externos según `Apuntes/cobertura-erp.md`).
