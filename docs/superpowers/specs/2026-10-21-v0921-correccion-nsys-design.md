# 0.9.21 (pieza «corrección en NSYS») — Una baja y alta en otro lote es el mismo móvil

Fecha: 2026-10-21. Estado: aprobada por el usuario (21/10), pendiente del plan.
**Pieza de la 0.9.21**, la misma versión que el «SKU del taller» (decisión del usuario el 21/10: sin cuatro números).
Se programa primero y **entra la primera en `main`** del servidor y de la web; las ramas del SKU del taller salen de ahí.
Sube a producción con el resto de la 0.9.21. **Servidor + web, sin base de datos**: no cambia el esquema ni lo guardado.

## 1. Qué pasa

- Para corregir un móvil (color, capacidad, producto o grado; también cuando el taller lo transforma, p. ej. con un chasis
  de otro color), NSYS **da de baja el lote** (`Removed`) y **da de alta el IMEI en un lote nuevo**.
- **Medido en producción el 21/10:** de 1.339 bajas con hora en el rastro, unas 1.330 tienen otro lote del mismo IMEI
  recibido entre 5 días antes y 1 día después; la mayoría, el mismo día (703) o el anterior (286). Los días de antes son
  porque, hasta agosto, el bot no exportaba cada media hora y el ERP veía la baja con retraso. En NSYS una baja es, casi
  siempre, una corrección; las bajas de verdad van a cajas (desguace, no vendible).
- **Lo que hace hoy el ERP:**
  - **rastro:** «Estado en NSYS: Removed» en el lote viejo, y «Entra en el lote …» + «Vuelve a entrar (lote …)», en el
    color de las devoluciones, en el nuevo. No dice qué cambió;
  - **ficha:** empieza de cero con el lote nuevo: «En stock 0 días (3 entradas)», sin la revisión del lote de antes y, si
    el lote nuevo entró directo en «Pedidos realizados», sin última caja;
  - **tabla del stock:** el móvil sale con 0-1 días y se ordena como recién llegado.

## 2. Regla: la cadena de correcciones

- El lote **L1** de un IMEI **sigue en** el lote **L2** del mismo IMEI cuando:
  - L1 tiene un cambio de estado a `Removed` **con hora** (no del esqueleto), el día **D**;
  - L2 es otro lote del mismo IMEI, con fecha de recepción entre **D−7 y D+1** y no anterior a la de L1;
  - si hay varios L2 posibles, el de recepción más cercana a D (y, si empatan, el de lote mayor); un L2 sigue a un solo L1.
- Las correcciones se **encadenan** (BT-1307 → BT-1309 → BT-1345): la cadena es un solo móvil. Su **primer lote** es el de
  la entrada de verdad; su **lote actual**, el último.
- Un lote sin fecha de recepción no entra en una cadena como L2. Una baja anterior a dic. 2023 (sin hora) no se detecta.
- Fuera del margen (una baja y una compra semanas después) sigue siendo una reentrada: «Vuelve a entrar».
- Se calcula **al leer**, con los lotes (`Nsys_movil`) y los eventos (`Nsys_evento`) del IMEI. Vale para todo el
  histórico y para lo que llegue, sin migración ni limpieza.

## 3. Rastro

- Cada corrección sale como **un solo evento** `CORRECCION`, a la hora de la baja de L1:
  - `lote` = L2, `loteAnterior` = L1 (campo nuevo de `EventoRastro`, nulo en los demás tipos);
  - `de` / `a` = el móvil antes y después. Si los dos lotes tienen capacidad y color del maestro: «512GB Amarillo · A» →
    «512GB Negro · A100» (grado tal cual de NSYS). Si no: el `Goods` de NSYS y el grado («Apple iPhone 15 Pro eSIM 256GB ·
    n/a»).
- Dejan de salir los tres eventos que sustituye: el `ESTADO` → `Removed` de L1 y la `ENTRADA` y la `REENTRADA` de L2 (con
  hora o del esqueleto).
- **Web** (`rastro.ts`): «**Corrección en NSYS (lote BT-1333 → BT-1345)**», debajo «512GB Amarillo · A → 512GB Negro ·
  A100», en ámbar (como los cambios de grado).
- La revisión del rastro (§4) se calcula con la cadena: una corrección no vuelve a dar «Revisado» de entrada si el móvil ya
  se había revisado en el lote anterior.

## 4. Ficha del IMEI y tabla del stock

La cadena del lote que enseña la ficha (el más reciente) cuenta como un solo móvil:

- **Días en stock:** desde la recepción del **primer lote** de la cadena (en la ficha y en la tabla de «Stock de
  teléfonos», que también ordena por días).
- **«(N entradas)»:** las entradas de verdad, es decir, los lotes del IMEI menos los que siguen a una corrección.
- **Revisado y «Pendiente de revisar desde …»:** se calculan con los eventos de toda la cadena, como si fueran de un solo
  lote, y sin la baja ni el alta de la corrección: una corrección no borra la revisión ni reinicia la espera.
- **Grado anterior** («Grado A (antes C)»): con los cambios de grado de toda la cadena.
- **Última caja de un móvil de pedido:** si el lote actual entró directo en «Pedidos realizados» y no tiene última caja, la
  del lote anterior de la cadena (también en la tabla del stock y en «Quién lo tiene»).
- Lo demás (modelo, color, grado, SKU, estado, lote) es del lote actual, como hoy.
- El buscador de IMEI y «Pegar varios IMEIs» no cambian.

## 5. Código

- **Servidor:**
  - `Correcciones` (nueva, pura): de los lotes y los eventos de un IMEI saca las correcciones (L1, L2, hora) y la cadena de
    cada lote (primer lote y lote actual). Constantes del margen: 7 días antes y 1 después.
  - `Rastro.unir(lotes, eventos, trabajos)`: añade los `CORRECCION` y quita los eventos que sustituyen; la revisión, con
    los eventos de la cadena.
  - `StockTelefonosController.ficha`: días, entradas, revisado, pendiente, grado anterior y última caja con la cadena.
  - `StockTelefonosController.stock` (`GET /`): días y última caja de pedido con la cadena. Para no leer IMEI a IMEI,
    `NsysDAO` lee de una vez los lotes y las bajas con hora de los IMEIs con más de un lote.
  - `EventoRastro`: campo `loteAnterior` (nulo salvo en `CORRECCION`).
- **Web:** el caso `CORRECCION` en `verEvento` (`rastro.ts`); `api/openapi.json` y `schema.d.ts` regenerados por
  `loteAnterior`.
- **Coordinación con el SKU del taller (0.9.21):** toca `Rastro`, `EventoRastro` y la ficha; esta pieza entra antes en
  `main` y se le avisa con los commits (sesión del SKU del taller).

## 6. Pruebas

- **Servidor:**
  - `CorreccionesTest`: baja y alta el mismo día; alta 5 días antes y 1 después (sí); 8 días antes y 2 después (no);
    cadena de 3 lotes; dos lotes candidatos (el más cercano); un lote sin fecha de recepción; una baja del esqueleto (no);
    una reentrada semanas después (no).
  - `RastroTest`: la corrección sale como un `CORRECCION` con los dos lotes y los textos de antes y después (con maestro y
    sin él), sin la baja ni la entrada ni la reentrada; una reentrada de verdad sigue saliendo; la revisión no se repite
    tras la corrección.
  - `StockTelefonosControllerTest`: la ficha de un lote corregido (días desde el primer lote, 1 entrada, la revisión y el
    grado anterior de la cadena, la última caja del lote anterior en un móvil de pedido); el stock con los días y la caja de
    la cadena.
  - `NsysDAOTest`: la lectura nueva de lotes y bajas.
- **Web:** `rastro.test.ts`, el texto de `CORRECCION`.
- **Preprod:** E2E `stock-telefonos`; la ficha y el rastro de los dos IMEIs de ejemplo (`353177640742481`, de BT-1333 a
  BT-1345, y `355407368461545`, de BT-1307 a BT-1309 y a BT-1345) y la tabla del stock (sus días).

## 7. Entrega

- Ramas `dev_v0921_correccion_nsys` en el servidor (desde `main` `4c4cfe5`) y en la web (desde `main`).
- **Versión y documentos compartidos** (acordado con la sesión del SKU del taller, que entra después y añade lo suyo sin
  volver a subir la versión): esta pieza sube la web a **0.9.21** (`npm pkg set version=0.9.21` y las dos entradas raíz
  de `package-lock.json` a mano; nunca `npm version`), abre `## [0.9.21] - 2026-10-XX` en el CHANGELOG de la web y crea
  `docs/novedades/NOVEDADES-v0.9.21.md` en la raíz. La fecha y el tag `v0.9.21`, tras producción y con el OK del usuario.
- Merge `--no-ff` a `main` de los dos repos y push, con el OK del usuario; aviso a la sesión del SKU del taller con los
  commits.
- Preprod con `main` (sin datos que limpiar); producción con el resto de la 0.9.21.
