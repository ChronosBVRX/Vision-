package com.chronosbvrx.visionplus.home.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El contrato es lo único que comparten los dos APK: sus nombres no pueden cambiar solos. */
class EnginePrewarmContractTest {

    @Test
    fun theServiceAndPermissionLiveInTheVisionPackage() {
        assertTrue(EnginePrewarmContract.APP_PACKAGE == "com.chronosbvrx.visionplus")
        assertTrue(
            EnginePrewarmContract.SERVICE_CLASS.startsWith("${EnginePrewarmContract.APP_PACKAGE}."),
        )
        assertTrue(
            EnginePrewarmContract.PERMISSION.startsWith("${EnginePrewarmContract.APP_PACKAGE}.permission."),
        )
        assertEquals(1, EnginePrewarmContract.VERSION)
    }

    @Test
    fun theRamGateKeepsOneAndAHalfGigabyteDevicesOut() {
        assertTrue(EnginePrewarmContract.MIN_RAM_MB in 1_800L..2_048L)
        assertTrue(EnginePrewarmContract.READY_TTL_MS > 0L)
        assertTrue(EnginePrewarmContract.BIND_DELAY_MS > 0L)
    }
}
