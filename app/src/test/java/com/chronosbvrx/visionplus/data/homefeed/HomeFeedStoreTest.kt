package com.chronosbvrx.visionplus.data.homefeed

import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.SeasonRef
import com.chronosbvrx.visionplus.home.contract.HomeContract
import com.chronosbvrx.visionplus.home.contract.HomeFeedCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El Inicio nunca puede quedar vacío ni mentir: con el equipo recién instalado (sin índice,
 * sin historial y sin canales) el feed sigue teniendo hero, filas y enlaces válidos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeFeedStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aFreshInstallStillHasAHome() {
        val feed = HomeFeedStore.build(context)
        assertFalse("el hero no puede quedar vacío en un equipo nuevo", feed.hero.isEmpty())
        assertFalse("hacen falta filas para navegar", feed.rows.isEmpty())
        assertEquals(HomeContract.VERSION, feed.meta.version)
        assertEquals("Principal", feed.meta.profile)
        assertEquals(1, feed.meta.profiles)
        assertTrue("cada héroe abre su ficha", feed.hero.all { it.openLink.startsWith("visionplus://title/") })
        assertTrue("cada héroe trae arte", feed.hero.all { it.poster.isNotBlank() })
    }

    @Test
    fun everyCardOpensSomethingReal() {
        val feed = HomeFeedStore.build(context)
        for (row in feed.rows) {
            for (item in row.items) {
                assertTrue("${item.title} sin ficha", item.openLink.startsWith("visionplus://title/"))
                assertTrue("${item.title} sin ref", item.ref.isNotBlank())
            }
        }
    }

    @Test
    fun theFeedIsWrittenAtomicallyAndServedFromDisk() {
        val first = HomeFeedStore.readOrBuild(context)
        val file = HomeFeedStore.file(context)
        assertTrue("el feed debe quedar en disco", file.exists())
        assertNotNull("el JSON servido debe decodificar", HomeFeedCodec.decode(first))
        assertEquals("mientras esté fresco, se sirve el mismo JSON", first, HomeFeedStore.readOrBuild(context))
    }

    @Test
    fun theProviderAnswersWithOneJsonRow() {
        val provider = HomeFeedProvider()
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = HomeContract.AUTHORITY
                exported = true
                readPermission = HomeContract.PERMISSION
            },
        )
        val cursor = provider.query(Uri.parse(HomeContract.FEED_URI), null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.columnCount)
            val json = it.getString(0)
            assertNotNull(HomeFeedCodec.decode(json))
        }
    }

    @Test
    fun moviesPlayDirectlyButSeriesOpenTheFichaFirst() {
        val movie = film(id = "xtv:movie", kind = "movie", seasons = emptyList())
        val grouped = film(
            id = "xtv:serie",
            kind = "series",
            seasons = listOf(SeasonRef(1, "s1", "xtv:s1"), SeasonRef(2, "s2", "xtv:s2")),
        )
        val series = film(id = "xtv:serie2", kind = "series", seasons = emptyList())
        assertFalse(HomeFeedStore.isSeriesFilm(movie))
        assertTrue(HomeFeedStore.isSeriesFilm(grouped))
        assertTrue("una serie de una temporada tampoco se reproduce a ciegas", HomeFeedStore.isSeriesFilm(series))
    }

    @Test
    fun duplicateQualityFeedsCollapseToOneChannel() {
        assertEquals(
            "A&E HD y A&E FHD son la misma señal",
            HomeFeedStore.channelKey("A&E HD"),
            HomeFeedStore.channelKey("A&E FHD"),
        )
        assertEquals(HomeFeedStore.channelKey("Netflix"), HomeFeedStore.channelKey("NETFLIX HD"))
        assertFalse(
            "AXN White no es AXN",
            HomeFeedStore.channelKey("AXN") == HomeFeedStore.channelKey("AXN White"),
        )
        assertEquals(HomeFeedStore.channelKey("TUDN MEXICO FHD"), HomeFeedStore.channelKey("TUDN MEXICO"))
    }

    @Test
    fun theGuideProgressIsComputedAgainstTheWallClock() {
        val window = HomeFeedStore.EpgWindow(
            now = "Soy Charro de Levita",
            next = "Noticiero",
            startMs = 1_000L,
            endMs = 2_000L,
        )
        assertEquals(50, window.progressAt(1_500L))
        assertEquals(0, window.progressAt(900L))
        assertEquals(100, window.progressAt(9_000L))
        assertEquals(0, HomeFeedStore.EpgWindow("x", "y").progressAt(1_500L))
    }

    private fun film(id: String, kind: String, seasons: List<SeasonRef>) = Film(
        id = id,
        title = "Título",
        year = 2026,
        durationMin = 100,
        synopsis = "",
        posterUrl = "https://img/p.jpg",
        videoUrl = "xtv:${id.removePrefix("xtv:")}",
        sourceUrl = "xtv",
        kind = kind,
        seasons = seasons,
    )
}
