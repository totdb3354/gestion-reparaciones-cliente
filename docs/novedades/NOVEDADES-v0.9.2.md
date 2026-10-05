# 🎉 Novedades — Versión 0.9.2 (web)

Las contraseñas ahora se eligen con ayuda: una barra te dice si la que escribes es lo bastante segura.

---

## 🔑 Contraseñas

- La **próxima vez que entres** el programa te pedirá una **contraseña nueva**. Es normal: se hace una sola vez.
- Al escribirla verás una **barra de seguridad** (Muy débil, Débil, Poco segura, Segura o Muy segura). El botón **"Guardar"** se activa cuando la contraseña es **suficientemente segura**; si no lo es, debajo te explica qué le falta.
- Una **frase de tres o cuatro palabras sueltas** (sin relación entre ellas) funciona muy bien y es fácil de recordar. No hace falta poner mayúsculas, números ni símbolos.
- Tiene que tener **al menos 10 caracteres** y ser **distinta de la que ya tenías**.
- El **administrador** necesita llegar a **«Muy segura»**.
- Cuando se da de alta a un **técnico nuevo**, ya no se escribe su contraseña: el programa **genera una temporal** y la enseña **una sola vez**. Hay que **dársela en persona**, y al entrar por primera vez tendrá que elegir una propia.

---

## 🚚 Notas de despliegue

- Se despliegan juntos el **servidor 0.9.2** y la **web 0.9.2**, **primero el servidor**.
- Hay **una variable de entorno nueva** en el compose de la máquina, para las palabras propias de la empresa que no se aceptan en una contraseña. Es una lista separada por comas, y su valor va solo en la máquina, no en los repositorios.
- **No hay migración de base de datos.**
- Para volver atrás basta con volver a la **0.9.1**, sin tocar la base de datos.
