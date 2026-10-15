# Vision+ v2.5.73 — Alta automática en el portal (réplica Hydra completa)

## Qué trae
- **Cada instalación acuña SU sesión de visitante al arrancar** (`mintOwnSlot`):
  `v3/snToken` virgen → `SN = MD5(snToken + SALT)` → `v8/active` → slot propio
  guardado. Cero pool, cero códigos, cero upstream para el caso común.
- Orden: slot local → reclamo → **auto-alta (gratis)** → pool del operador (respaldo).
- La sal es la compartida de la familia (`ntFT65…D28`, idéntica en Xuper/Hydra/Crystal).

## Verificado contra el portal (no adivinado)
- Sesión visitante nueva (`userId 969423096`, `identity=1`): columnas (10 repisas),
  ficha y **licencia VOD real de Moana** (mismo playCode que la cuenta principal).
- `active` exige el `sn` derivado (sin él → 100059); vector MD5 en tests.

## Guardianes
- 302 tests Android + 101 backend en verde (`derivedSnMatchesThePortalVector`).
