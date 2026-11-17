# Vision+ PC 0.6.0 — Actualización OTA

Actualización del ejecutable portable de Vision+ PC con el motor webOS 0.4.0.

- **Películas y series en 1080p**: el reproductor pide la licencia FHD al broker del VPS y usa el capítulo real de la serie (`episodeNumberList`), igual que la app Android 2.6.56.
- **Reproducción reparada**: se corrigen el proxy con Node moderno (ECONNRESET al primer byte), la selección de CDN (los balanceadores `cs` devuelven 403; se usa `goog`/`cfl`) y el relevo automático entre CDNs.
- **TV en vivo reparada**: la licencia sale de `getLiveData` (el portal retiró `startPlayLive`); cada canal abre con su señal y el relay FFmpeg sigue entregando MP4.
- **Infantil**: menús **Películas Infantiles** y **Series Infantiles** dentro de Películas/Series, con paginación y sin anime japonés.
- **Fichas en español**: sinopsis, reparto y país de TMDB; aviso de calidad real al empezar la reproducción.
- **Series**: «Sig. capítulo», capítulo anterior y selector piden el capítulo correcto.

Los datos, la sesión local, favoritos e historial se conservan.
