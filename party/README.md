# Vision+ Party

Subsistema de juegos de fiesta de Vision+, construido sobre **Open Party Lab** (Apache 2.0) y adaptado al español mexicano, a la identidad visual de Vision+ y a la narradora local Qwen3-TTS.

## Estado

| Pieza | Estado |
|---|---|
| **Banterbolt** | ✅ v1: servidor, host, controlador, contenido es-MX y voz nueva |
| **¿Le atinaste? Bien.** (Wavelength) | ✅ v1: servidor, host, dial táctil, equipos y Undercut |
| **El Comisionado Merk** (Secret Hitler) | ✅ v1: motor completo, tablero, roles, poderes y votos |
| **Los otros 10 juegos de Open Party Lab** | ✅ Mímica en llamas, Garabato mental, Cifras salvajes, El Infiltrado, Palabra maldita, Sombra fugitiva, Dulce puntería, Hexagonia, Baraja brava y Héroes del sótano; assets, 13 portadas, 7 fondos ambientales y 630 audios nuevos |
| **Interfaz Android TV** | ✅ Hub nativo con D-pad, texto a 10 pies y WebView del host |
| **Assets** | ✅ Portadas WebP, assets de juego y maestros en `/media/chronos/Disco Duro/vision-assets/party-games` |
| **Selección premium** | ✅ 13 portadas 600×800 generadas en local con ComfyUI + Juggernaut XL; maestros en `party-games/ai-covers`, encabezado «Selección premium» y tarjetas con foco D-pad |
| **Optimización de hardware** | ✅ Modo ligero automático en TV/equipos de 2 GB: menos efectos, 30 FPS, `prefers-reduced-motion`, prioridad de render del WebView y caché de assets |
| **Sala Smart TV unificada** | ✅ QR, código, configuración/packs, reglas y jugadores en una sola pantalla; D-pad y controles web |
| **Despliegue web** | ✅ `https://party.visionplusv2.duckdns.org/` (host + controlador + audio) |
| **Bienvenida de la selección** | ✅ 8 tomas Qwen3-TTS en `res/raw/party_welcome_*.m4a`, sin repetir la última; metadata de jugadores, tiempo y descriptor en cada tarjeta |
| **Transición y salas** | ✅ Entrada animada con audio, carga nativa “Preparando la sala”, sala bloqueada a un solo juego y lobby sin dashboard |
| **APK** | ✅ `VisionPlus.apk` 89,434,263 B, SHA-256 `d14dfbfe…`, 5 DEX, firma histórica |
| **Prueba física TV + teléfonos** | ✅ Transición, carga, lobby bloqueado, QR, D-pad y salida verificados en Chromecast HD; falta la noche completa con 3 teléfonos |

Verificado en local: `typecheck`, build de producción y smoke de los **13/13** juegos en español. Verificado en el VPS: `/health`, catálogo de 13 juegos, landing con `?game=`, controlador en español, diccionario de traducción, audio `.m4a` y narradora. El APK de Android compila con la sección Juegos completa y las portadas WebP; el tamaño final es **89,434,263 B**, muy por debajo de **150 MB** (los assets y audios de la plataforma viven en el servidor, no en el APK).

Puedes repetir la simulación del servidor con:

```bash
cd games/banterbolt
npm install
npm test
```

## Arquitectura

```
TV / Android TV ── WebView ──► host de Open Party Lab (React + Phaser)
                                     │
                                     ├── servidor autoritativo (Node + Socket.IO)
                                     │
Teléfonos ── QR ──► controlador web (React)
```

- El servidor es la autoridad: reglas, tiempos, puntajes y estado privado.
- El host es la pantalla compartida; los teléfonos solo mandan intención.
- Todo el contenido nuevo vive en `games/` y en `content/`; no se toca el motor de la plataforma salvo los parches de idioma/tema.
- La integración con Vision+ es un `WebView` a pantalla completa desde la pestaña Juegos. El QR y la sala los genera el host web; la app solo muestra la pantalla.

## Cómo montarlo en local

1. Clona Open Party Lab al lado de este directorio:

```bash
git clone https://github.com/Hartwich/Open-Party-Lab.git platform/open-party-lab
```

2. Aplica los parches de Vision+:

```bash
cp -R platform/patches/* platform/open-party-lab/
```

3. Copia el juego:

```bash
cp -R games/banterbolt platform/open-party-lab/local-games/
```

4. Instala y arranca:

```bash
cd platform/open-party-lab
npm install
npm run build:packages
npm run games:sync-local
npm run dev:server   # o npm run dev:all en Windows
```

Servidor por defecto en `:3000`; host en `:5173`; controlador en `:5174`.
Si usas otro puerto, crea `apps/host/.env.local` y `apps/controller/.env.local` con:

```env
VITE_SERVER_URL=http://127.0.0.1:3200
```

y arranca el servidor con `PORT=3200`.

## Parches de plataforma

Los archivos en `platform/patches/` son copias de los archivos modificados de Open Party Lab:

- `packages/game-core/src/i18n/language.ts`: agrega `es` y lo pone como idioma por defecto.
- `packages/game-core/src/types/GameManifest.ts`: permite reglas localizadas en `es`.
- `apps/controller/src/i18n/controllerText.ts`: diccionario español del controlador.
- `apps/host/src/i18n/hostText.ts`: diccionario español del host.
- `apps/controller/src/i18n/profileText.ts`: perfil en español.
- `apps/controller/src/controller-ui/layouts/SocialPartyLayout.tsx`: textos de escribir/votar en español y timer.
- `apps/controller/src/app/controllerBootstrap.tsx`: se suscribe **antes** de conectar (evita que el primer `connect` se pierda en redes rápidas).
- `apps/host/src/app/hostedLanding.ts`: landing en español.
- `config/known-games.json`: registro de los tres juegos portados.

## Narradora

La voz es una **narradora mexicana clonada en local** con Qwen3-TTS Base a partir de referencias es-MX. Hay **630 audios**: 10 variantes por momento en los 10 juegos base, Banterbolt, El Comisionado Merk, ¿Le atinaste? Bien. y la selección de juego (`narrator_<momento>_<0-9>.m4a`). El host rota las variantes con una bolsa mezclada para no repetir dentro de una sesión.

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
"$PY" party/tools/generate_party_voice_mx.py --force --batch 10
"$PY" party/tools/audit_party_audio.py --asr
```

La auditoría revisa formato, loudness, picos, silencios, duplicados, consistencia de tono y transcripción. El detalle está en `docs/AUDIO_NARRACION_MX.md`.

## Licencias

- Open Party Lab: **Apache 2.0**. Conserva su `LICENSE`, `NOTICE.md` y atribuciones.
- Banterbolt original (The Merkive): **Apache 2.0**; este puerto conserva la esencia y reescribe contenido/UI.
- Qwen3-TTS: **Apache 2.0**; la referencia de voz es interna de Vision+.
- Prompts y arte de Vision+: originales; no se importan ROMs, cartas ni assets de terceros sin licencia.

## Android TV

La sección **Juegos** de Vision+ ya es un hub nativo:

- `PartyGamesScreen.kt`: rejilla de tarjetas con texto grande, foco D-pad y portadas.
- `PartyHostScreen.kt`: WebView a pantalla completa con botón **Salir** y manejo de Atrás.
- `PartyConfig.kt`: URL del servidor mediante la flag remota `party_host` (default `https://party.visionplusv2.duckdns.org/`).
- `Route.PartyHost(gameId)`: abre la landing con `?game=<id>`; la landing crea la sala, entra al host y los teléfonos entran por QR.

Los 13 juegos aparecen disponibles. La interfaz y el contenido se muestran en español mediante contenido localizado y una capa de traducción para textos heredados de la plataforma. La sala previa reúne QR, configuración, reglas y jugadores; el D-pad recorre la interfaz y los controles web también aceptan toque.

## Siguiente paso

Leer `PLAN_PORT.md`. Falta la prueba física en TCL/Chromecast + 3 teléfonos y completar la traducción de los juegos restantes. El host se publica en `party.visionplusv2.duckdns.org`.
