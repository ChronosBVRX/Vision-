# Vision+ 2.5.180 (pruebas) — Juegos visibles en la tele + «Ahora en la tele»

**Canal beta. No es la OTA estable.** Corrige el acceso a Vision+ Play en la tele y conserva
todo lo de la 2.5.179: juegos con la tele como anfitriona y el control remoto con estado real.

## Lo que corrige esta versión

- En la 2.5.179 el acceso a Juegos se agregó al riel lateral, pero la app de tele ya usa la
  **barra superior** como navegación: el riel quedó sin uso y Juegos era inalcanzable.
- Ahora **«Juegos» está en la barra superior** de la tele (entre Deportes y Películas), con su
  etiqueta visible, no solo un icono.
- También aparece como **acceso rápido en el Inicio**, en la fila «Colecciones y Especiales».
- La pantalla de juegos se abre con el mando sin pasar por el launcher.

## Vision+ Play — Fase 2 (incluido)

- La tele crea la sala y muestra QR + código de 6 caracteres.
- El primer teléfono que entra es el anfitrión.
- La pregunta espera a que todos respondan; el anfitrión avanza cuando todos pulsan
  **Continuar**.
- Pantalla de TV con jugadores, pregunta, reveal y podio.
- Escáner QR y código manual en el teléfono; narradora y sonidos.
- Backend `/v1/game/*` desplegado; 200 preguntas.

## Control remoto — «Ahora en la tele» (incluido)

- Póster, título, temporada/capítulo, minuto, duración y barra arrastrable.
- Play/pausa, ±10 s, capítulo anterior/siguiente y saltos absolutos.
- En vivo: **EN VIVO**, programa actual de la EPG y cambio de canal.
- Audio y subtítulos abren el selector de la tele.
- Barra flotante persistente en el resto de la app del móvil.
- Funciona con el motor nativo XTV y con ExoPlayer (deportes, M3U y descargas).

## Evidencia

- Android: **1063 tests**, 0 fallos.
- Release R8 compilado y alineado, con los 3 DEX del motor inyectados.
- APK y launcher firmados con la llave histórica `9593cb0d…`.

## Cómo probar los juegos en la tele

1. Abrir **Vision+** (la app principal, no Vision+ Home).
2. En la barra superior, elegir **Juegos**.
3. La tele muestra el QR y el código de 6 caracteres.
4. En el teléfono: menú lateral → **Juegos** → escanear el QR o escribir el código.
5. El primer teléfono es el anfitrión y pulsa **Iniciar partida**.

También se puede abrir directo con
`visionplus://open/games`.

— ChronosBVRX
