# Rediseño de la fila "Otro" en el modal de reparación — Diseño

**Fecha:** 2026-06-11
**Item de backlog:** Item 1 (UAT junio 2026)

## Objetivo

Convertir la fila "Otro" del modal de reparación por componente en **acciones de reparación libres**: trabajos que no consumen una pieza inventariada (ej. "limpiar cámara", "reflujo de placa", "abrir teléfono"). El técnico puede añadir **varias** acciones, cada una con una **descripción de texto obligatoria**, sin contador, sin "reutilizado" y sin descontar stock.

## Estado actual (contexto)

El modal lo gestiona `FormularioReparacionController`. `cargarFilas()` crea una `FilaUI` por cada grupo de componente que devuelve `ComponenteDAO.getAgrupadosPorTipo()`, incluido el grupo **"otro"**. Hoy la fila "otro":
- Usa el desplegable `cbSku` con los SKU `otroi<modelo>` (ej. `otroi15`), el contador `+/-` y el checkbox "reutilizado", igual que las demás filas.
- Se trata de forma especial para el stock (`lblStock` muestra "—", el `+` no se limita por stock) pero al guardar se emite como una fila normal.

Al guardar, `insertarCompleta` (servidor) inserta una fila en `Reparacion_componente` (`ID_COM`, `ES_REUTILIZADO`, `OBSERVACIONES`, `CANTIDAD`) y, si no es reutilizado, descuenta stock del componente.

El historial (`ReparacionController{Tecnico,Admin,SuperTecnico}`) muestra `Componente = TIPO del componente` y `Observaciones = OBSERVACIONES` en columnas separadas. Permite **editar** (abre el formulario de edición) y **borrar** una reparación.

## Diseño

### Modelo de datos (sin cambios en BD)

Cada acción "otro" se guarda como una fila de `Reparacion_componente`:
- `ID_COM` = el `otroi<modelo>` correspondiente al **modelo de la asignación** (automático; no se elige SKU).
- `OBSERVACIONES` = la **descripción** de la acción (obligatoria).
- `ES_REUTILIZADO = 0`, `ES_SOLICITUD = 0`.
- `CANTIDAD = 0` → mecanismo para que la acción sea **neutra de stock** (la resta de stock con cantidad 0 es un no-op; igual al editar y al borrar). No requiere cambios en el servidor.

Cada acción genera su `R*` (reparación real) vía `insertarCompleta`, cuenta para cerrar la asignación, y **no** participa en la mecánica de solicitud/agotado ni bloquea el cierre (encaja con el fix de solicitudes ya existente, que solo considera filas `ES_SOLICITUD = 1`).

### UI del modal

- Se **elimina** la antigua `FilaUI` del grupo "otro" (contador, SKU, reutilizado). `cargarFilas()` no crea fila para el grupo "otro".
- Se añade una **sección "Otras acciones"** al final del modal (debajo de `contenedorFilas`), visualmente distinta (cabecera propia). Contiene:
  - Una **lista compacta**: una línea editable por acción (campo de texto con la descripción) + icono de borrar por línea.
  - La lista vive en un área de **altura máxima con scroll interno** (para que el modal no crezca sin fin y las filas de componente sigan visibles).
  - Un **badge** con el número de acciones.
  - Un botón **"+ Añadir acción"** que añade una línea nueva vacía y la enfoca.
- La sección aparece cuando hay **modelo seleccionado** (igual que las filas de componente), porque el `otroi<modelo>` depende del modelo.
- Sin contador `+/-`, sin "reutilizado", sin indicador de stock, sin botón de solicitud.

### Guardado y validación

- Una línea de acción con texto **no vacío** (tras `trim`) cuenta como **fila activa** → habilita el botón Guardar (junto con las filas de componente activas).
- Las líneas **vacías se descartan** (no se guardan); no bloquean el guardado.
- Una asignación puede **completarse solo con acciones "otro"** (sin ningún componente de stock): cada acción genera su `R*` y `creoReparacion = true`, así que la asignación se cierra normalmente.
- Cada acción se emite como una `FilaReparacion` con `idCom = otroi<modelo>`, `cantidad = 0`, `reutilizado = false`, `observacion = descripción`, `esSolicitud = false`.

### Stock

Las acciones "otro" son **neutras de stock**: no descuentan al crear, ni al editar, ni al borrar. Se consigue con `cantidad = 0` (las operaciones de stock del servidor quedan en no-op). No se tocan las mecánicas de stock de los componentes normales.

### Historial (presentación elegida: opción B)

- Columna **Componente** = `otroi<modelo>` (el SKU ya codifica que es un "otro" de ese modelo), **Observaciones** = la descripción.
- **Editar y borrar** como cualquier reparación:
  - **Borrar** elimina el `R*` (funciona con la lógica existente; al ser `cantidad = 0` y no reutilizado, la restauración de stock es no-op).
  - **Editar** abre el formulario de edición; al detectar que el componente es un "otro" (prefijo "otro"), presenta un **editor de texto simple** para la descripción (en vez de la maquinaria de componente). Al guardar, actualiza `OBSERVACIONES` (manteniendo `cantidad = 0`).

### CSV

La exportación del historial sigue la misma lógica: para una acción "otro", Componente = `otroi<modelo>`, Observaciones = la descripción.

## Componentes afectados

- `FormularioReparacionController` — nueva sección "Otras acciones" (alta de varias acciones); `cargarFilas()` omite el grupo "otro"; lógica de guardado (emitir filas otro con `cantidad = 0`); validación del botón Guardar; **modo edición** de una acción "otro" (editor de texto).
- `FormularioReparacionView.fxml` — contenedor de la sección "Otras acciones".
- Servidor: **sin cambios** previstos (el `cantidad = 0` hace neutras las operaciones de stock en insertar/editar/borrar).
- Historial (`ReparacionController*`): sin cambios estructurales (muestran Componente/Observaciones como hoy; editar/borrar ya existen y funcionan para el `R*` de un "otro").

## Riesgos

- La detección de "es un otro" se basa en el prefijo "otro" del tipo de componente; hay que aplicarla de forma consistente (alta, guardado, edición).
- El modo edición de una acción "otro" es un flujo nuevo dentro del formulario de edición (que hoy asume edición de un componente con su maquinaria).
- Verificar que `cantidad = 0` no rompe ninguna validación del servidor ni el historial (la cantidad no se muestra de forma prominente).

## Pruebas (manual)

1. Con un modelo seleccionado, añadir varias acciones "otro" con texto → se guardan; la asignación se cierra (si no hay piezas, solo con "otros").
2. Dejar una línea de acción vacía → no se guarda; no bloquea.
3. Intentar habilitar Guardar solo con una acción "otro" con texto → se habilita.
4. Stock: tras guardar acciones "otro", el stock del `otroi<modelo>` y de todo lo demás **no cambia**.
5. Historial: la acción aparece con Componente = `otroi<modelo>` y Observaciones = el texto.
6. Editar la acción desde el historial → cambia el texto; el stock no cambia.
7. Borrar la acción desde el historial → desaparece; el stock no cambia.
8. CSV del historial → la acción sale con Componente = `otroi<modelo>`, Observaciones = el texto.
9. La zona "Otras acciones" con muchas acciones → scroll interno, el modal no se desborda.

## Fuera de alcance

- "Otro" genérico sin modelo (toda acción va atada al `otroi<modelo>` de la asignación).
- Cambios en la mecánica de stock de los componentes normales.
- El borrador persistente del modal (es el Item 2 del backlog, va después de este).
