package com.chronosbvrx.visionplus.ui.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardián de los diálogos de TV: todo `Dialog` de Compose en `ui/tv` debe avisar a
 * [FocusRescue.dialogsOpen] mientras exista. Si no lo hace, el rescate de la pantalla de
 * atrás cree que el foco está «perdido» y le roba las teclas a la ventana del diálogo
 * (o, al revés, deja de rescatar cuando el diálogo se cierra). Es el caso real de
 * `TvNoUsbDialog`, que quedó sin el aviso mientras los demás sí lo traían.
 */
class TvModalGuardTest {

    @Test
    fun everyTvDialogWarnsTheFocusRescue() {
        val root = listOf(
            File("src/main/java/com/chronosbvrx/visionplus/ui/tv"),
            File("app/src/main/java/com/chronosbvrx/visionplus/ui/tv"),
        ).firstOrNull { it.isDirectory }
            ?: error("no se encontró el directorio ui/tv")

        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("sin fuentes de ui/tv que revisar", files.size >= 8)

        val offenders = files.filter { file ->
            val text = file.readText()
            val opensAComposeDialog = text.contains("import androidx.compose.ui.window.Dialog\n")
            opensAComposeDialog && !text.contains("FocusRescue.dialogsOpen++")
        }.map { it.name }

        assertTrue(
            "Un diálogo de TV sin `FocusRescue.dialogsOpen++` deja que el rescate de la " +
                "pantalla de atrás le robe el foco (o lo apague al cerrarse). Infractores: $offenders",
            offenders.isEmpty(),
        )
    }
}
