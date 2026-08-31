# 🎉 Novedades — Versión 0.16.1

El viaje del glass entre plantas por fin queda registrado: quién lo tiene, cuándo llegó y a quién hay que dárselo — visible para los dos técnicos y para quien reparte. Y dos arreglos que se notan a diario.

---

## 📲 Entrega del glass, con hora y nombre

- El técnico de la reparación normal marca la entrega con **clic derecho → "Entregar a <técnico de glass>"** en Mis pendientes (junto a "Marcar por cerrar"). Un clic: queda registrado **quién** lo entregó y **a qué hora**.
- El técnico de glass ve en su asignación la píldora **"Llegó 10:42"** (si es de otro día, "Llegó 27/08"), con el detalle completo en el tooltip.
- El de arriba ve **"→ Jhona"** en su fila: ya se lo entregó. ¿Clic por error? **"Deshacer entrega"** y listo.
- El supertécnico ve ambas píldoras en **Asignaciones**, sin hacer nada.
- Si reasignan la glass, el nombre se actualiza solo; quién la entregó no cambia (es un hecho, no un estado).

## 🟢 "Glass: Jhona" — sabes a quién dárselo antes de moverte

- Mientras la entrega no esté registrada, la reparación normal lleva bajo el IMEI la **píldora verde "Glass: <técnico>"**: de un vistazo sabes de quién es la glass de ese teléfono, esté arriba o ya abajo.
- Al registrar la entrega, la verde desaparece y queda la "→ <técnico>". En Asignaciones, el "2 asignados" genérico deja de estorbar cuando la píldora ya cuenta quién es el segundo.

## 🚫 Sin teléfono no hay glass

- En la pestaña Glass, el botón **"Añadir glass" no aparece** mientras el teléfono siga arriba (reparación normal abierta y entrega sin registrar). En cuanto llega — o si la glass va sola, sin reparación normal — el botón está disponible.
- Así nadie "termina" un glass que aún no tiene, y de paso todos se acuerdan de marcar la entrega.

## 📜 El historial también lo cuenta

- Las glass completadas muestran en **Agrupado por IMEI** e **Historial**, bajo el reparador, **"Llegó 28/08 16:20"**: la referencia real de cuándo tuvo el teléfono en la mano (las fechas de la fila son las de completar).
- El **CSV de Asignaciones** estrena columna **"Entregado"** con la fecha y hora completas.
- Todo queda en el **log de actividad**: acciones `ENTREGAR_GLASS` y `DESHACER_ENTREGA_GLASS`, filtrables.

## 🔔 Alertas de stock sin fantasmas

- La campana **ya no cuenta los componentes desactivados**: no aparecen como "Sin Stock", no entran en "Pedir todas las piezas" y no encienden el aviso al arrancar.

## 💬 Mensajes de error que dicen la verdad

- Los avisos de reglas de negocio del servidor muestran **su mensaje real** (antes cualquier caso de este tipo salía como "Contraseña actual incorrecta."). Cambiar la contraseña sigue avisando con su texto de siempre, también contra servidores antiguos.

---

## 🚚 Notas de despliegue

- Requiere el **servidor actualizado** (ya desplegado en preproducción) y la migración `migracion-entrega-glass.sql` (**ya aplicada** el 2026-08-28).
- **Orden**: ALTER → servidor → cliente. Como siempre, retrocompatible: los clientes 0.16.0 conviven sin enterarse.
