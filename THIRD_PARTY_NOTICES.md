# Third-party notices

This repository's own code is covered by `LICENSE` (Apache-2.0). The libraries below keep their own
licenses. The "Vision+" name and logo are not licensed (see `NOTICE`).

## Libraries in this app

Downloaded by Gradle at build time; none of the libraries is vendored in this repository. The
per-model cleanup lists are the exception: they travel inside the APK under
`app/src/main/assets/debloat/` and keep their own MIT notices.

| Library | License |
|---|---|
| Kotlin standard library, kotlinx.coroutines | Apache-2.0 |
| AndroidX Core, Core SplashScreen, Activity Compose, Lifecycle | Apache-2.0 |
| Jetpack Compose (UI, Foundation, Material 3, Material Icons) | Apache-2.0 |
| Compose for TV (androidx.tv:tv-material) | Apache-2.0 |
| AndroidX Media3 (ExoPlayer, UI) | Apache-2.0 |
| Coil (coil-compose) | Apache-2.0 |
| OkHttp 4 / kotlinx.serialization | Apache-2.0 |
| Kadb (cliente ADB y emparejamiento inalámbrico) | Apache-2.0 |
| Conscrypt (TLS para el emparejamiento de Kadb) | Apache-2.0 |
| BouncyCastle (llave ADB y certificados) | MIT |
| Plus Jakarta Sans (fuente incluida en Vision+ Home) | SIL Open Font License 1.1 |
| Fotografías deportivas de referencia (Unsplash, incluidas en Vision+ Home) | Unsplash License |
| Gradle wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`) | Apache-2.0 |
| JUnit 4 (tests only) | EPL-1.0 |
| JSON-java / org.json (tests only) | Public domain |
| Hamcrest Core (tests only) | BSD-3-Clause |
| Robolectric (tests only) | MIT |
| Compose UI Test (tests and the debug build's test manifest only) | Apache-2.0 |
| Listas de limpieza por modelo de TV (`app/src/main/assets/debloat/*.txt`) | MIT (TidyTV / CleanYourTV) |
| Motor y reglas de limpieza adaptados (`data/debloat/`) | MIT (TidyTV) |

## Herramientas de generación de contenido (no se distribuyen en el APK)

| Herramienta / modelo | Uso | Licencia |
|---|---|---|
| Qwen3-TTS-12Hz-1.7B-VoiceDesign (`tools/generate_fiesta_voice_qwen.py`) | Síntesis de voz local para los presentadores de Fiesta | Apache-2.0 |
| Qwen3-TTS-12Hz-1.7B-Base | Clonación de las referencias de voz | Apache-2.0 |
| ComfyUI + Juggernaut XL v9 (SDXL) | Generación local de arte IA para Fiesta | ComfyUI GPL-3.0; modelo con su licencia Open RAIL++-M |
| Piper / voces `es_MX-*` | **Retirados**; ya no se usan en Fiesta | — |
| ffmpeg | Codificación AAC/WebP y procesamiento de audio | LGPL/GPL según build |
| Node.js (runtime portable de Vision+ PC) | Servidor local del port de Windows | MIT |
| FFmpeg (runtime portable de Vision+ PC) | Transcodificación TS/HEVC/HLS a MP4 H.264 | LGPL/GPL según build |
| Electron / Chromium (runtime del `.exe` único) | Ventana, bandeja y motor web del portable de Windows | MIT / BSD-3-Clause y licencias de Chromium |
| Pillow | Generación de arte procedural | HPND |

Los audios, la música y el arte resultantes son originales de Vision+ y no se
copiaron de Jackbox ni de terceros. La app Vision+ incluye sus avisos de
licencias de software libre en su pantalla de Ajustes.

## TidyTV / CleanYourTV (MIT)

La limpieza por ADB y las listas por modelo se adaptaron de:

- **TidyTV** — https://github.com/jolan94/tidytv — Copyright (c) 2026 Winkle Studios y los
  contribuidores de TidyTV. Licencia MIT.
- **CleanYourTV** — https://github.com/Gathin23/cleanyourtv — Copyright (c) 2026 Gathin.
  Licencia MIT.

Los textos completos están en `app/src/main/assets/debloat/LICENSE-TidyTV.txt` y
`app/src/main/assets/debloat/LICENSE-CleanYourTV.txt`. Las listas se distribuyen sin cambios
para conservar la trazabilidad de lo que se probó en cada modelo.
