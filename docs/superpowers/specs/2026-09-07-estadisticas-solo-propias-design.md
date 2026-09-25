# Estadísticas: Técnico y SuperTécnico ven solo las suyas (0.16.2)

Fecha: 2026-09-07
Estado: **APROBADA por el usuario (brainstorm en sesión, 2026-09-07).** Cambio pequeño de cliente; una tarea.
Línea: hotfix `hotfix/0.16.2`. Rama **`feature/estadisticas-solo-propias`** desde `hotfix/0.16.2`, independiente de `feature/glass-prediccion` (no comparten ficheros). Servidor: **sin cambios**.

---

## 1. Contexto

- La vista Estadísticas (`EstadisticasController`) distingue hoy tres niveles: **Técnico** ve solo su serie y sus tarjetas ("tú"), sin desplegable de técnicos y sin clic en la leyenda, con Equipo, Promedio e "IMEIs típicos" como referencia anónima del equipo (diseño de la ronda 2, spec `2026-09-02-estadisticas-puntos-ronda2-design.md` §"El excluido en su propia vista"); **SuperTécnico** ve lo mismo que el Admin salvo los botones ⚙ Valores y 👥 Técnicos en estadísticas; **Admin** lo ve todo.
- Decisión del usuario (2026-09-07): el SuperTécnico pasa a ver **solo lo suyo**, exactamente como el Técnico. Solo el Admin ve las estadísticas de todos.
- La restricción es, hoy y para el Técnico también, **solo de cliente**: los endpoints `GET /api/reparaciones/estadisticas` y `/estadisticas/puntos` devuelven las filas de todos los técnicos a cualquier usuario autenticado. Ver §4.

## 2. Comportamiento

1. **Técnico y SuperTécnico**: solo su propia serie en el gráfico y sus tarjetas personales ("Puntos · mes · tú", "Puntos · hoy · tú"); sin desplegable de técnicos; sin clic en la leyenda para quitar a nadie; la navegación al Historial desde un punto de la serie solo en la suya. **Conservan** Equipo (suma), Promedio e "IMEIs típicos por técnico" como referencia anónima del equipo (decisión A del brainstorm).
2. **Admin**: sin cambios (todos los técnicos, tarjetas de equipo, ⚙ Valores, 👥).
3. Pestaña Stock y el resto de la aplicación: sin cambios.
4. **Caso límite**: un SuperTécnico sin técnico asociado (`Usuario.ID_TEC` nulo) vería solo Equipo y Promedio, igual que le ocurriría hoy a un Técnico sin técnico asociado. Los usuarios reales de ese rol tienen técnico.

## 3. Cambio en el cliente

`gestion-reparaciones-cliente/src/main/java/com/reparaciones/controllers/EstadisticasController.java`: las cinco decisiones de rol que hoy usan `Sesion.esAdminOSuperTecnico()` pasan a `Sesion.esAdmin()` (líneas al tip `5003df7`; reverificar por texto):

- `cargarTecnicos` (`:388`): montar el desplegable solo para Admin; para el resto, seleccionar el propio nombre y ocultar el menú.
- leyenda del gráfico (`:688`, `puedeQuitar`): quitar técnicos con clic solo Admin.
- puntos de la serie (`:971`, `navegable`): navegar al Historial desde cualquier serie solo Admin; el resto solo desde la suya.
- `limpiarFiltros`/arranque limpio (`:1202`): el no-admin vuelve siempre a verse solo a sí mismo.
- `cargarTarjetas` (`:1455`, `tecnico`): tarjetas personales para el no-admin.

Comentarios de esas líneas actualizados ("solo ADMIN"). Nada más cambia: ⚙ Valores y 👥 ya eran solo de Admin (`initialize`, `:182-185`).

## 4. Fuera de alcance, y compromiso a futuro

- **Filtrado en el servidor: pendiente y necesario.** Hoy la restricción vive solo en el cliente (como la del Técnico desde la ronda 2): un cliente manipulado o una llamada directa a la API obtiene las cifras de todos. Cuando se haga la autorización por rol en el servidor (Fase 3 del plan futuro, §"Seguridad"), los dos endpoints de estadísticas deben devolver a un no-ADMIN **solo sus filas más los agregados de equipo por periodo** (suma de puntos, número de técnicos que cuentan e IMEIs) calculados en el servidor sobre los técnicos con `ES_ESTADISTICA = 1`, para que Equipo, Promedio e IMEIs típicos sigan funcionando sin que viajen las cifras individuales de los demás. El cliente pasará entonces a consumir esos agregados en vez de calcularlos. Apuntado en `Apuntes/plan-futuro.md` (§Seguridad) y en la memoria de seguridad del proyecto.
- Ocultar Equipo/Promedio a los no-admin (decisión B, descartada).

## 5. Pruebas

- Sin test unitario (cinco condiciones de UI). Suite del cliente verde.
- Smoke (preproducción): entrar como `supertest` → Estadísticas sin desplegable de técnicos, solo su línea, tarjetas "tú", Equipo y Promedio marcables, IMEIs típicos visible, clic en la leyenda sin efecto, clic en un punto de su serie navega al Historial. Entrar como Admin → todo como antes (desplegable, tarjetas de equipo, ⚙, 👥). Entrar como Técnico → sin cambios.
- `CHANGELOG.md` `[Unreleased]` → Changed: "Estadísticas: el SuperTécnico ve solo sus propias estadísticas (como el Técnico); solo el Admin ve las de todos."

## 6. Entregables

Rama `feature/estadisticas-solo-propias` (un commit de código + CHANGELOG); merge `--no-ff` a `hotfix/0.16.2` solo con OK del usuario; entra en la release 0.16.2.
