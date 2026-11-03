# Coordinación Deportes ↔ Juegos (2026-10-31)

## Estado

- **Deportes:** cambio incluido en la beta combinada **2.5.210**.
- **Juegos:** backend de Fiesta **desplegado en el VPS** el 2026-10-31;
  `/v1/game/catalog` sirve los once juegos vigentes (cinco históricos + seis
  nuevos). La beta combinada se publicó como **pre-release**
  `v2.5.210-fiesta-beta8` y en el release con tag **`beta`** para que el switch
  de pruebas del celular y la tele la reciba desde el actualizador. La estable
  `/releases/latest` sigue en 2.5.204.
- **Estadísticas del partido:** panel a la derecha sobre los controles con
  posesión, tiros, tiros a puerta, córners, faltas, tarjetas, atajadas y
  alineaciones (ESPN). Sin botón de Pausa en vivo. La señal seleccionada se
  distingue con borde y etiqueta «Seleccionada». Arriba/Abajo cambia solo entre
  las señales disponibles del evento actual.

## Cambio de Deportes pendiente de incluir

- Selector de un partido (móvil y TV): se muestra **solo el nombre del canal y
  su formato** (`HLS` / `DASH · ClearKey`). Ya **no se muestra la URL** ni las
  cabeceras (`http…`, `user-agent=…`).
- Las fuentes declaradas sin dirección se ven como **«Sin señal»** y muestran
  «La fuente aún no publica su señal»; no se pueden sintonizar.
- Archivos:
  - `app/src/main/java/com/chronosbvrx/visionplus/ui/sports/GeeSportsTabs.kt`
  - `app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvGeeSportsTabs.kt`
  - `app/src/test/java/com/chronosbvrx/visionplus/ui/tv/GeePickDialogDuplicateTest.kt`
- Servidor de Deportes: ya desplegado (latido de metadatos con ETag, health y
  aviso al Telegram). No requiere OTA.

## Verificación de la beta publicada

- `./gradlew :app:testNormalDebugUnitTest` → **1,181 Android** en verde (suite completa) + pruebas dirigidas finales.
- `pytest server/tests` → **608 backend** en verde.
- Backend desplegado y `/v1/game/catalog` con los seis juegos nuevos.
- APK `VisionPlus-2.5.210-fiesta-beta8.apk` (2.5.210/vc450), firma histórica,
  motor X4K inyectado; pre-release, no OTA.

## Texto sugerido para las notas (sin detalles técnicos)

> **Deportes:** ahora ves el nombre de cada canal sin direcciones técnicas. Si una
> señal aún no está disponible, aparece como «Sin señal».
>
> **Juegos:** llega la nueva temporada de Vision+ Fiesta con sus juegos para
> jugar desde el teléfono y la tele.

No incluir en las notas: `link_names`, `events.txt`, ETag, `pro`, slots,
endpoints, worker, nombres de archivos ni métricas internas. Los usuarios normales
no los necesitan y solo generan ruido.
