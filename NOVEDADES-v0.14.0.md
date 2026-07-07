# 🎉 Novedades — Versión 0.14.0

Actualización de mejoras rápidas: las **reparaciones de chasis ahora se marcan y se ven**, los **pedidos de "Otros" por fin se editan**, la **vista IMEIs recuerda dónde estabas**, y una buena tanda de arreglos finos que llevaban tiempo molestando. Esto es lo que vas a notar:

---

## 🔩 Chasis en asignaciones

- Al asignar una reparación puedes marcar el check **"Reparación de chasis"** (solo en la cola de Reparación; se resetea entre IMEIs para que no se te escape marcado).
- La marca **"Chasis"** aparece discreta bajo la píldora de tipo en **Asignaciones** y en **Mis pendientes** — que además estrena **columna Tipo** con la misma píldora que ve el SuperTécnico.
- ¿Error al marcar? Clic derecho en la asignación → **"Marcar/Quitar chasis"** (solo SuperTécnico; queda en los logs).
- Al completarse la reparación la marca cumple su función y desaparece: el componente usado ya cuenta la historia.

## 🗂️ Vista IMEIs más cómoda

- Al entrar al **detalle** de un IMEI y volver (con "← Volver" **o** clicando "IMEIs" en el menú lateral), la fila **sigue seleccionada y a la vista** — se acabó el volver arriba del todo y buscar de nuevo.
- **Copiar celda** ahora también hace su destello azul aquí, como en el resto de tablas.

## 🧾 Pedidos de "Otros", editables

- Clic derecho → **Editar** en un pedido de otros componentes: **concepto, proveedor, cantidad, precio y divisa** (en pendiente, en camino o recibido). Sin urgente — no es mecánica de estos pedidos.
- El **CSV de pedidos** incluye ahora **precio unidad, divisa, total EUR y estado**; y con el toggle "Otros" activo se exporta **el CSV de otros** (antes salía la tabla equivocada).

## 🔍 Filtros y navegación

- **Filtro por cliente en Asignaciones**, igual que el de IMEIs e Historial (multiselección, con "(Sin cliente)").
- **"Ver Stock Completo"** del panel de notificaciones te lleva al apartado de **stock actual** (antes aterrizaba en la última pestaña que hubieras usado).

## 🖱️ Arreglos finos

- Los **desplegables de filtros** (técnicos, clientes, proveedores) ya no pegan el saltito hacia arriba al pasar el ratón por el fondo de la lista — la última fila ya no se esconde.
- Al **reasignar un técnico**, el desplegable ya no deja rastros: ni celdas fantasma en filas vacías ni el técnico "pintado" sobre otra fila.
- **Historial más limpio**: las reparaciones históricas de los técnicos de glass se han reclasificado como Glass — el historial de Reparaciones y su filtro de piezas quedan como deben.

---

## 🔭 Próximamente

Estamos preparando la ampliación más grande hasta la fecha: **gestión completa de teléfonos por lotes** — importar el fichero del proveedor, revisión con grado y diagnóstico funcional, estados y ubicaciones del teléfono de punta a punta (almacén → revisión → reparación → listo → enviado), y una vista IMEIs que pasa a ser el inventario completo. Llegará por partes en las próximas versiones.

---

## 🚀 Nota de despliegue

- Requiere el **servidor actualizado** (ya desplegado en preproducción).
- **Sin pasos especiales**: compatible con clientes 0.13.0, que pueden actualizarse en cualquier momento (hasta actualizarse no verán las marcas de chasis).
