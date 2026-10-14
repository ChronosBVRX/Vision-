# Vision+

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

- **Firma de release**: coloca `keystore.properties` en la raíz (ignorado por git) con `storeFile`,
  `storePassword`, `keyAlias` y `keyPassword`; también acepta `VISION_KEYSTORE_FILE`,
  `VISION_KEYSTORE_PASSWORD`, `VISION_KEY_ALIAS` y `VISION_KEY_PASSWORD` como variables de entorno
  (CI). Sin llave, el release se firma con debug para poder validar R8 sin exponer secretos.
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
```

## Legal y Licencia

- El código abierto de la aplicación se distribuye bajo [Apache-2.0](LICENSE).
- Se conservan [NOTICE](NOTICE) y [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) para atribución de bibliotecas y componentes de software libre.
- El nombre "Vision+", la marca, isotipo, imagotipos y elementos visuales son propiedad exclusiva de **ChronosBVRX**. Todos los derechos reservados.

Ver también: [LEGAL.md](LEGAL.md) · [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md) · [CHANGELOG.md](CHANGELOG.md)
