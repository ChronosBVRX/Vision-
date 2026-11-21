# Documentación de Vision+

## Empezar por aquí

- **`docs/INVARIANTES_REGRESION.md`** — reglas que no se pueden romper del
  reproductor, el 4K, el vivo y la agenda deportiva. Es la fuente de verdad.
- **`AGENTS.md`** — resumen operativo, entorno, estado de releases y reglas del
  proyecto.
- **`docs/REGRESSIONS.md`** — sistema anti-regresiones, tests y checklist.

## Reproductor

- `docs/HANDOFF_ESPERA_REPRODUCTOR.md` — pantalla de espera histórica; tiene un
  banner con el estado actual.
- `docs/ARQUITECTURA.md` — arquitectura e invariantes técnicas.
- `docs/HANDOFF_MAESTRO.md` — handoff histórico (sección 2 congelada); su banner
  apunta al estado actual.

## Deportes

- `docs/DEPORTES_FUENTES_ESTABLE.md` — contrato de fuentes por evento.
- `docs/MAPA_CANALES_DEPORTIVOS.md` — mapa de la parrilla XTV y cruce con ESPN.
- `docs/ANALISIS_GEE_SPORTS.md` — análisis del backend/plan de GeeSports.

## Release

- `tools/publish_ota.ps1` — bump, R8, motor inyectado, firma histórica y `gh release`.
- `ota_notes_2.6.81.md` — notas de la beta vigente (sin proveedores).
- `CHANGELOG.md` — hitos; el detalle por versión vive en `ota_notes_*.md`.

## Documentos históricos

`docs/MIGRACION_AGY.md`, `docs/HANDOFF_MAESTRO.md` y
`docs/HANDOFF_ESPERA_REPRODUCTOR.md` conservan contexto de versiones anteriores.
No tomes sus números de suite/APK como actuales: el estado vigente está en
`docs/INVARIANTES_REGRESION.md` y `AGENTS.md`.
