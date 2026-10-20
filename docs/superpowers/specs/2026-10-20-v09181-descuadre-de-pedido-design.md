# 0.9.18.1 — El descuadre no cuenta los móviles de pedido

Fecha: 2026-10-20. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Arreglo aparte de la **web**, sacado del tag `v0.9.18` (la 0.9.19 sigue abierta en `main`). **Solo web**: sin cambios en el
servidor ni en la base; funciona con el servidor 0.9.18.

## 1. Qué cambia

La 0.9.18 (spec `2026-10-19-v0918-stock-telefonos-quien-lo-tiene-design.md` §4.7) marca **descuadre** cuando hay un trabajo
abierto de hace más de 60 minutos y el móvil está en «Listos» **o es de pedido** (caja «Pedidos realizados»). Daba por hecho
que un móvil de pedido ya ha salido del taller.

**No es así (corrección del usuario, 20/10):**
- «Pedidos realizados» significa que el móvil **tiene dueño**: no se ofrece para otra venta.
- Del taller sale cuando NSYS pone **Released**.
- Repararlo estando en «Pedidos realizados» es lo normal. Así funciona el descuento de stock: los IMEIs que se van a trabajar
  se ponen en Simulador y en PEDIDOS 2026, y la automatización de NSYS los pasa a «Pedidos realizados».

Con la regla de la 0.9.18, cada móvil de pedido en reparación sale en rojo («⚠ Técnico 1: trabajo abierto»). Es una falsa
alarma.

## 2. Comportamiento

| Móvil | Trabajo abierto de hace más de 60 min | 0.9.18 | 0.9.18.1 |
|---|---|---|---|
| En stock, caja «Listos» | sí | ⚠ Técnico: trabajo abierto | igual |
| De pedido (última caja de taller) | sí | ⚠ Técnico: trabajo abierto | **Técnico** (como cualquier trabajo) |
| De pedido (última caja «Listos») | sí | ⚠ Técnico: trabajo abierto | **Técnico** |
| De pedido, sin trabajos | — | — | igual |

- **Descuadre = trabajo abierto de hace más de 60 minutos con el móvil en stock en «Listos».** Un móvil de pedido no da
  descuadre nunca, aunque la última caja que se le conoce sea «Listos».
- Un móvil de pedido con trabajo abierto se ve como cualquier otro: «Técnico 1», «Técnico 1 · glass Técnico 3 llegó 10:41»,
  «Técnico 2 (pulido)».
  Al pasar el ratón, el detalle de sus trabajos, sin la frase del descuadre.
- **«⚠ En caja, sin asignar» sigue sin salir en los de pedido** (como en la 0.9.18, ahora con el motivo bien escrito): al
  pasar a «Pedidos realizados» la última caja se congela. Un móvil de pedido ya reparado que espera el Released seguiría
  pareciendo «en caja de taller», así que no se puede saber si sigue en el taller.
- El texto del descuadre al pasar el ratón dice siempre «NSYS dice "Listos", pero hay un trabajo abierto. ¿Falta cerrarlo o
  mover la caja?».
- Todo lo que usa «Quién lo tiene» cambia a la vez, porque sale de la misma función: la celda de la tabla, el aviso
  «⚠ N con descuadre» junto a los filtros, la opción «Con descuadre» del filtro «Técnico», el orden de la tabla (descuadres
  primero), «Pegar varios IMEIs» y su copia para Excel.

## 3. Código

Solo `src/modules/telefonos/stock/quien.ts` (`quienLoTiene`):
- `fueraDelTaller` pasa a ser «en stock y en "Listos"»: `m.estado !== 'DE_PEDIDO' && m.ubicacion === 'Listos'` (la ubicación
  de un móvil de pedido es su última caja; por eso hace falta mirar el estado).
- Desaparece `caja` (la frase del descuadre ya solo puede decir «Listos»).
- Los comentarios de la cabecera y de `enCaja` explican la regla nueva y citan esta spec.

## 4. Pruebas

- `quien.test.ts`:
  - el caso «descuadre … Listos o de pedido» se queda solo con «Listos»;
  - nuevo: de pedido con trabajo de hace más de 60 minutos y última caja de taller → sin descuadre, texto «Técnico 1»,
    etiquetas solo con el técnico y detalle sin la frase del descuadre;
  - nuevo: de pedido con última caja «Listos» y trabajo de hace más de 60 minutos → sin descuadre;
  - el de «de pedido no está en la caja» se queda como está.
- El resto de la suite sin cambios (las pruebas de la página y de `modelo.ts` usan «Listos»).
- E2E: ninguna nueva. En preprod, la `stock-telefonos` en verde.

## 5. Versión y entrega

- Rama `hotfix/0.9.18.1` de la web desde el tag `v0.9.18` (`a501202`), en un worktree (`wt/web-09181`): el clon principal y
  los otros worktrees son de otras sesiones.
- `package.json` a `0.9.18.1` (npm la acepta: el paquete es privado). El aviso de versión nueva compara texto, así que las
  pestañas abiertas se enteran.
- CHANGELOG: entrada nueva `[0.9.18.1]`. La de la `[0.9.18]` se queda como se publicó.
- `docs/novedades/NOVEDADES-v0.9.18.md` (raíz): se corrige la línea del descuadre, porque el taller aún no la ha recibido.
- Preprod → producción en horario (solo web) → tag `v0.9.18.1` en la rama del arreglo →
  merge `--no-ff` a `main` de la web, para que la 0.9.19 lo lleve (conflictos esperados solo en `package.json` y el
  CHANGELOG: se quedan 0.9.19 y las dos entradas).
- Merge, push, tag y despliegues, solo con el OK del usuario.
