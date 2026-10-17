# 🎉 Novedades — Versión 0.9.17 (web)

Primera pieza del módulo de teléfonos: el **stock de teléfonos** y el **rastro** de cada teléfono, con los datos de NSYS.

---

## 🧰 Para todos

- Al escribir un IMEI, el **modelo** se rellena con los datos de NSYS (antes, con IMEIcheck). Si no sale, se escribe a
  mano, como siempre.
- El técnico ve **IMEIs** como hasta ahora.

---

## 👀 Supertécnicos y administrador

### 📱 Stock de teléfonos (grupo nuevo «Teléfonos»)

- Los móviles que hay en la empresa según NSYS, agrupados por **modelo y capacidad**. Cada grupo dice cuántos hay
  listos, en el taller y en revisión, y cuántos están libres.
- Al elegir un modelo salen sus móviles: color, grado, batería, eSIM, SKU, ubicación, estado y días.
- **Filtros de un clic:**
  - color y grado, en chips con su número;
  - batería, ubicación y estado, en desplegables;
  - «Quitar filtros» los borra todos.
- **Buscar IMEI** en todos los móviles, también en los vendidos.
- «Ver también lo que no cuenta como stock» enseña, en gris, los que esperan pieza, no se pueden vender o están en
  «Pedidos realizados» (de pedido). No suman en los totales, igual que en el Excel de stock.
- Arriba se ve la hora de la última exportación de NSYS:
  - si va con retraso, sale en ámbar;
  - si no llegan datos, lo avisa una banda roja.
- El ERP mira cada 5 minutos si NSYS ha dejado una exportación nueva en Drive.
- Es solo de consulta: los cambios se siguen haciendo en NSYS.

### 🔎 IMEIs

- El detalle de un IMEI lleva encima la **ficha de NSYS**: modelo, color, grado (y el que tenía antes), SKU, ubicación,
  estado, lote y días en stock.
- **«Ver rastro»** abre a la derecha la historia del teléfono: entradas, cambios de caja y de grado, la venta y los
  trabajos del taller. Un trabajo del rastro marca su fila en la tabla.
- Un IMEI que nunca ha pasado por el taller también se puede abrir.
- Al filtrar la lista por un IMEI, si NSYS tiene móviles con ese IMEI y sin trabajos del taller, sale un aviso con el
  enlace.
