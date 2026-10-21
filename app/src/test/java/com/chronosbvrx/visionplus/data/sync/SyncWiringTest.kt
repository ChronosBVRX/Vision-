package com.chronosbvrx.visionplus.data.sync

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado que no se puede componer sin red/físico:
 * - La app empuja/trae al volver a primer plano (y tras marcar favorito o vaciar el historial).
 * - Ajustes ofrece el código compartido, vincular el equipo y sincronizar a mano.
 * - La tele solo muestra el código (el teclado está en el teléfono).
 */
class SyncWiringTest {

    @Test
    fun theAppSyncsOnForegroundAndAfterLocalChanges() {
        val activity = source("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("al volver a la app", activity.contains("SyncStore.syncAsync(this)"))

        val title = source("src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt")
        assertTrue("favorito", title.contains("syncAsync(context)"))
        val tvTitle = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt")
        assertTrue("favorito en la tele", tvTitle.contains("syncAsync(context)"))
        val settings = source("src/main/java/com/chronosbvrx/visionplus/ui/phone/SettingsScreen.kt")
        assertTrue("vaciar historial", settings.contains("SyncStore.syncAsync(context, force = true)"))
    }

    @Test
    fun settingsShowTheCodeAndAllowLinkingAndForgetting() {
        val phone = source("src/main/java/com/chronosbvrx/visionplus/ui/settings/SyncSettingsCard.kt")
        assertTrue("muestra el código", phone.contains("formatSyncCode(state.code)"))
        assertTrue("vincular", phone.contains("SyncStore.setCode(context, input)"))
        assertTrue("sincronizar ahora", phone.contains("SyncStore.syncAsync(context, force = true)"))
        assertTrue("olvidar", phone.contains("SyncStore.forget(context)"))

        val tv = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSyncDialog.kt")
        assertTrue("la tele enseña el código", tv.contains("formatSyncCode(state.code)"))
        assertTrue("la tele empuja a mano", tv.contains("SyncStore.syncAsync(context, force = true)"))
    }

    private fun source(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
