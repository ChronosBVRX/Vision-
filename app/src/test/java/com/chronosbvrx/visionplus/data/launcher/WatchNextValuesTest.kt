package com.chronosbvrx.visionplus.data.launcher

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tvprovider.media.tv.TvContractCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Los valores que el canal Watch Next recibe por cada tarjeta: tipo, minuto, duración,
 * último toque y el intent que el launcher dispara al seleccionarla.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WatchNextValuesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun episodeCardCarriesEverythingTheLauncherNeeds() {
        val spec = WatchNextSpec(
            key = "abc", isEpisode = true, title = "Serie", episodeTitle = "T2 · C3",
            seasonNumber = 2, episodeNumber = 3, seriesId = "s1",
            positionMs = 120_000L, durationMs = 600_000L, updatedAt = 1_700_000_000_000L,
            posterUrl = "https://cdn/poster.jpg", code = "abc", videoUrl = "",
            displayTitle = "Serie · T2 · C3",
        )
        val values = LauncherWatchNext.buildValues(context, spec, Uri.parse("content://p/art.jpg"))
        assertEquals(
            TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
            values.getAsInteger(TvContractCompat.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE),
        )
        assertEquals(
            TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE,
            values.getAsInteger(TvContractCompat.PreviewProgramColumns.COLUMN_TYPE),
        )
        assertEquals("Serie", values.getAsString(TvContractCompat.Programs.COLUMN_TITLE))
        assertEquals("abc", values.getAsString(TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID))
        assertEquals(
            120_000,
            values.getAsInteger(TvContractCompat.PreviewProgramColumns.COLUMN_LAST_PLAYBACK_POSITION_MILLIS),
        )
        assertEquals(
            600_000,
            values.getAsInteger(TvContractCompat.PreviewProgramColumns.COLUMN_DURATION_MILLIS),
        )
        assertEquals(
            1_700_000_000_000L,
            values.getAsLong(TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS),
        )
        assertEquals("3", values.getAsString(TvContractCompat.Programs.COLUMN_EPISODE_DISPLAY_NUMBER))
        assertEquals("2", values.getAsString(TvContractCompat.Programs.COLUMN_SEASON_DISPLAY_NUMBER))
        assertEquals("content://p/art.jpg", values.getAsString(TvContractCompat.Programs.COLUMN_POSTER_ART_URI))
        val intentUri = values.getAsString(TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI)
        assertNotNull(intentUri)
        assertTrue(intentUri!!.startsWith("intent:"))
        // El launcher abre el deep link de la app: el dato va dentro del intent serializado.
        val launched = android.content.Intent.parseUri(intentUri, android.content.Intent.URI_INTENT_SCHEME)
        assertEquals("visionplus", launched.data?.scheme)
        assertEquals("watch", launched.data?.host)
    }

    @Test
    fun movieCardIsAMovieAndTheLinkSurvivesTheIntentRoundTrip() {
        val spec = WatchNextSpec(
            key = "xtv:9", isEpisode = false, title = "Peli", episodeTitle = "",
            seasonNumber = 0, episodeNumber = 0, seriesId = "",
            positionMs = 60_000L, durationMs = 300_000L, updatedAt = 42L,
            posterUrl = "https://cdn/p.jpg", code = "9", videoUrl = "", displayTitle = "Peli",
        )
        val values = LauncherWatchNext.buildValues(context, spec, null)
        assertEquals(
            TvContractCompat.PreviewProgramColumns.TYPE_MOVIE,
            values.getAsInteger(TvContractCompat.PreviewProgramColumns.COLUMN_TYPE),
        )
        val intentUri = values.getAsString(TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI)!!
        val launched = android.content.Intent.parseUri(intentUri, android.content.Intent.URI_INTENT_SCHEME)
        val link = LauncherLinks.parse(launched.data)
        assertEquals("xtv:9", link?.key)
        assertEquals("9", link?.code)
    }
}
