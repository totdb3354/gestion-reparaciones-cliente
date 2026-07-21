# F2b — Revisión: spec de detalle

Fecha: 2026-07-21
Estado: pendiente de review del usuario (brainstorm completo, todas las decisiones validadas con bocetos).
Origen: sub-fase F2b de la spec canónica de F2 (`2026-07-07-fase2-lotes-telefonos-design.md`, §2.3, §4, §5). Esta spec la detalla; en lo que no diga nada, manda la canónica.
Diagrama del ciclo (validado): `assets/2026-07-21-f2b-ciclo-estados.html` (commit 4bbe674).

---

## 1. Alcance

Dentro (F2b): tabla `Revision` + ficha de revisión (2 partes) + veredicto + acciones (a revisar masivo, OK, bloquear/desbloquear, desguace) + panel Revisión en la pestaña Inventario + estados efectivos derivados REVISADO/REPARADO.

Fuera (F2c o después): enviar, devolución, vista de lotes, trazabilidad visible (`Movimiento_telefono`), retirar el check `REVISION_LOGISTICA` antiguo (convive mientras tanto), UI del histórico de revisiones, sugerencia automática de reparación. La pestaña IMEIs/Taller y el importador no se tocan.

## 2. Ciclo de estados (decisión central del brainstorm)

Se revisa **antes y después** de reparar; el OK se da cuando la reparación quedó bien.

```
RECIBIDO --escaneo--> EN_REVISION --2 partes--> [REVISADO] --asignar--> [EN REPARACIÓN]
                                                    |                        |
                                                    | limpio            se cierra el último trabajo
                                                    v                        v
                                                    OK  <---OK manual--- [REPARADO]
```

- **Guardados en BD** (enum de F2a, sin ALTER): RECIBIDO, EN_REVISION, BLOQUEADO, OK, ENVIADO, DESGUACE. Los fija una acción.
- **Derivados** (los calcula la app; entre corchetes arriba) sobre `ESTADO = EN_REVISION`:
  - **EN REPARACIÓN** = tiene trabajo abierto (ya existía en F2a). Tiene precedencia sobre los otros dos.
  - **REPARADO** = revisión vigente con **las dos partes** guardadas + sin trabajos abiertos + existe trabajo cerrado con fecha ≥ `FECHA_CREACION` de la vigente. Esperando el OK humano.
  - **REVISADO** = las dos partes guardadas + sin trabajos abiertos + ningún trabajo cerrado desde la vigente. Esperando decisión.
  - En otro caso: **por revisar** (ficha vacía o a una parte).
- **Regla del OK** (= la del check de revisión actual): se pone a mano, se quita solo. Si a un teléfono `OK` se le crea cualquier trabajo, el servidor lo devuelve a `EN_REVISION` en la misma operación (misma pasada de revisión, sin fila nueva) → derivará EN REPARACIÓN → REPARADO → OK manual otra vez.
- La reaparición en la cola es automática: el REPARADO vuelve solo, sin re-escanear.
- Ubicación derivada: sin cambios de F2a (EN_REVISION → Para revisar, etc.); los derivados no introducen ubicaciones nuevas.

## 3. Tabla `Revision` (la única migración de F2b)

1:N con Telefono; **una fila por pasada** de revisión; la **vigente = la de mayor `ID_REVISION`** por IMEI (sin flag). La fila se crea **vacía al pasar a EN_REVISION**; su `FECHA_CREACION` es el "en revisión desde" de la cola y el ancla de REPARADO.

```sql
CREATE TABLE Revision (
    ID_REVISION      INT          NOT NULL AUTO_INCREMENT,
    IMEI             VARCHAR(15)  NOT NULL,
    FECHA_CREACION   DATETIME     NOT NULL,
    -- Parte estética
    EST_GRADO        ENUM('C','B','A-','A') NULL,
    EST_PANT         ENUM('P','G') NULL,          -- previsión pantalla; NULL = ninguna
    EST_ID_USU       INT          NULL,
    EST_FECHA        DATETIME     NULL,
    -- Parte funcional (check marcado = defecto)
    FUN_BATERIA_PCT  TINYINT UNSIGNED NULL,
    FUN_PANT_TACTIL  BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_PANT_QUEMADA BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_PANT_MAL     BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_CAM_MANCHA   BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_CAM_LENTE    BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_ALT_SUP      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_ALT_INF      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MIC          BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_FACE_ID      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MS           BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MS_TEXTO     VARCHAR(100) NULL,
    FUN_BLOQUEO_OP   BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_OBSERVACION  VARCHAR(500) NULL,
    FUN_ID_USU       INT          NULL,
    FUN_FECHA        DATETIME     NULL,
    UPDATED_AT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REVISION),
    KEY idx_revision_imei (IMEI, ID_REVISION),
    CONSTRAINT fk_revision_telefono FOREIGN KEY (IMEI) REFERENCES Telefono (IMEI),
    CONSTRAINT fk_revision_usu_est  FOREIGN KEY (EST_ID_USU) REFERENCES Usuario (ID_USU),
    CONSTRAINT fk_revision_usu_fun  FOREIGN KEY (FUN_ID_USU) REFERENCES Usuario (ID_USU)
);
```

Script: `sql/migracion-f2b-revision.sql` + sync en `crear_bd.sql`. La aplica el usuario en la VM con vista previa. "Parte guardada" ≡ su `*_FECHA IS NOT NULL`.

### Reglas de escritura
- Guardar una parte edita la **vigente** y sella `*_ID_USU` + `*_FECHA` de esa parte (re-guardar re-sella: último autor). Guardado parcial es lo normal — ninguna parte exige a la otra ni campos completos.
- Guardar estética **espeja `EST_GRADO` → `Telefono.GRADO_PROPIO`** (el inventario sigue enseñando la verdad vigente; "Editar atributos" sigue funcionando y no toca la Revision).
- El cliente del teléfono (`ID_CLI`) se edita desde la ficha con el mecanismo existente de atributos; no vive en Revision.
- Fila nueva SOLO al pasar a EN_REVISION (desde RECIBIDO u OK vía escaneo). El resto de transiciones no crean fila. Histórico: solo BD, sin UI (decisión A).

## 4. Acción "A revisar" (escaneo masivo)

Diálogo desde el panel Revisión: campo escáner (Enter añade y limpia), lista de resultados línea a línea, contador. Reglas por estado del IMEI escaneado (validadas contra el flujo físico §1.3 de la canónica):

| Estado actual | Resultado | Nota |
|---|---|---|
| RECIBIDO | → EN_REVISION (crea fila Revision) | caso normal |
| OK | → EN_REVISION con aviso "estaba OK" (crea fila: re-revisión) | re-comprobación legítima |
| EN_REVISION | sin cambio, aviso "ya estaba" | duplicado inofensivo |
| con trabajo abierto | rechazado, aviso | está en reparaciones; vuelve solo al acabar |
| BLOQUEADO | rechazado, aviso "usar Desbloquear" | salir de bloqueo es acción propia |
| ENVIADO / DESGUACE | rechazado, aviso | la vuelta post-envío es la devolución (F2c) |
| histórico (sin lote, ESTADO NULL) | rechazado, aviso "dar de alta en un lote" | fuera del ciclo (decisión 15) |
| no existe | rechazado, error | no se crea nada |

## 5. Ficha de revisión (layout A aprobado)

Una ventana, dos columnas (estética | funcional), cada una con su **Guardar alineado al fondo** y su chip de estado ("guardada · autor · fecha" / "pendiente"). Cabecera: IMEI, modelo (+eSIM), storage, color, grado proveedor, lote (batch + supplier), estado. Pie: selector de cliente + acciones.

- **Estética**: grado propio (C/B/A-/A) + PANT (—/P/G).
- **Funcional**: batería % (número) + checks (marcado = defecto): pantalla táctil/quemada/mal, cámara mancha/lente, alt. sup/alt. inf/micro, Face ID, MS + texto, bloqueo operador + observación libre.
- Se abre: desde el **escáner de la cola** (Enter con el IMEI) o **doble clic/fila de la cola**; segundo acceso de consulta: menú contextual "Ficha de revisión" en el inventario. Al cerrarla, el foco vuelve al escáner (encadenar sin ratón).
- Abierta sobre un teléfono no EN_REVISION: solo lectura (consulta de su última revisión si la hay).
- "Marcar OK" apagado mientras la revisión vigente no esté **completa (las dos partes)** — igual que exige el servidor (§7). Si la funcional salió limpia pero falta la estética, el banner lo dice ("limpio — falta estética para el OK").

### Veredicto (al guardar la funcional)

El PATCH devuelve el veredicto evaluado y la ficha lo enseña como banner:

1. **Bloqueo operador marcado** → el servidor pasa el teléfono a BLOQUEADO en el mismo guardado (el cliente pide confirmación antes de enviar). La ficha queda guardada para el desbloqueo.
2. **Batería < 85** → "reparación obligatoria": veto **duro y en servidor** — OK imposible mientras la funcional vigente tenga batería < 85 o NULL. Se levanta únicamente re-midiendo tras el cambio de batería y re-guardando el %.
3. **Defectos** (algún check funcional y/o PANT con valor) → "necesita trabajos" con botón **Asignar trabajos…** que abre el modal de asignación existente precargado: IMEI puesto, pulido si PANT=P, glass si PANT=G, reparación normal si hay defecto funcional. La persona decide en el modal, como siempre.
4. **Limpio** → "candidato a OK", botón verde habilitado. El OK jamás es automático (decisión 8).

Si coinciden batería < 85 y otros defectos, el banner combina ambos (asignar cubre también la batería). Al reabrir la ficha de un teléfono con funcional guardada, el banner se recalcula y se vuelve a mostrar (no es solo un mensaje post-guardado). **Desguace** siempre disponible en la ficha, sin banner que lo sugiera (inservible = decisión humana), con **motivo obligatorio**; también es la salida del BLOQUEADO imposible. **Desbloquear** vive en la ficha del bloqueado y devuelve a EN_REVISION (la derivación hace el resto).

## 6. Panel Revisión (cola)

Tercer botón en la navegación de la pestaña Inventario (Inventario · **Revisión** · Suppliers), calco del patrón de paneles existente. Contenido:

- **Escáner arriba**, siempre a mano (Enter → abre ficha; IMEI no válido para revisión → aviso con la misma tabla de reglas del §4). Botón "A revisar (masivo)".
- **Cola** = teléfonos EN_REVISION: IMEI, modelo, lote, chips de Estética/Funcional (✓ autor / pend.), Situación (por revisar / REVISADO — esperando decisión / en reparación (atenuado) / REPARADO — esperando OK), "en revisión desde" (= `FECHA_CREACION` vigente). Doble clic → ficha. Los "en reparación" se atenúan y reaparecen solos como REPARADO.
- La columna Estado del **inventario** también enseña los derivados nuevos (extensión de la derivación existente en `UbicacionTexto`); el CSV los hereda por espejo.

## 7. Servidor

Endpoints nuevos (mismo patrón/seguridad de la casa; ADMIN lectura en los GET):

- `POST /api/telefonos/a-revisar` — lista de IMEIs → resultado por IMEI (tabla §4); crea filas Revision; transaccional por IMEI (un fallo no tumba el resto).
- `PATCH /api/telefonos/{imei}/revision/estetica` — guarda parte estética + espejo GRADO_PROPIO.
- `PATCH /api/telefonos/{imei}/revision/funcional` — guarda parte funcional; si bloqueo marcado → ESTADO=BLOQUEADO; responde el veredicto.
- `POST /api/telefonos/{imei}/estado` — acciones `OK` / `BLOQUEAR` / `DESBLOQUEAR` / `DESGUACE` (motivo obligatorio en DESGUACE). Validaciones en servidor: OK exige ESTADO=EN_REVISION + dos partes vigentes + batería ≥ 85 + sin trabajos abiertos.
- `GET` de la cola (EN_REVISION con partes, autores, `FECHA_CREACION`, trabajos abiertos/último cerrado — lo que pide la derivación de chips) y campos de revisión vigente añadidos a la query del inventario para la columna Estado.
- **Quitar-OK automático**: crear cualquier trabajo (reparación/glass/pulido) para un IMEI con ESTADO='OK' lo pasa a EN_REVISION en la misma operación.
- Log de actividad: A_REVISAR, GUARDAR_REVISION (parte), TELEFONO_OK, TELEFONO_BLOQUEO / DESBLOQUEO, TELEFONO_DESGUACE (nombres definitivos en el plan, siguiendo el catálogo existente).

## 8. Permisos (hasta F3)

SUPERTECNICO: todo. ADMIN: pestaña Inventario en lectura — abre ficha solo-lectura, sin acciones ni guardar. TECNICO: no ve la pestaña (como ya está). Server-side con el mismo `PreAuthorize` que el resto de endpoints de la pestaña. Previsión F3: LOGISTICA se añade ampliando la lista de roles, sin rediseño (autoría por parte ya registrada).

## 9. Compatibilidad y despliegue

Orden obligatorio: **migración (usuario, con vista previa) → servidor → cliente**. Servidor nuevo sin tabla Revision: endpoints nuevos y query de inventario extendida fallarían → no desplegar sin migrar. Cliente viejo + servidor nuevo: endpoints preexistentes intactos → seguro. Arranque Spring validado en local antes del merge del servidor (regla de la casa).

## 10. Testing

- **Cliente (JUnit, patrón existente)**: derivación de estados efectivos (precedencias en-reparación > reparado/revisado > por-revisar, anclas de fecha), clasificación del masivo (8 casos §4), armado del banner desde la respuesta de veredicto, precarga del modal de asignar.
- **Servidor (Mockito, patrón suppliers/urgente)**: reglas por estado del a-revisar, sellado autor/fecha por parte + espejo grado, bloqueo automático en el PATCH funcional, validaciones del OK (batería, partes, trabajos), desguace con motivo, quitar-OK al crear trabajo, log.
- Smoke (al cierre, checklist para el usuario): masivo con los 8 casos, dos usuarios rellenando partes separadas, los 4 banners, veto de batería, bloqueo/desbloqueo, desguace, chips y reaparición del REPARADO, ADMIN solo-lectura, log.

## 11. Decisiones del brainstorm (resumen con porqué)

1. **Ficha todo-en-uno** (layout A): calca la etiqueta física; hoy revisa la misma persona; el veredicto necesita ver ambas partes; partirla en F3 sería barato porque el guardado ya es independiente.
2. **Guardar por parte, alineados, parcial siempre válido** — dos personas, una ficha; chips con autor/fecha por parte.
3. **Panel Revisión propio** (opción B): mesa de trabajo del revisor, escáner siempre a mano, patrón de paneles ya existente, hogar natural de LOGISTICA (F3) y lotes (F2c).
4. **REVISADO y REPARADO como estados derivados** (con EN REPARACIÓN): revisar antes y después sin tocar el enum de BD; REVISADO exige las DOS partes; REPARADO reaparece solo en la cola.
5. **OK = regla del check actual**: manual siempre; se quita solo al crear trabajo.
6. **Bloqueo automático** al guardar funcional con el check (con confirmación previa).
7. **Veto duro de batería < 85** en servidor; se levanta re-midiendo y actualizando el %.
8. **Histórico de revisiones solo en BD** (opción A): la UI llegará cuando aporte (devoluciones F2c, estadísticas F4).
9. **Vigente = última fila** por IMEI, creada vacía al entrar en revisión: da "en revisión desde" y el ancla de REPARADO gratis, sin flag ni columna de estado-fecha.
