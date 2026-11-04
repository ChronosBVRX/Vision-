# Migración de distribución — 2026-11-04

## Estado

- **Código:** `ChronosBVRX/Vision-` → **privado**.
- **Distribución pública:** `ChronosBVRX/Vision-Releases` (solo APK, launcher, `version.json` y documentación visible).
- **OTA:** la app y el launcher apuntan a `Vision-Releases`.

## Actualización obligatoria 2.5.247 (en curso)

- Publicada **2.5.247** (versionCode **491**) en los dos repos.
- `VisionPlus.apk` SHA-256 `7b77ce4995bb7c49a42b2d9c03b90dbc66529ca4cd9c6b565486eb1a23311bc5`.
- `version.json` SHA-256 `d6a17cabca043d3024631af549368b72184ea142d008c905643bff1ddef92070`.
- Fecha límite del aviso: **2026-11-09 23:59:59 -06:00** (epoch ms `1794290399000`).
- Config remota aplicada: `min_version_code=491`, `migration_version_code=491`,
  `migration_deadline_ms=1794290399000` y el mensaje de descontinuación.
- El diálogo de la 2.5.247 no tiene botón «Cerrar» cuando la actualización es forzada.
- El repo anterior queda **público temporalmente** hasta que el usuario confirme el cierre. Los
  usuarios que no actualicen verán el aviso forzado en cada apertura; la 2.5.247 es la última que
  se publica en el repo anterior.
- Después del cierre: `Vision-` vuelve a privado; `Vision-Releases` sigue público y es el único
  canal de OTA.

## Versiones publicadas en `Vision-Releases`

### Estable

- **2.5.246** (versionCode **486**)
- `VisionPlus.apk` SHA-256 `3e866f3437eb1a0a719151444b5df9479af28d136f1dfa5160a858e4469e0d0a`
- `VisionPlusHome.apk` **1.3.17** (versionCode **40**) SHA-256 `73b32e1d113483fd167857836a4d01e646b074ddc4930daff1eaf87fca3e2159`
- `version.json` SHA-256 `b0bd269ea26f3fd447b05292c615f3077c06d9926806aba11076937bf078f210`
- Firma histórica `9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3`.

### Beta

- **2.6.0-beta5** (versionCode **490**)
- `VisionPlus.apk` SHA-256 `87295782a8c2ad26db92d27d275940533b5248f2e0791ad34f4247301a0ee166`
- Tag `beta`, pre-release.
- Misma base que beta4 + cambio de URLs. Se instala encima de beta4 (vc489) sin borrar datos.
- Launcher 1.3.17/vc40.

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
2. **TMDB:** la API key del `.env` viejo **sigue siendo la misma que usa el VPS** (verificado por
   hash). Regenerarla y actualizar `/opt/streaming-backend/.env`; reiniciar `api` y `sync`.
3. **API key del backend:** la clave del APK es **la misma que usa el VPS** (verificado por hash).
   Viaja en el APK; no es un secreto. Añadir rate limiting por IP/dispositivo y tokens por
   instalación. Si se rota, el servidor debe aceptar la vieja y la nueva durante la transición.
4. **Llave del portal XTV:** viaja en el APK. No se puede rotar desde aquí; asumir que es extraíble.
   La única mitigación real es mover esa lógica al servidor o aceptar el riesgo.
5. **Keystore:** `~/.visionplus/visionplus-historic.keystore` + `keystore.properties` (contraseña).
   Mantener fuera de cualquier ZIP/Drive/repo. La llave no se comparte.
6. **Historial git:** el repo es privado, pero `.env` y `AGENTS.md` siguen en el historial. Si se van
   a añadir colaboradores, purgar con `git filter-repo` y rotar todo antes.

## PC y webOS

- **PC:** `pc/electron/updater.js` ya apunta a `Vision-Releases`, pero el `.exe` viejo sigue apuntando
  al repo anterior. Hay que compilar y publicar un `VisionPlusPC-Portable.exe` nuevo para migrar la
  OTA de PC.
- **webOS:** el IPK es manual; no tiene OTA que migrar.
