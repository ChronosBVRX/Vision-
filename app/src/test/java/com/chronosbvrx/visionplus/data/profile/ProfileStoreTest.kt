package com.chronosbvrx.visionplus.data.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.UserLibrary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Perfiles sin cuentas: cada persona ve SU «Seguir viendo», SU Mi lista y SUS series.
 *
 * Lo importante que se prueba aquí:
 * - El perfil «Principal» conserva los nombres de archivo de siempre → actualizar no pierde nada.
 * - Cambiar de perfil aísla de verdad los datos (favoritos e historial).
 * - Borrar un perfil borra solo lo suyo; el principal no se puede borrar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProfileStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startFromTheDefaultProfile() {
        ProfileStore.switchTo(context, ProfileStore.DEFAULT_ID)
        ProfileStore.profiles(context)
            .filter { it.id != ProfileStore.DEFAULT_ID }
            .forEach { ProfileStore.delete(context, it.id) }
        ProfileStore.rename(context, ProfileStore.DEFAULT_ID, ProfileStore.DEFAULT_NAME)
        PlaybackHistory.clear(context)
    }

    @After
    fun backToTheDefaultProfile() {
        ProfileStore.switchTo(context, ProfileStore.DEFAULT_ID)
        ProfileStore.profiles(context)
            .filter { it.id != ProfileStore.DEFAULT_ID }
            .forEach { ProfileStore.delete(context, it.id) }
        PlaybackHistory.clear(context)
    }

    // ----- piezas puras -----

    @Test
    fun theMainProfileKeepsTheLegacyFileNames() {
        assertEquals("vision_user", ProfileStore.prefsNameFor("vision_user", ProfileStore.DEFAULT_ID))
        assertEquals("vision_user__p1", ProfileStore.prefsNameFor("vision_user", "p1"))
        assertEquals("playback_history__p2.xml", ProfileStore.xmlFileNameFor("playback_history", "p2"))
        // Un archivo de perfil con basura no puede apuntar a cualquier preferencia.
        assertEquals("vision_user", ProfileStore.prefsNameFor("vision_user", "../otro"))
        assertEquals("vision_user", ProfileStore.prefsNameFor("vision_user", ""))
    }

    @Test
    fun namesAreSanitizedForTheUiAndStorage() {
        assertEquals("Sala", ProfileStore.sanitizeName("  Sala  "))
        assertEquals("Juan Perez", ProfileStore.sanitizeName("Juan   Perez"))
        assertEquals("a b", ProfileStore.sanitizeName("a\u0000b"))
        assertEquals(24, ProfileStore.sanitizeName("x".repeat(80)).length)
        assertEquals("", ProfileStore.sanitizeName("   "))
    }

    @Test
    fun idsAreSequentialAndSkipTheUsedOnes() {
        val profiles = listOf(
            Profile(ProfileStore.DEFAULT_ID, "Principal"),
            Profile("p1", "Uno"),
            Profile("p3", "Tres"),
        )
        assertEquals("p2", ProfileStore.nextId(profiles))
        assertTrue(ProfileStore.isValidId("p12"))
        assertFalse(ProfileStore.isValidId("p1x"))
        assertFalse(ProfileStore.isValidId("../x"))
        assertFalse(ProfileStore.canDelete(ProfileStore.DEFAULT_ID, profiles))
        assertTrue(ProfileStore.canDelete("p1", profiles))
    }

    @Test
    fun storedProfilesAreSaneAndTheMainProfileIsAlwaysFirst() {
        val onlyDefault = ProfileStore.parseProfiles(null)
        assertEquals(1, onlyDefault.size)
        assertEquals(ProfileStore.DEFAULT_ID, onlyDefault[0].id)

        val raw = """[
            {"id":"p1","name":"  Sala  ","createdAt":5},
            {"id":"../malo","name":"No"},
            {"id":"p1","name":"Duplicado"},
            {"id":"principal","name":""}
        ]"""
        val parsed = ProfileStore.parseProfiles(raw)
        assertEquals(listOf(ProfileStore.DEFAULT_ID, "p1"), parsed.map { it.id })
        assertEquals(ProfileStore.DEFAULT_NAME, parsed[0].name)
        assertEquals("Sala", parsed[1].name)
        assertEquals(2, ProfileStore.parseProfiles(ProfileStore.encodeProfiles(parsed)).size)
    }

    // ----- almacenamiento real -----

    @Test
    fun switchingProfilesIsolatesFavorites() {
        val forDefault = film("prof-fav-default-${System.nanoTime()}")
        val forSecond = film("prof-fav-second-${System.nanoTime()}")
        UserLibrary.toggleFavorite(context, forDefault)
        assertTrue(UserLibrary.isFavorite(context, forDefault.id))

        val second = ProfileStore.create(context, "Sala")!!
        assertTrue(ProfileStore.switchTo(context, second.id))
        assertFalse(UserLibrary.isFavorite(context, forDefault.id))
        UserLibrary.toggleFavorite(context, forSecond)
        assertTrue(UserLibrary.isFavorite(context, forSecond.id))

        assertTrue(ProfileStore.switchTo(context, ProfileStore.DEFAULT_ID))
        assertTrue(UserLibrary.isFavorite(context, forDefault.id))
        assertFalse(UserLibrary.isFavorite(context, forSecond.id))

        // Limpieza de lo que dejó la prueba.
        UserLibrary.toggleFavorite(context, forDefault)
    }

    @Test
    fun switchingProfilesIsolatesTheHistoryAndItsXmlFile() {
        PlaybackHistory.save(context, entry("cap-a-${System.nanoTime()}"), immediate = true)
        assertTrue(PlaybackHistory.list(context).isNotEmpty())

        val second = ProfileStore.create(context, "Sala")!!
        ProfileStore.switchTo(context, second.id)
        assertTrue(PlaybackHistory.list(context).isEmpty())
        PlaybackHistory.save(context, entry("cap-b-${System.nanoTime()}"), immediate = true)
        assertEquals(1, PlaybackHistory.list(context).size)

        ProfileStore.switchTo(context, ProfileStore.DEFAULT_ID)
        assertEquals(1, PlaybackHistory.list(context).size)
        assertTrue(PlaybackHistory.list(context).first().key.startsWith("cap-a-"))
        // El principal lee el XML heredado; el segundo, el suyo.
        assertTrue(File(File(context.dataDir, "shared_prefs"), PlaybackHistory.PREFS_XML_FILE).exists())
        assertTrue(
            File(File(context.dataDir, "shared_prefs"), ProfileStore.xmlFileNameFor("playback_history", second.id)).exists()
        )
    }

    @Test
    fun deletingAProfileWipesOnlyItsDataAndFallsBackToTheMainOne() {
        val doomed = ProfileStore.create(context, "Temporal")!!
        ProfileStore.switchTo(context, doomed.id)
        val favorite = film("prof-del-${System.nanoTime()}")
        UserLibrary.toggleFavorite(context, favorite)
        PlaybackHistory.save(context, entry("cap-del-${System.nanoTime()}"))
        assertTrue(UserLibrary.isFavorite(context, favorite.id))

        assertTrue(ProfileStore.delete(context, doomed.id))
        assertEquals(ProfileStore.DEFAULT_ID, ProfileStore.activeId(context))
        assertFalse(ProfileStore.profiles(context).any { it.id == doomed.id })
        assertFalse(UserLibrary.favorites(context).any { it.id == favorite.id })

        // El id se puede reutilizar, pero arranca vacío (los datos sí se borraron).
        val again = ProfileStore.create(context, "Temporal")!!
        assertEquals(doomed.id, again.id)
        ProfileStore.switchTo(context, again.id)
        assertTrue(UserLibrary.favorites(context).isEmpty())
        assertTrue(PlaybackHistory.list(context).isEmpty())
    }

    @Test
    fun theMainProfileCannotBeDeletedAndTheLimitIsFive() {
        assertFalse(ProfileStore.delete(context, ProfileStore.DEFAULT_ID))
        repeat(ProfileStore.MAX_PROFILES - 1) { i ->
            assertNotNull(ProfileStore.create(context, "Persona $i"))
        }
        assertEquals(ProfileStore.MAX_PROFILES, ProfileStore.profiles(context).size)
        assertNull(ProfileStore.create(context, "Sobra"))
        assertFalse(ProfileStore.switchTo(context, "no-existe"))
    }

    @Test
    fun renamingPersistsAndAnActiveIdFromNowhereFallsBackToTheMainProfile() {
        val profile = ProfileStore.create(context, "Cine")!!
        assertTrue(ProfileStore.rename(context, profile.id, "  Cine en casa  "))
        assertEquals("Cine en casa", ProfileStore.profiles(context).first { it.id == profile.id }.name)
        assertFalse(ProfileStore.rename(context, profile.id, "   "))

        File(context.filesDir, "active_profile").writeText("../../otro")
        assertEquals(ProfileStore.DEFAULT_ID, ProfileStore.activeId(context))
    }

    private fun film(id: String) = Film(
        id = id,
        title = "Título $id",
        year = 2026,
        durationMin = 100,
        synopsis = "",
        posterUrl = "",
        videoUrl = "xtv:$id",
        sourceUrl = "xtv",
    )

    private fun entry(key: String) = ResumeEntry(
        key = key,
        title = "Serie · C1",
        xtvCode = key,
        seriesId = "serie-perfil",
        episodeIds = listOf("a"),
        positionMs = 120_000,
        durationMs = 600_000,
        updatedAt = System.currentTimeMillis(),
    )
}
