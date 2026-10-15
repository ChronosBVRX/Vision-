# OTA v2.5.78 — Fin de las patadas de sesión + crash R8 del motor en release

## Causa 1 (sesión): tormenta de mints por SN compartido — VERIFICADA
- La TV tenía un "slot propio" que en realidad contenía el SN COMPARTIDO (`2392fe…`,
  cuenta `967111326`): el pool le entregó el mismo SN que usan el resto de equipos y
  el worker. El portal admite UNA sola sesión por SN: cada mint patea (`aaa100083`)
  a los demás (probado: un mint propio muere a los ~2.5 min).
- Ese "ya hay slot local" bloqueaba para siempre el alta derivada (v2.5.73), el único
  camino a una sesión privada.
- La reproducción necesita la sesión estable 20-60 s; pateada a medio camino, el motor
  se queda sin segmentos (`sin-primer-cuadro`, 40+ eventos en el VPS) y el auto-reparado
  rotaba el motor (gen 2→12) y re-acuñaba a la fuerza, realimentando la tormenta.
- Verificado: tras migrar, la TV corre con cuenta propia (`969757642`), cero
  renovaciones en 2 h, VOD con video+audio y TV en vivo con video.

## Causa 2 (crash): colisión R8 contra el DEX del motor — VERIFICADA con stack
- En release, `onPrepared` del motor (`yc.e$h`) arranca un `Observable.interval`
  (RxJava dentro del DEX del motor) cuyo `SchedulerPoolFactory` llama al método
  estático `a(AtomicReference,...)` de la clase `k4.x` (okhttp ofuscado del proveedor).
- R8 había renombrado `com.google.crypto.tink.proto.AesGcmKey` a `k4.x`: con
  parent-first, el motor resolvía la NUESTRA en vez de la suya → `NoSuchMethodError`
  en `k4/x` → muerte del proceso `:x4k` 1 s después de elegir decodificador.
  En debug no hay ofuscación y carga la suya: por eso el debug reproducía.
- Stack completo capturado en el A32 (proceso `:x4k`, v314) y gemelo en la otra TV.
- Fix: `-keep class com.google.crypto.tink.**` (+ dontwarn de sus deps no
  empaquetadas) — mapping verificado sin `k4.x` generado; release verificado con
  video en el A32.

## Fix (app)
- `DeviceSlot.isShared()`: detecta el SN compartido.
- `SlotClient.ensureSlot`: jubila el slot compartido local y completa el alta
  derivada (sesión privada por equipo); reclamo y pool ignoran SN compartido.
- Sin red o si el portal rechazara: sigue en modo compartido como antes.

## Operador (VPS, ya aplicado)
- Worker con SN propio derivado (`1ec7e0c1…`, user `969755826`) vía
  `POST /v1/slots/worker`: ya no patea a las apps (migra solo en su próxima
  revalidación; sin reinicios).
- No se reparten más SN compartidos del pool (la app los ignora desde esta versión).

## Verificación
- 351 Android + 122 backend en verde (incl. `isShared`).
- TV ZTE B866F (root): VOD Spider-Man con video+audio (4.6M frames), Azteca 7 en
  vivo con transmisión real, NBA Eventos; A32: Spider-Man con video en release.
- Firma histórica `9593cb0d…`, versionCode 314.
