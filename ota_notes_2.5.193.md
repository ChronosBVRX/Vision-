# Vision+ 2.5.193 — arte en `/now-playing` (y caché correcta)

**Beta de pruebas.** Misma entrega de arte que la 2.5.192, con el encabezado de caché correcto para que el estado no se quede pegado.

## Campos nuevos
```json
{
  "artwork": "https://.../logo-o-poster.jpg",
  "artwork_type": "logo"
}
```
- **TV en vivo:** el **logo real del canal** y `artwork_type: "logo"`.
- **VOD / película / serie:** el **póster** y `artwork_type: "poster"`.
- **Sin imagen:** `artwork: ""` (cadena vacía). Nunca base64.
- La URL es **absoluta**. Las remotas se devuelven tal cual; si el arte fuera local (`file://`, `content://` o una ruta), el mismo servidor lo publica en `http://<IP>:8765/artwork/<clave>` y lo sirve por la LAN (solo recursos registrados, sin rutas arbitrarias).

## Caché
- `GET /now-playing` responde **`Cache-Control: no-store`**: el estado cambia cada segundo y ningún panel debe quedarse con una foto vieja.
- `GET /artwork/<clave>` responde **`Cache-Control: public, max-age=3600`**: la carátula sí se puede cachear.
- `HEAD` disponible en ambos.

## De dónde sale
- El **logo del canal** es el `logo` real de `LiveChannel` (el mismo que pinta la tele), incluido al cambiar de canal con el zapping.
- El **póster** es el `posterUrl` real del título (`NowPlaying.poster`), el mismo de la ficha y «Seguir viendo».
- No se inventan imágenes: si el modelo no trae una, el campo queda vacío.
- No cambia el motor, el DRM ni la reproducción.

## Verificación
- **1131 pruebas Android + 168 del launcher** en verde (`NowPlayingShareTest`: logo/póster, sin imagen, arte local servido por `/artwork/...`, `Cache-Control` de estado y de arte, token, solo LAN y cableado).
- En el Chromecast HD, con `curl http://192.168.1.124:8765/now-playing`:
  - **Vivo:** `"artwork":"https://dio3ka.lpsyujwj.xyz/public/images/…png"`, `"artwork_type":"logo"`, `channel:"La Granja VIP 1 HD"`.
  - **VOD:** `"artwork":"https://joqotx.p27i1en9.com/public/images/…"`, `"artwork_type":"poster"`, `program:"Verity: la sombra de un engaño"`, `position:9`, `duration:7020`.
  - **Idle:** `"artwork":""`, `"artwork_type":""`.

— ChronosBVRX
