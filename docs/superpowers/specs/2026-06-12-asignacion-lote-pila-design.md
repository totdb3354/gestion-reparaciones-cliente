# Asignación por componente en lote ("pila") — Diseño

**Fecha:** 2026-06-12
**Item de backlog:** Item 5 (UAT junio 2026).

## Objetivo

Hacer más fluida la asignación de reparaciones por componente: poder **escanear varios IMEIs** y asignarlos en un mismo flujo (una "pila"), en vez de uno por uno reabriendo el formulario. Los técnicos y el comentario se mantienen entre IMEIs (el caso habitual es varios IMEIs a los mismos técnicos). Nada se escribe en BD hasta un **Guardar** final que crea todas las asignaciones de golpe.

## Estado actual (contexto)

El modal lo construye `PendientesSuperTecnicoController.abrirFormularioAsignacion()` (programático, no FXML). Hoy es **de un solo IMEI**:
- Campo IMEI (15 díg) con **auto-lookup del modelo** (`telefonoDAO.getModelo(imei)` en un hilo daemon; rellena `tfModelo` o pide selección manual).
- Buscador de **modelo** (autocompletado sobre `FormularioReparacionController.MODELOS_ORDENADOS` + `traducirModelo`).
- **Técnicos** (checkboxes); los que ya tienen asignación activa para ese IMEI se deshabilitan (`reparacionDAO.getTecnicosConAsignacionActiva(imei)` → `List<Integer>`), con una **pill "N asignados"**.
- **Comentario** (opcional).
- "Asignar reparación": `telefonoDAO.insertar(imei, modelo)` + un `reparacionDAO.insertarAsignacion(imei, idTec, comentario)` por técnico marcado; luego **resetea** y deja el modal abierto, foco en IMEI.

**Patrón de referencia ya existente:** `PulidoSuperTecnicoController.abrirFormularioAsignacion()` implementa un "lote": acumula entradas en una lista local (`record ImeiConf`), el escaneo añade filas en vivo, hay editor inline por fila, y un **`btnGuardar` que inserta todo el lote al final**; al cerrar con lote no vacío pide confirmación (`ConfirmDialog`). Pulidos asigna a **un** técnico y **sin** modelo; aquí necesitamos **varios técnicos** y **modelo obligatorio** por IMEI.

## Diseño

Se **sustituye** el modal de un IMEI por un modal de **lote/pila**, reutilizando:
- el esqueleto "acumular en local + guardar al final" de pulidos,
- el formulario por-IMEI del modal actual (lookup de modelo, buscador de modelo, checkboxes de técnicos, pill de ocupados),
- y los DAO existentes (`telefonoDAO.insertar`, `reparacionDAO.insertarAsignacion`, `reparacionDAO.getTecnicosConAsignacionActiva`, `telefonoDAO.getModelo`).

### Estado local

Una entrada mutable por IMEI (clase/record interno, p. ej. `EntradaAsignacion`):
```
{ imei, modeloCode (o null), tecnicos (List<Tecnico>/Set<Integer>), comentario }
```
La pila es una lista de estas entradas. **No se toca BD** hasta el Guardar final.

### Layout del modal

- **Campo de escaneo** (arriba): IMEI de 15 díg / Enter → añade una entrada a la pila **roja**. Saneo de input como hoy (solo dígitos, máx 15). **Validación de duplicado**: si el IMEI ya está en la pila (roja o verde), no se añade y se avisa ("Ese IMEI ya está en la pila").
- **Pila en 2 secciones:**
  - **Pendiente de asignar** (rojo): entradas escaneadas aún **no configuradas** (sin técnicos) o con **`⚠ falta modelo`**. Cada item con **✕** (quitar de la pila).
  - **Asignados** (verde): entradas ya configuradas (**modelo + ≥1 técnico**), en estado local, "sin guardar". Cada item con **✕**.
- **Formulario derecha (IMEI en curso / en edición):** el del modal actual —
  - **Modelo**: al cargar un IMEI rojo recién escaneado se lanza el lookup async; mientras, "Buscando…"; si lo encuentra, modelo auto; si falla / no encontrado / **límite de la API**, la entrada queda `⚠ falta modelo` (roja) hasta ponerlo a mano con el buscador. **Obligatorio.**
  - **Técnicos**: checkboxes (varios). La selección **se mantiene** entre IMEIs (la del formulario se arrastra al siguiente IMEI que se cargue). Los técnicos con asignación activa para ese IMEI (`getTecnicosConAsignacionActiva`) salen **deshabilitados** + pill "N asignados", como hoy.
  - **Comentario**: textarea, **se mantiene** entre IMEIs.
  - Botón **"Asignar →"**: mueve la entrada a **verde** (requiere modelo + ≥1 técnico). Botón **"Saltar"**: la deja en rojo y carga la siguiente.
- **Barra final**: progreso + **"Guardar (N)"**.

### Click en la pila

- **Click en una roja** → carga esa entrada en el formulario (saltar el orden).
- **Click en una verde** → carga esa entrada en **modo edición** con su config local (modelo / técnicos / comentario). Edición **completa**: cambiar modelo, añadir/quitar técnicos, editar comentario. Como es estado local, **editar y quitar no tocan BD** (la asignación todavía no existe), así que no hay riesgo de perder trabajo de un técnico.

### Guardado

- **"Guardar" habilitado ⟺ la pila roja está vacía**: no quedan pendientes de asignar **ni** IMEIs sin modelo (un IMEI sin modelo permanece en rojo; un lookup "Buscando…" cuenta como rojo). Así nunca se guarda algo incompleto.
- Al pulsar: por cada entrada **verde**, `telefonoDAO.insertar(imei, modeloCode)` + un `reparacionDAO.insertarAsignacion(imei, idTec, comentario)` por cada técnico. Luego cierra y `cargar()`.
- **Cerrar con la pila no vacía** (roja o verde) → `ConfirmDialog` "se descartarán N IMEIs", como en pulidos.

### Conflictos / validación

- **En vivo, por IMEI**: al cargar cada IMEI, `getTecnicosConAsignacionActiva` deshabilita a los técnicos ya ocupados para ese IMEI (igual que hoy).
- **En el commit**: antes de insertar cada entrada se revalida con `getTecnicosConAsignacionActiva` (otra sesión pudo asignar entremedias); los pares IMEI/técnico en conflicto se **saltan y se reportan** al final (mismo patrón de "conflictos" que `confirmarCambiosTecnico`). El resto se guarda.

## Componentes afectados

**Cliente (solo):**
- `PendientesSuperTecnicoController.java` — reescribir `abrirFormularioAsignacion()` al modelo de pila; añadir la clase/record interno `EntradaAsignacion` y los helpers de la lista (añadir, mover rojo↔verde, editar, quitar, validar duplicado, commit del lote). Reutiliza el lookup de modelo, el buscador de modelo, los checkboxes de técnicos y la pill de ocupados ya presentes en el método actual.

**Sin cambios de servidor** — se reutilizan los endpoints/DAO existentes (`insertarAsignacion`, `telefonoDAO.insertar`, `getTecnicosConAsignacionActiva`, `getModelo`). **Sin cambios de FXML** (el modal es programático).

## Estilo

Sigue el estilo real de la app (cabeceras navy `#2C3B54`, fondo de modal `#DDE1E7`, tarjetas blancas, pills/checkboxes/botones existentes), tomando los patrones del modal actual y del lote de pulidos. Los mockups del companion (rojo/verde, dos columnas) son solo referencia conceptual de la disposición y el flujo, no del aspecto pixel a pixel.

## Riesgos

- **Lookups async en vuelo al guardar**: un IMEI "Buscando…" cuenta como rojo → Guardar bloqueado hasta resolver. Si el lookup falla, queda `⚠ falta modelo` (rojo) hasta ponerlo a mano.
- **Técnicos arrastrados que están ocupados** para el nuevo IMEI: al cargar el IMEI se deshabilitan/deseleccionan (la validación de hoy ya lo hace).
- **Carrera en el commit**: dos supertécnicos asignando el mismo IMEI/técnico → se revalida y se reportan los saltados.
- **Volumen**: trivial (un puñado de IMEIs por lote).

## Pruebas (manual)

1. Escanear 3 IMEIs → caen en **rojo**. Configurar cada uno (técnicos + modelo auto) → pasan a **verde**. "Guardar" se habilita solo con la pila roja vacía. Guardar → se crean las asignaciones (una por técnico de cada IMEI).
2. IMEI con lookup fallido / límite API → `⚠ falta modelo`, permanece rojo; ponerlo a mano → pasa a verde; entonces sí Guarda.
3. Escanear un IMEI ya presente en la pila → aviso, no se añade.
4. ✕ en un rojo y en un verde → se quitan de la pila (nada en BD).
5. Click en una verde → edita su config (técnicos/modelo/comentario); Guardar refleja el cambio.
6. Técnico ya asignado a ese IMEI (de antes, en BD) → sale deshabilitado al cargar el IMEI.
7. Cerrar con la pila no vacía → confirma descartar.
8. Técnicos y comentario se mantienen al pasar de un IMEI al siguiente.
9. Un IMEI con varios técnicos → una asignación por técnico tras Guardar.

## Fuera de alcance

- El modal de pulidos (ya tiene su propio lote) y el modal de reparación por componente (`FormularioReparacionController`, no relacionado).
- Cambios de servidor o de esquema.
- Persistir la pila si se cierra la app (la pila es efímera dentro de la sesión del modal).
