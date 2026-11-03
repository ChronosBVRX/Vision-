# Vision+ Fiesta 4.0 · Cierre de implementación

Fecha: 2026-11-02

## Estado

Las fases 1 a 5 del plan 4.0 están implementadas y verificadas en local. No se
ha desplegado el backend ni se ha publicado OTA. Falta la prueba física con
televisor y teléfonos reales.

| Fase | Estado |
|---|---|
| 1 · Bucles de juego del servidor | Completa |
| 2 · Economía, apuestas, burlas y audiencia | Completa |
| 3 · Cliente Android asimétrico y transiciones | Completa |
| 4 · Expansión de contenido | Completa |
| 5 · Pruebas y release | Completa en local |

## Verificación ejecutada

```bash
.venv/bin/python -m pytest server/tests -q
# 667 passed

./gradlew :app:testNormalDebugUnitTest --offline --max-workers=1 \
  --tests '*Fiesta*' --tests '*GameWiring*'
# BUILD SUCCESSFUL

python3 tools/fiesta_quality_gate.py
# 213/213 assets pasan el filtro.

python3 tools/fiesta_quality_gate.py --premium
# 19/19 assets pasan el filtro.

./gradlew :app:assembleNormalRelease --offline
# BUILD SUCCESSFUL
```

## Blindaje pre-producción

- `texto_roto`: 3 subturnos de **18 s** (54 s de chat) para absorber el polling
  HTTP de ~700 ms.
- `ultima_palabra`: cada duelo dura **20 s**.
- Acciones duplicadas o que llegan justo después del cambio de fase responden
  `200 OK` con `ignored: true`; el cliente no muestra error.
- `GameAmbient.setTensionMode()` solo cambia `playbackParams` mientras el
  reproductor está sonando, fija `pitch = 1.0f` y vuelve a `1.0x` al hacer
  ducking o al salir.
- El teléfono envuelve el cambio de rol en un `Crossfade` de 220 ms.

## Contenido final por juego

| Juego | Contenido base | Conteo |
|---|---|---|
| La Última Palabra | Prompts de escritura | 87 |
| Vox Populi | Encuestas con 6 respuestas | 48 |
| Fuera de Época | Eventos históricos y pop | 66 |
| Texto Roto | Escenas colaborativas | 45 |
| Franela Fest | Prompts de camiseta | 40 |
| El Tasador | Objetos malditos/absurdos | 45 |
| Texto Roto | Directivas secretas únicas | 21 |
| Franela Fest | Eslóganes de reserva | 20 |
| Los seis | Burlas válidas por juego | 9 cada uno |

La Fase 4 agregó 20 encuestas, 20 eventos, 15 escenas, 15 prompts de Franela,
15 objetos de El Tasador, 6 directivas secretas y 16 eslóganes de reserva.

## Contrato asimétrico

El servidor sigue siendo la única autoridad. El teléfono elige su control con:

```kotlin
val activePhoneScreen = fiesta.you?.screen?.takeIf { it.isNotBlank() } ?: fiesta.screen
```

La TV usa siempre `fiesta.screen`. Esto permite que en Texto Roto convivan
escritores (`text_input`) y reactores (`react`), y que en El Tasador convivan
vendedores (`text_input`) y compradores (`invest`) sin compartir estado privado.

Campos nuevos consumidos por Android:

- Raíz: `duelState`, `voxState`, `sortItems`, `chatMessages`, `lots`,
  `reactionBanner`, `audienceVotes`.
- Privados: `screen`, `quickChips`, `chatRole`, `secretGoal`, `remixPool`,
  `remixChoice`, `incomingTaunt`, `finalBoost`, `lotRole`, `lotId`, `lotHint`,
  `waitBonus`.

Todos tienen valores por defecto en Kotlin, así que un snapshot 3.0 sigue
deserializando sin romper.

## Cambios de experiencia

### Teléfonos

- La Última Palabra: duelo activo, aviso de autoría y burla.
- Vox Populi: 8 chips de sondeo y botones HILO.
- Fuera de Época: ordenamiento con ▲/▼ y envío de línea temporal.
- Texto Roto: feed en vivo, directiva secreta y botonera de reacciones.
- Franela Fest: dos eslóganes al crear y taller de remix con dibujos/eslóganes
  ajenos.
- El Tasador: pitch de vendedor con pista secreta; puja de comprador con Lote
  A/B y montos 0/100/250/400.
- Transversales: splat de burla con 3 toques y minijuego de moneda de espera.

### Televisión

- `FiestaPhaseTransition` con cortinas, scanline CRT o franjas diagonales.
- Retrato del presentador animado mientras habla.
- Música a 1.12x en los últimos 10 segundos y regreso a 1.0x.
- Duelos, HILO, vórtice temporal, chat en vivo, créditos de remix y martillo de
  subasta con autenticidad/valor/recaudación.
- Cinta de `reactionBanner` e insignia de favorito de la audiencia.

## Anti-fugas

- `chatMessages.authorName` se enmascara como `ANÓNIMO` para terceros antes del
  reveal; el autor sí ve su propio nombre.
- Los duelos no llevan autores antes del reveal.
- El ranking del misterio Vox no viaja antes del reveal.
- Los años de ordenamiento no viajan antes del reveal.
- Autenticidad y valor real de los lotes no viajan antes del reveal.
- Las burlas entrantes solo llegan a su objetivo.

## Archivos principales modificados

- `server/api/fiesta.py`
- `server/api/fiesta_content.py`
- `server/tests/test_fiesta.py`
- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameModels.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameClient.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/data/games/NarratorAudio.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaDesign.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaPhoneWorlds.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaTvWorlds.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaVoice.kt`
- `app/src/test/java/com/chronosbvrx/visionplus/data/games/FiestaModelsTest.kt`
- `docs/FIESTA_REGRESSION_GUARD.md`
- `tools/export_fiesta_dossier.py`
- `docs/fiesta_dossier_data.json`

## Buscador de TV en vivo

- El foco inicial del modal usa `rememberDialogFocus()` y reintenta hasta que la
  ventana del diálogo realmente lo tiene.
- El teclado y los resultados quedan alcanzables con el D-pad.
- El dictado intenta la actividad estándar de reconocimiento y cae al
  `SpeechRecognizer` nativo cuando la TV no la trae.
- `RECORD_AUDIO` se pide solo la primera vez que se usa el micrófono.

## Estado de producción

- [x] Backend desplegado en el VPS con respaldo previo.
- [x] `/healthz` responde `ok` y el catálogo sirve los 11 juegos.
- [x] `DURATIONS` remoto: duelo 20 s, chat 18 s por subturno.
- [x] OTA v2.5.237 publicada como `/releases/latest`.
- [x] APK descargado del release y SHA-256 verificado.
- [ ] Prueba física con 2–3 teléfonos y un televisor real.


## Artefacto de release

Release publicado: **v2.5.237 / versionCode 477**.

```text
APK OTA firmado: VisionPlus.apk
Tamaño: 117,080,760 bytes
SHA-256: d3e83777644541b9de1ffc4864182afdf22fb24dd87aa2c838986384e38517fe
Firma: 9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3
DEX: classes.dex, classes2.dex, classes3.dex, classes4.dex, classes5.dex
R8/namespace: 0 colisiones

APK base sin motor inyectado:
app/build/outputs/apk/normal/release/app-normal-release.apk
Tamaño: 99,757,515 bytes
SHA-256: 0e13689fde3846228474533a4b8356b1130e8b4b70428bb16be07671ae6707e4

Launcher: VisionPlusHome.apk
Tamaño: 2,824,059 bytes
SHA-256: 06afb1b7919e60070bd04d1b405cadbb3e69f947b73aa67bc00e26ea8aa3cfe9

version.json: 2.5.237 / 477 / VisionPlus.apk
```

## Despliegue del backend (VPS OCI)

Archivos mínimos a sincronizar:

```text
server/api/app.py
server/api/fiesta.py
server/api/fiesta_content.py
server/api/games.py          (si cambió el contrato de acciones)
server/api/Dockerfile        (si cambió el COPY)
```

Comando recomendado (desde la raíz del repo):

```bash
rsync -avz -e "ssh -i ~/.ssh/oci_key_3"   server/api/app.py   server/api/fiesta.py   server/api/fiesta_content.py   server/api/games.py   server/api/Dockerfile   opc@159.54.146.146:/opt/streaming-backend/api/

ssh -i ~/.ssh/oci_key_3 opc@159.54.146.146 '
  cd /opt/streaming-backend &&
  docker compose up -d --build api &&
  docker compose ps api &&
  curl -fsS http://127.0.0.1:8100/healthz
'
```

Alternativa si el VPS usa git:

```bash
ssh -i ~/.ssh/oci_key_3 opc@159.54.146.146 '
  cd /opt/streaming-backend &&
  git pull --ff-only &&
  docker compose up -d --build api &&
  docker compose ps api
'
```

No hay unidad `systemd` para la API en este repo: el servicio se levanta con
`docker compose` y Caddy del host hace el reverse proxy a `127.0.0.1:8100`.

Orden de publicación:

1. Sincronizar y reconstruir `api` en el VPS.
2. Confirmar `/healthz` y una partida de humo.
3. Publicar el APK release/OTA en la TV y teléfonos.

### Activar la OTA

El APK de `assembleNormalRelease` es el artefacto verificable de esta corrida.
Para publicar la OTA real (inyecta el motor X4K, re-firma con la llave histórica
y crea el release de GitHub), ejecutar desde Windows en la raíz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\publish_ota.ps1 `
  -Version 2.5.237 `
  -NotesFile ota_notes_2.5.237.md `
  -Channel stable
```

Ese script hace bump de `versionCode`, corre tests, compila app + launcher,
verifica la firma histórica, genera `VisionPlus.apk`, `VisionPlusHome.apk` y
`version.json`, y publica `v2.5.237` como `/releases/latest`.
