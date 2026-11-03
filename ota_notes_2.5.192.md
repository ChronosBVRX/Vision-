# Vision+ 2.5.192 — arte del canal/póster en `/now-playing`

**Beta de pruebas.** El JSON que Vision+ sirve en la red local ahora incluye la carátula de lo que está reproduciendo, para que un panel o Home Assistant pueda pintarla sin integraciones extra.

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
- La URL es **absoluta**. Las remotas (TMDB, catálogo) se devuelven tal cual; si el arte fuera local (`file://`, `content://` o una ruta), el mismo servidor lo publica en `http://<IP>:8765/artwork/<clave>` y lo sirve por la LAN (solo recursos registrados, sin rutas arbitrarias).

## De dónde sale
- El **logo del canal** es el `logo` real de `LiveChannel` (el mismo que pinta la tele), incluido al cambiar de canal con el zapping.
- El **póster** es el `posterUrl` real del título (`NowPlaying.poster`), el mismo de la ficha y «Seguir viendo».
- No se inventan imágenes: si el modelo no trae una, el campo queda vacío.
- No cambia el motor, el DRM ni la reproducción.

## Verificación
- **1131 pruebas Android + 168 del launcher** en verde (`NowPlayingShareTest`: logo/póster, sin imagen, arte local servido por `/artwork/...`, token, solo LAN y cableado).
- En el Chromecast HD, con `curl http://192.168.1.124:8765/now-playing`:
  - **Vivo:** `"artwork":"https://dio3ka.lpsyujwj.xyz/public/images/…png"`, `"artwork_type":"logo"`, `channel:"La Granja VIP 1 HD"`.
  - **VOD:** `"artwork":"https://joqotx.p27i1en9.com/public/images/…"`, `"artwork_type":"poster"`, `program:"Verity: la sombra de un engaño"`, `position:9`, `duration:7020`.
  - **Idle:** `"artwork":""`, `"artwork_type":""`.
- El arte local se validó con un SVG temporal servido por el propio `/artwork/...` (misma respuesta 200 y contenido).

— ChronosBVRX
