package com.chronosbvrx.visionplus.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VisionSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vision_settings", Context.MODE_PRIVATE).edit().clear().commit()
        VisionSettings.init(context)
    }

    @Test
    fun defaultSettingsValues() {
        assertEquals("Auto", VisionSettings.defaultQuality)
        assertTrue(VisionSettings.autoPlayNext)
        assertTrue(VisionSettings.autoResume)
        assertEquals("Botón", VisionSettings.skipIntroModeLabel(VisionSettings.skipIntroMode))
        assertFalse(VisionSettings.nextOnCredits)
        assertEquals(0, VisionSettings.defaultAspect)
        assertEquals("Rápido (0.5s)", VisionSettings.liveBuffer)
    }

    @Test
    fun skipIntroOnlyLeapsWhenTheUserPressesByDefault() {
        // Pedido del usuario: el capítulo NO se mueve solo hasta que pulse «Omitir intro».
        // El ajuste se persiste únicamente al tocarlo, así que el default manda en toda
        // instalación que nunca lo cambió (el caso reportado).
        assertEquals(VisionSettings.SKIP_INTRO_BUTTON, VisionSettings.skipIntroMode)
        assertEquals(VisionSettings.SKIP_INTRO_BUTTON, VisionSettings.skipIntroModeOf(context))
        assertEquals("Botón", VisionSettings.skipIntroModeLabel(VisionSettings.skipIntroModeOf(context)))
    }

    @Test
    fun automaticSkipIsAnExplicitChoiceAndPersists() {
        VisionSettings.setSkipIntroMode(context, VisionSettings.SKIP_INTRO_AUTO)
        assertEquals("Automático", VisionSettings.skipIntroModeLabel(VisionSettings.skipIntroMode))
        assertEquals(VisionSettings.SKIP_INTRO_AUTO, VisionSettings.skipIntroModeOf(context))
        VisionSettings.init(context)
        assertEquals(VisionSettings.SKIP_INTRO_AUTO, VisionSettings.skipIntroMode)
        // Y regresar a Botón también se recuerda.
        VisionSettings.setSkipIntroMode(context, VisionSettings.SKIP_INTRO_BUTTON)
        VisionSettings.init(context)
        assertEquals(VisionSettings.SKIP_INTRO_BUTTON, VisionSettings.skipIntroMode)
        assertEquals(VisionSettings.SKIP_INTRO_BUTTON, VisionSettings.skipIntroModeOf(context))
    }

    @Test
    fun theModeCycleTouchesEveryOption() {
        assertEquals(VisionSettings.SKIP_INTRO_AUTO, VisionSettings.nextSkipIntroMode(VisionSettings.SKIP_INTRO_BUTTON))
        assertEquals(VisionSettings.SKIP_INTRO_NEVER, VisionSettings.nextSkipIntroMode(VisionSettings.SKIP_INTRO_AUTO))
        assertEquals(VisionSettings.SKIP_INTRO_BUTTON, VisionSettings.nextSkipIntroMode(VisionSettings.SKIP_INTRO_NEVER))
    }

    @Test
    fun qualityPreferencePersists() {
        VisionSettings.setDefaultQuality(context, "1080p")
        assertEquals("1080p", VisionSettings.defaultQuality)

        // Verify re-reading from SharedPreferences
        VisionSettings.init(context)
        assertEquals("1080p", VisionSettings.defaultQuality)
    }

    @Test
    fun autoPlayNextAndAutoResumePersist() {
        VisionSettings.setAutoPlayNext(context, false)
        VisionSettings.setAutoResume(context, false)
        assertFalse(VisionSettings.autoPlayNext)
        assertFalse(VisionSettings.autoResume)

        // Verify re-reading
        VisionSettings.init(context)
        assertFalse(VisionSettings.autoPlayNext)
        assertFalse(VisionSettings.autoResume)
    }

    @Test
    fun fastCreditsAdvanceIsOptionalAndPersists() {
        assertFalse(VisionSettings.nextOnCreditsOf(context))
        VisionSettings.setNextOnCredits(context, true)
        assertTrue(VisionSettings.nextOnCredits)
        VisionSettings.init(context)
        assertTrue(VisionSettings.nextOnCredits)
        assertTrue(VisionSettings.nextOnCreditsOf(context))
    }

    @Test
    fun defaultAspectPersists() {
        VisionSettings.setDefaultAspect(context, 1)
        assertEquals(1, VisionSettings.defaultAspect)

        VisionSettings.init(context)
        assertEquals(1, VisionSettings.defaultAspect)
    }

    @Test
    fun liveBufferPersists() {
        VisionSettings.setLiveBuffer(context, "Estable (3s)")
        assertEquals("Estable (3s)", VisionSettings.liveBuffer)

        VisionSettings.init(context)
        assertEquals("Estable (3s)", VisionSettings.liveBuffer)
    }

    @Test
    fun formatBytes() {
        assertEquals("0 B", VisionSettings.formatBytes(0L))
        assertEquals("500 B", VisionSettings.formatBytes(500L))
        assertTrue(VisionSettings.formatBytes(2048L).contains("KB"))
        assertTrue(VisionSettings.formatBytes(5 * 1024L * 1024L).contains("MB"))
        assertTrue(VisionSettings.formatBytes(2L * 1024L * 1024L * 1024L).contains("GB"))
    }

    @Test
    fun clearPlaybackHistoryWipesAllEntries() {
        val now = System.currentTimeMillis()
        val entry1 = ResumeEntry(key = "k1", title = "Pelicula 1", positionMs = 50_000L, durationMs = 120_000L, updatedAt = now)
        val entry2 = ResumeEntry(key = "k2", title = "Pelicula 2", positionMs = 40_000L, durationMs = 100_000L, updatedAt = now)
        PlaybackHistory.save(context, entry1, immediate = true)
        PlaybackHistory.save(context, entry2, immediate = true)

        assertEquals(2, PlaybackHistory.list(context).size)

        VisionSettings.clearPlaybackHistory(context)
        assertEquals(0, PlaybackHistory.list(context).size)
    }

    @Test
    fun clearCacheExecutesSafely() {
        val ok = VisionSettings.clearCache(context)
        assertTrue(ok)
    }
}
