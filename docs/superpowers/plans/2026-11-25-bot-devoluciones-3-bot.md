# Bot de devoluciones — plan 3: el bot de NSYS (informar y devolver) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El bot de stock de NSYS informa al ERP de lo que mueve y, con el interruptor encendido, devuelve de «Pedidos realizados»
a su caja los IMEI que le da el ERP; más los dos pendientes del bot (`en_revisar` una vez al día, refiltrar una sola vez).

**Architecture:** Todo en `stock/nsys_bot_completo.py` (repo de automatización), con funciones pequeñas y probadas por
monkeypatch como el resto del fichero: un módulo nuevo `stock/erp_bot.py` para hablar con el ERP (GET de la lista, POST de los
movimientos, fichero de pendientes), y en el bot el paso `devolver(page, a_mover)` entre mover y exportar, protegido con
try/except para que nunca impida exportar Items. Herramienta de ensayo de solo lectura `herramientas/ensayo_devolver.py`.

**Tech Stack:** Python 3, Playwright (sync), requests, pytest (monkeypatch; sin NSYS ni ERP reales).

**Spec:** `docs/superpowers/specs/2026-11-24-bot-devoluciones-design.md` §6 (contrato), §8 (el bot), §10 (orden, pruebas,
instalación). El ERP (0.9.47) ya está en producción: `GET /api/bot/devoluciones`, `POST /api/bot/movimientos`, clave
`PEDIDOS_BOT_CLAVE` (línea `botdev=` de `/root/claves-pedidos-prod.txt` en prod).

## Global Constraints

- Repo de automatización, worktree `C:\Users\dev\Documents\wt\auto-bot-devoluciones`, rama `dev_bot_devoluciones` (ya tiene el
  saneamiento). Partir de `origin/main` del repo si ha avanzado (fetch y merge antes de empezar). Nada de push/merge/tags sin
  confirmación de Imad. Commits sin `Co-Authored-By`.
- **NSYS:** máximo 4 filas de filtro; Status y Location solo Equals / Not equals con un único valor. Nunca sacar nada de
  `LOCATIONS_NO_TOCAR`. Nunca mover a una caja que no venga en la lista del ERP.
- **Nada puede impedir exportar Items:** los pasos nuevos (devolver, informar) atrapan sus excepciones, escriben `[AVISO]` y dejan
  la página en un estado que `limpiar_filtros` recupera. El fallo del Panel sigue cortando la vuelta como hoy.
- **El ERP nunca rompe la vuelta:** timeouts cortos (≤ 30 s), reintentos acotados, sin esperas largas; si falla, pendientes en
  `Automatizacion_datos\sistema\bot_movimientos_pendientes.json` y reenvío en la vuelta siguiente (el ERP descarta repetidos).
- **Identificador de vuelta para el ERP:** fecha y hora (`%Y-%m-%dT%H:%M:%S` del arranque), no `RUN_ID` (solo `HH:MM`, se repite
  cada día y el ERP lo usaría para descartar repetidos). `RUN_ID` y los textos que lee `orquestador.py` («TOTAL MOVIDOS»,
  «Exportado:», «RESUMEN:», «AUDITORIA:») no cambian.
- **Interruptor:** `"devolver": true` en `stock_config.json`; ausente o `false` = no devuelve (fase «informar»). Informar al ERP
  está activo si hay `erp_url` y `erp_clave_bot`; si falta alguna, no informa (como hoy vendidos sin `erp_clave`).
- **Motivos:** el bot no interpreta `motivo` (hoy `IMEI_CORREGIDO`; la 0.9.48 añadirá `RESERVA_LIBERADA`): lo devuelve tal cual en
  el POST.
- **Vender gana a devolver:** un IMEI de la lista de descontar de la vuelta (`a_mover`) se quita de los grupos.
- Como mucho **5 grupos por vuelta** (`TOPE_GRUPOS_DEVOLVER = 5`); el resto, en la siguiente.
- `movimientos.csv`: mismas columnas; resultados nuevos `devuelto`, `no_aparece_devolver`, `error_devolver`.
- Tests: `cd stock; python -m pytest tests -q` (489 en verde al empezar). `conftest.py` deja vacías las claves `erp_*` en todos los
  tests (fixture `_sin_erp_real`): los tests que usan el ERP las ponen con monkeypatch, como `test_bot_erp.py`.
- Repo privado, pero IMEI reales nunca en tests ni docs: IMEI inventados `35000000000000x`.

---

## File Structure

| Fichero | Qué |
|---|---|
| `stock/erp_bot.py` (nuevo) | `devoluciones()` (GET), `informar(movimientos)` (POST + pendientes), `RUN_ERP` |
| `stock/nsys_bot_completo.py` | recoger DESCONTADO al mover; `en_revisar` 1/día; refiltrar una vez; `devolver(page, a_mover)`; llamadas en `main()`; historial del docstring |
| `stock/credenciales.py` | `PLANTILLA` con `erp_clave_bot` y `devolver` |
| `stock/herramientas/ensayo_devolver.py` (nuevo) | Ensayo de solo lectura de los filtros de devolver |
| `stock/tests/test_erp_bot.py`, `test_bot_devolver.py` (nuevos), `test_bot_revisar.py`, `test_bot_exportar.py` | Tests |
| `docs/stock/2026-11-25-bot-devoluciones-instalacion.md` (nuevo) | Qué cambia, fases, vuelta atrás |
| `C:\Users\dev\Documents\Apuntes\prompt-instalar-bot-devoluciones.md` (nuevo, fuera del repo) | Prompt USB para el Claude del PC del bot |

---

### Task 1: `erp_bot.py` — lista de devoluciones e informe de movimientos

**Files:** Create `stock/erp_bot.py`, `stock/tests/test_erp_bot.py`; Modify `stock/credenciales.py` (`PLANTILLA`).

**Interfaces (Produces):**
- `RUN_ERP: str` — `datetime.now().strftime("%Y-%m-%dT%H:%M:%S")` al importar.
- `RUTA_DEVOLUCIONES = "/api/bot/devoluciones"`, `RUTA_MOVIMIENTOS = "/api/bot/movimientos"`, `TIMEOUT_S = 30`, `INTENTOS = 2`,
  `ESPERA_S = 5`, `TANDA = 1000`, `FICHERO_PENDIENTES = CARPETA_SISTEMA / "bot_movimientos_pendientes.json"`.
- `activo() -> bool`: hay `erp_url` y `erp_clave_bot`.
- `devoluciones() -> list[dict] | None`: `[{"destino": str, "imeis": [{"imei": str, "motivo": str}]}]`; `None` si no está activo o
  falla (nunca lanza). Valida forma: descarta grupos sin destino y IMEI que no son 15 cifras.
- `movimiento(imei, accion, resultado, origen, destino, motivo=None, fecha=None) -> dict` con `fecha` `%Y-%m-%dT%H:%M:%S` (ahora
  si no se da).
- `informar(movimientos: list[dict], log) -> bool`: junta los pendientes del fichero + los nuevos, los manda en tandas de
  `TANDA` con `{"run": RUN_ERP, "movimientos": [...]}`; si todo va bien borra el fichero; si falla una tanda, guarda en el fichero
  esa tanda y las siguientes (escritura atómica: `.tmp` + `replace`) y devuelve False. Sin ERP activo: guarda todo en pendientes
  y devuelve False (así, al configurar la clave, se envía lo acumulado). Nunca lanza.

Notas: los pendientes llevan su propio `run` (el de la vuelta en que se movieron): el fichero guarda `[{"run": ..., "movimientos":
[...]}]` y se reenvía cada bloque con su `run`, para que el ERP descarte bien los repetidos.

- [ ] **Step 1: Tests que fallan** — `tests/test_erp_bot.py`, con un fixture `erp` que parchee `requests.get`/`requests.post` con
  colas de respuestas falsas (copiar `_Respuesta` y la caja de llamadas de `tests/test_bot_erp.py`), `config` que parchee
  `erp_bot.credenciales.valor` (`erp_url`, `erp_clave_bot`) y `erp_bot.time.sleep`, y `FICHERO_PENDIENTES` en `tmp_path`:
  - `devoluciones` sin clave → None sin llamar; con 200 → la lista limpia (descarta IMEI raros y grupos sin destino); 500 dos
    veces → None; la cabecera es `X-API-Key: <erp_clave_bot>` y la URL `<erp_url>/api/bot/devoluciones`.
  - `informar` con ERP OK → un POST con `run` = `RUN_ERP` y los movimientos, devuelve True, no queda fichero.
  - `informar` con ERP caído → False y el fichero tiene el bloque; la siguiente llamada OK manda primero el bloque pendiente
    (con su `run`) y luego el nuevo, y borra el fichero.
  - 2500 movimientos → 3 POST (1000, 1000, 500).
  - Sin ERP activo → no llama, guarda pendientes.
  - Un fichero de pendientes corrupto → se renombra a `.corrupto-<fecha>` con `[AVISO]` y se sigue (no lanza).
- [ ] **Step 2: Ver que fallan** (`python -m pytest tests/test_erp_bot.py -q` → ImportError).
- [ ] **Step 3: Implementar** `erp_bot.py` siguiendo el estilo de `obtener_imeis_del_erp` (`requests` importado dentro, nunca
  lanza, `[AVISO]` con `log`). Añadir a `credenciales.PLANTILLA`: `"erp_clave_bot": ""` (opcional) y `"devolver": false`.
- [ ] **Step 4: Ver que pasan** y la suite completa.
- [ ] **Step 5: Commit** `feat(stock): erp_bot, lista de devoluciones e informe de movimientos al ERP con pendientes`.

### Task 2: el bot informa de lo que descuenta

**Files:** Modify `stock/nsys_bot_completo.py`; Test `stock/tests/test_bot_exportar.py` (y uno nuevo si queda más claro).

**Interfaces:** Consumes `erp_bot.movimiento`, `erp_bot.informar`. Produces `MOVIMIENTOS_ERP: list[dict]` (global del módulo,
como `IMEIS_MOVIDOS_RUN`).

- En `bucle_mover_todo`, donde hoy se registran los movidos (`registrar_movimientos(filas, "movido", ...)`, ~línea 919-920): las
  filas leídas antes de mover ya traen `Location` (se leen con `("IMEI/SN","Goods","Location")`); por cada una, añadir
  `erp_bot.movimiento(imei, "DESCONTADO", "OK", origen=Location, destino=LOCATION_DESTINO)` a `MOVIMIENTOS_ERP`. Si la lectura no
  trajera Location para esa fila, origen `None` (el ERP lo tratará como «sin caja»: no se devolverá solo).
- En `main()`, después de exportar Items (y de la auditoría), en su propio try/except: `erp_bot.informar(MOVIMIENTOS_ERP, log)` y
  una línea `ERP: informados N` (o `ERP: N pendientes`). Si `MOVIMIENTOS_ERP` está vacío, igual se llama (reenvía pendientes).
- Tests: la vuelta normal llama a `informar` con los DESCONTADO de lo movido (IMEI y origen); un fallo de `informar` no cambia el
  código de salida ni la exportación; el orden: exportar antes que informar.
- Commit: `feat(stock): el bot informa al ERP de lo que pasa a Pedidos realizados`.

### Task 3: `en_revisar` una vez al día y refiltrar una sola vez

**Files:** Modify `stock/nsys_bot_completo.py`; Test `stock/tests/test_bot_revisar.py`.

- `imeis_en_revisar_hoy() -> set[str]`: como `imeis_movidos_hoy()`, para `resultado == "en_revisar"` y fecha de hoy.
  `registrar_en_revisar(imeis)`: escribe solo los que no están en ese conjunto (lo usan `mover_sin_tocar_revisar` y la auditoría).
- `mover_sin_tocar_revisar`: si `bucle_mover_todo` devuelve filas en revisar, **leer todas las páginas que quedan** con
  `_leer_filas_visibles(page, ("IMEI/SN","Goods","Location"))` + `_pasar_pagina` (tope 100 páginas, como la auditoría), juntar
  todos los IMEI en revisar, quitarlos de la lista, `registrar_en_revisar`, `limpiar_filtros` y **refiltrar una sola vez**. Si en
  la segunda pasada vuelve a haber alguno en revisar (alguien lo movió entre medias): no se mueve esa página, `[AVISO]` y se
  sigue. Se quita `INTENTOS_FILTRO_REVISAR` (y su uso en el test `test_tope_de_intentos`, que pasa a comprobar «como mucho dos
  configuraciones de filtros»).
- Tests: revisar en la página 2 se detecta (antes no); dos vueltas el mismo día → una sola línea `en_revisar` por IMEI; un día
  nuevo → otra línea; segunda pasada con revisar → AVISO sin bucle; sin revisar → una sola configuración (como hoy).
- Commit: `feat(stock): en_revisar una vez al dia y una sola vuelta a filtrar leyendo todas las paginas`.

### Task 4: devolver

**Files:** Modify `stock/nsys_bot_completo.py`; Create `stock/tests/test_bot_devolver.py`.

**Interfaces:** Consumes `erp_bot.devoluciones`, `erp_bot.movimiento`, `MOVIMIENTOS_ERP`. Produces
`configurar_filtros_devolver(page, imeis)`, `mover_pagina_actual(page, destino=LOCATION_DESTINO)`,
`devolver_grupo(page, destino, imeis) -> dict[str, str]` (IMEI → `OK` / `NO_APARECE` / `ERROR`),
`devolver(page, a_mover) -> int` (devueltos), `TOPE_GRUPOS_DEVOLVER = 5`, `devolver_activo() -> bool`.

- `devolver_activo()`: `credenciales.cargar().get("devolver") is True` (solo un booleano `true` lo enciende).
- `mover_pagina_actual(page, destino=LOCATION_DESTINO)`: el destino sustituye al `LOCATION_DESTINO` fijo (línea ~826) y al texto
  del log (~841). Llamadas actuales sin cambios.
- `configurar_filtros_devolver(page, imeis)`: fila 0 lista de IMEI (`_abrir_filtros_y_pegar_imeis`), fila 1 `Location` /
  `Equals` / `LOCATION_DESTINO`, fila 2 `Status` / `Not equals` / `Released` (3 de las 4 filas), `_aplicar_filtros`. Mismo patrón
  que `configurar_filtros`.
- `devolver_grupo(page, destino, imeis)`: `limpiar_filtros`, `configurar_filtros_devolver`, `poner_50_filas_por_pagina`; bucle
  (tope 50, como `bucle_mover_todo`): si la tabla está vacía, fin; leer la página con `("IMEI/SN","Goods","Location")`; **si alguna
  fila no está en `LOCATION_DESTINO`, no mover esa página** (`[AVISO]`, esos IMEI → `ERROR`, fin del grupo); si alguna está en
  `LOCATIONS_NO_TOCAR`, igual; si no, `mover_pagina_actual(page, destino)`, `registrar_movimientos(filas, "devuelto")` y esos IMEI →
  `OK`. Al acabar: los IMEI del grupo que no aparecieron → `NO_APARECE` (`registrar_movimientos(..., "no_aparece_devolver")`).
  Cualquier excepción a mitad: los que quedaban → `ERROR` (`"error_devolver"`), `[AVISO]` con la etapa, y se devuelve el dict (no
  relanza). Cada resultado va a `MOVIMIENTOS_ERP` como `erp_bot.movimiento(imei, "DEVUELTO", resultado, origen=LOCATION_DESTINO,
  destino=destino, motivo=<el de la lista>)`.
- **Vendidos del Panel (petición de Imad 25/11):** `obtener_rezagados` ya descarga las ventas del Panel de 14 días (Simulador +
  PEDIDOS 2026, `accion=verificacion`); guardar también el conjunto completo de esas IMEI en un global `VENDIDOS_PANEL: set[str]`
  (además de lo que devuelve hoy), sin cambiar su resultado. Si la descarga falla, `VENDIDOS_PANEL = None`.
- `devolver(page, a_mover)`: si `not devolver_activo()` → 0 (sin pedir la lista). `grupos = erp_bot.devoluciones()`; `None` →
  `[AVISO]`, 0. **Si `VENDIDOS_PANEL` es `None` (no se pudo saber qué vendió el Panel) → no se devuelve nada en esta vuelta
  (`[AVISO]`, 0): en la duda, no.** Quitar de cada grupo los IMEI de `a_mover` (vender gana) **y los de `VENDIDOS_PANEL`** (el ERP no ve
  esas ventas: un reservado que se vendió por el Simulador no debe volver a stock); estos últimos se apuntan en `movimientos.csv`
  como `vendido_panel_no_devolver` una vez al día por IMEI (mismo lector que `en_revisar`) y **no se informan al ERP** (el contrato
  de la 0.9.47 solo admite OK / NO_APARECE / ERROR; si el ERP añade un resultado para esto, se informará entonces); quitar grupos vacíos y grupos cuyo destino esté en
  `LOCATIONS_NO_TOCAR` o sea `LOCATION_DESTINO` (defensa: el ERP nunca debería mandarlos). Los primeros `TOPE_GRUPOS_DEVOLVER`
  grupos, uno a uno con `devolver_grupo`. Línea final `DEVUELTOS: n (grupos g de G)`.
- `main()`: tras `mover_sin_tocar_revisar` y **antes** de `limpiar_filtros` + `exportar_xls`, en try/except propio:
  `etapa("devolver"); devolver(page, a_mover)`. Una excepción → `[AVISO]` y se sigue a exportar.
- Tests (monkeypatch como `test_bot_revisar.py`, con una `_Tabla` falsa): interruptor apagado → no pide la lista ni filtra; lista
  `None` → nada; vender gana; un IMEI vendido en el Panel no se devuelve ni se informa y queda en el csv una vez al día;
  `VENDIDOS_PANEL` desconocido → no se devuelve nada; tope de 5 grupos; página con una fila fuera de «Pedidos realizados» → no se mueve y ERROR; fila en
  revisar → no se mueve; NO_APARECE para los que no salen; excepción a mitad → ERROR para los que quedan y el siguiente grupo sigue;
  `mover_pagina_actual` recibe el destino del grupo; los DEVUELTO llegan a `MOVIMIENTOS_ERP` con el motivo; en `test_bot_exportar`,
  una excepción en `devolver` no impide exportar y el orden es mover → devolver → exportar → informar.
- Commit: `feat(stock): el bot devuelve a su caja los IMEI que le da el ERP (interruptor devolver)`.

### Task 5: ensayo de solo lectura

**Files:** Create `stock/herramientas/ensayo_devolver.py`.

Copia de `herramientas/ensayo_revisar.py` (mismos silencios: `b.log = print`, `b.etapa`, `b.registrar_movimientos` anulados;
navegador headless 1920×1080 con portapapeles). Uso: `python herramientas\ensayo_devolver.py` (pide la lista al ERP con
`erp_bot.devoluciones()` y ensaya los primeros 5 grupos) o `python herramientas\ensayo_devolver.py IMEI [IMEI ...]` (un grupo
de prueba con esos IMEI). Por grupo: `configurar_filtros_devolver`, `poner_50_filas_por_pagina`, leer TODAS las páginas con
`("IMEI/SN","Goods","Status","Location")` y `_pasar_pagina`, imprimir filas, cuántas fuera de «Pedidos realizados», cuántas en
revisar, los IMEI que no aparecen y el tiempo del grupo. **Nunca selecciona ni mueve.** Al final, tiempo total.

- [ ] Test mínimo: que el módulo importa y que `ensayo` no llama a `mover_pagina_actual` (monkeypatch que falle si se llama).
- [ ] Commit: `feat(stock): ensayo de solo lectura de los filtros de devolver`.

### Task 6: documentación, revisión e instalación

- Docstring de `nsys_bot_completo.py`: entrada del historial (informar al ERP, devolver con interruptor, en_revisar 1/día,
  refiltrar una vez).
- `docs/stock/2026-11-25-bot-devoluciones-instalacion.md`: qué cambia; claves nuevas de `stock_config.json` (`erp_clave_bot` =
  clave `botdev` de producción, `devolver`); fases (1 informar con `devolver: false`; 2 ensayo con Imad; 3 `devolver: true`);
  qué mirar en `movimientos.csv` y en el log; vuelta atrás (`devolver: false` sin reinstalar; restaurar `.bak_<fecha>`).
- Suite completa en verde; revisión final de la rama (opus).
- **Fuera del repo:** `C:\Users\dev\Documents\Apuntes\prompt-instalar-bot-devoluciones.md`, prompt para el Claude del PC del bot
  (va por USB con los ficheros): revisar primero lo instalado (diff contra lo que llega), copias `.bak_<fecha>` de cada fichero que
  se sustituye y de `stock_config.json`, copiar, añadir a `stock_config.json` `erp_clave_bot` (Imad la copia de
  `/root/claves-pedidos-prod.txt`, línea `botdev=`) y `"devolver": false`, pasar los tests, esperar una vuelta del ciclo y
  comprobar: Items exportado, línea `ERP: informados N` sin AVISO, y que no queda `bot_movimientos_pendientes.json`. Un fallo de
  login no se debe al cambio: no restaurar por eso. Traer de vuelta lo que cambió para guardarlo en el repo.
- Comprobación en el ERP tras la instalación (bloque de solo lectura para prod): `SELECT ACCION, RESULTADO, COUNT(*) FROM
  Bot_movimiento GROUP BY 1,2` y `GET /api/bot/devoluciones` (lo que daría la lista, sin mover nada).
