# Registrar revisión logística (botón OK) en el log de auditoría — Design

**Fecha:** 2026-06-22
**Estado:** Aprobado, pendiente de plan

## Resumen

El botón "OK" de revisión logística (toggle por IMEI en la columna Revisión del supertécnico) hoy actualiza `Telefono.REVISION_LOGISTICA` pero **no deja rastro en el log de auditoría**. Se añade el registro del marcado y desmarcado con dos acciones legibles: `MARCAR_REVISION` y `QUITAR_REVISION`.

## Contexto y decisión de alcance

Se evaluaron tres mejoras para este botón:
1. **Logging** — no existe. Bajo coste, alto valor (trazabilidad: quién marcó/desmarcó qué IMEI y cuándo).
2. **Control de concurrencia** (lock optimista) — no existe; `Telefono` ni siquiera tiene `UPDATED_AT`. Descartado: es un booleano idempotente, ya hay un guard de negocio ("tiene asignaciones activas" → 409) y el polling de 60s reconcilia; montar `UPDATED_AT` + locks sería sobre-ingeniería.
3. **Inmediatez** (push en tiempo real) — descartado: el polling de 60s ya refresca, push real no compensa para un check.

**Decisión:** implementar **solo el logging**. El log convierte el "pisado silencioso" entre supertécnicos en algo trazable (queda registrado quién hizo cada cambio), cubriendo la preocupación de concurrencia sin tocar BD ni locks.

## Estado actual

- `TelefonoController.actualizarRevisionLogistica` (servidor): valida `tieneAsignacionesActivas` (409 si las hay) y llama `dao.actualizarRevisionLogistica(imei, revisado)`. **No** registra log. El controller inyecta `TelefonoDAO` + `ImeiLookupService`, **no** `LogDAO`.
- `TelefonoDAO.getModelo(imei)` existe (devuelve el modelo del teléfono).
- Patrón de referencia: `MARCAR_URGENTE` en `ReparacionController` registra `logDao.insertar(principal.getIdUsu(), "MARCAR_URGENTE", detalle)`.

## Cambios

### Servidor — `TelefonoController`
- Inyectar `LogDAO` en el constructor (añadir campo y parámetro).
- En `actualizarRevisionLogistica`, añadir `@AuthenticationPrincipal UsuarioPrincipal principal`.
- Tras `dao.actualizarRevisionLogistica(imei, req.revisado())`, registrar:
  - Acción: `req.revisado()` → `"MARCAR_REVISION"`; si no → `"QUITAR_REVISION"`.
  - Detalle: `"IMEI: " + imei + ", MODELO: " + dao.getModelo(imei)`.
  - Llamada: `logDao.insertar(principal.getIdUsu(), accion, detalle)`.
- El log se registra solo si la operación procede (después del guard de asignaciones activas).

### Cliente — `LogController`
- Añadir `"MARCAR_REVISION"` y `"QUITAR_REVISION"` a la lista `TIPOS_ACCION` (dropdown de filtro por acción de la vista de logs), para poder filtrarlas.

## Alcance

- Servidor (registro del log) + cliente (dos entradas en el dropdown de filtro).
- **Sin** cambios de BD, **sin** tocar concurrencia ni inmediatez.

## Testing

- Servidor: tras marcar/desmarcar el OK de un IMEI, comprobar en `Log_Actividad` una fila con `ACCION = MARCAR_REVISION`/`QUITAR_REVISION`, el usuario correcto y `DETALLE` con IMEI y MODELO.
- Cliente (manual): en la vista de logs, el dropdown de acción ofrece `MARCAR_REVISION` y `QUITAR_REVISION` y filtra correctamente.
- Regresión: el botón OK sigue funcionando igual (toggle, guard de asignaciones activas, refresco).

## Despliegue

Recompilar y desplegar servidor y cliente juntos. Sin migración de BD.
