# Novedades de la versión 0.9.22

---

## 📦 Stock de teléfonos (supertécnicos y administrador)

- **«Por pasar a NSYS» tiene ahora cuatro tarjetas**, cada una con su número y su botón:
  - **Cambio de color**: «⬇ Descargar plantilla» da el Excel listo para subir a NSYS (bórralos en NSYS y súbelos otra vez con
    la plantilla; lleva el grado que tienen hoy en NSYS y su mismo precio).
  - **Cambio de grado**: «Copiar lista» para tenerlos delante; en NSYS se cambian uno a uno, **después** del color.
  - **No está en NSYS**: móviles que han pasado por el taller y NSYS no conoce, con su plantilla de altas (rellena en el Excel lo
    que va en rojo).
  - **Revisar a mano**: lo que el programa no supo calcular al guardar una reparación. «Corregir…» abre el móvil; «✓ Revisado»
    si ya estaba bien.
- Los móviles vendidos o de baja en NSYS no salen en Cambio de color ni en Cambio de grado (no se vuelven a subir); si vuelven a
  la empresa, salen otra vez.
- **«Hecho»** en cada fila (y «✓ Marcar todo»): la fila se queda en gris y ya no va en la plantilla. Desaparece sola cuando NSYS
  lo tiene; si se marcó por error, vuelve a salir con un aviso.
- Si NSYS nunca ha escrito un color, el producto lleva **«compruébalo»** (en amarillo en la plantilla): míralo antes de subirlo.
- El producto que hay que poner en NSYS sale casi siempre, también para colores que ningún móvil de ese modelo y capacidad tiene
  todavía en NSYS.

## 🔧 Formulario de reparación

- Al guardar una reparación con chasis o tapa trasera, el programa deduce el color en más casos (colores que aún no tiene ningún
  móvil en NSYS). Menos avisos de «el color del chasis no se puede deducir».

---

*Para el taller no cambia nada. Para los supertécnicos, ya no hace falta la hoja «Flujo trabajo» ni rellenar a mano la plantilla
de NSYS.*
