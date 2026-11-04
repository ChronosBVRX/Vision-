# Vision+

> **Aviso de migración (2026-11-04):** este repositorio es el canal anterior. La distribución
> pública, los APK y la OTA viven ahora en
> [**ChronosBVRX/Vision-Releases**](https://github.com/ChronosBVRX/Vision-Releases).
> Las versiones anteriores a **2.5.247** quedarán descontinuadas el **9 de noviembre de 2026**.
> Actualiza desde el canal nuevo.

**Vision+** es una plataforma de streaming y entretenimiento multimedia para Android (Smart TV / Android TV / Fire TV y dispositivos móviles) desarrollada por **ChronosBVRX**. Su interfaz es intuitiva y moderna, construida con Jetpack Compose en la paleta oficial Vision+ (azul `#2D7DF6` sobre negro profundo).

- **Catálogo Global en la Nube**: Miles de películas, series completas por temporadas, anime y novelas conectados al índice del servidor VPS, con puntuaciones IMDb/TMDB y estrenos curados.
- **TV en Vivo Premium**: Más de 1,000 canales de televisión organizados automáticamente por categoría y país, con guía EPG interactiva en tiempo real.
- **Deportes en Directo**: Agenda deportiva en vivo, cruce automático con la guía EPG, filtro de ligas con escudos y logotipos oficiales, y selector de señales por país y operadora.
- **Buscador Inteligente Universal**: Búsqueda insensible a acentos (`unaccent`), multicriterio por título, director o elenco, con soporte nativo de dictado por voz en Smart TV y móviles.
- **Motor de Reproducción Nativo**: Reproducción fluida con aceleración por hardware, duraciones exactas en transmisiones HLS, salto de intro/outro y selección de pistas de audio y subtítulos.
- **Navegación Blindada para Smart TV**: Experiencia nativa con control remoto D-pad, rescate automático de foco ante modales o tecla Atrás, y optimización de memoria para dispositivos modestos (Chromecast, TV Boxes).

## Compilar

Requisitos: **JDK 17 o superior** y **Android SDK** (API 35/36).

```bash
./gradlew assembleNormalDebug        # Genera app-normal-debug.apk (pruebas)
./gradlew assembleNormalRelease      # Genera APK de producción optimizado (R8)
./gradlew testNormalDebugUnitTest    # Ejecuta la suite de pruebas unitarias
```

- **Firma de release**: se usa la **llave histórica de la app** (`visionplus-historic.keystore`, alias
  `androiddebugkey`) para que las actualizaciones sigan siendo directas sobre la base instalada; vive en
  `.visionplus` (fuera del repo) y `keystore.properties` (gitignored) la apunta. También acepta
  `VISION_KEYSTORE_FILE`, `VISION_KEYSTORE_PASSWORD`, `VISION_KEY_ALIAS` y `VISION_KEY_PASSWORD` como
  variables de entorno (CI). Sin llave, el release se firma con debug para poder validar R8 sin exponer
  secretos. **No cambiar la firma**: rompería el update de los usuarios actuales.
- **R8**: el motor X4K (DEX en assets) se carga en runtime; `app/proguard-rules.pro` conserva el
  puente, `okhttp3legacy` y `kotlinx.serialization`. No quitar esas reglas.

- Paquete: `com.chronosbvrx.visionplus`
- Kotlin + Jetpack Compose (+ Compose for TV), AndroidX Media3 (ExoPlayer), Coil, OkHttp.

## Arquitectura

```text
app/src/main/java/com/chronosbvrx/visionplus/
├── data/          # Catálogos, canales en vivo, agenda de deportes, EPG y persistencia
├── ui/brand/      # Identidad visual oficial, splash screen nativo y wordmarks
├── ui/phone/      # Pantallas optimizadas para celulares y tablets (Touch)
├── ui/tv/         # Experiencia completa para Smart TV (D-pad, riel lateral, tarjetas fijas)
├── ui/player/     # Reproductores nativos (VOD y Live) con OSD inmutable
└── ui/sports/     # Módulo interactivo de fútbol y deportes en directo
server/            # Backend VPS en FastAPI + PostgreSQL (índice, búsqueda, EPG y deportes)
webos/             # Port para Smart TV LG webOS (misma UI y motor vía Node)
pc/                # Port portable para Windows (misma UI/motor + lanzador y FFmpeg)
```

## Distribución

Las compilaciones firmadas, el launcher y la OTA se publican en el repositorio público
[Vision-Releases](https://github.com/ChronosBVRX/Vision-Releases). El código fuente y el backend
son privados.

> **Nota (2026-11-04):** el repositorio de código pasó a privado. Las versiones publicadas antes de
> esta fecha conservan la licencia con la que se distribuyeron; el código nuevo se mantiene como
> propietario. Consulta `LEGAL.md` y `LICENSE`.

## Legal y Licencia

- El código nuevo es propietario: consulta [LICENSE-PROPRIETARY.md](LICENSE-PROPRIETARY.md). El texto de [Apache-2.0](LICENSE) se conserva como referencia de las versiones publicadas antes del **2026-11-04**.
- Se conservan [NOTICE](NOTICE) y [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) para atribución de bibliotecas y componentes de software libre.
- El nombre "Vision+", la marca, isotipo, imagotipos y elementos visuales son propiedad exclusiva de **ChronosBVRX**. Todos los derechos reservados.

Ver también: [LEGAL.md](LEGAL.md) · [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md) · [CHANGELOG.md](CHANGELOG.md)
