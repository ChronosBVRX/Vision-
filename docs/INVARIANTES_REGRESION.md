# Invariantes contra regresiones — Vision+ 2.6.81

> Fuente única de verdad para el reproductor y Deportes. Si un cambio toca
> `X4KPlayerAty`, `PlayerScreen`, `GeeZapSignals` o `GeeSportsTabs`, leer esto
> antes y correr los tests indicados. Un cambio que rompa un invariante no se
> publica.

## 1. Estado de publicación

- **Estable y beta:** 2.6.81 (vc577) — mismo APK/firma; la beta se promovió.
- **Estable anterior:** 2.6.67 (vc563).
- **Commit de la beta:** `f91d199`.
- **Notas:** `ota_notes_2.6.81_estable.md` (estable) y `ota_notes_2.6.81.md` (beta), sin nombrar proveedores.
- **Release estable:** https://github.com/ChronosBVRX/Vision-Releases/releases/tag/v2.6.81
- **Release beta:** https://github.com/ChronosBVRX/Vision-Releases/releases/tag/beta
- **Proceso de publicación:** `tools/publish_ota.ps1` (bump + R8 + motor inyectado + firma
  histórica + `gh release`). Tras la suite completa se publica con `-SkipTests`.

## 2. VOD y 4K (no romper)

1. **El arranque VOD es siempre 1080p nativo.** El 4K nunca se auto-selecciona;
   solo entra si el usuario lo elige en el selector de calidad.
2. **El 4K se resuelve después del primer cuadro** (≈6 s), no al abrir la ficha
   ni durante la carga. El estado de carga no compite con la red del 4K.
3. **El aviso 4K es temporal:** «Esta película está disponible en 4K en su idioma
   original» (o «…disponible en 4K» si el audio no es original). Aparece ≈4 s
   después del primer cuadro, dura ≈5 s y desaparece. No es un chip fijo.
4. **El 4K manual vive en el selector de calidad.** La píldora de calidad sigue
   mostrando 1080p. `External4KRepository.fresh4k` prefiere el CDN (R2) sobre
   hosts de archivos; si un IMDb guardado no da fuentes, reintenta por
   título/TMDB y limpia el IMDb viejo.
5. **Antes de abrir el 4K se bloquea esa ficha 5 minutos** (`FourKGuard.block`
   con `commit()`): si el decodificador tumba el proceso, la próxima reproducción
   entra directo en 1080p. Un 4K exitoso (`STATE_READY`) borra el bloqueo.
6. **Si el 4K falla con el reproductor abierto:**
   - se muestra el aviso grande «El 4K falló, pero puedes verlo en alta definición
     (1080p)»;
   - se libera ExoPlayer, se quita el 4K de la lista y se vuelve al 1080p nativo
     ≈1.5 s después (deja soltar el decodificador);
   - la app sigue abierta; no se cierra ni se reinicia.
7. **No hay auto-relanzamiento global.** No reintroducir
   `MainActivity.recoverFourKIfNeeded`, `FourKGuard.savePending`,
   `takePendingIfStale`, `requestHardReset` ni `finish()` de recuperación: esa
   ruta podía abrir una película encima del vivo. La marca previa del punto 5 es
   la única recuperación.
8. **El 4K de una ficha se oculta 5 minutos tras un fallo** (selector y badge de
   la ficha). Después vuelve a estar disponible. No se elimina la opción 4K.
9. **Ventana de primer cuadro del 4K:** 45 s (60 s en equipos lentos). No bajarla
   a 15 s: el archivo 4K directo tarda más que el 1080p nativo.
10. **Limitación conocida de la TCL/BeyondTV:** su decodificador Android rechaza
    HEVC Main10 4K (`NoSupport`/`BAD CODEC`). La app cae sola a 1080p; no es una
    regresión de red ni de la app. En un decodificador compatible la misma ruta
    queda lista.
11. **Descargas y reproducción compiten por `:x4k`:** mantener
    `EngineDownloadGate`. No abrir dos programas en el mismo proceso.

## 3. Subtítulos

- Si el audio del 4K es original (no español), se activan los subtítulos en
  español del catálogo (`SubsStore`), no las pistas del contenedor.
- Si el audio es español, no se fuerzan subtítulos.
- El cableado vive en `autoSelectSpanishAudioAndSubs` y `maybeAutoExternalSubs`;
  el log esperado es `subs activo 'es' cues=… overlay=true`.

## 4. Vivo y agenda deportiva (no romper)

1. **El modo vivo está aislado de la ruta 4K:** `startExternal` y
   `fallbackFromExternal` retornan si `liveMode`; `fourKPoller` y el resolvedor
   no corren en vivo.
2. **La agenda deportiva usa solo las señales del evento.** El selector se arma
   con `geePickRows(title, links, checks, playingUrl)`. No se usa
   `xtvPickRows`, ni `xtv = event.xtv`, ni canales XTV de la casa.
3. **`GeeZapSignals` no tiene `house`, `fallbackRequest`, `requestFallback` ni
   `consumeFallback`.** No reintroducirlos: la casa no debe aparecer en la
   agenda, el zapping ni el reproductor.
4. **`PlayerScreen` no tiene `PLAYER_FAILOVER_XTV`.** Si se agotan las señales
   del evento, se muestra «No se pudo reproducir este canal.»; no se abre un
   canal de la casa.
5. **El zapping solo recorre las señales http(s) del evento.** Las referencias
   `xuperch:`/`magis:` no entran a `PlayerCore`.
6. **No cambiar la firma de `PlayerCore` con parámetros nuevos:** ART rechaza las
   composables con demasiados parámetros (`VerifyError` visto en vivo).

## 5. Tests y baseline

- Suite: `:app:testNormalDebugUnitTest` (JUnit4 + Robolectric/Compose).
- Baseline actual: **1,373 tests, 2 fallas ambientales conocidas**:
  - `EncryptedVaultDataSourcePortableTest > streamsAndSeeksPortableContainer`
    (runtime nativo de Robolectric).
  - `TvProfilesRenameTest > theTvProfilesScreenIsDarkGlassWithoutFakeActionBars`
    (colores viejos del diálogo).
- **Debe pasar:** `LiveSportsPlayerUiTest` (incluye
  `thePlayerDoesNotShowHouseChannelsWhenEventSignalsFail`),
  `External4KWiringTest`, `PlaybackSpeedWiringTest`, `BackToTopTest`.
- `TvFocusTest`, `TvHero*BoundsTest` y `TvHomeRowsFitTest` pueden fallar por
  flakiness cuando la suite corre cargada. Correrlos aislados antes de tocar
  código; no “arreglar” el código por un fallo intermitente.
- Regla de oro: cada bug corregido deja un test que lo reproduce.

## 6. Checklist antes de publicar

- [ ] El VOD arranca en 1080p; el 4K solo se elige a mano.
- [ ] El resolvedor y el aviso 4K esperan al primer cuadro.
- [ ] Un fallo 4K muestra el aviso y vuelve al 1080p sin cerrar la app.
- [ ] La marca previa del 4K persiste si el proceso muere.
- [ ] El vivo normal no entra a la ruta 4K.
- [ ] La agenda deportiva no muestra ni abre canales de la casa.
- [ ] Los subtítulos del catálogo entran con audio original.
- [ ] Suite completa (solo las 2 fallas ambientales).
- [ ] Notas OTA sin nombres de proveedores; canal `beta`.
- [ ] `git push origin main` y commit del bump.

## 7. No reintroducir (lista negra)

- 4K automático o 4K como primera pista del catálogo.
- Resolvedor 4K en la ficha o antes del primer cuadro.
- Badge 4K fijo durante la carga.
- `MainActivity` auto-relanzando una película al volver.
- `FourKGuard` con intención pendiente / latido / hard reset.
- Canales de la casa (`xtvPickRows`, `event.xtv`, `house`, `fallbackRequest`,
  `PLAYER_FAILOVER_XTV`) en Deportes.
- Quitar `focusGroup()` de `TvTopBar`/`TvCategoryScreen` (el foco TV se pierde).
- Proveedores en notas OTA o en la UI.
