# 🎉 Novedades — Versión 0.9.21

El ERP ya sabe lo que el taller cambia en un móvil (color, grado y batería nueva) aunque NSYS todavía no lo diga, y lleva la
lista de lo que hay que cambiar en NSYS.

---

## 🧰 Para todos

- El técnico ve el programa como hasta ahora.

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

> ⚠️ El ERP no cambia nada en NSYS: la lista es lo que hay que pasar a mano. Lo automático desde las reparaciones (chasis de
> otro color, batería nueva…) llega en la siguiente pieza.
