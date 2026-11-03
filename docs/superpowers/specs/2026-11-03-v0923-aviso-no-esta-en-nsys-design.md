# 0.9.23 — El modal de asignación avisa si el IMEI no está en NSYS

Fecha: 2026-11-03. Estado: **diseño aprobado** en el brainstorming del 03/11, pendiente del plan.
Prototipo aprobado: `assets/2026-11-03-v0923-prototipo-aviso-no-nsys.html` (variante **A**, en rojo).
**Solo web**: sin cambios en el servidor ni en la base; funciona con el servidor 0.9.22 que está en producción.
Se apoya en la 0.9.22 (tarjeta «No está en NSYS», spec `2026-10-25-v0922-plantillas-nsys-design.md` §3.4) y en la 0.9.18
(«Comprobar una lista de IMEIs», `POST /api/stock-telefonos/comprobar`).

## 1. Qué cambia

**Hoy**, al picar un IMEI en «Asignar trabajos», el modal pide el modelo a `GET /api/telefonos/{imei}/modelo`, que mira primero
el modelo que ya tiene el ERP y solo si no lo tiene lo busca en el espejo de NSYS. Un móvil que no está en NSYS no se nota en
ese momento: aparece después en la tarjeta «No está en NSYS», cuando ya nadie tiene el móvil en la mano.

**Ahora**, al picar (o pegar) un IMEI, el modal pregunta a NSYS y, si no lo conoce, lo dice en la fila y en el detalle. Así se
puede dar de alta en NSYS en el momento, con el móvil delante y sus características a la vista.

**Decisiones del usuario (03/11):**

| Tema | Decisión |
|---|---|
| Qué avisa | Solo si **NSYS no conoce el IMEI** en ningún lote, la misma regla que la tarjeta «No está en NSYS». Un vendido o de baja no avisa: NSYS lo conoce. |
| Cómo se ve | Variante **A**: texto «no está en NSYS» en la fila de la cola y una línea en el detalle con la hora del último Items. |
| Color | **Rojo**, el de los mensajes de error del modal (`--color-texto-error`, #B03040). |
| Carácter | Indicación **sutil y meramente informativa**: no bloquea nada ni cambia lo que se guarda. |

Descartadas: **B** (solo la línea del detalle: no se ve al picar seguidos) y **C** (solo el mensaje bajo el campo: se borra al
picar el siguiente).

## 2. Comportamiento

### 2.1 Cuándo se pregunta
- **Al picar** un IMEI en Reparación, Glass o Pulido: se pregunta por ese IMEI en cuanto entra en la cola, sin esperar al
  modelo. Un repetido (el aviso «Ese IMEI ya está en la cola») no pregunta.
- **Al pegar varios**: una sola pregunta con todos los IMEIs añadidos, sin tener que abrirlos uno a uno. Si fueran más de 500
  (`MAX_IMEIS`), se pregunta en tandas de 500.
- **Una vez por IMEI** mientras el modal está abierto. La respuesta se guarda por IMEI y vale para todas sus entradas: el mismo
  IMEI en Reparación y en Glass, la glass creada con «Lleva glass» o un IMEI quitado y vuelto a picar no vuelven a preguntar.

### 2.2 Qué se enseña

Solo cuando la respuesta dice que NSYS **no** lo tiene (`encontrado: false`):

- **Fila de Reparación y Glass** (`FilaCola`): una segunda línea, debajo del IMEI, con «no está en NSYS» en rojo, 10,5 px y
  seminegrita. Va en la segunda línea porque la cola mide 300 px y, junto a «⚠ falta modelo», no cabe en la primera. En una
  verde se suma a los técnicos: «Marta · no está en NSYS».
- **Fila de Pulido**: detrás del técnico y el cliente: «(sin técnico) · — · no está en NSYS».
- **Detalle** (`DetalleEntrada` y el detalle de Pulido): una línea bajo «IMEI en curso», en el mismo rojo y a 11 px:
  «No está en NSYS · último Items: hoy 13:40» (o «del 02/11 22:10» si no es de hoy). Sin hora conocida, solo «No está en
  NSYS».
- **Al pasar el ratón** por la fila o la línea: «NSYS no tiene este IMEI en ningún lote del último Items (hoy 13:40). Al
  guardar saldrá en «No está en NSYS».».

Mientras llega la respuesta, o si NSYS lo tiene, no se enseña nada. No hay «Comprobando…»: la pastilla «Buscando…» del modelo
ya dice que el modal está trabajando.

### 2.3 Qué no hace
- **No bloquea**: «Asignar →» y «Guardar (N)» funcionan igual. El lote que se manda no cambia.
- **Si la consulta falla**, no se enseña nada: ni marca ni error, y el modal no se para. No se vuelve a preguntar por ese IMEI
  mientras el modal siga abierto.
- **No da de alta en NSYS** ni marca nada en el ERP. Al guardar, el móvil sale en la tarjeta «No está en NSYS» como hasta
  ahora, y de allí a la plantilla de altas.

## 3. Datos

- **`POST /api/stock-telefonos/comprobar`** (ya en producción): una entrada por IMEI con `encontrado`. Busca en todo el espejo,
  en cualquier lote y estado, cruzando por `claveCruce`: la misma regla que la tarjeta. Admite hasta 500 IMEIs por llamada.
  Lo demás que devuelve (modelo, color, grado…) no se usa aquí.
- **`GET /api/stock-telefonos/estado`** (ya en producción): `ultimaExportacion`, la hora del último Items. Se lee con
  `useEstadoNsys`, que ya tiene la caché del indicador de la barra superior. No hace falta pedirlo aparte.
- **Permisos**: los dos piden SUPERTECNICO o ADMIN, igual que la página de Asignaciones (`RequiereSupertecnicoOAdmin`).

## 4. Código (web)

En `src/modules/taller/asignaciones/modal/`:

- **`estado/tipos.ts`**:
  - `EstadoModal.enNsys: Record<string, boolean | null>`, por IMEI: `true` lo tiene, `false` no lo tiene, `null` preguntado
    sin respuesta (en camino o falló). Un IMEI sin clave no se ha preguntado.
  - Efecto nuevo `{ tipo: 'comprobarNsys'; imeis: string[] }`.
  - Acción nueva `{ tipo: 'NSYS_COMPROBADO'; resultado: Record<string, boolean> }`.
  - `estadoInicial` con `enNsys: {}`.
- **Reductor** (`colas.ts`, `pulido.ts`, `reductor.ts`):
  - `escanear`, `pegar`, `PULIDO_ESCANEAR` y `PULIDO_PEGAR` añaden el efecto con los IMEIs añadidos que aún no son clave
    de `enNsys`, y los apuntan con `null`. Así una respuesta en camino no se pide dos veces.
  - `NSYS_COMPROBADO` fusiona el resultado en `enNsys`.
  - Una función de `derivados.ts`, `noEstaEnNsys(s, imei)`, es `enNsys[imei] === false`.
- **`api.ts`**: `comprobarEnNsys(imeis)`. Llama a `/comprobar` en tandas de `MAX_IMEIS` y devuelve `{ imei: encontrado }`.
  Ante un error, lanza.
- **`useEfectosModal.ts`**: el caso `comprobarNsys` despacha `NSYS_COMPROBADO` con el resultado. Si falla, no despacha nada
  (los IMEIs quedan preguntados y sin respuesta).
- **`FilaCola.tsx`**, **`PanelPulido.tsx`** y **`DetalleEntrada.tsx`**: la marca y la línea de §2.2. `AsignarTrabajosDialog`
  lee `useEstadoNsys()` y pasa `ultimaExportacion` a los paneles.
- **Hora del Items**: el texto «hoy 13:40» o «del 02/11 22:10» sale de `src/modules/telefonos/stock/nsys.ts`. Se exporta una
  función pequeña con los `hora`, `diaMes` y `esHoy` que ya usa la píldora.
- **Token de color**: se usa `text-texto-error`. No hace falta uno nuevo.

## 5. Casos límite

- **Items con retraso**: si alguien acaba de dar de alta el móvil en NSYS y el bot aún no ha exportado, el aviso sale igual. La
  hora del último Items deja ver que puede ir con retraso.
- **NSYS lo tiene con el IMEI2 o el número de serie**: avisa, igual que la tarjeta (spec 0.9.22 §5).
- **Vendido o de baja en NSYS**: no avisa (decisión del usuario).
- **Clientes cargando**: la cola de efectos del modal espera a la lista de clientes (D6). La pregunta a NSYS espera con el
  resto, normalmente unos milisegundos al abrir.
- **Rendimiento**: `/comprobar` ya responde listas de 500 en «Comprobar una lista de IMEIs». Para uno o unas decenas no hay
  nada que medir.

## 6. Pruebas

- **Reductor** (`colas.test.ts`, `pulido.test.ts`):
  - picar pide `comprobarNsys` con ese IMEI;
  - un repetido no pide nada;
  - pegar pide una vez con todos los añadidos;
  - un IMEI ya preguntado (mismo IMEI en Glass, glass creada con «Lleva glass», quitar y volver a picar) no vuelve a pedir;
  - `NSYS_COMPROBADO` fusiona el resultado;
  - `noEstaEnNsys` solo es verdad con `false`.
- **`api.test.tsx`**: `comprobarEnNsys` parte en tandas de 500 y traduce `encontrado`.
- **`useEfectosModal.test.tsx`**: el efecto despacha el resultado; si falla, no despacha nada.
- **Pintado**:
  - `FilaCola`, roja y verde: con técnicos lleva «Marta · no está en NSYS»;
  - `PanelPulido` y `DetalleEntrada`: la línea con la hora, la línea sin hora y nada si NSYS lo tiene o no hay respuesta;
  - `AsignarTrabajosDialog.test.tsx`: un flujo completo con el servidor simulado.
- **E2E en preprod**: un IMEI inventado avisa; uno real del espejo de preprod no. Va en la E2E de asignaciones que ya
  existe (`e2e/asignar.spec.ts`), con los datos sintéticos por API y su limpieza.

## 7. Versión y entrega

- Rama `dev_v0923_aviso_nsys` de la web desde `main` (`206fa5b`, 0.9.22.1), en un worktree (`wt/web-0923-aviso`): el clon
  principal y los otros worktrees pueden ser de otras sesiones.
- `package.json` a `0.9.23`. CHANGELOG: entrada `[0.9.23]`.
- Preprod (E2E) → producción **en horario**: solo web, sin migración, con un corte despreciable → merge `--no-ff` a `main` de
  la web → gitlink en la raíz.
- Merge, push, tag y despliegues, solo con el OK del usuario.
