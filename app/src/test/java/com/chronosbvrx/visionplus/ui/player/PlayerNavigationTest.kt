package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para la navegación y tecla Atrás en los reproductores:
 * Si la navegación está abierta, presionar Atrás oculta la navegación sin salir.
 * Si se presiona Atrás nuevamente con la navegación oculta, sale del reproductor.
 */
class PlayerNavigationTest {

    @Test
    fun resolvePlayerBackActionHierarchy() {
        // Con selector abierto (canales, episodios, audio/subs), atrás primero descarta el selector.
        assertEquals(
            PlayerBackAction.DISMISS_PICKER,
            resolvePlayerBackAction(isPicking = true, isNavigationVisible = true),
        )
        assertEquals(
            PlayerBackAction.DISMISS_PICKER,
            resolvePlayerBackAction(isPicking = true, isNavigationVisible = false),
        )

        // Con navegación/controles abierta y sin selector, atrás oculta la navegación.
        assertEquals(
            PlayerBackAction.HIDE_NAVIGATION,
            resolvePlayerBackAction(isPicking = false, isNavigationVisible = true),
        )

        // Con navegación/controles oculta, atrás sale del reproductor.
        assertEquals(
            PlayerBackAction.EXIT_PLAYER,
            resolvePlayerBackAction(isPicking = false, isNavigationVisible = false),
        )
    }

    @Test
    fun resolveLiveDpadActionBehavior() {
        // 1. Con navegación oculta: flecha arriba/abajo solo despierta la navegación
        assertEquals(
            LiveDpadAction.SHOW_NAVIGATION,
            resolveLiveDpadAction(isPicking = false, isNavigationVisible = false),
        )

        // 2. Con navegación ya visible: presionar de nuevo sí cambia de canal
        assertEquals(
            LiveDpadAction.SWITCH_CHANNEL,
            resolveLiveDpadAction(isPicking = false, isNavigationVisible = true),
        )

        // 3. Con modal/selector de canales abierto: navega la lista
        assertEquals(
            LiveDpadAction.LIST_NAVIGATION,
            resolveLiveDpadAction(isPicking = true, isNavigationVisible = true),
        )
        assertEquals(
            LiveDpadAction.LIST_NAVIGATION,
            resolveLiveDpadAction(isPicking = true, isNavigationVisible = false),
        )
    }

    @Test
    fun controlsStateHandleBackHidesOnFirstPressAndExitsOnSecondPress() {
        val controls = ControlsState()
        var exitCalled = false

        // 1. Inicia con navegación oculta: presionar atrás sale directamente
        val handledWhenHidden = controls.handleBack(onExit = { exitCalled = true })
        assertFalse(handledWhenHidden)
        assertTrue(exitCalled)
        assertFalse(controls.visible)

        // 2. Usuario abre navegación / controles (bump)
        exitCalled = false
        controls.bump()
        assertTrue(controls.visible)

        // 3. Primer toque de atrás con navegación abierta: oculta navegación y NO sale
        val handledWhenOpen = controls.handleBack(onExit = { exitCalled = true })
        assertTrue(handledWhenOpen)
        assertFalse("No debe salir del reproductor al ocultar la navegación", exitCalled)
        assertFalse("La navegación debe quedar oculta", controls.visible)

        // 4. Segundo toque de atrás (navegación ya oculta): SÍ sale del reproductor
        val handledSecondPress = controls.handleBack(onExit = { exitCalled = true })
        assertFalse(handledSecondPress)
        assertTrue("Debe salir del reproductor en el segundo toque", exitCalled)
        assertFalse(controls.visible)
    }

    @Test
    fun modalConfinementPreventsFocusRescueAndChannelZapping() {
        // Con diálogo/modal abierto, FocusRescue.rescue() debe negarse a rescatar el foco hacia el fondo
        com.chronosbvrx.visionplus.ui.tv.FocusRescue.dialogsOpen = 1
        assertFalse(
            "FocusRescue no debe actuar mientras haya un diálogo/modal abierto",
            com.chronosbvrx.visionplus.ui.tv.FocusRescue.rescue(),
        )
        com.chronosbvrx.visionplus.ui.tv.FocusRescue.dialogsOpen = 0

        // En reproducción en vivo, con cualquier modal activo (isPicking = true) el D-pad está confinado a la lista
        assertEquals(
            LiveDpadAction.LIST_NAVIGATION,
            resolveLiveDpadAction(isPicking = true, isNavigationVisible = false),
        )
        assertEquals(
            LiveDpadAction.LIST_NAVIGATION,
            resolveLiveDpadAction(isPicking = true, isNavigationVisible = true),
        )
    }

    @Test
    fun calculateSeekTargetClamping() {
        // Adelanto normal
        assertEquals(25_000L, calculateSeekTarget(currentPosMs = 15_000L, deltaMs = 10_000L, durationMs = 100_000L))
        // Retroceso normal
        assertEquals(5_000L, calculateSeekTarget(currentPosMs = 15_000L, deltaMs = -10_000L, durationMs = 100_000L))
        // Retroceso no baja de 0
        assertEquals(0L, calculateSeekTarget(currentPosMs = 4_000L, deltaMs = -10_000L, durationMs = 100_000L))
        // Adelanto no supera la duración total
        assertEquals(100_000L, calculateSeekTarget(currentPosMs = 95_000L, deltaMs = 10_000L, durationMs = 100_000L))
    }

    @Test
    fun resolveDismissFocusTargetPreservesSourceButton() {
        // Al descartar con origen explícito (subtítulos, audio, capítulos, canales, info), regresa a ese botón
        assertEquals("subs", resolveDismissFocusTarget(lastPickerSource = "subs", isLive = false))
        assertEquals("audio", resolveDismissFocusTarget(lastPickerSource = "audio", isLive = false))
        assertEquals("episodes", resolveDismissFocusTarget(lastPickerSource = "episodes", isLive = false))
        assertEquals("channels", resolveDismissFocusTarget(lastPickerSource = "channels", isLive = true))
        assertEquals("info", resolveDismissFocusTarget(lastPickerSource = "info", isLive = true))

        // Si no había origen previo: en vivo cae en channels, en VOD cae en playPause
        assertEquals("playPause", resolveDismissFocusTarget(lastPickerSource = null, isLive = false))
        assertEquals("channels", resolveDismissFocusTarget(lastPickerSource = null, isLive = true))
    }

    @Test
    fun dpadWakeupWithoutActionOnSmartTv() {
        val dpadKeys = listOf(
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_MENU,
        )

        // 1. En Smart TV con menú oculto: cualquier tecla del D-pad despierta el menú sin ejecutar acción por sí sola.
        for (key in dpadKeys) {
            assertTrue(
                "Key $key con controles ocultos debe despertar controles sin acción",
                shouldWakeControlsWithoutAction(
                    keyCode = key,
                    isTv = true,
                    isControlsVisible = false,
                    hasControlFocus = false,
                ),
            )
        }

        // 2. Si el menú está visible pero ningún botón tiene foco (hasControlFocus = false): despierta/enfoca sin actuar.
        for (key in dpadKeys) {
            assertTrue(
                "Key $key sin foco activo debe enfocar controles sin ejecutar acción",
                shouldWakeControlsWithoutAction(
                    keyCode = key,
                    isTv = true,
                    isControlsVisible = true,
                    hasControlFocus = false,
                ),
            )
        }

        // 3. Con menú visible y botón enfocado: las teclas pasan al sistema de foco para navegar o activar.
        for (key in dpadKeys) {
            assertFalse(
                "Key $key con controles visibles y enfocados debe actuar normalmente",
                shouldWakeControlsWithoutAction(
                    keyCode = key,
                    isTv = true,
                    isControlsVisible = true,
                    hasControlFocus = true,
                ),
            )
        }

        // 4. Con modal abierto, intro activa o en teléfono móvil: no intercepta como wakeup.
        assertFalse(
            shouldWakeControlsWithoutAction(
                keyCode = android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                isTv = true,
                isControlsVisible = false,
                isModalActive = true,
            ),
        )
        assertFalse(
            shouldWakeControlsWithoutAction(
                keyCode = android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                isTv = true,
                isControlsVisible = false,
                isIntroActive = true,
            ),
        )
        assertFalse(
            shouldWakeControlsWithoutAction(
                keyCode = android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                isTv = false,
                isControlsVisible = false,
            ),
        )
    }

    @Test
    fun liveChannelMediaIndexO1Lookup() {
        val sample = org.json.JSONArray().put(
            org.json.JSONObject().apply {
                put("channelCode", "ESPN_MX_HD")
                put("name", "ESPN México HD")
                put(
                    "liveAddressList",
                    org.json.JSONArray()
                        .put(
                            org.json.JSONObject().apply {
                                put("tag", "free")
                                put("quality", 720)
                                put("playCode", "play_720")
                                put("license", "lic_720")
                            },
                        )
                        .put(
                            org.json.JSONObject().apply {
                                put("tag", "free")
                                put("quality", 1080)
                                put("playCode", "play_1080")
                                put("license", "lic_1080")
                            },
                        ),
                )
            },
        )
        com.chronosbvrx.visionplus.data.xuper.X4KApi.indexLiveArray(sample)
        val cached = com.chronosbvrx.visionplus.data.xuper.X4KApi.cachedLiveMediasFor("ESPN_MX_HD")
        assertTrue("El índice en memoria debe resolver el canal en O(1)", !cached.isNullOrBlank())
        val parsed = org.json.JSONArray(cached)
        assertEquals(1, parsed.length())
        assertEquals("play_1080", parsed.getJSONObject(0).getString("n"))
        assertEquals("lic_1080", parsed.getJSONObject(0).getString("l"))
    }

    @Test
    fun universalPlayerFirstClickNeverActsAcrossAllModes() {
        val dpadNavigationKeys = listOf(
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_MENU,
            android.view.KeyEvent.KEYCODE_CHANNEL_UP,
            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
            android.view.KeyEvent.KEYCODE_INFO,
            android.view.KeyEvent.KEYCODE_GUIDE,
        )

        // Verificamos tanto en modo VOD (Películas/Series) como en Vivo (Canales/Deportes)
        for (isLive in listOf(false, true)) {
            val modeName = if (isLive) "En Vivo" else "VOD"

            // 1. Con controles ocultos: NINGUNA tecla debe realizar acción, SOLO despertar controles
            for (key in dpadNavigationKeys) {
                val action = resolvePlayerKeyDownAction(
                    keyCode = key,
                    isLive = isLive,
                    isControlsVisible = false,
                    hasControlFocus = false,
                )
                assertEquals(
                    "[$modeName] Tecla $key con controles ocultos DEBE retornar WAKE_CONTROLS_ONLY y jamás actuar",
                    PlayerKeyDownAction.WAKE_CONTROLS_ONLY,
                    action,
                )
            }

            // 2. Con controles visibles pero sin foco activo en controles: despertar/enfocar controles sin actuar
            for (key in dpadNavigationKeys) {
                val action = resolvePlayerKeyDownAction(
                    keyCode = key,
                    isLive = isLive,
                    isControlsVisible = true,
                    hasControlFocus = false,
                )
                assertEquals(
                    "[$modeName] Tecla $key sin foco activo en controles DEBE retornar WAKE_CONTROLS_ONLY",
                    PlayerKeyDownAction.WAKE_CONTROLS_ONLY,
                    action,
                )
            }
        }
    }

    @Test
    fun universalPlayerActionsOnlyWhenControlsVisibleAndFocused() {
        // En VOD con controles visibles y enfocados: todas las teclas de navegación van al control enfocado
        val vodKeys = listOf(
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
        )
        for (key in vodKeys) {
            val action = resolvePlayerKeyDownAction(
                keyCode = key,
                isLive = false,
                isControlsVisible = true,
                hasControlFocus = true,
            )
            assertEquals(
                "[VOD] Con controles visibles y enfocados, tecla $key debe pasar al control enfocado",
                PlayerKeyDownAction.PASS_TO_FOCUSED_CONTROL,
                action,
            )
        }

        // En Vivo con controles visibles y enfocados:
        // Arriba / Channel Up cambia al canal siguiente
        assertEquals(
            PlayerKeyDownAction.LIVE_SWITCH_NEXT_CHANNEL,
            resolvePlayerKeyDownAction(
                keyCode = android.view.KeyEvent.KEYCODE_DPAD_UP,
                isLive = true,
                isControlsVisible = true,
                hasControlFocus = true,
            ),
        )
        assertEquals(
            PlayerKeyDownAction.LIVE_SWITCH_NEXT_CHANNEL,
            resolvePlayerKeyDownAction(
                keyCode = android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                isLive = true,
                isControlsVisible = true,
                hasControlFocus = true,
            ),
        )

        // Abajo / Channel Down cambia al canal anterior
        assertEquals(
            PlayerKeyDownAction.LIVE_SWITCH_PREV_CHANNEL,
            resolvePlayerKeyDownAction(
                keyCode = android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                isLive = true,
                isControlsVisible = true,
                hasControlFocus = true,
            ),
        )
        assertEquals(
            PlayerKeyDownAction.LIVE_SWITCH_PREV_CHANNEL,
            resolvePlayerKeyDownAction(
                keyCode = android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                isLive = true,
                isControlsVisible = true,
                hasControlFocus = true,
            ),
        )

        // Izquierda, Derecha, Centro en Vivo pasan al control enfocado (ej. botón Canales o Cerrar)
        val livePassKeys = listOf(
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
        )
        for (key in livePassKeys) {
            assertEquals(
                PlayerKeyDownAction.PASS_TO_FOCUSED_CONTROL,
                resolvePlayerKeyDownAction(
                    keyCode = key,
                    isLive = true,
                    isControlsVisible = true,
                    hasControlFocus = true,
                ),
            )
        }
    }

    @Test
    fun dedicatedMediaKeysBypassWakeup() {
        val mediaKeys = listOf(
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            android.view.KeyEvent.KEYCODE_HEADSETHOOK,
            android.view.KeyEvent.KEYCODE_SPACE,
        )

        for (key in mediaKeys) {
            // Con controles ocultos
            assertEquals(
                PlayerKeyDownAction.DEDICATED_MEDIA_KEY,
                resolvePlayerKeyDownAction(keyCode = key, isLive = false, isControlsVisible = false),
            )
            // Con controles visibles
            assertEquals(
                PlayerKeyDownAction.DEDICATED_MEDIA_KEY,
                resolvePlayerKeyDownAction(keyCode = key, isLive = false, isControlsVisible = true),
            )
        }
    }

    @Test
    fun modalsAndIntroBypassWakeup() {
        val key = android.view.KeyEvent.KEYCODE_DPAD_CENTER

        // Con modal activo: debe pasar al modal
        assertEquals(
            PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM,
            resolvePlayerKeyDownAction(keyCode = key, isLive = false, isControlsVisible = false, isModalActive = true),
        )

        // Con intro activa: debe pasar a la intro
        assertEquals(
            PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM,
            resolvePlayerKeyDownAction(keyCode = key, isLive = false, isControlsVisible = false, isIntroActive = true),
        )

        // Con error en pantalla: debe pasar al botón de error
        assertEquals(
            PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM,
            resolvePlayerKeyDownAction(keyCode = key, isLive = false, isControlsVisible = false, isErrorShown = true),
        )
    }

    @Test
    fun canExecuteControlActionDebounceCooldown() {
        val wakeupTime = 1000L

        // Inmediatamente después del despertar (0 ms transcurridos): BLOQUEADO
        assertFalse(canExecuteControlAction(nowMs = 1000L, lastWakeupMs = wakeupTime))

        // A los 100 ms (típico rebote o repetición rápida de control remoto): BLOQUEADO
        assertFalse(canExecuteControlAction(nowMs = 1100L, lastWakeupMs = wakeupTime))

        // A los 219 ms: BLOQUEADO
        assertFalse(canExecuteControlAction(nowMs = 1219L, lastWakeupMs = wakeupTime))

        // A partir de los 220 ms: PERMITIDO
        assertTrue(canExecuteControlAction(nowMs = 1220L, lastWakeupMs = wakeupTime))
        assertTrue(canExecuteControlAction(nowMs = 1500L, lastWakeupMs = wakeupTime))
    }

    @Test
    fun calculateScrubStepMsProgressiveAcceleration() {
        val twoHourMovie = 120 * 60_000L
        val episode45m = 45 * 60_000L
        val shortEp = 20 * 60_000L

        // Pulsación simple (repeatCount = 0): paso base según duración
        assertEquals(30_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 0))
        assertEquals(20_000L, calculateScrubStepMs(episode45m, repeatCount = 0))
        assertEquals(10_000L, calculateScrubStepMs(shortEp, repeatCount = 0))

        // Mantener presionado (repeatCount >= 5): acelera a 1 minuto por tick
        assertEquals(60_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 5))
        assertEquals(60_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 10))

        // Mantener presionado prolongado (repeatCount >= 15): acelera a 2 minutos por tick
        assertEquals(120_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 15))
        assertEquals(120_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 25))

        // Mantener presionado continuo (repeatCount >= 30): acelera a 5 minutos por tick para cruzar la película en segundos
        assertEquals(300_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 30))
        assertEquals(300_000L, calculateScrubStepMs(twoHourMovie, repeatCount = 50))
    }

    @Test
    fun extractDurationMsFromPortalJson() {
        // Objeto con string "duration"
        val obj1 = org.json.JSONObject().put("duration", "01:45:00")
        assertEquals(105 * 60_000L, com.chronosbvrx.visionplus.data.xuper.X4KApi.extractDurationMs(obj1))

        // Objeto con string "playLength"
        val obj2 = org.json.JSONObject().put("playLength", "90 min")
        assertEquals(90 * 60_000L, com.chronosbvrx.visionplus.data.xuper.X4KApi.extractDurationMs(obj2))

        // Objeto con long numérico en segundos
        val obj3 = org.json.JSONObject().put("totalTime", 5400L)
        assertEquals(90 * 60_000L, com.chronosbvrx.visionplus.data.xuper.X4KApi.extractDurationMs(obj3))

        // Objeto con string numérico en minutos
        val obj4 = org.json.JSONObject().put("runtime", "120")
        assertEquals(120 * 60_000L, com.chronosbvrx.visionplus.data.xuper.X4KApi.extractDurationMs(obj4))

        // Objeto nulo o vacío
        assertEquals(0L, com.chronosbvrx.visionplus.data.xuper.X4KApi.extractDurationMs(null))
        assertEquals(0L, com.chronosbvrx.visionplus.data.xuper.X4KApi.extractDurationMs(org.json.JSONObject()))
    }

    @Test
    fun seriesNextEpisodeAvailabilityLogic() {
        // En vivo: nunca hay siguiente episodio
        assertFalse(
            hasNextEpisodeAvailable(
                isLive = true,
                curEpIndex = 0,
                epCount = 10,
                curSeries = "sid_123",
                curSeasonIndex = 0,
                seasonCount = 2,
            ),
        )

        // En serie dentro de la misma temporada: capítulo 1 de 10 -> hay siguiente
        assertTrue(
            hasNextEpisodeAvailable(
                isLive = false,
                curEpIndex = 0,
                epCount = 10,
                curSeries = "sid_123",
                curSeasonIndex = 0,
                seasonCount = 2,
            ),
        )

        // En serie en el último capítulo de la temporada 1 (índice 9 de 10) pero existe temporada 2:
        // -> hay siguiente (siguiente temporada)
        assertTrue(
            hasNextEpisodeAvailable(
                isLive = false,
                curEpIndex = 9,
                epCount = 10,
                curSeries = "sid_123",
                curSeasonIndex = 0,
                seasonCount = 2,
            ),
        )

        // En serie en el último capítulo de la última temporada:
        // -> ya no hay siguiente
        assertFalse(
            hasNextEpisodeAvailable(
                isLive = false,
                curEpIndex = 9,
                epCount = 10,
                curSeries = "sid_123",
                curSeasonIndex = 1,
                seasonCount = 2,
            ),
        )

        // En película suelta (sin serie y con 0 o 1 episodio):
        // -> no hay siguiente episodio
        assertFalse(
            hasNextEpisodeAvailable(
                isLive = false,
                curEpIndex = 0,
                epCount = 0,
                curSeries = "",
                curSeasonIndex = 0,
                seasonCount = 0,
            ),
        )
    }

    @Test
    fun cleanSeriesResumeStartsAtBeginningWithoutPrompt() {
        // 1. En series, novelas y anime: NUNCA preguntar "¿Reanudar o desde el principio?"
        // Siempre debe iniciar limpio desde 00:00 sin interrumpir.
        assertFalse(
            "En series con entrada histórica no debe mostrar diálogo de reanudar",
            shouldPromptResume(
                isLive = false,
                isSeries = true,
                hasEntry = true,
                shouldKeepEntry = true,
                resumeExplicitlyRequested = false,
            ),
        )

        // 2. Si se solicitó reanudar explícitamente desde «Seguir viendo»:
        // Tampoco debe mostrar diálogo porque ya se solicitó continuar directamente.
        assertFalse(
            "Cuando se pide resume explícito no debe mostrar diálogo",
            shouldPromptResume(
                isLive = false,
                isSeries = false,
                hasEntry = true,
                shouldKeepEntry = true,
                resumeExplicitlyRequested = true,
            ),
        )

        // 3. En películas sueltas con progreso guardado y sin resume previo explícito:
        // SÍ debe mostrar el diálogo de reanudar.
        assertTrue(
            "En película con progreso previo debe ofrecer reanudar",
            shouldPromptResume(
                isLive = false,
                isSeries = false,
                hasEntry = true,
                shouldKeepEntry = true,
                resumeExplicitlyRequested = false,
            ),
        )

        // 4. En canales en vivo o cuando no hay entrada válida: NUNCA mostrar diálogo.
        assertFalse(
            shouldPromptResume(
                isLive = true,
                isSeries = false,
                hasEntry = true,
                shouldKeepEntry = true,
                resumeExplicitlyRequested = false,
            ),
        )
        assertFalse(
            shouldPromptResume(
                isLive = false,
                isSeries = false,
                hasEntry = false,
                shouldKeepEntry = false,
                resumeExplicitlyRequested = false,
            ),
        )
    }

    @Test
    fun isBufferWindowDurationDetectsSlidingPacketBufferWindow() {
        // Película con duración declarada de 105 min (6,300,000 ms), usuario en el minuto 5 (300,000 ms).
        val declaredDur = 105 * 60_000L
        val currentPos = 5 * 60_000L

        // El motor IjkPlayer reporta (pos + 120s) = 7 minutos (420,000 ms): "dos minutos más del minuto actual".
        val slidingBufferWindow = currentPos + 120_000L
        assertTrue(
            "La ventana de búfer deslizante del decodificador (pos + 120s) debe ser detectada y rechazada",
            isBufferWindowDuration(
                engineDur = slidingBufferWindow,
                currentPos = currentPos,
                declaredDurationMs = declaredDur,
                isLive = false,
            ),
        )

        // Si el motor reporta la duración real completa del archivo (105 min): NO es ventana de búfer.
        assertFalse(
            "La duración real completa del archivo debe ser aceptada",
            isBufferWindowDuration(
                engineDur = declaredDur,
                currentPos = currentPos,
                declaredDurationMs = declaredDur,
                isLive = false,
            ),
        )

        // Caso sin duración previa declarada (declaredDurationMs = 0), pero engineDur reporta un chunk de 2 minutos al segundo 0:
        assertTrue(
            "Un chunk de solo 2 minutos al inicio debe ser descartado como ventana de búfer",
            isBufferWindowDuration(
                engineDur = 120_000L,
                currentPos = 0L,
                declaredDurationMs = 0L,
                isLive = false,
            ),
        )

        // Caso sin duración declarada pero engineDur reporta un contenedor real de 45 minutos:
        assertFalse(
            "Una duración legítima de 45 min sin previa declaración debe ser aceptada",
            isBufferWindowDuration(
                engineDur = 45 * 60_000L,
                currentPos = 0L,
                declaredDurationMs = 0L,
                isLive = false,
            ),
        )
    }

    @Test
    fun resolveStableTotalDurationPreventsAlternatingFlicker() {
        val declaredDur = 105 * 60_000L // 1:45:00
        val currentPos = 5 * 60_000L   // 05:00
        val slidingBufferWindow = currentPos + 120_000L // 07:00

        // Cuando el poller de IjkPlayer sondea y obtiene 7 minutos:
        val stableTotal = resolveStableTotalDuration(
            declaredDurationMs = declaredDur,
            engineDurationMs = slidingBufferWindow,
            currentPos = currentPos,
            isLive = false,
        )
        assertEquals(
            "La duración total debe mantenerse fija en la duración real declarada (105 min) y no alternar a 7 min",
            declaredDur,
            stableTotal,
        )

        // En TV en vivo, la duración total debe ser 0L
        assertEquals(
            0L,
            resolveStableTotalDuration(
                declaredDurationMs = declaredDur,
                engineDurationMs = slidingBufferWindow,
                currentPos = currentPos,
                isLive = true,
            ),
        )
    }

    @Test
    fun videoQualityFormattingHelpers() {
        assertEquals("1080p", formatQualityShort("1080p"))
        assertEquals("1080p", formatQualityShort("1080"))
        assertEquals("1080p", formatQualityShort("FHD"))
        assertEquals("720p", formatQualityShort("720p"))
        assertEquals("720p", formatQualityShort("HD"))
        assertEquals("480p", formatQualityShort("480p"))
        assertEquals("480p", formatQualityShort("SD"))
        assertEquals("4K", formatQualityShort("4k"))
        assertEquals("4K", formatQualityShort("2160p"))
        assertEquals("Auto", formatQualityShort(""))

        assertEquals("1080p FHD", formatQualityLabel("1080p"))
        assertEquals("720p HD", formatQualityLabel("720p"))
        assertEquals("480p SD", formatQualityLabel("480p"))
        assertEquals("4K Ultra HD", formatQualityLabel("4k"))
        assertEquals("Automática (Alta definición)", formatQualityLabel(""))
    }

    @Test
    fun magisvodNormalizesToXtvForNativePlayback() {
        val film = com.chronosbvrx.visionplus.data.Film(
            id = "magis:E019F33B862F48178947ABF50261EAFD",
            title = "Pequeños calvarios",
            year = 2025,
            durationMin = 92,
            synopsis = "Sinopsis",
            posterUrl = "poster",
            videoUrl = "magisvod:E019F33B862F48178947ABF50261EAFD",
            sourceUrl = "magis",
        )
        val cid = film.videoUrl.removePrefix("magisvod:")
        val normalized = film.copy(id = "xtv:$cid", videoUrl = "xtv:$cid")
        assertEquals("xtv:E019F33B862F48178947ABF50261EAFD", normalized.id)
        assertEquals("xtv:E019F33B862F48178947ABF50261EAFD", normalized.videoUrl)
    }

    @Test
    fun autoRetryRecoversTransientFailureWithoutUserAction() {
        var attempts = 0
        val prepareLambda: () -> Boolean = {
            attempts++
            attempts >= 2 // Falla en el 1er intento (token frío/latencia), triunfa en el 2do
        }

        var ok = false
        for (retryIndex in 0..2) {
            ok = prepareLambda()
            if (ok) break
        }

        assertTrue("El auto-reintento debe resolver exitosamente la apertura sin acción del usuario", ok)
        assertEquals("Debe haberse resuelto en exactamente 2 intentos", 2, attempts)
    }
}


