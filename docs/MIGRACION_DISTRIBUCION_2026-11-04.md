# Migración de distribución — 2026-11-04

## Cierre del repositorio anterior

- **2026-11-04:** `ChronosBVRX/Vision-` pasó a **privado** por indicación del usuario.
- Verificado sin autenticación: repo y releases del canal anterior responden **404**.
- `ChronosBVRX/Vision-Releases` sigue **público** y es el único canal de descarga/OTA.
- Última estable del canal anterior: **2.6.1/vc494** (también publicada en el canal nuevo).
- El repo nuevo conserva Android 2.6.1, launcher 1.3.18, PC 0.5.0 (`pc-latest`) y LG webOS 0.3.0.

## Estado

- **Código:** `ChronosBVRX/Vision-` → **privado**.
- **Distribución pública:** `ChronosBVRX/Vision-Releases` (solo APK, launcher, `version.json` y documentación visible).
- **OTA:** la app y el launcher apuntan a `Vision-Releases`.

## Rate limiting desplegado (2026-11-04)

- Nuevo `server/api/rate_limit.py`: ventana deslizante de 60 s por IP y grupo de endpoint.
- Límites por defecto: índice 30/min, búsqueda 120/min, GeeSports 120/min, EPG 120/min,
  diagnóstico 30/min, configuración 20/min, resto 300/min.
- Se aplica en `server/api/app.py` como middleware; `/` y `/healthz` quedan exentos.
- La IP real se toma de `X-Forwarded-For` (la última entrada, la que añade Caddy).
- Desplegado en el VPS y verificado: 20 peticiones a `/v1/config` pasan y la 21 devuelve **429**;
  `/v1/index` y `/healthz` siguen en 200. **699 tests backend** en verde.
- El código vive en el árbol de trabajo (`server/api/rate_limit.py`, `app.py`, `Dockerfile`,
  `docker-compose.yml`, `.env.example`, `tests/`). **Pendiente commitearlo** junto con la tanda
  beta que ya estaba sin commitear, para no mezclar cambios.

## Actualización obligatoria 2.5.247 (en curso)

- Publicada **2.5.247** (versionCode **491**) en los dos repos.
- `VisionPlus.apk` SHA-256 `7b77ce4995bb7c49a42b2d9c03b90dbc66529ca4cd9c6b565486eb1a23311bc5`.
- `version.json` SHA-256 `d6a17cabca043d3024631af549368b72184ea142d008c905643bff1ddef92070`.
- Fecha límite del aviso: **2026-11-09 23:59:59 -06:00** (epoch ms `1794290399000`).
- Config remota aplicada: `min_version_code=491`, `migration_version_code=491`,
  `migration_deadline_ms=1794290399000` y el mensaje de descontinuación.
- El diálogo de la 2.5.247 no tiene botón «Cerrar» cuando la actualización es forzada.
- El repo anterior queda **público temporalmente** hasta que el usuario confirme el cierre. Los
  usuarios que no actualicen verán el aviso forzado en cada apertura; la **2.6.1** es la última
  estable publicada en el repo anterior.
- Después del cierre: `Vision-` vuelve a privado; `Vision-Releases` sigue público y es el único
  canal de OTA.

## Versiones publicadas en `Vision-Releases`

### Estable actual

- **2.6.1** (versionCode **494**, `v2.6.1`, **Latest**) — incluye el árbol de 2.6.0 (60 avatares,
  teclado en pantalla, capítulos al 85 %, calendario y USB) más los arreglos de Inicio, reproductor
  e instalador ADB de esta sesión.
- `VisionPlus.apk` **95,685,572 B** SHA-256 `e720fa8721cb2cad097a049d3143c102e82db3d065acbb038518ad2e0ecd84b2`
- `VisionPlusHome.apk` **1.3.18** (versionCode **41**) SHA-256 `1de5243610e433e5ab9e068ac3873a2c968a5037ba0165d8cbd2a2348cc7c48a`
- `version.json` 2,048 B SHA-256 `52d96f959b56728fda71c04cc57e8298867f721f5616648aae45de56aee6cdbc`
- LG webOS **0.3.0** `visionplus-webos_0.3.0_all.ipk` SHA-256 `90bfa16257c8363fa96a736043aa97fa1f39989850f4e743bc9a66204181f759`
- PC **0.5.0** `VisionPlusPC-Portable.exe` SHA-256 `e32b1dc2e6f9e14926e8bd07bce0091bd217520f396bee63cb9b5bbfbe1c923e` (canal `pc-latest` en los dos repos).
- Firma histórica `9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3`.
- `min_version_code=494` y `migration_version_code=494` en `remote_config`.

### Historial inmediato

- **2.6.0** (vc492, `v2.6.0`) fue la primera estable íntegra en el canal nuevo; quedó superada por
  2.6.1. Su APK SHA-256 `c14d861b9f4c2a45ee48253b20ed8c3076c66c7b526b8e67014c41ddf323886e`.
- **2.5.247** (vc491) fue una puente de distribución con un árbol reducido: perdió los 60 avatares, el teclado en pantalla, el visto al 85 %, Idioma/Subtítulos siempre visibles y el hero del calendario. Se conserva como release histórico, ya no es `Latest`.
- **2.5.246** (vc486) fue la puente de distribución inicial.
- **2.6.0-beta6** (vc491) y **2.6.0-beta5** (vc490) siguen como pre-release `beta`.

## Por qué hay dos versiones puente

- La estable 2.5.246 sube de 2.5.245 (vc485) a vc486.
- Beta4 estaba en vc489; Android no permite instalar la estable vc486 encima. Por eso beta5 sube a
  vc490 y conserva la base beta4.

## Actualización manual (una sola vez)

- Estable: https://github.com/ChronosBVRX/Vision-Releases/releases/latest
- Beta: https://github.com/ChronosBVRX/Vision-Releases/releases/tag/beta
- Launcher: https://github.com/ChronosBVRX/Vision-Releases/releases/latest/download/VisionPlusHome.apk

Los equipos que ya tenían la app necesitan instalar este APK una vez. Después la OTA sigue sola.

## Archivos de código tocados

- `app/.../data/AppUpdate.kt`
- `app/.../data/adb/TvLauncherPackage.kt`
- `app/.../data/homefeed/LauncherInstall.kt`
- `app/.../ui/Links.kt`
- `app/.../ui/about/AboutScreen.kt`
- `launcher/.../data/HomeUpdate.kt`
- `pc/electron/updater.js`
- `pc/tools/publish-pc-ota.sh`
- `tools/publish_ota.ps1`
- `.github/ISSUE_TEMPLATE/config.yml`
- `app/src/main/assets/vision_home.apk` (launcher 1.3.17 embebido)
- `beta/version.json`, `beta/NOTAS.md`

## Pendientes de seguridad

1. **Telegram:** la `TELEGRAM_STRING_SESSION` del `.env` viejo está en el historial privado. Terminar
   esa sesión en Telegram → Dispositivos y revocar la app en my.telegram.org.
2. **TMDB:** **rotada el 2026-11-04**. La key nueva ya está en `/opt/streaming-backend/.env`
   (hash `4452ce6283563bab504997eb068703b1df1026fb4d88e1d0b85e464a12cd74bb`) y `api`+`sync` se
   recrearon. La key anterior responde **401**: ya no sirve. No se imprime la key en claro.
3. **API key del backend:** la clave del APK es **la misma que usa el VPS** (verificado por hash).
   Viaja en el APK; no es un secreto. Añadir rate limiting por IP/dispositivo y tokens por
   instalación. Si se rota, el servidor debe aceptar la vieja y la nueva durante la transición.
4. **Llave del portal XTV:** viaja en el APK. No se puede rotar desde aquí; asumir que es extraíble.
   La única mitigación real es mover esa lógica al servidor o aceptar el riesgo.
5. **Keystore:** la contraseña ya **no vive en el proyecto**. Está en
   `~/.visionplus/keystore.properties` (600) y el helper `~/.visionplus/load-build-env.sh` (700)
   exporta `VISION_KEYSTORE_*`. Para compilar: `source ~/.visionplus/load-build-env.sh` y luego
   `./gradlew :app:assembleNormalRelease`. Verificado con `:app:packageNormalRelease` y la firma
   histórica. La llave sigue en `~/.visionplus/visionplus-historic.keystore`; respaldar offline y
   no compartir.
6. **Historial git:** el repo es privado, pero `.env` y `AGENTS.md` siguen en el historial. Si se van
   a añadir colaboradores, purgar con `git filter-repo` y rotar todo antes.

## PC y webOS

- **PC:** `pc/electron/updater.js` ya apunta a `Vision-Releases`, pero el `.exe` viejo sigue apuntando
  al repo anterior. Hay que compilar y publicar un `VisionPlusPC-Portable.exe` nuevo para migrar la
  OTA de PC.
- **webOS:** el IPK es manual; no tiene OTA que migrar.
