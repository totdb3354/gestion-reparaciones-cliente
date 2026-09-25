# 🎉 Novedades — Versión 0.16.2

La glass deja de asignarse dos veces: marcas "Lleva glass" en la reparación y el programa se la da al técnico de glass que menos carga tiene. Y las estadísticas por puntos llegan pulidas tras dos rondas de uso: tarjetas con objetivo, IMEIs típicos, cada uno ve lo suyo y el fin de semana ya no engaña a las medias.

---

## 🧩 Glass automática al asignar la reparación

- En el modal de asignación, la reparación tiene la casilla **"Lleva glass"** (junto a "Reparación de chasis"). Al pulsar **Asignar**, nace sola en la cola Glass una asignación del mismo IMEI, con su modelo y su cliente, para el **técnico de glass con menos carga hoy** (cuentan sus reparaciones y sus glass, las de la base de datos y las que estás asignando en ese mismo lote). Si el teléfono tiene cliente se mira la carga de Pedidos; si es stock, la Total.
- La glass nace **verde**, con la pastilla **"auto"** y el nombre del técnico en la fila. Si no hay ningún técnico de glass disponible, queda pendiente para asignarla a mano.
- Casilla y glass van siempre a la par: **desmarcar** la casilla o **quitar** la reparación retira la glass; quitar la glass desmarca la casilla; escanear el mismo IMEI en la otra cola enlaza las dos entradas. Si cambias el técnico de la reparación, la glass "auto" se recalcula; una glass que hayas tocado a mano no se toca.
- Si el IMEI ya tiene una glass abierta, la casilla sale deshabilitada con "ya tiene glass: <técnico>".

## 👷 Técnicos de glass

- Nuevo botón **"Técnicos de glass"** en Asignaciones (junto a "Carga técnicos"): marca quiénes entran en el reparto automático. El SuperTécnico edita; el Admin lo ve. Queda en el log (`HABILITAR_GLASS` / `DESHABILITAR_GLASS`).
- En la cola Glass del modal, la lista de técnicos marca con **"glass"** a los habilitados.
- **Hasta que marques a alguien, toda glass automática nace pendiente**: es el primer paso tras actualizar.

## 🔁 El modal de asignación, más listo

- **El modelo es del IMEI**: lo eliges una vez y lo ven todas las entradas de ese teléfono (Reparación y Glass); un IMEI que el modal ya conoce nace con modelo al escanearlo, sin volver a buscar.
- **Técnico propuesto por cola**: el último técnico de Reparación solo se propone en Reparación y el de Glass en Glass. Y se recuerda en cuanto lo marcas, aunque el modelo no se haya detectado todavía.
- Los botones Reparación / Glass / Pulido muestran **cuántas entradas** tiene cada cola (en rojo si hay alguna pendiente), y las filas asignadas muestran sus técnicos.
- El modal **cabe en pantallas pequeñas** (portátil con escalado): se ajusta a la zona visible y hace scroll si hace falta. Y Enter sobre un modelo ya confirmado ya no cambia el modelo.

## 📊 Estadísticas por puntos, segunda ronda

- **Tarjetas en formato objetivo**: "Puntos · mes" y "Puntos · hoy" muestran el % alcanzado respecto al mes anterior (y hoy respecto a la media de ese día de semana): gris hasta el 100%, verde al superarlo, nunca rojo.
- **IMEIs típicos por técnico**: tarjeta con la media de teléfonos tocados por día trabajado, y en la leyenda cada técnico con sus IMEIs del periodo visible (verde al alcanzar la media).
- **👥 Técnicos en estadísticas** (solo Admin): excluye de Promedio, Equipo y tarjetas a quien no repara a jornada completa.
- **Cada uno ve solo las suyas**: Técnico y SuperTécnico ven su propia serie y sus tarjetas; Equipo, Promedio e IMEIs típicos siguen como referencia. Solo el Admin ve las de todos.
- **El fin de semana suma, no promedia**: un sábado de horas extra cuenta en tarjetas, semanas y meses, pero en la vista Día no cuenta como "día trabajado" en el Promedio, la media de cada técnico ni IMEIs típicos. El tooltip del Promedio lo indica con "L–V".
- Detalles: la etiqueta de la ventana dice la unidad real ("30 días con actividad", "16 semanas"…); el selector Puntos / Puntos·día se deshabilita en Día; en la pestaña Stock, `g` es Glass y `lcd` es Pantalla, como en el formulario.

---

## 🚚 Notas de despliegue

- Requiere el **servidor actualizado** (`main` `1559a72`, ya desplegado en preproducción) y dos migraciones aditivas, **ya aplicadas** en preproducción: `migracion-estadisticas-exclusion.sql` (`Tecnico.ES_ESTADISTICA`) y `migracion-glass-habilitados.sql` (`Tecnico.ES_GLASS`).
- **Orden**: ALTER → servidor → cliente. Retrocompatible: los clientes 0.16.1 conviven sin enterarse; un cliente 0.16.2 contra un servidor antiguo funciona, pero sin técnicos de glass (toda glass automática nace pendiente).
- **Primer paso tras actualizar**: el SuperTécnico abre "Técnicos de glass" y marca a quienes hacen glass.
