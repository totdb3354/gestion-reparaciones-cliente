# 🎉 Novedades — Versión 0.9.21

El ERP ya sabe lo que el taller cambia en un móvil (color, grado y batería nueva) —también solo, al guardar una reparación— aunque NSYS todavía no lo diga, y lleva la
lista de lo que hay que cambiar en NSYS.

---

## 🔧 Formulario de reparación

- Al guardar una reparación con **chasis**, **tapa trasera** o **batería** de un móvil de NSYS, el programa **cambia solo el
  color y el grado** del móvil:
  - chasis o tapa (nuevos o reutilizados) → el color de la pieza y grado **A** (se conservan «Ms» y «100»);
  - batería nueva → el grado lleva «100»; batería reutilizada → se le quita.
- El técnico no tiene que hacer nada más: el formulario no cambia. El supertécnico lo revisa en «Por pasar a NSYS» antes de
  cambiarlo en NSYS, y corrige a mano lo que no se pudo calcular.
- Editar o borrar una reparación deshace lo que había cambiado (si nadie lo ha tocado después).

---

## 📋 Listas del taller (todos los roles)

- Donde ponía solo el modelo, ahora pone el **móvil entero**: **«iPhone 13 128GB Azul»** (la capacidad y el color de NSYS, con
  el color que haya puesto el taller). En Asignaciones, Pendientes, Pulidos pendientes, el Historial, IMEIs y el detalle del
  IMEI, y también al copiar la celda.
- Los CSV que llevan «Modelo» traen al lado **«Capacidad»** y **«Color»**.
- Un móvil que no está en NSYS (de un cliente) sigue con el modelo solo. En el historial sale el color de hoy.

---

## 👀 Supertécnicos y administrador

### 📱 Stock de teléfonos

- **Color, grado y SKU son los que valen:** si el taller ha cambiado algo, manda lo del taller. Esas celdas llevan la marca
  **«taller»** y, debajo, lo que dice NSYS («NSYS: Azul»). Los filtros y la tabla color × grado cuentan lo que vale.
- **Pestaña «Por pasar a NSYS (N)»:** lo que hay que cambiar a mano en NSYS, móvil a móvil: lo que dice hoy, lo que hay que
  poner (producto y grado), el SKU nuevo, quién lo cambió y por qué.
  - **Se vacía sola:** cuando la importación (cada 30 min) ve NSYS igual, la fila desaparece. No hay botón «Ya lo pasé».
  - Si después alguien pone en NSYS **otra cosa**, manda NSYS.
  - **«Exportar a Excel»** copia la lista para pegarla en una hoja.
- «Pegar varios IMEIs» y el buscador de IMEI también enseñan lo que vale, con la marca.
- En «Por pasar a NSYS» también salen los cambios de las reparaciones («Color: reparación R… · quién · pieza»).

### 📊 Carga de técnicos

- El panel flotante es **un poco más grande**: más ancho, con más sitio para el nombre del técnico, la letra algo mayor y
  la lista más alta. Si tapa algo, se minimiza como antes.

### 🔎 IMEIs

- **Correcciones de NSYS:** cuando en NSYS se corrige un móvil dándolo de baja y de alta en otro lote (por ejemplo, tras
  cambiarle el chasis de color en el taller), el ERP lo trata como el mismo móvil:
  - en el rastro sale una sola línea, **«Corrección en NSYS (lote … → …)»**, con el móvil antes y después;
  - la ficha y la tabla del stock cuentan los días desde que entró el primer lote, y la ficha cuenta solo las entradas de
    verdad y conserva la revisión, la espera y la última caja de antes de la corrección.
- **«Corregir color, grado o batería…»** en la ficha de NSYS del móvil:
  - el color (los del maestro para ese modelo), el grado (A, A-, B o C), «Batería nueva (100)» y «Mensaje de pieza (Ms)»;
  - el **motivo es obligatorio**;
  - antes de guardar se ve el SKU nuevo (o «sin SKU en el maestro», que se guarda igual) y lo que habrá que poner en NSYS;
  - **«Volver a lo de NSYS»** quita lo del taller.
- La ficha dice **«Por pasar a NSYS: …»** mientras NSYS no lo tenga.
- El **rastro** cuenta cada cambio del taller, cuándo pasó a NSYS y cuándo mandó NSYS.
- El rastro dice qué reparación cambió el color o el grado, cuándo se deshizo y lo que hay que corregir a mano.

> ⚠️ El ERP no cambia nada en NSYS: la lista es lo que hay que pasar a mano.
