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
}
