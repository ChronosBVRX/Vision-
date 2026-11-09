package com.chronosbvrx.visionplus.data.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Imágenes de perfil: catálogo de cine/series/anime y persistencia del avatar elegido.
 * Cada perfil guarda solo el id, así que un perfil viejo (sin avatar) sigue funcionando y
 * el arte se puede actualizar sin migrar datos.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProfileAvatarTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanProfiles() {
        ProfileStore.switchTo(context, ProfileStore.DEFAULT_ID)
        ProfileStore.profiles(context)
            .filter { it.id != ProfileStore.DEFAULT_ID }
            .forEach { ProfileStore.delete(context, it.id) }
        ProfileStore.rename(context, ProfileStore.DEFAULT_ID, ProfileStore.DEFAULT_NAME)
        ProfileStore.setAvatar(context, ProfileStore.DEFAULT_ID, "")
    }

    @After
    fun backToTheDefaultProfile() {
        cleanProfiles()
    }

    @Test
    fun theCatalogHasUniquePopularImagesForMoviesSeriesAndAnime() {
        assertTrue("el catálogo quedó vacío", ProfileAvatars.all.size >= 18)
        assertEquals(
            "los ids de las imágenes no pueden repetirse",
            ProfileAvatars.all.size,
            ProfileAvatars.all.map { it.id }.toSet().size,
        )
        ProfileAvatarCategory.entries.forEach { category ->
            assertEquals(
                "deben ser 20 personajes de ${category.label}",
                20,
                ProfileAvatars.of(category).size,
            )
        }
        ProfileAvatars.all.forEach { option ->
            assertTrue("imagen sin nombre: ${option.id}", option.label.isNotBlank())
            assertNotNull("no existe el dibujo de ${option.id}", option.drawable)
            assertNotNull("el id ${option.id} no se encuentra", ProfileAvatars.find(option.id))
        }
        assertNull("un id inventado no debe resolver", ProfileAvatars.find("no_existe"))
    }

    @Test
    fun avatarsAreSanitizedAgainstCorruptJson() {
        assertEquals("cine_barbie", ProfileStore.sanitizeAvatar("  cine_barbie  "))
        // Un id corrupto pierde puntos y barras (no puede escapar a una ruta); como no está
        // en el catálogo, la interfaz cae al avatar de inicial.
        assertEquals("otro", ProfileStore.sanitizeAvatar("../../otro"))
        assertNull(ProfileAvatars.find(ProfileStore.sanitizeAvatar("../../otro")))
        assertEquals(40, ProfileStore.sanitizeAvatar("a".repeat(80)).length)
        val parsed = ProfileStore.parseProfiles(
            """[{"id":"principal","name":"Casa","avatar":"../malo"}]"""
        )
        assertEquals("malo", parsed.first().avatar)
        assertNull(ProfileAvatars.find(parsed.first().avatar))
    }

    @Test
    fun creatingRenamingAndChangingTheImagePersistTheAvatar() {
        val profile = ProfileStore.create(context, "Sala", "series_stranger")!!
        assertEquals("series_stranger", profile.avatar)
        assertEquals("series_stranger", ProfileStore.profiles(context).first { it.id == profile.id }.avatar)

        // Renombrar no borra la imagen elegida.
        assertTrue(ProfileStore.rename(context, profile.id, "Sala de cine"))
        assertEquals("series_stranger", ProfileStore.profiles(context).first { it.id == profile.id }.avatar)

        // Cambiar solo la imagen no toca el nombre.
        assertTrue(ProfileStore.setAvatar(context, profile.id, "anime_onepiece"))
        val updated = ProfileStore.profiles(context).first { it.id == profile.id }
        assertEquals("Sala de cine", updated.name)
        assertEquals("anime_onepiece", updated.avatar)

        // Quitar la imagen vuelve al avatar de inicial.
        assertTrue(ProfileStore.setAvatar(context, profile.id, ""))
        assertEquals("", ProfileStore.profiles(context).first { it.id == profile.id }.avatar)
    }

    @Test
    fun oldProfilesWithoutAvatarStillLoad() {
        val oldJson = """[{"id":"principal","name":"Casa","createdAt":1}]"""
        val parsed = ProfileStore.parseProfiles(oldJson)
        assertEquals(1, parsed.size)
        assertEquals("Casa", parsed.first().name)
        assertEquals("", parsed.first().avatar)
        assertTrue(ProfileStore.parseProfiles(ProfileStore.encodeProfiles(parsed)).first().avatar.isEmpty())
    }
}
