# 0.9.18.2 — Un móvil vendido o dado de baja no está «En revisión»

Fecha: 2026-10-20. Estado: diseño aprobado en el brainstorming, pendiente del plan.
Arreglo aparte de la **web**, sobre la 0.9.18.1 (rama desde el tag `v0.9.18.1`). **Solo web**: sin cambios en el servidor ni
en la base; funciona con el servidor 0.9.18.

## 1. Qué pasa

Cuando NSYS saca un móvil (lo **vende**, Released, o lo **borra**, Removed: por ejemplo, para volver a importarlo con el color o
la capacidad corregidos), le **vacía la caja**. El ERP traduce «sin caja» como «En revisión», que es verdad para un móvil que
sigue en la empresa (sin caja = por revisar), pero no para uno que ha salido.

Hoy, con un móvil vendido:
- la ficha del IMEI enseña la píldora **«En revisión»** y **«Pendiente de revisar desde hoy»** junto a «Vendido el …»;
- «Pegar varios IMEIs» pone «En revisión» en la columna «Ubicación» (y en la copia para Excel);
- el rastro dice **«Pasa a En revisión»** (en ámbar) en la misma exportación que «Vendido (Released)» o que «Estado en NSYS:
  Removed».

El stock no se ve afectado: el servidor solo manda en el stock los móviles en la empresa (STOCK, DE_PEDIDO y NO_CUENTA). El
buscador de IMEI ya trata bien los vendidos y las bajas («Vendido el …», «Baja en NSYS», sin caja).

## 2. Comportamiento

**Fuera de la empresa** = estado `VENDIDO` o `BAJA`.

- **Ficha del IMEI:** si está fuera de la empresa, no sale la píldora de la caja ni «Pendiente de revisar…». «Revisado el …»
  sí sale si el lote se revisó (es historia del móvil).
- **«Pegar varios IMEIs»:** si está fuera de la empresa, la «Ubicación» va vacía, en la tabla y en la copia para Excel.
- **Rastro:** un cambio de caja **a «En revisión»** que llega en la **misma exportación** (misma hora) y del **mismo lote** que
  un «Vendido (Released)» o que un **estado de NSYS que no es de stock** (cualquiera fuera de Available, InProcess y Repair:
  Removed…) se rotula **«Sale de {caja de antes}»** (p. ej. «Sale de Pedidos realizados»), en gris y sin «Desde: …».
  - Los demás pasos a «En revisión» siguen como hoy («Pasa a En revisión», ámbar).
  - Los eventos de la venta y del estado no cambian.

## 3. Código (solo web)

- `src/shared/lib/telefonoNsys.ts`: `fueraDeLaEmpresa(estado)` (VENDIDO o BAJA).
- `src/modules/taller/imeis/FichaMovil.tsx`: sin píldora de caja ni «pendiente» si está fuera de la empresa.
- `src/modules/telefonos/stock/varios.ts`: `ubicacionDe(m)` (null si está fuera de la empresa), que usan
  `ComprobarImeis.tsx` y `paraExcel`.
- `src/modules/taller/imeis/rastro.ts`: `vaciaLaCajaAlSalir(e, eventos)` y `verEvento(e, alSalir)`; `PanelRastro.tsx` le pasa
  la marca.

## 4. Pruebas

- Unitarias: `fueraDeLaEmpresa`; `ubicacionDe` y `paraExcel` con un vendido; `vaciaLaCajaAlSalir` (venta, baja, y los casos
  que no: estado de stock, otro lote, otra hora, destino que no es «En revisión») y el texto «Sale de …».
- Componentes: la ficha de un vendido y de una baja (sin caja ni «pendiente», con «Revisado el …» si lo hay); «Pegar varios
  IMEIs» con un vendido (sin ubicación); el panel del rastro con venta + «sin caja» en la misma exportación.
- E2E: ninguna nueva. En preprod, la `stock-telefonos` en verde, y la ficha y el rastro de un IMEI vendido con un vistazo.

## 5. Versión y entrega

- Rama `hotfix/0.9.18.2` de la web desde el tag `v0.9.18.1` (`27637b5`), en el worktree `wt/web-09181`.
- `package.json` a `0.9.18.2` con `npm pkg set` y las dos entradas raíz de `package-lock.json` a mano (nunca `npm version`).
- CHANGELOG: entrada nueva `[0.9.18.2]`.
- `docs/novedades/NOVEDADES-v0.9.18.md` (raíz): una línea en el apartado de IMEIs.
- Preprod (sin `pull`, y vuelta a como estaba) → producción (commit suelto, como la 0.9.18.1) → tag `v0.9.18.2` → **un solo
  merge** `--no-ff` de `hotfix/0.9.18.2` (que lleva la 0.9.18.1) a `main` de la web.
- Merge, push, tag y despliegues, solo con el OK del usuario.
