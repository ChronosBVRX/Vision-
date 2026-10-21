package com.chronosbvrx.visionplus.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.launcher.LauncherLinks
import com.chronosbvrx.visionplus.data.launcher.WatchLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El deep link de las tarjetas del launcher: `visionplus://watch/<clave>` reanuda la película
 * o el capítulo exacto; su respaldo abre desde el principio si el historial ya se podó.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LauncherDeepLinkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("playback_history", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun linkRoundTripSurvivesAwkwardKeys() {
        val link = WatchLink(
            key = "xtv:ab/c?d#e", title = "Tom & Jerry · C2", posterUrl = "https://x/y.jpg?q=1&z=2",
            code = "ab/c", videoUrl = "",
        )
        val parsed = LauncherLinks.parse(LauncherLinks.uriFor(link))
        assertEquals(link, parsed)
    }

    @Test
    fun routeResumesTheEpisodeFromHistory() {
        PlaybackHistory.save(
            context,
            ResumeEntry(
                key = "abc", title = "La Casa de Papel · T2 · C3", posterUrl = "p",
                xtvCode = "abc", seriesId = "s1",
                episodeIds = listOf("e1", "e2", "e3"), episodeIndex = 2,
                seasonIds = listOf("t1", "t2"), seasonNames = listOf("T1", "T2"), seasonIndex = 1,
                positionMs = 120_000, durationMs = 600_000, updatedAt = System.currentTimeMillis(),
            ),
        )
        val route = launcherRouteFor(
            context,
            LauncherLinks.uriFor(WatchLink(key = "abc", title = "La Casa de Papel · T2 · C3")),
        ) as Route.Player
        assertEquals("xtv:abc", route.film.videoUrl)
        assertEquals(120_000L, route.episode?.resumeMs)
        assertEquals("s1", route.episode?.seriesId)
        assertEquals(2, route.episode?.episodeIndex)
        assertEquals(1, route.episode?.seasonIndex)
    }

    @Test
    fun routeResumesAMovieFromHistory() {
        PlaybackHistory.save(
            context,
            ResumeEntry(
                key = "m1", title = "Película", posterUrl = "p",
                xtvCode = "m1", positionMs = 300_000, durationMs = 600_000,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val route = launcherRouteFor(context, LauncherLinks.uriFor(WatchLink(key = "m1"))) as Route.Player
        assertEquals("xtv:m1", route.film.videoUrl)
        // En XTV el minuto viaja en el episodio (la app lo trata como reproducción con contexto).
        assertEquals(300_000L, route.episode?.resumeMs)
    }

    @Test
    fun watchLinkRoundTripCarriesThePhoneMinute() {
        val link = WatchLink(
            key = "xtv:ep5", title = "La Casa de Papel · T2 · C5", code = "ep5",
            seriesId = "t2ref", resumeMs = 754_000,
        )
        assertEquals(754_000L, LauncherLinks.parse(LauncherLinks.uriFor(link))?.resumeMs)
    }

    @Test
    fun thePhoneMinuteWinsOverTheTvHistoryForAMovie() {
        PlaybackHistory.save(
            context,
            ResumeEntry(
                key = "m1", title = "Película", xtvCode = "m1",
                positionMs = 60_000, durationMs = 600_000,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val route = launcherRouteFor(
            context,
            LauncherLinks.uriFor(
                WatchLink(key = "xtv:m1", title = "Película", code = "m1", resumeMs = 300_000),
            ),
        ) as Route.Player
        assertEquals("el minuto del teléfono manda", 300_000L, route.resumeMs)
    }

    @Test
    fun thePhoneMinuteWinsOverTheTvHistoryForAnEpisode() {
        PlaybackHistory.save(
            context,
            ResumeEntry(
                key = "ep5", title = "La Casa de Papel · T2 · C5", xtvCode = "ep5", seriesId = "t2ref",
                episodeIds = listOf("ep4", "ep5"), seasonIds = listOf("t1ref", "t2ref"),
                positionMs = 60_000, durationMs = 600_000, updatedAt = System.currentTimeMillis(),
            ),
        )
        val route = launcherRouteFor(
            context,
            LauncherLinks.uriFor(
                WatchLink(
                    key = "xtv:ep5", title = "La Casa de Papel · T2 · C5", code = "ep5",
                    seriesId = "t2ref", seasonIndex = 1,
                    seasonIds = listOf("t1ref", "t2ref"), seasonNames = listOf("T1", "T2"),
                    resumeMs = 420_000,
                ),
            ),
        ) as Route.Player
        assertEquals("el minuto del teléfono manda", 420_000L, route.episode?.resumeMs)
        assertEquals("ep5", route.episode?.code)
        assertEquals("t2ref", route.episode?.seriesId)
    }

    @Test
    fun episodeContextSurvivesTheLinkRoundTrip() {
        val link = WatchLink(
            key = "xtv:ep5", title = "La Casa de Papel · T2 · C5", posterUrl = "p", code = "ep5",
            seriesId = "t2ref", seasonIndex = 1,
            seasonIds = listOf("t1ref", "t2ref"), seasonNames = listOf("T1", "T2"),
        )
        assertEquals(link, LauncherLinks.parse(LauncherLinks.uriFor(link)))
    }

    @Test
    fun routeOpensTheExactEpisodeTheRemoteCastPicked() {
        val route = launcherRouteFor(
            context,
            LauncherLinks.uriFor(
                WatchLink(
                    key = "xtv:ep5", title = "La Casa de Papel · T2 · C5", posterUrl = "p", code = "ep5",
                    seriesId = "t2ref", seasonIndex = 1,
                    seasonIds = listOf("t1ref", "t2ref"), seasonNames = listOf("T1", "T2"),
                ),
            ),
        ) as Route.Player
        assertEquals("xtv:ep5", route.film.videoUrl)
        assertEquals("La Casa de Papel · T2 · C5", route.film.title)
        assertEquals("ep5", route.episode?.code)
        assertEquals("t2ref", route.episode?.seriesId)
        assertEquals(1, route.episode?.seasonIndex)
        assertEquals(listOf("t1ref", "t2ref"), route.episode?.seasonIds)
        assertEquals(listOf("T1", "T2"), route.episode?.seasonNames)
    }

    @Test
    fun aPlainWatchLinkStillCarriesNoEpisodeContext() {
        val parsed = LauncherLinks.parse(LauncherLinks.uriFor(WatchLink(key = "m1", title = "Película")))!!
        assertEquals("", parsed.seriesId)
        assertEquals(0, parsed.seasonIndex)
        assertTrue(parsed.seasonIds.isEmpty())
        assertTrue(parsed.seasonNames.isEmpty())
    }

    @Test
    fun routeFallsBackToDirectPlayWhenHistoryIsGone() {
        val route = launcherRouteFor(
            context,
            LauncherLinks.uriFor(WatchLink(key = "zzz", title = "Película X", code = "zzz", posterUrl = "p")),
        ) as Route.Player
        assertEquals("xtv:zzz", route.film.videoUrl)
        assertEquals("Película X", route.film.title)
        assertEquals(0L, route.resumeMs)
    }

    @Test
    fun routeFallsBackToTheVideoUrlWhenThereIsNoXtvCode() {
        val route = launcherRouteFor(
            context,
            LauncherLinks.uriFor(WatchLink(key = "v1", title = "Offline", videoUrl = "vault:abc")),
        ) as Route.Player
        assertEquals("vault:abc", route.film.videoUrl)
    }

    @Test
    fun routeIgnoresForeignOrEmptyUris() {
        assertNull(launcherRouteFor(context, Uri.parse("https://example.com/watch/x")))
        assertNull(launcherRouteFor(context, Uri.parse("visionplus://other/x")))
        assertNull(launcherRouteFor(context, null))
    }
}
