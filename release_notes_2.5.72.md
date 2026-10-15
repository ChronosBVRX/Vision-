# Vision+ v2.5.72 — Auto-asignación de identidad (cero códigos)

## Qué trae
- **El equipo toma su slot solo al arrancar** (`POST /v1/slots/auto`): con asignación
  previa la devuelve (reinstalación: no consume nada); si no, entrega el slot libre
  auto-entregable más viejo (transacción `SKIP LOCKED`: dos equipos a la vez jamás
  comparten slot). Sin libres → se sigue con la compartida, en silencio.
- El canje por código queda como respaldo (Ajustes → Identidad del equipo).
- El operador reserva slots con `auto=false` (manuales/worker) al emitirlos.

## Operador
- La columna `auto` (default true) y `created_at` se agregan solas al arrancar el sync.
- Sin pool libre no pasa nada visible: el equipo sigue compartido hasta que cargues más.

## Guardianes
- 301 tests Android + 101 backend en verde (`auto_decision`: existente/libre/agotado).
