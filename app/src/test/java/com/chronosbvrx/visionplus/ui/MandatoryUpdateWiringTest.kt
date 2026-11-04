package com.chronosbvrx.visionplus.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La actualización obligatoria no puede ofrecer "Cerrar": el usuario solo sale instalando.
 * Se vigila el cableado de los dos diálogos (móvil y tele).
 */
class MandatoryUpdateWiringTest {

    @Test
    fun theMobileForcedDialogHasNoCloseAction() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/ui/plugins/PluginUpdates.kt").readText()
        assertTrue(source.contains("PluginUpdatesCopy.MIGRATION_TITLE"))
        assertTrue(source.contains("MigrationNotice.headline"))
        assertTrue(source.contains("if (!forced)"))
        assertTrue(source.contains("MIGRATION_RETRY"))
    }

    @Test
    fun theTvForcedDialogHasNoCloseAction() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvDialogs.kt").readText()
        assertTrue(source.contains("PluginUpdatesCopy.MIGRATION_TITLE"))
        assertTrue(source.contains("MigrationNotice.headline"))
        assertTrue(source.contains("if (!forced)"))
        assertTrue(source.contains("MIGRATION_RETRY"))
    }
}
