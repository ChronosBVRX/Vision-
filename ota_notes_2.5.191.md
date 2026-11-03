# Vision+ 2.5.191 — «Qué está reproduciendo» en la red local

**Beta de pruebas.** Vision+ anuncia por mDNS/Bonjour lo que está reproduciendo y lo sirve por HTTP de solo lectura, para que cualquier equipo de la casa (Home Assistant, un panel, un script, `curl`) lo consulte sin webhooks ni integraciones especiales.

## Anuncio mDNS
- Tipo: **`_visionplus._tcp`**
- Nombre: **`Vision+ - <nombre del dispositivo>`**
- Puerto: **8765** (si está ocupado, se usa uno libre y se anuncia el real)
- TXT: **`app=Vision+`**, **`version=1`**, **`path=/now-playing`**
- Se vuelve a registrar cuando cambia la red o la IP (Wi-Fi nueva, DHCP, reconexión).

## HTTP de solo lectura
`GET http://<IP_DEL_TV>:8765/now-playing`

```json
{
  "app": "Vision+",
  "device": "TV Lalo",
  "state": "playing",
  "channel": "Azteca 1 HD",
  "program": "Al Extremo",
  "description": "",
  "position": 0,
  "duration": 0,
  "updated": 1793309219283,
  "kind": "live",
  "live": true,
  "title": "Azteca 1 HD",
  "subtitle": "En vivo · Al Extremo · México"
}
```

- `state`: `playing`, `paused` o `idle`.
- `channel`/`program`/`description`: del **estado real del reproductor y la EPG**; si la guía no tiene datos, se dejan vacíos (no se inventan).
- `position`/`duration`: **segundos** (0 en vivo).
- `updated`: epoch en milisegundos del último cambio.
- Solo responde `GET /now-playing` (y `HEAD`); no hay ninguna ruta de control.
- Acepta únicamente clientes de la LAN (privada, loopback o link-local); una IP pública recibe 403.
- Token opcional: si se configura, exige `?token=...` o la cabecera `X-Vision-Token`. Por defecto está vacío para que `curl` funcione.

## Sin tocar la reproducción
- No se modifica el motor, el DRM, el zapping ni la lógica de reproducción. El servidor solo lee el estado que el reproductor ya publica.
- Un **servicio en primer plano** (`dataSync`) mantiene el anuncio y el HTTP vivos si la app queda en segundo plano (por ejemplo, con el reproductor al frente). No bloquea el player.

## Verificación
- **1130 pruebas Android + 168 del launcher** en verde (`NowPlayingShareTest` incluye JSON puro, servidor HTTP real, 404, token, solo LAN y cableado).
- En el TV (Chromecast HD): `adb logcat` mostró `mDNS listo: Vision+ - TV Lalo _visionplus._tcp:8765` y el servicio en primer plano.
- Desde otro equipo: `avahi-browse -rt _visionplus._tcp` devolvió el servicio con `port=8765` y los TXT.
- `curl` validado en vivo, pausa y VOD:
  - vivo: `channel=Azteca 1 HD`, `program=Al Extremo`, `state=playing`.
  - pausa: `state=paused`.
  - VOD: `program=La La Land`, `position=118`, `duration=7680`.

— ChronosBVRX
