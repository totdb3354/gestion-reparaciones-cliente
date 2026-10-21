# 0.9.21 — SKU del taller (pieza 2: lo automático desde las reparaciones)

Fecha: 2026-10-21. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Va en la **0.9.21**, después de la pieza 1 (`2026-10-21-v0921-sku-del-taller-base-design.md`), en la que se apoya: la tabla
`Taller_cambio`, «lo que vale», el SKU del maestro, el cierre automático, el rastro y la lista «Por pasar a NSYS».
**Servidor + web** (sin migración propia: la columna `ID_CAMBIO_ANTERIOR` va ya en la de la pieza 1).
Decisiones previas: `Apuntes/plan-futuro.md` §5 («SKU del taller») y la conversación del 2026-10-21.

## 1. Qué cambia

**Hoy** (con la pieza 1) lo del taller solo entra a mano. **Ahora**, al guardar una reparación, el ERP cambia solo el color, el
grado y la batería nueva del móvil según las piezas usadas, avisa antes en el formulario y lo deja en el rastro y en la lista
«Por pasar a NSYS». Casi no hay pantalla nueva: una banda en el formulario.

**Decisiones del usuario:**

| Tema | Decisión |
|---|---|
| A qué móviles | A **todo IMEI que esté en NSYS**, en el estado que sea (si está en el taller es nuestro o estaba mal registrado). Un IMEI que no está en NSYS no cambia nada. |
| Chasis de otro color | **Nuevo o reutilizado** → color del chasis y grado A. |
| Tapa trasera de otro color | Igual que el chasis: color y grado A (siempre van juntos). |
| Batería | **Nueva** → «100». **Reutilizada** → sin «100» (se **quita** si lo tenía). |
| Cómo se compone el grado | Cada pieza toca su parte: chasis/tapa → la letra pasa a A (se conservan «Ms» y «100»); batería → el «100». «Ms» nunca cambia solo. Ej.: MsB + chasis nuevo + batería nueva → MsA100. |
| Editar o borrar la reparación | Si su cambio sigue **pendiente** (vigente, no pasado a NSYS) se **deshace** solo; si ya estaba en NSYS, solo aviso en el rastro. |
| Aviso | Una banda **encima de «Terminar asignación» / «Guardar cambios»**. |
| Color del chasis → color del móvil | **Deducido del maestro**; si no encaja con uno solo, el color no cambia, el aviso lo dice y se corrige a mano. |

## 2. Comportamiento

### 2.1 Cuándo y qué
- Se aplica al **guardar** una reparación: «Terminar asignación» (`POST /api/reparaciones/completa`), «✓ Guardar fila»
  (`POST /api/reparaciones/{idAsignacion}/filas`) y «Guardar cambios» en edición (`PUT /api/reparaciones/{idRep}` y las filas
  nuevas de la edición, que van por `completa`). Glass y marco no cambian nada.
- Cada fila guardada es su propia reparación `R…`; **cada `R…` que cambia algo crea sus propios cambios** (`ORIGEN = REPARACION`,
  `ID_REP`), uno por dato (`COLOR`, `GRADO`). Dentro de un guardado se procesan en el orden de las filas.
- Piezas que cuentan (por el prefijo del SKU de la fila): **chasis** (`cha…`), **tapa trasera** (`tapa…`) y **batería** (`bat…`).
  Una fila sin SKU, una solicitud de pieza o una acción «otro» no cuentan.
- Partiendo del valor que vale **en ese momento** (lo del taller vigente o NSYS):
  - **chasis o tapa** (nueva o reutilizada): color → el color deducido (§2.2), si es distinto del que vale; grado → la letra pasa a
    `A` conservando `Ms` y `100`;
  - **batería nueva:** grado → se añade `100` (conservando `Ms` y la letra);
  - **batería reutilizada:** grado → se quita `100` si lo tenía.
- Si chasis y tapa del mismo guardado deducen colores distintos, manda el del **chasis**.
- Igual que la corrección a mano (pieza 1): si el valor nuevo es el de NSYS, se quita lo del taller de ese dato; si es igual al
  que ya vale, no se crea nada; si no, queda vigente y sustituye al anterior (`ID_CAMBIO_ANTERIOR`).
- El grado tiene la forma `[Ms]<letra>[100]` (letra A, A-, B o C). Si el que vale **no** la tiene (ID = bloqueo de operador, F/FD
  = no vendible, n/a…), **el grado no cambia** con ninguna pieza (ponerle A sería un error) y el aviso dice «el grado (ID) no se
  puede calcular: lo corregirá un supertécnico». El color sí puede cambiar.
- Todo va **en la misma transacción** que la reparación: si el guardado falla, no queda ningún cambio.

### 2.2 Color del chasis → color del móvil (deducido del maestro)
- Del SKU de la pieza se saca su color con la misma lectura que la web (`leerSku`: lo que queda entre el modelo y un `esim`
  final; `chai16ultramarineesim` → `ultramarine`).
- Candidatos: los colores del maestro (`Nsys_producto.COLOR`) para el modelo, la capacidad y la eSIM del móvil. De cada uno, su
  **nombre en inglés**: el color que lleva el Goods de los móviles de NSYS de ese modelo, capacidad, eSIM y color (el Goods sin
  «Apple iPhone <modelo> <capacidad>», normalizado a minúsculas sin espacios ni signos: «(PRODUCT)RED» → `productred`,
  «Sierra Blue» → `sierrablue`).
- Encaja un candidato si su nombre normalizado es **igual** al color del chasis, o si su **color base** es igual (tabla fija de
  oficial → base en el servidor, la de los SKU de chasis: `productred` → `red`, `sierrablue` / `pacificblue` → `blue`,
  `alpinegreen` → `green`, `deeppurple` → `purple`, `spaceblack` → `black`… más los alias de `Color_equivalencia`).
- Siempre por palabra entera, **nunca por trozos** («productred» contiene «pro»).
- Si encaja **exactamente uno** → ese color. Si ninguno o más de uno → **el color no cambia** (el grado sí, por la regla de §2.1),
  el aviso lo dice y el rastro lo apunta («el color del chasis chai13xxx no se puede deducir»).

### 2.3 Editar y borrar
- **Editar** una reparación (`PUT /api/reparaciones/{idRep}`: otro SKU, o nueva ↔ reutilizada): se deshace su cambio (§ abajo) y se
  vuelve a aplicar con la pieza nueva.
- **Borrar** (`DELETE /api/reparaciones/{idRep}`, y cualquier otro camino del servidor que borre una `R…` con pieza; el plan los
  localiza todos): se deshace su cambio.
- **Deshacer** el cambio de una `R…`, para cada dato:
  - si es el **vigente** → pasa a `QUITADO`; si sustituyó a otro (`ID_CAMBIO_ANTERIOR`) que sigue `SUSTITUIDO`, ese vuelve a
    `VIGENTE`; si no, vale lo de NSYS;
  - si **ya no es el vigente** (lo sustituyó otro posterior, o ya se pasó a NSYS, o mandó NSYS) → no se toca nada y el rastro avisa
    «se borró/editó la reparación R… que cambió el <dato>; corrígelo a mano si hace falta».

### 2.4 El aviso en el formulario
- Banda fina **encima del botón de la zona de guardar** («Terminar asignación» / «Guardar cambios»), visible en cuanto alguna fila
  elegida cambia el móvil:
  - «Al guardar, este móvil pasará a: Rojo · A100 (ahora: Azul · B)» (solo los datos que cambian);
  - si el color no se puede deducir: «… el color del chasis no se puede deducir: lo corregirá un supertécnico»;
  - si el grado no se puede calcular: lo mismo para el grado.
- No sale si el IMEI no está en NSYS ni si ninguna fila cambia nada. No añade clics ni bloquea.
- La cuenta la hace el **servidor** con la **misma función** que aplica al guardar: `POST /api/reparaciones/sku-taller/previsualizar`
  `{ imei, filas: [{ idCom, reutilizada }] }` → `{ antes: {color, grado}, despues: {color, grado}, colorSinDeducir, gradoSinCalcular }`
  (o vacío si no cambia nada). Abierta a **técnico, supertécnico y admin** (es el formulario del técnico; no devuelve precios ni
  datos de stock). La web la pide al cambiar las filas (con un pequeño retraso, como el autoguardado) y no la pide en un IMEI que
  no está en NSYS.
- En «✓ Guardar fila» no hay banda propia: la de abajo ya refleja todas las filas; esa fila aplica su parte al guardarse.

### 2.5 Rastro y lista
- Rastro: «Taller: color Azul → Rojo (reparación R20261021_3 · Técnico 2 · chasis chai13red nuevo)», «Taller: grado B → A100
  (reparación R… · batería bati13 nueva)», y los avisos de §2.2 y §2.3.
- Lista «Por pasar a NSYS» (pieza 1): en «Por qué», «Reparación R… · <técnico> · <pieza>».

## 3. Errores y casos límite
- **IMEI que no está en NSYS** (cliente externo): nada; sin banda.
- **IMEI en varios lotes:** vale la fila más reciente (como en la pieza 1).
- **Sin modelo en el maestro:** no hay candidatos → el color no cambia (aviso); el grado sí.
- **Previsualización que falla** (red): la banda no sale; el guardado aplica igual (la regla está en el servidor).
- **Dos reparaciones del mismo móvil a la vez:** se aplican en el orden en que se guardan; cada una parte del valor que vale en ese
  momento.

## 4. Construcción (orientativa; el plan la fija)
- **Servidor:**
  - regla pura «efecto de una pieza» (grado `[Ms]<letra>[100]` + pieza + nueva/reutilizada → grado nuevo) con tabla de casos;
  - traductor de color de chasis (lectura del SKU, candidatos, tabla oficial → base + `Color_equivalencia`, coincidencia exacta);
  - servicio «aplicar / deshacer los cambios de una `R…`», llamado desde `ReparacionDAO`/`ReparacionController` en `completa`,
    `filas`, `editarReparacion` y `eliminar` (dentro de su transacción), que escribe en `Taller_cambio` con los métodos de la pieza 1;
  - ruta `POST /api/reparaciones/sku-taller/previsualizar`.
- **Web** (`modules/taller/formulario`): consulta de previsualización (TanStack Query, `meta.silenciarError`, sin capa de carga) y la
  banda en `ZonaGuardar`.

## 5. Pruebas
- **Servidor (JUnit):** efecto de cada pieza (tabla: letras, Ms, 100, grados sin letra); traductor (igual, base única, ambigua,
  ninguna, sin modelo, «productred» no es «pro», alias de `Color_equivalencia`); aplicar en `completa` y `filas` (orden de filas,
  chasis y tapa de colores distintos, valor igual a NSYS quita, igual al que vale no crea); editar y borrar (deshace vigente con y
  sin anterior; no vigente → solo rastro); previsualizar (= lo que aplica; técnico 200); transacción (si el guardado falla, nada en
  `Taller_cambio`).
- **Web (Vitest):** la banda (textos, sin cambios no sale, IMEI fuera de NSYS no pide, error de red no rompe).
- **E2E en preprod** (datos sintéticos; nunca en producción): una reparación con chasis de otro color → el stock enseña el color
  nuevo con «NSYS: …» y el móvil sale en «Por pasar a NSYS»; al borrar la reparación, sale de la lista.

## 6. Entrega
- En las mismas ramas de la 0.9.21 (`dev_v0921_sku_taller`), después de las tareas de la pieza 1, o en ramas propias desde ellas;
  lo fija el plan.
- Paridad del formulario (la banda) y del rastro; CHANGELOG y novedades de la 0.9.21 (avisar al taller: ahora las reparaciones
  cambian el color y el grado del móvil).
- Merge `--no-ff`, push y tags solo con el OK del usuario.

## 7. Fuera de esta pieza
- Otras piezas que puedan cambiar el grado (pantalla, glass, pulido): no cambian nada; si hace falta, se corrige a mano.
- Escribir en NSYS desde el ERP.
