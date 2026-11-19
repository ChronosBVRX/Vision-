# Vision+ 2.6.65 — Bots y personajes en Vision+ Party

Actualización sobre 2.6.64.

## Vision+ Party

- El anfitrión ya puede **agregar y quitar bots desde la sala de espera**, sin esperar a que empiece la partida.
- Selector de **dificultad** de bots (Normal / Difícil / Experto) y de **personaje** por jugador.
- La sala se limpia correctamente al cambiar de juego y los bots se retiran con el jugador que los ocupaba.
- El motor aplica el input de los bots en el mismo ciclo de la ronda, con una sola transición y un solo broadcast: menos saltos y menos estados duplicados.
- Textos de sala y controlador en español; el host y el controlador ya están desplegados en `party.visionplusv2.duckdns.org`.
- El QR sigue abriendo el controlador en el teléfono.

## Se mantiene

- 4K público por HdHub (R2), con Kronos como respaldo latino/LAN.
- Caché 4K con esquema y migración automática.
- Deportes por ventana, ranker de pistas y cadena de calidad.

## Verificación

- `npm run build:hosted` del Party: correcto.
- `party-server.mjs` reempaquetado con esbuild (node22).
- Host y controlador publicados en el VPS; `/controller/` sirve el bundle nuevo.
- Android: **1,356 pruebas**, solo los **3 fallos ambientales conocidos**.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
