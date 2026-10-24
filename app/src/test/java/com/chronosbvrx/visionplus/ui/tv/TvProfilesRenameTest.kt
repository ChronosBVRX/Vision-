package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editar el nombre del perfil desde la tele: el diálogo tiene la acción, reutiliza el teclado
 * del mando (el del buscador) y guarda con `ProfileStore.rename`. El móvil ya lo tenía; este
 * guardián evita que la tele se quede atrás.
 */
class TvProfilesRenameTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theTvDialogOffersRenameAndTheRemoteKeyboard() {
        val tv = source("ui/tv/TvProfilesDialog.kt")
        assertTrue("falta la acción Renombrar", tv.contains("Renombrar «"))
        assertTrue("falta el modal de escritura", tv.contains("RenameProfileDialog("))
        assertTrue("no usa el campo de texto", tv.contains("BasicTextField("))
        assertTrue("no pide el teclado nativo de la tele", tv.contains("LocalSoftwareKeyboardController"))
        assertTrue("no guarda el nombre", tv.contains("ProfileStore.rename(context, target.id, name)"))
    }

    @Test
    fun theTvEditIconAppearsOnFocusWithoutSelectingTheProfile() {
        val tv = source("ui/tv/TvProfilesDialog.kt")
        assertTrue("falta el lápiz por perfil", tv.contains("Icons.Rounded.Edit"))
        assertTrue("el lápiz no aparece al enfocar", tv.contains("cardFocused || editFocused"))
        assertTrue("el lápiz no se puede alcanzar con Abajo", tv.contains("softFocusDown(editFocus)"))
        assertTrue("el lápiz no abre la edición", tv.contains("editing = profile"))
    }

    @Test
    fun thePhoneEditIconIsInEveryProfileCard() {
        val phone = source("ui/settings/ProfilesCard.kt")
        assertTrue("el móvil no tiene lápiz por perfil", phone.contains("Icons.Rounded.Edit"))
        assertTrue("el lápiz no edita ese perfil", phone.contains("renameTarget = profile"))
        assertTrue("el diálogo no renombra el perfil elegido", phone.contains("ProfileStore.rename(context, target.id, name)"))
    }

    @Test
    fun thePhoneStillRenamesProfiles() {
        val phone = source("ui/settings/ProfilesCard.kt")
        assertTrue("el móvil perdió Renombrar", phone.contains("Renombrar"))
        assertTrue("el móvil no guarda el nombre elegido", phone.contains("ProfileStore.rename(context, target.id, name)"))
    }
}
