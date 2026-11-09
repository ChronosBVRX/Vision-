package com.chronosbvrx.visionplus.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.follow.FollowStore
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Estado de las funciones nuevas: series seguidas y canales recientes, por perfil. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UxStateStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun clear() {
        listOf("vision_follow", "vision_live_history").forEach { base ->
            context.getSharedPreferences(ProfileStore.prefsName(context, base), Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Before
    fun setUp() = clear()

    @Test
    fun followedFilmSurvivesWithoutCatalogAndCanBeUnfollowed() {
        val film = Film(
            id = "serie-1", title = "Serie de prueba", year = 2024, durationMin = 42,
            synopsis = "s", posterUrl = "https://poster", videoUrl = "xtv:serie-1", sourceUrl = "xtv",
        )
        assertTrue(FollowStore.toggle(context, film))
        assertTrue(FollowStore.isFollowing(context, film.id))

        val saved = FollowStore.followedFilms(context, emptyList()).single()
        assertEquals(film.id, saved.id)
        assertEquals("https://poster", saved.posterUrl)
        assertEquals("xtv:serie-1", saved.videoUrl)

        assertTrue(FollowStore.unfollow(context, film.id))
        assertFalse(FollowStore.isFollowing(context, film.id))
        assertTrue(FollowStore.followedFilms(context, emptyList()).isEmpty())
    }

    @Test
    fun recentLiveIsDeduplicatedAndNewestFirst() {
        LiveHistory.remember(context, "canal5", "Canal 5", "logo5")
        LiveHistory.remember(context, "azteca7", "Azteca 7", "logo7")
        LiveHistory.remember(context, "canal5", "Canal 5 HD", "logo5b")

        val recent = LiveHistory.recent(context)
        assertEquals(listOf("canal5", "azteca7"), recent.map { it.code })
        assertEquals("Canal 5 HD", recent.first().name)
        assertEquals("canal5", LiveHistory.last(context)?.code)
        assertEquals("xtvlive:canal5", recent.first().toChannel().url)
    }
}
