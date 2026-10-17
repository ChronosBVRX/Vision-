# Plan — Control remoto por LAN (teléfono → Smart TV)

**Pedido del usuario:** «Quiero hacer que si estás en la misma red con la app instalada en el
teléfono y abres la app en una Smart TV, detecte y puedas usarlo de control remoto.»

Decisiones del usuario (mismo día):
- **Alcance:** control remoto completo con **D-pad** + **acceso directo a contenido** (abrir un
  título o canal en la TV desde el teléfono) + play/pausa, ±10 s y cambio de canal.
- **Emparejamiento:** detección automática en la misma red + **PIN de 4 dígitos** (una sola vez;
  después queda emparejado).
- **Entrega:** lote completo con tests en verde y **OTA** publicada.

## Arquitectura

- **La TV escucha, el teléfono manda.** La tele levanta un servidor local mientras la app está
  abierta:
  - **UDP `48632`**: responde a la sonda de descubrimiento del teléfono (`VISIONPLUS-DISCOVER`)
    con su nombre, modelo y el puerto de control. Se sostiene con un `MulticastLock` (los
    drivers Wi-Fi filtran difusión si nadie la pide).
  - **TCP `48633`**: un JSON por línea, bidireccional (órdenes del teléfono, estado de la tele).
- **Inyección de teclas**: las órdenes `key` se traducen a `KeyEvent` y se entregan a la
  actividad que está al frente (`dispatchKeyEvent`), **reutilizando toda la navegación D-pad ya
  blindada** — cero cambios en el reproductor ni en las pantallas.
  - Menú (`MainActivity`) → se despacha en el mismo proceso.
  - Reproductor (`X4KPlayerAty`, proceso `:x4k`) → la tele emite un **broadcast interno** que el
    reproductor recibe mientras está al frente. Nunca se registran los dos a la vez, así que la
    tecla llega a quien debe.
- **Acceso directo a contenido**: la orden `open` se convierte en el deep link que ya existe
  (`visionplus://watch/…` para VOD) o en uno nuevo (`visionplus://live/…` para canales). Al
  lanzarlo como `Activity`, **funciona aunque el reproductor esté abierto** (la app entra por
  `onNewIntent`, camino ya probado por el canal Watch Next del launcher).

## Seguridad

- La primera vez la tele muestra un **PIN de 4 dígitos** (solo cuando un teléfono lo pide) y
  entrega un **token** que el teléfono guarda: después se reconecta solo.
- El servidor **solo acepta códigos de tecla de una lista blanca** (navegación, reproducción,
  volumen): una tecla arbitraria (POWER, HOME del sistema…) no entra.
- El broadcast interno va con `setPackage` y el receptor es dinámico (no exportado).

## Archivos

Nuevos:
- `data/remote/RemoteProtocol.kt` — mensajes (@Serializable) + códec + lista blanca de teclas (puro).
- `data/remote/RemotePairing.kt` — PIN y tokens (puro).
- `data/remote/RemoteDiscovery.kt` — sonda/baliza de descubrimiento (puro) + puertos.
- `data/remote/RemoteBus.kt` — bus en proceso: quién recibe las teclas, pantalla actual, estado
  (y `remoteOpenUriString`, puro: ref → deep link).
- `data/remote/RemoteServer.kt` — la tele: UDP + TCP + emparejamiento + despacho.
- `data/remote/RemoteClient.kt` — el teléfono: descubrimiento, conexión, órdenes, estado.
- `data/remote/RemoteDeepLink.kt` — `visionplus://live/…` → ruta de canal.
- `ui/remote/RemoteScreen.kt` — interfaz del control remoto (teléfono).
- `ui/remote/RemoteCast.kt` — buscador de contenido para abrir en la tele.

Modificados:
- `AndroidManifest.xml` — permiso `CHANGE_WIFI_MULTICAST_STATE` y host `live` del deep link.
- `ui/Navigation.kt` — `Route.Remote`.
- `ui/PhoneApp.kt` — entrada en el menú lateral + pantalla.
- `MainActivity.kt` — arranca/para el servidor en TV, recibe teclas, atiende `open`/`live` y pinta
  el PIN del emparejamiento.
- `ui/player/X4KPlayerAty.kt` — recibe teclas remotas mientras está al frente y publica «qué está
  viendo la tele».
- `ui/title/TitleScreen.kt` — botón «Ver en la TV» (solo con conexión viva).

## Guardianes (tests)

- `RemoteProtocolTest` — ida y vuelta de cada mensaje, basura de red, lista blanca de teclas.
- `RemotePairingTest` — PIN, tokens (alta/validez/tope/orden).
- `RemoteDiscoveryTest` — baliza y sonda, nombres con espacios, puerto inválido.
- `RemoteOpenTest` / `RemoteDeepLinkTest` — la orden `open` → deep link (VOD, canal y URL).
- `RemoteCastTest` / `RemoteTitleCastTest` — el buscador solo ofrece refs abribles y la ficha
  móvil ofrece «Ver en la TV» únicamente con la conexión viva.
- `RemoteServerFlowTest` — **de punta a punta sobre sockets reales** (lo que un emulador no puede
  dar: dos AVD no comparten red): PIN → token → reconexión silenciosa, teclas al frente por bus y
  por broadcast, tecla fuera de la lista que jamás llega, y `open` convertido en deep link.
- `RemoteClientWireTest` — las escrituras del cable ocurren en `remote-send`, nunca en el hilo que
  llama (ver «Lección» abajo).
- `PhoneMenuTest` — la pestaña «Control remoto» existe en el menú lateral, tras «En vivo».

## Lección (bug real cazado en vivo)

Los botones de la pantalla corrían en el **hilo principal** y escribían el socket ahí:
`NetworkOnMainThreadException`, tragada por un `runCatching`, y la orden se perdía **en silencio**.
El `HELLO` sí viajaba porque corre en el hilo de conexión — por eso «conectaba» pero ninguna tecla
llegaba. Fix: un **escritor único** (`Executors.newSingleThreadExecutor` llamado `remote-send`),
`socket`/`out` `@Volatile` y `synchronized(writer)`. Toda orden nueva pasa por ahí.

## Resultado (cerrado)

- **OTA v2.5.98 publicada** (vc334, 101 MB, firma histórica `9593cb0d…`), 528 pruebas Android en
  verde y verificación en emulador contra una tele simulada en el PC (`adb reverse tcp:48633`):
  PIN 4321 → token guardado → conectado; todas las teclas llegando (OK, Abajo/Arriba, Play/Pausa,
  Volumen+, Menú, Atrás); reconexión silenciosa sin PIN; y «Ver en la TV» desde la ficha de
  Spider-Man enviando `{"t":"open","ref":"xtv:…"}` correcto, recibido con título y póster.
- *Pendiente física*: probar teléfono + tele reales en la red de casa (el descubrimiento UDP real
  entre dos equipos es lo único que el emulador no pudo ejercer).
- *Limitación aceptada*: sin servicio en primer plano, si el proceso principal muere durante una
  reproducción larga el control remoto se cae (reabrir la app en la tele lo revive).
- *Nota*: `ui/remote/MiniPlayer.kt` (`RemoteMiniPlayer`, ya existente y conectado al `bottomBar`)
  es un remanente del acompañante simulado — con `VisionCompanion.pairedTvs` vacío y `foundTvs`
  fijo en `emptyList()`, **jamás se hace visible**. Candidato a limpieza junto con
  `ui/settings/DemoCompanion.kt`.

## Regla de oro

Todo es **aditivo**: sin emparejar (o fuera de la televisión) nada cambia. El servidor solo
existe en la tele; el teléfono solo abre la pantalla si el usuario entra a ella.
