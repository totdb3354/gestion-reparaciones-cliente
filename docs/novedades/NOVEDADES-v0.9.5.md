# 🎉 Novedades — Versión 0.9.5 (web)

Stock te dice **cuánto pedir** de cada pieza, y el stock mínimo pasa a ser cosa del administrador.

---

## 📦 Stock: cuánto pedir

Para **supertécnicos y administrador**. En la pantalla de Stock aparecen tres columnas nuevas (también en el CSV):

- **Consumo/día**: lo que se gasta de media al día de esa pieza. Cuenta lo que se ha usado en reparaciones en los últimos 90 días, pero **lo reciente pesa más**: los últimos 30 días cuentan un 50 %, los 30 anteriores un 30 % y los 30 anteriores a esos un 20 %.
- **Pedir 15 d** y **Pedir 30 d**: cuántas unidades conviene pedir para cubrir **15 o 30 días** de trabajo.

Ejemplo: una batería con un consumo de **0,31 al día**, **3 en stock** y un **mínimo de 2** sale con **Pedir 15 d = 2** y **Pedir 30 d = 7**.

- Un **0** sale en **gris**: no hace falta pedir nada.
- Lo que **ya está en camino** se cuenta como si lo tuvieras.
- **Nunca te deja por debajo del mínimo**: lo que sale en Pedir, más lo que tienes y lo que está en camino, llega como poco al mínimo. Aunque una pieza casi no se gaste, si estás por debajo del mínimo te pide lo que falta.
- Las piezas que **ya no se trabajan** hay que **desactivarlas**; entonces salen con **"—"** y no hacen ruido.
- Una pieza con **poco historial** (menos de tres meses de uso en el programa) sale con una cifra **más baja de la real** hasta que acumule historial. Para eso está el mínimo: es el suelo que la cubre mientras tanto.

---

## 🔗 Piezas que comparten stock, en una sola fila

Algunas piezas sirven para dos modelos y comparten el mismo stock (por ejemplo, la cámara del 13 y la del 13 Pro, o la
batería del 12 y la del 12 Pro).

- En **Stock** salen **en una sola fila**, con el nombre de las dos (`cami13 / cami13pro`) y debajo, en gris,
  **"stock compartido"**. Así los números (stock, en camino, Pedir) salen **una sola vez** y no se pide dos veces.
- El **buscador** encuentra la fila por cualquiera de los dos nombres.
- **"Solicitar pieza"** en esa fila te pide **elegir el modelo**, para que quede apuntado cuál hace falta.
- En **"Nuevo pedido"** la pieza sale **una sola vez**, con los dos nombres.
- En el formulario de **reparación** no cambia nada: cada modelo sigue saliendo con su nombre.

---

## 🛠️ Solo administrador

- El **stock mínimo** solo lo cambia el administrador: **clic derecho → "Ajustar mínimo"**. Los supertécnicos ya no tienen esa opción, y **"Editar stock" no toca el mínimo**.
- El botón **"Parámetros de previsión"**, en Stock, permite cambiar los **tres pesos** del cálculo. Tienen que **sumar 100**; si no suman, el botón Guardar no se activa. Los valores de partida son **50 / 30 / 20**.

---

## 🔐 Sesión y contraseñas

- Si la sesión se cierra por **dos horas sin uso**, al volver al inicio de sesión lo dice: **"Se cerró la sesión tras dos horas sin uso. Inicia sesión de nuevo."**
- En **"Cambiar contraseña"** y **"Cambia tu contraseña"**, si pulsas **Enter justo después de escribirla**, ya se guarda. Si la contraseña no vale, el programa te explica por qué.

---

## 🚚 Notas de despliegue

- Primero hay que ejecutar la **migración de base de datos** `migracion-parametros-prevision.sql` (crea la tabla `Parametro`), **antes** de actualizar el servidor.
- Se despliegan juntos el **servidor 0.9.5** y la **web 0.9.5**, **primero el servidor**.
- **No hay cambios en la configuración de nginx.**
