# 0.9.5 — Previsión de pedidos de piezas y dos arreglos de la sesión

Fecha: 2026-10-07. Versión del producto: **0.9.5** (servidor y web etiquetados juntos).
Antecedentes: spec del SP7b (`2026-09-28-sp7b-autorizacion-sesiones-design.md`, §6.1: cierre por inactividad) y spec
de la política de contraseñas (`2026-10-05-politica-contrasenas-design.md`, §4: barra de seguridad). La previsión de
pedidos es la "Fase 5 — Stock mínimo automático" del plan maestro, replanteada.

## 0. Nota sobre este documento

Los tres repositorios son públicos. Este documento describe el comportamiento objetivo. Los datos de las máquinas y
el registro de los despliegues viven fuera de git, en `Apuntes/`.

## 1. Objetivo

Primera versión tras el corte, y la primera que recorre entera el circuito preprod → producción con cambios reales.
Cuatro piezas:

1. **Mensaje propio al cerrar por inactividad** (web).
2. **Enter rápido en los formularios de contraseña** (web).
3. **Previsión de pedidos** en Stock: consumo diario previsto y cuánto pedir para 15 y 30 días (servidor y web).
4. **Parámetros de la previsión y stock mínimo solo para el administrador** (servidor, web y una migración).

Además, el tag `v0.9.5` del servidor recoge el arreglo del lookup de IMEI (serie 17 y Air), que ya está en `main` y
desplegado pero sin versión.

## 2. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Qué se calcula | El **consumo diario previsto** de cada pieza. El stock mínimo **no** se calcula | El mínimo es una decisión del negocio (suelo), no un dato que salga del histórico |
| Ponderación | Tres tramos de 30 días naturales hacia atrás, pesos **50 / 30 / 20 %** (de lo reciente a lo antiguo) | Lo reciente pesa más; los pesos suman 100, así que la suma ponderada es una media y no infla la previsión |
| Días que cuentan | Días naturales **completos, hasta ayer** | La previsión no cambia a lo largo de la jornada |
| Piezas nuevas (<90 días) | **Sin trato especial**: los tramos sin historia cuentan como 0 | Simplicidad; el mínimo hace de colchón hasta que la pieza tenga historia |
| Horizonte del pedido | **Dos columnas a la vista**, para **15 y 30 días** | Se pide cada 15 días idealmente y a veces al mes; con dos opciones fijas, verlas juntas es más claro que un selector |
| Papel del mínimo | **Suelo**: objetivo = máx(mínimo; consumo previsto) | Las piezas que se mueven ya llevan su colchón en el consumo; el mínimo protege a las que casi no se mueven. Mismo criterio que la hoja de cálculo de control que se usaba antes |
| Plazo de entrega | **Fuera de la fórmula** | Es imprevisible (de 3 días a un mes); meterlo como número fijo sería precisión falsa. Se elige 30 días cuando el pedido es lento |
| Qué es consumo | Solo las piezas de las **reparaciones resultantes** (`R…`, `G…`), sin reutilizadas | Una solicitud ya servida tiene las mismas marcas que una fila de consumo; solo las filas resultantes restan stock |
| Demanda no servida | **No cuenta** | No se distingue con fiabilidad una solicitud gestionada servida de una pendiente; la demanda atrasada entra igualmente como consumo cuando se monta la pieza |
| "Agotar componente" | **No cuenta** como consumo | Es un ajuste de stock (la pieza faltaba o estaba mal), no una reparación |
| Piezas en desuso | Se **desactivan**; las desactivadas no llevan previsión | Si una pieza no se gasta es porque el modelo ya no se trabaja |
| Quién ve la previsión | **SUPERTECNICO y ADMIN**. El servidor no envía las cifras a un TECNICO | Es información de compras |
| Quién edita el mínimo | **Solo ADMIN** (antes también SUPERTECNICO) | El mínimo es el suelo y lo decide el administrador |
| Quién edita los pesos | **Solo ADMIN**, desde Stock | Ajustar la ponderación sin desplegar |
| Dónde se guardan los pesos | Tabla nueva `Parametro` (clave → entero) | Se editan desde la aplicación; admite parámetros futuros sin otra migración |
| Cuándo se calcula | **Al cargar Stock**, sin guardar el resultado | Sin trabajo nocturno ni columnas calculadas; el badge, la gráfica y las alertas siguen leyendo `STOCK_MINIMO` como hoy |

## 3. Previsión de pedidos

### 3.1 La regla

Para cada pieza activa (resuelta a su master si pertenece a un grupo compartido):

- `t1`, `t2`, `t3` = unidades consumidas en los días `[hoy−30, hoy)`, `[hoy−60, hoy−30)` y `[hoy−90, hoy−60)`
  (fechas en `Europe/Madrid`; `hoy` excluido).
- `p1`, `p2`, `p3` = pesos en % (por defecto 50, 30, 20; suman 100).
- **Consumo/día** = `(p1·t1 + p2·t2 + p3·t3) / (100 · 30)`.
- Para `N` ∈ {15, 30}: **Pedir N d** = `máx(0, ⌈ máx(mínimo; consumo/día · N) − (stock + en camino) ⌉)`.

`stock`, `en camino` y `mínimo` son los que ya devuelve el listado: los del master del grupo; en camino = pedidos
`en_camino` y `parcial` menos lo recibido.

**Aritmética exacta.** El cálculo se hace en enteros escalados por `3000` (= 100 · 30) para que el redondeo hacia
arriba no se equivoque por decimales (por ejemplo, 6,0000001 → 7):

```
num(N)    = (p1·t1 + p2·t2 + p3·t3) · N
objetivo  = máx(mínimo · 3000, num(N))
falta     = objetivo − (stock + enCamino) · 3000
pedir(N)  = falta ≤ 0 ? 0 : ⌈falta / 3000⌉
consumo/día = num(1) / 3000   (se redondea a 2 decimales solo para mostrarlo)
```

**Ejemplos** (pesos 50/30/20):

| Caso | t1/t2/t3 | Mín. | Stock | En camino | Consumo/día | Pedir 15 d | Pedir 30 d |
|---|---|---|---|---|---|---|---|
| Batería que se mueve | 12 / 8 / 5 | 2 | 3 | 0 | 0,31 | máx(2; 4,7) − 3 → **2** | máx(2; 9,4) − 3 → **7** |
| Bien surtida | 12 / 8 / 5 | 2 | 10 | 0 | 0,31 | **0** | máx(2; 9,4) − 10 → **0** |
| Casi parada | 1 / 0 / 0 | 2 | 0 | 0 | 0,02 | máx(2; 0,25) − 0 → **2** | máx(2; 0,5) − 0 → **2** |
| Con pedido en camino | 12 / 8 / 5 | 2 | 3 | 5 | 0,31 | **0** | 9,4 − 8 → **2** |
| Pieza nueva (20 días) | 6 / 0 / 0 | 2 | 0 | 0 | 0,10 | máx(2; 1,5) → **2** | máx(2; 3) → **3** |
| Sin consumo | 0 / 0 / 0 | 2 | 0 | 0 | 0,00 | **2** | **2** |

### 3.2 Qué cuenta como consumo

Filas de `Reparacion_componente` unidas a su `Reparacion`, que cumplan todo esto:

- `ID_REP` empieza por `R` o por `G` (reparación resultante normal o de glass). Las solicitudes cuelgan de la
  asignación (`A…`, `AG…`) y nunca cuentan, estén en el estado que estén, con `ES_SOLICITUD` a 1 o a 0.
- `ID_COM` no nulo, `ES_REUTILIZADO = 0`.
- El componente no es de tipo `otro%`.
- Fecha = `DATE(r.FECHA_FIN)`. En las filas resultantes coincide con `FECHA_ASIG` (las dos se ponen a `NOW()` al
  crearlas); se usa `FECHA_FIN` porque es el momento en que se montó la pieza. La base guarda las horas en UTC y el
  día se toma tal cual: solo difiere del día de Madrid entre las 22:00 y las 02:00, cuando el taller no trabaja.
- Cantidad = `rc.CANTIDAD`.

El consumo de una fila cuyo `ID_COM` es un slave se suma a su master (`COALESCE(ID_COM_MASTER, ID_COM)`). Todas las
filas de un grupo compartido enseñan las mismas cifras, igual que ya pasa con el stock y lo que está en camino.

**Comprobación previa en preprod** (base = copia real de producción), antes de dar por buena la consulta: que no
haya filas con `ES_SOLICITUD = 1` ni con `DESCRIPCION_SOLICITUD` no nula bajo `R…`/`G…`, y que el total de piezas no
reutilizadas bajo `R…`/`G…` de los últimos 90 días cuadre con un recuento hecho a mano para dos o tres piezas.

### 3.3 Servidor

- **`PrevisionPedido`** (`util/`): clase pura con la regla de §3.1 y el reparto en tramos. `agrupar(consumos, hoy)`
  reparte las unidades de cada día en `t1..t3` por master; `calcular(tramos, pesos, mínimo, stock, en camino)`
  devuelve consumo/día (2 decimales), `pedir15` y `pedir30`. Sin acceso a BD.
- **`ComponenteDAO`**:
  - `getConsumoDiario(desde, hasta)`: una consulta que devuelve, por master y día, las unidades consumidas en
    `[desde, hasta)` (§3.2). El reparto en tramos va en Java para poder probarlo: los tests de DAO del servidor usan
    un `JdbcTemplate` simulado, no una base real.
  - `getAllGestionados()` no cambia su SQL.
- **`ParametroDAO`** (nuevo): leer y guardar los tres pesos.
- **`PrevisionPedidoService`** (nuevo): junta pesos, consumo y listado, y rellena la previsión de cada componente
  activo.
- **`GET /api/componentes/gestionados`**:
  - Recibe el usuario autenticado.
  - Si el rol es SUPERTECNICO o ADMIN, rellena en cada componente activo `consumoDiario`, `pedir15` y `pedir30`;
    en los desactivados, nulos.
  - A un TECNICO los tres campos le llegan siempre nulos.
  - `hoy` = fecha actual en `Europe/Madrid`.
- **Modelo `Componente`**: tres campos nuevos, nulables (`consumoDiario`: número con 2 decimales; `pedir15`,
  `pedir30`: enteros).
- El contrato OpenAPI se regenera; la web actualiza sus tipos.

### 3.4 Web

- **Tabla de Stock**: tres columnas nuevas entre "Stock Mínimo" y "Último pedido": **Consumo/día** (2 decimales,
  coma decimal), **Pedir 15 d** y **Pedir 30 d**.
  - Un 0 en "Pedir" sale en gris; las piezas desactivadas enseñan "—".
  - Solo se pintan para SUPERTECNICO y ADMIN; el TECNICO ve la tabla de hoy.
- **CSV de Stock** (`stock_actual`): las tres columnas, en el mismo orden, solo para SUPERTECNICO y ADMIN.

## 4. Parámetros y mínimo solo para el administrador

### 4.1 Base de datos (migración)

`sql/migracion-parametros-prevision.sql` y el mismo bloque en `crear_bd.sql`:

```sql
CREATE TABLE Parametro (
    CLAVE      VARCHAR(50) NOT NULL PRIMARY KEY,
    VALOR      INT         NOT NULL,
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO Parametro (CLAVE, VALOR) VALUES
    ('PREVISION_PESO_1', 50), ('PREVISION_PESO_2', 30), ('PREVISION_PESO_3', 20);
```

Solo añade una tabla: no toca datos existentes. Si el servidor arranca sin la tabla o sin alguna clave, la
previsión usa 50/30/20 y lo anota en el log (no rompe el listado de Stock).

### 4.2 Servidor

- **`GET /api/parametros/prevision`** (`hasRole('ADMIN')`): `{ peso1, peso2, peso3 }`.
- **`PUT /api/parametros/prevision`** (`hasRole('ADMIN')`):
  - Valida tres enteros entre 0 y 100 que suman 100; si no, 422 con el mensaje
    "Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.".
  - Guarda los tres en una transacción.
  - Lo anota en `Log_Actividad`: acción `EDITAR_PARAMETROS`, detalle `PREVISION: 50/30/20 → 40/35/25`.
- **`PATCH /api/componentes/{idCom}/stock-minimo`**: de `hasRole('SUPERTECNICO')` a **`hasRole('ADMIN')`**, y guarda
  el mínimo **en el master** del grupo compartido. Hasta ahora, en una fila slave cambiaba el mínimo del propio slave,
  que no se ve en ninguna pantalla (Stock enseña el del master) y que la previsión no usa.
- **`PUT /api/componentes/{idCom}`** ("Editar stock", SUPERTECNICO): **deja de cambiar `STOCK_MINIMO`**. El campo
  `stockMinimo` del cuerpo se ignora y la fila conserva el suyo. El mínimo solo se cambia por el `PATCH` de arriba.
- **`POST /api/componentes`** (alta) no se toca: es una de las rutas retiradas, responde 403 a todos desde la 0.9.0 y
  se borra en la tarea 27 del SP7b.
- `autorizacion_endpoints.md` del servidor se actualiza con las tres rutas.

### 4.3 Web

- **Botón "Parámetros de previsión"** en la barra de Stock, a la derecha de "Limpiar filtros", con el mismo estilo
  que "Carga técnicos" (`BotonSecundario`). **Solo lo ve el ADMIN.**
- **Diálogo "Parámetros de previsión"**:
  - Tres campos enteros en %: "Días 1-30", "Días 31-60" y "Días 61-90", cargados con los valores actuales.
  - Ayuda: "Lo reciente pesa más. Los tres tienen que sumar 100."
  - Suma en vivo ("Suma: 100 %"), en rojo si no es 100.
  - "Guardar" desactivado mientras la suma no sea 100 o algún campo no sea un entero entre 0 y 100 (`DialogoAlmacen`
    gana una propiedad para desactivar su botón de acción).
  - Un 422 del servidor se pinta dentro del diálogo, como en los demás diálogos de Stock.
  - Al guardar, se cierra y se recarga el listado de Stock.
- **Menú de clic derecho en Stock**:
  - **ADMIN** (hoy sin menú): menú nuevo con una sola entrada, "Ajustar mínimo".
  - **SUPERTECNICO**: el de hoy **sin** "Ajustar mínimo".
  - **TECNICO**: sin cambios ("Solicitar pieza").

## 5. Mensaje propio al cerrar por inactividad

**Hoy:** `VigilanciaInactividad` expulsa a las 2 h sin uso llamando a `dispararSesionExpirada()`, y el handler de
`main.tsx` deja siempre en el login "Tu sesión ha expirado. Inicia sesión de nuevo.", el mismo texto que un 401, el
token caducado o la desactivación del usuario.

**Cambio:**

- `dispararSesionExpirada(motivo?: 'inactividad')` pasa el motivo al handler registrado con `onSesionExpirada`.
- El handler de `main.tsx` elige el mensaje:
  - con `'inactividad'`: **"Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo."** (constante
    nueva junto a `MSG_SESION_EXPIRADA_UI`);
  - sin motivo: el de siempre.
- `VigilanciaInactividad` llama con `'inactividad'`.
- El resto de caminos (401, cliente de la API) no cambian.
- Las demás pestañas abiertas siguen como hoy: al desaparecer la sesión del almacenamiento compartido van al login
  sin mensaje (`SessionProvider`).

## 6. Enter rápido en los formularios de contraseña

**Hoy:** `useNotaPassword` espera 0,3 s tras la última tecla antes de pedir la nota, y `bloqueaGuardar` no deja
guardar mientras el estado es `comprobando`. Un Enter pulsado en ese intervalo no hace nada, en "Cambiar contraseña"
y en la pantalla del cambio obligatorio.

**Cambio:** `bloqueaGuardar` deja de bloquear en `comprobando`. Si se pulsa Enter, o "Guardar", antes de que llegue la
nota, la petición sale y **decide el servidor**, que aplica la misma regla (`PoliticaPassword`) y devuelve su mensaje
(422) si la rechaza. Una nota ya recibida y no aceptable sigue bloqueando, como hoy. Ya era así cuando la consulta de
la nota fallaba.

## 7. Versión, documentación y despliegue

- **Web:** `package.json` → `0.9.5`, `CHANGELOG.md`, rama → merge `--no-ff` → tag `v0.9.5`.
- **Servidor:** rama → merge `--no-ff` → tag `v0.9.5` (incluye el arreglo del lookup de IMEI ya en `main`).
- **Raíz:** gitlinks de servidor y web y `docs/novedades/NOVEDADES-v0.9.5.md` (lenguaje de usuario).
- Merge, push y tags, cada uno con OK del usuario.
- **Circuito** (guías en `Apuntes/`):
  1. **Preprod, en horario:** refrescar la base desde producción, aplicar la migración con su comprobación en el
     mismo bloque, desplegar servidor y después web.
  2. **Producción, fuera de horario:** copia a mano, migración con su comprobación en el mismo bloque, servidor,
     web, comprobación ligera sin escribir datos.
  3. **Orden crítico en las dos máquinas:** la migración **antes** que el servidor nuevo. Un servidor 0.9.5 sin la
     tabla funciona igual (pesos por defecto), pero el diálogo de parámetros daría error.

## 8. Pruebas

**Servidor:**

- `PrevisionPedidoTest`: los seis casos de §3.1, pesos distintos (40/35/25), un caso que en coma flotante
  redondearía mal y en enteros no, y el mínimo 0.
- Reparto en tramos (`PrevisionPedido.agrupar`): límites de cada tramo (día 30 frente a 31, hoy excluido, día 91
  fuera) y masters separados.
- Consulta de consumo (`JdbcTemplate` simulado, como el resto de DAOs): el SQL lleva el filtro `R…`/`G…`, las
  reutilizadas fuera, `otro%` fuera, el master del grupo y el intervalo `[desde, hasta)`; las filas se leen bien. Que
  las solicitudes nunca caen bajo `R…`/`G…` se comprueba con datos reales en preprod (§3.2).
- `PATCH stock-minimo` guarda en el master.
- Controlador:
  - TECNICO recibe la previsión nula;
  - SUPERTECNICO y ADMIN la reciben;
  - desactivados nulos;
  - `stock-minimo`: 403 para SUPERTECNICO y OK para ADMIN;
  - `PUT` de editar stock no cambia el mínimo;
  - parámetros: 403 para no ADMIN, 422 si no suman 100, OK y registro en el log.
- `OpenApiContractTest` en verde con el contrato nuevo.

**Web:**

- Columnas y CSV:
  - visibles para SUPERTECNICO y ADMIN, ausentes para TECNICO;
  - 0 en gris;
  - "—" en desactivados.
- Diálogo de parámetros:
  - suma en vivo;
  - Guardar desactivado;
  - 422 dentro del diálogo;
  - botón solo para ADMIN.
- Menú por rol: ADMIN solo "Ajustar mínimo"; SUPERTECNICO sin él.
- Inactividad: el motivo llega al handler y el login enseña el texto nuevo; un 401 sigue con el de siempre.
- Contraseña: Enter con la nota en `comprobando` envía; con nota no aceptable no envía. En los dos formularios.

**Preprod (manual):**

- Comprobación de §3.2 con SQL.
- Dos o tres piezas comparadas a mano contra la pantalla.
- Cierre por inactividad forzado con una marca vieja en el almacenamiento del navegador; un 401 normal.
- Enter rápido en los dos formularios.
- Cuentas de pruebas de cada rol: columnas, menú, botón y diálogo.
- Smoke E2E de gestión.

## 9. Fuera de alcance (al backlog del plan maestro)

- Estado **URGENTE** por cobertura (días de stock < plazo del proveedor), cuando se midan los plazos reales.
- Plazo real por proveedor a partir de `FECHA_PEDIDO` y `FECHA_LLEGADA` de las compras.
- La gráfica de evolución del stock cuenta las filas de solicitud como salidas (probable exceso); revisarla con el
  criterio de §3.2.
- Lista "propuesta de pedido" ordenada por necesidad.
- Hacer editables los tramos (30 días) o los horizontes (15/30).
