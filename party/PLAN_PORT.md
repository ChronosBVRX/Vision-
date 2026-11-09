# Plan de porte — Vision+ Party

## Estado actual

| Fase | Estado |
|---|---|
| Banterbolt (The Merkive) | ✅ Portado: servidor autoritativo, host, controlador, contenido es-MX y 27 audios |
| ¿Le atinaste? Bien. (Wavelength) | ✅ Portado: dial, equipos, Undercut, host y 60 audios |
| El Comisionado Merk (Secret Hitler) | ✅ Portado: motor completo, roles, poderes, votos y 70 audios |
| Hub Android TV | ✅ `PartyGamesScreen` con D-pad, tarjetas grandes y WebView host |
| Despliegue web | ✅ Host y controlador en `party.visionplusv2.duckdns.org` |
| Prueba física TV + teléfonos | ⏳ Pendiente: TCL/Chromecast + 3 teléfonos por QR |

Los 10 juegos restantes ya están integrados y disponibles, con catálogo, contenido y textos de juego localizados al español, más 10 variantes de narradora por momento.

## Mecánicas portadas

### Banterbolt
- Rondas de respuestas rápidas, votación y puntaje.
- Contenido original en español mexicano: México, cine/series, after dark y mixto.
- Host con marcador y controlador de teléfono.

### ¿Le atinaste? Bien. (Wavelength)
- Dos equipos; el Oráculo ve un objetivo secreto en un espectro y da una pista.
- El equipo mueve el dial; puntos por cercanía, Bullseye y robo Undercut.
- Servidor autoritativo para objetivo, turnos y puntaje.

### El Comisionado Merk
- 5–12 jugadores con roles ocultos: Merkizens, Merkites y El Comisionado Merk.
- Nominación, voto, decretos, veto, poderes y condiciones de victoria.
- El controlador solo recibe el rol privado que le corresponde.

## Integración Android

- La pestaña **Juegos** es un hub nativo; no usa las pantallas viejas de trivia.
- `PartyGamesScreen.kt`: tarjetas con texto a 10 pies y foco D-pad.
- `PartyHostScreen.kt`: WebView con botón **Salir** y Atrás.
- La URL sale de la flag remota `party_host`; default
  `https://party.visionplusv2.duckdns.org/`.
- Al abrir con `?game=<id>`, la landing crea la sala automáticamente y entra al host.
- El QR lo genera el host web; no se reutiliza el lector de la app vieja.

## Contenido y voz

- Formato: JSON con `id`, `gameId`, `locale`, `title`, `nsfw`, `kids`, `items`.
- Voz: `party/tools/generate_party_voice_mx.py`, Qwen3-TTS Base clonando una
  referencia mexicana; dicción es-MX, no doblaje neutro.
- 10 variantes por momento (`narrator_<momento>_<0-9>.m4a`), rotación sin
  repetición dentro de la sesión.
- Los audios viven en el servidor; el APK no los empaqueta.

## Pendientes

1. Probar en TCL/Chromecast: foco inicial, D-pad, texto a 10 pies, abrir juego, Salir/Atrás.
2. Probar una partida completa con 3 teléfonos por QR en los tres juegos.
3. Revisar la calidad de la traducción de los 10 juegos nuevos en la tele y los teléfonos.
4. Verificar el APK final <150 MB y publicar.
