package com.chronosbvrx.visionplus.data.xuper

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardianes v2.5.52: el Modo Offline solo hace lecturas read-only del motor.
 * La vía `NativeJni.o/g/u` directa (SIGSEGV en `star_file_cache`) está eliminada.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class X4KTitanStatusTest {

    @Test
    fun `parseStatusUrls extrae play_url y links`() {
        val raw = """{"res":"{\"play_url\":\"http://127.0.0.1:port/x\",\"links\":[\"http://cdn/a.ts\",\"mem://b\"]}"}"""
        val (url, links) = X4KTitan.parseStatusUrls(raw)
        assertEquals("http://127.0.0.1:port/x", url)
        assertEquals(listOf("http://cdn/a.ts", "mem://b"), links)
    }

    @Test
    fun `parseStatusUrls tolera vacios y rotos`() {
        assertEquals("" to emptyList<String>(), X4KTitan.parseStatusUrls(""))
        assertEquals("" to emptyList<String>(), X4KTitan.parseStatusUrls("{}"))
        assertEquals("" to emptyList<String>(), X4KTitan.parseStatusUrls("{no json"))
        val (url, links) = X4KTitan.parseStatusUrls("""{"res":"{}"}""")
        assertEquals("", url)
        assertTrue(links.isEmpty())
    }

    @Test
    fun `via nativa directa eliminada de X4KTitan`() {
        val src = java.io.File(
            "app/src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KTitan.kt",
        )
        if (src.exists()) {
            val text = src.readText()
            assertFalse(
                "resolveVodStream (vía SIGSEGV) debe seguir eliminado",
                text.contains("fun resolveVodStream("),
            )
            assertFalse(
                "stopBackgroundVod (NativeJni.u directo) debe seguir eliminado",
                text.contains("fun stopBackgroundVod("),
            )
            assertTrue(
                "la lectura read-only queryStatusUrls debe existir",
                text.contains("fun queryStatusUrls("),
            )
        }
    }

    @Test
    fun `servicio offline usa via facade, no nativa directa`() {
        val src = java.io.File(
            "app/src/main/java/com/chronosbvrx/visionplus/data/offline/X4KDownloadService.kt",
        )
        if (src.exists()) {
            val text = src.readText()
            assertTrue(text.contains("playHeadless("))
            assertTrue(text.contains("queryStatusUrls"))
            assertTrue(text.contains("stopHeadless("))
            assertFalse(text.contains("resolveVodStream"))
            assertFalse(text.contains("newVideoView"))
        }
    }

    @Test
    fun `playHeadless rechaza entradas vacias sin tocar nativo`() {
        assertFalse(X4KTitan.playHeadless("", "vod", "T", emptyList()))
        assertFalse(
            X4KTitan.playHeadless(
                "abc",
                "vod",
                "T",
                emptyList(),
            ),
        )
    }
}
