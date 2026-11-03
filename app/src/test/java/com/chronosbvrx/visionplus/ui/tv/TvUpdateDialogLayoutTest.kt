package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lista de novedades del actualizador de la tele no puede empujar los botones
 * fuera de la pantalla. Se muestran seis líneas y el resto queda en GitHub.
 */
class TvUpdateDialogLayoutTest {
    @Test
    fun theTvUpdateNotesAreCappedAndTheActionsStayVisible() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvDialogs.kt").readText()
        assertTrue(source.contains("maxLines = 6"))
        assertTrue(source.contains("overflow = TextOverflow.Ellipsis"))
        assertTrue(source.contains("heightIn(max = 120.dp)"))
        val notes = source.indexOf("release.notes")
        val download = source.indexOf("label = PluginUpdatesCopy.DOWNLOAD")
        assertTrue("las notas deben ir antes del botón", notes in 0 until download)
    }
}
