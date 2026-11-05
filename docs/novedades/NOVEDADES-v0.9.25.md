# Novedades de la versión 0.9.25

---

## 📱 Stock de teléfonos (supertécnicos y administrador)

- Pestaña nueva **«Qué revisar»**, al lado de «Por pasar a NSYS». Hace lo que hacía la hoja «PriorizarRevision», pero con el
  stock al momento y con los IMEI de cada móvil.
- **Revisar sin grado**: los modelos-color en los que todo está sin grado (`n/a`), como la tabla «Solo n/a» de la hoja. Primero
  **lo que más queda sin revisar**. En cuanto NSYS o el taller le ponen grado a uno de sus móviles, el modelo-color sale de la
  lista; F, FD y RMA no cuentan como grado.
- **Subir B a A**: los modelos-color con B o B100 y ningún A (como la hoja): candidatos a cambiarles el chasis (B pasa a A y
  B100 a A100). En cuanto el modelo-color tiene un A, sale de la lista.
- Pulsa una fila para ver sus móviles: dónde están, cuántos días llevan y quién los tiene. Un móvil que ya tiene un técnico
  sale en gris y no cuenta ni va en el Excel.
- La caja de arriba, en esta pestaña, **busca por palabras**: el modelo («13 pro 256», «14 esim»), un color, un SKU o un trozo
  del IMEI (6 cifras o más: abre su fila con el móvil en amarillo). Deja solo lo que coincide.
- **«⬇ Descargar Excel»** baja lo que ves, una fila por modelo-color como las tablas de la hoja, listo para imprimir
  (apaisado y a una página de ancho). En «Revisar sin grado» lleva «Sin grado» y «Revisado»; en «Subir B a A», «B», «B100» y
  «Hecho». La última columna va vacía para marcar a boli.
- Un móvil que acabas de revisar sigue saliendo hasta que su grado llega con el siguiente Items de NSYS (la hora está en la
  píldora NSYS de arriba).
