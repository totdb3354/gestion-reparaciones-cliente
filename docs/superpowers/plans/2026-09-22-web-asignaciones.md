# Sub-proyecto 3a — Asignaciones del supertécnico: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir el placeholder de `/reparaciones/asignaciones` por la vista real del supertécnico (tabla unificada de las tres categorías, filtros, menú contextual, editores, borrado y las dos ventanas de cabecera), y subir al servidor el cálculo de la carga diaria por técnico.

**Architecture:** El servidor gana un cálculo puro portado del cliente (`CargaTecnicos`) y un endpoint de solo lectura que devuelve los dos alcances a la vez. La web gana un módulo nuevo `modules/taller/asignaciones/` con la lógica pura separada de los componentes; todo lo demás (tabla, diálogos, selector de cliente, mapeo de errores) se reutiliza de los sub-proyectos 0-2.

**Tech Stack:** Servidor Spring Boot 3 + JdbcTemplate + JUnit 5 + MockMvc. Web React 19 + TypeScript 5.9 + React Router 7 + TanStack Query + TanStack Table 8 + Vitest + Testing Library.

**Spec:** [`docs/superpowers/specs/2026-09-22-web-asignaciones-design.md`](../specs/2026-09-22-web-asignaciones-design.md). Las decisiones D1-D7 de su §3 son vinculantes.

## Global Constraints

- **Ramas:** `feature/web-asignaciones` en `gestion-reparaciones-web` y en `gestion-reparaciones-servidor`, ambas creadas desde `main`. El repo raíz se queda en `main`.
- **Nunca** `push`, `merge` ni `tag` sin OK explícito del usuario, uno a uno.
- **Commits sin `Co-Authored-By`.** Mensajes en español, en minúscula tras el prefijo.
- **Los tres repos son públicos:** ningún dato real (IMEIs, clientes, nombres de técnicos, dominios, IPs) en código, tests, comentarios ni documentación. Los datos de test son sintéticos desde el primer commit.
- **El cliente JavaFX NO se toca.** Se consulta en solo lectura con `git show hotfix/0.16.3:<ruta>` desde el raíz.
- **Todo el trabajo de servidor es aditivo:** ninguna respuesta que el JavaFX ya consuma cambia de forma.
- **Maven en Bash:** `export JAVA_HOME=/c/Users/dev/tools/jdk-17; export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH`
- **El orden de la tabla es fijo** (urgente → con cliente → resto) y **no se ordena por columna** (D5).
- **Zona horaria:** todo cálculo de día en el servidor usa `Europe/Madrid`.

---

## Estructura de ficheros

**Servidor** (`gestion-reparaciones-servidor`)

| Fichero | Responsabilidad |
|---|---|
| `src/main/java/com/reparaciones/servidor/service/TipoTrabajo.java` | *Crear.* Enum mínimo: deriva el tipo del prefijo del `ID_REP` |
| `src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java` | *Crear.* Cálculo puro portado del cliente, sin cambios de comportamiento |
| `src/main/java/com/reparaciones/servidor/model/CargaTecnicosRespuesta.java` | *Crear.* Records de la respuesta del endpoint |
| `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java` | *Modificar.* Añadir `GET /carga-tecnicos` |
| `src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java` | *Crear.* Los 7 tests portados del cliente |
| `src/test/java/com/reparaciones/servidor/service/CargaTecnicosZonaTest.java` | *Crear.* La zona horaria del día |
| `src/test/java/com/reparaciones/servidor/controller/CargaTecnicosControllerTest.java` | *Crear.* Roles, dos alcances y degradación |

**Web** (`gestion-reparaciones-web`), todo bajo `src/modules/taller/asignaciones/`

| Fichero | Responsabilidad |
|---|---|
| `orden.ts` | *Crear.* Orden de prioridad. Puro |
| `filtros.ts` | *Crear.* Estado y predicado de los cinco filtros. Puro |
| `api.ts` | *Crear.* Queries y mutaciones del módulo |
| `columnas.tsx` | *Crear.* Las once columnas y sus celdas derivadas |
| `MenuAsignacion.tsx` | *Crear.* Menú contextual con sus tres variantes |
| `BarraFiltros.tsx` | *Crear.* La barra de filtros |
| `CargaTecnicosDialog.tsx` | *Crear.* Ventana de carga |
| `TecnicosGlassDialog.tsx` | *Crear.* Diálogo de técnicos de glass |
| `editores/EditorComentario.tsx` · `editores/EditorModelo.tsx` | *Crear.* Dos de los tres editores (cliente reutiliza el existente) |
| `AsignacionesPage.tsx` | *Crear.* Compone todo lo anterior |
| `src/app/router.tsx` | *Modificar.* Sustituir el placeholder y añadir la guarda |
| `docs/paridad/asignaciones.md` | *Crear.* Ficha de paridad |

---

## Task 1: Servidor — `TipoTrabajo` y `CargaTecnicos` portados

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/service/TipoTrabajo.java`
- Create: `src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java`
- Test: `src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java`

**Interfaces:**
- Consumes: `com.reparaciones.servidor.model.ReparacionResumen` (ya tiene `getIdRep`, `getIdTec`, `getCliente`, `isEsChasis`, `isPorCerrar`, `getEsSolicitud`, `getEstadoSolicitud`, `getStockSolicitud`).
- Produces:
  - `TipoTrabajo.desde(String idRep) -> TipoTrabajo` (`REPARACION` | `GLASS` | `PULIDO`)
  - `CargaTecnicos.calcularDia(List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy, DayOfWeek dia, boolean soloPedidos) -> Map<Integer, CargaTecnicos.DiaTecnico>`
  - `CargaTecnicos.DiaTecnico(double pctHecho, double pctPendiente, Desglose hecho, Desglose pendiente, boolean sinJornada)` con `pctTotal()`
  - `CargaTecnicos.Desglose(int normales, int chasis, int porCerrar, int glass, int enEsperaPieza, double carga)`
  - Constantes públicas `TOPE_CHASIS_9H = 8`, `TOPE_GLASS_9H = 17`, `TOPE_NORMALES_9H = 25`, `PESO_POR_CERRAR = 1.0/12`, `JORNADA_HORAS`

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-servidor
git checkout main && git pull --ff-only
git checkout -b feature/web-asignaciones
```

- [ ] **Step 2: Copiar el cálculo y su test desde el cliente, sin editarlos todavía**

Desde el repo raíz, el cliente es solo lectura. Se copia con `git show`:

```bash
cd ..   # raíz del monorepo
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/main/java/com/reparaciones/utils/CargaTecnicos.java \
  > gestion-reparaciones-servidor/src/main/java/com/reparaciones/servidor/service/CargaTecnicos.java
git show hotfix/0.16.3:gestion-reparaciones-cliente/src/test/java/com/reparaciones/utils/CargaTecnicosTest.java \
  > gestion-reparaciones-servidor/src/test/java/com/reparaciones/servidor/service/CargaTecnicosTest.java
```

- [ ] **Step 3: Escribir `TipoTrabajo`**

El cliente tiene un enum grande con colores y etiquetas de JavaFX. En el servidor solo hace falta derivar el tipo del prefijo. Crear `src/main/java/com/reparaciones/servidor/service/TipoTrabajo.java`:

```java
package com.reparaciones.servidor.service;

/**
 * Tipo de trabajo derivado del prefijo del ID_REP: {@code A…} reparación, {@code AG…} glass,
 * {@code AP…} pulido. Equivalente del enum del cliente, sin lo que allí es presentación.
 */
public enum TipoTrabajo {
    REPARACION, GLASS, PULIDO;

    /** Un id nulo o desconocido se trata como reparación, igual que en el cliente. */
    public static TipoTrabajo desde(String idRep) {
        if (idRep == null) return REPARACION;
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return PULIDO;
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return GLASS;
        return REPARACION;
    }
}
```

- [ ] **Step 4: Adaptar los dos ficheros copiados**

En `CargaTecnicos.java` y en `CargaTecnicosTest.java`, cambiar **solo** el paquete y los imports. **No tocar ninguna fórmula, constante ni expectativa de test:** el objetivo es que el cálculo dé exactamente lo mismo.

- `package com.reparaciones.utils;` → `package com.reparaciones.servidor.service;`
- `import com.reparaciones.models.ReparacionResumen;` → `import com.reparaciones.servidor.model.ReparacionResumen;`
- Quitar la referencia a `PrediccionGlass` del javadoc de `fraccion9h` (esa clase llega en el 3b) y **subir el método a `public`**, porque en el 3b lo usará otra clase del mismo paquete… no: dejarlo *package-private* como está. Si el test está en el mismo paquete, compila.
- Si el test construye `ReparacionResumen` con un constructor distinto al del servidor, adaptar **solo la construcción**, nunca los valores esperados.

- [ ] **Step 5: Ejecutar los tests portados y verificar que pasan**

```bash
export JAVA_HOME=/c/Users/dev/tools/jdk-17
export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-servidor
mvn -q -Dtest=CargaTecnicosTest test
```

Esperado: **7 tests, 0 fallos**. Si alguno falla, el fallo está en la adaptación, no en el cálculo: revisar imports y construcción de los datos de prueba.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/service/ src/test/java/com/reparaciones/servidor/service/
git commit -m "feat(servidor): carga diaria por tecnico portada del cliente con su suite"
```

---

## Task 2: Servidor — el endpoint `GET /carga-tecnicos`

**Files:**
- Create: `src/main/java/com/reparaciones/servidor/model/CargaTecnicosRespuesta.java`
- Modify: `src/main/java/com/reparaciones/servidor/controller/ReparacionController.java`
- Test: `src/test/java/com/reparaciones/servidor/controller/CargaTecnicosControllerTest.java`
- Test: `src/test/java/com/reparaciones/servidor/service/CargaTecnicosZonaTest.java`

**Interfaces:**
- Consumes: `CargaTecnicos.calcularDia(...)` y `TipoTrabajo` de la Task 1; `ReparacionDAO.getAsignaciones(Integer)`, `ReparacionDAO.getAsignacionesCompletadasHoy()`, `TecnicoDAO.getAllActivos()`.
- Produces: `GET /api/reparaciones/carga-tecnicos` → `CargaTecnicosRespuesta(List<FilaCarga> pedidos, List<FilaCarga> total)` con
  `FilaCarga(int idTec, String nombre, double pctHecho, double pctPendiente, DesgloseDto hecho, DesgloseDto pendiente, boolean sinJornada)` y
  `DesgloseDto(int normales, int chasis, int porCerrar, int glass, int enEsperaPieza)`.

- [ ] **Step 1: Escribir el test de la zona horaria, que debe fallar**

Crear `src/test/java/com/reparaciones/servidor/service/CargaTecnicosZonaTest.java`:

```java
package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CargaTecnicosZonaTest {

    /** Sábado 00:30 en Madrid es viernes 22:30 en UTC: si el día se resolviera en UTC,
     *  el fin de semana se desplazaría y la carga saldría con jornada donde no la hay. */
    @Test
    void elDiaSeResuelveEnMadridNoEnUtc() {
        Instant sabadoDeMadrugadaEnMadrid = Instant.parse("2026-09-26T22:30:00Z");
        DayOfWeek enMadrid = sabadoDeMadrugadaEnMadrid.atZone(ZoneId.of("Europe/Madrid")).getDayOfWeek();
        DayOfWeek enUtc = sabadoDeMadrugadaEnMadrid.atZone(ZoneId.of("UTC")).getDayOfWeek();

        assertEquals(DayOfWeek.SATURDAY, enMadrid);
        assertEquals(DayOfWeek.FRIDAY, enUtc);
        assertEquals(0, CargaTecnicos.JORNADA_HORAS.get(enMadrid));
        assertEquals(6, CargaTecnicos.JORNADA_HORAS.get(enUtc));
    }

    /** El helper que usa el controlador debe devolver el día de Madrid. */
    @Test
    void diaDeHoyUsaMadrid() {
        assertEquals(java.time.LocalDate.now(ZoneId.of("Europe/Madrid")).getDayOfWeek(),
                     CargaTecnicos.diaDeHoy());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
mvn -q -Dtest=CargaTecnicosZonaTest test
```

Esperado: FALLA al compilar, con `cannot find symbol: method diaDeHoy()`.

- [ ] **Step 3: Añadir `diaDeHoy()` a `CargaTecnicos`**

Al final de `CargaTecnicos.java`, antes de la llave de cierre:

```java
    /** Día de la semana en Madrid. El proceso del servidor puede correr en UTC: resolverlo ahí
     *  desplazaría el fin de semana entre medianoche y las 02:00. */
    public static java.time.DayOfWeek diaDeHoy() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Europe/Madrid")).getDayOfWeek();
    }
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

```bash
mvn -q -Dtest=CargaTecnicosZonaTest test
```

Esperado: **2 tests, 0 fallos**.

- [ ] **Step 5: Crear los records de la respuesta**

Crear `src/main/java/com/reparaciones/servidor/model/CargaTecnicosRespuesta.java`:

```java
package com.reparaciones.servidor.model;

import java.util.List;

/**
 * Carga diaria por técnico en los dos alcances a la vez (spec 3a §5): la ventana del cliente
 * alterna Pedidos|Total sin volver al servidor, así que se mandan juntos.
 */
public record CargaTecnicosRespuesta(List<FilaCarga> pedidos, List<FilaCarga> total) {

    /** Una fila: el técnico y sus porcentajes del día ya escalados a la jornada de hoy. */
    public record FilaCarga(int idTec, String nombre, double pctHecho, double pctPendiente,
                            DesgloseDto hecho, DesgloseDto pendiente, boolean sinJornada) {}

    /** Recuento por tipo de trabajo dentro de un tramo (hecho o pendiente). */
    public record DesgloseDto(int normales, int chasis, int porCerrar, int glass, int enEsperaPieza) {}
}
```

- [ ] **Step 6: Escribir el test del endpoint, que debe fallar**

Crear `src/test/java/com/reparaciones/servidor/controller/CargaTecnicosControllerTest.java`. Seguir el patrón de los tests de controlador que ya existen en esa carpeta (`@WebMvcTest` o `@SpringBootTest` + `MockMvc`, con los DAO mockeados) y cubrir exactamente estos cuatro casos:

1. `supertecnicoRecibeLosDosAlcances`: como SUPERTECNICO, 200 y el cuerpo trae `pedidos` y `total`, ambos con una fila por técnico activo.
2. `adminTambienPuedeConsultarla`: como ADMIN, 200.
3. `tecnicoRecibe403`: como TECNICO, 403.
4. `siFallanLasCompletadasHoyDegradaASoloPendiente`: si `getAsignacionesCompletadasHoy()` lanza, la respuesta sigue siendo 200 y todos los `pctHecho` son 0.

Datos de prueba **sintéticos**: técnicos `T1`/`T2` con ids 1 y 2, IMEIs `000000000000001` y siguientes, sin nombres reales.

- [ ] **Step 7: Ejecutar y verificar que falla**

```bash
mvn -q -Dtest=CargaTecnicosControllerTest test
```

Esperado: FALLA con 404 en las cuatro pruebas (el endpoint no existe).

- [ ] **Step 8: Implementar el endpoint**

En `ReparacionController.java`, junto al resto de GET de asignaciones (después de `getAsignaciones`, sobre la línea 92):

```java
    /**
     * Carga diaria por técnico, en los dos alcances (spec 3a §5). Cálculo que antes hacía el
     * cliente. El tramo "hecho hoy" degrada a vacío si su consulta falla, igual que el JavaFX.
     */
    @PreAuthorize("hasAnyRole('SUPERTECNICO','ADMIN')")
    @GetMapping("/carga-tecnicos")
    public CargaTecnicosRespuesta getCargaTecnicos() {
        List<ReparacionResumen> abiertas = dao.getAsignaciones(null);
        List<ReparacionResumen> cerradasHoy;
        try {
            cerradasHoy = dao.getAsignacionesCompletadasHoy();
        } catch (RuntimeException e) {
            cerradasHoy = List.of();   // degradación deliberada: solo-pendiente
        }
        java.time.DayOfWeek dia = CargaTecnicos.diaDeHoy();
        return new CargaTecnicosRespuesta(
                filas(CargaTecnicos.calcularDia(abiertas, cerradasHoy, dia, true)),
                filas(CargaTecnicos.calcularDia(abiertas, cerradasHoy, dia, false)));
    }

    /** Una fila por técnico ACTIVO, con ceros para quien no aparezca en el mapa. */
    private List<CargaTecnicosRespuesta.FilaCarga> filas(Map<Integer, CargaTecnicos.DiaTecnico> mapa) {
        boolean sinJornada = CargaTecnicos.JORNADA_HORAS.getOrDefault(CargaTecnicos.diaDeHoy(), 0) == 0;
        CargaTecnicos.Desglose vacio = new CargaTecnicos.Desglose(0, 0, 0, 0, 0, 0);
        return tecnicoDao.getAllActivos().stream()
                .map(t -> {
                    CargaTecnicos.DiaTecnico dt = mapa.getOrDefault(t.getIdTec(),
                            new CargaTecnicos.DiaTecnico(0, 0, vacio, vacio, sinJornada));
                    return new CargaTecnicosRespuesta.FilaCarga(
                            t.getIdTec(), t.getNombre(), dt.pctHecho(), dt.pctPendiente(),
                            dto(dt.hecho()), dto(dt.pendiente()), dt.sinJornada());
                })
                .toList();
    }

    private static CargaTecnicosRespuesta.DesgloseDto dto(CargaTecnicos.Desglose d) {
        return new CargaTecnicosRespuesta.DesgloseDto(
                d.normales(), d.chasis(), d.porCerrar(), d.glass(), d.enEsperaPieza());
    }
```

Añadir los imports que falten (`CargaTecnicosRespuesta`, `CargaTecnicos`, `Map`) y, si el controlador aún no tiene un `TecnicoDAO` inyectado, añadirlo al constructor siguiendo el patrón de los demás DAO.

- [ ] **Step 9: Ejecutar y verificar que pasan**

```bash
mvn -q -Dtest=CargaTecnicosControllerTest test
```

Esperado: **4 tests, 0 fallos**.

- [ ] **Step 10: Ejecutar la suite entera del servidor**

```bash
mvn -q test
```

Esperado: todo en verde (218 tests del sub-proyecto 2 más los nuevos). Ningún test existente debe cambiar: el trabajo es aditivo.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/reparaciones/servidor/ src/test/java/com/reparaciones/servidor/
git commit -m "feat(servidor): endpoint de carga de tecnicos con los dos alcances y dia en Madrid"
```

---

## Task 3: Web — rama, contrato regenerado y tipos

**Files:**
- Modify: `src/shared/api/schema.d.ts` (generado, no editar a mano)
- Modify: `src/shared/api/client.ts` (exportar los tipos nuevos)

**Interfaces:**
- Consumes: el endpoint de la Task 2.
- Produces: `CargaTecnicosRespuesta`, `FilaCarga` y `DesgloseDto` exportados desde `@/shared/api/client`.

- [ ] **Step 1: Crear la rama**

```bash
cd gestion-reparaciones-web
git checkout main && git pull --ff-only
git checkout -b feature/web-asignaciones
```

- [ ] **Step 2: Levantar el servidor de la Task 2 en local y regenerar el contrato**

```bash
npm run fetch-openapi     # script ya existente
npm run gen-api           # regenera src/shared/api/schema.d.ts
```

Si los nombres de los scripts difieren, mirar `package.json`: son los mismos que se usaron en los sub-proyectos 0-2.

- [ ] **Step 3: Exportar los tipos en `client.ts`**

Junto a los demás `export type` del bloque del sub-proyecto 2:

```ts
/** Carga diaria por técnico (sub-proyecto 3a): los dos alcances en una respuesta. */
export type CargaTecnicosRespuesta = components['schemas']['CargaTecnicosRespuesta']
export type FilaCarga = components['schemas']['CargaTecnicosRespuestaFilaCarga']
export type DesgloseCarga = components['schemas']['CargaTecnicosRespuestaDesgloseDto']
```

Verificar el nombre exacto de cada esquema en `schema.d.ts`: springdoc antepone el nombre de la clase contenedora a los records anidados.

- [ ] **Step 4: Comprobar que compila**

```bash
npm run check
```

Esperado: 0 errores de TypeScript y 0 de lint. Los tests existentes siguen en verde.

- [ ] **Step 5: Commit**

```bash
git add src/shared/api/
git commit -m "chore(web): contrato regenerado con la carga de tecnicos"
```

---

## Task 4: Web — `orden.ts`, el orden de prioridad

**Files:**
- Create: `src/modules/taller/asignaciones/orden.ts`
- Test: `src/modules/taller/asignaciones/orden.test.ts`

**Interfaces:**
- Consumes: `ReparacionResumen` de `@/shared/api/client`.
- Produces: `ordenarPorPrioridad(filas: ReparacionResumen[]): ReparacionResumen[]` — devuelve una copia nueva, no muta.

- [ ] **Step 1: Escribir el test que falla**

Crear `src/modules/taller/asignaciones/orden.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { ReparacionResumen } from '@/shared/api/client'
import { ordenarPorPrioridad } from './orden'

const fila = (idRep: string, urgente: boolean, cliente: string | null) =>
  ({ idRep, urgente, cliente }) as unknown as ReparacionResumen

describe('ordenarPorPrioridad', () => {
  it('pone primero las urgentes, luego las que tienen cliente y al final el resto', () => {
    const entrada = [fila('A3', false, null), fila('A2', false, 'CLI'), fila('A1', true, null)]
    expect(ordenarPorPrioridad(entrada).map((f) => f.idRep)).toEqual(['A1', 'A2', 'A3'])
  })

  it('una urgente va delante aunque no tenga cliente', () => {
    const entrada = [fila('A2', false, 'CLI'), fila('A1', true, null)]
    expect(ordenarPorPrioridad(entrada).map((f) => f.idRep)).toEqual(['A1', 'A2'])
  })

  it('el cliente vacío cuenta como sin cliente', () => {
    const entrada = [fila('A2', false, ''), fila('A1', false, 'CLI')]
    expect(ordenarPorPrioridad(entrada).map((f) => f.idRep)).toEqual(['A1', 'A2'])
  })

  it('es estable dentro de cada grupo', () => {
    const entrada = [fila('A1', true, null), fila('A2', true, null), fila('A3', true, null)]
    expect(ordenarPorPrioridad(entrada).map((f) => f.idRep)).toEqual(['A1', 'A2', 'A3'])
  })

  it('no muta la lista recibida', () => {
    const entrada = [fila('A2', false, null), fila('A1', true, null)]
    ordenarPorPrioridad(entrada)
    expect(entrada.map((f) => f.idRep)).toEqual(['A2', 'A1'])
  })
})
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/orden.test.ts
```

Esperado: FALLA con "Failed to resolve import './orden'".

- [ ] **Step 3: Implementar**

Crear `src/modules/taller/asignaciones/orden.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'

/** Calco del orden del JavaFX: urgente (0) → con cliente (1) → resto (2). */
function prioridad(f: ReparacionResumen): number {
  if (f.urgente) return 0
  return f.cliente ? 1 : 2
}

/**
 * Ordena por prioridad, estable dentro de cada grupo. Es el ÚNICO orden de la tabla: no se
 * ordena por columna (spec 3a, D5), porque lo urgente tiene que quedar arriba siempre.
 */
export function ordenarPorPrioridad(filas: ReparacionResumen[]): ReparacionResumen[] {
  return [...filas].sort((a, b) => prioridad(a) - prioridad(b))
}
```

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/orden.test.ts
```

Esperado: **5 tests, 0 fallos**. `Array.prototype.sort` es estable en todos los motores modernos, que es lo que cubre el cuarto test.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): orden de prioridad de las asignaciones"
```

---

## Task 5: Web — `filtros.ts`, los cinco filtros

**Files:**
- Create: `src/modules/taller/asignaciones/filtros.ts`
- Test: `src/modules/taller/asignaciones/filtros.test.ts`

**Interfaces:**
- Consumes: `ReparacionResumen`; `imeisValidos` de la utilidad de IMEI que ya existe en la web (buscarla con `grep -rn "imeisValidos\|FiltroImei" src/`; si no existe, crearla en `src/modules/taller/lib/filtroImei.ts` con la misma regla que el cliente y sus tests).
- Produces:
  - `SIN_CLIENTE = '(Sin cliente)'`
  - `type EstadoFiltros = { imei: string; tecnicos: number[]; clientes: string[]; tipos: TipoTrabajo[]; estados: EstadoAsignacion[] }`
  - `type TipoTrabajo = 'REPARACION' | 'GLASS' | 'PULIDO'`
  - `type EstadoAsignacion = 'SOLICITUD' | 'INCIDENCIA' | 'ASIGNACION'`
  - `FILTROS_VACIOS: EstadoFiltros`
  - `tipoDe(idRep: string): TipoTrabajo`
  - `aplicarFiltros(filas: ReparacionResumen[], f: EstadoFiltros): ReparacionResumen[]`

- [ ] **Step 1: Escribir el test que falla**

Crear `src/modules/taller/asignaciones/filtros.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import type { ReparacionResumen } from '@/shared/api/client'
import { FILTROS_VACIOS, SIN_CLIENTE, aplicarFiltros, tipoDe } from './filtros'

const fila = (p: Partial<ReparacionResumen>) =>
  ({ idRep: 'A1', imei: '000000000000001', idTec: 1, cliente: null, esSolicitud: 0, esIncidencia: false, ...p }) as unknown as ReparacionResumen

const ids = (fs: ReparacionResumen[]) => fs.map((f) => f.idRep)

describe('tipoDe', () => {
  it('deriva el tipo del prefijo del id', () => {
    expect(tipoDe('A20260922_1')).toBe('REPARACION')
    expect(tipoDe('AG20260922_1')).toBe('GLASS')
    expect(tipoDe('AP20260922_1')).toBe('PULIDO')
  })
})

describe('aplicarFiltros', () => {
  const filas = [
    fila({ idRep: 'A1', imei: '000000000000001', idTec: 1, cliente: 'CLI_A' }),
    fila({ idRep: 'AG2', imei: '000000000000002', idTec: 2, cliente: null }),
    fila({ idRep: 'AP3', imei: '000000000000003', idTec: 1, cliente: 'CLI_B', esSolicitud: 1 }),
    fila({ idRep: 'A4', imei: '000000000000004', idTec: 2, cliente: null, esIncidencia: true }),
  ]

  it('sin filtros devuelve todo', () => {
    expect(ids(aplicarFiltros(filas, FILTROS_VACIOS))).toEqual(['A1', 'AG2', 'AP3', 'A4'])
  })

  it('el IMEI incompleto no filtra', () => {
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, imei: '00000' }))).toHaveLength(4)
  })

  it('el IMEI completo filtra a esa fila', () => {
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, imei: '000000000000002' }))).toEqual(['AG2'])
  })

  it('admite varios IMEIs separados por comas', () => {
    const f = { ...FILTROS_VACIOS, imei: '000000000000001,000000000000003' }
    expect(ids(aplicarFiltros(filas, f))).toEqual(['A1', 'AP3'])
  })

  it('una lista de técnicos vacía significa no filtrar, no ninguno', () => {
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, tecnicos: [] }))).toHaveLength(4)
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, tecnicos: [2] }))).toEqual(['AG2', 'A4'])
  })

  it('el cliente admite el centinela de sin cliente', () => {
    const f = { ...FILTROS_VACIOS, clientes: [SIN_CLIENTE] }
    expect(ids(aplicarFiltros(filas, f))).toEqual(['AG2', 'A4'])
  })

  it('filtra por tipo', () => {
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, tipos: ['GLASS', 'PULIDO'] }))).toEqual(['AG2', 'AP3'])
  })

  it('los estados se combinan con O y asignación es ni solicitud ni incidencia', () => {
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, estados: ['SOLICITUD'] }))).toEqual(['AP3'])
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, estados: ['INCIDENCIA'] }))).toEqual(['A4'])
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, estados: ['ASIGNACION'] }))).toEqual(['A1', 'AG2'])
    expect(ids(aplicarFiltros(filas, { ...FILTROS_VACIOS, estados: ['SOLICITUD', 'INCIDENCIA'] }))).toEqual(['AP3', 'A4'])
  })

  it('combina filtros de distintos grupos con Y', () => {
    const f = { ...FILTROS_VACIOS, tecnicos: [1], tipos: ['REPARACION' as const] }
    expect(ids(aplicarFiltros(filas, f))).toEqual(['A1'])
  })
})
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/filtros.test.ts
```

Esperado: FALLA con "Failed to resolve import './filtros'".

- [ ] **Step 3: Implementar**

Crear `src/modules/taller/asignaciones/filtros.ts`:

```ts
import type { ReparacionResumen } from '@/shared/api/client'

export type TipoTrabajo = 'REPARACION' | 'GLASS' | 'PULIDO'
export type EstadoAsignacion = 'SOLICITUD' | 'INCIDENCIA' | 'ASIGNACION'

/** Centinela del filtro de cliente para las filas sin cliente. */
export const SIN_CLIENTE = '(Sin cliente)'

export type EstadoFiltros = {
  imei: string
  tecnicos: number[]
  clientes: string[]
  tipos: TipoTrabajo[]
  estados: EstadoAsignacion[]
}

export const FILTROS_VACIOS: EstadoFiltros = { imei: '', tecnicos: [], clientes: [], tipos: [], estados: [] }

/** El tipo sale del prefijo del id, no de un campo (calco del cliente). */
export function tipoDe(idRep: string): TipoTrabajo {
  if (idRep.startsWith('AP') || idRep.startsWith('P')) return 'PULIDO'
  if (idRep.startsWith('AG') || idRep.startsWith('G')) return 'GLASS'
  return 'REPARACION'
}

/** Solo los tramos de 15 dígitos cuentan: un IMEI a medias no filtra (calco del cliente). */
function imeisValidos(texto: string): Set<string> {
  return new Set(
    texto
      .split(',')
      .map((t) => t.trim())
      .filter((t) => /^\d{15}$/.test(t)),
  )
}

function estadoDe(f: ReparacionResumen): EstadoAsignacion {
  if (f.esSolicitud > 0) return 'SOLICITUD'
  if (f.esIncidencia) return 'INCIDENCIA'
  return 'ASIGNACION'
}

/**
 * Aplica los cinco filtros en memoria (spec 3a, D7). Dentro de cada grupo los valores se
 * combinan con O; entre grupos, con Y. Un grupo vacío NO filtra.
 */
export function aplicarFiltros(filas: ReparacionResumen[], f: EstadoFiltros): ReparacionResumen[] {
  const imeis = imeisValidos(f.imei)
  return filas.filter((r) => {
    if (imeis.size > 0 && !imeis.has(r.imei ?? '')) return false
    if (f.tecnicos.length > 0 && !f.tecnicos.includes(r.idTec)) return false
    if (f.clientes.length > 0) {
      const clave = r.cliente ? r.cliente : SIN_CLIENTE
      if (!f.clientes.includes(clave)) return false
    }
    if (f.tipos.length > 0 && !f.tipos.includes(tipoDe(r.idRep))) return false
    if (f.estados.length > 0 && !f.estados.includes(estadoDe(r))) return false
    return true
  })
}
```

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/filtros.test.ts
```

Esperado: **11 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): filtros de asignaciones, puros y en memoria"
```

---

## Task 6: Web — `api.ts` del módulo

**Files:**
- Create: `src/modules/taller/asignaciones/api.ts`
- Test: `src/modules/taller/asignaciones/api.test.tsx`

**Interfaces:**
- Consumes: `api` de `@/shared/api/client`, `useIntervaloRefresco` de `@/shared/api/refresco`, `ordenarPorPrioridad` de la Task 4.
- Produces:
  - `CLAVE_ASIGNACIONES_TODAS = ['asignaciones', 'todas'] as const`
  - `CLAVE_CARGA_TECNICOS = ['carga-tecnicos'] as const`
  - `useAsignacionesTodas(opciones?: { activo?: boolean })` → `UseQueryResult<ReparacionResumen[]>`, ya ordenada
  - `useCargaTecnicos(habilitada: boolean)` → `UseQueryResult<CargaTecnicosRespuesta>`
  - `useReasignar()` → mutación `{ idRep: string; idTec: number }`
  - `useUrgente()` → mutación `{ idRep: string; urgente: boolean }`
  - `useChasis()` → mutación `{ idRep: string; esChasis: boolean }`
  - `useBorrarAsignacion()` → mutación `{ idAsig: string; motivo?: string }`

- [ ] **Step 1: Escribir el test que falla**

Crear `src/modules/taller/asignaciones/api.test.tsx` siguiendo el patrón de `src/modules/taller/api.test.tsx`, que ya monta un `QueryClientProvider` y espía `api`. Cubrir:

1. `useAsignacionesTodas pide las tres listas sin el parámetro tecnico` — comprobar que las tres llamadas salen con `params.query.tecnico === undefined`.
2. `useAsignacionesTodas devuelve las tres categorías juntas y ordenadas` — con una urgente al final de la respuesta, debe salir la primera.
3. `useCargaTecnicos no consulta si no está habilitada` — con `false`, `api.GET` no se llama.
4. `useReasignar llama al PATCH de la asignación con el técnico nuevo`.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/api.test.tsx
```

Esperado: FALLA con "Failed to resolve import './api'".

- [ ] **Step 3: Implementar**

Crear `src/modules/taller/asignaciones/api.ts`:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type CargaTecnicosRespuesta, type ReparacionResumen } from '@/shared/api/client'
import { useIntervaloRefresco } from '@/shared/api/refresco'
import { ordenarPorPrioridad } from './orden'

export const CLAVE_ASIGNACIONES_TODAS = ['asignaciones', 'todas'] as const
export const CLAVE_CARGA_TECNICOS = ['carga-tecnicos'] as const

/** Las tres categorías, SIN ?tecnico=: es la lista completa del supertécnico. */
async function pedirTodas(): Promise<ReparacionResumen[]> {
  const [rep, glass, pul] = await Promise.all([
    api.GET('/api/reparaciones/asignaciones', { params: { query: {} } }),
    api.GET('/api/glass/asignaciones', { params: { query: {} } }),
    api.GET('/api/pulidos/asignaciones', { params: { query: {} } }),
  ])
  return ordenarPorPrioridad([...(rep.data ?? []), ...(glass.data ?? []), ...(pul.data ?? [])])
}

/**
 * Lista unificada. `activo = false` congela el sondeo mientras hay un menú, un desplegable o un
 * diálogo abiertos (spec 3a, D4): si no, la fila se mueve bajo el cursor y la acción se pierde.
 */
export function useAsignacionesTodas({ activo = true }: { activo?: boolean } = {}) {
  const intervalo = useIntervaloRefresco(activo)
  return useQuery({ queryKey: CLAVE_ASIGNACIONES_TODAS, queryFn: pedirTodas, refetchInterval: intervalo })
}

/** Solo se pide con la ventana abierta: no tiene sentido sondearla de fondo. */
export function useCargaTecnicos(habilitada: boolean) {
  return useQuery({
    queryKey: CLAVE_CARGA_TECNICOS,
    queryFn: async () => (await api.GET('/api/reparaciones/carga-tecnicos')).data as CargaTecnicosRespuesta,
    enabled: habilitada,
  })
}

function useRecargarAsignaciones() {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: CLAVE_ASIGNACIONES_TODAS })
    void qc.invalidateQueries({ queryKey: CLAVE_CARGA_TECNICOS })
  }
}

export function useReasignar() {
  const recargar = useRecargarAsignaciones()
  return useMutation({
    mutationFn: ({ idRep, idTec }: { idRep: string; idTec: number }) =>
      api.PATCH('/api/reparaciones/asignaciones/{idRep}', { params: { path: { idRep } }, body: { idTec } }),
    onSettled: recargar,
  })
}

export function useUrgente() {
  const recargar = useRecargarAsignaciones()
  return useMutation({
    mutationFn: ({ idRep, urgente }: { idRep: string; urgente: boolean }) =>
      api.PATCH('/api/reparaciones/asignaciones/{idRep}/urgente', { params: { path: { idRep } }, body: { urgente } }),
    onSettled: recargar,
  })
}

export function useChasis() {
  const recargar = useRecargarAsignaciones()
  return useMutation({
    mutationFn: ({ idRep, esChasis }: { idRep: string; esChasis: boolean }) =>
      api.PATCH('/api/reparaciones/asignaciones/{idRep}/chasis', { params: { path: { idRep } }, body: { esChasis } }),
    onSettled: recargar,
  })
}

export function useBorrarAsignacion() {
  const recargar = useRecargarAsignaciones()
  return useMutation({
    mutationFn: ({ idAsig, motivo }: { idAsig: string; motivo?: string }) =>
      api.DELETE('/api/reparaciones/asignaciones/{idAsig}', {
        params: { path: { idAsig } },
        body: motivo ? { motivo } : undefined,
      }),
    onSettled: recargar,
  })
}
```

**Antes de escribir las mutaciones, verificar el cuerpo exacto de cada endpoint** en `schema.d.ts`: los nombres de propiedad (`idTec`, `urgente`, `esChasis`, `motivo`) deben coincidir con los records del servidor. Si alguno difiere, manda el contrato.

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/api.test.tsx
```

Esperado: **4 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): api del modulo de asignaciones"
```

---

## Task 7: Web — `columnas.tsx`, las once columnas

**Files:**
- Create: `src/modules/taller/asignaciones/columnas.tsx`
- Test: `src/modules/taller/asignaciones/columnas.test.tsx`

**Interfaces:**
- Consumes: `tipoDe` de la Task 5; de `@/modules/taller/lib/entregaGlass` (ya existe, del sub-proyecto 1): `textoBadgeEntrega`, `tooltipEntrega`, `etiquetaGlassPendiente`, `etiquetaRepAbierta`. **Antes de escribir nada, abrir ese fichero y usar los nombres exactos que exporta.**
- Produces: `crearColumnas(opciones: OpcionesColumnas): ColumnDef<ReparacionResumen>[]` con
  `type OpcionesColumnas = { soloLectura: boolean; tecnicos: Tecnico[]; onReasignar: (idRep: string, idTec: number) => void; onBorrar: (fila: ReparacionResumen) => void }`
  y, exportado aparte para poder testearlo sin render: `badgesEstado(fila: ReparacionResumen): BadgeEstado[]` con
  `type BadgeEstado = { clave: 'URGENTE' | 'POR_CERRAR' | 'ENTREGA' | 'INCIDENCIA' | 'RECIBIDO' | 'EN_CAMINO' | 'SOLICITUD' | 'NORMAL'; texto: string }`

- [ ] **Step 1: Escribir el test de `badgesEstado`, que falla**

Crear `src/modules/taller/asignaciones/columnas.test.tsx`:

```tsx
import { describe, expect, it } from 'vitest'
import type { ReparacionResumen } from '@/shared/api/client'
import { badgesEstado } from './columnas'

const fila = (p: Partial<ReparacionResumen>) =>
  ({ idRep: 'A1', urgente: false, porCerrar: false, esSolicitud: 0, esIncidencia: false,
     estadoSolicitud: null, stockSolicitud: 0, enCaminoSolicitud: false,
     entregadoAt: null, glassEntregadoAt: null, ...p }) as unknown as ReparacionResumen

const claves = (f: ReparacionResumen) => badgesEstado(f).map((b) => b.clave)

describe('badgesEstado', () => {
  it('una fila sin nada es Normal', () => {
    expect(claves(fila({}))).toEqual(['NORMAL'])
  })

  it('urgente sola no lleva Normal detrás', () => {
    expect(claves(fila({ urgente: true }))).toEqual(['URGENTE'])
  })

  it('apila urgente y por cerrar', () => {
    expect(claves(fila({ urgente: true, porCerrar: true }))).toEqual(['URGENTE', 'POR_CERRAR'])
  })

  it('la incidencia gana a la solicitud', () => {
    expect(claves(fila({ esSolicitud: 1, esIncidencia: true }))).toEqual(['INCIDENCIA'])
  })

  it('la solicitud gestionada y con stock es Recibido', () => {
    expect(claves(fila({ esSolicitud: 1, estadoSolicitud: 'GESTIONADA', stockSolicitud: 3 }))).toEqual(['RECIBIDO'])
  })

  it('la solicitud en camino es En camino', () => {
    expect(claves(fila({ esSolicitud: 1, enCaminoSolicitud: true }))).toEqual(['EN_CAMINO'])
  })

  it('la solicitud sin gestionar ni enviar es Solicitud', () => {
    expect(claves(fila({ esSolicitud: 1 }))).toEqual(['SOLICITUD'])
  })
})
```

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/columnas.test.tsx
```

Esperado: FALLA con "Failed to resolve import './columnas'".

- [ ] **Step 3: Implementar `badgesEstado` y las columnas**

En `src/modules/taller/asignaciones/columnas.tsx`, la parte pura:

```tsx
export type BadgeEstado = {
  clave: 'URGENTE' | 'POR_CERRAR' | 'ENTREGA' | 'INCIDENCIA' | 'RECIBIDO' | 'EN_CAMINO' | 'SOLICITUD' | 'NORMAL'
  texto: string
}

/**
 * Los badges apilados de la columna Estado, en orden (spec 3a §8). Los tres primeros son
 * independientes; el cuarto es una cascada excluyente y solo aparece uno.
 */
export function badgesEstado(f: ReparacionResumen): BadgeEstado[] {
  const out: BadgeEstado[] = []
  if (f.urgente) out.push({ clave: 'URGENTE', texto: 'Urgente' })
  if (f.porCerrar) out.push({ clave: 'POR_CERRAR', texto: 'Por cerrar' })

  const entrega = textoBadgeEntrega(f, hoyMadrid())
  if (entrega) out.push({ clave: 'ENTREGA', texto: entrega })

  if (f.esIncidencia) {
    out.push({ clave: 'INCIDENCIA', texto: 'Incidencia' })
  } else if (f.esSolicitud > 0) {
    const recibido = f.estadoSolicitud === 'GESTIONADA' && f.stockSolicitud > 0
    if (recibido) out.push({ clave: 'RECIBIDO', texto: 'Recibido' })
    else if (f.enCaminoSolicitud) out.push({ clave: 'EN_CAMINO', texto: 'En camino' })
    else out.push({ clave: 'SOLICITUD', texto: 'Solicitud' })
  } else if (!f.urgente) {
    out.push({ clave: 'NORMAL', texto: 'Normal' })
  }
  return out
}
```

`hoyMadrid` ya existe en `@/shared/lib/fechas`. Los colores de cada clave van en un mapa de clases junto al componente que los pinta, no en esta función.

Después, `crearColumnas(opciones)` devuelve las once definiciones de `ColumnDef<ReparacionResumen>` en el orden de la spec §7, siguiendo el patrón de columnas de `src/modules/taller/historial/` (abrirlo y copiar la forma). Puntos a respetar:

- **Tipo**: badge del tipo y, debajo, la palabra `Chasis` cuando `fila.esChasis` y el tipo es reparación.
- **Técnico**: si `soloLectura`, texto plano con el nombre; si no, el desplegable (se cablea en la Task 11; aquí basta con dejar el hueco recibiendo `onReasignar`).
- **IMEI**: el IMEI y, debajo, `etiquetaGlassPendiente(fila)` o `etiquetaRepAbierta(fila)` si devuelven algo.
- **Estado**: pinta `badgesEstado(fila)` en vertical, y el badge de entrega lleva `title={tooltipEntrega(fila)}`.
- **Última columna**: papelera que llama a `onBorrar(fila)`; no se renderiza si `soloLectura`.
- Todas las columnas con `enableSorting: false` (D5).

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/columnas.test.tsx
```

Esperado: **7 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): columnas de la tabla de asignaciones"
```

---

## Task 8: Web — la página y la ruta con guarda

**Files:**
- Create: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Modify: `src/app/router.tsx:29`
- Test: `src/modules/taller/asignaciones/AsignacionesPage.test.tsx`

**Interfaces:**
- Consumes: `useAsignacionesTodas` (Task 6), `crearColumnas` (Task 7), `DataTable` de `@/shared/ui`.
- Produces: `<AsignacionesPage />`, montada en `/reparaciones/asignaciones`.

- [ ] **Step 1: Escribir el test que falla**

`AsignacionesPage.test.tsx` monta la página con el `QueryClientProvider` y la sesión falsa que usan los tests del sub-proyecto 1 (copiar el arranque de `src/modules/taller/pendientes/PendientesPage.test.tsx`) y comprueba:

1. `muestra el título y el contador filtrado`: con tres filas, aparece "3 asignaciones".
2. `pinta las tres categorías en la misma tabla`: se ven los tres identificadores `A…`, `AG…` y `AP…`.
3. `las urgentes salen primero`.
4. `la tabla no se puede ordenar por columna`: las cabeceras no tienen `button` de orden.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/AsignacionesPage.test.tsx
```

Esperado: FALLA con "Failed to resolve import './AsignacionesPage'".

- [ ] **Step 3: Implementar la página mínima**

`AsignacionesPage.tsx` con: título "Asignaciones pendientes", la pastilla del contador (número de filas **tras filtros**), el `DataTable` con `crearColumnas`, el `EtiquetaActualizado` del pie que ya usan las otras vistas, y el botón "Asignar" **deshabilitado con `title="Disponible en el siguiente sub-proyecto"`** (spec §2). Los filtros llegan en la Task 9: de momento, `FILTROS_VACIOS`.

- [ ] **Step 4: Sustituir el placeholder en el router**

En `src/app/router.tsx`, cambiar la línea 29:

```tsx
          { path: '/reparaciones/asignaciones', element: <PendienteDeMigrar nombre="Asignaciones" /> },
```

por una ruta guardada:

```tsx
          {
            element: <RequiereSupertecnicoOAdmin />,
            children: [{ path: '/reparaciones/asignaciones', element: <AsignacionesPage /> }],
          },
```

Crear `RequiereSupertecnicoOAdmin` en `src/modules/taller/rutas.tsx`, junto a `RequiereSupertecnico` y `RequiereTecnico`, **copiando el patrón de la que ya existe** y usando `esAdminOSuperTecnico` de `@/shared/session/storage`, que ya está escrita. Quitar el import de `PendienteDeMigrar` si deja de usarse en el fichero.

- [ ] **Step 5: Ejecutar los tests y la comprobación completa**

```bash
npx vitest run src/modules/taller/asignaciones/
npm run check
```

Esperado: los 4 tests nuevos en verde, 0 errores de tipos y de lint, y el resto de la suite intacta.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/asignaciones/ src/app/router.tsx src/modules/taller/rutas.tsx
git commit -m "feat(web): vista de asignaciones en su ruta, con guarda de supertecnico y admin"
```

---

## Task 9: Web — la barra de filtros

**Files:**
- Create: `src/modules/taller/asignaciones/BarraFiltros.tsx`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/BarraFiltros.test.tsx`

**Interfaces:**
- Consumes: `EstadoFiltros`, `FILTROS_VACIOS`, `SIN_CLIENTE` (Task 5).
- Produces: `<BarraFiltros valor={EstadoFiltros} onCambio={(f: EstadoFiltros) => void} tecnicos={Tecnico[]} clientes={string[]} onInteraccion={(abierta: boolean) => void} />`

- [ ] **Step 1: Escribir el test que falla**

Cubrir, con Testing Library:

1. `el IMEI incompleto se marca en rojo y no filtra`: escribir 5 dígitos deja `aria-invalid="true"` y `onCambio` recibe ese texto, pero la tabla de la página sigue con todas las filas.
2. `al completar 15 dígitos se añade una coma`.
3. `el desplegable de cliente arranca con todos marcados y dice Todos`.
4. `Limpiar filtros resetea los cinco`, incluidos técnico y cliente.
5. `abrir un desplegable avisa de interacción abierta`: `onInteraccion` se llama con `true` al abrir y con `false` al cerrar.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/BarraFiltros.test.tsx
```

Esperado: FALLA con "Failed to resolve import './BarraFiltros'".

- [ ] **Step 3: Implementar**

Un `FlowPane` equivalente (contenedor con `flex-wrap`, como las barras de filtros del sub-proyecto 1): campo de IMEI, selección múltiple de técnico, selección múltiple de cliente con el centinela `SIN_CLIENTE` y todos marcados por defecto, desplegable de Tipo, desplegable de Estado, y el botón "Limpiar filtros". Reutilizar los componentes de filtro ya existentes en `src/modules/taller/` en vez de crear nuevos.

Cada desplegable llama `onInteraccion(true)` al abrirse y `onInteraccion(false)` al cerrarse: es lo que alimenta la congelación del refresco de la Task 16.

- [ ] **Step 4: Cablear en la página**

`AsignacionesPage` pasa a tener `useState<EstadoFiltros>(FILTROS_VACIOS)`, pinta `<BarraFiltros/>` y alimenta la tabla con `aplicarFiltros(datos, filtros)`. El contador de la cabecera usa ya la lista filtrada.

- [ ] **Step 5: Ejecutar y verificar**

```bash
npx vitest run src/modules/taller/asignaciones/
```

Esperado: todo en verde, incluidos los tests de la Task 8.

- [ ] **Step 6: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): barra de filtros de asignaciones"
```

---

## Task 10: Web — el aviso con "Deshacer" y los toggles de urgente y chasis

**Files:**
- Create: `src/modules/taller/asignaciones/useAccionConDeshacer.ts`
- Create: `src/modules/taller/asignaciones/MenuAsignacion.tsx`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/useAccionConDeshacer.test.tsx`
- Test: `src/modules/taller/asignaciones/MenuAsignacion.test.tsx`

**Interfaces:**
- Consumes: `useUrgente`, `useChasis` (Task 6); `tipoDe` (Task 5).
- Produces:
  - `useAccionConDeshacer()` → `{ ejecutar: (opciones: { texto: string; hacer: () => Promise<unknown>; deshacer: () => Promise<unknown> }) => void, aviso: ReactNode }`
  - `<MenuAsignacion fila={ReparacionResumen} soloLectura={boolean} onEditarComentario onEditarModelo onEditarCliente onCopiarCelda onInteraccion />`

- [ ] **Step 1: Escribir el test del hook, que falla**

Cubrir:
1. `ejecuta la acción y muestra el aviso con su texto`.
2. `pulsar Deshacer llama a la acción inversa`.
3. `el aviso desaparece solo pasados los segundos` (con temporizadores falsos de Vitest).
4. `si la acción falla no se muestra el aviso` — el error lo trata el manejador global.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/useAccionConDeshacer.test.tsx
```

Esperado: FALLA con "Failed to resolve import './useAccionConDeshacer'".

- [ ] **Step 3: Implementar el hook**

Un hook con estado local `{ texto, deshacer } | null`, un `setTimeout` de **8 segundos** para limpiarlo, y `aviso` con el texto y un botón "Deshacer" que llama a la inversa y cierra. Limpiar el temporizador al desmontar. Si ya existe un componente de aviso flotante en `src/shared/ui/`, usarlo; si no existe, crear uno mínimo aquí con las clases del sistema de diseño de la web.

- [ ] **Step 4: Escribir el test del menú, que falla**

Cubrir los ítems por categoría y rol, que son la tabla de la spec §10:
1. `en reparación salen los seis ítems`.
2. `en glass no sale chasis`.
3. `en pulido salen editar modelo y no salen urgente ni chasis`.
4. `el ADMIN solo ve copiar celda`.
5. `el texto alterna entre Marcar y Quitar según el estado de la fila`.
6. `marcar urgente llama a la mutación y deja el aviso con Deshacer`.

- [ ] **Step 5: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/MenuAsignacion.test.tsx
```

Esperado: FALLA con "Failed to resolve import './MenuAsignacion'".

- [ ] **Step 6: Implementar el menú y cablearlo**

`MenuAsignacion` decide sus ítems con `tipoDe(fila.idRep)` y `soloLectura`. Urgente y chasis pasan por `ejecutar({ texto, hacer, deshacer })`, donde `deshacer` es la misma mutación con el valor contrario. El menú llama `onInteraccion(true|false)` al abrirse y cerrarse.

- [ ] **Step 7: Ejecutar y verificar**

```bash
npx vitest run src/modules/taller/asignaciones/
```

Esperado: todo en verde.

- [ ] **Step 8: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): menu contextual de asignaciones con urgente y chasis reversibles"
```

---

## Task 11: Web — reasignar desde la celda

**Files:**
- Modify: `src/modules/taller/asignaciones/columnas.tsx`
- Test: `src/modules/taller/asignaciones/columnas.test.tsx`

**Interfaces:**
- Consumes: `useReasignar` (Task 6), `useAccionConDeshacer` (Task 10), `ComboNavy` de `@/shared/ui`.
- Produces: la columna Técnico deja de ser un hueco y pasa a ser el desplegable cableado.

- [ ] **Step 1: Añadir los tests que fallan**

En `columnas.test.tsx`, añadir:
1. `la celda de técnico es un desplegable con los técnicos activos`.
2. `elegir otro técnico llama a onReasignar con el id nuevo`.
3. `tras reasignar aparece el aviso con Deshacer, y deshacer devuelve al técnico anterior`.
4. `en solo lectura la celda es texto plano, no un desplegable`.

- [ ] **Step 2: Ejecutar y verificar que fallan**

```bash
npx vitest run src/modules/taller/asignaciones/columnas.test.tsx
```

Esperado: los cuatro nuevos FALLAN; los 7 de `badgesEstado` siguen pasando.

- [ ] **Step 3: Implementar**

La celda usa `ComboNavy` con los técnicos activos y el actual seleccionado. Al cambiar:

```tsx
ejecutar({
  texto: `Reasignada a ${nombreNuevo}`,
  hacer: () => reasignar.mutateAsync({ idRep: fila.idRep, idTec: idTecNuevo }),
  deshacer: () => reasignar.mutateAsync({ idRep: fila.idRep, idTec: idTecAnterior }),
})
```

Guardar `idTecAnterior` **antes** de disparar la mutación: tras recargar, la fila ya trae el nuevo.

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/columnas.test.tsx
```

Esperado: **11 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): reasignar desde la celda, con deshacer"
```

---

## Task 12: Web — los tres editores

**Files:**
- Create: `src/modules/taller/asignaciones/editores/EditorComentario.tsx`
- Create: `src/modules/taller/asignaciones/editores/EditorModelo.tsx`
- Modify: `src/modules/taller/asignaciones/MenuAsignacion.tsx`
- Test: `src/modules/taller/asignaciones/editores/editores.test.tsx`

**Interfaces:**
- Consumes: el selector de cliente ya existente (buscarlo con `grep -rn "SelectorCliente\|sin cliente" src/modules/`); la lista de modelos ya existente en la web (la usa el formulario del sub-proyecto 2).
- Produces:
  - `<EditorComentario abierto idRep textoInicial onCerrar />`
  - `<EditorModelo abierto idRep modeloInicial onCerrar />`

- [ ] **Step 1: Escribir los tests que fallan**

1. `el editor de comentario precarga el texto actual y guarda`.
2. `Cancelar no guarda`.
3. `el editor de modelo filtra la lista al escribir`.
4. `el editor de modelo llega con el modelo actual preseleccionado`.
5. `el editor de cliente ofrece la opción de dejarlo sin cliente`.
6. `si el teléfono cambió mientras tanto, avisa y recarga` — la mutación devuelve 409 y se muestra el mensaje.

- [ ] **Step 2: Ejecutar y verificar que fallan**

```bash
npx vitest run src/modules/taller/asignaciones/editores/
```

Esperado: FALLA al resolver los imports.

- [ ] **Step 3: Implementar**

Los dos editores nuevos son diálogos con el `ConfirmDialog`/`Dialog` ya existente. El de cliente **no se crea**: se reutiliza el del sub-proyecto 2, pasándole el IMEI de la fila.

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): editores de comentario, modelo y cliente desde asignaciones"
```

---

## Task 13: Web — el borrado

**Files:**
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/borrado.test.tsx`

**Interfaces:**
- Consumes: `useBorrarAsignacion` (Task 6), `ConfirmDialog` existente.
- Produces: el `onBorrar` que `crearColumnas` ya recibe queda cableado.

- [ ] **Step 1: Escribir el test que falla**

1. `la papelera abre la confirmación con el identificador en el título`.
2. `el texto dice que el técnico dejará de verla`.
3. `si la fila es una incidencia, el texto añade que la incidencia se marcará como no activa`.
4. `no hay campo de motivo`.
5. `Cancelar no borra; Borrar asignación llama a la mutación`.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/borrado.test.tsx
```

Esperado: los cinco FALLAN.

- [ ] **Step 3: Implementar**

Estado local con la fila a borrar; `ConfirmDialog` con título `Borrar asignación ${fila.idRep}`, descripción `El técnico dejará de verla en su lista de pendientes.` más `` y la incidencia se marcará como no activa en la tabla principal.`` cuando `fila.esIncidencia`, botón rojo "Borrar asignación".

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): borrado de asignaciones con confirmacion"
```

---

## Task 14: Web — la ventana "Carga de técnicos"

**Files:**
- Create: `src/modules/taller/asignaciones/CargaTecnicosDialog.tsx`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/CargaTecnicosDialog.test.tsx`

**Interfaces:**
- Consumes: `useCargaTecnicos` (Task 6).
- Produces: `<CargaTecnicosDialog abierto onCerrar onFiltrarPorTecnico={(idTec: number) => void} />`

- [ ] **Step 1: Escribir el test que falla**

1. `arranca en Pedidos`.
2. `el toggle a Total cambia las cifras sin volver a consultar` — `api.GET` se llama una sola vez.
3. `las filas salen ordenadas de mayor a menor carga`.
4. `sin jornada las cifras son un guion`.
5. `el tooltip lleva el desglose y omite los ceros`.
6. `pulsar una fila cierra la ventana y llama a onFiltrarPorTecnico`.
7. `si la consulta falla, se ve un mensaje en la ventana` y la tabla de fondo no se ve afectada.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/CargaTecnicosDialog.test.tsx
```

Esperado: FALLA con "Failed to resolve import './CargaTecnicosDialog'".

- [ ] **Step 3: Implementar**

Diálogo con el toggle Pedidos|Total (estado local; los dos alcances vienen en la misma respuesta), una fila por técnico con nombre, las **dos barras superpuestas en la misma escala** (la de total con color por nivel — `≥90` rojo, `≥70` ámbar, si no azul — y la de hecho en verde, más fina), las cifras y el `title` con el desglose. El texto del desglose omite los ceros y une con `·`; los dos tramos se unen con `—`; si los dos están vacíos, "sin carga de cliente" en Pedidos y "sin carga" en Total.

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/CargaTecnicosDialog.test.tsx
```

Esperado: **7 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): ventana de carga de tecnicos"
```

---

## Task 15: Web — el diálogo "Técnicos de glass"

**Files:**
- Create: `src/modules/taller/asignaciones/TecnicosGlassDialog.tsx`
- Modify: `src/modules/taller/asignaciones/api.ts`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/TecnicosGlassDialog.test.tsx`

**Interfaces:**
- Consumes: el endpoint de técnicos que ya usa la web; hace falta la mutación `useMarcarTecnicoGlass()` → `{ idTec: number; esGlass: boolean }`. Buscar su ruta exacta en `schema.d.ts` (el cliente la llama `tecnicoDAO.setGlass`).
- Produces: `<TecnicosGlassDialog abierto soloLectura onCerrar />`

- [ ] **Step 1: Escribir el test que falla**

1. `lista los técnicos activos con su estado actual`.
2. `al aceptar solo se mandan los que cambiaron`.
3. `si una llamada falla, el diálogo no se cierra`.
4. `en solo lectura los checks están deshabilitados y solo hay botón de cerrar`.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/TecnicosGlassDialog.test.tsx
```

Esperado: FALLA con "Failed to resolve import './TecnicosGlassDialog'".

- [ ] **Step 3: Implementar**

Estado local con el mapa `idTec → esGlass` inicial y el editado; al aceptar, `Promise.all` de las diferencias; si alguna rechaza, se muestra el error y **no** se cierra.

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/TecnicosGlassDialog.test.tsx
```

Esperado: **4 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): dialogo de tecnicos de glass"
```

---

## Task 16: Web — congelar el refresco mientras se interactúa

**Files:**
- Create: `src/modules/taller/asignaciones/useInteraccionesAbiertas.ts`
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/useInteraccionesAbiertas.test.tsx`

**Interfaces:**
- Consumes: nada.
- Produces: `useInteraccionesAbiertas()` → `{ hayAlguna: boolean; marcar: (abierta: boolean) => void }`

- [ ] **Step 1: Escribir el test que falla**

1. `empieza sin ninguna abierta`.
2. `marcar(true) dos veces y marcar(false) una deja una abierta` — es un contador, no un booleano: puede haber un menú y un diálogo a la vez.
3. `nunca baja de cero`.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/useInteraccionesAbiertas.test.tsx
```

Esperado: FALLA con "Failed to resolve import './useInteraccionesAbiertas'".

- [ ] **Step 3: Implementar y cablear**

```ts
import { useCallback, useState } from 'react'

/**
 * Cuenta menús, desplegables y diálogos abiertos. Mientras haya alguno, la vista no se refresca
 * (spec 3a, D4): si la tabla se recarga con un menú abierto, la fila se mueve bajo el cursor y la
 * acción se pierde o cae en otra fila. Es un contador porque pueden solaparse.
 */
export function useInteraccionesAbiertas() {
  const [n, setN] = useState(0)
  const marcar = useCallback((abierta: boolean) => {
    setN((previo) => Math.max(0, previo + (abierta ? 1 : -1)))
  }, [])
  return { hayAlguna: n > 0, marcar }
}
```

En `AsignacionesPage`, pasar `marcar` como `onInteraccion` a la barra de filtros, al menú, a los editores y a los dos diálogos, y consumir `useAsignacionesTodas({ activo: !hayAlguna })`.

- [ ] **Step 4: Ejecutar y verificar que pasan**

```bash
npx vitest run src/modules/taller/asignaciones/
```

Esperado: todo en verde.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): el refresco se congela mientras hay algo abierto"
```

---

## Task 17: Web — el modo solo lectura del ADMIN

**Files:**
- Modify: `src/modules/taller/asignaciones/AsignacionesPage.tsx`
- Test: `src/modules/taller/asignaciones/admin.test.tsx`

**Interfaces:**
- Consumes: `esAdmin` de `@/shared/session/storage`.
- Produces: `soloLectura` se propaga a columnas, menú y diálogo de glass.

- [ ] **Step 1: Escribir el test que falla**

Con sesión de ADMIN:
1. `no se ve el botón Asignar`.
2. `no se ve la columna de la papelera`.
3. `el menú contextual solo tiene Copiar celda`.
4. `la celda de técnico es texto plano`.
5. `los checks de técnicos de glass están deshabilitados`.
6. `los filtros y la ventana de carga siguen funcionando`.

- [ ] **Step 2: Ejecutar y verificar que falla**

```bash
npx vitest run src/modules/taller/asignaciones/admin.test.tsx
```

Esperado: los seis FALLAN.

- [ ] **Step 3: Implementar**

`const soloLectura = esAdmin(sesion)` en la página, propagado a `crearColumnas`, `MenuAsignacion` y `TecnicosGlassDialog`.

- [ ] **Step 4: Ejecutar la suite completa de la web**

```bash
npm run check
npx vitest run
```

Esperado: 0 errores de tipos y lint; todos los tests en verde, los nuevos y los 683 del sub-proyecto 2.

- [ ] **Step 5: Commit**

```bash
git add src/modules/taller/asignaciones/
git commit -m "feat(web): modo solo lectura del admin en asignaciones"
```

---

## Task 18: Ficha de paridad y verificación final

**Files:**
- Create: `docs/paridad/asignaciones.md` (repo web)
- Modify: `docs/superpowers/plans/2026-09-22-web-asignaciones.md` (repo raíz): sección "Ejecución y cierre"

**Interfaces:**
- Consumes: las 46 capturas y los inventarios de referencia, guardados fuera del repo.
- Produces: la ficha marcada, que es el criterio de aceptación de la spec §15.

- [ ] **Step 1: Escribir la ficha**

Una línea por comportamiento observable, agrupadas por bloque (tabla, columnas, filtros, menú, editores, borrado, carga de técnicos, técnicos de glass, ADMIN), cada una con su casilla. Referenciar las capturas **por nombre**, nunca incrustarlas: llevan datos reales del taller y el repo es público. Anotar arriba las tres decisiones que se apartan del JavaFX: sin orden por columna (D5), aviso con "Deshacer" (D2) y refresco congelado (D4).

- [ ] **Step 2: Marcarla contra las capturas**

Recorrer las capturas una a una y marcar. Cualquier diferencia nueva que aparezca **se anota y se consulta con el usuario**, no se resuelve sobre la marcha.

- [ ] **Step 3: Verificación completa de los tres repos**

```bash
# servidor
cd gestion-reparaciones-servidor && mvn -q test
# web
cd ../gestion-reparaciones-web && npm run check && npx vitest run && npm run build
# cliente JavaFX: no se ha tocado, pero se comprueba que sigue compilando
cd .. && export JAVA_HOME=/c/Users/dev/tools/jdk-17 && export PATH=/c/Users/dev/tools/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH
cd gestion-reparaciones-cliente && mvn -q test
```

Esperado: verde en los tres. El cliente debe seguir con sus 284 tests intactos.

- [ ] **Step 4: Smoke contra producción**

```bash
cd gestion-reparaciones-web
set -a; . ~/.env.e2e; set +a
npx playwright test
```

Esperado: en verde. Recordar que corre con `workers: 1` por el límite de inicios de sesión del nginx.

- [ ] **Step 5: Capturas de la web y commit de cierre**

Guardar las capturas de la vista nueva fuera del repo y cerrar el plan con una sección "Ejecución y cierre" que recoja lo que se desvió, igual que se hizo en el sub-proyecto 2.

```bash
git add docs/paridad/asignaciones.md
git commit -m "docs(paridad): ficha de asignaciones marcada contra las capturas"
```

- [ ] **Step 6: Parar y pedir OK al usuario**

**No hacer push, merge ni tag.** Presentar al usuario: qué se ha hecho, el estado de los tres repos, y la lista de pasos que requieren su OK uno a uno (push de las dos ramas, merges `--no-ff`, tag `v0.4.0`, gitlinks en el raíz, despliegue en la VM con **el servidor antes que la web**).

---

## Autorrevisión del plan

**Cobertura de la spec.** §4 rutas → Task 8. §5 servidor → Tasks 1-2. §6 estructura → Tasks 4-17. §7 tabla → Tasks 7-8. §8 estado → Task 7. §9 filtros → Tasks 5 y 9. §10 menú, editores y borrado → Tasks 10, 12 y 13. §11 las dos ventanas → Tasks 14 y 15. §12 ADMIN → Task 17. §13 diferencias → Task 18. §14 errores → cubierto por el manejador global, verificado en las Tasks 12 y 14. §15 verificación → Task 18. D4 → Task 16. D2 → Tasks 10 y 11. D5 → Tasks 4 y 7.

**Sin marcadores.** No hay "TBD", "pendiente de definir" ni pasos sin contenido. Donde el plan dice "buscar el nombre exacto en el contrato" o "copiar el patrón de tal fichero", es una instrucción verificable con una ruta concreta, no un hueco.

**Consistencia de nombres.** `ordenarPorPrioridad`, `aplicarFiltros`, `tipoDe`, `SIN_CLIENTE`, `FILTROS_VACIOS`, `badgesEstado`, `crearColumnas`, `useAsignacionesTodas`, `useCargaTecnicos`, `useReasignar`, `useUrgente`, `useChasis`, `useBorrarAsignacion`, `useAccionConDeshacer`, `useInteraccionesAbiertas` se usan con el mismo nombre en todas las tareas donde aparecen. En el servidor, `CargaTecnicos.diaDeHoy()`, `CargaTecnicosRespuesta.FilaCarga` y `DesgloseDto` son consistentes entre las Tasks 1, 2 y 3.

**Riesgo conocido del plan.** Las Tasks 7, 9, 12 y 15 dependen de nombres de componentes y de rutas del contrato que hay que **leer del código antes de escribir**. Están marcadas con esa instrucción en su primer paso. Si un nombre no coincide, manda el código existente, no este plan.
