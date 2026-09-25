# 🎉 Novedades — Versión 0.12.0

¡Hola! Esta actualización añade los **pedidos de "Otros"**, hace mucho más cómodo trabajar con **lotes de IMEIs**, y mejora cómo la app **avisa cuando hay problemas de conexión o caduca la sesión**. Esto es lo que vas a notar:

---

## 📦 Pedidos de "Otros" (consumibles)

- Ahora puedes **apuntar pedidos que no son piezas** (alcohol isopropílico, consumibles, material…) desde la vista de **Pedidos**, con un selector **Componentes | Otros**.
- Tienen su propia **tabla**, un **modal multilínea** para meter varios de golpe, y los mismos **filtros** que los pedidos normales.
- **No afectan al stock** — son solo un registro de lo que se pide.
- _Importante:_ esta función necesita el **servidor actualizado**.

---

## ⌨️ Pegado de lote de IMEIs

- En los **modales de asignación** (normal y de pulidos) y en los **filtros de IMEI** de todas las vistas, puedes **pegar un bloque de IMEIs concatenados** y la app los **reparte solos cada 15 dígitos**.
- En los modales, si el lote está corrupto (no cuadra a múltiplos de 15) se avisa y no se mete nada; en los filtros, un trozo que no cuadre se marca en rojo.

---

## 👥 Aviso de "N asignados" bajo el IMEI

- En la vista de **Asignaciones**, debajo del IMEI aparece un pequeño **"2 asignados"** cuando ese dispositivo lo tienen **varios técnicos** a la vez — para verlo de un vistazo sin abrir nada.
- Solo aparece cuando hay 2 o más; se actualiza al instante si borras una asignación.

---

## 📡 Conexión y sesión más claras

- Si el servidor falla un momento, ya **no** salta un molesto cuadro de error que se queda colgado: ahora aparece una **franja discreta "Sin conexión. Reintentando…"** que **desaparece sola** al recuperarse.
- Cuando **caduca tu sesión**, la app te **avisa y te lleva al login** de forma controlada (en vez de fallar todo en silencio).
- Los avisos de error salen **pegados a su ventana**, no como ventanas sueltas perdidas por la pantalla.

---

## 🖱️ Otros ajustes

- **Historial**: ahora **doble clic** abre el detalle de un teléfono en los tres roles, y el **IMEI se ve con más contraste** en la vista de técnico.
- Las **barras de filtros** de Stock, Clientes y Estadísticas bajan a **debajo del título** y se adaptan al tamaño de la ventana.

---

_¿Dudas con alguna novedad? Pregunta a Imad._
