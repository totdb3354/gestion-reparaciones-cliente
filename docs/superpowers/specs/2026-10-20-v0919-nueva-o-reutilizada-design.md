# 0.9.19 — Formulario de reparación: «Nueva» o «Reutilizada» en la celda del SKU

Fecha: 2026-10-20. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va en la **0.9.19**, que se sube junta (decisión del usuario del 2026-10-20) con «Completar pulidos» (ya en `main` de la web),
el panel de carga (su spec dice 0.9.20: se renumera al cerrar) y el «SKU del taller» (otra sesión; lleva servidor y base).
**Esta pieza es solo web**: sin cambios en el servidor ni en la base; funciona con el servidor 0.9.18.
Prototipo aprobado: `assets/2026-10-20-v0919-prototipo-nueva-o-reutilizada.html` (versión 5; la pestaña «Hoy» es la referencia).

## 1. Qué cambia

**Hoy** cada fila de pieza tiene un contador «+/−» (pieza **nueva**: descuenta del stock) y, a la derecha, una casilla
«Reutilizado» (no descuenta). Por costumbre se pulsa «+» también con piezas reutilizadas: nada avisa y el stock baja de más.
Para corregirlo hay que bajar a 0 y marcar la casilla. Además, el SKU que viene elegido se escoge mirando el stock, y la
banda «Sin stock… Solicitar pieza» sale con cualquier SKU sin stock, aunque la pieza esté marcada como reutilizada o no se haya
tocado la fila.

**Ahora** cada pieza empieza por decir si es **nueva o reutilizada**, en la **celda del SKU**, el mismo sitio y el mismo gesto
que ya funciona con «— Elige color —» del chasis: hasta elegir, la fila no deja seguir.

**Por qué ahora:** es el paso previo del «SKU del taller» (va en la misma subida). Con él, una batería nueva pondrá «batería nueva» (A → A100)
en el móvil y una reutilizada no; si «reutilizada» no se marca bien, el ERP vendería como batería nueva una que no lo es.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| Dónde se elige | En la **celda del SKU**, encima del desplegable: dos botones, «+ Nueva» y «↺ Reutilizada». No es una pregunta, es la elección. |
| Después de elegir | En esa celda aparece el desplegable. Si la pieza solo tiene un SKU, se queda ese. |
| Nueva | Se enciende el contador de la izquierda, **en 1**. |
| Reutilizada | Sin contador. La columna de la derecha lo marca. |
| Volver atrás | La **«×»** de la marca de la derecha, o «−» hasta 0 en una nueva: la fila vuelve al principio y **conserva el SKU**. |
| Pieza sin stock | Si **ningún** SKU de la pieza tiene stock, el botón de la izquierda ya pone **«Solicitar pieza»** y la pide directamente. Si unos sí y otros no, «+ Nueva» y, al elegir uno sin stock, la banda de pedirla, como hoy. |
| Aviso al terminar | **No, por ahora** (el resumen «Vas a gastar del stock real» del prototipo). Si el stock sigue descuadrando, se añade. |

Opciones descartadas en el prototipo: A (dos botones después del desplegable), B (solo el aviso al terminar), C (marca en la fila
y «Era reutilizada») y D (una columna aparte con la pregunta).

## 2. Comportamiento

Aplica al formulario en sus tres modos: **nuevo**, **glass** y **editar**.

**Columnas** (cabecera de `FormularioReparacion`): las de hoy, con «¿Reutilizado?» renombrada a **«Pieza»**. El ancho total de
las columnas no cambia (870 px): «Pieza» crece lo que necesite la marca más larga («↺ Reutilizada ×») y lo pierde
«Observación».

**Estados de una fila** (sin solicitud guardada ni «Ya reparado»; esos, más abajo):

| Estado | Celda del SKU | Contador | Stock | «Pieza» | Banda de agotado |
|---|---|---|---|---|---|
| Sin elegir | «+ Nueva» y «↺ Reutilizada» (o «Solicitar pieza» y «↺ Reutilizada», ver abajo) | 0, apagado | el del SKU que viene elegido, o «—» | vacía | nunca |
| Nueva | desplegable, todos los SKU, los sin stock con «(sin stock)» | 1 al elegir (si el SKU tiene stock), «+/−» encendidos | «12 → 11» en rojo | «+ Nueva ×» | con SKU sin stock: «Solicitar pieza»; con todo el stock gastado: «Solicitar y descontar stock» |
| Reutilizada | desplegable, todos los SKU | «—», apagado | el del SKU, sin flecha | «↺ Reutilizada ×» | nunca |
| Solicitada (local) | desplegable con el SKU sin stock | 0, apagado | 0 | «Solicitada ×» | verde: «✓ Solicitud de reposición pendiente» |

**Elegir:**
- **«+ Nueva»**: la fila pasa a Nueva. Si el SKU elegido tiene stock, el contador queda en 1; si no, en 0 y sale la banda
  «⚠ Sin stock disponible. Solicita la pieza…» con «Solicitar pieza» (lo de hoy). En chasis y tapa sin color, el contador
  sigue en 0 hasta elegirlo.
- **«↺ Reutilizada»**: la fila pasa a Reutilizada (`reutilizado = true`, cantidad 0).
- **«Solicitar pieza»** (en lugar de «+ Nueva») solo sale cuando **ningún** SKU de la pieza (para el modelo) tiene stock. Con el
  SKU ya puesto (piezas sin color), abre el diálogo de solicitar de hoy (`DialogoSolicitarPieza`, variante sin stock); al
  confirmar, la fila queda Solicitada; al cancelar, no cambia nada. En chasis y tapa sin ningún color con stock, hace lo mismo
  que «+ Nueva»: sale el desplegable y, al elegir el color, la banda de pedirla.

**El SKU** (igual que hoy, salvo que en una fila sin elegir no se ve el desplegable):
- El que viene elegido: chasis y tapa, ninguno («— Elige color —» al elegir); el resto, el primero con stock o, si ninguno,
  el primero.
- Con Nueva, cambiar a un SKU con stock deja el contador en 1 (o el que tuviera, sin pasar del stock); a uno sin stock, en 0
  con la banda de pedirla. Con Reutilizada, se elige cualquiera sin tocar nada más.
- **Enlace de color** (0.9.7) igual que hoy: elegir el chasis pone la tapa del mismo color; elegir la tapa pone el chasis si ya
  estaba elegido. La otra fila **no** se da por elegida: conserva su estado y, si estaba sin elegir, solo se le prepara el
  color. Si estaba en Nueva, su contador se ajusta como arriba.

**Volver atrás:**
- La **«×»** de «Pieza» deja la fila **sin elegir**: cantidad 0, `reutilizado = false` y sin solicitud local. **Conserva el
  SKU** (el color elegido no se pierde).
- En una Nueva, **«−» hasta 0** hace lo mismo.

**Guardar:**
- «✓ Guardar fila» y «Terminar asignación», como hoy. Una fila cuenta como activa igual que hoy (cantidad > 0, reutilizada o
  con solicitud local).
- **Nuevo:** si al pulsar «Terminar asignación» alguna fila tiene la elección hecha pero **no el SKU** (chasis o tapa sin
  color), no se pasa a «✓ Confirmar terminar»: sale el aviso de error de la web con «Falta elegir el SKU de: Chasis.» (los
  nombres de las filas, separados por comas).

**Lo que ya existe hoy:**
- **Editar una reparación** (`modo === 'editar'`): la fila editada arranca con su elección hecha según lo guardado (cantidad
  > 0 → Nueva; reutilizada → Reutilizada) y con su SKU. Con la «×» se puede cambiar de una a otra: el servidor ya devuelve al
  stock la pieza vieja si era nueva y descuenta la nueva si lo es (`ReparacionDAO.editarReparacion`). La previsión de stock de
  la fila editada («5 → 4», `previsionStock`) sigue como hoy. Si se deja **sin elegir**, no se puede guardar: es el caso de hoy
  «a 0 sin Reutilizado» (`filaEditadaInvalida`, contador en rojo). Las filas nuevas que se añaden en la edición funcionan como
  en el flujo nuevo.
- **Solicitud ya guardada en el servidor:**
  - **Pendiente:** SKU fijo (desplegable deshabilitado, para que se vea qué se pidió), contador apagado y la banda verde de hoy
    («✓ Solicitud de reposición pendiente»), que ya dice que está pedida. En «Pieza», el botón **«↺ Reutilizada»** (sustituye a
    la casilla, que hoy se puede marcar en ese caso): elegido, la marca pasa a «↺ Reutilizada ×», y su «×» lo deshace. (No
    lleva marca «Solicitada»: con el botón al lado no cabe en la columna y repetiría la banda.)
  - **En camino:** igual, con «Pieza» vacía y el botón derecho «⚠ En camino» de hoy.
  - **Recibida** (`recibidoPendienteUso`): fila sin elegir con el SKU de la solicitud y el «✓ Recibido» de hoy a la derecha.
    Se usa eligiendo «+ Nueva».
- **«Ya reparado»** y **filas sin SKU para el modelo**: igual que hoy (bloqueadas; en una fila sin SKU, los dos botones se
  pintan deshabilitados).
- **Borrador:** se sigue guardando cantidad, «reutilizado» y solicitud local; la elección se **deduce** al recuperarlo
  (reutilizado → Reutilizada; cantidad > 0 o solicitud local → Nueva; si no, sin elegir). Los borradores de antes valen tal
  cual. Una fila elegida sin SKU no llega al borrador (no es activa) y vuelve sin elegir.
- **Glass:** las filas de glass y marco funcionan igual.

## 3. Errores y casos límite

- **Pieza con un solo SKU sin stock** (la cámara del prototipo): «Solicitar pieza» y «↺ Reutilizada».
- **Pieza con todos los SKU sin stock, con color:** «+ Nueva» lleva al desplegable y la banda sale al elegir el color.
- **Elegida y sin SKU:** no deja terminar (§2, Guardar). «✓ Guardar fila» no aparece (no es activa), como hoy con el chasis sin
  color.
- **Cambiar de modelo** (`CAMBIAR_MODELO`): las filas que hoy se reinician vuelven **sin elegir**.
- **El stock cambia mientras el formulario está abierto:** el botón «Solicitar pieza» / «+ Nueva» se decide con el stock que
  cargó el formulario (como hoy el «+»). El servidor sigue siendo quien valida al guardar.
- **Accesibilidad:** los botones llevan nombre con la pieza («Nueva Batería», «Reutilizada Batería», «Solicitar Cámara»,
  «Volver atrás Chasis»), en lugar de «Sumar…» y la casilla «Reutilizado…». Los «Sumar / Restar» del contador se quedan.

## 4. Construcción

**Piezas** (nombres orientativos; el plan los fija), en `modules/taller/formulario/`:
- `estado.ts`:
  - `FilaEstado` gana `eleccion: 'nueva' | 'reutilizada' | null`.
  - Acciones nuevas `ELEGIR { prefijo, eleccion }` y `VOLVER { prefijo }`. Desaparece `MARCAR_REUTILIZADO` (solo la usa la
    casilla).
  - `SUMAR` / `RESTAR` solo con `eleccion === 'nueva'`; `RESTAR` hasta 0 deja la fila sin elegir.
  - `CAMBIAR_SKU` con Nueva ajusta la cantidad (1 si hay stock, sin pasarse; 0 si no). El enlace de color, igual.
  - `CONFIRMAR_AGOTADO` (también desde «Solicitar pieza» directo) deja `eleccion = 'nueva'`.
  - `controles` se deducen de la elección. `subFila`: las bandas locales (sin stock, límite) solo con Nueva; la de solicitud
    guardada, como hoy.
  - Nuevo selector «filas elegidas sin SKU» para el aviso de «Terminar».
  - Edición (`filaEditada…`) y solicitudes guardadas (`aplicarSolicitud`): la elección inicial según §2.
- `FilaComponente.tsx`: la celda del SKU pinta los dos botones o el desplegable; la columna «Pieza», la marca con su «×» o el
  «↺ Reutilizada» de las filas con solicitud pendiente. El contador pinta «—» en una reutilizada.
- `FormularioReparacion.tsx`: la cabecera («Pieza») y los anchos.
- `SubFilaAgotado.tsx`: sin cambios de aspecto; deja de salir cuando `subFila` lo dice.
- `borrador.ts`: deducir la elección al aplicar un borrador.
- `useGuardado.ts`: el aviso «Falta elegir el SKU de: …» antes de `PEDIR_CONFIRMACION_GUARDAR`.
- Sin cambios en el contrato (`FilaReparacion` sigue con `cantidad` y `reutilizado`) ni en el servidor.

## 5. Pruebas

**Unitarias (Vitest + Testing Library):**
- Reductor: elegir Nueva (con y sin stock) y Reutilizada; contador en 1; «−» hasta 0 y «×» vuelven sin elegir conservando el
  SKU; cambiar de SKU en Nueva (con stock, sin stock, sin pasarse); enlace de color sin dar por elegida la otra fila;
  «Solicitar pieza» directo (confirmar y cancelar); cambiar de modelo; filas elegidas sin SKU.
- Selectores: `subFila` (sin elegir y Reutilizada nunca; Nueva sin stock y en el límite; solicitud guardada como hoy),
  `filaActiva`, `filaEditadaInvalida`, `previsionStock` al cambiar de Nueva a Reutilizada en edición.
- Edición: arranca elegida según lo guardado; cambiar de tipo con la «×»; sin elegir no deja guardar.
- Solicitudes guardadas: pendiente («↺ Reutilizada» y vuelta con la «×»), en camino, recibida.
- Borrador: elección deducida; borradores de antes.
- `FilaComponente`: los cuatro estados de la tabla de §2 y los nombres accesibles.
- `useGuardado`: el aviso de SKU pendiente no pasa a confirmar.
- Se ajustan los tests que pulsan «Sumar…» o la casilla «Reutilizado…» (`estado*.test.ts`, `FilaComponente.test.tsx`,
  `FormularioReparacion.test.tsx`, `SubFilaAgotado.test.tsx`, `useGuardado.test.tsx`, `borrador.test.ts`, `api.test.tsx` y
  los de otras pantallas que abren el formulario).

**E2E en preprod** (Chromium, datos sintéticos creados por API y borrados al terminar; nunca en producción):
- `tests/e2e/formulario.spec.ts` se adapta («Sumar» pasa por «Nueva»).
- Nueva: batería nueva → el stock baja 1 al terminar.
- Reutilizada: batería reutilizada → el stock no cambia; la reparación queda como reutilizada.
- Solicitar: pieza sin stock → «Solicitar pieza» → al terminar queda la solicitud.
- Editar: una reparación con pieza nueva pasa a reutilizada → el stock sube 1.

## 6. Entrega

- Rama `dev_v0919_nueva_reutilizada` en un **worktree** de la web (la copia principal de la web la usan otras sesiones).
- Versión **0.9.19** de la web (el `package.json` ya la tiene): una sección más en `CHANGELOG.md` (esta pieza no necesita
  servidor nuevo ni migración; lo que pida la subida entera lo fija el SKU del taller) y en
  `docs/novedades/NOVEDADES-v0.9.19.md` de la raíz.
- `docs/paridad/formulario.md` de la web: la casilla «Reutilizado» pasa a ser la elección en la celda del SKU, con las
  diferencias deliberadas respecto al JavaFX (elección obligatoria, banda de agotado solo con Nueva, «Solicitar pieza» directo,
  cambiar de tipo en la edición con la «×»).
- **Avisar al taller antes de subirla**: cambia la costumbre de pulsar «+». Un apartado corto en las novedades, sin capturas
  (el repo es público: las del programa real no van en `docs/`); el aviso en el taller lo da el usuario con la pantalla.
- Se despliega con el resto de la 0.9.19. Por sí sola sería de solo web y en horario; la subida entera lleva la migración del
  SKU del taller y se decide con esa pieza.
- Merge `--no-ff`, push y tag **solo cuando lo diga el usuario**.

## 7. Fuera de esta versión

- El aviso al terminar («Vas a gastar del stock real»): descartado por ahora (§1).
- **SKU del taller** (misma subida, otra sesión y otra spec): que el ERP cambie color, grado y batería del móvil tras la
  reparación y la edición a mano del SKU (color, grado y batería nueva). Decisiones en `Apuntes/plan-futuro.md` §5. Lo
  automático de la batería lee la elección de esta pieza (Nueva → batería nueva).
- La descripción completa del móvil donde pone «Modelo» (idea del 2026-10-20, en el plan).
