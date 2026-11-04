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

- **2.6.0** (versionCode **492**, `v2.6.0`, **Latest**) — actualización obligatoria que restaura las mejoras de beta3/4/6 perdidas en la puente 2.5.247.
- `VisionPlus.apk` **95,685,572 B** SHA-256 `c14d861b9f4c2a45ee48253b20ed8c3076c66c7b526b8e67014c41ddf323886e`
- `VisionPlusHome.apk` **1.3.17** (versionCode **40**) SHA-256 `73b32e1d113483fd167857836a4d01e646b074ddc4930daff1eaf87fca3e2159`
- `version.json` 2,313 B SHA-256 `7239f0b4122aa1f16eebea39e4b7b18d070b6c7f7ee6be6ad5d1702b60b9a95c`
- Firma histórica `9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3`.
- `min_version_code=492` y `migration_version_code=492` en `remote_config`.

### Historial inmediato

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
5. **Keystore:** `~/.visionplus/visionplus-historic.keystore` + `keystore.properties` (contraseña).
   Mantener fuera de cualquier ZIP/Drive/repo. La llave no se comparte.
6. **Historial git:** el repo es privado, pero `.env` y `AGENTS.md` siguen en el historial. Si se van
   a añadir colaboradores, purgar con `git filter-repo` y rotar todo antes.

## PC y webOS

- **PC:** `pc/electron/updater.js` ya apunta a `Vision-Releases`, pero el `.exe` viejo sigue apuntando
  al repo anterior. Hay que compilar y publicar un `VisionPlusPC-Portable.exe` nuevo para migrar la
  OTA de PC.
- **webOS:** el IPK es manual; no tiene OTA que migrar.
