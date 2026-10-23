package com.chronosbvrx.visionplus.data.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardián del cableado de perfiles: los almacenes que son de una persona deben pedirle el
 * nombre de archivo al perfil activo, y las dos interfaces (móvil y tele) deben ofrecer el
 * cambio recargando la pantalla. Si alguien vuelve a escribir `"vision_user"` a mano, esto
 * falla y el perfil deja de aislarse (el bug más caro de esta función).
 */
class ProfileWiringTest {

    private val root = "src/main/java/com/chronosbvrx/visionplus"

    @Test
    fun everyPersonalStoreAsksTheActiveProfileForItsFile() {
        val stores = mapOf(
            "Mi lista" to "$root/data/UserLibrary.kt",
            "historial" to "$root/data/PlaybackHistory.kt",
            "continuidad" to "$root/data/sync/SyncStore.kt",
            "series seguidas" to "$root/data/follow/FollowStore.kt",
        )
        stores.forEach { (what, path) ->
            val text = File(path).readText()
            assertTrue("$what debe resolver sus preferencias por perfil", text.contains("ProfileStore.prefsName"))
        }
        // El reproductor (`:x4k`) lee el XML directo: ese nombre también es del perfil.
        val history = File(stores.getValue("historial")).readText()
        assertTrue("el XML fresco es del perfil activo", history.contains("xmlFileNameFor"))
    }

    @Test
    fun thePhoneOffersProfilesAndReloadsAfterSwitching() {
        val card = File("$root/ui/settings/ProfilesCard.kt")
        assertTrue(card.exists())
        val text = card.readText()
        assertTrue("se puede crear", text.contains("ProfileStore.create("))
        assertTrue("se puede renombrar", text.contains("ProfileStore.rename("))
        assertTrue("se puede borrar", text.contains("ProfileStore.delete("))
        assertTrue("cambiar de perfil recarga la pantalla", text.contains("recreate()"))
        assertTrue(
            "Ajustes móvil muestra la tarjeta",
            File("$root/ui/phone/SettingsScreen.kt").readText().contains("ProfilesSettingsSection()"),
        )
    }

    @Test
    fun theTvOffersProfilesThroughTheGuardedDialog() {
        val dialog = File("$root/ui/tv/TvProfilesDialog.kt")
        assertTrue(dialog.exists())
        val text = dialog.readText()
        assertTrue(text.contains("ProfileStore.switchTo("))
        assertTrue(text.contains("recreate()"))
        // TvDialog incrementa FocusRescue.dialogsOpen: el rescate de foco no puede robarle teclas.
        assertTrue(text.contains("TvDialog("))
        assertFalse("la tele usa el diálogo de la casa", text.contains("import androidx.compose.ui.window.Dialog"))
        assertTrue(
            "Ajustes TV abre el diálogo",
            File("$root/ui/tv/TvSettingsScreen.kt").readText().contains("TvProfilesDialog("),
        )
    }
}
