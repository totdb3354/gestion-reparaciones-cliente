# 🎉 Novedades — Versión 0.9.6 (web)

Stock te dice cuánto pedir para **dos meses**, y el pedido de esas piezas se prepara **con un botón**.

---

## 📦 Stock: «Pedir 60 d»

Para **supertécnicos y administrador**. Las columnas «Pedir 15 d» y «Pedir 30 d» se sustituyen por una sola,
**«Pedir 60 d»**: cuántas unidades conviene pedir para cubrir **60 días** de trabajo. La regla es la misma de antes:
cuenta lo que tienes y lo que ya está en camino, y nunca te deja por debajo del mínimo.

---

## ✅ Pedido automático

1. En **Stock**, en la columna **«Modo»**, pasa a **Auto** (en verde) las piezas que quieras pedir siempre por
   previsión; las demás quedan en **Manual** (en gris). Se cambia con un clic, se guarda al momento y lo ven igual el
   resto de supertécnicos y el administrador. En el filtro **Estado**, debajo de una línea, puedes ver solo las piezas
   en **Auto** o en **Manual** (y combinarlo, p. ej. **Bajo** + **Auto**).
2. En **«Nuevo pedido»**, pulsa **«Añadir previsión (N)»**: se añaden las piezas marcadas con la cantidad de
   «Pedir 60 d». **N** es cuántas necesitan pedido; las que no, no se añaden y se avisa.
3. El **Proveedor** de arriba es una ayuda, no es obligatorio. Al elegirlo se pone en todas las líneas que aún no
   tienen proveedor (las que ya tienen uno no cambian) y lo llevan también las líneas que añadas después. Para ponerlo
   en **todas** las líneas, también en las que ya tenían otro, pulsa **«Aplicar a todas»**. La ventana de pedido es más
   grande: caben más líneas a la vista.
4. El **precio** puede quedarse en 0 y ponerse después con **«Editar pedido»**.

Si una pieza marcada ya estaba en el pedido, no se repite: se sube su cantidad a la de la previsión si es mayor.

---

## 🔢 Pedidos: número de pedido

En **Pedidos** (Componentes y Otros) hay una columna estrecha **ID** a la izquierda con el número de cada pedido. También
sale al principio del CSV, para poder citarlo o buscarlo.
