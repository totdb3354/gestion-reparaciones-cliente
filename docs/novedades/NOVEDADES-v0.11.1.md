# 🎉 Novedades — Versión 0.11.1

¡Hola! Esta actualización mejora el historial con **nuevos filtros**, automatiza las **urgencias de pedidos**, pule la interfaz y **arregla el modal de edición de reparaciones**. Esto es lo que vas a notar:

---

## 🔎 Nuevos filtros en el historial

- **Filtro de Cliente** (en la vista **Agrupado**): muestra solo los teléfonos del/los cliente(s) que elijas, con una opción **"(Sin cliente)"** para ver los que no tienen.
- **Filtro de Pieza** (en la vista **Plano**): filtra por tipo de pieza — Glass, Pantalla, Marco, Batería, Cámara, Chasis u Otros.
- La **barra de filtros se adapta** al tamaño de la ventana: en pantallas pequeñas los filtros bajan de línea en vez de cortarse.

---

## ⚡ Urgencias automáticas por cliente

- Las reparaciones **con cliente** que llevan **más de un día** sin completarse se marcan **urgentes** solas (cada noche) → así no se te olvida ningún pedido.
- Al **asignar** una reparación con cliente, **ya no** se marca urgente en el momento (antes sí); el urgente llega solo si el pedido pasa de día. Marcar/quitar urgente a mano sigue funcionando igual.

---

## 📋 Orden de los pendientes

- En **Asignaciones** y **"Mis pendientes"**, el orden ahora es: **urgentes primero → luego las que tienen cliente → luego el resto**.

---

## 🖱️ Ajustes de interfaz

- El **clic en la cabecera de una columna ya no reordena** la tabla (el orden lo llevan los filtros y la prioridad).
- El **contador** de filas muestra el **número real** (antes se quedaba en "999+").
- Cabeceras de las vistas más **consistentes** (título, contador y botones de acción en su sitio).

---

## 🛠️ Arreglos en el modal de "Editar reparación"

- Al **editar** una reparación ya **no aparecen los botones "Guardar fila"** por fila (esos solo tienen sentido al crear una reparación nueva).
- El botón **"Guardar cambios"** ahora se muestra siempre que haya un cambio válido — incluso si **solo cambias el comentario** de una fila.
- Tocar una fila que **no tenía reparación** ya no da el error _"La asignación ya fue eliminada"_.
- _Nota:_ por ahora el modal de edición no permite cambiar **quién hizo** la reparación (lo dejamos para una mejora futura).

---

_¿Dudas con alguna novedad? Pregunta a Imad._
