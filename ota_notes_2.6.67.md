# Vision+ 2.6.67 — El 4K también llega a las fichas del launcher

Actualización sobre 2.6.66.

## Qué se corrigió

- Las fichas abiertas desde un **deep link** (`visionplus://title/...`, por ejemplo desde
  Vision+ Home o una notificación) llegaban sin IMDb y sin `kind`. El resolvedor las
  saltaba y el badge nunca aparecía.
- El VPS ahora resuelve el IMDb por **título y año** (TMDB y, si falla, Cinemeta) cuando
  la app no lo tiene. La app manda `tmdb`, `title`, `original` y `year`, así que los
  títulos localizados («Spider-Man: Un nuevo día») también encuentran su ficha.
- La ficha y el reproductor aceptan `kind` vacío como película: el badge se muestra y el
  Play usa la fuente 4K en el mismo instante.
- La ficha entrega a Play un episodio sintético con el contentId de la película; el 4K
  ahora también se activa en ese camino (antes solo cuando no había episodio).

## Audio en español

- El reproductor elige automáticamente la pista **español latino** (o castellano) cuando
  el archivo la trae. Si no existe, usa el audio original inglés; **nunca arranca en
  hindi** solo porque sea la primera pista del archivo.
- El resolvedor y el VPS ordenan primero las fuentes 4K etiquetadas como **Latino** o
  **Español**; el resto queda como respaldo.
- La selección manual de audio del menú sigue funcionando igual.

## Se mantiene

- Orden de fuentes: VPS → HdHub directo → Kronos (LAN/latino) → calidad nativa de Xuper.
- Badge «4K · ULTRA HD» con el azul de Vision+, solo cuando hay una pista sana en caché.
- Caché con esquema 3, preflight real de 1 KB y vencimiento de las URLs firmadas de R2.

## Verificación

- Emulador: *La Odisea* (2026) abierta por deep link muestra el badge y resuelve
  **3 fuentes 4K** desde el VPS.
- Reproducción 4K comprobada en el emulador con una pista H.264 4K: el video se ve y
  el reproductor elige la pista **español latino** (estéreo) por encima de la inglesa.
- El decodificador del emulador no soporta **HEVC Main10**, el códec de los 4K de
  HdHub; ahí el reproductor cae a la calidad nativa. Los equipos 4K con Main10
  reproducen la fuente directa.
- Servidor: `GET /v1/4k` por título responde 6 pistas para *La Odisea* y *Spider-Man:
  Un nuevo día*, con el idioma detectado y el español primero.
- Android: compilación correcta y **1,363 pruebas**; solo los **3 fallos ambientales conocidos**.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
