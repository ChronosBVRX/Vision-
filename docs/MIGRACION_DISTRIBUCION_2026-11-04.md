# Migración de distribución — 2026-11-04

## Unificación de OTA en 2.6.58 (2026-11-19)

- Decisión del dueño: **una sola versión en todos los canales** para evitar mezclas de manifiestos.
- **2.6.58 / vc554** publicada en los cuatro puntos de actualización:
  - `ChronosBVRX/Vision-` → `v2.6.58` (estable) y `beta` (pre-release).
  - `ChronosBVRX/Vision-Releases` → `v2.6.58` (estable / `latest`) y `beta` (pre-release).
- Mismo `VisionPlus.apk` (125.742.916 B, SHA-256 `524a12cf…`), mismo `VisionPlusHome.apk`
  (3.504.168 B, SHA-256 `d453572e…`) y mismo `version.json` (`2.6.58` / `vc554`) en todos.
  Firma histórica `9593cb0d…` verificada en el build.
- Config remota: `ota_paused=false`, **`min_version_code=554`**, `migration_version_code=554` y
  mensaje de unificación. El forzado gana sobre la pausa y alcanza a todas las versiones por
  debajo de vc554 (incluidas 2.6.53 y 2.6.57), que convergen a 2.6.58.
- Nota operativa: los flags se cachean 6 h y se recargan al arrancar la app; en una TV que no vea
  el aviso hay que reiniciar el proceso (o la TV) para que lea `min_version_code=554`.

## Puente final del canal anterior (2026-11-18)

- Se publicó **2.6.58/vc554** en `ChronosBVRX/Vision-` con el tag **`v2.6.58`**, como release
  estable: es el nuevo `/releases/latest` del canal anterior (sustituye a 2.6.29/vc522).
- Assets: `VisionPlus.apk` 125.742.916 B, `VisionPlusHome.apk` 3.504.168 B y `version.json`
  (`{"version":"2.6.58","versionCode":554}`). Firma histórica `9593cb0d…` verificada en ambos APK.
- La build está compilada del árbol que apunta **exclusivamente** a `ChronosBVRX/Vision-Releases`:
  al instalarla, cualquier equipo que aún recibía OTA del canal anterior queda migrado al canal
  oficial sin perder perfiles, favoritos, historial ni descargas.
- **Canal nuevo intacto:** `Vision-Releases` sigue con `/releases/latest` = `v2.6.57` (vc553) y
  el tag `beta` en `2.6.0-beta6` (vc491). No se borraron ni reemplazaron assets ahí.
- Verificado sin autenticación: `/releases/latest` de los dos repos responde 200; el
  `version.json` del canal anterior declara 2.6.58/vc554 y el del canal nuevo 2.6.57/vc553.
- El repo anterior sigue público y **no archivado** (`git push` verificado 2026-11-16); cierre
  definitivo previsto para el **2026-11-19 23:59:59 -06**.

### Desbloqueo de la OTA (2026-11-18)

- Se encontró `ota_paused=true` en `remote_config`: con `min_version_code=522`, los equipos en
  vc522 o superior no veían ninguna oferta (`updateOffer`: `paused` sin `forced` = sin aviso),
  aunque ya existiera 2.6.58/vc554. Los equipos por debajo de 522 sí seguían recibiendo el forzado.
- El canario no podía rearmarlo por sí solo: faltaban `canary_paused_version` / `canary_paused_at`,
  así que `ota_paused` se quedaba `true` indefinidamente.
- Corregido en vivo: `POST /v1/config` con `{"ota_paused":false, "migration_message":"Instala
  Vision+ 2.6.58 del canal anterior…"}`. Verificado `ota_paused=false`, config `version=1795052644`.
- `min_version_code` se dejó en **522** a propósito: subirlo a 554 forzaría también a los equipos
  del canal nuevo en 2.6.57 (vc553) a reinstalar esa misma versión, sin que exista una 2.6.58 en
  `Vision-Releases`. La actualización de esos equipos sigue siendo normal cuando se publique ahí.
- Los flags se refrescan al arrancar la app (y cada 6 h): en la TV afectada basta con salir y
  volver a abrir Vision+ y pulsar «Buscar actualizaciones».

## Cierre del repositorio anterior

- **2026-11-04:** `ChronosBVRX/Vision-` pasó a **privado** por indicación del usuario.
- Verificado sin autenticación: repo y releases del canal anterior responden **404**.
- `ChronosBVRX/Vision-Releases` sigue **público** y es el único canal de descarga/OTA.
- Última estable del canal anterior: **2.6.1/vc494** (también publicada en el canal nuevo).
- El repo nuevo conserva Android 2.6.1, launcher 1.3.18, PC 0.5.0 (`pc-latest`) y LG webOS 0.3.0.

## Cierre definitivo del canal anterior (2026-11-12)

- Se publicó **2.6.29/vc522** como puente final en `ChronosBVRX/Vision-` (canal anterior).
  El repo se abrió a público, se actualizó `main` a `8c0d5e5` y se **archivó en solo lectura**.
  Sus releases siguen descargables (HTTP 200), pero no se publicarán más versiones ahí.
- La release **v2.6.29** del canal nuevo (`Vision-Releases`) es la estable actual y también
  la última que ofrece el canal anterior; después de instalarla, la app queda apuntando a
  `Vision-Releases`.
- `min_version_code=522` y `migration_version_code=522`; fecha límite del aviso:
  **2026-11-19 23:59:59 -06** (`1795154399000`).
- Verificado sin autenticación: `/releases/latest` de los dos repos = `v2.6.29`,
  `version.json` y `VisionPlus.apk` descargan, APK vc522 con la firma histórica.

## Estado

- **Código:** `ChronosBVRX/Vision-` → **público y archivado** (solo lectura).
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
