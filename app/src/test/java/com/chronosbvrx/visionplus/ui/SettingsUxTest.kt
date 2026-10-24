package com.chronosbvrx.visionplus.ui

import com.chronosbvrx.visionplus.ui.profile.profileColorIndex
import com.chronosbvrx.visionplus.ui.profile.profileInitial
import com.chronosbvrx.visionplus.ui.tv.tvTopBarVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lote de interfaz premium (2026-10-24): Ajustes y Perfiles de la tele dentro del mismo shell
 * que el Inicio nuevo, con avatares dibujados (sin imágenes) para no gastar memoria.
 */
class SettingsUxTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun settingsAndSearchLiveInsideTheTopBar() {
        // Antes Ajustes/Buscar entraban a pantalla completa con su propio título: parecían otra app.
        assertTrue(tvTopBarVisible(Route.Settings))
        assertTrue(tvTopBarVisible(Route.Search))
        assertTrue(tvTopBarVisible(Route.Home))
        // La píldora flotante del reloj se retira en Ajustes: la barra superior ya trae la hora.
        val clock = source("ui/Clock.kt")
        assertTrue(clock.contains("route != Route.Settings"))
    }

    @Test
    fun theSettingsScreenIsARailInsteadOfGiantTabs() {
        val settings = source("ui/tv/TvSettingsScreen.kt")
        assertTrue("no hay riel", settings.contains("private fun SettingsRailItem"))
        assertTrue("no se usa el riel", settings.contains("SettingsRailItem("))
        // La barra lateral con el acento del seleccionado.
        assertTrue(settings.contains("if (selected) VisionBlue else Color.Transparent"))
        assertFalse("siguen las píldoras gigantes", settings.contains("TvTab(\n"))
    }

    @Test
    fun rowsUseTheWhiteFocusRingOfTheNewHome() {
        val widgets = source("ui/tv/TvWidgets.kt")
        val row = widgets.substringAfter("internal fun TvActionOption")
        // Aro blanco al enfocar y todo el ancho (antes: azul y 60% del ancho).
        assertTrue(row.contains("BorderStroke(2.dp, Color.White)"))
        assertTrue(row.contains("modifier.fillMaxWidth()"))
        assertFalse(row.contains("fillMaxWidth(0.6f)"))
        assertFalse(row.contains("focusedContainerColor = VisionBlue"))
    }

    @Test
    fun profilesUseDrawnAvatarsWithOwnColour() {
        val avatars = source("ui/profile/ProfileAvatar.kt")
        assertTrue(avatars.contains("private val PROFILE_COLORS"))
        assertTrue(avatars.contains("internal fun profileColorIndex"))
        assertTrue(avatars.contains("internal fun profileInitial"))
        // Nada de imágenes: el avatar se dibuja.
        assertFalse(avatars.contains("AsyncImage"))
        assertFalse(avatars.contains("painterResource"))
        val dialog = source("ui/tv/TvProfilesDialog.kt")
        assertTrue("el diálogo no usa avatares", dialog.contains("ProfileAvatar("))
        assertTrue("no hay tarjeta de crear", dialog.contains("isAdd = true"))
        // Determinista y dentro de la paleta.
        val a = profileColorIndex("Sala")
        assertEquals(a, profileColorIndex("  sala "))
        assertTrue(a in 0 until 8)
        assertTrue(profileColorIndex("Axel") in 0 until 8)
    }

    @Test
    fun theAvatarInitialSurvivesAccentsAndSpaces() {
        assertEquals("A", profileInitial("axel"))
        // El acento se respeta: el avatar muestra «Á», no una A pelada.
        assertEquals("Á", profileInitial("  Ángel"))
        assertEquals("J", profileInitial("José Ramón"))
        // Los perfiles automáticos muestran su número: «Perfil 3» → «3», no otra «P».
        assertEquals("3", profileInitial("Perfil 3"))
        assertEquals("12", profileInitial("perfil  12"))
        assertEquals("P", profileInitial("   "))
    }
}
