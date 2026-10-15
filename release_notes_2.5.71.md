# Vision+ v2.5.71 — Pool de identidades (réplica del modelo Hydra)

## Qué trae
- **Identidad propia por equipo (pool + canje por código)**: el tenant no incorpora
  dispositivos nuevos (verificado contra el portal: ni SN virgen ni huella completa
  de 21 campos obtienen `sn`/`userId`), así que el operador carga los SN
  aprovisionados y cada instalación canjea UN código por SU slot. Un slot = una
  sesión = nadie patea a nadie.
- **Sin slot, todo igual que antes**: la identidad compartida sigue por defecto;
  ningún equipo existente cambia de comportamiento al actualizar.
- **Worker con slot propio**: el sync lee `device_slots(status='worker')` y deja de
  patear a la app en cada ciclo.
- **Ajustes → Identidad del equipo** (móvil y TV): muestra Compartida/Propia y
  canjea el código del operador. Reclamo silencioso tras reinstalar.

## Investigación Hydra (APK com.msandroid.mobile v6.5.7, descompilado)
- Sin SN fijo en el APK: `SnTokenBean` de 21 campos de huella (serial, MACs,
  android_id, CID eMMC, build…); el portal responde `{snToken, isNew, userId, sn}`.
- Flujo `v3/snToken → v6/active → v6/login`, visitante por defecto, `v5/heartbeat`,
  SN persistido en prefs + archivos + `.wav` en MediaStore (sobrevive reinstalos).

## Operador (pendiente de slots del upstream)
1. `POST /v1/slots/issue` (X-Admin-Key) `{"sn":…}` → `{"code":"…"}` por usuario.
2. `POST /v1/slots/worker` con el SN del worker.
3. El usuario canjea en Ajustes → Identidad del equipo.

## Guardianes
- 301 tests Android + 98 backend en verde (nuevos: `DeviceSlotTest`,
  `X4KApiSlotTest`, `SlotStoreTest`, `server/tests/test_slots.py`).
