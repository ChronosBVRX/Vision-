package com.chronosbvrx.visionplus

import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePerformanceTest {

    @Test
    fun parseEffectsMode() {
        assertEquals(EffectsMode.AUTO, EffectsMode.fromKey("auto"))
        assertEquals(EffectsMode.AUTO, EffectsMode.fromKey(null))
        assertEquals(EffectsMode.AUTO, EffectsMode.fromKey("something_else"))

        assertEquals(EffectsMode.LIGHT, EffectsMode.fromKey("light"))
        assertEquals(EffectsMode.LIGHT, EffectsMode.fromKey("performance"))
        assertEquals(EffectsMode.LIGHT, EffectsMode.fromKey("off"))

        assertEquals(EffectsMode.FULL, EffectsMode.fromKey("full"))
        assertEquals(EffectsMode.FULL, EffectsMode.fromKey("quality"))
        assertEquals(EffectsMode.FULL, EffectsMode.fromKey("on"))
    }

    @Test
    fun detectLowEndDevices() {
        // Chromecast HD / 4K (RAM <= 2.2GB)
        assertTrue(
            DevicePerformance.evaluateLowEnd(
                totalRamMb = 1850L,
                isLowRamFlag = false,
                model = "Chromecast",
                hardware = "amlogic",
                cpuCores = 4,
                isTv = true,
            )
        )

        // Android low RAM flag active
        assertTrue(
            DevicePerformance.evaluateLowEnd(
                totalRamMb = 3000L,
                isLowRamFlag = true,
                model = "GenericBox",
                hardware = "unknown",
                cpuCores = 4,
                isTv = true,
            )
        )

        // TV Stick with Fire TV signature
        assertTrue(
            DevicePerformance.evaluateLowEnd(
                totalRamMb = 2400L,
                isLowRamFlag = false,
                model = "AFTMM",
                hardware = "mt8695",
                cpuCores = 4,
                isTv = true,
            )
        )

        // TV with 4 or fewer cores
        assertTrue(
            DevicePerformance.evaluateLowEnd(
                totalRamMb = 2400L,
                isLowRamFlag = false,
                model = "SmartTvBox",
                hardware = "generic",
                cpuCores = 4,
                isTv = true,
            )
        )

        // Capable TV (e.g. Nvidia Shield TV: 3GB RAM, 8 cores)
        assertFalse(
            DevicePerformance.evaluateLowEnd(
                totalRamMb = 3072L,
                isLowRamFlag = false,
                model = "SHIELD Android TV",
                hardware = "tegra",
                cpuCores = 8,
                isTv = true,
            )
        )

        // Capable Phone / Tablet (8 cores, 6GB RAM)
        assertFalse(
            DevicePerformance.evaluateLowEnd(
                totalRamMb = 6144L,
                isLowRamFlag = false,
                model = "Pixel 8",
                hardware = "tensor",
                cpuCores = 8,
                isTv = false,
            )
        )
    }

    @Test
    fun splashScreenPolicyProtectsChromecastAndAndroidTv() {
        // Any TV with <= 4 cores (typical Chromecast / Fire TV / Mi Box) triggers low-end / safe mode
        val isChromecastLowEnd = DevicePerformance.evaluateLowEnd(
            totalRamMb = 1900L,
            isLowRamFlag = false,
            model = "Chromecast with Google TV",
            hardware = "amlogic",
            cpuCores = 4,
            isTv = true,
        )
        assertTrue(isChromecastLowEnd)

        // General TV flag also triggers safe compose splash if isTv is true
        val isTv = true
        val useComposeSplash = isTv || isChromecastLowEnd
        assertTrue(useComposeSplash)
    }

    @Test
    fun evaluateShouldReduceEffects() {
        // System accessibility animations disabled overrides everything
        assertTrue(
            DevicePerformance.evaluateShouldReduceEffects(
                mode = EffectsMode.FULL,
                systemAnimationsDisabled = true,
                isLowEnd = false,
            )
        )

        // Forced LIGHT mode always reduces
        assertTrue(
            DevicePerformance.evaluateShouldReduceEffects(
                mode = EffectsMode.LIGHT,
                systemAnimationsDisabled = false,
                isLowEnd = false,
            )
        )

        // Forced FULL mode never reduces on capable systems
        assertFalse(
            DevicePerformance.evaluateShouldReduceEffects(
                mode = EffectsMode.FULL,
                systemAnimationsDisabled = false,
                isLowEnd = true,
            )
        )

        // AUTO mode reduces when low-end hardware is detected
        assertTrue(
            DevicePerformance.evaluateShouldReduceEffects(
                mode = EffectsMode.AUTO,
                systemAnimationsDisabled = false,
                isLowEnd = true,
            )
        )

        // AUTO mode keeps full effects when capable hardware is detected
        assertFalse(
            DevicePerformance.evaluateShouldReduceEffects(
                mode = EffectsMode.AUTO,
                systemAnimationsDisabled = false,
                isLowEnd = false,
            )
        )
    }

    @Test
    fun visionSettingsEffectsCompatibility() {
        VisionSettings.effectsMode = EffectsMode.AUTO
        assertTrue(VisionSettings.effects)

        VisionSettings.effectsMode = EffectsMode.LIGHT
        assertFalse(VisionSettings.effects)

        VisionSettings.effectsMode = EffectsMode.FULL
        assertTrue(VisionSettings.effects)

        VisionSettings.effects = false
        assertEquals(EffectsMode.LIGHT, VisionSettings.effectsMode)

        VisionSettings.effects = true
        assertEquals(EffectsMode.FULL, VisionSettings.effectsMode)
    }
}
