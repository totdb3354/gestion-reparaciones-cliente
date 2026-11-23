# Pedidos: los pedidos web salen en 24 h laborables — diseño

Fecha: 2026-11-23. Estado: aprobado en conversación, pendiente de revisar este texto.
Versión: **0.9.44** (servidor + web).
Base: la fecha de salida de `2026-11-18-pedidos-buscar-y-salida-design.md` §2-§3.

## Para qué

Hoy un pedido web tiene de límite el día de entrada más 2 días laborables (envío 24/48 h) o más 3 (cualquier otro), y la
tabla dice «Sale mié 25/11» hasta ese día. En la práctica los pedidos web se sacan siempre en el día laborable: logística
quiere verlos como **«Sale hoy»** desde el primer día, y que pasen a **«Con retraso»** cuando se pasa el plazo de 24 h
laborables. Un pedido que entra en fin de semana o en festivo se cuenta desde el primer laborable y nunca sale «Con retraso»
el fin de semana.

## La regla (solo webs; Amazon no cambia)

- **Desde** = el día de entrada si es laborable; si no, el primer laborable siguiente.
- **Límite** = el laborable siguiente a «desde». Es la fecha de salida que se guarda (`FECHA_SALIDA`).
- No se mira el servicio de envío (24/48 o 24/72): todos los pedidos web igual.
- No hay hora de corte: un pedido de las 21:00 tiene todo el laborable siguiente.
- Laborable = de lunes a viernes y no festivo (`Calendario_festivo`), como hoy.

| Entra | Sale hoy | Con retraso |
|---|---|---|
| Lunes (a cualquier hora) | lunes y martes | miércoles |
| Viernes | viernes y lunes | martes |
| Sábado o domingo | lunes y martes | miércoles |
| Lunes festivo | martes y miércoles | jueves |

**Pantalla** (columna «Enviado» de una fila pendiente o con «Falta por enviar»):
- hoy antes de «desde»: se enseña «desde»: «Sale mañana» o «Sale lun 30/11»;
- hoy entre «desde» y «límite» (ambos incluidos) y en día de semana: **«Sale hoy»**;
- hoy entre «desde» y «límite» pero en sábado o domingo (pedido del viernes visto el sábado): «Sale lun 30/11» (el lunes
  siguiente), no «Sale hoy»;
- hoy después del límite: **«Con retraso»**.

La web no conoce los festivos: un festivo entre semana dentro del plazo saldría «Sale hoy». Se acepta (ese día nadie trabaja
y el límite, que sí los cuenta, no cambia).

Amazon: igual que hoy (límite = «Latest ship»; «Sale hoy» solo el día del límite).

## Servidor

1. `CalendarioLaboral`: `primerLaborable(LocalDate d, festivos)` → `d` si es laborable; si no, el siguiente laborable.
2. `FechaSalida`:
   - webs: `de(...)` devuelve `sumarLaborables(primerLaborable(entrada), 1, festivos)`. Fuera `DIAS_48`, `DIAS_72` y la
     lectura del servicio.
   - nuevo `desde(Canal canal, LocalDate entrada, festivos)`: webs → `primerLaborable(entrada)`; Amazon u otro → `null`.
   - Javadoc al día.
3. `FilaVista`: campo nuevo **`salidaDesde`** (`LocalDate`, nullable), al final, después de `pais`. Se calcula al servir en
   `VistaFilas` con la fecha de entrada de la fila (`fecha`) y los festivos (`CalendarioLaboral.festivos()`, inyectado en
   `VistaFilas`); nulo en Amazon, en el histórico y en las filas sin fecha de salida. Javadoc de `fechaSalida` al día
   («webs: el laborable siguiente al primer laborable desde la entrada»).
4. No se guarda: **sin migración**. La lectura de ShippyPro recalcula `FECHA_SALIDA` de las filas vivas en cada vuelta
   (cada 5 min), así que tras desplegar las filas web pendientes pasan solas al límite nuevo.
5. El Listado (`BuscarController.partir`) no cambia: solo parte por fecha las pestañas de Amazon.
6. OpenAPI: el campo nuevo sale en el contrato (`OpenApiContractTest`); `docs/api_contract.md` al día.

## Web

1. `salida.ts` `nivelSalida(f, hoy)`: con `f.salidaDesde` no nulo:
   - `hoy > fechaSalida` → `'RETRASO'`;
   - `salidaDesde <= hoy <= fechaSalida` y `hoy` de lunes a viernes → `'HOY'`;
   - `salidaDesde <= hoy <= fechaSalida` en sábado o domingo → el nivel y el texto se calculan con el lunes siguiente;
   - `hoy < salidaDesde` → el nivel y el texto se calculan con `salidaDesde` («Sale mañana» o «Sale lun 30/11»).
   Sin `salidaDesde` (Amazon), como hoy.
2. `textoSalida` usa la misma fecha que `nivelSalida` para «Sale dd/mm».
3. Lo demás sigue solo porque usa `nivelSalida`: contadores «Sale hoy» y «Con retraso», filtro de la columna «Enviado», aviso
   amarillo de pendientes fuera del periodo, texto de «Falta por enviar», CSV.
4. Tipos del cliente regenerados con el campo nuevo.
5. Ayuda de Pedidos (`AyudaPedidos.tsx`): la regla de las webs.

## Compatibilidad y despliegue

- Web vieja con servidor nuevo: ignora `salidaDesde`; ve el límite nuevo (un día antes que el de hoy) y dice «Sale mañana» /
  «Sale hoy» por él. Sin errores.
- Web nueva con servidor viejo: `salidaDesde` no llega (nulo) → se comporta como hoy.
- Sin migración, corte despreciable: despliegue en horario. Servidor primero, después la web.

## Pruebas

- **Servidor:** `CalendarioLaboralTest` (`primerLaborable`: laborable, sábado, domingo, festivo, festivo + fin de semana);
  `FechaSalidaTest` (los cuatro casos de la tabla y víspera de festivo; el servicio 24/72 da lo mismo que 24/48; Amazon sin
  cambios; `desde` nulo en Amazon); `VistaFilas`: `salidaDesde` en una fila web y nulo en Amazon e histórico;
  `OpenApiContractTest`.
- **Web:** `salida.test.ts`: pedido del sábado visto el sábado «Sale lun», el domingo «Sale mañana»; pedido del viernes
  visto el sábado «Sale lun» (no «Sale hoy»); los dos días «Sale hoy»; después «Con retraso»; Amazon igual que antes; fila
  sin `salidaDesde` igual que antes; contadores con una fila web en su primer y segundo día.
- **E2E en preprod** (datos sintéticos por API y limpieza): una fila web pendiente sale «Sale hoy» y cuenta en el contador.

## Documentación

`docs/modulos/pedidos.md` §4 (fecha de salida de las webs) y §3 (la tabla de estados de «Enviado» no cambia de textos).
