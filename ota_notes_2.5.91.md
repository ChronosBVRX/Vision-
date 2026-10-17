# OTA v2.5.91 — Red de seguridad de actualizaciones y vigilancia externa

## Qué trae la app
- **Actualización requerida (`min_version_code`)**: si el servidor marca una versión
  mínima, los equipos viejos piden actualizar en cada apertura y no pueden saltarse el
  aviso («skipped» se ignora) — el operador sube un número y no publica nada.
- **Pausar OTA (`ota_paused`)**: si un build sale mal, se deja de ofrecer la
  actualización a todo el mundo al instante (kill-switch en el servidor).
- La campana, el aviso automático y el flujo de descarga in-app siguen igual; esto
  solo agrega la política (pura y probada) por encima.

## Qué trae la operación (sin cambio visible en la app)
- **Vigilante externo de uptime** (GitHub Actions, corre FUERA del VPS): si
  visionplusv2.duckdns.org o la20.com.mx/catalogo dejan de responder, llega alerta al
  Telegram. El vigilante interno del VPS sigue reiniciando la API si se cuelga.
- **Respaldo off-site semanal**: los domingos el dump viaja a Telegram (copia fuera
  del disco del VPS).
- **Verificación semanal de restauración**: cada domingo se restaura el respaldo en
  una base temporal y se reporta el resultado al Telegram (heartbeat con títulos
  contados) — si el respaldo no restaura, se sabe ese mismo día.
- **Publicación en un comando**: `tools/publish_ota.ps1` (bump + tests + build firmado
  + verificación de firma histórica + release), con restauración automática si algo falla.

## Verificación
- 433 tests Android + 170 backend en verde.
- Firma histórica `9593cb0d…`, versionCode 327.
- Forzada probada en vivo contra el servidor (flag real puesto y quitado).

— ChronosBVRX
