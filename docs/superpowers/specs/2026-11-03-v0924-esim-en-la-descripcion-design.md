# 0.9.24 — La descripción del móvil dice si es eSIM

Fecha: 2026-11-03. Estado: **implementada y en `main` el 03/11** (servidor `1f2d89c`, web `c65ddfb`), falta preprod y producción; diseño aprobado en el brainstorming del 03/11; plan escrito (`plans/2026-11-03-v0924-esim-en-la-descripcion.md`). Los ajustes del plan están en el §7 y mandan sobre lo de arriba si chocan.
**Servidor + web, sin base de datos.** Amplía la 0.9.21 «Descripción completa donde pone «Modelo»»
(`2026-10-22-v0921-descripcion-completa-design.md`), que dejó la descripción en modelo, capacidad y color.

## 1. Qué cambia

**Hoy** la descripción del móvil es «iPhone 14 256GB Azul» sea o no eSIM. La eSIM solo se ve en la tabla del stock de
teléfonos, como marca junto al grado. **Ahora**, cuando el móvil es eSIM, la descripción lo dice detrás del modelo:
«iPhone 14 eSIM 256GB Azul». Un móvil con SIM física sigue como hoy.

**Decisiones del usuario (03/11):**

| Tema | Decisión |
|---|---|
| Dónde | En **todas** las pantallas que enseñan la descripción del móvil: las listas del taller y las de NSYS (ficha, buscador, «Por pasar a NSYS», «Pegar varios IMEIs»). |
| Formato | «eSIM» **detrás del modelo**, como lo escribe NSYS en el producto («Apple iPhone 14 eSIM 256GB Midnight») y como lo enseñaba el JavaFX. |
| SIM física | No se escribe nada. |
| CSV | La columna «Modelo» dice «iPhone 14 eSIM»; las columnas no cambian. |
| Versión | **0.9.24** propia: la 0.9.23 (aviso «no está en NSYS», solo web) la lleva otra sesión. |

## 2. Comportamiento

### 2.1 El texto
- `<nombre del modelo> eSIM <capacidad> <color>` si el móvil es eSIM; si no, `<nombre del modelo> <capacidad> <color>`, como
  hoy. Las partes que falten no se escriben, como en la 0.9.21.
- Se sabe si es eSIM por el espejo de NSYS: `Nsys_movil.ES_ESIM`, de la fila más reciente del IMEI, la misma de la que sale la
  capacidad. Es el «eSIM» que NSYS escribe en el producto (`Normalizacion.esEsim`). El taller no lo cambia (0.9.21: modelo,
  capacidad y eSIM no se corrigen en el taller) y no se mira `Telefono.ES_ESIM`, del JavaFX.
- Un IMEI que no está en el espejo (cliente externo) no lleva eSIM, igual que no lleva capacidad.
- Sin nombre de modelo, nada: si el texto es vacío o «Sin modelo en el maestro», no se le pone «eSIM» detrás.
- Una sola función de la web pone «eSIM» detrás del nombre del modelo. La usan `descripcionMovil` (a través de quien la llama)
  y las columnas «Modelo» de los CSV, así que todos los sitios dicen lo mismo.

### 2.2 Dónde
- **Listas del taller** (columna «Modelo», «Copiar celda» y textos con el modelo), las mismas de la 0.9.21 §2.2:
  - Asignaciones (reparación, glass y pulido);
  - Pendientes (reparación y glass) y Pulidos pendientes, con el texto de confirmación «IMEI · modelo»;
  - Historial de trabajos (R/G) e Historial de pulidos (P);
  - IMEIs (el modelo del grupo), la barra del detalle del IMEI y el aviso «También hay N móviles sin trabajos del taller con ese
    IMEI» («(iPhone 14 eSIM, vendido el …)»).
- **NSYS:**
  - la ficha del IMEI;
  - el buscador de IMEI;
  - la columna del modelo de las tarjetas de «Por pasar a NSYS»: «Cambio de color», «Cambio de grado», «Revisar a mano» y
    «Sin grado en NSYS»;
  - «Pegar varios IMEIs», en la tabla y en la copia para Excel.
- **CSV:** en `reparaciones_pendientes` (Asignaciones), `pulidos_pendientes`, `historial_pulidos` y `agrupado_resumen`
  (IMEIs), la columna «Modelo» lleva «eSIM» detrás. «Capacidad» y «Color» no cambian.
- **No cambian:**
  - la tabla del stock de teléfonos: ya lleva la marca «eSIM» junto al grado;
  - la lista de modelos del stock y su buscador: agrupan el 14 y el 14 eSIM en el mismo modelo, como hoy;
  - «No está en NSYS»: enseña el modelo del taller y el móvil no está en el espejo;
  - el producto que hay que poner en NSYS (ya es el nombre de NSYS, con su eSIM) y las plantillas Excel de NSYS.

### 2.3 Servidor
- Las respuestas que ya llevan la capacidad del espejo ganan un campo `esEsim` (booleano, no nulo), que sale del mismo
  `MovilNsys` que la capacidad (`esEsim()`):
  - `ReparacionResumen` (listas del taller): `esEsim` al final, con getter y setter, a `false` de nacimiento; `DescripcionMovil`
    lo rellena junto con la capacidad y el color, con la misma lectura del espejo. Las demás rutas que construyen
    `ReparacionResumen` lo dejan a `false`.
  - `FichaMovil`, `MovilBuscado` y `MovilComprobado` (`StockTelefonosController`); un IMEI no encontrado, `false`.
  - `FilaColorNsys`, `FilaGradoNsys`, `FilaRevisarNsys` y `FilaSinGradoNsys` (`TarjetasNsys`); un aviso sin móvil en el
    espejo, `false`.
- No cambian `MovilStock`, que ya lo lleva; `FilaPorPasar`, la ruta de la 0.9.21 que la web ya no usa; ni `FilaAltaNsys`.
- No hay consultas nuevas: el dato viene en la fila del espejo que ya se lee.

## 3. Errores y casos límite
- **Falla la lectura del espejo o la del taller** en las listas del taller: ninguna fila lleva capacidad, color ni eSIM, como
  hoy (todo o nada de la 0.9.21 §7.3).
- **IMEI en varios lotes:** vale la fila más reciente, la misma de la capacidad.
- **El móvil de un IMEI en la vista IMEIs** (`movilDe`): es eSIM si lo es en alguno de sus trabajos. Si una de las tres listas
  no pudo leer NSYS, trae `false`, y vale lo de las otras (como la capacidad y el color de la 0.9.21 §7.4).
- **NSYS tiene mal la eSIM:** se corrige en NSYS; el taller no la toca.
- **Web nueva con servidor viejo:** el campo no llega y se lee como «no es eSIM»; la descripción sale como hoy, sin error.
  **Servidor nuevo con web vieja:** la web no mira el campo. Ningún orden de despliegue rompe nada.

## 4. Pruebas
- **Servidor (JUnit):**
  - `DescripcionMovil` rellena `esEsim` (eSIM, SIM física, fuera del espejo y fallo del espejo);
  - las rutas de las listas del taller (también para el técnico), la ficha, el buscador, «Comprobar» y las cuatro tarjetas
    devuelven `esEsim`;
  - `OpenApiContractTest` con el campo nuevo.
- **Web (Vitest):**
  - la función del nombre: con y sin eSIM, sin modelo y con «Sin modelo en el maestro»;
  - `descripcionTrabajo`, `movilDe` y las cuatro copias de la descripción de NSYS, con y sin eSIM;
  - las columnas y «Copiar celda» de cada pantalla y los cuatro CSV con «iPhone 14 eSIM» en «Modelo»;
  - un móvil sin eSIM sigue diciendo lo de hoy.
- **E2E en preprod, de solo lectura:** toma por la API un IMEI eSIM del espejo con trabajos del taller y otro sin eSIM, y
  mira la descripción en IMEIs, en el detalle del IMEI y en la ficha. No guarda nada ni limpia.

## 5. Versión y entrega
- Rama `dev_v0924_esim` en los dos repos desde `main` (servidor `d714d3f`, con la 0.9.22.2; web `206fa5b`, 0.9.22.1), en
  worktrees (`wt/servidor-0924-esim`, `wt/web-0924-esim`): el clon principal y los otros worktrees pueden ser de otras
  sesiones. Si la 0.9.23 entra antes en `main` de la web, se mezcla `main` en la rama antes del merge.
- Versión `0.9.24` en el servidor y en la web; CHANGELOG `[0.9.24]` en los dos, novedades de la 0.9.24 y paridad de las
  pantallas del taller.
- Preprod (JUnit, Vitest y E2E) → producción: primero el servidor (lleva también la 0.9.22.2), después la web. Sin migración y
  compatible en los dos sentidos (§3), así que puede ir **en horario** si el corte es despreciable.
- Merge `--no-ff`, push, tags y despliegues, solo con el OK del usuario.

## 6. Fuera
- Un filtro o una búsqueda por eSIM en las listas del taller.
- Separar el 14 y el 14 eSIM en la lista de modelos del stock.
- Corregir la eSIM desde el taller.

## 7. Ajustes del plan (2026-11-03)

Fijados al escribir el plan (`plans/2026-11-03-v0924-esim-en-la-descripcion.md`); mandan sobre lo de arriba si chocan.

1. **El servidor no tiene CHANGELOG ni versión en el `pom.xml`:** su 0.9.24 es el tag y la entrada del CHANGELOG de la web; el
   contrato se apunta en `docs/api_contract.md` del servidor (§5).
2. **`main` del servidor ya no es `d714d3f`:** el 03/11 es `71f1ee1`, que suma a la 0.9.22.2 el borrado de las 24 rutas retiradas
   (sp7b tarea 27, otra sesión). La rama sale de `origin/main` al crearla y la subida del servidor lleva las tres cosas. El
   contrato de la web, regenerado aquí, pierde además esas rutas si nadie lo ha refrescado antes.
3. **`esEsim` va al final** de cada respuesta, como la capacidad y el color de la 0.9.21. Los constructores cortos de
   `FilaColorNsys`, `FilaGradoNsys` y `FilaRevisarNsys` («un móvil en la empresa», sin uso hoy) pasan `false`.
4. **Nombres en la web:** `conEsim(nombre, esEsim)`, `nombreMovilNsys(m)` y la constante `SIN_MODELO_MAESTRO` en
   `src/shared/lib/telefonoNsys.ts`; `nombreTrabajo(t)` en `src/modules/taller/lib/modelos.ts`.
5. **Fábricas de prueba:** `esEsim: false` por defecto, salvo `filaGrado()` y `filaRevisar()`, que ya describen un móvil eSIM
   («Apple iPhone 14 eSIM 256GB Midnight», chasis `chai14yellowesim`): `true`.
6. **E2E** `tests/e2e/esim-descripcion.spec.ts`: se salta con el motivo si el historial de preprod no tiene un trabajo de un
   móvil eSIM y otro sin eSIM, los dos con modelo y capacidad de NSYS.
7. **Si el nombre ya dice eSIM, no se repite** (revisión final, 03/11): un modelo fuera del catálogo del ERP se nombra con el
   texto del maestro, que ya lleva «eSIM» («17 e eSIM» → «iPhone 17 e eSIM»); `conEsim` no le añade otro. Misma regla que
   `Normalizacion.esEsim` del servidor (contiene «esim», sin mirar mayúsculas).
