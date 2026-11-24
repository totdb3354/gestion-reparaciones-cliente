# Saneamiento de «Pedidos realizados» — plan de implementación (1 de 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un Excel en `Documents/Apuntes` con los móviles que el bot movió a «Pedidos realizados» y que ya no tienen venta que
los respalde, para revisarlos a mano con los móviles delante. No mueve nada.

**Architecture:** Una consulta SQL de solo lectura que Imad pasa en la VM (salida TSV de `mariadb -B`) y un script de Python
en su PC que la cruza con `movimientos.csv` del bot (traído por USB) y con las ventas del Panel (Web App, como mucho 14 días),
y escribe el Excel. Spec: `docs/superpowers/specs/2026-11-24-bot-devoluciones-design.md` §9 y decisión 2.

**Tech Stack:** MariaDB 11 (consulta), Python 3 + openpyxl + requests (script), pytest. Todo en el repo privado de
automatización (`C:\Users\dev\Documents\Automatizaciones\Automatizacion`), que ya trae las tres librerías.

## Global Constraints

- **Solo lectura.** La consulta solo hace SELECT y tablas temporales de la sesión, con `READ COMMITTED`. El script no toca NSYS,
  el ERP ni el log del bot.
- **IMEI reales:** el TSV y el Excel van a `C:\Users\dev\Documents\Apuntes\saneamiento\`, nunca a un repo.
- **Claude no hace SSH a las VMs:** prepara los bloques y los ejecuta Imad. Cada bloque empieza con `hostname` y
  `ls -l /usr/local/sbin/backup-erp.sh` (solo existe en prod).
- **Comandos para el PC en PowerShell 5.1:** sin `&&` y con rutas completas.
- **Commits sin Co-Authored-By.** Rama `dev_bot_devoluciones` del repo de automatización, en el worktree
  `C:\Users\dev\Documents\wt\auto-bot-devoluciones`. Confirmar con Imad antes de merge o push.
- **Entra en la lista** (decisión 2): lo movió el bot (`movimientos.csv`, resultado `movido`), sigue `DE_PEDIDO` en
  «Pedidos realizados», no está en ninguna fila de Pedidos (`Pedido_fila.IMEI`, con las del histórico) y no está en las ventas
  del Panel. El Panel solo cubre 14 días: más allá, «SIN COMPROBAR».
- **Destino** (decisión 3): la caja de NSYS de antes de entrar en «Pedidos realizados» (`A_NSYS` del evento UBICACION o
  ENTRADA anterior). Solo es VENDIBLE si la caja está en `Nsys_ubicacion` con `CUENTA_STOCK` y sin `ES_PEDIDO`, se trae
  (`UBICACION` no nula), no es de revisar (`LOCATIONS_NO_TOCAR` del bot) y el grado no es F, FD ni RMA. Una caja vacía o
  desconocida es SIN_CAJA. Nunca se inventa una caja.

## Lo que hay que saber del código

- `Pedido_fila_cambio` (`gestion-reparaciones-servidor/sql/crear_bd.sql:713`): un cambio de IMEI es `TIPO = 'IMEI'` con
  `IMEI_ANTERIOR` / `IMEI_NUEVO`. El intercambio de Amazon escribe además dos `IMEI` (`AsociacionAmazon.ponerImei`); su
  `IMEI_INTERCAMBIADO` lleva los IMEI nulos. El traspaso solo rellena filas vacías (`SQL_TRASPASAR_IMEI`): nunca quita un IMEI.
- `Nsys_movil` (clave `IMEI_SN` + `BATCH`), con `ESTADO = 'DE_PEDIDO'` = vivo en la caja de pedidos. `Nsys_evento`: en
  UBICACION y ENTRADA, `A_NSYS` es la caja de NSYS tal cual. La ENTRADA del esqueleto la tiene nula.
- `Nsys_ubicacion(LOCATION, UBICACION, CUENTA_STOCK, ES_PEDIDO)`.
- MariaDB no deja abrir dos veces la misma tabla temporal en una sentencia («Can't reopen table»): cada temporal se usa una sola
  vez por sentencia.
- `movimientos.csv` del bot: separador `;`, UTF-8 con BOM, columnas
  `fecha;hora;run;ciclo;imei;modelo;resultado;detalle`. Se guarda desde el primer día.
- Web App del Panel: `GET url?token=…&accion=verificacion&hoja=simulador|pedidos&dias=N` (N ≤ 14) →
  `{"ok": true, "ventas": [{"imei": "…", "fecha": "AAAA-MM-DD"}, …]}`. Credenciales con `credenciales.requerido("webapp_url")`
  y `("webapp_token")`, desde `Automatizacion_datos\sistema\stock_config.json`, que también está en el PC de Imad.
- Los tests del repo de automatización van en `stock/tests/`. `conftest.py` añade `stock/` a `sys.path`.

## Archivos

- Create: `stock/herramientas/saneamiento_pedidos_realizados.sql`: la consulta.
- Create: `stock/herramientas/saneamiento_pedidos_realizados.py`: el cruce y el Excel.
- Create: `stock/tests/test_saneamiento.py`.

---

### Task 1: worktree y lectura de las entradas

**Files:**
- Create: `stock/herramientas/saneamiento_pedidos_realizados.py`
- Test: `stock/tests/test_saneamiento.py`

**Interfaces:**
- Produces: `leer_consulta(ruta) -> list[dict]` (valores `str` o `None`), `leer_movidos(ruta) -> dict[str, list[str]]`,
  `ventas_de_respuestas(respuestas: list[dict]) -> set[str]`.

- [ ] **Step 1: Crear el worktree**

```bash
cd /c/Users/dev/Documents/Automatizaciones/Automatizacion
git fetch -q origin
git status -sb | head -1
git worktree add /c/Users/dev/Documents/wt/auto-bot-devoluciones -b dev_bot_devoluciones main
```

Expected: `## main...origin/main` sin `ahead`/`behind` y el worktree creado. Si `main` va por detrás de origin, parar y
preguntar a Imad.

- [ ] **Step 2: Escribir los tests que fallan**

`stock/tests/test_saneamiento.py`:

```python
# -*- coding: utf-8 -*-
"""Tests del saneamiento de «Pedidos realizados» (sin red ni base de datos)."""
import sys
from datetime import date
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent / "herramientas"))
import saneamiento_pedidos_realizados as s  # noqa: E402


def _tsv(tmp_path, filas):
    ruta = tmp_path / "consulta.tsv"
    ruta.write_text("\n".join("\t".join(f) for f in filas) + "\n", encoding="utf-8")
    return ruta


def test_leer_consulta_convierte_null_en_none(tmp_path):
    ruta = _tsv(tmp_path, [["IMEI_SN", "ORIGEN_NSYS", "FILAS_HOY"],
                           ["351111111111111", "A-12", "NULL"]])
    assert s.leer_consulta(ruta) == [{"IMEI_SN": "351111111111111", "ORIGEN_NSYS": "A-12", "FILAS_HOY": None}]


def test_leer_consulta_no_interpreta_comillas(tmp_path):
    ruta = _tsv(tmp_path, [["IMEI_SN", "GOODS"], ["351111111111111", 'iPhone 13 "Pro"']])
    assert s.leer_consulta(ruta)[0]["GOODS"] == 'iPhone 13 "Pro"'


def test_leer_movidos_solo_los_movidos(tmp_path):
    ruta = tmp_path / "movimientos.csv"
    ruta.write_text(
        "fecha;hora;run;ciclo;imei;modelo;resultado;detalle\n"
        "2026-11-20;10:00:00;r1;1;351111111111111;13;movido;\n"
        "2026-11-21;10:00:00;r2;;351111111111111;;en_revisar;\n"
        "2026-11-22;10:00:00;r3;1; 352222222222222 ;13;movido;\n",
        encoding="utf-8-sig")
    assert s.leer_movidos(ruta) == {"351111111111111": ["2026-11-20"], "352222222222222": ["2026-11-22"]}


def test_ventas_de_respuestas_junta_las_dos_hojas():
    respuestas = [{"ok": True, "ventas": [{"imei": " 351111111111111", "fecha": "2026-11-20"}]},
                  {"ok": True, "ventas": [{"imei": "352222222222222", "fecha": "2026-11-21"}, {"imei": ""}]}]
    assert s.ventas_de_respuestas(respuestas) == {"351111111111111", "352222222222222"}


def test_ventas_de_respuestas_falla_si_una_hoja_no_responde_bien():
    with pytest.raises(ValueError):
        s.ventas_de_respuestas([{"ok": True, "ventas": []}, {"ok": False, "error": "token"}])
```

- [ ] **Step 3: Ver que fallan**

Run: `cd /c/Users/dev/Documents/wt/auto-bot-devoluciones/stock && python -m pytest tests/test_saneamiento.py -q`
Expected: error de import (`No module named 'saneamiento_pedidos_realizados'`).

- [ ] **Step 4: Implementar la lectura**

`stock/herramientas/saneamiento_pedidos_realizados.py`:

```python
# -*- coding: utf-8 -*-
"""Saneamiento de «Pedidos realizados» (spec del ERP 2026-11-24-bot-devoluciones-design.md §9). SOLO LEE Y ESCRIBE UN EXCEL.

Cruza la consulta de produccion (saneamiento_pedidos_realizados.sql, salida de `mariadb -B`) con movimientos.csv del bot y
con las ventas del Panel (Web App, como mucho 14 dias), y deja en un Excel la lista para revisar a mano con los moviles
delante. No toca NSYS ni el ERP, ni escribe en el log del bot.

Uso (en el PC de Imad, desde stock\\):
    python herramientas\\saneamiento_pedidos_realizados.py consulta.tsv movimientos.csv salida.xlsx [--sin-panel]
"""
import argparse
import csv
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

GRADOS_NO_VENDIBLES = {"F", "FD", "RMA"}
DIAS_PANEL = 14
HOJAS_PANEL = ("simulador", "pedidos")
NULO = "NULL"


def _valor(texto):
    return None if texto is None or texto == NULO else texto


def leer_consulta(ruta):
    """Las filas de la salida de `mariadb -B`: tabulador, cabecera, NULL = nulo, sin comillas."""
    with open(ruta, newline="", encoding="utf-8") as f:
        lector = csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
        return [{k: _valor(v) for k, v in fila.items()} for fila in lector]


def leer_movidos(ruta):
    """{imei: [fechas 'AAAA-MM-DD' en que el bot lo movio a Pedidos realizados]} segun movimientos.csv."""
    movidos = {}
    with open(ruta, newline="", encoding="utf-8-sig") as f:
        for fila in csv.DictReader(f, delimiter=";"):
            if fila.get("resultado") == "movido":
                imei = (fila.get("imei") or "").strip()
                if imei:
                    movidos.setdefault(imei, []).append(fila.get("fecha", ""))
    return movidos


def ventas_de_respuestas(respuestas):
    """Los IMEI de las ventas de las respuestas de la Web App. ValueError si alguna no es valida: mejor no comprobar el
    Panel que comprobarlo a medias."""
    imeis = set()
    for r in respuestas:
        if not r.get("ok") or not isinstance(r.get("ventas"), list):
            raise ValueError(f"respuesta no valida del Panel: {str(r.get('error', 'sin ventas'))[:80]}")
        for v in r["ventas"]:
            imei = str(v.get("imei", "")).strip()
            if imei:
                imeis.add(imei)
    return imeis
```

- [ ] **Step 5: Ver que pasan**

Run: `python -m pytest tests/test_saneamiento.py -q`
Expected: `5 passed`.

- [ ] **Step 6: Commit**

```bash
git add stock/herramientas/saneamiento_pedidos_realizados.py stock/tests/test_saneamiento.py
git commit -m "feat(stock): saneamiento de Pedidos realizados, lectura de la consulta, movimientos.csv y Panel"
```

---

### Task 2: clasificar cada móvil

**Files:**
- Modify: `stock/herramientas/saneamiento_pedidos_realizados.py`
- Test: `stock/tests/test_saneamiento.py`

**Interfaces:**
- Consumes: lo de la Task 1.
- Produces: `destino(fila, cajas_revisar) -> tuple[str | None, str, str]` (caja, `VENDIBLE` / `NO_VENDIBLE` / `SIN_CAJA`,
  motivo), `clasificar(fila, movidos, ventas, cajas_revisar, hoy) -> dict` (la fila más `ENTRADA`, `DIAS`, `BOT`, `PANEL`,
  `DESTINO`, `DESTINO_ESTADO`, `DESTINO_MOTIVO`, `DESCARTE`). `DESCARTE` es `None` si es candidato.

- [ ] **Step 1: Tests que fallan** (añadir al final de `test_saneamiento.py`)

```python
REVISAR = ("Pendiente revisar si hay error de inventario",)
HOY = date(2026, 11, 24)


def _fila(**cambios):
    fila = {"IMEI_SN": "351111111111111", "GRADE": "A", "FECHA_ENTRADA": "2026-11-20 10:15:00",
            "ORIGEN_NSYS": "A-12", "ORIGEN_ERP": "Estantería A", "ORIGEN_CUENTA_STOCK": "1", "ORIGEN_ES_PEDIDO": "0",
            "FILAS_HOY": None}
    fila.update(cambios)
    return fila


@pytest.mark.parametrize("cambios, esperado", [
    ({}, ("A-12", "VENDIBLE", "")),
    ({"ORIGEN_NSYS": None}, (None, "SIN_CAJA", "sin caja conocida")),
    ({"ORIGEN_NSYS": ""}, (None, "SIN_CAJA", "sin caja conocida")),
    ({"GRADE": "fd "}, ("A-12", "NO_VENDIBLE", "grado FD")),
    ({"ORIGEN_NSYS": REVISAR[0]}, (REVISAR[0], "NO_VENDIBLE", "caja de revisar")),
    ({"ORIGEN_ES_PEDIDO": "1"}, ("A-12", "NO_VENDIBLE", "caja de pedidos")),
    ({"ORIGEN_CUENTA_STOCK": None, "ORIGEN_ERP": None, "ORIGEN_ES_PEDIDO": None},
     ("A-12", "NO_VENDIBLE", "caja que el ERP no conoce")),
    ({"ORIGEN_ERP": None, "ORIGEN_CUENTA_STOCK": "0"}, ("A-12", "NO_VENDIBLE", "caja que no se trae")),
    ({"ORIGEN_CUENTA_STOCK": "0"}, ("A-12", "NO_VENDIBLE", "caja que no cuenta como stock")),
])
def test_destino(cambios, esperado):
    assert s.destino(_fila(**cambios), REVISAR) == esperado


def test_candidato_movido_por_el_bot_sin_fila_ni_panel():
    r = s.clasificar(_fila(), {"351111111111111": ["2026-11-20"]}, set(), REVISAR, HOY)
    assert (r["DESCARTE"], r["BOT"], r["PANEL"], r["DIAS"], r["DESTINO_ESTADO"]) == (None, "2026-11-20", "NO", 4, "VENDIBLE")


def test_el_bot_lo_movio_el_dia_antes_de_la_entrada_cuenta():
    r = s.clasificar(_fila(FECHA_ENTRADA="2026-11-21 00:05:00"), {"351111111111111": ["2026-11-20"]}, set(), REVISAR, HOY)
    assert r["BOT"] == "2026-11-20"


def test_un_movimiento_del_bot_de_otra_entrada_no_cuenta():
    r = s.clasificar(_fila(), {"351111111111111": ["2026-10-01"]}, set(), REVISAR, HOY)
    assert (r["BOT"], r["DESCARTE"]) == (None, "no lo movio el bot")


def test_sin_fecha_de_entrada_vale_el_ultimo_movimiento_del_bot():
    r = s.clasificar(_fila(FECHA_ENTRADA=None), {"351111111111111": ["2026-10-01", "2026-11-02"]}, set(), REVISAR, HOY)
    assert (r["BOT"], r["DIAS"], r["DESCARTE"]) == ("2026-11-02", None, None)


def test_en_una_fila_hoy_se_descarta():
    r = s.clasificar(_fila(FILAS_HOY="812"), {"351111111111111": ["2026-11-20"]}, set(), REVISAR, HOY)
    assert r["DESCARTE"] == "esta en una fila de Pedidos"


def test_con_venta_en_el_panel_se_descarta():
    r = s.clasificar(_fila(), {"351111111111111": ["2026-11-20"]}, {"351111111111111"}, REVISAR, HOY)
    assert (r["PANEL"], r["DESCARTE"]) == ("SI", "venta en el Panel")


def test_entrada_de_hace_mas_de_14_dias_el_panel_no_se_comprueba():
    r = s.clasificar(_fila(FECHA_ENTRADA="2026-11-01 09:00:00"), {"351111111111111": ["2026-11-01"]}, set(), REVISAR, HOY)
    assert (r["PANEL"], r["DESCARTE"]) == ("SIN COMPROBAR", None)


def test_sin_panel_todo_sin_comprobar():
    r = s.clasificar(_fila(), {"351111111111111": ["2026-11-20"]}, None, REVISAR, HOY)
    assert (r["PANEL"], r["DESCARTE"]) == ("SIN COMPROBAR", None)
```

- [ ] **Step 2: Ver que fallan**

Run: `python -m pytest tests/test_saneamiento.py -q`
Expected: FAIL con `AttributeError: module 'saneamiento_pedidos_realizados' has no attribute 'destino'`.

- [ ] **Step 3: Implementar** (añadir después de `ventas_de_respuestas`)

```python
def destino(fila, cajas_revisar):
    """(caja, estado, motivo) de la caja donde estaba antes de Pedidos realizados. Nunca inventa una caja: sin caja
    conocida es SIN_CAJA; con caja que hoy no es vendible, NO_VENDIBLE con el motivo."""
    caja = (fila.get("ORIGEN_NSYS") or "").strip()
    if not caja:
        return None, "SIN_CAJA", "sin caja conocida"
    grado = (fila.get("GRADE") or "").strip().upper()
    if grado in GRADOS_NO_VENDIBLES:
        return caja, "NO_VENDIBLE", f"grado {grado}"
    if caja in cajas_revisar:
        return caja, "NO_VENDIBLE", "caja de revisar"
    if fila.get("ORIGEN_ES_PEDIDO") == "1":
        return caja, "NO_VENDIBLE", "caja de pedidos"
    if fila.get("ORIGEN_CUENTA_STOCK") is None:
        return caja, "NO_VENDIBLE", "caja que el ERP no conoce"
    if fila.get("ORIGEN_ERP") is None:
        return caja, "NO_VENDIBLE", "caja que no se trae"
    if fila.get("ORIGEN_CUENTA_STOCK") != "1":
        return caja, "NO_VENDIBLE", "caja que no cuenta como stock"
    return caja, "VENDIBLE", ""


def _fecha_entrada(fila):
    texto = fila.get("FECHA_ENTRADA")
    return datetime.strptime(texto[:10], "%Y-%m-%d").date() if texto else None


def _movido_por_el_bot(imei, entrada, movidos):
    """La fecha en que el bot lo movio para esta entrada: el dia de la entrada o el anterior (el espejo ve el movimiento
    hasta 15 min despues). Sin fecha de entrada, el ultimo movimiento. None si el bot no lo movio."""
    fechas = sorted(d for d in movidos.get(imei, ()) if d)
    if not fechas:
        return None
    if entrada is None:
        return fechas[-1]
    validas = [d for d in fechas if (entrada - timedelta(days=1)).isoformat() <= d <= entrada.isoformat()]
    return validas[-1] if validas else None


def _panel(imei, entrada, ventas, hoy):
    if ventas is None:
        return "SIN COMPROBAR"
    if imei in ventas:
        return "SI"
    if entrada is not None and (hoy - entrada).days < DIAS_PANEL:
        return "NO"
    return "SIN COMPROBAR"


def clasificar(fila, movidos, ventas, cajas_revisar, hoy):
    """La fila con lo que dice el cruce. DESCARTE None = candidato (decision 2 de la spec); si no, el motivo."""
    imei = (fila.get("IMEI_SN") or "").strip()
    entrada = _fecha_entrada(fila)
    bot = _movido_por_el_bot(imei, entrada, movidos)
    panel = _panel(imei, entrada, ventas, hoy)
    caja, estado, motivo = destino(fila, cajas_revisar)
    if bot is None:
        descarte = "no lo movio el bot"
    elif fila.get("FILAS_HOY"):
        descarte = "esta en una fila de Pedidos"
    elif panel == "SI":
        descarte = "venta en el Panel"
    else:
        descarte = None
    return {**fila, "ENTRADA": entrada, "DIAS": (hoy - entrada).days if entrada else None, "BOT": bot, "PANEL": panel,
            "DESTINO": caja, "DESTINO_ESTADO": estado, "DESTINO_MOTIVO": motivo, "DESCARTE": descarte}
```

- [ ] **Step 4: Ver que pasan**

Run: `python -m pytest tests/test_saneamiento.py -q`
Expected: `22 passed`.

- [ ] **Step 5: Commit**

```bash
git add stock/herramientas/saneamiento_pedidos_realizados.py stock/tests/test_saneamiento.py
git commit -m "feat(stock): saneamiento, clasificar cada movil (bot, fila, Panel) y su caja de antes"
```

---

### Task 3: el Excel y el `main`

**Files:**
- Modify: `stock/herramientas/saneamiento_pedidos_realizados.py`
- Test: `stock/tests/test_saneamiento.py`

**Interfaces:**
- Consumes: `clasificar`, `leer_consulta`, `leer_movidos`, `ventas_de_respuestas`.
- Produces: `escribir_excel(clasificadas, ruta, hoy, panel_comprobado: bool) -> None` y
  `main(argv) -> int` (0 bien, 2 error de uso).

- [ ] **Step 1: Tests que fallan**

```python
import openpyxl  # noqa: E402  (arriba del todo, con los demas imports)


def test_excel_candidatos_ordenados_y_descartados_aparte(tmp_path):
    movidos = {"351111111111111": ["2026-11-20"], "352222222222222": ["2026-11-01"], "353333333333333": ["2026-11-20"]}
    filas = [_fila(),
             _fila(IMEI_SN="352222222222222", FECHA_ENTRADA="2026-11-01 09:00:00", ORIGEN_NSYS=None,
                   SALIDA_FILA="900", SALIDA_USUARIO=None),
             _fila(IMEI_SN="353333333333333", FILAS_HOY="812")]
    clasificadas = [s.clasificar(f, movidos, set(), REVISAR, HOY) for f in filas]
    ruta = tmp_path / "s.xlsx"
    s.escribir_excel(clasificadas, ruta, HOY, panel_comprobado=True)
    wb = openpyxl.load_workbook(ruta)
    cand = list(wb["Candidatos"].iter_rows(values_only=True))
    cab = cand[0]
    assert cab[0] == "IMEI" and cab[-1] == "Comprobado"
    assert [f[0] for f in cand[1:]] == ["352222222222222", "351111111111111"]  # mas dias primero
    quien = cab.index("Le quitó el IMEI")
    assert cand[1][quien] == "proceso (sin usuario)"
    desc = list(wb["Descartados"].iter_rows(values_only=True))
    assert desc[0][-1] == "Descartado por" and desc[1][0] == "353333333333333"
    resumen = dict(r for r in wb["Resumen"].iter_rows(values_only=True) if r[0])
    assert resumen["Candidatos"] == 2 and resumen["En Pedidos realizados (DE_PEDIDO)"] == 3


def test_main_sin_panel(tmp_path):
    consulta = _tsv(tmp_path, [["IMEI_SN", "GRADE", "FECHA_ENTRADA", "ORIGEN_NSYS", "ORIGEN_ERP", "ORIGEN_CUENTA_STOCK",
                                "ORIGEN_ES_PEDIDO", "FILAS_HOY"],
                               ["351111111111111", "A", "2026-11-20 10:15:00", "A-12", "Estantería A", "1", "0", "NULL"]])
    mov = tmp_path / "movimientos.csv"
    mov.write_text("fecha;hora;run;ciclo;imei;modelo;resultado;detalle\n2026-11-20;10:00:00;r;1;351111111111111;13;movido;\n",
                   encoding="utf-8-sig")
    salida = tmp_path / "s.xlsx"
    assert s.main([str(consulta), str(mov), str(salida), "--sin-panel"]) == 0
    assert salida.exists()
```

- [ ] **Step 2: Ver que fallan**

Run: `python -m pytest tests/test_saneamiento.py -q`
Expected: FAIL con `AttributeError: … has no attribute 'escribir_excel'`.

- [ ] **Step 3: Implementar** (añadir al final del módulo)

```python
COLUMNAS = [
    ("IMEI", "IMEI_SN"), ("Lote", "BATCH"), ("Modelo", "MODELO_MAESTRO"), ("Capacidad", "CAPACIDAD"), ("Color", "COLOR"),
    ("Grado", "GRADE"), ("Caja hoy", "LOCATION"), ("Entrada en la caja", "FECHA_ENTRADA"), ("Días", "DIAS"),
    ("Movido por el bot", "BOT"), ("Venta en el Panel", "PANEL"), ("Caja de antes", "ORIGEN_NSYS"),
    ("Destino", "DESTINO_ESTADO"), ("Por qué", "DESTINO_MOTIVO"), ("Filas hoy", "FILAS_HOY"),
    ("Veces en una fila", "VECES_EN_FILA"), ("Última fila", "SALIDA_FILA"), ("Pedido", "SALIDA_PEDIDO"),
    ("Canal", "SALIDA_CANAL"), ("Le quitó el IMEI", None), ("Cuándo", "SALIDA_FECHA"), ("IMEI que puso", "SALIDA_IMEI_NUEVO"),
]


def _quien(fila):
    if not fila.get("SALIDA_FILA"):
        return None
    return fila.get("SALIDA_USUARIO") or "proceso (sin usuario)"


def _valores(fila):
    return [_quien(fila) if clave is None else fila.get(clave) for _, clave in COLUMNAS]


def _por_dias(fila):
    # Sin fecha de entrada (no se sabe desde cuando) va primero; despues, de mas dias a menos.
    return (fila["DIAS"] is not None, -(fila["DIAS"] or 0), fila.get("IMEI_SN") or "")


def escribir_excel(clasificadas, ruta, hoy, panel_comprobado):
    import openpyxl
    candidatos = sorted((f for f in clasificadas if f["DESCARTE"] is None), key=_por_dias)
    descartados = sorted((f for f in clasificadas if f["DESCARTE"] is not None), key=_por_dias)
    wb = openpyxl.Workbook()
    hoja = wb.active
    hoja.title = "Candidatos"
    hoja.append([t for t, _ in COLUMNAS] + ["Comprobado"])
    for f in candidatos:
        hoja.append(_valores(f) + [None])
    hoja.freeze_panes = "B2"
    hoja = wb.create_sheet("Descartados")
    hoja.append([t for t, _ in COLUMNAS] + ["Descartado por"])
    for f in descartados:
        hoja.append(_valores(f) + [f["DESCARTE"]])
    hoja.freeze_panes = "B2"
    hoja = wb.create_sheet("Resumen")
    hoja.append(["Generado", hoy.isoformat()])
    hoja.append(["Panel", f"comprobado ({DIAS_PANEL} días)" if panel_comprobado else "sin comprobar"])
    hoja.append(["En Pedidos realizados (DE_PEDIDO)", len(clasificadas)])
    hoja.append(["Candidatos", len(candidatos)])
    for estado in ("VENDIBLE", "NO_VENDIBLE", "SIN_CAJA"):
        hoja.append([f"  destino {estado}", sum(1 for f in candidatos if f["DESTINO_ESTADO"] == estado)])
    for motivo in ("no lo movio el bot", "esta en una fila de Pedidos", "venta en el Panel"):
        hoja.append([f"Descartados: {motivo}", sum(1 for f in descartados if f["DESCARTE"] == motivo)])
    wb.save(ruta)


def leer_ventas_panel():
    """Los IMEI vendidos en el Panel en los ultimos DIAS_PANEL dias (las dos hojas)."""
    import requests
    import credenciales
    url = credenciales.requerido("webapp_url")
    token = credenciales.requerido("webapp_token")
    respuestas = []
    for hoja in HOJAS_PANEL:
        r = requests.get(url, params={"token": token, "accion": "verificacion", "hoja": hoja, "dias": DIAS_PANEL},
                         timeout=120, allow_redirects=True)
        r.raise_for_status()
        respuestas.append(r.json())
    return ventas_de_respuestas(respuestas)


def _cajas_revisar():
    import nsys_bot_completo as bot
    return tuple(bot.LOCATIONS_NO_TOCAR)


def main(argv):
    p = argparse.ArgumentParser(description="Saneamiento de Pedidos realizados: solo lee y escribe un Excel.")
    p.add_argument("consulta", type=Path)
    p.add_argument("movimientos", type=Path)
    p.add_argument("salida", type=Path)
    p.add_argument("--sin-panel", action="store_true")
    a = p.parse_args(argv)
    ventas = None
    if not a.sin_panel:
        try:
            ventas = leer_ventas_panel()
        except Exception as e:  # sin Panel el Excel sale igual, con «SIN COMPROBAR»
            print(f"[AVISO] Panel no disponible: {str(e)[:120]}")
    hoy = date.today()
    cajas = _cajas_revisar()
    movidos = leer_movidos(a.movimientos)
    clasificadas = [clasificar(f, movidos, ventas, cajas, hoy) for f in leer_consulta(a.consulta)]
    escribir_excel(clasificadas, a.salida, hoy, panel_comprobado=ventas is not None)
    candidatos = sum(1 for f in clasificadas if f["DESCARTE"] is None)
    print(f"[OK] {len(clasificadas)} en Pedidos realizados, {candidatos} candidatos -> {a.salida}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
```

- [ ] **Step 4: Ver que pasan, y el resto del repo también**

Run: `python -m pytest tests/test_saneamiento.py -q` → `24 passed`.
Run: `python -m pytest tests -q` → todo verde (salvo los mismos fallos previos, si los hubiera: anotarlos antes de empezar).

- [ ] **Step 5: Commit**

```bash
git add stock/herramientas/saneamiento_pedidos_realizados.py stock/tests/test_saneamiento.py
git commit -m "feat(stock): saneamiento, Excel con candidatos, descartados y resumen"
```

---

### Task 4: la consulta SQL

**Files:**
- Create: `stock/herramientas/saneamiento_pedidos_realizados.sql`

**Interfaces:**
- Produces: TSV con las columnas `IMEI_SN, BATCH, GOODS, GRADE, MODELO_MAESTRO, CAPACIDAD, COLOR, LOCATION, FECHA_ENTRADA,
  ENTRADA_SOLO_DIA, ORIGEN_NSYS, ORIGEN_ERP, ORIGEN_CUENTA_STOCK, ORIGEN_ES_PEDIDO, FILAS_HOY, VECES_EN_FILA, SALIDA_FILA,
  SALIDA_PEDIDO, SALIDA_CANAL, SALIDA_FECHA, SALIDA_IMEI_NUEVO, SALIDA_USUARIO`, que son las que lee el script.

- [ ] **Step 1: Escribir la consulta**

```sql
-- Saneamiento de «Pedidos realizados» (spec del ERP docs/superpowers/specs/2026-11-24-bot-devoluciones-design.md §9).
-- SOLO LEE: las tablas temporales son de la sesion. Una fila por movil DE_PEDIDO (vivo en la caja de pedidos) con su entrada
-- en la caja, la caja de NSYS de antes y su paso por las filas de Pedidos. La cruza saneamiento_pedidos_realizados.py.
-- MariaDB no deja abrir dos veces una temporal en una sentencia: cada una se usa una vez por sentencia.
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
DROP TEMPORARY TABLE IF EXISTS tmp_dp, tmp_entrada, tmp_anterior, tmp_hoy, tmp_veces, tmp_salidas, tmp_ultima;

CREATE TEMPORARY TABLE tmp_dp (PRIMARY KEY (IMEI_SN, BATCH))
SELECT IMEI_SN, BATCH, GOODS, GRADE, MODELO_MAESTRO, CAPACIDAD, COLOR, LOCATION
  FROM Nsys_movil WHERE ESTADO = 'DE_PEDIDO';

-- La ultima entrada en la caja de hoy (UBICACION o ENTRADA cuyo A_NSYS es esa caja).
CREATE TEMPORARY TABLE tmp_entrada (PRIMARY KEY (IMEI_SN, BATCH))
SELECT d.IMEI_SN, d.BATCH,
       (SELECT e.ID_EVENTO FROM Nsys_evento e
         WHERE e.IMEI_SN = d.IMEI_SN AND e.BATCH = d.BATCH AND e.TIPO IN ('UBICACION', 'ENTRADA') AND e.A_NSYS = d.LOCATION
         ORDER BY e.FECHA DESC, e.ID_EVENTO DESC LIMIT 1) AS ID_ENTRADA
  FROM tmp_dp d;

-- El evento de caja anterior a esa entrada que dice la caja de NSYS (A_NSYS no nulo; vacio = sin caja).
CREATE TEMPORARY TABLE tmp_anterior (PRIMARY KEY (IMEI_SN, BATCH))
SELECT t.IMEI_SN, t.BATCH, t.ID_ENTRADA,
       (SELECT a.ID_EVENTO FROM Nsys_evento a
         WHERE a.IMEI_SN = t.IMEI_SN AND a.BATCH = t.BATCH AND a.TIPO IN ('UBICACION', 'ENTRADA') AND a.A_NSYS IS NOT NULL
           AND (a.FECHA < en.FECHA OR (a.FECHA = en.FECHA AND a.ID_EVENTO < en.ID_EVENTO))
         ORDER BY a.FECHA DESC, a.ID_EVENTO DESC LIMIT 1) AS ID_ANTERIOR
  FROM tmp_entrada t
  LEFT JOIN Nsys_evento en ON en.ID_EVENTO = t.ID_ENTRADA;

-- Las filas que lo tienen hoy (las del historico incluidas: son ventas de la hoja).
CREATE TEMPORARY TABLE tmp_hoy (PRIMARY KEY (IMEI))
SELECT f.IMEI, GROUP_CONCAT(f.ID_FILA ORDER BY f.ID_FILA) AS FILAS
  FROM Pedido_fila f WHERE f.IMEI IN (SELECT IMEI_SN FROM tmp_dp) GROUP BY f.IMEI;

-- Cuantas veces se puso en una fila.
CREATE TEMPORARY TABLE tmp_veces (PRIMARY KEY (IMEI))
SELECT c.IMEI_NUEVO AS IMEI, COUNT(*) AS VECES
  FROM Pedido_fila_cambio c WHERE c.TIPO = 'IMEI' AND c.IMEI_NUEVO IN (SELECT IMEI_SN FROM tmp_dp) GROUP BY c.IMEI_NUEVO;

-- La ultima vez que salio de una fila (a mano, por el libro o por el intercambio: todos escriben un apunte IMEI).
CREATE TEMPORARY TABLE tmp_salidas (KEY (IMEI))
SELECT c.ID_CAMBIO, c.IMEI_ANTERIOR AS IMEI
  FROM Pedido_fila_cambio c WHERE c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR IN (SELECT IMEI_SN FROM tmp_dp);
CREATE TEMPORARY TABLE tmp_ultima (PRIMARY KEY (IMEI))
SELECT IMEI, MAX(ID_CAMBIO) AS ID_CAMBIO FROM tmp_salidas GROUP BY IMEI;

SELECT d.IMEI_SN, d.BATCH, d.GOODS, d.GRADE, d.MODELO_MAESTRO, d.CAPACIDAD, d.COLOR, d.LOCATION,
       en.FECHA AS FECHA_ENTRADA, en.SOLO_DIA AS ENTRADA_SOLO_DIA,
       an.A_NSYS AS ORIGEN_NSYS, ub.UBICACION AS ORIGEN_ERP, ub.CUENTA_STOCK AS ORIGEN_CUENTA_STOCK,
       ub.ES_PEDIDO AS ORIGEN_ES_PEDIDO,
       h.FILAS AS FILAS_HOY, COALESCE(v.VECES, 0) AS VECES_EN_FILA,
       c.ID_FILA AS SALIDA_FILA, f.PEDIDO AS SALIDA_PEDIDO, f.CANAL AS SALIDA_CANAL, c.FECHA AS SALIDA_FECHA,
       c.IMEI_NUEVO AS SALIDA_IMEI_NUEVO, u.NOMBRE_USUARIO AS SALIDA_USUARIO
  FROM tmp_dp d
  JOIN tmp_anterior t ON t.IMEI_SN = d.IMEI_SN AND t.BATCH = d.BATCH
  LEFT JOIN Nsys_evento en ON en.ID_EVENTO = t.ID_ENTRADA
  LEFT JOIN Nsys_evento an ON an.ID_EVENTO = t.ID_ANTERIOR
  LEFT JOIN Nsys_ubicacion ub ON ub.LOCATION = an.A_NSYS
  LEFT JOIN tmp_hoy h ON h.IMEI = d.IMEI_SN
  LEFT JOIN tmp_veces v ON v.IMEI = d.IMEI_SN
  LEFT JOIN tmp_ultima tu ON tu.IMEI = d.IMEI_SN
  LEFT JOIN Pedido_fila_cambio c ON c.ID_CAMBIO = tu.ID_CAMBIO
  LEFT JOIN Pedido_fila f ON f.ID_FILA = c.ID_FILA
  LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU
 ORDER BY en.FECHA, d.IMEI_SN;
```

- [ ] **Step 2: Commit**

```bash
git add stock/herramientas/saneamiento_pedidos_realizados.sql
git commit -m "feat(stock): consulta de solo lectura del saneamiento de Pedidos realizados"
```

---

### Task 5: ensayo en preprod y pasada en producción (con Imad)

Claude prepara los comandos; **Imad los ejecuta**. Primero en preprod, que es una copia de prod: comprueba la sintaxis, el
tiempo y las columnas. Después en prod.

- [ ] **Step 1: Copiar la consulta a la VM** (PowerShell, en el PC de Imad)

```powershell
scp C:\Users\dev\Documents\wt\auto-bot-devoluciones\stock\herramientas\saneamiento_pedidos_realizados.sql preprod:/tmp/
```

- [ ] **Step 2: Pasarla** (en la VM, después de `ssh preprod`)

```bash
hostname
ls -l /usr/local/sbin/backup-erp.sh
time docker exec -i reparaciones-mariadb-1 sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" -B gestion_reparaciones' < /tmp/saneamiento_pedidos_realizados.sql > /tmp/saneamiento.tsv
wc -l /tmp/saneamiento.tsv
head -3 /tmp/saneamiento.tsv
```

Expected: en preprod, `ls` dice `No such file`. El comando no muestra nada mientras dura; después, `time` con la duración,
el número de líneas (cabecera + 1 por móvil `DE_PEDIDO`) y la cabecera con las 22 columnas de la Task 4. Si sale un error de
SQL, traer el mensaje y corregir la Task 4.

- [ ] **Step 3: Traer el TSV y generar el Excel de prueba** (PowerShell)

```powershell
New-Item -ItemType Directory -Force C:\Users\dev\Documents\Apuntes\saneamiento
scp preprod:/tmp/saneamiento.tsv C:\Users\dev\Documents\Apuntes\saneamiento\preprod.tsv
python C:\Users\dev\Documents\wt\auto-bot-devoluciones\stock\herramientas\saneamiento_pedidos_realizados.py C:\Users\dev\Documents\Apuntes\saneamiento\preprod.tsv C:\Users\dev\Documents\Apuntes\saneamiento\movimientos.csv C:\Users\dev\Documents\Apuntes\saneamiento\preprod.xlsx
```

Antes, Imad deja en `Apuntes\saneamiento\movimientos.csv` la copia por USB de
`Automatizacion_datos\sistema\movimientos.csv` del PC del bot. Expected: `[OK] N en Pedidos realizados, M candidatos`.
Claude abre el Excel y revisa con Imad unas cuantas filas contra el ERP (historial de la fila) y contra NSYS.

- [ ] **Step 4: Producción.** Los mismos Steps 1–3 con `prod` en vez de `preprod` y los ficheros `prod.tsv` / `prod.xlsx`. En
  prod, `ls -l /usr/local/sbin/backup-erp.sh` tiene que existir. Si no existe, no es prod: parar.

- [ ] **Step 5: Borrar el TSV de la VM** (contiene IMEI): `rm /tmp/saneamiento.tsv /tmp/saneamiento_pedidos_realizados.sql`
  en las dos VMs.

- [ ] **Step 6: Entregar a Imad** `Apuntes\saneamiento\prod.xlsx` con un resumen: cuántos candidatos hay, cuántos con destino
  VENDIBLE / NO_VENDIBLE / SIN_CAJA, y cinco o seis IMEI de ejemplo de los de más días para comprobar físicamente. **Fin del
  plan 1:** cómo se devuelven se decide después de la revisión (decisión 7).

---

## Lo que sigue

- **Plan 2 (ERP):** `Bot_movimiento`, `POST /api/bot/movimientos`, `GET /api/bot/devoluciones`, apuntes del historial y la
  pieza «no vendible». La marca `NO_VENDIBLE` del libro va encima de la 0.9.45.
- **Plan 3 (bot):** informar, devolver con interruptor, los dos pendientes, ensayo y prompt de instalación.
