# «Ahora en la tele» — control remoto con estado y progreso

Implementado sobre el control remoto LAN existente (`docs/PLAN_CONTROL_REMOTO.md`), sin VPS,
sin puertos nuevos y sin servicios de terceros.

## Qué añade

- Tarjeta **«Ahora en la tele»** en el móvil con póster, título, temporada/capítulo, minuto,
  duración, pausa y barra arrastrable.
- Transporte real: play/pausa, ±10 s, capítulo anterior/siguiente y salto absoluto.
- En vivo: insignia **EN VIVO**, programa actual de la EPG, play/pausa y canal anterior/siguiente;
  la barra de búsqueda no se muestra.
- **Audio** y **Subtítulos**: abren el selector nativo de la tele; se navega con el D-pad del
  teléfono.
- Barra flotante persistente en el resto de la app del móvil (`RemoteNowBar`): tocar abre el
  control remoto y el botón central pausa/reanuda.
- Se cubren los dos reproductores: XTV nativo (`X4KPlayerAty`, proceso `:x4k`) y ExoPlayer
  (`PlayerCore`: deportes, M3U y búnker offline).

## Cómo viaja

1. El reproductor publica `NowPlaying` cada ~1 s y al instante en play/pausa, seek, cambio de
   capítulo o de canal.
2. En `:x4k` el estado viaja por **Messenger** (`RemoteBridge`), no por broadcast: el broadcast
   se pierde de forma intermitente en HyperOS/POCO y una barra de progreso no puede depender de él.
3. El proceso principal guarda el estado en `RemoteBus.playback` y `RemoteServer` lo manda en el
   mensaje `now` **solo a teléfonos emparejados**.
4. El teléfono manda `seek` (minuto absoluto) y `cmd` (`playpause`, `next`, `previous`, `audio`,
   `subtitles`, `stop`). Las teclas del D-pad siguen usando la lista blanca de siempre.
5. Si el reproductor muere, el `DeathRecipient` del binder limpia el estado. Si no llega estado
   en 5 s, el servidor vuelve a mandar el nombre de la pantalla del menú.
6. El teléfono mantiene la conexión con un `ping` cada 5 s y detecta el socket muerto en 15 s.

## Archivos

- `data/remote/NowPlaying` + campos nuevos de `RemoteMsg` (`RemoteProtocol.kt`).
- `data/remote/RemoteBridge.kt` — puente bidireccional `:x4k` ↔ proceso principal.
- `data/remote/RemoteBus.kt` — estado de reproducción, sink de ExoPlayer y ruteo de órdenes.
- `data/remote/RemoteServer.kt` — `now` estructurado, solo autorizados; `seek`/`cmd`.
- `data/remote/RemoteClient.kt` — parseo del estado, `sendSeek`, `sendCommand`, heartbeat.
- `data/xuper/X4KApi.kt` — entrega el buzón al abrir el reproductor.
- `ui/player/X4KPlayerAty.kt` — publica estado, seek absoluto, capítulo anterior/siguiente,
  selectores de audio/subtítulos.
- `ui/player/PlayerScreen.kt` — estado y órdenes para ExoPlayer.
- `ui/remote/RemoteScreen.kt` — tarjeta «Ahora en la tele» y transporte.
- `ui/remote/RemoteNowBar.kt` — barra persistente del móvil.
- `ui/PhoneApp.kt` — auto-conexión a la tele vinculada y barra persistente.

## Pruebas

- `RemoteProtocolTest`: round-trip de `NowPlaying`, seek, comandos y estado de menú.
- `RemoteBridgeTest`: el estado cruza el `Bundle` del Messenger.
- `RemoteBridgeFlowTest`: reproductor → proceso principal → órdenes de vuelta y limpieza al salir.
- `RemoteServerFlowTest`: el teléfono emparejado ve el estado y sus `seek`/`cmd` llegan al sink.
- `RemotePlaybackWiringTest`: cableado de la tarjeta, la barra, el puente y el filtro de autorizados.

## Pendiente de prueba física

- TCL/ZTE/Chromecast + teléfono en la misma Wi-Fi: abrir un VOD, arrastrar la barra, pausar,
  cambiar de capítulo, abrir audio/subtítulos y probar un canal en vivo.
- Medir la latencia del seek y confirmar que el estado del mando de la tele se refleja en el
  teléfono sin reconectar.
- Probar el caso HyperOS/POCO (el motivo del puente Messenger).
- Confirmar que al salir del reproductor el teléfono vuelve a «En la tele: Inicio».
