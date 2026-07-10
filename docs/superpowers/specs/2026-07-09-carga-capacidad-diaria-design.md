# Carga de técnicos v2 — % sobre capacidad diaria

Fecha: 2026-07-09
Estado: **DISEÑO APROBADO por el usuario (brainstorm completo en sesión)** — pendiente de plan e implementación **DESPUÉS de cerrar la F2a** (decisión D16). Evoluciona la ventana "Carga de técnicos" construida en `feature/carga-unidades` (spec base: `2026-07-08-por-cerrar-carga-design.md` §3).

---

## 1. El modelo: fracciones de jornada

El % deja de ser cuota del reparto entre técnicos y pasa a medir **cuánto de SU jornada de hoy consume el trabajo de cada técnico** (modelo de carga por fracción de jornada):

- Cada trabajo consume una fracción del día según su tipo: **chasis = 1/8** de jornada, **glass = 1/17**, **normal = 1/X** (X pendiente de derivar de la BD, ver §5). Ejemplo: 4 chasis + 5 glass = 4/8 + 5/17 ≈ 79% del día.
- **% del día = (completadas HOY + asignaciones abiertas) en fracciones ÷ jornada de hoy.** Puede superar el 100% (raro pero posible); la barra satura en 100% y el número sigue ("112%").
- **Por cerrar** = 8,3% del tiempo de su tipo (misma semántica 0,083 actual, aplicada a la fracción). **Solicitud de pieza pendiente libera carga** (consume 0 hasta recibir), como hoy.
- **Pulido NO cuenta** (decisión A5): quien pule, ese día solo pule — se gestiona al asignar ("a él no, que hoy solo pule"). No "arreglar" esto sin revisar la decisión.
- El backlog cuenta: una asignación pendiente de ayer es trabajo de hoy (A2).

## 2. Jornadas (horas por día de semana)

| Día | Horas |
|---|---|
| Lunes, Martes | 9 |
| Miércoles, Jueves | 8 |
| Viernes | 6 |
| Sábado, Domingo | 0 (sin jornada) |

- Los topes (8 chasis, 17 glass, X normales) están definidos sobre la **jornada de 9h**; el resto de días **escala lineal** (miércoles: 8/9 → 7,1 chasis; viernes: 6/9 → 5,3).
- **Día sin jornada** (capacidad 0): barra vacía con "sin jornada hoy", sin %; las unidades de carga sí se muestran.

## 3. Dos vistas con toggle (SOLO en la ventana)

- Toggle **Pedidos | Total** en la ventana "Carga de técnicos" (mismo patrón que el selector Reparaciones|Glass|Pulidos del Historial). **Pedidos es la vista por defecto** (es lo que urge y lo más mirado); Total (todo el trabajo, con o sin cliente) es la adicional — útil cuando los pedidos no aprietan.
- **Identidad de vista = PUNTO de color fijo** junto al %: **azul = Total, violeta = Pedidos**. El punto NUNCA cambia de color (identidad, no nivel). Los dos cálculos salen del mismo motor con distinto filtro.
- **Modal de asignación: SIN toggle.** Junto a cada técnico, fijo, SOLO el porcentaje de Pedidos con su punto violeta: `Juan (●62%)` (decisión del usuario 2026-07-10 en smoke; originalmente iban los dos — el Total queda solo en la ventana de carga).

## 4. Presentación

- **Cifra principal = % del día**, con **barra y número siempre sincronizados en color por nivel**: azul (<70%), ámbar (70–89%), rojo (≥90%, incluido >100%). El texto usa el paso OSCURO del mismo color (legibilidad sobre blanco); la barra el tono vivo. Verde/ámbar/rojo quedan reservados para nivel — nunca para identidad.
- **Barra en dos tramos** (A1): lo completado hoy (sólido) + lo pendiente asignado (tono más claro del mismo color de nivel).
- **Hover**: unidades de carga + desglose (el % relativo actual — cuota del total — se **jubila**, C13).
- En el modal, cada % se tiñe según su propio nivel; el punto mantiene su identidad.

## 5. Tope de normales/día — derivado de la BD (pendiente, se hace junto al usuario)

- **Método acordado**: reparaciones `R%` completadas por técnico y día desde el **2026-06-30** (ventana corta pero fiable: desde entonces todos los técnicos registran TODO en el programa; ampliable si hace falta). Días "casi puros" de normales (pureza ≥80%: normales/(normales+glass+pulidos)), tasa normalizada por las horas de ese día (§2), y **mediana** (inmune a extremos) × 9h = tope de jornada larga.
- La mediana se calcula fuera de SQL (el usuario ejecuta la query y pega resultados). Query preparada:

```sql
-- Producción por técnico y día desde que todos registran en el programa.
-- Con esto: filtrar pureza >= 0.8, dividir NORMALES entre las horas del día
-- de la semana (L-M 9, X-J 8, V 6) y tomar la MEDIANA de la tasa; tope = mediana × 9.
SELECT r.ID_TEC, t.NOMBRE, DATE(r.FECHA_FIN) AS DIA, DAYOFWEEK(r.FECHA_FIN) AS DIA_SEMANA,
       SUM(r.ID_REP LIKE 'R%') AS NORMALES,
       SUM(r.ID_REP LIKE 'G%') AS GLASS,
       SUM(r.ID_REP LIKE 'P%') AS PULIDOS
FROM Reparacion r JOIN Tecnico t ON t.ID_TEC = r.ID_TEC
WHERE r.FECHA_FIN >= '2026-06-30'
  AND (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%' OR r.ID_REP LIKE 'P%')
GROUP BY r.ID_TEC, t.NOMBRE, DATE(r.FECHA_FIN), DAYOFWEEK(r.FECHA_FIN)
ORDER BY r.ID_TEC, DIA;
```

- Excluir del cálculo a los técnicos de solo-glass.

**RESULTADO de la derivación (ejecutada con el usuario el 2026-07-09, sobre asignaciones cerradas por día — la query de arriba refinada a `A%` cerradas con split por `ES_CHASIS`):**
- **Método corregido en vivo**: los topes 8/17 del usuario son TECHOS ("si solo haces eso"), no medianas — así que el tope de normales se fijó por el mejor día real escalado, no por la mediana (la mediana solo como referencia).
- Glass validado como techo: javi (solo-glass), mediana ≈12/día-9h, **mejor día 16 en 8h → 18 escalado**, coherente con el techo 17.
- Normales: mediana ≈9,5/día-9h; **mejor día: marcos, 18 asignaciones normales en jornada de 8h el 2026-07-08 (era del flag ES_CHASIS, ese día marcó 1 chasis aparte) → 20,25 escalado**; osorio 17 en 8h → 19,1. Primero se fijó 20 (mejor día redondeado); **DECISIÓN FINAL del usuario (mismo día): `TOPE_NORMALES_9H = 25`, por criterio de taller** — la ventana de datos era corta (~8 días laborables) y estima el techo real por encima del mejor día observado.
- Chasis: sin días puros suficientes para validar; se mantiene el 8 medido por el usuario (revisar con más datos).
- Nota metodológica: la primera query (filas `R%`/`G%`) contaba COMPONENTES (una fila por pieza, javi salía a ~32 glass/día) — descartada; la unidad correcta es la asignación cerrada, la misma que usa el modelo.

## 6. Parámetros y datos

- **Topes y jornadas = constantes en código** (D14), con **documentación obligatoria en el código de de dónde sale cada número y por qué** (8 chasis/día y 17 glass/día: medición del taller sobre jornada de 9h, 2026-07-09; X normales: derivación §5). **Futuro apuntado**: moverlos a tabla de BD configurable si hace falta ajustarlos sin release.
- **Topes globales** para todos; futuro (F4, con más datos): topes dinámicos por técnico según su rendimiento (B9).
- **Servidor SÍ se toca** (D15): endpoint nuevo aditivo "completadas hoy por técnico" (para el tramo sólido de la barra) — probablemente en `ReparacionController`. Sin solape de ficheros con F2a (ramas independientes; merge final sin conflictos esperados).

## 7. Secuencia (D16)

1. Merge de `feature/carga-unidades` (unidades + hover, ya aprobado) tras smoke del usuario.
2. **Cerrar F2a** (quedan T10-T17, incluida la vista IMEIs).
3. Esta pieza: writing-plans + rama propia (`feature/carga-capacidad`) + derivación del tope de normales con el usuario (§5).
