# 🎉 Novedades — Versión 0.9.18 (web)

El **stock de teléfonos** dice quién tiene cada móvil, busca por SKU y cuenta por color y grado de un vistazo.
La ficha de un IMEI dice cuándo se revisó.

---

## 🧰 Para todos

- El técnico ve el programa como hasta ahora.

---

## 👀 Supertécnicos y administrador

### 📱 Stock de teléfonos

- **La columna de la izquierda** tiene una fila por modelo, con sus capacidades y cuántos hay de cada una. Al pulsar el
  nombre se abre el modelo entero; al pulsar una capacidad, solo esa.
- **El buscador de la columna** acepta un modelo («13 pro») o un **SKU**, entero o el principio. Debajo salen los SKU en
  stock que empiezan así; al elegir uno se abre su modelo con ese SKU.
- **Pestañas de capacidad** junto al nombre del modelo.
- **La tabla color × grado** sustituye a los chips: cuántos hay de cada color y grado, y en qué reparto (listos, taller
  y revisión). Pulsar una casilla filtra por ese color y grado; Ctrl+clic suma casillas.
- **«Quién lo tiene»:** el técnico que tiene el móvil (reparación, glass o pulido) y, en la glass, si ya le llegó. Avisa
  de dos cosas:
  - **«En caja, sin asignar»:** está en una caja de taller y nadie lo tiene;
  - **descuadre:** hay un trabajo abierto **de hace más de una hora** pero NSYS dice que está en Listos o de pedido.
    Falta cerrar el trabajo o mover la caja.
- Un filtro **«Técnico»**, con «Sin asignar» y «Con descuadre».
- **El grado tiene color** por familias (A, B, C, Ms, ID y n/a).
- **«Pegar varios IMEIs»:** pega una lista de hasta **500** IMEIs (de una columna de Excel, por ejemplo) y dice de cada
  IMEI si está en stock, de pedido, vendido o no está en NSYS, con quién lo tiene. El resultado se copia para Excel.
  Si la comprobación falla, lo dice y se puede reintentar.
- **La píldora de NSYS** se puede pulsar: enseña las últimas importaciones y avisa si falta algún Items.

### 🔔 Barra superior

- Si no llegan datos de NSYS, sale un aviso rojo **«NSYS sin datos»** en cualquier página. Lleva al stock de
  teléfonos.

### 🔎 IMEIs

- La ficha de NSYS dice **cuándo se revisó** el móvil o **desde cuándo espera** la revisión.
- Un móvil en **Funcionalidad pendiente** también sale como pendiente de revisar; su revisión es el paso a una caja.
- En el rastro, la revisión sale como **«Revisado → caja»**.
