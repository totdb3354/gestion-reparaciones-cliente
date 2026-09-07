# 🎉 Novedades — Versión 0.15.0

Actualización cortita pero con chicha: las reparaciones que **solo esperan el glass para cerrarse ya se marcan y se ven**, y estrenamos el **medidor de carga por técnico** para saber de un vistazo quién va hasta arriba y quién está libre. De regalo, el modal de asignación se vuelve más listo con los clientes.

---

## ✅ "Por cerrar": la reparación que solo espera su glass

- Cuando tu reparación está prácticamente hecha y **solo falta que te cedan el glass** para ensamblar y cerrar el móvil, márcala: clic derecho en **Mis pendientes** → **"Marcar por cerrar"**.
- La marca es un badge **verde** (el mismo verde de Glass, no es casualidad) que se ve en tu lista y también en Asignaciones.
- La marca **solo la pone quien repara**: cada técnico (y el SuperTécnico) marca únicamente **sus** asignaciones — el servidor lo vigila, no es solo cosmética.
- Al completar la reparación, la marca se va con ella. Y todo queda en el log (`MARCAR_POR_CERRAR` / `QUITAR_POR_CERRAR`).

## 📊 Carga de técnicos (Pedidos)

- Botón **"Carga técnicos"** arriba a la derecha en Asignaciones (SuperTécnico y Admin) → ventana con **una barra por técnico**, ordenada de más a menos cargado, **con los libres al 0% incluidos** — la respuesta rápida a "¿a quién se lo asigno?".
- ¿Qué cuenta? Solo asignaciones **abiertas con cliente** (reparación y glass; pulido no). Pesos: normal = 1 · **chasis = 2** · **por cerrar = 0,083** (casi nada: solo queda cerrar) · glass = 1.
- El porcentaje es la **cuota del total** (entre todos suman 100). Debajo de cada barra, el desglose: "6 normales · 5 chasis · 1 por cerrar…".
- En el **modal de asignar**, cada técnico sale con su **(N%)** al lado — decides con la carga delante.
- Se recalcula solo con cada recarga o acción; los filtros de la vista **no** cambian los números.

## 🤝 El modal de asignación ahora recuerda el cliente

- Igual que el técnico se mantiene entre IMEIs, ahora **el cliente también persiste**: eliges cliente una vez y los siguientes escaneos —y cada IMEI al que viajas con "Asignar"— salen ya con él puesto.
- Con una regla sagrada: **si el IMEI ya tiene cliente en la base de datos, manda el de la BD** (verás el reemplazo en cuanto responde el servidor). Y tu elección manual en una fila concreta manda sobre todo.
- "— Sin cliente —" también persiste, si es lo que toca.

## 🔍 Filtro de clientes en Asignaciones, más claro

- Ahora arranca con **todo marcado** ("Todos") y vas **desmarcando** lo que no quieres ver. Los clientes nuevos aparecen marcados y "Limpiar filtros" vuelve a "Todos".

---

## 🚚 Notas de despliegue

- Requiere el **servidor actualizado** con la columna `POR_CERRAR` (`sql/migracion-por-cerrar.sql`) — ambos ya en preproducción.
- **Orden obligatorio**: ALTER → servidor → cliente.
- Retrocompatible con clientes 0.14.0: pueden convivir durante la actualización (no verán la marca ni la carga, pero nada se rompe).
