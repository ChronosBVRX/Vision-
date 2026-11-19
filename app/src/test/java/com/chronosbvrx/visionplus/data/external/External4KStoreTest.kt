package com.chronosbvrx.visionplus.data.external

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class External4KStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.filesDir.resolve("external_4k.json").delete()
    }

    @Test
    fun aFreshSourceIsReturnedAndAnExpiredOneIsDropped() {
        val now = System.currentTimeMillis()
        External4KStore.put(context, "xtv:1", listOf(
            FourKSource("https://a.mkv", "A", "Latino", expiresAt = now + 60_000),
            FourKSource("https://b.mkv", "B", "Latino", expiresAt = now - 60_000),
        ))
        val fresh = External4KStore.fresh(context, "xtv:1")
        assertEquals(1, fresh.size)
        assertEquals("https://a.mkv", fresh[0].url)
        assertTrue(External4KStore.checkedRecently(context, "xtv:1"))
    }

    @Test
    fun anEmptyAnswerIsANegativeCacheAndDoesNotReturnSources() {
        External4KStore.put(context, "xtv:2", emptyList())
        assertTrue(External4KStore.checkedRecently(context, "xtv:2"))
        assertFalse(External4KStore.fresh(context, "xtv:2").isNotEmpty())
    }

    @Test
    fun anOldEntryWithoutSchemaIsIgnoredSoTheNewProviderIsQueried() {
        val now = System.currentTimeMillis()
        context.filesDir.resolve("external_4k.json").writeText(
            """{"titles":{"xtv:9":{"at":$now,"sources":[{"url":"http://192.168.1.101:7015/x.mkv","provider":"LAN","language":"Latino","expiresAt":${now + 60_000}}]}}}""",
        )
        assertFalse(External4KStore.fresh(context, "xtv:9").isNotEmpty())
        assertFalse(External4KStore.checkedRecently(context, "xtv:9"))
    }

    @Test
    fun imdbIsCachedPerTitle() {
        External4KStore.putImdb(context, "xtv:3", "tt0133093")
        assertEquals("tt0133093", External4KStore.imdb(context, "xtv:3"))
    }
}
