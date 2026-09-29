# 🎉 Novedades — Versión 0.9.0 (web)

La web cuida mejor las sesiones en los puestos compartidos: se cierra sola tras dos horas sin usarla, avisa cuando hay una versión nueva y las contraseñas que entrega el administrador hay que cambiarlas al entrar.

---

## ⏱️ La sesión se cierra tras dos horas sin usar el programa

- Si nadie toca la web durante **dos horas**, la sesión se cierra y vuelve a pedir usuario y contraseña.
- **Un minuto antes** sale el aviso **"Vas a salir por inactividad"** con el botón **"Seguir trabajando"**: con pulsarlo, sigues donde estabas.
- Cuenta como uso **el ratón y el teclado**, en cualquier pestaña de la web. Volver a la ventana o que las tablas se refresquen solas **no** cuenta.
- Lo que tengas **sin guardar en un pedido o en el reparto de trabajos se pierde** al cerrarse la sesión. Las **reparaciones a medias se guardan solas**.
- Un PC que se suspende con la web abierta más de dos horas pedirá entrar de nuevo al despertar.

## 🔄 Aviso de versión nueva

- Cuando se publica una versión nueva, las pestañas abiertas lo avisan abajo: **"Hay una versión nueva del programa."**, con el botón **"Recargar"**.
- **Nunca recarga sola**: recargar pierde lo que tengas escrito, así que decides tú cuándo.

## 🔑 Contraseñas

- En **Técnicos**, el administrador tiene **"Restablecer contraseña"** en cada fila. El programa genera una **contraseña temporal** y la enseña **una sola vez**, con un botón para copiarla; después ya no se puede volver a ver.
- Quien entra con una contraseña temporal pasa a la pantalla **"Cambia tu contraseña"** y tiene que elegir una propia antes de seguir. Desde esa pantalla también se puede **cerrar sesión**.
- Los **usuarios nuevos** también entran con la contraseña del alta como temporal y la cambian al entrar.
- Si el administrador restablece la contraseña de alguien que tiene la web abierta, su siguiente acción le lleva a esa pantalla. Cambiarla en una pestaña vale para todas.
- Tras **cinco contraseñas incorrectas seguidas** en una cuenta, el programa pide esperar unos segundos antes de volver a intentarlo, más cuanto más se insiste (hasta un minuto). Acertar la contraseña lo reinicia.

## 📋 Registro de actividad

- Los inicios de sesión fallidos y los restablecimientos de contraseña quedan en el registro de actividad.
- Cada línea del registro guarda el nombre de quien la hizo, así que sigue siendo legible aunque ese usuario se borre después.
- Desactivar a un usuario le impide seguir trabajando en menos de un minuto, aunque tenga la web abierta.

---

## 🚚 Notas de despliegue

- Requiere el **servidor 0.9.0** y la migración aditiva `sql/migracion-sp7b-auditoria-password.sql`. La migración se aplica una sola vez por base de datos y **después** de cargar un volcado, nunca antes.
- La web cambia la configuración de nginx (`deploy/nginx/default.conf`, ruta `/version.json` sin caché): hay que copiarla a la máquina y reiniciar nginx.
