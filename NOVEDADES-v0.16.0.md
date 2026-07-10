# 🎉 Novedades — Versión 0.16.0

El medidor de carga da el gran salto: el porcentaje ya no reparte un pastel entre técnicos, ahora **mide el día de verdad**. Cada técnico ve cuánta de su jornada de hoy está comprometida — contando lo que ya ha completado y lo que le queda — y tú decides las asignaciones con esa foto delante.

---

## 📅 El porcentaje ahora habla de tu día

- Cada trabajo consume una fracción de la jornada según lo que cuesta: un **chasis = 1/8** del día, un **glass = 1/17**, una **normal = 1/25**. Ejemplo: 4 chasis + 5 glass ≈ **79% del día**.
- Se suma **lo completado hoy + lo pendiente asignado**: completar una reparación no baja tu número (el día comprometido es el mismo), solo mueve trabajo de "pendiente" a "hecho".
- **La jornada real importa**: lunes y martes son de 9h, miércoles y jueves de 8h, viernes de 6h — el mismo trabajo pesa más un viernes. Sábado y domingo no hay jornada: verás "—" y "sin jornada hoy".
- **Se puede pasar del 100%** (la barra se llena, el número sigue: "112%"). Colores por nivel en barra y número: **azul** tranquilo, **ámbar** a partir del 70%, **rojo** del 90% en adelante.
- Las reglas finas se mantienen: **"por cerrar" pesa casi nada** (8,3% del tiempo de su tipo), **esperar una pieza no cuenta** hasta que llega el repuesto, y el pulido va aparte (quien pule, ese día solo pule).

## 🟣 Pedidos | 🔵 Total

- La ventana **Carga de técnicos** estrena toggle **Pedidos | Total**: Pedidos (solo trabajo con cliente, lo que urge) es la vista por defecto; Total lo cuenta todo.
- Cada vista tiene su **punto de identidad** fijo: violeta = Pedidos, azul = Total. El punto nunca cambia de color; el nivel lo cuenta el número.

## ✅ La barra verde: tu progreso del día

- Cada técnico tiene ahora **dos barras**: arriba, el **total del día** en su color de nivel; debajo, **en verde, lo ya completado hoy** — misma escala, así que cuando la verde alcanza a la de arriba, día liquidado.
- El **% completado se ve siempre** (✓ en verde junto a la cifra principal), sin tener que pasar el cursor. El hover sigue dando el desglose en unidades.

## 🖱️ De la barra a sus asignaciones, de un click

- Click en la fila de cualquier técnico en la ventana de carga → se cierra la ventana y aterrizas en **Asignaciones filtrado por ese técnico**. Como el "Ir a pedidos" de las notificaciones.

## 🧾 CSV de Asignaciones como debe ser

- El export de Asignaciones ahora es **espejo de la tabla**: ID, Tipo, Técnico, IMEI, Modelo, Fecha asignación, Comentario, Cliente, Asignado por, Urgente, Chasis, Por cerrar y **En espera de pieza**. Se acabaron las cabeceras del historial recicladas.

## 🎯 Modal de asignación, al grano

- Junto a cada técnico ahora solo aparece **su % de Pedidos** (punto violeta) — el dato que necesitas al repartir. El Total sigue en la ventana de carga.

---

## 🚚 Notas de despliegue

- Requiere el **servidor actualizado** con el endpoint `completadas-hoy` — **sin migración SQL** esta vez.
- **Orden**: servidor → cliente.
- Degradación amable: un cliente 0.16.0 contra el servidor viejo muestra la carga solo con lo pendiente (la barra verde sale vacía); los clientes 0.15.0 conviven sin enterarse.
