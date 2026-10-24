package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El launcher es el mejor lugar para calentar el motor, pero solo en teles con RAM
 * suficiente. En 1.5 GB se queda el camino diferido de Vision+ (no se arranca `:x4k`
 * durante el arranque del sistema).
 */
class EnginePrewarmPolicyTest {

    @Test
    fun lowEndTvsKeepTheDeferredPath() {
        assertFalse(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 1_536L, isTv = true))
        assertFalse(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 0L, isTv = true))
    }

    @Test
    fun twoGigabyteTvsUseTheFullService() {
        assertTrue(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 1_900L, isTv = true))
        assertTrue(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 2_048L, isTv = true))
        assertTrue(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 8_192L, isTv = true))
    }

    @Test
    fun phonesAndKillSwitchDoNotBind() {
        assertFalse(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 8_192L, isTv = false))
        assertFalse(EnginePrewarmPolicy.shouldPrewarm(totalRamMb = 8_192L, isTv = true, enabled = false))
    }

    @Test
    fun theLauncherBindsWithTheSignaturePermission() {
        val launcherManifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "el launcher no pide el permiso de prewarm",
            launcherManifest.contains("com.chronosbvrx.visionplus.permission.ENGINE_PREWARM"),
        )
        val appManifest = File("../app/src/main/AndroidManifest.xml").readText()
        assertTrue(
            "Vision+ no declara el permiso signature",
            appManifest.contains("com.chronosbvrx.visionplus.permission.ENGINE_PREWARM") &&
                appManifest.contains("android:protectionLevel=\"signature\""),
        )
        assertTrue(
            "Vision+ no expone el servicio en :x4k",
            appManifest.contains(".data.xuper.EngineWarmService") &&
                appManifest.contains("android:process=\":x4k\""),
        )
        val activity = File("src/main/java/com/chronosbvrx/visionplus/home/LauncherActivity.kt").readText()
        assertTrue("el launcher no se vincula", activity.contains("bindService"))
        assertTrue("no usa el componente del contrato", activity.contains("EnginePrewarmContract.SERVICE_CLASS"))
        assertTrue("no respeta la política", activity.contains("EnginePrewarmPolicy.shouldPrewarm"))
        assertTrue("no espera al primer frame", activity.contains("EnginePrewarmContract.BIND_DELAY_MS"))
        assertTrue("no libera la ventana", activity.contains("EnginePrewarmContract.BIND_WINDOW_MS"))
    }
}
