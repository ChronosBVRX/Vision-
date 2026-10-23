# Vision+ Home — launcher de Smart TV (opción B: APK aparte)

> Decisión del usuario (2026-10-23): **APK separado**, no un modo dentro de la app.
> Este documento fija la arquitectura y las fases. **No hay código de producto todavía.**

## Estado (2026-10-23) — PUBLICADO en la OTA 2.5.126

`VisionPlus.apk` (vc362, 101.2 MB) + `VisionPlusHome.apk` (v1.0.2 / vc3, 1.2 MB), los dos con la
firma histórica `9593cb0d…`. Fases 1-5 hechas; fase 6 verificada en emulador con el **release R8**
instalado (permiso `HOME_FEED: granted=true`, hero/filas/TV en vivo/apps reales, ficha y buscador
por deep link). **Pendiente**: prueba física en la ZTE y el Chromecast (elegir Inicio, reiniciar y
ver que se queda; abrir un canal y una ficha con el mando).

Lecciones que costaron un build:
- `Modifier.onFocusChanged` va **antes** del `clickable/focusable`; si va después, nunca observa el
  foco (el anillo blanco y el escalado no se pintaban).
- Cada host de deep link (`watch`, `live`, `title`, `open`) debe estar declarado en el
  `<intent-filter>` de `MainActivity`. Si falta, `startActivity` devuelve `START_CANCELED` y el
  launcher muestra «No se pudo abrir Vision+».
- El feed se refresca también **cuando llegan los canales** (`HomeFeedStore.refreshAsync(force)`),
  no solo al terminar el catálogo: si no, la fila de TV en vivo queda vacía hasta el próximo ciclo.
- El foco inicial del launcher se pide con **reintentos** hasta que el hero real está compuesto
  (el primer frame puede traer el hero de marca y el de tendencia llega después).

## 1. Qué es y qué no es

**Es** una pantalla de inicio para la tele: hero cinemático, «Seguir viendo», TV en vivo con
«AHORA», filas del catálogo, cuadrícula de apps instaladas y accesos al sistema. Todo con D-pad.

**No es** un segundo reproductor ni un segundo catálogo. Todo lo pesado (motor XTV, índice de
52k títulos, EPG propio, perfiles, descargas, sincronización) sigue viviendo en Vision+. El
launcher **pide datos y dispara intents**.

Esto es lo que hace que B sea barato: un launcher de ~15 MB, sin motor ARM, sin 34 MB de DEX,
sin cliente del portal. Si crashea, Vision+ sigue instalado y reproducible; si Vision+ falta, el
launcher degrada a lanzador de apps.

## 2. Por qué B no exige partir el módulo Gradle

La deuda de `docs/ARQUITECTURA.md` («`app` es un solo módulo de ~50k líneas») aplicaría si el
launcher reimplementara las pantallas. No es el caso: **comparte datos, no UI**. Lo único común
es un contrato de ~150 líneas (DTOs + llaves JSON), que cabe en un módulo puro de Kotlin sin
Android. No hay que extraer `:core:data` ni `:feature:catalog` para esta entrega.

## 3. Arquitectura

```
┌──────────────────────────────┐         ┌───────────────────────────────┐
│  VisionPlusHome.apk          │         │  VisionPlus.apk (sin cambios │
│  com.…visionplus.home        │         │  de comportamiento)           │
│                              │         │                               │
│  LauncherActivity (HOME)     │         │  HomeFeedProvider             │
│   ├─ hero + filas            │◄────────┤   authority  ….homefeed       │
│   ├─ apps instaladas         │  JSON   │   permission signature        │
│   ├─ reloj / perfil          │         │                               │
│   └─ emergencia              │         │  XtvHandoff / player residente│
│                              │         │  PlaybackHistory · EPG · VOD  │
│  caché local del feed        │         │                               │
└──────────────┬───────────────┘         └───────────────▲───────────────┘
               │  visionplus://watch/…?r=…               │
               │  visionplus://live/<code>?t=…&p=…       │
               └─────────────────────────────────────────┘
```

Sin `sharedUserId` (deprecado): provider propio + permiso `signature` + `exported=true` con
autoridad explícita. Ambos APK se firman con la **llave histórica**, requisito para que el
permiso `signature` se conceda.

## 4. El contrato (`:home-contract` + `HomeFeedProvider`)

Módulo nuevo `:home-contract` (Kotlin puro, sin Android): DTOs, llaves JSON, autoridad,
permiso y el constructor de deep links (`visionplus://watch|live`). Lo usan los dos APK, así
que un cambio de esquema rompe la compilación, no la tele.

`content://com.chronosbvrx.visionplus.homefeed/feed` devuelve **un solo JSON** `v1`:

| Bloque | Contenido | Uso en el launcher |
|---|---|---|
| `meta` | `version`, `profile`, `generatedAt`, `online` | chip de perfil, frescura |
| `hero` | hasta 5: `ref`, `title`, `backdrop`, `poster`, `year`, `rating`, `kind`, `synopsis` | hero estático |
| `continue` | hasta 10: `ref`, `title`, `poster`, `progressPct`, `resumeMs`, `episodeLabel` | fila 1 |
| `live` | hasta 10: `code`, `name`, `logo`, `now`, `next` | fila TV en vivo |
| `rows` | 4-6 filas: `id`, `label`, `items[]` (Top 10, Películas, Series, Anime, Porque viste X) | carruseles |
| `apps` | no viaja: lo resuelve el launcher con `PackageManager` | cuadrícula |

Reglas del contrato:
- **Un solo método**, tamaño objetivo < 80 KB. Nada de N llamadas al provider.
- **Versionado**: `v1` en la ruta (`/feed/v1`); el launcher ignora bloques desconocidos y cae a
  caché si `version` no es la suya.
- **Sin PII**: ni códigos de continuidad, ni tokens, ni rutas de archivo.
- `notifyChange` cuando cambia el historial, el perfil o la lista: el launcher refresca sin
  encuestas periódicas.

## 5. La app launcher

- `LauncherActivity`: `MAIN + HOME + DEFAULT + LEANBACK_LAUNCHER`, `stateNotNeeded`, `singleTask`,
  `excludeFromRecents`.
- Barra superior (estilo Apple TV): marca, Inicio · TV en vivo · Películas · Series · Apps, reloj
  y perfil. **El riel izquierdo se queda en Vision+**; el launcher nace con barra superior, así
  que el rediseño del shell no toca las 23 pruebas de foco existentes.
- Hero: arte + degradado + título + «Reproducir» / «Detalles». **Sin video** en v1 (el motor XTV
  es de instancia única; un preview en el launcher pelearía con el reproductor residente).
- Filas: Seguir viendo → TV en vivo ahora → Apps → Top 10 → Películas → Series → Anime.
- Apps: `queryIntentActivities` con `CATEGORY_LEANBACK_LAUNCHER` (+ `LAUNCHER` como respaldo),
  sin `QUERY_ALL_PACKAGES` (basta `<queries><intent>`). Favoritos y recientes en prefs del
  launcher; el propio Vision+ se oculta de la cuadrícula (o se marca como «Abrir Vision+»).
- Atrás en la raíz: **no sale**. Abre un menú corto (Apps · Ajustes · Inicio del sistema).
- Modo degradado: sin Vision+ instalado, sin permiso concedido o feed vacío → apps + «Instala
  Vision+» + «Abrir Ajustes». Nunca una pantalla negra.

## 6. Navegación (todo por deep links que YA existen)

| Acción | Intent |
|---|---|
| Reproducir película | `visionplus://watch/<ref>?t=&p=&c=&u=` |
| Reanudar capítulo | `…&s=&sx=&sl=&sn=&r=<ms>` |
| Abrir ficha | ficha desde el launcher: `watch` sin `r` (o `Route.Title` por un `host=title` nuevo) |
| Canal en vivo | `visionplus://live/<code>?t=<nombre>&p=<logo>` |
| Ajustes de Vision+ | `visionplus://open/settings` (host nuevo, 3 líneas en `MainActivity`) |

`MainActivity` ya resuelve `watch` y `live` (`launcherIntentRoute`). Los hosts nuevos son
aditivos: una tele con Vision+ vieja ignora el link y el launcher muestra «Actualiza Vision+».

## 7. Arranque y rendimiento (la regla que no se puede romper)

El provider vive en el proceso de Vision+. **Leerlo puede arrancar Vision+ en frío** (2-4 s en
la ZTE). Por eso:

1. Primer frame **siempre** desde la caché del launcher (`filesDir/feed.json` + apps cacheadas).
2. El provider se consulta **después**, en IO, con tope de 1.5 s; si tarda, se pinta la caché.
3. Objetivo medido: home visible en **< 500 ms** tras el botón Home, con catálogo o sin él.
4. Nada de video, nada de animaciones infinitas, `DevicePerformance` heredado por copia (el
   launcher no comparte código con la app, así que se replica la detección, ~40 líneas).

## 8. Actualizaciones y firma

- **Un release, dos assets**: `VisionPlus.apk` y `VisionPlusHome.apk` en el mismo tag. El enlace
  estable de la OTA no cambia.
- El launcher se actualiza solo (tiene INTERNET para los pósters): consulta el release, muestra
  un aviso discreto y usa el mismo instalador in-app.
- `tools/publish_ota.ps1` suma el segundo build y **verifica que las dos firmas sean la
  histórica** (`9593cb0d…`); si no coinciden, aborta (el permiso `signature` dejaría de
  concederse y el launcher quedaría ciego).
- Opcional: `/v1/config` con `launcher_min_version_code` para frenar una versión mala sin OTA.

## 9. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| El firmware devuelve el Inicio de Google tras actualizar | Ajuste «Poner Vision+ Home como Inicio» + guía de 3 pasos; re-elegir toma 10 s |
| El provider arranca Vision+ y retrasa el primer frame | Caché primero (regla de la sección 7) |
| Feed viejo o corrupto | `version` + `generatedAt`; si el JSON no parsea, se conserva la caché anterior |
| El launcher crashea y deja la tele sin Inicio | Crash guard propio, pantalla de apps de emergencia, «Inicio del sistema» siempre accesible |
| Firma distinta entre APK | Guardián en `publish_ota.ps1` (aborta) |
| Memoria en Chromecast HD | Sin video, filas perezosas, 3 filas visibles (`tvRowMetrics`), modo ligero |
| Doble OTA confunde al usuario | El launcher avisa; el release publica ambos y el aviso de Vision+ menciona el launcher |

## 10. Fases y esfuerzo

| Fase | Entrega | Días |
|---|---|---|
| 0 | Spike en tele/emulador (sección 11) | 0.5 |
| 1 | `:home-contract` + `HomeFeedProvider` + tests del contrato | 1.5 |
| 2 | Módulo `:launcher`: actividad HOME, barra, hero, filas con caché | 2 |
| 3 | Cuadrícula de apps (favoritos, recientes, lanzar) + emergencia | 1 |
| 4 | Deep links nuevos (ficha/ajustes) + arranque < 500 ms medido | 1 |
| 5 | Actualización dual + guardián de firma + `/v1/config` | 0.5 |
| 6 | Suite (contrato, caché, apps, Atrás, foco) + validación física | 1.5 |
| | **Total** | **~8 días** |

## 11. Spike previo (medio día, sin código de producto)

En la ZTE B866F y en el Chromecast con Google TV:

1. `cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`
   → quién puede ser Inicio hoy.
2. Instalar un launcher de prueba, `cmd package set-home-activity <pkg>/.<Act>`, botón Home,
   reinicio del equipo y volver a Home. ¿Se queda? ¿Lo revierte el firmware?
3. `pm list packages -3` y `cmd package query-activities -c android.intent.category.LEANBACK_LAUNCHER`
   → tamaño real de la cuadrícula de apps.
4. `dumpsys meminfo <pkg>` del launcher tras 10 min en reposo → veredicto de memoria.
5. (Opcional) intento de lectura del TvProvider del sistema para «Continuar viendo» ajeno:
   si el permiso no se concede, ese agregado se descarta y queda solo el nuestro (que ya
   funciona).

## 12. Decisiones abiertas

1. **Barra superior** (recomendada, es superficie nueva) o riel izquierdo para parecerse a Vision+.
2. **Apps**: todas las instaladas, o solo streaming/media + favoritos.
3. **Ficha desde el launcher**: abrir la ficha de Vision+ (host nuevo `title`) o ir directo a
   reproducir/reanudar como hace hoy el canal Watch Next.
4. **Entradas HDMI / Ajustes del sistema**: incluirlas en la cuadrícula de apps (validar por
   equipo) o dejarlas fuera de v1.
