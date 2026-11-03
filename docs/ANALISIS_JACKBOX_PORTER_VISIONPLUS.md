# Análisis de portabilidad de Jackbox a Vision+

**Fecha:** 2026-10-31  
**Alcance:** los 4 packs de Jackbox instalados en este equipo, su arquitectura completa y las vías reales para llevarlos a Vision+ (app Android/Android TV + backend + Vision+ Home).  
**Método:** inspección de binarios, manifiestos, assets, SWF descomprimidos, protocolo ecast, cliente web de jackbox.tv y comparación con la arquitectura actual de Vision+ Play.  
**Regla:** este documento es un análisis. No se copió ningún asset, código ni contenido de Jackbox al repositorio de Vision+.

---

## 0. Dictamen ejecutivo

**No es viable portar el código o los binarios de Jackbox a Vision+.** Los juegos instalados son binarios nativos **x86-64 Linux** que dependen de **Scaleform GFx + ActionScript 3 + FMOD + CRIWARE + Steamworks**, no hay código fuente, no hay SDK público y los assets son propiedad de Jackbox. Un port nativo exigiría reescribir el motor y los 14 juegos, además de una licencia.

**Sí hay cuatro caminos viables, en orden de coste:**

| Vía | Qué permite | Coste | Veredicto |
|---|---|---:|---|
| **A. Apps oficiales de Android TV** | Jugar Pack 9/10 comprados en Google TV, lanzados desde Vision+ Home | 1-3 días de integración | **Inmediato** |
| **B. Streaming del PC** | Jugar los packs de Steam que ya tienes, con el PC como host y la tele como pantalla | 2-4 semanas de MVP | **La mejor para conservar los juegos reales** |
| **C. Juegos originales en Vision+ Play** | Mecánicas equivalentes (Quiplash, encuestas, trivia temporal, etc.) sin IP de Jackbox | 1-6 semanas por juego | **La mejor para producto** |
| **D. Acuerdo con Jackbox / app cloud** | Integración oficial de su catálogo o de su app de smart TV | Negociación | **La única vía legal para “Jackbox dentro de Vision+”** |

**Recomendación:** no intentar el port. Hacer un **hub de juegos de fiesta** en Vision+ que (1) lance las apps oficiales de Android TV si existen, (2) ofrezca streaming del PC para las copias de Steam y (3) sume 4-6 juegos originales de Vision+ Play inspirados en las mecánicas, empezando por un **Quiplash-like** y un **Survey-like**, que son los de mejor relación esfuerzo/diversión.

---

## 1. Inventario real de lo instalado

### 1.1 Packs, versiones y tamaño

Ruta base:

```text
/media/chronos/Disco Duro/SteamLibrary/steamapps/common/
```

| Pack | App ID de Steam | Build | Fecha de build | Tamaño | Juegos |
|---|---:|---:|---|---:|---:|
| The Jackbox Party Starter | 1755580 | 337 | 2025-02-11 | 4.9 GB | 3 |
| The Jackbox Party Pack 9 | 1850960 | 101 | 2024-08-08 | 8.8 GB | 5 |
| The Jackbox Party Pack 10 | 2216830 | 526 | 2025-07-18 | 2.4 GB | 5 |
| The Jackbox Survey Scramble | 2948640 | 540 | 2025-05-14 | 302 MB | 1 |

**Total: 14 juegos, 16.29 GiB** (`du -sc` = 17,084,508 KB). El tamaño importa: el Chromecast HD de referencia tiene almacenamiento de clase 8 GB y RAM de clase 1.5 GB; el Pack 9 solo ya no cabe.

### 1.2 Los 14 juegos

| # | Juego | Pack | Carpeta interna | Tamaño aprox. | Notas |
|---:|---|---|---|---:|---|
| 1 | Quiplash 3 | Starter | `Quiplash3` | 1.2 GB | Escritura + voto; el más “portable” conceptualmente |
| 2 | Tee K.O. | Starter | `AwShirt` | 195 MB | Dibujo + eslóganes + camisetas |
| 3 | Trivia Murder Party 2 | Starter | `triviadeath2` | 2.4 GB | Trivia + minijuegos + eliminación |
| 4 | Fibbage 4 | Pack 9 | `Fibbage4` | 5.4 GB | Mentiras + voto; ya hay equivalente en Vision+ |
| 5 | Quixort | Pack 9 | `Lineup` | 409 MB | Ordenar secuencias en tiempo real |
| 6 | Junktopia | Pack 9 | `AntiqueGame` | 257 MB | Objetos raros + presentación + tasación |
| 7 | Nonsensory | Pack 9 | `RangeGame` | 590 MB | Adivinar escalas / dibujar |
| 8 | Roomerang | Pack 9 | `MakeFriends` | 681 MB | Deducción social con roles y reincorporación |
| 9 | Tee K.O. 2 | Pack 10 | `AwShirt2` | 193 MB | Evolución de Tee K.O. |
| 10 | FixyText | Pack 10 | `RiskyText` | 95 MB | Escritura cooperativa caótica |
| 11 | Timejinx | Pack 10 | `TimeTrivia` | 288 MB | Trivia temporal |
| 12 | Hypnotorious | Pack 10 | `Strangers` | 364 MB | Deducción social |
| 13 | Dodo Re Mi | Pack 10 | `NopusOpus` | 138 MB | Ritmo/música; el más difícil de adaptar |
| 14 | The Jackbox Survey Scramble | Survey | `BigSurvey` | 235 MB | Encuestas; varios modos |

`Picker` (1.2-1.5 GB en cada pack) es el menú/selector compartido, no un juego.

### 1.3 Hardware de este equipo

| Componente | Valor |
|---|---|
| CPU | Intel Core i7-2600S (4 núcleos / 8 hilos, 2.8 GHz) |
| RAM | 31 GiB |
| GPU | NVIDIA RTX 3060 (GA106) |
| SO | Linux (kernel 7.0, base Ubuntu/Mint) |
| Almacenamiento de juegos | Disco externo `/media/chronos/Disco Duro/SteamLibrary` |

Es suficiente para ejecutar y transmitir Jackbox en 1080p: los juegos son 2D y ligeros. La CPU es vieja, pero no es el cuello de botella para este tipo de título.

### 1.4 Cómo se lanzan

Los cuatro `.desktop` de `~/.local/share/applications` apuntan a:

```text
steam steam://rungameid/1755580
steam steam://rungameid/1850960
steam steam://rungameid/2216830
steam steam://rungameid/2948640
```

No hay versión Android instalada. Las copias son de **Steam PC/Linux**.

---

## 2. Arquitectura de Jackbox (lo que hay debajo)

### 2.1 Motor nativo propietario

Los ejecutables son:

```text
TJPS_OpenGL / TJPS_Vulkan        (Starter)
TJPP9_OpenGL / TJPP9_Vulkan      (Pack 9)
TJPP10_OpenGL / TJPP10_Vulkan    (Pack 10)
TJSS_OpenGL / TJSS_Vulkan        (Survey)
```

Características verificadas:

- **ELF 64-bit LSB, x86-64**, dinámicamente enlazado, `stripped`, sin símbolos de depuración.
- Tamaño: ~14.5-14.8 MB por ejecutable.
- Dependencias directas: `libSDL2-2.0.so.0`, `libfmod.so.13/14`, `libfmodstudio.so.13/14`, `libsteam_api.so`, `libstdc++`, `libm`, `libc`.
- Bibliotecas internas/estáticas: **Scaleform GFx**, **libcurl**, **libwebsockets**, **OpenSSL**, **CRIWARE/Sofdec** (video `.usm`), **Steamworks**.
- Render: **OpenGL** y **Vulkan**; el lanzador elige con `RenderDetector`.
- Rutas de compilación visibles: `jbg-common`, `Perforce/TJPP7`, `workspace_7`; el motor es compartido entre packs.
- No hay binarios ARM, ni `arm64-v8a`, ni `armeabi-v7a`. El APK de Vision+ solo empaqueta ARM, así que ni siquiera se puede “reutilizar” una librería de estos juegos.

**Conclusión:** es un motor de juego C++ propietario, no un contenedor web ni un emulador de Flash. No se puede recompilar sin fuente y sin las licencias de Scaleform/FMOD/CRIWARE/Steam.

### 2.2 Contenido: SWF, JSON, OGG, USM y bancos FMOD

Los juegos son **data-driven** en una parte importante:

| Formato | Qué es | Observación |
|---|---|---|
| `.swf` | Películas Flash/Scaleform con ActionScript 3 | Comprimidas CWS; se pueden descomprimir y descompilar; **no son Flash estándar** |
| `.jet` | JSON en texto plano (a pesar de la extensión) | Menús, configuración y mazos de contenido; legibles |
| `.ogg` | Audio | Miles de líneas de voz y música |
| `.usm` | Video CRIWARE Sofdec | Intros/fondos; no es MP4 |
| `.bank` | Bancos FMOD | Audio y eventos |
| `.png` | Arte/texturas | Parte del arte vive también dentro de los SWF |

Conteos aproximados por pack:

| Pack | `.swf` | `.jet` | `.ogg` | `.usm` | `.png` | `.bank` |
|---|---:|---:|---:|---:|---:|---:|
| Starter | 182 | 17,307 | 38,023 | 146 | 2 | 23 |
| Pack 9 | 169 | 17,145 | 32,452 | 218 | 675 | 35 |
| Pack 10 | 190 | 9,134 | 10,041 | 40 | 224 | 35 |
| Survey | 46 | 20 | 2,243 | 4 | 2 | 10 |

Los `.jet` son JSON corriente; por ejemplo, los mazos de contenido tienen la forma:

```json
{
  "content": [
    { "id": "95486", "text": "..." }
  ]
}
```

Eso significa que **el contenido es trivialmente extraíble**. También significa que **es trivialmente copiable**, y por tanto el riesgo legal es alto. No se debe usar ese contenido en Vision+ sin licencia, aunque técnicamente se pueda leer.

Cada juego tiene contenido localizado en:

```text
en, fr, it, de, es, es-XL, pt-BR
```

El soporte de español existe, pero es contenido de Jackbox, no de Vision+.

### 2.3 Lógica de juego: Scaleform + ActionScript 3 + “Talkshow”

Los SWF de juego son **AS3 para Scaleform GFx**, no Flash Player doméstico. Se ven clases como:

```text
scaleform.gfx.Extensions
scaleform.gfx.FocusEventEx
scaleform.gfx.KeyboardEventEx
jackboxgames.nativeoverride.AudioSystem
jackboxgames.nativeoverride.Video
jackboxgames.nativeoverride.WebSocket
jackboxgames.nativeoverride.Save
jackboxgames.nativeoverride.License
jackboxgames.nativeoverride.Platform
jackboxgames.nativeoverride.DLC
jackboxgames.nativeoverride.Trophy
```

Es decir: además del AS3, el juego llama a **APIs nativas del motor** para audio, video, red, guardado, licencias, logros, etc. Un emulador Flash genérico (Ruffle, Flash Player) no basta: no implementa esas extensiones ni los bindings nativos.

El framework interno se llama **Talkshow**. La lógica se organiza en:

- `JBGGameState` / `GameState`: estado de la partida.
- `ActionPackage`: paquetes de acciones por fase (`Intro`, `Round`, `Gameplay`, `FinalRound`, `Global`).
- `cells`: celdas de guion/flowchart.
- `widgets`: UI y animación.
- `delegates`: reglas y puntuación.
- `IEngineAPI`: puente con el motor.

Complejidad representativa de **Quiplash 3**:

- 4,458 clases AS3 en total (incluye Flex, Scaleform y el framework común).
- 155,446 líneas de AS3 descompiladas.
- 157 clases propias del juego, ~18,086 líneas.
- SWF principal: 6.7 MB; sub-SWF de arte/animación: 5-17 MB cada uno.

Los demás juegos tienen SWF principales de ~0.9-1.1 MB y 140-190 clases visibles, pero el peso real está en los sub-SWF de arte/animación, los bancos de audio y los videos.

**Conclusión:** la lógica es comprensible y hasta cierto punto reimplementable, pero **no es portable línea a línea**. Reimplementar un juego es un proyecto de producto, no una conversión.

### 2.4 Red: ecast es un relay, el host es la autoridad

Jackbox no ejecuta la lógica en la nube. El juego de la tele/pc es el **host**; el servidor `ecast` actúa como ** relay de estado y mensajes** para que los teléfonos se conecten.

Endpoints verificados en el AS3 (`APIClient.as`, `WSClient.as`) y en el cliente web:

```text
POST https://ecast.jackboxgames.com/api/v2/rooms
GET  https://ecast.jackboxgames.com/api/v2/rooms/{code}
WSS  wss://ecast.jackboxgames.com/api/v2/rooms/{code}/play
WSS  wss://ecast.jackboxgames.com/api/v2/audience/{code}/play
```

Crear sala (host):

```json
{
  "userId": "...",
  "appId": "uuid-del-juego",
  "time": 1234567890,
  "keepalive": true
}
```

Respuesta:

```json
{
  "ok": true,
  "body": {
    "code": "ABCD",
    "token": "token-del-host",
    "host": "ecast.jackboxgames.com"
  }
}
```

WebSocket del host:

```text
/api/v2/rooms/ABCD/play?role=host&name=...&format=json&user-id=...&host-token=...
```

Mensajes (JSON):

```json
{ "seq": 1, "opcode": "text/create", "params": { "key": "...", "val": "..." } }
```

Respuesta:

```json
{ "pc": 1, "re": 1, "opcode": "ok", "result": {} }
```

Notificación:

```json
{ "pc": 2, "opcode": "client/connected", "result": { "id": 3, "name": "...", "role": "player" } }
```

Opcodes del host (extracto real de `WSClient.as`):

```text
text/create, text/update, text/set, text/filter, text/echo
object/create, object/update, object/set, object/echo
doodle/create, doodle/get, doodle/stroke
client/send, client/kick
room/lock, room/exit, room/start-audience, room/get-audience
audience/count-group/*, audience/g-counter/*, audience/pn-counter/*, audience/text-ring/*
artifact/create
```

El host escribe el estado compartido (`text`, `object`, `doodle`) y los teléfonos reciben notificaciones. Los teléfonos envían `client/send` con el cuerpo de su acción; el host decide y actualiza el estado. **No hay lógica de juego en ecast**: si el host no la ejecuta, no hay partida.

Consecuencias:

- No se puede “portear solo el servidor”: no existe un servidor de reglas que reutilizar.
- No hay modo LAN/offline: el host necesita internet para crear la sala y los teléfonos para entrar.
- El código de sala es de 4 letras; el cliente oficial es `https://jackbox.tv`.
- El controller web usa el mismo WebSocket; se verificó en el bundle `CM0nr-Mh.js` de jackbox.tv.
- La audiencia puede ser de hasta 10,000 personas en Jackbox; Vision+ hoy soporta 8 espectadores.
- El `serverUrl` es configurable en `jbg.config.jet`, pero Jackbox no incluye un servidor local: apuntarlo a un host propio obligaría a implementar ecast y toda la lógica del host.
- El protocolo soporta reconexión: el cliente envía `id`, `secret` y `replay-since`, y ecast reproduce las notificaciones perdidas. Es una buena referencia para la resiliencia de Vision+ Play.

### 2.5 Flujo real de una partida

```text
PC/TV (host AS3)
   │  POST /api/v2/rooms                 → código + token
   │  WSS /rooms/{code}/play?role=host   → estado compartido
   │
   ├── text/object/doodle create/update  → ecast guarda y difunde
   │
Teléfonos (jackbox.tv)
   │  WSS /rooms/{code}/play?role=player
   │  client/send                       → ecast lo entrega al host
   │
Host AS3 ejecuta reglas, puntajes y fases
   │  notificaciones                     → teléfonos y TV
```

### 2.6 DRM, licencias y telemetría

- Cada ejecutable enlaza `libsteam_api.so` y usa el App ID de Steam.
- Strings del binario: “ERROR: Didn't launch from the Steam client. Restarting if possible.”, “Failed to initialize Steam”.
- Sin cliente Steam no arranca; la licencia se valida con Steamworks.
- Los `gameManifest.json` incluyen un **DSN de Sentry** y un **clientSecret de Epic Games Store** embebidos. No se reproducen aquí; es un hallazgo colateral de seguridad, no una vía de port.
- Hay analítica y `License`/`DLC`/`Trophy` en el motor.

**Implicación:** aunque se recompilara el motor para ARM, habría que sustituir Steamworks por el sistema de licencias de Android/Google Play. Eso no está en nuestro control.

### 2.7 ¿Existe una versión Android TV oficial?

Sí, para varios packs. Es un dato clave:

- **The Jackbox Party Pack 10** se publicó en Google TV/Android TV en noviembre de 2023 (9to5Google).
- **The Jackbox Party Pack 9** figura en la lista de plataformas Android TV de Wikipedia; Jackbox dijo que llevaría Pack 8 y 9 al nuevo sistema tras los cambios de Google.
- Packs 1-7 tienen historial de versiones Android TV; Pack 3, 5, 6, 7 aparecen en Google Play.
- **The Jackbox Survey Scramble** tiene app en Google Play (móvil) y versiones Fire TV/Apple TV; su disponibilidad exacta en Android TV debe verificarse en la tienda de la región.
- **The Jackbox Party Starter** tiene versiones Apple TV/iPad; no está confirmado que tenga una app Android TV.

Esto demuestra que **el motor de Jackbox sí es portable a ARM/Android**, pero es un build propietario de Jackbox, no un SDK que podamos usar. La existencia de la app oficial no nos da derecho a extraerla, repackarla ni integrarla dentro de Vision+.

También existe la **app cloud de Jackbox para smart TV** anunciada en abril de 2026: usa **AWS GameLift Streams**, arranca en beta en una o dos plataformas de TV (se ha visto beta cerrada en Fire TV) y apunta a un modelo gratuito con anuncios y luego suscripción. **No está confirmada para Android TV/Google TV**; si llega, sería una vía de integración por deep link o convenio, no un port.

---

## 3. Vision+ hoy: qué tenemos y qué encaja

### 3.1 Arquitectura actual

```text
app/            Android Kotlin + Compose (móvil y tele)
launcher/       Vision+ Home (APK aparte, lista apps instaladas y lanza Leanback)
home-contract/  DTOs, JSON del feed y deep links
server/api/     FastAPI + Postgres (catálogo, salas, juegos, diagnóstico)
server/sync/    Workers de catálogo/EPG/deportes
```

Datos relevantes:

- `minSdk 24`, `targetSdk 35`, **solo ARM** (`arm64-v8a`, `armeabi-v7a`).
- APK de ~97.6 MB con motor X4K; el motor X4K es **reproductor de video**, no un runtime de juegos/Flash.
- Vision+ Home ya usa `PackageManager.getLeanbackLaunchIntentForPackage` y `getLaunchIntentForPackage` para listar y abrir apps instaladas (`launcher/.../data/AppsRepository.kt`).
- Vision+ Play tiene **7 juegos**: Trivia, Charadas, Apuesta a la pista, El Impostor, Duelo de Dibujo Ciego, Subasta Ciega y Falso Conocimiento/Fibbage.
- Salas en memoria: 100 salas, TTL 4 h, inactividad 30 min, 8 jugadores, 8 espectadores.
- El servidor es la autoridad: fases, timers, puntajes y filtrado de secretos por rol.
- Transporte actual: **polling HTTP** (~700 ms en fases activas, 1 s en el resto), no WebSocket/SSE.
- Suite reciente: ~590 tests backend y ~1,148 Android; el proyecto tiene reglas duras de tests, firma y OTA.

### 3.2 Comparación directa

| Tema | Jackbox | Vision+ Play |
|---|---|---|
| Autoridad | Host (AS3 en PC/TV) | Servidor FastAPI |
| Transporte | WebSocket (ecast) | Polling HTTP |
| Estado | `text/object/doodle` en ecast | Snapshot filtrado por rol |
| Controllers | Web `jackbox.tv` | App Android (QR/código) |
| Jugadores | 1-8 (algunos 2-10) | 1-8 |
| Audiencia | Hasta 10,000 | 8 espectadores |
| Contenido | Mazos propietarios localizados | Generado del catálogo + mazos propios |
| Arte/animación | SWF/Scaleform + USM | Compose/WebP + Media3 + TTS |
| Extensión | Motor propietario | Framework propio, ya probado |
| Offline/LAN | No (ecast cloud) | No (VPS), pero se podría hacer LAN |

**Lectura:** Vision+ Play no necesita el motor de Jackbox. Su modelo es incluso mejor para integridad (servidor autoritativo) y ya tiene las piezas de sala, QR, TV/teléfono, voz, arte y tests. Lo que falta son **juegos y contenido**, no motor.

---

## 4. Matriz de viabilidad

Puntuación 1-10 (10 = mejor).

| Opción | Técnica | Legal | Coste | Time-to-market | Veredicto |
|---|---:|---:|---:|---:|---|
| Port nativo de binarios a ARM | 1 | 0 | 10 | 10 | **No** |
| Recompilar el motor desde fuente | 0 | 0 | 10 | 10 | **No existe la fuente** |
| Emular x86-64 en Android TV | 2 | 3 | 8 | 8 | **No para producto** |
| Extraer y reutilizar assets/SWF/JSON | 4 | 0 | 5 | 4 | **No** |
| Apps oficiales Android TV + deep link | 10 | 10 | 1 | 1 | **Sí, ya** |
| Streaming del PC (Steam Link/Moonlight) | 9 | 8 | 3 | 2 | **Sí, recomendado para juegos reales** |
| App cloud de Jackbox | 6 | 10 | 2 | ? | **Quizá, cuando esté disponible** |
| Juegos originales inspirados | 9 | 10 | 5 | 4 | **Sí, recomendado para producto** |
| Controller nativo para jackbox.tv | 7 | 8 | 4 | 3 | **Posible, pero WebView basta** |
| Host ecast propio con contenido Jackbox | 2 | 0 | 10 | 10 | **No** |

### 4.1 Por qué el port nativo no es viable

1. **ABI:** binarios x86-64; Vision+ es ARM. No hay fuente para recompilar.
2. **Runtime:** Scaleform GFx + AS3 + extensiones `scaleform.gfx.*` + `jackboxgames.nativeoverride.*`. Ruffle/Flash no los soporta.
3. **Dependencias:** FMOD, CRIWARE, SDL2, Steamworks, libcurl/libwebsockets/OpenSSL. Habría que sustituir cada una y licenciar Scaleform/FMOD/CRIWARE.
4. **DRM:** Steamworks y App IDs; no hay Steam en Android TV.
5. **Tamaño:** 16.29 GiB; el pack más grande son 8.8 GB. No cabe en el almacenamiento típico de un Chromecast HD.
6. **Legal:** código, arte, audio, preguntas, personajes y marcas son propiedad de Jackbox. No se pueden redistribuir.
7. **Mantenimiento:** cada actualización de Jackbox rompería el port; habría que perseguir 14 juegos.

### 4.2 Por qué la emulación no es producto

Box64/Wine/Termux-X11 sobre ARM podría, en teoría, ejecutar el binario Linux x86-64 en un dispositivo de gama alta. En la práctica:

- El Chromecast HD no tiene CPU/RAM/almacenamiento para ello.
- Scaleform + OpenGL/Vulkan + FMOD + CRIWARE bajo traducción es una cadena frágil.
- Steam DRM y el cliente Steam no existen en Android TV.
- El rendimiento y la compatibilidad variarían por dispositivo.
- No es instalable para usuarios normales ni aceptable para una OTA.

Como experimento de laboratorio en un mini-PC x86 con Android-x86 podría funcionar; como producto, no.

### 4.3 Por qué reutilizar assets no es opción

Técnicamente los `.jet` son JSON y los `.swf` se descomprimen. Pero:

- El contenido (preguntas, respuestas, arte, voces, animaciones) está protegido por copyright.
- Los SWF dependen de Scaleform y del motor; no se pueden reproducir en Compose.
- Los `.usm` son CRIWARE, no MP4; habría que transcodificar.
- Los bancos FMOD no son audio plano.
- Usar el contenido en Vision+ sería una violación, aunque el código fuera propio.

**No se debe copiar nada.**

---

## 5. Juego por juego: qué tan adaptables son

La columna “dificultad” es para **reimplementar la mecánica con contenido original** en Vision+ Play, no para portar el código.

| Juego | Mecánica | Dependencia técnica | Equivalente en Vision+ | Dificultad | Comentario |
|---|---|---|---|---|---|
| **Quiplash 3** | Escribir respuesta graciosa a un prompt; votar la mejor | Texto + voto + reveal | Fibbage (mentiras/voto) | **Baja** | El mejor primer candidato: reutiliza casi todo el flujo de Fibbage; solo cambia el prompt y la votación |
| **Tee K.O. / Tee K.O. 2** | Dibujar + escribir eslogan + combinar camisetas + votar | Dibujo vectorial + composición + arte | Duelo de Dibujo Ciego | **Media-alta** | El dibujo ya existe; falta el compositor de camiseta y más arte/animación |
| **Trivia Murder Party 2** | Trivia + minijuegos + eliminación + humor negro | Trivia + minijuegos + estados | Trivia Vision+ | **Media** | La trivia ya existe; los minijuegos y la eliminación son trabajo de UI/reglas |
| **Fibbage 4** | Inventar mentira y votar la verdad | Texto + voto + opciones | Falso Conocimiento | **Muy baja** | Ya está implementado; solo ampliar mazos, categorías y ronda final |
| **Quixort** | Ordenar secuencias que caen en tiempo real | Tick/tiempo real + equipos | Ninguno | **Media-alta** | El polling de 700 ms no basta; requeriría WebSocket/SSE o tick más fino |
| **Junktopia** | Objetos raros + presentación + tasación | Texto + turnos + voto | Subasta/Charadas | **Media-baja** | Muy factible; el arte de objetos se puede generar |
| **Nonsensory** | Adivinar escala numérica / dibujar según prompt | Texto + dibujo + rangos | Wager/Dibujo | **Media** | Buen candidato; mezcla dos mecánicas ya existentes |
| **Roomerang** | Deducción social con roles y reincorporación | Roles secretos + voto + rondas | El Impostor | **Media** | Se puede enriquecer El Impostor con roles y reingreso |
| **FixyText** | Escritura cooperativa caótica con sabotaje | Texto en tiempo real | Ninguno | **Media-baja** | Divertido y barato; cuidado con moderación y concurrencia |
| **Timejinx** | Trivia de años/tiempo | Trivia + años | Trivia Vision+ | **Baja** | Se resuelve con un modo de trivia temporal; muy barato |
| **Hypnotorious** | Deducción social por categorías ocultas | Roles + preguntas + voto | El Impostor | **Media-baja** | Buena variante de subasta social |
| **Dodo Re Mi** | Ritmo/música cooperativo | Audio de baja latencia + sincronía | Ninguno | **Muy alta** | El polling y la latencia de TV/teléfono lo hacen risky; no recomendado |
| **Survey Scramble** | Adivinar respuestas más populares de encuestas | Datos de encuestas + ranking | Ninguno | **Media-baja** | Muy buen candidato; requiere contenido original de encuestas |

### 5.1 Orden recomendado de adaptación

1. **Quiplash-like** (1-3 semanas): máximo retorno, reutiliza Fibbage.
2. **Survey-like** (2-3 semanas): contenido original, gran factor social.
3. **Timejinx-like** (1-2 semanas): extensión de Trivia.
4. **FixyText-like** (2-3 semanas): texto en tiempo real, bajo arte.
5. **Tee K.O.-like** (4-6 semanas): más arte y composición.
6. **Quixort-like** (3-4 semanas): requiere mejorar el transporte.
7. **Dodo Re Mi-like**: no hacerlo hasta tener WebSocket y audio de baja latencia.

Con 4 juegos originales (1-4) Vision+ Play pasaría de 7 a 11 y cubriría las mecánicas más reconocibles de Jackbox sin tocar su IP.

---

## 6. Qué podemos hacer con lo que tenemos

### 6.1 Hoy mismo, sin escribir código

**Opción A: apps oficiales de Android TV**

Si el televisor/Chromecast tiene Google TV/Android TV:

1. Instalar **The Jackbox Party Pack 10** desde Google Play (compra aparte; la licencia de Steam no se transfiere).
2. Verificar si Pack 9 está disponible en la región.
3. Al instalarse, la app aparece automáticamente en **Vision+ Home**, porque el launcher lista las apps con intent Leanback.
4. Se abre desde Vision+ Home; los teléfonos usan `jackbox.tv`.

Esto no es un port, pero es la vía más barata y legal para tener Jackbox “en la tele”.

**Opción B: streaming del PC**

1. Instalar **Steam Link** o **Moonlight** en el Chromecast/Android TV.
2. Emparejar con este PC (RTX 3060) por LAN.
3. Lanzar el pack de Steam; la tele recibe video/audio.
4. Los teléfonos abren `jackbox.tv` y escriben el código.

Ventajas: conserva las compras de Steam, los 14 juegos y las actualizaciones oficiales.  
Desventajas: el PC debe estar encendido, misma red (o buena conexión), y hay que abrir el stream.

### 6.2 Integración ligera en Vision+ (1-5 días)

Añadir un **hub “Sala Jackbox”** en Vision+ Play o en Vision+ Home:

- Detectar apps oficiales instaladas (`PackageManager`, intent Leanback) y lanzarlas.
- Si no están, mostrar QR/instrucciones de `jackbox.tv` y un botón “Cómo instalar”.
- Si hay PC, botón “Transmitir desde PC” que abra Steam Link/Moonlight (deep link) o muestre instrucciones.
- Mostrar un QR a `jackbox.tv` y un campo para el código de 4 letras.
- No empaquetar nada de Jackbox; solo enlaces y texto.

Esto se puede hacer sin tocar el motor X4K ni las zonas prohibidas.

### 6.3 Streaming integrado en Vision+ (2-4 semanas MVP)

Si se quiere que el usuario no salga de Vision+:

```text
Vision+ TV ──LAN──► Host Agent (PC)
     │                 ├─ lista juegos instalados
     │                 ├─ steam -applaunch <id>
     │                 └─ estado/stop
     └── video/audio ◄── Steam Link / Sunshine + Moonlight / WebRTC
Teléfonos ──► jackbox.tv (ecast)
```

Recomendación técnica:

- **MVP:** usar **Steam Link** o **Moonlight** como app externa; Vision+ solo orquesta el lanzamiento y muestra instrucciones. Evita GPL y DRM.
- **Integración profunda:** Moonlight Android es GPLv3; enlazarlo dentro de Vision+ obliga a abrir el código o a un acuerdo de licencia. Mejor mantenerlo como app separada o usar un protocolo propio (WebRTC/GStreamer), que es más caro.
- **Host Agent:** servicio pequeño en el PC (Python/Node) con pairing por código, solo LAN, sin exponer a internet; endpoints `list`, `launch`, `stop`, `status`.
- **Audio:** el stream del PC ya lleva el audio del juego; los teléfonos no necesitan sincronizarse con la tele.
- **Room code:** se lee del stream; no hace falta OCR ni integración con ecast.

Riesgos: latencia, NAT, DRM de Steam, PC encendido, consumo eléctrico, actualizaciones de Jackbox. Para uso familiar es aceptable; para producto distribuido, revisar ToS.

### 6.4 Juegos originales en Vision+ Play (recomendado)

Aprovechar lo que ya existe:

- Servidor autoritativo, salas, códigos, QR, snapshot por rol.
- TV + teléfono, háptica, SFX, narradora Qwen3-TTS.
- Arte con ComfyUI, mazos con `game_decks.py`, tests.
- Público/espectador, medallas, podio.

Añadir 4 juegos:

| Nuevo juego | Base | Trabajo principal |
|---|---|---|
| **Respuesta Ingeniosa** (Quiplash-like) | Fibbage | Prompt + escritura + voto + reveal; moderación |
| **La Encuesta** (Survey-like) | Nuevo | Mazo de encuestas original + ranking + equipos |
| **Trivia Temporal** (Timejinx-like) | Trivia | Años, líneas de tiempo, apuestas |
| **Texto Roto** (FixyText-like) | Nuevo | Texto cooperativo en tiempo real + sabotaje |

Después: **Camisetas** (Tee K.O.-like), **Ordena** (Quixort-like), **Roles** (Roomerang-like).

### 6.5 Acuerdo con Jackbox (largo plazo)

Si el objetivo es “Jackbox oficial dentro de Vision+”:

- Contactar a Jackbox Games (business development/partnerships).
- Proponer distribución en México/LATAM, español, modelo ad-supported o suscripción.
- Pedir licencia de contenido + builds Android TV + guía de integración.
- Asumir que puede ser un “no”; su estrategia 2026 es una app propia de smart TV con AWS GameLift Streams, no licenciar a terceros.

Sin acuerdo, no se puede usar su marca, contenido ni binarios.

---

## 7. Qué no podemos hacer

1. **Compilar los binarios x86-64 para ARM.** No hay fuente.
2. **Ejecutar Scaleform AS3 en Vision+.** El motor X4K es de video, no de Flash/juegos.
3. **Reutilizar SWF, `.jet`, OGG, USM, bancos FMOD o arte.** Copyright.
4. **Meter los packs en el APK.** 16.29 GiB, ABI incorrecta, DRM de Steam.
5. **Emular en el Chromecast HD.** RAM/almacenamiento/CPU insuficientes; DRM.
6. **Portar los 14 juegos a la vez.** Es un proyecto de varios meses y no todos aportan.
7. **Usar marcas “Jackbox”, “Quiplash”, “Fibbage”, “Tee K.O.”, etc.** Sin licencia.
8. **Garantizar la app cloud de Jackbox en Vision+.** No está confirmada para Android TV ni tiene API pública.
9. **Implementar un host ecast propio con contenido de Jackbox.** Técnicamente concebible, legalmente inviable y frágil.
10. **Prometer 10,000 espectadores.** Vision+ hoy soporta 8; eso requeriría WebSocket/Redis y carga.

---

## 8. Esfuerzo y riesgos

| Trabajo | Esfuerzo | Riesgo | Nota |
|---|---:|---|---|
| Hub de apps oficiales + QR | 1-5 días | Bajo | Solo `PackageManager`/intents |
| Streaming PC MVP (Steam Link/Moonlight) | 2-4 semanas | Medio | PC encendido, LAN, pairing |
| Streaming integrado propio (WebRTC) | 2-3 meses | Alto | GPL, latencia, códecs, NAT |
| Un juego original simple | 1-3 semanas | Bajo-medio | Reutiliza framework |
| Un juego de dibujo/arte | 4-6 semanas | Medio | Arte/animación |
| Un juego en tiempo real (Quixort/ritmo) | 4-8 semanas | Alto | Requiere WebSocket/tick fino |
| 4 juegos originales | 2-3 meses | Medio | Con contenido y pruebas |
| 8 juegos originales | 4-6 meses | Medio-alto | Contenido, arte, voces |
| Port nativo de Jackbox | 12-24+ meses | Extremo | No recomendado |
| Emulación x86 en Android | 3-6 meses I+D | Extremo | No producto |

Riesgos transversales:

- **Legal:** copyright, marcas, EULA de Jackbox, Steam Subscriber Agreement.
- **Técnico:** Scaleform/FMOD/CRIWARE/Steam no disponibles en ARM.
- **Producto:** el usuario espera “Jackbox” y recibe “inspirado en”; hay que comunicarlo bien.
- **Contenido:** los prompts de Jackbox son humanos y con gracia; generar contenido original de calidad requiere curación, no solo LLM.
- **Moderación:** texto libre de jugadores; hacen falta filtros, reportes y kick.
- **Accesibilidad:** Jackbox ofrece subtítulos, sensibilidad al movimiento, timers extendidos/sin timers; Vision+ debe decidir paridad.
- **Latencia:** el polling de 700 ms sirve para turnos, no para ritmo/ordenamiento en tiempo real.
- **Mantenimiento:** si se elige streaming, depende de que Jackbox/Steam sigan funcionando.

---

## 9. Hoja de ruta recomendada

### Fase 0 — Decisión (1 día)

- Confirmar si el objetivo es **jugar los juegos reales** o **tener juegos de fiesta propios**.
- Revisar legal antes de distribuir cualquier cosa.
- No tocar el motor X4K ni las zonas prohibidas.

### Fase 1 — Hub “Sala Jackbox” (3-5 días)

Entregable:

- Tarjeta “Sala Jackbox” en Vision+ Play/Home.
- Detección de apps oficiales instaladas y botón para abrirlas.
- QR a `jackbox.tv` + campo de código + instrucciones.
- Botón “Transmitir desde PC” (abre Steam Link/Moonlight o muestra guía).
- Tests de `GameCatalog`/wiring; sin assets de Jackbox.

Criterio de aceptación: en el Chromecast HD se abre el hub, se lanza una app instalada y se muestra el QR; sin crashes.

### Fase 2 — Streaming del PC (2-4 semanas)

Entregable:

- Host Agent en el PC (pairing, lista de juegos, launch/stop/status).
- Vision+ TV muestra el stream (vía app externa en MVP).
- Guía de red y audio.
- Prueba real con 2-3 teléfonos.

Criterio: jugar una partida completa de Quiplash 3 o Fibbage 4 con video/audio estables en LAN.

### Fase 3 — 4 juegos originales (6-10 semanas)

Orden:

1. Respuesta Ingeniosa (Quiplash-like).
2. La Encuesta (Survey-like).
3. Trivia Temporal (Timejinx-like).
4. Texto Roto (FixyText-like).

Criterio: 4 juegos en el catálogo, tests backend/Android, secretos filtrados por rol, moderación básica, voz/arte originales.

### Fase 4 — Profundizar (opcional)

- Tee K.O.-like y Quixort-like.
- WebSocket/SSE para tiempo real.
- Audiencia escalable (si hay uso streamer).
- Más idiomas.

### Fase 5 — Negocio (paralelo)

- Contacto con Jackbox para licencia/app cloud.
- Si hay acuerdo, integrar su app/build oficial; si no, seguir con originales.

---

## 10. Notas de integración técnica para Vision+

Si se aprueba la Fase 1:

- **Launcher:** `launcher/src/main/java/com/chronosbvrx/visionplus/home/data/AppsRepository.kt` ya resuelve `getLeanbackLaunchIntentForPackage` y `getLaunchIntentForPackage`; se puede reutilizar para abrir una app de Jackbox.
- **Catálogo de juegos:** hoy está duplicado en `server/api/games.py` (`catalog()`) y `app/.../data/games/GameCatalog.kt`. Cualquier juego nuevo debe ir en ambos y en `GAMES`.
- **Salas:** `server/api/games.py` (`Room`, `create_room`, `tick`, `snapshot`) es el patrón; añadir un juego implica extender el estado y las acciones.
- **Endpoints:** `server/api/app.py` expone `/v1/game/*`; mantener el contrato y los tests.
- **TV/teléfono:** `TvGamesScreen.kt`, `GameModesScreen.kt`, `GameRoomScreen.kt`, `GameV2Screens.kt`; el foco D-pad tiene guardián (`TvFocusTest`).
- **No tocar:** `data/offline/*`, `X4KTitan.play/stop/queryStatusUrls/beanPlayUrl`, `X4KPlayerAty.onCreate/play`, `VisionApp.onCreate`, proceso `:x4k`.
- **No publicar OTA** sin instrucción explícita; la firma histórica no se cambia.
- **Tests:** backend `pytest server/tests`; Android `:app:testNormalDebugUnitTest`; launcher; añadir casos de snapshot/secretos y de catálogo.

Para la Fase 2:

- El host agent no debe exponerse a internet; pairing por código y allowlist de apps.
- Usar `steam -applaunch <appid>` para lanzar; verificar que Steam esté abierto.
- Si se usa Moonlight, revisar GPLv3 antes de enlazarlo; para MVP, app externa.
- Si se hace WebRTC propio, presupuestar 2-3 meses y pruebas en Chromecast HD.

Para la Fase 3:

- Reutilizar `game_decks.py` y el pipeline de arte/TTS.
- Añadir moderación de texto (lexicón, reportes, kick) antes de texto libre.
- Medir latencia y tamaño de snapshot; el polling actual es suficiente para turnos.
- No copiar prompts, respuestas ni arte de Jackbox; generar contenido original y revisarlo.

---

## 11. Legal (lectura obligatoria antes de distribuir)

- **Código y assets:** propiedad de Jackbox Games. No se pueden copiar, extraer, traducir, reempaquetar ni distribuir sin licencia.
- **Marcas:** “Jackbox”, “Quiplash”, “Fibbage”, “Tee K.O.”, “Trivia Murder Party”, “Survey Scramble”, etc. son marcas de Jackbox. No se pueden usar para branding de clones.
- **Mecánicas:** las reglas e ideas generales no suelen ser copyrightables, pero su **expresión** (preguntas, textos, arte, audio, personajes, UI) sí. Un clon debe ser original en la expresión.
- **Steam:** la licencia de Steam es personal; hacer streaming de tu propia copia a tu propia tele es el uso previsto de Steam Link/Remote Play. Automatizarlo para terceros o distribuirlo como servicio requiere revisar el Steam Subscriber Agreement.
- **Jackbox EULA:** puede prohibir ingeniería inversa y redistribución; el análisis de interoperabilidad no equivale a licencia.
- **Google Play/Android TV:** integrar apps oficiales por deep link suele ser aceptable si el usuario las compró; no empaquetar sus APK.
- **App cloud:** si se integra, debe ser por acuerdo o por deep link permitido; no por scraping.

Este documento no es asesoría legal. Antes de una OTA o de un producto comercial, consultar a un abogado de propiedad intelectual.

---

## 12. Pruebas y evidencias

Evidencias locales usadas:

```text
/media/chronos/Disco Duro/SteamLibrary/steamapps/common/The Jackbox Party Starter/
/media/chronos/Disco Duro/SteamLibrary/steamapps/common/The Jackbox Party Pack 9/
/media/chronos/Disco Duro/SteamLibrary/steamapps/common/The Jackbox Party Pack 10/
/media/chronos/Disco Duro/SteamLibrary/steamapps/common/The Jackbox Survey Scramble/
```

Comprobaciones:

- `file`, `readelf`, `ldd`, `strings` sobre los ejecutables.
- `sha256sum` de los 8 ejecutables (OpenGL/Vulkan por pack).
- Lectura de `gameManifest.json`, `jbg.config.jet`, `settings.json`.
- Descompresión de SWF (`CWS` → zlib) y descompilación AS3 con JPEXS Free Flash Decompiler.
- Lectura de `APIClient.as`, `WSClient.as`, `EcastUtil.as`, mensajes `room/*`, `client/*`.
- Inspección del bundle `https://jackbox.tv/CM0nr-Mh.js` para confirmar el WebSocket de controllers.
- Comparación con `docs/ARQUITECTURA.md`, `docs/PLAN_JUEGOS.md`, `docs/ENTREGA_GAMES_FASE4.md`, `docs/HANDOFF_MAESTRO.md`.
- No se pudo validar en hardware Android TV en esta sesión: `adb devices` no mostró dispositivos. Las pruebas físicas siguen pendientes.

---

## 13. Conclusión

**Portar Jackbox a Vision+ no es un problema de “adaptar el APK”.** Es un problema de motor propietario, runtime Scaleform, DRM de Steam, 16.29 GiB de assets y licencias. La respuesta corta es **no**.

Pero el objetivo real —“que en Vision+ se pueda jugar a juegos de fiesta como Jackbox”— sí se puede cumplir:

- **Para jugar los juegos reales:** apps oficiales de Android TV o streaming del PC. Rápido, legal y conserva las compras.
- **Para que Vision+ tenga su propio catálogo:** juegos originales inspirados en las mecánicas, con el framework de Vision+ Play que ya funciona. Es la vía más sólida y defendible.
- **Para una integración oficial:** acuerdo con Jackbox; su app cloud es la vía natural, pero no está garantizada en Android TV.

La decisión recomendada es **no portar** y ejecutar en paralelo:

1. Hub de acceso (apps oficiales + streaming).
2. Cuatro juegos originales (Quiplash-like, Survey-like, Timejinx-like, FixyText-like).
3. Evaluación comercial con Jackbox solo si el producto crece.

---

## Apéndice A — Glosario

| Término | Significado |
|---|---|
| **Scaleform GFx** | Runtime de Flash/AS3 propietario de Autodesk, usado por el motor de Jackbox |
| **AS3** | ActionScript 3, lenguaje de los SWF |
| **ecast** | Servicio de salas/relay de Jackbox (`ecast.jackboxgames.com`) |
| **USM** | Contenedor de video CRIWARE Sofdec |
| **FMOD** | Motor de audio y bancos de sonido |
| **Steamworks** | SDK de licencias/logros/DRM de Steam |
| **Leanback** | Modo de Android TV para apps de TV |
| **X4K/Titan/Ranger** | Motor nativo de video de Vision+, no un runtime de juegos |
| **Vision+ Play** | Módulo de juegos de fiesta de Vision+ (7 juegos) |

## Apéndice B — Preguntas abiertas

1. ¿El objetivo es jugar los **juegos reales de Jackbox** o tener **juegos de fiesta propios**?
2. ¿Vision+ se distribuye a terceros o es un proyecto personal/familiar? (cambia el riesgo legal)
3. ¿El PC puede quedar encendido y en la misma red durante las partidas?
4. ¿Hay presupuesto/contacto para una licencia de Jackbox?
5. ¿Se acepta que los juegos originales se llamen distinto y no usen la marca Jackbox?
6. ¿Se quiere priorizar texto (Quiplash/Survey) o dibujo (Tee K.O.)?

## Apéndice C — Hallazgos menores

- **`gameId` no es único por minijuego.** En Pack 9, Junktopia y Fibbage 4 comparten `gameId`; en Pack 10, Hypnotorious y Timejinx comparten otro. El campo que distingue el juego es `gameTag` (`antique-freak`, `fourbage`, `us-them`, `time-trivia`). Si alguien intenta identificar juegos por `appId`, va a fallar.
- **Los `.jet` no están cifrados.** Son JSON legibles; la extensión es solo una convención del motor.
- **`Picker` no es un juego.** Es el menú/selector compartido de cada pack; ocupa 1.2-1.5 GB y no debe contarse en el catálogo.
- **Los `gameManifest.json` incluyen credenciales de terceros** (Sentry DSN y clientSecret de EGS). No se reproducen; es un hallazgo de seguridad colateral.
- **`joinUrl` no siempre está normalizado.** Algunos juegos usan `jackbox.tv` y otros `JACKBOX.TV`; el cliente debe normalizar.
- **El motor es compartido entre packs.** Las rutas de compilación (`jbg-common`, `Perforce/TJPP7`) y las clases comunes de ecast se repiten; los packs son revisiones del mismo motor, no motores distintos.
- **El soporte de español existe en contenido** (`es`, `es-XL`), pero es contenido de Jackbox; no se puede reutilizar sin licencia.

## Apéndice D — Fuentes externas consultadas

- 9to5Google, “Jackbox Party Pack returns to Google TV & Android TV with Party Pack 10”, 2023-11-17.
- Wikipedia, “The Jackbox Party Pack” (plataformas y packs).
- Jackbox Games, “Jackbox Games is Coming to Your Smart TV… for free”, 2026-04-13.
- AWS for Games, “Jackbox Games unlocks new opportunities with Amazon GameLift Streams”.
- jackbox.tv (cliente web y bundle JS).
- Documentación interna de Vision+: `docs/ARQUITECTURA.md`, `docs/HANDOFF_MAESTRO.md`, `docs/PLAN_JUEGOS.md`, `docs/ENTREGA_GAMES_FASE4.md`.

---

**Fin del análisis.**
