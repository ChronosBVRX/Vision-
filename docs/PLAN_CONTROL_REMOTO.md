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
- `data/remote/RemoteBus.kt` — bus en proceso: quién recibe las teclas, pantalla actual, estado.
- `data/remote/RemoteServer.kt` — la tele: UDP + TCP + emparejamiento + despacho.
- `data/remote/RemoteClient.kt` — el teléfono: descubrimiento, conexión, órdenes, estado.
- `ui/remote/RemoteScreen.kt` — interfaz del control remoto (teléfono).
- `ui/remote/RemoteCast.kt` — buscador de contenido para abrir en la tele.

Modificados:
- `AndroidManifest.xml` — permiso `CHANGE_WIFI_MULTICAST_STATE` y receptor del reproductor.
- `ui/Navigation.kt` — `Route.Remote`.
- `ui/PhoneApp.kt` — entrada en el menú lateral + pantalla.
- `MainActivity.kt` — arranca/para el servidor en TV, recibe teclas, atiende `open`/`live`.
- `ui/player/X4KPlayerAty.kt` — recibe teclas remotas mientras está al frente.

## Guardianes (tests)

- `RemoteProtocolTest` — ida y vuelta de cada mensaje, basura de red, lista blanca de teclas.
- `RemotePairingTest` — PIN, tokens (alta/validez/tope/orden).
- `RemoteDiscoveryTest` — baliza y sonda, nombres con espacios, puerto inválido.

## Regla de oro

Todo es **aditivo**: sin emparejar (o fuera de la televisión) nada cambia. El servidor solo
existe en la tele; el teléfono solo abre la pantalla si el usuario entra a ella.
