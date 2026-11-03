# Vision+ 2.5.194 — estable: todo lo pendiente, consolidado

**Versión estable.** Reúne lo que estaba en el árbol de trabajo y en las betas 2.5.187–2.5.193, con el launcher 1.3.11 y las voces nuevas de la narradora. Sin regresiones: 1132 pruebas Android + 170 del launcher + 22 del contrato + 527 del backend en verde.

## Launcher 1.3.11
- **Atrás desde el catálogo**: el primer Atrás sube el foco a la barra superior (la pestaña activa se desplaza a la vista y se enfoca) y el segundo vuelve a Inicio.
- **OK largo a prueba de cualquier mando**: el menú contextual se queda abierto, el primer OK nuevo lo usa y el KeyUp de la pulsación larga no activa nada.
- **Actualizador OTA interno**: comprueba el release, lee la versión real del APK y solo ofrece instalar si es más nueva (nunca un downgrade). La comprobación vive en Ajustes de Vision+ Home → Actualizaciones.
- Centro de control y ajustes sin filas de actualización duplicadas.

## «Qué está reproduciendo» en la red local
- mDNS/Bonjour **`_visionplus._tcp`**, nombre **`Vision+ - <dispositivo>`**, puerto **8765**, TXT `app=Vision+`, `version=1`, `path=/now-playing`; se re-registra al cambiar de red.
- `GET /now-playing` (solo lectura, solo LAN) con `app, device, state, channel, program, description, position, duration, updated` y **`artwork`/`artwork_type`**:
  - TV en vivo → **logo real del canal** (`artwork_type: "logo"`).
  - VOD/película → **póster real** (`artwork_type: "poster"`).
  - Sin imagen → `artwork: ""`; nunca base64.
  - URLs absolutas; el arte local se sirve en `http://<IP>:8765/artwork/<clave>`.
- `Cache-Control: no-store` en el estado y `public, max-age=3600` en el arte.
- Servicio en primer plano: sigue disponible si la app queda en segundo plano. No toca motor, DRM ni reproducción.

## App
- **OK largo en toda la app (tele y móvil)**: menú de **Ver ficha / Favoritos / Ver más tarde** en Inicio, secciones, categorías, Mi lista, buscador, descargas y ficha; en el móvil, mantener pulsado un canal de TV en vivo lo marca/desmarca como favorito.
- **Deep links del launcher restaurados**: `visionplus://watch|live|title|open` ya no comparten el `pathPrefix` de Vision+ Play; canales, películas, «Seguir viendo», ajustes y secciones abren desde Vision+ Home.
- **Deportes**: favoritos por equipo, búsqueda, recordatorios 10/30/60 min, agenda Hoy/Mañana y NBA/EuroLeague/NFL/MLB/NHL; categoría **Motor** con F1, MotoGP, NASCAR, WRC y WorldWCR.
- **UX**: «En vivo» con menú TV/Deportes, pestaña Juegos, Atrás en dos pasos, «Seguir viendo» al tope, hero cubierto y «Ahora en la tele» en el móvil.
- **Voces de la narradora**: 200 audios AAC regenerados con Qwen3-TTS (6 tomas por línea, WER 0.07, 4.2 MB en total) con los mismos `R.raw`; se reproducen en la tele con `MediaPlayer`.

## Verificación
- **1132 Android + 170 launcher + 22 contrato + 527 backend** en verde.
- APK release R8 con el motor inyectado (5 DEX), firma histórica `9593cb0d…`.
- Verificado en el Chromecast HD: app y launcher arrancan, los deep links abren ficha/canal, el OK largo mantiene el menú, Atrás sube a la barra y `/now-playing` responde con estado, programa, minuto y arte.

— ChronosBVRX
